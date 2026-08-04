package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookReference;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookSet;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Bridges declarative definition hooks into the trusted V3B runtime after an owning transaction
 * has committed.
 *
 * <p>Fixed data-pack arguments are copied first. Server-authored facts are then overlaid, so a
 * stale or malicious definition can never shadow authoritative event context. Hook failures are
 * intentionally isolated inside {@link MessageServerRuntime#playHook}; callers must never roll
 * back an already-committed game transaction because presentation failed.</p>
 */
public final class MessageHookDispatcher {
	private MessageHookDispatcher() {
	}

	/**
	 * Dispatches one hook against the exact definition generation owned by the committing
	 * transaction.
	 *
	 * <p>Long-lived flows and timelines must call this overload with their restored frozen
	 * snapshot. This prevents a later {@code /reload} from replacing or deleting the cue while the
	 * owning transaction is still active.</p>
	 */
	public static boolean dispatch(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final MessageHookSet hooks,
		final MessageHookEvent event,
		final Set<UUID> targets,
		final Optional<UUID> invoker,
		final Map<String, String> automaticArguments
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(hooks, "hooks");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(targets, "targets");
		Objects.requireNonNull(invoker, "invoker");
		Objects.requireNonNull(automaticArguments, "automaticArguments");
		Optional<MessageHookReference> hook = hooks.get(event);
		if (hook.isEmpty()) {
			return false;
		}
		MessageHookReference reference = hook.orElseThrow();
		return MessageServerRuntime.playHook(
			server,
			definitions,
			reference.cue(),
			Set.copyOf(targets),
			invoker,
			effectiveArguments(reference, event, automaticArguments)
		);
	}

	static Map<String, String> effectiveArguments(
		final MessageHookReference reference,
		final MessageHookEvent event,
		final Map<String, String> automaticArguments
	) {
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(automaticArguments, "automaticArguments");
		return MessageHookDispatchContract.effectiveArguments(
			reference.arguments(),
			automaticArguments,
			event.name()
		);
	}
}
