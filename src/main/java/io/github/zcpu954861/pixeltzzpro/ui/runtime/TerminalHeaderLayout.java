package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;

/**
 * Responsive geometry for the persistent ordinary-player terminal header.
 *
 * <p>The header is intentionally outside the data-pack page viewport. It is the one stable place
 * for product identity, the viewer's real identity, the current page label, and the shared
 * synchronization badge. Data-pack pages therefore do not need to repeat shell information or
 * spend their own vertical space on it.</p>
 */
public final class TerminalHeaderLayout {
	public static final int HEIGHT = 52;
	public static final int SAFE_INSET = 12;
	public static final int AVATAR_SIZE = 24;
	public static final int COMPACT_THRESHOLD = 500;
	private static final int GAP = 8;
	private static final int NORMAL_BRAND_WIDTH = 116;
	private static final int COMPACT_BRAND_WIDTH = 78;
	private static final int NORMAL_IDENTITY_WIDTH = 166;
	private static final int COMPACT_IDENTITY_WIDTH = 132;
	private static final int NORMAL_PAGE_WIDTH = 168;
	private static final int COMPACT_PAGE_WIDTH = 108;

	private TerminalHeaderLayout() {
	}

	public static Layout resolve(
		final int shellX,
		final int shellY,
		final int shellWidth,
		final int syncBadgeWidth
	) {
		int safeWidth = Math.max(1, shellWidth);
		int badgeWidth = Math.max(1, Math.min(syncBadgeWidth, safeWidth));
		boolean compact = safeWidth < COMPACT_THRESHOLD;
		int brandWidth = compact ? COMPACT_BRAND_WIDTH : NORMAL_BRAND_WIDTH;
		int identityWidth = compact ? COMPACT_IDENTITY_WIDTH : NORMAL_IDENTITY_WIDTH;
		int contentLeft = shellX + SAFE_INSET;
		int contentRight = shellX + safeWidth - SAFE_INSET;
		int syncX = Math.max(contentLeft, contentRight - badgeWidth);
		Rect sync = new Rect(syncX, shellY + 16, Math.max(1, contentRight - syncX), 17);

		int brandRight = Math.min(syncX - GAP, contentLeft + brandWidth);
		Rect brand = new Rect(
			contentLeft,
			shellY + 8,
			Math.max(1, brandRight - contentLeft),
			HEIGHT - 16
		);

		int identityX = Math.min(syncX - GAP, brandRight + GAP);
		int requestedPageWidth = safeWidth >= COMPACT_THRESHOLD
			? NORMAL_PAGE_WIDTH
			: safeWidth >= 360
				? COMPACT_PAGE_WIDTH
				: safeWidth >= 280 ? 60 : 1;
		int identityLimit = Math.max(
			identityX + 1,
			syncX - GAP - requestedPageWidth - GAP
		);
		int identityRight = Math.min(
			Math.min(syncX - GAP, identityX + identityWidth),
			identityLimit
		);
		Rect avatar = new Rect(
			identityX,
			shellY + (HEIGHT - AVATAR_SIZE) / 2,
			Math.max(1, Math.min(AVATAR_SIZE, identityRight - identityX)),
			AVATAR_SIZE
		);
		int identityTextX = Math.min(identityRight, avatar.x() + avatar.width() + 6);
		Rect identity = new Rect(
			identityTextX,
			shellY + 8,
			Math.max(1, identityRight - identityTextX),
			HEIGHT - 16
		);

		int pageAreaX = Math.min(syncX - GAP, identityRight + GAP);
		int pageAreaWidth = Math.max(1, syncX - GAP - pageAreaX);
		int pageWidth = Math.max(1, Math.min(requestedPageWidth, pageAreaWidth));
		int pageX = pageAreaX + Math.max(0, (pageAreaWidth - pageWidth) / 2);
		Rect page = new Rect(
			pageX,
			shellY + 8,
			pageWidth,
			HEIGHT - 16
		);
		return new Layout(brand, avatar, identity, page, sync, compact);
	}

	public record Layout(
		Rect brand,
		Rect avatar,
		Rect identity,
		Rect page,
		Rect sync,
		boolean compact
	) {
	}
}
