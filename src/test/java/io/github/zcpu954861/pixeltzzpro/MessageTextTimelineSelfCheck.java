package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CursorPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CursorSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EffectUse;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TypewriterPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TypewriterSpec;
import io.github.zcpu954861.pixeltzzpro.message.GraphemeClusters;
import io.github.zcpu954861.pixeltzzpro.message.MessageEffectResolver;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter.FrameSpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.Bootstrap;

/**
 * Runnable checks for V3B Unicode segmentation and rich-style typewriter projection.
 */
public final class MessageTextTimelineSelfCheck {
	private MessageTextTimelineSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkGraphemeBoundaries();
		checkCrossStyleGraphemes();
		checkRichStyleProjection();
		checkTemporaryColorRestore();
		checkEffectResolution();

		System.out.println("MESSAGE_TEXT_TIMELINE_SELF_CHECK=PASS");
	}

	private static void checkGraphemeBoundaries() {
		check(GraphemeClusters.count("全员逃走") == 4, "Chinese text must split per character");
		check(GraphemeClusters.count("e\u0301") == 1, "combining marks must stay attached");
		check(GraphemeClusters.count("👨‍👩‍👧‍👦") == 1, "ZWJ family emoji must remain intact");
		check(GraphemeClusters.count("👍🏽") == 1, "emoji skin tone must remain attached");
		check(GraphemeClusters.count("🇨🇳🇯🇵") == 2, "regional indicators must form flag pairs");
		check(
			GraphemeClusters.split("A\r\nB").equals(List.of("A", "\r\n", "B")),
			"CRLF must remain one control cluster"
		);
		List<String> acceptance = GraphemeClusters.split("🙂‍↕️｜组合字素 e\u0301｜");
		check(
			acceptance.contains("🙂‍↕️") && acceptance.contains("e\u0301"),
			"the acceptance emoji and decomposed accent must each remain one cluster"
		);
	}

	private static void checkCrossStyleGraphemes() {
		Component splitAcrossComponents = Component.empty()
			.append(Component.literal("e").withColor(0xFF0000))
			.append(Component.literal("\u0301").withColor(0x00FF00))
			.append(Component.literal("🙂").withColor(0x112233))
			.append(Component.literal("‍↕").withColor(0x445566))
			.append(Component.literal("️").withColor(0x778899));
		var flattened = StyledTypewriter.flatten(splitAcrossComponents);
		check(
			flattened.graphemes().size() == 2
				&& flattened.graphemes().get(0).text().equals("e\u0301")
				&& flattened.graphemes().get(1).text().equals("🙂‍↕️"),
			"component/style boundaries must not become typewriter grapheme boundaries"
		);
		check(
			flattened.graphemes().get(0).spans().size() == 2
				&& flattened.graphemes().get(1).spans().size() == 3,
			"one atomic grapheme must retain every registered style span"
		);
		Component first = StyledTypewriter.frame(
			flattened,
			StyledTypewriter.FrameSpec.plain(1)
		);
		check(
			first.getString().equals("e\u0301"),
			"a frame must reveal a cross-style combining cluster atomically"
		);
		check(
			first.toFlatList().stream()
				.anyMatch(part -> part.getStyle().getColor() != null
					&& part.getStyle().getColor().getValue() == 0xFF0000)
				&& first.toFlatList().stream()
					.anyMatch(part -> part.getStyle().getColor() != null
						&& part.getStyle().getColor().getValue() == 0x00FF00),
			"atomic reveal must not flatten the grapheme's component styles"
		);
	}

	private static void checkRichStyleProjection() {
		MutableComponent finalText = Component.empty()
			.append(
				Component.literal("A")
					.withStyle(style -> style.withColor(0xFF5544).withBold(true).withInsertion("A"))
			)
			.append(
				Component.literal("中")
					.withStyle(style -> style.withColor(0x44CCFF).withUnderlined(true))
			)
			.append(Component.literal("👨‍👩‍👧‍👦").withColor(0x65D68A));
		var flattened = StyledTypewriter.flatten(finalText);
		check(flattened.graphemes().size() == 3, "styled stream must count grapheme clusters");

		Component frame = StyledTypewriter.frame(
			flattened,
			new FrameSpec(
				2,
				true,
				"▌",
				OptionalInt.of(0x77E6FF),
				OptionalInt.empty(),
				0,
				RestoreMode.INSTANT,
				0.0D
			)
		);
		check(frame.getString().equals("A中▌"), "frame must append one separate cursor");
		List<Component> flat = frame.toFlatList();
		check(flat.get(0).getStyle().isBold(), "visible rich-text bold style must survive");
		check(
			"A".equals(flat.get(0).getStyle().getInsertion()),
			"visible insertion metadata must survive"
		);
		check(
			flat.get(1).getStyle().isUnderlined(),
			"visible rich-text underline style must survive"
		);
		check(
			flat.getLast().getStyle().getInsertion() == null,
			"cursor must not inherit interactive metadata"
		);
	}

	private static void checkTemporaryColorRestore() {
		MutableComponent finalText = Component.empty()
			.append(Component.literal("A").withColor(0xFF0000))
			.append(Component.literal("B").withColor(0x00FF00))
			.append(Component.literal("C").withColor(0x0000FF));
		var flattened = StyledTypewriter.flatten(finalText);

		Component instant = StyledTypewriter.frame(
			flattened,
			new FrameSpec(
				3,
				false,
				"",
				OptionalInt.empty(),
				OptionalInt.of(0xFFFFFF),
				2,
				RestoreMode.INSTANT,
				0.0D
			)
		);
		List<Component> instantParts = instant.toFlatList();
		check(
			instantParts.get(0).getStyle().getColor().getValue() == 0xFF0000,
			"character outside the temporary-color distance must restore its own color"
		);
		check(
			instantParts.get(1).getStyle().getColor().getValue() == 0xFFFFFF
				&& instantParts.get(2).getStyle().getColor().getValue() == 0xFFFFFF,
			"fresh characters must use the registered temporary color"
		);

		Component gradient = StyledTypewriter.frame(
			flattened,
			new FrameSpec(
				3,
				false,
				"",
				OptionalInt.empty(),
				OptionalInt.of(0xFFFFFF),
				2,
				RestoreMode.GRADIENT,
				0.0D
			)
		);
		int middle = gradient.toFlatList().get(1).getStyle().getColor().getValue();
		check(
			middle != 0xFFFFFF && middle != 0x00FF00,
			"gradient restore must interpolate between fresh and original colors"
		);
	}

	private static void checkEffectResolution() {
		var effectId = PixelTzzPro.id("acceptance/typewriter");
		TextEffectDefinition preset = new TextEffectDefinition(
			effectId,
			Optional.of(
				new TypewriterSpec(
					new TimeSpan(80_000_000L),
					new CursorSpec(true, "▌", "#FFFFFF", false, Optional.empty()),
					Optional.of("#FFCC66"),
					3,
					RestoreMode.INSTANT
				)
			),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			64,
			"{}"
		);
		EffectUse use = new EffectUse(
			Optional.of(effectId),
			new TextEffectPatch(
				Optional.of(
					new TypewriterPatch(
						Optional.of(new TimeSpan(40_000_000L)),
						Optional.of(
							new CursorPatch(
								Optional.empty(),
								Optional.of("█"),
								Optional.empty(),
								Optional.empty(),
								Optional.empty()
							)
						),
						Optional.empty(),
						Optional.empty(),
						Optional.of(RestoreMode.GRADIENT)
					)
				),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			)
		);
		var resolved = MessageEffectResolver.resolve(Optional.of(use), Map.of(effectId, preset));
		check(resolved.successful(), "preset plus local override must resolve");
		var typewriter = resolved.effect().orElseThrow().typewriter().orElseThrow();
		check(
			typewriter.characterInterval().nanoseconds() == 40_000_000L
				&& typewriter.cursor().glyph().equals("█")
				&& typewriter.cursor().enabled()
				&& typewriter.freshColor().orElseThrow().equals("#FFCC66")
				&& typewriter.restoreMode() == RestoreMode.GRADIENT,
			"effect override must change only explicitly registered values"
		);
		check(
			!MessageEffectResolver.resolve(
				Optional.of(
					new EffectUse(
						Optional.of(PixelTzzPro.id("missing")),
						TextEffectPatch.empty()
					)
				),
				Map.of()
			).successful(),
			"missing frozen preset must fail closed"
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
