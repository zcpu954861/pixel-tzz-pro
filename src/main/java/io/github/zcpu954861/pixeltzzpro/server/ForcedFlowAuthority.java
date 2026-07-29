package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler.FreezeResult;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ApplyTiming;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignRoleOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ConfirmNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.EditPolicy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.NodeScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelSurface;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetFilter;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ExecutionSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowIdentity;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChange;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * Pure authoritative transactions for the single persisted forced-flow instance.
 *
 * <p>The 2C boundary supports player page, confirm, choice, branch, and complete nodes. A flow
 * containing another node kind is rejected before state is created, so an unsupported definition
 * can never leave a half-created instance.
 */
public final class ForcedFlowAuthority {
	private static final int MAX_FIELD_INPUT_BYTES = 65_536;
	private static final PredicateEvaluator NO_PREDICATE_EVALUATOR = (predicate, context) -> {
		throw new UnsupportedOperationException("player branch requires a predicate evaluator");
	};

	private ForcedFlowAuthority() {
	}

	public static StartResult start(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis
	) {
		return start(
			state,
			definitions,
			sourceAction,
			initiatedBy,
			connectedPlayers,
			explicitTargetIds,
			instanceId,
			pageInstanceIds,
			occurredAtEpochMillis,
			Optional.empty(),
			false
		);
	}

	/**
	 * Starts a predicate-gated action using availability results evaluated for this exact commit.
	 */
	public static StartResult start(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis,
		final PanelActionAuthorization authorization
	) {
		return start(
			state,
			definitions,
			sourceAction,
			initiatedBy,
			connectedPlayers,
			explicitTargetIds,
			instanceId,
			pageInstanceIds,
			occurredAtEpochMillis,
			Optional.of(Objects.requireNonNull(authorization, "authorization")),
			false
		);
	}

	/**
	 * Starts a schema-v3 forced flow after checking every referenced exclusive field has enough
	 * capacity for the exact target set.
	 */
	public static V3StartResult start(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis,
		final ExclusiveContext exclusiveContext
	) {
		return startV3(
			state,
			definitions,
			sourceAction,
			initiatedBy,
			connectedPlayers,
			explicitTargetIds,
			instanceId,
			pageInstanceIds,
			occurredAtEpochMillis,
			Optional.empty(),
			exclusiveContext
		);
	}

	/**
	 * Predicate-authorized schema-v3 start.
	 */
	public static V3StartResult start(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis,
		final PanelActionAuthorization authorization,
		final ExclusiveContext exclusiveContext
	) {
		return startV3(
			state,
			definitions,
			sourceAction,
			initiatedBy,
			connectedPlayers,
			explicitTargetIds,
			instanceId,
			pageInstanceIds,
			occurredAtEpochMillis,
			Optional.of(Objects.requireNonNull(authorization, "authorization")),
			exclusiveContext
		);
	}

	private static V3StartResult startV3(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis,
		final Optional<PanelActionAuthorization> authorization,
		final ExclusiveContext exclusiveContext
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(exclusiveContext, "exclusiveContext");
		if (state.validated().result().isEmpty()) {
			return V3StartResult.failed(OperationCode.SCHEMA_BLOCKED, "world state is not writable");
		}
		if (state.activeGameInstanceId().filter(exclusiveContext.scopeId()::equals).isEmpty()) {
			return V3StartResult.failed(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"exclusive scope is not the active game instance"
			);
		}
		StartResult coreResult = start(
			state.core(),
			definitions,
			sourceAction,
			initiatedBy,
			connectedPlayers,
			explicitTargetIds,
			instanceId,
			pageInstanceIds,
			occurredAtEpochMillis,
			authorization,
			true
		);
		if (!coreResult.successful()) {
			return V3StartResult.failed(coreResult.code(), coreResult.message());
		}
		if (coreResult.completedImmediately()) {
			return V3StartResult.completedWithoutChange(coreResult.message());
		}
		WorldStateV3 candidate = state.withCore(coreResult.nextState().orElseThrow());
		CapacityFailure capacity = exclusiveCapacityFailure(
			candidate,
			definitions,
			exclusiveContext.scopeId()
		);
		if (capacity != null) {
			return V3StartResult.failed(capacity.code(), capacity.message());
		}
		return candidate.validated().result().isPresent()
			? V3StartResult.succeeded(candidate, coreResult.instanceId().orElseThrow())
			: V3StartResult.failed(
				OperationCode.INTERNAL_ERROR,
				"schema-v3 forced-flow start failed state validation"
			);
	}

	private static StartResult start(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final UUID instanceId,
		final Supplier<UUID> pageInstanceIds,
		final long occurredAtEpochMillis,
		final Optional<PanelActionAuthorization> authorization,
		final boolean exclusiveChoiceAuthorized
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(sourceAction, "sourceAction");
		Objects.requireNonNull(initiatedBy, "initiatedBy");
		Objects.requireNonNull(connectedPlayers, "connectedPlayers");
		Objects.requireNonNull(explicitTargetIds, "explicitTargetIds");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(pageInstanceIds, "pageInstanceIds");
		if (occurredAtEpochMillis < 0L) {
			return StartResult.failed(OperationCode.INTERNAL_ERROR, "operation time is invalid");
		}
		if (state.validated().result().isEmpty()) {
			return StartResult.failed(OperationCode.SCHEMA_BLOCKED, "world state is not writable");
		}
		if (state.host().map(host -> host.playerId().equals(initiatedBy)).orElse(false) == false) {
			return StartResult.failed(OperationCode.NOT_HOST, "only the current host may start a forced flow");
		}
		if (
			(
				authorization.isPresent()
					|| sourceAction.visibleWhen().isPresent()
					|| sourceAction.enabledWhen().isPresent()
			)
				&& authorization.filter(value ->
					value.actionId().equals(sourceAction.id())
						&& value.definitionGeneration() == definitions.generation()
						&& value.stateRevision() == state.stateRevision()
						&& value.actorId().equals(initiatedBy)
						&& (sourceAction.visibleWhen().isEmpty() || value.visibleAllowed())
						&& (sourceAction.enabledWhen().isEmpty() || value.enabledAllowed())
				).isEmpty()
		) {
			return StartResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"predicate-gated action authorization is missing, stale, or denied"
			);
		}
		if (
			state.activeForcedFlow()
				.map(ForcedFlowInstance::runtime)
				.map(ForcedFlowRuntime::status)
				.filter(ForcedFlowAuthority::unfinished)
				.isPresent()
		) {
			return StartResult.failed(OperationCode.ACTIVE_FLOW_EXISTS, "another forced flow is still active");
		}
		if (
			state.activeGameId().isEmpty()
				|| state.activePhaseId().isEmpty()
				|| !state.activeGameId().orElseThrow().equals(sourceAction.game())
		) {
			return StartResult.failed(OperationCode.PHASE_MISMATCH, "the action is not in the active game");
		}
		if (
			sourceAction.surface() != PanelSurface.HOST
				|| (
					!sourceAction.phases().isEmpty()
						&& !sourceAction.phases().contains(state.activePhaseId().orElseThrow())
				)
		) {
			return StartResult.failed(OperationCode.PHASE_MISMATCH, "the action is not available in this phase");
		}
		FlowPlan flowPlan = flowPlan(definitions, sourceAction);
		if (flowPlan == null) {
			return StartResult.failed(
				OperationCode.UNSUPPORTED_OPERATION,
				"the action is not an executable forced-flow operation"
			);
		}
		FlowDefinition flow = flowPlan.flow();
		if (flowPlan.completionPolicy() == WorldStateV2.CompletionPolicy.RESUME_ONLY) {
			return StartResult.failed(
				OperationCode.FLOW_NOT_ACTIVE,
				"resume_only cannot create a new forced-flow instance"
			);
		}
		if (!flow.required()) {
			return StartResult.failed(
				OperationCode.UNSUPPORTED_OPERATION,
				"milestone 2C only starts required forced flows"
			);
		}
		for (FlowNode node : flow.nodes().values()) {
			if (
				!(node instanceof PageNode)
					&& !(node instanceof ChoiceNode)
					&& !(node instanceof ConfirmNode)
					&& !(
						node instanceof BranchNode branch
							&& branch.scope() == NodeScope.PLAYER
					)
					&& !(
						node instanceof CompleteNode complete
							&& complete.scope() == NodeScope.PLAYER
					)
			) {
				return StartResult.failed(
					OperationCode.UNSUPPORTED_FLOW_NODE,
					"the current executor does not support node " + node.id()
				);
			}
		}
		FlowNode entry = flow.nodes().get(flow.entry());
		if (
			!(entry instanceof PageNode)
				&& !(entry instanceof ChoiceNode)
				&& !(entry instanceof ConfirmNode)
		) {
			return StartResult.failed(
				OperationCode.UNSUPPORTED_FLOW_NODE,
				"the entry must be a displayable player node"
			);
		}
		if (!exclusiveChoiceAuthorized && !exclusiveFields(definitions, flow).isEmpty()) {
			return StartResult.failed(
				OperationCode.UNSUPPORTED_OPERATION,
				"exclusive_choice requires the schema-v3 forced-flow transaction"
			);
		}

		FreezeResult freeze = ExecutionSnapshotCompiler.freeze(definitions, flow, sourceAction);
		if (!freeze.success()) {
			return StartResult.failed(
				operationCode(freeze.code()),
				freeze.message() + (freeze.nodeId().isEmpty() ? "" : " [" + freeze.nodeId() + "]")
			);
		}

		TargetResult targetResult = resolveTargets(
			state,
			definitions,
			sourceAction,
			initiatedBy,
			connectedPlayers,
			explicitTargetIds,
			occurredAtEpochMillis
		);
		if (!targetResult.successful()) {
			return StartResult.failed(targetResult.code(), targetResult.message());
		}
		List<ConnectedPlayer> targets = targetResult.targets();
		if (targets.isEmpty()) {
			return StartResult.failed(
				OperationCode.NO_ELIGIBLE_TARGETS,
				"no eligible player needs this flow"
			);
		}

