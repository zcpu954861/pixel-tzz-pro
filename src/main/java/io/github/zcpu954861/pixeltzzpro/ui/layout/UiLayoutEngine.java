package io.github.zcpu954861.pixeltzzpro.ui.layout;

import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AlignSelf;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Anchor;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.CrossAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Direction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Insets;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MainAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeMode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, client-neutral layout of an already expanded UI tree.
 *
 * <p>Binding, responsive overrides, text measurement, and Repeat expansion happen before this
 * class is called. The engine has no retained state, so the same input always produces the same
 * result.
 */
public final class UiLayoutEngine {
	public static final int MAX_DIAGNOSTIC_DETAILS = 50;

	private UiLayoutEngine() {
	}

	public static LayoutResult layout(
		final LayoutItem root,
		final int viewportWidth,
		final int viewportHeight
	) {
		Objects.requireNonNull(root, "root");
		Diagnostics diagnostics = new Diagnostics();
		int safeWidth = Math.max(0, viewportWidth);
		int safeHeight = Math.max(0, viewportHeight);
		if (viewportWidth < 0 || viewportHeight < 0) {
			diagnostics.add(
				DiagnosticCode.NEGATIVE_VIEWPORT,
				root,
				"negative viewport " + viewportWidth + "x" + viewportHeight + " was clamped to zero"
			);
		}

		Rect viewport = new Rect(0, 0, safeWidth, safeHeight);
		Edges margin = edges(root.layout().margin());
		int availableWidth = subtract(safeWidth, margin.horizontal());
		int availableHeight = subtract(safeHeight, margin.vertical());
		Measure measured = measure(root, availableWidth, availableHeight, false);
		Rect rootBounds = new Rect(
			margin.left(),
			margin.top(),
			measured.width(),
			measured.height()
		);
		if (
			rootBounds.x() < viewport.x()
				|| rootBounds.y() < viewport.y()
				|| rootBounds.right() > viewport.right()
				|| rootBounds.bottom() > viewport.bottom()
		) {
			diagnostics.add(DiagnosticCode.OVERFLOW, root, "root bounds exceed the viewport");
		}
		PlacedNode placed = place(root, rootBounds, viewport, diagnostics);
		return new LayoutResult(placed, diagnostics.details(), diagnostics.total());
	}

	public static Rect scrollbarThumb(
		final Rect track,
		final Direction direction,
		final int contentExtent,
		final int scrollOffset,
		final int maximumOffset
	) {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(direction, "direction");
		int viewportExtent = direction == Direction.VERTICAL ? track.height() : track.width();
		if (viewportExtent <= 0) {
			return new Rect(track.x(), track.y(), 0, 0);
		}
		int thumbExtent = Math.min(
			viewportExtent,
			Math.max(
				12,
				(int)Math.round(
					(double)viewportExtent * viewportExtent / Math.max(1, contentExtent)
				)
			)
		);
		int travel = Math.max(0, viewportExtent - thumbExtent);
		int safeMaximum = Math.max(0, maximumOffset);
		int safeOffset = Math.clamp(scrollOffset, 0, safeMaximum);
		int position = safeMaximum == 0
			? 0
			: (int)Math.round((double)safeOffset * travel / safeMaximum);
		return direction == Direction.VERTICAL
			? new Rect(track.x(), coordinateAdd(track.y(), position), track.width(), thumbExtent)
			: new Rect(coordinateAdd(track.x(), position), track.y(), thumbExtent, track.height());
	}

	/**
	 * Returns a uniform down-scale that keeps oversized page content inside its physical viewport.
	 */
	public static double fitScale(
		final int contentWidth,
		final int contentHeight,
		final int viewportWidth,
		final int viewportHeight
	) {
		if (contentWidth <= 0 || contentHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
			return 1.0;
		}
		return Math.min(
			1.0,
			Math.min(
				(double)viewportWidth / contentWidth,
				(double)viewportHeight / contentHeight
			)
		);
	}

	private static PlacedNode place(
		final LayoutItem item,
		final Rect bounds,
		final Rect inheritedClip,
		final Diagnostics diagnostics
	) {
		validateItem(item, diagnostics);
		Edges padding = edges(item.layout().padding());
		Rect contentBounds = bounds.inset(padding);
		Placement placement = switch (item.type()) {
			case ROW -> placeLinear(item, contentBounds, inheritedClip, diagnostics, Axis.HORIZONTAL);
			case COLUMN, REPEAT -> placeLinear(item, contentBounds, inheritedClip, diagnostics, Axis.VERTICAL);
			case GRID -> placeGrid(item, contentBounds, inheritedClip, diagnostics);
			case FLOW -> placeFlow(item, contentBounds, inheritedClip, diagnostics);
			case OVERLAY -> placeOverlay(item, contentBounds, inheritedClip, diagnostics);
			case SCROLL -> placeScroll(item, contentBounds, inheritedClip, diagnostics);
			case CARD -> placeCard(item, contentBounds, inheritedClip, diagnostics);
			default -> placeLeaf(item, contentBounds, diagnostics);
		};

		boolean allowHorizontalOverflow = item.type() == NodeType.SCROLL
			&& scrollDirection(item) == Direction.HORIZONTAL;
		boolean allowVerticalOverflow = item.type() == NodeType.SCROLL
			&& scrollDirection(item) == Direction.VERTICAL;
		if (!allowHorizontalOverflow && placement.contentWidth() > contentBounds.width()) {
			diagnostics.add(
				DiagnosticCode.OVERFLOW,
				item,
				"content width " + placement.contentWidth() + " exceeds " + contentBounds.width()
			);
		}
		if (!allowVerticalOverflow && placement.contentHeight() > contentBounds.height()) {
			diagnostics.add(
				DiagnosticCode.OVERFLOW,
				item,
				"content height " + placement.contentHeight() + " exceeds " + contentBounds.height()
			);
		}

		return new PlacedNode(
			item.key(),
			item.type(),
			item.pointer(),
			bounds,
			contentBounds,
			inheritedClip,
			placement.contentWidth(),
			placement.contentHeight(),
			placement.scrollOffset(),
			placement.children()
		);
	}

