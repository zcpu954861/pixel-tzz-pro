package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.IntermissionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskRoute;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TransitionTiming;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.IntermissionState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionTiming;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure immutable transactions for one persisted task timeline.
 *
 * <p>This authority deliberately does not parse the frozen document or execute callbacks. Its
 * caller must obtain {@link TaskDefinition} values from the already compiled frozen plan and only
 * report callback success after the corresponding ledger transaction has succeeded.
 */
public final class TimelineAuthority {
	private static final int MAX_PAUSE_REASON_LENGTH = 1_024;

	private TimelineAuthority() {
	}

	/**
	 * Optimistic identity supplied by every operation after creation.
	 */
	public record ActionContext(
		UUID timelineInstanceId,
		UUID taskInstanceId,
		Identifier taskId,
		long expectedTimelineRevision
	) {
		public ActionContext {
			Objects.requireNonNull(timelineInstanceId, "timelineInstanceId");
			Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			Objects.requireNonNull(taskId, "taskId");
		}
	}

	/**
	 * Caller-resolved immutable inputs for a task that is about to become current.
	 */
	public record TaskStart(
		UUID taskInstanceId,
		TaskDefinition definition,
		List<UUID> participants
	) {
		public TaskStart {
			Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			Objects.requireNonNull(definition, "definition");
			participants = List.copyOf(participants);
		}
	}

