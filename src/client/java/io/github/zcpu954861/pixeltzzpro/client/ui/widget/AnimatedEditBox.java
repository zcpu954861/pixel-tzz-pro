package io.github.zcpu954861.pixeltzzpro.client.ui.widget;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;

import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * EditBox whose presentation follows a page-node transform while vanilla input keeps the transformed
 * hit rectangle.
 */
public final class AnimatedEditBox extends EditBox {
	private float presentationScale = 1.0F;
	private OptionalInt motionColor = OptionalInt.empty();
	private int baseTextColor = DEFAULT_TEXT_COLOR;
	private int baseUneditableTextColor = DEFAULT_TEXT_COLOR;
	private boolean usingSourceBounds;
	private boolean consoleChrome;

	public AnimatedEditBox(
		final Font font,
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message
	) {
		super(font, x, y, width, height, message);
	}

	public void setPresentationScale(final float scale) {
		this.presentationScale = Float.isFinite(scale)
			? Math.clamp(scale, 0.0F, 8.0F)
			: 1.0F;
	}

	public void setMotionColor(final OptionalInt color) {
		this.motionColor = Objects.requireNonNull(color, "color");
		applyTextColors();
	}

	public void enableConsoleChrome() {
		this.consoleChrome = true;
		this.setBordered(false);
	}

	@Override
	public void setTextColor(final int color) {
		this.baseTextColor = color;
		applyTextColors();
	}

	@Override
	public void setTextColorUneditable(final int color) {
		this.baseUneditableTextColor = color;
		applyTextColors();
	}

	private void applyTextColors() {
		OptionalInt effectiveMotion = this.motionColor == null
			? OptionalInt.empty()
			: this.motionColor;
		super.setTextColor(effectiveMotion.orElse(this.baseTextColor));
		super.setTextColorUneditable(effectiveMotion.orElse(this.baseUneditableTextColor));
	}

	@Override
	public void extractWidgetRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		float scale = this.presentationScale;
		if (scale <= 0.001F) {
			return;
		}
		if (Math.abs(scale - 1.0F) < 0.001F) {
			extractUnscaledWidget(graphics, mouseX, mouseY, tickProgress);
			return;
		}
		Rect hit = bounds();
		Rect source = UiMotionRuntime.presentationSource(hit, scale);
		float centerX = hit.x() + hit.width() / 2.0F;
		float centerY = hit.y() + hit.height() / 2.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
		try {
			withBounds(
				source,
				() -> extractUnscaledWidget(graphics, mouseX, mouseY, tickProgress)
			);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private void extractUnscaledWidget(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		if (this.consoleChrome) {
			graphics.fill(
				this.getX(),
				this.getY(),
				this.getX() + this.getWidth(),
				this.getY() + this.getHeight(),
				SURFACE
			);
			graphics.outline(
				this.getX(),
				this.getY(),
				this.getWidth(),
				this.getHeight(),
				this.isFocused() ? INFO_CYAN : SURFACE_BORDER
			);
			graphics.fill(
				this.getX(),
				this.getY(),
				this.getX() + (this.isFocused() ? 3 : 2),
				this.getY() + this.getHeight(),
				this.isFocused() ? INFO_CYAN : SURFACE_BORDER
			);
			withBounds(
				contentBounds(bounds()),
				() -> super.extractWidgetRenderState(graphics, mouseX, mouseY, tickProgress)
			);
			return;
		}
		super.extractWidgetRenderState(graphics, mouseX, mouseY, tickProgress);
	}

	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		Rect hit = bounds();
		Rect source = UiMotionRuntime.presentationSource(hit, this.presentationScale);
		MouseButtonEvent mapped = mapToSource(event, hit, this.presentationScale);
		withBounds(contentBounds(source), () -> super.onClick(mapped, doubleClick));
	}

	@Override
	protected void onDrag(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		Rect hit = bounds();
		Rect source = UiMotionRuntime.presentationSource(hit, this.presentationScale);
		MouseButtonEvent mapped = mapToSource(event, hit, this.presentationScale);
		double scale = safeScale(this.presentationScale);
		withBounds(
			contentBounds(source),
			() -> super.onDrag(mapped, deltaX / scale, deltaY / scale)
		);
	}

	@Override
	public int getInnerWidth() {
		int inherited = super.getInnerWidth();
		if (this.usingSourceBounds) {
			return inherited;
		}
		int inset = this.consoleChrome ? 14 : Math.max(0, this.getWidth() - inherited);
		if (Math.abs(this.presentationScale - 1.0F) < 0.001F) {
			return Math.max(0, this.getWidth() - inset);
		}
		Rect source = UiMotionRuntime.presentationSource(bounds(), this.presentationScale);
		return Math.max(0, source.width() - inset);
	}

	@Override
	public int getScreenX(final int characterIndex) {
		if (this.usingSourceBounds) {
			return super.getScreenX(characterIndex);
		}
		if (Math.abs(this.presentationScale - 1.0F) < 0.001F) {
			return withBounds(
				contentBounds(bounds()),
				() -> super.getScreenX(characterIndex)
			);
		}
		Rect hit = bounds();
		Rect source = UiMotionRuntime.presentationSource(hit, this.presentationScale);
		int logical = withBounds(
			contentBounds(source),
			() -> super.getScreenX(characterIndex)
		);
		double center = hit.x() + hit.width() / 2.0;
		return safeRound(center + (logical - center) * safeScale(this.presentationScale));
	}

	private Rect bounds() {
		return new Rect(this.getX(), this.getY(), this.getWidth(), this.getHeight());
	}

	private Rect contentBounds(final Rect bounds) {
		if (!this.consoleChrome) {
			return bounds;
		}
		int textHeight = Math.min(8, bounds.height());
		return new Rect(
			bounds.x() + 7,
			bounds.y() + Math.max(0, bounds.height() - textHeight) / 2,
			Math.max(1, bounds.width() - 14),
			Math.max(1, textHeight)
		);
	}

	private static MouseButtonEvent mapToSource(
		final MouseButtonEvent event,
		final Rect hit,
		final float presentationScale
	) {
		double scale = safeScale(presentationScale);
		double centerX = hit.x() + hit.width() / 2.0;
		double centerY = hit.y() + hit.height() / 2.0;
		return new MouseButtonEvent(
			centerX + (event.x() - centerX) / scale,
			centerY + (event.y() - centerY) / scale,
			event.buttonInfo()
		);
	}

	private void withBounds(final Rect bounds, final Runnable operation) {
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();
		this.usingSourceBounds = true;
		setBounds(bounds);
		try {
			operation.run();
		} finally {
			setBounds(new Rect(x, y, width, height));
			this.usingSourceBounds = false;
		}
	}

	private int withBounds(final Rect bounds, final IntSupplier operation) {
		int[] result = new int[1];
		withBounds(bounds, (Runnable)() -> result[0] = operation.getAsInt());
		return result[0];
	}

	private void setBounds(final Rect bounds) {
		this.setWidth(Math.max(1, bounds.width()));
		this.setHeight(Math.max(1, bounds.height()));
		this.setX(bounds.x());
		this.setY(bounds.y());
	}

	private static double safeScale(final float scale) {
		return Float.isFinite(scale) && scale > 0.001F ? scale : 1.0;
	}

	private static int safeRound(final double value) {
		long rounded = Math.round(Double.isFinite(value) ? value : 0.0);
		return (int)Math.clamp(rounded, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}
}
