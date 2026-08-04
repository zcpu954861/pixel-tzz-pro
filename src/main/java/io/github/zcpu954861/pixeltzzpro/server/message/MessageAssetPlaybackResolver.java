package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.CharacterSound;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Applies one recipient's verified asset choices to a source-free playback projection. */
public final class MessageAssetPlaybackResolver {
	private MessageAssetPlaybackResolver() {
	}

	/**
	 * Replaces original sound IDs with verified fallback IDs and removes sounds resolved silent.
	 * Undeclared sounds are preserved for compatibility with built-in plans; data-pack cue sounds
	 * are always included in the cue's cumulative manifest by {@link MessageAssetAuthority}.
	 */
	public static Optional<MessagePlaybackPlan> resolvePlan(
		final MessagePlaybackPlan source,
		final Map<AssetKey, Optional<Identifier>> effectiveAssets
	) {
		Objects.requireNonNull(source, "source");
		List<ResolvedNode> nodes = resolveNodes(source.nodes(), effectiveAssets);
		if (nodes.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(
			new MessagePlaybackPlan(
				source.instanceId(),
				source.cueId(),
				source.generation(),
				source.sequence(),
				source.cycle(),
				source.disposition(),
				source.replacedInstanceId(),
				source.clock(),
				source.policies(),
				source.origin(),
				nodes
			)
		);
	}

	public static List<ResolvedNode> resolveNodes(
		final List<ResolvedNode> source,
		final Map<AssetKey, Optional<Identifier>> effectiveAssets
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(effectiveAssets, "effectiveAssets");
		List<ResolvedNode> resolved = new ArrayList<>(source.size());
		for (ResolvedNode node : source) {
			if (node instanceof ResolvedSoundNode sound) {
				Optional<Identifier> effective = effective(
					effectiveAssets,
					sound.sound()
				);
				if (effective.isEmpty()) {
					continue;
				}
				resolved.add(
					new ResolvedSoundNode(
						sound.ordinal(),
						sound.startOffsetNanos(),
						sound.priority(),
						sound.conflict(),
						effective.orElseThrow(),
						sound.category(),
						sound.volume(),
						sound.pitch(),
						sound.attachment()
					)
				);
				continue;
			}
			ResolvedTextNode text = (ResolvedTextNode)node;
			ResolvedEffect effect = text.effect();
			Optional<CharacterSound> characterSound = effect.characterSound()
				.flatMap(sound ->
					effective(effectiveAssets, sound.sound()).map(effective ->
						new CharacterSound(
							effective,
							sound.category(),
							sound.everyCharacters(),
							sound.skipWhitespace(),
							sound.skipPunctuation(),
							sound.skipNewline(),
							sound.volume(),
							sound.pitch(),
							sound.pitchVariation(),
							sound.attachment()
						)
					)
				);
			ResolvedEffect resolvedEffect = new ResolvedEffect(
				effect.typewriter(),
				effect.fade(),
				effect.scramble(),
				effect.gradient(),
				effect.motion(),
				effect.pulse(),
				characterSound,
				characterSound.isPresent() ? effect.maxCharacterSoundEvents() : 0
			);
			resolved.add(
				new ResolvedTextNode(
					text.ordinal(),
					text.startOffsetNanos(),
					text.priority(),
					text.conflict(),
					text.channel(),
					text.segments(),
					resolvedEffect,
					text.layout(),
					text.duration(),
					text.speaker()
				)
			);
		}
		return List.copyOf(resolved);
	}

	private static Optional<Identifier> effective(
		final Map<AssetKey, Optional<Identifier>> effectiveAssets,
		final Identifier original
	) {
		AssetKey key = new AssetKey(AssetType.SOUND, original);
		return effectiveAssets.containsKey(key)
			? effectiveAssets.get(key)
			: Optional.of(original);
	}
}
