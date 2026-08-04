package io.github.zcpu954861.pixeltzzpro.client.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.message.GraphemeClusters;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.CharacterSound;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockAnchor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Conflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Cursor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ExternalConflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Fade;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedOrigin;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedPolicies;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Typewriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Adapts the mod's small built-in HUD notices to the same V3B presentation pipeline as data-pack
 * cues. Every generated plan is local, fully static, and contains no callback or authority logic.
 */
public final class ClientBuiltinMessages {
	private static final int BRAND_GOLD = 0xF4C95D;
	private static final int INFO_CYAN = 0x64D8E8;
	private static final int MAIN_TEXT = 0xF2F2F2;
	private static final int SUCCESS = 0x65D68A;
	private static final long LOADING_CHARACTER_NANOS = 65_000_000L;
	private static final long LOADING_FINAL_FRAME_NANOS = 50_000_000L;
	private static final long LOADING_TAIL_NANOS = 3_000_000_000L;
	private static final long LOADING_FADE_NANOS = 1_000_000_000L;
	private static final long OPERATION_BASE_CHARACTER_NANOS = 120_000_000L;
	private static final long OPERATION_MINIMUM_REVEAL_NANOS = 900_000_000L;
	private static final long OPERATION_HOLD_NANOS = 1_500_000_000L;
	private static final long OPERATION_FADE_NANOS = 400_000_000L;
	private static final long OPERATION_SETTLE_NANOS = 150_000_000L;
	private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
	private static final Identifier HAT_SOUND = Identifier.parse("minecraft:block.note_block.hat");
	private static final Identifier COMPLETE_SOUND = Identifier.parse(
		"minecraft:entity.experience_orb.pickup"
	);
	private static long latestLoadingSequence = Long.MIN_VALUE;

	private ClientBuiltinMessages() {
	}

	public static void enqueueLoadingActionBar(
		final long sequence,
		final Component finalText
	) {
		if (
			sequence < 0L
				|| sequence <= latestLoadingSequence
				|| finalText == null
				|| finalText.getString().isBlank()
		) {
			return;
		}
		latestLoadingSequence = sequence;
		Component resolved = Component.literal(finalText.getString()).withColor(BRAND_GOLD);
		int graphemes = GraphemeClusters.count(resolved.getString());
		ResolvedEffect typing = new ResolvedEffect(
			Optional.of(
				new Typewriter(
					LOADING_CHARACTER_NANOS,
					new Cursor(true, "▌", color(MAIN_TEXT), false, 0L),
					Optional.of(color(INFO_CYAN)),
					1,
					RestoreMode.INSTANT
				)
			),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			graphemes > 1
				? Optional.of(
					new CharacterSound(
						HAT_SOUND,
						SoundCategory.UI,
						1,
						false,
						false,
						false,
						0.10F,
						1.75F,
						0.0F,
						SoundAttachment.PLAYER
					)
				)
				: Optional.empty(),
			Math.max(0, graphemes - 1)
		);
		ResolvedEffect tail = new ResolvedEffect(
			Optional.empty(),
			Optional.of(
				new Fade(
					0L,
					LOADING_TAIL_NANOS - LOADING_FADE_NANOS,
					LOADING_FADE_NANOS
				)
			),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.of(
				new CharacterSound(
					COMPLETE_SOUND,
					SoundCategory.UI,
					1,
					false,
					false,
					false,
					0.28F,
					1.35F,
					0.0F,
					SoundAttachment.PLAYER
				)
			),
			1
		);
		ClientMessagePlayback.offerBuiltinPlan(
			plan(
				PixelTzzPro.id("builtin/loading_action_bar"),
				sequence,
				0L,
				List.of(
					textNode(
						0,
						Channel.ACTION_BAR,
						resolved,
						typing,
						ResolvedDuration.contentDriven(LOADING_FINAL_FRAME_NANOS)
					),
					textNode(
						1,
						Channel.ACTION_BAR,
						resolved,
						tail,
						ResolvedDuration.contentDriven(LOADING_TAIL_NANOS)
					)
				)
			)
		);
	}

