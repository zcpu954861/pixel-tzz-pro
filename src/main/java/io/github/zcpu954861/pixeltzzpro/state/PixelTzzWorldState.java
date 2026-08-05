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
import java.util.OptionalLong;
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
 * replace one immutable {@link WorldStateV5} payload and mark this wrapper dirty; this prevents
 * partial state mutations and keeps the unreadable-file guard from mistaking a legitimate
 * replacement object for a failed load. Existing 2C and 2D callers transact through the schema-v2
 * and schema-v3 core adapters.
 */
public final class PixelTzzWorldState extends SavedData {
	public static final int CURRENT_SCHEMA_VERSION = WorldStateV5.SCHEMA_VERSION;
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
	private WorldStateV5 currentV5;
	private WorldStateV4 currentV4;
	private WorldStateV4 legacyV4;
	private WorldStateV3 legacyV3;
	private WorldStateV2 legacyV2;
	private LegacyWorldStateV1 legacyV1;
	private Dynamic<?> preservedData;
	private boolean loadGuardChecked;
	private boolean loadFailureProtected;
	private String incompatibilityReason;

	private PixelTzzWorldState(
		final LoadKind loadKind,
		final WorldStateV5 currentV5,
		final WorldStateV4 legacyV4,
		final WorldStateV3 legacyV3,
		final WorldStateV2 legacyV2,
		final LegacyWorldStateV1 legacyV1,
		final Dynamic<?> preservedData,
		final boolean loadFailureProtected,
		final String incompatibilityReason
	) {
		this.loadKind = Objects.requireNonNull(loadKind, "loadKind");
		this.currentV5 = currentV5;
		this.currentV4 = currentV5 == null ? null : currentV5.core();
		this.legacyV4 = legacyV4;
		this.legacyV3 = legacyV3;
		this.legacyV2 = legacyV2;
		this.legacyV1 = legacyV1;
		this.preservedData = preservedData;
		this.loadFailureProtected = loadFailureProtected;
		this.incompatibilityReason = Objects.requireNonNull(incompatibilityReason, "incompatibilityReason");
		requirePayloadInvariant();
	}

