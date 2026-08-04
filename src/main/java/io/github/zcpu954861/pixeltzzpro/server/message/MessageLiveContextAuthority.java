package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextRecheck;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Pure server-authoritative decision boundary for a cue's live game context.
 *
 * <p>The invocation snapshot remains useful for deterministic fields and callback execution, but
 * a {@code live_strict} cue must compare its registered game, phase and task constraints with the
 * current world on every server tick. An explicitly authorized context bypass remains frozen on
 * the invocation and skips only this registered-context check.
 */
public final class MessageLiveContextAuthority {
	private MessageLiveContextAuthority() {
	}

	public static Directive resolve(
		final Optional<Identifier> cueGame,
		final ContextPolicy policy,
		final boolean bypassContext,
		final CurrentContext current
	) {
		Objects.requireNonNull(cueGame, "cueGame");
		Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(current, "current");
		if (
			bypassContext
				|| policy.recheck() != ContextRecheck.LIVE_STRICT
		) {
			return Directive.CONTINUE;
		}
		if (
			cueGame.isPresent() && !cueGame.equals(current.game())
				|| !matches(policy.games(), current.game())
				|| !matches(policy.phases(), current.phase())
				|| !matches(policy.tasks(), current.task())
		) {
			return Directive.CANCEL;
		}
		return Directive.CONTINUE;
	}

	private static boolean matches(
		final Set<Identifier> allowed,
		final Optional<Identifier> actual
	) {
		return allowed.isEmpty() || actual.filter(allowed::contains).isPresent();
	}

	public record CurrentContext(
		Optional<Identifier> game,
		Optional<Identifier> phase,
		Optional<Identifier> task
	) {
		public CurrentContext {
			game = Objects.requireNonNull(game, "game");
			phase = Objects.requireNonNull(phase, "phase");
			task = Objects.requireNonNull(task, "task");
		}

		public static CurrentContext empty() {
			return new CurrentContext(
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public enum Directive {
		CONTINUE,
		CANCEL
	}
}
