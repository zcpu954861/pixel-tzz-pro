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
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActiveFlowSummary;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.MemberFieldSummary;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.MemberSummary;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Host-facing progress surface; member rows are drawn virtually from a bounded 64-player snapshot.
 */
final class ActiveFlowScreen extends TransitioningConsoleScreen {
	private static final int ROW_HEIGHT = 36;
	private static final int ROW_GAP = 4;
	private static final DateTimeFormatter CREATED_AT = DateTimeFormatter
		.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private long observedRevision = Long.MIN_VALUE;
	private boolean unfinishedOnly = true;
	private int firstVisible;
	private int visibleRows;
	private String widgetStructureKey = "";
	private ConsoleButton addButton;
	private ConsoleButton filterButton;
	private ConsoleButton refreshButton;
	private ConsoleButton cancelButton;
	private final java.util.ArrayList<RemoveBinding> removeButtons = new java.util.ArrayList<>();

	ActiveFlowScreen(final Screen parent) {
		super(Component.literal("当前强制流程"), parent, Motion.PUSH_ENTER);
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
		this.removeButtons.clear();
		this.addButton = null;
		this.filterButton = null;
		this.refreshButton = null;
		this.cancelButton = null;
		int x = panelX();
		int y = panelY();
		int width = panelWidth();
		int height = panelHeight();
		int listTop = flowListTop();
		int footerTop = y + height - footerHeight();
		this.visibleRows = Math.max(1, (footerTop - listTop) / (ROW_HEIGHT + ROW_GAP));
		List<MemberSummary> members = visibleMembers();
		this.firstVisible = Math.clamp(
			this.firstVisible,
			0,
			Math.max(0, members.size() - this.visibleRows)
		);
		ActionEntry addAction = action("add_flow_members");
		if (addAction != null) {
			int addWidth = width < 520 ? 58 : 82;
			ConsoleButton add = new ConsoleButton(
				x + width - 18 - addWidth,
				listTop - 28,
				addWidth,
				22,
				Component.literal(
					actionRouting(addAction.operationId())
						? "审阅中…"
						: width < 520 ? "+ 添加" : "+ 添加玩家"
				),
				button -> dispatchAction(addAction),
				Variant.PRIMARY
			);
			add.active = addAction.enabled();
			this.addRenderableWidget(add);
			this.addButton = add;
		}
		ActionEntry removeAction = action("remove_flow_members");
		if (removeAction != null) {
			int removeWidth = width < 520 ? 52 : 68;
			for (
				int index = this.firstVisible;
				index < Math.min(members.size(), this.firstVisible + this.visibleRows);
				index++
			) {
				MemberSummary member = members.get(index);
				if (!removable(member)) {
					continue;
				}
				int rowY = listTop + (index - this.firstVisible) * (ROW_HEIGHT + ROW_GAP);
				ConsoleButton remove = new ConsoleButton(
					x + width - 22 - removeWidth,
					rowY + 6,
					removeWidth,
					24,
					Component.literal(
						actionRouting(removeAction.operationId()) ? "审阅中…" : "移除"
					),
					button -> dispatchAction(
						removeAction,
						List.of(member.player().playerId())
					),
					Variant.DANGER
				);
				remove.active = removeAction.enabled();
				this.addRenderableWidget(remove);
				this.removeButtons.add(new RemoveBinding(member.player().playerId(), remove));
			}
		}

		int footerY = footerTop + 7;
		int available = width - 36;
		int gap = 6;
		boolean stacked = width < 560;
		int filterWidth = stacked ? (available - gap) / 2 : 112;
		int refreshWidth = stacked ? available - filterWidth - gap : 74;
		ConsoleButton filter = new ConsoleButton(
			x + 18,
			footerY,
			filterWidth,
			24,
			Component.literal(this.unfinishedOnly ? "显示全部成员" : "仅看未完成"),
			button -> {
				this.unfinishedOnly = !this.unfinishedOnly;
				this.firstVisible = 0;
				rebuildWidgets();
			},
			Variant.NORMAL
		);
		ActiveFlowSummary flow = flowSnapshot();
		filter.active = flow != null && flow.completed() < flow.total();
		this.addRenderableWidget(filter);
		this.filterButton = filter;

		ConsoleButton refresh = new ConsoleButton(
			x + 18 + filterWidth + gap,
			footerY,
			refreshWidth,
			24,
			Component.literal("刷新"),
			button -> ClientConsoleState.requestConsole(),
			Variant.NORMAL
		);
		refresh.active = true;
		this.addRenderableWidget(refresh);
		this.refreshButton = refresh;

		int secondaryY = stacked ? footerY + 29 : footerY;
		int secondaryX = stacked
			? x + 18
			: x + 18 + filterWidth + refreshWidth + gap * 2;
		var cancelAction = ClientConsoleState.snapshot()
			.stream()
			.flatMap(snapshot -> snapshot.actions().stream())
			.filter(action -> action.operationType().equals("cancel_flow"))
			.findFirst();
		if (cancelAction.isPresent()) {
			ActionEntry cancel = cancelAction.orElseThrow();
			ConsoleButton cancelButton = new ConsoleButton(
				secondaryX,
				secondaryY,
				stacked ? Math.max(1, (available - gap * 2) / 3) : 110,
				24,
				Component.literal(
					actionRouting(cancel.operationId()) ? "正在审阅…" : "终止当前流程"
				),
				button -> dispatchAction(cancel),
				Variant.DANGER
			);
			cancelButton.active = cancel.enabled();
			this.addRenderableWidget(cancelButton);
			this.cancelButton = cancelButton;
			secondaryX += cancelButton.getWidth() + gap;
		}

		int remainingWidth = x + width - 18 - secondaryX;
		int backWidth = stacked
			? Math.max(1, (available - gap * 2) / 3)
			: 76;
		int operationsWidth = stacked
			? Math.max(1, remainingWidth - backWidth - gap)
			: 94;
		ConsoleButton operations = new ConsoleButton(
			secondaryX,
			secondaryY,
			operationsWidth,
			24,
			Component.literal("其他流程操作"),
			button -> pushScreen(
				new ActionCatalogScreen(
					this,
					Set.of("add_flow_members", "remove_flow_members")
				)
			),
			Variant.PRIMARY
		);
		this.addRenderableWidget(operations);
		ConsoleButton back = new ConsoleButton(
			x + width - backWidth - 18,
			secondaryY,
			backWidth,
			24,
			Component.literal("返回"),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(back);
		this.widgetStructureKey = widgetStructureKey();
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
			if (!this.widgetStructureKey.equals(widgetStructureKey())) {
				rebuildWidgets();
			} else {
				refreshWidgets();
			}
		}
	}

