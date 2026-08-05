package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudAudience;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBinding;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBindingSource;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudValueType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.MissingMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Resolves server-only HUD binding sources into bounded presentation values. */
public final class HudBindingResolver {
	private static final Pattern DURATION = Pattern.compile("(0|[1-9][0-9]*)(?:\\.([0-9]{1,3}))?([tsm])");
	private static final int MAX_STRING_LENGTH = 8_192;
	private static final int MAX_COLLECTION_ENTRIES = 64;

	private HudBindingResolver() {
	}

	public static Result resolve(
		final Map<String, HudBinding> bindings,
		final ResolveContext context
	) {
		Objects.requireNonNull(bindings, "bindings");
		Objects.requireNonNull(context, "context");
		Map<String, ResolvedField> values = new LinkedHashMap<>();
		List<String> diagnostics = new ArrayList<>();
		Identifier degradation = null;
		boolean hidden = false;
		for (HudBinding binding : bindings.values().stream()
			.sorted(java.util.Comparator.comparing(HudBinding::id))
			.toList()) {
			BindingRequest request = new BindingRequest(
				binding,
				binding.source(),
				context.audience(),
				context.item()
			);
			if (
				binding.audience().filter(value -> !HudRouteAuthority.matches(value, context.audience())).isPresent()
					|| !safeAuthorized(context.access(), request, diagnostics)
			) {
				MissingResult missing = missing(binding, diagnostics, "binding audience denied");
				hidden |= missing.hideComponent();
				degradation = mergeDegradation(degradation, missing.degradationLayout(), diagnostics);
				continue;
			}

			SourceValue supplied;
			try {
				supplied = Objects.requireNonNull(
					context.access().resolve(request),
					"binding source returned null"
				);
			} catch (RuntimeException error) {
				diagnostics.add("binding " + binding.id() + " source failed: " + message(error));
				supplied = SourceValue.missing();
			}
			if (
				supplied.audience().filter(value -> !HudRouteAuthority.matches(value, context.audience())).isPresent()
			) {
				supplied = SourceValue.missing();
				diagnostics.add("binding " + binding.id() + " source audience denied");
			}

			Optional<Value> usable = supplied.value().filter(value -> matches(binding, value));
			if (supplied.value().isPresent() && usable.isEmpty()) {
				diagnostics.add("binding " + binding.id() + " returned a value of the wrong type");
			}
			if (usable.isPresent() && !supplied.stale()) {
				values.put(binding.id(), new ResolvedField(usable.orElseThrow(), Optional.empty()));
				continue;
			}
			long suppliedAgeTicks = supplied.ageTicks();
			if (
				usable.isPresent()
					&& supplied.stale()
					&& binding.onMissing().mode() == MissingMode.RETAIN_STALE
					&& binding.onMissing().staleTtlTicks().filter(ttl -> suppliedAgeTicks <= ttl).isPresent()
			) {
				Optional<Component> indicator = binding.onMissing().staleIndicator().map(HudBindingResolver::component);
				values.put(binding.id(), new ResolvedField(usable.orElseThrow(), indicator));
				continue;
			}

			MissingResult missing = missing(binding, diagnostics, supplied.stale()
				? "stale value expired"
				: "value is unavailable");
			hidden |= missing.hideComponent();
			degradation = mergeDegradation(degradation, missing.degradationLayout(), diagnostics);
			missing.placeholder().ifPresent(value -> values.put(binding.id(), new ResolvedField(value, Optional.empty())));
		}
		if (diagnostics.stream().anyMatch(value -> value.startsWith("conflicting degradation layouts"))) {
			hidden = true;
			degradation = null;
		}
		return new Result(
			Map.copyOf(values),
			hidden,
			Optional.ofNullable(degradation),
			List.copyOf(diagnostics)
		);
	}

	private static boolean safeAuthorized(
		final BindingSourceAccess access,
		final BindingRequest request,
		final List<String> diagnostics
	) {
		try {
			return access.authorized(request);
		} catch (RuntimeException error) {
			diagnostics.add("binding " + request.binding().id() + " authorization failed: " + message(error));
			return false;
		}
	}

