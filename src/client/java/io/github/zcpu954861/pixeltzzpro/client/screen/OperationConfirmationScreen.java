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
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState.OperationResultContext;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState;
import io.github.zcpu954861.pixeltzzpro.client.animation.OperationSubtitleAnimator;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compact server-authored review with transparent renewal of short-lived server tokens.
 */
final class OperationConfirmationScreen extends TransitioningConsoleScreen {
	private static final int TARGET_ROW_HEIGHT = 30;
	private static final int TARGET_SUMMARY_HEIGHT = 38;
	private static final int CONSEQUENCE_HEIGHT = 30;
	private static final int MAX_VISIBLE_CONSEQUENCES = 4;

	private final Screen returnTo;
	private final ActionEntry action;
	private List<UUID> reviewTargets = List.of();
	private ConsoleButton confirmButton;
	private boolean submitted;
	private boolean reviewing;
	private boolean submitAfterReview;
	private boolean renewalRequested;
	private UUID observedToken;

	OperationConfirmationScreen(final Screen returnTo, final ActionEntry action) {
		super(Component.literal("二次确认"), returnTo, Motion.REPLACE_ENTER);
		this.returnTo = returnTo;
		this.action = action;
		ClientConsoleState.confirmation().ifPresent(this::rememberReview);
	}

	@Override
	boolean requiresCurrentHost() {
		return !this.action.operationType().equals("player_action")
			&& !ClientPageState.isTerminalHostControlOperation(
				this.action.operationType(),
				this.action.operationId()
			);
	}

