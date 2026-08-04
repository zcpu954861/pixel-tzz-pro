package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackLedger;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.PendingOfflineDelivery;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.ProgressState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.RestartMode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Snapshot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.TerminalStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Tombstone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure checkpoint and cold-start recovery boundary for the V3B message runtime.
 *
 * <p>The authority accepts and returns only stable {@link PersistedMessageRuntime} DTOs. It never
 * renders, sends packets, evaluates fields, resolves audiences, or invokes callbacks. In
 * particular, a runtime must execute the returned recovery actions explicitly instead of
 * deserializing private runtime objects directly.
 */
public final class MessageRuntimePersistenceAuthority {
	private static final Comparator<Instance> NEWEST_INSTANCE_FIRST = Comparator
		.comparingLong(Instance::updatedAtGameTick)
		.reversed()
		.thenComparing(Instance::instanceId);
	private static final Comparator<PendingOfflineDelivery> NEWEST_PENDING_FIRST = Comparator
		.comparingLong(PendingOfflineDelivery::queuedAtGameTick)
		.reversed()
		.thenComparing(PendingOfflineDelivery::instanceId)
		.thenComparing(PendingOfflineDelivery::recipientId);
	private static final Comparator<Tombstone> NEWEST_TOMBSTONE_FIRST = Comparator
		.comparingLong(Tombstone::terminalAtGameTick)
		.reversed()
		.thenComparing(Tombstone::instanceId);

	private MessageRuntimePersistenceAuthority() {
	}

	/**
	 * Produces one bounded, codec-valid checkpoint candidate.
	 *
	 * <p>Transient instances are deliberately omitted. If the hard active-instance bound is
	 * exceeded, the oldest instances become cancelled tombstones and are reported to the caller.
	 * Pending offline entries and tombstones keep their newest bounded suffix. Callback entries are
	 * never silently trimmed for a retained instance because that could replay game logic; an
	 * invalid callback ledger rejects the whole checkpoint.
	 */
	public static CheckpointResult checkpoint(final Snapshot runtimeState) {
		Objects.requireNonNull(runtimeState, "runtimeState");
		if (runtimeState.snapshotGameTick() < 0L || runtimeState.runtimeRevision() < 0L) {
			return CheckpointResult.rejected("消息运行时快照时钟无效");
		}

		List<Instance> transientInstances = runtimeState.instances().stream()
			.filter(value -> value.restart() == RestartMode.TRANSIENT)
			.toList();
		List<Instance> persistent = runtimeState.instances().stream()
			.filter(value -> value.restart() != RestartMode.TRANSIENT)
			.sorted(NEWEST_INSTANCE_FIRST)
			.toList();
		List<Instance> retained = take(persistent, PersistedMessageRuntime.HARD_MAX_INSTANCES);
		List<Instance> evicted = persistent.subList(retained.size(), persistent.size());
		Set<UUID> retainedIds = ids(retained);

		List<PendingOfflineDelivery> eligiblePending = runtimeState.pendingOffline().stream()
			.filter(value -> retainedIds.contains(value.instanceId()))
			.sorted(NEWEST_PENDING_FIRST)
			.toList();
		List<PendingOfflineDelivery> retainedPending = take(
			eligiblePending,
			PersistedMessageRuntime.HARD_MAX_PENDING_OFFLINE
		);
		List<PendingOfflineDelivery> evictedPending = new ArrayList<>();
		evictedPending.addAll(eligiblePending.subList(retainedPending.size(), eligiblePending.size()));
		runtimeState.pendingOffline().stream()
			.filter(value -> !retainedIds.contains(value.instanceId()))
			.forEach(evictedPending::add);

		List<Tombstone> tombstones = new ArrayList<>(runtimeState.tombstones());
		for (Instance value : evicted) {
			tombstones.add(
				new Tombstone(
					value.instanceId(),
					value.cueId(),
					TerminalStatus.CANCELLED,
					runtimeState.snapshotGameTick(),
					"超过消息运行时持久化实例上限，已安全取消"
				)
			);
		}
		List<Tombstone> retainedTombstones = newestUniqueTombstones(tombstones, retainedIds);

		CallbackLedger callbacks = runtimeState.callbackLedger();
		if (
			callbacks.capacity() < 1
				|| callbacks.capacity() > PersistedMessageRuntime.HARD_MAX_CALLBACKS
		) {
			return CheckpointResult.rejected("消息回调账本容量无效");
		}
		List<CallbackEntry> retainedCallbacks = callbacks.entries().stream()
			.filter(value -> retainedIds.contains(value.key().instanceId()))
			.toList();
		if (retainedCallbacks.size() > callbacks.capacity()) {
			return CheckpointResult.rejected("活动实例的回调账本超过容量，拒绝丢弃防重放证据");
		}

		Snapshot candidate = new Snapshot(
			runtimeState.gameInstanceId(),
			runtimeState.snapshotGameTick(),
			runtimeState.runtimeRevision(),
			retained,
			retainedPending,
			retainedTombstones,
			new CallbackLedger(callbacks.capacity(), retainedCallbacks)
		);
		var validation = candidate.validated();
		if (validation.error().isPresent()) {
			return CheckpointResult.rejected(
				"消息运行时快照无效：" + validation.error().orElseThrow().message()
			);
		}
		return new CheckpointResult(
			Optional.of(candidate),
			transientInstances.stream().map(Instance::instanceId).toList(),
			evicted.stream().map(Instance::instanceId).toList(),
			List.copyOf(evictedPending),
			Math.max(0, tombstones.size() - retainedTombstones.size()),
			"消息运行时检查点已生成"
		);
	}

