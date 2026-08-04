package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.client.animation.LoadingActionBarAnimator;
import io.github.zcpu954861.pixeltzzpro.client.animation.OperationSubtitleAnimator;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageKeyBindings;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageAssetReporter;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessagePlayback;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessagePreferences;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageWireState;
import io.github.zcpu954861.pixeltzzpro.client.message.MessageHudOverlay;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState.ConnectionStatus;
import io.github.zcpu954861.pixeltzzpro.client.screen.DataDrivenPageScreen;
import io.github.zcpu954861.pixeltzzpro.client.screen.ForcedFlowPauseScreen;
import io.github.zcpu954861.pixeltzzpro.client.screen.LivePageSupervisor;
import io.github.zcpu954861.pixeltzzpro.client.screen.OptionsMenuEntry;
import io.github.zcpu954861.pixeltzzpro.client.screen.PauseMenuEntry;
import io.github.zcpu954861.pixeltzzpro.client.screen.TransitioningConsoleScreen;
import io.github.zcpu954861.pixeltzzpro.client.ui.PixelTzzRenderPipelines;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostUiStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostSubtitleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalBindingDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalInvalidationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.gui.components.toasts.SystemToast;

/**
 * Client bootstrap. Rendering and animation remain client-side; state authority remains server-side.
 */
public final class PixelTzzProClient implements ClientModInitializer {
	private static Boolean lastModUiVisible;
	private static boolean messagePhysicalConnectionActive;
	private static final String MOD_VERSION = FabricLoader.getInstance()
		.getModContainer("pixel-tzz-pro")
		.orElseThrow()
		.getMetadata()
		.getVersion()
		.getFriendlyString();

