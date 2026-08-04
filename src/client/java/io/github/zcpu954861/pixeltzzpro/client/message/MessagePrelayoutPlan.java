package io.github.zcpu954861.pixeltzzpro.client.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldReservation;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter.StyledGrapheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client-only layout projection for one authorized message node.
 *
 * <p>The projection is never drawn and never replaces the authoritative final component. Known
 * text is retained solely so the real client font can measure future text, while an unresolved
 * field contributes its declared reservation. Once a field resolves, its complete value remains
 * present and the reservation only pads a value that is smaller than the registered envelope; a
 * larger value is left untouched for the node's normal overflow policy.</p>
 */
public final class MessagePrelayoutPlan {
	private static final String MEASURE_GLYPH = "W";
	private static final int MAX_MEASURE_WIDTH = 1_000_000;

	private final List<Part> parts;

	private MessagePrelayoutPlan(final List<Part> parts) {
		this.parts = List.copyOf(parts);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static MessagePrelayoutPlan fixed(final Component component) {
		return builder().append(component).build();
	}

	/** Builds the non-rendered measurement component with the active client font. */
	public Component materialize(final Font font) {
		Objects.requireNonNull(font, "font");
		MutableComponent result = Component.empty();
		for (Part part : this.parts) {
			if (part instanceof TextPart text) {
				result.append(text.component().copy());
			} else {
				appendFieldEnvelope(result, font, (FieldPart)part);
			}
		}
		return result;
	}

	/**
	 * Builds Chat's measurement-only frame for the still-hidden suffix. The returned component must
	 * only be passed to {@link ChatLineProjection}; its sentinel padding is removed after vanilla
	 * computes line breaks and must never become {@code GuiMessage.content}.
	 */
	public Component padChatFrame(
		final Font font,
		final Component visible,
		final int visibleSourceGraphemes
	) {
		return padFrame(font, visible, visibleSourceGraphemes, false);
	}

	/** Builds the hidden-suffix projection used by bounded HUD layout. */
	public Component padHudFrame(
		final Font font,
		final Component visible,
		final int visibleSourceGraphemes
	) {
		return padFrame(font, visible, visibleSourceGraphemes, true);
	}

	private Component padFrame(
		final Font font,
		final Component visible,
		final int visibleSourceGraphemes,
		final boolean includeReservationEnvelope
	) {
		Objects.requireNonNull(font, "font");
		Objects.requireNonNull(visible, "visible");
		if (visibleSourceGraphemes < 0) {
			throw new IllegalArgumentException("visibleSourceGraphemes cannot be negative");
		}
		List<LayoutToken> layout = layoutTokens(font);
		int sourceCount = layout.stream().mapToInt(token -> token.source() ? 1 : 0).sum();
		int represented = Math.min(visibleSourceGraphemes, sourceCount);
		List<StyledGrapheme> visibleTokens = StyledTypewriter.flatten(visible).graphemes();
		MutableComponent projected = Component.empty();
		int visibleCursor = 0;
		boolean extrasInserted = false;
		for (LayoutToken token : layout) {
			if (!extrasInserted && token.sourceOffset() >= represented) {
				visibleCursor = appendVisibleExtras(
					projected,
					visibleTokens,
					visibleCursor,
					represented
				);
				extrasInserted = true;
			}
			if (token.source() && token.sourceOffset() < represented) {
				if (visibleCursor < visibleTokens.size()) {
					append(projected, visibleTokens.get(visibleCursor++));
				} else {
					append(projected, token.grapheme());
				}
			} else if (token.source() || includeReservationEnvelope) {
				// Reserve only the exact, already resolved suffix for Chat. A field's
				// max-width/max-lines envelope is useful for HUD bounds, but feeding those
				// synthetic glyphs into vanilla's splitter creates false line breaks and
				// blank tail lines that disappear when the history entry is finalized.
				projected.append(
					ChatLineProjection.hidden(component(token.grapheme()))
				);
			}
		}
		if (!extrasInserted) {
			appendVisibleExtras(projected, visibleTokens, visibleCursor, represented);
		}
		return projected;
	}

	private List<LayoutToken> layoutTokens(final Font font) {
		List<LayoutToken> result = new ArrayList<>();
		int sourceOffset = 0;
		for (Part part : this.parts) {
			Component source = part instanceof TextPart text
				? text.component()
				: ((FieldPart)part).component();
			List<StyledGrapheme> sourceTokens = StyledTypewriter.flatten(source).graphemes();
			for (StyledGrapheme grapheme : sourceTokens) {
				result.add(new LayoutToken(grapheme, sourceOffset++, true));
			}
			if (part instanceof FieldPart field) {
				List<StyledGrapheme> envelope = StyledTypewriter
					.flatten(fieldEnvelope(font, field))
					.graphemes();
				for (int index = sourceTokens.size(); index < envelope.size(); index++) {
					result.add(new LayoutToken(envelope.get(index), sourceOffset, false));
				}
			}
		}
		return result;
	}

	private static int appendVisibleExtras(
		final MutableComponent target,
		final List<StyledGrapheme> visible,
		final int consumed,
		final int represented
	) {
		int cursor = Math.max(consumed, Math.min(represented, visible.size()));
		while (cursor < visible.size()) {
			append(target, visible.get(cursor++));
		}
		return cursor;
	}

	private static void append(
		final MutableComponent target,
		final StyledGrapheme grapheme
	) {
		target.append(component(grapheme));
	}

	private static Component component(final StyledGrapheme grapheme) {
		return grapheme.component();
	}

	private static Component fieldEnvelope(
		final Font font,
		final FieldPart field
	) {
		MutableComponent result = Component.empty();
		appendFieldEnvelope(result, font, field);
		return result;
	}

	private static void appendFieldEnvelope(
		final MutableComponent target,
		final Font font,
		final FieldPart field
	) {
		Component value = field.component();
		target.append(value.copy());
		List<net.minecraft.network.chat.FormattedText> explicitLines =
			font.splitIgnoringLanguage(value, MAX_MEASURE_WIDTH);
		List<net.minecraft.network.chat.FormattedText> fallbackLines =
			font.splitIgnoringLanguage(field.fallback(), MAX_MEASURE_WIDTH);
		int valueWidth = explicitLines.stream().mapToInt(font::width).max().orElse(0);
		int fallbackWidth = fallbackLines.stream().mapToInt(font::width).max().orElse(0);
		boolean useExactFallback = valueWidth == 0 && fallbackWidth > 0;
		if (useExactFallback) {
			target.append(field.fallback().copy());
		}

		FieldReservation reservation = field.reservation();
		int glyphWidth = Math.max(1, font.width(MEASURE_GLYPH));
		int targetWidth = Math.max(
			reservation.estimatedWidth().orElse(0),
			fallbackWidth
		);
		if (reservation.maxGraphemes().isPresent()) {
			targetWidth = Math.max(
				targetWidth,
				Math.multiplyExact(reservation.maxGraphemes().getAsInt(), glyphWidth)
			);
		}
		int occupiedWidth = Math.max(valueWidth, useExactFallback ? fallbackWidth : 0);
		int remainingWidth = Math.max(0, targetWidth - occupiedWidth);
		int paddingGlyphs = Math.ceilDiv(remainingWidth, glyphWidth);
		if (paddingGlyphs > 0) {
			target.append(Component.literal(MEASURE_GLYPH.repeat(paddingGlyphs)));
		}

		int valueLines = Math.max(
			Math.max(1, explicitLines.size()),
			useExactFallback ? Math.max(1, fallbackLines.size()) : 1
		);
		int reservedLines = Math.max(
			reservation.maxLines().orElse(valueLines),
			Math.max(1, fallbackLines.size())
		);
		for (int line = valueLines; line < reservedLines; line++) {
			target.append(Component.literal("\n" + MEASURE_GLYPH));
		}
	}

	private record LayoutToken(
		StyledGrapheme grapheme,
		int sourceOffset,
		boolean source
	) {
		private LayoutToken {
			grapheme = Objects.requireNonNull(grapheme, "grapheme");
			if (sourceOffset < 0) {
				throw new IllegalArgumentException("sourceOffset cannot be negative");
			}
		}
	}

	private sealed interface Part permits TextPart, FieldPart {
	}

	private record TextPart(Component component) implements Part {
		private TextPart {
			component = Objects.requireNonNull(component, "component").copy();
		}
	}

	private record FieldPart(
		Component component,
		Component fallback,
		FieldReservation reservation
	) implements Part {
		private FieldPart {
			component = Objects.requireNonNull(component, "component").copy();
			fallback = Objects.requireNonNull(fallback, "fallback").copy();
			reservation = Objects.requireNonNull(reservation, "reservation");
		}
	}

	public static final class Builder {
		private final List<Part> parts = new ArrayList<>();

		private Builder() {
		}

		public Builder append(final Component component) {
			this.parts.add(new TextPart(component));
			return this;
		}

		public Builder field(
			final Component component,
			final Component fallback,
			final FieldReservation reservation
		) {
			this.parts.add(new FieldPart(component, fallback, reservation));
			return this;
		}

		public MessagePrelayoutPlan build() {
			return new MessagePrelayoutPlan(this.parts);
		}
	}
}