	/**
	 * Converts a validated checkpoint into explicit cold-start work.
	 *
	 * <p>CONTINUE remains active and must be rebuilt at its saved progress. FINALIZE emits one
	 * static-final action; CANCEL emits one cancel action. Neither terminal action may invoke a
	 * callback. Crash-left PREPARED callbacks become OUTCOME_UNKNOWN before any continue action is
	 * exposed, so recovery can never replay a callback whose outcome is uncertain.
	 */
	public static RecoveryPlan recover(
		final Snapshot persisted,
		final UUID activeGameInstanceId,
		final long currentGameTick
	) {
		Objects.requireNonNull(persisted, "persisted");
		Objects.requireNonNull(activeGameInstanceId, "activeGameInstanceId");
		if (currentGameTick < 0L) {
			return RecoveryPlan.rejected("消息运行时恢复时钟无效");
		}
		var validation = persisted.validated();
		if (validation.error().isPresent()) {
			return RecoveryPlan.rejected(
				"消息运行时恢复快照无效：" + validation.error().orElseThrow().message()
			);
		}
		if (!persisted.gameInstanceId().equals(activeGameInstanceId)) {
			return new RecoveryPlan(
				Optional.of(Snapshot.empty(activeGameInstanceId, currentGameTick)),
				List.of(),
				List.of(),
				0,
				true,
				"活动游戏已变化，旧消息运行时已清空"
			);
		}

		long nextRevision;
		try {
			nextRevision = Math.addExact(persisted.runtimeRevision(), 1L);
		} catch (ArithmeticException overflow) {
			return RecoveryPlan.rejected("消息运行时恢复修订号溢出");
		}

		List<CallbackEntry> recoveredCallbacks = new ArrayList<>();
		int preparedRecovered = 0;
		for (CallbackEntry value : persisted.callbackLedger().entries()) {
			CallbackEntry recovered = value.recoverPrepared(currentGameTick);
			if (value.status() == CallbackStatus.PREPARED) {
				preparedRecovered++;
			}
			recoveredCallbacks.add(recovered);
		}

		List<Instance> continuing = new ArrayList<>();
		List<Instance> retained = new ArrayList<>();
		List<RecoveryAction> actions = new ArrayList<>();
		List<Tombstone> tombstones = new ArrayList<>(persisted.tombstones());
		for (Instance instance : persisted.instances()) {
			switch (instance.restart()) {
				case CONTINUE -> {
					continuing.add(instance);
					retained.add(instance);
					actions.add(RecoveryAction.restore(instance));
				}
				case FINALIZE -> {
					if (pendingFinalRecipients(instance).isEmpty()) {
						tombstones.add(
							terminal(instance, TerminalStatus.COMPLETED, currentGameTick)
						);
					} else {
						/*
						 * A recovered final is durable work, not a process-local notification. Keep
						 * the frozen instance until every original recipient has consumed it.
						 */
						retained.add(instance);
						actions.add(RecoveryAction.finalizeStatic(instance));
					}
				}
				case CANCEL -> {
					actions.add(RecoveryAction.cancel(instance));
					tombstones.add(terminal(instance, TerminalStatus.CANCELLED, currentGameTick));
				}
				case TRANSIENT -> throw new IllegalStateException("validated snapshots cannot contain transient instances");
			}
		}
		Set<UUID> continuingIds = ids(continuing);
		Set<UUID> retainedIds = ids(retained);
		List<PendingOfflineDelivery> expired = persisted.pendingOffline().stream()
			.filter(value -> continuingIds.contains(value.instanceId()))
			.filter(value -> value.expiresAtGameTick().filter(deadline -> deadline <= currentGameTick).isPresent())
			.toList();
		List<PendingOfflineDelivery> pending = persisted.pendingOffline().stream()
			.filter(value -> continuingIds.contains(value.instanceId()))
			.filter(value -> value.expiresAtGameTick().filter(deadline -> deadline <= currentGameTick).isEmpty())
			.toList();
		List<CallbackEntry> callbacks = recoveredCallbacks.stream()
			.filter(value -> continuingIds.contains(value.key().instanceId()))
			.toList();

		Snapshot candidate = new Snapshot(
			activeGameInstanceId,
			currentGameTick,
			nextRevision,
			retained,
			pending,
			newestUniqueTombstones(tombstones, retainedIds),
			new CallbackLedger(persisted.callbackLedger().capacity(), callbacks)
		);
		var nextValidation = candidate.validated();
		if (nextValidation.error().isPresent()) {
			return RecoveryPlan.rejected(
				"消息运行时恢复结果无效：" + nextValidation.error().orElseThrow().message()
			);
		}
		return new RecoveryPlan(
			Optional.of(candidate),
			List.copyOf(actions),
			List.copyOf(expired),
			preparedRecovered,
			false,
			"消息运行时恢复计划已生成"
		);
	}

