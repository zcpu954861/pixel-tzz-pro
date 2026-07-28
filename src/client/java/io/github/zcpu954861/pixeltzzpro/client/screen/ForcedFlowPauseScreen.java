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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * The forced flow stays active while Esc still exposes settings and a safe disconnect path.
 */
public final class ForcedFlowPauseScreen extends Screen {
	private final DataDrivenPageScreen flowScreen;

	ForcedFlowPauseScreen(final DataDrivenPageScreen flowScreen) {
		super(Component.literal("流程暂停菜单"));
		this.flowScreen = flowScreen;
	}

	@Override
	protected void init() {
		int width = Math.min(430, Math.max(280, this.width - 24));
		int x = (this.width - width) / 2;
		int y = (this.height - 210) / 2;
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
		int width = Math.min(430, Math.max(280, this.width - 24));
		int x = (this.width - width) / 2;
		int y = (this.height - 210) / 2;
		graphics.fill(x, y, x + width, y + 210, PANEL);
		graphics.outline(x, y, width, 210, PANEL_BORDER);
		graphics.fill(x, y, x + 4, y + 210, BRAND_GOLD);
		graphics.fill(x + 4, y, x + width, y + 2, INFO_CYAN);
		graphics.text(this.font, "FORCED FLOW // 受限暂停", x + 18, y + 14, INFO_CYAN, false);
		graphics.text(this.font, "当前选择流程仍在等待你完成", x + 18, y + 36, MAIN_TEXT, true);
		graphics.textWithWordWrap(
			this.font,
			Component.literal("你可以调整设置或正常断开连接，但不能借此绕过当前流程。"),
			x + 18,
			y + 56,
			width - 36,
			SECONDARY_TEXT,
			false
		);
		super.extractRenderState(graphics, mouseX, mouseY, tickProgress);
	}

	@Override
	public void onClose() {
		LivePageSupervisor.resumeForcedPage(this.minecraft, this.flowScreen);
	}
}
