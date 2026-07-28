package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackAuthority.OutcomeResult;
import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackAuthority.PrepareResult;
import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackAuthority.PreparedInvocation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackFailure;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackReferences;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ExecutionSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowIdentity;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostOperation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RemovalRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable state-machine check for callback markers, failures, and explicit retry isolation.
 */
public final class FlowCallbackAuthoritySelfCheck {
	private static final UUID HOST = uuid(1);
	private static final UUID OTHER = uuid(2);
	private static final UUID PLAYER = uuid(3);
	private static final UUID INSTANCE = uuid(4);
	private static final UUID PAGE = uuid(5);
	private static final Identifier GAME = id("test:game");
	private static final Identifier PHASE = id("test:setup");
	private static final Identifier FLOW = id("test:flow");
	private static final Identifier ACTION = id("test:start");
	private static final Identifier ON_START = id("test:on_start");
	private static final Identifier ON_PLAYER = id("test:on_player_complete");
	private static final Identifier ON_ALL = id("test:on_all_complete");

	private FlowCallbackAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		// Initialize the outer schema codec before constructing its nested snapshot records.
		WorldStateV2.initial();
		WorldStateV2 active = activeState();
		WorldStateV2 untouched = active;

		PrepareResult start = FlowCallbackAuthority.prepareOnStart(active, 100L);
		check(start.prepared(), "on_start must prepare");
		WorldStateV2 startMarked = start.nextState().orElseThrow();
		PreparedInvocation startInvocation = start.invocation().orElseThrow();
		check(active.equals(untouched), "automatic preparation mutated its input");
		check(
			startMarked.activeForcedFlow().orElseThrow().runtime().callbacks().onStartInvoked(),
			"on_start marker was not committed first"
		);
		check(
			startInvocation.context().stateRevision() == startMarked.stateRevision(),
			"callback context must use the marker-committed revision"
		);
		check(
			startInvocation.context().playerId().isEmpty(),
			"on_start must use instance context"
		);
		PrepareResult repeatedStart = FlowCallbackAuthority.prepareOnStart(startMarked, 101L);
		check(repeatedStart.successful() && !repeatedStart.prepared(), "on_start replay must be a no-op");
		OutcomeResult startSuccess = FlowCallbackAuthority.recordOutcome(
			startMarked,
			startInvocation,
			success(),
			102L
		);
		check(
			startSuccess.invocationSucceeded() && startSuccess.nextState().isEmpty(),
			"first-attempt success must need no second state write"
		);

		WorldStateV2 completed = naturallyCompleted(startMarked, FlowMemberStatus.COMPLETED);
		CoreProjection committedCore = core(completed);
		PrepareResult player = FlowCallbackAuthority.preparePlayerComplete(completed, PLAYER, 200L);
		check(player.prepared(), "natural member completion must prepare its callback");
		WorldStateV2 playerMarked = player.nextState().orElseThrow();
		PreparedInvocation playerInvocation = player.invocation().orElseThrow();
		check(
			playerMarked.activeForcedFlow().orElseThrow().runtime().callbacks()
				.onPlayerCompleteInvoked()
				.contains(PLAYER),
			"player callback marker was not committed first"
		);
		check(
			playerInvocation.context().playerId().equals(Optional.of(PLAYER))
				&& playerInvocation.context().playerName().equals(Optional.of("Runner")),
			"player callback context is wrong"
		);
		check(
			core(playerMarked).equals(committedCore),
			"callback marker changed completion or identity core state"
		);

