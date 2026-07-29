package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.LiveVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultSemantic;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultStyle;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCallbacks;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskRoute;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticType;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticWrite;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.ActionContext;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.TaskStart;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecordState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable checks for task-fact authority, bounded persistence, and recap provenance.
 */
public final class TaskFactAuthoritySelfCheck {
	private static final Identifier GAME = id("test:game");
	private static final Identifier PHASE = id("test:running");
	private static final Identifier END_PHASE = id("test:ended");
	private static final Identifier TASK = id("test:main");
	private static final UUID TIMELINE = uuid(1);
	private static final UUID TASK_INSTANCE = uuid(2);
	private static final UUID PLAYER_A = uuid(10);
	private static final UUID PLAYER_B = uuid(11);
	private static final UUID OUTSIDER = uuid(12);

	private TaskFactAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		WorldStateV2.initial();
		TaskDefinition definition = definition();
		TimelineInstance timeline = timelineChanged(
			TimelineAuthority.create(
				Optional.empty(),
				TIMELINE,
				1,
				FrozenDocument.of("{\"task_facts\":\"self-check\"}"),
				new TaskStart(TASK_INSTANCE, definition, List.of(PLAYER_B, PLAYER_A)),
				100L
			),
			"create timeline"
		);
		timeline = timelineChanged(
			TimelineAuthority.startSucceeded(timeline, context(timeline), 101L),
			"start task"
		);
		timeline = timelineChanged(
			TimelineAuthority.tickRunning(timeline, context(timeline), definition),
			"task tick one"
		);
		timeline = timelineChanged(
			TimelineAuthority.tickRunning(timeline, context(timeline), definition),
			"task tick two"
		);
		check(timeline.timelineRevision() == 1L, "ordinary ticks must not increase timeline revision");

