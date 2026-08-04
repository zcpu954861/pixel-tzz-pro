package io.github.zcpu954861.pixeltzzpro.client.message;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/**
 * Bounded client record of vanilla respawn packets received on the current physical connection.
 *
 * <p>Vanilla replaces {@code LocalPlayer} for both a real respawn and a dimension transfer. The
 * packet's keep-data flag is therefore captured before the replacement and exposed as a monotonic
 * event stream. Each message instance starts at the current sequence, so a plan received after an
 * event cannot accidentally replay that older lifecycle transition.</p>
 */
public final class ClientMessageLifecycleTracker {
	private static final int MAX_EVENTS = 32;
	private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>();
	private static long sequence;

	private ClientMessageLifecycleTracker() {
	}

	public static synchronized void reset() {
		EVENTS.clear();
		sequence = 0L;
	}

	public static synchronized long currentSequence() {
		return sequence;
	}

	public static synchronized void record(
		final Reason reason,
		final Identifier previousDimension,
		final Identifier currentDimension
	) {
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(previousDimension, "previousDimension");
		Objects.requireNonNull(currentDimension, "currentDimension");
		if (sequence == Long.MAX_VALUE) {
			throw new IllegalStateException(
				"client lifecycle sequence exhausted within one physical connection"
			);
		}
		Event event = new Event(
			++sequence,
			reason,
			previousDimension,
			currentDimension
		);
		EVENTS.addLast(event);
		while (EVENTS.size() > MAX_EVENTS) {
			EVENTS.removeFirst();
		}
	}

	public static synchronized List<Event> after(final long observedSequence) {
		if (observedSequence < 0L || observedSequence > sequence) {
			throw new IllegalArgumentException(
				"observedSequence is outside the current connection"
			);
		}
		return EVENTS.stream()
			.filter(event -> event.sequence() > observedSequence)
			.toList();
	}

	public enum Reason {
		RESPAWN,
		DIMENSION_TRANSFER
	}

	public record Event(
		long sequence,
		Reason reason,
		Identifier previousDimension,
		Identifier currentDimension
	) {
		public Event {
			if (sequence <= 0L) {
				throw new IllegalArgumentException("sequence must be positive");
			}
			reason = Objects.requireNonNull(reason, "reason");
			previousDimension = Objects.requireNonNull(
				previousDimension,
				"previousDimension"
			);
			currentDimension = Objects.requireNonNull(
				currentDimension,
				"currentDimension"
			);
		}
	}
}
