package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import java.util.Locale;

/**
 * Converts the authoritative 20 Hz game clock into player-facing elapsed-time text.
 *
 * <p>Storage and protocol values remain ticks. This formatter is deliberately presentation-only
 * and floors partial seconds so the same tick always produces the same stable label.
 */
public final class TickTimeFormatter {
	private TickTimeFormatter() {
	}

	public static String format(final long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		long hours = seconds / 3_600L;
		long minutes = (seconds % 3_600L) / 60L;
		long remainingSeconds = seconds % 60L;
		return hours > 0L
			? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
			: String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds);
	}
}