	private static Placement placeLeaf(
		final LayoutItem item,
		final Rect contentBounds,
		final Diagnostics diagnostics
	) {
		if (!item.children().isEmpty()) {
			diagnostics.add(
				DiagnosticCode.INVALID_CHILD_COUNT,
				item,
				item.type() + " is a leaf but received " + item.children().size() + " children"
			);
		}
		return new Placement(
			safeIntrinsic(item.intrinsicWidth()),
			safeIntrinsic(item.intrinsicHeight()),
			0,
			List.of()
		);
	}

	private static Placement placeLinear(
		final LayoutItem item,
		final Rect area,
		final Rect clip,
		final Diagnostics diagnostics,
		final Axis axis
	) {
		List<LinearChild> plans = new ArrayList<>(item.children().size());
		int baseGap = nonNegative(item.layout().gap().orElse(0));
		int availableMain = axis.main(area);
		int availableCross = axis.cross(area);
		int fixedMain = multiply(baseGap, Math.max(0, item.children().size() - 1));
		double totalWeight = 0.0;

		for (LayoutItem child : item.children()) {
			Measure natural = measure(child, area.width(), area.height(), true);
			Edges margin = edges(child.layout().margin());
			SizeSpec mainSpec = sizeSpec(child.layout(), axis);
			boolean flexible = mainSpec.mode() == SizeMode.FILL || mainSpec.mode() == SizeMode.WEIGHT;
			int mainSize;
			double weight;
			if (flexible) {
				mainSize = minimum(child.layout(), axis);
				weight = mainSpec.mode() == SizeMode.WEIGHT
					? safeWeight(mainSpec.value().orElse(1.0))
					: 1.0;
				totalWeight += weight;
			} else {
				mainSize = resolve(
					mainSpec,
					axis.main(natural),
					availableMain,
					child.layout(),
					axis,
					true
				);
				weight = 0.0;
			}
			fixedMain = add(fixedMain, add(mainSize, axis.main(margin)));
			plans.add(new LinearChild(child, natural, margin, mainSize, flexible, weight));
		}

		int remaining = Math.max(0, availableMain - fixedMain);
		if (totalWeight > 0.0 && remaining > 0) {
			double accumulatedWeight = 0.0;
			int allocated = 0;
			for (int index = 0; index < plans.size(); index++) {
				LinearChild plan = plans.get(index);
				if (!plan.flexible()) {
					continue;
				}
				accumulatedWeight += plan.weight();
				int targetAllocated = accumulatedWeight >= totalWeight
					? remaining
					: boundedFloor(remaining * accumulatedWeight / totalWeight);
				int share = Math.max(0, targetAllocated - allocated);
				allocated = targetAllocated;
				int mainSize = clampDimension(
					add(plan.mainSize(), share),
					plan.item().layout(),
					axis
				);
				plans.set(index, plan.withMainSize(mainSize));
			}
		}
		for (int index = 0; index < plans.size(); index++) {
			LinearChild plan = plans.get(index);
			int childWidth = axis == Axis.HORIZONTAL ? plan.mainSize() : availableCross;
			int childHeight = axis == Axis.VERTICAL ? plan.mainSize() : availableCross;
			plans.set(
				index,
				plan.withNatural(measure(plan.item(), childWidth, childHeight, true))
			);
		}

		int usedMain = multiply(baseGap, Math.max(0, plans.size() - 1));
		for (LinearChild plan : plans) {
			usedMain = add(usedMain, add(plan.mainSize(), axis.main(plan.margin())));
		}
		int spare = Math.max(0, availableMain - usedMain);
		MainAlign mainAlign = item.layout().mainAlign().orElse(MainAlign.START);
		int mainCursor = switch (mainAlign) {
			case START, SPACE_BETWEEN -> 0;
			case CENTER -> spare / 2;
			case END -> spare;
		};
		int distributableGap = mainAlign == MainAlign.SPACE_BETWEEN && plans.size() > 1 ? spare : 0;
		int distributedBefore = 0;
		List<PlacedNode> children = new ArrayList<>(plans.size());
		int crossExtent = 0;

		for (int index = 0; index < plans.size(); index++) {
			LinearChild plan = plans.get(index);
			AlignSelf crossAlignment = effectiveCrossAlignment(item.layout(), plan.item().layout());
			int crossStartMargin = axis.crossStart(plan.margin());
			int crossEndMargin = axis.crossEnd(plan.margin());
			int crossAvailable = subtract(availableCross, add(crossStartMargin, crossEndMargin));
			int crossSize = resolveCrossSize(
				plan.item(),
				plan.natural(),
				axis,
				crossAvailable,
				crossAlignment
			);
			int crossPosition = switch (crossAlignment) {
				case CENTER -> crossStartMargin + Math.max(0, crossAvailable - crossSize) / 2;
				case END -> crossStartMargin + Math.max(0, crossAvailable - crossSize);
				case AUTO, START, STRETCH -> crossStartMargin;
			};

			int childMain = mainCursor + axis.mainStart(plan.margin());
			Rect childBounds = axis.rect(
				area,
				childMain,
				crossPosition,
				plan.mainSize(),
				crossSize
			);
			children.add(place(plan.item(), childBounds, clip, diagnostics));
			mainCursor = add(mainCursor, add(plan.mainSize(), axis.main(plan.margin())));
			crossExtent = Math.max(
				crossExtent,
				add(crossPosition, add(crossSize, crossEndMargin))
			);
			if (index + 1 < plans.size()) {
				int extraGap = 0;
				if (distributableGap > 0) {
					int target = (int)((long)distributableGap * (index + 1) / (plans.size() - 1));
					extraGap = target - distributedBefore;
					distributedBefore = target;
				}
				mainCursor = add(mainCursor, add(baseGap, extraGap));
			}
		}

		int contentMain = Math.max(usedMain, mainCursor);
		return axis.placement(contentMain, crossExtent, children);
	}

