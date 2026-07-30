package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import com.google.gson.JsonElement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable values exposed to one page evaluation.
 *
 * <p>JSON values are copied both when the context is built and when they are read, so mutable Gson
 * arrays and objects cannot leak between the preview model and the renderer.
 */
public final class BindingContext {
	private static final String FLOW_FIELDS_PREFIX = "flow/fields/";
	private static final String FIELDS_PREFIX = "fields/";
	private static final String PERSONAL_PREFIX = "personal/";
	private static final String PERSONAL_META_PREFIX = "personal_meta/";
	private static final String UI_PREFIX = "ui/";
	private static final String UI_VALUE_SUFFIX = "/value";
	private static final Set<String> NESTED_HISTORY_BINDINGS = Set.of(
		"actor.uuid",
		"actor.name"
	);

	private final Map<String, JsonElement> viewer;
	private final Map<String, JsonElement> session;
	private final Map<String, JsonElement> flow;
	private final Map<String, JsonElement> task;
	private final Map<String, JsonElement> history;
	private final Map<String, JsonElement> detail;
	private final Map<Identifier, JsonElement> fields;
	private final Map<Identifier, JsonElement> flowFields;
	private final Map<Identifier, JsonElement> personal;
	private final Map<Identifier, JsonElement> personalMetadata;
	private final Map<String, JsonElement> item;
	private final Map<String, JsonElement> ui;

