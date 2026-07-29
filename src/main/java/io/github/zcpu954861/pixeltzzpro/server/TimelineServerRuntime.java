package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.IntermissionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.PlayerCallback;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskTimeline;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostSubtitleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.MutationResult;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.PrepareResult;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.PreparedInvocation;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.StepIdentity;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.ActionContext;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.TaskStart;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStepStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.IntermissionState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.LifecycleCallbackKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.LifecycleCallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionTiming;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionCause;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.PhaseTransitionTrigger;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Executes one persisted timeline from its frozen data-pack snapshot.
 *
 * <p>Every callback is committed as {@code prepared} before its function runs and receives a
 * separately committed outcome afterwards. Routine clocks use the narrow checkpoint transaction;
 * lifecycle changes, callback ledger changes, and phase transitions use schema-v3 transactions.
 */
public final class TimelineServerRuntime {
	private static final String HOST_OFFLINE_PAUSE_REASON = "pixel_tzz:host_offline";
	private static final String RECOVERY_REASON =
		"server restarted while the callback outcome was not durably recorded";
	private static final Map<MinecraftServer, CachedSnapshot> SNAPSHOT_CACHE =
		new IdentityHashMap<>();
	private static final Map<UUID, String> LAST_DIAGNOSTIC = new LinkedHashMap<>();

