package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;

/**
 * Fits the ordinary-player terminal's fixed reference canvas into the current GUI.
 *
 * <p>The data-pack page is always laid out against the same {@value #REFERENCE_WIDTH} by
 * {@value #REFERENCE_HEIGHT} shell. This is the exact logical shell previously rendered by the
 * 2560x1440 acceptance client at GUI scale 4: its 640x360 GUI leaves the established ten-pixel
 * outer margin on every side. Smaller windows scale that complete 2K-authored shell uniformly
 * instead of changing its aspect ratio or reflowing the registered page. Larger windows never
 * upscale it, preserving the accepted control, type and spacing proportions.</p>
 */
public final class TerminalShellViewport {
	public static final int REFERENCE_WIDTH = 620;
	public static final int REFERENCE_HEIGHT = 340;
	public static final int SAFE_MARGIN = 10;
	public static final int CONTENT_INSET = 12;

	private TerminalShellViewport() {
	}

	public static Layout fit(final int screenWidth, final int screenHeight) {
		int safeScreenWidth = Math.max(1, screenWidth);
		int safeScreenHeight = Math.max(1, screenHeight);
		int availableWidth = Math.max(1, safeScreenWidth - SAFE_MARGIN * 2);
		int availableHeight = Math.max(1, safeScreenHeight - SAFE_MARGIN * 2);
		double scale = Math.min(
			1.0,
			Math.min(
				availableWidth / (double)REFERENCE_WIDTH,
				availableHeight / (double)REFERENCE_HEIGHT
			)
		);
		int width = Math.max(
			1,
			Math.min(safeScreenWidth, (int)Math.floor(REFERENCE_WIDTH * scale))
		);
		int height = Math.max(
			1,
			Math.min(safeScreenHeight, (int)Math.floor(REFERENCE_HEIGHT * scale))
		);
		return new Layout(
			(safeScreenWidth - width) / 2,
			(safeScreenHeight - height) / 2,
			width,
			height,
			Math.min(width / (double)REFERENCE_WIDTH, height / (double)REFERENCE_HEIGHT)
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
				throw new IllegalArgumentException("invalid terminal shell viewport");
			}
		}

		/**
		 * Maps a reference-canvas rectangle to its real hit/render rectangle.
		 */
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

		public Rect pageViewport(final int headerHeight, final int footerHeight) {
			if (
				headerHeight < 0
					|| footerHeight < 0
					|| headerHeight + footerHeight >= REFERENCE_HEIGHT
			) {
				throw new IllegalArgumentException("invalid terminal chrome height");
			}
			return map(
				new Rect(
					CONTENT_INSET,
					headerHeight,
					REFERENCE_WIDTH - CONTENT_INSET * 2,
					REFERENCE_HEIGHT - headerHeight - footerHeight
				)
			);
		}

		public int scaleLength(final int referenceLength) {
			if (referenceLength < 0) {
				throw new IllegalArgumentException("reference length must be non-negative");
			}
			return Math.max(0, scaleCoordinate(referenceLength));
		}

		private int scaleCoordinate(final int coordinate) {
			return (int)Math.round(coordinate * this.scale);
		}
	}
}
