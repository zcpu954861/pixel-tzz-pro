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
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.*;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Strict, bounded parser for V3C HUD and opening-countdown resources. */
public final class HudDefinitionParser {
	public static final int MAX_ISSUE_DETAILS = 100;
	public static final int MAX_SOURCE_CHARACTERS = HudDefinitions.MAX_CANONICAL_DOCUMENT_BYTES;
	public static final int MAX_JSON_DEPTH = 64;
	public static final int MAX_AUDIENCE_SELECTORS = 16;
	public static final int MAX_IDENTIFIER_SET = 64;
	public static final int MAX_TEMPLATE_PARTS = 128;
	public static final int MAX_TEXT_COMPONENT_BYTES = 65_536;
	public static final int MAX_TEXT_PLAIN_CHARACTERS = 8_192;
	public static final int MIN_ROUTE_PRIORITY = -10_000;
	public static final int MAX_ROUTE_PRIORITY = 10_000;
	public static final int MAX_LOGICAL_SIZE = 2_048;
	public static final int MAX_PADDING = 256;
	public static final int MAX_GAP = 256;
	public static final int MAX_IMAGE_SIZE = 1_024;
	public static final int MAX_SEGMENTS = 128;
	public static final int MAX_STALE_TTL_TICKS = 72_000;

	private static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
	private static final Pattern COLOR = Pattern.compile("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
	private static final Pattern SCORE_OBJECTIVE = Pattern.compile("[A-Za-z0-9_.+\\-]{1,16}");
	private static final Pattern SCORE_HOLDER = Pattern.compile("[A-Za-z0-9_#.+:\\-]{1,128}");
	private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_./{}\\[\\]\\-]{1,256}");
	private static final Pattern SAFE_TARGET = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
	private static final Pattern DURATION = Pattern.compile("(0|[1-9][0-9]*)(?:\\.([0-9]{1,3}))?([tsm])");
	private static final Set<String> DYNAMIC_COMPONENT_KEYS = Set.of("score", "selector", "nbt");

	private HudDefinitionParser() {
	}

