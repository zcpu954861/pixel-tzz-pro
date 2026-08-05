package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler.RestoredCountdown;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.CallbackWork;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.PrepareCallbackResult;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.RestrictedAction;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.RestrictionDecision;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.TransitionResult;
import io.github.zcpu954861.pixeltzzpro.server.TimelineApprovalAuthority.FrozenActivationContext;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.LaunchPlan;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV5;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Minecraft integration boundary for the pure persisted opening-countdown authority. */
public final class CountdownServerRuntime {
	private CountdownServerRuntime() {
	}

	public static CommitResult commitApprovedCountdown(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final long expectedRevision,
		final OpeningCountdownApproval.Result approval
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(wrapper, "wrapper");
		Objects.requireNonNull(approval, "approval");
		if (!approval.successful() || approval.countdown().isEmpty()) {
			return CommitResult.rejected(
				approval.successful() ? OperationCode.ACTION_UNAVAILABLE : approval.code(),
				approval.message()
			);
		}
		if (wrapper.currentV5().flatMap(WorldStateV5::countdown).isPresent()) {
			return CommitResult.rejected(
				OperationCode.ACTION_UNAVAILABLE,
				"已有正式开局倒计时实例，不能覆盖冻结候选"
			);
		}
		Instance countdown = approval.countdown().orElseThrow();
		PixelTzzWorldState.CommitV5Result committed = wrapper.commitV5(
			expectedRevision,
			state -> state.withCountdown(Optional.of(countdown))
		);
		if (!committed.committed()) {
			return CommitResult.rejected(OperationCode.STATE_REVISION_STALE, committed.reason());
		}
		Instance persisted = wrapper.currentV5()
			.flatMap(WorldStateV5::countdown)
			.orElseThrow();
		HudServerRuntime.countdownTransitionCommitted(
			server,
			null,
			persisted,
			initialCheckpoint(persisted)
		);
		processCallbackWork(server, wrapper, approval.callbackWork(), "start");
		return CommitResult.committed(
			wrapper.currentV5().orElseThrow(),
			approval.frozenParticipants(),
			"正式开局倒计时已开始"
		);
	}

