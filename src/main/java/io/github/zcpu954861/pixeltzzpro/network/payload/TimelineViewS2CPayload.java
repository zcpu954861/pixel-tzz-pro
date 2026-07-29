package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bounded, server-authored timeline or terminal recap projection.
 *
 * <p>Visibility is already enforced before this payload is built. A client never receives hidden
 * facts or a live player view that it is expected to conceal locally.</p>
 */
public record TimelineViewS2CPayload(
	int protocolVersion,
	long requestSequence,
	long timelineRevision,
	String status,
	String message,
	boolean hostView,
	boolean recapView,
	Optional<UUID> timelineInstanceId,
	Optional<Identifier> gameId,
	String gameNameJson,
	String timelineStatus,
	long gameElapsedTicks,
	boolean paused,
	String pauseReason,
	String auditStatus,
	List<CallbackView> timelineCallbacks,
	List<TaskView> tasks
) implements CustomPacketPayload {
	public static final int MAX_STATUS_LENGTH = 48;
	public static final int MAX_LOCAL_ID_LENGTH = 128;
	public static final int MAX_MESSAGE_LENGTH = 1_024;
	public static final int MAX_COMPONENT_LENGTH = 8_192;
	public static final int MAX_VALUE_LENGTH = 1_024;
	public static final int MAX_PLAYER_NAME_LENGTH = 64;
	public static final int MAX_TASKS = 256;
	public static final int MAX_TOTAL_EVENTS = 512;
	public static final int MAX_TOTAL_STATISTICS = 256;
	public static final int MAX_TOTAL_PARTICIPANTS = 256;
	public static final int MAX_TOTAL_CALLBACKS = 512;
	public static final int MAX_TOTAL_COMPONENT_CHARACTERS = 512 * 1_024;
	private static final Set<String> RESPONSE_STATUSES = Set.of(
		"ready",
		"unavailable",
		"error"
	);
	private static final Set<String> ROUTE_STATES = Set.of(
		"completed",
		"current",
		"future",
		"alternate",
		"interrupted"
	);
	private static final Set<String> AUDIT_STATUSES = Set.of(
		"healthy",
		"pending",
		"intervention_required",
		"blocked"
	);
	public static final Type<TimelineViewS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("timeline_view_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TimelineViewS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(TimelineViewS2CPayload::write, TimelineViewS2CPayload::new);

	public TimelineViewS2CPayload {
		if (
			protocolVersion < 0
				|| requestSequence < 0L
				|| timelineRevision < 0L
				|| gameElapsedTicks < 0L
		) {
			throw new IllegalArgumentException("timeline view counters are invalid");
		}
		status = known(status, RESPONSE_STATUSES, "status");
		message = requireBounded(message, MAX_MESSAGE_LENGTH, "message");
		timelineInstanceId = Objects.requireNonNull(timelineInstanceId, "timelineInstanceId");
		gameId = Objects.requireNonNull(gameId, "gameId");
		gameNameJson = requireBounded(gameNameJson, MAX_COMPONENT_LENGTH, "gameNameJson");
		timelineStatus = localOptional(timelineStatus, MAX_STATUS_LENGTH, "timelineStatus");
		pauseReason = requireBounded(pauseReason, MAX_MESSAGE_LENGTH, "pauseReason");
		auditStatus = localOptional(auditStatus, MAX_STATUS_LENGTH, "auditStatus");
		if (!auditStatus.isEmpty() && !AUDIT_STATUSES.contains(auditStatus)) {
			throw new IllegalArgumentException("auditStatus is not recognized");
		}
		timelineCallbacks = boundedCopy(
			timelineCallbacks,
			MAX_TOTAL_CALLBACKS,
			"timelineCallbacks"
		);
		tasks = boundedCopy(tasks, MAX_TASKS, "tasks");
		if (status.equals("ready")) {
			if (
				hostView == recapView
					|| timelineInstanceId.isEmpty()
					|| gameId.isEmpty()
					|| timelineStatus.isEmpty()
					|| tasks.isEmpty()
					|| hostView && auditStatus.isEmpty()
			) {
				throw new IllegalArgumentException("ready timeline view has an invalid authority envelope");
			}
		} else if (
			hostView
				|| recapView
				|| timelineInstanceId.isPresent()
				|| gameId.isPresent()
				|| !gameNameJson.isEmpty()
				|| !timelineStatus.isEmpty()
				|| gameElapsedTicks != 0L
				|| paused
				|| !pauseReason.isEmpty()
				|| !auditStatus.isEmpty()
				|| !timelineCallbacks.isEmpty()
				|| !tasks.isEmpty()
		) {
			throw new IllegalArgumentException("unavailable timeline view must not carry timeline data");
		}
		int events = tasks.stream().mapToInt(value -> value.events().size()).sum();
		int statistics = tasks.stream().mapToInt(value -> value.statistics().size()).sum();
		int participants = tasks.stream().mapToInt(value -> value.participants().size()).sum();
		int callbacks = timelineCallbacks.size()
			+ tasks.stream().mapToInt(value -> value.callbacks().size()).sum();
		if (
			events > MAX_TOTAL_EVENTS
				|| statistics > MAX_TOTAL_STATISTICS
				|| participants > MAX_TOTAL_PARTICIPANTS
				|| callbacks > MAX_TOTAL_CALLBACKS
		) {
			throw new IllegalArgumentException("timeline fact projection exceeds aggregate limits");
		}
		if (
			recapView
				&& (
					!auditStatus.isEmpty()
						|| !timelineCallbacks.isEmpty()
						|| tasks.stream().anyMatch(
							task -> !task.participants().isEmpty()
								|| !task.nextTaskIds().isEmpty()
								|| !task.callbacks().isEmpty()
						)
				)
		) {
			throw new IllegalArgumentException("participant recap carries host-only topology or audit data");
		}
		long componentCharacters = gameNameJson.length();
		for (TaskView task : tasks) {
			componentCharacters += task.nameJson().length() + task.descriptionJson().length();
			componentCharacters += task.result()
				.map(value -> (long) value.nameJson().length() + value.recapJson().length())
				.orElse(0L);
			componentCharacters += task.events()
				.stream()
				.mapToLong(value -> value.nameJson().length())
				.sum();
			componentCharacters += task.statistics()
				.stream()
				.mapToLong(value -> value.nameJson().length())
				.sum();
		}
		if (componentCharacters > MAX_TOTAL_COMPONENT_CHARACTERS) {
			throw new IllegalArgumentException("timeline component projection exceeds aggregate character limit");
		}
		if (
			tasks.stream().map(TaskView::taskId).distinct().count() != tasks.size()
				|| tasks.stream()
					.map(TaskView::taskInstanceId)
					.flatMap(Optional::stream)
					.distinct()
					.count()
					!= tasks.stream().filter(value -> value.taskInstanceId().isPresent()).count()
		) {
			throw new IllegalArgumentException("timeline tasks contain duplicate identities");
		}
	}

	private TimelineViewS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readUtf(MAX_STATUS_LENGTH),
			buffer.readUtf(MAX_MESSAGE_LENGTH),
			buffer.readBoolean(),
			buffer.readBoolean(),
			readOptionalUuid(buffer),
			readOptionalIdentifier(buffer, "gameId"),
			buffer.readUtf(MAX_COMPONENT_LENGTH),
			buffer.readUtf(MAX_STATUS_LENGTH),
			buffer.readVarLong(),
			buffer.readBoolean(),
			buffer.readUtf(MAX_MESSAGE_LENGTH),
			buffer.readUtf(MAX_STATUS_LENGTH),
			readList(buffer, MAX_TOTAL_CALLBACKS, "timelineCallbacks", CallbackView::read),
			readList(buffer, MAX_TASKS, "tasks", TaskView::read)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeVarLong(this.timelineRevision);
		buffer.writeUtf(this.status, MAX_STATUS_LENGTH);
		buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
		buffer.writeBoolean(this.hostView);
		buffer.writeBoolean(this.recapView);
		writeOptionalUuid(buffer, this.timelineInstanceId);
		writeOptionalIdentifier(buffer, this.gameId);
		buffer.writeUtf(this.gameNameJson, MAX_COMPONENT_LENGTH);
		buffer.writeUtf(this.timelineStatus, MAX_STATUS_LENGTH);
		buffer.writeVarLong(this.gameElapsedTicks);
		buffer.writeBoolean(this.paused);
		buffer.writeUtf(this.pauseReason, MAX_MESSAGE_LENGTH);
		buffer.writeUtf(this.auditStatus, MAX_STATUS_LENGTH);
		writeList(buffer, this.timelineCallbacks, CallbackView::write);
		writeList(buffer, this.tasks, TaskView::write);
	}

	@Override
	public Type<TimelineViewS2CPayload> type() {
		return TYPE;
	}

	public static TimelineViewS2CPayload unavailable(
		final int protocolVersion,
		final long requestSequence,
		final String message
	) {
		return empty(protocolVersion, requestSequence, "unavailable", message);
	}

	public static TimelineViewS2CPayload error(
		final int protocolVersion,
		final long requestSequence,
		final String message
	) {
		return empty(protocolVersion, requestSequence, "error", message);
	}

	private static TimelineViewS2CPayload empty(
		final int protocolVersion,
		final long requestSequence,
		final String status,
		final String message
	) {
		return new TimelineViewS2CPayload(
			protocolVersion,
			requestSequence,
			0L,
			status,
			message,
			false,
			false,
			Optional.empty(),
			Optional.empty(),
			"",
			"",
			0L,
			false,
			"",
			"",
			List.of(),
			List.of()
		);
	}

	public record TaskView(
		Optional<UUID> taskInstanceId,
		Identifier taskId,
		String kind,
		String routeState,
		String taskStatus,
		String nameJson,
		String descriptionJson,
		long elapsedTicks,
		Optional<Long> startedAtGameTick,
		Optional<Long> settledAtGameTick,
		Optional<Long> durationTicks,
		boolean countsTowardGameTime,
		int participantCount,
		List<PlayerView> participants,
		int omittedParticipantCount,
		List<Identifier> nextTaskIds,
		Optional<ResultView> result,
		Optional<IntermissionView> intermission,
		List<EventView> events,
		List<StatisticView> statistics,
		List<CallbackView> callbacks,
		int omittedEventCount,
		int omittedStatisticCount,
		int omittedCallbackCount
	) {
		public TaskView {
			taskInstanceId = Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			taskId = requireIdentifier(taskId, "taskId");
			kind = local(kind, MAX_STATUS_LENGTH, "task.kind");
			routeState = known(routeState, ROUTE_STATES, "task.routeState");
			taskStatus = local(taskStatus, MAX_STATUS_LENGTH, "task.taskStatus");
			nameJson = requireBounded(nameJson, MAX_COMPONENT_LENGTH, "task.nameJson");
			descriptionJson = requireBounded(
				descriptionJson,
				MAX_COMPONENT_LENGTH,
				"task.descriptionJson"
			);
			startedAtGameTick = Objects.requireNonNull(
				startedAtGameTick,
				"startedAtGameTick"
			);
			settledAtGameTick = Objects.requireNonNull(
				settledAtGameTick,
				"settledAtGameTick"
			);
			durationTicks = Objects.requireNonNull(durationTicks, "durationTicks");
			participants = boundedCopy(participants, MAX_TOTAL_PARTICIPANTS, "task.participants");
			nextTaskIds = boundedCopy(nextTaskIds, MAX_TASKS, "task.nextTaskIds");
			nextTaskIds.forEach(value -> requireIdentifier(value, "task.nextTaskId"));
			if (nextTaskIds.stream().distinct().count() != nextTaskIds.size()) {
				throw new IllegalArgumentException("task nextTaskIds contains duplicates");
			}
			result = Objects.requireNonNull(result, "result");
			intermission = Objects.requireNonNull(intermission, "intermission");
			events = boundedCopy(events, MAX_TOTAL_EVENTS, "task.events");
			statistics = boundedCopy(
				statistics,
				MAX_TOTAL_STATISTICS,
				"task.statistics"
			);
			callbacks = boundedCopy(callbacks, MAX_TOTAL_CALLBACKS, "task.callbacks");
			if (
				elapsedTicks < 0L
					|| startedAtGameTick.filter(value -> value < 0L).isPresent()
					|| settledAtGameTick.filter(value -> value < 0L).isPresent()
					|| settledAtGameTick.isPresent()
						&& (
							startedAtGameTick.isEmpty()
								|| settledAtGameTick.orElseThrow()
									< startedAtGameTick.orElseThrow()
						)
					|| durationTicks.filter(value -> value <= 0L).isPresent()
					|| participantCount < 0
					|| omittedParticipantCount < 0
					|| omittedEventCount < 0
					|| omittedStatisticCount < 0
					|| omittedCallbackCount < 0
					|| participants.size() + omittedParticipantCount > participantCount
			) {
				throw new IllegalArgumentException("task view counters are invalid");
			}
			boolean terminal = taskStatus.equals("settled") || taskStatus.equals("interrupted");
			if (
				taskInstanceId.isPresent() != startedAtGameTick.isPresent()
					|| taskInstanceId.isPresent() && terminal != settledAtGameTick.isPresent()
			) {
				throw new IllegalArgumentException("task view lifecycle ticks are inconsistent");
			}
			if (
				taskInstanceId.isEmpty()
					&& (
						!routeState.equals("future")
							&& !routeState.equals("alternate")
							|| !taskStatus.equals("not_started")
							|| elapsedTicks != 0L
							|| startedAtGameTick.isPresent()
							|| settledAtGameTick.isPresent()
							|| participantCount != 0
							|| !participants.isEmpty()
							|| omittedParticipantCount != 0
							|| result.isPresent()
							|| intermission.isPresent()
							|| !events.isEmpty()
							|| !statistics.isEmpty()
							|| !callbacks.isEmpty()
					)
			) {
				throw new IllegalArgumentException("future task view carries runtime state");
			}
			Set<String> statisticIds = new HashSet<>();
			if (statistics.stream().anyMatch(value -> !statisticIds.add(value.statisticId()))) {
				throw new IllegalArgumentException("task statistics contain duplicate IDs");
			}
		}

		private static TaskView read(final RegistryFriendlyByteBuf buffer) {
			return new TaskView(
				readOptionalUuid(buffer),
				readIdentifier(buffer, "taskId"),
				buffer.readUtf(MAX_STATUS_LENGTH),
				buffer.readUtf(MAX_STATUS_LENGTH),
				buffer.readUtf(MAX_STATUS_LENGTH),
				buffer.readUtf(MAX_COMPONENT_LENGTH),
				buffer.readUtf(MAX_COMPONENT_LENGTH),
				buffer.readVarLong(),
				readOptionalLong(buffer),
				readOptionalLong(buffer),
				readOptionalLong(buffer),
				buffer.readBoolean(),
				buffer.readVarInt(),
				readList(buffer, MAX_TOTAL_PARTICIPANTS, "task.participants", PlayerView::read),
				buffer.readVarInt(),
				readList(buffer, MAX_TASKS, "task.nextTaskIds", value -> readIdentifier(value, "task.nextTaskId")),
				readOptional(buffer, ResultView::read),
				readOptional(buffer, IntermissionView::read),
				readList(buffer, MAX_TOTAL_EVENTS, "task.events", EventView::read),
				readList(buffer, MAX_TOTAL_STATISTICS, "task.statistics", StatisticView::read),
				readList(buffer, MAX_TOTAL_CALLBACKS, "task.callbacks", CallbackView::read),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt()
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final TaskView value) {
			writeOptionalUuid(buffer, value.taskInstanceId);
			writeIdentifier(buffer, value.taskId);
			buffer.writeUtf(value.kind, MAX_STATUS_LENGTH);
			buffer.writeUtf(value.routeState, MAX_STATUS_LENGTH);
			buffer.writeUtf(value.taskStatus, MAX_STATUS_LENGTH);
			buffer.writeUtf(value.nameJson, MAX_COMPONENT_LENGTH);
			buffer.writeUtf(value.descriptionJson, MAX_COMPONENT_LENGTH);
			buffer.writeVarLong(value.elapsedTicks);
			writeOptionalLong(buffer, value.startedAtGameTick);
			writeOptionalLong(buffer, value.settledAtGameTick);
			writeOptionalLong(buffer, value.durationTicks);
			buffer.writeBoolean(value.countsTowardGameTime);
			buffer.writeVarInt(value.participantCount);
			writeList(buffer, value.participants, PlayerView::write);
			buffer.writeVarInt(value.omittedParticipantCount);
			writeList(buffer, value.nextTaskIds, (output, taskId) -> writeIdentifier(output, taskId));
			writeOptional(buffer, value.result, ResultView::write);
			writeOptional(buffer, value.intermission, IntermissionView::write);
			writeList(buffer, value.events, EventView::write);
			writeList(buffer, value.statistics, StatisticView::write);
			writeList(buffer, value.callbacks, CallbackView::write);
			buffer.writeVarInt(value.omittedEventCount);
			buffer.writeVarInt(value.omittedStatisticCount);
			buffer.writeVarInt(value.omittedCallbackCount);
		}
	}

	public record CallbackView(
		String stepKey,
		Identifier functionId,
		Optional<PlayerView> player,
		String status,
		int attemptCount,
		String lastError
	) {
		public CallbackView {
			stepKey = requireBounded(stepKey, 192, "callback.stepKey");
			functionId = requireIdentifier(functionId, "callback.functionId");
			player = Objects.requireNonNull(player, "player");
			status = local(status, MAX_STATUS_LENGTH, "callback.status");
			lastError = requireBounded(lastError, MAX_MESSAGE_LENGTH, "callback.lastError");
			if (stepKey.isBlank() || attemptCount <= 0) {
				throw new IllegalArgumentException("callback view is invalid");
			}
			boolean needsError = status.equals("failed") || status.equals("outcome_unknown");
			if (needsError != !lastError.isEmpty()) {
				throw new IllegalArgumentException("callback error does not match its status");
			}
		}

		private static CallbackView read(final RegistryFriendlyByteBuf buffer) {
			return new CallbackView(
				buffer.readUtf(192),
				readIdentifier(buffer, "callback.functionId"),
				readOptional(buffer, PlayerView::read),
				buffer.readUtf(MAX_STATUS_LENGTH),
				buffer.readVarInt(),
				buffer.readUtf(MAX_MESSAGE_LENGTH)
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final CallbackView value) {
			buffer.writeUtf(value.stepKey, 192);
			writeIdentifier(buffer, value.functionId);
			writeOptional(buffer, value.player, PlayerView::write);
			buffer.writeUtf(value.status, MAX_STATUS_LENGTH);
			buffer.writeVarInt(value.attemptCount);
			buffer.writeUtf(value.lastError, MAX_MESSAGE_LENGTH);
		}
	}

	public record ResultView(
		String resultId,
		String nameJson,
		String recapJson,
		String semantic,
		long frozenAtGameTick
	) {
		public ResultView {
			resultId = local(resultId, MAX_LOCAL_ID_LENGTH, "result.resultId");
			nameJson = requireBounded(nameJson, MAX_COMPONENT_LENGTH, "result.nameJson");
			recapJson = requireBounded(recapJson, MAX_COMPONENT_LENGTH, "result.recapJson");
			semantic = requireBounded(semantic, PayloadCodecUtil.MAX_IDENTIFIER_LENGTH, "result.semantic");
			if (semantic.isBlank() || frozenAtGameTick < 0L) {
				throw new IllegalArgumentException("result view is invalid");
			}
		}

		private static ResultView read(final RegistryFriendlyByteBuf buffer) {
			return new ResultView(
				buffer.readUtf(MAX_LOCAL_ID_LENGTH),
				buffer.readUtf(MAX_COMPONENT_LENGTH),
				buffer.readUtf(MAX_COMPONENT_LENGTH),
				buffer.readUtf(PayloadCodecUtil.MAX_IDENTIFIER_LENGTH),
				buffer.readVarLong()
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final ResultView value) {
			buffer.writeUtf(value.resultId, MAX_LOCAL_ID_LENGTH);
			buffer.writeUtf(value.nameJson, MAX_COMPONENT_LENGTH);
			buffer.writeUtf(value.recapJson, MAX_COMPONENT_LENGTH);
			buffer.writeUtf(value.semantic, PayloadCodecUtil.MAX_IDENTIFIER_LENGTH);
			buffer.writeVarLong(value.frozenAtGameTick);
		}
	}

	public record IntermissionView(
		long durationTicks,
		long elapsedTicks,
		boolean countsTowardGameTime,
		boolean paused
	) {
		public IntermissionView {
			if (durationTicks <= 0L || elapsedTicks < 0L || elapsedTicks > durationTicks) {
				throw new IllegalArgumentException("intermission view ticks are invalid");
			}
		}

		private static IntermissionView read(final RegistryFriendlyByteBuf buffer) {
			return new IntermissionView(
				buffer.readVarLong(),
				buffer.readVarLong(),
				buffer.readBoolean(),
				buffer.readBoolean()
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final IntermissionView value
		) {
			buffer.writeVarLong(value.durationTicks);
			buffer.writeVarLong(value.elapsedTicks);
			buffer.writeBoolean(value.countsTowardGameTime);
			buffer.writeBoolean(value.paused);
		}
	}

	public record EventView(
		String eventId,
		String nameJson,
		long gameElapsedTicks,
		long taskElapsedTicks,
		String state,
		Optional<PlayerView> player
	) {
		public EventView {
			eventId = local(eventId, MAX_LOCAL_ID_LENGTH, "event.eventId");
			nameJson = requireBounded(nameJson, MAX_COMPONENT_LENGTH, "event.nameJson");
			state = local(state, MAX_STATUS_LENGTH, "event.state");
			player = Objects.requireNonNull(player, "player");
			if (gameElapsedTicks < 0L || taskElapsedTicks < 0L) {
				throw new IllegalArgumentException("event ticks are invalid");
			}
		}

		private static EventView read(final RegistryFriendlyByteBuf buffer) {
			return new EventView(
				buffer.readUtf(MAX_LOCAL_ID_LENGTH),
				buffer.readUtf(MAX_COMPONENT_LENGTH),
				buffer.readVarLong(),
				buffer.readVarLong(),
				buffer.readUtf(MAX_STATUS_LENGTH),
				readOptional(buffer, PlayerView::read)
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final EventView value) {
			buffer.writeUtf(value.eventId, MAX_LOCAL_ID_LENGTH);
			buffer.writeUtf(value.nameJson, MAX_COMPONENT_LENGTH);
			buffer.writeVarLong(value.gameElapsedTicks);
			buffer.writeVarLong(value.taskElapsedTicks);
			buffer.writeUtf(value.state, MAX_STATUS_LENGTH);
			writeOptional(buffer, value.player, PlayerView::write);
		}
	}

	public record StatisticView(
		String statisticId,
		String nameJson,
		String type,
		String value,
		Optional<PlayerView> player
	) {
		public StatisticView {
			statisticId = local(statisticId, MAX_LOCAL_ID_LENGTH, "statistic.statisticId");
			nameJson = requireBounded(nameJson, MAX_COMPONENT_LENGTH, "statistic.nameJson");
			type = local(type, MAX_STATUS_LENGTH, "statistic.type");
			value = requireBounded(value, MAX_VALUE_LENGTH, "statistic.value");
			player = Objects.requireNonNull(player, "player");
			if (type.equals("player") != player.isPresent()) {
				throw new IllegalArgumentException("player statistic identity does not match its type");
			}
		}

		private static StatisticView read(final RegistryFriendlyByteBuf buffer) {
			return new StatisticView(
				buffer.readUtf(MAX_LOCAL_ID_LENGTH),
				buffer.readUtf(MAX_COMPONENT_LENGTH),
				buffer.readUtf(MAX_STATUS_LENGTH),
				buffer.readUtf(MAX_VALUE_LENGTH),
				readOptional(buffer, PlayerView::read)
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final StatisticView value
		) {
			buffer.writeUtf(value.statisticId, MAX_LOCAL_ID_LENGTH);
			buffer.writeUtf(value.nameJson, MAX_COMPONENT_LENGTH);
			buffer.writeUtf(value.type, MAX_STATUS_LENGTH);
			buffer.writeUtf(value.value, MAX_VALUE_LENGTH);
			writeOptional(buffer, value.player, PlayerView::write);
		}
	}

	public record PlayerView(UUID playerId, String name, boolean online) {
		public PlayerView {
			playerId = Objects.requireNonNull(playerId, "playerId");
			name = requireBounded(name, MAX_PLAYER_NAME_LENGTH, "player.name");
			if (name.isBlank()) {
				throw new IllegalArgumentException("timeline player name cannot be blank");
			}
		}

		private static PlayerView read(final RegistryFriendlyByteBuf buffer) {
			return new PlayerView(
				buffer.readUUID(),
				buffer.readUtf(MAX_PLAYER_NAME_LENGTH),
				buffer.readBoolean()
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final PlayerView value) {
			buffer.writeUUID(value.playerId);
			buffer.writeUtf(value.name, MAX_PLAYER_NAME_LENGTH);
			buffer.writeBoolean(value.online);
		}
	}

	private static String known(
		final String value,
		final Set<String> allowed,
		final String field
	) {
		String local = local(value, MAX_STATUS_LENGTH, field);
		if (!allowed.contains(local)) {
			throw new IllegalArgumentException(field + " is not recognized");
		}
		return local;
	}

	private static String localOptional(
		final String value,
		final int maximumLength,
		final String field
	) {
		String bounded = requireBounded(value, maximumLength, field);
		return bounded.isEmpty() ? bounded : local(bounded, maximumLength, field);
	}

	private static String local(
		final String value,
		final int maximumLength,
		final String field
	) {
		String bounded = requireBounded(value, maximumLength, field);
		if (
			bounded.isBlank()
				|| !bounded.equals(bounded.toLowerCase(Locale.ROOT))
				|| !bounded.matches("[a-z0-9_.-]+")
		) {
			throw new IllegalArgumentException(field + " must match [a-z0-9_.-]+");
		}
		return bounded;
	}

	private static <T> List<T> boundedCopy(
		final List<T> values,
		final int maximum,
		final String field
	) {
		Objects.requireNonNull(values, field);
		if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException(field + " exceeds its bounded size");
		}
		return List.copyOf(values);
	}

	private static Optional<Identifier> readOptionalIdentifier(
		final RegistryFriendlyByteBuf buffer,
		final String field
	) {
		return buffer.readBoolean()
			? Optional.of(readIdentifier(buffer, field))
			: Optional.empty();
	}

	private static void writeOptionalIdentifier(
		final RegistryFriendlyByteBuf buffer,
		final Optional<Identifier> value
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(identifier -> writeIdentifier(buffer, identifier));
	}

	private static Optional<UUID> readOptionalUuid(final RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
	}

	private static void writeOptionalUuid(
		final RegistryFriendlyByteBuf buffer,
		final Optional<UUID> value
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(buffer::writeUUID);
	}

	private static Optional<Long> readOptionalLong(final RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? Optional.of(buffer.readVarLong()) : Optional.empty();
	}

	private static void writeOptionalLong(
		final RegistryFriendlyByteBuf buffer,
		final Optional<Long> value
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(buffer::writeVarLong);
	}

	private static <T> Optional<T> readOptional(
		final RegistryFriendlyByteBuf buffer,
		final java.util.function.Function<RegistryFriendlyByteBuf, T> reader
	) {
		return buffer.readBoolean() ? Optional.of(reader.apply(buffer)) : Optional.empty();
	}

	private static <T> void writeOptional(
		final RegistryFriendlyByteBuf buffer,
		final Optional<T> value,
		final java.util.function.BiConsumer<RegistryFriendlyByteBuf, T> writer
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(present -> writer.accept(buffer, present));
	}
}