	private static MissingResult missing(
		final HudBinding binding,
		final List<String> diagnostics,
		final String reason
	) {
		return switch (binding.onMissing().mode()) {
			case HIDE_FIELD -> MissingResult.empty();
			case HIDE_COMPONENT, RETAIN_STALE -> new MissingResult(true, Optional.empty(), Optional.empty());
			case DEGRADATION_LAYOUT -> new MissingResult(
				false,
				binding.onMissing().degradationLayout(),
				Optional.empty()
			);
			case PLACEHOLDER -> {
				try {
					Value value = placeholder(
						binding.valueType(),
						binding.itemType(),
						binding.onMissing().placeholderCanonicalJson().orElseThrow()
					);
					yield new MissingResult(false, Optional.empty(), Optional.of(value));
				} catch (RuntimeException error) {
					diagnostics.add(
						"binding " + binding.id() + " placeholder failed after " + reason + ": " + message(error)
					);
					yield new MissingResult(true, Optional.empty(), Optional.empty());
				}
			}
		};
	}

	private static Identifier mergeDegradation(
		final Identifier current,
		final Optional<Identifier> next,
		final List<String> diagnostics
	) {
		if (next.isEmpty()) {
			return current;
		}
		if (current == null || current.equals(next.orElseThrow())) {
			return next.orElseThrow();
		}
		diagnostics.add("conflicting degradation layouts: " + current + " and " + next.orElseThrow());
		return current;
	}

	private static boolean matches(final HudBinding binding, final Value value) {
		if (value.type() != binding.valueType()) {
			return false;
		}
		if (value instanceof ListValue list) {
			HudValueType itemType = binding.itemType().orElse(null);
			return itemType != null && list.values().stream().allMatch(item -> item.type() == itemType);
		}
		return true;
	}

	private static Value placeholder(
		final HudValueType type,
		final Optional<HudValueType> itemType,
		final String canonical
	) {
		JsonElement value = JsonParser.parseString(canonical);
		return switch (type) {
			case STRING -> new StringValue(value.getAsString());
			case INTEGER -> new IntegerValue(value.getAsBigDecimal().longValueExact());
			case DECIMAL -> new DecimalValue(finite(value.getAsDouble()));
			case BOOLEAN -> new BooleanValue(value.getAsBoolean());
			case IDENTIFIER -> new IdentifierValue(Identifier.parse(value.getAsString()));
			case COMPONENT -> new ComponentValue(
				ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()
			);
			case DURATION -> new DurationValue(duration(value));
			case LIST -> {
				HudValueType nested = itemType.orElseThrow();
				JsonArray array = value.getAsJsonArray();
				if (array.size() > MAX_COLLECTION_ENTRIES) {
					throw new IllegalArgumentException("placeholder list exceeds limit");
				}
				List<Value> values = new ArrayList<>();
				for (JsonElement element : array) {
					values.add(plainValue(nested, element));
				}
				yield new ListValue(nested, values);
			}
			case OBJECT -> objectValue(value.getAsJsonObject());
			case PLAYER, CLOCK -> throw new IllegalArgumentException("player and clock placeholders are forbidden");
		};
	}

	private static Value plainValue(final HudValueType type, final JsonElement value) {
		return switch (type) {
			case STRING -> new StringValue(value.getAsString());
			case INTEGER -> new IntegerValue(value.getAsBigDecimal().longValueExact());
			case DECIMAL -> new DecimalValue(finite(value.getAsDouble()));
			case BOOLEAN -> new BooleanValue(value.getAsBoolean());
			case IDENTIFIER -> new IdentifierValue(Identifier.parse(value.getAsString()));
			case COMPONENT -> new ComponentValue(ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow());
			case DURATION -> new DurationValue(duration(value));
			case OBJECT -> objectValue(value.getAsJsonObject());
			case LIST, PLAYER, CLOCK -> throw new IllegalArgumentException("unsupported nested placeholder type " + type);
		};
	}

