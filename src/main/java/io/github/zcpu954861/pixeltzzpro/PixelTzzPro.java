package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.network.NetworkPayloads;
import io.github.zcpu954861.pixeltzzpro.server.MessageCommands;
import io.github.zcpu954861.pixeltzzpro.server.MessageServerRuntime;
import io.github.zcpu954861.pixeltzzpro.server.PixelTzzServerRuntime;
import io.github.zcpu954861.pixeltzzpro.server.TimelineCommands;
import io.github.zcpu954861.pixeltzzpro.server.TimelineServerRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common bootstrap. It only wires protocol types and the authoritative server runtime.
 */
public final class PixelTzzPro implements ModInitializer {
	public static final String MOD_ID = "pixel-tzz-pro";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NetworkPayloads.register();
		MessageServerRuntime.register();
		MessageCommands.register();
		PixelTzzServerRuntime.register();
		TimelineServerRuntime.register();
		TimelineCommands.register();
		LOGGER.info("全员逃走中-扩展 foundation initialized");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
