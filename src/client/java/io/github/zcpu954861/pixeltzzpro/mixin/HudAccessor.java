package io.github.zcpu954861.pixeltzzpro.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only ownership probe for V3B's vanilla Title/Subtitle/ActionBar adapters.
 */
@Mixin(Hud.class)
public interface HudAccessor {
	@Accessor("overlayMessageString")
	Component pixelTzzPro$getOverlayMessage();

	@Accessor("overlayMessageTime")
	int pixelTzzPro$getOverlayMessageTime();

	@Accessor("title")
	Component pixelTzzPro$getTitle();

	@Accessor("subtitle")
	Component pixelTzzPro$getSubtitle();

	@Accessor("titleTime")
	int pixelTzzPro$getTitleTime();
}
