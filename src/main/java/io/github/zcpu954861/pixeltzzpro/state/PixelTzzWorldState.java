package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The single world-level source of truth owned by the server mod.
 *
 * <p>The wrapper keeps its object identity for the lifetime of the loaded world. Successful changes
 * replace one immutable {@link WorldStateV2} payload and mark this wrapper dirty; this prevents partial
 * state mutations and keeps the unreadable-file guard from mistaking a legitimate replacement object
 * for a failed load.
 */
public final class PixelTzzWorldState extends SavedData {
	public static final int CURRENT_SCHEMA_VERSION = WorldStateV2.SCHEMA_VERSION;
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
	private WorldStateV2 currentV2;
	private LegacyWorldStateV1 legacyV1;
	private Dynamic<?> preservedData;
	private boolean loadGuardChecked;
	private boolean loadFailureProtected;
	private String incompatibilityReason;

	private PixelTzzWorldState(
		final LoadKind loadKind,
		final WorldStateV2 currentV2,
		final LegacyWorldStateV1 legacyV1,
		final Dynamic<?> preservedData,
		final boolean loadFailureProtected,
		final String incompatibilityReason
	) {
		this.loadKind = Objects.requireNonNull(loadKind, "loadKind");
		this.currentV2 = currentV2;
		this.legacyV1 = legacyV1;
		this.preservedData = preservedData;
		this.loadFailureProtected = loadFailureProtected;
		this.incompatibilityReason = Objects.requireNonNull(incompatibilityReason, "incompatibilityReason");
		requirePayloadInvariant();
	}

	public static PixelTzzWorldState initial() {
		return current(WorldStateV2.initial());
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
		if (schemaVersion == WorldStateV2.SCHEMA_VERSION) {
			DataResult<WorldStateV2> decoded = WorldStateV2.CODEC.parse(data);
			return decoded.result()
				.map(value -> DataResult.success(current(value)))
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

	private static PixelTzzWorldState current(final WorldStateV2 value) {
		return new PixelTzzWorldState(LoadKind.CURRENT_V2, value, null, null, false, "");
	}

	private static PixelTzzWorldState legacy(final LegacyWorldStateV1 value, final Dynamic<?> preservedData) {
		return new PixelTzzWorldState(
			LoadKind.LEGACY_V1,
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
			data,
			false,
			(schemaVersion == 0 ? "missing or invalid schema; " : "") + reason
		);
	}

	private static DataResult<Dynamic<?>> encode(final PixelTzzWorldState state) {
		if (state.currentV2 != null) {
			return WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, state.currentV2)
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
		if (this.currentV2.stateRevision() != expectedRevision) {
			return CommitResult.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV2.stateRevision()
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
			candidate = Objects.requireNonNull(mutation.apply(this.currentV2), "mutation result")
				.withStateRevision(nextRevision);
		} catch (RuntimeException error) {
			return CommitResult.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV2> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitResult.rejected(validated.error().orElseThrow().message());
		}

		this.currentV2 = candidate;
		this.setDirty();
		return CommitResult.committed(candidate);
	}

	synchronized MigrationCommitResult commitLegacyMigration(
		final LegacyWorldStateV1 expectedLegacy,
		final WorldStateV2 candidate
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
		DataResult<WorldStateV2> validated = candidate.validated();
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

		this.loadKind = LoadKind.CURRENT_V2;
		this.currentV2 = candidate;
		this.legacyV1 = null;
		this.preservedData = null;
		this.incompatibilityReason = "";
		this.setDirty();
		requirePayloadInvariant();
		return MigrationCommitResult.committed(candidate);
	}

	synchronized void setMigrationDiagnostic(final String diagnostic) {
		if (this.loadKind == LoadKind.LEGACY_V1 && !this.loadFailureProtected) {
			this.incompatibilityReason = Objects.requireNonNull(diagnostic, "diagnostic");
		}
	}

	public LoadKind loadKind() {
		return this.loadKind;
	}

	public int schemaVersion() {
		if (this.currentV2 != null) {
			return this.currentV2.schemaVersion();
		}
		if (this.legacyV1 != null) {
			return this.legacyV1.schemaVersion();
		}
		return this.preservedData == null ? 0 : this.preservedData.get("schema_version").asInt(0);
	}

	public Optional<WorldStateV2> currentV2() {
		return Optional.ofNullable(this.currentV2);
	}

	public Optional<LegacyWorldStateV1> legacyV1() {
		return Optional.ofNullable(this.legacyV1);
	}

	public Optional<net.minecraft.resources.Identifier> activeGameId() {
		return this.currentV2 == null ? Optional.empty() : this.currentV2.activeGameId();
	}

	public Optional<net.minecraft.resources.Identifier> activePhaseId() {
		return this.currentV2 == null ? Optional.empty() : this.currentV2.activePhaseId();
	}

	/**
	 * Temporary v1 bridge for existing protocol-v5 callers. Schema v2 callers must use
	 * {@link #activePhaseId()}.
	 */
	@Deprecated
	public GamePhase phase() {
		return this.legacyV1 == null ? GamePhase.IDLE : this.legacyV1.phase();
	}

	public Optional<UUID> hostId() {
		if (this.currentV2 != null) {
			return this.currentV2.host().map(WorldStateV2.HostRecord::playerId);
		}
		return this.legacyV1 == null ? Optional.empty() : this.legacyV1.hostId();
	}

	public long stateRevision() {
		if (this.currentV2 != null) {
			return this.currentV2.stateRevision();
		}
		return this.legacyV1 == null ? 0L : this.legacyV1.stateRevision();
	}

	public boolean isSchemaCompatible() {
		return !this.loadFailureProtected && this.loadKind == LoadKind.CURRENT_V2;
	}

	public String incompatibilityReason() {
		return this.incompatibilityReason;
	}

	private void requirePayloadInvariant() {
		boolean valid = switch (this.loadKind) {
			case CURRENT_V2 -> this.currentV2 != null && this.legacyV1 == null && this.preservedData == null;
			case LEGACY_V1 -> this.currentV2 == null && this.legacyV1 != null && this.preservedData != null;
			case OPAQUE -> this.currentV2 == null && this.legacyV1 == null && this.preservedData != null;
		};
		if (!valid) {
			throw new IllegalStateException("invalid " + this.loadKind + " world-state payload");
		}
	}

	private static String safeMessage(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public enum LoadKind {
		CURRENT_V2,
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

	record MigrationCommitResult(boolean committed, String reason, Optional<WorldStateV2> state) {
		private static MigrationCommitResult committed(final WorldStateV2 state) {
			return new MigrationCommitResult(true, "", Optional.of(state));
		}

		private static MigrationCommitResult rejected(final String reason) {
			return new MigrationCommitResult(false, reason, Optional.empty());
		}
	}
}