	private BindingContext(final Builder builder) {
		this.viewer = copyValues(builder.viewer);
		this.session = copyValues(builder.session);
		this.flow = copyValues(builder.flow);
		this.task = copyValues(builder.task);
		this.history = copyValues(builder.history);
		this.detail = copyValues(builder.detail);
		this.fields = copyValues(builder.fields);
		this.flowFields = copyValues(builder.flowFields);
		this.personal = copyValues(builder.personal);
		this.personalMetadata = copyValues(builder.personalMetadata);
		this.item = copyValues(builder.item);
		this.ui = copyValues(builder.ui);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static BindingContext empty() {
		return builder().build();
	}

	public BindingContext withItem(final com.google.gson.JsonObject itemValue) {
		Objects.requireNonNull(itemValue, "itemValue");
		Builder copy = copyBuilder();
		copy.item.clear();
		itemValue.entrySet().forEach(entry -> copy.item(entry.getKey(), entry.getValue()));
		return copy.build();
	}

	public BindingContext withUiValue(final String nodeId, final JsonElement value) {
		Builder copy = copyBuilder();
		copy.ui(nodeId, value);
		return copy.build();
	}

	public BindingContext withUiValues(final Map<String, JsonElement> values) {
		Objects.requireNonNull(values, "values");
		Builder copy = copyBuilder();
		values.forEach(copy::ui);
		return copy.build();
	}

	/**
	 * Resolves one compiler-approved binding path.
	 *
	 * <p>The field identifier is parsed from the entire suffix. In particular,
	 * {@code fields/pixel_tzz:profile/route} retains {@code profile/route} as the identifier path
	 * instead of treating the slash as another binding segment.
	 */
	public Optional<JsonElement> resolve(final String path) {
		if (path == null || path.isEmpty()) {
			return Optional.empty();
		}
		JsonElement value;
		if (path.startsWith(FLOW_FIELDS_PREFIX)) {
			value = identifierValue(this.flowFields, path.substring(FLOW_FIELDS_PREFIX.length()));
		} else if (path.startsWith(FIELDS_PREFIX)) {
			value = identifierValue(this.fields, path.substring(FIELDS_PREFIX.length()));
		} else if (path.startsWith(PERSONAL_PREFIX)) {
			value = identifierValue(this.personal, path.substring(PERSONAL_PREFIX.length()));
		} else if (path.startsWith(PERSONAL_META_PREFIX)) {
			value = personalMetadataValue(
				this.personalMetadata,
				path.substring(PERSONAL_META_PREFIX.length())
			);
		} else if (path.startsWith(UI_PREFIX) && path.endsWith(UI_VALUE_SUFFIX)) {
			String nodeId = path.substring(UI_PREFIX.length(), path.length() - UI_VALUE_SUFFIX.length());
			value = nodeId.indexOf('/') < 0 ? this.ui.get(nodeId) : null;
		} else {
			int separator = path.indexOf('.');
			if (separator <= 0 || separator == path.length() - 1) {
				return Optional.empty();
			}
			String root = path.substring(0, separator);
			String property = path.substring(separator + 1);
			if (
				!root.equals("item")
					&& !root.equals("detail")
					&& property.indexOf('.') >= 0
			) {
				return Optional.empty();
			}
			value = switch (root) {
				case "viewer" -> this.viewer.get(property);
				case "session" -> this.session.get(property);
				case "flow" -> this.flow.get(property);
				case "task" -> this.task.get(property);
				case "history" -> this.history.get(property);
				case "detail" -> nestedHistoryValue(this.detail, property);
				case "item" -> nestedHistoryValue(this.item, property);
				default -> null;
			};
		}
		return value == null ? Optional.empty() : Optional.of(value.deepCopy());
	}

	private static JsonElement nestedHistoryValue(
		final Map<String, JsonElement> values,
		final String property
	) {
		int separator = property.indexOf('.');
		if (separator < 0) {
			return values.get(property);
		}
		if (
			property.indexOf('.', separator + 1) >= 0
				|| !NESTED_HISTORY_BINDINGS.contains(property)
		) {
			return null;
		}
		JsonElement parent = values.get(property.substring(0, separator));
		return parent != null && parent.isJsonObject()
			? parent.getAsJsonObject().get(property.substring(separator + 1))
			: null;
	}

	private Builder copyBuilder() {
		Builder copy = builder();
		this.viewer.forEach(copy::viewer);
		this.session.forEach(copy::session);
		this.flow.forEach(copy::flow);
		this.task.forEach(copy::task);
		this.history.forEach(copy::history);
		this.detail.forEach(copy::detail);
		this.fields.forEach(copy::field);
		this.flowFields.forEach(copy::flowField);
		this.personal.forEach(copy::personal);
		this.personalMetadata.forEach(copy::personalMeta);
		this.item.forEach(copy::item);
		this.ui.forEach(copy::ui);
		return copy;
	}

	private static JsonElement identifierValue(
		final Map<Identifier, JsonElement> values,
		final String rawIdentifier
	) {
		Identifier identifier = Identifier.tryParse(rawIdentifier);
		return identifier == null ? null : values.get(identifier);
	}

	private static JsonElement personalMetadataValue(
		final Map<Identifier, JsonElement> values,
		final String suffix
	) {
		int separator = suffix.lastIndexOf('/');
		String property = separator < 0 ? "" : suffix.substring(separator + 1);
		if (
			separator <= 0
				|| (!property.equals("name") && !property.equals("value_name"))
		) {
			return null;
		}
		String rawIdentifier = suffix.substring(0, separator);
		Identifier identifier = rawIdentifier.indexOf(':') > 0
			? Identifier.tryParse(rawIdentifier)
			: null;
		JsonElement metadata = identifier == null ? null : values.get(identifier);
		return metadata != null && metadata.isJsonObject()
			? metadata.getAsJsonObject().get(property)
			: null;
	}

	private static <K> Map<K, JsonElement> copyValues(final Map<K, JsonElement> source) {
		Map<K, JsonElement> copied = new LinkedHashMap<>();
		source.forEach((key, value) -> copied.put(key, value.deepCopy()));
		return Map.copyOf(copied);
	}

	public static final class Builder {
		private final Map<String, JsonElement> viewer = new LinkedHashMap<>();
		private final Map<String, JsonElement> session = new LinkedHashMap<>();
		private final Map<String, JsonElement> flow = new LinkedHashMap<>();
		private final Map<String, JsonElement> task = new LinkedHashMap<>();
		private final Map<String, JsonElement> history = new LinkedHashMap<>();
		private final Map<String, JsonElement> detail = new LinkedHashMap<>();
		private final Map<Identifier, JsonElement> fields = new LinkedHashMap<>();
		private final Map<Identifier, JsonElement> flowFields = new LinkedHashMap<>();
		private final Map<Identifier, JsonElement> personal = new LinkedHashMap<>();
		private final Map<Identifier, JsonElement> personalMetadata = new LinkedHashMap<>();
		private final Map<String, JsonElement> item = new LinkedHashMap<>();
		private final Map<String, JsonElement> ui = new LinkedHashMap<>();

		private Builder() {
		}

		public Builder viewer(final String property, final JsonElement value) {
			put(this.viewer, property, value);
			return this;
		}

		public Builder session(final String property, final JsonElement value) {
			put(this.session, property, value);
			return this;
		}

		public Builder flow(final String property, final JsonElement value) {
			put(this.flow, property, value);
			return this;
		}

		public Builder task(final String property, final JsonElement value) {
			put(this.task, property, value);
			return this;
		}

		public Builder history(final String property, final JsonElement value) {
			put(this.history, property, value);
			return this;
		}

		public Builder detail(final String property, final JsonElement value) {
			put(this.detail, property, value);
			return this;
		}

		public Builder field(final Identifier field, final JsonElement value) {
			put(this.fields, field, value);
			return this;
		}

		public Builder flowField(final Identifier field, final JsonElement value) {
			put(this.flowFields, field, value);
			return this;
		}

		public Builder personal(final Identifier grant, final JsonElement value) {
			put(this.personal, grant, value);
			return this;
		}

		public Builder personalMeta(final Identifier grant, final JsonElement value) {
			put(this.personalMetadata, grant, value);
			return this;
		}

		public Builder item(final String property, final JsonElement value) {
			put(this.item, property, value);
			return this;
		}

		public Builder ui(final String nodeId, final JsonElement value) {
			put(this.ui, nodeId, value);
			return this;
		}

		public BindingContext build() {
			return new BindingContext(this);
		}

		private static <K> void put(
			final Map<K, JsonElement> target,
			final K key,
			final JsonElement value
		) {
			target.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
		}
	}
}
