package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure, bounded and crash-conservative ledger for V3B message callbacks.
 *
 * <p>The integration layer must commit a {@link CallbackStatus#PREPARED} entry before executing
 * the returned function. A callback that has succeeded is never prepared again. A callback left
 * prepared across a crash is converted to {@link CallbackStatus#OUTCOME_UNKNOWN}; it is not
 * replayed automatically because its world-side effects may already have happened.
 */
public final class MessageCallbackLedger {
	public static final int HARD_MAX_ENTRIES = 4_096;
	private static final int MAX_NODE_ID_LENGTH = 128;
	private static final int MAX_DIAGNOSTIC_LENGTH = 4_096;

	private MessageCallbackLedger() {
	}

	public enum CallbackStatus {
		PREPARED,
		SUCCEEDED,
		FAILED,
		OUTCOME_UNKNOWN
	}

	public enum PrepareDisposition {
		PREPARED,
		ALREADY_SUCCEEDED,
		REJECTED
	}

	public record CallbackKey(
		UUID instanceId,
		long cycle,
		String nodeId,
		int occurrence,
		CallbackTrigger trigger,
		Optional<UUID> targetId
	) {
		public CallbackKey {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			nodeId = requireNodeId(nodeId);
			trigger = Objects.requireNonNull(trigger, "trigger");
			targetId = Objects.requireNonNull(targetId, "targetId");
			if (cycle < 0L || occurrence < 0) {
				throw new IllegalArgumentException("callback cycle and occurrence cannot be negative");
			}
			if ((trigger == CallbackTrigger.TARGET_COMPLETE) != targetId.isPresent()) {
				throw new IllegalArgumentException(
					"only target-complete callback keys carry a target"
				);
			}
		}
	}

	public record CallbackEntry(
		CallbackKey key,
		Identifier functionId,
		CallbackExecution execution,
		CallbackLocation location,
		CallbackStatus status,
		int attemptCount,
		long updatedAtGameTick,
		Optional<String> diagnostic
	) {
		public CallbackEntry {
			key = Objects.requireNonNull(key, "key");
			functionId = Objects.requireNonNull(functionId, "functionId");
			execution = Objects.requireNonNull(execution, "execution");
			location = Objects.requireNonNull(location, "location");
			status = Objects.requireNonNull(status, "status");
			diagnostic = Objects.requireNonNull(diagnostic, "diagnostic")
				.map(MessageCallbackLedger::boundedDiagnostic);
			if (attemptCount != 1 || updatedAtGameTick < 0L) {
				throw new IllegalArgumentException(
					"automatic message callbacks have exactly one non-negative-tick attempt"
				);
			}
			boolean requiresDiagnostic = status == CallbackStatus.FAILED
				|| status == CallbackStatus.OUTCOME_UNKNOWN;
			if (requiresDiagnostic != diagnostic.isPresent()) {
				throw new IllegalArgumentException(
					"callback diagnostic presence does not match its status"
				);
			}
		}
	}

	public record Ledger(int capacity, List<CallbackEntry> entries) {
		public Ledger {
			if (capacity < 1 || capacity > HARD_MAX_ENTRIES) {
				throw new IllegalArgumentException(
					"callback ledger capacity must be within 1.." + HARD_MAX_ENTRIES
				);
			}
			entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
			if (entries.size() > capacity) {
				throw new IllegalArgumentException("callback ledger exceeds its capacity");
			}
			Set<CallbackKey> keys = new HashSet<>();
			for (CallbackEntry entry : entries) {
				if (entry == null || !keys.add(entry.key())) {
					throw new IllegalArgumentException(
						"callback ledger contains a null or duplicate entry"
					);
				}
			}
		}

		public static Ledger empty(final int capacity) {
			return new Ledger(capacity, List.of());
		}

		public Optional<CallbackEntry> find(final CallbackKey key) {
			Objects.requireNonNull(key, "key");
			return this.entries.stream().filter(entry -> entry.key().equals(key)).findFirst();
		}
	}

	/**
	 * An invocation whose PREPARED marker is already present in {@link PrepareResult#nextLedger()}.
	 */
	public record PreparedCallback(
		CallbackKey key,
		Identifier functionId,
		CallbackExecution execution,
		CallbackLocation location,
		long preparedAtGameTick
	) {
		public PreparedCallback {
			key = Objects.requireNonNull(key, "key");
			functionId = Objects.requireNonNull(functionId, "functionId");
			execution = Objects.requireNonNull(execution, "execution");
			location = Objects.requireNonNull(location, "location");
			if (preparedAtGameTick < 0L) {
				throw new IllegalArgumentException("prepared callback tick cannot be negative");
			}
		}
	}

	public record PrepareResult(
		PrepareDisposition disposition,
		String message,
		Ledger nextLedger,
		Optional<PreparedCallback> callback
	) {
		public PrepareResult {
			disposition = Objects.requireNonNull(disposition, "disposition");
			message = requireMessage(message);
			nextLedger = Objects.requireNonNull(nextLedger, "nextLedger");
			callback = Objects.requireNonNull(callback, "callback");
			if ((disposition == PrepareDisposition.PREPARED) != callback.isPresent()) {
				throw new IllegalArgumentException(
					"prepared callback presence does not match prepare disposition"
				);
			}
		}

		public boolean prepared() {
			return this.disposition == PrepareDisposition.PREPARED;
		}
	}

	public record MutationResult(
		boolean accepted,
		boolean changed,
		String message,
		Ledger nextLedger
	) {
		public MutationResult {
			message = requireMessage(message);
			nextLedger = Objects.requireNonNull(nextLedger, "nextLedger");
			if (!accepted && changed) {
				throw new IllegalArgumentException(
					"a rejected callback mutation cannot change the ledger"
				);
			}
		}
	}

	public static PrepareResult prepare(
		final Ledger ledger,
		final CallbackKey key,
		final Identifier functionId,
		final CallbackExecution execution,
		final CallbackLocation location,
		final long gameTick
	) {
		Objects.requireNonNull(ledger, "ledger");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(functionId, "functionId");
		Objects.requireNonNull(execution, "execution");
		Objects.requireNonNull(location, "location");
		if (gameTick < 0L) {
			return rejectedPrepare(ledger, "callback preparation tick cannot be negative");
		}
		Optional<CallbackEntry> existing = ledger.find(key);
		if (existing.isPresent()) {
			CallbackEntry entry = existing.orElseThrow();
			if (
				!entry.functionId().equals(functionId)
					|| entry.execution() != execution
					|| entry.location() != location
			) {
				return rejectedPrepare(
					ledger,
					"callback key is already bound to a different registered callback"
				);
			}
			return switch (entry.status()) {
				case SUCCEEDED -> new PrepareResult(
					PrepareDisposition.ALREADY_SUCCEEDED,
					"callback already succeeded",
					ledger,
					Optional.empty()
				);
				case PREPARED -> rejectedPrepare(
					ledger,
					"callback is already prepared and cannot be executed twice"
				);
				case FAILED -> rejectedPrepare(
					ledger,
					"failed callback is retained and is not retried automatically"
				);
				case OUTCOME_UNKNOWN -> rejectedPrepare(
					ledger,
					"callback outcome is unknown and is not replayed automatically"
				);
			};
		}
		if (ledger.entries().size() >= ledger.capacity()) {
			return rejectedPrepare(ledger, "callback ledger reached its bounded capacity");
		}
		CallbackEntry entry = new CallbackEntry(
			key,
			functionId,
			execution,
			location,
			CallbackStatus.PREPARED,
			1,
			gameTick,
			Optional.empty()
		);
		List<CallbackEntry> entries = new ArrayList<>(ledger.entries());
		entries.add(entry);
		Ledger next = new Ledger(ledger.capacity(), entries);
		return new PrepareResult(
			PrepareDisposition.PREPARED,
			"callback prepared; commit the ledger before execution",
			next,
			Optional.of(
				new PreparedCallback(key, functionId, execution, location, gameTick)
			)
		);
	}

	public static MutationResult recordOutcome(
		final Ledger ledger,
		final PreparedCallback prepared,
		final boolean succeeded,
		final Optional<String> diagnostic,
		final long gameTick
	) {
		Objects.requireNonNull(ledger, "ledger");
		Objects.requireNonNull(prepared, "prepared");
		Objects.requireNonNull(diagnostic, "diagnostic");
		if (gameTick < prepared.preparedAtGameTick()) {
			return rejectedMutation(ledger, "callback outcome tick moved backwards");
		}
		if (succeeded == diagnostic.isPresent()) {
			return rejectedMutation(
				ledger,
				"callback outcome requires a diagnostic exactly when execution failed"
			);
		}
		int index = indexOf(ledger, prepared.key());
		if (index < 0) {
			return rejectedMutation(ledger, "prepared callback entry is no longer present");
		}
		CallbackEntry current = ledger.entries().get(index);
		if (
			current.status() != CallbackStatus.PREPARED
				|| !current.functionId().equals(prepared.functionId())
				|| current.execution() != prepared.execution()
				|| current.location() != prepared.location()
				|| current.updatedAtGameTick() != prepared.preparedAtGameTick()
		) {
			return rejectedMutation(
				ledger,
				"prepared callback entry changed before its outcome was recorded"
			);
		}
		CallbackEntry replacement = new CallbackEntry(
			current.key(),
			current.functionId(),
			current.execution(),
			current.location(),
			succeeded ? CallbackStatus.SUCCEEDED : CallbackStatus.FAILED,
			1,
			gameTick,
			succeeded ? Optional.empty() : diagnostic
		);
		return changedMutation(
			ledger,
			index,
			replacement,
			succeeded ? "callback succeeded" : "callback failed without automatic retry"
		);
	}

	/**
	 * Marks every crash-left PREPARED entry outcome-unknown without replaying it.
	 */
	public static MutationResult recoverPrepared(
		final Ledger ledger,
		final long gameTick,
		final String reason
	) {
		Objects.requireNonNull(ledger, "ledger");
		if (gameTick < 0L) {
			return rejectedMutation(ledger, "callback recovery tick cannot be negative");
		}
		String diagnostic = boundedDiagnostic(reason);
		List<CallbackEntry> entries = new ArrayList<>(ledger.entries().size());
		boolean changed = false;
		for (CallbackEntry entry : ledger.entries()) {
			if (entry.status() != CallbackStatus.PREPARED) {
				entries.add(entry);
				continue;
			}
			if (gameTick < entry.updatedAtGameTick()) {
				return rejectedMutation(ledger, "callback recovery tick moved backwards");
			}
			entries.add(
				new CallbackEntry(
					entry.key(),
					entry.functionId(),
					entry.execution(),
					entry.location(),
					CallbackStatus.OUTCOME_UNKNOWN,
					1,
					gameTick,
					Optional.of(diagnostic)
				)
			);
			changed = true;
		}
		return changed
			? new MutationResult(
				true,
				true,
				"prepared callback outcomes marked unknown after recovery",
				new Ledger(ledger.capacity(), entries)
			)
			: new MutationResult(true, false, "no prepared callbacks required recovery", ledger);
	}

	private static PrepareResult rejectedPrepare(final Ledger ledger, final String message) {
		return new PrepareResult(
			PrepareDisposition.REJECTED,
			message,
			ledger,
			Optional.empty()
		);
	}

	private static MutationResult rejectedMutation(final Ledger ledger, final String message) {
		return new MutationResult(false, false, message, ledger);
	}

	private static MutationResult changedMutation(
		final Ledger ledger,
		final int index,
		final CallbackEntry replacement,
		final String message
	) {
		List<CallbackEntry> entries = new ArrayList<>(ledger.entries());
		entries.set(index, replacement);
		return new MutationResult(
			true,
			true,
			message,
			new Ledger(ledger.capacity(), entries)
		);
	}

	private static int indexOf(final Ledger ledger, final CallbackKey key) {
		for (int index = 0; index < ledger.entries().size(); index++) {
			if (ledger.entries().get(index).key().equals(key)) {
				return index;
			}
		}
		return -1;
	}

	private static String requireNodeId(final String value) {
		String nodeId = Objects.requireNonNull(value, "nodeId").strip();
		if (nodeId.isEmpty() || nodeId.length() > MAX_NODE_ID_LENGTH) {
			throw new IllegalArgumentException(
				"callback node id must contain 1.." + MAX_NODE_ID_LENGTH + " characters"
			);
		}
		return nodeId;
	}

	private static String requireMessage(final String value) {
		String message = Objects.requireNonNull(value, "message").strip();
		if (message.isEmpty() || message.length() > MAX_DIAGNOSTIC_LENGTH) {
			throw new IllegalArgumentException(
				"callback result message must contain 1.." + MAX_DIAGNOSTIC_LENGTH + " characters"
			);
		}
		return message;
	}

	private static String boundedDiagnostic(final String value) {
		String diagnostic = Objects.requireNonNull(value, "diagnostic").strip();
		if (diagnostic.isEmpty()) {
			diagnostic = "callback failed without a diagnostic";
		}
		return diagnostic.length() <= MAX_DIAGNOSTIC_LENGTH
			? diagnostic
			: diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
	}
}