		timeline = factChanged(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"alarm",
				Optional.empty()
			),
			"record once event"
		);
		TaskEventRecord alarm = timeline.currentTask().orElseThrow().events().getFirst();
		check(
			alarm.gameElapsedTicks() == 2L
				&& alarm.taskElapsedTicks() == 2L
				&& alarm.state() == TaskEventRecordState.RUNNING,
			"server must freeze both clocks and the actual event state"
		);
		check(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"alarm",
				Optional.empty()
			).code() == OperationCode.EVENT_DUPLICATE,
			"once event must de-duplicate"
		);

		timeline = factChanged(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"terminal",
				player(PLAYER_A, "PlayerA")
			),
			"record player A"
		);
		check(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"terminal",
				player(PLAYER_A, "PlayerA")
			).code() == OperationCode.EVENT_DUPLICATE,
			"per-player event must de-duplicate by UUID"
		);
		timeline = factChanged(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"terminal",
				player(PLAYER_B, "PlayerB")
			),
			"record player B"
		);
		check(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"terminal",
				player(OUTSIDER, "Outsider")
			).code() == OperationCode.PERMISSION_DENIED,
			"player event must use the frozen participant roster"
		);
		check(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"terminal",
				Optional.empty()
			).code() == OperationCode.TARGET_INVALID,
			"per-player event must require a player"
		);

		for (int index = 0; index < 2; index++) {
			timeline = factChanged(
				TaskFactAuthority.recordEvent(
					timeline,
					context(timeline),
					definition,
					"crate",
					Optional.empty()
				),
				"record repeatable event " + index
			);
		}
		check(
			TaskFactAuthority.recordEvent(
				timeline,
				context(timeline),
				definition,
				"crate",
				Optional.empty()
			).code() == OperationCode.EVENT_LIMIT_REACHED,
			"repeatable event must honor max_records"
		);

		timeline = factChanged(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.booleanValue("opened", true)
			),
			"write once statistic"
		);
		check(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.booleanValue("opened", false)
			).code() == OperationCode.ACTION_UNAVAILABLE,
			"once statistic must freeze its first value"
		);

		timeline = factChanged(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.stringValue("label", "first")
			),
			"write replace statistic"
		);
		timeline = factChanged(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.stringValue("label", "latest")
			),
			"replace statistic"
		);
		check(
			statistic(timeline, "label").stringValue().orElseThrow().equals("latest"),
			"replace statistic must retain only the latest value"
		);

		timeline = factChanged(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.integer("rescued", 2L)
			),
			"increment statistic first"
		);
		timeline = factChanged(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.integer("rescued", 3L)
			),
			"increment statistic second"
		);
		check(
			statistic(timeline, "rescued").longValue().orElseThrow() == 5L,
			"increment statistic must store the accumulated value"
		);
		check(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.integer("rescued", 6L)
			).code() == OperationCode.TARGET_INVALID,
			"increment result must honor registered bounds"
		);
		check(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.stringValue("rescued", "wrong")
			).code() == OperationCode.STATISTIC_TYPE_MISMATCH,
			"statistic type mismatch must be rejected"
		);

		timeline = factChanged(
			TaskFactAuthority.writeStatistic(
				timeline,
				context(timeline),
				definition,
				TaskStatisticValue.player("winner", PLAYER_B, "PlayerB")
			),
			"write player statistic"
		);
		FrozenPlayerValue winner = statistic(timeline, "winner").playerValue().orElseThrow();
		check(
			winner.playerId().equals(PLAYER_B) && winner.playerName().equals("PlayerB"),
			"player statistic must freeze UUID and the name at write time"
		);

		TimelineInstance settling = timelineChanged(
			TimelineAuthority.submitResult(timeline, context(timeline), definition, "finished"),
			"freeze result"
		);
		check(
			settling.currentTask().orElseThrow().events().equals(timeline.currentTask().orElseThrow().events())
				&& settling.currentTask().orElseThrow().statistics()
					.equals(timeline.currentTask().orElseThrow().statistics()),
			"timeline copies must preserve task facts"
		);
		FrozenResult route = settling.currentTask().orElseThrow().result().orElseThrow();
		settling = factChanged(
			TaskFactAuthority.recordEvent(
				settling,
				context(settling),
				definition,
				"capture",
				player(PLAYER_A, "PlayerA")
			),
			"record settling event"
		);
		check(
			settling.currentTask().orElseThrow().result().orElseThrow().equals(route),
			"recording an event must not rewrite the frozen result route"
		);

		TimelineInstance completed = timelineChanged(
			TimelineAuthority.settlementSucceeded(
				settling,
				context(settling),
				definition,
				Optional.empty(),
				120L
			),
			"complete task"
		);
		check(
			completed.status() == TimelineStatus.COMPLETED
				&& completed.currentTask().isEmpty()
				&& completed.taskHistory().size() == 1
				&& completed.taskHistory().getFirst().taskInstanceId().equals(TASK_INSTANCE),
			"recap history must contain only the actual completed task"
		);
		check(
			completed.taskHistory().getFirst().events().size() == 6
				&& completed.taskHistory().getFirst().statistics().size() == 4,
			"completed task facts must remain attached to their actual history entry"
		);
		check(
			TaskFactAuthority.writeStatistic(
				completed,
				context(settling),
				definition,
				TaskStatisticValue.stringValue("label", "too late")
			).code() == OperationCode.TIMELINE_NOT_ACTIVE,
			"historical task facts must be frozen"
		);

		checkCodecRoundTrip(completed);
		checkOldTaskDefaults(completed);
		checkStrictStatisticCodec();
		checkEventCapacity(completed.taskHistory().getFirst());
		System.out.println("TASK_FACT_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkCodecRoundTrip(final TimelineInstance completed) {
		WorldStateV3 root = root(completed);
		JsonElement encoded = WorldStateV3.CODEC.encodeStart(JsonOps.INSTANCE, root).getOrThrow();
		WorldStateV3 decoded = WorldStateV3.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(decoded.equals(root), "task facts must survive world-state codec round-trip");
	}

	private static void checkOldTaskDefaults(final TimelineInstance completed) {
		WorldStateV3 root = root(completed);
		JsonObject encoded = WorldStateV3.CODEC
			.encodeStart(JsonOps.INSTANCE, root)
			.getOrThrow()
			.getAsJsonObject();
		JsonObject oldTask = encoded
			.getAsJsonObject("timeline")
			.getAsJsonArray("task_history")
			.get(0)
			.getAsJsonObject();
		oldTask.remove("events");
		oldTask.remove("statistics");
		WorldStateV3 decoded = WorldStateV3.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(
			decoded.timeline().orElseThrow().taskHistory().getFirst().events().isEmpty()
				&& decoded.timeline().orElseThrow().taskHistory().getFirst().statistics().isEmpty(),
			"older schema-v3 task facts must default to empty"
		);
	}

	private static void checkStrictStatisticCodec() {
		TaskStatisticValue malformed = new TaskStatisticValue(
			"opened",
			TaskStatisticValueType.BOOLEAN,
			Optional.empty(),
			Optional.of(1L),
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
		check(
			TaskStatisticValue.CODEC.encodeStart(JsonOps.INSTANCE, malformed).error().isPresent(),
			"statistic codec must reject a payload that disagrees with its declared type"
		);
	}

	private static void checkEventCapacity(final TaskInstance source) {
		TaskEventRecord event = new TaskEventRecord(
			"crate",
			2L,
			2L,
			TaskEventRecordState.RUNNING,
			Optional.empty()
		);
		List<TaskEventRecord> oversized = new ArrayList<>(WorldStateV3.MAX_TASK_EVENT_RECORDS + 1);
		for (int index = 0; index <= WorldStateV3.MAX_TASK_EVENT_RECORDS; index++) {
			oversized.add(event);
		}
		TaskInstance invalid = new TaskInstance(
			source.taskInstanceId(),
			source.taskId(),
			source.kind(),
			source.status(),
			source.resumeStatus(),
			source.elapsedTicks(),
			source.result(),
			source.intermission(),
			source.participants(),
			source.startedAtGameTick(),
			source.settledAtGameTick(),
			source.callbackLedger(),
			oversized,
			source.statistics()
		);
		check(
			TaskInstance.CODEC.encodeStart(JsonOps.INSTANCE, invalid).error().isPresent(),
			"task event persistence must enforce its aggregate capacity"
		);
	}

	private static TaskDefinition definition() {
		TaskResult finished = new TaskResult(
			"finished",
			text("完成"),
			new ResultStyle(ResultSemantic.SUCCESS, Optional.empty()),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			false,
			new TaskRoute(
				Optional.empty(),
				Optional.of(END_PHASE),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			)
		);
		return new TaskDefinition(
			TASK,
			GAME,
			1,
			TaskKind.MAIN,
			text("主任务"),
			Optional.empty(),
			Optional.empty(),
			io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.defaultTaskAudience(),
			TaskCompletionPolicy.EARLY_OR_TIMEOUT,
			OptionalLong.of(100L),
			true,
			LiveVisibility.hidden(),
			RecapVisibility.PARTICIPANTS,
			TaskCallbacks.empty(),
			Optional.empty(),
			Map.of("finished", finished),
			List.of(
				event("alarm", TaskEventPolicy.ONCE, Optional.empty(), Set.of(TaskEventState.RUNNING)),
				event("terminal", TaskEventPolicy.PER_PLAYER, Optional.empty(), Set.of(TaskEventState.RUNNING)),
				event("crate", TaskEventPolicy.REPEATABLE, Optional.of(2), Set.of(TaskEventState.RUNNING)),
				event("capture", TaskEventPolicy.ONCE, Optional.empty(), Set.of(TaskEventState.SETTLING))
			),
			List.of(
				statistic("opened", TaskStatisticType.BOOLEAN, TaskStatisticWrite.ONCE, Optional.empty(), Optional.empty()),
				statistic("label", TaskStatisticType.STRING, TaskStatisticWrite.REPLACE, Optional.empty(), Optional.empty()),
				statistic("rescued", TaskStatisticType.INTEGER, TaskStatisticWrite.INCREMENT, Optional.of(0L), Optional.of(10L)),
				statistic("winner", TaskStatisticType.PLAYER, TaskStatisticWrite.REPLACE, Optional.empty(), Optional.empty())
			)
		);
	}

	private static TaskEventDefinition event(
		final String id,
		final TaskEventPolicy policy,
		final Optional<Integer> maximum,
		final Set<TaskEventState> states
	) {
		return new TaskEventDefinition(
			id,
			text(id),
			policy,
			maximum,
			states,
			Optional.empty(),
			RecapVisibility.PARTICIPANTS
		);
	}

	private static TaskStatisticDefinition statistic(
		final String id,
		final TaskStatisticType type,
		final TaskStatisticWrite write,
		final Optional<Long> minimum,
		final Optional<Long> maximum
	) {
		return new TaskStatisticDefinition(
			id,
			text(id),
			type,
			minimum,
			maximum,
			write,
			RecapVisibility.PARTICIPANTS
		);
	}

	private static WorldStateV3 root(final TimelineInstance timeline) {
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			WorldStateV2.initial().withActivity(GAME, PHASE),
			Optional.of(TIMELINE),
			Optional.of(timeline),
			List.of(),
			List.of()
		);
	}

	private static TaskStatisticValue statistic(
		final TimelineInstance timeline,
		final String statisticId
	) {
		return timeline.currentTask()
			.orElseThrow()
			.statistics()
			.stream()
			.filter(value -> value.statisticId().equals(statisticId))
			.findFirst()
			.orElseThrow();
	}

	private static Optional<FrozenPlayerValue> player(
		final UUID playerId,
		final String playerName
	) {
		return Optional.of(new FrozenPlayerValue(playerId, playerName));
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

	private static TimelineInstance factChanged(
		final TaskFactAuthority.OperationResult result,
		final String operation
	) {
		check(result.successful(), operation + " failed: " + result.code() + " " + result.message());
		return result.nextState().orElseThrow(
			() -> new AssertionError(operation + " did not return a changed state")
		);
	}

	private static TimelineInstance timelineChanged(
		final TimelineAuthority.OperationResult result,
		final String operation
	) {
		check(result.successful(), operation + " failed: " + result.code() + " " + result.message());
		return result.nextState().orElseThrow(
			() -> new AssertionError(operation + " did not return a changed state")
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
