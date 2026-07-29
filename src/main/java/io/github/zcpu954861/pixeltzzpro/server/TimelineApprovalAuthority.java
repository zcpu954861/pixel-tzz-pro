package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RequiredFieldRequirement;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskTimeline;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.TaskStart;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FieldValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionCause;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionTrigger;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure, atomic authority for the host's final pre-start approval.
 *
 * <p>The caller supplies only trusted server facts and fresh instance IDs. No callback, network,
 * phase hook, or world side effect runs here; a successful result is one immutable state candidate
 * containing the frozen first task and a persisted start-phase transition trigger.
 */
public final class TimelineApprovalAuthority {
	private TimelineApprovalAuthority() {
	}

	public static ApprovalResult approve(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final ApprovalContext context
	) {
		if (state == null || definitions == null || context == null) {
			return ApprovalResult.rejected(
				OperationCode.TARGET_INVALID,
				"timeline approval input is missing"
			);
		}
		if (state.validated().result().isEmpty()) {
			return ApprovalResult.rejected(
				OperationCode.SCHEMA_BLOCKED,
				"world state is not writable"
			);
		}
		if (state.stateRevision() != context.expectedStateRevision()) {
			return ApprovalResult.rejected(
				OperationCode.STATE_REVISION_STALE,
				"world state changed before timeline approval"
			);
		}
		if (definitions.generation() != context.expectedDefinitionGeneration()) {
			return ApprovalResult.rejected(
				OperationCode.DEFINITION_GENERATION_STALE,
				"data-pack definition generation changed before timeline approval"
			);
		}
		if (
			state.core()
				.host()
				.map(host -> host.playerId().equals(context.actorId()))
				.orElse(false) == false
		) {
			return ApprovalResult.rejected(
				OperationCode.NOT_HOST,
				"only the current host may approve the timeline"
			);
		}

		Identifier gameId = state.core().activeGameId().orElse(null);
		Identifier phaseId = state.core().activePhaseId().orElse(null);
		if (
			gameId == null
				|| phaseId == null
				|| state.activeGameInstanceId()
					.filter(context.gameInstanceId()::equals)
					.isEmpty()
		) {
			return ApprovalResult.rejected(
				OperationCode.PHASE_MISMATCH,
				"the approval does not target the active game instance"
			);
		}
		GameDefinition game = definitions.games().get(gameId);
		if (game == null) {
			return ApprovalResult.rejected(
				OperationCode.DEFINITION_UNAVAILABLE,
				"the active game definition is unavailable"
			);
		}
		TaskTimeline liveTimeline = game.taskTimeline().orElse(null);
		if (liveTimeline == null) {
			return ApprovalResult.rejected(
				OperationCode.TIMELINE_NOT_ACTIVE,
				"the active game has no registered task timeline"
			);
		}
		if (state.timeline().isPresent()) {
			return ApprovalResult.rejected(
				OperationCode.TIMELINE_ALREADY_ACTIVE,
				"an unfinished or retained timeline already exists"
			);
		}
		if (!phaseId.equals(liveTimeline.approvalPhase())) {
			return ApprovalResult.rejected(
				OperationCode.PHASE_MISMATCH,
				"the active phase does not allow timeline approval"
			);
		}
		PhaseDefinition approvalPhase = definitions.phases().get(phaseId);
		PhaseDefinition startPhase = definitions.phases().get(liveTimeline.startPhase());
		if (
			approvalPhase == null
				|| startPhase == null
				|| !approvalPhase.game().equals(gameId)
				|| !startPhase.game().equals(gameId)
				|| (
					!phaseId.equals(liveTimeline.startPhase())
						&& !approvalPhase.transitions().contains(liveTimeline.startPhase())
				)
		) {
			return ApprovalResult.rejected(
				OperationCode.PHASE_MISMATCH,
				"approval phase cannot transition to the registered start phase"
			);
		}
		if (
			state.core()
				.activeForcedFlow()
				.map(flow -> flow.runtime().status())
				.filter(TimelineApprovalAuthority::unfinishedFlow)
				.isPresent()
		) {
			return ApprovalResult.rejected(
				OperationCode.ACTIVE_FLOW_EXISTS,
				"an unfinished forced flow still blocks timeline approval"
			);
		}
		if (game.readiness().isPresent()) {
			var readinessDefinition = game.readiness().orElseThrow();
			var readiness = state.readiness().orElse(null);
			var action = definitions.panelActions().get(readinessDefinition.action());
			StartFlowOperation readinessOperation =
				action != null && action.operation() instanceof StartFlowOperation value
					? value
					: null;
			boolean readinessMatches = readiness != null
				&& readiness.gameInstanceId().equals(context.gameInstanceId())
				&& readiness.flow().identity().gameId().equals(gameId)
				&& readiness.flow().identity().phaseIdAtCreation().equals(readinessDefinition.phase())
				&& readiness.flow().identity().sourceActionId().equals(readinessDefinition.action())
				&& readinessOperation != null
				&& readiness.flow().identity().flowId().equals(readinessOperation.flow());
			if (!readinessMatches) {
				return ApprovalResult.rejected(
					OperationCode.ACTION_UNAVAILABLE,
					"player readiness session is missing or does not match this game instance"
				);
			}
			var runtime = readiness.flow().runtime();
			if (
				runtime.status() != FlowInstanceStatus.COMPLETED
					|| runtime.completedCount() != runtime.totalCount()
			) {
				return ApprovalResult.rejected(
					OperationCode.ACTION_UNAVAILABLE,
					"not every readiness participant has confirmed"
				);
			}
		}

		TimelineSnapshotCompiler.FreezeResult freeze =
			TimelineSnapshotCompiler.freeze(definitions, game);
		if (!freeze.success()) {
			return ApprovalResult.rejected(
				snapshotCode(freeze.code()),
				freeze.message()
			);
		}
		DefinitionSnapshot frozen = freeze.compiled().orElseThrow();
		GameDefinition frozenGame = frozen.games().get(gameId);
		if (frozenGame == null || frozenGame.taskTimeline().isEmpty()) {
			return ApprovalResult.rejected(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen snapshot lost its game timeline"
			);
		}
		TaskTimeline frozenTimeline = frozenGame.taskTimeline().orElseThrow();
		ApprovalResult requirementFailure = requirementFailure(
			state,
			frozen,
			frozenGame,
			frozenTimeline,
			context.onlinePlayerIds()
		);
		if (requirementFailure != null) {
			return requirementFailure;
		}

		TaskDefinition firstTask = frozen.tasks().get(frozenTimeline.initialTask());
		if (firstTask == null || !firstTask.game().equals(gameId)) {
			return ApprovalResult.rejected(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen initial task is unavailable"
			);
		}
		List<UUID> participants = state.core()
			.players()
			.values()
			.stream()
			.filter(player ->
				AudienceMatcher.matches(
					frozen,
					firstTask.audience(),
					player,
					state.core().host().map(host -> host.playerId()),
					context.onlinePlayerIds().contains(player.playerId())
				)
			)
			.map(PlayerRecord::playerId)
			.sorted()
			.toList();

		TimelineAuthority.OperationResult created = TimelineAuthority.create(
			Optional.empty(),
			context.timelineInstanceId(),
			frozenGame.contentVersion(),
			freeze.frozen().orElseThrow(),
			new TaskStart(context.firstTaskInstanceId(), firstTask, participants),
			context.serverTick()
		);
		if (!created.successful() || created.nextState().isEmpty()) {
			return ApprovalResult.rejected(created.code(), created.message());
		}
		final long nextStateRevision;
		try {
			nextStateRevision = Math.incrementExact(state.stateRevision());
		} catch (ArithmeticException error) {
			return ApprovalResult.rejected(
				OperationCode.RESOURCE_BLOCKED,
				"world state revision capacity is exhausted"
			);
		}

		try {
			TimelineInstance timeline = created.nextState().orElseThrow();
			if (!phaseId.equals(frozenTimeline.startPhase())) {
				timeline = withApprovalPhaseTransition(
					timeline,
					phaseId,
					frozenTimeline.startPhase(),
					context.timelineInstanceId()
				);
			}
			WorldStateV3 next = state
				.withCore(
					state.core()
						.withStateRevision(nextStateRevision)
				)
				.withTimeline(Optional.of(timeline));
			if (next.validated().result().isEmpty()) {
				return ApprovalResult.rejected(
					OperationCode.INTERNAL_ERROR,
					"approved timeline candidate failed state validation"
				);
			}
			return ApprovalResult.approved(next, participants);
		} catch (RuntimeException error) {
			return ApprovalResult.rejected(
				OperationCode.INTERNAL_ERROR,
				safeMessage(error)
			);
		}
	}

