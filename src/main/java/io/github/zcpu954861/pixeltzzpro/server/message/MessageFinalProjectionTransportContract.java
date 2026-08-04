package io.github.zcpu954861.pixeltzzpro.server.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure transport state machine for one recipient's final message projection.
 *
 * <p>A complete plan sent on a fresh connection already contains every newly authorized node.
 * An established connection may append only while its old nodes remain authorized; the runtime
 * uses an atomic REFRESH (or a text-free CANCEL for zero visible nodes) before this contract when
 * current authorization retracts an old node or field slot.</p>
 */
public final class MessageFinalProjectionTransportContract {
	private MessageFinalProjectionTransportContract() {
	}

	public enum Operation {
		PLAN,
		APPEND,
		LOCKED_SNAPSHOT,
		FINALIZE
	}

	public enum DeliveryResult {
		SENT,
		RETRYABLE_FAILURE
	}

	/**
	 * Starts a final-delivery transaction for the current physical connection.
	 *
	 * @param establishedOnConnection whether this connection already accepted the instance plan
	 * @param additionsPending whether finalization authorized nodes absent from the established plan
	 * @param lockedSnapshotAvailable whether the server has prior locked values to recover
	 */
	public static State begin(
		final boolean establishedOnConnection,
		final boolean additionsPending,
		final boolean lockedSnapshotAvailable
	) {
		if (establishedOnConnection) {
			return new State(
				true,
				false,
				additionsPending,
				false,
				true,
				false
			);
		}
		/* The fresh full plan must already include every final addition. */
		return new State(
			false,
			true,
			false,
			lockedSnapshotAvailable,
			true,
			false
		);
	}

	public record State(
		boolean establishedOnConnection,
		boolean planPending,
		boolean appendPending,
		boolean lockedSnapshotPending,
		boolean finalizePending,
		boolean terminal
	) {
		public State {
			if (terminal) {
				if (
					!establishedOnConnection
						|| planPending
						|| appendPending
						|| lockedSnapshotPending
						|| finalizePending
				) {
					throw new IllegalArgumentException(
						"a terminal final projection cannot retain pending transport work"
					);
				}
			} else {
				if (!finalizePending) {
					throw new IllegalArgumentException(
						"a live final projection must retain its FINALIZE operation"
					);
				}
				if (planPending == establishedOnConnection) {
					throw new IllegalArgumentException(
						"exactly one of connection establishment and PLAN pending must hold"
					);
				}
				if (appendPending && !establishedOnConnection) {
					throw new IllegalArgumentException(
						"APPEND requires an instance established on this connection"
					);
				}
			}
		}

		public Optional<Operation> nextOperation() {
			if (this.terminal) {
				return Optional.empty();
			}
			if (this.planPending) {
				return Optional.of(Operation.PLAN);
			}
			if (this.appendPending) {
				return Optional.of(Operation.APPEND);
			}
			if (this.lockedSnapshotPending) {
				return Optional.of(Operation.LOCKED_SNAPSHOT);
			}
			return Optional.of(Operation.FINALIZE);
		}

		/**
		 * Applies one attempted wire operation. A retryable failure changes no state, so an APPEND
		 * failure can never masquerade as a fresh connection requiring another PLAN.
		 */
		public State record(
			final Operation operation,
			final DeliveryResult result
		) {
			Objects.requireNonNull(operation, "operation");
			Objects.requireNonNull(result, "result");
			Operation expected = this.nextOperation().orElseThrow(
				() -> new IllegalStateException("the final projection is already terminal")
			);
			if (operation != expected) {
				throw new IllegalStateException(
					"expected " + expected + " before " + operation
				);
			}
			if (result == DeliveryResult.RETRYABLE_FAILURE) {
				return this;
			}
			return switch (operation) {
				case PLAN -> new State(
					true,
					false,
					false,
					this.lockedSnapshotPending,
					true,
					false
				);
				case APPEND -> new State(
					true,
					false,
					false,
					false,
					true,
					false
				);
				case LOCKED_SNAPSHOT -> new State(
					true,
					false,
					false,
					false,
					true,
					false
				);
				case FINALIZE -> new State(
					true,
					false,
					false,
					false,
					false,
					true
				);
			};
		}

		/** Returns the remaining successful-path operations without mutating this state. */
		public List<Operation> successfulPath() {
			List<Operation> operations = new ArrayList<>();
			State cursor = this;
			while (!cursor.terminal()) {
				Operation operation = cursor.nextOperation().orElseThrow();
				operations.add(operation);
				cursor = cursor.record(operation, DeliveryResult.SENT);
			}
			return List.copyOf(operations);
		}
	}
}
