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

	/**
	 * Marks a registered phase, task, result, or other definition as a formal name.
	 *
	 * <p>Data packs register clean names without punctuation. Presentation code adds the semantic
	 * brackets only where the name is being introduced as a definition, which prevents duplicated
	 * punctuation when the same component is reused elsewhere.</p>
	 */
	static Component formalName(final Component name) {
		return wrap(name, "『", "』");
	}

	/**
	 * Marks the object currently being inspected or operated on.
	 */
	static Component currentObject(final Component name) {
		return wrap(name, "「", "」");
	}

	private static Component wrap(
		final Component name,
		final String opening,
		final String closing
	) {
		String visible = name.getString();
		if (visible.isBlank() || (visible.startsWith(opening) && visible.endsWith(closing))) {
			return name;
		}
		return Component.literal(opening).append(name.copy()).append(closing);
	}
}