	private static TimelineInstance withApprovalPhaseTransition(
		final TimelineInstance timeline,
		final Identifier fromPhase,
		final Identifier toPhase,
		final UUID transitionId
	) {
		TaskInstance task = timeline.currentTask().orElseThrow();
		TaskInstance stagedTask = new TaskInstance(
			task.taskInstanceId(),
			task.taskId(),
			task.kind(),
			task.status(),
			task.resumeStatus(),
			task.elapsedTicks(),
			task.result(),
			task.intermission(),
			task.participants(),
			task.startedAtGameTick(),
			task.settledAtGameTick(),
			task.callbackLedger(),
			task.lifecycleCallbackTrigger(),
			Optional.of(
				new PhaseTransitionTrigger(
					transitionId,
					fromPhase,
					toPhase,
					PhaseTransitionCause.APPROVAL,
					timeline.timelineRevision()
				)
			),
			task.events(),
			task.statistics()
		);
		return new TimelineInstance(
			timeline.instanceId(),
			timeline.gameId(),
			timeline.contentVersion(),
			timeline.snapshot(),
			timeline.status(),
			timeline.resumeStatus(),
			Optional.of(stagedTask),
			timeline.taskHistory(),
			timeline.gameElapsedTicks(),
			timeline.paused(),
			timeline.pauseReason(),
			timeline.createdAtServerTick(),
			timeline.startedAtServerTick(),
			timeline.completedAtServerTick(),
			timeline.timelineRevision(),
			timeline.callbackLedger()
		);
	}

