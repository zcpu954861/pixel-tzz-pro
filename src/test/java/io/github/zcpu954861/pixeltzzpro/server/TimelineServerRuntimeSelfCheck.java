package io.github.zcpu954861.pixeltzzpro.server;

import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.IntermissionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.LiveVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultSemantic;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultStyle;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCallbacks;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskRoute;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TransitionTiming;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.IntermissionState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.LifecycleCallbackKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.LifecycleCallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionCause;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionTiming;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionTrigger;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Small pure-state regression check for the server Tick coordinator's recovery decisions.
 */
public final class TimelineServerRuntimeSelfCheck {
	private static final Identifier GAME = id("test:game");
	private static final Identifier TASK = id("test:task");
	private static final Identifier NEXT_TASK = id("test:next");
	private static final Identifier BEFORE_PHASE = id("test:before_phase");
	private static final Identifier AFTER_PHASE = id("test:after_phase");
	private static final Identifier END_PHASE = id("test:end_phase");
	private static final Identifier OLD_PHASE = id("test:old_phase");
	private static final Identifier PHASE_EXIT = id("test:phase_exit");
	private static final Identifier PHASE_ENTER = id("test:phase_enter");
	private static final Identifier PAUSE_A = id("test:pause_a");
	private static final Identifier RESUME_A = id("test:resume_a");
	private static final Identifier PAUSE_B = id("test:pause_b");
	private static final Identifier RESUME_B = id("test:resume_b");
	private static final UUID TIMELINE = uuid(1L);
	private static final UUID TASK_INSTANCE = uuid(2L);
	private static final UUID NEXT_TASK_INSTANCE = uuid(3L);
	private static final UUID PLAYER_A = uuid(10L);
	private static final UUID PLAYER_B = uuid(11L);
	private static final UUID LATE_PLAYER = uuid(12L);
	private static final UUID PHASE_TRANSITION = uuid(20L);

	private TimelineServerRuntimeSelfCheck() {
	}

	public static void main(final String[] arguments) {
		checkFrozenIntermissionCallbackRecovery();
		checkPhaseCallbackFailureRecovery();
		checkPhaseTransitionTiming();
		checkFixedGameParticipants();
		checkHostFallbackPolicy();
		System.out.println("TIMELINE_SERVER_RUNTIME_SELF_CHECK=PASS");
	}

	private static void checkHostFallbackPolicy() {
		TaskResult denied = result(
			"first",
			BEFORE_PHASE,
			TransitionTiming.BEFORE_INTERMISSION,
			PAUSE_A,
			RESUME_A
		);
		TaskResult allowed = new TaskResult(
			denied.id(),
			denied.name(),
			denied.style(),
			denied.recap(),
			denied.onApply(),
			denied.onApplyPlayers(),
			true,
			denied.route()
		);
		TimelineInstance waiting = timeline(
			TimelineStatus.SETTLING,
			Optional.of(settlingWithoutResult()),
			List.of()
		);
		check(
			TimelineServerRuntime.hostFallbackEligible(waiting, allowed),
			"an opted-in result must be available only in resultless settlement"
		);
		check(
			!TimelineServerRuntime.hostFallbackEligible(waiting, denied),
			"a result without explicit host fallback must stay unavailable"
		);
		check(
			!TimelineServerRuntime.hostFallbackEligible(
				timeline(
					TimelineStatus.SETTLING,
					Optional.of(
						settlingTask(
							frozenNext(
								"first",
								BEFORE_PHASE,
								PhaseTransitionTiming.BEFORE_INTERMISSION
							),
							List.of(PLAYER_A)
						)
					),
					List.of()
				),
				allowed
			),
			"a host cannot replace an already frozen result"
		);
	}