	private static Placement placeGrid(
		final LayoutItem item,
		final Rect area,
		final Rect clip,
		final Diagnostics diagnostics
	) {
		GridPlan plan = gridPlan(item, area.width(), area.height(), true);
		List<PlacedNode> children = new ArrayList<>(plan.entries().size());
		int[] columnOffsets = offsets(plan.columnSizes(), plan.columnGap());
		int[] rowOffsets = offsets(plan.rowSizes(), plan.rowGap());

		for (GridEntry entry : plan.entries()) {
			LayoutItem child = entry.item();
			Edges margin = entry.margin();
			int spanWidth = spanSize(plan.columnSizes(), entry.column(), entry.span(), plan.columnGap());
			int cellHeight = plan.rowSizes()[entry.row()];
			int availableWidth = subtract(spanWidth, margin.horizontal());
			int availableHeight = subtract(cellHeight, margin.vertical());
			AlignSelf alignment = child.layout().alignSelf();
			int width = resolveGridChildSize(
				child,
				Axis.HORIZONTAL,
				entry.natural().width(),
				availableWidth,
				alignment
			);
			int height = resolveGridChildSize(
				child,
				Axis.VERTICAL,
				entry.natural().height(),
				availableHeight,
				alignment
			);
			if (add(width, margin.horizontal()) > spanWidth || add(height, margin.vertical()) > cellHeight) {
				diagnostics.add(
					DiagnosticCode.OVERFLOW,
					child,
					"grid child exceeds its assigned cell"
				);
			}
			int x = add(
				area.x(),
				add(columnOffsets[entry.column()], alignedOffset(alignment, availableWidth, width, margin.left()))
			);
			int y = add(
				area.y(),
				add(rowOffsets[entry.row()], alignedOffset(alignment, availableHeight, height, margin.top()))
			);
			children.add(place(child, new Rect(x, y, width, height), clip, diagnostics));
		}

		return new Placement(plan.contentWidth(), plan.contentHeight(), 0, children);
	}

	private static Placement placeFlow(
		final LayoutItem item,
		final Rect area,
		final Rect clip,
		final Diagnostics diagnostics
	) {
		Axis axis = item.layout().direction().orElse(Direction.HORIZONTAL) == Direction.HORIZONTAL
			? Axis.HORIZONTAL
			: Axis.VERTICAL;
		int availableMain = axis.main(area);
		int gap = nonNegative(item.layout().gap().orElse(0));
		int lineGap = nonNegative(item.layout().lineGap().orElse(gap));
		List<FlowLine> lines = flowLines(item, area.width(), area.height(), axis, gap);
		int crossCursor = 0;
		int contentMain = 0;
		List<PlacedNode> children = new ArrayList<>(item.children().size());

		for (FlowLine line : lines) {
			int lineUsed = line.mainSize();
			int spare = Math.max(0, availableMain - lineUsed);
			MainAlign alignment = item.layout().mainAlign().orElse(MainAlign.START);
			int mainCursor = switch (alignment) {
				case START, SPACE_BETWEEN -> 0;
				case CENTER -> spare / 2;
				case END -> spare;
			};
			int extraForGaps = alignment == MainAlign.SPACE_BETWEEN && line.children().size() > 1 ? spare : 0;
			int distributedBefore = 0;

			for (int index = 0; index < line.children().size(); index++) {
				FlowChild child = line.children().get(index);
				AlignSelf crossAlignment = effectiveCrossAlignment(item.layout(), child.item().layout());
				int childCrossAvailable = subtract(
					line.crossSize(),
					add(axis.crossStart(child.margin()), axis.crossEnd(child.margin()))
				);
				int childCross = resolveCrossSize(
					child.item(),
					child.natural(),
					axis,
					childCrossAvailable,
					crossAlignment
				);
				int crossPosition = crossCursor
					+ alignedOffset(
						crossAlignment,
						childCrossAvailable,
						childCross,
						axis.crossStart(child.margin())
					);
				int childMain = mainCursor + axis.mainStart(child.margin());
				Rect childBounds = axis.rect(
					area,
					childMain,
					crossPosition,
					child.mainSize(),
					childCross
				);
				children.add(place(child.item(), childBounds, clip, diagnostics));
				mainCursor = add(mainCursor, add(child.mainSize(), axis.main(child.margin())));
				if (index + 1 < line.children().size()) {
					int extraGap = 0;
					if (extraForGaps > 0) {
						int target = (int)((long)extraForGaps * (index + 1) / (line.children().size() - 1));
						extraGap = target - distributedBefore;
						distributedBefore = target;
					}
					mainCursor = add(mainCursor, add(gap, extraGap));
				}
			}
			contentMain = Math.max(contentMain, Math.max(lineUsed, mainCursor));
			crossCursor = add(crossCursor, line.crossSize());
			if (line != lines.getLast()) {
				crossCursor = add(crossCursor, lineGap);
			}
		}

		return axis.placement(contentMain, crossCursor, children);
	}

	private static Placement placeOverlay(
		final LayoutItem item,
		final Rect area,
		final Rect clip,
		final Diagnostics diagnostics
	) {
		List<PlacedNode> children = new ArrayList<>(item.children().size());
		int minimumX = 0;
		int minimumY = 0;
		int maximumX = 0;
		int maximumY = 0;
		for (LayoutItem child : item.children()) {
			Measure natural = measure(child, area.width(), area.height(), true);
			Edges margin = edges(child.layout().margin());
			int availableWidth = subtract(area.width(), margin.horizontal());
			int availableHeight = subtract(area.height(), margin.vertical());
			int width = resolveOverlaySize(child, Axis.HORIZONTAL, natural.width(), availableWidth);
			int height = resolveOverlaySize(child, Axis.VERTICAL, natural.height(), availableHeight);
			Anchor anchor = child.layout().anchor().orElse(Anchor.TOP_LEFT);
			int x = coordinateAdd(
				coordinateAdd(anchoredX(anchor, availableWidth, width), margin.left()),
				child.layout().offsetX().orElse(0)
			);
			int y = coordinateAdd(
				coordinateAdd(anchoredY(anchor, availableHeight, height), margin.top()),
				child.layout().offsetY().orElse(0)
			);
			children.add(
				place(
					child,
					new Rect(coordinateAdd(area.x(), x), coordinateAdd(area.y(), y), width, height),
					clip,
					diagnostics
				)
			);
			minimumX = Math.min(minimumX, coordinateAdd(x, -margin.left()));
			minimumY = Math.min(minimumY, coordinateAdd(y, -margin.top()));
			maximumX = Math.max(maximumX, coordinateAdd(x, add(width, margin.right())));
			maximumY = Math.max(maximumY, coordinateAdd(y, add(height, margin.bottom())));
		}
		return new Placement(
			extent(minimumX, maximumX),
			extent(minimumY, maximumY),
			0,
			children
		);
	}