	private static ApprovalResult requirementFailure(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final GameDefinition game,
		final TaskTimeline timeline,
		final Set<UUID> onlinePlayerIds
	) {
		Optional<UUID> hostId = state.core().host().map(host -> host.playerId());
		UUID scopeId = state.activeGameInstanceId().orElseThrow();
		for (RequiredFieldRequirement requirement : timeline.preStartRequirements()) {
			FieldDefinition field = definitions.fields().get(requirement.field());
			if (
				field == null
					|| !field.game().equals(game.id())
					|| field.scope() != FieldScope.PLAYER
					|| !field.required()
			) {
				return ApprovalResult.rejected(
					OperationCode.SNAPSHOT_INVALID,
					"frozen pre-start requirement has an invalid field: " + requirement.field()
				);
			}
			for (PlayerRecord player : state.core().players().values()) {
				if (
					!AudienceMatcher.matches(
						definitions,
						requirement.audience(),
						player,
						hostId,
						onlinePlayerIds.contains(player.playerId())
					)
				) {
					continue;
				}
				PersistentFieldValue stored = player.persistentFields().get(field.id());
				if (
					!field.roles().isEmpty()
						&& !field.roles().contains(player.roleId())
					|| !validCurrentValue(field, stored)
				) {
					return incomplete(field, player);
				}
				if (
					field.type() == FieldType.EXCLUSIVE_CHOICE
						&& !hasMatchingLock(state, field, scopeId, player, stored)
				) {
					return incomplete(field, player);
				}
			}
		}
		return null;
	}

	private static ApprovalResult incomplete(
		final FieldDefinition field,
		final PlayerRecord player
	) {
		return ApprovalResult.rejected(
			field.type() == FieldType.EXCLUSIVE_CHOICE
				? OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE
				: OperationCode.ACTION_UNAVAILABLE,
			"player "
				+ player.playerId()
				+ " has not satisfied pre-start field "
				+ field.id()
		);
	}

