package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticType;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticWrite;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.ActionContext;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecordState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure immutable transactions for data-pack-owned task events and recap statistics.
 *
 * <p>Facts live only on the task instance that produced them. This authority never creates a
 * parallel recap history and never accepts a result or route, so recording a fact cannot alter a
 * frozen outcome.
 */
public final class TaskFactAuthority {
	private TaskFactAuthority() {
	}

	/**
	 * Records one server-timestamped event on the current task.
	 *
	 * <p>A missing player denotes a system event. Player-triggered events are limited to the
	 * task's frozen participant roster; event-specific audiences must therefore be resolved before
	 * that roster is frozen.
	 */
	public static OperationResult recordEvent(
		final TimelineInstance current,
		final ActionContext context,
		final TaskDefinition definition,
		final String eventId,
		final Optional<FrozenPlayerValue> player
	) {
		OperationResult failure = contextFailure(current, context, definition);
		if (failure != null) {
			return failure;
		}
		if (eventId == null || player == null) {
			return OperationResult.failed(OperationCode.TARGET_INVALID, "task event input is missing");
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskEventDefinition event = definition.events()
			.stream()
			.filter(candidate -> candidate.id().equals(eventId))
			.findFirst()
			.orElse(null);
		if (event == null) {
			return OperationResult.failed(
				OperationCode.EVENT_NOT_REGISTERED,
				"task event is not registered"
			);
		}
		TaskEventRecordState state = eventState(current, task);
		if (state == null || !event.allowedStates().contains(toDefinitionState(state))) {
			return OperationResult.failed(
				OperationCode.TASK_STATE_MISMATCH,
				"task event is not allowed in the current state"
			);
		}
		if (player.filter(value -> !validPlayer(value)).isPresent()) {
			return OperationResult.failed(OperationCode.TARGET_INVALID, "event player identity is invalid");
		}
		if (
			player
				.map(FrozenPlayerValue::playerId)
				.filter(playerId -> !task.participants().contains(playerId))
				.isPresent()
		) {
			return OperationResult.failed(
				OperationCode.PERMISSION_DENIED,
				"event player is not a frozen task participant"
			);
		}
		List<TaskEventRecord> existing = task.events()
			.stream()
			.filter(record -> record.eventId().equals(eventId))
			.toList();
		if (event.policy() == TaskEventPolicy.ONCE && !existing.isEmpty()) {
			return OperationResult.failed(OperationCode.EVENT_DUPLICATE, "once event was already recorded");
		}
		if (event.policy() == TaskEventPolicy.PER_PLAYER) {
			if (player.isEmpty()) {
				return OperationResult.failed(
					OperationCode.TARGET_INVALID,
					"per-player event requires a player"
				);
			}
			if (
				existing.stream()
					.anyMatch(
						record -> record.player()
							.map(FrozenPlayerValue::playerId)
							.filter(player.orElseThrow().playerId()::equals)
							.isPresent()
					)
			) {
				return OperationResult.failed(
					OperationCode.EVENT_DUPLICATE,
					"per-player event was already recorded for this player"
				);
			}
		}
		if (event.policy() == TaskEventPolicy.REPEATABLE) {
			int maximum = event.maximumRecords().orElse(0);
			if (maximum <= 0) {
				return OperationResult.failed(
					OperationCode.DEFINITION_UNAVAILABLE,
					"repeatable event has no valid record limit"
				);
			}
			if (existing.size() >= maximum) {
				return OperationResult.failed(
					OperationCode.EVENT_LIMIT_REACHED,
					"repeatable event reached its registered limit"
				);
			}
		}
		if (task.events().size() >= WorldStateV3.MAX_TASK_EVENT_RECORDS) {
			return OperationResult.failed(
				OperationCode.EVENT_LIMIT_REACHED,
				"task event record capacity was reached"
			);
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		List<TaskEventRecord> events = new ArrayList<>(task.events().size() + 1);
		events.addAll(task.events());
		events.add(
			new TaskEventRecord(
				eventId,
				current.gameElapsedTicks(),
				task.elapsedTicks(),
				state,
				player
			)
		);
		return OperationResult.changed(
			withCurrentTask(
				current,
				copyTask(task, events, task.statistics()),
				revision
			),
			"task event recorded"
		);
	}

	/**
	 * Applies a typed recap statistic according to its registered write strategy.
	 */
	public static OperationResult writeStatistic(
		final TimelineInstance current,
		final ActionContext context,
		final TaskDefinition definition,
		final TaskStatisticValue requested
	) {
		OperationResult failure = contextFailure(current, context, definition);
		if (failure != null) {
			return failure;
		}
		if (requested == null) {
			return OperationResult.failed(OperationCode.TARGET_INVALID, "task statistic is missing");
		}
		TaskInstance task = current.currentTask().orElseThrow();
		TaskStatus businessStatus = task.status() == TaskStatus.BLOCKED
			? task.resumeStatus().orElseThrow()
			: task.status();
		if (businessStatus == TaskStatus.SETTLED || businessStatus == TaskStatus.INTERRUPTED) {
			return OperationResult.failed(
				OperationCode.TASK_STATE_MISMATCH,
				"settled task statistics are frozen"
			);
		}
		TaskStatisticDefinition statistic = definition.statistics()
			.stream()
			.filter(candidate -> candidate.id().equals(requested.statisticId()))
			.findFirst()
			.orElse(null);
		if (statistic == null) {
			return OperationResult.failed(
				OperationCode.STATISTIC_NOT_REGISTERED,
				"task statistic is not registered"
			);
		}
		TaskStatisticValueType expectedType = toValueType(statistic.type());
		if (requested.type() != expectedType || !validValueShape(requested)) {
			return OperationResult.failed(
				OperationCode.STATISTIC_TYPE_MISMATCH,
				"task statistic value does not match its registered type"
			);
		}
		int existingIndex = indexOfStatistic(task.statistics(), requested.statisticId());
		if (statistic.write() == TaskStatisticWrite.ONCE && existingIndex >= 0) {
			return OperationResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"once statistic is already frozen"
			);
		}
		TaskStatisticValue stored = requested;
		if (statistic.write() == TaskStatisticWrite.INCREMENT) {
			long previous = existingIndex < 0
				? 0L
				: task.statistics().get(existingIndex).longValue().orElseThrow();
			long delta = requested.longValue().orElseThrow();
			try {
				long total = Math.addExact(previous, delta);
				stored = expectedType == TaskStatisticValueType.INTEGER
					? TaskStatisticValue.integer(requested.statisticId(), total)
					: TaskStatisticValue.durationTicks(requested.statisticId(), total);
			} catch (ArithmeticException error) {
				return OperationResult.failed(
					OperationCode.TARGET_INVALID,
					"task statistic increment overflowed"
				);
			}
		}
		if (!withinBounds(stored, statistic)) {
			return OperationResult.failed(
				OperationCode.TARGET_INVALID,
				"task statistic is outside its registered bounds"
			);
		}
		if (task.statistics().size() >= WorldStateV3.MAX_TASK_STATISTIC_VALUES && existingIndex < 0) {
			return OperationResult.failed(
				OperationCode.RESOURCE_BLOCKED,
				"task statistic capacity was reached"
			);
		}
		Long revision = incremented(current.timelineRevision());
		if (revision == null) {
			return revisionExhausted();
		}
		List<TaskStatisticValue> statistics = new ArrayList<>(task.statistics());
		if (existingIndex < 0) {
			statistics.add(stored);
		} else {
			statistics.set(existingIndex, stored);
		}
		return OperationResult.changed(
			withCurrentTask(
				current,
				copyTask(task, task.events(), statistics),
				revision
			),
			"task statistic stored"
		);
	}

	private static OperationResult contextFailure(
		final TimelineInstance current,
		final ActionContext context,
		final TaskDefinition definition
	) {
		if (current == null || context == null) {
			return OperationResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline context is missing");
		}
		if (
			current.status() == TimelineStatus.COMPLETED
				|| current.status() == TimelineStatus.INTERRUPTED
				|| current.currentTask().isEmpty()
		) {
			return OperationResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline has no active task");
		}
		if (!current.instanceId().equals(context.timelineInstanceId())) {
			return OperationResult.failed(OperationCode.TIMELINE_NOT_ACTIVE, "timeline instance changed");
		}
		TaskInstance task = current.currentTask().orElseThrow();
		if (
			!task.taskInstanceId().equals(context.taskInstanceId())
				|| !task.taskId().equals(context.taskId())
		) {
			return OperationResult.failed(OperationCode.TASK_NOT_CURRENT, "current task changed");
		}
		if (
			context.expectedTimelineRevision() < 0L
				|| current.timelineRevision() != context.expectedTimelineRevision()
		) {
			return OperationResult.failed(OperationCode.REVISION_MISMATCH, "timeline revision changed");
		}
		if (
			definition == null
				|| !definition.id().equals(task.taskId())
				|| !definition.game().equals(current.gameId())
				|| !kindMatches(definition, task)
		) {
			return OperationResult.failed(
				OperationCode.DEFINITION_UNAVAILABLE,
				"current task definition is unavailable or mismatched"
			);
		}
		return null;
	}

	private static boolean kindMatches(
		final TaskDefinition definition,
		final TaskInstance task
	) {
		return definition.kind().name().equals(task.kind().name());
	}

	private static TaskEventRecordState eventState(
		final TimelineInstance timeline,
		final TaskInstance task
	) {
		return switch (timeline.status()) {
			case RUNNING -> task.status() == TaskStatus.RUNNING
				? TaskEventRecordState.RUNNING
				: null;
			case SETTLING -> task.status() == TaskStatus.SETTLING
				? TaskEventRecordState.SETTLING
				: null;
			case INTERMISSION -> task.status() == TaskStatus.SETTLED
				? TaskEventRecordState.INTERMISSION
				: null;
			case PRE_START, STARTING, COMPLETED, INTERRUPTED, BLOCKED -> null;
		};
	}

	private static TaskEventState toDefinitionState(final TaskEventRecordState state) {
		return switch (state) {
			case RUNNING -> TaskEventState.RUNNING;
			case SETTLING -> TaskEventState.SETTLING;
			case INTERMISSION -> TaskEventState.INTERMISSION;
		};
	}

	private static TaskStatisticValueType toValueType(final TaskStatisticType type) {
		return switch (type) {
			case BOOLEAN -> TaskStatisticValueType.BOOLEAN;
			case INTEGER -> TaskStatisticValueType.INTEGER;
			case DURATION_TICKS -> TaskStatisticValueType.DURATION_TICKS;
			case STRING -> TaskStatisticValueType.STRING;
			case IDENTIFIER -> TaskStatisticValueType.IDENTIFIER;
			case PLAYER -> TaskStatisticValueType.PLAYER;
		};
	}

	private static boolean validValueShape(final TaskStatisticValue value) {
		int present = (value.booleanValue().isPresent() ? 1 : 0)
			+ (value.longValue().isPresent() ? 1 : 0)
			+ (value.stringValue().isPresent() ? 1 : 0)
			+ (value.identifierValue().isPresent() ? 1 : 0)
			+ (value.playerValue().isPresent() ? 1 : 0);
		if (present != 1) {
			return false;
		}
		return switch (value.type()) {
			case BOOLEAN -> value.booleanValue().isPresent();
			case INTEGER -> value.longValue().isPresent();
			case DURATION_TICKS -> value.longValue().filter(number -> number >= 0L).isPresent();
			case STRING -> value.stringValue()
				.filter(
					text -> !text.isBlank()
						&& text.length() <= WorldStateV3.MAX_TASK_STATISTIC_STRING_LENGTH
				)
				.isPresent();
			case IDENTIFIER -> value.identifierValue().isPresent();
			case PLAYER -> value.playerValue().filter(TaskFactAuthority::validPlayer).isPresent();
		};
	}

	private static boolean validPlayer(final FrozenPlayerValue value) {
		return value != null
			&& value.playerId() != null
			&& value.playerName() != null
			&& !value.playerName().isBlank()
			&& value.playerName().length() <= WorldStateV3.MAX_TASK_FACT_PLAYER_NAME_LENGTH;
	}

	private static boolean withinBounds(
		final TaskStatisticValue value,
		final TaskStatisticDefinition definition
	) {
		if (
			value.type() != TaskStatisticValueType.INTEGER
				&& value.type() != TaskStatisticValueType.DURATION_TICKS
		) {
			return true;
		}
		long number = value.longValue().orElseThrow();
		return definition.minimum().filter(minimum -> number < minimum).isEmpty()
			&& definition.maximum().filter(maximum -> number > maximum).isEmpty();
	}

	private static int indexOfStatistic(
		final List<TaskStatisticValue> statistics,
		final String statisticId
	) {
		for (int index = 0; index < statistics.size(); index++) {
			if (statistics.get(index).statisticId().equals(statisticId)) {
				return index;
			}
		}
		return -1;
	}

	private static Long incremented(final long value) {
		return value == Long.MAX_VALUE ? null : value + 1L;
	}

	private static OperationResult revisionExhausted() {
		return OperationResult.failed(
			OperationCode.RESOURCE_BLOCKED,
			"timeline revision is exhausted"
		);
	}

	private static TaskInstance copyTask(
		final TaskInstance source,
		final List<TaskEventRecord> events,
		final List<TaskStatisticValue> statistics
	) {
		return new TaskInstance(
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
			source.lifecycleCallbackTrigger(),
			source.phaseTransitionTrigger(),
			events,
			statistics
		);
	}

	private static TimelineInstance withCurrentTask(
		final TimelineInstance source,
		final TaskInstance task,
		final long revision
	) {
		return new TimelineInstance(
			source.instanceId(),
			source.gameId(),
			source.contentVersion(),
			source.snapshot(),
			source.status(),
			source.resumeStatus(),
			Optional.of(task),
			source.taskHistory(),
			source.gameElapsedTicks(),
			source.paused(),
			source.pauseReason(),
			source.createdAtServerTick(),
			source.startedAtServerTick(),
			source.completedAtServerTick(),
			revision,
			source.callbackLedger()
		);
	}

	public record OperationResult(
		OperationCode code,
		String message,
		Optional<TimelineInstance> nextState
	) {
		public OperationResult {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (code != OperationCode.SUCCESS && nextState.isPresent()) {
				throw new IllegalArgumentException("failed task-fact operation cannot change state");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		public boolean changed() {
			return this.nextState.isPresent();
		}

		private static OperationResult changed(
			final TimelineInstance next,
			final String message
		) {
			return new OperationResult(OperationCode.SUCCESS, message, Optional.of(next));
		}

		private static OperationResult failed(
			final OperationCode code,
			final String message
		) {
			return new OperationResult(code, message, Optional.empty());
		}
	}
}