	private static Placement placeScroll(
		final LayoutItem item,
		final Rect area,
		final Rect inheritedClip,
		final Diagnostics diagnostics
	) {
		if (item.children().size() != 1) {
			diagnostics.add(
				DiagnosticCode.INVALID_CHILD_COUNT,
				item,
				"scroll requires exactly one child but received " + item.children().size()
			);
		}
		if (item.children().isEmpty()) {
			return new Placement(0, 0, 0, List.of());
		}

		LayoutItem child = item.children().getFirst();
		Direction direction = scrollDirection(item);
		Measure natural = measure(child, area.width(), area.height(), true);
		Edges margin = edges(child.layout().margin());
		int childWidth = direction == Direction.VERTICAL
			? resolveScrollCrossSize(child, Axis.HORIZONTAL, natural.width(), subtract(area.width(), margin.horizontal()))
			: natural.width();
		int childHeight = direction == Direction.HORIZONTAL
			? resolveScrollCrossSize(child, Axis.VERTICAL, natural.height(), subtract(area.height(), margin.vertical()))
			: natural.height();
		int contentWidth = add(childWidth, margin.horizontal());
		int contentHeight = add(childHeight, margin.vertical());
		int contentMain = direction == Direction.VERTICAL ? contentHeight : contentWidth;
		int viewportMain = direction == Direction.VERTICAL ? area.height() : area.width();
		int maximumOffset = Math.max(0, contentMain - viewportMain);
		int offset = Math.clamp(item.scrollOffset(), 0, maximumOffset);
		if (offset != item.scrollOffset()) {
			diagnostics.add(
				DiagnosticCode.SCROLL_CLAMPED,
				item,
				"scroll offset " + item.scrollOffset() + " was clamped to " + offset
			);
		}
		int childX = coordinateAdd(
			area.x(),
			margin.left() - (direction == Direction.HORIZONTAL ? offset : 0)
		);
		int childY = coordinateAdd(
			area.y(),
			margin.top() - (direction == Direction.VERTICAL ? offset : 0)
		);
		Rect scrollClip = inheritedClip.intersection(area);
		PlacedNode placed = place(
			child,
			new Rect(childX, childY, childWidth, childHeight),
			scrollClip,
			diagnostics
		);
		return new Placement(contentWidth, contentHeight, offset, List.of(placed));
	}

	private static Placement placeCard(
		final LayoutItem item,
		final Rect area,
		final Rect clip,
		final Diagnostics diagnostics
	) {
		if (item.children().size() != 1) {
			diagnostics.add(
				DiagnosticCode.INVALID_CHILD_COUNT,
				item,
				"card requires exactly one child but received " + item.children().size()
			);
		}
		if (item.children().isEmpty()) {
			return new Placement(0, 0, 0, List.of());
		}

		LayoutItem child = item.children().getFirst();
		Measure natural = measure(child, area.width(), area.height(), true);
		Edges margin = edges(child.layout().margin());
		int availableWidth = subtract(area.width(), margin.horizontal());
		int availableHeight = subtract(area.height(), margin.vertical());
		int width = resolveCardSize(child, Axis.HORIZONTAL, natural.width(), availableWidth);
		int height = resolveCardSize(child, Axis.VERTICAL, natural.height(), availableHeight);
		PlacedNode placed = place(
			child,
			new Rect(area.x() + margin.left(), area.y() + margin.top(), width, height),
			clip,
			diagnostics
		);
		return new Placement(
			add(width, margin.horizontal()),
			add(height, margin.vertical()),
			0,
			List.of(placed)
		);
	}

	private static Measure measure(
		final LayoutItem item,
		final int offeredWidth,
		final int offeredHeight,
		final boolean flexibleAsContent
	) {
		int availableWidth = Math.max(0, offeredWidth);
		int availableHeight = Math.max(0, offeredHeight);
		Edges padding = edges(item.layout().padding());
		int innerWidth = subtract(provisional(item.layout().width(), availableWidth), padding.horizontal());
		int innerHeight = subtract(provisional(item.layout().height(), availableHeight), padding.vertical());
		Measure content = switch (item.type()) {
			case ROW -> measureLinear(item, innerWidth, innerHeight, Axis.HORIZONTAL);
			case COLUMN, REPEAT -> measureLinear(item, innerWidth, innerHeight, Axis.VERTICAL);
			case GRID -> {
				GridPlan plan = gridPlan(item, innerWidth, innerHeight, false);
				yield new Measure(plan.contentWidth(), plan.contentHeight());
			}
			case FLOW -> measureFlow(item, innerWidth, innerHeight);
			case OVERLAY -> measureOverlay(item, innerWidth, innerHeight);
			case SCROLL, CARD -> measureSingle(item, innerWidth, innerHeight);
			default -> new Measure(
				safeIntrinsic(item.intrinsicWidth()),
				safeIntrinsic(item.intrinsicHeight())
			);
		};
		int naturalWidth = add(content.width(), padding.horizontal());
		int naturalHeight = add(content.height(), padding.vertical());
		return new Measure(
			resolve(
				sizeSpec(item.layout(), Axis.HORIZONTAL),
				naturalWidth,
				availableWidth,
				item.layout(),
				Axis.HORIZONTAL,
				flexibleAsContent
			),
			resolve(
				sizeSpec(item.layout(), Axis.VERTICAL),
				naturalHeight,
				availableHeight,
				item.layout(),
				Axis.VERTICAL,
				flexibleAsContent
			)
		);
	}

	private static Measure measureLinear(
		final LayoutItem item,
		final int availableWidth,
		final int availableHeight,
		final Axis axis
	) {
		int main = multiply(nonNegative(item.layout().gap().orElse(0)), Math.max(0, item.children().size() - 1));
		int cross = 0;
		for (LayoutItem child : item.children()) {
			Measure measured = measure(child, availableWidth, availableHeight, true);
			Edges margin = edges(child.layout().margin());
			main = add(main, add(axis.main(measured), axis.main(margin)));
			cross = Math.max(cross, add(axis.cross(measured), axis.cross(margin)));
		}
		return axis.measure(main, cross);
	}

