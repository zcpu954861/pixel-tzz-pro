package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RecoverySnapshotMetadata;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Additive world-state schema v3 envelope.
 *
 * <p>The complete, already accepted schema-v2 payload remains the compatibility core. Timeline and
 * exclusive-choice state are stored beside it so existing 2C authorities can keep using the core
 * adapter while 2D authorities transact against this root.
 */
public record WorldStateV3(
	int schemaVersion,
	WorldStateV2 core,
	Optional<UUID> activeGameInstanceId,
	Optional<TimelineInstance> timeline,
	Optional<ReadinessInstance> readiness,
	List<ExclusiveReservation> exclusiveReservations,
	List<RecoverySnapshotMetadata> recoverySnapshots
) {
	public static final int SCHEMA_VERSION = 3;
	public static final int MAX_TIMELINE_TASKS = 256;
	public static final int MAX_TIMELINE_PARTICIPANTS = WorldStateV2.MAX_PLAYER_RECORDS;
	public static final int MAX_CALLBACK_STEPS = 4_096;
	public static final int MAX_TASK_EVENT_RECORDS = 4_096;
	public static final int MAX_TASK_STATISTIC_VALUES = 64;
	public static final int MAX_TASK_FACT_PLAYER_NAME_LENGTH = 64;
	public static final int MAX_TASK_STATISTIC_STRING_LENGTH = 1_024;
	public static final int MAX_EXCLUSIVE_RESERVATIONS = 4_096;
	public static final int MAX_RECOVERY_SNAPSHOTS = 16;

	private static final Pattern STABLE_VALUE = Pattern.compile("[a-z0-9_.-]+");
	private static final Codec<List<ExclusiveReservation>> RESERVATIONS_CODEC = ExclusiveReservation.CODEC
		.sizeLimitedListOf(MAX_EXCLUSIVE_RESERVATIONS);
	private static final Codec<List<RecoverySnapshotMetadata>> RECOVERY_SNAPSHOTS_CODEC =
		RecoverySnapshotMetadata.CODEC.sizeLimitedListOf(MAX_RECOVERY_SNAPSHOTS);

	public static final Codec<WorldStateV3> CODEC = Codec.lazyInitialized(
		() -> RecordCodecBuilder.<WorldStateV3>create(
			instance -> instance.group(
					Codec.INT.fieldOf("schema_version").forGetter(WorldStateV3::schemaVersion),
					WorldStateV2.CODEC.fieldOf("core").forGetter(WorldStateV3::core),
					UUIDUtil.STRING_CODEC
						.optionalFieldOf("active_game_instance_id")
						.forGetter(WorldStateV3::activeGameInstanceId),
					TimelineInstance.CODEC.optionalFieldOf("timeline").forGetter(WorldStateV3::timeline),
					ReadinessInstance.CODEC
						.optionalFieldOf("readiness")
						.forGetter(WorldStateV3::readiness),
					RESERVATIONS_CODEC
						.fieldOf("exclusive_reservations")
						.forGetter(WorldStateV3::exclusiveReservations),
					RECOVERY_SNAPSHOTS_CODEC
						.fieldOf("recovery_snapshots")
						.forGetter(WorldStateV3::recoverySnapshots)
				)
				.apply(instance, WorldStateV3::new)
		).validate(WorldStateV3::validated)
	);

	public WorldStateV3 {
		Objects.requireNonNull(core, "core");
		activeGameInstanceId = requiredOptional(activeGameInstanceId, "activeGameInstanceId");
		timeline = requiredOptional(timeline, "timeline");
		readiness = requiredOptional(readiness, "readiness");
		exclusiveReservations = exclusiveReservations.stream()
			.sorted(ExclusiveReservation.ORDER)
			.toList();
		recoverySnapshots = List.copyOf(recoverySnapshots);
	}

	public static WorldStateV3 initial() {
		return new WorldStateV3(
			SCHEMA_VERSION,
			WorldStateV2.initial(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			List.of(),
			List.of()
		);
	}

	/**
	 * Compatibility constructor for the schema-v3 shape that predates persisted readiness.
	 */
	public WorldStateV3(
		final int schemaVersion,
		final WorldStateV2 core,
		final Optional<UUID> activeGameInstanceId,
		final Optional<TimelineInstance> timeline,
		final List<ExclusiveReservation> exclusiveReservations,
		final List<RecoverySnapshotMetadata> recoverySnapshots
	) {
		this(
			schemaVersion,
			core,
			activeGameInstanceId,
			timeline,
			Optional.empty(),
			exclusiveReservations,
			recoverySnapshots
		);
	}

	public long stateRevision() {
		return this.core.stateRevision();
	}

	public WorldStateV3 withCore(final WorldStateV2 value) {
		Objects.requireNonNull(value, "value");
		if (!value.activeGameId().equals(this.core.activeGameId())) {
			throw new IllegalArgumentException(
				"active game changes must use beginGameInstance or a fresh initial state"
			);
		}
		return new WorldStateV3(
			this.schemaVersion,
			value,
			this.activeGameInstanceId,
			this.timeline,
			this.readiness,
			this.exclusiveReservations,
			this.recoverySnapshots
		);
	}

	/**
	 * Starts a fresh game-instance scope, including when replaying the same game definition.
	 */
	public WorldStateV3 beginGameInstance(
		final Identifier gameId,
		final Identifier phaseId,
		final UUID gameInstanceId
	) {
		Objects.requireNonNull(gameId, "gameId");
		Objects.requireNonNull(phaseId, "phaseId");
		Objects.requireNonNull(gameInstanceId, "gameInstanceId");
		return new WorldStateV3(
			this.schemaVersion,
			this.core.withActivity(gameId, phaseId),
			Optional.of(gameInstanceId),
			Optional.empty(),
			Optional.empty(),
			List.of(),
			this.recoverySnapshots
		);
	}

	public WorldStateV3 withTimeline(final Optional<TimelineInstance> value) {
		return new WorldStateV3(
			this.schemaVersion,
			this.core,
			this.activeGameInstanceId,
			value,
			this.readiness,
			this.exclusiveReservations,
			this.recoverySnapshots
		);
	}

	public WorldStateV3 withReadiness(final Optional<ReadinessInstance> value) {
		return new WorldStateV3(
			this.schemaVersion,
			this.core,
			this.activeGameInstanceId,
			this.timeline,
			Objects.requireNonNull(value, "value"),
			this.exclusiveReservations,
			this.recoverySnapshots
		);
	}

	public WorldStateV3 withExclusiveReservations(final List<ExclusiveReservation> value) {
		return new WorldStateV3(
			this.schemaVersion,
			this.core,
			this.activeGameInstanceId,
			this.timeline,
			this.readiness,
			value,
			this.recoverySnapshots
		);
	}

	public WorldStateV3 withRecoverySnapshots(final List<RecoverySnapshotMetadata> value) {
		return new WorldStateV3(
			this.schemaVersion,
			this.core,
			this.activeGameInstanceId,
			this.timeline,
			this.readiness,
			this.exclusiveReservations,
			value
		);
	}

	public DataResult<WorldStateV3> validated() {
		String error = validationError();
		return error == null ? DataResult.success(this) : DataResult.error(() -> error);
	}

	private String validationError() {
		if (this.schemaVersion != SCHEMA_VERSION) {
			return "schema v3 payload declared schema " + this.schemaVersion;
		}
		if (this.core.validated().error().isPresent()) {
			return "schema v3 core is invalid: " + this.core.validated().error().orElseThrow().message();
		}
		if (this.core.activeGameId().isPresent() != this.activeGameInstanceId.isPresent()) {
			return "active game and game-instance scope must be present together";
		}
		if (this.timeline.isPresent()) {
			TimelineInstance value = this.timeline.orElseThrow();
			String nested = value.validationError();
			if (nested != null) {
				return "timeline: " + nested;
			}
			if (this.core.activeGameId().filter(value.gameId()::equals).isEmpty()) {
				return "timeline game does not match the active core game";
			}
		}
		if (this.readiness.isPresent()) {
			ReadinessInstance value = this.readiness.orElseThrow();
			String nested = value.validationError();
			if (nested != null) {
				return "readiness: " + nested;
			}
			if (this.core.activeGameId().filter(value.flow().identity().gameId()::equals).isEmpty()) {
				return "readiness game does not match the active core game";
			}
			if (this.activeGameInstanceId.filter(value.gameInstanceId()::equals).isEmpty()) {
				return "readiness game-instance scope does not match the active game instance";
			}
		}
		if (this.exclusiveReservations.size() > MAX_EXCLUSIVE_RESERVATIONS) {
			return "exclusive reservation count exceeds " + MAX_EXCLUSIVE_RESERVATIONS;
		}
		Set<ReservationOptionKey> optionKeys = new HashSet<>();
		Set<ReservationHolderKey> holderKeys = new HashSet<>();
		for (ExclusiveReservation reservation : this.exclusiveReservations) {
			String nested = reservation.validationError();
			if (nested != null) {
				return "exclusive reservation: " + nested;
			}
			if (this.core.activeGameId().filter(reservation.gameId()::equals).isEmpty()) {
				return "exclusive reservation game does not match the active core game";
			}
			if (!optionKeys.add(reservation.optionKey())) {
				return "duplicate exclusive reservation option";
			}
			if (!holderKeys.add(reservation.holderKey())) {
				return "player has duplicate " + reservation.status().getSerializedName() + " reservations";
			}
		}
		if (this.recoverySnapshots.size() > MAX_RECOVERY_SNAPSHOTS) {
			return "recovery snapshot count exceeds " + MAX_RECOVERY_SNAPSHOTS;
		}
		Set<String> recoveryFiles = new HashSet<>();
		for (RecoverySnapshotMetadata recovery : this.recoverySnapshots) {
			if (!recoveryFiles.add(recovery.fileName())) {
				return "duplicate recovery snapshot file name " + recovery.fileName();
			}
		}
		return null;
	}

	/**
	 * Persisted readiness session kept separate from the ordinary forced-flow slot.
	 *
	 * <p>The embedded flow snapshot is intentionally retained even after everyone is ready, because
	 * a configured disconnect or later identity change can invalidate one member and must reopen
	 * the exact page generation that the group originally accepted.
	 */
	public record ReadinessInstance(
		UUID gameInstanceId,
		ForcedFlowInstance flow,
		boolean disconnectInvalidates,
		boolean hostCanForce
	) {
		public static final Codec<ReadinessInstance> CODEC =
			RecordCodecBuilder.<ReadinessInstance>create(
				instance -> instance.group(
						UUIDUtil.STRING_CODEC
							.fieldOf("game_instance_id")
							.forGetter(ReadinessInstance::gameInstanceId),
						ForcedFlowInstance.CODEC
							.fieldOf("flow")
							.forGetter(ReadinessInstance::flow),
						Codec.BOOL
							.fieldOf("disconnect_invalidates")
							.forGetter(ReadinessInstance::disconnectInvalidates),
						Codec.BOOL
							.fieldOf("host_can_force")
							.forGetter(ReadinessInstance::hostCanForce)
					)
					.apply(instance, ReadinessInstance::new)
			).validate(ReadinessInstance::validated);

		public ReadinessInstance {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(flow, "flow");
		}

		private DataResult<ReadinessInstance> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				this.flow.runtime().status()
					!= io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus.ACTIVE
					&& this.flow.runtime().status()
						!= io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus.COMPLETED
			) {
				return "readiness flow must be active or complete";
			}
			if (
				this.flow.runtime().status()
						== io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus.COMPLETED
					!= (
						this.flow.runtime().completedCount()
							== this.flow.runtime().totalCount()
					)
			) {
				return "readiness completion status does not match its member counts";
			}
			long activeMembers = this.flow.runtime().members().values().stream()
				.filter(member ->
					member.status()
						!= io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus.REMOVED
				)
				.count();
			long completedMembers = this.flow.runtime().members().values().stream()
				.filter(member ->
					member.status()
						== io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus.COMPLETED
						|| member.status()
							== io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus.ALREADY_COMPLETE
				)
				.count();
			if (
				activeMembers != this.flow.runtime().totalCount()
					|| completedMembers != this.flow.runtime().completedCount()
			) {
				return "readiness member counts do not match its roster";
			}
			return null;
		}
	}

	public record TimelineInstance(
		UUID instanceId,
		Identifier gameId,
		int contentVersion,
		FrozenDocument snapshot,
		TimelineStatus status,
		Optional<TimelineStatus> resumeStatus,
		Optional<TaskInstance> currentTask,
		List<TaskInstance> taskHistory,
		long gameElapsedTicks,
		boolean paused,
		Optional<String> pauseReason,
		long createdAtServerTick,
		Optional<Long> startedAtServerTick,
		Optional<Long> completedAtServerTick,
		long timelineRevision,
		List<CallbackStep> callbackLedger
	) {
		private static final Codec<List<TaskInstance>> TASK_HISTORY_CODEC = TaskInstance.CODEC
			.sizeLimitedListOf(MAX_TIMELINE_TASKS);
		private static final Codec<List<CallbackStep>> CALLBACK_LEDGER_CODEC = CallbackStep.CODEC
			.sizeLimitedListOf(MAX_CALLBACK_STEPS);

		public static final Codec<TimelineInstance> CODEC = RecordCodecBuilder.<TimelineInstance>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(TimelineInstance::instanceId),
					Identifier.CODEC.fieldOf("game_id").forGetter(TimelineInstance::gameId),
					Codec.INT.fieldOf("content_version").forGetter(TimelineInstance::contentVersion),
					FrozenDocument.CODEC.fieldOf("snapshot").forGetter(TimelineInstance::snapshot),
					TimelineStatus.CODEC.fieldOf("status").forGetter(TimelineInstance::status),
					TimelineStatus.CODEC.optionalFieldOf("resume_status").forGetter(TimelineInstance::resumeStatus),
					TaskInstance.CODEC.optionalFieldOf("current_task").forGetter(TimelineInstance::currentTask),
					TASK_HISTORY_CODEC.fieldOf("task_history").forGetter(TimelineInstance::taskHistory),
					Codec.LONG.fieldOf("game_elapsed_ticks").forGetter(TimelineInstance::gameElapsedTicks),
					Codec.BOOL.fieldOf("paused").forGetter(TimelineInstance::paused),
					Codec.sizeLimitedString(1_024)
						.optionalFieldOf("pause_reason")
						.forGetter(TimelineInstance::pauseReason),
					Codec.LONG.fieldOf("created_at_server_tick").forGetter(TimelineInstance::createdAtServerTick),
					Codec.LONG
						.optionalFieldOf("started_at_server_tick")
						.forGetter(TimelineInstance::startedAtServerTick),
					Codec.LONG
						.optionalFieldOf("completed_at_server_tick")
						.forGetter(TimelineInstance::completedAtServerTick),
					Codec.LONG.fieldOf("timeline_revision").forGetter(TimelineInstance::timelineRevision),
					CALLBACK_LEDGER_CODEC
						.fieldOf("callback_ledger")
						.forGetter(TimelineInstance::callbackLedger)
				)
				.apply(instance, TimelineInstance::new)
		).validate(TimelineInstance::validated);

		public TimelineInstance {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(snapshot, "snapshot");
			Objects.requireNonNull(status, "status");
			resumeStatus = requiredOptional(resumeStatus, "resumeStatus");
			currentTask = requiredOptional(currentTask, "currentTask");
			taskHistory = List.copyOf(taskHistory);
			pauseReason = requiredOptional(pauseReason, "pauseReason");
			startedAtServerTick = requiredOptional(startedAtServerTick, "startedAtServerTick");
			completedAtServerTick = requiredOptional(completedAtServerTick, "completedAtServerTick");
			callbackLedger = List.copyOf(callbackLedger);
		}

		public TimelineInstance withTimelineRevision(final long value) {
			return new TimelineInstance(
				this.instanceId,
				this.gameId,
				this.contentVersion,
				this.snapshot,
				this.status,
				this.resumeStatus,
				this.currentTask,
				this.taskHistory,
				this.gameElapsedTicks,
				this.paused,
				this.pauseReason,
				this.createdAtServerTick,
				this.startedAtServerTick,
				this.completedAtServerTick,
				value,
				this.callbackLedger
			);
		}

		public TimelineInstance withSnapshot(final FrozenDocument value) {
			return new TimelineInstance(
				this.instanceId,
				this.gameId,
				this.contentVersion,
				Objects.requireNonNull(value, "value"),
				this.status,
				this.resumeStatus,
				this.currentTask,
				this.taskHistory,
				this.gameElapsedTicks,
				this.paused,
				this.pauseReason,
				this.createdAtServerTick,
				this.startedAtServerTick,
				this.completedAtServerTick,
				this.timelineRevision,
				this.callbackLedger
			);
		}

		private DataResult<TimelineInstance> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				this.contentVersion <= 0
					|| this.gameElapsedTicks < 0L
					|| this.createdAtServerTick < 0L
					|| this.timelineRevision < 0L
			) {
				return "invalid timeline version, tick, or revision";
			}
			if ((this.status == TimelineStatus.BLOCKED) != this.resumeStatus.isPresent()) {
				return "only a blocked timeline must carry a resume status";
			}
			if (this.resumeStatus.filter(TimelineStatus.BLOCKED::equals).isPresent()) {
				return "timeline resume status cannot itself be blocked";
			}
			if (this.paused != this.pauseReason.isPresent()) {
				return "paused timeline and pause reason must be present together";
			}
			if (
				this.startedAtServerTick.filter(value -> value < this.createdAtServerTick).isPresent()
					|| this.completedAtServerTick.filter(value -> value < this.createdAtServerTick).isPresent()
					|| (
						this.startedAtServerTick.isPresent()
							&& this.completedAtServerTick.isPresent()
							&& this.completedAtServerTick.orElseThrow() < this.startedAtServerTick.orElseThrow()
					)
			) {
				return "invalid timeline lifecycle ticks";
			}
			TimelineStatus businessStatus = this.status == TimelineStatus.BLOCKED
				? this.resumeStatus.orElseThrow()
				: this.status;
			if (
				this.status == TimelineStatus.BLOCKED
					&& (
						businessStatus == TimelineStatus.PRE_START
							|| businessStatus == TimelineStatus.COMPLETED
							|| businessStatus == TimelineStatus.INTERRUPTED
					)
			) {
				return "blocked timeline cannot resume a non-active or terminal status";
			}
			boolean terminal = businessStatus == TimelineStatus.COMPLETED
				|| businessStatus == TimelineStatus.INTERRUPTED;
			if (terminal != this.completedAtServerTick.isPresent()) {
				return "terminal timeline and completion tick must be present together";
			}
			boolean requiresCurrentTask = switch (businessStatus) {
				case STARTING, RUNNING, SETTLING, INTERMISSION -> true;
				case PRE_START, COMPLETED, INTERRUPTED -> false;
				case BLOCKED -> throw new IllegalStateException("blocked business status");
			};
			if (requiresCurrentTask != this.currentTask.isPresent()) {
				return "timeline current task does not match its status";
			}
			if (this.taskHistory.size() > MAX_TIMELINE_TASKS) {
				return "timeline task history exceeds " + MAX_TIMELINE_TASKS;
			}
			Set<UUID> taskIds = new HashSet<>();
			for (TaskInstance task : this.taskHistory) {
				String nested = task.validationError();
				if (nested != null) {
					return "task history: " + nested;
				}
				if (task.status() != TaskStatus.SETTLED && task.status() != TaskStatus.INTERRUPTED) {
					return "task history contains a non-terminal task";
				}
				if (!taskIds.add(task.taskInstanceId())) {
					return "duplicate task instance in timeline";
				}
			}
			if (this.currentTask.isPresent()) {
				TaskInstance task = this.currentTask.orElseThrow();
				String nested = task.validationError();
				if (nested != null) {
					return "current task: " + nested;
				}
				if (!taskIds.add(task.taskInstanceId())) {
					return "current task is already present in history";
				}
				TaskStatus taskBusinessStatus = task.status() == TaskStatus.BLOCKED
					? task.resumeStatus().orElseThrow()
					: task.status();
				if ((this.status == TimelineStatus.BLOCKED) != (task.status() == TaskStatus.BLOCKED)) {
					return "timeline and current task block overlays do not match";
				}
				TaskStatus expectedTaskStatus = switch (businessStatus) {
					case STARTING -> TaskStatus.STARTING;
					case RUNNING -> TaskStatus.RUNNING;
					case SETTLING -> TaskStatus.SETTLING;
					case INTERMISSION -> TaskStatus.SETTLED;
					case PRE_START, COMPLETED, INTERRUPTED, BLOCKED ->
						throw new IllegalStateException("timeline current-task status");
				};
				if (taskBusinessStatus != expectedTaskStatus) {
					return "timeline and current task business statuses do not match";
				}
				if (
					businessStatus == TimelineStatus.INTERMISSION
						&& (
							taskBusinessStatus != TaskStatus.SETTLED
								|| task.intermission().isEmpty()
						)
				) {
					return "intermission timeline requires a settled task with intermission state";
				}
			}
			return callbackLedgerError(this.callbackLedger);
		}
	}

	public enum TimelineStatus implements StringRepresentable {
		PRE_START("pre_start"),
		STARTING("starting"),
		RUNNING("running"),
		SETTLING("settling"),
		INTERMISSION("intermission"),
		COMPLETED("completed"),
		INTERRUPTED("interrupted"),
		BLOCKED("blocked");

		public static final Codec<TimelineStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		TimelineStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record TaskInstance(
		UUID taskInstanceId,
		Identifier taskId,
		TaskKind kind,
		TaskStatus status,
		Optional<TaskStatus> resumeStatus,
		long elapsedTicks,
		Optional<FrozenResult> result,
		Optional<IntermissionState> intermission,
		List<UUID> participants,
		long startedAtGameTick,
		Optional<Long> settledAtGameTick,
		List<CallbackStep> callbackLedger,
		Optional<LifecycleCallbackTrigger> lifecycleCallbackTrigger,
		Optional<PhaseTransitionTrigger> phaseTransitionTrigger,
		List<TaskEventRecord> events,
		List<TaskStatisticValue> statistics
	) {
		private static final Codec<List<UUID>> PARTICIPANTS_CODEC = UUIDUtil.STRING_CODEC
			.sizeLimitedListOf(MAX_TIMELINE_PARTICIPANTS);
		private static final Codec<List<CallbackStep>> CALLBACK_LEDGER_CODEC = CallbackStep.CODEC
			.sizeLimitedListOf(MAX_CALLBACK_STEPS);
		private static final Codec<List<TaskEventRecord>> EVENTS_CODEC = TaskEventRecord.CODEC
			.sizeLimitedListOf(MAX_TASK_EVENT_RECORDS);
		private static final Codec<List<TaskStatisticValue>> STATISTICS_CODEC = TaskStatisticValue.CODEC
			.sizeLimitedListOf(MAX_TASK_STATISTIC_VALUES);

		public static final Codec<TaskInstance> CODEC = RecordCodecBuilder.<TaskInstance>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("task_instance_id").forGetter(TaskInstance::taskInstanceId),
					Identifier.CODEC.fieldOf("task_id").forGetter(TaskInstance::taskId),
					TaskKind.CODEC.fieldOf("kind").forGetter(TaskInstance::kind),
					TaskStatus.CODEC.fieldOf("status").forGetter(TaskInstance::status),
					TaskStatus.CODEC.optionalFieldOf("resume_status").forGetter(TaskInstance::resumeStatus),
					Codec.LONG.fieldOf("elapsed_ticks").forGetter(TaskInstance::elapsedTicks),
					FrozenResult.CODEC.optionalFieldOf("result").forGetter(TaskInstance::result),
					IntermissionState.CODEC.optionalFieldOf("intermission").forGetter(TaskInstance::intermission),
					PARTICIPANTS_CODEC.fieldOf("participants").forGetter(TaskInstance::participants),
					Codec.LONG.fieldOf("started_at_game_tick").forGetter(TaskInstance::startedAtGameTick),
					Codec.LONG
						.optionalFieldOf("settled_at_game_tick")
						.forGetter(TaskInstance::settledAtGameTick),
					CALLBACK_LEDGER_CODEC.fieldOf("callback_ledger").forGetter(TaskInstance::callbackLedger),
					LifecycleCallbackTrigger.CODEC
						.optionalFieldOf("lifecycle_callback_trigger")
						.forGetter(TaskInstance::lifecycleCallbackTrigger),
					PhaseTransitionTrigger.CODEC
						.optionalFieldOf("phase_transition_trigger")
						.forGetter(TaskInstance::phaseTransitionTrigger),
					EVENTS_CODEC.optionalFieldOf("events", List.of()).forGetter(TaskInstance::events),
					STATISTICS_CODEC
						.optionalFieldOf("statistics", List.of())
						.forGetter(TaskInstance::statistics)
				)
				.apply(instance, TaskInstance::new)
		).validate(TaskInstance::validated);

		public TaskInstance {
			Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			Objects.requireNonNull(taskId, "taskId");
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(status, "status");
			resumeStatus = requiredOptional(resumeStatus, "resumeStatus");
			result = requiredOptional(result, "result");
			intermission = requiredOptional(intermission, "intermission");
			participants = immutableSortedUuidList(participants);
			settledAtGameTick = requiredOptional(settledAtGameTick, "settledAtGameTick");
			callbackLedger = List.copyOf(callbackLedger);
			lifecycleCallbackTrigger = requiredOptional(
				lifecycleCallbackTrigger,
				"lifecycleCallbackTrigger"
			);
			phaseTransitionTrigger = requiredOptional(
				phaseTransitionTrigger,
				"phaseTransitionTrigger"
			);
			events = List.copyOf(events);
			statistics = statistics.stream()
				.sorted(Comparator.comparing(TaskStatisticValue::statisticId))
				.toList();
		}

		/**
		 * Compatibility constructor for callers that only create pause/resume triggers.
		 */
		public TaskInstance(
			final UUID taskInstanceId,
			final Identifier taskId,
			final TaskKind kind,
			final TaskStatus status,
			final Optional<TaskStatus> resumeStatus,
			final long elapsedTicks,
			final Optional<FrozenResult> result,
			final Optional<IntermissionState> intermission,
			final List<UUID> participants,
			final long startedAtGameTick,
			final Optional<Long> settledAtGameTick,
			final List<CallbackStep> callbackLedger,
			final Optional<LifecycleCallbackTrigger> lifecycleCallbackTrigger,
			final List<TaskEventRecord> events,
			final List<TaskStatisticValue> statistics
		) {
			this(
				taskInstanceId,
				taskId,
				kind,
				status,
				resumeStatus,
				elapsedTicks,
				result,
				intermission,
				participants,
				startedAtGameTick,
				settledAtGameTick,
				callbackLedger,
				lifecycleCallbackTrigger,
				Optional.empty(),
				events,
				statistics
			);
		}

		/**
		 * Compatibility constructor for callers that do not create pause/resume triggers.
		 */
		public TaskInstance(
			final UUID taskInstanceId,
			final Identifier taskId,
			final TaskKind kind,
			final TaskStatus status,
			final Optional<TaskStatus> resumeStatus,
			final long elapsedTicks,
			final Optional<FrozenResult> result,
			final Optional<IntermissionState> intermission,
			final List<UUID> participants,
			final long startedAtGameTick,
			final Optional<Long> settledAtGameTick,
			final List<CallbackStep> callbackLedger,
			final List<TaskEventRecord> events,
			final List<TaskStatisticValue> statistics
		) {
			this(
				taskInstanceId,
				taskId,
				kind,
				status,
				resumeStatus,
				elapsedTicks,
				result,
				intermission,
				participants,
				startedAtGameTick,
				settledAtGameTick,
				callbackLedger,
				Optional.empty(),
				Optional.empty(),
				events,
				statistics
			);
		}

		/**
		 * Compatibility constructor for schema-v3 values written before task facts were added.
		 */
		public TaskInstance(
			final UUID taskInstanceId,
			final Identifier taskId,
			final TaskKind kind,
			final TaskStatus status,
			final Optional<TaskStatus> resumeStatus,
			final long elapsedTicks,
			final Optional<FrozenResult> result,
			final Optional<IntermissionState> intermission,
			final List<UUID> participants,
			final long startedAtGameTick,
			final Optional<Long> settledAtGameTick,
			final List<CallbackStep> callbackLedger
		) {
			this(
				taskInstanceId,
				taskId,
				kind,
				status,
				resumeStatus,
				elapsedTicks,
				result,
				intermission,
				participants,
				startedAtGameTick,
				settledAtGameTick,
				callbackLedger,
				Optional.empty(),
				Optional.empty(),
				List.of(),
				List.of()
			);
		}

		private DataResult<TaskInstance> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.elapsedTicks < 0L || this.startedAtGameTick < 0L) {
				return "task ticks cannot be negative";
			}
			if ((this.status == TaskStatus.BLOCKED) != this.resumeStatus.isPresent()) {
				return "only a blocked task must carry a resume status";
			}
			if (this.resumeStatus.filter(TaskStatus.BLOCKED::equals).isPresent()) {
				return "task resume status cannot itself be blocked";
			}
			TaskStatus businessStatus = this.status == TaskStatus.BLOCKED
				? this.resumeStatus.orElseThrow()
				: this.status;
			if (this.status == TaskStatus.BLOCKED && businessStatus == TaskStatus.INTERRUPTED) {
				return "blocked task cannot resume an interrupted status";
			}
			boolean terminal = businessStatus == TaskStatus.SETTLED
				|| businessStatus == TaskStatus.INTERRUPTED;
			if (terminal != this.settledAtGameTick.isPresent()) {
				return "terminal task and settled tick must be present together";
			}
			if (this.settledAtGameTick.filter(value -> value < this.startedAtGameTick).isPresent()) {
				return "task settled before it started";
			}
			if (businessStatus == TaskStatus.SETTLED && this.result.isEmpty()) {
				return "settled task is missing its frozen result";
			}
			if (
				(businessStatus == TaskStatus.STARTING || businessStatus == TaskStatus.RUNNING)
					&& (this.result.isPresent() || this.intermission.isPresent())
			) {
				return "unsettled task cannot carry a result or intermission";
			}
			if (businessStatus == TaskStatus.INTERRUPTED && this.result.isPresent()) {
				return "interrupted task cannot masquerade as a normal result";
			}
			if (this.intermission.isPresent() && this.result.isEmpty()) {
				return "intermission requires a frozen result";
			}
			if (
				this.lifecycleCallbackTrigger.isPresent()
					&& businessStatus != TaskStatus.RUNNING
					&& businessStatus != TaskStatus.SETTLED
			) {
				return "pause/resume callback trigger requires a running task or intermission";
			}
			if (
				this.lifecycleCallbackTrigger.isPresent()
					&& this.phaseTransitionTrigger.isPresent()
			) {
				return "task cannot carry lifecycle and phase-transition triggers together";
			}
			if (this.phaseTransitionTrigger.isPresent()) {
				PhaseTransitionCause cause = this.phaseTransitionTrigger.orElseThrow().cause();
				if (
					(cause == PhaseTransitionCause.APPROVAL && businessStatus != TaskStatus.STARTING)
						|| (
							cause == PhaseTransitionCause.SETTLEMENT
								&& businessStatus != TaskStatus.SETTLING
						)
						|| (
							cause == PhaseTransitionCause.INTERMISSION
								&& (
									businessStatus != TaskStatus.SETTLED
										|| this.intermission.isEmpty()
								)
						)
				) {
					return "phase-transition trigger does not match the task business state";
				}
			}
			if (
				this.lifecycleCallbackTrigger
					.map(LifecycleCallbackTrigger::validated)
					.flatMap(DataResult::error)
					.isPresent()
			) {
				return this.lifecycleCallbackTrigger
					.orElseThrow()
					.validated()
					.error()
					.orElseThrow()
					.message();
			}
			if (this.participants.size() > MAX_TIMELINE_PARTICIPANTS) {
				return "task participant count exceeds " + MAX_TIMELINE_PARTICIPANTS;
			}
			if (new HashSet<>(this.participants).size() != this.participants.size()) {
				return "task participants contain duplicates";
			}
			if (this.events.size() > MAX_TASK_EVENT_RECORDS) {
				return "task event record count exceeds " + MAX_TASK_EVENT_RECORDS;
			}
			for (TaskEventRecord event : this.events) {
				String nested = event.validationError();
				if (nested != null) {
					return "task event: " + nested;
				}
			}
			if (this.statistics.size() > MAX_TASK_STATISTIC_VALUES) {
				return "task statistic value count exceeds " + MAX_TASK_STATISTIC_VALUES;
			}
			Set<String> statisticIds = new HashSet<>();
			for (TaskStatisticValue statistic : this.statistics) {
				String nested = statistic.validationError();
				if (nested != null) {
					return "task statistic: " + nested;
				}
				if (!statisticIds.add(statistic.statisticId())) {
					return "duplicate task statistic " + statistic.statisticId();
				}
			}
			return callbackLedgerError(this.callbackLedger);
		}
	}

	public enum TaskKind implements StringRepresentable {
		WARMUP("warmup"),
		MAIN("main");

		public static final Codec<TaskKind> CODEC = stableEnumCodec(values());
		private final String serializedName;

		TaskKind(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum TaskStatus implements StringRepresentable {
		STARTING("starting"),
		RUNNING("running"),
		SETTLING("settling"),
		SETTLED("settled"),
		INTERRUPTED("interrupted"),
		BLOCKED("blocked");

		public static final Codec<TaskStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		TaskStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record LifecycleCallbackTrigger(
		LifecycleCallbackKind kind,
		long triggerRevision
	) {
		public static final Codec<LifecycleCallbackTrigger> CODEC =
			RecordCodecBuilder.<LifecycleCallbackTrigger>create(
				instance -> instance.group(
						LifecycleCallbackKind.CODEC
							.fieldOf("kind")
							.forGetter(LifecycleCallbackTrigger::kind),
						Codec.LONG
							.fieldOf("trigger_revision")
							.forGetter(LifecycleCallbackTrigger::triggerRevision)
					)
					.apply(instance, LifecycleCallbackTrigger::new)
			).validate(LifecycleCallbackTrigger::validated);

		public LifecycleCallbackTrigger {
			Objects.requireNonNull(kind, "kind");
		}

		private DataResult<LifecycleCallbackTrigger> validated() {
			return this.triggerRevision < 0L
				? DataResult.error(() -> "lifecycle callback trigger revision cannot be negative")
				: DataResult.success(this);
		}
	}

	public enum LifecycleCallbackKind implements StringRepresentable {
		TASK_PAUSE("task_pause"),
		TASK_RESUME("task_resume"),
		INTERMISSION_PAUSE("intermission_pause"),
		INTERMISSION_RESUME("intermission_resume");

		public static final Codec<LifecycleCallbackKind> CODEC = stableEnumCodec(values());
		private final String serializedName;

		LifecycleCallbackKind(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record PhaseTransitionTrigger(
		UUID transitionId,
		Identifier fromPhaseId,
		Identifier toPhaseId,
		PhaseTransitionCause cause,
		long triggerRevision
	) {
		public static final Codec<PhaseTransitionTrigger> CODEC =
			RecordCodecBuilder.<PhaseTransitionTrigger>create(
				instance -> instance.group(
						UUIDUtil.STRING_CODEC
							.fieldOf("transition_id")
							.forGetter(PhaseTransitionTrigger::transitionId),
						Identifier.CODEC
							.fieldOf("from_phase_id")
							.forGetter(PhaseTransitionTrigger::fromPhaseId),
						Identifier.CODEC
							.fieldOf("to_phase_id")
							.forGetter(PhaseTransitionTrigger::toPhaseId),
						PhaseTransitionCause.CODEC
							.fieldOf("cause")
							.forGetter(PhaseTransitionTrigger::cause),
						Codec.LONG
							.fieldOf("trigger_revision")
							.forGetter(PhaseTransitionTrigger::triggerRevision)
					)
					.apply(instance, PhaseTransitionTrigger::new)
			).validate(PhaseTransitionTrigger::validated);

		public PhaseTransitionTrigger {
			Objects.requireNonNull(transitionId, "transitionId");
			Objects.requireNonNull(fromPhaseId, "fromPhaseId");
			Objects.requireNonNull(toPhaseId, "toPhaseId");
			Objects.requireNonNull(cause, "cause");
		}

		private DataResult<PhaseTransitionTrigger> validated() {
			return this.fromPhaseId.equals(this.toPhaseId) || this.triggerRevision < 0L
				? DataResult.error(
					() -> "phase transition requires distinct phases and a non-negative revision"
				)
				: DataResult.success(this);
		}
	}

	public enum PhaseTransitionCause implements StringRepresentable {
		APPROVAL("approval"),
		SETTLEMENT("settlement"),
		INTERMISSION("intermission");

		public static final Codec<PhaseTransitionCause> CODEC = stableEnumCodec(values());
		private final String serializedName;

		PhaseTransitionCause(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	/**
	 * One immutable event fact attached to the task that actually produced it.
	 */
	public record TaskEventRecord(
		String eventId,
		long gameElapsedTicks,
		long taskElapsedTicks,
		TaskEventRecordState state,
		Optional<FrozenPlayerValue> player
	) {
		public static final Codec<TaskEventRecord> CODEC = RecordCodecBuilder.<TaskEventRecord>create(
			instance -> instance.group(
					Codec.sizeLimitedString(128).fieldOf("event_id").forGetter(TaskEventRecord::eventId),
					Codec.LONG.fieldOf("game_elapsed_ticks").forGetter(TaskEventRecord::gameElapsedTicks),
					Codec.LONG.fieldOf("task_elapsed_ticks").forGetter(TaskEventRecord::taskElapsedTicks),
					TaskEventRecordState.CODEC.fieldOf("state").forGetter(TaskEventRecord::state),
					FrozenPlayerValue.CODEC.optionalFieldOf("player").forGetter(TaskEventRecord::player)
				)
				.apply(instance, TaskEventRecord::new)
		).validate(TaskEventRecord::validated);

		public TaskEventRecord {
			Objects.requireNonNull(eventId, "eventId");
			Objects.requireNonNull(state, "state");
			player = requiredOptional(player, "player");
		}

		private DataResult<TaskEventRecord> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				!STABLE_VALUE.matcher(this.eventId).matches()
					|| this.gameElapsedTicks < 0L
					|| this.taskElapsedTicks < 0L
			) {
				return "invalid event ID or tick";
			}
			return this.player
				.map(FrozenPlayerValue::validationError)
				.filter(Objects::nonNull)
				.orElse(null);
		}
	}

	public enum TaskEventRecordState implements StringRepresentable {
		RUNNING("running"),
		SETTLING("settling"),
		INTERMISSION("intermission");

		public static final Codec<TaskEventRecordState> CODEC = stableEnumCodec(values());
		private final String serializedName;

		TaskEventRecordState(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	/**
	 * A typed recap statistic. Exactly one payload field is legal for its declared type.
	 */
	public record TaskStatisticValue(
		String statisticId,
		TaskStatisticValueType type,
		Optional<Boolean> booleanValue,
		Optional<Long> longValue,
		Optional<String> stringValue,
		Optional<Identifier> identifierValue,
		Optional<FrozenPlayerValue> playerValue
	) {
		public static final Codec<TaskStatisticValue> CODEC = RecordCodecBuilder.<TaskStatisticValue>create(
			instance -> instance.group(
					Codec.sizeLimitedString(128)
						.fieldOf("statistic_id")
						.forGetter(TaskStatisticValue::statisticId),
					TaskStatisticValueType.CODEC.fieldOf("type").forGetter(TaskStatisticValue::type),
					Codec.BOOL.optionalFieldOf("boolean_value").forGetter(TaskStatisticValue::booleanValue),
					Codec.LONG.optionalFieldOf("long_value").forGetter(TaskStatisticValue::longValue),
					Codec.sizeLimitedString(MAX_TASK_STATISTIC_STRING_LENGTH)
						.optionalFieldOf("string_value")
						.forGetter(TaskStatisticValue::stringValue),
					Identifier.CODEC
						.optionalFieldOf("identifier_value")
						.forGetter(TaskStatisticValue::identifierValue),
					FrozenPlayerValue.CODEC
						.optionalFieldOf("player_value")
						.forGetter(TaskStatisticValue::playerValue)
				)
				.apply(instance, TaskStatisticValue::new)
		).validate(TaskStatisticValue::validated);

		public TaskStatisticValue {
			Objects.requireNonNull(statisticId, "statisticId");
			Objects.requireNonNull(type, "type");
			booleanValue = requiredOptional(booleanValue, "booleanValue");
			longValue = requiredOptional(longValue, "longValue");
			stringValue = requiredOptional(stringValue, "stringValue");
			identifierValue = requiredOptional(identifierValue, "identifierValue");
			playerValue = requiredOptional(playerValue, "playerValue");
		}

		public static TaskStatisticValue booleanValue(final String id, final boolean value) {
			return new TaskStatisticValue(
				id,
				TaskStatisticValueType.BOOLEAN,
				Optional.of(value),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}

		public static TaskStatisticValue integer(final String id, final long value) {
			return numeric(id, TaskStatisticValueType.INTEGER, value);
		}

		public static TaskStatisticValue durationTicks(final String id, final long value) {
			return numeric(id, TaskStatisticValueType.DURATION_TICKS, value);
		}

		public static TaskStatisticValue stringValue(final String id, final String value) {
			return new TaskStatisticValue(
				id,
				TaskStatisticValueType.STRING,
				Optional.empty(),
				Optional.empty(),
				Optional.of(value),
				Optional.empty(),
				Optional.empty()
			);
		}

		public static TaskStatisticValue identifier(final String id, final Identifier value) {
			return new TaskStatisticValue(
				id,
				TaskStatisticValueType.IDENTIFIER,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.of(value),
				Optional.empty()
			);
		}

		public static TaskStatisticValue player(
			final String id,
			final UUID playerId,
			final String playerName
		) {
			return new TaskStatisticValue(
				id,
				TaskStatisticValueType.PLAYER,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.of(new FrozenPlayerValue(playerId, playerName))
			);
		}

		private static TaskStatisticValue numeric(
			final String id,
			final TaskStatisticValueType type,
			final long value
		) {
			return new TaskStatisticValue(
				id,
				type,
				Optional.empty(),
				Optional.of(value),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}

		private DataResult<TaskStatisticValue> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (!STABLE_VALUE.matcher(this.statisticId).matches()) {
				return "invalid statistic ID";
			}
			int presentValues = (this.booleanValue.isPresent() ? 1 : 0)
				+ (this.longValue.isPresent() ? 1 : 0)
				+ (this.stringValue.isPresent() ? 1 : 0)
				+ (this.identifierValue.isPresent() ? 1 : 0)
				+ (this.playerValue.isPresent() ? 1 : 0);
			if (presentValues != 1) {
				return "statistic must carry exactly one typed value";
			}
			boolean matchingValue = switch (this.type) {
				case BOOLEAN -> this.booleanValue.isPresent();
				case INTEGER -> this.longValue.isPresent();
				case DURATION_TICKS -> this.longValue.filter(value -> value >= 0L).isPresent();
				case STRING -> this.stringValue
					.filter(value -> !value.isBlank() && value.length() <= MAX_TASK_STATISTIC_STRING_LENGTH)
					.isPresent();
				case IDENTIFIER -> this.identifierValue.isPresent();
				case PLAYER -> this.playerValue
					.filter(value -> value.validationError() == null)
					.isPresent();
			};
			return matchingValue ? null : "statistic payload does not match type " + this.type.getSerializedName();
		}
	}

	public enum TaskStatisticValueType implements StringRepresentable {
		BOOLEAN("boolean"),
		INTEGER("integer"),
		DURATION_TICKS("duration_ticks"),
		STRING("string"),
		IDENTIFIER("identifier"),
		PLAYER("player");

		public static final Codec<TaskStatisticValueType> CODEC = stableEnumCodec(values());
		private final String serializedName;

		TaskStatisticValueType(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record FrozenPlayerValue(UUID playerId, String playerName) {
		public static final Codec<FrozenPlayerValue> CODEC = RecordCodecBuilder.<FrozenPlayerValue>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(FrozenPlayerValue::playerId),
					Codec.sizeLimitedString(MAX_TASK_FACT_PLAYER_NAME_LENGTH)
						.fieldOf("name")
						.forGetter(FrozenPlayerValue::playerName)
				)
				.apply(instance, FrozenPlayerValue::new)
		).validate(FrozenPlayerValue::validated);

		public FrozenPlayerValue {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(playerName, "playerName");
		}

		private DataResult<FrozenPlayerValue> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.playerName.isBlank()
				|| this.playerName.length() > MAX_TASK_FACT_PLAYER_NAME_LENGTH
				? "invalid frozen player name"
				: null;
		}
	}

	public record FrozenResult(
		String resultId,
		Optional<Identifier> nextTaskId,
		Optional<Identifier> endPhaseId,
		Optional<Identifier> phaseId,
		Optional<PhaseTransitionTiming> phaseTransitionTiming,
		long frozenAtGameTick
	) {
		public static final Codec<FrozenResult> CODEC = RecordCodecBuilder.<FrozenResult>create(
			instance -> instance.group(
					Codec.sizeLimitedString(128).fieldOf("result_id").forGetter(FrozenResult::resultId),
					Identifier.CODEC.optionalFieldOf("next_task_id").forGetter(FrozenResult::nextTaskId),
					Identifier.CODEC.optionalFieldOf("end_phase_id").forGetter(FrozenResult::endPhaseId),
					Identifier.CODEC.optionalFieldOf("phase_id").forGetter(FrozenResult::phaseId),
					PhaseTransitionTiming.CODEC
						.optionalFieldOf("phase_transition_timing")
						.forGetter(FrozenResult::phaseTransitionTiming),
					Codec.LONG.fieldOf("frozen_at_game_tick").forGetter(FrozenResult::frozenAtGameTick)
				)
				.apply(instance, FrozenResult::new)
		).validate(FrozenResult::validated);

		public FrozenResult {
			Objects.requireNonNull(resultId, "resultId");
			nextTaskId = requiredOptional(nextTaskId, "nextTaskId");
			endPhaseId = requiredOptional(endPhaseId, "endPhaseId");
			phaseId = requiredOptional(phaseId, "phaseId");
			phaseTransitionTiming = requiredOptional(
				phaseTransitionTiming,
				"phaseTransitionTiming"
			);
		}

		public FrozenResult(
			final String resultId,
			final Optional<Identifier> nextTaskId,
			final Optional<Identifier> endPhaseId,
			final long frozenAtGameTick
		) {
			this(
				resultId,
				nextTaskId,
				endPhaseId,
				Optional.empty(),
				Optional.empty(),
				frozenAtGameTick
			);
		}

		private DataResult<FrozenResult> validated() {
			if (!STABLE_VALUE.matcher(this.resultId).matches() || this.frozenAtGameTick < 0L) {
				return DataResult.error(() -> "invalid frozen result ID or tick");
			}
			if (this.nextTaskId.isPresent() == this.endPhaseId.isPresent()) {
				return DataResult.error(() -> "frozen result must route to exactly one next task or end phase");
			}
			if (this.endPhaseId.isPresent() && this.phaseId.isPresent()) {
				return DataResult.error(() -> "final frozen result cannot also carry an intermediate phase");
			}
			return DataResult.success(this);
		}
	}

	public enum PhaseTransitionTiming implements StringRepresentable {
		BEFORE_INTERMISSION("before_intermission"),
		AFTER_INTERMISSION("after_intermission");

		public static final Codec<PhaseTransitionTiming> CODEC = stableEnumCodec(values());
		private final String serializedName;

		PhaseTransitionTiming(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record IntermissionState(
		long durationTicks,
		long elapsedTicks,
		boolean countsTowardGameTime,
		boolean paused
	) {
		public static final Codec<IntermissionState> CODEC = RecordCodecBuilder.<IntermissionState>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("duration_ticks").forGetter(IntermissionState::durationTicks),
					Codec.LONG.fieldOf("elapsed_ticks").forGetter(IntermissionState::elapsedTicks),
					Codec.BOOL
						.fieldOf("counts_toward_game_time")
						.forGetter(IntermissionState::countsTowardGameTime),
					Codec.BOOL.fieldOf("paused").forGetter(IntermissionState::paused)
				)
				.apply(instance, IntermissionState::new)
		).validate(IntermissionState::validated);

		private DataResult<IntermissionState> validated() {
			return this.durationTicks <= 0L
				|| this.elapsedTicks < 0L
				|| this.elapsedTicks > this.durationTicks
				? DataResult.error(() -> "invalid intermission duration or elapsed ticks")
				: DataResult.success(this);
		}
	}

	public record CallbackStep(
		String stepKey,
		Identifier functionId,
		Optional<UUID> playerId,
		CallbackStepStatus status,
		int attemptCount,
		Optional<String> lastError,
		long updatedAtGameTick
	) {
		public static final Codec<CallbackStep> CODEC = RecordCodecBuilder.<CallbackStep>create(
			instance -> instance.group(
					Codec.sizeLimitedString(192).fieldOf("step_key").forGetter(CallbackStep::stepKey),
					Identifier.CODEC.fieldOf("function_id").forGetter(CallbackStep::functionId),
					UUIDUtil.STRING_CODEC.optionalFieldOf("player_id").forGetter(CallbackStep::playerId),
					CallbackStepStatus.CODEC.fieldOf("status").forGetter(CallbackStep::status),
					Codec.INT.fieldOf("attempt_count").forGetter(CallbackStep::attemptCount),
					Codec.sizeLimitedString(4_096)
						.optionalFieldOf("last_error")
						.forGetter(CallbackStep::lastError),
					Codec.LONG.fieldOf("updated_at_game_tick").forGetter(CallbackStep::updatedAtGameTick)
				)
				.apply(instance, CallbackStep::new)
		).validate(CallbackStep::validated);

		public CallbackStep {
			Objects.requireNonNull(stepKey, "stepKey");
			Objects.requireNonNull(functionId, "functionId");
			playerId = requiredOptional(playerId, "playerId");
			Objects.requireNonNull(status, "status");
			lastError = requiredOptional(lastError, "lastError");
		}

		private DataResult<CallbackStep> validated() {
			if (
				this.stepKey.isBlank()
					|| this.attemptCount <= 0
					|| this.updatedAtGameTick < 0L
					|| this.lastError.filter(String::isBlank).isPresent()
			) {
				return DataResult.error(() -> "invalid callback step");
			}
			boolean requiresError = this.status == CallbackStepStatus.FAILED
				|| this.status == CallbackStepStatus.OUTCOME_UNKNOWN;
			return requiresError != this.lastError.isPresent()
				? DataResult.error(() -> "callback error does not match its status")
				: DataResult.success(this);
		}
	}

	public enum CallbackStepStatus implements StringRepresentable {
		PREPARED("prepared"),
		SUCCEEDED("succeeded"),
		FAILED("failed"),
		OUTCOME_UNKNOWN("outcome_unknown");

		public static final Codec<CallbackStepStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		CallbackStepStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record ExclusiveReservation(
		UUID reservationId,
		Identifier gameId,
		Identifier fieldId,
		UUID scopeId,
		String value,
		UUID ownerId,
		ReservationStatus status,
		Optional<UUID> flowInstanceId,
		long createdAtServerTick,
		long updatedAtServerTick,
		long reservationRevision
	) {
		private static final Comparator<ExclusiveReservation> ORDER = Comparator
			.comparing(ExclusiveReservation::gameId)
			.thenComparing(ExclusiveReservation::fieldId)
			.thenComparing(ExclusiveReservation::scopeId)
			.thenComparing(ExclusiveReservation::value)
			.thenComparing(value -> value.status().getSerializedName())
			.thenComparing(ExclusiveReservation::ownerId);

		public static final Codec<ExclusiveReservation> CODEC = RecordCodecBuilder.<ExclusiveReservation>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("reservation_id").forGetter(ExclusiveReservation::reservationId),
					Identifier.CODEC.fieldOf("game_id").forGetter(ExclusiveReservation::gameId),
					Identifier.CODEC.fieldOf("field_id").forGetter(ExclusiveReservation::fieldId),
					UUIDUtil.STRING_CODEC.fieldOf("scope_id").forGetter(ExclusiveReservation::scopeId),
					Codec.sizeLimitedString(128).fieldOf("value").forGetter(ExclusiveReservation::value),
					UUIDUtil.STRING_CODEC.fieldOf("owner_id").forGetter(ExclusiveReservation::ownerId),
					ReservationStatus.CODEC.fieldOf("status").forGetter(ExclusiveReservation::status),
					UUIDUtil.STRING_CODEC
						.optionalFieldOf("flow_instance_id")
						.forGetter(ExclusiveReservation::flowInstanceId),
					Codec.LONG
						.fieldOf("created_at_server_tick")
						.forGetter(ExclusiveReservation::createdAtServerTick),
					Codec.LONG
						.fieldOf("updated_at_server_tick")
						.forGetter(ExclusiveReservation::updatedAtServerTick),
					Codec.LONG
						.fieldOf("reservation_revision")
						.forGetter(ExclusiveReservation::reservationRevision)
				)
				.apply(instance, ExclusiveReservation::new)
		).validate(ExclusiveReservation::validated);

		public ExclusiveReservation {
			Objects.requireNonNull(reservationId, "reservationId");
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(fieldId, "fieldId");
			Objects.requireNonNull(scopeId, "scopeId");
			Objects.requireNonNull(value, "value");
			Objects.requireNonNull(ownerId, "ownerId");
			Objects.requireNonNull(status, "status");
			flowInstanceId = requiredOptional(flowInstanceId, "flowInstanceId");
		}

		private DataResult<ExclusiveReservation> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				!STABLE_VALUE.matcher(this.value).matches()
					|| this.createdAtServerTick < 0L
					|| this.updatedAtServerTick < this.createdAtServerTick
					|| this.reservationRevision < 0L
			) {
				return "invalid reservation value, ticks, or revision";
			}
			return this.status == ReservationStatus.HELD && this.flowInstanceId.isEmpty()
				? "held reservation requires a flow instance"
				: null;
		}

		private ReservationOptionKey optionKey() {
			return new ReservationOptionKey(
				this.gameId,
				this.fieldId,
				this.scopeId,
				this.value
			);
		}

		private ReservationHolderKey holderKey() {
			return new ReservationHolderKey(
				this.gameId,
				this.fieldId,
				this.scopeId,
				this.ownerId,
				this.status
			);
		}
	}

	public enum ReservationStatus implements StringRepresentable {
		HELD("held"),
		LOCKED("locked");

		public static final Codec<ReservationStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		ReservationStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	private static String callbackLedgerError(final List<CallbackStep> ledger) {
		if (ledger.size() > MAX_CALLBACK_STEPS) {
			return "callback ledger exceeds " + MAX_CALLBACK_STEPS;
		}
		Set<String> keys = new HashSet<>();
		for (CallbackStep step : ledger) {
			if (step.validated().error().isPresent()) {
				return step.validated().error().orElseThrow().message();
			}
			String key = step.stepKey() + "\0" + step.playerId().map(UUID::toString).orElse("");
			if (!keys.add(key)) {
				return "duplicate callback step " + step.stepKey();
			}
		}
		return null;
	}

	private static List<UUID> immutableSortedUuidList(final List<UUID> values) {
		Objects.requireNonNull(values, "values");
		TreeSet<UUID> sorted = new TreeSet<>();
		for (UUID value : values) {
			if (!sorted.add(Objects.requireNonNull(value, "participant"))) {
				throw new IllegalArgumentException("task participants contain duplicates");
			}
		}
		return List.copyOf(sorted);
	}

	private static <E extends Enum<E> & StringRepresentable> Codec<E> stableEnumCodec(final E[] values) {
		return Codec.STRING.comapFlatMap(
			name -> Arrays.stream(values)
				.filter(value -> value.getSerializedName().equals(name))
				.findFirst()
				.map(DataResult::success)
				.orElseGet(() -> DataResult.error(() -> "unknown value '" + name + "'")),
			StringRepresentable::getSerializedName
		);
	}

	private static <T> Optional<T> requiredOptional(final Optional<T> value, final String name) {
		return Objects.requireNonNull(value, name);
	}

	private record ReservationOptionKey(
		Identifier gameId,
		Identifier fieldId,
		UUID scopeId,
		String value
	) {
	}

	private record ReservationHolderKey(
		Identifier gameId,
		Identifier fieldId,
		UUID scopeId,
		UUID holderId,
		ReservationStatus status
	) {
	}
}
