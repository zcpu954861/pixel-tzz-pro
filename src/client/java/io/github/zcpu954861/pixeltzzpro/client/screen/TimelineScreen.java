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

import io.github.zcpu954861.pixeltzzpro.client.ClientTimelineState;
import io.github.zcpu954861.pixeltzzpro.client.ClientUiPreferences;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Appearance;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Direction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.CallbackView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.EventView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.PlayerView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.ResultView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.StatisticView;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.TaskView;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TickTimeFormatter;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TimelineViewLayout;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TimelineViewLayout.Layout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Host mission-route telemetry. The same presentation is reused by {@link RecapScreen} after the
 * server has reduced it to a terminal participant recap.
 */
public class TimelineScreen extends TransitioningConsoleScreen {
	private static final int ROUTE_ROW_HEIGHT = 28;
	private static final int ROUTE_ROW_STEP = 34;
	private static final int ROUTE_HEADER_HEIGHT = 34;
	private static final int DETAIL_PADDING = 12;
	private static final int SINGLE_LINE_EVENT_HEIGHT = 25;
	private static final int MULTI_LINE_EVENT_HEIGHT = 34;
	private static final int EVENT_MARKER_SIZE = 5;
	private static final int EVENT_MULTI_LINE_GAP = 4;
	private static final long REFRESH_INTERVAL_MILLIS = 900L;

	private final boolean recapSurface;
	private final List<RouteButton> routeButtons = new ArrayList<>();
	private Identifier selectedTaskId;
	private Identifier lastCurrentTaskId;
	private boolean userSelectedTask;
	private String structureKey = "";
	private long observedRevision = Long.MIN_VALUE;
	private long nextRefreshAtMillis;
	private int routeScroll;
	private int detailScroll;
	private int routeContentHeight;
	private int detailContentHeight;
	private Rect callbackModeToggle;
	private long callbackToggleChangedAtMillis;
	private boolean callbackToggleFromRaw;

	public TimelineScreen(final Screen parent) {
		this(parent, false);
	}

	protected TimelineScreen(final Screen parent, final boolean recapSurface) {
		super(
			Component.literal(recapSurface ? "赛后回顾" : "任务时间线"),
			parent,
			Motion.PUSH_ENTER
		);
		this.recapSurface = recapSurface;
	}

	@Override
	protected void init() {
		ClientTimelineState.request();
		reconcileWidgets(true);
	}

	@Override
	public void tick() {
		super.tick();
		if (navigationLocked()) {
			return;
		}
		long now = Util.getMillis();
		if (now >= this.nextRefreshAtMillis) {
			ClientTimelineState.requestIfStale(REFRESH_INTERVAL_MILLIS);
			this.nextRefreshAtMillis = now + REFRESH_INTERVAL_MILLIS;
		}
		long revision = ClientTimelineState.revision();
		if (revision != this.observedRevision) {
			this.observedRevision = revision;
			reconcileWidgets(false);
		}
		layoutRouteButtons();
	}

