package io.github.zcpu954861.pixeltzzpro.client.screen;

import com.google.gson.JsonElement;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.ActivePage;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Local input values for a server-authored page. Authority remains in {@link ActivePage}.
 */
public final class LivePageSession implements PageScreenSession {
	private final Map<String, JsonElement> uiValues = new LinkedHashMap<>();
	private UUID pageInstanceId;

	@Override
	public BindingContext context(final ActivePage active) {
		resetFor(active);
		return active.serverContext();
	}

	@Override
	public JsonElement uiValue(final String nodeId) {
		JsonElement value = this.uiValues.get(nodeId);
		return value == null ? null : value.deepCopy();
	}

	@Override
	public void setUiValue(final String nodeId, final JsonElement value) {
		this.uiValues.put(
			Objects.requireNonNull(nodeId, "nodeId"),
			Objects.requireNonNull(value, "value").deepCopy()
		);
	}

	@Override
	public JsonElement initialFieldValue(
		final ActivePage active,
		final Identifier fieldId
	) {
		resetFor(active);
		return active.serverContext()
			.resolve("fields/" + fieldId)
			.orElse(null);
	}

	private void resetFor(final ActivePage active) {
		if (active.instanceId().equals(this.pageInstanceId)) {
			return;
		}
		this.pageInstanceId = active.instanceId();
		this.uiValues.clear();
	}
}