		String oversizedError = "x".repeat(5_000);
		OutcomeResult firstFailure = FlowCallbackAuthority.recordOutcome(
			playerMarked,
			playerInvocation,
			failure(oversizedError),
			210L
		);
		check(firstFailure.code() == OperationCode.CALLBACK_FAILED, "failure code");
		WorldStateV2 blocked = firstFailure.nextState().orElseThrow();
		CallbackFailure recorded = blocked.activeForcedFlow().orElseThrow().runtime()
			.callbacks()
			.failures()
			.getFirst();
		check(
			blocked.activeForcedFlow().orElseThrow().runtime().status() == FlowInstanceStatus.BLOCKED,
			"callback failure must block unsafe progress"
		);
		check(recorded.attemptCount() == 1, "first failure attempt");
		check(recorded.error().length() == 4_096, "failure diagnostic must be bounded");
		check(
			blocked.audit().recentEvents().getLast().type() == AuditEventType.CALLBACK_FAILED,
			"callback failure audit"
		);
		check(core(blocked).equals(committedCore), "failure rolled core completion state back");
		PrepareResult repeatedPlayer = FlowCallbackAuthority.preparePlayerComplete(blocked, PLAYER, 211L);
		check(
			repeatedPlayer.successful() && !repeatedPlayer.prepared(),
			"failed automatic callback must not invoke itself again"
		);
		check(
			FlowCallbackAuthority.prepareAllComplete(blocked, 212L).code()
				== OperationCode.CALLBACK_FAILED,
			"on_all_complete must wait for the earlier callback failure"
		);

		PrepareResult wrongActor = FlowCallbackAuthority.prepareRetry(
			blocked,
			OTHER,
			CallbackKind.ON_PLAYER_COMPLETE,
			Optional.of(PLAYER),
			220L
		);
		check(wrongActor.code() == OperationCode.NOT_HOST, "only the current host may retry");
		PrepareResult retryTwo = FlowCallbackAuthority.prepareRetry(
			blocked,
			HOST,
			CallbackKind.ON_PLAYER_COMPLETE,
			Optional.of(PLAYER),
			221L
		);
		check(retryTwo.prepared(), "explicit retry must prepare");
		WorldStateV2 retryTwoMarked = retryTwo.nextState().orElseThrow();
		PreparedInvocation retryTwoInvocation = retryTwo.invocation().orElseThrow();
		check(retryTwoInvocation.retry() && retryTwoInvocation.attemptCount() == 2, "retry attempt two");
		check(
			retryTwoMarked.audit().recentEvents().getLast().type() == AuditEventType.CALLBACK_RETRIED,
			"retry audit"
		);
		check(core(retryTwoMarked).equals(committedCore), "retry preparation changed core state");
		OutcomeResult retryTwoFailure = FlowCallbackAuthority.recordOutcome(
			retryTwoMarked,
			retryTwoInvocation,
			failure("still broken"),
			222L
		);
		WorldStateV2 failedTwice = retryTwoFailure.nextState().orElseThrow();
		check(
			failedTwice.activeForcedFlow().orElseThrow().runtime().callbacks()
				.failures()
				.getFirst()
				.attemptCount() == 2,
			"failed retry must retain incremented attempt"
		);
		check(core(failedTwice).equals(committedCore), "failed retry changed core state");

		PrepareResult retryThree = FlowCallbackAuthority.prepareRetry(
			failedTwice,
			HOST,
			CallbackKind.ON_PLAYER_COMPLETE,
			Optional.of(PLAYER),
			230L
		);
		WorldStateV2 retryThreeMarked = retryThree.nextState().orElseThrow();
		PreparedInvocation retryThreeInvocation = retryThree.invocation().orElseThrow();
		check(retryThreeInvocation.attemptCount() == 3, "retry attempt three");
		OutcomeResult recoveredResult = FlowCallbackAuthority.recordOutcome(
			retryThreeMarked,
			retryThreeInvocation,
			success(),
			231L
		);
		check(recoveredResult.invocationSucceeded(), "successful retry result");
		WorldStateV2 recovered = recoveredResult.nextState().orElseThrow();
		check(
			recovered.activeForcedFlow().orElseThrow().runtime().callbacks().failures().isEmpty(),
			"successful retry must clear its matching failure"
		);
		check(
			recovered.activeForcedFlow().orElseThrow().runtime().status()
				== FlowInstanceStatus.COMPLETED,
			"successful retry must restore the completed core status"
		);
		check(
			recovered.activeForcedFlow().orElseThrow().runtime().blockDiagnostic().isEmpty(),
			"successful retry must clear its diagnostic"
		);
		check(
			archived(recovered.activeForcedFlow().orElseThrow().runtime().executionSnapshot()),
			"successful terminal retry must compact its snapshot"
		);
		check(core(recovered).equals(committedCore), "successful retry changed core state");

