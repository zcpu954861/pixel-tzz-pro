package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.animation.SubtitleQueueTimeline;

/**
 * Pure check for the no-overlap queue gate used by operation subtitles.
 */
public final class SubtitleQueueTimelineSelfCheck {
	private SubtitleQueueTimelineSelfCheck() {
	}

	public static void main(final String[] arguments) {
		int completedTicks = 0;
		for (int tick = 1; tick <= 30; tick++) {
			var hold = SubtitleQueueTimeline.afterCompletedFrame(completedTicks, 30);
			completedTicks = hold.completedTicks();
			check(
				hold.mayAdvance() == (tick == 30),
				"the next subtitle must remain blocked until 30 complete-text ticks have rendered"
			);
		}
		System.out.println("SUBTITLE_QUEUE_TIMELINE_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
