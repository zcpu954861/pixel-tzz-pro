package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonObject;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultSemantic;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.CallbackView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.EventView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.IntermissionView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.PlayerView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.ResultView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.StatisticView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.TaskView;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;

/**
 * Compiles one least-authority timeline projection from persisted state and its frozen definitions.
 */
public final class TimelineViewBuilder {
	private static final int MAX_EVENTS_PER_TASK = 128;
	private static final int MAX_STATISTICS_PER_TASK = 64;
	private static final int MAX_PARTICIPANTS_PER_TASK = 64;
	private static final int MAX_CALLBACKS_PER_TASK = 128;

	private TimelineViewBuilder() {
	}

	public static TimelineViewS2CPayload build(
		final WorldStateV3 root,
		final UUID viewerId,
		final boolean host,
		final long requestSequence,
		final Predicate<UUID> online
	) {
		Objects.requireNonNull(root, "root");
		Objects.requireNonNull(viewerId, "viewerId");
		Objects.requireNonNull(online, "online");
		TimelineInstance timeline = root.timeline().orElse(null);
		if (timeline == null) {
			return unavailable(requestSequence);
		}
		var restored = TimelineSnapshotCompiler.restore(timeline.gameId(), timeline.snapshot());
		if (!restored.success()) {
			return TimelineViewS2CPayload.error(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				"时间线冻结快照无法读取。"
			);
		}
		return build(root, restored.snapshot().orElseThrow(), viewerId, host, requestSequence, online);
	}

