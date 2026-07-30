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

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState.ConnectionStatus;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState.Snapshot;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.PhaseEntry;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Reusable player/host control surface. The server snapshot remains authoritative;
 * this screen only changes presentation and exposes actions that the server enables.
 */
public final class ControlConsoleScreen extends TransitioningConsoleScreen {
	private static final int FULL_PHASE_RAIL_WIDTH = 102;
	private static final int FULL_PHASE_RAIL_GAP = 12;
	private static final int PANEL_INSET = 16;
	private static final int ACTION_ZONE_HORIZONTAL_PADDING = 9;
	private static final int QUICK_ACTION_GAP = 5;
	private static final int QUICK_ACTION_HEIGHT = 24;
	private static final int QUICK_ACTION_MIN_COLUMN_WIDTH = 80;
	private static final int ACTION_ZONE_BUTTON_BOTTOM_PADDING = 9;
	private static final int COMPACT_ACTION_ZONE_HEIGHT = 51;
	private static final int ACTION_ZONE_SUMMARY_MIN_HEIGHT = 68;

	private ConsoleButton actionButton;
	private ConsoleButton catalogButton;
	private ConsoleButton rosterButton;
	private final Screen terminalReturnScreen;
	private final List<ConsoleButton> quickActionButtons = new ArrayList<>();
	private long observedConsoleRevision = Long.MIN_VALUE;
	private String widgetLayoutKey = "";
	private int actionZoneY;
	private int actionZoneHeight;
	private boolean actionZoneShowsSummary;

	public ControlConsoleScreen(final Screen parent) {
		super(Component.translatable("pixel_tzz_pro.menu.open"), parent, Motion.ROOT_ENTER);
		this.terminalReturnScreen = parent;
	}

	@Override
	protected void init() {
		Snapshot session = ClientSessionState.snapshot();
		if (
			session.currentPlayerHost()
				&& !ClientConsoleState.pending()
		) {
			ClientConsoleState.requestConsole();
		}
		rebuildConsoleWidgets();
	}

