package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Throttled client request for an authoritative full replacement after a sequence gap. */
public record HudResyncRequestC2SPayload(
	UUID gameInstanceId,
	Surface surface,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long appliedSequence
) implements CustomPacketPayload {
	public static final Type<HudResyncRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("hud_resync_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HudResyncRequestC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HudResyncRequestC2SPayload::write, HudResyncRequestC2SPayload::new);

	public HudResyncRequestC2SPayload {
		gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
		surface = Objects.requireNonNull(surface, "surface");
		if (
			definitionGeneration < 0L
				|| epoch < 0L
				|| contextVersion < 0L
				|| appliedSequence < 0L
		) {
			throw new IllegalArgumentException("HUD resync versions must be non-negative");
		}
	}

	private HudResyncRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			Surface.read(buffer.readVarInt()),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readVarLong()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.gameInstanceId);
		buffer.writeVarInt(this.surface.ordinal());
		buffer.writeVarLong(this.definitionGeneration);
		buffer.writeVarLong(this.epoch);
		buffer.writeVarLong(this.contextVersion);
		buffer.writeVarLong(this.appliedSequence);
	}

	@Override
	public Type<HudResyncRequestC2SPayload> type() {
		return TYPE;
	}

	public enum Surface {
		HUD,
		COUNTDOWN;

		private static Surface read(final int ordinal) {
			if (ordinal < 0 || ordinal >= values().length) {
				throw new IllegalArgumentException("unknown HUD resync surface " + ordinal);
			}
			return values()[ordinal];
		}
	}
}
