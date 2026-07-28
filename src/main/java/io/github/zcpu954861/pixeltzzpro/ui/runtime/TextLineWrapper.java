package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Width-aware text wrapping with Unicode line breaks and grapheme-safe hard wrapping.
 */
public final class TextLineWrapper {
	private TextLineWrapper() {
	}

	public static List<String> wrap(
		final String text,
		final int maximumWidth,
		final ToIntFunction<String> measure
	) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(measure, "measure");
		int width = Math.max(1, maximumWidth);
		List<String> result = new ArrayList<>();
		for (String paragraph : text.split("\\R", -1)) {
			wrapParagraph(paragraph, width, measure, result);
		}
		return List.copyOf(result);
	}

	private static void wrapParagraph(
		final String paragraph,
		final int maximumWidth,
		final ToIntFunction<String> measure,
		final List<String> target
	) {
		if (paragraph.isEmpty()) {
			target.add("");
			return;
		}

		BreakIterator lines = BreakIterator.getLineInstance(Locale.ROOT);
		lines.setText(paragraph);
		StringBuilder current = new StringBuilder();
		for (
			int start = lines.first(), end = lines.next();
			end != BreakIterator.DONE;
			start = end, end = lines.next()
		) {
			appendSegment(
				paragraph.substring(start, end),
				maximumWidth,
				measure,
				current,
				target
			);
		}
		if (!current.isEmpty()) {
			target.add(current.toString().stripTrailing());
		}
	}

	private static void appendSegment(
		final String input,
		final int maximumWidth,
		final ToIntFunction<String> measure,
		final StringBuilder current,
		final List<String> target
	) {
		String segment = input;
		if (
			!current.isEmpty()
				&& measured(measure, current + segment) > maximumWidth
		) {
			target.add(current.toString().stripTrailing());
			current.setLength(0);
			segment = segment.stripLeading();
		}
		if (segment.isEmpty()) {
			return;
		}
		if (measured(measure, segment) <= maximumWidth) {
			current.append(segment);
			return;
		}

		BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
		graphemes.setText(segment);
		for (
			int start = graphemes.first(), end = graphemes.next();
			end != BreakIterator.DONE;
			start = end, end = graphemes.next()
		) {
			String grapheme = segment.substring(start, end);
			if (
				!current.isEmpty()
					&& measured(measure, current + grapheme) > maximumWidth
			) {
				target.add(current.toString().stripTrailing());
				current.setLength(0);
			}
			current.append(grapheme);
			if (measured(measure, current.toString()) > maximumWidth) {
				// A single grapheme may be wider than the viewport, but it must never be split.
				target.add(current.toString());
				current.setLength(0);
			}
		}
	}

	private static int measured(
		final ToIntFunction<String> measure,
		final CharSequence value
	) {
		return Math.max(0, measure.applyAsInt(value.toString()));
	}
}
