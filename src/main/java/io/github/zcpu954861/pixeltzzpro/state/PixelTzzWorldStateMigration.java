package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.View;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RecoverySnapshotMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Startup-only migration into the current schema-v3 envelope.
 *
 * <p>The original compressed SavedData file is copied and verified before the in-memory wrapper is
 * atomically changed. Schema v2 migration is purely additive and definition-independent; schema v1
 * still needs the existing conservative game binding. Callers must not invoke this from a normal
 * data-pack reload.
 */
public final class PixelTzzWorldStateMigration {
	private PixelTzzWorldStateMigration() {
	}

	public static MigrationResult migrateOnServerStart(
		final MinecraftServer server,
		final View definitions
	) {
		Objects.requireNonNull(server, "server");
		Path stateFile = PixelTzzWorldState.stateFile(server);
		return migrate(
			PixelTzzWorldState.get(server),
			definitions,
			stateFile,
			stateFile.getParent().resolve("recovery"),
			System.currentTimeMillis()
		);
	}

	static MigrationResult migrate(
		final PixelTzzWorldState state,
		final View definitions,
		final Path stateFile,
		final Path recoveryDirectory,
		final long nowEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(stateFile, "stateFile");
		Objects.requireNonNull(recoveryDirectory, "recoveryDirectory");
		if (nowEpochMillis < 0L) {
			return failed(state, "migration time cannot be negative");
		}
		if (state.loadKind() == PixelTzzWorldState.LoadKind.CURRENT_V3) {
			return new MigrationResult(MigrationStatus.NOT_NEEDED, "world state already uses schema v3", Optional.empty());
		}
		if (
			state.loadKind() != PixelTzzWorldState.LoadKind.LEGACY_V1
				&& state.loadKind() != PixelTzzWorldState.LoadKind.LEGACY_V2
		) {
			return new MigrationResult(
				MigrationStatus.BLOCKED,
				"opaque or unreadable world state cannot be migrated automatically",
				Optional.empty()
			);
		}

		LegacyWorldStateV1 legacyV1 = state.legacyV1().orElse(null);
		WorldStateV2 legacyV2 = state.legacyV2().orElse(null);
		GameDefinition game = null;
		if (legacyV1 != null) {
			if (legacyV1.phase() != GamePhase.IDLE) {
				return blocked(state, "schema v1 phase is not IDLE");
			}
			if (!definitions.healthy()) {
				return blocked(state, "current data-pack definitions are not healthy");
			}
			if (definitions.active().games().size() != 1) {
				return blocked(state, "automatic migration requires exactly one healthy game definition");
			}
			DefinitionSnapshot snapshot = definitions.active();
			game = snapshot.games().values().iterator().next();
			String definitionError = validateMigrationGame(snapshot, game);
			if (definitionError != null) {
				return blocked(state, definitionError);
			}
		}

		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(
				legacyV2 == null ? legacyV1.stateRevision() : legacyV2.stateRevision()
			);
		} catch (ArithmeticException error) {
			return blocked(state, "legacy state revision cannot be incremented");
		}
		if (!Files.isRegularFile(stateFile)) {
			return failed(state, "legacy source file is missing");
		}

		int sourceSchema = legacyV2 == null
			? LegacyWorldStateV1.SCHEMA_VERSION
			: WorldStateV2.SCHEMA_VERSION;
		String backupFileName = "world_state-v" + sourceSchema + "-to-v3-" + nowEpochMillis + ".dat";
		Path backupFile = recoveryDirectory.resolve(backupFileName);
		final long sourceSize;
		final String sourceSha256;
		try {
			sourceSize = Files.size(stateFile);
			sourceSha256 = sha256(stateFile);
		} catch (IOException error) {
			return failed(state, "could not inspect legacy source file: " + safeMessage(error));
		}

		RecoverySnapshotMetadata metadata = new RecoverySnapshotMetadata(
			backupFileName,
			nowEpochMillis,
			sourceSchema,
			sourceSize,
			sourceSha256
		);
		WorldStateV3 candidate = legacyV2 == null
			? candidate(legacyV1, game, metadata, nextRevision, nowEpochMillis)
			: candidate(legacyV2, metadata, nextRevision);
		String candidateError = roundTripError(candidate);
		if (candidateError != null) {
			return failed(state, "schema v3 migration candidate is invalid: " + candidateError);
		}

		try {
			Files.createDirectories(recoveryDirectory);
			Files.copy(stateFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
			if (Files.size(backupFile) != sourceSize || !sha256(backupFile).equals(sourceSha256)) {
				return failed(state, "recovery copy verification failed");
			}
		} catch (IOException | RuntimeException error) {
			return failed(state, "could not create verified recovery copy: " + safeMessage(error));
		}

		PixelTzzWorldState.MigrationCommitResult committed = legacyV2 == null
			? state.commitLegacyV1Migration(legacyV1, candidate)
			: state.commitLegacyV2Migration(legacyV2, candidate);
		if (!committed.committed()) {
			return failed(state, "migration commit rejected: " + committed.reason());
		}
		return new MigrationResult(
			MigrationStatus.MIGRATED,
			"schema v" + sourceSchema + " migrated to v3",
			Optional.of(metadata)
		);
	}

