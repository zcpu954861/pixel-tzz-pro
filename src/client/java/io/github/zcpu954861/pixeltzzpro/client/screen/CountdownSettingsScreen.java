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

import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownPreferences;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownPreferences.Motion;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Checkpoint;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.CompletionPresentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.DigitPresentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.DigitTransition;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Easing;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.PanelPresentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Presentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.ProgressMode;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.ProgressPresentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Purpose;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.RollDirection;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.State;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.SurfaceMotion;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.SurfaceTransition;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Text;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.TimeFormat;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.TimePrecision;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.TimePresentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Timing;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Version;
import io.github.zcpu954861.pixeltzzpro.client.countdown.CountdownRenderer;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Two-column local accessibility page for the non-hideable V3C countdown. */
public final class CountdownSettingsScreen extends Screen {
	private static final int PANEL_X = 54;
	private static final int PANEL_Y = 28;
	private static final int PANEL_WIDTH = 532;
	private static final int PANEL_HEIGHT = 304;
	private static final int LEFT_X = PANEL_X + 14;
	private static final int LEFT_WIDTH = 238;
	private static final int RIGHT_X = PANEL_X + 266;
	private static final int RIGHT_WIDTH = 252;
	private static final int CONTENT_Y = 105;
	private static final UUID PREVIEW_GAME_ID = UUID.nameUUIDFromBytes(
		"pixel-tzz-pro:countdown-preview-game".getBytes(StandardCharsets.UTF_8)
	);
	private static final UUID PREVIEW_COUNTDOWN_ID = UUID.nameUUIDFromBytes(
		"pixel-tzz-pro:countdown-preview".getBytes(StandardCharsets.UTF_8)
	);

	private final Screen parent;
	private final CountdownRenderer previewRenderer = new CountdownRenderer();
	private long previewOriginNanos = System.nanoTime();
	private long previewCycle = Long.MIN_VALUE;
	private long previewSettleGeneration;
	private PreviewState previewState = PreviewState.RUNNING;
	private ConsoleButton motion;
	private ConsoleButton contrast;
	private ConsoleSlider volume;
	private ConsoleButton previewStateControl;

