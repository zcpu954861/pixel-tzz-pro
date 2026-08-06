package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.client.animation.LoadingActionBarAnimator;
import io.github.zcpu954861.pixeltzzpro.client.animation.OperationSubtitleAnimator;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageKeyBindings;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageAssetReporter;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessagePlayback;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessagePreferences;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageWireState;
import io.github.zcpu954861.pixeltzzpro.client.message.MessageHudOverlay;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownPreferences;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownRuntime;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownRuntime.FrameAcceptance;
import io.github.zcpu954861.pixeltzzpro.client.countdown.CountdownOverlay;
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
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownCheckpointSoundS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownResyncRequestC2SPayload;
import java.util.UUID;
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
import net.minecraft.client.Minecraft;

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
		ClientCountdownPreferences.initialize();
		PixelTzzRenderPipelines.register();
		MessageHudOverlay.register();
		CountdownOverlay.register();
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
			ClientCountdownRuntime.reset();
			CountdownOverlay.reset();
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
			ClientCountdownRuntime.reset();
			CountdownOverlay.reset();
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
				if (payload.currentPlayerHost()) {
					ClientPageState.releaseForcedPageForHost(payload.stateRevision());
				}
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
		ClientPlayNetworking.registerGlobalReceiver(
			CountdownReplaceS2CPayload.TYPE,
			(payload, context) -> {
				long receivedAtNanos = System.nanoTime();
				FrameAcceptance result = ClientCountdownRuntime.acceptReplace(
					payload.gameInstanceId(),
					payload.definitionGeneration(),
					payload.epoch(),
					payload.contextVersion(),
					payload.sequence(),
					payload.snapshotJson(),
					receivedAtNanos,
					estimatedOneWayDelayNanos()
				);
				handleFrameResult(
					payload.gameInstanceId(),
					payload.definitionGeneration(),
					payload.epoch(),
					payload.contextVersion(),
					result,
					receivedAtNanos
				);
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			CountdownPatchS2CPayload.TYPE,
			(payload, context) -> {
				long receivedAtNanos = System.nanoTime();
				FrameAcceptance result = ClientCountdownRuntime.acceptPatch(
					payload.gameInstanceId(),
					payload.definitionGeneration(),
					payload.epoch(),
					payload.contextVersion(),
					payload.baseSequence(),
					payload.sequence(),
					payload.patchJson(),
					receivedAtNanos,
					estimatedOneWayDelayNanos()
				);
				handleFrameResult(
					payload.gameInstanceId(),
					payload.definitionGeneration(),
					payload.epoch(),
					payload.contextVersion(),
					result,
					receivedAtNanos
				);
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			CountdownClearS2CPayload.TYPE,
			(payload, context) -> ClientCountdownRuntime.acceptClear(
				payload.gameInstanceId(),
				payload.definitionGeneration(),
				payload.epoch(),
				payload.contextVersion(),
				payload.sequence(),
				System.nanoTime()
			)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			CountdownCheckpointSoundS2CPayload.TYPE,
			(payload, context) -> ClientCountdownRuntime.playCheckpointSound(
				payload.countdownInstanceId(),
				payload.stateVersion(),
				payload.checkpointId(),
				payload.soundId(),
				payload.volume(),
				payload.pitch()
			)
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

	private static void handleFrameResult(
		final UUID game,
		final long generation,
		final long epoch,
		final long context,
		final FrameAcceptance result,
		final long receivedAtNanos
	) {
		if (!result.accepted() && result.requiresResync()) {
			requestResync(game, generation, epoch, context, receivedAtNanos);
		}
	}

	private static long estimatedOneWayDelayNanos() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null && client.player != null) {
			var playerInfo = client.getConnection().getPlayerInfo(client.player.getUUID());
			if (playerInfo != null) {
				long latencyMillis = Math.clamp((long)playerInfo.getLatency(), 0L, 1_000L);
				return latencyMillis * 500_000L;
			}
		}
		return 25_000_000L;
	}

	private static void requestResync(
		final UUID game,
		final long generation,
		final long epoch,
		final long context,
		final long nowNanos
	) {
		if (!ClientPlayNetworking.canSend(CountdownResyncRequestC2SPayload.TYPE)) {
			return;
		}
		var applied = ClientCountdownRuntime.prepareResync(
			game,
			generation,
			epoch,
			context,
			nowNanos
		);
		if (applied.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new CountdownResyncRequestC2SPayload(
			game,
			generation,
			epoch,
			context,
			applied.orElseThrow()
		));
	}
}
