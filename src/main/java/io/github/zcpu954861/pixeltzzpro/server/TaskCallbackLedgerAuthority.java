package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStepStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure, bounded transactions for task callback ledgers.
 *
 * <p>A caller must persist the {@link CallbackStepStatus#PREPARED} entry before invoking the data
 * pack function. A successful entry is never prepared again. On startup, any entry left prepared
 * must first pass through {@link #markPreparedUnknown(List, long, String)} so a crash cannot
 * silently replay a function whose world-side effects may already have happened.
 */
public final class TaskCallbackLedgerAuthority {
	private static final int MAX_STEP_KEY_LENGTH = 192;
	private static final int MAX_ERROR_LENGTH = 4_096;

	private TaskCallbackLedgerAuthority() {
	}

	public static PrepareResult prepareAutomatic(
		final List<CallbackStep> ledger,
		final StepIdentity identity,
		final long gameTick
	) {
		return prepare(ledger, identity, gameTick, false);
	}

	/**
	 * Prepares an explicit retry of a failed or outcome-unknown step.
	 *
	 * <p>The caller is responsible for requiring the host confirmation appropriate to an
	 * outcome-unknown retry.
	 */
	public static PrepareResult prepareRetry(
		final List<CallbackStep> ledger,
		final StepIdentity identity,
		final long gameTick
	) {
		return prepare(ledger, identity, gameTick, true);
	}

	public static MutationResult recordOutcome(
		final List<CallbackStep> ledger,
		final PreparedInvocation prepared,
		final boolean succeeded,
		final Optional<String> error,
		final long gameTick
	) {
		Objects.requireNonNull(prepared, "prepared");
		Objects.requireNonNull(error, "error");
		String invalid = validationError(ledger, gameTick);
		if (invalid != null) {
			return MutationResult.rejected(OperationCode.SCHEMA_BLOCKED, invalid);
		}
		if (succeeded == error.isPresent()) {
			return MutationResult.rejected(
				OperationCode.INTERNAL_ERROR,
				"callback outcome must provide an error exactly when it failed"
			);
		}
		String boundedError = error.map(TaskCallbackLedgerAuthority::boundedError).orElse("");
		int index = indexOf(ledger, prepared.identity());
		if (index < 0) {
			return MutationResult.rejected(
				OperationCode.STATE_REVISION_STALE,
				"prepared callback step is no longer present"
			);
		}
		CallbackStep current = ledger.get(index);
		if (
			current.status() != CallbackStepStatus.PREPARED
				|| current.attemptCount() != prepared.attemptCount()
				|| current.updatedAtGameTick() != prepared.preparedAtGameTick()
				|| !current.functionId().equals(prepared.identity().functionId())
		) {
			return MutationResult.rejected(
				OperationCode.STATE_REVISION_STALE,
				"prepared callback step changed before its outcome was recorded"
			);
		}
		if (gameTick < current.updatedAtGameTick()) {
			return MutationResult.rejected(OperationCode.INTERNAL_ERROR, "callback outcome tick moved backwards");
		}
		CallbackStep replacement = new CallbackStep(
			current.stepKey(),
			current.functionId(),
			current.playerId(),
			succeeded ? CallbackStepStatus.SUCCEEDED : CallbackStepStatus.FAILED,
			current.attemptCount(),
			succeeded ? Optional.empty() : Optional.of(boundedError),
			gameTick
		);
		return MutationResult.changed(
			succeeded ? OperationCode.SUCCESS : OperationCode.CALLBACK_FAILED,
			replace(ledger, index, replacement),
			succeeded ? "callback step succeeded" : "callback step failed"
		);
	}

	/**
	 * Converts crash-left prepared markers into an explicit diagnostic state without replaying
	 * them.
	 */
	public static MutationResult markPreparedUnknown(
		final List<CallbackStep> ledger,
		final long gameTick,
		final String reason
	) {
		String invalid = validationError(ledger, gameTick);
		if (invalid != null) {
			return MutationResult.rejected(OperationCode.SCHEMA_BLOCKED, invalid);
		}
		String boundedReason = boundedError(reason);
		List<CallbackStep> next = new ArrayList<>(ledger.size());
		boolean changed = false;
		for (CallbackStep step : ledger) {
			if (step.status() == CallbackStepStatus.PREPARED) {
				if (gameTick < step.updatedAtGameTick()) {
					return MutationResult.rejected(
						OperationCode.INTERNAL_ERROR,
						"callback recovery tick moved backwards"
					);
				}
				next.add(
					new CallbackStep(
						step.stepKey(),
						step.functionId(),
						step.playerId(),
						CallbackStepStatus.OUTCOME_UNKNOWN,
						step.attemptCount(),
						Optional.of(boundedReason),
						gameTick
					)
				);
				changed = true;
			} else {
				next.add(step);
			}
		}
		return changed
			? MutationResult.changed(
				OperationCode.CALLBACK_FAILED,
				List.copyOf(next),
				"prepared callback outcome is unknown after recovery"
			)
			: MutationResult.unchanged("callback ledger has no prepared steps");
	}

	public static boolean allSucceeded(
		final List<CallbackStep> ledger,
		final List<StepIdentity> required
	) {
		Objects.requireNonNull(required, "required");
		if (validationError(ledger, 0L) != null) {
			return false;
		}
		Set<StepKey> identities = new HashSet<>();
		for (StepIdentity identity : required) {
			Objects.requireNonNull(identity, "required callback identity");
			if (!identities.add(identity.key())) {
				throw new IllegalArgumentException("required callback steps contain duplicates");
			}
			int index = indexOf(ledger, identity);
			if (
				index < 0
					|| ledger.get(index).status() != CallbackStepStatus.SUCCEEDED
					|| !ledger.get(index).functionId().equals(identity.functionId())
			) {
				return false;
			}
		}
		return true;
	}

	private static PrepareResult prepare(
		final List<CallbackStep> ledger,
		final StepIdentity identity,
		final long gameTick,
		final boolean retry
	) {
		Objects.requireNonNull(identity, "identity");
		String invalid = validationError(ledger, gameTick);
		if (invalid != null) {
			return PrepareResult.rejected(OperationCode.SCHEMA_BLOCKED, invalid);
		}
		int index = indexOf(ledger, identity);
		if (index < 0) {
			if (retry) {
				return PrepareResult.rejected(
					OperationCode.ACTION_UNAVAILABLE,
					"callback step has no failed attempt to retry"
				);
			}
			if (ledger.size() >= WorldStateV3.MAX_CALLBACK_STEPS) {
				return PrepareResult.rejected(
					OperationCode.RESOURCE_BLOCKED,
					"callback ledger reached its capacity"
				);
			}
			CallbackStep prepared = new CallbackStep(
				identity.stepKey(),
				identity.functionId(),
				identity.playerId(),
				CallbackStepStatus.PREPARED,
				1,
				Optional.empty(),
				gameTick
			);
			List<CallbackStep> next = new ArrayList<>(ledger);
			next.add(prepared);
			return PrepareResult.prepared(
				List.copyOf(next),
				new PreparedInvocation(identity, 1, gameTick, false)
			);
		}

		CallbackStep current = ledger.get(index);
		if (!current.functionId().equals(identity.functionId())) {
			return PrepareResult.rejected(
				OperationCode.SNAPSHOT_INVALID,
				"callback step key now points to a different function"
			);
		}
		if (!retry) {
			return switch (current.status()) {
				case SUCCEEDED -> PrepareResult.skipped("callback step already succeeded");
				case PREPARED -> PrepareResult.rejected(
					OperationCode.CALLBACK_FAILED,
					"callback step is already prepared"
				);
				case FAILED, OUTCOME_UNKNOWN -> PrepareResult.rejected(
					OperationCode.CALLBACK_FAILED,
					"callback step requires an explicit retry"
				);
			};
		}
		if (
			current.status() != CallbackStepStatus.FAILED
				&& current.status() != CallbackStepStatus.OUTCOME_UNKNOWN
		) {
			return PrepareResult.rejected(
				OperationCode.ACTION_UNAVAILABLE,
				current.status() == CallbackStepStatus.SUCCEEDED
					? "successful callback steps cannot be retried"
					: "callback step is already prepared"
			);
		}
		if (gameTick < current.updatedAtGameTick()) {
			return PrepareResult.rejected(OperationCode.INTERNAL_ERROR, "callback retry tick moved backwards");
		}
		try {
			int attempt = Math.incrementExact(current.attemptCount());
			CallbackStep prepared = new CallbackStep(
				current.stepKey(),
				current.functionId(),
				current.playerId(),
				CallbackStepStatus.PREPARED,
				attempt,
				Optional.empty(),
				gameTick
			);
			return PrepareResult.prepared(
				replace(ledger, index, prepared),
				new PreparedInvocation(identity, attempt, gameTick, true)
			);
		} catch (ArithmeticException error) {
			return PrepareResult.rejected(
				OperationCode.RESOURCE_BLOCKED,
				"callback attempt counter is exhausted"
			);
		}
	}

	private static String validationError(final List<CallbackStep> ledger, final long gameTick) {
		if (ledger == null) {
			return "callback ledger is missing";
		}
		if (gameTick < 0L) {
			return "callback game tick cannot be negative";
		}
		if (ledger.size() > WorldStateV3.MAX_CALLBACK_STEPS) {
			return "callback ledger exceeds its capacity";
		}
		Set<StepKey> keys = new HashSet<>();
		for (CallbackStep step : ledger) {
			if (step == null) {
				return "callback ledger contains a null step";
			}
			if (
				step.stepKey().isBlank()
					|| step.stepKey().length() > MAX_STEP_KEY_LENGTH
					|| step.attemptCount() <= 0
					|| step.updatedAtGameTick() < 0L
					|| !keys.add(new StepKey(step.stepKey(), step.playerId()))
			) {
				return "callback ledger contains an invalid or duplicate step";
			}
			boolean errorRequired = step.status() == CallbackStepStatus.FAILED
				|| step.status() == CallbackStepStatus.OUTCOME_UNKNOWN;
			if (
				errorRequired != step.lastError().isPresent()
					|| step.lastError().filter(String::isBlank).isPresent()
			) {
				return "callback step error does not match its status";
			}
		}
		return null;
	}

	private static int indexOf(final List<CallbackStep> ledger, final StepIdentity identity) {
		StepKey key = identity.key();
		for (int index = 0; index < ledger.size(); index++) {
			CallbackStep candidate = ledger.get(index);
			if (key.equals(new StepKey(candidate.stepKey(), candidate.playerId()))) {
				return index;
			}
		}
		return -1;
	}

	private static List<CallbackStep> replace(
		final List<CallbackStep> ledger,
		final int index,
		final CallbackStep replacement
	) {
		List<CallbackStep> next = new ArrayList<>(ledger);
		next.set(index, replacement);
		return List.copyOf(next);
	}

	private static String boundedError(final String value) {
		String error = Objects.requireNonNull(value, "error").strip();
		if (error.isEmpty()) {
			error = "callback failed without a diagnostic";
		}
		return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
	}

	public record StepIdentity(
		String stepKey,
		Identifier functionId,
		Optional<UUID> playerId
	) {
		public StepIdentity {
			stepKey = Objects.requireNonNull(stepKey, "stepKey");
			functionId = Objects.requireNonNull(functionId, "functionId");
			playerId = Objects.requireNonNull(playerId, "playerId");
			if (stepKey.isBlank() || stepKey.length() > MAX_STEP_KEY_LENGTH) {
				throw new IllegalArgumentException("callback step key is invalid");
			}
		}

		private StepKey key() {
			return new StepKey(this.stepKey, this.playerId);
		}
	}

	public record PreparedInvocation(
		StepIdentity identity,
		int attemptCount,
		long preparedAtGameTick,
		boolean retry
	) {
		public PreparedInvocation {
			identity = Objects.requireNonNull(identity, "identity");
			if (attemptCount <= 0 || preparedAtGameTick < 0L || retry != (attemptCount > 1)) {
				throw new IllegalArgumentException("prepared callback invocation is invalid");
			}
		}
	}

	public record PrepareResult(
		OperationCode code,
		String message,
		Optional<List<CallbackStep>> nextLedger,
		Optional<PreparedInvocation> invocation
	) {
		public PrepareResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextLedger = Objects.requireNonNull(nextLedger, "nextLedger").map(List::copyOf);
			invocation = Objects.requireNonNull(invocation, "invocation");
			if (
				message.isBlank()
					|| nextLedger.isPresent() != invocation.isPresent()
					|| (code != OperationCode.SUCCESS && nextLedger.isPresent())
			) {
				throw new IllegalArgumentException("callback preparation result is inconsistent");
			}
		}

		public boolean prepared() {
			return this.invocation.isPresent();
		}

		private static PrepareResult prepared(
			final List<CallbackStep> ledger,
			final PreparedInvocation invocation
		) {
			return new PrepareResult(
				OperationCode.SUCCESS,
				"callback step prepared",
				Optional.of(ledger),
				Optional.of(invocation)
			);
		}

		private static PrepareResult skipped(final String message) {
			return new PrepareResult(
				OperationCode.SUCCESS,
				message,
				Optional.empty(),
				Optional.empty()
			);
		}

		private static PrepareResult rejected(final OperationCode code, final String message) {
			return new PrepareResult(code, message, Optional.empty(), Optional.empty());
		}
	}

	public record MutationResult(
		OperationCode code,
		String message,
		Optional<List<CallbackStep>> nextLedger
	) {
		public MutationResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextLedger = Objects.requireNonNull(nextLedger, "nextLedger").map(List::copyOf);
			if (
				message.isBlank()
					|| (
						nextLedger.isPresent()
							&& code != OperationCode.SUCCESS
							&& code != OperationCode.CALLBACK_FAILED
					)
			) {
				throw new IllegalArgumentException("callback ledger mutation result is inconsistent");
			}
		}

		public boolean changed() {
			return this.nextLedger.isPresent();
		}

		private static MutationResult changed(
			final OperationCode code,
			final List<CallbackStep> ledger,
			final String message
		) {
			return new MutationResult(code, message, Optional.of(ledger));
		}

		private static MutationResult unchanged(final String message) {
			return new MutationResult(OperationCode.SUCCESS, message, Optional.empty());
		}

		private static MutationResult rejected(final OperationCode code, final String message) {
			return new MutationResult(code, message, Optional.empty());
		}
	}

	private record StepKey(String stepKey, Optional<UUID> playerId) {
	}
}
