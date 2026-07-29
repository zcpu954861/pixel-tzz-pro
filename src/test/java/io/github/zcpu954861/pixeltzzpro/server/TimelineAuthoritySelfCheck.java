package io.github.zcpu954861.pixeltzzpro.server;

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
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.ActionContext;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.OperationResult;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.TaskStart;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable check for timeline state, clocks, frozen results, routing, and interruption.
 */
public final class TimelineAuthoritySelfCheck {
	private static final Identifier GAME = id("test:game");
	private static final Identifier PHASE = id("test:setup");
	private static final Identifier END_PHASE = id("test:ended");
	private static final Identifier RUNNING_PHASE = id("test:running");
	private static final Identifier WARMUP_ID = id("test:warmup");
	private static final Identifier MAIN_ID = id("test:main");
	private static final UUID TIMELINE_ID = uuid(1);
	private static final UUID WARMUP_INSTANCE = uuid(2);
	private static final UUID MAIN_INSTANCE = uuid(3);
	private static final UUID PLAYER_A = uuid(10);
	private static final UUID PLAYER_B = uuid(11);

	private TimelineAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		WorldStateV2.initial();
		TaskDefinition warmup = warmupDefinition();
		TaskDefinition main = mainDefinition();
		TimelineInstance created = changed(
			TimelineAuthority.create(
				Optional.empty(),
				TIMELINE_ID,
				1,
				FrozenDocument.of("{\"timeline\":\"self-check\"}"),
				new TaskStart(
					WARMUP_INSTANCE,
					warmup,
					List.of(PLAYER_B, PLAYER_A)
				),
				100L
			),
			"create"
		);
		check(created.status() == TimelineStatus.STARTING, "first task status");
		check(created.timelineRevision() == 0L, "initial revision");
		check(created.currentTask().orElseThrow().participants().equals(List.of(PLAYER_A, PLAYER_B)), "participants sorted");
		checkValid(created, "created timeline");
		check(
			TimelineAuthority.create(
				Optional.of(created),
				uuid(99),
				1,
				created.snapshot(),
				new TaskStart(uuid(98), warmup, List.of()),
				101L
			).code() == OperationCode.TIMELINE_ALREADY_ACTIVE,
			"existing timeline must not be overwritten"
		);
		check(
			TimelineAuthority.startSucceeded(
				created,
				new ActionContext(uuid(77), WARMUP_INSTANCE, WARMUP_ID, 0L),
				101L
			).code() == OperationCode.TIMELINE_NOT_ACTIVE,
			"timeline instance validation"
		);
		check(
			TimelineAuthority.startSucceeded(
				created,
				context(created, 1L),
				101L
			).code() == OperationCode.REVISION_MISMATCH,
			"timeline revision validation"
		);

		TimelineInstance runningWarmup = changed(
			TimelineAuthority.startSucceeded(created, context(created), 101L),
			"start warmup"
		);
		check(runningWarmup.status() == TimelineStatus.RUNNING, "warmup running");
		check(runningWarmup.timelineRevision() == 1L, "start advances revision");
		TimelineInstance warmupTickOne = changed(
			TimelineAuthority.tickRunning(runningWarmup, context(runningWarmup), warmup),
			"warmup tick one"
		);
		check(warmupTickOne.currentTask().orElseThrow().elapsedTicks() == 1L, "warmup task clock");
		check(warmupTickOne.gameElapsedTicks() == 0L, "warmup excluded from game clock");
		check(warmupTickOne.timelineRevision() == 1L, "ordinary task tick preserves revision");
		TimelineInstance earlyResult = changed(
			TimelineAuthority.submitResult(
				warmupTickOne,
				context(warmupTickOne),
				warmup,
				"continue"
			),
			"early result"
		);
		check(earlyResult.status() == TimelineStatus.SETTLING, "early result enters settling");
		check(
			warmupTickOne.status() == TimelineStatus.RUNNING,
			"early result transaction must not mutate its input"
		);

		TimelineInstance paused = changed(
			TimelineAuthority.pause(warmupTickOne, context(warmupTickOne), "host pause"),
			"pause"
		);
		check(paused.paused() && paused.timelineRevision() == 2L, "pause state and revision");
		OperationResult pausedTick = TimelineAuthority.tickRunning(paused, context(paused), warmup);
		check(pausedTick.successful() && !pausedTick.changed(), "paused task tick is a no-op");
		TimelineInstance resumed = changed(
			TimelineAuthority.resume(paused, context(paused)),
			"resume"
		);
		check(!resumed.paused() && resumed.timelineRevision() == 3L, "resume state and revision");

