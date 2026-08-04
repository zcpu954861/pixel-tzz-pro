package io.github.zcpu954861.pixeltzzpro.server.message;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Process-local lookup index for durable {@code restart=finalize} static projections.
 *
 * <p>The stable instance id de-duplicates defensive recovery retries, but this queue is not the
 * consume-once authority. After draining, the integration layer must first persist the recipient
 * in the instance's completed-target set and may send the projection only after that checkpoint
 * commits. A failed commit may safely enqueue the projection again.
 */
public final class MessageRecoveredFinalQueue<T> {
	private final Map<UUID, LinkedHashMap<UUID, T>> byRecipient = new LinkedHashMap<>();
	private final Set<UUID> authorityReadyRecipients = new HashSet<>();

	public void enqueue(
		final UUID instanceId,
		final Collection<UUID> recipients,
		final T projection
	) {
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(recipients, "recipients");
		Objects.requireNonNull(projection, "projection");
		for (UUID recipient : recipients) {
			this.byRecipient
				.computeIfAbsent(
					Objects.requireNonNull(recipient, "recipient"),
					ignored -> new LinkedHashMap<>()
				)
				.putIfAbsent(instanceId, projection);
		}
	}

	public List<T> drain(final UUID recipient) {
		LinkedHashMap<UUID, T> pending = this.byRecipient.remove(
			Objects.requireNonNull(recipient, "recipient")
		);
		return pending == null ? List.of() : List.copyOf(pending.values());
	}

	/** Starts a new physical connection before core player authority has been restored. */
	public void beginConnection(final UUID recipient) {
		this.authorityReadyRecipients.remove(
			Objects.requireNonNull(recipient, "recipient")
		);
	}

	/** Marks registration, online state, role and forced-flow state as ready for live rechecks. */
	public void markAuthorityReady(final UUID recipient) {
		this.authorityReadyRecipients.add(
			Objects.requireNonNull(recipient, "recipient")
		);
	}

	/**
	 * Drains only after the current physical connection has restored authoritative player state.
	 * Calling this too early leaves the durable projection queued for the later readiness signal.
	 */
	public List<T> drainReady(final UUID recipient) {
		UUID checked = Objects.requireNonNull(recipient, "recipient");
		return this.authorityReadyRecipients.contains(checked)
			? drain(checked)
			: List.of();
	}

	public void clear() {
		this.byRecipient.clear();
		this.authorityReadyRecipients.clear();
	}

	public int pendingRecipients() {
		return this.byRecipient.size();
	}
}