	public static OperationResult create(
		final Optional<TimelineInstance> existing,
		final UUID timelineInstanceId,
		final int contentVersion,
		final FrozenDocument snapshot,
		final TaskStart firstTask,
		final long createdAtServerTick
	) {
		if (
			existing == null
				|| timelineInstanceId == null
				|| snapshot == null
				|| firstTask == null
		) {
			return OperationResult.failed(OperationCode.TARGET_INVALID, "timeline creation input is missing");
		}
		if (existing.isPresent()) {
			return OperationResult.failed(
				OperationCode.TIMELINE_ALREADY_ACTIVE,
				"an unfinished or retained timeline already exists"
			);
		}
		if (contentVersion <= 0 || createdAtServerTick < 0L) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"timeline version and creation tick must be positive"
			);
		}
		if (snapshot.normalizedJson().isBlank() || snapshot.sha256().isBlank()) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"the frozen timeline snapshot is blank"
			);
		}
		OperationResult startFailure = validateTaskStart(
			firstTask,
			firstTask.definition().game(),
			Optional.empty(),
			List.of()
		);
		if (startFailure != null) {
			return startFailure;
		}
		try {
			TaskInstance task = newTask(firstTask, 0L);
			TimelineInstance timeline = new TimelineInstance(
				timelineInstanceId,
				firstTask.definition().game(),
				contentVersion,
				snapshot,
				TimelineStatus.STARTING,
				Optional.empty(),
				Optional.of(task),
				List.of(),
				0L,
				false,
				Optional.empty(),
				createdAtServerTick,
				Optional.empty(),
				Optional.empty(),
				0L,
				List.of()
			);
			return OperationResult.changed(timeline, "first task created in starting state");
		} catch (RuntimeException error) {
			return invalidInput(error);
		}
	}

	/**
	 * Commits successful completion of all required start callbacks.
	 */
	public static OperationResult startSucceeded(
		final TimelineInstance current,
		final ActionContext context,
		final long serverTick
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.STARTING
				|| task.status() != TaskStatus.STARTING
		) {
			return wrongState("only a starting task can enter running");
		}
		if (serverTick < current.createdAtServerTick()) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"task start tick precedes timeline creation"
			);
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance running = copyTask(
			task,
			TaskStatus.RUNNING,
			Optional.empty(),
			task.elapsedTicks(),
			task.result(),
			task.intermission(),
			task.settledAtGameTick()
		);
		TimelineInstance next = copyTimeline(
			current,
			TimelineStatus.RUNNING,
			Optional.empty(),
			Optional.of(running),
			current.taskHistory(),
			current.gameElapsedTicks(),
			current.paused(),
			current.pauseReason(),
			current.startedAtServerTick().or(() -> Optional.of(serverTick)),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(next, "task start callbacks succeeded; task is running");
	}

	/**
	 * Advances one valid server tick while a task is running.
	 *
	 * <p>Clock-only changes intentionally preserve {@code timelineRevision}. The tick that reaches a
	 * configured timeout also enters settling and therefore advances the revision once.
	 */
	public static OperationResult tickRunning(
		final TimelineInstance current,
		final ActionContext context,
		final TaskDefinition definition
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.RUNNING
				|| task.status() != TaskStatus.RUNNING
		) {
			return wrongState("task clocks only advance in running state");
		}
		OperationResult definitionFailure = definitionFailure(current, task, definition);
		if (definitionFailure != null) {
			return definitionFailure;
		}
		OperationResult policyFailure = timingPolicyFailure(definition);
		if (policyFailure != null) {
			return policyFailure;
		}
		if (current.paused()) {
			return OperationResult.unchanged("timeline is paused; clocks did not advance");
		}
		Long taskElapsed = incremented(task.elapsedTicks());
		Long gameElapsed = definition.countsTowardGameTime()
			? incremented(current.gameElapsedTicks())
			: current.gameElapsedTicks();
		if (taskElapsed == null || gameElapsed == null) {
			return OperationResult.failed(
				OperationCode.RESOURCE_BLOCKED,
				"timeline clock capacity is exhausted"
			);
		}
		OptionalLong duration = definition.durationTicks();
		if (duration.isPresent() && task.elapsedTicks() >= duration.getAsLong()) {
			return OperationResult.failed(
				OperationCode.SCHEMA_BLOCKED,
				"a running timed task is already at or beyond its timeout"
			);
		}
		boolean timedOut = duration.isPresent() && taskElapsed >= duration.getAsLong();
		Long revision = timedOut ? incremented(current.timelineRevision()) : current.timelineRevision();
		if (revision == null) {
			return revisionExhausted();
		}
		TaskStatus taskStatus = timedOut ? TaskStatus.SETTLING : TaskStatus.RUNNING;
		TimelineStatus timelineStatus = timedOut ? TimelineStatus.SETTLING : TimelineStatus.RUNNING;
		TaskInstance tickedTask = copyTask(
			task,
			taskStatus,
			Optional.empty(),
			taskElapsed,
			task.result(),
			task.intermission(),
			task.settledAtGameTick()
		);
		TimelineInstance next = copyTimeline(
			current,
			timelineStatus,
			Optional.empty(),
			Optional.of(tickedTask),
			current.taskHistory(),
			gameElapsed,
			false,
			Optional.empty(),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(
			next,
			timedOut ? "task reached its timeout and entered settling" : "task clocks advanced"
		);
	}

	public static OperationResult pause(
		final TimelineInstance current,
		final ActionContext context,
		final String reason
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (reason == null || reason.isBlank() || reason.length() > MAX_PAUSE_REASON_LENGTH) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"pause reason must be non-blank and at most " + MAX_PAUSE_REASON_LENGTH + " characters"
			);
		}
		if (
			current.status() != TimelineStatus.RUNNING
				&& current.status() != TimelineStatus.INTERMISSION
		) {
			return wrongState("only a running task or intermission can be paused");
		}
		if (current.paused()) {
			return OperationResult.unchanged("timeline is already paused");
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskInstance pausedTask = current.status() == TimelineStatus.INTERMISSION
			? withIntermissionPaused(task, true)
			: task;
		TimelineInstance next = copyTimeline(
			current,
			current.status(),
			current.resumeStatus(),
			Optional.of(pausedTask),
			current.taskHistory(),
			current.gameElapsedTicks(),
			true,
			Optional.of(reason),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(next, "timeline paused");
	}

	public static OperationResult resume(
		final TimelineInstance current,
		final ActionContext context
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (
			current.status() != TimelineStatus.RUNNING
				&& current.status() != TimelineStatus.INTERMISSION
		) {
			return wrongState("only a running task or intermission can resume");
		}
		if (!current.paused()) {
			return OperationResult.unchanged("timeline is not paused");
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskInstance resumedTask = current.status() == TimelineStatus.INTERMISSION
			? withIntermissionPaused(task, false)
			: task;
		TimelineInstance next = copyTimeline(
			current,
			current.status(),
			current.resumeStatus(),
			Optional.of(resumedTask),
			current.taskHistory(),
			current.gameElapsedTicks(),
			false,
			Optional.empty(),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(next, "timeline resumed");
	}

	/**
	 * Applies the technical block overlay after a required callback cannot safely advance.
	 */
	public static OperationResult block(
		final TimelineInstance current,
		final ActionContext context
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (current.status() == TimelineStatus.BLOCKED) {
			return OperationResult.unchanged("timeline is already technically blocked");
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskInstance blockedTask = copyTask(
			task,
			TaskStatus.BLOCKED,
			Optional.of(task.status()),
			task.elapsedTicks(),
			task.result(),
			task.intermission(),
			task.settledAtGameTick()
		);
		TimelineInstance next = copyTimeline(
			current,
			TimelineStatus.BLOCKED,
			Optional.of(current.status()),
			Optional.of(blockedTask),
			current.taskHistory(),
			current.gameElapsedTicks(),
			current.paused(),
			current.pauseReason(),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(next, "timeline entered a recoverable technical block");
	}

	/**
	 * Removes a technical block only after the callback coordinator proves all required steps safe.
	 */
	public static OperationResult unblock(
		final TimelineInstance current,
		final ActionContext context,
		final boolean callbacksResolved
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (current.status() != TimelineStatus.BLOCKED) {
			return OperationResult.failed(
				OperationCode.TASK_STATE_MISMATCH,
				"timeline is not technically blocked"
			);
		}
		if (!callbacksResolved) {
			return OperationResult.failed(
				OperationCode.CALLBACK_FAILED,
				"required callback steps are still failed or outcome-unknown"
			);
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskStatus taskStatus = task.resumeStatus().orElseThrow();
		TimelineStatus timelineStatus = current.resumeStatus().orElseThrow();
		TaskInstance resumedTask = copyTask(
			task,
			taskStatus,
			Optional.empty(),
			task.elapsedTicks(),
			task.result(),
			task.intermission(),
			task.settledAtGameTick()
		);
		TimelineInstance next = copyTimeline(
			current,
			timelineStatus,
			Optional.empty(),
			Optional.of(resumedTask),
			current.taskHistory(),
			current.gameElapsedTicks(),
			current.paused(),
			current.pauseReason(),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(next, "technical block cleared");
	}

	/**
	 * Atomically freezes the first accepted result and its route.
	 */
	public static OperationResult submitResult(
		final TimelineInstance current,
		final ActionContext context,
		final TaskDefinition definition,
		final String resultId
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		TaskInstance task = current.currentTask().orElseThrow();
		OperationResult definitionFailure = definitionFailure(current, task, definition);
		if (definitionFailure != null) {
			return definitionFailure;
		}
		if (resultId == null || resultId.isBlank() || resultId.equals("skipped")) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"result ID is missing or reserved"
			);
		}
		if (task.result().isPresent()) {
			return task.result().orElseThrow().resultId().equals(resultId)
				? OperationResult.unchanged("the same result was already accepted")
				: OperationResult.failed(
					OperationCode.RESULT_ALREADY_FROZEN,
					"another result is already frozen and cannot be replaced"
				);
		}
		boolean running = current.status() == TimelineStatus.RUNNING
			&& task.status() == TaskStatus.RUNNING;
		boolean settling = current.status() == TimelineStatus.SETTLING
			&& task.status() == TaskStatus.SETTLING;
		if (!running && !settling) {
			return wrongState("results are only accepted from a running or settling task");
		}
		if (running && definition.completionPolicy() == TaskCompletionPolicy.TIMEOUT_ONLY) {
			return OperationResult.failed(
				OperationCode.COMPLETION_POLICY_MISMATCH,
				"timeout-only tasks cannot accept an early result"
			);
		}
		if (settling && definition.completionPolicy() == TaskCompletionPolicy.EVENT_ONLY) {
			return OperationResult.failed(
				OperationCode.COMPLETION_POLICY_MISMATCH,
				"event-only tasks do not enter resultless timeout settlement"
			);
		}
		TaskResult registered = definition.results().get(resultId);
		if (registered == null || !registered.id().equals(resultId)) {
			return OperationResult.failed(
				OperationCode.RESULT_NOT_REGISTERED,
				"result is not registered in the frozen task"
			);
		}
		TaskRoute route = registered.route();
		if (!validRoute(route)) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"registered result route is invalid"
			);
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		FrozenResult frozen = new FrozenResult(
			resultId,
			route.nextTask(),
			route.endPhase(),
			route.phase(),
			route.transitionTiming().map(TimelineAuthority::toStateTransitionTiming),
			current.gameElapsedTicks()
		);
		TaskInstance settlingTask = copyTask(
			task,
			TaskStatus.SETTLING,
			Optional.empty(),
			task.elapsedTicks(),
			Optional.of(frozen),
			Optional.empty(),
			Optional.empty()
		);
		TimelineInstance next = copyTimeline(
			current,
			TimelineStatus.SETTLING,
			Optional.empty(),
			Optional.of(settlingTask),
			current.taskHistory(),
			current.gameElapsedTicks(),
			false,
			Optional.empty(),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			revision
		);
		return OperationResult.changed(next, "result and route frozen; task is settling");
	}

	/**
	 * Commits successful completion of all result and settlement callbacks.
	 */
	public static OperationResult settlementSucceeded(
		final TimelineInstance current,
		final ActionContext context,
		final TaskDefinition definition,
		final Optional<TaskStart> nextTask,
		final long serverTick
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (nextTask == null) {
			return OperationResult.failed(OperationCode.TARGET_INVALID, "next task input is missing");
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.SETTLING
				|| task.status() != TaskStatus.SETTLING
				|| task.result().isEmpty()
		) {
			return wrongState("only a settling task with a frozen result can finish settlement");
		}
		if (current.paused()) {
			return wrongState("a paused timeline cannot finish settlement");
		}
		if (serverTick < current.createdAtServerTick()) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"settlement tick precedes timeline creation"
			);
		}
		OperationResult definitionFailure = definitionFailure(current, task, definition);
		if (definitionFailure != null) {
			return definitionFailure;
		}
		FrozenResult frozen = task.result().orElseThrow();
		TaskResult registered = definition.results().get(frozen.resultId());
		if (registered == null || !routeMatches(registered.route(), frozen)) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"frozen result route no longer matches the supplied frozen task"
			);
		}
		Optional<IntermissionDefinition> intermission = registered.route().intermission();
		if (intermission.isPresent()) {
			if (nextTask.isPresent()) {
				return OperationResult.failed(
					OperationCode.TARGET_INVALID,
					"next task must not be created before intermission completes"
				);
			}
			IntermissionDefinition definitionValue = intermission.orElseThrow();
			if (definitionValue.durationTicks() <= 0L) {
				return OperationResult.failed(
					OperationCode.SNAPSHOT_INVALID,
					"intermission duration must be positive"
				);
			}
			Long revision = incremented(current.timelineRevision());
			if (revision == null) {
				return revisionExhausted();
			}
			TaskInstance settled = copyTask(
				task,
				TaskStatus.SETTLED,
				Optional.empty(),
				task.elapsedTicks(),
				task.result(),
				Optional.of(
					new IntermissionState(
						definitionValue.durationTicks(),
						0L,
						definitionValue.countsTowardGameTime(),
						false
					)
				),
				Optional.of(current.gameElapsedTicks())
			);
			TimelineInstance next = copyTimeline(
				current,
				TimelineStatus.INTERMISSION,
				Optional.empty(),
				Optional.of(settled),
				current.taskHistory(),
				current.gameElapsedTicks(),
				false,
				Optional.empty(),
				current.startedAtServerTick(),
				current.completedAtServerTick(),
				revision
			);
			return OperationResult.changed(next, "settlement succeeded; intermission started");
		}
		TaskInstance settled = copyTask(
			task,
			TaskStatus.SETTLED,
			Optional.empty(),
			task.elapsedTicks(),
			task.result(),
			Optional.empty(),
			Optional.of(current.gameElapsedTicks())
		);
		return advanceAfterSettled(current, settled, nextTask, serverTick, "settlement succeeded");
	}

	/**
	 * Advances one valid server tick during an intermission.
	 */
	public static OperationResult tickIntermission(
		final TimelineInstance current,
		final ActionContext context
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.INTERMISSION
				|| task.status() != TaskStatus.SETTLED
				|| task.intermission().isEmpty()
		) {
			return OperationResult.failed(
				OperationCode.INTERMISSION_NOT_ACTIVE,
				"intermission clocks only advance in intermission state"
			);
		}
		IntermissionState intermission = task.intermission().orElseThrow();
		if (current.paused() || intermission.paused()) {
			return OperationResult.unchanged("intermission is paused; clocks did not advance");
		}
		if (intermission.elapsedTicks() >= intermission.durationTicks()) {
			return OperationResult.unchanged("intermission duration elapsed; completion is pending");
		}
		Long elapsed = incremented(intermission.elapsedTicks());
		Long gameElapsed = intermission.countsTowardGameTime()
			? incremented(current.gameElapsedTicks())
			: current.gameElapsedTicks();
		if (elapsed == null || gameElapsed == null) {
			return OperationResult.failed(
				OperationCode.RESOURCE_BLOCKED,
				"intermission clock capacity is exhausted"
			);
		}
		IntermissionState ticked = new IntermissionState(
			intermission.durationTicks(),
			Math.min(elapsed, intermission.durationTicks()),
			intermission.countsTowardGameTime(),
			false
		);
		TaskInstance tickedTask = copyTask(
			task,
			task.status(),
			task.resumeStatus(),
			task.elapsedTicks(),
			task.result(),
			Optional.of(ticked),
			task.settledAtGameTick()
		);
		TimelineInstance next = copyTimeline(
			current,
			current.status(),
			current.resumeStatus(),
			Optional.of(tickedTask),
			current.taskHistory(),
			gameElapsed,
			false,
			Optional.empty(),
			current.startedAtServerTick(),
			current.completedAtServerTick(),
			current.timelineRevision()
		);
		return OperationResult.changed(next, "intermission clocks advanced");
	}

	public static OperationResult extendIntermission(
		final TimelineInstance current,
		final ActionContext context,
		final long additionalTicks
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.INTERMISSION
				|| task.status() != TaskStatus.SETTLED
				|| task.intermission().isEmpty()
		) {
			return OperationResult.failed(
				OperationCode.INTERMISSION_NOT_ACTIVE,
				"only an active intermission can be extended"
			);
		}
		if (additionalTicks <= 0L) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"intermission extension must be positive"
			);
		}
		try {
			IntermissionState intermission = task.intermission().orElseThrow();
			long duration = Math.addExact(intermission.durationTicks(), additionalTicks);
			if (duration > DefinitionCompiler.MAX_INTERMISSION_DURATION_TICKS) {
				return OperationResult.failed(
					OperationCode.RESOURCE_BLOCKED,
					"extended intermission exceeds the registered duration limit"
				);
			}
			Long revision = incremented(current.timelineRevision());
			if (revision == null) {
				return revisionExhausted();
			}
			TaskInstance extendedTask = copyTask(
				task,
				task.status(),
				task.resumeStatus(),
				task.elapsedTicks(),
				task.result(),
				Optional.of(
					new IntermissionState(
						duration,
						intermission.elapsedTicks(),
						intermission.countsTowardGameTime(),
						intermission.paused()
					)
				),
				task.settledAtGameTick()
			);
			return OperationResult.changed(
				copyTimeline(
					current,
					current.status(),
					current.resumeStatus(),
					Optional.of(extendedTask),
					current.taskHistory(),
					current.gameElapsedTicks(),
					current.paused(),
					current.pauseReason(),
					current.startedAtServerTick(),
					current.completedAtServerTick(),
					revision
				),
				"intermission extended"
			);
		} catch (ArithmeticException error) {
			return OperationResult.failed(
				OperationCode.RESOURCE_BLOCKED,
				"intermission duration capacity is exhausted"
			);
		}
	}

	/**
	 * Marks the current intermission elapsed; callback completion still gates the next task.
	 */
	public static OperationResult finishIntermissionEarly(
		final TimelineInstance current,
		final ActionContext context
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.INTERMISSION
				|| task.status() != TaskStatus.SETTLED
				|| task.intermission().isEmpty()
		) {
			return OperationResult.failed(
				OperationCode.INTERMISSION_NOT_ACTIVE,
				"only an active intermission can finish early"
			);
		}
		IntermissionState intermission = task.intermission().orElseThrow();
		if (intermission.elapsedTicks() >= intermission.durationTicks()) {
			return OperationResult.unchanged("intermission is already elapsed");
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance elapsedTask = copyTask(
			task,
			task.status(),
			task.resumeStatus(),
			task.elapsedTicks(),
			task.result(),
			Optional.of(
				new IntermissionState(
					intermission.durationTicks(),
					intermission.durationTicks(),
					intermission.countsTowardGameTime(),
					intermission.paused()
				)
			),
			task.settledAtGameTick()
		);
		return OperationResult.changed(
			copyTimeline(
				current,
				current.status(),
				current.resumeStatus(),
				Optional.of(elapsedTask),
				current.taskHistory(),
				current.gameElapsedTicks(),
				current.paused(),
				current.pauseReason(),
				current.startedAtServerTick(),
				current.completedAtServerTick(),
				revision
			),
			"intermission marked complete; callback completion is pending"
		);
	}

	/**
	 * Commits successful completion of the intermission callback and follows the frozen route.
	 */
	public static OperationResult intermissionSucceeded(
		final TimelineInstance current,
		final ActionContext context,
		final Optional<TaskStart> nextTask,
		final long serverTick
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (nextTask == null) {
			return OperationResult.failed(OperationCode.TARGET_INVALID, "next task input is missing");
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			current.status() != TimelineStatus.INTERMISSION
				|| task.status() != TaskStatus.SETTLED
				|| task.result().isEmpty()
				|| task.intermission().isEmpty()
		) {
			return OperationResult.failed(
				OperationCode.INTERMISSION_NOT_ACTIVE,
				"only a settled task in intermission can complete its intermission"
			);
		}
		if (current.paused() || task.intermission().orElseThrow().paused()) {
			return OperationResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"a paused intermission cannot complete"
			);
		}
		IntermissionState intermission = task.intermission().orElseThrow();
		if (intermission.elapsedTicks() < intermission.durationTicks()) {
			return OperationResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"intermission duration has not elapsed"
			);
		}
		if (serverTick < current.createdAtServerTick()) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"intermission completion tick precedes timeline creation"
			);
		}
		return advanceAfterSettled(
			current,
			task,
			nextTask,
			serverTick,
			"intermission completed"
		);
	}

	/**
	 * Terminates the whole timeline without manufacturing a normal task result.
	 */
	public static OperationResult interrupt(
		final TimelineInstance current,
		final ActionContext context,
		final long serverTick
	) {
		OperationResult failure = contextFailure(current, context);
		if (failure != null) {
			return failure;
		}
		if (serverTick < current.createdAtServerTick()) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"interruption tick precedes timeline creation"
			);
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskInstance terminalTask;
		if (
			task.status() == TaskStatus.SETTLED
				&& task.result().isPresent()
				&& task.settledAtGameTick().isPresent()
		) {
			terminalTask = task;
		} else {
			terminalTask = copyTask(
				task,
				TaskStatus.INTERRUPTED,
				Optional.empty(),
				task.elapsedTicks(),
				Optional.empty(),
				Optional.empty(),
				Optional.of(current.gameElapsedTicks())
			);
		}
		OperationResult historyFailure = historyCapacityFailure(current, terminalTask);
		if (historyFailure != null) {
			return historyFailure;
		}
		List<TaskInstance> history = appended(current.taskHistory(), terminalTask);
		TimelineInstance next = copyTimeline(
			current,
			TimelineStatus.INTERRUPTED,
			Optional.empty(),
			Optional.empty(),
			history,
			current.gameElapsedTicks(),
			false,
			Optional.empty(),
			current.startedAtServerTick(),
			Optional.of(serverTick),
			revision
		);
		return OperationResult.changed(
			next,
			"timeline interrupted; no new normal result was manufactured"
		);
	}

	private static OperationResult advanceAfterSettled(
		final TimelineInstance current,
		final TaskInstance settled,
		final Optional<TaskStart> nextTask,
		final long serverTick,
		final String prefix
	) {
		FrozenResult frozen = settled.result().orElseThrow();
		OperationResult historyFailure = historyCapacityFailure(current, settled);
		if (historyFailure != null) {
			return historyFailure;
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		List<TaskInstance> history = appended(current.taskHistory(), settled);
		if (frozen.nextTaskId().isPresent()) {
			if (history.size() >= WorldStateV3.MAX_TIMELINE_TASKS) {
				return OperationResult.failed(
					OperationCode.SNAPSHOT_TOO_LARGE,
					"timeline task limit leaves no room for the routed task"
				);
			}
			if (nextTask.isEmpty()) {
				return OperationResult.failed(
					OperationCode.TARGET_INVALID,
					"the frozen route requires a next task"
				);
			}
			TaskStart start = nextTask.orElseThrow();
			if (!frozen.nextTaskId().orElseThrow().equals(start.definition().id())) {
				return OperationResult.failed(
					OperationCode.TASK_NOT_CURRENT,
					"next task does not match the frozen route"
				);
			}
			OperationResult startFailure = validateTaskStart(
				start,
				current.gameId(),
				Optional.of(settled),
				history
			);
			if (startFailure != null) {
				return startFailure;
			}
			try {
				TaskInstance following = newTask(start, current.gameElapsedTicks());
				TimelineInstance next = copyTimeline(
					current,
					TimelineStatus.STARTING,
					Optional.empty(),
					Optional.of(following),
					history,
					current.gameElapsedTicks(),
					false,
					Optional.empty(),
					current.startedAtServerTick(),
					Optional.empty(),
					revision
				);
				return OperationResult.changed(next, prefix + "; next task entered starting");
			} catch (RuntimeException error) {
				return invalidInput(error);
			}
		}
		if (nextTask.isPresent()) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"the frozen end route does not accept a next task"
			);
		}
		if (frozen.endPhaseId().isEmpty()) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"frozen route has no next task or end phase"
			);
		}
		TimelineInstance next = copyTimeline(
			current,
			TimelineStatus.COMPLETED,
			Optional.empty(),
			Optional.empty(),
			history,
			current.gameElapsedTicks(),
			false,
			Optional.empty(),
			current.startedAtServerTick(),
			Optional.of(serverTick),
			revision
		);
		return OperationResult.changed(next, prefix + "; timeline completed");
	}

	private static OperationResult contextFailure(
		final TimelineInstance current,
		final ActionContext context
	) {
		if (current == null || context == null) {
			return OperationResult.failed(
				OperationCode.TIMELINE_NOT_ACTIVE,
				"timeline context is missing"
			);
		}
		if (
			current.status() == TimelineStatus.COMPLETED
				|| current.status() == TimelineStatus.INTERRUPTED
		) {
			return OperationResult.failed(
				OperationCode.TIMELINE_COMPLETED,
				"timeline is already terminal"
			);
		}
		if (!current.instanceId().equals(context.timelineInstanceId())) {
			return OperationResult.failed(
				OperationCode.TIMELINE_NOT_ACTIVE,
				"timeline instance changed"
			);
		}
		if (current.currentTask().isEmpty()) {
			return OperationResult.failed(
				OperationCode.TIMELINE_NOT_ACTIVE,
				"timeline has no current task"
			);
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			!task.taskInstanceId().equals(context.taskInstanceId())
				|| !task.taskId().equals(context.taskId())
		) {
			return OperationResult.failed(
				OperationCode.TASK_NOT_CURRENT,
				"current task changed"
			);
		}
		if (
			context.expectedTimelineRevision() < 0L
				|| current.timelineRevision() != context.expectedTimelineRevision()
		) {
			return OperationResult.failed(
				OperationCode.REVISION_MISMATCH,
				"timeline revision changed"
			);
		}
		if (!validStatePair(current.status(), task)) {
			return OperationResult.failed(
				OperationCode.SCHEMA_BLOCKED,
				"timeline and task states are inconsistent"
			);
		}
		if (current.paused() != current.pauseReason().isPresent()) {
			return OperationResult.failed(
				OperationCode.SCHEMA_BLOCKED,
				"timeline pause state is inconsistent"
			);
		}
		return null;
	}

	private static boolean validStatePair(
		final TimelineStatus timelineStatus,
		final TaskInstance task
	) {
		return switch (timelineStatus) {
			case STARTING -> task.status() == TaskStatus.STARTING;
			case RUNNING -> task.status() == TaskStatus.RUNNING;
			case SETTLING -> task.status() == TaskStatus.SETTLING;
			case INTERMISSION -> task.status() == TaskStatus.SETTLED
				&& task.intermission().isPresent();
			case BLOCKED -> task.status() == TaskStatus.BLOCKED;
			case PRE_START, COMPLETED, INTERRUPTED -> false;
		};
	}

	private static OperationResult definitionFailure(
		final TimelineInstance timeline,
		final TaskInstance task,
		final TaskDefinition definition
	) {
		if (definition == null) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"frozen task definition is unavailable"
			);
		}
		if (
			!definition.id().equals(task.taskId())
				|| !definition.game().equals(timeline.gameId())
				|| toStateKind(definition.kind()) != task.kind()
		) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"frozen task definition does not match the current task"
			);
		}
		return null;
	}

	private static OperationResult timingPolicyFailure(final TaskDefinition definition) {
		OptionalLong duration = definition.durationTicks();
		boolean timed = definition.completionPolicy() != TaskCompletionPolicy.EVENT_ONLY;
		if (timed != duration.isPresent() || duration.isPresent() && duration.getAsLong() <= 0L) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"task completion policy and duration are inconsistent"
			);
		}
		return null;
	}

	private static OperationResult validateTaskStart(
		final TaskStart start,
		final Identifier gameId,
		final Optional<TaskInstance> previous,
		final List<TaskInstance> history
	) {
		if (!start.definition().game().equals(gameId)) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"next task belongs to another game"
			);
		}
		if (
			previous.filter(task -> task.taskInstanceId().equals(start.taskInstanceId())).isPresent()
				|| history.stream()
					.anyMatch(task -> task.taskInstanceId().equals(start.taskInstanceId()))
		) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"task instance ID is already in this timeline"
			);
		}
		if (
			previous.filter(task -> task.taskId().equals(start.definition().id())).isPresent()
				|| history.stream().anyMatch(task -> task.taskId().equals(start.definition().id()))
		) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"task route would revisit an earlier task"
			);
		}
		if (start.participants().size() > WorldStateV3.MAX_TIMELINE_PARTICIPANTS) {
			return OperationResult.failed(
				OperationCode.TARGET_COUNT_INVALID,
				"task participant count exceeds the timeline limit"
			);
		}
		return timingPolicyFailure(start.definition());
	}

	private static OperationResult historyCapacityFailure(
		final TimelineInstance timeline,
		final TaskInstance terminalTask
	) {
		if (timeline.taskHistory().size() >= WorldStateV3.MAX_TIMELINE_TASKS) {
			return OperationResult.failed(
				OperationCode.SNAPSHOT_TOO_LARGE,
				"timeline task history reached its limit"
			);
		}
		if (
			timeline.taskHistory().stream()
				.anyMatch(task -> task.taskInstanceId().equals(terminalTask.taskInstanceId()))
		) {
			return OperationResult.failed(
				OperationCode.SCHEMA_BLOCKED,
				"current task is already present in timeline history"
			);
		}
		return null;
	}

	private static boolean validRoute(final TaskRoute route) {
		if (route == null || route.nextTask().isPresent() == route.endPhase().isPresent()) {
			return false;
		}
		if (route.endPhase().isPresent() && route.phase().isPresent()) {
			return false;
		}
		if (route.intermission().isEmpty() && route.transitionTiming().isPresent()) {
			return false;
		}
		return route.intermission().filter(value -> value.durationTicks() <= 0L).isEmpty();
	}

	private static boolean routeMatches(final TaskRoute route, final FrozenResult frozen) {
		return validRoute(route)
			&& route.nextTask().equals(frozen.nextTaskId())
			&& route.endPhase().equals(frozen.endPhaseId())
			&& route.phase().equals(frozen.phaseId())
			&& route.transitionTiming()
				.map(TimelineAuthority::toStateTransitionTiming)
				.equals(frozen.phaseTransitionTiming());
	}

	private static PhaseTransitionTiming toStateTransitionTiming(
		final TransitionTiming timing
	) {
		return switch (timing) {
			case BEFORE_INTERMISSION -> PhaseTransitionTiming.BEFORE_INTERMISSION;
			case AFTER_INTERMISSION -> PhaseTransitionTiming.AFTER_INTERMISSION;
		};
	}

	private static TaskInstance newTask(final TaskStart start, final long gameElapsedTicks) {
		return new TaskInstance(
			start.taskInstanceId(),
			start.definition().id(),
			toStateKind(start.definition().kind()),
			TaskStatus.STARTING,
			Optional.empty(),
			0L,
			Optional.empty(),
			Optional.empty(),
			start.participants(),
			gameElapsedTicks,
			Optional.empty(),
			List.of()
		);
	}

	private static io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind toStateKind(
		final io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskKind kind
	) {
		return switch (kind) {
			case WARMUP -> io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.WARMUP;
			case MAIN -> io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind.MAIN;
		};
	}

	private static TaskInstance withIntermissionPaused(
		final TaskInstance task,
		final boolean paused
	) {
		IntermissionState current = task.intermission().orElseThrow();
		return copyTask(
			task,
			task.status(),
			task.resumeStatus(),
			task.elapsedTicks(),
			task.result(),
			Optional.of(
				new IntermissionState(
					current.durationTicks(),
					current.elapsedTicks(),
					current.countsTowardGameTime(),
					paused
				)
			),
			task.settledAtGameTick()
		);
	}

	private static TaskInstance copyTask(
		final TaskInstance source,
		final TaskStatus status,
		final Optional<TaskStatus> resumeStatus,
		final long elapsedTicks,
		final Optional<FrozenResult> result,
		final Optional<IntermissionState> intermission,
		final Optional<Long> settledAtGameTick
	) {
		return new TaskInstance(
			source.taskInstanceId(),
			source.taskId(),
			source.kind(),
			status,
			resumeStatus,
			elapsedTicks,
			result,
			intermission,
			source.participants(),
			source.startedAtGameTick(),
			settledAtGameTick,
			source.callbackLedger(),
			source.lifecycleCallbackTrigger(),
			source.phaseTransitionTrigger(),
			source.events(),
			source.statistics()
		);
	}

	private static TimelineInstance copyTimeline(
		final TimelineInstance source,
		final TimelineStatus status,
		final Optional<TimelineStatus> resumeStatus,
		final Optional<TaskInstance> currentTask,
		final List<TaskInstance> taskHistory,
		final long gameElapsedTicks,
		final boolean paused,
		final Optional<String> pauseReason,
		final Optional<Long> startedAtServerTick,
		final Optional<Long> completedAtServerTick,
		final long timelineRevision
	) {
		return new TimelineInstance(
			source.instanceId(),
			source.gameId(),
			source.contentVersion(),
			source.snapshot(),
			status,
			resumeStatus,
			currentTask,
			taskHistory,
			gameElapsedTicks,
			paused,
			pauseReason,
			source.createdAtServerTick(),
			startedAtServerTick,
			completedAtServerTick,
			timelineRevision,
			source.callbackLedger()
		);
	}

	private static List<TaskInstance> appended(
		final List<TaskInstance> history,
		final TaskInstance task
	) {
		List<TaskInstance> next = new ArrayList<>(history.size() + 1);
		next.addAll(history);
		next.add(task);
		return List.copyOf(next);
	}

	private static Long incremented(final long value) {
		return value == Long.MAX_VALUE ? null : value + 1L;
	}

	private static OperationResult wrongState(final String message) {
		return OperationResult.failed(OperationCode.TASK_STATE_MISMATCH, message);
	}

	private static OperationResult revisionExhausted() {
		return OperationResult.failed(
			OperationCode.RESOURCE_BLOCKED,
			"timeline revision capacity is exhausted"
		);
	}

	private static OperationResult invalidInput(final RuntimeException error) {
		String message = error.getMessage();
		return OperationResult.failed(
			OperationCode.TARGET_INVALID,
			message == null || message.isBlank() ? error.getClass().getSimpleName() : message
		);
	}

	public record OperationResult(
		OperationCode code,
		String message,
		Optional<TimelineInstance> nextState
	) {
		public OperationResult {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (code != OperationCode.SUCCESS && nextState.isPresent()) {
				throw new IllegalArgumentException("failed timeline operation cannot change state");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		public boolean changed() {
			return this.nextState.isPresent();
		}

		private static OperationResult changed(
			final TimelineInstance next,
			final String message
		) {
			return new OperationResult(OperationCode.SUCCESS, message, Optional.of(next));
		}

		private static OperationResult unchanged(final String message) {
			return new OperationResult(OperationCode.SUCCESS, message, Optional.empty());
		}

		private static OperationResult failed(
			final OperationCode code,
			final String message
		) {
			return new OperationResult(code, message, Optional.empty());
		}
	}
}
