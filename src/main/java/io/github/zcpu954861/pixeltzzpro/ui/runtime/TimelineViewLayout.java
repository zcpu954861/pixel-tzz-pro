package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;

/**
 * Deterministic responsive geometry for the built-in mission route and recap surface.
 */
public final class TimelineViewLayout {
	private static final int OUTER_MARGIN = 6;
	private static final int MAX_PANEL_WIDTH = 900;
	private static final int MAX_PANEL_HEIGHT = 500;
	private static final int INNER_PADDING = 14;
	private static final int GAP = 8;

	private TimelineViewLayout() {
	}

	public static Layout compute(final int viewportWidth, final int viewportHeight) {
		int safeWidth = Math.max(1, viewportWidth);
		int safeHeight = Math.max(1, viewportHeight);
		int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, safeWidth - OUTER_MARGIN * 2));
		int panelHeight = Math.max(1, Math.min(MAX_PANEL_HEIGHT, safeHeight - OUTER_MARGIN * 2));
		int panelX = (safeWidth - panelWidth) / 2;
		int panelY = (safeHeight - panelHeight) / 2;
		Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);

		int headerHeight = Math.min(56, Math.max(22, panelHeight / 5));
		int footerHeight = Math.min(44, Math.max(22, panelHeight / 5));
		int innerX = panelX + Math.min(INNER_PADDING, Math.max(1, panelWidth / 8));
		int innerWidth = Math.max(1, panelWidth - (innerX - panelX) * 2);
		int contentY = panelY + headerHeight;
		int contentHeight = Math.max(1, panelHeight - headerHeight - footerHeight);
		Rect content = new Rect(innerX, contentY, innerWidth, contentHeight);
		boolean wide = panelWidth >= 600 && contentHeight >= 170;

		Rect route;
		Rect detail;
		if (wide) {
			int routeWidth = Math.clamp(panelWidth * 31 / 100, 210, 280);
			routeWidth = Math.min(routeWidth, Math.max(1, innerWidth - GAP - 1));
			route = new Rect(innerX, contentY, routeWidth, contentHeight);
			detail = new Rect(
				route.right() + GAP,
				contentY,
				Math.max(1, content.right() - route.right() - GAP),
				contentHeight
			);
		} else {
			int routeHeight = Math.min(
				Math.max(46, contentHeight * 34 / 100),
				Math.max(1, contentHeight - GAP - 1)
			);
			route = new Rect(innerX, contentY, innerWidth, routeHeight);
			detail = new Rect(
				innerX,
				route.bottom() + GAP,
				innerWidth,
				Math.max(1, content.bottom() - route.bottom() - GAP)
			);
		}

		int footerY = panel.bottom() - footerHeight;
		int buttonHeight = Math.max(1, Math.min(24, footerHeight - 8));
		int buttonWidth = Math.max(1, Math.min(132, innerWidth));
		Rect backButton = new Rect(
			content.right() - buttonWidth,
			footerY + Math.max(0, (footerHeight - buttonHeight) / 2),
			buttonWidth,
			buttonHeight
		);
		return new Layout(panel, content, route, detail, backButton, wide);
	}

	public record Layout(
		Rect panel,
		Rect content,
		Rect route,
		Rect detail,
		Rect backButton,
		boolean wide
	) {
	}
}
