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
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.AnimatedEditBox;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Virtualized, bounded selection for an authoritative target snapshot.
 */
final class TargetSelectionScreen extends TransitioningConsoleScreen {
	private static final int ROW_HEIGHT = 30;
	private static final int ROW_GAP = 4;

	private final Screen returnTo;
	private final ActionEntry action;
	private final TargetSnapshotS2CPayload snapshot;
	private final Set<UUID> selected = new LinkedHashSet<>();
	private int firstVisible;
	private int visibleRows;
	private boolean waiting;
	private String filter = "";
	private boolean filterDirty;
	private ConsoleButton submitButton;

	TargetSelectionScreen(
		final Screen returnTo,
		final ActionEntry action,
		final TargetSnapshotS2CPayload snapshot
	) {
		super(Component.literal("选择目标"), returnTo, Motion.REPLACE_ENTER);
		this.returnTo = returnTo;
		this.action = action;
		this.snapshot = snapshot;
	}

	@Override
	protected void init() {
		rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		int panelX = panelX();
		int panelY = panelY();
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();
		int listTop = panelY + 92;
		int footerTop = panelY + panelHeight - footerHeight();
		this.visibleRows = Math.max(1, (footerTop - listTop) / (ROW_HEIGHT + ROW_GAP));
		List<Target> filtered = filteredTargets();
		this.firstVisible = Math.clamp(
			this.firstVisible,
			0,
			Math.max(0, filtered.size() - this.visibleRows)
		);

		List<Target> visible = filtered
			.stream()
			.skip(this.firstVisible)
			.limit(this.visibleRows)
			.toList();
		for (int index = 0; index < visible.size(); index++) {
			Target target = visible.get(index);
			int rowY = listTop + index * (ROW_HEIGHT + ROW_GAP);
			boolean chosen = this.selected.contains(target.playerId());
			ConsoleButton row = new ConsoleButton(
				panelX + 18,
				rowY,
				panelWidth - 36,
				ROW_HEIGHT,
				Component.empty(),
				button -> toggle(target),
				chosen ? Variant.PRIMARY : Variant.NORMAL
			);
			row.active = !this.waiting;
			row.setTooltip(
				Tooltip.create(
					Component.literal(
						"点击" + (chosen ? "取消选择" : "选择此玩家")
					)
				)
			);
			this.addRenderableWidget(row);
		}

		AnimatedEditBox search = new AnimatedEditBox(
			this.font,
			panelX + 18,
			panelY + 64,
			panelWidth - 36,
			22,
			Component.literal("筛选玩家")
		);
		search.enableConsoleChrome();
		search.setMaxLength(64);
		search.setHint(Component.literal("输入玩家名、身份或流程状态…"));
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
		search.active = !this.waiting;
		this.addRenderableWidget(search);

		int footerY = panelY + panelHeight - 36;
		boolean compactFooter = panelWidth < 420;
		int innerWidth = panelWidth - 36;
		int gap = 8;
		int compactWidth = Math.max(1, (innerWidth - gap * 2) / 3);
		ConsoleButton clear = new ConsoleButton(
			panelX + 18,
			footerY,
			compactFooter ? compactWidth : 76,
			24,
			Component.literal("清除选择"),
			button -> {
				this.selected.clear();
				rebuildWidgets();
			},
			Variant.NORMAL
		);
		clear.active = !this.selected.isEmpty() && !this.waiting;
		this.addRenderableWidget(clear);

		int rightButtonWidth = compactFooter ? compactWidth : 96;
		this.submitButton = new ConsoleButton(
			compactFooter
				? panelX + 18 + compactWidth + gap
				: panelX + panelWidth - rightButtonWidth * 2 - gap - 18,
			footerY,
			rightButtonWidth,
			24,
			Component.literal(this.waiting ? "等待确认…" : "继续"),
			button -> submit(),
			Variant.PRIMARY
		);
		updateSubmitState();
		this.addRenderableWidget(this.submitButton);
		ConsoleButton back = new ConsoleButton(
			compactFooter
				? panelX + 18 + (compactWidth + gap) * 2
				: panelX + panelWidth - rightButtonWidth - 18,
			footerY,
			rightButtonWidth,
			24,
			Component.literal("返回"),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(
			this.waiting
				? back
				: this.filterDirty ? search : this.submitButton.active ? this.submitButton : search
		);
		this.filterDirty = false;
	}

	private void toggle(final Target target) {
		if (!target.eligible() || this.waiting) {
			return;
		}
		if (!this.selected.remove(target.playerId())) {
			if (this.snapshot.maximumTargets() == 1) {
				this.selected.clear();
			}
			if (this.selected.size() < this.snapshot.maximumTargets()) {
				this.selected.add(target.playerId());
			}
		}
		rebuildWidgets();
	}

	private void submit() {
		if (
			this.selected.size() < this.snapshot.minimumTargets()
				|| this.selected.size() > this.snapshot.maximumTargets()
		) {
			return;
		}
		this.waiting = ClientConsoleState.prepareOperation(
			this.action,
			List.copyOf(this.selected)
		);
		updateSubmitState();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.filterDirty) {
			rebuildWidgets();
			return;
		}
		if (!this.waiting || navigationLocked()) {
			return;
		}
		ClientConsoleState.confirmation()
			.filter(value -> value.operationId().equals(this.action.operationId()))
			.ifPresent(value -> {
				this.waiting = false;
				replaceScreen(new OperationConfirmationScreen(this, this.action));
			});
		if (!ClientConsoleState.pending() && ClientConsoleState.confirmation().isEmpty()) {
			this.waiting = false;
			updateSubmitState();
		}
	}

