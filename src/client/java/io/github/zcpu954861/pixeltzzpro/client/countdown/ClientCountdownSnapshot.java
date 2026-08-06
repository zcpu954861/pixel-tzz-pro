package io.github.zcpu954861.pixeltzzpro.client.countdown;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Immutable, countdown-only client projection. It deliberately has no generic HUD node model. */
public record ClientCountdownSnapshot(
	Version version,
	UUID countdownInstanceId,
	Purpose purpose,
	long totalTicks,
	State state,
	boolean inventoryBlocked,
	Timing timing,
	Text text,
	Presentation presentation,
	Optional<Checkpoint> checkpoint,
	long receivedAtNanos
) {
	public static final int FORMAT_VERSION = 1;
	private static final int MAX_TEXT_BYTES = 16 * 1024;
	private static final int MAX_TEXT_CHARACTERS = 256;
	private static final Set<String> REPLACE_KEYS = Set.of(
		"format_version",
		"surface",
		"countdown_instance_id",
		"purpose",
		"total_ticks",
		"state",
		"inventory_blocked",
		"timing",
		"text",
		"presentation",
		"checkpoint"
	);
	private static final Set<String> PATCH_KEYS = Set.of(
		"format_version",
		"surface",
		"state",
		"inventory_blocked",
		"timing",
		"checkpoint"
	);

	public ClientCountdownSnapshot {
		version = Objects.requireNonNull(version, "version");
		countdownInstanceId = Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
		purpose = Objects.requireNonNull(purpose, "purpose");
		state = Objects.requireNonNull(state, "state");
		timing = Objects.requireNonNull(timing, "timing");
		text = Objects.requireNonNull(text, "text");
		presentation = Objects.requireNonNull(presentation, "presentation");
		checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
		if (totalTicks <= 0L || totalTicks > 72_000L) {
			throw new IllegalArgumentException("countdown totalTicks must be between 1 and 72000");
		}
		if (timing.baseRemainingTicks() > totalTicks + 20.0D) {
			throw new IllegalArgumentException("countdown remaining time exceeds its total");
		}
		if (receivedAtNanos < 0L) {
			throw new IllegalArgumentException("countdown receive time cannot be negative");
		}
	}

	public static ClientCountdownSnapshot parseReplace(
		final Version version,
		final byte[] json,
		final long receivedAtNanos
	) {
		Objects.requireNonNull(json, "json");
		JsonObject root = object(JsonParser.parseString(new String(json, StandardCharsets.UTF_8)), "root");
		requireKnownKeys(root, REPLACE_KEYS, "root");
		requireEnvelope(root);
		UUID instance = UUID.fromString(string(root, "countdown_instance_id"));
		Purpose purpose = enumValue(Purpose.class, string(root, "purpose"), "purpose");
		long totalTicks = integer(root, "total_ticks");
		State state = enumValue(State.class, string(root, "state"), "state");
		boolean inventoryBlocked = bool(root, "inventory_blocked");
		Timing timing = timing(object(root.get("timing"), "timing"));
		Text text = text(object(root.get("text"), "text"));
		Presentation presentation = presentation(object(root.get("presentation"), "presentation"));
		Optional<Checkpoint> checkpoint = optionalCheckpoint(root.get("checkpoint"));
		return new ClientCountdownSnapshot(
			version,
			instance,
			purpose,
			totalTicks,
			state,
			inventoryBlocked,
			timing,
			text,
			presentation,
			checkpoint,
			receivedAtNanos
		);
	}

	public ClientCountdownSnapshot applyPatch(
		final Version nextVersion,
		final byte[] json,
		final long nextReceivedAtNanos
	) {
		Objects.requireNonNull(json, "json");
		JsonObject root = object(JsonParser.parseString(new String(json, StandardCharsets.UTF_8)), "root");
		requireKnownKeys(root, PATCH_KEYS, "root");
		requireEnvelope(root);
		State nextState = root.has("state")
			? enumValue(State.class, string(root, "state"), "state")
			: this.state;
		boolean nextInventoryBlocked = root.has("inventory_blocked")
			? bool(root, "inventory_blocked")
			: this.inventoryBlocked;
		Timing nextTiming = root.has("timing")
			? timing(object(root.get("timing"), "timing"))
			: this.timing;
		Optional<Checkpoint> nextCheckpoint = root.has("checkpoint")
			? optionalCheckpoint(root.get("checkpoint"))
			: this.checkpoint;
		return new ClientCountdownSnapshot(
			nextVersion,
			this.countdownInstanceId,
			this.purpose,
			this.totalTicks,
			nextState,
			nextInventoryBlocked,
			nextTiming,
			this.text,
			this.presentation,
			nextCheckpoint,
			nextReceivedAtNanos
		);
	}

	public ClientCountdownSnapshot withReceiveTime(final long value) {
		return new ClientCountdownSnapshot(
			this.version,
			this.countdownInstanceId,
			this.purpose,
			this.totalTicks,
			this.state,
			this.inventoryBlocked,
			this.timing,
			this.text,
			this.presentation,
			this.checkpoint,
			value
		);
	}

	private static void requireEnvelope(final JsonObject root) {
		if (integer(root, "format_version") != FORMAT_VERSION) {
			throw new IllegalArgumentException("unsupported countdown frame format");
		}
		if (!"countdown".equals(string(root, "surface"))) {
			throw new IllegalArgumentException("countdown frame has the wrong surface");
		}
	}

	private static Timing timing(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of("server_tick", "base_remaining_ticks", "rate", "paused"),
			"timing"
		);
		return new Timing(
			integer(object, "server_tick"),
			number(object, "base_remaining_ticks"),
			number(object, "rate"),
			bool(object, "paused")
		);
	}

	private static Text text(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of("title", "prefix", "suffix", "waiting", "paused", "complete"),
			"text"
		);
		return new Text(
			component(object.get("title"), "text.title"),
			optionalComponent(object.get("prefix"), "text.prefix"),
			optionalComponent(object.get("suffix"), "text.suffix"),
			component(object.get("waiting"), "text.waiting"),
			component(object.get("paused"), "text.paused"),
			component(object.get("complete"), "text.complete")
		);
	}

	private static Presentation presentation(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of("time", "panel", "progress", "digits", "enter", "exit", "completion"),
			"presentation"
		);
		return new Presentation(
			timePresentation(object(object.get("time"), "presentation.time")),
			panelPresentation(object(object.get("panel"), "presentation.panel")),
			progressPresentation(object(object.get("progress"), "presentation.progress")),
			digitPresentation(object(object.get("digits"), "presentation.digits")),
			surfaceMotion(object(object.get("enter"), "presentation.enter")),
			surfaceMotion(object(object.get("exit"), "presentation.exit")),
			completionPresentation(object(object.get("completion"), "presentation.completion"))
		);
	}

	private static TimePresentation timePresentation(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of("format", "precision", "leading_zero", "separator", "color", "scale", "shadow"),
			"presentation.time"
		);
		return new TimePresentation(
			enumValue(TimeFormat.class, string(object, "format"), "time.format"),
			enumValue(TimePrecision.class, string(object, "precision"), "time.precision"),
			bool(object, "leading_zero"),
			string(object, "separator"),
			color(object, "color"),
			number(object, "scale"),
			bool(object, "shadow")
		);
	}

	private static PanelPresentation panelPresentation(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of(
				"background_color",
				"border_color",
				"accent_color",
				"opacity",
				"padding_x",
				"padding_y",
				"gap_above_action_bar",
				"max_width"
			),
			"presentation.panel"
		);
		return new PanelPresentation(
			color(object, "background_color"),
			color(object, "border_color"),
			color(object, "accent_color"),
			number(object, "opacity"),
			(int)integer(object, "padding_x"),
			(int)integer(object, "padding_y"),
			(int)integer(object, "gap_above_action_bar"),
			(int)integer(object, "max_width")
		);
	}

	private static ProgressPresentation progressPresentation(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of("mode", "color", "track_color", "thickness", "smooth"),
			"presentation.progress"
		);
		return new ProgressPresentation(
			enumValue(ProgressMode.class, string(object, "mode"), "progress.mode"),
			color(object, "color"),
			color(object, "track_color"),
			(int)integer(object, "thickness"),
			bool(object, "smooth")
		);
	}

	private static DigitPresentation digitPresentation(final JsonObject object) {
		requireKnownKeys(
			object,
			Set.of(
				"transition",
				"direction",
				"duration_ms",
				"distance",
				"easing",
				"stagger_ms",
				"animate_fractional",
				"incoming_color",
				"outgoing_color",
				"incoming_alpha",
				"outgoing_alpha"
			),
			"presentation.digits"
		);
		return new DigitPresentation(
			enumValue(DigitTransition.class, string(object, "transition"), "digits.transition"),
			enumValue(RollDirection.class, string(object, "direction"), "digits.direction"),
			(int)integer(object, "duration_ms"),
			(int)integer(object, "distance"),
			enumValue(Easing.class, string(object, "easing"), "digits.easing"),
			(int)integer(object, "stagger_ms"),
			bool(object, "animate_fractional"),
			color(object, "incoming_color"),
			color(object, "outgoing_color"),
			number(object, "incoming_alpha"),
			number(object, "outgoing_alpha")
		);
	}

	private static SurfaceMotion surfaceMotion(final JsonObject object) {
		requireKnownKeys(object, Set.of("mode", "duration_ms", "easing"), "surface motion");
		return new SurfaceMotion(
			enumValue(SurfaceTransition.class, string(object, "mode"), "motion.mode"),
			(int)integer(object, "duration_ms"),
			enumValue(Easing.class, string(object, "easing"), "motion.easing")
		);
	}

	private static CompletionPresentation completionPresentation(final JsonObject object) {
		requireKnownKeys(object, Set.of("hold_ms", "color"), "presentation.completion");
		return new CompletionPresentation(
			(int)integer(object, "hold_ms"),
			color(object, "color")
		);
	}

	private static Optional<Checkpoint> optionalCheckpoint(final JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return Optional.empty();
		}
		JsonObject object = object(value, "checkpoint");
		requireKnownKeys(object, Set.of("id", "text", "color", "scale"), "checkpoint");
		return Optional.of(new Checkpoint(
			string(object, "id"),
			component(object.get("text"), "checkpoint.text"),
			color(object, "color"),
			number(object, "scale")
		));
	}

	private static Optional<Component> optionalComponent(final JsonElement value, final String field) {
		return value == null || value.isJsonNull()
			? Optional.empty()
			: Optional.of(component(value, field));
	}

	private static Component component(final JsonElement value, final String field) {
		if (value == null || value.isJsonNull()) {
			throw new IllegalArgumentException(field + " is required");
		}
		if (value.toString().getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
			throw new IllegalArgumentException(field + " is too large");
		}
		Component result = ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, value)
			.result()
			.orElseThrow(() -> new IllegalArgumentException(field + " is not a valid component"));
		if (result.getString().codePointCount(0, result.getString().length()) > MAX_TEXT_CHARACTERS) {
			throw new IllegalArgumentException(field + " contains too much visible text");
		}
		return result.copy();
	}

	private static JsonObject object(final JsonElement value, final String field) {
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(field + " must be an object");
		}
		return value.getAsJsonObject();
	}

	private static String string(final JsonObject object, final String field) {
		if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
			throw new IllegalArgumentException(field + " must be a string");
		}
		return object.get(field).getAsString();
	}

	private static boolean bool(final JsonObject object, final String field) {
		if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
			throw new IllegalArgumentException(field + " must be a boolean");
		}
		return object.get(field).getAsBoolean();
	}

	private static long integer(final JsonObject object, final String field) {
		if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
			throw new IllegalArgumentException(field + " must be an integer");
		}
		double value = object.get(field).getAsDouble();
		if (!Double.isFinite(value) || value != Math.rint(value)) {
			throw new IllegalArgumentException(field + " must be an integer");
		}
		return object.get(field).getAsLong();
	}

	private static double number(final JsonObject object, final String field) {
		if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
			throw new IllegalArgumentException(field + " must be a number");
		}
		double result = object.get(field).getAsDouble();
		if (!Double.isFinite(result)) {
			throw new IllegalArgumentException(field + " must be finite");
		}
		return result;
	}

	private static int color(final JsonObject object, final String field) {
		long value = integer(object, field);
		if (value < Integer.MIN_VALUE || value > 0xFFFF_FFFFL) {
			throw new IllegalArgumentException(field + " is not an ARGB color");
		}
		return (int)value;
	}

	private static <E extends Enum<E>> E enumValue(
		final Class<E> type,
		final String value,
		final String field
	) {
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException error) {
			throw new IllegalArgumentException(field + " has an unsupported value", error);
		}
	}

	private static void requireKnownKeys(
		final JsonObject object,
		final Set<String> allowed,
		final String field
	) {
		for (String key : object.keySet()) {
			if (!allowed.contains(key)) {
				throw new IllegalArgumentException(field + " contains unknown key " + key);
			}
		}
	}

	public record Version(
		UUID gameInstanceId,
		long definitionGeneration,
		long epoch,
		long contextVersion,
		long sequence
	) {
		public Version {
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			if (definitionGeneration < 0L || epoch < 0L || contextVersion < 0L || sequence < 0L) {
				throw new IllegalArgumentException("countdown version fields must be non-negative");
			}
		}
	}

	public enum State {
		WAITING,
		RUNNING,
		PAUSED,
		COMPLETING,
		COMPLETED,
		CANCELING,
		CANCELED;

		public boolean terminal() {
			return this == COMPLETED || this == CANCELED;
		}
	}

	/** V3C reserves the wire discriminator now; this milestone specializes only opening countdowns. */
	public enum Purpose {
		OPENING
	}

	public record Timing(long serverTick, double baseRemainingTicks, double rate, boolean paused) {
		public Timing {
			if (serverTick < 0L) {
				throw new IllegalArgumentException("countdown server tick cannot be negative");
			}
			if (!Double.isFinite(baseRemainingTicks) || baseRemainingTicks < 0.0D) {
				throw new IllegalArgumentException("countdown remaining ticks are invalid");
			}
			if (!Double.isFinite(rate) || rate < -4.0D || rate > 4.0D) {
				throw new IllegalArgumentException("countdown rate is outside its safe range");
			}
		}
	}

	public record Text(
		Component title,
		Optional<Component> prefix,
		Optional<Component> suffix,
		Component waiting,
		Component paused,
		Component complete
	) {
		public Text {
			title = Objects.requireNonNull(title, "title").copy();
			prefix = copy(prefix);
			suffix = copy(suffix);
			waiting = Objects.requireNonNull(waiting, "waiting").copy();
			paused = Objects.requireNonNull(paused, "paused").copy();
			complete = Objects.requireNonNull(complete, "complete").copy();
		}

		private static Optional<Component> copy(final Optional<Component> value) {
			return Objects.requireNonNull(value, "optional component").map(Component::copy);
		}
	}

	public enum TimeFormat { SECONDS, MM_SS }
	public enum TimePrecision {
		SECONDS(0), TENTHS(1), HUNDREDTHS(2);
		private final int decimals;
		TimePrecision(final int decimals) { this.decimals = decimals; }
		public int decimals() { return this.decimals; }
	}
	public enum ProgressMode { NONE, LINE }
	public enum DigitTransition { ROLL, FADE, INSTANT }
	public enum RollDirection { DOWN, UP }
	public enum Easing { LINEAR, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC }
	public enum SurfaceTransition { FADE, SLIDE, INSTANT }

	public record TimePresentation(
		TimeFormat format,
		TimePrecision precision,
		boolean leadingZero,
		String separator,
		int color,
		double scale,
		boolean shadow
	) {
		public TimePresentation {
			format = Objects.requireNonNull(format, "format");
			precision = Objects.requireNonNull(precision, "precision");
			separator = Objects.requireNonNull(separator, "separator");
			int count = separator.codePointCount(0, separator.length());
			if (count < 1 || count > 2 || !Double.isFinite(scale) || scale < 0.75D || scale > 1.5D) {
				throw new IllegalArgumentException("countdown time presentation is outside its safe range");
			}
		}
	}

	public record PanelPresentation(
		int backgroundColor,
		int borderColor,
		int accentColor,
		double opacity,
		int paddingX,
		int paddingY,
		int gapAboveActionBar,
		int maxWidth
	) {
		public PanelPresentation {
			if (!Double.isFinite(opacity) || opacity < 0.2D || opacity > 1.0D
				|| paddingX < 2 || paddingX > 24 || paddingY < 1 || paddingY > 12
				|| gapAboveActionBar < 2 || gapAboveActionBar > 24
				|| maxWidth < 96 || maxWidth > 480) {
				throw new IllegalArgumentException("countdown panel presentation is outside its safe range");
			}
		}
	}

	public record ProgressPresentation(
		ProgressMode mode,
		int color,
		int trackColor,
		int thickness,
		boolean smooth
	) {
		public ProgressPresentation {
			mode = Objects.requireNonNull(mode, "mode");
			if (thickness < 1 || thickness > 4) {
				throw new IllegalArgumentException("countdown progress thickness is invalid");
			}
		}
	}

	public record DigitPresentation(
		DigitTransition transition,
		RollDirection direction,
		int durationMs,
		int distance,
		Easing easing,
		int staggerMs,
		boolean animateFractional,
		int incomingColor,
		int outgoingColor,
		double incomingAlpha,
		double outgoingAlpha
	) {
		public DigitPresentation {
			transition = Objects.requireNonNull(transition, "transition");
			direction = Objects.requireNonNull(direction, "direction");
			easing = Objects.requireNonNull(easing, "easing");
			if (durationMs < 0 || durationMs > 2_000 || distance < 4 || distance > 24
				|| staggerMs < 0 || staggerMs > 100 || !Double.isFinite(incomingAlpha)
				|| !Double.isFinite(outgoingAlpha) || incomingAlpha < 0.2D || incomingAlpha > 1.0D
				|| outgoingAlpha < 0.0D || outgoingAlpha > 1.0D) {
				throw new IllegalArgumentException("countdown digit presentation is outside its safe range");
			}
		}
	}

	public record SurfaceMotion(SurfaceTransition mode, int durationMs, Easing easing) {
		public SurfaceMotion {
			mode = Objects.requireNonNull(mode, "mode");
			easing = Objects.requireNonNull(easing, "easing");
			if (durationMs < 0 || durationMs > 2_000) {
				throw new IllegalArgumentException("countdown surface duration is invalid");
			}
		}
	}

	public record CompletionPresentation(int holdMs, int color) {
		public CompletionPresentation {
			if (holdMs < 0 || holdMs > 5_000) {
				throw new IllegalArgumentException("countdown completion hold is invalid");
			}
		}
	}

	public record Presentation(
		TimePresentation time,
		PanelPresentation panel,
		ProgressPresentation progress,
		DigitPresentation digits,
		SurfaceMotion enter,
		SurfaceMotion exit,
		CompletionPresentation completion
	) {
		public Presentation {
			time = Objects.requireNonNull(time, "time");
			panel = Objects.requireNonNull(panel, "panel");
			progress = Objects.requireNonNull(progress, "progress");
			digits = Objects.requireNonNull(digits, "digits");
			enter = Objects.requireNonNull(enter, "enter");
			exit = Objects.requireNonNull(exit, "exit");
			completion = Objects.requireNonNull(completion, "completion");
		}
	}

	public record Checkpoint(String id, Component text, int color, double scale) {
		public Checkpoint {
			id = Objects.requireNonNull(id, "id");
			text = Objects.requireNonNull(text, "text").copy();
			if (id.isBlank() || id.length() > 128 || !Double.isFinite(scale) || scale < 0.75D || scale > 1.5D) {
				throw new IllegalArgumentException("countdown checkpoint presentation is invalid");
			}
		}
	}
}
