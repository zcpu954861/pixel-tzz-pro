package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostOperation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure authoritative transactions for the single persisted host.
 *
 * <p>Online state and operator permission are explicit inputs from the server connection. This
 * class never infers them from client data and never mutates the supplied world-state object.
 */
public final class HostAuthority {
	private static final int MAX_PLAYER_NAME_LENGTH = 64;

	private HostAuthority() {
	}

	/**
	 * Claims an unowned world. Only a current level-2 operator may claim.
	 */
	public static Result claim(
		final WorldStateV2 state,
		final Actor actor,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actor, "actor");
		OperationCode preflight = preflight(state, occurredAtEpochMillis);
		if (preflight != OperationCode.SUCCESS) {
			return Result.failed(preflight);
		}
		if (!actor.operatorLevel2()) {
			return Result.failed(OperationCode.FORBIDDEN);
		}
		if (state.host().isPresent()) {
			return Result.failed(OperationCode.HOST_ALREADY_ASSIGNED);
		}
		return commit(
			state,
			actor.playerId(),
			actor.lastKnownName(),
			actor.playerId(),
			HostOperation.CLAIM,
			AuditEventType.HOST_CLAIMED,
			occurredAtEpochMillis,
			"Host claimed"
		);
	}

	/**
	 * Transfers authority from the current host to one online player. The target need not be OP.
	 */
	public static Result transfer(
		final WorldStateV2 state,
		final Actor actor,
		final OnlineTarget target,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(target, "target");
		OperationCode preflight = preflight(state, occurredAtEpochMillis);
		if (preflight != OperationCode.SUCCESS) {
			return Result.failed(preflight);
		}
		if (state.host().map(HostRecord::playerId).filter(actor.playerId()::equals).isEmpty()) {
			return Result.failed(OperationCode.NOT_HOST);
		}
		if (actor.playerId().equals(target.playerId())) {
			return Result.failed(OperationCode.TARGET_INVALID);
		}
		if (!target.online()) {
			return Result.failed(OperationCode.TARGET_OFFLINE);
		}
		return commit(
			state,
			target.playerId(),
			target.lastKnownName(),
			actor.playerId(),
			HostOperation.TRANSFER,
			AuditEventType.HOST_TRANSFERRED,
			occurredAtEpochMillis,
			"Host transferred"
		);
	}

	/**
	 * Replaces an existing host. Only a current level-2 operator may take over.
	 */
	public static Result takeover(
		final WorldStateV2 state,
		final Actor actor,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actor, "actor");
		OperationCode preflight = preflight(state, occurredAtEpochMillis);
		if (preflight != OperationCode.SUCCESS) {
			return Result.failed(preflight);
		}
		if (!actor.operatorLevel2()) {
			return Result.failed(OperationCode.FORBIDDEN);
		}
		if (
			state.host().isEmpty()
				|| state.host().map(HostRecord::playerId).filter(actor.playerId()::equals).isPresent()
		) {
			return Result.failed(OperationCode.ACTION_UNAVAILABLE);
		}
		return commit(
			state,
			actor.playerId(),
			actor.lastKnownName(),
			actor.playerId(),
			HostOperation.TAKEOVER,
			AuditEventType.HOST_TAKEN_OVER,
			occurredAtEpochMillis,
			"Host taken over"
		);
	}

	private static OperationCode preflight(
		final WorldStateV2 state,
		final long occurredAtEpochMillis
	) {
		if (state.validated().result().isEmpty()) {
			return OperationCode.SCHEMA_BLOCKED;
		}
		if (
			occurredAtEpochMillis < 0L
				|| state.stateRevision() == Long.MAX_VALUE
				|| state.audit().totalEvents() == Long.MAX_VALUE
		) {
			return OperationCode.INTERNAL_ERROR;
		}
		return OperationCode.SUCCESS;
	}

	private static Result commit(
		final WorldStateV2 state,
		final UUID nextHostId,
		final String nextHostName,
		final UUID actorId,
		final HostOperation operation,
		final AuditEventType auditType,
		final long occurredAtEpochMillis,
		final String auditSummary
	) {
		HostRecord host = new HostRecord(
			nextHostId,
			Optional.of(nextHostName),
			Optional.of(occurredAtEpochMillis),
			Optional.of(actorId),
			Optional.of(operation)
		);
		AuditEvent auditEvent = new AuditEvent(
			state.audit().totalEvents(),
			auditType,
			occurredAtEpochMillis,
			Optional.of(actorId),
			Optional.of(nextHostId),
			Optional.empty(),
			auditSummary
		);
		try {
			WorldStateV2 next = state
				.withHost(Optional.of(host))
				.withAudit(state.audit().append(auditEvent))
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			if (next.validated().result().isEmpty()) {
				return Result.failed(OperationCode.INTERNAL_ERROR);
			}
			return Result.succeeded(next);
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR);
		}
	}

	public record Actor(UUID playerId, String lastKnownName, boolean operatorLevel2) {
		public Actor {
			playerId = Objects.requireNonNull(playerId, "playerId");
			lastKnownName = requirePlayerName(lastKnownName, "lastKnownName");
		}
	}

	public record OnlineTarget(UUID playerId, String lastKnownName, boolean online) {
		public OnlineTarget {
			playerId = Objects.requireNonNull(playerId, "playerId");
			lastKnownName = requirePlayerName(lastKnownName, "lastKnownName");
		}
	}

	public record Result(OperationCode code, Optional<WorldStateV2> nextState) {
		public Result {
			code = Objects.requireNonNull(code, "code");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if ((code == OperationCode.SUCCESS) != nextState.isPresent()) {
				throw new IllegalArgumentException(
					"only successful host transactions may contain nextState"
				);
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static Result succeeded(final WorldStateV2 nextState) {
			return new Result(OperationCode.SUCCESS, Optional.of(nextState));
		}

		private static Result failed(final OperationCode code) {
			return new Result(code, Optional.empty());
		}
	}

	private static String requirePlayerName(final String value, final String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank() || value.length() > MAX_PLAYER_NAME_LENGTH) {
			throw new IllegalArgumentException(
				field + " must be non-blank and at most " + MAX_PLAYER_NAME_LENGTH + " characters"
			);
		}
		return value;
	}
}
