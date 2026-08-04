package io.github.zcpu954861.pixeltzzpro.client.message;

import net.minecraft.client.multiplayer.chat.GuiMessage;

/** Narrow bridge implemented by the ChatComponent projection mixin. */
public interface ChatLineProjectionAccess {
	MutationResult pixelTzzPro$replaceProjection(
		GuiMessage previous,
		GuiMessage replacement,
		ChatLineProjection projection
	);

	MutationResult pixelTzzPro$replaceWithClean(
		GuiMessage previous,
		GuiMessage replacement
	);

	MutationResult pixelTzzPro$removeMessage(GuiMessage message);

	MutationResult pixelTzzPro$setForceOpaque(GuiMessage message, boolean forceOpaque);

	boolean pixelTzzPro$projectionHooksOperational();

	enum MutationResult {
		UPDATED,
		ENTRY_GONE,
		CAPABILITY_FAILURE
	}
}
