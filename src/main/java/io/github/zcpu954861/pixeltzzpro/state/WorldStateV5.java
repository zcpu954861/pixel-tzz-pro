package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Additive schema-v5 envelope for one optional opening-countdown authority snapshot.
 *
 * <p>The complete accepted schema-v4 payload remains the core. Existing player actions, message
 * history and message-runtime recovery therefore keep their exact storage and mutation semantics.
 */
public record WorldStateV5(
	int schemaVersion,
	WorldStateV4 core,
	Optional<PersistedCountdown.Instance> countdown
) {
	public static final int SCHEMA_VERSION = 5;

	public static final Codec<WorldStateV5> CODEC = Codec.lazyInitialized(
		() -> RecordCodecBuilder.<WorldStateV5>create(
			builder -> builder.group(
					Codec.INT.fieldOf("schema_version").forGetter(WorldStateV5::schemaVersion),
					WorldStateV4.CODEC.fieldOf("core").forGetter(WorldStateV5::core),
					PersistedCountdown.Instance.CODEC
						.optionalFieldOf("countdown")
						.forGetter(WorldStateV5::countdown)
				)
				.apply(builder, WorldStateV5::new)
		).validate(WorldStateV5::validated)
	);

	public WorldStateV5 {
		Objects.requireNonNull(core, "core");
		countdown = Objects.requireNonNull(countdown, "countdown");
	}

	public static WorldStateV5 initial() {
		return new WorldStateV5(SCHEMA_VERSION, WorldStateV4.initial(), Optional.empty());
	}

	public long stateRevision() {
		return this.core.stateRevision();
	}

	/**
	 * Replaces the v4 core and retains the countdown only while the active game instance is the
	 * same. Resetting or clearing a game can therefore never leak an old countdown into a new scope.
	 */
	public WorldStateV5 withCore(final WorldStateV4 value) {
		Objects.requireNonNull(value, "value");
		Optional<UUID> before = this.core.core().activeGameInstanceId();
		Optional<UUID> after = value.core().activeGameInstanceId();
		return new WorldStateV5(
			this.schemaVersion,
			value,
			before.equals(after) ? this.countdown : Optional.empty()
		);
	}

	public WorldStateV5 withCountdown(final Optional<PersistedCountdown.Instance> value) {
		return new WorldStateV5(
			this.schemaVersion,
			this.core,
			Objects.requireNonNull(value, "value")
		);
	}

	public DataResult<WorldStateV5> validated() {
		String error = validationError();
		return error == null ? DataResult.success(this) : DataResult.error(() -> error);
	}

	private String validationError() {
		if (this.schemaVersion != SCHEMA_VERSION) {
			return "schema v5 payload declared schema " + this.schemaVersion;
		}
		DataResult<WorldStateV4> coreValidation = this.core.validated();
		if (coreValidation.error().isPresent()) {
			return "schema v5 core is invalid: " + coreValidation.error().orElseThrow().message();
		}
		if (this.countdown.isEmpty()) {
			return null;
		}
		PersistedCountdown.Instance value = this.countdown.orElseThrow();
		DataResult<PersistedCountdown.Instance> countdownValidation = value.validated();
		if (countdownValidation.error().isPresent()) {
			return "schema v5 countdown is invalid: "
				+ countdownValidation.error().orElseThrow().message();
		}
		return this.core.core().activeGameInstanceId().filter(value.gameInstanceId()::equals).isPresent()
			? null
			: "schema v5 countdown does not belong to the active game instance";
	}
}
