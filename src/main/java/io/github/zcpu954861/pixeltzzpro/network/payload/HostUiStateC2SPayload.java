package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells the server whether the player is currently inside a mod-owned game UI.
 */
public record HostUiStateC2SPayload(boolean visible) implements CustomPacketPayload {
	public static final Type<HostUiStateC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("host_ui_state_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HostUiStateC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HostUiStateC2SPayload::write, HostUiStateC2SPayload::new);

	private HostUiStateC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readBoolean());
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeBoolean(this.visible);
	}

	@Override
	public Type<HostUiStateC2SPayload> type() {
		return TYPE;
	}
}
