package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ConfirmNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ReadinessDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowIdentity;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RemovalRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReadinessInstance;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * Pure server-authoritative readiness transaction.
 *
 * <p>Readiness deliberately owns a second persisted forced-flow-shaped instance. This lets it
 * reuse the data-driven page executor without occupying the ordinary forced-flow slot used by
 * later role corrections. Its frozen page snapshot is retained after completion so disconnect or
 * identity invalidation can reopen exactly the accepted generation.
 */
public final class ReadinessAuthority {
	private ReadinessAuthority() {
	}

	public static OpenResult open(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID initiatedBy,
		final Set<UUID> onlinePlayerIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(initiatedBy, "initiatedBy");
		Objects.requireNonNull(onlinePlayerIds, "onlinePlayerIds");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(pageInstanceIds, "pageInstanceIds");
		if (state.validated().result().isEmpty()) {
			return OpenResult.failed(OperationCode.SCHEMA_BLOCKED, "世界状态当前不可写。");
		}
		if (state.readiness().isPresent()) {
			return OpenResult.failed(OperationCode.ACTION_UNAVAILABLE, "当前游戏已经建立准备会话。");
		}
		if (occurredAtEpochMillis < 0L) {
			return OpenResult.failed(OperationCode.INTERNAL_ERROR, "准备会话时间无效。");
		}
		if (state.core().host().map(WorldStateV2.HostRecord::playerId).filter(initiatedBy::equals).isEmpty()) {
			return OpenResult.failed(OperationCode.NOT_HOST, "只有当前主持人可以开放准备。");
		}
		Identifier gameId = state.core().activeGameId().orElse(null);
		Identifier phaseId = state.core().activePhaseId().orElse(null);
		UUID gameInstanceId = state.activeGameInstanceId().orElse(null);
		GameDefinition game = gameId == null ? null : definitions.games().get(gameId);
		ReadinessDefinition readiness = game == null ? null : game.readiness().orElse(null);
		if (
			game == null
				|| readiness == null
				|| phaseId == null
				|| gameInstanceId == null
				|| !phaseId.equals(readiness.phase())
		) {
			return OpenResult.failed(OperationCode.PHASE_MISMATCH, "当前阶段没有注册玩家准备。");
		}
		PanelActionDefinition action = definitions.panelActions().get(readiness.action());
		StartFlowOperation operation = action != null && action.operation() instanceof StartFlowOperation value
			? value
			: null;
		FlowDefinition flow = operation == null ? null : definitions.flows().get(operation.flow());
		if (
			action == null
				|| flow == null
				|| !flow.game().equals(gameId)
				|| !flow.required()
				|| flow.nodes().values().stream().anyMatch(node ->
					!(node instanceof PageNode)
						&& !(node instanceof ConfirmNode)
						&& !(node instanceof CompleteNode)
				)
		) {
			return OpenResult.failed(OperationCode.DEFINITION_UNAVAILABLE, "准备流程定义不可用。");
		}
		var freeze = ExecutionSnapshotCompiler.freeze(definitions, flow, action);
		if (!freeze.success() || freeze.frozen().isEmpty()) {
			return OpenResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				freeze.message().isBlank() ? "准备流程快照无法冻结。" : freeze.message()
			);
		}
		if (!freeze.frozen().orElseThrow().fields().isEmpty()) {
			return OpenResult.failed(
				OperationCode.UNSUPPORTED_OPERATION,
				"准备流程只能确认锁定信息，不能在准备页修改字段。"
			);
		}

