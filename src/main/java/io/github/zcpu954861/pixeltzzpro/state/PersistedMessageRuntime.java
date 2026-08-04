package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Stable schema-v4 DTO for V3B restart recovery.
 *
 * <p>These records contain only primitive values, identifiers, UUIDs and canonical JSON/text.
 * They intentionally do not serialize compiled cue nodes, Minecraft objects, network payloads or
 * private runtime caches. The runtime integration must rebuild those objects from the frozen cue
 * document after {@link io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority}
 * has produced a recovery plan.
 */
public final class PersistedMessageRuntime {
	public static final int HARD_MAX_INSTANCES = 1_024;
	public static final int HARD_MAX_RECIPIENTS_PER_INSTANCE = 1_024;
	public static final int HARD_MAX_NODE_AUDIENCES = 4_096;
	public static final int HARD_MAX_OCCURRENCES = 32_768;
	public static final int HARD_MAX_EVENT_OFFSETS = 4_096;
	public static final int HARD_MAX_LOCKED_FIELDS_PER_RECIPIENT = 4_096;
	public static final int HARD_MAX_CONDITION_DECISIONS_PER_RECIPIENT = 16_384;
	public static final int HARD_MAX_PENDING_OFFLINE = 16_384;
	public static final int HARD_MAX_TOMBSTONES = 4_096;
	public static final int HARD_MAX_CALLBACKS = 4_096;
	public static final int MAX_LOCAL_ID_LENGTH = 128;
	public static final int MAX_CANONICAL_VALUE_LENGTH = 65_536;
	public static final int MAX_REASON_LENGTH = 1_024;
	public static final int MAX_DIAGNOSTIC_LENGTH = 4_096;
	public static final int MAX_MAP_ENTRIES = 256;
	public static final int MAX_MAP_CHARACTERS = 131_072;

	private static final Codec<String> LOCAL_ID_CODEC =
		Codec.sizeLimitedString(MAX_LOCAL_ID_LENGTH);
	private static final Codec<String> CANONICAL_VALUE_CODEC =
		Codec.sizeLimitedString(MAX_CANONICAL_VALUE_LENGTH);
	private static final Codec<Map<String, String>> STRING_MAP_CODEC = Codec.unboundedMap(
		LOCAL_ID_CODEC,
		CANONICAL_VALUE_CODEC
	);
	private static final Codec<Map<String, Long>> DURATION_MAP_CODEC = Codec.unboundedMap(
		LOCAL_ID_CODEC,
		Codec.LONG
	);

	private PersistedMessageRuntime() {
	}

