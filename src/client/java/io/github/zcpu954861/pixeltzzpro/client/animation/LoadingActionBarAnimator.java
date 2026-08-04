package io.github.zcpu954861.pixeltzzpro.client.animation;

import io.github.zcpu954861.pixeltzzpro.client.message.ClientBuiltinMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Compatibility entry point for the built-in loading cue. V3B owns its queue and frame state.
 */
public final class LoadingActionBarAnimator {
	private LoadingActionBarAnimator() {
	}

	public static void enqueue(final long sequence, final Component finalText) {
		ClientBuiltinMessages.enqueueLoadingActionBar(sequence, finalText);
	}

	public static void reset() {
		ClientBuiltinMessages.resetConnection();
	}

	public static void tick(final Minecraft client) {
	}
}
