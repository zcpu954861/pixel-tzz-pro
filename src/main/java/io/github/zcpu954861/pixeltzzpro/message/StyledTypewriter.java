package io.github.zcpu954861.pixeltzzpro.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestoreMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Builds one V3B typewriter frame without discarding vanilla rich-text styles or interactions.
 *
 * <p>The final component remains authoritative. A frame is a temporary projection of its flat
 * styled grapheme stream; click, hover, insertion, font, bold, and every other style attribute are
 * retained for already visible characters. The cursor is always a separate non-interactive
 * component, so hidden text never creates an invisible click target.
 */
public final class StyledTypewriter {
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;

	private StyledTypewriter() {
	}

	public static StyledText flatten(final Component component) {
		Objects.requireNonNull(component, "component");
		StringBuilder plain = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		component.visit(
			(style, contents) -> {
				plain.append(contents);
				for (int index = 0; index < contents.length(); index++) {
					styles.add(style);
				}
				return Optional.empty();
			},
			Style.EMPTY
		);
		List<StyledGrapheme> graphemes = new ArrayList<>();
		String text = plain.toString();
		for (GraphemeClusters.Cluster cluster : GraphemeClusters.boundaries(text)) {
			List<StyledSpan> spans = new ArrayList<>();
			int cursor = cluster.start();
			while (cursor < cluster.end()) {
				Style style = styles.get(cursor);
				int end = cursor + 1;
				while (end < cluster.end() && styles.get(end).equals(style)) {
					end++;
				}
				spans.add(new StyledSpan(text.substring(cursor, end), style));
				cursor = end;
			}
			graphemes.add(
				new StyledGrapheme(
					text.substring(cluster.start(), cluster.end()),
					spans
				)
			);
		}
		return new StyledText(component, graphemes);
	}

