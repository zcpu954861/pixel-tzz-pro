package io.github.zcpu954861.pixeltzzpro.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.Frame;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.SegmentWindow;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.Timeline;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter.FrameSpec;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter.StyledGrapheme;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter.StyledText;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Pure, frame-rate-independent composition of the visual V3B text primitives.
 *
 * <p>This class never draws directly. It produces a rich component plus the transform that the
 * Chat or HUD adapter can consume. Seeking to any elapsed time gives the same frame, so low FPS,
 * late packets, pause/resume and reconnect do not replay missed animation steps.
 */
public final class StyledMessageEffects {
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;
	private static final int HIGH_CONTRAST_CURSOR_COLOR = 0x00FFFF;
	private static final int HIGH_CONTRAST_FRESH_COLOR = 0xFFFF00;

	private StyledMessageEffects() {
	}

	public static VisualFrame frame(
		final Timeline timeline,
		final Frame frame,
		final UUID instanceId,
		final int nodeOrdinal,
		final boolean reducedMotion,
		final boolean cursorBlinkEnabled
	) {
		return frame(
			timeline,
			frame,
			instanceId,
			nodeOrdinal,
			reducedMotion,
			cursorBlinkEnabled,
			false
		);
	}

	public static VisualFrame frame(
		final Timeline timeline,
		final Frame frame,
		final UUID instanceId,
		final int nodeOrdinal,
		final boolean reducedMotion,
		final boolean cursorBlinkEnabled,
		final boolean highContrast
	) {
		Objects.requireNonNull(timeline, "timeline");
		Objects.requireNonNull(frame, "frame");
		Objects.requireNonNull(instanceId, "instanceId");

		StyledText styled = applyGradient(
			timeline.styledText(),
			timeline.effect().gradient(),
			frame.elapsedNanos()
		);
		Component component = buildComponent(
			styled,
			timeline,
			timeline.effect(),
			frame,
			instanceId,
			nodeOrdinal,
			reducedMotion,
			cursorBlinkEnabled,
			highContrast
		);
		return new VisualFrame(
			component,
			opacity(timeline, frame.elapsedNanos(), reducedMotion),
			translateX(timeline, frame.elapsedNanos(), reducedMotion),
			translateY(timeline, frame.elapsedNanos(), reducedMotion),
			scale(timeline, frame.elapsedNanos(), reducedMotion)
		);
	}

	private static Component buildComponent(
		final StyledText styled,
		final Timeline timeline,
		final MessagePlaybackPlan.ResolvedEffect effect,
		final Frame frame,
		final UUID instanceId,
		final int nodeOrdinal,
		final boolean reducedMotion,
		final boolean cursorBlinkEnabled,
		final boolean highContrast
	) {
		Optional<MessagePlaybackPlan.Typewriter> optionalTypewriter = effect.typewriter();
		int visible = Math.min(frame.visibleGraphemes(), styled.graphemes().size());
		double colorRestoreAdvance = visible >= styled.graphemes().size()
			? timeline.colorRestoreAdvanceAt(frame.elapsedNanos())
			: 0.0D;
		int colorCursor = Math.addExact(
			visible,
			(int)Math.floor(colorRestoreAdvance)
		);
		double colorCursorPartial = colorRestoreAdvance > 0.0D
			? colorRestoreAdvance - Math.floor(colorRestoreAdvance)
			: frame.partialGraphemeProgress();
		MutableComponent result;
		if (optionalTypewriter.isPresent()) {
			MessagePlaybackPlan.Typewriter typewriter = optionalTypewriter.orElseThrow();
			result = Component.empty().append(
				StyledTypewriter.frame(
					styled,
					new FrameSpec(
						colorCursor,
						false,
						"",
						OptionalInt.empty(),
						highContrast
							? OptionalInt.of(HIGH_CONTRAST_FRESH_COLOR)
							: color(typewriter.freshColor().orElse(null)),
						typewriter.restoreAfterCharacters(),
						typewriter.restoreMode() == MessagePlaybackPlan.RestoreMode.GRADIENT
							? RestoreMode.GRADIENT
							: RestoreMode.INSTANT,
						colorCursorPartial,
						highContrast
					)
				)
			);
			boolean cursorVisible = typewriter.cursor().enabled()
				&& (
					!typewriter.cursor().blink()
						|| !cursorBlinkEnabled
						|| frame.cursorVisible()
				);
			if (
				cursorVisible
					&& visible < styled.graphemes().size()
					&& !typewriter.cursor().glyph().isEmpty()
			) {
				Style style = Style.EMPTY;
				OptionalInt cursorColor = highContrast
					? OptionalInt.of(HIGH_CONTRAST_CURSOR_COLOR)
					: color(typewriter.cursor().color());
				if (cursorColor.isPresent()) {
					style = style.withColor(cursorColor.getAsInt());
				}
				if (highContrast) {
					style = style.withUnderlined(true);
				}
				result.append(Component.literal(typewriter.cursor().glyph()).withStyle(style));
			}
		} else {
			result = Component.empty().append(
				StyledTypewriter.frame(styled, StyledTypewriter.FrameSpec.plain(visible))
			);
		}

		if (!reducedMotion && effect.scramble().isPresent() && visible < styled.graphemes().size()) {
			appendScramble(
				result,
				styled,
				visible,
				effect.scramble().orElseThrow(),
				frame.elapsedNanos(),
				instanceId,
				nodeOrdinal
			);
		}
		return result;
	}

