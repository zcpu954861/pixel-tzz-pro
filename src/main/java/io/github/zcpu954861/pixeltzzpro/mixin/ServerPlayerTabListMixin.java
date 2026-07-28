package io.github.zcpu954861.pixeltzzpro.mixin;

import io.github.zcpu954861.pixeltzzpro.server.TabListDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies a display-only TAB label without claiming vanilla scoreboard-team ownership.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerTabListMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void pixelTzzPro$displayName(
		final CallbackInfoReturnable<Component> callback
	) {
		Component displayName = TabListDisplay.resolve((ServerPlayer)(Object)this);
		if (displayName != null) {
			callback.setReturnValue(displayName);
		}
	}

	@Inject(method = "getTabListOrder", at = @At("HEAD"), cancellable = true)
	private void pixelTzzPro$listOrder(
		final CallbackInfoReturnable<Integer> callback
	) {
		callback.setReturnValue(TabListDisplay.resolveOrder((ServerPlayer)(Object)this));
	}
}
