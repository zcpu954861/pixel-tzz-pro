package io.github.zcpu954861.pixeltzzpro.client.screen;

import com.google.gson.JsonElement;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.ActivePage;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import net.minecraft.resources.Identifier;

/**
 * Per-screen values and presentation capabilities, separated from preview/live implementations.
 */
public interface PageScreenSession {
	BindingContext context(ActivePage active);

	JsonElement uiValue(String nodeId);

	void setUiValue(String nodeId, JsonElement value);

	default JsonElement initialFieldValue(
		final ActivePage active,
		final Identifier fieldId
	) {
		return null;
	}

	default boolean previewChrome() {
		return false;
	}

	default String profileDisplayName() {
		return "";
	}

	default int playerCountValue() {
		return 0;
	}

	default Viewport viewport() {
		return Viewport.STANDARD;
	}

	/**
	 * Preview explicitly forces a simulated tier; live pages return {@code null} for automatic
	 * width-based selection.
	 */
	default ResponsiveTier responsiveTier() {
		if (!previewChrome()) {
			return null;
		}
		return switch (viewport()) {
			case COMPACT -> ResponsiveTier.COMPACT;
			case STANDARD -> ResponsiveTier.STANDARD;
			case WIDE -> ResponsiveTier.WIDE;
		};
	}

	default boolean reducedMotion() {
		return false;
	}

	default boolean highContrast() {
		return false;
	}

	default boolean defaultFont() {
		return false;
	}

	default boolean debugLayout() {
		return false;
	}

	default void cycleProfile() {
	}

	default void cyclePlayerCount() {
	}

	default void cycleViewport() {
	}

	default void toggleReducedMotion() {
	}

	default void toggleHighContrast() {
	}

	default void toggleDefaultFont() {
	}

	default void toggleDebugLayout() {
	}

	enum Viewport {
		COMPACT,
		STANDARD,
		WIDE;

		public Viewport next() {
			Viewport[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public String label() {
			return switch (this) {
				case COMPACT -> "Compact";
				case STANDARD -> "Standard";
				case WIDE -> "Wide";
			};
		}
	}
}
