package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.OptionalDynamic;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The single world-level source of truth owned by the server mod.
 *
 * <p>The wrapper keeps its object identity for the lifetime of the loaded world. Successful changes
 * replace one immutable {@link WorldStateV3} payload and mark this wrapper dirty; this prevents
 * partial state mutations and keeps the unreadable-file guard from mistaking a legitimate
 * replacement object for a failed load. Existing 2C callers transact through the schema-v2 core
 * adapter.
 */
public final class PixelTzzWorldState extends SavedData {
	public static final int CURRENT_SCHEMA_VERSION = WorldStateV3.SCHEMA_VERSION;
	public static final Codec<PixelTzzWorldState> CODEC = Codec.PASSTHROUGH.flatXmap(
		PixelTzzWorldState::decode,
		PixelTzzWorldState::encode
	);
	public static final SavedDataType<PixelTzzWorldState> TYPE = new SavedDataType<>(
		PixelTzzPro.id("world_state"),
		PixelTzzWorldState::initial,
		CODEC,
		null
	);

	private LoadKind loadKind;
	private WorldStateV3 currentV3;
	private WorldStateV2 legacyV2;
	private LegacyWorldStateV1 legacyV1;
	private Dynamic<?> preservedData;
	private boolean loadGuardChecked;
	private boolean loadFailureProtected;
	private String incompatibilityReason;

	private PixelTzzWorldState(
		final LoadKind loadKind,
		final WorldStateV3 currentV3,
		final WorldStateV2 legacyV2,
		final LegacyWorldStateV1 legacyV1,
		final Dynamic<?> preservedData,
		final boolean loadFailureProtected,
		final String incompatibilityReason
	) {
		this.loadKind = Objects.requireNonNull(loadKind, "loadKind");
		this.currentV3 = currentV3;
		this.legacyV2 = legacyV2;
		this.legacyV1 = legacyV1;
		this.preservedData = preservedData;
		this.loadFailureProtected = loadFailureProtected;
		this.incompatibilityReason = Objects.requireNonNull(incompatibilityReason, "incompatibilityReason");
		requirePayloadInvariant();
	}

	public static PixelTzzWorldState initial() {
		return current(WorldStateV3.initial());
	}

	public static PixelTzzWorldState get(final MinecraftServer server) {
		Path stateFile = stateFile(server);
		boolean stateFileExisted = Files.exists(stateFile);
		PixelTzzWorldState state = server.getDataStorage().computeIfAbsent(TYPE);
		if (!state.loadGuardChecked) {
			state.loadGuardChecked = true;
			if (stateFileExisted && state.isDirty()) {
				protectUnreadableStateFile(state, stateFile);
			}
		}
		return state;
	}

	static Path stateFile(final MinecraftServer server) {
		Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("data");
		return TYPE.id().withSuffix(".dat").resolveAgainst(dataDirectory);
	}

	private static DataResult<PixelTzzWorldState> decode(final Dynamic<?> data) {
		int schemaVersion = data.get("schema_version").asInt(0);
		if (schemaVersion == WorldStateV3.SCHEMA_VERSION) {
			ScopeRepair repair = addMissingGameInstanceScope(data);
			DataResult<WorldStateV3> decoded = WorldStateV3.CODEC.parse(repair.data());
			return decoded.result()
				.map(value -> {
					PixelTzzWorldState state = value.timeline()
						.filter(timeline -> timeline.snapshot().hasMissingContent())
						.map(ignored -> repairableCurrent(
							value,
							"时间线冻结快照内容在旧版 NBT 保存时丢失，正在等待按 SHA-256 安全重建"
						))
						.orElseGet(() -> current(value));
					if (repair.repaired()) {
						state.setDirty();
					}
					return DataResult.success(state);
				})
				.orElseGet(() -> DataResult.success(
					opaque(
						schemaVersion,
						data,
						decoded.error().map(DataResult.Error::message).orElse("invalid schema v3 world state")
					)
				));
		}
		if (schemaVersion == WorldStateV2.SCHEMA_VERSION) {
			DataResult<WorldStateV2> decoded = WorldStateV2.CODEC.parse(data);
			return decoded.result()
				.map(value -> DataResult.success(legacy(value, data)))
				.orElseGet(() -> DataResult.success(
					opaque(
						schemaVersion,
						data,
						decoded.error().map(DataResult.Error::message).orElse("invalid schema v2 world state")
					)
				));
		}
		if (schemaVersion == LegacyWorldStateV1.SCHEMA_VERSION) {
			DataResult<LegacyWorldStateV1> decoded = LegacyWorldStateV1.CODEC.parse(data);
			return decoded.result()
				.map(value -> DataResult.success(legacy(value, data)))
				.orElseGet(() -> DataResult.success(
					opaque(
						schemaVersion,
						data,
						decoded.error().map(DataResult.Error::message).orElse("invalid schema v1 world state")
					)
				));
		}
		return DataResult.success(opaque(schemaVersion, data, "unsupported schema " + schemaVersion));
	}

