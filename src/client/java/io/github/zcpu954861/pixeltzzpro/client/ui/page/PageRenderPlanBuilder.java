package io.github.zcpu954861.pixeltzzpro.client.ui.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.ActivePage;
import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Direction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.DividerContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FontToken;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ImageContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Insets;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PlayerHeadContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ProgressContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveOverride;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeMode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeSpec;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SpacerContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FieldSchema;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.LayoutItem;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.LayoutResult;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime.Diagnostic;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ChoiceCardGrid;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.RepeatVirtualization;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.RepeatVirtualization.Window;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime.Evaluation;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TextLineWrapper;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiStyleRuntime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Shared production adapter from a validated page definition to one measured render plan.
 */
public final class PageRenderPlanBuilder {
	private static final int MAX_RUNTIME_DIAGNOSTICS = 50;
	private static final int REPEAT_OVERSCAN = 2;
	private static final Identifier DEFAULT_FONT = Identifier.withDefaultNamespace("default");

	private final Font font;
	private final ActivePage active;
	private final BindingRuntime bindings;
	private final BindingContext rootContext;
	private final ResponsiveTier tier;
	private final int viewportWidth;
	private final int viewportHeight;
	private final Map<String, Integer> scrollOffsets;
	private final boolean forceDefaultFont;
	private final Map<String, RenderNode> nodes = new LinkedHashMap<>();
	private final Map<String, Identifier> resolvedFonts = new LinkedHashMap<>();
	private final List<Diagnostic> bindingDiagnostics = new ArrayList<>();
	private int totalBindingDiagnostics;
	private int visibleRepeatItems;

	private PageRenderPlanBuilder(
		final Font font,
		final ActivePage active,
		final BindingContext rootContext,
		final ResponsiveTier tier,
		final int viewportWidth,
		final int viewportHeight,
		final Map<String, Integer> scrollOffsets,
		final boolean forceDefaultFont
	) {
		this.font = font;
		this.active = active;
		this.rootContext = rootContext;
		this.tier = tier;
		this.viewportWidth = Math.max(0, viewportWidth);
		this.viewportHeight = Math.max(0, viewportHeight);
		this.scrollOffsets = Map.copyOf(scrollOffsets);
		this.forceDefaultFont = forceDefaultFont;
		this.bindings = new BindingRuntime(PageRenderPlanBuilder::translate);
	}

	public static RenderPlan build(
		final Font font,
		final ActivePage active,
		final BindingContext context,
		final ResponsiveTier forcedTier,
		final int viewportWidth,
		final int viewportHeight,
		final Map<String, Integer> scrollOffsets,
		final boolean forceDefaultFont
	) {
		ResponsiveTier tier = forcedTier == null
			? tier(active, viewportWidth)
			: forcedTier;
		PageRenderPlanBuilder builder = new PageRenderPlanBuilder(
			font,
			active,
			context,
			tier,
			viewportWidth,
			viewportHeight,
			scrollOffsets,
			forceDefaultFont
		);
		RenderNode root = builder.buildNode(active.page().root(), context, "root", null);
		if (root == null) {
			throw new IllegalStateException("validated page root became invisible at runtime");
		}
		LayoutResult layout = UiLayoutEngine.layout(root.layoutItem(), viewportWidth, viewportHeight);
		LayoutItem remeasured = builder.remeasureWrappedText(root.layoutItem(), contentWidths(layout.root()));
		if (remeasured != root.layoutItem()) {
			layout = UiLayoutEngine.layout(remeasured, viewportWidth, viewportHeight);
		}
		return new RenderPlan(
			root,
			layout,
			builder.nodes,
			builder.bindingDiagnostics,
			builder.totalBindingDiagnostics,
			builder.visibleRepeatItems,
			tier
		);
	}