	/**
	 * Durably consumes one {@code restart=finalize} projection before the integration layer sends
	 * it to a newly connected recipient.
	 *
	 * <p>Partially consumed instances remain in the ordinary bounded instance list. The last
	 * recipient retires the instance into a completed tombstone. Repeating the same consume request
	 * is idempotent and never asks the caller to project the final content twice.
	 */
	public static RecoveredFinalConsumption consumeRecoveredFinal(
		final Snapshot persisted,
		final UUID activeGameInstanceId,
		final UUID instanceId,
		final UUID recipientId,
		final long currentGameTick
	) {
		Objects.requireNonNull(persisted, "persisted");
		Objects.requireNonNull(activeGameInstanceId, "activeGameInstanceId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(recipientId, "recipientId");
		if (currentGameTick < persisted.snapshotGameTick()) {
			return RecoveredFinalConsumption.rejected(
				"恢复最终内容消费时钟早于现有检查点"
			);
		}
		var validation = persisted.validated();
		if (validation.error().isPresent()) {
			return RecoveredFinalConsumption.rejected(
				"恢复最终内容快照无效：" + validation.error().orElseThrow().message()
			);
		}
		if (!persisted.gameInstanceId().equals(activeGameInstanceId)) {
			return RecoveredFinalConsumption.rejected("活动游戏已变化");
		}

		Instance instance = persisted.instances().stream()
			.filter(value -> value.instanceId().equals(instanceId))
			.findFirst()
			.orElse(null);
		if (instance == null) {
			boolean completed = persisted.tombstones().stream().anyMatch(value ->
				value.instanceId().equals(instanceId)
					&& value.status() == TerminalStatus.COMPLETED
			);
			return completed
				? RecoveredFinalConsumption.unchanged(persisted, "最终内容已经消费")
				: RecoveredFinalConsumption.rejected("待消费的恢复最终实例不存在");
		}
		if (instance.restart() != RestartMode.FINALIZE) {
			return RecoveredFinalConsumption.rejected("实例不是 restart=finalize 恢复投影");
		}
		if (!instance.audience().cueTargets().contains(recipientId)) {
			return RecoveredFinalConsumption.rejected("玩家不在冻结受众中");
		}
		if (instance.progress().completedTargets().contains(recipientId)) {
			return RecoveredFinalConsumption.unchanged(persisted, "玩家已经消费最终内容");
		}

		List<UUID> completedTargets = new ArrayList<>(instance.progress().completedTargets());
		completedTargets.add(recipientId);
		long instanceRevision;
		long runtimeRevision;
		try {
			instanceRevision = Math.addExact(instance.progress().instanceRevision(), 1L);
			runtimeRevision = Math.addExact(persisted.runtimeRevision(), 1L);
		} catch (ArithmeticException overflow) {
			return RecoveredFinalConsumption.rejected("恢复最终内容修订号溢出");
		}
		ProgressState progress = new ProgressState(
			instance.progress().status(),
			instance.progress().resumeStatus(),
			instance.progress().cycle(),
			instance.progress().standardElapsedNanos(),
			instance.progress().emittedOccurrences(),
			completedTargets,
			instance.progress().eventOffsets(),
			instance.progress().forcedCompletion(),
			instanceRevision
		);
		Instance updated = new Instance(
			instance.instanceId(),
			instance.cueId(),
			instance.restart(),
			instance.frozenCue(),
			instance.context(),
			instance.canonicalParameters(),
			instance.audience(),
			instance.timing(),
			progress,
			instance.recipients(),
			instance.dedupeKey(),
			instance.refreshKey(),
			currentGameTick
		);
		boolean retired = pendingFinalRecipients(updated).isEmpty();
		List<Instance> instances = new ArrayList<>(persisted.instances());
		instances.removeIf(value -> value.instanceId().equals(instanceId));
		if (!retired) {
			instances.add(updated);
		}
		List<Tombstone> tombstones = new ArrayList<>(persisted.tombstones());
		if (retired) {
			tombstones.removeIf(value -> value.instanceId().equals(instanceId));
			tombstones.add(terminal(updated, TerminalStatus.COMPLETED, currentGameTick));
		}
		List<PendingOfflineDelivery> pending = persisted.pendingOffline().stream()
			.filter(value -> !retired || !value.instanceId().equals(instanceId))
			.toList();
		List<CallbackEntry> callbacks = persisted.callbackLedger().entries().stream()
			.filter(value -> !retired || !value.key().instanceId().equals(instanceId))
			.toList();
		Snapshot candidate = new Snapshot(
			persisted.gameInstanceId(),
			currentGameTick,
			runtimeRevision,
			instances,
			pending,
			newestUniqueTombstones(tombstones, ids(instances)),
			new CallbackLedger(persisted.callbackLedger().capacity(), callbacks)
		);
		var candidateValidation = candidate.validated();
		if (candidateValidation.error().isPresent()) {
			return RecoveredFinalConsumption.rejected(
				"恢复最终内容消费结果无效："
					+ candidateValidation.error().orElseThrow().message()
			);
		}
		return new RecoveredFinalConsumption(
			Optional.of(candidate),
			true,
			retired,
			"恢复最终内容消费已持久化"
		);
	}

	private static Set<UUID> pendingFinalRecipients(final Instance instance) {
		Set<UUID> pending = new HashSet<>(instance.audience().cueTargets());
		pending.removeAll(instance.progress().completedTargets());
		return Set.copyOf(pending);
	}

	private static Tombstone terminal(
		final Instance instance,
		final TerminalStatus status,
		final long gameTick
	) {
		return new Tombstone(
			instance.instanceId(),
			instance.cueId(),
			status,
			gameTick,
			status == TerminalStatus.COMPLETED
				? "服务器重启：按 restart=finalize 补全静态最终内容"
				: "服务器重启：按 restart=cancel 取消演出"
		);
	}

	private static List<Tombstone> newestUniqueTombstones(
		final List<Tombstone> values,
		final Set<UUID> activeIds
	) {
		Map<UUID, Tombstone> newest = new LinkedHashMap<>();
		values.stream()
			.filter(value -> !activeIds.contains(value.instanceId()))
			.sorted(NEWEST_TOMBSTONE_FIRST)
			.forEach(value -> newest.putIfAbsent(value.instanceId(), value));
		return newest.values().stream()
			.limit(PersistedMessageRuntime.HARD_MAX_TOMBSTONES)
			.toList();
	}

	private static Set<UUID> ids(final List<Instance> values) {
		Set<UUID> result = new HashSet<>();
		values.forEach(value -> result.add(value.instanceId()));
		return Set.copyOf(result);
	}

	private static <T> List<T> take(final List<T> values, final int limit) {
		return List.copyOf(values.subList(0, Math.min(values.size(), limit)));
	}

	public enum RecoveryActionType {
		RESTORE_CONTINUE,
		FINALIZE_STATIC,
		CANCEL
	}

	public record RecoveredFinalConsumption(
		Optional<Snapshot> snapshot,
		boolean deliveryRequired,
		boolean instanceRetired,
		String message
	) {
		public RecoveredFinalConsumption {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank() || snapshot.isEmpty() && (deliveryRequired || instanceRetired)) {
				throw new IllegalArgumentException("invalid recovered final consumption result");
			}
		}

		public boolean accepted() {
			return this.snapshot.isPresent();
		}

		private static RecoveredFinalConsumption unchanged(
			final Snapshot snapshot,
			final String message
		) {
			return new RecoveredFinalConsumption(Optional.of(snapshot), false, false, message);
		}

		private static RecoveredFinalConsumption rejected(final String message) {
			return new RecoveredFinalConsumption(Optional.empty(), false, false, message);
		}
	}

