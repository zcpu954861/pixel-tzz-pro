package io.github.zcpu954861.pixeltzzpro.content;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Declarative links from authoritative game definitions to V3B message cues.
 *
 * <p>The owner-specific event whitelist is enforced by {@link DefinitionCompiler}. Hook arguments
 * are immutable fixed defaults only; the runtime will add reserved authoritative arguments after
 * a successful transaction and therefore data packs may not shadow any reserved name here.
 */
public final class MessageHookDefinitions {
	public static final int MAX_HOOKS_PER_OWNER = 8;
	public static final int MAX_FIXED_ARGUMENTS = 32;

	/** Stable automatic argument names owned by the future authoritative hook dispatcher. */
	public static final Set<String> RESERVED_AUTOMATIC_ARGUMENTS = Set.of(
		"hook_event",
		"game_id",
		"game_instance_id",
		"phase_id",
		"previous_phase_id",
		"state_revision",
		"server_tick",
		"game_elapsed_ticks",
		"initiator",
		"target",
		"task_id",
		"task_instance_id",
		"task_kind",
		"task_elapsed_ticks",
		"result_id",
		"result_semantic",
		"interrupt_reason",
		"event",
		"event_state",
		"event_player",
		"flow_id",
		"flow_version",
		"flow_instance_id",
		"source_action_id",
		"initiated_by",
		"completed",
		"total",
		"player",
		"player_name",
		"previous_role_id",
		"role_id",
		"initialization",
		"readiness_instance_id",
		"end_status",
		"end_reason",
		"pause_reason"
	);

	private MessageHookDefinitions() {
	}

	public enum MessageHookEvent {
		START,
		PAUSE,
		RESUME,
		END,
		ENTER,
		EXIT,
		COMPLETE,
		INTERRUPT,
		TRIGGER,
		INITIALIZATION,
		ROLE_CHANGED,
		PLAYER_COMPLETE,
		ALL_COMPLETE
	}

	public record MessageHookReference(Identifier cue, Map<String, String> arguments) {
		public MessageHookReference {
			cue = Objects.requireNonNull(cue, "cue");
			arguments = Map.copyOf(arguments);
		}

		public MessageHookReference(final Identifier cue) {
			this(cue, Map.of());
		}
	}

	public record MessageHookSet(Map<MessageHookEvent, MessageHookReference> hooks) {
		public MessageHookSet {
			hooks = Map.copyOf(hooks);
		}

		public static MessageHookSet empty() {
			return new MessageHookSet(Map.of());
		}

		public Optional<MessageHookReference> get(final MessageHookEvent event) {
			return Optional.ofNullable(this.hooks.get(event));
		}

		public boolean isEmpty() {
			return this.hooks.isEmpty();
		}
	}
}
