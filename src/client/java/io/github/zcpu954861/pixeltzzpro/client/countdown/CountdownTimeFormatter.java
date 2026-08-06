package io.github.zcpu954861.pixeltzzpro.client.countdown;

import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.TimeFormat;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.TimePresentation;
import java.util.Locale;

/** Stable-width, upward-quantized countdown text used by both the live overlay and its preview. */
public final class CountdownTimeFormatter {
	private CountdownTimeFormatter() {
	}

	public static Formatted format(
		final TimePresentation presentation,
		final long totalTicks,
		final double remainingTicks
	) {
		int decimals = presentation.precision().decimals();
		long factor = switch (decimals) {
			case 0 -> 1L;
			case 1 -> 10L;
			case 2 -> 100L;
			default -> throw new IllegalStateException("unsupported countdown precision");
		};
		double clampedTicks = Math.clamp(remainingTicks, 0.0D, (double)totalTicks);
		double exactUnits = clampedTicks / 20.0D * factor;
		long units = (long)Math.ceil(exactUnits - 1.0E-9D);
		if (clampedTicks > 0.0D && units == 0L) {
			units = 1L;
		}
		long totalUnits = Math.max(1L, (long)Math.ceil(totalTicks / 20.0D * factor));
		long wholeSeconds = units / factor;
		long fraction = units % factor;

		String integer;
		if (presentation.format() == TimeFormat.MM_SS) {
			long minutes = wholeSeconds / 60L;
			long seconds = wholeSeconds % 60L;
			long totalMinutes = totalUnits / factor / 60L;
			int minuteWidth = Math.max(
				presentation.leadingZero() ? 2 : 1,
				digits(totalMinutes)
			);
			integer = pad(minutes, minuteWidth)
				+ presentation.separator()
				+ pad(seconds, 2);
		} else {
			long totalWholeSeconds = Math.max(0L, totalUnits / factor);
			int width = Math.max(
				presentation.leadingZero() ? 2 : 1,
				digits(totalWholeSeconds)
			);
			integer = pad(wholeSeconds, width);
		}

		if (decimals == 0) {
			return new Formatted(integer, integer.length());
		}
		String result = integer + "." + pad(fraction, decimals);
		return new Formatted(result, integer.length() + 1);
	}

	private static int digits(final long value) {
		return Long.toString(Math.max(0L, value)).length();
	}

	private static String pad(final long value, final int width) {
		return String.format(Locale.ROOT, "%0" + Math.max(1, width) + "d", Math.max(0L, value));
	}

	public record Formatted(String text, int fractionalStart) {
		public boolean isDigit(final int index) {
			return index >= 0 && index < this.text.length() && Character.isDigit(this.text.charAt(index));
		}

		public boolean isFractionalDigit(final int index) {
			return index >= this.fractionalStart && isDigit(index);
		}
	}
}