		try {
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>();
			Optional<UUID> hostId = state.core().host().map(WorldStateV2.HostRecord::playerId);
			state.core().players().values().stream()
				.sorted(Comparator.comparing(PlayerRecord::playerId))
				.filter(player ->
					AudienceMatcher.matches(
						definitions,
						flow.audience(),
						player,
						hostId,
						onlinePlayerIds.contains(player.playerId())
					)
				)
				.limit(WorldStateV2.MAX_INSTANCE_MEMBERS)
				.forEach(player -> {
					boolean online = onlinePlayerIds.contains(player.playerId());
					members.put(
						player.playerId(),
						waitingMember(
							player.playerId(),
							player.lastKnownName(),
							flow.entry(),
							online,
							0L,
							Objects.requireNonNull(pageInstanceIds.get(), "page instance ID"),
							occurredAtEpochMillis
						)
					);
				});
			long matchingCount = state.core().players().values().stream()
				.filter(player ->
					AudienceMatcher.matches(
						definitions,
						flow.audience(),
						player,
						hostId,
						onlinePlayerIds.contains(player.playerId())
					)
				)
				.count();
			if (matchingCount > WorldStateV2.MAX_INSTANCE_MEMBERS) {
				return OpenResult.failed(
					OperationCode.RESOURCE_BLOCKED,
					"准备参与者数量超过 " + WorldStateV2.MAX_INSTANCE_MEMBERS + " 人。"
				);
			}
			FlowInstanceStatus status = members.isEmpty()
				? FlowInstanceStatus.COMPLETED
				: FlowInstanceStatus.ACTIVE;
			ForcedFlowInstance readinessFlow = new ForcedFlowInstance(
				new ForcedFlowIdentity(
					instanceId,
					gameId,
					flow.id(),
					flow.version(),
					action.id(),
					initiatedBy,
					occurredAtEpochMillis,
					phaseId,
					definitions.generation(),
					WorldStateV2.CompletionPolicy.ALWAYS
				),
				new ForcedFlowRuntime(
					status,
					members,
					0,
					members.size(),
					0L,
					freeze.frozen().orElseThrow(),
					CallbackState.initial(),
					Optional.empty(),
					occurredAtEpochMillis
				)
			);
			WorldStateV2 nextCore = state.core()
				.withAudit(
					appendAudit(
						state.core(),
						AuditEventType.READINESS_OPENED,
						occurredAtEpochMillis,
						Optional.of(initiatedBy),
						Optional.empty(),
						Optional.of(instanceId),
						"Opened readiness " + flow.id()
					)
				)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			WorldStateV3 next = state
				.withCore(nextCore)
				.withReadiness(
					Optional.of(
						new ReadinessInstance(
							gameInstanceId,
							readinessFlow,
							readiness.disconnectInvalidates(),
							readiness.hostCanForce()
						)
					)
				);
			return next.validated().result().isPresent()
				? OpenResult.opened(next)
				: OpenResult.failed(OperationCode.INTERNAL_ERROR, "准备会话未通过状态校验。");
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return OpenResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static SubmitResult submit(
		final WorldStateV3 state,
		final DefinitionSnapshot frozenDefinitions,
		final ForcedFlowAuthority.Submission submission,
		final ForcedFlowAuthority.PredicateEvaluator predicateEvaluator,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(frozenDefinitions, "frozenDefinitions");
		Objects.requireNonNull(submission, "submission");
		Objects.requireNonNull(predicateEvaluator, "predicateEvaluator");
		Objects.requireNonNull(nextPageInstanceId, "nextPageInstanceId");
		ReadinessInstance readiness = state.readiness().orElse(null);
		if (
			readiness == null
				|| !readiness.flow().identity().instanceId().equals(submission.flowInstanceId())
		) {
			return SubmitResult.failed(OperationCode.FLOW_INSTANCE_MISMATCH, "准备会话已经变化。");
		}
		if (!submission.fieldValues().isEmpty()) {
			return SubmitResult.failed(
				OperationCode.TARGET_INVALID,
				"准备确认不能提交或修改玩家字段。"
			);
		}
		WorldStateV2 original = state.core();
		WorldStateV2 temporary = original.withActiveForcedFlow(Optional.of(readiness.flow()));
		ForcedFlowAuthority.ActionResult advanced = ForcedFlowAuthority.advance(
			temporary,
			frozenDefinitions,
			submission,
			predicateEvaluator,
			nextPageInstanceId,
			occurredAtEpochMillis
		);
		if (!advanced.successful()) {
			return SubmitResult.failed(advanced.code(), advanced.message());
		}
		try {
			WorldStateV2 advancedCore = advanced.nextState().orElseThrow();
			ForcedFlowInstance advancedFlow = advancedCore.activeForcedFlow().orElseThrow();
			ForcedFlowRuntime advancedRuntime = advancedFlow.runtime();
			ForcedFlowRuntime retainedRuntime = new ForcedFlowRuntime(
				advancedRuntime.status(),
				advancedRuntime.members(),
				advancedRuntime.completedCount(),
				advancedRuntime.totalCount(),
				advancedRuntime.instanceRevision(),
				readiness.flow().runtime().executionSnapshot(),
				advancedRuntime.callbacks(),
				advancedRuntime.blockDiagnostic(),
				advancedRuntime.updatedAtEpochMillis()
			);
			WorldStateV2 cleanCore = advancedCore
				.withPlayers(original.players())
				.withCompletionHistory(original.completionHistory())
				.withActiveForcedFlow(original.activeForcedFlow());
			if (advanced.flowCompleted()) {
				cleanCore = cleanCore.withAudit(
					appendAudit(
						cleanCore,
						AuditEventType.READINESS_COMPLETED,
						occurredAtEpochMillis,
						Optional.of(submission.playerId()),
						Optional.empty(),
						Optional.of(readiness.flow().identity().instanceId()),
						"All readiness members completed"
					)
				);
			}
			WorldStateV3 next = state
				.withCore(cleanCore)
				.withReadiness(
					Optional.of(
						new ReadinessInstance(
							readiness.gameInstanceId(),
							new ForcedFlowInstance(advancedFlow.identity(), retainedRuntime),
							readiness.disconnectInvalidates(),
							readiness.hostCanForce()
						)
					)
				);
			return next.validated().result().isPresent()
				? SubmitResult.succeeded(next, advanced.memberCompleted(), advanced.flowCompleted())
				: SubmitResult.failed(OperationCode.INTERNAL_ERROR, "准备提交后的状态未通过校验。");
		} catch (IllegalArgumentException | NullPointerException error) {
			return SubmitResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Applies join/disconnect semantics without changing the frozen denominator.
	 */
	public static MutationResult setOnline(
		final WorldStateV3 state,
		final UUID playerId,
		final String currentName,
		final boolean online,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis
	) {
		return setOnline(
			state,
			playerId,
			currentName,
			online,
			nextPageInstanceId,
			occurredAtEpochMillis,
			false
		);
	}

	/**
	 * Applies join/disconnect semantics while optionally retaining a completed readiness lock.
	 *
	 * <p>Once an opening countdown has frozen its participants, the countdown owns temporary
	 * connectivity and pause/resume behavior. Reverting an already-completed readiness member at
	 * that point would mutate the approved launch facts and route the same UUID back into a page it
	 * can no longer submit. The ordinary pre-approval path continues to honor
	 * {@code disconnect_invalidates} through the overload above.</p>
	 */
	public static MutationResult setOnline(
		final WorldStateV3 state,
		final UUID playerId,
		final String currentName,
		final boolean online,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis,
		final boolean retainCompletedLock
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(currentName, "currentName");
		Objects.requireNonNull(nextPageInstanceId, "nextPageInstanceId");
		ReadinessInstance readiness = state.readiness().orElse(null);
		if (readiness == null || state.timeline().isPresent()) {
			return MutationResult.unchanged(state);
		}
		ForcedFlowInstance flow = readiness.flow();
		FlowMemberState member = flow.runtime().members().get(playerId);
		if (member == null || member.status() == FlowMemberStatus.REMOVED) {
			return MutationResult.unchanged(state);
		}
		boolean wasReady = completed(member);
		if (wasReady && (online || retainCompletedLock)) {
			return MutationResult.unchanged(state);
		}
		if (
			online
				&& member.status() != FlowMemberStatus.OFFLINE
				&& !member.offline()
				&& (currentName.isBlank() || currentName.equals(member.lockedName()))
		) {
			return MutationResult.unchanged(state);
		}
		if (
			!online
				&& member.status() == FlowMemberStatus.OFFLINE
				&& member.offline()
		) {
			return MutationResult.unchanged(state);
		}
		if (!online && wasReady && !readiness.disconnectInvalidates()) {
			return MutationResult.unchanged(state);
		}
		try {
			FlowMemberState nextMember;
			int completed = flow.runtime().completedCount();
			if (!online) {
				if (wasReady) {
					completed--;
				}
				nextMember = waitingMember(
					playerId,
					currentName.isBlank() ? member.lockedName() : currentName,
					entry(flow),
					false,
					Math.incrementExact(member.memberRevision()),
					nextPageInstanceId,
					occurredAtEpochMillis
				);
			} else {
				nextMember = waitingMember(
					playerId,
					currentName.isBlank() ? member.lockedName() : currentName,
					member.currentNodeId().orElseGet(() -> entry(flow)),
					true,
					Math.incrementExact(member.memberRevision()),
					nextPageInstanceId,
					occurredAtEpochMillis
				);
			}
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(flow.runtime().members());
			members.put(playerId, nextMember);
			return replace(
				state,
				readiness,
				members,
				completed,
				flow.runtime().totalCount(),
				wasReady ? AuditEventType.READINESS_INVALIDATED : null,
				playerId,
				online ? "Readiness member reconnected" : "Readiness member disconnected",
				occurredAtEpochMillis,
				wasReady
			);
		} catch (ArithmeticException | IllegalArgumentException error) {
			return MutationResult.failed(state, OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Re-evaluates one player after a role/team change against the frozen readiness audience.
	 */
	public static MutationResult reconcilePlayer(
		final WorldStateV3 state,
		final UUID playerId,
		final boolean online,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis,
		final String reason
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(nextPageInstanceId, "nextPageInstanceId");
		Objects.requireNonNull(reason, "reason");
		ReadinessInstance readiness = state.readiness().orElse(null);
		PlayerRecord player = state.core().players().get(playerId);
		if (readiness == null || player == null || state.timeline().isPresent()) {
			return MutationResult.unchanged(state);
		}
		var restored = ExecutionSnapshotCompiler.restore(
			readiness.flow().identity().flowId(),
			readiness.flow().runtime().executionSnapshot()
		);
		if (!restored.success()) {
			return MutationResult.failed(state, OperationCode.SNAPSHOT_INVALID, restored.message());
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition definition = definitions.flows().get(readiness.flow().identity().flowId());
		if (definition == null) {
			return MutationResult.failed(
				state,
				OperationCode.SNAPSHOT_INVALID,
				"准备流程的冻结定义不可恢复。"
			);
		}
		boolean eligible = AudienceMatcher.matches(
			definitions,
			definition.audience(),
			player,
			state.core().host().map(WorldStateV2.HostRecord::playerId),
			online
		);
		ForcedFlowInstance flow = readiness.flow();
		FlowMemberState member = flow.runtime().members().get(playerId);
		try {
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(flow.runtime().members());
			int completed = flow.runtime().completedCount();
			int total = flow.runtime().totalCount();
			boolean invalidated = member != null && completed(member);
			if (!eligible) {
				if (member == null || member.status() == FlowMemberStatus.REMOVED) {
					return MutationResult.unchanged(state);
				}
				if (completed(member)) {
					completed--;
				}
				total--;
				members.put(
					playerId,
					new FlowMemberState(
						member.playerId(),
						player.lastKnownName(),
						Optional.empty(),
						FlowMemberStatus.REMOVED,
						Math.incrementExact(member.memberRevision()),
						Map.of(),
						Optional.empty(),
						RequestSequenceWindow.empty(),
						Optional.of(occurredAtEpochMillis),
						!online,
						online ? Optional.of(occurredAtEpochMillis) : member.lastOnlineAtEpochMillis(),
						Optional.empty(),
						Optional.of(
							new RemovalRecord(
								state.core().host().map(WorldStateV2.HostRecord::playerId).orElse(playerId),
								occurredAtEpochMillis,
								reason
							)
						)
					)
				);
			} else {
				if (member == null || member.status() == FlowMemberStatus.REMOVED) {
					total++;
				} else if (completed(member)) {
					completed--;
				}
				long revision = member == null ? 0L : Math.incrementExact(member.memberRevision());
				members.put(
					playerId,
					waitingMember(
						playerId,
						player.lastKnownName(),
						definition.entry(),
						online,
						revision,
						nextPageInstanceId,
						occurredAtEpochMillis
					)
				);
			}
			return replace(
				state,
				readiness,
				members,
				completed,
				total,
				invalidated ? AuditEventType.READINESS_INVALIDATED : null,
				playerId,
				reason,
				occurredAtEpochMillis,
				invalidated
			);
		} catch (ArithmeticException | IllegalArgumentException error) {
			return MutationResult.failed(state, OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static boolean matchesInstance(final WorldStateV3 state, final UUID instanceId) {
		return state.readiness()
			.map(ReadinessInstance::flow)
			.map(ForcedFlowInstance::identity)
			.map(ForcedFlowIdentity::instanceId)
			.filter(instanceId::equals)
			.isPresent();
	}

	public static boolean complete(final WorldStateV3 state) {
		return state.readiness()
			.map(ReadinessInstance::flow)
			.map(ForcedFlowInstance::runtime)
			.filter(runtime ->
				runtime.status() == FlowInstanceStatus.COMPLETED
					&& runtime.completedCount() == runtime.totalCount()
			)
			.isPresent();
	}

	private static MutationResult replace(
		final WorldStateV3 state,
		final ReadinessInstance readiness,
		final Map<UUID, FlowMemberState> members,
		final int completed,
		final int total,
		final AuditEventType auditType,
		final UUID playerId,
		final String reason,
		final long occurredAtEpochMillis,
		final boolean invalidated
	) {
		try {
			ForcedFlowInstance flow = readiness.flow();
			FlowInstanceStatus status = completed == total
				? FlowInstanceStatus.COMPLETED
				: FlowInstanceStatus.ACTIVE;
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				status,
				members,
				completed,
				total,
				Math.incrementExact(flow.runtime().instanceRevision()),
				flow.runtime().executionSnapshot(),
				flow.runtime().callbacks(),
				Optional.empty(),
				occurredAtEpochMillis
			);
			WorldStateV2 core = state.core();
			if (auditType != null) {
				core = core.withAudit(
					appendAudit(
						core,
						auditType,
						occurredAtEpochMillis,
						Optional.of(playerId),
						Optional.of(playerId),
						Optional.of(flow.identity().instanceId()),
						reason
					)
				);
			}
			core = core.withStateRevision(Math.incrementExact(core.stateRevision()));
			WorldStateV3 next = state
				.withCore(core)
				.withReadiness(
					Optional.of(
						new ReadinessInstance(
							readiness.gameInstanceId(),
							new ForcedFlowInstance(flow.identity(), runtime),
							readiness.disconnectInvalidates(),
							readiness.hostCanForce()
						)
					)
				);
			return next.validated().result().isPresent()
				? MutationResult.changed(next, invalidated)
				: MutationResult.failed(state, OperationCode.INTERNAL_ERROR, "准备变更未通过状态校验。");
		} catch (ArithmeticException | IllegalArgumentException error) {
			return MutationResult.failed(state, OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	private static FlowMemberState waitingMember(
		final UUID playerId,
		final String name,
		final String entry,
		final boolean online,
		final long revision,
		final UUID pageInstanceId,
		final long occurredAtEpochMillis
	) {
		return new FlowMemberState(
			playerId,
			name,
			Optional.of(entry),
			online ? FlowMemberStatus.IN_PROGRESS : FlowMemberStatus.OFFLINE,
			revision,
			Map.of(),
			Optional.of(pageInstanceId),
			RequestSequenceWindow.empty(),
			Optional.empty(),
			!online,
			online ? Optional.of(occurredAtEpochMillis) : Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
	}

	private static String entry(final ForcedFlowInstance flow) {
		var restored = ExecutionSnapshotCompiler.restore(
			flow.identity().flowId(),
			flow.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			throw new IllegalArgumentException(restored.message());
		}
		FlowDefinition definition = restored.snapshot().orElseThrow().flows().get(flow.identity().flowId());
		if (definition == null) {
			throw new IllegalArgumentException("frozen readiness flow is unavailable");
		}
		return definition.entry();
	}

	private static boolean completed(final FlowMemberState member) {
		return member.status() == FlowMemberStatus.COMPLETED
			|| member.status() == FlowMemberStatus.ALREADY_COMPLETE;
	}

	private static WorldStateV2.AuditLog appendAudit(
		final WorldStateV2 state,
		final AuditEventType type,
		final long occurredAtEpochMillis,
		final Optional<UUID> actorId,
		final Optional<UUID> targetId,
		final Optional<UUID> instanceId,
		final String summary
	) {
		return state.audit().append(
			new AuditEvent(
				state.audit().totalEvents(),
				type,
				occurredAtEpochMillis,
				actorId,
				targetId,
				instanceId,
				summary
			)
		);
	}

	private static String safeMessage(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public record OpenResult(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState
	) {
		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static OpenResult opened(final WorldStateV3 state) {
			return new OpenResult(OperationCode.SUCCESS, "已开放玩家准备。", Optional.of(state));
		}

		private static OpenResult failed(final OperationCode code, final String message) {
			return new OpenResult(code, message, Optional.empty());
		}
	}

	public record SubmitResult(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		boolean memberCompleted,
		boolean allCompleted
	) {
		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static SubmitResult succeeded(
			final WorldStateV3 state,
			final boolean memberCompleted,
			final boolean allCompleted
		) {
			return new SubmitResult(
				OperationCode.SUCCESS,
				memberCompleted ? "准备状态已锁定。" : "",
				Optional.of(state),
				memberCompleted,
				allCompleted
			);
		}

		private static SubmitResult failed(final OperationCode code, final String message) {
			return new SubmitResult(code, message, Optional.empty(), false, false);
		}
	}

	public record MutationResult(
		OperationCode code,
		String message,
		WorldStateV3 state,
		boolean changed,
		boolean invalidated
	) {
		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static MutationResult unchanged(final WorldStateV3 state) {
			return new MutationResult(OperationCode.SUCCESS, "", state, false, false);
		}

		private static MutationResult changed(final WorldStateV3 state, final boolean invalidated) {
			return new MutationResult(OperationCode.SUCCESS, "", state, true, invalidated);
		}

		private static MutationResult failed(
			final WorldStateV3 state,
			final OperationCode code,
			final String message
		) {
			return new MutationResult(code, message, state, false, false);
		}
	}
}
