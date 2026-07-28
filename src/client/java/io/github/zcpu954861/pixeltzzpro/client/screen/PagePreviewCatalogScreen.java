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

import io.github.zcpu954861.pixeltzzpro.client.ClientPageState;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.Catalog;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.CatalogStatus;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.PageStatus;
import io.github.zcpu954861.pixeltzzpro.client.screen.preview.PreviewModel;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Administrator-only catalog for opening production-rendered data-pack pages.
 */
public final class PagePreviewCatalogScreen extends TransitioningConsoleScreen {
	private static final int ROW_HEIGHT = 25;

	private long observedRevision = Long.MIN_VALUE;
	private int firstVisibleEntry;
	private boolean waitingForPage;

	public PagePreviewCatalogScreen(final Screen parent) {
		super(
			Component.translatable("pixel_tzz_pro.preview.catalog.title"),
			parent,
			Motion.PUSH_ENTER
		);
	}

	@Override
	protected void init() {
		ClientPageState.requestCatalog();
		buildWidgets();
	}

	private void buildWidgets() {
		this.clearWidgets();
		int panelX = panelX();
		int panelY = panelY();
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();
		int listTop = panelY + 58;
		int listBottom = panelY + panelHeight - 46;
		int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
		Catalog catalog = ClientPageState.catalog();
		List<PreviewCatalogS2CPayload.Entry> entries = catalog.entries();
		this.firstVisibleEntry = Math.clamp(
			this.firstVisibleEntry,
			0,
			Math.max(0, entries.size() - visibleRows)
		);

		if (catalog.status() == CatalogStatus.READY) {
			for (
				int index = this.firstVisibleEntry;
				index < Math.min(entries.size(), this.firstVisibleEntry + visibleRows);
				index++
			) {
				PreviewCatalogS2CPayload.Entry entry = entries.get(index);
				int row = index - this.firstVisibleEntry;
				ConsoleButton button = new ConsoleButton(
					panelX + 18,
					listTop + row * ROW_HEIGHT,
					panelWidth - 64,
					21,
					Component.literal("[" + entry.gameId() + "]  " + entry.title()),
					pressed -> {
						this.waitingForPage = true;
						ClientPageState.requestPage(entry.pageId());
						buildWidgets();
					},
					Variant.NORMAL,
					false
				);
				button.setTooltip(
					Tooltip.create(
						Component.literal(entry.pageId() + "\n" + entry.gameId())
					)
				);
				button.active = !this.waitingForPage;
				this.addRenderableWidget(button);
			}
		}

		if (entries.size() > visibleRows) {
			ConsoleButton up = new ConsoleButton(
				panelX + panelWidth - 40,
				listTop,
				22,
				21,
				Component.literal("▲"),
				pressed -> scroll(-1),
				Variant.NORMAL
			);
			up.active = this.firstVisibleEntry > 0;
			this.addRenderableWidget(up);
			ConsoleButton down = new ConsoleButton(
				panelX + panelWidth - 40,
				listBottom - 21,
				22,
				21,
				Component.literal("▼"),
				pressed -> scroll(1),
				Variant.NORMAL
			);
			down.active = this.firstVisibleEntry + visibleRows < entries.size();
			this.addRenderableWidget(down);
		}

		ConsoleButton refresh = new ConsoleButton(
			panelX + 18,
			panelY + panelHeight - 34,
			112,
			24,
			Component.translatable("pixel_tzz_pro.preview.refresh"),
			pressed -> {
				this.waitingForPage = false;
				ClientPageState.requestCatalog();
				buildWidgets();
			},
			Variant.NORMAL
		);
		refresh.active = !this.waitingForPage && catalog.status() != CatalogStatus.LOADING;
		this.addRenderableWidget(refresh);

		ConsoleButton back = new ConsoleButton(
			panelX + panelWidth - 114,
			panelY + panelHeight - 34,
			96,
			24,
			Component.translatable("pixel_tzz_pro.screen.back"),
			pressed -> onClose(),
			Variant.NAVIGATION
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(back);
	}

	private void scroll(final int rows) {
		this.firstVisibleEntry = Math.max(0, this.firstVisibleEntry + rows);
		buildWidgets();
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
		if (verticalAmount != 0.0) {
			scroll(verticalAmount > 0.0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void tick() {
		super.tick();
		long currentRevision = ClientPageState.revision();
		if (this.observedRevision == currentRevision) {
			return;
		}
		this.observedRevision = currentRevision;
		PageStatus status = ClientPageState.pageStatus();
		if (
			this.waitingForPage
				&& (status == PageStatus.READY || status == PageStatus.RESOURCE_ERROR)
				&& ClientPageState.activePage().isPresent()
		) {
			pushScreen(new DataDrivenPageScreen(this, new PreviewModel()));
			return;
		}
		if (this.waitingForPage && (status == PageStatus.ERROR || status == PageStatus.IDLE)) {
			this.waitingForPage = false;
		}
		buildWidgets();
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
			int panelX = panelX();
			int panelY = panelY();
			int panelWidth = panelWidth();
			int panelHeight = panelHeight();
			Catalog catalog = ClientPageState.catalog();
			graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
			graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
			graphics.fill(panelX, panelY, panelX + 4, panelY + panelHeight, BRAND_GOLD);
			graphics.fill(panelX + 4, panelY, panelX + panelWidth, panelY + 2, INFO_CYAN);
			graphics.text(
				this.font,
				Component.translatable("pixel_tzz_pro.preview.catalog.eyebrow"),
				panelX + 18,
				panelY + 12,
				SECONDARY_TEXT,
				false
			);
			graphics.text(
				this.font,
				this.title,
				panelX + 18,
				panelY + 28,
				MAIN_TEXT,
				true
			);
			graphics.text(
				this.font,
				Component.literal(
					catalog.status() == CatalogStatus.READY
						? catalog.entries().size() + " 页 // generation " + catalog.generation()
						: catalog.status() == CatalogStatus.LOADING ? "正在同步目录…" : "目录不可用"
				),
				panelX + 18,
				panelY + 43,
				catalog.status() == CatalogStatus.ERROR ? DANGER : MUTED_TEXT,
				false
			);
			if (catalog.entries().isEmpty() || catalog.status() != CatalogStatus.READY) {
				String message = this.waitingForPage
					? "正在按需同步并预检页面…"
					: ClientPageState.pageStatus() == PageStatus.ERROR
						? ClientPageState.pageMessage()
						: catalog.message();
				graphics.fill(
					panelX + 18,
					panelY + 70,
					panelX + panelWidth - 18,
					panelY + panelHeight - 50,
					SURFACE
				);
				graphics.outline(
					panelX + 18,
					panelY + 70,
					panelWidth - 36,
					panelHeight - 120,
					SURFACE_BORDER
				);
				graphics.textWithWordWrap(
					this.font,
					Component.literal(message.isEmpty() ? "当前没有可预览页面" : message),
					panelX + 30,
					panelY + 84,
					panelWidth - 60,
					this.waitingForPage ? INFO_CYAN : SECONDARY_TEXT,
					false
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

	private int panelWidth() {
		return Math.min(620, Math.max(300, this.width - 20));
	}

	private int panelHeight() {
		return Math.min(330, Math.max(230, this.height - 20));
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