	private static ObjectValue objectValue(final JsonObject object) {
		if (object.size() > MAX_COLLECTION_ENTRIES) {
			throw new IllegalArgumentException("placeholder object exceeds limit");
		}
		Map<String, Value> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			JsonElement value = entry.getValue();
			Value converted;
			if (value.isJsonObject()) {
				converted = objectValue(value.getAsJsonObject());
			} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
				converted = new BooleanValue(value.getAsBoolean());
			} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				BigDecimal number = value.getAsBigDecimal();
				try {
					converted = new IntegerValue(number.longValueExact());
				} catch (ArithmeticException ignored) {
					converted = new DecimalValue(finite(number.doubleValue()));
				}
			} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				converted = new StringValue(value.getAsString());
			} else {
				throw new IllegalArgumentException("unsupported object placeholder value");
			}
			values.put(entry.getKey(), converted);
		}
		return new ObjectValue(values);
	}

	private static long duration(final JsonElement value) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			return value.getAsBigDecimal().longValueExact();
		}
		String encoded = value.getAsString();
		Matcher matcher = DURATION.matcher(encoded);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("invalid duration placeholder");
		}
		BigDecimal amount = new BigDecimal(matcher.group(1) + (matcher.group(2) == null ? "" : "." + matcher.group(2)));
		BigDecimal multiplier = switch (matcher.group(3)) {
			case "t" -> BigDecimal.ONE;
			case "s" -> BigDecimal.valueOf(20L);
			case "m" -> BigDecimal.valueOf(1_200L);
			default -> throw new IllegalArgumentException("invalid duration unit");
		};
		return amount.multiply(multiplier).longValueExact();
	}

	private static double finite(final double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("number must be finite");
		}
		return value;
	}

	private static Component component(final io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText value) {
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(value.json()))
			.getOrThrow();
	}

	private static String message(final Throwable error) {
		String value = error.getMessage();
		return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
	}

	public interface BindingSourceAccess {
		/** Must decide source-definition authorization without reading or returning its value. */
		boolean authorized(BindingRequest request);

		/** Called only after component, binding and source-definition authorization succeeded. */
		SourceValue resolve(BindingRequest request);
	}

	public record ResolveContext(
		HudRouteAuthority.Context audience,
		BindingSourceAccess access,
		Optional<Map<String, Value>> item
	) {
		public ResolveContext {
			audience = Objects.requireNonNull(audience, "audience");
			access = Objects.requireNonNull(access, "access");
			item = Objects.requireNonNull(item, "item").map(Map::copyOf);
		}

		public ResolveContext(
			final HudRouteAuthority.Context audience,
			final BindingSourceAccess access
		) {
			this(audience, access, Optional.empty());
		}
	}

	public record BindingRequest(
		HudBinding binding,
		HudBindingSource source,
		HudRouteAuthority.Context audience,
		Optional<Map<String, Value>> item
	) {
		public BindingRequest {
			binding = Objects.requireNonNull(binding, "binding");
			source = Objects.requireNonNull(source, "source");
			audience = Objects.requireNonNull(audience, "audience");
			item = Objects.requireNonNull(item, "item").map(Map::copyOf);
		}
	}

	public record SourceValue(
		Optional<Value> value,
		boolean stale,
		long ageTicks,
		Optional<HudAudience> audience
	) {
		public SourceValue {
			value = Objects.requireNonNull(value, "value");
			if (ageTicks < 0L) {
				throw new IllegalArgumentException("source value age cannot be negative");
			}
			audience = Objects.requireNonNull(audience, "audience");
		}

		public static SourceValue current(final Value value) {
			return new SourceValue(Optional.of(value), false, 0L, Optional.empty());
		}

		public static SourceValue stale(final Value value, final long ageTicks) {
			return new SourceValue(Optional.of(value), true, ageTicks, Optional.empty());
		}

		public static SourceValue missing() {
			return new SourceValue(Optional.empty(), false, 0L, Optional.empty());
		}
	}

	public sealed interface Value permits
		StringValue,
		IntegerValue,
		DecimalValue,
		BooleanValue,
		IdentifierValue,
		PlayerValue,
		ComponentValue,
		DurationValue,
		ClockValue,
		ListValue,
		ObjectValue {
		HudValueType type();
	}

	public record StringValue(String value) implements Value {
		public StringValue {
			value = Objects.requireNonNull(value, "value");
			if (value.length() > MAX_STRING_LENGTH) {
				throw new IllegalArgumentException("HUD string exceeds limit");
			}
		}

		@Override
		public HudValueType type() {
			return HudValueType.STRING;
		}
	}

	public record IntegerValue(long value) implements Value {
		@Override
		public HudValueType type() {
			return HudValueType.INTEGER;
		}
	}

	public record DecimalValue(double value) implements Value {
		public DecimalValue {
			finite(value);
		}

		@Override
		public HudValueType type() {
			return HudValueType.DECIMAL;
		}
	}

	public record BooleanValue(boolean value) implements Value {
		@Override
		public HudValueType type() {
			return HudValueType.BOOLEAN;
		}
	}

	public record IdentifierValue(Identifier value) implements Value {
		public IdentifierValue {
			value = Objects.requireNonNull(value, "value");
		}

		@Override
		public HudValueType type() {
			return HudValueType.IDENTIFIER;
		}
	}

	public record PlayerValue(UUID playerId, String playerName, boolean online) implements Value {
		public PlayerValue {
			playerId = Objects.requireNonNull(playerId, "playerId");
			playerName = Objects.requireNonNull(playerName, "playerName").strip();
			if (playerName.length() > 64) {
				throw new IllegalArgumentException("player name exceeds limit");
			}
		}

		@Override
		public HudValueType type() {
			return HudValueType.PLAYER;
		}
	}

	public record ComponentValue(Component value) implements Value {
		public ComponentValue {
			value = Objects.requireNonNull(value, "value").copy();
		}

		@Override
		public HudValueType type() {
			return HudValueType.COMPONENT;
		}
	}

	public record DurationValue(long ticks) implements Value {
		public DurationValue {
			if (ticks < 0L) {
				throw new IllegalArgumentException("duration cannot be negative");
			}
		}

		@Override
		public HudValueType type() {
			return HudValueType.DURATION;
		}
	}

	public record ClockValue(
		long serverTick,
		double baseTicks,
		double rate,
		boolean paused
	) implements Value {
		public ClockValue {
			if (serverTick < 0L || !Double.isFinite(baseTicks) || baseTicks < 0.0D || !Double.isFinite(rate)) {
				throw new IllegalArgumentException("clock value is invalid");
			}
		}

		@Override
		public HudValueType type() {
			return HudValueType.CLOCK;
		}
	}

	public record ListValue(HudValueType itemType, List<Value> values) implements Value {
		public ListValue {
			itemType = Objects.requireNonNull(itemType, "itemType");
			values = List.copyOf(values);
			boolean invalid = values.size() > MAX_COLLECTION_ENTRIES;
			for (Value value : values) {
				if (value.type() != itemType) {
					invalid = true;
					break;
				}
			}
			if (invalid) {
				throw new IllegalArgumentException("HUD list value is invalid");
			}
		}

		@Override
		public HudValueType type() {
			return HudValueType.LIST;
		}
	}

	public record ObjectValue(Map<String, Value> values) implements Value {
		public ObjectValue {
			values = Map.copyOf(values);
			if (values.size() > MAX_COLLECTION_ENTRIES) {
				throw new IllegalArgumentException("HUD object value exceeds limit");
			}
		}

		@Override
		public HudValueType type() {
			return HudValueType.OBJECT;
		}
	}

	public record ResolvedField(Value value, Optional<Component> staleIndicator) {
		public ResolvedField {
			value = Objects.requireNonNull(value, "value");
			staleIndicator = Objects.requireNonNull(staleIndicator, "staleIndicator").map(Component::copy);
		}
	}

	public record Result(
		Map<String, ResolvedField> values,
		boolean hideComponent,
		Optional<Identifier> degradationLayout,
		List<String> diagnostics
	) {
		public Result {
			values = Map.copyOf(values);
			degradationLayout = Objects.requireNonNull(degradationLayout, "degradationLayout");
			diagnostics = List.copyOf(diagnostics);
		}
	}

	private record MissingResult(
		boolean hideComponent,
		Optional<Identifier> degradationLayout,
		Optional<Value> placeholder
	) {
		private MissingResult {
			degradationLayout = Objects.requireNonNull(degradationLayout, "degradationLayout");
			placeholder = Objects.requireNonNull(placeholder, "placeholder");
		}

		private static MissingResult empty() {
			return new MissingResult(false, Optional.empty(), Optional.empty());
		}
	}
}
