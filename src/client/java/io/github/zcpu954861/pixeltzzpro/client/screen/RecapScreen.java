package io.github.zcpu954861.pixeltzzpro.client.screen;

import net.minecraft.client.gui.screens.Screen;

/**
 * Terminal participant entry. The server response contains only participant-visible frozen facts.
 */
public final class RecapScreen extends TimelineScreen {
	public RecapScreen(final Screen parent) {
		super(parent, true);
	}
}
