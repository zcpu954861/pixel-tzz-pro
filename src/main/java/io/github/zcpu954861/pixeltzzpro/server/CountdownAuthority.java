package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackLedger;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicy;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenParticipant;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Lifecycle;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.LaunchPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.RestrictionMarker;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure state machine for one server-authoritative opening countdown.
 *
 * <p>Every method accepts and returns stable DTOs. It does not inspect a server, resolve a player,
 * execute a function, send a packet, or mutate a game phase. The integration must durably publish
 * a prepared callback entry before invoking the returned function and must durably record its
 * outcome afterwards.
 */
public final class CountdownAuthority {
	private CountdownAuthority() {
	}

	/** Creates the frozen instance and activates the first start-callback occurrence. */
	public static TransitionResult start(final StartRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.serverTick() < 0L || request.totalTicks() <= 0L) {
			return TransitionResult.rejected("倒计时起始 Tick 或总时长无效");
		}
		if (
			request.definition().closure().hasMissingContent()
				|| !FrozenDocument.of(request.definition().closure().normalizedJson())
					.sha256()
					.equals(request.definition().closure().sha256())
		) {
			return TransitionResult.rejected("倒计时冻结闭包无效");
		}
		List<RestrictionMarker> markers = request.participants().stream()
			.map(value -> new RestrictionMarker(value.playerId(), 0L, true))
			.toList();
		Lifecycle lifecycle = new Lifecycle(
			CountdownState.RUNNING,
			0L,
			request.totalTicks(),
			request.serverTick(),
			Optional.empty(),
			Optional.empty()
		);
		Instance initial = new Instance(
			request.gameInstanceId(),
			request.countdownInstanceId(),
			request.definition(),
			Optional.of(request.launchPlan()),
			lifecycle,
			request.totalTicks(),
			request.participants(),
			request.hostId(),
			request.disconnectPolicies(),
			request.restrictions(),
			markers,
			request.checkpoints(),
			List.of(),
			request.callbacks(),
			CallbackLedger.empty()
		);
		var validation = initial.validated();
		if (validation.error().isPresent()) {
			return TransitionResult.rejected(
				"倒计时冻结实例无效：" + validation.error().orElseThrow().message()
			);
		}

