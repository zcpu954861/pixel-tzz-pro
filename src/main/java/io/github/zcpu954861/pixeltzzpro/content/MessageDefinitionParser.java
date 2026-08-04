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
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConflictMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueDefaults;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CursorSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FadeSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldReference;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScrambleSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TypewriterSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.VanillaComponentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.*;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

/**
 * Strict, bounded parser for V3B text effects and message cues.
 *
 * <p>A malformed resource is represented by a {@link ParseResult} and never escapes as a runtime
 * exception. When a cue has a readable envelope, the result may retain a partial value alongside
 * issues so the registry can still inspect {@code game} and {@code required}; callers must use
 * {@link ParseResult#valid()} before publishing the definition itself.
 */
public final class MessageDefinitionParser {
	public static final int MAX_ISSUE_DETAILS = 100;
	public static final int MAX_SOURCE_CHARACTERS = 262_144;
	public static final int MAX_CANONICAL_COMPONENT_BYTES = 65_536;
	public static final int MAX_CANONICAL_DOCUMENT_BYTES = 262_144;
	public static final int MAX_JSON_DEPTH = 64;
	public static final int MAX_PARAMETERS = 64;
	public static final int MAX_FIELDS = 64;
	public static final int MAX_NODES = 128;
	public static final int MAX_TEMPLATE_PARTS = 128;
	public static final int MAX_VARIANTS_PER_NODE = 16;
	public static final int MAX_TOTAL_VARIANTS = 128;
	public static final int MAX_REPEAT_COUNT = 32;
	public static final int MAX_EXPANDED_NODES = 256;
	public static final int MAX_LOCALES = 32;
	public static final int MAX_ASSETS = 64;
	public static final int MAX_CONTROL_GROUPS = 32;
	public static final int MAX_LOCAL_ID_LENGTH = 64;
	public static final int MAX_BINDING_PATH_LENGTH = 256;
	public static final int MAX_CURSOR_CODE_POINTS = 8;
	public static final int MAX_SCRAMBLE_CODE_POINTS = 128;
	public static final int MAX_RESTORE_CHARACTERS = 256;
	public static final long MIN_CHARACTER_INTERVAL_NANOS = 1_000_000L;
	public static final int DEFAULT_MAX_CHARACTER_SOUND_EVENTS = 256;
	public static final int MAX_CHARACTER_SOUND_EVENTS = 1_024;
	public static final long MAX_TIME_NANOS = Duration.ofDays(7L).toNanos();
	public static final int MIN_PRIORITY = -10_000;
	public static final int MAX_PRIORITY = 10_000;

