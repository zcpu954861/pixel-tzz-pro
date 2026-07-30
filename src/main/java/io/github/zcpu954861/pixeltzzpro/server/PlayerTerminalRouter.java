package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRouteDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Selects one ordinary player-terminal root page from server-authoritative context.
 *
 * <p>The router never trusts a page ID supplied by a client. Task-scoped candidates outrank
 * contextual candidates, then the data-pack priority decides. The compiler rejects duplicate
 * priorities within one game, while the stable ID tiebreaker keeps recovery deterministic if an
 * older frozen snapshot predates that rule.
 */
public final class PlayerTerminalRouter {
	private PlayerTerminalRouter() {
	}

	public static Resolution resolve(
		final DefinitionSnapshot definitions,
		final GameDefinition game,
		final Context context,
		final PredicateEvaluator predicates
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(game, "game");
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(predicates, "predicates");
		if (!game.id().equals(context.gameId())) {
			return Resolution.unavailable("玩家终端上下文与当前游戏不一致");
		}
		if (context.hostId().filter(context.player().playerId()::equals).isPresent()) {
			return Resolution.host();
		}
		var terminal = game.playerTerminal().orElse(null);
		if (terminal == null) {
			return Resolution.unavailable("当前游戏没有注册玩家终端");
		}

		PlayerRouteDefinition route = definitions.playerRoutes()
			.values()
			.stream()
			.filter(candidate -> candidate.game().equals(game.id()))
			.filter(candidate -> matches(definitions, candidate, context, predicates))
			.sorted(
				Comparator
					.comparing((PlayerRouteDefinition candidate) ->
						context.taskId().isPresent() && !candidate.tasks().isEmpty()
					)
					.reversed()
					.thenComparing(
						Comparator.comparingInt(PlayerRouteDefinition::priority).reversed()
					)
					.thenComparing(PlayerRouteDefinition::id)
			)
			.findFirst()
			.orElse(null);
		if (route != null) {
			return Resolution.route(route.id(), route.page());
		}
		return Resolution.fallback(terminal.defaultPage());
	}

	private static boolean matches(
		final DefinitionSnapshot definitions,
		final PlayerRouteDefinition route,
		final Context context,
		final PredicateEvaluator predicates
	) {
		if (
			!AudienceMatcher.matches(
				definitions,
				route.audience(),
				context.player(),
				context.hostId(),
				context.online()
			)
				|| (
					!route.phases().isEmpty()
						&& !route.phases().contains(context.phaseId())
				)
				|| (
					!route.tasks().isEmpty()
						&& context.taskId().filter(route.tasks()::contains).isEmpty()
				)
				|| (
					!route.taskStates().isEmpty()
						&& context.taskState().filter(route.taskStates()::contains).isEmpty()
				)
		) {
			return false;
		}
		return route.predicate().map(value -> predicates.test(value, context.player())).orElse(true);
	}

	@FunctionalInterface
	public interface PredicateEvaluator {
		boolean test(Identifier predicate, PlayerRecord player);

		static PredicateEvaluator allowAll() {
			return (predicate, player) -> true;
		}
	}

	public record Context(
		Identifier gameId,
		Identifier phaseId,
		PlayerRecord player,
		Optional<UUID> hostId,
		boolean online,
		Optional<Identifier> taskId,
		Optional<PlayerTaskState> taskState
	) {
		public Context {
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(phaseId, "phaseId");
			Objects.requireNonNull(player, "player");
			hostId = Objects.requireNonNull(hostId, "hostId");
			taskId = Objects.requireNonNull(taskId, "taskId");
			taskState = Objects.requireNonNull(taskState, "taskState");
			if (taskId.isEmpty() && taskState.isPresent()) {
				throw new IllegalArgumentException("task state requires a current task");
			}
		}
	}

	public enum Kind {
		ROUTE,
		FALLBACK,
		HOST,
		UNAVAILABLE
	}

	public record Resolution(
		Kind kind,
		Optional<Identifier> routeId,
		Optional<Identifier> pageId,
		String message
	) {
		public Resolution {
			Objects.requireNonNull(kind, "kind");
			routeId = Objects.requireNonNull(routeId, "routeId");
			pageId = Objects.requireNonNull(pageId, "pageId");
			message = Objects.requireNonNull(message, "message");
			if (
				(kind == Kind.ROUTE) != routeId.isPresent()
					|| (
						(kind == Kind.ROUTE || kind == Kind.FALLBACK)
							!= pageId.isPresent()
					)
			) {
				throw new IllegalArgumentException("terminal route result has mismatched payload");
			}
		}

		private static Resolution route(
			final Identifier routeId,
			final Identifier pageId
		) {
			return new Resolution(
				Kind.ROUTE,
				Optional.of(routeId),
				Optional.of(pageId),
				""
			);
		}

		private static Resolution fallback(final Identifier pageId) {
			return new Resolution(
				Kind.FALLBACK,
				Optional.empty(),
				Optional.of(pageId),
				""
			);
		}

		private static Resolution host() {
			return new Resolution(
				Kind.HOST,
				Optional.empty(),
				Optional.empty(),
				"主持人应打开主持人控制台"
			);
		}

		private static Resolution unavailable(final String message) {
			return new Resolution(
				Kind.UNAVAILABLE,
				Optional.empty(),
				Optional.empty(),
				message
			);
		}
	}
}
