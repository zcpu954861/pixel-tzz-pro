package io.github.zcpu954861.pixeltzzpro.server.message;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Pure server-side clock for one recipient's occurrence.
 *
 * <p>The client remains responsible for drawing frames, but dependencies and completion callbacks
 * must use the same effective playback clock. This state therefore separates presentation progress
 * from time spent waiting for a safe screen or paused behind another screen.
 */
public final class MessageOccurrencePlayback {
	private final long scheduledAt;
	private final long actualStart;
	private long lastElapsed;
	private long playbackElapsed;
	private long totalPausedElapsed;
	private long continuousPausedElapsed;
	private long completedAt = -1L;
	private Completion completion = Completion.NONE;

	public MessageOccurrencePlayback(
		final long scheduledAt,
		final long actualStart
	) {
		if (scheduledAt < 0L || actualStart < scheduledAt) {
			throw new IllegalArgumentException(
				"occurrence playback requires 0 <= scheduledAt <= actualStart"
			);
		}
		this.scheduledAt = scheduledAt;
		this.actualStart = actualStart;
		this.lastElapsed = actualStart;
	}

	/**
	 * Advances to an authoritative instance elapsed time.
	 *
	 * @param obscuredAction action selected for the current channel while its surface is obscured
	 * @param visualDuration exact visual duration when all required fields have been captured
	 * @param maxPause maximum continuous pause, or empty for no pause timeout
	 */
	public Step advance(
		final long elapsed,
		final ObscuredAction obscuredAction,
		final OptionalLong visualDuration,
		final OptionalLong maxPause
	) {
		Objects.requireNonNull(obscuredAction, "obscuredAction");
		Objects.requireNonNull(visualDuration, "visualDuration");
		Objects.requireNonNull(maxPause, "maxPause");
		if (elapsed < this.lastElapsed) {
			throw new IllegalArgumentException(
				"occurrence playback elapsed time cannot move backwards"
			);
		}
		if (visualDuration.isPresent() && visualDuration.getAsLong() < 0L) {
			throw new IllegalArgumentException("visualDuration must be non-negative");
		}
		if (maxPause.isPresent() && maxPause.getAsLong() <= 0L) {
			throw new IllegalArgumentException("maxPause must be positive");
		}
		if (complete()) {
			this.lastElapsed = elapsed;
			return Step.completed(this.completedAt, this.completion);
		}

		long previousElapsed = this.lastElapsed;
		long delta = elapsed - previousElapsed;
		this.lastElapsed = elapsed;
		if (obscuredAction == ObscuredAction.FINALIZE) {
			complete(elapsed, Completion.FINALIZED);
			return Step.completed(this.completedAt, this.completion);
		}
		if (obscuredAction == ObscuredAction.CANCEL) {
			complete(elapsed, Completion.CANCELED);
			return Step.completed(this.completedAt, this.completion);
		}
		if (obscuredAction == ObscuredAction.PAUSE) {
			long acceptedPause = delta;
			boolean timedOut = false;
			long timeoutAt = -1L;
			if (maxPause.isPresent()) {
				long remaining = Math.max(
					0L,
					maxPause.getAsLong() - this.continuousPausedElapsed
				);
				if (acceptedPause >= remaining) {
					acceptedPause = remaining;
					timedOut = true;
					timeoutAt = Math.addExact(previousElapsed, remaining);
				}
			}
			this.totalPausedElapsed = Math.addExact(
				this.totalPausedElapsed,
				acceptedPause
			);
			this.continuousPausedElapsed = Math.addExact(
				this.continuousPausedElapsed,
				acceptedPause
			);
			return new Step(
				acceptedPause,
				timedOut,
				timeoutAt,
				OptionalLong.empty(),
				Completion.NONE
			);
		}

		this.continuousPausedElapsed = 0L;
		long previousPlayback = this.playbackElapsed;
		this.playbackElapsed = Math.addExact(this.playbackElapsed, delta);
		if (
			visualDuration.isPresent()
				&& this.playbackElapsed >= visualDuration.getAsLong()
		) {
			long naturalCompletion;
			if (previousPlayback >= visualDuration.getAsLong()) {
				naturalCompletion = Math.addExact(
					Math.addExact(
						this.actualStart,
						this.totalPausedElapsed
					),
					visualDuration.getAsLong()
				);
			} else {
				long remainingAtTickStart =
					visualDuration.getAsLong() - previousPlayback;
				naturalCompletion = Math.addExact(
					previousElapsed,
					remainingAtTickStart
				);
			}
			complete(Math.min(elapsed, naturalCompletion), Completion.NATURAL);
			return Step.completed(this.completedAt, this.completion);
		}
		return Step.progressing();
	}

	/**
	 * Completes a final-chat-only occurrence as soon as its final fields are available.
	 */
	public Step finalizeWithoutAnimation(final long elapsed) {
		if (elapsed < this.lastElapsed) {
			throw new IllegalArgumentException(
				"occurrence playback elapsed time cannot move backwards"
			);
		}
		this.lastElapsed = elapsed;
		if (!complete()) {
			complete(elapsed, Completion.FINALIZED);
		}
		return Step.completed(this.completedAt, this.completion);
	}

	public long scheduledAt() {
		return this.scheduledAt;
	}

	public long actualStart() {
		return this.actualStart;
	}

	public long playbackElapsed() {
		return this.playbackElapsed;
	}

	public long totalPausedElapsed() {
		return this.totalPausedElapsed;
	}

	/**
	 * Start offset used when a deferred occurrence is first sent after earlier pauses.
	 */
	public long projectedStart() {
		return Math.addExact(this.actualStart, this.totalPausedElapsed);
	}

	public boolean complete() {
		return this.completion != Completion.NONE;
	}

	public OptionalLong completedAt() {
		return complete()
			? OptionalLong.of(this.completedAt)
			: OptionalLong.empty();
	}

	public Completion completion() {
		return this.completion;
	}

	private void complete(final long elapsed, final Completion completion) {
		this.completedAt = Math.max(this.actualStart, elapsed);
		this.completion = Objects.requireNonNull(completion, "completion");
	}

	public enum ObscuredAction {
		CONTINUE,
		PAUSE,
		FINALIZE,
		CANCEL
	}

	public enum Completion {
		NONE,
		NATURAL,
		FINALIZED,
		CANCELED
	}

	public record Step(
		long pausedDelta,
		boolean pauseTimedOut,
		long timeoutAt,
		OptionalLong completedAt,
		Completion completion
	) {
		public Step {
			if (pausedDelta < 0L) {
				throw new IllegalArgumentException("pausedDelta must be non-negative");
			}
			if (pauseTimedOut != (timeoutAt >= 0L)) {
				throw new IllegalArgumentException(
					"pause timeout flag and timestamp must agree"
				);
			}
			completedAt = Objects.requireNonNull(completedAt, "completedAt");
			completion = Objects.requireNonNull(completion, "completion");
			if (
				completedAt.isPresent()
					!= (completion != Completion.NONE)
			) {
				throw new IllegalArgumentException(
					"completion timestamp and reason must agree"
				);
			}
		}

		private static Step progressing() {
			return new Step(
				0L,
				false,
				-1L,
				OptionalLong.empty(),
				Completion.NONE
			);
		}

		private static Step completed(
			final long completedAt,
			final Completion completion
		) {
			return new Step(
				0L,
				false,
				-1L,
				OptionalLong.of(completedAt),
				completion
			);
		}
	}
}
