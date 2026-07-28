package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Connection-local replay and burst guard for one protocol request sequence space.
 */
public final class ControlRequestGate {
	public static final int MAX_REQUESTS_PER_WINDOW = 40;
	public static final long WINDOW_MILLIS = 1_000L;

	private final Map<UUID, State> states = new HashMap<>();

	public synchronized Decision admit(
		final UUID playerId,
		final long requestSequence,
		final long nowEpochMillis
	) {
		Objects.requireNonNull(playerId, "playerId");
		if (requestSequence < 0L || nowEpochMillis < 0L) {
			return Decision.INVALID;
		}
		State previous = this.states.getOrDefault(
			playerId,
			new State(RequestSequenceWindow.empty(), nowEpochMillis, 0)
		);
		var accepted = ForcedFlowAuthority.acceptSequence(
			previous.sequences(),
			requestSequence
		);
		if (accepted.isEmpty()) {
			return Decision.REPLAYED;
		}
		long windowStart = previous.windowStartEpochMillis();
		int requestCount = previous.requestCount();
		if (nowEpochMillis < windowStart || nowEpochMillis - windowStart >= WINDOW_MILLIS) {
			windowStart = nowEpochMillis;
			requestCount = 0;
		}
		Decision decision = requestCount >= MAX_REQUESTS_PER_WINDOW
			? Decision.RATE_LIMITED
			: Decision.ACCEPTED;
		this.states.put(
			playerId,
			new State(
				accepted.orElseThrow(),
				windowStart,
				decision == Decision.ACCEPTED ? requestCount + 1 : requestCount
			)
		);
		return decision;
	}

	public synchronized void remove(final UUID playerId) {
		this.states.remove(Objects.requireNonNull(playerId, "playerId"));
	}

	public synchronized void clear() {
		this.states.clear();
	}

	public enum Decision {
		ACCEPTED,
		REPLAYED,
		RATE_LIMITED,
		INVALID
	}

	private record State(
		RequestSequenceWindow sequences,
		long windowStartEpochMillis,
		int requestCount
	) {
		private State {
			Objects.requireNonNull(sequences, "sequences");
		}
	}
}