	private TimelineServerRuntime() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(TimelineServerRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SNAPSHOT_CACHE.remove(server);
			LAST_DIAGNOSTIC.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TimelineServerRuntime::onServerTick);
	}

	/**
	 * Pauses the active running task or intermission and dispatches its registered callback.
	 *
	 * <p>Permission and host confirmation belong to the caller. This method revalidates the active
	 * timeline and owns the callback trigger so UI code cannot accidentally build a second runner.
	 */
	public static RuntimeResult hostPause(
		final MinecraftServer server,
		final String reason
	) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		TimelineInstance current = resolved.timeline().orElseThrow();
		TaskInstance currentTask = current.currentTask().orElseThrow();
		if (
			currentTask.phaseTransitionTrigger().isPresent()
				|| currentTask.lifecycleCallbackTrigger().isPresent()
		) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"the current task is still completing a lifecycle step"
			);
		}
		if (current.currentTask().orElseThrow().lifecycleCallbackTrigger().isPresent()) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"a lifecycle callback is already pending"
			);
		}
		TimelineAuthority.OperationResult paused = TimelineAuthority.pause(
			current,
			context(current),
			reason
		);
		if (!paused.successful()) {
			return RuntimeResult.failed(paused.code(), paused.message());
		}
		if (!paused.changed()) {
			return RuntimeResult.unchanged(paused.message());
		}
		TimelineInstance next = paused.nextState().orElseThrow();
		Optional<LifecycleCallbackKind> callbackKind = pauseCallbackKind(
			resolved.definition().orElseThrow(),
			current
		);
		if (callbackKind.isPresent()) {
			next = withLifecycleTrigger(
				next,
				Optional.of(
					new LifecycleCallbackTrigger(
						callbackKind.orElseThrow(),
						next.timelineRevision()
					)
				)
			);
		}
		RuntimeResult committed = commitLifecycle(server, resolved.root().orElseThrow(), current, next);
		if (!committed.successful() || callbackKind.isEmpty()) {
			return committed;
		}
		RuntimeResult callback = dispatchPendingLifecycleCallback(server);
		return callback.successful()
			? RuntimeResult.changed(paused.message())
			: callback;
	}

	/**
	 * Resumes a host-paused timeline and dispatches the matching resume callback.
	 */
	public static RuntimeResult hostResume(final MinecraftServer server) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		TimelineInstance current = resolved.timeline().orElseThrow();
		if (current.currentTask().orElseThrow().phaseTransitionTrigger().isPresent()) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"a phase transition is already pending"
			);
		}
		if (current.currentTask().orElseThrow().lifecycleCallbackTrigger().isPresent()) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"the previous lifecycle callback is still pending"
			);
		}
		TimelineAuthority.OperationResult resumed = TimelineAuthority.resume(
			current,
			context(current)
		);
		if (!resumed.successful()) {
			return RuntimeResult.failed(resumed.code(), resumed.message());
		}
		if (!resumed.changed()) {
			return RuntimeResult.unchanged(resumed.message());
		}
		TimelineInstance next = resumed.nextState().orElseThrow();
		Optional<LifecycleCallbackKind> callbackKind = resumeCallbackKind(
			resolved.definition().orElseThrow(),
			current
		);
		if (callbackKind.isPresent()) {
			next = withLifecycleTrigger(
				next,
				Optional.of(
					new LifecycleCallbackTrigger(
						callbackKind.orElseThrow(),
						next.timelineRevision()
					)
				)
			);
		}
		RuntimeResult committed = commitLifecycle(server, resolved.root().orElseThrow(), current, next);
		if (!committed.successful() || callbackKind.isEmpty()) {
			return committed;
		}
		RuntimeResult callback = dispatchPendingLifecycleCallback(server);
		return callback.successful()
			? RuntimeResult.changed(resumed.message())
			: callback;
	}

	public static RuntimeResult extendIntermission(
		final MinecraftServer server,
		final long additionalTicks
	) {
		return applySimpleAuthority(
			server,
			current -> TimelineAuthority.extendIntermission(
				current,
				context(current),
				additionalTicks
			)
		);
	}

	public static RuntimeResult finishIntermissionEarly(final MinecraftServer server) {
		return applySimpleAuthority(
			server,
			current -> TimelineAuthority.finishIntermissionEarly(current, context(current))
		);
	}

	/**
	 * Freezes one explicitly opted-in result while a timed task is waiting for a result.
	 *
	 * <p>This is deliberately narrower than the data-pack result command: a host cannot use it to
	 * finish a running task, replace a frozen result, or bypass a failed timeout callback.
	 */
	public static RuntimeResult hostFallbackResult(
		final MinecraftServer server,
		final String resultId
	) {
		Objects.requireNonNull(resultId, "resultId");
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		TimelineInstance current = resolved.timeline().orElseThrow();
		TaskDefinition definition = resolved.definition().orElseThrow();
		TaskResult result = definition.results().get(resultId);
		if (!hostFallbackEligible(current, result)) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"host fallback is unavailable for the requested result"
			);
		}
		TimelineAuthority.OperationResult submitted = TimelineAuthority.submitResult(
			current,
			context(current),
			definition,
			resultId
		);
		if (!submitted.successful()) {
			return RuntimeResult.failed(submitted.code(), submitted.message());
		}
		if (!submitted.changed()) {
			return RuntimeResult.unchanged(submitted.message());
		}
		return commitLifecycle(
			server,
			resolved.root().orElseThrow(),
			current,
			submitted.nextState().orElseThrow()
		);
	}

	static boolean hostFallbackEligible(
		final TimelineInstance timeline,
		final TaskResult result
	) {
		if (timeline.currentTask().isEmpty()) {
			return false;
		}
		TaskInstance task = timeline.currentTask().orElseThrow();
		return timeline.status() == TimelineStatus.SETTLING
			&& task.status() == TaskStatus.SETTLING
			&& task.result().isEmpty()
			&& result != null
			&& result.allowHostFallback();
	}

	public static RuntimeResult interrupt(
		final MinecraftServer server,
		final long serverTick
	) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return RuntimeResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline is not active");
		}
		TimelineInstance current = root.timeline().orElseThrow();
		if (
			current.status() == TimelineStatus.COMPLETED
				|| current.status() == TimelineStatus.INTERRUPTED
				|| current.currentTask().isEmpty()
		) {
			return RuntimeResult.failed(OperationCode.TIMELINE_COMPLETED, "timeline is already terminal");
		}
		TimelineInstance withoutTrigger = withPhaseTransitionTrigger(
			withLifecycleTrigger(current, Optional.empty()),
			Optional.empty()
		);
		TimelineAuthority.OperationResult interrupted = TimelineAuthority.interrupt(
			withoutTrigger,
			context(withoutTrigger),
			serverTick
		);
		if (!interrupted.successful()) {
			return RuntimeResult.failed(interrupted.code(), interrupted.message());
		}
		return commitLifecycle(
			server,
			root,
			current,
			interrupted.nextState().orElseThrow()
		);
	}

	/**
	 * Explicitly retries one failed or outcome-unknown current-task callback.
	 */
	public static RuntimeResult retryCurrentCallback(
		final MinecraftServer server,
		final String stepKey,
		final Optional<UUID> playerId
	) {
		Objects.requireNonNull(stepKey, "stepKey");
		Objects.requireNonNull(playerId, "playerId");
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		if (timeline.status() != TimelineStatus.BLOCKED) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"callback retry is only available while the timeline is blocked"
			);
		}
		List<CallbackSpec> required = requiredCallbacks(
			server,
			resolved.root().orElseThrow(),
			resolved.snapshot().orElseThrow(),
			timeline,
			resolved.definition().orElseThrow()
		);
		CallbackSpec target = required.stream()
			.filter(spec -> spec.stepKey().equals(stepKey) && spec.playerId().equals(playerId))
			.findFirst()
			.orElse(null);
		if (target == null) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"callback step is not required by the current blocked state"
			);
		}
		CallbackRun callback = executeCallback(server, target, true);
		if (!callback.successful()) {
			return RuntimeResult.failed(callback.code(), callback.message());
		}
		if (target.lifecycle()) {
			return clearLifecycleTrigger(server, true);
		}
		ResolvedTimeline refreshed = resolve(server);
		if (!refreshed.successful()) {
			return refreshed.asRuntimeResult();
		}
		List<CallbackSpec> stillRequired = requiredCallbacks(
			server,
			refreshed.root().orElseThrow(),
			refreshed.snapshot().orElseThrow(),
			refreshed.timeline().orElseThrow(),
			refreshed.definition().orElseThrow()
		);
		if (!callbacksSucceeded(refreshed.timeline().orElseThrow(), stillRequired)) {
			return RuntimeResult.changed("callback retry succeeded; other required steps remain");
		}
		if (
			refreshed.timeline()
				.orElseThrow()
				.currentTask()
				.orElseThrow()
				.phaseTransitionTrigger()
				.isPresent()
		) {
			return completePhaseTransition(server);
		}
		return unblock(server);
	}

	private static void onServerStarted(final MinecraftServer server) {
		SNAPSHOT_CACHE.remove(server);
		recoverPreparedCallbacks(server);
	}

	private static void onServerTick(final MinecraftServer server) {
		try {
			WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
			if (root == null || root.timeline().isEmpty()) {
				SNAPSHOT_CACHE.remove(server);
				return;
			}
			TimelineInstance timeline = root.timeline().orElseThrow();
			if (
				timeline.status() == TimelineStatus.COMPLETED
					|| timeline.status() == TimelineStatus.INTERRUPTED
			) {
				SNAPSHOT_CACHE.remove(server);
				return;
			}
			if (recoverPreparedCallbacks(server)) {
				return;
			}
			ResolvedTimeline resolved = resolve(server);
			if (!resolved.successful()) {
				blockCurrent(server);
				diagnose(timeline, resolved.message());
				return;
			}
			LAST_DIAGNOSTIC.remove(timeline.instanceId());
			if (applyHostOfflinePolicy(server, resolved)) {
				return;
			}
			drive(server);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.error("Pixel TZZ timeline tick failed safely", error);
		}
	}

	private static boolean applyHostOfflinePolicy(
		final MinecraftServer server,
		final ResolvedTimeline resolved
	) {
		GameDefinition game = resolved.snapshot().orElseThrow()
			.games()
			.get(resolved.timeline().orElseThrow().gameId());
		TaskTimeline configuration = game == null ? null : game.taskTimeline().orElse(null);
		if (configuration == null || !configuration.pauseWhenHostOffline()) {
			return false;
		}
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		boolean hostOnline = resolved.root().orElseThrow()
			.core()
			.host()
			.map(host -> server.getPlayerList().getPlayer(host.playerId()) != null)
			.orElse(false);
		if (
			!hostOnline
				&& !timeline.paused()
				&& (
					timeline.status() == TimelineStatus.RUNNING
						|| timeline.status() == TimelineStatus.INTERMISSION
				)
		) {
			return hostPause(server, HOST_OFFLINE_PAUSE_REASON).changed();
		}
		if (
			hostOnline
				&& timeline.paused()
				&& timeline.pauseReason().filter(HOST_OFFLINE_PAUSE_REASON::equals).isPresent()
		) {
			return hostResume(server).changed();
		}
		return false;
	}

	private static void drive(final MinecraftServer server) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return;
		}
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		TaskInstance task = timeline.currentTask().orElseThrow();
		if (task.phaseTransitionTrigger().isPresent()) {
			RuntimeResult dispatched = dispatchPendingPhaseTransition(server);
			if (!dispatched.successful()) {
				blockCurrent(server);
			}
			return;
		}
		if (task.lifecycleCallbackTrigger().isPresent()) {
			RuntimeResult dispatched = dispatchPendingLifecycleCallback(server);
			if (!dispatched.successful()) {
				blockCurrent(server);
			}
			return;
		}
		switch (timeline.status()) {
			case STARTING -> driveStarting(server, resolved);
			case RUNNING -> driveRunning(server, resolved);
			case SETTLING -> driveSettling(server, resolved);
			case INTERMISSION -> driveIntermission(server, resolved);
			case BLOCKED -> driveBlocked(server, resolved);
			case PRE_START, COMPLETED, INTERRUPTED -> {
			}
		}
	}

	private static void driveStarting(
		final MinecraftServer server,
		final ResolvedTimeline resolved
	) {
		CallbackBatch batch = executeCallbacks(
			server,
			startCallbacks(
				server,
				resolved.root().orElseThrow(),
				resolved.snapshot().orElseThrow(),
				resolved.timeline().orElseThrow(),
				resolved.definition().orElseThrow()
			)
		);
		if (!batch.successful()) {
			blockCurrent(server);
			return;
		}
		ResolvedTimeline refreshed = resolve(server);
		if (!refreshed.successful()) {
			return;
		}
		TimelineInstance current = refreshed.timeline().orElseThrow();
		TimelineAuthority.OperationResult started = TimelineAuthority.startSucceeded(
			current,
			context(current),
			server.getTickCount()
		);
		commitAuthority(server, refreshed, started);
	}

	private static void driveRunning(
		final MinecraftServer server,
		final ResolvedTimeline resolved
	) {
		TimelineInstance current = resolved.timeline().orElseThrow();
		if (current.paused()) {
			return;
		}
		TimelineAuthority.OperationResult ticked = TimelineAuthority.tickRunning(
			current,
			context(current),
			resolved.definition().orElseThrow()
		);
		if (!ticked.successful() || !ticked.changed()) {
			if (!ticked.successful()) {
				diagnose(current, ticked.message());
			}
			return;
		}
		TimelineInstance next = ticked.nextState().orElseThrow();
		if (next.timelineRevision() == current.timelineRevision()) {
			checkpointClock(server, current, next);
		} else {
			commitLifecycle(server, resolved.root().orElseThrow(), current, next);
		}
	}

	private static void driveSettling(
		final MinecraftServer server,
		final ResolvedTimeline resolved
	) {
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		TaskInstance task = timeline.currentTask().orElseThrow();
		List<CallbackSpec> callbacks = task.result().isEmpty()
			? timeoutCallbacks(resolved.definition().orElseThrow())
			: settlementCallbacks(
				server,
				resolved.root().orElseThrow(),
				resolved.snapshot().orElseThrow(),
				timeline,
				resolved.definition().orElseThrow()
			);
		CallbackBatch batch = executeCallbacks(server, callbacks);
		if (!batch.successful()) {
			blockCurrent(server);
			return;
		}
		ResolvedTimeline refreshed = resolve(server);
		if (
			!refreshed.successful()
				|| refreshed.timeline().orElseThrow().currentTask().orElseThrow().result().isEmpty()
		) {
			return;
		}
		TimelineInstance current = refreshed.timeline().orElseThrow();
		TaskDefinition definition = refreshed.definition().orElseThrow();
		TaskResult result = definition.results()
			.get(current.currentTask().orElseThrow().result().orElseThrow().resultId());
		Optional<TaskStart> nextTask = result.route().intermission().isPresent()
			? Optional.empty()
			: routedTaskStart(server, refreshed, result);
		TimelineAuthority.OperationResult settled = TimelineAuthority.settlementSucceeded(
			current,
			context(current),
			definition,
			nextTask,
			server.getTickCount()
		);
		commitAuthority(server, refreshed, settled);
	}

	private static void driveIntermission(
		final MinecraftServer server,
		final ResolvedTimeline resolved
	) {
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		if (timeline.paused()) {
			return;
		}
		TaskInstance task = timeline.currentTask().orElseThrow();
		IntermissionState intermission = task.intermission().orElseThrow();
		List<CallbackSpec> callbacks = intermissionCallbacks(
			resolved.definition().orElseThrow(),
			task,
			intermission.elapsedTicks() >= intermission.durationTicks()
		);
		CallbackBatch batch = executeCallbacks(server, callbacks);
		if (!batch.successful()) {
			blockCurrent(server);
			return;
		}
		ResolvedTimeline refreshed = resolve(server);
		if (!refreshed.successful()) {
			return;
		}
		TimelineInstance current = refreshed.timeline().orElseThrow();
		IntermissionState currentIntermission = current.currentTask()
			.orElseThrow()
			.intermission()
			.orElseThrow();
		if (currentIntermission.elapsedTicks() < currentIntermission.durationTicks()) {
			TimelineAuthority.OperationResult ticked = TimelineAuthority.tickIntermission(
				current,
				context(current)
			);
			if (ticked.successful() && ticked.changed()) {
				checkpointClock(server, current, ticked.nextState().orElseThrow());
			}
			return;
		}
		TaskResult result = currentResult(
			refreshed.definition().orElseThrow(),
			current.currentTask().orElseThrow()
		);
		Optional<TaskStart> nextTask = routedTaskStart(server, refreshed, result);
		TimelineAuthority.OperationResult completed = TimelineAuthority.intermissionSucceeded(
			current,
			context(current),
			nextTask,
			server.getTickCount()
		);
		commitAuthority(server, refreshed, completed);
	}

	private static void driveBlocked(
		final MinecraftServer server,
		final ResolvedTimeline resolved
	) {
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		TaskInstance task = timeline.currentTask().orElseThrow();
		if (task.phaseTransitionTrigger().isPresent()) {
			CallbackPlan phase = phaseCallbacks(
				resolved.snapshot().orElseThrow(),
				task.phaseTransitionTrigger().orElseThrow()
			);
			if (!phase.successful()) {
				diagnose(timeline, phase.message());
				return;
			}
		}
		List<CallbackSpec> required = requiredCallbacks(
			server,
			resolved.root().orElseThrow(),
			resolved.snapshot().orElseThrow(),
			timeline,
			resolved.definition().orElseThrow()
		);
		if (hasFailedOrUnknown(timeline, required)) {
			return;
		}
		CallbackBatch batch = executeCallbacks(server, required);
		if (batch.successful()) {
			if (task.phaseTransitionTrigger().isPresent()) {
				completePhaseTransition(server);
			} else {
				unblock(server);
			}
		}
	}

	private static RuntimeResult dispatchPendingPhaseTransition(final MinecraftServer server) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		TaskInstance task = timeline.currentTask().orElseThrow();
		if (task.phaseTransitionTrigger().isEmpty()) {
			return RuntimeResult.unchanged("no phase transition is pending");
		}
		CallbackPlan plan = phaseCallbacks(
			resolved.snapshot().orElseThrow(),
			task.phaseTransitionTrigger().orElseThrow()
		);
		if (!plan.successful()) {
			return RuntimeResult.failed(plan.code(), plan.message());
		}
		CallbackBatch batch = executeCallbacks(server, plan.callbacks());
		if (!batch.successful()) {
			return RuntimeResult.failed(batch.code(), batch.message());
		}
		return completePhaseTransition(server);
	}

	private static RuntimeResult completePhaseTransition(final MinecraftServer server) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		WorldStateV3 root = resolved.root().orElseThrow();
		TimelineInstance current = resolved.timeline().orElseThrow();
		TaskInstance task = current.currentTask().orElseThrow();
		PhaseTransitionTrigger trigger = task.phaseTransitionTrigger().orElse(null);
		if (trigger == null) {
			return RuntimeResult.unchanged("phase transition already completed");
		}
		CallbackPlan plan = phaseCallbacks(resolved.snapshot().orElseThrow(), trigger);
		if (!plan.successful()) {
			return RuntimeResult.failed(plan.code(), plan.message());
		}
		if (!callbacksSucceeded(current, plan.callbacks())) {
			return RuntimeResult.failed(
				OperationCode.CALLBACK_FAILED,
				"phase transition callbacks are not all successful"
			);
		}
		if (root.core().activePhaseId().filter(trigger.fromPhaseId()::equals).isEmpty()) {
			return RuntimeResult.failed(
				OperationCode.PHASE_MISMATCH,
				"active phase changed while the phase transition was pending"
			);
		}

		TimelineInstance business = current;
		if (business.status() == TimelineStatus.BLOCKED) {
			TimelineAuthority.OperationResult unblocked = TimelineAuthority.unblock(
				business,
				context(business),
				true
			);
			if (!unblocked.successful()) {
				return RuntimeResult.failed(unblocked.code(), unblocked.message());
			}
			business = unblocked.nextState().orElseThrow();
		}
		business = withPhaseTransitionTrigger(business, Optional.empty());

		TimelineInstance next = null;
		switch (trigger.cause()) {
			case APPROVAL -> next = current.status() == TimelineStatus.BLOCKED
				? business
				: incrementTimelineRevision(business);
			case SETTLEMENT -> {
				TaskResult result = currentResult(
					resolved.definition().orElseThrow(),
					business.currentTask().orElseThrow()
				);
				Optional<TaskStart> nextTask = result.route().intermission().isPresent()
					? Optional.empty()
					: routedTaskStart(server, resolved, result);
				TimelineAuthority.OperationResult settled = TimelineAuthority.settlementSucceeded(
					business,
					context(business),
					resolved.definition().orElseThrow(),
					nextTask,
					server.getTickCount()
				);
				if (!settled.successful()) {
					return RuntimeResult.failed(settled.code(), settled.message());
				}
				next = settled.nextState().orElseThrow();
			}
			case INTERMISSION -> {
				TaskResult result = currentResult(
					resolved.definition().orElseThrow(),
					business.currentTask().orElseThrow()
				);
				TimelineAuthority.OperationResult completed = TimelineAuthority.intermissionSucceeded(
					business,
					context(business),
					routedTaskStart(server, resolved, result),
					server.getTickCount()
				);
				if (!completed.successful()) {
					return RuntimeResult.failed(completed.code(), completed.message());
				}
				next = completed.nextState().orElseThrow();
			}
		}
		return commitLifecycle(
			server,
			root,
			current,
			Objects.requireNonNull(next, "phase transition did not produce a timeline"),
			Optional.of(trigger.toPhaseId())
		);
	}

	private static RuntimeResult dispatchPendingLifecycleCallback(final MinecraftServer server) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return resolved.asRuntimeResult();
		}
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		TaskInstance task = timeline.currentTask().orElseThrow();
		if (task.lifecycleCallbackTrigger().isEmpty()) {
			return RuntimeResult.unchanged("no lifecycle callback is pending");
		}
		CallbackSpec callback = lifecycleCallback(
			resolved.definition().orElseThrow(),
			task,
			task.lifecycleCallbackTrigger().orElseThrow()
		);
		if (callback == null) {
			return RuntimeResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"pending lifecycle callback is absent from the frozen task"
			);
		}
		CallbackBatch batch = executeCallbacks(server, List.of(callback));
		if (!batch.successful()) {
			blockCurrent(server);
			return RuntimeResult.failed(batch.code(), batch.message());
		}
		return clearLifecycleTrigger(server, timeline.status() == TimelineStatus.BLOCKED);
	}

	private static RuntimeResult clearLifecycleTrigger(
		final MinecraftServer server,
		final boolean unblock
	) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return RuntimeResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline is not active");
		}
		TimelineInstance current = root.timeline().orElseThrow();
		if (current.currentTask().orElseThrow().lifecycleCallbackTrigger().isEmpty()) {
			return unblock ? unblock(server) : RuntimeResult.unchanged("lifecycle callback already cleared");
		}
		TimelineInstance cleared = withLifecycleTrigger(current, Optional.empty());
		if (unblock && cleared.status() == TimelineStatus.BLOCKED) {
			TimelineAuthority.OperationResult result = TimelineAuthority.unblock(
				cleared,
				context(cleared),
				true
			);
			if (!result.successful()) {
				return RuntimeResult.failed(result.code(), result.message());
			}
			cleared = result.nextState().orElseThrow();
		} else {
			cleared = incrementTimelineRevision(cleared);
		}
		return commitLifecycle(server, root, current, cleared);
	}

	private static RuntimeResult unblock(final MinecraftServer server) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return RuntimeResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline is not active");
		}
		TimelineInstance current = root.timeline().orElseThrow();
		if (current.status() != TimelineStatus.BLOCKED) {
			return RuntimeResult.unchanged("timeline is not blocked");
		}
		TimelineAuthority.OperationResult result = TimelineAuthority.unblock(
			current,
			context(current),
			true
		);
		if (!result.successful()) {
			return RuntimeResult.failed(result.code(), result.message());
		}
		return commitLifecycle(server, root, current, result.nextState().orElseThrow());
	}

	private static RuntimeResult blockCurrent(final MinecraftServer server) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return RuntimeResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline is not active");
		}
		TimelineInstance current = root.timeline().orElseThrow();
		if (
			current.status() == TimelineStatus.BLOCKED
				|| current.status() == TimelineStatus.COMPLETED
				|| current.status() == TimelineStatus.INTERRUPTED
		) {
			return RuntimeResult.unchanged("timeline is already blocked or terminal");
		}
		TimelineAuthority.OperationResult result = TimelineAuthority.block(current, context(current));
		if (!result.successful()) {
			return RuntimeResult.failed(result.code(), result.message());
		}
		return commitLifecycle(server, root, current, result.nextState().orElseThrow());
	}

	private static boolean recoverPreparedCallbacks(final MinecraftServer server) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return false;
		}
		TimelineInstance current = root.timeline().orElseThrow();
		long gameTick = current.gameElapsedTicks();
		MutationResult timelineLedger = TaskCallbackLedgerAuthority.markPreparedUnknown(
			current.callbackLedger(),
			gameTick,
			RECOVERY_REASON
		);
		TaskInstance task = current.currentTask().orElse(null);
		MutationResult taskLedger = task == null
			? new MutationResult(OperationCode.SUCCESS, "no current task", Optional.empty())
			: TaskCallbackLedgerAuthority.markPreparedUnknown(
				task.callbackLedger(),
				gameTick,
				RECOVERY_REASON
			);
		if (!timelineLedger.changed() && !taskLedger.changed()) {
			return false;
		}
		TimelineInstance recovered = current;
		if (timelineLedger.changed()) {
			recovered = withTimelineLedger(
				recovered,
				timelineLedger.nextLedger().orElseThrow()
			);
		}
		if (taskLedger.changed()) {
			recovered = withTaskLedger(recovered, taskLedger.nextLedger().orElseThrow());
		}
		if (
			recovered.status() != TimelineStatus.BLOCKED
				&& recovered.status() != TimelineStatus.COMPLETED
				&& recovered.status() != TimelineStatus.INTERRUPTED
		) {
			TimelineAuthority.OperationResult blocked = TimelineAuthority.block(
				recovered,
				context(recovered)
			);
			if (!blocked.successful()) {
				return false;
			}
			recovered = blocked.nextState().orElseThrow();
		} else {
			recovered = incrementTimelineRevision(recovered);
		}
		return commitLifecycle(server, root, current, recovered).successful();
	}

	private static CallbackBatch executeCallbacks(
		final MinecraftServer server,
		final List<CallbackSpec> callbacks
	) {
		for (CallbackSpec callback : callbacks) {
			CallbackRun result = executeCallback(server, callback, false);
			if (!result.successful()) {
				return new CallbackBatch(result.code(), result.message());
			}
		}
		return new CallbackBatch(OperationCode.SUCCESS, "required callbacks succeeded");
	}

	private static CallbackRun executeCallback(
		final MinecraftServer server,
		final CallbackSpec callback,
		final boolean retry
	) {
		ResolvedTimeline resolved = resolve(server);
		if (!resolved.successful()) {
			return CallbackRun.failed(resolved.code(), resolved.message());
		}
		TimelineInstance timeline = resolved.timeline().orElseThrow();
		TaskInstance task = timeline.currentTask().orElseThrow();
		List<CallbackStep> ledger = callbackLedger(timeline, callback);
		CallbackStep existing = callbackStep(ledger, callback);
		if (!retry && existing != null) {
			if (
				existing.status() == CallbackStepStatus.SUCCEEDED
					&& existing.functionId().equals(callback.functionId())
			) {
				return CallbackRun.succeeded(false);
			}
			return CallbackRun.failed(
				OperationCode.CALLBACK_FAILED,
				"callback step requires explicit recovery"
			);
		}
		Optional<ServerPlayer> sourcePlayer = callback.playerId()
			.map(server.getPlayerList()::getPlayer);
		if (callback.playerId().isPresent() && sourcePlayer.isEmpty()) {
			return CallbackRun.failed(
				OperationCode.TARGET_OFFLINE,
				"callback target is offline and remains pending"
			);
		}
		ContextResolution context = callbackContext(
			resolved.root().orElseThrow(),
			resolved.snapshot().orElseThrow(),
			timeline,
			task,
			callback,
			sourcePlayer
		);
		StepIdentity identity = new StepIdentity(
			callback.stepKey(),
			callback.functionId(),
			callback.playerId()
		);
		PrepareResult prepared = retry
			? TaskCallbackLedgerAuthority.prepareRetry(
				ledger,
				identity,
				timeline.gameElapsedTicks()
			)
			: TaskCallbackLedgerAuthority.prepareAutomatic(
				ledger,
				identity,
				timeline.gameElapsedTicks()
			);
		if (prepared.code() != OperationCode.SUCCESS) {
			return CallbackRun.failed(prepared.code(), prepared.message());
		}
		if (!prepared.prepared()) {
			return CallbackRun.succeeded(false);
		}
		Optional<WorldStateV3> marked = commitCallbackLedger(
			server,
			resolved.root().orElseThrow(),
			timeline,
			callback,
			prepared.nextLedger().orElseThrow()
		);
		if (marked.isEmpty()) {
			return CallbackRun.failed(
				OperationCode.REVISION_MISMATCH,
				"callback prepared marker could not be committed"
			);
		}

		boolean succeeded;
		Optional<String> error;
		if (!context.successful()) {
			succeeded = false;
			error = Optional.of(context.message());
		} else {
			FlowCallbackRunner.Invocation invocation = TaskCallbackRunner.invoke(
				server,
				callback.functionId(),
				context.context().orElseThrow(),
				sourcePlayer
			);
			succeeded = invocation.success();
			error = invocation.success() ? Optional.empty() : Optional.of(invocation.error());
		}

		WorldStateV3 latestRoot = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (latestRoot == null || latestRoot.timeline().isEmpty()) {
			return CallbackRun.failed(
				OperationCode.STATE_REVISION_STALE,
				"timeline disappeared while callback outcome was being recorded"
			);
		}
		TimelineInstance latestTimeline = latestRoot.timeline().orElseThrow();
		if (
			latestTimeline.currentTask().isEmpty()
				|| !latestTimeline.instanceId().equals(timeline.instanceId())
				|| !latestTimeline.currentTask()
					.orElseThrow()
					.taskInstanceId()
					.equals(task.taskInstanceId())
		) {
			return CallbackRun.failed(
				OperationCode.STATE_REVISION_STALE,
				"current task changed while callback outcome was being recorded"
			);
		}
		PreparedInvocation invocation = prepared.invocation().orElseThrow();
		MutationResult outcome = TaskCallbackLedgerAuthority.recordOutcome(
			callbackLedger(latestTimeline, callback),
			invocation,
			succeeded,
			error,
			latestTimeline.gameElapsedTicks()
		);
		if (!outcome.changed()) {
			return CallbackRun.failed(outcome.code(), outcome.message());
		}
		Optional<WorldStateV3> committed = commitCallbackLedger(
			server,
			latestRoot,
			latestTimeline,
			callback,
			outcome.nextLedger().orElseThrow()
		);
		if (committed.isEmpty()) {
			recoverPreparedCallbacks(server);
			return CallbackRun.failed(
				OperationCode.STATE_REVISION_STALE,
				"callback outcome could not be committed"
			);
		}
		return succeeded
			? CallbackRun.succeeded(true)
			: CallbackRun.failed(OperationCode.CALLBACK_FAILED, error.orElseThrow());
	}

	private static ContextResolution callbackContext(
		final WorldStateV3 root,
		final DefinitionSnapshot snapshot,
		final TimelineInstance timeline,
		final TaskInstance task,
		final CallbackSpec callback,
		final Optional<ServerPlayer> sourcePlayer
	) {
		Optional<String> resultId = task.result().map(FrozenResult::resultId);
		if (callback.playerId().isEmpty()) {
			return ContextResolution.success(
				new TaskCallbackRunner.Context(
					timeline.gameId(),
					timeline.instanceId(),
					task.taskId(),
					task.taskInstanceId(),
					resultId,
					timeline.gameElapsedTicks(),
					task.elapsedTicks(),
					Optional.empty(),
					Optional.empty(),
					Map.of()
				)
			);
		}
		UUID playerId = callback.playerId().orElseThrow();
		PlayerRecord player = root.core().players().get(playerId);
		if (player == null || sourcePlayer.isEmpty()) {
			return ContextResolution.failed("callback player record or online command source is missing");
		}
		Map<String, StoredValue> fields = new LinkedHashMap<>();
		for (Map.Entry<String, Identifier> binding : callback.fields().entrySet()) {
			FieldDefinition definition = snapshot.fields().get(binding.getValue());
			PersistentFieldValue value = definition == null
				? null
				: player.persistentFields().get(definition.id());
			if (
				definition == null
					|| definition.scope() != FieldScope.PLAYER
					|| !definition.game().equals(timeline.gameId())
					|| !TimelineApprovalAuthority.validCurrentValue(definition, value)
			) {
				return ContextResolution.failed(
					"callback field " + binding.getValue() + " is missing or invalid"
				);
			}
			if (
				definition.type() == FieldType.EXCLUSIVE_CHOICE
					&& (
						root.activeGameInstanceId().isEmpty()
							|| !TimelineApprovalAuthority.hasMatchingLock(
								root,
								definition,
								root.activeGameInstanceId().orElseThrow(),
								player,
								value
							)
					)
			) {
				return ContextResolution.failed(
					"callback exclusive field " + binding.getValue() + " is not locked"
				);
			}
			fields.put(binding.getKey(), value.value());
		}
		return ContextResolution.success(
			new TaskCallbackRunner.Context(
				timeline.gameId(),
				timeline.instanceId(),
				task.taskId(),
				task.taskInstanceId(),
				resultId,
				timeline.gameElapsedTicks(),
				task.elapsedTicks(),
				Optional.of(playerId),
				Optional.of(sourcePlayer.orElseThrow().getPlainTextName()),
				fields
			)
		);
	}

	private static List<CallbackSpec> requiredCallbacks(
		final MinecraftServer server,
		final WorldStateV3 root,
		final DefinitionSnapshot snapshot,
		final TimelineInstance timeline,
		final TaskDefinition definition
	) {
		TaskInstance task = timeline.currentTask().orElseThrow();
		if (task.phaseTransitionTrigger().isPresent()) {
			CallbackPlan phase = phaseCallbacks(
				snapshot,
				task.phaseTransitionTrigger().orElseThrow()
			);
			return phase.successful() ? phase.callbacks() : List.of();
		}
		if (task.lifecycleCallbackTrigger().isPresent()) {
			CallbackSpec lifecycle = lifecycleCallback(
				definition,
				task,
				task.lifecycleCallbackTrigger().orElseThrow()
			);
			return lifecycle == null ? List.of() : List.of(lifecycle);
		}
		TimelineStatus status = timeline.status() == TimelineStatus.BLOCKED
			? timeline.resumeStatus().orElseThrow()
			: timeline.status();
		return switch (status) {
			case STARTING -> startCallbacks(server, root, snapshot, timeline, definition);
			case SETTLING -> task.result().isEmpty()
				? timeoutCallbacks(definition)
				: settlementCallbacks(server, root, snapshot, timeline, definition);
			case INTERMISSION -> {
				IntermissionState intermission = task.intermission().orElseThrow();
				yield intermissionCallbacks(
					definition,
					task,
					intermission.elapsedTicks() >= intermission.durationTicks()
				);
			}
			case PRE_START, RUNNING, COMPLETED, INTERRUPTED, BLOCKED -> List.of();
		};
	}

	private static List<CallbackSpec> startCallbacks(
		final MinecraftServer server,
		final WorldStateV3 root,
		final DefinitionSnapshot snapshot,
		final TimelineInstance timeline,
		final TaskDefinition definition
	) {
		List<CallbackSpec> callbacks = new ArrayList<>();
		definition.callbacks()
			.onStart()
			.ifPresent(function -> callbacks.add(CallbackSpec.global("task/on_start", function)));
		definition.onStartPlayers()
			.ifPresent(playerCallback ->
				callbacks.addAll(
					playerCallbacks(
						server,
						root,
						snapshot,
						timeline.currentTask().orElseThrow(),
						definition,
						"task/on_start_players",
						playerCallback
					)
				)
			);
		return List.copyOf(callbacks);
	}

	private static List<CallbackSpec> timeoutCallbacks(final TaskDefinition definition) {
		return definition.callbacks()
			.onTimeout()
			.map(function -> List.of(CallbackSpec.global("task/on_timeout", function)))
			.orElse(List.of());
	}

	private static List<CallbackSpec> settlementCallbacks(
		final MinecraftServer server,
		final WorldStateV3 root,
		final DefinitionSnapshot snapshot,
		final TimelineInstance timeline,
		final TaskDefinition definition
	) {
		TaskInstance task = timeline.currentTask().orElseThrow();
		TaskResult result = currentResult(definition, task);
		List<CallbackSpec> callbacks = new ArrayList<>();
		String resultKey = "result/" + result.id();
		result.onApply()
			.ifPresent(function -> callbacks.add(CallbackSpec.global(resultKey + "/on_apply", function)));
		result.onApplyPlayers()
			.ifPresent(playerCallback ->
				callbacks.addAll(
					playerCallbacks(
						server,
						root,
						snapshot,
						task,
						definition,
						resultKey + "/on_apply_players",
						playerCallback
					)
				)
			);
		definition.callbacks()
			.onSettled()
			.ifPresent(function -> callbacks.add(CallbackSpec.global("task/on_settled", function)));
		return List.copyOf(callbacks);
	}

	private static List<CallbackSpec> intermissionCallbacks(
		final TaskDefinition definition,
		final TaskInstance task,
		final boolean completionDue
	) {
		TaskResult result = currentResult(definition, task);
		IntermissionDefinition intermission = result.route().intermission().orElseThrow();
		List<CallbackSpec> callbacks = new ArrayList<>();
		String key = "intermission/" + result.id();
		intermission.onStart()
			.ifPresent(function -> callbacks.add(CallbackSpec.global(key + "/on_start", function)));
		if (completionDue) {
			intermission.onComplete()
				.ifPresent(function -> callbacks.add(CallbackSpec.global(key + "/on_complete", function)));
		}
		return List.copyOf(callbacks);
	}

	private static List<CallbackSpec> playerCallbacks(
		final MinecraftServer server,
		final WorldStateV3 root,
		final DefinitionSnapshot snapshot,
		final TaskInstance task,
		final TaskDefinition definition,
		final String stepKey,
		final PlayerCallback callback
	) {
		Optional<UUID> hostId = root.core().host().map(host -> host.playerId());
		return task.participants()
			.stream()
			.filter(playerId -> {
				PlayerRecord player = root.core().players().get(playerId);
				if (player == null || callback.audience().isEmpty()) {
					return true;
				}
				return AudienceMatcher.matches(
					snapshot,
					callback.audience().orElse(definition.audience()),
					player,
					hostId,
					server.getPlayerList().getPlayer(playerId) != null
				);
			})
			.sorted()
			.map(playerId ->
				CallbackSpec.player(stepKey, callback.function(), playerId, callback.fields())
			)
			.toList();
	}

	private static CallbackPlan phaseCallbacks(
		final DefinitionSnapshot snapshot,
		final PhaseTransitionTrigger trigger
	) {
		PhaseDefinition from = snapshot.phases().get(trigger.fromPhaseId());
		PhaseDefinition to = snapshot.phases().get(trigger.toPhaseId());
		if (
			from == null
				|| to == null
				|| !from.game().equals(to.game())
		) {
			return CallbackPlan.failed(
				OperationCode.SNAPSHOT_INVALID,
				"pending phase transition is absent from the frozen snapshot"
			);
		}
		List<CallbackSpec> callbacks = new ArrayList<>(2);
		from.onExit()
			.ifPresent(function ->
				callbacks.add(
					CallbackSpec.phase(phaseCallbackStepKey(trigger, "on_exit"), function)
				)
			);
		to.onEnter()
			.ifPresent(function ->
				callbacks.add(
					CallbackSpec.phase(phaseCallbackStepKey(trigger, "on_enter"), function)
				)
			);
		return CallbackPlan.success(callbacks);
	}

	static String phaseCallbackStepKey(
		final PhaseTransitionTrigger trigger,
		final String callback
	) {
		return "phase/" + trigger.transitionId() + "/" + callback;
	}

	private static CallbackSpec lifecycleCallback(
		final TaskDefinition definition,
		final TaskInstance task,
		final LifecycleCallbackTrigger trigger
	) {
		return lifecycleFunction(definition, task, trigger.kind()).map(value ->
			CallbackSpec.lifecycle(
				"lifecycle/"
					+ trigger.kind().getSerializedName()
					+ "/"
					+ trigger.triggerRevision(),
				value
			)
		).orElse(null);
	}

	static Optional<Identifier> lifecycleFunction(
		final TaskDefinition definition,
		final TaskInstance task,
		final LifecycleCallbackKind kind
	) {
		return switch (kind) {
			case TASK_PAUSE -> definition.callbacks().onPause();
			case TASK_RESUME -> definition.callbacks().onResume();
			case INTERMISSION_PAUSE -> currentIntermission(definition, task).flatMap(
				IntermissionDefinition::onPause
			);
			case INTERMISSION_RESUME -> currentIntermission(definition, task).flatMap(
				IntermissionDefinition::onResume
			);
		};
	}

	private static Optional<LifecycleCallbackKind> pauseCallbackKind(
		final TaskDefinition definition,
		final TimelineInstance timeline
	) {
		if (timeline.status() == TimelineStatus.RUNNING) {
			return definition.callbacks().onPause().map(ignored -> LifecycleCallbackKind.TASK_PAUSE);
		}
		return currentIntermission(definition, timeline.currentTask().orElseThrow())
			.flatMap(IntermissionDefinition::onPause)
			.map(ignored -> LifecycleCallbackKind.INTERMISSION_PAUSE);
	}

	private static Optional<LifecycleCallbackKind> resumeCallbackKind(
		final TaskDefinition definition,
		final TimelineInstance timeline
	) {
		if (timeline.status() == TimelineStatus.RUNNING) {
			return definition.callbacks().onResume().map(ignored -> LifecycleCallbackKind.TASK_RESUME);
		}
		return currentIntermission(definition, timeline.currentTask().orElseThrow())
			.flatMap(IntermissionDefinition::onResume)
			.map(ignored -> LifecycleCallbackKind.INTERMISSION_RESUME);
	}

	private static Optional<IntermissionDefinition> currentIntermission(
		final TaskDefinition definition,
		final TaskInstance task
	) {
		if (task.result().isEmpty()) {
			return Optional.empty();
		}
		TaskResult result = definition.results().get(task.result().orElseThrow().resultId());
		return result == null ? Optional.empty() : result.route().intermission();
	}

	private static Optional<TaskStart> routedTaskStart(
		final MinecraftServer server,
		final ResolvedTimeline resolved,
		final TaskResult result
	) {
		if (result.route().nextTask().isEmpty()) {
			return Optional.empty();
		}
		DefinitionSnapshot snapshot = resolved.snapshot().orElseThrow();
		TaskDefinition next = snapshot.tasks().get(result.route().nextTask().orElseThrow());
		if (next == null) {
			return Optional.empty();
		}
		WorldStateV3 root = resolved.root().orElseThrow();
		Set<UUID> online = server.getPlayerList()
			.getPlayers()
			.stream()
			.map(ServerPlayer::getUUID)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		List<UUID> participants = fixedTimelineParticipants(resolved.timeline().orElseThrow())
			.stream()
			.map(root.core().players()::get)
			.filter(Objects::nonNull)
			.filter(player ->
				AudienceMatcher.matches(
					snapshot,
					next.audience(),
					player,
					root.core().host().map(host -> host.playerId()),
					online.contains(player.playerId())
				)
			)
			.map(PlayerRecord::playerId)
			.sorted()
			.toList();
		return Optional.of(new TaskStart(UUID.randomUUID(), next, participants));
	}

	static List<UUID> fixedTimelineParticipants(final TimelineInstance timeline) {
		if (!timeline.taskHistory().isEmpty()) {
			return timeline.taskHistory().getFirst().participants();
		}
		return timeline.currentTask().map(TaskInstance::participants).orElse(List.of());
	}

	private static TaskResult currentResult(
		final TaskDefinition definition,
		final TaskInstance task
	) {
		String resultId = task.result().orElseThrow().resultId();
		TaskResult result = definition.results().get(resultId);
		if (result == null) {
			throw new IllegalStateException("frozen task result is not registered");
		}
		return result;
	}

	private static RuntimeResult applySimpleAuthority(
		final MinecraftServer server,
		final java.util.function.Function<TimelineInstance, TimelineAuthority.OperationResult> operation
	) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return RuntimeResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline is not active");
		}
		TimelineInstance current = root.timeline().orElseThrow();
		if (current.currentTask().isEmpty()) {
			return RuntimeResult.failed(OperationCode.TIMELINE_COMPLETED, "timeline is terminal");
		}
		if (current.currentTask().orElseThrow().phaseTransitionTrigger().isPresent()) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"a phase transition is already pending"
			);
		}
		TimelineAuthority.OperationResult result = operation.apply(current);
		if (!result.successful()) {
			return RuntimeResult.failed(result.code(), result.message());
		}
		if (!result.changed()) {
			return RuntimeResult.unchanged(result.message());
		}
		return commitLifecycle(server, root, current, result.nextState().orElseThrow());
	}

	private static void commitAuthority(
		final MinecraftServer server,
		final ResolvedTimeline resolved,
		final TimelineAuthority.OperationResult result
	) {
		if (!result.successful() || !result.changed()) {
			if (!result.successful()) {
				diagnose(resolved.timeline().orElseThrow(), result.message());
			}
			return;
		}
		TimelineInstance current = resolved.timeline().orElseThrow();
		TimelineInstance next = result.nextState().orElseThrow();
		Optional<Identifier> phase = phaseAfterTransition(current, next);
		if (
			phase.isPresent()
				&& resolved.root().orElseThrow()
					.core()
					.activePhaseId()
					.filter(phase.orElseThrow()::equals)
					.isEmpty()
		) {
			PhaseTransitionCause cause = switch (current.status()) {
				case SETTLING -> PhaseTransitionCause.SETTLEMENT;
				case INTERMISSION -> PhaseTransitionCause.INTERMISSION;
				default -> null;
			};
			if (cause == null) {
				diagnose(current, "unsupported phase-transition source state");
				blockCurrent(server);
				return;
			}
			RuntimeResult staged = stagePhaseTransition(
				server,
				resolved,
				phase.orElseThrow(),
				cause
			);
			if (!staged.successful()) {
				diagnose(current, staged.message());
				blockCurrent(server);
			}
			return;
		}
		commitLifecycle(
			server,
			resolved.root().orElseThrow(),
			current,
			next
		);
	}

	private static RuntimeResult stagePhaseTransition(
		final MinecraftServer server,
		final ResolvedTimeline resolved,
		final Identifier targetPhase,
		final PhaseTransitionCause cause
	) {
		WorldStateV3 root = resolved.root().orElseThrow();
		TimelineInstance current = resolved.timeline().orElseThrow();
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			task.phaseTransitionTrigger().isPresent()
				|| task.lifecycleCallbackTrigger().isPresent()
		) {
			return RuntimeResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"another persisted transition callback is already pending"
			);
		}
		Identifier fromPhase = root.core().activePhaseId().orElse(null);
		if (fromPhase == null) {
			return RuntimeResult.failed(OperationCode.PHASE_MISMATCH, "active phase is missing");
		}
		if (fromPhase.equals(targetPhase)) {
			return RuntimeResult.unchanged("phase is already active");
		}
		PhaseDefinition from = resolved.snapshot().orElseThrow().phases().get(fromPhase);
		PhaseDefinition to = resolved.snapshot().orElseThrow().phases().get(targetPhase);
		if (
			from == null
				|| to == null
				|| !from.game().equals(current.gameId())
				|| !to.game().equals(current.gameId())
		) {
			return RuntimeResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"phase transition is absent from the frozen snapshot"
			);
		}
		if (!from.transitions().contains(targetPhase)) {
			return RuntimeResult.failed(
				OperationCode.PHASE_MISMATCH,
				"frozen phase graph does not allow this transition"
			);
		}
		final long triggerRevision;
		try {
			triggerRevision = Math.incrementExact(current.timelineRevision());
		} catch (ArithmeticException error) {
			return RuntimeResult.failed(
				OperationCode.RESOURCE_BLOCKED,
				"timeline revision capacity is exhausted"
			);
		}
		TimelineInstance staged = withPhaseTransitionTrigger(
			current,
			Optional.of(
				new PhaseTransitionTrigger(
					UUID.randomUUID(),
					fromPhase,
					targetPhase,
					cause,
					triggerRevision
				)
			)
		);
		staged = incrementTimelineRevision(staged);
		return commitLifecycle(server, root, current, staged);
	}

	private static RuntimeResult commitLifecycle(
		final MinecraftServer server,
		final WorldStateV3 expectedRoot,
		final TimelineInstance expectedTimeline,
		final TimelineInstance nextTimeline
	) {
		return commitLifecycle(
			server,
			expectedRoot,
			expectedTimeline,
			nextTimeline,
			Optional.empty()
		);
	}

	private static RuntimeResult commitLifecycle(
		final MinecraftServer server,
		final WorldStateV3 expectedRoot,
		final TimelineInstance expectedTimeline,
		final TimelineInstance nextTimeline,
		final Optional<Identifier> phase
	) {
		PixelTzzWorldState.CommitV3Result committed = PixelTzzWorldState.get(server)
			.commitV3(
				expectedRoot.stateRevision(),
				current -> {
					TimelineInstance actual = current.timeline().orElseThrow(
						() -> new IllegalStateException("timeline disappeared")
					);
					if (
						!actual.instanceId().equals(expectedTimeline.instanceId())
							|| actual.timelineRevision() != expectedTimeline.timelineRevision()
					) {
						throw new IllegalStateException("timeline changed");
					}
					WorldStateV3 next = current.withTimeline(Optional.of(nextTimeline));
					if (phase.isPresent()) {
						next = next.withCore(
							next.core().withActivity(nextTimeline.gameId(), phase.orElseThrow())
						);
					}
					return next;
				}
			);
		if (!committed.committed()) {
			return RuntimeResult.failed(OperationCode.REVISION_MISMATCH, committed.reason());
		}
		PixelTzzServerRuntime.timelineStateChanged(server);
		sendLifecycleSubtitle(server, expectedTimeline, nextTimeline);
		if (
			nextTimeline.status() == TimelineStatus.COMPLETED
				|| nextTimeline.status() == TimelineStatus.INTERRUPTED
		) {
			SNAPSHOT_CACHE.remove(server);
		}
		return RuntimeResult.changed("timeline lifecycle committed");
	}

	private static void sendLifecycleSubtitle(
		final MinecraftServer server,
		final TimelineInstance before,
		final TimelineInstance after
	) {
		TaskInstance task = before.currentTask().orElse(null);
		if (task == null) {
			return;
		}
		String name = frozenTaskName(server, before, task.taskId())
			.orElse(task.taskId().getPath());
		Optional<String> subtitle = lifecycleSubtitle(before.status(), after.status(), name);
		if (subtitle.isEmpty()) {
			return;
		}
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		UUID hostId = root == null
			? null
			: root.core().host().map(value -> value.playerId()).orElse(null);
		ServerPlayer host = hostId == null ? null : server.getPlayerList().getPlayer(hostId);
		if (host == null || !ServerPlayNetworking.canSend(host, HostSubtitleS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(
			host,
			new HostSubtitleS2CPayload(
				Integer.toUnsignedLong(server.getTickCount()),
				subtitle.orElseThrow()
			)
		);
	}

	static Optional<String> lifecycleSubtitle(
		final TimelineStatus before,
		final TimelineStatus after,
		final String taskName
	) {
		Objects.requireNonNull(before, "before");
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(taskName, "taskName");
		String prefix;
		if (before == TimelineStatus.STARTING && after == TimelineStatus.RUNNING) {
			prefix = "任务开始";
		} else if (
			before == TimelineStatus.SETTLING
				&& (
					after == TimelineStatus.INTERMISSION
						|| after == TimelineStatus.STARTING
						|| after == TimelineStatus.COMPLETED
				)
		) {
			prefix = "任务结束";
		} else if (after == TimelineStatus.INTERRUPTED && before != TimelineStatus.INTERRUPTED) {
			prefix = "任务已终止";
		} else {
			return Optional.empty();
		}
		return Optional.of(prefix + " · " + taskName);
	}

	private static Optional<String> frozenTaskName(
		final MinecraftServer server,
		final TimelineInstance timeline,
		final Identifier taskId
	) {
		CachedSnapshot cached = SNAPSHOT_CACHE.get(server);
		DefinitionSnapshot snapshot = cached != null
				&& cached.timelineInstanceId().equals(timeline.instanceId())
				&& cached.sha256().equals(timeline.snapshot().sha256())
			? cached.snapshot()
			: null;
		if (snapshot == null) {
			TimelineSnapshotCompiler.RestoreResult restored = TimelineSnapshotCompiler.restore(
				timeline.gameId(),
				timeline.snapshot()
			);
			if (!restored.success()) {
				return Optional.empty();
			}
			snapshot = restored.snapshot().orElseThrow();
		}
		TaskDefinition definition = snapshot.tasks().get(taskId);
		return definition == null
			? Optional.empty()
			: Optional.of(definition.name().plainText());
	}

	private static Optional<WorldStateV3> commitCallbackLedger(
		final MinecraftServer server,
		final WorldStateV3 expectedRoot,
		final TimelineInstance expectedTimeline,
		final CallbackSpec callback,
		final List<CallbackStep> ledger
	) {
		return callback.ledgerScope() == CallbackLedgerScope.TIMELINE
			? commitTimelineLedger(server, expectedRoot, expectedTimeline, ledger)
			: commitTaskLedger(server, expectedRoot, expectedTimeline, ledger);
	}

	private static Optional<WorldStateV3> commitTaskLedger(
		final MinecraftServer server,
		final WorldStateV3 expectedRoot,
		final TimelineInstance expectedTimeline,
		final List<CallbackStep> ledger
	) {
		PixelTzzWorldState.CommitV3Result committed = PixelTzzWorldState.get(server)
			.commitV3(
				expectedRoot.stateRevision(),
				current -> {
					TimelineInstance actual = current.timeline().orElseThrow(
						() -> new IllegalStateException("timeline disappeared")
					);
					if (
						!actual.instanceId().equals(expectedTimeline.instanceId())
							|| actual.timelineRevision() != expectedTimeline.timelineRevision()
							|| actual.currentTask().isEmpty()
							|| expectedTimeline.currentTask().isEmpty()
							|| !actual.currentTask()
								.orElseThrow()
								.taskInstanceId()
								.equals(expectedTimeline.currentTask().orElseThrow().taskInstanceId())
					) {
						throw new IllegalStateException("timeline changed");
					}
					TimelineInstance next = incrementTimelineRevision(withTaskLedger(actual, ledger));
					return current.withTimeline(Optional.of(next));
				}
			);
		return committed.state();
	}

	private static Optional<WorldStateV3> commitTimelineLedger(
		final MinecraftServer server,
		final WorldStateV3 expectedRoot,
		final TimelineInstance expectedTimeline,
		final List<CallbackStep> ledger
	) {
		PixelTzzWorldState.CommitV3Result committed = PixelTzzWorldState.get(server)
			.commitV3(
				expectedRoot.stateRevision(),
				current -> {
					TimelineInstance actual = current.timeline().orElseThrow(
						() -> new IllegalStateException("timeline disappeared")
					);
					if (
						!actual.instanceId().equals(expectedTimeline.instanceId())
							|| actual.timelineRevision() != expectedTimeline.timelineRevision()
							|| actual.currentTask().isEmpty()
							|| expectedTimeline.currentTask().isEmpty()
							|| !actual.currentTask()
								.orElseThrow()
								.taskInstanceId()
								.equals(expectedTimeline.currentTask().orElseThrow().taskInstanceId())
					) {
						throw new IllegalStateException("timeline changed");
					}
					TimelineInstance next = incrementTimelineRevision(
						withTimelineLedger(actual, ledger)
					);
					return current.withTimeline(Optional.of(next));
				}
			);
		return committed.state();
	}

	private static void checkpointClock(
		final MinecraftServer server,
		final TimelineInstance current,
		final TimelineInstance next
	) {
		PixelTzzWorldState.TimelineCheckpointResult committed = PixelTzzWorldState.get(server)
			.timelineCheckpoint(
				current.timelineRevision(),
				actual -> {
					if (
						!actual.instanceId().equals(current.instanceId())
							|| actual.currentTask().isEmpty()
							|| current.currentTask().isEmpty()
							|| !actual.currentTask()
								.orElseThrow()
								.taskInstanceId()
								.equals(current.currentTask().orElseThrow().taskInstanceId())
					) {
						throw new IllegalStateException("timeline changed");
					}
					return next;
				}
			);
		if (!committed.committed()) {
			diagnose(current, committed.reason());
		}
	}

	private static ResolvedTimeline resolve(final MinecraftServer server) {
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return ResolvedTimeline.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline is not active");
		}
		TimelineInstance timeline = root.timeline().orElseThrow();
		if (timeline.currentTask().isEmpty()) {
			return ResolvedTimeline.failed(OperationCode.TIMELINE_COMPLETED, "timeline is terminal");
		}
		CachedSnapshot cached = SNAPSHOT_CACHE.get(server);
		if (
			cached == null
				|| !cached.timelineInstanceId().equals(timeline.instanceId())
				|| !cached.sha256().equals(timeline.snapshot().sha256())
		) {
			TimelineSnapshotCompiler.RestoreResult restored = TimelineSnapshotCompiler.restore(
				timeline.gameId(),
				timeline.snapshot()
			);
			if (!restored.success()) {
				return ResolvedTimeline.failed(
					OperationCode.bySerializedName(restored.code())
						.orElse(OperationCode.SNAPSHOT_INVALID),
					restored.message()
				);
			}
			cached = new CachedSnapshot(
				timeline.instanceId(),
				timeline.snapshot().sha256(),
				restored.snapshot().orElseThrow()
			);
			SNAPSHOT_CACHE.put(server, cached);
		}
		TaskDefinition definition = cached.snapshot()
			.tasks()
			.get(timeline.currentTask().orElseThrow().taskId());
		if (definition == null || !definition.game().equals(timeline.gameId())) {
			return ResolvedTimeline.failed(
				OperationCode.SNAPSHOT_INVALID,
				"frozen snapshot has no matching current task"
			);
		}
		return ResolvedTimeline.success(root, timeline, cached.snapshot(), definition);
	}

	static Optional<Identifier> phaseAfterTransition(
		final TimelineInstance before,
		final TimelineInstance after
	) {
		if (before.currentTask().isEmpty()) {
			return Optional.empty();
		}
		FrozenResult result = before.currentTask().orElseThrow().result().orElse(null);
		if (result == null) {
			return Optional.empty();
		}
		if (after.status() == TimelineStatus.COMPLETED) {
			return result.endPhaseId();
		}
		if (
			before.status() == TimelineStatus.SETTLING
				&& after.status() == TimelineStatus.INTERMISSION
		) {
			return result.phaseTransitionTiming()
				.filter(PhaseTransitionTiming.BEFORE_INTERMISSION::equals)
				.flatMap(ignored -> result.phaseId());
		}
		if (
			before.status() == TimelineStatus.INTERMISSION
				&& after.status() == TimelineStatus.STARTING
		) {
			return result.phaseTransitionTiming()
				.filter(PhaseTransitionTiming.AFTER_INTERMISSION::equals)
				.flatMap(ignored -> result.phaseId());
		}
		if (
			before.status() == TimelineStatus.SETTLING
				&& after.status() == TimelineStatus.STARTING
				&& before.currentTask().orElseThrow().intermission().isEmpty()
		) {
			return result.phaseId();
		}
		return Optional.empty();
	}

	private static boolean callbacksSucceeded(
		final TimelineInstance timeline,
		final List<CallbackSpec> callbacks
	) {
		for (CallbackSpec callback : callbacks) {
			CallbackStep step = callbackStep(callbackLedger(timeline, callback), callback);
			if (
				step == null
					|| step.status() != CallbackStepStatus.SUCCEEDED
					|| !step.functionId().equals(callback.functionId())
			) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasFailedOrUnknown(
		final TimelineInstance timeline,
		final List<CallbackSpec> callbacks
	) {
		for (CallbackSpec callback : callbacks) {
			CallbackStep step = callbackStep(callbackLedger(timeline, callback), callback);
			if (
				step != null
					&& (
						step.status() == CallbackStepStatus.FAILED
							|| step.status() == CallbackStepStatus.OUTCOME_UNKNOWN
							|| step.status() == CallbackStepStatus.PREPARED
					)
			) {
				return true;
			}
		}
		return false;
	}

	private static List<CallbackStep> callbackLedger(
		final TimelineInstance timeline,
		final CallbackSpec callback
	) {
		return callback.ledgerScope() == CallbackLedgerScope.TIMELINE
			? timeline.callbackLedger()
			: timeline.currentTask().orElseThrow().callbackLedger();
	}

	private static CallbackStep callbackStep(
		final List<CallbackStep> ledger,
		final CallbackSpec callback
	) {
		return ledger.stream()
			.filter(step ->
				step.stepKey().equals(callback.stepKey())
					&& step.playerId().equals(callback.playerId())
			)
			.findFirst()
			.orElse(null);
	}

	private static TimelineInstance withTaskLedger(
		final TimelineInstance timeline,
		final List<CallbackStep> ledger
	) {
		TaskInstance task = timeline.currentTask().orElseThrow();
		return withCurrentTask(
			timeline,
			new TaskInstance(
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
				ledger,
				task.lifecycleCallbackTrigger(),
				task.phaseTransitionTrigger(),
				task.events(),
				task.statistics()
			)
		);
	}

	private static TimelineInstance withLifecycleTrigger(
		final TimelineInstance timeline,
		final Optional<LifecycleCallbackTrigger> trigger
	) {
		if (timeline.currentTask().isEmpty()) {
			return timeline;
		}
		TaskInstance task = timeline.currentTask().orElseThrow();
		return withCurrentTask(
			timeline,
			new TaskInstance(
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
				trigger,
				task.phaseTransitionTrigger(),
				task.events(),
				task.statistics()
			)
		);
	}

	private static TimelineInstance withPhaseTransitionTrigger(
		final TimelineInstance timeline,
		final Optional<PhaseTransitionTrigger> trigger
	) {
		if (timeline.currentTask().isEmpty()) {
			return timeline;
		}
		TaskInstance task = timeline.currentTask().orElseThrow();
		return withCurrentTask(
			timeline,
			new TaskInstance(
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
				trigger,
				task.events(),
				task.statistics()
			)
		);
	}

	private static TimelineInstance withCurrentTask(
		final TimelineInstance timeline,
		final TaskInstance task
	) {
		return new TimelineInstance(
			timeline.instanceId(),
			timeline.gameId(),
			timeline.contentVersion(),
			timeline.snapshot(),
			timeline.status(),
			timeline.resumeStatus(),
			Optional.of(task),
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

	private static TimelineInstance withTimelineLedger(
		final TimelineInstance timeline,
		final List<CallbackStep> ledger
	) {
		return new TimelineInstance(
			timeline.instanceId(),
			timeline.gameId(),
			timeline.contentVersion(),
			timeline.snapshot(),
			timeline.status(),
			timeline.resumeStatus(),
			timeline.currentTask(),
			timeline.taskHistory(),
			timeline.gameElapsedTicks(),
			timeline.paused(),
			timeline.pauseReason(),
			timeline.createdAtServerTick(),
			timeline.startedAtServerTick(),
			timeline.completedAtServerTick(),
			timeline.timelineRevision(),
			ledger
		);
	}

	private static TimelineInstance incrementTimelineRevision(final TimelineInstance timeline) {
		if (timeline.timelineRevision() == Long.MAX_VALUE) {
			throw new IllegalStateException("timeline revision capacity is exhausted");
		}
		return timeline.withTimelineRevision(timeline.timelineRevision() + 1L);
	}

	private static ActionContext context(final TimelineInstance timeline) {
		TaskInstance task = timeline.currentTask().orElseThrow();
		return new ActionContext(
			timeline.instanceId(),
			task.taskInstanceId(),
			task.taskId(),
			timeline.timelineRevision()
		);
	}

	private static void diagnose(final TimelineInstance timeline, final String message) {
		String previous = LAST_DIAGNOSTIC.put(timeline.instanceId(), message);
		if (!message.equals(previous)) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ timeline {} is not advancing: {}",
				timeline.instanceId(),
				message
			);
		}
	}

	public record RuntimeResult(OperationCode code, String message, boolean changed) {
		public RuntimeResult {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static RuntimeResult changed(final String message) {
			return new RuntimeResult(OperationCode.SUCCESS, message, true);
		}

		private static RuntimeResult unchanged(final String message) {
			return new RuntimeResult(OperationCode.SUCCESS, message, false);
		}

		private static RuntimeResult failed(final OperationCode code, final String message) {
			return new RuntimeResult(code, message, false);
		}
	}

	private record CallbackSpec(
		String stepKey,
		Identifier functionId,
		Optional<UUID> playerId,
		Map<String, Identifier> fields,
		boolean lifecycle,
		CallbackLedgerScope ledgerScope
	) {
		private CallbackSpec {
			Objects.requireNonNull(stepKey, "stepKey");
			Objects.requireNonNull(functionId, "functionId");
			playerId = Objects.requireNonNull(playerId, "playerId");
			fields = Map.copyOf(fields);
			Objects.requireNonNull(ledgerScope, "ledgerScope");
		}

		private static CallbackSpec global(final String key, final Identifier function) {
			return new CallbackSpec(
				key,
				function,
				Optional.empty(),
				Map.of(),
				false,
				CallbackLedgerScope.TASK
			);
		}

		private static CallbackSpec player(
			final String key,
			final Identifier function,
			final UUID playerId,
			final Map<String, Identifier> fields
		) {
			return new CallbackSpec(
				key,
				function,
				Optional.of(playerId),
				fields,
				false,
				CallbackLedgerScope.TASK
			);
		}

		private static CallbackSpec lifecycle(final String key, final Identifier function) {
			return new CallbackSpec(
				key,
				function,
				Optional.empty(),
				Map.of(),
				true,
				CallbackLedgerScope.TASK
			);
		}

		private static CallbackSpec phase(final String key, final Identifier function) {
			return new CallbackSpec(
				key,
				function,
				Optional.empty(),
				Map.of(),
				false,
				CallbackLedgerScope.TIMELINE
			);
		}

		private StepIdentity identity() {
			return new StepIdentity(this.stepKey, this.functionId, this.playerId);
		}
	}

	private enum CallbackLedgerScope {
		TASK,
		TIMELINE
	}

	private record CallbackRun(OperationCode code, String message, boolean changed) {
		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static CallbackRun succeeded(final boolean changed) {
			return new CallbackRun(OperationCode.SUCCESS, "callback succeeded", changed);
		}

		private static CallbackRun failed(final OperationCode code, final String message) {
			return new CallbackRun(code, message, false);
		}
	}

	private record CallbackBatch(OperationCode code, String message) {
		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}
	}

	private record CallbackPlan(
		OperationCode code,
		String message,
		List<CallbackSpec> callbacks
	) {
		private CallbackPlan {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			callbacks = List.copyOf(callbacks);
		}

		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static CallbackPlan success(final List<CallbackSpec> callbacks) {
			return new CallbackPlan(OperationCode.SUCCESS, "", callbacks);
		}

		private static CallbackPlan failed(
			final OperationCode code,
			final String message
		) {
			return new CallbackPlan(code, message, List.of());
		}
	}

	private record ContextResolution(
		OperationCode code,
		String message,
		Optional<TaskCallbackRunner.Context> context
	) {
		private boolean successful() {
			return this.code == OperationCode.SUCCESS && this.context.isPresent();
		}

		private static ContextResolution success(final TaskCallbackRunner.Context context) {
			return new ContextResolution(OperationCode.SUCCESS, "", Optional.of(context));
		}

		private static ContextResolution failed(final String message) {
			return new ContextResolution(OperationCode.CALLBACK_FAILED, message, Optional.empty());
		}
	}

	private record CachedSnapshot(
		UUID timelineInstanceId,
		String sha256,
		DefinitionSnapshot snapshot
	) {
	}

	private record ResolvedTimeline(
		OperationCode code,
		String message,
		Optional<WorldStateV3> root,
		Optional<TimelineInstance> timeline,
		Optional<DefinitionSnapshot> snapshot,
		Optional<TaskDefinition> definition
	) {
		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private RuntimeResult asRuntimeResult() {
			return RuntimeResult.failed(this.code, this.message);
		}

		private static ResolvedTimeline success(
			final WorldStateV3 root,
			final TimelineInstance timeline,
			final DefinitionSnapshot snapshot,
			final TaskDefinition definition
		) {
			return new ResolvedTimeline(
				OperationCode.SUCCESS,
				"",
				Optional.of(root),
				Optional.of(timeline),
				Optional.of(snapshot),
				Optional.of(definition)
			);
		}

		private static ResolvedTimeline failed(
			final OperationCode code,
			final String message
		) {
			return new ResolvedTimeline(
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}
}