		PrepareResult all = FlowCallbackAuthority.prepareAllComplete(recovered, 240L);
		check(all.prepared(), "on_all_complete must prepare after recovery");
		WorldStateV2 allMarked = all.nextState().orElseThrow();
		check(
			allMarked.activeForcedFlow().orElseThrow().runtime().callbacks().onAllCompleteInvoked(),
			"on_all_complete marker"
		);
		check(
			FlowCallbackAuthority.recordOutcome(
				allMarked,
				all.invocation().orElseThrow(),
				success(),
				241L
			).nextState().isEmpty(),
			"successful on_all_complete must not rewrite core state"
		);
		check(
			!FlowCallbackAuthority.prepareAllComplete(allMarked, 242L).prepared(),
			"on_all_complete replay must be a no-op"
		);

		WorldStateV2 skipped = naturallyCompleted(activeState(), FlowMemberStatus.ALREADY_COMPLETE);
		PrepareResult skippedPlayer = FlowCallbackAuthority.preparePlayerComplete(skipped, PLAYER, 300L);
		check(
			skippedPlayer.code() == OperationCode.ACTION_UNAVAILABLE
				&& !skipped.activeForcedFlow().orElseThrow().runtime().callbacks()
					.onPlayerCompleteInvoked()
					.contains(PLAYER),
			"already_complete must not trigger a player completion callback"
		);
		checkBoundedFailureHistory(startMarked, startInvocation);
		checkCanceledDoesNotComplete(active);
		checkCanceledRetryFinalizesTerminalState();
		System.out.println("FLOW_CALLBACK_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkBoundedFailureHistory(
		final WorldStateV2 preparedState,
		final PreparedInvocation invocation
	) {
		List<CallbackFailure> failures = new ArrayList<>();
		for (int index = 0; index < 128; index++) {
			failures.add(
				new CallbackFailure(
					CallbackKind.ON_PLAYER_COMPLETE,
					ON_PLAYER,
					Optional.of(uuid(10_000L + index)),
					"old failure " + index,
					10L + index,
					1
				)
			);
		}
		ForcedFlowInstance instance = preparedState.activeForcedFlow().orElseThrow();
		CallbackState crowdedCallbacks = new CallbackState(
			true,
			Set.of(),
			false,
			failures
		);
		WorldStateV2 crowded = withRuntime(
			preparedState,
			instance,
			new ForcedFlowRuntime(
				instance.runtime().status(),
				instance.runtime().members(),
				instance.runtime().completedCount(),
				instance.runtime().totalCount(),
				instance.runtime().instanceRevision(),
				instance.runtime().executionSnapshot(),
				crowdedCallbacks,
				instance.runtime().blockDiagnostic(),
				instance.runtime().updatedAtEpochMillis()
			),
			preparedState.stateRevision()
		);
		OutcomeResult result = FlowCallbackAuthority.recordOutcome(
			crowded,
			invocation,
			failure("newest"),
			400L
		);
		check(
			result.nextState().orElseThrow().activeForcedFlow().orElseThrow().runtime()
				.callbacks()
				.failures()
				.size() == 128,
			"callback failure history must remain bounded"
		);
	}

	private static void checkCanceledDoesNotComplete(final WorldStateV2 state) {
		ForcedFlowInstance instance = state.activeForcedFlow().orElseThrow();
		FlowMemberState removed = new FlowMemberState(
			PLAYER,
			"Runner",
			Optional.empty(),
			FlowMemberStatus.REMOVED,
			1L,
			Map.of(),
			Optional.empty(),
			RequestSequenceWindow.empty(),
			Optional.of(500L),
			false,
			Optional.of(500L),
			Optional.empty(),
			Optional.of(new RemovalRecord(HOST, 500L, "canceled"))
		);
		WorldStateV2 canceled = withRuntime(
			state,
			instance,
			new ForcedFlowRuntime(
				FlowInstanceStatus.CANCELED,
				Map.of(PLAYER, removed),
				0,
				0,
				1L,
				instance.runtime().executionSnapshot(),
				CallbackState.initial(),
				Optional.empty(),
				500L
			),
			state.stateRevision() + 1L
		);
		PrepareResult result = FlowCallbackAuthority.prepareAllComplete(canceled, 501L);
		check(
			result.code() == OperationCode.ACTION_UNAVAILABLE && !result.prepared(),
			"all-member removal must not invoke on_all_complete"
		);
	}