	private RenderNode buildNode(
		final NodeDefinition definition,
		final BindingContext context,
		final String key,
		final VirtualWindow virtualWindow
	) {
		ResponsiveOverride responsive = definition.responsive().get(this.tier);
		if (responsive != null && responsive.visible().filter(value -> !value).isPresent()) {
			return null;
		}
		if (definition.visibleWhen().isPresent()) {
			Evaluation<Boolean> visible = this.bindings.evaluateCondition(
				definition.visibleWhen().orElseThrow(),
				context
			);
			capture(visible);
			if (!visible.value().orElse(false)) {
				return null;
			}
		}

		NodeType type = responsive == null
			? definition.type()
			: responsive.type().orElse(definition.type());
		LayoutDefinition layout = responsive == null || responsive.layout().isEmpty()
			? definition.layout()
			: mergeLayout(
				definition.layout(),
				responsive.layout().orElseThrow(),
				responsive.layoutFields()
			);
		if (type == NodeType.CARD && layout.padding().equals(Insets.zero())) {
			layout = withPadding(layout, new Insets(8, 9, 8, 9));
		}
		if (
			definition.content() instanceof TextContent content
				&& content.wrap()
				&& layout.width().isEmpty()
		) {
			layout = replaceWidth(
				layout,
				new SizeSpec(SizeMode.FILL, Optional.empty())
			);
		}

		Identifier fontId = resolveFont(definition);
		String text = "";
		int intrinsicWidth = 0;
		int intrinsicHeight = 0;
		List<RenderNode> children = new ArrayList<>();
		int scrollOffset = 0;

		switch (definition.content()) {
			case ChildrenContent content -> {
				int index = 0;
				for (NodeDefinition child : content.children()) {
					RenderNode rendered = buildNode(
						child,
						context,
						childKey(key, child, index++),
						virtualWindow
					);
					if (rendered != null) {
						children.add(rendered);
					}
				}
			}
			case SingleChildContent content -> {
				if (type == NodeType.SCROLL) {
					Direction direction = content.scrollDirection().orElse(Direction.VERTICAL);
					layout = withDirection(layout, direction);
					scrollOffset = Math.max(0, this.scrollOffsets.getOrDefault(key, 0));
					RenderNode child = buildNode(
						content.child(),
						context,
						childKey(key, content.child(), 0),
						new VirtualWindow(scrollOffset, this.viewportHeight, direction)
					);
					if (child != null) {
						children.add(child);
					}
				} else {
					RenderNode child = buildNode(
						content.child(),
						context,
						childKey(key, content.child(), 0),
						virtualWindow
					);
					if (child != null) {
						children.add(child);
					}
				}
			}
			case TextContent content -> {
				text = evaluateText(content.text(), context);
				Component styledText = styledText(text, fontId);
				int maximum = Math.max(16, this.viewportWidth - 36);
				intrinsicWidth = Math.min(maximum, this.font.width(styledText));
				intrinsicHeight = content.wrap()
					? wrappedTextHeight(text, fontId, maximum)
					: this.font.lineHeight;
				if (content.maximumLines().isPresent()) {
					intrinsicHeight = Math.min(
						intrinsicHeight,
						content.maximumLines().orElseThrow() * this.font.lineHeight
					);
				}
			}
			case ButtonContent content -> {
				text = evaluateText(content.label(), context);
				intrinsicWidth = Math.max(86, this.font.width(styledText(text, fontId)) + 30);
				intrinsicHeight = 24;
			}
			case DividerContent content -> {
				intrinsicWidth = content.direction() == Direction.HORIZONTAL ? this.viewportWidth : content.thickness();
				intrinsicHeight = content.direction() == Direction.HORIZONTAL ? content.thickness() : 24;
			}
			case SpacerContent ignored -> {
			}
			case ImageContent ignored -> {
				intrinsicWidth = 96;
				intrinsicHeight = 72;
			}
			case ProgressContent content -> {
				text = content.label().map(label -> evaluateText(label, context)).orElse("");
				intrinsicWidth = 220;
				intrinsicHeight = text.isEmpty() ? 18 : 28;
			}
			case PlayerHeadContent ignored -> {
				intrinsicWidth = 24;
				intrinsicHeight = 24;
			}
			case FieldInputContent input -> {
				FieldSchema field = this.active.fields().get(input.field());
				text = field == null ? input.field().toString() : field.name();
				intrinsicWidth = Math.min(360, Math.max(180, this.viewportWidth - 64));
				intrinsicHeight = field == null
					? 26
					: fieldHeaderHeight(input, field)
						+ fieldControlHeight(field, intrinsicWidth);
			}
			case RepeatContent repeat -> {
				layout = ensureRepeatLayout(layout);
				buildRepeat(definition, repeat, context, key, virtualWindow, type, layout, children);
			}
		}

		List<LayoutItem> childItems = children.stream().map(RenderNode::layoutItem).toList();
		LayoutItem item = new LayoutItem(
			key,
			type,
			layout,
			intrinsicWidth,
			intrinsicHeight,
			childItems,
			definition.pointer(),
			scrollOffset
		);
		RenderNode result = new RenderNode(
			key,
			definition,
			type,
			layout,
			context,
			text,
			styledText(text, fontId),
			fontId,
			List.copyOf(children),
			item,
			false
		);
		this.nodes.put(key, result);
		return result;
	}