	private static String validateMigrationGame(
		final DefinitionSnapshot snapshot,
		final GameDefinition game
	) {
		var phase = snapshot.phases().get(game.initialPhase());
		if (phase == null || !phase.game().equals(game.id())) {
			return "unique game has no valid initial phase";
		}
		var role = snapshot.roles().get(game.defaultRole());
		if (role == null || !role.game().equals(game.id())) {
			return "unique game has no valid default role";
		}
		var lifeState = snapshot.lifeStates().get(game.defaultLifeState());
		return lifeState == null || !lifeState.game().equals(game.id())
			? "unique game has no valid default life state"
			: null;
	}

	private static WorldStateV3 candidate(
		final LegacyWorldStateV1 legacy,
		final GameDefinition game,
		final RecoverySnapshotMetadata metadata,
		final long revision,
		final long nowEpochMillis
	) {
		Optional<HostRecord> host = legacy.hostId().map(HostRecord::migratedLegacy);
		AuditLog audit = new AuditLog(
			2L,
			List.of(
				new AuditEvent(
					0L,
					AuditEventType.RECOVERY_CREATED,
					nowEpochMillis,
					Optional.empty(),
					Optional.empty(),
					Optional.empty(),
					"Created verified schema v1 recovery snapshot " + metadata.fileName()
				),
				new AuditEvent(
					1L,
					AuditEventType.SCHEMA_MIGRATED,
					nowEpochMillis,
					Optional.empty(),
					Optional.empty(),
					Optional.empty(),
					"Migrated world state from schema v1 to v3"
				)
			)
		);
		WorldStateV2 core = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			revision,
			Optional.of(game.id()),
			Optional.of(game.initialPhase()),
			host,
			Map.of(),
			Optional.empty(),
			List.of(),
			audit,
			Optional.of(metadata)
		);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(UUID.randomUUID()),
			Optional.empty(),
			List.of(),
			List.of(metadata)
		);
	}

	private static WorldStateV3 candidate(
		final WorldStateV2 legacy,
		final RecoverySnapshotMetadata metadata,
		final long revision
	) {
		List<RecoverySnapshotMetadata> recoverySnapshots = new ArrayList<>();
		legacy.recoverySnapshotMetadata().ifPresent(recoverySnapshots::add);
		recoverySnapshots.add(metadata);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			legacy.withStateRevision(revision),
			legacy.activeGameId().map(ignored -> UUID.randomUUID()),
			Optional.empty(),
			List.of(),
			recoverySnapshots
		);
	}

	private static String roundTripError(final WorldStateV3 candidate) {
		DataResult<WorldStateV3> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return validated.error().orElseThrow().message();
		}
		DataResult<com.google.gson.JsonElement> encoded = WorldStateV3.CODEC.encodeStart(JsonOps.INSTANCE, candidate);
		if (encoded.error().isPresent()) {
			return encoded.error().orElseThrow().message();
		}
		DataResult<WorldStateV3> decoded = encoded.flatMap(value -> WorldStateV3.CODEC.parse(JsonOps.INSTANCE, value));
		if (decoded.error().isPresent()) {
			return decoded.error().orElseThrow().message();
		}
		return decoded.result().orElseThrow().equals(candidate) ? null : "codec round-trip changed the candidate";
	}

	private static String sha256(final Path path) throws IOException {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("JVM does not provide SHA-256", error);
		}
		try (InputStream input = Files.newInputStream(path)) {
			byte[] buffer = new byte[8_192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				if (read != 0) {
					digest.update(buffer, 0, read);
				}
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static MigrationResult blocked(final PixelTzzWorldState state, final String reason) {
		state.setMigrationDiagnostic(reason);
		return new MigrationResult(MigrationStatus.BLOCKED, reason, Optional.empty());
	}

	private static MigrationResult failed(final PixelTzzWorldState state, final String reason) {
		state.setMigrationDiagnostic(reason);
		return new MigrationResult(MigrationStatus.FAILED, reason, Optional.empty());
	}

	private static String safeMessage(final Throwable error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public enum MigrationStatus {
		NOT_NEEDED,
		MIGRATED,
		BLOCKED,
		FAILED
	}

	public record MigrationResult(
		MigrationStatus status,
		String reason,
		Optional<RecoverySnapshotMetadata> recoverySnapshot
	) {
		public MigrationResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(reason, "reason");
			recoverySnapshot = Objects.requireNonNull(recoverySnapshot, "recoverySnapshot");
		}
	}
}
