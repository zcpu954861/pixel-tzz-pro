package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-authored host feedback that the client renders only after returning to the HUD.
 */
public record HostSubtitleS2CPayload(long sequence, String message)
	implements CustomPacketPayload {
	public static final int MAX_MESSAGE_LENGTH = 512;
	public static final Type<HostSubtitleS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("host_subtitle_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HostSubtitleS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HostSubtitleS2CPayload::write, HostSubtitleS2CPayload::new);

	public HostSubtitleS2CPayload {
		if (sequence < 0L) {
			throw new IllegalArgumentException("host subtitle sequence must be non-negative");
		}
		message = requireBounded(message, MAX_MESSAGE_LENGTH, "message");
		if (message.isBlank()) {
			throw new IllegalArgumentException("host subtitle message cannot be blank");
		}
	}

	private HostSubtitleS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarLong(), buffer.readUtf(MAX_MESSAGE_LENGTH));
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarLong(this.sequence);
		buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
	}

	@Override
	public Type<HostSubtitleS2CPayload> type() {
		return TYPE;
	}
}