	private void buildRepeat(
		final NodeDefinition definition,
		final RepeatContent repeat,
		final BindingContext context,
		final String key,
		final VirtualWindow window,
		final NodeType renderedType,
		final LayoutDefinition layout,
		final List<RenderNode> children
	) {
		Evaluation<JsonElement> evaluated = this.bindings.evaluateValue(repeat.items(), context);
		capture(evaluated);
		JsonArray values = evaluated.value()
			.filter(JsonElement::isJsonArray)
			.map(JsonElement::getAsJsonArray)
			.orElseGet(JsonArray::new);
		int boundedCount = Math.min(values.size(), repeat.maximumItems());
		Map<Integer, String> stableItemKeys = new LinkedHashMap<>();
		Set<String> usedItemKeys = new HashSet<>();
		for (int index = 0; index < boundedCount; index++) {
			JsonElement itemValue = values.get(index);
			if (!itemValue.isJsonObject()) {
				continue;
			}
			BindingContext itemContext = context.withItem(itemValue.getAsJsonObject());
			String rawKey = evaluateValueAsString(
				repeat.itemKey(),
				itemContext,
				Integer.toString(index)
			);
			String stableKey = stableRepeatKey(rawKey);
			if (!usedItemKeys.add(stableKey)) {
				captureRuntimeDiagnostic(
					"DUPLICATE_REPEAT_KEY",
					definition.pointer() + "/item_key",
					"repeat item key is not unique: " + rawKey
				);
				continue;
			}
			stableItemKeys.put(index, stableKey);
		}
		int columns = renderedType == NodeType.GRID
			? Math.max(1, layout.columns().size())
			: 1;
		int rowGap = renderedType == NodeType.GRID
			? layout.rowGap().orElse(0)
			: 0;
		int start = 0;
		int end = boundedCount;
		int leadingExtent = 0;
		int trailingExtent = 0;
		if (window != null && window.direction == Direction.VERTICAL && boundedCount > 0) {
			Window repeatWindow = RepeatVirtualization.verticalWindow(
				boundedCount,
				columns,
				repeat.estimatedItemHeight(),
				rowGap,
				window.offset,
				window.extent,
				REPEAT_OVERSCAN
			);
			start = repeatWindow.startItem();
			end = repeatWindow.endItem();
			leadingExtent = repeatWindow.leadingExtent();
			trailingExtent = repeatWindow.trailingExtent();
		}
		if (start > 0) {
			children.add(
				syntheticSpacer(
					key + "/virtual-leading",
					leadingExtent,
					definition.pointer() + "/template",
					columns
				)
			);
		}
		for (int index = start; index < end; index++) {
			JsonElement itemValue = values.get(index);
			String stableKey = stableItemKeys.get(index);
			if (!itemValue.isJsonObject() || stableKey == null) {
				children.add(
					syntheticSpacer(
						key + "/invalid-item-" + index,
						Math.max(1, repeat.estimatedItemHeight() - rowGap),
						definition.pointer() + "/template"
					)
				);
				continue;
			}
			BindingContext itemContext = context.withItem(itemValue.getAsJsonObject());
			RenderNode child = buildNode(
				repeat.template(),
				itemContext,
				key + "/item-" + stableKey,
				window
			);
			if (child != null) {
				children.add(child);
				this.visibleRepeatItems++;
			}
		}
		if (end < boundedCount) {
			children.add(
				syntheticSpacer(
					key + "/virtual-trailing",
					trailingExtent,
					definition.pointer() + "/template",
					columns
				)
			);
		}
	}

