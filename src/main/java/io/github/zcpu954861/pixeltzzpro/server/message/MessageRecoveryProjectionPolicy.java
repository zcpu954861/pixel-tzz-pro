package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import java.util.Objects;
import java.util.Optional;

/** Pure clock and reconnect projection decisions used by cold-start message recovery. */
public final class MessageRecoveryProjectionPolicy {
	private MessageRecoveryProjectionPolicy() {
	}

	/**
	 * Converts the original absolute game-tick TTL into the current presentation-clock domain.
	 * The deadline is never replaced with a fresh full TTL.
	 */
	public static long pendingPresentationExpiry(
		final long expiresAtGameTick,
		final ClockSample recoveryClock
	) {
		Objects.requireNonNull(recoveryClock, "recoveryClock");
		if (
			expiresAtGameTick < 0L
				|| expiresAtGameTick > Long.MAX_VALUE / TimeSpan.NANOS_PER_TICK
		) {
			throw new IllegalArgumentException("offline delivery deadline is outside its safe range");
		}
		long remainingTicks = Math.max(0L, expiresAtGameTick - recoveryClock.gameTick());
		return Math.addExact(
			recoveryClock.presentationNanos(),
			Math.multiplyExact(remainingTicks, TimeSpan.NANOS_PER_TICK)
		);
	}

	/** Recreates the pre-restart visual anchor so clients seek instead of restarting playback. */
	public static long recoveredVisualAnchor(
		final long recoveredClockNanos,
		final long accumulatedElapsedNanos
	) {
		if (recoveredClockNanos < 0L || accumulatedElapsedNanos < 0L) {
			throw new IllegalArgumentException("recovered message clocks cannot be negative");
		}
		long anchor = Math.subtractExact(recoveredClockNanos, accumulatedElapsedNanos);
		if (anchor < 0L) {
			throw new IllegalArgumentException(
				"recovered message elapsed time exceeds its clock anchor"
			);
		}
		return anchor;
	}

	/** Control packet that must follow a freshly resent recovered plan, if any. */
	public static Optional<MessageControlS2CPayload.Action> reconnectControl(
		final InstanceStatus status
	) {
		Objects.requireNonNull(status, "status");
		return switch (status) {
			case PAUSED -> Optional.of(MessageControlS2CPayload.Action.PAUSE);
			case COMPLETING -> Optional.of(MessageControlS2CPayload.Action.FINALIZE);
			case CANCELLING -> Optional.of(MessageControlS2CPayload.Action.CANCEL);
			case ACTIVE, COMPLETED, CANCELLED -> Optional.empty();
		};
	}
}
