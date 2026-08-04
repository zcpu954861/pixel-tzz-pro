package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalProjector.Projection;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalProjector.SessionMetadata;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalProjector.ViewerSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecordState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContextDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Runnable, serverless checks for the ordinary-player terminal projection boundary.
 */
public final class PlayerTerminalProjectorSelfCheck {
	private static final Identifier GAME = Identifier.parse("test:main");
	private static final Identifier PHASE = Identifier.parse("test:active");
	private static final Identifier TASK = Identifier.parse("test:task");
	private static final Identifier HOME = Identifier.parse("test:home");
	private static final Identifier HISTORY_DETAIL = Identifier.parse("test:history_detail");
	private static final Identifier RUNNER = Identifier.parse("test:runner");
	private static final Identifier HUNTER = Identifier.parse("test:hunter");
	private static final Identifier ALIVE = Identifier.parse("test:alive");
	private static final Identifier SPAWN_FIELD = Identifier.parse("test:hunter_spawn");
	private static final Identifier RUNNER_GATE = Identifier.parse("test:runner_gate");
	private static final Identifier UNUSED_GATE = Identifier.parse("test:unused_gate");
	private static final String FROZEN_PREDICATE = """
		{"condition":"minecraft:random_chance","chance":0.25}
		""";
	private static final String RELOADED_PREDICATE = """
		{"condition":"minecraft:random_chance","chance":0.75}
		""";
	private static final String UNUSED_PREDICATE = """
		{"condition":"minecraft:random_chance","chance":0.5}
		""";
	private static final String REFERENCE_PREDICATE = """
		{
		  "condition": "minecraft:inverted",
		  "term": {
		    "condition": "minecraft:reference",
		    "name": "test:live_target"
		  }
		}
		""";
	private static final UUID GAME_INSTANCE =
		UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID TIMELINE_INSTANCE =
		UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID TASK_INSTANCE =
		UUID.fromString("00000000-0000-0000-0000-000000000103");
	private static final UUID RUNNER_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000201");
	private static final UUID HUNTER_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000202");
	private static final UUID SPAWN_RESERVATION_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000301");

	private PlayerTerminalProjectorSelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		check(
			PlayerTerminalProjector.formatHistoryTicks(0L).equals("00:00")
				&& PlayerTerminalProjector.formatHistoryTicks(1_199L).equals("00:59")
				&& PlayerTerminalProjector.formatHistoryTicks(1_200L).equals("01:00")
				&& PlayerTerminalProjector.formatHistoryTicks(72_000L).equals("1:00:00"),
			"history time formatting must remain stable across minute and hour boundaries"
		);
		List<Source> frozenSources = sources("冻结任务", "冻结说明");
		DefinitionSnapshot frozenDefinitions = compile(
			frozenSources,
			10L,
			FROZEN_PREDICATE
		);
		var frozen = TimelineSnapshotCompiler.freeze(
			frozenDefinitions,
			frozenDefinitions.games().get(GAME)
		);
		check(
			frozen.success(),
			"fixture timeline must freeze: " + frozen.code() + " / " + frozen.message()
		);
		DefinitionSnapshot reloadedDefinitions = compile(
			sources("重载后的任务", "重载后的说明"),
			11L,
			RELOADED_PREDICATE
		);
		check(
			frozen.compiled().orElseThrow().predicates().equals(Set.of(RUNNER_GATE))
				&& frozen.compiled()
					.orElseThrow()
					.predicateDocuments()
					.keySet()
					.equals(Set.of(RUNNER_GATE)),
			"timeline snapshot must retain only predicates referenced by its frozen game"
		);
		check(
			sameJson(
				frozen.compiled()
					.orElseThrow()
					.predicateDocuments()
					.get(RUNNER_GATE),
				FROZEN_PREDICATE
			)
				&& sameJson(
					reloadedDefinitions.predicateDocuments().get(RUNNER_GATE),
					RELOADED_PREDICATE
				),
			"reload must not replace the predicate document frozen for an active timeline"
		);
		DefinitionSnapshot missingPredicateDocuments = compileBase(
			frozenSources,
			12L
		);
		var missingPredicateFreeze = TimelineSnapshotCompiler.freeze(
			missingPredicateDocuments,
			missingPredicateDocuments.games().get(GAME)
		);
		check(
			!missingPredicateFreeze.success()
				&& missingPredicateFreeze.code().equals("snapshot_invalid")
				&& missingPredicateFreeze.message().contains("no retained document"),
			"freeze must reject a referenced predicate without a retained document"
		);
		DefinitionSnapshot referenceDefinitions = compile(
			frozenSources,
			13L,
			REFERENCE_PREDICATE
		);
		var referenceFreeze = TimelineSnapshotCompiler.freeze(
			referenceDefinitions,
			referenceDefinitions.games().get(GAME)
		);
		check(
			!referenceFreeze.success()
				&& referenceFreeze.code().equals("snapshot_invalid")
				&& referenceFreeze.message().contains("minecraft:reference"),
			"freeze must reject predicate documents that escape the frozen closure"
		);
		JsonObject frozenRoot = JsonParser.parseString(
			frozen.frozen().orElseThrow().normalizedJson()
		).getAsJsonObject();
		JsonObject incompleteRoot = frozenRoot.deepCopy();
		incompleteRoot.getAsJsonObject("predicate_documents")
			.remove(RUNNER_GATE.toString());
		var incompleteRestore = TimelineSnapshotCompiler.restore(
			GAME,
			WorldStateV2.FrozenDocument.of(incompleteRoot.toString())
		);
		check(
			!incompleteRestore.success()
				&& incompleteRestore.code().equals("snapshot_invalid"),
			"restore must reject incomplete predicate-document coverage"
		);
		JsonObject escapedRoot = frozenRoot.deepCopy();
		escapedRoot.getAsJsonObject("predicate_documents")
			.add(RUNNER_GATE.toString(), JsonParser.parseString(REFERENCE_PREDICATE));
		var escapedRestore = TimelineSnapshotCompiler.restore(
			GAME,
			WorldStateV2.FrozenDocument.of(escapedRoot.toString())
		);
		check(
			!escapedRestore.success()
				&& escapedRestore.message().contains("minecraft:reference"),
			"restore must reject a persisted predicate that escapes the frozen closure"
		);
		JsonObject legacyRoot = frozenRoot.deepCopy();
		legacyRoot.remove("predicate_documents");
		var legacyRestore = TimelineSnapshotCompiler.restore(
			GAME,
			WorldStateV2.FrozenDocument.of(legacyRoot.toString())
		);
		check(
			legacyRestore.success()
				&& legacyRestore.snapshot().orElseThrow().predicateDocuments().isEmpty(),
			"legacy format-v1 snapshots without predicate documents must remain restorable"
		);

