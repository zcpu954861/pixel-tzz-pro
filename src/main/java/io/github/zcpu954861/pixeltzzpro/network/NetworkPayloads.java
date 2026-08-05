package io.github.zcpu954861.pixeltzzpro.network;

import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostUiStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostSubtitleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CancelConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CommitConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownCheckpointSoundS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ExclusiveChoiceMutationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageCloseC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PrepareOperationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalBindingDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalInvalidationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalIntentC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalOpenC2SPayload;
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
		PayloadTypeRegistry.serverboundPlay()
			.register(PrepareOperationC2SPayload.TYPE, PrepareOperationC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(CommitConfirmationC2SPayload.TYPE, CommitConfirmationC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(CancelConfirmationC2SPayload.TYPE, CancelConfirmationC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(FlowActionC2SPayload.TYPE, FlowActionC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(
				ExclusiveChoiceMutationC2SPayload.TYPE,
				ExclusiveChoiceMutationC2SPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.serverboundPlay()
			.register(ConsoleRequestC2SPayload.TYPE, ConsoleRequestC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(TargetSnapshotRequestC2SPayload.TYPE, TargetSnapshotRequestC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(HostUiStateC2SPayload.TYPE, HostUiStateC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(TimelineViewRequestC2SPayload.TYPE, TimelineViewRequestC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(TerminalOpenC2SPayload.TYPE, TerminalOpenC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(TerminalIntentC2SPayload.TYPE, TerminalIntentC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
			.register(
				MessageCapabilitiesC2SPayload.TYPE,
				MessageCapabilitiesC2SPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.serverboundPlay()
			.register(
				MessageAssetReportC2SPayload.TYPE,
				MessageAssetReportC2SPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.serverboundPlay()
			.register(
				MessageScreenStateC2SPayload.TYPE,
				MessageScreenStateC2SPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.serverboundPlay()
			.register(HudResyncRequestC2SPayload.TYPE, HudResyncRequestC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HandshakeS2CPayload.TYPE, HandshakeS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionSnapshotS2CPayload.TYPE, SessionSnapshotS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(PreviewCatalogS2CPayload.TYPE, PreviewCatalogS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PageBundleS2CPayload.TYPE, PageBundleS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(ConfirmationS2CPayload.TYPE, ConfirmationS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(OperationResultS2CPayload.TYPE, OperationResultS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(ConsoleSnapshotS2CPayload.TYPE, ConsoleSnapshotS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(TargetSnapshotS2CPayload.TYPE, TargetSnapshotS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(ForcedPageReleaseS2CPayload.TYPE, ForcedPageReleaseS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(TimelineViewS2CPayload.TYPE, TimelineViewS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(HostSubtitleS2CPayload.TYPE, HostSubtitleS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(
				TerminalBindingDeltaS2CPayload.TYPE,
				TerminalBindingDeltaS2CPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.clientboundPlay()
			.register(
				TerminalInvalidationS2CPayload.TYPE,
				TerminalInvalidationS2CPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.clientboundPlay()
			.register(MessagePlanS2CPayload.TYPE, MessagePlanS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(
				MessageAssetManifestS2CPayload.TYPE,
				MessageAssetManifestS2CPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.clientboundPlay()
			.register(
				MessageFieldDeltaS2CPayload.TYPE,
				MessageFieldDeltaS2CPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.clientboundPlay()
			.register(
				MessageNodeAppendS2CPayload.TYPE,
				MessageNodeAppendS2CPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.clientboundPlay()
			.register(
				MessageControlS2CPayload.TYPE,
				MessageControlS2CPayload.STREAM_CODEC
			);
		PayloadTypeRegistry.clientboundPlay().register(HudReplaceS2CPayload.TYPE, HudReplaceS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HudPatchS2CPayload.TYPE, HudPatchS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HudClearS2CPayload.TYPE, HudClearS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CountdownReplaceS2CPayload.TYPE, CountdownReplaceS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CountdownPatchS2CPayload.TYPE, CountdownPatchS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CountdownClearS2CPayload.TYPE, CountdownClearS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
			CountdownCheckpointSoundS2CPayload.TYPE,
			CountdownCheckpointSoundS2CPayload.STREAM_CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(HudPreviewReplaceS2CPayload.TYPE, HudPreviewReplaceS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HudPreviewClearS2CPayload.TYPE, HudPreviewClearS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HudPreviewRequestC2SPayload.TYPE, HudPreviewRequestC2SPayload.STREAM_CODEC);
	}
}
