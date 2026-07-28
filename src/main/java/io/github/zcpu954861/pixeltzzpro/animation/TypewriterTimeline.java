package io.github.zcpu954861.pixeltzzpro.animation;

/**
 * Pure timing math for typewriter animations.
 *
 * <p>Counts Unicode code points rather than UTF-16 code units so supplementary characters are
 * never split into broken surrogate halves.
 */
public final class TypewriterTimeline {
	private TypewriterTimeline() {
	}

	public static int visibleCodePoints(
		final long elapsedNanos,
		final int totalCodePoints,
		final long nanosPerCodePoint
	) {
		if (elapsedNanos < 0L) {
			throw new IllegalArgumentException("elapsedNanos must be non-negative");
		}
		if (totalCodePoints < 0) {
			throw new IllegalArgumentException("totalCodePoints must be non-negative");
		}
		if (nanosPerCodePoint <= 0L) {
			throw new IllegalArgumentException("nanosPerCodePoint must be positive");
		}
		if (totalCodePoints == 0) {
			return 0;
		}

		long elapsedSteps = elapsedNanos / nanosPerCodePoint;
		return (int) Math.min(totalCodePoints, Math.min(Integer.MAX_VALUE, elapsedSteps + 1L));
	}
}
