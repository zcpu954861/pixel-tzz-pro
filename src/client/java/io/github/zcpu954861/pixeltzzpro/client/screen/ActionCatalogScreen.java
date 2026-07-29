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
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.WARNING;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.RepeatVirtualization;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Bounded action directory projected directly from the latest authoritative snapshot.
 */
final class ActionCatalogScreen extends TransitioningConsoleScreen {
	private static final int ROW_HEIGHT = 43;
	private static final int ROW_GAP = 5;
	private static final Comparator<ActionEntry> ACTION_ORDER = Comparator
		.comparing((ActionEntry action) -> !action.enabled())
		.thenComparingInt(ActionCatalogScreen::sectionPriority)
		.thenComparingInt(ActionEntry::order);

	private long observedRevision = Long.MIN_VALUE;
	private int firstVisible;
	private int visibleRows;
	private final List<ConsoleButton> actionButtons = new ArrayList<>();
	private String widgetStructureKey = "";
	private final Set<String> excludedOperationTypes;

	ActionCatalogScreen(final Screen parent) {
		this(parent, Set.of());
	}

	ActionCatalogScreen(final Screen parent, final Set<String> excludedOperationTypes) {
		super(Component.literal("全部操作"), parent, Motion.PUSH_ENTER);
		this.excludedOperationTypes = Set.copyOf(excludedOperationTypes);
	}

