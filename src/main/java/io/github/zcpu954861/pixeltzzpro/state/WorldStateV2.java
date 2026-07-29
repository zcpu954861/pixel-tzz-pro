package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Immutable persisted payload for world-state schema v2.
 *
 * <p>This type only describes durable data. Permission checks and flow execution remain server-runtime
 * responsibilities.
 */
public record WorldStateV2(
	int schemaVersion,
	long stateRevision,
	Optional<Identifier> activeGameId,
	Optional<Identifier> activePhaseId,
	Optional<HostRecord> host,
	Map<UUID, PlayerRecord> players,
	Optional<ForcedFlowInstance> activeForcedFlow,
	List<CompletionRecord> completionHistory,
	AuditLog audit,
	Optional<RecoverySnapshotMetadata> recoverySnapshotMetadata
) {
	public static final int SCHEMA_VERSION = 2;
	public static final int MAX_PLAYER_RECORDS = 4_096;
	public static final int MAX_COMPLETION_HISTORY = 16_384;
	public static final int MAX_AUDIT_DETAILS = 256;
	public static final int MAX_INSTANCE_MEMBERS = 64;
	public static final int MAX_PERSISTENT_FIELDS = 256;
	public static final int MAX_SNAPSHOT_DOCUMENTS = 256;
	public static final int MAX_SNAPSHOT_PAGES = 64;
	public static final int MAX_SNAPSHOT_THEMES = 8;
	public static final int MAX_SNAPSHOT_UTF8_BYTES = 2 * 1_024 * 1_024;
	/**
	 * NBT strings are serialized through {@link java.io.DataOutput#writeUTF(String)}, whose
	 * modified-UTF payload is limited to 65,535 bytes. Minecraft deliberately replaces an
	 * oversized string with an empty string instead of aborting the whole save. Keeping frozen
	 * documents in much smaller character chunks prevents a valid snapshot from being silently
	 * blanked during a normal world save. 16,384 UTF-16 code units remain below the limit even
	 * when every code unit needs three modified-UTF bytes.
	 */
	public static final int MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS = 16_384;
	public static final int MAX_FROZEN_DOCUMENT_CHUNKS =
		(MAX_SNAPSHOT_UTF8_BYTES / MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS) + 1;
	public static final int MAX_MEMBER_FIELD_UTF8_BYTES = 64 * 1_024;

	private static final Codec<Map<UUID, PlayerRecord>> PLAYERS_CODEC = Codec.lazyInitialized(
		() -> PlayerRecord.CODEC
			.sizeLimitedListOf(MAX_PLAYER_RECORDS)
			.comapFlatMap(
				records -> indexByUuid(records, PlayerRecord::playerId, "player"),
				records -> List.copyOf(records.values())
			)
	);
	private static final Codec<List<CompletionRecord>> COMPLETION_HISTORY_CODEC =
		Codec.lazyInitialized(
			() -> CompletionRecord.CODEC.sizeLimitedListOf(MAX_COMPLETION_HISTORY)
		);

	public static final Codec<WorldStateV2> CODEC = Codec.lazyInitialized(
		WorldStateV2::createCodec
	);

	private static Codec<WorldStateV2> createCodec() {
		return RecordCodecBuilder.<WorldStateV2>create(
			instance -> instance.group(
					Codec.INT.fieldOf("schema_version").forGetter(WorldStateV2::schemaVersion),
					Codec.LONG.fieldOf("state_revision").forGetter(WorldStateV2::stateRevision),
					Identifier.CODEC.optionalFieldOf("active_game_id").forGetter(WorldStateV2::activeGameId),
					Identifier.CODEC.optionalFieldOf("active_phase_id").forGetter(WorldStateV2::activePhaseId),
					HostRecord.CODEC.optionalFieldOf("host").forGetter(WorldStateV2::host),
					PLAYERS_CODEC.fieldOf("players").forGetter(WorldStateV2::players),
					ForcedFlowInstance.CODEC.optionalFieldOf("active_forced_flow").forGetter(WorldStateV2::activeForcedFlow),
					COMPLETION_HISTORY_CODEC.fieldOf("completion_history").forGetter(WorldStateV2::completionHistory),
					AuditLog.CODEC.fieldOf("audit").forGetter(WorldStateV2::audit),
					RecoverySnapshotMetadata.CODEC
						.optionalFieldOf("recovery_snapshot_metadata")
						.forGetter(WorldStateV2::recoverySnapshotMetadata)
				)
				.apply(instance, WorldStateV2::new)
		).validate(WorldStateV2::validated);
	}

	public WorldStateV2 {
		activeGameId = requiredOptional(activeGameId, "activeGameId");
		activePhaseId = requiredOptional(activePhaseId, "activePhaseId");
		host = requiredOptional(host, "host");
		players = immutableSortedMap(players);
		activeForcedFlow = requiredOptional(activeForcedFlow, "activeForcedFlow");
		completionHistory = List.copyOf(completionHistory);
		audit = Objects.requireNonNull(audit, "audit");
		recoverySnapshotMetadata = requiredOptional(recoverySnapshotMetadata, "recoverySnapshotMetadata");
	}

	public static WorldStateV2 initial() {
		return new WorldStateV2(
			SCHEMA_VERSION,
			0L,
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Map.of(),
			Optional.empty(),
			List.of(),
			AuditLog.empty(),
			Optional.empty()
		);
	}

	public WorldStateV2 withStateRevision(final long revision) {
		return new WorldStateV2(
			this.schemaVersion,
			revision,
			this.activeGameId,
			this.activePhaseId,
			this.host,
			this.players,
			this.activeForcedFlow,
			this.completionHistory,
			this.audit,
			this.recoverySnapshotMetadata
		);
	}

	public WorldStateV2 withActivity(final Identifier gameId, final Identifier phaseId) {
		return new WorldStateV2(
			this.schemaVersion,
			this.stateRevision,
			Optional.of(Objects.requireNonNull(gameId, "gameId")),
			Optional.of(Objects.requireNonNull(phaseId, "phaseId")),
			this.host,
			this.players,
			this.activeForcedFlow,
			this.completionHistory,
			this.audit,
			this.recoverySnapshotMetadata
		);
	}

	public WorldStateV2 withHost(final Optional<HostRecord> value) {
		return copy(this.activeGameId, this.activePhaseId, value, this.players, this.activeForcedFlow, this.completionHistory, this.audit, this.recoverySnapshotMetadata);
	}

	public WorldStateV2 withPlayers(final Map<UUID, PlayerRecord> value) {
		return copy(this.activeGameId, this.activePhaseId, this.host, value, this.activeForcedFlow, this.completionHistory, this.audit, this.recoverySnapshotMetadata);
	}

	public WorldStateV2 withActiveForcedFlow(final Optional<ForcedFlowInstance> value) {
		return copy(this.activeGameId, this.activePhaseId, this.host, this.players, value, this.completionHistory, this.audit, this.recoverySnapshotMetadata);
	}

	public WorldStateV2 withCompletionHistory(final List<CompletionRecord> value) {
		return copy(this.activeGameId, this.activePhaseId, this.host, this.players, this.activeForcedFlow, value, this.audit, this.recoverySnapshotMetadata);
	}

	public WorldStateV2 withAudit(final AuditLog value) {
		return copy(this.activeGameId, this.activePhaseId, this.host, this.players, this.activeForcedFlow, this.completionHistory, value, this.recoverySnapshotMetadata);
	}

	public WorldStateV2 withRecoverySnapshotMetadata(final Optional<RecoverySnapshotMetadata> value) {
		return copy(this.activeGameId, this.activePhaseId, this.host, this.players, this.activeForcedFlow, this.completionHistory, this.audit, value);
	}

	private WorldStateV2 copy(
		final Optional<Identifier> gameId,
		final Optional<Identifier> phaseId,
		final Optional<HostRecord> hostRecord,
		final Map<UUID, PlayerRecord> playerRecords,
		final Optional<ForcedFlowInstance> forcedFlow,
		final List<CompletionRecord> completions,
		final AuditLog auditLog,
		final Optional<RecoverySnapshotMetadata> recoveryMetadata
	) {
		return new WorldStateV2(
			this.schemaVersion,
			this.stateRevision,
			gameId,
			phaseId,
			hostRecord,
			playerRecords,
			forcedFlow,
			completions,
			auditLog,
			recoveryMetadata
		);
	}

	public DataResult<WorldStateV2> validated() {
		String error = validationError();
		return error == null ? DataResult.success(this) : DataResult.error(() -> error);
	}

	private String validationError() {
		if (this.schemaVersion != SCHEMA_VERSION) {
			return "schema v2 payload declared schema " + this.schemaVersion;
		}
		if (this.stateRevision < 0L) {
			return "negative state revision";
		}
		if (this.activeGameId.isPresent() != this.activePhaseId.isPresent()) {
			return "active game and phase must either both be present or both be absent";
		}
		if (this.players.size() > MAX_PLAYER_RECORDS) {
			return "player record count exceeds " + MAX_PLAYER_RECORDS;
		}
		if (this.completionHistory.size() > MAX_COMPLETION_HISTORY) {
			return "completion history exceeds " + MAX_COMPLETION_HISTORY;
		}
		for (PlayerRecord player : this.players.values()) {
			String nested = player.validationError();
			if (nested != null) {
				return "player " + player.playerId + ": " + nested;
			}
		}
		if (this.activeForcedFlow.isPresent()) {
			ForcedFlowInstance flow = this.activeForcedFlow.orElseThrow();
			String nested = flow.validationError();
			if (nested != null) {
				return "active forced flow: " + nested;
			}
			if (this.activeGameId.isPresent() && !this.activeGameId.orElseThrow().equals(flow.identity.gameId)) {
				return "active forced flow belongs to a different game";
			}
		}
		for (CompletionRecord completion : this.completionHistory) {
			String nested = completion.validationError();
			if (nested != null) {
				return "completion history: " + nested;
			}
		}
		String auditError = this.audit.validationError();
		if (auditError != null) {
			return "audit: " + auditError;
		}
		return this.recoverySnapshotMetadata.map(RecoverySnapshotMetadata::validationError).orElse(null);
	}

	public record HostRecord(
		UUID playerId,
		Optional<String> lastKnownName,
		Optional<Long> assignedAtEpochMillis,
		Optional<UUID> lastActorId,
		Optional<HostOperation> lastOperation
	) {
		public static final Codec<HostRecord> CODEC = RecordCodecBuilder.<HostRecord>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("player_id").forGetter(HostRecord::playerId),
					Codec.sizeLimitedString(64).optionalFieldOf("last_known_name").forGetter(HostRecord::lastKnownName),
					Codec.LONG.optionalFieldOf("assigned_at").forGetter(HostRecord::assignedAtEpochMillis),
					UUIDUtil.STRING_CODEC.optionalFieldOf("last_actor_id").forGetter(HostRecord::lastActorId),
					HostOperation.CODEC.optionalFieldOf("last_operation").forGetter(HostRecord::lastOperation)
				)
				.apply(instance, HostRecord::new)
		).validate(HostRecord::validated);

		public HostRecord {
			Objects.requireNonNull(playerId, "playerId");
			lastKnownName = requiredOptional(lastKnownName, "lastKnownName");
			assignedAtEpochMillis = requiredOptional(assignedAtEpochMillis, "assignedAtEpochMillis");
			lastActorId = requiredOptional(lastActorId, "lastActorId");
			lastOperation = requiredOptional(lastOperation, "lastOperation");
		}

		public static HostRecord migratedLegacy(final UUID playerId) {
			return new HostRecord(
				playerId,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}

		private DataResult<HostRecord> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.lastKnownName.filter(String::isBlank).isPresent()) {
				return "host name cannot be blank";
			}
			if (this.assignedAtEpochMillis.filter(value -> value < 0L).isPresent()) {
				return "host assignment time cannot be negative";
			}
			return null;
		}
	}

	public enum HostOperation implements StringRepresentable {
		CLAIM("claim"),
		TRANSFER("transfer"),
		TAKEOVER("takeover");

		public static final Codec<HostOperation> CODEC = stableEnumCodec(values());
		private final String serializedName;

		HostOperation(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record PlayerRecord(
		UUID playerId,
		String lastKnownName,
		Identifier roleId,
		Optional<Identifier> teamId,
		Identifier lifeStateId,
		Map<Identifier, PersistentFieldValue> persistentFields,
		Optional<PendingRoleChange> pendingRoleChange,
		Optional<PlayerFlowParticipation> activeFlowParticipation,
		CompletionSummary completionSummary,
		long firstSeenAtEpochMillis,
		long lastSeenAtEpochMillis
	) {
		public static final Codec<PlayerRecord> CODEC = RecordCodecBuilder.<PlayerRecord>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("player_id").forGetter(PlayerRecord::playerId),
					Codec.sizeLimitedString(64).fieldOf("last_known_name").forGetter(PlayerRecord::lastKnownName),
					Identifier.CODEC.fieldOf("role_id").forGetter(PlayerRecord::roleId),
					Identifier.CODEC.optionalFieldOf("team_id").forGetter(PlayerRecord::teamId),
					Identifier.CODEC.fieldOf("life_state_id").forGetter(PlayerRecord::lifeStateId),
					Codec.unboundedMap(Identifier.CODEC, PersistentFieldValue.CODEC)
						.fieldOf("persistent_fields")
						.forGetter(PlayerRecord::persistentFields),
					PendingRoleChange.CODEC
						.optionalFieldOf("pending_role_change")
						.forGetter(PlayerRecord::pendingRoleChange),
					PlayerFlowParticipation.CODEC
						.optionalFieldOf("active_flow_participation")
						.forGetter(PlayerRecord::activeFlowParticipation),
					CompletionSummary.CODEC.fieldOf("completion_summary").forGetter(PlayerRecord::completionSummary),
					Codec.LONG.fieldOf("first_seen_at").forGetter(PlayerRecord::firstSeenAtEpochMillis),
					Codec.LONG.fieldOf("last_seen_at").forGetter(PlayerRecord::lastSeenAtEpochMillis)
				)
				.apply(instance, PlayerRecord::new)
		).validate(PlayerRecord::validated);

		public PlayerRecord {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(lastKnownName, "lastKnownName");
			Objects.requireNonNull(roleId, "roleId");
			teamId = requiredOptional(teamId, "teamId");
			Objects.requireNonNull(lifeStateId, "lifeStateId");
			persistentFields = immutableSortedMap(persistentFields);
			pendingRoleChange = requiredOptional(pendingRoleChange, "pendingRoleChange");
			activeFlowParticipation = requiredOptional(activeFlowParticipation, "activeFlowParticipation");
			Objects.requireNonNull(completionSummary, "completionSummary");
		}

		private DataResult<PlayerRecord> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.lastKnownName.isBlank()) {
				return "last known name cannot be blank";
			}
			if (this.persistentFields.size() > MAX_PERSISTENT_FIELDS) {
				return "persistent field count exceeds " + MAX_PERSISTENT_FIELDS;
			}
			if (this.firstSeenAtEpochMillis < 0L || this.lastSeenAtEpochMillis < this.firstSeenAtEpochMillis) {
				return "invalid first/last seen timestamps";
			}
			for (PersistentFieldValue value : this.persistentFields.values()) {
				String nested = value.validationError();
				if (nested != null) {
					return nested;
				}
			}
			if (this.pendingRoleChange.isPresent()) {
				String nested = this.pendingRoleChange.orElseThrow().validationError();
				if (nested != null) {
					return nested;
				}
				if (!this.pendingRoleChange.orElseThrow().targetPlayerId.equals(this.playerId)) {
					return "pending role change targets a different player";
				}
			}
			if (this.activeFlowParticipation.isPresent()) {
				String nested = this.activeFlowParticipation.orElseThrow().validationError();
				if (nested != null) {
					return nested;
				}
			}
			return this.completionSummary.validationError();
		}
	}

	public record PersistentFieldValue(int fieldVersion, StoredValue value) {
		public static final Codec<PersistentFieldValue> CODEC = RecordCodecBuilder.<PersistentFieldValue>create(
			instance -> instance.group(
					Codec.INT.fieldOf("field_version").forGetter(PersistentFieldValue::fieldVersion),
					StoredValue.CODEC.fieldOf("value").forGetter(PersistentFieldValue::value)
				)
				.apply(instance, PersistentFieldValue::new)
		).validate(PersistentFieldValue::validated);

		public PersistentFieldValue {
			Objects.requireNonNull(value, "value");
		}

		private DataResult<PersistentFieldValue> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.fieldVersion <= 0) {
				return "persistent field version must be positive";
			}
			return this.value.validationError();
		}
	}

	/**
	 * Closed, immutable representation of every field value type registered by 2A.
	 */
	public record StoredValue(
		FieldValueType type,
		Optional<Boolean> booleanValue,
		Optional<Integer> integerValue,
		Optional<String> stringValue,
		Optional<Identifier> identifierValue,
		List<String> choices
	) {
		public static final Codec<StoredValue> CODEC = RecordCodecBuilder.<StoredValue>create(
			instance -> instance.group(
					FieldValueType.CODEC.fieldOf("type").forGetter(StoredValue::type),
					Codec.BOOL.optionalFieldOf("boolean_value").forGetter(StoredValue::booleanValue),
					Codec.INT.optionalFieldOf("integer_value").forGetter(StoredValue::integerValue),
					Codec.sizeLimitedString(32_767).optionalFieldOf("string_value").forGetter(StoredValue::stringValue),
					Identifier.CODEC.optionalFieldOf("identifier_value").forGetter(StoredValue::identifierValue),
					Codec.sizeLimitedString(1_024).sizeLimitedListOf(256).fieldOf("choices").forGetter(StoredValue::choices)
				)
				.apply(instance, StoredValue::new)
		).validate(StoredValue::validated);

		public StoredValue {
			Objects.requireNonNull(type, "type");
			booleanValue = requiredOptional(booleanValue, "booleanValue");
			integerValue = requiredOptional(integerValue, "integerValue");
			stringValue = requiredOptional(stringValue, "stringValue");
			identifierValue = requiredOptional(identifierValue, "identifierValue");
			choices = List.copyOf(choices);
		}

		public static StoredValue ofBoolean(final boolean value) {
			return new StoredValue(
				FieldValueType.BOOLEAN,
				Optional.of(value),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				List.of()
			);
		}

		public static StoredValue ofInteger(final int value) {
			return new StoredValue(
				FieldValueType.INTEGER,
				Optional.empty(),
				Optional.of(value),
				Optional.empty(),
				Optional.empty(),
				List.of()
			);
		}

		public static StoredValue ofString(final String value) {
			return new StoredValue(
				FieldValueType.STRING,
				Optional.empty(),
				Optional.empty(),
				Optional.of(value),
				Optional.empty(),
				List.of()
			);
		}

		public static StoredValue ofIdentifier(final Identifier value) {
			return new StoredValue(
				FieldValueType.IDENTIFIER,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.of(value),
				List.of()
			);
		}

		public static StoredValue singleChoice(final String value) {
			return new StoredValue(
				FieldValueType.SINGLE_CHOICE,
				Optional.empty(),
				Optional.empty(),
				Optional.of(value),
				Optional.empty(),
				List.of()
			);
		}

		public static StoredValue multiChoice(final List<String> values) {
			return new StoredValue(
				FieldValueType.MULTI_CHOICE,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				values
			);
		}

		private DataResult<StoredValue> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			int scalarCount = (this.booleanValue.isPresent() ? 1 : 0)
				+ (this.integerValue.isPresent() ? 1 : 0)
				+ (this.stringValue.isPresent() ? 1 : 0)
				+ (this.identifierValue.isPresent() ? 1 : 0);
			return switch (this.type) {
				case BOOLEAN -> scalarCount == 1 && this.booleanValue.isPresent() && this.choices.isEmpty()
					? null
					: "boolean stored value has mismatched payload";
				case INTEGER -> scalarCount == 1 && this.integerValue.isPresent() && this.choices.isEmpty()
					? null
					: "integer stored value has mismatched payload";
				case STRING, SINGLE_CHOICE -> scalarCount == 1 && this.stringValue.isPresent() && this.choices.isEmpty()
					? null
					: "string stored value has mismatched payload";
				case IDENTIFIER -> scalarCount == 1 && this.identifierValue.isPresent() && this.choices.isEmpty()
					? null
					: "identifier stored value has mismatched payload";
				case MULTI_CHOICE -> scalarCount == 0
					? null
					: "multi-choice stored value has mismatched scalar payload";
			};
		}

		private int estimatedUtf8Bytes() {
			return 32
				+ this.stringValue.map(value -> value.getBytes(StandardCharsets.UTF_8).length).orElse(0)
				+ this.identifierValue.map(value -> value.toString().getBytes(StandardCharsets.UTF_8).length).orElse(0)
				+ this.choices.stream().mapToInt(value -> value.getBytes(StandardCharsets.UTF_8).length).sum();
		}
	}

	public enum FieldValueType implements StringRepresentable {
		BOOLEAN("boolean"),
		INTEGER("integer"),
		STRING("string"),
		IDENTIFIER("identifier"),
		SINGLE_CHOICE("single_choice"),
		MULTI_CHOICE("multi_choice");

		public static final Codec<FieldValueType> CODEC = stableEnumCodec(values());
		private final String serializedName;

		FieldValueType(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record PendingRoleChange(
		UUID targetPlayerId,
		Identifier targetRoleId,
		Identifier sourceActionId,
		UUID flowInstanceId,
		UUID createdBy,
		long createdAtEpochMillis,
		Identifier previousRoleId,
		long createdAtStateRevision,
		PendingRoleChangeStatus status
	) {
		public static final Codec<PendingRoleChange> CODEC = RecordCodecBuilder.<PendingRoleChange>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("target_player_id").forGetter(PendingRoleChange::targetPlayerId),
					Identifier.CODEC.fieldOf("target_role_id").forGetter(PendingRoleChange::targetRoleId),
					Identifier.CODEC.fieldOf("source_action_id").forGetter(PendingRoleChange::sourceActionId),
					UUIDUtil.STRING_CODEC.fieldOf("flow_instance_id").forGetter(PendingRoleChange::flowInstanceId),
					UUIDUtil.STRING_CODEC.fieldOf("created_by").forGetter(PendingRoleChange::createdBy),
					Codec.LONG.fieldOf("created_at").forGetter(PendingRoleChange::createdAtEpochMillis),
					Identifier.CODEC.fieldOf("previous_role_id").forGetter(PendingRoleChange::previousRoleId),
					Codec.LONG.fieldOf("created_at_state_revision").forGetter(PendingRoleChange::createdAtStateRevision),
					PendingRoleChangeStatus.CODEC.fieldOf("status").forGetter(PendingRoleChange::status)
				)
				.apply(instance, PendingRoleChange::new)
		).validate(PendingRoleChange::validated);

		public PendingRoleChange {
			Objects.requireNonNull(targetPlayerId, "targetPlayerId");
			Objects.requireNonNull(targetRoleId, "targetRoleId");
			Objects.requireNonNull(sourceActionId, "sourceActionId");
			Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			Objects.requireNonNull(createdBy, "createdBy");
			Objects.requireNonNull(previousRoleId, "previousRoleId");
			Objects.requireNonNull(status, "status");
		}

		private DataResult<PendingRoleChange> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.createdAtEpochMillis < 0L || this.createdAtStateRevision < 0L
				? "pending role change contains a negative timestamp or revision"
				: null;
		}
	}

	public enum PendingRoleChangeStatus implements StringRepresentable {
		PENDING("pending"),
		APPLIED("applied"),
		CANCELED("canceled"),
		BLOCKED("blocked");

		public static final Codec<PendingRoleChangeStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		PendingRoleChangeStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record PlayerFlowParticipation(UUID instanceId, FlowMemberStatus status) {
		public static final Codec<PlayerFlowParticipation> CODEC = RecordCodecBuilder.<PlayerFlowParticipation>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(PlayerFlowParticipation::instanceId),
					FlowMemberStatus.CODEC.fieldOf("status").forGetter(PlayerFlowParticipation::status)
				)
				.apply(instance, PlayerFlowParticipation::new)
		);

		public PlayerFlowParticipation {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(status, "status");
		}

		private String validationError() {
			return null;
		}
	}

	public record CompletionSummary(long naturalCompletionCount, Optional<Long> lastCompletedAtEpochMillis) {
		public static final Codec<CompletionSummary> CODEC = RecordCodecBuilder.<CompletionSummary>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("natural_completion_count").forGetter(CompletionSummary::naturalCompletionCount),
					Codec.LONG.optionalFieldOf("last_completed_at").forGetter(CompletionSummary::lastCompletedAtEpochMillis)
				)
				.apply(instance, CompletionSummary::new)
		).validate(CompletionSummary::validated);

		public CompletionSummary {
			lastCompletedAtEpochMillis = requiredOptional(lastCompletedAtEpochMillis, "lastCompletedAtEpochMillis");
		}

		public static CompletionSummary empty() {
			return new CompletionSummary(0L, Optional.empty());
		}

		private DataResult<CompletionSummary> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.naturalCompletionCount < 0L
				|| this.lastCompletedAtEpochMillis.filter(value -> value < 0L).isPresent()
				? "invalid completion summary"
				: null;
		}
	}

	public record ForcedFlowInstance(ForcedFlowIdentity identity, ForcedFlowRuntime runtime) {
		public static final Codec<ForcedFlowInstance> CODEC = RecordCodecBuilder.<ForcedFlowInstance>create(
			instance -> instance.group(
					ForcedFlowIdentity.CODEC.fieldOf("identity").forGetter(ForcedFlowInstance::identity),
					ForcedFlowRuntime.CODEC.fieldOf("runtime").forGetter(ForcedFlowInstance::runtime)
				)
				.apply(instance, ForcedFlowInstance::new)
		).validate(ForcedFlowInstance::validated);

		public ForcedFlowInstance {
			Objects.requireNonNull(identity, "identity");
			Objects.requireNonNull(runtime, "runtime");
		}

		private DataResult<ForcedFlowInstance> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			String identityError = this.identity.validationError();
			if (identityError != null) {
				return identityError;
			}
			String runtimeError = this.runtime.validationError();
			if (runtimeError != null) {
				return runtimeError;
			}
			long activeMembers = this.runtime.members.values()
				.stream()
				.filter(member -> member.status != FlowMemberStatus.REMOVED)
				.count();
			if (this.runtime.totalCount != activeMembers) {
				return "flow total count does not match non-removed member count";
			}
			long completed = this.runtime.members.values()
				.stream()
				.filter(member -> member.status != FlowMemberStatus.REMOVED)
				.filter(member -> member.status == FlowMemberStatus.COMPLETED || member.status == FlowMemberStatus.ALREADY_COMPLETE)
				.count();
			return completed != this.runtime.completedCount
				? "flow completed count does not match member states"
				: null;
		}
	}

	public record ForcedFlowIdentity(
		UUID instanceId,
		Identifier gameId,
		Identifier flowId,
		int flowVersion,
		Identifier sourceActionId,
		UUID initiatedBy,
		long createdAtEpochMillis,
		Identifier phaseIdAtCreation,
		long definitionGeneration,
		CompletionPolicy completionPolicy
	) {
		public static final Codec<ForcedFlowIdentity> CODEC = RecordCodecBuilder.<ForcedFlowIdentity>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(ForcedFlowIdentity::instanceId),
					Identifier.CODEC.fieldOf("game_id").forGetter(ForcedFlowIdentity::gameId),
					Identifier.CODEC.fieldOf("flow_id").forGetter(ForcedFlowIdentity::flowId),
					Codec.INT.fieldOf("flow_version").forGetter(ForcedFlowIdentity::flowVersion),
					Identifier.CODEC.fieldOf("source_action_id").forGetter(ForcedFlowIdentity::sourceActionId),
					UUIDUtil.STRING_CODEC.fieldOf("initiated_by").forGetter(ForcedFlowIdentity::initiatedBy),
					Codec.LONG.fieldOf("created_at").forGetter(ForcedFlowIdentity::createdAtEpochMillis),
					Identifier.CODEC.fieldOf("phase_id_at_creation").forGetter(ForcedFlowIdentity::phaseIdAtCreation),
					Codec.LONG.fieldOf("definition_generation").forGetter(ForcedFlowIdentity::definitionGeneration),
					CompletionPolicy.CODEC.fieldOf("completion_policy").forGetter(ForcedFlowIdentity::completionPolicy)
				)
				.apply(instance, ForcedFlowIdentity::new)
		).validate(ForcedFlowIdentity::validated);

		public ForcedFlowIdentity {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(flowId, "flowId");
			Objects.requireNonNull(sourceActionId, "sourceActionId");
			Objects.requireNonNull(initiatedBy, "initiatedBy");
			Objects.requireNonNull(phaseIdAtCreation, "phaseIdAtCreation");
			Objects.requireNonNull(completionPolicy, "completionPolicy");
		}

		private DataResult<ForcedFlowIdentity> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.flowVersion <= 0 || this.createdAtEpochMillis < 0L || this.definitionGeneration < 0L
				? "invalid flow version, timestamp, or generation"
				: null;
		}
	}

	public record ForcedFlowRuntime(
		FlowInstanceStatus status,
		Map<UUID, FlowMemberState> members,
		int completedCount,
		int totalCount,
		long instanceRevision,
		ExecutionSnapshot executionSnapshot,
		CallbackState callbacks,
		Optional<BlockDiagnostic> blockDiagnostic,
		long updatedAtEpochMillis
	) {
		private static final Codec<Map<UUID, FlowMemberState>> MEMBERS_CODEC = FlowMemberState.CODEC
			.sizeLimitedListOf(MAX_INSTANCE_MEMBERS)
			.comapFlatMap(
				records -> indexByUuid(records, FlowMemberState::playerId, "flow member"),
				records -> List.copyOf(records.values())
			);

		public static final Codec<ForcedFlowRuntime> CODEC = RecordCodecBuilder.<ForcedFlowRuntime>create(
			instance -> instance.group(
					FlowInstanceStatus.CODEC.fieldOf("status").forGetter(ForcedFlowRuntime::status),
					MEMBERS_CODEC.fieldOf("members").forGetter(ForcedFlowRuntime::members),
					Codec.INT.fieldOf("completed_count").forGetter(ForcedFlowRuntime::completedCount),
					Codec.INT.fieldOf("total_count").forGetter(ForcedFlowRuntime::totalCount),
					Codec.LONG.fieldOf("instance_revision").forGetter(ForcedFlowRuntime::instanceRevision),
					ExecutionSnapshot.CODEC.fieldOf("execution_snapshot").forGetter(ForcedFlowRuntime::executionSnapshot),
					CallbackState.CODEC.fieldOf("callbacks").forGetter(ForcedFlowRuntime::callbacks),
					BlockDiagnostic.CODEC
						.optionalFieldOf("block_diagnostic")
						.forGetter(ForcedFlowRuntime::blockDiagnostic),
					Codec.LONG.fieldOf("updated_at").forGetter(ForcedFlowRuntime::updatedAtEpochMillis)
				)
				.apply(instance, ForcedFlowRuntime::new)
		).validate(ForcedFlowRuntime::validated);

		public ForcedFlowRuntime {
			Objects.requireNonNull(status, "status");
			members = immutableSortedMap(members);
			Objects.requireNonNull(executionSnapshot, "executionSnapshot");
			Objects.requireNonNull(callbacks, "callbacks");
			blockDiagnostic = requiredOptional(blockDiagnostic, "blockDiagnostic");
		}

		private DataResult<ForcedFlowRuntime> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				this.members.size() > MAX_INSTANCE_MEMBERS
					|| this.completedCount < 0
					|| this.totalCount < 0
					|| this.completedCount > this.totalCount
					|| this.instanceRevision < 0L
					|| this.updatedAtEpochMillis < 0L
			) {
				return "invalid member count, revision, or timestamp";
			}
			for (FlowMemberState member : this.members.values()) {
				String nested = member.validationError();
				if (nested != null) {
					return "member " + member.playerId + ": " + nested;
				}
			}
			String snapshotError = this.executionSnapshot.validationError();
			if (snapshotError != null) {
				return snapshotError;
			}
			String callbackError = this.callbacks.validationError();
			if (callbackError != null) {
				return callbackError;
			}
			return this.blockDiagnostic.map(BlockDiagnostic::validationError).orElse(null);
		}
	}

	public enum CompletionPolicy implements StringRepresentable {
		IF_INCOMPLETE("if_incomplete"),
		ALWAYS("always"),
		RESUME_ONLY("resume_only");

		public static final Codec<CompletionPolicy> CODEC = stableEnumCodec(values());
		private final String serializedName;

		CompletionPolicy(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum FlowInstanceStatus implements StringRepresentable {
		ACTIVE("active"),
		COMPLETED("completed"),
		CANCELED("canceled"),
		BLOCKED("blocked");

		public static final Codec<FlowInstanceStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		FlowInstanceStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record FlowMemberState(
		UUID playerId,
		String lockedName,
		Optional<String> currentNodeId,
		FlowMemberStatus status,
		long memberRevision,
		Map<Identifier, StoredValue> flowFields,
		Optional<UUID> pageInstanceId,
		RequestSequenceWindow requestSequenceWindow,
		Optional<Long> lastCommittedAtEpochMillis,
		boolean offline,
		Optional<Long> lastOnlineAtEpochMillis,
		Optional<Long> completedAtEpochMillis,
		Optional<RemovalRecord> removal
	) {
		public static final Codec<FlowMemberState> CODEC = RecordCodecBuilder.<FlowMemberState>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("player_id").forGetter(FlowMemberState::playerId),
					Codec.sizeLimitedString(64).fieldOf("locked_name").forGetter(FlowMemberState::lockedName),
					Codec.sizeLimitedString(128).optionalFieldOf("current_node_id").forGetter(FlowMemberState::currentNodeId),
					FlowMemberStatus.CODEC.fieldOf("status").forGetter(FlowMemberState::status),
					Codec.LONG.fieldOf("member_revision").forGetter(FlowMemberState::memberRevision),
					Codec.unboundedMap(Identifier.CODEC, StoredValue.CODEC)
						.fieldOf("flow_fields")
						.forGetter(FlowMemberState::flowFields),
					UUIDUtil.STRING_CODEC.optionalFieldOf("page_instance_id").forGetter(FlowMemberState::pageInstanceId),
					RequestSequenceWindow.CODEC
						.fieldOf("request_sequence_window")
						.forGetter(FlowMemberState::requestSequenceWindow),
					Codec.LONG
						.optionalFieldOf("last_committed_at")
						.forGetter(FlowMemberState::lastCommittedAtEpochMillis),
					Codec.BOOL.fieldOf("offline").forGetter(FlowMemberState::offline),
					Codec.LONG.optionalFieldOf("last_online_at").forGetter(FlowMemberState::lastOnlineAtEpochMillis),
					Codec.LONG.optionalFieldOf("completed_at").forGetter(FlowMemberState::completedAtEpochMillis),
					RemovalRecord.CODEC.optionalFieldOf("removal").forGetter(FlowMemberState::removal)
				)
				.apply(instance, FlowMemberState::new)
		).validate(FlowMemberState::validated);

		public FlowMemberState {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(lockedName, "lockedName");
			currentNodeId = requiredOptional(currentNodeId, "currentNodeId");
			Objects.requireNonNull(status, "status");
			flowFields = immutableSortedMap(flowFields);
			pageInstanceId = requiredOptional(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(requestSequenceWindow, "requestSequenceWindow");
			lastCommittedAtEpochMillis = requiredOptional(lastCommittedAtEpochMillis, "lastCommittedAtEpochMillis");
			lastOnlineAtEpochMillis = requiredOptional(lastOnlineAtEpochMillis, "lastOnlineAtEpochMillis");
			completedAtEpochMillis = requiredOptional(completedAtEpochMillis, "completedAtEpochMillis");
			removal = requiredOptional(removal, "removal");
		}

		private DataResult<FlowMemberState> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.lockedName.isBlank()) {
				return "locked name cannot be blank";
			}
			if (this.memberRevision < 0L) {
				return "member revision cannot be negative";
			}
			if (this.flowFields.size() > MAX_PERSISTENT_FIELDS) {
				return "flow field count exceeds " + MAX_PERSISTENT_FIELDS;
			}
			int encodedBytes = this.flowFields.entrySet()
				.stream()
				.mapToInt(entry -> entry.getKey().toString().getBytes(StandardCharsets.UTF_8).length + entry.getValue().estimatedUtf8Bytes())
				.sum();
			if (encodedBytes > MAX_MEMBER_FIELD_UTF8_BYTES) {
				return "member flow fields exceed " + MAX_MEMBER_FIELD_UTF8_BYTES + " UTF-8 bytes";
			}
			for (StoredValue value : this.flowFields.values()) {
				String nested = value.validationError();
				if (nested != null) {
					return nested;
				}
			}
			if (
				this.lastCommittedAtEpochMillis.filter(value -> value < 0L).isPresent()
					|| this.lastOnlineAtEpochMillis.filter(value -> value < 0L).isPresent()
					|| this.completedAtEpochMillis.filter(value -> value < 0L).isPresent()
			) {
				return "member timestamps cannot be negative";
			}
			if (this.status == FlowMemberStatus.REMOVED && this.removal.isEmpty()) {
				return "removed member is missing removal metadata";
			}
			if (this.status != FlowMemberStatus.REMOVED && this.removal.isPresent()) {
				return "non-removed member contains removal metadata";
			}
			if (
				(this.status == FlowMemberStatus.COMPLETED || this.status == FlowMemberStatus.ALREADY_COMPLETE)
					&& this.completedAtEpochMillis.isEmpty()
			) {
				return "completed member is missing completion time";
			}
			String windowError = this.requestSequenceWindow.validationError();
			if (windowError != null) {
				return windowError;
			}
			return this.removal.map(RemovalRecord::validationError).orElse(null);
		}
	}

	public enum FlowMemberStatus implements StringRepresentable {
		NOT_STARTED("not_started"),
		IN_PROGRESS("in_progress"),
		OFFLINE("offline"),
		ALREADY_COMPLETE("already_complete"),
		COMPLETED("completed"),
		REMOVED("removed"),
		BLOCKED("blocked");

		public static final Codec<FlowMemberStatus> CODEC = stableEnumCodec(values());
		private final String serializedName;

		FlowMemberStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record RequestSequenceWindow(long highestAcceptedSequence, long acceptedWindowBits) {
		public static final Codec<RequestSequenceWindow> CODEC = RecordCodecBuilder.<RequestSequenceWindow>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("highest_accepted_sequence").forGetter(RequestSequenceWindow::highestAcceptedSequence),
					Codec.LONG.fieldOf("accepted_window_bits").forGetter(RequestSequenceWindow::acceptedWindowBits)
				)
				.apply(instance, RequestSequenceWindow::new)
		).validate(RequestSequenceWindow::validated);

		public static RequestSequenceWindow empty() {
			return new RequestSequenceWindow(-1L, 0L);
		}

		private DataResult<RequestSequenceWindow> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.highestAcceptedSequence < -1L
				? "highest accepted request sequence cannot be below -1"
				: null;
		}
	}

	public record RemovalRecord(UUID removedBy, long removedAtEpochMillis, String reason) {
		public static final Codec<RemovalRecord> CODEC = RecordCodecBuilder.<RemovalRecord>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("removed_by").forGetter(RemovalRecord::removedBy),
					Codec.LONG.fieldOf("removed_at").forGetter(RemovalRecord::removedAtEpochMillis),
					Codec.sizeLimitedString(1_024).fieldOf("reason").forGetter(RemovalRecord::reason)
				)
				.apply(instance, RemovalRecord::new)
		).validate(RemovalRecord::validated);

		public RemovalRecord {
			Objects.requireNonNull(removedBy, "removedBy");
			Objects.requireNonNull(reason, "reason");
		}

		private DataResult<RemovalRecord> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.removedAtEpochMillis < 0L || this.reason.isBlank()
				? "invalid removal time or reason"
				: null;
		}
	}

	public record ExecutionSnapshot(
		long definitionGeneration,
		FrozenDocument flow,
		Map<Identifier, FrozenDocument> pages,
		Map<Identifier, FrozenDocument> themes,
		Map<Identifier, FrozenDocument> fields,
		List<FrozenSourceDocument> supportingDefinitions,
		Set<Identifier> predicateReferences,
		Map<Identifier, FrozenDocument> predicateDocuments,
		Set<Identifier> functionReferences,
		CallbackReferences callbackReferences,
		Set<String> textReferences,
		Set<Identifier> soundReferences,
		String confirmationConsequenceSummary,
		String contentSha256
	) {
		private static final Codec<Set<Identifier>> IDENTIFIER_SET_CODEC = Identifier.CODEC
			.sizeLimitedListOf(MAX_SNAPSHOT_DOCUMENTS)
			.xmap(WorldStateV2::immutableSortedSet, List::copyOf);
		private static final Codec<Set<String>> STRING_SET_CODEC = Codec.sizeLimitedString(1_024)
			.sizeLimitedListOf(MAX_SNAPSHOT_DOCUMENTS)
			.xmap(WorldStateV2::immutableSortedSet, List::copyOf);

		public static final Codec<ExecutionSnapshot> CODEC = RecordCodecBuilder.<ExecutionSnapshot>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("definition_generation").forGetter(ExecutionSnapshot::definitionGeneration),
					FrozenDocument.CODEC.fieldOf("flow").forGetter(ExecutionSnapshot::flow),
					Codec.unboundedMap(Identifier.CODEC, FrozenDocument.CODEC)
						.fieldOf("pages")
						.forGetter(ExecutionSnapshot::pages),
					Codec.unboundedMap(Identifier.CODEC, FrozenDocument.CODEC)
						.fieldOf("themes")
						.forGetter(ExecutionSnapshot::themes),
					Codec.unboundedMap(Identifier.CODEC, FrozenDocument.CODEC)
						.fieldOf("fields")
						.forGetter(ExecutionSnapshot::fields),
					FrozenSourceDocument.CODEC
						.sizeLimitedListOf(MAX_SNAPSHOT_DOCUMENTS)
						.fieldOf("supporting_definitions")
						.forGetter(ExecutionSnapshot::supportingDefinitions),
					IDENTIFIER_SET_CODEC
						.fieldOf("predicate_references")
						.forGetter(ExecutionSnapshot::predicateReferences),
					Codec.unboundedMap(Identifier.CODEC, FrozenDocument.CODEC)
						.optionalFieldOf("predicate_documents", Map.of())
						.forGetter(ExecutionSnapshot::predicateDocuments),
					IDENTIFIER_SET_CODEC
						.fieldOf("function_references")
						.forGetter(ExecutionSnapshot::functionReferences),
					CallbackReferences.CODEC
						.fieldOf("callback_references")
						.forGetter(ExecutionSnapshot::callbackReferences),
					STRING_SET_CODEC.fieldOf("text_references").forGetter(ExecutionSnapshot::textReferences),
					IDENTIFIER_SET_CODEC.fieldOf("sound_references").forGetter(ExecutionSnapshot::soundReferences),
					Codec.sizeLimitedString(4_096)
						.fieldOf("confirmation_consequence_summary")
						.forGetter(ExecutionSnapshot::confirmationConsequenceSummary),
					Codec.sizeLimitedString(64).fieldOf("content_sha256").forGetter(ExecutionSnapshot::contentSha256)
				)
				.apply(instance, ExecutionSnapshot::new)
		).validate(ExecutionSnapshot::validated);

		public ExecutionSnapshot {
			Objects.requireNonNull(flow, "flow");
			pages = immutableSortedMap(pages);
			themes = immutableSortedMap(themes);
			fields = immutableSortedMap(fields);
			supportingDefinitions = supportingDefinitions.stream()
				.sorted(
					Comparator.comparing((FrozenSourceDocument source) -> source.type.getSerializedName())
						.thenComparing(FrozenSourceDocument::id)
				)
				.toList();
			predicateReferences = immutableSortedSet(predicateReferences);
			predicateDocuments = immutableSortedMap(predicateDocuments);
			functionReferences = immutableSortedSet(functionReferences);
			Objects.requireNonNull(callbackReferences, "callbackReferences");
			textReferences = immutableSortedSet(textReferences);
			soundReferences = immutableSortedSet(soundReferences);
			Objects.requireNonNull(confirmationConsequenceSummary, "confirmationConsequenceSummary");
			Objects.requireNonNull(contentSha256, "contentSha256");
		}

		public ExecutionSnapshot withComputedContentSha256() {
			return new ExecutionSnapshot(
				this.definitionGeneration,
				this.flow,
				this.pages,
				this.themes,
				this.fields,
				this.supportingDefinitions,
				this.predicateReferences,
				this.predicateDocuments,
				this.functionReferences,
				this.callbackReferences,
				this.textReferences,
				this.soundReferences,
				this.confirmationConsequenceSummary,
				computeContentSha256()
			);
		}

		/**
		 * Retains only callback identity after a flow reaches a terminal state.
		 */
		public ExecutionSnapshot compactedForTerminalState() {
			Set<Identifier> callbackFunctions = new TreeSet<>();
			this.callbackReferences.onStart().ifPresent(callbackFunctions::add);
			this.callbackReferences.onPlayerComplete().ifPresent(callbackFunctions::add);
			this.callbackReferences.onAllComplete().ifPresent(callbackFunctions::add);
			return new ExecutionSnapshot(
				this.definitionGeneration,
				FrozenDocument.of("{\"archived\":true}"),
				Map.of(),
				Map.of(),
				Map.of(),
				List.of(),
				Set.of(),
				Map.of(),
				callbackFunctions,
				this.callbackReferences,
				Set.of(),
				Set.of(),
				"",
				"0".repeat(64)
			).withComputedContentSha256();
		}

		public String computeContentSha256() {
			MessageDigest digest = newSha256();
			updateDigest(digest, Long.toString(this.definitionGeneration));
			updateDocumentDigest(digest, "flow", null, this.flow);
			this.pages.forEach((id, document) -> updateDocumentDigest(digest, "page", id, document));
			this.themes.forEach((id, document) -> updateDocumentDigest(digest, "theme", id, document));
			this.fields.forEach((id, document) -> updateDocumentDigest(digest, "field", id, document));
			this.supportingDefinitions.forEach(source ->
				updateDocumentDigest(digest, source.type.getSerializedName(), source.id, source.document)
			);
			this.predicateReferences.forEach(id -> {
				updateDigest(digest, "predicate");
				updateDigest(digest, id.toString());
			});
			this.predicateDocuments.forEach(
				(id, document) -> updateDocumentDigest(digest, "predicate_document", id, document)
			);
			this.functionReferences.forEach(id -> {
				updateDigest(digest, "function");
				updateDigest(digest, id.toString());
			});
			updateOptionalIdentifierDigest(digest, "on_start", this.callbackReferences.onStart);
			updateOptionalIdentifierDigest(digest, "on_player_complete", this.callbackReferences.onPlayerComplete);
			updateOptionalIdentifierDigest(digest, "on_all_complete", this.callbackReferences.onAllComplete);
			this.textReferences.forEach(value -> {
				updateDigest(digest, "text");
				updateDigest(digest, value);
			});
			this.soundReferences.forEach(id -> {
				updateDigest(digest, "sound");
				updateDigest(digest, id.toString());
			});
			updateDigest(digest, "consequences");
			updateDigest(digest, this.confirmationConsequenceSummary);
			return HexFormat.of().formatHex(digest.digest());
		}

		private DataResult<ExecutionSnapshot> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.definitionGeneration < 0L) {
				return "snapshot generation cannot be negative";
			}
			int documentCount = 1
				+ this.pages.size()
				+ this.themes.size()
				+ this.fields.size()
				+ this.supportingDefinitions.size()
				+ this.predicateDocuments.size();
			if (documentCount > MAX_SNAPSHOT_DOCUMENTS) {
				return "snapshot document count exceeds " + MAX_SNAPSHOT_DOCUMENTS;
			}
			if (this.pages.size() > MAX_SNAPSHOT_PAGES) {
				return "snapshot page count exceeds " + MAX_SNAPSHOT_PAGES;
			}
			if (this.themes.size() > MAX_SNAPSHOT_THEMES) {
				return "snapshot theme count exceeds " + MAX_SNAPSHOT_THEMES;
			}
			if (
				this.predicateReferences.size() > MAX_SNAPSHOT_DOCUMENTS
					|| this.predicateDocuments.size() > MAX_SNAPSHOT_DOCUMENTS
					|| this.functionReferences.size() > MAX_SNAPSHOT_DOCUMENTS
					|| this.textReferences.size() > MAX_SNAPSHOT_DOCUMENTS
					|| this.soundReferences.size() > MAX_SNAPSHOT_DOCUMENTS
			) {
				return "snapshot reference count exceeds " + MAX_SNAPSHOT_DOCUMENTS;
			}
			if (this.textReferences.stream().anyMatch(value -> value.length() > 1_024)) {
				return "snapshot text reference exceeds 1024 characters";
			}
			if (!this.predicateReferences.containsAll(this.predicateDocuments.keySet())) {
				return "snapshot predicate document is not present in predicate references";
			}
			if (this.confirmationConsequenceSummary.length() > 4_096) {
				return "snapshot confirmation consequence summary exceeds 4096 characters";
			}
			List<FrozenDocument> documents = new ArrayList<>();
			documents.add(this.flow);
			documents.addAll(this.pages.values());
			documents.addAll(this.themes.values());
			documents.addAll(this.fields.values());
			documents.addAll(this.predicateDocuments.values());
			documents.addAll(this.supportingDefinitions.stream().map(FrozenSourceDocument::document).toList());
			TreeSet<String> sourceKeys = new TreeSet<>();
			int totalBytes = 0;
			for (FrozenSourceDocument source : this.supportingDefinitions) {
				String nested = source.validationError();
				if (nested != null) {
					return nested;
				}
				if (!sourceKeys.add(source.type.getSerializedName() + "/" + source.id)) {
					return "duplicate supporting definition " + source.type.getSerializedName() + "/" + source.id;
				}
				totalBytes = addUtf8Bytes(totalBytes, source.type.getSerializedName());
				totalBytes = addUtf8Bytes(totalBytes, source.id.toString());
			}
			for (FrozenDocument document : documents) {
				String nested = document.validationError();
				if (nested != null) {
					return nested;
				}
				totalBytes = addUtf8Bytes(totalBytes, document.normalizedJson);
			}
			for (Identifier id : this.pages.keySet()) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			for (Identifier id : this.themes.keySet()) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			for (Identifier id : this.fields.keySet()) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			for (Identifier id : this.predicateReferences) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			for (Identifier id : this.predicateDocuments.keySet()) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			for (Identifier id : this.functionReferences) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			for (Optional<Identifier> callback : List.of(
				this.callbackReferences.onStart,
				this.callbackReferences.onPlayerComplete,
				this.callbackReferences.onAllComplete
			)) {
				if (callback.isPresent()) {
					totalBytes = addUtf8Bytes(totalBytes, callback.orElseThrow().toString());
				}
			}
			for (String value : this.textReferences) {
				totalBytes = addUtf8Bytes(totalBytes, value);
			}
			for (Identifier id : this.soundReferences) {
				totalBytes = addUtf8Bytes(totalBytes, id.toString());
			}
			totalBytes = addUtf8Bytes(totalBytes, this.confirmationConsequenceSummary);
			if (totalBytes > MAX_SNAPSHOT_UTF8_BYTES) {
				return "snapshot content exceeds " + MAX_SNAPSHOT_UTF8_BYTES + " UTF-8 bytes";
			}
			String callbackError = this.callbackReferences.validationError();
			if (callbackError != null) {
				return callbackError;
			}
			if (
				List.of(
					this.callbackReferences.onStart,
					this.callbackReferences.onPlayerComplete,
					this.callbackReferences.onAllComplete
				).stream().flatMap(Optional::stream).anyMatch(id -> !this.functionReferences.contains(id))
			) {
				return "snapshot callback reference is missing from function references";
			}
			return validSha256(this.contentSha256) && this.contentSha256.equals(computeContentSha256())
				? null
				: "invalid or mismatched snapshot SHA-256";
		}
	}

	public record FrozenSourceDocument(
		FrozenDefinitionType type,
		Identifier id,
		FrozenDocument document
	) {
		public static final Codec<FrozenSourceDocument> CODEC = RecordCodecBuilder.<FrozenSourceDocument>create(
			instance -> instance.group(
					FrozenDefinitionType.CODEC.fieldOf("type").forGetter(FrozenSourceDocument::type),
					Identifier.CODEC.fieldOf("id").forGetter(FrozenSourceDocument::id),
					FrozenDocument.CODEC.fieldOf("document").forGetter(FrozenSourceDocument::document)
				)
				.apply(instance, FrozenSourceDocument::new)
		).validate(FrozenSourceDocument::validated);

		public FrozenSourceDocument {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(document, "document");
		}

		private DataResult<FrozenSourceDocument> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.document.validationError();
		}
	}

	public enum FrozenDefinitionType implements StringRepresentable {
		GAME("game"),
		ROLE("role"),
		TEAM("team"),
		LIFE_STATE("life_state"),
		PHASE("phase"),
		PANEL_ACTION("panel_action");

		public static final Codec<FrozenDefinitionType> CODEC = stableEnumCodec(values());
		private final String serializedName;

		FrozenDefinitionType(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record FrozenDocument(String normalizedJson, String sha256) {
		private static final Codec<List<String>> CHUNKS_CODEC = Codec
			.sizeLimitedString(MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS)
			.sizeLimitedListOf(MAX_FROZEN_DOCUMENT_CHUNKS);

		public static final Codec<FrozenDocument> CODEC = RecordCodecBuilder.<FrozenDocument>create(
			instance -> instance.group(
					Codec.sizeLimitedString(MAX_SNAPSHOT_UTF8_BYTES)
						.optionalFieldOf("normalized_json")
						.forGetter(ignored -> Optional.empty()),
					CHUNKS_CODEC
						.optionalFieldOf("normalized_json_chunks")
						.forGetter(document -> Optional.of(chunks(document.normalizedJson))),
					Codec.sizeLimitedString(64).fieldOf("sha256").forGetter(FrozenDocument::sha256)
				)
				.apply(instance, FrozenDocument::decode)
		).validate(FrozenDocument::storageValidated);

		public FrozenDocument {
			Objects.requireNonNull(normalizedJson, "normalizedJson");
			Objects.requireNonNull(sha256, "sha256");
		}

		public static FrozenDocument of(final String normalizedJson) {
			return new FrozenDocument(normalizedJson, sha256Utf8(normalizedJson));
		}

		/**
		 * Identifies the exact fail-safe representation written by Minecraft when an old,
		 * oversized single-string snapshot exceeded the NBT modified-UTF limit. The retained
		 * digest lets startup recover only from a freshly compiled byte-for-byte match.
		 */
		public boolean hasMissingContent() {
			return this.normalizedJson.isEmpty()
				&& validSha256(this.sha256)
				&& !this.sha256.equals(sha256Utf8(""));
		}

		private static FrozenDocument decode(
			final Optional<String> legacy,
			final Optional<List<String>> chunks,
			final String sha256
		) {
			if (legacy.isPresent() == chunks.isPresent()) {
				return new FrozenDocument("", "");
			}
			if (chunks.isPresent()) {
				List<String> values = chunks.orElseThrow();
				if (values.isEmpty() || values.stream().anyMatch(String::isEmpty)) {
					return new FrozenDocument("", "");
				}
				StringBuilder joined = new StringBuilder();
				values.forEach(joined::append);
				return new FrozenDocument(joined.toString(), sha256);
			}
			return new FrozenDocument(legacy.orElseThrow(), sha256);
		}

		private static List<String> chunks(final String value) {
			if (value.isEmpty()) {
				return List.of("");
			}
			List<String> chunks = new ArrayList<>(
				(value.length() + MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS - 1)
					/ MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS
			);
			for (int start = 0; start < value.length(); start += MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS) {
				chunks.add(
					value.substring(
						start,
						Math.min(value.length(), start + MAX_FROZEN_DOCUMENT_CHUNK_CHARACTERS)
					)
				);
			}
			return List.copyOf(chunks);
		}

		private DataResult<FrozenDocument> storageValidated() {
			if (hasMissingContent()) {
				return DataResult.success(this);
			}
			return validated();
		}

		private DataResult<FrozenDocument> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.normalizedJson.isBlank()
				|| !validSha256(this.sha256)
				|| !this.sha256.equals(sha256Utf8(this.normalizedJson))
				? "frozen document is blank or has an invalid or mismatched SHA-256"
				: null;
		}
	}

	public record CallbackReferences(
		Optional<Identifier> onStart,
		Optional<Identifier> onPlayerComplete,
		Optional<Identifier> onAllComplete
	) {
		public static final Codec<CallbackReferences> CODEC = RecordCodecBuilder.<CallbackReferences>create(
			instance -> instance.group(
					Identifier.CODEC.optionalFieldOf("on_start").forGetter(CallbackReferences::onStart),
					Identifier.CODEC
						.optionalFieldOf("on_player_complete")
						.forGetter(CallbackReferences::onPlayerComplete),
					Identifier.CODEC.optionalFieldOf("on_all_complete").forGetter(CallbackReferences::onAllComplete)
				)
				.apply(instance, CallbackReferences::new)
		);

		public CallbackReferences {
			onStart = requiredOptional(onStart, "onStart");
			onPlayerComplete = requiredOptional(onPlayerComplete, "onPlayerComplete");
			onAllComplete = requiredOptional(onAllComplete, "onAllComplete");
		}

		private String validationError() {
			return null;
		}
	}

	public record CallbackState(
		boolean onStartInvoked,
		Set<UUID> onPlayerCompleteInvoked,
		boolean onAllCompleteInvoked,
		List<CallbackFailure> failures
	) {
		private static final Codec<Set<UUID>> UUID_SET_CODEC = UUIDUtil.STRING_CODEC
			.sizeLimitedListOf(MAX_INSTANCE_MEMBERS)
			.xmap(WorldStateV2::immutableSortedSet, List::copyOf);

		public static final Codec<CallbackState> CODEC = RecordCodecBuilder.<CallbackState>create(
			instance -> instance.group(
					Codec.BOOL.fieldOf("on_start_invoked").forGetter(CallbackState::onStartInvoked),
					UUID_SET_CODEC
						.fieldOf("on_player_complete_invoked")
						.forGetter(CallbackState::onPlayerCompleteInvoked),
					Codec.BOOL.fieldOf("on_all_complete_invoked").forGetter(CallbackState::onAllCompleteInvoked),
					CallbackFailure.CODEC.sizeLimitedListOf(128).fieldOf("failures").forGetter(CallbackState::failures)
				)
				.apply(instance, CallbackState::new)
		).validate(CallbackState::validated);

		public CallbackState {
			onPlayerCompleteInvoked = immutableSortedSet(onPlayerCompleteInvoked);
			failures = List.copyOf(failures);
		}

		public static CallbackState initial() {
			return new CallbackState(false, Set.of(), false, List.of());
		}

		private DataResult<CallbackState> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.onPlayerCompleteInvoked.size() > MAX_INSTANCE_MEMBERS) {
				return "player callback marker count exceeds " + MAX_INSTANCE_MEMBERS;
			}
			for (CallbackFailure failure : this.failures) {
				String nested = failure.validationError();
				if (nested != null) {
					return nested;
				}
			}
			return null;
		}
	}

	public record CallbackFailure(
		CallbackKind kind,
		Identifier functionId,
		Optional<UUID> playerId,
		String error,
		long failedAtEpochMillis,
		int attemptCount
	) {
		public static final Codec<CallbackFailure> CODEC = RecordCodecBuilder.<CallbackFailure>create(
			instance -> instance.group(
					CallbackKind.CODEC.fieldOf("kind").forGetter(CallbackFailure::kind),
					Identifier.CODEC.fieldOf("function_id").forGetter(CallbackFailure::functionId),
					UUIDUtil.STRING_CODEC.optionalFieldOf("player_id").forGetter(CallbackFailure::playerId),
					Codec.sizeLimitedString(4_096).fieldOf("error").forGetter(CallbackFailure::error),
					Codec.LONG.fieldOf("failed_at").forGetter(CallbackFailure::failedAtEpochMillis),
					Codec.INT.fieldOf("attempt_count").forGetter(CallbackFailure::attemptCount)
				)
				.apply(instance, CallbackFailure::new)
		).validate(CallbackFailure::validated);

		public CallbackFailure {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(functionId, "functionId");
			playerId = requiredOptional(playerId, "playerId");
			Objects.requireNonNull(error, "error");
		}

		private DataResult<CallbackFailure> validated() {
			String nested = validationError();
			return nested == null ? DataResult.success(this) : DataResult.error(() -> nested);
		}

		private String validationError() {
			if (this.error.isBlank() || this.failedAtEpochMillis < 0L || this.attemptCount <= 0) {
				return "invalid callback failure";
			}
			return this.kind == CallbackKind.ON_PLAYER_COMPLETE && this.playerId.isEmpty()
				? "player-complete callback failure is missing player"
				: null;
		}
	}

	public enum CallbackKind implements StringRepresentable {
		ON_START("on_start"),
		ON_PLAYER_COMPLETE("on_player_complete"),
		ON_ALL_COMPLETE("on_all_complete");

		public static final Codec<CallbackKind> CODEC = stableEnumCodec(values());
		private final String serializedName;

		CallbackKind(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record BlockDiagnostic(
		String code,
		String message,
		long blockedAtEpochMillis,
		Optional<UUID> playerId,
		Optional<Identifier> functionId
	) {
		public static final Codec<BlockDiagnostic> CODEC = RecordCodecBuilder.<BlockDiagnostic>create(
			instance -> instance.group(
					Codec.sizeLimitedString(64).fieldOf("code").forGetter(BlockDiagnostic::code),
					Codec.sizeLimitedString(4_096).fieldOf("message").forGetter(BlockDiagnostic::message),
					Codec.LONG.fieldOf("blocked_at").forGetter(BlockDiagnostic::blockedAtEpochMillis),
					UUIDUtil.STRING_CODEC.optionalFieldOf("player_id").forGetter(BlockDiagnostic::playerId),
					Identifier.CODEC.optionalFieldOf("function_id").forGetter(BlockDiagnostic::functionId)
				)
				.apply(instance, BlockDiagnostic::new)
		).validate(BlockDiagnostic::validated);

		public BlockDiagnostic {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			playerId = requiredOptional(playerId, "playerId");
			functionId = requiredOptional(functionId, "functionId");
		}

		private DataResult<BlockDiagnostic> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.code.isBlank() || this.message.isBlank() || this.blockedAtEpochMillis < 0L
				? "invalid block diagnostic"
				: null;
		}
	}

	public record CompletionRecord(
		UUID playerId,
		Identifier flowId,
		int flowVersion,
		UUID instanceId,
		long completedAtEpochMillis,
		Identifier sourceActionId,
		boolean forcedRedo,
		long definitionGeneration
	) {
		public static final Codec<CompletionRecord> CODEC = RecordCodecBuilder.<CompletionRecord>create(
			instance -> instance.group(
					UUIDUtil.STRING_CODEC.fieldOf("player_id").forGetter(CompletionRecord::playerId),
					Identifier.CODEC.fieldOf("flow_id").forGetter(CompletionRecord::flowId),
					Codec.INT.fieldOf("flow_version").forGetter(CompletionRecord::flowVersion),
					UUIDUtil.STRING_CODEC.fieldOf("instance_id").forGetter(CompletionRecord::instanceId),
					Codec.LONG.fieldOf("completed_at").forGetter(CompletionRecord::completedAtEpochMillis),
					Identifier.CODEC.fieldOf("source_action_id").forGetter(CompletionRecord::sourceActionId),
					Codec.BOOL.fieldOf("forced_redo").forGetter(CompletionRecord::forcedRedo),
					Codec.LONG.fieldOf("definition_generation").forGetter(CompletionRecord::definitionGeneration)
				)
				.apply(instance, CompletionRecord::new)
		).validate(CompletionRecord::validated);

		public CompletionRecord {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(flowId, "flowId");
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(sourceActionId, "sourceActionId");
		}

		private DataResult<CompletionRecord> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.flowVersion <= 0 || this.completedAtEpochMillis < 0L || this.definitionGeneration < 0L
				? "invalid completion record"
				: null;
		}
	}

	public record AuditLog(long totalEvents, List<AuditEvent> recentEvents) {
		public static final Codec<AuditLog> CODEC = RecordCodecBuilder.<AuditLog>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("total_events").forGetter(AuditLog::totalEvents),
					AuditEvent.CODEC
						.sizeLimitedListOf(MAX_AUDIT_DETAILS)
						.fieldOf("recent_events")
						.forGetter(AuditLog::recentEvents)
				)
				.apply(instance, AuditLog::new)
		).validate(AuditLog::validated);

		public AuditLog {
			recentEvents = List.copyOf(recentEvents);
		}

		public static AuditLog empty() {
			return new AuditLog(0L, List.of());
		}

		public AuditLog append(final AuditEvent event) {
			Objects.requireNonNull(event, "event");
			List<AuditEvent> next = new ArrayList<>(this.recentEvents.size() + 1);
			next.addAll(this.recentEvents);
			next.add(event);
			if (next.size() > MAX_AUDIT_DETAILS) {
				next = new ArrayList<>(next.subList(next.size() - MAX_AUDIT_DETAILS, next.size()));
			}
			return new AuditLog(Math.incrementExact(this.totalEvents), next);
		}

		private DataResult<AuditLog> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.totalEvents < this.recentEvents.size() || this.recentEvents.size() > MAX_AUDIT_DETAILS) {
				return "audit total/detail count is invalid";
			}
			long previousSequence = -1L;
			for (AuditEvent event : this.recentEvents) {
				String nested = event.validationError();
				if (nested != null) {
					return nested;
				}
				if (event.sequence <= previousSequence) {
					return "audit event sequence must be strictly increasing";
				}
				previousSequence = event.sequence;
			}
			return null;
		}
	}

	public record AuditEvent(
		long sequence,
		AuditEventType type,
		long occurredAtEpochMillis,
		Optional<UUID> actorId,
		Optional<UUID> targetPlayerId,
		Optional<UUID> flowInstanceId,
		String summary
	) {
		public static final Codec<AuditEvent> CODEC = RecordCodecBuilder.<AuditEvent>create(
			instance -> instance.group(
					Codec.LONG.fieldOf("sequence").forGetter(AuditEvent::sequence),
					AuditEventType.CODEC.fieldOf("type").forGetter(AuditEvent::type),
					Codec.LONG.fieldOf("occurred_at").forGetter(AuditEvent::occurredAtEpochMillis),
					UUIDUtil.STRING_CODEC.optionalFieldOf("actor_id").forGetter(AuditEvent::actorId),
					UUIDUtil.STRING_CODEC.optionalFieldOf("target_player_id").forGetter(AuditEvent::targetPlayerId),
					UUIDUtil.STRING_CODEC.optionalFieldOf("flow_instance_id").forGetter(AuditEvent::flowInstanceId),
					Codec.sizeLimitedString(1_024).fieldOf("summary").forGetter(AuditEvent::summary)
				)
				.apply(instance, AuditEvent::new)
		).validate(AuditEvent::validated);

		public AuditEvent {
			Objects.requireNonNull(type, "type");
			actorId = requiredOptional(actorId, "actorId");
			targetPlayerId = requiredOptional(targetPlayerId, "targetPlayerId");
			flowInstanceId = requiredOptional(flowInstanceId, "flowInstanceId");
			Objects.requireNonNull(summary, "summary");
		}

		private DataResult<AuditEvent> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			return this.sequence < 0L || this.occurredAtEpochMillis < 0L || this.summary.isBlank()
				? "invalid audit event"
				: null;
		}
	}

	public enum AuditEventType implements StringRepresentable {
		HOST_CLAIMED("host_claimed"),
		HOST_TRANSFERRED("host_transferred"),
		HOST_TAKEN_OVER("host_taken_over"),
		CONFIRMATION_ISSUED("confirmation_issued"),
		CONFIRMATION_CANCELED("confirmation_canceled"),
		CONFIRMATION_EXPIRED("confirmation_expired"),
		CONFIRMATION_REJECTED("confirmation_rejected"),
		CONFIRMATION_CONSUMED("confirmation_consumed"),
		FLOW_CREATED("flow_created"),
		FLOW_BLOCKED("flow_blocked"),
		FLOW_COMPLETED("flow_completed"),
		FLOW_CANCELED("flow_canceled"),
		MEMBER_ADDED("member_added"),
		MEMBER_REMOVED("member_removed"),
		MEMBER_COMPLETED("member_completed"),
		READINESS_OPENED("readiness_opened"),
		READINESS_INVALIDATED("readiness_invalidated"),
		READINESS_COMPLETED("readiness_completed"),
		ROLE_CHANGE_CREATED("role_change_created"),
		ROLE_CHANGE_APPLIED("role_change_applied"),
		ROLE_CHANGE_CANCELED("role_change_canceled"),
		ROLE_CHANGE_BLOCKED("role_change_blocked"),
		CALLBACK_FAILED("callback_failed"),
		CALLBACK_RETRIED("callback_retried"),
		PHASE_TRANSITIONED("phase_transitioned"),
		TIMELINE_CONTROL("timeline_control"),
		SCHEMA_MIGRATED("schema_migrated"),
		RECOVERY_CREATED("recovery_created"),
		REQUEST_REJECTED("request_rejected");

		public static final Codec<AuditEventType> CODEC = stableEnumCodec(values());
		private final String serializedName;

		AuditEventType(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public record RecoverySnapshotMetadata(
		String fileName,
		long createdAtEpochMillis,
		int sourceSchemaVersion,
		long byteSize,
		String sha256
	) {
		public static final Codec<RecoverySnapshotMetadata> CODEC = RecordCodecBuilder.<RecoverySnapshotMetadata>create(
			instance -> instance.group(
					Codec.sizeLimitedString(255).fieldOf("file_name").forGetter(RecoverySnapshotMetadata::fileName),
					Codec.LONG.fieldOf("created_at").forGetter(RecoverySnapshotMetadata::createdAtEpochMillis),
					Codec.INT.fieldOf("source_schema_version").forGetter(RecoverySnapshotMetadata::sourceSchemaVersion),
					Codec.LONG.fieldOf("byte_size").forGetter(RecoverySnapshotMetadata::byteSize),
					Codec.sizeLimitedString(64).fieldOf("sha256").forGetter(RecoverySnapshotMetadata::sha256)
				)
				.apply(instance, RecoverySnapshotMetadata::new)
		).validate(RecoverySnapshotMetadata::validated);

		public RecoverySnapshotMetadata {
			Objects.requireNonNull(fileName, "fileName");
			Objects.requireNonNull(sha256, "sha256");
		}

		private DataResult<RecoverySnapshotMetadata> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (
				this.fileName.isBlank()
					|| !this.fileName.equals(java.nio.file.Path.of(this.fileName).getFileName().toString())
					|| this.createdAtEpochMillis < 0L
					|| this.sourceSchemaVersion <= 0
					|| this.byteSize < 0L
					|| !validSha256(this.sha256)
			) {
				return "invalid recovery snapshot metadata";
			}
			return null;
		}
	}

	private static boolean validSha256(final String value) {
		return value.length() == 64 && value.chars().allMatch(character -> {
			char lower = Character.toLowerCase((char)character);
			return lower >= '0' && lower <= '9' || lower >= 'a' && lower <= 'f';
		});
	}

	private static String sha256Utf8(final String value) {
		MessageDigest digest = newSha256();
		digest.update(value.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest.digest());
	}

	private static MessageDigest newSha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("JVM does not provide SHA-256", error);
		}
	}

	private static void updateDocumentDigest(
		final MessageDigest digest,
		final String type,
		final Identifier id,
		final FrozenDocument document
	) {
		updateDigest(digest, type);
		updateDigest(digest, id == null ? "" : id.toString());
		updateDigest(digest, document.normalizedJson);
	}

	private static void updateOptionalIdentifierDigest(
		final MessageDigest digest,
		final String name,
		final Optional<Identifier> value
	) {
		updateDigest(digest, name);
		updateDigest(digest, value.map(Identifier::toString).orElse(""));
	}

	private static void updateDigest(final MessageDigest digest, final String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}

	private static int addUtf8Bytes(final int total, final String value) {
		return Math.addExact(total, value.getBytes(StandardCharsets.UTF_8).length);
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

	private static <K extends Comparable<? super K>, V> Map<K, V> immutableSortedMap(final Map<K, V> values) {
		Objects.requireNonNull(values, "values");
		TreeMap<K, V> sorted = new TreeMap<>();
		values.forEach((key, value) -> sorted.put(Objects.requireNonNull(key, "map key"), Objects.requireNonNull(value, "map value")));
		return Collections.unmodifiableMap(sorted);
	}

	private static <E extends Comparable<? super E>> Set<E> immutableSortedSet(final java.util.Collection<E> values) {
		Objects.requireNonNull(values, "values");
		TreeSet<E> sorted = new TreeSet<>();
		values.forEach(value -> sorted.add(Objects.requireNonNull(value, "set value")));
		return Collections.unmodifiableSet(sorted);
	}

	private static <T> DataResult<Map<UUID, T>> indexByUuid(
		final List<T> records,
		final Function<T, UUID> id,
		final String label
	) {
		TreeMap<UUID, T> indexed = new TreeMap<>();
		for (T record : records) {
			UUID key = id.apply(record);
			if (indexed.putIfAbsent(key, record) != null) {
				return DataResult.error(() -> "duplicate " + label + " UUID " + key);
			}
		}
		return DataResult.success(Collections.unmodifiableMap(indexed));
	}

	private static <T> Optional<T> requiredOptional(final Optional<T> value, final String name) {
		return Objects.requireNonNull(value, name);
	}
}
