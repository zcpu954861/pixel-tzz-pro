package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.LiveVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultSemantic;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultStyle;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCallbacks;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskRoute;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskTimeline;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStepStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TimelineClockProjection;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TimelineViewLayout;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Minimal authority, clock-projection, and small-window regression check for the read-only view.
 */
public final class TimelineViewSelfCheck {
	private static final Identifier GAME = id("test:game");
	private static final Identifier PHASE = id("test:running");
	private static final Identifier END_PHASE = id("test:ended");
	private static final Identifier ROLE = id("test:runner");
	private static final Identifier LIFE_STATE = id("test:alive");
	private static final Identifier TASK_A = id("test:task_a");
	private static final Identifier TASK_B = id("test:task_b");
	private static final Identifier TASK_C = id("test:task_c");
	private static final UUID GAME_INSTANCE = uuid(1L);
	private static final UUID TIMELINE_INSTANCE = uuid(2L);
	private static final UUID TASK_INSTANCE = uuid(3L);
	private static final UUID PARTICIPANT = uuid(10L);
	private static final UUID OUTSIDER = uuid(11L);

	private TimelineViewSelfCheck() {
	}

	public static void main(final String[] arguments) {
		DefinitionSnapshot definitions = definitions();
		checkPreStartPreview(definitions);
		checkLiveAuthority(definitions);
		checkTerminalRecapAuthority(definitions);
		checkClockProjection();
		checkSmallWindowLayout();
		System.out.println("TIMELINE_VIEW_SELF_CHECK=PASS");
	}

	private static void checkPreStartPreview(final DefinitionSnapshot definitions) {
		WorldStateV3 world = WorldStateV3.initial()
			.beginGameInstance(GAME, PHASE, GAME_INSTANCE);
		TimelineViewS2CPayload preview = TimelineViewBuilder.preview(world, definitions, 0L);
		check(
			preview.status().equals("ready")
				&& preview.hostView()
				&& preview.timelineStatus().equals("preview"),
			"host must receive a pre-start task preview before a timeline exists"
		);
		check(
			preview.tasks().stream().allMatch(task ->
				task.taskInstanceId().isEmpty()
					&& task.startedAtGameTick().isEmpty()
					&& task.settledAtGameTick().isEmpty()
					&& task.routeState().equals("future")
					&& task.taskStatus().equals("not_started")
			),
			"pre-start preview must carry definitions without runtime facts"
		);
		check(
			preview.tasks().stream().map(task -> task.taskId()).toList()
				.equals(List.of(TASK_A, TASK_B, TASK_C)),
			"pre-start preview must retain deterministic mission order"
		);
	}

	private static void checkLiveAuthority(final DefinitionSnapshot definitions) {
		WorldStateV3 world = world(liveTimeline());
		TimelineViewS2CPayload player = TimelineViewBuilder.build(
			world,
			definitions,
			PARTICIPANT,
			false,
			1L,
			ignored -> true
		);
		check(
			player.status().equals("unavailable"),
			"a non-host must not receive a live timeline even when participating"
		);

		TimelineViewS2CPayload host = TimelineViewBuilder.build(
			world,
			definitions,
			OUTSIDER,
			true,
			2L,
			ignored -> true
		);
		check(host.status().equals("ready") && host.hostView(), "host live view must be available");
		var current = host.tasks()
			.stream()
			.filter(task -> task.taskId().equals(TASK_A))
			.findFirst()
			.orElseThrow();
		check(
			current.nextTaskIds().equals(List.of(TASK_B, TASK_C)),
			"host must receive the bounded candidate-successor set"
		);
		check(
			host.auditStatus().equals("intervention_required") && !current.callbacks().isEmpty(),
			"host must receive callback audit state"
		);
		check(
			current.countsTowardGameTime(),
			"task clock policy must survive the server projection"
		);
		check(
			current.startedAtGameTick().equals(Optional.of(120L))
				&& current.settledAtGameTick().isEmpty(),
			"a live task must expose its game-clock start without inventing an end"
		);
	}

