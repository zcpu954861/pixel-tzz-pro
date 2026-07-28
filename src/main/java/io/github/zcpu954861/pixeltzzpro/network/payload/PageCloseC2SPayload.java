package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Releases one client page instance and its server-side preview subscription.
 */
public record PageCloseC2SPayload(UUID instanceId) implements CustomPacketPayload {
	public static final Type<PageCloseC2SPayload> TYPE = new Type<>(PixelTzzPro.id("page_close_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PageCloseC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(PageCloseC2SPayload::write, PageCloseC2SPayload::new);

	public PageCloseC2SPayload {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
	}

	private PageCloseC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readUUID());
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.instanceId);
	}

	@Override
	public Type<PageCloseC2SPayload> type() {
		return TYPE;
	}
}