	/**
	 * Host-only pre-start projection compiled from the current registered task definitions. It
	 * carries no runtime facts and never exposes the route to ordinary players.
	 */
	public static TimelineViewS2CPayload preview(
		final WorldStateV3 root,
		final DefinitionSnapshot definitions,
		final long requestSequence
	) {
		Objects.requireNonNull(root, "root");
		Objects.requireNonNull(definitions, "definitions");
		Identifier gameId = root.core().activeGameId().orElse(null);
		GameDefinition game = gameId == null ? null : definitions.games().get(gameId);
		if (game == null || game.taskTimeline().isEmpty()) {
			return TimelineViewS2CPayload.error(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				"当前数据包未注册任务路线，无法生成开局前预览。"
			);
		}
		List<Identifier> order;
		try {
			order = missionOrder(game, definitions);
		} catch (IllegalArgumentException error) {
			return TimelineViewS2CPayload.error(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				"已注册任务路线不完整，请检查数据包任务定义。"
			);
		}
		TextBudget textBudget = new TextBudget(
			TimelineViewS2CPayload.MAX_TOTAL_COMPONENT_CHARACTERS
		);
		FactBudget factBudget = new FactBudget(0, 0, 0, 0);
		List<TaskView> tasks = order.stream()
			.limit(TimelineViewS2CPayload.MAX_TASKS)
			.map(taskId -> taskView(
				definitions.tasks().get(taskId),
				null,
				"future",
				true,
				root,
				textBudget,
				factBudget,
				ignored -> false
			))
			.toList();
		UUID previewId = UUID.nameUUIDFromBytes(
			("pixel-tzz-preview:" + game.id()).getBytes(StandardCharsets.UTF_8)
		);
		return new TimelineViewS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			requestSequence,
			0L,
			"ready",
			"任务定义检查正常",
			true,
			false,
			Optional.of(previewId),
			Optional.of(game.id()),
			textBudget.take(game.name()),
			"preview",
			0L,
			false,
			"",
			"healthy",
			List.of(),
			tasks
		);
	}

	/**
	 * Test seam after the production restore boundary has validated the frozen definition snapshot.
	 */
	static TimelineViewS2CPayload build(
		final WorldStateV3 root,
		final DefinitionSnapshot definitions,
		final UUID viewerId,
		final boolean host,
		final long requestSequence,
		final Predicate<UUID> online
	) {
		TimelineInstance timeline = root.timeline().orElse(null);
		if (timeline == null) {
			return unavailable(requestSequence);
		}
		GameDefinition game = definitions.games().get(timeline.gameId());
		if (game == null || game.taskTimeline().isEmpty()) {
			return TimelineViewS2CPayload.error(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				"冻结时间线缺少所属游戏定义。"
			);
		}

		boolean terminal = timeline.status() == TimelineStatus.COMPLETED
			|| timeline.status() == TimelineStatus.INTERRUPTED;
		if (!host && !terminal) {
			return unavailable(requestSequence);
		}

		List<TaskInstance> actualTasks = new ArrayList<>(timeline.taskHistory());
		timeline.currentTask().ifPresent(actualTasks::add);
		if (
			!host
				&& actualTasks.stream().noneMatch(task -> task.participants().contains(viewerId))
		) {
			return unavailable(requestSequence);
		}

		List<Identifier> order;
		try {
			order = host
				? missionOrder(game, definitions)
				: actualTasks.stream().map(TaskInstance::taskId).toList();
		} catch (IllegalArgumentException error) {
			return TimelineViewS2CPayload.error(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				"冻结任务路线无法读取。"
			);
		}
		Map<Identifier, TaskInstance> actualByTask = new HashMap<>();
		for (TaskInstance task : actualTasks) {
			if (actualByTask.put(task.taskId(), task) != null) {
				return TimelineViewS2CPayload.error(
					NetworkProtocol.CURRENT_VERSION,
					requestSequence,
					"冻结任务路线包含重复节点。"
				);
			}
		}
		Set<Identifier> reachableFuture = timeline.currentTask()
			.map(task -> reachableFrom(task.taskId(), definitions))
			.orElseGet(Set::of);
		TextBudget textBudget = new TextBudget(
			TimelineViewS2CPayload.MAX_TOTAL_COMPONENT_CHARACTERS
		);
		String gameNameJson = textBudget.take(game.name());
		FactBudget factBudget = new FactBudget(
			TimelineViewS2CPayload.MAX_TOTAL_EVENTS,
			TimelineViewS2CPayload.MAX_TOTAL_STATISTICS,
			TimelineViewS2CPayload.MAX_TOTAL_PARTICIPANTS,
			TimelineViewS2CPayload.MAX_TOTAL_CALLBACKS
		);
		List<CallbackView> timelineCallbacks = host
			? callbacks(timeline.callbackLedger(), root, factBudget, online, Integer.MAX_VALUE)
			: List.of();
		List<TaskView> tasks = new ArrayList<>();
		for (Identifier taskId : order) {
			TaskDefinition definition = definitions.tasks().get(taskId);
			if (definition == null) {
				return TimelineViewS2CPayload.error(
					NetworkProtocol.CURRENT_VERSION,
					requestSequence,
					"冻结任务定义不完整。"
				);
			}
			TaskInstance instance = actualByTask.get(taskId);
			if (
				!host
					&& (
						instance == null
							|| !instance.participants().contains(viewerId)
							|| definition.recapVisibility() != RecapVisibility.PARTICIPANTS
					)
			) {
				continue;
			}
			tasks.add(
				taskView(
					definition,
					instance,
					routeState(timeline, instance, taskId, reachableFuture),
					host,
					root,
					textBudget,
					factBudget,
					online
				)
			);
			if (tasks.size() == TimelineViewS2CPayload.MAX_TASKS) {
				break;
			}
		}
		if (tasks.isEmpty()) {
			return unavailable(requestSequence);
		}
		return new TimelineViewS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			requestSequence,
			timeline.timelineRevision(),
			"ready",
			"",
			host,
			!host,
			Optional.of(timeline.instanceId()),
			Optional.of(timeline.gameId()),
			gameNameJson,
			timeline.status().getSerializedName(),
			timeline.gameElapsedTicks(),
			timeline.paused(),
			timeline.pauseReason().orElse(""),
			host ? auditStatus(timeline) : "",
			timelineCallbacks,
			tasks
		);
	}

	private static TimelineViewS2CPayload unavailable(final long requestSequence) {
		return TimelineViewS2CPayload.unavailable(
			NetworkProtocol.CURRENT_VERSION,
			requestSequence,
			"本局尚无可查看的赛后回顾。"
		);
	}

	private static TaskView taskView(
		final TaskDefinition definition,
		final TaskInstance instance,
		final String routeState,
		final boolean host,
		final WorldStateV3 root,
		final TextBudget textBudget,
		final FactBudget factBudget,
		final Predicate<UUID> online
	) {
		String taskNameJson = textBudget.take(definition.name());
		String taskDescriptionJson = definition.description().map(textBudget::take).orElse("");
		Optional<ResultView> result = instance == null
			? Optional.empty()
			: instance.result().flatMap(value -> {
				TaskResult registered = definition.results().get(value.resultId());
				if (registered == null) {
					return Optional.empty();
				}
				String semantic = registered.style().semantic() == ResultSemantic.CUSTOM
					? registered.style()
						.customSemantic()
						.map(Identifier::toString)
						.orElse("custom")
					: registered.style().semantic().name().toLowerCase(Locale.ROOT);
				return Optional.of(
					new ResultView(
						value.resultId(),
						textBudget.take(registered.name()),
						registered.recap().map(textBudget::take).orElse(""),
						semantic,
						value.frozenAtGameTick()
					)
				);
			});
		Optional<IntermissionView> intermission = instance == null
			? Optional.empty()
			: instance.intermission()
				.map(value -> new IntermissionView(
					value.durationTicks(),
					value.elapsedTicks(),
					value.countsTowardGameTime(),
					value.paused()
				));

		List<EventView> events = new ArrayList<>();
		int eligibleEvents = 0;
		if (instance != null) {
			Map<String, TaskEventDefinition> eventDefinitions = new HashMap<>();
			definition.events().forEach(value -> eventDefinitions.put(value.id(), value));
			for (TaskEventRecord event : instance.events()) {
				TaskEventDefinition registered = eventDefinitions.get(event.eventId());
				if (registered == null || !visible(registered.recapVisibility(), host)) {
					continue;
				}
				eligibleEvents++;
				if (
					events.size() >= MAX_EVENTS_PER_TASK
						|| !factBudget.takeEvent()
				) {
					continue;
				}
				events.add(
					new EventView(
						event.eventId(),
						textBudget.take(registered.name()),
						event.gameElapsedTicks(),
						event.taskElapsedTicks(),
						event.state().getSerializedName(),
						event.player().map(value -> player(value, online))
					)
				);
			}
		}

		List<StatisticView> statistics = new ArrayList<>();
		int eligibleStatistics = 0;
		if (instance != null) {
			Map<String, TaskStatisticDefinition> statisticDefinitions = new HashMap<>();
			definition.statistics().forEach(value -> statisticDefinitions.put(value.id(), value));
			for (TaskStatisticValue statistic : instance.statistics()) {
				TaskStatisticDefinition registered = statisticDefinitions.get(statistic.statisticId());
				if (registered == null || !visible(registered.recapVisibility(), host)) {
					continue;
				}
				eligibleStatistics++;
				if (
					statistics.size() >= MAX_STATISTICS_PER_TASK
						|| !factBudget.takeStatistic()
				) {
					continue;
				}
				statistics.add(
					new StatisticView(
						statistic.statisticId(),
						textBudget.take(registered.name()),
						statistic.type().getSerializedName(),
						statisticValue(statistic),
						statistic.playerValue().map(value -> player(value, online))
					)
				);
			}
		}

		List<PlayerView> participants = new ArrayList<>();
		if (host && instance != null) {
			for (UUID playerId : instance.participants()) {
				if (
					participants.size() >= MAX_PARTICIPANTS_PER_TASK
						|| !factBudget.takeParticipant()
				) {
					continue;
				}
				participants.add(player(playerId, root, online));
			}
		}
		List<CallbackView> callbacks = host && instance != null
			? callbacks(
				instance.callbackLedger(),
				root,
				factBudget,
				online,
				MAX_CALLBACKS_PER_TASK
			)
			: List.of();
		List<Identifier> nextTaskIds = host
			? definition.results()
				.values()
				.stream()
				.map(TaskResult::route)
				.flatMap(route -> route.nextTask().stream())
				.distinct()
				.sorted()
				.toList()
			: List.of();

		return new TaskView(
			Optional.ofNullable(instance).map(TaskInstance::taskInstanceId),
			definition.id(),
			definition.kind().name().toLowerCase(Locale.ROOT),
			routeState,
			instance == null ? "not_started" : instance.status().getSerializedName(),
			taskNameJson,
			taskDescriptionJson,
			instance == null ? 0L : instance.elapsedTicks(),
			Optional.ofNullable(instance).map(TaskInstance::startedAtGameTick),
			instance == null ? Optional.empty() : instance.settledAtGameTick(),
			optionalLong(definition.durationTicks()),
			definition.countsTowardGameTime(),
			instance == null ? 0 : instance.participants().size(),
			participants,
			host && instance != null ? instance.participants().size() - participants.size() : 0,
			nextTaskIds,
			result,
			intermission,
			events,
			statistics,
			callbacks,
			eligibleEvents - events.size(),
			eligibleStatistics - statistics.size(),
			host && instance != null ? instance.callbackLedger().size() - callbacks.size() : 0
		);
	}

	private static List<CallbackView> callbacks(
		final List<CallbackStep> steps,
		final WorldStateV3 root,
		final FactBudget budget,
		final Predicate<UUID> online,
		final int perSectionMaximum
	) {
		List<CallbackView> result = new ArrayList<>();
		for (CallbackStep step : steps) {
			if (result.size() >= perSectionMaximum || !budget.takeCallback()) {
				continue;
			}
			result.add(
				new CallbackView(
					step.stepKey(),
					step.functionId(),
					step.playerId().map(playerId -> player(playerId, root, online)),
					step.status().getSerializedName(),
					step.attemptCount(),
					step.lastError()
						.map(value -> value.substring(
							0,
							Math.min(value.length(), TimelineViewS2CPayload.MAX_MESSAGE_LENGTH)
						))
						.orElse("")
				)
			);
		}
		return List.copyOf(result);
	}

	private static String auditStatus(final TimelineInstance timeline) {
		if (
			timeline.status() == TimelineStatus.BLOCKED
				|| timeline.currentTask()
					.map(TaskInstance::status)
					.map(value -> value.getSerializedName().equals("blocked"))
					.orElse(false)
		) {
			return "blocked";
		}
		var steps = java.util.stream.Stream.concat(
			timeline.callbackLedger().stream(),
			java.util.stream.Stream.concat(
				timeline.taskHistory().stream(),
				timeline.currentTask().stream()
			).flatMap(task -> task.callbackLedger().stream())
		).toList();
		if (
			steps.stream().anyMatch(step ->
				step.status().getSerializedName().equals("failed")
					|| step.status().getSerializedName().equals("outcome_unknown")
			)
		) {
			return "intervention_required";
		}
		return steps.stream()
			.anyMatch(step -> step.status().getSerializedName().equals("prepared"))
			? "pending"
			: "healthy";
	}

	private static String routeState(
		final TimelineInstance timeline,
		final TaskInstance instance,
		final Identifier taskId,
		final Set<Identifier> reachableFuture
	) {
		if (instance != null) {
			if (
				timeline.currentTask()
					.map(TaskInstance::taskInstanceId)
					.filter(instance.taskInstanceId()::equals)
					.isPresent()
			) {
				return "current";
			}
			return instance.status().getSerializedName().equals("interrupted")
				? "interrupted"
				: "completed";
		}
		return reachableFuture.contains(taskId) ? "future" : "alternate";
	}

	private static List<Identifier> missionOrder(
		final GameDefinition game,
		final DefinitionSnapshot definitions
	) {
		Identifier initial = game.taskTimeline().orElseThrow().initialTask();
		LinkedHashSet<Identifier> ordered = new LinkedHashSet<>();
		ArrayDeque<Identifier> pending = new ArrayDeque<>();
		pending.add(initial);
		while (!pending.isEmpty()) {
			Identifier taskId = pending.removeFirst();
			if (!ordered.add(taskId)) {
				continue;
			}
			TaskDefinition task = definitions.tasks().get(taskId);
			if (task == null || !task.game().equals(game.id())) {
				throw new IllegalArgumentException("mission route is incomplete");
			}
			task.results()
				.values()
				.stream()
				.map(TaskResult::route)
				.flatMap(route -> route.nextTask().stream())
				.distinct()
				.sorted()
				.forEach(pending::addLast);
			if (ordered.size() > TimelineViewS2CPayload.MAX_TASKS) {
				throw new IllegalArgumentException("mission route exceeds the view limit");
			}
		}
		return List.copyOf(ordered);
	}

	private static Set<Identifier> reachableFrom(
		final Identifier current,
		final DefinitionSnapshot definitions
	) {
		Set<Identifier> result = new HashSet<>();
		ArrayDeque<Identifier> pending = new ArrayDeque<>();
		pending.add(current);
		while (!pending.isEmpty()) {
			Identifier taskId = pending.removeFirst();
			if (!result.add(taskId)) {
				continue;
			}
			TaskDefinition task = definitions.tasks().get(taskId);
			if (task == null) {
				continue;
			}
			task.results()
				.values()
				.stream()
				.map(TaskResult::route)
				.flatMap(route -> route.nextTask().stream())
				.distinct()
				.sorted()
				.forEach(pending::addLast);
		}
		result.remove(current);
		return Set.copyOf(result);
	}

	private static boolean visible(final RecapVisibility visibility, final boolean host) {
		return visibility == RecapVisibility.PARTICIPANTS
			|| host && visibility == RecapVisibility.HOST_ONLY;
	}

	private static PlayerView player(
		final FrozenPlayerValue value,
		final Predicate<UUID> online
	) {
		return new PlayerView(
			value.playerId(),
			value.playerName(),
			online.test(value.playerId())
		);
	}

	private static PlayerView player(
		final UUID playerId,
		final WorldStateV3 root,
		final Predicate<UUID> online
	) {
		String name = Optional.ofNullable(root.core().players().get(playerId))
			.map(PlayerRecord::lastKnownName)
			.filter(value -> !value.isBlank())
			.orElse(playerId.toString());
		return new PlayerView(playerId, name, online.test(playerId));
	}

	private static String statisticValue(final TaskStatisticValue value) {
		return switch (value.type()) {
			case BOOLEAN -> Boolean.toString(value.booleanValue().orElseThrow());
			case INTEGER, DURATION_TICKS -> Long.toString(value.longValue().orElseThrow());
			case STRING -> value.stringValue().orElseThrow();
			case IDENTIFIER -> value.identifierValue().orElseThrow().toString();
			case PLAYER -> value.playerValue().orElseThrow().playerName();
		};
	}

	private static Optional<Long> optionalLong(final OptionalLong value) {
		return value.isPresent() ? Optional.of(value.getAsLong()) : Optional.empty();
	}

	private static final class TextBudget {
		private int remaining;

		private TextBudget(final int maximum) {
			this.remaining = maximum;
		}

		private String take(final RichText text) {
			if (this.remaining <= 0) {
				return "";
			}
			String json = text.json();
			if (
				json.length() <= TimelineViewS2CPayload.MAX_COMPONENT_LENGTH
					&& json.length() <= this.remaining
			) {
				this.remaining -= json.length();
				return json;
			}
			int plainLimit = Math.min(
				text.plainText().length(),
				Math.min(TimelineViewS2CPayload.MAX_COMPONENT_LENGTH / 2, this.remaining)
			);
			while (plainLimit >= 0) {
				JsonObject fallback = new JsonObject();
				fallback.addProperty("text", text.plainText().substring(0, plainLimit));
				String candidate = fallback.toString();
				if (
					candidate.length() <= TimelineViewS2CPayload.MAX_COMPONENT_LENGTH
						&& candidate.length() <= this.remaining
				) {
					this.remaining -= candidate.length();
					return candidate;
				}
				plainLimit--;
			}
			return "";
		}
	}

	private static final class FactBudget {
		private int events;
		private int statistics;
		private int participants;
		private int callbacks;

		private FactBudget(
			final int events,
			final int statistics,
			final int participants,
			final int callbacks
		) {
			this.events = events;
			this.statistics = statistics;
			this.participants = participants;
			this.callbacks = callbacks;
		}

		private boolean takeEvent() {
			if (this.events <= 0) {
				return false;
			}
			this.events--;
			return true;
		}

		private boolean takeStatistic() {
			if (this.statistics <= 0) {
				return false;
			}
			this.statistics--;
			return true;
		}

		private boolean takeParticipant() {
			if (this.participants <= 0) {
				return false;
			}
			this.participants--;
			return true;
		}

		private boolean takeCallback() {
			if (this.callbacks <= 0) {
				return false;
			}
			this.callbacks--;
			return true;
		}
	}
}
