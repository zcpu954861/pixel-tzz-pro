package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.client.animation.LoadingActionBarAnimator;
import io.github.zcpu954861.pixeltzzpro.client.screen.PauseMenuEntry;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
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

/**
 * Client bootstrap. Rendering and animation remain client-side; state authority remains server-side.
 */
public final class PixelTzzProClient implements ClientModInitializer {
	private static final String MOD_VERSION = FabricLoader.getInstance()
		.getModContainer("pixel-tzz-pro")
		.orElseThrow()
		.getMetadata()
		.getVersion()
		.getFriendlyString();

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			ClientSessionState.beginConnection();
			ClientPageState.beginConnection();
			LoadingActionBarAnimator.reset();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientPageState.disconnect();
			ClientSessionState.disconnect();
			LoadingActionBarAnimator.reset();
		});

		ClientPlayNetworking.registerGlobalReceiver(HandshakeS2CPayload.TYPE, (payload, context) -> {
			ClientSessionState.acceptHandshake(payload);
			if (ClientPlayNetworking.canSend(HandshakeC2SPayload.TYPE)) {
				ClientPlayNetworking.send(new HandshakeC2SPayload(NetworkProtocol.CURRENT_VERSION, MOD_VERSION));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SessionSnapshotS2CPayload.TYPE, (payload, context) -> {
			if (ClientSessionState.acceptSnapshot(payload) && payload.schemaCompatible() && payload.animateLoad()) {
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

		ClientTickEvents.END_CLIENT_TICK.register(LoadingActionBarAnimator::tick);
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
			.registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return PixelTzzPro.id("page_resource_preflight");
					}

					@Override
					public void onResourceManagerReload(final ResourceManager resourceManager) {
						ClientPageState.retryResourcePreflight();
					}
				}
			);
		PauseMenuEntry.register();
	}
}