	@Override
	protected void init() {
		rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		this.confirmButton = null;
		int x = panelX();
		int y = panelY();
		int width = panelWidth();
		int footerY = y + panelHeight() - 36;
		ConfirmationS2CPayload confirmation = ClientConsoleState.confirmation().orElse(null);
		int availableFooterWidth = Math.max(2, width - 36);
		int footerGap = Math.min(8, Math.max(0, availableFooterWidth - 2));
		int backWidth = Math.min(
			92,
			Math.max(1, (availableFooterWidth - footerGap) / 3)
		);
		int primaryWidth = Math.min(
			144,
			Math.max(1, availableFooterWidth - footerGap - backWidth)
		);
		int backX = x + width - 18 - backWidth;
		int primaryX = backX - footerGap - primaryWidth;

		if (confirmation != null) {
			this.confirmButton = new ConsoleButton(
				primaryX,
				footerY,
				primaryWidth,
				24,
				confirmLabel(),
				button -> confirmOrReview(),
				Variant.DANGER
			);
			this.confirmButton.active = !this.submitted && !this.reviewing;
			this.addRenderableWidget(this.confirmButton);
		} else {
			ConsoleButton review = new ConsoleButton(
				primaryX,
				footerY,
				primaryWidth,
				24,
				Component.literal(this.reviewing ? "正在重新审阅…" : "重新审阅"),
				button -> reviewAgain(),
				Variant.PRIMARY
			);
			review.active = !this.reviewing && !ClientConsoleState.pending();
			this.addRenderableWidget(review);
			this.confirmButton = review;
		}

		if (
			confirmation != null
				&& confirmation.targets().size() > 1
				&& panelHeight() >= 250
		) {
			int detailsWidth = Math.min(126, Math.max(88, width / 5));
			ConsoleButton details = new ConsoleButton(
				x + width - 24 - detailsWidth,
				y + 95,
				detailsWidth,
				22,
				Component.literal("查看全部受影响玩家"),
				button -> pushScreen(
					new AffectedPlayersScreen(
						this,
						this.action,
						orderedTargets(confirmation.targets())
					)
				),
				Variant.NORMAL
			);
			this.addRenderableWidget(details);
		}

		ConsoleButton back = new ConsoleButton(
			backX,
			footerY,
			backWidth,
			24,
			Component.literal(
				this.returnTo instanceof TargetSelectionScreen ? "返回上一步" : "取消"
			),
			button -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(this.confirmButton.active ? this.confirmButton : back);
	}

	@Override
	public void tick() {
		super.tick();
		continueAutomaticRenewal();
		ConfirmationS2CPayload confirmation = ClientConsoleState.confirmation().orElse(null);
		if (confirmation != null && !confirmation.tokenId().equals(this.observedToken)) {
			boolean autoSubmit = this.submitAfterReview;
			rememberReview(confirmation);
			this.reviewing = false;
			this.submitted = false;
			this.renewalRequested = false;
			if (autoSubmit) {
				this.submitAfterReview = false;
				if (ClientConsoleState.commitConfirmation()) {
					this.submitted = true;
				}
				updatePrimaryButton();
			} else {
				rebuildWidgets();
			}
			return;
		}

		OperationResultContext result = ClientConsoleState.lastResult()
			.filter(value -> value.operationId().filter(this.action.operationId()::equals).isPresent())
			.orElse(null);
		if (this.submitted && result != null) {
			this.submitted = false;
			if (result.successfulCommit(this.action.operationId())) {
				String message = result.message().isBlank() ? "操作已成功完成" : result.message();
				ClientConsoleState.clearResult();
				if (this.action.operationType().equals("player_action")) {
					OperationSubtitleAnimator.enqueue(message);
					closeToParent();
					return;
				}
				if (
					this.returnTo instanceof DataDrivenPageScreen terminal
						&& terminal.isNormalTerminalScreen()
				) {
					io.github.zcpu954861.pixeltzzpro.client.ClientPageState.closeActivePage();
				}
				OperationSubtitleAnimator.enqueue(message);
				closeToHud();
				return;
			}
			if (
				result.code() == OperationCode.CONFIRMATION_EXPIRED
					|| result.code() == OperationCode.CONFIRMATION_MISSING
			) {
				this.submitted = false;
				refreshExpiredAndSubmit();
				return;
			}
			if (this.action.operationType().equals("player_action")) {
				String message = result.message().isBlank()
					? "本次玩家终端操作未执行"
					: result.message();
				ClientConsoleState.clearResult();
				OperationSubtitleAnimator.enqueue(message);
				closeToParent();
				return;
			}
			this.observedToken = confirmation == null ? null : confirmation.tokenId();
			rebuildWidgets();
			return;
		}
		if (this.reviewing && result != null && confirmation == null) {
			this.reviewing = false;
			this.submitAfterReview = false;
			this.renewalRequested = false;
			this.observedToken = null;
			rebuildWidgets();
			return;
		}
		if (
			confirmation == null
				&& this.observedToken != null
				&& !this.submitted
				&& !this.reviewing
		) {
			this.observedToken = null;
			rebuildWidgets();
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
			int x = panelX();
			int y = panelY();
			int width = panelWidth();
			int height = panelHeight();
			graphics.fill(x, y, x + width, y + height, PANEL);
			graphics.outline(x, y, width, height, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + height, DANGER);
			graphics.fill(x + 4, y, x + width, y + 2, BRAND_GOLD);
			graphics.text(this.font, "HIGH-RISK CONFIRMATION // 二次确认", x + 18, y + 13, DANGER, false);
			drawServerSyncBadge(graphics, x + width - 14, y + 10);

			ConfirmationS2CPayload confirmation = ClientConsoleState.confirmation().orElse(null);
			if (confirmation == null) {
				renderUnavailable(graphics, x, y, width);
			} else {
				renderConfirmation(graphics, confirmation, x, y, width, height);
				renderTargetSummary(graphics, confirmation, x, y, width, height);
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

	private void renderConfirmation(
		final GuiGraphicsExtractor graphics,
		final ConfirmationS2CPayload confirmation,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(
				ConsoleText.parse(
					confirmation.titleJson(),
					this.action.operationId().toString()
				).getString(),
				width - 36
			),
			x + 18,
			y + 34,
			MAIN_TEXT,
			true
		);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(confirmation.contextSummary(), width - 36),
			x + 18,
			y + 52,
			SECONDARY_TEXT,
			false
		);

		boolean compact = height < 300;
		int consequenceTop = consequenceTop(confirmation, y);
		int consequenceBottom = y + height - 51;
		graphics.fill(
			x + 14,
			consequenceTop,
			x + width - 14,
			consequenceBottom,
			SURFACE
		);
		graphics.outline(
			x + 14,
			consequenceTop,
			width - 28,
			consequenceBottom - consequenceTop,
			SURFACE_BORDER
		);
		graphics.text(this.font, "执行后将发生", x + 26, consequenceTop + 9, DANGER, false);
		int consequenceStep = compact ? 13 : CONSEQUENCE_HEIGHT;
		int availableRows = Math.max(
			1,
			(consequenceBottom - consequenceTop - (compact ? 23 : 27)) / consequenceStep
		);
		int visible = Math.min(
			Math.min(MAX_VISIBLE_CONSEQUENCES, confirmation.consequenceJson().size()),
			availableRows
		);
		for (int index = 0; index < visible; index++) {
			int rowY = consequenceTop + (compact ? 24 : 27) + index * consequenceStep;
			if (compact) {
				graphics.fill(x + 24, rowY, x + 27, rowY + 8, DANGER);
			} else {
				graphics.fill(x + 24, rowY - 4, x + width - 24, rowY + 22, 0xB51A242D);
				graphics.fill(x + 24, rowY - 4, x + 27, rowY + 22, DANGER);
			}
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(
					ConsoleText.parse(
						confirmation.consequenceJson().get(index),
						"无法解析的后果说明"
					).getString(),
					width - 70
				),
				x + 34,
				rowY + (compact ? 0 : 4),
				MAIN_TEXT,
				false
			);
		}
		if (height >= 250) {
			graphics.text(
				this.font,
				this.font.plainSubstrByWidth(
					"提交时自动重新校验权限、阶段、目标与操作后果",
					Math.max(0, width - 306)
				),
				x + 18,
				y + height - 29,
				MUTED_TEXT,
				false
			);
		}
	}

	private void renderTargetSummary(
		final GuiGraphicsExtractor graphics,
		final ConfirmationS2CPayload confirmation,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		List<Target> targets = orderedTargets(confirmation.targets());
		if (targets.isEmpty()) {
			return;
		}
		graphics.text(
			this.font,
			"影响玩家  ·  %d 人".formatted(targets.size()),
			x + 18,
			y + 71,
			INFO_CYAN,
			false
		);
		if (height < 250) {
			return;
		}
		if (targets.size() == 1) {
			PlayerIdentityRenderer.drawCompactIdentity(
				graphics,
				this.font,
				targets.getFirst(),
				x + 18,
				y + 87,
				width - 36
			);
			return;
		}

		int cardX = x + 18;
		int cardY = y + 87;
		int cardWidth = width - 36;
		graphics.fill(cardX, cardY, cardX + cardWidth, cardY + TARGET_SUMMARY_HEIGHT, SURFACE);
		graphics.outline(
			cardX,
			cardY,
			cardWidth,
			TARGET_SUMMARY_HEIGHT,
			withAlpha(INFO_CYAN, 150)
		);
		graphics.fill(cardX, cardY, cardX + 3, cardY + TARGET_SUMMARY_HEIGHT, INFO_CYAN);

		int stackCount = Math.min(2, targets.size());
		int headSize = 22;
		int headStep = 14;
		int headX = cardX + 10;
		int headY = cardY + 8;
		for (int index = stackCount - 1; index >= 0; index--) {
			Target target = targets.get(index);
			int currentX = headX + index * headStep;
			graphics.fill(
				currentX - 1,
				headY - 1,
				currentX + headSize + 1,
				headY + headSize + 1,
				PANEL
			);
			PlayerIdentityRenderer.drawHead(
				graphics,
				target.playerId(),
				target.online(),
				currentX,
				headY,
				headSize,
				false
			);
		}
		if (targets.size() > stackCount) {
			int stackRight = headX + headSize + (stackCount - 1) * headStep;
			int badgeX = stackRight - 11;
			int badgeY = headY + headSize - 9;
			graphics.fill(
				badgeX,
				badgeY,
				stackRight + 1,
				headY + headSize + 1,
				PANEL
			);
			graphics.text(
				this.font,
				"…",
				badgeX + 3,
				badgeY - 1,
				MAIN_TEXT,
				true
			);
		}

		int labelX = headX + headSize + (stackCount - 1) * headStep + 10;
		int detailsWidth = Math.min(126, Math.max(88, width / 5));
		int detailsX = x + width - 24 - detailsWidth;
		String summary = "%s 等 %d 人".formatted(
			targets.getFirst().name(),
			targets.size()
		);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(summary, Math.max(20, detailsX - labelX - 8)),
			labelX,
			cardY + 7,
			MAIN_TEXT,
			true
		);
		graphics.text(
			this.font,
			this.font.plainSubstrByWidth(
				"服务端已锁定全部目标",
				Math.max(20, detailsX - labelX - 8)
			),
			labelX,
			cardY + 21,
			INFO_CYAN,
			false
		);
	}

