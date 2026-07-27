package io.github.zcpu954861.pixeltzzpro.ui.runtime;

/**
 * Pure row-window calculation shared by list and responsive-grid Repeat rendering.
 */
public final class RepeatVirtualization {
	private RepeatVirtualization() {
	}

	public static Window verticalWindow(
		final int itemCount,
		final int columnCount,
		final int estimatedRowStride,
		final int rowGap,
		final int scrollOffset,
		final int viewportExtent,
		final int overscanRows
	) {
		int count = Math.max(0, itemCount);
		int columns = Math.max(1, columnCount);
		int stride = Math.max(1, estimatedRowStride);
		int gap = Math.clamp(rowGap, 0, stride);
		int rows = Math.ceilDiv(count, columns);
		if (rows == 0) {
			return new Window(0, 0, 0, 0);
		}
		int overscan = Math.max(0, overscanRows);
		int firstRow = Math.max(0, Math.max(0, scrollOffset) / stride - overscan);
		int visibleRows = Math.ceilDiv(Math.max(0, viewportExtent), stride) + overscan * 2;
		int endRow = Math.min(rows, firstRow + Math.max(1, visibleRows));
		int leadingExtent = firstRow == 0 ? 0 : Math.max(0, firstRow * stride - gap);
		int remainingRows = Math.max(0, rows - endRow);
		int trailingExtent = remainingRows == 0
			? 0
			: Math.max(0, remainingRows * stride - gap);
		return new Window(
			Math.min(count, firstRow * columns),
			Math.min(count, endRow * columns),
			leadingExtent,
			trailingExtent
		);
	}

	public record Window(
		int startItem,
		int endItem,
		int leadingExtent,
		int trailingExtent
	) {
	}
}