		TimelineInstance warmupTimedOut = changed(
			TimelineAuthority.tickRunning(resumed, context(resumed), warmup),
			"warmup timeout"
		);
		check(warmupTimedOut.status() == TimelineStatus.SETTLING, "timeout enters settling");
		check(warmupTimedOut.currentTask().orElseThrow().elapsedTicks() == 2L, "timeout tick counted");
		check(warmupTimedOut.gameElapsedTicks() == 0L, "warmup still excluded");
		check(warmupTimedOut.timelineRevision() == 4L, "timeout transition advances revision");

		TimelineInstance resultFrozen = changed(
			TimelineAuthority.submitResult(
				warmupTimedOut,
				context(warmupTimedOut),
				warmup,
				"continue"
			),
			"freeze warmup result"
		);
		check(resultFrozen.timelineRevision() == 5L, "result freeze advances revision");
		check(
			resultFrozen.currentTask().orElseThrow().result().orElseThrow().nextTaskId()
				.equals(Optional.of(MAIN_ID)),
			"next route frozen"
		);
		check(
			resultFrozen.currentTask().orElseThrow().result().orElseThrow().phaseId()
				.equals(Optional.of(RUNNING_PHASE)),
			"result-associated phase was not frozen"
		);
		OperationResult repeated = TimelineAuthority.submitResult(
			resultFrozen,
			context(resultFrozen),
			warmup,
			"continue"
		);
		check(repeated.successful() && !repeated.changed(), "same result is idempotent");
		check(
			TimelineAuthority.submitResult(
				resultFrozen,
				context(resultFrozen),
				warmup,
				"alternate"
			).code() == OperationCode.RESULT_ALREADY_FROZEN,
			"different result cannot replace frozen result"
		);

		TimelineInstance intermission = changed(
			TimelineAuthority.settlementSucceeded(
				resultFrozen,
				context(resultFrozen),
				warmup,
				Optional.empty(),
				110L
			),
			"start intermission"
		);
		check(intermission.status() == TimelineStatus.INTERMISSION, "intermission status");
		check(intermission.timelineRevision() == 6L, "settlement advances revision");
		check(
			intermission.currentTask().orElseThrow().status() == TaskStatus.SETTLED,
			"task settled before intermission"
		);
		checkValid(intermission, "intermission timeline");
		TimelineInstance intervalTickOne = changed(
			TimelineAuthority.tickIntermission(intermission, context(intermission)),
			"intermission tick one"
		);
		check(intervalTickOne.gameElapsedTicks() == 1L, "counted intermission advances game clock");
		check(intervalTickOne.timelineRevision() == 6L, "ordinary intermission tick preserves revision");
		TimelineInstance intervalElapsed = changed(
			TimelineAuthority.tickIntermission(intervalTickOne, context(intervalTickOne)),
			"intermission tick two"
		);
		check(
			intervalElapsed.currentTask().orElseThrow().intermission().orElseThrow().elapsedTicks() == 2L,
			"intermission elapsed"
		);
		check(intervalElapsed.gameElapsedTicks() == 2L, "second interval game tick");
		check(
			!TimelineAuthority.tickIntermission(intervalElapsed, context(intervalElapsed)).changed(),
			"elapsed intermission waits for completion transaction"
		);

		TimelineInstance mainStarting = changed(
			TimelineAuthority.intermissionSucceeded(
				intervalElapsed,
				context(intervalElapsed),
				Optional.of(new TaskStart(MAIN_INSTANCE, main, List.of(PLAYER_A, PLAYER_B))),
				112L
			),
			"advance to main"
		);
		check(mainStarting.status() == TimelineStatus.STARTING, "next task starts");
		check(mainStarting.timelineRevision() == 7L, "intermission completion advances revision");
		check(mainStarting.taskHistory().size() == 1, "settled warmup archived");
		check(mainStarting.currentTask().orElseThrow().elapsedTicks() == 0L, "new task clock reset");
		check(mainStarting.currentTask().orElseThrow().startedAtGameTick() == 2L, "new task game start tick");
		checkValid(mainStarting, "next task timeline");

		TimelineInstance mainRunning = changed(
			TimelineAuthority.startSucceeded(mainStarting, context(mainStarting), 113L),
			"start main"
		);
		check(
			TimelineAuthority.submitResult(
				mainRunning,
				context(mainRunning),
				main,
				"finished"
			).code() == OperationCode.COMPLETION_POLICY_MISMATCH,
			"timeout-only result cannot be submitted early"
		);
		TimelineInstance mainTickOne = changed(
			TimelineAuthority.tickRunning(mainRunning, context(mainRunning), main),
			"main tick one"
		);
		check(mainTickOne.gameElapsedTicks() == 3L, "main counts toward game clock");
		check(mainTickOne.timelineRevision() == 8L, "ordinary main tick preserves revision");
		TimelineInstance mainTimedOut = changed(
			TimelineAuthority.tickRunning(mainTickOne, context(mainTickOne), main),
			"main timeout"
		);
		check(mainTimedOut.gameElapsedTicks() == 4L, "timeout tick advances game clock");
		check(mainTimedOut.timelineRevision() == 9L, "main timeout revision");
		TimelineInstance mainResult = changed(
			TimelineAuthority.submitResult(mainTimedOut, context(mainTimedOut), main, "finished"),
			"freeze final result"
		);
		TimelineInstance completed = changed(
			TimelineAuthority.settlementSucceeded(
				mainResult,
				context(mainResult),
				main,
				Optional.empty(),
				120L
			),
			"complete timeline"
		);
		check(completed.status() == TimelineStatus.COMPLETED, "timeline completed");
		check(completed.currentTask().isEmpty(), "completed timeline has no current task");
		check(completed.taskHistory().size() == 2, "both actual tasks archived");
		check(completed.completedAtServerTick().equals(Optional.of(120L)), "completion tick");
		checkValid(completed, "completed timeline");

