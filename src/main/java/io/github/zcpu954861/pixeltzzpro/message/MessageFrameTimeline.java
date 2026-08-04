package io.github.zcpu954861.pixeltzzpro.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Typewriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;

/**
 * Immutable, frame-rate-independent timing function for one resolved V3B text node.
 *
 * <p>The caller supplies only the final authorized component, the source-free network effect and
 * duration, and the grapheme span owned by each resolved segment. A frame is calculated directly
 * from elapsed nanoseconds, so low FPS seeks to the correct state instead of replaying missed
 * character steps.
 */
public final class MessageFrameTimeline {
	public static final int MAX_GRAPHEMES = 16_384;
	public static final int MAX_SEGMENTS = MessagePlaybackPlan.MAX_SEGMENTS_PER_NODE;
	private static final double NANOS_PER_SECOND = 1_000_000_000.0D;

	private MessageFrameTimeline() {
	}

	/**
	 * Compiles one bounded timeline. Segment spans must cover the final component exactly and stay
	 * in final-text order.
	 */
	public static Timeline compile(
		final Component finalComponent,
		final ResolvedEffect effect,
		final ResolvedDuration duration,
		final List<SegmentSpan> segments
	) {
		Objects.requireNonNull(finalComponent, "finalComponent");
		Objects.requireNonNull(effect, "effect");
		Objects.requireNonNull(duration, "duration");
		Objects.requireNonNull(segments, "segments");
		if (segments.isEmpty() || segments.size() > MAX_SEGMENTS) {
			throw new IllegalArgumentException(
				"segment spans must contain 1.." + MAX_SEGMENTS + " entries"
			);
		}

		StyledTypewriter.StyledText styled = StyledTypewriter.flatten(finalComponent.copy());
		int graphemeCount = styled.graphemes().size();
		if (graphemeCount > MAX_GRAPHEMES) {
			throw new IllegalArgumentException(
				"final component exceeds " + MAX_GRAPHEMES + " graphemes"
			);
		}
		int covered = 0;
		long pauseNanos = 0L;
		for (SegmentSpan segment : segments) {
			Objects.requireNonNull(segment, "segment");
			covered = Math.addExact(covered, segment.graphemeCount());
			pauseNanos = safeAdd(pauseNanos, segment.timing().pauseBeforeNanos());
			pauseNanos = safeAdd(pauseNanos, segment.timing().pauseAfterNanos());
		}
		if (covered != graphemeCount) {
			throw new IllegalArgumentException(
				"segment grapheme spans cover "
					+ covered
					+ " graphemes but final component contains "
					+ graphemeCount
			);
		}

		long characterInterval = effectiveCharacterInterval(
			graphemeCount,
			pauseNanos,
			effect,
			duration
		);
		List<Long> revealStarts = new ArrayList<>(graphemeCount);
		List<Long> revealTimes = new ArrayList<>(graphemeCount);
		List<SegmentWindow> windows = new ArrayList<>(segments.size());
		long cursor = 0L;
		int firstGrapheme = 0;
		for (int index = 0; index < segments.size(); index++) {
			SegmentSpan segment = segments.get(index);
			long segmentStart = cursor;
			cursor = safeAdd(cursor, segment.timing().pauseBeforeNanos());
			if (characterInterval == 0L) {
				for (int offset = 0; offset < segment.graphemeCount(); offset++) {
					revealStarts.add(cursor);
					revealTimes.add(cursor);
				}
			} else {
				for (int offset = 0; offset < segment.graphemeCount(); offset++) {
					revealStarts.add(cursor);
					cursor = safeAdd(cursor, characterInterval);
					revealTimes.add(cursor);
				}
			}
			cursor = safeAdd(cursor, segment.timing().pauseAfterNanos());
			windows.add(
				new SegmentWindow(
					index,
					firstGrapheme,
					Math.addExact(firstGrapheme, segment.graphemeCount()),
					segmentStart,
					cursor,
					segment.timing().emphasis()
				)
			);
			firstGrapheme = Math.addExact(firstGrapheme, segment.graphemeCount());
		}
		if (cursor > MessagePlaybackPlan.MAX_TIMELINE_NANOS) {
			throw new IllegalArgumentException("resolved reveal timeline exceeds its network horizon");
		}

		long colorRestoreCompleteAt = colorRestoreCompleteAt(
			revealTimes,
			characterInterval,
			effect
		);
		long completionAt = Math.max(
			safeAdd(cursor, duration.holdNanos()),
			colorRestoreCompleteAt
		);
		if (duration.mode() == MessagePlaybackPlan.DurationMode.FIXED_TOTAL) {
			completionAt = Math.max(completionAt, duration.totalNanos().orElseThrow());
		}
		if (effect.fade().isPresent()) {
			MessagePlaybackPlan.Fade fade = effect.fade().orElseThrow();
			long minimumFadeLifecycle = safeAdd(
				safeAdd(fade.enterNanos(), fade.holdNanos()),
				fade.exitNanos()
			);
			// The exit belongs inside the registered hold whenever there is room. If a node has
			// no hold, extend it just enough to make the fade-out observable instead of clearing
			// the vanilla surface on the same frame that the final grapheme appears.
			completionAt = Math.max(completionAt, minimumFadeLifecycle);
			completionAt = Math.max(completionAt, safeAdd(cursor, fade.exitNanos()));
		}
		if (completionAt > MessagePlaybackPlan.MAX_TIMELINE_NANOS) {
			throw new IllegalArgumentException(
				"resolved text completion exceeds its network horizon"
			);
		}
		return new Timeline(
			styled,
			effect,
			duration,
			segments,
			windows,
			revealStarts,
			revealTimes,
			characterInterval,
			cursor,
			completionAt
		);
	}

