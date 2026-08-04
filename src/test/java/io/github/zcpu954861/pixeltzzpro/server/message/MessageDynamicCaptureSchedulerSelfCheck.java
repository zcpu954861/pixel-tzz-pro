package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Conflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Cursor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldReservation;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Typewriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Exact-value scheduling checks for fields following generously reserved dynamic text. */
public final class MessageDynamicCaptureSchedulerSelfCheck {
	private static final long MS = 1_000_000L;

	private MessageDynamicCaptureSchedulerSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		ResolvedTextNode node = node();
		check(
			MessageDynamicCaptureScheduler.captureOffset(
				node,
				1,
				CaptureMode.ON_DISPLAY,
				Map.of()
			).orElseThrow() == 450L * MS,
			"on_display must capture one tick before the node starts"
		);
		check(
			MessageDynamicCaptureScheduler.captureOffset(
				node,
				1,
				CaptureMode.ON_FIRST_FIELD,
				Map.of()
			).orElseThrow() == 550L * MS,
			"on_first_field must use the first dynamic boundary, not this field's reservation"
		);
		long earliest = MessageDynamicCaptureScheduler.captureOffset(
			node,
			1,
			CaptureMode.PER_FIELD,
			Map.of()
		).orElseThrow();
		long exact = MessageDynamicCaptureScheduler.captureOffset(
			node,
			1,
			CaptureMode.PER_FIELD,
			Map.of(0, component("甲乙"))
		).orElseThrow();
		check(
			earliest == 650L * MS,
			"an unresolved preceding field must occupy zero reveal time at the next client gate"
		);
		check(
			exact == 850L * MS,
			"a frozen preceding field must use its exact graphemes instead of max reservation"
		);
		check(
			exact < 10_000L * MS,
			"a 96-grapheme layout reservation must never delay a short live field"
		);

		System.out.println("MESSAGE_DYNAMIC_CAPTURE_SCHEDULER_SELF_CHECK=PASS");
	}

	private static ResolvedTextNode node() {
		ResolvedEffect effect = new ResolvedEffect(
			Optional.of(
				new Typewriter(
					100L * MS,
					new Cursor(true, "█", "#FFFFFF", false, 0L),
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
		FieldReservation generous = new FieldReservation(
			OptionalInt.of(96),
			OptionalInt.of(420),
			OptionalInt.of(2)
		);
		return new ResolvedTextNode(
			0,
			500L * MS,
			0,
			Conflict.PARALLEL,
			Channel.SUBTITLE,
			List.of(
				new StaticSegment(component("前"), SegmentTiming.none()),
				new FieldSlotSegment(0, Optional.of(component("回退")), generous, SegmentTiming.none()),
				new StaticSegment(component("/"), SegmentTiming.none()),
				new FieldSlotSegment(1, Optional.of(component("回退")), generous, SegmentTiming.none())
			),
			effect,
			ResolvedLayout.standard(),
			ResolvedDuration.contentDriven(0L),
			new ResolvedSpeaker(SpeakerKind.SYSTEM, Optional.empty(), Optional.empty())
		);
	}

	private static ResolvedComponent component(final String text) {
		return ResolvedComponent.parseCanonical("{\"text\":\"" + text + "\"}");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
