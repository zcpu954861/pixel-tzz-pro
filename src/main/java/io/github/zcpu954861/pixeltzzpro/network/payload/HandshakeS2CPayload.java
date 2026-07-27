package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandshakeS2CPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {
	private static final int MAX_VERSION_LENGTH = 64;
	public static final Type<HandshakeS2CPayload> TYPE = new Type<>(PixelTzzPro.id("handshake_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeS2CPayload> STREAM_CODEC = CustomPacketPayload.codec(
		HandshakeS2CPayload::write,
		HandshakeS2CPayload::new
	);

	private HandshakeS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readUtf(MAX_VERSION_LENGTH));
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeUtf(this.modVersion, MAX_VERSION_LENGTH);
	}

	@Override
	public Type<HandshakeS2CPayload> type() {
		return TYPE;
	}
}
