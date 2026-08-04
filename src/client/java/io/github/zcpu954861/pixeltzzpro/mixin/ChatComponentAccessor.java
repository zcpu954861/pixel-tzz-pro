package io.github.zcpu954861.pixeltzzpro.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Narrow client-only bridge used to update one V3B system-chat entry in place.
 *
 * <p>The animation never appends one vanilla message per frame. It replaces the exact
 * {@link GuiMessage} record that was inserted for the presentation and asks vanilla to rebuild
 * its wrapped-line cache. Player chat, signatures, deletion state, and the rest of the history
 * remain untouched.</p>
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> pixelTzzPro$getAllMessages();

	@Invoker("refreshTrimmedMessages")
	void pixelTzzPro$refreshTrimmedMessages();
}
