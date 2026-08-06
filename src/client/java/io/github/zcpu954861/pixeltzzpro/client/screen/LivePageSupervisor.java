package io.github.zcpu954861.pixeltzzpro.client.screen;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.PageStatus;
import io.github.zcpu954861.pixeltzzpro.client.animation.OperationSubtitleAnimator;
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

	static void openPlayerTerminal(final Minecraft client, final Screen parent) {
		if (ClientSessionState.snapshot().currentPlayerHost()) {
			client.gui.setScreen(new ControlConsoleScreen(parent));
			return;
		}
		if (ClientPageState.forcedPageActive()) {
			openCurrentForcedPage(client, parent);
			return;
		}
		boolean requestAccepted = ClientPageState.openTerminal();
		if (!requestAccepted && !ClientPageState.terminalSyncPending()) {
			return;
		}
		session = new LivePageSession();
		returnScreen = parent;
		liveWasActive = false;
		forcedWasActive = false;
		restrictedPauseActive = false;
		client.gui.setScreen(new DataDrivenPageScreen(parent, session));
	}

	static void openCurrentForcedPage(final Minecraft client, final Screen parent) {
		if (
			ClientSessionState.snapshot().currentPlayerHost()
				|| !ClientPageState.forcedPageActive()
		) {
			return;
		}
		if (session == null) {
			session = new LivePageSession();
		}
		if (returnScreen == null) {
			returnScreen = parent;
		}
		restrictedPauseActive = false;
		liveWasActive = true;
		forcedWasActive = true;
		client.gui.setScreen(new DataDrivenPageScreen(returnScreen, session));
	}

	public static void tick(final Minecraft client) {
		PageStatus status = ClientPageState.pageStatus();
		boolean forcedActive = !ClientSessionState.snapshot().currentPlayerHost()
			&& ClientPageState.forcedPageActive();
		boolean liveActive = status != PageStatus.IDLE
			&& ClientPageState.pagePurpose() == PagePurpose.FORCED_FLOW;
		Screen current = client.gui.screen();
		var terminalRelease = ClientPageState.consumeTerminalRelease();
		if (terminalRelease.isPresent()) {
			OperationSubtitleAnimator.enqueue(terminalRelease.orElseThrow().message());
			if (
				current instanceof DataDrivenPageScreen page
					&& page.isNormalTerminalScreen()
			) {
				page.releaseFromServer();
			} else {
				ClientPageState.completeTerminalRelease();
				TransitioningConsoleScreen.preparePopEntry(returnScreen);
				client.gui.setScreen(returnScreen);
			}
			return;
		}
		if (
			ClientPageState.terminalReleasePending()
				&& !(
					current instanceof DataDrivenPageScreen page
						&& page.isNormalTerminalScreen()
				)
		) {
			ClientPageState.completeTerminalRelease();
			TransitioningConsoleScreen.preparePopEntry(returnScreen);
			client.gui.setScreen(returnScreen);
			return;
		}
		if (
			current instanceof TransitioningConsoleScreen console
				&& console.requiresCurrentHost()
				&& !ClientSessionState.snapshot().currentPlayerHost()
				&& !console.navigationLocked()
				&& !(
					current instanceof OperationConfirmationScreen
						&& ClientConsoleState.hostCommitResolutionPending()
				)
		) {
			openPlayerTerminal(client, null);
			return;
		}

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
