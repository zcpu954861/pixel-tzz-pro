package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalRouter.Context;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalRouter.Kind;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalRouter.Resolution;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Runnable, serverless checks for ordinary-player terminal route selection.
 *
 * <p>The fixture is compiled through the real {@link DefinitionCompiler}; this check intentionally
 * does not construct route definitions by hand. That keeps route syntax, cross references,
 * priority uniqueness, and predicate registration inside the same trust boundary used by a data
 * pack reload.
 */
public final class PlayerTerminalRouterSelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier OTHER_GAME = id("other");
	private static final Identifier LOBBY = id("lobby");
	private static final Identifier ACTIVE = id("active");
	private static final Identifier ENDED = id("ended");
	private static final Identifier TASK = id("task");
	private static final Identifier RUNNER = id("runner");
	private static final Identifier HUNTER = id("hunter");
	private static final Identifier ALIVE = id("alive");
	private static final Identifier GATE = id("route_gate");

	private static final Identifier HOME_PAGE = id("page/home");
	private static final Identifier RUNNER_LOBBY_PAGE = id("page/runner_lobby");
	private static final Identifier RUNNER_ACTIVE_PAGE = id("page/runner_active");
	private static final Identifier RUNNER_RELOADED_PAGE = id("page/runner_reloaded");
	private static final Identifier RUNNER_PREDICATE_PAGE = id("page/runner_predicate");
	private static final Identifier RUNNER_TASK_PAGE = id("page/runner_task");
	private static final Identifier HUNTER_ACTIVE_PAGE = id("page/hunter_active");
	private static final Identifier HUNTER_TASK_PAGE = id("page/hunter_task");

	private static final Identifier RUNNER_LOBBY_ROUTE = id("route/runner_lobby");
	private static final Identifier RUNNER_ACTIVE_ROUTE = id("route/runner_active");
	private static final Identifier RUNNER_PREDICATE_ROUTE = id("route/runner_predicate");
	private static final Identifier RUNNER_TASK_ROUTE = id("route/runner_task");
	private static final Identifier HUNTER_ACTIVE_ROUTE = id("route/hunter_active");
	private static final Identifier HUNTER_TASK_ROUTE = id("route/hunter_task");

	private static final UUID RUNNER_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final UUID HUNTER_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000302");
	private static final UUID HOST_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000303");

	private PlayerTerminalRouterSelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		DefinitionSnapshot initial = compile(sources(false), 41L, Set.of(GATE));
		var game = initial.games().get(GAME);
		PlayerRecord runner = player(RUNNER_ID, "Runner", RUNNER);
		PlayerRecord hunter = player(HUNTER_ID, "Hunter", HUNTER);

		assertRoute(
			resolve(initial, game, context(LOBBY, runner), false),
			RUNNER_LOBBY_ROUTE,
			RUNNER_LOBBY_PAGE,
			"runner lobby route"
		);
		assertRoute(
			resolve(initial, game, context(ACTIVE, runner), false),
			RUNNER_ACTIVE_ROUTE,
			RUNNER_ACTIVE_PAGE,
			"higher-priority active runner route"
		);
		assertRoute(
			resolve(initial, game, context(ACTIVE, runner), true),
			RUNNER_PREDICATE_ROUTE,
			RUNNER_PREDICATE_PAGE,
			"predicate-enabled route"
		);

		// A task-scoped route deliberately has a lower numeric priority than every contextual
		// route. Task specificity must still win while the matching task is current.
		assertRoute(
			resolve(
				initial,
				game,
				context(ACTIVE, runner, TASK, PlayerTaskState.RUNNING),
				true
			),
			RUNNER_TASK_ROUTE,
			RUNNER_TASK_PAGE,
			"matching task route"
		);
		assertRoute(
			resolve(
				initial,
				game,
				context(ACTIVE, runner, TASK, PlayerTaskState.PAUSED),
				true
			),
			RUNNER_PREDICATE_ROUTE,
			RUNNER_PREDICATE_PAGE,
			"task-state mismatch must fall through"
		);
		assertRoute(
			resolve(
				initial,
				game,
				contextWithTaskButNoState(ACTIVE, runner, TASK),
				false
			),
			RUNNER_ACTIVE_ROUTE,
			RUNNER_ACTIVE_PAGE,
			"missing task state must fail closed"
		);

		assertRoute(
			resolve(initial, game, context(ACTIVE, hunter), true),
			HUNTER_ACTIVE_ROUTE,
			HUNTER_ACTIVE_PAGE,
			"hunter audience route"
		);
		assertRoute(
			resolve(
				initial,
				game,
				context(ACTIVE, hunter, TASK, PlayerTaskState.PAUSED),
				true
			),
			HUNTER_TASK_ROUTE,
			HUNTER_TASK_PAGE,
			"hunter paused-task route"
		);
		assertRoute(
			resolve(
				initial,
				game,
				context(ACTIVE, hunter, TASK, PlayerTaskState.RUNNING),
				true
			),
			HUNTER_ACTIVE_ROUTE,
			HUNTER_ACTIVE_PAGE,
			"wrong hunter task state must not leak the task page"
		);

		Resolution fallback = resolve(initial, game, context(ENDED, runner), true);
		check(fallback.kind() == Kind.FALLBACK, "unmatched routes must use the game fallback");
		check(
			fallback.routeId().isEmpty() && fallback.pageId().equals(Optional.of(HOME_PAGE)),
			"fallback must expose only the configured default page"
		);

		Resolution predicateDenied = resolve(initial, game, context(ACTIVE, runner), false);
		check(
			!predicateDenied.routeId().equals(Optional.of(RUNNER_PREDICATE_ROUTE)),
			"a false server predicate must fail closed rather than reveal its route"
		);

		Resolution host = PlayerTerminalRouter.resolve(
			initial,
			game,
			new Context(
				GAME,
				ACTIVE,
				player(HOST_ID, "Host", RUNNER),
				Optional.of(HOST_ID),
				true,
				Optional.empty(),
				Optional.empty()
			),
			PlayerTerminalRouter.PredicateEvaluator.allowAll()
		);
		check(
			host.kind() == Kind.HOST && host.pageId().isEmpty(),
			"the authoritative host must be diverted before ordinary-terminal routing"
		);

		Resolution wrongGame = PlayerTerminalRouter.resolve(
			initial,
			game,
			new Context(
				OTHER_GAME,
				ACTIVE,
				runner,
				Optional.empty(),
				true,
				Optional.empty(),
				Optional.empty()
			),
			PlayerTerminalRouter.PredicateEvaluator.allowAll()
		);
		check(
			wrongGame.kind() == Kind.UNAVAILABLE && wrongGame.pageId().isEmpty(),
			"a mismatched game context must fail closed"
		);

		expectThrows(
			IllegalArgumentException.class,
			() -> new Context(
				GAME,
				ACTIVE,
				runner,
				Optional.empty(),
				true,
				Optional.empty(),
				Optional.of(PlayerTaskState.RUNNING)
			),
			"task state without a task must be rejected at the context boundary"
		);

		// The same stable route ID may resolve to a different registered page after reload. The
		// generation and page must both come from the newly compiled snapshot.
		DefinitionSnapshot reloaded = compile(sources(true), 42L, Set.of(GATE));
		Resolution reloadedRoute = resolve(
			reloaded,
			reloaded.games().get(GAME),
			context(ACTIVE, runner),
			false
		);
		assertRoute(
			reloadedRoute,
			RUNNER_ACTIVE_ROUTE,
			RUNNER_RELOADED_PAGE,
			"reloaded route"
		);
		check(
			initial.generation() == 41L && reloaded.generation() == 42L,
			"definition reload must publish the requested generation"
		);

		List<Source> duplicatePriority = new ArrayList<>(sources(false));
		duplicatePriority.add(
			route(
				"route/duplicate_priority",
				"test:page/home",
				200,
				"{\"roles\":[\"test:hunter\"]}",
				""
			)
		);
		Compilation duplicateCompilation = DefinitionCompiler.compile(
			duplicatePriority,
			Set.of(),
			Set.of(GATE)
		);
		check(
			!duplicateCompilation.valid()
				&& duplicateCompilation.problems()
					.stream()
					.anyMatch(problem -> problem.code().equals("DUPLICATE_PRIORITY")),
			"duplicate route priorities in one game must be rejected"
		);

		List<Source> unknownPredicate = new ArrayList<>(sources(false));
		unknownPredicate.add(
			route(
				"route/unknown_predicate",
				"test:page/home",
				999,
				"{\"roles\":[\"test:runner\"]}",
				",\"predicate\":\"test:missing_gate\""
			)
		);
		Compilation unknownPredicateCompilation = DefinitionCompiler.compile(
			unknownPredicate,
			Set.of(),
			Set.of(GATE)
		);
		check(
			!unknownPredicateCompilation.valid(),
			"an unregistered route predicate must fail the data-pack reload"
		);

		System.out.println("PlayerTerminalRouterSelfCheck PASS");
	}

	private static Resolution resolve(
		final DefinitionSnapshot definitions,
		final io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition game,
		final Context context,
		final boolean predicateResult
	) {
		return PlayerTerminalRouter.resolve(
			definitions,
			game,
			context,
			(predicate, player) -> predicate.equals(GATE) && predicateResult
		);
	}

	private static Context context(final Identifier phase, final PlayerRecord player) {
		return new Context(
			GAME,
			phase,
			player,
			Optional.empty(),
			true,
			Optional.empty(),
			Optional.empty()
		);
	}

	private static Context context(
		final Identifier phase,
		final PlayerRecord player,
		final Identifier task,
		final PlayerTaskState state
	) {
		return new Context(
			GAME,
			phase,
			player,
			Optional.empty(),
			true,
			Optional.of(task),
			Optional.of(state)
		);
	}

	private static Context contextWithTaskButNoState(
		final Identifier phase,
		final PlayerRecord player,
		final Identifier task
	) {
		return new Context(
			GAME,
			phase,
			player,
			Optional.empty(),
			true,
			Optional.of(task),
			Optional.empty()
		);
	}

	private static PlayerRecord player(
		final UUID playerId,
		final String name,
		final Identifier role
	) {
		return new PlayerRecord(
			playerId,
			name,
			role,
			Optional.empty(),
			ALIVE,
			Map.of(),
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			0L,
			0L
		);
	}

	private static DefinitionSnapshot compile(
		final List<Source> sources,
		final long generation,
		final Set<Identifier> predicates
	) {
		Compilation compilation = DefinitionCompiler.compile(sources, Set.of(), predicates);
		check(
			compilation.valid(),
			"terminal router fixture must compile: " + compilation.problems()
		);
		return compilation.snapshot().orElseThrow().withGeneration(generation);
	}

	private static List<Source> sources(final boolean reloaded) {
		List<Source> result = new ArrayList<>();
		result.add(
			source(
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 3,
				  "content_version": 1,
				  "name": {"text": "玩家终端路由自检"},
				  "initial_phase": "test:lobby",
				  "default_role": "test:runner",
				  "default_life_state": "test:alive",
				  "task_timeline": {
				    "initial_task": "test:task",
				    "approval_phase": "test:lobby",
				    "start_phase": "test:active"
				  },
				  "player_terminal": {
				    "default_page": "test:page/home",
				    "history_enabled": true
				  }
				}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.ROLE,
				"runner",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"逃走者"}}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.ROLE,
				"hunter",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"猎人"}}
				"""
			)
		);
		result.add(
			source(
				DefinitionType.LIFE_STATE,
				"alive",
				"""
				{"format_version":1,"game":"test:main","name":{"text":"存活"}}
				"""
			)
		);
		result.add(phase("lobby", "大厅"));
		result.add(phase("active", "进行中"));
		result.add(phase("ended", "已结束"));

		result.add(page("page/home", "HOME"));
		result.add(page("page/runner_lobby", "RUNNER LOBBY"));
		result.add(page("page/runner_active", "RUNNER ACTIVE"));
		result.add(page("page/runner_reloaded", "RUNNER RELOADED"));
		result.add(page("page/runner_predicate", "RUNNER PREDICATE"));
		result.add(page("page/runner_task", "RUNNER TASK"));
		result.add(page("page/hunter_active", "HUNTER ACTIVE"));
		result.add(page("page/hunter_task", "HUNTER TASK"));
		result.add(
			source(
				DefinitionType.TASK,
				"task",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "kind": "main",
				  "name": {"text": "路由任务"},
				  "description": {"text": "用于验证任务状态路由"},
				  "completion_policy": "event_only",
				  "results": [{
				    "id": "done",
				    "name": {"text": "完成"},
				    "semantic": "success",
				    "route": {"end_phase": "test:ended"}
				  }]
				}
				"""
			)
		);

		result.add(
			route(
				"route/runner_lobby",
				"test:page/runner_lobby",
				100,
				"{\"roles\":[\"test:runner\"]}",
				",\"phases\":[\"test:lobby\"]"
			)
		);
		result.add(
			route(
				"route/runner_active",
				reloaded
					? "test:page/runner_reloaded"
					: "test:page/runner_active",
				200,
				"{\"roles\":[\"test:runner\"]}",
				",\"phases\":[\"test:active\"]"
			)
		);
		result.add(
			route(
				"route/runner_predicate",
				"test:page/runner_predicate",
				300,
				"{\"roles\":[\"test:runner\"]}",
				",\"phases\":[\"test:active\"],\"predicate\":\"test:route_gate\""
			)
		);
		result.add(
			route(
				"route/runner_task",
				"test:page/runner_task",
				-100,
				"{\"roles\":[\"test:runner\"]}",
				",\"phases\":[\"test:active\"],\"tasks\":[\"test:task\"],\"task_states\":[\"running\"]"
			)
		);
		result.add(
			route(
				"route/hunter_active",
				"test:page/hunter_active",
				250,
				"{\"roles\":[\"test:hunter\"]}",
				",\"phases\":[\"test:active\"]"
			)
		);
		result.add(
			route(
				"route/hunter_task",
				"test:page/hunter_task",
				-90,
				"{\"roles\":[\"test:hunter\"]}",
				",\"phases\":[\"test:active\"],\"tasks\":[\"test:task\"],\"task_states\":[\"paused\"]"
			)
		);
		return List.copyOf(result);
	}

	private static Source phase(final String path, final String name) {
		return source(
			DefinitionType.PHASE,
			path,
			"""
			{"format_version":1,"game":"test:main","name":{"text":"%s"},"transitions":[]}
			""".formatted(name)
		);
	}

	private static Source page(final String path, final String text) {
		return source(
			DefinitionType.PAGE,
			path,
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "title": {"text": "%s"},
			  "root": {
			    "type": "column",
			    "children": [{"type":"text","text":{"text":"%s"}}]
			  }
			}
			""".formatted(text, text)
		);
	}

	private static Source route(
		final String path,
		final String page,
		final int priority,
		final String audience,
		final String suffix
	) {
		return source(
			DefinitionType.PLAYER_ROUTE,
			path,
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "page": "%s",
			  "priority": %d,
			  "audience": %s
			  %s
			}
			""".formatted(page, priority, audience, suffix)
		);
	}

	private static Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		Identifier id = id(path);
		return new Source(
			type,
			id,
			Identifier.fromNamespaceAndPath(
				"test",
				"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
			),
			"player-terminal-router-self-check",
			json
		);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("test", path);
	}

	private static void assertRoute(
		final Resolution resolution,
		final Identifier expectedRoute,
		final Identifier expectedPage,
		final String label
	) {
		check(
			resolution.kind() == Kind.ROUTE
				&& resolution.routeId().equals(Optional.of(expectedRoute))
				&& resolution.pageId().equals(Optional.of(expectedPage)),
			label + " selected " + resolution
		);
	}

	private static void expectThrows(
		final Class<? extends Throwable> type,
		final Runnable action,
		final String message
	) {
		try {
			action.run();
		} catch (Throwable thrown) {
			if (type.isInstance(thrown)) {
				return;
			}
			throw new AssertionError(message + ": wrong exception " + thrown, thrown);
		}
		throw new AssertionError(message + ": no exception");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
