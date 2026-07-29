package io.github.zcpu954861.pixeltzzpro.client.ui.widget;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.DANGER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiStyleRuntime;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Reusable self-drawn control-console button that keeps vanilla input, focus,
 * tooltip, and narration behavior.
 */
public final class ConsoleButton extends Button {
	private static final float HOVER_ANIMATION_MILLIS = 220.0F;
	private static final long PRESSED_FEEDBACK_MILLIS = 110L;
	private static final int DEFAULT_ARROW_TRAVEL = 7;

	private final Variant variant;
	private final boolean playDefaultSound;
	private final StyleResolver styleResolver;
	private float hoverProgress;
	private float presentationScale = 1.0F;
	private boolean renderLabel = true;
	private long lastRenderMillis = Util.getMillis();
	private long pressedUntilMillis;

	public ConsoleButton(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final Button.OnPress onPress,
		final Variant variant
	) {
		this(x, y, width, height, message, onPress, variant, true);
	}

	public ConsoleButton(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final Button.OnPress onPress,
		final Variant variant,
		final boolean playDefaultSound
	) {
		this(x, y, width, height, message, onPress, variant, playDefaultSound, StyleResolver.DEFAULT);
	}

	public ConsoleButton(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final Button.OnPress onPress,
		final Variant variant,
		final boolean playDefaultSound,
		final StyleResolver styleResolver
	) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.variant = variant;
		this.playDefaultSound = playDefaultSound;
		this.styleResolver = Objects.requireNonNull(styleResolver, "styleResolver");
	}

	@Override
	public void playDownSound(final SoundManager soundManager) {
		if (this.playDefaultSound) {
			super.playDownSound(soundManager);
		}
	}

	@Override
	public void onPress(final InputWithModifiers input) {
		if (!this.active) {
			return;
		}
		this.pressedUntilMillis = Util.getMillis() + PRESSED_FEEDBACK_MILLIS;
		super.onPress(input);
	}

	@Override
	protected void extractContents(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		float scale = Math.clamp(this.presentationScale, 0.0F, 8.0F);
		if (scale <= 0.001F) {
			return;
		}
		if (Math.abs(scale - 1.0F) < 0.001F) {
			extractUnscaledContents(graphics, mouseX, mouseY, tickProgress);
			return;
		}
		int hitX = this.getX();
		int hitY = this.getY();
		int hitWidth = this.getWidth();
		int hitHeight = this.getHeight();
		float centerX = hitX + hitWidth / 2.0F;
		float centerY = hitY + hitHeight / 2.0F;
		int sourceWidth = Math.max(1, Math.round(hitWidth / scale));
		int sourceHeight = Math.max(1, Math.round(hitHeight / scale));
		this.setX(Math.round(centerX - sourceWidth / 2.0F));
		this.setY(Math.round(centerY - sourceHeight / 2.0F));
		this.setWidth(sourceWidth);
		this.setHeight(sourceHeight);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
		try {
			extractUnscaledContents(graphics, mouseX, mouseY, tickProgress);
		} finally {
			graphics.pose().popMatrix();
			this.setX(hitX);
			this.setY(hitY);
			this.setWidth(hitWidth);
			this.setHeight(hitHeight);
		}
	}

	private void extractUnscaledContents(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		float hoverAmount = updateHoverProgress(this.isHovered() && this.active);
		StyleState state = UiStyleRuntime.interactionState(
			this.active,
			Util.getMillis() < this.pressedUntilMillis,
			this.isHovered(),
			this.isFocused()
		);
		Appearance fallback = defaultAppearance(state);
		Appearance target = Objects.requireNonNullElse(
			this.styleResolver.resolve(state, fallback),
			fallback
		);
		Appearance appearance = state == StyleState.HOVER || (state == StyleState.NORMAL && hoverAmount > 0.0F)
			? interpolateAppearance(
				resolveAppearance(StyleState.NORMAL),
				resolveAppearance(StyleState.HOVER),
				hoverAmount
			)
			: target;
		float opacity = Math.clamp(appearance.opacity(), 0.0F, 1.0F);
		int background = withOpacity(appearance.background(), opacity);
		int border = withOpacity(appearance.border(), opacity);
		int textColor = withOpacity(appearance.text(), opacity);

		graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), background);
		drawHoverSweep(graphics, hoverAmount, appearance.accent(), opacity);
		int borderWidth = Math.clamp(appearance.borderWidth(), 0, 4);
		for (int index = 0; index < borderWidth; index++) {
			graphics.outline(
				this.getX() + index,
				this.getY() + index,
				Math.max(0, this.getWidth() - index * 2),
				Math.max(0, this.getHeight() - index * 2),
				border
			);
		}
		if (this.variant == Variant.PRIMARY || this.variant == Variant.DANGER) {
			int railWidth = 3 + Math.round(2.0F * hoverAmount);
			graphics.fill(
				this.getX(),
				this.getY(),
				this.getX() + railWidth,
				this.getBottom(),
				withOpacity(appearance.accent(), opacity)
			);
		}
		drawSemanticFrame(graphics, hoverAmount, appearance.accent(), opacity);
		float highlightAmount = state == StyleState.HOVER
			? hoverAmount
			: state == StyleState.FOCUSED || state == StyleState.PRESSED ? 1.0F : 0.0F;
		if (highlightAmount > 0.0F && this.active) {
			graphics.fill(
				this.getX() + 1,
				this.getY() + 1,
				this.getRight() - 1,
				this.getY() + 2,
				withAlpha(border, Math.round(150.0F * opacity * highlightAmount))
			);
		}

		if (this.renderLabel) {
			ActiveTextCollector output = graphics.textRendererForWidget(
				this,
				GuiGraphicsExtractor.HoveredTextEffects.NONE
			);
			output.defaultParameters(
				output.defaultParameters().withOpacity(output.defaultParameters().opacity() * opacity)
			);
			var label = this.getMessage().copy().withColor(textColor);
			if (!appearance.shadow()) {
				label.withoutShadow();
			}
			output.acceptScrollingWithDefaultCenter(
				label,
				this.getX() + 8,
				this.getRight() - 8,
				this.getY(),
				this.getBottom()
			);
		}
		drawHoverArrows(
			graphics,
			hoverAmount,
			appearance.navigationArrows(),
			appearance.arrowTravel(),
			appearance.accent(),
			opacity
		);
	}

	public void setPresentationScale(final float scale) {
		this.presentationScale = Float.isFinite(scale)
			? Math.clamp(scale, 0.0F, 8.0F)
			: 1.0F;
	}

	/**
	 * Keeps the button's message for narration while allowing a richer caller-drawn label.
	 */
	public void setRenderLabel(final boolean renderLabel) {
		this.renderLabel = renderLabel;
	}

	private Appearance resolveAppearance(final StyleState state) {
		Appearance fallback = defaultAppearance(state);
		return Objects.requireNonNullElse(this.styleResolver.resolve(state, fallback), fallback);
	}

	private Appearance defaultAppearance(final StyleState state) {
		boolean disabled = state == StyleState.DISABLED;
		boolean highlighted = state != StyleState.NORMAL && !disabled;
		int background = disabled
			? 0xFF151E27
			: switch (this.variant) {
				case PRIMARY -> highlighted ? 0xFF3A3525 : 0xFF2B2B24;
				case DANGER -> highlighted ? 0xFF48252C : 0xFF322329;
				case NORMAL, NAVIGATION -> highlighted ? 0xFF2B3A46 : 0xFF202B35;
			};
		int border = disabled
			? 0xFF2B3945
			: switch (this.variant) {
				case PRIMARY -> highlighted ? BRAND_GOLD : 0xFF8E793E;
				case DANGER -> highlighted ? DANGER : 0xFF82404A;
				case NORMAL, NAVIGATION -> highlighted ? INFO_CYAN : SURFACE_BORDER;
			};
		int accent = disabled
			? MUTED_TEXT
			: this.variant == Variant.DANGER ? DANGER : this.variant == Variant.PRIMARY ? BRAND_GOLD : INFO_CYAN;
		return new Appearance(
			background,
			border,
			disabled ? MUTED_TEXT : highlighted ? 0xFFFFFFFF : MAIN_TEXT,
			accent,
			1,
			1.0F,
			true,
			this.variant == Variant.NAVIGATION,
			DEFAULT_ARROW_TRAVEL
		);
	}

	private void drawHoverArrows(
		final GuiGraphicsExtractor graphics,
		final float hoverAmount,
		final boolean enabled,
		final int configuredTravel,
		final int configuredColor,
		final float opacity
	) {
		if (!enabled || hoverAmount <= 0.0F) {
			return;
		}

		int alpha = Math.round(255.0F * hoverAmount * opacity);
		int travel = Math.round(Math.clamp(configuredTravel, 0, 32) * hoverAmount);
		int centerX = (this.getX() + this.getRight()) / 2;
		int labelWidth = Minecraft.getInstance().font.width(this.getMessage());
		int arrowY = (this.getY() + this.getBottom() - 9) / 2 + 1;
		int arrowColor = withAlpha(configuredColor, alpha);

		graphics.text(
			Minecraft.getInstance().font,
			">",
			centerX - labelWidth / 2 - 8 - travel,
			arrowY,
			arrowColor,
			false
		);
		graphics.text(
			Minecraft.getInstance().font,
			"<",
			centerX + (labelWidth + 1) / 2 + 2 + travel,
			arrowY,
			arrowColor,
			false
		);
	}

	private float updateHoverProgress(final boolean hovered) {
		long now = Util.getMillis();
		float elapsed = Math.min(50L, Math.max(0L, now - this.lastRenderMillis));
		this.lastRenderMillis = now;
		float step = elapsed / HOVER_ANIMATION_MILLIS;
		this.hoverProgress = hovered
			? Math.min(1.0F, this.hoverProgress + step)
			: Math.max(0.0F, this.hoverProgress - step);
		return smoothStep(this.hoverProgress);
	}

	private void drawHoverSweep(
		final GuiGraphicsExtractor graphics,
		final float amount,
		final int accent,
		final float opacity
	) {
		if (!this.active || amount <= 0.0F || this.getWidth() < 12 || this.getHeight() < 6) {
			return;
		}
		int sweepWidth = Math.clamp(this.getWidth() / 9, 7, 18);
		int innerWidth = Math.max(0, this.getWidth() - 2);
		int sweepX = this.getX() + 1 - sweepWidth
			+ Math.round((innerWidth + sweepWidth) * amount);
		int top = this.getY() + 2;
		int bottom = this.getBottom() - 2;
		fillClipped(
			graphics,
			sweepX,
			top,
			sweepX + sweepWidth / 3,
			bottom,
			withAlpha(accent, Math.round(12.0F * opacity))
		);
		fillClipped(
			graphics,
			sweepX + sweepWidth / 3,
			top,
			sweepX + sweepWidth * 2 / 3,
			bottom,
			withAlpha(accent, Math.round(28.0F * opacity))
		);
		fillClipped(
			graphics,
			sweepX + sweepWidth * 2 / 3,
			top,
			sweepX + sweepWidth,
			bottom,
			withAlpha(accent, Math.round(12.0F * opacity))
		);
	}

	private void fillClipped(
		final GuiGraphicsExtractor graphics,
		final int left,
		final int top,
		final int right,
		final int bottom,
		final int color
	) {
		int clippedLeft = Math.max(this.getX() + 1, left);
		int clippedRight = Math.min(this.getRight() - 1, right);
		if (clippedRight > clippedLeft && bottom > top) {
			graphics.fill(clippedLeft, top, clippedRight, bottom, color);
		}
	}

	private void drawSemanticFrame(
		final GuiGraphicsExtractor graphics,
		final float amount,
		final int accent,
		final float opacity
	) {
		if (!this.active || this.getWidth() < 8 || this.getHeight() < 5) {
			return;
		}
		int accentColor = withAlpha(accent, Math.round((112.0F + 110.0F * amount) * opacity));
		int lineY = this.getBottom() - 2;
		if (this.variant == Variant.PRIMARY || this.variant == Variant.DANGER) {
			int start = this.getX() + 3;
			int length = Math.round(Math.max(0, this.getWidth() - 6) * amount);
			graphics.fill(start, lineY, start + length, lineY + 1, accentColor);
			return;
		}
		if (this.variant == Variant.NORMAL) {
			int center = (this.getX() + this.getRight()) / 2;
			int half = Math.round(Math.max(0, this.getWidth() - 6) * amount / 2.0F);
			graphics.fill(center - half, lineY, center + half, lineY + 1, accentColor);
			int cornerLength = 4 + Math.round(4.0F * amount);
			graphics.fill(
				this.getX() + 1,
				this.getY() + 1,
				this.getX() + 1 + cornerLength,
				this.getY() + 2,
				accentColor
			);
			graphics.fill(
				this.getRight() - 1 - cornerLength,
				this.getBottom() - 2,
				this.getRight() - 1,
				this.getBottom() - 1,
				accentColor
			);
		}
	}

	private static Appearance interpolateAppearance(
		final Appearance from,
		final Appearance to,
		final float amount
	) {
		return new Appearance(
			UiMotionRuntime.interpolateColor(from.background(), to.background(), amount),
			UiMotionRuntime.interpolateColor(from.border(), to.border(), amount),
			UiMotionRuntime.interpolateColor(from.text(), to.text(), amount),
			UiMotionRuntime.interpolateColor(from.accent(), to.accent(), amount),
			Math.round(from.borderWidth() + (to.borderWidth() - from.borderWidth()) * amount),
			from.opacity() + (to.opacity() - from.opacity()) * amount,
			amount < 0.5F ? from.shadow() : to.shadow(),
			from.navigationArrows() || to.navigationArrows(),
			Math.round(from.arrowTravel() + (to.arrowTravel() - from.arrowTravel()) * amount)
		);
	}

	private static float smoothStep(final float value) {
		float clamped = Math.clamp(value, 0.0F, 1.0F);
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}

	private static int withOpacity(final int color, final float opacity) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * Math.clamp(opacity, 0.0F, 1.0F));
		return withAlpha(color, alpha);
	}

	@FunctionalInterface
	public interface StyleResolver {
		StyleResolver DEFAULT = (state, fallback) -> fallback;

		Appearance resolve(StyleState state, Appearance fallback);
	}

	public record Appearance(
		int background,
		int border,
		int text,
		int accent,
		int borderWidth,
		float opacity,
		boolean shadow,
		boolean navigationArrows,
		int arrowTravel
	) {
	}

	public enum Variant {
		PRIMARY,
		NORMAL,
		NAVIGATION,
		DANGER
	}
}