		try {
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>();
			int completed = 0;
			for (ConnectedPlayer connected : targets) {
				PlayerRecord player = currentPlayer(
					state,
					definitions,
					connected,
					occurredAtEpochMillis
				);
				Optional<CompletionRecord> previousCompletion = latestCompletion(
					state,
					connected.playerId(),
					flow.id(),
					flow.version()
				);
				boolean alreadyComplete = flowPlan.completionPolicy()
					== WorldStateV2.CompletionPolicy.IF_INCOMPLETE
					&& previousCompletion.isPresent();
				if (alreadyComplete) {
					completed++;
				}
				UUID pageInstanceId = alreadyComplete
					? null
					: Objects.requireNonNull(pageInstanceIds.get(), "page instance ID");
				FlowMemberStatus status = alreadyComplete
					? FlowMemberStatus.ALREADY_COMPLETE
					: FlowMemberStatus.IN_PROGRESS;
				FlowMemberState member = new FlowMemberState(
					connected.playerId(),
					connected.name(),
					alreadyComplete ? Optional.empty() : Optional.of(flow.entry()),
					status,
					0L,
					Map.of(),
					Optional.ofNullable(pageInstanceId),
					RequestSequenceWindow.empty(),
					Optional.empty(),
					false,
					Optional.of(occurredAtEpochMillis),
					previousCompletion.map(CompletionRecord::completedAtEpochMillis)
						.filter(ignored -> alreadyComplete),
					Optional.empty()
				);
				members.put(connected.playerId(), member);

				Optional<PendingRoleChange> pendingRoleChange = player.pendingRoleChange();
				Optional<Identifier> initializationRole = flowPlan.targetRoleId()
					.or(() ->
						Optional.ofNullable(definitions.roles().get(player.roleId()))
							.flatMap(role -> role.initializationFlow()
								.filter(flow.id()::equals)
								.map(ignored -> player.roleId())
							)
					);
				if (initializationRole.isPresent() && !alreadyComplete) {
					if (
						pendingRoleChange
							.filter(change -> change.status() == PendingRoleChangeStatus.PENDING)
							.isPresent()
					) {
						throw new TargetSelectionException(
							OperationCode.ACTION_UNAVAILABLE,
							"target " + connected.name() + " already has a pending role change"
						);
					}
					pendingRoleChange = Optional.of(
						new PendingRoleChange(
							player.playerId(),
							initializationRole.orElseThrow(),
							sourceAction.id(),
							instanceId,
							initiatedBy,
							occurredAtEpochMillis,
							player.roleId(),
							state.stateRevision(),
							PendingRoleChangeStatus.PENDING
						)
					);
				}
				players.put(
					player.playerId(),
					copyPlayer(
						player,
						player.roleId(),
						player.persistentFields(),
						pendingRoleChange,
						Optional.of(new PlayerFlowParticipation(instanceId, status)),
						player.completionSummary(),
						connected.name(),
						occurredAtEpochMillis
					)
				);
			}
			if (completed == targets.size()) {
				return StartResult.completedWithoutChange();
			}

			ForcedFlowIdentity identity = new ForcedFlowIdentity(
				instanceId,
				state.activeGameId().orElseThrow(),
				flow.id(),
				flow.version(),
				sourceAction.id(),
				initiatedBy,
				occurredAtEpochMillis,
				state.activePhaseId().orElseThrow(),
				definitions.generation(),
				flowPlan.completionPolicy()
			);
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				completed == targets.size() ? FlowInstanceStatus.COMPLETED : FlowInstanceStatus.ACTIVE,
				members,
				completed,
				targets.size(),
				0L,
				freeze.frozen().orElseThrow(),
				CallbackState.initial(),
				Optional.empty(),
				occurredAtEpochMillis
			);
			AuditLog audit = appendAudit(
				state.audit(),
				AuditEventType.FLOW_CREATED,
				occurredAtEpochMillis,
				Optional.of(initiatedBy),
				Optional.empty(),
				Optional.of(instanceId),
				"Created forced flow " + flow.id()
			);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(Optional.of(new ForcedFlowInstance(identity, runtime)))
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			if (next.validated().result().isEmpty()) {
				return StartResult.failed(OperationCode.INTERNAL_ERROR, "created flow failed state validation");
			}
			return StartResult.succeeded(next, instanceId);
		} catch (TargetSelectionException error) {
			return StartResult.failed(error.code, error.getMessage());
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return StartResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static ActionResult advance(
		final WorldStateV2 state,
		final DefinitionSnapshot frozenDefinitions,
		final Submission submission,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis
	) {
		return advance(
			state,
			frozenDefinitions,
			submission,
			NO_PREDICATE_EVALUATOR,
			nextPageInstanceId,
			occurredAtEpochMillis
		);
	}

	public static ActionResult advance(
		final WorldStateV2 state,
		final DefinitionSnapshot frozenDefinitions,
		final Submission submission,
		final PredicateEvaluator predicateEvaluator,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis
	) {
		return advanceCore(
			state,
			frozenDefinitions,
			submission,
			predicateEvaluator,
			nextPageInstanceId,
			occurredAtEpochMillis,
			Optional.empty()
		);
	}

	/**
	 * Advances a schema-v3 member transaction, atomically composing temporary exclusive holds and
	 * final locks with the existing forced-flow reducer.
	 */
	public static V3ActionResult advance(
		final WorldStateV3 state,
		final DefinitionSnapshot frozenDefinitions,
		final Submission submission,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis,
		final ExclusiveContext exclusiveContext
	) {
		return advance(
			state,
			frozenDefinitions,
			submission,
			NO_PREDICATE_EVALUATOR,
			nextPageInstanceId,
			occurredAtEpochMillis,
			exclusiveContext
		);
	}

	public static V3ActionResult advance(
		final WorldStateV3 state,
		final DefinitionSnapshot frozenDefinitions,
		final Submission submission,
		final PredicateEvaluator predicateEvaluator,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis,
		final ExclusiveContext exclusiveContext
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(frozenDefinitions, "frozenDefinitions");
		Objects.requireNonNull(submission, "submission");
		Objects.requireNonNull(predicateEvaluator, "predicateEvaluator");
		Objects.requireNonNull(nextPageInstanceId, "nextPageInstanceId");
		Objects.requireNonNull(exclusiveContext, "exclusiveContext");
		if (state.activeGameInstanceId().filter(exclusiveContext.scopeId()::equals).isEmpty()) {
			return V3ActionResult.failed(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"exclusive scope is not the active game instance",
				Optional.empty()
			);
		}
		ExclusivePreparation preparation = prepareExclusiveSubmission(
			state,
			frozenDefinitions,
			submission,
			exclusiveContext
		);
		if (!preparation.successful()) {
			return V3ActionResult.failed(
				preparation.code(),
				preparation.message(),
				preparation.conflict()
			);
		}
		ActionResult coreResult = advanceCore(
			preparation.nextState().orElseThrow().core(),
			frozenDefinitions,
			submission,
			predicateEvaluator,
			nextPageInstanceId,
			occurredAtEpochMillis,
			preparation.authorization()
		);
		if (!coreResult.successful()) {
			return V3ActionResult.failed(coreResult.code(), coreResult.message(), Optional.empty());
		}
		WorldStateV3 candidate = preparation.nextState().orElseThrow()
			.withCore(coreResult.nextState().orElseThrow());
		if (coreResult.memberCompleted()) {
			ExclusiveCompletion completion = completeExclusiveFields(
				candidate,
				frozenDefinitions,
				submission.playerId(),
				submission.flowInstanceId(),
				exclusiveContext
			);
			if (!completion.successful()) {
				return V3ActionResult.failed(
					completion.code(),
					completion.message(),
					Optional.empty()
				);
			}
			candidate = completion.state();
			ExclusiveChoiceAuthority.Result release = ExclusiveChoiceAuthority.releaseRoleMismatches(
				candidate,
				frozenDefinitions,
				exclusiveContext.scopeId(),
				exclusiveContext.serverTick()
			);
			if (!release.accepted()) {
				return V3ActionResult.failed(release.code(), release.message(), release.conflict());
			}
			candidate = release.nextState().orElse(candidate);
		}
		return candidate.validated().result().isPresent()
			? V3ActionResult.succeeded(
				candidate,
				coreResult.memberCompleted(),
				coreResult.flowCompleted()
			)
			: V3ActionResult.failed(
				OperationCode.INTERNAL_ERROR,
				"schema-v3 flow advance failed state validation",
				Optional.empty()
			);
	}

	/**
	 * Mutates one exclusive choice on the member's current choice page without advancing its node.
	 *
	 * <p>The request window, member revision, instance revision, and world revision are committed
	 * together with the reservation mutation. This makes a card click an authoritative write fence
	 * while leaving the normal {@code submit} action responsible only for moving to the next node.
	 */
	public static ExclusiveChoiceMutationResult mutateExclusiveChoice(
		final WorldStateV3 state,
		final DefinitionSnapshot frozenDefinitions,
		final ExclusiveChoiceMutation mutation,
		final long occurredAtEpochMillis,
		final ExclusiveContext exclusiveContext
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(frozenDefinitions, "frozenDefinitions");
		Objects.requireNonNull(mutation, "mutation");
		Objects.requireNonNull(exclusiveContext, "exclusiveContext");
		if (occurredAtEpochMillis < 0L || state.validated().result().isEmpty()) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.SCHEMA_BLOCKED,
				"world state is not writable",
				Optional.empty()
			);
		}
		if (state.activeGameInstanceId().filter(exclusiveContext.scopeId()::equals).isEmpty()) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"exclusive scope is not the active game instance",
				Optional.empty()
			);
		}

		ForcedFlowInstance instance = state.core().activeForcedFlow().orElse(null);
		if (instance == null || instance.runtime().status() != FlowInstanceStatus.ACTIVE) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.FLOW_NOT_ACTIVE,
				"no active forced flow accepts this choice",
				Optional.empty()
			);
		}
		if (
			!instance.identity().instanceId().equals(mutation.flowInstanceId())
				|| !instance.identity().flowId().equals(mutation.flowId())
				|| instance.identity().flowVersion() != mutation.flowVersion()
		) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"flow identity changed",
				Optional.empty()
			);
		}
		if (
			mutation.instanceRevision() < 0L
				|| mutation.instanceRevision() > instance.runtime().instanceRevision()
		) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.STATE_REVISION_STALE,
				"flow revision is invalid",
				Optional.empty()
			);
		}

		FlowMemberState member = instance.runtime().members().get(mutation.playerId());
		if (member == null) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.FORBIDDEN,
				"the player is not a flow member",
				Optional.empty()
			);
		}
		if (member.status() != FlowMemberStatus.IN_PROGRESS || member.offline()) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"the member cannot change this choice",
				Optional.empty()
			);
		}
		if (member.memberRevision() != mutation.memberRevision()) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.STATE_REVISION_STALE,
				"member revision changed",
				Optional.empty()
			);
		}
		if (
			!member.currentNodeId().equals(Optional.of(mutation.nodeId()))
				|| !member.pageInstanceId().equals(Optional.of(mutation.pageInstanceId()))
		) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.NODE_MISMATCH,
				"the displayed choice page is stale",
				Optional.empty()
			);
		}
		Optional<RequestSequenceWindow> acceptedWindow = acceptSequence(
			member.requestSequenceWindow(),
			mutation.requestSequence()
		);
		if (acceptedWindow.isEmpty()) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.REQUEST_REPLAYED,
				"request sequence was already accepted",
				Optional.empty()
			);
		}

		FlowDefinition flow = frozenDefinitions.flows().get(instance.identity().flowId());
		FlowNode node = flow == null ? null : flow.nodes().get(mutation.nodeId());
		if (
			flow == null
				|| flow.version() != instance.identity().flowVersion()
				|| !(node instanceof ChoiceNode choice)
				|| !choice.field().equals(mutation.fieldId())
		) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.NODE_MISMATCH,
				"the current node does not own this exclusive field",
				Optional.empty()
			);
		}
		FieldDefinition field = frozenDefinitions.fields().get(mutation.fieldId());
		PlayerRecord player = state.core().players().get(mutation.playerId());
		if (
			field == null
				|| field.type() != FieldType.EXCLUSIVE_CHOICE
				|| field.exclusiveChoice().isEmpty()
				|| player == null
				|| !applicableInFlow(field, player, instance)
		) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"exclusive field is unavailable in the current frozen flow",
				Optional.empty()
			);
		}

		ExclusiveChoiceAuthority.Result reservationResult;
		if (mutation.operation() == ExclusiveChoiceMutationOperation.HOLD) {
			String value = mutation.value().orElseThrow();
			if (!field.exclusiveChoice().orElseThrow().values().contains(value)) {
				return ExclusiveChoiceMutationResult.failed(
					OperationCode.TARGET_INVALID,
					"exclusive choice is not registered",
					Optional.empty()
				);
			}
			UUID reservationId;
			try {
				reservationId = exclusiveContext.nextReservationId();
			} catch (RuntimeException error) {
				return ExclusiveChoiceMutationResult.failed(
					OperationCode.INTERNAL_ERROR,
					"exclusive reservation ID allocation failed: " + safeMessage(error),
					Optional.empty()
				);
			}
			if (
				state.exclusiveReservations()
					.stream()
					.anyMatch(valueReservation ->
						valueReservation.reservationId().equals(reservationId)
					)
			) {
				return ExclusiveChoiceMutationResult.failed(
					OperationCode.INTERNAL_ERROR,
					"exclusive reservation ID is not unique",
					Optional.empty()
				);
			}
			reservationResult = ExclusiveChoiceAuthority.hold(
				state,
				new ExclusiveChoiceAuthority.Selection(
					field.game(),
					field.id(),
					exclusiveContext.scopeId(),
					mutation.playerId(),
					mutation.flowInstanceId(),
					value
				),
				Set.copyOf(field.exclusiveChoice().orElseThrow().values()),
				reservationId,
				exclusiveContext.serverTick()
			);
		} else {
			reservationResult = ExclusiveChoiceAuthority.cancelHeld(
				state,
				new ExclusiveChoiceAuthority.ReservationOwner(
					field.game(),
					field.id(),
					exclusiveContext.scopeId(),
					mutation.playerId()
				),
				mutation.flowInstanceId(),
				exclusiveContext.serverTick()
			);
		}
		if (!reservationResult.accepted()) {
			return ExclusiveChoiceMutationResult.failed(
				reservationResult.code(),
				reservationResult.message(),
				reservationResult.conflict()
			);
		}

		try {
			WorldStateV3 reservationState = reservationResult.nextState().orElse(state);
			long nextMemberRevision = Math.incrementExact(member.memberRevision());
			long nextInstanceRevision = Math.incrementExact(instance.runtime().instanceRevision());
			FlowMemberState nextMember = new FlowMemberState(
				member.playerId(),
				member.lockedName(),
				member.currentNodeId(),
				member.status(),
				nextMemberRevision,
				member.flowFields(),
				member.pageInstanceId(),
				acceptedWindow.orElseThrow(),
				Optional.of(occurredAtEpochMillis),
				member.offline(),
				member.lastOnlineAtEpochMillis(),
				member.completedAtEpochMillis(),
				member.removal()
			);
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(instance.runtime().members());
			members.put(member.playerId(), nextMember);
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				instance.runtime().status(),
				members,
				instance.runtime().completedCount(),
				instance.runtime().totalCount(),
				nextInstanceRevision,
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				instance.runtime().blockDiagnostic(),
				occurredAtEpochMillis
			);
			WorldStateV2 nextCore = reservationState.core()
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withStateRevision(Math.incrementExact(reservationState.core().stateRevision()));
			WorldStateV3 candidate = reservationState.withCore(nextCore);
			return candidate.validated().result().isPresent()
				? ExclusiveChoiceMutationResult.succeeded(candidate, reservationResult.message())
				: ExclusiveChoiceMutationResult.failed(
					OperationCode.INTERNAL_ERROR,
					"exclusive mutation failed state validation",
					Optional.empty()
				);
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return ExclusiveChoiceMutationResult.failed(
				OperationCode.INTERNAL_ERROR,
				safeMessage(error),
				Optional.empty()
			);
		}
	}

	private static ActionResult advanceCore(
		final WorldStateV2 state,
		final DefinitionSnapshot frozenDefinitions,
		final Submission submission,
		final PredicateEvaluator predicateEvaluator,
		final UUID nextPageInstanceId,
		final long occurredAtEpochMillis,
		final Optional<ExclusiveInputAuthorization> exclusiveAuthorization
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(frozenDefinitions, "frozenDefinitions");
		Objects.requireNonNull(submission, "submission");
		Objects.requireNonNull(predicateEvaluator, "predicateEvaluator");
		Objects.requireNonNull(nextPageInstanceId, "nextPageInstanceId");
		if (occurredAtEpochMillis < 0L) {
			return ActionResult.failed(OperationCode.INTERNAL_ERROR, "operation time is invalid");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (instance == null) {
			return ActionResult.failed(OperationCode.FLOW_NOT_ACTIVE, "no forced flow is active");
		}
		if (instance.runtime().status() != FlowInstanceStatus.ACTIVE) {
			return ActionResult.failed(OperationCode.FLOW_NOT_ACTIVE, "the forced flow is not accepting actions");
		}
		if (!instance.identity().instanceId().equals(submission.flowInstanceId())) {
			return ActionResult.failed(OperationCode.FLOW_INSTANCE_MISMATCH, "flow instance changed");
		}
		if (
			!instance.identity().flowId().equals(submission.flowId())
				|| instance.identity().flowVersion() != submission.flowVersion()
		) {
			return ActionResult.failed(OperationCode.FLOW_INSTANCE_MISMATCH, "flow identity changed");
		}
		// ponytail: a member cursor is the write fence; accepting an older instance revision avoids
		// rebuilding every member page after an unrelated submission. Cross-member mutations must
		// invalidate only the affected member revisions if their page semantics ever change.
		if (
			submission.instanceRevision() < 0L
				|| submission.instanceRevision() > instance.runtime().instanceRevision()
		) {
			return ActionResult.failed(OperationCode.STATE_REVISION_STALE, "flow revision is invalid");
		}
		FlowMemberState member = instance.runtime().members().get(submission.playerId());
		if (member == null) {
			return ActionResult.failed(OperationCode.FORBIDDEN, "the player is not a flow member");
		}
		if (
			member.status() != FlowMemberStatus.IN_PROGRESS
				|| member.offline()
		) {
			return ActionResult.failed(OperationCode.ACTION_UNAVAILABLE, "the member cannot submit this page");
		}
		if (member.memberRevision() != submission.memberRevision()) {
			return ActionResult.failed(OperationCode.STATE_REVISION_STALE, "member revision changed");
		}
		if (
			!member.currentNodeId().equals(Optional.of(submission.nodeId()))
				|| !member.pageInstanceId().equals(Optional.of(submission.pageInstanceId()))
		) {
			return ActionResult.failed(OperationCode.NODE_MISMATCH, "the displayed page is stale");
		}
		Optional<RequestSequenceWindow> acceptedWindow = acceptSequence(
			member.requestSequenceWindow(),
			submission.requestSequence()
		);
		if (acceptedWindow.isEmpty()) {
			return ActionResult.failed(OperationCode.REQUEST_REPLAYED, "request sequence was already accepted");
		}

		FlowDefinition flow = frozenDefinitions.flows().get(instance.identity().flowId());
		if (flow == null || flow.version() != instance.identity().flowVersion()) {
			return ActionResult.failed(OperationCode.SNAPSHOT_INVALID, "frozen flow definition is unavailable");
		}
		FlowNode node = flow.nodes().get(submission.nodeId());
		PlayerRecord player = state.players().get(member.playerId());
		if (player == null) {
			return ActionResult.failed(OperationCode.INTERNAL_ERROR, "flow member lost its player record");
		}

		final Identifier pageId;
		if (node instanceof PageNode page) {
			if (!validPageAction(frozenDefinitions, page.page(), submission.actionId())) {
				return ActionResult.failed(OperationCode.NODE_MISMATCH, "page action is not registered");
			}
			pageId = page.page();
		} else if (node instanceof ConfirmNode confirm) {
			boolean confirming = "confirm".equals(submission.actionId());
			boolean returning = "back".equals(submission.actionId()) && confirm.back().isPresent();
			if (
				(!confirming && !returning)
					|| !validPageAction(frozenDefinitions, confirm.page(), submission.actionId())
			) {
				return ActionResult.failed(
					OperationCode.NODE_MISMATCH,
					"confirm node requires an explicit confirm or registered back action"
				);
			}
			pageId = confirm.page();
		} else if (node instanceof ChoiceNode choice) {
			if (
				!"submit".equals(submission.actionId())
					|| !validPageAction(frozenDefinitions, choice.page(), submission.actionId())
			) {
				return ActionResult.failed(OperationCode.NODE_MISMATCH, "choice requires the submit action");
			}
			FieldDefinition choiceField = frozenDefinitions.fields().get(choice.field());
			if (
				choiceField == null
					|| (
						choiceField.type() != FieldType.SINGLE_CHOICE
							&& !(
								choiceField.type() == FieldType.EXCLUSIVE_CHOICE
									&& exclusiveAuthorization.isPresent()
							)
					)
			) {
				return ActionResult.failed(
					OperationCode.SNAPSHOT_INVALID,
					"choice field is not an authorized frozen choice definition"
				);
			}
			pageId = choice.page();
		} else {
			return ActionResult.failed(OperationCode.NODE_MISMATCH, "the current node is not a page");
		}

		try {
			FieldUpdates fieldUpdates = validateFieldInputs(
				frozenDefinitions,
				instance,
				member,
				player,
				pageId,
				predicateEvaluator,
				submission.fieldValues(),
				exclusiveAuthorization
			);
			String directNext = switch (node) {
				case PageNode page -> page.next();
				case ConfirmNode confirm -> "back".equals(submission.actionId())
					? confirm.back().orElseThrow()
					: confirm.next();
				case ChoiceNode choice -> choiceNext(choice, fieldUpdates);
				default -> throw new ActionValidationException(
					OperationCode.NODE_MISMATCH,
					"the current node is not displayable"
				);
			};
			NodeReduction reduction = reducePlayerBranches(
				frozenDefinitions,
				flow,
				directNext,
				predicateEvaluator,
				predicateContext(
					instance,
					member,
					fieldUpdates.flowFields(),
					fieldUpdates.playerFields()
				)
			);
			String nextNodeId = reduction.nodeId();
			boolean completed = reduction.completed();
			long nextMemberRevision = Math.incrementExact(member.memberRevision());
			long nextInstanceRevision = Math.incrementExact(instance.runtime().instanceRevision());
			FlowMemberState nextMember = new FlowMemberState(
				member.playerId(),
				member.lockedName(),
				Optional.of(nextNodeId),
				completed ? FlowMemberStatus.COMPLETED : FlowMemberStatus.IN_PROGRESS,
				nextMemberRevision,
				fieldUpdates.flowFields(),
				completed ? Optional.empty() : Optional.of(nextPageInstanceId),
				acceptedWindow.orElseThrow(),
				Optional.of(occurredAtEpochMillis),
				false,
				Optional.of(occurredAtEpochMillis),
				completed ? Optional.of(occurredAtEpochMillis) : Optional.empty(),
				Optional.empty()
			);
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(instance.runtime().members());
			members.put(member.playerId(), nextMember);
			int completedCount = instance.runtime().completedCount() + (completed ? 1 : 0);
			boolean flowCompleted = completedCount == instance.runtime().totalCount();

			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			List<CompletionRecord> completionHistory = new ArrayList<>(state.completionHistory());
			AuditLog audit = state.audit();
			Optional<PendingRoleChange> pendingRoleChange = player.pendingRoleChange();
			Identifier nextRole = player.roleId();
			CompletionSummary summary = player.completionSummary();
			if (completed) {
				boolean forcedRedo = latestCompletion(
					state,
					player.playerId(),
					instance.identity().flowId(),
					instance.identity().flowVersion()
				).isPresent();
				completionHistory.add(
					new CompletionRecord(
						player.playerId(),
						instance.identity().flowId(),
						instance.identity().flowVersion(),
						instance.identity().instanceId(),
						occurredAtEpochMillis,
						instance.identity().sourceActionId(),
						forcedRedo,
						instance.identity().definitionGeneration()
					)
				);
				if (completionHistory.size() > WorldStateV2.MAX_COMPLETION_HISTORY) {
					completionHistory.removeFirst();
				}
				summary = new CompletionSummary(
					Math.incrementExact(summary.naturalCompletionCount()),
					Optional.of(occurredAtEpochMillis)
				);
				if (
					pendingRoleChange
						.filter(change -> change.status() == PendingRoleChangeStatus.PENDING)
						.filter(change -> change.flowInstanceId().equals(instance.identity().instanceId()))
						.isPresent()
				) {
					PendingRoleChange change = pendingRoleChange.orElseThrow();
					nextRole = change.targetRoleId();
					pendingRoleChange = Optional.of(
						new PendingRoleChange(
							change.targetPlayerId(),
							change.targetRoleId(),
							change.sourceActionId(),
							change.flowInstanceId(),
							change.createdBy(),
							change.createdAtEpochMillis(),
							change.previousRoleId(),
							change.createdAtStateRevision(),
							PendingRoleChangeStatus.APPLIED
						)
					);
					audit = appendAudit(
						audit,
						AuditEventType.ROLE_CHANGE_APPLIED,
						occurredAtEpochMillis,
						Optional.of(player.playerId()),
						Optional.of(player.playerId()),
						Optional.of(instance.identity().instanceId()),
						"Applied pending role " + nextRole
					);
				}
				audit = appendAudit(
					audit,
					AuditEventType.MEMBER_COMPLETED,
					occurredAtEpochMillis,
					Optional.of(player.playerId()),
					Optional.of(player.playerId()),
					Optional.of(instance.identity().instanceId()),
					"Completed forced flow " + instance.identity().flowId()
				);
			}

			players.put(
				player.playerId(),
				copyPlayer(
					player,
					nextRole,
					fieldUpdates.playerFields(),
					pendingRoleChange,
					flowCompleted
						? Optional.empty()
						: Optional.of(
							new PlayerFlowParticipation(
								instance.identity().instanceId(),
								nextMember.status()
							)
						),
					summary,
					player.lastKnownName(),
					occurredAtEpochMillis
				)
			);
			if (flowCompleted) {
				for (UUID memberId : members.keySet()) {
					PlayerRecord other = players.get(memberId);
					if (other != null && other.activeFlowParticipation().isPresent()) {
						players.put(
							memberId,
							copyPlayer(
								other,
								other.roleId(),
								other.persistentFields(),
								other.pendingRoleChange(),
								Optional.empty(),
								other.completionSummary(),
								other.lastKnownName(),
								Math.max(occurredAtEpochMillis, other.lastSeenAtEpochMillis())
							)
						);
					}
				}
				audit = appendAudit(
					audit,
					AuditEventType.FLOW_COMPLETED,
					occurredAtEpochMillis,
					Optional.of(player.playerId()),
					Optional.empty(),
					Optional.of(instance.identity().instanceId()),
					"Completed forced flow " + instance.identity().flowId()
				);
			}
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				flowCompleted ? FlowInstanceStatus.COMPLETED : FlowInstanceStatus.ACTIVE,
				members,
				completedCount,
				instance.runtime().totalCount(),
				nextInstanceRevision,
				flowCompleted
					? instance.runtime().executionSnapshot().compactedForTerminalState()
					: instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				Optional.empty(),
				occurredAtEpochMillis
			);
			WorldStateV2 next = state
				.withPlayers(players)
				.withActiveForcedFlow(
					Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
				)
				.withCompletionHistory(completionHistory)
				.withAudit(audit)
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			if (next.validated().result().isEmpty()) {
				return ActionResult.failed(OperationCode.INTERNAL_ERROR, "flow advance failed state validation");
			}
			return ActionResult.succeeded(next, completed, flowCompleted);
		} catch (ActionValidationException error) {
			return ActionResult.failed(error.code, error.getMessage());
		} catch (ArithmeticException | IllegalArgumentException | NullPointerException error) {
			return ActionResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static Optional<RequestSequenceWindow> acceptSequence(
		final RequestSequenceWindow window,
		final long sequence
	) {
		Objects.requireNonNull(window, "window");
		if (sequence < 0L) {
			return Optional.empty();
		}
		long highest = window.highestAcceptedSequence();
		if (highest < 0L) {
			return Optional.of(new RequestSequenceWindow(sequence, 1L));
		}
		if (sequence > highest) {
			long distance = sequence - highest;
			long bits = distance >= Long.SIZE
				? 1L
				: (window.acceptedWindowBits() << (int)distance) | 1L;
			return Optional.of(new RequestSequenceWindow(sequence, bits));
		}
		long distance = highest - sequence;
		if (distance >= Long.SIZE) {
			return Optional.empty();
		}
		long mask = 1L << (int)distance;
		return (window.acceptedWindowBits() & mask) == 0L
			? Optional.of(
				new RequestSequenceWindow(
					highest,
					window.acceptedWindowBits() | mask
				)
			)
			: Optional.empty();
	}

	public static boolean playerMayEditField(
		final FieldDefinition field,
		final PlayerRecord player,
		final Identifier phaseId,
		final PredicateEvaluator predicateEvaluator,
		final PredicateContext predicateContext
	) {
		Objects.requireNonNull(field, "field");
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(phaseId, "phaseId");
		Objects.requireNonNull(predicateEvaluator, "predicateEvaluator");
		Objects.requireNonNull(predicateContext, "predicateContext");
		return (
			field.editableBy() == EditPolicy.PLAYER
				|| field.editableBy() == EditPolicy.BOTH
		)
			&& applicable(field, player, phaseId)
			&& visible(field, predicateEvaluator, predicateContext);
	}

	/**
	 * Uses the role that this exact forced-flow instance will commit when deciding whether a field
	 * may be shown and edited. The persisted player role deliberately remains unchanged until the
	 * flow completes.
	 */
	public static boolean playerMayEditFieldInFlow(
		final FieldDefinition field,
		final PlayerRecord player,
		final ForcedFlowInstance instance,
		final PredicateEvaluator predicateEvaluator,
		final PredicateContext predicateContext
	) {
		Objects.requireNonNull(field, "field");
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(instance, "instance");
		Objects.requireNonNull(predicateEvaluator, "predicateEvaluator");
		Objects.requireNonNull(predicateContext, "predicateContext");
		return (
			field.editableBy() == EditPolicy.PLAYER
				|| field.editableBy() == EditPolicy.BOTH
		)
			&& applicableInFlow(field, player, instance)
			&& visible(field, predicateEvaluator, predicateContext);
	}

	/**
	 * Resolves and revalidates the exact locked target set without mutating world state.
	 */
	public static TargetResult resolveTargets(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(sourceAction, "sourceAction");
		Objects.requireNonNull(initiatedBy, "initiatedBy");
		Objects.requireNonNull(connectedPlayers, "connectedPlayers");
		Objects.requireNonNull(explicitTargetIds, "explicitTargetIds");
		FlowPlan plan = flowPlan(definitions, sourceAction);
		if (plan == null) {
			return TargetResult.failed(
				OperationCode.UNSUPPORTED_OPERATION,
				"the action does not create a forced flow"
			);
		}
		try {
			return TargetResult.succeeded(
				selectTargets(
					state,
					definitions,
					sourceAction,
					plan,
					initiatedBy,
					connectedPlayers,
					explicitTargetIds,
					occurredAtEpochMillis
				)
			);
		} catch (TargetSelectionException error) {
			return TargetResult.failed(error.code, error.getMessage());
		} catch (RuntimeException error) {
			return TargetResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Checks one candidate without applying the action's aggregate min/max bounds.
	 *
	 * <p>This is the shared authority used by target-list previews. Final submission must still call
	 * {@link #resolveTargets(WorldStateV2, DefinitionSnapshot, PanelActionDefinition, UUID, List,
	 * List, long)} or the corresponding immediate-role transaction for the exact selected set.</p>
	 */
	public static TargetResult resolveEligibleCandidate(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition sourceAction,
		final UUID initiatedBy,
		final ConnectedPlayer candidate,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(sourceAction, "sourceAction");
		Objects.requireNonNull(initiatedBy, "initiatedBy");
		Objects.requireNonNull(candidate, "candidate");
		if (!candidate.online() || !candidate.clientReady()) {
			return TargetResult.failed(
				OperationCode.TARGET_OFFLINE,
				"the target must be online with a verified client"
			);
		}
		FlowPlan plan = flowPlan(definitions, sourceAction);
		boolean immediateRole = sourceAction.operation() instanceof AssignRoleOperation operation
			&& operation.apply() == ApplyTiming.IMMEDIATE;
		if (immediateRole && candidate.playerId().equals(initiatedBy)) {
			return TargetResult.failed(
				OperationCode.TARGET_INVALID,
				"主持人不能成为此次身份调整的目标"
			);
		}
		if (plan == null && !immediateRole) {
			return TargetResult.failed(
				OperationCode.UNSUPPORTED_OPERATION,
				"the action has no supported target eligibility contract"
			);
		}
		try {
			PlayerRecord player = currentPlayer(
				state,
				definitions,
				candidate,
				occurredAtEpochMillis
			);
			if (!matchesFilter(definitions, sourceAction.target().filter(), state, player)) {
				return TargetResult.failed(
					OperationCode.TARGET_INVALID,
					"the target does not satisfy the operation filter"
				);
			}
			if (
				plan != null
					&& !matchesAudience(
						definitions,
						plan.flow().audience(),
						player,
						initiatedBy
					)
			) {
				return TargetResult.failed(
					OperationCode.TARGET_INVALID,
					"the target does not satisfy the flow audience"
				);
			}
			if (plan != null && !needsCurrentFlow(definitions, state, player, plan)) {
				return TargetResult.failed(
					OperationCode.TARGET_INVALID,
					"目标玩家已经完成当前版本流程"
				);
			}
			return TargetResult.succeeded(List.of(candidate));
		} catch (RuntimeException error) {
			return TargetResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	private static ExclusivePreparation prepareExclusiveSubmission(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final Submission submission,
		final ExclusiveContext context
	) {
		if (state.validated().result().isEmpty()) {
			return ExclusivePreparation.failed(
				OperationCode.SCHEMA_BLOCKED,
				"world state is not writable",
				Optional.empty()
			);
		}
		Map<Identifier, String> submitted = new LinkedHashMap<>();
		for (FieldInput input : submission.fieldValues()) {
			if (input == null) {
				return ExclusivePreparation.failed(
					OperationCode.TARGET_INVALID,
					"field submission contains a null field",
					Optional.empty()
				);
			}
			FieldDefinition field = definitions.fields().get(input.fieldId());
			if (field == null || field.type() != FieldType.EXCLUSIVE_CHOICE) {
				continue;
			}
			if (submitted.containsKey(field.id())) {
				return ExclusivePreparation.failed(
					OperationCode.TARGET_INVALID,
					"field submission contains a duplicate exclusive field",
					Optional.empty()
				);
			}
			try {
				submitted.put(field.id(), parseExclusiveValue(field, input));
			} catch (ActionValidationException error) {
				return ExclusivePreparation.failed(
					error.code,
					error.getMessage(),
					Optional.empty()
				);
			}
		}

		WorldStateV3 candidate = state;
		Set<UUID> reservationIds = new LinkedHashSet<>();
		for (Map.Entry<Identifier, String> entry : submitted.entrySet()) {
			FieldDefinition field = definitions.fields().get(entry.getKey());
			UUID reservationId;
			try {
				reservationId = context.nextReservationId();
			} catch (RuntimeException error) {
				return ExclusivePreparation.failed(
					OperationCode.INTERNAL_ERROR,
					"exclusive reservation ID allocation failed: " + safeMessage(error),
					Optional.empty()
				);
			}
			if (
				!reservationIds.add(reservationId)
					|| candidate.exclusiveReservations()
						.stream()
						.anyMatch(value -> value.reservationId().equals(reservationId))
			) {
				return ExclusivePreparation.failed(
					OperationCode.INTERNAL_ERROR,
					"exclusive reservation ID is not unique",
					Optional.empty()
				);
			}
			ExclusiveChoiceAuthority.Result held = ExclusiveChoiceAuthority.hold(
				candidate,
				new ExclusiveChoiceAuthority.Selection(
					field.game(),
					field.id(),
					context.scopeId(),
					submission.playerId(),
					submission.flowInstanceId(),
					entry.getValue()
				),
				Set.copyOf(field.exclusiveChoice().orElseThrow().values()),
				reservationId,
				context.serverTick()
			);
			if (!held.accepted()) {
				return ExclusivePreparation.failed(
					held.code(),
					held.message(),
					held.conflict()
				);
			}
			candidate = held.nextState().orElse(candidate);
		}
		return ExclusivePreparation.succeeded(
			candidate,
			new ExclusiveInputAuthorization(
				candidate,
				context.scopeId(),
				submission.playerId(),
				submission.flowInstanceId(),
				submitted
			)
		);
	}

	private static ExclusiveCompletion completeExclusiveFields(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID playerId,
		final UUID flowInstanceId,
		final ExclusiveContext context
	) {
		ForcedFlowInstance instance = state.core().activeForcedFlow()
			.filter(value -> value.identity().instanceId().equals(flowInstanceId))
			.orElse(null);
		PlayerRecord player = state.core().players().get(playerId);
		if (instance == null || player == null) {
			return ExclusiveCompletion.failed(
				OperationCode.SNAPSHOT_INVALID,
				"completed flow lost its instance or player record"
			);
		}
		FlowDefinition flow = definitions.flows().get(instance.identity().flowId());
		if (flow == null || flow.version() != instance.identity().flowVersion()) {
			return ExclusiveCompletion.failed(
				OperationCode.SNAPSHOT_INVALID,
				"completed flow definition is unavailable"
			);
		}

		WorldStateV3 candidate = state;
		Map<Identifier, PersistentFieldValue> persistent = new LinkedHashMap<>(
			player.persistentFields()
		);
		for (FieldDefinition field : exclusiveFields(definitions, flow)) {
			Set<String> values = Set.copyOf(field.exclusiveChoice().orElseThrow().values());
			ExclusiveReservation held = candidate.exclusiveReservations()
				.stream()
				.filter(reservation ->
					reservation.gameId().equals(field.game())
						&& reservation.fieldId().equals(field.id())
						&& reservation.scopeId().equals(context.scopeId())
						&& reservation.ownerId().equals(playerId)
						&& reservation.status() == ReservationStatus.HELD
						&& reservation.flowInstanceId().filter(flowInstanceId::equals).isPresent()
				)
				.findFirst()
				.orElse(null);
			if (held != null) {
				ExclusiveChoiceAuthority.Result locked = ExclusiveChoiceAuthority.lock(
					candidate,
					new ExclusiveChoiceAuthority.Selection(
						field.game(),
						field.id(),
						context.scopeId(),
						playerId,
						flowInstanceId,
						held.value()
					),
					values,
					context.serverTick()
				);
				if (!locked.accepted()) {
					return ExclusiveCompletion.failed(locked.code(), locked.message());
				}
				candidate = locked.nextState().orElse(candidate);
			}
			Optional<String> lockedValue = ExclusiveChoiceAuthority.lockedValue(
				candidate,
				new ExclusiveChoiceAuthority.ReservationOwner(
					field.game(),
					field.id(),
					context.scopeId(),
					playerId
				)
			);
			if (lockedValue.filter(values::contains).isEmpty()) {
				if (field.required() && applicableInFlow(field, player, instance)) {
					return ExclusiveCompletion.failed(
						OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE,
						"required exclusive field is not locked: " + field.id()
					);
				}
				continue;
			}
			persistent.put(
				field.id(),
				new PersistentFieldValue(
					field.version(),
					StoredValue.singleChoice(lockedValue.orElseThrow())
				)
			);
		}
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(candidate.core().players());
		players.put(
			playerId,
			copyPlayer(
				players.get(playerId),
				players.get(playerId).roleId(),
				persistent,
				players.get(playerId).pendingRoleChange(),
				players.get(playerId).activeFlowParticipation(),
				players.get(playerId).completionSummary(),
				players.get(playerId).lastKnownName(),
				players.get(playerId).lastSeenAtEpochMillis()
			)
		);
		candidate = candidate.withCore(candidate.core().withPlayers(players));
		return candidate.validated().result().isPresent()
			? ExclusiveCompletion.succeeded(candidate)
			: ExclusiveCompletion.failed(
				OperationCode.INTERNAL_ERROR,
				"exclusive completion failed state validation"
			);
	}

	static CapacityFailure exclusiveCapacityFailure(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID scopeId
	) {
		ForcedFlowInstance instance = state.core().activeForcedFlow().orElse(null);
		if (instance == null) {
			return new CapacityFailure(
				OperationCode.SNAPSHOT_INVALID,
				"started flow instance is unavailable"
			);
		}
		FlowDefinition flow = definitions.flows().get(instance.identity().flowId());
		if (flow == null || flow.version() != instance.identity().flowVersion()) {
			return new CapacityFailure(
				OperationCode.SNAPSHOT_INVALID,
				"started flow definition is unavailable"
			);
		}
		for (FieldDefinition field : exclusiveFields(definitions, flow)) {
			Set<UUID> targets = new LinkedHashSet<>();
			for (FlowMemberState member : instance.runtime().members().values()) {
				if (member.status() != FlowMemberStatus.IN_PROGRESS) {
					continue;
				}
				PlayerRecord player = state.core().players().get(member.playerId());
				if (player == null) {
					return new CapacityFailure(
						OperationCode.SNAPSHOT_INVALID,
						"started flow member lost its player record"
					);
				}
				if (applicableInFlow(field, player, instance)) {
					targets.add(player.playerId());
				}
			}
			ExclusiveChoiceAuthority.Capacity capacity = ExclusiveChoiceAuthority.capacity(
				state,
				field.game(),
				field.id(),
				scopeId,
				Set.copyOf(field.exclusiveChoice().orElseThrow().values()),
				targets
			);
			if (!capacity.sufficient()) {
				return new CapacityFailure(
					OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE,
					"当前无法发起『"
						+ flow.name().plainText()
						+ "』：『"
						+ field.name().plainText()
						+ "』当前剩余 "
						+ capacity.available()
						+ " 个可用位置，无法为 "
						+ capacity.required()
						+ " 名玩家分配。"
				);
			}
		}
		return null;
	}

	private static List<FieldDefinition> exclusiveFields(
		final DefinitionSnapshot definitions,
		final FlowDefinition flow
	) {
		Set<Identifier> fieldIds = new LinkedHashSet<>();
		for (FlowNode node : flow.nodes().values()) {
			if (node instanceof ChoiceNode choice) {
				fieldIds.add(choice.field());
			}
			Identifier pageId = switch (node) {
				case PageNode page -> page.page();
				case ChoiceNode choice -> choice.page();
				case ConfirmNode confirm -> confirm.page();
				default -> null;
			};
			if (pageId != null) {
				PageDefinition page = definitions.pages().get(pageId);
				if (page != null) {
					collectPageFields(page.root(), fieldIds);
				}
			}
		}
		return fieldIds.stream()
			.map(definitions.fields()::get)
			.filter(Objects::nonNull)
			.filter(field -> field.type() == FieldType.EXCLUSIVE_CHOICE)
			.sorted(Comparator.comparing(FieldDefinition::id))
			.toList();
	}

	private static String parseExclusiveValue(
		final FieldDefinition field,
		final FieldInput input
	) {
		if (!"exclusive_choice".equals(input.type())) {
			throw invalidField(field, "declared type does not match exclusive_choice");
		}
		if (
			input.canonicalJson().isBlank()
				|| input.canonicalJson().getBytes(StandardCharsets.UTF_8).length
					> MAX_FIELD_INPUT_BYTES
		) {
			throw invalidField(field, "value is blank or exceeds the payload bound");
		}
		final JsonElement json;
		try {
			json = JsonParser.parseString(input.canonicalJson());
		} catch (JsonParseException error) {
			throw invalidField(field, "value is not valid JSON");
		}
		JsonPrimitive primitive = primitive(json, field, "exclusive choice");
		String value = primitive.isString() ? primitive.getAsString() : "";
		if (
			value.isEmpty()
				|| field.exclusiveChoice()
					.filter(definition -> definition.values().contains(value))
					.isEmpty()
		) {
			throw invalidField(field, "exclusive choice is not a registered option");
		}
		return value;
	}

	private static FlowPlan flowPlan(
		final DefinitionSnapshot definitions,
		final PanelActionDefinition action
	) {
		Identifier flowId;
		WorldStateV2.CompletionPolicy policy;
		Optional<Identifier> targetRole = Optional.empty();
		if (action.operation() instanceof StartFlowOperation operation) {
			flowId = operation.flow();
			policy = completionPolicy(operation.completionPolicy());
		} else if (
			action.operation() instanceof AssignRoleOperation operation
				&& operation.flow().isPresent()
				&& operation.apply() == ApplyTiming.AFTER_FLOW
		) {
			flowId = operation.flow().orElseThrow();
			policy = completionPolicy(operation.completionPolicy());
			targetRole = Optional.of(operation.role());
		} else {
			return null;
		}
		FlowDefinition flow = definitions.flows().get(flowId);
		if (
			flow == null
				|| !flow.game().equals(action.game())
				|| targetRole.filter(role -> !definitions.roles().containsKey(role)).isPresent()
		) {
			return null;
		}
		return new FlowPlan(flow, policy, targetRole);
	}

	private static WorldStateV2.CompletionPolicy completionPolicy(
		final io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompletionPolicy value
	) {
		return switch (value) {
			case IF_INCOMPLETE -> WorldStateV2.CompletionPolicy.IF_INCOMPLETE;
			case ALWAYS -> WorldStateV2.CompletionPolicy.ALWAYS;
			case RESUME_ONLY -> WorldStateV2.CompletionPolicy.RESUME_ONLY;
		};
	}

	private static List<ConnectedPlayer> selectTargets(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition action,
		final FlowPlan plan,
		final UUID hostId,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final long now
	) {
		FlowDefinition flow = plan.flow();
		Map<UUID, ConnectedPlayer> connected = new LinkedHashMap<>();
		for (ConnectedPlayer player : connectedPlayers) {
			if (connected.put(player.playerId(), player) != null) {
				throw new TargetSelectionException(
					OperationCode.TARGET_INVALID,
					"connected player list contains duplicate UUIDs"
				);
			}
		}
		List<UUID> sortedExplicit = explicitTargetIds.stream().sorted().toList();
		if (sortedExplicit.stream().distinct().count() != sortedExplicit.size()) {
			throw new TargetSelectionException(
				OperationCode.TARGET_INVALID,
				"target list contains duplicate UUIDs"
			);
		}
		if (sortedExplicit.size() > WorldStateV2.MAX_INSTANCE_MEMBERS) {
			throw new TargetSelectionException(
				OperationCode.TARGET_COUNT_INVALID,
				"target count exceeds " + WorldStateV2.MAX_INSTANCE_MEMBERS
			);
		}

		List<ConnectedPlayer> selected = new ArrayList<>();
		if (action.target().mode() == SelectionMode.NONE) {
			if (!sortedExplicit.isEmpty()) {
				throw new TargetSelectionException(
					OperationCode.TARGET_COUNT_INVALID,
					"this operation does not accept explicit targets"
				);
			}
			for (ConnectedPlayer candidate : connected.values()) {
				if (!candidate.online() || !candidate.clientReady()) {
					continue;
				}
				PlayerRecord player = currentPlayer(state, definitions, candidate, now);
				if (
					matchesFilter(definitions, action.target().filter(), state, player)
						&& matchesAudience(definitions, flow.audience(), player, hostId)
						&& needsCurrentFlow(definitions, state, player, plan)
				) {
					selected.add(candidate);
				}
			}
		} else {
			int targetCount = sortedExplicit.size();
			if (
				targetCount < action.target().minimum()
					|| targetCount > action.target().maximum()
			) {
				throw new TargetSelectionException(
					OperationCode.TARGET_COUNT_INVALID,
					"selected target count is outside the action bounds"
				);
			}
			for (UUID targetId : sortedExplicit) {
				ConnectedPlayer candidate = connected.get(targetId);
				if (candidate == null || !candidate.online() || !candidate.clientReady()) {
					throw new TargetSelectionException(
						OperationCode.TARGET_OFFLINE,
						"every selected target must be online with a verified client"
					);
				}
				PlayerRecord player = currentPlayer(state, definitions, candidate, now);
				if (
					!matchesFilter(definitions, action.target().filter(), state, player)
						|| !matchesAudience(definitions, flow.audience(), player, hostId)
						|| !needsCurrentFlow(definitions, state, player, plan)
				) {
					throw new TargetSelectionException(
						OperationCode.TARGET_INVALID,
						"selected target no longer satisfies the operation filter"
					);
				}
				selected.add(candidate);
			}
		}
		selected.sort(Comparator.comparing(ConnectedPlayer::playerId));
		if (selected.size() > WorldStateV2.MAX_INSTANCE_MEMBERS) {
			throw new TargetSelectionException(
				OperationCode.TARGET_COUNT_INVALID,
				"eligible participant count exceeds " + WorldStateV2.MAX_INSTANCE_MEMBERS
			);
		}
		return List.copyOf(selected);
	}

	private static boolean needsCurrentFlow(
		final DefinitionSnapshot definitions,
		final WorldStateV2 state,
		final PlayerRecord player,
		final FlowPlan plan
	) {
		return plan.completionPolicy() != WorldStateV2.CompletionPolicy.IF_INCOMPLETE
			|| !hasCurrentCompletion(definitions, state, player.playerId(), plan.flow().id());
	}

	private static boolean matchesAudience(
		final DefinitionSnapshot definitions,
		final Audience audience,
		final PlayerRecord player,
		final UUID hostId
	) {
		if (audience.excludeHost() && player.playerId().equals(hostId)) {
			return false;
		}
		if (!audience.roles().isEmpty() && !audience.roles().contains(player.roleId())) {
			return false;
		}
		if (
			!audience.teams().isEmpty()
				&& player.teamId().filter(audience.teams()::contains).isEmpty()
		) {
			return false;
		}
		if (!audience.lifeStates().isEmpty() && !audience.lifeStates().contains(player.lifeStateId())) {
			return false;
		}
		Set<Identifier> roleTags = Optional.ofNullable(definitions.roles().get(player.roleId()))
			.map(role -> role.tags())
			.orElse(Set.of());
		Set<Identifier> teamTags = player.teamId()
			.map(definitions.teams()::get)
			.map(team -> team.tags())
			.orElse(Set.of());
		Set<Identifier> lifeTags = Optional.ofNullable(
			definitions.lifeStates().get(player.lifeStateId())
		).map(life -> life.tags()).orElse(Set.of());
		return intersectsOrEmpty(audience.roleTags(), roleTags)
			&& intersectsOrEmpty(audience.teamTags(), teamTags)
			&& intersectsOrEmpty(audience.lifeStateTags(), lifeTags);
	}

	private static boolean matchesFilter(
		final DefinitionSnapshot definitions,
		final TargetFilter filter,
		final WorldStateV2 state,
		final PlayerRecord player
	) {
		if (!filter.roles().isEmpty() && !filter.roles().contains(player.roleId())) {
			return false;
		}
		if (
			!filter.teams().isEmpty()
				&& player.teamId().filter(filter.teams()::contains).isEmpty()
		) {
			return false;
		}
		if (!filter.lifeStates().isEmpty() && !filter.lifeStates().contains(player.lifeStateId())) {
			return false;
		}
		Set<Identifier> roleTags = Optional.ofNullable(definitions.roles().get(player.roleId()))
			.map(role -> role.tags())
			.orElse(Set.of());
		Set<Identifier> teamTags = player.teamId()
			.map(definitions.teams()::get)
			.map(team -> team.tags())
			.orElse(Set.of());
		Set<Identifier> lifeTags = Optional.ofNullable(
			definitions.lifeStates().get(player.lifeStateId())
		).map(life -> life.tags()).orElse(Set.of());
		if (
			!intersectsOrEmpty(filter.roleTags(), roleTags)
				|| !intersectsOrEmpty(filter.teamTags(), teamTags)
				|| !intersectsOrEmpty(filter.lifeStateTags(), lifeTags)
		) {
			return false;
		}
		boolean completedMatch = filter.completedFlows().isEmpty()
			|| filter.completedFlows()
				.stream()
				.anyMatch(
					flowId -> hasCurrentCompletion(
						definitions,
						state,
						player.playerId(),
						flowId
					)
				);
		boolean incompleteMatch = filter.incompleteFlows().isEmpty()
			|| filter.incompleteFlows()
				.stream()
				.anyMatch(
					flowId -> isCurrentFlowIncomplete(
						definitions,
						state,
						player.playerId(),
						flowId
					)
				);
		return completedMatch && incompleteMatch;
	}

	private static boolean hasCurrentCompletion(
		final DefinitionSnapshot definitions,
		final WorldStateV2 state,
		final UUID playerId,
		final Identifier flowId
	) {
		FlowDefinition flow = definitions.flows().get(flowId);
		return flow != null
			&& state.completionHistory()
				.stream()
				.anyMatch(
					record -> record.playerId().equals(playerId)
						&& record.flowId().equals(flowId)
						&& record.flowVersion() == flow.version()
				);
	}

	private static boolean isCurrentFlowIncomplete(
		final DefinitionSnapshot definitions,
		final WorldStateV2 state,
		final UUID playerId,
		final Identifier flowId
	) {
		return definitions.flows().containsKey(flowId)
			&& !hasCurrentCompletion(definitions, state, playerId, flowId);
	}

	private static boolean intersectsOrEmpty(
		final Set<Identifier> expected,
		final Set<Identifier> actual
	) {
		return expected.isEmpty() || expected.stream().anyMatch(actual::contains);
	}

	public static PlayerRecord currentPlayer(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final ConnectedPlayer connected,
		final long now
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(connected, "connected");
		PlayerRecord existing = state.players().get(connected.playerId());
		if (existing != null) {
			if (now < existing.firstSeenAtEpochMillis()) {
				throw new IllegalArgumentException("player timestamp precedes first seen time");
			}
			return copyPlayer(
				existing,
				existing.roleId(),
				existing.persistentFields(),
				existing.pendingRoleChange(),
				existing.activeFlowParticipation(),
				existing.completionSummary(),
				connected.name(),
				Math.max(now, existing.lastSeenAtEpochMillis())
			);
		}
		Identifier gameId = state.activeGameId().orElseThrow(
			() -> new IllegalArgumentException("active game is not configured")
		);
		var game = definitions.games().get(gameId);
		if (game == null) {
			throw new IllegalArgumentException("active game definition is unavailable");
		}
		return new PlayerRecord(
			connected.playerId(),
			connected.name(),
			game.defaultRole(),
			Optional.empty(),
			game.defaultLifeState(),
			Map.of(),
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			now,
			now
		);
	}

	private static PlayerRecord copyPlayer(
		final PlayerRecord source,
		final Identifier roleId,
		final Map<Identifier, PersistentFieldValue> persistentFields,
		final Optional<PendingRoleChange> pendingRoleChange,
		final Optional<PlayerFlowParticipation> participation,
		final CompletionSummary completionSummary,
		final String name,
		final long lastSeenAt
	) {
		return new PlayerRecord(
			source.playerId(),
			name,
			roleId,
			source.teamId(),
			source.lifeStateId(),
			persistentFields,
			pendingRoleChange,
			participation,
			completionSummary,
			source.firstSeenAtEpochMillis(),
			lastSeenAt
		);
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

	private static FieldUpdates validateFieldInputs(
		final DefinitionSnapshot definitions,
		final ForcedFlowInstance instance,
		final FlowMemberState member,
		final PlayerRecord player,
		final Identifier pageId,
		final PredicateEvaluator predicateEvaluator,
		final List<FieldInput> inputs,
		final Optional<ExclusiveInputAuthorization> exclusiveAuthorization
	) {
		PageDefinition page = definitions.pages().get(pageId);
		if (page == null) {
			throw new ActionValidationException(
				OperationCode.SNAPSHOT_INVALID,
				"current page definition is unavailable"
			);
		}
		Set<Identifier> pageFields = new LinkedHashSet<>();
		collectPageFields(page.root(), pageFields);
		Map<Identifier, StoredValue> flowFields = new LinkedHashMap<>(member.flowFields());
		Map<Identifier, PersistentFieldValue> playerFields = new LinkedHashMap<>(
			player.persistentFields()
		);
		Map<Identifier, StoredValue> submitted = new LinkedHashMap<>();
		for (FieldInput input : inputs) {
			if (input == null || submitted.containsKey(input.fieldId())) {
				throw new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"field submission contains a duplicate or null field"
				);
			}
			if (!pageFields.contains(input.fieldId())) {
				throw new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"field is not editable on the current page: " + input.fieldId()
				);
			}
			FieldDefinition field = definitions.fields().get(input.fieldId());
			if (field == null || !field.game().equals(instance.identity().gameId())) {
				throw new ActionValidationException(
					OperationCode.SNAPSHOT_INVALID,
					"current page field definition is unavailable"
				);
			}
			if (!applicableInFlow(field, player, instance)) {
				throw new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"field is not applicable to this player or phase: " + field.id()
				);
			}
			if (
				field.editableBy() != EditPolicy.PLAYER
					&& field.editableBy() != EditPolicy.BOTH
			) {
				throw new ActionValidationException(
					OperationCode.FORBIDDEN,
					"field is not player-editable: " + field.id()
				);
			}
			StoredValue value = parseFieldValue(field, input, exclusiveAuthorization);
			submitted.put(field.id(), value);
			if (field.type() == FieldType.EXCLUSIVE_CHOICE) {
				continue;
			}
			if (field.scope() == FieldScope.FLOW) {
				flowFields.put(field.id(), value);
			} else {
				playerFields.put(field.id(), new PersistentFieldValue(field.version(), value));
			}
		}
		PredicateContext predicateContext = predicateContext(
			instance,
			member,
			flowFields,
			playerFields
		);
		for (Identifier fieldId : submitted.keySet()) {
			FieldDefinition field = definitions.fields().get(fieldId);
			if (!visible(field, predicateEvaluator, predicateContext)) {
				throw new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"hidden field cannot be submitted: " + field.id()
				);
			}
		}
		for (Identifier fieldId : pageFields) {
			FieldDefinition field = definitions.fields().get(fieldId);
			if (field == null || !field.game().equals(instance.identity().gameId())) {
				throw new ActionValidationException(
					OperationCode.SNAPSHOT_INVALID,
					"current page field definition is unavailable"
				);
			}
			if (
				field.required()
					&& applicableInFlow(field, player, instance)
					&& visible(field, predicateEvaluator, predicateContext)
					&& !hasCurrentValue(
						field,
						flowFields,
						playerFields,
						submitted,
						exclusiveAuthorization
					)
			) {
				throw new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"required field is missing: " + field.id()
				);
			}
		}
		return new FieldUpdates(flowFields, playerFields, submitted);
	}

	private static PredicateContext predicateContext(
		final ForcedFlowInstance instance,
		final FlowMemberState member,
		final Map<Identifier, StoredValue> flowFields,
		final Map<Identifier, PersistentFieldValue> playerFields
	) {
		return new PredicateContext(
			member.playerId(),
			instance.identity().instanceId(),
			instance.identity().gameId(),
			instance.identity().flowId(),
			instance.identity().flowVersion(),
			instance.identity().phaseIdAtCreation(),
			flowFields,
			playerFields
		);
	}

	private static boolean visible(
		final FieldDefinition field,
		final PredicateEvaluator predicateEvaluator,
		final PredicateContext context
	) {
		if (field.visibleWhen().isEmpty()) {
			return true;
		}
		Identifier predicate = field.visibleWhen().orElseThrow();
		try {
			return predicateEvaluator.evaluate(predicate, context);
		} catch (RuntimeException error) {
			throw new ActionValidationException(
				OperationCode.SNAPSHOT_INVALID,
				"field visibility predicate failed for "
					+ field.id()
					+ ": "
					+ safeMessage(error)
			);
		}
	}

	private static boolean applicable(
		final FieldDefinition field,
		final PlayerRecord player,
		final Identifier phaseId
	) {
		return (field.roles().isEmpty() || field.roles().contains(player.roleId()))
			&& (field.phases().isEmpty() || field.phases().contains(phaseId));
	}

	private static boolean applicableInFlow(
		final FieldDefinition field,
		final PlayerRecord player,
		final ForcedFlowInstance instance
	) {
		Identifier effectiveRole = player.pendingRoleChange()
			.filter(change ->
				change.status() == PendingRoleChangeStatus.PENDING
					&& change.flowInstanceId().equals(instance.identity().instanceId())
			)
			.map(PendingRoleChange::targetRoleId)
			.orElse(player.roleId());
		return (field.roles().isEmpty() || field.roles().contains(effectiveRole))
			&& (
				field.phases().isEmpty()
					|| field.phases().contains(instance.identity().phaseIdAtCreation())
			);
	}

	private static boolean hasCurrentValue(
		final FieldDefinition field,
		final Map<Identifier, StoredValue> flowFields,
		final Map<Identifier, PersistentFieldValue> playerFields,
		final Map<Identifier, StoredValue> submitted,
		final Optional<ExclusiveInputAuthorization> exclusiveAuthorization
	) {
		if (field.type() == FieldType.EXCLUSIVE_CHOICE) {
			if (
				Optional.ofNullable(submitted.get(field.id()))
					.filter(value -> matchesType(field.type(), value))
					.isPresent()
			) {
				return true;
			}
			if (
				Optional.ofNullable(playerFields.get(field.id()))
					.filter(value -> value.fieldVersion() == field.version())
					.map(PersistentFieldValue::value)
					.filter(value -> matchesType(field.type(), value))
					.isPresent()
			) {
				return true;
			}
			return exclusiveAuthorization
				.flatMap(value -> value.currentValue(field.id()))
				.isPresent();
		}
		if (field.scope() == FieldScope.FLOW) {
			return Optional.ofNullable(flowFields.get(field.id()))
				.filter(value -> matchesType(field.type(), value))
				.isPresent();
		}
		return Optional.ofNullable(playerFields.get(field.id()))
			.filter(value -> value.fieldVersion() == field.version())
			.map(PersistentFieldValue::value)
			.filter(value -> matchesType(field.type(), value))
			.isPresent();
	}

	private static StoredValue parseFieldValue(
		final FieldDefinition field,
		final FieldInput input,
		final Optional<ExclusiveInputAuthorization> exclusiveAuthorization
	) {
		String expectedType = field.type().name().toLowerCase(Locale.ROOT);
		if (!expectedType.equals(input.type())) {
			throw invalidField(field, "declared type does not match " + expectedType);
		}
		if (
			input.canonicalJson().isBlank()
				|| input.canonicalJson().getBytes(StandardCharsets.UTF_8).length
					> MAX_FIELD_INPUT_BYTES
		) {
			throw invalidField(field, "value is blank or exceeds the payload bound");
		}
		final JsonElement json;
		try {
			json = JsonParser.parseString(input.canonicalJson());
		} catch (JsonParseException error) {
			throw invalidField(field, "value is not valid JSON");
		}
		return switch (field.type()) {
			case BOOLEAN -> {
				JsonPrimitive primitive = primitive(json, field, "boolean");
				if (!primitive.isBoolean()) {
					throw invalidField(field, "value must be a boolean");
				}
				yield StoredValue.ofBoolean(primitive.getAsBoolean());
			}
			case INTEGER -> {
				JsonPrimitive primitive = primitive(json, field, "integer");
				final int value;
				try {
					if (
						!primitive.isNumber()
							|| primitive.getAsBigDecimal().stripTrailingZeros().scale() > 0
					) {
						throw invalidField(field, "value must be an integral number");
					}
					value = primitive.getAsBigDecimal().intValueExact();
				} catch (ArithmeticException | NumberFormatException error) {
					throw invalidField(field, "integer value is outside the supported range");
				}
				if (
					field.constraints().minimum().filter(minimum -> value < minimum).isPresent()
						|| field.constraints().maximum().filter(maximum -> value > maximum).isPresent()
				) {
					throw invalidField(field, "integer value violates min/max");
				}
				yield StoredValue.ofInteger(value);
			}
			case STRING -> {
				JsonPrimitive primitive = primitive(json, field, "string");
				if (!primitive.isString()) {
					throw invalidField(field, "value must be a string");
				}
				String value = primitive.getAsString();
				int length = value.codePointCount(0, value.length());
				if (
					value.length() > 32_767
						|| field.constraints().minimumLength()
							.filter(minimum -> length < minimum)
							.isPresent()
						|| field.constraints().maximumLength()
							.filter(maximum -> length > maximum)
							.isPresent()
				) {
					throw invalidField(field, "string value violates length constraints");
				}
				yield StoredValue.ofString(value);
			}
			case IDENTIFIER -> {
				JsonPrimitive primitive = primitive(json, field, "identifier");
				if (!primitive.isString()) {
					throw invalidField(field, "identifier value must be a string");
				}
				String value = primitive.getAsString();
				int separator = value.indexOf(':');
				Identifier identifier = separator <= 0 || separator == value.length() - 1
					? null
					: Identifier.tryParse(value);
				if (identifier == null) {
					throw invalidField(field, "identifier value is invalid");
				}
				yield StoredValue.ofIdentifier(identifier);
			}
			case SINGLE_CHOICE -> {
				JsonPrimitive primitive = primitive(json, field, "single choice");
				if (
					!primitive.isString()
						|| !field.constraints().options().contains(primitive.getAsString())
				) {
					throw invalidField(field, "single choice is not a registered option");
				}
				yield StoredValue.singleChoice(primitive.getAsString());
			}
			case EXCLUSIVE_CHOICE -> {
				JsonPrimitive primitive = primitive(json, field, "exclusive choice");
				String value = primitive.isString() ? primitive.getAsString() : "";
				if (
					value.isEmpty()
						|| field.exclusiveChoice()
							.filter(definition -> definition.values().contains(value))
							.isEmpty()
				) {
					throw invalidField(field, "exclusive choice is not a registered option");
				}
				if (
					exclusiveAuthorization
						.filter(authorization -> authorization.authorized(field.id(), value))
						.isEmpty()
				) {
					throw invalidField(
						field,
						"exclusive choice requires an authoritative reservation transaction"
					);
				}
				// ponytail: the field definition carries exclusivity; the persisted scalar reuses the
				// existing single-choice payload instead of expanding the schema-v2 value union.
				yield StoredValue.singleChoice(value);
			}
			case MULTI_CHOICE -> parseMultiChoice(field, json);
		};
	}

	private static JsonPrimitive primitive(
		final JsonElement json,
		final FieldDefinition field,
		final String expected
	) {
		if (!json.isJsonPrimitive()) {
			throw invalidField(field, expected + " value must be a JSON primitive");
		}
		return json.getAsJsonPrimitive();
	}

	private static StoredValue parseMultiChoice(
		final FieldDefinition field,
		final JsonElement json
	) {
		if (!json.isJsonArray()) {
			throw invalidField(field, "multi choice value must be an array");
		}
		JsonArray array = json.getAsJsonArray();
		Set<String> selected = new LinkedHashSet<>();
		for (JsonElement element : array) {
			if (
				!element.isJsonPrimitive()
					|| !element.getAsJsonPrimitive().isString()
					|| !field.constraints().options().contains(element.getAsString())
					|| !selected.add(element.getAsString())
			) {
				throw invalidField(
					field,
					"multi choice contains an invalid or duplicate option"
				);
			}
		}
		int minimum = field.constraints().minimumSelections().orElse(0);
		int maximum = field.constraints().maximumSelections()
			.orElse(field.constraints().options().size());
		if (selected.size() < minimum || selected.size() > maximum) {
			throw invalidField(field, "multi choice violates selection limits");
		}
		List<String> canonical = field.constraints().options()
			.stream()
			.filter(selected::contains)
			.toList();
		return StoredValue.multiChoice(canonical);
	}

	private static boolean matchesType(
		final FieldType type,
		final StoredValue value
	) {
		if (type == FieldType.EXCLUSIVE_CHOICE) {
			return value.type() == WorldStateV2.FieldValueType.SINGLE_CHOICE;
		}
		return value.type().getSerializedName().equals(type.name().toLowerCase(Locale.ROOT));
	}

	private static ActionValidationException invalidField(
		final FieldDefinition field,
		final String message
	) {
		return new ActionValidationException(
			OperationCode.TARGET_INVALID,
			"invalid field " + field.id() + ": " + message
		);
	}

	private static String requiredChoice(
		final FieldUpdates updates,
		final Identifier fieldId
	) {
		return Optional.ofNullable(updates.submitted().get(fieldId))
			.filter(value -> value.type() == WorldStateV2.FieldValueType.SINGLE_CHOICE)
			.flatMap(StoredValue::stringValue)
			.orElseThrow(
				() -> new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"choice node requires its single_choice field in the same submission"
				)
			);
	}

	private static String choiceNext(
		final ChoiceNode choice,
		final FieldUpdates updates
	) {
		String selected = requiredChoice(updates, choice.field());
		return choice.choices()
			.stream()
			.filter(route -> route.value().equals(selected))
			.findFirst()
			.orElseThrow(
				() -> new ActionValidationException(
					OperationCode.TARGET_INVALID,
					"choice value has no registered route"
				)
			)
			.next();
	}

	private static NodeReduction reducePlayerBranches(
		final DefinitionSnapshot definitions,
		final FlowDefinition flow,
		final String firstNodeId,
		final PredicateEvaluator evaluator,
		final PredicateContext context
	) {
		Set<String> visited = new LinkedHashSet<>();
		String nodeId = firstNodeId;
		while (true) {
			FlowNode node = flow.nodes().get(nodeId);
			if (node == null) {
				throw new ActionValidationException(
					OperationCode.SNAPSHOT_INVALID,
					"next flow node is missing"
				);
			}
			if (node instanceof BranchNode branch) {
				if (
					branch.scope() != NodeScope.PLAYER
						|| evaluator == NO_PREDICATE_EVALUATOR
				) {
					throw new ActionValidationException(
						OperationCode.UNSUPPORTED_FLOW_NODE,
						"player branch requires an injected predicate evaluator"
					);
				}
				if (!visited.add(nodeId) || visited.size() > flow.nodes().size()) {
					throw new ActionValidationException(
						OperationCode.SNAPSHOT_INVALID,
						"player branch chain contains a cycle"
					);
				}
				String selected = branch.fallback();
				for (var branchCase : branch.cases()) {
					if (!definitions.predicates().contains(branchCase.predicate())) {
						throw new ActionValidationException(
							OperationCode.SNAPSHOT_INVALID,
							"branch predicate is unavailable: " + branchCase.predicate()
						);
					}
					final boolean matches;
					try {
						matches = evaluator.evaluate(branchCase.predicate(), context);
					} catch (RuntimeException error) {
						throw new ActionValidationException(
							OperationCode.RESOURCE_BLOCKED,
							"branch predicate failed: " + safeMessage(error)
						);
					}
					if (matches) {
						selected = branchCase.next();
						break;
					}
				}
				nodeId = selected;
				continue;
			}
			if (
				node instanceof PageNode
					|| node instanceof ChoiceNode
					|| node instanceof ConfirmNode
			) {
				return new NodeReduction(nodeId, false);
			}
			if (node instanceof CompleteNode complete && complete.scope() == NodeScope.PLAYER) {
				return new NodeReduction(nodeId, true);
			}
			throw new ActionValidationException(
				OperationCode.UNSUPPORTED_FLOW_NODE,
				"next node is not executable in 2C: " + node.id()
			);
		}
	}

	private static boolean validPageAction(
		final DefinitionSnapshot definitions,
		final Identifier pageId,
		final String actionId
	) {
		PageDefinition page = definitions.pages().get(pageId);
		if (page == null) {
			return false;
		}
		Set<String> actions = new LinkedHashSet<>();
		collectPageActions(page.root(), actions);
		return actions.contains(actionId);
	}

	private static void collectPageActions(
		final NodeDefinition node,
		final Set<String> actions
	) {
		switch (node.content()) {
			case ButtonContent button -> {
				if (button.action().type() == ActionType.FLOW) {
					button.action().name().ifPresent(actions::add);
				}
			}
			case ChildrenContent children -> children.children()
				.forEach(child -> collectPageActions(child, actions));
			case SingleChildContent single -> collectPageActions(single.child(), actions);
			case RepeatContent repeat -> collectPageActions(repeat.template(), actions);
			default -> {
			}
		}
	}

	private static void collectPageFields(
		final NodeDefinition node,
		final Set<Identifier> fields
	) {
		switch (node.content()) {
			case FieldInputContent input -> fields.add(input.field());
			case ChildrenContent children -> children.children()
				.forEach(child -> collectPageFields(child, fields));
			case SingleChildContent single -> collectPageFields(single.child(), fields);
			case RepeatContent repeat -> collectPageFields(repeat.template(), fields);
			default -> {
			}
		}
	}

	private static AuditLog appendAudit(
		final AuditLog audit,
		final AuditEventType type,
		final long occurredAt,
		final Optional<UUID> actor,
		final Optional<UUID> target,
		final Optional<UUID> flow,
		final String summary
	) {
		return audit.append(
			new AuditEvent(
				audit.totalEvents(),
				type,
				occurredAt,
				actor,
				target,
				flow,
				summary
			)
		);
	}

	private static boolean unfinished(final FlowInstanceStatus status) {
		return status == FlowInstanceStatus.ACTIVE || status == FlowInstanceStatus.BLOCKED;
	}

	private static OperationCode operationCode(final String serializedName) {
		return OperationCode.bySerializedName(serializedName).orElse(OperationCode.SNAPSHOT_INVALID);
	}

	private static String safeMessage(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	private record ExclusiveInputAuthorization(
		WorldStateV3 state,
		UUID scopeId,
		UUID playerId,
		UUID flowInstanceId,
		Map<Identifier, String> submitted
	) {
		private ExclusiveInputAuthorization {
			state = Objects.requireNonNull(state, "state");
			scopeId = Objects.requireNonNull(scopeId, "scopeId");
			playerId = Objects.requireNonNull(playerId, "playerId");
			flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			submitted = Map.copyOf(submitted);
		}

		private boolean authorized(final Identifier fieldId, final String value) {
			return this.submitted.getOrDefault(fieldId, "").equals(value)
				&& currentValue(fieldId).filter(value::equals).isPresent();
		}

		private Optional<String> currentValue(final Identifier fieldId) {
			String submittedValue = this.submitted.get(fieldId);
			if (submittedValue != null) {
				return Optional.of(submittedValue);
			}
			return this.state.exclusiveReservations()
				.stream()
				.filter(reservation ->
					reservation.fieldId().equals(fieldId)
						&& reservation.scopeId().equals(this.scopeId)
						&& reservation.ownerId().equals(this.playerId)
						&& (
							reservation.status() == ReservationStatus.LOCKED
								|| (
									reservation.status() == ReservationStatus.HELD
										&& reservation.flowInstanceId()
											.filter(this.flowInstanceId::equals)
											.isPresent()
								)
						)
				)
				.sorted(
					Comparator.comparing(
						reservation -> reservation.status() == ReservationStatus.HELD ? 0 : 1
					)
				)
				.map(ExclusiveReservation::value)
				.findFirst();
		}
	}

	private record ExclusivePreparation(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		Optional<ExclusiveInputAuthorization> authorization,
		Optional<ExclusiveChoiceAuthority.Conflict> conflict
	) {
		private ExclusivePreparation {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			authorization = Objects.requireNonNull(authorization, "authorization");
			conflict = Objects.requireNonNull(conflict, "conflict");
		}

		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static ExclusivePreparation succeeded(
			final WorldStateV3 state,
			final ExclusiveInputAuthorization authorization
		) {
			return new ExclusivePreparation(
				OperationCode.SUCCESS,
				"",
				Optional.of(state),
				Optional.of(authorization),
				Optional.empty()
			);
		}

		private static ExclusivePreparation failed(
			final OperationCode code,
			final String message,
			final Optional<ExclusiveChoiceAuthority.Conflict> conflict
		) {
			return new ExclusivePreparation(
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				conflict
			);
		}
	}

	private record ExclusiveCompletion(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState
	) {
		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private WorldStateV3 state() {
			return this.nextState.orElseThrow();
		}

		private static ExclusiveCompletion succeeded(final WorldStateV3 state) {
			return new ExclusiveCompletion(
				OperationCode.SUCCESS,
				"",
				Optional.of(state)
			);
		}

		private static ExclusiveCompletion failed(
			final OperationCode code,
			final String message
		) {
			return new ExclusiveCompletion(code, message, Optional.empty());
		}
	}

	record CapacityFailure(OperationCode code, String message) {
	}

	private record FlowPlan(
		FlowDefinition flow,
		WorldStateV2.CompletionPolicy completionPolicy,
		Optional<Identifier> targetRoleId
	) {
	}

	private record FieldUpdates(
		Map<Identifier, StoredValue> flowFields,
		Map<Identifier, PersistentFieldValue> playerFields,
		Map<Identifier, StoredValue> submitted
	) {
		private FieldUpdates {
			flowFields = Map.copyOf(flowFields);
			playerFields = Map.copyOf(playerFields);
			submitted = Map.copyOf(submitted);
		}
	}

	private record NodeReduction(String nodeId, boolean completed) {
	}

	private static final class ActionValidationException extends RuntimeException {
		private final OperationCode code;

		private ActionValidationException(
			final OperationCode code,
			final String message
		) {
			super(message);
			this.code = Objects.requireNonNull(code, "code");
		}
	}

	private static final class TargetSelectionException extends RuntimeException {
		private final OperationCode code;

		private TargetSelectionException(final OperationCode code, final String message) {
			super(message);
			this.code = Objects.requireNonNull(code, "code");
		}
	}

	@FunctionalInterface
	public interface PredicateEvaluator {
		boolean evaluate(Identifier predicateId, PredicateContext context);
	}

	/**
	 * Explicit game-instance scope and server clock used by schema-v3 exclusive transactions.
	 */
	public record ExclusiveContext(
		UUID scopeId,
		long serverTick,
		Supplier<UUID> reservationIds
	) {
		public ExclusiveContext {
			scopeId = Objects.requireNonNull(scopeId, "scopeId");
			reservationIds = Objects.requireNonNull(reservationIds, "reservationIds");
			if (serverTick < 0L) {
				throw new IllegalArgumentException("exclusive server tick cannot be negative");
			}
		}

		private UUID nextReservationId() {
			return Objects.requireNonNull(this.reservationIds.get(), "reservation ID");
		}
	}

	public record PanelActionAuthorization(
		Identifier actionId,
		long definitionGeneration,
		long stateRevision,
		UUID actorId,
		boolean visibleAllowed,
		boolean enabledAllowed
	) {
		public PanelActionAuthorization {
			actionId = Objects.requireNonNull(actionId, "actionId");
			actorId = Objects.requireNonNull(actorId, "actorId");
		}
	}

	public record PredicateContext(
		UUID playerId,
		UUID flowInstanceId,
		Identifier gameId,
		Identifier flowId,
		int flowVersion,
		Identifier phaseId,
		Map<Identifier, StoredValue> flowFields,
		Map<Identifier, PersistentFieldValue> playerFields
	) {
		public PredicateContext {
			playerId = Objects.requireNonNull(playerId, "playerId");
			flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			gameId = Objects.requireNonNull(gameId, "gameId");
			flowId = Objects.requireNonNull(flowId, "flowId");
			phaseId = Objects.requireNonNull(phaseId, "phaseId");
			flowFields = Map.copyOf(flowFields);
			playerFields = Map.copyOf(playerFields);
			if (flowVersion <= 0) {
				throw new IllegalArgumentException("predicate flow version must be positive");
			}
		}
	}

	public record ConnectedPlayer(
		UUID playerId,
		String name,
		boolean online,
		boolean clientReady
	) {
		public ConnectedPlayer {
			playerId = Objects.requireNonNull(playerId, "playerId");
			name = Objects.requireNonNull(name, "name");
			if (name.isBlank() || name.length() > 64) {
				throw new IllegalArgumentException("player name must be non-blank and at most 64 characters");
			}
			if (clientReady && !online) {
				throw new IllegalArgumentException("an offline player cannot have a ready client");
			}
		}
	}

	public record FieldInput(Identifier fieldId, String type, String canonicalJson) {
		public FieldInput {
			fieldId = Objects.requireNonNull(fieldId, "fieldId");
			type = Objects.requireNonNull(type, "type");
			canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
		}
	}

	public record Submission(
		UUID playerId,
		UUID flowInstanceId,
		UUID pageInstanceId,
		Identifier flowId,
		int flowVersion,
		String nodeId,
		long instanceRevision,
		long memberRevision,
		long requestSequence,
		String actionId,
		List<FieldInput> fieldValues
	) {
		public Submission {
			playerId = Objects.requireNonNull(playerId, "playerId");
			flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			flowId = Objects.requireNonNull(flowId, "flowId");
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			actionId = Objects.requireNonNull(actionId, "actionId");
			fieldValues = List.copyOf(fieldValues);
		}
	}

	public enum ExclusiveChoiceMutationOperation {
		HOLD,
		RELEASE
	}

	public record ExclusiveChoiceMutation(
		UUID playerId,
		UUID flowInstanceId,
		UUID pageInstanceId,
		Identifier flowId,
		int flowVersion,
		String nodeId,
		long instanceRevision,
		long memberRevision,
		long requestSequence,
		Identifier fieldId,
		ExclusiveChoiceMutationOperation operation,
		Optional<String> value
	) {
		public ExclusiveChoiceMutation {
			playerId = Objects.requireNonNull(playerId, "playerId");
			flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			flowId = Objects.requireNonNull(flowId, "flowId");
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			fieldId = Objects.requireNonNull(fieldId, "fieldId");
			operation = Objects.requireNonNull(operation, "operation");
			value = Objects.requireNonNull(value, "value");
			if (flowVersion <= 0) {
				throw new IllegalArgumentException("flowVersion must be positive");
			}
			if (
				instanceRevision < 0L
					|| memberRevision < 0L
					|| requestSequence < 0L
			) {
				throw new IllegalArgumentException("exclusive mutation revisions must be non-negative");
			}
			if (
				(operation == ExclusiveChoiceMutationOperation.HOLD) != value.isPresent()
					|| value.filter(String::isBlank).isPresent()
			) {
				throw new IllegalArgumentException(
					"hold requires one value and release must omit it"
				);
			}
		}
	}

	public record StartResult(
		OperationCode code,
		String message,
		Optional<WorldStateV2> nextState,
		Optional<UUID> instanceId,
		boolean completedImmediately
	) {
		public StartResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			if (code != OperationCode.SUCCESS && nextState.isPresent()) {
				throw new IllegalArgumentException("a failed start cannot contain state");
			}
			if (nextState.isPresent() != instanceId.isPresent()) {
				throw new IllegalArgumentException("flow state and instance ID must be paired");
			}
			if (completedImmediately != (code == OperationCode.SUCCESS && nextState.isEmpty())) {
				throw new IllegalArgumentException(
					"only a successful all-complete result may omit the candidate state"
				);
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static StartResult succeeded(
			final WorldStateV2 state,
			final UUID instanceId
		) {
			return new StartResult(
				OperationCode.SUCCESS,
				"",
				Optional.of(state),
				Optional.of(instanceId),
				false
			);
		}

		private static StartResult completedWithoutChange() {
			return new StartResult(
				OperationCode.SUCCESS,
				"all selected players already completed this flow",
				Optional.empty(),
				Optional.empty(),
				true
			);
		}

		private static StartResult failed(final OperationCode code, final String message) {
			return new StartResult(code, message, Optional.empty(), Optional.empty(), false);
		}
	}

	public record V3StartResult(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		Optional<UUID> instanceId,
		boolean completedImmediately
	) {
		public V3StartResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			if (
				(code == OperationCode.SUCCESS) != (nextState.isPresent() || completedImmediately)
					|| nextState.isPresent() != instanceId.isPresent()
					|| (completedImmediately && nextState.isPresent())
			) {
				throw new IllegalArgumentException("schema-v3 start result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static V3StartResult succeeded(
			final WorldStateV3 state,
			final UUID instanceId
		) {
			return new V3StartResult(
				OperationCode.SUCCESS,
				"",
				Optional.of(state),
				Optional.of(instanceId),
				false
			);
		}

		private static V3StartResult completedWithoutChange(final String message) {
			return new V3StartResult(
				OperationCode.SUCCESS,
				message,
				Optional.empty(),
				Optional.empty(),
				true
			);
		}

		private static V3StartResult failed(final OperationCode code, final String message) {
			return new V3StartResult(
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				false
			);
		}
	}

	public record ActionResult(
		OperationCode code,
		String message,
		Optional<WorldStateV2> nextState,
		boolean memberCompleted,
		boolean flowCompleted
	) {
		public ActionResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if ((code == OperationCode.SUCCESS) != nextState.isPresent()) {
				throw new IllegalArgumentException("only a successful action may contain state");
			}
			if (flowCompleted && !memberCompleted) {
				throw new IllegalArgumentException("a flow cannot complete without the submitting member");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static ActionResult succeeded(
			final WorldStateV2 state,
			final boolean memberCompleted,
			final boolean flowCompleted
		) {
			return new ActionResult(
				OperationCode.SUCCESS,
				"",
				Optional.of(state),
				memberCompleted,
				flowCompleted
			);
		}

		private static ActionResult failed(final OperationCode code, final String message) {
			return new ActionResult(code, message, Optional.empty(), false, false);
		}
	}

	public record V3ActionResult(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		boolean memberCompleted,
		boolean flowCompleted,
		Optional<ExclusiveChoiceAuthority.Conflict> conflict
	) {
		public V3ActionResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			conflict = Objects.requireNonNull(conflict, "conflict");
			if (
				(code == OperationCode.SUCCESS) != nextState.isPresent()
					|| (flowCompleted && !memberCompleted)
					|| (conflict.isPresent() != (code == OperationCode.EXCLUSIVE_OPTION_TAKEN))
			) {
				throw new IllegalArgumentException("schema-v3 action result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static V3ActionResult succeeded(
			final WorldStateV3 state,
			final boolean memberCompleted,
			final boolean flowCompleted
		) {
			return new V3ActionResult(
				OperationCode.SUCCESS,
				"",
				Optional.of(state),
				memberCompleted,
				flowCompleted,
				Optional.empty()
			);
		}

		private static V3ActionResult failed(
			final OperationCode code,
			final String message,
			final Optional<ExclusiveChoiceAuthority.Conflict> conflict
		) {
			return new V3ActionResult(
				code,
				message,
				Optional.empty(),
				false,
				false,
				conflict
			);
		}
	}

	public record ExclusiveChoiceMutationResult(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		Optional<ExclusiveChoiceAuthority.Conflict> conflict
	) {
		public ExclusiveChoiceMutationResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			conflict = Objects.requireNonNull(conflict, "conflict");
			if (
				(code == OperationCode.SUCCESS) != nextState.isPresent()
					|| (conflict.isPresent() != (code == OperationCode.EXCLUSIVE_OPTION_TAKEN))
			) {
				throw new IllegalArgumentException("exclusive mutation result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static ExclusiveChoiceMutationResult succeeded(
			final WorldStateV3 state,
			final String message
		) {
			return new ExclusiveChoiceMutationResult(
				OperationCode.SUCCESS,
				message,
				Optional.of(state),
				Optional.empty()
			);
		}

		private static ExclusiveChoiceMutationResult failed(
			final OperationCode code,
			final String message,
			final Optional<ExclusiveChoiceAuthority.Conflict> conflict
		) {
			return new ExclusiveChoiceMutationResult(
				code,
				message,
				Optional.empty(),
				conflict
			);
		}
	}

	public record TargetResult(
		OperationCode code,
		String message,
		List<ConnectedPlayer> targets
	) {
		public TargetResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			targets = List.copyOf(targets);
			if (code != OperationCode.SUCCESS && !targets.isEmpty()) {
				throw new IllegalArgumentException("failed target resolution cannot contain targets");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static TargetResult succeeded(final List<ConnectedPlayer> targets) {
			return new TargetResult(OperationCode.SUCCESS, "", targets);
		}

		private static TargetResult failed(final OperationCode code, final String message) {
			return new TargetResult(code, message, List.of());
		}
	}
}
