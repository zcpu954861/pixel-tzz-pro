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
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AlignSelf;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Anchor;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BoundText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Breakpoints;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ComparisonCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConcatenatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.CrossAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Direction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.DividerContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.DurationTicksText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Easing;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.EventBinding;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ExistsCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldPresentation;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FontToken;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ImageContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ImageFit;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Insets;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LiteralValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MainAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionKeyframe;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionProperty;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionTrack;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NotCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PlayerHeadContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ProgressContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ReducedMotion;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveOverride;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeMode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeSpec;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SoundCue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SpacerContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StaticText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextOverflow;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextTemplate;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeTokens;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TranslatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiAction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiEvent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Shared strict syntax compiler for data-pack pages and themes.
 *
 * <p>It deliberately does not resolve game, field, action, or cross-document references. The
 * authoritative {@link DefinitionCompiler} performs those checks against the complete candidate
 * generation after this parser has produced a typed immutable tree.
 */
public final class UiDefinitionParser {
	public static final int MAX_PAGE_NODES = 512;
	public static final int MAX_PAGE_DEPTH = 32;
	public static final int MAX_DIRECT_CHILDREN = 128;
	public static final int MAX_NODE_ID_LENGTH = 64;
	public static final int MAX_EXPRESSION_NODES = 64;
	public static final int MAX_EXPRESSION_DEPTH = 16;
	public static final int MAX_THEME_STYLES = 128;
	public static final int MAX_THEME_MOTIONS = 128;
	public static final int MAX_MOTION_TRACKS = 8;
	public static final int MAX_MOTION_KEYFRAMES = 16;
	public static final int MAX_MOTION_DURATION_MILLIS = 2_000;
	public static final int MAX_PAGE_SOUND_REFERENCES = 64;
	public static final int MAX_REPEAT_ITEMS = 256;
	private static final int MAX_JSON_DEPTH = 64;
	private static final int MAX_SHORT_TEXT = 1_024;
	private static final int MAX_LAYOUT_VALUE = 8_192;
	private static final int MIN_BREAKPOINT = 160;
	private static final int MAX_BREAKPOINT = 8_192;
	private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Pattern COLOR_TOKEN_REFERENCE = Pattern.compile("@colors\\.[a-z0-9_.-]+");
	private static final Pattern FONT_TOKEN_REFERENCE = Pattern.compile("@fonts\\.[a-z0-9_.-]+");
	private static final Pattern SPACING_TOKEN_REFERENCE = Pattern.compile("@spacing\\.[a-z0-9_.-]+");
	private static final Pattern EXTERNAL_BINDING_PATH = Pattern.compile("[a-z0-9_./:-]+");
	private static final Set<String> FRAME_STYLES = Set.of(
		"plain",
		"rail",
		"dossier",
		"target",
		"broadcast",
		"warning",
		"portal",
		"manual"
	);
	private static final Set<String> ACCENT_EDGES = Set.of("none", "left", "top", "right", "bottom");
	private static final Set<String> STYLE_PROPERTIES = Set.of(
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
		"font",
		"font_size",
		"line_height",
		"opacity",
		"shadow",
		"navigation_arrows",
		"obfuscated",
		"frame_style",
		"accent_edge",
		"arrow_travel",
		"segment_count",
		"padding",
		"padding_horizontal",
		"padding_vertical"
	);
	private static final Set<String> SPACING_STYLE_PROPERTIES = Set.of(
		"border_width",
		"corner_radius",
		"font_size",
		"line_height",
		"arrow_travel",
		"segment_count",
		"padding",
		"padding_horizontal",
		"padding_vertical"
	);
	private static final Set<String> FIXED_BINDINGS = Set.of(
		"viewer.uuid",
		"viewer.name",
		"viewer.online",
		"viewer.admin",
		"viewer.host",
		"viewer.role",
		"viewer.role_name",
		"viewer.team",
		"viewer.team_name",
		"viewer.life_state",
		"viewer.life_state_name",
		"viewer.ready",
		"viewer.initialized",
		"session.game",
		"session.game_name",
		"session.phase",
		"session.phase_name",
		"session.revision",
		"session.generation",
		"session.connected",
		"session.players",
		"flow.id",
		"flow.version",
		"flow.node",
		"flow.completed",
		"flow.total",
		"flow.remaining",
		"flow.percent",
		"terminal.connected",
		"terminal.loading",
		"terminal.history_enabled",
		"terminal.page",
		"terminal.route",
		"terminal.status",
		"task.exists",
		"task.paused",
		"task.id",
		"task.name",
		"task.description",
		"task.status",
		"task.result",
		"task.elapsed_ticks",
		"task.remaining_ticks",
		"task.duration_ticks",
		"task.progress",
		"task.progress_min",
		"task.progress_max",
		"task.statistics",
		"history.enabled",
		"history.empty",
		"history.count",
		"history.source",
		"history.items",
		"detail.exists",
		"detail.status",
		"detail.id",
		"detail.title",
		"detail.summary",
		"detail.source",
		"detail.source_name",
		"detail.event_count",
		"detail.category",
		"detail.color",
		"detail.icon",
		"detail.game_time",
		"detail.task_time",
		"detail.game_time_ticks",
		"detail.task_time_ticks",
		"detail.game_time_text",
		"detail.task_time_text",
		"detail.task",
		"detail.task_name",
		"detail.actor.uuid",
		"detail.actor.name",
		"detail.targets",
		"detail.statistics",
		"detail.result",
		"detail.details",
		"detail.metadata",
		"detail.detail_available",
		"detail.replay_allowed",
		"item.id",
		"item.title",
		"item.summary",
		"item.source",
		"item.source_name",
		"item.event_count",
		"item.category",
		"item.color",
		"item.icon",
		"item.game_time",
		"item.task_time",
		"item.game_time_ticks",
		"item.task_time_ticks",
		"item.game_time_text",
		"item.task_time_text",
		"item.task",
		"item.task_name",
		"item.actor.uuid",
		"item.actor.name",
		"item.targets",
		"item.statistics",
		"item.result",
		"item.details",
		"item.detail_available",
		"item.replay_allowed",
		"item.label",
		"item.value",
		"item.uuid",
		"item.name",
		"item.online",
		"item.admin",
		"item.host",
		"item.role",
		"item.role_name",
		"item.team",
		"item.team_name",
		"item.life_state",
		"item.life_state_name",
		"item.ready",
		"item.initialized"
	);
	private static final Set<String> LOCAL_ACTIONS = Set.of(
		"back",
		"close",
		"tab",
		"expand",
		"collapse",
		"toggle",
		"preview"
	);

	private UiDefinitionParser() {
	}

	public static ParseResult<PageDefinition> parsePage(final Identifier id, final String json) {
		List<Issue> issues = new ArrayList<>();
		JsonElement root = parseStrict(json, issues);
		if (root == null || !root.isJsonObject()) {
			if (root != null) {
				issues.add(new Issue("TYPE_MISMATCH", "", "page root must be an object"));
			}
			return ParseResult.failed(issues);
		}

		PageParser parser = new PageParser(id, root.getAsJsonObject(), issues);
		PageDefinition page = parser.parse();
		return issues.isEmpty() && page != null
			? ParseResult.success(page)
			: ParseResult.failed(issues);
	}

	public static ParseResult<ThemeDefinition> parseTheme(final Identifier id, final String json) {
		List<Issue> issues = new ArrayList<>();
		JsonElement root = parseStrict(json, issues);
		if (root == null || !root.isJsonObject()) {
			if (root != null) {
				issues.add(new Issue("TYPE_MISMATCH", "", "theme root must be an object"));
			}
			return ParseResult.failed(issues);
		}

		ThemeParser parser = new ThemeParser(id, root.getAsJsonObject(), issues);
		ThemeDefinition theme = parser.parse();
		return issues.isEmpty() && theme != null
			? ParseResult.success(theme)
			: ParseResult.failed(issues);
	}

	/**
	 * Parses one bounded condition fragment for other trusted data-pack definition types.
	 *
	 * <p>This is intentionally the same AST and grammar used by page visibility. Callers retain
	 * ownership of reference validation and must not create a parallel condition evaluator.
	 */
	public static ParseResult<ConditionExpression> parseConditionFragment(
		final JsonElement value
	) {
		List<Issue> issues = new ArrayList<>();
		if (value == null || value.isJsonNull()) {
			issues.add(new Issue("TYPE_MISMATCH", "", "condition fragment is required"));
			return ParseResult.failed(issues);
		}
		ParserBase parser = new ParserBase(null, new JsonObject(), issues, true) {
		};
		ConditionExpression condition = parser.parseCondition(value, "", false);
		return issues.isEmpty() && condition != null
			? ParseResult.success(condition)
			: ParseResult.failed(issues);
	}

	public record Issue(String code, String pointer, String message) {
	}

	public record ParseResult<T>(Optional<T> value, List<Issue> issues) {
		public ParseResult {
			value = value == null ? Optional.empty() : value;
			issues = List.copyOf(issues);
		}

		public static <T> ParseResult<T> success(final T value) {
			return new ParseResult<>(Optional.of(value), List.of());
		}

		public static <T> ParseResult<T> failed(final List<Issue> issues) {
			return new ParseResult<>(Optional.empty(), issues);
		}

		public boolean valid() {
			return this.value.isPresent() && this.issues.isEmpty();
		}
	}

	private abstract static class ParserBase {
		final Identifier id;
		final JsonObject root;
		final List<Issue> issues;
		final List<AssetReference> assets = new ArrayList<>();
		final boolean externalConditionBindings;

		ParserBase(final Identifier id, final JsonObject root, final List<Issue> issues) {
			this(id, root, issues, false);
		}

		ParserBase(
			final Identifier id,
			final JsonObject root,
			final List<Issue> issues,
			final boolean externalConditionBindings
		) {
			this.id = id;
			this.root = root;
			this.issues = issues;
			this.externalConditionBindings = externalConditionBindings;
		}

		final void requireFormat(final Reader reader) {
			Integer format = reader.requiredInteger("format_version");
			if (format != null && format != UiDefinitions.CURRENT_FORMAT_VERSION) {
				reader.add(
					"UNSUPPORTED_FORMAT",
					"/format_version",
					"expected " + UiDefinitions.CURRENT_FORMAT_VERSION + " but found " + format
				);
			}
		}

		final RichText parseStaticText(final JsonElement value, final String pointer) {
			String encoded = canonical(value);
			if (encoded.length() > DefinitionCompiler.MAX_TEXT_JSON_LENGTH) {
				add("RESOURCE_LIMIT", pointer, "text component is too large");
				return null;
			}
			DataResult<Component> result = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value);
			Optional<Component> component = result.result();
			if (component.isEmpty()) {
				add(
					"INVALID_TEXT_COMPONENT",
					pointer,
					result.error().map(DataResult.Error::message).orElse("invalid text component")
				);
				return null;
			}
			String plain = component.orElseThrow().getString();
			if (plain.isBlank()) {
				add("EMPTY_TEXT", pointer, "visible text must not be blank");
				return null;
			}
			collectStaticTranslations(value, pointer);
			return new RichText(encoded, plain);
		}

