package io.github.zcpu954861.pixeltzzpro.mixin;

import java.util.List;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only probe used solely to reserve the vanilla sound-subtitle rectangle. */
@Mixin(SubtitleOverlay.class)
public interface SubtitleOverlayAccessor {
	@Accessor("subtitles")
	List<?> pixelTzzPro$getSubtitles();
}
