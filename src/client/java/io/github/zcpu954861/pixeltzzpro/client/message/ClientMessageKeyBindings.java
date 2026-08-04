package io.github.zcpu954861.pixeltzzpro.client.message;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Local-only V3B controls.
 *
 * <p>The completion key starts unbound and can only ask the already-authorized client playback
 * service to reveal final content. It never sends an acknowledgement or advances a server
 * callback.</p>
 */
public final class ClientMessageKeyBindings {
	private static KeyMapping completeAnimation;
	private static Runnable completeAction = () -> {
	};

	private ClientMessageKeyBindings() {
	}

	public static void register(final Runnable action) {
		if (completeAnimation != null) {
			throw new IllegalStateException("V3B message key bindings are already registered");
		}
		completeAction = Objects.requireNonNull(action, "action");
		KeyMapping.Category category = KeyMapping.Category.register(
			PixelTzzPro.id("message")
		);
		completeAnimation = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.pixel_tzz_pro.message.complete",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				category
			)
		);
		ClientTickEvents.END_CLIENT_TICK.register(ClientMessageKeyBindings::tick);
	}

	public static boolean isCompleteAnimationUnbound() {
		return completeAnimation == null || completeAnimation.isUnbound();
	}

	private static void tick(final Minecraft client) {
		if (completeAnimation == null) {
			return;
		}
		while (completeAnimation.consumeClick()) {
			if (client.player != null && client.gui.screen() == null) {
				completeAction.run();
			}
		}
	}
}
