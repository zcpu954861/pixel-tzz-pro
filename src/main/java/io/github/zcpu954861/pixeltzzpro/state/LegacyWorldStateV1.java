package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Typed read-only bridge for schema v1. It is never emitted after a successful v2 migration.
 */
public record LegacyWorldStateV1(
	int schemaVersion,
	GamePhase phase,
	Optional<UUID> hostId,
	long stateRevision
) {
	public static final int SCHEMA_VERSION = 1;
	public static final Codec<LegacyWorldStateV1> CODEC = RecordCodecBuilder.<LegacyWorldStateV1>create(
		instance -> instance.group(
				Codec.INT.fieldOf("schema_version").forGetter(LegacyWorldStateV1::schemaVersion),
				GamePhase.CODEC.fieldOf("phase").forGetter(LegacyWorldStateV1::phase),
				UUIDUtil.STRING_CODEC.optionalFieldOf("host_id").forGetter(LegacyWorldStateV1::hostId),
				Codec.LONG.fieldOf("state_revision").forGetter(LegacyWorldStateV1::stateRevision)
			)
			.apply(instance, LegacyWorldStateV1::new)
	).validate(LegacyWorldStateV1::validated);

	public LegacyWorldStateV1 {
		java.util.Objects.requireNonNull(phase, "phase");
		hostId = java.util.Objects.requireNonNull(hostId, "hostId");
	}

	private DataResult<LegacyWorldStateV1> validated() {
		if (this.schemaVersion != SCHEMA_VERSION) {
			return DataResult.error(() -> "schema v1 payload declared schema " + this.schemaVersion);
		}
		return this.stateRevision < 0L
			? DataResult.error(() -> "negative state revision")
			: DataResult.success(this);
	}
}
