package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Explicit request to open the caller's current data-pack-routed player terminal.
 */
public record TerminalOpenC2SPayload(
	int protocolVersion,
	long requestSequence
) implements CustomPacketPayload {
	public static final Type<TerminalOpenC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("terminal_open_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalOpenC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(TerminalOpenC2SPayload::write, TerminalOpenC2SPayload::new);

	public TerminalOpenC2SPayload {
		if (protocolVersion < 0 || requestSequence < 0L) {
			throw new IllegalArgumentException(
				"protocolVersion and requestSequence must be non-negative"
			);
		}
	}

	private TerminalOpenC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readVarLong());
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
	}

	@Override
	public Type<TerminalOpenC2SPayload> type() {
		return TYPE;
	}
}
