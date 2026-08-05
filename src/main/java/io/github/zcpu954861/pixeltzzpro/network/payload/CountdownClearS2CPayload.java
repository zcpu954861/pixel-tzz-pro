package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudFrameCodec.Header;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Authoritative removal of the active countdown projection. */
public record CountdownClearS2CPayload(
	UUID gameInstanceId,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long sequence
) implements CustomPacketPayload {
	public static final Type<CountdownClearS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("countdown_clear_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CountdownClearS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(CountdownClearS2CPayload::write, CountdownClearS2CPayload::new);

	public CountdownClearS2CPayload {
		HudFrameCodec.requireHeader(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
	}

	private CountdownClearS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(HudFrameCodec.readHeader(buffer));
	}

	private CountdownClearS2CPayload(final Header header) {
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
	public Type<CountdownClearS2CPayload> type() {
		return TYPE;
	}
}