	private static long colorRestoreCompleteAt(
		final List<Long> revealTimes,
		final long characterInterval,
		final ResolvedEffect effect
	) {
		Optional<Typewriter> typewriter = effect.typewriter();
		if (
			typewriter.isEmpty()
				|| typewriter.orElseThrow().freshColor().isEmpty()
				|| typewriter.orElseThrow().restoreAfterCharacters() <= 0
				|| revealTimes.isEmpty()
		) {
			return 0L;
		}
		Typewriter resolved = typewriter.orElseThrow();
		return safeAdd(
			revealTimes.getLast(),
			Math.multiplyExact(
				characterInterval,
				(long)resolved.restoreAfterCharacters()
			)
		);
	}

	private static long effectiveCharacterInterval(
		final int graphemeCount,
		final long pauseNanos,
		final ResolvedEffect effect,
		final ResolvedDuration duration
	) {
		Optional<Typewriter> typewriter = effect.typewriter();
		Optional<MessagePlaybackPlan.Scramble> scramble = effect.scramble();
		if (typewriter.isEmpty() && scramble.isEmpty() || graphemeCount == 0) {
			return 0L;
		}
		long registered = typewriter
			.map(Typewriter::characterIntervalNanos)
			.orElseGet(() -> scramble.orElseThrow().characterIntervalNanos());
		if (duration.mode() == MessagePlaybackPlan.DurationMode.CONTENT_DRIVEN) {
			return registered;
		}

		long fixed = duration.totalNanos().orElseThrow();
		long unavailable = safeAdd(pauseNanos, duration.holdNanos());
		long typingBudget = fixed > unavailable ? fixed - unavailable : 0L;
		long desired = typingBudget == 0L
			? 1L
			: Math.max(1L, Math.round(typingBudget / (double)graphemeCount));

		long fastestInterval = duration.maximumSpeed()
			.map(speed -> intervalForMaximumSpeed(speed))
			.orElse(1L);
		long slowestInterval = duration.minimumSpeed()
			.map(speed -> intervalForMinimumSpeed(speed))
			.orElse(MessagePlaybackPlan.MAX_TIMELINE_NANOS);
		if (fastestInterval > slowestInterval) {
			throw new IllegalArgumentException("fixed-duration speed bounds have no overlap");
		}
		return Math.clamp(desired, fastestInterval, slowestInterval);
	}