	private RenderNode syntheticSpacer(final String key, final int height, final String pointer) {
		return syntheticSpacer(key, height, pointer, 1);
	}

	private RenderNode syntheticSpacer(
		final String key,
		final int height,
		final String pointer,
		final int columnSpan
	) {
		LayoutDefinition layout = LayoutDefinition.empty();
		layout = replaceHeight(layout, new SizeSpec(SizeMode.FIXED, Optional.of((double) Math.max(0, height))));
		if (columnSpan > 1) {
			layout = replaceColumnSpan(layout, columnSpan);
		}
		LayoutItem item = new LayoutItem(key, NodeType.SPACER, layout, 0, height, List.of(), pointer, 0);
		RenderNode node = new RenderNode(
			key,
			null,
			NodeType.SPACER,
			layout,
			this.rootContext,
			"",
			styledText("", DEFAULT_FONT),
			DEFAULT_FONT,
			List.of(),
			item,
			true
		);
		this.nodes.put(key, node);
		return node;
	}

	private Identifier resolveFont(final NodeDefinition definition) {
		if (this.forceDefaultFont) {
			return DEFAULT_FONT;
		}
		Map<String, io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue> values =
			UiStyleRuntime.resolve(
				this.active.theme(),
				definition,
				this.tier,
				StyleState.NORMAL,
				false
			);
		String configured = UiStyleRuntime.stringValue(values, "font")
			.orElseGet(
				() -> this.active.theme().tokens().fonts().containsKey("body")
					? "@fonts.body"
					: DEFAULT_FONT.toString()
			);
		return this.resolvedFonts.computeIfAbsent(configured, this::resolveConfiguredFont);
	}

	private Identifier resolveConfiguredFont(final String configured) {
		Identifier resolved = configured.startsWith("@fonts.")
			? resolveFontToken(configured.substring("@fonts.".length()))
			: Identifier.tryParse(configured);
		return resolved != null && ClientResourcePreflight.fontAvailable(resolved)
			? resolved
			: DEFAULT_FONT;
	}

	private Identifier resolveFontToken(final String name) {
		FontToken token = this.active.theme().tokens().fonts().get(name);
		if (token == null) {
			return null;
		}
		String pointer = "/tokens/fonts/" + name + "/asset";
		AssetReference reference = this.active.theme().assets()
			.stream()
			.filter(asset -> asset.type() == AssetType.FONT)
			.filter(asset -> asset.pointer().equals(pointer))
			.findFirst()
			.orElse(null);
		if (reference == null) {
			return null;
		}
		String resolved = this.active.resources().resolved(reference);
		return resolved == null ? null : Identifier.tryParse(resolved);
	}

	private static Component styledText(final String text, final Identifier font) {
		return Component.literal(text)
			.withStyle(style -> style.withFont(new FontDescription.Resource(font)));
	}

	private String evaluateText(
		final io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextTemplate template,
		final BindingContext context
	) {
		Evaluation<String> value = this.bindings.evaluateText(template, context);
		capture(value);
		return value.value().orElse("");
	}

	private String evaluateValueAsString(
		final io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression expression,
		final BindingContext context,
		final String fallback
	) {
		Evaluation<JsonElement> value = this.bindings.evaluateValue(expression, context);
		capture(value);
		return value.value().map(PageRenderPlanBuilder::displayValue).orElse(fallback);
	}

	private void capture(final Evaluation<?> evaluation) {
		this.totalBindingDiagnostics += evaluation.totalDiagnosticCount();
		int remaining = MAX_RUNTIME_DIAGNOSTICS - this.bindingDiagnostics.size();
		if (remaining > 0) {
			this.bindingDiagnostics.addAll(
				evaluation.diagnostics().subList(0, Math.min(remaining, evaluation.diagnostics().size()))
			);
		}
	}

