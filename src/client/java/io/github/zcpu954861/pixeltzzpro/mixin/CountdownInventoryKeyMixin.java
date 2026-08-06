package io.github.zcpu954861.pixeltzzpro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Consumes only the vanilla inventory binding while its effective server projection is blocked. */
@Mixin(Minecraft.class)
public abstract class CountdownInventoryKeyMixin {
	@Shadow
	@Final
	public Options options;

	@WrapOperation(
		method = "handleKeybinds",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"
		)
	)
	private boolean pixelTzz$restrictInventoryKey(
		final KeyMapping key,
		final Operation<Boolean> original
	) {
		boolean clicked = original.call(key);
		return clicked && (
			key != this.options.keyInventory || !ClientCountdownRuntime.inventoryBlocked()
		);
	}
}
