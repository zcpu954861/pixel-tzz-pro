package io.github.zcpu954861.pixeltzzpro.network;

import java.util.Objects;

/** Stable, closed vocabulary for the isolated V3C host-development preview namespace. */
public final class HudPreviewContract {
	private HudPreviewContract() {
	}

	public static boolean scenarioAllowed(final Surface surface, final Scenario scenario) {
		Objects.requireNonNull(surface, "surface");
		Objects.requireNonNull(scenario, "scenario");
		return switch (surface) {
			case HUD -> !scenario.countdown();
			case COUNTDOWN -> scenario.countdown();
		};
	}

	public static <E extends Enum<E>> E byOrdinal(
		final Class<E> type,
		final int ordinal,
		final String label
	) {
		E[] values = Objects.requireNonNull(type, "type").getEnumConstants();
		if (ordinal < 0 || ordinal >= values.length) {
			throw new IllegalArgumentException("unknown " + label + " " + ordinal);
		}
		return values[ordinal];
	}

	public enum Action {
		OPEN,
		REFRESH,
		CLOSE
	}

	public enum Surface {
		HUD,
		COUNTDOWN
	}

	public enum Scenario {
		SETUP(false),
		TASK_RUNNING(false),
		TASK_PAUSED(false),
		INTERMISSION(false),
		COUNTDOWN_START(true),
		COUNTDOWN_KEY_SECOND(true),
		COUNTDOWN_PAUSED(true),
		COUNTDOWN_FINAL_TICK(true);

		private final boolean countdown;

		Scenario(final boolean countdown) {
			this.countdown = countdown;
		}

		public boolean countdown() {
			return this.countdown;
		}
	}

	public enum Identity {
		HOST,
		PARTICIPANT,
		OBSERVER
	}

	public enum Mode {
		NORMAL,
		COMPACT,
		SUMMARY
	}

	/** Fixed stress presets; no caller-supplied text, selector, path or value crosses the wire. */
	public enum Boundary {
		NORMAL,
		NARROW,
		SHORT,
		LONG_TEXT,
		MISSING,
		STALE,
		LIST_LIMIT,
		SAFE_ZONE
	}

	public enum ClearReason {
		CLOSED,
		EXPIRED,
		REVOKED,
		REPLACED,
		UNAVAILABLE
	}
}
