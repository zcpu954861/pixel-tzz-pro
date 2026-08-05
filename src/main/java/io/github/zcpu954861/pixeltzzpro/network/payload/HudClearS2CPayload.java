package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudFrameCodec.Header;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Authoritative removal of a dock projection; newer than any older frame in the epoch. */
public record HudClearS2CPayload(
	UUID gameInstanceId,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long sequence
) implements CustomPacketPayload {
	public static final Type<HudClearS2CPayload> TYPE = new Type<>(PixelTzzPro.id("hud_clear_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HudClearS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HudClearS2CPayload::write, HudClearS2CPayload::new);

	public HudClearS2CPayload {
		HudFrameCodec.requireHeader(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
	}

	private HudClearS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(HudFrameCodec.readHeader(buffer));
	}

	private HudClearS2CPayload(final Header header) {
		this(
			header.gameInstanceId(),
			header.definitionGeneration(),
			header.epoch(),
			header.contextVersion(),
			header.sequence()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		HudFrameCodec.writeHeader(buffer, HudFrameCodec.requireHeader(
			this.gameInstanceId,
			this.definitionGeneration,
			this.epoch,
			this.contextVersion,
			this.sequence
		));
	}

	@Override
	public Type<HudClearS2CPayload> type() {
		return TYPE;
	}
}
