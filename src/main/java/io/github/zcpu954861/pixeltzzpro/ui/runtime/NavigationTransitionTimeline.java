package io.github.zcpu954861.pixeltzzpro.ui.runtime;

/**
 * Pure timing and geometry for hierarchical screen navigation.
 *
 * <p>The caller owns rendering and input locking. Keeping the timeline client-neutral makes
 * direction, end states and the vanilla screen-effect accessibility scale independently testable.</p>
 */
public final class NavigationTransitionTimeline {
	private static final int REDUCED_DURATION_MILLIS = 80;

	private NavigationTransitionTimeline() {
	}

	public static Sample sample(
		final Motion motion,
		final long elapsedMilliseconds,
		final int viewportWidth,
		final double screenEffectScale
	) {
		double strength = Math.clamp(screenEffectScale, 0.0, 1.0);
		int duration = durationMilliseconds(motion, strength);
		double progress = duration == 0
			? 1.0
			: Math.clamp((double)Math.max(0L, elapsedMilliseconds) / duration, 0.0, 1.0);
		double eased = motion.entering() ? easeOutCubic(progress) : easeInCubic(progress);
		double enterAmount = motion.entering() ? 1.0 - eased : eased;
		double horizontalTravel = Math.clamp(viewportWidth * 0.06, 12.0, 30.0) * strength;
		float translateX = switch (motion) {
			case PUSH_ENTER -> (float)(horizontalTravel * enterAmount);
			case PUSH_EXIT -> (float)(-horizontalTravel * enterAmount);
			case POP_ENTER -> (float)(-horizontalTravel * enterAmount);
			case POP_EXIT -> (float)(horizontalTravel * enterAmount);
			default -> 0.0F;
		};
		float translateY = switch (motion) {
			case ROOT_ENTER -> (float)(8.0 * strength * enterAmount);
			case ROOT_EXIT -> (float)(4.0 * strength * enterAmount);
			case REPLACE_ENTER -> (float)(3.0 * strength * enterAmount);
			case REPLACE_EXIT -> (float)(-2.0 * strength * enterAmount);
			default -> 0.0F;
		};
		float scale = switch (motion) {
			case ROOT_ENTER -> (float)(1.0 - 0.025 * strength * enterAmount);
			case ROOT_EXIT -> (float)(1.0 - 0.018 * strength * enterAmount);
			default -> 1.0F;
		};
		int veilAlpha = (int)Math.round(
			(motion.entering() ? 1.0 - eased : eased)
				* (motion.root() ? 54.0 : motion.replacing() ? 34.0 : 42.0)
		);
		return new Sample(
			translateX,
			translateY,
			scale,
			Math.clamp(veilAlpha, 0, 255),
			progress >= 1.0
		);
	}

	public static int durationMilliseconds(final Motion motion, final double screenEffectScale) {
		double strength = Math.clamp(screenEffectScale, 0.0, 1.0);
		return (int)Math.round(
			REDUCED_DURATION_MILLIS
				+ (motion.fullDurationMilliseconds() - REDUCED_DURATION_MILLIS) * strength
		);
	}

	private static double easeOutCubic(final double progress) {
		double remaining = 1.0 - progress;
		return 1.0 - remaining * remaining * remaining;
	}

	private static double easeInCubic(final double progress) {
		return progress * progress * progress;
	}

	public enum Motion {
		ROOT_ENTER(220, true, true, false),
		ROOT_EXIT(150, false, true, false),
		PUSH_ENTER(170, true, false, false),
		PUSH_EXIT(110, false, false, false),
		POP_ENTER(170, true, false, false),
		POP_EXIT(110, false, false, false),
		REPLACE_ENTER(130, true, false, true),
		REPLACE_EXIT(90, false, false, true);

		private final int fullDurationMilliseconds;
		private final boolean entering;
		private final boolean root;
		private final boolean replacing;

		Motion(
			final int fullDurationMilliseconds,
			final boolean entering,
			final boolean root,
			final boolean replacing
		) {
			this.fullDurationMilliseconds = fullDurationMilliseconds;
			this.entering = entering;
			this.root = root;
			this.replacing = replacing;
		}

		public int fullDurationMilliseconds() {
			return this.fullDurationMilliseconds;
		}

		public boolean entering() {
			return this.entering;
		}

		private boolean root() {
			return this.root;
		}

		private boolean replacing() {
			return this.replacing;
		}
	}

	public record Sample(
		float translateX,
		float translateY,
		float scale,
		int veilAlpha,
		boolean finished
	) {
	}
}
