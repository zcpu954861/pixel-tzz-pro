package io.github.zcpu954861.pixeltzzpro.client.screen;

import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.ClientTimelineState;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Adds the context-sensitive terminal entry to the vanilla pause screen; local presentation
 * preferences live under the vanilla Options screen instead of competing with gameplay actions.
 */
public final class PauseMenuEntry {
	private static final int BRAND_GOLD = 0xF4C95D;
	private static final int HALF_BUTTON_WIDTH = 98;
	private static final int ICON_BUTTON_WIDTH = 20;
	private static final int ROW_GAP = 4;

	private PauseMenuEntry() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof PauseScreen pauseScreen) || !pauseScreen.showsPauseMenu() || client.player == null) {
				return;
			}
			ClientTimelineState.probe();

			List<AbstractWidget> widgets = Screens.getWidgets(screen);
			List<AbstractWidget> iconButtons = widgets
				.stream()
				.filter(widget -> widget.getWidth() == ICON_BUTTON_WIDTH && widget.getHeight() == 20)
				.sorted(Comparator.comparingInt(AbstractWidget::getX))
				.toList();
			int iconRowY = iconButtons.isEmpty() ? -1 : iconButtons.getFirst().getY();
			List<AbstractWidget> upperHalfButtons = widgets
				.stream()
				.filter(widget -> widget.getWidth() == HALF_BUTTON_WIDTH && widget.getHeight() == 20)
				.filter(widget -> iconRowY >= 0 && widget.getY() < iconRowY)
				.toList();
			int upperRowY = upperHalfButtons.stream().mapToInt(AbstractWidget::getY).max().orElse(-1);
			List<AbstractWidget> upperRow = upperHalfButtons
				.stream()
				.filter(widget -> widget.getY() == upperRowY)
				.sorted(Comparator.comparingInt(AbstractWidget::getX))
				.toList();
			if (iconButtons.size() == 4 && upperRow.size() >= 2) {
				AbstractWidget leftColumn = upperRow.getFirst();
				AbstractWidget rightColumn = upperRow.getLast();
				int iconGroupWidth = iconButtons.size() * ICON_BUTTON_WIDTH + (iconButtons.size() - 1) * ROW_GAP;
				int iconX = leftColumn.getX() + (leftColumn.getWidth() - iconGroupWidth) / 2;
				for (AbstractWidget iconButton : iconButtons) {
					iconButton.setX(iconX);
					iconX += ICON_BUTTON_WIDTH + ROW_GAP;
				}

				Button entry = buildEntry(client, screen, rightColumn.getX(), iconRowY, rightColumn.getWidth());
				widgets.add(entry);
				registerLiveRefresh(screen, entry);
				return;
			}

			// ponytail: another mod changed the vanilla grid; retain a usable fallback instead of guessing its layout.
			int entryX = Math.max(8, scaledWidth - 128);
			Button entry = buildEntry(client, screen, entryX, 8, 120);
			widgets.add(entry);
			registerLiveRefresh(screen, entry);
		});
	}

	private static Button buildEntry(
		final Minecraft client,
		final Screen parent,
		final int x,
		final int y,
		final int width
	) {
		EntryState state = entryState();
		Button entry = Button.builder(
				label(state.labelKey()),
				button -> {
					EntryState current = entryState();
					if (current.currentPlayerHost()) {
						client.gui.setScreen(new ControlConsoleScreen(parent));
					} else if (current.forcedFlow()) {
						LivePageSupervisor.openCurrentForcedPage(client, parent);
					} else if (current.recapAvailable()) {
						client.gui.setScreen(new RecapScreen(parent));
					} else {
						LivePageSupervisor.openPlayerTerminal(client, parent);
					}
				}
			)
			.bounds(x, y, width, 20)
			.tooltip(tooltip(state))
			.build();
		return entry;
	}

	private static void registerLiveRefresh(final Screen screen, final Button entry) {
		ScreenEvents.afterTick(screen).register(ignored -> refreshEntry(entry));
	}

	private static void refreshEntry(final Button entry) {
		EntryState state = entryState();
		Component label = label(state.labelKey());
		if (!entry.getMessage().equals(label)) {
			entry.setMessage(label);
			entry.setTooltip(tooltip(state));
		}
	}

	private static EntryState entryState() {
		ClientSessionState.Snapshot snapshot = ClientSessionState.snapshot();
		boolean forcedFlow = !snapshot.currentPlayerHost()
			&& io.github.zcpu954861.pixeltzzpro.client.ClientPageState.forcedPageActive();
		boolean recapAvailable = ClientTimelineState.recapAvailable();
		String labelKey = snapshot.currentPlayerHost()
			? "pixel_tzz_pro.menu.host"
			: forcedFlow
				? "pixel_tzz_pro.menu.forced"
				: recapAvailable
					? "pixel_tzz_pro.menu.recap"
					: "pixel_tzz_pro.menu.terminal";
		return new EntryState(
			labelKey,
			forcedFlow,
			snapshot.currentPlayerHost(),
			recapAvailable
		);
	}

	private static Component label(final String key) {
		return Component.translatable(key).withColor(BRAND_GOLD);
	}

	private static Tooltip tooltip(final EntryState state) {
		return Tooltip.create(
			Component.translatable(
				state.forcedFlow()
					? "pixel_tzz_pro.menu.forced.tooltip"
					: state.currentPlayerHost()
						? "pixel_tzz_pro.menu.open.tooltip"
						: state.recapAvailable()
							? "pixel_tzz_pro.menu.recap.tooltip"
							: "pixel_tzz_pro.menu.terminal.tooltip"
			)
		);
	}

	private record EntryState(
		String labelKey,
		boolean forcedFlow,
		boolean currentPlayerHost,
		boolean recapAvailable
	) {
	}
}