	private static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
	private static final Pattern LOCALE = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,8}");
	private static final Pattern SCORE_OBJECTIVE = Pattern.compile("[A-Za-z0-9_.+-]{1,16}");
	private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_./{}\\[\\]-]{1,256}");
	private static final Pattern TIME = Pattern.compile(
		"(0|[1-9][0-9]*)(?:\\.([0-9]{1,3}))?(t|ms|s)"
	);
	private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Set<String> NAMED_COLORS = Set.of(
		"black",
		"dark_blue",
		"dark_green",
		"dark_aqua",
		"dark_red",
		"dark_purple",
		"gold",
		"gray",
		"dark_gray",
		"blue",
		"green",
		"aqua",
		"red",
		"light_purple",
		"yellow",
		"white"
	);
	private static final Set<String> DYNAMIC_COMPONENT_KEYS = Set.of("score", "selector", "nbt");
	private static final TimeSpan ZERO_TIME = new TimeSpan(0L);
	private static final TimeSpan DEFAULT_BLINK_PERIOD = new TimeSpan(500_000_000L);
	private static final String DEFAULT_SCRAMBLE_CHARACTERS =
		"█▓▒░ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private MessageDefinitionParser() {
	}

	public static ParseResult<TextEffectDefinition> parseTextEffect(
		final Identifier id,
		final String json
	) {
		IssueCollector issues = new IssueCollector();
		JsonObject root = parseRoot(json, issues);
		if (id == null) {
			issues.add("", "INVALID_IDENTIFIER", "definition id is required");
		}
		if (root == null || id == null) {
			return issues.result(Optional.empty());
		}

		TextEffectDefinition value = null;
		try {
			ObjectReader reader = new ObjectReader(root, "", issues);
			checkFormat(reader, issues);
			JsonObject typewriterObject = reader.optionalObject("typewriter");
			JsonObject fadeObject = reader.optionalObject("fade");
			JsonObject scrambleObject = reader.optionalObject("scramble");
			JsonObject gradientObject = reader.optionalObject("gradient");
			JsonObject motionObject = reader.optionalObject("motion");
			JsonObject pulseObject = reader.optionalObject("pulse");
			JsonObject soundObject = reader.optionalObject("character_sound");
			int maxCharacterSoundEvents = reader.optionalInteger(
				"max_character_sound_events",
				DEFAULT_MAX_CHARACTER_SOUND_EVENTS
			);
			reader.finish();

			Optional<TypewriterSpec> typewriter = Optional.ofNullable(
				parseTypewriter(typewriterObject, "/typewriter", issues)
			);
			Optional<FadeSpec> fade = Optional.ofNullable(
				parseFade(fadeObject, "/fade", issues)
			);
			Optional<ScrambleSpec> scramble = Optional.ofNullable(
				parseScramble(scrambleObject, "/scramble", issues)
			);
			Optional<GradientSpec> gradient = Optional.ofNullable(
				parseGradient(gradientObject, "/gradient", issues)
			);
			Optional<MotionSpec> motion = Optional.ofNullable(
				parseMotion(motionObject, "/motion", issues)
			);
			Optional<PulseSpec> pulse = Optional.ofNullable(
				parsePulse(pulseObject, "/pulse", issues)
			);
			Optional<CharacterSoundSpec> characterSound = Optional.ofNullable(
				parseCharacterSound(soundObject, "/character_sound", issues)
			);
			if (
				typewriterObject == null
					&& fadeObject == null
					&& scrambleObject == null
					&& gradientObject == null
					&& motionObject == null
					&& pulseObject == null
					&& soundObject == null
			) {
				issues.add("", "EMPTY_DEFINITION", "text effect must declare at least one effect");
			}
			if (
				maxCharacterSoundEvents < 1
					|| maxCharacterSoundEvents > MAX_CHARACTER_SOUND_EVENTS
			) {
				issues.add(
					"/max_character_sound_events",
					"OUT_OF_RANGE",
					"max_character_sound_events must be between 1 and "
						+ MAX_CHARACTER_SOUND_EVENTS
				);
				maxCharacterSoundEvents = Math.max(
					1,
					Math.min(maxCharacterSoundEvents, MAX_CHARACTER_SOUND_EVENTS)
				);
			}
			String canonical = canonical(root);
			checkDocumentSize(canonical, issues);
			value = new TextEffectDefinition(
				id,
				typewriter,
				fade,
				scramble,
				gradient,
				motion,
				pulse,
				characterSound,
				maxCharacterSoundEvents,
				canonical
			);
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(Optional.ofNullable(value));
	}

	public static ParseResult<MessageCueDefinition> parseMessageCue(
		final Identifier id,
		final String json
	) {
		IssueCollector issues = new IssueCollector();
		JsonObject root = parseRoot(json, issues);
		if (id == null) {
			issues.add("", "INVALID_IDENTIFIER", "definition id is required");
		}
		if (root == null || id == null) {
			return issues.result(Optional.empty());
		}

		MessageCueDefinition value = null;
		try {
			ObjectReader reader = new ObjectReader(root, "", issues);
			checkFormat(reader, issues);
			Identifier game = reader.optionalIdentifier("game");
			boolean required = reader.optionalBoolean("required", false);
			JsonObject defaultsObject = reader.optionalObject("defaults");
			JsonObject policiesObject = reader.optionalObject("policies");
			JsonObject parametersObject = reader.optionalObject("parameters");
			JsonObject fieldsObject = reader.optionalObject("fields");
			JsonArray nodesArray = reader.requiredArray("nodes");
			JsonObject historyObject = reader.optionalObject("history");
			JsonObject staticFallbackObject = reader.optionalObject("static_fallback");
			JsonArray assetsArray = reader.optionalArray("assets");
			JsonObject softLimitsObject = reader.optionalObject("soft_limits");
			reader.finish();

			if (defaultsObject != null && policiesObject != null) {
				issues.add(
					"/policies",
					"INVALID_ONE_OF",
					"declare policies or legacy defaults, not both"
				);
			}
			CuePolicies policies = policiesObject == null
				? policiesFromDefaults(parseDefaults(defaultsObject, "/defaults", issues))
				: parsePolicies(policiesObject, "/policies", issues);
			Map<String, ParameterDefinition> parameters = parseParameters(
				parametersObject,
				"/parameters",
				issues
			);
			Map<String, DynamicFieldDefinition> fields = parseFields(
				fieldsObject,
				"/fields",
				policies.capture(),
				issues
			);
			List<CueNode> nodes = parseNodes(
				nodesArray,
				policies.capture(),
				parameters,
				fields,
				issues
			);
			validateFieldSources(fields, parameters, "/fields", issues);
			SoftLimits softLimits = parseSoftLimits(
				softLimitsObject,
				"/soft_limits",
				issues
			);
			HistorySpec history = parseHistory(
				historyObject,
				"/history",
				parameters,
				fields,
				policies.audience().audience(),
				issues
			);
			validateCueReferences(
				parameters,
				fields,
				nodes,
				Optional.ofNullable(history),
				policies,
				softLimits,
				issues
			);
			StaticFallbackSpec staticFallback = parseStaticFallback(
				staticFallbackObject,
				"/static_fallback",
				issues
			);
			List<AssetSpec> assets = parseAssets(assetsArray, "/assets", issues);
			if (nodes.isEmpty()) {
				issues.add("/nodes", "OUT_OF_RANGE", "message cue must contain at least one node");
			}
			String canonical = canonical(root);
			checkDocumentSize(canonical, issues);
			if (required && game == null) {
				issues.add(
					"/game",
					"MISSING_REQUIRED",
					"required message cues must declare their owning game"
				);
			}
			value = new MessageCueDefinition(
				id,
				Optional.ofNullable(game),
				required,
				policies,
				parameters,
				fields,
				nodes,
				Optional.ofNullable(history),
				Optional.ofNullable(staticFallback),
				assets,
				softLimits,
				canonical
			);
		} catch (RuntimeException error) {
			issues.add("", "PARSE_FAILED", safeMessage(error));
		}
		return issues.result(Optional.ofNullable(value));
	}

	private static void checkFormat(final ObjectReader reader, final IssueCollector issues) {
		Integer format = reader.requiredInteger("format_version");
		if (
			format != null
				&& format.intValue() != MessageDefinitions.CURRENT_FORMAT_VERSION
		) {
			issues.add(
				reader.pointer("format_version"),
				"UNSUPPORTED_FORMAT",
				"expected "
					+ MessageDefinitions.CURRENT_FORMAT_VERSION
					+ " but found "
					+ format
			);
		}
	}

	private static TypewriterSpec parseTypewriter(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TimeSpan interval = parseRequiredTime(reader, "character_interval", false, issues);
		JsonObject cursorObject = reader.optionalObject("cursor");
		String freshColor = reader.optionalString("fresh_color");
		int restoreAfter = reader.optionalInteger("restore_after_characters", 0);
		RestoreMode restoreMode = parseEnum(
			reader.optionalString("restore_mode", "instant"),
			RestoreMode.class,
			reader.pointer("restore_mode"),
			issues
		);
		reader.finish();
		validateCharacterInterval(
			interval,
			reader.pointer("character_interval"),
			issues
		);

		if (freshColor != null) {
			validateColor(freshColor, reader.pointer("fresh_color"), issues);
		}
		if (restoreAfter < 0 || restoreAfter > MAX_RESTORE_CHARACTERS) {
			issues.add(
				reader.pointer("restore_after_characters"),
				"OUT_OF_RANGE",
				"restore_after_characters must be between 0 and " + MAX_RESTORE_CHARACTERS
			);
			restoreAfter = Math.max(0, Math.min(restoreAfter, MAX_RESTORE_CHARACTERS));
		}
		CursorSpec cursor = parseCursor(cursorObject, pointer + "/cursor", issues);
		if (interval == null || restoreMode == null) {
			return null;
		}
		return new TypewriterSpec(
			interval,
			cursor,
			Optional.ofNullable(freshColor),
			restoreAfter,
			restoreMode
		);
	}

	private static CursorSpec parseCursor(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return CursorSpec.disabled();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		boolean enabled = reader.optionalBoolean("enabled", true);
		String glyph = reader.optionalString("glyph", "▌");
		String color = reader.optionalString("color", "#FFFFFF");
		boolean blink = reader.optionalBoolean("blink", false);
		TimeSpan blinkPeriod = parseOptionalTime(reader, "blink_period", false, issues);
		reader.finish();

		if (glyph == null || glyph.isBlank() || glyph.codePointCount(0, glyph.length()) > MAX_CURSOR_CODE_POINTS) {
			issues.add(
				reader.pointer("glyph"),
				"OUT_OF_RANGE",
				"cursor glyph must contain between 1 and " + MAX_CURSOR_CODE_POINTS + " code points"
			);
			glyph = "▌";
		}
		if (color == null) {
			color = "#FFFFFF";
		} else {
			validateColor(color, reader.pointer("color"), issues);
		}
		if (blink && blinkPeriod == null) {
			blinkPeriod = DEFAULT_BLINK_PERIOD;
		}
		if (!blink && blinkPeriod != null) {
			issues.add(
				reader.pointer("blink_period"),
				"CONTRADICTORY_VALUE",
				"blink_period requires blink to be true"
			);
		}
		return new CursorSpec(
			enabled,
			glyph,
			color,
			blink,
			Optional.ofNullable(blinkPeriod)
		);
	}

	private static FadeSpec parseFade(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TimeSpan enter = defaultTime(parseOptionalTime(reader, "enter", true, issues));
		TimeSpan hold = defaultTime(parseOptionalTime(reader, "hold", true, issues));
		TimeSpan exit = defaultTime(parseOptionalTime(reader, "exit", true, issues));
		reader.finish();
		if (enter.nanoseconds() == 0L && hold.nanoseconds() == 0L && exit.nanoseconds() == 0L) {
			issues.add(pointer, "EMPTY_DEFINITION", "fade must have at least one non-zero duration");
		}
		return new FadeSpec(enter, hold, exit);
	}

	private static ScrambleSpec parseScramble(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TimeSpan interval = parseRequiredTime(reader, "character_interval", false, issues);
		String characters = reader.optionalString("characters", DEFAULT_SCRAMBLE_CHARACTERS);
		reader.finish();
		validateCharacterInterval(
			interval,
			reader.pointer("character_interval"),
			issues
		);
		if (
			characters == null
				|| characters.isBlank()
				|| characters.codePointCount(0, characters.length()) > MAX_SCRAMBLE_CODE_POINTS
		) {
			issues.add(
				reader.pointer("characters"),
				"OUT_OF_RANGE",
				"scramble characters must contain between 1 and "
					+ MAX_SCRAMBLE_CODE_POINTS
					+ " code points"
			);
			characters = DEFAULT_SCRAMBLE_CHARACTERS;
		}
		return interval == null ? null : new ScrambleSpec(interval, characters);
	}

	private static GradientSpec parseGradient(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String fromColor = reader.requiredString("from_color");
		String toColor = reader.requiredString("to_color");
		TimeSpan duration = parseRequiredTime(reader, "duration", false, issues);
		reader.finish();
		if (fromColor != null) {
			validateColor(fromColor, reader.pointer("from_color"), issues);
		}
		if (toColor != null) {
			validateColor(toColor, reader.pointer("to_color"), issues);
		}
		return fromColor == null || toColor == null || duration == null
			? null
			: new GradientSpec(fromColor, toColor, duration);
	}

	private static MotionSpec parseMotion(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		int slideX = boundedInteger(
			reader.optionalInteger("slide_x", 0),
			-64,
			64,
			reader.pointer("slide_x"),
			issues
		);
		int slideY = boundedInteger(
			reader.optionalInteger("slide_y", 0),
			-64,
			64,
			reader.pointer("slide_y"),
			issues
		);
		float startScale = boundedFloat(
			reader.optionalDouble("start_scale", 1.0D),
			0.75D,
			1.25D,
			reader.pointer("start_scale"),
			issues
		);
		TimeSpan duration = parseRequiredTime(reader, "duration", false, issues);
		reader.finish();
		if (slideX == 0 && slideY == 0 && startScale == 1.0F) {
			issues.add(
				pointer,
				"EMPTY_DEFINITION",
				"motion must declare a non-zero slide or a start_scale other than 1"
			);
		}
		return duration == null ? null : new MotionSpec(slideX, slideY, startScale, duration);
	}

	private static PulseSpec parsePulse(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		float scale = boundedFloat(
			reader.optionalDouble("scale", 1.08D),
			1.0D,
			1.5D,
			reader.pointer("scale"),
			issues
		);
		TimeSpan duration = parseRequiredTime(reader, "duration", false, issues);
		reader.finish();
		if (scale == 1.0F) {
			issues.add(reader.pointer("scale"), "EMPTY_DEFINITION", "pulse scale must exceed 1");
		}
		return duration == null ? null : new PulseSpec(scale, duration);
	}

	private static CharacterSoundSpec parseCharacterSound(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Identifier sound = reader.requiredIdentifier("sound");
		SoundCategory category = parseEnum(
			reader.optionalString("category", "ui"),
			SoundCategory.class,
			reader.pointer("category"),
			issues
		);
		int everyCharacters = reader.optionalInteger("every_characters", 1);
		boolean skipWhitespace = reader.optionalBoolean("skip_whitespace", true);
		boolean skipPunctuation = reader.optionalBoolean("skip_punctuation", false);
		boolean skipNewline = reader.optionalBoolean("skip_newline", true);
		double volume = reader.optionalDouble("volume", 1.0D);
		double pitch = reader.optionalDouble("pitch", 1.0D);
		double pitchVariation = reader.optionalDouble("pitch_variation", 0.0D);
		SoundAttachment attachment = parseEnum(
			reader.optionalString("attachment", "player"),
			SoundAttachment.class,
			reader.pointer("attachment"),
			issues
		);
		MissingAssetMode missing = parseEnum(
			reader.optionalString("missing", "silent"),
			MissingAssetMode.class,
			reader.pointer("missing"),
			issues
		);
		Identifier fallback = reader.optionalIdentifier("fallback");
		reader.finish();
		if (everyCharacters < 1 || everyCharacters > 64) {
			issues.add(
				reader.pointer("every_characters"),
				"OUT_OF_RANGE",
				"every_characters must be between 1 and 64"
			);
			everyCharacters = Math.max(1, Math.min(everyCharacters, 64));
		}
		float checkedVolume = boundedFloat(
			volume,
			0.0D,
			4.0D,
			reader.pointer("volume"),
			issues
		);
		float checkedPitch = boundedFloat(
			pitch,
			0.0D,
			2.0D,
			reader.pointer("pitch"),
			issues
		);
		float checkedPitchVariation = boundedFloat(
			pitchVariation,
			0.0D,
			2.0D,
			reader.pointer("pitch_variation"),
			issues
		);
		validateMissingFallback(
			missing,
			fallback,
			reader.pointer("missing"),
			reader.pointer("fallback"),
			issues
		);
		if (sound == null || category == null || attachment == null || missing == null) {
			return null;
		}
		return new CharacterSoundSpec(
			sound,
			category,
			everyCharacters,
			skipWhitespace,
			skipPunctuation,
			skipNewline,
			checkedVolume,
			checkedPitch,
			checkedPitchVariation,
			attachment,
			missing,
			Optional.ofNullable(fallback)
		);
	}

	private static CueDefaults parseDefaults(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return CueDefaults.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject audienceObject = reader.optionalObject("audience");
		ClockMode clock = parseEnum(
			reader.optionalString("clock"),
			ClockMode.class,
			reader.pointer("clock"),
			issues
		);
		SynchronizationMode synchronization = parseEnum(
			reader.optionalString("synchronization"),
			SynchronizationMode.class,
			reader.pointer("synchronization"),
			issues
		);
		ConflictMode conflict = parseEnum(
			reader.optionalString("conflict"),
			ConflictMode.class,
			reader.pointer("conflict"),
			issues
		);
		DuplicateMode duplicate = parseEnum(
			reader.optionalString("duplicate", "ignore_while_active"),
			DuplicateMode.class,
			reader.pointer("duplicate"),
			issues
		);
		CaptureMode capture = parseEnum(
			reader.optionalString("capture", "on_display"),
			CaptureMode.class,
			reader.pointer("capture"),
			issues
		);
		int priority = reader.optionalInteger("priority", 0);
		reader.finish();
		priority = validatePriority(priority, reader.pointer("priority"), issues);
		return new CueDefaults(
			parseAudience(audienceObject, pointer + "/audience", issues),
			Optional.ofNullable(clock),
			Optional.ofNullable(synchronization),
			Optional.ofNullable(conflict),
			duplicate == null ? DuplicateMode.IGNORE_WHILE_ACTIVE : duplicate,
			capture == null ? CaptureMode.ON_DISPLAY : capture,
			priority
		);
	}

	private static CuePolicies policiesFromDefaults(final CueDefaults defaults) {
		return new CuePolicies(
			new TimingPolicy(
				defaults.clock(),
				defaults.synchronization(),
				Optional.empty()
			),
			new AudiencePolicy(
				defaults.audience(),
				AudienceEvolution.SNAPSHOT,
				false,
				AudienceLossMode.FINALIZE
			),
			ContextPolicy.unrestricted(),
			new ConcurrencyPolicy(
				defaults.duplicate(),
				Optional.empty(),
				true,
				Set.of(),
				Optional.empty(),
				ChatInterruptMode.FINALIZE,
				defaults.priority(),
				Set.of()
			),
			DeliveryPolicy.standard(),
			LifecyclePolicy.standard(),
			AccessibilityPolicy.standard(),
			Set.of(),
			defaults.capture(),
			defaults.conflict()
		);
	}

	private static CuePolicies parsePolicies(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return CuePolicies.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject timingObject = reader.optionalObject("timing");
		JsonObject audienceObject = reader.optionalObject("audience");
		JsonObject contextObject = reader.optionalObject("context");
		JsonObject concurrencyObject = reader.optionalObject("concurrency");
		JsonObject deliveryObject = reader.optionalObject("delivery");
		JsonObject lifecycleObject = reader.optionalObject("lifecycle");
		JsonObject accessibilityObject = reader.optionalObject("accessibility");
		Set<String> controlGroups = reader.optionalLocalIdSet(
			"control_groups",
			MAX_CONTROL_GROUPS
		);
		CaptureMode capture = parseEnum(
			reader.optionalString("capture", "on_display"),
			CaptureMode.class,
			reader.pointer("capture"),
			issues
		);
		ConflictMode conflict = parseEnum(
			reader.optionalString("default_conflict"),
			ConflictMode.class,
			reader.pointer("default_conflict"),
			issues
		);
		reader.finish();
		return new CuePolicies(
			parseTimingPolicy(timingObject, pointer + "/timing", issues),
			parseAudiencePolicy(audienceObject, pointer + "/audience", issues),
			parseContextPolicy(contextObject, pointer + "/context", issues),
			parseConcurrencyPolicy(concurrencyObject, pointer + "/concurrency", issues),
			parseDeliveryPolicy(deliveryObject, pointer + "/delivery", issues),
			parseLifecyclePolicy(lifecycleObject, pointer + "/lifecycle", issues),
			parseAccessibilityPolicy(
				accessibilityObject,
				pointer + "/accessibility",
				issues
			),
			controlGroups,
			capture == null ? CaptureMode.ON_DISPLAY : capture,
			Optional.ofNullable(conflict)
		);
	}

	private static TimingPolicy parseTimingPolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return TimingPolicy.runtimeDefaults();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		ClockMode clock = parseEnum(
			reader.optionalString("clock"),
			ClockMode.class,
			reader.pointer("clock"),
			issues
		);
		SynchronizationMode synchronization = parseEnum(
			reader.optionalString("synchronization"),
			SynchronizationMode.class,
			reader.pointer("synchronization"),
			issues
		);
		TimeSpan synchronizedWait = parseOptionalTime(
			reader,
			"synchronized_wait",
			false,
			issues
		);
		LateArrivalMode lateArrival = parseEnum(
			reader.optionalString("late_arrival", "seek"),
			LateArrivalMode.class,
			reader.pointer("late_arrival"),
			issues
		);
		SynchronizationTimeoutMode synchronizationTimeout = parseEnum(
			reader.optionalString("synchronization_timeout", "start_available"),
			SynchronizationTimeoutMode.class,
			reader.pointer("synchronization_timeout"),
			issues
		);
		reader.finish();
		if (
			synchronizedWait != null
				&& synchronization != SynchronizationMode.SYNCHRONIZED
		) {
			issues.add(
				reader.pointer("synchronized_wait"),
				"CONTRADICTORY_VALUE",
				"synchronized_wait requires synchronized synchronization"
			);
		}
		return new TimingPolicy(
			Optional.ofNullable(clock),
			Optional.ofNullable(synchronization),
			Optional.ofNullable(synchronizedWait),
			lateArrival == null ? LateArrivalMode.SEEK : lateArrival,
			synchronizationTimeout == null
				? SynchronizationTimeoutMode.START_AVAILABLE
				: synchronizationTimeout
		);
	}

	private static AudiencePolicy parseAudiencePolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return AudiencePolicy.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject targetObject = reader.optionalObject("target");
		AudienceEvolution evolution = parseEnum(
			reader.optionalString("evolution", "snapshot"),
			AudienceEvolution.class,
			reader.pointer("evolution"),
			issues
		);
		boolean sensitive = reader.optionalBoolean("sensitive", false);
		AudienceLossMode onLoss = parseEnum(
			reader.optionalString("on_loss", "finalize"),
			AudienceLossMode.class,
			reader.pointer("on_loss"),
			issues
		);
		reader.finish();
		if (sensitive && evolution != AudienceEvolution.LIVE_STRICT) {
			issues.add(
				reader.pointer("evolution"),
				"INSECURE_POLICY",
				"sensitive audiences require live_strict evolution"
			);
		}
		return new AudiencePolicy(
			parseAudience(targetObject, pointer + "/target", issues),
			evolution == null ? AudienceEvolution.SNAPSHOT : evolution,
			sensitive,
			onLoss == null ? AudienceLossMode.FINALIZE : onLoss
		);
	}

	private static ContextPolicy parseContextPolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return ContextPolicy.unrestricted();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Set<Identifier> games = reader.optionalIdentifierSet("games");
		Set<Identifier> phases = reader.optionalIdentifierSet("phases");
		Set<Identifier> tasks = reader.optionalIdentifierSet("tasks");
		ContextRecheck recheck = parseEnum(
			reader.optionalString("recheck", "before_start"),
			ContextRecheck.class,
			reader.pointer("recheck"),
			issues
		);
		reader.finish();
		return new ContextPolicy(
			games,
			phases,
			tasks,
			recheck == null ? ContextRecheck.BEFORE_START : recheck
		);
	}

	private static ConcurrencyPolicy parseConcurrencyPolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return ConcurrencyPolicy.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		DuplicateMode duplicate = parseEnum(
			reader.optionalString("duplicate", "ignore_while_active"),
			DuplicateMode.class,
			reader.pointer("duplicate"),
			issues
		);
		TimeSpan cooldown = parseOptionalTime(reader, "cooldown", true, issues);
		boolean dedupeTargets = reader.optionalBoolean("dedupe_targets", true);
		Set<String> dedupeParameters = reader.optionalLocalIdSet(
			"dedupe_parameters",
			MAX_PARAMETERS
		);
		String refreshKey = reader.optionalString("refresh_key");
		if (refreshKey != null) {
			validateLocalId(refreshKey, reader.pointer("refresh_key"), issues);
		}
		ChatInterruptMode chatInterrupt = parseEnum(
			reader.optionalString("chat_interrupt", "finalize"),
			ChatInterruptMode.class,
			reader.pointer("chat_interrupt"),
			issues
		);
		int priority = validatePriority(
			reader.optionalInteger("priority", 0),
			reader.pointer("priority"),
			issues
		);
		Set<String> groups = reader.optionalLocalIdSet("groups", MAX_CONTROL_GROUPS);
		reader.finish();
		return new ConcurrencyPolicy(
			duplicate == null ? DuplicateMode.IGNORE_WHILE_ACTIVE : duplicate,
			Optional.ofNullable(cooldown),
			dedupeTargets,
			dedupeParameters,
			Optional.ofNullable(refreshKey),
			chatInterrupt == null ? ChatInterruptMode.FINALIZE : chatInterrupt,
			priority,
			groups
		);
	}

	private static DeliveryPolicy parseDeliveryPolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return DeliveryPolicy.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		EmptyAudienceMode emptyAudience = parseEnum(
			reader.optionalString("empty_audience", "fail"),
			EmptyAudienceMode.class,
			reader.pointer("empty_audience"),
			issues
		);
		OfflineTargetMode offlineTargets = parseEnum(
			reader.optionalString("offline_targets", "skip"),
			OfflineTargetMode.class,
			reader.pointer("offline_targets"),
			issues
		);
		TimeSpan offlineTtl = parseOptionalTime(reader, "offline_ttl", false, issues);
		reader.finish();
		if (
			offlineTtl != null
				&& offlineTargets != OfflineTargetMode.QUEUE
				&& offlineTargets != OfflineTargetMode.FINAL_ONLY
		) {
			issues.add(
				reader.pointer("offline_ttl"),
				"CONTRADICTORY_VALUE",
				"offline_ttl requires queue or final_only offline targets"
			);
		}
		return new DeliveryPolicy(
			emptyAudience == null ? EmptyAudienceMode.FAIL : emptyAudience,
			offlineTargets == null ? OfflineTargetMode.SKIP : offlineTargets,
			Optional.ofNullable(offlineTtl)
		);
	}

	private static LifecyclePolicy parseLifecyclePolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		LifecyclePolicy defaults = LifecyclePolicy.standard();
		if (object == null) {
			return defaults;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject screenStartObject = reader.optionalObject("screen_start");
		JsonObject obscuredObject = reader.optionalObject("obscured");
		TimeSpan screenWaitTimeout = parseOptionalTime(
			reader,
			"screen_wait_timeout",
			false,
			issues
		);
		ScreenTimeoutAction timeoutAction = parseEnum(
			reader.optionalString("screen_timeout_action", "finalize"),
			ScreenTimeoutAction.class,
			reader.pointer("screen_timeout_action"),
			issues
		);
		TimeSpan maxPause = parseOptionalTime(reader, "max_pause", false, issues);
		ReloadMode reload = parseEnum(
			reader.optionalString("reload", "finish_snapshot"),
			ReloadMode.class,
			reader.pointer("reload"),
			issues
		);
		RestartMode restart = parseEnum(
			reader.optionalString("restart", "transient"),
			RestartMode.class,
			reader.pointer("restart"),
			issues
		);
		ExternalConflictMode externalConflict = parseEnum(
			reader.optionalString("external_conflict", "yield"),
			ExternalConflictMode.class,
			reader.pointer("external_conflict"),
			issues
		);
		ReconnectMode reconnect = parseEnum(
			reader.optionalString("reconnect", "continue"),
			ReconnectMode.class,
			reader.pointer("reconnect"),
			issues
		);
		LateJoinMode lateJoin = parseEnum(
			reader.optionalString("late_join", "drop"),
			LateJoinMode.class,
			reader.pointer("late_join"),
			issues
		);
		ResourceReloadMode resourceReload = parseEnum(
			reader.optionalString("resource_reload", "finish"),
			ResourceReloadMode.class,
			reader.pointer("resource_reload"),
			issues
		);
		RespawnMode respawn = parseEnum(
			reader.optionalString("respawn", "continue"),
			RespawnMode.class,
			reader.pointer("respawn"),
			issues
		);
		AttachmentMode attachment = parseEnum(
			reader.optionalString("attachment", "player"),
			AttachmentMode.class,
			reader.pointer("attachment"),
			issues
		);
		DimensionExitMode dimensionExit = parseEnum(
			reader.optionalString("dimension_exit", "continue"),
			DimensionExitMode.class,
			reader.pointer("dimension_exit"),
			issues
		);
		reader.finish();
		return new LifecyclePolicy(
			parseChannelMap(
				screenStartObject,
				pointer + "/screen_start",
				ScreenStartMode.class,
				defaults.screenStart(),
				issues
			),
			parseChannelMap(
				obscuredObject,
				pointer + "/obscured",
				ObscuredMode.class,
				defaults.obscured(),
				issues
			),
			Optional.ofNullable(screenWaitTimeout),
			timeoutAction == null ? ScreenTimeoutAction.FINALIZE : timeoutAction,
			Optional.ofNullable(maxPause),
			reload == null ? ReloadMode.FINISH_SNAPSHOT : reload,
			restart == null ? RestartMode.TRANSIENT : restart,
			externalConflict == null ? ExternalConflictMode.YIELD : externalConflict,
			reconnect == null ? ReconnectMode.CONTINUE : reconnect,
			lateJoin == null ? LateJoinMode.DROP : lateJoin,
			resourceReload == null ? ResourceReloadMode.FINISH : resourceReload,
			respawn == null ? RespawnMode.CONTINUE : respawn,
			attachment == null ? AttachmentMode.PLAYER : attachment,
			dimensionExit == null ? DimensionExitMode.CONTINUE : dimensionExit
		);
	}

	private static AccessibilityPolicy parseAccessibilityPolicy(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return AccessibilityPolicy.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		boolean allowManualComplete = reader.optionalBoolean("allow_manual_complete", false);
		ReducedMotionMode reducedMotion = parseEnum(
			reader.optionalString("reduced_motion", "simplified"),
			ReducedMotionMode.class,
			reader.pointer("reduced_motion"),
			issues
		);
		reader.finish();
		return new AccessibilityPolicy(
			allowManualComplete,
			reducedMotion == null ? ReducedMotionMode.SIMPLIFIED : reducedMotion
		);
	}

	private static <T extends Enum<T>> Map<TextChannel, T> parseChannelMap(
		final JsonObject object,
		final String pointer,
		final Class<T> type,
		final Map<TextChannel, T> defaults,
		final IssueCollector issues
	) {
		Map<TextChannel, T> result = new LinkedHashMap<>(defaults);
		if (object == null) {
			return Map.copyOf(result);
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			TextChannel channel = parseEnum(
				entry.getKey(),
				TextChannel.class,
				pointer + "/" + escapePointer(entry.getKey()),
				issues
			);
			if (!isString(entry.getValue())) {
				issues.add(
					pointer + "/" + escapePointer(entry.getKey()),
					"TYPE_MISMATCH",
					"channel policy value must be a string"
				);
				continue;
			}
			T value = parseEnum(
				entry.getValue().getAsString(),
				type,
				pointer + "/" + escapePointer(entry.getKey()),
				issues
			);
			if (channel != null && value != null) {
				result.put(channel, value);
			}
		}
		return Map.copyOf(result);
	}

	private static AudienceSpec parseAudience(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return AudienceSpec.allOnline();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonArray anyOf = reader.optionalArray("any_of");
		boolean hasDirectSelector = object.keySet().stream().anyMatch(
			key -> !key.equals("any_of")
		);
		AudienceSelector direct = parseAudienceSelector(reader, pointer, issues);
		reader.finish();
		if (anyOf == null) {
			return new AudienceSpec(List.of(direct));
		}
		if (hasDirectSelector) {
			issues.add(
				pointer,
				"INVALID_ONE_OF",
				"audience must use either direct selector keys or any_of, not both"
			);
		}
		if (anyOf.isEmpty()) {
			issues.add(pointer + "/any_of", "OUT_OF_RANGE", "any_of must not be empty");
			return AudienceSpec.allOnline();
		}
		if (anyOf.size() > 16) {
			issues.add(
				pointer + "/any_of",
				"RESOURCE_LIMIT",
				"audience any_of exceeds 16 selectors"
			);
		}
		List<AudienceSelector> selectors = new ArrayList<>();
		int limit = Math.min(anyOf.size(), 16);
		for (int index = 0; index < limit; index++) {
			JsonElement element = anyOf.get(index);
			String selectorPointer = pointer + "/any_of/" + index;
			if (!element.isJsonObject()) {
				issues.add(
					selectorPointer,
					"TYPE_MISMATCH",
					"audience selector must be an object"
				);
				continue;
			}
			ObjectReader selectorReader = new ObjectReader(
				element.getAsJsonObject(),
				selectorPointer,
				issues
			);
			selectors.add(parseAudienceSelector(selectorReader, selectorPointer, issues));
			selectorReader.finish();
		}
		return selectors.isEmpty() ? AudienceSpec.allOnline() : new AudienceSpec(selectors);
	}

	private static AudienceSelector parseAudienceSelector(
		final ObjectReader reader,
		final String pointer,
		final IssueCollector issues
	) {
		AudienceSource source = parseEnum(
			reader.optionalString("source", "all"),
			AudienceSource.class,
			reader.pointer("source"),
			issues
		);
		Set<Identifier> roles = reader.optionalIdentifierSet("roles");
		Set<Identifier> teams = reader.optionalIdentifierSet("teams");
		Set<Identifier> lifeStates = reader.optionalIdentifierSet("life_states");
		Set<Identifier> roleTags = reader.optionalIdentifierSet("role_tags");
		Set<Identifier> teamTags = reader.optionalIdentifierSet("team_tags");
		Set<Identifier> lifeStateTags = reader.optionalIdentifierSet("life_state_tags");
		boolean excludeHost = reader.optionalBoolean("exclude_host", false);
		boolean onlineOnly = reader.optionalBoolean("online_only", true);
		return new AudienceSelector(
			source == null ? AudienceSource.ALL : source,
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

	private static Map<String, ParameterDefinition> parseParameters(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return Map.of();
		}
		if (object.size() > MAX_PARAMETERS) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"parameter count exceeds " + MAX_PARAMETERS
			);
		}
		Map<String, ParameterDefinition> result = new LinkedHashMap<>();
		int retained = 0;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String name = entry.getKey();
			String entryPointer = pointer + "/" + escapePointer(name);
			validateLocalId(name, entryPointer, issues);
			if (retained >= MAX_PARAMETERS) {
				continue;
			}
			if (!entry.getValue().isJsonObject()) {
				issues.add(entryPointer, "TYPE_MISMATCH", "parameter definition must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(
				entry.getValue().getAsJsonObject(),
				entryPointer,
				issues
			);
			ParameterType type = parseEnum(
				reader.requiredString("type"),
				ParameterType.class,
				reader.pointer("type"),
				issues
			);
			boolean required = reader.optionalBoolean("required", false);
			JsonElement defaultValue = reader.optionalElement("default");
			JsonArray sourcesArray = reader.optionalArray("sources");
			JsonElement onErrorElement = reader.optionalElement("on_error");
			boolean dedupe = reader.optionalBoolean("dedupe", false);
			Set<String> allowedNodes = reader.optionalLocalIdSet(
				"allowed_nodes",
				MAX_NODES
			);
			JsonObject audienceObject = reader.optionalObject("audience");
			boolean sensitive = reader.optionalBoolean("sensitive", false);
			IdentifierKind identifierKind = parseEnum(
				reader.optionalString("identifier_kind", "any"),
				IdentifierKind.class,
				reader.pointer("identifier_kind"),
				issues
			);
			reader.finish();
			if (required && defaultValue != null) {
				issues.add(
					reader.pointer("default"),
					"CONTRADICTORY_VALUE",
					"a required parameter cannot also declare a default"
				);
			}
			String canonicalDefault = null;
			if (defaultValue != null && type != null) {
				if (validateParameterDefault(defaultValue, type, reader.pointer("default"), issues)) {
					canonicalDefault = canonical(defaultValue);
				}
			}
			List<ParameterSource> sources = parseParameterSources(
				sourcesArray,
				entryPointer + "/sources",
				issues
			);
			ParameterErrorPolicy onError = parseParameterErrorPolicy(
				onErrorElement,
				entryPointer + "/on_error",
				type,
				canonicalDefault,
				issues
			);
			AudienceSpec audience = audienceObject == null
				? null
				: parseAudience(audienceObject, entryPointer + "/audience", issues);
			if (
				type != null
					&& type != ParameterType.IDENTIFIER
					&& identifierKind != null
					&& identifierKind != IdentifierKind.ANY
			) {
				issues.add(
					reader.pointer("identifier_kind"),
					"CONTRADICTORY_VALUE",
					"identifier_kind is only valid for identifier parameters"
				);
			}
			if (sensitive && audience == null) {
				issues.add(
					reader.pointer("audience"),
					"INSECURE_POLICY",
					"sensitive parameters must declare their authorized audience"
				);
			}
			if (type != null) {
				result.put(
					name,
					new ParameterDefinition(
						type,
						required,
						Optional.ofNullable(canonicalDefault),
						sources,
						onError,
						dedupe,
						allowedNodes,
						Optional.ofNullable(audience),
						sensitive,
						identifierKind == null ? IdentifierKind.ANY : identifierKind
					)
				);
				retained++;
			}
		}
		return immutableMap(result);
	}

	private static List<ParameterSource> parseParameterSources(
		final JsonArray array,
		final String pointer,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of(new SimpleParameterSource(ParameterSourceKind.CALL_ARGUMENT));
		}
		if (array.isEmpty()) {
			issues.add(pointer, "OUT_OF_RANGE", "parameter sources must not be empty");
			return List.of(new SimpleParameterSource(ParameterSourceKind.CALL_ARGUMENT));
		}
		if (array.size() > 8) {
			issues.add(pointer, "RESOURCE_LIMIT", "parameter sources exceed 8 entries");
		}
		List<ParameterSource> result = new ArrayList<>();
		int limit = Math.min(array.size(), 8);
		for (int index = 0; index < limit; index++) {
			String sourcePointer = pointer + "/" + index;
			JsonElement element = array.get(index);
			ParameterSource source = null;
			if (isString(element)) {
				ParameterSourceKind kind = parseEnum(
					element.getAsString(),
					ParameterSourceKind.class,
					sourcePointer,
					issues
				);
				if (
					kind == ParameterSourceKind.CALL_ARGUMENT
						|| kind == ParameterSourceKind.INVOKER
						|| kind == ParameterSourceKind.ORIGIN
						|| kind == ParameterSourceKind.TARGET
				) {
					source = new SimpleParameterSource(kind);
				} else if (kind != null) {
					issues.add(
						sourcePointer,
						"TYPE_MISMATCH",
						"structured parameter sources must be objects"
					);
				}
			} else if (element.isJsonObject()) {
				source = parseParameterSource(
					element.getAsJsonObject(),
					sourcePointer,
					issues
				);
			} else {
				issues.add(
					sourcePointer,
					"TYPE_MISMATCH",
					"parameter source must be a string or object"
				);
			}
			if (source != null) {
				result.add(source);
			}
		}
		if (result.isEmpty()) {
			return List.of(new SimpleParameterSource(ParameterSourceKind.CALL_ARGUMENT));
		}
		return List.copyOf(result);
	}

	private static ParameterSource parseParameterSource(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		ParameterSourceKind kind = parseEnum(
			reader.requiredString("type"),
			ParameterSourceKind.class,
			reader.pointer("type"),
			issues
		);
		ParameterSource result = null;
		if (kind == null) {
			reader.finish();
			return null;
		}
		switch (kind) {
			case CALL_ARGUMENT, INVOKER, ORIGIN, TARGET ->
				result = new SimpleParameterSource(kind);
			case SCORE -> {
				String objective = reader.requiredString("objective");
				String holder = reader.optionalString("holder");
				if (objective != null && !SCORE_OBJECTIVE.matcher(objective).matches()) {
					issues.add(
						reader.pointer("objective"),
						"INVALID_SCORE_OBJECTIVE",
						"score objective must match " + SCORE_OBJECTIVE.pattern()
					);
				}
				if (holder != null && (holder.isBlank() || holder.length() > 128)) {
					issues.add(
						reader.pointer("holder"),
						"OUT_OF_RANGE",
						"score holder must be non-blank and at most 128 characters"
					);
				}
				if (objective != null) {
					result = new ScoreParameterSource(objective, Optional.ofNullable(holder));
				}
			}
			case STORAGE -> {
				Identifier storage = reader.requiredIdentifier("storage");
				String path = reader.requiredString("path");
				if (path != null && !SAFE_PATH.matcher(path).matches()) {
					issues.add(
						reader.pointer("path"),
						"INVALID_PATH",
						"storage path contains unsupported characters or exceeds 256 characters"
					);
				}
				if (storage != null && path != null) {
					result = new StorageParameterSource(storage, path);
				}
			}
			case GAME_CONTEXT -> {
				GameContextValue value = parseEnum(
					reader.requiredString("value"),
					GameContextValue.class,
					reader.pointer("value"),
					issues
				);
				if (value != null) {
					result = new GameContextParameterSource(value);
				}
			}
			case PLAYER_DATA -> {
				Identifier field = reader.requiredIdentifier("field");
				if (field != null) {
					result = new PlayerDataParameterSource(field);
				}
			}
		}
		reader.finish();
		return result;
	}

	private static ParameterErrorPolicy parseParameterErrorPolicy(
		final JsonElement element,
		final String pointer,
		final ParameterType type,
		final String canonicalDefault,
		final IssueCollector issues
	) {
		if (element == null) {
			return ParameterErrorPolicy.fail();
		}
		ParameterErrorMode mode;
		JsonElement fallback = null;
		if (isString(element)) {
			mode = parseEnum(
				element.getAsString(),
				ParameterErrorMode.class,
				pointer,
				issues
			);
		} else if (element.isJsonObject()) {
			ObjectReader reader = new ObjectReader(element.getAsJsonObject(), pointer, issues);
			mode = parseEnum(
				reader.requiredString("mode"),
				ParameterErrorMode.class,
				reader.pointer("mode"),
				issues
			);
			fallback = reader.optionalElement("fallback");
			reader.finish();
		} else {
			issues.add(pointer, "TYPE_MISMATCH", "on_error must be a string or object");
			return ParameterErrorPolicy.fail();
		}
		if (mode == null) {
			return ParameterErrorPolicy.fail();
		}
		String canonicalFallback = null;
		if (fallback != null && type != null) {
			if (validateParameterDefault(fallback, type, pointer + "/fallback", issues)) {
				canonicalFallback = canonical(fallback);
			}
		}
		switch (mode) {
			case FAIL -> {
				if (fallback != null) {
					issues.add(
						pointer + "/fallback",
						"CONTRADICTORY_VALUE",
						"fail on_error cannot declare a fallback"
					);
				}
			}
			case USE_DEFAULT -> {
				if (canonicalDefault == null) {
					issues.add(
						pointer,
						"MISSING_REFERENCE",
						"use_default requires a valid parameter default"
					);
				}
				if (fallback != null) {
					issues.add(
						pointer + "/fallback",
						"CONTRADICTORY_VALUE",
						"use_default cannot declare a separate fallback"
					);
				}
			}
			case USE_FALLBACK -> {
				if (fallback == null) {
					issues.add(
						pointer + "/fallback",
						"MISSING_REQUIRED",
						"use_fallback requires a fallback value"
					);
				}
			}
		}
		return new ParameterErrorPolicy(mode, Optional.ofNullable(canonicalFallback));
	}

	private static boolean validateParameterDefault(
		final JsonElement value,
		final ParameterType type,
		final String pointer,
		final IssueCollector issues
	) {
		boolean valid = switch (type) {
			case STRING -> isString(value);
			case INTEGER -> integralLong(value) != null;
			case DECIMAL -> finiteNumber(value) != null;
			case BOOLEAN -> isBoolean(value);
			case IDENTIFIER -> isString(value) && parseIdentifier(value.getAsString()) != null;
			case PLAYER -> false;
			case COMPONENT -> parseComponent(value, pointer, issues, false, true) != null;
			case DURATION -> isString(value)
				&& parseTime(value.getAsString(), pointer, true, issues) != null;
		};
		if (type == ParameterType.PLAYER) {
			issues.add(pointer, "UNSUPPORTED_VALUE", "player parameters cannot declare defaults");
			return false;
		}
		if (!valid && type != ParameterType.COMPONENT) {
			issues.add(
				pointer,
				"TYPE_MISMATCH",
				"default does not match parameter type " + serialized(type)
			);
		}
		return valid;
	}

	private static Map<String, DynamicFieldDefinition> parseFields(
		final JsonObject object,
		final String pointer,
		final CaptureMode defaultCapture,
		final IssueCollector issues
	) {
		if (object == null) {
			return Map.of();
		}
		if (object.size() > MAX_FIELDS) {
			issues.add(pointer, "RESOURCE_LIMIT", "field count exceeds " + MAX_FIELDS);
		}
		Map<String, DynamicFieldDefinition> result = new LinkedHashMap<>();
		int retained = 0;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String name = entry.getKey();
			String entryPointer = pointer + "/" + escapePointer(name);
			validateLocalId(name, entryPointer, issues);
			if (retained >= MAX_FIELDS) {
				continue;
			}
			if (!entry.getValue().isJsonObject()) {
				issues.add(entryPointer, "TYPE_MISMATCH", "field definition must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(
				entry.getValue().getAsJsonObject(),
				entryPointer,
				issues
			);
			CaptureMode capture = parseEnum(
				reader.optionalString("capture", serialized(defaultCapture)),
				CaptureMode.class,
				reader.pointer("capture"),
				issues
			);
			JsonObject sourceObject = reader.requiredObject("source");
			JsonElement fallbackElement = reader.optionalElement("fallback");
			JsonObject reservationObject = reader.optionalObject("reservation");
			reader.finish();
			FieldSource source = parseFieldSource(
				sourceObject,
				entryPointer + "/source",
				issues
			);
			RichComponent fallback = fallbackElement == null
				? null
				: parseComponent(
					fallbackElement,
					entryPointer + "/fallback",
					issues,
					true,
					true
				);
			FieldReservation reservation = parseFieldReservation(
				reservationObject,
				entryPointer + "/reservation",
				issues
			);
			if (capture != null && source != null) {
				result.put(
					name,
					new DynamicFieldDefinition(
						capture,
						source,
						Optional.ofNullable(fallback),
						Optional.ofNullable(reservation)
					)
				);
				retained++;
			}
		}
		return immutableMap(result);
	}

	private static FieldReservation parseFieldReservation(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Integer maxGraphemes = boundedOptionalInteger(
			reader.optionalInteger("max_graphemes"),
			1,
			4096,
			reader.pointer("max_graphemes"),
			issues
		);
		Integer estimatedWidth = boundedOptionalInteger(
			reader.optionalInteger("estimated_width"),
			1,
			16_384,
			reader.pointer("estimated_width"),
			issues
		);
		Integer maxLines = boundedOptionalInteger(
			reader.optionalInteger("max_lines"),
			1,
			128,
			reader.pointer("max_lines"),
			issues
		);
		reader.finish();
		if (maxGraphemes == null && estimatedWidth == null && maxLines == null) {
			issues.add(
				pointer,
				"EMPTY_DEFINITION",
				"field reservation must declare at least one bound"
			);
		}
		return new FieldReservation(
			Optional.ofNullable(maxGraphemes),
			Optional.ofNullable(estimatedWidth),
			Optional.ofNullable(maxLines)
		);
	}

	private static FieldSource parseFieldSource(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String argument = reader.optionalString("argument");
		String binding = reader.optionalString("binding");
		JsonElement component = reader.optionalElement("component");
		reader.finish();
		int sourceCount = (argument == null ? 0 : 1)
			+ (binding == null ? 0 : 1)
			+ (component == null ? 0 : 1);
		if (sourceCount != 1) {
			issues.add(
				pointer,
				"INVALID_ONE_OF",
				"field source must declare exactly one of argument, binding, or component"
			);
			return null;
		}
		if (argument != null) {
			validateLocalId(argument, pointer + "/argument", issues);
			return new ArgumentSource(argument);
		}
		if (binding != null) {
			if (
				binding.isBlank()
					|| binding.length() > MAX_BINDING_PATH_LENGTH
					|| binding.chars().anyMatch(Character::isWhitespace)
			) {
				issues.add(
					pointer + "/binding",
					"INVALID_BINDING",
					"binding path must be non-blank, whitespace-free, and at most "
						+ MAX_BINDING_PATH_LENGTH
						+ " characters"
				);
			}
			return new BindingSource(binding);
		}
		RichComponent parsed = parseComponent(
			component,
			pointer + "/component",
			issues,
			false,
			true
		);
		return parsed == null ? null : new VanillaComponentSource(parsed);
	}

	private static List<CueNode> parseNodes(
		final JsonArray array,
		final CaptureMode defaultCapture,
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> cueFields,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > MAX_NODES) {
			issues.add("/nodes", "RESOURCE_LIMIT", "node count exceeds " + MAX_NODES);
		}
		List<CueNode> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		int limit = Math.min(array.size(), MAX_NODES);
		for (int index = 0; index < limit; index++) {
			String pointer = "/nodes/" + index;
			JsonElement element = array.get(index);
			if (!element.isJsonObject()) {
				issues.add(pointer, "TYPE_MISMATCH", "cue node must be an object");
				continue;
			}
			CueNode node = parseNode(
				element.getAsJsonObject(),
				pointer,
				defaultCapture,
				parameters,
				cueFields,
				issues
			);
			if (node == null) {
				continue;
			}
			if (!ids.add(node.id())) {
				issues.add(pointer + "/id", "DUPLICATE_NODE_ID", "cue node id must be unique");
			}
			result.add(node);
		}
		return List.copyOf(result);
	}

	private static CueNode parseNode(
		final JsonObject object,
		final String pointer,
		final CaptureMode defaultCapture,
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> cueFields,
		final IssueCollector issues
	) {
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String id = reader.requiredString("id");
		String type = reader.requiredString("type");
		TimeSpan legacyAt = parseOptionalTime(reader, "at", true, issues);
		JsonObject scheduleObject = reader.optionalObject("schedule");
		JsonObject audienceObject = reader.optionalObject("audience");
		Integer priority = reader.optionalInteger("priority");
		ConflictMode conflict = parseEnum(
			reader.optionalString("conflict"),
			ConflictMode.class,
			reader.pointer("conflict"),
			issues
		);
		NodePolicy policy = new NodePolicy(
			Optional.ofNullable(
				audienceObject == null
					? null
					: parseAudience(audienceObject, pointer + "/audience", issues)
			),
			Optional.ofNullable(
				priority == null
					? null
					: validatePriority(priority, reader.pointer("priority"), issues)
			),
			Optional.ofNullable(conflict)
		);
		JsonObject whenObject = reader.optionalObject("when");
		JsonObject repeatObject = reader.optionalObject("repeat");
		if (legacyAt != null && scheduleObject != null) {
			issues.add(
				pointer,
				"INVALID_ONE_OF",
				"node must use either legacy at or schedule, not both"
			);
		}
		ScheduleSpec schedule = parseSchedule(
			scheduleObject,
			pointer + "/schedule",
			legacyAt,
			issues
		);
		ConditionSpec when = parseConditionSpec(
			whenObject,
			pointer + "/when",
			ConditionEvaluation.NODE_START,
			issues
		);
		RepeatSpec repeat = parseRepeat(repeatObject, pointer + "/repeat", issues);
		if (id != null) {
			validateLocalId(id, pointer + "/id", issues);
		}
		if (type == null || id == null || schedule == null || repeat == null) {
			reader.finish();
			return null;
		}
		NodeHeader header = new NodeHeader(
			id,
			schedule,
			policy,
			Optional.ofNullable(when),
			repeat
		);
		return switch (type) {
			case "text" -> parseTextNode(
				reader,
				header,
				defaultCapture,
				parameters,
				cueFields,
				issues
			);
			case "sound" -> parseSoundNode(reader, header, issues);
			case "callback" -> parseCallbackNode(reader, header, issues);
			default -> {
				issues.add(
					pointer + "/type",
					"UNKNOWN_NODE_TYPE",
					"unknown message cue node type " + type
				);
				reader.finish();
				yield null;
			}
		};
	}

	private static ScheduleSpec parseSchedule(
		final JsonObject object,
		final String pointer,
		final TimeSpan legacyAt,
		final IssueCollector issues
	) {
		if (object == null) {
			return new AtCueTime(defaultTime(legacyAt));
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		String type = reader.requiredString("type");
		TimeSpan offset = defaultTime(parseOptionalTime(reader, "offset", true, issues));
		ScheduleSpec result = null;
		if (type != null) {
			switch (type) {
				case "cue_start" -> result = new AtCueTime(offset);
				case "after_node" -> {
					String node = reader.requiredString("node");
					if (node != null) {
						validateLocalId(node, reader.pointer("node"), issues);
						result = new AfterNode(node, offset);
					}
				}
				case "cue_event" -> {
					CallbackTrigger event = parseEnum(
						reader.requiredString("event"),
						CallbackTrigger.class,
						reader.pointer("event"),
						issues
					);
					if (event != null) {
						result = new OnCueEvent(event, offset);
					}
				}
				default -> issues.add(
					reader.pointer("type"),
					"INVALID_ENUM",
					"unsupported schedule type " + type
				);
			}
		}
		reader.finish();
		return result;
	}

	private static ConditionSpec parseConditionSpec(
		final JsonObject object,
		final String pointer,
		final ConditionEvaluation defaultEvaluation,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		boolean wrapped = object.has("evaluate_at")
			|| object.has("expression")
			|| object.has("predicate");
		if (!wrapped) {
			ConditionExpression expression = parseConditionExpression(object, pointer, issues);
			return expression == null
				? null
				: new ConditionSpec(
					defaultEvaluation,
					Optional.of(expression),
					Optional.empty()
				);
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		ConditionEvaluation evaluateAt = parseEnum(
			reader.optionalString("evaluate_at", serialized(defaultEvaluation)),
			ConditionEvaluation.class,
			reader.pointer("evaluate_at"),
			issues
		);
		JsonElement expressionElement = reader.optionalElement("expression");
		Identifier predicate = reader.optionalIdentifier("predicate");
		reader.finish();
		int selected = (expressionElement == null ? 0 : 1) + (predicate == null ? 0 : 1);
		if (selected != 1) {
			issues.add(
				pointer,
				"INVALID_ONE_OF",
				"condition must declare exactly one of expression or predicate"
			);
			return null;
		}
		ConditionExpression expression = expressionElement == null
			? null
			: parseConditionExpression(expressionElement, pointer + "/expression", issues);
		if (evaluateAt == null || (expression == null && predicate == null)) {
			return null;
		}
		return new ConditionSpec(
			evaluateAt,
			Optional.ofNullable(expression),
			Optional.ofNullable(predicate)
		);
	}

	private static ConditionExpression parseConditionExpression(
		final JsonElement element,
		final String pointer,
		final IssueCollector issues
	) {
		UiDefinitionParser.ParseResult<ConditionExpression> parsed =
			UiDefinitionParser.parseConditionFragment(element);
		for (UiDefinitionParser.Issue issue : parsed.issues()) {
			String suffix = issue.pointer() == null ? "" : issue.pointer();
			issues.add(pointer + suffix, issue.code(), issue.message());
		}
		return parsed.value().orElse(null);
	}

	private static RepeatSpec parseRepeat(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return RepeatSpec.once();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		int count = boundedInteger(
			reader.optionalInteger("count", 1),
			1,
			MAX_REPEAT_COUNT,
			reader.pointer("count"),
			issues
		);
		TimeSpan interval = defaultTime(parseOptionalTime(reader, "interval", true, issues));
		RepeatCaptureMode capture = parseEnum(
			reader.optionalString("capture", "keep"),
			RepeatCaptureMode.class,
			reader.pointer("capture"),
			issues
		);
		reader.finish();
		if (count == 1 && interval.nanoseconds() > 0L) {
			issues.add(
				reader.pointer("interval"),
				"CONTRADICTORY_VALUE",
				"repeat interval has no effect when count is 1"
			);
		}
		return new RepeatSpec(
			count,
			interval,
			capture == null ? RepeatCaptureMode.KEEP : capture
		);
	}

	private static TextNode parseTextNode(
		final ObjectReader reader,
		final NodeHeader header,
		final CaptureMode defaultCapture,
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> cueFields,
		final IssueCollector issues
	) {
		TextChannel channel = parseEnum(
			reader.requiredString("channel"),
			TextChannel.class,
			reader.pointer("channel"),
			issues
		);
		JsonObject contentObject = reader.optionalObject("content");
		JsonObject legacyTextObject = reader.optionalObject("text");
		JsonObject legacyFieldsObject = reader.optionalObject("fields");
		JsonElement legacyEffect = reader.optionalElement("effect");
		TimeSpan legacyHold = parseOptionalTime(reader, "hold", true, issues);
		JsonArray variantsArray = reader.optionalArray("variants");
		reader.finish();
		boolean hasLegacyContent = legacyTextObject != null
			|| legacyFieldsObject != null
			|| legacyEffect != null
			|| legacyHold != null;
		if (contentObject != null && hasLegacyContent) {
			issues.add(
				reader.pointer("content"),
				"INVALID_ONE_OF",
				"text node must use either content or legacy text/fields/effect/hold"
			);
		}
		TextContent content;
		if (contentObject != null) {
			content = parseTextContent(
				contentObject,
				reader.pointer("content"),
				defaultCapture,
				issues
			);
		} else {
			if (legacyTextObject == null) {
				issues.add(
					reader.pointer("text"),
					"MISSING_REQUIRED",
					"text node requires content or legacy text"
				);
			}
			content = parseLegacyTextContent(
				legacyTextObject,
				legacyFieldsObject,
				legacyEffect,
				legacyHold,
				reader.basePointer,
				defaultCapture,
				issues
			);
		}
		List<TextVariant> variants = parseTextVariants(
			variantsArray,
			reader.pointer("variants"),
			defaultCapture,
			issues
		);
		if (channel == null || content == null) {
			return null;
		}
		return new TextNode(header, channel, content, variants);
	}

	private static TextContent parseTextContent(
		final JsonObject object,
		final String pointer,
		final CaptureMode defaultCapture,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject textObject = reader.requiredObject("text");
		JsonObject fieldsObject = reader.optionalObject("fields");
		JsonElement effectElement = reader.optionalElement("effect");
		JsonObject layoutObject = reader.optionalObject("layout");
		JsonObject durationObject = reader.optionalObject("duration");
		JsonObject speakerObject = reader.optionalObject("speaker");
		CharacterSoundRole characterSoundRole = parseEnum(
			reader.optionalString("character_sound_role", "muted"),
			CharacterSoundRole.class,
			reader.pointer("character_sound_role"),
			issues
		);
		TimeSpan hold = parseOptionalTime(reader, "hold", true, issues);
		reader.finish();
		LocalizedTemplate text = parseLocalizedTemplate(
			textObject,
			pointer + "/text",
			issues
		);
		Map<String, DynamicFieldDefinition> fields = parseFields(
			fieldsObject,
			pointer + "/fields",
			defaultCapture,
			issues
		);
		EffectUse effect = parseEffectUse(effectElement, pointer + "/effect", issues);
		TextLayout layout = parseTextLayout(layoutObject, pointer + "/layout", issues);
		DurationSpec duration = parseDurationSpec(
			durationObject,
			pointer + "/duration",
			issues
		);
		SpeakerSpec speaker = parseSpeaker(speakerObject, pointer + "/speaker", issues);
		if (
			text == null
				|| layout == null
				|| duration == null
				|| speaker == null
				|| characterSoundRole == null
		) {
			return null;
		}
		return new TextContent(
			text,
			fields,
			Optional.ofNullable(effect),
			layout,
			duration,
			speaker,
			characterSoundRole,
			Optional.ofNullable(hold)
		);
	}

	private static TextContent parseLegacyTextContent(
		final JsonObject textObject,
		final JsonObject fieldsObject,
		final JsonElement effectElement,
		final TimeSpan hold,
		final String pointer,
		final CaptureMode defaultCapture,
		final IssueCollector issues
	) {
		ComponentTemplate template = parseComponentTemplate(
			textObject,
			pointer + "/text",
			issues
		);
		if (template == null) {
			return null;
		}
		return new TextContent(
			new LocalizedTemplate(template, Map.of()),
			parseFields(fieldsObject, pointer + "/fields", defaultCapture, issues),
			Optional.ofNullable(parseEffectUse(effectElement, pointer + "/effect", issues)),
			TextLayout.standard(),
			DurationSpec.contentDriven(),
			SpeakerSpec.system(),
			CharacterSoundRole.MUTED,
			Optional.ofNullable(hold)
		);
	}

	private static List<TextVariant> parseTextVariants(
		final JsonArray array,
		final String pointer,
		final CaptureMode defaultCapture,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > MAX_VARIANTS_PER_NODE) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"text node variants exceed " + MAX_VARIANTS_PER_NODE
			);
		}
		List<TextVariant> result = new ArrayList<>();
		int limit = Math.min(array.size(), MAX_VARIANTS_PER_NODE);
		for (int index = 0; index < limit; index++) {
			String variantPointer = pointer + "/" + index;
			JsonElement element = array.get(index);
			if (!element.isJsonObject()) {
				issues.add(
					variantPointer,
					"TYPE_MISMATCH",
					"text variant must be an object"
				);
				continue;
			}
			ObjectReader reader = new ObjectReader(
				element.getAsJsonObject(),
				variantPointer,
				issues
			);
			JsonObject whenObject = reader.requiredObject("when");
			JsonObject contentObject = reader.requiredObject("content");
			reader.finish();
			ConditionSpec when = parseConditionSpec(
				whenObject,
				variantPointer + "/when",
				ConditionEvaluation.NODE_START,
				issues
			);
			TextContent content = parseTextContent(
				contentObject,
				variantPointer + "/content",
				defaultCapture,
				issues
			);
			if (when != null && content != null) {
				result.add(new TextVariant(when, content));
			}
		}
		return List.copyOf(result);
	}

	private static EffectUse parseEffectUse(
		final JsonElement element,
		final String pointer,
		final IssueCollector issues
	) {
		if (element == null) {
			return null;
		}
		if (isString(element)) {
			Identifier preset = parseIdentifier(element.getAsString());
			if (preset == null) {
				issues.add(
					pointer,
					"INVALID_IDENTIFIER",
					"effect preset must include an explicit namespace"
				);
				return null;
			}
			return new EffectUse(Optional.of(preset), TextEffectPatch.empty());
		}
		if (!element.isJsonObject()) {
			issues.add(pointer, "TYPE_MISMATCH", "effect must be an identifier or object");
			return null;
		}
		ObjectReader reader = new ObjectReader(element.getAsJsonObject(), pointer, issues);
		Identifier preset = reader.optionalIdentifier("preset");
		JsonObject overridesObject = reader.optionalObject("overrides");
		reader.finish();
		TextEffectPatch overrides = parseTextEffectPatch(
			overridesObject,
			pointer + "/overrides",
			issues
		);
		if (preset == null && overrides.isEmpty()) {
			issues.add(
				pointer,
				"EMPTY_DEFINITION",
				"effect must declare a preset or at least one override"
			);
			return null;
		}
		return new EffectUse(Optional.ofNullable(preset), overrides);
	}

	private static TextEffectPatch parseTextEffectPatch(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return TextEffectPatch.empty();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject typewriterObject = reader.optionalObject("typewriter");
		JsonObject fadeObject = reader.optionalObject("fade");
		JsonObject scrambleObject = reader.optionalObject("scramble");
		JsonObject gradientObject = reader.optionalObject("gradient");
		JsonObject motionObject = reader.optionalObject("motion");
		JsonObject pulseObject = reader.optionalObject("pulse");
		JsonObject characterSoundObject = reader.optionalObject("character_sound");
		Integer maxSoundEvents = boundedOptionalInteger(
			reader.optionalInteger("max_character_sound_events"),
			1,
			MAX_CHARACTER_SOUND_EVENTS,
			reader.pointer("max_character_sound_events"),
			issues
		);
		reader.finish();
		return new TextEffectPatch(
			Optional.ofNullable(
				parseTypewriterPatch(typewriterObject, pointer + "/typewriter", issues)
			),
			Optional.ofNullable(parseFadePatch(fadeObject, pointer + "/fade", issues)),
			Optional.ofNullable(
				parseScramblePatch(scrambleObject, pointer + "/scramble", issues)
			),
			Optional.ofNullable(parseGradient(gradientObject, pointer + "/gradient", issues)),
			Optional.ofNullable(parseMotion(motionObject, pointer + "/motion", issues)),
			Optional.ofNullable(parsePulse(pulseObject, pointer + "/pulse", issues)),
			Optional.ofNullable(
				parseCharacterSoundPatch(
					characterSoundObject,
					pointer + "/character_sound",
					issues
				)
			),
			Optional.ofNullable(maxSoundEvents)
		);
	}

	private static TypewriterPatch parseTypewriterPatch(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TimeSpan interval = parseOptionalTime(reader, "character_interval", false, issues);
		JsonObject cursorObject = reader.optionalObject("cursor");
		String freshColor = reader.optionalString("fresh_color");
		Integer restoreAfter = boundedOptionalInteger(
			reader.optionalInteger("restore_after_characters"),
			0,
			MAX_RESTORE_CHARACTERS,
			reader.pointer("restore_after_characters"),
			issues
		);
		RestoreMode restoreMode = parseEnum(
			reader.optionalString("restore_mode"),
			RestoreMode.class,
			reader.pointer("restore_mode"),
			issues
		);
		reader.finish();
		validateCharacterInterval(interval, reader.pointer("character_interval"), issues);
		if (freshColor != null) {
			validateColor(freshColor, reader.pointer("fresh_color"), issues);
		}
		CursorPatch cursor = parseCursorPatch(cursorObject, pointer + "/cursor", issues);
		if (
			interval == null
				&& cursor == null
				&& freshColor == null
				&& restoreAfter == null
				&& restoreMode == null
		) {
			issues.add(pointer, "EMPTY_DEFINITION", "typewriter override must not be empty");
		}
		return new TypewriterPatch(
			Optional.ofNullable(interval),
			Optional.ofNullable(cursor),
			Optional.ofNullable(freshColor),
			Optional.ofNullable(restoreAfter),
			Optional.ofNullable(restoreMode)
		);
	}

	private static CursorPatch parseCursorPatch(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Boolean enabled = reader.optionalBoolean("enabled");
		String glyph = reader.optionalString("glyph");
		String color = reader.optionalString("color");
		Boolean blink = reader.optionalBoolean("blink");
		TimeSpan blinkPeriod = parseOptionalTime(reader, "blink_period", false, issues);
		reader.finish();
		if (
			glyph != null
				&& (
					glyph.isBlank()
						|| glyph.codePointCount(0, glyph.length()) > MAX_CURSOR_CODE_POINTS
				)
		) {
			issues.add(
				reader.pointer("glyph"),
				"OUT_OF_RANGE",
				"cursor glyph must contain between 1 and "
					+ MAX_CURSOR_CODE_POINTS
					+ " code points"
			);
		}
		if (color != null) {
			validateColor(color, reader.pointer("color"), issues);
		}
		if (
			enabled == null
				&& glyph == null
				&& color == null
				&& blink == null
				&& blinkPeriod == null
		) {
			issues.add(pointer, "EMPTY_DEFINITION", "cursor override must not be empty");
		}
		return new CursorPatch(
			Optional.ofNullable(enabled),
			Optional.ofNullable(glyph),
			Optional.ofNullable(color),
			Optional.ofNullable(blink),
			Optional.ofNullable(blinkPeriod)
		);
	}

	private static FadePatch parseFadePatch(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TimeSpan enter = parseOptionalTime(reader, "enter", true, issues);
		TimeSpan hold = parseOptionalTime(reader, "hold", true, issues);
		TimeSpan exit = parseOptionalTime(reader, "exit", true, issues);
		reader.finish();
		if (enter == null && hold == null && exit == null) {
			issues.add(pointer, "EMPTY_DEFINITION", "fade override must not be empty");
		}
		return new FadePatch(
			Optional.ofNullable(enter),
			Optional.ofNullable(hold),
			Optional.ofNullable(exit)
		);
	}

	private static ScramblePatch parseScramblePatch(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TimeSpan interval = parseOptionalTime(reader, "character_interval", false, issues);
		String characters = reader.optionalString("characters");
		reader.finish();
		validateCharacterInterval(interval, reader.pointer("character_interval"), issues);
		if (
			characters != null
				&& (
					characters.isBlank()
						|| characters.codePointCount(0, characters.length())
							> MAX_SCRAMBLE_CODE_POINTS
				)
		) {
			issues.add(
				reader.pointer("characters"),
				"OUT_OF_RANGE",
				"scramble characters must contain between 1 and "
					+ MAX_SCRAMBLE_CODE_POINTS
					+ " code points"
			);
		}
		if (interval == null && characters == null) {
			issues.add(pointer, "EMPTY_DEFINITION", "scramble override must not be empty");
		}
		return new ScramblePatch(
			Optional.ofNullable(interval),
			Optional.ofNullable(characters)
		);
	}

	private static CharacterSoundPatch parseCharacterSoundPatch(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Identifier sound = reader.optionalIdentifier("sound");
		SoundCategory category = parseEnum(
			reader.optionalString("category"),
			SoundCategory.class,
			reader.pointer("category"),
			issues
		);
		Integer everyCharacters = boundedOptionalInteger(
			reader.optionalInteger("every_characters"),
			1,
			64,
			reader.pointer("every_characters"),
			issues
		);
		Boolean skipWhitespace = reader.optionalBoolean("skip_whitespace");
		Boolean skipPunctuation = reader.optionalBoolean("skip_punctuation");
		Boolean skipNewline = reader.optionalBoolean("skip_newline");
		Double volumeValue = reader.optionalDouble("volume");
		Double pitchValue = reader.optionalDouble("pitch");
		Double pitchVariationValue = reader.optionalDouble("pitch_variation");
		SoundAttachment attachment = parseEnum(
			reader.optionalString("attachment"),
			SoundAttachment.class,
			reader.pointer("attachment"),
			issues
		);
		MissingAssetMode missing = parseEnum(
			reader.optionalString("missing"),
			MissingAssetMode.class,
			reader.pointer("missing"),
			issues
		);
		Identifier fallback = reader.optionalIdentifier("fallback");
		reader.finish();
		Float volume = volumeValue == null
			? null
			: boundedFloat(volumeValue, 0.0D, 4.0D, reader.pointer("volume"), issues);
		Float pitch = pitchValue == null
			? null
			: boundedFloat(pitchValue, 0.0D, 2.0D, reader.pointer("pitch"), issues);
		Float pitchVariation = pitchVariationValue == null
			? null
			: boundedFloat(
				pitchVariationValue,
				0.0D,
				2.0D,
				reader.pointer("pitch_variation"),
				issues
			);
		CharacterSoundPatch patch = new CharacterSoundPatch(
			Optional.ofNullable(sound),
			Optional.ofNullable(category),
			Optional.ofNullable(everyCharacters),
			Optional.ofNullable(skipWhitespace),
			Optional.ofNullable(skipPunctuation),
			Optional.ofNullable(skipNewline),
			Optional.ofNullable(volume),
			Optional.ofNullable(pitch),
			Optional.ofNullable(pitchVariation),
			Optional.ofNullable(attachment),
			Optional.ofNullable(missing),
			Optional.ofNullable(fallback)
		);
		if (
			patch.sound().isEmpty()
				&& patch.category().isEmpty()
				&& patch.everyCharacters().isEmpty()
				&& patch.skipWhitespace().isEmpty()
				&& patch.skipPunctuation().isEmpty()
				&& patch.skipNewline().isEmpty()
				&& patch.volume().isEmpty()
				&& patch.pitch().isEmpty()
				&& patch.pitchVariation().isEmpty()
				&& patch.attachment().isEmpty()
				&& patch.missing().isEmpty()
				&& patch.fallback().isEmpty()
		) {
			issues.add(pointer, "EMPTY_DEFINITION", "character_sound override must not be empty");
		}
		return patch;
	}

	private static TextLayout parseTextLayout(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return TextLayout.standard();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TextAlignment alignment = parseEnum(
			reader.optionalString("alignment", "center"),
			TextAlignment.class,
			reader.pointer("alignment"),
			issues
		);
		int offsetX = boundedInteger(
			reader.optionalInteger("offset_x", 0),
			-256,
			256,
			reader.pointer("offset_x"),
			issues
		);
		int offsetY = boundedInteger(
			reader.optionalInteger("offset_y", 0),
			-256,
			256,
			reader.pointer("offset_y"),
			issues
		);
		Integer maxWidth = boundedOptionalInteger(
			reader.optionalInteger("max_width"),
			1,
			8192,
			reader.pointer("max_width"),
			issues
		);
		Integer maxLines = boundedOptionalInteger(
			reader.optionalInteger("max_lines"),
			1,
			128,
			reader.pointer("max_lines"),
			issues
		);
		float lineSpacing = boundedFloat(
			reader.optionalDouble("line_spacing", 1.0D),
			0.5D,
			4.0D,
			reader.pointer("line_spacing"),
			issues
		);
		TextOverflowMode overflow = parseEnum(
			reader.optionalString("overflow", "wrap"),
			TextOverflowMode.class,
			reader.pointer("overflow"),
			issues
		);
		reader.finish();
		return new TextLayout(
			alignment == null ? TextAlignment.CENTER : alignment,
			offsetX,
			offsetY,
			Optional.ofNullable(maxWidth),
			Optional.ofNullable(maxLines),
			lineSpacing,
			overflow == null ? TextOverflowMode.WRAP : overflow
		);
	}

	private static DurationSpec parseDurationSpec(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return DurationSpec.contentDriven();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		DurationMode mode = parseEnum(
			reader.optionalString("mode", "content_driven"),
			DurationMode.class,
			reader.pointer("mode"),
			issues
		);
		TimeSpan total = parseOptionalTime(reader, "total", false, issues);
		LanguageDurationBasis languageBasis = parseEnum(
			reader.optionalString("language_basis", "longest_declared"),
			LanguageDurationBasis.class,
			reader.pointer("language_basis"),
			issues
		);
		Double minimumSpeedValue = reader.optionalDouble("minimum_speed");
		Double maximumSpeedValue = reader.optionalDouble("maximum_speed");
		reader.finish();
		Float minimumSpeed = minimumSpeedValue == null
			? null
			: boundedFloat(
				minimumSpeedValue,
				0.01D,
				1000.0D,
				reader.pointer("minimum_speed"),
				issues
			);
		Float maximumSpeed = maximumSpeedValue == null
			? null
			: boundedFloat(
				maximumSpeedValue,
				0.01D,
				1000.0D,
				reader.pointer("maximum_speed"),
				issues
			);
		if (mode == DurationMode.FIXED_TOTAL && total == null) {
			issues.add(
				reader.pointer("total"),
				"MISSING_REQUIRED",
				"fixed_total duration requires total"
			);
		}
		if (mode == DurationMode.CONTENT_DRIVEN && total != null) {
			issues.add(
				reader.pointer("total"),
				"CONTRADICTORY_VALUE",
				"content_driven duration cannot declare total"
			);
		}
		if (
			minimumSpeed != null
				&& maximumSpeed != null
				&& minimumSpeed > maximumSpeed
		) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"minimum_speed must not exceed maximum_speed"
			);
		}
		return new DurationSpec(
			mode == null ? DurationMode.CONTENT_DRIVEN : mode,
			Optional.ofNullable(total),
			languageBasis == null
				? LanguageDurationBasis.LONGEST_DECLARED
				: languageBasis,
			Optional.ofNullable(minimumSpeed),
			Optional.ofNullable(maximumSpeed)
		);
	}

	private static SpeakerSpec parseSpeaker(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return SpeakerSpec.system();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		SpeakerKind kind = parseEnum(
			reader.optionalString("kind", "system"),
			SpeakerKind.class,
			reader.pointer("kind"),
			issues
		);
		String parameter = reader.optionalString("parameter");
		if (parameter != null) {
			validateLocalId(parameter, reader.pointer("parameter"), issues);
		}
		Identifier entity = reader.optionalIdentifier("entity");
		reader.finish();
		if (kind == SpeakerKind.PLAYER_PARAMETER && parameter == null) {
			issues.add(
				reader.pointer("parameter"),
				"MISSING_REQUIRED",
				"player_parameter speaker requires parameter"
			);
		}
		if (kind == SpeakerKind.REGISTERED_ENTITY && entity == null) {
			issues.add(
				reader.pointer("entity"),
				"MISSING_REQUIRED",
				"registered_entity speaker requires entity"
			);
		}
		if (kind != SpeakerKind.PLAYER_PARAMETER && parameter != null) {
			issues.add(
				reader.pointer("parameter"),
				"CONTRADICTORY_VALUE",
				"parameter is only valid for player_parameter speaker"
			);
		}
		if (kind != SpeakerKind.REGISTERED_ENTITY && entity != null) {
			issues.add(
				reader.pointer("entity"),
				"CONTRADICTORY_VALUE",
				"entity is only valid for registered_entity speaker"
			);
		}
		return new SpeakerSpec(
			kind == null ? SpeakerKind.SYSTEM : kind,
			Optional.ofNullable(parameter),
			Optional.ofNullable(entity)
		);
	}

	private static SoundNode parseSoundNode(
		final ObjectReader reader,
		final NodeHeader header,
		final IssueCollector issues
	) {
		Identifier sound = reader.requiredIdentifier("sound");
		SoundCategory category = parseEnum(
			reader.optionalString("category", "master"),
			SoundCategory.class,
			reader.pointer("category"),
			issues
		);
		double volume = reader.optionalDouble("volume", 1.0D);
		double pitch = reader.optionalDouble("pitch", 1.0D);
		SoundAttachment attachment = parseEnum(
			reader.optionalString("attachment", "player"),
			SoundAttachment.class,
			reader.pointer("attachment"),
			issues
		);
		MissingAssetMode missing = parseEnum(
			reader.optionalString("missing", "silent"),
			MissingAssetMode.class,
			reader.pointer("missing"),
			issues
		);
		Identifier fallback = reader.optionalIdentifier("fallback");
		reader.finish();
		validateMissingFallback(
			missing,
			fallback,
			reader.pointer("missing"),
			reader.pointer("fallback"),
			issues
		);
		if (sound == null || category == null || attachment == null || missing == null) {
			return null;
		}
		return new SoundNode(
			header,
			sound,
			category,
			boundedFloat(volume, 0.0D, 4.0D, reader.pointer("volume"), issues),
			boundedFloat(pitch, 0.0D, 2.0D, reader.pointer("pitch"), issues),
			attachment,
			missing,
			Optional.ofNullable(fallback)
		);
	}

	private static CallbackNode parseCallbackNode(
		final ObjectReader reader,
		final NodeHeader header,
		final IssueCollector issues
	) {
		Identifier function = reader.requiredIdentifier("function");
		CallbackExecution execution = parseEnum(
			reader.optionalString("execution", "as_server"),
			CallbackExecution.class,
			reader.pointer("execution"),
			issues
		);
		CallbackLocation location = parseEnum(
			reader.optionalString("location", "origin"),
			CallbackLocation.class,
			reader.pointer("location"),
			issues
		);
		reader.finish();
		if (function == null || execution == null || location == null) {
			return null;
		}
		return new CallbackNode(header, function, execution, location);
	}

	private static ComponentTemplate parseComponentTemplate(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonArray partsArray = reader.requiredArray("parts");
		reader.finish();
		if (partsArray == null) {
			return null;
		}
		if (partsArray.isEmpty()) {
			issues.add(pointer + "/parts", "OUT_OF_RANGE", "text template must contain at least one part");
		}
		if (partsArray.size() > MAX_TEMPLATE_PARTS) {
			issues.add(
				pointer + "/parts",
				"RESOURCE_LIMIT",
				"text template part count exceeds " + MAX_TEMPLATE_PARTS
			);
		}
		List<ComponentPart> parts = new ArrayList<>();
		int limit = Math.min(partsArray.size(), MAX_TEMPLATE_PARTS);
		for (int index = 0; index < limit; index++) {
			String partPointer = pointer + "/parts/" + index;
			JsonElement element = partsArray.get(index);
			if (!element.isJsonObject()) {
				issues.add(partPointer, "TYPE_MISMATCH", "text template part must be an object");
				continue;
			}
			ObjectReader partReader = new ObjectReader(element.getAsJsonObject(), partPointer, issues);
			JsonElement componentElement = partReader.optionalElement("component");
			JsonObject fieldObject = partReader.optionalObject("field");
			TimeSpan pauseBefore = parseOptionalTime(
				partReader,
				"pause_before",
				true,
				issues
			);
			TimeSpan pauseAfter = parseOptionalTime(
				partReader,
				"pause_after",
				true,
				issues
			);
			boolean emphasis = partReader.optionalBoolean("emphasis", false);
			partReader.finish();
			SegmentTiming timing = new SegmentTiming(
				Optional.ofNullable(pauseBefore),
				Optional.ofNullable(pauseAfter),
				emphasis
			);
			int selected = (componentElement == null ? 0 : 1) + (fieldObject == null ? 0 : 1);
			if (selected != 1) {
				issues.add(
					partPointer,
					"INVALID_ONE_OF",
					"text template part must declare exactly one of component or field"
				);
				continue;
			}
			if (componentElement != null) {
				RichComponent component = parseComponent(
					componentElement,
					partPointer + "/component",
					issues,
					false,
					false
				);
				if (component != null) {
					parts.add(new StaticComponentPart(component, timing));
				}
				continue;
			}
			ObjectReader fieldReader = new ObjectReader(
				fieldObject,
				partPointer + "/field",
				issues
			);
			FieldScope scope = parseEnum(
				fieldReader.requiredString("scope"),
				FieldScope.class,
				fieldReader.pointer("scope"),
				issues
			);
			String fieldId = fieldReader.requiredString("id");
			fieldReader.finish();
			if (fieldId != null) {
				validateLocalId(fieldId, fieldReader.pointer("id"), issues);
			}
			if (scope != null && fieldId != null) {
				parts.add(new FieldPart(new FieldReference(scope, fieldId), timing));
			}
		}
		boolean hasDynamicField = parts.stream().anyMatch(FieldPart.class::isInstance);
		boolean hasVisibleText = parts.stream()
			.filter(StaticComponentPart.class::isInstance)
			.map(StaticComponentPart.class::cast)
			.anyMatch(part -> !part.component().plainText().isBlank());
		if (!parts.isEmpty() && !hasDynamicField && !hasVisibleText) {
			issues.add(
				pointer,
				"EMPTY_TEXT",
				"text template must contain visible static text or a dynamic field"
			);
		}
		return new ComponentTemplate(parts);
	}

	private static LocalizedTemplate parseLocalizedTemplate(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		if (object.has("parts")) {
			ComponentTemplate fallback = parseComponentTemplate(object, pointer, issues);
			return fallback == null ? null : new LocalizedTemplate(fallback, Map.of());
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject fallbackObject = reader.requiredObject("fallback");
		JsonObject localesObject = reader.optionalObject("locales");
		reader.finish();
		ComponentTemplate fallback = parseComponentTemplate(
			fallbackObject,
			pointer + "/fallback",
			issues
		);
		Map<String, ComponentTemplate> locales = new LinkedHashMap<>();
		if (localesObject != null) {
			if (localesObject.size() > MAX_LOCALES) {
				issues.add(
					pointer + "/locales",
					"RESOURCE_LIMIT",
					"localized template exceeds " + MAX_LOCALES + " locales"
				);
			}
			int retained = 0;
			for (Map.Entry<String, JsonElement> entry : localesObject.entrySet()) {
				String localePointer = pointer + "/locales/" + escapePointer(entry.getKey());
				if (retained >= MAX_LOCALES) {
					continue;
				}
				if (!LOCALE.matcher(entry.getKey()).matches()) {
					issues.add(
						localePointer,
						"INVALID_LOCALE",
						"locale must match " + LOCALE.pattern()
					);
				}
				if (!entry.getValue().isJsonObject()) {
					issues.add(
						localePointer,
						"TYPE_MISMATCH",
						"localized text template must be an object"
					);
					continue;
				}
				ComponentTemplate template = parseComponentTemplate(
					entry.getValue().getAsJsonObject(),
					localePointer,
					issues
				);
				if (template != null) {
					locales.put(entry.getKey(), template);
					retained++;
				}
			}
		}
		return fallback == null ? null : new LocalizedTemplate(fallback, locales);
	}

	private static HistorySpec parseHistory(
		final JsonObject object,
		final String pointer,
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> fields,
		final AudienceSpec defaultAudience,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		JsonObject titleObject = reader.requiredObject("title");
		JsonObject bodyObject = reader.requiredObject("body");
		HistoryTaskSource taskSource = parseEnum(
			reader.optionalString("task_source", "none"),
			HistoryTaskSource.class,
			reader.pointer("task_source"),
			issues
		);
		String taskParameter = reader.optionalString("task_parameter");
		if (taskParameter != null) {
			validateLocalId(taskParameter, reader.pointer("task_parameter"), issues);
		}
		HistoryTimestampSource timestampSource = parseEnum(
			reader.optionalString("timestamp_source", "completion"),
			HistoryTimestampSource.class,
			reader.pointer("timestamp_source"),
			issues
		);
		JsonObject audienceObject = reader.optionalObject("audience");
		Set<String> savedFields = reader.optionalLocalIdSet("saved_fields", MAX_FIELDS);
		boolean replayAllowed = reader.optionalBoolean("replay_allowed", false);
		reader.finish();

		LocalizedTemplate title = parseLocalizedTemplate(
			titleObject,
			pointer + "/title",
			issues
		);
		LocalizedTemplate body = parseLocalizedTemplate(
			bodyObject,
			pointer + "/body",
			issues
		);
		if (title != null) {
			validateLocalizedTemplateFields(
				title,
				fields,
				Map.of(),
				pointer + "/title",
				issues
			);
		}
		if (body != null) {
			validateLocalizedTemplateFields(
				body,
				fields,
				Map.of(),
				pointer + "/body",
				issues
			);
		}
		if (taskSource == HistoryTaskSource.PARAMETER) {
			if (taskParameter == null) {
				issues.add(
					reader.pointer("task_parameter"),
					"MISSING_REQUIRED",
					"parameter task_source requires task_parameter"
				);
			} else {
				ParameterDefinition parameter = parameters.get(taskParameter);
				if (parameter == null) {
					issues.add(
						reader.pointer("task_parameter"),
						"MISSING_REFERENCE",
						"history task parameter " + taskParameter + " is not declared"
					);
				} else if (
					parameter.type() != ParameterType.IDENTIFIER
						|| (
							parameter.identifierKind() != IdentifierKind.ANY
								&& parameter.identifierKind() != IdentifierKind.TASK
						)
				) {
					issues.add(
						reader.pointer("task_parameter"),
						"TYPE_MISMATCH",
						"history task parameter must be an identifier parameter of task kind"
					);
				}
			}
		} else if (taskParameter != null) {
			issues.add(
				reader.pointer("task_parameter"),
				"CONTRADICTORY_VALUE",
				"task_parameter requires parameter task_source"
			);
		}
		for (String field : savedFields) {
			if (!fields.containsKey(field)) {
				issues.add(
					reader.pointer("saved_fields"),
					"MISSING_REFERENCE",
					"saved history field " + field + " is not a cue field"
				);
			}
		}
		if (title == null || body == null || taskSource == null || timestampSource == null) {
			return null;
		}
		return new HistorySpec(
			title,
			body,
			taskSource,
			Optional.ofNullable(taskParameter),
			timestampSource,
			audienceObject == null
				? defaultAudience
				: parseAudience(audienceObject, pointer + "/audience", issues),
			savedFields,
			replayAllowed
		);
	}

	private static StaticFallbackSpec parseStaticFallback(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return null;
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		TextChannel channel = parseEnum(
			reader.optionalString("channel", "chat"),
			TextChannel.class,
			reader.pointer("channel"),
			issues
		);
		JsonElement componentElement = reader.requiredElement("component");
		boolean satisfiesRequired = reader.optionalBoolean(
			"fallback_satisfies_required",
			false
		);
		reader.finish();
		RichComponent component = parseComponent(
			componentElement,
			pointer + "/component",
			issues,
			true,
			false
		);
		return channel == null || component == null
			? null
			: new StaticFallbackSpec(channel, component, satisfiesRequired);
	}

	private static List<AssetSpec> parseAssets(
		final JsonArray array,
		final String pointer,
		final IssueCollector issues
	) {
		if (array == null) {
			return List.of();
		}
		if (array.size() > MAX_ASSETS) {
			issues.add(pointer, "RESOURCE_LIMIT", "asset count exceeds " + MAX_ASSETS);
		}
		List<AssetSpec> result = new ArrayList<>();
		Set<String> identities = new HashSet<>();
		int limit = Math.min(array.size(), MAX_ASSETS);
		for (int index = 0; index < limit; index++) {
			String assetPointer = pointer + "/" + index;
			JsonElement element = array.get(index);
			if (!element.isJsonObject()) {
				issues.add(assetPointer, "TYPE_MISMATCH", "asset definition must be an object");
				continue;
			}
			ObjectReader reader = new ObjectReader(
				element.getAsJsonObject(),
				assetPointer,
				issues
			);
			MessageAssetType type = parseEnum(
				reader.requiredString("type"),
				MessageAssetType.class,
				reader.pointer("type"),
				issues
			);
			Identifier id = reader.requiredIdentifier("id");
			MissingAssetMode missing = parseEnum(
				reader.optionalString("missing", "silent"),
				MissingAssetMode.class,
				reader.pointer("missing"),
				issues
			);
			Identifier fallback = reader.optionalIdentifier("fallback");
			boolean preload = reader.optionalBoolean("preload", false);
			boolean requiredForStart = reader.optionalBoolean("required_for_start", false);
			reader.finish();
			validateMissingFallback(
				missing,
				fallback,
				reader.pointer("missing"),
				reader.pointer("fallback"),
				issues
			);
			if (missing == MissingAssetMode.BLOCK_START && !requiredForStart) {
				issues.add(
					reader.pointer("required_for_start"),
					"CONTRADICTORY_VALUE",
					"block_start assets must be required_for_start"
				);
			}
			if (type == null || id == null || missing == null) {
				continue;
			}
			String identity = type.name() + ":" + id;
			if (!identities.add(identity)) {
				issues.add(assetPointer, "DUPLICATE_VALUE", "asset is declared more than once");
			}
			result.add(
				new AssetSpec(
					type,
					id,
					missing,
					Optional.ofNullable(fallback),
					preload,
					requiredForStart
				)
			);
		}
		return List.copyOf(result);
	}

	private static SoftLimits parseSoftLimits(
		final JsonObject object,
		final String pointer,
		final IssueCollector issues
	) {
		if (object == null) {
			return SoftLimits.empty();
		}
		ObjectReader reader = new ObjectReader(object, pointer, issues);
		Integer maxVisibleGraphemes = boundedOptionalInteger(
			reader.optionalInteger("max_visible_graphemes"),
			1,
			32_768,
			reader.pointer("max_visible_graphemes"),
			issues
		);
		Integer maxLines = boundedOptionalInteger(
			reader.optionalInteger("max_lines"),
			1,
			256,
			reader.pointer("max_lines"),
			issues
		);
		Integer maxNodes = boundedOptionalInteger(
			reader.optionalInteger("max_nodes"),
			1,
			MAX_NODES,
			reader.pointer("max_nodes"),
			issues
		);
		Integer maxSoundNodes = boundedOptionalInteger(
			reader.optionalInteger("max_sound_nodes"),
			0,
			MAX_NODES,
			reader.pointer("max_sound_nodes"),
			issues
		);
		Integer maxCallbackNodes = boundedOptionalInteger(
			reader.optionalInteger("max_callback_nodes"),
			0,
			MAX_NODES,
			reader.pointer("max_callback_nodes"),
			issues
		);
		Integer maxVariants = boundedOptionalInteger(
			reader.optionalInteger("max_variants"),
			0,
			MAX_TOTAL_VARIANTS,
			reader.pointer("max_variants"),
			issues
		);
		Integer maxActiveInstances = boundedOptionalInteger(
			reader.optionalInteger("max_active_instances"),
			1,
			1024,
			reader.pointer("max_active_instances"),
			issues
		);
		Integer maxQueueDepth = boundedOptionalInteger(
			reader.optionalInteger("max_queue_depth"),
			0,
			1024,
			reader.pointer("max_queue_depth"),
			issues
		);
		TimeSpan maxTotalDuration = parseOptionalTime(
			reader,
			"max_total_duration",
			false,
			issues
		);
		Integer maxPersistedInstances = boundedOptionalInteger(
			reader.optionalInteger("max_persisted_instances"),
			0,
			1024,
			reader.pointer("max_persisted_instances"),
			issues
		);
		Integer maxPendingOffline = boundedOptionalInteger(
			reader.optionalInteger("max_pending_offline"),
			0,
			4096,
			reader.pointer("max_pending_offline"),
			issues
		);
		reader.finish();
		return new SoftLimits(
			Optional.ofNullable(maxVisibleGraphemes),
			Optional.ofNullable(maxLines),
			Optional.ofNullable(maxNodes),
			Optional.ofNullable(maxSoundNodes),
			Optional.ofNullable(maxCallbackNodes),
			Optional.ofNullable(maxVariants),
			Optional.ofNullable(maxActiveInstances),
			Optional.ofNullable(maxQueueDepth),
			Optional.ofNullable(maxTotalDuration),
			Optional.ofNullable(maxPersistedInstances),
			Optional.ofNullable(maxPendingOffline)
		);
	}

	private static void validateCueReferences(
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> fields,
		final List<CueNode> nodes,
		final Optional<HistorySpec> history,
		final CuePolicies policies,
		final SoftLimits softLimits,
		final IssueCollector issues
	) {
		Set<String> nodeIds = new LinkedHashSet<>();
		Map<String, String> dependencies = new LinkedHashMap<>();
		int expandedNodes = 0;
		int totalVariants = 0;
		int primaryCharacterTracks = 0;
		for (CueNode node : nodes) {
			nodeIds.add(node.id());
			expandedNodes += node.header().repeat().count();
			if (node.header().schedule() instanceof AfterNode after) {
				dependencies.put(node.id(), after.nodeId());
			}
			if (node instanceof TextNode textNode) {
				totalVariants += textNode.variants().size();
				boolean primaryNode = textNode.content().characterSoundRole()
					== CharacterSoundRole.PRIMARY;
				validateTextContentReferences(
					textNode.content(),
					textNode.id(),
					parameters,
					fields,
					"/nodes/" + escapePointer(textNode.id()),
					issues
				);
				for (int index = 0; index < textNode.variants().size(); index++) {
					TextContent content = textNode.variants().get(index).content();
					primaryNode |= content.characterSoundRole()
						== CharacterSoundRole.PRIMARY;
					validateTextContentReferences(
						content,
						textNode.id(),
						parameters,
						fields,
						"/nodes/" + escapePointer(textNode.id()) + "/variants/" + index,
						issues
					);
				}
				primaryCharacterTracks += primaryNode ? 1 : 0;
			}
		}
		if (expandedNodes > MAX_EXPANDED_NODES) {
			issues.add(
				"/nodes",
				"RESOURCE_LIMIT",
				"repeat expansion exceeds " + MAX_EXPANDED_NODES + " nodes"
			);
		}
		if (totalVariants > MAX_TOTAL_VARIANTS) {
			issues.add(
				"/nodes",
				"RESOURCE_LIMIT",
				"total text variants exceed " + MAX_TOTAL_VARIANTS
			);
		}
		long soundNodes = nodes.stream().filter(SoundNode.class::isInstance).count();
		long callbackNodes = nodes.stream().filter(CallbackNode.class::isInstance).count();
		int variantCount = totalVariants;
		softLimits.maxNodes().ifPresent(maximum -> {
			if (nodes.size() > maximum) {
				issues.add(
					"/soft_limits/max_nodes",
					"SOFT_LIMIT_EXCEEDED",
					"node count " + nodes.size() + " exceeds configured maximum " + maximum
				);
			}
		});
		softLimits.maxSoundNodes().ifPresent(maximum -> {
			if (soundNodes > maximum) {
				issues.add(
					"/soft_limits/max_sound_nodes",
					"SOFT_LIMIT_EXCEEDED",
					"sound node count " + soundNodes + " exceeds configured maximum " + maximum
				);
			}
		});
		softLimits.maxCallbackNodes().ifPresent(maximum -> {
			if (callbackNodes > maximum) {
				issues.add(
					"/soft_limits/max_callback_nodes",
					"SOFT_LIMIT_EXCEEDED",
					"callback node count "
						+ callbackNodes
						+ " exceeds configured maximum "
						+ maximum
				);
			}
		});
		softLimits.maxVariants().ifPresent(maximum -> {
			if (variantCount > maximum) {
				issues.add(
					"/soft_limits/max_variants",
					"SOFT_LIMIT_EXCEEDED",
					"variant count "
						+ variantCount
						+ " exceeds configured maximum "
						+ maximum
				);
			}
		});
		if (primaryCharacterTracks > 1) {
			issues.add(
				"/nodes",
				"MULTIPLE_PRIMARY_TRACKS",
				"message cue may declare only one primary character-sound track"
			);
		}
		for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
			if (!nodeIds.contains(dependency.getValue())) {
				issues.add(
					"/nodes/" + escapePointer(dependency.getKey()) + "/schedule/node",
					"MISSING_REFERENCE",
					"scheduled node " + dependency.getValue() + " is not declared"
				);
			}
			if (dependency.getKey().equals(dependency.getValue())) {
				issues.add(
					"/nodes/" + escapePointer(dependency.getKey()) + "/schedule/node",
					"CYCLIC_REFERENCE",
					"a node cannot be scheduled after itself"
				);
			}
		}
		validateScheduleCycles(dependencies, issues);
		for (Map.Entry<String, ParameterDefinition> entry : parameters.entrySet()) {
			for (String allowedNode : entry.getValue().allowedNodes()) {
				if (!nodeIds.contains(allowedNode)) {
					issues.add(
						"/parameters/" + escapePointer(entry.getKey()) + "/allowed_nodes",
						"MISSING_REFERENCE",
						"allowed node " + allowedNode + " is not declared"
					);
				}
			}
			if (
				entry.getValue().sensitive()
					&& policies.audience().evolution() != AudienceEvolution.LIVE_STRICT
			) {
				issues.add(
					"/parameters/" + escapePointer(entry.getKey()) + "/sensitive",
					"INSECURE_POLICY",
					"sensitive parameters require live_strict audience evolution"
				);
			}
		}
		validateParameterAllowedNodeClosure(
			parameters,
			fields,
			nodes,
			history,
			issues
		);
		for (String parameter : policies.concurrency().dedupeParameters()) {
			if (!parameters.containsKey(parameter)) {
				issues.add(
					"/policies/concurrency/dedupe_parameters",
					"MISSING_REFERENCE",
					"dedupe parameter " + parameter + " is not declared"
				);
			}
		}
		policies.concurrency().refreshKey().ifPresent(parameter -> {
			if (!parameters.containsKey(parameter)) {
				issues.add(
					"/policies/concurrency/refresh_key",
					"MISSING_REFERENCE",
					"refresh key parameter " + parameter + " is not declared"
				);
			}
		});
	}

	private static void validateTextContentReferences(
		final TextContent content,
		final String nodeId,
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> cueFields,
		final String pointer,
		final IssueCollector issues
	) {
		validateFieldSources(content.fields(), parameters, pointer + "/fields", issues);
		validateLocalizedTemplateFields(
			content.text(),
			cueFields,
			content.fields(),
			pointer + "/text",
			issues
		);
		if (content.speaker().kind() == SpeakerKind.PLAYER_PARAMETER) {
			String parameter = content.speaker().parameter().orElse(null);
			ParameterDefinition definition = parameter == null ? null : parameters.get(parameter);
			if (definition == null) {
				issues.add(
					pointer + "/speaker/parameter",
					"MISSING_REFERENCE",
					"speaker player parameter is not declared"
				);
			} else if (definition.type() != ParameterType.PLAYER) {
				issues.add(
					pointer + "/speaker/parameter",
					"TYPE_MISMATCH",
					"speaker parameter must have player type"
				);
			}
		}
	}

	private static void validateParameterAllowedNodeClosure(
		final Map<String, ParameterDefinition> parameters,
		final Map<String, DynamicFieldDefinition> fields,
		final List<CueNode> nodes,
		final Optional<HistorySpec> history,
		final IssueCollector issues
	) {
		MessageParameterUseGraph.UseGraph uses = MessageParameterUseGraph.compile(
			parameters,
			fields,
			nodes,
			history
		);
		for (Map.Entry<String, ParameterDefinition> entry : parameters.entrySet()) {
			String parameterId = entry.getKey();
			Set<String> allowedNodes = entry.getValue().allowedNodes();
			if (allowedNodes.isEmpty()) {
				continue;
			}
			String pointer = "/parameters/"
				+ escapePointer(parameterId)
				+ "/allowed_nodes";
			for (CueNode node : nodes) {
				if (
					uses.parametersForNode(node.id()).contains(parameterId)
						&& !allowedNodes.contains(node.id())
				) {
					issues.add(
						pointer,
						"UNAUTHORIZED_REFERENCE",
						"parameter "
							+ parameterId
							+ " is used by node "
							+ node.id()
							+ " but that node is not listed in allowed_nodes"
					);
				}
			}
			if (uses.historyParameters().contains(parameterId)) {
				/* History is not a cue node, so a node-scoped parameter is never authorized here. */
				issues.add(
					pointer,
					"UNAUTHORIZED_REFERENCE",
					"parameter "
						+ parameterId
						+ " restricts allowed_nodes and cannot be referenced by history"
				);
			}
		}
	}

	private static void validateLocalizedTemplateFields(
		final LocalizedTemplate localized,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Map<String, DynamicFieldDefinition> localFields,
		final String pointer,
		final IssueCollector issues
	) {
		validateTemplateFields(localized.fallback(), cueFields, localFields, pointer + "/fallback", issues);
		localized.locales().forEach(
			(locale, template) -> validateTemplateFields(
				template,
				cueFields,
				localFields,
				pointer + "/locales/" + escapePointer(locale),
				issues
			)
		);
	}

	private static void validateScheduleCycles(
		final Map<String, String> dependencies,
		final IssueCollector issues
	) {
		Set<String> complete = new HashSet<>();
		Set<String> visiting = new HashSet<>();
		for (String node : dependencies.keySet()) {
			validateScheduleNode(node, dependencies, visiting, complete, issues);
		}
	}

	private static void validateScheduleNode(
		final String node,
		final Map<String, String> dependencies,
		final Set<String> visiting,
		final Set<String> complete,
		final IssueCollector issues
	) {
		if (complete.contains(node)) {
			return;
		}
		if (!visiting.add(node)) {
			issues.add(
				"/nodes/" + escapePointer(node) + "/schedule/node",
				"CYCLIC_REFERENCE",
				"after_node schedules must form an acyclic graph"
			);
			return;
		}
		String dependency = dependencies.get(node);
		if (dependency != null && dependencies.containsKey(dependency)) {
			validateScheduleNode(dependency, dependencies, visiting, complete, issues);
		}
		visiting.remove(node);
		complete.add(node);
	}

	private static void validateFieldSources(
		final Map<String, DynamicFieldDefinition> fields,
		final Map<String, ParameterDefinition> parameters,
		final String pointer,
		final IssueCollector issues
	) {
		for (Map.Entry<String, DynamicFieldDefinition> entry : fields.entrySet()) {
			if (
				entry.getValue().source() instanceof ArgumentSource argument
					&& !parameters.containsKey(argument.parameter())
			) {
				issues.add(
					pointer + "/" + escapePointer(entry.getKey()) + "/source/argument",
					"MISSING_REFERENCE",
					"parameter " + argument.parameter() + " is not declared"
				);
			}
		}
	}

	private static void validateTemplateFields(
		final ComponentTemplate template,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Map<String, DynamicFieldDefinition> localFields,
		final String pointer,
		final IssueCollector issues
	) {
		for (int index = 0; index < template.parts().size(); index++) {
			ComponentPart part = template.parts().get(index);
			if (!(part instanceof FieldPart fieldPart)) {
				continue;
			}
			FieldReference reference = fieldPart.field();
			Map<String, DynamicFieldDefinition> available = reference.scope() == FieldScope.CUE
				? cueFields
				: localFields;
			if (!available.containsKey(reference.id())) {
				issues.add(
					pointer + "/parts/" + index + "/field/id",
					"MISSING_REFERENCE",
					serialized(reference.scope())
						+ " field "
						+ reference.id()
						+ " is not declared"
				);
			}
		}
	}

	private static boolean containsDynamicComponent(final JsonElement value) {
		if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
			return false;
		}
		if (value.isJsonArray()) {
			for (JsonElement element : value.getAsJsonArray()) {
				if (containsDynamicComponent(element)) {
					return true;
				}
			}
			return false;
		}
		JsonObject object = value.getAsJsonObject();
		for (String key : DYNAMIC_COMPONENT_KEYS) {
			if (object.has(key)) {
				return true;
			}
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (containsDynamicComponent(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private static RichComponent parseComponent(
		final JsonElement value,
		final String pointer,
		final IssueCollector issues,
		final boolean requireVisibleText,
		final boolean allowDynamicContent
	) {
		if (value == null || value.isJsonNull()) {
			issues.add(pointer, "TYPE_MISMATCH", "text component cannot be null");
			return null;
		}
		String canonical = canonical(value);
		if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_COMPONENT_BYTES) {
			issues.add(
				pointer,
				"RESOURCE_LIMIT",
				"canonical text component exceeds " + MAX_CANONICAL_COMPONENT_BYTES + " UTF-8 bytes"
			);
			return null;
		}
		if (!allowDynamicContent && containsDynamicComponent(value)) {
			issues.add(
				pointer,
				"DYNAMIC_STATIC_COMPONENT",
				"static component parts cannot use score, selector, or nbt; declare a FieldSource"
			);
			return null;
		}
		DataResult<Component> decoded;
		try {
			decoded = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value);
		} catch (RuntimeException error) {
			issues.add(pointer, "INVALID_TEXT_COMPONENT", safeMessage(error));
			return null;
		}
		Optional<Component> component = decoded.result();
		if (component.isEmpty()) {
			issues.add(
				pointer,
				"INVALID_TEXT_COMPONENT",
				decoded.error().map(DataResult.Error::message).orElse("invalid text component")
			);
			return null;
		}
		String plain = component.orElseThrow().getString();
		if (requireVisibleText && plain.isBlank()) {
			issues.add(pointer, "EMPTY_TEXT", "static text component must not be blank");
			return null;
		}
		return new RichComponent(canonical, plain);
	}

	private static TimeSpan parseRequiredTime(
		final ObjectReader reader,
		final String name,
		final boolean allowZero,
		final IssueCollector issues
	) {
		String value = reader.requiredString(name);
		return parseTime(value, reader.pointer(name), allowZero, issues);
	}

	private static TimeSpan parseOptionalTime(
		final ObjectReader reader,
		final String name,
		final boolean allowZero,
		final IssueCollector issues
	) {
		String value = reader.optionalString(name);
		return parseTime(value, reader.pointer(name), allowZero, issues);
	}

	private static TimeSpan parseTime(
		final String value,
		final String pointer,
		final boolean allowZero,
		final IssueCollector issues
	) {
		if (value == null) {
			return null;
		}
		Matcher matcher = TIME.matcher(value);
		if (!matcher.matches()) {
			issues.add(
				pointer,
				"INVALID_TIME",
				"time must include t, ms, or s, for example 10t, 80ms, or 1.5s"
			);
			return null;
		}
		String fraction = matcher.group(2);
		String unit = matcher.group(3);
		if (unit.equals("t") && fraction != null) {
			issues.add(pointer, "INVALID_TIME", "tick durations must be whole numbers");
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(matcher.group(1) + (fraction == null ? "" : "." + fraction));
			BigDecimal nanosPerUnit = switch (unit) {
				case "t" -> BigDecimal.valueOf(TimeSpan.NANOS_PER_TICK);
				case "ms" -> BigDecimal.valueOf(1_000_000L);
				case "s" -> BigDecimal.valueOf(1_000_000_000L);
				default -> throw new IllegalStateException("unexpected time unit");
			};
			BigInteger exactNanos = amount.multiply(nanosPerUnit).toBigIntegerExact();
			if (exactNanos.signum() < 0 || exactNanos.compareTo(BigInteger.valueOf(MAX_TIME_NANOS)) > 0) {
				issues.add(pointer, "OUT_OF_RANGE", "time exceeds the supported seven-day range");
				return null;
			}
			long nanos = exactNanos.longValueExact();
			if (!allowZero && nanos == 0L) {
				issues.add(pointer, "OUT_OF_RANGE", "time must be greater than zero");
				return null;
			}
			return new TimeSpan(nanos);
		} catch (ArithmeticException | NumberFormatException error) {
			issues.add(pointer, "INVALID_TIME", "time cannot be represented exactly");
			return null;
		}
	}

	private static void validateCharacterInterval(
		final TimeSpan interval,
		final String pointer,
		final IssueCollector issues
	) {
		if (interval != null && interval.nanoseconds() < MIN_CHARACTER_INTERVAL_NANOS) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"character interval must be at least 1ms"
			);
		}
	}

	private static int validatePriority(
		final int priority,
		final String pointer,
		final IssueCollector issues
	) {
		if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"priority must be between " + MIN_PRIORITY + " and " + MAX_PRIORITY
			);
			return Math.max(MIN_PRIORITY, Math.min(priority, MAX_PRIORITY));
		}
		return priority;
	}

	private static void validateLocalId(
		final String value,
		final String pointer,
		final IssueCollector issues
	) {
		if (
			value == null
				|| value.length() > MAX_LOCAL_ID_LENGTH
				|| !LOCAL_ID.matcher(value).matches()
		) {
			issues.add(
				pointer,
				"INVALID_LOCAL_ID",
				"local id must match " + LOCAL_ID.pattern()
			);
		}
	}

	private static void validateColor(
		final String value,
		final String pointer,
		final IssueCollector issues
	) {
		if (!HEX_COLOR.matcher(value).matches() && !NAMED_COLORS.contains(value)) {
			issues.add(
				pointer,
				"INVALID_COLOR",
				"color must be a #RRGGBB value or a vanilla named text color"
			);
		}
	}

	private static float boundedFloat(
		final double value,
		final double minimum,
		final double maximum,
		final String pointer,
		final IssueCollector issues
	) {
		if (!Double.isFinite(value) || value < minimum || value > maximum) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"value must be finite and between " + minimum + " and " + maximum
			);
			return (float)Math.max(minimum, Math.min(Double.isFinite(value) ? value : minimum, maximum));
		}
		return (float)value;
	}

	private static int boundedInteger(
		final int value,
		final int minimum,
		final int maximum,
		final String pointer,
		final IssueCollector issues
	) {
		if (value < minimum || value > maximum) {
			issues.add(
				pointer,
				"OUT_OF_RANGE",
				"value must be between " + minimum + " and " + maximum
			);
			return Math.max(minimum, Math.min(value, maximum));
		}
		return value;
	}

	private static Integer boundedOptionalInteger(
		final Integer value,
		final int minimum,
		final int maximum,
		final String pointer,
		final IssueCollector issues
	) {
		return value == null
			? null
			: boundedInteger(value, minimum, maximum, pointer, issues);
	}

	private static void validateMissingFallback(
		final MissingAssetMode missing,
		final Identifier fallback,
		final String missingPointer,
		final String fallbackPointer,
		final IssueCollector issues
	) {
		if (missing == MissingAssetMode.FALLBACK && fallback == null) {
			issues.add(
				fallbackPointer,
				"MISSING_REQUIRED",
				"fallback is required when missing is fallback"
			);
		}
		if (missing != null && missing != MissingAssetMode.FALLBACK && fallback != null) {
			issues.add(
				fallbackPointer,
				"CONTRADICTORY_VALUE",
				"fallback is only valid when missing is fallback"
			);
		}
	}

	private static <T extends Enum<T>> T parseEnum(
		final String value,
		final Class<T> type,
		final String pointer,
		final IssueCollector issues
	) {
		if (value == null) {
			return null;
		}
		String normalized = value.toUpperCase(Locale.ROOT);
		try {
			return Enum.valueOf(type, normalized);
		} catch (IllegalArgumentException error) {
			issues.add(
				pointer,
				"INVALID_ENUM",
				"unsupported " + type.getSimpleName() + " value " + value
			);
			return null;
		}
	}

	private static String serialized(final Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static TimeSpan defaultTime(final TimeSpan value) {
		return value == null ? ZERO_TIME : value;
	}

	private static void checkDocumentSize(
		final String canonical,
		final IssueCollector issues
	) {
		if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_DOCUMENT_BYTES) {
			issues.add(
				"",
				"RESOURCE_LIMIT",
				"canonical message document exceeds "
					+ MAX_CANONICAL_DOCUMENT_BYTES
					+ " UTF-8 bytes"
			);
		}
	}

	private static JsonObject parseRoot(
		final String json,
		final IssueCollector issues
	) {
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
			return value.getAsJsonObject();
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

	private static String canonical(final JsonElement value) {
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

	private static Identifier parseIdentifier(final String value) {
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
		return value != null
			&& value.isJsonPrimitive()
			&& value.getAsJsonPrimitive().isString();
	}

	private static boolean isBoolean(final JsonElement value) {
		return value != null
			&& value.isJsonPrimitive()
			&& value.getAsJsonPrimitive().isBoolean();
	}

	private static Long integralLong(final JsonElement value) {
		if (
			value == null
				|| !value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isNumber()
		) {
			return null;
		}
		try {
			return value.getAsBigDecimal().longValueExact();
		} catch (ArithmeticException | NumberFormatException error) {
			return null;
		}
	}

	private static Double finiteNumber(final JsonElement value) {
		if (
			value == null
				|| !value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isNumber()
		) {
			return null;
		}
		try {
			double result = value.getAsDouble();
			return Double.isFinite(result) ? result : null;
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

	public record Issue(String path, String code, String message) {
		public Issue {
			path = path == null ? "" : truncate(path, 512);
			code = code == null || code.isBlank() ? "UNKNOWN" : truncate(code, 64);
			message = message == null || message.isBlank()
				? "unspecified message definition problem"
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
				throw new IllegalArgumentException(
					"total issue count cannot be smaller than retained details"
				);
			}
		}

		public ParseResult(final Optional<T> value, final List<Issue> issues) {
			this(value, issues, issues.size());
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
			if (result.length() > 1024) {
				this.issues.add(
					this.pointer(name),
					"OUT_OF_RANGE",
					"string exceeds 1024 characters"
				);
				return null;
			}
			return result;
		}

		private Integer requiredInteger(final String name) {
			JsonElement value = this.requiredElement(name);
			return value == null ? null : this.readInteger(name, value);
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
				this.issues.add(
					this.pointer(name),
					"TYPE_MISMATCH",
					"value must be a 32-bit integer"
				);
				return null;
			}
			return parsed.intValue();
		}

		private boolean optionalBoolean(final String name, final boolean fallback) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return fallback;
			}
			if (!isBoolean(value)) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be a boolean");
				return fallback;
			}
			return value.getAsBoolean();
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

		private double optionalDouble(final String name, final double fallback) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return fallback;
			}
			Double parsed = finiteNumber(value);
			if (parsed == null) {
				this.issues.add(
					this.pointer(name),
					"TYPE_MISMATCH",
					"value must be a finite number"
				);
				return fallback;
			}
			return parsed;
		}

		private Double optionalDouble(final String name) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return null;
			}
			Double parsed = finiteNumber(value);
			if (parsed == null) {
				this.issues.add(
					this.pointer(name),
					"TYPE_MISMATCH",
					"value must be a finite number"
				);
			}
			return parsed;
		}

		private JsonObject requiredObject(final String name) {
			JsonElement value = this.requiredElement(name);
			return this.readObject(name, value);
		}

		private JsonObject optionalObject(final String name) {
			JsonElement value = this.optionalElement(name);
			return value == null ? null : this.readObject(name, value);
		}

		private JsonObject readObject(final String name, final JsonElement value) {
			if (value == null) {
				return null;
			}
			if (!value.isJsonObject()) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be an object");
				return null;
			}
			return value.getAsJsonObject();
		}

		private JsonArray requiredArray(final String name) {
			JsonElement value = this.requiredElement(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonArray()) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be an array");
				return null;
			}
			return value.getAsJsonArray();
		}

		private JsonArray optionalArray(final String name) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return null;
			}
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

		private Set<Identifier> optionalIdentifierSet(final String name) {
			JsonElement value = this.optionalElement(name);
			if (value == null) {
				return Set.of();
			}
			if (!value.isJsonArray()) {
				this.issues.add(this.pointer(name), "TYPE_MISMATCH", "value must be an array");
				return Set.of();
			}
			JsonArray array = value.getAsJsonArray();
			if (array.size() > 128) {
				this.issues.add(
					this.pointer(name),
					"RESOURCE_LIMIT",
					"identifier list exceeds 128 entries"
				);
			}
			Set<Identifier> result = new LinkedHashSet<>();
			int limit = Math.min(array.size(), 128);
			for (int index = 0; index < limit; index++) {
				JsonElement entry = array.get(index);
				String entryPointer = this.pointer(name) + "/" + index;
				if (!isString(entry)) {
					this.issues.add(entryPointer, "TYPE_MISMATCH", "identifier must be a string");
					continue;
				}
				Identifier parsed = parseIdentifier(entry.getAsString());
				if (parsed == null) {
					this.issues.add(
						entryPointer,
						"INVALID_IDENTIFIER",
						"identifier must include an explicit namespace"
					);
					continue;
				}
				if (!result.add(parsed)) {
					this.issues.add(entryPointer, "DUPLICATE_VALUE", "identifier is duplicated");
				}
			}
			return Set.copyOf(result);
		}

		private Set<String> optionalLocalIdSet(final String name, final int maximum) {
			JsonArray array = this.optionalArray(name);
			if (array == null) {
				return Set.of();
			}
			if (array.size() > maximum) {
				this.issues.add(
					this.pointer(name),
					"RESOURCE_LIMIT",
					"local-id list exceeds " + maximum + " entries"
				);
			}
			Set<String> result = new LinkedHashSet<>();
			int limit = Math.min(array.size(), maximum);
			for (int index = 0; index < limit; index++) {
				JsonElement entry = array.get(index);
				String entryPointer = this.pointer(name) + "/" + index;
				if (!isString(entry)) {
					this.issues.add(entryPointer, "TYPE_MISMATCH", "local id must be a string");
					continue;
				}
				String value = entry.getAsString();
				validateLocalId(value, entryPointer, this.issues);
				if (!result.add(value)) {
					this.issues.add(entryPointer, "DUPLICATE_VALUE", "local id is duplicated");
				}
			}
			return Set.copyOf(result);
		}

		private void finish() {
			for (String name : this.object.keySet()) {
				if (!this.consumed.contains(name)) {
					this.issues.add(
						this.pointer(name),
						"UNKNOWN_KEY",
						"unknown message definition key " + name
					);
				}
			}
		}
	}

	private static String truncate(final String value, final int maximum) {
		return value.length() <= maximum ? value : value.substring(0, maximum);
	}
}