	private static long intervalForMaximumSpeed(final float charactersPerSecond) {
		return Math.max(1L, (long)Math.ceil(NANOS_PER_SECOND / charactersPerSecond));
	}

	private static long intervalForMinimumSpeed(final float charactersPerSecond) {
		return Math.max(1L, (long)Math.floor(NANOS_PER_SECOND / charactersPerSecond));
	}

	private static long safeAdd(final long left, final long right) {
		return Math.addExact(left, right);
	}

	public record SegmentSpan(int graphemeCount, SegmentTiming timing) {
		public SegmentSpan {
			if (graphemeCount < 0 || graphemeCount > MAX_GRAPHEMES) {
				throw new IllegalArgumentException(
					"segment grapheme count must remain within 0.." + MAX_GRAPHEMES
				);
			}
			timing = Objects.requireNonNull(timing, "timing");
		}

		public static SegmentSpan plain(final int graphemeCount) {
			return new SegmentSpan(graphemeCount, SegmentTiming.none());
		}
	}

	public record SegmentWindow(
		int index,
		int firstGrapheme,
		int endGrapheme,
		long startNanos,
		long endNanos,
		boolean emphasis
	) {
		public SegmentWindow {
			if (
				index < 0
					|| firstGrapheme < 0
					|| endGrapheme < firstGrapheme
					|| startNanos < 0L
					|| endNanos < startNanos
			) {
				throw new IllegalArgumentException("segment window is invalid");
			}
		}
	}

	public enum Phase {
		REVEALING,
		HOLDING,
		COMPLETE
	}

	public record Frame(
		long elapsedNanos,
		int visibleGraphemes,
		double partialGraphemeProgress,
		boolean cursorVisible,
		boolean revealComplete,
		boolean holding,
		boolean complete,
		Phase phase,
		OptionalInt activeSegment
	) {
		public Frame {
			phase = Objects.requireNonNull(phase, "phase");
			activeSegment = Objects.requireNonNull(activeSegment, "activeSegment");
			if (
				elapsedNanos < 0L
					|| visibleGraphemes < 0
					|| !Double.isFinite(partialGraphemeProgress)
					|| partialGraphemeProgress < 0.0D
					|| partialGraphemeProgress > 1.0D
					|| holding != (phase == Phase.HOLDING)
					|| complete != (phase == Phase.COMPLETE)
			) {
				throw new IllegalArgumentException("message frame state is inconsistent");
			}
		}
	}

