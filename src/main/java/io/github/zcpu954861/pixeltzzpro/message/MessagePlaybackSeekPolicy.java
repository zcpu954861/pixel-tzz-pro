package io.github.zcpu954861.pixeltzzpro.message;

/**
 * Pure seek rules shared by initial playback and cold-start recovery.
 *
 * <p>Text may seek to an already-running frame, but sound is an edge-triggered effect. A small
 * transport allowance keeps a newly received live sound audible while preventing a restored plan
 * from replaying every sound whose authoritative point is already in the past.
 */
public final class MessagePlaybackSeekPolicy {
	public static final long LIVE_SOUND_GRACE_NANOS = 100_000_000L;

	private MessagePlaybackSeekPolicy() {
	}

	public static boolean suppressPastSound(
		final long playbackElapsedNanos,
		final long soundStartNanos
	) {
		if (playbackElapsedNanos < 0L || soundStartNanos < 0L) {
			throw new IllegalArgumentException("message seek times cannot be negative");
		}
		long latestLiveStart = soundStartNanos > Long.MAX_VALUE - LIVE_SOUND_GRACE_NANOS
			? Long.MAX_VALUE
			: soundStartNanos + LIVE_SOUND_GRACE_NANOS;
		return playbackElapsedNanos > latestLiveStart;
	}
}
