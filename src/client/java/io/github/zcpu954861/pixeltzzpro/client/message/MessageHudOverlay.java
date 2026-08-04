package io.github.zcpu954861.pixeltzzpro.client.message;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.message.StyledMessageEffects.VisualFrame;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Alignment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Overflow;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Draws V3B-owned Title, Subtitle, and ActionBar frames without taking ownership of the vanilla
 * HUD fields.
 *
 * <p>The overlay is deliberately a narrow rendering adapter. Playback remains responsible for
 * timing, conflict policy, and deciding when a frame is active. This class only snapshots a frame,
 * lays it out with the real client font, and draws it after the vanilla title layer. Consequently,
 * clearing a V3B frame never clears a title or action bar owned by vanilla, a data pack command, or
 * another mod.</p>
 */
public final class MessageHudOverlay {
	private static final int SAFE_MARGIN = 8;
	private static final int BACKDROP_PADDING = 2;
	private static final int MAX_MEASURE_WIDTH = 1_000_000;
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();
	private static final AtomicReferenceArray<OverlaySnapshot> FRAMES =
		new AtomicReferenceArray<>(Channel.values().length);

	private MessageHudOverlay() {
	}

	/**
	 * Registers the overlay once, immediately after vanilla Title and Subtitle extraction.
	 *
	 * <p>The inherited vanilla HUD render condition means F1 still hides this layer exactly as it
	 * hides the corresponding vanilla content.</p>
	 */
	public static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		try {
			HudElementRegistry.attachElementAfter(
				VanillaHudElements.TITLE_AND_SUBTITLE,
				PixelTzzPro.id("message_hud_overlay"),
				MessageHudOverlay::render
			);
		} catch (RuntimeException exception) {
			REGISTERED.set(false);
			throw exception;
		}
	}

	/**
	 * Replaces the current V3B frame for one exclusive HUD channel.
	 *
	 * <p>For {@link Overflow#STATIC_FINAL}, this overload can only use the submitted frame as its
	 * fallback. Call the four-argument overload when the playback adapter has the resolved final
	 * component available.</p>
	 */
	public static void update(
		final Channel channel,
		final VisualFrame frame,
		final ResolvedLayout layout
	) {
		VisualFrame value = Objects.requireNonNull(frame, "frame");
		update(
			channel,
			value.component(),
			MessagePrelayoutPlan.fixed(value.component()),
			value,
			layout
		);
	}

	/**
	 * Replaces the current V3B frame and supplies the stable final component used by
	 * {@link Overflow#STATIC_FINAL}.
	 */
	public static void update(
		final Channel channel,
		final Component staticFinalComponent,
		final VisualFrame frame,
		final ResolvedLayout layout
	) {
		update(
			channel,
			staticFinalComponent,
			MessagePrelayoutPlan.fixed(staticFinalComponent),
			frame,
			layout
		);
	}

	/**
	 * Replaces the current frame while retaining a separate non-rendered pre-layout projection.
	 */
	public static void update(
		final Channel channel,
		final Component staticFinalComponent,
		final MessagePrelayoutPlan prelayout,
		final VisualFrame frame,
		final ResolvedLayout layout
	) {
		Channel supported = requireHudChannel(channel);
		Objects.requireNonNull(staticFinalComponent, "staticFinalComponent");
		Objects.requireNonNull(prelayout, "prelayout");
		Objects.requireNonNull(frame, "frame");
		Objects.requireNonNull(layout, "layout");
		VisualFrame copiedFrame = new VisualFrame(
			frame.component().copy(),
			frame.opacity(),
			frame.translateX(),
			frame.translateY(),
			frame.scale()
		);
		FRAMES.set(
			supported.ordinal(),
			new OverlaySnapshot(
				copiedFrame,
				staticFinalComponent.copy(),
				prelayout,
				layout
			)
		);
	}

	/**
	 * Stops drawing the V3B frame on one channel. Vanilla HUD state is left untouched.
	 */
	public static void clear(final Channel channel) {
		FRAMES.set(requireHudChannel(channel).ordinal(), null);
	}

	/**
	 * Clears every V3B-owned HUD frame without unregistering the stable HUD layer.
	 */
	public static void reset() {
		FRAMES.set(Channel.TITLE.ordinal(), null);
		FRAMES.set(Channel.SUBTITLE.ordinal(), null);
		FRAMES.set(Channel.ACTION_BAR.ordinal(), null);
	}

	private static Channel requireHudChannel(final Channel channel) {
		Channel value = Objects.requireNonNull(channel, "channel");
		if (value == Channel.CHAT) {
			throw new IllegalArgumentException("Chat uses the logical vanilla chat history");
		}
		return value;
	}

	private static void render(
		final GuiGraphicsExtractor graphics,
		final DeltaTracker tickCounter
	) {
		Objects.requireNonNull(graphics, "graphics");
		// HUD animation time is already resolved into VisualFrame by playback.
		Objects.requireNonNull(tickCounter, "tickCounter");
		Font font = Minecraft.getInstance().font;
		boolean openedStratum = false;
		for (
			Channel channel
			: List.of(Channel.ACTION_BAR, Channel.TITLE, Channel.SUBTITLE)
		) {
			OverlaySnapshot snapshot = FRAMES.get(channel.ordinal());
			if (snapshot == null || snapshot.frame().opacity() <= 0.0F) {
				continue;
			}
			if (!openedStratum) {
				graphics.nextStratum();
				openedStratum = true;
			}
			renderChannel(graphics, font, channel, snapshot);
		}
	}

	private static void renderChannel(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Channel channel,
		final OverlaySnapshot snapshot
	) {
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		if (screenWidth <= 0 || screenHeight <= 0) {
			return;
		}

		int marginX = Math.min(SAFE_MARGIN, Math.max(0, (screenWidth - 1) / 2));
		int marginY = Math.min(SAFE_MARGIN, Math.max(0, (screenHeight - 1) / 2));
		int safeLeft = marginX;
		int safeTop = marginY;
		int safeRight = Math.max(safeLeft + 1, screenWidth - marginX);
		int safeBottom = Math.max(safeTop + 1, screenHeight - marginY);
		int availableWidth = Math.max(1, safeRight - safeLeft);
		int availableHeight = Math.max(1, safeBottom - safeTop);

		ChannelGeometry geometry = ChannelGeometry.forChannel(channel, screenHeight);
		ResolvedLayout layout = snapshot.layout();
		float baseScale = geometry.baseScale();
		int basePaddingWidth = Math.round(BACKDROP_PADDING * 2.0F * baseScale);
		int requestedContentWidth = layout.maxWidth().orElse(availableWidth);
		int targetContentWidth = Math.max(
			1,
			Math.min(requestedContentWidth, Math.max(1, availableWidth - basePaddingWidth))
		);
		int localWidth = Math.max(1, (int)Math.floor(targetContentWidth / baseScale));

		PreparedBlock block = prepareBlock(
			font,
			snapshot,
			localWidth,
			layout.maxLines().orElse(Integer.MAX_VALUE)
		);
		if (block.lines().isEmpty()) {
			return;
		}

		float requestedScale = baseScale * snapshot.frame().scale();
		float paddedLocalWidth = block.alignmentWidth() + BACKDROP_PADDING * 2.0F;
		float paddedLocalHeight = block.contentHeight() + BACKDROP_PADDING * 2.0F;
		float widthLimit = layout.maxWidth().isPresent()
			? Math.min(
				availableWidth,
				targetContentWidth + BACKDROP_PADDING * 2.0F * baseScale
			)
			: availableWidth;
		float heightLimit = availableHeight;
		if (layout.maxLines().isPresent()) {
			int registeredLines = layout.maxLines().getAsInt();
			float registeredHeight = lineBlockHeight(
				font,
				registeredLines,
				layout.lineSpacing()
			);
			heightLimit = Math.min(
				heightLimit,
				(registeredHeight + BACKDROP_PADDING * 2.0F) * baseScale
			);
		}

		float fitScale = 1.0F;
		if (paddedLocalWidth * requestedScale > widthLimit) {
			fitScale = Math.min(
				fitScale,
				widthLimit / (paddedLocalWidth * requestedScale)
			);
		}
		if (paddedLocalHeight * requestedScale > heightLimit) {
			fitScale = Math.min(
				fitScale,
				heightLimit / (paddedLocalHeight * requestedScale)
			);
		}
		float renderScale = Math.max(0.01F, requestedScale * fitScale);
		float renderedWidth = paddedLocalWidth * renderScale;
		float renderedHeight = paddedLocalHeight * renderScale;

		float centerX = screenWidth / 2.0F
			+ layout.offsetX()
			+ snapshot.frame().translateX();
		float contentTop = geometry.anchorY()
			+ geometry.localY() * renderScale
			+ layout.offsetY()
			+ snapshot.frame().translateY();
		float blockLeft = clamp(
			centerX - renderedWidth / 2.0F,
			safeLeft,
			safeRight - renderedWidth
		);
		float blockTop = clamp(
			contentTop - BACKDROP_PADDING * renderScale,
			safeTop,
			safeBottom - renderedHeight
		);
		int alpha = Math.max(
			0,
			Math.min(255, Math.round(snapshot.frame().opacity() * 255.0F))
		);
		if (alpha == 0) {
			return;
		}
		int color = alpha << 24 | 0x00FFFFFF;

		graphics.enableScissor(safeLeft, safeTop, safeRight, safeBottom);
		graphics.pose().pushMatrix();
		graphics.pose().translate(blockLeft, blockTop);
		graphics.pose().scale(renderScale, renderScale);
		try {
			for (PreparedLine line : block.lines()) {
				int lineX = BACKDROP_PADDING + alignedX(
					layout.alignment(),
					block.alignmentWidth(),
					line.width()
				);
				int lineY = BACKDROP_PADDING + line.y();
				graphics.textWithBackdrop(
					font,
					line.component(),
					lineX,
					lineY,
					line.width(),
					color
				);
			}
		} finally {
			graphics.pose().popMatrix();
			graphics.disableScissor();
		}
	}

	private static PreparedBlock prepareBlock(
		final Font font,
		final OverlaySnapshot snapshot,
		final int localWidth,
		final int maxLines
	) {
		Overflow overflow = snapshot.layout().overflow();
		Component current = snapshot.frame().component();
		if (current.getString().isEmpty()) {
			return PreparedBlock.empty();
		}
		Component projected = snapshot.prelayout().padHudFrame(
			font,
			current,
			StyledTypewriter.flatten(current).graphemes().size()
		);
		PreparedBlock visible = switch (overflow) {
			case WRAP -> prepareProjectedWrapped(
				font,
				projected,
				localWidth,
				maxLines,
				snapshot.layout().maxWidth().isPresent(),
				snapshot.layout().lineSpacing()
			);
			case ELLIPSIS -> prepareProjectedEllipsized(
				font,
				projected,
				snapshot.staticFinalComponent(),
				localWidth,
				snapshot.layout().maxLines().orElse(1),
				snapshot.layout().maxWidth().isPresent(),
				snapshot.layout().lineSpacing()
			);
			case SCALE -> prepareScaled(
				font,
				current,
				localWidth,
				snapshot.layout().maxWidth().isPresent(),
				snapshot.layout().lineSpacing()
			);
			case STATIC_FINAL -> {
				boolean overflowed = exceedsRegisteredBounds(
					font,
					current,
					localWidth,
					maxLines
				);
				Component source = overflowed ? snapshot.staticFinalComponent() : current;
				yield prepareWrapped(
					font,
					source,
					localWidth,
					Integer.MAX_VALUE,
					snapshot.layout().maxWidth().isPresent(),
					snapshot.layout().lineSpacing(),
					true
				);
			}
		};
		Component prelayout = snapshot.prelayout().materialize(font);
		if (prelayout.getString().isEmpty()) {
			return visible;
		}
		PreparedBlock reserved = switch (overflow) {
			case WRAP, STATIC_FINAL -> prepareWrapped(
				font,
				prelayout,
				localWidth,
				maxLines,
				snapshot.layout().maxWidth().isPresent(),
				snapshot.layout().lineSpacing(),
				false
			);
			case ELLIPSIS -> prepareEllipsized(
				font,
				prelayout,
				localWidth,
				snapshot.layout().maxLines().orElse(1),
				snapshot.layout().maxWidth().isPresent(),
				snapshot.layout().lineSpacing()
			);
			case SCALE -> prepareScaled(
				font,
				prelayout,
				localWidth,
				snapshot.layout().maxWidth().isPresent(),
				snapshot.layout().lineSpacing()
			);
		};
		return new PreparedBlock(
			visible.lines(),
			Math.max(visible.alignmentWidth(), reserved.alignmentWidth()),
			Math.max(visible.contentHeight(), reserved.contentHeight())
		);
	}

	private static PreparedBlock prepareProjectedWrapped(
		final Font font,
		final Component projected,
		final int localWidth,
		final int maxLines,
		final boolean fixedAlignmentWidth,
		final float lineSpacing
	) {
		List<FormattedText> logicalLines = font.splitIgnoringLanguage(
			projected,
			localWidth
		);
		if (logicalLines.size() > maxLines) {
			logicalLines = logicalLines.subList(0, maxLines);
		}
		return prepareProjectedLines(
			font,
			logicalLines,
			fixedAlignmentWidth ? localWidth : 0,
			localWidth,
			lineSpacing,
			false
		);
	}

	private static PreparedBlock prepareProjectedEllipsized(
		final Font font,
		final Component projected,
		final Component staticFinalComponent,
		final int localWidth,
		final int maxLines,
		final boolean fixedAlignmentWidth,
		final float lineSpacing
	) {
		List<FormattedText> wrapped = font.splitIgnoringLanguage(projected, localWidth);
		if (wrapped.isEmpty()) {
			return PreparedBlock.empty();
		}
		int lineLimit = Math.max(1, maxLines);
		boolean truncated = font.splitIgnoringLanguage(
			staticFinalComponent,
			localWidth
		).size() > lineLimit;
		List<FormattedText> selected = wrapped.subList(
			0,
			Math.min(wrapped.size(), lineLimit)
		);
		return prepareProjectedLines(
			font,
			selected,
			fixedAlignmentWidth ? localWidth : 0,
			localWidth,
			lineSpacing,
			truncated
		);
	}

	private static PreparedBlock prepareProjectedLines(
		final Font font,
		final List<FormattedText> logicalLines,
		final int minimumAlignmentWidth,
		final int availableLineWidth,
		final float lineSpacing,
		final boolean ellipsizeLastLine
	) {
		if (logicalLines.isEmpty()) {
			return PreparedBlock.empty();
		}
		List<PreparedLine> lines = new ArrayList<>(logicalLines.size());
		int widest = Math.max(0, minimumAlignmentWidth);
		float step = font.lineHeight * lineSpacing;
		int lastY = 0;
		for (int index = 0; index < logicalLines.size(); index++) {
			Component visible = withoutPadding(logicalLines.get(index));
			if (
				ellipsizeLastLine
					&& index == logicalLines.size() - 1
					&& !visible.getString().isEmpty()
			) {
				FormattedText ellipsis = FormattedText.of("…", visible.getStyle());
				int finalWidth = Math.max(0, availableLineWidth - font.width(ellipsis));
				FormattedText head = font.substrByWidth(visible, finalWidth);
				visible = toComponent(FormattedText.composite(head, ellipsis));
			}
			int y = Math.round(index * step);
			lastY = y;
			if (visible.getString().isEmpty()) {
				continue;
			}
			int width = font.width(visible);
			lines.add(new PreparedLine(visible, width, y));
			widest = Math.max(widest, width);
		}
		return new PreparedBlock(
			List.copyOf(lines),
			Math.max(1, widest),
			lastY + font.lineHeight
		);
	}

	private static Component withoutPadding(final FormattedText text) {
		MutableComponent component = Component.empty();
		text.visit(
			(style, value) -> {
				if (!ChatLineProjection.isPaddingStyle(style)) {
					component.append(Component.literal(value).withStyle(style));
				}
				return Optional.empty();
			},
			Style.EMPTY
		);
		return component;
	}

	private static PreparedBlock prepareWrapped(
		final Font font,
		final Component source,
		final int localWidth,
		final int maxLines,
		final boolean fixedAlignmentWidth,
		final float lineSpacing,
		final boolean retainAllLines
	) {
		List<FormattedText> logicalLines = font.splitIgnoringLanguage(source, localWidth);
		if (!retainAllLines && logicalLines.size() > maxLines) {
			logicalLines = logicalLines.subList(0, maxLines);
		}
		return prepareLines(
			font,
			logicalLines,
			fixedAlignmentWidth ? localWidth : 0,
			lineSpacing
		);
	}

	private static PreparedBlock prepareEllipsized(
		final Font font,
		final Component source,
		final int localWidth,
		final int maxLines,
		final boolean fixedAlignmentWidth,
		final float lineSpacing
	) {
		List<FormattedText> wrapped = font.splitIgnoringLanguage(source, localWidth);
		if (wrapped.isEmpty()) {
			return PreparedBlock.empty();
		}
		int lineLimit = Math.max(1, maxLines);
		boolean truncated = wrapped.size() > lineLimit;
		List<FormattedText> visible = new ArrayList<>(
			wrapped.subList(0, Math.min(wrapped.size(), lineLimit))
		);
		if (truncated) {
			FormattedText ellipsis = FormattedText.of("…", source.getStyle());
			int ellipsisWidth = font.width(ellipsis);
			int finalWidth = Math.max(0, localWidth - ellipsisWidth);
			FormattedText head = font.substrByWidth(
				visible.get(visible.size() - 1),
				finalWidth
			);
			visible.set(
				visible.size() - 1,
				FormattedText.composite(head, ellipsis)
			);
		}
		return prepareLines(
			font,
			visible,
			fixedAlignmentWidth ? localWidth : 0,
			lineSpacing
		);
	}

	private static PreparedBlock prepareScaled(
		final Font font,
		final Component source,
		final int localWidth,
		final boolean fixedAlignmentWidth,
		final float lineSpacing
	) {
		List<FormattedText> explicitLines = font.splitIgnoringLanguage(
			source,
			MAX_MEASURE_WIDTH
		);
		PreparedBlock measured = prepareLines(font, explicitLines, 0, lineSpacing);
		int alignmentWidth = fixedAlignmentWidth
			? Math.max(localWidth, measured.alignmentWidth())
			: measured.alignmentWidth();
		return new PreparedBlock(
			measured.lines(),
			alignmentWidth,
			measured.contentHeight()
		);
	}

	private static boolean exceedsRegisteredBounds(
		final Font font,
		final Component source,
		final int localWidth,
		final int maxLines
	) {
		List<FormattedText> explicit = font.splitIgnoringLanguage(
			source,
			MAX_MEASURE_WIDTH
		);
		boolean wider = explicit.stream().anyMatch(line -> font.width(line) > localWidth);
		if (wider) {
			return true;
		}
		if (maxLines == Integer.MAX_VALUE) {
			return false;
		}
		return font.splitIgnoringLanguage(source, localWidth).size() > maxLines;
	}

	private static PreparedBlock prepareLines(
		final Font font,
		final List<FormattedText> logicalLines,
		final int minimumAlignmentWidth,
		final float lineSpacing
	) {
		if (logicalLines.isEmpty()) {
			return PreparedBlock.empty();
		}
		List<PreparedLine> lines = new ArrayList<>(logicalLines.size());
		int widest = Math.max(0, minimumAlignmentWidth);
		float step = font.lineHeight * lineSpacing;
		int lastY = 0;
		for (int index = 0; index < logicalLines.size(); index++) {
			FormattedText logical = logicalLines.get(index);
			int width = font.width(logical);
			int y = Math.round(index * step);
			lines.add(
				new PreparedLine(
					toComponent(logical),
					width,
					y
				)
			);
			widest = Math.max(widest, width);
			lastY = y;
		}
		return new PreparedBlock(
			List.copyOf(lines),
			Math.max(1, widest),
			lastY + font.lineHeight
		);
	}

	private static Component toComponent(final FormattedText text) {
		MutableComponent component = Component.empty();
		text.visit(
			(style, value) -> {
				component.append(Component.literal(value).withStyle(style));
				return Optional.empty();
			},
			Style.EMPTY
		);
		return component;
	}

	private static int alignedX(
		final Alignment alignment,
		final int blockWidth,
		final int lineWidth
	) {
		return switch (alignment) {
			case LEFT -> 0;
			case CENTER -> Math.max(0, (blockWidth - lineWidth) / 2);
			case RIGHT -> Math.max(0, blockWidth - lineWidth);
		};
	}

	private static float lineBlockHeight(
		final Font font,
		final int lineCount,
		final float lineSpacing
	) {
		if (lineCount <= 1) {
			return font.lineHeight;
		}
		return Math.round((lineCount - 1) * font.lineHeight * lineSpacing)
			+ font.lineHeight;
	}

	private static float clamp(
		final float value,
		final float minimum,
		final float maximum
	) {
		if (maximum <= minimum) {
			return minimum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	private record OverlaySnapshot(
		VisualFrame frame,
		Component staticFinalComponent,
		MessagePrelayoutPlan prelayout,
		ResolvedLayout layout
	) {
		private OverlaySnapshot {
			frame = Objects.requireNonNull(frame, "frame");
			staticFinalComponent = Objects.requireNonNull(
				staticFinalComponent,
				"staticFinalComponent"
			);
			prelayout = Objects.requireNonNull(prelayout, "prelayout");
			layout = Objects.requireNonNull(layout, "layout");
		}
	}

	private record PreparedLine(Component component, int width, int y) {
		private PreparedLine {
			component = Objects.requireNonNull(component, "component");
			if (width < 0 || y < 0) {
				throw new IllegalArgumentException("prepared HUD line metrics are invalid");
			}
		}
	}

	private record PreparedBlock(
		List<PreparedLine> lines,
		int alignmentWidth,
		int contentHeight
	) {
		private PreparedBlock {
			lines = List.copyOf(lines);
			if (alignmentWidth < 0 || contentHeight < 0) {
				throw new IllegalArgumentException("prepared HUD block metrics are invalid");
			}
		}

		private static PreparedBlock empty() {
			return new PreparedBlock(List.of(), 0, 0);
		}
	}

	private record ChannelGeometry(float baseScale, float anchorY, float localY) {
		private static ChannelGeometry forChannel(
			final Channel channel,
			final int screenHeight
		) {
			return switch (channel) {
				case TITLE -> new ChannelGeometry(4.0F, screenHeight / 2.0F, -10.0F);
				case SUBTITLE -> new ChannelGeometry(2.0F, screenHeight / 2.0F, 5.0F);
				case ACTION_BAR -> new ChannelGeometry(1.0F, screenHeight - 68.0F, -4.0F);
				case CHAT -> throw new IllegalArgumentException(
					"Chat uses the logical vanilla chat history"
				);
			};
		}
	}
}
