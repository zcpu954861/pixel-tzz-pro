package io.github.zcpu954861.pixeltzzpro.server.message;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Bounds the connection-local capability/asset handshake used by final-only delivery.
 *
 * <p>The server prefers the ordinary V3B projection while the new physical connection is still
 * reporting capabilities and assets. A client that never reports must not keep an authoritative
 * message instance, its completion callbacks, or other recipients blocked forever, so the same
 * delivery eventually falls back to the already-authorized vanilla static result.</p>
 */
public final class MessageFinalOnlyHandshakePolicy {
	/** Five seconds at the vanilla 20 TPS rate. */
	public static final long DEFAULT_GRACE_TICKS = 100L;

	public enum Directive {
		WAIT,
		DELIVER_DYNAMIC,
		DELIVER_STATIC
	}

	public record Decision(Directive directive, OptionalLong deadlineGameTick) {
		public Decision {
			directive = Objects.requireNonNull(directive, "directive");
			deadlineGameTick = Objects.requireNonNull(deadlineGameTick, "deadlineGameTick");
			if ((directive == Directive.WAIT) != deadlineGameTick.isPresent()) {
				throw new IllegalArgumentException(
					"only a waiting final-only handshake may retain a deadline"
				);
			}
		}
	}

	private MessageFinalOnlyHandshakePolicy() {}

	public static Decision evaluate(
		final boolean handshakePending,
		final long currentGameTick,
		final OptionalLong existingDeadline
	) {
		return evaluate(
			handshakePending,
			currentGameTick,
			existingDeadline,
			DEFAULT_GRACE_TICKS
		);
	}

	static Decision evaluate(
		final boolean handshakePending,
		final long currentGameTick,
		final OptionalLong existingDeadline,
		final long graceTicks
	) {
		Objects.requireNonNull(existingDeadline, "existingDeadline");
		if (currentGameTick < 0L) {
			throw new IllegalArgumentException("current game tick cannot be negative");
		}
		if (graceTicks <= 0L) {
			throw new IllegalArgumentException("final-only handshake grace must be positive");
		}
		if (!handshakePending) {
			return new Decision(Directive.DELIVER_DYNAMIC, OptionalLong.empty());
		}
		long deadline = existingDeadline.orElseGet(() ->
			Math.addExact(currentGameTick, graceTicks)
		);
		return currentGameTick < deadline
			? new Decision(Directive.WAIT, OptionalLong.of(deadline))
			: new Decision(Directive.DELIVER_STATIC, OptionalLong.empty());
	}
}
