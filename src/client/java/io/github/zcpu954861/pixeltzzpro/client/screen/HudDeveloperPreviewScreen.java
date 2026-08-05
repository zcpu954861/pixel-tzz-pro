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

import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime;
import io.github.zcpu954861.pixeltzzpro.client.hud.InformationDockOverlay;
import io.github.zcpu954861.pixeltzzpro.client.screen.HudDeveloperPreviewSession.Selection;
import io.github.zcpu954861.pixeltzzpro.client.screen.HudDeveloperPreviewSession.State;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Action;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Boundary;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Identity;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Mode;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Scenario;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Host-only, bounded development console for the two isolated V3C preview surfaces. */
final class HudDeveloperPreviewScreen extends Screen {
	private static final int PANEL_X = 30;
	private static final int PANEL_Y = 20;
	private static final int PANEL_WIDTH = 580;
	private static final int PANEL_HEIGHT = 320;
	private static final int COLUMN_WIDTH = 270;
	private static final int LEFT_X = PANEL_X + 14;
	private static final int RIGHT_X = LEFT_X + COLUMN_WIDTH + 12;
	private static final int PREVIEW_Y = 74;
	private static final int PREVIEW_HEIGHT = 76;

	private final Screen parent;
	private final HudSettingsSession session;
	private Scenario hudScenario = Scenario.TASK_RUNNING;
	private Scenario countdownScenario = Scenario.COUNTDOWN_START;
	private Identity identity = Identity.HOST;
	private Mode mode = Mode.NORMAL;
	private Boundary boundary = Boundary.NORMAL;
	private boolean leaving;

