package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Requests the current previewable page catalog.
 */
public record PreviewCatalogRequestC2SPayload() implements CustomPacketPayload {
	public static final PreviewCatalogRequestC2SPayload INSTANCE = new PreviewCatalogRequestC2SPayload();
	public static final Type<PreviewCatalogRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("preview_catalog_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PreviewCatalogRequestC2SPayload> STREAM_CODEC =
		StreamCodec.unit(INSTANCE);

	@Override
	public Type<PreviewCatalogRequestC2SPayload> type() {
		return TYPE;
	}
}
