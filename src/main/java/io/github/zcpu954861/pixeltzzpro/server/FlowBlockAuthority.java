package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.BlockDiagnostic;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic fatal-block and client-resource recovery transitions for one forced flow.
 */
public final class FlowBlockAuthority {
	public static final String CLIENT_PAGE_DIAGNOSTIC_PREFIX = "client_page: ";
	private static final int MAX_DIAGNOSTIC_LENGTH = 4_096;
	private static final int MAX_AUDIT_SUMMARY_LENGTH = 1_024;

	private FlowBlockAuthority() {
	}

	public static Result block(
		final WorldStateV2 state,
		final OperationCode code,
		final String message,
		final Optional<UUID> playerId,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(playerId, "playerId");
		if (code != OperationCode.SNAPSHOT_INVALID && code != OperationCode.RESOURCE_BLOCKED) {
			return Result.failed(OperationCode.INTERNAL_ERROR, "unsupported fatal flow code");
		}
		if (occurredAtEpochMillis < 0L || message.isBlank()) {
			return Result.failed(OperationCode.INTERNAL_ERROR, "invalid fatal flow diagnostic");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (instance == null) {
			return Result.failed(OperationCode.FLOW_NOT_ACTIVE, "no forced flow is active");
		}
		if (instance.runtime().status() == FlowInstanceStatus.BLOCKED) {
			return sameDiagnostic(instance, code, message, playerId)
				? Result.unchanged(code, "forced flow is already blocked by this failure")
				: Result.failed(
					OperationCode.ACTION_UNAVAILABLE,
					"forced flow already has a different blocking diagnostic"
				);
		}
		if (instance.runtime().status() != FlowInstanceStatus.ACTIVE) {
			return Result.failed(OperationCode.FLOW_NOT_ACTIVE, "forced flow is not active");
		}
		try {
			String boundedMessage = bounded(message, MAX_DIAGNOSTIC_LENGTH);
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(
				instance.runtime().members()
			);
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			playerId.ifPresent(id -> blockMember(instance, members, players, id));
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				FlowInstanceStatus.BLOCKED,
				members,
				instance.runtime().completedCount(),
				instance.runtime().totalCount(),
				Math.incrementExact(instance.runtime().instanceRevision()),
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				Optional.of(
					new BlockDiagnostic(
						code.serializedName(),
						boundedMessage,
						occurredAtEpochMillis,
						playerId,
						Optional.empty()
					)
				),
				occurredAtEpochMillis
			);
			var audit = state.audit()
				.append(
					new AuditEvent(
						state.audit().totalEvents(),
						AuditEventType.FLOW_BLOCKED,
						occurredAtEpochMillis,
						Optional.empty(),
						playerId,
						Optional.of(instance.identity().instanceId()),
						bounded(
							"Blocked forced flow "
								+ instance.identity().flowId()
								+ ": "
								+ boundedMessage,
							MAX_AUDIT_SUMMARY_LENGTH
						)
					)
				);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			if (next.validated().result().isEmpty()) {
				return Result.failed(OperationCode.INTERNAL_ERROR, "blocked flow state failed validation");
			}
			return Result.changed(code, next, boundedMessage);
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static Result recoverResourceBlock(
		final WorldStateV2 state,
		final UUID playerId,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(playerId, "playerId");
		if (occurredAtEpochMillis < 0L) {
			return Result.failed(OperationCode.INTERNAL_ERROR, "invalid recovery timestamp");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (
			instance == null
				|| instance.runtime().status() != FlowInstanceStatus.BLOCKED
				|| !instance.runtime().callbacks().failures().isEmpty()
		) {
			return Result.failed(OperationCode.FLOW_NOT_ACTIVE, "no recoverable resource block is active");
		}
		BlockDiagnostic diagnostic = instance.runtime().blockDiagnostic().orElse(null);
		if (
			diagnostic == null
				|| !diagnostic.code().equalsIgnoreCase(OperationCode.RESOURCE_BLOCKED.serializedName())
				|| !diagnostic.message().startsWith(CLIENT_PAGE_DIAGNOSTIC_PREFIX)
				|| diagnostic.playerId().filter(playerId::equals).isEmpty()
		) {
			return Result.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"resource block belongs to another failure"
			);
		}
		if (instance.runtime().completedCount() >= instance.runtime().totalCount()) {
			return Result.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"terminal flow cannot resume as active"
			);
		}
		try {
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(
				instance.runtime().members()
			);
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			FlowMemberState member = members.get(playerId);
			PlayerRecord player = players.get(playerId);
			if (
				member == null
					|| member.status() != FlowMemberStatus.BLOCKED
					|| player == null
					|| player.activeFlowParticipation()
						.filter(value -> value.instanceId().equals(instance.identity().instanceId()))
						.isEmpty()
			) {
				return Result.failed(
					OperationCode.SNAPSHOT_INVALID,
					"blocked member participation is unavailable"
				);
			}
			FlowMemberStatus resumedStatus = member.offline()
				? FlowMemberStatus.OFFLINE
				: FlowMemberStatus.IN_PROGRESS;
			members.put(
				playerId,
				copyMember(
					member,
					resumedStatus,
					Math.incrementExact(member.memberRevision())
				)
			);
			players.put(
				playerId,
				copyPlayer(
					player,
					Optional.of(
						new PlayerFlowParticipation(
							instance.identity().instanceId(),
							resumedStatus
						)
					)
				)
			);
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				FlowInstanceStatus.ACTIVE,
				members,
				instance.runtime().completedCount(),
				instance.runtime().totalCount(),
				Math.incrementExact(instance.runtime().instanceRevision()),
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				Optional.empty(),
				occurredAtEpochMillis
			);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			if (next.validated().result().isEmpty()) {
				return Result.failed(OperationCode.INTERNAL_ERROR, "recovered flow state failed validation");
			}
			return Result.changed(OperationCode.SUCCESS, next, "resource and render checks recovered");
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static boolean isRecoverableResourceBlock(final ForcedFlowInstance instance) {
		Objects.requireNonNull(instance, "instance");
		return instance.runtime().status() == FlowInstanceStatus.BLOCKED
			&& instance.runtime().callbacks().failures().isEmpty()
			&& instance.runtime()
				.blockDiagnostic()
				.filter(
					diagnostic -> diagnostic.code()
						.equalsIgnoreCase(OperationCode.RESOURCE_BLOCKED.serializedName())
				)
				.filter(
					diagnostic -> diagnostic.message()
						.startsWith(CLIENT_PAGE_DIAGNOSTIC_PREFIX)
				)
				.isPresent();
	}

	private static void blockMember(
		final ForcedFlowInstance instance,
		final Map<UUID, FlowMemberState> members,
		final Map<UUID, PlayerRecord> players,
		final UUID playerId
	) {
		FlowMemberState member = members.get(playerId);
		PlayerRecord player = players.get(playerId);
		if (
			member == null
				|| player == null
				|| (
					member.status() != FlowMemberStatus.IN_PROGRESS
						&& member.status() != FlowMemberStatus.OFFLINE
						&& member.status() != FlowMemberStatus.BLOCKED
				)
		) {
			return;
		}
		members.put(
			playerId,
			copyMember(
				member,
				FlowMemberStatus.BLOCKED,
				Math.incrementExact(member.memberRevision())
			)
		);
		if (
			player.activeFlowParticipation()
				.filter(value -> value.instanceId().equals(instance.identity().instanceId()))
				.isPresent()
		) {
			players.put(
				playerId,
				copyPlayer(
					player,
					Optional.of(
						new PlayerFlowParticipation(
							instance.identity().instanceId(),
							FlowMemberStatus.BLOCKED
						)
					)
				)
			);
		}
	}

	private static FlowMemberState copyMember(
		final FlowMemberState member,
		final FlowMemberStatus status,
		final long memberRevision
	) {
		return new FlowMemberState(
			member.playerId(),
			member.lockedName(),
			member.currentNodeId(),
			status,
			memberRevision,
			member.flowFields(),
			member.pageInstanceId(),
			member.requestSequenceWindow(),
			member.lastCommittedAtEpochMillis(),
			member.offline(),
			member.lastOnlineAtEpochMillis(),
			member.completedAtEpochMillis(),
			member.removal()
		);
	}

	private static PlayerRecord copyPlayer(
		final PlayerRecord player,
		final Optional<PlayerFlowParticipation> participation
	) {
		return new PlayerRecord(
			player.playerId(),
			player.lastKnownName(),
			player.roleId(),
			player.teamId(),
			player.lifeStateId(),
			player.persistentFields(),
			player.pendingRoleChange(),
			participation,
			player.completionSummary(),
			player.firstSeenAtEpochMillis(),
			player.lastSeenAtEpochMillis()
		);
	}

	private static boolean sameDiagnostic(
		final ForcedFlowInstance instance,
		final OperationCode code,
		final String message,
		final Optional<UUID> playerId
	) {
		return instance.runtime()
			.blockDiagnostic()
			.filter(diagnostic -> diagnostic.code().equalsIgnoreCase(code.serializedName()))
			.filter(diagnostic -> diagnostic.playerId().equals(playerId))
			.filter(diagnostic -> diagnostic.message().equals(bounded(message, MAX_DIAGNOSTIC_LENGTH)))
			.isPresent();
	}

	private static String bounded(final String value, final int maximumLength) {
		return value.length() <= maximumLength
			? value
			: value.substring(0, maximumLength - 3) + "...";
	}

	private static String safeMessage(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public record Result(
		OperationCode code,
		String message,
		Optional<WorldStateV2> nextState,
		boolean changed
	) {
		public Result {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (changed != nextState.isPresent()) {
				throw new IllegalArgumentException("changed transition must contain exactly one next state");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS
				|| this.code == OperationCode.SNAPSHOT_INVALID
				|| this.code == OperationCode.RESOURCE_BLOCKED;
		}

		private static Result changed(
			final OperationCode code,
			final WorldStateV2 state,
			final String message
		) {
			return new Result(code, message, Optional.of(state), true);
		}

		private static Result unchanged(final OperationCode code, final String message) {
			return new Result(code, message, Optional.empty(), false);
		}

		private static Result failed(final OperationCode code, final String message) {
			return new Result(code, message, Optional.empty(), false);
		}
	}
}
