package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveOverride;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared, client-neutral style cascade for data-driven UI nodes.
 *
 * <p>Interactive states may only replace paint properties. Layout-affecting values stay frozen at
 * their normal-state values so hover, focus, and press feedback cannot move neighboring controls.
 */
public final class UiStyleRuntime {
	private static final Set<String> INTERACTION_PAINT_PROPERTIES = Set.of(
		"background_color",
		"surface_color",
		"border_color",
		"text_color",
		"secondary_text_color",
		"accent_color",
		"track_color",
		"fill_color",
		"shadow_color",
		"border_width",
		"corner_radius",
		"opacity",
		"shadow",
		"navigation_arrows",
		"arrow_travel"
	);

	private UiStyleRuntime() {
	}

	public static Map<String, StyleValue> resolve(
		final ThemeDefinition theme,
		final NodeDefinition node,
		final ResponsiveTier tier,
		final StyleState state,
		final boolean interactive
	) {
		if (node == null) {
			// Virtualized Repeat spacers are presentation-only and intentionally have no definition.
			return Map.of();
		}
		ResponsiveOverride responsive = node.responsive().get(tier);
		StyleDefinition style = effectiveStyleName(node, tier, theme.styles().keySet())
			.map(theme.styles()::get)
			.orElse(null);
		return resolve(
			style,
			state,
			node.styleOverrides(),
			responsive == null ? Map.of() : responsive.styleOverrides(),
			interactive
		);
	}

	public static Map<String, StyleValue> resolve(
		final StyleDefinition style,
		final StyleState state,
		final Map<String, StyleValue> nodeOverrides,
		final Map<String, StyleValue> responsiveOverrides,
		final boolean interactive
	) {
		Map<String, StyleValue> result = new LinkedHashMap<>();
		if (style != null) {
			Map<String, StyleValue> normal = style.states().get(StyleState.NORMAL);
			if (normal != null) {
				result.putAll(normal);
			}
			if (state != StyleState.NORMAL) {
				Map<String, StyleValue> active = style.states().get(state);
				if (active != null) {
					active.forEach(
						(name, value) -> {
							if (!interactive || INTERACTION_PAINT_PROPERTIES.contains(name)) {
								result.put(name, value);
							}
						}
					);
				}
			}
		}
		result.putAll(nodeOverrides);
		result.putAll(responsiveOverrides);
		return Map.copyOf(result);
	}

	public static Optional<String> effectiveStyleName(
		final NodeDefinition node,
		final ResponsiveTier tier,
		final Set<String> availableStyles
	) {
		ResponsiveOverride responsive = node.responsive().get(tier);
		Optional<String> explicit = responsive != null && responsive.style().isPresent()
			? responsive.style()
			: node.style();
		if (explicit.isPresent()) {
			return explicit;
		}
		if (node.content() instanceof ButtonContent button) {
			boolean navigation = button.action().type() == ActionType.LOCAL
				&& button.action()
					.name()
					.filter(name -> name.equals("back") || name.equals("close"))
					.isPresent();
			if (navigation) {
				return availableStyles.contains("navigation_button")
					? Optional.of("navigation_button")
					: Optional.empty();
			}
			if (availableStyles.contains("primary_button")) {
				return Optional.of("primary_button");
			}
		}
		return Optional.empty();
	}

	public static StyleState interactionState(
		final boolean active,
		final boolean pressed,
		final boolean hovered,
		final boolean focused
	) {
		if (!active) {
			return StyleState.DISABLED;
		}
		if (pressed) {
			return StyleState.PRESSED;
		}
		if (hovered) {
			return StyleState.HOVER;
		}
		return focused ? StyleState.FOCUSED : StyleState.NORMAL;
	}

	public static Optional<String> stringValue(
		final Map<String, StyleValue> values,
		final String name
	) {
		StyleValue value = values.get(name);
		if (value == null) {
			return Optional.empty();
		}
		try {
			JsonElement json = JsonParser.parseString(value.canonicalJson());
			return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()
				? Optional.of(json.getAsString())
				: Optional.empty();
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}
}