	private static void appendScramble(
		final MutableComponent result,
		final StyledText styled,
		final int firstHidden,
		final MessagePlaybackPlan.Scramble scramble,
		final long elapsedNanos,
		final UUID instanceId,
		final int nodeOrdinal
	) {
		List<String> alphabet = GraphemeClusters.split(scramble.characters());
		if (alphabet.isEmpty()) {
			return;
		}
		long frameIndex = elapsedNanos / scramble.characterIntervalNanos();
		for (int index = firstHidden; index < styled.graphemes().size(); index++) {
			StyledGrapheme grapheme = styled.graphemes().get(index);
			String text = grapheme.text();
			if (isLayoutCharacter(text)) {
				result.append(grapheme.component(StyledMessageEffects::nonInteractive));
				continue;
			}
			long mixed = mix(
				instanceId.getMostSignificantBits()
					^ Long.rotateLeft(instanceId.getLeastSignificantBits(), 23)
					^ (long)nodeOrdinal * 0x9E3779B97F4A7C15L
					^ (long)index * 0x632BE59BD9B4E019L
					^ frameIndex * 0x94D049BB133111EBL
			);
			int choice = (int)Long.remainderUnsigned(mixed, alphabet.size());
			result.append(
				Component.literal(alphabet.get(choice))
					.withStyle(nonInteractive(grapheme.style()))
			);
		}
	}

	private static StyledText applyGradient(
		final StyledText source,
		final Optional<MessagePlaybackPlan.Gradient> gradient,
		final long elapsedNanos
	) {
		if (gradient.isEmpty()) {
			return source;
		}
		MessagePlaybackPlan.Gradient effect = gradient.orElseThrow();
		OptionalInt from = color(effect.fromColor());
		OptionalInt to = color(effect.toColor());
		if (from.isEmpty() || to.isEmpty()) {
			return source;
		}
		double progress = Math.clamp(
			elapsedNanos / (double)effect.durationNanos(),
			0.0D,
			1.0D
		);
		int value = interpolateRgb(from.getAsInt(), to.getAsInt(), progress);
		List<StyledGrapheme> transformed = new ArrayList<>(source.graphemes().size());
		for (StyledGrapheme grapheme : source.graphemes()) {
			transformed.add(grapheme.mapStyles(style -> style.withColor(value)));
		}
		return new StyledText(source.finalComponent(), transformed);
	}

	private static float opacity(
		final Timeline timeline,
		final long elapsedNanos,
		final boolean reducedMotion
	) {
		if (reducedMotion || timeline.effect().fade().isEmpty()) {
			return 1.0F;
		}
		MessagePlaybackPlan.Fade fade = timeline.effect().fade().orElseThrow();
		double enter = fade.enterNanos() == 0L
			? 1.0D
			: Math.clamp(elapsedNanos / (double)fade.enterNanos(), 0.0D, 1.0D);
		long exitStart = Math.max(0L, timeline.completeAtNanos() - fade.exitNanos());
		double exit = fade.exitNanos() == 0L || elapsedNanos <= exitStart
			? 1.0D
			: 1.0D - Math.clamp(
				(elapsedNanos - exitStart) / (double)fade.exitNanos(),
				0.0D,
				1.0D
			);
		return (float)Math.clamp(Math.min(enter, exit), 0.0D, 1.0D);
	}

	private static float translateX(
		final Timeline timeline,
		final long elapsedNanos,
		final boolean reducedMotion
	) {
		return motionProgress(timeline, elapsedNanos, reducedMotion)
			.map(sample -> sample.effect().slideX() * (1.0F - sample.progress()))
			.orElse(0.0F);
	}

