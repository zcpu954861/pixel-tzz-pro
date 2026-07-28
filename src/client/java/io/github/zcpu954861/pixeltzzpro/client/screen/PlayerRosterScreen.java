package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.AnimatedEditBox;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Read-only, server-authored roster shared by every host phase.
 */
final class PlayerRosterScreen extends TransitioningConsoleScreen {
	private static final int ROW_HEIGHT = 36;
	private static final int ROW_GAP = 4;

	private long observedRevision = Long.MIN_VALUE;
	private int firstVisible;
	private int visibleRows;
	private String filter = "";
	private boolean filterDirty;

	PlayerRosterScreen(final Screen parent) {
		super(Component.literal("玩家名单"), parent, Motion.PUSH_ENTER);
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
		int x = panelX();
		int y = panelY();
		int width = panelWidth();
		int height = panelHeight();
		int listTop = y + 82;
		int listBottom = y + height - 46;
		this.visibleRows = Math.max(1, (listBottom - listTop) / (ROW_HEIGHT + ROW_GAP));
		List<Target> players = visiblePlayers();
		this.firstVisible = Math.clamp(
			this.firstVisible,
			0,
			Math.max(0, players.size() - this.visibleRows)
		);

		AnimatedEditBox search = new AnimatedEditBox(
			this.font,
			x + 18,
			y + 45,
			width - 36,
			22,
			Component.literal("筛选玩家")
		);
		search.enableConsoleChrome();
		search.setMaxLength(64);
		search.setHint(Component.literal("搜索玩家、身份、队伍或生存状态…"));
		search.setTextColor(MAIN_TEXT);
		search.setTextColorUneditable(MUTED_TEXT);
		search.setValue(this.filter);
		search.setResponder(value -> {
			if (!this.filter.equals(value)) {
				this.filter = value;
				this.firstVisible = 0;
				this.filterDirty = true;
			}
		});
		this.addRenderableWidget(search);

		int footerY = y + height - 35;
		ConsoleButton refresh = new ConsoleButton(
			x + 18,
			footerY,
			94,
			24,
			Component.literal("刷新名单"),
			button -> ClientConsoleState.requestConsole(),
			Variant.NORMAL
		);
		refresh.active = !ClientConsoleState.pending();
		this.addRenderableWidget(refresh);
		ConsoleButton back = new ConsoleButton(
			x + width - 94,
			footerY,
			76,
			24,
			Component.literal("返回"),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(this.filterDirty ? search : back);
		this.filterDirty = false;
	}

	@Override
	public void tick() {
		super.tick();
		if (navigationLocked()) {
			return;
		}
		long revision = ClientConsoleState.revision();
		if (this.filterDirty || revision != this.observedRevision) {
			this.observedRevision = revision;
			rebuildWidgets();
		}
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
			Math.max(0, visiblePlayers().size() - this.visibleRows)
		);
		if (next == this.firstVisible) {
			return false;
		}
		this.firstVisible = next;
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
			int listTop = y + 82;
			int listBottom = y + height - 46;
			graphics.fill(x, y, x + width, y + height, PANEL);
			graphics.outline(x, y, width, height, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + height, INFO_CYAN);
			graphics.fill(x + 4, y, x + width, y + 2, BRAND_GOLD);
			graphics.text(this.font, "PLAYER ROSTER // 玩家权威名单", x + 18, y + 12, INFO_CYAN, false);
			drawServerSyncBadge(graphics, x + width - 14, y + 10);
			List<Target> allPlayers = ClientConsoleState.snapshot()
				.map(ConsoleSnapshotS2CPayload::players)
				.orElseGet(List::of);
			long online = allPlayers.stream().filter(Target::online).count();
			graphics.text(
				this.font,
				"登记 %d 人  ·  在线 %d 人  ·  当前显示 %d 人"
					.formatted(allPlayers.size(), online, visiblePlayers().size()),
				x + 18,
				y + 28,
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
			renderRows(graphics, x, listTop);
			drawScrollBar(graphics, x, width, listTop, listBottom);
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

	private void renderRows(
		final GuiGraphicsExtractor graphics,
		final int panelX,
		final int listTop
	) {
		List<Target> players = visiblePlayers();
		if (players.isEmpty()) {
			graphics.text(
				this.font,
				this.filter.isBlank() ? "当前没有登记玩家" : "没有匹配筛选条件的玩家",
				panelX + 28,
				listTop + 16,
				MUTED_TEXT,
				false
			);
			return;
		}
		UUID hostId = ClientConsoleState.snapshot()
			.flatMap(ConsoleSnapshotS2CPayload::host)
			.map(ConsoleSnapshotS2CPayload.HostInfo::playerId)
			.orElse(null);
		for (
			int index = this.firstVisible;
			index < Math.min(players.size(), this.firstVisible + this.visibleRows);
			index++
		) {
			Target player = players.get(index);
			boolean host = player.playerId().equals(hostId);
			PlayerIdentityRenderer.drawTargetRow(
				graphics,
				this.font,
				player,
				panelX + 18,
				listTop + (index - this.firstVisible) * (ROW_HEIGHT + ROW_GAP),
				panelWidth() - 36,
				ROW_HEIGHT,
				false,
				playerDetail(player, host),
				host ? BRAND_GOLD : PlayerIdentityRenderer.completionColor(player)
			);
		}
	}

	private String playerDetail(final Target player, final boolean host) {
		String role = ConsoleText.parse(player.roleNameJson(), player.roleId().toString()).getString();
		String team = player.teamId().isEmpty()
			? ""
			: ConsoleText.parse(player.teamNameJson(), player.teamId().orElseThrow().toString()).getString();
		String life = ConsoleText.parse(
			player.lifeStateNameJson(),
			player.lifeStateId().toString()
		).getString();
		String initialization = PlayerIdentityRenderer.completionLabel(player);
		return (host ? "主持人  ·  " : "")
			+ role
			+ (team.isBlank() ? "" : " / " + team)
			+ "  ·  "
			+ life
			+ "  ·  "
			+ initialization;
	}

	private List<Target> visiblePlayers() {
		UUID hostId = ClientConsoleState.snapshot()
			.flatMap(ConsoleSnapshotS2CPayload::host)
			.map(ConsoleSnapshotS2CPayload.HostInfo::playerId)
			.orElse(null);
		String needle = this.filter.strip().toLowerCase(Locale.ROOT);
		return ClientConsoleState.snapshot()
			.map(ConsoleSnapshotS2CPayload::players)
			.orElseGet(List::of)
			.stream()
			.filter(player -> needle.isEmpty() || searchableText(player).contains(needle))
			.sorted(
				Comparator.<Target>comparingInt(player ->
					player.playerId().equals(hostId) ? 0 : player.online() ? 1 : 2
				).thenComparing(Target::name, String.CASE_INSENSITIVE_ORDER)
			)
			.toList();
	}

	private static String searchableText(final Target player) {
		return (
			player.name()
				+ "\n"
				+ ConsoleText.parse(player.roleNameJson(), player.roleId().toString()).getString()
				+ "\n"
				+ ConsoleText.parse(player.teamNameJson(), "").getString()
				+ "\n"
				+ ConsoleText.parse(player.lifeStateNameJson(), player.lifeStateId().toString()).getString()
		).toLowerCase(Locale.ROOT);
	}

	private void drawScrollBar(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int width,
		final int listTop,
		final int listBottom
	) {
		int total = visiblePlayers().size();
		if (total <= this.visibleRows) {
			return;
		}
		int trackHeight = Math.max(1, listBottom - listTop);
		int thumbHeight = Math.max(18, trackHeight * this.visibleRows / total);
		int thumbY = listTop
			+ (trackHeight - thumbHeight) * this.firstVisible / (total - this.visibleRows);
		int trackX = x + width - 9;
		graphics.fill(trackX, listTop, trackX + 2, listBottom, withAlpha(MUTED_TEXT, 90));
		graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, INFO_CYAN);
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
