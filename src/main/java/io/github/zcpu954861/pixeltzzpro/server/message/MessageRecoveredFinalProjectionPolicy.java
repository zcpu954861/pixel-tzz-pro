package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.NodeOccurrence;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Fail-closed disclosure policy for a frozen {@code restart=finalize} fallback.
 *
 * <p>A static fallback represents the recipient's complete final presentation and cannot be
 * redacted per node. It is therefore safe only when every text occurrence that the frozen
 * invocation could have shown that recipient is still authorized at the delivery boundary. One
 * public node must never be used as cover for another now-revoked node.</p>
 */
public final class MessageRecoveredFinalProjectionPolicy {
	private MessageRecoveredFinalProjectionPolicy() {
	}

	public static boolean maySendStaticFallback(
		final MessageInstance instance,
		final UUID recipient,
		final Set<String> authorizedNodeIds
	) {
		Objects.requireNonNull(instance, "instance");
		Objects.requireNonNull(recipient, "recipient");
		Set<String> authorized = Set.copyOf(
			Objects.requireNonNull(authorizedNodeIds, "authorizedNodeIds")
		);
		if (instance.cue().staticFallback().isEmpty()) {
			return false;
		}
		List<NodeOccurrence> visible = MessageFinalProjectionPlanner.plan(
			instance,
			recipient,
			Set.of(),
			Set.of()
		).orderedOccurrences();
		return !visible.isEmpty()
			&& visible.stream().allMatch(value -> authorized.contains(value.nodeId()));
	}
}
