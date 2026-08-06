package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/** Strict data-pack model for the one authoritative countdown surface. */
public final class CountdownDefinitions {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public static final int MAX_COUNTDOWNS = 128;
	public static final int MAX_COUNTDOWN_TICKS = 72_000;
	public static final int MAX_CHECKPOINTS = 64;
	public static final int MAX_CALLBACKS_PER_SLOT = 64;
	public static final int MAX_CANONICAL_DOCUMENT_BYTES = 256 * 1024;
	public static final int MAX_TEXT_COMPONENT_BYTES = 16 * 1024;
	public static final int MAX_TEXT_PLAIN_CHARACTERS = 256;

	private CountdownDefinitions() {
	}

	public enum TimeFormat {
		SECONDS,
		MM_SS
	}

	public enum TimePrecision {
		SECONDS(0),
		TENTHS(1),
		HUNDREDTHS(2);

		private final int decimalPlaces;

		TimePrecision(final int decimalPlaces) {
			this.decimalPlaces = decimalPlaces;
		}

		public int decimalPlaces() {
			return this.decimalPlaces;
		}
	}

	public enum ProgressMode {
		NONE,
		LINE
	}

	public enum DigitTransition {
		ROLL,
		FADE,
		INSTANT
	}

	public enum RollDirection {
		DOWN,
		UP
	}

	public enum Easing {
		LINEAR,
		EASE_OUT_CUBIC,
		EASE_IN_OUT_CUBIC
	}

	public enum SurfaceTransition {
		FADE,
		SLIDE,
		INSTANT
	}

	public enum DisconnectAction {
		PAUSE,
		CANCEL,
		CONTINUE
	}

	/**
	 * Additional recipients for the dedicated countdown surface.
	 *
	 * <p>Frozen participants are intentionally absent from this policy: they are always authorized
	 * and a data pack cannot hide the opening countdown from them.</p>
	 */
	public record DisplayAudience(boolean includeHost, boolean includeObservers) {
		public static DisplayAudience safeDefault() {
			return new DisplayAudience(true, false);
		}
	}

	public enum FreezeMode {
		FREEZE,
		ALLOW
	}

	public enum BlockMode {
		BLOCK,
		ALLOW
	}

	public enum DamageMode {
		IMMUNE,
		ALLOW
	}

	public enum AllowOnly {
		ALLOW
	}

	public enum CountdownCallbackSlot {
		START,
		PAUSE,
		RESUME,
		CANCEL,
		COMPLETE
	}

	public enum CountdownCallbackScope {
		GLOBAL,
		EACH_PARTICIPANT
	}

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
			if (separator.isEmpty() || separator.codePointCount(0, separator.length()) > 2) {
				throw new IllegalArgumentException("countdown time separator must contain 1..2 characters");
			}
			if (!Double.isFinite(scale) || scale < 0.75D || scale > 1.50D) {
				throw new IllegalArgumentException("countdown time scale must be between 0.75 and 1.50");
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
			if (!Double.isFinite(opacity) || opacity < 0.20D || opacity > 1.0D) {
				throw new IllegalArgumentException("countdown panel opacity must be between 0.20 and 1.0");
			}
			if (paddingX < 2 || paddingX > 24 || paddingY < 1 || paddingY > 12) {
				throw new IllegalArgumentException("countdown panel padding is outside its safe range");
			}
			if (gapAboveActionBar < 2 || gapAboveActionBar > 24) {
				throw new IllegalArgumentException("countdown ActionBar gap must be between 2 and 24 pixels");
			}
			if (maxWidth < 96 || maxWidth > 480) {
				throw new IllegalArgumentException("countdown maximum width must be between 96 and 480 pixels");
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
				throw new IllegalArgumentException("countdown progress thickness must be between 1 and 4 pixels");
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
			if (durationMs < 0 || durationMs > 2_000) {
				throw new IllegalArgumentException("countdown digit duration must be between 0 and 2000ms");
			}
			if (distance < 4 || distance > 24 || staggerMs < 0 || staggerMs > 100) {
				throw new IllegalArgumentException("countdown digit motion is outside its safe range");
			}
			if (
				!Double.isFinite(incomingAlpha)
					|| !Double.isFinite(outgoingAlpha)
					|| incomingAlpha < 0.20D
					|| incomingAlpha > 1.0D
					|| outgoingAlpha < 0.0D
					|| outgoingAlpha > 1.0D
			) {
				throw new IllegalArgumentException("countdown digit alpha is outside its safe range");
			}
		}
	}

