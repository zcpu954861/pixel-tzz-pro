package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.*;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Strict, bounded parser for standalone V3C countdown resources. */
public final class CountdownDefinitionParser {
	public static final int MAX_ISSUE_DETAILS = 64;
	private static final int MAX_JSON_DEPTH = 64;
	private static final Pattern DURATION = Pattern.compile("([0-9]+)(t|s|m|h)");
	private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9._-]{1,128}");
	private static final Set<String> DYNAMIC_COMPONENT_KEYS = Set.of("score", "selector", "nbt");

	private CountdownDefinitionParser() {
	}

	public static ParseResult parse(final Identifier id, final String json) {
		IssueCollector issues = new IssueCollector();
		JsonObject root = parseRoot(json, issues);
		if (root == null) {
			return issues.result(Optional.empty(), "", "");
		}
		String canonical = canonical(root);
		if (canonical.getBytes(StandardCharsets.UTF_8).length > CountdownDefinitions.MAX_CANONICAL_DOCUMENT_BYTES) {
			issues.add("", "RESOURCE_LIMIT", "countdown document exceeds the UTF-8 byte limit");
		}
		CountdownDefinition definition = null;
		try {
			ObjectReader reader = new ObjectReader(root, "", issues);
			Integer format = reader.requiredInt("format_version");
			if (format != null && format != CountdownDefinitions.CURRENT_FORMAT_VERSION) {
				issues.add("/format_version", "UNSUPPORTED_FORMAT", "expected format_version 1");
			}
			RichText name = richText(reader.required("name"), "/name", issues);
			long duration = duration(reader.optional("duration"), "/duration", 200L, false, issues);
			JsonObject displayAudienceObject = reader.optionalObject("display_audience");
			JsonObject presentationObject = reader.optionalObject("presentation");
			JsonObject disconnectObject = reader.optionalObject("disconnect");
			JsonObject restrictionsObject = reader.optionalObject("restrictions");
			JsonArray checkpointsArray = reader.optionalArray("checkpoints");
			JsonObject callbacksObject = reader.optionalObject("callbacks");
			reader.finish("countdown");
			if (name != null) {
				definition = new CountdownDefinition(
					id,
					name,
					duration,
					displayAudience(displayAudienceObject, issues),
					presentation(presentationObject, name, issues),
					disconnect(disconnectObject, issues),
					restrictions(restrictionsObject, issues),
					checkpoints(checkpointsArray, duration, issues),
					callbacks(callbacksObject, issues),
					canonical,
					sha256(canonical)
				);
			}
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(issues.count() == 0 ? Optional.ofNullable(definition) : Optional.empty(), canonical, sha256(canonical));
	}

	private static DisplayAudience displayAudience(
		final JsonObject object,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(
			object == null ? new JsonObject() : object,
			"/display_audience",
			issues
		);
		boolean includeHost = reader.optionalBoolean("include_host", true);
		boolean includeObservers = reader.optionalBoolean("include_observers", false);
		reader.finish("countdown display audience");
		return new DisplayAudience(includeHost, includeObservers);
	}

	private static CountdownPresentation presentation(
		final JsonObject object,
		final RichText name,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation", issues);
		RichText title = optionalRichText(reader.optional("title"), "/presentation/title", issues).orElse(name);
		Optional<RichText> prefix = optionalRichText(reader.optional("prefix"), "/presentation/prefix", issues);
		Optional<RichText> suffix = optionalRichText(reader.optional("suffix"), "/presentation/suffix", issues);
		RichText waiting = optionalRichText(reader.optional("waiting_text"), "/presentation/waiting_text", issues)
			.orElse(literal("等待玩家"));
		RichText paused = optionalRichText(reader.optional("paused_text"), "/presentation/paused_text", issues)
			.orElse(literal("倒计时暂停"));
		RichText complete = optionalRichText(reader.optional("complete_text"), "/presentation/complete_text", issues)
			.orElse(literal("开始"));
		JsonObject timeObject = reader.optionalObject("time");
		JsonObject panelObject = reader.optionalObject("panel");
		JsonObject progressObject = reader.optionalObject("progress");
		JsonObject digitsObject = reader.optionalObject("digits");
		JsonObject enterObject = reader.optionalObject("enter");
		JsonObject exitObject = reader.optionalObject("exit");
		JsonObject completionObject = reader.optionalObject("completion");
		JsonObject soundsObject = reader.optionalObject("sounds");
		reader.finish("countdown presentation");
		return new CountdownPresentation(
			title,
			prefix,
			suffix,
			waiting,
			paused,
			complete,
			time(timeObject, issues),
			panel(panelObject, issues),
			progress(progressObject, issues),
			digits(digitsObject, issues),
			motion(enterObject, "/presentation/enter", SurfaceTransition.FADE, 180, issues),
			motion(exitObject, "/presentation/exit", SurfaceTransition.FADE, 220, issues),
			completion(completionObject, issues),
			lifecycleSounds(soundsObject, issues)
		);
	}

	private static TimePresentation time(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation/time", issues);
		TimeFormat format = reader.optionalEnum("format", TimeFormat.class, TimeFormat.MM_SS);
		TimePrecision precision = reader.optionalEnum("precision", TimePrecision.class, TimePrecision.SECONDS);
		boolean leadingZero = reader.optionalBoolean("leading_zero", true);
		String separator = reader.optionalString("separator", ":");
		int color = reader.optionalColor("color", 0xFFF4F7FA);
		double scale = reader.optionalDouble("scale", 1.0D);
		boolean shadow = reader.optionalBoolean("shadow", true);
		reader.finish("countdown time presentation");
		return guarded(
			() -> new TimePresentation(format, precision, leadingZero, separator, color, scale, shadow),
			new TimePresentation(TimeFormat.MM_SS, TimePrecision.SECONDS, true, ":", 0xFFF4F7FA, 1.0D, true),
			"/presentation/time",
			issues
		);
	}

	private static PanelPresentation panel(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation/panel", issues);
		int background = reader.optionalColor("background_color", 0xD0081018);
		int border = reader.optionalColor("border_color", 0xCC33424E);
		int accent = reader.optionalColor("accent_color", 0xFFF2C14E);
		double opacity = reader.optionalDouble("opacity", 0.88D);
		int paddingX = reader.optionalInt("padding_x", 6);
		int paddingY = reader.optionalInt("padding_y", 3);
		int gap = reader.optionalInt("gap_above_actionbar", 6);
		int maxWidth = reader.optionalInt("max_width", 320);
		reader.finish("countdown panel presentation");
		return guarded(
			() -> new PanelPresentation(background, border, accent, opacity, paddingX, paddingY, gap, maxWidth),
			new PanelPresentation(0xD0081018, 0xCC33424E, 0xFFF2C14E, 0.88D, 6, 3, 6, 320),
			"/presentation/panel",
			issues
		);
	}

	private static ProgressPresentation progress(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation/progress", issues);
		ProgressMode mode = reader.optionalEnum("mode", ProgressMode.class, ProgressMode.LINE);
		int color = reader.optionalColor("color", 0xFFF2C14E);
		int track = reader.optionalColor("track_color", 0x6033424E);
		int thickness = reader.optionalInt("thickness", 1);
		boolean smooth = reader.optionalBoolean("smooth", true);
		reader.finish("countdown progress presentation");
		return guarded(
			() -> new ProgressPresentation(mode, color, track, thickness, smooth),
			new ProgressPresentation(ProgressMode.LINE, 0xFFF2C14E, 0x6033424E, 1, true),
			"/presentation/progress",
			issues
		);
	}

	private static DigitPresentation digits(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation/digits", issues);
		DigitTransition transition = reader.optionalEnum("transition", DigitTransition.class, DigitTransition.ROLL);
		RollDirection direction = reader.optionalEnum("direction", RollDirection.class, RollDirection.DOWN);
		int durationMs = reader.optionalInt("duration_ms", 240);
		int distance = reader.optionalInt("distance", 10);
		Easing easing = reader.optionalEnum("easing", Easing.class, Easing.EASE_OUT_CUBIC);
		int staggerMs = reader.optionalInt("stagger_ms", 0);
		boolean animateFractional = reader.optionalBoolean("animate_fractional", false);
		int incomingColor = reader.optionalColor("incoming_color", 0xFFFFFFFF);
		int outgoingColor = reader.optionalColor("outgoing_color", 0xFF8A9AA8);
		double incomingAlpha = reader.optionalDouble("incoming_alpha", 1.0D);
		double outgoingAlpha = reader.optionalDouble("outgoing_alpha", 0.55D);
		reader.finish("countdown digit presentation");
		return guarded(
			() -> new DigitPresentation(
				transition,
				direction,
				durationMs,
				distance,
				easing,
				staggerMs,
				animateFractional,
				incomingColor,
				outgoingColor,
				incomingAlpha,
				outgoingAlpha
			),
			new DigitPresentation(
				DigitTransition.ROLL,
				RollDirection.DOWN,
				240,
				10,
				Easing.EASE_OUT_CUBIC,
				0,
				false,
				0xFFFFFFFF,
				0xFF8A9AA8,
				1.0D,
				0.55D
			),
			"/presentation/digits",
			issues
		);
	}

	private static SurfaceMotion motion(
		final JsonObject object,
		final String pointer,
		final SurfaceTransition defaultMode,
		final int defaultDuration,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, pointer, issues);
		SurfaceTransition mode = reader.optionalEnum("mode", SurfaceTransition.class, defaultMode);
		int duration = reader.optionalInt("duration_ms", defaultDuration);
		Easing easing = reader.optionalEnum("easing", Easing.class, Easing.EASE_OUT_CUBIC);
		reader.finish("countdown surface motion");
		return guarded(
			() -> new SurfaceMotion(mode, duration, easing),
			new SurfaceMotion(defaultMode, defaultDuration, Easing.EASE_OUT_CUBIC),
			pointer,
			issues
		);
	}

	private static CompletionPresentation completion(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation/completion", issues);
		int hold = reader.optionalInt("hold_ms", 600);
		int color = reader.optionalColor("color", 0xFFF2C14E);
		reader.finish("countdown completion presentation");
		return guarded(
			() -> new CompletionPresentation(hold, color),
			new CompletionPresentation(600, 0xFFF2C14E),
			"/presentation/completion",
			issues
		);
	}

	private static Map<CountdownCallbackSlot, Optional<CountdownSound>> lifecycleSounds(
		final JsonObject object,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/presentation/sounds", issues);
		EnumMap<CountdownCallbackSlot, Optional<CountdownSound>> values = new EnumMap<>(CountdownCallbackSlot.class);
		for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
			String key = serialized(slot);
			values.put(slot, Optional.ofNullable(sound(reader.optionalObject(key), "/presentation/sounds/" + key, issues)));
		}
		reader.finish("countdown lifecycle sounds");
		return Map.copyOf(values);
	}

	private static DisconnectPolicy disconnect(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/disconnect", issues);
		DisconnectAction player = reader.optionalEnum("required_player", DisconnectAction.class, DisconnectAction.PAUSE);
		DisconnectAction host = reader.optionalEnum("host", DisconnectAction.class, DisconnectAction.CONTINUE);
		reader.finish("countdown disconnect policy");
		return new DisconnectPolicy(player, host);
	}

	private static CountdownRestrictions restrictions(final JsonObject object, final IssueCollector issues) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/restrictions", issues);
		FreezeMode movement = reader.optionalEnum("movement", FreezeMode.class, FreezeMode.FREEZE);
		BlockMode attack = reader.optionalEnum("attack", BlockMode.class, BlockMode.BLOCK);
		BlockMode interact = reader.optionalEnum("interact", BlockMode.class, BlockMode.BLOCK);
		JsonObject itemsObject = reader.optionalObject("items");
		DamageMode damage = reader.optionalEnum("damage", DamageMode.class, DamageMode.IMMUNE);
		AllowOnly camera = reader.optionalEnum("camera", AllowOnly.class, AllowOnly.ALLOW);
		AllowOnly chat = reader.optionalEnum("chat", AllowOnly.class, AllowOnly.ALLOW);
		AllowOnly escape = reader.optionalEnum("escape", AllowOnly.class, AllowOnly.ALLOW);
		reader.finish("countdown restrictions");
		ObjectReader items = new ObjectReader(itemsObject == null ? new JsonObject() : itemsObject, "/restrictions/items", issues);
		ItemRestrictions itemRestrictions = new ItemRestrictions(
			items.optionalEnum("use", BlockMode.class, BlockMode.BLOCK),
			items.optionalEnum("drop", BlockMode.class, BlockMode.BLOCK),
			items.optionalEnum("swap", BlockMode.class, BlockMode.BLOCK),
			items.optionalEnum("inventory", BlockMode.class, BlockMode.BLOCK)
		);
		items.finish("countdown item restrictions");
		return new CountdownRestrictions(movement, attack, interact, itemRestrictions, damage, camera, chat, escape);
	}

	private static List<CountdownCheckpoint> checkpoints(
		final JsonArray array,
		final long totalTicks,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > CountdownDefinitions.MAX_CHECKPOINTS) {
			issues.add("/checkpoints", "RESOURCE_LIMIT", "too many countdown checkpoints");
		}
		List<CountdownCheckpoint> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		Set<Long> remainingValues = new HashSet<>();
		for (int index = 0; index < Math.min(array.size(), CountdownDefinitions.MAX_CHECKPOINTS); index++) {
			String pointer = "/checkpoints/" + index;
			if (!array.get(index).isJsonObject()) {
				issues.add(pointer, "TYPE_MISMATCH", "checkpoint must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(array.get(index).getAsJsonObject(), pointer, issues);
			String id = reader.requiredString("id");
			long remaining = duration(reader.required("remaining"), pointer + "/remaining", 0L, true, issues);
			JsonObject soundObject = reader.optionalObject("sound");
			Identifier cue = reader.optionalIdentifier("cue");
			Optional<RichText> text = optionalRichText(reader.optional("text"), pointer + "/text", issues);
			Optional<Integer> color = reader.optionalColorValue("color");
			Optional<Double> scale = reader.optionalDoubleValue("scale");
			reader.finish("countdown checkpoint");
			if (id == null || !LOCAL_ID.matcher(id).matches()) {
				issues.add(pointer + "/id", "INVALID_ID", "checkpoint id must be a lowercase local id");
				continue;
			}
			if (!ids.add(id)) {
				issues.add(pointer + "/id", "DUPLICATE_VALUE", "checkpoint id is duplicated");
			}
			if (remaining > totalTicks) {
				issues.add(pointer + "/remaining", "OUT_OF_RANGE", "checkpoint exceeds countdown duration");
			}
			if (!remainingValues.add(remaining)) {
				issues.add(pointer + "/remaining", "DUPLICATE_VALUE", "checkpoint time is duplicated");
			}
			try {
				result.add(new CountdownCheckpoint(
					id,
					remaining,
					Optional.ofNullable(sound(soundObject, pointer + "/sound", issues)),
					Optional.ofNullable(cue),
					text,
					color,
					scale
				));
			} catch (IllegalArgumentException error) {
				issues.add(pointer, "OUT_OF_RANGE", safeMessage(error));
			}
		}
		result.sort(Comparator.comparingLong(CountdownCheckpoint::remainingTicks).reversed());
		return List.copyOf(result);
	}

	private static CountdownSound sound(final JsonObject object, final String pointer, final IssueCollector issues) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Identifier event = reader.requiredIdentifier("event");
		double volume = reader.optionalDouble("volume", 1.0D);
		double pitch = reader.optionalDouble("pitch", 1.0D);
		reader.finish("countdown sound");
		if (event == null) {
			return null;
		}
		return guarded(() -> new CountdownSound(event, volume, pitch), null, pointer, issues);
	}

	private static Map<CountdownCallbackSlot, List<CountdownCallback>> callbacks(
		final JsonObject object,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object == null ? new JsonObject() : object, "/callbacks", issues);
		EnumMap<CountdownCallbackSlot, List<CountdownCallback>> result = new EnumMap<>(CountdownCallbackSlot.class);
		for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
			String key = serialized(slot);
			JsonArray array = reader.optionalArray(key);
			result.put(slot, callbackList(array, "/callbacks/" + key, issues));
		}
		reader.finish("countdown callbacks");
		return Map.copyOf(result);
	}

	private static List<CountdownCallback> callbackList(
		final JsonArray array,
		final String pointer,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > CountdownDefinitions.MAX_CALLBACKS_PER_SLOT) {
			issues.add(pointer, "RESOURCE_LIMIT", "too many countdown callbacks");
		}
		List<CountdownCallback> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (int index = 0; index < Math.min(array.size(), CountdownDefinitions.MAX_CALLBACKS_PER_SLOT); index++) {
			String itemPointer = pointer + "/" + index;
			if (!array.get(index).isJsonObject()) {
				issues.add(itemPointer, "TYPE_MISMATCH", "callback must be an object");
				continue;
			}
			ObjectReader item = new ObjectReader(array.get(index).getAsJsonObject(), itemPointer, issues);
			String id = item.requiredString("id");
			CountdownCallbackScope scope = item.requiredEnum("scope", CountdownCallbackScope.class);
			Identifier function = item.requiredIdentifier("function");
			boolean required = item.optionalBoolean("required", true);
			item.finish("countdown callback");
			if (id == null || !LOCAL_ID.matcher(id).matches()) {
				issues.add(itemPointer + "/id", "INVALID_ID", "callback id must be a lowercase local id");
				continue;
			}
			if (!ids.add(id)) {
				issues.add(itemPointer + "/id", "DUPLICATE_VALUE", "callback id is duplicated in this slot");
			}
			if (scope != null && function != null) {
				result.add(new CountdownCallback(id, scope, function, required));
			}
		}
		return List.copyOf(result);
	}

	private static Optional<RichText> optionalRichText(
		final JsonElement element,
		final String pointer,
		final IssueCollector issues
	) {
		return Optional.ofNullable(richText(element, pointer, issues));
	}

	private static RichText richText(final JsonElement element, final String pointer, final IssueCollector issues) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		String canonical = canonical(element);
		if (canonical.getBytes(StandardCharsets.UTF_8).length > CountdownDefinitions.MAX_TEXT_COMPONENT_BYTES) {
			issues.add(pointer, "RESOURCE_LIMIT", "text component is too large");
			return null;
		}
		if (containsDynamicComponent(element)) {
			issues.add(pointer, "UNAUTHORIZED_TEXT", "score, selector and nbt text are not allowed here");
			return null;
		}
		DataResult<Component> decoded = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element);
		Component component = decoded.result().orElse(null);
		if (component == null) {
			issues.add(pointer, "INVALID_TEXT", decoded.error().map(DataResult.Error::message).orElse("invalid text"));
			return null;
		}
		String plain = component.getString();
		if (plain.length() > CountdownDefinitions.MAX_TEXT_PLAIN_CHARACTERS) {
			issues.add(pointer, "RESOURCE_LIMIT", "plain text is too long");
			return null;
		}
		return new RichText(canonical, plain);
	}

	private static RichText literal(final String value) {
		JsonObject text = new JsonObject();
		text.addProperty("text", value);
		return new RichText(text.toString(), value);
	}

	private static boolean containsDynamicComponent(final JsonElement element) {
		if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
			return false;
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsDynamicComponent(child)) {
					return true;
				}
			}
			return false;
		}
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (DYNAMIC_COMPONENT_KEYS.contains(entry.getKey()) || containsDynamicComponent(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private static long duration(
		final JsonElement element,
		final String pointer,
		final long fallback,
		final boolean allowZero,
		final IssueCollector issues
	) {
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		long ticks = -1L;
		try {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
				ticks = element.getAsLong();
			} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				Matcher matcher = DURATION.matcher(element.getAsString());
				if (!matcher.matches()) {
					throw new IllegalArgumentException("duration must use t, s, m or h");
				}
				long value = Long.parseLong(matcher.group(1));
				ticks = Math.multiplyExact(value, switch (matcher.group(2)) {
					case "t" -> 1L;
					case "s" -> 20L;
					case "m" -> 1_200L;
					case "h" -> 72_000L;
					default -> throw new IllegalStateException();
				});
			} else {
				throw new IllegalArgumentException("duration must be an integer tick count or duration string");
			}
		} catch (RuntimeException error) {
			issues.add(pointer, "INVALID_DURATION", safeMessage(error));
			return fallback;
		}
		long minimum = allowZero ? 0L : 1L;
		if (ticks < minimum || ticks > CountdownDefinitions.MAX_COUNTDOWN_TICKS) {
			issues.add(pointer, "OUT_OF_RANGE", "duration is outside the supported range");
			return fallback;
		}
		return ticks;
	}

	private static JsonObject parseRoot(final String json, final IssueCollector issues) {
		if (json == null || json.length() > CountdownDefinitions.MAX_CANONICAL_DOCUMENT_BYTES) {
			issues.add("", "RESOURCE_LIMIT", "countdown source is missing or too large");
			return null;
		}
		try {
			JsonReader reader = new JsonReader(new StringReader(json));
			reader.setStrictness(Strictness.STRICT);
			JsonElement element = readStrict(reader, 0);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("trailing content after JSON value");
			}
			if (!element.isJsonObject()) {
				throw new JsonParseException("countdown root must be an object");
			}
			return element.getAsJsonObject();
		} catch (IOException | RuntimeException error) {
			issues.add("", "JSON_SYNTAX", safeMessage(error));
			return null;
		}
	}

	private static JsonElement readStrict(final JsonReader reader, final int depth) throws IOException {
		if (depth > MAX_JSON_DEPTH) {
			throw new JsonParseException("JSON nesting exceeds " + MAX_JSON_DEPTH);
		}
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				JsonObject object = new JsonObject();
				Set<String> names = new HashSet<>();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (!names.add(name)) {
						throw new JsonParseException("duplicate object key " + name);
					}
					object.add(name, readStrict(reader, depth + 1));
				}
				reader.endObject();
				yield object;
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				JsonArray array = new JsonArray();
				while (reader.hasNext()) {
					array.add(readStrict(reader, depth + 1));
				}
				reader.endArray();
				yield array;
			}
			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}
			default -> throw new JsonParseException("unexpected JSON token " + reader.peek());
		};
	}

	private static String canonical(final JsonElement value) {
		if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
			return value == null ? "null" : value.toString();
		}
		if (value.isJsonArray()) {
			StringBuilder result = new StringBuilder("[");
			for (int index = 0; index < value.getAsJsonArray().size(); index++) {
				if (index > 0) {
					result.append(',');
				}
				result.append(canonical(value.getAsJsonArray().get(index)));
			}
			return result.append(']').toString();
		}
		StringBuilder result = new StringBuilder("{");
		boolean first = true;
		for (String key : value.getAsJsonObject().keySet().stream().sorted().toList()) {
			if (!first) {
				result.append(',');
			}
			first = false;
			result.append(new JsonPrimitive(key)).append(':').append(canonical(value.getAsJsonObject().get(key)));
		}
		return result.append('}').toString();
	}

	private static String sha256(final String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is required", error);
		}
	}

	private static String serialized(final Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static int parseColor(final String raw) {
		if (raw == null || !raw.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
			throw new IllegalArgumentException("color must be #RRGGBB or #RRGGBBAA");
		}
		long rgb = Long.parseUnsignedLong(raw.substring(1), 16);
		if (raw.length() == 7) {
			return 0xFF000000 | (int)rgb;
		}
		int red = (int)((rgb >>> 24) & 0xFF);
		int green = (int)((rgb >>> 16) & 0xFF);
		int blue = (int)((rgb >>> 8) & 0xFF);
		int alpha = (int)(rgb & 0xFF);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static String safeMessage(final Throwable error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	private static <T> T guarded(
		final Factory<T> factory,
		final T fallback,
		final String pointer,
		final IssueCollector issues
	) {
		try {
			return factory.create();
		} catch (RuntimeException error) {
			issues.add(pointer, "OUT_OF_RANGE", safeMessage(error));
			return fallback;
		}
	}

	@FunctionalInterface
	private interface Factory<T> {
		T create();
	}

	public record Issue(String pointer, String code, String message) {
	}

	public record ParseResult(
		Optional<CountdownDefinition> definition,
		List<Issue> issues,
		int totalIssueCount,
		String canonical,
		String sha256
	) {
		public ParseResult {
			definition = definition == null ? Optional.empty() : definition;
			issues = List.copyOf(issues);
		}

		public boolean valid() {
			return this.definition.isPresent() && this.totalIssueCount == 0;
		}
	}

	private static final class IssueCollector {
		private final List<Issue> retained = new ArrayList<>();
		private int count;

		private void add(final String pointer, final String code, final String message) {
			this.count++;
			if (this.retained.size() < MAX_ISSUE_DETAILS) {
				this.retained.add(new Issue(pointer, code, message));
			}
		}

		private int count() {
			return this.count;
		}

		private ParseResult result(
			final Optional<CountdownDefinition> definition,
			final String canonical,
			final String sha256
		) {
			return new ParseResult(definition, this.retained, this.count, canonical, sha256);
		}
	}

	private static final class ObjectReader {
		private final JsonObject object;
		private final String pointer;
		private final IssueCollector issues;
		private final Set<String> consumed = new HashSet<>();

		private ObjectReader(final JsonObject object, final String pointer, final IssueCollector issues) {
			this.object = object;
			this.pointer = pointer;
			this.issues = issues;
		}

		private JsonElement required(final String key) {
			JsonElement value = optional(key);
			if (value == null || value.isJsonNull()) {
				this.issues.add(path(key), "MISSING_FIELD", "required field is missing");
				return null;
			}
			return value;
		}

		private JsonElement optional(final String key) {
			this.consumed.add(key);
			return this.object.get(key);
		}

		private JsonObject optionalObject(final String key) {
			JsonElement value = optional(key);
			if (value == null || value.isJsonNull()) {
				return null;
			}
			if (!value.isJsonObject()) {
				this.issues.add(path(key), "TYPE_MISMATCH", "field must be an object");
				return null;
			}
			return value.getAsJsonObject();
		}

		private JsonArray optionalArray(final String key) {
			JsonElement value = optional(key);
			if (value == null || value.isJsonNull()) {
				return null;
			}
			if (!value.isJsonArray()) {
				this.issues.add(path(key), "TYPE_MISMATCH", "field must be an array");
				return null;
			}
			return value.getAsJsonArray();
		}

		private String requiredString(final String key) {
			JsonElement value = required(key);
			return string(value, path(key));
		}

		private String optionalString(final String key, final String fallback) {
			JsonElement value = optional(key);
			return value == null ? fallback : string(value, path(key));
		}

		private String string(final JsonElement value, final String path) {
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				this.issues.add(path, "TYPE_MISMATCH", "field must be a string");
				return null;
			}
			return value.getAsString();
		}

		private boolean optionalBoolean(final String key, final boolean fallback) {
			JsonElement value = optional(key);
			if (value == null) {
				return fallback;
			}
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
				this.issues.add(path(key), "TYPE_MISMATCH", "field must be a boolean");
				return fallback;
			}
			return value.getAsBoolean();
		}

		private int optionalInt(final String key, final int fallback) {
			JsonElement value = optional(key);
			if (value == null) {
				return fallback;
			}
			Integer parsed = integer(value, key);
			return parsed == null ? fallback : parsed;
		}

		private Integer requiredInt(final String key) {
			JsonElement value = required(key);
			return value == null ? null : integer(value, key);
		}

		private Integer integer(final JsonElement value, final String key) {
			try {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
					throw new IllegalArgumentException();
				}
				return value.getAsBigDecimal().intValueExact();
			} catch (RuntimeException error) {
				this.issues.add(path(key), "TYPE_MISMATCH", "field must be an integer");
				return null;
			}
		}

		private double optionalDouble(final String key, final double fallback) {
			return optionalDoubleValue(key).orElse(fallback);
		}

		private Optional<Double> optionalDoubleValue(final String key) {
			JsonElement value = optional(key);
			if (value == null) {
				return Optional.empty();
			}
			try {
				double number = value.getAsDouble();
				if (!Double.isFinite(number)) {
					throw new IllegalArgumentException();
				}
				return Optional.of(number);
			} catch (RuntimeException error) {
				this.issues.add(path(key), "TYPE_MISMATCH", "field must be a finite number");
				return Optional.empty();
			}
		}

		private int optionalColor(final String key, final int fallback) {
			return optionalColorValue(key).orElse(fallback);
		}

		private Optional<Integer> optionalColorValue(final String key) {
			JsonElement value = optional(key);
			if (value == null) {
				return Optional.empty();
			}
			String raw = string(value, path(key));
			if (raw == null) {
				return Optional.empty();
			}
			try {
				return Optional.of(parseColor(raw));
			} catch (IllegalArgumentException error) {
				this.issues.add(path(key), "INVALID_COLOR", safeMessage(error));
				return Optional.empty();
			}
		}

		private Identifier requiredIdentifier(final String key) {
			Identifier value = optionalIdentifier(key);
			if (value == null) {
				this.issues.add(path(key), "MISSING_FIELD", "valid identifier is required");
			}
			return value;
		}

		private Identifier optionalIdentifier(final String key) {
			JsonElement value = optional(key);
			if (value == null) {
				return null;
			}
			String raw = string(value, path(key));
			Identifier result = raw == null ? null : Identifier.tryParse(raw);
			if (result == null || !result.toString().equals(raw)) {
				this.issues.add(path(key), "INVALID_ID", "field must be a canonical identifier");
				return null;
			}
			return result;
		}

		private <E extends Enum<E>> E requiredEnum(final String key, final Class<E> type) {
			JsonElement value = required(key);
			return enumValue(value, key, type, null);
		}

		private <E extends Enum<E>> E optionalEnum(final String key, final Class<E> type, final E fallback) {
			JsonElement value = optional(key);
			return value == null ? fallback : enumValue(value, key, type, fallback);
		}

		private <E extends Enum<E>> E enumValue(final JsonElement value, final String key, final Class<E> type, final E fallback) {
			String raw = string(value, path(key));
			if (raw == null) {
				return fallback;
			}
			try {
				return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException error) {
				this.issues.add(path(key), "INVALID_ENUM", "unsupported value " + raw);
				return fallback;
			}
		}

		private void finish(final String label) {
			for (String key : this.object.keySet()) {
				if (!this.consumed.contains(key)) {
					this.issues.add(path(key), "UNKNOWN_FIELD", label + " contains unknown field " + key);
				}
			}
		}

		private String path(final String key) {
			return this.pointer + "/" + key;
		}
	}
}