	private static void checkTerminalRecapAuthority(final DefinitionSnapshot definitions) {
		WorldStateV3 world = world(terminalTimeline());
		TimelineViewS2CPayload participant = TimelineViewBuilder.build(
			world,
			definitions,
			PARTICIPANT,
			false,
			3L,
			ignored -> false
		);
		check(
			participant.status().equals("ready")
				&& participant.recapView()
				&& !participant.hostView(),
			"a terminal participant must receive the recap"
		);
		check(participant.tasks().size() == 1, "recap must contain only the route that occurred");
		var task = participant.tasks().getFirst();
		check(
			task.participants().isEmpty()
				&& task.nextTaskIds().isEmpty()
				&& task.callbacks().isEmpty()
				&& participant.timelineCallbacks().isEmpty()
				&& participant.auditStatus().isEmpty(),
			"participant recap must not carry host-only roster, topology, or audit fields"
		);
		check(
			task.startedAtGameTick().equals(Optional.of(100L))
				&& task.settledAtGameTick().equals(Optional.of(400L))
				&& task.result().orElseThrow().frozenAtGameTick() == 400L,
			"a recap must retain the authoritative task and frozen-result game ticks"
		);
		TimelineViewS2CPayload outsider = TimelineViewBuilder.build(
			world,
			definitions,
			OUTSIDER,
			false,
			4L,
			ignored -> false
		);
		check(
			outsider.status().equals("unavailable"),
			"a terminal non-participant must not receive another match's recap"
		);
	}

	private static void checkClockProjection() {
		check(
			TimelineClockProjection.project(100L, 1_000L, 1_249L, true) == 104L,
			"an advancing clock must interpolate at the server's 20 Hz rate"
		);
		check(
			TimelineClockProjection.project(100L, 1_000L, 2_000L, false) == 100L,
			"a paused or non-counting clock must remain frozen"
		);
		check(
			TimelineClockProjection.project(105L, 1_250L, 1_300L, true) == 106L,
			"a newer snapshot must become the next interpolation baseline"
		);
	}

	private static void checkSmallWindowLayout() {
		for (int[] viewport : List.of(
			new int[]{320, 180},
			new int[]{120, 80},
			new int[]{64, 48}
		)) {
			var layout = TimelineViewLayout.compute(viewport[0], viewport[1]);
			check(inside(layout.panel(), viewport[0], viewport[1]), "panel escaped the viewport");
			check(
				inside(layout.backButton(), viewport[0], viewport[1])
					&& inside(layout.backButton(), layout.panel()),
				"small-window back button must remain visible and inside the panel"
			);
		}
		check(TimelineViewLayout.compute(900, 500).wide(), "wide viewport must use two columns");
		check(!TimelineViewLayout.compute(320, 180).wide(), "small viewport must fold vertically");
	}

	private static TimelineInstance liveTimeline() {
		CallbackStep failure = new CallbackStep(
			"task:start",
			id("test:callback"),
			Optional.of(PARTICIPANT),
			CallbackStepStatus.FAILED,
			1,
			Optional.of("fixture failure"),
			120L
		);
		TaskInstance task = new TaskInstance(
			TASK_INSTANCE,
			TASK_A,
			WorldStateV3.TaskKind.MAIN,
			TaskStatus.RUNNING,
			Optional.empty(),
			120L,
			Optional.empty(),
			Optional.empty(),
			List.of(PARTICIPANT),
			120L,
			Optional.empty(),
			List.of(failure)
		);
		return timeline(TimelineStatus.RUNNING, Optional.of(task), List.of(), Optional.empty());
	}

	private static TimelineInstance terminalTimeline() {
		TaskInstance task = new TaskInstance(
			TASK_INSTANCE,
			TASK_A,
			WorldStateV3.TaskKind.MAIN,
			TaskStatus.SETTLED,
			Optional.empty(),
			400L,
			Optional.of(
				new FrozenResult(
					"finish",
					Optional.empty(),
					Optional.of(END_PHASE),
					400L
				)
			),
			Optional.empty(),
			List.of(PARTICIPANT),
			100L,
			Optional.of(400L),
			List.of()
		);
		return timeline(
			TimelineStatus.COMPLETED,
			Optional.empty(),
			List.of(task),
			Optional.of(400L)
		);
	}

