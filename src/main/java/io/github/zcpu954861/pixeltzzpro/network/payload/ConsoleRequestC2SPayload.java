package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Requests the latest bounded, context-aware control-console snapshot.
 */
public record ConsoleRequestC2SPayload(
	StatefulRequest request
) implements CustomPacketPayload {
	public static final Type<ConsoleRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("console_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleRequestC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(ConsoleRequestC2SPayload::write, ConsoleRequestC2SPayload::new);

	public ConsoleRequestC2SPayload {
		request = Objects.requireNonNull(request, "request");
	}

	private ConsoleRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(StatefulRequest.read(buffer));
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		this.request.write(buffer);
	}

	@Override
	public Type<ConsoleRequestC2SPayload> type() {
		return TYPE;
	}
}