	private static Measure measureFlow(
		final LayoutItem item,
		final int availableWidth,
		final int availableHeight
	) {
		Axis axis = item.layout().direction().orElse(Direction.HORIZONTAL) == Direction.HORIZONTAL
			? Axis.HORIZONTAL
			: Axis.VERTICAL;
		List<FlowLine> lines = flowLines(
			item,
			availableWidth,
			availableHeight,
			axis,
			nonNegative(item.layout().gap().orElse(0))
		);
		int main = 0;
		int cross = multiply(
			nonNegative(item.layout().lineGap().orElse(item.layout().gap().orElse(0))),
			Math.max(0, lines.size() - 1)
		);
		for (FlowLine line : lines) {
			main = Math.max(main, line.mainSize());
			cross = add(cross, line.crossSize());
		}
		return axis.measure(main, cross);
	}

	private static Measure measureOverlay(
		final LayoutItem item,
		final int availableWidth,
		final int availableHeight
	) {
		int width = 0;
		int height = 0;
		for (LayoutItem child : item.children()) {
			Measure measured = measure(child, availableWidth, availableHeight, true);
			Edges margin = edges(child.layout().margin());
			width = Math.max(
				width,
				add(add(measured.width(), margin.horizontal()), safeAbsolute(child.layout().offsetX().orElse(0)))
			);
			height = Math.max(
				height,
				add(add(measured.height(), margin.vertical()), safeAbsolute(child.layout().offsetY().orElse(0)))
			);
		}
		return new Measure(width, height);
	}

	private static Measure measureSingle(
		final LayoutItem item,
		final int availableWidth,
		final int availableHeight
	) {
		if (item.children().isEmpty()) {
			return new Measure(0, 0);
		}
		LayoutItem child = item.children().getFirst();
		Measure measured = measure(child, availableWidth, availableHeight, true);
		Edges margin = edges(child.layout().margin());
		return new Measure(
			add(measured.width(), margin.horizontal()),
			add(measured.height(), margin.vertical())
		);
	}

	private static GridPlan gridPlan(
		final LayoutItem item,
		final int availableWidth,
		final int availableHeight,
		final boolean distributeFlexibleSpace
	) {
		List<SizeSpec> columns = item.layout().columns().isEmpty()
			? List.of(new SizeSpec(SizeMode.FILL, Optional.empty()))
			: item.layout().columns();
		int columnCount = columns.size();
		int columnGap = nonNegative(item.layout().columnGap().orElse(0));
		int rowGap = nonNegative(item.layout().rowGap().orElse(0));
		List<GridEntry> entries = new ArrayList<>(item.children().size());
		int column = 0;
		int row = 0;
		for (LayoutItem child : item.children()) {
			int span = Math.clamp(child.layout().columnSpan().orElse(1), 1, columnCount);
			if (column + span > columnCount) {
				row++;
				column = 0;
			}
			Measure natural = measure(child, availableWidth, availableHeight, true);
			entries.add(new GridEntry(child, natural, edges(child.layout().margin()), column, row, span));
			column += span;
			if (column >= columnCount) {
				row++;
				column = 0;
			}
		}
		int rowCount = entries.isEmpty() ? 0 : entries.stream().mapToInt(GridEntry::row).max().orElse(0) + 1;
		int[] trackSizes = new int[columnCount];
		for (int index = 0; index < columnCount; index++) {
			SizeSpec spec = columns.get(index);
			trackSizes[index] = switch (spec.mode()) {
				case FIXED -> specValue(spec, 0);
				case PERCENT -> percent(availableWidth, spec.value().orElse(0.0));
				default -> 0;
			};
		}

		for (GridEntry entry : entries) {
			if (entry.span() != 1) {
				continue;
			}
			SizeMode mode = columns.get(entry.column()).mode();
			if (mode == SizeMode.CONTENT || mode == SizeMode.FILL || mode == SizeMode.WEIGHT) {
				trackSizes[entry.column()] = Math.max(
					trackSizes[entry.column()],
					add(entry.natural().width(), entry.margin().horizontal())
				);
			}
		}
		for (GridEntry entry : entries) {
			if (entry.span() == 1) {
				continue;
			}
			int required = add(entry.natural().width(), entry.margin().horizontal());
			int current = spanSize(trackSizes, entry.column(), entry.span(), columnGap);
			int deficit = Math.max(0, required - current);
			if (deficit > 0) {
				List<Integer> candidates = new ArrayList<>();
				for (int index = entry.column(); index < entry.column() + entry.span(); index++) {
					SizeMode mode = columns.get(index).mode();
					if (mode == SizeMode.CONTENT || mode == SizeMode.FILL || mode == SizeMode.WEIGHT) {
						candidates.add(index);
					}
				}
				if (candidates.isEmpty()) {
					candidates.add(entry.column() + entry.span() - 1);
				}
				distribute(deficit, candidates, trackSizes);
			}
		}

		if (distributeFlexibleSpace) {
			int gaps = multiply(columnGap, Math.max(0, columnCount - 1));
			int used = add(gaps, sum(trackSizes));
			int remaining = Math.max(0, availableWidth - used);
			List<WeightedTrack> flexible = new ArrayList<>();
			for (int index = 0; index < columnCount; index++) {
				SizeSpec spec = columns.get(index);
				if (spec.mode() == SizeMode.FILL || spec.mode() == SizeMode.WEIGHT) {
					flexible.add(
						new WeightedTrack(
							index,
							spec.mode() == SizeMode.WEIGHT ? safeWeight(spec.value().orElse(1.0)) : 1.0
						)
					);
				}
			}
			distributeWeighted(remaining, flexible, trackSizes);
		}

		int[] rowSizes = new int[rowCount];
		for (int index = 0; index < entries.size(); index++) {
			GridEntry entry = entries.get(index);
			int spanWidth = spanSize(trackSizes, entry.column(), entry.span(), columnGap);
			Measure remeasured = measure(
				entry.item(),
				subtract(spanWidth, entry.margin().horizontal()),
				availableHeight,
				true
			);
			entry = entry.withNatural(remeasured);
			entries.set(index, entry);
			rowSizes[entry.row()] = Math.max(
				rowSizes[entry.row()],
				add(remeasured.height(), entry.margin().vertical())
			);
		}
		int contentWidth = add(sum(trackSizes), multiply(columnGap, Math.max(0, columnCount - 1)));
		int contentHeight = add(sum(rowSizes), multiply(rowGap, Math.max(0, rowCount - 1)));
		return new GridPlan(
			entries,
			trackSizes,
			rowSizes,
			columnGap,
			rowGap,
			contentWidth,
			contentHeight
		);
	}

