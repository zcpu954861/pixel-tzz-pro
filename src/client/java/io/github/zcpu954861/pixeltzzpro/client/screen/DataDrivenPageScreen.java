package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.DANGER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_RAISED;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.WARNING;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.ActivePage;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.FlowSubmission;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.PageStatus;
import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight.Diagnostic;
import io.github.zcpu954861.pixeltzzpro.client.ui.page.PageRenderPlanBuilder;
import io.github.zcpu954861.pixeltzzpro.client.ui.page.PageRenderPlanBuilder.RenderNode;
import io.github.zcpu954861.pixeltzzpro.client.ui.page.PageRenderPlanBuilder.RenderPlan;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.AnimatedEditBox;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Appearance;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.content.BuiltInUiResources;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Direction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.DividerContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.EventBinding;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ImageContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ImageFit;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PlayerHeadContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ProgressContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SoundCue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextOverflow;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiAction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiEvent;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FieldSchema;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.PagePurpose;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.PlacedNode;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TextLineWrapper;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime.Transform;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiStyleRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * Production renderer and local-only administrator preview shell for one validated page bundle.
 *
 * <p>The screen deliberately consumes {@link PageRenderPlanBuilder} output instead of measuring or
 * expanding the component tree itself. Page actions stop at a visible request envelope in 2B.
 */
public final class DataDrivenPageScreen extends Screen {
	private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Identifier BUILT_IN_WARNING = Identifier.withDefaultNamespace(
		"textures/gui/sprites/dialog/warning_button.png"
	);
	private static final int OUTER_MARGIN = 8;
	private static final int TOOL_GAP = 4;
	private static final int TOOL_HEIGHT = 22;
	private static final int VIEWPORT_HEADER_HEIGHT = 26;
	private static final int FOOTER_HEIGHT = 34;
	private static final int COMPACT_VIEWPORT_WIDTH = 420;
	private static final int STANDARD_VIEWPORT_WIDTH = 620;
	private static final int WIDE_VIEWPORT_WIDTH = 800;
	private static final int COMPACT_VIEWPORT_HEIGHT = 300;
	private static final int STANDARD_VIEWPORT_HEIGHT = 360;
	private static final int WIDE_VIEWPORT_HEIGHT = 400;
	private static final int SOUND_LIMIT_MILLIS = 120;
	private static final int SCROLL_STEP = 24;
	private static final int MAX_DEBUG_DETAILS = 4;
	private static final UUID FALLBACK_UUID = new UUID(0L, 0L);

	private final Screen parent;
	private final PageScreenSession session;
	private final BindingRuntime bindings = new BindingRuntime(DataDrivenPageScreen::translate);
	private final Map<String, Integer> scrollOffsets = new LinkedHashMap<>();
	private final Map<String, WidgetBinding> pageWidgets = new LinkedHashMap<>();
	private final Map<String, PlacedNode> placedNodes = new LinkedHashMap<>();
	private final Map<String, JsonElement> localUiValues = new LinkedHashMap<>();
	private final Map<StyleCacheKey, Map<String, StyleValue>> resolvedStyles = new HashMap<>();
	private final Map<BindingContext, BindingContext> bindingContextCache = new IdentityHashMap<>();
	private final Map<String, EnumMap<UiEvent, MotionPlayback>> motionPlaybacks = new LinkedHashMap<>();
	private final Set<String> shownNodeKeys = new HashSet<>();
	private final Set<String> hoveredNodeKeys = new HashSet<>();
	private final Set<String> focusedNodeKeys = new HashSet<>();
	private final Map<String, Long> lastSoundTimes = new HashMap<>();
	private final List<PendingSound> pendingSounds = new ArrayList<>();
	private final Map<Identifier, ImageSize> imageSizes = new HashMap<>();

	private ActivePage active;
	private RenderPlan plan;
	private MotionLayout currentMotionLayout;
	private Map<String, String> motionColorTokens = Map.of();
	private String renderFailure = "";
	private String lastRequestEnvelope = "";
	private UiEvent lastRequestOutcome = UiEvent.SUCCESS;
	private UUID motionInstanceId;
	private long requestSequence;
	private long motionSequence;
	private long observedRevision = Long.MIN_VALUE;
	private boolean pendingPlanRefresh;
	private boolean closing;
	private boolean serverReleased;
	private boolean safetyDiagnosticsExpanded;
	private long pendingActionSequence = -1L;
	private String pendingActionNodeKey = "";
	private int toolbarHeight;
	private int viewportX;
	private int viewportY;
	private int viewportWidth;
	private int viewportHeight;
	private int pageContentWidth;
	private int pageContentHeight;
	private double pageScale = 1.0;
	private String draggingScrollKey;
	private double dragStartMouse;
	private int dragStartOffset;

	public DataDrivenPageScreen(final Screen parent, final PageScreenSession session) {
		super(initialTitle());
		this.parent = parent;
		this.session = session;
	}

	public boolean isLivePageScreen() {
		return !this.session.previewChrome();
	}

	public Screen returnScreen() {
		return this.parent;
	}

	public void releaseFromServer() {
		if (!isLivePageScreen() || this.serverReleased) {
			return;
		}
		this.serverReleased = true;
		onClose();
	}

	private static Component initialTitle() {
		return ClientPageState.activePage()
			.map(page -> Component.literal(page.page().title().plainText()))
			.orElseGet(() -> Component.literal("数据驱动页面"));
	}

	@Override
	public Component getNarrationMessage() {
		if (this.plan == null || this.active == null) {
			String message = ClientPageState.pageMessage();
			return message.isEmpty()
				? this.title
				: Component.literal(this.title.getString() + "。 " + message);
		}
		List<String> stableText = new ArrayList<>();
		collectNarration(this.plan.root(), stableText);
		StringBuilder narration = new StringBuilder(this.active.page().title().plainText());
		for (String text : stableText) {
			if (text.isBlank() || narration.length() + text.length() > 1_024) {
				continue;
			}
			narration.append("。 ").append(text);
		}
		return Component.literal(narration.toString());
	}

	private void collectNarration(final RenderNode node, final List<String> target) {
		if (target.size() >= 16 || node.definition() == null) {
			return;
		}
		switch (node.definition().content()) {
			case TextContent ignored -> target.add(node.text());
			case ProgressContent ignored -> {
				if (!node.text().isEmpty()) {
					target.add(node.text());
				}
			}
			case ImageContent image -> image.alternativeText()
				.flatMap(
					text -> this.bindings.evaluateText(
						text,
						contextWithLocalUi(node.context())
					).value()
				)
				.ifPresent(target::add);
			case PlayerHeadContent head -> evaluatePrimitive(head.name(), node.context())
				.map(name -> name + "，玩家头像")
				.ifPresent(target::add);
			default -> {
				// Interactive widgets narrate themselves; layout-only nodes have no stable prose.
			}
		}
		for (RenderNode child : node.children()) {
			if (target.size() >= 16) {
				break;
			}
			collectNarration(child, target);
		}
	}

	@Override
	protected void init() {
		rebuild();
	}

	private void rebuild() {
		String focusedKey = this.pageWidgets.entrySet()
			.stream()
			.filter(entry -> entry.getValue().widget().isFocused())
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
		this.clearWidgets();
		this.pageWidgets.clear();
		this.placedNodes.clear();
		this.resolvedStyles.clear();
		this.bindingContextCache.clear();
		this.renderFailure = "";
		this.active = ClientPageState.activePage().orElse(null);
		resetMotionStateIfInstanceChanged();
		this.observedRevision = ClientPageState.revision();

		if (isSafetyPage()) {
			this.plan = null;
			layoutSafetyWidgets();
			return;
		}

		if (this.session.previewChrome()) {
			layoutToolbar();
			calculateViewport();
		} else {
			layoutLiveViewport();
		}
		try {
			this.plan = PageRenderPlanBuilder.build(
				this.font,
				this.active,
				contextWithLocalUi(this.session.context(this.active)),
				this.session.responsiveTier(),
				this.viewportWidth,
				this.viewportHeight,
				this.scrollOffsets,
				this.session.defaultFont()
			);
			indexPlaced(this.plan.layout().root());
			updatePageFit();
			initializeShowMotions();
			buildPageWidgets(this.plan.layout().root());
			if (this.active.purpose() == PagePurpose.FORCED_FLOW) {
				ClientPageState.reportRenderReady(this.active.instanceId());
			}
		} catch (RuntimeException error) {
			this.plan = null;
			PixelTzzPro.LOGGER.error(
				"Failed to build Pixel TZZ page render plan for {}",
				this.active == null ? "<missing>" : this.active.page().id(),
				error
			);
			this.renderFailure = safeMessage(error);
			if (this.active != null && this.active.purpose() == PagePurpose.FORCED_FLOW) {
				ClientPageState.reportRenderFailure(
					this.active.instanceId(),
					this.renderFailure
				);
			}
			layoutSafetyWidgets();
			return;
		}

		if (this.session.previewChrome()) {
			int backWidth = Math.min(104, Math.max(72, this.width - 24));
			ConsoleButton back = new ConsoleButton(
				this.width - backWidth - 12,
				this.height - 30,
				backWidth,
				22,
				Component.literal("返回页面目录"),
				button -> onClose(),
				Variant.NAVIGATION
			);
			this.addRenderableWidget(back);
		}

		if (focusedKey != null) {
			WidgetBinding restored = this.pageWidgets.get(focusedKey);
			if (restored != null) {
				this.setInitialFocus(restored.widget());
			}
		}
	}

	private void resetMotionStateIfInstanceChanged() {
		UUID currentInstance = this.active == null ? null : this.active.instanceId();
		if (Objects.equals(this.motionInstanceId, currentInstance)) {
			return;
		}
		this.motionInstanceId = currentInstance;
		this.motionPlaybacks.clear();
		this.shownNodeKeys.clear();
		this.hoveredNodeKeys.clear();
		this.focusedNodeKeys.clear();
		this.pendingSounds.clear();
		this.lastSoundTimes.clear();
		this.motionSequence = 0L;
		this.closing = false;
		this.serverReleased = false;
		Map<String, String> colors = new HashMap<>(
			BuiltInUiResources.defaultTheme().tokens().colors()
		);
		if (this.active != null) {
			colors.putAll(this.active.theme().tokens().colors());
		}
		this.motionColorTokens = Map.copyOf(colors);
	}

	private void initializeShowMotions() {
		long now = Util.getMillis();
		for (RenderNode node : this.plan.nodes().values()) {
			if (node.definition() != null && this.shownNodeKeys.add(node.key())) {
				triggerMotion(node, UiEvent.SHOW, now);
				playEventSound(node, UiEvent.SHOW, node.key());
			}
		}
	}

	private boolean isSafetyPage() {
		PageStatus status = ClientPageState.pageStatus();
		return this.active == null
			|| status != PageStatus.READY
			|| !this.renderFailure.isEmpty();
	}

	private boolean forcedPage() {
		return !this.serverReleased
			&& (
				(this.active != null && this.active.purpose() == PagePurpose.FORCED_FLOW)
					|| ClientPageState.forcedPageActive()
			);
	}

	private void layoutToolbar() {
		List<ToolSpec> tools = toolSpecs();
		int x = 14;
		int y = 12;
		int right = Math.max(24, this.width - 14);
		for (ToolSpec spec : tools) {
			if (x > 14 && x + spec.width() > right) {
				x = 14;
				y += TOOL_HEIGHT + TOOL_GAP;
			}
			ConsoleButton button = new ConsoleButton(
				x,
				y,
				Math.min(spec.width(), Math.max(44, right - x)),
				TOOL_HEIGHT,
				Component.literal(spec.label()),
				pressed -> {
					spec.action().run();
					rebuild();
				},
				Variant.NORMAL
			);
			button.setTooltip(Tooltip.create(Component.literal(spec.tooltip())));
			this.addRenderableWidget(button);
			x += spec.width() + TOOL_GAP;
		}
		this.toolbarHeight = y + TOOL_HEIGHT + 8;
	}

	private List<ToolSpec> toolSpecs() {
		return List.of(
			new ToolSpec(
				116,
				"身份: " + this.session.profileDisplayName(),
				"循环模拟普通玩家、管理员、主持人、猎人、逃走者和旁观者",
				this.session::cycleProfile
			),
			new ToolSpec(
				76,
				"人数: " + this.session.playerCountValue(),
				"循环模拟 0、1、32、64 名参与者",
				this.session::cyclePlayerCount
			),
			new ToolSpec(
				112,
				"视口: " + this.session.viewport().label(),
				"真实改变中央业务视口宽度和响应档位",
				this.session::cycleViewport
			),
			new ToolSpec(
				98,
				this.session.reducedMotion() ? "动态: 减少" : "动态: 标准",
				"切换减少动态效果",
				this.session::toggleReducedMotion
			),
			new ToolSpec(
				92,
				this.session.highContrast() ? "对比: 高" : "对比: 标准",
				"切换高对比度预览",
				this.session::toggleHighContrast
			),
			new ToolSpec(
				92,
				this.session.defaultFont() ? "字体: 默认" : "字体: 主题",
				"强制使用 Minecraft 默认字体回退",
				this.session::toggleDefaultFont
			),
			new ToolSpec(
				80,
				this.session.debugLayout() ? "调试: 开" : "调试: 关",
				"显示节点、裁剪、诊断、缓存及 Repeat 可见项",
				this.session::toggleDebugLayout
			)
		);
	}

	private void calculateViewport() {
		int availableWidth = Math.max(1, this.width - 32);
		int requestedWidth = switch (this.session.viewport()) {
			case COMPACT -> COMPACT_VIEWPORT_WIDTH;
			case STANDARD -> STANDARD_VIEWPORT_WIDTH;
			case WIDE -> WIDE_VIEWPORT_WIDTH;
		};
		this.viewportWidth = Math.min(availableWidth, requestedWidth);
		this.viewportX = (this.width - this.viewportWidth) / 2;
		int contentTop = this.toolbarHeight + VIEWPORT_HEADER_HEIGHT;
		int availableHeight = Math.max(
			1,
			this.height - contentTop - FOOTER_HEIGHT - OUTER_MARGIN
		);
		int requestedHeight = switch (this.session.viewport()) {
			case COMPACT -> COMPACT_VIEWPORT_HEIGHT;
			case STANDARD -> STANDARD_VIEWPORT_HEIGHT;
			case WIDE -> WIDE_VIEWPORT_HEIGHT;
		};
		this.viewportHeight = Math.min(availableHeight, requestedHeight);
		this.viewportY = contentTop + Math.max(0, availableHeight - this.viewportHeight) / 2;
	}