	private void renderUnavailable(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width
	) {
		OperationResultContext result = ClientConsoleState.lastResult()
			.filter(value -> value.operationId().filter(this.action.operationId()::equals).isPresent())
			.orElse(null);
		boolean error = ClientConsoleState.feedbackError();
		String message = result == null
			? this.reviewing
				? "正在重新获取操作上下文，并审阅权限、阶段和目标。"
				: "确认已关闭；如需继续，请重新向服务端审阅。"
			: result.message();
		graphics.text(
			this.font,
			this.reviewing ? "正在重新审阅" : error ? "本次操作未执行" : "需要重新审阅",
			x + 18,
			y + 46,
			error ? DANGER : INFO_CYAN,
			true
		);
		graphics.textWithWordWrap(
			this.font,
			Component.literal(message),
			x + 18,
			y + 72,
			width - 36,
			SECONDARY_TEXT,
			false
		);
	}

	private int consequenceTop(
		final ConfirmationS2CPayload confirmation,
		final int panelY
	) {
		if (panelHeight() < 250 || confirmation.targets().isEmpty()) {
			return panelY + 76;
		}
		return panelY
			+ (confirmation.targets().size() == 1 ? 130 : 137);
	}

	private void confirmOrReview() {
		if (ClientConsoleState.confirmationExpired()) {
			refreshExpiredAndSubmit();
			return;
		}
		if (ClientConsoleState.commitConfirmation()) {
			this.submitted = true;
			updatePrimaryButton();
		}
	}

