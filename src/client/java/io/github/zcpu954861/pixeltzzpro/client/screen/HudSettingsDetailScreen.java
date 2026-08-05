package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.DANGER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.WARNING;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Exclusion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Motion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Anchor;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import io.github.zcpu954861.pixeltzzpro.client.hud.InformationDockOverlay;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** One shared shell for the six reachable HUD/countdown preference pages. */
final class HudSettingsDetailScreen extends Screen {
	private static final int PANEL_X = 30;
	private static final int PANEL_Y = 20;
	private static final int PANEL_WIDTH = 580;
	private static final int PANEL_HEIGHT = 320;
	private static final int PREVIEW_X = PANEL_X + 14;
	private static final int PREVIEW_Y = PANEL_Y + 58;
	private static final int PREVIEW_WIDTH = 238;
	private static final int PREVIEW_HEIGHT = 202;
	private static final int CONTROL_X = PANEL_X + 270;
	private static final int CONTROL_WIDTH = 294;
	private static final DecimalFormat DECIMAL = new DecimalFormat(
		"0.00",
		DecimalFormatSymbols.getInstance(Locale.ROOT)
	);

	private final Screen rootParent;
	private final HudSettingsSession session;
	private final Section section;
	private int componentScroll;
	private int selectedExclusion;
	private boolean draggingPreview;
	private ConsoleButton apply;

	HudSettingsDetailScreen(
		final Screen rootParent,
		final HudSettingsSession session,
		final Section section
	) {
		super(Component.translatable(section.titleKey()));
		this.rootParent = rootParent;
		this.session = session;
		this.section = section;
	}