	@Override
	public void onInitializeClient() {
		ClientMessagePreferences.initialize();
		PixelTzzRenderPipelines.register();
		MessageHudOverlay.register();
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			SystemToast.forceHide(
				client.gui.toastManager(),
				SystemToast.SystemToastId.UNSECURE_SERVER_WARNING
			);
			ClientSessionState.beginConnection();
			ClientPageState.beginConnection();
			ClientConsoleState.beginConnection();
			ClientTimelineState.beginConnection();
			LivePageSupervisor.reset();
			LoadingActionBarAnimator.reset();
			OperationSubtitleAnimator.reset();
			if (!messagePhysicalConnectionActive) {
				messagePhysicalConnectionActive = true;
				ClientMessageAssetReporter.beginConnection();
				ClientMessageWireState.beginConnection();
				ClientMessagePlayback.reset(client);
			}
			lastModUiVisible = null;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			LivePageSupervisor.reset();
			ClientConsoleState.disconnect();
			ClientTimelineState.disconnect();
			ClientPageState.disconnect();
			ClientSessionState.disconnect();
			LoadingActionBarAnimator.reset();
			OperationSubtitleAnimator.reset();
			ClientMessageAssetReporter.disconnect();
			ClientMessageWireState.disconnect();
			ClientMessagePlayback.reset(client);
			messagePhysicalConnectionActive = false;
			lastModUiVisible = null;
		});

		ClientPlayNetworking.registerGlobalReceiver(HandshakeS2CPayload.TYPE, (payload, context) -> {
			ClientSessionState.acceptHandshake(payload);
			if (ClientPlayNetworking.canSend(HandshakeC2SPayload.TYPE)) {
				ClientPlayNetworking.send(new HandshakeC2SPayload(NetworkProtocol.CURRENT_VERSION, MOD_VERSION));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SessionSnapshotS2CPayload.TYPE, (payload, context) -> {
			boolean accepted = ClientSessionState.acceptSnapshot(payload);
			if (accepted) {
				ClientConsoleState.acceptSessionProjection(payload);
				ClientTimelineState.probe();
			}
			if (accepted && payload.schemaCompatible() && payload.animateLoad()) {
				LoadingActionBarAnimator.enqueue(payload.loadSequence(), Component.translatable("pixel_tzz_pro.load.success"));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(
			PreviewCatalogS2CPayload.TYPE,
			(payload, context) -> ClientPageState.acceptCatalog(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			PageBundleS2CPayload.TYPE,
			(payload, context) -> ClientPageState.acceptPageBundle(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			TerminalBindingDeltaS2CPayload.TYPE,
			(payload, context) -> ClientPageState.acceptTerminalBindingDelta(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			TerminalInvalidationS2CPayload.TYPE,
			(payload, context) -> ClientPageState.acceptTerminalInvalidation(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			OperationResultS2CPayload.TYPE,
			(payload, context) -> {
				ClientPageState.acceptOperationResult(payload);
				ClientConsoleState.acceptOperationResult(payload);
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ForcedPageReleaseS2CPayload.TYPE,
			(payload, context) -> {
				boolean accepted = ClientPageState.acceptForcedRelease(payload);
				if (
					accepted
						&& (
							payload.reason().equals("member_ready")
								|| payload.reason().equals("readiness_completed")
						)
				) {
					OperationSubtitleAnimator.enqueue("准备状态已锁定");
				}
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ConsoleSnapshotS2CPayload.TYPE,
			(payload, context) -> ClientConsoleState.acceptConsole(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			TargetSnapshotS2CPayload.TYPE,
			(payload, context) -> ClientConsoleState.acceptTargets(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ConfirmationS2CPayload.TYPE,
			(payload, context) -> ClientConsoleState.acceptConfirmation(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			TimelineViewS2CPayload.TYPE,
			(payload, context) -> ClientTimelineState.accept(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			HostSubtitleS2CPayload.TYPE,
			(payload, context) -> OperationSubtitleAnimator.enqueue(payload.message())
		);
		ClientPlayNetworking.registerGlobalReceiver(
			MessageAssetManifestS2CPayload.TYPE,
			(payload, context) -> ClientMessageAssetReporter.accept(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			MessagePlanS2CPayload.TYPE,
			(payload, context) -> ClientMessageWireState.accept(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			MessageFieldDeltaS2CPayload.TYPE,
			(payload, context) -> ClientMessageWireState.accept(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			MessageNodeAppendS2CPayload.TYPE,
			(payload, context) -> ClientMessageWireState.accept(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			MessageControlS2CPayload.TYPE,
			(payload, context) -> ClientMessageWireState.accept(payload)
		);

		// Announce resource-reload start before the fresh asset report emitted in the same tick, so
		// the server invalidates the old observation first and cannot leave the new one pending.
		ClientTickEvents.END_CLIENT_TICK.register(ClientMessageWireState::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ClientMessageAssetReporter::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ClientMessagePlayback::tick);
		ClientTickEvents.END_CLIENT_TICK.register(LivePageSupervisor::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean visible = client.gui.screen() instanceof TransitioningConsoleScreen
				|| client.gui.screen() instanceof DataDrivenPageScreen
				|| client.gui.screen() instanceof ForcedFlowPauseScreen;
			if (
				!java.util.Objects.equals(lastModUiVisible, visible)
					&& ClientPlayNetworking.canSend(HostUiStateC2SPayload.TYPE)
			) {
				ClientPlayNetworking.send(new HostUiStateC2SPayload(visible));
				lastModUiVisible = visible;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ClientSessionState.snapshot().status() == ConnectionStatus.READY) {
				SystemToast.forceHide(
					client.gui.toastManager(),
					SystemToast.SystemToastId.UNSECURE_SERVER_WARNING
				);
			}
		});
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
			.registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return PixelTzzPro.id("page_resource_preflight");
					}

					@Override
					public void onResourceManagerReload(final ResourceManager resourceManager) {
						ClientMessageWireState.beginResourceReload();
						try {
							ClientPageState.retryResourcePreflight();
						} finally {
							ClientMessageWireState.finishResourceReload();
							ClientMessageAssetReporter.resourcesReloaded();
						}
					}
				}
		);
		ClientMessageKeyBindings.register(ClientMessagePlayback::manualComplete);
		PauseMenuEntry.register();
		OptionsMenuEntry.register();
	}
}
