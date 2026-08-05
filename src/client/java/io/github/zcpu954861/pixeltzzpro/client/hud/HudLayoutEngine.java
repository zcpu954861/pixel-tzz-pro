package io.github.zcpu954861.pixeltzzpro.client.hud;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Alignment;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BackgroundNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BadgeNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ContainerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.CounterNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ImageNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeStyle;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Orientation;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.PlayerHeadNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ProgressNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.SeparatorNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TextNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TimerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Recursive, side-effect-free measurement for one already-authorized HUD tree.
 *
 * <p>The renderer consumes only the resulting immutable plan. This keeps measurement, clipping and
 * hit-free settings previews on the same geometry, and makes an invalid or over-sized tree fail as
 * one unit instead of drawing half a component.</p>
 */
final class HudLayoutEngine {
	private static final int MAX_NODE_DIMENSION = 640;
	private static final int DEFAULT_TEXT_WIDTH = 220;
	private static final int DEFAULT_PROGRESS_WIDTH = 148;
	private static final int DEFAULT_TIMER_WIDTH = 72;

	private HudLayoutEngine() {
	}

	static Optional<Plan> measure(
		final ClientHudSnapshot snapshot,
		final Font font,
		final int maximumWidth,
		final int maximumHeight,
		final Set<String> hiddenComponents,
		final Density density
	) {
		return measure(
			snapshot.rootNodeId(),
			snapshot.nodes(),
			font,
			maximumWidth,
			maximumHeight,
			hiddenComponents,
			density
		);
	}

	static Optional<Plan> measure(
		final String rootNodeId,
		final Map<String, ClientHudNode> nodes,
		final Font font,
		final int maximumWidth,
		final int maximumHeight,
		final Set<String> hiddenComponents,
		final Density density
	) {
		if (maximumWidth <= 0 || maximumHeight <= 0) {
			return Optional.empty();
		}
		MeasureContext context = new MeasureContext(
			nodes,
			font,
			Set.copyOf(hiddenComponents),
			density
		);
		MeasureResult measured = context.measure(rootNodeId, Math.min(MAX_NODE_DIMENSION, maximumWidth));
		Entry root = measured.entry();
		if (root == null || root.width() <= 0 || root.height() <= 0) {
			return Optional.empty();
		}
		if (root.width() > maximumWidth || root.height() > maximumHeight) {
			return Optional.empty();
		}
		return Optional.of(new Plan(root, root.width(), root.height(), density));
	}

	enum Density {
		NORMAL,
		COMPACT,
		SUMMARY
	}

	record Plan(Entry root, int width, int height, Density density) {
	}

	private enum MeasureStatus {
		ENTRY,
		HIDDEN,
		FAILURE
	}

	private record MeasureResult(MeasureStatus status, Entry entry) {
		private static MeasureResult entry(final Entry value) {
			return new MeasureResult(MeasureStatus.ENTRY, value);
		}

		private static MeasureResult hidden() {
			return new MeasureResult(MeasureStatus.HIDDEN, null);
		}

		private static MeasureResult failure() {
			return new MeasureResult(MeasureStatus.FAILURE, null);
		}

		private boolean failed() {
			return this.status == MeasureStatus.FAILURE;
		}
	}

	record Entry(
		ClientHudNode node,
		int x,
		int y,
		int width,
		int height,
		int contentX,
		int contentY,
		int contentWidth,
		int contentHeight,
		double contentScale,
		List<FormattedCharSequence> lines,
		List<Entry> children
	) {
		Entry {
			lines = List.copyOf(lines);
			children = List.copyOf(children);
		}

		Entry moved(final int dx, final int dy) {
			return new Entry(
				this.node,
				this.x + dx,
				this.y + dy,
				this.width,
				this.height,
				this.contentX + dx,
				this.contentY + dy,
				this.contentWidth,
				this.contentHeight,
				this.contentScale,
				this.lines,
				this.children.stream().map(child -> child.moved(dx, dy)).toList()
			);
		}
	}