		WorldStateV3 running = state(
			frozen.frozen().orElseThrow(),
			TimelineStatus.RUNNING,
			Optional.of(runningTask(ordinaryEvents())),
			List.of(),
			Optional.empty()
		);
		checkMessageHistoryProjection(reloadedDefinitions, running);
		Projection runnerProjection = project(
			reloadedDefinitions,
			running,
			RUNNER_ID,
			"Runner",
			10L
		);
		JsonObject runner = document(runnerProjection);
		check(
			runner.getAsJsonObject("task").get("name").getAsString().equals("冻结任务"),
			"active timeline must use the frozen task label after a data-pack reload"
		);
		check(
			runner.getAsJsonObject("personal").get("test:runner_task").getAsString().equals("冻结任务"),
			"runner-only grant must project the frozen task name"
		);
		check(
			runner.getAsJsonObject("personal")
				.get("test:formatted_description")
				.getAsString()
				.equals("【冻结说明】"),
			"string player-data format must apply exactly one {value} placeholder"
		);
		check(
			runner.getAsJsonObject("personal")
				.get("test:missing_result")
				.getAsString()
				.equals("尚无结果"),
			"authorized missing string data must use its masked fallback"
		);
		JsonObject runnerPersonalMetadata = runner.getAsJsonObject("personal_meta");
		JsonObject runnerViewer = runner.getAsJsonObject("viewer");
		check(
			runner.getAsJsonObject("personal")
					.get("test:profile/runner_role")
					.getAsString()
					.equals(RUNNER.toString())
				&& runnerPersonalMetadata
					.getAsJsonObject("test:profile/runner_role")
					.get("name")
					.getAsString()
					.equals("当前身份")
				&& BindingContextDocument.decode(runnerProjection.bindingDocument())
					.resolve("personal_meta/test:profile/runner_role/name")
					.filter(value -> value.getAsString().equals("当前身份"))
					.isPresent(),
			"personal metadata must preserve the full player-data id and declared display name"
		);
		check(
			runner.getAsJsonObject("personal")
					.get("test:profile/hunter_spawn")
					.getAsString()
					.equals("north_gate")
				&& runnerPersonalMetadata
					.getAsJsonObject("test:profile/hunter_spawn")
					.get("value_name")
					.getAsString()
					.equals("北门"),
			"locked exclusive choices must expose the data-pack display name without replacing the raw value"
		);
		check(
			!runner.getAsJsonObject("personal").has("test:hunter_role")
				&& !runnerPersonalMetadata.has("test:hunter_role"),
			"runner must not receive hunter-only personal data or metadata"
		);
		check(
			!runner.getAsJsonObject("personal").has("test:missing/team")
				&& !runnerPersonalMetadata.has("test:missing/team"),
			"a missing personal value must not leave orphan display metadata"
		);
		check(
			runnerViewer.get("initialized").getAsBoolean()
				&& runnerViewer.get("role").getAsString().equals(RUNNER.toString())
				&& runnerViewer.get("role_name").getAsString().equals("逃走者"),
			"an initialized viewer with an eligible role player-data grant must receive the localized role label"
		);
		check(
			!runnerViewer.has("life_state")
				&& !runnerViewer.has("life_state_name"),
			"the fixed terminal header must not receive life-state data without a matching player-data grant"
		);
		check(
			!runner.toString().contains("do-not-leak"),
			"viewer and personal roots must not expose unregistered persistent fields"
		);
		checkUninitializedViewerHeaderBoundary();
		check(
			runner.getAsJsonObject("history").get("count").getAsInt() == 1,
			"running timeline must release only the immediate event"
		);
		String visibleRecordKey = runner.getAsJsonObject("history")
			.getAsJsonArray("items")
			.get(0)
			.getAsJsonObject()
			.get("id")
			.getAsString();
		JsonObject visibleHistoryItem = runner.getAsJsonObject("history")
			.getAsJsonArray("items")
			.get(0)
			.getAsJsonObject();
		check(
			visibleRecordKey.matches("h1_[0-9a-f]{64}")
				&& !visibleRecordKey.contains(TASK_INSTANCE.toString())
				&& !visibleRecordKey.contains("checkpoint"),
			"history record keys must be opaque, versioned SHA-256 handles"
		);
		check(
			visibleHistoryItem.get("detail_available").getAsBoolean()
				&& !visibleHistoryItem.has("detail_page"),
			"history items must expose only detail availability, never the target page id"
		);
		check(
			visibleHistoryItem.get("game_time").getAsLong() == 100L
				&& visibleHistoryItem.get("game_time_ticks").getAsLong() == 100L
				&& visibleHistoryItem.get("game_time_text")
					.getAsString()
					.equals("开局后 00:05")
				&& visibleHistoryItem.get("task_time").getAsLong() == 20L
				&& visibleHistoryItem.get("task_time_text")
					.getAsString()
					.equals("任务开始后 00:01"),
			"history must preserve raw ticks while adding stable player-readable time text"
		);
		check(
			!runner.getAsJsonObject("detail").get("exists").getAsBoolean()
				&& runner.getAsJsonObject("detail")
					.get("status")
					.getAsString()
					.equals("empty"),
			"opening a history-capable terminal page without an applicable detail must use a neutral empty state"
		);
		SessionMetadata historyListSession = new SessionMetadata(
			UUID.nameUUIDFromBytes("detail-session".getBytes(StandardCharsets.UTF_8)),
			UUID.nameUUIDFromBytes("detail-list-page".getBytes(StandardCharsets.UTF_8)),
			HOME,
			Optional.empty(),
			10L,
			running.stateRevision(),
			true
		);
		check(
			PlayerTerminalProjector.resolveHistoryDetailDetached(
				reloadedDefinitions,
				running,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				historyListSession,
				PlayerTerminalProjector.PredicateGate.allowAll(),
				visibleRecordKey
			).map(PlayerTerminalProjector.HistoryDetailTarget::pageId)
				.filter(HISTORY_DETAIL::equals)
				.isPresent(),
			"visible history record must resolve its server-declared detail page"
		);
		Projection detailProjection = PlayerTerminalProjector.projectDetached(
			reloadedDefinitions,
			running,
			new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
			new SessionMetadata(
				historyListSession.sessionId(),
				UUID.nameUUIDFromBytes("detail-page".getBytes(StandardCharsets.UTF_8)),
				HISTORY_DETAIL,
				Optional.empty(),
				10L,
				running.stateRevision(),
				true,
				Optional.of(visibleRecordKey)
			),
			PlayerTerminalProjector.PredicateGate.allowAll()
		);
		JsonObject detail = document(detailProjection).getAsJsonObject("detail");
		check(
			detail.get("exists").getAsBoolean()
				&& detail.get("status").getAsString().equals("selected")
				&& detail.get("id").getAsString().equals(visibleRecordKey)
				&& detail.get("title").getAsString().equals("已触发检查点")
				&& detail.get("detail_available").getAsBoolean()
				&& !detail.has("detail_page")
				&& detail.getAsJsonObject("actor")
					.get("uuid")
					.getAsString()
					.equals(RUNNER_ID.toString()),
			"detail projection must reuse the exact authorized public history fields"
		);
		check(
			detail.getAsJsonArray("metadata")
					.asList()
					.stream()
					.map(JsonElement::getAsJsonObject)
					.anyMatch(value ->
						value.get("label").getAsString().equals("发生时间")
							&& value.get("value").getAsString().equals("开局后 00:05")
					)
				&& detail.getAsJsonArray("metadata")
					.asList()
					.stream()
					.map(JsonElement::getAsJsonObject)
					.anyMatch(value ->
						value.get("label").getAsString().equals("分数")
							&& value.get("value").getAsString().equals("7")
					),
			"detail metadata must be a bounded repeat-ready array derived only from public fields"
		);
		JsonObject defaultDetail = document(
			PlayerTerminalProjector.projectDetached(
				reloadedDefinitions,
				running,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				new SessionMetadata(
					historyListSession.sessionId(),
					UUID.nameUUIDFromBytes("default-detail".getBytes(StandardCharsets.UTF_8)),
					HISTORY_DETAIL,
					Optional.empty(),
					10L,
					running.stateRevision(),
					true
				),
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		).getAsJsonObject("detail");
		check(
			defaultDetail.get("exists").getAsBoolean()
				&& defaultDetail.get("status").getAsString().equals("defaulted")
				&& defaultDetail.get("id").getAsString().equals(visibleRecordKey),
			"first opening a master-detail history page must select its latest visible legal record"
		);
		JsonObject recoveredDetail = document(
			PlayerTerminalProjector.projectDetached(
				reloadedDefinitions,
				running,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				new SessionMetadata(
					historyListSession.sessionId(),
					UUID.nameUUIDFromBytes("recovered-detail".getBytes(StandardCharsets.UTF_8)),
					HISTORY_DETAIL,
					Optional.empty(),
					10L,
					running.stateRevision(),
					true,
					Optional.of("h1_" + "0".repeat(64)),
					true
				),
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		).getAsJsonObject("detail");
		check(
			recoveredDetail.get("exists").getAsBoolean()
				&& recoveredDetail.get("status").getAsString().equals("recovered")
				&& recoveredDetail.get("id").getAsString().equals(visibleRecordKey),
			"a still-authorized same-page history view must recover from a stale selection to another visible record"
		);
		JsonObject missingDetail = document(
			PlayerTerminalProjector.projectDetached(
				reloadedDefinitions,
				running,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				new SessionMetadata(
					historyListSession.sessionId(),
					UUID.nameUUIDFromBytes("missing-detail".getBytes(StandardCharsets.UTF_8)),
					HISTORY_DETAIL,
					Optional.empty(),
					10L,
					running.stateRevision(),
					true,
					Optional.of("missing/record/0")
				),
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		).getAsJsonObject("detail");
		check(
			!missingDetail.get("exists").getAsBoolean()
				&& missingDetail.get("status").getAsString().equals("unavailable")
				&& missingDetail.size() == 2,
			"a stale or unauthorized dedicated detail record must fail closed with an explicit status"
		);
		check(
			runner.getAsJsonObject("task").get("progress").getAsLong() == 7L
				&& runner.getAsJsonObject("task").getAsJsonArray("statistics").size() == 1,
			"task progress and statistics must come from explicit numeric grants"
		);
		List<Source> noTaskGrantSources = sources("隐藏任务", "不应投影")
			.stream()
			.filter(source -> source.type() != DefinitionType.PLAYER_DATA)
			.toList();
		DefinitionSnapshot noTaskGrantDefinitions = compile(
			noTaskGrantSources,
			14L,
			FROZEN_PREDICATE
		);
		var noTaskGrantFrozen = TimelineSnapshotCompiler.freeze(
			noTaskGrantDefinitions,
			noTaskGrantDefinitions.games().get(GAME)
		);
		check(
			noTaskGrantFrozen.success(),
			"hidden-task fixture must freeze: "
				+ noTaskGrantFrozen.code()
				+ " / "
				+ noTaskGrantFrozen.message()
		);
		WorldStateV3 hiddenTaskState = state(
			noTaskGrantFrozen.frozen().orElseThrow(),
			TimelineStatus.RUNNING,
			Optional.of(runningTask(ordinaryEvents())),
			List.of(),
			Optional.empty()
		);
		JsonObject hiddenTask = document(
			project(
				noTaskGrantDefinitions,
				hiddenTaskState,
				RUNNER_ID,
				"Runner",
				14L
			)
		).getAsJsonObject("task");
		check(
			!hiddenTask.get("exists").getAsBoolean() && hiddenTask.size() == 1,
			"a current task without an eligible task-source grant must not leak through task.exists"
		);

		Projection hunterProjection = project(
			reloadedDefinitions,
			running,
			HUNTER_ID,
			"Hunter",
			10L
		);
		JsonObject hunter = document(hunterProjection);
		check(
			!hunter.getAsJsonObject("personal").has("test:runner_task")
				&& hunter.getAsJsonObject("personal")
					.get("test:hunter_role")
					.getAsString()
					.equals(HUNTER.toString()),
			"runner and hunter audiences must produce different personal projections"
		);
		check(
			hunter.getAsJsonObject("history").get("count").getAsInt() == 0,
			"event history audience must be checked independently for each viewer"
		);
		check(
			PlayerTerminalProjector.resolveHistoryDetailDetached(
				reloadedDefinitions,
				running,
				new ViewerSnapshot(HUNTER_ID, "Hunter", true, false),
				historyListSession,
				PlayerTerminalProjector.PredicateGate.allowAll(),
				visibleRecordKey
			).isEmpty(),
			"a viewer outside the event audience must not resolve another player's detail page"
		);

		List<TaskEventRecord> manyEvents = new ArrayList<>();
		for (int index = 0; index < 90; index++) {
			manyEvents.add(
				new TaskEventRecord(
					index % 2 == 0 ? "checkpoint" : "game_end",
					100L + index,
					20L + index,
					TaskEventRecordState.RUNNING,
					Optional.of(new FrozenPlayerValue(RUNNER_ID, "Runner"))
				)
			);
		}
		TaskInstance settled = settledTask(manyEvents);
		WorldStateV3 completed = state(
			frozen.frozen().orElseThrow(),
			TimelineStatus.COMPLETED,
			Optional.empty(),
			List.of(settled),
			Optional.of(500L)
		);
		Projection bounded = project(
			reloadedDefinitions,
			completed,
			RUNNER_ID,
			"Runner",
			10L
		);
		JsonObject completedDocument = document(bounded);
		int historyCount = completedDocument.getAsJsonObject("history").get("count").getAsInt();
		check(
			historyCount > 0 && historyCount <= 64,
			"history projection must retain a bounded recent window"
		);
		check(
			bounded.summary().truncated()
				&& bounded.bindingDocument().length <= BindingContextDocument.MAX_UTF8_BYTES,
			"bounded projection must report truncation and stay inside the binding wire limit"
		);
		check(
			completedDocument.getAsJsonObject("history")
				.getAsJsonArray("items")
				.asList()
				.stream()
				.anyMatch(value -> value.getAsJsonObject().get("title").getAsString().equals("赛后公开")),
			"game-end history must become visible only after the timeline terminates"
		);
		DefinitionSnapshot taskHistoryDefinitions = compile(
			taskHistorySources("任务历史", "任务历史说明"),
			18L,
			FROZEN_PREDICATE
		);
		var taskHistoryFrozen = TimelineSnapshotCompiler.freeze(
			taskHistoryDefinitions,
			taskHistoryDefinitions.games().get(GAME)
		);
		check(
			taskHistoryFrozen.success(),
			"task-grouped history fixture must freeze: "
				+ taskHistoryFrozen.code()
				+ " / "
				+ taskHistoryFrozen.message()
		);
		WorldStateV3 taskHistoryState = state(
			taskHistoryFrozen.frozen().orElseThrow(),
			TimelineStatus.COMPLETED,
			Optional.empty(),
			List.of(settledTask(ordinaryEvents())),
			Optional.of(500L)
		);
		JsonObject taskHistory = document(
			project(
				taskHistoryDefinitions,
				taskHistoryState,
				RUNNER_ID,
				"Runner",
				18L
			)
		).getAsJsonObject("history");
		JsonObject taskSummary = taskHistory.getAsJsonArray("items")
			.get(0)
			.getAsJsonObject();
		check(
			taskHistory.get("source").getAsString().equals("tasks")
				&& taskHistory.get("count").getAsInt() == 1
				&& taskSummary.get("source").getAsString().equals("task")
				&& taskSummary.get("title").getAsString().equals("任务历史")
				&& taskSummary.get("event_count").getAsInt() == 2
				&& taskSummary.get("game_time_text")
					.getAsString()
					.equals("开局后 00:06")
				&& taskSummary.get("task_time_text")
					.getAsString()
					.equals("任务开始后 00:06")
				&& taskSummary.get("detail_available").getAsBoolean(),
			"task history mode must group only released viewer-visible events into one completed-task summary"
		);
		JsonObject taskDefaultDetail = document(
			PlayerTerminalProjector.projectDetached(
				taskHistoryDefinitions,
				taskHistoryState,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				new SessionMetadata(
					UUID.nameUUIDFromBytes("task-history-session".getBytes(StandardCharsets.UTF_8)),
					UUID.nameUUIDFromBytes("task-history-page".getBytes(StandardCharsets.UTF_8)),
					HISTORY_DETAIL,
					Optional.empty(),
					18L,
					taskHistoryState.stateRevision(),
					true
				),
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		).getAsJsonObject("detail");
		check(
			taskDefaultDetail.get("exists").getAsBoolean()
				&& taskDefaultDetail.get("status").getAsString().equals("defaulted")
				&& taskDefaultDetail.get("source").getAsString().equals("task")
				&& taskDefaultDetail.get("event_count").getAsInt() == 2
				&& !taskDefaultDetail.has("actor"),
			"task summaries must support same-page default detail without inventing a task-level actor"
		);
		JsonObject hunterTaskHistory = document(
			project(
				taskHistoryDefinitions,
				taskHistoryState,
				HUNTER_ID,
				"Hunter",
				18L
			)
		).getAsJsonObject("history");
		check(
			hunterTaskHistory.get("count").getAsInt() == 1
				&& hunterTaskHistory.getAsJsonArray("items")
					.get(0)
					.getAsJsonObject()
					.get("event_count")
					.getAsInt() == 1,
			"task grouping must preserve per-viewer event audience cropping instead of leaking a shared count"
		);
		JsonObject activeTaskHistory = document(
			project(
				taskHistoryDefinitions,
				state(
					taskHistoryFrozen.frozen().orElseThrow(),
					TimelineStatus.RUNNING,
					Optional.of(runningTask(ordinaryEvents())),
					List.of(),
					Optional.empty()
				),
				RUNNER_ID,
				"Runner",
				18L
			)
		).getAsJsonObject("history");
		check(
			activeTaskHistory.get("count").getAsInt() == 0,
			"task history mode must never expose the current or future task as a past task"
		);
		WorldStateV3 oldestOnly = state(
			frozen.frozen().orElseThrow(),
			TimelineStatus.COMPLETED,
			Optional.empty(),
			List.of(settledTask(List.of(manyEvents.get(0)))),
			Optional.of(500L)
		);
		String oldestRecordKey = document(
			project(
				reloadedDefinitions,
				oldestOnly,
				RUNNER_ID,
				"Runner",
				10L
			)
		).getAsJsonObject("history")
			.getAsJsonArray("items")
			.get(0)
			.getAsJsonObject()
			.get("id")
			.getAsString();
		check(
			completedDocument.getAsJsonObject("history")
				.getAsJsonArray("items")
				.asList()
				.stream()
				.noneMatch(value ->
					value.getAsJsonObject().get("id").getAsString().equals(oldestRecordKey)
				)
				&& PlayerTerminalProjector.resolveHistoryDetailDetached(
					reloadedDefinitions,
					completed,
					new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
					historyListSession,
					PlayerTerminalProjector.PredicateGate.allowAll(),
					oldestRecordKey
				).isEmpty(),
			"a record outside the most-recent 64 item window must not resolve a detail page"
		);

		String largeDetails = "X".repeat(2_048);
		String maximumName = "名".repeat(
			BindingContextDocument.MAX_PERSONAL_METADATA_NAME_LENGTH
		);
		List<Source> budgetSources = new ArrayList<>(
			sources("预算任务", largeDetails)
		);
		for (int index = 0; index < 243; index++) {
			budgetSources.add(
				playerData(
					"budget/" + String.format(java.util.Locale.ROOT, "%03d", index),
					"{\"type\":\"role\"}",
					"{\"roles\":[\"test:runner\"]}",
					",\"name\":{\"text\":\"" + maximumName + "\"}"
				)
			);
		}
		DefinitionSnapshot budgetDefinitions = compile(
			budgetSources,
			15L,
			FROZEN_PREDICATE
		);
		var budgetFrozen = TimelineSnapshotCompiler.freeze(
			budgetDefinitions,
			budgetDefinitions.games().get(GAME)
		);
		check(
			budgetFrozen.success(),
			"budget fixture must freeze: "
				+ budgetFrozen.code()
				+ " / "
				+ budgetFrozen.message()
		);
		List<TaskEventRecord> budgetEvents = new ArrayList<>();
		for (int index = 0; index < 30; index++) {
			budgetEvents.add(
				new TaskEventRecord(
					"game_end",
					100L + index,
					20L + index,
					TaskEventRecordState.RUNNING,
					Optional.of(new FrozenPlayerValue(RUNNER_ID, "Runner"))
				)
			);
		}
		budgetEvents.add(
			new TaskEventRecord(
				"checkpoint",
				200L,
				120L,
				TaskEventRecordState.RUNNING,
				Optional.of(new FrozenPlayerValue(RUNNER_ID, "Runner"))
			)
		);
		WorldStateV3 budgetRunning = state(
			budgetFrozen.frozen().orElseThrow(),
			TimelineStatus.RUNNING,
			Optional.of(runningTask(budgetEvents)),
			List.of(),
			Optional.empty()
		);
		JsonObject budgetRunningHistory = document(
			PlayerTerminalProjector.projectDetached(
				budgetDefinitions,
				budgetRunning,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				new SessionMetadata(
					UUID.nameUUIDFromBytes("budget-running-session".getBytes(StandardCharsets.UTF_8)),
					UUID.nameUUIDFromBytes("budget-running-page".getBytes(StandardCharsets.UTF_8)),
					HISTORY_DETAIL,
					Optional.empty(),
					15L,
					budgetRunning.stateRevision(),
					true
				),
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		);
		String budgetTargetKey = budgetRunningHistory.getAsJsonObject("history")
			.getAsJsonArray("items")
			.get(0)
			.getAsJsonObject()
			.get("id")
			.getAsString();
		WorldStateV3 budgetCompleted = state(
			budgetFrozen.frozen().orElseThrow(),
			TimelineStatus.COMPLETED,
			Optional.empty(),
			List.of(settledTask(budgetEvents)),
			Optional.of(500L)
		);
		Projection budgetTaskPage = project(
			budgetDefinitions,
			budgetCompleted,
			RUNNER_ID,
			"Runner",
			15L
		);
		JsonObject budgetTaskDocument = document(budgetTaskPage);
		SessionMetadata budgetTaskSession = new SessionMetadata(
			UUID.nameUUIDFromBytes("budget-detail-session".getBytes(StandardCharsets.UTF_8)),
			UUID.nameUUIDFromBytes("budget-list-page".getBytes(StandardCharsets.UTF_8)),
			HOME,
			Optional.empty(),
			15L,
			budgetCompleted.stateRevision(),
			true
		);
		check(
			budgetTaskPage.summary().truncated()
				&& budgetTaskDocument.getAsJsonObject("personal").has("test:budget/242")
				&& budgetTaskDocument.getAsJsonObject("history")
					.get("count")
					.getAsInt() < budgetEvents.size()
				&& PlayerTerminalProjector.resolveHistoryDetailDetached(
					budgetDefinitions,
					budgetCompleted,
					new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
					budgetTaskSession,
					PlayerTerminalProjector.PredicateGate.allowAll(),
					budgetTargetKey
				).isEmpty(),
			"an ordinary task page must keep its task/personal priority instead of donating shared budget to history"
		);

		SessionMetadata budgetDefaultSession = new SessionMetadata(
			UUID.nameUUIDFromBytes("budget-default-session".getBytes(StandardCharsets.UTF_8)),
			UUID.nameUUIDFromBytes("budget-default-page".getBytes(StandardCharsets.UTF_8)),
			HISTORY_DETAIL,
			Optional.empty(),
			15L,
			budgetCompleted.stateRevision(),
			true
		);
		Projection budgetDefaultProjection = PlayerTerminalProjector.projectDetached(
			budgetDefinitions,
			budgetCompleted,
			new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
			budgetDefaultSession,
			PlayerTerminalProjector.PredicateGate.allowAll()
		);
		JsonObject budgetDefaultDocument = document(budgetDefaultProjection);
		JsonArray budgetHistoryItems = budgetDefaultDocument.getAsJsonObject("history")
			.getAsJsonArray("items");
		JsonObject budgetDefaultDetail =
			budgetDefaultDocument.getAsJsonObject("detail");
		check(
			budgetDefaultProjection.summary().truncated()
				&& budgetHistoryItems.size() > 0
				&& budgetHistoryItems.size() < budgetEvents.size()
				&& historyIsChronological(budgetHistoryItems)
				&& budgetHistoryItems.get(budgetHistoryItems.size() - 1)
					.getAsJsonObject()
					.get("id")
					.getAsString()
					.equals(budgetTargetKey)
				&& budgetDefaultDetail.get("exists").getAsBoolean()
				&& budgetDefaultDetail.get("status").getAsString().equals("defaulted")
				&& budgetDefaultDetail.get("id").getAsString().equals(budgetTargetKey),
			"history eviction must keep the newest records, preserve chronological output, and atomically retain the default detail"
		);
		check(
			PlayerTerminalProjector.resolveHistoryDetailDetached(
				budgetDefinitions,
				budgetCompleted,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				budgetDefaultSession,
				PlayerTerminalProjector.PredicateGate.allowAll(),
				budgetTargetKey
			).map(PlayerTerminalProjector.HistoryDetailTarget::pageId)
				.filter(HISTORY_DETAIL::equals)
				.isPresent(),
			"a record retained by history-page priority must remain resolvable"
		);

		JsonObject budgetRecoveredDocument = document(
			PlayerTerminalProjector.projectDetached(
				budgetDefinitions,
				budgetCompleted,
				new ViewerSnapshot(RUNNER_ID, "Runner", true, false),
				new SessionMetadata(
					budgetDefaultSession.sessionId(),
					UUID.nameUUIDFromBytes("budget-recovered-page".getBytes(StandardCharsets.UTF_8)),
					HISTORY_DETAIL,
					Optional.empty(),
					15L,
					budgetCompleted.stateRevision(),
					true,
					Optional.of("h1_" + "0".repeat(64)),
					true
				),
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		);
		JsonObject budgetRecoveredDetail =
			budgetRecoveredDocument.getAsJsonObject("detail");
		check(
			budgetRecoveredDetail.get("exists").getAsBoolean()
				&& budgetRecoveredDetail.get("status").getAsString().equals("recovered")
				&& budgetRecoveredDetail.get("id").getAsString().equals(budgetTargetKey)
				&& budgetRecoveredDocument.getAsJsonObject("history")
					.getAsJsonArray("items")
					.asList()
					.stream()
					.anyMatch(value ->
						value.getAsJsonObject()
							.get("id")
							.getAsString()
							.equals(budgetTargetKey)
					),
			"history recovery must atomically retain the recovered newest item and its detail under shared-budget pressure"
		);

		List<Source> personalBudgetSources = new ArrayList<>(
			sources("个人数据预算", "个人数据预算说明")
		);
		for (int index = 0; index < 243; index++) {
			personalBudgetSources.add(
				playerData(
					"budget/" + String.format(java.util.Locale.ROOT, "%03d", index),
					"{\"type\":\"role\"}",
					"{\"roles\":[\"test:runner\"]}",
					",\"name\":{\"text\":\"" + maximumName + "\"}"
				)
			);
		}
		DefinitionSnapshot personalBudgetDefinitions = compile(
			personalBudgetSources,
			16L,
			FROZEN_PREDICATE
		);
		var personalBudgetFrozen = TimelineSnapshotCompiler.freeze(
			personalBudgetDefinitions,
			personalBudgetDefinitions.games().get(GAME)
		);
		check(
			personalBudgetFrozen.success(),
			"personal metadata budget fixture must freeze: "
				+ personalBudgetFrozen.code()
				+ " / "
				+ personalBudgetFrozen.message()
		);
		WorldStateV3 personalBudgetState = state(
			personalBudgetFrozen.frozen().orElseThrow(),
			TimelineStatus.RUNNING,
			Optional.of(runningTask(ordinaryEvents())),
			List.of(),
			Optional.empty()
		);
		Projection personalBudgetProjection = project(
			personalBudgetDefinitions,
			personalBudgetState,
			RUNNER_ID,
			"Runner",
			16L
		);
		JsonObject personalBudgetDocument = document(personalBudgetProjection);
		JsonObject budgetPersonal = personalBudgetDocument.getAsJsonObject("personal");
		JsonObject budgetPersonalMetadata =
			personalBudgetDocument.getAsJsonObject("personal_meta");
		check(
			budgetPersonal.has("test:budget/242")
				&& !budgetPersonalMetadata.has("test:budget/242")
				&& budgetPersonalMetadata.keySet()
					.stream()
					.allMatch(budgetPersonal::has)
				&& personalBudgetProjection.summary().truncated(),
			"metadata must consume budget only after real values and never outlive a value"
		);

		Projection repeated = project(
			reloadedDefinitions,
			running,
			RUNNER_ID,
			"Runner",
			10L
		);
		check(
			runnerProjection.summary().equals(repeated.summary()),
			"unchanged authority inputs must produce an equality-stable projection summary"
		);
		check(
			document(repeated)
				.getAsJsonObject("history")
				.getAsJsonArray("items")
				.get(0)
				.getAsJsonObject()
				.get("id")
				.getAsString()
				.equals(visibleRecordKey),
			"the same history record must keep the same opaque handle across projections"
		);
		check(
			runnerProjection.bindingDocument().length
				== runnerProjection.summary().utf8Bytes(),
			"summary byte count must describe the exact wire document"
		);
		System.out.println(
			"PLAYER_TERMINAL_PROJECTOR_SELF_CHECK=PASS"
				+ " bytes="
				+ runnerProjection.summary().utf8Bytes()
				+ " bounded_history="
				+ historyCount
		);
	}

	private static void checkUninitializedViewerHeaderBoundary() {
		DefinitionSnapshot definitions = compile(
			sourcesWithPendingRunnerInitialization(),
			17L,
			FROZEN_PREDICATE
		);
		var frozen = TimelineSnapshotCompiler.freeze(
			definitions,
			definitions.games().get(GAME)
		);
		check(
			frozen.success(),
			"uninitialized viewer fixture must freeze: "
				+ frozen.code()
				+ " / "
				+ frozen.message()
		);
		WorldStateV3 pending = state(
			frozen.frozen().orElseThrow(),
			TimelineStatus.RUNNING,
			Optional.of(runningTask(ordinaryEvents())),
			List.of(),
			Optional.empty()
		);
		JsonObject viewer = document(
			project(definitions, pending, RUNNER_ID, "Runner", 17L)
		).getAsJsonObject("viewer");
		check(
			!viewer.get("initialized").getAsBoolean()
				&& !viewer.has("role")
				&& !viewer.has("role_name")
				&& !viewer.has("life_state")
				&& !viewer.has("life_state_name"),
			"an uninitialized viewer must not leak role or life-state identity even when both player-data grants are eligible"
		);
	}

	private static void checkMessageHistoryProjection(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 running
	) {
		UUID firstInstance = UUID.nameUUIDFromBytes(
			"history-first".getBytes(StandardCharsets.UTF_8)
		);
		UUID latestInstance = UUID.nameUUIDFromBytes(
			"history-latest".getBytes(StandardCharsets.UTF_8)
		);
		MessageHistoryRecord first = new MessageHistoryRecord(
			GAME_INSTANCE,
			RUNNER_ID,
			Identifier.parse("test:opening_notice"),
			firstInstance,
			Optional.of(TASK),
			105L,
			"『较早消息』",
			"这是只为逃走者冻结的较早正文。",
			Map.of("target_name", "Runner"),
			false
		);
		MessageHistoryRecord latest = new MessageHistoryRecord(
			GAME_INSTANCE,
			RUNNER_ID,
			Identifier.parse("test:latest_notice"),
			latestInstance,
			Optional.of(TASK),
			130L,
			"『最新消息』",
			"这是默认选中的静态正文。",
			Map.of("live_score", "7"),
			true
		);
		MessageHistoryRecord otherViewer = new MessageHistoryRecord(
			GAME_INSTANCE,
			HUNTER_ID,
			Identifier.parse("test:hunter_secret"),
			UUID.nameUUIDFromBytes("hunter-secret".getBytes(StandardCharsets.UTF_8)),
			Optional.empty(),
			125L,
			"猎人私有标题",
			"逃走者绝不能看到这段正文",
			Map.of("secret", "hidden"),
			false
		);
		WorldStateV4 root = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			running,
			List.of(),
			List.of(),
			List.of(otherViewer, latest, first)
		);
		ViewerSnapshot viewer = new ViewerSnapshot(RUNNER_ID, "Runner", true, false);
		SessionMetadata session = new SessionMetadata(
			UUID.nameUUIDFromBytes("history-session".getBytes(StandardCharsets.UTF_8)),
			UUID.nameUUIDFromBytes("history-page".getBytes(StandardCharsets.UTF_8)),
			HISTORY_DETAIL,
			Optional.empty(),
			10L,
			running.stateRevision(),
			true
		);
		Projection projection = PlayerTerminalProjector.projectDetached(
			liveDefinitions,
			root,
			viewer,
			session,
			PlayerTerminalProjector.PredicateGate.allowAll()
		);
		JsonObject projected = document(projection);
		JsonArray items = projected.getAsJsonObject("history").getAsJsonArray("items");
		check(
			items.asList().stream().filter(value ->
				value.getAsJsonObject().get("source").getAsString().equals("message")
			).count() == 2
				&& items.asList().stream().anyMatch(value ->
					value.getAsJsonObject().get("source").getAsString().equals("event")
				)
				&& historyIsChronological(items),
			"V4 projection must merge the authorized viewer's message records with task/event history"
		);
		check(
			items.asList().stream().noneMatch(value ->
				value.getAsJsonObject().toString().contains("猎人私有标题")
					|| value.getAsJsonObject().toString().contains("hidden")
			),
			"a viewer projection must never contain another recipient's resolved message history"
		);
		JsonObject detail = projected.getAsJsonObject("detail");
		check(
			detail.get("exists").getAsBoolean()
				&& detail.get("status").getAsString().equals("defaulted")
				&& detail.get("title").getAsString().equals("『最新消息』")
				&& detail.getAsJsonObject("saved_fields").get("live_score").getAsString().equals("7")
				&& detail.get("replay_allowed").getAsBoolean(),
			"an empty selection must default to the latest authorized message on the current history page"
		);
		String recordKey = detail.get("id").getAsString();
		check(
			PlayerTerminalProjector.resolveHistoryDetailDetached(
				liveDefinitions,
				root,
				viewer,
				session,
				PlayerTerminalProjector.PredicateGate.allowAll(),
				recordKey
			).map(PlayerTerminalProjector.HistoryDetailTarget::pageId)
				.filter(HISTORY_DETAIL::equals)
				.isPresent(),
			"message history details must route back into the current authorized history page"
		);
		check(
			PlayerTerminalProjector.resolveMessageHistoryReplayDetached(
				liveDefinitions,
				root,
				viewer,
				session,
				PlayerTerminalProjector.PredicateGate.allowAll(),
				recordKey
			).filter(latest::equals).isPresent(),
			"an explicitly replayable message record must resolve only for its authorized viewer and current game"
		);
		String nonReplayRecordKey = items.asList()
			.stream()
			.map(JsonElement::getAsJsonObject)
			.filter(value ->
				value.get("title").getAsString().equals("『较早消息』")
			)
			.map(value -> value.get("id").getAsString())
			.findFirst()
			.orElseThrow();
		check(
			PlayerTerminalProjector.resolveMessageHistoryReplayDetached(
				liveDefinitions,
				root,
				viewer,
				session,
				PlayerTerminalProjector.PredicateGate.allowAll(),
				nonReplayRecordKey
			).isEmpty(),
			"a visible history record without replay_allowed must remain non-replayable"
		);

		JsonObject legacy = document(
			PlayerTerminalProjector.projectDetached(
				liveDefinitions,
				running,
				viewer,
				session,
				PlayerTerminalProjector.PredicateGate.allowAll()
			)
		);
		check(
			legacy.getAsJsonObject("history")
				.getAsJsonArray("items")
				.asList()
				.stream()
				.noneMatch(value -> value.getAsJsonObject().get("source").getAsString().equals("message")),
			"the retained V3 overload must not invent or inherit V4 message history"
		);
	}

	private static Projection project(
		final DefinitionSnapshot live,
		final WorldStateV3 state,
		final UUID playerId,
		final String name,
		final long generation
	) {
		return PlayerTerminalProjector.projectDetached(
			live,
			state,
			new ViewerSnapshot(playerId, name, true, false),
			new SessionMetadata(
				UUID.nameUUIDFromBytes(("session-" + playerId).getBytes(StandardCharsets.UTF_8)),
				UUID.nameUUIDFromBytes(("page-" + playerId).getBytes(StandardCharsets.UTF_8)),
				HOME,
				Optional.empty(),
				generation,
				state.stateRevision(),
				true
			),
			PlayerTerminalProjector.PredicateGate.allowAll()
		);
	}

	private static JsonObject document(final Projection projection) {
		byte[] encoded = projection.bindingDocument();
		BindingContextDocument.decode(encoded);
		return JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static boolean historyIsChronological(final JsonArray items) {
		long previous = Long.MIN_VALUE;
		for (JsonElement element : items) {
			JsonObject item = element.getAsJsonObject();
			long current = item.get("game_time_ticks").getAsLong();
			if (current < previous) {
				return false;
			}
			previous = current;
		}
		return true;
	}

	private static WorldStateV3 state(
		final WorldStateV2.FrozenDocument snapshot,
		final TimelineStatus status,
		final Optional<TaskInstance> current,
		final List<TaskInstance> history,
		final Optional<Long> completedAt
	) {
		Map<UUID, PlayerRecord> players = Map.of(
			RUNNER_ID,
			player(
				RUNNER_ID,
				"Runner",
				RUNNER,
				Map.of(
					SPAWN_FIELD,
					new PersistentFieldValue(1, StoredValue.ofString("north_gate")),
					Identifier.parse("test:unregistered_secret"),
					new PersistentFieldValue(1, StoredValue.ofString("do-not-leak"))
				)
			),
			HUNTER_ID,
			player(HUNTER_ID, "Hunter", HUNTER, Map.of())
		);
		WorldStateV2 core = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			27L,
			Optional.of(GAME),
			Optional.of(PHASE),
			Optional.empty(),
			players,
			Optional.empty(),
			List.of(),
			WorldStateV2.AuditLog.empty(),
			Optional.empty()
		);
		TimelineInstance timeline = new TimelineInstance(
			TIMELINE_INSTANCE,
			GAME,
			1,
			snapshot,
			status,
			Optional.empty(),
			current,
			history,
			400L,
			false,
			Optional.empty(),
			0L,
			Optional.of(1L),
			completedAt,
			8L,
			List.of()
		);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(GAME_INSTANCE),
			Optional.of(timeline),
			Optional.empty(),
			List.of(
				new ExclusiveReservation(
					SPAWN_RESERVATION_ID,
					GAME,
					SPAWN_FIELD,
					GAME_INSTANCE,
					"north_gate",
					RUNNER_ID,
					ReservationStatus.LOCKED,
					Optional.empty(),
					1L,
					1L,
					1L
				)
			),
			List.of()
		);
	}

	private static PlayerRecord player(
		final UUID id,
		final String name,
		final Identifier role,
		final Map<Identifier, PersistentFieldValue> fields
	) {
		return new PlayerRecord(
			id,
			name,
			role,
			Optional.empty(),
			ALIVE,
			fields,
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			0L,
			0L
		);
	}

	private static TaskInstance runningTask(final List<TaskEventRecord> events) {
		return new TaskInstance(
			TASK_INSTANCE,
			TASK,
			TaskKind.MAIN,
			TaskStatus.RUNNING,
			Optional.empty(),
			40L,
			Optional.empty(),
			Optional.empty(),
			List.of(RUNNER_ID, HUNTER_ID),
			0L,
			Optional.empty(),
			List.of(),
			events,
			List.of(TaskStatisticValue.integer("score", 7L))
		);
	}

	private static TaskInstance settledTask(final List<TaskEventRecord> events) {
		return new TaskInstance(
			TASK_INSTANCE,
			TASK,
			TaskKind.MAIN,
			TaskStatus.SETTLED,
			Optional.empty(),
			120L,
			Optional.of(
				new FrozenResult(
					"done",
					Optional.empty(),
					Optional.of(PHASE),
					120L
				)
			),
			Optional.empty(),
			List.of(RUNNER_ID, HUNTER_ID),
			0L,
			Optional.of(120L),
			List.of(),
			events,
			List.of(TaskStatisticValue.integer("score", 9L))
		);
	}

	private static List<TaskEventRecord> ordinaryEvents() {
		return List.of(
			new TaskEventRecord(
				"checkpoint",
				100L,
				20L,
				TaskEventRecordState.RUNNING,
				Optional.of(new FrozenPlayerValue(RUNNER_ID, "Runner"))
			),
			new TaskEventRecord(
				"game_end",
				120L,
				40L,
				TaskEventRecordState.RUNNING,
				Optional.of(new FrozenPlayerValue(RUNNER_ID, "Runner"))
			)
		);
	}

	private static DefinitionSnapshot compile(
		final List<Source> sources,
		final long generation,
		final String runnerPredicate
	) {
		return compileBase(sources, generation)
			.withPredicateDocuments(
				Map.of(
					RUNNER_GATE,
					runnerPredicate,
					UNUSED_GATE,
					UNUSED_PREDICATE
				)
			);
	}

	private static DefinitionSnapshot compileBase(
		final List<Source> sources,
		final long generation
	) {
		Compilation compilation = DefinitionCompiler.compile(
			sources,
			Set.of(),
			Set.of(RUNNER_GATE, UNUSED_GATE)
		);
		check(compilation.valid(), "projection fixture must compile: " + compilation.problems());
		return compilation.snapshot().orElseThrow().withGeneration(generation);
	}

	private static List<Source> sources(
		final String taskName,
		final String taskDescription
	) {
		List<Source> result = new ArrayList<>();
		result.add(
			source(
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 3,
				  "content_version": 1,
				  "name": {"text": "投影验收"},
				  "initial_phase": "test:active",
				  "default_role": "test:runner",
				  "default_life_state": "test:alive",
				  "task_timeline": {
				    "initial_task": "test:task",
				    "approval_phase": "test:active",
				    "start_phase": "test:active"
				  },
				  "player_terminal": {
				    "default_page": "test:home",
				    "history_enabled": true
				  }
				}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.ROLE,
				"runner",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"逃走者"}}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.ROLE,
				"hunter",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"猎人"}}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.LIFE_STATE,
				"alive",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"存活"}}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.PHASE,
				"active",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"进行中"},"transitions":[]}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.FIELD,
				"hunter_spawn",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "猎人出生点"},
				  "scope": "player",
				  "type": "exclusive_choice",
				  "required": false,
				  "editable_by": "player",
				  "invalidates_ready": false,
				  "roles": ["test:runner"],
				  "phases": ["test:active"],
				  "migration": "preserve",
				  "reservation_scope": "game_instance",
				  "release_when_role_mismatch": true,
				  "show_occupant": true,
				  "options": [
				    {"value":"north_gate","name":{"text":"北门"}},
				    {"value":"south_yard","name":{"text":"南侧场地"}}
				  ]
				}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.PAGE,
				"home",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "玩家终端"},
				  "root": {
				    "type": "column",
				    "children": [{"type":"text","text":{"text":"HOME"}}]
				  }
				}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.PAGE,
				"history_detail",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "事件详情"},
				  "root": {
				    "type": "column",
				    "children": [
				      {"type":"text","text":{"bind":"detail.title","fallback":{"text":"记录不可用"}}},
				      {
				        "type":"button",
				        "id":"detail_back",
				        "label":{"text":"返回"},
				        "action":{"type":"local","name":"back"}
				      }
				    ]
				  }
				}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.TASK,
				"task",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "kind": "main",
				  "name": {"text": "%s"},
				  "description": {"text": "%s"},
				  "completion_policy": "event_only",
				  "results": [{
				    "id": "done",
				    "name": {"text": "完成"},
				    "semantic": "success",
				    "route": {"end_phase": "test:active"}
				  }],
				  "events": [
				    {
				      "id": "checkpoint",
				      "name": {"text": "检查点"},
				      "policy": "repeatable",
				      "max_records": 128,
				      "allowed_states": ["running"],
				      "player_history": {
				        "release": "immediate",
				        "audience": {"roles": ["test:runner"]},
				        "title": {"text": "已触发检查点"},
				        "details": {"text": "%s"},
				        "detail_page": "test:history_detail",
				        "show_game_time": true,
				        "show_task": true,
				        "show_actor": true,
				        "statistics": ["score"]
				      }
				    },
				    {
				      "id": "game_end",
				      "name": {"text": "赛后"},
				      "policy": "repeatable",
				      "max_records": 128,
				      "allowed_states": ["running"],
				      "player_history": {
				        "release": "game_end",
				        "audience": {},
				        "title": {"text": "赛后公开"},
				        "details": {"text": "%s"},
				        "show_game_time": true,
				        "show_task": true,
				        "show_personal_result": true
				      }
				    }
				  ],
				  "statistics": [{
				    "id": "score",
				    "name": {"text": "分数"},
				    "type": "integer",
				    "write": "replace"
				  }]
				}
				""".formatted(
					taskName,
					taskDescription,
					taskDescription,
					taskDescription
				)
			)
		);
		result.add(playerData("task_id", "{\"type\":\"task_id\"}", "{}"));
		result.add(
			playerData(
				"runner_task",
				"{\"type\":\"task_name\"}",
				"{\"roles\":[\"test:runner\"]}",
				",\"predicate\":\"test:runner_gate\""
			)
		);
		result.add(
			playerData(
				"hunter_role",
				"{\"type\":\"role\"}",
				"{\"roles\":[\"test:hunter\"]}"
			)
		);
		result.add(playerData("task_status", "{\"type\":\"task_status\"}", "{}"));
		result.add(playerData("task_elapsed", "{\"type\":\"task_elapsed_ticks\"}", "{}"));
		result.add(playerData("game_elapsed", "{\"type\":\"game_elapsed_ticks\"}", "{}"));
		result.add(
			playerData(
				"progress",
				"{\"type\":\"task_progress\",\"id\":\"test:task\",\"key\":\"score\"}",
				"{}"
			)
		);
		result.add(
			playerData(
				"score",
				"{\"type\":\"task_statistic\",\"id\":\"test:task\",\"key\":\"score\"}",
				"{}"
			)
		);
		result.add(
			playerData(
				"formatted_description",
				"{\"type\":\"task_description\"}",
				"{\"roles\":[\"test:runner\"]}",
				",\"format\":\"【{value}】\""
			)
		);
		result.add(
			playerData(
				"missing_result",
				"{\"type\":\"task_result\",\"id\":\"test:task\",\"key\":\"done\"}",
				"{\"roles\":[\"test:runner\"]}",
				",\"masked_fallback\":{\"text\":\"尚无结果\"}"
			)
		);
		result.add(
			playerData(
				"profile/runner_role",
				"{\"type\":\"role\"}",
				"{\"roles\":[\"test:runner\"]}",
				",\"name\":{\"text\":\"当前身份\"}"
			)
		);
		result.add(
			playerData(
				"profile/hunter_spawn",
				"{\"type\":\"exclusive_choice\",\"id\":\"test:hunter_spawn\"}",
				"{\"roles\":[\"test:runner\"]}",
				",\"name\":{\"text\":\"猎人出生点\"}"
			)
		);
		result.add(
			playerData(
				"missing/team",
				"{\"type\":\"team\"}",
				"{\"roles\":[\"test:runner\"]}",
				",\"name\":{\"text\":\"当前队伍\"}"
			)
		);
		return List.copyOf(result);
	}

	private static List<Source> taskHistorySources(
		final String taskName,
		final String taskDescription
	) {
		return sources(taskName, taskDescription).stream()
			.map(source -> {
				if (source.type() != DefinitionType.GAME) {
					return source;
				}
				return new Source(
					source.type(),
					source.id(),
					source.resource(),
					source.sourcePack(),
					source.json().replace(
						"\"history_enabled\": true",
						"\"history_enabled\": true,\n    \"history_source\": \"tasks\""
					)
				);
			})
			.toList();
	}

	private static List<Source> sourcesWithPendingRunnerInitialization() {
		List<Source> result = new ArrayList<>(
			sources("未初始化顶栏", "未初始化顶栏说明")
		);
		result.removeIf(source ->
			source.type() == DefinitionType.ROLE && source.id().equals(RUNNER)
		);
		result.add(
			source(
				DefinitionType.ROLE,
				"runner",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "逃走者"},
				  "initialization_flow": "test:identity_init"
				}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.FLOW,
				"identity_init",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "身份初始化"},
				  "entry": "done",
				  "audience": {
				    "roles": ["test:runner"],
				    "exclude_host": true,
				    "online_only": true
				  },
				  "required": true,
				  "host_bossbar": {
				    "name": {"text": "{event}: {completed}/{total}"},
				    "color": "green",
				    "style": "progress",
				    "priority": 10
				  },
				  "nodes": [{"id": "done", "type": "complete"}]
				}
				"""
			)
		);
		result.add(
			playerData(
				"header_life_state",
				"{\"type\":\"life_state\"}",
				"{\"roles\":[\"test:runner\"]}"
			)
		);
		return List.copyOf(result);
	}

	private static Source playerData(
		final String id,
		final String source,
		final String audience
	) {
		return playerData(id, source, audience, "");
	}

	private static Source playerData(
		final String id,
		final String dataSource,
		final String audience,
		final String suffix
	) {
		return source(
			DefinitionType.PLAYER_DATA,
			id,
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "source": %s,
			  "surfaces": ["terminal"],
			  "audience": %s
			  %s
			}
			""".formatted(dataSource, audience, suffix)
		);
	}

	private static Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		Identifier id = Identifier.fromNamespaceAndPath("test", path);
		return new Source(
			type,
			id,
			Identifier.fromNamespaceAndPath(
				"test",
				"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
			),
			"projector-self-check",
			json
		);
	}

	private static boolean sameJson(final String left, final String right) {
		return left != null
			&& right != null
			&& JsonParser.parseString(left).equals(JsonParser.parseString(right));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