	static boolean validCurrentValue(
		final FieldDefinition field,
		final PersistentFieldValue persisted
	) {
		if (persisted == null || persisted.fieldVersion() != field.version()) {
			return false;
		}
		StoredValue value = persisted.value();
		return switch (field.type()) {
			case BOOLEAN -> value.type() == FieldValueType.BOOLEAN
				&& value.booleanValue().isPresent();
			case INTEGER -> value.type() == FieldValueType.INTEGER
				&& value.integerValue()
					.filter(candidate ->
						field.constraints().minimum().filter(min -> candidate < min).isEmpty()
							&& field.constraints().maximum().filter(max -> candidate > max).isEmpty()
					)
					.isPresent();
			case STRING -> value.type() == FieldValueType.STRING
				&& value.stringValue()
					.filter(candidate -> {
						int length = candidate.codePointCount(0, candidate.length());
						return field.constraints()
								.minimumLength()
								.filter(min -> length < min)
								.isEmpty()
							&& field.constraints()
								.maximumLength()
								.filter(max -> length > max)
								.isEmpty();
					})
					.isPresent();
			case IDENTIFIER -> value.type() == FieldValueType.IDENTIFIER
				&& value.identifierValue().isPresent();
			case SINGLE_CHOICE -> value.type() == FieldValueType.SINGLE_CHOICE
				&& value.stringValue()
					.filter(field.constraints().options()::contains)
					.isPresent();
			case MULTI_CHOICE -> validMultiChoice(field, value);
			case EXCLUSIVE_CHOICE -> value.type() == FieldValueType.SINGLE_CHOICE
				&& value.stringValue()
					.filter(candidate ->
						field.exclusiveChoice()
							.filter(choice -> choice.values().contains(candidate))
							.isPresent()
					)
					.isPresent();
		};
	}

	private static boolean validMultiChoice(
		final FieldDefinition field,
		final StoredValue value
	) {
		if (value.type() != FieldValueType.MULTI_CHOICE) {
			return false;
		}
		List<String> choices = value.choices();
		int minimum = field.constraints().minimumSelections().orElse(0);
		int maximum = field.constraints()
			.maximumSelections()
			.orElse(field.constraints().options().size());
		return choices.size() >= minimum
			&& choices.size() <= maximum
			&& new HashSet<>(choices).size() == choices.size()
			&& choices.stream().allMatch(field.constraints().options()::contains);
	}

	static boolean hasMatchingLock(
		final WorldStateV3 state,
		final FieldDefinition field,
		final UUID scopeId,
		final PlayerRecord player,
		final PersistentFieldValue stored
	) {
		String value = stored.value().stringValue().orElseThrow();
		return state.exclusiveReservations()
			.stream()
			.filter(reservation -> reservation.status() == ReservationStatus.LOCKED)
			.filter(reservation -> reservation.gameId().equals(field.game()))
			.filter(reservation -> reservation.fieldId().equals(field.id()))
			.filter(reservation -> reservation.scopeId().equals(scopeId))
			.filter(reservation -> reservation.ownerId().equals(player.playerId()))
			.map(ExclusiveReservation::value)
			.anyMatch(value::equals);
	}

	private static boolean unfinishedFlow(final FlowInstanceStatus status) {
		return status == FlowInstanceStatus.ACTIVE || status == FlowInstanceStatus.BLOCKED;
	}

	private static OperationCode snapshotCode(final String value) {
		return OperationCode.bySerializedName(value).orElse(OperationCode.SNAPSHOT_INVALID);
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	public record ApprovalContext(
		UUID actorId,
		UUID gameInstanceId,
		long expectedStateRevision,
		long expectedDefinitionGeneration,
		UUID timelineInstanceId,
		UUID firstTaskInstanceId,
		long serverTick,
		Set<UUID> onlinePlayerIds
	) {
		public ApprovalContext {
			actorId = Objects.requireNonNull(actorId, "actorId");
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			timelineInstanceId = Objects.requireNonNull(timelineInstanceId, "timelineInstanceId");
			firstTaskInstanceId = Objects.requireNonNull(firstTaskInstanceId, "firstTaskInstanceId");
			onlinePlayerIds = Set.copyOf(onlinePlayerIds);
			if (
				expectedStateRevision < 0L
					|| expectedDefinitionGeneration < 0L
					|| serverTick < 0L
			) {
				throw new IllegalArgumentException("approval revisions and server tick cannot be negative");
			}
		}
	}

	public record ApprovalResult(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		List<UUID> frozenParticipants
	) {
		public ApprovalResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			frozenParticipants = List.copyOf(frozenParticipants);
			if (
				(code == OperationCode.SUCCESS) != nextState.isPresent()
					|| (nextState.isEmpty() && !frozenParticipants.isEmpty())
			) {
				throw new IllegalArgumentException("timeline approval result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static ApprovalResult approved(
			final WorldStateV3 state,
			final List<UUID> participants
		) {
			return new ApprovalResult(
				OperationCode.SUCCESS,
				"timeline approved and first task created",
				Optional.of(state),
				participants
			);
		}

		private static ApprovalResult rejected(
			final OperationCode code,
			final String message
		) {
			return new ApprovalResult(code, message, Optional.empty(), List.of());
		}
	}
}