	public record Snapshot(
		UUID gameInstanceId,
		long snapshotGameTick,
		long runtimeRevision,
		List<Instance> instances,
		List<PendingOfflineDelivery> pendingOffline,
		List<Tombstone> tombstones,
		CallbackLedger callbackLedger
	) {
		private static final Codec<List<Instance>> INSTANCES_CODEC =
			Instance.CODEC.sizeLimitedListOf(HARD_MAX_INSTANCES);
		private static final Codec<List<PendingOfflineDelivery>> PENDING_CODEC =
			PendingOfflineDelivery.CODEC.sizeLimitedListOf(HARD_MAX_PENDING_OFFLINE);
		private static final Codec<List<Tombstone>> TOMBSTONES_CODEC =
			Tombstone.CODEC.sizeLimitedListOf(HARD_MAX_TOMBSTONES);

		public static final Codec<Snapshot> CODEC = RecordCodecBuilder.<Snapshot>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("game_instance_id").forGetter(Snapshot::gameInstanceId),
					Codec.LONG.fieldOf("snapshot_game_tick").forGetter(Snapshot::snapshotGameTick),
					Codec.LONG.fieldOf("runtime_revision").forGetter(Snapshot::runtimeRevision),
					INSTANCES_CODEC.optionalFieldOf("instances", List.of()).forGetter(Snapshot::instances),
					PENDING_CODEC.optionalFieldOf("pending_offline", List.of()).forGetter(Snapshot::pendingOffline),
					TOMBSTONES_CODEC.optionalFieldOf("tombstones", List.of()).forGetter(Snapshot::tombstones),
					CallbackLedger.CODEC.fieldOf("callback_ledger").forGetter(Snapshot::callbackLedger)
				)
				.apply(instance, Snapshot::new)
		).validate(Snapshot::validated);

		public Snapshot {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			instances = Objects.requireNonNull(instances, "instances").stream()
				.sorted(Instance.ORDER)
				.toList();
			pendingOffline = Objects.requireNonNull(pendingOffline, "pendingOffline").stream()
				.sorted(PendingOfflineDelivery.ORDER)
				.toList();
			tombstones = Objects.requireNonNull(tombstones, "tombstones").stream()
				.sorted(Tombstone.ORDER)
				.toList();
			Objects.requireNonNull(callbackLedger, "callbackLedger");
		}

		public static Snapshot empty(final UUID gameInstanceId, final long gameTick) {
			return new Snapshot(
				gameInstanceId,
				gameTick,
				0L,
				List.of(),
				List.of(),
				List.of(),
				CallbackLedger.empty(HARD_MAX_CALLBACKS)
			);
		}

		public Snapshot withContent(
			final List<Instance> nextInstances,
			final List<PendingOfflineDelivery> nextPending,
			final List<Tombstone> nextTombstones,
			final CallbackLedger nextCallbacks,
			final long gameTick,
			final long revision
		) {
			return new Snapshot(
				this.gameInstanceId,
				gameTick,
				revision,
				nextInstances,
				nextPending,
				nextTombstones,
				nextCallbacks
			);
		}

		public DataResult<Snapshot> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.snapshotGameTick < 0L || this.runtimeRevision < 0L) {
				return "message runtime snapshot clocks cannot be negative";
			}
			if (this.instances.size() > HARD_MAX_INSTANCES) {
				return "persisted message instance count exceeds " + HARD_MAX_INSTANCES;
			}
			if (this.pendingOffline.size() > HARD_MAX_PENDING_OFFLINE) {
				return "pending offline count exceeds " + HARD_MAX_PENDING_OFFLINE;
			}
			if (this.tombstones.size() > HARD_MAX_TOMBSTONES) {
				return "message tombstone count exceeds " + HARD_MAX_TOMBSTONES;
			}
			Set<UUID> instanceIds = new HashSet<>();
			for (Instance value : this.instances) {
				String nested = value.validationError();
				if (nested != null) {
					return "persisted message instance: " + nested;
				}
				if (!instanceIds.add(value.instanceId())) {
					return "duplicate persisted message instance id";
				}
			}
			Set<PendingOfflineKey> pendingKeys = new HashSet<>();
			for (PendingOfflineDelivery value : this.pendingOffline) {
				String nested = value.validationError();
				if (nested != null) {
					return "pending offline delivery: " + nested;
				}
				if (!instanceIds.contains(value.instanceId())) {
					return "pending offline delivery references an unpersisted instance";
				}
				if (!pendingKeys.add(value.key())) {
					return "duplicate pending offline delivery";
				}
			}
			Set<UUID> tombstoneIds = new HashSet<>();
			for (Tombstone value : this.tombstones) {
				String nested = value.validationError();
				if (nested != null) {
					return "message tombstone: " + nested;
				}
				if (instanceIds.contains(value.instanceId()) || !tombstoneIds.add(value.instanceId())) {
					return "message instance cannot be both active and terminal or duplicated";
				}
			}
			return this.callbackLedger.validationError();
		}
	}

	public record Instance(
		UUID instanceId,
		Identifier cueId,
		RestartMode restart,
		FrozenCue frozenCue,
		InvocationContext context,
		Map<String, String> canonicalParameters,
		AudienceState audience,
		TimingState timing,
		ProgressState progress,
		List<RecipientState> recipients,
		Optional<String> dedupeKey,
		Optional<String> refreshKey,
		long updatedAtGameTick
	) {
		private static final Comparator<Instance> ORDER = Comparator
			.comparingLong(Instance::updatedAtGameTick)
			.thenComparing(Instance::instanceId);
		private static final Codec<List<RecipientState>> RECIPIENTS_CODEC =
			RecipientState.CODEC.sizeLimitedListOf(HARD_MAX_RECIPIENTS_PER_INSTANCE);

		public static final Codec<Instance> CODEC = RecordCodecBuilder.<Instance>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(Instance::instanceId),
					Identifier.CODEC.fieldOf("cue_id").forGetter(Instance::cueId),
					RestartMode.CODEC.fieldOf("restart").forGetter(Instance::restart),
					FrozenCue.CODEC.fieldOf("frozen_cue").forGetter(Instance::frozenCue),
					InvocationContext.CODEC.fieldOf("context").forGetter(Instance::context),
					STRING_MAP_CODEC.optionalFieldOf("canonical_parameters", Map.of()).forGetter(Instance::canonicalParameters),
					AudienceState.CODEC.fieldOf("audience").forGetter(Instance::audience),
					TimingState.CODEC.fieldOf("timing").forGetter(Instance::timing),
					ProgressState.CODEC.fieldOf("progress").forGetter(Instance::progress),
					RECIPIENTS_CODEC.optionalFieldOf("recipients", List.of()).forGetter(Instance::recipients),
					CANONICAL_VALUE_CODEC.optionalFieldOf("dedupe_key").forGetter(Instance::dedupeKey),
					CANONICAL_VALUE_CODEC.optionalFieldOf("refresh_key").forGetter(Instance::refreshKey),
					Codec.LONG.fieldOf("updated_at_game_tick").forGetter(Instance::updatedAtGameTick)
				)
				.apply(instance, Instance::new)
		).validate(Instance::validated);

		public Instance {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(cueId, "cueId");
			Objects.requireNonNull(restart, "restart");
			Objects.requireNonNull(frozenCue, "frozenCue");
			Objects.requireNonNull(context, "context");
			canonicalParameters = immutableStringMap(canonicalParameters);
			Objects.requireNonNull(audience, "audience");
			Objects.requireNonNull(timing, "timing");
			Objects.requireNonNull(progress, "progress");
			recipients = Objects.requireNonNull(recipients, "recipients").stream()
				.sorted(Comparator.comparing(RecipientState::recipientId))
				.toList();
			dedupeKey = immutableOptional(dedupeKey);
			refreshKey = immutableOptional(refreshKey);
		}

		private DataResult<Instance> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.restart == RestartMode.TRANSIENT) {
				return "transient message instance must not be persisted";
			}
			if (this.updatedAtGameTick < 0L) {
				return "message instance update tick cannot be negative";
			}
			String mapError = stringMapError(this.canonicalParameters, "canonical parameters");
			if (mapError != null) {
				return mapError;
			}
			if (this.recipients.size() > HARD_MAX_RECIPIENTS_PER_INSTANCE) {
				return "recipient count exceeds " + HARD_MAX_RECIPIENTS_PER_INSTANCE;
			}
			Set<UUID> recipientIds = new HashSet<>();
			for (RecipientState recipient : this.recipients) {
				String nested = recipient.validationError();
				if (nested != null) {
					return "recipient state: " + nested;
				}
				if (!this.audience.cueTargets().contains(recipient.recipientId())) {
					return "recipient is not present in the frozen cue audience";
				}
				if (!recipientIds.add(recipient.recipientId())) {
					return "duplicate recipient state";
				}
			}
			String nested = this.frozenCue.validationError();
			if (nested != null) {
				return nested;
			}
			nested = this.context.validationError();
			if (nested != null) {
				return nested;
			}
			nested = this.audience.validationError();
			if (nested != null) {
				return nested;
			}
			nested = this.timing.validationError();
			if (nested != null) {
				return nested;
			}
			return this.progress.validationError();
		}
	}

	public record FrozenCue(
		long definitionGeneration,
		FrozenDocument canonicalDocument,
		Optional<String> definitionFingerprint,
		Optional<FrozenDocument> dependencySnapshot
	) {
		public static final Codec<FrozenCue> CODEC = RecordCodecBuilder.<FrozenCue>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("definition_generation").forGetter(FrozenCue::definitionGeneration),
					FrozenDocument.CODEC.fieldOf("canonical_document").forGetter(FrozenCue::canonicalDocument),
					Codec.STRING.optionalFieldOf("definition_fingerprint").forGetter(FrozenCue::definitionFingerprint),
					FrozenDocument.CODEC.optionalFieldOf("dependency_snapshot").forGetter(FrozenCue::dependencySnapshot)
				)
				.apply(instance, FrozenCue::new)
		).validate(FrozenCue::validated);

		public FrozenCue {
			Objects.requireNonNull(canonicalDocument, "canonicalDocument");
			definitionFingerprint = immutableOptional(definitionFingerprint);
			dependencySnapshot = immutableOptional(dependencySnapshot);
		}

		/** Compatibility constructor for fingerprint-only V3B checkpoints. */
		public FrozenCue(
			final long definitionGeneration,
			final FrozenDocument canonicalDocument,
			final Optional<String> definitionFingerprint
		) {
			this(
				definitionGeneration,
				canonicalDocument,
				definitionFingerprint,
				Optional.empty()
			);
		}

		/** Compatibility constructor for snapshots created before dependency fingerprints existed. */
		public FrozenCue(
			final long definitionGeneration,
			final FrozenDocument canonicalDocument
		) {
			this(
				definitionGeneration,
				canonicalDocument,
				Optional.empty(),
				Optional.empty()
			);
		}

		private DataResult<FrozenCue> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.definitionGeneration < 0L) {
				return "frozen cue generation cannot be negative";
			}
			if (
				this.definitionFingerprint.filter(value -> !value.matches("[0-9a-f]{64}"))
					.isPresent()
			) {
				return "frozen definition fingerprint must be a lowercase SHA-256 digest";
			}
			if (this.dependencySnapshot.isPresent()) {
				FrozenDocument dependency = this.dependencySnapshot.orElseThrow();
				if (
					dependency.normalizedJson().isBlank()
						|| !FrozenDocument.of(dependency.normalizedJson()).sha256()
							.equals(dependency.sha256())
				) {
					return "frozen dependency snapshot is blank or its digest mismatches";
				}
				if (
					this.definitionFingerprint.isEmpty()
						|| !this.definitionFingerprint.orElseThrow().equals(dependency.sha256())
				) {
					return "frozen dependency fingerprint does not identify its snapshot";
				}
			}
			return this.canonicalDocument.normalizedJson().isBlank()
				? "frozen cue canonical document cannot be blank"
				: null;
		}
	}

	public record InvocationContext(
		Optional<UUID> invokerId,
		List<UUID> callTargets,
		Optional<Identifier> gameId,
		Optional<Identifier> phaseId,
		Optional<Identifier> taskId,
		Map<String, String> arguments,
		boolean bypassContext,
		boolean externallyTimed,
		Optional<Origin> origin
	) {
		private static final Codec<List<UUID>> TARGETS_CODEC =
			UUIDUtil.STRING_CODEC.sizeLimitedListOf(HARD_MAX_RECIPIENTS_PER_INSTANCE);

		public static final Codec<InvocationContext> CODEC = RecordCodecBuilder.<InvocationContext>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.optionalFieldOf("invoker_id").forGetter(InvocationContext::invokerId),
					TARGETS_CODEC.optionalFieldOf("call_targets", List.of()).forGetter(InvocationContext::callTargets),
					Identifier.CODEC.optionalFieldOf("game_id").forGetter(InvocationContext::gameId),
					Identifier.CODEC.optionalFieldOf("phase_id").forGetter(InvocationContext::phaseId),
					Identifier.CODEC.optionalFieldOf("task_id").forGetter(InvocationContext::taskId),
					STRING_MAP_CODEC.optionalFieldOf("arguments", Map.of()).forGetter(InvocationContext::arguments),
					Codec.BOOL.optionalFieldOf("bypass_context", false).forGetter(InvocationContext::bypassContext),
					Codec.BOOL.optionalFieldOf("externally_timed", false).forGetter(InvocationContext::externallyTimed),
					Origin.CODEC.optionalFieldOf("origin").forGetter(InvocationContext::origin)
				)
				.apply(instance, InvocationContext::new)
		).validate(InvocationContext::validated);

		public InvocationContext {
			invokerId = immutableOptional(invokerId);
			callTargets = sortedUuidList(callTargets);
			gameId = immutableOptional(gameId);
			phaseId = immutableOptional(phaseId);
			taskId = immutableOptional(taskId);
			arguments = immutableStringMap(arguments);
			origin = immutableOptional(origin);
		}

		/** Source-compatible constructor for snapshots and callers written before stable origins. */
		public InvocationContext(
			final Optional<UUID> invokerId,
			final List<UUID> callTargets,
			final Optional<Identifier> gameId,
			final Optional<Identifier> phaseId,
			final Optional<Identifier> taskId,
			final Map<String, String> arguments,
			final boolean externallyTimed
		) {
			this(
				invokerId,
				callTargets,
				gameId,
				phaseId,
				taskId,
				arguments,
				false,
				externallyTimed,
				Optional.empty()
			);
		}

		/** Source-compatible constructor for snapshots written before context bypass existed. */
		public InvocationContext(
			final Optional<UUID> invokerId,
			final List<UUID> callTargets,
			final Optional<Identifier> gameId,
			final Optional<Identifier> phaseId,
			final Optional<Identifier> taskId,
			final Map<String, String> arguments,
			final boolean externallyTimed,
			final Optional<Origin> origin
		) {
			this(
				invokerId,
				callTargets,
				gameId,
				phaseId,
				taskId,
				arguments,
				false,
				externallyTimed,
				origin
			);
		}

		private DataResult<InvocationContext> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (new HashSet<>(this.callTargets).size() != this.callTargets.size()) {
				return "call targets contain duplicates";
			}
			String mapError = stringMapError(this.arguments, "invocation arguments");
			if (mapError != null) {
				return mapError;
			}
			return this.origin.map(Origin::validationError).orElse(null);
		}
	}

	/** Stable command invocation dimension and XYZ origin. */
	public record Origin(Identifier dimensionId, double x, double y, double z) {
		public static final Codec<Origin> CODEC = RecordCodecBuilder.<Origin>create(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("dimension_id").forGetter(Origin::dimensionId),
					Codec.DOUBLE.fieldOf("x").forGetter(Origin::x),
					Codec.DOUBLE.fieldOf("y").forGetter(Origin::y),
					Codec.DOUBLE.fieldOf("z").forGetter(Origin::z)
				)
				.apply(instance, Origin::new)
		).validate(Origin::validated);

		public Origin {
			Objects.requireNonNull(dimensionId, "dimensionId");
		}

		private DataResult<Origin> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z)
				? null
				: "message invocation origin coordinates must be finite";
		}
	}

	public record AudienceState(List<UUID> cueTargets, List<NodeAudience> nodeTargets) {
		private static final Codec<List<UUID>> TARGETS_CODEC =
			UUIDUtil.STRING_CODEC.sizeLimitedListOf(HARD_MAX_RECIPIENTS_PER_INSTANCE);
		private static final Codec<List<NodeAudience>> NODE_TARGETS_CODEC =
			NodeAudience.CODEC.sizeLimitedListOf(HARD_MAX_NODE_AUDIENCES);

		public static final Codec<AudienceState> CODEC = RecordCodecBuilder.<AudienceState>create(
			instance -> instance.group(
					TARGETS_CODEC.fieldOf("cue_targets").forGetter(AudienceState::cueTargets),
					NODE_TARGETS_CODEC.optionalFieldOf("node_targets", List.of()).forGetter(AudienceState::nodeTargets)
				)
				.apply(instance, AudienceState::new)
		).validate(AudienceState::validated);

		public AudienceState {
			cueTargets = sortedUuidList(cueTargets);
			nodeTargets = Objects.requireNonNull(nodeTargets, "nodeTargets").stream()
				.sorted(Comparator.comparing(NodeAudience::nodeId))
				.toList();
		}

		private DataResult<AudienceState> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (new HashSet<>(this.cueTargets).size() != this.cueTargets.size()) {
				return "cue targets contain duplicates";
			}
			Set<String> nodeIds = new HashSet<>();
			for (NodeAudience value : this.nodeTargets) {
				String nested = value.validationError();
				if (nested != null) {
					return nested;
				}
				if (!nodeIds.add(value.nodeId())) {
					return "duplicate node audience";
				}
			}
			return null;
		}
	}

	public record NodeAudience(String nodeId, List<UUID> recipients) {
		private static final Codec<List<UUID>> RECIPIENTS_CODEC =
			UUIDUtil.STRING_CODEC.sizeLimitedListOf(HARD_MAX_RECIPIENTS_PER_INSTANCE);
		public static final Codec<NodeAudience> CODEC = RecordCodecBuilder.<NodeAudience>create(
			instance -> instance.group(
					LOCAL_ID_CODEC.fieldOf("node_id").forGetter(NodeAudience::nodeId),
					RECIPIENTS_CODEC.fieldOf("recipients").forGetter(NodeAudience::recipients)
				)
				.apply(instance, NodeAudience::new)
		).validate(NodeAudience::validated);

		public NodeAudience {
			nodeId = requireLocalId(nodeId, "node id");
			recipients = sortedUuidList(recipients);
		}

		private DataResult<NodeAudience> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return new HashSet<>(this.recipients).size() == this.recipients.size()
				? null
				: "node audience recipients contain duplicates";
		}
	}

	public record TimingState(
		ClockMode clock,
		Map<String, Long> authoritativeNodeDurationsNanos,
		Optional<Long> standardCompleteAtNanos
	) {
		public static final Codec<TimingState> CODEC = RecordCodecBuilder.<TimingState>create(
			instance -> instance.group(
					ClockMode.CODEC.fieldOf("clock").forGetter(TimingState::clock),
					DURATION_MAP_CODEC.optionalFieldOf("node_durations_nanos", Map.of()).forGetter(TimingState::authoritativeNodeDurationsNanos),
					Codec.LONG.optionalFieldOf("standard_complete_at_nanos").forGetter(TimingState::standardCompleteAtNanos)
				)
				.apply(instance, TimingState::new)
		).validate(TimingState::validated);

		public TimingState {
			Objects.requireNonNull(clock, "clock");
			authoritativeNodeDurationsNanos = immutableLongMap(authoritativeNodeDurationsNanos);
			standardCompleteAtNanos = immutableOptional(standardCompleteAtNanos);
		}

		private DataResult<TimingState> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.authoritativeNodeDurationsNanos.size() > MAX_MAP_ENTRIES) {
				return "authoritative node duration count exceeds " + MAX_MAP_ENTRIES;
			}
			for (Map.Entry<String, Long> value : this.authoritativeNodeDurationsNanos.entrySet()) {
				if (!validLocalId(value.getKey()) || value.getValue() < 0L) {
					return "authoritative node duration is invalid";
				}
			}
			return this.standardCompleteAtNanos.filter(value -> value < 0L).isPresent()
				? "standard completion point cannot be negative"
				: null;
		}
	}

	public record ProgressState(
		InstanceStatus status,
		Optional<InstanceStatus> resumeStatus,
		long cycle,
		long standardElapsedNanos,
		List<Occurrence> emittedOccurrences,
		List<UUID> completedTargets,
		List<EventOffset> eventOffsets,
		boolean forcedCompletion,
		long instanceRevision
	) {
		private static final Codec<List<Occurrence>> OCCURRENCES_CODEC =
			Occurrence.CODEC.sizeLimitedListOf(HARD_MAX_OCCURRENCES);
		private static final Codec<List<UUID>> COMPLETED_CODEC =
			UUIDUtil.STRING_CODEC.sizeLimitedListOf(HARD_MAX_RECIPIENTS_PER_INSTANCE);
		private static final Codec<List<EventOffset>> EVENTS_CODEC =
			EventOffset.CODEC.sizeLimitedListOf(HARD_MAX_EVENT_OFFSETS);

		public static final Codec<ProgressState> CODEC = RecordCodecBuilder.<ProgressState>create(
			instance -> instance.group(
					InstanceStatus.CODEC.fieldOf("status").forGetter(ProgressState::status),
					InstanceStatus.CODEC.optionalFieldOf("resume_status").forGetter(ProgressState::resumeStatus),
					Codec.LONG.fieldOf("cycle").forGetter(ProgressState::cycle),
					Codec.LONG.fieldOf("standard_elapsed_nanos").forGetter(ProgressState::standardElapsedNanos),
					OCCURRENCES_CODEC.optionalFieldOf("emitted_occurrences", List.of()).forGetter(ProgressState::emittedOccurrences),
					COMPLETED_CODEC.optionalFieldOf("completed_targets", List.of()).forGetter(ProgressState::completedTargets),
					EVENTS_CODEC.optionalFieldOf("event_offsets", List.of()).forGetter(ProgressState::eventOffsets),
					Codec.BOOL.optionalFieldOf("forced_completion", false).forGetter(ProgressState::forcedCompletion),
					Codec.LONG.fieldOf("instance_revision").forGetter(ProgressState::instanceRevision)
				)
				.apply(instance, ProgressState::new)
		).validate(ProgressState::validated);

		public ProgressState {
			Objects.requireNonNull(status, "status");
			resumeStatus = immutableOptional(resumeStatus);
			emittedOccurrences = sortedUniqueOccurrences(emittedOccurrences);
			completedTargets = sortedUuidList(completedTargets);
			eventOffsets = Objects.requireNonNull(eventOffsets, "eventOffsets").stream()
				.sorted(EventOffset.ORDER)
				.toList();
		}

		private DataResult<ProgressState> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.cycle < 0L || this.standardElapsedNanos < 0L || this.instanceRevision < 0L) {
				return "message progress values cannot be negative";
			}
			if ((this.status == InstanceStatus.PAUSED) != this.resumeStatus.isPresent()) {
				return "paused persisted instance must carry exactly one resume status";
			}
			if (this.resumeStatus.filter(value -> value == InstanceStatus.PAUSED).isPresent()) {
				return "paused instance cannot resume into PAUSED";
			}
			if (new HashSet<>(this.emittedOccurrences).size() != this.emittedOccurrences.size()) {
				return "emitted occurrences contain duplicates";
			}
			if (new HashSet<>(this.completedTargets).size() != this.completedTargets.size()) {
				return "completed targets contain duplicates";
			}
			Set<EventOffsetKey> eventKeys = new HashSet<>();
			for (EventOffset value : this.eventOffsets) {
				if (value.validationError() != null || !eventKeys.add(value.key())) {
					return "event offsets contain an invalid or duplicate event";
				}
			}
			return null;
		}
	}

	public record RecipientState(
		UUID recipientId,
		long createdElapsedNanos,
		Optional<Long> perPlayerAnchorElapsedNanos,
		List<Occurrence> projectedOccurrences,
		List<Occurrence> decidedOccurrences,
		List<LockedField> lockedFields,
		List<ConditionDecision> conditionDecisions,
		Optional<Long> completionAtNanos,
		boolean completionCommitted
	) {
		private static final Codec<List<Occurrence>> OCCURRENCES_CODEC =
			Occurrence.CODEC.sizeLimitedListOf(HARD_MAX_OCCURRENCES);
		private static final Codec<List<LockedField>> LOCKED_CODEC =
			LockedField.CODEC.sizeLimitedListOf(HARD_MAX_LOCKED_FIELDS_PER_RECIPIENT);
		private static final Codec<List<ConditionDecision>> DECISIONS_CODEC =
			ConditionDecision.CODEC.sizeLimitedListOf(HARD_MAX_CONDITION_DECISIONS_PER_RECIPIENT);

		public static final Codec<RecipientState> CODEC = RecordCodecBuilder.<RecipientState>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("recipient_id").forGetter(RecipientState::recipientId),
					Codec.LONG.fieldOf("created_elapsed_nanos").forGetter(RecipientState::createdElapsedNanos),
					Codec.LONG.optionalFieldOf("per_player_anchor_elapsed_nanos").forGetter(RecipientState::perPlayerAnchorElapsedNanos),
					OCCURRENCES_CODEC.optionalFieldOf("projected_occurrences", List.of()).forGetter(RecipientState::projectedOccurrences),
					OCCURRENCES_CODEC.optionalFieldOf("decided_occurrences", List.of()).forGetter(RecipientState::decidedOccurrences),
					LOCKED_CODEC.optionalFieldOf("locked_fields", List.of()).forGetter(RecipientState::lockedFields),
					DECISIONS_CODEC.optionalFieldOf("condition_decisions", List.of()).forGetter(RecipientState::conditionDecisions),
					Codec.LONG.optionalFieldOf("completion_at_nanos").forGetter(RecipientState::completionAtNanos),
					Codec.BOOL.optionalFieldOf("completion_committed", false).forGetter(RecipientState::completionCommitted)
				)
				.apply(instance, RecipientState::new)
		).validate(RecipientState::validated);

		public RecipientState {
			Objects.requireNonNull(recipientId, "recipientId");
			perPlayerAnchorElapsedNanos = immutableOptional(perPlayerAnchorElapsedNanos);
			projectedOccurrences = sortedUniqueOccurrences(projectedOccurrences);
			decidedOccurrences = sortedUniqueOccurrences(decidedOccurrences);
			lockedFields = Objects.requireNonNull(lockedFields, "lockedFields").stream()
				.sorted(Comparator.comparingInt(LockedField::slot))
				.toList();
			conditionDecisions = Objects.requireNonNull(conditionDecisions, "conditionDecisions").stream()
				.sorted(Comparator.comparing(ConditionDecision::decisionKey))
				.toList();
			completionAtNanos = immutableOptional(completionAtNanos);
		}

		private DataResult<RecipientState> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				this.createdElapsedNanos < 0L
					|| this.perPlayerAnchorElapsedNanos.filter(value -> value < 0L).isPresent()
					|| this.completionAtNanos.filter(value -> value < 0L).isPresent()
			) {
				return "recipient elapsed/completion values cannot be negative";
			}
			if (new HashSet<>(this.projectedOccurrences).size() != this.projectedOccurrences.size()) {
				return "projected occurrences contain duplicates";
			}
			if (new HashSet<>(this.decidedOccurrences).size() != this.decidedOccurrences.size()) {
				return "decided occurrences contain duplicates";
			}
			Set<Integer> slots = new HashSet<>();
			for (LockedField value : this.lockedFields) {
				if (value.validationError() != null || !slots.add(value.slot())) {
					return "locked fields contain an invalid or duplicate slot";
				}
			}
			Set<String> conditionKeys = new HashSet<>();
			for (ConditionDecision value : this.conditionDecisions) {
				if (value.validationError() != null || !conditionKeys.add(value.decisionKey())) {
					return "condition decisions contain an invalid or duplicate key";
				}
			}
			return this.completionCommitted && this.completionAtNanos.isEmpty()
				? "committed recipient completion requires a frozen completion point"
				: null;
		}
	}

	public record Occurrence(
		String nodeId,
		int occurrence,
		CallbackTrigger trigger,
		Optional<UUID> targetId
	) {
		private static final Comparator<Occurrence> ORDER = Comparator
			.comparing(Occurrence::nodeId)
			.thenComparingInt(Occurrence::occurrence)
			.thenComparing(value -> value.trigger().getSerializedName())
			.thenComparing(value -> value.targetId().map(UUID::toString).orElse(""));

		public static final Codec<Occurrence> CODEC = RecordCodecBuilder.<Occurrence>create(
			instance -> instance.group(
					LOCAL_ID_CODEC.fieldOf("node_id").forGetter(Occurrence::nodeId),
					Codec.INT.fieldOf("occurrence").forGetter(Occurrence::occurrence),
					CallbackTrigger.CODEC.fieldOf("trigger").forGetter(Occurrence::trigger),
					UUIDUtil.STRING_CODEC.optionalFieldOf("target_id").forGetter(Occurrence::targetId)
				)
				.apply(instance, Occurrence::new)
		).validate(Occurrence::validated);

		public Occurrence {
			nodeId = requireLocalId(nodeId, "node id");
			Objects.requireNonNull(trigger, "trigger");
			targetId = immutableOptional(targetId);
		}

		private DataResult<Occurrence> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.occurrence < 0) {
				return "occurrence index cannot be negative";
			}
			return (this.trigger == CallbackTrigger.TARGET_COMPLETE) != this.targetId.isPresent()
				? "only target-complete occurrence carries a target"
				: null;
		}
	}

	public record EventOffset(CallbackTrigger trigger, Optional<UUID> targetId, long offsetNanos) {
		private static final Comparator<EventOffset> ORDER = Comparator
			.<EventOffset, String>comparing(value -> value.trigger().getSerializedName())
			.thenComparing(value -> value.targetId().map(UUID::toString).orElse(""));
		public static final Codec<EventOffset> CODEC = RecordCodecBuilder.<EventOffset>create(
			instance -> instance.group(
					CallbackTrigger.CODEC.fieldOf("trigger").forGetter(EventOffset::trigger),
					UUIDUtil.STRING_CODEC.optionalFieldOf("target_id").forGetter(EventOffset::targetId),
					Codec.LONG.fieldOf("offset_nanos").forGetter(EventOffset::offsetNanos)
				)
				.apply(instance, EventOffset::new)
		).validate(EventOffset::validated);

		public EventOffset {
			Objects.requireNonNull(trigger, "trigger");
			targetId = immutableOptional(targetId);
		}

		private EventOffsetKey key() {
			return new EventOffsetKey(this.trigger, this.targetId);
		}

		private DataResult<EventOffset> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.offsetNanos < 0L) {
				return "event offset cannot be negative";
			}
			return (this.trigger == CallbackTrigger.TARGET_COMPLETE) != this.targetId.isPresent()
				? "only target-complete event carries a target"
				: null;
		}
	}

	public record LockedField(int slot, String canonicalComponent) {
		public static final Codec<LockedField> CODEC = RecordCodecBuilder.<LockedField>create(
			instance -> instance.group(
					Codec.INT.fieldOf("slot").forGetter(LockedField::slot),
					CANONICAL_VALUE_CODEC.fieldOf("canonical_component").forGetter(LockedField::canonicalComponent)
				)
				.apply(instance, LockedField::new)
		).validate(LockedField::validated);

		public LockedField {
			canonicalComponent = Objects.requireNonNull(canonicalComponent, "canonicalComponent");
		}

		private DataResult<LockedField> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.slot < 0 || this.canonicalComponent.isBlank()
				? "locked field slot/component is invalid"
				: null;
		}
	}

	public record ConditionDecision(String decisionKey, boolean matched) {
		public static final Codec<ConditionDecision> CODEC = RecordCodecBuilder.<ConditionDecision>create(
			instance -> instance.group(
					CANONICAL_VALUE_CODEC.fieldOf("decision_key").forGetter(ConditionDecision::decisionKey),
					Codec.BOOL.fieldOf("matched").forGetter(ConditionDecision::matched)
				)
				.apply(instance, ConditionDecision::new)
		).validate(ConditionDecision::validated);

		public ConditionDecision {
			decisionKey = Objects.requireNonNull(decisionKey, "decisionKey");
		}

		private DataResult<ConditionDecision> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.decisionKey.isBlank() ? "condition decision key cannot be blank" : null;
		}
	}

	public record PendingOfflineDelivery(
		UUID instanceId,
		UUID recipientId,
		OfflineMode mode,
		long queuedAtGameTick,
		Optional<Long> expiresAtGameTick,
		Optional<String> finalCanonicalComponent
	) {
		private static final Comparator<PendingOfflineDelivery> ORDER = Comparator
			.comparingLong(PendingOfflineDelivery::queuedAtGameTick)
			.thenComparing(PendingOfflineDelivery::instanceId)
			.thenComparing(PendingOfflineDelivery::recipientId);

		public static final Codec<PendingOfflineDelivery> CODEC = RecordCodecBuilder.<PendingOfflineDelivery>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(PendingOfflineDelivery::instanceId),
					UUIDUtil.STRING_CODEC.fieldOf("recipient_id").forGetter(PendingOfflineDelivery::recipientId),
					OfflineMode.CODEC.fieldOf("mode").forGetter(PendingOfflineDelivery::mode),
					Codec.LONG.fieldOf("queued_at_game_tick").forGetter(PendingOfflineDelivery::queuedAtGameTick),
					Codec.LONG.optionalFieldOf("expires_at_game_tick").forGetter(PendingOfflineDelivery::expiresAtGameTick),
					CANONICAL_VALUE_CODEC.optionalFieldOf("final_canonical_component").forGetter(PendingOfflineDelivery::finalCanonicalComponent)
				)
				.apply(instance, PendingOfflineDelivery::new)
		).validate(PendingOfflineDelivery::validated);

		public PendingOfflineDelivery {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(recipientId, "recipientId");
			Objects.requireNonNull(mode, "mode");
			expiresAtGameTick = immutableOptional(expiresAtGameTick);
			finalCanonicalComponent = immutableOptional(finalCanonicalComponent);
		}

		private PendingOfflineKey key() {
			return new PendingOfflineKey(this.instanceId, this.recipientId);
		}

		private DataResult<PendingOfflineDelivery> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.queuedAtGameTick < 0L) {
				return "offline delivery queued tick cannot be negative";
			}
			if (this.expiresAtGameTick.filter(value -> value < this.queuedAtGameTick).isPresent()) {
				return "offline delivery expiry cannot precede its queued tick";
			}
			return (this.mode == OfflineMode.FINAL_ONLY) != this.finalCanonicalComponent.isPresent()
				? "final-only delivery must carry exactly one final canonical component"
				: null;
		}
	}

	public record Tombstone(
		UUID instanceId,
		Identifier cueId,
		TerminalStatus status,
		long terminalAtGameTick,
		String reason
	) {
		private static final Comparator<Tombstone> ORDER = Comparator
			.comparingLong(Tombstone::terminalAtGameTick)
			.thenComparing(Tombstone::instanceId);
		public static final Codec<Tombstone> CODEC = RecordCodecBuilder.<Tombstone>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(Tombstone::instanceId),
					Identifier.CODEC.fieldOf("cue_id").forGetter(Tombstone::cueId),
					TerminalStatus.CODEC.fieldOf("status").forGetter(Tombstone::status),
					Codec.LONG.fieldOf("terminal_at_game_tick").forGetter(Tombstone::terminalAtGameTick),
					Codec.sizeLimitedString(MAX_REASON_LENGTH).fieldOf("reason").forGetter(Tombstone::reason)
				)
				.apply(instance, Tombstone::new)
		).validate(Tombstone::validated);

		public Tombstone {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(cueId, "cueId");
			Objects.requireNonNull(status, "status");
			reason = Objects.requireNonNull(reason, "reason").strip();
		}

		private DataResult<Tombstone> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.terminalAtGameTick < 0L || this.reason.isBlank()
				? "message tombstone tick/reason is invalid"
				: null;
		}
	}

	public record CallbackLedger(int capacity, List<CallbackEntry> entries) {
		private static final Codec<List<CallbackEntry>> ENTRIES_CODEC =
			CallbackEntry.CODEC.sizeLimitedListOf(HARD_MAX_CALLBACKS);
		public static final Codec<CallbackLedger> CODEC = RecordCodecBuilder.<CallbackLedger>create(
			instance -> instance.group(
					Codec.INT.fieldOf("capacity").forGetter(CallbackLedger::capacity),
					ENTRIES_CODEC.optionalFieldOf("entries", List.of()).forGetter(CallbackLedger::entries)
				)
				.apply(instance, CallbackLedger::new)
		).validate(CallbackLedger::validated);

		public CallbackLedger {
			entries = Objects.requireNonNull(entries, "entries").stream()
				.sorted(CallbackEntry.ORDER)
				.toList();
		}

		public static CallbackLedger empty(final int capacity) {
			return new CallbackLedger(capacity, List.of());
		}

		private DataResult<CallbackLedger> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.capacity < 1 || this.capacity > HARD_MAX_CALLBACKS || this.entries.size() > this.capacity) {
				return "persisted callback ledger capacity/count is invalid";
			}
			Set<CallbackKey> keys = new HashSet<>();
			for (CallbackEntry value : this.entries) {
				String nested = value.validationError();
				if (nested != null) {
					return nested;
				}
				if (!keys.add(value.key())) {
					return "duplicate persisted callback key";
				}
			}
			return null;
		}
	}

	public record CallbackEntry(
		CallbackKey key,
		Identifier functionId,
		CallbackExecution execution,
		CallbackLocation location,
		CallbackStatus status,
		int attemptCount,
		long updatedAtGameTick,
		Optional<String> diagnostic
	) {
		private static final Comparator<CallbackEntry> ORDER = Comparator
			.comparingLong(CallbackEntry::updatedAtGameTick)
			.thenComparing(value -> value.key().instanceId())
			.thenComparing(value -> value.key().nodeId());
		public static final Codec<CallbackEntry> CODEC = RecordCodecBuilder.<CallbackEntry>create(
			instance -> instance.group(
					CallbackKey.CODEC.fieldOf("key").forGetter(CallbackEntry::key),
					Identifier.CODEC.fieldOf("function_id").forGetter(CallbackEntry::functionId),
					CallbackExecution.CODEC.fieldOf("execution").forGetter(CallbackEntry::execution),
					CallbackLocation.CODEC.fieldOf("location").forGetter(CallbackEntry::location),
					CallbackStatus.CODEC.fieldOf("status").forGetter(CallbackEntry::status),
					Codec.INT.fieldOf("attempt_count").forGetter(CallbackEntry::attemptCount),
					Codec.LONG.fieldOf("updated_at_game_tick").forGetter(CallbackEntry::updatedAtGameTick),
					Codec.sizeLimitedString(MAX_DIAGNOSTIC_LENGTH).optionalFieldOf("diagnostic").forGetter(CallbackEntry::diagnostic)
				)
				.apply(instance, CallbackEntry::new)
		).validate(CallbackEntry::validated);

		public CallbackEntry {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(functionId, "functionId");
			Objects.requireNonNull(execution, "execution");
			Objects.requireNonNull(location, "location");
			Objects.requireNonNull(status, "status");
			diagnostic = immutableOptional(diagnostic).map(String::strip);
		}

		public CallbackEntry recoverPrepared(final long gameTick) {
			if (this.status != CallbackStatus.PREPARED) {
				return this;
			}
			return new CallbackEntry(
				this.key,
				this.functionId,
				this.execution,
				this.location,
				CallbackStatus.OUTCOME_UNKNOWN,
				this.attemptCount,
				Math.max(gameTick, this.updatedAtGameTick),
				Optional.of("服务端重启时回调结果未知，已禁止自动重放")
			);
		}

		private DataResult<CallbackEntry> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.attemptCount != 1 || this.updatedAtGameTick < 0L) {
				return "persisted callbacks must contain exactly one non-negative-tick attempt";
			}
			boolean diagnosticRequired = this.status == CallbackStatus.FAILED
				|| this.status == CallbackStatus.OUTCOME_UNKNOWN;
			if (diagnosticRequired != this.diagnostic.filter(value -> !value.isBlank()).isPresent()) {
				return "persisted callback diagnostic does not match status";
			}
			return this.key.validationError();
		}
	}

	public record CallbackKey(
		UUID instanceId,
		long cycle,
		String nodeId,
		int occurrence,
		CallbackTrigger trigger,
		Optional<UUID> targetId
	) {
		public static final Codec<CallbackKey> CODEC = RecordCodecBuilder.<CallbackKey>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(CallbackKey::instanceId),
					Codec.LONG.fieldOf("cycle").forGetter(CallbackKey::cycle),
					LOCAL_ID_CODEC.fieldOf("node_id").forGetter(CallbackKey::nodeId),
					Codec.INT.fieldOf("occurrence").forGetter(CallbackKey::occurrence),
					CallbackTrigger.CODEC.fieldOf("trigger").forGetter(CallbackKey::trigger),
					UUIDUtil.STRING_CODEC.optionalFieldOf("target_id").forGetter(CallbackKey::targetId)
				)
				.apply(instance, CallbackKey::new)
		).validate(CallbackKey::validated);

		public CallbackKey {
			Objects.requireNonNull(instanceId, "instanceId");
			nodeId = requireLocalId(nodeId, "callback node id");
			Objects.requireNonNull(trigger, "trigger");
			targetId = immutableOptional(targetId);
		}

		private DataResult<CallbackKey> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.cycle < 0L || this.occurrence < 0) {
				return "persisted callback cycle/occurrence cannot be negative";
			}
			return (this.trigger == CallbackTrigger.TARGET_COMPLETE) != this.targetId.isPresent()
				? "only target-complete callback key carries a target"
				: null;
		}
	}

	private record EventOffsetKey(CallbackTrigger trigger, Optional<UUID> targetId) {
	}

	private record PendingOfflineKey(UUID instanceId, UUID recipientId) {
	}

	public enum RestartMode implements StringRepresentable {
		TRANSIENT("transient"),
		CONTINUE("continue"),
		FINALIZE("finalize"),
		CANCEL("cancel");

		public static final Codec<RestartMode> CODEC =
			StringRepresentable.fromEnum(RestartMode::values);
		private final String serializedName;

		RestartMode(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum InstanceStatus implements StringRepresentable {
		ACTIVE("active"),
		PAUSED("paused"),
		COMPLETING("completing"),
		CANCELLING("cancelling");

		public static final Codec<InstanceStatus> CODEC =
			StringRepresentable.fromEnum(InstanceStatus::values);
		private final String serializedName;

		InstanceStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum ClockMode implements StringRepresentable {
		GAME_TIME("game_time"),
		PRESENTATION_TIME("presentation_time");

		public static final Codec<ClockMode> CODEC = StringRepresentable.fromEnum(ClockMode::values);
		private final String serializedName;

		ClockMode(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackTrigger implements StringRepresentable {
		START("start"),
		TARGET_COMPLETE("target_complete"),
		ALL_COMPLETE("all_complete"),
		INTERRUPT("interrupt");

		public static final Codec<CallbackTrigger> CODEC =
			StringRepresentable.fromEnum(CallbackTrigger::values);
		private final String serializedName;

		CallbackTrigger(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackExecution implements StringRepresentable {
		AS_SERVER("as_server"),
		AS_INVOKER("as_invoker"),
		AS_TARGET("as_target");

		public static final Codec<CallbackExecution> CODEC =
			StringRepresentable.fromEnum(CallbackExecution::values);
		private final String serializedName;

		CallbackExecution(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackLocation implements StringRepresentable {
		ORIGIN("origin"),
		TARGET("target");

		public static final Codec<CallbackLocation> CODEC =
			StringRepresentable.fromEnum(CallbackLocation::values);
		private final String serializedName;

		CallbackLocation(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackStatus implements StringRepresentable {
		PREPARED("prepared"),
		SUCCEEDED("succeeded"),
		FAILED("failed"),
		OUTCOME_UNKNOWN("outcome_unknown");

		public static final Codec<CallbackStatus> CODEC =
			StringRepresentable.fromEnum(CallbackStatus::values);
		private final String serializedName;

		CallbackStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum OfflineMode implements StringRepresentable {
		QUEUE("queue"),
		FINAL_ONLY("final_only");

		public static final Codec<OfflineMode> CODEC =
			StringRepresentable.fromEnum(OfflineMode::values);
		private final String serializedName;

		OfflineMode(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum TerminalStatus implements StringRepresentable {
		COMPLETED("completed"),
		CANCELLED("cancelled");

		public static final Codec<TerminalStatus> CODEC =
			StringRepresentable.fromEnum(TerminalStatus::values);
		private final String serializedName;

		TerminalStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	private static <T> Optional<T> immutableOptional(final Optional<T> value) {
		return Objects.requireNonNull(value, "optional value");
	}

	private static List<UUID> sortedUuidList(final List<UUID> values) {
		return Objects.requireNonNull(values, "uuid list").stream()
			.map(value -> Objects.requireNonNull(value, "uuid"))
			.sorted()
			.toList();
	}

	private static List<Occurrence> sortedUniqueOccurrences(final List<Occurrence> values) {
		return Objects.requireNonNull(values, "occurrences").stream()
			.map(value -> Objects.requireNonNull(value, "occurrence"))
			.sorted(Occurrence.ORDER)
			.toList();
	}

	private static Map<String, String> immutableStringMap(final Map<String, String> values) {
		TreeMap<String, String> sorted = new TreeMap<>(Objects.requireNonNull(values, "string map"));
		return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
	}

	private static Map<String, Long> immutableLongMap(final Map<String, Long> values) {
		TreeMap<String, Long> sorted = new TreeMap<>(Objects.requireNonNull(values, "long map"));
		return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
	}

	private static String stringMapError(final Map<String, String> values, final String label) {
		if (values.size() > MAX_MAP_ENTRIES) {
			return label + " count exceeds " + MAX_MAP_ENTRIES;
		}
		int characters = 0;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!validLocalId(entry.getKey()) || entry.getValue() == null) {
				return label + " contains an invalid entry";
			}
			try {
				characters = Math.addExact(
					characters,
					Math.addExact(entry.getKey().length(), entry.getValue().length())
				);
			} catch (ArithmeticException overflow) {
				return label + " character count overflow";
			}
		}
		return characters > MAX_MAP_CHARACTERS
			? label + " exceeds " + MAX_MAP_CHARACTERS + " characters"
			: null;
	}

	private static boolean validLocalId(final String value) {
		return value != null && value.matches("[a-z0-9_.-]{1," + MAX_LOCAL_ID_LENGTH + "}");
	}

	private static String requireLocalId(final String value, final String label) {
		String result = Objects.requireNonNull(value, label).strip();
		if (!validLocalId(result)) {
			throw new IllegalArgumentException(
				label + " must contain 1.." + MAX_LOCAL_ID_LENGTH + " local-id characters"
			);
		}
		return result;
	}
}