	private static List<FlowLine> flowLines(
		final LayoutItem item,
		final int availableWidth,
		final int availableHeight,
		final Axis axis,
		final int gap
	) {
		int limit = axis == Axis.HORIZONTAL ? Math.max(0, availableWidth) : Math.max(0, availableHeight);
		List<FlowLine> lines = new ArrayList<>();
		List<FlowChild> current = new ArrayList<>();
		int currentMain = 0;
		int currentCross = 0;
		for (LayoutItem child : item.children()) {
			Measure natural = measure(child, availableWidth, availableHeight, true);
			Edges margin = edges(child.layout().margin());
			int childMain = add(axis.main(natural), axis.main(margin));
			int childCross = add(axis.cross(natural), axis.cross(margin));
			int candidate = current.isEmpty() ? childMain : add(currentMain, add(gap, childMain));
			if (!current.isEmpty() && candidate > limit) {
				lines.add(new FlowLine(List.copyOf(current), currentMain, currentCross));
				current.clear();
				currentMain = 0;
				currentCross = 0;
			}
			current.add(new FlowChild(child, natural, margin, axis.main(natural)));
			currentMain = current.size() == 1 ? childMain : add(currentMain, add(gap, childMain));
			currentCross = Math.max(currentCross, childCross);
		}
		if (!current.isEmpty()) {
			lines.add(new FlowLine(List.copyOf(current), currentMain, currentCross));
		}
		return List.copyOf(lines);
	}

	private static int resolve(
		final SizeSpec spec,
		final int natural,
		final int available,
		final LayoutDefinition layout,
		final Axis axis,
		final boolean flexibleAsContent
	) {
		int value = switch (spec.mode()) {
			case CONTENT -> natural;
			case FIXED -> specValue(spec, natural);
			case PERCENT -> percent(available, spec.value().orElse(0.0));
			case FILL, WEIGHT -> flexibleAsContent ? natural : available;
		};
		return clampDimension(value, layout, axis);
	}

	private static int resolveCrossSize(
		final LayoutItem item,
		final Measure natural,
		final Axis mainAxis,
		final int available,
		final AlignSelf alignment
	) {
		Axis crossAxis = mainAxis.other();
		SizeSpec spec = sizeSpec(item.layout(), crossAxis);
		if (alignment == AlignSelf.STRETCH || spec.mode() == SizeMode.FILL || spec.mode() == SizeMode.WEIGHT) {
			return clampDimension(available, item.layout(), crossAxis);
		}
		return resolve(spec, crossAxis.main(natural), available, item.layout(), crossAxis, true);
	}

	private static int resolveGridChildSize(
		final LayoutItem item,
		final Axis axis,
		final int natural,
		final int available,
		final AlignSelf alignment
	) {
		SizeSpec spec = sizeSpec(item.layout(), axis);
		if (alignment == AlignSelf.STRETCH || spec.mode() == SizeMode.FILL || spec.mode() == SizeMode.WEIGHT) {
			return clampDimension(available, item.layout(), axis);
		}
		return resolve(spec, natural, available, item.layout(), axis, true);
	}

	private static int resolveOverlaySize(
		final LayoutItem item,
		final Axis axis,
		final int natural,
		final int available
	) {
		SizeSpec spec = sizeSpec(item.layout(), axis);
		return resolve(spec, natural, available, item.layout(), axis, false);
	}

	private static int resolveScrollCrossSize(
		final LayoutItem item,
		final Axis axis,
		final int natural,
		final int available
	) {
		SizeSpec spec = sizeSpec(item.layout(), axis);
		if (spec.mode() == SizeMode.FILL || spec.mode() == SizeMode.WEIGHT) {
			return clampDimension(available, item.layout(), axis);
		}
		return resolve(spec, natural, available, item.layout(), axis, true);
	}

	private static int resolveCardSize(
		final LayoutItem item,
		final Axis axis,
		final int natural,
		final int available
	) {
		SizeSpec spec = sizeSpec(item.layout(), axis);
		if (spec.mode() == SizeMode.CONTENT) {
			return clampDimension(Math.min(natural, available), item.layout(), axis);
		}
		if (
			item.layout().alignSelf() == AlignSelf.STRETCH
				|| spec.mode() == SizeMode.FILL
				|| spec.mode() == SizeMode.WEIGHT
		) {
			return clampDimension(available, item.layout(), axis);
		}
		return resolve(spec, natural, available, item.layout(), axis, true);
	}

	private static int provisional(final Optional<SizeSpec> optional, final int available) {
		SizeSpec spec = optional.orElse(new SizeSpec(SizeMode.CONTENT, Optional.empty()));
		return switch (spec.mode()) {
			case FIXED -> specValue(spec, available);
			case PERCENT -> percent(available, spec.value().orElse(0.0));
			default -> available;
		};
	}

	private static SizeSpec sizeSpec(final LayoutDefinition layout, final Axis axis) {
		return (axis == Axis.HORIZONTAL ? layout.width() : layout.height())
			.orElse(new SizeSpec(SizeMode.CONTENT, Optional.empty()));
	}

	private static int minimum(final LayoutDefinition layout, final Axis axis) {
		return Math.max(
			0,
			(axis == Axis.HORIZONTAL ? layout.minimumWidth() : layout.minimumHeight()).orElse(0)
		);
	}

	private static int clampDimension(
		final int input,
		final LayoutDefinition layout,
		final Axis axis
	) {
		int minimum = minimum(layout, axis);
		int maximum = Math.max(
			minimum,
			(axis == Axis.HORIZONTAL ? layout.maximumWidth() : layout.maximumHeight())
				.orElse(Integer.MAX_VALUE)
		);
		return Math.clamp(Math.max(0, input), minimum, maximum);
	}

	private static AlignSelf effectiveCrossAlignment(
		final LayoutDefinition parent,
		final LayoutDefinition child
	) {
		if (child.alignSelf() != AlignSelf.AUTO) {
			return child.alignSelf();
		}
		return switch (parent.crossAlign().orElse(CrossAlign.START)) {
			case START -> AlignSelf.START;
			case CENTER -> AlignSelf.CENTER;
			case END -> AlignSelf.END;
			case STRETCH -> AlignSelf.STRETCH;
		};
	}

	private static int alignedOffset(
		final AlignSelf alignment,
		final int available,
		final int size,
		final int startMargin
	) {
		return switch (alignment) {
			case CENTER -> startMargin + Math.max(0, available - size) / 2;
			case END -> startMargin + Math.max(0, available - size);
			case AUTO, START, STRETCH -> startMargin;
		};
	}

