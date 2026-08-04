package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.DANGER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;

import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * The forced flow stays active while Esc still exposes settings and a safe disconnect path.
 */
public final class ForcedFlowPauseScreen extends Screen {
	private static final int PANEL_HEIGHT = 242;

	private final DataDrivenPageScreen flowScreen;

	ForcedFlowPauseScreen(final DataDrivenPageScreen flowScreen) {
		super(Component.literal("流程暂停菜单"));
		this.flowScreen = flowScreen;
	}

	@Override
	protected void init() {
		int width = Math.min(
			430,
			Math.max(280, ConsoleScreenViewport.REFERENCE_WIDTH - 24)
		);
		int x = (ConsoleScreenViewport.REFERENCE_WIDTH - width) / 2;
		int y = (ConsoleScreenViewport.REFERENCE_HEIGHT - PANEL_HEIGHT) / 2;
		int buttonWidth = width - 36;
		ConsoleButton resume = new ConsoleButton(
			x + 18,
			y + 85,
			buttonWidth,
			26,
			Component.literal("返回当前流程"),
			button -> LivePageSupervisor.resumeForcedPage(this.minecraft, this.flowScreen),
			Variant.PRIMARY
		);
		this.addRenderableWidget(resume);
		this.addRenderableWidget(
			new ConsoleButton(
				x + 18,
				y + 119,
				buttonWidth,
				24,
				Component.literal("游戏设置"),
				button -> this.minecraft.gui.setScreen(
					new OptionsScreen(this, this.minecraft.options, this.minecraft.level != null)
				),
				Variant.NORMAL
			)
		);
		this.addRenderableWidget(
			new ConsoleButton(
				x + 18,
				y + 151,
				buttonWidth,
				24,
				Component.literal("动态文字设置"),
				button -> this.minecraft.gui.setScreen(
					new MessageAccessibilityScreen(this)
				),
				Variant.NORMAL
			)
		);
		this.addRenderableWidget(
			new ConsoleButton(
				x + 18,
				y + 183,
				buttonWidth,
				24,
				Component.literal("断开连接"),
				button -> {
					button.active = false;
					this.minecraft.getReportingContext().draftReportHandled(
						this.minecraft,
						this,
						() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE),
						true
					);
				},
				Variant.DANGER
			)
		);
		this.setInitialFocus(resume);
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
		ConsoleScreenViewport.Layout canvas = consoleCanvas();
		graphics.pose().pushMatrix();
		graphics.pose().translate(canvas.x(), canvas.y());
		graphics.pose().scale((float)canvas.scale(), (float)canvas.scale());
		try {
			int width = Math.min(
				430,
				Math.max(280, ConsoleScreenViewport.REFERENCE_WIDTH - 24)
			);
			int x = (ConsoleScreenViewport.REFERENCE_WIDTH - width) / 2;
			int y = (ConsoleScreenViewport.REFERENCE_HEIGHT - PANEL_HEIGHT) / 2;
			graphics.fill(x, y, x + width, y + PANEL_HEIGHT, PANEL);
			graphics.outline(x, y, width, PANEL_HEIGHT, PANEL_BORDER);
			graphics.fill(x, y, x + 4, y + PANEL_HEIGHT, BRAND_GOLD);
			graphics.fill(x + 4, y, x + width, y + 2, INFO_CYAN);
			graphics.text(
				this.font,
				"FORCED FLOW // 受限暂停",
				x + 18,
				y + 14,
				INFO_CYAN,
				false
			);
			graphics.text(
				this.font,
				"当前选择流程仍在等待你完成",
				x + 18,
				y + 36,
				MAIN_TEXT,
				true
			);
			graphics.textWithWordWrap(
				this.font,
				Component.literal("你可以调整设置或正常断开连接，但不能借此绕过当前流程。"),
				x + 18,
				y + 56,
				width - 36,
				SECONDARY_TEXT,
				false
			);
			super.extractRenderState(
				graphics,
				(int)Math.floor(canvas.toReferenceX(mouseX)),
				(int)Math.floor(canvas.toReferenceY(mouseY)),
				tickProgress
			);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return super.mouseClicked(referenceMouseEvent(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return super.mouseReleased(referenceMouseEvent(event));
	}

	@Override
	public boolean mouseDragged(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		ConsoleScreenViewport.Layout canvas = consoleCanvas();
		return super.mouseDragged(
			referenceMouseEvent(event),
			canvas.toReferenceLength(deltaX),
			canvas.toReferenceLength(deltaY)
		);
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		ConsoleScreenViewport.Layout canvas = consoleCanvas();
		return super.mouseScrolled(
			canvas.toReferenceX(mouseX),
			canvas.toReferenceY(mouseY),
			horizontalAmount,
			verticalAmount
		);
	}

	private MouseButtonEvent referenceMouseEvent(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = consoleCanvas();
		return new MouseButtonEvent(
			canvas.toReferenceX(event.x()),
			canvas.toReferenceY(event.y()),
			event.buttonInfo()
		);
	}

	private ConsoleScreenViewport.Layout consoleCanvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	@Override
	public void onClose() {
		LivePageSupervisor.resumeForcedPage(this.minecraft, this.flowScreen);
	}
}