	private void refreshWidgets() {
		ActionEntry add = action("add_flow_members");
		if (this.addButton != null && add != null) {
			this.addButton.setMessage(
				Component.literal(
					actionRouting(add.operationId())
						? "审阅中…"
						: panelWidth() < 520 ? "+ 添加" : "+ 添加玩家"
				)
			);
			this.addButton.active = add.enabled();
		}
		ActionEntry remove = action("remove_flow_members");
		for (RemoveBinding binding : this.removeButtons) {
			binding.button().setMessage(
				Component.literal(
					remove != null && actionRouting(remove.operationId()) ? "审阅中…" : "移除"
				)
			);
			binding.button().active = remove != null && remove.enabled();
		}
		ActiveFlowSummary flow = flowSnapshot();
		if (this.filterButton != null) {
			this.filterButton.active = flow != null && flow.completed() < flow.total();
		}
		if (this.refreshButton != null) {
			this.refreshButton.active = true;
		}
		ActionEntry cancel = action("cancel_flow");
		if (this.cancelButton != null && cancel != null) {
			this.cancelButton.setMessage(
				Component.literal(
					actionRouting(cancel.operationId()) ? "正在审阅…" : "终止当前流程"
				)
			);
			this.cancelButton.active = cancel.enabled();
		}
	}

