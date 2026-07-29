package io.github.zcpu954861.pixeltzzpro.animation;

/**
 * Pure completed-text hold gate for queued subtitles.
 *
 * <p>The caller records one completed frame per client tick. The next queued subtitle may only
 * start after the configured number of complete-text ticks has been rendered.
 */
public final class SubtitleQueueTimeline {
	private SubtitleQueueTimeline() {
	}

	public static HoldFrame afterCompletedFrame(
		final int completedTicks,
		final int minimumHoldTicks
	) {
		if (completedTicks < 0) {
			throw new IllegalArgumentException("completedTicks must be non-negative");
		}
		if (minimumHoldTicks <= 0) {
			throw new IllegalArgumentException("minimumHoldTicks must be positive");
		}
		int next = Math.min(minimumHoldTicks, Math.incrementExact(completedTicks));
		return new HoldFrame(next, next >= minimumHoldTicks);
	}

	public record HoldFrame(int completedTicks, boolean mayAdvance) {
	}
}
