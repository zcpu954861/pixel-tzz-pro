package io.github.zcpu954861.pixeltzzpro.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the two text blocks that participate in vanilla TAB-list geometry. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Accessor("header")
	Component pixelTzzPro$getHeader();

	@Accessor("footer")
	Component pixelTzzPro$getFooter();
}
