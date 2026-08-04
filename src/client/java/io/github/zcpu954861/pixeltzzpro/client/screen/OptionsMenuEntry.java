package io.github.zcpu954861.pixeltzzpro.client.screen;

import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Places the mod's local preferences beside Done on the vanilla Options screen. */
public final class OptionsMenuEntry {
	private static final int BRAND_GOLD = 0xF2C14E;
	private static final int PREFERRED_WIDTH = 150;
	private static final int GAP = 8;

	private OptionsMenuEntry() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof OptionsScreen)) {
				return;
			}
			List<AbstractWidget> widgets = Screens.getWidgets(screen);
			Button done = widgets.stream()
				.filter(Button.class::isInstance)
				.map(Button.class::cast)
				.filter(button -> button.getMessage().equals(CommonComponents.GUI_DONE))
				.findFirst()
				.orElse(null);
			if (done == null) {
				return;
			}

			int available = Math.max(72, scaledWidth - 16 - GAP);
			int width = Math.min(PREFERRED_WIDTH, available / 2);
			int rowWidth = width * 2 + GAP;
			int left = Math.max(8, (scaledWidth - rowWidth) / 2);
			done.setX(left + width + GAP);
			done.setWidth(width);

			Button settings = Button.builder(
					Component.translatable("pixel_tzz_pro.menu.settings").withColor(BRAND_GOLD),
					button -> client.gui.setScreen(new ModSettingsScreen(screen))
				)
				.bounds(left, done.getY(), width, done.getHeight())
				.tooltip(
					Tooltip.create(Component.translatable("pixel_tzz_pro.menu.settings.tooltip"))
				)
				.build();
			widgets.add(settings);
		});
	}
}
