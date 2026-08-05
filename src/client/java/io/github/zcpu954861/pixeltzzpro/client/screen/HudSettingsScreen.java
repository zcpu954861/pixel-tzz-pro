package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_RAISED;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.WARNING;

import io.github.zcpu954861.pixeltzzpro.client.hud.InformationDockOverlay;
import io.github.zcpu954861.pixeltzzpro.client.screen.HudSettingsDetailScreen.Section;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Two-column, non-scrolling directory for the six V3C local setting families. */
public final class HudSettingsScreen extends Screen {
	private static final int PANEL_X = 30;
	private static final int PANEL_Y = 20;
	private static final int PANEL_WIDTH = 580;
	private static final int PANEL_HEIGHT = 320;
	private static final int CARD_WIDTH = 270;
	private static final int CARD_HEIGHT = 47;

	private final Screen parent;
	private final HudSettingsSession session;
	private ConsoleButton apply;

	public HudSettingsScreen(final Screen parent) {
		this(parent, new HudSettingsSession());
	}

	HudSettingsScreen(final Screen parent, final HudSettingsSession session) {
		super(Component.translatable("pixel_tzz_pro.settings.hud"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int firstX = PANEL_X + 14;
		int secondX = firstX + CARD_WIDTH + 12;
		int firstY = 137;
		Section[] sections = Section.values();
		for (int index = 0; index < sections.length; index++) {
			Section section = sections[index];
			int x = index % 2 == 0 ? firstX : secondX;
			int y = firstY + index / 2 * (CARD_HEIGHT + 7);
			this.addRenderableWidget(new SectionButton(
				x,
				y,
				CARD_WIDTH,
				CARD_HEIGHT,
				section,
				ignored -> this.minecraft.gui.setScreen(
					new HudSettingsDetailScreen(this.parent, this.session, section)
				)
			));
		}

		ConsoleButton cancel = new ConsoleButton(
			PANEL_X + PANEL_WIDTH - 270,
			PANEL_Y + PANEL_HEIGHT - 31,
			122,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.cancel"),
			ignored -> leave(false),
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
				refreshButtons();
			},
			Variant.PRIMARY
		);
		this.addRenderableWidget(this.apply);
		this.setInitialFocus(this.apply);
		refreshButtons();
	}

	private void refreshButtons() {
		if (this.apply != null) {
			this.apply.active = this.session.dirty();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.session.refreshContext()) {
			rebuildWidgets();
		}
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
		ConsoleScreenViewport.Layout canvas = canvas();
		graphics.pose().pushMatrix();
		graphics.pose().translate(canvas.x(), canvas.y());
		graphics.pose().scale((float)canvas.scale(), (float)canvas.scale());
		try {
			drawChrome(graphics);
			InformationDockOverlay.renderPreview(
				graphics,
				PANEL_X + 14,
				PANEL_Y + 54,
				236,
				55,
				this.session.effectiveDraft()
			);
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
		graphics.fill(PANEL_X + 4, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + 2, INFO_CYAN);
		graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud.eyebrow"), PANEL_X + 16, PANEL_Y + 12, INFO_CYAN, false);
		graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud"), PANEL_X + 16, PANEL_Y + 29, MAIN_TEXT, true);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.hud.directory.description"),
			PANEL_X + 264,
			PANEL_Y + 59,
			PANEL_WIDTH - 282,
			SECONDARY_TEXT,
			false
		);
		graphics.text(
			this.font,
			Component.translatable(
				this.session.hasLiveProfile()
					? "pixel_tzz_pro.settings.hud.preview.live"
					: "pixel_tzz_pro.settings.hud.preview.sample"
			),
			PANEL_X + 264,
			PANEL_Y + 92,
			this.session.hasLiveProfile() ? SUCCESS : MUTED_TEXT,
			false
		);
		graphics.fill(PANEL_X + 14, PANEL_Y + 116, PANEL_X + PANEL_WIDTH - 14, PANEL_Y + 117, SURFACE_BORDER);
		graphics.text(
			this.font,
			Component.translatable(this.session.dirty()
				? "pixel_tzz_pro.settings.hud.unsaved"
				: "pixel_tzz_pro.settings.hud.saved"),
			PANEL_X + 14,
			PANEL_Y + PANEL_HEIGHT - 24,
			this.session.dirty() ? WARNING : MUTED_TEXT,
			false
		);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		return super.mouseClicked(referenceMouseEvent(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		return super.mouseReleased(referenceMouseEvent(event));
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double deltaX, final double deltaY) {
		if (this.session.refreshContext()) {
			rebuildWidgets();
			return true;
		}
		ConsoleScreenViewport.Layout canvas = canvas();
		return super.mouseDragged(
			referenceMouseEvent(event),
			canvas.toReferenceLength(deltaX),
			canvas.toReferenceLength(deltaY)
		);
	}

	private MouseButtonEvent referenceMouseEvent(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return new MouseButtonEvent(canvas.toReferenceX(event.x()), canvas.toReferenceY(event.y()), event.buttonInfo());
	}

	private ConsoleScreenViewport.Layout canvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	private void leave(final boolean fromEscape) {
		if (this.session.dirty()) {
			this.minecraft.gui.setScreen(new HudDiscardScreen(this, this.parent, this.session));
			return;
		}
		this.session.close();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void onClose() {
		leave(true);
	}

	private static final class SectionButton extends Button {
		private final Section section;

		private SectionButton(
			final int x,
			final int y,
			final int width,
			final int height,
			final Section section,
			final OnPress press
		) {
			super(x, y, width, height, Component.translatable(section.titleKey()), press, DEFAULT_NARRATION);
			this.section = section;
		}

		@Override
		protected void extractContents(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float tickProgress
		) {
			boolean highlighted = this.active && (isHovered() || isFocused());
			int accent = this.section.accent();
			graphics.fill(getX(), getY(), getRight(), getBottom(), highlighted ? SURFACE_RAISED : SURFACE);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), highlighted ? accent : SURFACE_BORDER);
			graphics.fill(getX(), getY(), getX() + 3, getBottom(), accent);
			Font font = Minecraft.getInstance().font;
			graphics.text(font, this.section.icon(), getX() + 12, getY() + 18, accent, true);
			graphics.text(font, getMessage(), getX() + 48, getY() + 9, MAIN_TEXT, true);
			graphics.textWithWordWrap(
				font,
				Component.translatable(this.section.descriptionKey()),
				getX() + 48,
				getY() + 25,
				getWidth() - 59,
				SECONDARY_TEXT,
				false
			);
			handleCursor(graphics);
		}
	}
}