	private static void checkPhaseCallbackFailureRecovery() {
		PhaseTransitionTrigger trigger = new PhaseTransitionTrigger(
			PHASE_TRANSITION,
			OLD_PHASE,
			BEFORE_PHASE,
			PhaseTransitionCause.SETTLEMENT,
			7L
		);
		var exit = new TaskCallbackLedgerAuthority.StepIdentity(
			TimelineServerRuntime.phaseCallbackStepKey(trigger, "on_exit"),
			PHASE_EXIT,
			Optional.empty()
		);
		var enter = new TaskCallbackLedgerAuthority.StepIdentity(
			TimelineServerRuntime.phaseCallbackStepKey(trigger, "on_enter"),
			PHASE_ENTER,
			Optional.empty()
		);

		var exitPrepared = TaskCallbackLedgerAuthority.prepareAutomatic(
			List.of(),
			exit,
			20L
		);
		var exitSucceeded = TaskCallbackLedgerAuthority.recordOutcome(
			exitPrepared.nextLedger().orElseThrow(),
			exitPrepared.invocation().orElseThrow(),
			true,
			Optional.empty(),
			20L
		);
		var enterPrepared = TaskCallbackLedgerAuthority.prepareAutomatic(
			exitSucceeded.nextLedger().orElseThrow(),
			enter,
			20L
		);
		var enterFailed = TaskCallbackLedgerAuthority.recordOutcome(
			enterPrepared.nextLedger().orElseThrow(),
			enterPrepared.invocation().orElseThrow(),
			false,
			Optional.of("expected self-check failure"),
			20L
		);

		TimelineInstance blocked = blockedPhaseTimeline(
			trigger,
			enterFailed.nextLedger().orElseThrow()
		);
		TimelineInstance recovered = TimelineInstance.CODEC.parse(
			JsonOps.INSTANCE,
			TimelineInstance.CODEC.encodeStart(JsonOps.INSTANCE, blocked).getOrThrow()
		).getOrThrow();
		check(recovered.equals(blocked), "phase trigger and callback ledger must survive restart");

		var skippedExit = TaskCallbackLedgerAuthority.prepareAutomatic(
			recovered.callbackLedger(),
			exit,
			recovered.gameElapsedTicks()
		);
		check(
			skippedExit.code()
				== io.github.zcpu954861.pixeltzzpro.network.OperationCode.SUCCESS
				&& !skippedExit.prepared(),
			"successful on_exit must not replay after restart"
		);
		var automaticEnter = TaskCallbackLedgerAuthority.prepareAutomatic(
			recovered.callbackLedger(),
			enter,
			recovered.gameElapsedTicks()
		);
		check(
			automaticEnter.code()
				== io.github.zcpu954861.pixeltzzpro.network.OperationCode.CALLBACK_FAILED,
			"failed on_enter must require explicit recovery"
		);
		var retriedEnter = TaskCallbackLedgerAuthority.prepareRetry(
			recovered.callbackLedger(),
			enter,
			recovered.gameElapsedTicks()
		);
		var enterSucceeded = TaskCallbackLedgerAuthority.recordOutcome(
			retriedEnter.nextLedger().orElseThrow(),
			retriedEnter.invocation().orElseThrow(),
			true,
			Optional.empty(),
			recovered.gameElapsedTicks()
		);
		check(
			TaskCallbackLedgerAuthority.allSucceeded(
				enterSucceeded.nextLedger().orElseThrow(),
				List.of(exit, enter)
			),
			"retrying only on_enter must complete the persisted phase callback pair"
		);
	}

	private static void checkFrozenIntermissionCallbackRecovery() {
		TaskDefinition definition = definition();
		TaskInstance task = settledTask(
			frozenNext("second", AFTER_PHASE, PhaseTransitionTiming.AFTER_INTERMISSION),
			List.of(PLAYER_A, PLAYER_B),
			Optional.of(
				new LifecycleCallbackTrigger(
					LifecycleCallbackKind.INTERMISSION_PAUSE,
					7L
				)
			)
		);
		TaskInstance decoded = TaskInstance.CODEC.parse(
			JsonOps.INSTANCE,
			TaskInstance.CODEC.encodeStart(JsonOps.INSTANCE, task).getOrThrow()
		).getOrThrow();
		check(decoded.equals(task), "lifecycle callback trigger must survive codec recovery");
		check(
			TimelineServerRuntime.lifecycleFunction(
				definition,
				decoded,
				decoded.lifecycleCallbackTrigger().orElseThrow().kind()
			).equals(Optional.of(PAUSE_B)),
			"recovered pause callback must come from the frozen second result"
		);
		check(
			TimelineServerRuntime.lifecycleFunction(
				definition,
				decoded,
				LifecycleCallbackKind.INTERMISSION_RESUME
			).equals(Optional.of(RESUME_B)),
			"resume callback must come from the frozen second result"
		);
	}

