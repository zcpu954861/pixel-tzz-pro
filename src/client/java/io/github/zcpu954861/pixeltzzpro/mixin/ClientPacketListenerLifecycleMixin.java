package io.github.zcpu954861.pixeltzzpro.mixin;

import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageLifecycleTracker;
import io.github.zcpu954861.pixeltzzpro.client.message.ClientMessageLifecycleTracker.Reason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the vanilla reason before both respawn paths replace the local player object. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerLifecycleMixin {
	@Inject(method = "handleRespawn", at = @At("HEAD"))
	private void pixelTzzPro$recordMessageLifecycle(
		final ClientboundRespawnPacket packet,
		final CallbackInfo callback
	) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Identifier previous = client.level.dimension().identifier();
		Identifier current = packet.commonPlayerSpawnInfo().dimension().identifier();
		Reason reason = packet.dataToKeep() == ClientboundRespawnPacket.KEEP_ALL_DATA
			? Reason.DIMENSION_TRANSFER
			: Reason.RESPAWN;
		ClientMessageLifecycleTracker.record(reason, previous, current);
	}
}