	/**
	 * One-time compatibility repair for development schema-v3 saves written before the persistent
	 * game-instance scope was added.
	 */
	private static ScopeRepair addMissingGameInstanceScope(final Dynamic<?> source) {
		if (
			source.get("active_game_instance_id").result().isPresent()
				|| source.get("core").get("active_game_id").result().isEmpty()
		) {
			return new ScopeRepair(source, false);
		}
		return new ScopeRepair(
			source.set(
				"active_game_instance_id",
				source.createString(recoverGameInstanceScope(source).toString())
			),
			true
		);
	}

	private static UUID recoverGameInstanceScope(final Dynamic<?> root) {
		UUID timelineId = uuid(root.get("timeline").get("instance_id"));
		if (timelineId != null) {
			return timelineId;
		}
		for (Dynamic<?> reservation : root.get("exclusive_reservations").asStream().toList()) {
			UUID scope = uuid(reservation.get("scope_id"));
			if (scope != null) {
				return scope;
			}
		}
		return UUID.randomUUID();
	}

	private static UUID uuid(final OptionalDynamic<?> element) {
		String value = element.asString().result().orElse(null);
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	private record ScopeRepair(
		Dynamic<?> data,
		boolean repaired
	) {
		private ScopeRepair {
			Objects.requireNonNull(data, "data");
		}
	}

	private static PixelTzzWorldState current(final WorldStateV3 value) {
		return new PixelTzzWorldState(LoadKind.CURRENT_V3, value, null, null, null, false, "");
	}

	private static PixelTzzWorldState repairableCurrent(
		final WorldStateV3 value,
		final String reason
	) {
		return new PixelTzzWorldState(
			LoadKind.CURRENT_V3,
			value,
			null,
			null,
			null,
			true,
			reason
		);
	}

	private static PixelTzzWorldState legacy(final WorldStateV2 value, final Dynamic<?> preservedData) {
		return new PixelTzzWorldState(
			LoadKind.LEGACY_V2,
			null,
			value,
			null,
			preservedData,
			false,
			"schema v2 requires an additive startup migration"
		);
	}

	private static PixelTzzWorldState legacy(final LegacyWorldStateV1 value, final Dynamic<?> preservedData) {
		return new PixelTzzWorldState(
			LoadKind.LEGACY_V1,
			null,
			null,
			value,
			preservedData,
			false,
			"schema v1 requires a conservative startup migration"
		);
	}

	private static PixelTzzWorldState opaque(
		final int schemaVersion,
		final Dynamic<?> data,
		final String reason
	) {
		return new PixelTzzWorldState(
			LoadKind.OPAQUE,
			null,
			null,
			null,
			data,
			false,
			(schemaVersion == 0 ? "missing or invalid schema; " : "") + reason
		);
	}

	private static DataResult<Dynamic<?>> encode(final PixelTzzWorldState state) {
		if (state.currentV3 != null) {
			return WorldStateV3.CODEC.encodeStart(JsonOps.INSTANCE, state.currentV3)
				.map(value -> new Dynamic<>(JsonOps.INSTANCE, value));
		}
		if (state.preservedData != null) {
			return DataResult.success(state.preservedData);
		}
		return DataResult.error(() -> "Pixel TZZ world-state payload is missing");
	}

	private static void protectUnreadableStateFile(
		final PixelTzzWorldState state,
		final Path stateFile
	) {
		String reason = "saved data could not be read; original file is protected from overwrite";
		try {
			Path backupDirectory = stateFile.getParent().resolve("recovery");
			Files.createDirectories(backupDirectory);
			Path backupFile = backupDirectory.resolve(
				"world_state-load-failed-" + System.currentTimeMillis() + ".dat"
			);
			Files.copy(stateFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
			reason += "; recovery copy: " + backupFile.getFileName();
			PixelTzzPro.LOGGER.error(
				"Pixel TZZ world state could not be read. Original left untouched; recovery copy written to {}",
				backupFile
			);
		} catch (IOException | RuntimeException error) {
			reason += "; recovery copy failed: " + error.getMessage();
			PixelTzzPro.LOGGER.error(
				"Pixel TZZ world state could not be read and its recovery copy failed. Original remains untouched at {}",
				stateFile,
				error
			);
		}

		state.loadFailureProtected = true;
		state.incompatibilityReason = reason;
		state.setDirty(false);
	}

	public synchronized CommitResult commit(
		final long expectedRevision,
		final UnaryOperator<WorldStateV2> mutation
	) {
		Objects.requireNonNull(mutation, "mutation");
		if (!isSchemaCompatible()) {
			return CommitResult.rejected("world state is not writable: " + this.incompatibilityReason);
		}
		if (this.currentV3.stateRevision() != expectedRevision) {
			return CommitResult.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV3.stateRevision()
			);
		}

		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(expectedRevision);
		} catch (ArithmeticException error) {
			return CommitResult.rejected("state revision overflow");
		}

		final WorldStateV2 candidate;
		try {
			candidate = Objects.requireNonNull(mutation.apply(this.currentV3.core()), "mutation result")
				.withStateRevision(nextRevision);
		} catch (RuntimeException error) {
			return CommitResult.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV2> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitResult.rejected(validated.error().orElseThrow().message());
		}

		WorldStateV3 next = this.currentV3.withCore(candidate);
		DataResult<WorldStateV3> rootValidation = next.validated();
		if (rootValidation.error().isPresent()) {
			return CommitResult.rejected(rootValidation.error().orElseThrow().message());
		}
		this.currentV3 = next;
		this.setDirty();
		return CommitResult.committed(candidate);
	}

