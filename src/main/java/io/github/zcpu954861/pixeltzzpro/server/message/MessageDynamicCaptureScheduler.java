package io.github.zcpu954861.pixeltzzpro.server.message;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.SegmentSpan;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;

/**
 * Recomputes a dynamic-field capture boundary from the exact values already frozen for a stream.
 *
 * <p>The projection compiler must reserve worst-case field widths before any values exist. Those
 * reservations are correct for layout, but they are deliberately larger than the text most calls
 * produce. Reusing the reserved width as a later {@code per_field} capture clock makes the client
 * reach the field first and visibly wait. This scheduler mirrors the client's current timeline:
 * known fields use their exact grapheme count and not-yet-known fields occupy zero reveal time.
 * The server can therefore resolve the next field one tick before the earliest frame that may show
 * its cursor, without weakening the registered capture mode.</p>
 */
public final class MessageDynamicCaptureScheduler {
	public static final long LOOKAHEAD_NANOS = 50_000_000L;

	private MessageDynamicCaptureScheduler() {
	}

	public static OptionalLong captureOffset(
		final ResolvedTextNode node,
		final int slot,
		final CaptureMode mode,
		final Map<Integer, ResolvedComponent> frozenFields
	) {
		Objects.requireNonNull(node, "node");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(frozenFields, "frozenFields");
		if (mode == CaptureMode.ON_DISPLAY) {
			return OptionalLong.of(lookAhead(node.startOffsetNanos()));
		}

		int targetSegment = -1;
		int firstFieldSegment = -1;
		MutableComponent combined = Component.empty();
		List<SegmentSpan> spans = new ArrayList<>(node.segments().size());
		for (int index = 0; index < node.segments().size(); index++) {
			ResolvedSegment segment = node.segments().get(index);
			Component value;
			if (segment instanceof StaticSegment fixed) {
				value = parse(fixed.component());
			} else {
				FieldSlotSegment field = (FieldSlotSegment)segment;
				if (firstFieldSegment < 0) {
					firstFieldSegment = index;
				}
				if (field.slot() == slot) {
					targetSegment = index;
				}
				ResolvedComponent frozen = frozenFields.get(field.slot());
				value = frozen == null ? Component.empty() : parse(frozen);
			}
			combined.append(value.copy());
			spans.add(
				new SegmentSpan(
					StyledTypewriter.flatten(value.copy()).graphemes().size(),
					segment.timing()
				)
			);
		}
		if (targetSegment < 0 || firstFieldSegment < 0) {
			return OptionalLong.empty();
		}
		int boundarySegment = mode == CaptureMode.ON_FIRST_FIELD
			? firstFieldSegment
			: targetSegment;
		MessageFrameTimeline.Timeline timeline = MessageFrameTimeline.compile(
			combined,
			node.effect(),
			node.duration(),
			spans
		);
		long boundary = safeAdd(
			timeline.segmentWindows().get(boundarySegment).startNanos(),
			node.segments().get(boundarySegment).timing().pauseBeforeNanos()
		);
		return OptionalLong.of(lookAhead(safeAdd(node.startOffsetNanos(), boundary)));
	}

	private static Component parse(final ResolvedComponent component) {
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(component.canonicalJson()))
			.getOrThrow();
	}

	private static long lookAhead(final long boundary) {
		return Math.max(0L, boundary - LOOKAHEAD_NANOS);
	}

	private static long safeAdd(final long left, final long right) {
		try {
			return Math.addExact(left, right);
		} catch (ArithmeticException overflow) {
			return Long.MAX_VALUE;
		}
	}
}