	private static void checkCanceledRetryFinalizesTerminalState() {
		WorldStateV2 active = activeState().withPlayers(
			Map.of(
				PLAYER,
				new PlayerRecord(
					PLAYER,
					"Runner",
					id("test:runner"),
					Optional.empty(),
					id("test:alive"),
					Map.of(),
					Optional.empty(),
					Optional.of(
						new PlayerFlowParticipation(
							INSTANCE,
							FlowMemberStatus.IN_PROGRESS
						)
					),
					CompletionSummary.empty(),
					1L,
					1L
				)
			)
		);
		PrepareResult start = FlowCallbackAuthority.prepareOnStart(active, 600L);
		OutcomeResult failed = FlowCallbackAuthority.recordOutcome(
			start.nextState().orElseThrow(),
			start.invocation().orElseThrow(),
			failure("expected"),
			601L
		);
		WorldStateV2 blocked = failed.nextState().orElseThrow();
		ForcedFlowInstance instance = blocked.activeForcedFlow().orElseThrow();
		FlowMemberState current = instance.runtime().members().get(PLAYER);
		FlowMemberState removed = new FlowMemberState(
			current.playerId(),
			current.lockedName(),
			Optional.empty(),
			FlowMemberStatus.REMOVED,
			current.memberRevision() + 1L,
			current.flowFields(),
			Optional.empty(),
			current.requestSequenceWindow(),
			current.lastCommittedAtEpochMillis(),
			current.offline(),
			current.lastOnlineAtEpochMillis(),
			Optional.empty(),
			Optional.of(new RemovalRecord(HOST, 602L, "removed while callback-blocked"))
		);
		WorldStateV2 canceledCore = withRuntime(
			blocked,
			instance,
			new ForcedFlowRuntime(
				FlowInstanceStatus.BLOCKED,
				Map.of(PLAYER, removed),
				0,
				0,
				instance.runtime().instanceRevision() + 1L,
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				instance.runtime().blockDiagnostic(),
				602L
			),
			blocked.stateRevision() + 1L
		);
		PrepareResult retry = FlowCallbackAuthority.prepareRetry(
			canceledCore,
			HOST,
			CallbackKind.ON_START,
			Optional.empty(),
			603L
		);
		check(retry.prepared(), "canceled-core callback retry must prepare");
		OutcomeResult recovered = FlowCallbackAuthority.recordOutcome(
			retry.nextState().orElseThrow(),
			retry.invocation().orElseThrow(),
			success(),
			604L
		);
		WorldStateV2 terminal = recovered.nextState().orElseThrow();
		check(
			terminal.activeForcedFlow().orElseThrow().runtime().status()
				== FlowInstanceStatus.CANCELED,
			"successful retry must restore a zero-member core as canceled"
		);
		check(
			archived(terminal.activeForcedFlow().orElseThrow().runtime().executionSnapshot()),
			"successful canceled retry must compact its snapshot"
		);
		check(
			terminal.players().get(PLAYER).activeFlowParticipation().isEmpty(),
			"successful terminal retry must clear bound participation"
		);
	}