	private record MeasureContext(
		Map<String, ClientHudNode> nodes,
		Font font,
		Set<String> hidden,
		Density density
	) {
		private MeasureResult measure(final String id, final int maximumWidth) {
			ClientHudNode node = this.nodes.get(id);
			if (node == null) {
				return MeasureResult.failure();
			}
			if (!visible(node)) {
				return MeasureResult.hidden();
			}
			NodeStyle style = node.meta().style();
			int paddingX = style.padding().left() + style.padding().right();
			int requestedWidth = style.width().orElse(Math.max(1, maximumWidth));
			int outerLimit = Math.max(1, Math.min(maximumWidth, requestedWidth));
			int innerLimit = Math.max(1, outerLimit - paddingX);

			MeasureResult rawResult;
			if (node instanceof TextNode value) {
				rawResult = raw(node, text(value, innerLimit));
			} else if (node instanceof ImageNode value) {
				rawResult = raw(node, leaf(value, value.width(), value.height()));
			} else if (node instanceof PlayerHeadNode value) {
				rawResult = raw(node, leaf(value, value.size(), value.size()));
			} else if (node instanceof CounterNode value) {
				rawResult = raw(node, counter(value, innerLimit));
			} else if (node instanceof BadgeNode value) {
				rawResult = raw(node, badge(value, innerLimit));
			} else if (node instanceof ProgressNode value) {
				rawResult = raw(node, progress(value, innerLimit));
			} else if (node instanceof TimerNode value) {
				rawResult = raw(node, timer(value, innerLimit));
			} else if (node instanceof SeparatorNode value) {
				rawResult = raw(node, separator(value, innerLimit));
			} else if (node instanceof BackgroundNode value) {
				rawResult = background(value, innerLimit);
			} else if (node instanceof ContainerNode value) {
				rawResult = container(value, innerLimit);
			} else {
				return MeasureResult.failure();
			}
			if (rawResult.status() != MeasureStatus.ENTRY) {
				return rawResult;
			}
			Entry raw = rawResult.entry();

			int width = Math.max(raw.width() + paddingX, style.width().orElse(0));
			int height = Math.max(
				raw.height() + style.padding().top() + style.padding().bottom(),
				style.height().orElse(0)
			);
			if (width > maximumWidth || width > MAX_NODE_DIMENSION || height > MAX_NODE_DIMENSION) {
				return unmeasurable(node);
			}
			int innerWidth = Math.max(0, width - paddingX);
			int dx = style.padding().left();
			int dy = style.padding().top();
			if (node instanceof ContainerNode container) {
				if (container.meta().type() == Type.COLUMN || container.meta().type() == Type.REPEAT) {
					dx += alignedOffset(container.alignment(), innerWidth, raw.width());
				}
			}
			Entry moved = raw.moved(dx, dy);
			return MeasureResult.entry(new Entry(
				node,
				0,
				0,
				width,
				height,
				moved.contentX(),
				moved.contentY(),
				moved.contentWidth(),
				moved.contentHeight(),
				moved.contentScale(),
				moved.lines(),
				moved.children()
			));
		}

		private MeasureResult raw(final ClientHudNode node, final Entry entry) {
			return entry == null ? unmeasurable(node) : MeasureResult.entry(entry);
		}

		private MeasureResult unmeasurable(final ClientHudNode node) {
			return MeasureResult.failure();
		}

		private boolean visible(final ClientHudNode node) {
			var control = node.meta().clientControl();
			if (!control.visible()) {
				return false;
			}
			return !(control.allowHide() && this.hidden.contains(node.id()));
		}

		private Entry text(final TextNode node, final int maximumWidth) {
			int sourceWidth = this.font.width(node.value());
			int preferred = Math.max(1, sourceWidth);
			int width = Math.min(Math.min(DEFAULT_TEXT_WIDTH, maximumWidth), preferred);
			double scale = 1.0D;
			List<FormattedCharSequence> lines;
			switch (node.overflow()) {
				case WRAP -> lines = this.font.split(node.value(), Math.max(1, width));
				case ELLIPSIS -> {
					lines = List.of(ellipsized(node.value(), Math.max(1, width)));
				}
				case SCALE_ONCE -> {
					lines = List.of(node.value().getVisualOrderText());
					if (preferred > width) {
						scale = Math.max(0.67D, width / (double)preferred);
						if (preferred * scale > width) {
							lines = List.of(ellipsized(node.value(), Math.max(1, (int)Math.floor(width / scale))));
						}
					}
				}
				default -> throw new IllegalStateException("unknown HUD text overflow");
			}
			if (lines.isEmpty()) {
				return null;
			}
			int visibleLines = Math.min(node.maximumLines(), lines.size());
			lines = new ArrayList<>(lines.subList(0, visibleLines));
			int effectiveWidth = Math.max(1, width);
			final double renderedScale = scale;
			if (
				sourceWidth > 0
					&& (
						lines.stream().allMatch(line -> this.font.width(line) == 0)
							|| lines.stream().anyMatch(line -> this.font.width(line) * renderedScale > effectiveWidth + 0.01D)
					)
			) {
				return null;
			}
			int measuredWidth = lines.stream().mapToInt(this.font::width).max().orElse(1);
			int height = Math.max(1, (int)Math.ceil(visibleLines * this.font.lineHeight * scale));
			return entry(node, Math.min(width, Math.max(1, measuredWidth)), height, scale, lines, List.of());
		}

		private FormattedCharSequence ellipsized(final Component value, final int width) {
			if (this.font.width(value) <= width) {
				return value.getVisualOrderText();
			}
			Component suffix = Component.literal("…");
			FormattedText cut = this.font.substrByWidth(
				value,
				Math.max(0, width - this.font.width(suffix))
			);
			return Language.getInstance().getVisualOrder(FormattedText.composite(cut, suffix));
		}

		private Entry counter(final CounterNode node, final int maximumWidth) {
			String value = node.value().getString() + node.suffix().map(Component::getString).orElse("");
			int gap = 4;
			int labelWidth = this.font.width(node.label());
			int valueWidth = this.font.width(value);
			int minimum = minimumInlineWidth(labelWidth, valueWidth, gap);
			if (maximumWidth < minimum) {
				return null;
			}
			int width = Math.min(maximumWidth, Math.max(40, labelWidth + gap + valueWidth));
			return entry(node, width, this.font.lineHeight, 1.0D, List.of(), List.of());
		}

		private Entry badge(final BadgeNode node, final int maximumWidth) {
			if (maximumWidth < this.font.width("…") + 6) {
				return null;
			}
			int width = Math.min(maximumWidth, Math.max(18, this.font.width(node.label()) + 10));
			return entry(node, width, this.font.lineHeight + 6, 1.0D, List.of(), List.of());
		}

		private Entry progress(final ProgressNode node, final int maximumWidth) {
			if (node.orientation() == Orientation.VERTICAL) {
				return entry(node, 14, Math.min(96, Math.max(48, node.meta().style().height().orElse(72))), 1.0D, List.of(), List.of());
			}
			int width = Math.min(maximumWidth, node.meta().style().width().orElse(DEFAULT_PROGRESS_WIDTH));
			int height = node.label().isPresent() ? this.font.lineHeight + 10 : node.spine() ? 12 : 8;
			return entry(node, Math.max(24, width), height, 1.0D, List.of(), List.of());
		}

		private Entry timer(final TimerNode node, final int maximumWidth) {
			int labelWidth = node.label().map(this.font::width).orElse(0);
			int representativeValueWidth = this.font.width("00:00:00");
			int minimum = minimumInlineWidth(labelWidth, representativeValueWidth, 4);
			if (maximumWidth < minimum) {
				return null;
			}
			int width = Math.min(maximumWidth, Math.max(DEFAULT_TIMER_WIDTH, labelWidth));
			return entry(node, width, this.font.lineHeight + 4, 1.0D, List.of(), List.of());
		}

		private int minimumInlineWidth(final int leftWidth, final int rightWidth, final int gap) {
			int fragment = this.font.width("…");
			int left = leftWidth <= 0 ? 0 : Math.min(leftWidth, fragment);
			int right = rightWidth <= 0 ? 0 : Math.min(rightWidth, fragment);
			return left + right + (left > 0 && right > 0 ? gap : 0);
		}

		private Entry separator(final SeparatorNode node, final int maximumWidth) {
			return node.orientation() == Orientation.HORIZONTAL
				? entry(node, maximumWidth, node.thickness(), 1.0D, List.of(), List.of())
				: entry(node, node.thickness(), 24, 1.0D, List.of(), List.of());
		}

		private MeasureResult background(final BackgroundNode node, final int maximumWidth) {
			int insetX = node.innerPadding().left() + node.innerPadding().right() + node.borderWidth() * 2;
			MeasureResult childResult = measure(node.child(), Math.max(1, maximumWidth - insetX));
			if (childResult.failed()) {
				return childResult;
			}
			if (childResult.status() == MeasureStatus.HIDDEN) {
				return MeasureResult.hidden();
			}
			Entry child = childResult.entry();
			int dx = node.borderWidth() + node.innerPadding().left();
			int dy = node.borderWidth() + node.innerPadding().top();
			Entry moved = child.moved(dx, dy);
			return MeasureResult.entry(entry(
				node,
				child.width() + insetX,
				child.height() + node.innerPadding().top() + node.innerPadding().bottom() + node.borderWidth() * 2,
				1.0D,
				List.of(),
				List.of(moved)
			));
		}

		private MeasureResult container(final ContainerNode node, final int maximumWidth) {
			List<String> ids = node.children();
			List<Entry> measured = new ArrayList<>();
			Type type = node.meta().type();
			if (type == Type.ROW) {
				for (String id : ids) {
					if (!this.nodes.containsKey(id)) {
						return MeasureResult.failure();
					}
				}
				List<String> visibleIds = ids.stream().filter(id -> visible(this.nodes.get(id))).toList();
				int gaps = node.gap() * Math.max(0, visibleIds.size() - 1);
				int available = Math.max(1, maximumWidth - gaps);
				double weightTotal = node.weights().isEmpty()
					? visibleIds.size()
					: visibleIds.stream().mapToDouble(id -> {
						int index = ids.indexOf(id);
						double value = node.weights().get(index);
						return value <= 0.0D ? 1.0D : value;
					}).sum();
				for (int index = 0; index < ids.size(); index++) {
					if (!visible(this.nodes.get(ids.get(index)))) {
						continue;
					}
					double weight = node.weights().isEmpty() || node.weights().get(index) <= 0.0D
						? 1.0D
						: node.weights().get(index);
					int share = Math.max(1, (int)Math.floor(available * weight / Math.max(1.0D, weightTotal)));
					MeasureResult child = measure(ids.get(index), share);
					if (child.failed()) {
						return child;
					}
					if (child.entry() != null) {
						measured.add(child.entry());
					}
				}
			} else {
				for (String childId : ids) {
					MeasureResult child = measure(childId, maximumWidth);
					if (child.failed()) {
						return child;
					}
					if (child.entry() != null) {
						measured.add(child.entry());
					}
				}
			}
			if (measured.isEmpty()) {
				return ids.isEmpty() ? MeasureResult.failure() : MeasureResult.hidden();
			}

			List<Entry> placed = new ArrayList<>();
			if (type == Type.ROW) {
				int height = measured.stream().mapToInt(Entry::height).max().orElse(1);
				int x = 0;
				for (Entry child : measured) {
					int y = alignedOffset(node.alignment(), height, child.height());
					placed.add(child.moved(x, y));
					x += child.width() + node.gap();
				}
				int width = x - node.gap();
				return MeasureResult.entry(entry(node, width, height, 1.0D, List.of(), placed));
			}
			if (type == Type.OVERLAY) {
				int width = measured.stream().mapToInt(Entry::width).max().orElse(1);
				int height = measured.stream().mapToInt(Entry::height).max().orElse(1);
				for (Entry child : measured) {
					int x = alignedOffset(node.alignment(), width, child.width());
					int y = alignedOffset(node.alignment(), height, child.height());
					placed.add(child.moved(x, y));
				}
				return MeasureResult.entry(entry(node, width, height, 1.0D, List.of(), placed));
			}

			int width = measured.stream().mapToInt(Entry::width).max().orElse(1);
			int y = 0;
			for (Entry child : measured) {
				int x = alignedOffset(node.alignment(), width, child.width());
				placed.add(child.moved(x, y));
				y += child.height() + node.gap();
			}
			return MeasureResult.entry(entry(node, width, y - node.gap(), 1.0D, List.of(), placed));
		}

		private Entry leaf(final ClientHudNode node, final int width, final int height) {
			return entry(node, width, height, 1.0D, List.of(), List.of());
		}

		private static Entry entry(
			final ClientHudNode node,
			final int width,
			final int height,
			final double scale,
			final List<FormattedCharSequence> lines,
			final List<Entry> children
		) {
			return new Entry(
				node,
				0,
				0,
				Math.max(1, width),
				Math.max(1, height),
				0,
				0,
				Math.max(1, width),
				Math.max(1, height),
				scale,
				lines,
				children
			);
		}

		private static int alignedOffset(final Alignment alignment, final int available, final int size) {
			return switch (alignment) {
				case CENTER -> Math.max(0, available - size) / 2;
				case END -> Math.max(0, available - size);
				case START, STRETCH -> 0;
			};
		}
	}
}
