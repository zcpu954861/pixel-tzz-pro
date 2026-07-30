package io.github.zcpu954861.pixeltzzpro.client.screen;

import net.minecraft.client.gui.screens.Screen;

/**
 * Terminal participant entry. The server response contains only participant-visible frozen facts.
 */
public final class RecapScreen extends TimelineScreen {
	public RecapScreen(final Screen parent) {
		super(parent, true);
	}

	/**
	 * The recap reuses the host timeline presentation, but its server projection is explicitly
	 * filtered for an ordinary participant. Treating this screen as host-only makes the live-screen
	 * supervisor open a player terminal on top of it after the game ends, which is then immediately
	 * closed by the recap transition and creates a repeated close/notification loop.
	 */
	@Override
	boolean requiresCurrentHost() {
		return false;
	}
}
