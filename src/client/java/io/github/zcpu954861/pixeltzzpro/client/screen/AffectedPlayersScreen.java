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
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Read-only drill-down for the exact server-frozen target set shown by a confirmation.
 */
final class AffectedPlayersScreen extends TransitioningConsoleScreen {
	private static final int ROW_HEIGHT = 30;
	private static final int ROW_GAP = 4;

	private final ActionEntry action;
	private final List<Target> targets;
	private int firstVisible;
	private int visibleRows;

	AffectedPlayersScreen(
		final Screen parent,
		final ActionEntry action,
		final List<Target> targets
	) {
		super(Component.literal("全部受影响玩家"), parent, Motion.PUSH_ENTER);
		this.action = action;
		this.targets = targets.stream()
			.sorted(
				Comparator.comparing(Target::name, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(Target::playerId)
			)
			.toList();
	}

	@Override
	protected void init() {
		rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		int x = panelX();
		int y = panelY();
		int width = panelWidth();
		int height = panelHeight();
		int listTop = y + 76;
		int footerTop = y + height - 46;
		this.visibleRows = Math.max(1, (footerTop - listTop) / (ROW_HEIGHT + ROW_GAP));
		this.firstVisible = Math.clamp(
			this.firstVisible,
			0,
			Math.max(0, this.targets.size() - this.visibleRows)
		);

		int backWidth = Math.max(1, Math.min(90, width - 36));
		ConsoleButton back = new ConsoleButton(
			x + width - 18 - backWidth,
			y + height - 35,
			backWidth,
			24,
			Component.literal("返回确认"),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(back);
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
			int listTop = y + 76;
			int listBottom = y + height - 46;
			graphics.fill(x, y, x + width, y + height, PANEL);
			graphics.outline(x, y, width, height, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + height, INFO_CYAN);
			graphics.fill(x + 4, y, x + width, y + 2, BRAND_GOLD);
			graphics.text(
				this.font,
				"全部受影响玩家 // AFFECTED PLAYERS",
				x + 18,
				y + 13,
				INFO_CYAN,
				false
			);
			drawServerSyncBadge(graphics, x + width - 14, y + 10);
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(
					ConsoleText.parse(
						this.action.labelJson(),
						this.action.operationId().toString()
					).getString(),
					width - 36
				),
				x + 18,
				y + 35,
				MAIN_TEXT,
				true
			);
			graphics.text(
				this.font,
				"本次确认将影响 %d 人 · 名单由服务端锁定".formatted(this.targets.size()),
				x + 18,
				y + 53,
				SECONDARY_TEXT,
				false
			);

			graphics.fill(x + 12, listTop - 6, x + width - 12, listBottom, SURFACE);
			graphics.outline(
				x + 12,
				listTop - 6,
				width - 24,
				listBottom - listTop + 6,
				SURFACE_BORDER
			);
			for (
				int index = this.firstVisible;
				index < Math.min(this.targets.size(), this.firstVisible + this.visibleRows);
				index++
			) {
				PlayerIdentityRenderer.drawTargetRow(
					graphics,
					this.font,
					this.targets.get(index),
					x + 18,
					listTop + (index - this.firstVisible) * (ROW_HEIGHT + ROW_GAP),
					width - 36,
					ROW_HEIGHT,
					false
				);
			}
			drawScrollBar(graphics, x, width, listTop, listBottom);
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(
					"只读目标快照 · 返回后继续本次二次确认",
					Math.max(0, width - 144)
				),
				x + 18,
				y + height - 28,
				MUTED_TEXT,
				false
			);
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
		final int panelX,
		final int panelWidth,
		final int listTop,
		final int listBottom
	) {
		if (this.targets.size() <= this.visibleRows) {
			return;
		}
		int trackX = panelX + panelWidth - 9;
		int trackHeight = listBottom - listTop;
		int thumbHeight = Math.max(18, trackHeight * this.visibleRows / this.targets.size());
		int travel = trackHeight - thumbHeight;
		int maximum = this.targets.size() - this.visibleRows;
		int thumbY = listTop + travel * this.firstVisible / maximum;
		graphics.fill(trackX, listTop, trackX + 2, listBottom, withAlpha(MUTED_TEXT, 90));
		graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, INFO_CYAN);
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
			Math.max(0, this.targets.size() - this.visibleRows)
		);
		if (next == this.firstVisible) {
			return false;
		}
		this.firstVisible = next;
		return true;
	}

	private int panelWidth() {
		return Math.max(1, Math.min(720, this.width - 12));
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