	private void rebuildConsoleWidgets() {
		this.clearWidgets();
		this.quickActionButtons.clear();
		Snapshot session = ClientSessionState.snapshot();
		Optional<ConsoleSnapshotS2CPayload> console = visibleConsole(session);
		int panelX = panelX();
		int panelY = panelY();
		int panelWidth = panelWidth();
		int innerWidth = panelWidth - 32;
		boolean compact = panelWidth < 560 || panelHeight() < 296;
		int actionX = compact
			? panelX + PANEL_INSET
			: panelX + PANEL_INSET + FULL_PHASE_RAIL_WIDTH + FULL_PHASE_RAIL_GAP;
		int actionAreaWidth = panelX + panelWidth - PANEL_INSET - actionX;
		List<ActionEntry> quickActions = console
			.map(ControlConsoleScreen::contextQuickActions)
			.orElseGet(List::of);
		boolean showRecent = !compact
			&& console.flatMap(ConsoleSnapshotS2CPayload::activeFlow).isEmpty()
			&& console.flatMap(ConsoleSnapshotS2CPayload::recentFlow).isPresent();
		int quickCount = Math.min(
			3,
			1 + quickActions.size() + (showRecent ? 1 : 0)
		);
		ActionZoneLayout actionLayout = actionZoneLayout(
			actionX,
			actionAreaWidth,
			quickCount,
			compact,
			panelY,
			panelHeight(),
			footerHeight()
		);
		List<ActionSlot> actionSlots = actionLayout.slots();
		this.actionZoneY = actionLayout.zoneY();
		this.actionZoneHeight = actionLayout.zoneHeight();
		this.actionZoneShowsSummary = actionLayout.showSummary();

		this.actionButton = new ConsoleButton(
			actionSlots.getFirst().x(),
			actionSlots.getFirst().y(),
			actionSlots.getFirst().width(),
			actionSlots.getFirst().height(),
			primaryActionLabel(console),
			button -> openPrimaryAction(),
			Variant.PRIMARY
		);
		updatePrimaryAction(console);
		this.addRenderableWidget(this.actionButton);
		int slot = 1;
		if (showRecent && slot < quickCount) {
			ActionSlot recentSlot = actionSlots.get(slot);
			ConsoleButton recent = new ConsoleButton(
				recentSlot.x(),
				recentSlot.y(),
				recentSlot.width(),
				recentSlot.height(),
				Component.literal("查看最近流程"),
				button -> pushScreen(new ActiveFlowScreen(this)),
				Variant.NORMAL
			);
			recent.setTooltip(Tooltip.create(Component.literal("查看最近一次已完成或已取消流程的终态快照")));
			this.addRenderableWidget(recent);
			slot++;
		}
		for (int index = 0; slot < quickCount && index < quickActions.size(); index++, slot++) {
			ActionEntry quickAction = quickActions.get(index);
			int quickIndex = index;
			ActionSlot quickSlot = actionSlots.get(slot);
			ConsoleButton quick = new ConsoleButton(
				quickSlot.x(),
				quickSlot.y(),
				quickSlot.width(),
				quickSlot.height(),
				actionRouting(quickAction.operationId())
					? Component.literal("正在审阅…")
					: ConsoleText.parse(quickAction.labelJson(), quickAction.operationId().toString()),
				button -> dispatchQuickAction(quickIndex),
				quickAction.requiresConfirmation() ? Variant.DANGER : Variant.NORMAL
			);
			quick.active = quickAction.enabled();
			quick.setTooltip(
				Tooltip.create(
					ConsoleText.parse(
						quickAction.descriptionJson(),
						quickAction.operationId().toString()
					)
				)
			);
			this.addRenderableWidget(quick);
			this.quickActionButtons.add(quick);
		}

		int footerTop = panelY + panelHeight() - footerHeight();
		boolean stackedFooter = panelWidth < 420;
		int footerGap = 6;
		int footerButtonWidth = stackedFooter
			? Math.max(1, (innerWidth - footerGap) / 2)
			: Math.max(1, (innerWidth - footerGap * 3) / 4);
		int firstFooterY = footerTop + 7;
		int secondFooterY = stackedFooter ? firstFooterY + 29 : firstFooterY;
		this.catalogButton = new ConsoleButton(
			panelX + 16,
			firstFooterY,
			footerButtonWidth,
			24,
			Component.literal("全部操作"),
			button -> pushScreen(new ActionCatalogScreen(this)),
			Variant.PRIMARY
		);
		this.catalogButton.active = console.map(ConsoleSnapshotS2CPayload::adminEligible).orElse(false);
		this.catalogButton.setTooltip(Tooltip.create(Component.literal("查看服务端在当前阶段公开的完整操作目录")));
		this.addRenderableWidget(this.catalogButton);

		int rosterX = panelX + 16 + footerButtonWidth + footerGap;
		this.rosterButton = new ConsoleButton(
			rosterX,
			firstFooterY,
			footerButtonWidth,
			24,
			Component.literal("玩家名单"),
			button -> pushScreen(new PlayerRosterScreen(this)),
			Variant.NORMAL
		);
		this.rosterButton.active = console.isPresent();
		this.rosterButton.setTooltip(Tooltip.create(Component.literal("查看全部登记玩家的身份、在线、初始化与生存状态")));
		this.addRenderableWidget(this.rosterButton);

		int developmentX = stackedFooter
			? panelX + 16
			: rosterX + footerButtonWidth + footerGap;
		boolean showTimeline = session.currentPlayerHost();
		ConsoleButton development = new ConsoleButton(
			developmentX,
			secondFooterY,
			footerButtonWidth,
			24,
			showTimeline
				? Component.translatable("pixel_tzz_pro.timeline.open")
				: Component.literal("开发与诊断"),
			button -> pushScreen(
				showTimeline
					? new TimelineScreen(this)
					: new PagePreviewCatalogScreen(this)
			),
			Variant.NORMAL
		);
		development.active = showTimeline || previewAvailable(session);
		development.setTooltip(
			Tooltip.create(
				showTimeline
					? Component.translatable("pixel_tzz_pro.timeline.open.tooltip")
					: Component.literal("打开数据包页面预览器；此入口不参与正式游戏流程")
			)
		);
		this.addRenderableWidget(development);

		int backX = developmentX + footerButtonWidth + footerGap;
		ConsoleButton back = new ConsoleButton(
			backX,
			secondFooterY,
			footerButtonWidth,
			24,
			Component.translatable("pixel_tzz_pro.screen.back"),
			button -> this.onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(back);
		this.widgetLayoutKey = widgetLayoutKey(session, console);
		this.observedConsoleRevision = ClientConsoleState.revision();
	}

	@Override
	public void tick() {
		super.tick();
		if (navigationLocked()) {
			return;
		}
		Snapshot session = ClientSessionState.snapshot();
		if (!session.currentPlayerHost()) {
			LivePageSupervisor.openPlayerTerminal(this.minecraft, this.terminalReturnScreen);
			return;
		}
		long revision = ClientConsoleState.revision();
		if (this.observedConsoleRevision != revision) {
			this.observedConsoleRevision = revision;
			Optional<ConsoleSnapshotS2CPayload> console = visibleConsole(session);
			if (!this.widgetLayoutKey.equals(widgetLayoutKey(session, console))) {
				rebuildConsoleWidgets();
			} else {
				refreshConsoleWidgets(console);
			}
		}
	}

	private Optional<ConsoleSnapshotS2CPayload> visibleConsole(final Snapshot session) {
		return session.currentPlayerHost()
			? ClientConsoleState.snapshot()
			: Optional.empty();
	}

	private void refreshConsoleWidgets(
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		this.actionButton.setMessage(primaryActionLabel(console));
		updatePrimaryAction(console);
		List<ActionEntry> quickActions = console
			.map(ControlConsoleScreen::contextQuickActions)
			.orElseGet(List::of);
		for (int index = 0; index < this.quickActionButtons.size(); index++) {
			ConsoleButton button = this.quickActionButtons.get(index);
			ActionEntry action = quickActions.get(index);
			button.setMessage(
				actionRouting(action.operationId())
					? Component.literal("正在审阅…")
					: ConsoleText.parse(action.labelJson(), action.operationId().toString())
			);
			button.active = action.enabled();
			button.setTooltip(
				Tooltip.create(
					ConsoleText.parse(action.descriptionJson(), action.operationId().toString())
				)
			);
		}
		this.catalogButton.active = console.map(ConsoleSnapshotS2CPayload::adminEligible).orElse(false);
		this.rosterButton.active = console.isPresent();
	}

	private void dispatchQuickAction(final int index) {
		visibleConsole(ClientSessionState.snapshot())
			.map(ControlConsoleScreen::contextQuickActions)
			.filter(actions -> index >= 0 && index < actions.size())
			.map(actions -> actions.get(index))
			.ifPresent(this::dispatchAction);
	}

	private String widgetLayoutKey(
		final Snapshot session,
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		boolean compact = panelWidth() < 560 || panelHeight() < 296;
		List<ActionEntry> quickActions = console
			.map(ControlConsoleScreen::contextQuickActions)
			.orElseGet(List::of);
		boolean showRecent = !compact
			&& console.flatMap(ConsoleSnapshotS2CPayload::activeFlow).isEmpty()
			&& console.flatMap(ConsoleSnapshotS2CPayload::recentFlow).isPresent();
		int quickCount = Math.min(
			3,
			1 + quickActions.size() + (showRecent ? 1 : 0)
		);
		int visibleQuickActions = Math.max(0, quickCount - 1 - (showRecent ? 1 : 0));
		String variants = quickActions.stream()
			.limit(visibleQuickActions)
			.map(action -> action.requiresConfirmation() ? "danger" : "normal")
			.reduce("", (left, right) -> left + "/" + right);
		return compact
			+ "|"
			+ quickCount
			+ "|"
			+ showRecent
			+ "|"
			+ session.currentPlayerHost()
			+ "|"
			+ variants;
	}

	private void updatePrimaryAction(
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		this.actionButton.active = console
			.map(snapshot -> snapshot.activeFlow().isPresent()
				|| recommendedAction(snapshot).isPresent()
				|| !snapshot.actions().isEmpty())
			.orElse(false);
		Component tooltip = console
			.flatMap(snapshot -> {
				if (snapshot.activeFlow().isPresent()) {
					return Optional.of(Component.literal("查看流程进度、阻塞状态与未完成人员"));
				}
				return recommendedAction(snapshot)
					.map(action -> ConsoleText.parse(
						action.descriptionJson(),
						action.operationId().toString()
					));
			})
			.orElseGet(() -> Component.literal(
				ClientConsoleState.pending()
					? "正在等待服务端返回控制台上下文"
					: "当前上下文没有可执行的首要操作"
			));
		this.actionButton.setTooltip(Tooltip.create(tooltip));
	}

	private void openPrimaryAction() {
		ConsoleSnapshotS2CPayload console = visibleConsole(
			ClientSessionState.snapshot()
		).orElse(null);
		if (console == null) {
			return;
		}
		if (console.activeFlow().isPresent()) {
			pushScreen(new ActiveFlowScreen(this));
			return;
		}
		Optional<ActionEntry> action = recommendedAction(console);
		if (action.isPresent()) {
			dispatchAction(action.orElseThrow());
			return;
		}
		if (!console.actions().isEmpty()) {
			pushScreen(new ActionCatalogScreen(this));
		}
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float tickProgress) {
		graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float tickProgress) {
		NavigationFrame transition = beginNavigationFrame(graphics);
		try {
			Snapshot snapshot = ClientSessionState.snapshot();
			Optional<ConsoleSnapshotS2CPayload> console = visibleConsole(snapshot);
			int panelX = panelX();
			int panelY = panelY();
			int panelWidth = panelWidth();
			int panelHeight = panelHeight();
			int innerX = panelX + 16;
			int innerWidth = panelWidth - 32;
			boolean compact = panelWidth < 560 || panelHeight < 296;

			drawShell(
				graphics,
				snapshot,
				console,
				panelX,
				panelY,
				panelWidth,
				panelHeight,
				innerX,
				innerWidth
			);
			if (compact) {
				drawCompactContent(
					graphics,
					snapshot,
					console,
					panelX,
					panelY,
					panelWidth,
					panelHeight,
					innerX,
					innerWidth
				);
			} else {
				drawFullContent(
					graphics,
					snapshot,
					console,
					panelX,
					panelY,
					panelWidth,
					panelHeight,
					innerX,
					innerWidth
				);
			}

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

	private void drawShell(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int panelX,
		final int panelY,
		final int panelWidth,
		final int panelHeight,
		final int innerX,
		final int innerWidth
	) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
		graphics.fill(panelX, panelY, panelX + 4, panelY + panelHeight, BRAND_GOLD);
		graphics.fill(panelX + 4, panelY, panelX + panelWidth, panelY + 2, INFO_CYAN);

		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.screen.eyebrow"),
			innerX,
			panelY + 11,
			SECONDARY_TEXT,
			false
		);
		drawScaledText(
			graphics,
			Component.translatable(
				snapshot.currentPlayerHost()
					? "pixel_tzz_pro.screen.host.title"
					: snapshot.adminEligible()
						? "pixel_tzz_pro.screen.admin.title"
						: "pixel_tzz_pro.screen.player.title"
			),
			innerX,
			panelY + 26,
			1.25F,
			MAIN_TEXT,
			true
		);

		Component statusText = Component.translatable(snapshot.status().translationKey());
		int statusWidth = this.font.width(statusText) + 23;
		int statusX = panelX + panelWidth - statusWidth - 14;
		int statusColor = statusColor(snapshot.status());
		int statusY = panelY + 15;
		graphics.fill(statusX, statusY, statusX + statusWidth, statusY + 17, 0xD5202B35);
		graphics.outline(statusX, statusY, statusWidth, 17, withAlpha(statusColor, 190));
		graphics.fill(statusX + 6, statusY + 6, statusX + 11, statusY + 11, statusColor);
		graphics.text(this.font, statusText, statusX + 15, statusY + 5, statusColor, false);

		drawServerSyncBadge(graphics, statusX - 6, statusY);

		graphics.fill(innerX, panelY + 49, innerX + innerWidth, panelY + 50, 0xFF33424E);
		int footerTop = panelY + panelHeight - footerHeight();
		graphics.fill(innerX, footerTop, innerX + innerWidth, footerTop + 1, 0xFF33424E);
	}

	private void drawFullContent(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int panelX,
		final int panelY,
		final int panelWidth,
		final int panelHeight,
		final int innerX,
		final int innerWidth
	) {
		int mainX = innerX + FULL_PHASE_RAIL_WIDTH + FULL_PHASE_RAIL_GAP;
		int mainWidth = innerWidth - FULL_PHASE_RAIL_WIDTH - FULL_PHASE_RAIL_GAP;
		int contentTop = panelY + 61;

		drawCurrentPhaseMarker(
			graphics,
			snapshot,
			console,
			innerX,
			contentTop,
			FULL_PHASE_RAIL_WIDTH,
			Math.max(52, panelY + panelHeight - footerHeight() - 8 - contentTop)
		);
		drawPhaseHero(graphics, snapshot, console, mainX, contentTop, mainWidth);

		int cardY = panelY + 121;
		int gap = 7;
		int cardWidth = (mainWidth - gap * 2) / 3;
		drawDefinitionCard(graphics, snapshot, mainX, cardY, cardWidth, 57);
		drawHostCard(
			graphics,
			snapshot,
			console,
			mainX + cardWidth + gap,
			cardY,
			cardWidth,
			57
		);
		drawPlayersCard(
			graphics,
			console,
			mainX + (cardWidth + gap) * 2,
			cardY,
			cardWidth,
			57
		);

		drawActionZone(
			graphics,
			console,
			mainX,
			this.actionZoneY,
			mainWidth,
			this.actionZoneHeight
		);
	}

	private void drawCompactContent(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int panelX,
		final int panelY,
		final int panelWidth,
		final int panelHeight,
		final int innerX,
		final int innerWidth
	) {
		int contentTop = panelY + 58;
		int actionZoneY = this.actionZoneY;
		drawCompactPhaseMarker(graphics, snapshot, console, innerX, contentTop, innerWidth);
		if (actionZoneY - contentTop < 68) {
			drawActionZone(
				graphics,
				console,
				innerX,
				actionZoneY,
				innerWidth,
				this.actionZoneHeight
			);
			return;
		}
		drawPhaseHero(graphics, snapshot, console, innerX, contentTop + 17, innerWidth);

		int cardY = contentTop + 72;
		if (cardY + 45 <= actionZoneY - 6) {
			int gap = 5;
			int cardWidth = (innerWidth - gap * 2) / 3;
			drawDefinitionCard(graphics, snapshot, innerX, cardY, cardWidth, 45);
			drawHostCard(
				graphics,
				snapshot,
				console,
				innerX + cardWidth + gap,
				cardY,
				cardWidth,
				45
			);
			drawPlayersCard(
				graphics,
				console,
				innerX + (cardWidth + gap) * 2,
				cardY,
				cardWidth,
				45
			);
		}
		drawActionZone(
			graphics,
			console,
			innerX,
			actionZoneY,
			innerWidth,
			this.actionZoneHeight
		);
	}

	private void drawPhaseHero(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int x,
		final int y,
		final int width
	) {
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.screen.current_phase"),
			x,
			y,
			SECONDARY_TEXT,
			false
		);
		Component sequence = Component.literal(phaseSequenceLabel(snapshot, console));
		graphics.text(this.font, sequence, x + width - this.font.width(sequence), y, MUTED_TEXT, false);
		Component phaseName = currentPhaseName(snapshot, console);
		drawScaledText(
			graphics,
			phaseName,
			x,
			y + 15,
			1.45F,
			phaseColor(snapshot),
			true
		);
		Component phaseDetail = console
			.filter(value -> value.gameId().isPresent())
			.map(value -> Component.literal(
				ConsoleText.parse(
					value.gameNameJson(),
					value.gameId().orElseThrow().toString()
				).getString()
					+ "  ·  状态版本 "
					+ value.stateRevision()
					+ "  ·  审计记录 "
					+ value.auditEventCount()
			))
			.orElseGet(() -> Component.literal(
				phaseSyncPending(snapshot, console)
					? "正在获取服务端阶段定义……"
					: snapshot.phaseId().isPresent()
						? "当前阶段由服务端数据包定义。"
						: "当前尚未配置活动阶段。"
			));
		graphics.textWithWordWrap(this.font, phaseDetail, x, y + 37, width, SECONDARY_TEXT, false);
	}

