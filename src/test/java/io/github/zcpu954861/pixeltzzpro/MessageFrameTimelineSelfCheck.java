package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.Frame;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.Phase;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.SegmentSpan;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.Timeline;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Cursor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.DurationMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Typewriter;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.Bootstrap;

/**
 * Runnable checks for frame-rate-independent V3B reveal, pause, cursor and hold timing.
 */
public final class MessageFrameTimelineSelfCheck {
	private static final long MS = 1_000_000L;
	private static final long SECOND = 1_000_000_000L;

	private MessageFrameTimelineSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkContentDrivenUnicodeAndCursor();
		checkSegmentPauses();
		checkFixedTotalAndSpeedClamps();
		checkTrailingColorLifecycle();
		checkStaticRevealAndBounds();

		System.out.println("MESSAGE_FRAME_TIMELINE_SELF_CHECK=PASS");
	}

	private static void checkContentDrivenUnicodeAndCursor() {
		Timeline timeline = MessageFrameTimeline.compile(
			Component.literal("中👨‍👩‍👧‍👦A"),
			typewriterEffect(100L * MS, true, 200L * MS),
			ResolvedDuration.contentDriven(200L * MS),
			List.of(SegmentSpan.plain(3))
		);
		check(timeline.styledText().graphemes().size() == 3, "Unicode text must have 3 graphemes");
		check(timeline.characterIntervalNanos() == 100L * MS, "registered interval must survive");
		check(timeline.revealCompleteAtNanos() == 300L * MS, "three graphemes reveal in 300ms");
		check(timeline.completeAtNanos() == 500L * MS, "declared hold must follow reveal");

		Frame halfwayFirst = timeline.frameAt(50L * MS);
		check(halfwayFirst.visibleGraphemes() == 0, "first grapheme is not visible at half interval");
		check(
			Math.abs(halfwayFirst.partialGraphemeProgress() - 0.5D) < 0.000_001D,
			"partial progress must be continuous"
		);
		check(halfwayFirst.cursorVisible(), "blinking cursor starts visible");

		Frame halfwaySecond = timeline.frameAt(150L * MS);
		check(halfwaySecond.visibleGraphemes() == 1, "one grapheme must be visible at 150ms");
		check(
			Math.abs(halfwaySecond.partialGraphemeProgress() - 0.5D) < 0.000_001D,
			"second grapheme partial progress must be continuous"
		);
		check(!halfwaySecond.cursorVisible(), "cursor must be hidden in second blink half");

		Frame holding = timeline.frameAt(300L * MS);
		check(
			holding.visibleGraphemes() == 3
				&& holding.revealComplete()
				&& holding.holding()
				&& !holding.complete()
				&& holding.phase() == Phase.HOLDING,
			"completed reveal must enter hold without retaining the cursor"
		);
		check(!holding.cursorVisible(), "cursor disappears once final text is revealed");
		check(timeline.frameAt(500L * MS).complete(), "timeline completes after hold");
	}

	private static void checkSegmentPauses() {
		Timeline timeline = MessageFrameTimeline.compile(
			Component.literal("AB"),
			typewriterEffect(100L * MS, false, 0L),
			ResolvedDuration.contentDriven(0L),
			List.of(
				new SegmentSpan(1, new SegmentTiming(50L * MS, 100L * MS, false)),
				new SegmentSpan(1, new SegmentTiming(50L * MS, 0L, true))
			)
		);
		check(
			timeline.revealCompleteAtNanos() == 400L * MS,
			"segment pauses must be part of the reveal timeline"
		);
		Frame firstPauseAfter = timeline.frameAt(200L * MS);
		check(
			firstPauseAfter.visibleGraphemes() == 1
				&& firstPauseAfter.partialGraphemeProgress() == 0.0D,
			"pause-after must hold the completed segment without advancing the next grapheme"
		);
		Frame secondPartial = timeline.frameAt(325L * MS);
		check(
			secondPartial.visibleGraphemes() == 1
				&& Math.abs(secondPartial.partialGraphemeProgress() - 0.25D) < 0.000_001D,
			"pause-before must end before the next segment partial progress begins"
		);
		check(
			secondPartial.activeSegment().orElseThrow() == 1
				&& timeline.segmentWindows().get(1).emphasis(),
			"active segment and emphasis metadata must remain addressable"
		);
	}

	private static void checkFixedTotalAndSpeedClamps() {
		ResolvedDuration fittedDuration = new ResolvedDuration(
			DurationMode.FIXED_TOTAL,
			Optional.of(SECOND),
			Optional.of(2.0F),
			Optional.of(5.0F),
			100L * MS
		);
		Timeline fitted = MessageFrameTimeline.compile(
			Component.literal("ABCD"),
			typewriterEffect(100L * MS, false, 0L),
			fittedDuration,
			List.of(SegmentSpan.plain(4))
		);
		check(
			fitted.characterIntervalNanos() == 225L * MS,
			"fixed total must fit four characters into the 900ms typing budget"
		);
		check(
			fitted.frameAt(450L * MS).visibleGraphemes() == 2,
			"fitted fixed-total interval must drive exact visible count"
		);
		check(fitted.completeAtNanos() == SECOND, "fitted fixed total must end at 1s");

		ResolvedDuration tooFast = new ResolvedDuration(
			DurationMode.FIXED_TOTAL,
			Optional.of(200L * MS),
			Optional.empty(),
			Optional.of(5.0F),
			0L
		);
		Timeline clampedFast = MessageFrameTimeline.compile(
			Component.literal("ABCD"),
			typewriterEffect(100L * MS, false, 0L),
			tooFast,
			List.of(SegmentSpan.plain(4))
		);
		check(
			clampedFast.characterIntervalNanos() == 200L * MS,
			"maximum speed must prevent an unreadably fast fixed-total reveal"
		);
		check(
			clampedFast.completeAtNanos() == 800L * MS,
			"speed clamp may extend completion beyond an impossible fixed target"
		);

		ResolvedDuration tooSlow = new ResolvedDuration(
			DurationMode.FIXED_TOTAL,
			Optional.of(4L * SECOND),
			Optional.of(2.0F),
			Optional.of(5.0F),
			0L
		);
		Timeline clampedSlow = MessageFrameTimeline.compile(
			Component.literal("ABCD"),
			typewriterEffect(100L * MS, false, 0L),
			tooSlow,
			List.of(SegmentSpan.plain(4))
		);
		check(
			clampedSlow.characterIntervalNanos() == 500L * MS,
			"minimum speed must prevent an unnecessarily slow reveal"
		);
		check(
			clampedSlow.revealCompleteAtNanos() == 2L * SECOND
				&& clampedSlow.effectiveHoldNanos() == 2L * SECOND
				&& clampedSlow.completeAtNanos() == 4L * SECOND,
			"unused fixed-total time must become deterministic hold"
		);
	}

	private static void checkTrailingColorLifecycle() {
		Timeline tail = MessageFrameTimeline.compile(
			Component.literal("A").withColor(0x123456),
			colorEffect(100L * MS, 3, RestoreMode.GRADIENT),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(1))
		);
		check(tail.revealCompleteAtNanos() == 100L * MS, "single grapheme reveals at 100ms");
		check(
			tail.completeAtNanos() == 400L * MS,
			"three restore intervals must keep the dynamic frame alive through 400ms"
		);
		check(!tail.frameAt(399L * MS).complete(), "restore tail must not complete one ms early");
		check(tail.frameAt(400L * MS).complete(), "restore tail completes at its exact boundary");
		check(
			Math.abs(tail.colorRestoreAdvanceAt(250L * MS) - 1.5D) < 0.000_001D,
			"restore cursor must advance continuously after the last reveal"
		);
		check(
			tail.colorRestoreAdvanceAt(400L * MS) == 3.0D,
			"restore cursor must reach the full registered distance before completion"
		);

		Timeline held = MessageFrameTimeline.compile(
			Component.literal("A"),
			colorEffect(100L * MS, 3, RestoreMode.INSTANT),
			ResolvedDuration.contentDriven(500L * MS),
			List.of(SegmentSpan.plain(1))
		);
		check(
			held.completeAtNanos() == 600L * MS,
			"a longer registered hold must not be extended a second time by the restore tail"
		);
		check(
			held.colorRestoreAdvanceAt(400L * MS) == 3.0D
				&& !held.frameAt(400L * MS).complete(),
			"restored text must remain in its final style for the rest of a longer hold"
		);

		Timeline pauseCovered = MessageFrameTimeline.compile(
			Component.literal("A"),
			colorEffect(100L * MS, 3, RestoreMode.GRADIENT),
			ResolvedDuration.contentDriven(0L),
			List.of(
				new SegmentSpan(1, new SegmentTiming(0L, 500L * MS, false))
			)
		);
		check(
			pauseCovered.completeAtNanos() == 600L * MS
				&& pauseCovered.colorRestoreAdvanceAt(400L * MS) == 3.0D,
			"segment pause-after time must cover, not duplicate, the restore tail"
		);

		Timeline noRestore = MessageFrameTimeline.compile(
			Component.literal("A"),
			colorEffect(100L * MS, 0, RestoreMode.GRADIENT),
			ResolvedDuration.contentDriven(0L),
			List.of(SegmentSpan.plain(1))
		);
		check(
			noRestore.completeAtNanos() == 100L * MS
				&& noRestore.colorRestoreAdvanceAt(200L * MS) == 0.0D,
			"zero restore distance must preserve the original lifecycle"
		);
	}

	private static void checkStaticRevealAndBounds() {
		MutableComponent mutableFinal = Component.literal("AB");
		Timeline staticTimeline = MessageFrameTimeline.compile(
			mutableFinal,
			ResolvedEffect.none(),
			ResolvedDuration.contentDriven(0L),
			List.of(
				new SegmentSpan(1, new SegmentTiming(100L * MS, 0L, false)),
				SegmentSpan.plain(1)
			)
		);
		mutableFinal.append("C");
		check(
			staticTimeline.styledText().finalComponent().getString().equals("AB"),
			"compiled timeline must freeze a defensive component copy"
		);
		check(
			staticTimeline.frameAt(0L).visibleGraphemes() == 0,
			"static segment must still respect its registered pause-before"
		);
		check(
			staticTimeline.frameAt(100L * MS).visibleGraphemes() == 2
				&& staticTimeline.frameAt(100L * MS).complete(),
			"static segments reveal atomically after their pauses"
		);

		boolean rejected = false;
		try {
			MessageFrameTimeline.compile(
				Component.literal("AB"),
				ResolvedEffect.none(),
				ResolvedDuration.contentDriven(0L),
				List.of(SegmentSpan.plain(1))
			);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		check(rejected, "segment spans must cover the final component exactly");
	}

	private static ResolvedEffect typewriterEffect(
		final long intervalNanos,
		final boolean blink,
		final long blinkPeriodNanos
	) {
		return new ResolvedEffect(
			Optional.of(
				new Typewriter(
					intervalNanos,
					new Cursor(
						true,
						"▌",
						"#FFFFFF",
						blink,
						blink ? blinkPeriodNanos : 0L
					),
					Optional.empty(),
					0,
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
	}

	private static ResolvedEffect colorEffect(
		final long intervalNanos,
		final int restoreAfter,
		final RestoreMode mode
	) {
		return new ResolvedEffect(
			Optional.of(
				new Typewriter(
					intervalNanos,
					new Cursor(false, "▌", "#FFFFFF", false, 0L),
					Optional.of("#FEDCBA"),
					restoreAfter,
					mode
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

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