	private void layoutLiveViewport() {
		this.toolbarHeight = 0;
		this.viewportWidth = Math.max(1, Math.min(620, this.width - 24));
		this.viewportHeight = Math.max(1, Math.min(360, this.height - 24));
		this.viewportX = (this.width - this.viewportWidth) / 2;
		this.viewportY = (this.height - this.viewportHeight) / 2;
	}

	private void updatePageFit() {
		PlacedNode root = this.plan.layout().root();
		this.pageContentWidth = Math.max(
			root.bounds().right(),
			root.contentBounds().x() + root.contentWidth()
		);
		this.pageContentHeight = Math.max(
			root.bounds().bottom(),
			root.contentBounds().y() + root.contentHeight()
		);
		this.pageScale = this.session.previewChrome()
			? 1.0
			: UiLayoutEngine.fitScale(
				this.pageContentWidth,
				this.pageContentHeight,
				this.viewportWidth,
				this.viewportHeight
			);
	}

	private void layoutSafetyWidgets() {
		int panelWidth = Math.max(1, Math.min(620, this.width - 24));
		int panelHeight = Math.max(1, Math.min(330, this.height - 24));
		int x = (this.width - panelWidth) / 2;
		int y = (this.height - panelHeight) / 2;
		int buttonY = y + panelHeight - 38;
		if (forcedPage()) {
			layoutForcedSafetyWidgets(x, panelWidth, buttonY);
			return;
		}
		ConsoleButton retry = new ConsoleButton(
			x + 18,
			buttonY,
			104,
			24,
			Component.literal("重新检查资源"),
			button -> {
				ClientPageState.retryResourcePreflight();
				rebuild();
			},
			Variant.PRIMARY
		);
		retry.active = this.active != null;
		this.addRenderableWidget(retry);
		this.addRenderableWidget(
			new ConsoleButton(
				x + 130,
				buttonY,
				86,
				24,
				Component.literal("打开设置"),
				button -> this.minecraft.gui.setScreen(
					new OptionsScreen(this, this.minecraft.options, this.minecraft.level != null)
				),
				Variant.NORMAL
			)
		);
		ConsoleButton back = new ConsoleButton(
			x + panelWidth - 118,
			buttonY,
			100,
			24,
			Component.literal("返回目录"),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(retry.active ? retry : back);
	}

	private void layoutForcedSafetyWidgets(
		final int x,
		final int panelWidth,
		final int buttonY
	) {
		ConsoleButton retry = new ConsoleButton(
			x + 18,
			buttonY,
			112,
			24,
			Component.literal("重新检查资源"),
			button -> {
				ClientPageState.retryResourcePreflight();
				rebuild();
			},
			Variant.PRIMARY
		);
		retry.active = this.active != null;
		this.addRenderableWidget(retry);

		ConsoleButton diagnostics = new ConsoleButton(
			x + 138,
			buttonY,
			104,
			24,
			Component.literal(
				this.safetyDiagnosticsExpanded ? "收起诊断" : "查看诊断"
			),
			button -> {
				this.safetyDiagnosticsExpanded = !this.safetyDiagnosticsExpanded;
				rebuild();
			},
			Variant.NORMAL
		);
		this.addRenderableWidget(diagnostics);

		ConsoleButton disconnect = new ConsoleButton(
			x + panelWidth - 118,
			buttonY,
			100,
			24,
			Component.literal("断开连接"),
			button -> this.minecraft.disconnectFromWorld(
				Component.literal("已从强制流程安全错误页断开连接")
			),
			Variant.DANGER
		);
		this.addRenderableWidget(disconnect);
		this.setInitialFocus(retry.active ? retry : diagnostics);
	}

	private void indexPlaced(final PlacedNode placed) {
		this.placedNodes.put(placed.key(), placed);
		placed.children().forEach(this::indexPlaced);
	}

	private void buildPageWidgets(final PlacedNode placed) {
		RenderNode node = this.plan.nodes().get(placed.key());
		if (node != null && !node.synthetic() && node.definition() != null) {
			if (node.definition().content() instanceof ButtonContent button) {
				addButtonWidget(placed, node, button);
			} else if (node.definition().content() instanceof FieldInputContent input) {
				addFieldWidgets(placed, node, input);
			}
		}
		placed.children().forEach(this::buildPageWidgets);
	}

	private void addButtonWidget(
		final PlacedNode placed,
		final RenderNode node,
		final ButtonContent content
	) {
		Rect bounds = placed.contentBounds();
		ConsoleButton button = new ConsoleButton(
			screenX(bounds.x()),
			screenY(bounds.y()),
			Math.max(1, bounds.width()),
			Math.max(20, bounds.height()),
			node.styledText(),
			pressed -> {
				triggerUiEvent(node, UiEvent.PRESS, node.key());
				handleAction(node, content.action());
			},
			buttonVariant(node, content.action()),
			eventSound(node, UiEvent.PRESS).isEmpty(),
			(state, fallback) -> buttonAppearance(node, state, fallback)
		);
		button.active = actionEnabled(node, content);
		updateButtonTooltip(button, node, content);
		addPageWidget(node.key(), button, placed, node);
	}

	private Variant buttonVariant(final RenderNode node, final UiAction action) {
		String style = effectiveStyleName(node).orElse("");
		if ("danger_button".equals(style)) {
			return Variant.DANGER;
		}
		if (
			"navigation_button".equals(style)
				|| (
					action.type() == ActionType.LOCAL
						&& action.name().filter(name -> name.equals("back") || name.equals("close")).isPresent()
				)
				|| node.definition()
					.events()
					.getOrDefault(UiEvent.HOVER_ENTER, new EventBinding(Optional.empty(), Optional.empty()))
					.motion()
					.filter(name -> name.contains("navigation"))
					.isPresent()
		) {
			return Variant.NAVIGATION;
		}
		return "secondary_button".equals(style) ? Variant.NORMAL : Variant.PRIMARY;
	}

	private boolean evaluateEnabled(final RenderNode node, final ButtonContent content) {
		return content.enabledWhen()
			.map(
				condition -> this.bindings.evaluateCondition(
					condition,
					contextWithLocalUi(node.context())
				).value().orElse(false)
			)
			.orElse(true);
	}

	private boolean actionEnabled(final RenderNode node, final ButtonContent content) {
		return evaluateEnabled(node, content)
			&& (
				content.action().type() == ActionType.LOCAL
					|| !ClientPageState.flowActionPending()
			);
	}

	private void updateButtonTooltip(
		final ConsoleButton button,
		final RenderNode node,
		final ButtonContent content
	) {
		if (button.active || content.disabledReason().isEmpty()) {
			button.setTooltip(null);
			return;
		}
		String reason = this.bindings.evaluateText(
			content.disabledReason().orElseThrow(),
			contextWithLocalUi(node.context())
		).value().orElse("当前不可用");
		button.setTooltip(Tooltip.create(Component.literal(reason)));
	}

	private void addFieldWidgets(
		final PlacedNode placed,
		final RenderNode node,
		final FieldInputContent input
	) {
		FieldSchema field = this.active.fields().get(input.field());
		if (field == null || node.definition().id().isEmpty()) {
			return;
		}
		String nodeId = node.definition().id().orElseThrow();
		JsonElement existing = this.session.uiValue(nodeId);
		if (existing == null) {
			existing = this.session.initialFieldValue(this.active, input.field());
			if (existing != null) {
				this.session.setUiValue(nodeId, existing);
			}
		}
		if (existing != null) {
			this.localUiValues.put(nodeId, existing);
		}
		Rect area = fieldControlArea(placed, input, field);
		switch (field.type()) {
			case "boolean" -> addBooleanField(node, placed, field, nodeId, area);
			case "single_choice", "multi_choice" ->
				addChoiceField(node, placed, field, nodeId, area);
			case "integer", "string", "identifier" ->
				addTextField(node, placed, field, nodeId, area);
			default -> {
				// The server compiler rejects unsupported field types before this screen is opened.
			}
		}
	}

	private Rect fieldControlArea(
		final PlacedNode placed,
		final FieldInputContent input,
		final FieldSchema field
	) {
		Rect content = placed.contentBounds();
		int header = input.showLabel() ? 14 : 0;
		if (input.showDescription() && !field.description().isEmpty()) {
			header += 20;
		}
		return new Rect(
			content.x(),
			content.y() + header,
			content.width(),
			Math.max(20, content.height() - header)
		);
	}

	private void addBooleanField(
		final RenderNode node,
		final PlacedNode placed,
		final FieldSchema field,
		final String nodeId,
		final Rect area
	) {
		boolean value = primitiveBoolean(this.localUiValues.get(nodeId)).orElse(false);
		ConsoleButton button = new ConsoleButton(
			screenX(area.x()),
			screenY(area.y()),
			Math.max(1, area.width()),
			Math.min(24, Math.max(20, area.height())),
			styledText(node, field.name() + ": " + (value ? "开启" : "关闭")),
			pressed -> {
				triggerUiEvent(node, UiEvent.PRESS, node.key() + "/boolean");
				setUiValue(nodeId, new JsonPrimitive(!value));
				triggerUiEvent(node, UiEvent.VALUE_CHANGE, node.key() + "/boolean");
				rebuild();
			},
			value ? Variant.PRIMARY : Variant.NORMAL,
			eventSound(node, UiEvent.VALUE_CHANGE).isEmpty(),
			(state, fallback) -> buttonAppearance(node, state, fallback)
		);
		button.setTooltip(Tooltip.create(Component.literal(field.description())));
		addPageWidget(node.key() + "/field-boolean", button, placed, node);
	}

	private void addChoiceField(
		final RenderNode node,
		final PlacedNode placed,
		final FieldSchema field,
		final String nodeId,
		final Rect area
	) {
		List<String> options = field.options();
		if (options.isEmpty()) {
			return;
		}
		int columns = Math.min(3, options.size());
		int rows = Math.ceilDiv(options.size(), columns);
		int gap = 4;
		int buttonWidth = Math.max(28, (area.width() - gap * (columns - 1)) / columns);
		int buttonHeight = Math.max(20, Math.min(24, (area.height() - gap * (rows - 1)) / rows));
		int selectedCount = selectionCount(this.localUiValues.get(nodeId));
		int minimumSelections = field.minimumSelections().orElse(0);
		int maximumSelections = field.maximumSelections().orElse(options.size());
		for (int index = 0; index < options.size(); index++) {
			String option = options.get(index);
			boolean selected = field.type().equals("multi_choice")
				? arrayContains(this.localUiValues.get(nodeId), option)
				: primitiveString(this.localUiValues.get(nodeId)).filter(option::equals).isPresent();
			boolean canToggle = !field.type().equals("multi_choice")
				|| (selected ? selectedCount > minimumSelections : selectedCount < maximumSelections);
			int column = index % columns;
			int row = index / columns;
			ConsoleButton button = new ConsoleButton(
				screenX(area.x() + column * (buttonWidth + gap)),
				screenY(area.y() + row * (buttonHeight + gap)),
				buttonWidth,
				buttonHeight,
				styledText(node, (selected ? "✓ " : "") + option),
				pressed -> {
					triggerUiEvent(node, UiEvent.PRESS, node.key() + "/choice-" + option);
					JsonElement value = field.type().equals("multi_choice")
						? toggledArray(this.localUiValues.get(nodeId), option)
						: new JsonPrimitive(option);
					setUiValue(nodeId, value);
					triggerUiEvent(node, UiEvent.VALUE_CHANGE, node.key() + "/choice-" + option);
					rebuild();
				},
				selected ? Variant.PRIMARY : Variant.NORMAL,
				eventSound(node, UiEvent.VALUE_CHANGE).isEmpty(),
				(state, fallback) -> buttonAppearance(node, state, fallback)
			);
			button.active = canToggle;
			button.setTooltip(
				Tooltip.create(
					Component.literal(
						field.name()
							+ ": "
							+ option
							+ (selected ? "（已选择）" : "")
							+ (
								canToggle
									? ""
									: selected
										? "；至少保留 " + minimumSelections + " 项"
										: "；最多选择 " + maximumSelections + " 项"
							)
					)
				)
			);
			addPageWidget(node.key() + "/field-choice-" + index, button, placed, node);
		}
	}

	private void addTextField(
		final RenderNode node,
		final PlacedNode placed,
		final FieldSchema field,
		final String nodeId,
		final Rect area
	) {
		AnimatedEditBox edit = new AnimatedEditBox(
			this.font,
			screenX(area.x()),
			screenY(area.y()),
			Math.max(1, area.width()),
			Math.min(24, Math.max(20, area.height())),
			styledText(node, field.name())
		);
		edit.setMaxLength(Math.clamp(field.maximumLength().orElse(256), 1, 2_048));
		edit.setHint(styledText(node, field.required() ? "必填" : "可选"));
		edit.setValue(primitiveInputText(this.localUiValues.get(nodeId)).orElse(""));
		edit.setTextColor(styleColor(node, "text_color", MAIN_TEXT));
		edit.setResponder(
			value -> {
				Optional<JsonElement> parsed = parseFieldValue(field, value);
				JsonElement stored = parsed.orElse(JsonNull.INSTANCE);
				setUiValue(nodeId, stored);
				triggerUiEvent(node, UiEvent.VALUE_CHANGE, node.key() + "/field-edit");
				edit.setTextColor(
					parsed.isPresent() || value.isEmpty()
						? styleColor(node, "text_color", MAIN_TEXT)
						: DANGER
				);
				this.pendingPlanRefresh = true;
			}
		);
		edit.setTooltip(Tooltip.create(Component.literal(field.description())));
		addPageWidget(node.key() + "/field-edit", edit, placed, node);
	}

	private Optional<JsonElement> parseFieldValue(final FieldSchema field, final String value) {
		if (value.isEmpty()) {
			return field.required() ? Optional.empty() : Optional.of(JsonNull.INSTANCE);
		}
		return switch (field.type()) {
			case "integer" -> {
				try {
					long parsed = Long.parseLong(value);
					if (
						parsed < field.minimum().orElse(Long.MIN_VALUE)
							|| parsed > field.maximum().orElse(Long.MAX_VALUE)
					) {
						yield Optional.empty();
					}
					yield Optional.of(new JsonPrimitive(parsed));
				} catch (NumberFormatException ignored) {
					yield Optional.empty();
				}
			}
			case "identifier" -> Identifier.tryParse(value) == null
				? Optional.empty()
				: Optional.of(new JsonPrimitive(value));
			case "string" -> {
				int length = value.codePointCount(0, value.length());
				yield length < field.minimumLength().orElse(0)
						|| length > field.maximumLength().orElse(Integer.MAX_VALUE)
					? Optional.empty()
					: Optional.of(new JsonPrimitive(value));
			}
			default -> Optional.empty();
		};
	}

	private void addPageWidget(
		final String widgetKey,
		final AbstractWidget widget,
		final PlacedNode placed,
		final RenderNode node
	) {
		Rect visible = placed.clip().intersection(placed.bounds());
		widget.visible = visible.width() > 0 && visible.height() > 0;
		this.addWidget(widget);
		this.pageWidgets.put(
			widgetKey,
			new WidgetBinding(
				widget,
				placed,
				node,
				new Rect(
					widget.getX() - this.viewportX,
					widget.getY() - this.viewportY,
					widget.getWidth(),
					widget.getHeight()
				)
			)
		);
	}

	private void setUiValue(final String nodeId, final JsonElement value) {
		JsonElement copied = value.deepCopy();
		this.localUiValues.put(nodeId, copied);
		this.session.setUiValue(nodeId, copied);
		this.bindingContextCache.clear();
	}

	private BindingContext contextWithLocalUi(final BindingContext base) {
		return this.bindingContextCache.computeIfAbsent(
			base,
			context -> context.withUiValues(this.localUiValues)
		);
	}

	private void handleAction(final RenderNode node, final UiAction action) {
		boolean sameInstance = ClientPageState.activePage()
			.map(current -> this.active != null && current.instanceId().equals(this.active.instanceId()))
			.orElse(false);
		if (ClientPageState.pageStatus() != PageStatus.READY || !sameInstance) {
			this.lastRequestEnvelope = "页面实例已变化；本次预览请求已拒绝。";
			triggerActionOutcome(node, false);
			return;
		}
		this.requestSequence++;
		JsonObject envelope = new JsonObject();
		envelope.addProperty("instance_id", this.active.instanceId().toString());
		envelope.addProperty("generation", this.active.generation());
		envelope.addProperty("state_revision", this.active.stateRevision());
		envelope.addProperty("page_id", this.active.page().id().toString());
		envelope.addProperty("node_id", node.definition().id().orElse("<unnamed>"));
		envelope.addProperty("node_key", node.key());
		envelope.addProperty("node_pointer", node.definition().pointer());
		envelope.addProperty("action_type", action.type().name().toLowerCase(Locale.ROOT));
		envelope.addProperty(
			"action_id",
			action.registeredAction()
				.map(Identifier::toString)
				.or(() -> action.name())
				.orElse("")
		);
		envelope.addProperty("local_request_sequence", this.requestSequence);
		envelope.add("typed_ui_values", currentUiValues());
		this.lastRequestEnvelope = PRETTY_JSON.toJson(envelope);

		if (action.type() != ActionType.LOCAL) {
			if (this.session.previewChrome()) {
				triggerActionOutcome(node, true);
				return;
			}
			submitFlowAction(node, action);
			return;
		}
		String name = action.name().orElse("");
		switch (name) {
			case "back", "close" -> {
				if (forcedPage()) {
					this.lastRequestEnvelope = "强制流程只能由匹配的服务端释放状态关闭。";
					triggerActionOutcome(node, false);
					return;
				}
				triggerActionOutcome(node, true);
				onClose();
			}
			case "expand" -> {
				triggerActionOutcome(node, true);
				updateLocalActionValue(node, true);
			}
			case "collapse" -> {
				triggerActionOutcome(node, true);
				updateLocalActionValue(node, false);
			}
			case "toggle" -> {
				triggerActionOutcome(node, true);
				String id = node.definition().id().orElse(node.key());
				boolean current = primitiveBoolean(this.localUiValues.get(id)).orElse(false);
				updateLocalActionValue(node, !current);
			}
			case "tab", "preview" -> {
				triggerActionOutcome(node, true);
				updateLocalActionValue(node, name);
			}
			default -> {
				// Unknown local actions are intentionally visible in the envelope but have no effect.
				triggerActionOutcome(node, false);
			}
		}
	}

	private void submitFlowAction(final RenderNode node, final UiAction action) {
		String actionId = action.name().orElse("");
		if (actionId.isBlank()) {
			this.lastRequestEnvelope = "当前非本地动作没有可提交的流程动作名称。";
			triggerActionOutcome(node, false);
			return;
		}
		FlowSubmission submission = ClientPageState.submitFlowAction(
			actionId,
			currentFieldValues()
		);
		if (!submission.sent()) {
			this.lastRequestEnvelope = "动作未发送: " + submission.message();
			triggerActionOutcome(node, false);
			return;
		}
		this.pendingActionSequence = submission.requestSequence();
		this.pendingActionNodeKey = node.key();
		this.lastRequestEnvelope = "动作已发送，正在等待服务端确认…";
	}

	private List<FieldValue> currentFieldValues() {
		if (this.plan == null) {
			return List.of();
		}
		Map<Identifier, FieldValue> values = new LinkedHashMap<>();
		for (RenderNode renderNode : this.plan.nodes().values()) {
			if (
				renderNode.definition() == null
					|| !(renderNode.definition().content() instanceof FieldInputContent input)
					|| renderNode.definition().id().isEmpty()
			) {
				continue;
			}
			FieldSchema schema = this.active.fields().get(input.field());
			if (schema == null) {
				continue;
			}
			String nodeId = renderNode.definition().id().orElseThrow();
			JsonElement value = this.localUiValues.get(nodeId);
			if (value == null) {
				value = this.session.initialFieldValue(this.active, input.field());
			}
			if (value != null) {
				values.put(
					input.field(),
					new FieldValue(input.field(), schema.type(), value.toString())
				);
			}
		}
		return List.copyOf(values.values());
	}

	private void triggerActionOutcome(final RenderNode node, final boolean success) {
		UiEvent event = success ? UiEvent.SUCCESS : UiEvent.ERROR;
		this.lastRequestOutcome = event;
		EventBinding binding = eventBinding(node, event).orElse(null);
		triggerMotion(node, event, Util.getMillis());
		if (binding != null && binding.sound().isPresent()) {
			playNamedSound(binding.sound().orElseThrow(), node.key() + "|" + event);
		} else {
			playNamedSound(success ? "success" : "error", node.key() + "|" + event);
		}
	}

	private void updateLocalActionValue(final RenderNode node, final boolean value) {
		updateLocalActionValue(node, new JsonPrimitive(value));
	}

	private void updateLocalActionValue(final RenderNode node, final String value) {
		updateLocalActionValue(node, new JsonPrimitive(value));
	}

	private void updateLocalActionValue(final RenderNode node, final JsonElement value) {
		String id = node.definition().id().orElse(node.key());
		setUiValue(id, value);
		rebuild();
	}

	private JsonObject currentUiValues() {
		JsonObject values = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : this.localUiValues.entrySet()) {
			values.add(entry.getKey(), entry.getValue().deepCopy());
		}
		return values;
	}

