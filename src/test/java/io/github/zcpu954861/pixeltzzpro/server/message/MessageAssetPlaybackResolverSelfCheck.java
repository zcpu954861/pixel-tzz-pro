package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.CharacterSound;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockAnchor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Conflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedOrigin;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedPolicies;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure projection checks for per-recipient fallback and silent sound selection. */
public final class MessageAssetPlaybackResolverSelfCheck {
	private static final Identifier ORIGINAL_NODE = Identifier.parse("pixel_tzz:node_original");
	private static final Identifier FALLBACK_NODE = Identifier.parse("minecraft:ui.button.click");
	private static final Identifier ORIGINAL_CHARACTER = Identifier.parse("pixel_tzz:character_original");
	private static final Identifier FALLBACK_CHARACTER = Identifier.parse("minecraft:block.note_block.hat");

	private MessageAssetPlaybackResolverSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_ASSET_PLAYBACK_RESOLVER_SELF_CHECK=PASS");
	}

	public static void run() {
		MessagePlaybackPlan plan = plan();
		MessagePlaybackPlan fallback = MessageAssetPlaybackResolver.resolvePlan(
			plan,
			Map.of(
				new AssetKey(AssetType.SOUND, ORIGINAL_NODE), Optional.of(FALLBACK_NODE),
				new AssetKey(AssetType.SOUND, ORIGINAL_CHARACTER), Optional.of(FALLBACK_CHARACTER)
			)
		).orElseThrow();
		ResolvedTextNode text = (ResolvedTextNode)fallback.nodes().getFirst();
		ResolvedSoundNode sound = (ResolvedSoundNode)fallback.nodes().get(1);
		check(
			text.effect().characterSound().orElseThrow().sound().equals(FALLBACK_CHARACTER)
				&& sound.sound().equals(FALLBACK_NODE),
			"fallback-used reports must replace both character and independent sound IDs"
		);

		MessagePlaybackPlan silentCharacter = MessageAssetPlaybackResolver.resolvePlan(
			plan,
			Map.of(
				new AssetKey(AssetType.SOUND, ORIGINAL_NODE), Optional.of(FALLBACK_NODE),
				new AssetKey(AssetType.SOUND, ORIGINAL_CHARACTER), Optional.empty()
			)
		).orElseThrow();
		check(
			((ResolvedTextNode)silentCharacter.nodes().getFirst())
				.effect().characterSound().isEmpty(),
			"silent character sounds must remove the character-sound track"
		);

		MessagePlaybackPlan silentNode = MessageAssetPlaybackResolver.resolvePlan(
			plan,
			Map.of(
				new AssetKey(AssetType.SOUND, ORIGINAL_NODE), Optional.empty(),
				new AssetKey(AssetType.SOUND, ORIGINAL_CHARACTER), Optional.of(FALLBACK_CHARACTER)
			)
		).orElseThrow();
		check(
			silentNode.nodes().size() == 1
				&& silentNode.nodes().getFirst() instanceof ResolvedTextNode,
			"silent independent sounds must be removed without removing visible text"
		);
		MessagePlaybackPlan soundOnly = new MessagePlaybackPlan(
			plan.instanceId(),
			plan.cueId(),
			plan.generation(),
			plan.sequence(),
			plan.cycle(),
			plan.disposition(),
			plan.replacedInstanceId(),
			plan.clock(),
			plan.policies(),
			plan.origin(),
			List.of(plan.nodes().get(1))
		);
		check(
			MessageAssetPlaybackResolver.resolvePlan(
				soundOnly,
				Map.of(
					new AssetKey(AssetType.SOUND, ORIGINAL_NODE), Optional.empty()
				)
			).isEmpty(),
			"a sound-only silent projection must not emit an empty invalid plan"
		);
	}

	private static MessagePlaybackPlan plan() {
		CharacterSound character = new CharacterSound(
			ORIGINAL_CHARACTER,
			SoundCategory.UI,
			1,
			true,
			true,
			true,
			1.0F,
			1.0F,
			0.0F,
			SoundAttachment.PLAYER
		);
		ResolvedTextNode text = new ResolvedTextNode(
			0,
			0L,
			0,
			Conflict.PARALLEL,
			Channel.SUBTITLE,
			List.of(
				new StaticSegment(
					new ResolvedComponent("{\"text\":\"资源选择\"}"),
					SegmentTiming.none()
				)
			),
			new ResolvedEffect(
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.of(character), 16
			),
			new ResolvedLayout(
				MessagePlaybackPlan.Alignment.CENTER,
				0,
				0,
				OptionalInt.empty(),
				OptionalInt.empty(),
				1.0F,
				MessagePlaybackPlan.Overflow.WRAP
			),
			ResolvedDuration.contentDriven(0L),
			new ResolvedSpeaker(SpeakerKind.SYSTEM, Optional.empty(), Optional.empty())
		);
		ResolvedNode sound = new ResolvedSoundNode(
			1,
			1L,
			0,
			Conflict.PARALLEL,
			ORIGINAL_NODE,
			SoundCategory.UI,
			1.0F,
			1.0F,
			SoundAttachment.PLAYER
		);
		return new MessagePlaybackPlan(
			new UUID(0L, 51L),
			Identifier.parse("pixel_tzz:asset_projection"),
			1L,
			1L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			new ClockAnchor(ClockKind.PRESENTATION_NANOS, 0L, 0L),
			ResolvedPolicies.standard(),
			new ResolvedOrigin(Identifier.parse("minecraft:overworld"), 0D, 64D, 0D),
			List.of(text, sound)
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