	private void refreshExpiredAndSubmit() {
		ClientConsoleState.clearResult();
		this.submitAfterReview = true;
		this.reviewing = true;
		this.submitted = false;
		this.renewalRequested = false;
		continueAutomaticRenewal();
		updatePrimaryButton();
	}

	private void continueAutomaticRenewal() {
		if (
			!this.reviewing
				|| this.renewalRequested
				|| ClientConsoleState.pending()
		) {
			return;
		}
		if (ClientConsoleState.refreshOperation(this.action, renewalTargetIds())) {
			this.renewalRequested = true;
		} else {
			this.submitAfterReview = false;
			this.reviewing = false;
			updatePrimaryButton();
		}
	}

	private void reviewAgain() {
		ClientConsoleState.clearResult();
		this.submitAfterReview = false;
		this.renewalRequested = false;
		this.reviewing = true;
		this.submitted = false;
		continueAutomaticRenewal();
		updatePrimaryButton();
	}

	private void updatePrimaryButton() {
		if (this.confirmButton == null) {
			return;
		}
		if (ClientConsoleState.confirmation().isPresent()) {
			this.confirmButton.active = !this.submitted && !this.reviewing;
			this.confirmButton.setMessage(confirmLabel());
			return;
		}
		this.confirmButton.active = !this.reviewing && !ClientConsoleState.pending();
		this.confirmButton.setMessage(
			Component.literal(this.reviewing ? "正在重新审阅…" : "重新审阅")
		);
	}

	private void rememberReview(final ConfirmationS2CPayload confirmation) {
		this.reviewTargets = confirmation.targets().stream().map(Target::playerId).toList();
		this.observedToken = confirmation.tokenId();
	}

	private static List<Target> orderedTargets(final List<Target> targets) {
		return targets.stream()
			.sorted(
				Comparator.comparing(Target::name, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(Target::playerId)
			)
			.toList();
	}

	private List<UUID> renewalTargetIds() {
		return this.action.targetMode().equals("none") ? List.of() : this.reviewTargets;
	}

	private Component confirmLabel() {
		if (this.submitted) {
			return Component.literal("正在提交…");
		}
		if (this.reviewing) {
			return Component.literal(this.submitAfterReview ? "正在校验并执行…" : "正在重新审阅…");
		}
		return Component.literal("确认执行");
	}

	private int panelWidth() {
		return Math.max(1, Math.min(650, designWidth() - 12));
	}

	private int panelHeight() {
		ConfirmationS2CPayload confirmation = ClientConsoleState.confirmation().orElse(null);
		if (confirmation == null) {
			return Math.max(1, Math.min(220, designHeight() - 12));
		}
		int consequences = Math.min(MAX_VISIBLE_CONSEQUENCES, confirmation.consequenceJson().size());
		int targetBlock = confirmation.targets().isEmpty()
			? 0
			: confirmation.targets().size() == 1 ? TARGET_ROW_HEIGHT : TARGET_SUMMARY_HEIGHT;
		int desired = 166 + targetBlock + consequences * CONSEQUENCE_HEIGHT;
		return Math.max(1, Math.min(Math.clamp(desired, 250, 390), designHeight() - 12));
	}

	private int panelX() {
		return (designWidth() - panelWidth()) / 2;
	}

	private int panelY() {
		return (designHeight() - panelHeight()) / 2;
	}

	@Override
	public void onClose() {
		if (!this.submitted && ClientConsoleState.confirmation().isPresent()) {
			ClientConsoleState.cancelConfirmation();
		}
		closeToParent();
	}
}
