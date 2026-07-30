package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic responsive geometry for the ordinary-player terminal footer.
 *
 * <p>The root page owns a dedicated Exit Terminal control on the left and may expose the
 * low-priority host ownership action on the right. Child pages replace both with one stack-aware
 * Back control. Keeping this calculation client-neutral makes those information-architecture
 * rules executable instead of screenshot-only assumptions.</p>
 */
public final class TerminalFooterLayout {
	public static final int NONE_HEIGHT = 0;
	public static final int NORMAL_HEIGHT = 42;
	public static final int STACKED_HEIGHT = 72;
	public static final int SAFE_INSET = 12;
	public static final int MAX_ROOT_NAVIGATION_ITEMS = 5;
	private static final int BUTTON_HEIGHT = 24;
	private static final int BUTTON_BOTTOM_INSET = 9;
	private static final int BUTTON_ROW_GAP = 6;
	private static final int BUTTON_GAP = 8;
	private static final int NAVIGATION_GAP = 6;
	private static final int NAVIGATION_SIDE_GAP = 12;
	private static final int NAVIGATION_PREFERRED_WIDTH = 88;
	private static final int NAVIGATION_MINIMUM_WIDTH = 58;
	private static final int EXIT_WIDTH = 86;
	private static final int NAVIGATION_WIDTH = 86;
	private static final int HOST_WIDTH = 112;

	private TerminalFooterLayout() {
	}

	public static Layout resolve(
		final int shellX,
		final int shellY,
		final int shellWidth,
		final int shellHeight,
		final boolean hostControl,
		final int stackDepth
	) {
		return resolve(
			shellX,
			shellY,
			shellWidth,
			shellHeight,
			hostControl,
			stackDepth,
			0
		);
	}

	/**
	 * Resolves the fixed terminal chrome together with a data-pack registered root navigation group.
	 *
	 * <p>The centre actions remain definitions from the current page; this class only gives those
	 * actions deterministic shell geometry. When the logical canvas is unusually narrow, the
	 * navigation group moves to its own row instead of squeezing or hiding a registered action.</p>
	 */
	public static Layout resolve(
		final int shellX,
		final int shellY,
		final int shellWidth,
		final int shellHeight,
		final boolean hostControl,
		final int stackDepth,
		final int requestedRootNavigationItems
	) {
		if (stackDepth <= 0) {
			throw new IllegalArgumentException("stackDepth must be positive");
		}
		if (requestedRootNavigationItems < 0) {
			throw new IllegalArgumentException("root navigation item count cannot be negative");
		}
		if (requestedRootNavigationItems > MAX_ROOT_NAVIGATION_ITEMS) {
			throw new IllegalArgumentException(
				"root navigation item count exceeds " + MAX_ROOT_NAVIGATION_ITEMS
			);
		}
		int safeWidth = Math.max(1, shellWidth);
		int safeHeight = Math.max(1, shellHeight);
		int innerX = shellX + SAFE_INSET;
		int innerWidth = Math.max(1, safeWidth - SAFE_INSET * 2);
		int innerRight = innerX + innerWidth;
		boolean showExit = stackDepth == 1;
		boolean showNavigation = stackDepth > 1;
		boolean showHostControl = hostControl && !showNavigation;
		int rootNavigationItems = stackDepth == 1
			? requestedRootNavigationItems
			: 0;
		boolean stackSideControls = showExit
			&& showHostControl
			&& innerWidth < EXIT_WIDTH + BUTTON_GAP + HOST_WIDTH;
		int oneRowNavigationWidth = Math.max(
			0,
			innerWidth
				- (showExit ? Math.min(EXIT_WIDTH, innerWidth) + NAVIGATION_SIDE_GAP : 0)
				- (showHostControl ? Math.min(HOST_WIDTH, innerWidth) + NAVIGATION_SIDE_GAP : 0)
		);
		int minimumNavigationWidth = rootNavigationItems == 0
			? 0
			: rootNavigationItems * NAVIGATION_MINIMUM_WIDTH
				+ (rootNavigationItems - 1) * NAVIGATION_GAP;
		boolean stackRootNavigation = rootNavigationItems > 0
			&& (stackSideControls || oneRowNavigationWidth < minimumNavigationWidth);
		int footerRows = 1
			+ (stackSideControls ? 1 : 0)
			+ (stackRootNavigation ? 1 : 0);
		int footerHeight = NORMAL_HEIGHT + (footerRows - 1) * (BUTTON_HEIGHT + BUTTON_ROW_GAP);
		int buttonY = shellY + safeHeight - BUTTON_BOTTOM_INSET - BUTTON_HEIGHT;
		int exitWidth = showExit ? Math.min(EXIT_WIDTH, innerWidth) : 0;
		int hostWidth = showHostControl ? Math.min(HOST_WIDTH, innerWidth) : 0;
		Optional<Rect> exit = showExit
			? Optional.of(
				new Rect(
					innerX,
					stackSideControls
						? buttonY - BUTTON_ROW_GAP - BUTTON_HEIGHT
						: buttonY,
					exitWidth,
					BUTTON_HEIGHT
				)
			)
			: Optional.empty();
		Optional<Rect> navigation = showNavigation
			? Optional.of(
				new Rect(
					shellX + safeWidth
						- SAFE_INSET
						- Math.min(NAVIGATION_WIDTH, innerWidth),
					buttonY,
					Math.min(NAVIGATION_WIDTH, innerWidth),
					BUTTON_HEIGHT
				)
			)
			: Optional.empty();
		Optional<Rect> host = showHostControl
			? Optional.of(
				new Rect(
					innerRight - hostWidth,
					buttonY,
					hostWidth,
					BUTTON_HEIGHT
				)
			)
			: Optional.empty();
		int navigationY = stackRootNavigation
			? buttonY
				- (stackSideControls ? 2 : 1) * (BUTTON_HEIGHT + BUTTON_ROW_GAP)
			: buttonY;
		int navigationLeft = stackRootNavigation
			? innerX
			: exit.map(Rect::right).orElse(innerX)
				+ (showExit ? NAVIGATION_SIDE_GAP : 0);
		int navigationRight = stackRootNavigation
			? innerRight
			: host.map(Rect::x).orElse(innerRight)
				- (showHostControl ? NAVIGATION_SIDE_GAP : 0);
		List<Rect> rootNavigation = evenlySpacedNavigation(
			navigationLeft,
			navigationRight,
			navigationY,
			rootNavigationItems
		);
		return new Layout(
			footerHeight,
			exit,
			navigation,
			host,
			rootNavigation
		);
	}

	private static List<Rect> evenlySpacedNavigation(
		final int left,
		final int right,
		final int y,
		final int count
	) {
		if (count <= 0 || right <= left) {
			return List.of();
		}
		int available = right - left;
		int gap = count <= 1
			? 0
			: Math.min(NAVIGATION_GAP, Math.max(0, (available - count) / (count - 1)));
		int usable = Math.max(0, available - gap * (count - 1));
		int width = Math.min(NAVIGATION_PREFERRED_WIDTH, usable / count);
		int groupWidth = width * count + gap * (count - 1);
		int x = left + Math.max(0, available - groupWidth) / 2;
		List<Rect> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			result.add(new Rect(x, y, width, BUTTON_HEIGHT));
			x += width + gap;
		}
		return List.copyOf(result);
	}

	public record Layout(
		int footerHeight,
		Optional<Rect> exit,
		Optional<Rect> navigation,
		Optional<Rect> hostControl,
		List<Rect> rootNavigation
	) {
		public Layout {
			rootNavigation = List.copyOf(rootNavigation);
		}
	}
}
