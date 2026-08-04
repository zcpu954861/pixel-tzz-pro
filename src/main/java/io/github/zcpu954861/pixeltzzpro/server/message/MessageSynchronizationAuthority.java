package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LateArrivalMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationTimeoutMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure server authority for choosing each recipient's visual start without owning the cue clock.
 *
 * <p>The authority deliberately has no callback input or output. Callbacks remain attached to the
 * instance's authoritative cue timeline; this class only decides when (or whether) an individual
 * client should receive animated playback. That separation prevents one slow client from holding
 * the callback ledger open.
 */
public final class MessageSynchronizationAuthority {
	public static final int MAX_RECIPIENTS = 4_096;

	public enum Directive {
		PENDING,
		START_SHARED,
		START_NOW,
		SEEK_SHARED,
		STATIC_FINAL,
		CANCELLED
	}

	public record Policy(
		SynchronizationMode mode,
		long synchronizedWaitNanos,
		LateArrivalMode lateArrival,
		SynchronizationTimeoutMode timeout
	) {
		public Policy {
			mode = Objects.requireNonNull(mode, "mode");
			lateArrival = Objects.requireNonNull(lateArrival, "lateArrival");
			timeout = Objects.requireNonNull(timeout, "timeout");
			if (synchronizedWaitNanos < 0L) {
				throw new IllegalArgumentException("synchronized wait cannot be negative");
			}
			if (mode != SynchronizationMode.SYNCHRONIZED && synchronizedWaitNanos != 0L) {
				throw new IllegalArgumentException(
					"only synchronized playback can declare a synchronized wait"
				);
			}
		}
	}

	public record Decision(Directive directive, Optional<Long> visualStartElapsedNanos) {
		public Decision {
			directive = Objects.requireNonNull(directive, "directive");
			visualStartElapsedNanos = Objects.requireNonNull(
				visualStartElapsedNanos,
				"visualStartElapsedNanos"
			);
			if (
				visualStartElapsedNanos.filter(value -> value < 0L).isPresent()
					|| (directive == Directive.PENDING
						|| directive == Directive.STATIC_FINAL
						|| directive == Directive.CANCELLED)
						!= visualStartElapsedNanos.isEmpty()
			) {
				throw new IllegalArgumentException("synchronization decision has an invalid anchor");
			}
		}

		public static Decision pending() {
			return new Decision(Directive.PENDING, Optional.empty());
		}

		public static Decision withoutAnchor(final Directive directive) {
			return new Decision(directive, Optional.empty());
		}

		public static Decision anchored(final Directive directive, final long elapsed) {
			return new Decision(directive, Optional.of(elapsed));
		}
	}

	private final Policy policy;
	private final long sharedStartElapsedNanos;
	private final Map<UUID, Decision> recipients = new LinkedHashMap<>();
	private boolean cancelled;

	public MessageSynchronizationAuthority(
		final Policy policy,
		final long cueStartElapsedNanos,
		final Set<UUID> recipients
	) {
		this.policy = Objects.requireNonNull(policy, "policy");
		if (cueStartElapsedNanos < 0L) {
			throw new IllegalArgumentException("cue start elapsed time cannot be negative");
		}
		this.sharedStartElapsedNanos = Math.addExact(
			cueStartElapsedNanos,
			policy.mode() == SynchronizationMode.SYNCHRONIZED
				? policy.synchronizedWaitNanos()
				: 0L
		);
		Objects.requireNonNull(recipients, "recipients");
		if (recipients.size() > MAX_RECIPIENTS) {
			throw new IllegalArgumentException("synchronization recipient limit exceeded");
		}
		recipients.forEach(this::addRecipient);
	}

	public Policy policy() {
		return this.policy;
	}

	public long sharedStartElapsedNanos() {
		return this.sharedStartElapsedNanos;
	}

	public boolean cancelled() {
		return this.cancelled;
	}

	public Map<UUID, Decision> decisions() {
		return Map.copyOf(this.recipients);
	}