	private static float translateY(
		final Timeline timeline,
		final long elapsedNanos,
		final boolean reducedMotion
	) {
		return motionProgress(timeline, elapsedNanos, reducedMotion)
			.map(sample -> sample.effect().slideY() * (1.0F - sample.progress()))
			.orElse(0.0F);
	}

	private static float scale(
		final Timeline timeline,
		final long elapsedNanos,
		final boolean reducedMotion
	) {
		float scale = motionProgress(timeline, elapsedNanos, reducedMotion)
			.map(
				sample -> sample.effect().startScale()
					+ (1.0F - sample.effect().startScale()) * sample.progress()
			)
			.orElse(1.0F);
		if (reducedMotion || timeline.effect().pulse().isEmpty()) {
			return scale;
		}
		MessagePlaybackPlan.Pulse pulse = timeline.effect().pulse().orElseThrow();
		Optional<SegmentWindow> emphasis = timeline.segmentWindows()
			.stream()
			.filter(SegmentWindow::emphasis)
			.filter(
				window -> elapsedNanos >= window.startNanos()
					&& elapsedNanos < safeAdd(window.startNanos(), pulse.durationNanos())
			)
			.findFirst();
		if (emphasis.isEmpty()) {
			return scale;
		}
		double progress = Math.clamp(
			(elapsedNanos - emphasis.orElseThrow().startNanos())
				/ (double)pulse.durationNanos(),
			0.0D,
			1.0D
		);
		float pulseScale = 1.0F
			+ (pulse.scale() - 1.0F) * (float)Math.sin(Math.PI * progress);
		return scale * pulseScale;
	}

	private static Optional<MotionSample> motionProgress(
		final Timeline timeline,
		final long elapsedNanos,
		final boolean reducedMotion
	) {
		if (reducedMotion || timeline.effect().motion().isEmpty()) {
			return Optional.empty();
		}
		MessagePlaybackPlan.Motion motion = timeline.effect().motion().orElseThrow();
		double linear = Math.clamp(
			elapsedNanos / (double)motion.durationNanos(),
			0.0D,
			1.0D
		);
		float eased = (float)(1.0D - Math.pow(1.0D - linear, 3.0D));
		return Optional.of(new MotionSample(motion, eased));
	}

	private static boolean isLayoutCharacter(final String value) {
		if (value.isEmpty()) {
			return true;
		}
		for (int offset = 0; offset < value.length();) {
			int codePoint = value.codePointAt(offset);
			if (!Character.isWhitespace(codePoint)) {
				return false;
			}
			offset += Character.charCount(codePoint);
		}
		return true;
	}

	private static Style nonInteractive(final Style source) {
		return source
			.withClickEvent(null)
			.withHoverEvent(null)
			.withInsertion(null);
	}

	private static OptionalInt color(final String value) {
		if (value == null) {
			return OptionalInt.empty();
		}
		return TextColor.parseColor(value)
			.result()
			.map(parsed -> OptionalInt.of(parsed.getValue()))
			.orElseGet(OptionalInt::empty);
	}

	private static int interpolateRgb(
		final int start,
		final int end,
		final double progress
	) {
		int red = channel(start, end, 16, progress);
		int green = channel(start, end, 8, progress);
		int blue = channel(start, end, 0, progress);
		return red << 16 | green << 8 | blue;
	}

	private static int channel(
		final int start,
		final int end,
		final int shift,
		final double progress
	) {
		int from = start >> shift & 0xFF;
		int to = end >> shift & 0xFF;
		return (int)Math.round(from + (to - from) * progress);
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static long safeAdd(final long left, final long right) {
		return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
	}

	public record VisualFrame(
		Component component,
		float opacity,
		float translateX,
		float translateY,
		float scale
	) {
		public VisualFrame {
			component = Objects.requireNonNull(component, "component");
			if (
				!Float.isFinite(opacity)
					|| opacity < 0.0F
					|| opacity > 1.0F
					|| !Float.isFinite(translateX)
					|| !Float.isFinite(translateY)
					|| !Float.isFinite(scale)
					|| scale <= 0.0F
			) {
				throw new IllegalArgumentException("visual message frame is invalid");
			}
		}
	}

	private record MotionSample(MessagePlaybackPlan.Motion effect, float progress) {
		private MotionSample {
			effect = Objects.requireNonNull(effect, "effect");
			if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
				throw new IllegalArgumentException("motion progress must remain within 0..1");
			}
		}
	}
}