	private static int anchoredX(final Anchor anchor, final int available, final int width) {
		return switch (anchor) {
			case TOP, CENTER, BOTTOM -> Math.max(0, available - width) / 2;
			case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> Math.max(0, available - width);
			default -> 0;
		};
	}

	private static int anchoredY(final Anchor anchor, final int available, final int height) {
		return switch (anchor) {
			case LEFT, CENTER, RIGHT -> Math.max(0, available - height) / 2;
			case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> Math.max(0, available - height);
			default -> 0;
		};
	}

	private static Direction scrollDirection(final LayoutItem item) {
		return item.layout().direction().orElse(Direction.VERTICAL);
	}

	private static int specValue(final SizeSpec spec, final int fallback) {
		double value = spec.value().orElse((double)fallback);
		if (!Double.isFinite(value)) {
			return Math.max(0, fallback);
		}
		return boundedRound(Math.max(0.0, value));
	}

	private static int percent(final int available, final double value) {
		if (!Double.isFinite(value)) {
			return 0;
		}
		double percent = Math.clamp(value, 0.0, 100.0);
		return boundedRound(Math.max(0, available) * percent / 100.0);
	}

	private static double safeWeight(final double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 1.0;
	}

	private static int safeIntrinsic(final int value) {
		return Math.max(0, value);
	}

	private static int nonNegative(final int value) {
		return Math.max(0, value);
	}