	private static WorldStateV2 activeState() {
		ExecutionSnapshot snapshot = new ExecutionSnapshot(
			7L,
			FrozenDocument.of("{}"),
			Map.of(),
			Map.of(),
			Map.of(),
			List.of(),
			Set.of(),
			Map.of(),
			Set.of(ON_START, ON_PLAYER, ON_ALL),
			new CallbackReferences(
				Optional.of(ON_START),
				Optional.of(ON_PLAYER),
				Optional.of(ON_ALL)
			),
			Set.of(),
			Set.of(),
			"Starts the self-check flow",
			"0".repeat(64)
		).withComputedContentSha256();
		FlowMemberState member = new FlowMemberState(
			PLAYER,
			"Runner",
			Optional.of("page"),
			FlowMemberStatus.IN_PROGRESS,
			0L,
			Map.of(),
			Optional.of(PAGE),
			RequestSequenceWindow.empty(),
			Optional.empty(),
			false,
			Optional.of(1L),
			Optional.empty(),
			Optional.empty()
		);
		ForcedFlowIdentity identity = new ForcedFlowIdentity(
			INSTANCE,
			GAME,
			FLOW,
			1,
			ACTION,
			HOST,
			1L,
			PHASE,
			7L,
			CompletionPolicy.ALWAYS
		);
		ForcedFlowRuntime runtime = new ForcedFlowRuntime(
			FlowInstanceStatus.ACTIVE,
			Map.of(PLAYER, member),
			0,
			1,
			0L,
			snapshot,
			CallbackState.initial(),
			Optional.empty(),
			1L
		);
		return WorldStateV2.initial()
			.withActivity(GAME, PHASE)
			.withHost(
				Optional.of(
					new HostRecord(
						HOST,
						Optional.of("Host"),
						Optional.of(1L),
						Optional.of(HOST),
						Optional.of(HostOperation.CLAIM)
					)
				)
			)
			.withActiveForcedFlow(Optional.of(new ForcedFlowInstance(identity, runtime)))
			.withStateRevision(10L);
	}

	private static WorldStateV2 naturallyCompleted(
		final WorldStateV2 state,
		final FlowMemberStatus status
	) {
		ForcedFlowInstance instance = state.activeForcedFlow().orElseThrow();
		FlowMemberState current = instance.runtime().members().get(PLAYER);
		FlowMemberState member = new FlowMemberState(
			current.playerId(),
			current.lockedName(),
			Optional.of("done"),
			status,
			current.memberRevision() + 1L,
			current.flowFields(),
			Optional.empty(),
			current.requestSequenceWindow(),
			Optional.of(150L),
			false,
			Optional.of(150L),
			Optional.of(150L),
			Optional.empty()
		);
		WorldStateV2 next = withRuntime(
			state,
			instance,
			new ForcedFlowRuntime(
				FlowInstanceStatus.COMPLETED,
				Map.of(PLAYER, member),
				1,
				1,
				instance.runtime().instanceRevision() + 1L,
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				Optional.empty(),
				150L
			),
			state.stateRevision() + 1L
		);
		if (status != FlowMemberStatus.COMPLETED) {
			return next;
		}
		return next.withCompletionHistory(
			List.of(
				new CompletionRecord(
					PLAYER,
					FLOW,
					1,
					INSTANCE,
					150L,
					ACTION,
					false,
					7L
				)
			)
		);
	}

	private static WorldStateV2 withRuntime(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final ForcedFlowRuntime runtime,
		final long stateRevision
	) {
		WorldStateV2 next = state
			.withActiveForcedFlow(
				Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
			)
			.withStateRevision(stateRevision);
		check(next.validated().result().isPresent(), "self-check fixture must be valid");
		return next;
	}

	private static CoreProjection core(final WorldStateV2 state) {
		ForcedFlowRuntime runtime = state.activeForcedFlow().orElseThrow().runtime();
		return new CoreProjection(
			runtime.members(),
			runtime.completedCount(),
			runtime.totalCount(),
			state.players(),
			state.completionHistory()
		);
	}

	private static boolean archived(final ExecutionSnapshot snapshot) {
		return snapshot.flow().normalizedJson().equals("{\"archived\":true}");
	}

	private static FlowCallbackRunner.Invocation success() {
		return new FlowCallbackRunner.Invocation(true, false, 0, "");
	}

	private static FlowCallbackRunner.Invocation failure(final String error) {
		return new FlowCallbackRunner.Invocation(false, false, 0, error);
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static UUID uuid(final long value) {
		return new UUID(0L, value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record CoreProjection(
		Map<UUID, FlowMemberState> members,
		int completed,
		int total,
		Map<UUID, WorldStateV2.PlayerRecord> players,
		List<CompletionRecord> completionHistory
	) {
	}
}