	@Override
	protected void init() {
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.session.refreshContext()) {
			rebuildWidgets();
		}
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		switch (this.section) {
			case GLOBAL -> globalWidgets();
			case LAYOUT -> layoutWidgets();
			case COMPONENTS -> componentWidgets();
			case EXCLUSIONS -> exclusionWidgets();
			case COUNTDOWN -> countdownWidgets();
			case RESET_DIAGNOSTICS -> resetWidgets();
		}
		ConsoleButton back = new ConsoleButton(
			PANEL_X + 14,
			PANEL_Y + PANEL_HEIGHT - 31,
			116,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.back"),
			ignored -> returnToDirectory(),
			Variant.NORMAL
		);
		this.addRenderableWidget(back);
		ConsoleButton cancel = new ConsoleButton(
			PANEL_X + PANEL_WIDTH - 270,
			PANEL_Y + PANEL_HEIGHT - 31,
			122,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.cancel"),
			ignored -> {
				if (this.session.dirty()) {
					this.minecraft.gui.setScreen(new HudDiscardScreen(
						this,
						new HudSettingsScreen(this.rootParent, this.session),
						this.session
					));
				} else {
					returnToDirectory();
				}
			},
			Variant.NORMAL
		);
		this.addRenderableWidget(cancel);
		this.apply = new ConsoleButton(
			PANEL_X + PANEL_WIDTH - 140,
			PANEL_Y + PANEL_HEIGHT - 31,
			126,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.apply"),
			ignored -> {
				this.session.apply();
				rebuildWidgets();
			},
			Variant.PRIMARY
		);
		this.apply.active = this.session.dirty();
		this.addRenderableWidget(this.apply);
		this.setInitialFocus(back);
	}

	private void globalWidgets() {
		var policy = this.session.policy();
		var effective = this.session.effectiveDraft();
		ConsoleButton visibility = toggleButton(
			CONTROL_X + 174,
			92,
			effective.hudVisible(),
			ignored -> this.session.update(value -> value.withHudVisible(!value.hudVisible()))
		);
		visibility.active = policy.allowHide();
		this.addRenderableWidget(visibility);
		this.addRenderableWidget(toggleButton(
			CONTROL_X + 174,
			142,
			effective.highContrast(),
			ignored -> this.session.update(value -> value.withHighContrast(!value.highContrast()))
		));
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X + 174,
			192,
			106,
			22,
			motionLabel(effective.motion()),
			ignored -> {
				this.session.update(value -> value.withMotion(next(value.motion())));
				rebuildWidgets();
			},
			Variant.NORMAL
		));
	}

	private void layoutWidgets() {
		var policy = this.session.policy();
		var effective = this.session.effectiveDraft();
		List<Anchor> anchors = policy.allowedAnchors().stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
		ConsoleButton anchor = new ConsoleButton(
			CONTROL_X + 154,
			84,
			126,
			22,
			anchorLabel(effective.anchor()),
			ignored -> {
				int index = anchors.indexOf(this.session.effectiveDraft().anchor());
				Anchor next = anchors.get((Math.max(0, index) + 1) % anchors.size());
				this.session.update(value -> value.withAnchor(next));
				rebuildWidgets();
			},
			Variant.NORMAL
		);
		anchor.active = anchors.size() > 1;
		this.addRenderableWidget(anchor);
		this.addRenderableWidget(new HudSlider(
			CONTROL_X + 154, 124, 126, 22,
			-policy.maximumOffsetX(), policy.maximumOffsetX(), effective.offsetX(),
			value -> Component.literal(Math.round(value) + " px").withColor(INFO_CYAN),
			value -> {
				this.session.update(current -> current.withOffset((int)Math.round(value), current.offsetY()));
				refreshApplyButton();
			}
		));
		this.addRenderableWidget(new HudSlider(
			CONTROL_X + 154, 164, 126, 22,
			-policy.maximumOffsetY(), policy.maximumOffsetY(), effective.offsetY(),
			value -> Component.literal(Math.round(value) + " px").withColor(INFO_CYAN),
			value -> {
				this.session.update(current -> current.withOffset(current.offsetX(), (int)Math.round(value)));
				refreshApplyButton();
			}
		));
		this.addRenderableWidget(new HudSlider(
			CONTROL_X + 154, 204, 126, 22,
			policy.minimumScale(), policy.maximumScale(), effective.scale(),
			value -> Component.literal(DECIMAL.format(value) + "×").withColor(INFO_CYAN),
			value -> {
				this.session.update(current -> current.withScale((float)value));
				refreshApplyButton();
			}
		));
		this.addRenderableWidget(new HudSlider(
			CONTROL_X + 154, 244, 126, 22,
			policy.minimumOpacity(), policy.maximumOpacity(), effective.opacity(),
			value -> Component.literal(Math.round(value * 100.0D) + "%").withColor(INFO_CYAN),
			value -> {
				this.session.update(current -> current.withOpacity((float)value));
				refreshApplyButton();
			}
		));
	}

	private void componentWidgets() {
		List<ComponentControl> components = controls();
		int visibleRows = 4;
		this.componentScroll = Math.clamp(
			this.componentScroll,
			0,
			Math.max(0, components.size() - visibleRows)
		);
		var effective = this.session.effectiveDraft();
		boolean management = this.session.policy().allowComponentManagement();
		for (int row = 0; row < visibleRows; row++) {
			int index = this.componentScroll + row;
			if (index >= components.size()) {
				break;
			}
			ComponentControl control = components.get(index);
			int y = 76 + row * 39 + 6;
			boolean hidden = effective.hiddenComponents().contains(control.id());
			boolean compact = effective.compactComponents().contains(control.id());

			ConsoleButton visibility = new ConsoleButton(
				CONTROL_X + 136,
				y,
				50,
				22,
				Component.translatable(!management || !control.allowHide()
					? "pixel_tzz_pro.settings.hud.components.action.locked"
					: hidden
						? "pixel_tzz_pro.settings.hud.components.action.show"
						: "pixel_tzz_pro.settings.hud.components.action.hide"),
				ignored -> {
					if (this.session.toggleHidden(control.id())) {
						rebuildWidgets();
					}
				},
				Variant.NORMAL
			);
			visibility.active = management && control.visible() && control.allowHide();
			this.addRenderableWidget(visibility);

			ConsoleButton compactButton = new ConsoleButton(
				CONTROL_X + 190,
				y,
				50,
				22,
				Component.translatable(!management || !control.allowCompact()
					? "pixel_tzz_pro.settings.hud.components.action.locked"
					: !this.session.nodes().containsKey(control.id())
						? "pixel_tzz_pro.settings.hud.components.action.unavailable"
						: compact
							? "pixel_tzz_pro.settings.hud.components.action.normal"
							: "pixel_tzz_pro.settings.hud.components.action.compact"),
				ignored -> {
					if (this.session.toggleCompact(control.id())) {
						rebuildWidgets();
					}
				},
				Variant.NORMAL
			);
			compactButton.active = management
				&& control.visible()
				&& control.allowCompact()
				&& this.session.nodes().containsKey(control.id());
			this.addRenderableWidget(compactButton);

			HudSettingsSession.ReorderState reorder = this.session.reorderState(control.id());
			if (control.reorderGroup().isPresent()) {
				ConsoleButton up = new ConsoleButton(
					CONTROL_X + 244,
					y,
					22,
					22,
					Component.literal("↑"),
					ignored -> {
						if (this.session.moveComponent(control.id(), -1)) {
							rebuildWidgets();
						}
					},
					Variant.NORMAL
				);
				up.active = reorder.canMoveUp();
				this.addRenderableWidget(up);
				ConsoleButton down = new ConsoleButton(
					CONTROL_X + 270,
					y,
					22,
					22,
					Component.literal("↓"),
					ignored -> {
						if (this.session.moveComponent(control.id(), 1)) {
							rebuildWidgets();
						}
					},
					Variant.NORMAL
				);
				down.active = reorder.canMoveDown();
				this.addRenderableWidget(down);
			}
		}

		ConsoleButton reset = new ConsoleButton(
			CONTROL_X + 168,
			242,
			112,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.components.reset"),
			ignored -> {
				this.session.resetComponentDraft();
				rebuildWidgets();
			},
			Variant.NORMAL
		);
		reset.active = !this.session.draft().hiddenComponents().isEmpty()
			|| !this.session.draft().compactComponents().isEmpty()
			|| !this.session.draft().componentOrder().isEmpty();
		this.addRenderableWidget(reset);
	}

	private void exclusionWidgets() {
		List<Exclusion> values = this.session.draft().exclusions();
		this.selectedExclusion = values.isEmpty() ? 0 : Math.clamp(this.selectedExclusion, 0, values.size() - 1);
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X,
			92,
			88,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.exclusions.add"),
			ignored -> addExclusion(),
			Variant.NORMAL
		));
		ConsoleButton previous = new ConsoleButton(
			CONTROL_X + 96, 92, 54, 22, Component.literal("◀"), ignored -> {
				this.selectedExclusion = Math.max(0, this.selectedExclusion - 1);
				rebuildWidgets();
			}, Variant.NORMAL
		);
		previous.active = this.selectedExclusion > 0;
		this.addRenderableWidget(previous);
		ConsoleButton next = new ConsoleButton(
			CONTROL_X + 156, 92, 54, 22, Component.literal("▶"), ignored -> {
				this.selectedExclusion = Math.min(values.size() - 1, this.selectedExclusion + 1);
				rebuildWidgets();
			}, Variant.NORMAL
		);
		next.active = !values.isEmpty() && this.selectedExclusion < values.size() - 1;
		this.addRenderableWidget(next);
		ConsoleButton delete = new ConsoleButton(
			CONTROL_X + 218, 92, 62, 22,
			Component.translatable("pixel_tzz_pro.settings.hud.exclusions.delete"),
			ignored -> deleteExclusion(), Variant.DANGER
		);
		delete.active = !values.isEmpty();
		this.addRenderableWidget(delete);
		if (values.isEmpty()) {
			return;
		}
		Exclusion selected = values.get(this.selectedExclusion);
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X + 174, 136, 106, 22,
			toggleLabel(selected.enabled()),
			ignored -> changeSelected(value -> value.withEnabled(!value.enabled())), Variant.NORMAL
		));
		int centerX = CONTROL_X + 190;
		this.addRenderableWidget(nudgeButton(centerX, 176, "↑", 0.0D, -0.02D));
		this.addRenderableWidget(nudgeButton(centerX - 42, 202, "←", -0.02D, 0.0D));
		this.addRenderableWidget(nudgeButton(centerX + 42, 202, "→", 0.02D, 0.0D));
		this.addRenderableWidget(nudgeButton(centerX, 228, "↓", 0.0D, 0.02D));
	}

	private void countdownWidgets() {
		var effective = this.session.effectiveDraft();
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X + 174, 94, 106, 22,
			motionLabel(effective.motion()),
			ignored -> {
				this.session.update(value -> value.withMotion(next(value.motion())));
				rebuildWidgets();
			}, Variant.NORMAL
		));
		this.addRenderableWidget(new HudSlider(
			CONTROL_X + 154, 150, 126, 22, 0.0D, 1.0D, effective.hudVolume(),
			value -> Component.literal(Math.round(value * 100.0D) + "%").withColor(INFO_CYAN),
			value -> {
				this.session.update(current -> current.withVolumes((float)value, current.countdownVolume()));
				refreshApplyButton();
			}
		));
		this.addRenderableWidget(new HudSlider(
			CONTROL_X + 154, 206, 126, 22, 0.0D, 1.0D, effective.countdownVolume(),
			value -> Component.literal(Math.round(value * 100.0D) + "%").withColor(INFO_CYAN),
			value -> {
				this.session.update(current -> current.withVolumes(current.hudVolume(), (float)value));
				refreshApplyButton();
			}
		));
	}

	private void resetWidgets() {
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X + 154, 96, 126, 22,
			Component.translatable("pixel_tzz_pro.settings.hud.reset.layout"),
			ignored -> {
				this.session.resetLayoutDraft();
				rebuildWidgets();
			}, Variant.NORMAL
		));
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X + 154, 148, 126, 22,
			Component.translatable("pixel_tzz_pro.settings.hud.reset.all"),
			ignored -> {
				this.session.resetAllDraft();
				rebuildWidgets();
			}, Variant.DANGER
		));
		this.addRenderableWidget(new ConsoleButton(
			CONTROL_X + 154, 200, 126, 22,
			toggleLabel(this.session.draft().rawDiagnostics()),
			ignored -> {
				this.session.update(value -> value.withRawDiagnostics(!value.rawDiagnostics()));
				rebuildWidgets();
			}, Variant.NORMAL
		));
		if (this.session.developerPreviewAvailable()) {
			this.addRenderableWidget(new ConsoleButton(
				CONTROL_X + 154,
				240,
				126,
				22,
				Component.translatable("pixel_tzz_pro.settings.hud.developer.open"),
				ignored -> this.minecraft.gui.setScreen(new HudDeveloperPreviewScreen(this, this.session)),
				Variant.PRIMARY
			));
		}
	}

	private ConsoleButton toggleButton(
		final int x,
		final int y,
		final boolean enabled,
		final java.util.function.Consumer<ConsoleButton> change
	) {
		return new ConsoleButton(x, y, 106, 22, toggleLabel(enabled), button -> {
			change.accept((ConsoleButton)button);
			rebuildWidgets();
		}, Variant.NORMAL);
	}

	private void refreshApplyButton() {
		if (this.apply != null) {
			this.apply.active = this.session.dirty();
		}
	}

	private ConsoleButton nudgeButton(
		final int x,
		final int y,
		final String label,
		final double dx,
		final double dy
	) {
		return new ConsoleButton(x, y, 36, 22, Component.literal(label), ignored -> changeSelected(value -> value.moved(dx, dy)), Variant.NORMAL);
	}

	private void addExclusion() {
		List<Exclusion> values = new ArrayList<>(this.session.draft().exclusions());
		if (values.size() >= 16) {
			return;
		}
		int number = values.size() + 1;
		values.add(new Exclusion("custom-" + number, "自定义避让区域 " + number, true, 0.72D, 0.66D, 0.24D, 0.18D));
		this.selectedExclusion = values.size() - 1;
		this.session.update(current -> current.withExclusions(values));
		rebuildWidgets();
	}

	private void deleteExclusion() {
		List<Exclusion> values = new ArrayList<>(this.session.draft().exclusions());
		if (values.isEmpty()) {
			return;
		}
		values.remove(this.selectedExclusion);
		this.selectedExclusion = Math.max(0, this.selectedExclusion - 1);
		this.session.update(current -> current.withExclusions(values));
		rebuildWidgets();
	}

	private void changeSelected(final java.util.function.UnaryOperator<Exclusion> change) {
		List<Exclusion> values = new ArrayList<>(this.session.draft().exclusions());
		if (values.isEmpty()) {
			return;
		}
		values.set(this.selectedExclusion, change.apply(values.get(this.selectedExclusion)).validated());
		this.session.update(current -> current.withExclusions(values));
		rebuildWidgets();
	}

	@Override
	public void extractBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
	}

	@Override
	public void extractRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		if (this.apply != null) {
			this.apply.active = this.session.dirty();
		}
		ConsoleScreenViewport.Layout canvas = canvas();
		graphics.pose().pushMatrix();
		graphics.pose().translate(canvas.x(), canvas.y());
		graphics.pose().scale((float)canvas.scale(), (float)canvas.scale());
		try {
			drawChrome(graphics);
			if (this.section == Section.COUNTDOWN) {
				drawCountdownPreview(graphics);
			} else {
				InformationDockOverlay.renderPreview(
					graphics,
					PREVIEW_X,
					PREVIEW_Y,
					PREVIEW_WIDTH,
					PREVIEW_HEIGHT,
					this.session.effectiveDraft()
				);
			}
			if (this.section == Section.EXCLUSIONS) {
				drawExclusions(graphics);
			}
			drawSectionContent(graphics);
			super.extractRenderState(
				graphics,
				(int)Math.floor(canvas.toReferenceX(mouseX)),
				(int)Math.floor(canvas.toReferenceY(mouseY)),
				tickProgress
			);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private void drawChrome(final GuiGraphicsExtractor graphics) {
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, PANEL);
		graphics.outline(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + 4, PANEL_Y + PANEL_HEIGHT, BRAND_GOLD);
		graphics.fill(PANEL_X + 4, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + 2, this.section.accent());
		graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud.eyebrow"), PANEL_X + 16, PANEL_Y + 11, this.section.accent(), false);
		graphics.text(this.font, getTitle(), PANEL_X + 16, PANEL_Y + 28, MAIN_TEXT, true);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable(this.section.descriptionKey()),
			PANEL_X + 270,
			PANEL_Y + 25,
			PANEL_WIDTH - 286,
			SECONDARY_TEXT,
			false
		);
		graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud.preview"), PREVIEW_X, PREVIEW_Y - 14, MUTED_TEXT, false);
		graphics.text(
			this.font,
			Component.translatable(this.session.dirty() ? "pixel_tzz_pro.settings.hud.unsaved" : "pixel_tzz_pro.settings.hud.saved"),
			PANEL_X + 142,
			PANEL_Y + PANEL_HEIGHT - 24,
			this.session.dirty() ? WARNING : MUTED_TEXT,
			false
		);
	}

	private void drawSectionContent(final GuiGraphicsExtractor graphics) {
		switch (this.section) {
			case GLOBAL -> {
				row(graphics, 84, "pixel_tzz_pro.settings.hud.global.visibility", this.session.policy().allowHide() ? null : "pixel_tzz_pro.settings.hud.locked");
				row(graphics, 134, "pixel_tzz_pro.settings.hud.global.contrast", null);
				row(graphics, 184, "pixel_tzz_pro.settings.hud.global.motion", null);
			}
			case LAYOUT -> {
				row(graphics, 76, "pixel_tzz_pro.settings.hud.layout.anchor", null);
				row(graphics, 116, "pixel_tzz_pro.settings.hud.layout.offset_x", null);
				row(graphics, 156, "pixel_tzz_pro.settings.hud.layout.offset_y", null);
				row(graphics, 196, "pixel_tzz_pro.settings.hud.layout.scale", null);
				row(graphics, 236, "pixel_tzz_pro.settings.hud.layout.opacity", null);
				graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud.layout.drag_hint"), PREVIEW_X + 8, PREVIEW_Y + PREVIEW_HEIGHT - 15, MUTED_TEXT, false);
			}
			case COMPONENTS -> drawComponents(graphics);
			case EXCLUSIONS -> drawExclusionDetails(graphics);
			case COUNTDOWN -> {
				row(graphics, 86, "pixel_tzz_pro.settings.hud.countdown.motion", null);
				row(graphics, 142, "pixel_tzz_pro.settings.hud.countdown.hud_volume", null);
				row(graphics, 198, "pixel_tzz_pro.settings.hud.countdown.volume", null);
				graphics.textWithWordWrap(this.font, Component.translatable("pixel_tzz_pro.settings.hud.countdown.authority"), CONTROL_X, 244, CONTROL_WIDTH, MUTED_TEXT, false);
			}
			case RESET_DIAGNOSTICS -> drawDiagnostics(graphics);
		}
	}

	private void row(final GuiGraphicsExtractor graphics, final int y, final String title, final String note) {
		graphics.fill(CONTROL_X, y, CONTROL_X + CONTROL_WIDTH, y + 32, SURFACE);
		graphics.outline(CONTROL_X, y, CONTROL_WIDTH, 32, SURFACE_BORDER);
		graphics.text(this.font, Component.translatable(title), CONTROL_X + 10, y + 11, MAIN_TEXT, false);
		if (note != null) {
			graphics.text(this.font, Component.translatable(note), CONTROL_X + 114, y + 11, WARNING, false);
		}
	}

	private void drawComponents(final GuiGraphicsExtractor graphics) {
		List<ComponentControl> components = controls();
		var effective = this.session.effectiveDraft();
		int visible = 4;
		this.componentScroll = Math.clamp(this.componentScroll, 0, Math.max(0, components.size() - visible));
		int top = 76;
		graphics.enableScissor(CONTROL_X, top, CONTROL_X + CONTROL_WIDTH, top + 156);
		try {
			for (int row = 0; row < visible; row++) {
				int index = this.componentScroll + row;
				if (index >= components.size()) {
					break;
				}
				ComponentControl value = components.get(index);
				int y = top + row * 39;
				boolean hidden = effective.hiddenComponents().contains(value.id());
				boolean compact = effective.compactComponents().contains(value.id());
				boolean management = this.session.policy().allowComponentManagement();
				int accent = !management || !value.visible()
					? MUTED_TEXT
					: hidden
						? WARNING
						: compact ? INFO_CYAN : SUCCESS;
				graphics.fill(CONTROL_X, y, CONTROL_X + CONTROL_WIDTH, y + 34, SURFACE);
				graphics.outline(CONTROL_X, y, CONTROL_WIDTH, 34, SURFACE_BORDER);
				graphics.fill(CONTROL_X, y, CONTROL_X + 3, y + 34, accent);
				graphics.text(this.font, ellipsized(value.label(), 118), CONTROL_X + 11, y + 6, MAIN_TEXT, false);
				HudSettingsSession.ReorderState reorder = this.session.reorderState(value.id());
				Component status;
				if (value.reorderGroup().isPresent() && !reorder.available()) {
					status = Component.translatable(reorder.issue().translationKey());
				} else if (!management) {
					status = Component.translatable("pixel_tzz_pro.settings.hud.components.policy_locked");
				} else if (!value.visible()) {
					status = Component.translatable("pixel_tzz_pro.settings.hud.components.unavailable");
				} else if (hidden) {
					status = Component.translatable("pixel_tzz_pro.settings.hud.components.hidden");
				} else if (compact) {
					status = Component.translatable("pixel_tzz_pro.settings.hud.components.compact");
				} else if (!value.allowHide() && !value.allowCompact() && value.reorderGroup().isEmpty()) {
					status = Component.translatable("pixel_tzz_pro.settings.hud.components.locked");
				} else {
					status = Component.translatable("pixel_tzz_pro.settings.hud.components.shown");
				}
				graphics.text(
					this.font,
					ellipsized(status, 118),
					CONTROL_X + 11,
					y + 19,
					accent,
					false
				);
			}
		} finally {
			graphics.disableScissor();
		}
		if (components.isEmpty()) {
			graphics.textWithWordWrap(this.font, Component.translatable("pixel_tzz_pro.settings.hud.components.empty"), CONTROL_X + 10, top + 12, CONTROL_WIDTH - 20, MUTED_TEXT, false);
		}
		graphics.text(this.font, Component.literal((this.componentScroll + Math.min(1, components.size())) + " / " + components.size()), CONTROL_X, 246, MUTED_TEXT, false);
	}

	private void drawExclusionDetails(final GuiGraphicsExtractor graphics) {
		List<Exclusion> values = this.session.draft().exclusions();
		if (values.isEmpty()) {
			graphics.textWithWordWrap(this.font, Component.translatable("pixel_tzz_pro.settings.hud.exclusions.empty"), CONTROL_X, 136, CONTROL_WIDTH, MUTED_TEXT, false);
			return;
		}
		Exclusion selected = values.get(this.selectedExclusion);
		graphics.text(this.font, selected.description(), CONTROL_X, 128, MAIN_TEXT, true);
		graphics.text(this.font, selected.id(), CONTROL_X, 146, MUTED_TEXT, false);
		graphics.text(
			this.font,
			Component.literal(DECIMAL.format(selected.x()) + ", " + DECIMAL.format(selected.y()) + "  ·  " + DECIMAL.format(selected.width()) + " × " + DECIMAL.format(selected.height())),
			CONTROL_X,
			164,
			SECONDARY_TEXT,
			false
		);
	}

	private void drawExclusions(final GuiGraphicsExtractor graphics) {
		List<Exclusion> values = this.session.draft().exclusions();
		InformationDockOverlay.PreviewFrame frame = InformationDockOverlay.previewFrame(
			PREVIEW_X,
			PREVIEW_Y,
			PREVIEW_WIDTH,
			PREVIEW_HEIGHT
		);
		for (int index = 0; index < values.size(); index++) {
			Exclusion value = values.get(index);
			if (!value.enabled()) {
				continue;
			}
			int x = frame.x() + (int)Math.floor(value.x() * frame.width());
			int y = frame.y() + (int)Math.floor(value.y() * frame.height());
			int width = Math.max(1, (int)Math.ceil(value.width() * frame.width()));
			int height = Math.max(1, (int)Math.ceil(value.height() * frame.height()));
			int color = index == this.selectedExclusion ? BRAND_GOLD : WARNING;
			graphics.fill(x, y, x + width, y + height, color & 0x00FFFFFF | 0x36000000);
			graphics.outline(x, y, width, height, color);
		}
	}

	private void drawDiagnostics(final GuiGraphicsExtractor graphics) {
		row(graphics, 88, "pixel_tzz_pro.settings.hud.reset.layout", null);
		row(graphics, 140, "pixel_tzz_pro.settings.hud.reset.all", null);
		row(graphics, 192, "pixel_tzz_pro.settings.hud.diagnostics.raw", null);
		if (this.session.developerPreviewAvailable()) {
			row(graphics, 232, "pixel_tzz_pro.settings.hud.developer.open", null);
		}
		var version = ClientHudRuntime.dockVersion();
		String summary = version.map(value -> this.session.draft().rawDiagnostics()
			? "generation=" + value.definitionGeneration() + " epoch=" + value.epoch() + " context=" + value.contextVersion() + " sequence=" + value.sequence()
			: "HUD 已同步 · 序列 " + value.sequence()).orElse("当前没有正式 HUD 投影");
		graphics.textWithWordWrap(
			this.font,
			Component.literal(summary),
			CONTROL_X,
			this.session.developerPreviewAvailable() ? 270 : 242,
			CONTROL_WIDTH,
			version.isPresent() ? SUCCESS : MUTED_TEXT,
			false
		);
	}

	private void drawCountdownPreview(final GuiGraphicsExtractor graphics) {
		graphics.fill(
			PREVIEW_X,
			PREVIEW_Y,
			PREVIEW_X + PREVIEW_WIDTH,
			PREVIEW_Y + PREVIEW_HEIGHT,
			0xB80B1219
		);
		graphics.outline(PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT, SURFACE_BORDER);
		boolean rendered = ClientHudRuntime.previewCountdownSnapshot()
			.or(ClientHudRuntime::countdownSnapshot)
			.map(value -> InformationDockOverlay.renderCountdownPreview(
				graphics,
				value,
				PREVIEW_X,
				PREVIEW_Y,
				PREVIEW_WIDTH,
				PREVIEW_HEIGHT,
				this.session.effectiveDraft()
			))
			.orElse(false);
		if (!rendered) {
			graphics.textWithWordWrap(
				this.font,
				Component.translatable("pixel_tzz_pro.settings.hud.countdown.preview_empty"),
				PREVIEW_X + 12,
				PREVIEW_Y + 18,
				PREVIEW_WIDTH - 24,
				MUTED_TEXT,
				false
			);
		}
	}

	private List<ComponentControl> controls() {
		return this.session.controls();
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		MouseButtonEvent reference = reference(event);
		if (this.section == Section.LAYOUT && in(reference.x(), reference.y(), PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT)) {
			this.draggingPreview = true;
			return true;
		}
		return super.mouseClicked(reference, doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		boolean wasDragging = this.draggingPreview;
		this.draggingPreview = false;
		if (wasDragging) {
			rebuildWidgets();
			return true;
		}
		return super.mouseReleased(reference(event));
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double deltaX, final double deltaY) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		ConsoleScreenViewport.Layout canvas = canvas();
		if (this.draggingPreview && this.section == Section.LAYOUT) {
			double previewScale = InformationDockOverlay.previewReferenceScale(PREVIEW_WIDTH, PREVIEW_HEIGHT);
			int dx = (int)Math.round(canvas.toReferenceLength(deltaX) / previewScale);
			int dy = (int)Math.round(canvas.toReferenceLength(deltaY) / previewScale);
			var effective = this.session.effectiveDraft();
			this.session.update(value -> {
				boolean left = effective.anchor() == Anchor.BOTTOM_LEFT || effective.anchor() == Anchor.TOP_LEFT;
				boolean top = effective.anchor() == Anchor.TOP_LEFT || effective.anchor() == Anchor.TOP_RIGHT;
				return value.withOffset(
					Math.clamp(
						effective.offsetX() + (left ? dx : -dx),
						-this.session.policy().maximumOffsetX(),
						this.session.policy().maximumOffsetX()
					),
					Math.clamp(
						effective.offsetY() + (top ? dy : -dy),
						-this.session.policy().maximumOffsetY(),
						this.session.policy().maximumOffsetY()
					)
				);
			});
			return true;
		}
		return super.mouseDragged(reference(event), canvas.toReferenceLength(deltaX), canvas.toReferenceLength(deltaY));
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		ConsoleScreenViewport.Layout canvas = canvas();
		double x = canvas.toReferenceX(mouseX);
		double y = canvas.toReferenceY(mouseY);
		if (this.section == Section.COMPONENTS && in(x, y, CONTROL_X, 76, CONTROL_WIDTH, 156)) {
			int maximum = Math.max(0, controls().size() - 4);
			int next = Math.clamp(this.componentScroll - (int)Math.signum(verticalAmount), 0, maximum);
			if (next != this.componentScroll) {
				this.componentScroll = next;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private static boolean in(final double x, final double y, final int left, final int top, final int width, final int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private MouseButtonEvent reference(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return new MouseButtonEvent(canvas.toReferenceX(event.x()), canvas.toReferenceY(event.y()), event.buttonInfo());
	}

	private ConsoleScreenViewport.Layout canvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	private void returnToDirectory() {
		this.minecraft.gui.setScreen(new HudSettingsScreen(this.rootParent, this.session));
	}

	@Override
	public void onClose() {
		returnToDirectory();
	}

	private Component ellipsized(final Component value, final int maximumWidth) {
		if (this.font.width(value) <= maximumWidth) {
			return value;
		}
		String suffix = "…";
		String prefix = this.font.plainSubstrByWidth(
			value.getString(),
			Math.max(0, maximumWidth - this.font.width(suffix))
		);
		return Component.literal(prefix + suffix).withStyle(value.getStyle());
	}

	private static Component toggleLabel(final boolean enabled) {
		return Component.translatable("pixel_tzz_pro.settings.toggle." + (enabled ? "on" : "off"))
			.withColor(enabled ? SUCCESS : SECONDARY_TEXT);
	}

	private static Component motionLabel(final Motion motion) {
		return Component.translatable("pixel_tzz_pro.settings.hud.motion." + motion.name().toLowerCase(Locale.ROOT)).withColor(INFO_CYAN);
	}

	private static Component anchorLabel(final Anchor anchor) {
		return Component.translatable("pixel_tzz_pro.settings.hud.anchor." + anchor.name().toLowerCase(Locale.ROOT)).withColor(INFO_CYAN);
	}

	private static Motion next(final Motion value) {
		return Motion.values()[(value.ordinal() + 1) % Motion.values().length];
	}

	enum Section {
		GLOBAL("global", "◎", INFO_CYAN),
		LAYOUT("layout", "⌖", BRAND_GOLD),
		COMPONENTS("components", "▤", SUCCESS),
		EXCLUSIONS("exclusions", "▱", WARNING),
		COUNTDOWN("countdown", "03", INFO_CYAN),
		RESET_DIAGNOSTICS("diagnostics", "i", DANGER);

		private final String key;
		private final String icon;
		private final int accent;

		Section(final String key, final String icon, final int accent) {
			this.key = key;
			this.icon = icon;
			this.accent = accent;
		}

		String titleKey() {
			return "pixel_tzz_pro.settings.hud.section." + this.key;
		}

		String descriptionKey() {
			return titleKey() + ".description";
		}

		String icon() {
			return this.icon;
		}

		int accent() {
			return this.accent;
		}
	}

	private static final class HudSlider extends AbstractSliderButton {
		private final double minimum;
		private final double maximum;
		private final java.util.function.DoubleFunction<Component> label;
		private final DoubleConsumer change;

		private HudSlider(
			final int x,
			final int y,
			final int width,
			final int height,
			final double minimum,
			final double maximum,
			final double initial,
			final java.util.function.DoubleFunction<Component> label,
			final DoubleConsumer change
		) {
			super(x, y, width, height, Component.empty(), normalize(initial, minimum, maximum));
			this.minimum = minimum;
			this.maximum = maximum;
			this.label = label;
			this.change = change;
			updateMessage();
		}

		private double actual() {
			return this.minimum + (this.maximum - this.minimum) * this.value;
		}

		@Override
		protected void updateMessage() {
			if (this.label != null) {
				setMessage(this.label.apply(actual()));
			}
		}

		@Override
		protected void applyValue() {
			if (this.change != null) {
				this.change.accept(actual());
			}
		}

		@Override
		public void extractWidgetRenderState(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float tickProgress
		) {
			graphics.fill(getX(), getY(), getRight(), getBottom(), this.active ? 0xFF202B35 : 0xFF151E27);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), this.active && (isHovered() || isFocused()) ? INFO_CYAN : SURFACE_BORDER);
			int left = getX() + 7;
			int right = getRight() - 7;
			int y = getBottom() - 5;
			int handle = left + (int)Math.round((right - left) * this.value);
			graphics.fill(left, y, right, y + 1, 0xFF364854);
			graphics.fill(left, y, handle, y + 1, INFO_CYAN);
			graphics.fill(handle - 1, y - 2, handle + 2, y + 3, BRAND_GOLD);
			graphics.text(net.minecraft.client.Minecraft.getInstance().font, getMessage(), getX() + 7, getY() + 5, this.active ? MAIN_TEXT : MUTED_TEXT, false);
			handleCursor(graphics);
		}

		private static double normalize(final double value, final double minimum, final double maximum) {
			if (maximum <= minimum) {
				return 0.0D;
			}
			return Math.clamp((value - minimum) / (maximum - minimum), 0.0D, 1.0D);
		}
	}
}