	public static ParseResult<HudComponentDefinition> parseComponent(
		final Identifier id,
		final String json
	) {
		IssueCollector issues = new IssueCollector();
		ParsedRoot parsed = parseRoot(id, json, issues);
		if (parsed == null) {
			return issues.result(Optional.empty());
		}
		HudComponentDefinition value = null;
		try {
			ObjectReader reader = new ObjectReader(parsed.root(), "", issues);
			checkFormat(reader, issues);
			ComponentType type = reader.requiredEnum("type", ComponentType.class);
			ComponentPriority priority = reader.optionalEnum(
				"priority",
				ComponentPriority.class,
				ComponentPriority.PRIMARY
			);
			JsonObject audienceObject = reader.optionalObject("audience");
			Optional<HudAudience> audience = audienceObject == null
				? Optional.empty()
				: Optional.of(parseAudience(audienceObject, "/audience", issues));
			JsonObject conditionObject = reader.optionalObject("visible_when");
			HudCondition visibleWhen = conditionObject == null
				? HudCondition.always()
				: parseCondition(conditionObject, "/visible_when", issues);
			JsonObject bindingsObject = reader.optionalObject("bindings");
			Map<String, HudBinding> bindings = parseBindings(bindingsObject, "/bindings", issues);
			JsonObject styleObject = reader.optionalObject("style");
			JsonObject controlObject = reader.optionalObject("client_control");
			HudStyle style = parseStyle(styleObject, "/style", type, issues);
			ClientControl control = parseClientControl(controlObject, "/client_control", issues);
			ComponentBody body = type == null
				? null
				: parseComponentBody(type, reader, styleObject, bindings, issues);
			reader.finish("HUD component");
			if (type != null && priority != null && body != null) {
				validateBodyBindings(type, body, bindings, issues);
				value = new HudComponentDefinition(
					id,
					type,
					priority,
					audience,
					visibleWhen,
					bindings,
					style,
					control,
					body,
					parsed.canonical(),
					parsed.sha256()
				);
			}
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(Optional.ofNullable(value));
	}

	public static ParseResult<HudLayoutDefinition> parseLayout(
		final Identifier id,
		final String json
	) {
		IssueCollector issues = new IssueCollector();
		ParsedRoot parsed = parseRoot(id, json, issues);
		if (parsed == null) {
			return issues.result(Optional.empty());
		}
		HudLayoutDefinition value = null;
		try {
			ObjectReader reader = new ObjectReader(parsed.root(), "", issues);
			checkFormat(reader, issues);
			Surface surface = reader.requiredEnum("surface", Surface.class);
			JsonObject audienceObject = reader.optionalObject("audience");
			Optional<HudAudience> audience = audienceObject == null
				? Optional.empty()
				: Optional.of(parseAudience(audienceObject, "/audience", issues));
			Identifier root = reader.requiredIdentifier("root");
			Optional<Identifier> compactRoot = Optional.ofNullable(reader.optionalIdentifier("compact_root"));
			Optional<Identifier> summaryRoot = Optional.ofNullable(reader.optionalIdentifier("summary_root"));
			Optional<Identifier> degradation = Optional.ofNullable(
				reader.optionalIdentifier("degradation_layout")
			);
			JsonObject sizeObject = reader.requiredObject("size");
			JsonObject transitionObject = reader.optionalObject("transition");
			LayoutSize size = parseLayoutSize(sizeObject, "/size", surface, issues);
			LayoutTransition transition = parseLayoutTransition(
				transitionObject,
				"/transition",
				issues
			);
			reader.finish("HUD layout");
			if (surface != null && root != null && size != null) {
				value = new HudLayoutDefinition(
					id,
					surface,
					audience,
					root,
					compactRoot,
					summaryRoot,
					degradation,
					size,
					transition,
					parsed.canonical(),
					parsed.sha256()
				);
			}
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(Optional.ofNullable(value));
	}

	public static ParseResult<HudProfileDefinition> parseProfile(
		final Identifier id,
		final String json
	) {
		IssueCollector issues = new IssueCollector();
		ParsedRoot parsed = parseRoot(id, json, issues);
		if (parsed == null) {
			return issues.result(Optional.empty());
		}
		HudProfileDefinition value = null;
		try {
			ObjectReader reader = new ObjectReader(parsed.root(), "", issues);
			checkFormat(reader, issues);
			Optional<Identifier> defaultLayout = Optional.ofNullable(reader.optionalIdentifier("default_layout"));
			JsonArray routesArray = reader.optionalArray("routes");
			JsonObject policyObject = reader.optionalObject("client_policy");
			List<HudRoute> routes = parseRoutes(routesArray, "/routes", issues);
			HudClientPolicy policy = parseClientPolicy(policyObject, "/client_policy", issues);
			reader.finish("HUD profile");
			value = new HudProfileDefinition(
				id,
				defaultLayout,
				routes,
				policy,
				parsed.canonical(),
				parsed.sha256()
			);
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(Optional.ofNullable(value));
	}

	public static ParseResult<CountdownDefinition> parseCountdown(
		final Identifier id,
		final String json
	) {
		IssueCollector issues = new IssueCollector();
		ParsedRoot parsed = parseRoot(id, json, issues);
		if (parsed == null) {
			return issues.result(Optional.empty());
		}
		CountdownDefinition value = null;
		try {
			ObjectReader reader = new ObjectReader(parsed.root(), "", issues);
			checkFormat(reader, issues);
			RichText name = parseRichText(reader.requiredElement("name"), "/name", issues);
			long duration = parseDuration(
				reader.optionalElement("duration"),
				"/duration",
				200L,
				false,
				issues
			);
			Identifier layout = reader.requiredIdentifier("layout");
			JsonObject audienceObject = reader.optionalObject("display_audience");
			HudAudience audience = audienceObject == null
				? HudAudience.frozenParticipants()
				: parseAudience(audienceObject, "/display_audience", issues);
			JsonObject disconnectObject = reader.optionalObject("disconnect");
			JsonObject restrictionsObject = reader.optionalObject("restrictions");
			JsonArray checkpointsArray = reader.optionalArray("checkpoints");
			JsonObject callbacksObject = reader.optionalObject("callbacks");
			DisconnectPolicy disconnect = parseDisconnect(disconnectObject, "/disconnect", issues);
			CountdownRestrictions restrictions = parseRestrictions(
				restrictionsObject,
				"/restrictions",
				issues
			);
			List<CountdownCheckpoint> checkpoints = parseCheckpoints(
				checkpointsArray,
				"/checkpoints",
				duration,
				issues
			);
			Map<CountdownCallbackSlot, List<CountdownCallback>> callbacks = parseCallbacks(
				callbacksObject,
				"/callbacks",
				issues
			);
			reader.finish("countdown");
			if (name != null && layout != null) {
				value = new CountdownDefinition(
					id,
					name,
					duration,
					layout,
					audience,
					disconnect,
					restrictions,
					checkpoints,
					callbacks,
					parsed.canonical(),
					parsed.sha256()
				);
			}
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(Optional.ofNullable(value));
	}

	private static ComponentBody parseComponentBody(
		final ComponentType type,
		final ObjectReader reader,
		final JsonObject styleObject,
		final Map<String, HudBinding> bindings,
		final IssueCollector issues
	) {
		return switch (type) {
			case TEXT -> parseTextBody(reader, issues);
			case IMAGE -> parseImageBody(reader, styleObject, issues);
			case PLAYER_HEAD -> parsePlayerHeadBody(reader, styleObject, issues);
			case COUNTER -> parseCounterBody(reader, issues);
			case BADGE -> parseBadgeBody(reader, issues);
			case PROGRESS -> parseProgressBody(reader, issues);
			case TIMER -> parseTimerBody(reader, issues);
			case SEPARATOR -> parseSeparatorBody(reader, issues);
			case BACKGROUND -> parseBackgroundBody(reader, styleObject, issues);
			case ROW, COLUMN -> parseContainerBody(reader, styleObject, issues);
			case OVERLAY -> parseOverlayBody(reader, issues);
			case REPEAT -> parseRepeatBody(reader, bindings, issues);
		};
	}

	private static TextBody parseTextBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		JsonObject textObject = reader.requiredObject("text");
		JsonObject layoutObject = reader.requiredObject("layout");
		HudTextTemplate text = parseTextTemplate(textObject, "/text", issues);
		TextLayout layout = parseTextLayout(layoutObject, "/layout", issues);
		return text == null || layout == null ? null : new TextBody(text, layout);
	}

	private static HudTextTemplate parseTextTemplate(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonArray parts = reader.requiredArray("parts");
		reader.finish("HUD text template");
		if (parts == null) {
			return null;
		}
		if (parts.isEmpty() || parts.size() > MAX_TEMPLATE_PARTS) {
			issues.add(
				pointer + "/parts",
				"RESOURCE_LIMIT",
				"text parts must contain 1.." + MAX_TEMPLATE_PARTS + " entries"
			);
		}
		Set<String> references = new LinkedHashSet<>();
		int limit = Math.min(parts.size(), MAX_TEMPLATE_PARTS);
		for (int index = 0; index < limit; index++) {
			JsonElement element = parts.get(index);
			String partPointer = pointer + "/parts/" + index;
			if (!element.isJsonObject()) {
				issues.add(partPointer, "TYPE_MISMATCH", "text part must be an object");
				continue;
			}
			ObjectReader part = new ObjectReader(element.getAsJsonObject(), partPointer, issues);
			JsonElement component = part.optionalElement("component");
			JsonObject field = part.optionalObject("field");
			part.finish("HUD text part");
			if ((component == null) == (field == null)) {
				issues.add(
					partPointer,
					"INVALID_ONE_OF",
					"text part must declare exactly one of component or field"
				);
				continue;
			}
			if (component != null) {
				parseRichText(component, partPointer + "/component", issues);
				continue;
			}
			ObjectReader fieldReader = new ObjectReader(field, partPointer + "/field", issues);
			String scope = fieldReader.optionalString("scope", "component");
			String fieldId = fieldReader.requiredString("id");
			fieldReader.finish("HUD text field");
			if (!"component".equals(scope)) {
				issues.add(
					partPointer + "/field/scope",
					"INVALID_ENUM",
					"HUD text fields only support component scope"
				);
			}
			if (validateLocalId(fieldId, partPointer + "/field/id", issues)) {
				references.add(fieldId);
			}
		}
		return new HudTextTemplate(canonical(object), references);
	}

	private static TextLayout parseTextLayout(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TextAlignment alignment = reader.optionalEnum(
			"alignment",
			TextAlignment.class,
			TextAlignment.LEFT
		);
		int maxLines = reader.optionalInteger("max_lines", 1);
		TextOverflow overflow = reader.optionalEnum(
			"overflow",
			TextOverflow.class,
			TextOverflow.ELLIPSIS
		);
		reader.finish("HUD text layout");
		if (maxLines < 1 || maxLines > 16) {
			issues.add(pointer + "/max_lines", "OUT_OF_RANGE", "max_lines must be between 1 and 16");
			maxLines = Math.max(1, Math.min(maxLines, 16));
		}
		return new TextLayout(alignment, maxLines, overflow);
	}

	private static ImageBody parseImageBody(
		final ObjectReader reader,
		final JsonObject styleObject,
		final IssueCollector issues
	) {
		Identifier texture = reader.requiredIdentifier("texture");
		JsonObject sizeObject = reader.requiredObject("size");
		String tint = reader.optionalString("tint", "#FFFFFFFF");
		ImageFit fit = reader.optionalEnum("fit", ImageFit.class, ImageFit.CONTAIN);
		RichText alt = parseRichText(reader.requiredElement("alt"), "/alt", issues);
		JsonObject missingObject = reader.optionalObject("on_asset_missing");
		IntSize size = parseIntSize(sizeObject, "/size", MAX_IMAGE_SIZE, issues);
		AssetMissingPolicy missing = parseAssetMissing(missingObject, "/on_asset_missing", issues);
		validateColor(tint, "/tint", issues);
		if (styleObject != null && (styleObject.has("width") || styleObject.has("height"))) {
			issues.add(
				"/style",
				"CONFLICTING_FIELD",
				"image size and style width/height cannot both be declared"
			);
		}
		return texture == null || size == null || alt == null
			? null
			: new ImageBody(texture, size, tint, fit, alt, missing);
	}

	private static AssetMissingPolicy parseAssetMissing(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return new AssetMissingPolicy(
				AssetMissingMode.HIDE_COMPONENT,
				Optional.empty(),
				Optional.empty()
			);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		AssetMissingMode mode = reader.requiredEnum("mode", AssetMissingMode.class);
		Optional<Identifier> placeholder = Optional.ofNullable(reader.optionalIdentifier("texture"));
		Optional<Identifier> degradation = Optional.ofNullable(reader.optionalIdentifier("layout"));
		reader.finish("asset missing policy");
		if (mode == AssetMissingMode.PLACEHOLDER && placeholder.isEmpty()) {
			issues.add(pointer + "/texture", "MISSING_REQUIRED", "placeholder mode requires texture");
		}
		if (mode == AssetMissingMode.DEGRADATION_LAYOUT && degradation.isEmpty()) {
			issues.add(pointer + "/layout", "MISSING_REQUIRED", "degradation mode requires layout");
		}
		if (mode != AssetMissingMode.PLACEHOLDER && placeholder.isPresent()) {
			issues.add(pointer + "/texture", "INVALID_CONSTRAINT", "texture is only valid for placeholder mode");
		}
		if (mode != AssetMissingMode.DEGRADATION_LAYOUT && degradation.isPresent()) {
			issues.add(pointer + "/layout", "INVALID_CONSTRAINT", "layout is only valid for degradation mode");
		}
		return new AssetMissingPolicy(
			mode == null ? AssetMissingMode.HIDE_COMPONENT : mode,
			placeholder,
			degradation
		);
	}

	private static PlayerHeadBody parsePlayerHeadBody(
		final ObjectReader reader,
		final JsonObject styleObject,
		final IssueCollector issues
	) {
		FieldReference player = parseFieldReference(reader.requiredObject("player"), "/player", issues);
		int size = reader.optionalInteger("size", 16);
		JsonArray layersArray = reader.optionalArray("layers");
		OfflineHeadStyle offline = reader.optionalEnum(
			"offline_style",
			OfflineHeadStyle.class,
			OfflineHeadStyle.GRAYSCALE
		);
		Set<HeadLayer> layers = parseEnumSet(
			layersArray,
			"/layers",
			HeadLayer.class,
			Set.of(HeadLayer.BASE, HeadLayer.HAT),
			issues
		);
		if (!layers.containsAll(Set.of(HeadLayer.BASE, HeadLayer.HAT))) {
			issues.add("/layers", "INVALID_CONSTRAINT", "player head must include base and hat layers");
		}
		if (size < 8 || size > 128) {
			issues.add("/size", "OUT_OF_RANGE", "player head size must be between 8 and 128");
			size = Math.max(8, Math.min(size, 128));
		}
		if (styleObject != null && (styleObject.has("width") || styleObject.has("height"))) {
			issues.add(
				"/style",
				"CONFLICTING_FIELD",
				"player head size and style width/height cannot both be declared"
			);
		}
		return player == null ? null : new PlayerHeadBody(player, size, layers, offline);
	}

	private static CounterBody parseCounterBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		RichText label = parseRichText(reader.requiredElement("label"), "/label", issues);
		FieldReference value = parseFieldReference(reader.requiredObject("value"), "/value", issues);
		JsonElement suffixElement = reader.optionalElement("suffix");
		Optional<RichText> suffix = suffixElement == null
			? Optional.empty()
			: Optional.ofNullable(parseRichText(suffixElement, "/suffix", issues));
		return label == null || value == null ? null : new CounterBody(label, value, suffix);
	}

	private static BadgeBody parseBadgeBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		FieldReference value = parseFieldReference(reader.requiredObject("value"), "/value", issues);
		JsonObject variantsObject = reader.requiredObject("variants");
		JsonObject unknownObject = reader.optionalObject("unknown");
		Map<Identifier, BadgeVariant> variants = parseBadgeVariants(variantsObject, "/variants", issues);
		UnknownBadgeMode unknown = parseUnknownBadge(unknownObject, "/unknown", issues);
		return value == null ? null : new BadgeBody(value, variants, unknown);
	}

	private static Map<Identifier, BadgeVariant> parseBadgeVariants(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return Map.of();
		}
		if (object.isEmpty()) {
			issues.add(pointer, "OUT_OF_RANGE", "badge variants must not be empty");
		}
		if (object.size() > 64) {
			issues.add(pointer, "RESOURCE_LIMIT", "badge variants exceed 64 entries");
		}
		Map<Identifier, BadgeVariant> result = new LinkedHashMap<>();
		int index = 0;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (index++ >= 64) {
				break;
			}
			String entryPointer = pointer + "/" + escapePointer(entry.getKey());
			Identifier key = parseIdentifier(entry.getKey());
			if (key == null) {
				issues.add(entryPointer, "INVALID_IDENTIFIER", "variant key must include an explicit namespace");
				continue;
			}
			if (!entry.getValue().isJsonObject()) {
				issues.add(entryPointer, "TYPE_MISMATCH", "badge variant must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(entry.getValue().getAsJsonObject(), entryPointer, issues);
			RichText label = parseRichText(reader.requiredElement("label"), entryPointer + "/label", issues);
			String color = reader.requiredString("color");
			reader.finish("badge variant");
			validateColor(color, entryPointer + "/color", issues);
			if (label != null && color != null) {
				result.put(key, new BadgeVariant(label, color));
			}
		}
		return immutableMap(result);
	}

	private static UnknownBadgeMode parseUnknownBadge(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return UnknownBadgeMode.HIDE_COMPONENT;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		UnknownBadgeMode mode = reader.optionalEnum(
			"mode",
			UnknownBadgeMode.class,
			UnknownBadgeMode.HIDE_COMPONENT
		);
		reader.finish("unknown badge policy");
		return mode;
	}

	private static ProgressBody parseProgressBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		FieldReference current = parseFieldReference(reader.requiredObject("current"), "/current", issues);
		FieldReference maximum = parseFieldReference(reader.requiredObject("maximum"), "/maximum", issues);
		Orientation orientation = reader.optionalEnum(
			"orientation",
			Orientation.class,
			Orientation.HORIZONTAL
		);
		int segments = reader.optionalInteger("segments", 0);
		ProgressLabel label = reader.optionalEnum("label", ProgressLabel.class, ProgressLabel.NONE);
		ProgressInterpolation interpolation = reader.optionalEnum(
			"interpolation",
			ProgressInterpolation.class,
			ProgressInterpolation.NONE
		);
		if (segments < 0 || segments > MAX_SEGMENTS) {
			issues.add("/segments", "OUT_OF_RANGE", "segments must be between 0 and " + MAX_SEGMENTS);
			segments = Math.max(0, Math.min(segments, MAX_SEGMENTS));
		}
		return current == null || maximum == null
			? null
			: new ProgressBody(current, maximum, orientation, segments, label, interpolation);
	}

