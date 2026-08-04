package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ApplyTiming;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignRoleOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDefinitionType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenSourceDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChange;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RemovalRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * Pure authoritative transactions for changing a persisted forced-flow roster.
 *
 * <p>Every method builds and validates a candidate {@link WorldStateV2}; callers persist only the
 * returned state. No method mutates its input or consults online server objects.</p>
 */
public final class FlowRosterAuthority {
	private static final int MAX_REASON_LENGTH = 1_024;
	private static final int MAX_AUDIT_SUMMARY_LENGTH = 1_024;

	private FlowRosterAuthority() {
	}

	/**
	 * Adds verified online players using the frozen entry, audience, source filter, and role intent.
	 */
	public static Result addMembers(
		final WorldStateV2 state,
		final UUID actorId,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final List<OnlineMember> targets,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(targets, "targets");
		Objects.requireNonNull(pageInstanceIds, "pageInstanceIds");
		Failure base = preflight(state, actorId, occurredAtEpochMillis);
		if (base != null) {
			return Result.failed(base.code, base.message);
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		Failure flowFailure = flowPreflight(
			instance,
			instanceId,
			expectedInstanceRevision,
			occurredAtEpochMillis
		);
		if (flowFailure != null) {
			return Result.failed(flowFailure.code, flowFailure.message);
		}
		boolean recoverableBlock = FlowBlockAuthority.isRecoverableResourceBlock(instance);
		if (
			instance.runtime().status() == FlowInstanceStatus.BLOCKED
				&& !recoverableBlock
		) {
			return Result.failed(
				OperationCode.FLOW_NOT_ACTIVE,
				"blocked forced flow must be recovered by its owning subsystem or explicitly canceled before adding members"
			);
		}
		if (
			instance.runtime().status() != FlowInstanceStatus.ACTIVE
				&& !recoverableBlock
		) {
			return Result.failed(
				OperationCode.FLOW_NOT_ACTIVE,
				"only an active forced flow accepts new members"
			);
		}
		if (targets.isEmpty()) {
			return Result.failed(
				OperationCode.TARGET_COUNT_INVALID,
				"at least one member is required"
			);
		}
		int remaining = WorldStateV2.MAX_INSTANCE_MEMBERS
			- instance.runtime().members().size();
		if (targets.size() > remaining) {
			return Result.failed(
				OperationCode.TARGET_COUNT_INVALID,
				"member additions exceed the remaining capacity of " + remaining
			);
		}
		try {
			FrozenRosterPolicy policy = frozenRosterPolicy(state, instance);
			Map<UUID, PlayerRecord> eligiblePlayers = validateAddTargets(
				state,
				instance,
				targets,
				policy,
				occurredAtEpochMillis
			);
			String entry = policy.flow().entry();
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(
				instance.runtime().members()
			);
			Set<UUID> pageIds = new LinkedHashSet<>();
			instance.runtime().members()
				.values()
				.stream()
				.map(FlowMemberState::pageInstanceId)
				.flatMap(Optional::stream)
				.forEach(pageIds::add);
			AuditLog audit = state.audit();
			for (OnlineMember target : targets.stream()
				.sorted(Comparator.comparing(OnlineMember::playerId))
				.toList()) {
				PlayerRecord player = eligiblePlayers.get(target.playerId());
				Optional<CompletionRecord> previous = latestCompletion(
					state,
					target.playerId(),
					instance.identity().flowId(),
					instance.identity().flowVersion()
				);
				boolean alreadyComplete = instance.identity().completionPolicy()
					== CompletionPolicy.IF_INCOMPLETE
					&& previous.isPresent();
				UUID pageInstanceId = null;
				if (!alreadyComplete) {
					pageInstanceId = Objects.requireNonNull(
						pageInstanceIds.get(),
						"page instance ID"
					);
					if (!pageIds.add(pageInstanceId)) {
						throw new CandidateException(
							OperationCode.TARGET_INVALID,
							"page instance IDs must be unique"
						);
					}
				}
				FlowMemberStatus memberStatus = alreadyComplete
					? FlowMemberStatus.ALREADY_COMPLETE
					: FlowMemberStatus.IN_PROGRESS;
				Optional<PendingRoleChange> pendingRoleChange = player.pendingRoleChange();
				if (policy.targetRoleId().isPresent() && !alreadyComplete) {
					pendingRoleChange = Optional.of(
						new PendingRoleChange(
							target.playerId(),
							policy.targetRoleId().orElseThrow(),
							instance.identity().sourceActionId(),
							instanceId,
							actorId,
							occurredAtEpochMillis,
							player.roleId(),
							state.stateRevision(),
							PendingRoleChangeStatus.PENDING
						)
					);
				}
				members.put(
					target.playerId(),
					new FlowMemberState(
						target.playerId(),
						target.name(),
						alreadyComplete ? Optional.empty() : Optional.of(entry),
						memberStatus,
						0L,
						Map.of(),
						Optional.ofNullable(pageInstanceId),
						RequestSequenceWindow.empty(),
						Optional.empty(),
						false,
						Optional.of(occurredAtEpochMillis),
						previous
							.map(CompletionRecord::completedAtEpochMillis)
							.filter(ignored -> alreadyComplete),
						Optional.empty()
					)
				);
				players.put(
					target.playerId(),
					copyPlayer(
						player,
						player.roleId(),
						pendingRoleChange,
						alreadyComplete
							? Optional.empty()
							: Optional.of(
								new PlayerFlowParticipation(
									instance.identity().instanceId(),
									memberStatus
								)
							),
						target.name(),
						Math.max(occurredAtEpochMillis, player.lastSeenAtEpochMillis())
					)
				);
				audit = appendAudit(
					audit,
					AuditEventType.MEMBER_ADDED,
					occurredAtEpochMillis,
					actorId,
					Optional.of(target.playerId()),
					Optional.of(instanceId),
					"Added member " + target.playerId() + " to forced flow"
				);
				if (policy.targetRoleId().isPresent() && !alreadyComplete) {
					audit = appendAudit(
						audit,
						AuditEventType.ROLE_CHANGE_CREATED,
						occurredAtEpochMillis,
						actorId,
						Optional.of(target.playerId()),
						Optional.of(instanceId),
						"Created pending role " + policy.targetRoleId().orElseThrow()
					);
				}
			}
			Counts counts = counts(members);
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				instance.runtime().status(),
				members,
				counts.completed(),
				counts.total(),
				Math.incrementExact(instance.runtime().instanceRevision()),
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				instance.runtime().blockDiagnostic(),
				occurredAtEpochMillis
			);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			return validated(next, false, "member addition failed state validation");
		} catch (CandidateException error) {
			return Result.failed(error.code, error.getMessage());
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Removes unfinished members and recalculates the effective denominator atomically.
	 */
	public static Result removeMembers(
		final WorldStateV2 state,
		final UUID actorId,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final List<UUID> targetIds,
		final String reason,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(targetIds, "targetIds");
		Failure base = preflight(state, actorId, occurredAtEpochMillis);
		if (base != null) {
			return Result.failed(base.code, base.message);
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		Failure flowFailure = flowPreflight(
			instance,
			instanceId,
			expectedInstanceRevision,
			occurredAtEpochMillis
		);
		if (flowFailure != null) {
			return Result.failed(flowFailure.code, flowFailure.message);
		}
		if (!unfinished(instance.runtime().status())) {
			return Result.failed(
				OperationCode.FLOW_NOT_ACTIVE,
				"the forced flow is already terminal"
			);
		}
		Failure selectionFailure = validateMemberSelection(
			instance,
			targetIds,
			true
		);
		if (selectionFailure != null) {
			return Result.failed(selectionFailure.code, selectionFailure.message);
		}
		if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
			return Result.failed(
				OperationCode.TARGET_INVALID,
				"removal reason must be non-blank and at most " + MAX_REASON_LENGTH + " characters"
			);
		}

		try {
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(
				instance.runtime().members()
			);
			AuditLog audit = state.audit();
			for (UUID targetId : targetIds.stream().sorted().toList()) {
				FlowMemberState member = members.get(targetId);
				PlayerRecord player = players.get(targetId);
				if (
					player == null
						|| player.activeFlowParticipation()
							.filter(value -> value.instanceId().equals(instanceId))
							.isEmpty()
				) {
					throw new CandidateException(
						OperationCode.SNAPSHOT_INVALID,
						"unfinished member is missing matching player participation"
					);
				}
				members.put(
					targetId,
					removedMember(member, actorId, reason, occurredAtEpochMillis)
				);
				Optional<PendingRoleChange> pending = cancelPendingRoleChange(
					player.pendingRoleChange(),
					instanceId
				);
				boolean roleCanceled = !pending.equals(player.pendingRoleChange());
				players.put(
					targetId,
					copyPlayer(
						player,
						player.roleId(),
						pending,
						Optional.empty(),
						player.lastKnownName(),
						player.lastSeenAtEpochMillis()
					)
				);
				audit = appendAudit(
					audit,
					AuditEventType.MEMBER_REMOVED,
					occurredAtEpochMillis,
					actorId,
					Optional.of(targetId),
					Optional.of(instanceId),
					"Removed member " + targetId + ": " + reason
				);
				if (roleCanceled) {
					audit = appendAudit(
						audit,
						AuditEventType.ROLE_CHANGE_CANCELED,
						occurredAtEpochMillis,
						actorId,
						Optional.of(targetId),
						Optional.of(instanceId),
						"Canceled pending role change for removed member"
					);
				}
			}
			Counts counts = counts(members);
			FlowInstanceStatus coreStatus = counts.total == 0
				? FlowInstanceStatus.CANCELED
				: counts.completed == counts.total
					? FlowInstanceStatus.COMPLETED
					: FlowInstanceStatus.ACTIVE;
			boolean callbackBlocked = !instance.runtime().callbacks().failures().isEmpty();
			boolean removedResourceOwner = FlowBlockAuthority.isRecoverableResourceBlock(instance)
				&& instance.runtime()
					.blockDiagnostic()
					.flatMap(WorldStateV2.BlockDiagnostic::playerId)
					.filter(targetIds::contains)
					.isPresent();
			boolean preserveBlock = callbackBlocked
				|| (
					instance.runtime().status() == FlowInstanceStatus.BLOCKED
						&& !removedResourceOwner
				);
			FlowInstanceStatus status = preserveBlock
				? FlowInstanceStatus.BLOCKED
				: coreStatus;
			boolean becameCompleted = status == FlowInstanceStatus.COMPLETED;
			if (status == FlowInstanceStatus.COMPLETED || status == FlowInstanceStatus.CANCELED) {
				players = clearParticipations(players, instanceId);
				audit = appendAudit(
					audit,
					status == FlowInstanceStatus.COMPLETED
						? AuditEventType.FLOW_COMPLETED
						: AuditEventType.FLOW_CANCELED,
					occurredAtEpochMillis,
					actorId,
					Optional.empty(),
					Optional.of(instanceId),
					status == FlowInstanceStatus.COMPLETED
						? "Roster removal completed forced flow"
						: "All forced-flow members were removed"
				);
			}
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				status,
				members,
				counts.completed,
				counts.total,
				Math.incrementExact(instance.runtime().instanceRevision()),
				status == FlowInstanceStatus.COMPLETED || status == FlowInstanceStatus.CANCELED
					? instance.runtime().executionSnapshot().compactedForTerminalState()
					: instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				status == FlowInstanceStatus.BLOCKED
					? instance.runtime().blockDiagnostic()
					: Optional.empty(),
				occurredAtEpochMillis
			);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			return validated(next, becameCompleted, "member removal failed state validation");
		} catch (CandidateException error) {
			return Result.failed(error.code, error.getMessage());
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Cancels an active or blocked instance without manufacturing completion records.
	 */
	public static Result cancelFlow(
		final WorldStateV2 state,
		final UUID actorId,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final String reason,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(instanceId, "instanceId");
		Failure base = preflight(state, actorId, occurredAtEpochMillis);
		if (base != null) {
			return Result.failed(base.code, base.message);
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		Failure flowFailure = flowPreflight(
			instance,
			instanceId,
			expectedInstanceRevision,
			occurredAtEpochMillis
		);
		if (flowFailure != null) {
			return Result.failed(flowFailure.code, flowFailure.message);
		}
		if (!unfinished(instance.runtime().status())) {
			return Result.failed(
				OperationCode.FLOW_NOT_ACTIVE,
				"the forced flow is already terminal"
			);
		}
		if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
			return Result.failed(
				OperationCode.TARGET_INVALID,
				"cancellation reason must be non-blank and at most " + MAX_REASON_LENGTH + " characters"
			);
		}

		try {
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(
				instance.runtime().members()
			);
			AuditLog audit = state.audit();
			for (FlowMemberState member : instance.runtime().members().values()) {
				if (!unfinished(member.status())) {
					continue;
				}
				PlayerRecord player = players.get(member.playerId());
				if (player == null) {
					throw new CandidateException(
						OperationCode.SNAPSHOT_INVALID,
						"unfinished member lost its player record"
					);
				}
				members.put(
					member.playerId(),
					removedMember(member, actorId, reason, occurredAtEpochMillis)
				);
				audit = appendAudit(
					audit,
					AuditEventType.MEMBER_REMOVED,
					occurredAtEpochMillis,
					actorId,
					Optional.of(member.playerId()),
					Optional.of(instanceId),
					"Removed member during flow cancellation: " + reason
				);
			}
			for (Map.Entry<UUID, PlayerRecord> entry : List.copyOf(players.entrySet())) {
				PlayerRecord player = entry.getValue();
				Optional<PendingRoleChange> pending = cancelPendingRoleChange(
					player.pendingRoleChange(),
					instanceId
				);
				boolean roleCanceled = !pending.equals(player.pendingRoleChange());
				Optional<PlayerFlowParticipation> participation = player
					.activeFlowParticipation()
					.filter(value -> !value.instanceId().equals(instanceId));
				if (roleCanceled || !participation.equals(player.activeFlowParticipation())) {
					players.put(
						entry.getKey(),
						copyPlayer(
							player,
							player.roleId(),
							pending,
							participation,
							player.lastKnownName(),
							player.lastSeenAtEpochMillis()
						)
					);
				}
				if (roleCanceled) {
					audit = appendAudit(
						audit,
						AuditEventType.ROLE_CHANGE_CANCELED,
						occurredAtEpochMillis,
						actorId,
						Optional.of(entry.getKey()),
						Optional.of(instanceId),
						"Canceled pending role change with forced flow"
					);
				}
			}
			Counts counts = counts(members);
			audit = appendAudit(
				audit,
				AuditEventType.FLOW_CANCELED,
				occurredAtEpochMillis,
				actorId,
				Optional.empty(),
				Optional.of(instanceId),
				"Canceled forced flow: " + reason
			);
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				FlowInstanceStatus.CANCELED,
				members,
				counts.completed,
				counts.total,
				Math.incrementExact(instance.runtime().instanceRevision()),
				instance.runtime().executionSnapshot().compactedForTerminalState(),
				instance.runtime().callbacks(),
				Optional.empty(),
				occurredAtEpochMillis
			);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			return validated(next, false, "flow cancellation failed state validation");
		} catch (CandidateException error) {
			return Result.failed(error.code, error.getMessage());
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Immediately assigns one role to existing players when no forced flow is unfinished.
	 */
	public static Result assignRoleImmediate(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final UUID actorId,
		final long expectedStateRevision,
		final Identifier roleId,
		final List<UUID> targetIds,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(roleId, "roleId");
		Objects.requireNonNull(targetIds, "targetIds");
		Failure base = preflight(state, actorId, occurredAtEpochMillis);
		if (base != null) {
			return Result.failed(base.code, base.message);
		}
		if (expectedStateRevision != state.stateRevision()) {
			return Result.failed(
				OperationCode.STATE_REVISION_STALE,
				"world-state revision changed"
			);
		}
		if (
			state.activeForcedFlow()
				.map(ForcedFlowInstance::runtime)
				.map(ForcedFlowRuntime::status)
				.filter(FlowRosterAuthority::unfinished)
				.isPresent()
		) {
			return Result.failed(
				OperationCode.ACTIVE_FLOW_EXISTS,
				"a forced flow must finish or be canceled before immediate role assignment"
			);
		}
		if (state.activeGameId().isEmpty()) {
			return Result.failed(OperationCode.PHASE_MISMATCH, "no game is active");
		}
		var role = definitions.roles().get(roleId);
		if (role == null || !role.game().equals(state.activeGameId().orElseThrow())) {
			return Result.failed(
				OperationCode.TARGET_INVALID,
				"role does not belong to the active game"
			);
		}
		Failure selectionFailure = validatePlayerTargets(state, targetIds);
		if (selectionFailure != null) {
			return Result.failed(selectionFailure.code, selectionFailure.message);
		}

		try {
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			AuditLog audit = state.audit();
			for (UUID targetId : targetIds.stream().sorted().toList()) {
				PlayerRecord player = players.get(targetId);
				if (
					player.activeFlowParticipation().isPresent()
						|| player.pendingRoleChange()
							.map(PendingRoleChange::status)
							.filter(FlowRosterAuthority::unfinishedRoleChange)
							.isPresent()
				) {
					throw new CandidateException(
						OperationCode.ACTION_UNAVAILABLE,
						"target has unfinished flow or role-change state"
					);
				}
				Identifier previousRole = player.roleId();
				players.put(
					targetId,
					copyPlayer(
						player,
						roleId,
						// A direct assignment cannot carry a previous identity-flow credential.
						Optional.empty(),
						Optional.empty(),
						player.lastKnownName(),
						player.lastSeenAtEpochMillis()
					)
				);
				audit = appendAudit(
					audit,
					AuditEventType.ROLE_CHANGE_APPLIED,
					occurredAtEpochMillis,
					actorId,
					Optional.of(targetId),
					Optional.empty(),
					"Immediately changed role from " + previousRole + " to " + roleId
				);
			}
			WorldStateV2 next = state
				.withPlayers(players)
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			return validated(next, false, "immediate role assignment failed state validation");
		} catch (CandidateException error) {
			return Result.failed(error.code, error.getMessage());
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Schema-v3 member addition with the same exclusive-field capacity preflight used by a fresh
	 * flow start. Adding members must not turn a viable flow into one that cannot be completed.
	 */
	public static V3Result addMembers(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID actorId,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final List<OnlineMember> additions,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis,
		final UUID gameScopeId
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(gameScopeId, "gameScopeId");
		if (state.activeGameInstanceId().filter(gameScopeId::equals).isEmpty()) {
			return V3Result.failed(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"exclusive scope is not the active game instance"
			);
		}
		Result coreResult = addMembers(
			state.core(),
			actorId,
			instanceId,
			expectedInstanceRevision,
			additions,
			pageInstanceIds,
			occurredAtEpochMillis
		);
		if (!coreResult.successful()) {
			return V3Result.failed(coreResult.code(), coreResult.message());
		}
		WorldStateV3 candidate = state.withCore(coreResult.nextState().orElseThrow());
		ForcedFlowAuthority.CapacityFailure capacityFailure =
			ForcedFlowAuthority.exclusiveCapacityFailure(
				candidate,
				definitions,
				gameScopeId
			);
		if (capacityFailure != null) {
			return V3Result.failed(capacityFailure.code(), capacityFailure.message());
		}
		return candidate.validated().result().isPresent()
			? V3Result.succeeded(candidate, coreResult.becameCompleted())
			: V3Result.failed(
				OperationCode.INTERNAL_ERROR,
				"schema-v3 member addition failed state validation"
			);
	}

	/**
	 * Schema-v3 member removal that also releases this member's temporary exclusive selections.
	 */
	public static V3Result removeMembers(
		final WorldStateV3 state,
		final UUID actorId,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final List<UUID> targetIds,
		final String reason,
		final long occurredAtEpochMillis,
		final long serverTick
	) {
		Objects.requireNonNull(state, "state");
		Result coreResult = removeMembers(
			state.core(),
			actorId,
			instanceId,
			expectedInstanceRevision,
			targetIds,
			reason,
			occurredAtEpochMillis
		);
		if (!coreResult.successful()) {
			return V3Result.failed(coreResult.code(), coreResult.message());
		}
		WorldStateV3 candidate = state.withCore(coreResult.nextState().orElseThrow());
		ExclusiveChoiceAuthority.Result released = ExclusiveChoiceAuthority.cancelHeld(
			candidate,
			instanceId,
			Set.copyOf(targetIds),
			serverTick
		);
		if (!released.accepted()) {
			return V3Result.failed(released.code(), released.message());
		}
		candidate = released.nextState().orElse(candidate);
		return candidate.validated().result().isPresent()
			? V3Result.succeeded(candidate, coreResult.becameCompleted())
			: V3Result.failed(
				OperationCode.INTERNAL_ERROR,
				"schema-v3 member removal failed state validation"
			);
	}

	/**
	 * Schema-v3 cancellation that releases every temporary selection owned by the canceled flow.
	 */
	public static V3Result cancelFlow(
		final WorldStateV3 state,
		final UUID actorId,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final String reason,
		final long occurredAtEpochMillis,
		final long serverTick
	) {
		Objects.requireNonNull(state, "state");
		ForcedFlowInstance instance = state.core().activeForcedFlow().orElse(null);
		Result coreResult = cancelFlow(
			state.core(),
			actorId,
			instanceId,
			expectedInstanceRevision,
			reason,
			occurredAtEpochMillis
		);
		if (!coreResult.successful()) {
			return V3Result.failed(coreResult.code(), coreResult.message());
		}
		Set<UUID> owners = instance == null
			? Set.of()
			: Set.copyOf(instance.runtime().members().keySet());
		WorldStateV3 candidate = state.withCore(coreResult.nextState().orElseThrow());
		ExclusiveChoiceAuthority.Result released = ExclusiveChoiceAuthority.cancelHeld(
			candidate,
			instanceId,
			owners,
			serverTick
		);
		if (!released.accepted()) {
			return V3Result.failed(released.code(), released.message());
		}
		candidate = released.nextState().orElse(candidate);
		return candidate.validated().result().isPresent()
			? V3Result.succeeded(candidate, false)
			: V3Result.failed(
				OperationCode.INTERNAL_ERROR,
				"schema-v3 flow cancellation failed state validation"
			);
	}

	/**
	 * Immediate role assignment composed with configured exclusive-lock release.
	 */
	public static V3Result assignRoleImmediate(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID actorId,
		final long expectedStateRevision,
		final Identifier roleId,
		final List<UUID> targetIds,
		final long occurredAtEpochMillis,
		final UUID gameScopeId,
		final long serverTick
	) {
		Objects.requireNonNull(state, "state");
		Result coreResult = assignRoleImmediate(
			state.core(),
			definitions,
			actorId,
			expectedStateRevision,
			roleId,
			targetIds,
			occurredAtEpochMillis
		);
		if (!coreResult.successful()) {
			return V3Result.failed(coreResult.code(), coreResult.message());
		}
		WorldStateV3 candidate = state.withCore(coreResult.nextState().orElseThrow());
		ExclusiveChoiceAuthority.Result released =
			ExclusiveChoiceAuthority.releaseRoleMismatches(
				candidate,
				definitions,
				gameScopeId,
				serverTick
			);
		if (!released.accepted()) {
			return V3Result.failed(released.code(), released.message());
		}
		candidate = released.nextState().orElse(candidate);
		return candidate.validated().result().isPresent()
			? V3Result.succeeded(candidate, false)
			: V3Result.failed(
				OperationCode.INTERNAL_ERROR,
				"schema-v3 immediate role assignment failed state validation"
			);
	}

	private static Failure preflight(
		final WorldStateV2 state,
		final UUID actorId,
		final long occurredAtEpochMillis
	) {
		if (state.validated().result().isEmpty()) {
			return new Failure(OperationCode.SCHEMA_BLOCKED, "world state is not writable");
		}
		if (
			occurredAtEpochMillis < 0L
				|| state.stateRevision() == Long.MAX_VALUE
				|| state.audit().totalEvents() == Long.MAX_VALUE
		) {
			return new Failure(OperationCode.INTERNAL_ERROR, "operation counters or time are invalid");
		}
		if (state.host().map(host -> host.playerId().equals(actorId)).orElse(false) == false) {
			return new Failure(OperationCode.NOT_HOST, "only the current host may change the roster");
		}
		return null;
	}

	private static Failure flowPreflight(
		final ForcedFlowInstance instance,
		final UUID instanceId,
		final long expectedInstanceRevision,
		final long occurredAtEpochMillis
	) {
		if (instance == null) {
			return new Failure(OperationCode.FLOW_NOT_ACTIVE, "no forced flow exists");
		}
		if (!instance.identity().instanceId().equals(instanceId)) {
			return new Failure(OperationCode.FLOW_INSTANCE_MISMATCH, "flow instance changed");
		}
		if (expectedInstanceRevision != instance.runtime().instanceRevision()) {
			return new Failure(
				OperationCode.STATE_REVISION_STALE,
				"flow instance revision changed"
			);
		}
		if (
			expectedInstanceRevision < 0L
				|| instance.runtime().instanceRevision() == Long.MAX_VALUE
				|| occurredAtEpochMillis < instance.runtime().updatedAtEpochMillis()
		) {
			return new Failure(
				OperationCode.INTERNAL_ERROR,
				"flow revision or operation time is invalid"
			);
		}
		return null;
	}

	private static Map<UUID, PlayerRecord> validateAddTargets(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final List<OnlineMember> targets,
		final FrozenRosterPolicy policy,
		final long occurredAtEpochMillis
	) {
		Set<UUID> ids = new LinkedHashSet<>();
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
		for (OnlineMember target : targets) {
			if (target == null || !ids.add(target.playerId())) {
				throw new CandidateException(
					OperationCode.TARGET_INVALID,
					"member additions contain a null or duplicate player"
				);
			}
			if (!target.online()) {
				throw new CandidateException(
					OperationCode.TARGET_OFFLINE,
					"added members must be online"
				);
			}
			if (instance.runtime().members().containsKey(target.playerId())) {
				throw new CandidateException(
					OperationCode.TARGET_INVALID,
					"the player already exists in this flow roster"
				);
			}
			ForcedFlowAuthority.ConnectedPlayer connected =
				new ForcedFlowAuthority.ConnectedPlayer(
					target.playerId(),
					target.name(),
					true,
					true
				);
			PlayerRecord player = ForcedFlowAuthority.currentPlayer(
				state,
				policy.definitions(),
				connected,
				occurredAtEpochMillis
			);
			if (player.activeFlowParticipation().isPresent()) {
				throw new CandidateException(
					OperationCode.ACTION_UNAVAILABLE,
					"the player already participates in a forced flow"
				);
			}
			if (
				player.pendingRoleChange()
					.map(PendingRoleChange::status)
					.filter(FlowRosterAuthority::unfinishedRoleChange)
					.isPresent()
			) {
				throw new CandidateException(
					OperationCode.ACTION_UNAVAILABLE,
					"the player already has an unfinished role change"
				);
			}
			ForcedFlowAuthority.TargetResult eligibility =
				ForcedFlowAuthority.resolveEligibleCandidate(
					state,
					policy.definitions(),
					policy.action(),
					instance.identity().initiatedBy(),
					connected,
					occurredAtEpochMillis
				);
			if (!eligibility.successful()) {
				throw new CandidateException(eligibility.code(), eligibility.message());
			}
			players.put(target.playerId(), player);
		}
		return Map.copyOf(players);
	}

	private static Failure validateMemberSelection(
		final ForcedFlowInstance instance,
		final List<UUID> targetIds,
		final boolean requireUnfinished
	) {
		if (targetIds.isEmpty() || targetIds.size() > WorldStateV2.MAX_INSTANCE_MEMBERS) {
			return new Failure(
				OperationCode.TARGET_COUNT_INVALID,
				"member selection must contain 1.." + WorldStateV2.MAX_INSTANCE_MEMBERS + " players"
			);
		}
		Set<UUID> unique = new LinkedHashSet<>();
		for (UUID targetId : targetIds) {
			if (targetId == null || !unique.add(targetId)) {
				return new Failure(
					OperationCode.TARGET_INVALID,
					"member selection contains a null or duplicate player"
				);
			}
			FlowMemberState member = instance.runtime().members().get(targetId);
			if (
				member == null
					|| (requireUnfinished && !unfinished(member.status()))
			) {
				return new Failure(
					OperationCode.TARGET_INVALID,
					"selected player is not an unfinished flow member"
				);
			}
		}
		return null;
	}

	private static Failure validatePlayerTargets(
		final WorldStateV2 state,
		final List<UUID> targetIds
	) {
		if (targetIds.isEmpty() || targetIds.size() > WorldStateV2.MAX_INSTANCE_MEMBERS) {
			return new Failure(
				OperationCode.TARGET_COUNT_INVALID,
				"role assignment must contain 1.." + WorldStateV2.MAX_INSTANCE_MEMBERS + " players"
			);
		}
		Set<UUID> unique = new LinkedHashSet<>();
		for (UUID targetId : targetIds) {
			if (
				targetId == null
					|| !unique.add(targetId)
					|| !state.players().containsKey(targetId)
			) {
				return new Failure(
					OperationCode.TARGET_INVALID,
					"role targets must be unique existing players"
				);
			}
		}
		return null;
	}

	private static FrozenRosterPolicy frozenRosterPolicy(
		final WorldStateV2 state,
		final ForcedFlowInstance instance
	) {
		if (
			state.activeGameId()
				.filter(instance.identity().gameId()::equals)
				.isEmpty()
				|| state.activePhaseId()
					.filter(instance.identity().phaseIdAtCreation()::equals)
					.isEmpty()
		) {
			throw new CandidateException(
				OperationCode.PHASE_MISMATCH,
				"the active game or phase changed after this flow was created"
			);
		}
		var frozen = instance.runtime().executionSnapshot();
		if (frozen.definitionGeneration() != instance.identity().definitionGeneration()) {
			throw new CandidateException(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen definition generation does not match the flow identity"
			);
		}

		List<Source> sources = new ArrayList<>();
		sources.add(source(DefinitionType.FLOW, instance.identity().flowId(), frozen.flow()));
		frozen.pages().forEach(
			(id, document) -> sources.add(source(DefinitionType.PAGE, id, document))
		);
		frozen.themes().forEach(
			(id, document) -> sources.add(source(DefinitionType.THEME, id, document))
		);
		frozen.fields().forEach(
			(id, document) -> sources.add(source(DefinitionType.FIELD, id, document))
		);
		for (FrozenSourceDocument support : frozen.supportingDefinitions()) {
			sources.add(source(definitionType(support.type()), support.id(), support.document()));
		}
		Compilation compilation = DefinitionCompiler.compile(
			sources,
			frozen.functionReferences(),
			frozen.predicateReferences()
		);
		if (!compilation.valid()) {
			String diagnostic = compilation.problems().isEmpty()
				? "frozen roster definitions did not compile"
				: compilation.problems().getFirst().summary();
			throw new CandidateException(OperationCode.SNAPSHOT_INVALID, diagnostic);
		}
		DefinitionSnapshot definitions = compilation.snapshot()
			.orElseThrow()
			.withGeneration(frozen.definitionGeneration());
		FlowDefinition flow = definitions.flows().get(instance.identity().flowId());
		PanelActionDefinition action = definitions.panelActions()
			.get(instance.identity().sourceActionId());
		if (
			flow == null
				|| action == null
				|| !flow.required()
				|| flow.version() != instance.identity().flowVersion()
				|| !flow.game().equals(instance.identity().gameId())
				|| !action.game().equals(instance.identity().gameId())
				|| !action.phases().contains(instance.identity().phaseIdAtCreation())
		) {
			throw new CandidateException(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen flow or source action does not match the active instance"
			);
		}

		Optional<Identifier> targetRoleId;
		CompletionPolicy completionPolicy;
		if (action.operation() instanceof StartFlowOperation operation) {
			if (!operation.flow().equals(flow.id())) {
				throw new CandidateException(
					OperationCode.SNAPSHOT_INVALID,
					"the frozen source action points to another flow"
				);
			}
			targetRoleId = Optional.empty();
			completionPolicy = completionPolicy(operation.completionPolicy());
		} else if (
			action.operation() instanceof AssignRoleOperation operation
				&& operation.apply() == ApplyTiming.AFTER_FLOW
				&& operation.flow().filter(flow.id()::equals).isPresent()
				&& Optional.ofNullable(definitions.roles().get(operation.role()))
					.filter(role -> role.game().equals(instance.identity().gameId()))
					.isPresent()
		) {
			targetRoleId = Optional.of(operation.role());
			completionPolicy = completionPolicy(operation.completionPolicy());
		} else {
			throw new CandidateException(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen source action cannot create this forced flow"
			);
		}
		if (completionPolicy != instance.identity().completionPolicy()) {
			throw new CandidateException(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen completion policy does not match the active instance"
			);
		}
		return new FrozenRosterPolicy(
			definitions,
			flow,
			action,
			targetRoleId
		);
	}

	private static Source source(
		final DefinitionType type,
		final Identifier id,
		final FrozenDocument document
	) {
		return new Source(
			type,
			id,
			Identifier.fromNamespaceAndPath(
				id.getNamespace(),
				"pixel_tzz_pro/" + type.directory() + "/" + id.getPath() + ".json"
			),
			"frozen-roster",
			document.normalizedJson()
		);
	}

	private static DefinitionType definitionType(final FrozenDefinitionType type) {
		return switch (type) {
			case GAME -> DefinitionType.GAME;
			case ROLE -> DefinitionType.ROLE;
			case TEAM -> DefinitionType.TEAM;
			case LIFE_STATE -> DefinitionType.LIFE_STATE;
			case PHASE -> DefinitionType.PHASE;
			case TASK -> DefinitionType.TASK;
			case FLOW -> DefinitionType.FLOW;
			case PANEL_ACTION -> DefinitionType.PANEL_ACTION;
			case PLAYER_DATA -> DefinitionType.PLAYER_DATA;
			case PLAYER_ACTION -> DefinitionType.PLAYER_ACTION;
			case TEXT_EFFECT -> DefinitionType.TEXT_EFFECT;
			case MESSAGE_CUE -> DefinitionType.MESSAGE_CUE;
		};
	}

	private static CompletionPolicy completionPolicy(
		final io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompletionPolicy policy
	) {
		return switch (policy) {
			case IF_INCOMPLETE -> CompletionPolicy.IF_INCOMPLETE;
			case ALWAYS -> CompletionPolicy.ALWAYS;
			case RESUME_ONLY -> CompletionPolicy.RESUME_ONLY;
		};
	}

	private static Optional<CompletionRecord> latestCompletion(
		final WorldStateV2 state,
		final UUID playerId,
		final Identifier flowId,
		final int flowVersion
	) {
		return state.completionHistory()
			.stream()
			.filter(record -> record.playerId().equals(playerId))
			.filter(record -> record.flowId().equals(flowId))
			.filter(record -> record.flowVersion() == flowVersion)
			.max(Comparator.comparingLong(CompletionRecord::completedAtEpochMillis));
	}

	private static FlowMemberState removedMember(
		final FlowMemberState member,
		final UUID actorId,
		final String reason,
		final long occurredAtEpochMillis
	) {
		return new FlowMemberState(
			member.playerId(),
			member.lockedName(),
			Optional.empty(),
			FlowMemberStatus.REMOVED,
			Math.incrementExact(member.memberRevision()),
			member.flowFields(),
			Optional.empty(),
			member.requestSequenceWindow(),
			member.lastCommittedAtEpochMillis(),
			member.offline(),
			member.lastOnlineAtEpochMillis(),
			Optional.empty(),
			Optional.of(new RemovalRecord(actorId, occurredAtEpochMillis, reason))
		);
	}

	private static Optional<PendingRoleChange> cancelPendingRoleChange(
		final Optional<PendingRoleChange> pending,
		final UUID instanceId
	) {
		return pending.map(change -> {
			if (
				!change.flowInstanceId().equals(instanceId)
					|| !unfinishedRoleChange(change.status())
			) {
				return change;
			}
			return new PendingRoleChange(
				change.targetPlayerId(),
				change.targetRoleId(),
				change.sourceActionId(),
				change.flowInstanceId(),
				change.createdBy(),
				change.createdAtEpochMillis(),
				change.previousRoleId(),
				change.createdAtStateRevision(),
				PendingRoleChangeStatus.CANCELED
			);
		});
	}

	static Map<UUID, PlayerRecord> clearParticipations(
		final Map<UUID, PlayerRecord> source,
		final UUID instanceId
	) {
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(source);
		for (Map.Entry<UUID, PlayerRecord> entry : source.entrySet()) {
			PlayerRecord player = entry.getValue();
			if (
				player.activeFlowParticipation()
					.filter(value -> value.instanceId().equals(instanceId))
					.isEmpty()
			) {
				continue;
			}
			players.put(
				entry.getKey(),
				copyPlayer(
					player,
					player.roleId(),
					player.pendingRoleChange(),
					Optional.empty(),
					player.lastKnownName(),
					player.lastSeenAtEpochMillis()
				)
			);
		}
		return players;
	}

	private static PlayerRecord copyPlayer(
		final PlayerRecord source,
		final Identifier roleId,
		final Optional<PendingRoleChange> pendingRoleChange,
		final Optional<PlayerFlowParticipation> participation,
		final String name,
		final long lastSeenAtEpochMillis
	) {
		return new PlayerRecord(
			source.playerId(),
			name,
			roleId,
			source.teamId(),
			source.lifeStateId(),
			source.persistentFields(),
			pendingRoleChange,
			participation,
			source.completionSummary(),
			source.firstSeenAtEpochMillis(),
			lastSeenAtEpochMillis
		);
	}

	private static Counts counts(final Map<UUID, FlowMemberState> members) {
		int total = 0;
		int completed = 0;
		for (FlowMemberState member : members.values()) {
			if (member.status() == FlowMemberStatus.REMOVED) {
				continue;
			}
			total = Math.incrementExact(total);
			if (
				member.status() == FlowMemberStatus.COMPLETED
					|| member.status() == FlowMemberStatus.ALREADY_COMPLETE
			) {
				completed = Math.incrementExact(completed);
			}
		}
		return new Counts(completed, total);
	}

	private static AuditLog appendAudit(
		final AuditLog audit,
		final AuditEventType type,
		final long occurredAtEpochMillis,
		final UUID actorId,
		final Optional<UUID> targetId,
		final Optional<UUID> instanceId,
		final String summary
	) {
		return audit.append(
			new AuditEvent(
				audit.totalEvents(),
				type,
				occurredAtEpochMillis,
				Optional.of(actorId),
				targetId,
				instanceId,
				summary.length() <= MAX_AUDIT_SUMMARY_LENGTH
					? summary
					: summary.substring(0, MAX_AUDIT_SUMMARY_LENGTH)
			)
		);
	}

	private static Result validated(
		final WorldStateV2 next,
		final boolean becameCompleted,
		final String failureMessage
	) {
		return next.validated().result().isPresent()
			? Result.succeeded(next, becameCompleted)
			: Result.failed(OperationCode.INTERNAL_ERROR, failureMessage);
	}

	private static boolean unfinished(final FlowInstanceStatus status) {
		return status == FlowInstanceStatus.ACTIVE || status == FlowInstanceStatus.BLOCKED;
	}

	private static boolean unfinished(final FlowMemberStatus status) {
		return switch (status) {
			case NOT_STARTED, IN_PROGRESS, OFFLINE, BLOCKED -> true;
			case ALREADY_COMPLETE, COMPLETED, REMOVED -> false;
		};
	}

	private static boolean unfinishedRoleChange(final PendingRoleChangeStatus status) {
		return status == PendingRoleChangeStatus.PENDING
			|| status == PendingRoleChangeStatus.BLOCKED;
	}

	private static String safeMessage(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public record OnlineMember(UUID playerId, String name, boolean online) {
		public OnlineMember {
			playerId = Objects.requireNonNull(playerId, "playerId");
			name = Objects.requireNonNull(name, "name");
			if (name.isBlank() || name.length() > 64) {
				throw new IllegalArgumentException(
					"member name must be non-blank and at most 64 characters"
				);
			}
		}
	}

	public record Result(
		OperationCode code,
		String message,
		Optional<WorldStateV2> nextState,
		boolean becameCompleted
	) {
		public Result {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if ((code == OperationCode.SUCCESS) != nextState.isPresent()) {
				throw new IllegalArgumentException(
					"only a successful roster transaction may contain nextState"
				);
			}
			if (becameCompleted && code != OperationCode.SUCCESS) {
				throw new IllegalArgumentException(
					"a failed roster transaction cannot complete a flow"
				);
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static Result succeeded(
			final WorldStateV2 nextState,
			final boolean becameCompleted
		) {
			return new Result(
				OperationCode.SUCCESS,
				"",
				Optional.of(nextState),
				becameCompleted
			);
		}

		private static Result failed(final OperationCode code, final String message) {
			return new Result(code, message, Optional.empty(), false);
		}
	}

	public record V3Result(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		boolean becameCompleted
	) {
		public V3Result {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (
				(code == OperationCode.SUCCESS) != nextState.isPresent()
					|| (becameCompleted && code != OperationCode.SUCCESS)
			) {
				throw new IllegalArgumentException("schema-v3 roster result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static V3Result succeeded(
			final WorldStateV3 state,
			final boolean becameCompleted
		) {
			return new V3Result(
				OperationCode.SUCCESS,
				"",
				Optional.of(state),
				becameCompleted
			);
		}

		private static V3Result failed(final OperationCode code, final String message) {
			return new V3Result(code, message, Optional.empty(), false);
		}
	}

	private record Counts(int completed, int total) {
	}

	private record FrozenRosterPolicy(
		DefinitionSnapshot definitions,
		FlowDefinition flow,
		PanelActionDefinition action,
		Optional<Identifier> targetRoleId
	) {
	}

	private record Failure(OperationCode code, String message) {
	}

	private static final class CandidateException extends RuntimeException {
		private final OperationCode code;

		private CandidateException(final OperationCode code, final String message) {
			super(message);
			this.code = Objects.requireNonNull(code, "code");
		}
	}
}
