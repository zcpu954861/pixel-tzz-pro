package io.github.zcpu954861.pixeltzzpro.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Small, allocation-bounded Unicode grapheme segmenter used by V3B text timelines.
 *
 * <p>Java's code-point APIs are not sufficient for typewriter animation: a cursor must never split
 * a combining mark, emoji skin tone, flag pair, variation selector, or a zero-width-joiner emoji
 * sequence. This implementation intentionally covers the extended clusters that occur in Minecraft
 * chat while keeping the runtime independent from an additional ICU dependency.
 */
public final class GraphemeClusters {
	private GraphemeClusters() {
	}

	public static List<Cluster> boundaries(final String text) {
		Objects.requireNonNull(text, "text");
		if (text.isEmpty()) {
			return List.of();
		}

		List<Cluster> clusters = new ArrayList<>();
		int clusterStart = 0;
		int index = 0;
		int previous = -1;
		int regionalIndicators = 0;
		while (index < text.length()) {
			int current = text.codePointAt(index);
			if (index > clusterStart && shouldBreak(previous, current, regionalIndicators)) {
				clusters.add(new Cluster(clusterStart, index));
				clusterStart = index;
				regionalIndicators = 0;
			}
			if (isRegionalIndicator(current)) {
				regionalIndicators++;
			} else if (!isExtend(current) && current != 0x200D) {
				regionalIndicators = 0;
			}
			previous = current;
			index += Character.charCount(current);
		}
		clusters.add(new Cluster(clusterStart, text.length()));
		return List.copyOf(clusters);
	}

	public static List<String> split(final String text) {
		Objects.requireNonNull(text, "text");
		return boundaries(text)
			.stream()
			.map(cluster -> text.substring(cluster.start(), cluster.end()))
			.toList();
	}

	public static int count(final String text) {
		return boundaries(text).size();
	}

	private static boolean shouldBreak(
		final int previous,
		final int current,
		final int regionalIndicators
	) {
		if (previous == '\r' && current == '\n') {
			return false;
		}
		if (isControl(previous) || isControl(current)) {
			return true;
		}
		if (current == 0x200D || isExtend(current)) {
			return false;
		}
		if (previous == 0x200D) {
			return false;
		}
		if (isRegionalIndicator(previous) && isRegionalIndicator(current)) {
			return regionalIndicators % 2 == 0;
		}
		return true;
	}

	private static boolean isControl(final int codePoint) {
		if (codePoint < 0) {
			return false;
		}
		int type = Character.getType(codePoint);
		return codePoint == '\r'
			|| codePoint == '\n'
			|| type == Character.CONTROL
			|| type == Character.FORMAT
				&& codePoint != 0x200D
				&& !isEmojiTag(codePoint);
	}

	private static boolean isExtend(final int codePoint) {
		int type = Character.getType(codePoint);
		return type == Character.NON_SPACING_MARK
			|| type == Character.COMBINING_SPACING_MARK
			|| type == Character.ENCLOSING_MARK
			|| codePoint >= 0xFE00 && codePoint <= 0xFE0F
			|| codePoint >= 0xE0100 && codePoint <= 0xE01EF
			|| codePoint >= 0x1F3FB && codePoint <= 0x1F3FF
			|| codePoint == 0x20E3
			|| isEmojiTag(codePoint);
	}

	private static boolean isEmojiTag(final int codePoint) {
		return codePoint >= 0xE0020 && codePoint <= 0xE007F;
	}

	private static boolean isRegionalIndicator(final int codePoint) {
		return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
	}

	public record Cluster(int start, int end) {
		public Cluster {
			if (start < 0 || end <= start) {
				throw new IllegalArgumentException("grapheme cluster requires a non-empty range");
			}
		}
	}
}