	public Decision decision(final UUID recipient) {
		return this.recipients.getOrDefault(
			Objects.requireNonNull(recipient, "recipient"),
			Decision.pending()
		);
	}

	public void addRecipient(final UUID recipient) {
		Objects.requireNonNull(recipient, "recipient");
		if (this.recipients.size() >= MAX_RECIPIENTS && !this.recipients.containsKey(recipient)) {
			throw new IllegalArgumentException("synchronization recipient limit exceeded");
		}
		this.recipients.putIfAbsent(
			recipient,
			this.cancelled
				? Decision.withoutAnchor(Directive.CANCELLED)
				: Decision.pending()
		);
	}

	/** Marks one client ready and freezes its visual directive exactly once. */
	public Decision markReady(final UUID recipient, final long elapsedNanos) {
		validateElapsed(elapsedNanos);
		addRecipient(recipient);
		Decision current = this.recipients.get(recipient);
		if (current.directive() != Directive.PENDING) {
			return current;
		}
		if (this.cancelled) {
			Decision cancelledDecision = Decision.withoutAnchor(Directive.CANCELLED);
			this.recipients.put(recipient, cancelledDecision);
			return cancelledDecision;
		}
		Decision decided = switch (this.policy.mode()) {
			case IMMEDIATE, PER_PLAYER -> Decision.anchored(
				Directive.START_NOW,
				elapsedNanos
			);
			case SYNCHRONIZED -> synchronizedReadyDecision(elapsedNanos);
		};
		this.recipients.put(recipient, decided);
		return decided;
	}

	/**
	 * Advances only the shared synchronization deadline. Immediate and per-player playback never
	 * wait for other recipients and therefore have no group timeout.
	 */
	public void advance(final long elapsedNanos) {
		validateElapsed(elapsedNanos);
		if (
			this.cancelled
				|| this.policy.mode() != SynchronizationMode.SYNCHRONIZED
				|| elapsedNanos <= this.sharedStartElapsedNanos
		) {
			return;
		}
		boolean pending = this.recipients.values().stream()
			.anyMatch(value -> value.directive() == Directive.PENDING);
		if (!pending) {
			return;
		}
		switch (this.policy.timeout()) {
			case START_AVAILABLE -> {
				// Pending recipients become late arrivals when they eventually report ready.
			}
			case STATIC_FINAL -> this.recipients.replaceAll(
				(recipient, current) -> current.directive() == Directive.PENDING
					? Decision.withoutAnchor(Directive.STATIC_FINAL)
					: current
			);
			case CANCEL -> {
				this.cancelled = true;
				this.recipients.replaceAll(
					(recipient, current) -> Decision.withoutAnchor(Directive.CANCELLED)
				);
			}
		}
	}

	/** Restores a previously frozen recipient decision without re-running timeout policy. */
	public void restore(final UUID recipient, final Decision decision) {
		Objects.requireNonNull(recipient, "recipient");
		Objects.requireNonNull(decision, "decision");
		addRecipient(recipient);
		this.recipients.put(recipient, decision);
		if (decision.directive() == Directive.CANCELLED) {
			this.cancelled = true;
		}
	}

	private Decision synchronizedReadyDecision(final long elapsedNanos) {
		if (elapsedNanos <= this.sharedStartElapsedNanos) {
			return Decision.anchored(Directive.START_SHARED, this.sharedStartElapsedNanos);
		}
		return switch (this.policy.lateArrival()) {
			case SEEK -> Decision.anchored(Directive.SEEK_SHARED, this.sharedStartElapsedNanos);
			case WAIT -> Decision.anchored(Directive.START_NOW, elapsedNanos);
			case STATIC_FINAL -> Decision.withoutAnchor(Directive.STATIC_FINAL);
		};
	}

	private static void validateElapsed(final long elapsedNanos) {
		if (elapsedNanos < 0L) {
			throw new IllegalArgumentException("elapsed time cannot be negative");
		}
	}
}
