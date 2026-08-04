package io.github.zcpu954861.pixeltzzpro.client.animation;

import io.github.zcpu954861.pixeltzzpro.client.message.ClientBuiltinMessages;
import net.minecraft.client.Minecraft;

/**
 * Compatibility entry point for built-in operation feedback. V3B owns its queue and frame state.
 */
public final class OperationSubtitleAnimator {
	private OperationSubtitleAnimator() {
	}

	public static void enqueue(final String message) {
		ClientBuiltinMessages.enqueueOperationSubtitle(message);
	}

	public static void reset() {
	}

	public static void tick(final Minecraft client) {
	}
}