	public static PixelTzzWorldState initial() {
		return current(WorldStateV5.initial());
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
		if (schemaVersion == WorldStateV5.SCHEMA_VERSION) {
			DataResult<WorldStateV5> decoded = WorldStateV5.CODEC.parse(data);
			return decoded.result()
				.map(value -> DataResult.success(
					value.core().core().timeline()
						.filter(timeline -> timeline.snapshot().hasMissingContent())
						.map(ignored -> repairableCurrent(
							value,
							"时间线冻结快照内容在旧版 NBT 保存时丢失，正在等待按 SHA-256 安全重建"
						))
						.orElseGet(() -> current(value))
				))
				.orElseGet(() -> DataResult.success(
					opaque(
						schemaVersion,
						data,
						decoded.error().map(DataResult.Error::message).orElse("invalid schema v5 world state")
					)
				));
		}
		if (schemaVersion == WorldStateV4.SCHEMA_VERSION) {
			DataResult<WorldStateV4> decoded = WorldStateV4.CODEC.parse(data);
			return decoded.result()
				.map(value -> DataResult.success(legacy(value, data)))
				.orElseGet(() -> DataResult.success(
					opaque(
						schemaVersion,
						data,
						decoded.error().map(DataResult.Error::message).orElse("invalid schema v4 world state")
					)
				));
		}
		if (schemaVersion == WorldStateV3.SCHEMA_VERSION) {
			ScopeRepair repair = addMissingGameInstanceScope(data);
			DataResult<WorldStateV3> decoded = WorldStateV3.CODEC.parse(repair.data());
			return decoded.result()
				.map(value -> DataResult.success(legacy(value, data)))
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

	private static PixelTzzWorldState current(final WorldStateV5 value) {
		return new PixelTzzWorldState(
			LoadKind.CURRENT_V5,
			value,
			null,
			null,
			null,
			null,
			null,
			false,
			""
		);
	}

	private static PixelTzzWorldState repairableCurrent(
		final WorldStateV5 value,
		final String reason
	) {
		return new PixelTzzWorldState(
			LoadKind.CURRENT_V5,
			value,
			null,
			null,
			null,
			null,
			null,
			true,
			reason
		);
	}

	private static PixelTzzWorldState legacy(
		final WorldStateV4 value,
		final Dynamic<?> preservedData
	) {
		return new PixelTzzWorldState(
			LoadKind.LEGACY_V4,
			null,
			value,
			null,
			null,
			null,
			preservedData,
			false,
			"schema v4 requires an additive startup migration"
		);
	}

	private static PixelTzzWorldState legacy(final WorldStateV3 value, final Dynamic<?> preservedData) {
		return new PixelTzzWorldState(
			LoadKind.LEGACY_V3,
			null,
			null,
			value,
			null,
			null,
			preservedData,
			false,
			"schema v3 requires an additive startup migration"
		);
	}

	private static PixelTzzWorldState legacy(final WorldStateV2 value, final Dynamic<?> preservedData) {
		return new PixelTzzWorldState(
			LoadKind.LEGACY_V2,
			null,
			null,
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
			null,
			null,
			data,
			false,
			(schemaVersion == 0 ? "missing or invalid schema; " : "") + reason
		);
	}

	private static DataResult<Dynamic<?>> encode(final PixelTzzWorldState state) {
		if (state.currentV5 != null) {
			return WorldStateV5.CODEC.encodeStart(JsonOps.INSTANCE, state.currentV5)
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
		if (this.currentV4.stateRevision() != expectedRevision) {
			return CommitResult.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV4.stateRevision()
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
			candidate = Objects.requireNonNull(mutation.apply(this.currentV4.core().core()), "mutation result")
				.withStateRevision(nextRevision);
		} catch (RuntimeException error) {
			return CommitResult.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV2> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitResult.rejected(validated.error().orElseThrow().message());
		}

		WorldStateV4 next = this.currentV4.withCore(this.currentV4.core().withCore(candidate));
		DataResult<WorldStateV4> rootValidation = next.validated();
		if (rootValidation.error().isPresent()) {
			return CommitResult.rejected(rootValidation.error().orElseThrow().message());
		}
		setCurrentCore(next);
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
		if (this.currentV4.stateRevision() != expectedRevision) {
			return CommitV3Result.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV4.stateRevision()
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
			WorldStateV3 mutated = Objects.requireNonNull(
				mutation.apply(this.currentV4.core()),
				"mutation result"
			);
			candidate = mutated.withCore(mutated.core().withStateRevision(nextRevision));
		} catch (RuntimeException error) {
			return CommitV3Result.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV3> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitV3Result.rejected(validated.error().orElseThrow().message());
		}
		setCurrentCore(this.currentV4.withCore(candidate));
		this.setDirty();
		return CommitV3Result.committed(candidate);
	}

	/**
	 * Native schema-v4 transaction for player-action ledgers and future additive root state.
	 */
	public synchronized CommitV4Result commitV4(
		final long expectedRevision,
		final UnaryOperator<WorldStateV4> mutation
	) {
		Objects.requireNonNull(mutation, "mutation");
		if (!isSchemaCompatible()) {
			return CommitV4Result.rejected("world state is not writable: " + this.incompatibilityReason);
		}
		if (this.currentV4.stateRevision() != expectedRevision) {
			return CommitV4Result.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV4.stateRevision()
			);
		}
		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(expectedRevision);
		} catch (ArithmeticException error) {
			return CommitV4Result.rejected("state revision overflow");
		}
		final WorldStateV4 candidate;
		try {
			WorldStateV4 mutated = Objects.requireNonNull(mutation.apply(this.currentV4), "mutation result");
			WorldStateV3 revisedCore = mutated.core()
				.withCore(mutated.core().core().withStateRevision(nextRevision));
			candidate = mutated.withCore(revisedCore);
		} catch (RuntimeException error) {
			return CommitV4Result.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV4> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitV4Result.rejected(validated.error().orElseThrow().message());
		}
		setCurrentCore(candidate);
		this.setDirty();
		return CommitV4Result.committed(candidate);
	}

	/** Native schema-v5 transaction used for atomic approval/lifecycle changes. */
	public synchronized CommitV5Result commitV5(
		final long expectedRevision,
		final UnaryOperator<WorldStateV5> mutation
	) {
		Objects.requireNonNull(mutation, "mutation");
		if (!isSchemaCompatible()) {
			return CommitV5Result.rejected("world state is not writable: " + this.incompatibilityReason);
		}
		if (this.currentV5.stateRevision() != expectedRevision) {
			return CommitV5Result.rejected(
				"state revision mismatch: expected " + expectedRevision + ", current " + this.currentV5.stateRevision()
			);
		}
		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(expectedRevision);
		} catch (ArithmeticException error) {
			return CommitV5Result.rejected("state revision overflow");
		}
		final WorldStateV5 candidate;
		try {
			WorldStateV5 mutated = Objects.requireNonNull(mutation.apply(this.currentV5), "mutation result");
			WorldStateV3 revisedV3 = mutated.core().core()
				.withCore(mutated.core().core().core().withStateRevision(nextRevision));
			WorldStateV4 revisedV4 = mutated.core().withCore(revisedV3);
			candidate = mutated.withCore(revisedV4);
		} catch (RuntimeException error) {
			return CommitV5Result.rejected("state mutation failed validation: " + safeMessage(error));
		}
		DataResult<WorldStateV5> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CommitV5Result.rejected(validated.error().orElseThrow().message());
		}
		this.currentV5 = candidate;
		this.currentV4 = candidate.core();
		this.setDirty();
		return CommitV5Result.committed(candidate);
	}

	/**
	 * Narrow countdown checkpoint that leaves the global revision unchanged. Routine remaining-time
	 * and callback-ledger progress must not invalidate unrelated host confirmations or terminals.
	 */
	public synchronized CountdownCheckpointResult countdownCheckpoint(
		final UUID expectedGameInstanceId,
		final UUID expectedCountdownInstanceId,
		final long expectedStateVersion,
		final long expectedRemainingTicks,
		final long expectedServerTickAnchor,
		final Optional<PersistedCountdown.Instance> replacement
	) {
		Objects.requireNonNull(expectedGameInstanceId, "expectedGameInstanceId");
		Objects.requireNonNull(expectedCountdownInstanceId, "expectedCountdownInstanceId");
		Objects.requireNonNull(replacement, "replacement");
		if (!isSchemaCompatible()) {
			return CountdownCheckpointResult.rejected(
				"world state is not writable: " + this.incompatibilityReason
			);
		}
		PersistedCountdown.Instance current = this.currentV5.countdown().orElse(null);
		if (current == null) {
			return CountdownCheckpointResult.rejected("countdown checkpoint has no active instance");
		}
		if (
			!current.gameInstanceId().equals(expectedGameInstanceId)
				|| !current.countdownInstanceId().equals(expectedCountdownInstanceId)
		) {
			return CountdownCheckpointResult.rejected("countdown instance changed before checkpoint");
		}
		if (
			current.lifecycle().stateVersion() != expectedStateVersion
				|| current.lifecycle().remainingTicks() != expectedRemainingTicks
				|| current.lifecycle().lastServerTickAnchor() != expectedServerTickAnchor
		) {
			return CountdownCheckpointResult.rejected("countdown lifecycle changed before checkpoint");
		}
		if (replacement.isPresent()) {
			PersistedCountdown.Instance next = replacement.orElseThrow();
			if (
				!next.gameInstanceId().equals(expectedGameInstanceId)
					|| !next.countdownInstanceId().equals(expectedCountdownInstanceId)
			) {
				return CountdownCheckpointResult.rejected("replacement belongs to another countdown");
			}
			if (!sameFrozenCountdown(current, next)) {
				return CountdownCheckpointResult.rejected("narrow checkpoint cannot replace frozen countdown content");
			}
			String monotonicError = countdownMonotonicError(current, next);
			if (monotonicError != null) {
				return CountdownCheckpointResult.rejected(monotonicError);
			}
		} else if (!current.lifecycle().state().terminal()) {
			return CountdownCheckpointResult.rejected("only a terminal countdown may be retired");
		}
		WorldStateV5 candidate = this.currentV5.withCountdown(replacement);
		DataResult<WorldStateV5> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return CountdownCheckpointResult.rejected(validated.error().orElseThrow().message());
		}
		if (!candidate.equals(this.currentV5)) {
			this.currentV5 = candidate;
			this.currentV4 = candidate.core();
			this.setDirty();
		}
		return CountdownCheckpointResult.committed(replacement);
	}

	private static boolean sameFrozenCountdown(
		final PersistedCountdown.Instance current,
		final PersistedCountdown.Instance replacement
	) {
		return current.definition().equals(replacement.definition())
			&& current.totalTicks() == replacement.totalTicks()
			&& current.participants().equals(replacement.participants())
			&& current.hostId().equals(replacement.hostId())
			&& current.disconnectPolicies().equals(replacement.disconnectPolicies())
			&& current.restrictions().equals(replacement.restrictions())
			&& current.checkpoints().equals(replacement.checkpoints())
			&& current.callbackDefinitions().equals(replacement.callbackDefinitions());
	}

	private static String countdownMonotonicError(
		final PersistedCountdown.Instance current,
		final PersistedCountdown.Instance replacement
	) {
		boolean recoveryAnchorReset = recoveryAnchorReset(current, replacement);
		if (
			replacement.lifecycle().stateVersion() < current.lifecycle().stateVersion()
				|| replacement.lifecycle().remainingTicks() > current.lifecycle().remainingTicks()
				|| (
					replacement.lifecycle().lastServerTickAnchor()
						< current.lifecycle().lastServerTickAnchor()
						&& !recoveryAnchorReset
				)
		) {
			return "countdown checkpoint moved backwards";
		}
		if (
			!replacement.lifecycle().state().equals(current.lifecycle().state())
				&& replacement.lifecycle().stateVersion() == current.lifecycle().stateVersion()
		) {
			return "countdown state changed without incrementing its version";
		}
		if (
			current.lifecycle().state().terminal()
				&& replacement.lifecycle().state() != current.lifecycle().state()
		) {
			return "terminal countdown cannot return to an active state";
		}
		if (
			(current.lifecycle().state() == PersistedCountdown.CountdownState.COMPLETING
				&& (replacement.lifecycle().state() == PersistedCountdown.CountdownState.CANCELING
					|| replacement.lifecycle().state() == PersistedCountdown.CountdownState.CANCELED))
				|| (current.lifecycle().state() == PersistedCountdown.CountdownState.CANCELING
					&& (replacement.lifecycle().state() == PersistedCountdown.CountdownState.COMPLETING
						|| replacement.lifecycle().state() == PersistedCountdown.CountdownState.COMPLETED))
		) {
			return "countdown completion and cancellation paths are mutually exclusive";
		}
		if (!replacement.emittedCheckpointIds().containsAll(current.emittedCheckpointIds())) {
			return "countdown checkpoint cannot forget an emitted presentation checkpoint";
		}
		for (PersistedCountdown.RestrictionMarker marker : current.restrictionMarkers()) {
			PersistedCountdown.RestrictionMarker next = replacement.restrictionMarkers().stream()
				.filter(value -> value.participantId().equals(marker.participantId()))
				.findFirst()
				.orElseThrow();
			if (!marker.active() && next.active()) {
				return "countdown checkpoint cannot reactivate a released restriction marker";
			}
		}
		for (PersistedCountdown.CallbackEntry entry : current.callbackLedger().entries()) {
			PersistedCountdown.CallbackEntry next = replacement.callbackLedger().entries().stream()
				.filter(value -> value.key().equals(entry.key()))
				.findFirst()
				.orElse(null);
			if (next == null) {
				return "countdown checkpoint cannot discard callback replay evidence";
			}
			if (entry.status() == PersistedCountdown.CallbackStatus.SUCCEEDED && !next.equals(entry)) {
				return "successful countdown callback evidence is immutable";
			}
			if (next.attemptCount() < entry.attemptCount()) {
				return "countdown callback attempt count moved backwards";
			}
			switch (entry.status()) {
				case PENDING -> {
					if (
						next.attemptCount() > 1
							|| next.attemptCount() == 1
								&& next.status() != PersistedCountdown.CallbackStatus.PREPARED
					) {
						return "pending countdown callback advanced without a durable prepare";
					}
				}
				case PREPARED -> {
					if (next.attemptCount() != entry.attemptCount()) {
						return "prepared countdown callback cannot start another attempt";
					}
				}
				case SUCCEEDED -> {
					// Exact immutability was checked above.
				}
				case FAILED, OUTCOME_UNKNOWN -> {
					long nextAllowedAttempt = (long) entry.attemptCount() + 1L;
					if (
						(long) next.attemptCount() > nextAllowedAttempt
							|| (long) next.attemptCount() == nextAllowedAttempt
								&& next.status() != PersistedCountdown.CallbackStatus.PREPARED
					) {
						return "countdown callback retry skipped its durable prepare boundary";
					}
				}
			}
		}
		return null;
	}

	/**
	 * A normal process restart resets {@code MinecraftServer#getTickCount()} to zero. The only
	 * legal backwards anchor is therefore the versioned transition into {@code recovery_wait}; it
	 * must preserve both the remaining time and the original nonterminal recovery target.
	 */
	private static boolean recoveryAnchorReset(
		final PersistedCountdown.Instance current,
		final PersistedCountdown.Instance replacement
	) {
		if (
			replacement.lifecycle().state() != PersistedCountdown.CountdownState.RECOVERY_WAIT
				|| replacement.lifecycle().stateVersion() <= current.lifecycle().stateVersion()
				|| replacement.lifecycle().remainingTicks() != current.lifecycle().remainingTicks()
		) {
			return false;
		}
		PersistedCountdown.CountdownState expectedTarget =
			current.lifecycle().state() == PersistedCountdown.CountdownState.RECOVERY_WAIT
				? current.lifecycle().recoveryTarget().orElse(null)
				: current.lifecycle().state();
		return expectedTarget != null
			&& !expectedTarget.terminal()
			&& expectedTarget != PersistedCountdown.CountdownState.RECOVERY_WAIT
			&& replacement.lifecycle().recoveryTarget().filter(expectedTarget::equals).isPresent();
	}

	/**
	 * Persists only the optional V3B message-runtime snapshot without changing global or game
	 * lifecycle revisions.
	 *
	 * <p>Routine message progress can checkpoint frequently, so routing it through {@link
	 * #commitV4(long, UnaryOperator)} would invalidate confirmation tokens and terminal sessions.
	 * This transaction is intentionally narrow: the caller must still name the active game scope
	 * and the exact previously persisted runtime revision, and no core or sibling v4 ledger can be
	 * replaced here.
	 */
	public synchronized MessageRuntimeCheckpointResult messageRuntimeCheckpoint(
		final UUID expectedGameInstanceId,
		final OptionalLong expectedRuntimeRevision,
		final Optional<PersistedMessageRuntime.Snapshot> replacement
	) {
		Objects.requireNonNull(expectedGameInstanceId, "expectedGameInstanceId");
		Objects.requireNonNull(expectedRuntimeRevision, "expectedRuntimeRevision");
		Objects.requireNonNull(replacement, "replacement");
		if (!isSchemaCompatible()) {
			return MessageRuntimeCheckpointResult.rejected(
				"world state is not writable: " + this.incompatibilityReason
			);
		}
		Optional<UUID> activeScope = this.currentV4.core().activeGameInstanceId();
		if (activeScope.isEmpty()) {
			return MessageRuntimeCheckpointResult.rejected("message runtime has no active game scope");
		}
		if (!activeScope.orElseThrow().equals(expectedGameInstanceId)) {
			return MessageRuntimeCheckpointResult.rejected(
				"active game changed before message runtime checkpoint"
			);
		}

		Optional<PersistedMessageRuntime.Snapshot> current = this.currentV4.messageRuntime();
		OptionalLong currentRevision = current.isPresent()
			? OptionalLong.of(current.orElseThrow().runtimeRevision())
			: OptionalLong.empty();
		if (!sameOptionalLong(currentRevision, expectedRuntimeRevision)) {
			return MessageRuntimeCheckpointResult.rejected(
				"message runtime revision changed before checkpoint"
			);
		}

		if (replacement.isPresent()) {
			PersistedMessageRuntime.Snapshot next = replacement.orElseThrow();
			if (!next.gameInstanceId().equals(expectedGameInstanceId)) {
				return MessageRuntimeCheckpointResult.rejected(
					"message runtime checkpoint belongs to another game instance"
				);
			}
			if (
				current.filter(value -> next.runtimeRevision() < value.runtimeRevision()).isPresent()
			) {
				return MessageRuntimeCheckpointResult.rejected(
					"message runtime checkpoint revision moved backwards"
				);
			}
			if (
				current.filter(value -> next.snapshotGameTick() < value.snapshotGameTick()).isPresent()
			) {
				return MessageRuntimeCheckpointResult.rejected(
					"message runtime checkpoint tick moved backwards"
				);
			}
		}

		WorldStateV4 candidate = this.currentV4.withMessageRuntime(replacement);
		DataResult<WorldStateV4> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return MessageRuntimeCheckpointResult.rejected(
				validated.error().orElseThrow().message()
			);
		}
		if (!candidate.equals(this.currentV4)) {
			setCurrentCore(candidate);
			this.setDirty();
		}
		return MessageRuntimeCheckpointResult.committed(replacement);
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
		WorldStateV3.TimelineInstance current = this.currentV4.core().timeline().orElse(null);
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
		WorldStateV4 next = this.currentV4.withCore(
			this.currentV4.core().withTimeline(Optional.of(candidate))
		);
		DataResult<WorldStateV4> validated = next.validated();
		if (validated.error().isPresent()) {
			return TimelineCheckpointResult.rejected(validated.error().orElseThrow().message());
		}
		setCurrentCore(next);
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
			this.loadKind == LoadKind.LEGACY_V4
				&& this.legacyV4 != null
				&& this.legacyV4.core().timeline()
					.map(timeline -> timeline.snapshot().hasMissingContent())
					.orElse(false)
		) {
			TimelineInstance timeline = this.legacyV4.core().timeline().orElseThrow();
			String replacementError = snapshotReplacementError(timeline.snapshot(), replacement);
			if (replacementError != null) {
				return TimelineSnapshotRepairResult.rejected(replacementError);
			}
			WorldStateV3 candidateCore = this.legacyV4.core().withTimeline(
				Optional.of(timeline.withSnapshot(replacement))
			);
			WorldStateV4 candidate = this.legacyV4.withCore(candidateCore);
			DataResult<WorldStateV4> validated = candidate.validated();
			if (validated.error().isPresent()) {
				return TimelineSnapshotRepairResult.rejected(validated.error().orElseThrow().message());
			}
			this.legacyV4 = candidate;
			return TimelineSnapshotRepairResult.committed(candidateCore);
		}
		if (
			this.loadKind == LoadKind.LEGACY_V3
				&& this.legacyV3 != null
				&& this.legacyV3.timeline()
					.map(timeline -> timeline.snapshot().hasMissingContent())
					.orElse(false)
		) {
			TimelineInstance timeline = this.legacyV3.timeline().orElseThrow();
			String replacementError = snapshotReplacementError(timeline.snapshot(), replacement);
			if (replacementError != null) {
				return TimelineSnapshotRepairResult.rejected(replacementError);
			}
			WorldStateV3 candidate = this.legacyV3.withTimeline(
				Optional.of(timeline.withSnapshot(replacement))
			);
			DataResult<WorldStateV3> validated = candidate.validated();
			if (validated.error().isPresent()) {
				return TimelineSnapshotRepairResult.rejected(validated.error().orElseThrow().message());
			}
			/*
			 * Keep the original dynamic payload and dirty flag untouched. The immediately following
			 * startup migration must first create a verified copy of the actual source file.
			 */
			this.legacyV3 = candidate;
			return TimelineSnapshotRepairResult.committed(candidate);
		}
		if (
			this.loadKind != LoadKind.CURRENT_V5
				|| !this.loadFailureProtected
				|| this.currentV4 == null
				|| this.currentV4.core().timeline().isEmpty()
		) {
			return TimelineSnapshotRepairResult.rejected("world is not awaiting timeline snapshot repair");
		}
		TimelineInstance timeline = this.currentV4.core().timeline().orElseThrow();
		FrozenDocument missing = timeline.snapshot();
		String replacementError = snapshotReplacementError(missing, replacement);
		if (replacementError != null) {
			return TimelineSnapshotRepairResult.rejected(replacementError);
		}
		final long nextRevision;
		try {
			nextRevision = Math.incrementExact(this.currentV4.stateRevision());
		} catch (ArithmeticException error) {
			return TimelineSnapshotRepairResult.rejected("state revision overflow");
		}
		WorldStateV3 candidateCore = this.currentV4.core()
			.withTimeline(Optional.of(timeline.withSnapshot(replacement)))
			.withCore(this.currentV4.core().core().withStateRevision(nextRevision));
		WorldStateV4 candidate = this.currentV4.withCore(candidateCore);
		DataResult<WorldStateV4> validated = candidate.validated();
		if (validated.error().isPresent()) {
			return TimelineSnapshotRepairResult.rejected(validated.error().orElseThrow().message());
		}
		setCurrentCore(candidate);
		this.loadFailureProtected = false;
		this.incompatibilityReason = "";
		this.setDirty();
		return TimelineSnapshotRepairResult.committed(candidateCore);
	}

	private static String snapshotReplacementError(
		final FrozenDocument missing,
		final FrozenDocument replacement
	) {
		if (!missing.hasMissingContent()) {
			return "timeline snapshot content is not missing";
		}
		if (
			replacement.hasMissingContent()
				|| replacement.normalizedJson().isBlank()
				|| !FrozenDocument.of(replacement.normalizedJson()).sha256().equals(replacement.sha256())
		) {
			return "replacement timeline snapshot is invalid";
		}
		return missing.sha256().equals(replacement.sha256())
			? null
			: "replacement timeline snapshot SHA-256 does not match the protected save";
	}

	private void setCurrentCore(final WorldStateV4 value) {
		if (this.currentV5 == null) {
			throw new IllegalStateException("schema-v5 world-state payload is missing");
		}
		this.currentV5 = this.currentV5.withCore(Objects.requireNonNull(value, "value"));
		this.currentV4 = this.currentV5.core();
	}

	synchronized MigrationCommitResult commitLegacyV1Migration(
		final LegacyWorldStateV1 expectedLegacy,
		final WorldStateV5 candidate
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
		DataResult<WorldStateV5> validated = candidate.validated();
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

		publishMigrated(candidate);
		return MigrationCommitResult.committed(candidate);
	}

	synchronized MigrationCommitResult commitLegacyV2Migration(
		final WorldStateV2 expectedLegacy,
		final WorldStateV5 candidate
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
		DataResult<WorldStateV5> validated = candidate.validated();
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
		publishMigrated(candidate);
		return MigrationCommitResult.committed(candidate);
	}

	synchronized MigrationCommitResult commitLegacyV3Migration(
		final WorldStateV3 expectedLegacy,
		final WorldStateV5 candidate
	) {
		Objects.requireNonNull(expectedLegacy, "expectedLegacy");
		Objects.requireNonNull(candidate, "candidate");
		if (
			this.loadFailureProtected
				|| this.loadKind != LoadKind.LEGACY_V3
				|| !expectedLegacy.equals(this.legacyV3)
		) {
			return MigrationCommitResult.rejected("legacy state changed before migration commit");
		}
		DataResult<WorldStateV5> validated = candidate.validated();
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
		publishMigrated(candidate);
		return MigrationCommitResult.committed(candidate);
	}

	synchronized MigrationCommitResult commitLegacyV4Migration(
		final WorldStateV4 expectedLegacy,
		final WorldStateV5 candidate
	) {
		Objects.requireNonNull(expectedLegacy, "expectedLegacy");
		Objects.requireNonNull(candidate, "candidate");
		if (
			this.loadFailureProtected
				|| this.loadKind != LoadKind.LEGACY_V4
				|| !expectedLegacy.equals(this.legacyV4)
		) {
			return MigrationCommitResult.rejected("legacy state changed before migration commit");
		}
		DataResult<WorldStateV5> validated = candidate.validated();
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
		publishMigrated(candidate);
		return MigrationCommitResult.committed(candidate);
	}

	private void publishMigrated(final WorldStateV5 candidate) {
		this.loadKind = LoadKind.CURRENT_V5;
		this.currentV5 = candidate;
		this.currentV4 = candidate.core();
		this.legacyV4 = null;
		this.legacyV3 = null;
		this.legacyV2 = null;
		this.legacyV1 = null;
		this.preservedData = null;
		this.incompatibilityReason = "";
		this.setDirty();
		requirePayloadInvariant();
	}

	synchronized void setMigrationDiagnostic(final String diagnostic) {
		if (
			(
				this.loadKind == LoadKind.LEGACY_V4
					|| this.loadKind == LoadKind.LEGACY_V1
					|| this.loadKind == LoadKind.LEGACY_V2
					|| this.loadKind == LoadKind.LEGACY_V3
			)
				&& !this.loadFailureProtected
		) {
			this.incompatibilityReason = Objects.requireNonNull(diagnostic, "diagnostic");
		}
	}

	public LoadKind loadKind() {
		return this.loadKind;
	}

	public int schemaVersion() {
		if (this.currentV5 != null) {
			return this.currentV5.schemaVersion();
		}
		if (this.legacyV4 != null) {
			return this.legacyV4.schemaVersion();
		}
		if (this.legacyV3 != null) {
			return this.legacyV3.schemaVersion();
		}
		if (this.legacyV2 != null) {
			return this.legacyV2.schemaVersion();
		}
		if (this.legacyV1 != null) {
			return this.legacyV1.schemaVersion();
		}
		return this.preservedData == null ? 0 : this.preservedData.get("schema_version").asInt(0);
	}

	public Optional<WorldStateV5> currentV5() {
		return Optional.ofNullable(this.currentV5);
	}

	public Optional<WorldStateV4> currentV4() {
		return Optional.ofNullable(this.currentV4);
	}

	public Optional<WorldStateV4> legacyV4() {
		return Optional.ofNullable(this.legacyV4);
	}

	/**
	 * Schema-v3 compatibility view of the current v4 core.
	 *
	 * <p>The only legacy exception is a damaged v3 timeline snapshot during the startup repair
	 * window; it is exposed read-only so the existing SHA-256 repair can run before the verified
	 * v3-to-v4 migration copy is created.
	 */
	public Optional<WorldStateV3> currentV3() {
		if (this.currentV4 != null) {
			return Optional.of(this.currentV4.core());
		}
		return this.legacyV3 != null
				&& this.legacyV3.timeline()
					.map(timeline -> timeline.snapshot().hasMissingContent())
					.orElse(false)
			? Optional.of(this.legacyV3)
			: Optional.empty();
	}

	/**
	 * Compatibility view for existing 2C authorities.
	 */
	public Optional<WorldStateV2> currentV2() {
		return this.currentV4 == null ? Optional.empty() : Optional.of(this.currentV4.core().core());
	}

	public Optional<WorldStateV3> legacyV3() {
		return Optional.ofNullable(this.legacyV3);
	}

	public Optional<WorldStateV2> legacyV2() {
		return Optional.ofNullable(this.legacyV2);
	}

	public Optional<LegacyWorldStateV1> legacyV1() {
		return Optional.ofNullable(this.legacyV1);
	}

	public Optional<net.minecraft.resources.Identifier> activeGameId() {
		return this.currentV4 == null ? Optional.empty() : this.currentV4.core().core().activeGameId();
	}

	public Optional<UUID> activeGameInstanceId() {
		return this.currentV4 == null
			? Optional.empty()
			: this.currentV4.core().activeGameInstanceId();
	}

	public Optional<net.minecraft.resources.Identifier> activePhaseId() {
		return this.currentV4 == null ? Optional.empty() : this.currentV4.core().core().activePhaseId();
	}

	public Optional<UUID> hostId() {
		if (this.currentV4 != null) {
			return this.currentV4.core().core().host().map(WorldStateV2.HostRecord::playerId);
		}
		if (this.legacyV3 != null) {
			return this.legacyV3.core().host().map(WorldStateV2.HostRecord::playerId);
		}
		if (this.legacyV2 != null) {
			return this.legacyV2.host().map(WorldStateV2.HostRecord::playerId);
		}
		return this.legacyV1 == null ? Optional.empty() : this.legacyV1.hostId();
	}

	public long stateRevision() {
		if (this.currentV4 != null) {
			return this.currentV4.stateRevision();
		}
		if (this.legacyV4 != null) {
			return this.legacyV4.stateRevision();
		}
		if (this.legacyV3 != null) {
			return this.legacyV3.stateRevision();
		}
		if (this.legacyV2 != null) {
			return this.legacyV2.stateRevision();
		}
		return this.legacyV1 == null ? 0L : this.legacyV1.stateRevision();
	}

	public boolean isSchemaCompatible() {
		return !this.loadFailureProtected && this.loadKind == LoadKind.CURRENT_V5;
	}

	public boolean needsTimelineSnapshotRepair() {
		if (
			this.loadKind == LoadKind.CURRENT_V5
				&& this.loadFailureProtected
				&& this.currentV4 != null
		) {
			return this.currentV4.core().timeline()
				.map(timeline -> timeline.snapshot().hasMissingContent())
				.orElse(false);
		}
		if (this.loadKind == LoadKind.LEGACY_V4 && this.legacyV4 != null) {
			return this.legacyV4.core().timeline()
				.map(timeline -> timeline.snapshot().hasMissingContent())
				.orElse(false);
		}
		return this.loadKind == LoadKind.LEGACY_V3
			&& this.legacyV3 != null
			&& this.legacyV3.timeline()
				.map(timeline -> timeline.snapshot().hasMissingContent())
				.orElse(false);
	}

	public String incompatibilityReason() {
		return this.incompatibilityReason;
	}

	private void requirePayloadInvariant() {
		boolean valid = switch (this.loadKind) {
			case CURRENT_V5 ->
				this.currentV5 != null
					&& this.currentV4 != null
					&& this.currentV4.equals(this.currentV5.core())
					&& this.legacyV4 == null
					&& this.legacyV3 == null
					&& this.legacyV2 == null
					&& this.legacyV1 == null
					&& this.preservedData == null;
			case LEGACY_V4 ->
				this.currentV5 == null
					&& this.currentV4 == null
					&& this.legacyV4 != null
					&& this.legacyV3 == null
					&& this.legacyV2 == null
					&& this.legacyV1 == null
					&& this.preservedData != null;
			case LEGACY_V3 ->
				this.currentV5 == null
					&& this.currentV4 == null
					&& this.legacyV4 == null
					&& this.legacyV3 != null
					&& this.legacyV2 == null
					&& this.legacyV1 == null
					&& this.preservedData != null;
			case LEGACY_V2 ->
				this.currentV5 == null
					&& this.currentV4 == null
					&& this.legacyV4 == null
					&& this.legacyV3 == null
					&& this.legacyV2 != null
					&& this.legacyV1 == null
					&& this.preservedData != null;
			case LEGACY_V1 ->
				this.currentV5 == null
					&& this.currentV4 == null
					&& this.legacyV4 == null
					&& this.legacyV3 == null
					&& this.legacyV2 == null
					&& this.legacyV1 != null
					&& this.preservedData != null;
			case OPAQUE ->
				this.currentV5 == null
					&& this.currentV4 == null
					&& this.legacyV4 == null
					&& this.legacyV3 == null
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
		CURRENT_V5,
		LEGACY_V4,
		LEGACY_V3,
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

	public record CommitV4Result(boolean committed, String reason, Optional<WorldStateV4> state) {
		public CommitV4Result {
			Objects.requireNonNull(reason, "reason");
			state = Objects.requireNonNull(state, "state");
		}

		private static CommitV4Result committed(final WorldStateV4 state) {
			return new CommitV4Result(true, "", Optional.of(state));
		}

		private static CommitV4Result rejected(final String reason) {
			return new CommitV4Result(false, reason, Optional.empty());
		}
	}

	public record CommitV5Result(boolean committed, String reason, Optional<WorldStateV5> state) {
		public CommitV5Result {
			Objects.requireNonNull(reason, "reason");
			state = Objects.requireNonNull(state, "state");
		}

		private static CommitV5Result committed(final WorldStateV5 state) {
			return new CommitV5Result(true, "", Optional.of(state));
		}

		private static CommitV5Result rejected(final String reason) {
			return new CommitV5Result(false, reason, Optional.empty());
		}
	}

	public record CountdownCheckpointResult(
		boolean committed,
		String reason,
		Optional<PersistedCountdown.Instance> countdown
	) {
		public CountdownCheckpointResult {
			Objects.requireNonNull(reason, "reason");
			countdown = Objects.requireNonNull(countdown, "countdown");
		}

		private static CountdownCheckpointResult committed(
			final Optional<PersistedCountdown.Instance> countdown
		) {
			return new CountdownCheckpointResult(true, "", countdown);
		}

		private static CountdownCheckpointResult rejected(final String reason) {
			return new CountdownCheckpointResult(false, reason, Optional.empty());
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

	public record MessageRuntimeCheckpointResult(
		boolean committed,
		String reason,
		Optional<PersistedMessageRuntime.Snapshot> snapshot
	) {
		public MessageRuntimeCheckpointResult {
			Objects.requireNonNull(reason, "reason");
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
		}

		private static MessageRuntimeCheckpointResult committed(
			final Optional<PersistedMessageRuntime.Snapshot> snapshot
		) {
			return new MessageRuntimeCheckpointResult(true, "", snapshot);
		}

		private static MessageRuntimeCheckpointResult rejected(final String reason) {
			return new MessageRuntimeCheckpointResult(false, reason, Optional.empty());
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

	record MigrationCommitResult(boolean committed, String reason, Optional<WorldStateV5> state) {
		private static MigrationCommitResult committed(final WorldStateV5 state) {
			return new MigrationCommitResult(true, "", Optional.of(state));
		}

		private static MigrationCommitResult rejected(final String reason) {
			return new MigrationCommitResult(false, reason, Optional.empty());
		}
	}

	private static boolean sameOptionalLong(
		final OptionalLong left,
		final OptionalLong right
	) {
		return left.isPresent() == right.isPresent()
			&& (left.isEmpty() || left.orElseThrow() == right.orElseThrow());
	}
}