	/** Called once after world migration; wall-clock downtime is never deducted. */
	public static void enterRecoveryWait(final MinecraftServer server) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null || current.lifecycle().state().terminal()) {
			return;
		}
		TransitionResult recovery = CountdownAuthority.enterRecoveryWait(
			current,
			server.getTickCount(),
			"服务器启动后等待客户端连接协调"
		);
		commitTransition(server, wrapper, current, recovery);
	}

	public static void tick(
		final MinecraftServer server,
		final Set<UUID> verifiedOnlinePlayers
	) {
		Objects.requireNonNull(verifiedOnlinePlayers, "verifiedOnlinePlayers");
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null) {
			return;
		}
		long tick = server.getTickCount();
		CountdownState state = current.lifecycle().state();
		if (state == CountdownState.RECOVERY_WAIT || state == CountdownState.WAITING_FOR_PLAYERS) {
			TransitionResult reconciliation = CountdownAuthority.reconcileConnections(
				current,
				verifiedOnlinePlayers,
				tick
			);
			if (commitTransition(server, wrapper, current, reconciliation)) {
				processCallbackWork(server, wrapper, reconciliation.callbackWork(), "resume");
			}
			current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
			if (current == null) {
				return;
			}
		}

		if (current.lifecycle().state() == CountdownState.RUNNING) {
			TransitionResult advanced = CountdownAuthority.tick(current, tick);
			if (commitTransition(server, wrapper, current, advanced)) {
				processCallbackWork(server, wrapper, advanced.callbackWork(), "complete");
			}
		}
		finalizeIfReady(server, wrapper);
	}

	public static void playerDisconnected(
		final MinecraftServer server,
		final UUID playerId
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null) {
			return;
		}
		TransitionResult participant = CountdownAuthority.requiredParticipantDisconnected(
			current,
			playerId,
			server.getTickCount()
		);
		if (commitTransition(server, wrapper, current, participant)) {
			processCallbackWork(server, wrapper, participant.callbackWork(), "required_player_disconnect");
		}
		current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null) {
			return;
		}
		TransitionResult host = CountdownAuthority.hostDisconnected(
			current,
			playerId,
			server.getTickCount()
		);
		if (commitTransition(server, wrapper, current, host)) {
			processCallbackWork(server, wrapper, host.callbackWork(), "host_disconnect");
		}
		finalizeIfReady(server, wrapper);
	}

	public static CancelResult requestCancel(
		final MinecraftServer server,
		final UUID actorId,
		final UUID expectedCountdownInstanceId,
		final long expectedStateVersion,
		final String reason
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		if (wrapper.hostId().filter(actorId::equals).isEmpty()) {
			return CancelResult.rejected(OperationCode.NOT_HOST, "只有当前主持人可以取消正式倒计时");
		}
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (
			current == null
				|| !current.countdownInstanceId().equals(expectedCountdownInstanceId)
				|| current.lifecycle().stateVersion() != expectedStateVersion
		) {
			return CancelResult.rejected(
				OperationCode.STATE_REVISION_STALE,
				"倒计时状态已经变化，请重新审阅当前状态"
			);
		}
		TransitionResult cancel = CountdownAuthority.requestCancel(
			current,
			server.getTickCount(),
			reason
		);
		if (!commitTransition(server, wrapper, current, cancel)) {
			return CancelResult.rejected(OperationCode.ACTION_UNAVAILABLE, cancel.message());
		}
		processCallbackWork(server, wrapper, cancel.callbackWork(), "host_cancel");
		finalizeIfReady(server, wrapper);
		Instance after = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		return new CancelResult(
			OperationCode.SUCCESS,
			after == null ? "正式开局倒计时已取消，已返回等待批准" : "正式开局倒计时正在提交取消回调",
			Optional.ofNullable(after)
		);
	}

	/** Retries only the unresolved callbacks bound by the reviewed countdown ledger. */
	public static RetryResult retryFailedCallbacks(
		final MinecraftServer server,
		final UUID actorId,
		final CountdownCallbackRetryConfirmation.Challenge challenge
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(challenge, "challenge");
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		if (wrapper.hostId().filter(actorId::equals).isEmpty()) {
			return RetryResult.rejected(
				OperationCode.NOT_HOST,
				"只有当前主持人可以重试正式倒计时回调"
			);
		}
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		var validation = CountdownCallbackRetryConfirmation.validate(
			current,
			challenge,
			server.getTickCount()
		);
		if (!validation.successful()) {
			return RetryResult.rejected(validation.code(), validation.message());
		}

		var retryable = CountdownCallbackRetryConfirmation.retryableEntries(current);
		int succeeded = 0;
		int failed = 0;
		for (var entry : retryable) {
			CallbackProcessResult result = processCallback(
				server,
				wrapper,
				new CallbackWork(entry.key(), entry.functionId(), entry.required()),
				"host_retry_failed_callbacks",
				true
			);
			if (result == CallbackProcessResult.PERSISTENCE_FAILED) {
				return new RetryResult(
					OperationCode.INTERNAL_ERROR,
					"回调重试的持久化检查点失败；已成功项不会重放，请重新审阅当前账本",
					retryable.size(),
					succeeded,
					failed
				);
			}
			if (result == CallbackProcessResult.SUCCEEDED) {
				succeeded++;
			} else if (result == CallbackProcessResult.FAILED) {
				failed++;
			}
		}
		finalizeIfReady(server, wrapper);
		if (failed > 0) {
			return new RetryResult(
				OperationCode.CALLBACK_FAILED,
				"已重试 " + retryable.size() + " 项：" + succeeded + " 项成功，" + failed
					+ " 项仍失败；成功项不会再次执行",
				retryable.size(),
				succeeded,
				failed
			);
		}
		return new RetryResult(
			OperationCode.SUCCESS,
			"已重试并确认 " + succeeded + " 项回调；成功项不会再次执行",
			retryable.size(),
			succeeded,
			0
		);
	}

	public static RestrictionDecision restrictionDecision(
		final MinecraftServer server,
		final UUID playerId,
		final RestrictedAction action
	) {
		Instance countdown = PixelTzzWorldState.get(server)
			.currentV5()
			.flatMap(WorldStateV5::countdown)
			.orElse(null);
		return CountdownAuthority.restrictionDecision(countdown, playerId, action);
	}

	private static void finalizeIfReady(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper
	) {
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null) {
			return;
		}
		if (current.lifecycle().state() == CountdownState.COMPLETED) {
			startFrozenTimeline(server, wrapper, current);
			return;
		}
		if (current.lifecycle().state() == CountdownState.CANCELED) {
			retireTerminal(server, wrapper, current);
			return;
		}
		TransitionResult terminal = switch (current.lifecycle().state()) {
			case COMPLETING -> CountdownAuthority.finalizeComplete(current, server.getTickCount());
			case CANCELING -> CountdownAuthority.finalizeCancel(current, server.getTickCount());
			default -> null;
		};
		if (
			terminal == null
				|| !terminal.accepted()
				|| !commitTransition(server, wrapper, current, terminal)
		) {
			return;
		}
		Instance finalized = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElseThrow();
		if (finalized.lifecycle().state() == CountdownState.COMPLETED) {
			startFrozenTimeline(server, wrapper, finalized);
		} else if (finalized.lifecycle().state() == CountdownState.CANCELED) {
			retireTerminal(server, wrapper, finalized);
		}
	}

	private static void startFrozenTimeline(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final Instance countdown
	) {
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		if (root == null || root.core().activeGameId().isEmpty()) {
			PixelTzzPro.LOGGER.error("Completed countdown {} has no active game", countdown.countdownInstanceId());
			return;
		}
		var restored = CountdownSnapshotCompiler.restore(
			root.core().activeGameId().orElseThrow(),
			countdown.definition().definitionId(),
			countdown.definition().closure()
		);
		if (!restored.success()) {
			PixelTzzPro.LOGGER.error(
				"Completed countdown {} could not restore its frozen timeline: {}",
				countdown.countdownInstanceId(),
				restored.message()
			);
			return;
		}
		RestoredCountdown frozen = restored.restored().orElseThrow();
		DefinitionSnapshot definitions = frozen.timelineSnapshot();
		LaunchPlan launchPlan = resolveLaunchPlan(countdown, frozen);
		var approval = TimelineApprovalAuthority.activateFrozen(
			root,
			definitions,
			frozen.timelineDocument(),
			new FrozenActivationContext(
				countdown.gameInstanceId(),
				launchPlan.approvalPhaseId(),
				launchPlan.timelineInstanceId(),
				launchPlan.firstTaskInstanceId(),
				server.getTickCount(),
				launchPlan.participantIds()
			)
		);
		if (!approval.successful()) {
			PixelTzzPro.LOGGER.error(
				"Completed countdown {} could not create its frozen timeline: {}",
				countdown.countdownInstanceId(),
				approval.message()
			);
			return;
		}
		long revision = wrapper.stateRevision();
		PixelTzzWorldState.CommitV5Result committed = wrapper.commitV5(
			revision,
			state -> state
				.withCore(state.core().withCore(approval.nextState().orElseThrow()))
				.withCountdown(Optional.empty())
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.error(
				"Completed countdown {} timeline commit failed: {}",
				countdown.countdownInstanceId(),
				committed.reason()
			);
			return;
		}
		HudServerRuntime.countdownRetired(server, countdown);
		PixelTzzServerRuntime.timelineStateChanged(server);
	}

	/** Resolves legacy schema-v5 countdowns without consulting live players or active definitions. */
	static LaunchPlan resolveLaunchPlan(
		final Instance countdown,
		final RestoredCountdown frozen
	) {
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(frozen, "frozen");
		if (countdown.launchPlan().isPresent()) {
			return countdown.launchPlan().orElseThrow();
		}
		var timeline = frozen.game().taskTimeline().orElseThrow(() ->
			new IllegalArgumentException("legacy countdown snapshot has no task timeline")
		);
		return resolveLegacyLaunchPlan(countdown, timeline.approvalPhase());
	}

	static LaunchPlan resolveLegacyLaunchPlan(
		final Instance countdown,
		final net.minecraft.resources.Identifier approvalPhaseId
	) {
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(approvalPhaseId, "approvalPhaseId");
		List<UUID> participants = countdown.participants().stream()
			.map(value -> value.playerId())
			.sorted()
			.toList();
		return new LaunchPlan(
			approvalPhaseId,
			legacyLaunchId(countdown.countdownInstanceId(), "timeline"),
			legacyLaunchId(countdown.countdownInstanceId(), "first_task"),
			participants
		);
	}

	private static UUID legacyLaunchId(final UUID countdownInstanceId, final String slot) {
		return UUID.nameUUIDFromBytes(
			("pixel-tzz-pro:countdown-launch:" + countdownInstanceId + ":" + slot)
				.getBytes(StandardCharsets.UTF_8)
		);
	}

	private static void processCallbackWork(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final List<CallbackWork> work,
		final String reason
	) {
		for (CallbackWork item : work) {
			CallbackProcessResult result = processCallback(
				server,
				wrapper,
				item,
				reason,
				false
			);
			if (result == CallbackProcessResult.INSTANCE_RETIRED) {
				return;
			}
		}
		if (!work.isEmpty()) {
			PixelTzzServerRuntime.timelineStateChanged(server);
		}
	}

	private static CallbackProcessResult processCallback(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final CallbackWork item,
		final String reason,
		final boolean explicitRetry
	) {
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null || !current.countdownInstanceId().equals(item.key().countdownInstanceId())) {
			return CallbackProcessResult.INSTANCE_RETIRED;
		}
		PrepareCallbackResult prepared = CountdownAuthority.prepareCallback(
			current,
			item.key(),
			server.getTickCount(),
			explicitRetry
		);
		if (!prepared.prepared()) {
			PixelTzzPro.LOGGER.warn("Countdown callback was not prepared: {}", prepared.message());
			return CallbackProcessResult.PERSISTENCE_FAILED;
		}
		Instance preparedState = prepared.instance().orElseThrow();
		if (!checkpoint(wrapper, current, Optional.of(preparedState))) {
			return CallbackProcessResult.PERSISTENCE_FAILED;
		}
		publishCommittedTransition(server, current, preparedState, Optional.empty());
		var callback = prepared.callback().orElseThrow();
		CountdownCallbackRunner.Result invocation = CountdownCallbackRunner.invoke(
			server,
			preparedState,
			item,
			reason
		);
		TransitionResult outcome = CountdownAuthority.recordCallbackOutcome(
			preparedState,
			callback.key(),
			callback.attempt(),
			invocation.success(),
			invocation.success() ? Optional.empty() : Optional.of(invocation.error()),
			server.getTickCount()
		);
		if (!commitTransition(server, wrapper, preparedState, outcome)) {
			PixelTzzPro.LOGGER.error(
				"Countdown callback outcome could not be persisted: {}",
				outcome.message()
			);
			return CallbackProcessResult.PERSISTENCE_FAILED;
		}
		return invocation.success()
			? CallbackProcessResult.SUCCEEDED
			: CallbackProcessResult.FAILED;
	}

	private static boolean commitTransition(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final Instance before,
		final TransitionResult transition
	) {
		if (!transition.accepted()) {
			return false;
		}
		Instance after = transition.instance().orElseThrow();
		if (before.equals(after)) {
			return true;
		}
		if (!checkpoint(wrapper, before, Optional.of(after))) {
			return false;
		}
		publishCommittedTransition(server, before, after, transition.checkpointToProject());
		return true;
	}

	private static boolean checkpoint(
		final PixelTzzWorldState wrapper,
		final Instance before,
		final Optional<Instance> replacement
	) {
		var committed = wrapper.countdownCheckpoint(
			before.gameInstanceId(),
			before.countdownInstanceId(),
			before.lifecycle().stateVersion(),
			before.lifecycle().remainingTicks(),
			before.lifecycle().lastServerTickAnchor(),
			replacement
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.warn("Countdown checkpoint rejected: {}", committed.reason());
		}
		return committed.committed();
	}

	private static void retireTerminal(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final Instance terminal
	) {
		if (checkpoint(wrapper, terminal, Optional.empty())) {
			HudServerRuntime.countdownRetired(server, terminal);
			PixelTzzServerRuntime.timelineStateChanged(server);
		}
	}

	private static void publishCommittedTransition(
		final MinecraftServer server,
		final Instance before,
		final Instance after,
		final Optional<io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition>
			checkpoint
	) {
		boolean projectionChanged = before.lifecycle().stateVersion()
			!= after.lifecycle().stateVersion()
			|| !before.callbackLedger().equals(after.callbackLedger())
			|| checkpoint.isPresent();
		if (projectionChanged) {
			HudServerRuntime.countdownTransitionCommitted(server, before, after, checkpoint);
		}
	}

	private static Optional<io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition>
	initialCheckpoint(final Instance countdown) {
		Set<String> emitted = Set.copyOf(countdown.emittedCheckpointIds());
		return countdown.checkpoints()
			.stream()
			.filter(value -> value.remainingTicks() == countdown.totalTicks())
			.filter(value -> emitted.contains(value.id()))
			.min(
				java.util.Comparator.comparingLong(
					io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition::remainingTicks
				).thenComparing(
					io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition::id
				)
			);
	}

	public record CommitResult(
		OperationCode code,
		String message,
		Optional<WorldStateV5> state,
		List<UUID> frozenParticipants
	) {
		public CommitResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			state = Objects.requireNonNull(state, "state");
			frozenParticipants = List.copyOf(frozenParticipants);
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static CommitResult committed(
			final WorldStateV5 state,
			final List<UUID> participants,
			final String message
		) {
			return new CommitResult(OperationCode.SUCCESS, message, Optional.of(state), participants);
		}

		private static CommitResult rejected(final OperationCode code, final String message) {
			return new CommitResult(code, message, Optional.empty(), List.of());
		}
	}

	public record CancelResult(
		OperationCode code,
		String message,
		Optional<Instance> countdown
	) {
		public CancelResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			countdown = Objects.requireNonNull(countdown, "countdown");
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static CancelResult rejected(final OperationCode code, final String message) {
			return new CancelResult(code, message, Optional.empty());
		}
	}

	public record RetryResult(
		OperationCode code,
		String message,
		int attempted,
		int succeeded,
		int failed
	) {
		public RetryResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			if (attempted < 0 || succeeded < 0 || failed < 0 || succeeded + failed > attempted) {
				throw new IllegalArgumentException("countdown retry counts are inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static RetryResult rejected(final OperationCode code, final String message) {
			return new RetryResult(code, message, 0, 0, 0);
		}
	}

	private enum CallbackProcessResult {
		SUCCEEDED,
		FAILED,
		PERSISTENCE_FAILED,
		INSTANCE_RETIRED
	}
}