	public static Component frame(
		final StyledText text,
		final FrameSpec spec
	) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(spec, "spec");
		int visible = Math.min(spec.visibleGraphemes(), text.graphemes().size());
		MutableComponent frame = Component.empty();
		for (int index = 0; index < visible; index++) {
			StyledGrapheme grapheme = text.graphemes().get(index);
			int graphemeIndex = index;
			frame.append(
				grapheme.component(style -> temporaryColor(
					style,
					graphemeIndex,
					spec.visibleGraphemes(),
					spec
				))
			);
		}
		if (visible < text.graphemes().size() && spec.cursorVisible() && !spec.cursorGlyph().isEmpty()) {
			Style cursorStyle = Style.EMPTY;
			if (spec.cursorColor().isPresent()) {
				cursorStyle = cursorStyle.withColor(spec.cursorColor().getAsInt());
			}
			frame.append(Component.literal(spec.cursorGlyph()).withStyle(cursorStyle));
		}
		return frame;
	}

	private static Style temporaryColor(
		final Style original,
		final int index,
		final int visible,
		final FrameSpec spec
	) {
		if (spec.freshColor().isEmpty() || spec.restoreAfterGraphemes() <= 0) {
			return original;
		}
		int distanceBehindCursor = visible - 1 - index;
		if (distanceBehindCursor >= spec.restoreAfterGraphemes()) {
			return original;
		}
		int fresh = spec.freshColor().getAsInt();
		Style temporary;
		if (spec.restoreMode() == RestoreMode.INSTANT) {
			temporary = original.withColor(fresh);
		} else {
			int target = original.getColor() == null
				? DEFAULT_TEXT_COLOR
				: original.getColor().getValue();
			double restored = Math.clamp(
				(distanceBehindCursor + spec.partialCharacterProgress())
					/ spec.restoreAfterGraphemes(),
				0.0D,
				1.0D
			);
			temporary = original.withColor(interpolateRgb(fresh, target, restored));
		}
		return spec.emphasizeFresh()
			? temporary.withUnderlined(true)
			: temporary;
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

	public record StyledText(Component finalComponent, List<StyledGrapheme> graphemes) {
		public StyledText {
			finalComponent = Objects.requireNonNull(finalComponent, "finalComponent");
			graphemes = List.copyOf(graphemes);
		}
	}

	public record StyledGrapheme(String text, List<StyledSpan> spans) {
		public StyledGrapheme {
			text = Objects.requireNonNull(text, "text");
			spans = List.copyOf(spans);
			if (GraphemeClusters.count(text) != 1) {
				throw new IllegalArgumentException("styled grapheme must contain exactly one cluster");
			}
			if (
				spans.isEmpty()
					|| !spans.stream().map(StyledSpan::text).reduce("", String::concat).equals(text)
			) {
				throw new IllegalArgumentException(
					"styled grapheme spans must cover the cluster exactly"
				);
			}
		}

		public StyledGrapheme(final String text, final Style style) {
			this(text, List.of(new StyledSpan(text, style)));
		}

		/** The leading style is used only for one-glyph temporary substitutions. */
		public Style style() {
			return this.spans.getFirst().style();
		}

		public Component component() {
			return component(UnaryOperator.identity());
		}

		public Component component(final UnaryOperator<Style> styleTransform) {
			Objects.requireNonNull(styleTransform, "styleTransform");
			MutableComponent result = Component.empty();
			for (StyledSpan span : this.spans) {
				result.append(
					Component.literal(span.text())
						.withStyle(styleTransform.apply(span.style()))
				);
			}
			return result;
		}

		public StyledGrapheme mapStyles(final UnaryOperator<Style> styleTransform) {
			Objects.requireNonNull(styleTransform, "styleTransform");
			return new StyledGrapheme(
				this.text,
				this.spans.stream()
					.map(span -> new StyledSpan(span.text(), styleTransform.apply(span.style())))
					.toList()
			);
		}
	}

	public record StyledSpan(String text, Style style) {
		public StyledSpan {
			text = Objects.requireNonNull(text, "text");
			style = Objects.requireNonNull(style, "style");
			if (text.isEmpty()) {
				throw new IllegalArgumentException("styled span cannot be empty");
			}
		}
	}

	public record FrameSpec(
		int visibleGraphemes,
		boolean cursorVisible,
		String cursorGlyph,
		OptionalInt cursorColor,
		OptionalInt freshColor,
		int restoreAfterGraphemes,
		RestoreMode restoreMode,
		double partialCharacterProgress,
		boolean emphasizeFresh
	) {
		public FrameSpec(
			final int visibleGraphemes,
			final boolean cursorVisible,
			final String cursorGlyph,
			final OptionalInt cursorColor,
			final OptionalInt freshColor,
			final int restoreAfterGraphemes,
			final RestoreMode restoreMode,
			final double partialCharacterProgress
		) {
			this(
				visibleGraphemes,
				cursorVisible,
				cursorGlyph,
				cursorColor,
				freshColor,
				restoreAfterGraphemes,
				restoreMode,
				partialCharacterProgress,
				false
			);
		}

		public FrameSpec {
			if (visibleGraphemes < 0 || restoreAfterGraphemes < 0) {
				throw new IllegalArgumentException("frame character counts cannot be negative");
			}
			cursorGlyph = Objects.requireNonNull(cursorGlyph, "cursorGlyph");
			cursorColor = Objects.requireNonNull(cursorColor, "cursorColor");
			freshColor = Objects.requireNonNull(freshColor, "freshColor");
			restoreMode = Objects.requireNonNull(restoreMode, "restoreMode");
			if (
				!Double.isFinite(partialCharacterProgress)
					|| partialCharacterProgress < 0.0D
					|| partialCharacterProgress > 1.0D
			) {
				throw new IllegalArgumentException(
					"partialCharacterProgress must be within 0..1"
				);
			}
		}

		public static FrameSpec plain(final int visibleGraphemes) {
			return new FrameSpec(
				visibleGraphemes,
				false,
				"",
				OptionalInt.empty(),
				OptionalInt.empty(),
				0,
				RestoreMode.INSTANT,
				0.0D,
				false
			);
		}
	}
}