		checkBlockAndIntermissionControls(warmup);
		checkInterruption(warmup);
		System.out.println("TIMELINE_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkInterruption(final TaskDefinition warmup) {
		TimelineInstance created = changed(
			TimelineAuthority.create(
				Optional.empty(),
				uuid(50),
				1,
				FrozenDocument.of("{\"timeline\":\"interrupt\"}"),
				new TaskStart(uuid(51), warmup, List.of(PLAYER_A)),
				200L
			),
			"interrupt fixture create"
		);
		TimelineInstance running = changed(
			TimelineAuthority.startSucceeded(created, context(created), 201L),
			"interrupt fixture start"
		);
		TimelineInstance interrupted = changed(
			TimelineAuthority.interrupt(running, context(running), 202L),
			"interrupt"
		);
		check(interrupted.status() == TimelineStatus.INTERRUPTED, "interrupted status");
		check(interrupted.currentTask().isEmpty(), "interrupted timeline has no current task");
		check(interrupted.taskHistory().size() == 1, "interrupted task archived");
		check(
			interrupted.taskHistory().getFirst().status() == TaskStatus.INTERRUPTED
				&& interrupted.taskHistory().getFirst().result().isEmpty(),
			"interruption does not manufacture a normal result"
		);
		checkValid(interrupted, "interrupted timeline");
	}

	private static void checkBlockAndIntermissionControls(final TaskDefinition warmup) {
		TimelineInstance created = changed(
			TimelineAuthority.create(
				Optional.empty(),
				uuid(60),
				1,
				FrozenDocument.of("{\"timeline\":\"block-controls\"}"),
				new TaskStart(uuid(61), warmup, List.of(PLAYER_A)),
				300L
			),
			"block fixture create"
		);
		TimelineInstance running = changed(
			TimelineAuthority.startSucceeded(created, context(created), 301L),
			"block fixture start"
		);
		TimelineInstance blocked = changed(
			TimelineAuthority.block(running, context(running)),
			"technical block"
		);
		check(blocked.status() == TimelineStatus.BLOCKED, "technical block status");
		checkValid(blocked, "blocked running timeline");
		check(
			TimelineAuthority.unblock(blocked, context(blocked), false).code()
				== OperationCode.CALLBACK_FAILED,
			"unresolved callback must keep the timeline blocked"
		);
		TimelineInstance unblocked = changed(
			TimelineAuthority.unblock(blocked, context(blocked), true),
			"technical unblock"
		);
		check(unblocked.status() == TimelineStatus.RUNNING, "technical unblock resumes business status");

		TimelineInstance result = changed(
			TimelineAuthority.submitResult(unblocked, context(unblocked), warmup, "continue"),
			"block fixture result"
		);
		TimelineInstance intermission = changed(
			TimelineAuthority.settlementSucceeded(
				result,
				context(result),
				warmup,
				Optional.empty(),
				302L
			),
			"block fixture intermission"
		);
		TimelineInstance extended = changed(
			TimelineAuthority.extendIntermission(intermission, context(intermission), 3L),
			"extend intermission"
		);
		check(
			extended.currentTask().orElseThrow().intermission().orElseThrow().durationTicks() == 5L,
			"intermission extension"
		);
		TimelineInstance pausedIntermission = changed(
			TimelineAuthority.pause(intermission, context(intermission), "test:host"),
			"pause intermission"
		);
		TimelineInstance extendedWhilePaused = changed(
			TimelineAuthority.extendIntermission(
				pausedIntermission,
				context(pausedIntermission),
				3L
			),
			"extend paused intermission"
		);
		check(
			extendedWhilePaused.paused()
				&& extendedWhilePaused.currentTask()
					.orElseThrow()
					.intermission()
					.orElseThrow()
					.paused(),
			"extending a paused intermission must preserve both pause flags"
		);
		TimelineInstance elapsedWhilePaused = changed(
			TimelineAuthority.finishIntermissionEarly(
				extendedWhilePaused,
				context(extendedWhilePaused)
			),
			"finish paused intermission early"
		);
		check(
			elapsedWhilePaused.paused()
				&& elapsedWhilePaused.currentTask()
					.orElseThrow()
					.intermission()
					.orElseThrow()
					.elapsedTicks() == 5L,
			"early finish while paused must mark elapsed without resuming"
		);
		TimelineInstance elapsed = changed(
			TimelineAuthority.finishIntermissionEarly(extended, context(extended)),
			"finish intermission early"
		);
		check(
			elapsed.currentTask().orElseThrow().intermission().orElseThrow().elapsedTicks() == 5L,
			"early finish must mark the interval elapsed"
		);
		TimelineInstance blockedIntermission = changed(
			TimelineAuthority.block(elapsed, context(elapsed)),
			"block intermission"
		);
		checkValid(blockedIntermission, "blocked intermission timeline");
		check(
			changed(
				TimelineAuthority.unblock(
					blockedIntermission,
					context(blockedIntermission),
					true
				),
				"unblock intermission"
			).status() == TimelineStatus.INTERMISSION,
			"blocked intermission must restore its business status"
		);
	}