	private void captureRuntimeDiagnostic(
		final String code,
		final String source,
		final String message
	) {
		this.totalBindingDiagnostics++;
		if (this.bindingDiagnostics.size() < MAX_RUNTIME_DIAGNOSTICS) {
			this.bindingDiagnostics.add(new Diagnostic(code, source, message));
		}
	}

	private static Optional<String> translate(final String key, final List<JsonElement> arguments) {
		if (!Language.getInstance().has(key)) {
			return Optional.empty();
		}
		Object[] values = arguments.stream().map(PageRenderPlanBuilder::displayValue).toArray();
		return Optional.of(Component.translatable(key, values).getString());
	}

	private static String displayValue(final JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return "";
		}
		if (value.isJsonPrimitive()) {
			return value.getAsJsonPrimitive().getAsString();
		}
		return value.toString();
	}

	private static ResponsiveTier tier(final ActivePage active, final int width) {
		if (width <= active.page().breakpoints().compactMax()) {
			return ResponsiveTier.COMPACT;
		}
		return width >= active.page().breakpoints().wideMin()
			? ResponsiveTier.WIDE
			: ResponsiveTier.STANDARD;
	}

	private static String childKey(final String parent, final NodeDefinition child, final int index) {
		return parent + "/" + child.id().orElse("node-" + index);
	}

	private static String stableRepeatKey(final String value) {
		String sanitized = value.replaceAll("[^a-zA-Z0-9_.:-]", "_");
		String prefix = sanitized.isEmpty() ? "item" : sanitized;
		String suffix = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
			.toString()
			.replace("-", "")
			.substring(0, 12);
		int maximumPrefix = 80 - suffix.length() - 1;
		return (prefix.length() <= maximumPrefix ? prefix : prefix.substring(0, maximumPrefix))
			+ "-"
			+ suffix;
	}

	private static LayoutDefinition mergeLayout(
		final LayoutDefinition base,
		final LayoutDefinition override,
		final Set<String> fields
	) {
		return new LayoutDefinition(
			fields.contains("width") ? override.width() : base.width(),
			fields.contains("height") ? override.height() : base.height(),
			fields.contains("min_width") ? override.minimumWidth() : base.minimumWidth(),
			fields.contains("max_width") ? override.maximumWidth() : base.maximumWidth(),
			fields.contains("min_height") ? override.minimumHeight() : base.minimumHeight(),
			fields.contains("max_height") ? override.maximumHeight() : base.maximumHeight(),
			fields.contains("margin") ? override.margin() : base.margin(),
			fields.contains("padding") ? override.padding() : base.padding(),
			fields.contains("align_self") ? override.alignSelf() : base.alignSelf(),
			fields.contains("gap") ? override.gap() : base.gap(),
			fields.contains("line_gap") ? override.lineGap() : base.lineGap(),
			fields.contains("column_gap") ? override.columnGap() : base.columnGap(),
			fields.contains("row_gap") ? override.rowGap() : base.rowGap(),
			fields.contains("main_align") ? override.mainAlign() : base.mainAlign(),
			fields.contains("cross_align") ? override.crossAlign() : base.crossAlign(),
			fields.contains("direction") ? override.direction() : base.direction(),
			fields.contains("columns") ? override.columns() : base.columns(),
			fields.contains("column_span") ? override.columnSpan() : base.columnSpan(),
			fields.contains("anchor") ? override.anchor() : base.anchor(),
			fields.contains("offset_x") ? override.offsetX() : base.offsetX(),
			fields.contains("offset_y") ? override.offsetY() : base.offsetY()
		);
	}

	private static LayoutDefinition withPadding(final LayoutDefinition value, final Insets padding) {
		return new LayoutDefinition(
			value.width(),
			value.height(),
			value.minimumWidth(),
			value.maximumWidth(),
			value.minimumHeight(),
			value.maximumHeight(),
			value.margin(),
			padding,
			value.alignSelf(),
			value.gap(),
			value.lineGap(),
			value.columnGap(),
			value.rowGap(),
			value.mainAlign(),
			value.crossAlign(),
			value.direction(),
			value.columns(),
			value.columnSpan(),
			value.anchor(),
			value.offsetX(),
			value.offsetY()
		);
	}

	private static LayoutDefinition withDirection(
		final LayoutDefinition value,
		final Direction direction
	) {
		return new LayoutDefinition(
			value.width(),
			value.height(),
			value.minimumWidth(),
			value.maximumWidth(),
			value.minimumHeight(),
			value.maximumHeight(),
			value.margin(),
			value.padding(),
			value.alignSelf(),
			value.gap(),
			value.lineGap(),
			value.columnGap(),
			value.rowGap(),
			value.mainAlign(),
			value.crossAlign(),
			Optional.of(direction),
			value.columns(),
			value.columnSpan(),
			value.anchor(),
			value.offsetX(),
			value.offsetY()
		);
	}

	private static LayoutDefinition replaceHeight(
		final LayoutDefinition value,
		final SizeSpec height
	) {
		return new LayoutDefinition(
			value.width(),
			Optional.of(height),
			value.minimumWidth(),
			value.maximumWidth(),
			value.minimumHeight(),
			value.maximumHeight(),
			value.margin(),
			value.padding(),
			value.alignSelf(),
			value.gap(),
			value.lineGap(),
			value.columnGap(),
			value.rowGap(),
			value.mainAlign(),
			value.crossAlign(),
			value.direction(),
			value.columns(),
			value.columnSpan(),
			value.anchor(),
			value.offsetX(),
			value.offsetY()
		);
	}

	private static LayoutDefinition replaceWidth(
		final LayoutDefinition value,
		final SizeSpec width
	) {
		return new LayoutDefinition(
			Optional.of(width),
			value.height(),
			value.minimumWidth(),
			value.maximumWidth(),
			value.minimumHeight(),
			value.maximumHeight(),
			value.margin(),
			value.padding(),
			value.alignSelf(),
			value.gap(),
			value.lineGap(),
			value.columnGap(),
			value.rowGap(),
			value.mainAlign(),
			value.crossAlign(),
			value.direction(),
			value.columns(),
			value.columnSpan(),
			value.anchor(),
			value.offsetX(),
			value.offsetY()
		);
	}

	private static LayoutDefinition replaceColumnSpan(
		final LayoutDefinition value,
		final int columnSpan
	) {
		return new LayoutDefinition(
			value.width(),
			value.height(),
			value.minimumWidth(),
			value.maximumWidth(),
			value.minimumHeight(),
			value.maximumHeight(),
			value.margin(),
			value.padding(),
			value.alignSelf(),
			value.gap(),
			value.lineGap(),
			value.columnGap(),
			value.rowGap(),
			value.mainAlign(),
			value.crossAlign(),
			value.direction(),
			value.columns(),
			Optional.of(Math.max(1, columnSpan)),
			value.anchor(),
			value.offsetX(),
			value.offsetY()
		);
	}

	private static Map<String, Integer> contentWidths(final UiLayoutEngine.PlacedNode root) {
		Map<String, Integer> result = new LinkedHashMap<>();
		collectContentWidths(root, result);
		return result;
	}

	private static void collectContentWidths(
		final UiLayoutEngine.PlacedNode node,
		final Map<String, Integer> target
	) {
		target.put(node.key(), node.contentBounds().width());
		node.children().forEach(child -> collectContentWidths(child, target));
	}

	private LayoutItem remeasureWrappedText(
		final LayoutItem item,
		final Map<String, Integer> contentWidths
	) {
		boolean changed = false;
		List<LayoutItem> children = new ArrayList<>(item.children().size());
		for (LayoutItem child : item.children()) {
			LayoutItem remeasured = remeasureWrappedText(child, contentWidths);
			children.add(remeasured);
			changed |= remeasured != child;
		}

		int intrinsicHeight = item.intrinsicHeight();
		RenderNode node = this.nodes.get(item.key());
		if (
			node != null
				&& node.definition() != null
				&& node.definition().content() instanceof TextContent content
				&& content.wrap()
		) {
			int width = Math.max(1, contentWidths.getOrDefault(item.key(), this.viewportWidth));
			int measuredHeight = wrappedTextHeight(node.text(), node.fontId(), width);
			if (content.maximumLines().isPresent()) {
				measuredHeight = Math.min(
					measuredHeight,
					content.maximumLines().orElseThrow() * this.font.lineHeight
				);
			}
			if (measuredHeight != intrinsicHeight) {
				intrinsicHeight = measuredHeight;
				changed = true;
			}
		} else if (
			node != null
				&& node.definition() != null
				&& node.definition().content() instanceof FieldInputContent input
		) {
			FieldSchema field = this.active.fields().get(input.field());
			if (field != null) {
				int width = Math.max(1, contentWidths.getOrDefault(item.key(), this.viewportWidth));
				int measuredHeight = fieldHeaderHeight(input, field)
					+ fieldControlHeight(field, width);
				if (measuredHeight != intrinsicHeight) {
					intrinsicHeight = measuredHeight;
					changed = true;
				}
			}
		}

		return changed
			? new LayoutItem(
				item.key(),
				item.type(),
				item.layout(),
				item.intrinsicWidth(),
				intrinsicHeight,
				children,
				item.pointer(),
				item.scrollOffset()
			)
			: item;
	}

	public static int fieldChoiceColumns(final FieldSchema field, final int width) {
		return ChoiceCardGrid.columns(field.type(), field.options().size(), width);
	}

	public static int fieldControlHeight(final FieldSchema field, final int width) {
		return ChoiceCardGrid.controlHeight(field.type(), field.options().size(), width);
	}

	private static int fieldHeaderHeight(
		final FieldInputContent input,
		final FieldSchema field
	) {
		return (input.showLabel() ? 14 : 0)
			+ (
				input.showDescription() && !field.description().isEmpty()
					? 20
					: 0
			);
	}

	private int wrappedTextHeight(
		final String text,
		final Identifier fontId,
		final int width
	) {
		int lines = TextLineWrapper.wrap(
			text,
			width,
			value -> this.font.width(styledText(value, fontId))
		).size();
		return Math.max(this.font.lineHeight, lines * this.font.lineHeight);
	}

	private static LayoutDefinition ensureRepeatLayout(final LayoutDefinition value) {
		LayoutDefinition result = value;
		if (result.width().isEmpty()) {
			result = new LayoutDefinition(
				Optional.of(new SizeSpec(SizeMode.FILL, Optional.empty())),
				result.height(),
				result.minimumWidth(),
				result.maximumWidth(),
				result.minimumHeight(),
				result.maximumHeight(),
				result.margin(),
				result.padding(),
				result.alignSelf(),
				result.gap(),
				result.lineGap(),
				result.columnGap(),
				result.rowGap(),
				result.mainAlign(),
				result.crossAlign(),
				result.direction(),
				result.columns(),
				result.columnSpan(),
				result.anchor(),
				result.offsetX(),
				result.offsetY()
			);
		}
		return result;
	}

	public record RenderNode(
		String key,
		NodeDefinition definition,
		NodeType type,
		LayoutDefinition layout,
		BindingContext context,
		String text,
		Component styledText,
		Identifier fontId,
		List<RenderNode> children,
		LayoutItem layoutItem,
		boolean synthetic
	) {
		public RenderNode {
			children = List.copyOf(children);
		}
	}

	public record RenderPlan(
		RenderNode root,
		LayoutResult layout,
		Map<String, RenderNode> nodes,
		List<Diagnostic> bindingDiagnostics,
		int totalBindingDiagnostics,
		int visibleRepeatItems,
		ResponsiveTier tier
	) {
		public RenderPlan {
			nodes = Map.copyOf(nodes);
			bindingDiagnostics = List.copyOf(bindingDiagnostics);
		}
	}

	private record VirtualWindow(int offset, int extent, Direction direction) {
	}
}
