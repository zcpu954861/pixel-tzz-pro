package io.github.zcpu954861.pixeltzzpro.client.screen;

import io.github.zcpu954861.pixeltzzpro.client.ClientPageState;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.PageStatus;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.PagePurpose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens server-authored pages and restores a forced page if another screen replaces it.
 */
public final class LivePageSupervisor {
	private static LivePageSession session;
	private static Screen returnScreen;
	private static boolean liveWasActive;
	private static boolean forcedWasActive;
	private static boolean restrictedPauseActive;

	private LivePageSupervisor() {
	}

	public static void reset() {
		session = null;
		returnScreen = null;
		liveWasActive = false;
		forcedWasActive = false;
		restrictedPauseActive = false;
	}

	static void openRestrictedPause(
		final Minecraft client,
		final DataDrivenPageScreen flowScreen
	) {
		restrictedPauseActive = true;
		client.gui.setScreen(new ForcedFlowPauseScreen(flowScreen));
	}

	static void resumeForcedPage(
		final Minecraft client,
		final DataDrivenPageScreen flowScreen
	) {
		restrictedPauseActive = false;
		client.gui.setScreen(flowScreen);
	}

	public static void tick(final Minecraft client) {
		PageStatus status = ClientPageState.pageStatus();
		boolean liveActive = status != PageStatus.IDLE
			&& ClientPageState.pagePurpose() != PagePurpose.PREVIEW;
		boolean forcedActive = ClientPageState.forcedPageActive();
		Screen current = client.gui.screen();

		if (forcedWasActive && !forcedActive) {
			if (
				current instanceof DataDrivenPageScreen page
					&& page.isLivePageScreen()
			) {
				page.releaseFromServer();
			}
			forcedWasActive = false;
			liveWasActive = false;
			session = null;
			returnScreen = null;
			restrictedPauseActive = false;
			return;
		}

		if (!liveActive) {
			if (liveWasActive) {
				liveWasActive = false;
				session = null;
				returnScreen = null;
			}
			return;
		}

		boolean showingLive = current instanceof DataDrivenPageScreen page
			&& page.isLivePageScreen();
		if (forcedActive && restrictedPauseActive) {
			liveWasActive = true;
			forcedWasActive = true;
			return;
		}
		if (!showingLive && (forcedActive || !liveWasActive)) {
			if (!liveWasActive) {
				returnScreen = current instanceof DataDrivenPageScreen page
					? page.returnScreen()
					: current;
				session = new LivePageSession();
			}
			client.gui.setScreen(new DataDrivenPageScreen(returnScreen, session));
		}
		liveWasActive = true;
		forcedWasActive |= forcedActive;
	}
}