	private String widgetStructureKey() {
		StringBuilder key = new StringBuilder()
			.append(panelWidth())
			.append('|')
			.append(panelHeight())
			.append('|')
			.append(this.unfinishedOnly)
			.append('|')
			.append(this.firstVisible);
		ActiveFlowSummary flow = flowSnapshot();
		key.append('|').append(flow == null ? "none" : flow.instanceId());
		for (String operation : List.of(
			"add_flow_members",
			"remove_flow_members",
			"cancel_flow"
		)) {
			ActionEntry action = action(operation);
			key.append('|').append(operation).append('=');
			if (action != null) {
				key.append(action.operationId());
			}
		}
		List<MemberSummary> members = visibleMembers();
		int end = Math.min(members.size(), this.firstVisible + Math.max(1, this.visibleRows));
		for (int index = this.firstVisible; index < end; index++) {
			MemberSummary member = members.get(index);
			key.append('|')
				.append(member.player().playerId())
				.append(':')
				.append(removable(member));
		}
		return key.toString();
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
			Math.max(0, visibleMembers().size() - this.visibleRows)
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
			int listTop = flowListTop();
			int listBottom = y + height - footerHeight();

			graphics.fill(x, y, x + width, y + height, PANEL);
			graphics.outline(x, y, width, height, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + height, INFO_CYAN);
			graphics.fill(x + 4, y, x + width, y + 2, BRAND_GOLD);
			graphics.text(this.font, "FLOW TELEMETRY // 强制流程遥测", x + 18, y + 12, INFO_CYAN, false);
			drawServerSyncBadge(graphics, x + width - 14, y + 10);

			ActiveFlowSummary flow = flowSnapshot();
			if (flow == null) {
				renderEmpty(graphics, x, y, width, height);
			} else {
				renderFlowHeader(graphics, flow, x, y, width, compactHeader());
				renderMembers(graphics, flow, x, width, listTop, listBottom);
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

	private void renderEmpty(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		graphics.text(this.font, "当前没有活动强制流程", x + 18, y + 35, MAIN_TEXT, true);
		int contentBottom = y + height - footerHeight() - 7;
		graphics.fill(x + 18, y + 61, x + width - 18, contentBottom, SURFACE);
		graphics.outline(
			x + 18,
			y + 61,
			width - 36,
			Math.max(1, contentBottom - y - 61),
			SURFACE_BORDER
		);
		graphics.textWithWordWrap(
			this.font,
			Component.literal(
			ClientConsoleState.pending()
					? "正在向服务端重新确认当前流程状态…"
					: "当前没有活动或最近结束的强制流程。返回控制台可选择当前阶段的下一项操作。"
			),
			x + 32,
			y + 78,
			width - 64,
			ClientConsoleState.pending() ? INFO_CYAN : SECONDARY_TEXT,
			false
		);
	}

	private void renderFlowHeader(
		final GuiGraphicsExtractor graphics,
		final ActiveFlowSummary flow,
		final int x,
		final int y,
		final int width,
		final boolean compact
	) {
		Component name = ConsoleText.parse(flow.flowNameJson(), flow.flowId().toString());
		int statusColor = flow.callbackFailed() || flow.resourceBlocked()
			? DANGER
			: flow.completed() == flow.total() ? SUCCESS : INFO_CYAN;
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(name.getString(), Math.max(80, width - 190)),
			x + 18,
			y + 31,
			MAIN_TEXT,
			true
		);
		String shortId = flow.instanceId().toString().substring(0, 8).toUpperCase();
		String status = statusLabel(flow.status());
		String statusText = "#" + shortId + "  ·  " + status;
		graphics.text(
			this.font,
			statusText,
			x + width - 18 - this.font.width(statusText),
			y + 31,
			statusColor,
			false
		);
		PlayerIdentityRenderer.drawHead(
			graphics,
			flow.initiatedBy().playerId(),
			flow.initiatedBy().online(),
			x + 18,
			y + 45,
			14
		);
		String creation = "由 %s 于 %s 发起   ·   名单版本 %d"
			.formatted(
				flow.initiatedBy().name(),
				CREATED_AT.format(Instant.ofEpochMilli(flow.createdAtEpochMillis())),
				flow.instanceRevision()
			);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(creation, width - 36),
			x + 38,
			y + 48,
			SECONDARY_TEXT,
			false
		);
		drawProgress(graphics, flow, x + 18, y + (compact ? 61 : 69), width - 36, statusColor);
		String alert = flow.resourceBlocked()
			? "资源阻塞"
			: flow.callbackFailed()
				? "成员状态已完成，但数据包回调失败；修复后可安全重试"
				: flow.status().equals("completed")
					? "流程已完成"
					: flow.status().equals("canceled")
						? "流程已取消"
						: "名单按服务端快照锁定";
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(
				"%d / %d 已完成  ·  %d%%  ·  %s".formatted(
					flow.completed(),
					flow.total(),
					flow.total() == 0 ? 0 : flow.completed() * 100 / flow.total(),
					alert
				),
				width - 36
			),
			x + 18,
			y + (compact ? 79 : 91),
			flow.resourceBlocked() || flow.callbackFailed() ? DANGER : SECONDARY_TEXT,
			false
		);
		graphics.text(
			this.font,
			flow.completed() == flow.total()
				? "已完成人员"
				: flow.status().equals("canceled")
					? "终止时成员"
					: this.unfinishedOnly ? "未完成人员" : "全部成员",
			x + 18,
			y + (compact ? 96 : 109),
			BRAND_GOLD,
			false
		);
	}

	private void drawProgress(
		final GuiGraphicsExtractor graphics,
		final ActiveFlowSummary flow,
		final int x,
		final int y,
		final int width,
		final int color
	) {
		graphics.fill(x, y, x + width, y + 10, 0xFF121B23);
		graphics.outline(x, y, width, 10, SURFACE_BORDER);
		int filled = flow.total() == 0 ? 0 : width * flow.completed() / flow.total();
		if (filled > 2) {
			graphics.fill(x + 1, y + 1, x + filled - 1, y + 9, withAlpha(color, 210));
			graphics.fill(x + 1, y + 1, x + filled - 1, y + 3, withAlpha(BRAND_GOLD, 150));
		}
		int segments = Math.min(16, Math.max(1, flow.total()));
		for (int index = 1; index < segments; index++) {
			int marker = x + width * index / segments;
			graphics.fill(marker, y + 2, marker + 1, y + 8, withAlpha(MAIN_TEXT, 54));
		}
	}

	private void renderMembers(
		final GuiGraphicsExtractor graphics,
		final ActiveFlowSummary flow,
		final int x,
		final int width,
		final int listTop,
		final int listBottom
	) {
		List<MemberSummary> members = visibleMembers();
		graphics.fill(x + 12, listTop - 4, x + width - 12, listBottom, SURFACE);
		graphics.outline(
			x + 12,
			listTop - 4,
			width - 24,
			Math.max(1, listBottom - listTop + 4),
			SURFACE_BORDER
		);
		if (members.isEmpty()) {
			graphics.text(
				this.font,
				this.unfinishedOnly ? "所有参与者均已结束该流程" : "此流程没有成员记录",
				x + 28,
				listTop + 17,
				this.unfinishedOnly ? SUCCESS : MUTED_TEXT,
				false
			);
		}
		for (
			int index = this.firstVisible;
			index < Math.min(members.size(), this.firstVisible + this.visibleRows);
			index++
		) {
			MemberSummary member = members.get(index);
			int rowY = listTop + (index - this.firstVisible) * (ROW_HEIGHT + ROW_GAP);
			int color = memberColor(member);
			String node = finished(member.status())
				? "已完成「"
					+ ConsoleText.parse(flow.flowNameJson(), "当前流程").getString()
					+ "」"
				: ConsoleText.parse(
					member.currentNodeNameJson(),
					"尚未进入流程页面"
				).getString();
			String field = memberFieldDetail(member);
			String detail = node
				+ (field.isEmpty() ? "" : "  ·  " + field)
				+ "  ·  "
				+ statusLabel(member);
			PlayerIdentityRenderer.drawTargetRow(
				graphics,
				this.font,
				member.player(),
				x + 18,
				rowY,
				Math.max(
					1,
					width - 36 - (action("remove_flow_members") != null && removable(member)
						? (width < 520 ? 60 : 76)
						: 0)
				),
				ROW_HEIGHT,
				false,
				detail,
				color
			);
		}
		drawScrollBar(graphics, x, width, listTop, listBottom, members.size());
	}

	private static String memberFieldDetail(final MemberSummary member) {
		if (member.fields().isEmpty()) {
			return "";
		}
		MemberFieldSummary field = member.fields().getFirst();
		if (field.status().equals("unselected")) {
			return "尚未选择"
				+ ConsoleText.currentObject(
					ConsoleText.parse(field.fieldNameJson(), field.fieldId().toString())
				).getString();
		}
		Component value = ConsoleText.currentObject(
			ConsoleText.parse(field.valueNameJson(), "已选项目")
		);
		return (field.status().equals("held") ? "暂时预约" : "已确认") + value.getString();
	}

	private void drawScrollBar(
		final GuiGraphicsExtractor graphics,
		final int x,
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

	private List<MemberSummary> visibleMembers() {
		ActiveFlowSummary current = flowSnapshot();
		boolean filterUnfinished = current != null
			&& this.unfinishedOnly
			&& current.completed() < current.total()
			&& !current.status().equals("canceled");
		return Optional.ofNullable(current)
			.map(flow -> flow.members()
				.stream()
				.filter(member -> !filterUnfinished || !finished(member.status()))
				.sorted(
					Comparator.comparingInt(ActiveFlowScreen::memberPriority)
				.thenComparing(member -> member.player().name(), String.CASE_INSENSITIVE_ORDER)
				)
				.toList())
			.orElseGet(List::of);
	}

	private ActiveFlowSummary flowSnapshot() {
		return ClientConsoleState.snapshot()
			.flatMap(snapshot -> snapshot.activeFlow().or(snapshot::recentFlow))
			.orElse(null);
	}

	private ActionEntry action(final String operationType) {
		return ClientConsoleState.snapshot()
			.stream()
			.flatMap(snapshot -> snapshot.actions().stream())
			.filter(action -> action.operationType().equals(operationType))
			.findFirst()
			.orElse(null);
	}

	private static boolean removable(final MemberSummary member) {
		return !finished(member.status()) && !member.status().equals("removed");
	}

	private static String statusLabel(final String status) {
		return switch (status) {
			case "active" -> "进行中";
			case "blocked" -> "已阻塞";
			case "completed" -> "已完成";
			case "canceled" -> "已取消";
			default -> "状态未知";
		};
	}

	private static boolean finished(final String status) {
		return status.equals("completed")
			|| status.equals("already_complete")
			|| status.equals("removed");
	}

	private static int memberPriority(final MemberSummary member) {
		if (member.blocked() || member.status().equals("blocked")) {
			return 0;
		}
		if (!member.player().online() || member.status().equals("offline")) {
			return 1;
		}
		return finished(member.status()) ? 3 : 2;
	}

	private static int memberColor(final MemberSummary member) {
		if (member.blocked() || member.status().equals("blocked")) {
			return DANGER;
		}
		if (!member.player().online() || member.status().equals("offline")) {
			return MUTED_TEXT;
		}
		return switch (member.status()) {
			case "completed", "already_complete" -> SUCCESS;
			case "in_progress" -> INFO_CYAN;
			case "removed" -> MUTED_TEXT;
			default -> WARNING;
		};
	}

	private static String statusLabel(final MemberSummary member) {
		if (member.blocked() || member.status().equals("blocked")) {
			return "阻塞";
		}
		if (!member.player().online() || member.status().equals("offline")) {
			return "离线";
		}
		return switch (member.status()) {
			case "completed" -> "已完成";
			case "already_complete" -> "历史已完成";
			case "in_progress" -> "进行中";
			case "removed" -> "已移除";
			default -> "未开始";
		};
	}

	private int panelWidth() {
		return Math.max(1, Math.min(760, this.width - 12));
	}

	private int panelHeight() {
		return Math.max(1, Math.min(430, this.height - 12));
	}

	private int footerHeight() {
		return panelWidth() < 560 ? 75 : 46;
	}

	private boolean compactHeader() {
		return panelHeight() < 300;
	}

	private int flowListTop() {
		return panelY() + (compactHeader() ? 111 : 126);
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

	private record RemoveBinding(UUID playerId, ConsoleButton button) {
	}
}
