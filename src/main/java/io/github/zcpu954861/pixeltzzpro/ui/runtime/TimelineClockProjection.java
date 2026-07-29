package io.github.zcpu954861.pixeltzzpro.ui.runtime;

/**
 * Projects a server-authored 20 Hz clock between snapshots without changing authority.
 */
public final class TimelineClockProjection {
	private static final long MILLIS_PER_TICK = 50L;

	private TimelineClockProjection() {
	}

	public static long project(
		final long baseTicks,
		final long acceptedAtMillis,
		final long nowMillis,
		final boolean advancing
	) {
		if (baseTicks < 0L || acceptedAtMillis < 0L || nowMillis < 0L) {
			throw new IllegalArgumentException("timeline clock inputs cannot be negative");
		}
		if (!advancing || nowMillis <= acceptedAtMillis) {
			return baseTicks;
		}
		long deltaTicks = (nowMillis - acceptedAtMillis) / MILLIS_PER_TICK;
		return baseTicks > Long.MAX_VALUE - deltaTicks
			? Long.MAX_VALUE
			: baseTicks + deltaTicks;
	}
}
