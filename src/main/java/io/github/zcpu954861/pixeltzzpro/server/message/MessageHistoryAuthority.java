package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure, server-authoritative transaction boundary for the optional V3B message-history ledger.
 *
 * <p>The caller must first resolve the configured history title, body, timestamp, task and saved
 * fields separately for every authorized viewer. This class deliberately accepts no audience or
 * resolver input; it can therefore never widen visibility after the occurrence has completed.
 */
public final class MessageHistoryAuthority {
	private static final Comparator<MessageHistoryRecord> OLDEST_FIRST = Comparator
		.comparingLong(MessageHistoryRecord::timestampTicks)
		.thenComparing(value -> value.cueId().toString())
		.thenComparing(MessageHistoryRecord::messageInstanceId)
		.thenComparing(MessageHistoryRecord::viewerId);

	private MessageHistoryAuthority() {
	}

	/**
	 * Appends one per-viewer record exactly once and keeps the new record while evicting the oldest
	 * records at the per-viewer and world bounds.
	 */
	public static Transaction append(
		final WorldStateV4 state,
		final MessageHistoryRecord record
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(record, "record");

		Transaction reconciled = reconcileScope(state);
		WorldStateV4 scoped = reconciled.state();
		Optional<UUID> activeScope = scoped.core().activeGameInstanceId();
		if (activeScope.isEmpty()) {
			return new Transaction(
				scoped,
				Status.NO_ACTIVE_GAME,
				reconciled.evictedRecords(),
				false,
				"当前没有可写入消息回顾的游戏实例"
			);
		}
		if (!activeScope.orElseThrow().equals(record.gameInstanceId())) {
			return new Transaction(
				scoped,
				Status.WRONG_GAME_INSTANCE,
				reconciled.evictedRecords(),
				false,
				"消息回顾不属于当前游戏实例"
			);
		}
		if (scoped.messageHistory().stream().anyMatch(value -> sameKey(value, record))) {
			return new Transaction(
				scoped,
				Status.DUPLICATE,
				reconciled.evictedRecords(),
				false,
				"该玩家的这次消息回顾已经写入"
			);
		}

		List<MessageHistoryRecord> retained = new ArrayList<>(scoped.messageHistory());
		int evicted = reconciled.evictedRecords();
		while (
			retained.stream().filter(value -> value.viewerId().equals(record.viewerId())).count()
				>= WorldStateV4.MAX_MESSAGE_HISTORY_PER_VIEWER
		) {
			if (!removeOldest(retained, record.viewerId())) {
				break;
			}
			evicted++;
		}
		while (retained.size() >= WorldStateV4.MAX_MESSAGE_HISTORY_RECORDS) {
			if (!removeOldest(retained, null)) {
				break;
			}
			evicted++;
		}
		retained.add(record);
		WorldStateV4 candidate = scoped.withMessageHistory(retained);
		var validation = candidate.validated();
		if (validation.error().isPresent()) {
			return new Transaction(
				scoped,
				Status.INVALID_RECORD,
				reconciled.evictedRecords(),
				false,
				"消息回顾记录无效：" + validation.error().orElseThrow().message()
			);
		}
		return new Transaction(
			candidate,
			Status.APPENDED,
			evicted,
			true,
			"消息回顾已写入"
		);
	}

	/**
	 * Applies all per-viewer records for one completed occurrence as one pure candidate. A malformed
	 * or wrong-scope record rolls back every append from this batch; exact duplicates remain a
	 * successful no-op. The caller can therefore publish the returned state with one commitV4.
	 */
	public static BatchTransaction appendAll(
		final WorldStateV4 state,
		final List<MessageHistoryRecord> records
	) {
		Objects.requireNonNull(state, "state");
		List<MessageHistoryRecord> requested = List.copyOf(records);
		Transaction reconciled = reconcileScope(state);
		WorldStateV4 working = reconciled.state();
		int appended = 0;
		int duplicates = 0;
		int evicted = reconciled.evictedRecords();
		for (MessageHistoryRecord record : requested) {
			Transaction result = append(working, record);
			if (result.status() == Status.APPENDED) {
				working = result.state();
				appended++;
				evicted += result.evictedRecords();
				continue;
			}
			if (result.status() == Status.DUPLICATE) {
				duplicates++;
				continue;
			}
			return new BatchTransaction(
				reconciled.state(),
				0,
				0,
				reconciled.evictedRecords(),
				Optional.of(result.status()),
				"消息回顾批量写入已回滚：" + result.message()
			);
		}
		return new BatchTransaction(
			working,
			appended,
			duplicates,
			evicted,
			Optional.empty(),
			appended == 0 ? "消息回顾无需重复写入" : "消息回顾已批量写入"
		);
	}

	/**
	 * Removes records outside the current active-game scope in one immutable state replacement.
	 * Normal {@link WorldStateV4#withCore} transitions already do this; the explicit transaction is
	 * also safe for recovery and integration boundaries that receive a hand-built candidate.
	 */
	public static Transaction reconcileScope(final WorldStateV4 state) {
		Objects.requireNonNull(state, "state");
		Optional<UUID> activeScope = state.core().activeGameInstanceId();
		List<MessageHistoryRecord> retained = activeScope
			.map(scope -> state.messageHistory().stream()
				.filter(value -> value.gameInstanceId().equals(scope))
				.toList())
			.orElse(List.of());
		int evicted = state.messageHistory().size() - retained.size();
		if (evicted == 0) {
			return new Transaction(state, Status.UNCHANGED, 0, false, "消息回顾范围无需清理");
		}
		return new Transaction(
			state.withMessageHistory(retained),
			Status.SCOPE_CLEANED,
			evicted,
			false,
			"已清理不属于当前游戏实例的消息回顾"
		);
	}

	private static boolean removeOldest(
		final List<MessageHistoryRecord> records,
		final UUID viewerId
	) {
		MessageHistoryRecord oldest = records.stream()
			.filter(value -> viewerId == null || value.viewerId().equals(viewerId))
			.min(OLDEST_FIRST)
			.orElse(null);
		return oldest != null && records.remove(oldest);
	}

	private static boolean sameKey(
		final MessageHistoryRecord left,
		final MessageHistoryRecord right
	) {
		return left.gameInstanceId().equals(right.gameInstanceId())
			&& left.viewerId().equals(right.viewerId())
			&& left.messageInstanceId().equals(right.messageInstanceId());
	}

	public enum Status {
		APPENDED,
		DUPLICATE,
		NO_ACTIVE_GAME,
		WRONG_GAME_INSTANCE,
		INVALID_RECORD,
		SCOPE_CLEANED,
		UNCHANGED
	}

	public record Transaction(
		WorldStateV4 state,
		Status status,
		int evictedRecords,
		boolean appended,
		String message
	) {
		public Transaction {
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			if (evictedRecords < 0 || message.isBlank()) {
				throw new IllegalArgumentException("invalid message-history transaction result");
			}
		}
	}

	public record BatchTransaction(
		WorldStateV4 state,
		int appendedRecords,
		int duplicateRecords,
		int evictedRecords,
		Optional<Status> rejectedStatus,
		String message
	) {
		public BatchTransaction {
			Objects.requireNonNull(state, "state");
			rejectedStatus = Objects.requireNonNull(rejectedStatus, "rejectedStatus");
			Objects.requireNonNull(message, "message");
			if (
				appendedRecords < 0
					|| duplicateRecords < 0
					|| evictedRecords < 0
					|| message.isBlank()
			) {
				throw new IllegalArgumentException("invalid message-history batch result");
			}
		}

		public boolean accepted() {
			return this.rejectedStatus.isEmpty();
		}
	}
}