	private static TimelineInstance timeline(
		final TimelineStatus status,
		final Optional<TaskInstance> current,
		final List<TaskInstance> history,
		final Optional<Long> completedAt
	) {
		return new TimelineInstance(
			TIMELINE_INSTANCE,
			GAME,
			1,
			FrozenDocument.of("{\"timeline\":\"view-self-check\"}"),
			status,
			Optional.empty(),
			current,
			history,
			400L,
			false,
			Optional.empty(),
			0L,
			Optional.of(0L),
			completedAt,
			7L,
			List.of()
		);
	}

	private static WorldStateV3 world(final TimelineInstance timeline) {
		return WorldStateV3.initial()
			.beginGameInstance(GAME, PHASE, GAME_INSTANCE)
			.withTimeline(Optional.of(timeline));
	}

	private static DefinitionSnapshot definitions() {
		TaskTimeline timeline = new TaskTimeline(TASK_A, false, PHASE, PHASE, List.of());
		GameDefinition game = new GameDefinition(
			GAME,
			2,
			1,
			text("全员逃走中"),
			PHASE,
			ROLE,
			LIFE_STATE,
			Optional.of(timeline)
		);
		TaskDefinition first = task(
			TASK_A,
			true,
			Map.of(
				"left", result("left", Optional.of(TASK_B), Optional.empty()),
				"right", result("right", Optional.of(TASK_C), Optional.empty()),
				"finish", result("finish", Optional.empty(), Optional.of(END_PHASE))
			)
		);
		TaskDefinition second = task(
			TASK_B,
			false,
			Map.of("finish", result("finish", Optional.empty(), Optional.of(END_PHASE)))
		);
		TaskDefinition alternate = task(
			TASK_C,
			true,
			Map.of("finish", result("finish", Optional.empty(), Optional.of(END_PHASE)))
		);
		return new DefinitionSnapshot(
			1L,
			Map.of(GAME, game),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(TASK_A, first, TASK_B, second, TASK_C, alternate),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Set.of(),
			Set.of(),
			Map.of()
		);
	}

	private static TaskDefinition task(
		final Identifier taskId,
		final boolean countsTowardGameTime,
		final Map<String, TaskResult> results
	) {
		return new TaskDefinition(
			taskId,
			GAME,
			1,
			TaskKind.MAIN,
			text(taskId.getPath()),
			Optional.empty(),
			Optional.empty(),
			io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.defaultTaskAudience(),
			TaskCompletionPolicy.EVENT_ONLY,
			OptionalLong.empty(),
			countsTowardGameTime,
			LiveVisibility.hidden(),
			RecapVisibility.PARTICIPANTS,
			TaskCallbacks.empty(),
			Optional.empty(),
			results,
			List.of(),
			List.of()
		);
	}

	private static TaskResult result(
		final String resultId,
		final Optional<Identifier> nextTask,
		final Optional<Identifier> endPhase
	) {
		return new TaskResult(
			resultId,
			text(resultId),
			new ResultStyle(ResultSemantic.NEUTRAL, Optional.empty()),
			Optional.of(text(resultId)),
			Optional.empty(),
			Optional.empty(),
			false,
			new TaskRoute(
				nextTask,
				endPhase,
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			)
		);
	}

	private static boolean inside(final Rect child, final Rect parent) {
		return child.x() >= parent.x()
			&& child.y() >= parent.y()
			&& child.right() <= parent.right()
			&& child.bottom() <= parent.bottom();
	}

	private static boolean inside(final Rect rect, final int width, final int height) {
		return rect.width() > 0
			&& rect.height() > 0
			&& rect.x() >= 0
			&& rect.y() >= 0
			&& rect.right() <= width
			&& rect.bottom() <= height;
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