	private static TimerBody parseTimerBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		FieldReference clock = parseFieldReference(reader.requiredObject("clock"), "/clock", issues);
		TimerDirection direction = reader.optionalEnum(
			"direction",
			TimerDirection.class,
			TimerDirection.ELAPSED
		);
		String formatValue = reader.optionalString("format", "m:ss");
		TimerFormat format = switch (formatValue == null ? "" : formatValue) {
			case "m:ss" -> TimerFormat.M_SS;
			case "h:mm:ss" -> TimerFormat.H_MM_SS;
			case "localized_duration" -> TimerFormat.LOCALIZED_DURATION;
			default -> {
				issues.add("/format", "INVALID_ENUM", "unsupported timer format " + formatValue);
				yield TimerFormat.M_SS;
			}
		};
		JsonElement markerElement = reader.optionalElement("paused_marker");
		Optional<RichText> marker = markerElement == null
			? Optional.empty()
			: Optional.ofNullable(parseRichText(markerElement, "/paused_marker", issues));
		return clock == null ? null : new TimerBody(clock, direction, format, marker);
	}

	private static SeparatorBody parseSeparatorBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		Orientation orientation = reader.optionalEnum(
			"orientation",
			Orientation.class,
			Orientation.HORIZONTAL
		);
		int thickness = reader.optionalInteger("thickness", 1);
		String color = reader.requiredString("color");
		if (thickness < 1 || thickness > 32) {
			issues.add("/thickness", "OUT_OF_RANGE", "separator thickness must be between 1 and 32");
			thickness = Math.max(1, Math.min(thickness, 32));
		}
		validateColor(color, "/color", issues);
		return color == null ? null : new SeparatorBody(orientation, thickness, color);
	}

	private static BackgroundBody parseBackgroundBody(
		final ObjectReader reader,
		final JsonObject styleObject,
		final IssueCollector issues
	) {
		Identifier child = reader.requiredIdentifier("child");
		String fill = reader.requiredString("fill");
		JsonObject borderObject = reader.requiredObject("border");
		JsonObject paddingObject = reader.optionalObject("padding");
		Border border = parseBorder(borderObject, "/border", issues);
		Insets padding = parseInsets(paddingObject, "/padding", issues);
		validateColor(fill, "/fill", issues);
		if (
			styleObject != null
				&& (styleObject.has("background_color") || styleObject.has("border_color") || styleObject.has("padding"))
		) {
			issues.add(
				"/style",
				"CONFLICTING_FIELD",
				"background fill/border/padding cannot be duplicated in style"
			);
		}
		return child == null || fill == null || border == null
			? null
			: new BackgroundBody(child, fill, border, padding);
	}

	private static ContainerBody parseContainerBody(
		final ObjectReader reader,
		final JsonObject styleObject,
		final IssueCollector issues
	) {
		JsonArray childrenArray = reader.requiredArray("children");
		int gap = reader.optionalInteger("gap", 0);
		ContainerAlignment alignment = reader.optionalEnum(
			"alignment",
			ContainerAlignment.class,
			ContainerAlignment.START
		);
		JsonArray weightsArray = reader.optionalArray("weights");
		List<Identifier> children = parseIdentifierList(
			childrenArray,
			"/children",
			HudDefinitions.MAX_CONTAINER_CHILDREN,
			true,
			issues
		);
		List<Double> weights = parseWeights(weightsArray, "/weights", children.size(), issues);
		if (gap < 0 || gap > MAX_GAP) {
			issues.add("/gap", "OUT_OF_RANGE", "container gap must be between 0 and " + MAX_GAP);
			gap = Math.max(0, Math.min(gap, MAX_GAP));
		}
		if (styleObject != null && styleObject.has("gap")) {
			issues.add("/style/gap", "CONFLICTING_FIELD", "container gap cannot also be declared in style");
		}
		return children.isEmpty() ? null : new ContainerBody(children, gap, alignment, weights);
	}

	private static OverlayBody parseOverlayBody(
		final ObjectReader reader,
		final IssueCollector issues
	) {
		JsonArray layersArray = reader.requiredArray("layers");
		OverlayAlignment alignment = reader.optionalEnum(
			"alignment",
			OverlayAlignment.class,
			OverlayAlignment.CENTER
		);
		List<Identifier> layers = parseIdentifierList(
			layersArray,
			"/layers",
			HudDefinitions.MAX_OVERLAY_LAYERS,
			true,
			issues
		);
		return layers.isEmpty() ? null : new OverlayBody(layers, alignment);
	}

	private static RepeatBody parseRepeatBody(
		final ObjectReader reader,
		final Map<String, HudBinding> bindings,
		final IssueCollector issues
	) {
		FieldReference items = parseFieldReference(reader.requiredObject("items"), "/items", issues);
		Identifier itemComponent = reader.requiredIdentifier("item_component");
		RepeatLayout layout = reader.optionalEnum("layout", RepeatLayout.class, RepeatLayout.COLUMN);
		int maximumItems = reader.requiredInteger("maximum_items", 1);
		Optional<Identifier> overflow = Optional.ofNullable(reader.optionalIdentifier("overflow_component"));
		if (maximumItems < 1 || maximumItems > HudDefinitions.MAX_REPEAT_ITEMS) {
			issues.add(
				"/maximum_items",
				"OUT_OF_RANGE",
				"maximum_items must be between 1 and " + HudDefinitions.MAX_REPEAT_ITEMS
			);
			maximumItems = Math.max(1, Math.min(maximumItems, HudDefinitions.MAX_REPEAT_ITEMS));
		}
		if (items != null) {
			HudBinding binding = bindings.get(items.field());
			if (
				binding != null
					&& (binding.valueType() != HudValueType.LIST || binding.itemType().orElse(null) != HudValueType.OBJECT)
			) {
				issues.add("/items/field", "TYPE_MISMATCH", "repeat items requires list<object> binding");
			}
		}
		return items == null || itemComponent == null
			? null
			: new RepeatBody(
				items,
				itemComponent,
				layout,
				maximumItems,
				overflow.isPresent() ? RepeatOverflowMode.COMPONENT : RepeatOverflowMode.HIDE,
				overflow
			);
	}

	private static Map<String, HudBinding> parseBindings(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return Map.of();
		}
		if (object.size() > HudDefinitions.MAX_BINDINGS) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"bindings exceed " + HudDefinitions.MAX_BINDINGS + " entries"
			);
		}
		Map<String, HudBinding> result = new LinkedHashMap<>();
		int count = 0;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (count++ >= HudDefinitions.MAX_BINDINGS) {
				break;
			}
			String id = entry.getKey();
			String bindingPointer = pointer + "/" + escapePointer(id);
			if (!validateLocalId(id, bindingPointer, issues)) {
				continue;
			}
			if (!entry.getValue().isJsonObject()) {
				issues.add(bindingPointer, "TYPE_MISMATCH", "binding must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(entry.getValue().getAsJsonObject(), bindingPointer, issues);
			HudValueType valueType = reader.requiredEnum("value_type", HudValueType.class);
			HudValueType itemType = reader.optionalEnum("item_type", HudValueType.class, null);
			JsonObject sourceObject = reader.requiredObject("source");
			JsonObject audienceObject = reader.optionalObject("audience");
			boolean sensitive = reader.optionalBoolean("sensitive", false);
			boolean critical = reader.optionalBoolean("critical", false);
			JsonObject missingObject = reader.requiredObject("on_missing");
			reader.finish("HUD binding");
			HudBindingSource source = parseBindingSource(
				sourceObject,
				bindingPointer + "/source",
				issues
			);
			Optional<HudAudience> audience = audienceObject == null
				? Optional.empty()
				: Optional.of(parseAudience(audienceObject, bindingPointer + "/audience", issues));
			if (valueType == HudValueType.LIST && itemType == null) {
				issues.add(bindingPointer + "/item_type", "MISSING_REQUIRED", "list binding requires item_type");
			}
			if (valueType != HudValueType.LIST && itemType != null) {
				issues.add(
					bindingPointer + "/item_type",
					"INVALID_CONSTRAINT",
					"item_type is only valid for list bindings"
				);
			}
			MissingPolicy missing = parseMissingPolicy(
				missingObject,
				bindingPointer + "/on_missing",
				valueType,
				source,
				sensitive,
				critical,
				issues
			);
			if (valueType != null && source != null) {
				result.put(
					id,
					new HudBinding(
						id,
						valueType,
						Optional.ofNullable(itemType),
						source,
						audience,
						sensitive,
						critical,
						missing
					)
				);
			}
		}
		return immutableMap(result);
	}

	private static HudBindingSource parseBindingSource(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		HudBindingSourceType type = reader.requiredEnum("type", HudBindingSourceType.class);
		String value = reader.optionalString("value");
		Identifier id = reader.optionalIdentifier("id");
		Identifier field = reader.optionalIdentifier("field");
		String objective = reader.optionalString("objective");
		String holder = reader.optionalString("holder");
		Identifier storage = reader.optionalIdentifier("storage");
		String path = reader.optionalString("path");
		String target = reader.optionalString("target");
		reader.finish("HUD binding source");
		if (type == null) {
			return null;
		}
		Set<String> allowed = switch (type) {
			case GAME_FACT, PHASE_FACT, TASK_FACT, PLAYER_FACT, RESULT, CLOCK -> Set.of("value");
			case EVENT -> Set.of("id", "value");
			case STATISTIC -> Set.of("id");
			case PLAYER_DATA -> Set.of("field");
			case SCORE -> Set.of("objective", "holder");
			case STORAGE -> Set.of("storage", "path");
			case ENTITY_DATA -> Set.of("target", "path");
		};
		Map<String, Object> populated = new LinkedHashMap<>();
		putIfNonNull(populated, "value", value);
		putIfNonNull(populated, "id", id);
		putIfNonNull(populated, "field", field);
		putIfNonNull(populated, "objective", objective);
		putIfNonNull(populated, "holder", holder);
		putIfNonNull(populated, "storage", storage);
		putIfNonNull(populated, "path", path);
		putIfNonNull(populated, "target", target);
		for (String key : populated.keySet()) {
			if (!allowed.contains(key)) {
				issues.add(pointer + "/" + key, "INVALID_CONSTRAINT", key + " is not valid for " + serialized(type));
			}
		}
		for (String key : allowed) {
			if (!populated.containsKey(key)) {
				issues.add(pointer + "/" + key, "MISSING_REQUIRED", serialized(type) + " requires " + key);
			}
		}
		if (value != null && value.length() > 128) {
			issues.add(pointer + "/value", "OUT_OF_RANGE", "source value exceeds 128 characters");
		}
		if (type == HudBindingSourceType.CLOCK && value != null && !Set.of(
			"game", "task", "interval", "warmup", "countdown"
		).contains(value)) {
			issues.add(pointer + "/value", "INVALID_ENUM", "unsupported clock source " + value);
		}
		if (objective != null && !SCORE_OBJECTIVE.matcher(objective).matches()) {
			issues.add(pointer + "/objective", "INVALID_VALUE", "invalid scoreboard objective");
		}
		if (holder != null && !SCORE_HOLDER.matcher(holder).matches()) {
			issues.add(pointer + "/holder", "INVALID_VALUE", "unsafe scoreboard holder");
		}
		if (path != null && !SAFE_PATH.matcher(path).matches()) {
			issues.add(pointer + "/path", "INVALID_VALUE", "unsafe data path");
		}
		if (target != null && !SAFE_TARGET.matcher(target).matches()) {
			issues.add(pointer + "/target", "INVALID_VALUE", "unsafe entity target token");
		}
		return new HudBindingSource(
			type,
			Optional.ofNullable(value),
			Optional.ofNullable(id),
			Optional.ofNullable(field),
			Optional.ofNullable(objective),
			Optional.ofNullable(holder),
			Optional.ofNullable(storage),
			Optional.ofNullable(path),
			Optional.ofNullable(target)
		);
	}

	private static MissingPolicy parseMissingPolicy(
		final JsonObject object,
		final String pointer,
		final HudValueType valueType,
		final HudBindingSource source,
		final boolean sensitive,
		final boolean critical,
		final IssueCollector issues
	) {
		if (object == null) {
			return new MissingPolicy(
				MissingMode.HIDE_COMPONENT,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		MissingMode mode = reader.requiredEnum("mode", MissingMode.class);
		JsonElement placeholder = reader.optionalElement("value");
		Identifier degradation = reader.optionalIdentifier("layout");
		JsonElement ttlElement = reader.optionalElement("ttl");
		JsonElement indicatorElement = reader.optionalElement("indicator");
		reader.finish("HUD missing policy");
		String placeholderCanonical = placeholder == null ? null : canonical(placeholder);
		Long ttl = ttlElement == null
			? null
			: parseDuration(ttlElement, pointer + "/ttl", 0L, false, issues);
		RichText indicator = indicatorElement == null
			? null
			: parseRichText(indicatorElement, pointer + "/indicator", issues);
		MissingMode effective = mode == null ? MissingMode.HIDE_COMPONENT : mode;
		if (effective == MissingMode.PLACEHOLDER) {
			if (placeholder == null) {
				issues.add(pointer + "/value", "MISSING_REQUIRED", "placeholder mode requires value");
			} else if (valueType != null && !placeholderMatches(valueType, placeholder)) {
				issues.add(pointer + "/value", "TYPE_MISMATCH", "placeholder does not match binding value_type");
			}
		} else if (placeholder != null) {
			issues.add(pointer + "/value", "INVALID_CONSTRAINT", "value is only valid for placeholder mode");
		}
		if (effective == MissingMode.DEGRADATION_LAYOUT) {
			if (degradation == null) {
				issues.add(pointer + "/layout", "MISSING_REQUIRED", "degradation mode requires layout");
			}
		} else if (degradation != null) {
			issues.add(pointer + "/layout", "INVALID_CONSTRAINT", "layout is only valid for degradation mode");
		}
		if (effective == MissingMode.RETAIN_STALE) {
			if (ttl == null || ttl <= 0L || ttl > MAX_STALE_TTL_TICKS) {
				issues.add(
					pointer + "/ttl",
					"OUT_OF_RANGE",
					"retain_stale ttl must be between 1 and " + MAX_STALE_TTL_TICKS + " ticks"
				);
			}
			if (indicator == null) {
				issues.add(pointer + "/indicator", "MISSING_REQUIRED", "retain_stale requires indicator");
			}
			if (sensitive || critical) {
				issues.add(pointer, "INVALID_CONSTRAINT", "sensitive or critical bindings cannot retain stale values");
			}
			if (
				source != null
					&& source.type() == HudBindingSourceType.CLOCK
					&& source.value().filter("countdown"::equals).isPresent()
			) {
				issues.add(pointer, "INVALID_CONSTRAINT", "countdown clock cannot retain stale values");
			}
		} else {
			if (ttlElement != null) {
				issues.add(pointer + "/ttl", "INVALID_CONSTRAINT", "ttl is only valid for retain_stale mode");
			}
			if (indicatorElement != null) {
				issues.add(
					pointer + "/indicator",
					"INVALID_CONSTRAINT",
					"indicator is only valid for retain_stale mode"
				);
			}
		}
		return new MissingPolicy(
			effective,
			Optional.ofNullable(placeholderCanonical),
			Optional.ofNullable(degradation),
			Optional.ofNullable(ttl),
			Optional.ofNullable(indicator)
		);
	}

	private static boolean placeholderMatches(final HudValueType type, final JsonElement value) {
		return switch (type) {
			case STRING -> isString(value);
			case INTEGER -> integralLong(value) != null;
			case DECIMAL -> finiteNumber(value) != null;
			case BOOLEAN -> isBoolean(value);
			case IDENTIFIER -> isString(value) && parseIdentifier(value.getAsString()) != null;
			case COMPONENT -> value.isJsonObject() || value.isJsonArray() || isString(value);
			case DURATION -> parseDurationQuiet(value) != null;
			case LIST -> value.isJsonArray();
			case OBJECT -> value.isJsonObject();
			case PLAYER, CLOCK -> false;
		};
	}

	private static HudAudience parseAudience(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonArray anyOf = reader.optionalArray("any_of");
		boolean hasDirect = object.keySet().stream().anyMatch(key -> !"any_of".equals(key));
		HudAudienceSelector direct = parseAudienceSelector(reader, pointer, issues);
		reader.finish("HUD audience");
		if (anyOf == null) {
			return new HudAudience(List.of(direct));
		}
		if (hasDirect) {
			issues.add(pointer, "INVALID_ONE_OF", "audience cannot mix any_of with direct selector keys");
		}
		if (anyOf.isEmpty() || anyOf.size() > MAX_AUDIENCE_SELECTORS) {
			issues.add(
				pointer + "/any_of",
				"RESOURCE_LIMIT",
				"audience any_of must contain 1.." + MAX_AUDIENCE_SELECTORS + " selectors"
			);
		}
		List<HudAudienceSelector> selectors = new ArrayList<>();
		int limit = Math.min(anyOf.size(), MAX_AUDIENCE_SELECTORS);
		for (int index = 0; index < limit; index++) {
			JsonElement element = anyOf.get(index);
			String entryPointer = pointer + "/any_of/" + index;
			if (!element.isJsonObject()) {
				issues.add(entryPointer, "TYPE_MISMATCH", "audience selector must be an object");
				continue;
			}
			ObjectReader selectorReader = new ObjectReader(element.getAsJsonObject(), entryPointer, issues);
			selectors.add(parseAudienceSelector(selectorReader, entryPointer, issues));
			selectorReader.finish("HUD audience selector");
		}
		return selectors.isEmpty() ? HudAudience.allOnline() : new HudAudience(selectors);
	}

	private static HudAudienceSelector parseAudienceSelector(
		final ObjectReader reader,
		final String pointer,
		final IssueCollector issues
	) {
		AudienceSource source = reader.optionalEnum("source", AudienceSource.class, AudienceSource.ALL);
		boolean participants = reader.optionalBoolean("participants", false);
		Set<Identifier> roles = reader.optionalIdentifierSet("roles", MAX_IDENTIFIER_SET);
		Set<Identifier> teams = reader.optionalIdentifierSet("teams", MAX_IDENTIFIER_SET);
		Set<Identifier> lifeStates = reader.optionalIdentifierSet("life_states", MAX_IDENTIFIER_SET);
		Set<Identifier> roleTags = reader.optionalIdentifierSet("role_tags", MAX_IDENTIFIER_SET);
		Set<Identifier> teamTags = reader.optionalIdentifierSet("team_tags", MAX_IDENTIFIER_SET);
		Set<Identifier> lifeStateTags = reader.optionalIdentifierSet("life_state_tags", MAX_IDENTIFIER_SET);
		boolean excludeHost = reader.optionalBoolean("exclude_host", false);
		boolean onlineOnly = reader.optionalBoolean("online_only", true);
		if (source == AudienceSource.FROZEN_PARTICIPANTS) {
			participants = true;
		}
		return new HudAudienceSelector(
			source,
			participants,
			roles,
			teams,
			lifeStates,
			roleTags,
			teamTags,
			lifeStateTags,
			excludeHost,
			onlineOnly
		);
	}

	private static HudCondition parseCondition(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		return parseCondition(object, pointer, 0, new ConditionBudget(), issues);
	}

	private static HudCondition parseCondition(
		final JsonObject object,
		final String pointer,
		final int depth,
		final ConditionBudget budget,
		final IssueCollector issues
	) {
		if (depth > HudDefinitions.MAX_CONDITION_DEPTH) {
			issues.add(pointer, "RESOURCE_LIMIT", "condition nesting exceeds " + HudDefinitions.MAX_CONDITION_DEPTH);
			return HudCondition.always();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Set<Identifier> games = reader.optionalIdentifierSet("games", MAX_IDENTIFIER_SET);
		Set<Identifier> phases = reader.optionalIdentifierSet("phases", MAX_IDENTIFIER_SET);
		Set<Identifier> tasks = reader.optionalIdentifierSet("tasks", MAX_IDENTIFIER_SET);
		Set<TaskKind> taskKinds = parseEnumSet(
			reader.optionalArray("task_kinds"),
			pointer + "/task_kinds",
			TaskKind.class,
			Set.of(),
			issues
		);
		Set<TaskStatus> taskStatuses = parseEnumSet(
			reader.optionalArray("task_statuses"),
			pointer + "/task_statuses",
			TaskStatus.class,
			Set.of(),
			issues
		);
		Set<Identifier> roles = reader.optionalIdentifierSet("roles", MAX_IDENTIFIER_SET);
		Set<Identifier> teams = reader.optionalIdentifierSet("teams", MAX_IDENTIFIER_SET);
		Set<Identifier> lifeStates = reader.optionalIdentifierSet("life_states", MAX_IDENTIFIER_SET);
		Boolean host = reader.optionalBoolean("host");
		Boolean online = reader.optionalBoolean("online");
		JsonArray allArray = reader.optionalArray("all");
		JsonArray anyArray = reader.optionalArray("any");
		JsonObject notObject = reader.optionalObject("not");
		reader.finish("HUD condition");
		budget.add(
			games.size() + phases.size() + tasks.size() + taskKinds.size() + taskStatuses.size()
				+ roles.size() + teams.size() + lifeStates.size()
				+ (host == null ? 0 : 1) + (online == null ? 0 : 1),
			pointer,
			issues
		);
		List<HudCondition> all = parseConditionArray(allArray, pointer + "/all", depth, budget, issues);
		List<HudCondition> any = parseConditionArray(anyArray, pointer + "/any", depth, budget, issues);
		Optional<HudCondition> not = notObject == null
			? Optional.empty()
			: Optional.of(parseCondition(notObject, pointer + "/not", depth + 1, budget, issues));
		return new HudCondition(
			games,
			phases,
			tasks,
			taskKinds,
			taskStatuses,
			roles,
			teams,
			lifeStates,
			Optional.ofNullable(host),
			Optional.ofNullable(online),
			all,
			any,
			not
		);
	}

	private static List<HudCondition> parseConditionArray(
		final JsonArray array,
		final String pointer,
		final int depth,
		final ConditionBudget budget,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > HudDefinitions.MAX_CONDITION_TERMS) {
			issues.add(pointer, "RESOURCE_LIMIT", "condition array exceeds " + HudDefinitions.MAX_CONDITION_TERMS);
		}
		List<HudCondition> result = new ArrayList<>();
		int limit = Math.min(array.size(), HudDefinitions.MAX_CONDITION_TERMS);
		for (int index = 0; index < limit; index++) {
			JsonElement element = array.get(index);
			String entryPointer = pointer + "/" + index;
			if (!element.isJsonObject()) {
				issues.add(entryPointer, "TYPE_MISMATCH", "condition entry must be an object");
				continue;
			}
			budget.add(1, entryPointer, issues);
			result.add(parseCondition(element.getAsJsonObject(), entryPointer, depth + 1, budget, issues));
		}
		return List.copyOf(result);
	}

	private static HudStyle parseStyle(
		final JsonObject object,
		final String pointer,
		final ComponentType type,
		final IssueCollector issues
	) {
		if (object == null) {
			return HudStyle.standard();
		}
		Set<String> allowed = allowedStyleKeys(type);
		for (String key : object.keySet()) {
			if (!allowed.contains(key)) {
				issues.add(
					pointer + "/" + escapePointer(key),
					"UNKNOWN_KEY",
					"style key is not allowed for " + (type == null ? "unknown component" : serialized(type))
				);
			}
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String textColor = reader.optionalString("text_color");
		String secondary = reader.optionalString("secondary_text_color");
		String background = reader.optionalString("background_color");
		String border = reader.optionalString("border_color");
		String track = reader.optionalString("track_color");
		String fill = reader.optionalString("fill_color");
		String marker = reader.optionalString("marker_color");
		double opacity = reader.optionalDouble("opacity", 1.0D);
		FontWeight weight = reader.optionalEnum("font_weight", FontWeight.class, FontWeight.NORMAL);
		JsonObject paddingObject = reader.optionalObject("padding");
		int gap = reader.optionalInteger("gap", 0);
		Integer width = reader.optionalInteger("width");
		Integer height = reader.optionalInteger("height");
		reader.finish("HUD style");
		for (Map.Entry<String, String> color : Map.of(
			"text_color", textColor == null ? "" : textColor,
			"secondary_text_color", secondary == null ? "" : secondary,
			"background_color", background == null ? "" : background,
			"border_color", border == null ? "" : border,
			"track_color", track == null ? "" : track,
			"fill_color", fill == null ? "" : fill,
			"marker_color", marker == null ? "" : marker
		).entrySet()) {
			if (!color.getValue().isEmpty()) {
				validateColor(color.getValue(), pointer + "/" + color.getKey(), issues);
			}
		}
		if (opacity < 0.0D || opacity > 1.0D) {
			issues.add(pointer + "/opacity", "OUT_OF_RANGE", "opacity must be between 0 and 1");
			opacity = Math.max(0.0D, Math.min(opacity, 1.0D));
		}
		if (gap < 0 || gap > MAX_GAP) {
			issues.add(pointer + "/gap", "OUT_OF_RANGE", "gap must be between 0 and " + MAX_GAP);
			gap = Math.max(0, Math.min(gap, MAX_GAP));
		}
		width = boundedNullableSize(width, pointer + "/width", issues);
		height = boundedNullableSize(height, pointer + "/height", issues);
		return new HudStyle(
			Optional.ofNullable(textColor),
			Optional.ofNullable(secondary),
			Optional.ofNullable(background),
			Optional.ofNullable(border),
			Optional.ofNullable(track),
			Optional.ofNullable(fill),
			Optional.ofNullable(marker),
			opacity,
			weight,
			parseInsets(paddingObject, pointer + "/padding", issues),
			gap,
			Optional.ofNullable(width),
			Optional.ofNullable(height)
		);
	}

	private static Set<String> allowedStyleKeys(final ComponentType type) {
		Set<String> base = Set.of("opacity", "padding", "gap", "width", "height");
		if (type == null) {
			return Set.of();
		}
		Set<String> result = new LinkedHashSet<>(base);
		switch (type) {
			case TEXT, COUNTER, BADGE, TIMER -> result.addAll(
				Set.of("text_color", "secondary_text_color", "background_color", "border_color", "font_weight")
			);
			case PROGRESS -> result.addAll(
				Set.of(
					"text_color",
					"secondary_text_color",
					"background_color",
					"border_color",
					"track_color",
					"fill_color",
					"marker_color",
					"font_weight"
				)
			);
			case IMAGE, PLAYER_HEAD, BACKGROUND, ROW, COLUMN, OVERLAY, REPEAT -> result.addAll(
				Set.of("background_color", "border_color")
			);
			case SEPARATOR -> {
			}
		}
		return Set.copyOf(result);
	}

	private static ClientControl parseClientControl(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return ClientControl.locked();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		VisibilityControl visibility = reader.optionalEnum(
			"visibility",
			VisibilityControl.class,
			VisibilityControl.LOCKED_SHOWN
		);
		CompactControl compact = reader.optionalEnum(
			"compact",
			CompactControl.class,
			CompactControl.LOCKED
		);
		String reorderGroup = reader.optionalString("reorder_group");
		reader.finish("HUD client control");
		if (reorderGroup != null) {
			validateLocalId(reorderGroup, pointer + "/reorder_group", issues);
		}
		return new ClientControl(visibility, compact, Optional.ofNullable(reorderGroup));
	}

	private static LayoutSize parseLayoutSize(
		final JsonObject object,
		final String pointer,
		final Surface surface,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		int minimumWidth = reader.requiredInteger("minimum_width", 1);
		int preferredWidth = reader.requiredInteger("preferred_width", 1);
		int maximumWidth = reader.requiredInteger("maximum_width", 1);
		int maximumHeight = reader.requiredInteger("maximum_height", 1);
		Growth growth = reader.requiredEnum("growth", Growth.class);
		reader.finish("HUD layout size");
		if (
			minimumWidth < 1
				|| preferredWidth < minimumWidth
				|| maximumWidth < preferredWidth
				|| maximumWidth > MAX_LOGICAL_SIZE
		) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"layout widths must satisfy 1 <= minimum <= preferred <= maximum <= " + MAX_LOGICAL_SIZE
			);
		}
		if (maximumHeight < 1 || maximumHeight > MAX_LOGICAL_SIZE) {
			issues.add(
				pointer + "/maximum_height",
				"OUT_OF_RANGE",
				"maximum_height must be between 1 and " + MAX_LOGICAL_SIZE
			);
		}
		if (surface == Surface.DOCK && growth != Growth.UP) {
			issues.add(pointer + "/growth", "INVALID_CONSTRAINT", "dock layout growth must be up");
		}
		if (surface == Surface.COUNTDOWN && growth != Growth.CENTER) {
			issues.add(pointer + "/growth", "INVALID_CONSTRAINT", "countdown layout growth must be center");
		}
		if (growth == null) {
			return null;
		}
		return new LayoutSize(minimumWidth, preferredWidth, maximumWidth, maximumHeight, growth);
	}

	private static LayoutTransition parseLayoutTransition(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return LayoutTransition.none();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		EnterTransition enter = reader.optionalEnum("enter", EnterTransition.class, EnterTransition.NONE);
		ChangeTransition change = reader.optionalEnum("change", ChangeTransition.class, ChangeTransition.NONE);
		ExitTransition exit = reader.optionalEnum("exit", ExitTransition.class, ExitTransition.NONE);
		HudSound enterSound = parseHudSound(
			reader.optionalObject("enter_sound"),
			pointer + "/enter_sound",
			issues
		);
		HudSound changeSound = parseHudSound(
			reader.optionalObject("change_sound"),
			pointer + "/change_sound",
			issues
		);
		reader.finish("HUD transition");
		return new LayoutTransition(
			enter,
			change,
			exit,
			Optional.ofNullable(enterSound),
			Optional.ofNullable(changeSound)
		);
	}

	private static HudSound parseHudSound(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Identifier event = reader.requiredIdentifier("event");
		double volume = reader.optionalDouble("volume", 1.0D);
		double pitch = reader.optionalDouble("pitch", 1.0D);
		reader.finish("HUD transition sound");
		if (volume < 0.0D || volume > 4.0D) {
			issues.add(pointer + "/volume", "OUT_OF_RANGE", "sound volume must be between 0 and 4");
			volume = Math.clamp(volume, 0.0D, 4.0D);
		}
		if (pitch < 0.5D || pitch > 2.0D) {
			issues.add(pointer + "/pitch", "OUT_OF_RANGE", "sound pitch must be between 0.5 and 2");
			pitch = Math.clamp(pitch, 0.5D, 2.0D);
		}
		return event == null ? null : new HudSound(event, volume, pitch);
	}

	private static List<HudRoute> parseRoutes(
		final JsonArray array,
		final String pointer,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > HudDefinitions.MAX_ROUTES) {
			issues.add(pointer, "RESOURCE_LIMIT", "routes exceed " + HudDefinitions.MAX_ROUTES + " entries");
		}
		List<HudRoute> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		int limit = Math.min(array.size(), HudDefinitions.MAX_ROUTES);
		for (int index = 0; index < limit; index++) {
			JsonElement element = array.get(index);
			String routePointer = pointer + "/" + index;
			if (!element.isJsonObject()) {
				issues.add(routePointer, "TYPE_MISMATCH", "route must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(element.getAsJsonObject(), routePointer, issues);
			String id = reader.requiredString("id");
			RouteTier tier = reader.requiredEnum("tier", RouteTier.class);
			int priority = reader.requiredInteger("priority", 0);
			JsonObject whenObject = reader.optionalObject("when");
			Identifier layout = reader.requiredIdentifier("layout");
			JsonObject audienceObject = reader.optionalObject("audience");
			reader.finish("HUD route");
			validateLocalId(id, routePointer + "/id", issues);
			if (id != null && !ids.add(id)) {
				issues.add(routePointer + "/id", "DUPLICATE_VALUE", "route id is duplicated");
			}
			if (priority < MIN_ROUTE_PRIORITY || priority > MAX_ROUTE_PRIORITY) {
				issues.add(
					routePointer + "/priority",
					"OUT_OF_RANGE",
					"route priority must be between " + MIN_ROUTE_PRIORITY + " and " + MAX_ROUTE_PRIORITY
				);
			}
			HudCondition when = whenObject == null
				? HudCondition.always()
				: parseCondition(whenObject, routePointer + "/when", issues);
			if (tier == RouteTier.TASK && !conditionUsesTask(when)) {
				issues.add(routePointer + "/when", "INVALID_CONSTRAINT", "task route must constrain current task");
			}
			if (tier == RouteTier.CONTEXT && conditionUsesTask(when)) {
				issues.add(routePointer + "/when", "INVALID_CONSTRAINT", "context route cannot use task conditions");
			}
			Optional<HudAudience> audience = audienceObject == null
				? Optional.empty()
				: Optional.of(parseAudience(audienceObject, routePointer + "/audience", issues));
			if (id != null && tier != null && layout != null) {
				result.add(new HudRoute(id, tier, priority, when, layout, audience));
			}
		}
		return List.copyOf(result);
	}

	private static HudClientPolicy parseClientPolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return HudClientPolicy.safeDefault();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		boolean allowHide = reader.optionalBoolean("allow_hide", false);
		Anchor defaultAnchor = reader.optionalEnum("default_anchor", Anchor.class, Anchor.BOTTOM_RIGHT);
		Set<Anchor> allowedAnchors = parseEnumSet(
			reader.optionalArray("allowed_anchors"),
			pointer + "/allowed_anchors",
			Anchor.class,
			Set.of(Anchor.BOTTOM_RIGHT),
			issues
		);
		JsonObject offsetObject = reader.optionalObject("offset");
		JsonObject scaleObject = reader.optionalObject("scale");
		JsonObject opacityObject = reader.optionalObject("opacity");
		boolean componentManagement = reader.optionalBoolean("allow_component_management", false);
		TabCollision tabCollision = reader.optionalEnum("tab_collision", TabCollision.class, TabCollision.DIM);
		reader.finish("HUD client policy");
		if (allowedAnchors.isEmpty()) {
			issues.add(pointer + "/allowed_anchors", "OUT_OF_RANGE", "allowed_anchors must not be empty");
			allowedAnchors = Set.of(Anchor.BOTTOM_RIGHT);
		}
		if (!allowedAnchors.contains(defaultAnchor)) {
			issues.add(pointer + "/default_anchor", "INVALID_CONSTRAINT", "default_anchor must be allowed");
		}
		OffsetPolicy offset = parseOffset(offsetObject, pointer + "/offset", issues);
		RangePolicy scale = parseRange(scaleObject, pointer + "/scale", 1.0D, issues);
		RangePolicy opacity = parseRange(opacityObject, pointer + "/opacity", 1.0D, issues);
		if (scale.minimum() < 0.5D || scale.maximum() > 2.0D) {
			issues.add(pointer + "/scale", "OUT_OF_RANGE", "scale range must stay within 0.5..2.0");
		}
		if (opacity.minimum() < 0.0D || opacity.maximum() > 1.0D) {
			issues.add(pointer + "/opacity", "OUT_OF_RANGE", "opacity range must stay within 0..1");
		}
		if (!allowHide && opacity.minimum() <= 0.0D) {
			issues.add(pointer + "/opacity/minimum", "INVALID_CONSTRAINT", "non-hideable HUD requires positive opacity");
		}
		return new HudClientPolicy(
			allowHide,
			defaultAnchor,
			allowedAnchors,
			offset,
			scale,
			opacity,
			componentManagement,
			tabCollision
		);
	}

	private static OffsetPolicy parseOffset(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return new OffsetPolicy(0, 0);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		int maxX = reader.optionalInteger("max_x", 0);
		int maxY = reader.optionalInteger("max_y", 0);
		reader.finish("HUD offset policy");
		if (maxX < 0 || maxX > 640 || maxY < 0 || maxY > 360) {
			issues.add(pointer, "OUT_OF_RANGE", "HUD offsets must stay within the 640x360 reference canvas");
			maxX = Math.max(0, Math.min(maxX, 640));
			maxY = Math.max(0, Math.min(maxY, 360));
		}
		return new OffsetPolicy(maxX, maxY);
	}

	private static RangePolicy parseRange(
		final JsonObject object,
		final String pointer,
		final double fallback,
		final IssueCollector issues
	) {
		if (object == null) {
			return new RangePolicy(fallback, fallback, fallback);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		double minimum = reader.requiredDouble("minimum", fallback);
		double defaultValue = reader.requiredDouble("default", fallback);
		double maximum = reader.requiredDouble("maximum", fallback);
		reader.finish("HUD range policy");
		if (minimum > defaultValue || defaultValue > maximum) {
			issues.add(pointer, "OUT_OF_RANGE", "range must satisfy minimum <= default <= maximum");
			minimum = fallback;
			defaultValue = fallback;
			maximum = fallback;
		}
		return new RangePolicy(minimum, defaultValue, maximum);
	}

	private static DisconnectPolicy parseDisconnect(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return new DisconnectPolicy(DisconnectAction.PAUSE, DisconnectAction.CONTINUE);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		DisconnectAction requiredPlayer = reader.optionalEnum(
			"required_player",
			DisconnectAction.class,
			DisconnectAction.PAUSE
		);
		DisconnectAction host = reader.optionalEnum(
			"host",
			DisconnectAction.class,
			DisconnectAction.CONTINUE
		);
		reader.finish("countdown disconnect policy");
		return new DisconnectPolicy(requiredPlayer, host);
	}

	private static CountdownRestrictions parseRestrictions(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return CountdownRestrictions.safeDefault();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		FreezeMode movement = reader.optionalEnum("movement", FreezeMode.class, FreezeMode.FREEZE);
		BlockMode attack = reader.optionalEnum("attack", BlockMode.class, BlockMode.BLOCK);
		BlockMode interact = reader.optionalEnum("interact", BlockMode.class, BlockMode.BLOCK);
		JsonObject itemsObject = reader.optionalObject("items");
		DamageMode damage = reader.optionalEnum("damage", DamageMode.class, DamageMode.IMMUNE);
		AllowOnly camera = reader.optionalEnum("camera", AllowOnly.class, AllowOnly.ALLOW);
		AllowOnly chat = reader.optionalEnum("chat", AllowOnly.class, AllowOnly.ALLOW);
		AllowOnly escape = reader.optionalEnum("escape", AllowOnly.class, AllowOnly.ALLOW);
		reader.finish("countdown restrictions");
		return new CountdownRestrictions(
			movement,
			attack,
			interact,
			parseItemRestrictions(itemsObject, pointer + "/items", issues),
			damage,
			camera,
			chat,
			escape
		);
	}

	private static ItemRestrictions parseItemRestrictions(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return new ItemRestrictions(BlockMode.BLOCK, BlockMode.BLOCK, BlockMode.BLOCK, BlockMode.BLOCK);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		BlockMode use = reader.optionalEnum("use", BlockMode.class, BlockMode.BLOCK);
		BlockMode drop = reader.optionalEnum("drop", BlockMode.class, BlockMode.BLOCK);
		BlockMode swap = reader.optionalEnum("swap", BlockMode.class, BlockMode.BLOCK);
		BlockMode inventory = reader.optionalEnum("inventory", BlockMode.class, BlockMode.BLOCK);
		reader.finish("countdown item restrictions");
		return new ItemRestrictions(use, drop, swap, inventory);
	}

	private static List<CountdownCheckpoint> parseCheckpoints(
		final JsonArray array,
		final String pointer,
		final long duration,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > HudDefinitions.MAX_CHECKPOINTS) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"checkpoints exceed " + HudDefinitions.MAX_CHECKPOINTS + " entries"
			);
		}
		List<CountdownCheckpoint> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		Set<Long> remainingValues = new HashSet<>();
		int limit = Math.min(array.size(), HudDefinitions.MAX_CHECKPOINTS);
		for (int index = 0; index < limit; index++) {
			JsonElement element = array.get(index);
			String checkpointPointer = pointer + "/" + index;
			if (!element.isJsonObject()) {
				issues.add(checkpointPointer, "TYPE_MISMATCH", "checkpoint must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(element.getAsJsonObject(), checkpointPointer, issues);
			String id = reader.requiredString("id");
			JsonElement remainingElement = reader.requiredElement("remaining");
			JsonObject soundObject = reader.optionalObject("sound");
			Identifier cue = reader.optionalIdentifier("cue");
			reader.finish("countdown checkpoint");
			validateLocalId(id, checkpointPointer + "/id", issues);
			if (id != null && !ids.add(id)) {
				issues.add(checkpointPointer + "/id", "DUPLICATE_VALUE", "checkpoint id is duplicated");
			}
			long remaining = parseDuration(
				remainingElement,
				checkpointPointer + "/remaining",
				0L,
				true,
				issues
			);
			if (remaining > duration) {
				issues.add(
					checkpointPointer + "/remaining",
					"OUT_OF_RANGE",
					"checkpoint remaining cannot exceed countdown duration"
				);
			}
			if (!remainingValues.add(remaining)) {
				issues.add(
					checkpointPointer + "/remaining",
					"DUPLICATE_VALUE",
					"checkpoint remaining value is duplicated"
				);
			}
			CountdownSound sound = parseCountdownSound(
				soundObject,
				checkpointPointer + "/sound",
				issues
			);
			if (id != null) {
				result.add(
					new CountdownCheckpoint(
						id,
						remaining,
						Optional.ofNullable(sound),
						Optional.ofNullable(cue)
					)
				);
			}
		}
		result.sort(java.util.Comparator.comparingLong(CountdownCheckpoint::remainingTicks).reversed());
		return List.copyOf(result);
	}

	private static CountdownSound parseCountdownSound(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Identifier event = reader.requiredIdentifier("event");
		double volume = reader.optionalDouble("volume", 1.0D);
		double pitch = reader.optionalDouble("pitch", 1.0D);
		reader.finish("countdown sound");
		if (volume < 0.0D || volume > 4.0D) {
			issues.add(pointer + "/volume", "OUT_OF_RANGE", "sound volume must be between 0 and 4");
			volume = Math.max(0.0D, Math.min(volume, 4.0D));
		}
		if (pitch < 0.5D || pitch > 2.0D) {
			issues.add(pointer + "/pitch", "OUT_OF_RANGE", "sound pitch must be between 0.5 and 2");
			pitch = Math.max(0.5D, Math.min(pitch, 2.0D));
		}
		return event == null ? null : new CountdownSound(event, volume, pitch);
	}

	private static Map<CountdownCallbackSlot, List<CountdownCallback>> parseCallbacks(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		Map<CountdownCallbackSlot, List<CountdownCallback>> result = new EnumMap<>(CountdownCallbackSlot.class);
		for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
			result.put(slot, List.of());
		}
		if (object == null) {
			return Map.copyOf(result);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
			String key = serialized(slot);
			JsonArray callbacks = reader.optionalArray(key);
			result.put(slot, parseCallbackList(callbacks, pointer + "/" + key, issues));
		}
		reader.finish("countdown callbacks");
		return Map.copyOf(result);
	}

	private static List<CountdownCallback> parseCallbackList(
		final JsonArray array,
		final String pointer,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > HudDefinitions.MAX_CALLBACKS_PER_SLOT) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"callbacks exceed " + HudDefinitions.MAX_CALLBACKS_PER_SLOT + " entries"
			);
		}
		List<CountdownCallback> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		int limit = Math.min(array.size(), HudDefinitions.MAX_CALLBACKS_PER_SLOT);
		for (int index = 0; index < limit; index++) {
			JsonElement element = array.get(index);
			String callbackPointer = pointer + "/" + index;
			if (!element.isJsonObject()) {
				issues.add(callbackPointer, "TYPE_MISMATCH", "callback must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(element.getAsJsonObject(), callbackPointer, issues);
			String id = reader.requiredString("id");
			CountdownCallbackScope scope = reader.requiredEnum("scope", CountdownCallbackScope.class);
			Identifier function = reader.requiredIdentifier("function");
			boolean required = reader.optionalBoolean("required", true);
			reader.finish("countdown callback");
			validateLocalId(id, callbackPointer + "/id", issues);
			if (id != null && !ids.add(id)) {
				issues.add(callbackPointer + "/id", "DUPLICATE_VALUE", "callback id is duplicated within slot");
			}
			if (id != null && scope != null && function != null) {
				result.add(new CountdownCallback(id, scope, function, required));
			}
		}
		return List.copyOf(result);
	}

	private static void validateBodyBindings(
		final ComponentType type,
		final ComponentBody body,
		final Map<String, HudBinding> bindings,
		final IssueCollector issues
	) {
		if (body instanceof TextBody text) {
			for (String reference : text.text().referencedBindings()) {
				if (!bindings.containsKey(reference)) {
					issues.add("/text", "MISSING_REFERENCE", "text references unknown binding " + reference);
				}
			}
		}
		if (body instanceof PlayerHeadBody head) {
			requireBindingType(head.player(), Set.of(HudValueType.PLAYER), "/player/field", bindings, issues);
		}
		if (body instanceof CounterBody counter) {
			requireBindingType(
				counter.value(),
				Set.of(
					HudValueType.STRING,
					HudValueType.INTEGER,
					HudValueType.DECIMAL,
					HudValueType.COMPONENT,
					HudValueType.DURATION
				),
				"/value/field",
				bindings,
				issues
			);
		}
		if (body instanceof BadgeBody badge) {
			requireBindingType(badge.value(), Set.of(HudValueType.IDENTIFIER), "/value/field", bindings, issues);
		}
		if (body instanceof ProgressBody progress) {
			Set<HudValueType> numbers = Set.of(HudValueType.INTEGER, HudValueType.DECIMAL);
			requireBindingType(progress.current(), numbers, "/current/field", bindings, issues);
			requireBindingType(progress.maximum(), numbers, "/maximum/field", bindings, issues);
		}
		if (body instanceof TimerBody timer) {
			requireBindingType(timer.clock(), Set.of(HudValueType.CLOCK), "/clock/field", bindings, issues);
		}
		if (body instanceof RepeatBody repeat) {
			requireBindingType(repeat.items(), Set.of(HudValueType.LIST), "/items/field", bindings, issues);
		}
	}

	private static void requireBindingType(
		final FieldReference reference,
		final Set<HudValueType> accepted,
		final String pointer,
		final Map<String, HudBinding> bindings,
		final IssueCollector issues
	) {
		HudBinding binding = bindings.get(reference.field());
		if (binding == null) {
			issues.add(pointer, "MISSING_REFERENCE", "unknown binding " + reference.field());
			return;
		}
		if (!accepted.contains(binding.valueType())) {
			issues.add(pointer, "TYPE_MISMATCH", "binding type " + serialized(binding.valueType()) + " is not supported here");
		}
	}

	private static boolean conditionUsesTask(final HudCondition condition) {
		return condition.hasTaskConstraint()
			|| condition.all().stream().anyMatch(HudDefinitionParser::conditionUsesTask)
			|| condition.any().stream().anyMatch(HudDefinitionParser::conditionUsesTask)
			|| condition.not().map(HudDefinitionParser::conditionUsesTask).orElse(false);
	}

	private static RichText parseRichText(
		final JsonElement value,
		final String pointer,
		final IssueCollector issues
	) {
		if (value == null || value.isJsonNull()) {
			return null;
		}
		String canonical = canonical(value);
		if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_COMPONENT_BYTES) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"text component exceeds " + MAX_TEXT_COMPONENT_BYTES + " UTF-8 bytes"
			);
			return null;
		}
		if (containsDynamicComponent(value)) {
			issues.add(pointer, "HUD_BINDING_UNAUTHORIZED", "score, selector and nbt text components are not allowed");
			return null;
		}
		DataResult<Component> decoded;
		try {
			decoded = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value);
		} catch (RuntimeException error) {
			issues.add(pointer, "INVALID_TEXT", safeMessage(error));
			return null;
		}
		Component component = decoded.result().orElse(null);
		if (component == null) {
			issues.add(
				pointer,
				"INVALID_TEXT",
				decoded.error().map(valueError -> valueError.message()).orElse("invalid text component")
			);
			return null;
		}
		String plain = component.getString();
		if (plain.length() > MAX_TEXT_PLAIN_CHARACTERS) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"plain text exceeds " + MAX_TEXT_PLAIN_CHARACTERS + " characters"
			);
			return null;
		}
		return new RichText(canonical, plain);
	}

	private static boolean containsDynamicComponent(final JsonElement value) {
		if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
			return false;
		}
		if (value.isJsonArray()) {
			for (JsonElement child : value.getAsJsonArray()) {
				if (containsDynamicComponent(child)) {
					return true;
				}
			}
			return false;
		}
		for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
			if (DYNAMIC_COMPONENT_KEYS.contains(entry.getKey()) || containsDynamicComponent(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private static FieldReference parseFieldReference(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String field = reader.requiredString("field");
		reader.finish("HUD field reference");
		return validateLocalId(field, pointer + "/field", issues) ? new FieldReference(field) : null;
	}

	private static IntSize parseIntSize(
		final JsonObject object,
		final String pointer,
		final int maximum,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		int width = reader.requiredInteger("width", 1);
		int height = reader.requiredInteger("height", 1);
		reader.finish("integer size");
		if (width < 1 || width > maximum || height < 1 || height > maximum) {
			issues.add(pointer, "OUT_OF_RANGE", "size must stay between 1 and " + maximum);
		}
		return new IntSize(width, height);
	}

	private static Border parseBorder(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String color = reader.requiredString("color");
		int width = reader.optionalInteger("width", 1);
		reader.finish("HUD border");
		validateColor(color, pointer + "/color", issues);
		if (width < 0 || width > 32) {
			issues.add(pointer + "/width", "OUT_OF_RANGE", "border width must be between 0 and 32");
			width = Math.max(0, Math.min(width, 32));
		}
		return color == null ? null : new Border(color, width);
	}

	private static Insets parseInsets(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return Insets.zero();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		int left = reader.optionalInteger("left", 0);
		int top = reader.optionalInteger("top", 0);
		int right = reader.optionalInteger("right", 0);
		int bottom = reader.optionalInteger("bottom", 0);
		reader.finish("HUD insets");
		if (
			left < 0 || left > MAX_PADDING || top < 0 || top > MAX_PADDING
				|| right < 0 || right > MAX_PADDING || bottom < 0 || bottom > MAX_PADDING
		) {
			issues.add(pointer, "OUT_OF_RANGE", "insets must stay between 0 and " + MAX_PADDING);
			left = Math.max(0, Math.min(left, MAX_PADDING));
			top = Math.max(0, Math.min(top, MAX_PADDING));
			right = Math.max(0, Math.min(right, MAX_PADDING));
			bottom = Math.max(0, Math.min(bottom, MAX_PADDING));
		}
		return new Insets(left, top, right, bottom);
	}

	private static List<Identifier> parseIdentifierList(
		final JsonArray array,
		final String pointer,
		final int maximum,
		final boolean nonEmpty,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if ((nonEmpty && array.isEmpty()) || array.size() > maximum) {
			issues.add(pointer, "RESOURCE_LIMIT", "identifier list must contain " + (nonEmpty ? "1.." : "0..") + maximum + " entries");
		}
		List<Identifier> result = new ArrayList<>();
		Set<Identifier> seen = new HashSet<>();
		int limit = Math.min(array.size(), maximum);
		for (int index = 0; index < limit; index++) {
			JsonElement element = array.get(index);
			String entryPointer = pointer + "/" + index;
			if (!isString(element)) {
				issues.add(entryPointer, "TYPE_MISMATCH", "identifier must be a string");
				continue;
			}
			Identifier id = parseIdentifier(element.getAsString());
			if (id == null) {
				issues.add(entryPointer, "INVALID_IDENTIFIER", "identifier must include an explicit namespace");
				continue;
			}
			if (!seen.add(id)) {
				issues.add(entryPointer, "DUPLICATE_VALUE", "identifier is duplicated");
				continue;
			}
			result.add(id);
		}
		return List.copyOf(result);
	}

	private static List<Double> parseWeights(
		final JsonArray array,
		final String pointer,
		final int childCount,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() != childCount) {
			issues.add(pointer, "OUT_OF_RANGE", "weights count must equal children count");
		}
		List<Double> result = new ArrayList<>();
		int limit = Math.min(array.size(), HudDefinitions.MAX_CONTAINER_CHILDREN);
		for (int index = 0; index < limit; index++) {
			Double value = finiteNumber(array.get(index));
			if (value == null || value < 0.0D || value > 1_000.0D) {
				issues.add(pointer + "/" + index, "OUT_OF_RANGE", "weight must be a finite value between 0 and 1000");
				continue;
			}
			result.add(value);
		}
		return List.copyOf(result);
	}

	private static <E extends Enum<E>> Set<E> parseEnumSet(
		final JsonArray array,
		final String pointer,
		final Class<E> type,
		final Set<E> fallback,
		final IssueCollector issues
	) {
		if (array == null) {
			return Set.copyOf(fallback);
		}
		if (array.size() > MAX_IDENTIFIER_SET) {
			issues.add(pointer, "RESOURCE_LIMIT", "enum list exceeds " + MAX_IDENTIFIER_SET + " entries");
		}
		Set<E> result = new LinkedHashSet<>();
		int limit = Math.min(array.size(), MAX_IDENTIFIER_SET);
		for (int index = 0; index < limit; index++) {
			JsonElement element = array.get(index);
			String entryPointer = pointer + "/" + index;
			if (!isString(element)) {
				issues.add(entryPointer, "TYPE_MISMATCH", "enum value must be a string");
				continue;
			}
			E parsed = parseEnum(element.getAsString(), type, entryPointer, issues);
			if (parsed != null && !result.add(parsed)) {
				issues.add(entryPointer, "DUPLICATE_VALUE", "enum value is duplicated");
			}
		}
		return Set.copyOf(result);
	}

	private static long parseDuration(
		final JsonElement value,
		final String pointer,
		final long fallback,
		final boolean allowZero,
		final IssueCollector issues
	) {
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		Long parsed = parseDurationQuiet(value);
		if (parsed == null) {
			issues.add(pointer, "TYPE_MISMATCH", "duration must be integer ticks or a t/s/m duration string");
			return fallback;
		}
		if (parsed < (allowZero ? 0L : 1L) || parsed > HudDefinitions.MAX_COUNTDOWN_TICKS) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"duration must be between " + (allowZero ? 0 : 1) + " and " + HudDefinitions.MAX_COUNTDOWN_TICKS + " ticks"
			);
			return fallback;
		}
		return parsed;
	}

	private static Long parseDurationQuiet(final JsonElement value) {
		Long integer = integralLong(value);
		if (integer != null) {
			return integer;
		}
		if (!isString(value)) {
			return null;
		}
		Matcher matcher = DURATION.matcher(value.getAsString());
		if (!matcher.matches()) {
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(matcher.group(1) + (matcher.group(2) == null ? "" : "." + matcher.group(2)));
			BigDecimal multiplier = switch (matcher.group(3)) {
				case "t" -> BigDecimal.ONE;
				case "s" -> BigDecimal.valueOf(20L);
				case "m" -> BigDecimal.valueOf(1_200L);
				default -> throw new IllegalStateException("unreachable duration unit");
			};
			return amount.multiply(multiplier).longValueExact();
		} catch (ArithmeticException | NumberFormatException error) {
			return null;
		}
	}

	private static Integer boundedNullableSize(
		final Integer value,
		final String pointer,
		final IssueCollector issues
	) {
		if (value == null) {
			return null;
		}
		if (value < 1 || value > MAX_LOGICAL_SIZE) {
			issues.add(pointer, "OUT_OF_RANGE", "size must be between 1 and " + MAX_LOGICAL_SIZE);
			return Math.max(1, Math.min(value, MAX_LOGICAL_SIZE));
		}
		return value;
	}

	private static void validateColor(
		final String value,
		final String pointer,
		final IssueCollector issues
	) {
		if (value != null && !COLOR.matcher(value).matches()) {
			issues.add(pointer, "INVALID_COLOR", "color must be #RRGGBB or #AARRGGBB");
		}
	}

	private static boolean validateLocalId(
		final String value,
		final String pointer,
		final IssueCollector issues
	) {
		if (value == null || !LOCAL_ID.matcher(value).matches()) {
			issues.add(pointer, "INVALID_LOCAL_ID", "local id must match [a-z][a-z0-9_.-]{0,63}");
			return false;
		}
		return true;
	}

	private static <E extends Enum<E>> E parseEnum(
		final String value,
		final Class<E> type,
		final String pointer,
		final IssueCollector issues
	) {
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException error) {
			issues.add(pointer, "INVALID_ENUM", "unsupported " + type.getSimpleName() + " value " + value);
			return null;
		}
	}

	private static String serialized(final Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static void putIfNonNull(final Map<String, Object> values, final String key, final Object value) {
		if (value != null) {
			values.put(key, value);
		}
	}

	private static void checkFormat(final ObjectReader reader, final IssueCollector issues) {
		Integer format = reader.requiredInteger("format_version");
		if (format != null && format.intValue() != HudDefinitions.CURRENT_FORMAT_VERSION) {
			issues.add(
				reader.pointer("format_version"),
				"UNSUPPORTED_FORMAT",
				"expected " + HudDefinitions.CURRENT_FORMAT_VERSION + " but found " + format
			);
		}
	}

	private static ParsedRoot parseRoot(
		final Identifier id,
		final String json,
		final IssueCollector issues
	) {
		if (id == null) {
			issues.add("", "INVALID_IDENTIFIER", "definition id is required");
		}
		if (json == null) {
			issues.add("", "JSON_SYNTAX", "definition source is required");
			return null;
		}
		if (json.length() > MAX_SOURCE_CHARACTERS) {
			issues.add(
				"",
				"RESOURCE_LIMIT",
				"definition source exceeds " + MAX_SOURCE_CHARACTERS + " characters"
			);
			return null;
		}
		try {
			JsonReader reader = new JsonReader(new StringReader(json));
			reader.setStrictness(Strictness.STRICT);
			JsonElement value = readStrictValue(reader, 0);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("trailing content after JSON value");
			}
			if (!value.isJsonObject()) {
				issues.add("", "TYPE_MISMATCH", "definition root must be an object");
				return null;
			}
			String canonical = canonical(value);
			if (canonical.getBytes(StandardCharsets.UTF_8).length > HudDefinitions.MAX_CANONICAL_DOCUMENT_BYTES) {
				issues.add(
					"",
					"RESOURCE_LIMIT",
					"canonical HUD document exceeds " + HudDefinitions.MAX_CANONICAL_DOCUMENT_BYTES + " UTF-8 bytes"
				);
			}
			return id == null ? null : new ParsedRoot(value.getAsJsonObject(), canonical, sha256(canonical));
		} catch (IOException | RuntimeException error) {
			issues.add("", "JSON_SYNTAX", safeMessage(error));
			return null;
		}
	}

	private static JsonElement readStrictValue(
		final JsonReader reader,
		final int depth
	) throws IOException {
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
					object.add(name, readStrictValue(reader, depth + 1));
				}
				reader.endObject();
				yield object;
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				JsonArray array = new JsonArray();
				while (reader.hasNext()) {
					array.add(readStrictValue(reader, depth + 1));
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

	static String canonical(final JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return "null";
		}
		if (value.isJsonPrimitive()) {
			return value.toString();
		}
		if (value.isJsonArray()) {
			StringBuilder result = new StringBuilder("[");
			JsonArray array = value.getAsJsonArray();
			for (int index = 0; index < array.size(); index++) {
				if (index > 0) {
					result.append(',');
				}
				result.append(canonical(array.get(index)));
			}
			return result.append(']').toString();
		}
		StringBuilder result = new StringBuilder("{");
		List<Map.Entry<String, JsonElement>> entries = value.getAsJsonObject()
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.toList();
		for (int index = 0; index < entries.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			Map.Entry<String, JsonElement> entry = entries.get(index);
			result.append(new JsonPrimitive(entry.getKey())).append(':').append(canonical(entry.getValue()));
		}
		return result.append('}').toString();
	}

	static String sha256(final String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	static Identifier parseIdentifier(final String value) {
		if (value == null || !value.contains(":")) {
			return null;
		}
		try {
			return Identifier.parse(value);
		} catch (RuntimeException error) {
			return null;
		}
	}

	private static boolean isString(final JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
	}

	private static boolean isBoolean(final JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
	}

	private static Long integralLong(final JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			return value.getAsBigDecimal().longValueExact();
		} catch (ArithmeticException | NumberFormatException error) {
			return null;
		}
	}

	private static Double finiteNumber(final JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			double parsed = value.getAsDouble();
			return Double.isFinite(parsed) ? parsed : null;
		} catch (NumberFormatException error) {
			return null;
		}
	}

	private static String escapePointer(final String value) {
		return value.replace("~", "~0").replace("/", "~1");
	}

	private static String safeMessage(final Throwable error) {
		String message = error == null ? null : error.getMessage();
		if (message == null || message.isBlank()) {
			return error == null ? "unknown parser failure" : error.getClass().getSimpleName();
		}
		return message.length() <= 512 ? message : message.substring(0, 512);
	}

	private static <K, V> Map<K, V> immutableMap(final Map<K, V> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	private record ParsedRoot(JsonObject root, String canonical, String sha256) {
	}

	private static final class ConditionBudget {
		private int terms;
		private boolean reported;

		private void add(final int count, final String pointer, final IssueCollector issues) {
			this.terms += Math.max(0, count);
			if (!this.reported && this.terms > HudDefinitions.MAX_CONDITION_TERMS) {
				this.reported = true;
				issues.add(
					pointer,
					"RESOURCE_LIMIT",
					"condition terms exceed " + HudDefinitions.MAX_CONDITION_TERMS
				);
			}
		}
	}

	public record Issue(String path, String code, String message) {
		public Issue {
			path = path == null ? "" : truncate(path, 512);
			code = code == null || code.isBlank() ? "UNKNOWN" : truncate(code, 64);
			message = message == null || message.isBlank()
				? "unspecified HUD definition problem"
				: truncate(message, 512);
		}
	}

	public record ParseResult<T>(
		Optional<T> value,
		List<Issue> issues,
		int totalIssueCount
	) {
		public ParseResult {
			value = value == null ? Optional.empty() : value;
			issues = List.copyOf(issues);
			if (totalIssueCount < issues.size()) {
				throw new IllegalArgumentException("total issue count cannot be smaller than retained details");
			}
		}

		public boolean valid() {
			return this.value.isPresent() && this.totalIssueCount == 0;
		}

		public boolean issuesTruncated() {
			return this.totalIssueCount > this.issues.size();
		}
	}

	private static final class IssueCollector {
		private final List<Issue> issues = new ArrayList<>();
		private int totalCount;

		private void add(final String path, final String code, final String message) {
			this.totalCount++;
			if (this.issues.size() < MAX_ISSUE_DETAILS) {
				this.issues.add(new Issue(path, code, message));
			}
		}

		private <T> ParseResult<T> result(final Optional<T> value) {
			return new ParseResult<>(value, this.issues, this.totalCount);
		}
	}

	private static final class ObjectReader {
		private final JsonObject object;
		private final String basePointer;
		private final IssueCollector issues;
		private final Set<String> consumed = new LinkedHashSet<>();

		private ObjectReader(
			final JsonObject object,
			final String basePointer,
			final IssueCollector issues
		) {
			this.object = Objects.requireNonNull(object, "object");
			this.basePointer = basePointer == null ? "" : basePointer;
			this.issues = Objects.requireNonNull(issues, "issues");
		}

		private String pointer(final String name) {
			return this.basePointer + "/" + escapePointer(name);
		}

		private JsonElement requiredElement(final String name) {
			this.consumed.add(name);
			JsonElement value = this.object.get(name);
			if (value == null || value.isJsonNull()) {
				this.issues.add(this.pointer(name), "MISSING_REQUIRED", "required value is missing");
				return null;
			}
			return value;
		}

		private JsonElement optionalElement(final String name) {
			this.consumed.add(name);
			JsonElement value = this.object.get(name);
			return value == null || value.isJsonNull() ? null : value;
		}

		private String requiredString(final String name) {
			JsonElement value = this.requiredElement(name);
			return value == null ? null : this.readString(name, value);
		}

		private String optionalString(final String name) {
			JsonElement value = this.optionalElement(name);
			return value == null ? null : this.readString(name, value);
		}

		private String optionalString(final String name, final String fallback) {
			String value = this.optionalString(name);
			return value == null ? fallback : value;
		}

		private String readString(final String name, final JsonElement value) {
			if (!isString(value)) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be a string");
				return null;
			}
			String result = value.getAsString();
			if (result.length() > 1_024) {
				this.issues.add(this.pointer(name), "OUT_OF_RANGE", "string exceeds 1024 characters");
				return null;
			}
			return result;
		}

		private Integer requiredInteger(final String name) {
			JsonElement value = this.requiredElement(name);
			return value == null ? null : this.readInteger(name, value);
		}

		private int requiredInteger(final String name, final int fallback) {
			Integer value = this.requiredInteger(name);
			return value == null ? fallback : value;
		}

		private Integer optionalInteger(final String name) {
			JsonElement value = this.optionalElement(name);
			return value == null ? null : this.readInteger(name, value);
		}

		private int optionalInteger(final String name, final int fallback) {
			Integer value = this.optionalInteger(name);
			return value == null ? fallback : value;
		}

		private Integer readInteger(final String name, final JsonElement value) {
			Long parsed = integralLong(value);
			if (parsed == null || parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be a 32-bit integer");
				return null;
			}
			return parsed.intValue();
		}

		private boolean optionalBoolean(final String name, final boolean fallback) {
			Boolean value = this.optionalBoolean(name);
			return value == null ? fallback : value;
		}

		private Boolean optionalBoolean(final String name) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return null;
			}
			if (!isBoolean(value)) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be a boolean");
				return null;
			}
			return value.getAsBoolean();
		}

		private double requiredDouble(final String name, final double fallback) {
			JsonElement value = this.requiredElement(name);
			if (value == null) {
				return fallback;
			}
			Double parsed = finiteNumber(value);
			if (parsed == null) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be a finite number");
				return fallback;
			}
			return parsed;
		}

		private double optionalDouble(final String name, final double fallback) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return fallback;
			}
			Double parsed = finiteNumber(value);
			if (parsed == null) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be a finite number");
				return fallback;
			}
			return parsed;
		}

		private JsonObject requiredObject(final String name) {
			JsonElement value = this.requiredElement(name);
			return value == null ? null : this.readObject(name, value);
		}

		private JsonObject optionalObject(final String name) {
			JsonElement value = this.optionalElement(name);
			return value == null ? null : this.readObject(name, value);
		}

		private JsonObject readObject(final String name, final JsonElement value) {
			if (!value.isJsonObject()) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be an object");
				return null;
			}
			return value.getAsJsonObject();
		}

		private JsonArray requiredArray(final String name) {
			JsonElement value = this.requiredElement(name);
			return value == null ? null : this.readArray(name, value);
		}

		private JsonArray optionalArray(final String name) {
			JsonElement value = this.optionalElement(name);
			return value == null ? null : this.readArray(name, value);
		}

		private JsonArray readArray(final String name, final JsonElement value) {
			if (!value.isJsonArray()) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be an array");
				return null;
			}
			return value.getAsJsonArray();
		}

		private Identifier requiredIdentifier(final String name) {
			String value = this.requiredString(name);
			return value == null ? null : this.readIdentifier(name, value);
		}

		private Identifier optionalIdentifier(final String name) {
			String value = this.optionalString(name);
			return value == null ? null : this.readIdentifier(name, value);
		}

		private Identifier readIdentifier(final String name, final String value) {
			Identifier parsed = parseIdentifier(value);
			if (parsed == null) {
				this.issues.add(
					this.pointer(name),
					"INVALID_IDENTIFIER",
					"identifier must include an explicit namespace"
				);
			}
			return parsed;
		}

		private <E extends Enum<E>> E requiredEnum(final String name, final Class<E> type) {
			String value = this.requiredString(name);
			return parseEnum(value, type, this.pointer(name), this.issues);
		}

		private <E extends Enum<E>> E optionalEnum(
			final String name,
			final Class<E> type,
			final E fallback
		) {
			String value = this.optionalString(name);
			if (value == null) {
				return fallback;
			}
			E parsed = parseEnum(value, type, this.pointer(name), this.issues);
			return parsed == null ? fallback : parsed;
		}

		private Set<Identifier> optionalIdentifierSet(final String name, final int maximum) {
			JsonArray array = this.optionalArray(name);
			if (array == null) {
				return Set.of();
			}
			if (array.size() > maximum) {
				this.issues.add(this.pointer(name), "RESOURCE_LIMIT", "identifier set exceeds " + maximum);
			}
			Set<Identifier> result = new LinkedHashSet<>();
			int limit = Math.min(array.size(), maximum);
			for (int index = 0; index < limit; index++) {
				JsonElement element = array.get(index);
				String entryPointer = this.pointer(name) + "/" + index;
				if (!isString(element)) {
					this.issues.add(entryPointer, "TYPE_MISMATCH", "identifier must be a string");
					continue;
				}
				Identifier parsed = parseIdentifier(element.getAsString());
				if (parsed == null) {
					this.issues.add(entryPointer, "INVALID_IDENTIFIER", "identifier must include an explicit namespace");
					continue;
				}
				if (!result.add(parsed)) {
					this.issues.add(entryPointer, "DUPLICATE_VALUE", "identifier is duplicated");
				}
			}
			return Set.copyOf(result);
		}

		private void finish(final String subject) {
			for (String name : this.object.keySet()) {
				if (!this.consumed.contains(name)) {
					this.issues.add(
						this.pointer(name),
						"UNKNOWN_KEY",
						"unknown " + subject + " key " + name
					);
				}
			}
		}
	}

	private static String truncate(final String value, final int maximum) {
		return value.length() <= maximum ? value : value.substring(0, maximum);
	}
}