	private void updateSubmitState() {
		if (this.submitButton == null) {
			return;
		}
		this.submitButton.active = !this.waiting
			&& this.selected.size() >= this.snapshot.minimumTargets()
			&& this.selected.size() <= this.snapshot.maximumTargets();
		this.submitButton.setMessage(Component.literal(this.waiting ? "等待确认…" : "继续"));
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return this.waiting || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		return this.waiting || super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		if (navigationLocked() || verticalAmount == 0.0) {
			return navigationLocked();
		}
		int next = Math.clamp(
			this.firstVisible + (verticalAmount > 0.0 ? -1 : 1),
			0,
			Math.max(0, filteredTargets().size() - this.visibleRows)
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
			graphics.fill(x, y, x + width, y + height, PANEL);
			graphics.outline(x, y, width, height, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + height, INFO_CYAN);
			graphics.fill(x + 4, y, x + width, y + 2, BRAND_GOLD);
			graphics.text(this.font, "TARGET MATRIX // 64 人有界虚拟名单", x + 18, y + 12, INFO_CYAN, false);
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
				y + 29,
				MAIN_TEXT,
				true
			);
			String selectionSummary = "可选 %d 人   ·   已选 %d 人   ·   至少 %d 人   ·   当前显示 %d-%d"
				.formatted(
					this.snapshot.targets().size(),
					this.selected.size(),
					this.snapshot.minimumTargets(),
					filteredTargets().isEmpty() ? 0 : this.firstVisible + 1,
					Math.min(
						filteredTargets().size(),
						this.firstVisible + this.visibleRows
					)
				);
			String feedback = ClientConsoleState.feedback();
			boolean feedbackError = !feedback.isEmpty() && ClientConsoleState.feedbackError();
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(
					feedbackError ? feedback : selectionSummary,
					width - 36
				),
				x + 18,
				y + 48,
				feedbackError ? DANGER : SECONDARY_TEXT,
				false
			);
			int listTop = y + 92;
			int listBottom = y + height - footerHeight();
			graphics.fill(x + 12, listTop - 6, x + width - 12, listBottom, SURFACE);
			graphics.outline(x + 12, listTop - 6, width - 24, listBottom - listTop + 6, SURFACE_BORDER);
			List<Target> filtered = filteredTargets();
			if (filtered.isEmpty()) {
				graphics.text(
					this.font,
					this.filter.isBlank() ? "没有符合当前操作范围的玩家" : "没有匹配筛选条件的玩家",
					x + 28,
					listTop + 16,
					MUTED_TEXT,
					false
				);
			}
			if (filtered.size() > this.visibleRows) {
				int trackX = x + width - 9;
				int trackHeight = listBottom - listTop;
				int thumbHeight = Math.max(
					18,
					trackHeight * this.visibleRows / filtered.size()
				);
				int travel = trackHeight - thumbHeight;
				int maximum = filtered.size() - this.visibleRows;
				int thumbY = listTop + (maximum == 0 ? 0 : travel * this.firstVisible / maximum);
				graphics.fill(trackX, listTop, trackX + 2, listBottom, withAlpha(MUTED_TEXT, 90));
				graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, INFO_CYAN);
			}
			super.extractRenderState(
				graphics,
				transitionMouseX(mouseX),
				transitionMouseY(mouseY),
				tickProgress
			);
			renderVisibleIdentities(graphics, x, y);
		} finally {
			endNavigationFrame(graphics, transition);
		}
	}

	private void renderVisibleIdentities(
		final GuiGraphicsExtractor graphics,
		final int panelX,
		final int panelY
	) {
		List<Target> visible = filteredTargets()
			.stream()
			.skip(this.firstVisible)
			.limit(this.visibleRows)
			.toList();
		for (int index = 0; index < visible.size(); index++) {
			Target target = visible.get(index);
			PlayerIdentityRenderer.drawTargetRow(
				graphics,
				this.font,
				target,
				panelX + 18,
				panelY + 92 + index * (ROW_HEIGHT + ROW_GAP),
				panelWidth() - 36,
				ROW_HEIGHT,
				this.selected.contains(target.playerId())
			);
		}
	}

	private List<Target> filteredTargets() {
		String needle = this.filter.strip().toLowerCase(java.util.Locale.ROOT);
		return this.snapshot.targets()
			.stream()
			.filter(target ->
				needle.isEmpty()
					|| (
						target.name()
							+ "\n"
							+ ConsoleText.parse(
								target.roleNameJson(),
								target.roleId().toString()
							).getString()
							+ "\n"
							+ target.flowStatus()
					).toLowerCase(java.util.Locale.ROOT).contains(needle)
			)
			.sorted(
				Comparator.<Target>comparingInt(target -> target.eligible() ? 0 : 1)
					.thenComparingInt(target -> target.online() ? 0 : 1)
					.thenComparing(Target::name, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(Target::playerId)
			)
			.toList();
	}

	private int panelWidth() {
		return Math.max(1, Math.min(720, designWidth() - 12));
	}

	private int panelHeight() {
		return Math.max(1, Math.min(430, designHeight() - 12));
	}

	private int footerHeight() {
		return 48;
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
}
