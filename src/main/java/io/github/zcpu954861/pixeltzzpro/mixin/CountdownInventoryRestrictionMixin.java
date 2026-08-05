package io.github.zcpu954861.pixeltzzpro.mixin;

import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.RestrictedAction;
import io.github.zcpu954861.pixeltzzpro.server.CountdownRestrictionEvents;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packet-level close of vanilla drop/swap/inventory gaps not covered by Fabric interaction events. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CountdownInventoryRestrictionMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void pixelTzz$restrictPlayerAction(
		final ServerboundPlayerActionPacket packet,
		final CallbackInfo callback
	) {
		RestrictedAction action = switch (packet.getAction()) {
			case DROP_ALL_ITEMS, DROP_ITEM -> RestrictedAction.ITEM_DROP;
			case SWAP_ITEM_WITH_OFFHAND -> RestrictedAction.ITEM_SWAP;
			default -> null;
		};
		if (action != null && CountdownRestrictionEvents.blocked(this.player, action)) {
			callback.cancel();
			this.player.containerMenu.sendAllDataToRemote();
		}
	}

	@Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
	private void pixelTzz$restrictCarriedItem(
		final ServerboundSetCarriedItemPacket packet,
		final CallbackInfo callback
	) {
		if (CountdownRestrictionEvents.blocked(this.player, RestrictedAction.ITEM_SWAP)) {
			callback.cancel();
			this.player.containerMenu.sendAllDataToRemote();
		}
	}

	@Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
	private void pixelTzz$restrictContainerClick(
		final ServerboundContainerClickPacket packet,
		final CallbackInfo callback
	) {
		cancelInventory(callback);
	}

	@Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
	private void pixelTzz$restrictContainerButton(
		final ServerboundContainerButtonClickPacket packet,
		final CallbackInfo callback
	) {
		cancelInventory(callback);
	}

	@Inject(method = "handleContainerSlotStateChanged", at = @At("HEAD"), cancellable = true)
	private void pixelTzz$restrictContainerSlotState(
		final ServerboundContainerSlotStateChangedPacket packet,
		final CallbackInfo callback
	) {
		cancelInventory(callback);
	}

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void pixelTzz$restrictCreativeSlot(
		final ServerboundSetCreativeModeSlotPacket packet,
		final CallbackInfo callback
	) {
		cancelInventory(callback);
	}

	private void cancelInventory(final CallbackInfo callback) {
		if (CountdownRestrictionEvents.blocked(this.player, RestrictedAction.INVENTORY)) {
			callback.cancel();
			this.player.containerMenu.sendAllDataToRemote();
		}
	}
}
