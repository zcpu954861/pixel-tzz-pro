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
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.ExclusiveMutationSubmission;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.FlowSubmission;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.PageStatus;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.TerminalSubmission;
import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.OperationFeedbackText;
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
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
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
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
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
import io.github.zcpu954861.pixeltzzpro.network.payload.ExclusiveChoiceMutationC2SPayload.Operation;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.ExclusiveOptionState;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FieldSchema;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.PagePurpose;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.TerminalContext;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalIntentC2SPayload.Intent;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.PlacedNode;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageRenderProjection;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TerminalFooterLayout;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TerminalHeaderLayout;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TerminalShellViewport;
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
	private static final int LIVE_PAGE_INSET = 12;
	private static final long CAROUSEL_TRANSITION_MILLIS = 220L;
	private static final long REDUCED_CAROUSEL_TRANSITION_MILLIS = 110L;
	private static final long TERMINAL_FEEDBACK_HOLD_MILLIS = 3_200L;
	private static final long TERMINAL_FEEDBACK_FADE_MILLIS = 420L;
	private static final String TERMINAL_FOOTER_NAVIGATION_ID =
		"terminal_footer_navigation";
	private static final int MAX_PERSISTED_CAROUSEL_SELECTIONS = 256;
	private static final Map<CarouselSelectionKey, String> CAROUSEL_SELECTIONS =
		new LinkedHashMap<>();
	private static final Identifier CLAIM_HOST_OPERATION = Identifier.parse(
		"pixel-tzz-pro:host/claim"
	);
	private static final Identifier TAKEOVER_HOST_OPERATION = Identifier.parse(
		"pixel-tzz-pro:host/takeover"
	);
	private static final ActionEntry CLAIM_HOST_ACTION = new ActionEntry(
		"claim_host",
		CLAIM_HOST_OPERATION,
		"system",
		-110,
		"{\"text\":\"认领主持人\"}",
		"{\"text\":\"成为该世界唯一的全员逃走中主持人。\"}",
		"#D7B45A",
		true,
		"",
		"none",
		0,
		0,
		true
	);
	private static final ActionEntry TAKEOVER_HOST_ACTION = new ActionEntry(
		"takeover_host",
		TAKEOVER_HOST_OPERATION,
		"system",
		-100,
		"{\"text\":\"接管主持人\"}",
		"{\"text\":\"以至少 2 级 OP 权限接管离线或失去控制的主持人。\"}",
		"#E94F64",
		true,
		"",
		"none",
		0,
		0,
		true
	);
	private static final UUID FALLBACK_UUID = new UUID(0L, 0L);

	private final Screen parent;
	private final PageScreenSession session;
	private final BindingRuntime bindings = new BindingRuntime(DataDrivenPageScreen::translate);
	private final Map<String, Integer> scrollOffsets = new LinkedHashMap<>();
	private final Map<String, WidgetBinding> pageWidgets = new LinkedHashMap<>();
	private final Map<String, TerminalNavigationBinding> terminalRootNavigationWidgets =
		new LinkedHashMap<>();
	private final Set<String> terminalFooterNavigationKeys = new HashSet<>();
	private List<String> terminalRootNavigationNodeKeys = List.of();
	private final Map<String, CarouselRuntime> carouselStates = new LinkedHashMap<>();
	private final Map<String, CarouselControls> carouselControls = new LinkedHashMap<>();
	private final Map<AbstractWidget, ExclusiveChoicePresentation> exclusiveChoicePresentations =
		new IdentityHashMap<>();
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
	private String terminalFeedbackMessage = "";
	private UiEvent terminalFeedbackOutcome = UiEvent.SUCCESS;
	private long terminalFeedbackUntilMillis;
	private UUID motionInstanceId;
	private long requestSequence;
	private long motionSequence;
	private long observedRevision = Long.MIN_VALUE;
	private PageRenderProjection<ActivePage, PageStatus> observedPageProjection =
		currentPageProjection();
	private boolean pendingPlanRefresh;
	private boolean closing;
	private boolean serverReleased;
	private boolean safetyDiagnosticsExpanded;
	private long pendingActionSequence = -1L;
	private String pendingActionNodeKey = "";
	private boolean pendingActionIsTerminal;
	private long pendingTerminalChromeSequence = -1L;
	private boolean pendingTakeoverReview;
	private long pendingTakeoverSequence = -1L;
	private long pendingExclusiveMutationSequence = -1L;
	private Identifier pendingExclusiveMutationField;
	private String pendingExclusiveMutationOption = "";
	private int toolbarHeight;
	private int viewportX;
	private int viewportY;
	private int viewportWidth;
	private int viewportHeight;
	private int pageLayoutWidth;
	private int pageLayoutHeight;
	private int pageContentWidth;
	private int pageContentHeight;
	private int terminalShellX;
	private int terminalShellY;
	private int terminalShellWidth;
	private int terminalShellHeight;
	private TerminalShellViewport.Layout terminalShellViewport =
		TerminalShellViewport.fit(1, 1);
	private int terminalFooterHeight = TerminalFooterLayout.NONE_HEIGHT;
	private ConsoleButton terminalExitButton;
	private ConsoleButton terminalBackButton;
	private ConsoleButton terminalTakeoverButton;
	private double referencePageScale = 1.0;
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

	boolean isNormalTerminalScreen() {
		return isLivePageScreen()
			&& this.active != null
			&& this.active.purpose() == PagePurpose.NORMAL;
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
		List<RenderNode> narratedChildren = node.type() == NodeType.CAROUSEL
			? selectedCarouselNarrationChildren(node)
			: node.children();
		for (RenderNode child : narratedChildren) {
			if (target.size() >= 16) {
				break;
			}
			collectNarration(child, target);
		}
	}

	private List<RenderNode> selectedCarouselNarrationChildren(final RenderNode node) {
		CarouselRuntime state = this.carouselStates.get(node.key());
		if (state == null) {
			return node.children().isEmpty()
				? List.of()
				: List.of(node.children().getFirst());
		}
		return node.children()
			.stream()
			.filter(child ->
				child.definition() != null
					&& child.definition().id().filter(state.selectedId()::equals).isPresent()
			)
			.findFirst()
			.map(List::of)
			.orElse(List.of());
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
		this.terminalRootNavigationWidgets.clear();
		this.terminalFooterNavigationKeys.clear();
		this.terminalRootNavigationNodeKeys = List.of();
		this.carouselControls.clear();
		this.exclusiveChoicePresentations.clear();
		this.placedNodes.clear();
		this.resolvedStyles.clear();
		this.bindingContextCache.clear();
		this.terminalExitButton = null;
		this.terminalBackButton = null;
		this.terminalTakeoverButton = null;
		this.renderFailure = "";
		ActivePage previousActive = this.active;
		this.active = ClientPageState.activePage().orElse(null);
		boolean preserveStableTerminalMotion = shouldPreserveStableTerminalMotion(
			previousActive,
			this.active
		);
		resetMotionStateIfInstanceChanged(preserveStableTerminalMotion);
		rememberPageProjection();

		if (isTerminalLoading()) {
			this.plan = null;
			layoutTerminalLoadingWidgets();
			return;
		}
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
				pageResponsiveTier(),
				this.pageLayoutWidth,
				this.pageLayoutHeight,
				this.scrollOffsets,
				this.session.defaultFont()
			);
			indexPlaced(this.plan.layout().root());
			applyTerminalNavigationPlan(analyzeTerminalNavigation(this.plan));
			reconcileCarouselStates();
			updatePageFit();
			if (preserveStableTerminalMotion) {
				this.shownNodeKeys.retainAll(this.plan.nodes().keySet());
			}
			initializeShowMotions();
			buildPageWidgets(this.plan.layout().root());
			if (normalTerminalPage()) {
				layoutTerminalChrome();
			}
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
			int backWidth = Math.min(
				104,
				Math.max(72, ConsoleScreenViewport.REFERENCE_WIDTH - 24)
			);
			ConsoleButton back = new ConsoleButton(
				ConsoleScreenViewport.REFERENCE_WIDTH - backWidth - 12,
				ConsoleScreenViewport.REFERENCE_HEIGHT - 30,
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

	private static boolean shouldPreserveStableTerminalMotion(
		final ActivePage previous,
		final ActivePage current
	) {
		if (
			previous == null
				|| current == null
				|| previous.purpose() != PagePurpose.NORMAL
				|| current.purpose() != PagePurpose.NORMAL
				|| !previous.page().id().equals(current.page().id())
				|| !previous.page().equals(current.page())
				|| !previous.theme().equals(current.theme())
				|| !previous.fields().equals(current.fields())
		) {
			return false;
		}
		return previous.terminalContext()
			.map(TerminalContext::sessionId)
			.equals(current.terminalContext().map(TerminalContext::sessionId));
	}

	private void resetMotionStateIfInstanceChanged(final boolean preserveStableTerminalMotion) {
		UUID currentInstance = this.active == null ? null : this.active.instanceId();
		if (Objects.equals(this.motionInstanceId, currentInstance)) {
			return;
		}
		this.motionInstanceId = currentInstance;
		this.motionPlaybacks.clear();
		if (!preserveStableTerminalMotion) {
			this.shownNodeKeys.clear();
			this.lastSoundTimes.clear();
		}
		this.hoveredNodeKeys.clear();
		this.focusedNodeKeys.clear();
		this.pendingSounds.clear();
		this.motionSequence = 0L;
		this.closing = false;
		this.serverReleased = false;
		this.terminalFeedbackMessage = "";
		this.terminalFeedbackUntilMillis = 0L;
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

	private boolean isTerminalLoading() {
		if (
			this.session.previewChrome()
				|| this.active != null
				|| ClientPageState.pagePurpose() != PagePurpose.NORMAL
		) {
			return false;
		}
		return ClientPageState.pageStatus() == PageStatus.LOADING
			|| ClientPageState.activePage()
				.map(page -> page.purpose() == PagePurpose.NORMAL)
				.orElse(false);
	}

	private boolean normalTerminalPage() {
		return this.active != null && this.active.purpose() == PagePurpose.NORMAL;
	}

	private int terminalRootNavigationDefinitionCount() {
		if (
			!normalTerminalPage()
				|| this.active.terminalContext()
					.map(TerminalContext::stackDepth)
					.orElse(1) != 1
		) {
			return 0;
		}
		List<NodeDefinition> containers = directNodeChildren(this.active.page().root())
			.stream()
			.filter(node ->
				node.id().filter(TERMINAL_FOOTER_NAVIGATION_ID::equals).isPresent()
			)
			.toList();
		if (containers.size() != 1) {
			return 0;
		}
		if (!(containers.getFirst().content() instanceof ChildrenContent children)) {
			return 0;
		}
		int count = children.children().size();
		return count >= 1 && count <= TerminalFooterLayout.MAX_ROOT_NAVIGATION_ITEMS
			? count
			: 0;
	}

	private TerminalNavigationPlan analyzeTerminalNavigation(
		final RenderPlan renderPlan
	) {
		List<RenderNode> reserved = renderPlan.nodes()
			.values()
			.stream()
			.filter(node ->
				node.definition() != null
					&& node.definition()
						.id()
						.filter(TERMINAL_FOOTER_NAVIGATION_ID::equals)
						.isPresent()
			)
			.toList();
		if (reserved.isEmpty()) {
			return TerminalNavigationPlan.empty();
		}
		if (reserved.size() != 1) {
			throw new IllegalArgumentException(
				"ordinary terminal pages may declare only one terminal_footer_navigation container"
			);
		}
		RenderNode container = reserved.getFirst();
		boolean directRootChild = renderPlan.root()
			.children()
			.stream()
			.anyMatch(child -> child.key().equals(container.key()));
		if (!directRootChild) {
			throw new IllegalArgumentException(
				"terminal_footer_navigation must be a direct child of the page root"
			);
		}
		if (!(container.definition().content() instanceof ChildrenContent children)) {
			throw new IllegalArgumentException(
				"terminal_footer_navigation must directly contain registered buttons"
			);
		}
		List<NodeDefinition> registeredDefinitions = children.children();
		if (
			registeredDefinitions.isEmpty()
				|| registeredDefinitions.size()
					> TerminalFooterLayout.MAX_ROOT_NAVIGATION_ITEMS
		) {
			throw new IllegalArgumentException(
				"terminal_footer_navigation must declare between 1 and "
					+ TerminalFooterLayout.MAX_ROOT_NAVIGATION_ITEMS
					+ " buttons"
			);
		}
		for (NodeDefinition definition : registeredDefinitions) {
			if (
				!(definition.content() instanceof ButtonContent button)
					|| definition.id().filter(id -> !id.isBlank()).isEmpty()
					|| button.action().type() != ActionType.REGISTERED
					|| button.action().registeredAction().isEmpty()
			) {
				throw new IllegalArgumentException(
					"terminal_footer_navigation accepts only stable registered buttons"
				);
			}
		}
		Set<String> subtreeKeys = new HashSet<>();
		collectRenderNodeKeys(container, subtreeKeys);
		if (!normalTerminalPage()) {
			return new TerminalNavigationPlan(List.of(), Set.copyOf(subtreeKeys));
		}
		int stackDepth = this.active.terminalContext()
			.map(TerminalContext::stackDepth)
			.orElse(1);
		if (stackDepth != 1) {
			throw new IllegalArgumentException(
				"terminal_footer_navigation is only valid on an ordinary terminal root page"
			);
		}
		List<String> buttonKeys = container.children()
			.stream()
			.map(child -> {
				if (
					child.definition() == null
						|| !(child.definition().content() instanceof ButtonContent)
				) {
					throw new IllegalArgumentException(
						"visible terminal footer navigation entries must remain buttons"
					);
				}
				return child.key();
			})
			.toList();
		if (buttonKeys.size() > TerminalFooterLayout.MAX_ROOT_NAVIGATION_ITEMS) {
			throw new IllegalArgumentException(
				"visible terminal footer navigation exceeds the supported item limit"
			);
		}
		return new TerminalNavigationPlan(buttonKeys, Set.copyOf(subtreeKeys));
	}

	private void applyTerminalNavigationPlan(final TerminalNavigationPlan navigation) {
		this.terminalRootNavigationNodeKeys = navigation.buttonKeys();
		this.terminalFooterNavigationKeys.clear();
		this.terminalFooterNavigationKeys.addAll(navigation.subtreeKeys());
	}

	private static void collectRenderNodeKeys(
		final RenderNode node,
		final Set<String> target
	) {
		target.add(node.key());
		node.children().forEach(child -> collectRenderNodeKeys(child, target));
	}

	private static List<NodeDefinition> directNodeChildren(final NodeDefinition node) {
		return switch (node.content()) {
			case ChildrenContent children -> children.children();
			case SingleChildContent child -> List.of(child.child());
			case RepeatContent repeat -> List.of(repeat.template());
			default -> List.of();
		};
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
		int right = Math.max(24, ConsoleScreenViewport.REFERENCE_WIDTH - 14);
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
		int availableWidth = Math.max(1, ConsoleScreenViewport.REFERENCE_WIDTH - 32);
		int requestedWidth = switch (this.session.viewport()) {
			case COMPACT -> COMPACT_VIEWPORT_WIDTH;
			case STANDARD -> STANDARD_VIEWPORT_WIDTH;
			case WIDE -> WIDE_VIEWPORT_WIDTH;
		};
		this.viewportWidth = Math.min(availableWidth, requestedWidth);
		this.viewportX = (ConsoleScreenViewport.REFERENCE_WIDTH - this.viewportWidth) / 2;
		int contentTop = this.toolbarHeight + VIEWPORT_HEADER_HEIGHT;
		int availableHeight = Math.max(
			1,
			ConsoleScreenViewport.REFERENCE_HEIGHT
				- contentTop
				- FOOTER_HEIGHT
				- OUTER_MARGIN
		);
		int requestedHeight = switch (this.session.viewport()) {
			case COMPACT -> COMPACT_VIEWPORT_HEIGHT;
			case STANDARD -> STANDARD_VIEWPORT_HEIGHT;
			case WIDE -> WIDE_VIEWPORT_HEIGHT;
		};
		this.viewportHeight = Math.min(availableHeight, requestedHeight);
		this.viewportY = contentTop + Math.max(0, availableHeight - this.viewportHeight) / 2;
		this.pageLayoutWidth = this.viewportWidth;
		this.pageLayoutHeight = this.viewportHeight;
	}

	private ResponsiveTier pageResponsiveTier() {
		return normalTerminalPage()
			? ResponsiveTier.STANDARD
			: this.session.responsiveTier();
	}

	private void layoutLiveViewport() {
		this.toolbarHeight = 0;
		if (normalTerminalPage()) {
			this.terminalShellViewport = TerminalShellViewport.fit(this.width, this.height);
			this.terminalShellWidth = this.terminalShellViewport.width();
			this.terminalShellHeight = this.terminalShellViewport.height();
			this.terminalShellX = this.terminalShellViewport.x();
			this.terminalShellY = this.terminalShellViewport.y();
			boolean hostControl = this.active.terminalContext()
				.map(TerminalContext::takeoverAllowed)
				.orElse(false);
			int stackDepth = this.active.terminalContext()
				.map(TerminalContext::stackDepth)
				.orElse(1);
			int rootNavigationItems = terminalRootNavigationDefinitionCount();
			this.terminalFooterHeight = TerminalFooterLayout.resolve(
				0,
				0,
				TerminalShellViewport.REFERENCE_WIDTH,
				TerminalShellViewport.REFERENCE_HEIGHT,
				hostControl,
				stackDepth,
				rootNavigationItems
			).footerHeight();
			Rect pageViewport = this.terminalShellViewport.pageViewport(
				TerminalHeaderLayout.HEIGHT,
				this.terminalFooterHeight
			);
			this.viewportX = pageViewport.x();
			this.viewportY = pageViewport.y();
			this.viewportWidth = pageViewport.width();
			this.viewportHeight = pageViewport.height();
			this.pageLayoutWidth = TerminalShellViewport.REFERENCE_WIDTH
				- TerminalShellViewport.CONTENT_INSET * 2;
			this.pageLayoutHeight = TerminalShellViewport.REFERENCE_HEIGHT
				- TerminalHeaderLayout.HEIGHT
				- this.terminalFooterHeight;
			return;
		}
		ConsoleScreenViewport.Layout canvas = ConsoleScreenViewport.fit(this.width, this.height);
		Rect pageViewport = canvas.map(
			new Rect(
				LIVE_PAGE_INSET,
				LIVE_PAGE_INSET,
				ConsoleScreenViewport.REFERENCE_WIDTH - LIVE_PAGE_INSET * 2,
				ConsoleScreenViewport.REFERENCE_HEIGHT - LIVE_PAGE_INSET * 2
			)
		);
		this.viewportX = pageViewport.x();
		this.viewportY = pageViewport.y();
		this.viewportWidth = pageViewport.width();
		this.viewportHeight = pageViewport.height();
		this.pageLayoutWidth = ConsoleScreenViewport.REFERENCE_WIDTH - LIVE_PAGE_INSET * 2;
		this.pageLayoutHeight = ConsoleScreenViewport.REFERENCE_HEIGHT - LIVE_PAGE_INSET * 2;
	}

	private void layoutTerminalLoadingWidgets() {
		this.terminalShellViewport = TerminalShellViewport.fit(this.width, this.height);
		this.terminalShellWidth = this.terminalShellViewport.width();
		this.terminalShellHeight = this.terminalShellViewport.height();
		this.terminalShellX = this.terminalShellViewport.x();
		this.terminalShellY = this.terminalShellViewport.y();
		this.terminalFooterHeight = TerminalFooterLayout.NONE_HEIGHT;
	}

	private void layoutTerminalChrome() {
		TerminalContext terminal = this.active.terminalContext().orElseThrow();
		boolean hostControl = terminal.takeoverAllowed();
		TerminalFooterLayout.Layout footer = TerminalFooterLayout.resolve(
			0,
			0,
			TerminalShellViewport.REFERENCE_WIDTH,
			TerminalShellViewport.REFERENCE_HEIGHT,
			hostControl,
			terminal.stackDepth(),
			this.terminalRootNavigationNodeKeys.size()
		);
		this.terminalFooterHeight = footer.footerHeight();
		footer.exit().map(this.terminalShellViewport::map).ifPresent(exit -> {
			this.terminalExitButton = new ConsoleButton(
				exit.x(),
				exit.y(),
				exit.width(),
				exit.height(),
				Component.translatable("pixel_tzz_pro.terminal.exit"),
				button -> onClose(),
				Variant.NAVIGATION
			);
			this.terminalExitButton.setPresentationScale(
				(float)this.terminalShellViewport.scale()
			);
			this.terminalExitButton.setTooltip(
				Tooltip.create(
					Component.translatable("pixel_tzz_pro.terminal.exit.tooltip")
				)
			);
			this.addRenderableWidget(this.terminalExitButton);
		});
		footer.navigation().map(this.terminalShellViewport::map).ifPresent(navigation -> {
			this.terminalBackButton = new ConsoleButton(
				navigation.x(),
				navigation.y(),
				navigation.width(),
				navigation.height(),
				Component.translatable("pixel_tzz_pro.terminal.back"),
				button -> terminalBackOrClose(),
				Variant.NAVIGATION
			);
			this.terminalBackButton.setPresentationScale(
				(float)this.terminalShellViewport.scale()
			);
			this.terminalBackButton.active = !ClientPageState.terminalIntentPending(Intent.BACK);
			this.addRenderableWidget(this.terminalBackButton);
		});
		if (
			footer.rootNavigation().size()
				!= this.terminalRootNavigationNodeKeys.size()
		) {
			throw new IllegalStateException(
				"terminal footer navigation geometry does not match its registered buttons"
			);
		}
		for (int index = 0; index < this.terminalRootNavigationNodeKeys.size(); index++) {
			String nodeKey = this.terminalRootNavigationNodeKeys.get(index);
			RenderNode node = this.plan.nodes().get(nodeKey);
			if (
				node == null
					|| !(node.definition().content() instanceof ButtonContent content)
			) {
				throw new IllegalStateException(
					"terminal footer navigation button disappeared from the render plan"
				);
			}
			Rect referenceBounds = footer.rootNavigation().get(index);
			Rect bounds = this.terminalShellViewport.map(referenceBounds);
			ConsoleButton button = new ConsoleButton(
				bounds.x(),
				bounds.y(),
				bounds.width(),
				bounds.height(),
				node.styledText(),
				pressed -> {
					RenderNode currentNode = currentRenderNode(nodeKey, node);
					ButtonContent currentContent =
						currentNode.definition().content() instanceof ButtonContent refreshed
							? refreshed
							: content;
					triggerUiEvent(currentNode, UiEvent.PRESS, currentNode.key());
					handleAction(currentNode, currentContent.action());
				},
				buttonVariant(node, content.action()),
				eventSound(node, UiEvent.PRESS).isEmpty(),
				(state, fallback) -> buttonAppearance(
					currentRenderNode(nodeKey, node),
					state,
					fallback
				)
			);
			button.setPresentationScale((float)this.terminalShellViewport.scale());
			button.active = actionEnabled(node, content);
			updateButtonTooltip(button, node, content);
			this.terminalRootNavigationWidgets.put(
				nodeKey,
				new TerminalNavigationBinding(button, nodeKey, referenceBounds)
			);
			this.addRenderableWidget(button);
		}
		if (footer.hostControl().isPresent()) {
			boolean claim = !ClientSessionState.snapshot().hostAssigned();
			Rect host = this.terminalShellViewport.map(
				footer.hostControl().orElseThrow()
			);
			this.terminalTakeoverButton = new ConsoleButton(
				host.x(),
				host.y(),
				host.width(),
				host.height(),
				Component.translatable(
					claim
						? "pixel_tzz_pro.terminal.claim"
						: "pixel_tzz_pro.terminal.takeover"
				),
				button -> prepareHostControlReview(),
				Variant.NORMAL
			);
			this.terminalTakeoverButton.setPresentationScale(
				(float)this.terminalShellViewport.scale()
			);
			this.terminalTakeoverButton.active = !this.pendingTakeoverReview
				&& !ClientPageState.terminalSyncPending();
			this.terminalTakeoverButton.setTooltip(
				Tooltip.create(
					Component.translatable(
						claim
							? "pixel_tzz_pro.terminal.claim.tooltip"
							: "pixel_tzz_pro.terminal.takeover.tooltip"
					)
				)
			);
			this.addRenderableWidget(this.terminalTakeoverButton);
		}
	}

	private void terminalBackOrClose() {
		TerminalContext terminal = this.active == null
			? null
			: this.active.terminalContext().orElse(null);
		if (terminal == null || terminal.stackDepth() <= 1) {
			return;
		}
		TerminalSubmission submission = ClientPageState.submitTerminalBack();
		if (!submission.sent()) {
			showTerminalFeedback(submission.message(), false);
			return;
		}
		this.pendingTerminalChromeSequence = submission.requestSequence();
	}

	private void prepareHostControlReview() {
		ActionEntry action = hostControlAction();
		boolean claim = action.operationType().equals("claim_host");
		TerminalSubmission submission = ClientPageState.submitTerminalHostControl(
			action.operationId()
		);
		if (!submission.sent()) {
			String message = submission.message().isBlank()
				? claim ? "未能开始主持人认领审阅。" : "未能开始主持人接管审阅。"
				: submission.message();
			showTerminalFeedback(message, false);
			return;
		}
		this.pendingTakeoverReview = true;
		this.pendingTakeoverSequence = submission.requestSequence();
		this.lastRequestEnvelope = claim
			? "正在向服务端申请认领主持人审阅…"
			: "正在向服务端申请接管主持人审阅…";
	}

	private static ActionEntry hostControlAction() {
		boolean claim = !ClientSessionState.snapshot().hostAssigned();
		String operationType = claim ? "claim_host" : "takeover_host";
		Identifier operationId = claim ? CLAIM_HOST_OPERATION : TAKEOVER_HOST_OPERATION;
		return ClientConsoleState.snapshot()
			.stream()
			.flatMap(snapshot -> snapshot.actions().stream())
			.filter(action -> action.operationType().equals(operationType))
			.filter(action -> action.operationId().equals(operationId))
			.findFirst()
			.orElse(claim ? CLAIM_HOST_ACTION : TAKEOVER_HOST_ACTION);
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
		if (this.session.previewChrome()) {
			this.referencePageScale = 1.0;
			this.pageScale = 1.0;
			return;
		}
		if (normalTerminalPage()) {
			this.referencePageScale = 1.0;
			this.pageScale = this.terminalShellViewport.scale();
			return;
		}
		this.referencePageScale = UiLayoutEngine.fitScale(
			this.pageContentWidth,
			this.pageContentHeight,
			this.pageLayoutWidth,
			this.pageLayoutHeight
		);
		this.pageScale = this.referencePageScale * consoleCanvas().scale();
	}

	private void layoutSafetyWidgets() {
		int panelWidth = Math.max(
			1,
			Math.min(620, ConsoleScreenViewport.REFERENCE_WIDTH - 24)
		);
		int panelHeight = Math.max(
			1,
			Math.min(330, ConsoleScreenViewport.REFERENCE_HEIGHT - 24)
		);
		int x = (ConsoleScreenViewport.REFERENCE_WIDTH - panelWidth) / 2;
		int y = (ConsoleScreenViewport.REFERENCE_HEIGHT - panelHeight) / 2;
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

	private void reconcileCarouselStates() {
		Map<String, CarouselRuntime> reconciled = new LinkedHashMap<>();
		for (PlacedNode placed : this.placedNodes.values()) {
			if (placed.type() != NodeType.CAROUSEL || placed.children().isEmpty()) {
				continue;
			}
			List<CarouselChild> children = carouselChildren(placed);
			if (children.isEmpty()) {
				continue;
			}
			CarouselSelectionKey persistenceKey = carouselSelectionKey(placed);
			CarouselRuntime previous = this.carouselStates.get(placed.key());
			String selectedId = previous == null
				? rememberedCarouselSelection(persistenceKey).orElse(children.getFirst().id())
				: previous.selectedId();
			String requestedSelection = selectedId;
			if (
				children.stream().noneMatch(child -> child.id().equals(requestedSelection))
			) {
				selectedId = children.getFirst().id();
			}
			CarouselRuntime state = previous == null
				? new CarouselRuntime(selectedId)
				: previous.reconcile(selectedId, children);
			reconciled.put(placed.key(), state);
			rememberCarouselSelection(persistenceKey, state.selectedId());
		}
		this.carouselStates.clear();
		this.carouselStates.putAll(reconciled);
	}

	private void buildPageWidgets(final PlacedNode placed) {
		if (this.terminalFooterNavigationKeys.contains(placed.key())) {
			return;
		}
		RenderNode node = this.plan.nodes().get(placed.key());
		if (node != null && !node.synthetic() && node.definition() != null) {
			if (node.definition().content() instanceof ButtonContent button) {
				addButtonWidget(placed, node, button);
			} else if (node.definition().content() instanceof FieldInputContent input) {
				addFieldWidgets(placed, node, input);
			} else if (node.type() == NodeType.CAROUSEL) {
				addCarouselWidgets(placed, node);
			}
		}
		placed.children().forEach(this::buildPageWidgets);
	}

	private void addCarouselWidgets(final PlacedNode placed, final RenderNode node) {
		List<CarouselChild> children = carouselChildren(placed);
		if (children.size() <= 1) {
			return;
		}
		CarouselControlRects controls = carouselControlRects(placed);
		ConsoleButton previous = new ConsoleButton(
			screenX(controls.previous().x()),
			screenY(controls.previous().y()),
			controls.previous().width(),
			controls.previous().height(),
			Component.literal("‹"),
			button -> changeCarousel(placed.key(), -1),
			Variant.NORMAL
		);
		previous.setTooltip(Tooltip.create(Component.literal("上一份文档")));
		ConsoleButton next = new ConsoleButton(
			screenX(controls.next().x()),
			screenY(controls.next().y()),
			controls.next().width(),
			controls.next().height(),
			Component.literal("›"),
			button -> changeCarousel(placed.key(), 1),
			Variant.NORMAL
		);
		next.setTooltip(Tooltip.create(Component.literal("下一份文档")));
		addPageWidget(
			node.key() + "/carousel-previous",
			previous,
			placed,
			node
		);
		addPageWidget(node.key() + "/carousel-next", next, placed, node);
		this.carouselControls.put(
			placed.key(),
			new CarouselControls(previous, next)
		);
	}

	private static CarouselControlRects carouselControlRects(final PlacedNode placed) {
		Rect content = placed.contentBounds();
		int width = Math.min(26, Math.max(20, content.width() / 10));
		int height = Math.min(30, Math.max(22, content.height() / 5));
		int y = content.y() + Math.max(0, (content.height() - height) / 2);
		int inset = Math.min(8, Math.max(3, content.width() / 40));
		return new CarouselControlRects(
			new Rect(content.x() + inset, y, width, height),
			new Rect(
				Math.max(content.x() + inset, content.right() - inset - width),
				y,
				width,
				height
			)
		);
	}

	private void changeCarousel(final String carouselKey, final int direction) {
		PlacedNode placed = this.placedNodes.get(carouselKey);
		CarouselRuntime state = this.carouselStates.get(carouselKey);
		if (placed == null || state == null || direction == 0 || state.transitioning()) {
			return;
		}
		List<CarouselChild> children = carouselChildren(placed);
		if (children.size() <= 1) {
			return;
		}
		int current = indexOfCarouselChild(children, state.selectedId());
		int next = Math.floorMod(current + Integer.signum(direction), children.size());
		String selectedId = children.get(next).id();
		state.select(selectedId, Integer.signum(direction), Util.getMillis());
		rememberCarouselSelection(carouselSelectionKey(placed), selectedId);
		CarouselControls controls = this.carouselControls.get(carouselKey);
		if (controls != null) {
			controls.previous().active = false;
			controls.next().active = false;
		}
	}

	private static int indexOfCarouselChild(
		final List<CarouselChild> children,
		final String selectedId
	) {
		for (int index = 0; index < children.size(); index++) {
			if (children.get(index).id().equals(selectedId)) {
				return index;
			}
		}
		return 0;
	}

	private List<CarouselChild> carouselChildren(final PlacedNode placed) {
		List<CarouselChild> result = new ArrayList<>();
		for (PlacedNode child : placed.children()) {
			RenderNode node = this.plan == null ? null : this.plan.nodes().get(child.key());
			if (node == null || node.definition() == null || node.definition().id().isEmpty()) {
				continue;
			}
			result.add(new CarouselChild(node.definition().id().orElseThrow(), child));
		}
		return List.copyOf(result);
	}

	private CarouselSelectionKey carouselSelectionKey(final PlacedNode placed) {
		UUID sessionId = this.active == null
			? FALLBACK_UUID
			: this.active.terminalContext()
				.map(TerminalContext::sessionId)
				.orElse(this.active.instanceId());
		Identifier pageId = this.active == null
			? Identifier.parse("pixel-tzz-pro:missing")
			: this.active.page().id();
		RenderNode node = this.plan == null ? null : this.plan.nodes().get(placed.key());
		String carouselId = node == null || node.definition() == null
			? placed.key()
			: node.definition().id().orElse(placed.key());
		return new CarouselSelectionKey(sessionId, pageId, carouselId);
	}

	private static Optional<String> rememberedCarouselSelection(
		final CarouselSelectionKey key
	) {
		synchronized (CAROUSEL_SELECTIONS) {
			return Optional.ofNullable(CAROUSEL_SELECTIONS.get(key));
		}
	}

	private static void rememberCarouselSelection(
		final CarouselSelectionKey key,
		final String childId
	) {
		synchronized (CAROUSEL_SELECTIONS) {
			CAROUSEL_SELECTIONS.remove(key);
			CAROUSEL_SELECTIONS.put(key, childId);
			while (CAROUSEL_SELECTIONS.size() > MAX_PERSISTED_CAROUSEL_SELECTIONS) {
				CarouselSelectionKey oldest = CAROUSEL_SELECTIONS.keySet()
					.iterator()
					.next();
				CAROUSEL_SELECTIONS.remove(oldest);
			}
		}
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
				RenderNode currentNode = currentRenderNode(node.key(), node);
				ButtonContent currentContent =
					currentNode.definition().content() instanceof ButtonContent refreshed
						? refreshed
						: content;
				triggerUiEvent(currentNode, UiEvent.PRESS, currentNode.key());
				handleAction(currentNode, currentContent.action());
			},
			buttonVariant(node, content.action()),
			eventSound(node, UiEvent.PRESS).isEmpty(),
			(state, fallback) -> buttonAppearance(
				currentRenderNode(node.key(), node),
				state,
				fallback
			)
		);
		button.active = actionEnabled(node, content);
		updateButtonTooltip(button, node, content);
		addPageWidget(node.key(), button, placed, node);
	}

	private RenderNode currentRenderNode(final String key, final RenderNode fallback) {
		if (this.plan == null) {
			return fallback;
		}
		return this.plan.nodes().getOrDefault(key, fallback);
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
		if (!evaluateEnabled(node, content)) {
			return false;
		}
		if (
			normalTerminalPage()
				&& (
					content.action().type() == ActionType.REGISTERED
						|| content.action().type() == ActionType.HISTORY_DETAIL
				)
		) {
			String nodeId = node.definition().id().orElse("");
			return !nodeId.isBlank() && !ClientPageState.terminalIntentPending(nodeId);
		}
		return true;
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
		if (field.type().equals("exclusive_choice") && !this.session.previewChrome()) {
			JsonElement authoritative = field.exclusiveOptions()
				.stream()
				.filter(option ->
					option.state().equals("held_by_self")
						|| option.state().equals("locked_by_self")
				)
				.findFirst()
				.<JsonElement>map(option -> new JsonPrimitive(option.value()))
				.orElse(JsonNull.INSTANCE);
			boolean preservePendingDraft =
				ClientPageState.exclusiveChoiceMutationPending(input.field())
					&& existing != null;
			if (!preservePendingDraft) {
				existing = authoritative;
				this.session.setUiValue(nodeId, authoritative);
			}
		} else if (existing == null) {
			existing = this.session.initialFieldValue(this.active, input.field());
			if (
				existing == null
					&& field.type().equals("exclusive_choice")
			) {
				existing = field.exclusiveOptions()
					.stream()
					.filter(option ->
						option.state().equals("held_by_self")
							|| option.state().equals("locked_by_self")
					)
					.findFirst()
					.<JsonElement>map(option -> new JsonPrimitive(option.value()))
					.orElse(null);
			}
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
			case "exclusive_choice" ->
				addExclusiveChoiceField(node, placed, field, nodeId, area);
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
		int columns = PageRenderPlanBuilder.fieldChoiceColumns(field, area.width());
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

	private void addExclusiveChoiceField(
		final RenderNode node,
		final PlacedNode placed,
		final FieldSchema field,
		final String nodeId,
		final Rect area
	) {
		if (field.exclusiveOptions().isEmpty()) {
			return;
		}
		boolean interactionPending = ClientPageState.exclusiveChoiceMutationPending()
			|| ClientPageState.flowActionPending();
		int columns = PageRenderPlanBuilder.fieldChoiceColumns(field, area.width());
		int rows = Math.ceilDiv(field.exclusiveOptions().size(), columns);
		int gap = 6;
		int buttonWidth = Math.max(80, (area.width() - gap * (columns - 1)) / columns);
		int buttonHeight = Math.max(
			36,
			Math.min(48, (area.height() - gap * (rows - 1)) / rows)
		);
		Optional<String> localValue = primitiveString(this.localUiValues.get(nodeId));
		for (int index = 0; index < field.exclusiveOptions().size(); index++) {
			ExclusiveOptionState option = field.exclusiveOptions().get(index);
			boolean selected = localValue.filter(option.value()::equals).isPresent();
			boolean selectable = switch (option.state()) {
				case "available", "held_by_self", "locked_by_self" -> true;
				default -> false;
			} && !interactionPending;
			int column = index % columns;
			int row = index / columns;
			Component name = ConsoleText.parse(option.nameJson(), option.value());
			Component description = option.descriptionJson().isBlank()
				? Component.empty()
				: ConsoleText.parse(option.descriptionJson(), "");
			String status = exclusiveStatusLabel(option.state(), selected);
			String narration = name.getString()
				+ "，"
				+ status
				+ (description.getString().isBlank() ? "" : "。" + description.getString());
			ConsoleButton button = new ConsoleButton(
				screenX(area.x() + column * (buttonWidth + gap)),
				screenY(area.y() + row * (buttonHeight + gap)),
				buttonWidth,
				buttonHeight,
				Component.literal(narration),
				pressed -> {
					if (
						ClientPageState.exclusiveChoiceMutationPending()
							|| ClientPageState.flowActionPending()
					) {
						this.lastRequestEnvelope = "上一项选择仍在等待服务端确认。";
						return;
					}
					triggerUiEvent(node, UiEvent.PRESS, node.key() + "/choice-" + option.value());
					if (this.session.previewChrome()) {
						setUiValue(
							nodeId,
							selected ? JsonNull.INSTANCE : new JsonPrimitive(option.value())
						);
						triggerUiEvent(
							node,
							UiEvent.VALUE_CHANGE,
							node.key() + "/choice-" + option.value()
						);
						rebuild();
						return;
					}
					if (selected && option.state().equals("locked_by_self")) {
						this.lastRequestEnvelope =
							"已锁定的旧值不会被普通点击释放；请选择其他位置进行原子改选。";
						return;
					}
					Operation operation =
						selected && option.state().equals("held_by_self")
							? Operation.RELEASE
							: Operation.HOLD;
					Optional<String> value = operation == Operation.HOLD
						? Optional.of(option.value())
						: Optional.empty();
					ExclusiveMutationSubmission submission =
						ClientPageState.mutateExclusiveChoice(field.id(), operation, value);
					if (!submission.sent()) {
						this.lastRequestEnvelope =
							"选择未发送: " + submission.message();
						return;
					}
					setUiValue(
						nodeId,
						operation == Operation.HOLD
							? new JsonPrimitive(option.value())
							: JsonNull.INSTANCE
					);
					this.pendingExclusiveMutationSequence = submission.requestSequence();
					this.pendingExclusiveMutationField = field.id();
					this.pendingExclusiveMutationOption = option.value();
					this.lastRequestEnvelope = operation == Operation.HOLD
						? "正在向服务端预约该位置…"
						: "正在释放该临时预约…";
					triggerUiEvent(
						node,
						UiEvent.VALUE_CHANGE,
						node.key() + "/choice-" + option.value()
					);
					rebuild();
				},
				selected ? Variant.PRIMARY : Variant.NORMAL,
				eventSound(node, UiEvent.VALUE_CHANGE).isEmpty(),
				(state, fallback) -> buttonAppearance(node, state, fallback)
			);
			button.active = selectable;
			button.setRenderLabel(false);
			button.setTooltip(
				Tooltip.create(
					Component.literal(
						name.getString()
							+ (description.getString().isBlank()
								? ""
								: "\n" + description.getString())
							+ "\n状态：" + status
							+ option.occupantName()
								.map(occupant -> "\n占用者：" + occupant)
								.orElse("")
					)
				)
			);
			this.exclusiveChoicePresentations.put(
				button,
				new ExclusiveChoicePresentation(
					field.id(),
					option,
					name,
					description,
					selected
				)
			);
			addPageWidget(
				node.key() + "/field-exclusive-choice-" + index,
				button,
				placed,
				node
			);
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
			if (
				action.type() == ActionType.FLOW
					&& this.active.purpose() == PagePurpose.FORCED_FLOW
			) {
				submitFlowAction(node, action);
				return;
			}
			if (
				action.type() == ActionType.REGISTERED
					&& this.active.purpose() == PagePurpose.NORMAL
			) {
				submitTerminalAction(node, action);
				return;
			}
			if (
				action.type() == ActionType.HISTORY_DETAIL
					&& this.active.purpose() == PagePurpose.NORMAL
			) {
				submitHistoryDetail(node);
				return;
			}
			this.lastRequestEnvelope = "当前页面用途不允许该动作类型。";
			triggerActionOutcome(node, false);
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
				if (
					this.active.purpose() == PagePurpose.NORMAL
						&& this.active.terminalContext().orElseThrow().stackDepth() > 1
						&& name.equals("back")
				) {
					TerminalSubmission submission = ClientPageState.submitTerminalBack();
					if (!submission.sent()) {
						this.lastRequestEnvelope = submission.message();
						triggerActionOutcome(node, false);
						return;
					}
					this.pendingActionSequence = submission.requestSequence();
					this.pendingActionNodeKey = node.key();
					this.pendingActionIsTerminal = true;
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
		this.pendingActionIsTerminal = false;
		this.lastRequestEnvelope = "动作已发送，正在等待服务端确认…";
	}

	private void submitTerminalAction(final RenderNode node, final UiAction action) {
		String nodeId = node.definition().id().orElse("");
		Identifier actionId = action.registeredAction().orElse(null);
		if (nodeId.isBlank() || actionId == null) {
			showTerminalFeedback("终端注册动作缺少稳定节点 ID 或动作 ID。", false);
			triggerActionOutcome(node, false);
			return;
		}
		TerminalSubmission submission = ClientPageState.submitTerminalAction(nodeId, actionId);
		if (!submission.sent()) {
			showTerminalFeedback("动作未发送: " + submission.message(), false);
			triggerActionOutcome(node, false);
			return;
		}
		this.pendingActionSequence = submission.requestSequence();
		this.pendingActionNodeKey = node.key();
		this.pendingActionIsTerminal = true;
		this.lastRequestEnvelope = "动作已发送，正在等待服务端确认…";
	}

	private void submitHistoryDetail(final RenderNode node) {
		String nodeId = node.definition().id().orElse("");
		String recordKey = node.context()
			.resolve("item.id")
			.flatMap(DataDrivenPageScreen::primitiveString)
			.orElse("");
		if (nodeId.isBlank() || recordKey.isBlank()) {
			showTerminalFeedback("历史详情动作缺少稳定节点 ID 或权威记录键。", false);
			triggerActionOutcome(node, false);
			return;
		}
		TerminalSubmission submission = ClientPageState.submitTerminalHistoryDetail(
			nodeId,
			recordKey
		);
		if (!submission.sent()) {
			showTerminalFeedback("详情未打开: " + submission.message(), false);
			triggerActionOutcome(node, false);
			return;
		}
		this.pendingActionSequence = submission.requestSequence();
		this.pendingActionNodeKey = node.key();
		this.pendingActionIsTerminal = true;
		this.lastRequestEnvelope = "正在核验这条历史记录…";
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

	private void showTerminalFeedback(final String message, final boolean success) {
		String resolved = message == null ? "" : message.strip();
		this.lastRequestEnvelope = resolved;
		this.lastRequestOutcome = success ? UiEvent.SUCCESS : UiEvent.ERROR;
		if (!normalTerminalPage() || resolved.isEmpty()) {
			return;
		}
		this.terminalFeedbackMessage = resolved;
		this.terminalFeedbackOutcome = this.lastRequestOutcome;
		this.terminalFeedbackUntilMillis = Util.getMillis() + TERMINAL_FEEDBACK_HOLD_MILLIS;
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
		consumeTerminalChromeResult();
		consumeExclusiveMutationResult();
		tickTakeoverReview();
		tickTerminalActionReview();
		if (this.minecraft != null && this.minecraft.gui.screen() != this) {
			return;
		}
		if (this.observedRevision != ClientPageState.revision()) {
			if (pageProjectionChanged()) {
				if (applyTerminalBindingUpdateInPlace()) {
					return;
				}
				rebuild();
				return;
			}
			this.observedRevision = ClientPageState.revision();
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
		refreshTerminalChromeStates();
		refreshCarouselControlStates();
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
		(this.pendingActionIsTerminal
			? ClientPageState.consumeTerminalIntentResult(this.pendingActionSequence)
			: ClientPageState.consumeFlowActionResult(this.pendingActionSequence))
			.ifPresent(result -> {
				RenderNode node = this.plan == null
					? null
					: this.plan.nodes().get(this.pendingActionNodeKey);
				this.pendingActionSequence = -1L;
				this.pendingActionNodeKey = "";
				this.pendingActionIsTerminal = false;
				String feedback = OperationFeedbackText.resolve(
					result.code(),
					result.message()
				);
				showTerminalFeedback(feedback, result.success());
				if (node != null) {
					triggerActionOutcome(node, result.success());
				}
			});
	}

	private void consumeTerminalChromeResult() {
		if (this.pendingTerminalChromeSequence < 0L) {
			return;
		}
		ClientPageState.consumeTerminalIntentResult(this.pendingTerminalChromeSequence)
			.ifPresent(result -> {
				this.pendingTerminalChromeSequence = -1L;
				showTerminalFeedback(
					OperationFeedbackText.resolve(result.code(), result.message()),
					result.success()
				);
			});
	}

	private void tickTakeoverReview() {
		if (!this.pendingTakeoverReview || this.minecraft == null) {
			return;
		}
		ActionEntry action = hostControlAction();
		if (
			ClientConsoleState.confirmation()
				.filter(value -> value.operationId().equals(action.operationId()))
				.isPresent()
		) {
			this.pendingTakeoverReview = false;
			this.pendingTakeoverSequence = -1L;
			this.minecraft.gui.setScreen(new OperationConfirmationScreen(this, action));
			return;
		}
		if (this.pendingTakeoverSequence < 0L) {
			return;
		}
		ClientPageState.consumeTerminalIntentResult(this.pendingTakeoverSequence)
			.ifPresent(result -> {
				this.pendingTakeoverSequence = -1L;
				this.pendingTakeoverReview = false;
				showTerminalFeedback(
					OperationFeedbackText.resolve(result.code(), result.message()),
					result.success()
				);
			});
	}

	private void tickTerminalActionReview() {
		if (
			!this.pendingActionIsTerminal
				|| this.pendingActionSequence < 0L
				|| this.minecraft == null
		) {
			return;
		}
		ConfirmationS2CPayload confirmation = ClientConsoleState.confirmation()
			.filter(value -> value.operationType().equals("player_action"))
			.filter(value -> value.requestSequence() == this.pendingActionSequence)
			.orElse(null);
		if (confirmation == null) {
			return;
		}
		ActionEntry action = new ActionEntry(
			confirmation.operationType(),
			confirmation.operationId(),
			"terminal",
			0,
			confirmation.titleJson(),
			new JsonPrimitive(confirmation.contextSummary()).toString(),
			"#d9b85f",
			true,
			"",
			"none",
			0,
			0,
			true
		);
		this.pendingActionSequence = -1L;
		this.pendingActionNodeKey = "";
		this.pendingActionIsTerminal = false;
		this.lastRequestEnvelope = "请审阅本次操作将产生的影响。";
		this.minecraft.gui.setScreen(new OperationConfirmationScreen(this, action));
	}

	private void consumeExclusiveMutationResult() {
		if (this.pendingExclusiveMutationSequence < 0L) {
			return;
		}
		ClientPageState.consumeExclusiveChoiceMutationResult(
			this.pendingExclusiveMutationSequence
		).ifPresent(result -> {
			this.pendingExclusiveMutationSequence = -1L;
			this.pendingExclusiveMutationField = null;
			this.pendingExclusiveMutationOption = "";
			this.lastRequestEnvelope = OperationFeedbackText.resolve(
				result.code(),
				result.message()
			);
			// Recoverable failures are followed by an authoritative page bundle. Waiting for that
			// bundle avoids rebuilding the stale projection once here and once again a packet later.
			// Fatal failures already change pageStatus and are rebuilt by pageProjectionChanged().
		});
	}

	private void refreshButtonStates() {
		for (WidgetBinding binding : this.pageWidgets.values()) {
			if (
				binding.widget() instanceof ConsoleButton button
					&& binding.node().definition().content() instanceof ButtonContent content
			) {
				button.setMessage(binding.node().styledText());
				button.active = actionEnabled(binding.node(), content);
				updateButtonTooltip(button, binding.node(), content);
			}
		}
	}

	private void refreshTerminalChromeStates() {
		if (this.terminalBackButton != null) {
			this.terminalBackButton.active = !ClientPageState.terminalIntentPending(Intent.BACK);
		}
		if (this.terminalTakeoverButton != null) {
			this.terminalTakeoverButton.active = !this.pendingTakeoverReview
				&& !ClientPageState.terminalSyncPending();
		}
		for (TerminalNavigationBinding binding : this.terminalRootNavigationWidgets.values()) {
			RenderNode node = this.plan == null
				? null
				: this.plan.nodes().get(binding.nodeKey());
			if (
				node == null
					|| !(node.definition().content() instanceof ButtonContent content)
			) {
				binding.button().active = false;
				continue;
			}
			binding.button().setMessage(node.styledText());
			binding.button().active = actionEnabled(node, content);
			updateButtonTooltip(binding.button(), node, content);
		}
	}

	private void refreshCarouselControlStates() {
		long now = Util.getMillis();
		for (Map.Entry<String, CarouselControls> entry : this.carouselControls.entrySet()) {
			CarouselRuntime state = this.carouselStates.get(entry.getKey());
			PlacedNode placed = this.placedNodes.get(entry.getKey());
			int count = placed == null ? 0 : carouselChildren(placed).size();
			if (state != null) {
				state.progress(
					now,
					this.session.reducedMotion()
						? REDUCED_CAROUSEL_TRANSITION_MILLIS
						: CAROUSEL_TRANSITION_MILLIS
				);
			}
			boolean enabled = count > 1 && state != null && !state.transitioning();
			entry.getValue().previous().active = enabled;
			entry.getValue().next().active = enabled;
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
		for (TerminalNavigationBinding binding : this.terminalRootNavigationWidgets.values()) {
			if (!binding.button().visible || !binding.button().active) {
				continue;
			}
			if (binding.button().isHovered()) {
				nowHovered.add(binding.nodeKey());
			}
			if (binding.button().isFocused()) {
				nowFocused.add(binding.nodeKey());
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
		if (isTerminalLoading()) {
			renderTerminalLoading(graphics);
			super.extractRenderState(graphics, mouseX, mouseY, tickProgress);
			return;
		}
		if (isSafetyPage()) {
			ConsoleScreenViewport.Layout canvas = consoleCanvas();
			graphics.pose().pushMatrix();
			graphics.pose().translate(canvas.x(), canvas.y());
			graphics.pose().scale((float)canvas.scale(), (float)canvas.scale());
			try {
				renderSafetyPage(graphics);
				super.extractRenderState(
					graphics,
					(int)Math.floor(canvas.toReferenceX(mouseX)),
					(int)Math.floor(canvas.toReferenceY(mouseY)),
					tickProgress
				);
			} finally {
				graphics.pose().popMatrix();
			}
			return;
		}

		ConsoleScreenViewport.Layout previewCanvas = this.session.previewChrome()
			? consoleCanvas()
			: null;
		int renderMouseX = mouseX;
		int renderMouseY = mouseY;
		if (previewCanvas != null) {
			renderMouseX = (int)Math.floor(previewCanvas.toReferenceX(mouseX));
			renderMouseY = (int)Math.floor(previewCanvas.toReferenceY(mouseY));
			graphics.pose().pushMatrix();
			graphics.pose().translate(previewCanvas.x(), previewCanvas.y());
			graphics.pose().scale(
				(float)previewCanvas.scale(),
				(float)previewCanvas.scale()
			);
		}
		try {
			if (this.session.previewChrome()) {
				renderToolbarFrame(graphics);
				renderViewportFrame(graphics);
			} else if (normalTerminalPage()) {
				renderTerminalShell(graphics);
			}
			MotionLayout motionLayout = buildMotionLayout(Util.getMillis());
			this.currentMotionLayout = motionLayout;
			try {
				renderForcedViewportFrame(graphics, motionLayout);
				drawNode(graphics, this.plan.layout().root(), motionLayout);
				graphics.nextStratum();
				drawPageWidgets(
					graphics,
					renderMouseX,
					renderMouseY,
					tickProgress,
					motionLayout
				);
				drawScrollbars(graphics, motionLayout);
				if (normalTerminalPage()) {
					graphics.nextStratum();
					drawTerminalFeedback(graphics);
				}
				if (this.session.debugLayout()) {
					graphics.nextStratum();
					drawDebugOverlay(
						graphics,
						renderMouseX,
						renderMouseY,
						motionLayout
					);
				}
				if (
					this.session.previewChrome()
						&& !this.lastRequestEnvelope.isEmpty()
				) {
					graphics.nextStratum();
					drawRequestEnvelope(graphics);
				}
				if (this.session.previewChrome()) {
					renderFooter(graphics);
				}
				super.extractRenderState(
					graphics,
					renderMouseX,
					renderMouseY,
					tickProgress
				);
			} finally {
				this.currentMotionLayout = null;
			}
		} finally {
			if (previewCanvas != null) {
				graphics.pose().popMatrix();
			}
		}
	}

	private void renderTerminalLoading(final GuiGraphicsExtractor graphics) {
		layoutTerminalLoadingWidgets();
		withTerminalReferenceCanvas(
			graphics,
			() -> renderTerminalLoadingReference(graphics)
		);
	}

	private void renderTerminalLoadingReference(final GuiGraphicsExtractor graphics) {
		renderTerminalShellFrame(graphics);
		int badgeWidth = terminalSyncBadgeWidth(true, false);
		TerminalHeaderLayout.Layout header = TerminalHeaderLayout.resolve(
			0,
			0,
			TerminalShellViewport.REFERENCE_WIDTH,
			badgeWidth
		);
		drawTerminalHeaderModules(graphics, header);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.terminal.title"),
			header.brand().x() + 7,
			header.brand().y() + 7,
			SUCCESS,
			false
		);
		if (!header.compact()) {
			graphics.text(
				this.font,
				Component.translatable("pixel_tzz_pro.terminal.subtitle"),
				header.brand().x() + 7,
				header.brand().y() + 20,
				MUTED_TEXT,
				false
			);
		}
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.terminal.loading"),
			header.identity().x(),
			header.identity().y() + 9,
			MAIN_TEXT,
			false
		);
		drawTerminalSyncBadge(graphics, true, false, header.sync());
		int centerY = TerminalShellViewport.REFERENCE_HEIGHT / 2;
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.terminal.loading"),
			24,
			centerY,
			MAIN_TEXT,
			false
		);
		graphics.fill(
			24,
			centerY + 18,
			TerminalShellViewport.REFERENCE_WIDTH - 24,
			centerY + 20,
			withAlpha(INFO_CYAN, 90)
		);
	}

	private void renderTerminalShell(final GuiGraphicsExtractor graphics) {
		withTerminalReferenceCanvas(
			graphics,
			() -> renderTerminalShellReference(graphics)
		);
	}

	private void renderTerminalShellReference(final GuiGraphicsExtractor graphics) {
		renderTerminalShellFrame(graphics);
		boolean syncPending = ClientPageState.terminalSyncPending()
			|| this.pendingTakeoverReview;
		boolean synchronizedState = ClientPageState.pageStatus() == PageStatus.READY;
		TerminalHeaderLayout.Layout header = TerminalHeaderLayout.resolve(
			0,
			0,
			TerminalShellViewport.REFERENCE_WIDTH,
			terminalSyncBadgeWidth(syncPending, synchronizedState)
		);
		drawTerminalHeaderModules(graphics, header);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.terminal.title"),
			header.brand().x() + 7,
			header.brand().y() + 7,
			SUCCESS,
			false
		);
		if (!header.compact()) {
			graphics.text(
				this.font,
				Component.translatable("pixel_tzz_pro.terminal.subtitle"),
				header.brand().x() + 7,
				header.brand().y() + 20,
				MUTED_TEXT,
				false
			);
		}
		drawTerminalViewerIdentity(graphics, header);
		if (header.page().width() >= 8) {
			String pageTitle = this.active.page().title().plainText();
			if (header.page().width() >= 78) {
				graphics.text(
					this.font,
					Component.translatable("pixel_tzz_pro.terminal.current_view"),
					header.page().x() + 9,
					header.page().y() + 6,
					withAlpha(INFO_CYAN, 150),
					false
				);
			}
			graphics.text(
				this.font,
				PlayerIdentityRenderer.ellipsize(
					this.font,
					pageTitle,
					Math.max(1, header.page().width() - 18)
				),
				header.page().x() + 9,
				header.page().y() + 20,
				0xFFE5F5F6,
				false
			);
		}
		drawTerminalSyncBadge(
			graphics,
			syncPending,
			synchronizedState,
			header.sync()
		);
	}

	private void withTerminalReferenceCanvas(
		final GuiGraphicsExtractor graphics,
		final Runnable renderer
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.terminalShellX, this.terminalShellY);
		graphics.pose().scale(
			(float)this.terminalShellViewport.scale(),
			(float)this.terminalShellViewport.scale()
		);
		try {
			renderer.run();
		} finally {
			graphics.pose().popMatrix();
		}
	}

	/**
	 * Gives every ordinary-player terminal page the same authored header hierarchy without
	 * requiring a data pack to repeat decorative chrome.
	 *
	 * <p>The header uses one continuous instrument band. Brand and identity stay visually quiet;
	 * only the current-page plate and synchronization badge receive contained frames. This avoids
	 * a collection of unrelated short rails that reads like a layout debug overlay.</p>
	 */
	private void drawTerminalHeaderModules(
		final GuiGraphicsExtractor graphics,
		final TerminalHeaderLayout.Layout header
	) {
		Rect brand = header.brand();
		if (brand.width() >= 2 && brand.height() >= 2) {
			graphics.fill(
				brand.x(),
				brand.y(),
				Math.min(brand.right(), brand.x() + 2),
				brand.bottom(),
				withAlpha(BRAND_GOLD, 204)
			);
		}

		Rect page = header.page();
		if (page.width() >= 2 && page.height() >= 2) {
			graphics.fill(page.x(), page.y() + 2, page.right(), page.bottom() - 2, 0x8F182933);
			graphics.fill(
				page.x(),
				page.y() + 2,
				Math.min(page.right(), page.x() + 3),
				page.bottom() - 2,
				withAlpha(INFO_CYAN, 190)
			);
		}
	}

	private void drawTerminalViewerIdentity(
		final GuiGraphicsExtractor graphics,
		final TerminalHeaderLayout.Layout header
	) {
		UUID viewerId = terminalBindingString("viewer.uuid")
			.flatMap(DataDrivenPageScreen::parseUuid)
			.orElse(FALLBACK_UUID);
		boolean online = terminalBindingBoolean("viewer.online").orElse(true);
		Rect avatar = header.avatar();
		if (avatar.width() >= 8) {
			PlayerIdentityRenderer.drawHead(
				graphics,
				viewerId,
				online,
				avatar.x(),
				avatar.y(),
				Math.min(avatar.width(), avatar.height())
			);
		}
		Rect identity = header.identity();
		if (identity.width() < 8) {
			return;
		}
		String name = terminalBindingString("viewer.name").orElse("玩家");
		int identityTop = avatar.y() + Math.max(0, (avatar.height() - 22) / 2);
		graphics.text(
			this.font,
			PlayerIdentityRenderer.ellipsize(this.font, name, identity.width()),
			identity.x(),
			identityTop,
			MAIN_TEXT,
			true
		);
		List<String> status = new ArrayList<>(2);
		terminalBindingString("viewer.role_name")
			.filter(value -> !value.isBlank())
			.ifPresent(status::add);
		terminalBindingString("viewer.life_state_name")
			.filter(value -> !value.isBlank())
			.filter(value -> status.stream().noneMatch(value::equals))
			.ifPresent(status::add);
		if (status.isEmpty()) {
			boolean initialized = terminalBindingBoolean("viewer.initialized").orElse(false);
			status.add(
				Component.translatable(
					initialized
						? "pixel_tzz_pro.terminal.initialized"
						: "pixel_tzz_pro.terminal.uninitialized"
				).getString()
			);
		}
		String statusText = String.join(" · ", status);
		graphics.text(
			this.font,
			PlayerIdentityRenderer.ellipsize(this.font, statusText, identity.width()),
			identity.x(),
			identityTop + 13,
			terminalIdentityColor(statusText),
			false
		);
	}

	private int terminalIdentityColor(final String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		if (
			normalized.contains("猎人")
				|| normalized.contains("死亡")
				|| normalized.contains("淘汰")
				|| normalized.contains("hunter")
				|| normalized.contains("dead")
		) {
			return DANGER;
		}
		if (
			normalized.contains("逃走者")
				|| normalized.contains("存活")
				|| normalized.contains("runner")
				|| normalized.contains("alive")
		) {
			return SUCCESS;
		}
		if (normalized.contains("旁观") || normalized.contains("spectator")) {
			return MUTED_TEXT;
		}
		return INFO_CYAN;
	}

	private Optional<String> terminalBindingString(final String path) {
		if (this.active == null) {
			return Optional.empty();
		}
		return this.active.serverContext()
			.resolve(path)
			.filter(JsonElement::isJsonPrimitive)
			.map(JsonElement::getAsString);
	}

	private Optional<Boolean> terminalBindingBoolean(final String path) {
		if (this.active == null) {
			return Optional.empty();
		}
		return this.active.serverContext()
			.resolve(path)
			.filter(JsonElement::isJsonPrimitive)
			.map(JsonElement::getAsBoolean);
	}

	private static Optional<UUID> parseUuid(final String value) {
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException error) {
			return Optional.empty();
		}
	}

	private void renderTerminalShellFrame(final GuiGraphicsExtractor graphics) {
		int x = 0;
		int y = 0;
		int width = TerminalShellViewport.REFERENCE_WIDTH;
		int height = TerminalShellViewport.REFERENCE_HEIGHT;
		int right = width;
		int bottom = height;

		graphics.fill(x + 5, y + 6, right + 5, bottom + 6, 0x54000000);
		graphics.outline(
			x - 2,
			y - 2,
			width + 4,
			height + 4,
			withAlpha(0xFF0A1118, 232)
		);
		graphics.fill(
			x,
			y,
			right,
			bottom,
			PANEL
		);
		graphics.outline(
			x,
			y,
			width,
			height,
			withAlpha(SURFACE_BORDER, 238)
		);
		if (width > 10 && height > 10) {
			graphics.outline(
				x + 3,
				y + 3,
				width - 6,
				height - 6,
				withAlpha(0xFF233845, 178)
			);
		}
		drawTerminalShellCorners(graphics, x, y, right, bottom);

		int headerBottom = Math.min(bottom - 3, y + TerminalHeaderLayout.HEIGHT - 3);
		if (width > 8 && headerBottom > y + 4) {
			graphics.fill(x + 4, y + 4, right - 4, headerBottom, 0xE9111C24);
		}
		int headerRailY = Math.min(bottom - 2, y + TerminalHeaderLayout.HEIGHT - 4);
		if (right - x > 12 && headerRailY >= y) {
			graphics.fill(x + 6, headerRailY, right - 6, headerRailY + 2, 0xE30A141B);
			graphics.fill(
				x + 12,
				headerRailY,
				right - 12,
				headerRailY + 1,
				withAlpha(INFO_CYAN, 152)
			);
		}

		if (this.terminalFooterHeight > 0) {
			int footerY = bottom - this.terminalFooterHeight;
			graphics.fill(
				x + 3,
				footerY - 1,
				right - 3,
				footerY + 2,
				0xE60D171F
			);
			graphics.fill(
				x + 12,
				footerY,
				right - 12,
				footerY + 1,
				withAlpha(SURFACE_BORDER, 188)
			);
		}
	}

	private static void drawTerminalShellCorners(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int right,
		final int bottom
	) {
		int width = right - x;
		int height = bottom - y;
		if (width < 4 || height < 4) {
			return;
		}
		int horizontal = Math.max(4, Math.min(12, width / 6));
		int vertical = Math.max(4, Math.min(9, height / 8));
		int gold = withAlpha(BRAND_GOLD, 202);
		int cyan = withAlpha(INFO_CYAN, 174);
		graphics.fill(x, y, Math.min(right, x + horizontal), y + 2, gold);
		graphics.fill(x, y, x + 2, Math.min(bottom, y + vertical), gold);
		graphics.fill(Math.max(x, right - horizontal), bottom - 2, right, bottom, cyan);
		graphics.fill(right - 2, Math.max(y, bottom - vertical), right, bottom, cyan);
	}

	private int terminalSyncBadgeWidth(
		final boolean pending,
		final boolean synchronizedState
	) {
		String key = pending
			? "pixel_tzz_pro.terminal.syncing"
			: synchronizedState
				? "pixel_tzz_pro.terminal.synced"
				: "pixel_tzz_pro.terminal.unsynced";
		return this.font.width(Component.translatable(key).getString()) + 23;
	}

	private void drawTerminalSyncBadge(
		final GuiGraphicsExtractor graphics,
		final boolean pending,
		final boolean synchronizedState,
		final Rect bounds
	) {
		String key = pending
			? "pixel_tzz_pro.terminal.syncing"
			: synchronizedState
				? "pixel_tzz_pro.terminal.synced"
				: "pixel_tzz_pro.terminal.unsynced";
		String label = Component.translatable(key).getString();
		int color = pending ? WARNING : synchronizedState ? SUCCESS : DANGER;
		int width = bounds.width();
		int right = bounds.x() + bounds.width();
		int x = bounds.x();
		int y = bounds.y();
		graphics.fill(x, y, right, y + 17, 0xD5202B35);
		graphics.outline(x, y, width, 17, withAlpha(color, 190));
		graphics.fill(x + 6, y + 6, x + 11, y + 11, color);
		graphics.text(this.font, label, x + 15, y + 5, color, false);
	}

	private void drawTerminalFeedback(final GuiGraphicsExtractor graphics) {
		withTerminalReferenceCanvas(
			graphics,
			() -> drawTerminalFeedbackReference(graphics)
		);
	}

	private void drawTerminalFeedbackReference(final GuiGraphicsExtractor graphics) {
		long remaining = this.terminalFeedbackUntilMillis - Util.getMillis();
		if (remaining <= 0L || this.terminalFeedbackMessage.isBlank()) {
			return;
		}
		double fade = remaining >= TERMINAL_FEEDBACK_FADE_MILLIS
			? 1.0
			: Math.clamp(remaining / (double)TERMINAL_FEEDBACK_FADE_MILLIS, 0.0, 1.0);
		int accent = this.terminalFeedbackOutcome == UiEvent.ERROR ? DANGER : SUCCESS;
		TerminalContext terminal = this.active.terminalContext().orElseThrow();
		TerminalFooterLayout.Layout footer = TerminalFooterLayout.resolve(
			0,
			0,
			TerminalShellViewport.REFERENCE_WIDTH,
			TerminalShellViewport.REFERENCE_HEIGHT,
			terminal.takeoverAllowed(),
			terminal.stackDepth(),
			this.terminalRootNavigationNodeKeys.size()
		);
		int height = 24;
		int minimumWidth = 120;
		int availableLeft = TerminalFooterLayout.SAFE_INSET;
		int availableRight =
			TerminalShellViewport.REFERENCE_WIDTH - TerminalFooterLayout.SAFE_INSET;
		int y = -1;
		if (terminal.stackDepth() > 1 && footer.navigation().isPresent()) {
			Rect back = footer.navigation().orElseThrow();
			int footerFeedbackRight = back.x() - 8;
			if (footerFeedbackRight - availableLeft >= minimumWidth) {
				availableRight = footerFeedbackRight;
				y = back.y();
			}
		}
		if (y < 0) {
			int contentBottom = TerminalShellViewport.REFERENCE_HEIGHT
				- footer.footerHeight();
			int minimumY = TerminalHeaderLayout.HEIGHT + 6;
			int maximumY = Math.max(
				minimumY,
				TerminalShellViewport.REFERENCE_HEIGHT - height - 4
			);
			y = Math.clamp(contentBottom - height - 8, minimumY, maximumY);
		}
		int available = Math.max(1, availableRight - availableLeft);
		String text = PlayerIdentityRenderer.ellipsize(
			this.font,
			this.terminalFeedbackMessage,
			Math.max(8, Math.min(360, available - 32))
		);
		int width = Math.max(
			1,
			Math.min(available, Math.max(minimumWidth, this.font.width(text) + 32))
		);
		int x = availableLeft + Math.max(0, available - width) / 2;
		int surfaceAlpha = (int)Math.round(232.0 * fade);
		int borderAlpha = (int)Math.round(210.0 * fade);
		int textAlpha = (int)Math.round(255.0 * fade);
		graphics.fill(x, y, x + width, y + height, withAlpha(SURFACE_RAISED, surfaceAlpha));
		graphics.outline(x, y, width, height, withAlpha(accent, borderAlpha));
		graphics.fill(x + 1, y + 1, x + 4, y + height - 1, withAlpha(accent, textAlpha));
		graphics.text(
			this.font,
			text,
			x + 12,
			y + 8,
			withAlpha(MAIN_TEXT, textAlpha),
			false
		);
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
		int background = withOpacity(
			styleColor(
				rootNode,
				"background_color",
				this.session.highContrast() ? 0xFF020406 : SURFACE
			),
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
		graphics.fill(x, y, right, bottom, background);
		graphics.outline(x, y, right - x, bottom - y, border);
		graphics.fill(x, y, Math.min(right, x + 4), bottom, info);
		graphics.fill(Math.min(right, x + 4), y, right, Math.min(bottom, y + 2), brand);
	}

	private void renderToolbarFrame(final GuiGraphicsExtractor graphics) {
		graphics.fill(
			OUTER_MARGIN,
			OUTER_MARGIN,
			ConsoleScreenViewport.REFERENCE_WIDTH - OUTER_MARGIN,
			this.toolbarHeight,
			PANEL
		);
		graphics.outline(
			OUTER_MARGIN,
			OUTER_MARGIN,
			ConsoleScreenViewport.REFERENCE_WIDTH - OUTER_MARGIN * 2,
			this.toolbarHeight - OUTER_MARGIN,
			PANEL_BORDER
		);
		graphics.fill(OUTER_MARGIN, OUTER_MARGIN, OUTER_MARGIN + 3, this.toolbarHeight, BRAND_GOLD);
		graphics.fill(
			OUTER_MARGIN + 3,
			OUTER_MARGIN,
			ConsoleScreenViewport.REFERENCE_WIDTH - OUTER_MARGIN,
			OUTER_MARGIN + 1,
			INFO_CYAN
		);
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
			case CAROUSEL -> {
				if (node == this.plan.root()) {
					int background = motionColor.orElse(
						styleColor(node, "background_color", PANEL)
					);
					graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), withOpacity(background, opacity));
				}
				drawCarouselIndicators(graphics, placed, node, content, opacity);
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
			case "portal" -> drawPortalFrame(
				graphics,
				bounds,
				background,
				border,
				accent,
				borderWidth,
				detailSize,
				opacity
			);
			case "manual" -> drawManualFrame(
				graphics,
				bounds,
				background,
				border,
				accent,
				borderWidth,
				detailSize,
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
		).orElse(
			frameStyle.equals("plain")
					|| frameStyle.equals("target")
					|| frameStyle.equals("portal")
					|| frameStyle.equals("manual")
				? "none"
				: "left"
		);
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
			FormattedText value = textNodeComponent(node, node.text());
			if (
				text.overflow() == TextOverflow.ELLIPSIS
					&& this.font.width(value) > content.width()
			) {
				Component ellipsis = textNodeComponent(node, "…");
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
			value -> this.font.width(textNodeComponent(node, value))
		);
		int maximum = text.maximumLines().orElse(lines.size());
		int visible = Math.min(maximum, Math.min(lines.size(), Math.max(1, content.height() / this.font.lineHeight)));
		for (int index = 0; index < visible; index++) {
			FormattedCharSequence line = Language.getInstance()
				.getVisualOrder(textNodeComponent(node, lines.get(index)));
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
			Component ellipsis = textNodeComponent(node, "…");
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
		if (
			!(node.definition().content() instanceof ProgressContent progress)
				|| content.width() <= 0
				|| content.height() <= 0
		) {
			return;
		}
		double value = frame.progressValue()
			.orElseGet(() -> evaluateNumber(progress.value(), node.context()).orElse(0.0));
		double minimum = evaluateNumber(progress.minimum(), node.context()).orElse(0.0);
		double maximum = evaluateNumber(progress.maximum(), node.context()).orElse(0.0);
		int requestedBarY = node.text().isEmpty()
			? content.y()
			: content.y() + this.font.lineHeight + 3;
		int barY = Math.clamp(requestedBarY, content.y(), content.bottom());
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
		int barHeight = Math.min(12, content.bottom() - barY);
		if (barHeight <= 0) {
			return;
		}
		int barBottom = barY + barHeight;
		int track = withOpacity(styleColor(node, "track_color", 0xFF17212B), opacity);
		int border = styleColor(node, "border_color", SURFACE_BORDER);
		int fill = withOpacity(
			(this.session.highContrast() ? OptionalInt.empty() : frame.color())
				.orElse(styleColor(node, "fill_color", BRAND_GOLD)),
			opacity
		);
		graphics.fill(content.x(), barY, content.right(), barBottom, track);
		graphics.outline(
			content.x(),
			barY,
			content.width(),
			barHeight,
			withOpacity(border, opacity)
		);
		if (maximum > minimum) {
			double ratio = Math.clamp((value - minimum) / (maximum - minimum), 0.0, 1.0);
			int innerLeft = Math.min(content.right(), content.x() + 1);
			int innerRight = Math.max(innerLeft, content.right() - 1);
			int innerTop = Math.min(barBottom, barY + 1);
			int innerBottom = Math.max(innerTop, barBottom - 1);
			int innerWidth = innerRight - innerLeft;
			int filled = (int)Math.round(innerWidth * ratio);
			if (filled > 0 && innerBottom > innerTop) {
				graphics.fill(
					innerLeft,
					innerTop,
					Math.min(innerRight, innerLeft + filled),
					innerBottom,
					fill
				);
			}
			int segments = Math.clamp(
				Math.round(styleNumber(node, "segment_count", 1.0F)),
				1,
				64
			);
			int separator = withOpacity(border, opacity * 0.72F);
			for (
				int index = 1;
				index < segments && innerWidth > 1 && innerBottom > innerTop;
				index++
			) {
				int separatorX = Math.clamp(
					innerLeft
						+ (int)Math.round(innerWidth * index / (double)segments),
					innerLeft,
					innerRight - 1
				);
				graphics.fill(
					separatorX,
					innerTop,
					Math.min(innerRight, separatorX + 1),
					innerBottom,
					separator
				);
			}
			if (
				filled > 0
					&& filled < innerWidth
					&& innerBottom > innerTop
			) {
				int markerX = Math.clamp(
					innerLeft + filled,
					innerLeft,
					innerRight - 1
				);
				int marker = styleColor(node, "accent_color", BRAND_GOLD);
				graphics.fill(
					Math.max(innerLeft, markerX - 1),
					innerTop,
					Math.min(innerRight, markerX + 2),
					innerBottom,
					withOpacity(marker, opacity * 0.30F)
				);
				graphics.fill(
					markerX,
					innerTop,
					Math.min(innerRight, markerX + 1),
					innerBottom,
					withOpacity(marker, opacity)
				);
			}
		} else if (barHeight >= this.font.lineHeight && content.width() > 4) {
			FormattedText unavailable = this.font.substrByWidth(
				styledText(node, "暂无进度"),
				content.width() - 4
			);
			graphics.text(
				this.font,
				Language.getInstance().getVisualOrder(unavailable),
				content.x() + 4,
				barY + (barHeight - this.font.lineHeight) / 2,
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
			boolean showRequirement = field.required();
			boolean satisfied = !showRequirement || fieldInputSatisfied(node, field);
			String requirement = satisfied ? "已选择" : "必选";
			int requirementColor = satisfied ? SUCCESS : WARNING;
			int requirementWidth = showRequirement ? this.font.width(requirement) + 12 : 0;
			int labelWidth = Math.max(
				1,
				content.width() - (showRequirement ? requirementWidth + 7 : 0)
			);
			graphics.text(
				this.font,
				styledText(node, this.font.plainSubstrByWidth(field.name(), labelWidth)),
				content.x(),
				y,
				withOpacity(
					motionColor.orElse(styleColor(node, "text_color", MAIN_TEXT)),
					opacity
				),
				false
			);
			if (showRequirement) {
				int badgeX = content.right() - requirementWidth;
				graphics.fill(
					badgeX,
					y - 2,
					content.right(),
					y + 11,
					withOpacity(withAlpha(requirementColor, 22), opacity)
				);
				graphics.outline(
					badgeX,
					y - 2,
					requirementWidth,
					13,
					withOpacity(withAlpha(requirementColor, 138), opacity)
				);
				graphics.text(
					this.font,
					Component.literal(requirement),
					badgeX + 6,
					y + 2,
					withOpacity(requirementColor, opacity),
					false
				);
			}
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
				ExclusiveChoicePresentation choice = this.exclusiveChoicePresentations.get(widget);
				if (choice != null) {
					drawExclusiveChoiceContent(
						graphics,
						widget,
						choice,
						frame.transform().scale()
					);
				}
			} finally {
				graphics.disableScissor();
			}
		}
	}

	/**
	 * Network pending/result changes share the state revision but do not alter the rendered page.
	 * Keeping this projection separate prevents a short request from replaying every widget's show
	 * animation. A new page bundle, occupancy update, resource state, or safety error still rebuilds.
	 */
	private boolean pageProjectionChanged() {
		return this.observedPageProjection.differsFrom(currentPageProjection());
	}

	/**
	 * Applies a binding-only NORMAL terminal refresh without reconstructing interactive widgets.
	 *
	 * <p>Text, progress, and other painted content use the replacement render plan immediately.
	 * Existing buttons and inputs keep their hover/focus/motion state when the page definition,
	 * terminal session, node keys and widget types remain stable. A same-page history selection may
	 * still receive a fresh server page instance to invalidate delayed requests; button callbacks
	 * resolve their current render node at click time, so retaining those widgets cannot submit the
	 * previous record context. Binding text is allowed to change layout geometry: every surviving
	 * widget receives a freshly derived layout rectangle and is moved through the same motion
	 * transform as painted nodes. A structural repeat/list, node-type, or widget-type change returns
	 * {@code false} so the normal rebuild path can create or remove the required controls without
	 * replaying the stable page's enter animation.
	 */
	private boolean applyTerminalBindingUpdateInPlace() {
		ActivePage current = ClientPageState.activePage().orElse(null);
		/*
		 * A Binding Delta or same-page detail selection may replace serverContext/stateRevision and
		 * the server page instance. Keeping the terminal session, page, theme, generation, and field
		 * schema exact guarantees that each retained widget's variant and field presentation still
		 * describe the same control.
		 */
		if (
			current == null
				|| this.active == null
				|| this.plan == null
				|| current.purpose() != PagePurpose.NORMAL
				|| ClientPageState.pageStatus() != PageStatus.READY
				|| current.generation() != this.active.generation()
				|| !current.page().id().equals(this.active.page().id())
				|| !current.page().equals(this.active.page())
				|| !current.theme().equals(this.active.theme())
				|| !current.fields().equals(this.active.fields())
				|| !current.terminalContext()
					.map(TerminalContext::sessionId)
					.equals(
						this.active.terminalContext().map(TerminalContext::sessionId)
					)
		) {
			return false;
		}
		this.active = current;
		this.motionInstanceId = current.instanceId();
		this.bindingContextCache.clear();
		final RenderPlan replacement;
		try {
			replacement = PageRenderPlanBuilder.build(
				this.font,
				this.active,
				contextWithLocalUi(this.session.context(this.active)),
				pageResponsiveTier(),
				this.pageLayoutWidth,
				this.pageLayoutHeight,
				this.scrollOffsets,
				this.session.defaultFont()
			);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"Could not apply an in-place player-terminal binding refresh for {}",
				current.page().id(),
				error
			);
			return false;
		}
		if (!replacement.nodes().keySet().equals(this.plan.nodes().keySet())) {
			return false;
		}
		Map<String, PlacedNode> replacementPlaced = new LinkedHashMap<>();
		indexPlaced(replacement.layout().root(), replacementPlaced);
		if (!replacementPlaced.keySet().equals(this.placedNodes.keySet())) {
			return false;
		}
		for (Map.Entry<String, RenderNode> entry : this.plan.nodes().entrySet()) {
			RenderNode next = replacement.nodes().get(entry.getKey());
			if (next == null || entry.getValue().type() != next.type()) {
				return false;
			}
		}
		for (Map.Entry<String, PlacedNode> entry : this.placedNodes.entrySet()) {
			PlacedNode next = replacementPlaced.get(entry.getKey());
			if (next == null || entry.getValue().type() != next.type()) {
				return false;
			}
		}
		final TerminalNavigationPlan replacementNavigation;
		try {
			replacementNavigation = analyzeTerminalNavigation(replacement);
		} catch (RuntimeException error) {
			return false;
		}
		if (
			!replacementNavigation.buttonKeys()
				.equals(this.terminalRootNavigationNodeKeys)
		) {
			return false;
		}
		Map<String, WidgetLayout> replacementWidgets = widgetLayouts(
			replacement,
			replacementPlaced,
			replacementNavigation.subtreeKeys()
		);
		if (!replacementWidgets.keySet().equals(this.pageWidgets.keySet())) {
			return false;
		}
		Map<String, WidgetBinding> refreshedBindings = new LinkedHashMap<>();
		for (Map.Entry<String, WidgetBinding> entry : this.pageWidgets.entrySet()) {
			WidgetBinding previous = entry.getValue();
			WidgetLayout layout = replacementWidgets.get(entry.getKey());
			if (
				layout == null
					|| !layout.kind().matches(previous.widget())
					|| !layout.node().key().equals(previous.node().key())
			) {
				return false;
			}
			refreshedBindings.put(
				entry.getKey(),
				new WidgetBinding(
					previous.widget(),
					layout.placed(),
					layout.node(),
					layout.bounds()
				)
			);
		}
		this.plan = replacement;
		this.placedNodes.clear();
		this.placedNodes.putAll(replacementPlaced);
		this.pageWidgets.clear();
		this.pageWidgets.putAll(refreshedBindings);
		applyTerminalNavigationPlan(replacementNavigation);
		updatePageFit();
		refreshButtonStates();
		refreshTerminalChromeStates();
		synchronizeWidgetBounds(buildMotionLayout(Util.getMillis()));
		rememberPageProjection();
		return true;
	}

	private static void indexPlaced(
		final PlacedNode placed,
		final Map<String, PlacedNode> target
	) {
		target.put(placed.key(), placed);
		placed.children().forEach(child -> indexPlaced(child, target));
	}

	private Map<String, WidgetLayout> widgetLayouts(
		final RenderPlan renderPlan,
		final Map<String, PlacedNode> placed,
		final Set<String> excludedKeys
	) {
		Map<String, WidgetLayout> result = new LinkedHashMap<>();
		for (PlacedNode candidate : placed.values()) {
			if (excludedKeys.contains(candidate.key())) {
				continue;
			}
			RenderNode node = renderPlan.nodes().get(candidate.key());
			if (node == null || node.synthetic() || node.definition() == null) {
				continue;
			}
			if (node.definition().content() instanceof ButtonContent) {
				Rect content = candidate.contentBounds();
				result.put(
					node.key(),
					new WidgetLayout(
						WidgetKind.BUTTON,
						candidate,
						node,
						new Rect(
							content.x(),
							content.y(),
							Math.max(1, content.width()),
							Math.max(20, content.height())
						)
					)
				);
				continue;
			}
			if (node.type() == NodeType.CAROUSEL && candidate.children().size() > 1) {
				CarouselControlRects controls = carouselControlRects(candidate);
				result.put(
					node.key() + "/carousel-previous",
					new WidgetLayout(
						WidgetKind.BUTTON,
						candidate,
						node,
						controls.previous()
					)
				);
				result.put(
					node.key() + "/carousel-next",
					new WidgetLayout(
						WidgetKind.BUTTON,
						candidate,
						node,
						controls.next()
					)
				);
				continue;
			}
			if (!(node.definition().content() instanceof FieldInputContent input)) {
				continue;
			}
			FieldSchema field = this.active.fields().get(input.field());
			if (field == null || node.definition().id().isEmpty()) {
				continue;
			}
			Rect area = fieldControlArea(candidate, input, field);
			switch (field.type()) {
				case "boolean" -> result.put(
					node.key() + "/field-boolean",
					new WidgetLayout(
						WidgetKind.BUTTON,
						candidate,
						node,
						new Rect(
							area.x(),
							area.y(),
							Math.max(1, area.width()),
							Math.min(24, Math.max(20, area.height()))
						)
					)
				);
				case "single_choice", "multi_choice" ->
					addChoiceWidgetLayouts(result, candidate, node, field, area, false);
				case "exclusive_choice" ->
					addChoiceWidgetLayouts(result, candidate, node, field, area, true);
				case "integer", "string", "identifier" -> result.put(
					node.key() + "/field-edit",
					new WidgetLayout(
						WidgetKind.EDIT_BOX,
						candidate,
						node,
						new Rect(
							area.x(),
							area.y(),
							Math.max(1, area.width()),
							Math.min(24, Math.max(20, area.height()))
						)
					)
				);
				default -> {
					// The definition compiler prevents unsupported field controls.
				}
			}
		}
		return result;
	}

	private static void addChoiceWidgetLayouts(
		final Map<String, WidgetLayout> target,
		final PlacedNode placed,
		final RenderNode node,
		final FieldSchema field,
		final Rect area,
		final boolean exclusive
	) {
		int count = exclusive ? field.exclusiveOptions().size() : field.options().size();
		if (count == 0) {
			return;
		}
		int columns = PageRenderPlanBuilder.fieldChoiceColumns(field, area.width());
		int rows = Math.ceilDiv(count, columns);
		int gap = exclusive ? 6 : 4;
		int buttonWidth = exclusive
			? Math.max(80, (area.width() - gap * (columns - 1)) / columns)
			: Math.max(28, (area.width() - gap * (columns - 1)) / columns);
		int buttonHeight = exclusive
			? Math.max(36, Math.min(48, (area.height() - gap * (rows - 1)) / rows))
			: Math.max(20, Math.min(24, (area.height() - gap * (rows - 1)) / rows));
		String keyPart = exclusive ? "/field-exclusive-choice-" : "/field-choice-";
		for (int index = 0; index < count; index++) {
			int column = index % columns;
			int row = index / columns;
			target.put(
				node.key() + keyPart + index,
				new WidgetLayout(
					WidgetKind.BUTTON,
					placed,
					node,
					new Rect(
						area.x() + column * (buttonWidth + gap),
						area.y() + row * (buttonHeight + gap),
						buttonWidth,
						buttonHeight
					)
				)
			);
		}
	}

	/**
	 * A linked destination blade for ordinary-player navigation. Its clipped corners and short
	 * signal rails create hierarchy without turning a sparse player page into a host dashboard.
	 */
	private static void drawPortalFrame(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int background,
		final int border,
		final int accent,
		final int borderWidth,
		final int detailSize,
		final float opacity
	) {
		if (bounds.width() < 12 || bounds.height() < 12) {
			graphics.fill(
				bounds.x(),
				bounds.y(),
				bounds.right(),
				bounds.bottom(),
				withOpacity(background, opacity)
			);
			graphics.outline(
				bounds.x(),
				bounds.y(),
				bounds.width(),
				bounds.height(),
				withOpacity(border, opacity)
			);
			return;
		}
		int cut = Math.min(
			Math.max(5, detailSize),
			Math.max(1, Math.min(bounds.width(), bounds.height()) / 4)
		);
		int fill = withOpacity(background, opacity);
		int line = withOpacity(border, opacity);
		int hot = withOpacity(accent, opacity);
		graphics.fill(bounds.x() + cut, bounds.y(), bounds.right(), bounds.bottom() - cut, fill);
		graphics.fill(bounds.x(), bounds.y() + cut, bounds.right() - cut, bounds.bottom(), fill);
		graphics.fill(
			bounds.x() + 2,
			bounds.y() + 2,
			bounds.right() - 2,
			bounds.bottom() - 2,
			fill
		);
		for (int index = 0; index < Math.max(1, borderWidth); index++) {
			int x = bounds.x() + index;
			int y = bounds.y() + index;
			int right = bounds.right() - index;
			int bottom = bounds.bottom() - index;
			graphics.fill(x + cut, y, right, y + 1, hot);
			graphics.fill(x, y + cut, x + 1, bottom, hot);
			graphics.fill(x, y + cut, x + cut, y + cut + 1, line);
			graphics.fill(x + cut - 1, y, x + cut, y + cut, line);
			graphics.fill(x, bottom - 1, right - cut, bottom, line);
			graphics.fill(right - cut, bottom - cut, right, bottom - cut + 1, line);
			graphics.fill(right - cut, bottom - cut, right - cut + 1, bottom, line);
			graphics.fill(right - 1, y, right, bottom - cut, line);
		}
		int pulse = Math.max(8, Math.min(bounds.width() / 3, 42));
		graphics.fill(
			bounds.x() + cut + 4,
			bounds.y() + 4,
			bounds.x() + cut + 4 + pulse,
			bounds.y() + 5,
			withOpacity(accent, opacity * 0.42F)
		);
		int markerLeft = Math.max(bounds.x() + 2, bounds.right() - 12);
		graphics.fill(
			markerLeft,
			bounds.bottom() - cut - 4,
			bounds.right() - 4,
			bounds.bottom() - cut - 2,
			withOpacity(accent, opacity * 0.72F)
		);
	}

	/**
	 * A resource-pack-independent document cover. Data packs control its colors and contents while
	 * the mod supplies the physical cover, spine, page block and bookmark details.
	 */
	private static void drawManualFrame(
		final GuiGraphicsExtractor graphics,
		final Rect bounds,
		final int background,
		final int border,
		final int accent,
		final int borderWidth,
		final int detailSize,
		final float opacity
	) {
		if (bounds.width() < 18 || bounds.height() < 18) {
			graphics.fill(
				bounds.x(),
				bounds.y(),
				bounds.right(),
				bounds.bottom(),
				withOpacity(background, opacity)
			);
			graphics.outline(
				bounds.x(),
				bounds.y(),
				bounds.width(),
				bounds.height(),
				withOpacity(border, opacity)
			);
			return;
		}
		int pageInset = Math.max(3, Math.min(6, detailSize));
		int spine = Math.max(8, Math.min(14, bounds.width() / 5));
		int coverRight = Math.max(bounds.x() + 1, bounds.right() - pageInset);
		int coverBottom = Math.max(bounds.y() + 1, bounds.bottom() - pageInset);
		int fill = withOpacity(background, opacity);
		int line = withOpacity(border, opacity);
		int hot = withOpacity(accent, opacity);
		int page = withOpacity(0xFFD5D2BE, opacity * 0.72F);
		int pageShadow = withOpacity(0xFF6C716C, opacity * 0.55F);

		graphics.fill(
			bounds.x() + pageInset,
			bounds.y() + pageInset,
			bounds.right(),
			bounds.bottom(),
			pageShadow
		);
		graphics.fill(bounds.x() + 2, bounds.y() + 2, coverRight, coverBottom, fill);
		graphics.fill(
			bounds.x() + 2,
			bounds.y() + 2,
			bounds.x() + spine,
			coverBottom,
			withOpacity(border, opacity * 0.62F)
		);
		graphics.fill(bounds.x() + spine, bounds.y() + 2, bounds.x() + spine + 1, coverBottom, hot);
		graphics.fill(bounds.x() + 2, bounds.y() + 2, coverRight, bounds.y() + 5, hot);
		for (int index = 0; index < Math.max(1, borderWidth); index++) {
			graphics.outline(
				bounds.x() + 2 + index,
				bounds.y() + 2 + index,
				Math.max(0, coverRight - bounds.x() - 2 - index * 2),
				Math.max(0, coverBottom - bounds.y() - 2 - index * 2),
				line
			);
		}
		for (int offset = 1; offset < pageInset; offset += 2) {
			graphics.fill(
				coverRight,
				bounds.y() + 5 + offset,
				bounds.right(),
				coverBottom + offset,
				page
			);
			graphics.fill(
				bounds.x() + spine,
				coverBottom + offset,
				coverRight,
				bounds.bottom(),
				page
			);
		}
		int labelAvailable = Math.max(1, coverRight - bounds.x() - spine - 8);
		int labelWidth = Math.max(1, Math.min(labelAvailable, Math.max(10, bounds.width() / 2)));
		graphics.fill(
			bounds.x() + spine + 5,
			bounds.y() + 12,
			bounds.x() + spine + 5 + labelWidth,
			bounds.y() + 14,
			withOpacity(accent, opacity * 0.58F)
		);
		graphics.fill(coverRight - 12, bounds.y() + 2, coverRight - 7, bounds.y() + 18, hot);
		graphics.fill(coverRight - 11, bounds.y() + 18, coverRight - 9, bounds.y() + 21, hot);
		graphics.fill(
			bounds.x() + 5,
			coverBottom - 13,
			bounds.x() + spine - 3,
			coverBottom - 10,
			withOpacity(accent, opacity * 0.76F)
		);
	}

	private void drawCarouselIndicators(
		final GuiGraphicsExtractor graphics,
		final PlacedNode placed,
		final RenderNode node,
		final Rect content,
		final float opacity
	) {
		List<CarouselChild> children = carouselChildren(placed);
		CarouselRuntime state = this.carouselStates.get(placed.key());
		if (state == null || children.size() <= 1 || content.width() < 36) {
			return;
		}
		int selected = indexOfCarouselChild(children, state.selectedId());
		int maximumVisible = Math.min(7, children.size());
		int start = Math.clamp(
			selected - maximumVisible / 2,
			0,
			Math.max(0, children.size() - maximumVisible)
		);
		int gap = 4;
		int regularWidth = 9;
		int selectedWidth = 20;
		int totalWidth = (maximumVisible - 1) * regularWidth
			+ selectedWidth
			+ (maximumVisible - 1) * gap;
		int x = content.x() + Math.max(0, (content.width() - totalWidth) / 2);
		int y = content.bottom() - 6;
		RenderNode selectedNode = this.plan.nodes().get(children.get(selected).placed().key());
		int accent = selectedNode == null
			? styleColor(node, "accent_color", INFO_CYAN)
			: styleColor(selectedNode, "accent_color", INFO_CYAN);
		for (int visibleIndex = 0; visibleIndex < maximumVisible; visibleIndex++) {
			int childIndex = start + visibleIndex;
			boolean current = childIndex == selected;
			int width = current ? selectedWidth : regularWidth;
			graphics.fill(
				x,
				y,
				x + width,
				y + 2,
				withOpacity(current ? accent : SURFACE_BORDER, opacity)
			);
			x += width + gap;
		}
	}

	private void rememberPageProjection() {
		this.observedRevision = ClientPageState.revision();
		this.observedPageProjection = currentPageProjection();
	}

	private static PageRenderProjection<ActivePage, PageStatus> currentPageProjection() {
		return new PageRenderProjection<>(
			ClientPageState.activePage().orElse(null),
			ClientPageState.pageStatus(),
			ClientPageState.pageMessage()
		);
	}

	private boolean fieldInputSatisfied(
		final RenderNode node,
		final FieldSchema field
	) {
		String nodeId = node.definition().id().orElse(node.key());
		JsonElement value = this.localUiValues.get(nodeId);
		if (value == null || value.isJsonNull()) {
			return false;
		}
		return switch (field.type()) {
			case "boolean" -> primitiveBoolean(value).isPresent();
			case "multi_choice" -> selectionCount(value)
				>= Math.max(1, field.minimumSelections().orElse(1));
			case "single_choice", "exclusive_choice" ->
				primitiveString(value).filter(candidate -> !candidate.isBlank()).isPresent();
			case "integer", "string", "identifier" ->
				primitiveInputText(value).filter(candidate -> !candidate.isBlank()).isPresent();
			default -> false;
		};
	}

	private void drawExclusiveChoiceContent(
		final GuiGraphicsExtractor graphics,
		final AbstractWidget widget,
		final ExclusiveChoicePresentation presentation,
		final double presentationScale
	) {
		float scale = (float)Math.clamp(presentationScale, 0.0, 8.0);
		if (scale <= 0.001F) {
			return;
		}
		if (Math.abs(scale - 1.0F) < 0.001F) {
			drawExclusiveChoiceContentUnscaled(graphics, widget, presentation);
			return;
		}

		int hitX = widget.getX();
		int hitY = widget.getY();
		int hitWidth = widget.getWidth();
		int hitHeight = widget.getHeight();
		float centerX = hitX + hitWidth / 2.0F;
		float centerY = hitY + hitHeight / 2.0F;
		int sourceWidth = Math.max(1, Math.round(hitWidth / scale));
		int sourceHeight = Math.max(1, Math.round(hitHeight / scale));
		widget.setX(Math.round(centerX - sourceWidth / 2.0F));
		widget.setY(Math.round(centerY - sourceHeight / 2.0F));
		widget.setWidth(sourceWidth);
		widget.setHeight(sourceHeight);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
		try {
			drawExclusiveChoiceContentUnscaled(graphics, widget, presentation);
		} finally {
			graphics.pose().popMatrix();
			widget.setX(hitX);
			widget.setY(hitY);
			widget.setWidth(hitWidth);
			widget.setHeight(hitHeight);
		}
	}

	private void drawExclusiveChoiceContentUnscaled(
		final GuiGraphicsExtractor graphics,
		final AbstractWidget widget,
		final ExclusiveChoicePresentation presentation
	) {
		ExclusiveOptionState option = presentation.option();
		int left = widget.getX() + 9;
		int right = widget.getRight() - 8;
		int height = widget.getHeight();
		boolean pending = presentation.fieldId().equals(this.pendingExclusiveMutationField)
			&& option.value().equals(this.pendingExclusiveMutationOption);
		int avatarSize = option.occupantId().isPresent() && height >= 36
			? Math.clamp(height - 18, 16, 22)
			: 0;
		int detailRight = right;
		if (avatarSize > 0) {
			int avatarX = right - avatarSize;
			PlayerIdentityRenderer.drawHead(
				graphics,
				option.occupantId().orElseThrow(),
				option.occupantOnline(),
				avatarX,
				widget.getY() + height - avatarSize - 5,
				avatarSize,
				false
			);
			detailRight = avatarX - 7;
		}

		String status = pending
			? "同步中"
			: exclusiveStatusLabel(option.state(), presentation.selected());
		int statusColor = pending
			? INFO_CYAN
			: exclusiveStatusColor(option.state(), presentation.selected());
		int statusWidth = this.font.width(status) + 8;
		boolean showBadge = right - left >= statusWidth + 64;
		int titleRight = right;
		if (showBadge) {
			int statusX = right - statusWidth;
			graphics.fill(
				statusX,
				widget.getY() + 5,
				right,
				widget.getY() + 16,
				withAlpha(statusColor, 46)
			);
			graphics.outline(
				statusX,
				widget.getY() + 5,
				statusWidth,
				11,
				withAlpha(statusColor, 150)
			);
			graphics.text(
				this.font,
				status,
				statusX + 4,
				widget.getY() + 6,
				statusColor,
				false
			);
			titleRight = statusX - 5;
		}

		int titleWidth = Math.max(1, titleRight - left);
		FormattedText title = this.font.substrByWidth(presentation.name(), titleWidth);
		graphics.text(
			this.font,
			Language.getInstance().getVisualOrder(title),
			left,
			widget.getY() + 6,
			widget.active ? MAIN_TEXT : MUTED_TEXT,
			true
		);

		String detail = pending ? "正在确认服务端占用状态…" : exclusiveChoiceDetail(presentation);
		if (!showBadge && detail.isBlank()) {
			detail = status;
		}
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(detail, Math.max(1, detailRight - left)),
			left,
			widget.getY() + Math.max(20, height - 15),
			widget.active ? SECONDARY_TEXT : MUTED_TEXT,
			false
		);
	}

	private static String exclusiveChoiceDetail(
		final ExclusiveChoicePresentation presentation
	) {
		ExclusiveOptionState option = presentation.option();
		if (
			option.state().equals("held_by_other")
				|| option.state().equals("locked_by_other")
		) {
			return option.occupantName()
				.map(name -> name + (option.occupantOnline() ? " · 在线" : " · 离线"))
				.orElse("其他玩家已选择");
		}
		if (!presentation.description().getString().isBlank()) {
			return presentation.description().getString();
		}
		return exclusiveStatusLabel(option.state(), presentation.selected());
	}

	private static String exclusiveStatusLabel(final String state, final boolean selected) {
		if (selected && !state.equals("locked_by_self")) {
			return "已选择";
		}
		return switch (state) {
			case "held_by_self" -> "已预留";
			case "locked_by_self" -> "已锁定";
			case "held_by_other" -> "暂占";
			case "locked_by_other" -> "已占用";
			default -> "可选";
		};
	}

	private static int exclusiveStatusColor(final String state, final boolean selected) {
		if (selected) {
			return BRAND_GOLD;
		}
		return switch (state) {
			case "held_by_self" -> WARNING;
			case "locked_by_self" -> SUCCESS;
			case "held_by_other" -> WARNING;
			case "locked_by_other" -> DANGER;
			default -> INFO_CYAN;
		};
	}

	private void synchronizeWidgetBounds(final MotionLayout motionLayout) {
		for (WidgetBinding binding : this.pageWidgets.values()) {
			if (!carouselInteractionVisible(binding.node().key())) {
				binding.widget().visible = false;
				continue;
			}
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
		synchronizeTerminalNavigationBounds(motionLayout);
	}

	private void synchronizeTerminalNavigationBounds(final MotionLayout motionLayout) {
		if (
			this.terminalRootNavigationWidgets.isEmpty()
				|| this.terminalShellViewport == null
		) {
			return;
		}
		long now = Util.getMillis();
		double shellScale = this.terminalShellViewport.scale();
		Rect shell = new Rect(
			this.terminalShellX,
			this.terminalShellY,
			this.terminalShellWidth,
			this.terminalShellHeight
		);
		for (TerminalNavigationBinding binding : this.terminalRootNavigationWidgets.values()) {
			NodeFrame frame = motionLayout.frames().get(binding.nodeKey());
			if (frame == null) {
				binding.button().visible = false;
				continue;
			}
			UiMotionRuntime.Sample sample = sampleNodeMotion(binding.nodeKey(), now);
			Rect base = this.terminalShellViewport.map(binding.referenceBounds());
			Transform transform = Transform.root(0, 0)
				.around(
					base,
					sample.scale(),
					sample.translateX() * shellScale,
					sample.translateY() * shellScale
				);
			Rect bounds = transform.apply(base);
			setWidgetBounds(binding.button(), bounds);
			binding.button().setPresentationScale(
				(float)(shellScale * sample.scale())
			);
			Rect visible = bounds.intersection(shell);
			binding.button().visible = frame.opacity() > 0.001F
				&& visible.width() > 0
				&& visible.height() > 0;
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
		int panelWidth = Math.min(
			360,
			Math.max(250, ConsoleScreenViewport.REFERENCE_WIDTH - 24)
		);
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

		int panelHeight = Math.min(
			ConsoleScreenViewport.REFERENCE_HEIGHT - 24,
			12 + lines.size() * 10
		);
		int x = 12;
		int y = Math.min(
			ConsoleScreenViewport.REFERENCE_HEIGHT - panelHeight - 12,
			this.viewportY + 8
		);
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
		int width = Math.min(
			560,
			Math.max(280, ConsoleScreenViewport.REFERENCE_WIDTH - 32)
		);
		int textWidth = width - 16;
		int textHeight = this.font.wordWrapHeight(
			Component.literal(this.lastRequestEnvelope),
			textWidth
		);
		int height = Math.min(
			ConsoleScreenViewport.REFERENCE_HEIGHT - 40,
			textHeight + 28
		);
		int x = (ConsoleScreenViewport.REFERENCE_WIDTH - width) / 2;
		int y = Math.max(
			20,
			ConsoleScreenViewport.REFERENCE_HEIGHT - FOOTER_HEIGHT - height - 6
		);
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
		int y = ConsoleScreenViewport.REFERENCE_HEIGHT - 25;
		String status = this.plan.tier()
			+ "  // "
			+ this.viewportWidth
			+ "×"
			+ this.viewportHeight
			+ "  // Repeat "
			+ this.plan.visibleRepeatItems()
			+ " visible";
		int availableWidth = Math.max(
			0,
			ConsoleScreenViewport.REFERENCE_WIDTH - 140
		);
		if (this.font.width(status) > availableWidth) {
			status = this.plan.tier() + "  // " + this.viewportWidth + "×" + this.viewportHeight;
		}
		graphics.text(this.font, status, 12, y + 6, MUTED_TEXT, false);
	}

	private void renderSafetyPage(final GuiGraphicsExtractor graphics) {
		int panelWidth = Math.max(
			1,
			Math.min(620, ConsoleScreenViewport.REFERENCE_WIDTH - 24)
		);
		int panelHeight = Math.max(
			1,
			Math.min(330, ConsoleScreenViewport.REFERENCE_HEIGHT - 24)
		);
		int x = (ConsoleScreenViewport.REFERENCE_WIDTH - panelWidth) / 2;
		int y = (ConsoleScreenViewport.REFERENCE_HEIGHT - panelHeight) / 2;
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
			case ROW, COLUMN, GRID, FLOW, OVERLAY, CAROUSEL, REPEAT ->
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
		if (this.referencePageScale >= 0.999) {
			return new Transform(this.pageScale, this.viewportX, this.viewportY);
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
		Rect childClip = placed.type() == NodeType.SCROLL
				|| placed.type() == NodeType.CARD
				|| placed.type() == NodeType.CAROUSEL
			? clip.intersection(content)
			: clip;
		if (placed.type() == NodeType.CAROUSEL) {
			CarouselRuntime state = this.carouselStates.get(placed.key());
			for (PlacedNode child : placed.children()) {
				CarouselChildMotion carousel = carouselChildMotion(
					placed,
					child,
					state,
					now
				);
				if (!carousel.visible()) {
					continue;
				}
				Transform childTransform = transform.around(
					child.bounds(),
					carousel.scale(),
					carousel.translateX(),
					0.0
				);
				buildNodeFrames(
					child,
					childTransform,
					childClip,
					(float)(opacity * carousel.opacity()),
					now,
					frames
				);
			}
			return;
		}
		for (PlacedNode child : placed.children()) {
			buildNodeFrames(child, transform, childClip, opacity, now, frames);
		}
	}

	private boolean carouselInteractionVisible(final String nodeKey) {
		for (Map.Entry<String, CarouselRuntime> entry : this.carouselStates.entrySet()) {
			PlacedNode carousel = this.placedNodes.get(entry.getKey());
			if (carousel == null || nodeKey.equals(carousel.key())) {
				continue;
			}
			for (CarouselChild child : carouselChildren(carousel)) {
				if (
					(nodeKey.equals(child.placed().key())
							|| nodeKey.startsWith(child.placed().key() + "/"))
						&& !child.id().equals(entry.getValue().selectedId())
				) {
					return false;
				}
			}
		}
		return true;
	}

	private CarouselChildMotion carouselChildMotion(
		final PlacedNode carousel,
		final PlacedNode child,
		final CarouselRuntime state,
		final long now
	) {
		RenderNode childNode = this.plan.nodes().get(child.key());
		if (childNode == null || childNode.definition() == null) {
			return CarouselChildMotion.hidden();
		}
		String childId = childNode.definition().id().orElse("");
		if (state == null) {
			return carousel.children().getFirst().key().equals(child.key())
				? CarouselChildMotion.still()
				: CarouselChildMotion.hidden();
		}
		double progress = state.progress(
			now,
			this.session.reducedMotion()
				? REDUCED_CAROUSEL_TRANSITION_MILLIS
				: CAROUSEL_TRANSITION_MILLIS
		);
		if (!state.transitioning()) {
			return childId.equals(state.selectedId())
				? CarouselChildMotion.still()
				: CarouselChildMotion.hidden();
		}
		double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
		if (childId.equals(state.selectedId())) {
			return this.session.reducedMotion()
				? new CarouselChildMotion(true, 1.0, 0.0, eased)
				: new CarouselChildMotion(
					true,
					0.96 + eased * 0.04,
					state.direction() * (1.0 - eased) * 36.0,
					eased
				);
		}
		if (childId.equals(state.previousId())) {
			return this.session.reducedMotion()
				? new CarouselChildMotion(true, 1.0, 0.0, 1.0 - eased)
				: new CarouselChildMotion(
					true,
					1.0 - eased * 0.04,
					-state.direction() * eased * 30.0,
					1.0 - eased
				);
		}
		return CarouselChildMotion.hidden();
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

	private Component textNodeComponent(final RenderNode node, final String text) {
		return Component.literal(text)
			.withStyle(style ->
				style.withFont(new FontDescription.Resource(node.fontId()))
					.withObfuscated(styleBoolean(node, "obfuscated", false))
			);
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

	private ConsoleScreenViewport.Layout consoleCanvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	private boolean safetyPageVisible() {
		return !isTerminalLoading() && isSafetyPage();
	}

	private MouseButtonEvent consoleReferenceMouseEvent(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = consoleCanvas();
		return new MouseButtonEvent(
			canvas.toReferenceX(event.x()),
			canvas.toReferenceY(event.y()),
			event.buttonInfo()
		);
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
		if (safetyPageVisible()) {
			ConsoleScreenViewport.Layout canvas = consoleCanvas();
			return super.mouseScrolled(
				canvas.toReferenceX(mouseX),
				canvas.toReferenceY(mouseY),
				horizontalAmount,
				verticalAmount
			);
		}
		double contentMouseX = mouseX;
		double contentMouseY = mouseY;
		if (this.session.previewChrome()) {
			ConsoleScreenViewport.Layout canvas = consoleCanvas();
			contentMouseX = canvas.toReferenceX(mouseX);
			contentMouseY = canvas.toReferenceY(mouseY);
		}
		if (this.plan == null) {
			return super.mouseScrolled(
				contentMouseX,
				contentMouseY,
				horizontalAmount,
				verticalAmount
			);
		}
		MotionLayout motionLayout = buildMotionLayout(Util.getMillis());
		PlacedNode scroll = deepestScrollableAt(contentMouseX, contentMouseY, motionLayout);
		if (scroll == null) {
			return super.mouseScrolled(
				contentMouseX,
				contentMouseY,
				horizontalAmount,
				verticalAmount
			);
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
		if (
			event.key() == GLFW_KEY_ESCAPE
				&& normalTerminalPage()
				&& this.active.terminalContext()
					.map(TerminalContext::stackDepth)
					.orElse(1) > 1
		) {
			if (!ClientPageState.terminalIntentPending(Intent.BACK)) {
				terminalBackOrClose();
			}
			return true;
		}
		if (ClientPageState.flowActionPending()) {
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
		if (this.closing || ClientPageState.flowActionPending()) {
			return true;
		}
		if (safetyPageVisible()) {
			return super.mouseClicked(consoleReferenceMouseEvent(event), doubleClick);
		}
		MouseButtonEvent contentEvent = this.session.previewChrome()
			? consoleReferenceMouseEvent(event)
			: event;
		MotionLayout motionLayout = this.plan == null
			? null
			: buildMotionLayout(Util.getMillis());
		if (motionLayout != null) {
			synchronizeWidgetBounds(motionLayout);
		}
		if (contentEvent.button() == 0 && this.plan != null) {
			PlacedNode scroll = this.placedNodes.values()
				.stream()
				.filter(node -> node.type() == NodeType.SCROLL)
				.filter(this::showScrollbar)
				.filter(
					node -> {
						Scrollbar bar = scrollbar(node, motionLayout);
						return bar != null
							&& contains(
								visibleClip(node, motionLayout),
								contentEvent.x(),
								contentEvent.y()
							)
							&& contains(
								bar.track(),
								contentEvent.x(),
								contentEvent.y()
							);
					}
				)
				.max(Comparator.comparingInt(node -> node.key().length()))
				.orElse(null);
			if (scroll != null) {
				Scrollbar bar = scrollbar(scroll, motionLayout);
				this.draggingScrollKey = scroll.key();
				this.dragStartMouse = bar.direction() == Direction.VERTICAL
					? contentEvent.y()
					: contentEvent.x();
				this.dragStartOffset = scroll.scrollOffset();
				return true;
			}
		}
		List<AbstractWidget> hidden = hideClippedPageWidgetsAt(
			contentEvent.x(),
			contentEvent.y(),
			motionLayout
		);
		try {
			return super.mouseClicked(contentEvent, doubleClick);
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
		if (safetyPageVisible()) {
			ConsoleScreenViewport.Layout canvas = consoleCanvas();
			return super.mouseDragged(
				consoleReferenceMouseEvent(event),
				canvas.toReferenceLength(deltaX),
				canvas.toReferenceLength(deltaY)
			);
		}
		MouseButtonEvent contentEvent = this.session.previewChrome()
			? consoleReferenceMouseEvent(event)
			: event;
		double contentDeltaX = deltaX;
		double contentDeltaY = deltaY;
		if (this.session.previewChrome()) {
			ConsoleScreenViewport.Layout canvas = consoleCanvas();
			contentDeltaX = canvas.toReferenceLength(deltaX);
			contentDeltaY = canvas.toReferenceLength(deltaY);
		}
		if (
			this.draggingScrollKey == null
				|| this.plan == null
				|| contentEvent.button() != 0
		) {
			return super.mouseDragged(
				contentEvent,
				contentDeltaX,
				contentDeltaY
			);
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
		double mouse = bar.direction() == Direction.VERTICAL
			? contentEvent.y()
			: contentEvent.x();
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
		if (safetyPageVisible()) {
			return super.mouseReleased(consoleReferenceMouseEvent(event));
		}
		MouseButtonEvent contentEvent = this.session.previewChrome()
			? consoleReferenceMouseEvent(event)
			: event;
		if (contentEvent.button() == 0 && this.draggingScrollKey != null) {
			this.draggingScrollKey = null;
			return true;
		}
		return super.mouseReleased(contentEvent);
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
		this.terminalRootNavigationWidgets.values()
			.forEach(binding -> binding.button().active = false);
		if (this.terminalExitButton != null) {
			this.terminalExitButton.active = false;
		}
		if (this.terminalBackButton != null) {
			this.terminalBackButton.active = false;
		}
		if (this.terminalTakeoverButton != null) {
			this.terminalTakeoverButton.active = false;
		}
		if (!animated || motionsFinished(UiEvent.HIDE, now)) {
			finishClose();
		}
	}

	private void finishClose() {
		if (this.serverReleased) {
			ClientPageState.completeTerminalRelease();
		} else {
			if (!ClientPageState.closeActivePage()) {
				this.closing = false;
				return;
			}
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

	private record TerminalNavigationBinding(
		ConsoleButton button,
		String nodeKey,
		Rect referenceBounds
	) {
	}

	private record TerminalNavigationPlan(
		List<String> buttonKeys,
		Set<String> subtreeKeys
	) {
		private TerminalNavigationPlan {
			buttonKeys = List.copyOf(buttonKeys);
			subtreeKeys = Set.copyOf(subtreeKeys);
		}

		private static TerminalNavigationPlan empty() {
			return new TerminalNavigationPlan(List.of(), Set.of());
		}
	}

	private record CarouselSelectionKey(
		UUID terminalSessionId,
		Identifier pageId,
		String carouselId
	) {
		private CarouselSelectionKey {
			Objects.requireNonNull(terminalSessionId, "terminalSessionId");
			Objects.requireNonNull(pageId, "pageId");
			carouselId = Objects.requireNonNull(carouselId, "carouselId");
		}
	}

	private record CarouselChild(String id, PlacedNode placed) {
	}

	private record CarouselControls(ConsoleButton previous, ConsoleButton next) {
	}

	private record CarouselControlRects(Rect previous, Rect next) {
	}

	private record CarouselChildMotion(
		boolean visible,
		double scale,
		double translateX,
		double opacity
	) {
		private static CarouselChildMotion hidden() {
			return new CarouselChildMotion(false, 1.0, 0.0, 0.0);
		}

		private static CarouselChildMotion still() {
			return new CarouselChildMotion(true, 1.0, 0.0, 1.0);
		}
	}

	private static final class CarouselRuntime {
		private String selectedId;
		private String previousId = "";
		private int direction;
		private long startedAt = -1L;

		private CarouselRuntime(final String selectedId) {
			this.selectedId = Objects.requireNonNull(selectedId, "selectedId");
		}

		private CarouselRuntime reconcile(
			final String fallbackSelectedId,
			final List<CarouselChild> children
		) {
			if (children.stream().noneMatch(child -> child.id().equals(this.selectedId))) {
				this.selectedId = fallbackSelectedId;
				clearTransition();
			}
			if (
				!this.previousId.isEmpty()
					&& children.stream().noneMatch(child -> child.id().equals(this.previousId))
			) {
				clearTransition();
			}
			return this;
		}

		private void select(
			final String nextSelectedId,
			final int nextDirection,
			final long now
		) {
			if (this.selectedId.equals(nextSelectedId)) {
				return;
			}
			this.previousId = this.selectedId;
			this.selectedId = Objects.requireNonNull(nextSelectedId, "nextSelectedId");
			this.direction = nextDirection < 0 ? -1 : 1;
			this.startedAt = now;
		}

		private double progress(final long now, final long duration) {
			if (!transitioning()) {
				return 1.0;
			}
			long safeDuration = Math.max(1L, duration);
			double value = Math.clamp((double)(now - this.startedAt) / safeDuration, 0.0, 1.0);
			if (value >= 1.0) {
				clearTransition();
				return 1.0;
			}
			return value;
		}

		private boolean transitioning() {
			return this.startedAt >= 0L && !this.previousId.isEmpty();
		}

		private String selectedId() {
			return this.selectedId;
		}

		private String previousId() {
			return this.previousId;
		}

		private int direction() {
			return this.direction;
		}

		private void clearTransition() {
			this.previousId = "";
			this.direction = 0;
			this.startedAt = -1L;
		}
	}

	private record WidgetLayout(
		WidgetKind kind,
		PlacedNode placed,
		RenderNode node,
		Rect bounds
	) {
	}

	private enum WidgetKind {
		BUTTON {
			@Override
			boolean matches(final AbstractWidget widget) {
				return widget instanceof ConsoleButton;
			}
		},
		EDIT_BOX {
			@Override
			boolean matches(final AbstractWidget widget) {
				return widget instanceof AnimatedEditBox;
			}
		};

		abstract boolean matches(AbstractWidget widget);
	}

	private record ExclusiveChoicePresentation(
		Identifier fieldId,
		ExclusiveOptionState option,
		Component name,
		Component description,
		boolean selected
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
