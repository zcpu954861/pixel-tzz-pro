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
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Reusable player/host control surface. The server snapshot remains authoritative;
 * this screen only changes presentation and exposes actions that the server enables.
 */
public final class ControlConsoleScreen extends TransitioningConsoleScreen {
	private ConsoleButton actionButton;
	private long observedConsoleRevision = Long.MIN_VALUE;

	public ControlConsoleScreen(final Screen parent) {
		super(Component.translatable("pixel_tzz_pro.menu.open"), parent, Motion.ROOT_ENTER);
	}

	@Override
	protected void init() {
		Snapshot session = ClientSessionState.snapshot();
		if (
			(session.adminEligible() || session.currentPlayerHost())
				&& !ClientConsoleState.pending()
		) {
			ClientConsoleState.requestConsole();
		}
		rebuildConsoleWidgets();
	}

	private void rebuildConsoleWidgets() {
		this.clearWidgets();
		Snapshot session = ClientSessionState.snapshot();
		Optional<ConsoleSnapshotS2CPayload> console =
			session.adminEligible() || session.currentPlayerHost()
				? ClientConsoleState.snapshot()
				: Optional.empty();
		int panelX = panelX();
		int panelY = panelY();
		int panelWidth = panelWidth();
		int innerWidth = panelWidth - 32;
		boolean compact = panelWidth < 560 || panelHeight() < 296;
		int actionX = compact ? panelX + 16 : panelX + 154;
		int actionAreaWidth = compact ? innerWidth : panelWidth - 178;
		int actionY = actionButtonY();
		List<ActionEntry> quickActions = console
			.map(ControlConsoleScreen::contextQuickActions)
			.orElseGet(List::of);
		boolean showRecent = !compact
			&& console.flatMap(ConsoleSnapshotS2CPayload::activeFlow).isEmpty()
			&& console.flatMap(ConsoleSnapshotS2CPayload::recentFlow).isPresent();
		int quickCount = compact
			? 1
			: Math.min(3, 1 + quickActions.size() + (showRecent ? 1 : 0));
		int quickGap = 5;
		int actionWidth = Math.max(
			1,
			(actionAreaWidth - quickGap * (quickCount - 1)) / quickCount
		);

		this.actionButton = new ConsoleButton(
			actionX,
			actionY,
			actionWidth,
			24,
			primaryActionLabel(console),
			button -> openPrimaryAction(),
			Variant.PRIMARY
		);
		updatePrimaryAction(console);
		this.addRenderableWidget(this.actionButton);
		int slot = 1;
		if (showRecent && slot < quickCount) {
			ConsoleButton recent = new ConsoleButton(
				actionX + slot * (actionWidth + quickGap),
				actionY,
				actionWidth,
				24,
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
			ConsoleButton quick = new ConsoleButton(
				actionX + slot * (actionWidth + quickGap),
				actionY,
				actionWidth,
				24,
				actionRouting(quickAction.operationId())
					? Component.literal("正在审阅…")
					: ConsoleText.parse(quickAction.labelJson(), quickAction.operationId().toString()),
				button -> dispatchAction(quickAction),
				quickAction.requiresConfirmation() ? Variant.DANGER : Variant.NORMAL
			);
			quick.active = quickAction.enabled() && !ClientConsoleState.pending();
			quick.setTooltip(
				Tooltip.create(
					ConsoleText.parse(
						quickAction.descriptionJson(),
						quickAction.operationId().toString()
					)
				)
			);
			this.addRenderableWidget(quick);
		}

		int footerTop = panelY + panelHeight() - footerHeight();
		boolean stackedFooter = panelWidth < 420;
		int footerGap = 6;
		int footerButtonWidth = stackedFooter
			? Math.max(1, (innerWidth - footerGap) / 2)
			: Math.max(1, (innerWidth - footerGap * 3) / 4);
		int firstFooterY = footerTop + 7;
		int secondFooterY = stackedFooter ? firstFooterY + 29 : firstFooterY;
		ConsoleButton catalog = new ConsoleButton(
			panelX + 16,
			firstFooterY,
			footerButtonWidth,
			24,
			Component.literal("全部操作"),
			button -> pushScreen(new ActionCatalogScreen(this)),
			Variant.PRIMARY
		);
		catalog.active = console.map(ConsoleSnapshotS2CPayload::adminEligible).orElse(false);
		catalog.setTooltip(Tooltip.create(Component.literal("查看服务端在当前阶段公开的完整操作目录")));
		this.addRenderableWidget(catalog);

		int rosterX = panelX + 16 + footerButtonWidth + footerGap;
		ConsoleButton roster = new ConsoleButton(
			rosterX,
			firstFooterY,
			footerButtonWidth,
			24,
			Component.literal("玩家名单"),
			button -> pushScreen(new PlayerRosterScreen(this)),
			Variant.NORMAL
		);
		roster.active = console.isPresent();
		roster.setTooltip(Tooltip.create(Component.literal("查看全部登记玩家的身份、在线、初始化与生存状态")));
		this.addRenderableWidget(roster);

		int developmentX = stackedFooter
			? panelX + 16
			: rosterX + footerButtonWidth + footerGap;
		ConsoleButton development = new ConsoleButton(
			developmentX,
			secondFooterY,
			footerButtonWidth,
			24,
			Component.literal("开发与诊断"),
			button -> pushScreen(new PagePreviewCatalogScreen(this)),
			Variant.NORMAL
		);
		development.active = previewAvailable(session);
		development.setTooltip(
			Tooltip.create(Component.literal("打开数据包页面预览器；此入口不参与正式游戏流程"))
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
	}

	@Override
	public void tick() {
		super.tick();
		if (navigationLocked()) {
			return;
		}
		long revision = ClientConsoleState.revision();
		if (this.observedConsoleRevision != revision) {
			this.observedConsoleRevision = revision;
			rebuildConsoleWidgets();
		}
	}

	private void updatePrimaryAction(
		final Optional<ConsoleSnapshotS2CPayload> console
	) {
		this.actionButton.active = console
			.map(snapshot -> snapshot.activeFlow().isPresent()
				|| recommendedAction(snapshot).isPresent()
				|| !snapshot.actions().isEmpty())
			.orElse(false)
			&& !ClientConsoleState.pending();
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
		ConsoleSnapshotS2CPayload console = ClientConsoleState.snapshot().orElse(null);
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
			Optional<ConsoleSnapshotS2CPayload> console = ClientConsoleState.snapshot();
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
		int railWidth = 116;
		int mainX = innerX + railWidth + 14;
		int mainWidth = innerWidth - railWidth - 14;
		int contentTop = panelY + 61;

		drawPhaseRail(graphics, snapshot.phase(), innerX + 5, contentTop, railWidth - 5, 20);
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

		int actionZoneY = panelY + 187;
		drawActionZone(
			graphics,
			console,
			mainX,
			actionZoneY,
			mainWidth,
			Math.max(30, panelY + panelHeight - footerHeight() - 8 - actionZoneY)
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
		int actionZoneY = actionButtonY() - 18;
		drawCompactPhaseRail(graphics, snapshot.phase(), innerX, contentTop, innerWidth);
		if (actionZoneY - contentTop < 68) {
			Component phaseName = console
				.filter(value -> value.phaseId().isPresent())
				.map(value -> ConsoleText.parse(
					value.phaseNameJson(),
					value.phaseId().orElseThrow().toString()
				))
				.orElseGet(() -> Component.translatable(
					"pixel_tzz_pro.phase." + snapshot.phase().getSerializedName()
				));
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(phaseName.getString(), innerWidth),
				innerX,
				contentTop + 17,
				phaseColor(snapshot),
				true
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
			42
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
		int phaseIndex = snapshot.phase().ordinal();
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.screen.current_phase"),
			x,
			y,
			SECONDARY_TEXT,
			false
		);
		Component sequence = console
			.flatMap(ConsoleSnapshotS2CPayload::phaseId)
			.<Component>map(identifier -> Component.literal(identifier.getPath()))
			.orElseGet(() -> Component.literal(
				"%02d / %02d".formatted(phaseIndex + 1, GamePhase.values().length)
			));
		graphics.text(this.font, sequence, x + width - this.font.width(sequence), y, MUTED_TEXT, false);
		Component phaseName = console
			.filter(value -> value.phaseId().isPresent())
			.map(value -> ConsoleText.parse(
				value.phaseNameJson(),
				value.phaseId().orElseThrow().toString()
			))
			.orElseGet(() -> Component.translatable(
				"pixel_tzz_pro.phase." + snapshot.phase().getSerializedName()
			));
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
			.orElseGet(() -> Component.translatable(
				"pixel_tzz_pro.phase.hint." + snapshot.phase().getSerializedName()
			));
		graphics.textWithWordWrap(this.font, phaseDetail, x, y + 37, width, SECONDARY_TEXT, false);
	}

	private void drawPhaseRail(
		final GuiGraphicsExtractor graphics,
		final GamePhase currentPhase,
		final int x,
		final int y,
		final int width,
		final int step
	) {
		GamePhase[] phases = GamePhase.values();
		int currentIndex = currentPhase.ordinal();
		int lineX = x + 4;
		graphics.fill(lineX, y + 3, lineX + 1, y + (phases.length - 1) * step + 4, 0xFF3B4A56);

		for (int index = 0; index < phases.length; index++) {
			int nodeY = y + index * step;
			boolean current = index == currentIndex;
			boolean completed = index < currentIndex;
			int color = current ? BRAND_GOLD : completed ? INFO_CYAN : MUTED_TEXT;
			if (current) {
				int glowAlpha = 74 + (int)((Math.sin(Util.getMillis() / 240.0) + 1.0) * 34.0);
				graphics.fill(lineX - 4, nodeY - 3, lineX + 5, nodeY + 6, withAlpha(BRAND_GOLD, glowAlpha));
				graphics.fill(lineX - 2, nodeY - 1, lineX + 3, nodeY + 4, BRAND_GOLD);
			} else {
				graphics.fill(lineX - 1, nodeY, lineX + 2, nodeY + 3, color);
			}
			graphics.text(
				this.font,
				Component.translatable("pixel_tzz_pro.phase." + phases[index].getSerializedName()),
				x + 15,
				nodeY - 3,
				color,
				false
			);
		}
	}

	private void drawCompactPhaseRail(
		final GuiGraphicsExtractor graphics,
		final GamePhase currentPhase,
		final int x,
		final int y,
		final int width
	) {
		GamePhase[] phases = GamePhase.values();
		int currentIndex = currentPhase.ordinal();
		int startX = x + 3;
		int endX = x + width - 4;
		graphics.fill(startX, y + 2, endX, y + 3, 0xFF3B4A56);
		for (int index = 0; index < phases.length; index++) {
			int nodeX = startX + (endX - startX) * index / (phases.length - 1);
			int color = index == currentIndex ? BRAND_GOLD : index < currentIndex ? INFO_CYAN : MUTED_TEXT;
			int radius = index == currentIndex ? 3 : 2;
			graphics.fill(nodeX - radius, y + 2 - radius, nodeX + radius + 1, y + 3 + radius, color);
		}
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
		if (height < 58) {
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
			.sorted(
				Comparator.comparing(ActionEntry::section)
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
		return snapshot.phase() == GamePhase.RUNNING ? BRAND_GOLD : INFO_CYAN;
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
		return Math.max(1, Math.min(620, this.width - 12));
	}

	private int panelHeight() {
		return Math.max(1, Math.min(330, this.height - 12));
	}

	private int footerHeight() {
		return panelWidth() < 420 ? 72 : 43;
	}

	private int actionButtonY() {
		boolean compact = panelWidth() < 560 || panelHeight() < 296;
		return compact
			? panelY() + panelHeight() - footerHeight() - 31
			: panelY() + 233;
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