	public static void resetConnection() {
		latestLoadingSequence = Long.MIN_VALUE;
	}

	public static void enqueueOperationSubtitle(final String message) {
		if (message == null || message.isBlank()) {
			return;
		}
		int graphemes = Math.max(1, GraphemeClusters.count(message));
		long characterNanos = Math.max(
			OPERATION_BASE_CHARACTER_NANOS,
			Math.ceilDiv(OPERATION_MINIMUM_REVEAL_NANOS, graphemes)
		);
		Component resolved = Component.literal(message).withColor(MAIN_TEXT);
		ResolvedEffect effect = new ResolvedEffect(
			Optional.of(
				new Typewriter(
					characterNanos,
					new Cursor(true, " ▌", color(SUCCESS), false, 0L),
					Optional.of(color(SUCCESS)),
					1,
					RestoreMode.INSTANT
				)
			),
			Optional.of(
				new Fade(
					0L,
					OPERATION_HOLD_NANOS,
					OPERATION_FADE_NANOS
				)
			),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.of(
				new CharacterSound(
					COMPLETE_SOUND,
					SoundCategory.UI,
					1,
					false,
					false,
					false,
					0.32F,
					1.22F,
					0.0F,
					SoundAttachment.PLAYER
				)
			),
			1
		);
		ClientMessagePlayback.offerBuiltinPlan(
			plan(
				PixelTzzPro.id("builtin/operation_subtitle"),
				0L,
				OPERATION_SETTLE_NANOS,
				List.of(
					textNode(
						0,
						Channel.SUBTITLE,
						resolved,
						effect,
						ResolvedDuration.contentDriven(
							OPERATION_HOLD_NANOS + OPERATION_FADE_NANOS
						)
					)
				)
			)
		);
	}

	private static MessagePlaybackPlan plan(
		final Identifier cueId,
		final long sequence,
		final long startAtNanos,
		final List<ResolvedNode> nodes
	) {
		return new MessagePlaybackPlan(
			UUID.randomUUID(),
			cueId,
			0L,
			sequence,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			new ClockAnchor(ClockKind.PRESENTATION_NANOS, 0L, startAtNanos),
			builtinPolicies(),
			new ResolvedOrigin(OVERWORLD, 0.0D, 0.0D, 0.0D),
			nodes
		);
	}

	private static ResolvedTextNode textNode(
		final int ordinal,
		final Channel channel,
		final Component component,
		final ResolvedEffect effect,
		final ResolvedDuration duration
	) {
		return new ResolvedTextNode(
			ordinal,
			0L,
			0,
			Conflict.QUEUE,
			channel,
			List.of(new StaticSegment(resolved(component), SegmentTiming.none())),
			effect,
			ResolvedLayout.standard(),
			duration,
			ResolvedSpeaker.system()
		);
	}

	private static ResolvedPolicies builtinPolicies() {
		ResolvedPolicies standard = ResolvedPolicies.standard();
		return new ResolvedPolicies(
			standard.chatInterrupt(),
			standard.screenStart(),
			standard.obscured(),
			standard.screenWaitTimeoutNanos(),
			standard.screenTimeoutAction(),
			standard.maxPauseNanos(),
			ExternalConflict.OVERLAY,
			standard.resourceReload(),
			standard.reducedMotion(),
			standard.allowManualComplete(),
			standard.respawn(),
			standard.dimensionExit()
		);
	}

	private static ResolvedComponent resolved(final Component component) {
		JsonElement encoded = ComponentSerialization.CODEC
			.encodeStart(JsonOps.INSTANCE, component)
			.getOrThrow();
		return ResolvedComponent.parseCanonical(canonical(encoded));
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
			result.append(new JsonPrimitive(entry.getKey()))
				.append(':')
				.append(canonical(entry.getValue()));
		}
		return result.append('}').toString();
	}

	private static String color(final int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}
}
