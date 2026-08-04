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

import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessagePreferences;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessagePreferences.Snapshot;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Compact local accessibility surface for V3B presentations.
 *
 * <p>The screen deliberately contains only presentation preferences. It cannot choose message
 * content, audience, targets, fields or callbacks, and every option preserves the final text.</p>
 */
public final class MessageAccessibilityScreen extends Screen {
	private static final int PANEL_X = 54;
	private static final int PANEL_Y = 28;
	private static final int PANEL_WIDTH = 532;
	private static final int PANEL_HEIGHT = 304;
	private static final int CARD_WIDTH = 248;
	private static final int CARD_HEIGHT = 54;
	private static final DecimalFormat SPEED_FORMAT = new DecimalFormat(
		"0.##",
		DecimalFormatSymbols.getInstance(Locale.ROOT)
	);

	private final Screen parent;
	private ConsoleSlider speed;
	private ConsoleButton reducedMotion;
	private ConsoleButton characterSound;
	private ConsoleSlider characterVolume;
	private ConsoleButton cursorBlink;
	private ConsoleButton highContrast;

	public MessageAccessibilityScreen(final Screen parent) {
		super(Component.translatable("pixel_tzz_pro.settings.text_effects"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientMessagePreferences.initialize();
		int left = PANEL_X + 14;
		int right = PANEL_X + PANEL_WIDTH / 2 + 4;
		int buttonWidth = 92;
		int buttonXLeft = left + CARD_WIDTH - buttonWidth - 10;
		int buttonXRight = right + CARD_WIDTH - buttonWidth - 10;
		int firstY = 105;
		int secondY = 165;
		int thirdY = 225;

		this.speed = new ConsoleSlider(
			buttonXLeft,
			firstY + 16,
			buttonWidth,
			22,
			0.5D,
			3.0D,
			ClientMessagePreferences.snapshot().animationSpeed(),
			value -> Component.literal(SPEED_FORMAT.format(value) + "×")
				.withColor(INFO_CYAN),
			value -> ClientMessagePreferences.update(
				current -> current.withAnimationSpeed((float)value)
			)
		);
		this.reducedMotion = valueButton(
			buttonXRight,
			firstY + 16,
			buttonWidth,
			() -> ClientMessagePreferences.update(
				current -> current.withReducedMotion(!current.reducedMotion())
			)
		);
		this.characterSound = valueButton(
			buttonXLeft,
			secondY + 16,
			buttonWidth,
			() -> ClientMessagePreferences.update(
				current -> current.withCharacterSound(!current.characterSound())
			)
		);
		this.characterVolume = new ConsoleSlider(
			buttonXRight,
			secondY + 16,
			buttonWidth,
			22,
			0.0D,
			1.0D,
			ClientMessagePreferences.snapshot().characterSoundVolume(),
			value -> Component.literal(Math.round(value * 100.0D) + "%")
				.withColor(
					ClientMessagePreferences.snapshot().characterSound()
						? INFO_CYAN
						: MUTED_TEXT
				),
			value -> ClientMessagePreferences.update(
				current -> current.withCharacterSoundVolume((float)value)
			)
		);
		this.cursorBlink = valueButton(
			buttonXLeft,
			thirdY + 16,
			buttonWidth,
			() -> ClientMessagePreferences.update(
				current -> current.withCursorBlink(!current.cursorBlink())
			)
		);
		this.highContrast = valueButton(
			buttonXRight,
			thirdY + 16,
			buttonWidth,
			() -> ClientMessagePreferences.update(
				current -> current.withHighContrast(!current.highContrast())
			)
		);
		this.addRenderableWidget(this.speed);
		this.addRenderableWidget(this.reducedMotion);
		this.addRenderableWidget(this.characterSound);
		this.addRenderableWidget(this.characterVolume);
		this.addRenderableWidget(this.cursorBlink);
		this.addRenderableWidget(this.highContrast);

		ConsoleButton reset = new ConsoleButton(
			PANEL_X + 14,
			PANEL_Y + PANEL_HEIGHT - 34,
			118,
			22,
			Component.translatable("pixel_tzz_pro.settings.text_effects.reset"),
			button -> {
				ClientMessagePreferences.resetDefaults();
				refreshLabels();
			},
			Variant.NORMAL
		);
		reset.setTooltip(
			Tooltip.create(
				Component.translatable("pixel_tzz_pro.settings.text_effects.reset.tooltip")
			)
		);
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

	private ConsoleButton valueButton(
		final int x,
		final int y,
		final int width,
		final Runnable change
	) {
		return new ConsoleButton(
			x,
			y,
			width,
			22,
			Component.empty(),
			button -> {
				change.run();
				refreshLabels();
			},
			Variant.NORMAL
		);
	}

	private void refreshLabels() {
		Snapshot current = ClientMessagePreferences.snapshot();
		this.speed.setActualValue(current.animationSpeed());
		this.reducedMotion.setMessage(toggle(current.reducedMotion()));
		this.characterSound.setMessage(toggle(current.characterSound()));
		this.characterVolume.setActualValue(current.characterSoundVolume());
		this.characterVolume.active = current.characterSound();
		this.cursorBlink.setMessage(toggle(current.cursorBlink()));
		this.highContrast.setMessage(toggle(current.highContrast()));
	}

	private static Component toggle(final boolean enabled) {
		return Component.translatable(
			"pixel_tzz_pro.settings.toggle." + (enabled ? "on" : "off")
		)
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
		graphics.fill(
			PANEL_X,
			PANEL_Y,
			PANEL_X + PANEL_WIDTH,
			PANEL_Y + PANEL_HEIGHT,
			PANEL
		);
		graphics.outline(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + 4, PANEL_Y + PANEL_HEIGHT, BRAND_GOLD);
		graphics.fill(PANEL_X + 4, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + 2, INFO_CYAN);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.text_effects.eyebrow"),
			PANEL_X + 18,
			PANEL_Y + 15,
			INFO_CYAN,
			false
		);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.text_effects"),
			PANEL_X + 18,
			PANEL_Y + 35,
			MAIN_TEXT,
			true
		);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.text_effects.subtitle"),
			PANEL_X + 18,
			PANEL_Y + 55,
			PANEL_WIDTH - 36,
			SECONDARY_TEXT,
			false
		);

		int left = PANEL_X + 14;
		int right = PANEL_X + PANEL_WIDTH / 2 + 4;
		drawCard(graphics, left, 105, "animation_speed");
		drawCard(graphics, right, 105, "reduced_motion");
		drawCard(graphics, left, 165, "character_sound");
		drawCard(graphics, right, 165, "character_volume");
		drawCard(graphics, left, 225, "cursor_blink");
		drawCard(graphics, right, 225, "high_contrast");
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.text_effects.key_hint"),
			PANEL_X + 18,
			PANEL_Y + PANEL_HEIGHT - 47,
			MUTED_TEXT,
			false
		);
	}

	private void drawCard(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final String key
	) {
		graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, SURFACE);
		graphics.outline(x, y, CARD_WIDTH, CARD_HEIGHT, SURFACE_BORDER);
		graphics.fill(x, y, x + 2, y + CARD_HEIGHT, INFO_CYAN);
		String prefix = "pixel_tzz_pro.settings.text_effects." + key;
		graphics.text(
			this.font,
			Component.translatable(prefix),
			x + 10,
			y + 9,
			MAIN_TEXT,
			true
		);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable(prefix + ".description"),
			x + 10,
			y + 29,
			CARD_WIDTH - 120,
			MUTED_TEXT,
			false
		);
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
		private final double minimum;
		private final double maximum;
		private final java.util.function.DoubleFunction<Component> label;
		private final DoubleConsumer change;

		private ConsoleSlider(
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
			super(
				x,
				y,
				width,
				height,
				Component.empty(),
				normalize(initial, minimum, maximum)
			);
			this.minimum = minimum;
			this.maximum = maximum;
			this.label = label;
			this.change = change;
			updateMessage();
		}

		private void setActualValue(final double actual) {
			this.value = normalize(actual, this.minimum, this.maximum);
			updateMessage();
		}

		private double actualValue() {
			return this.minimum + (this.maximum - this.minimum) * this.value;
		}

		@Override
		protected void updateMessage() {
			if (this.label != null) {
				setMessage(this.label.apply(actualValue()));
			}
		}

		@Override
		protected void applyValue() {
			if (this.change != null) {
				this.change.accept(actualValue());
			}
		}

		@Override
		public void extractWidgetRenderState(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float tickProgress
		) {
			int border = this.active && (isHovered() || isFocused())
				? INFO_CYAN
				: SURFACE_BORDER;
			int background = this.active ? 0xFF202B35 : 0xFF151E27;
			graphics.fill(getX(), getY(), getRight(), getBottom(), background);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
			int trackLeft = getX() + 7;
			int trackRight = getRight() - 7;
			int trackY = getBottom() - 5;
			int handleX = trackLeft + (int)Math.round((trackRight - trackLeft) * this.value);
			graphics.fill(trackLeft, trackY, trackRight, trackY + 1, 0xFF364854);
			graphics.fill(trackLeft, trackY, handleX, trackY + 1, INFO_CYAN);
			graphics.fill(handleX - 2, trackY - 2, handleX + 3, trackY + 3, border);

			var font = Minecraft.getInstance().font;
			int textX = getX() + Math.max(0, (getWidth() - font.width(getMessage())) / 2);
			graphics.text(
				font,
				getMessage(),
				textX,
				getY() + 4,
				this.active ? MAIN_TEXT : MUTED_TEXT,
				false
			);
			handleCursor(graphics);
		}

		private static double normalize(
			final double value,
			final double minimum,
			final double maximum
		) {
			if (
				!Double.isFinite(value)
					|| !Double.isFinite(minimum)
					|| !Double.isFinite(maximum)
					|| maximum <= minimum
			) {
				throw new IllegalArgumentException("console slider range is invalid");
			}
			return Math.clamp((value - minimum) / (maximum - minimum), 0.0D, 1.0D);
		}
	}
}
