package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;

/**
 * Fits every mod-owned console surface to the exact logical canvas used by the established
 * 2560x1440, GUI-scale-4 acceptance setup.
 *
 * <p>The authored page remains 640x360. Smaller GUI surfaces uniformly scale that entire canvas;
 * larger surfaces center it without upscaling. This keeps typography, controls, spacing, motion,
 * clipping and hit testing on one shared coordinate system.</p>
 */
public final class ConsoleScreenViewport {
	public static final int REFERENCE_WIDTH = 640;
	public static final int REFERENCE_HEIGHT = 360;

	private ConsoleScreenViewport() {
	}

	public static Layout fit(final int screenWidth, final int screenHeight) {
		int safeWidth = Math.max(1, screenWidth);
		int safeHeight = Math.max(1, screenHeight);
		double requestedScale = Math.min(
			1.0,
			Math.min(
				safeWidth / (double)REFERENCE_WIDTH,
				safeHeight / (double)REFERENCE_HEIGHT
			)
		);
		int width = Math.max(1, (int)Math.floor(REFERENCE_WIDTH * requestedScale));
		int height = Math.max(1, (int)Math.floor(REFERENCE_HEIGHT * requestedScale));
		double scale = Math.min(
			width / (double)REFERENCE_WIDTH,
			height / (double)REFERENCE_HEIGHT
		);
		return new Layout(
			(safeWidth - width) / 2,
			(safeHeight - height) / 2,
			width,
			height,
			scale
		);
	}

	public record Layout(int x, int y, int width, int height, double scale) {
		public Layout {
			if (
				x < 0
					|| y < 0
					|| width <= 0
					|| height <= 0
					|| !Double.isFinite(scale)
					|| scale <= 0.0
					|| scale > 1.0
			) {
				throw new IllegalArgumentException("invalid console screen viewport");
			}
		}

		public Rect map(final Rect reference) {
			int left = this.x + scaleCoordinate(reference.x());
			int top = this.y + scaleCoordinate(reference.y());
			int right = this.x + scaleCoordinate(reference.right());
			int bottom = this.y + scaleCoordinate(reference.bottom());
			return new Rect(
				left,
				top,
				Math.max(1, right - left),
				Math.max(1, bottom - top)
			);
		}

		public double toReferenceX(final double screenX) {
			return (screenX - this.x) / this.scale;
		}

		public double toReferenceY(final double screenY) {
			return (screenY - this.y) / this.scale;
		}

		public double toReferenceLength(final double screenLength) {
			return screenLength / this.scale;
		}

		private int scaleCoordinate(final int coordinate) {
			return (int)Math.round(coordinate * this.scale);
		}
	}
}