	/**
	 * Native schema-v3 transaction used by 2D authorities.
	 */
	public synchronized CommitV3Result commitV3(
		final long expectedRevision,
		final UnaryOperator<WorldStateV3> mutation
	) {
		Objects.requireNonNull(mutation, "mutation");
		if (!isSchemaCompatible()) {
			return CommitV3Result.rejected("world state is not writable: " + this.incompatibilityReason);
		}
		if (this.currentV3.stateRevision() != expectedRevision) {
			return CommitV3Result.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV3.stateRevision()
			);
		}
		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(expectedRevision);
		} catch (ArithmeticException error) {
			return CommitV3Result.rejected("state revision overflow");
		}
		final WorldStateV3 candidate;
		try {
			WorldStateV3 mutated = Objects.requireNonNull(mutation.apply(this.currentV3), "mutation result");
			candidate = mutated.withCore(mutated.core().withStateRevision(nextRevision));
		} catch (RuntimeException error) {
			return CommitV3Result.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV3> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitV3Result.rejected(validated.error().orElseThrow().message());
		}
		this.currentV3 = candidate;
		this.setDirty();
		return CommitV3Result.committed(candidate);
	}

	/**
	 * Persists a routine clock checkpoint without invalidating global state-revision consumers.
	 *
	 * <p>Lifecycle changes still belong in {@link #commitV3(long, UnaryOperator)}. This narrower
	 * transaction preserves both lifecycle revisions and cannot replace the active timeline
	 * instance.
	 */
	public synchronized TimelineCheckpointResult timelineCheckpoint(
		final long expectedTimelineRevision,
		final UnaryOperator<WorldStateV3.TimelineInstance> mutation
	) {
		Objects.requireNonNull(mutation, "mutation");
		if (!isSchemaCompatible()) {
			return TimelineCheckpointResult.rejected(
				"world state is not writable: " + this.incompatibilityReason
			);
		}
		WorldStateV3.TimelineInstance current = this.currentV3.timeline().orElse(null);
		if (current == null) {
			return TimelineCheckpointResult.rejected("timeline is not active");
		}
		if (current.timelineRevision() != expectedTimelineRevision) {
			return TimelineCheckpointResult.rejected(
				"timeline revision mismatch: expected "
					+ expectedTimelineRevision
					+ ", current "
					+ current.timelineRevision()
			);
		}
		final WorldStateV3.TimelineInstance candidate;
		try {
			candidate = Objects.requireNonNull(
				mutation.apply(current),
				"mutation result"
			);
			if (!candidate.instanceId().equals(current.instanceId())) {
				return TimelineCheckpointResult.rejected(
					"timeline checkpoint cannot replace the active timeline instance"
				);
			}
			if (candidate.timelineRevision() != expectedTimelineRevision) {
				return TimelineCheckpointResult.rejected(
					"clock checkpoint cannot change the lifecycle timeline revision"
				);
			}
		} catch (RuntimeException error) {
			return TimelineCheckpointResult.rejected(
				"timeline mutation failed validation: " + safeMessage(error)
			);
		}
		WorldStateV3 next = this.currentV3.withTimeline(Optional.of(candidate));
		DataResult<WorldStateV3> validated = next.validated();
		if (validated.error().isPresent()) {
			return TimelineCheckpointResult.rejected(validated.error().orElseThrow().message());
		}
		this.currentV3 = next;
		this.setDirty();
		return TimelineCheckpointResult.committed(candidate);
	}

	/**
	 * Repairs only the historical Minecraft oversized-string fallback. The replacement must
	 * reproduce the retained SHA-256 exactly; a changed or incomplete data pack therefore leaves
	 * the world read-only instead of silently accepting different task logic.
	 */
	public synchronized TimelineSnapshotRepairResult repairMissingTimelineSnapshot(
		final FrozenDocument replacement
	) {
		Objects.requireNonNull(replacement, "replacement");
		if (
			this.loadKind != LoadKind.CURRENT_V3
				|| !this.loadFailureProtected
				|| this.currentV3 == null
				|| this.currentV3.timeline().isEmpty()
		) {
			return TimelineSnapshotRepairResult.rejected("world is not awaiting timeline snapshot repair");
		}
		TimelineInstance timeline = this.currentV3.timeline().orElseThrow();
		FrozenDocument missing = timeline.snapshot();
		if (!missing.hasMissingContent()) {
			return TimelineSnapshotRepairResult.rejected("timeline snapshot content is not missing");
		}
		if (
			replacement.hasMissingContent()
				|| replacement.normalizedJson().isBlank()
				|| !FrozenDocument.of(replacement.normalizedJson()).sha256().equals(replacement.sha256())
		) {
			return TimelineSnapshotRepairResult.rejected("replacement timeline snapshot is invalid");
		}
		if (!missing.sha256().equals(replacement.sha256())) {
			return TimelineSnapshotRepairResult.rejected(
				"replacement timeline snapshot SHA-256 does not match the protected save"
			);
		}
		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(this.currentV3.stateRevision());
		} catch (ArithmeticException error) {
			return TimelineSnapshotRepairResult.rejected("state revision overflow");
		}
		WorldStateV3 candidate = this.currentV3
			.withTimeline(Optional.of(timeline.withSnapshot(replacement)))
			.withCore(this.currentV3.core().withStateRevision(nextRevision));
		DataResult<WorldStateV3> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return TimelineSnapshotRepairResult.rejected(validated.error().orElseThrow().message());
		}
		this.currentV3 = candidate;
		this.loadFailureProtected = false;
		this.incompatibilityReason = "";
		this.setDirty();
		return TimelineSnapshotRepairResult.committed(candidate);
	}

	synchronized MigrationCommitResult commitLegacyV1Migration(
		final LegacyWorldStateV1 expectedLegacy,
		final WorldStateV3 candidate
	) {
		Objects.requireNonNull(expectedLegacy, "expectedLegacy");
		Objects.requireNonNull(candidate, "candidate");
		if (
			this.loadFailureProtected
				|| this.loadKind != LoadKind.LEGACY_V1
				|| !expectedLegacy.equals(this.legacyV1)
		) {
			return MigrationCommitResult.rejected("legacy state changed before migration commit");
		}
		DataResult<WorldStateV3> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return MigrationCommitResult.rejected(validated.error().orElseThrow().message());
		}
		long expectedRevision;
		try {
			expectedRevision = Math.incrementExact(expectedLegacy.stateRevision());
		} catch (ArithmeticException error) {
			return MigrationCommitResult.rejected("legacy state revision overflow");
		}
		if (candidate.stateRevision() != expectedRevision) {
			return MigrationCommitResult.rejected("migration candidate revision is not legacy revision + 1");
		}

		this.loadKind = LoadKind.CURRENT_V3;
		this.currentV3 = candidate;
		this.legacyV2 = null;
		this.legacyV1 = null;
		this.preservedData = null;
		this.incompatibilityReason = "";
		this.setDirty();
		requirePayloadInvariant();
		return MigrationCommitResult.committed(candidate);
	}

	synchronized MigrationCommitResult commitLegacyV2Migration(
		final WorldStateV2 expectedLegacy,
		final WorldStateV3 candidate
	) {
		Objects.requireNonNull(expectedLegacy, "expectedLegacy");
		Objects.requireNonNull(candidate, "candidate");
		if (
			this.loadFailureProtected
				|| this.loadKind != LoadKind.LEGACY_V2
				|| !expectedLegacy.equals(this.legacyV2)
		) {
			return MigrationCommitResult.rejected("legacy state changed before migration commit");
		}
		DataResult<WorldStateV3> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return MigrationCommitResult.rejected(validated.error().orElseThrow().message());
		}
		final long expectedRevision;
		try {
			expectedRevision = Math.incrementExact(expectedLegacy.stateRevision());
		} catch (ArithmeticException error) {
			return MigrationCommitResult.rejected("legacy state revision overflow");
		}
		if (candidate.stateRevision() != expectedRevision) {
			return MigrationCommitResult.rejected("migration candidate revision is not legacy revision + 1");
		}
		this.loadKind = LoadKind.CURRENT_V3;
		this.currentV3 = candidate;
		this.legacyV2 = null;
		this.legacyV1 = null;
		this.preservedData = null;
		this.incompatibilityReason = "";
		this.setDirty();
		requirePayloadInvariant();
		return MigrationCommitResult.committed(candidate);
	}

	synchronized void setMigrationDiagnostic(final String diagnostic) {
		if (
			(this.loadKind == LoadKind.LEGACY_V1 || this.loadKind == LoadKind.LEGACY_V2)
				&& !this.loadFailureProtected
		) {
			this.incompatibilityReason = Objects.requireNonNull(diagnostic, "diagnostic");
		}
	}

	public LoadKind loadKind() {
		return this.loadKind;
	}

	public int schemaVersion() {
		if (this.currentV3 != null) {
			return this.currentV3.schemaVersion();
		}
		if (this.legacyV2 != null) {
			return this.legacyV2.schemaVersion();
		}
		if (this.legacyV1 != null) {
			return this.legacyV1.schemaVersion();
		}
		return this.preservedData == null ? 0 : this.preservedData.get("schema_version").asInt(0);
	}

	public Optional<WorldStateV3> currentV3() {
		return Optional.ofNullable(this.currentV3);
	}

	/**
	 * Compatibility view for existing 2C authorities.
	 */
	public Optional<WorldStateV2> currentV2() {
		return this.currentV3 == null ? Optional.empty() : Optional.of(this.currentV3.core());
	}

	public Optional<WorldStateV2> legacyV2() {
		return Optional.ofNullable(this.legacyV2);
	}

	public Optional<LegacyWorldStateV1> legacyV1() {
		return Optional.ofNullable(this.legacyV1);
	}

	public Optional<net.minecraft.resources.Identifier> activeGameId() {
		return this.currentV3 == null ? Optional.empty() : this.currentV3.core().activeGameId();
	}

	public Optional<UUID> activeGameInstanceId() {
		return this.currentV3 == null
			? Optional.empty()
			: this.currentV3.activeGameInstanceId();
	}

	public Optional<net.minecraft.resources.Identifier> activePhaseId() {
		return this.currentV3 == null ? Optional.empty() : this.currentV3.core().activePhaseId();
	}

	public Optional<UUID> hostId() {
		if (this.currentV3 != null) {
			return this.currentV3.core().host().map(WorldStateV2.HostRecord::playerId);
		}
		if (this.legacyV2 != null) {
			return this.legacyV2.host().map(WorldStateV2.HostRecord::playerId);
		}
		return this.legacyV1 == null ? Optional.empty() : this.legacyV1.hostId();
	}

	public long stateRevision() {
		if (this.currentV3 != null) {
			return this.currentV3.stateRevision();
		}
		if (this.legacyV2 != null) {
			return this.legacyV2.stateRevision();
		}
		return this.legacyV1 == null ? 0L : this.legacyV1.stateRevision();
	}

	public boolean isSchemaCompatible() {
		return !this.loadFailureProtected && this.loadKind == LoadKind.CURRENT_V3;
	}

	public boolean needsTimelineSnapshotRepair() {
		return this.loadKind == LoadKind.CURRENT_V3
			&& this.loadFailureProtected
			&& this.currentV3 != null
			&& this.currentV3.timeline()
				.map(timeline -> timeline.snapshot().hasMissingContent())
				.orElse(false);
	}

	public String incompatibilityReason() {
		return this.incompatibilityReason;
	}

	private void requirePayloadInvariant() {
		boolean valid = switch (this.loadKind) {
			case CURRENT_V3 ->
				this.currentV3 != null
					&& this.legacyV2 == null
					&& this.legacyV1 == null
					&& this.preservedData == null;
			case LEGACY_V2 ->
				this.currentV3 == null
					&& this.legacyV2 != null
					&& this.legacyV1 == null
					&& this.preservedData != null;
			case LEGACY_V1 ->
				this.currentV3 == null
					&& this.legacyV2 == null
					&& this.legacyV1 != null
					&& this.preservedData != null;
			case OPAQUE ->
				this.currentV3 == null
					&& this.legacyV2 == null
					&& this.legacyV1 == null
					&& this.preservedData != null;
		};
		if (!valid) {
			throw new IllegalStateException("invalid " + this.loadKind + " world-state payload");
		}
	}

	private static String safeMessage(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public enum LoadKind {
		CURRENT_V3,
		LEGACY_V2,
		LEGACY_V1,
		OPAQUE
	}

	public record CommitResult(boolean committed, String reason, Optional<WorldStateV2> state) {
		public CommitResult {
			Objects.requireNonNull(reason, "reason");
			state = Objects.requireNonNull(state, "state");
		}

		private static CommitResult committed(final WorldStateV2 state) {
			return new CommitResult(true, "", Optional.of(state));
		}

		private static CommitResult rejected(final String reason) {
			return new CommitResult(false, reason, Optional.empty());
		}
	}

	public record CommitV3Result(boolean committed, String reason, Optional<WorldStateV3> state) {
		public CommitV3Result {
			Objects.requireNonNull(reason, "reason");
			state = Objects.requireNonNull(state, "state");
		}

		private static CommitV3Result committed(final WorldStateV3 state) {
			return new CommitV3Result(true, "", Optional.of(state));
		}

		private static CommitV3Result rejected(final String reason) {
			return new CommitV3Result(false, reason, Optional.empty());
		}
	}

	public record TimelineCheckpointResult(
		boolean committed,
		String reason,
		Optional<WorldStateV3.TimelineInstance> timeline
	) {
		public TimelineCheckpointResult {
			Objects.requireNonNull(reason, "reason");
			timeline = Objects.requireNonNull(timeline, "timeline");
		}

		private static TimelineCheckpointResult committed(
			final WorldStateV3.TimelineInstance timeline
		) {
			return new TimelineCheckpointResult(true, "", Optional.of(timeline));
		}

		private static TimelineCheckpointResult rejected(final String reason) {
			return new TimelineCheckpointResult(false, reason, Optional.empty());
		}
	}

	public record TimelineSnapshotRepairResult(
		boolean committed,
		String reason,
		Optional<WorldStateV3> state
	) {
		public TimelineSnapshotRepairResult {
			Objects.requireNonNull(reason, "reason");
			state = Objects.requireNonNull(state, "state");
		}

		private static TimelineSnapshotRepairResult committed(final WorldStateV3 state) {
			return new TimelineSnapshotRepairResult(true, "", Optional.of(state));
		}

		private static TimelineSnapshotRepairResult rejected(final String reason) {
			return new TimelineSnapshotRepairResult(false, reason, Optional.empty());
		}
	}

	record MigrationCommitResult(boolean committed, String reason, Optional<WorldStateV3> state) {
		private static MigrationCommitResult committed(final WorldStateV3 state) {
			return new MigrationCommitResult(true, "", Optional.of(state));
		}

		private static MigrationCommitResult rejected(final String reason) {
			return new MigrationCommitResult(false, reason, Optional.empty());
		}
	}
}
