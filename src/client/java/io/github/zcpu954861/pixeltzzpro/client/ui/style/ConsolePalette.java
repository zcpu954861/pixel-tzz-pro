package io.github.zcpu954861.pixeltzzpro.client.ui.style;

/**
 * Shared semantic colors for the verified Pixel TZZ control-console visual language.
 */
public final class ConsolePalette {
	public static final int BACKGROUND_TOP = 0xEC09111A;
	public static final int BACKGROUND_BOTTOM = 0xF0101923;
	public static final int PANEL = 0xF51A232E;
	public static final int PANEL_BORDER = 0xFF40505F;
	public static final int SURFACE = 0xF2222E3A;
	public static final int SURFACE_RAISED = 0xF5293744;
	public static final int SURFACE_BORDER = 0xFF354654;
	public static final int BRAND_GOLD = 0xFFF2C14E;
	public static final int INFO_CYAN = 0xFF55D7E6;
	public static final int MAIN_TEXT = 0xFFF1F5F7;
	public static final int SECONDARY_TEXT = 0xFFAAB4BE;
	public static final int MUTED_TEXT = 0xFF697580;
	public static final int SUCCESS = 0xFF5FD18A;
	public static final int WARNING = 0xFFF2B84B;
	public static final int DANGER = 0xFFD94B5B;

	private ConsolePalette() {
	}

	public static int withAlpha(final int color, final int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
