package io.github.zcpu954861.pixeltzzpro.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Connection-local burst bound for client page diagnostics.
 */
public final class ResourceReportGate {
	public static final int MAX_REPORTS_PER_WINDOW = 8;
	public static final long WINDOW_MILLIS = 1_000L;

	private final Map<UUID, State> states = new HashMap<>();

	public synchronized boolean admit(final UUID playerId, final long nowEpochMillis) {
		Objects.requireNonNull(playerId, "playerId");
		if (nowEpochMillis < 0L) {
			return false;
		}
		State previous = this.states.get(playerId);
		long windowStart = previous == null ? nowEpochMillis : previous.windowStartEpochMillis();
		int reportCount = previous == null ? 0 : previous.reportCount();
		if (nowEpochMillis < windowStart || nowEpochMillis - windowStart >= WINDOW_MILLIS) {
			windowStart = nowEpochMillis;
			reportCount = 0;
		}
		if (reportCount >= MAX_REPORTS_PER_WINDOW) {
			return false;
		}
		this.states.put(playerId, new State(windowStart, reportCount + 1));
		return true;
	}

	public synchronized void remove(final UUID playerId) {
		this.states.remove(Objects.requireNonNull(playerId, "playerId"));
	}

	public synchronized void clear() {
		this.states.clear();
	}

	private record State(long windowStartEpochMillis, int reportCount) {
	}
}