	public CountdownSettingsScreen(final Screen parent) {
		super(Component.translatable("pixel_tzz_pro.settings.countdown"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientCountdownPreferences.initialize();
		int controlX = LEFT_X + LEFT_WIDTH - 96;
		this.motion = new ConsoleButton(
			controlX,
			CONTENT_Y + 15,
			86,
			22,
			Component.empty(),
			button -> {
				ClientCountdownPreferences.update(current -> current.withMotion(current.motion().next()));
				this.previewRenderer.reset();
				refreshLabels();
			},
			Variant.NORMAL
		);
		this.contrast = new ConsoleButton(
			controlX,
			CONTENT_Y + 69,
			86,
			22,
			Component.empty(),
			button -> {
				ClientCountdownPreferences.update(current -> current.withHighContrast(!current.highContrast()));
				refreshLabels();
			},
			Variant.NORMAL
		);
		this.volume = new ConsoleSlider(
			controlX,
			CONTENT_Y + 123,
			86,
			22,
			ClientCountdownPreferences.snapshot().volume(),
			value -> ClientCountdownPreferences.update(current -> current.withVolume((float)value))
		);
		this.addRenderableWidget(this.motion);
		this.addRenderableWidget(this.contrast);
		this.addRenderableWidget(this.volume);
		this.previewStateControl = new ConsoleButton(
			RIGHT_X + RIGHT_WIDTH - 104,
			CONTENT_Y + 7,
			94,
			18,
			Component.empty(),
			button -> {
				this.previewState = this.previewState.next();
				this.previewOriginNanos = System.nanoTime();
				this.previewCycle = Long.MIN_VALUE;
				this.previewSettleGeneration++;
				this.previewRenderer.reset();
				refreshLabels();
			},
			Variant.NORMAL
		);
		this.previewStateControl.setTooltip(Tooltip.create(
			Component.translatable("pixel_tzz_pro.settings.countdown.preview.state.tooltip")
		));
		this.addRenderableWidget(this.previewStateControl);

		ConsoleButton reset = new ConsoleButton(
			PANEL_X + 14,
			PANEL_Y + PANEL_HEIGHT - 34,
			118,
			22,
			Component.translatable("pixel_tzz_pro.settings.countdown.reset"),
			button -> {
				ClientCountdownPreferences.resetDefaults();
				this.previewRenderer.reset();
				refreshLabels();
			},
			Variant.NORMAL
		);
		reset.setTooltip(Tooltip.create(Component.translatable("pixel_tzz_pro.settings.countdown.reset.tooltip")));
		this.addRenderableWidget(reset);
		ConsoleButton done = new ConsoleButton(
			PANEL_X + PANEL_WIDTH - 132,
			PANEL_Y + PANEL_HEIGHT - 34,
			118,
			22,
			Component.translatable("pixel_tzz_pro.settings.back_to_categories"),
			button -> onClose(),
			Variant.PRIMARY
		);
		this.addRenderableWidget(done);
		this.setInitialFocus(done);
		refreshLabels();
	}

	private void refreshLabels() {
		var preferences = ClientCountdownPreferences.snapshot();
		this.motion.setMessage(Component.translatable(
			"pixel_tzz_pro.settings.countdown.motion." + preferences.motion().name().toLowerCase(java.util.Locale.ROOT)
		).withColor(INFO_CYAN));
		this.contrast.setMessage(toggle(preferences.highContrast()));
		this.volume.setActualValue(preferences.volume());
		this.previewStateControl.setMessage(Component.translatable(
			"pixel_tzz_pro.settings.countdown.preview.state." + this.previewState.key
		).withColor(this.previewState == PreviewState.RUNNING ? SUCCESS : INFO_CYAN));
	}

	private static Component toggle(final boolean enabled) {
		return Component.translatable("pixel_tzz_pro.settings.toggle." + (enabled ? "on" : "off"))
			.withColor(enabled ? SUCCESS : SECONDARY_TEXT);
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
			drawPanel(graphics);
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

	private void drawPanel(final GuiGraphicsExtractor graphics) {
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, PANEL);
		graphics.outline(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + 4, PANEL_Y + PANEL_HEIGHT, BRAND_GOLD);
		graphics.fill(PANEL_X + 4, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + 2, INFO_CYAN);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown.eyebrow"),
			PANEL_X + 18,
			PANEL_Y + 15,
			INFO_CYAN,
			false
		);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown"),
			PANEL_X + 18,
			PANEL_Y + 35,
			MAIN_TEXT,
			true
		);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown.subtitle"),
			PANEL_X + 18,
			PANEL_Y + 55,
			PANEL_WIDTH - 36,
			SECONDARY_TEXT,
			false
		);