	private static void checkPhaseTransitionTiming() {
		TaskInstance beforeTask = settlingTask(
			frozenNext("first", BEFORE_PHASE, PhaseTransitionTiming.BEFORE_INTERMISSION),
			List.of(PLAYER_A)
		);
		check(
			TimelineServerRuntime.phaseAfterTransition(
				timeline(TimelineStatus.SETTLING, Optional.of(beforeTask), List.of()),
				timeline(
					TimelineStatus.INTERMISSION,
					Optional.of(
						settledTask(
							beforeTask.result().orElseThrow(),
							beforeTask.participants(),
							Optional.empty()
						)
					),
					List.of()
				)
			).equals(Optional.of(BEFORE_PHASE)),
			"before-intermission phase must apply when settlement opens the interval"
		);

		TaskInstance afterTask = settledTask(
			frozenNext("second", AFTER_PHASE, PhaseTransitionTiming.AFTER_INTERMISSION),
			List.of(PLAYER_A),
			Optional.empty()
		);
		check(
			TimelineServerRuntime.phaseAfterTransition(
				timeline(TimelineStatus.INTERMISSION, Optional.of(afterTask), List.of()),
				timeline(
					TimelineStatus.STARTING,
					Optional.of(startingTask(List.of(PLAYER_A))),
					List.of(afterTask)
				)
			).equals(Optional.of(AFTER_PHASE)),
			"after-intermission phase must wait until the interval routes onward"
		);

		TaskInstance finalTask = settlingTask(
			new FrozenResult(
				"finished",
				Optional.empty(),
				Optional.of(END_PHASE),
				Optional.empty(),
				Optional.empty(),
				20L
			),
			List.of(PLAYER_A)
		);
		check(
			TimelineServerRuntime.phaseAfterTransition(
				timeline(TimelineStatus.SETTLING, Optional.of(finalTask), List.of()),
				timeline(TimelineStatus.COMPLETED, Optional.empty(), List.of())
			).equals(Optional.of(END_PHASE)),
			"terminal route must publish its frozen end phase"
		);
	}

	private static void checkFixedGameParticipants() {
		TaskInstance first = settledTask(
			frozenNext("first", BEFORE_PHASE, PhaseTransitionTiming.BEFORE_INTERMISSION),
			List.of(PLAYER_B, PLAYER_A),
			Optional.empty()
		);
		TaskInstance current = startingTask(List.of(PLAYER_A, LATE_PLAYER));
		TimelineInstance routed = timeline(
			TimelineStatus.STARTING,
			Optional.of(current),
			List.of(first)
		);
		check(
			TimelineServerRuntime.fixedTimelineParticipants(routed)
				.equals(List.of(PLAYER_A, PLAYER_B)),
			"later task routing must use the first frozen game roster"
		);
		check(
			!TimelineServerRuntime.fixedTimelineParticipants(routed).contains(LATE_PLAYER),
			"a player registered after approval must not enter a later task automatically"
		);
	}

	private static TaskDefinition definition() {
		return new TaskDefinition(
			TASK,
			GAME,
			1,
			TaskKind.MAIN,
			text("任务"),
			Optional.empty(),
			Optional.empty(),
			io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.defaultTaskAudience(),
			TaskCompletionPolicy.EVENT_ONLY,
			OptionalLong.empty(),
			true,
			LiveVisibility.hidden(),
			RecapVisibility.PARTICIPANTS,
			TaskCallbacks.empty(),
			Optional.empty(),
			Map.of(
				"first",
				result(
					"first",
					BEFORE_PHASE,
					TransitionTiming.BEFORE_INTERMISSION,
					PAUSE_A,
					RESUME_A
				),
				"second",
				result(
					"second",
					AFTER_PHASE,
					TransitionTiming.AFTER_INTERMISSION,
					PAUSE_B,
					RESUME_B
				)
			),
			List.of(),
			List.of()
		);
	}

	private static TaskResult result(
		final String resultId,
		final Identifier phase,
		final TransitionTiming timing,
		final Identifier pause,
		final Identifier resume
	) {
		IntermissionDefinition intermission = new IntermissionDefinition(
			20L,
			false,
			text(resultId),
			Optional.empty(),
			Optional.of(pause),
			Optional.of(resume),
			Optional.empty()
		);
		return new TaskResult(
			resultId,
			text(resultId),
			new ResultStyle(ResultSemantic.NEUTRAL, Optional.empty()),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			false,
			new TaskRoute(
				Optional.of(NEXT_TASK),
				Optional.empty(),
				Optional.of(phase),
				Optional.of(timing),
				Optional.of(intermission)
			)
		);
	}