	@Override
	public void tick() {
		super.tick();
		long now = Util.getMillis();
		playPendingSounds(now);
		consumeActionResult();
		if (this.observedRevision != ClientPageState.revision()) {
			rebuild();
			return;
		}
		if (this.plan == null) {
			return;
		}
		if (this.closing) {
			if (motionsFinished(UiEvent.HIDE, now)) {
				finishClose();
			}
			return;
		}
		refreshButtonStates();
		refreshInteractionEvents();
		ensureFocusedWidgetVisible();
		if (this.pendingPlanRefresh && !hasFocusedEditBox()) {
			this.pendingPlanRefresh = false;
			rebuild();
		}
	}

	private void consumeActionResult() {
		if (this.pendingActionSequence < 0L) {
			return;
		}
		ClientPageState.consumeFlowActionResult(this.pendingActionSequence)
			.ifPresent(result -> {
				RenderNode node = this.plan == null
					? null
					: this.plan.nodes().get(this.pendingActionNodeKey);
				this.pendingActionSequence = -1L;
				this.pendingActionNodeKey = "";
				this.lastRequestEnvelope = result.message().isEmpty()
					? "服务端返回: " + result.code().serializedName()
					: result.message();
				if (node != null) {
					triggerActionOutcome(node, result.success());
				}
			});
	}

	private void refreshButtonStates() {
		for (WidgetBinding binding : this.pageWidgets.values()) {
			if (
				binding.widget() instanceof ConsoleButton button
					&& binding.node().definition().content() instanceof ButtonContent content
			) {
				button.active = actionEnabled(binding.node(), content);
				updateButtonTooltip(button, binding.node(), content);
			}
		}
	}

	private void refreshInteractionEvents() {
		Set<String> nowHovered = new HashSet<>();
		Set<String> nowFocused = new HashSet<>();
		for (WidgetBinding binding : this.pageWidgets.values()) {
			if (!binding.widget().visible || !binding.widget().active) {
				continue;
			}
			if (binding.widget().isHovered()) {
				nowHovered.add(binding.node().key());
			}
			if (binding.widget().isFocused()) {
				nowFocused.add(binding.node().key());
			}
		}
		dispatchInteractionChanges(
			this.hoveredNodeKeys,
			nowHovered,
			UiEvent.HOVER_ENTER,
			UiEvent.HOVER_LEAVE
		);
		dispatchInteractionChanges(
			this.focusedNodeKeys,
			nowFocused,
			UiEvent.FOCUS,
			UiEvent.BLUR
		);
	}

	private void dispatchInteractionChanges(
		final Set<String> previous,
		final Set<String> current,
		final UiEvent entered,
		final UiEvent left
	) {
		for (String key : current) {
			if (!previous.contains(key)) {
				renderNode(key).ifPresent(node -> triggerUiEvent(node, entered, key));
			}
		}
		for (String key : previous) {
			if (!current.contains(key)) {
				renderNode(key).ifPresent(node -> triggerUiEvent(node, left, key));
			}
		}
		previous.clear();
		previous.addAll(current);
	}

	private Optional<RenderNode> renderNode(final String key) {
		return this.plan == null ? Optional.empty() : Optional.ofNullable(this.plan.nodes().get(key));
	}

	private boolean hasFocusedEditBox() {
		return this.pageWidgets.values()
			.stream()
			.anyMatch(binding -> binding.widget() instanceof EditBox && binding.widget().isFocused());
	}

	private void ensureFocusedWidgetVisible() {
		WidgetBinding focused = this.pageWidgets.values()
			.stream()
			.filter(binding -> binding.widget().isFocused())
			.findFirst()
			.orElse(null);
		if (focused == null) {
			return;
		}
		PlacedNode scroll = this.placedNodes.values()
			.stream()
			.filter(node -> node.type() == NodeType.SCROLL)
			.filter(node -> focused.node().key().startsWith(node.key() + "/"))
			.max(Comparator.comparingInt(node -> node.key().length()))
			.orElse(null);
		if (scroll == null) {
			return;
		}
		Direction direction = scrollDirection(scroll);
		Rect view = scroll.contentBounds();
		Rect target = focused.placed().bounds();
		int delta = direction == Direction.VERTICAL
			? target.y() < view.y()
				? target.y() - view.y()
				: target.bottom() > view.bottom() ? target.bottom() - view.bottom() : 0
			: target.x() < view.x()
				? target.x() - view.x()
				: target.right() > view.right() ? target.right() - view.right() : 0;
		if (delta == 0) {
			return;
		}
		int offset = Math.clamp(
			scroll.scrollOffset() + delta,
			0,
			maxScrollOffset(scroll)
		);
		if (offset != scroll.scrollOffset()) {
			this.scrollOffsets.put(scroll.key(), offset);
			rebuild();
		}
	}

	private Optional<EventBinding> eventBinding(final RenderNode node, final UiEvent event) {
		if (node.definition() == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(node.definition().events().get(event));
	}

	private Optional<String> eventSound(final RenderNode node, final UiEvent event) {
		return eventBinding(node, event).flatMap(EventBinding::sound);
	}

	private void triggerUiEvent(
		final RenderNode node,
		final UiEvent event,
		final String sourceKey
	) {
		triggerMotion(node, event, Util.getMillis());
		playEventSound(node, event, sourceKey);
	}

	private void playEventSound(
		final RenderNode node,
		final UiEvent event,
		final String sourceKey
	) {
		eventSound(node, event).ifPresent(name -> playNamedSound(name, sourceKey));
	}

	private void playNamedSound(final String name, final String sourceKey) {
		if (this.active == null) {
			return;
		}
		SoundCue cue = this.active.theme().soundCues().get(name);
		if (cue == null) {
			cue = BuiltInUiResources.defaultTheme().soundCues().get(name);
		}
		if (cue == null) {
			return;
		}
		Identifier sound = cue.sound();
		int delay = cue.delayMilliseconds();
		float volume = (float)cue.volume();
		float pitch = (float)cue.pitch();
		long now = Util.getMillis();
		String limitKey = sourceKey + "|" + sound;
		if (now - this.lastSoundTimes.getOrDefault(limitKey, Long.MIN_VALUE / 2) < SOUND_LIMIT_MILLIS) {
			return;
		}
		this.lastSoundTimes.put(limitKey, now);
		if (delay <= 0) {
			playSound(sound, volume, pitch);
		} else {
			this.pendingSounds.add(new PendingSound(sound, volume, pitch, now + delay));
		}
	}

	private void playPendingSounds(final long now) {
		for (int index = this.pendingSounds.size() - 1; index >= 0; index--) {
			PendingSound pending = this.pendingSounds.get(index);
			if (pending.dueMillis() <= now) {
				playSound(pending.sound(), pending.volume(), pending.pitch());
				this.pendingSounds.remove(index);
			}
		}
	}

	private void playSound(final Identifier sound, final float volume, final float pitch) {
		if (this.minecraft.getSoundManager().getSoundEvent(sound) == null) {
			return;
		}
		this.minecraft.getSoundManager()
			.play(
				SimpleSoundInstance.forUI(
					SoundEvent.createVariableRangeEvent(sound),
					pitch,
					volume
				)
			);
	}

	@Override
	public void extractBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
	}

