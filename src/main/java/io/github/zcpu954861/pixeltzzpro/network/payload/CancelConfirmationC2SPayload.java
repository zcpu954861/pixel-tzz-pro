package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cancels the exact confirmation token currently displayed by the client.
 */
public record CancelConfirmationC2SPayload(
	int protocolVersion,
	long requestSequence,
	UUID tokenId
) implements CustomPacketPayload {
	public static final Type<CancelConfirmationC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("cancel_confirmation_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CancelConfirmationC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(CancelConfirmationC2SPayload::write, CancelConfirmationC2SPayload::new);

	public CancelConfirmationC2SPayload {
		if (protocolVersion < 0 || requestSequence < 0L) {
			throw new IllegalArgumentException("protocolVersion and requestSequence must be non-negative");
		}
		tokenId = Objects.requireNonNull(tokenId, "tokenId");
	}

	public CancelConfirmationC2SPayload(final long requestSequence, final UUID tokenId) {
		this(NetworkProtocol.CURRENT_VERSION, requestSequence, tokenId);
	}

	private CancelConfirmationC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readVarLong(), buffer.readUUID());
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeUUID(this.tokenId);
	}

	@Override
	public Type<CancelConfirmationC2SPayload> type() {
		return TYPE;
	}
}