	/**
	 * Explicit runtime work. Past callbacks and past sounds must never be replayed by any action;
	 * FINALIZE_STATIC and CANCEL suppress callbacks altogether.
	 */
	public record RecoveryAction(RecoveryActionType type, Instance instance, String reason) {
		public RecoveryAction {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(instance, "instance");
			reason = Objects.requireNonNull(reason, "reason").strip();
			if (reason.isBlank()) {
				throw new IllegalArgumentException("recovery action reason cannot be blank");
			}
		}

		private static RecoveryAction restore(final Instance instance) {
			return new RecoveryAction(
				RecoveryActionType.RESTORE_CONTINUE,
				instance,
				"从冻结进度继续；不得重放已过去的声音或回调"
			);
		}

		private static RecoveryAction finalizeStatic(final Instance instance) {
			return new RecoveryAction(
				RecoveryActionType.FINALIZE_STATIC,
				instance,
				"仅投影冻结最终静态内容；不得执行回调"
			);
		}

		private static RecoveryAction cancel(final Instance instance) {
			return new RecoveryAction(
				RecoveryActionType.CANCEL,
				instance,
				"取消客户端演出并清理实例；不得执行回调"
			);
		}
	}

	public record CheckpointResult(
		Optional<Snapshot> snapshot,
		List<UUID> discardedTransientInstances,
		List<UUID> evictedPersistentInstances,
		List<PendingOfflineDelivery> evictedPendingOffline,
		int evictedTombstones,
		String message
	) {
		public CheckpointResult {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			discardedTransientInstances = List.copyOf(discardedTransientInstances);
			evictedPersistentInstances = List.copyOf(evictedPersistentInstances);
			evictedPendingOffline = List.copyOf(evictedPendingOffline);
			message = Objects.requireNonNull(message, "message").strip();
			if (evictedTombstones < 0 || message.isBlank()) {
				throw new IllegalArgumentException("invalid checkpoint result");
			}
		}

		public boolean accepted() {
			return this.snapshot.isPresent();
		}

		private static CheckpointResult rejected(final String message) {
			return new CheckpointResult(
				Optional.empty(),
				List.of(),
				List.of(),
				List.of(),
				0,
				message
			);
		}
	}

	public record RecoveryPlan(
		Optional<Snapshot> nextSnapshot,
		List<RecoveryAction> actions,
		List<PendingOfflineDelivery> expiredPendingOffline,
		int recoveredPreparedCallbacks,
		boolean scopeCleared,
		String message
	) {
		public RecoveryPlan {
			nextSnapshot = Objects.requireNonNull(nextSnapshot, "nextSnapshot");
			actions = List.copyOf(actions);
			expiredPendingOffline = List.copyOf(expiredPendingOffline);
			message = Objects.requireNonNull(message, "message").strip();
			if (recoveredPreparedCallbacks < 0 || message.isBlank()) {
				throw new IllegalArgumentException("invalid recovery plan");
			}
		}

		public boolean accepted() {
			return this.nextSnapshot.isPresent();
		}

		private static RecoveryPlan rejected(final String message) {
			return new RecoveryPlan(
				Optional.empty(),
				List.of(),
				List.of(),
				0,
				false,
				message
			);
		}
	}
}