	private void reconcileWidgets(final boolean force) {
		Optional<TimelineViewS2CPayload> optional = ClientTimelineState.snapshot();
		String nextStructure = optional
			.filter(value -> value.status().equals("ready"))
			.map(value -> value.tasks()
				.stream()
				.map(task -> task.taskId() + ":" + task.routeState())
				.reduce("", (left, right) -> left + "|" + right))
			.orElse("");
		if (!force && nextStructure.equals(this.structureKey)) {
			clampScroll();
			return;
		}
		this.structureKey = nextStructure;
		this.clearWidgets();
		this.routeButtons.clear();
		Layout layout = layout();
		List<TaskView> tasks = optional
			.filter(value -> value.status().equals("ready"))
			.map(TimelineViewS2CPayload::tasks)
			.orElseGet(List::of);
		selectDefault(optional.orElse(null), tasks);
		for (TaskView task : tasks) {
			ConsoleButton button = new ConsoleButton(
				0,
				0,
				1,
				ROUTE_ROW_HEIGHT,
				ConsoleText.formalName(
					ConsoleText.parse(task.nameJson(), task.taskId().getPath())
				),
				ignored -> selectTask(task.taskId()),
				Variant.NORMAL,
				true,
				(state, fallback) -> routeAppearance(task.taskId(), state, fallback)
			);
			button.setTooltip(
				Tooltip.create(
					Component.literal(
						routeStateLabel(
							task.routeState(),
							optional
								.map(value -> value.timelineStatus().equals("preview"))
								.orElse(false)
						)
					)
				)
			);
			this.routeButtons.add(new RouteButton(task.taskId(), button));
			this.addRenderableWidget(button);
		}
		Rect back = layout.backButton();
		ConsoleButton backButton = new ConsoleButton(
			back.x(),
			back.y(),
			back.width(),
			back.height(),
			Component.translatable("pixel_tzz_pro.screen.back"),
			ignored -> this.onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(backButton);
		this.setInitialFocus(backButton);
		this.routeContentHeight = Math.max(0, tasks.size() * ROUTE_ROW_STEP - 6);
		ensureSelectedVisible();
		clampScroll();
		layoutRouteButtons();
	}

	private void selectDefault(
		final TimelineViewS2CPayload payload,
		final List<TaskView> tasks
	) {
		Identifier currentTaskId = tasks.stream()
			.filter(task -> task.routeState().equals("current"))
			.map(TaskView::taskId)
			.findFirst()
			.orElse(null);
		if (
			currentTaskId != null
				&& (
					this.selectedTaskId == null
						|| !this.userSelectedTask
						|| Objects.equals(this.selectedTaskId, this.lastCurrentTaskId)
				)
		) {
			this.selectedTaskId = currentTaskId;
			this.detailScroll = 0;
		}
		this.lastCurrentTaskId = currentTaskId;
		if (
			this.selectedTaskId != null
				&& tasks.stream().anyMatch(task -> task.taskId().equals(this.selectedTaskId))
		) {
			return;
		}
		this.selectedTaskId = tasks.isEmpty()
			? null
			: payload != null && payload.timelineStatus().equals("preview")
				? tasks.getFirst().taskId()
				: tasks.getLast().taskId();
		this.userSelectedTask = false;
		this.detailScroll = 0;
	}

	private void selectTask(final Identifier taskId) {
		if (Objects.equals(this.selectedTaskId, taskId)) {
			return;
		}
		this.selectedTaskId = taskId;
		this.userSelectedTask = true;
		this.detailScroll = 0;
	}

	private Appearance routeAppearance(
		final Identifier taskId,
		final StyleState state,
		final Appearance fallback
	) {
		TaskView task = task(taskId).orElse(null);
		if (task == null) {
			return fallback;
		}
		boolean selected = taskId.equals(this.selectedTaskId);
		boolean hovered = state == StyleState.HOVER || state == StyleState.FOCUSED;
		int accent = routeColor(task.routeState());
		int background = selected
			? withAlpha(accent, 42)
			: hovered ? 0xB52A3945 : 0x6B1A2530;
		int border = selected
			? withAlpha(accent, 220)
			: hovered ? withAlpha(accent, 150) : 0x50394A57;
		int text = task.routeState().equals("alternate") ? MUTED_TEXT : MAIN_TEXT;
		return new Appearance(
			background,
			border,
			text,
			accent,
			1,
			1.0F,
			true,
			false,
			0
		);
	}

	private void layoutRouteButtons() {
		Layout layout = layout();
		Rect route = layout.route();
		int viewportTop = route.y() + ROUTE_HEADER_HEIGHT;
		int viewportBottom = route.bottom() - 6;
		int x = route.x() + 32;
		int width = Math.max(1, route.width() - 42);
		for (int index = 0; index < this.routeButtons.size(); index++) {
			RouteButton routeButton = this.routeButtons.get(index);
			int y = viewportTop + index * ROUTE_ROW_STEP - this.routeScroll;
			ConsoleButton button = routeButton.button();
			button.setX(x);
			button.setY(y);
			button.setWidth(width);
			button.setHeight(ROUTE_ROW_HEIGHT);
			button.visible = y >= viewportTop && y + ROUTE_ROW_HEIGHT <= viewportBottom;
		}
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
			Layout layout = layout();
			TimelineViewS2CPayload payload = ClientTimelineState.snapshot().orElse(null);
			drawShell(graphics, layout, payload);
			drawRoute(graphics, layout.route(), payload);
			drawDetail(graphics, layout.detail(), payload);
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
		final Layout layout,
		final TimelineViewS2CPayload payload
	) {
		Rect panel = layout.panel();
		graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PANEL);
		graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), PANEL_BORDER);
		graphics.fill(panel.x(), panel.y(), panel.x() + 4, panel.bottom(), BRAND_GOLD);
		graphics.fill(panel.x() + 4, panel.y(), panel.right(), panel.y() + 2, INFO_CYAN);
		int titleX = panel.x() + Math.min(14, Math.max(5, panel.width() / 9));
		int titleY = panel.y() + 10;
		if (panel.height() >= 150) {
			graphics.text(
				this.font,
				Component.literal(
					this.recapSurface
						? "AFTER ACTION REVIEW"
						: payload != null && payload.timelineStatus().equals("preview")
							? "MISSION PREVIEW"
							: "MISSION CONTROL"
				),
				titleX,
				titleY,
				MUTED_TEXT,
				false
			);
		}
		graphics.text(
			this.font,
			Component.literal(
				this.recapSurface
					? "赛后回顾"
					: payload != null && payload.timelineStatus().equals("preview")
						? "任务预览"
						: "任务时间线"
			),
			titleX,
			titleY + (panel.height() >= 150 ? 14 : 2),
			MAIN_TEXT,
			true
		);
		if (payload != null && payload.status().equals("ready") && panel.width() >= 420) {
			Component gameName = ConsoleText.parse(
				payload.gameNameJson(),
				payload.gameId().map(Identifier::toString).orElse("本局")
			);
			int nameWidth = Math.max(1, panel.width() / 3);
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(gameName.getString(), nameWidth),
				titleX + 92,
				titleY + (panel.height() >= 150 ? 14 : 2),
				SECONDARY_TEXT,
				false
			);
		}
		drawServerSyncBadge(
			graphics,
			panel.right() - 14,
			panel.y() + 14,
			ClientTimelineState.pending(),
			ClientTimelineState.synchronizedResponse()
		);
		int footerY = layout.backButton().y() - 5;
		graphics.fill(
			layout.content().x(),
			footerY,
			layout.content().right(),
			footerY + 1,
			0xFF33424E
		);
	}

	private void drawRoute(
		final GuiGraphicsExtractor graphics,
		final Rect route,
		final TimelineViewS2CPayload payload
	) {
		graphics.fill(route.x(), route.y(), route.right(), route.bottom(), SURFACE);
		graphics.outline(route.x(), route.y(), route.width(), route.height(), SURFACE_BORDER);
		graphics.fill(route.x(), route.y(), route.x() + 3, route.bottom(), INFO_CYAN);
		graphics.text(
			this.font,
			Component.literal("任务路线轨"),
			route.x() + 12,
			route.y() + 8,
			MAIN_TEXT,
			true
		);
		if (route.width() >= 150) {
			Component utility = Component.literal("MISSION ROUTE");
			graphics.text(
				this.font,
				utility,
				route.right() - 10 - this.font.width(utility),
				route.y() + 8,
				MUTED_TEXT,
				false
			);
		}
		int viewportTop = route.y() + ROUTE_HEADER_HEIGHT;
		int viewportBottom = route.bottom() - 6;
		if (viewportBottom <= viewportTop) {
			return;
		}
		if (payload == null || !payload.status().equals("ready")) {
			drawEmptyState(
				graphics,
				route.x() + 12,
				viewportTop + 6,
				Math.max(1, route.width() - 24),
				payload
			);
			return;
		}
		graphics.enableScissor(route.x() + 4, viewportTop, route.right() - 4, viewportBottom);
		try {
			int lineX = route.x() + 17;
			graphics.fill(lineX, viewportTop, lineX + 1, viewportBottom, 0xFF34444F);
			for (int index = 0; index < payload.tasks().size(); index++) {
				TaskView task = payload.tasks().get(index);
				int centerY = viewportTop
					+ index * ROUTE_ROW_STEP
					- this.routeScroll
					+ ROUTE_ROW_HEIGHT / 2;
				if (centerY < viewportTop - 10 || centerY > viewportBottom + 10) {
					continue;
				}
				drawRouteNode(graphics, lineX, centerY, task);
			}
		} finally {
			graphics.disableScissor();
		}
		drawScrollbar(
			graphics,
			new Rect(route.right() - 4, viewportTop, 2, viewportBottom - viewportTop),
			this.routeContentHeight,
			this.routeScroll
		);
	}

	private void drawRouteNode(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final TaskView task
	) {
		int color = routeColor(task.routeState());
		switch (task.routeState()) {
			case "completed" -> {
				graphics.fill(x - 5, y - 5, x + 6, y + 6, withAlpha(BRAND_GOLD, 46));
				graphics.outline(x - 4, y - 4, 9, 9, BRAND_GOLD);
				graphics.fill(x - 2, y - 2, x + 3, y + 3, BRAND_GOLD);
				graphics.fill(x - 1, y - 5, x + 2, y - 3, BRAND_GOLD);
				graphics.fill(x - 1, y + 4, x + 2, y + 6, BRAND_GOLD);
			}
			case "current" -> {
				float pulse = 0.5F + 0.5F * (float)Math.sin(Util.getMillis() / 520.0);
				int pulseAlpha = 42 + Math.round(54.0F * pulse);
				graphics.outline(x - 6, y - 6, 13, 13, withAlpha(INFO_CYAN, pulseAlpha));
				graphics.fill(x - 4, y - 4, x + 5, y - 2, INFO_CYAN);
				graphics.fill(x - 4, y + 3, x + 5, y + 5, INFO_CYAN);
				graphics.fill(x - 4, y - 2, x - 2, y + 3, INFO_CYAN);
				graphics.fill(x + 3, y - 2, x + 5, y + 3, INFO_CYAN);
				graphics.fill(x - 1, y - 1, x + 2, y + 2, MAIN_TEXT);
			}
			case "interrupted" -> {
				graphics.outline(x - 4, y - 4, 9, 9, DANGER);
				graphics.fill(x - 3, y - 1, x + 4, y + 2, DANGER);
			}
			default -> {
				graphics.outline(x - 3, y - 3, 7, 7, withAlpha(color, 120));
				graphics.fill(x - 1, y - 1, x + 2, y + 2, withAlpha(color, 100));
			}
		}
	}

	private void drawDetail(
		final GuiGraphicsExtractor graphics,
		final Rect detail,
		final TimelineViewS2CPayload payload
	) {
		graphics.fill(detail.x(), detail.y(), detail.right(), detail.bottom(), SURFACE);
		graphics.outline(detail.x(), detail.y(), detail.width(), detail.height(), SURFACE_BORDER);
		int innerX = detail.x() + DETAIL_PADDING;
		int innerY = detail.y() + DETAIL_PADDING;
		int innerWidth = Math.max(1, detail.width() - DETAIL_PADDING * 2 - 3);
		int innerBottom = detail.bottom() - DETAIL_PADDING;
		this.callbackModeToggle = null;
		if (innerBottom <= innerY) {
			return;
		}
		graphics.enableScissor(detail.x() + 2, detail.y() + 2, detail.right() - 3, detail.bottom() - 2);
		int endY;
		try {
			int y = innerY - this.detailScroll;
			if (payload == null || !payload.status().equals("ready")) {
				drawEmptyState(graphics, innerX, y, innerWidth, payload);
				endY = y + 52;
			} else {
				TaskView selected = selectedTask(payload).orElse(payload.tasks().getLast());
				endY = drawTaskDetail(graphics, payload, selected, innerX, y, innerWidth);
			}
		} finally {
			graphics.disableScissor();
		}
		this.detailContentHeight = Math.max(0, endY - innerY + this.detailScroll);
		clampScroll();
		drawScrollbar(
			graphics,
			new Rect(detail.right() - 4, innerY, 2, Math.max(1, innerBottom - innerY)),
			this.detailContentHeight,
			this.detailScroll
		);
	}

	private int drawTaskDetail(
		final GuiGraphicsExtractor graphics,
		final TimelineViewS2CPayload payload,
		final TaskView task,
		final int x,
		final int startY,
		final int width
	) {
		int y = startY;
		boolean preview = payload.timelineStatus().equals("preview");
		int accent = routeColor(task.routeState());
		graphics.text(
			this.font,
			Component.literal(
				preview
					? "任务预览"
					: task.kind().equals("warmup") ? "赛前环节" : "当前任务"
			),
			x,
			y,
			accent,
			false
		);
		int taskIndex = Math.max(0, payload.tasks().indexOf(task));
		Component utility = Component.literal(
			"TASK %02d / %02d".formatted(taskIndex + 1, payload.tasks().size())
		);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(utility.getString(), Math.max(1, width / 2)),
			x + width - Math.min(width / 2, this.font.width(utility)),
			y,
			MUTED_TEXT,
			false
		);
		y += 14;
		Component name = ConsoleText.currentObject(
			ConsoleText.parse(task.nameJson(), task.taskId().getPath())
		);
		graphics.text(this.font, name, x, y, MAIN_TEXT, true);
		y += 15;

		if (payload.hostView() && !preview) {
			int auditColor = auditColor(payload.auditStatus());
			String auditLabel = auditLabel(payload.auditStatus());
			graphics.fill(x, y, x + width, y + 20, withAlpha(auditColor, 20));
			graphics.outline(x, y, width, 20, withAlpha(auditColor, 112));
			graphics.text(
				this.font,
				Component.literal("技术审计"),
				x + 7,
				y + 6,
				SECONDARY_TEXT,
				false
			);
			graphics.text(
				this.font,
				Component.literal(auditLabel),
				x + width - 7 - this.font.width(auditLabel),
				y + 6,
				auditColor,
				true
			);
			y += 26;
		}

		long displayedTaskTicks = ClientTimelineState.displayedTaskElapsedTicks(task);
		long displayedGameTicks = ClientTimelineState.displayedGameElapsedTicks();
		TaskView activeTask = payload.tasks()
			.stream()
			.filter(candidate -> candidate.routeState().equals("current"))
			.findFirst()
			.orElse(task);
		boolean currentSegmentCounts = activeTask.intermission()
			.filter(ignored -> payload.timelineStatus().equals("intermission"))
			.map(value -> value.countsTowardGameTime())
			.orElse(activeTask.countsTowardGameTime());
		graphics.fill(x, y, x + width, y + 24, SURFACE_RAISED);
		graphics.outline(
			x,
			y,
			width,
			24,
			withAlpha(preview ? SUCCESS : INFO_CYAN, 86)
		);
		String timeLabel = preview ? "任务定义检查正常" : "正式游戏时间";
		graphics.text(
			this.font,
			Component.literal(timeLabel),
			x + 7,
			y + 7,
			preview ? SUCCESS : SECONDARY_TEXT,
			preview
		);
		String gameTime = preview ? "PREVIEW" : formatTicks(displayedGameTicks);
		graphics.text(
			this.font,
			Component.literal(gameTime),
			x + width - 7 - this.font.width(gameTime),
			y + 7,
			preview ? MUTED_TEXT : INFO_CYAN,
			!preview
		);
		if (!preview && !currentSegmentCounts && width >= 230) {
			String excluded = "当前环节不计时";
			graphics.text(
				this.font,
				Component.literal(excluded),
				x + width - 17 - this.font.width(gameTime) - this.font.width(excluded),
				y + 7,
				MUTED_TEXT,
				false
			);
		}
		y += 30;

		int cardGap = 5;
		int cardWidth = Math.max(1, (width - cardGap * 2) / 3);
		drawTelemetryCard(
			graphics,
			x,
			y,
			cardWidth,
			34,
			preview ? "任务时限" : "任务时间",
			preview
				? task.durationTicks().map(TimelineScreen::formatTicks).orElse("不限时")
				: formatTicks(displayedTaskTicks),
			INFO_CYAN
		);
		drawTelemetryCard(
			graphics,
			x + cardWidth + cardGap,
			y,
			cardWidth,
			34,
			preview ? "正式计时" : "参与者",
			preview
				? task.countsTowardGameTime() ? "计入" : "不计入"
				: task.taskInstanceId().isPresent() ? Integer.toString(task.participantCount()) : "待定",
			BRAND_GOLD
		);
		drawTelemetryCard(
			graphics,
			x + (cardWidth + cardGap) * 2,
			y,
			cardWidth,
			34,
			"状态",
			preview ? "等待开局" : taskStatusLabel(task.taskStatus()),
			preview ? SECONDARY_TEXT : taskStatusColor(task.taskStatus())
		);
		y += 41;
		if (!preview) {
			y = drawTaskLifecycle(graphics, task, x, y, width);
		}

		if (!preview && task.durationTicks().isPresent()) {
			long duration = task.durationTicks().orElseThrow();
			float progress = Math.clamp(
				(float)displayedTaskTicks / Math.max(1L, duration),
				0.0F,
				1.0F
			);
			graphics.fill(x, y, x + width, y + 3, 0xFF263540);
			graphics.fill(x, y, x + Math.round(width * progress), y + 3, INFO_CYAN);
			graphics.text(
				this.font,
				Component.literal(formatTicks(displayedTaskTicks) + " / " + formatTicks(duration)),
				x,
				y + 7,
				SECONDARY_TEXT,
				false
			);
			y += 21;
		}

		if (!task.descriptionJson().isEmpty()) {
			Component description = ConsoleText.parse(task.descriptionJson(), "");
			int descriptionHeight = Math.max(9, this.font.wordWrapHeight(description, width));
			graphics.textWithWordWrap(
				this.font,
				description,
				x,
				y,
				width,
				SECONDARY_TEXT,
				false
			);
			y += descriptionHeight + 8;
		}

		if (!task.nextTaskIds().isEmpty()) {
			graphics.fill(x, y, x + width, y + 1, 0xFF33424E);
			y += 7;
			graphics.text(
				this.font,
				Component.literal(preview ? "可能路线" : "候选后继"),
				x,
				y,
				INFO_CYAN,
				false
			);
			Component candidates = Component.literal(
				task.nextTaskIds()
					.stream()
					.map(nextTask -> taskName(payload, nextTask))
					.reduce((left, right) -> left + "  /  " + right)
					.orElse("无")
			);
			int candidateHeight = Math.max(9, this.font.wordWrapHeight(candidates, width));
			graphics.textWithWordWrap(
				this.font,
				candidates,
				x,
				y + 13,
				width,
				SECONDARY_TEXT,
				false
			);
			y += candidateHeight + 19;
		}

		if (preview) {
			graphics.fill(x, y, x + width, y + 1, 0xFF33424E);
			y += 8;
			graphics.text(
				this.font,
				Component.literal("预览说明"),
				x,
				y,
				MAIN_TEXT,
				true
			);
			y += 15;
			Component note = Component.literal(
				"此处展示数据包已注册的可能任务路线；实际结算、任务间隔与后继任务由本局运行结果决定。"
			);
			int noteHeight = Math.max(9, this.font.wordWrapHeight(note, width));
			graphics.textWithWordWrap(
				this.font,
				note,
				x,
				y,
				width,
				SECONDARY_TEXT,
				false
			);
			return y + noteHeight + 8;
		}

		if (!task.participants().isEmpty()) {
			y = drawParticipants(graphics, task, x, y, width) + 8;
		}

		if (task.result().isPresent()) {
			y = drawResult(graphics, task.result().orElseThrow(), x, y, width) + 8;
		}
		if (task.intermission().isPresent()) {
			var intermission = task.intermission().orElseThrow();
			long displayedIntermissionTicks =
				ClientTimelineState.displayedIntermissionElapsedTicks(task);
			String intermissionTime = formatTicks(displayedIntermissionTicks)
				+ " / "
				+ formatTicks(intermission.durationTicks());
			graphics.fill(x, y, x + width, y + 29, 0xA51A2B35);
			graphics.outline(x, y, width, 29, withAlpha(INFO_CYAN, 105));
			int intermissionTextY = y + Math.max(0, (29 - this.font.lineHeight) / 2);
			graphics.text(
				this.font,
				Component.literal("任务间隔"),
				x + 8,
				intermissionTextY,
				INFO_CYAN,
				true
			);
			graphics.text(
				this.font,
				Component.literal(intermissionTime),
				x + width - 8 - this.font.width(intermissionTime),
				intermissionTextY,
				SECONDARY_TEXT,
				false
			);
			y += 37;
		}

		graphics.fill(x, y, x + width, y + 1, 0xFF33424E);
		y += 8;
		graphics.text(
			this.font,
			Component.literal(this.recapSurface ? "回顾事实" : "任务遥测"),
			x,
			y,
			MAIN_TEXT,
			true
		);
		Component factsUtility = Component.literal("RECORDED FACTS");
		graphics.text(
			this.font,
			factsUtility,
			x + width - this.font.width(factsUtility),
			y,
			MUTED_TEXT,
			false
		);
		y += 16;

		if (task.events().isEmpty() && task.statistics().isEmpty()) {
			graphics.text(
				this.font,
				Component.literal(
					task.taskInstanceId().isEmpty()
						? "该任务尚未开始，暂无遥测记录。"
						: "本任务没有可显示的事件或统计。"
				),
				x,
				y,
				SECONDARY_TEXT,
				false
			);
			y += 24;
		} else {
			for (EventView event : task.events()) {
				y = drawEvent(graphics, event, x, y, width) + 5;
			}
			for (StatisticView statistic : task.statistics()) {
				y = drawStatistic(graphics, statistic, x, y, width) + 5;
			}
		}
		int omitted = task.omittedEventCount() + task.omittedStatisticCount();
		if (omitted > 0) {
			graphics.text(
				this.font,
				Component.literal("另有 " + omitted + " 条记录因视图上限未展开"),
				x,
				y,
				WARNING,
				false
			);
			y += 16;
		}
		if (!task.callbacks().isEmpty() || !payload.timelineCallbacks().isEmpty()) {
			graphics.fill(x, y, x + width, y + 1, 0xFF33424E);
			y += 8;
			graphics.text(
				this.font,
				Component.literal("回调审计"),
				x,
				y,
				MAIN_TEXT,
				true
			);
			y += drawCallbackModeToggle(graphics, x, y, width);
			List<CallbackView> callbacks = new ArrayList<>(task.callbacks());
			if (task.routeState().equals("current") || task.callbacks().isEmpty()) {
				callbacks.addAll(payload.timelineCallbacks());
			}
			if (ClientUiPreferences.callbackAuditRaw()) {
				for (CallbackView callback : callbacks) {
					y = drawCallback(graphics, callback, x, y, width) + 4;
				}
			} else {
				for (CallbackGroup group : callbackGroups(callbacks)) {
					y = drawCallbackGroup(graphics, group, x, y, width) + 4;
				}
			}
			if (task.omittedCallbackCount() > 0) {
				graphics.text(
					this.font,
					Component.literal("另有 " + task.omittedCallbackCount() + " 条回调记录未展开"),
					x,
					y,
					WARNING,
					false
				);
				y += 15;
			}
			if (
				payload.auditStatus().equals("blocked")
					|| payload.auditStatus().equals("intervention_required")
			) {
				graphics.text(
					this.font,
					Component.literal("请返回主持人控制台执行恢复或确认操作。"),
					x,
					y,
					DANGER,
					true
				);
				y += 17;
			}
		}
		return y + 4;
	}

	private int drawParticipants(
		final GuiGraphicsExtractor graphics,
		final TaskView task,
		final int x,
		final int y,
		final int width
	) {
		graphics.text(
			this.font,
			Component.literal("冻结参与者"),
			x,
			y,
			MAIN_TEXT,
			true
		);
		String count = task.participantCount() + " 人";
		graphics.text(
			this.font,
			count,
			x + width - this.font.width(count),
			y,
			BRAND_GOLD,
			false
		);
		int columns = width >= 400 ? 4 : width >= 300 ? 3 : width >= 190 ? 2 : 1;
		int gap = 5;
		int cellWidth = Math.max(1, (width - gap * (columns - 1)) / columns);
		int rowHeight = 26;
		int top = y + 15;
		List<PlayerView> participants = orderedPlayers(task.participants());
		for (int index = 0; index < participants.size(); index++) {
			PlayerView player = participants.get(index);
			int column = index % columns;
			int row = index / columns;
			int cellX = x + column * (cellWidth + gap);
			int cellY = top + row * rowHeight;
			graphics.fill(cellX, cellY, cellX + cellWidth, cellY + 22, 0xA51B2732);
			graphics.outline(cellX, cellY, cellWidth, 22, 0x70374855);
			PlayerIdentityRenderer.drawHead(
				graphics,
				player.playerId(),
				player.online(),
				cellX + 4,
				cellY + 3,
				16
			);
			graphics.text(
				this.font,
				PlayerIdentityRenderer.ellipsize(
					this.font,
					player.name(),
					Math.max(1, cellWidth - 29)
				),
				cellX + 25,
				cellY + 7,
				player.online() ? MAIN_TEXT : MUTED_TEXT,
				false
			);
		}
		int rows = (participants.size() + columns - 1) / columns;
		int bottom = top + rows * rowHeight;
		if (task.omittedParticipantCount() > 0) {
			graphics.text(
				this.font,
				Component.literal("另有 " + task.omittedParticipantCount() + " 人未展开"),
				x,
				bottom,
				MUTED_TEXT,
				false
			);
			bottom += 12;
		}
		return bottom;
	}

	private int drawCallback(
		final GuiGraphicsExtractor graphics,
		final CallbackView callback,
		final int x,
		final int y,
		final int width
	) {
		int color = callbackColor(callback.status());
		int errorHeight = callback.lastError().isEmpty()
			? 0
			: Math.max(
				9,
				this.font.wordWrapHeight(
					Component.literal(callback.lastError()),
					Math.max(1, width - 18)
				)
			);
		int height = 31 + errorHeight;
		graphics.fill(x, y, x + width, y + height, 0xA51B2732);
		graphics.outline(x, y, width, height, withAlpha(color, 120));
		graphics.fill(x, y, x + 3, y + height, color);
		int textX = x + 9;
		if (callback.player().isPresent()) {
			PlayerView player = callback.player().orElseThrow();
			PlayerIdentityRenderer.drawHead(
				graphics,
				player.playerId(),
				player.online(),
				x + 7,
				y + 6,
				17
			);
			textX = x + 30;
		}
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(callback.stepKey(), Math.max(1, width / 2)),
			textX,
			y + 5,
			MAIN_TEXT,
			true
		);
		String status = callbackLabel(callback.status()) + " · " + callback.attemptCount() + " 次";
		graphics.text(
			this.font,
			status,
			x + width - 8 - this.font.width(status),
			y + 5,
			color,
			false
		);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(callback.functionId().toString(), Math.max(1, width - 18)),
			textX,
			y + 17,
			MUTED_TEXT,
			false
		);
		if (!callback.lastError().isEmpty()) {
			graphics.textWithWordWrap(
				this.font,
				Component.literal(callback.lastError()),
				x + 9,
				y + 29,
				Math.max(1, width - 18),
				DANGER,
				false
			);
		}
		return y + height;
	}

	private int drawCallbackModeToggle(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width
	) {
		boolean stacked = width < 180;
		int toggleWidth = stacked ? width : Math.min(104, Math.max(82, width / 3));
		int toggleX = x + width - toggleWidth;
		int toggleY = stacked ? y + 13 : y - 4;
		int toggleHeight = 17;
		this.callbackModeToggle = new Rect(toggleX, toggleY, toggleWidth, toggleHeight);
		boolean raw = ClientUiPreferences.callbackAuditRaw();
		graphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, 0xC51A2731);
		graphics.outline(
			toggleX,
			toggleY,
			toggleWidth,
			toggleHeight,
			withAlpha(raw ? INFO_CYAN : BRAND_GOLD, 145)
		);
		int half = toggleWidth / 2;
		long elapsed = Math.max(0L, Util.getMillis() - this.callbackToggleChangedAtMillis);
		float progress = this.callbackToggleChangedAtMillis == 0L
			? 1.0F
			: Math.clamp(elapsed / 150.0F, 0.0F, 1.0F);
		float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
		boolean fromRaw = this.callbackToggleFromRaw;
		float from = fromRaw ? 1.0F : 0.0F;
		float to = raw ? 1.0F : 0.0F;
		int highlightX = toggleX + Math.round((half - 1) * (from + (to - from) * eased));
		graphics.fill(
			highlightX + 1,
			toggleY + 1,
			highlightX + half,
			toggleY + toggleHeight - 1,
			withAlpha(raw ? INFO_CYAN : BRAND_GOLD, 38)
		);
		graphics.text(
			this.font,
			Component.literal("简洁"),
			toggleX + Math.max(4, (half - this.font.width("简洁")) / 2),
			toggleY + 5,
			raw ? MUTED_TEXT : BRAND_GOLD,
			!raw
		);
		graphics.text(
			this.font,
			Component.literal("原始"),
			toggleX + half + Math.max(4, (toggleWidth - half - this.font.width("原始")) / 2),
			toggleY + 5,
			raw ? INFO_CYAN : MUTED_TEXT,
			raw
		);
		return stacked ? 35 : 15;
	}

	private List<CallbackGroup> callbackGroups(final List<CallbackView> callbacks) {
		Map<String, List<CallbackView>> grouped = new LinkedHashMap<>();
		for (CallbackView callback : callbacks) {
			String key = callback.stepKey() + "\u0000" + callback.functionId();
			grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(callback);
		}
		return grouped.values()
			.stream()
			.map(values -> new CallbackGroup(List.copyOf(values)))
			.toList();
	}

	private int drawCallbackGroup(
		final GuiGraphicsExtractor graphics,
		final CallbackGroup group,
		final int x,
		final int y,
		final int width
	) {
		List<CallbackView> callbacks = group.callbacks();
		int failures = (int)callbacks.stream()
			.filter(callback ->
				callback.status().equals("failed")
					|| callback.status().equals("outcome_unknown")
			)
			.count();
		int prepared = (int)callbacks.stream()
			.filter(callback -> callback.status().equals("prepared"))
			.count();
		int succeeded = callbacks.size() - failures - prepared;
		int color = failures > 0 ? DANGER : prepared > 0 ? INFO_CYAN : SUCCESS;
		boolean outcomeUnknown = callbacks.stream()
			.anyMatch(callback -> callback.status().equals("outcome_unknown"));
		String advice = outcomeUnknown
			? "服务器无法确认执行结果，请主持人返回控制台审阅并恢复。"
			: failures > 0
				? "数据包处理未完成，请主持人返回控制台按提示恢复。"
				: prepared > 0
					? "服务器仍在执行，请稍候。"
					: "";
		int adviceHeight = advice.isEmpty()
			? 0
			: Math.max(
				9,
				this.font.wordWrapHeight(
					Component.literal(advice),
					Math.max(1, width - 18)
				)
			);
		int height = advice.isEmpty() ? 34 : 39 + adviceHeight;
		graphics.fill(x, y, x + width, y + height, 0xA51B2732);
		graphics.outline(x, y, width, height, withAlpha(color, 120));
		graphics.fill(x, y, x + 3, y + height, color);

		List<PlayerView> players = orderedPlayers(
			callbacks.stream()
				.map(CallbackView::player)
				.flatMap(Optional::stream)
				.toList()
		);
		int textX = x + 9;
		int stack = Math.min(2, players.size());
		for (int index = 0; index < stack; index++) {
			PlayerView player = players.get(index);
			PlayerIdentityRenderer.drawHead(
				graphics,
				player.playerId(),
				player.online(),
				x + 7 + index * 10,
				y + 7,
				17,
				false
			);
		}
		if (stack > 0) {
			textX = x + 18 + stack * 10;
			if (players.size() > stack) {
				graphics.text(this.font, "...", textX - 7, y + 18, MUTED_TEXT, false);
			}
		}
		String title = callbackSemanticName(callbacks.getFirst().stepKey());
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(title, Math.max(1, width / 2)),
			textX,
			y + 5,
			MAIN_TEXT,
			true
		);
		String status = failures > 0
			? failures + " 失败 · " + succeeded + "/" + callbacks.size() + " 成功"
			: prepared > 0
				? prepared + " 执行中 · " + succeeded + "/" + callbacks.size() + " 成功"
				: succeeded + "/" + callbacks.size() + " 成功";
		graphics.text(
			this.font,
			status,
			x + width - 8 - this.font.width(status),
			y + 5,
			color,
			false
		);
		int maximumAttempt = callbacks.stream().mapToInt(CallbackView::attemptCount).max().orElse(1);
		String detail = players.isEmpty()
			? "全局处理"
			: players.getFirst().name() + (players.size() > 1 ? " 等 " + players.size() + " 人" : "");
		if (maximumAttempt > 1) {
			detail += " · 已尝试 " + maximumAttempt + " 次";
		}
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(detail, Math.max(1, width - (textX - x) - 9)),
			textX,
			y + 19,
			MUTED_TEXT,
			false
		);
		if (!advice.isEmpty()) {
			graphics.textWithWordWrap(
				this.font,
				Component.literal(advice),
				x + 9,
				y + 32,
				Math.max(1, width - 18),
				DANGER,
				false
			);
		}
		return y + height;
	}

	private static String callbackSemanticName(final String stepKey) {
		if (stepKey.equals("task/on_start")) {
			return "任务启动";
		}
		if (stepKey.equals("task/on_start_players")) {
			return "参与者任务初始化";
		}
		if (stepKey.equals("task/on_timeout")) {
			return "任务超时处理";
		}
		if (stepKey.equals("task/on_settled")) {
			return "任务结算完成";
		}
		if (stepKey.startsWith("result/") && stepKey.endsWith("/on_apply")) {
			return "任务结果应用";
		}
		if (stepKey.startsWith("intermission/") && stepKey.endsWith("/on_start")) {
			return "任务间隔开始";
		}
		if (stepKey.startsWith("intermission/") && stepKey.endsWith("/on_complete")) {
			return "任务间隔完成";
		}
		return "数据包处理";
	}

	private int drawResult(
		final GuiGraphicsExtractor graphics,
		final ResultView result,
		final int x,
		final int y,
		final int width
	) {
		int color = resultColor(result.semantic());
		int recapHeight = result.recapJson().isEmpty()
			? 0
			: Math.max(
				9,
				this.font.wordWrapHeight(
					ConsoleText.parse(result.recapJson(), ""),
					Math.max(1, width - 18)
				)
			);
		int height = result.recapJson().isEmpty() ? 36 : 41 + recapHeight;
		graphics.fill(x, y, x + width, y + height, withAlpha(color, 24));
		graphics.outline(x, y, width, height, withAlpha(color, 170));
		graphics.fill(x, y, x + 4, y + height, color);
		graphics.text(
			this.font,
			Component.literal("结算"),
			x + 10,
			y + 7,
			SECONDARY_TEXT,
			false
		);
		Component name = ConsoleText.formalName(
			ConsoleText.parse(result.nameJson(), result.resultId())
		);
		int nameWidth = Math.max(1, width - 74);
		String visibleName = this.font.plainSubstrByWidth(name.getString(), nameWidth);
		graphics.text(
			this.font,
			Component.literal(visibleName),
			x + width - 10 - this.font.width(visibleName),
			y + 7,
			color,
			true
		);
		graphics.text(
			this.font,
			Component.literal("结果冻结于游戏第 " + formatTicks(result.frozenAtGameTick())),
			x + 10,
			y + 21,
			MUTED_TEXT,
			false
		);
		if (!result.recapJson().isEmpty()) {
			graphics.textWithWordWrap(
				this.font,
				ConsoleText.parse(result.recapJson(), ""),
				x + 10,
				y + 35,
				Math.max(1, width - 18),
				MAIN_TEXT,
				false
			);
		}
		return y + height;
	}

	private int drawTaskLifecycle(
		final GuiGraphicsExtractor graphics,
		final TaskView task,
		final int x,
		final int y,
		final int width
	) {
		int cardGap = 5;
		int cardWidth = Math.max(1, (width - cardGap * 2) / 3);
		String started = task.startedAtGameTick()
			.map(value -> "第 " + formatTicks(value))
			.orElse("尚未开始");
		String settled = task.settledAtGameTick()
			.map(value -> "第 " + formatTicks(value))
			.orElseGet(() -> task.startedAtGameTick().isPresent() ? "进行中" : "尚未开始");
		String duration = task.startedAtGameTick()
			.map(start -> {
				long end = task.settledAtGameTick()
					.orElseGet(ClientTimelineState::displayedGameElapsedTicks);
				return formatTicks(Math.max(0L, end - start));
			})
			.orElse("--:--");
		drawTelemetryCard(
			graphics,
			x,
			y,
			cardWidth,
			34,
			"开始 · 游戏时钟",
			started,
			INFO_CYAN
		);
		drawTelemetryCard(
			graphics,
			x + cardWidth + cardGap,
			y,
			cardWidth,
			34,
			"结束 · 游戏时钟",
			settled,
			task.settledAtGameTick().isPresent() ? SUCCESS : SECONDARY_TEXT
		);
		drawTelemetryCard(
			graphics,
			x + (cardWidth + cardGap) * 2,
			y,
			cardWidth,
			34,
			"持续时间",
			duration,
			BRAND_GOLD
		);
		return y + 41;
	}

	private int drawEvent(
		final GuiGraphicsExtractor graphics,
		final EventView event,
		final int x,
		final int y,
		final int width
	) {
		boolean hasPlayer = event.player().isPresent();
		int height = hasPlayer ? MULTI_LINE_EVENT_HEIGHT : SINGLE_LINE_EVENT_HEIGHT;
		graphics.fill(x, y, x + width, y + height, 0xA51B2732);
		graphics.outline(x, y, width, height, 0x80374855);
		int textX = x + 20;
		int titleY = y + (height - this.font.lineHeight) / 2;
		if (hasPlayer) {
			var player = event.player().orElseThrow();
			PlayerIdentityRenderer.drawHead(
				graphics,
				player.playerId(),
				player.online(),
				x + 8,
				y + 8,
				18
			);
			textX = x + 33;
			int textBlockHeight = this.font.lineHeight * 2 + EVENT_MULTI_LINE_GAP;
			titleY = y + (height - textBlockHeight) / 2;
			graphics.text(
				this.font,
				Component.literal(player.name()),
				textX,
				titleY + this.font.lineHeight + EVENT_MULTI_LINE_GAP,
				player.online() ? SECONDARY_TEXT : MUTED_TEXT,
				false
			);
		} else {
			int markerY = y + (height - EVENT_MARKER_SIZE) / 2;
			graphics.fill(
				x + 8,
				markerY,
				x + 8 + EVENT_MARKER_SIZE,
				markerY + EVENT_MARKER_SIZE,
				INFO_CYAN
			);
		}
		String time = "第 " + formatTicks(event.gameElapsedTicks());
		int titleWidth = Math.max(
			1,
			width - (textX - x) - this.font.width(time) - 22
		);
		Component name = ConsoleText.parse(event.nameJson(), event.eventId());
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(name.getString(), titleWidth),
			textX,
			titleY,
			MAIN_TEXT,
			true
		);
		graphics.text(
			this.font,
			time,
			x + width - 8 - this.font.width(time),
			titleY,
			INFO_CYAN,
			false
		);
		return y + height;
	}

	private int drawStatistic(
		final GuiGraphicsExtractor graphics,
		final StatisticView statistic,
		final int x,
		final int y,
		final int width
	) {
		int height = 29;
		graphics.fill(x, y, x + width, y + height, SURFACE_RAISED);
		graphics.outline(x, y, width, height, withAlpha(BRAND_GOLD, 92));
		int textX = x + 9;
		if (statistic.player().isPresent()) {
			var player = statistic.player().orElseThrow();
			PlayerIdentityRenderer.drawHead(
				graphics,
				player.playerId(),
				player.online(),
				x + 7,
				y + 6,
				17
			);
			textX = x + 31;
		}
		String value = statisticValue(statistic);
		String visibleValue = this.font.plainSubstrByWidth(value, Math.max(1, width / 2));
		int valueWidth = this.font.width(visibleValue);
		int textY = y + Math.max(0, (height - this.font.lineHeight) / 2);
		Component name = ConsoleText.parse(statistic.nameJson(), statistic.statisticId());
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(
				name.getString(),
				Math.max(1, width - (textX - x) - valueWidth - 20)
			),
			textX,
			textY,
			SECONDARY_TEXT,
			false
		);
		graphics.text(
			this.font,
			visibleValue,
			x + width - 9 - valueWidth,
			textY,
			BRAND_GOLD,
			true
		);
		return y + height;
	}

	private void drawTelemetryCard(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final String label,
		final String value,
		final int color
	) {
		graphics.fill(x, y, x + width, y + height, SURFACE_RAISED);
		graphics.outline(x, y, width, height, SURFACE_BORDER);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(label, Math.max(1, width - 10)),
			x + 6,
			y + 5,
			MUTED_TEXT,
			false
		);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(value, Math.max(1, width - 10)),
			x + 6,
			y + 17,
			color,
			true
		);
	}

	private void drawEmptyState(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final TimelineViewS2CPayload payload
	) {
		String primary = ClientTimelineState.pending()
			? "正在同步任务时间线…"
			: payload == null
				? "尚未取得任务时间线"
				: payload.status().equals("error")
					? "任务时间线暂不可用"
					: "本局尚无可查看的回顾";
		int color = payload != null && payload.status().equals("error") ? DANGER : INFO_CYAN;
		graphics.text(this.font, Component.literal(primary), x, y, color, true);
		if (payload != null && !payload.message().isEmpty()) {
			graphics.textWithWordWrap(
				this.font,
				Component.literal(payload.message()),
				x,
				y + 15,
				width,
				SECONDARY_TEXT,
				false
			);
		}
	}

	private void drawScrollbar(
		final GuiGraphicsExtractor graphics,
		final Rect track,
		final int contentHeight,
		final int scroll
	) {
		int maximum = Math.max(0, contentHeight - track.height());
		if (maximum <= 0 || track.height() <= 0) {
			return;
		}
		graphics.fill(track.x(), track.y(), track.right(), track.bottom(), 0xFF24313B);
		Rect thumb = UiLayoutEngine.scrollbarThumb(
			track,
			Direction.VERTICAL,
			contentHeight,
			scroll,
			maximum
		);
		graphics.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), INFO_CYAN);
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		if (navigationLocked()) {
			return true;
		}
		Layout layout = layout();
		double referenceX = referenceMouseX(mouseX);
		double referenceY = referenceMouseY(mouseY);
		int delta = (int)Math.round(-verticalAmount * 22.0);
		if (contains(layout.route(), referenceX, referenceY)) {
			this.routeScroll += delta;
			clampScroll();
			layoutRouteButtons();
			return true;
		}
		if (contains(layout.detail(), referenceX, referenceY)) {
			this.detailScroll += delta;
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		double referenceX = referenceMouseX(event.x());
		double referenceY = referenceMouseY(event.y());
		if (
			!navigationLocked()
				&& this.callbackModeToggle != null
				&& contains(layout().detail(), referenceX, referenceY)
				&& contains(this.callbackModeToggle, referenceX, referenceY)
		) {
			boolean previous = ClientUiPreferences.callbackAuditRaw();
			this.callbackToggleFromRaw = previous;
			ClientUiPreferences.setCallbackAuditRaw(!previous);
			this.callbackToggleChangedAtMillis = Util.getMillis();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void ensureSelectedVisible() {
		if (this.selectedTaskId == null) {
			return;
		}
		int index = -1;
		for (int cursor = 0; cursor < this.routeButtons.size(); cursor++) {
			if (this.routeButtons.get(cursor).taskId().equals(this.selectedTaskId)) {
				index = cursor;
				break;
			}
		}
		if (index < 0) {
			return;
		}
		Rect route = layout().route();
		int viewport = Math.max(1, route.height() - ROUTE_HEADER_HEIGHT - 6);
		int top = index * ROUTE_ROW_STEP;
		int bottom = top + ROUTE_ROW_HEIGHT;
		if (top < this.routeScroll) {
			this.routeScroll = top;
		} else if (bottom > this.routeScroll + viewport) {
			this.routeScroll = bottom - viewport;
		}
	}

	private void clampScroll() {
		Layout layout = layout();
		int routeViewport = Math.max(1, layout.route().height() - ROUTE_HEADER_HEIGHT - 6);
		int detailViewport = Math.max(1, layout.detail().height() - DETAIL_PADDING * 2);
		this.routeScroll = Math.clamp(
			this.routeScroll,
			0,
			Math.max(0, this.routeContentHeight - routeViewport)
		);
		this.detailScroll = Math.clamp(
			this.detailScroll,
			0,
			Math.max(0, this.detailContentHeight - detailViewport)
		);
	}

	private Optional<TaskView> task(final Identifier taskId) {
		return ClientTimelineState.snapshot()
			.stream()
			.flatMap(payload -> payload.tasks().stream())
			.filter(task -> task.taskId().equals(taskId))
			.findFirst();
	}

	private Optional<TaskView> selectedTask(final TimelineViewS2CPayload payload) {
		if (this.selectedTaskId == null) {
			return Optional.empty();
		}
		return payload.tasks()
			.stream()
			.filter(task -> task.taskId().equals(this.selectedTaskId))
			.findFirst();
	}

	private Layout layout() {
		return TimelineViewLayout.compute(designWidth(), designHeight());
	}

	private static boolean contains(
		final Rect rect,
		final double x,
		final double y
	) {
		return x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom();
	}

	private static int routeColor(final String routeState) {
		return switch (routeState) {
			case "completed" -> BRAND_GOLD;
			case "current" -> INFO_CYAN;
			case "interrupted" -> DANGER;
			case "future" -> SECONDARY_TEXT;
			default -> MUTED_TEXT;
		};
	}

	private static String routeStateLabel(
		final String routeState,
		final boolean preview
	) {
		if (preview) {
			return "可能路线";
		}
		return switch (routeState) {
			case "completed" -> "已完成";
			case "current" -> "当前任务";
			case "interrupted" -> "已中断";
			case "future" -> "后续任务";
			default -> "其他分支";
		};
	}

	private static String taskStatusLabel(final String status) {
		return switch (status) {
			case "starting" -> "正在启动";
			case "running" -> "进行中";
			case "settling" -> "结算中";
			case "settled" -> "已完成";
			case "interrupted" -> "已中断";
			case "blocked" -> "技术阻塞";
			default -> "尚未开始";
		};
	}

	private static int taskStatusColor(final String status) {
		return switch (status) {
			case "running" -> INFO_CYAN;
			case "settled" -> SUCCESS;
			case "interrupted", "blocked" -> DANGER;
			case "settling" -> BRAND_GOLD;
			default -> SECONDARY_TEXT;
		};
	}

	private static int resultColor(final String semantic) {
		return switch (semantic) {
			case "success" -> SUCCESS;
			case "failure" -> DANGER;
			case "neutral" -> BRAND_GOLD;
			default -> INFO_CYAN;
		};
	}

	private static int auditColor(final String status) {
		return switch (status) {
			case "healthy" -> SUCCESS;
			case "pending" -> INFO_CYAN;
			case "blocked", "intervention_required" -> DANGER;
			default -> MUTED_TEXT;
		};
	}

	private static String auditLabel(final String status) {
		return switch (status) {
			case "healthy" -> "正常";
			case "pending" -> "正在执行";
			case "blocked" -> "时间线阻塞";
			case "intervention_required" -> "需要人工处理";
			default -> "无审计信息";
		};
	}

	private static List<PlayerView> orderedPlayers(final List<PlayerView> players) {
		return players.stream()
			.sorted(
				Comparator.<PlayerView>comparingInt(player -> player.online() ? 0 : 1)
					.thenComparing(PlayerView::name, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(PlayerView::playerId)
			)
			.toList();
	}

	private static int callbackColor(final String status) {
		return switch (status) {
			case "succeeded" -> SUCCESS;
			case "prepared" -> INFO_CYAN;
			case "failed", "outcome_unknown" -> DANGER;
			default -> MUTED_TEXT;
		};
	}

	private static String callbackLabel(final String status) {
		return switch (status) {
			case "succeeded" -> "已成功";
			case "prepared" -> "执行中";
			case "failed" -> "失败";
			case "outcome_unknown" -> "结果未知";
			default -> status;
		};
	}

	private static String taskName(
		final TimelineViewS2CPayload payload,
		final Identifier taskId
	) {
		return payload.tasks()
			.stream()
			.filter(task -> task.taskId().equals(taskId))
			.findFirst()
			.map(task -> ConsoleText.formalName(
				ConsoleText.parse(task.nameJson(), taskId.getPath())
			).getString())
			.orElse(taskId.getPath());
	}

	private static String statisticValue(final StatisticView statistic) {
		if (statistic.player().isPresent()) {
			return statistic.player().orElseThrow().name();
		}
		return switch (statistic.type()) {
			case "boolean" -> statistic.value().equals("true") ? "是" : "否";
			case "duration_ticks" -> {
				try {
					yield formatTicks(Long.parseLong(statistic.value()));
				} catch (NumberFormatException error) {
					yield statistic.value();
				}
			}
			default -> statistic.value();
		};
	}

	private static String formatTicks(final long ticks) {
		return TickTimeFormatter.format(ticks);
	}

	@Override
	public void onClose() {
		closeToParent();
	}

	private record RouteButton(Identifier taskId, ConsoleButton button) {
	}

	private record CallbackGroup(List<CallbackView> callbacks) {
		private CallbackGroup {
			callbacks = List.copyOf(callbacks);
		}
	}
}
