package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.SegmentSpan;
import io.github.zcpu954861.pixeltzzpro.message.StyledMessageEffects;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Cursor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.DurationMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Fade;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Gradient;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Motion;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Pulse;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Scramble;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Typewriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;

/**
 * Runnable checks for deterministic V3B scramble, gradient, fade, motion and pulse composition.
 */
public final class MessageVisualEffectsSelfCheck {
	private static final long MS = 1_000_000L;
	private static final UUID INSTANCE = UUID.fromString("67fc92b8-a665-4197-9996-446d86088ca8");

	private MessageVisualEffectsSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkScrambleAndGradient();
		checkFadeLifecycle();
		checkMotionPulseAndReducedMotion();
		checkCursorPreferenceAndContrast();
		checkTrailingFreshColorRestore();

		System.out.println("MESSAGE_VISUAL_EFFECTS_SELF_CHECK=PASS");
	}

	private static void checkScrambleAndGradient() {
		MessageFrameTimeline.Timeline timeline = MessageFrameTimeline.compile(
			Component.literal("AB C").withColor(0xFFFFFF),
			effect(),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(4))
		);
		var firstFrame = timeline.frameAt(50L * MS);
		var first = StyledMessageEffects.frame(
			timeline,
			firstFrame,
			INSTANCE,
			7,
			false,
			true
		);
		check(first.component().getString().length() >= 4, "scramble must prefill hidden layout");
		check(
			first.component().getString().contains(" "),
			"scramble must preserve whitespace used for layout"
		);
		check(
			first.component().toFlatList().stream()
				.anyMatch(part -> part.getStyle().getColor() != null),
			"gradient must color the projected component"
		);

		var repeated = StyledMessageEffects.frame(
			timeline,
			firstFrame,
			INSTANCE,
			7,
			false,
			true
		);
		check(
			repeated.component().equals(first.component()),
			"the same instance, ordinal and elapsed time must produce the same scramble"
		);
	}

	private static void checkFadeLifecycle() {
		MessageFrameTimeline.Timeline timeline = MessageFrameTimeline.compile(
			Component.literal("AB"),
			effect(),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(2))
		);
		check(
			timeline.completeAtNanos() >= 700L * MS,
			"fade hold and exit must keep the node alive long enough to render"
		);
		float entering = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(50L * MS),
			INSTANCE,
			1,
			false,
			true
		).opacity();
		float leaving = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(timeline.completeAtNanos() - 100L * MS),
			INSTANCE,
			1,
			false,
			true
		).opacity();
		check(entering > 0.0F && entering < 1.0F, "fade enter must interpolate opacity");
		check(leaving > 0.0F && leaving < 1.0F, "fade exit must interpolate opacity");
	}

	private static void checkMotionPulseAndReducedMotion() {
		MessageFrameTimeline.Timeline timeline = MessageFrameTimeline.compile(
			Component.literal("AB"),
			effect(),
			new ResolvedDuration(
				DurationMode.CONTENT_DRIVEN,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				500L * MS
			),
			List.of(
				new SegmentSpan(1, new SegmentTiming(0L, 0L, true)),
				SegmentSpan.plain(1)
			)
		);
		var active = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(100L * MS),
			INSTANCE,
			4,
			false,
			true
		);
		check(active.translateX() < 0.0F, "motion must seek from its registered X offset");
		check(active.translateY() > 0.0F, "motion must seek from its registered Y offset");
		check(active.scale() > 1.0F, "emphasized segment must produce one bounded pulse");

		var reduced = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(100L * MS),
			INSTANCE,
			4,
			true,
			false
		);
		check(
			reduced.opacity() == 1.0F
				&& reduced.translateX() == 0.0F
				&& reduced.translateY() == 0.0F
				&& reduced.scale() == 1.0F,
			"reduced motion must remove fade, motion and pulse without hiding final content"
		);
		check(
			!reduced.component().getString().matches(".*[XYZ].*"),
			"reduced motion must not retain moving scramble placeholders"
		);
	}

	private static void checkCursorPreferenceAndContrast() {
		ResolvedEffect cursorEffect = new ResolvedEffect(
			Optional.of(
				new Typewriter(
					100L * MS,
					new Cursor(true, "▌", "#101010", true, 100L * MS),
					Optional.of("#202020"),
					1,
					RestoreMode.INSTANT
				)
			),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			0
		);
		MessageFrameTimeline.Timeline timeline = MessageFrameTimeline.compile(
			Component.literal("A").withColor(0x303030),
			cursorEffect,
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(1))
		);
		var blinkOff = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(75L * MS),
			INSTANCE,
			9,
			false,
			false,
			true
		);
		check(
			blinkOff.component().getString().contains("▌"),
			"disabling cursor blink must keep the registered cursor steadily visible"
		);
		check(
			blinkOff.component().toFlatList().stream()
				.anyMatch(
					part -> part.getString().contains("▌")
						&& part.getStyle().getColor() != null
						&& part.getStyle().getColor().getValue() == 0x00FFFF
						&& !part.getStyle().isBold()
						&& part.getStyle().isUnderlined()
				),
			"high contrast cursor must be cyan and underlined without width-changing bold"
		);
		var fresh = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(100L * MS),
			INSTANCE,
			9,
			false,
			true,
			true
		);
		check(
			fresh.component().toFlatList().stream()
				.anyMatch(part -> part.getString().equals("A")
					&& part.getStyle().getColor() != null
					&& part.getStyle().getColor().getValue() == 0xFFFF00
					&& !part.getStyle().isBold()
					&& part.getStyle().isUnderlined()),
			"high contrast fresh text must be yellow and underlined without width-changing bold"
		);
		var complete = StyledMessageEffects.frame(
			timeline,
			timeline.frameAt(timeline.completeAtNanos()),
			INSTANCE,
			9,
			false,
			true,
			true
		);
		check(
			colorOf(complete.component(), "A") == 0x303030
				&& complete.component().toFlatList().stream()
					.filter(part -> part.getString().equals("A"))
					.noneMatch(part -> part.getStyle().isBold() || part.getStyle().isUnderlined()),
			"high contrast must restore the registered final color and style exactly"
		);
	}

	private static void checkTrailingFreshColorRestore() {
		MessageFrameTimeline.Timeline gradient = MessageFrameTimeline.compile(
			Component.literal("A").withColor(0x123456),
			freshColorEffect(3, RestoreMode.GRADIENT),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(1))
		);
		var newlyRevealed = StyledMessageEffects.frame(
			gradient,
			gradient.frameAt(100L * MS),
			INSTANCE,
			10,
			false,
			true
		);
		check(
			colorOf(newlyRevealed.component(), "A") == 0xFEDCBA,
			"the final revealed grapheme must begin in its registered fresh color"
		);

		var restoring = StyledMessageEffects.frame(
			gradient,
			gradient.frameAt(250L * MS),
			INSTANCE,
			10,
			false,
			true
		);
		check(
			colorOf(restoring.component(), "A") == 0x888888,
			"gradient restoration must continue while the virtual cursor advances past the final grapheme"
		);

		var gradientCompleteFrame = gradient.frameAt(400L * MS);
		var gradientComplete = StyledMessageEffects.frame(
			gradient,
			gradientCompleteFrame,
			INSTANCE,
			10,
			false,
			true
		);
		check(
			gradientCompleteFrame.complete()
				&& colorOf(gradientComplete.component(), "A") == 0x123456
				&& !containsColor(gradientComplete.component(), 0xFEDCBA),
			"a complete gradient frame must restore the original color before becoming static"
		);

		MessageFrameTimeline.Timeline instant = MessageFrameTimeline.compile(
			Component.literal("A").withColor(0x123456),
			freshColorEffect(3, RestoreMode.INSTANT),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(1))
		);
		check(
			colorOf(
				StyledMessageEffects.frame(
					instant,
					instant.frameAt(399L * MS),
					INSTANCE,
					11,
					false,
					true
				).component(),
				"A"
			) == 0xFEDCBA,
			"instant restoration must retain the fresh color until the full restore distance elapses"
		);
		var instantCompleteFrame = instant.frameAt(400L * MS);
		var instantComplete = StyledMessageEffects.frame(
			instant,
			instantCompleteFrame,
			INSTANCE,
			11,
			false,
			true
		);
		check(
			instantCompleteFrame.complete()
				&& colorOf(instantComplete.component(), "A") == 0x123456
				&& !containsColor(instantComplete.component(), 0xFEDCBA),
			"instant restoration must switch back on the exact completion boundary"
		);

		Component multicolor = Component.empty()
			.append(Component.literal("A").withColor(0x123456))
			.append(Component.literal("B").withColor(0x654321));
		MessageFrameTimeline.Timeline multicolorTimeline = MessageFrameTimeline.compile(
			multicolor,
			freshColorEffect(2, RestoreMode.GRADIENT),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(2))
		);
		var multicolorComplete = StyledMessageEffects.frame(
			multicolorTimeline,
			multicolorTimeline.frameAt(400L * MS),
			INSTANCE,
			12,
			false,
			true
		);
		check(
			colorOf(multicolorComplete.component(), "A") == 0x123456
				&& colorOf(multicolorComplete.component(), "B") == 0x654321,
			"each grapheme must recover its own registered rich-text color"
		);
	}

	private static ResolvedEffect freshColorEffect(
		final int restoreAfterCharacters,
		final RestoreMode restoreMode
	) {
		return new ResolvedEffect(
			Optional.of(
				new Typewriter(
					100L * MS,
					new Cursor(false, "▌", "#FFFFFF", false, 0L),
					Optional.of("#FEDCBA"),
					restoreAfterCharacters,
					restoreMode
				)
			),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			0
		);
	}

	private static int colorOf(final Component component, final String text) {
		return component.toFlatList()
			.stream()
			.filter(part -> part.getString().equals(text))
			.map(part -> part.getStyle().getColor())
			.filter(java.util.Objects::nonNull)
			.mapToInt(color -> color.getValue())
			.findFirst()
			.orElseThrow(
				() -> new IllegalStateException("missing color for projected text " + text)
			);
	}

	private static boolean containsColor(final Component component, final int color) {
		return component.toFlatList()
			.stream()
			.anyMatch(
				part -> part.getStyle().getColor() != null
					&& part.getStyle().getColor().getValue() == color
			);
	}

	private static ResolvedEffect effect() {
		return new ResolvedEffect(
			Optional.of(
				new Typewriter(
					100L * MS,
					new Cursor(true, "▌", "#FFFFFF", false, 0L),
					Optional.of("#FFFF00"),
					2,
					RestoreMode.GRADIENT
				)
			),
			Optional.of(new Fade(200L * MS, 300L * MS, 200L * MS)),
			Optional.of(new Scramble(50L * MS, "XYZ")),
			Optional.of(new Gradient("#0000FF", "#FF0000", 400L * MS)),
			Optional.of(new Motion(-8, 4, 0.9F, 300L * MS)),
			Optional.of(new Pulse(1.2F, 200L * MS)),
			Optional.empty(),
			0
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
