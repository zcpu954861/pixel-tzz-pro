package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Consumes one server-issued confirmation token.
 */
public record CommitConfirmationC2SPayload(
	StatefulRequest request,
	UUID tokenId
) implements CustomPacketPayload {
	public static final Type<CommitConfirmationC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("commit_confirmation_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CommitConfirmationC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(CommitConfirmationC2SPayload::write, CommitConfirmationC2SPayload::new);

	public CommitConfirmationC2SPayload {
		request = Objects.requireNonNull(request, "request");
		tokenId = Objects.requireNonNull(tokenId, "tokenId");
	}

	private CommitConfirmationC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(StatefulRequest.read(buffer), buffer.readUUID());
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		this.request.write(buffer);
		buffer.writeUUID(this.tokenId);
	}

	@Override
	public Type<CommitConfirmationC2SPayload> type() {
		return TYPE;
	}
}