		final TextTemplate parseTextTemplate(
			final JsonElement value,
			final String pointer,
			final boolean itemScope
		) {
			if (!value.isJsonObject()) {
				RichText staticText = parseStaticText(value, pointer);
				return staticText == null ? null : new StaticText(staticText);
			}
			JsonObject object = value.getAsJsonObject();
			if (object.has("bind")) {
				Reader reader = new Reader(object, pointer, this.issues);
				String binding = reader.requiredString("bind");
				JsonElement fallbackElement = reader.optionalElement("fallback");
				reader.finish();
				BindingValue parsed = parseBinding(binding, pointer + "/bind", itemScope);
				TextTemplate fallback = fallbackElement == null
					? null
					: parseTextTemplate(fallbackElement, pointer + "/fallback", itemScope);
				return parsed == null ? null : new BoundText(parsed, Optional.ofNullable(fallback));
			}
			if (object.has("duration_ticks")) {
				Reader reader = new Reader(object, pointer, this.issues);
				JsonElement ticksElement = reader.requiredElement("duration_ticks");
				JsonElement fallbackElement = reader.optionalElement("fallback");
				reader.finish();
				ValueExpression ticks = ticksElement == null
					? null
					: parseValue(ticksElement, pointer + "/duration_ticks", itemScope);
				TextTemplate fallback = fallbackElement == null
					? null
					: parseTextTemplate(fallbackElement, pointer + "/fallback", itemScope);
				return ticks == null
					? null
					: new DurationTicksText(ticks, Optional.ofNullable(fallback));
			}
			if (object.has("concat")) {
				Reader reader = new Reader(object, pointer, this.issues);
				JsonArray array = reader.requiredArray("concat");
				reader.finish();
				List<TextTemplate> parts = new ArrayList<>();
				if (array != null) {
					if (array.isEmpty()) {
						add("OUT_OF_RANGE", pointer + "/concat", "concat must contain at least one part");
					}
					for (int index = 0; index < array.size(); index++) {
						TextTemplate part = parseTextTemplate(
							array.get(index),
							pointer + "/concat/" + index,
							itemScope
						);
						if (part != null) {
							parts.add(part);
						}
					}
				}
				return new ConcatenatedText(parts);
			}
			if (object.has("translate") && (object.has("args") || object.get("fallback") instanceof JsonObject)) {
				Reader reader = new Reader(object, pointer, this.issues);
				String key = reader.requiredString("translate");
				JsonArray args = reader.optionalArray("args");
				JsonElement fallbackElement = reader.optionalElement("fallback");
				reader.finish();
				if (key != null && (key.isBlank() || key.length() > MAX_SHORT_TEXT)) {
					add("OUT_OF_RANGE", pointer + "/translate", "translation key must be non-empty and bounded");
				}
				List<ValueExpression> arguments = new ArrayList<>();
				if (args != null) {
					for (int index = 0; index < args.size(); index++) {
						ValueExpression argument = parseValue(
							args.get(index),
							pointer + "/args/" + index,
							itemScope
						);
						if (argument != null) {
							arguments.add(argument);
						}
					}
				}
				TextTemplate fallback = fallbackElement == null
					? null
					: parseTextTemplate(fallbackElement, pointer + "/fallback", itemScope);
				if (key != null) {
					this.assets.add(
						new AssetReference(
							AssetType.TRANSLATION,
							key,
							pointer + "/translate",
							fallback == null,
							Optional.empty()
						)
					);
				}
				return key == null
					? null
					: new TranslatedText(key, arguments, Optional.ofNullable(fallback));
			}
			RichText staticText = parseStaticText(value, pointer);
			return staticText == null ? null : new StaticText(staticText);
		}

		final ValueExpression parseValue(
			final JsonElement value,
			final String pointer,
			final boolean itemScope
		) {
			if (!value.isJsonObject()) {
				add("TYPE_MISMATCH", pointer, "value expression must be an object");
				return null;
			}
			Reader reader = new Reader(value.getAsJsonObject(), pointer, this.issues);
			JsonElement bindingElement = reader.optionalElement("bind");
			JsonElement literalElement = reader.optionalElement("literal");
			reader.finish();
			if ((bindingElement == null) == (literalElement == null)) {
				add("INVALID_EXPRESSION", pointer, "expected exactly one of bind or literal");
				return null;
			}
			if (bindingElement != null) {
				String binding = primitiveString(bindingElement, pointer + "/bind");
				return parseBinding(binding, pointer + "/bind", itemScope);
			}
			return new LiteralValue(canonical(literalElement));
		}

		final BindingValue parseBinding(
			final String path,
			final String pointer,
			final boolean itemScope
		) {
			if (path == null) {
				return null;
			}
			if (path.length() > 256) {
				add("RESOURCE_LIMIT", pointer, "binding path is too long");
				return null;
			}
			if (FIXED_BINDINGS.contains(path)) {
				if (path.startsWith("item.") && !itemScope) {
					add("INVALID_BINDING_SCOPE", pointer, "item bindings are only available inside Repeat.template");
					return null;
				}
				return new BindingValue(path);
			}
			if (path.startsWith("fields/")) {
				return validateNamespacedBinding(path, "fields/", pointer);
			}
			if (path.startsWith("flow/fields/")) {
				return validateNamespacedBinding(path, "flow/fields/", pointer);
			}
			if (path.startsWith("personal/")) {
				return validateNamespacedBinding(path, "personal/", pointer);
			}
			if (path.startsWith("personal_meta/")) {
				return validatePersonalMetadataBinding(path, pointer);
			}
			if (path.startsWith("ui/")) {
				String[] segments = path.split("/", -1);
				if (
					segments.length == 3
						&& LOCAL_ID.matcher(segments[1]).matches()
						&& segments[1].length() <= MAX_NODE_ID_LENGTH
						&& segments[2].equals("value")
				) {
					return new BindingValue(path);
				}
			}
			if (path.startsWith("ui.")) {
				String[] segments = path.split("\\.", -1);
				if (
					segments.length == 3
						&& LOCAL_ID.matcher(segments[1]).matches()
						&& segments[1].length() <= MAX_NODE_ID_LENGTH
						&& segments[2].equals("value")
				) {
					return new BindingValue(path);
				}
			}
			if (this.externalConditionBindings) {
				if (
					EXTERNAL_BINDING_PATH.matcher(path).matches()
						&& !path.startsWith(".")
						&& !path.startsWith("/")
						&& !path.endsWith(".")
						&& !path.endsWith("/")
						&& !path.contains("..")
						&& !path.contains("//")
				) {
					return new BindingValue(path);
				}
				add(
					"INVALID_BINDING_PATH",
					pointer,
					"external condition binding path contains unsupported characters or segments"
				);
				return null;
			}
			add("UNKNOWN_BINDING", pointer, "binding path is not exposed by the UI context");
			return null;
		}

		private BindingValue validateNamespacedBinding(
			final String path,
			final String prefix,
			final String pointer
		) {
			String rawId = path.substring(prefix.length());
			if (parseIdentifier(rawId) == null) {
				add("INVALID_IDENTIFIER", pointer, "binding field must be explicitly namespaced");
				return null;
			}
			return new BindingValue(path);
		}

		private BindingValue validatePersonalMetadataBinding(
			final String path,
			final String pointer
		) {
			String suffix = path.substring("personal_meta/".length());
			int propertySeparator = suffix.lastIndexOf('/');
			String property = propertySeparator < 0
				? ""
				: suffix.substring(propertySeparator + 1);
			if (
				propertySeparator <= 0
					|| (!property.equals("name") && !property.equals("value_name"))
			) {
				add(
					"UNKNOWN_BINDING",
					pointer,
					"personal metadata exposes only the /name and /value_name properties"
				);
				return null;
			}
			String rawId = suffix.substring(0, propertySeparator);
			if (parseIdentifier(rawId) == null) {
				add(
					"INVALID_IDENTIFIER",
					pointer,
					"personal metadata identifier must be explicitly namespaced"
				);
				return null;
			}
			return new BindingValue(path);
		}

		final ConditionExpression parseCondition(
			final JsonElement value,
			final String pointer,
			final boolean itemScope
		) {
			ExpressionBudget budget = new ExpressionBudget();
			return parseCondition(value, pointer, itemScope, budget, 1);
		}

		private ConditionExpression parseCondition(
			final JsonElement value,
			final String pointer,
			final boolean itemScope,
			final ExpressionBudget budget,
			final int depth
		) {
			if (!budget.accept(depth, pointer)) {
				return null;
			}
			if (!value.isJsonObject()) {
				add("TYPE_MISMATCH", pointer, "condition must be an object");
				return null;
			}
			JsonObject object = value.getAsJsonObject();
			if (object.size() != 1) {
				add("INVALID_EXPRESSION", pointer, "condition must contain exactly one operator");
				return null;
			}
			String operator = object.keySet().iterator().next();
			JsonElement operand = object.get(operator);
			return switch (operator) {
				case "eq", "not_eq", "greater", "greater_or_equal", "less", "less_or_equal" -> {
					if (!operand.isJsonArray() || operand.getAsJsonArray().size() != 2) {
						add("INVALID_EXPRESSION", pointer + "/" + operator, "comparison requires two values");
						yield null;
					}
					JsonArray values = operand.getAsJsonArray();
					if (!budget.accept(depth + 1, pointer + "/" + operator + "/0")) {
						yield null;
					}
					ValueExpression left = parseValue(values.get(0), pointer + "/" + operator + "/0", itemScope);
					if (!budget.accept(depth + 1, pointer + "/" + operator + "/1")) {
						yield null;
					}
					ValueExpression right = parseValue(values.get(1), pointer + "/" + operator + "/1", itemScope);
					yield left == null || right == null
						? null
						: new ComparisonCondition(
							ConditionOperator.valueOf(operator.toUpperCase(Locale.ROOT)),
							left,
							right
						);
				}
				case "all", "any" -> {
					if (!operand.isJsonArray() || operand.getAsJsonArray().isEmpty()) {
						add("INVALID_EXPRESSION", pointer + "/" + operator, "junction requires at least one condition");
						yield null;
					}
					List<ConditionExpression> conditions = new ArrayList<>();
					JsonArray array = operand.getAsJsonArray();
					for (int index = 0; index < array.size(); index++) {
						ConditionExpression child = parseCondition(
							array.get(index),
							pointer + "/" + operator + "/" + index,
							itemScope,
							budget,
							depth + 1
						);
						if (child != null) {
							conditions.add(child);
						}
					}
					yield new JunctionCondition(
						JunctionOperator.valueOf(operator.toUpperCase(Locale.ROOT)),
						conditions
					);
				}
				case "not" -> {
					ConditionExpression child = parseCondition(
						operand,
						pointer + "/not",
						itemScope,
						budget,
						depth + 1
					);
					yield child == null ? null : new NotCondition(child);
				}
				case "exists" -> {
					if (!budget.accept(depth + 1, pointer + "/exists")) {
						yield null;
					}
					ValueExpression expression = parseValue(operand, pointer + "/exists", itemScope);
					if (!(expression instanceof BindingValue binding)) {
						add("TYPE_MISMATCH", pointer + "/exists", "exists requires a binding");
						yield null;
					}
					yield new ExistsCondition(binding);
				}
				default -> {
					add("UNKNOWN_OPERATOR", pointer + "/" + escapePointer(operator), "unknown condition operator");
					yield null;
				}
			};
		}

