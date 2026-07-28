package io.github.zcpu954861.pixeltzzpro.client.screen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Fail-closed display conversion for bounded server-authored component JSON.
 */
final class ConsoleText {
	private ConsoleText() {
	}

	static Component parse(final String json, final String fallback) {
		try {
			return ComponentSerialization.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
				.result()
				.orElseGet(() -> Component.literal(fallback));
		} catch (RuntimeException error) {
			return Component.literal(fallback);
		}
	}
}