	@Override
	public void extractRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		if (isSafetyPage()) {
			renderSafetyPage(graphics);
			super.extractRenderState(graphics, mouseX, mouseY, tickProgress);
			return;
		}

		if (this.session.previewChrome()) {
			renderToolbarFrame(graphics);
			renderViewportFrame(graphics);
		}
		MotionLayout motionLayout = buildMotionLayout(Util.getMillis());
		this.currentMotionLayout = motionLayout;
		try {
			renderForcedViewportFrame(graphics, motionLayout);
			drawNode(graphics, this.plan.layout().root(), motionLayout);
			graphics.nextStratum();
			drawPageWidgets(graphics, mouseX, mouseY, tickProgress, motionLayout);
			drawScrollbars(graphics, motionLayout);
			if (this.session.debugLayout()) {
				graphics.nextStratum();
				drawDebugOverlay(graphics, mouseX, mouseY, motionLayout);
			}
			if (this.session.previewChrome() && !this.lastRequestEnvelope.isEmpty()) {
				graphics.nextStratum();
				drawRequestEnvelope(graphics);
			}
			if (this.session.previewChrome()) {
				renderFooter(graphics);
			}
			super.extractRenderState(graphics, mouseX, mouseY, tickProgress);
		} finally {
			this.currentMotionLayout = null;
		}
	}

	private void renderForcedViewportFrame(
		final GuiGraphicsExtractor graphics,
		final MotionLayout motionLayout
	) {
		if (!forcedPage()) {
			return;
		}
		PlacedNode root = this.plan.layout().root();
		NodeFrame frame = motionLayout.frames().get(root.key());
		RenderNode rootNode = this.plan.nodes().get(root.key());
		if (frame == null || rootNode == null || frame.opacity() <= 0.0F) {
			return;
		}
		float opacity = frame.opacity() * styleNumber(rootNode, "opacity", 1.0F);
		int border = withOpacity(
			styleColor(rootNode, "border_color", SURFACE_BORDER),
			opacity
		);
		int brand = withOpacity(
			parseHexColor(this.motionColorTokens.get("brand")).orElse(BRAND_GOLD),
			opacity
		);
		int info = withOpacity(
			parseHexColor(this.motionColorTokens.get("info")).orElse(INFO_CYAN),
			opacity
		);
		int x = Math.max(0, this.viewportX - 2);
		int y = Math.max(0, this.viewportY - 2);
		int right = Math.min(this.width, this.viewportX + this.viewportWidth + 2);
		int bottom = Math.min(this.height, this.viewportY + this.viewportHeight + 2);
		if (right <= x || bottom <= y) {
			return;
		}
		graphics.outline(x, y, right - x, bottom - y, border);
		graphics.fill(x, y, Math.min(right, x + 4), bottom, info);
		graphics.fill(Math.min(right, x + 4), y, right, Math.min(bottom, y + 2), brand);
	}

	private void renderToolbarFrame(final GuiGraphicsExtractor graphics) {
		graphics.fill(OUTER_MARGIN, OUTER_MARGIN, this.width - OUTER_MARGIN, this.toolbarHeight, PANEL);
		graphics.outline(
			OUTER_MARGIN,
			OUTER_MARGIN,
			this.width - OUTER_MARGIN * 2,
			this.toolbarHeight - OUTER_MARGIN,
			PANEL_BORDER
		);
		graphics.fill(OUTER_MARGIN, OUTER_MARGIN, OUTER_MARGIN + 3, this.toolbarHeight, BRAND_GOLD);
		graphics.fill(OUTER_MARGIN + 3, OUTER_MARGIN, this.width - OUTER_MARGIN, OUTER_MARGIN + 1, INFO_CYAN);
	}

	private void renderViewportFrame(final GuiGraphicsExtractor graphics) {
		int headerY = this.viewportY - VIEWPORT_HEADER_HEIGHT + 2;
		graphics.text(
			this.font,
			Component.literal(this.active.page().title().plainText()),
			this.viewportX,
			headerY,
			MAIN_TEXT,
			true
		);
		String meta = this.active.page().id()
			+ "  // generation "
			+ this.active.generation()
			+ "  // revision "
			+ this.active.stateRevision();
		graphics.text(
			this.font,
			meta,
			this.viewportX,
			headerY + 11,
			MUTED_TEXT,
			false
		);
		graphics.fill(
			this.viewportX - 1,
			this.viewportY - 1,
			this.viewportX + this.viewportWidth + 1,
			this.viewportY + this.viewportHeight + 1,
			SURFACE_BORDER
		);
		graphics.fill(
			this.viewportX,
			this.viewportY,
			this.viewportX + this.viewportWidth,
			this.viewportY + this.viewportHeight,
			this.session.highContrast() ? 0xFF020406 : SURFACE
		);
	}

	private void drawNode(
		final GuiGraphicsExtractor graphics,
		final PlacedNode placed,
		final MotionLayout motionLayout
	) {
		NodeFrame frame = motionLayout.frames().get(placed.key());
		if (frame == null) {
			return;
		}
		Rect clip = frame.clip();
		if (clip.width() <= 0 || clip.height() <= 0) {
			return;
		}
		graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
		try {
			RenderNode node = this.plan.nodes().get(placed.key());
			if (node != null) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(
					(float)frame.transform().offsetX(),
					(float)frame.transform().offsetY()
				);
				graphics.pose().scale(
					(float)frame.transform().scale(),
					(float)frame.transform().scale()
				);
				try {
					drawNodeContents(graphics, placed, node, frame);
				} finally {
					graphics.pose().popMatrix();
				}
			}
			for (PlacedNode child : placed.children()) {
				drawNode(graphics, child, motionLayout);
			}
		} finally {
			graphics.disableScissor();
		}
	}

	private void drawNodeContents(
		final GuiGraphicsExtractor graphics,
		final PlacedNode placed,
		final RenderNode node,
		final NodeFrame frame
	) {
		Rect bounds = placed.bounds();
		Rect content = placed.contentBounds();
		float opacity = frame.opacity() * styleNumber(node, "opacity", 1.0F);
		OptionalInt motionColor = this.session.highContrast()
			? OptionalInt.empty()
			: frame.color();
		switch (node.type()) {
			case CARD -> drawCard(graphics, node, bounds, opacity, motionColor);
			case TEXT -> drawText(graphics, node, content, opacity, motionColor);
			case IMAGE -> drawImage(graphics, node, content, opacity, motionColor);
			case DIVIDER -> drawDivider(graphics, node, content, opacity, motionColor);
			case PROGRESS -> drawProgress(graphics, node, content, opacity, frame);
			case PLAYER_HEAD -> drawPlayerHead(graphics, node, content, opacity, motionColor);
			case FIELD_INPUT -> drawFieldInput(graphics, node, content, opacity, motionColor);
			case SCROLL -> {
				if (this.session.highContrast()) {
					graphics.outline(
						content.x(),
						content.y(),
						content.width(),
						content.height(),
						withOpacity(INFO_CYAN, opacity)
					);
				}
			}
			case ROW, COLUMN, GRID, FLOW, OVERLAY, REPEAT -> {
				if (node == this.plan.root()) {
					int background = motionColor.orElse(
						styleColor(node, "background_color", PANEL)
					);
					graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), withOpacity(background, opacity));
				}
			}
			case BUTTON, SPACER -> {
				// Native widgets and non-rendering spacers are handled elsewhere.
			}
		}
	}

	private void drawCard(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect bounds,
		final float opacity,
		final OptionalInt motionColor
	) {
		int background = styleColor(node, "background_color", SURFACE_RAISED);
		int border = styleColor(node, "border_color", SURFACE_BORDER);
		int accent = motionColor.orElse(styleColor(node, "accent_color", INFO_CYAN));
		int borderWidth = Math.clamp(Math.round(styleNumber(node, "border_width", 1.0F)), 0, 4);
		int detailSize = Math.clamp(
			Math.round(styleNumber(node, "corner_radius", 5.0F)),
			2,
			12
		);
		String frameStyle = UiStyleRuntime.stringValue(
			styleValues(node),
			"frame_style"
		).orElse("rail");
		switch (frameStyle) {
			case "dossier" -> drawDossierFrame(
				graphics,
				bounds,
				background,
				border,
				borderWidth,
				detailSize,
				opacity
			);
			case "target" -> drawTargetFrame(
				graphics,
				bounds,
				background,
				border,
				accent,
				borderWidth,
				detailSize,
				opacity
			);
			case "broadcast" -> drawBroadcastFrame(
				graphics,
				bounds,
				background,
				border,
				accent,
				borderWidth,
				opacity
			);
			case "warning" -> drawWarningFrame(
				graphics,
				bounds,
				background,
				border,
				accent,
				borderWidth,
				opacity
			);
			case "plain", "rail" -> {
				graphics.fill(
					bounds.x(),
					bounds.y(),
					bounds.right(),
					bounds.bottom(),
					withOpacity(background, opacity)
				);
				for (int index = 0; index < borderWidth; index++) {
					graphics.outline(
						bounds.x() + index,
						bounds.y() + index,
						Math.max(0, bounds.width() - index * 2),
						Math.max(0, bounds.height() - index * 2),
						withOpacity(border, opacity)
					);
				}
			}
		}
		String accentEdge = UiStyleRuntime.stringValue(
			styleValues(node),
			"accent_edge"
		).orElse(frameStyle.equals("plain") || frameStyle.equals("target") ? "none" : "left");
		drawAccentEdge(
			graphics,
			bounds,
			accent,
			accentEdge,
			opacity
		);
	}

	private static void drawDossierFrame(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int background,
		final int border,
		final int borderWidth,
		final int cut,
		final float opacity
	) {
		int effectiveCut = Math.min(
			cut,
			Math.max(1, Math.min(bounds.width(), bounds.height()) / 3)
		);
		int fill = withOpacity(background, opacity);
		graphics.fill(
			bounds.x() + effectiveCut,
			bounds.y(),
			bounds.right() - effectiveCut,
			bounds.bottom(),
			fill
		);
		graphics.fill(bounds.x() + 2, bounds.y() + 2, bounds.right() - 2, bounds.bottom() - 2, fill);
		graphics.fill(
			bounds.x(),
			bounds.y() + effectiveCut,
			bounds.right(),
			bounds.bottom() - effectiveCut,
			fill
		);
		int line = withOpacity(border, opacity);
		for (int index = 0; index < borderWidth; index++) {
			int x = bounds.x() + index;
			int y = bounds.y() + index;
			int right = bounds.right() - index;
			int bottom = bounds.bottom() - index;
			graphics.fill(x + effectiveCut, y, right - effectiveCut, y + 1, line);
			graphics.fill(x + effectiveCut, bottom - 1, right - effectiveCut, bottom, line);
			graphics.fill(x, y + effectiveCut, x + 1, bottom - effectiveCut, line);
			graphics.fill(right - 1, y + effectiveCut, right, bottom - effectiveCut, line);
			graphics.fill(x + 2, y + 2, x + effectiveCut, y + 3, line);
			graphics.fill(right - effectiveCut, y + 2, right - 2, y + 3, line);
			graphics.fill(x + 2, bottom - 3, x + effectiveCut, bottom - 2, line);
			graphics.fill(right - effectiveCut, bottom - 3, right - 2, bottom - 2, line);
		}
	}

	private static void drawTargetFrame(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int background,
		final int border,
		final int accent,
		final int borderWidth,
		final int detailSize,
		final float opacity
	) {
		graphics.fill(
			bounds.x(),
			bounds.y(),
			bounds.right(),
			bounds.bottom(),
			withOpacity(background, opacity)
		);
		int length = Math.min(
			Math.max(8, detailSize * 2),
			Math.max(1, Math.min(bounds.width(), bounds.height()) / 3)
		);
		int line = withOpacity(border, opacity);
		int hot = withOpacity(accent, opacity);
		for (int index = 0; index < Math.max(1, borderWidth); index++) {
			int x = bounds.x() + index;
			int y = bounds.y() + index;
			int right = bounds.right() - index;
			int bottom = bounds.bottom() - index;
			graphics.fill(x, y, x + length, y + 1, hot);
			graphics.fill(x, y, x + 1, y + length, hot);
			graphics.fill(right - length, y, right, y + 1, line);
			graphics.fill(right - 1, y, right, y + length, line);
			graphics.fill(x, bottom - 1, x + length, bottom, line);
			graphics.fill(x, bottom - length, x + 1, bottom, line);
			graphics.fill(right - length, bottom - 1, right, bottom, hot);
			graphics.fill(right - 1, bottom - length, right, bottom, hot);
		}
	}

	private static void drawBroadcastFrame(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int background,
		final int border,
		final int accent,
		final int borderWidth,
		final float opacity
	) {
		graphics.fill(
			bounds.x(),
			bounds.y(),
			bounds.right(),
			bounds.bottom(),
			withOpacity(background, opacity)
		);
		graphics.fill(
			bounds.x(),
			bounds.y(),
			bounds.right(),
			Math.min(bounds.bottom(), bounds.y() + 3),
			withOpacity(accent, opacity)
		);
		for (int index = 0; index < borderWidth; index++) {
			graphics.outline(
				bounds.x() + index,
				bounds.y() + index,
				Math.max(0, bounds.width() - index * 2),
				Math.max(0, bounds.height() - index * 2),
				withOpacity(border, opacity)
			);
		}
		for (int y = bounds.y() + 7; y < bounds.bottom() - 2; y += 5) {
			graphics.fill(
				bounds.x() + 2,
				y,
				bounds.right() - 2,
				y + 1,
				withOpacity(border, opacity * 0.10F)
			);
		}
	}

	private static void drawWarningFrame(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int background,
		final int border,
		final int accent,
		final int borderWidth,
		final float opacity
	) {
		graphics.fill(
			bounds.x(),
			bounds.y(),
			bounds.right(),
			bounds.bottom(),
			withOpacity(background, opacity)
		);
		graphics.fill(
			bounds.x(),
			bounds.y(),
			bounds.right(),
			Math.min(bounds.bottom(), bounds.y() + 3),
			withOpacity(accent, opacity)
		);
		int outer = withOpacity(accent, opacity);
		int inner = withOpacity(border, opacity);
		for (int index = 0; index < Math.max(1, borderWidth); index++) {
			graphics.outline(
				bounds.x() + index,
				bounds.y() + index,
				Math.max(0, bounds.width() - index * 2),
				Math.max(0, bounds.height() - index * 2),
				outer
			);
		}
		if (bounds.width() > 8 && bounds.height() > 8) {
			graphics.outline(
				bounds.x() + 4,
				bounds.y() + 4,
				bounds.width() - 8,
				bounds.height() - 8,
				inner
			);
		}
	}

	private static void drawAccentEdge(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int accent,
		final String edge,
		final float opacity
	) {
		int color = withOpacity(accent, opacity);
		switch (edge) {
			case "left" -> graphics.fill(
				bounds.x(),
				bounds.y(),
				Math.min(bounds.right(), bounds.x() + 2),
				bounds.bottom(),
				color
			);
			case "top" -> graphics.fill(
				bounds.x(),
				bounds.y(),
				bounds.right(),
				Math.min(bounds.bottom(), bounds.y() + 2),
				color
			);
			case "right" -> graphics.fill(
				Math.max(bounds.x(), bounds.right() - 2),
				bounds.y(),
				bounds.right(),
				bounds.bottom(),
				color
			);
			case "bottom" -> graphics.fill(
				bounds.x(),
				Math.max(bounds.y(), bounds.bottom() - 2),
				bounds.right(),
				bounds.bottom(),
				color
			);
			default -> {
			}
		}
	}

	private void drawText(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final OptionalInt motionColor
	) {
		if (!(node.definition().content() instanceof TextContent text) || content.width() <= 0) {
			return;
		}
		int color = withOpacity(
			motionColor.orElse(styleColor(node, "text_color", MAIN_TEXT)),
			opacity
		);
		boolean shadow = styleBoolean(node, "shadow", true);
		if (!text.wrap()) {
			FormattedText value = node.styledText();
			if (
				text.overflow() == TextOverflow.ELLIPSIS
					&& this.font.width(value) > content.width()
			) {
				Component ellipsis = styledText(node, "…");
				int allowed = Math.max(0, content.width() - this.font.width(ellipsis));
				value = FormattedText.composite(
					this.font.substrByWidth(value, allowed),
					ellipsis
				);
			}
			FormattedCharSequence visual = Language.getInstance().getVisualOrder(value);
			int x = alignedTextX(text.alignment(), content, this.font.width(visual));
			graphics.text(this.font, visual, x, content.y(), color, shadow);
			return;
		}
		List<String> lines = TextLineWrapper.wrap(
			node.text(),
			Math.max(1, content.width()),
			value -> this.font.width(styledText(node, value))
		);
		int maximum = text.maximumLines().orElse(lines.size());
		int visible = Math.min(maximum, Math.min(lines.size(), Math.max(1, content.height() / this.font.lineHeight)));
		for (int index = 0; index < visible; index++) {
			FormattedCharSequence line = Language.getInstance()
				.getVisualOrder(styledText(node, lines.get(index)));
			int x = alignedTextX(text.alignment(), content, this.font.width(line));
			graphics.text(
				this.font,
				line,
				x,
				content.y() + index * this.font.lineHeight,
				color,
				shadow
			);
		}
		if (
			text.overflow() == TextOverflow.ELLIPSIS
				&& visible > 0
				&& lines.size() > visible
		) {
			Component ellipsis = styledText(node, "…");
			graphics.text(
				this.font,
				ellipsis,
				Math.max(content.x(), content.right() - this.font.width(ellipsis)),
				content.y() + (visible - 1) * this.font.lineHeight,
				color,
				shadow
			);
		}
	}

	private int alignedTextX(final TextAlign align, final Rect area, final int textWidth) {
		return switch (align) {
			case LEFT -> area.x();
			case CENTER -> area.x() + Math.max(0, area.width() - textWidth) / 2;
			case RIGHT -> area.right() - Math.min(area.width(), textWidth);
		};
	}

	private void drawImage(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final OptionalInt motionColor
	) {
		if (
			!(node.definition().content() instanceof ImageContent image)
				|| content.width() <= 0
				|| content.height() <= 0
		) {
			return;
		}
		Identifier texture = resolvedTexture(node.definition(), image).orElse(null);
		if (texture == null) {
			drawMissingImage(graphics, node, content, opacity, motionColor);
			return;
		}
		ImageSize size = imageSize(texture);
		Rect destination = fitted(content, size, image.fit());
		graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
		try {
			graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture,
				destination.x(),
				destination.y(),
				0.0F,
				0.0F,
				destination.width(),
				destination.height(),
				destination.width(),
				destination.height(),
				withOpacity(motionColor.orElse(0xFFFFFFFF), opacity)
			);
		} finally {
			graphics.disableScissor();
		}
	}

	private Optional<Identifier> resolvedTexture(
		final NodeDefinition definition,
		final ImageContent image
	) {
		AssetReference reference = this.active.page().assets()
			.stream()
			.filter(asset -> asset.type() == AssetType.TEXTURE)
			.filter(asset -> asset.id().equals(image.asset().toString()))
			.filter(asset -> asset.pointer().startsWith(definition.pointer()))
			.findFirst()
			.orElse(null);
		String resolved = reference == null
			? image.asset().toString()
			: this.active.resources().resolved(reference);
		return Optional.ofNullable(resolved).map(Identifier::tryParse);
	}

	private ImageSize imageSize(final Identifier texture) {
		return this.imageSizes.computeIfAbsent(
			texture,
			id -> this.minecraft.getResourceManager()
				.getResource(id)
				.map(
					resource -> {
						try (
							InputStream input = resource.open();
							NativeImage image = NativeImage.read(input)
						) {
							return new ImageSize(
								Math.max(1, image.getWidth()),
								Math.max(1, image.getHeight())
							);
						} catch (IOException | RuntimeException ignored) {
							return ImageSize.square();
						}
					}
				)
				.orElseGet(ImageSize::square)
		);
	}

	private Rect fitted(final Rect area, final ImageSize image, final ImageFit fit) {
		if (fit == ImageFit.STRETCH || area.width() <= 0 || area.height() <= 0) {
			return area;
		}
		double scale = fit == ImageFit.CONTAIN
			? Math.min((double)area.width() / image.width(), (double)area.height() / image.height())
			: Math.max((double)area.width() / image.width(), (double)area.height() / image.height());
		int width = Math.max(1, (int)Math.round(image.width() * scale));
		int height = Math.max(1, (int)Math.round(image.height() * scale));
		return new Rect(
			area.x() + (area.width() - width) / 2,
			area.y() + (area.height() - height) / 2,
			width,
			height
		);
	}

	private void drawMissingImage(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final OptionalInt motionColor
	) {
		graphics.fill(
			content.x(),
			content.y(),
			content.right(),
			content.bottom(),
			withOpacity(0xFF2B2026, opacity)
		);
		graphics.outline(
			content.x(),
			content.y(),
			content.width(),
			content.height(),
			withOpacity(motionColor.orElse(DANGER), opacity)
		);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			BUILT_IN_WARNING,
			content.x() + Math.max(0, (content.width() - 20) / 2),
			content.y() + 4,
			0,
			0,
			20,
			20,
			20,
			20,
			withOpacity(motionColor.orElse(0xFFFFFFFF), opacity)
		);
		String alt = node.definition().content() instanceof ImageContent image
			? image.alternativeText()
				.flatMap(
					text -> this.bindings.evaluateText(
						text,
						contextWithLocalUi(node.context())
					).value()
				)
				.orElse("图片不可用")
			: "图片不可用";
		graphics.textWithWordWrap(
			this.font,
			styledText(node, alt),
			content.x() + 6,
			content.y() + 28,
			Math.max(1, content.width() - 12),
			withOpacity(SECONDARY_TEXT, opacity),
			false
		);
	}

	private void drawDivider(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final OptionalInt motionColor
	) {
		if (!(node.definition().content() instanceof DividerContent divider)) {
			return;
		}
		int color = withOpacity(
			motionColor.orElse(styleColor(node, "border_color", SURFACE_BORDER)),
			opacity
		);
		if (divider.direction() == Direction.HORIZONTAL) {
			graphics.fill(
				content.x(),
				content.y(),
				content.right(),
				content.y() + Math.min(content.height(), divider.thickness()),
				color
			);
		} else {
			graphics.fill(
				content.x(),
				content.y(),
				content.x() + Math.min(content.width(), divider.thickness()),
				content.bottom(),
				color
			);
		}
	}

	private void drawProgress(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final NodeFrame frame
	) {
		if (!(node.definition().content() instanceof ProgressContent progress)) {
			return;
		}
		double value = frame.progressValue()
			.orElseGet(() -> evaluateNumber(progress.value(), node.context()).orElse(0.0));
		double minimum = evaluateNumber(progress.minimum(), node.context()).orElse(0.0);
		double maximum = evaluateNumber(progress.maximum(), node.context()).orElse(0.0);
		int barY = node.text().isEmpty() ? content.y() : content.y() + this.font.lineHeight + 3;
		int barHeight = Math.max(6, Math.min(12, content.bottom() - barY));
		if (!node.text().isEmpty()) {
			graphics.text(
				this.font,
				node.styledText(),
				content.x(),
				content.y(),
				withOpacity(styleColor(node, "text_color", MAIN_TEXT), opacity),
				false
			);
		}
		int track = withOpacity(styleColor(node, "track_color", 0xFF17212B), opacity);
		int border = styleColor(node, "border_color", SURFACE_BORDER);
		int fill = withOpacity(
			(this.session.highContrast() ? OptionalInt.empty() : frame.color())
				.orElse(styleColor(node, "fill_color", BRAND_GOLD)),
			opacity
		);
		graphics.fill(content.x(), barY, content.right(), barY + barHeight, track);
		graphics.outline(
			content.x(),
			barY,
			content.width(),
			barHeight,
			withOpacity(border, opacity)
		);
		if (maximum > minimum) {
			double ratio = Math.clamp((value - minimum) / (maximum - minimum), 0.0, 1.0);
			int innerWidth = Math.max(0, content.width() - 2);
			int filled = (int)Math.round(innerWidth * ratio);
			graphics.fill(
				content.x() + 1,
				barY + 1,
				content.x() + 1 + filled,
				barY + barHeight - 1,
				fill
			);
			int segments = Math.clamp(
				Math.round(styleNumber(node, "segment_count", 1.0F)),
				1,
				64
			);
			int separator = withOpacity(border, opacity * 0.72F);
			for (int index = 1; index < segments; index++) {
				int separatorX = content.x() + 1 + (int)Math.round(innerWidth * index / (double)segments);
				graphics.fill(
					separatorX,
					barY + 1,
					separatorX + 1,
					barY + barHeight - 1,
					separator
				);
			}
			if (filled > 0 && filled < innerWidth) {
				int markerX = content.x() + 1 + filled;
				int marker = styleColor(node, "accent_color", BRAND_GOLD);
				graphics.fill(
					markerX - 1,
					barY - 1,
					markerX + 2,
					barY + barHeight + 1,
					withOpacity(marker, opacity * 0.30F)
				);
				graphics.fill(
					markerX,
					barY,
					markerX + 1,
					barY + barHeight,
					withOpacity(marker, opacity)
				);
			}
		} else {
			graphics.text(
				this.font,
				styledText(node, "暂无进度"),
				content.x() + 4,
				barY + Math.max(0, (barHeight - this.font.lineHeight) / 2),
				withOpacity(MUTED_TEXT, opacity),
				false
			);
		}
	}

	private void drawFieldInput(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final OptionalInt motionColor
	) {
		if (!(node.definition().content() instanceof FieldInputContent input)) {
			return;
		}
		FieldSchema field = this.active.fields().get(input.field());
		if (field == null) {
			if (forcedPage()) {
				return;
			}
			graphics.text(
				this.font,
				styledText(node, "字段定义不可用: " + input.field()),
				content.x(),
				content.y(),
				withOpacity(DANGER, opacity),
				false
			);
			return;
		}
		int y = content.y();
		if (input.showLabel()) {
			graphics.text(
				this.font,
				styledText(node, field.name() + (field.required() ? "  *" : "")),
				content.x(),
				y,
				withOpacity(
					motionColor.orElse(styleColor(node, "text_color", MAIN_TEXT)),
					opacity
				),
				false
			);
			y += 14;
		}
		if (input.showDescription() && !field.description().isEmpty()) {
			int bottom = Math.min(content.bottom(), y + 20);
			graphics.enableScissor(content.x(), y, content.right(), bottom);
			try {
				graphics.textWithWordWrap(
					this.font,
					styledText(node, field.description()),
					content.x(),
					y,
					Math.max(1, content.width()),
					withOpacity(
						styleColor(node, "secondary_text_color", SECONDARY_TEXT),
						opacity
					),
					false
				);
			} finally {
				graphics.disableScissor();
			}
		}
	}

	private Optional<Double> evaluateNumber(
		final io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression expression,
		final BindingContext context
	) {
		return this.bindings.evaluateValue(expression, contextWithLocalUi(context))
			.value()
			.filter(JsonElement::isJsonPrimitive)
			.filter(value -> value.getAsJsonPrimitive().isNumber())
			.flatMap(
				value -> {
					try {
						double number = value.getAsDouble();
						return Double.isFinite(number) ? Optional.of(number) : Optional.empty();
					} catch (RuntimeException ignored) {
						return Optional.empty();
					}
				}
			);
	}

	private void drawPlayerHead(
		final GuiGraphicsExtractor graphics,
		final RenderNode node,
		final Rect content,
		final float opacity,
		final OptionalInt motionColor
	) {
		if (!(node.definition().content() instanceof PlayerHeadContent head)) {
			return;
		}
		UUID uuid = evaluatePrimitive(head.uuid(), node.context())
			.flatMap(
				value -> {
					try {
						return Optional.of(UUID.fromString(value));
					} catch (IllegalArgumentException ignored) {
						return Optional.empty();
					}
				}
			)
			.orElse(FALLBACK_UUID);
		boolean online = evaluatePrimitive(head.online(), node.context())
			.map(Boolean::parseBoolean)
			.orElse(false);
		int size = Math.max(1, Math.min(content.width(), content.height()));
		PlayerIdentityRenderer.drawHead(
			graphics,
			uuid,
			online,
			content.x(),
			content.y(),
			size,
			false
		);
		if (head.showStatus()) {
			int dot = Math.max(3, size / 4);
			int color = online ? SUCCESS : MUTED_TEXT;
			graphics.fill(
				content.x() + size - dot,
				content.y() + size - dot,
				content.x() + size,
				content.y() + size,
				withOpacity(color, opacity)
			);
			graphics.outline(
				content.x() + size - dot,
				content.y() + size - dot,
				dot,
				dot,
				withOpacity(0xFF081018, opacity)
			);
		}
	}

	private Optional<String> evaluatePrimitive(
		final io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression expression,
		final BindingContext context
	) {
		return this.bindings.evaluateValue(expression, contextWithLocalUi(context))
			.value()
			.filter(JsonElement::isJsonPrimitive)
			.map(JsonElement::getAsString);
	}

	private void drawPageWidgets(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress,
		final MotionLayout motionLayout
	) {
		synchronizeWidgetBounds(motionLayout);
		for (WidgetBinding binding : this.pageWidgets.values()) {
			AbstractWidget widget = binding.widget();
			NodeFrame frame = motionLayout.frames().get(binding.node().key());
			if (frame == null || !widget.visible) {
				continue;
			}
			Rect clip = frame.clip();
			graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
			try {
				widget.extractRenderState(graphics, mouseX, mouseY, tickProgress);
			} finally {
				graphics.disableScissor();
			}
		}
	}

	private void synchronizeWidgetBounds(final MotionLayout motionLayout) {
		for (WidgetBinding binding : this.pageWidgets.values()) {
			NodeFrame frame = motionLayout.frames().get(binding.node().key());
			if (frame == null) {
				binding.widget().visible = false;
				continue;
			}
			Rect bounds = frame.transform().apply(binding.layoutBounds());
			setWidgetBounds(binding.widget(), bounds);
			if (binding.widget() instanceof ConsoleButton button) {
				button.setPresentationScale((float)frame.transform().scale());
				button.setAlpha(1.0F);
			} else if (binding.widget() instanceof AnimatedEditBox edit) {
				edit.setPresentationScale((float)frame.transform().scale());
				edit.setMotionColor(
					this.session.highContrast()
						? OptionalInt.empty()
						: frame.color()
				);
				edit.setAlpha(frame.opacity());
			} else {
				binding.widget().setAlpha(frame.opacity());
			}
			Rect visible = bounds.intersection(frame.clip());
			binding.widget().visible = visible.width() > 0 && visible.height() > 0;
		}
	}

	private static void setWidgetBounds(final AbstractWidget widget, final Rect bounds) {
		widget.setX(bounds.x());
		widget.setY(bounds.y());
		widget.setWidth(Math.max(1, bounds.width()));
		widget.setHeight(Math.max(1, bounds.height()));
	}

	private void drawScrollbars(
		final GuiGraphicsExtractor graphics,
		final MotionLayout motionLayout
	) {
		for (PlacedNode scroll : this.placedNodes.values()) {
			if (scroll.type() != NodeType.SCROLL || !showScrollbar(scroll)) {
				continue;
			}
			Scrollbar bar = scrollbar(scroll, motionLayout);
			if (bar == null) {
				continue;
			}
			Rect visible = visibleClip(scroll, motionLayout);
			if (visible.width() <= 0 || visible.height() <= 0) {
				continue;
			}
			graphics.enableScissor(visible.x(), visible.y(), visible.right(), visible.bottom());
			try {
				graphics.fill(
					bar.track().x(),
					bar.track().y(),
					bar.track().right(),
					bar.track().bottom(),
					0xA015202A
				);
				graphics.fill(
					bar.thumb().x(),
					bar.thumb().y(),
					bar.thumb().right(),
					bar.thumb().bottom(),
					this.draggingScrollKey != null && this.draggingScrollKey.equals(scroll.key())
						? BRAND_GOLD
						: INFO_CYAN
				);
			} finally {
				graphics.disableScissor();
			}
		}
	}

	private boolean showScrollbar(final PlacedNode scroll) {
		RenderNode node = this.plan.nodes().get(scroll.key());
		return node != null
			&& node.definition() != null
			&& node.definition().content() instanceof SingleChildContent content
			&& content.showScrollbar()
			&& maxScrollOffset(scroll) > 0;
	}

	private Scrollbar scrollbar(final PlacedNode scroll, final MotionLayout motionLayout) {
		int maximum = maxScrollOffset(scroll);
		if (maximum <= 0) {
			return null;
		}
		NodeFrame frame = motionLayout.frames().get(scroll.key());
		if (frame == null) {
			return null;
		}
		Direction direction = scrollDirection(scroll);
		Rect view = scroll.contentBounds();
		Rect logicalTrack;
		int contentExtent;
		if (direction == Direction.VERTICAL) {
			int trackWidth = 4;
			logicalTrack = new Rect(
				view.right() - trackWidth,
				view.y(),
				trackWidth,
				view.height()
			);
			contentExtent = scroll.contentHeight();
		} else {
			int trackHeight = 4;
			logicalTrack = new Rect(
				view.x(),
				view.bottom() - trackHeight,
				view.width(),
				trackHeight
			);
			contentExtent = scroll.contentWidth();
		}
		Rect logicalThumb = UiLayoutEngine.scrollbarThumb(
			logicalTrack,
			direction,
			contentExtent,
			scroll.scrollOffset(),
			maximum
		);
		return new Scrollbar(
			frame.transform().apply(logicalTrack),
			frame.transform().apply(logicalThumb),
			direction
		);
	}

	private int maxScrollOffset(final PlacedNode scroll) {
		return scrollDirection(scroll) == Direction.VERTICAL
			? Math.max(0, scroll.contentHeight() - scroll.contentBounds().height())
			: Math.max(0, scroll.contentWidth() - scroll.contentBounds().width());
	}

	private Direction scrollDirection(final PlacedNode scroll) {
		RenderNode node = this.plan == null ? null : this.plan.nodes().get(scroll.key());
		if (
			node != null
				&& node.definition() != null
				&& node.definition().content() instanceof SingleChildContent content
		) {
			return content.scrollDirection().orElse(Direction.VERTICAL);
		}
		return Direction.VERTICAL;
	}

	private void drawDebugOverlay(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final MotionLayout motionLayout
	) {
		for (PlacedNode placed : this.placedNodes.values()) {
			NodeFrame frame = motionLayout.frames().get(placed.key());
			if (frame == null) {
				continue;
			}
			Rect bounds = frame.bounds();
			int color = placed.type() == NodeType.SCROLL
				? WARNING
				: placed.type() == NodeType.BUTTON || placed.type() == NodeType.FIELD_INPUT
					? BRAND_GOLD
					: INFO_CYAN;
			graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), withAlpha(color, 170));
		}
		PlacedNode hovered = deepestAt(
			this.plan.layout().root(),
			mouseX,
			mouseY,
			motionLayout
		);
		int panelWidth = Math.min(360, Math.max(250, this.width - 24));
		List<String> lines = new ArrayList<>();
		lines.add(
			"tier="
				+ this.plan.tier()
				+ " viewport="
				+ this.viewportWidth
				+ "x"
				+ this.viewportHeight
		);
		lines.add(
			"layout="
				+ this.plan.layout().totalDiagnostics()
				+ " binding="
				+ this.plan.totalBindingDiagnostics()
				+ " resources="
				+ this.active.resources().totalMissing()
		);
		lines.add(
			"repeat visible="
				+ this.plan.visibleRepeatItems()
				+ " cache="
				+ ClientPageState.cacheEntries()
				+ "/"
				+ ClientPageState.cacheBytes()
				+ "B"
		);
		lines.add(
			"preview reduced="
				+ this.session.reducedMotion()
				+ " contrast="
				+ this.session.highContrast()
				+ " defaultFont="
				+ this.session.defaultFont()
		);
		if (hovered != null) {
			lines.add("hover key=" + hovered.key());
			lines.add("pointer=" + hovered.pointer());
		}
		this.plan.layout()
			.diagnostics()
			.stream()
			.limit(MAX_DEBUG_DETAILS)
			.forEach(diagnostic -> lines.add("L " + diagnostic.code() + " " + diagnostic.pointer()));
		this.plan.bindingDiagnostics()
			.stream()
			.limit(MAX_DEBUG_DETAILS)
			.forEach(diagnostic -> lines.add("B " + diagnostic.code() + " " + diagnostic.source()));
		this.active.resourceDiagnostics()
			.stream()
			.limit(MAX_DEBUG_DETAILS)
			.forEach(diagnostic -> lines.add("R " + diagnostic.code() + " " + diagnostic.pointer()));

		int panelHeight = Math.min(this.height - 24, 12 + lines.size() * 10);
		int x = 12;
		int y = Math.min(this.height - panelHeight - 12, this.viewportY + 8);
		graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF20B1118);
		graphics.outline(x, y, panelWidth, panelHeight, WARNING);
		for (int index = 0; index < lines.size() && y + 7 + index * 10 < y + panelHeight - 3; index++) {
			graphics.text(
				this.font,
				lines.get(index),
				x + 6,
				y + 6 + index * 10,
				index < 4 ? MAIN_TEXT : SECONDARY_TEXT,
				false
			);
		}
	}

	private PlacedNode deepestAt(
		final PlacedNode placed,
		final int mouseX,
		final int mouseY,
		final MotionLayout motionLayout
	) {
		NodeFrame frame = motionLayout.frames().get(placed.key());
		if (frame == null || !contains(frame.bounds().intersection(frame.clip()), mouseX, mouseY)) {
			return null;
		}
		for (int index = placed.children().size() - 1; index >= 0; index--) {
			PlacedNode child = deepestAt(
				placed.children().get(index),
				mouseX,
				mouseY,
				motionLayout
			);
			if (child != null) {
				return child;
			}
		}
		return placed;
	}

	private void drawRequestEnvelope(final GuiGraphicsExtractor graphics) {
		int width = Math.min(560, Math.max(280, this.width - 32));
		int textWidth = width - 16;
		int textHeight = this.font.wordWrapHeight(
			Component.literal(this.lastRequestEnvelope),
			textWidth
		);
		int height = Math.min(this.height - 40, textHeight + 28);
		int x = (this.width - width) / 2;
		int y = Math.max(20, this.height - FOOTER_HEIGHT - height - 6);
		int outcomeColor = this.lastRequestOutcome == UiEvent.ERROR ? DANGER : SUCCESS;
		graphics.fill(x, y, x + width, y + height, 0xFA0B1219);
		graphics.outline(x, y, width, height, outcomeColor);
		graphics.fill(x, y, x + 3, y + height, outcomeColor);
		graphics.text(
			this.font,
			this.lastRequestOutcome == UiEvent.ERROR
				? "2B 动作请求预览 // 已拒绝"
				: "2B 动作请求预览 // 已截获，未发送",
			x + 9,
			y + 7,
			outcomeColor,
			false
		);
		graphics.enableScissor(x + 6, y + 20, x + width - 6, y + height - 5);
		try {
			graphics.textWithWordWrap(
				this.font,
				Component.literal(this.lastRequestEnvelope),
				x + 9,
				y + 20,
				textWidth,
				MAIN_TEXT,
				false
			);
		} finally {
			graphics.disableScissor();
		}
	}

	private void renderFooter(final GuiGraphicsExtractor graphics) {
		int y = this.height - 25;
		String status = this.plan.tier()
			+ "  // "
			+ this.viewportWidth
			+ "×"
			+ this.viewportHeight
			+ "  // Repeat "
			+ this.plan.visibleRepeatItems()
			+ " visible";
		int availableWidth = Math.max(0, this.width - 140);
		if (this.font.width(status) > availableWidth) {
			status = this.plan.tier() + "  // " + this.viewportWidth + "×" + this.viewportHeight;
		}
		graphics.text(this.font, status, 12, y + 6, MUTED_TEXT, false);
	}

	private void renderSafetyPage(final GuiGraphicsExtractor graphics) {
		int panelWidth = Math.max(1, Math.min(620, this.width - 24));
		int panelHeight = Math.max(1, Math.min(330, this.height - 24));
		int x = (this.width - panelWidth) / 2;
		int y = (this.height - panelHeight) / 2;
		graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
		graphics.outline(x, y, panelWidth, panelHeight, DANGER);
		graphics.fill(x, y, x + 4, y + panelHeight, DANGER);
		graphics.fill(x + 4, y, x + panelWidth, y + 2, WARNING);
		graphics.text(this.font, "PIXEL TZZ // 安全错误页", x + 18, y + 13, WARNING, false);
		graphics.text(
			this.font,
			this.active == null ? "页面实例不可用" : "缺少页面必需资源",
			x + 18,
			y + 31,
			MAIN_TEXT,
			true
		);
		String summary = this.renderFailure.isEmpty()
			? ClientPageState.pageMessage()
			: "页面渲染计划构建失败: " + this.renderFailure;
		graphics.textWithWordWrap(
			this.font,
			Component.literal(summary.isEmpty() ? "业务页面已停止渲染。" : summary),
			x + 18,
			y + 49,
			panelWidth - 36,
			SECONDARY_TEXT,
			false
		);
		int lineY = y + 84;
		if (this.active != null) {
			graphics.text(
				this.font,
				"page="
					+ this.active.page().id()
					+ "  generation="
					+ this.active.generation()
					+ "  missing="
					+ this.active.resources().totalMissing(),
				x + 18,
				lineY,
				MUTED_TEXT,
				false
			);
			lineY += 14;
			int diagnosticLimit = this.safetyDiagnosticsExpanded ? 16 : 4;
			for (
				Diagnostic diagnostic
					: this.active.resourceDiagnostics().stream().limit(diagnosticLimit).toList()
			) {
				String detail = diagnostic.serializedType()
					+ "  "
					+ diagnostic.id()
					+ "  "
					+ diagnostic.pointer()
					+ (diagnostic.required() ? "  [必需]" : "  [可选]");
				graphics.text(
					this.font,
					detail,
					x + 24,
					lineY,
					diagnostic.required() ? DANGER : WARNING,
					false
				);
				lineY += 12;
			}
		}
		graphics.text(
			this.font,
			forcedPage()
				? "强制流程仍保持锁定；这里只允许重试、查看诊断或断开连接。"
				: "此页只使用模组内置颜色、原版字体和控件；未渲染任何数据包业务节点。",
			x + 18,
			y + panelHeight - 57,
			MUTED_TEXT,
			false
		);
	}

	private boolean triggerMotion(
		final RenderNode node,
		final UiEvent event,
		final long now
	) {
		if (node.definition() == null) {
			return false;
		}
		MotionChannel channel = MotionChannel.forEvent(event);
		EnumMap<UiEvent, MotionPlayback> playbacks = this.motionPlaybacks.computeIfAbsent(
			node.key(),
			ignored -> new EnumMap<>(UiEvent.class)
		);
		MotionPlayback replaced = null;
		for (var iterator = playbacks.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<UiEvent, MotionPlayback> entry = iterator.next();
			if (MotionChannel.forEvent(entry.getKey()) != channel) {
				continue;
			}
			if (replaced == null || entry.getValue().sequence() > replaced.sequence()) {
				replaced = entry.getValue();
			}
			iterator.remove();
		}

		MotionDefinition motion = eventMotion(node, event).orElse(null);
		if (motion == null) {
			if (playbacks.isEmpty()) {
				this.motionPlaybacks.remove(node.key());
			}
			return false;
		}
		UiMotionRuntime.Sample origin = replaced == null
			? null
			: samplePlayback(replaced, now);
		UiMotionRuntime.Sample initial = UiMotionRuntime.sample(
			motion,
			0L,
			this.session.reducedMotion(),
			this.motionColorTokens
		);
		MotionCorrection correction = origin == null || this.session.reducedMotion()
			? MotionCorrection.none()
			: MotionCorrection.between(
				origin,
				initial,
				baseMotionColor(node),
				baseMotionProgress(node)
			);
		playbacks.put(
			event,
			new MotionPlayback(motion, now, ++this.motionSequence, correction)
		);
		return true;
	}

	private Optional<MotionDefinition> eventMotion(
		final RenderNode node,
		final UiEvent event
	) {
		return eventBinding(node, event)
			.flatMap(EventBinding::motion)
			.flatMap(this::namedMotion);
	}

	private Optional<MotionDefinition> namedMotion(final String name) {
		if (this.active == null) {
			return Optional.empty();
		}
		MotionDefinition motion = this.active.theme().motions().get(name);
		if (motion == null) {
			motion = BuiltInUiResources.defaultTheme().motions().get(name);
		}
		return Optional.ofNullable(motion);
	}

	private int baseMotionColor(final RenderNode node) {
		return switch (node.type()) {
			case CARD -> styleColor(node, "accent_color", INFO_CYAN);
			case TEXT, BUTTON, FIELD_INPUT -> styleColor(node, "text_color", MAIN_TEXT);
			case IMAGE, PLAYER_HEAD -> 0xFFFFFFFF;
			case DIVIDER -> styleColor(node, "border_color", SURFACE_BORDER);
			case PROGRESS -> styleColor(node, "fill_color", BRAND_GOLD);
			case ROW, COLUMN, GRID, FLOW, OVERLAY, REPEAT ->
				node == this.plan.root()
					? styleColor(node, "background_color", PANEL)
					: MAIN_TEXT;
			case SCROLL, SPACER -> MAIN_TEXT;
		};
	}

	private double baseMotionProgress(final RenderNode node) {
		return node.definition().content() instanceof ProgressContent progress
			? evaluateNumber(progress.value(), node.context()).orElse(0.0)
			: 0.0;
	}

	private UiMotionRuntime.Sample sampleNodeMotion(final String key, final long now) {
		EnumMap<UiEvent, MotionPlayback> playbacks = this.motionPlaybacks.get(key);
		if (playbacks == null || playbacks.isEmpty()) {
			return UiMotionRuntime.compose(List.of());
		}
		return UiMotionRuntime.compose(
			playbacks.values()
				.stream()
				.sorted(Comparator.comparingLong(MotionPlayback::sequence))
				.map(playback -> samplePlayback(playback, now))
				.toList()
		);
	}

	private UiMotionRuntime.Sample samplePlayback(
		final MotionPlayback playback,
		final long now
	) {
		long elapsed = Math.max(0L, now - playback.startedMillis());
		UiMotionRuntime.Sample sample = UiMotionRuntime.sample(
			playback.motion(),
			elapsed,
			this.session.reducedMotion(),
			this.motionColorTokens
		);
		if (this.session.reducedMotion() || playback.correction().isIdentity()) {
			return sample;
		}
		long activeElapsed = elapsed - playback.motion().delayMilliseconds();
		double activeProgress = activeElapsed <= 0L
			? 0.0
			: playback.motion().durationMilliseconds() <= 0
				? 1.0
				: Math.clamp(
					(double)activeElapsed / playback.motion().durationMilliseconds(),
					0.0,
					1.0
				);
		return playback.correction().apply(sample, 1.0 - activeProgress);
	}

	private boolean motionsFinished(final UiEvent event, final long now) {
		for (EnumMap<UiEvent, MotionPlayback> playbacks : this.motionPlaybacks.values()) {
			MotionPlayback playback = playbacks.get(event);
			if (playback != null && !samplePlayback(playback, now).finished()) {
				return false;
			}
		}
		return true;
	}

	private MotionLayout buildMotionLayout(final long now) {
		Rect viewport = new Rect(
			this.viewportX,
			this.viewportY,
			this.viewportWidth,
			this.viewportHeight
		);
		Map<String, NodeFrame> frames = new LinkedHashMap<>();
		buildNodeFrames(
			this.plan.layout().root(),
			pageRootTransform(),
			viewport,
			1.0F,
			now,
			frames
		);
		return new MotionLayout(frames);
	}

	private Transform pageRootTransform() {
		if (this.pageScale >= 0.999) {
			return Transform.root(this.viewportX, this.viewportY);
		}
		double offsetX = this.viewportX
			+ (this.viewportWidth - this.pageContentWidth * this.pageScale) / 2.0;
		double offsetY = this.viewportY
			+ (this.viewportHeight - this.pageContentHeight * this.pageScale) / 2.0;
		return new Transform(this.pageScale, offsetX, offsetY);
	}

	private void buildNodeFrames(
		final PlacedNode placed,
		final Transform parentTransform,
		final Rect parentClip,
		final float parentOpacity,
		final long now,
		final Map<String, NodeFrame> frames
	) {
		UiMotionRuntime.Sample sample = sampleNodeMotion(placed.key(), now);
		Transform transform = parentTransform.around(
			placed.bounds(),
			sample.scale(),
			sample.translateX(),
			sample.translateY()
		);
		Rect viewport = new Rect(
			this.viewportX,
			this.viewportY,
			this.viewportWidth,
			this.viewportHeight
		);
		Rect bounds = transform.apply(placed.bounds());
		Rect content = transform.apply(placed.contentBounds());
		Rect clip = parentClip.intersection(viewport);
		if (sample.clipProgress() < 1.0) {
			Rect reveal = new Rect(
				bounds.x(),
				bounds.y(),
				Math.clamp(
					(int)Math.round(bounds.width() * sample.clipProgress()),
					0,
					bounds.width()
				),
				bounds.height()
			);
			clip = clip.intersection(reveal);
		}
		float opacity = parentOpacity * sample.opacity();
		NodeFrame frame = new NodeFrame(
			transform,
			bounds,
			content,
			clip,
			Math.clamp(opacity, 0.0F, 1.0F),
			sample.color(),
			sample.progressValue()
		);
		frames.put(placed.key(), frame);
		Rect childClip = placed.type() == NodeType.SCROLL || placed.type() == NodeType.CARD
			? clip.intersection(content)
			: clip;
		for (PlacedNode child : placed.children()) {
			buildNodeFrames(child, transform, childClip, opacity, now, frames);
		}
	}

	private Optional<String> effectiveStyleName(final RenderNode node) {
		return UiStyleRuntime.effectiveStyleName(
			node.definition(),
			this.plan.tier(),
			this.active.theme().styles().keySet()
		);
	}

	private Map<String, StyleValue> styleValues(final RenderNode node) {
		return styleValues(node, StyleState.NORMAL, false);
	}

	private Map<String, StyleValue> styleValues(
		final RenderNode node,
		final StyleState state,
		final boolean interactive
	) {
		return this.resolvedStyles.computeIfAbsent(
			new StyleCacheKey(node.key(), state, interactive),
			ignored -> UiStyleRuntime.resolve(
				this.active.theme(),
				node.definition(),
				this.plan.tier(),
				state,
				interactive
			)
		);
	}

	private int styleColor(final RenderNode node, final String name, final int fallback) {
		return styleColor(styleValues(node), name, fallback);
	}

	private int styleColor(
		final Map<String, StyleValue> values,
		final String name,
		final int fallback
	) {
		if (this.session.highContrast()) {
			return switch (name) {
				case "background_color", "surface_color", "track_color" -> 0xFF05080C;
				case "text_color" -> 0xFFFFFFFF;
				case "secondary_text_color" -> 0xFFE2E8EC;
				case "border_color" -> INFO_CYAN;
				case "accent_color", "fill_color" -> BRAND_GOLD;
				default -> fallback;
			};
		}
		StyleValue value = values.get(name);
		if (value == null) {
			return fallback;
		}
		try {
			JsonElement json = JsonParser.parseString(value.canonicalJson());
			if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			String raw = json.getAsString();
			if (raw.startsWith("@colors.")) {
				raw = this.active.theme().tokens().colors().get(raw.substring("@colors.".length()));
			}
			return parseHexColor(raw).orElse(fallback);
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private float styleNumber(final RenderNode node, final String name, final float fallback) {
		return styleNumber(styleValues(node), name, fallback);
	}

	private float styleNumber(
		final Map<String, StyleValue> values,
		final String name,
		final float fallback
	) {
		StyleValue value = values.get(name);
		if (value == null) {
			return fallback;
		}
		try {
			JsonElement json = JsonParser.parseString(value.canonicalJson());
			double number;
			if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
				String raw = json.getAsString();
				if (!raw.startsWith("@spacing.")) {
					return fallback;
				}
				Integer spacing = this.active.theme()
					.tokens()
					.spacing()
					.get(raw.substring("@spacing.".length()));
				if (spacing == null) {
					return fallback;
				}
				number = spacing;
			} else {
				number = json.getAsDouble();
			}
			return Double.isFinite(number) ? (float)number : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private boolean styleBoolean(final RenderNode node, final String name, final boolean fallback) {
		return styleBoolean(styleValues(node), name, fallback);
	}

	private boolean styleBoolean(
		final Map<String, StyleValue> values,
		final String name,
		final boolean fallback
	) {
		StyleValue value = values.get(name);
		if (value == null) {
			return fallback;
		}
		try {
			JsonElement json = JsonParser.parseString(value.canonicalJson());
			return json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()
				? json.getAsBoolean()
				: fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private Appearance buttonAppearance(
		final RenderNode node,
		final StyleState state,
		final Appearance fallback
	) {
		Map<String, StyleValue> values = styleValues(node, state, true);
		NodeFrame frame = this.currentMotionLayout == null
			? null
			: this.currentMotionLayout.frames().get(node.key());
		OptionalInt motionColor = frame == null || this.session.highContrast()
			? OptionalInt.empty()
			: frame.color();
		int text = motionColor.orElse(styleColor(values, "text_color", fallback.text()));
		int accent = motionColor.orElse(styleColor(values, "accent_color", fallback.accent()));
		return new Appearance(
			styleColor(values, "background_color", fallback.background()),
			styleColor(values, "border_color", fallback.border()),
			text,
			accent,
			Math.round(styleNumber(values, "border_width", fallback.borderWidth())),
			styleNumber(values, "opacity", fallback.opacity())
				* (frame == null ? 1.0F : frame.opacity()),
			styleBoolean(values, "shadow", fallback.shadow()),
			styleBoolean(values, "navigation_arrows", fallback.navigationArrows()),
			Math.round(styleNumber(values, "arrow_travel", fallback.arrowTravel()))
		);
	}

	private static Component styledText(final RenderNode node, final String text) {
		return Component.literal(text)
			.withStyle(style -> style.withFont(new FontDescription.Resource(node.fontId())));
	}

	private static Optional<Integer> parseHexColor(final String value) {
		if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
			return Optional.empty();
		}
		return Optional.of(0xFF000000 | Integer.parseInt(value.substring(1), 16));
	}

	private int screenX(final int layoutX) {
		return this.viewportX + layoutX;
	}

	private int screenY(final int layoutY) {
		return this.viewportY + layoutY;
	}

	private Rect visibleClip(final PlacedNode placed, final MotionLayout motionLayout) {
		NodeFrame frame = motionLayout.frames().get(placed.key());
		return frame == null
			? new Rect(0, 0, 0, 0)
			: frame.content().intersection(frame.clip());
	}

	private static int withOpacity(final int color, final float opacity) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * Math.clamp(opacity, 0.0F, 1.0F));
		return withAlpha(color, alpha);
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		if (this.closing) {
			return true;
		}
		if (this.plan == null) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		MotionLayout motionLayout = buildMotionLayout(Util.getMillis());
		PlacedNode scroll = deepestScrollableAt(mouseX, mouseY, motionLayout);
		if (scroll == null) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		Direction direction = scrollDirection(scroll);
		double amount = direction == Direction.HORIZONTAL && horizontalAmount != 0.0
			? horizontalAmount
			: verticalAmount;
		if (amount == 0.0) {
			return false;
		}
		int offset = Math.clamp(
			scroll.scrollOffset() - (int)Math.round(amount * SCROLL_STEP),
			0,
			maxScrollOffset(scroll)
		);
		if (offset == scroll.scrollOffset()) {
			return false;
		}
		this.scrollOffsets.put(scroll.key(), offset);
		rebuild();
		return true;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.key() == GLFW_KEY_ESCAPE && forcedPage()) {
			LivePageSupervisor.openRestrictedPause(this.minecraft, this);
			return true;
		}
		if (this.closing) {
			if (event.key() == GLFW_KEY_ESCAPE) {
				finishClose();
			}
			return true;
		}
		if (super.keyPressed(event)) {
			return true;
		}
		if (this.plan == null || hasFocusedEditBox()) {
			return false;
		}
		PlacedNode scroll = keyboardScrollTarget(buildMotionLayout(Util.getMillis()));
		if (scroll == null) {
			return false;
		}
		Direction direction = scrollDirection(scroll);
		int key = event.key();
		int maximum = maxScrollOffset(scroll);
		int pageStep = Math.max(
			SCROLL_STEP,
			(direction == Direction.VERTICAL
				? scroll.contentBounds().height()
				: scroll.contentBounds().width()) - SCROLL_STEP
		);
		Integer target = switch (key) {
			case GLFW_KEY_HOME -> 0;
			case GLFW_KEY_END -> maximum;
			case GLFW_KEY_PAGE_UP -> scroll.scrollOffset() - pageStep;
			case GLFW_KEY_PAGE_DOWN -> scroll.scrollOffset() + pageStep;
			case GLFW_KEY_UP -> direction == Direction.VERTICAL
				? scroll.scrollOffset() - SCROLL_STEP
				: null;
			case GLFW_KEY_DOWN -> direction == Direction.VERTICAL
				? scroll.scrollOffset() + SCROLL_STEP
				: null;
			case GLFW_KEY_LEFT -> direction == Direction.HORIZONTAL
				? scroll.scrollOffset() - SCROLL_STEP
				: null;
			case GLFW_KEY_RIGHT -> direction == Direction.HORIZONTAL
				? scroll.scrollOffset() + SCROLL_STEP
				: null;
			default -> null;
		};
		if (target == null) {
			return false;
		}
		int offset = Math.clamp(target, 0, maximum);
		if (offset != scroll.scrollOffset()) {
			this.scrollOffsets.put(scroll.key(), offset);
			rebuild();
		}
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !forcedPage();
	}

	@Override
	public boolean canInterruptWithAnotherScreen() {
		return !forcedPage();
	}

	private PlacedNode keyboardScrollTarget(final MotionLayout motionLayout) {
		WidgetBinding focused = this.pageWidgets.values()
			.stream()
			.filter(binding -> binding.widget().isFocused())
			.findFirst()
			.orElse(null);
		if (focused != null) {
			PlacedNode enclosing = this.placedNodes.values()
				.stream()
				.filter(node -> node.type() == NodeType.SCROLL)
				.filter(node -> focused.node().key().startsWith(node.key() + "/"))
				.filter(node -> maxScrollOffset(node) > 0)
				.max(Comparator.comparingInt(node -> node.key().length()))
				.orElse(null);
			if (enclosing != null) {
				return enclosing;
			}
		}
		return this.placedNodes.values()
			.stream()
			.filter(node -> node.type() == NodeType.SCROLL)
			.filter(node -> maxScrollOffset(node) > 0)
			.filter(node -> {
				Rect visible = visibleClip(node, motionLayout);
				return visible.width() > 0 && visible.height() > 0;
			})
			.findFirst()
			.orElse(null);
	}

	private PlacedNode deepestScrollableAt(
		final double mouseX,
		final double mouseY,
		final MotionLayout motionLayout
	) {
		return this.placedNodes.values()
			.stream()
			.filter(node -> node.type() == NodeType.SCROLL)
			.filter(node -> maxScrollOffset(node) > 0)
			.filter(node -> contains(visibleClip(node, motionLayout), mouseX, mouseY))
			.max(Comparator.comparingInt(node -> node.key().length()))
			.orElse(null);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.closing) {
			return true;
		}
		MotionLayout motionLayout = this.plan == null
			? null
			: buildMotionLayout(Util.getMillis());
		if (motionLayout != null) {
			synchronizeWidgetBounds(motionLayout);
		}
		if (event.button() == 0 && this.plan != null) {
			PlacedNode scroll = this.placedNodes.values()
				.stream()
				.filter(node -> node.type() == NodeType.SCROLL)
				.filter(this::showScrollbar)
				.filter(
					node -> {
						Scrollbar bar = scrollbar(node, motionLayout);
						return bar != null
							&& contains(visibleClip(node, motionLayout), event.x(), event.y())
							&& contains(bar.track(), event.x(), event.y());
					}
				)
				.max(Comparator.comparingInt(node -> node.key().length()))
				.orElse(null);
			if (scroll != null) {
				Scrollbar bar = scrollbar(scroll, motionLayout);
				this.draggingScrollKey = scroll.key();
				this.dragStartMouse = bar.direction() == Direction.VERTICAL ? event.y() : event.x();
				this.dragStartOffset = scroll.scrollOffset();
				return true;
			}
		}
		List<AbstractWidget> hidden = hideClippedPageWidgetsAt(
			event.x(),
			event.y(),
			motionLayout
		);
		try {
			return super.mouseClicked(event, doubleClick);
		} finally {
			hidden.forEach(widget -> widget.visible = true);
		}
	}

	private List<AbstractWidget> hideClippedPageWidgetsAt(
		final double mouseX,
		final double mouseY,
		final MotionLayout motionLayout
	) {
		List<AbstractWidget> hidden = new ArrayList<>();
		if (motionLayout == null) {
			return hidden;
		}
		for (WidgetBinding binding : this.pageWidgets.values()) {
			Rect widget = widgetRect(binding, motionLayout);
			if (!contains(widget, mouseX, mouseY)) {
				continue;
			}
			NodeFrame frame = motionLayout.frames().get(binding.node().key());
			Rect clip = frame == null ? new Rect(0, 0, 0, 0) : frame.clip();
			if (!contains(clip, mouseX, mouseY) && binding.widget().visible) {
				binding.widget().visible = false;
				hidden.add(binding.widget());
			}
		}
		return hidden;
	}

	private Rect widgetRect(final WidgetBinding binding, final MotionLayout motionLayout) {
		NodeFrame frame = motionLayout.frames().get(binding.node().key());
		return frame == null
			? new Rect(0, 0, 0, 0)
			: frame.transform().apply(binding.layoutBounds());
	}

	@Override
	public boolean mouseDragged(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		if (this.closing) {
			return true;
		}
		if (this.draggingScrollKey == null || this.plan == null || event.button() != 0) {
			return super.mouseDragged(event, deltaX, deltaY);
		}
		PlacedNode scroll = this.placedNodes.get(this.draggingScrollKey);
		if (scroll == null) {
			this.draggingScrollKey = null;
			return false;
		}
		Scrollbar bar = scrollbar(scroll, buildMotionLayout(Util.getMillis()));
		if (bar == null) {
			this.draggingScrollKey = null;
			return false;
		}
		double mouse = bar.direction() == Direction.VERTICAL ? event.y() : event.x();
		int travel = bar.direction() == Direction.VERTICAL
			? bar.track().height() - bar.thumb().height()
			: bar.track().width() - bar.thumb().width();
		if (travel <= 0) {
			return true;
		}
		int offset = Math.clamp(
			this.dragStartOffset
				+ (int)Math.round((mouse - this.dragStartMouse) * maxScrollOffset(scroll) / travel),
			0,
			maxScrollOffset(scroll)
		);
		this.scrollOffsets.put(scroll.key(), offset);
		rebuild();
		return true;
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.closing) {
			return true;
		}
		if (event.button() == 0 && this.draggingScrollKey != null) {
			this.draggingScrollKey = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	private static boolean contains(final Rect rect, final double x, final double y) {
		return x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom();
	}

	private static Optional<Boolean> primitiveBoolean(final JsonElement value) {
		return value != null
				&& value.isJsonPrimitive()
				&& value.getAsJsonPrimitive().isBoolean()
			? Optional.of(value.getAsBoolean())
			: Optional.empty();
	}

	private static Optional<String> primitiveString(final JsonElement value) {
		return value != null
				&& value.isJsonPrimitive()
				&& value.getAsJsonPrimitive().isString()
			? Optional.of(value.getAsString())
			: Optional.empty();
	}

	private static Optional<String> primitiveInputText(final JsonElement value) {
		return value != null
				&& value.isJsonPrimitive()
				&& (
					value.getAsJsonPrimitive().isString()
						|| value.getAsJsonPrimitive().isNumber()
				)
			? Optional.of(value.getAsString())
			: Optional.empty();
	}

	private static boolean arrayContains(final JsonElement value, final String option) {
		if (value == null || !value.isJsonArray()) {
			return false;
		}
		for (JsonElement element : value.getAsJsonArray()) {
			if (element.isJsonPrimitive() && option.equals(element.getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static int selectionCount(final JsonElement value) {
		return value != null && value.isJsonArray() ? value.getAsJsonArray().size() : 0;
	}

	private static JsonArray toggledArray(final JsonElement value, final String option) {
		JsonArray result = new JsonArray();
		boolean removed = false;
		if (value != null && value.isJsonArray()) {
			for (JsonElement element : value.getAsJsonArray()) {
				if (!removed && element.isJsonPrimitive() && option.equals(element.getAsString())) {
					removed = true;
				} else {
					result.add(element.deepCopy());
				}
			}
		}
		if (!removed) {
			result.add(option);
		}
		return result;
	}

	private static Optional<String> translate(
		final String key,
		final List<JsonElement> arguments
	) {
		if (!Language.getInstance().has(key)) {
			return Optional.empty();
		}
		Object[] values = arguments.stream()
			.map(
				value -> value == null || value.isJsonNull()
					? ""
					: value.isJsonPrimitive() ? value.getAsString() : value.toString()
			)
			.toArray();
		return Optional.of(Component.translatable(key, values).getString());
	}

	private static String safeMessage(final RuntimeException error) {
		return "INTERNAL_RENDER_ERROR (" + error.getClass().getSimpleName() + ")";
	}

	@Override
	public void onClose() {
		if (forcedPage()) {
			return;
		}
		if (this.closing) {
			finishClose();
			return;
		}
		if (this.plan == null) {
			finishClose();
			return;
		}
		this.closing = true;
		long now = Util.getMillis();
		boolean animated = false;
		for (RenderNode node : this.plan.nodes().values()) {
			if (node.definition() == null) {
				continue;
			}
			animated |= triggerMotion(node, UiEvent.HIDE, now);
			playEventSound(node, UiEvent.HIDE, node.key());
		}
		this.pageWidgets.values().forEach(binding -> binding.widget().active = false);
		if (!animated || motionsFinished(UiEvent.HIDE, now)) {
			finishClose();
		}
	}

	private void finishClose() {
		if (!this.serverReleased && !ClientPageState.closeActivePage()) {
			this.closing = false;
			return;
		}
		TransitioningConsoleScreen.preparePopEntry(this.parent);
		this.minecraft.gui.setScreen(this.parent);
	}

	private record ToolSpec(int width, String label, String tooltip, Runnable action) {
	}

	private record WidgetBinding(
		AbstractWidget widget,
		PlacedNode placed,
		RenderNode node,
		Rect layoutBounds
	) {
	}

	private record PendingSound(Identifier sound, float volume, float pitch, long dueMillis) {
	}

	private record StyleCacheKey(String nodeKey, StyleState state, boolean interactive) {
	}

	private record ImageSize(int width, int height) {
		private static ImageSize square() {
			return new ImageSize(1, 1);
		}
	}

	private enum MotionChannel {
		LIFECYCLE,
		HOVER,
		FOCUS,
		PRESS,
		VALUE,
		OUTCOME;

		private static MotionChannel forEvent(final UiEvent event) {
			return switch (event) {
				case SHOW, HIDE -> LIFECYCLE;
				case HOVER_ENTER, HOVER_LEAVE -> HOVER;
				case FOCUS, BLUR -> FOCUS;
				case PRESS -> PRESS;
				case VALUE_CHANGE -> VALUE;
				case SUCCESS, ERROR -> OUTCOME;
			};
		}
	}

	private record MotionPlayback(
		MotionDefinition motion,
		long startedMillis,
		long sequence,
		MotionCorrection correction
	) {
	}

	private record MotionCorrection(
		double opacity,
		double translateX,
		double translateY,
		double scale,
		double clipProgress,
		int colorBase,
		OptionalInt colorOrigin,
		double progressBase,
		OptionalDouble progressDelta
	) {
		private static MotionCorrection none() {
			return new MotionCorrection(
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0,
				OptionalInt.empty(),
				0.0,
				OptionalDouble.empty()
			);
		}

		private static MotionCorrection between(
			final UiMotionRuntime.Sample origin,
			final UiMotionRuntime.Sample initial,
			final int colorBase,
			final double progressBase
		) {
			return new MotionCorrection(
				origin.opacity() - initial.opacity(),
				origin.translateX() - initial.translateX(),
				origin.translateY() - initial.translateY(),
				origin.scale() - initial.scale(),
				origin.clipProgress() - initial.clipProgress(),
				colorBase,
				UiMotionRuntime.continuityColorOrigin(
					origin.color(),
					initial.color(),
					colorBase
				),
				progressBase,
				UiMotionRuntime.continuityNumberDelta(
					origin.progressValue(),
					initial.progressValue(),
					progressBase
				)
			);
		}

		private boolean isIdentity() {
			return Math.abs(this.opacity) < 0.000001
				&& Math.abs(this.translateX) < 0.000001
				&& Math.abs(this.translateY) < 0.000001
				&& Math.abs(this.scale) < 0.000001
				&& Math.abs(this.clipProgress) < 0.000001
				&& this.colorOrigin.isEmpty()
				&& this.progressDelta.isEmpty();
		}

		private UiMotionRuntime.Sample apply(
			final UiMotionRuntime.Sample sample,
			final double remaining
		) {
			double amount = Math.clamp(remaining, 0.0, 1.0);
			OptionalInt color = UiMotionRuntime.applyColorContinuity(
				sample.color(),
				this.colorBase,
				this.colorOrigin,
				amount
			);
			OptionalDouble progress = UiMotionRuntime.applyNumberContinuity(
				sample.progressValue(),
				this.progressBase,
				this.progressDelta,
				amount
			);
			return new UiMotionRuntime.Sample(
				(float)Math.clamp(sample.opacity() + this.opacity * amount, 0.0, 1.0),
				sample.translateX() + this.translateX * amount,
				sample.translateY() + this.translateY * amount,
				Math.clamp(sample.scale() + this.scale * amount, 0.0, 8.0),
				color,
				Math.clamp(
					sample.clipProgress() + this.clipProgress * amount,
					0.0,
					1.0
				),
				progress,
				sample.finished()
			);
		}
	}

	private record NodeFrame(
		Transform transform,
		Rect bounds,
		Rect content,
		Rect clip,
		float opacity,
		OptionalInt color,
		OptionalDouble progressValue
	) {
	}

	private record MotionLayout(Map<String, NodeFrame> frames) {
		private MotionLayout {
			frames = Map.copyOf(frames);
		}
	}

	private record Scrollbar(Rect track, Rect thumb, Direction direction) {
	}
}