		drawControlCard(graphics, CONTENT_Y, "motion");
		drawControlCard(graphics, CONTENT_Y + 54, "contrast");
		drawControlCard(graphics, CONTENT_Y + 108, "volume");
		drawRequiredNotice(graphics);
		drawPreview(graphics);
	}

	private void drawControlCard(
		final GuiGraphicsExtractor graphics,
		final int y,
		final String key
	) {
		graphics.fill(LEFT_X, y, LEFT_X + LEFT_WIDTH, y + 48, SURFACE);
		graphics.outline(LEFT_X, y, LEFT_WIDTH, 48, SURFACE_BORDER);
		graphics.fill(LEFT_X, y, LEFT_X + 2, y + 48, INFO_CYAN);
		String prefix = "pixel_tzz_pro.settings.countdown." + key;
		graphics.text(this.font, Component.translatable(prefix), LEFT_X + 10, y + 8, MAIN_TEXT, true);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable(prefix + ".description"),
			LEFT_X + 10,
			y + 25,
			LEFT_WIDTH - 116,
			MUTED_TEXT,
			false
		);
	}

	private void drawRequiredNotice(final GuiGraphicsExtractor graphics) {
		int y = CONTENT_Y + 162;
		graphics.fill(LEFT_X, y, LEFT_X + LEFT_WIDTH, y + 30, 0xFF17212B);
		graphics.outline(LEFT_X, y, LEFT_WIDTH, 30, BRAND_GOLD);
		graphics.fill(LEFT_X, y, LEFT_X + 3, y + 30, BRAND_GOLD);
		graphics.text(this.font, "!", LEFT_X + 10, y + 10, BRAND_GOLD, true);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown.required"),
			LEFT_X + 23,
			y + 6,
			LEFT_WIDTH - 31,
			SECONDARY_TEXT,
			false
		);
	}

	private void drawPreview(final GuiGraphicsExtractor graphics) {
		int top = CONTENT_Y;
		int height = 192;
		graphics.fill(RIGHT_X, top, RIGHT_X + RIGHT_WIDTH, top + height, 0xFF101820);
		graphics.outline(RIGHT_X, top, RIGHT_WIDTH, height, SURFACE_BORDER);
		graphics.fill(RIGHT_X, top, RIGHT_X + RIGHT_WIDTH, top + 2, INFO_CYAN);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown.preview"),
			RIGHT_X + 10,
			top + 9,
			MAIN_TEXT,
			true
		);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown.preview.live"),
			RIGHT_X + 10,
			top + 24,
			MUTED_TEXT,
			false
		);

		long now = System.nanoTime();
		double remainingTicks = previewRemainingTicks(now);
		ClientCountdownSnapshot sample = previewSnapshot(now, this.previewState, remainingTicks);
		int actionBarY = top + height - 27;
		graphics.fill(RIGHT_X + 20, actionBarY, RIGHT_X + RIGHT_WIDTH - 20, actionBarY + 1, 0xFF2A3945);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.countdown.preview.actionbar"),
			RIGHT_X + (RIGHT_WIDTH - this.font.width(Component.translatable("pixel_tzz_pro.settings.countdown.preview.actionbar"))) / 2,
			actionBarY + 7,
			MUTED_TEXT,
			false
		);
		this.previewRenderer.renderFrame(
			graphics,
			this.font,
			new CountdownRenderer.Frame(
				sample,
				remainingTicks,
				this.previewSettleGeneration,
				1.0D,
				false
			),
			ClientCountdownPreferences.snapshot(),
			RIGHT_X + 4,
			RIGHT_X + RIGHT_WIDTH - 4,
			actionBarY - 3,
			now
		);
	}

	private double previewRemainingTicks(final long nowNanos) {
		if (this.previewState != PreviewState.RUNNING) {
			return this.previewState == PreviewState.COMPLETED ? 0.0D : 125.0D;
		}
		long elapsed = Math.max(0L, nowNanos - this.previewOriginNanos);
		long cycle = elapsed / 10_000_000_000L;
		if (cycle != this.previewCycle) {
			this.previewCycle = cycle;
			this.previewSettleGeneration++;
		}
		double seconds = 10.0D - (elapsed % 10_000_000_000L) / 1_000_000_000.0D;
		return seconds * 20.0D;
	}

	private static ClientCountdownSnapshot previewSnapshot(
		final long nowNanos,
		final PreviewState previewState,
		final double remainingTicks
	) {
		State state = previewState.state;
		boolean paused = state != State.RUNNING;
		return new ClientCountdownSnapshot(
			new Version(PREVIEW_GAME_ID, 1L, 1L, 1L, 1L),
			PREVIEW_COUNTDOWN_ID,
			Purpose.OPENING,
			200L,
			state,
			false,
			new Timing(0L, remainingTicks, paused ? 0.0D : -1.0D, paused),
			new Text(
				Component.literal("正式开局"),
				Optional.empty(),
				Optional.of(Component.literal("秒")),
				Component.literal("等待开始"),
				Component.literal("已暂停"),
				Component.literal("开始")
			),
			new Presentation(
				new TimePresentation(TimeFormat.SECONDS, TimePrecision.HUNDREDTHS, true, ":", 0xFFF4F7FA, 1.0D, true),
				new PanelPresentation(0xE0081018, 0xFF33424E, 0xFFF2C14E, 0.92D, 6, 3, 6, 236),
				new ProgressPresentation(ProgressMode.LINE, 0xFF42D9F5, 0xFF263641, 1, true),
				new DigitPresentation(
					DigitTransition.ROLL,
					RollDirection.DOWN,
					180,
					10,
					Easing.EASE_OUT_CUBIC,
					12,
					true,
					0xFFFFFFFF,
					0xFF92A6B4,
					1.0D,
					0.78D
				),
				new SurfaceMotion(SurfaceTransition.FADE, 160, Easing.EASE_OUT_CUBIC),
				new SurfaceMotion(SurfaceTransition.FADE, 140, Easing.LINEAR),
				new CompletionPresentation(600, 0xFFF2C14E)
			),
			Optional.<Checkpoint>empty(),
			nowNanos
		);
	}

	private enum PreviewState {
		RUNNING("running", State.RUNNING),
		PAUSED("paused", State.PAUSED),
		WAITING("waiting", State.WAITING),
		COMPLETED("completed", State.COMPLETED);

		private final String key;
		private final State state;

		PreviewState(final String key, final State state) {
			this.key = key;
			this.state = state;
		}

		private PreviewState next() {
			return switch (this) {
				case RUNNING -> PAUSED;
				case PAUSED -> WAITING;
				case WAITING -> COMPLETED;
				case COMPLETED -> RUNNING;
			};
		}
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return super.mouseClicked(referenceMouseEvent(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return super.mouseReleased(referenceMouseEvent(event));
	}

	@Override
	public boolean mouseDragged(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return super.mouseDragged(
			referenceMouseEvent(event),
			canvas.toReferenceLength(deltaX),
			canvas.toReferenceLength(deltaY)
		);
	}

	private MouseButtonEvent referenceMouseEvent(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return new MouseButtonEvent(
			canvas.toReferenceX(event.x()),
			canvas.toReferenceY(event.y()),
			event.buttonInfo()
		);
	}

	private ConsoleScreenViewport.Layout canvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private static final class ConsoleSlider extends AbstractSliderButton {
		private final DoubleConsumer change;

		private ConsoleSlider(
			final int x,
			final int y,
			final int width,
			final int height,
			final double initial,
			final DoubleConsumer change
		) {
			super(x, y, width, height, Component.empty(), Math.clamp(initial, 0.0D, 1.0D));
			this.change = change;
			updateMessage();
		}

		private void setActualValue(final double actual) {
			this.value = Math.clamp(actual, 0.0D, 1.0D);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(Math.round(this.value * 100.0D) + "%").withColor(INFO_CYAN));
		}

		@Override
		protected void applyValue() {
			this.change.accept(this.value);
		}

		@Override
		public void extractWidgetRenderState(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float tickProgress
		) {
			int border = isHovered() || isFocused() ? INFO_CYAN : SURFACE_BORDER;
			graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF202B35);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
			int trackLeft = getX() + 7;
			int trackRight = getRight() - 7;
			int trackY = getBottom() - 5;
			int handleX = trackLeft + (int)Math.round((trackRight - trackLeft) * this.value);
			graphics.fill(trackLeft, trackY, trackRight, trackY + 1, 0xFF364854);
			graphics.fill(trackLeft, trackY, handleX, trackY + 1, INFO_CYAN);
			graphics.fill(handleX - 2, trackY - 2, handleX + 3, trackY + 3, border);
			int textX = getX() + Math.max(0, (getWidth() - Minecraft.getInstance().font.width(getMessage())) / 2);
			graphics.text(Minecraft.getInstance().font, getMessage(), textX, getY() + 4, MAIN_TEXT, false);
			handleCursor(graphics);
		}
	}
}