	private static TaskDefinition warmupDefinition() {
		IntermissionDefinition intermission = new IntermissionDefinition(
			2L,
			true,
			text("任务间隔"),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
		TaskResult continued = result(
			"continue",
			new TaskRoute(
				Optional.of(MAIN_ID),
				Optional.empty(),
				Optional.of(RUNNING_PHASE),
				Optional.of(TransitionTiming.AFTER_INTERMISSION),
				Optional.of(intermission)
			)
		);
		TaskResult alternate = result(
			"alternate",
			new TaskRoute(
				Optional.of(MAIN_ID),
				Optional.empty(),
				Optional.of(RUNNING_PHASE),
				Optional.of(TransitionTiming.AFTER_INTERMISSION),
				Optional.of(intermission)
			)
		);
		return definition(
			WARMUP_ID,
			TaskKind.WARMUP,
			TaskCompletionPolicy.EARLY_OR_TIMEOUT,
			OptionalLong.of(2L),
			false,
			Map.of("continue", continued, "alternate", alternate)
		);
	}

	private static TaskDefinition mainDefinition() {
		return definition(
			MAIN_ID,
			TaskKind.MAIN,
			TaskCompletionPolicy.TIMEOUT_ONLY,
			OptionalLong.of(2L),
			true,
			Map.of(
				"finished",
				result(
					"finished",
					new TaskRoute(
						Optional.empty(),
						Optional.of(END_PHASE),
						Optional.empty(),
						Optional.empty(),
						Optional.empty()
					)
				)
			)
		);
	}

	private static TaskDefinition definition(
		final Identifier id,
		final TaskKind kind,
		final TaskCompletionPolicy policy,
		final OptionalLong duration,
		final boolean countsTowardGameTime,
		final Map<String, TaskResult> results
	) {
		return new TaskDefinition(
			id,
			GAME,
			1,
			kind,
			text(id.toString()),
			Optional.empty(),
			Optional.empty(),
			io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.defaultTaskAudience(),
			policy,
			duration,
			countsTowardGameTime,
			LiveVisibility.hidden(),
			RecapVisibility.PARTICIPANTS,
			TaskCallbacks.empty(),
			Optional.empty(),
			results,
			List.of(),
			List.of()
		);
	}

	private static TaskResult result(final String id, final TaskRoute route) {
		return new TaskResult(
			id,
			text(id),
			new ResultStyle(ResultSemantic.NEUTRAL, Optional.empty()),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			false,
			route
		);
	}

	private static ActionContext context(final TimelineInstance timeline) {
		return context(timeline, timeline.timelineRevision());
	}

	private static ActionContext context(
		final TimelineInstance timeline,
		final long revision
	) {
		return new ActionContext(
			timeline.instanceId(),
			timeline.currentTask().orElseThrow().taskInstanceId(),
			timeline.currentTask().orElseThrow().taskId(),
			revision
		);
	}

	private static TimelineInstance changed(
		final OperationResult result,
		final String operation
	) {
		check(result.successful(), operation + " failed: " + result.code() + " " + result.message());
		return result.nextState().orElseThrow(
			() -> new AssertionError(operation + " did not return its changed state")
		);
	}

	private static void checkValid(final TimelineInstance timeline, final String label) {
		WorldStateV3 root = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			WorldStateV2.initial().withActivity(GAME, PHASE),
			Optional.of(TIMELINE_ID),
			Optional.of(timeline),
			List.of(),
			List.of()
		);
		check(
			root.validated().error().isEmpty(),
			label + " is invalid: " + root.validated().error().map(value -> value.message()).orElse("")
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