	HudDeveloperPreviewScreen(final Screen parent, final HudSettingsSession session) {
		super(Component.translatable("pixel_tzz_pro.settings.hud.developer.title"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		if (!this.session.developerPreviewAvailable()) {
			addBackButton();
			return;
		}

		addCycleButton(
			LEFT_X + 112,
			184,
			144,
			valueKey("identity", this.identity),
			() -> this.identity = next(this.identity)
		);
		addCycleButton(
			LEFT_X + 112,
			214,
			144,
			valueKey("mode", this.mode),
			() -> this.mode = next(this.mode)
		);
		addCycleButton(
			LEFT_X + 112,
			244,
			144,
			valueKey("boundary", this.boundary),
			() -> this.boundary = next(this.boundary)
		);

		addSurfaceControls(Surface.HUD, 164, this.hudScenario);
		addSurfaceControls(Surface.COUNTDOWN, 226, this.countdownScenario);
		addBackButton();
	}

	private void addCycleButton(
		final int x,
		final int y,
		final int width,
		final String valueKey,
		final Runnable cycle
	) {
		this.addRenderableWidget(new ConsoleButton(
			x,
			y,
			width,
			22,
			Component.translatable(valueKey).withColor(INFO_CYAN),
			ignored -> {
				cycle.run();
				rebuildWidgets();
			},
			Variant.NORMAL
		));
	}

	private void addSurfaceControls(
		final Surface surface,
		final int top,
		final Scenario scenario
	) {
		HudDeveloperPreviewSession preview = this.session.developerPreview();
		ConsoleButton scenarioButton = new ConsoleButton(
			RIGHT_X + 80,
			top + 13,
			176,
			20,
			Component.translatable(valueKey("scenario", scenario)).withColor(INFO_CYAN),
			ignored -> {
				if (surface == Surface.HUD) {
					this.hudScenario = nextScenario(surface, this.hudScenario);
				} else {
					this.countdownScenario = nextScenario(surface, this.countdownScenario);
				}
				rebuildWidgets();
			},
			Variant.NORMAL
		);
		this.addRenderableWidget(scenarioButton);

		int actionY = top + 36;
		for (Action action : Action.values()) {
			int index = action.ordinal();
			ConsoleButton button = new ConsoleButton(
				RIGHT_X + index * 86,
				actionY,
				82,
				20,
				Component.translatable(
					"pixel_tzz_pro.settings.hud.developer.action."
						+ action.name().toLowerCase(Locale.ROOT)
				),
				ignored -> request(action, surface),
				action == Action.OPEN
					? Variant.PRIMARY
					: action == Action.CLOSE ? Variant.DANGER : Variant.NORMAL
			);
			button.active = preview.canRequest(action, surface);
			this.addRenderableWidget(button);
		}
	}

	private void addBackButton() {
		ConsoleButton back = new ConsoleButton(
			LEFT_X,
			PANEL_Y + PANEL_HEIGHT - 31,
			124,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.developer.back"),
			ignored -> leave(),
			Variant.NORMAL
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(back);
	}

	private void request(final Action action, final Surface surface) {
		Scenario scenario = surface == Surface.HUD ? this.hudScenario : this.countdownScenario;
		this.session.developerPreview().request(
			action,
			surface,
			new Selection(scenario, this.identity, this.mode, this.boundary)
		);
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		boolean changed = this.session.refreshContext();
		if (!this.session.developerPreviewAvailable()) {
			leave();
			return;
		}
		if (changed) {
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
			drawHudPreview(graphics);
			drawCountdownPreview(graphics);
			drawControls(graphics);
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
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.hud.developer.eyebrow"),
			PANEL_X + 16,
			PANEL_Y + 10,
			INFO_CYAN,
			false
		);
		graphics.text(this.font, getTitle(), PANEL_X + 16, PANEL_Y + 27, MAIN_TEXT, true);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.hud.developer.description"),
			PANEL_X + 270,
			PANEL_Y + 22,
			PANEL_WIDTH - 286,
			SECONDARY_TEXT,
			false
		);
	}

	private void drawHudPreview(final GuiGraphicsExtractor graphics) {
		drawPreviewBase(graphics, LEFT_X);
		if (ClientHudRuntime.previewSnapshot().isPresent()) {
			InformationDockOverlay.renderPreview(
				graphics,
				LEFT_X,
				PREVIEW_Y,
				COLUMN_WIDTH,
				PREVIEW_HEIGHT,
				this.session.effectiveDraft()
			);
		} else {
			drawPreviewEmpty(graphics, LEFT_X, Surface.HUD);
		}
		drawPreviewHeader(graphics, LEFT_X, Surface.HUD, BRAND_GOLD);
	}

	private void drawCountdownPreview(final GuiGraphicsExtractor graphics) {
		drawPreviewBase(graphics, RIGHT_X);
		boolean rendered = ClientHudRuntime.previewCountdownSnapshot()
			.map(value -> InformationDockOverlay.renderCountdownPreview(
				graphics,
				value,
				RIGHT_X,
				PREVIEW_Y,
				COLUMN_WIDTH,
				PREVIEW_HEIGHT,
				this.session.effectiveDraft()
			))
			.orElse(false);
		if (!rendered) {
			drawPreviewEmpty(graphics, RIGHT_X, Surface.COUNTDOWN);
		}
		drawPreviewHeader(graphics, RIGHT_X, Surface.COUNTDOWN, INFO_CYAN);
	}

	private void drawPreviewBase(
		final GuiGraphicsExtractor graphics,
		final int x
	) {
		graphics.fill(x, PREVIEW_Y, x + COLUMN_WIDTH, PREVIEW_Y + PREVIEW_HEIGHT, 0xD8070D13);
		graphics.outline(x, PREVIEW_Y, COLUMN_WIDTH, PREVIEW_HEIGHT, SURFACE_BORDER);
	}

	private void drawPreviewHeader(
		final GuiGraphicsExtractor graphics,
		final int x,
		final Surface surface,
		final int accent
	) {
		graphics.fill(x + 1, PREVIEW_Y + 1, x + COLUMN_WIDTH - 1, PREVIEW_Y + 21, 0xE80B1219);
		graphics.fill(x, PREVIEW_Y, x + 3, PREVIEW_Y + PREVIEW_HEIGHT, accent);
		graphics.outline(x, PREVIEW_Y, COLUMN_WIDTH, PREVIEW_HEIGHT, SURFACE_BORDER);
		graphics.text(
			this.font,
			Component.translatable(surfaceKey(surface)),
			x + 9,
			PREVIEW_Y + 7,
			MAIN_TEXT,
			true
		);
		State state = this.session.developerPreview().state(surface);
		Component status = Component.translatable(
			"pixel_tzz_pro.settings.hud.developer.state." + state.name().toLowerCase(Locale.ROOT)
		);
		graphics.text(
			this.font,
			status,
			x + COLUMN_WIDTH - this.font.width(status) - 9,
			PREVIEW_Y + 7,
			state == State.OPEN ? SUCCESS : state == State.CLOSED ? MUTED_TEXT : INFO_CYAN,
			false
		);
	}

	private void drawPreviewEmpty(
		final GuiGraphicsExtractor graphics,
		final int x,
		final Surface surface
	) {
		graphics.textWithWordWrap(
			this.font,
			Component.translatable(
				this.session.developerPreview().state(surface) == State.OPENING
					? "pixel_tzz_pro.settings.hud.developer.preview.waiting"
					: "pixel_tzz_pro.settings.hud.developer.preview.closed"
			),
			x + 12,
			PREVIEW_Y + 29,
			COLUMN_WIDTH - 24,
			MUTED_TEXT,
			false
		);
	}

	private void drawControls(final GuiGraphicsExtractor graphics) {
		graphics.fill(LEFT_X, 164, LEFT_X + COLUMN_WIDTH, 278, SURFACE);
		graphics.outline(LEFT_X, 164, COLUMN_WIDTH, 114, SURFACE_BORDER);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.hud.developer.simulation"),
			LEFT_X + 10,
			172,
			BRAND_GOLD,
			true
		);
		controlLabel(graphics, LEFT_X + 10, 191, "identity");
		controlLabel(graphics, LEFT_X + 10, 221, "mode");
		controlLabel(graphics, LEFT_X + 10, 251, "boundary");

		for (int top : new int[] {164, 226}) {
			graphics.fill(RIGHT_X, top, RIGHT_X + COLUMN_WIDTH, top + 58, SURFACE);
			graphics.outline(RIGHT_X, top, COLUMN_WIDTH, 58, SURFACE_BORDER);
		}
		graphics.text(this.font, Component.translatable(surfaceKey(Surface.HUD)), RIGHT_X + 9, 170, BRAND_GOLD, true);
		graphics.text(this.font, Component.translatable(surfaceKey(Surface.COUNTDOWN)), RIGHT_X + 9, 232, INFO_CYAN, true);
		graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud.developer.scenario"), RIGHT_X + 9, 183, MUTED_TEXT, false);
		graphics.text(this.font, Component.translatable("pixel_tzz_pro.settings.hud.developer.scenario"), RIGHT_X + 9, 245, MUTED_TEXT, false);

		String phase = ClientSessionState.snapshot().phaseId().map(Object::toString).orElse("—");
		Component automatic = Component.translatable(
			"pixel_tzz_pro.settings.hud.developer.automatic",
			phase
		);
		graphics.text(
			this.font,
			ellipsized(automatic, PANEL_WIDTH - 156),
			LEFT_X + 140,
			PANEL_Y + PANEL_HEIGHT - 23,
			MUTED_TEXT,
			false
		);
	}

	private void controlLabel(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final String key
	) {
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.hud.developer." + key),
			x,
			y,
			SECONDARY_TEXT,
			false
		);
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

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.session.refreshContext()) {
			if (!this.session.developerPreviewAvailable()) {
				leave();
				return true;
			}
			rebuildWidgets();
			return true;
		}
		return super.mouseClicked(reference(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.session.refreshContext()) {
			if (!this.session.developerPreviewAvailable()) {
				leave();
				return true;
			}
			rebuildWidgets();
			return true;
		}
		return super.mouseReleased(reference(event));
	}