	@Override
	protected void init() {
		if (!ClientConsoleState.pending()) {
			ClientConsoleState.requestConsole();
		}
		rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		this.actionButtons.clear();
		List<ActionEntry> actions = actions();
		int x = panelX();
		int y = panelY();
		int width = panelWidth();
		int height = panelHeight();
		int listTop = y + 65;
		int footerTop = y + height - 46;
		int viewportHeight = Math.max(1, footerTop - listTop);
		this.visibleRows = RepeatVirtualization.fullyVisibleRows(
			viewportHeight,
			ROW_HEIGHT,
			ROW_GAP
		);
		this.firstVisible = Math.clamp(
			this.firstVisible,
			0,
			Math.max(0, actions.size() - this.visibleRows)
		);

		int actionWidth = Math.min(112, Math.max(82, width / 5));
		for (
			int index = this.firstVisible;
			index < Math.min(actions.size(), this.firstVisible + this.visibleRows);
			index++
		) {
			ActionEntry action = actions.get(index);
			int visibleActionIndex = index;
			int rowY = listTop + (index - this.firstVisible) * (ROW_HEIGHT + ROW_GAP);
			ConsoleButton open = new ConsoleButton(
				x + width - actionWidth - 22,
				rowY + 9,
				actionWidth,
				24,
				Component.literal(
					actionRouting(action.operationId())
						? "正在审阅…"
						: action.enabled() ? "审阅并执行" : "当前不可用"
				),
				button -> dispatchVisibleAction(visibleActionIndex),
				action.enabled() && action.requiresConfirmation() ? Variant.DANGER : Variant.NORMAL
			);
			open.active = action.enabled();
			Component reason = action.enabled()
				? ConsoleText.parse(action.descriptionJson(), action.operationId().toString())
				: ConsoleText.parse(action.disabledReasonJson(), "服务端未提供禁用原因");
			open.setTooltip(Tooltip.create(reason));
			this.addRenderableWidget(open);
			this.actionButtons.add(open);
		}

		int footerY = y + height - 35;
		ConsoleButton refresh = new ConsoleButton(
			x + 18,
			footerY,
			86,
			24,
			Component.literal("刷新目录"),
			button -> ClientConsoleState.requestConsole(),
			Variant.NORMAL
		);
		refresh.active = true;
		this.addRenderableWidget(refresh);

		ConsoleButton back = new ConsoleButton(
			x + width - 104,
			footerY,
			86,
			24,
			Component.literal("返回"),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(back);
		this.widgetStructureKey = widgetStructureKey(actions);
		this.observedRevision = ClientConsoleState.revision();
	}

	@Override
	public void tick() {
		super.tick();
		if (navigationLocked()) {
			return;
		}
		long revision = ClientConsoleState.revision();
		if (this.observedRevision != revision) {
			this.observedRevision = revision;
			List<ActionEntry> actions = actions();
			int nextFirstVisible = Math.clamp(
				this.firstVisible,
				0,
				Math.max(0, actions.size() - this.visibleRows)
			);
			if (nextFirstVisible != this.firstVisible) {
				this.firstVisible = nextFirstVisible;
				rebuildWidgets();
			} else if (!this.widgetStructureKey.equals(widgetStructureKey(actions))) {
				rebuildWidgets();
			} else {
				refreshActionButtons(actions);
			}
		}
	}

	private void refreshActionButtons(final List<ActionEntry> actions) {
		for (int index = 0; index < this.actionButtons.size(); index++) {
			int actionIndex = this.firstVisible + index;
			if (actionIndex >= actions.size()) {
				break;
			}
			ActionEntry action = actions.get(actionIndex);
			ConsoleButton button = this.actionButtons.get(index);
			button.setMessage(
				Component.literal(
					actionRouting(action.operationId())
						? "正在审阅…"
						: action.enabled() ? "审阅并执行" : "当前不可用"
				)
			);
			button.active = action.enabled();
			Component reason = action.enabled()
				? ConsoleText.parse(action.descriptionJson(), action.operationId().toString())
				: ConsoleText.parse(action.disabledReasonJson(), "服务端未提供禁用原因");
			button.setTooltip(Tooltip.create(reason));
		}
	}

	private void dispatchVisibleAction(final int actionIndex) {
		List<ActionEntry> current = actions();
		if (actionIndex >= 0 && actionIndex < current.size()) {
			dispatchAction(current.get(actionIndex));
		}
	}

	private String widgetStructureKey(final List<ActionEntry> actions) {
		int end = Math.min(actions.size(), this.firstVisible + this.visibleRows);
		StringBuilder key = new StringBuilder()
			.append(this.firstVisible)
			.append('|')
			.append(Math.max(0, end - this.firstVisible));
		for (int index = this.firstVisible; index < end; index++) {
			key.append('|').append(actions.get(index).requiresConfirmation());
		}
		return key.toString();
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (navigationLocked()) {
			return true;
		}
		if (revealPartialRow(event.x(), event.y())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * The bottom edge deliberately exposes the next row when the viewport has spare pixels. Clicking
	 * that preview scrolls it fully into view instead of leaving a visible but inert fragment.
	 */
	private boolean revealPartialRow(final double mouseX, final double mouseY) {
		List<ActionEntry> actions = actions();
		int x = panelX();
		int y = panelY();
		int width = panelWidth();
		int height = panelHeight();
		int listTop = y + 65;
		int listBottom = y + height - 46;
		int partialIndex = this.firstVisible + this.visibleRows;
		int partialTop = listTop + this.visibleRows * (ROW_HEIGHT + ROW_GAP);
		if (
			partialIndex >= actions.size()
				|| partialTop >= listBottom
				|| mouseX < x + 18
				|| mouseX >= x + width - 18
				|| mouseY < partialTop
				|| mouseY >= listBottom
		) {
			return false;
		}
		this.firstVisible = Math.min(
			this.firstVisible + 1,
			Math.max(0, actions.size() - this.visibleRows)
		);
		rebuildWidgets();
		return true;
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		if (navigationLocked() || verticalAmount == 0.0) {
			return navigationLocked()
				|| super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		int next = Math.clamp(
			this.firstVisible + (verticalAmount > 0.0 ? -1 : 1),
			0,
			Math.max(0, actions().size() - this.visibleRows)
		);
		if (next == this.firstVisible) {
			return false;
		}
		this.firstVisible = next;
		rebuildWidgets();
		return true;
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
		NavigationFrame transition = beginNavigationFrame(graphics);
		try {
			int x = panelX();
			int y = panelY();
			int width = panelWidth();
			int height = panelHeight();
			int listTop = y + 65;
			int listBottom = y + height - 46;
			List<ActionEntry> actions = actions();
			long enabled = actions.stream().filter(ActionEntry::enabled).count();

			graphics.fill(x, y, x + width, y + height, PANEL);
			graphics.outline(x, y, width, height, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + height, BRAND_GOLD);
			graphics.fill(x + 4, y, x + width, y + 2, INFO_CYAN);
			graphics.text(this.font, "ACTION REGISTRY // 服务端权威目录", x + 18, y + 12, INFO_CYAN, false);
			drawServerSyncBadge(graphics, x + width - 14, y + 10);
			graphics.text(this.font, this.title, x + 18, y + 29, MAIN_TEXT, true);
			graphics.text(
				this.font,
				"%d 项可用 / %d 项已注册   ·   按区域与优先级排列".formatted(enabled, actions.size()),
				x + 18,
				y + 46,
				SECONDARY_TEXT,
				false
			);

			graphics.fill(x + 12, listTop - 5, x + width - 12, listBottom, SURFACE);
			graphics.outline(
				x + 12,
				listTop - 5,
				width - 24,
				Math.max(1, listBottom - listTop + 5),
				SURFACE_BORDER
			);
			if (actions.isEmpty()) {
				graphics.text(
					this.font,
					ClientConsoleState.pending() ? "正在请求当前上下文操作…" : "当前阶段没有可显示的操作",
					x + 28,
					listTop + 18,
					ClientConsoleState.pending() ? INFO_CYAN : MUTED_TEXT,
					false
				);
			}

			int actionWidth = Math.min(112, Math.max(82, width / 5));
			int textWidth = Math.max(32, width - actionWidth - 72);
			graphics.enableScissor(x + 13, listTop, x + width - 13, listBottom);
			try {
				int viewportHeight = Math.max(1, listBottom - listTop);
				boolean hasPartialRow = viewportHeight
					> this.visibleRows * (ROW_HEIGHT + ROW_GAP);
				int renderCount = this.visibleRows
					+ (
						hasPartialRow && this.firstVisible + this.visibleRows < actions.size()
							? 1
							: 0
					);
				for (
					int index = this.firstVisible;
					index < Math.min(actions.size(), this.firstVisible + renderCount);
					index++
				) {
					ActionEntry action = actions.get(index);
					int rowY = listTop + (index - this.firstVisible) * (ROW_HEIGHT + ROW_GAP);
					int accent = actionColor(action.color());
					int rowColor = action.enabled() ? 0xD51C2833 : 0xA5172028;
					graphics.fill(x + 18, rowY, x + width - 18, rowY + ROW_HEIGHT, rowColor);
					graphics.outline(
						x + 18,
						rowY,
						width - 36,
						ROW_HEIGHT,
						action.enabled() ? withAlpha(accent, 135) : withAlpha(MUTED_TEXT, 80)
					);
					graphics.fill(x + 18, rowY, x + 21, rowY + ROW_HEIGHT, accent);
					Component label = ConsoleText.parse(action.labelJson(), action.operationId().toString());
					graphics.text(
						this.font,
						this.font.plainSubstrByWidth(label.getString(), textWidth),
						x + 29,
						rowY + 8,
						action.enabled() ? MAIN_TEXT : SECONDARY_TEXT,
						true
					);
					String meta = action.enabled()
						? sectionName(action.section())
							+ (action.requiresConfirmation() ? "  ·  需要二次确认" : "  ·  可直接执行")
						: "不可用 // " + ConsoleText.parse(
							action.disabledReasonJson(),
							"服务端未提供原因"
						).getString();
					graphics.text(
						this.font,
						this.font.plainSubstrByWidth(meta, textWidth),
						x + 29,
						rowY + 23,
						action.enabled() ? accent : DANGER,
						false
					);
				}
			} finally {
				graphics.disableScissor();
			}
			drawScrollBar(graphics, x, y, width, listTop, listBottom, actions.size());
			drawFeedback(graphics, x, y, width, height);
			super.extractRenderState(
				graphics,
				transitionMouseX(mouseX),
				transitionMouseY(mouseY),
				tickProgress
			);
		} finally {
			endNavigationFrame(graphics, transition);
		}
	}

	private void drawScrollBar(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int listTop,
		final int listBottom,
		final int total
	) {
		if (total <= this.visibleRows) {
			return;
		}
		int trackX = x + width - 9;
		int trackHeight = Math.max(1, listBottom - listTop);
		int thumbHeight = Math.max(18, trackHeight * this.visibleRows / total);
		int maximum = total - this.visibleRows;
		int thumbY = listTop + (trackHeight - thumbHeight) * this.firstVisible / maximum;
		graphics.fill(trackX, listTop, trackX + 2, listBottom, withAlpha(MUTED_TEXT, 90));
		graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, INFO_CYAN);
	}

	private void drawFeedback(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		String feedback = ClientConsoleState.feedback();
		if (feedback.isEmpty()) {
			return;
		}
		int available = Math.max(1, width - 226);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(feedback, available),
			x + 112,
			y + height - 28,
			ClientConsoleState.feedbackError() ? DANGER : SUCCESS,
			false
		);
	}

	private static int actionColor(final String color) {
		if (color.startsWith("#") && color.length() == 7) {
			try {
				return 0xFF000000 | Integer.parseInt(color.substring(1), 16);
			} catch (NumberFormatException ignored) {
				return INFO_CYAN;
			}
		}
		return switch (color.toLowerCase(Locale.ROOT)) {
			case "red", "dark_red" -> DANGER;
			case "gold", "yellow" -> BRAND_GOLD;
			case "green", "dark_green" -> SUCCESS;
			case "aqua", "dark_aqua", "blue" -> INFO_CYAN;
			case "gray", "grey", "dark_gray", "dark_grey" -> MUTED_TEXT;
			default -> WARNING;
		};
	}

	private List<ActionEntry> actions() {
		return ClientConsoleState.snapshot()
			.map(snapshot -> snapshot.actions()
				.stream()
				.filter(action -> !this.excludedOperationTypes.contains(action.operationType()))
				.sorted(ACTION_ORDER)
				.toList())
			.orElseGet(List::of);
	}

	private static int sectionPriority(final ActionEntry action) {
		return switch (action.section()) {
			case "primary" -> 0;
			case "roles" -> 1;
			case "system" -> 2;
			case "diagnostics" -> 3;
			default -> 4;
		};
	}

	private static String sectionName(final String section) {
		return switch (section) {
			case "primary" -> "当前阶段";
			case "roles" -> "身份管理";
			case "system" -> "系统管理";
			case "diagnostics" -> "异常恢复";
			default -> "其他操作";
		};
	}

	private int panelWidth() {
		return Math.max(1, Math.min(760, this.width - 12));
	}

	private int panelHeight() {
		return Math.max(1, Math.min(430, this.height - 12));
	}

	private int panelX() {
		return (this.width - panelWidth()) / 2;
	}

	private int panelY() {
		return (this.height - panelHeight()) / 2;
	}

	@Override
	public void onClose() {
		closeToParent();
	}
}
