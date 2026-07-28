package io.github.zcpu954861.pixeltzzpro.server;

/**
 * Small pure regression check for the TAB visibility and stable role ordering contract.
 */
public final class TabListDisplaySelfCheck {
	private TabListDisplaySelfCheck() {
	}

	public static void main(final String[] args) {
		check(!TabListDisplay.shouldExposeRole(false, 0L, false), "fresh runner hidden");
		check(TabListDisplay.shouldExposeRole(false, 1L, false), "initialized runner visible");
		check(TabListDisplay.shouldExposeRole(false, 0L, true), "explicit role visible");
		check(TabListDisplay.shouldExposeRole(true, 0L, false), "spectator visible");

		int host = TabListDisplay.sortOrder(true, false, false, false);
		int hunter = TabListDisplay.sortOrder(false, true, false, true);
		int runner = TabListDisplay.sortOrder(false, false, false, true);
		int pending = TabListDisplay.sortOrder(false, false, false, false);
		int spectator = TabListDisplay.sortOrder(false, false, true, true);
		check(host > hunter, "vanilla descending order places host before hunter");
		check(hunter > runner, "vanilla descending order places hunter before runner");
		check(runner > pending, "vanilla descending order places initialized runner before uninitialized players");
		check(pending > spectator, "vanilla descending order places uninitialized players before spectator");
		System.out.println("TabListDisplaySelfCheck PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
