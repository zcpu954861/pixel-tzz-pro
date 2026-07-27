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

import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState.ConnectionStatus;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState.Snapshot;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
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

	public ControlConsoleScreen(final Screen parent) {
		super(Component.translatable("pixel_tzz_pro.menu.open"), parent, Motion.ROOT_ENTER);
	}

	@Override
	protected void init() {
		Snapshot snapshot = ClientSessionState.snapshot();
		int panelX = panelX();
		int panelY = panelY();
		int panelWidth = panelWidth();
		int buttonY = panelY + panelHeight() - 34;
		int availableButtonWidth = Math.max(86, (panelWidth - 42) / 2);
		int actionWidth = Math.min(210, availableButtonWidth);
		int backWidth = Math.min(96, availableButtonWidth);

		this.actionButton = new ConsoleButton(
			panelX + 16,
			buttonY,
			actionWidth,
			24,
			actionLabel(snapshot),
			button -> {
				if (previewAvailable(ClientSessionState.snapshot())) {
					pushScreen(new PagePreviewCatalogScreen(this));
				}
			},
			Variant.PRIMARY
		);
		updateActionButton(snapshot);
		this.addRenderableWidget(this.actionButton);

		ConsoleButton back = new ConsoleButton(
			panelX + panelWidth - backWidth - 16,
			buttonY,
			backWidth,
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
		if (this.actionButton != null) {
			updateActionButton(ClientSessionState.snapshot());
		}
	}

	private void updateActionButton(final Snapshot snapshot) {
		this.actionButton.setMessage(actionLabel(snapshot));
		this.actionButton.active = previewAvailable(snapshot);
		this.actionButton.setTooltip(
			Tooltip.create(
				Component.translatable(
					previewAvailable(snapshot)
						? "pixel_tzz_pro.screen.action.preview.tooltip"
						: "pixel_tzz_pro.screen.action.unavailable"
				)
			)
		);
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
			int panelX = panelX();
			int panelY = panelY();
			int panelWidth = panelWidth();
			int panelHeight = panelHeight();
			int innerX = panelX + 16;
			int innerWidth = panelWidth - 32;
			boolean compact = panelWidth < 520 || panelHeight < 276;

			drawShell(graphics, snapshot, panelX, panelY, panelWidth, panelHeight, innerX, innerWidth);
			if (compact) {
				drawCompactContent(graphics, snapshot, panelX, panelY, panelWidth, panelHeight, innerX, innerWidth);
			} else {
				drawFullContent(graphics, snapshot, panelX, panelY, panelWidth, panelHeight, innerX, innerWidth);
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

		graphics.fill(innerX, panelY + 49, innerX + innerWidth, panelY + 50, 0xFF33424E);
		graphics.fill(innerX, panelY + panelHeight - 43, innerX + innerWidth, panelY + panelHeight - 42, 0xFF33424E);
	}

	private void drawFullContent(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
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
		drawPhaseHero(graphics, snapshot, mainX, contentTop, mainWidth);

		int cardY = panelY + 121;
		int gap = 7;
		int cardWidth = (mainWidth - gap * 2) / 3;
		drawConnectionCard(graphics, snapshot, mainX, cardY, cardWidth, 57);
		drawDefinitionCard(graphics, snapshot, mainX + cardWidth + gap, cardY, cardWidth, 57);
		drawHostCard(graphics, snapshot, mainX + (cardWidth + gap) * 2, cardY, cardWidth, 57);

		int guidanceY = panelY + 187;
		int guidanceHeight = panelY + panelHeight - 51 - guidanceY;
		drawGuidance(graphics, snapshot, mainX, guidanceY, mainWidth, Math.max(28, guidanceHeight));
	}

	private void drawCompactContent(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final int panelX,
		final int panelY,
		final int panelWidth,
		final int panelHeight,
		final int innerX,
		final int innerWidth
	) {
		int contentTop = panelY + 58;
		drawCompactPhaseRail(graphics, snapshot.phase(), innerX, contentTop, innerWidth);
		drawPhaseHero(graphics, snapshot, innerX, contentTop + 17, innerWidth);

		int cardY = contentTop + 72;
		int footerTop = panelY + panelHeight - 43;
		if (cardY + 45 < footerTop) {
			int gap = 5;
			int cardWidth = (innerWidth - gap * 2) / 3;
			drawConnectionCard(graphics, snapshot, innerX, cardY, cardWidth, 45);
			drawDefinitionCard(graphics, snapshot, innerX + cardWidth + gap, cardY, cardWidth, 45);
			drawHostCard(graphics, snapshot, innerX + (cardWidth + gap) * 2, cardY, cardWidth, 45);
		}
	}

	private void drawPhaseHero(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
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
		Component sequence = Component.literal("%02d / %02d".formatted(phaseIndex + 1, GamePhase.values().length));
		graphics.text(this.font, sequence, x + width - this.font.width(sequence), y, MUTED_TEXT, false);
		drawScaledText(
			graphics,
			Component.translatable("pixel_tzz_pro.phase." + snapshot.phase().getSerializedName()),
			x,
			y + 15,
			1.45F,
			phaseColor(snapshot),
			true
		);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.phase.hint." + snapshot.phase().getSerializedName()),
			x,
			y + 37,
			width,
			SECONDARY_TEXT,
			false
		);
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

	private void drawGuidance(
		final GuiGraphicsExtractor graphics,
		final Snapshot snapshot,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		int color = summaryColor(snapshot);
		graphics.fill(x, y, x + width, y + height, SURFACE_RAISED);
		graphics.outline(x, y, width, height, withAlpha(color, 125));
		graphics.fill(x, y, x + 3, y + height, color);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.screen.current_guidance"),
			x + 10,
			y + 7,
			color,
			false
		);
		graphics.textWithWordWrap(this.font, summaryText(snapshot), x + 67, y + 7, width - 76, color, false);
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

	private static Component actionLabel(final Snapshot snapshot) {
		if (previewAvailable(snapshot)) {
			return Component.translatable("pixel_tzz_pro.screen.action.preview");
		}
		if (!snapshot.definitionHealthy() || snapshot.gameDefinitionCount() == 0) {
			return Component.translatable("pixel_tzz_pro.screen.action.definition_required");
		}
		return Component.translatable(
			snapshot.currentPlayerHost()
				? "pixel_tzz_pro.screen.action.initialize"
				: snapshot.adminEligible()
					? "pixel_tzz_pro.screen.action.host_required"
					: "pixel_tzz_pro.screen.action.wait"
		);
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
		return Math.min(620, Math.max(280, this.width - 20));
	}

	private int panelHeight() {
		return Math.min(296, Math.max(214, this.height - 20));
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
