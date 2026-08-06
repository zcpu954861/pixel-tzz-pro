package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Minecraft integration boundary for the pure persisted opening-countdown authority. */
public final class CountdownServerRuntime {
	private static final long HANDOFF_FAILURE_LOG_INTERVAL_TICKS = 200L;
	static final long RECOVERY_CONNECTION_STABILITY_TICKS = 40L;
	private static final Map<UUID, HandoffFailure> HANDOFF_FAILURES = new HashMap<>();
	private static final Map<UUID, Long> RECOVERY_ELIGIBLE_SINCE = new HashMap<>();
	private static final Set<UUID> RECOVERY_FAILED_CLOSED = new HashSet<>();

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
		CountdownProjectionRuntime.countdownTransitionCommitted(
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
		HANDOFF_FAILURES.clear();
		RECOVERY_ELIGIBLE_SINCE.clear();
		RECOVERY_FAILED_CLOSED.clear();
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
			HANDOFF_FAILURES.clear();
			RECOVERY_ELIGIBLE_SINCE.clear();
			RECOVERY_FAILED_CLOSED.clear();
			return;
		}
		UUID activeCountdownInstanceId = current.countdownInstanceId();
		HANDOFF_FAILURES.keySet().removeIf(
			countdownInstanceId -> !countdownInstanceId.equals(activeCountdownInstanceId)
		);
		long tick = server.getTickCount();
		CountdownState state = current.lifecycle().state();
		if (state == CountdownState.RECOVERY_WAIT) {
			if (RECOVERY_FAILED_CLOSED.contains(current.countdownInstanceId())) {
				return;
			}
			if (!recoverySnapshotStable(current, verifiedOnlinePlayers, tick)) {
				return;
			}
			TransitionResult recovery = CountdownAuthority.resumeRecovery(
				current,
				verifiedOnlinePlayers,
				tick
			);
			if (!recovery.accepted()) {
				RECOVERY_FAILED_CLOSED.add(current.countdownInstanceId());
				PixelTzzPro.LOGGER.error(
					"Countdown {} recovery failed closed: {}",
					current.countdownInstanceId(),
					recovery.message()
				);
				return;
			}
			if (commitTransition(server, wrapper, current, recovery)) {
				processCallbackWork(server, wrapper, recovery.callbackWork(), "cold_start_recovery");
				RECOVERY_ELIGIBLE_SINCE.remove(current.countdownInstanceId());
				RECOVERY_FAILED_CLOSED.remove(current.countdownInstanceId());
			}
			current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
			if (current == null) {
				return;
			}
		}
		if (current.lifecycle().state() == CountdownState.WAITING_FOR_PLAYERS) {
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

	static boolean recoverySnapshotStable(
		final Instance countdown,
		final Set<UUID> verifiedOnlinePlayers,
		final long serverTick
	) {
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(verifiedOnlinePlayers, "verifiedOnlinePlayers");
		boolean eligible = countdown.participants().stream()
			.anyMatch(value -> verifiedOnlinePlayers.contains(value.playerId()))
			|| countdown.hostId().filter(verifiedOnlinePlayers::contains).isPresent();
		if (!eligible) {
			RECOVERY_ELIGIBLE_SINCE.remove(countdown.countdownInstanceId());
			return false;
		}
		long first = RECOVERY_ELIGIBLE_SINCE.computeIfAbsent(
			countdown.countdownInstanceId(),
			ignored -> serverTick
		);
		return serverTick >= first
			&& serverTick - first >= RECOVERY_CONNECTION_STABILITY_TICKS;
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
		TransitionResult disconnected = CountdownAuthority.playerDisconnected(
			current,
			playerId,
			server.getTickCount()
		);
		if (commitTransition(server, wrapper, current, disconnected)) {
			processCallbackWork(server, wrapper, disconnected.callbackWork(), "player_disconnect");
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
				true,
				false
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
			if (!HANDOFF_FAILURES.containsKey(current.countdownInstanceId())) {
				dispatchCompleted(server, wrapper, current);
			}
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
			dispatchCompleted(server, wrapper, finalized);
		} else if (finalized.lifecycle().state() == CountdownState.CANCELED) {
			retireTerminal(server, wrapper, finalized);
		}
	}

	/** Separate high-risk path for crash-left callbacks whose external outcome is unknowable. */
	public static RetryResult retryOutcomeUnknownCallbacks(
		final MinecraftServer server,
		final UUID actorId
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		if (wrapper.hostId().filter(actorId::equals).isEmpty()) {
			return RetryResult.rejected(OperationCode.NOT_HOST, "只有当前主持人可以处理结果未知回调");
		}
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		List<io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackEntry> unknown =
			CountdownCallbackRetryConfirmation.outcomeUnknownEntries(current);
		if (current == null || unknown.isEmpty()) {
			return RetryResult.rejected(OperationCode.ACTION_UNAVAILABLE, "当前没有 OUTCOME_UNKNOWN 回调");
		}
		int succeeded = 0;
		int failed = 0;
		for (var entry : unknown) {
			CallbackProcessResult result = processCallback(
				server,
				wrapper,
				new CallbackWork(entry.key(), entry.functionId(), entry.required()),
				"host_retry_outcome_unknown_high_risk",
				false,
				true
			);
			if (result == CallbackProcessResult.PERSISTENCE_FAILED) {
				return new RetryResult(
					OperationCode.INTERNAL_ERROR,
					"结果未知回调的 PREPARED 检查点失败；请重新审阅",
					unknown.size(), succeeded, failed
				);
			}
			if (result == CallbackProcessResult.SUCCEEDED) {
				succeeded++;
			} else if (result == CallbackProcessResult.FAILED) {
				failed++;
			}
		}
		finalizeIfReady(server, wrapper);
		return new RetryResult(
			failed == 0 ? OperationCode.SUCCESS : OperationCode.CALLBACK_FAILED,
			failed == 0
				? "已在高风险确认后重试 " + succeeded + " 项结果未知回调"
				: "高风险重试完成：" + succeeded + " 项成功，" + failed + " 项失败",
			unknown.size(), succeeded, failed
		);
	}

	public static HandoffResult retryFrozenHandoff(
		final MinecraftServer server,
		final UUID actorId,
		final UUID expectedCountdownInstanceId
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		if (wrapper.hostId().filter(actorId::equals).isEmpty()) {
			return HandoffResult.rejected(OperationCode.NOT_HOST, "只有当前主持人可以重试冻结交接");
		}
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (
			current == null
				|| !current.countdownInstanceId().equals(expectedCountdownInstanceId)
				|| current.lifecycle().state() != CountdownState.COMPLETED
		) {
			return HandoffResult.rejected(OperationCode.STATE_REVISION_STALE, "冻结交接实例已经变化");
		}
		dispatchCompleted(server, wrapper, current);
		boolean retired = wrapper.currentV5().flatMap(WorldStateV5::countdown).isEmpty();
		return retired
			? HandoffResult.accepted("冻结任务候选已成功接管；同一 LaunchPlan 仅提交一次")
			: HandoffResult.rejected(OperationCode.ACTION_UNAVAILABLE, "冻结交接仍失败，请查看诊断或选择高风险放弃");
	}

	public static HandoffResult abandonFailedHandoff(
		final MinecraftServer server,
		final UUID actorId,
		final UUID expectedCountdownInstanceId
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		if (wrapper.hostId().filter(actorId::equals).isEmpty()) {
			return HandoffResult.rejected(OperationCode.NOT_HOST, "只有当前主持人可以放弃失败交接");
		}
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (
			current == null
				|| !current.countdownInstanceId().equals(expectedCountdownInstanceId)
				|| current.lifecycle().state() != CountdownState.COMPLETED
				|| !HANDOFF_FAILURES.containsKey(expectedCountdownInstanceId)
		) {
			return HandoffResult.rejected(OperationCode.ACTION_UNAVAILABLE, "当前没有已确认失败的冻结交接可放弃");
		}
		if (!checkpoint(wrapper, current, Optional.empty())) {
			return HandoffResult.rejected(OperationCode.INTERNAL_ERROR, "放弃冻结交接的持久化提交失败");
		}
		HANDOFF_FAILURES.remove(expectedCountdownInstanceId);
		CountdownProjectionRuntime.countdownRetired(server, current);
		PixelTzzServerRuntime.timelineStateChanged(server);
		return HandoffResult.accepted("已放弃失败的冻结交接并返回等待批准；外部副作用不会回滚");
	}

	private static void dispatchCompleted(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final Instance countdown
	) {
		switch (countdown.purpose()) {
			case OPENING -> startFrozenTimeline(server, wrapper, countdown);
		}
	}

	private static void startFrozenTimeline(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final Instance countdown
	) {
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		if (root == null || root.core().activeGameId().isEmpty()) {
			reportHandoffFailure(server, countdown, "已完成的开局倒计时找不到活动游戏");
			return;
		}
		LaunchPlan persistedPlan = countdown.launchPlan().orElse(null);
		if (persistedPlan != null && persistedPlan.approvedCandidate().isPresent()) {
			startExactFrozenCandidate(server, wrapper, countdown, root, persistedPlan);
			return;
		}
		var restored = CountdownSnapshotCompiler.restore(
			root.core().activeGameId().orElseThrow(),
			countdown.definition().definitionId(),
			countdown.definition().closure()
		);
		if (!restored.success()) {
			reportHandoffFailure(
				server,
				countdown,
				"无法恢复冻结任务时间线：" + restored.message()
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
			reportHandoffFailure(
				server,
				countdown,
				"无法接管冻结任务时间线：" + approval.message()
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
			reportHandoffFailure(
				server,
				countdown,
				"任务时间线接管提交失败：" + committed.reason()
			);
			return;
		}
		HANDOFF_FAILURES.remove(countdown.countdownInstanceId());
		CountdownProjectionRuntime.countdownRetired(server, countdown);
		PixelTzzServerRuntime.timelineActivated(server, root);
	}

	private static void startExactFrozenCandidate(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final Instance countdown,
		final WorldStateV3 current,
		final LaunchPlan launchPlan
	) {
		var document = launchPlan.approvedCandidate().orElseThrow();
		if (
			document.hasMissingContent()
				|| !io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument
					.of(document.normalizedJson()).sha256().equals(document.sha256())
		) {
			reportHandoffFailure(server, countdown, "冻结开局候选内容缺失或哈希损坏");
			return;
		}
		WorldStateV3 frozen = WorldStateV3.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(document.normalizedJson()))
			.result()
			.orElse(null);
		if (
			frozen == null
				|| !frozen.activeGameInstanceId().filter(countdown.gameInstanceId()::equals).isPresent()
				|| frozen.timeline().filter(value -> value.instanceId().equals(launchPlan.timelineInstanceId())).isEmpty()
				|| frozen.core().activePhaseId().filter(launchPlan.approvalPhaseId()::equals).isEmpty()
				|| current.timeline().isPresent()
		) {
			reportHandoffFailure(server, countdown, "冻结开局候选与倒计时交接身份不一致");
			return;
		}
		WorldStateV3 candidate = frozen
			.withReadiness(Optional.empty())
			.withCore(
				frozen.core().withAudit(current.core().audit())
			);
		long revision = wrapper.stateRevision();
		PixelTzzWorldState.CommitV5Result committed = wrapper.commitV5(
			revision,
			state -> state
				.withCore(state.core().withCore(candidate))
				.withCountdown(Optional.empty())
		);
		if (!committed.committed()) {
			reportHandoffFailure(server, countdown, "精确冻结候选提交失败：" + committed.reason());
			return;
		}
		HANDOFF_FAILURES.remove(countdown.countdownInstanceId());
		CountdownProjectionRuntime.countdownRetired(server, countdown);
		PixelTzzServerRuntime.timelineActivated(server, current);
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
			participants,
			Optional.empty()
		);
	}

	private static UUID legacyLaunchId(final UUID countdownInstanceId, final String slot) {
		return UUID.nameUUIDFromBytes(
			("pixel-tzz-pro:countdown-launch:" + countdownInstanceId + ":" + slot)
				.getBytes(StandardCharsets.UTF_8)
		);
	}

	private static void reportHandoffFailure(
		final MinecraftServer server,
		final Instance countdown,
		final String diagnostic
	) {
		long tick = server.getTickCount();
		HandoffFailure previous = HANDOFF_FAILURES.get(countdown.countdownInstanceId());
		boolean changed = previous == null || !previous.diagnostic().equals(diagnostic);
		if (changed || tick - previous.lastLoggedTick() >= HANDOFF_FAILURE_LOG_INTERVAL_TICKS) {
			PixelTzzPro.LOGGER.error(
				"Completed countdown {} is waiting for its purpose handoff: {}",
				countdown.countdownInstanceId(),
				diagnostic
			);
			HANDOFF_FAILURES.put(countdown.countdownInstanceId(), new HandoffFailure(tick, diagnostic));
		}
		CountdownProjectionRuntime.handoffFailed(server, countdown, diagnostic);
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
				false,
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
		final boolean explicitRetry,
		final boolean highRiskUnknownRetry
	) {
		Instance current = wrapper.currentV5().flatMap(WorldStateV5::countdown).orElse(null);
		if (current == null || !current.countdownInstanceId().equals(item.key().countdownInstanceId())) {
			return CallbackProcessResult.INSTANCE_RETIRED;
		}
		PrepareCallbackResult prepared = highRiskUnknownRetry
			? CountdownAuthority.prepareUnknownCallback(
				current, item.key(), server.getTickCount(), true
			)
			: CountdownAuthority.prepareCallback(
				current, item.key(), server.getTickCount(), explicitRetry
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
			HANDOFF_FAILURES.remove(terminal.countdownInstanceId());
			CountdownProjectionRuntime.countdownRetired(server, terminal);
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
			CountdownProjectionRuntime.countdownTransitionCommitted(server, before, after, checkpoint);
		}
	}

	private record HandoffFailure(long lastLoggedTick, String diagnostic) {
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

	public record HandoffResult(OperationCode code, String message) {
		public HandoffResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static HandoffResult accepted(final String message) {
			return new HandoffResult(OperationCode.SUCCESS, message);
		}

		private static HandoffResult rejected(final OperationCode code, final String message) {
			return new HandoffResult(code, message);
		}
	}

	private enum CallbackProcessResult {
		SUCCEEDED,
		FAILED,
		PERSISTENCE_FAILED,
		INSTANCE_RETIRED
	}
}