	public record SurfaceMotion(SurfaceTransition mode, int durationMs, Easing easing) {
		public SurfaceMotion {
			mode = Objects.requireNonNull(mode, "mode");
			easing = Objects.requireNonNull(easing, "easing");
			if (durationMs < 0 || durationMs > 2_000) {
				throw new IllegalArgumentException("countdown surface duration must be between 0 and 2000ms");
			}
		}
	}

	public record CompletionPresentation(int holdMs, int color) {
		public CompletionPresentation {
			if (holdMs < 0 || holdMs > 5_000) {
				throw new IllegalArgumentException("countdown completion hold must be between 0 and 5000ms");
			}
		}
	}

	public record CountdownSound(Identifier event, double volume, double pitch) {
		public CountdownSound {
			event = Objects.requireNonNull(event, "event");
			if (
				!Double.isFinite(volume)
					|| !Double.isFinite(pitch)
					|| volume < 0.0D
					|| volume > 4.0D
					|| pitch < 0.5D
					|| pitch > 2.0D
			) {
				throw new IllegalArgumentException("countdown sound is outside its safe range");
			}
		}
	}

	public record CountdownPresentation(
		RichText title,
		Optional<RichText> prefix,
		Optional<RichText> suffix,
		RichText waitingText,
		RichText pausedText,
		RichText completeText,
		TimePresentation time,
		PanelPresentation panel,
		ProgressPresentation progress,
		DigitPresentation digits,
		SurfaceMotion enter,
		SurfaceMotion exit,
		CompletionPresentation completion,
		Map<CountdownCallbackSlot, Optional<CountdownSound>> lifecycleSounds
	) {
		public CountdownPresentation {
			title = Objects.requireNonNull(title, "title");
			prefix = optional(prefix);
			suffix = optional(suffix);
			waitingText = Objects.requireNonNull(waitingText, "waitingText");
			pausedText = Objects.requireNonNull(pausedText, "pausedText");
			completeText = Objects.requireNonNull(completeText, "completeText");
			time = Objects.requireNonNull(time, "time");
			panel = Objects.requireNonNull(panel, "panel");
			progress = Objects.requireNonNull(progress, "progress");
			digits = Objects.requireNonNull(digits, "digits");
			enter = Objects.requireNonNull(enter, "enter");
			exit = Objects.requireNonNull(exit, "exit");
			completion = Objects.requireNonNull(completion, "completion");
			EnumMap<CountdownCallbackSlot, Optional<CountdownSound>> copy =
				new EnumMap<>(CountdownCallbackSlot.class);
			for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
				copy.put(slot, optional(lifecycleSounds.get(slot)));
			}
			lifecycleSounds = Map.copyOf(copy);
		}
	}

	public record DisconnectPolicy(DisconnectAction requiredPlayer, DisconnectAction host) {
		public DisconnectPolicy {
			requiredPlayer = Objects.requireNonNull(requiredPlayer, "requiredPlayer");
			host = Objects.requireNonNull(host, "host");
		}
	}

	public record ItemRestrictions(BlockMode use, BlockMode drop, BlockMode swap, BlockMode inventory) {
		public ItemRestrictions {
			use = Objects.requireNonNull(use, "use");
			drop = Objects.requireNonNull(drop, "drop");
			swap = Objects.requireNonNull(swap, "swap");
			inventory = Objects.requireNonNull(inventory, "inventory");
		}
	}

	public record CountdownRestrictions(
		FreezeMode movement,
		BlockMode attack,
		BlockMode interact,
		ItemRestrictions items,
		DamageMode damage,
		AllowOnly camera,
		AllowOnly chat,
		AllowOnly escape
	) {
		public CountdownRestrictions {
			movement = Objects.requireNonNull(movement, "movement");
			attack = Objects.requireNonNull(attack, "attack");
			interact = Objects.requireNonNull(interact, "interact");
			items = Objects.requireNonNull(items, "items");
			damage = Objects.requireNonNull(damage, "damage");
			camera = Objects.requireNonNull(camera, "camera");
			chat = Objects.requireNonNull(chat, "chat");
			escape = Objects.requireNonNull(escape, "escape");
		}

		public static CountdownRestrictions safeDefault() {
			return new CountdownRestrictions(
				FreezeMode.FREEZE,
				BlockMode.BLOCK,
				BlockMode.BLOCK,
				new ItemRestrictions(BlockMode.BLOCK, BlockMode.BLOCK, BlockMode.BLOCK, BlockMode.BLOCK),
				DamageMode.IMMUNE,
				AllowOnly.ALLOW,
				AllowOnly.ALLOW,
				AllowOnly.ALLOW
			);
		}
	}

	public record CountdownCheckpoint(
		String id,
		long remainingTicks,
		Optional<CountdownSound> sound,
		Optional<Identifier> cue,
		Optional<RichText> text,
		Optional<Integer> color,
		Optional<Double> scale
	) {
		public CountdownCheckpoint {
			id = Objects.requireNonNull(id, "id");
			sound = optional(sound);
			cue = optional(cue);
			text = optional(text);
			color = optional(color);
			scale = optional(scale);
			scale.ifPresent(value -> {
				if (!Double.isFinite(value) || value < 1.0D || value > 1.5D) {
					throw new IllegalArgumentException("countdown checkpoint scale must be between 1 and 1.5");
				}
			});
		}
	}

	public record CountdownCallback(
		String id,
		CountdownCallbackScope scope,
		Identifier function,
		boolean required
	) {
		public CountdownCallback {
			id = Objects.requireNonNull(id, "id");
			scope = Objects.requireNonNull(scope, "scope");
			function = Objects.requireNonNull(function, "function");
		}
	}

	public record CountdownDefinition(
		Identifier id,
		RichText name,
		long durationTicks,
		DisplayAudience displayAudience,
		CountdownPresentation presentation,
		DisconnectPolicy disconnect,
		CountdownRestrictions restrictions,
		List<CountdownCheckpoint> checkpoints,
		Map<CountdownCallbackSlot, List<CountdownCallback>> callbacks,
		String canonicalDocument,
		String sha256
	) {
		public CountdownDefinition {
			id = Objects.requireNonNull(id, "id");
			name = Objects.requireNonNull(name, "name");
			displayAudience = Objects.requireNonNull(displayAudience, "displayAudience");
			presentation = Objects.requireNonNull(presentation, "presentation");
			disconnect = Objects.requireNonNull(disconnect, "disconnect");
			restrictions = Objects.requireNonNull(restrictions, "restrictions");
			checkpoints = List.copyOf(checkpoints);
			Map<CountdownCallbackSlot, List<CountdownCallback>> copy = new LinkedHashMap<>();
			for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
				copy.put(slot, List.copyOf(callbacks.getOrDefault(slot, List.of())));
			}
			callbacks = Map.copyOf(copy);
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
			sha256 = Objects.requireNonNull(sha256, "sha256");
			if (durationTicks < 1 || durationTicks > MAX_COUNTDOWN_TICKS) {
				throw new IllegalArgumentException("countdown duration is outside its safe range");
			}
		}
	}

	public record DisabledCountdown(
		Identifier id,
		List<Problem> diagnostics,
		int totalDiagnosticCount
	) {
		public DisabledCountdown {
			id = Objects.requireNonNull(id, "id");
			diagnostics = List.copyOf(diagnostics);
			if (totalDiagnosticCount < diagnostics.size()) {
				throw new IllegalArgumentException("countdown diagnostic count is inconsistent");
			}
		}
	}

	public record CountdownCatalog(
		Map<Identifier, CountdownDefinition> countdowns,
		Map<Identifier, DisabledCountdown> disabled,
		Set<Identifier> requiredCountdownFailures,
		Set<Identifier> retainedFallbacks
	) {
		public CountdownCatalog {
			countdowns = Map.copyOf(countdowns);
			disabled = Map.copyOf(disabled);
			requiredCountdownFailures = Set.copyOf(requiredCountdownFailures);
			retainedFallbacks = Set.copyOf(retainedFallbacks);
			if (!countdowns.keySet().containsAll(retainedFallbacks)) {
				throw new IllegalArgumentException("retained countdown fallbacks must be executable");
			}
			if (!disabled.keySet().containsAll(retainedFallbacks)) {
				throw new IllegalArgumentException("retained countdown fallbacks must keep candidate diagnostics");
			}
		}

		public CountdownCatalog(
			final Map<Identifier, CountdownDefinition> countdowns,
			final Map<Identifier, DisabledCountdown> disabled,
			final Set<Identifier> requiredCountdownFailures
		) {
			this(countdowns, disabled, requiredCountdownFailures, Set.of());
		}

		public static CountdownCatalog empty() {
			return new CountdownCatalog(Map.of(), Map.of(), Set.of(), Set.of());
		}

		public int definitionCount() {
			Set<Identifier> identities = new java.util.HashSet<>(this.countdowns.keySet());
			identities.addAll(this.disabled.keySet());
			return identities.size();
		}

		public boolean gameStartBlocked(final Identifier gameId) {
			return this.requiredCountdownFailures.contains(gameId);
		}

		public boolean countdownUsable(final Identifier id) {
			return this.countdowns.containsKey(id);
		}

		public boolean usingRetainedFallback(final Identifier id) {
			return this.retainedFallbacks.contains(id);
		}
	}

	private static <T> Optional<T> optional(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}
}
