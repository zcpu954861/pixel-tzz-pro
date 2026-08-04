package io.github.zcpu954861.pixeltzzpro.client.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Internal measurement-only projection for one animated vanilla Chat entry.
 *
 * <p>The measurement component may contain sentinel-tagged future graphemes, reservation glyphs,
 * and newlines. They are consumed by vanilla's real splitter and then removed before the result reaches
 * {@link GuiMessage.Line}. Consequently the clean {@link GuiMessage#content()} remains the only
 * copyable/history-bearing text. Future-only lines are removed as soon as any real glyph is
 * visible, so a reservation cannot surface as a blank third Chat line. One hidden anchor is kept
 * only before the first glyph; the in-place adapter needs that parent line to attach the first
 * visible frame without rebuilding all Chat history.</p>
 */
public final class ChatLineProjection {
	private static final int VANILLA_DISPLAY_LINE_LIMIT = 100;
	private static final String PADDING_SENTINEL =
		"pixel_tzz_pro:internal/chat_prelayout/7cb31eb4";
	private static final FormattedCharSequence HIDDEN_LINE = sink -> true;

	private final Component measurement;

	public ChatLineProjection(final Component measurement) {
		this.measurement = Objects.requireNonNull(measurement, "measurement").copy();
	}

	static Component hidden(final Component value) {
		Objects.requireNonNull(value, "value");
		MutableComponent result = Component.empty();
		value.visit(
			(style, contents) -> {
				result.append(
					Component.literal(contents)
						.withStyle(style.withInsertion(PADDING_SENTINEL))
				);
				return Optional.empty();
			},
			Style.EMPTY
		);
		return result;
	}

	public static boolean isHiddenLine(final FormattedCharSequence line) {
		return line == HIDDEN_LINE;
	}

	static boolean isPaddingStyle(final Style style) {
		return PADDING_SENTINEL.equals(Objects.requireNonNull(style, "style").getInsertion());
	}

	public List<FormattedCharSequence> split(
		final GuiMessage clean,
		final Font font,
		final int maxWidth
	) {
		Objects.requireNonNull(clean, "clean");
		Objects.requireNonNull(font, "font");
		GuiMessage measurementEntry = new GuiMessage(
			clean.addedTime(),
			this.measurement,
			clean.signature(),
			clean.source(),
			clean.tag()
		);
		List<FormattedCharSequence> projected = new ArrayList<>(
			measurementEntry.splitLines(font, maxWidth)
			.stream()
			.map(ChatLineProjection::withoutPadding)
			.toList()
		);
		boolean hasVisibleLine = projected.stream()
			.anyMatch(line -> !isHiddenLine(line));
		if (hasVisibleLine) {
			projected.removeIf(ChatLineProjection::isHiddenLine);
		} else if (projected.size() > 1) {
			projected.subList(1, projected.size()).clear();
		}
		// Vanilla keeps at most 100 display lines. Drop fully hidden reservation lines
		// before allowing that cap to evict real visible text from this message.
		for (
			int index = projected.size() - 1;
			projected.size() > VANILLA_DISPLAY_LINE_LIMIT && index >= 0;
			index--
		) {
			if (isHiddenLine(projected.get(index))) {
				projected.remove(index);
			}
		}
		if (projected.size() > VANILLA_DISPLAY_LINE_LIMIT) {
			return List.copyOf(
				projected.subList(
					projected.size() - VANILLA_DISPLAY_LINE_LIMIT,
					projected.size()
				)
			);
		}
		return List.copyOf(projected);
	}

	private static FormattedCharSequence withoutPadding(
		final FormattedCharSequence measured
	) {
		List<ProjectedGlyph> visible = new ArrayList<>();
		measured.accept((position, style, codepoint) -> {
			if (!isPaddingStyle(style)) {
				visible.add(new ProjectedGlyph(style, codepoint));
			}
			return true;
		});
		if (visible.isEmpty()) {
			return HIDDEN_LINE;
		}
		return sink -> {
			int position = 0;
			for (ProjectedGlyph glyph : visible) {
				if (!sink.accept(position, glyph.style(), glyph.codepoint())) {
					return false;
				}
				position += Character.charCount(glyph.codepoint());
			}
			return true;
		};
	}

	private record ProjectedGlyph(Style style, int codepoint) {
		private ProjectedGlyph {
			style = Objects.requireNonNull(style, "style");
		}
	}
}
