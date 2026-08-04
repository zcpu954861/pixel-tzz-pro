package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryAuthority.Status;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryAuthority.Transaction;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Runnable append, de-duplication, retention and game-scope contract check. */
public final class MessageHistoryAuthoritySelfCheck {
	private static final Identifier GAME = Identifier.parse("test:main");
	private static final Identifier PHASE = Identifier.parse("test:active");
	private static final Identifier CUE = Identifier.parse("test:notice");
	private static final UUID GAME_INSTANCE = uuid("game-instance");
	private static final UUID NEXT_GAME_INSTANCE = uuid("next-game-instance");
	private static final UUID VIEWER = uuid("viewer");
	private static final UUID OTHER_VIEWER = uuid("other-viewer");

	private MessageHistoryAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		WorldStateV4 empty = activeState(GAME_INSTANCE);
		MessageHistoryRecord first = record(VIEWER, "first", 10L);
		Transaction appended = MessageHistoryAuthority.append(empty, first);
		check(
			appended.status() == Status.APPENDED
				&& appended.appended()
				&& appended.state().messageHistory().equals(List.of(first)),
			"a valid per-viewer record must append atomically"
		);

		Transaction duplicate = MessageHistoryAuthority.append(appended.state(), first);
		check(
			duplicate.status() == Status.DUPLICATE
				&& !duplicate.appended()
				&& duplicate.state().equals(appended.state()),
			"the same cue occurrence must be idempotent for one viewer"
		);

		MessageHistoryRecord otherViewer = record(OTHER_VIEWER, "first", 10L);
		Transaction perViewer = MessageHistoryAuthority.append(appended.state(), otherViewer);
		check(
			perViewer.status() == Status.APPENDED
				&& perViewer.state().messageHistoryFor(VIEWER).size() == 1
				&& perViewer.state().messageHistoryFor(OTHER_VIEWER).size() == 1,
			"one occurrence may persist separately resolved content for each authorized viewer"
		);

		WorldStateV4 bounded = empty;
		for (int index = 0; index <= WorldStateV4.MAX_MESSAGE_HISTORY_PER_VIEWER; index++) {
			Transaction next = MessageHistoryAuthority.append(
				bounded,
				record(VIEWER, "bounded-" + index, index)
			);
			check(next.status() == Status.APPENDED, "bounded history append " + index);
			bounded = next.state();
		}
		check(
			bounded.messageHistoryFor(VIEWER).size()
				== WorldStateV4.MAX_MESSAGE_HISTORY_PER_VIEWER
				&& bounded.messageHistoryFor(VIEWER).stream()
					.noneMatch(value -> value.messageInstanceId().equals(uuid("bounded-0")))
				&& bounded.messageHistoryFor(VIEWER).stream()
					.anyMatch(value -> value.messageInstanceId().equals(
						uuid("bounded-" + WorldStateV4.MAX_MESSAGE_HISTORY_PER_VIEWER)
					)),
			"per-viewer retention must evict the oldest record while retaining the new one"
		);

		MessageHistoryRecord foreign = new MessageHistoryRecord(
			NEXT_GAME_INSTANCE,
			VIEWER,
			CUE,
			uuid("foreign"),
			Optional.empty(),
			1L,
			"异局消息",
			"不得写入",
			Map.of(),
			false
		);
		Transaction wrongScope = MessageHistoryAuthority.append(bounded, foreign);
		check(
			wrongScope.status() == Status.WRONG_GAME_INSTANCE
				&& wrongScope.state().equals(bounded),
			"a record from another game instance must be rejected without partial mutation"
		);
		var rolledBackBatch = MessageHistoryAuthority.appendAll(
			empty,
			List.of(record(VIEWER, "batch-valid", 20L), foreign)
		);
		check(
			!rolledBackBatch.accepted()
				&& rolledBackBatch.appendedRecords() == 0
				&& rolledBackBatch.state().equals(empty),
			"one invalid per-viewer record must roll back the complete occurrence-history batch"
		);
		var acceptedBatch = MessageHistoryAuthority.appendAll(
			empty,
			List.of(first, first, otherViewer)
		);
		check(
			acceptedBatch.accepted()
				&& acceptedBatch.appendedRecords() == 2
				&& acceptedBatch.duplicateRecords() == 1
				&& acceptedBatch.state().messageHistory().size() == 2,
			"one occurrence batch must commit unique viewer records once and tolerate retries"
		);

		WorldStateV4 handBuiltCrossScope = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			activeCore(NEXT_GAME_INSTANCE),
			List.of(),
			List.of(),
			List.of(first)
		);
		Transaction cleaned = MessageHistoryAuthority.reconcileScope(handBuiltCrossScope);
		check(
			cleaned.status() == Status.SCOPE_CLEANED
				&& cleaned.evictedRecords() == 1
				&& cleaned.state().messageHistory().isEmpty()
				&& cleaned.state().validated().error().isEmpty(),
			"scope reconciliation must atomically remove records from an older game instance"
		);

		check(
			appended.state().withCore(activeCore(NEXT_GAME_INSTANCE)).messageHistory().isEmpty(),
			"normal core rotation must clear message history without relying on runtime cleanup"
		);

		MessageHistoryRecord invalid = new MessageHistoryRecord(
			GAME_INSTANCE,
			VIEWER,
			CUE,
			uuid("invalid"),
			Optional.empty(),
			1L,
			"   ",
			"正文",
			Map.of(),
			false
		);
		Transaction invalidResult = MessageHistoryAuthority.append(empty, invalid);
		check(
			invalidResult.status() == Status.INVALID_RECORD
				&& invalidResult.state().equals(empty),
			"invalid resolved content must fail closed"
		);

		System.out.println(
			"MESSAGE_HISTORY_AUTHORITY_SELF_CHECK=PASS retained="
				+ bounded.messageHistory().size()
		);
	}

	private static WorldStateV4 activeState(final UUID gameInstanceId) {
		return new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			activeCore(gameInstanceId),
			List.of(),
			List.of()
		);
	}

	private static WorldStateV3 activeCore(final UUID gameInstanceId) {
		return WorldStateV3.initial().beginGameInstance(GAME, PHASE, gameInstanceId);
	}

	private static MessageHistoryRecord record(
		final UUID viewerId,
		final String occurrence,
		final long timestamp
	) {
		return new MessageHistoryRecord(
			GAME_INSTANCE,
			viewerId,
			CUE,
			uuid(occurrence),
			Optional.of(Identifier.parse("test:task")),
			timestamp,
			"『消息回顾』",
			"玩家 " + viewerId + " 的静态正文",
			Map.of("target_name", viewerId.toString()),
			false
		);
	}

	private static UUID uuid(final String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