	private static FrozenResult frozenNext(
		final String resultId,
		final Identifier phase,
		final PhaseTransitionTiming timing
	) {
		return new FrozenResult(
			resultId,
			Optional.of(NEXT_TASK),
			Optional.empty(),
			Optional.of(phase),
			Optional.of(timing),
			20L
		);
	}

	private static TaskInstance startingTask(final List<UUID> participants) {
		return new TaskInstance(
			NEXT_TASK_INSTANCE,
			NEXT_TASK,
			io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.MAIN,
			TaskStatus.STARTING,
			Optional.empty(),
			0L,
			Optional.empty(),
			Optional.empty(),
			participants,
			20L,
			Optional.empty(),
			List.of()
		);
	}

	private static TaskInstance settlingTask(
		final FrozenResult result,
		final List<UUID> participants
	) {
		return new TaskInstance(
			TASK_INSTANCE,
			TASK,
			io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.MAIN,
			TaskStatus.SETTLING,
			Optional.empty(),
			20L,
			Optional.of(result),
			Optional.empty(),
			participants,
			0L,
			Optional.empty(),
			List.of()
		);
	}

	private static TaskInstance settlingWithoutResult() {
		return new TaskInstance(
			TASK_INSTANCE,
			TASK,
			io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.MAIN,
			TaskStatus.SETTLING,
			Optional.empty(),
			20L,
			Optional.empty(),
			Optional.empty(),
			List.of(PLAYER_A),
			0L,
			Optional.empty(),
			List.of()
		);
	}

	private static TaskInstance settledTask(
		final FrozenResult result,
		final List<UUID> participants,
		final Optional<LifecycleCallbackTrigger> trigger
	) {
		return new TaskInstance(
			TASK_INSTANCE,
			TASK,
			io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.MAIN,
			TaskStatus.SETTLED,
			Optional.empty(),
			20L,
			Optional.of(result),
			Optional.of(new IntermissionState(20L, 0L, false, false)),
			participants,
			0L,
			Optional.of(20L),
			List.of(),
			trigger,
			List.of(),
			List.of()
		);
	}

	private static TimelineInstance timeline(
		final TimelineStatus status,
		final Optional<TaskInstance> currentTask,
		final List<TaskInstance> history
	) {
		return new TimelineInstance(
			TIMELINE,
			GAME,
			1,
			FrozenDocument.of("{\"timeline\":\"runtime-self-check\"}"),
			status,
			Optional.empty(),
			currentTask,
			history,
			20L,
			false,
			Optional.empty(),
			0L,
			Optional.of(0L),
			status == TimelineStatus.COMPLETED ? Optional.of(20L) : Optional.empty(),
			7L,
			List.of()
		);
	}

	private static TimelineInstance blockedPhaseTimeline(
		final PhaseTransitionTrigger trigger,
		final List<io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep> ledger
	) {
		FrozenResult result = frozenNext(
			"first",
			BEFORE_PHASE,
			PhaseTransitionTiming.BEFORE_INTERMISSION
		);
		TaskInstance task = new TaskInstance(
			TASK_INSTANCE,
			TASK,
			io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.MAIN,
			TaskStatus.BLOCKED,
			Optional.of(TaskStatus.SETTLING),
			20L,
			Optional.of(result),
			Optional.empty(),
			List.of(PLAYER_A),
			0L,
			Optional.empty(),
			List.of(),
			Optional.empty(),
			Optional.of(trigger),
			List.of(),
			List.of()
		);
		return new TimelineInstance(
			TIMELINE,
			GAME,
			1,
			FrozenDocument.of("{\"timeline\":\"blocked-phase-self-check\"}"),
			TimelineStatus.BLOCKED,
			Optional.of(TimelineStatus.SETTLING),
			Optional.of(task),
			List.of(),
			20L,
			false,
			Optional.empty(),
			0L,
			Optional.of(0L),
			Optional.empty(),
			9L,
			ledger
		);
	}

	private static RichText text(final String value) {
		return new RichText("{\"text\":\"" + value + "\"}", value);
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
}
