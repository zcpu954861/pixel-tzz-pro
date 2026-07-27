package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandshakeC2SPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {
	private static final int MAX_VERSION_LENGTH = 64;
	public static final Type<HandshakeC2SPayload> TYPE = new Type<>(PixelTzzPro.id("handshake_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeC2SPayload> STREAM_CODEC = CustomPacketPayload.codec(
		HandshakeC2SPayload::write,
		HandshakeC2SPayload::new
	);

	private HandshakeC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readUtf(MAX_VERSION_LENGTH));
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeUtf(this.modVersion, MAX_VERSION_LENGTH);
	}

	@Override
	public Type<HandshakeC2SPayload> type() {
		return TYPE;
	}
}