	private static int add(final int left, final int right) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, (long)left + right));
	}

	private static int subtract(final int left, final int right) {
		return Math.max(0, left - Math.max(0, right));
	}

	private static int extent(final int minimum, final int maximum) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, (long)maximum - minimum));
	}

	private static int multiply(final int left, final int right) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, (long)left * right));
	}

	private static int boundedRound(final double value) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return 0;
		}
		return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.round(value);
	}

	private static int boundedFloor(final double value) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return 0;
		}
		return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.floor(value);
	}

	private static int safeAbsolute(final int value) {
		return value == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(value);
	}

	private static int coordinateAdd(final int coordinate, final int offset) {
		long result = (long)coordinate + offset;
		return (int)Math.clamp(result, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	private static int sum(final int[] values) {
		int result = 0;
		for (int value : values) {
			result = add(result, value);
		}
		return result;
	}

	private static int[] offsets(final int[] sizes, final int gap) {
		int[] offsets = new int[sizes.length];
		int current = 0;
		for (int index = 0; index < sizes.length; index++) {
			offsets[index] = current;
			current = add(current, sizes[index]);
			if (index + 1 < sizes.length) {
				current = add(current, gap);
			}
		}
		return offsets;
	}

	private static int spanSize(
		final int[] sizes,
		final int start,
		final int span,
		final int gap
	) {
		int result = multiply(gap, Math.max(0, span - 1));
		for (int index = start; index < start + span; index++) {
			result = add(result, sizes[index]);
		}
		return result;
	}

	private static void distribute(
		final int amount,
		final List<Integer> indices,
		final int[] targets
	) {
		int previous = 0;
		for (int position = 0; position < indices.size(); position++) {
			int target = (int)((long)amount * (position + 1) / indices.size());
			targets[indices.get(position)] = add(targets[indices.get(position)], target - previous);
			previous = target;
		}
	}

	private static void distributeWeighted(
		final int amount,
		final List<WeightedTrack> tracks,
		final int[] targets
	) {
		double totalWeight = tracks.stream().mapToDouble(WeightedTrack::weight).sum();
		if (amount <= 0 || totalWeight <= 0.0) {
			return;
		}
		double accumulated = 0.0;
		int previous = 0;
		for (WeightedTrack track : tracks) {
			accumulated += track.weight();
			int target = accumulated >= totalWeight
				? amount
				: boundedFloor(amount * accumulated / totalWeight);
			targets[track.index()] = add(targets[track.index()], target - previous);
			previous = target;
		}
	}

	private static void validateItem(final LayoutItem item, final Diagnostics diagnostics) {
		if (item.intrinsicWidth() < 0 || item.intrinsicHeight() < 0) {
			diagnostics.add(
				DiagnosticCode.NEGATIVE_INTRINSIC_SIZE,
				item,
				"negative intrinsic size was clamped to zero"
			);
		}
		LayoutDefinition layout = item.layout();
		if (
			layout.minimumWidth().orElse(0) > layout.maximumWidth().orElse(Integer.MAX_VALUE)
				|| layout.minimumHeight().orElse(0) > layout.maximumHeight().orElse(Integer.MAX_VALUE)
		) {
			diagnostics.add(
				DiagnosticCode.INVALID_CONSTRAINT,
				item,
				"minimum size exceeds maximum size"
			);
		}
		Insets margin = layout.margin();
		Insets padding = layout.padding();
		if (
			margin.top() < 0
				|| margin.right() < 0
				|| margin.bottom() < 0
				|| margin.left() < 0
				|| padding.top() < 0
				|| padding.right() < 0
				|| padding.bottom() < 0
				|| padding.left() < 0
				|| layout.gap().orElse(0) < 0
				|| layout.lineGap().orElse(0) < 0
				|| layout.columnGap().orElse(0) < 0
				|| layout.rowGap().orElse(0) < 0
		) {
			diagnostics.add(
				DiagnosticCode.NEGATIVE_LAYOUT_VALUE,
				item,
				"negative spacing was clamped to zero"
			);
		}
	}

	private static Edges edges(final Insets insets) {
		return new Edges(
			nonNegative(insets.top()),
			nonNegative(insets.right()),
			nonNegative(insets.bottom()),
			nonNegative(insets.left())
		);
	}

	public record LayoutItem(
		String key,
		NodeType type,
		LayoutDefinition layout,
		int intrinsicWidth,
		int intrinsicHeight,
		List<LayoutItem> children,
		String pointer,
		int scrollOffset
	) {
		public LayoutItem {
			key = Objects.requireNonNull(key, "key");
			type = Objects.requireNonNull(type, "type");
			layout = layout == null ? LayoutDefinition.empty() : layout;
			children = List.copyOf(children);
			pointer = pointer == null ? "" : pointer;
		}
	}

	public record LayoutResult(
		PlacedNode root,
		List<Diagnostic> diagnostics,
		int totalDiagnostics
	) {
		public LayoutResult {
			root = Objects.requireNonNull(root, "root");
			diagnostics = List.copyOf(diagnostics);
			if (totalDiagnostics < diagnostics.size()) {
				throw new IllegalArgumentException("totalDiagnostics cannot be smaller than retained details");
			}
		}
	}

	public record PlacedNode(
		String key,
		NodeType type,
		String pointer,
		Rect bounds,
		Rect contentBounds,
		Rect clip,
		int contentWidth,
		int contentHeight,
		int scrollOffset,
		List<PlacedNode> children
	) {
		public PlacedNode {
			children = List.copyOf(children);
		}
	}

	public record Rect(int x, int y, int width, int height) {
		public Rect {
			width = Math.max(0, width);
			height = Math.max(0, height);
		}

		public int right() {
			return coordinateAdd(this.x, this.width);
		}

		public int bottom() {
			return coordinateAdd(this.y, this.height);
		}

		public Rect inset(final Insets insets) {
			return this.inset(edges(insets));
		}

		private Rect inset(final Edges insets) {
			return new Rect(
				coordinateAdd(this.x, insets.left()),
				coordinateAdd(this.y, insets.top()),
				subtract(this.width, insets.horizontal()),
				subtract(this.height, insets.vertical())
			);
		}

		public Rect intersection(final Rect other) {
			int left = Math.max(this.x, other.x);
			int top = Math.max(this.y, other.y);
			int right = Math.min(this.right(), other.right());
			int bottom = Math.min(this.bottom(), other.bottom());
			return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
		}

	}

	public record Diagnostic(
		DiagnosticCode code,
		String key,
		String pointer,
		String message
	) {
	}

	public enum DiagnosticCode {
		NEGATIVE_VIEWPORT,
		NEGATIVE_INTRINSIC_SIZE,
		NEGATIVE_LAYOUT_VALUE,
		INVALID_CONSTRAINT,
		INVALID_CHILD_COUNT,
		SCROLL_CLAMPED,
		OVERFLOW
	}

	private enum Axis {
		HORIZONTAL,
		VERTICAL;

		private int main(final Rect rect) {
			return this == HORIZONTAL ? rect.width() : rect.height();
		}

		private int cross(final Rect rect) {
			return this == HORIZONTAL ? rect.height() : rect.width();
		}

		private int main(final Measure measure) {
			return this == HORIZONTAL ? measure.width() : measure.height();
		}

		private int cross(final Measure measure) {
			return this == HORIZONTAL ? measure.height() : measure.width();
		}

		private int main(final Edges edges) {
			return this == HORIZONTAL ? edges.horizontal() : edges.vertical();
		}

		private int cross(final Edges edges) {
			return this == HORIZONTAL ? edges.vertical() : edges.horizontal();
		}

		private int mainStart(final Edges edges) {
			return this == HORIZONTAL ? edges.left() : edges.top();
		}

		private int crossStart(final Edges edges) {
			return this == HORIZONTAL ? edges.top() : edges.left();
		}

		private int crossEnd(final Edges edges) {
			return this == HORIZONTAL ? edges.bottom() : edges.right();
		}

		private Measure measure(final int main, final int cross) {
			return this == HORIZONTAL ? new Measure(main, cross) : new Measure(cross, main);
		}

		private Rect rect(
			final Rect area,
			final int mainPosition,
			final int crossPosition,
			final int mainSize,
			final int crossSize
		) {
			return this == HORIZONTAL
				? new Rect(
					coordinateAdd(area.x(), mainPosition),
					coordinateAdd(area.y(), crossPosition),
					mainSize,
					crossSize
				)
				: new Rect(
					coordinateAdd(area.x(), crossPosition),
					coordinateAdd(area.y(), mainPosition),
					crossSize,
					mainSize
				);
		}

		private Placement placement(
			final int main,
			final int cross,
			final List<PlacedNode> children
		) {
			return this == HORIZONTAL
				? new Placement(main, cross, 0, children)
				: new Placement(cross, main, 0, children);
		}

		private Axis other() {
			return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
		}
	}

	private record Measure(int width, int height) {
	}

	private record Placement(
		int contentWidth,
		int contentHeight,
		int scrollOffset,
		List<PlacedNode> children
	) {
	}

	private record Edges(int top, int right, int bottom, int left) {
		private int horizontal() {
			return add(this.left, this.right);
		}

		private int vertical() {
			return add(this.top, this.bottom);
		}
	}

	private record LinearChild(
		LayoutItem item,
		Measure natural,
		Edges margin,
		int mainSize,
		boolean flexible,
		double weight
	) {
		private LinearChild withNatural(final Measure value) {
			return new LinearChild(
				this.item,
				value,
				this.margin,
				this.mainSize,
				this.flexible,
				this.weight
			);
		}

		private LinearChild withMainSize(final int value) {
			return new LinearChild(
				this.item,
				this.natural,
				this.margin,
				value,
				this.flexible,
				this.weight
			);
		}
	}

	private record GridEntry(
		LayoutItem item,
		Measure natural,
		Edges margin,
		int column,
		int row,
		int span
	) {
		private GridEntry withNatural(final Measure value) {
			return new GridEntry(this.item, value, this.margin, this.column, this.row, this.span);
		}
	}

	private record GridPlan(
		List<GridEntry> entries,
		int[] columnSizes,
		int[] rowSizes,
		int columnGap,
		int rowGap,
		int contentWidth,
		int contentHeight
	) {
	}

	private record WeightedTrack(int index, double weight) {
	}

	private record FlowChild(
		LayoutItem item,
		Measure natural,
		Edges margin,
		int mainSize
	) {
	}

	private record FlowLine(
		List<FlowChild> children,
		int mainSize,
		int crossSize
	) {
	}

	private static final class Diagnostics {
		private final List<Diagnostic> details = new ArrayList<>();
		private int total;

		private void add(
			final DiagnosticCode code,
			final LayoutItem item,
			final String message
		) {
			this.total++;
			if (this.details.size() < MAX_DIAGNOSTIC_DETAILS) {
				this.details.add(new Diagnostic(code, item.key(), item.pointer(), message));
			}
		}

		private List<Diagnostic> details() {
			return List.copyOf(this.details);
		}

		private int total() {
			return this.total;
		}
	}
}
