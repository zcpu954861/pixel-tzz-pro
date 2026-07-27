package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The single world-level source of truth owned by the server mod.
 *
 * <p>Scoreboards and tags may project this state in later milestones, but never replace it.
 */
public final class PixelTzzWorldState extends SavedData {
	public static final int CURRENT_SCHEMA_VERSION = 1;
	private static final Codec<StoredState> STORED_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.INT.fieldOf("schema_version").forGetter(StoredState::schemaVersion),
				GamePhase.CODEC.fieldOf("phase").forGetter(StoredState::phase),
				UUIDUtil.STRING_CODEC.optionalFieldOf("host_id").forGetter(StoredState::hostId),
				Codec.LONG.fieldOf("state_revision").forGetter(StoredState::stateRevision)
			)
			.apply(instance, StoredState::new)
	);
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

	private final int schemaVersion;
	private final GamePhase phase;
	private final Optional<UUID> hostId;
	private final long stateRevision;
	private final Dynamic<?> opaqueData;
	private boolean loadGuardChecked;
	private boolean loadFailureProtected;
	private String incompatibilityReason;

	private PixelTzzWorldState(
		final int schemaVersion,
		final GamePhase phase,
		final Optional<UUID> hostId,
		final long stateRevision,
		final Dynamic<?> opaqueData,
		final boolean loadFailureProtected,
		final String incompatibilityReason
	) {
		this.schemaVersion = schemaVersion;
		this.phase = phase;
		this.hostId = hostId;
		this.stateRevision = stateRevision;
		this.opaqueData = opaqueData;
		this.loadFailureProtected = loadFailureProtected;
		this.incompatibilityReason = incompatibilityReason;
	}

	public static PixelTzzWorldState initial() {
		return new PixelTzzWorldState(
			CURRENT_SCHEMA_VERSION,
			GamePhase.IDLE,
			Optional.empty(),
			0L,
			null,
			false,
			""
		);
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

	private static DataResult<PixelTzzWorldState> decode(final Dynamic<?> data) {
		int schemaVersion = data.get("schema_version").asInt(0);
		if (schemaVersion != CURRENT_SCHEMA_VERSION) {
			return DataResult.success(opaque(schemaVersion, data, "unsupported schema " + schemaVersion));
		}

		DataResult<StoredState> decoded = STORED_CODEC.parse(data);
		Optional<StoredState> result = decoded.result();
		if (result.isEmpty()) {
			String reason = decoded.error().map(DataResult.Error::message).orElse("invalid world state");
			return DataResult.success(opaque(schemaVersion, data, reason));
		}

		StoredState stored = result.orElseThrow();
		if (stored.stateRevision() < 0L) {
			return DataResult.success(opaque(schemaVersion, data, "negative state revision"));
		}

		return DataResult.success(
			new PixelTzzWorldState(
				stored.schemaVersion(),
				stored.phase(),
				stored.hostId(),
				stored.stateRevision(),
				null,
				false,
				""
			)
		);
	}

	private static PixelTzzWorldState opaque(
		final int schemaVersion,
		final Dynamic<?> data,
		final String reason
	) {
		return new PixelTzzWorldState(
			schemaVersion,
			GamePhase.IDLE,
			Optional.empty(),
			0L,
			data,
			false,
			reason
		);
	}

	private static Path stateFile(final MinecraftServer server) {
		Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("data");
		return TYPE.id().withSuffix(".dat").resolveAgainst(dataDirectory);
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

	private static DataResult<Dynamic<?>> encode(final PixelTzzWorldState state) {
		if (state.opaqueData != null) {
			return DataResult.success(state.opaqueData);
		}

		StoredState stored = new StoredState(
			state.schemaVersion,
			state.phase,
			state.hostId,
			state.stateRevision
		);
		return STORED_CODEC.encodeStart(JsonOps.INSTANCE, stored)
			.map(value -> new Dynamic<>(JsonOps.INSTANCE, value));
	}

	public int schemaVersion() {
		return this.schemaVersion;
	}

	public GamePhase phase() {
		return this.phase;
	}

	public Optional<UUID> hostId() {
		return this.hostId;
	}

	public long stateRevision() {
		return this.stateRevision;
	}

	public boolean isSchemaCompatible() {
		return !this.loadFailureProtected
			&& this.opaqueData == null
			&& this.schemaVersion == CURRENT_SCHEMA_VERSION;
	}

	public String incompatibilityReason() {
		return this.incompatibilityReason;
	}

	private record StoredState(
		int schemaVersion,
		GamePhase phase,
		Optional<UUID> hostId,
		long stateRevision
	) {
	}
}