	private void drawCurrentPhaseMarker(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		List<PhaseEntry> route = console
			.map(ConsoleSnapshotS2CPayload::phaseRoute)
			.orElseGet(List::of);
		if (!route.isEmpty()) {
			drawPhaseRouteRail(graphics, route, x, y, width, height);
			return;
		}
		int lineX = x + 4;
		int color = phaseColor(snapshot);
		graphics.fill(lineX, y + 3, lineX + 1, y + 47, 0xFF3B4A56);
		graphics.fill(lineX - 3, y, lineX + 4, y + 7, withAlpha(color, 64));
		graphics.fill(lineX - 1, y + 2, lineX + 2, y + 5, color);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.screen.current_phase"),
			x + 15,
			y - 3,
			SECONDARY_TEXT,
			false
		);
		Component phaseName = currentPhaseName(snapshot, console);
		graphics.textWithWordWrap(
			this.font,
			phaseName,
			x + 15,
			y + 11,
			Math.max(1, width - 15),
			color,
			true
		);
		Component phaseState = currentPhaseId(snapshot, console)
			.<Component>map(value -> Component.literal("由数据包定义"))
			.orElseGet(() -> Component.literal(
				phaseSyncPending(snapshot, console) ? "正在准备" : "尚未配置"
			));
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(phaseState.getString(), Math.max(1, width - 15)),
			x + 15,
			y + 36,
			MUTED_TEXT,
			false
		);
	}

	/**
	 * Shows one previous phase, the current phase, and as many future phases as the viewport allows.
	 * Ellipsis markers expose omitted history/future without adding a route-end fade.
	 */
	private void drawPhaseRouteRail(
		final GuiGraphicsExtractor graphics,
		final List<PhaseEntry> route,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		int current = 0;
		for (int index = 0; index < route.size(); index++) {
			if (route.get(index).relation().equals("current")) {
				current = index;
				break;
			}
		}
		int headerHeight = 16;
		int rowStep = 24;
		int capacity = Math.max(1, (height - headerHeight - 5) / rowStep);
		int first = Math.max(0, current - 1);
		int last = Math.min(route.size(), first + capacity);
		if (last - first < capacity && first > 0) {
			first = Math.max(0, last - capacity);
		}
		boolean hiddenAbove = first > 0;
		boolean hiddenBelow = last < route.size();
		int lineX = x + 4;
		int top = y + headerHeight;
		int bottom = Math.min(y + height - 2, top + Math.max(1, last - first) * rowStep);
		graphics.text(this.font, "游戏阶段", x + 8, y - 2, SECONDARY_TEXT, false);
		graphics.text(this.font, "PHASE ROUTE", x + 8, y + 8, MUTED_TEXT, false);
		graphics.fill(lineX, top, lineX + 1, bottom, 0xFF34444F);
		for (int index = first; index < last; index++) {
			PhaseEntry phase = route.get(index);
			int row = index - first;
			int rowCenterY = top + row * rowStep + 7;
			int color = switch (phase.relation()) {
				case "completed" -> withAlpha(BRAND_GOLD, 145);
				case "current" -> phaseColor(ClientSessionState.snapshot());
				default -> MUTED_TEXT;
			};
			if (phase.relation().equals("current")) {
				graphics.fill(
					lineX - 4,
					rowCenterY - 4,
					lineX + 5,
					rowCenterY + 5,
					withAlpha(color, 44)
				);
				graphics.outline(lineX - 3, rowCenterY - 3, 7, 7, color);
				graphics.fill(
					lineX - 1,
					rowCenterY - 1,
					lineX + 2,
					rowCenterY + 2,
					MAIN_TEXT
				);
			} else if (phase.relation().equals("completed")) {
				graphics.fill(
					lineX - 2,
					rowCenterY - 2,
					lineX + 3,
					rowCenterY + 3,
					color
				);
			} else {
				graphics.outline(
					lineX - 2,
					rowCenterY - 2,
					5,
					5,
					withAlpha(color, 150)
				);
			}
			String label = ConsoleText.formalName(
				ConsoleText.parse(phase.nameJson(), "未命名阶段")
			).getString();
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(label, Math.max(1, width - 17)),
				x + 13,
				centeredTextY(rowCenterY),
				phase.relation().equals("current") ? color : withAlpha(color, 210),
				phase.relation().equals("current")
			);
		}
		if (hiddenAbove) {
			graphics.text(this.font, "…", lineX - 2, top, MUTED_TEXT, false);
		}
		if (hiddenBelow) {
			graphics.text(this.font, "…", lineX - 2, bottom - 9, MUTED_TEXT, false);
		}
	}

	private void drawCompactPhaseMarker(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int x,
		final int y,
		final int width
	) {
		int startX = x + 3;
		int endX = x + width - 4;
		int color = phaseColor(snapshot);
		graphics.fill(startX, y + 2, endX, y + 3, 0xFF3B4A56);
		graphics.fill(startX - 3, y - 1, startX + 4, y + 6, withAlpha(color, 64));
		graphics.fill(startX - 1, y + 1, startX + 2, y + 4, color);
		Component name = currentPhaseName(snapshot, console);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(name.getString(), Math.max(1, width - 17)),
			x + 14,
			y - 4,
			color,
			true
		);
	}

	private static Optional<Identifier> currentPhaseId(
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		return console.isPresent() ? console.orElseThrow().phaseId() : snapshot.phaseId();
	}

	private static String phaseSequenceLabel(
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		List<PhaseEntry> route = console
			.map(ConsoleSnapshotS2CPayload::phaseRoute)
			.orElseGet(List::of);
		for (int index = 0; index < route.size(); index++) {
			if (route.get(index).relation().equals("current")) {
				return "PHASE %02d / %02d".formatted(index + 1, route.size());
			}
		}
		return phaseSyncPending(snapshot, console) ? "准备中" : "尚未配置";
	}

	private Component currentPhaseName(
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		if (console.isPresent()) {
			ConsoleSnapshotS2CPayload value = console.orElseThrow();
			return value.phaseId()
				.map(identifier -> ConsoleText.currentObject(
					ConsoleText.parse(value.phaseNameJson(), "未命名阶段")
				))
				.orElseGet(() -> Component.literal("尚未配置阶段"));
		}
		if (phaseSyncPending(snapshot, console)) {
			return Component.literal("正在同步阶段信息……");
		}
		return snapshot.phaseId()
			.<Component>map(value -> Component.literal("活动阶段已配置"))
			.orElseGet(() -> Component.literal("尚未配置阶段"));
	}

	private static boolean phaseSyncPending(
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		return console.isEmpty()
			&& snapshot.currentPlayerHost()
			&& ClientConsoleState.pending();
	}

	private void drawConnectionCard(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		drawStatusCard(
			graphics,
			x,
			y,
			width,
			height,
			Component.translatable("pixel_tzz_pro.screen.connection"),
			Component.translatable(snapshot.status().translationKey()),
			statusColor(snapshot.status()),
			Component.translatable("pixel_tzz_pro.screen.server_version.short", snapshot.serverModVersion())
		);
	}

	private void drawPlayersCard(
		final GuiGraphicsExtractor graphics,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		int total = console.map(value -> value.players().size()).orElse(0);
		long online = console
			.map(value -> value.players().stream().filter(player -> player.online()).count())
			.orElse(0L);
		drawStatusCard(
			graphics,
			x,
			y,
			width,
			height,
			Component.literal("玩家名单"),
			Component.literal("%d / %d 在线".formatted(online, total)),
			console.isPresent() ? SUCCESS : SECONDARY_TEXT,
			Component.literal("身份与初始化状态")
		);
	}

	private void drawDefinitionCard(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		drawStatusCard(
			graphics,
			x,
			y,
			width,
			height,
			Component.translatable("pixel_tzz_pro.screen.definition"),
			definitionStateText(snapshot),
			definitionColor(snapshot),
			Component.translatable(
				"pixel_tzz_pro.screen.definition.detail",
				snapshot.definitionCount(),
				snapshot.definitionGeneration()
			)
		);
	}

	private void drawHostCard(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		var host = console.flatMap(ConsoleSnapshotS2CPayload::host).orElse(null);
		if (host != null) {
			int color = snapshot.currentPlayerHost() ? BRAND_GOLD : INFO_CYAN;
			graphics.fill(x, y, x + width, y + height, SURFACE);
			graphics.outline(x, y, width, height, SURFACE_BORDER);
			graphics.fill(x, y, x + 2, y + height, withAlpha(color, 190));
			graphics.text(
				this.font,
				Component.translatable("pixel_tzz_pro.screen.host"),
				x + 9,
				y + 8,
				SECONDARY_TEXT,
				false
			);
			int headSize = Math.min(22, Math.max(14, height - 29));
			PlayerIdentityRenderer.drawHead(
				graphics,
				host.playerId(),
				host.online(),
				x + 9,
				y + 22,
				headSize
			);
			int textX = x + 17 + headSize;
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(
					host.name() + (host.online() ? "" : " · 离线"),
					Math.max(24, width - (textX - x) - 9)
				),
				textX,
				y + 23,
				host.online() ? color : MUTED_TEXT,
				true
			);
			if (height >= 54) {
				graphics.text(
					this.font,
					Component.translatable(
						snapshot.currentPlayerHost()
							? "pixel_tzz_pro.screen.role.host"
							: snapshot.adminEligible()
								? "pixel_tzz_pro.screen.role.operator"
								: "pixel_tzz_pro.screen.role.player"
					),
					textX,
					y + 39,
					MUTED_TEXT,
					false
				);
			}
			return;
		}
		drawStatusCard(
			graphics,
			x,
			y,
			width,
			height,
			Component.translatable("pixel_tzz_pro.screen.host"),
			hostText(snapshot),
			snapshot.currentPlayerHost() ? BRAND_GOLD : snapshot.hostAssigned() ? INFO_CYAN : SECONDARY_TEXT,
			Component.translatable(
				snapshot.currentPlayerHost()
					? "pixel_tzz_pro.screen.role.host"
					: snapshot.adminEligible()
						? "pixel_tzz_pro.screen.role.operator"
						: "pixel_tzz_pro.screen.role.player"
			)
		);
	}

	private void drawStatusCard(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final Component label,
		final Component value,
		final int valueColor,
		final Component detail
	) {
		graphics.fill(x, y, x + width, y + height, SURFACE);
		graphics.outline(x, y, width, height, SURFACE_BORDER);
		graphics.fill(x, y, x + 2, y + height, withAlpha(valueColor, 190));
		graphics.text(this.font, label, x + 9, y + 8, SECONDARY_TEXT, false);
		graphics.text(this.font, value, x + 9, y + 23, valueColor, true);
		if (height >= 54) {
			graphics.text(this.font, detail, x + 9, y + 39, MUTED_TEXT, false);
		}
	}

	private void drawActionZone(
		final GuiGraphicsExtractor graphics,
		final Optional<ConsoleSnapshotS2CPayload> console,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		boolean feedbackError = ClientConsoleState.feedbackError();
		boolean hasDiagnostics = console
			.map(value -> !value.diagnostics().isEmpty())
			.orElse(false);
		int color = feedbackError || hasDiagnostics ? DANGER : console
			.flatMap(ConsoleSnapshotS2CPayload::activeFlow)
			.map(flow -> flow.callbackFailed() || flow.resourceBlocked()
				? DANGER
				: flow.completed() == flow.total() ? SUCCESS : INFO_CYAN)
			.orElse(BRAND_GOLD);
		graphics.fill(x, y, x + width, y + height, SURFACE);
		graphics.outline(x, y, width, height, withAlpha(INFO_CYAN, 105));
		graphics.fill(x, y, x + 3, y + height, color);
		Optional<ConsoleSnapshotS2CPayload.ActiveFlowSummary> activeFlow = console
			.flatMap(ConsoleSnapshotS2CPayload::activeFlow);
		graphics.text(
			this.font,
			Component.literal(activeFlow.isPresent() ? "当前流程快捷操作" : "当前阶段快捷操作"),
			x + 10,
			y + 7,
			color,
			false
		);
		if (!this.actionZoneShowsSummary) {
			return;
		}
		Component summary = activeFlow
			.<Component>map(flow -> Component.literal(
				ConsoleText.parse(flow.flowNameJson(), flow.flowId().toString()).getString()
					+ "  ·  "
					+ flow.completed()
					+ "/"
					+ flow.total()
					+ " 已完成"
			))
			.orElseGet(() -> Component.literal(
				!ClientConsoleState.feedback().isEmpty()
					? ClientConsoleState.feedback()
					: "优先显示此阶段最可能使用的操作"
			));
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(summary.getString(), width - 20),
			x + 10,
			y + 23,
			SECONDARY_TEXT,
			false
		);
	}

	private void drawScaledText(
		final GuiGraphicsExtractor graphics,
		final Component text,
		final int x,
		final int y,
		final float scale,
		final int color,
		final boolean shadow
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	private int centeredTextY(final int centerY) {
		return centerY - this.font.lineHeight / 2;
	}

	/**
	 * Keeps quick actions inside the cyan action-zone outline. Narrow layouts retain all contextual
	 * actions: they use equal-width columns while readable, then become a vertical stack when the
	 * screen is tall enough to preserve the same safe frame padding.
	 */
	private static ActionZoneLayout actionZoneLayout(
		final int x,
		final int width,
		final int count,
		final boolean compact,
		final int panelY,
		final int panelHeight,
		final int footerHeight
	) {
		int safeCount = Math.max(1, count);
		int footerTop = panelY + panelHeight - footerHeight;
		boolean stackVertically = compact
			&& panelHeight >= 296
			&& width
				< ACTION_ZONE_HORIZONTAL_PADDING * 2
					+ safeCount * QUICK_ACTION_MIN_COLUMN_WIDTH
					+ (safeCount - 1) * QUICK_ACTION_GAP;
		if (stackVertically) {
			int buttonsHeight = safeCount * QUICK_ACTION_HEIGHT
				+ (safeCount - 1) * QUICK_ACTION_GAP;
			int zoneHeight = 18 + buttonsHeight + ACTION_ZONE_BUTTON_BOTTOM_PADDING;
			int zoneY = footerTop - 1 - zoneHeight;
			int horizontalPadding = Math.min(
				ACTION_ZONE_HORIZONTAL_PADDING,
				Math.max(0, (width - 1) / 2)
			);
			int slotWidth = Math.max(1, width - horizontalPadding * 2);
			List<ActionSlot> slots = new ArrayList<>(safeCount);
			for (int index = 0; index < safeCount; index++) {
				slots.add(new ActionSlot(
					x + horizontalPadding,
					zoneY + 18 + index * (QUICK_ACTION_HEIGHT + QUICK_ACTION_GAP),
					slotWidth,
					QUICK_ACTION_HEIGHT
				));
			}
			return new ActionZoneLayout(zoneY, zoneHeight, false, List.copyOf(slots));
		}
		int zoneY = compact ? footerTop - 1 - COMPACT_ACTION_ZONE_HEIGHT : panelY + 187;
		int zoneBottom = compact ? footerTop - 1 : footerTop - 8;
		int zoneHeight = compact
			? COMPACT_ACTION_ZONE_HEIGHT
			: Math.max(30, zoneBottom - zoneY);
		int buttonY = compact
			? zoneY + 18
			: Math.min(
				panelY + 233,
				zoneBottom - ACTION_ZONE_BUTTON_BOTTOM_PADDING - QUICK_ACTION_HEIGHT
			);
		int horizontalPadding = Math.min(
			ACTION_ZONE_HORIZONTAL_PADDING,
			Math.max(0, (width - safeCount) / 2)
		);
		int innerWidth = Math.max(
			safeCount,
			width - horizontalPadding * 2
		);
		int gap = safeCount == 1
			? 0
			: Math.min(
				QUICK_ACTION_GAP,
				Math.max(0, (innerWidth - safeCount) / (safeCount - 1))
			);
		int distributableWidth = Math.max(
			safeCount,
			innerWidth - gap * (safeCount - 1)
		);
		int baseWidth = distributableWidth / safeCount;
		int remainder = distributableWidth % safeCount;
		int cursorX = x + horizontalPadding;
		List<ActionSlot> slots = new ArrayList<>(safeCount);
		for (int index = 0; index < safeCount; index++) {
			int slotWidth = baseWidth + (index < remainder ? 1 : 0);
			slots.add(new ActionSlot(cursorX, buttonY, slotWidth, QUICK_ACTION_HEIGHT));
			cursorX += slotWidth + gap;
		}
		return new ActionZoneLayout(
			zoneY,
			zoneHeight,
			!compact && zoneHeight >= ACTION_ZONE_SUMMARY_MIN_HEIGHT,
			List.copyOf(slots)
		);
	}

	private static Component hostText(final Snapshot snapshot) {
		if (!snapshot.hostAssigned()) {
			return Component.translatable("pixel_tzz_pro.screen.host.unassigned");
		}
		return Component.translatable(
			snapshot.currentPlayerHost() ? "pixel_tzz_pro.screen.host.current" : "pixel_tzz_pro.screen.host.other"
		);
	}

	private Component primaryActionLabel(
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		if (console.isEmpty()) {
			return Component.literal(
				ClientConsoleState.pending() ? "正在同步控制台…" : "控制台暂不可用"
			);
		}
		ConsoleSnapshotS2CPayload snapshot = console.orElseThrow();
		if (snapshot.activeFlow().isPresent()) {
			ConsoleSnapshotS2CPayload.ActiveFlowSummary flow = snapshot.activeFlow().orElseThrow();
			return Component.literal(
				"查看当前流程  ·  " + flow.completed() + "/" + flow.total()
			);
		}
		Optional<ActionEntry> recommended = recommendedAction(snapshot);
		if (recommended.isPresent()) {
			ActionEntry action = recommended.orElseThrow();
			if (actionRouting(action.operationId())) {
				return Component.literal("正在审阅…");
			}
			return ConsoleText.parse(action.labelJson(), action.operationId().toString());
		}
		return Component.literal(
			snapshot.actions().isEmpty() ? "当前没有可执行操作" : "查看受限操作"
		);
	}

	private static Optional<ActionEntry> recommendedAction(
		final ConsoleSnapshotS2CPayload snapshot
	) {
		return snapshot.actions()
			.stream()
			.filter(ActionEntry::enabled)
			.filter(ControlConsoleScreen::contextualAction)
			.sorted(
				Comparator.<ActionEntry>comparingInt(action -> contextPriority(snapshot, action))
					.thenComparingInt(ActionEntry::order)
			)
			.findFirst();
	}

	private static List<ActionEntry> contextQuickActions(
		final ConsoleSnapshotS2CPayload snapshot
	) {
		var primary = snapshot.activeFlow().isPresent()
			? Optional.<net.minecraft.resources.Identifier>empty()
			: recommendedAction(snapshot).map(ActionEntry::operationId);
		return snapshot.actions()
			.stream()
			.filter(ActionEntry::enabled)
			.filter(ControlConsoleScreen::contextualAction)
			.filter(action -> primary.filter(action.operationId()::equals).isEmpty())
			.sorted(
				Comparator.<ActionEntry>comparingInt(action -> contextPriority(snapshot, action))
					.thenComparingInt(ActionEntry::order)
			)
			.filter(action ->
				snapshot.activeFlow().isEmpty()
					|| action.operationType().equals("add_flow_members")
					|| action.operationType().equals("remove_flow_members")
					|| action.operationType().equals("cancel_flow")
					|| action.operationType().equals("retry_callback")
			)
			.limit(2)
			.toList();
	}

	private static boolean contextualAction(final ActionEntry action) {
		return !action.operationType().equals("clear_all_state")
			&& !action.operationType().equals("interrupt_timeline");
	}

	private static int contextPriority(
		final ConsoleSnapshotS2CPayload snapshot,
		final ActionEntry action
	) {
		if (snapshot.activeFlow().isPresent()) {
			return switch (action.operationType()) {
				case "add_flow_members" -> 0;
				case "remove_flow_members" -> 1;
				case "retry_callback" -> 2;
				case "cancel_flow" -> 3;
				default -> 4;
			};
		}
		return switch (action.section()) {
			case "primary" -> 0;
			case "roles" -> 1;
			case "system" -> 2;
			default -> 3;
		};
	}

	private static boolean previewAvailable(final Snapshot snapshot) {
		return snapshot.adminEligible()
			&& snapshot.definitionHealthy()
			&& snapshot.gameDefinitionCount() > 0;
	}

	private static Component definitionStateText(final Snapshot snapshot) {
		if (!snapshot.schemaCompatible()) {
			return Component.translatable("pixel_tzz_pro.screen.definition.incompatible");
		}
		return Component.translatable(
			"pixel_tzz_pro.screen.definition."
				+ switch (snapshot.definitionStatus()) {
					case "ready" -> "ready";
					case "invalid" -> "invalid";
					case "platform_reload_failed" -> "reload_failed";
					default -> "empty";
				}
		);
	}

	private static Component summaryText(final Snapshot snapshot) {
		if (!snapshot.schemaCompatible()) {
			return Component.translatable("pixel_tzz_pro.screen.schema_incompatible");
		}
		if (snapshot.definitionStatus().equals("invalid")) {
			return Component.translatable(
				"pixel_tzz_pro.screen.definition_invalid",
				snapshot.definitionDiagnostic()
			);
		}
		if (snapshot.definitionStatus().equals("platform_reload_failed")) {
			return Component.translatable("pixel_tzz_pro.screen.definition_platform_failed");
		}
		if (snapshot.gameDefinitionCount() == 0) {
			return Component.translatable("pixel_tzz_pro.screen.definition_empty");
		}
		return Component.translatable(
			snapshot.currentPlayerHost()
				? "pixel_tzz_pro.screen.host.summary"
				: snapshot.adminEligible()
					? "pixel_tzz_pro.screen.operator.summary"
					: "pixel_tzz_pro.screen.player.summary"
		);
	}

	private static int phaseColor(final Snapshot snapshot) {
		if (!snapshot.schemaCompatible() || snapshot.status() == ConnectionStatus.INCOMPATIBLE) {
			return DANGER;
		}
		return INFO_CYAN;
	}

	private static int definitionColor(final Snapshot snapshot) {
		if (!snapshot.schemaCompatible() || snapshot.definitionStatus().equals("invalid")) {
			return DANGER;
		}
		if (snapshot.definitionStatus().equals("platform_reload_failed")) {
			return WARNING;
		}
		return snapshot.definitionHealthy() ? SUCCESS : SECONDARY_TEXT;
	}

	private static int summaryColor(final Snapshot snapshot) {
		if (!snapshot.schemaCompatible() || snapshot.definitionStatus().equals("invalid")) {
			return DANGER;
		}
		return snapshot.definitionStatus().equals("platform_reload_failed") ? WARNING : SECONDARY_TEXT;
	}

	private static int statusColor(final ConnectionStatus status) {
		return switch (status) {
			case READY -> SUCCESS;
			case WAITING -> WARNING;
			case INCOMPATIBLE -> DANGER;
			case OFFLINE -> SECONDARY_TEXT;
		};
	}

	private int panelWidth() {
		return Math.max(1, Math.min(620, designWidth() - 12));
	}

	private int panelHeight() {
		return Math.max(1, Math.min(330, designHeight() - 12));
	}

	private int footerHeight() {
		return panelWidth() < 420 ? 72 : 43;
	}

	private int panelX() {
		return (designWidth() - panelWidth()) / 2;
	}

	private int panelY() {
		return (designHeight() - panelHeight()) / 2;
	}

	@Override
	public void onClose() {
		closeToParent();
	}

	private record ActionSlot(int x, int y, int width, int height) {
	}

	private record ActionZoneLayout(
		int zoneY,
		int zoneHeight,
		boolean showSummary,
		List<ActionSlot> slots
	) {
	}

}
