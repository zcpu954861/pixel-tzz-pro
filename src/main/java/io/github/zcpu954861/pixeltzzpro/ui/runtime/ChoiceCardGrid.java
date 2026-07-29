package io.github.zcpu954861.pixeltzzpro.ui.runtime;

/**
 * Client-neutral responsive grid rules for data-driven choice cards.
 *
 * <p>The renderer and its self-checks share these calculations so GUI scaling cannot silently
 * collapse a three-option exclusive choice into two columns while the available content width can
 * still hold three readable cards.</p>
 */
public final class ChoiceCardGrid {
	private static final int EXCLUSIVE_THREE_COLUMN_WIDTH = 360;
	private static final int EXCLUSIVE_TWO_COLUMN_WIDTH = 240;

	private ChoiceCardGrid() {
	}

	public static int columns(
		final String fieldType,
		final int optionCount,
		final int width
	) {
		if (optionCount <= 0) {
			return 1;
		}
		if ("exclusive_choice".equals(fieldType)) {
			int responsiveColumns = width >= EXCLUSIVE_THREE_COLUMN_WIDTH
				? 3
				: width >= EXCLUSIVE_TWO_COLUMN_WIDTH
					? 2
					: 1;
			return Math.min(optionCount, responsiveColumns);
		}
		return Math.min(optionCount, Math.min(3, Math.max(1, width / 96)));
	}

	public static int controlHeight(
		final String fieldType,
		final int optionCount,
		final int width
	) {
		if (optionCount <= 0) {
			return 26;
		}
		int columns = columns(fieldType, optionCount, width);
		int rows = Math.ceilDiv(optionCount, columns);
		int buttonHeight = "exclusive_choice".equals(fieldType) ? 48 : 24;
		int gap = "exclusive_choice".equals(fieldType) ? 6 : 4;
		return rows * buttonHeight + Math.max(0, rows - 1) * gap;
	}
}