		final Map<String, StyleValue> parseStyleProperties(
			final JsonObject object,
			final String pointer
		) {
			Map<String, StyleValue> result = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				String name = entry.getKey();
				String propertyPointer = pointer + "/" + escapePointer(name);
				if (!STYLE_PROPERTIES.contains(name)) {
					add("UNKNOWN_KEY", propertyPointer, "unknown style property");
					continue;
				}
				JsonElement value = entry.getValue();
				if (!validStyleValue(name, value, propertyPointer)) {
					continue;
				}
				result.put(name, new StyleValue(canonical(value)));
			}
			return Map.copyOf(result);
		}

		private boolean validStyleValue(
			final String property,
			final JsonElement value,
			final String pointer
		) {
			if (property.endsWith("_color")) {
				if (
					value.isJsonPrimitive()
						&& value.getAsJsonPrimitive().isString()
						&& (
							HEX_COLOR.matcher(value.getAsString()).matches()
								|| COLOR_TOKEN_REFERENCE.matcher(value.getAsString()).matches()
						)
				) {
					return true;
				}
				add("TYPE_MISMATCH", pointer, "color must be #RRGGBB or a theme token reference");
				return false;
			}
			if (property.equals("font")) {
				if (
					value.isJsonPrimitive()
						&& value.getAsJsonPrimitive().isString()
						&& (
							parseIdentifier(value.getAsString()) != null
								|| FONT_TOKEN_REFERENCE.matcher(value.getAsString()).matches()
						)
				) {
					return true;
				}
				add("TYPE_MISMATCH", pointer, "font must be a namespaced asset or theme token reference");
				return false;
			}
			if (property.equals("frame_style") || property.equals("accent_edge")) {
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
					String option = value.getAsString();
					if (
						(property.equals("frame_style") && FRAME_STYLES.contains(option))
							|| (property.equals("accent_edge") && ACCENT_EDGES.contains(option))
					) {
						return true;
					}
				}
				add(
					"TYPE_MISMATCH",
					pointer,
					property.equals("frame_style")
						? "frame_style must be plain, rail, dossier, target, broadcast, warning, portal, or manual"
						: "accent_edge must be none, left, top, right, or bottom"
				);
				return false;
			}
			if (
				property.equals("shadow")
					|| property.equals("navigation_arrows")
					|| property.equals("obfuscated")
			) {
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
					return true;
				}
				add("TYPE_MISMATCH", pointer, "expected a boolean");
				return false;
			}
			if (
				SPACING_STYLE_PROPERTIES.contains(property)
					&& value.isJsonPrimitive()
					&& value.getAsJsonPrimitive().isString()
					&& SPACING_TOKEN_REFERENCE.matcher(value.getAsString()).matches()
			) {
				return true;
			}
			Double number = finiteNumber(value);
			if (number == null) {
				add(
					"TYPE_MISMATCH",
					pointer,
					SPACING_STYLE_PROPERTIES.contains(property)
						? "expected a finite number or spacing token"
						: "expected a finite number"
				);
				return false;
			}
			if (number < 0.0 || number > MAX_LAYOUT_VALUE) {
				add("OUT_OF_RANGE", pointer, "style number is outside the supported range");
				return false;
			}
			if (property.equals("opacity") && number > 1.0) {
				add("OUT_OF_RANGE", pointer, "opacity must be between 0 and 1");
				return false;
			}
			return true;
		}

		final void collectStaticTranslations(final JsonElement value, final String pointer) {
			if (value.isJsonObject()) {
				JsonObject object = value.getAsJsonObject();
				JsonElement translated = object.get("translate");
				if (
					translated != null
						&& translated.isJsonPrimitive()
						&& translated.getAsJsonPrimitive().isString()
				) {
					String key = translated.getAsString();
					boolean hasFallback = object.has("fallback")
						&& object.get("fallback").isJsonPrimitive()
						&& object.getAsJsonPrimitive("fallback").isString();
					this.assets.add(
						new AssetReference(
							AssetType.TRANSLATION,
							key,
							pointer + "/translate",
							!hasFallback,
							Optional.empty()
						)
					);
				}
				object.entrySet()
					.forEach(entry -> collectStaticTranslations(entry.getValue(), pointer + "/" + escapePointer(entry.getKey())));
			} else if (value.isJsonArray()) {
				for (int index = 0; index < value.getAsJsonArray().size(); index++) {
					collectStaticTranslations(value.getAsJsonArray().get(index), pointer + "/" + index);
				}
			}
		}

		final String primitiveString(final JsonElement value, final String pointer) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				add("TYPE_MISMATCH", pointer, "expected a string");
				return null;
			}
			String result = value.getAsString();
			if (result.length() > MAX_SHORT_TEXT) {
				add("RESOURCE_LIMIT", pointer, "string is too long");
				return null;
			}
			return result;
		}

		final void add(final String code, final String pointer, final String message) {
			this.issues.add(new Issue(code, pointer, message));
		}

		private final class ExpressionBudget {
			private int count;

			private boolean accept(final int depth, final String pointer) {
				this.count++;
				if (this.count > MAX_EXPRESSION_NODES) {
					add("RESOURCE_LIMIT", pointer, "condition exceeds " + MAX_EXPRESSION_NODES + " nodes");
					return false;
				}
				if (depth > MAX_EXPRESSION_DEPTH) {
					add("RESOURCE_LIMIT", pointer, "condition exceeds depth " + MAX_EXPRESSION_DEPTH);
					return false;
				}
				return true;
			}
		}
	}

	private static final class PageParser extends ParserBase {
		private final Set<String> nodeIds = new HashSet<>();
		private int nodeCount;
		private int soundReferenceCount;

		private PageParser(final Identifier id, final JsonObject root, final List<Issue> issues) {
			super(id, root, issues);
		}

		private PageDefinition parse() {
			Reader reader = new Reader(this.root, "", this.issues);
			requireFormat(reader);
			Identifier game = reader.requiredIdentifier("game");
			JsonElement titleElement = reader.requiredElement("title");
			RichText title = titleElement == null ? null : parseStaticText(titleElement, "/title");
			Identifier theme = reader.optionalIdentifier("theme").orElse(UiDefinitions.DEFAULT_THEME);
			Breakpoints breakpoints = parseBreakpoints(reader.optionalObject("breakpoints"));
			JsonObject rootNode = reader.requiredObject("root");
			reader.finish();
			NodeDefinition node = rootNode == null
				? null
				: parseNode(rootNode, "/root", null, true, false, false, 1);
			if (game == null || title == null || theme == null || node == null) {
				return null;
			}
			String document = canonical(this.root);
			return new PageDefinition(
				this.id,
				game,
				title,
				theme,
				breakpoints,
				node,
				this.assets,
				document,
				sha256(document)
			);
		}

		private Breakpoints parseBreakpoints(final JsonObject object) {
			if (object == null) {
				return new Breakpoints(479, 721);
			}
			Reader reader = new Reader(object, "/breakpoints", this.issues);
			int compact = reader.optionalInteger("compact_max", 479);
			int wide = reader.optionalInteger("wide_min", 721);
			reader.finish();
			if (compact < MIN_BREAKPOINT || compact > MAX_BREAKPOINT) {
				add("OUT_OF_RANGE", "/breakpoints/compact_max", "breakpoint is outside the safe range");
			}
			if (wide < MIN_BREAKPOINT || wide > MAX_BREAKPOINT) {
				add("OUT_OF_RANGE", "/breakpoints/wide_min", "breakpoint is outside the safe range");
			}
			if (compact >= wide) {
				add("INVALID_RANGE", "/breakpoints", "compact_max must be smaller than wide_min");
			}
			return new Breakpoints(compact, wide);
		}

		private NodeDefinition parseNode(
			final JsonObject object,
			final String pointer,
			final NodeType parentType,
			final boolean rootNode,
			final boolean itemScope,
			final boolean historyItemScope,
			final int depth
		) {
			this.nodeCount++;
			if (this.nodeCount > MAX_PAGE_NODES) {
				add("RESOURCE_LIMIT", pointer, "page exceeds " + MAX_PAGE_NODES + " nodes");
				return null;
			}
			if (depth > MAX_PAGE_DEPTH) {
				add("RESOURCE_LIMIT", pointer, "page tree exceeds depth " + MAX_PAGE_DEPTH);
				return null;
			}

			Reader reader = new Reader(object, pointer, this.issues);
			NodeType type = reader.requiredEnum("type", NodeType.class);
			Optional<String> nodeId = reader.optionalLocalId("id", MAX_NODE_ID_LENGTH);
			if (nodeId.isPresent() && !this.nodeIds.add(nodeId.orElseThrow())) {
				add("DUPLICATE_NODE_ID", pointer + "/id", "node id already exists in this page");
			}
			JsonObject layoutObject = reader.optionalObject("layout");
			LayoutDefinition layout = type == null
				? LayoutDefinition.empty()
				: parseLayout(layoutObject, pointer + "/layout", type, parentType, rootNode);
			Optional<String> style = reader.optionalLocalId("style", MAX_NODE_ID_LENGTH);
			JsonObject overridesObject = reader.optionalObject("style_overrides");
			Map<String, StyleValue> styleOverrides = overridesObject == null
				? Map.of()
				: parseStyleProperties(overridesObject, pointer + "/style_overrides");
			JsonElement visibleElement = reader.optionalElement("visible_when");
			ConditionExpression visible = visibleElement == null
				? null
				: parseCondition(visibleElement, pointer + "/visible_when", itemScope);
			Map<ResponsiveTier, ResponsiveOverride> responsive = type == null
				? Map.of()
				: parseResponsive(reader.optionalObject("responsive"), pointer + "/responsive", type, parentType);
			Map<UiEvent, EventBinding> events = parseEvents(
				reader.optionalObject("events"),
				pointer + "/events"
			);
			NodeContent content = type == null
				? null
				: parseNodeContent(
					reader,
					type,
					pointer,
					itemScope,
					historyItemScope,
					depth
				);
			reader.finish();
			if (type == null || content == null) {
				return null;
			}
			return new NodeDefinition(
				type,
				nodeId,
				layout,
				style,
				styleOverrides,
				Optional.ofNullable(visible),
				responsive,
				events,
				content,
				pointer
			);
		}

		private NodeContent parseNodeContent(
			final Reader reader,
			final NodeType type,
			final String pointer,
			final boolean itemScope,
			final boolean historyItemScope,
			final int depth
		) {
			return switch (type) {
				case ROW, COLUMN, GRID, FLOW, OVERLAY, CAROUSEL -> {
					JsonArray childrenArray = reader.requiredArray("children");
					List<NodeDefinition> children = parseChildren(
						childrenArray,
						pointer + "/children",
						type,
						itemScope,
						historyItemScope,
						depth + 1
					);
					if (type == NodeType.GRID) {
						int columns = parseLayoutColumns(reader, pointer);
						if (columns > 0) {
							for (NodeDefinition child : children) {
								child.layout()
									.columnSpan()
									.filter(span -> span > columns)
									.ifPresent(
										span -> add(
											"OUT_OF_RANGE",
											child.pointer() + "/layout/column_span",
											"column_span exceeds the grid column count"
										)
									);
							}
						}
					}
					if (type == NodeType.CAROUSEL) {
						for (NodeDefinition child : children) {
							if (child.id().isEmpty()) {
								add(
									"MISSING_KEY",
									child.pointer() + "/id",
									"carousel direct children require stable ids"
								);
							}
						}
					}
					yield new ChildrenContent(children);
				}
				case SCROLL -> {
					Direction direction = reader.optionalEnum("direction", Direction.class, Direction.VERTICAL);
					boolean showScrollbar = reader.optionalBoolean("show_scrollbar", true);
					JsonObject childObject = reader.requiredObject("child");
					NodeDefinition child = childObject == null
						? null
						: parseNode(
							childObject,
							pointer + "/child",
							type,
							false,
							itemScope,
							historyItemScope,
							depth + 1
						);
					yield child == null
						? null
						: new SingleChildContent(child, Optional.ofNullable(direction), showScrollbar);
				}
				case CARD -> {
					JsonObject childObject = reader.requiredObject("child");
					NodeDefinition child = childObject == null
						? null
						: parseNode(
							childObject,
							pointer + "/child",
							type,
							false,
							itemScope,
							historyItemScope,
							depth + 1
						);
					yield child == null
						? null
						: new SingleChildContent(child, Optional.empty(), false);
				}
				case TEXT -> {
					JsonElement textElement = reader.requiredElement("text");
					TextTemplate text = textElement == null
						? null
						: parseTextTemplate(textElement, pointer + "/text", itemScope);
					boolean wrap = reader.optionalBoolean("wrap", false);
					Optional<Integer> maximumLines = reader.optionalInteger("max_lines");
					maximumLines
						.filter(value -> value < 1 || value > 256)
						.ifPresent(value -> add("OUT_OF_RANGE", pointer + "/max_lines", "max_lines must be 1..256"));
					TextOverflow overflow = reader.optionalEnum("overflow", TextOverflow.class, TextOverflow.CLIP);
					TextAlign alignment = reader.optionalEnum("text_align", TextAlign.class, TextAlign.LEFT);
					yield text == null
						? null
						: new TextContent(text, wrap, maximumLines, overflow, alignment);
				}
				case IMAGE -> {
					Identifier asset = reader.requiredIdentifier("asset");
					ImageFit fit = reader.optionalEnum("fit", ImageFit.class, ImageFit.CONTAIN);
					boolean required = reader.optionalBoolean("required", false);
					Optional<Identifier> fallback = reader.optionalIdentifier("fallback");
					JsonElement altElement = reader.optionalElement("alt");
					TextTemplate alt = altElement == null
						? null
						: parseTextTemplate(altElement, pointer + "/alt", itemScope);
					if (asset != null) {
						this.assets.add(
							new AssetReference(
								AssetType.TEXTURE,
								asset.toString(),
								pointer + "/asset",
								required,
								fallback.map(Identifier::toString)
							)
						);
					}
					fallback.ifPresent(
						value -> this.assets.add(
							new AssetReference(
								AssetType.TEXTURE,
								value.toString(),
								pointer + "/fallback",
								false,
								Optional.empty()
							)
						)
					);
					yield asset == null || fit == null
						? null
						: new ImageContent(asset, fit, required, fallback, Optional.ofNullable(alt));
				}
				case BUTTON -> {
					JsonElement labelElement = reader.requiredElement("label");
					TextTemplate label = labelElement == null
						? null
						: parseTextTemplate(labelElement, pointer + "/label", itemScope);
					JsonElement enabledElement = reader.optionalElement("enabled_when");
					ConditionExpression enabled = enabledElement == null
						? null
						: parseCondition(enabledElement, pointer + "/enabled_when", itemScope);
					JsonElement reasonElement = reader.optionalElement("disabled_reason");
					TextTemplate reason = reasonElement == null
						? null
						: parseTextTemplate(reasonElement, pointer + "/disabled_reason", itemScope);
					if (enabledElement != null && reasonElement == null) {
						add(
							"MISSING_KEY",
							pointer + "/disabled_reason",
							"buttons with enabled_when require disabled_reason"
						);
					}
					JsonObject actionObject = reader.requiredObject("action");
					UiAction action = actionObject == null
						? null
						: parseAction(
							actionObject,
							pointer + "/action",
							historyItemScope
						);
					yield label == null || action == null
						? null
						: new ButtonContent(
							label,
							Optional.ofNullable(enabled),
							Optional.ofNullable(reason),
							action
						);
				}
				case DIVIDER -> {
					Direction direction = reader.optionalEnum("direction", Direction.class, Direction.HORIZONTAL);
					int thickness = reader.optionalInteger("thickness", 1);
					if (thickness < 1 || thickness > 16) {
						add("OUT_OF_RANGE", pointer + "/thickness", "divider thickness must be 1..16");
					}
					yield direction == null ? null : new DividerContent(direction, thickness);
				}
				case SPACER -> new SpacerContent();
				case PROGRESS -> {
					JsonElement valueElement = reader.requiredElement("value");
					JsonElement minimumElement = reader.requiredElement("min");
					JsonElement maximumElement = reader.requiredElement("max");
					JsonElement labelElement = reader.optionalElement("label");
					ValueExpression value = valueElement == null
						? null
						: parseValue(valueElement, pointer + "/value", itemScope);
					ValueExpression minimum = minimumElement == null
						? null
						: parseValue(minimumElement, pointer + "/min", itemScope);
					ValueExpression maximum = maximumElement == null
						? null
						: parseValue(maximumElement, pointer + "/max", itemScope);
					TextTemplate label = labelElement == null
						? null
						: parseTextTemplate(labelElement, pointer + "/label", itemScope);
					yield value == null || minimum == null || maximum == null
						? null
						: new ProgressContent(value, minimum, maximum, Optional.ofNullable(label));
				}
				case PLAYER_HEAD -> {
					JsonElement uuidElement = reader.requiredElement("uuid");
					JsonElement nameElement = reader.requiredElement("name");
					JsonElement onlineElement = reader.requiredElement("online");
					ValueExpression uuid = uuidElement == null
						? null
						: parseValue(uuidElement, pointer + "/uuid", itemScope);
					ValueExpression name = nameElement == null
						? null
						: parseValue(nameElement, pointer + "/name", itemScope);
					ValueExpression online = onlineElement == null
						? null
						: parseValue(onlineElement, pointer + "/online", itemScope);
					boolean showStatus = reader.optionalBoolean("show_status", true);
					yield uuid == null || name == null || online == null
						? null
						: new PlayerHeadContent(uuid, name, online, showStatus);
				}
				case REPEAT -> {
					JsonElement itemsElement = reader.requiredElement("items");
					JsonElement keyElement = reader.requiredElement("item_key");
					ValueExpression items = itemsElement == null
						? null
						: parseValue(itemsElement, pointer + "/items", itemScope);
					ValueExpression key = keyElement == null
						? null
						: parseValue(keyElement, pointer + "/item_key", true);
					int maximumItems = reader.optionalInteger("max_items", 64);
					int estimatedHeight = reader.optionalInteger("estimated_item_height", 28);
					if (maximumItems < 1 || maximumItems > MAX_REPEAT_ITEMS) {
						add(
							"OUT_OF_RANGE",
							pointer + "/max_items",
							"max_items must be 1.." + MAX_REPEAT_ITEMS
						);
					}
					if (estimatedHeight < 1 || estimatedHeight > MAX_LAYOUT_VALUE) {
						add(
							"OUT_OF_RANGE",
							pointer + "/estimated_item_height",
							"estimated item height is outside the safe range"
						);
					}
					JsonObject templateObject = reader.requiredObject("template");
					boolean historyTemplate =
						items instanceof BindingValue binding
							&& binding.path().equals("history.items");
					NodeDefinition template = templateObject == null
						? null
						: parseNode(
							templateObject,
							pointer + "/template",
							type,
							false,
							true,
							historyTemplate,
							depth + 1
						);
					yield items == null || key == null || template == null
						? null
						: new RepeatContent(items, key, maximumItems, estimatedHeight, template);
				}
				case FIELD_INPUT -> {
					Identifier field = reader.requiredIdentifier("field");
					FieldPresentation presentation = reader.optionalEnum(
						"presentation",
						FieldPresentation.class,
						FieldPresentation.AUTO
					);
					boolean showLabel = reader.optionalBoolean("show_label", true);
					boolean showDescription = reader.optionalBoolean("show_description", true);
					if (itemScope) {
						add(
							"UNSUPPORTED_COMBINATION",
							pointer,
							"FieldInput inside Repeat requires item-scoped UI state and is not supported"
						);
					}
					yield field == null || presentation == null || itemScope
						? null
						: new FieldInputContent(field, presentation, showLabel, showDescription);
				}
			};
		}

		private int parseLayoutColumns(final Reader nodeReader, final String pointer) {
			JsonObject layout = nodeReader.peekConsumedObject("layout");
			if (layout == null) {
				add("MISSING_KEY", pointer + "/layout/columns", "grid requires layout.columns");
				return 0;
			}
			JsonElement columns = layout.get("columns");
			if (columns == null || !columns.isJsonArray()) {
				return 0;
			}
			return columns.getAsJsonArray().size();
		}

		private List<NodeDefinition> parseChildren(
			final JsonArray array,
			final String pointer,
			final NodeType parent,
			final boolean itemScope,
			final boolean historyItemScope,
			final int depth
		) {
			if (array == null) {
				return List.of();
			}
			if (array.size() > MAX_DIRECT_CHILDREN) {
				add(
					"RESOURCE_LIMIT",
					pointer,
					"container exceeds " + MAX_DIRECT_CHILDREN + " direct children"
				);
			}
			List<NodeDefinition> children = new ArrayList<>();
			for (int index = 0; index < Math.min(array.size(), MAX_DIRECT_CHILDREN); index++) {
				JsonElement value = array.get(index);
				if (!value.isJsonObject()) {
					add("TYPE_MISMATCH", pointer + "/" + index, "child node must be an object");
					continue;
				}
				NodeDefinition child = parseNode(
					value.getAsJsonObject(),
					pointer + "/" + index,
					parent,
					false,
					itemScope,
					historyItemScope,
					depth
				);
				if (child != null) {
					children.add(child);
				}
			}
			return children;
		}

		private LayoutDefinition parseLayout(
			final JsonObject object,
			final String pointer,
			final NodeType nodeType,
			final NodeType parentType,
			final boolean rootNode
		) {
			if (object == null) {
				if (!rootNode) {
					return LayoutDefinition.empty();
				}
				return withRootDefaults(LayoutDefinition.empty());
			}
			Reader reader = new Reader(object, pointer, this.issues);
			SizeSpec width = parseSize(reader.optionalObject("width"), pointer + "/width", parentType, false);
			SizeSpec height = parseSize(reader.optionalObject("height"), pointer + "/height", parentType, false);
			Optional<Integer> minimumWidth = reader.optionalInteger("min_width");
			Optional<Integer> maximumWidth = reader.optionalInteger("max_width");
			Optional<Integer> minimumHeight = reader.optionalInteger("min_height");
			Optional<Integer> maximumHeight = reader.optionalInteger("max_height");
			validateDimension(minimumWidth, pointer + "/min_width");
			validateDimension(maximumWidth, pointer + "/max_width");
			validateDimension(minimumHeight, pointer + "/min_height");
			validateDimension(maximumHeight, pointer + "/max_height");
			validateRange(minimumWidth, maximumWidth, pointer, "width");
			validateRange(minimumHeight, maximumHeight, pointer, "height");
			Insets margin = parseInsets(reader.optionalObject("margin"), pointer + "/margin");
			Insets padding = parseInsets(reader.optionalObject("padding"), pointer + "/padding");
			AlignSelf alignSelf = reader.optionalEnum("align_self", AlignSelf.class, AlignSelf.AUTO);
			Optional<Integer> gap = reader.optionalInteger("gap");
			Optional<Integer> lineGap = reader.optionalInteger("line_gap");
			Optional<Integer> columnGap = reader.optionalInteger("column_gap");
			Optional<Integer> rowGap = reader.optionalInteger("row_gap");
			validateDimension(gap, pointer + "/gap");
			validateDimension(lineGap, pointer + "/line_gap");
			validateDimension(columnGap, pointer + "/column_gap");
			validateDimension(rowGap, pointer + "/row_gap");
			Optional<MainAlign> mainAlign = reader.optionalEnum("main_align", MainAlign.class);
			Optional<CrossAlign> crossAlign = reader.optionalEnum("cross_align", CrossAlign.class);
			Optional<Direction> direction = reader.optionalEnum("direction", Direction.class);
			JsonArray columnsArray = reader.optionalArray("columns");
			List<SizeSpec> columns = parseGridColumns(columnsArray, pointer + "/columns");
			Optional<Integer> columnSpan = reader.optionalInteger("column_span");
			Optional<Anchor> anchor = reader.optionalEnum("anchor", Anchor.class);
			Optional<Integer> offsetX = reader.optionalInteger("offset_x");
			Optional<Integer> offsetY = reader.optionalInteger("offset_y");
			reader.finish();

			if (gap.isPresent() && nodeType != NodeType.ROW && nodeType != NodeType.COLUMN && nodeType != NodeType.FLOW) {
				add("INVALID_LAYOUT", pointer + "/gap", "gap is only valid on row, column, or flow");
			}
			if (lineGap.isPresent() && nodeType != NodeType.FLOW) {
				add("INVALID_LAYOUT", pointer + "/line_gap", "line_gap is only valid on flow");
			}
			if ((columnGap.isPresent() || rowGap.isPresent() || !columns.isEmpty()) && nodeType != NodeType.GRID) {
				add("INVALID_LAYOUT", pointer, "grid layout fields are only valid on grid");
			}
			if (nodeType == NodeType.GRID && columns.isEmpty()) {
				add("MISSING_KEY", pointer + "/columns", "grid requires at least one column");
			}
			if ((mainAlign.isPresent() || crossAlign.isPresent()) && !isLinearContainer(nodeType)) {
				add("INVALID_LAYOUT", pointer, "alignment fields require row, column, or flow");
			}
			if (direction.isPresent() && nodeType != NodeType.FLOW) {
				add("INVALID_LAYOUT", pointer + "/direction", "layout.direction is only valid on flow");
			}
			if (columnSpan.isPresent() && parentType != NodeType.GRID) {
				add("INVALID_LAYOUT", pointer + "/column_span", "column_span is only valid on grid children");
			}
			columnSpan
				.filter(value -> value < 1)
				.ifPresent(value -> add("OUT_OF_RANGE", pointer + "/column_span", "column_span must be positive"));
			if (
				(anchor.isPresent() || offsetX.isPresent() || offsetY.isPresent())
					&& parentType != NodeType.OVERLAY
					&& parentType != NodeType.CAROUSEL
			) {
				add(
					"INVALID_LAYOUT",
					pointer,
					"anchor and offsets are only valid on overlay or carousel children"
				);
			}
			validateOffset(offsetX, pointer + "/offset_x");
			validateOffset(offsetY, pointer + "/offset_y");

			LayoutDefinition result = new LayoutDefinition(
				Optional.ofNullable(width),
				Optional.ofNullable(height),
				minimumWidth,
				maximumWidth,
				minimumHeight,
				maximumHeight,
				margin,
				padding,
				alignSelf,
				gap,
				lineGap,
				columnGap,
				rowGap,
				mainAlign,
				crossAlign,
				direction,
				columns,
				columnSpan,
				anchor,
				offsetX,
				offsetY
			);
			return rootNode ? withRootDefaults(result) : result;
		}

		private LayoutDefinition withRootDefaults(final LayoutDefinition layout) {
			SizeSpec fill = new SizeSpec(SizeMode.FILL, Optional.empty());
			return new LayoutDefinition(
				layout.width().isPresent() ? layout.width() : Optional.of(fill),
				layout.height().isPresent() ? layout.height() : Optional.of(fill),
				layout.minimumWidth(),
				layout.maximumWidth(),
				layout.minimumHeight(),
				layout.maximumHeight(),
				layout.margin(),
				layout.padding(),
				layout.alignSelf(),
				layout.gap(),
				layout.lineGap(),
				layout.columnGap(),
				layout.rowGap(),
				layout.mainAlign(),
				layout.crossAlign(),
				layout.direction(),
				layout.columns(),
				layout.columnSpan(),
				layout.anchor(),
				layout.offsetX(),
				layout.offsetY()
			);
		}

		private SizeSpec parseSize(
			final JsonObject object,
			final String pointer,
			final NodeType parentType,
			final boolean gridTrack
		) {
			if (object == null) {
				return null;
			}
			Reader reader = new Reader(object, pointer, this.issues);
			SizeMode mode = reader.requiredEnum("mode", SizeMode.class);
			JsonElement valueElement = reader.optionalElement("value");
			reader.finish();
			if (mode == null) {
				return null;
			}
			Double value = valueElement == null ? null : finiteNumber(valueElement);
			if (valueElement != null && value == null) {
				add("TYPE_MISMATCH", pointer + "/value", "size value must be a finite number");
			}
			switch (mode) {
				case CONTENT, FILL -> {
					if (valueElement != null) {
						add("UNKNOWN_KEY", pointer + "/value", mode.name().toLowerCase(Locale.ROOT) + " has no value");
					}
				}
				case FIXED -> {
					if (value == null || value < 0 || value > MAX_LAYOUT_VALUE || value % 1.0 != 0.0) {
						add("OUT_OF_RANGE", pointer + "/value", "fixed size must be an integer in the safe range");
					}
				}
				case PERCENT -> {
					if (value == null || value < 0.0 || value > 100.0) {
						add("OUT_OF_RANGE", pointer + "/value", "percent size must be 0..100");
					}
				}
				case WEIGHT -> {
					if (value == null || value <= 0.0 || value > MAX_LAYOUT_VALUE) {
						add("OUT_OF_RANGE", pointer + "/value", "weight must be positive and bounded");
					}
					if (
						!gridTrack
							&& parentType != NodeType.ROW
							&& parentType != NodeType.COLUMN
							&& parentType != NodeType.GRID
					) {
						add("INVALID_LAYOUT", pointer, "weight is only valid inside row, column, or grid");
					}
				}
			}
			if (gridTrack && mode == SizeMode.FILL) {
				add("INVALID_LAYOUT", pointer, "grid tracks do not support fill");
			}
			return new SizeSpec(mode, Optional.ofNullable(value));
		}

		private List<SizeSpec> parseGridColumns(final JsonArray array, final String pointer) {
			if (array == null) {
				return List.of();
			}
			if (array.isEmpty() || array.size() > 32) {
				add("OUT_OF_RANGE", pointer, "grid columns must contain 1..32 tracks");
			}
			List<SizeSpec> columns = new ArrayList<>();
			for (int index = 0; index < Math.min(array.size(), 32); index++) {
				JsonElement element = array.get(index);
				if (!element.isJsonObject()) {
					add("TYPE_MISMATCH", pointer + "/" + index, "grid track must be an object");
					continue;
				}
				SizeSpec parsed = parseSize(element.getAsJsonObject(), pointer + "/" + index, NodeType.GRID, true);
				if (parsed != null) {
					columns.add(parsed);
				}
			}
			return columns;
		}

		private Insets parseInsets(final JsonObject object, final String pointer) {
			if (object == null) {
				return Insets.zero();
			}
			Reader reader = new Reader(object, pointer, this.issues);
			int horizontal = reader.optionalInteger("horizontal", 0);
			int vertical = reader.optionalInteger("vertical", 0);
			int top = reader.optionalInteger("top", vertical);
			int right = reader.optionalInteger("right", horizontal);
			int bottom = reader.optionalInteger("bottom", vertical);
			int left = reader.optionalInteger("left", horizontal);
			reader.finish();
			int[] values = {top, right, bottom, left};
			for (int index = 0; index < values.length; index++) {
				if (values[index] < 0 || values[index] > MAX_LAYOUT_VALUE) {
					add("OUT_OF_RANGE", pointer, "inset must be non-negative and bounded");
					break;
				}
			}
			return new Insets(top, right, bottom, left);
		}

		private Map<ResponsiveTier, ResponsiveOverride> parseResponsive(
			final JsonObject object,
			final String pointer,
			final NodeType originalType,
			final NodeType parentType
		) {
			if (object == null) {
				return Map.of();
			}
			Map<ResponsiveTier, ResponsiveOverride> result = new EnumMap<>(ResponsiveTier.class);
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				ResponsiveTier tier = enumValue(ResponsiveTier.class, entry.getKey());
				String tierPointer = pointer + "/" + escapePointer(entry.getKey());
				if (tier == null) {
					add("UNKNOWN_KEY", tierPointer, "unknown responsive tier");
					continue;
				}
				if (!entry.getValue().isJsonObject()) {
					add("TYPE_MISMATCH", tierPointer, "responsive override must be an object");
					continue;
				}
				Reader reader = new Reader(entry.getValue().getAsJsonObject(), tierPointer, this.issues);
				Optional<NodeType> type = reader.optionalEnum("type", NodeType.class);
				type.ifPresent(
					value -> {
						if (!isResponsiveTypeSwitchAllowed(originalType, value)) {
							add(
								"INVALID_RESPONSIVE_OVERRIDE",
								tierPointer + "/type",
								"container type switch is incompatible with this node"
							);
						}
					}
				);
				NodeType effectiveType = type.orElse(originalType);
				JsonObject layoutObject = reader.optionalObject("layout");
				LayoutDefinition layout = layoutObject == null
					? null
					: parseLayout(layoutObject, tierPointer + "/layout", effectiveType, parentType, false);
				Optional<String> style = reader.optionalLocalId("style", MAX_NODE_ID_LENGTH);
				JsonObject overrides = reader.optionalObject("style_overrides");
				Optional<Boolean> visible = reader.optionalBoolean("visible");
				reader.finish();
				result.put(
					tier,
					new ResponsiveOverride(
						type,
						Optional.ofNullable(layout),
						layoutObject == null ? Set.of() : Set.copyOf(layoutObject.keySet()),
						style,
						overrides == null
							? Map.of()
							: parseStyleProperties(overrides, tierPointer + "/style_overrides"),
						visible
					)
				);
			}
			return Map.copyOf(result);
		}

		private Map<UiEvent, EventBinding> parseEvents(final JsonObject object, final String pointer) {
			if (object == null) {
				return Map.of();
			}
			Map<UiEvent, EventBinding> result = new EnumMap<>(UiEvent.class);
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				UiEvent event = enumValue(UiEvent.class, entry.getKey());
				String eventPointer = pointer + "/" + escapePointer(entry.getKey());
				if (event == null) {
					add("UNKNOWN_KEY", eventPointer, "unknown UI event");
					continue;
				}
				if (!entry.getValue().isJsonObject()) {
					add("TYPE_MISMATCH", eventPointer, "event binding must be an object");
					continue;
				}
				Reader reader = new Reader(entry.getValue().getAsJsonObject(), eventPointer, this.issues);
				Optional<String> motion = reader.optionalLocalId("motion", MAX_NODE_ID_LENGTH);
				Optional<String> sound = reader.optionalLocalId("sound", MAX_NODE_ID_LENGTH);
				reader.finish();
				if (motion.isEmpty() && sound.isEmpty()) {
					add("MISSING_KEY", eventPointer, "event binding requires motion or sound");
				}
				if (sound.isPresent()) {
					this.soundReferenceCount++;
					if (this.soundReferenceCount > MAX_PAGE_SOUND_REFERENCES) {
						add(
							"RESOURCE_LIMIT",
							eventPointer + "/sound",
							"page exceeds " + MAX_PAGE_SOUND_REFERENCES + " sound references"
						);
					}
				}
				result.put(event, new EventBinding(motion, sound));
			}
			return Map.copyOf(result);
		}

		private UiAction parseAction(
			final JsonObject object,
			final String pointer,
			final boolean historyItemScope
		) {
			Reader reader = new Reader(object, pointer, this.issues);
			ActionType type = reader.requiredEnum("type", ActionType.class);
			Optional<String> name = Optional.empty();
			Optional<Identifier> registered = Optional.empty();
			if (type == ActionType.LOCAL || type == ActionType.FLOW) {
				name = reader.optionalLocalId("name", MAX_NODE_ID_LENGTH);
				if (name.isEmpty()) {
					add("MISSING_KEY", pointer + "/name", "action name is required");
				} else if (type == ActionType.LOCAL && !LOCAL_ACTIONS.contains(name.orElseThrow())) {
					add("INVALID_ENUM", pointer + "/name", "unknown local action");
				}
			} else if (type == ActionType.REGISTERED) {
				registered = reader.optionalIdentifier("action");
				if (registered.isEmpty()) {
					add("MISSING_KEY", pointer + "/action", "registered action ID is required");
				}
			} else if (type == ActionType.HISTORY_DETAIL && !historyItemScope) {
				add(
					"UNSUPPORTED_COMBINATION",
					pointer,
					"history_detail is only allowed inside a Repeat template bound to history.items"
				);
			}
			reader.finish();
			return type == null ? null : new UiAction(type, name, registered);
		}

		private void validateDimension(final Optional<Integer> value, final String pointer) {
			value
				.filter(number -> number < 0 || number > MAX_LAYOUT_VALUE)
				.ifPresent(number -> add("OUT_OF_RANGE", pointer, "layout value is outside the safe range"));
		}

		private void validateOffset(final Optional<Integer> value, final String pointer) {
			value
				.filter(number -> number < -MAX_LAYOUT_VALUE || number > MAX_LAYOUT_VALUE)
				.ifPresent(number -> add("OUT_OF_RANGE", pointer, "offset is outside the safe range"));
		}

		private void validateRange(
			final Optional<Integer> minimum,
			final Optional<Integer> maximum,
			final String pointer,
			final String name
		) {
			if (minimum.isPresent() && maximum.isPresent() && minimum.orElseThrow() > maximum.orElseThrow()) {
				add("INVALID_RANGE", pointer, "minimum " + name + " exceeds maximum " + name);
			}
		}

		private static boolean isLinearContainer(final NodeType type) {
			return type == NodeType.ROW || type == NodeType.COLUMN || type == NodeType.FLOW;
		}

	private static boolean isResponsiveContainer(final NodeType type) {
			return type == NodeType.ROW
				|| type == NodeType.COLUMN
				|| type == NodeType.GRID
				|| type == NodeType.FLOW;
		}

		private static boolean isResponsiveTypeSwitchAllowed(
			final NodeType original,
			final NodeType replacement
		) {
			if (original == NodeType.REPEAT) {
				return replacement == NodeType.REPEAT
					|| replacement == NodeType.COLUMN
					|| replacement == NodeType.GRID;
			}
			return replacement != NodeType.REPEAT
				&& isResponsiveContainer(original)
				&& isResponsiveContainer(replacement);
		}
	}

	private static final class ThemeParser extends ParserBase {
		private ThemeParser(final Identifier id, final JsonObject root, final List<Issue> issues) {
			super(id, root, issues);
		}

		private ThemeDefinition parse() {
			Reader reader = new Reader(this.root, "", this.issues);
			requireFormat(reader);
			JsonObject tokensObject = reader.requiredObject("tokens");
			JsonObject stylesObject = reader.requiredObject("styles");
			JsonObject motionsObject = reader.requiredObject("motions");
			JsonObject soundsObject = reader.requiredObject("sound_cues");
			reader.finish();

			ThemeTokens tokens = parseTokens(tokensObject);
			Map<String, StyleDefinition> styles = parseStyles(stylesObject);
			Map<String, MotionDefinition> motions = parseMotions(motionsObject);
			Map<String, SoundCue> sounds = parseSounds(soundsObject);
			if (tokens == null) {
				return null;
			}
			validateStyleTokenReferences(tokens, styles);
			validateMotionTokenReferences(tokens, motions);
			String document = canonical(this.root);
			return new ThemeDefinition(
				this.id,
				tokens,
				styles,
				motions,
				sounds,
				this.assets,
				document,
				sha256(document)
			);
		}

		private ThemeTokens parseTokens(final JsonObject object) {
			if (object == null) {
				return null;
			}
			Reader reader = new Reader(object, "/tokens", this.issues);
			JsonObject colorsObject = reader.optionalObject("colors");
			JsonObject spacingObject = reader.optionalObject("spacing");
			JsonObject fontsObject = reader.optionalObject("fonts");
			reader.finish();

			Map<String, String> colors = new LinkedHashMap<>();
			if (colorsObject != null) {
				for (Map.Entry<String, JsonElement> entry : colorsObject.entrySet()) {
					String pointer = "/tokens/colors/" + escapePointer(entry.getKey());
					if (!validLocalName(entry.getKey(), pointer)) {
						continue;
					}
					String color = primitiveString(entry.getValue(), pointer);
					if (color != null && !HEX_COLOR.matcher(color).matches()) {
						add("INVALID_COLOR", pointer, "color token must be #RRGGBB");
					} else if (color != null) {
						colors.put(entry.getKey(), color);
					}
				}
			}

			Map<String, Integer> spacing = new LinkedHashMap<>();
			if (spacingObject != null) {
				for (Map.Entry<String, JsonElement> entry : spacingObject.entrySet()) {
					String pointer = "/tokens/spacing/" + escapePointer(entry.getKey());
					if (!validLocalName(entry.getKey(), pointer)) {
						continue;
					}
					Integer value = integralNumber(entry.getValue());
					if (value == null) {
						add("TYPE_MISMATCH", pointer, "spacing token must be an integer");
					} else if (value < 0 || value > MAX_LAYOUT_VALUE) {
						add("OUT_OF_RANGE", pointer, "spacing token is outside the safe range");
					} else {
						spacing.put(entry.getKey(), value);
					}
				}
			}

			Map<String, FontToken> fonts = new LinkedHashMap<>();
			if (fontsObject != null) {
				for (Map.Entry<String, JsonElement> entry : fontsObject.entrySet()) {
					String pointer = "/tokens/fonts/" + escapePointer(entry.getKey());
					if (!validLocalName(entry.getKey(), pointer)) {
						continue;
					}
					if (!entry.getValue().isJsonObject()) {
						add("TYPE_MISMATCH", pointer, "font token must be an object");
						continue;
					}
					Reader fontReader = new Reader(entry.getValue().getAsJsonObject(), pointer, this.issues);
					Identifier asset = fontReader.requiredIdentifier("asset");
					boolean required = fontReader.optionalBoolean("required", false);
					Optional<Identifier> fallback = fontReader.optionalIdentifier("fallback");
					fontReader.finish();
					if (asset != null) {
						fonts.put(entry.getKey(), new FontToken(asset, required, fallback));
						this.assets.add(
							new AssetReference(
								AssetType.FONT,
								asset.toString(),
								pointer + "/asset",
								required,
								fallback.map(Identifier::toString)
							)
						);
					}
				}
			}
			return new ThemeTokens(colors, spacing, fonts);
		}

		private Map<String, StyleDefinition> parseStyles(final JsonObject object) {
			if (object == null) {
				return Map.of();
			}
			if (object.size() > MAX_THEME_STYLES) {
				add("RESOURCE_LIMIT", "/styles", "theme exceeds " + MAX_THEME_STYLES + " named styles");
			}
			Map<String, StyleDefinition> result = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (result.size() >= MAX_THEME_STYLES) {
					break;
				}
				String pointer = "/styles/" + escapePointer(entry.getKey());
				if (!validLocalName(entry.getKey(), pointer)) {
					continue;
				}
				if (!entry.getValue().isJsonObject()) {
					add("TYPE_MISMATCH", pointer, "style must be an object of state objects");
					continue;
				}
				Map<StyleState, Map<String, StyleValue>> states = new EnumMap<>(StyleState.class);
				for (Map.Entry<String, JsonElement> stateEntry : entry.getValue().getAsJsonObject().entrySet()) {
					String statePointer = pointer + "/" + escapePointer(stateEntry.getKey());
					StyleState state = enumValue(StyleState.class, stateEntry.getKey());
					if (state == null) {
						add("UNKNOWN_KEY", statePointer, "unknown style state");
						continue;
					}
					if (!stateEntry.getValue().isJsonObject()) {
						add("TYPE_MISMATCH", statePointer, "style state must be an object");
						continue;
					}
					states.put(
						state,
						parseStyleProperties(stateEntry.getValue().getAsJsonObject(), statePointer)
					);
				}
				result.put(entry.getKey(), new StyleDefinition(states));
			}
			return Map.copyOf(result);
		}

		private Map<String, MotionDefinition> parseMotions(final JsonObject object) {
			if (object == null) {
				return Map.of();
			}
			if (object.size() > MAX_THEME_MOTIONS) {
				add("RESOURCE_LIMIT", "/motions", "theme exceeds " + MAX_THEME_MOTIONS + " motions");
			}
			Map<String, MotionDefinition> result = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (result.size() >= MAX_THEME_MOTIONS) {
					break;
				}
				String pointer = "/motions/" + escapePointer(entry.getKey());
				if (!validLocalName(entry.getKey(), pointer)) {
					continue;
				}
				if (!entry.getValue().isJsonObject()) {
					add("TYPE_MISMATCH", pointer, "motion must be an object");
					continue;
				}
				Reader reader = new Reader(entry.getValue().getAsJsonObject(), pointer, this.issues);
				int duration = reader.optionalInteger("duration_ms", 0);
				int delay = reader.optionalInteger("delay_ms", 0);
				Easing easing = reader.optionalEnum("easing", Easing.class, Easing.LINEAR);
				JsonArray tracksArray = reader.requiredArray("tracks");
				ReducedMotion reduced = reader.optionalEnum(
					"reduced_motion",
					ReducedMotion.class,
					ReducedMotion.FINAL
				);
				reader.finish();
				if (duration < 0 || duration > MAX_MOTION_DURATION_MILLIS) {
					add(
						"OUT_OF_RANGE",
						pointer + "/duration_ms",
						"duration must be 0.." + MAX_MOTION_DURATION_MILLIS
					);
				}
				if (delay < 0 || delay > MAX_MOTION_DURATION_MILLIS) {
					add(
						"OUT_OF_RANGE",
						pointer + "/delay_ms",
						"delay must be 0.." + MAX_MOTION_DURATION_MILLIS
					);
				}
				List<MotionTrack> tracks = parseTracks(tracksArray, pointer + "/tracks");
				if (easing != null && reduced != null) {
					result.put(entry.getKey(), new MotionDefinition(duration, delay, easing, tracks, reduced));
				}
			}
			return Map.copyOf(result);
		}

		private List<MotionTrack> parseTracks(final JsonArray array, final String pointer) {
			if (array == null) {
				return List.of();
			}
			if (array.isEmpty() || array.size() > MAX_MOTION_TRACKS) {
				add(
					"OUT_OF_RANGE",
					pointer,
					"motion must contain 1.." + MAX_MOTION_TRACKS + " tracks"
				);
			}
			List<MotionTrack> tracks = new ArrayList<>();
			Set<MotionProperty> properties = new HashSet<>();
			for (int index = 0; index < Math.min(array.size(), MAX_MOTION_TRACKS); index++) {
				JsonElement element = array.get(index);
				String trackPointer = pointer + "/" + index;
				if (!element.isJsonObject()) {
					add("TYPE_MISMATCH", trackPointer, "motion track must be an object");
					continue;
				}
				Reader reader = new Reader(element.getAsJsonObject(), trackPointer, this.issues);
				MotionProperty property = reader.requiredEnum("property", MotionProperty.class);
				JsonArray framesArray = reader.requiredArray("keyframes");
				reader.finish();
				if (property != null && !properties.add(property)) {
					add("DUPLICATE_VALUE", trackPointer + "/property", "motion property already has a track");
				}
				List<MotionKeyframe> frames = parseFrames(framesArray, trackPointer + "/keyframes", property);
				if (property != null) {
					tracks.add(new MotionTrack(property, frames));
				}
			}
			return tracks;
		}

		private List<MotionKeyframe> parseFrames(
			final JsonArray array,
			final String pointer,
			final MotionProperty property
		) {
			if (array == null) {
				return List.of();
			}
			if (array.size() < 2 || array.size() > MAX_MOTION_KEYFRAMES) {
				add(
					"OUT_OF_RANGE",
					pointer,
					"motion track must contain 2.." + MAX_MOTION_KEYFRAMES + " keyframes"
				);
			}
			List<MotionKeyframe> frames = new ArrayList<>();
			double previous = -1.0;
			for (int index = 0; index < Math.min(array.size(), MAX_MOTION_KEYFRAMES); index++) {
				JsonElement element = array.get(index);
				String framePointer = pointer + "/" + index;
				if (!element.isJsonObject()) {
					add("TYPE_MISMATCH", framePointer, "keyframe must be an object");
					continue;
				}
				Reader reader = new Reader(element.getAsJsonObject(), framePointer, this.issues);
				JsonElement atElement = reader.requiredElement("at");
				JsonElement value = reader.requiredElement("value");
				reader.finish();
				Double at = atElement == null ? null : finiteNumber(atElement);
				if (at == null || at < 0.0 || at > 1.0) {
					add("OUT_OF_RANGE", framePointer + "/at", "keyframe at must be 0..1");
					continue;
				}
				if (at <= previous) {
					add("INVALID_RANGE", framePointer + "/at", "keyframe positions must strictly increase");
				}
				previous = at;
				if (value == null || !validMotionValue(property, value, framePointer + "/value")) {
					continue;
				}
				frames.add(new MotionKeyframe(at, new StyleValue(canonical(value))));
			}
			if (!frames.isEmpty()) {
				if (frames.getFirst().at() != 0.0) {
					add("INVALID_RANGE", pointer + "/0/at", "first keyframe must start at 0.0");
				}
				if (frames.getLast().at() != 1.0) {
					add("INVALID_RANGE", pointer + "/" + (frames.size() - 1) + "/at", "last keyframe must end at 1.0");
				}
			}
			return frames;
		}

		private boolean validMotionValue(
			final MotionProperty property,
			final JsonElement value,
			final String pointer
		) {
			if (property == MotionProperty.COLOR) {
				if (
					value.isJsonPrimitive()
						&& value.getAsJsonPrimitive().isString()
						&& (
							HEX_COLOR.matcher(value.getAsString()).matches()
								|| COLOR_TOKEN_REFERENCE.matcher(value.getAsString()).matches()
						)
				) {
					return true;
				}
				add("TYPE_MISMATCH", pointer, "color keyframe must be #RRGGBB or a token reference");
				return false;
			}
			Double number = finiteNumber(value);
			if (number == null) {
				add("TYPE_MISMATCH", pointer, "motion keyframe value must be a finite number");
				return false;
			}
			if (
				(property == MotionProperty.OPACITY
					|| property == MotionProperty.CLIP_PROGRESS)
					&& (number < 0.0 || number > 1.0)
			) {
				add("OUT_OF_RANGE", pointer, "normalized motion value must be 0..1");
				return false;
			}
			if (property == MotionProperty.SCALE && (number < 0.0 || number > 8.0)) {
				add("OUT_OF_RANGE", pointer, "scale must be 0..8");
				return false;
			}
			if (
				(property == MotionProperty.TRANSLATE_X || property == MotionProperty.TRANSLATE_Y)
					&& (number < -MAX_LAYOUT_VALUE || number > MAX_LAYOUT_VALUE)
			) {
				add("OUT_OF_RANGE", pointer, "translation is outside the safe range");
				return false;
			}
			return true;
		}

		private Map<String, SoundCue> parseSounds(final JsonObject object) {
			if (object == null) {
				return Map.of();
			}
			if (object.size() > MAX_PAGE_SOUND_REFERENCES) {
				add(
					"RESOURCE_LIMIT",
					"/sound_cues",
					"theme exceeds " + MAX_PAGE_SOUND_REFERENCES + " sound cues"
				);
			}
			Map<String, SoundCue> result = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (result.size() >= MAX_PAGE_SOUND_REFERENCES) {
					break;
				}
				String pointer = "/sound_cues/" + escapePointer(entry.getKey());
				if (!validLocalName(entry.getKey(), pointer)) {
					continue;
				}
				if (!entry.getValue().isJsonObject()) {
					add("TYPE_MISMATCH", pointer, "sound cue must be an object");
					continue;
				}
				Reader reader = new Reader(entry.getValue().getAsJsonObject(), pointer, this.issues);
				Identifier sound = reader.requiredIdentifier("sound");
				int delay = reader.optionalInteger("delay_ms", 0);
				double volume = reader.optionalDouble("volume", 1.0);
				double pitch = reader.optionalDouble("pitch", 1.0);
				boolean required = reader.optionalBoolean("required", false);
				reader.finish();
				if (delay < 0 || delay > MAX_MOTION_DURATION_MILLIS) {
					add("OUT_OF_RANGE", pointer + "/delay_ms", "sound delay is outside the safe range");
				}
				if (volume < 0.0 || volume > 1.0) {
					add("OUT_OF_RANGE", pointer + "/volume", "sound volume must be 0..1");
				}
				if (pitch < 0.5 || pitch > 2.0) {
					add("OUT_OF_RANGE", pointer + "/pitch", "sound pitch must be 0.5..2");
				}
				if (sound != null) {
					result.put(entry.getKey(), new SoundCue(sound, delay, volume, pitch, required));
					this.assets.add(
						new AssetReference(
							AssetType.SOUND,
							sound.toString(),
							pointer + "/sound",
							required,
							Optional.empty()
						)
					);
				}
			}
			return Map.copyOf(result);
		}

		private boolean validLocalName(final String value, final String pointer) {
			if (!LOCAL_ID.matcher(value).matches() || value.length() > MAX_NODE_ID_LENGTH) {
				add("INVALID_LOCAL_ID", pointer, "expected bounded lowercase [a-z0-9_.-]+");
				return false;
			}
			return true;
		}

		private void validateStyleTokenReferences(
			final ThemeTokens tokens,
			final Map<String, StyleDefinition> styles
		) {
			for (Map.Entry<String, StyleDefinition> style : styles.entrySet()) {
				for (Map.Entry<StyleState, Map<String, StyleValue>> state : style.getValue().states().entrySet()) {
					for (Map.Entry<String, StyleValue> property : state.getValue().entrySet()) {
						JsonElement value;
						try {
							value = parseStrictValue(property.getValue().canonicalJson());
						} catch (IOException | JsonParseException error) {
							continue;
						}
						if (
							!value.isJsonPrimitive()
								|| !value.getAsJsonPrimitive().isString()
								|| !value.getAsString().startsWith("@")
						) {
							continue;
						}
						String reference = value.getAsString().substring(1);
						int separator = reference.indexOf('.');
						String group = separator < 0 ? reference : reference.substring(0, separator);
						String name = separator < 0 ? "" : reference.substring(separator + 1);
						boolean present = switch (group) {
							case "colors" -> tokens.colors().containsKey(name);
							case "spacing" -> tokens.spacing().containsKey(name);
							case "fonts" -> tokens.fonts().containsKey(name);
							default -> false;
						};
						if (!present) {
							add(
								"MISSING_REFERENCE",
								"/styles/"
									+ escapePointer(style.getKey())
									+ "/"
									+ state.getKey().name().toLowerCase(Locale.ROOT)
									+ "/"
									+ escapePointer(property.getKey()),
								"theme token " + value.getAsString() + " does not exist"
							);
						}
					}
				}
			}
		}

		private void validateMotionTokenReferences(
			final ThemeTokens tokens,
			final Map<String, MotionDefinition> motions
		) {
			for (Map.Entry<String, MotionDefinition> motion : motions.entrySet()) {
				for (int trackIndex = 0; trackIndex < motion.getValue().tracks().size(); trackIndex++) {
					MotionTrack track = motion.getValue().tracks().get(trackIndex);
					if (track.property() != MotionProperty.COLOR) {
						continue;
					}
					for (int frameIndex = 0; frameIndex < track.keyframes().size(); frameIndex++) {
						MotionKeyframe frame = track.keyframes().get(frameIndex);
						JsonElement value;
						try {
							value = parseStrictValue(frame.value().canonicalJson());
						} catch (IOException | JsonParseException error) {
							continue;
						}
						if (
							!value.isJsonPrimitive()
								|| !value.getAsJsonPrimitive().isString()
								|| !value.getAsString().startsWith("@colors.")
						) {
							continue;
						}
						String name = value.getAsString().substring("@colors.".length());
						if (!tokens.colors().containsKey(name)) {
							add(
								"MISSING_REFERENCE",
								"/motions/"
									+ escapePointer(motion.getKey())
									+ "/tracks/"
									+ trackIndex
									+ "/keyframes/"
									+ frameIndex
									+ "/value",
								"theme token " + value.getAsString() + " does not exist"
							);
						}
					}
				}
			}
		}
	}

	private static final class Reader {
		private final JsonObject object;
		private final String pointer;
		private final List<Issue> issues;
		private final Set<String> consumed = new HashSet<>();
		private final Map<String, JsonObject> consumedObjects = new LinkedHashMap<>();

		private Reader(final JsonObject object, final String pointer, final List<Issue> issues) {
			this.object = object;
			this.pointer = pointer;
			this.issues = issues;
		}

		private JsonElement requiredElement(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				add("MISSING_KEY", "/" + escapePointer(name), "required key is missing");
			}
			return value;
		}

		private JsonElement optionalElement(final String name) {
			return take(name);
		}

		private Integer requiredInteger(final String name) {
			JsonElement value = requiredElement(name);
			if (value == null) {
				return null;
			}
			Integer result = integralNumber(value);
			if (result == null) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an integer");
			}
			return result;
		}

		private int optionalInteger(final String name, final int defaultValue) {
			return optionalInteger(name).orElse(defaultValue);
		}

		private Optional<Integer> optionalInteger(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return Optional.empty();
			}
			Integer result = integralNumber(value);
			if (result == null) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an integer");
				return Optional.empty();
			}
			return Optional.of(result);
		}

		private double optionalDouble(final String name, final double defaultValue) {
			JsonElement value = take(name);
			if (value == null) {
				return defaultValue;
			}
			Double result = finiteNumber(value);
			if (result == null) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected a finite number");
				return defaultValue;
			}
			return result;
		}

		private boolean optionalBoolean(final String name, final boolean defaultValue) {
			return optionalBoolean(name).orElse(defaultValue);
		}

		private Optional<Boolean> optionalBoolean(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return Optional.empty();
			}
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected a boolean");
				return Optional.empty();
			}
			return Optional.of(value.getAsBoolean());
		}

		private String requiredString(final String name) {
			JsonElement value = requiredElement(name);
			return value == null ? null : string(name, value);
		}

		private Optional<String> optionalLocalId(final String name, final int maximumLength) {
			JsonElement value = take(name);
			if (value == null) {
				return Optional.empty();
			}
			String parsed = string(name, value);
			if (parsed == null) {
				return Optional.empty();
			}
			if (!LOCAL_ID.matcher(parsed).matches() || parsed.length() > maximumLength) {
				add(
					"INVALID_LOCAL_ID",
					"/" + escapePointer(name),
					"expected bounded lowercase [a-z0-9_.-]+"
				);
				return Optional.empty();
			}
			return Optional.of(parsed);
		}

		private Identifier requiredIdentifier(final String name) {
			JsonElement value = requiredElement(name);
			if (value == null) {
				return null;
			}
			String parsed = string(name, value);
			Identifier identifier = parsed == null ? null : parseIdentifier(parsed);
			if (parsed != null && identifier == null) {
				add(
					"INVALID_IDENTIFIER",
					"/" + escapePointer(name),
					"expected an explicitly namespaced identifier"
				);
			}
			return identifier;
		}

		private Optional<Identifier> optionalIdentifier(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return Optional.empty();
			}
			String parsed = string(name, value);
			Identifier identifier = parsed == null ? null : parseIdentifier(parsed);
			if (parsed != null && identifier == null) {
				add(
					"INVALID_IDENTIFIER",
					"/" + escapePointer(name),
					"expected an explicitly namespaced identifier"
				);
			}
			return Optional.ofNullable(identifier);
		}

		private JsonArray requiredArray(final String name) {
			JsonElement value = requiredElement(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonArray()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an array");
				return null;
			}
			return value.getAsJsonArray();
		}

		private JsonArray optionalArray(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonArray()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an array");
				return null;
			}
			return value.getAsJsonArray();
		}

		private JsonObject requiredObject(final String name) {
			JsonElement value = requiredElement(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonObject()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an object");
				return null;
			}
			this.consumedObjects.put(name, value.getAsJsonObject());
			return value.getAsJsonObject();
		}

		private JsonObject optionalObject(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonObject()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an object");
				return null;
			}
			this.consumedObjects.put(name, value.getAsJsonObject());
			return value.getAsJsonObject();
		}

		private JsonObject peekConsumedObject(final String name) {
			return this.consumedObjects.get(name);
		}

		private <E extends Enum<E>> E requiredEnum(final String name, final Class<E> type) {
			JsonElement value = requiredElement(name);
			if (value == null) {
				return null;
			}
			String parsed = string(name, value);
			E result = parsed == null ? null : enumValue(type, parsed);
			if (parsed != null && result == null) {
				add("INVALID_ENUM", "/" + escapePointer(name), "unknown " + type.getSimpleName() + " value");
			}
			return result;
		}

		private <E extends Enum<E>> E optionalEnum(
			final String name,
			final Class<E> type,
			final E defaultValue
		) {
			return optionalEnum(name, type).orElse(defaultValue);
		}

		private <E extends Enum<E>> Optional<E> optionalEnum(final String name, final Class<E> type) {
			JsonElement value = take(name);
			if (value == null) {
				return Optional.empty();
			}
			String parsed = string(name, value);
			E result = parsed == null ? null : enumValue(type, parsed);
			if (parsed != null && result == null) {
				add("INVALID_ENUM", "/" + escapePointer(name), "unknown " + type.getSimpleName() + " value");
			}
			return Optional.ofNullable(result);
		}

		private String string(final String name, final JsonElement value) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected a string");
				return null;
			}
			String result = value.getAsString();
			if (result.length() > MAX_SHORT_TEXT) {
				add("RESOURCE_LIMIT", "/" + escapePointer(name), "string is too long");
				return null;
			}
			return result;
		}

		private JsonElement take(final String name) {
			this.consumed.add(name);
			return this.object.get(name);
		}

		private void finish() {
			for (String name : this.object.keySet()) {
				if (!this.consumed.contains(name)) {
					add("UNKNOWN_KEY", "/" + escapePointer(name), "unknown key");
				}
			}
		}

		private void add(final String code, final String suffix, final String message) {
			this.issues.add(new Issue(code, this.pointer + suffix, message));
		}
	}

	private static JsonElement parseStrict(final String json, final List<Issue> issues) {
		try {
			return parseStrictValue(json);
		} catch (IOException | JsonParseException | NumberFormatException error) {
			issues.add(
				new Issue(
					"JSON_SYNTAX",
					"",
					error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
				)
			);
			return null;
		}
	}

	private static JsonElement parseStrictValue(final String json) throws IOException {
		JsonReader reader = new JsonReader(new StringReader(json));
		reader.setStrictness(Strictness.STRICT);
		JsonElement value = readStrictValue(reader, 0);
		if (reader.peek() != JsonToken.END_DOCUMENT) {
			throw new JsonParseException("trailing content after JSON value");
		}
		return value;
	}

	private static JsonElement readStrictValue(final JsonReader reader, final int depth) throws IOException {
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

	private static String canonical(final JsonElement value) {
		if (value.isJsonObject()) {
			StringBuilder result = new StringBuilder("{");
			List<Map.Entry<String, JsonElement>> entries = value.getAsJsonObject()
				.entrySet()
				.stream()
				.sorted(Comparator.comparing(Map.Entry::getKey))
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
		return value.toString();
	}

	private static String sha256(final String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", error);
		}
	}

	private static Identifier parseIdentifier(final String value) {
		int separator = value.indexOf(':');
		return separator <= 0 || separator == value.length() - 1 ? null : Identifier.tryParse(value);
	}

	private static Integer integralNumber(final JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException | NumberFormatException error) {
			return null;
		}
	}

	private static Double finiteNumber(final JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			double result = value.getAsBigDecimal().doubleValue();
			return Double.isFinite(result) ? result : null;
		} catch (NumberFormatException error) {
			return null;
		}
	}

	private static <E extends Enum<E>> E enumValue(final Class<E> type, final String value) {
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	private static String escapePointer(final String value) {
		return value.replace("~", "~0").replace("/", "~1");
	}
}