	public record Timeline(
		StyledTypewriter.StyledText styledText,
		ResolvedEffect effect,
		ResolvedDuration duration,
		List<SegmentSpan> segments,
		List<SegmentWindow> segmentWindows,
		List<Long> revealStartsNanos,
		List<Long> revealTimesNanos,
		long characterIntervalNanos,
		long revealCompleteAtNanos,
		long completeAtNanos
	) {
		public Timeline {
			styledText = Objects.requireNonNull(styledText, "styledText");
			effect = Objects.requireNonNull(effect, "effect");
			duration = Objects.requireNonNull(duration, "duration");
			segments = List.copyOf(segments);
			segmentWindows = List.copyOf(segmentWindows);
			revealStartsNanos = List.copyOf(revealStartsNanos);
			revealTimesNanos = List.copyOf(revealTimesNanos);
			if (
				revealStartsNanos.size() != styledText.graphemes().size()
					|| revealTimesNanos.size() != styledText.graphemes().size()
					|| characterIntervalNanos < 0L
					|| revealCompleteAtNanos < 0L
					|| completeAtNanos < revealCompleteAtNanos
			) {
				throw new IllegalArgumentException("compiled message timeline is inconsistent");
			}
		}

		/**
		 * Returns the exact state for any non-negative elapsed time.
		 */
		public Frame frameAt(final long elapsedNanos) {
			if (elapsedNanos < 0L) {
				throw new IllegalArgumentException("elapsedNanos cannot be negative");
			}
			int visible = upperBound(this.revealTimesNanos, elapsedNanos);
			boolean revealComplete = elapsedNanos >= this.revealCompleteAtNanos;
			boolean complete = elapsedNanos >= this.completeAtNanos;
			boolean holding = revealComplete && !complete;
			Phase phase = complete
				? Phase.COMPLETE
				: holding ? Phase.HOLDING : Phase.REVEALING;
			double partial = partialProgress(elapsedNanos, visible);
			boolean cursorVisible = cursorVisible(elapsedNanos, revealComplete, visible);
			return new Frame(
				elapsedNanos,
				visible,
				partial,
				cursorVisible,
				revealComplete,
				holding,
				complete,
				phase,
				activeSegment(elapsedNanos, complete)
			);
		}

		public long effectiveHoldNanos() {
			return this.completeAtNanos - this.revealCompleteAtNanos;
		}

		/**
		 * Returns the virtual cursor advance that restores the trailing fresh-color window after
		 * the final real grapheme appears. The value is bounded to the registered restore distance;
		 * an instantaneous reveal restores immediately because it has no observable interval.
		 */
		public double colorRestoreAdvanceAt(final long elapsedNanos) {
			if (elapsedNanos < 0L) {
				throw new IllegalArgumentException("elapsedNanos cannot be negative");
			}
			Optional<Typewriter> typewriter = this.effect.typewriter();
			if (
				typewriter.isEmpty()
					|| typewriter.orElseThrow().freshColor().isEmpty()
					|| typewriter.orElseThrow().restoreAfterCharacters() <= 0
					|| this.revealTimesNanos.isEmpty()
			) {
				return 0.0D;
			}
			int restoreDistance = typewriter.orElseThrow().restoreAfterCharacters();
			long lastReveal = this.revealTimesNanos.getLast();
			if (this.characterIntervalNanos == 0L) {
				return elapsedNanos >= lastReveal ? restoreDistance : 0.0D;
			}
			return Math.clamp(
				(elapsedNanos - lastReveal) / (double)this.characterIntervalNanos,
				0.0D,
				restoreDistance
			);
		}

		private double partialProgress(final long elapsedNanos, final int visible) {
			if (visible >= this.revealTimesNanos.size() || this.characterIntervalNanos == 0L) {
				return 0.0D;
			}
			long start = this.revealStartsNanos.get(visible);
			long end = this.revealTimesNanos.get(visible);
			if (elapsedNanos <= start || end <= start) {
				return 0.0D;
			}
			return Math.clamp(
				(elapsedNanos - start) / (double)(end - start),
				0.0D,
				1.0D
			);
		}

		private boolean cursorVisible(
			final long elapsedNanos,
			final boolean revealComplete,
			final int visibleGraphemes
		) {
			if (
				revealComplete
					|| visibleGraphemes >= this.styledText.graphemes().size()
					|| this.effect.typewriter().isEmpty()
			) {
				return false;
			}
			MessagePlaybackPlan.Cursor cursor = this.effect.typewriter().orElseThrow().cursor();
			if (!cursor.enabled()) {
				return false;
			}
			if (!cursor.blink()) {
				return true;
			}
			long period = cursor.blinkPeriodNanos();
			long visiblePart = Math.max(1L, period / 2L);
			return elapsedNanos % period < visiblePart;
		}

		private OptionalInt activeSegment(
			final long elapsedNanos,
			final boolean complete
		) {
			if (complete) {
				return OptionalInt.empty();
			}
			for (SegmentWindow window : this.segmentWindows) {
				if (elapsedNanos >= window.startNanos() && elapsedNanos < window.endNanos()) {
					return OptionalInt.of(window.index());
				}
			}
			return OptionalInt.empty();
		}

		private static int upperBound(final List<Long> sorted, final long value) {
			int low = 0;
			int high = sorted.size();
			while (low < high) {
				int middle = low + (high - low) / 2;
				if (sorted.get(middle) <= value) {
					low = middle + 1;
				} else {
					high = middle;
				}
			}
			return low;
		}
	}
}