		Activation activation = activateSlot(initial, CallbackSlot.START);
		if (!activation.accepted()) {
			return TransitionResult.rejected(activation.message());
		}
		Instance startedInstance = activation.instance().orElseThrow();
		List<CheckpointDefinition> atStart = startedInstance.checkpoints().stream()
			.filter(value -> value.remainingTicks() == startedInstance.totalTicks())
			.toList();
		Optional<CheckpointDefinition> projection = lastMeaningful(atStart);
		Instance activated = startedInstance;
		if (!atStart.isEmpty()) {
			List<String> emitted = new ArrayList<>(activated.emittedCheckpointIds());
			atStart.forEach(value -> emitted.add(value.id()));
			activated = activated.withEmittedCheckpoints(emitted, activated.lifecycle());
		}
		return TransitionResult.accepted(
			activated,
			activation.work(),
			projection,
			markers.stream().map(RestrictionMarker::participantId).toList(),
			List.of(),
			"正式开局倒计时已冻结"
		);
	}

	/**
	 * Advances by the positive server-Tick delta since the persisted anchor. Only RUNNING can
	 * decrease remaining time; duplicate or backwards anchors are idempotent.
	 */
	public static TransitionResult tick(final Instance current, final long serverTick) {
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		if (current.lifecycle().state() != CountdownState.RUNNING) {
			return TransitionResult.unchanged(current, "当前倒计时状态不扣减时间");
		}
		if (serverTick <= current.lifecycle().lastServerTickAnchor()) {
			return TransitionResult.unchanged(current, "重复或倒退的服务端 Tick 已忽略");
		}

		long delta = serverTick - current.lifecycle().lastServerTickAnchor();
		if (
			!latestRequiredCallbacksSucceeded(current, CallbackSlot.START)
				|| !latestRequiredCallbacksSucceeded(current, CallbackSlot.RESUME)
		) {
			Lifecycle frozen = lifecycle(
				current,
				CountdownState.RUNNING,
				current.lifecycle().stateVersion(),
				current.lifecycle().remainingTicks(),
				serverTick
			);
			return TransitionResult.accepted(
				current.withLifecycle(frozen),
				List.of(),
				Optional.empty(),
				List.of(),
				List.of(),
				"必需回调尚未完成，倒计时保持冻结"
			);
		}

		long oldRemaining = current.lifecycle().remainingTicks();
		long nextRemaining = Math.max(0L, oldRemaining - Math.min(delta, oldRemaining));
		Set<String> alreadyEmitted = Set.copyOf(current.emittedCheckpointIds());
		List<CheckpointDefinition> crossed = current.checkpoints().stream()
			.filter(value -> !alreadyEmitted.contains(value.id()))
			.filter(value -> oldRemaining > value.remainingTicks())
			.filter(value -> nextRemaining <= value.remainingTicks())
			.toList();
		List<String> emitted = new ArrayList<>(current.emittedCheckpointIds());
		crossed.forEach(value -> emitted.add(value.id()));

		long stateVersion = current.lifecycle().stateVersion();
		CountdownState state = CountdownState.RUNNING;
		if (nextRemaining == 0L) {
			state = CountdownState.COMPLETING;
			stateVersion = increment(stateVersion, "倒计时状态版本溢出");
			if (stateVersion < 0L) {
				return TransitionResult.rejected("倒计时状态版本溢出");
			}
		}
		Lifecycle nextLifecycle = lifecycle(current, state, stateVersion, nextRemaining, serverTick);
		Instance next = current.withEmittedCheckpoints(emitted, nextLifecycle);
		List<CallbackWork> work = List.of();
		if (state == CountdownState.COMPLETING) {
			Activation activation = activateSlot(next, CallbackSlot.COMPLETE);
			if (!activation.accepted()) {
				return TransitionResult.rejected(activation.message());
			}
			next = activation.instance().orElseThrow();
			work = activation.work();
		}
		return TransitionResult.accepted(
			next,
			work,
			lastMeaningful(crossed),
			List.of(),
			List.of(),
			state == CountdownState.COMPLETING ? "倒计时进入完成提交" : "倒计时已推进"
		);
	}

	/**
	 * Whether a client may interpolate the visible clock between authoritative projections.
	 * Required start/resume callbacks freeze the server clock even while the persisted lifecycle is
	 * still {@code running}; exposing the same decision here prevents the client from visually
	 * counting ahead of authority after a callback failure.
	 */
	public static boolean clockAdvancing(final Instance current) {
		return current != null
			&& current.lifecycle().state() == CountdownState.RUNNING
			&& latestRequiredCallbacksSucceeded(current, CallbackSlot.START)
			&& latestRequiredCallbacksSucceeded(current, CallbackSlot.RESUME);
	}

	/** Applies the frozen required-participant policy to one verified disconnect. */
	public static TransitionResult requiredParticipantDisconnected(
		final Instance current,
		final UUID participantId,
		final long serverTick
	) {
		Objects.requireNonNull(participantId, "participantId");
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		boolean required = current.participants().stream()
			.anyMatch(value -> value.playerId().equals(participantId) && value.required());
		if (!required || current.lifecycle().state() != CountdownState.RUNNING) {
			return TransitionResult.unchanged(current, "该玩家掉线不改变当前倒计时");
		}
		return applyDisconnectPolicy(
			current,
			current.disconnectPolicies().requiredParticipant(),
			serverTick,
			"所需参与者掉线"
		);
	}

	/** Applies the independently frozen host-disconnect policy. */
	public static TransitionResult hostDisconnected(
		final Instance current,
		final UUID disconnectedHostId,
		final long serverTick
	) {
		Objects.requireNonNull(disconnectedHostId, "disconnectedHostId");
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		if (
			current.hostId().filter(disconnectedHostId::equals).isEmpty()
				|| current.lifecycle().state() != CountdownState.RUNNING
		) {
			return TransitionResult.unchanged(current, "该主持人掉线不改变当前倒计时");
		}
		return applyDisconnectPolicy(
			current,
			current.disconnectPolicies().host(),
			serverTick,
			"主持人掉线"
		);
	}

	/**
	 * Reconciles the frozen UUID roster after reconnect or startup. In RECOVERY_WAIT this is the
	 * only route back to running/waiting/completing/canceling.
	 */
	public static TransitionResult reconcileConnections(
		final Instance current,
		final Set<UUID> onlinePlayers,
		final long serverTick
	) {
		Objects.requireNonNull(onlinePlayers, "onlinePlayers");
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		CountdownState state = current.lifecycle().state();
		if (state.terminal() || state == CountdownState.COMPLETING || state == CountdownState.CANCELING) {
			return TransitionResult.unchanged(current, "终态提交不受连接协调改变");
		}
		if (state == CountdownState.RECOVERY_WAIT) {
			CountdownState target = current.lifecycle().recoveryTarget().orElseThrow();
			if (target == CountdownState.COMPLETING || target == CountdownState.CANCELING) {
				long version = increment(current.lifecycle().stateVersion(), "倒计时状态版本溢出");
				if (version < 0L) {
					return TransitionResult.rejected("倒计时状态版本溢出");
				}
				return TransitionResult.accepted(
					current.withLifecycle(lifecycle(current, target, version, current.lifecycle().remainingTicks(), serverTick)),
					List.of(), Optional.empty(), List.of(), List.of(),
					"倒计时终态提交已从恢复等待中还原"
				);
			}
		}

		boolean requiredMissing = current.participants().stream()
			.filter(FrozenParticipant::required)
			.anyMatch(value -> !onlinePlayers.contains(value.playerId()));
		boolean hostMissing = current.hostId().filter(value -> !onlinePlayers.contains(value)).isPresent();
		DisconnectPolicy strongest = strongestPolicy(
			requiredMissing ? current.disconnectPolicies().requiredParticipant() : DisconnectPolicy.CONTINUE,
			hostMissing ? current.disconnectPolicies().host() : DisconnectPolicy.CONTINUE
		);
		if (strongest == DisconnectPolicy.CANCEL) {
			return beginCancel(current, serverTick, "连接协调触发取消策略");
		}
		CountdownState target = strongest == DisconnectPolicy.PAUSE
			? CountdownState.WAITING_FOR_PLAYERS
			: CountdownState.RUNNING;
		if (state == target && state != CountdownState.RECOVERY_WAIT) {
			return TransitionResult.unchanged(current, "连接协调未改变倒计时状态");
		}

		long version = increment(current.lifecycle().stateVersion(), "倒计时状态版本溢出");
		if (version < 0L) {
			return TransitionResult.rejected("倒计时状态版本溢出");
		}
		Lifecycle nextLifecycle = lifecycle(
			current,
			target,
			version,
			current.lifecycle().remainingTicks(),
			serverTick
		);
		Instance next = current.withLifecycle(nextLifecycle);
		if (state == CountdownState.WAITING_FOR_PLAYERS && target == CountdownState.RUNNING) {
			Activation activation = activateSlot(next, CallbackSlot.RESUME);
			if (!activation.accepted()) {
				return TransitionResult.rejected(activation.message());
			}
			return TransitionResult.accepted(
				activation.instance().orElseThrow(),
				activation.work(), Optional.empty(), List.of(), List.of(),
				"所需连接已恢复，倒计时等待 resume 回调"
			);
		}
		return TransitionResult.accepted(
			next,
			List.of(), Optional.empty(), List.of(), List.of(),
			target == CountdownState.RUNNING ? "倒计时连接协调完成" : "倒计时等待缺失玩家"
		);
	}

	/** Normal process restart always freezes the same instance in RECOVERY_WAIT. */
	public static TransitionResult enterRecoveryWait(
		final Instance current,
		final long serverTick,
		final String reason
	) {
		Objects.requireNonNull(reason, "reason");
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		if (current.lifecycle().state().terminal()) {
			return TransitionResult.unchanged(current, "终态倒计时不会恢复");
		}
		CountdownState recoveryTarget = current.lifecycle().state() == CountdownState.RECOVERY_WAIT
			? current.lifecycle().recoveryTarget().orElseThrow()
			: current.lifecycle().state();
		long version = increment(current.lifecycle().stateVersion(), "倒计时状态版本溢出");
		if (version < 0L) {
			return TransitionResult.rejected("倒计时状态版本溢出");
		}
		List<CallbackEntry> recovered = current.callbackLedger().entries().stream()
			.map(value -> value.status() == CallbackStatus.PREPARED
				? new CallbackEntry(
					value.key(),
					value.functionId(),
					value.required(),
					CallbackStatus.OUTCOME_UNKNOWN,
					value.attemptCount(),
					value.preparedAtServerTick(),
					Optional.of(serverTick),
					Optional.of("服务器重启前无法确认回调结果")
				)
				: value
			)
			.toList();
		Lifecycle lifecycle = new Lifecycle(
			CountdownState.RECOVERY_WAIT,
			version,
			current.lifecycle().remainingTicks(),
			serverTick,
			Optional.of(reason.strip().isBlank() ? "服务器重启恢复" : reason.strip()),
			Optional.of(recoveryTarget)
		);
		Instance next = current.withLifecycleAndCallbacks(lifecycle, new CallbackLedger(recovered));
		return TransitionResult.accepted(
			next,
			List.of(), Optional.empty(), List.of(), List.of(),
			"倒计时已进入恢复等待，离线时间未扣减"
		);
	}

	/** Explicit recovery alias used by integrations after their connection snapshot is complete. */
	public static TransitionResult resumeRecovery(
		final Instance current,
		final Set<UUID> onlinePlayers,
		final long serverTick
	) {
		if (current == null || current.lifecycle().state() != CountdownState.RECOVERY_WAIT) {
			return TransitionResult.rejected("倒计时当前不在 recovery_wait");
		}
		if (current.definition().closure().hasMissingContent()) {
			return TransitionResult.rejected("倒计时冻结闭包损坏，保持 recovery_wait 并等待安全取消");
		}
		return reconcileConnections(current, onlinePlayers, serverTick);
	}

	/** Reload is deliberately an identity operation over the frozen instance. */
	public static TransitionResult reload(final Instance current) {
		String error = inputError(current, current == null ? -1L : current.lifecycle().lastServerTickAnchor());
		return error == null
			? TransitionResult.unchanged(current, "活动倒计时继续使用原冻结 generation")
			: TransitionResult.rejected(error);
	}

	/** Begins the only supported host control: a normal, callback-accounted cancel. */
	public static TransitionResult requestCancel(
		final Instance current,
		final long serverTick,
		final String reason
	) {
		Objects.requireNonNull(reason, "reason");
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		return beginCancel(current, serverTick, reason);
	}

	/**
	 * Marks one pending callback PREPARED. FAILED and OUTCOME_UNKNOWN entries require an explicit
	 * retry; SUCCEEDED and already PREPARED entries can never be replayed.
	 */
	public static PrepareCallbackResult prepareCallback(
		final Instance current,
		final CallbackKey key,
		final long serverTick,
		final boolean explicitRetry
	) {
		Objects.requireNonNull(key, "key");
		String error = inputError(current, serverTick);
		if (error != null) {
			return PrepareCallbackResult.rejected(error);
		}
		CallbackEntry existing = current.callbackLedger().entries().stream()
			.filter(value -> value.key().equals(key))
			.findFirst()
			.orElse(null);
		if (existing == null) {
			return PrepareCallbackResult.rejected("倒计时回调账本中不存在该键");
		}
		if (existing.status() == CallbackStatus.SUCCEEDED) {
			return PrepareCallbackResult.rejected("成功回调禁止重放");
		}
		if (existing.status() == CallbackStatus.PREPARED) {
			return PrepareCallbackResult.rejected("回调结果尚未记账，禁止并发执行");
		}
		if (
			(existing.status() == CallbackStatus.FAILED
				|| existing.status() == CallbackStatus.OUTCOME_UNKNOWN)
				&& !explicitRetry
		) {
			return PrepareCallbackResult.rejected("失败或结果未知回调需要显式重试");
		}
		int attempt;
		try {
			attempt = Math.incrementExact(existing.attemptCount());
		} catch (ArithmeticException overflow) {
			return PrepareCallbackResult.rejected("倒计时回调尝试次数溢出");
		}
		CallbackEntry prepared = new CallbackEntry(
			existing.key(),
			existing.functionId(),
			existing.required(),
			CallbackStatus.PREPARED,
			attempt,
			Optional.of(serverTick),
			Optional.empty(),
			Optional.empty()
		);
		Instance next = replaceCallback(current, prepared);
		return new PrepareCallbackResult(
			Optional.of(next),
			Optional.of(new PreparedCallback(key, prepared.functionId(), prepared.required(), attempt)),
			"倒计时回调已准备，必须先持久化再执行"
		);
	}

	/** Records the outcome for the exact prepared attempt. */
	public static TransitionResult recordCallbackOutcome(
		final Instance current,
		final CallbackKey key,
		final int expectedAttempt,
		final boolean succeeded,
		final Optional<String> failureReason,
		final long serverTick
	) {
		Objects.requireNonNull(key, "key");
		Optional<String> normalizedFailureReason = Objects.requireNonNull(
			failureReason,
			"failureReason"
		).map(String::strip);
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		CallbackEntry existing = current.callbackLedger().entries().stream()
			.filter(value -> value.key().equals(key))
			.findFirst()
			.orElse(null);
		if (
			existing == null
				|| existing.status() != CallbackStatus.PREPARED
				|| existing.attemptCount() != expectedAttempt
		) {
			return TransitionResult.rejected("倒计时回调结果与已准备尝试不匹配");
		}
		if (!succeeded && normalizedFailureReason.filter(value -> !value.isBlank()).isEmpty()) {
			return TransitionResult.rejected("失败回调必须记录原因");
		}
		CallbackEntry outcome = new CallbackEntry(
			existing.key(),
			existing.functionId(),
			existing.required(),
			succeeded ? CallbackStatus.SUCCEEDED : CallbackStatus.FAILED,
			existing.attemptCount(),
			existing.preparedAtServerTick(),
			Optional.of(serverTick),
			succeeded ? Optional.empty() : normalizedFailureReason
		);
		Instance next;
		try {
			next = replaceCallback(current, outcome);
		} catch (IllegalArgumentException invalid) {
			return TransitionResult.rejected(invalid.getMessage());
		}
		return TransitionResult.accepted(
			next,
			List.of(), Optional.empty(), List.of(), List.of(),
			succeeded ? "倒计时回调成功结果已记账" : "倒计时回调失败结果已记账"
		);
	}

	/** Commits completion only after all due callbacks have settled and all required ones succeeded. */
	public static TransitionResult finalizeComplete(final Instance current, final long serverTick) {
		return finalizeTerminal(current, serverTick, CallbackSlot.COMPLETE, CountdownState.COMPLETED);
	}

	/** Commits cancellation only after all due callbacks have settled and all required ones succeeded. */
	public static TransitionResult finalizeCancel(final Instance current, final long serverTick) {
		return finalizeTerminal(current, serverTick, CallbackSlot.CANCEL, CountdownState.CANCELED);
	}

	/** Pure server-event gate. CAMERA, CHAT and ESC are unconditionally allowed by design. */
	public static RestrictionDecision restrictionDecision(
		final Instance current,
		final UUID playerId,
		final RestrictedAction action
	) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(action, "action");
		if (current == null || current.validated().error().isPresent()) {
			return new RestrictionDecision(false, "无有效倒计时限制实例");
		}
		if (action == RestrictedAction.CAMERA || action == RestrictedAction.CHAT || action == RestrictedAction.ESCAPE) {
			return new RestrictionDecision(false, "镜头、聊天与 ESC 始终允许");
		}
		boolean active = current.restrictionMarkers().stream()
			.anyMatch(value -> value.participantId().equals(playerId) && value.active());
		if (!active || current.lifecycle().state().terminal()) {
			return new RestrictionDecision(false, "玩家没有活动倒计时限制");
		}
		Restrictions value = current.restrictions();
		boolean blocked = switch (action) {
			case MOVEMENT -> value.freezeMovement();
			case ATTACK -> value.blockAttack();
			case INTERACT -> value.blockInteract();
			case ITEM_USE -> value.blockItemUse();
			case ITEM_DROP -> value.blockItemDrop();
			case ITEM_SWAP -> value.blockItemSwap();
			case INVENTORY -> value.blockInventory();
			case DAMAGE -> value.damageImmune();
			case CAMERA, CHAT, ESCAPE -> false;
		};
		return new RestrictionDecision(blocked, blocked ? "倒计时限制已拒绝该操作" : "数据包允许该操作");
	}

	/** Idempotently releases only the named marker; no game mode, effect or attribute is restored. */
	public static TransitionResult releaseRestrictions(
		final Instance current,
		final Set<UUID> participantIds
	) {
		Objects.requireNonNull(participantIds, "participantIds");
		String error = inputError(current, current == null ? -1L : current.lifecycle().lastServerTickAnchor());
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		List<UUID> released = current.restrictionMarkers().stream()
			.filter(RestrictionMarker::active)
			.filter(value -> participantIds.contains(value.participantId()))
			.map(RestrictionMarker::participantId)
			.toList();
		List<RestrictionMarker> markers = current.restrictionMarkers().stream()
			.map(value -> participantIds.contains(value.participantId()) ? value.released() : value)
			.toList();
		return TransitionResult.accepted(
			current.withRestrictionMarkers(markers),
			List.of(), Optional.empty(), List.of(), released,
			released.isEmpty() ? "倒计时限制已处于解除状态" : "倒计时限制标记已幂等解除"
		);
	}

	private static TransitionResult applyDisconnectPolicy(
		final Instance current,
		final DisconnectPolicy policy,
		final long serverTick,
		final String reason
	) {
		return switch (policy) {
			case CONTINUE -> TransitionResult.unchanged(current, reason + "，按 continue 继续");
			case CANCEL -> beginCancel(current, serverTick, reason + "，按 cancel 取消");
			case PAUSE -> beginPause(current, serverTick, reason + "，按 pause 等待");
		};
	}

	private static TransitionResult beginPause(
		final Instance current,
		final long serverTick,
		final String reason
	) {
		long version = increment(current.lifecycle().stateVersion(), "倒计时状态版本溢出");
		if (version < 0L) {
			return TransitionResult.rejected("倒计时状态版本溢出");
		}
		Instance waiting = current.withLifecycle(
			lifecycle(
				current,
				CountdownState.WAITING_FOR_PLAYERS,
				version,
				current.lifecycle().remainingTicks(),
				serverTick
			)
		);
		Activation activation = activateSlot(waiting, CallbackSlot.PAUSE);
		return activation.accepted()
			? TransitionResult.accepted(
				activation.instance().orElseThrow(), activation.work(), Optional.empty(),
				List.of(), List.of(), reason
			)
			: TransitionResult.rejected(activation.message());
	}

	private static TransitionResult beginCancel(
		final Instance current,
		final long serverTick,
		final String reason
	) {
		CountdownState state = current.lifecycle().state();
		if (state == CountdownState.CANCELING || state == CountdownState.CANCELED) {
			return TransitionResult.unchanged(current, "倒计时已经处于取消路径");
		}
		if (state == CountdownState.COMPLETING || state == CountdownState.COMPLETED) {
			return TransitionResult.rejected("倒计时完成路径已经提交，不能同时取消");
		}
		long version = increment(current.lifecycle().stateVersion(), "倒计时状态版本溢出");
		if (version < 0L) {
			return TransitionResult.rejected("倒计时状态版本溢出");
		}
		Instance canceling = current.withLifecycle(
			lifecycle(
				current,
				CountdownState.CANCELING,
				version,
				current.lifecycle().remainingTicks(),
				serverTick
			)
		);
		Activation activation = activateSlot(canceling, CallbackSlot.CANCEL);
		return activation.accepted()
			? TransitionResult.accepted(
				activation.instance().orElseThrow(), activation.work(), Optional.empty(),
				List.of(), List.of(), reason
			)
			: TransitionResult.rejected(activation.message());
	}

	private static TransitionResult finalizeTerminal(
		final Instance current,
		final long serverTick,
		final CallbackSlot slot,
		final CountdownState terminalState
	) {
		String error = inputError(current, serverTick);
		if (error != null) {
			return TransitionResult.rejected(error);
		}
		CountdownState requiredState = terminalState == CountdownState.COMPLETED
			? CountdownState.COMPLETING
			: CountdownState.CANCELING;
		if (current.lifecycle().state() == terminalState) {
			return TransitionResult.unchanged(current, "倒计时终态已经提交");
		}
		if (current.lifecycle().state() != requiredState) {
			return TransitionResult.rejected("倒计时不在对应终态提交路径");
		}
		List<CallbackEntry> due = latestSlotEntries(current, slot);
		if (due.stream().anyMatch(value -> value.status() == CallbackStatus.PENDING || value.status() == CallbackStatus.PREPARED)) {
			return TransitionResult.rejected("倒计时终态仍有未结算回调");
		}
		if (due.stream().anyMatch(value -> value.required() && value.status() != CallbackStatus.SUCCEEDED)) {
			return TransitionResult.rejected("倒计时终态的必需回调尚未全部成功");
		}
		long version = increment(current.lifecycle().stateVersion(), "倒计时状态版本溢出");
		if (version < 0L) {
			return TransitionResult.rejected("倒计时状态版本溢出");
		}
		List<UUID> released = current.restrictionMarkers().stream()
			.filter(RestrictionMarker::active)
			.map(RestrictionMarker::participantId)
			.toList();
		List<RestrictionMarker> markers = current.restrictionMarkers().stream()
			.map(RestrictionMarker::released)
			.toList();
		Lifecycle lifecycle = lifecycle(
			current,
			terminalState,
			version,
			current.lifecycle().remainingTicks(),
			serverTick
		);
		Instance next = current.withRestrictionMarkers(markers).withLifecycle(lifecycle);
		return TransitionResult.accepted(
			next,
			List.of(), Optional.empty(), List.of(), released,
			terminalState == CountdownState.COMPLETED ? "倒计时完成已提交" : "倒计时取消已提交"
		);
	}

	private static Activation activateSlot(final Instance current, final CallbackSlot slot) {
		List<CallbackDefinition> definitions = current.callbackDefinitions().stream()
			.filter(value -> value.slot() == slot)
			.toList();
		if (definitions.isEmpty()) {
			return new Activation(Optional.of(current), List.of(), "该回调槽为空");
		}
		long occurrence = current.callbackLedger().entries().stream()
			.filter(value -> value.key().slot() == slot)
			.mapToLong(value -> value.key().occurrence())
			.max()
			.orElse(-1L);
		try {
			occurrence = Math.incrementExact(occurrence);
		} catch (ArithmeticException overflow) {
			return Activation.rejected("倒计时回调发生序号溢出");
		}
		List<CallbackEntry> entries = new ArrayList<>(current.callbackLedger().entries());
		List<CallbackWork> work = new ArrayList<>();
		for (CallbackDefinition definition : definitions) {
			if (definition.scope() == CallbackScope.GLOBAL) {
				addCallback(current, definition, Optional.empty(), occurrence, entries, work);
			} else {
				for (FrozenParticipant participant : current.participants()) {
					addCallback(
						current,
						definition,
						Optional.of(participant.playerId()),
						occurrence,
						entries,
						work
					);
				}
			}
		}
		if (entries.size() > PersistedCountdown.MAX_CALLBACK_LEDGER_ENTRIES) {
			return Activation.rejected("倒计时回调账本超过硬上限");
		}
		Instance next = current.withCallbackLedger(new CallbackLedger(entries));
		return new Activation(Optional.of(next), List.copyOf(work), "倒计时回调槽已激活");
	}

	private static void addCallback(
		final Instance current,
		final CallbackDefinition definition,
		final Optional<UUID> participantId,
		final long occurrence,
		final List<CallbackEntry> entries,
		final List<CallbackWork> work
	) {
		CallbackKey key = new CallbackKey(
			current.countdownInstanceId(),
			definition.slot(),
			definition.callbackId(),
			definition.scope(),
			participantId,
			occurrence
		);
		entries.add(
			new CallbackEntry(
				key,
				definition.functionId(),
				definition.required(),
				CallbackStatus.PENDING,
				0,
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			)
		);
		work.add(new CallbackWork(key, definition.functionId(), definition.required()));
	}

	private static Instance replaceCallback(final Instance current, final CallbackEntry replacement) {
		List<CallbackEntry> entries = new ArrayList<>(current.callbackLedger().entries());
		boolean removed = entries.removeIf(value -> value.key().equals(replacement.key()));
		if (!removed) {
			throw new IllegalArgumentException("倒计时回调账本中不存在替换键");
		}
		entries.add(replacement);
		Instance next = current.withCallbackLedger(new CallbackLedger(entries));
		var validation = next.validated();
		if (validation.error().isPresent()) {
			throw new IllegalArgumentException(validation.error().orElseThrow().message());
		}
		return next;
	}

	private static boolean latestRequiredCallbacksSucceeded(
		final Instance current,
		final CallbackSlot slot
	) {
		List<CallbackEntry> entries = latestSlotEntries(current, slot);
		return entries.stream().noneMatch(value -> value.required() && value.status() != CallbackStatus.SUCCEEDED);
	}

	private static List<CallbackEntry> latestSlotEntries(
		final Instance current,
		final CallbackSlot slot
	) {
		Optional<Long> occurrence = current.callbackLedger().entries().stream()
			.filter(value -> value.key().slot() == slot)
			.map(value -> value.key().occurrence())
			.max(Long::compareTo);
		return occurrence.isEmpty()
			? List.of()
			: current.callbackLedger().entries().stream()
				.filter(value -> value.key().slot() == slot)
				.filter(value -> value.key().occurrence() == occurrence.orElseThrow())
				.toList();
	}

	private static Optional<CheckpointDefinition> lastMeaningful(
		final List<CheckpointDefinition> crossed
	) {
		return crossed.stream().min(
			java.util.Comparator.comparingLong(CheckpointDefinition::remainingTicks)
				.thenComparing(CheckpointDefinition::id)
		);
	}

	private static DisconnectPolicy strongestPolicy(
		final DisconnectPolicy first,
		final DisconnectPolicy second
	) {
		if (first == DisconnectPolicy.CANCEL || second == DisconnectPolicy.CANCEL) {
			return DisconnectPolicy.CANCEL;
		}
		return first == DisconnectPolicy.PAUSE || second == DisconnectPolicy.PAUSE
			? DisconnectPolicy.PAUSE
			: DisconnectPolicy.CONTINUE;
	}

	private static Lifecycle lifecycle(
		final Instance current,
		final CountdownState state,
		final long stateVersion,
		final long remainingTicks,
		final long serverTick
	) {
		return new Lifecycle(
			state,
			stateVersion,
			remainingTicks,
			serverTick,
			Optional.empty(),
			Optional.empty()
		);
	}

	private static long increment(final long value, final String ignoredMessage) {
		try {
			return Math.incrementExact(value);
		} catch (ArithmeticException overflow) {
			return -1L;
		}
	}

	private static String inputError(final Instance current, final long serverTick) {
		if (current == null) {
			return "倒计时实例不存在";
		}
		if (serverTick < 0L) {
			return "服务端 Tick 不能为负数";
		}
		var validation = current.validated();
		return validation.error().isPresent()
			? "倒计时实例无效：" + validation.error().orElseThrow().message()
			: null;
	}

	public record StartRequest(
		UUID gameInstanceId,
		UUID countdownInstanceId,
		FrozenDefinition definition,
		LaunchPlan launchPlan,
		long totalTicks,
		List<FrozenParticipant> participants,
		Optional<UUID> hostId,
		DisconnectPolicies disconnectPolicies,
		Restrictions restrictions,
		List<CheckpointDefinition> checkpoints,
		List<CallbackDefinition> callbacks,
		long serverTick
	) {
		public StartRequest {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			Objects.requireNonNull(definition, "definition");
			Objects.requireNonNull(launchPlan, "launchPlan");
			participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
			hostId = Objects.requireNonNull(hostId, "hostId");
			Objects.requireNonNull(disconnectPolicies, "disconnectPolicies");
			Objects.requireNonNull(restrictions, "restrictions");
			checkpoints = List.copyOf(Objects.requireNonNull(checkpoints, "checkpoints"));
			callbacks = List.copyOf(Objects.requireNonNull(callbacks, "callbacks"));
		}
	}

	public record TransitionResult(
		Optional<Instance> instance,
		List<CallbackWork> callbackWork,
		Optional<CheckpointDefinition> checkpointToProject,
		List<UUID> restrictionActivations,
		List<UUID> restrictionReleases,
		String message
	) {
		public TransitionResult {
			instance = Objects.requireNonNull(instance, "instance");
			callbackWork = List.copyOf(Objects.requireNonNull(callbackWork, "callbackWork"));
			checkpointToProject = Objects.requireNonNull(checkpointToProject, "checkpointToProject");
			restrictionActivations = List.copyOf(
				Objects.requireNonNull(restrictionActivations, "restrictionActivations")
			);
			restrictionReleases = List.copyOf(
				Objects.requireNonNull(restrictionReleases, "restrictionReleases")
			);
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank()) {
				throw new IllegalArgumentException("countdown transition message cannot be blank");
			}
		}

		public boolean accepted() {
			return this.instance.isPresent();
		}

		private static TransitionResult accepted(
			final Instance instance,
			final List<CallbackWork> callbackWork,
			final Optional<CheckpointDefinition> checkpoint,
			final List<UUID> activations,
			final List<UUID> releases,
			final String message
		) {
			return new TransitionResult(
				Optional.of(instance), callbackWork, checkpoint, activations, releases, message
			);
		}

		private static TransitionResult unchanged(final Instance instance, final String message) {
			return accepted(
				instance, List.of(), Optional.empty(), List.of(), List.of(), message
			);
		}

		private static TransitionResult rejected(final String message) {
			return new TransitionResult(
				Optional.empty(), List.of(), Optional.empty(), List.of(), List.of(), message
			);
		}
	}

	public record CallbackWork(CallbackKey key, Identifier functionId, boolean required) {
		public CallbackWork {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(functionId, "functionId");
		}
	}

	public record PreparedCallback(
		CallbackKey key,
		Identifier functionId,
		boolean required,
		int attempt
	) {
		public PreparedCallback {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(functionId, "functionId");
			if (attempt <= 0) {
				throw new IllegalArgumentException("prepared callback attempt must be positive");
			}
		}
	}

	public record PrepareCallbackResult(
		Optional<Instance> instance,
		Optional<PreparedCallback> callback,
		String message
	) {
		public PrepareCallbackResult {
			instance = Objects.requireNonNull(instance, "instance");
			callback = Objects.requireNonNull(callback, "callback");
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank() || instance.isPresent() != callback.isPresent()) {
				throw new IllegalArgumentException("invalid prepare callback result");
			}
		}

		public boolean prepared() {
			return this.callback.isPresent();
		}

		private static PrepareCallbackResult rejected(final String message) {
			return new PrepareCallbackResult(Optional.empty(), Optional.empty(), message);
		}
	}

	public record RestrictionDecision(boolean blocked, String reason) {
		public RestrictionDecision {
			reason = Objects.requireNonNull(reason, "reason").strip();
			if (reason.isBlank()) {
				throw new IllegalArgumentException("restriction decision reason cannot be blank");
			}
		}
	}

	public enum RestrictedAction {
		MOVEMENT,
		ATTACK,
		INTERACT,
		ITEM_USE,
		ITEM_DROP,
		ITEM_SWAP,
		INVENTORY,
		DAMAGE,
		CAMERA,
		CHAT,
		ESCAPE
	}

	private record Activation(
		Optional<Instance> instance,
		List<CallbackWork> work,
		String message
	) {
		private Activation {
			instance = Objects.requireNonNull(instance, "instance");
			work = List.copyOf(Objects.requireNonNull(work, "work"));
			message = Objects.requireNonNull(message, "message");
		}

		private boolean accepted() {
			return this.instance.isPresent();
		}

		private static Activation rejected(final String message) {
			return new Activation(Optional.empty(), List.of(), message);
		}
	}
}
