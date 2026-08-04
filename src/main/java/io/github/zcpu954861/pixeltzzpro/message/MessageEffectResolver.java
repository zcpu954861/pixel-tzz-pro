package io.github.zcpu954861.pixeltzzpro.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CursorPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CursorSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EffectUse;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FadePatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FadeSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.GradientSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MotionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.PulseSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScramblePatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScrambleSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TypewriterPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TypewriterSpec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Resolves one preset plus its explicit per-content overrides into an immutable V3B effect.
 *
 * <p>The client never receives a preset identifier or the complete effect catalog. Resolving on the
 * server both preserves the least-privilege wire boundary and pins an active presentation to the
 * generation that created it.
 */
public final class MessageEffectResolver {
	private static final TimeSpan DEFAULT_CHARACTER_INTERVAL = new TimeSpan(50_000_000L);
	private static final String DEFAULT_SCRAMBLE_CHARACTERS = "█▓▒░";

	private MessageEffectResolver() {
	}

	public static Resolution resolve(
		final Optional<EffectUse> use,
		final Map<Identifier, TextEffectDefinition> effects
	) {
		Objects.requireNonNull(use, "use");
		Objects.requireNonNull(effects, "effects");
		if (use.isEmpty()) {
			return Resolution.none();
		}

		EffectUse requested = use.orElseThrow();
		TextEffectDefinition preset = requested.preset().map(effects::get).orElse(null);
		if (requested.preset().isPresent() && preset == null) {
			return Resolution.failed(
				"effect preset is unavailable in the frozen generation: "
					+ requested.preset().orElseThrow()
			);
		}
		try {
			TextEffectPatch patch = requested.overrides();
			ResolvedEffect resolved = new ResolvedEffect(
				mergeTypewriter(
					preset == null ? Optional.empty() : preset.typewriter(),
					patch.typewriter()
				),
				mergeFade(
					preset == null ? Optional.empty() : preset.fade(),
					patch.fade()
				),
				mergeScramble(
					preset == null ? Optional.empty() : preset.scramble(),
					patch.scramble()
				),
				patch.gradient().isPresent()
					? patch.gradient()
					: preset == null ? Optional.empty() : preset.gradient(),
				patch.motion().isPresent()
					? patch.motion()
					: preset == null ? Optional.empty() : preset.motion(),
				patch.pulse().isPresent()
					? patch.pulse()
					: preset == null ? Optional.empty() : preset.pulse(),
				mergeCharacterSound(
					preset == null ? Optional.empty() : preset.characterSound(),
					patch.characterSound()
				),
				patch.maxCharacterSoundEvents().orElse(
					preset == null
						? MessageDefinitionParser.DEFAULT_MAX_CHARACTER_SOUND_EVENTS
						: preset.maxCharacterSoundEvents()
				)
			);
			return Resolution.resolved(resolved);
		} catch (IllegalArgumentException incompleteOverride) {
			return Resolution.failed(incompleteOverride.getMessage());
		}
	}

	private static Optional<TypewriterSpec> mergeTypewriter(
		final Optional<TypewriterSpec> base,
		final Optional<TypewriterPatch> patch
	) {
		if (patch.isEmpty()) {
			return base;
		}
		TypewriterPatch change = patch.orElseThrow();
		TypewriterSpec existing = base.orElse(
			new TypewriterSpec(
				DEFAULT_CHARACTER_INTERVAL,
				CursorSpec.disabled(),
				Optional.empty(),
				0,
				RestoreMode.INSTANT
			)
		);
		return Optional.of(
			new TypewriterSpec(
				change.characterInterval().orElse(existing.characterInterval()),
				mergeCursor(existing.cursor(), change.cursor()),
				change.freshColor().isPresent()
					? change.freshColor()
					: existing.freshColor(),
				change.restoreAfterCharacters().orElse(existing.restoreAfterCharacters()),
				change.restoreMode().orElse(existing.restoreMode())
			)
		);
	}

	private static CursorSpec mergeCursor(
		final CursorSpec base,
		final Optional<CursorPatch> patch
	) {
		if (patch.isEmpty()) {
			return base;
		}
		CursorPatch change = patch.orElseThrow();
		return new CursorSpec(
			change.enabled().orElse(base.enabled()),
			change.glyph().orElse(base.glyph()),
			change.color().orElse(base.color()),
			change.blink().orElse(base.blink()),
			change.blinkPeriod().isPresent() ? change.blinkPeriod() : base.blinkPeriod()
		);
	}

