package io.github.zcpu954861.pixeltzzpro.network;

import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageCloseC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers every payload codec before either logical side installs handlers.
 */
public final class NetworkPayloads {
	private NetworkPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(HandshakeC2SPayload.TYPE, HandshakeC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(PreviewCatalogRequestC2SPayload.TYPE, PreviewCatalogRequestC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(PreviewPageRequestC2SPayload.TYPE, PreviewPageRequestC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PageCloseC2SPayload.TYPE, PageCloseC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(ResourceReportC2SPayload.TYPE, ResourceReportC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HandshakeS2CPayload.TYPE, HandshakeS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionSnapshotS2CPayload.TYPE, SessionSnapshotS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(PreviewCatalogS2CPayload.TYPE, PreviewCatalogS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PageBundleS2CPayload.TYPE, PageBundleS2CPayload.STREAM_CODEC);
	}
}