	private MouseButtonEvent reference(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return new MouseButtonEvent(canvas.toReferenceX(event.x()), canvas.toReferenceY(event.y()), event.buttonInfo());
	}

	private ConsoleScreenViewport.Layout canvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	private void leave() {
		if (this.leaving) {
			return;
		}
		this.leaving = true;
		this.session.closeDeveloperPreview();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void onClose() {
		leave();
	}

	private static String surfaceKey(final Surface surface) {
		return "pixel_tzz_pro.settings.hud.developer.surface."
			+ surface.name().toLowerCase(Locale.ROOT);
	}

	private static String valueKey(final String family, final Enum<?> value) {
		return "pixel_tzz_pro.settings.hud.developer."
			+ family
			+ "."
			+ value.name().toLowerCase(Locale.ROOT);
	}

	private static Scenario nextScenario(final Surface surface, final Scenario current) {
		Scenario[] values = Arrays.stream(Scenario.values())
			.filter(value -> value.countdown() == (surface == Surface.COUNTDOWN))
			.toArray(Scenario[]::new);
		int index = Arrays.asList(values).indexOf(current);
		return values[(Math.max(0, index) + 1) % values.length];
	}

	private static <E extends Enum<E>> E next(final E current) {
		E[] values = current.getDeclaringClass().getEnumConstants();
		return values[(current.ordinal() + 1) % values.length];
	}
}