	private static Optional<FadeSpec> mergeFade(
		final Optional<FadeSpec> base,
		final Optional<FadePatch> patch
	) {
		if (patch.isEmpty()) {
			return base;
		}
		FadePatch change = patch.orElseThrow();
		FadeSpec existing = base.orElse(
			new FadeSpec(new TimeSpan(0L), new TimeSpan(0L), new TimeSpan(0L))
		);
		return Optional.of(
			new FadeSpec(
				change.enter().orElse(existing.enter()),
				change.hold().orElse(existing.hold()),
				change.exit().orElse(existing.exit())
			)
		);
	}

	private static Optional<ScrambleSpec> mergeScramble(
		final Optional<ScrambleSpec> base,
		final Optional<ScramblePatch> patch
	) {
		if (patch.isEmpty()) {
			return base;
		}
		ScramblePatch change = patch.orElseThrow();
		ScrambleSpec existing = base.orElse(
			new ScrambleSpec(DEFAULT_CHARACTER_INTERVAL, DEFAULT_SCRAMBLE_CHARACTERS)
		);
		return Optional.of(
			new ScrambleSpec(
				change.characterInterval().orElse(existing.characterInterval()),
				change.characters().orElse(existing.characters())
			)
		);
	}

	private static Optional<CharacterSoundSpec> mergeCharacterSound(
		final Optional<CharacterSoundSpec> base,
		final Optional<CharacterSoundPatch> patch
	) {
		if (patch.isEmpty()) {
			return base;
		}
		CharacterSoundPatch change = patch.orElseThrow();
		Identifier sound = change.sound()
			.or(() -> base.map(CharacterSoundSpec::sound))
			.orElseThrow(
				() -> new IllegalArgumentException(
					"standalone character_sound override requires sound"
				)
			);
		CharacterSoundSpec existing = base.orElse(
			new CharacterSoundSpec(
				sound,
				SoundCategory.UI,
				1,
				true,
				true,
				true,
				1.0F,
				1.0F,
				0.0F,
				SoundAttachment.PLAYER,
				MissingAssetMode.SILENT,
				Optional.empty()
			)
		);
		return Optional.of(
			new CharacterSoundSpec(
				sound,
				change.category().orElse(existing.category()),
				change.everyCharacters().orElse(existing.everyCharacters()),
				change.skipWhitespace().orElse(existing.skipWhitespace()),
				change.skipPunctuation().orElse(existing.skipPunctuation()),
				change.skipNewline().orElse(existing.skipNewline()),
				change.volume().orElse(existing.volume()),
				change.pitch().orElse(existing.pitch()),
				change.pitchVariation().orElse(existing.pitchVariation()),
				change.attachment().orElse(existing.attachment()),
				change.missing().orElse(existing.missing()),
				change.fallback().isPresent() ? change.fallback() : existing.fallback()
			)
		);
	}

	public record ResolvedEffect(
		Optional<TypewriterSpec> typewriter,
		Optional<FadeSpec> fade,
		Optional<ScrambleSpec> scramble,
		Optional<GradientSpec> gradient,
		Optional<MotionSpec> motion,
		Optional<PulseSpec> pulse,
		Optional<CharacterSoundSpec> characterSound,
		int maxCharacterSoundEvents
	) {
		public ResolvedEffect {
			typewriter = immutable(typewriter);
			fade = immutable(fade);
			scramble = immutable(scramble);
			gradient = immutable(gradient);
			motion = immutable(motion);
			pulse = immutable(pulse);
			characterSound = immutable(characterSound);
			if (
				maxCharacterSoundEvents < 1
					|| maxCharacterSoundEvents
						> MessageDefinitionParser.MAX_CHARACTER_SOUND_EVENTS
			) {
				throw new IllegalArgumentException(
					"resolved character-sound event budget is out of range"
				);
			}
		}
	}

	public record Resolution(
		Optional<ResolvedEffect> effect,
		Optional<String> error
	) {
		public Resolution {
			effect = immutable(effect);
			error = immutable(error);
			if (effect.isPresent() && error.isPresent()) {
				throw new IllegalArgumentException(
					"effect resolution cannot succeed and fail together"
				);
			}
			if (error.filter(String::isBlank).isPresent()) {
				throw new IllegalArgumentException("effect resolution error cannot be blank");
			}
		}

		public boolean successful() {
			return this.error.isEmpty();
		}

		public static Resolution none() {
			return new Resolution(Optional.empty(), Optional.empty());
		}

		public static Resolution resolved(final ResolvedEffect effect) {
			return new Resolution(Optional.of(effect), Optional.empty());
		}

		public static Resolution failed(final String error) {
			return new Resolution(
				Optional.empty(),
				Optional.of(Objects.requireNonNull(error, "error"))
			);
		}
	}

	private static <T> Optional<T> immutable(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}
}
