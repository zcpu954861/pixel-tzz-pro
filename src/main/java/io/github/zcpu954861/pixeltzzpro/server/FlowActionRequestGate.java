package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.ControlRequestGate.Decision;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps one replay window per connected player and current forced-flow instance.
 */
public final class FlowActionRequestGate {
	private final Map<UUID, Scope> scopes = new HashMap<>();

	public synchronized Decision admit(
		final UUID playerId,
		final UUID flowInstanceId,
		final long requestSequence,
		final long nowEpochMillis
	) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(flowInstanceId, "flowInstanceId");
		Scope scope = this.scopes.get(playerId);
		if (scope == null || !scope.flowInstanceId().equals(flowInstanceId)) {
			scope = new Scope(flowInstanceId, new ControlRequestGate());
			this.scopes.put(playerId, scope);
		}
		return scope.gate().admit(playerId, requestSequence, nowEpochMillis);
	}

	public synchronized void remove(final UUID playerId) {
		this.scopes.remove(Objects.requireNonNull(playerId, "playerId"));
	}

	public synchronized void clear() {
		this.scopes.clear();
	}

	private record Scope(UUID flowInstanceId, ControlRequestGate gate) {
		private Scope {
			Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			Objects.requireNonNull(gate, "gate");
		}
	}
}
