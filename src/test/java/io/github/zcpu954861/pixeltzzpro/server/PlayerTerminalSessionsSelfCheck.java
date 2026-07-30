package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.MutationResult;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.NavigationGrant;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.OpenResult;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.SequenceStatus;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.Session;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable, serverless checks for the ordinary-player terminal navigation session.
 */
public final class PlayerTerminalSessionsSelfCheck {
	private static final UUID PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000401");
	private static final UUID OTHER_PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000402");
	private static final UUID ROLLBACK_PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000403");
	private static final UUID TRUNCATE_PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000404");
	private static final UUID SAME_PAGE_HISTORY_PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000405");
	private static final Identifier ROUTE = id("route/home");
	private static final Identifier RELOADED_ROUTE = id("route/task");
	private static final Identifier HOME = id("page/home");
	private static final Identifier HELP = id("page/help");
	private static final Identifier TASK = id("page/task");
	private static final Identifier OPEN_HELP = id("action/open_help");
	private static final Identifier OPEN_TASK = id("action/open_task");

	private PlayerTerminalSessionsSelfCheck() {
	}

	public static void main(final String[] arguments) {
		PlayerTerminalSessions sessions = new PlayerTerminalSessions();
		OpenResult opened = sessions.open(
			PLAYER,
			7L,
			11L,
			Optional.of(ROUTE),
			HOME,
			true,
			100L
		);
		check(opened.successful(), "ordinary terminal must open");
		Session original = opened.session().orElseThrow();
		check(
			original.definitionGeneration() == 7L
				&& original.stateRevision() == 11L
				&& original.routeRevision() == 0L
				&& original.nextRequestSequence() == 0L
				&& original.stack().size() == 1
				&& original.current().pageId().equals(HOME)
				&& original.current().navigationGrant().isEmpty()
				&& original.current().historyRecord().isEmpty(),
			"open must bind the initial route, revision, and root frame"
		);

		check(
			sessions.acceptSequence(
				PLAYER,
				UUID.fromString("00000000-0000-0000-0000-000000000499"),
				0L,
				101L
			).status() == SequenceStatus.SESSION_MISMATCH,
			"a request from a stale session must fail closed"
		);
		check(
			sessions.acceptSequence(PLAYER, original.sessionId(), 1L, 101L).status()
				== SequenceStatus.OUT_OF_ORDER,
			"a future sequence must be rejected"
		);
		Session sequenced = sessions.acceptSequence(
			PLAYER,
			original.sessionId(),
			0L,
			102L
		).session().orElseThrow();
		check(
			sequenced.nextRequestSequence() == 1L
				&& sequenced.lastUserActivityTick() == 102L,
			"the exact next sequence must advance once"
		);
		check(
			sessions.acceptSequence(PLAYER, original.sessionId(), 0L, 103L).status()
				== SequenceStatus.REPLAYED,
			"a repeated sequence must be rejected"
		);

		UUID originalInstance = original.current().instanceId();
		check(
			!sessions.pushAuthorized(
				PLAYER,
				HELP,
				UUID.fromString("00000000-0000-0000-0000-000000000498"),
				"open_help",
				OPEN_HELP,
				12L
			).successful()
				&& sessions.find(PLAYER).orElseThrow().equals(sequenced),
			"a stale source instance must reject without mutating the stack"
		);
		MutationResult pushedResult = sessions.pushAuthorized(
			PLAYER,
			HELP,
			sequenced.current().instanceId(),
			"open_help",
			OPEN_HELP,
			12L
		);
		check(pushedResult.successful(), "a registered page may be pushed");
		Session pushed = pushedResult.session().orElseThrow();
		NavigationGrant helpGrant = pushed.current().navigationGrant().orElseThrow();
		check(
			pushed.stack().size() == 2
				&& pushed.current().pageId().equals(HELP)
				&& !pushed.current().instanceId().equals(originalInstance)
				&& pushed.stateRevision() == 12L
				&& pushed.nextRequestSequence() == 1L
				&& helpGrant.sourcePageId().equals(HOME)
				&& helpGrant.sourcePageInstanceId().equals(originalInstance)
				&& helpGrant.sourceNodeId().equals("open_help")
				&& helpGrant.actionId().equals(OPEN_HELP)
				&& pushed.current().historyRecord().isEmpty(),
			"push must create a fresh instance without resetting request sequence"
		);

		UUID helpInstance = pushed.current().instanceId();
		List<PlayerTerminalSessions.Frame> preReloadStack = pushed.stack();
		MutationResult reissuedResult = sessions.reissue(
			PLAYER,
			8L,
			13L,
			false
		);
		check(reissuedResult.successful(), "reload refresh must reissue the current page");
		Session reissued = reissuedResult.session().orElseThrow();
		check(
			reissued.sessionId().equals(original.sessionId())
				&& reissued.definitionGeneration() == 8L
				&& reissued.stateRevision() == 13L
				&& reissued.routeRevision() == 0L
				&& reissued.nextRequestSequence() == 1L
				&& reissued.stack().size() == preReloadStack.size()
				&& reissued.stack().getFirst().equals(preReloadStack.getFirst())
				&& reissued.current().pageId().equals(HELP)
				&& !reissued.current().instanceId().equals(helpInstance)
				&& reissued.current().navigationGrant().equals(Optional.of(helpGrant))
				&& reissued.current().historyRecord().isEmpty()
				&& !reissued.takeoverAllowed(),
			"same-route reload must reissue only the visible frame and retain its grant"
		);

		UUID reissuedInstance = reissued.current().instanceId();
		MutationResult projectionResult = sessions.touchProjection(
			PLAYER,
			14L,
			true
		);
		Session projection = projectionResult.session().orElseThrow();
		check(
			projection.definitionGeneration() == 8L
				&& projection.stateRevision() == 14L
				&& projection.routeRevision() == 0L
				&& projection.current().instanceId().equals(reissuedInstance)
				&& projection.stack().equals(reissued.stack())
				&& projection.takeoverAllowed()
				&& projection.lastUserActivityTick() == 102L,
			"a binding-only revision must not rebuild the page, route, or idle clock"
		);

		MutationResult backResult = sessions.back(PLAYER, 15L);
		check(backResult.successful(), "back must pop a non-root frame");
		Session backed = backResult.session().orElseThrow();
		check(
			backed.stack().size() == 1
				&& backed.current().pageId().equals(HOME)
				&& !backed.current().instanceId().equals(originalInstance)
				&& backed.definitionGeneration() == 8L
				&& backed.stateRevision() == 15L
				&& backed.nextRequestSequence() == 1L,
			"back must issue a new instance even when returning to the same data-pack page"
		);
		check(
			!sessions.back(PLAYER, 16L).successful(),
			"back at the root must fail closed"
		);

		UUID preRouteReplaceInstance = backed.current().instanceId();
		MutationResult routeResult = sessions.replaceRoute(
			PLAYER,
			9L,
			16L,
			Optional.of(RELOADED_ROUTE),
			TASK,
			false
		);
		check(routeResult.successful(), "an authoritative route change must replace the stack");
		Session rerouted = routeResult.session().orElseThrow();
		check(
			rerouted.sessionId().equals(original.sessionId())
				&& rerouted.definitionGeneration() == 9L
				&& rerouted.stateRevision() == 16L
				&& rerouted.routeRevision() == 1L
				&& rerouted.nextRequestSequence() == 1L
				&& rerouted.routeId().equals(Optional.of(RELOADED_ROUTE))
				&& rerouted.rootPage().equals(TASK)
				&& rerouted.stack().size() == 1
				&& rerouted.current().pageId().equals(TASK)
				&& rerouted.current().navigationGrant().isEmpty()
				&& rerouted.current().historyRecord().isEmpty()
				&& !rerouted.current().instanceId().equals(preRouteReplaceInstance),
			"route replacement must reset navigation and advance only the route revision"
		);

		for (int depth = 1; depth < PlayerTerminalSessions.MAX_STACK_DEPTH; depth++) {
			Session source = sessions.find(PLAYER).orElseThrow();
			MutationResult result = sessions.pushAuthorized(
				PLAYER,
				id("page/depth_" + depth),
				source.current().instanceId(),
				"open_depth_" + depth,
				id("action/open_depth_" + depth),
				16L + depth
			);
			check(result.successful(), "stack depth " + (depth + 1) + " must remain legal");
		}
		Session fullStack = sessions.find(PLAYER).orElseThrow();
		check(
			fullStack.stack().size() == PlayerTerminalSessions.MAX_STACK_DEPTH,
			"session must reach the documented stack limit"
		);
		check(
			!sessions.pushAuthorized(
				PLAYER,
				id("page/overflow"),
				fullStack.current().instanceId(),
				"open_overflow",
				id("action/open_overflow"),
				100L
			).successful()
				&& sessions.find(PLAYER).orElseThrow().equals(fullStack),
			"push beyond the maximum stack must reject without mutating the session"
		);

		check(
			!sessions.close(PLAYER, preRouteReplaceInstance)
				&& sessions.find(PLAYER).isPresent(),
			"a close for a stale page instance must not close the current terminal"
		);
		UUID currentInstance = sessions.find(PLAYER).orElseThrow().current().instanceId();
		check(
			sessions.close(PLAYER, currentInstance) && sessions.find(PLAYER).isEmpty(),
			"a close matching the visible instance must remove the session"
		);

		check(
			!sessions.open(
				PLAYER,
				-1L,
				0L,
				Optional.empty(),
				HOME,
				false,
				0L
			).successful(),
			"negative session revisions must be rejected"
		);

		sessions.open(
			OTHER_PLAYER,
			1L,
			1L,
			Optional.empty(),
			HOME,
			false,
			500L
		);
		check(
			sessions.expire(
				500L + PlayerTerminalSessions.IDLE_EXPIRY_TICKS - 1L
			).isEmpty(),
			"a session must survive until the idle boundary"
		);
		Session backgroundRefreshed = sessions.touchProjection(
			OTHER_PLAYER,
			2L,
			true
		).session().orElseThrow();
		check(
			backgroundRefreshed.lastUserActivityTick() == 500L,
			"background binding deltas must not renew the user-activity clock"
		);
		check(
			sessions.expire(
				500L + PlayerTerminalSessions.IDLE_EXPIRY_TICKS
			).equals(List.of(OTHER_PLAYER))
				&& sessions.find(OTHER_PLAYER).isEmpty(),
			"a session must expire exactly at the idle boundary"
		);

		sessions.open(
			ROLLBACK_PLAYER,
			1L,
			1L,
			Optional.empty(),
			HOME,
			false,
			900L
		);
		check(
			sessions.expire(899L).equals(List.of(ROLLBACK_PLAYER)),
			"a backwards server tick must invalidate the session rather than extend it"
		);

		sessions.open(
			OTHER_PLAYER,
			20L,
			30L,
			Optional.of(ROUTE),
			HOME,
			false,
			1_000L
		);
		MutationResult detailPushed = sessions.pushHistoryDetail(
			OTHER_PLAYER,
			HELP,
			"task-instance/checkpoint/0",
			31L
		);
		check(
			detailPushed.successful()
				&& detailPushed.session()
					.orElseThrow()
					.current()
					.navigationGrant()
					.isEmpty()
				&& detailPushed.session()
					.orElseThrow()
					.current()
					.historyRecord()
					.orElseThrow()
					.recordKey()
					.equals("task-instance/checkpoint/0"),
			"history detail push must retain only the server-resolved record reference"
		);
		Session detailReissued = sessions.reissue(
			OTHER_PLAYER,
			20L,
			32L,
			false
		).session().orElseThrow();
		check(
			detailReissued.current()
				.historyRecord()
				.orElseThrow()
				.recordKey()
				.equals("task-instance/checkpoint/0"),
			"binding refresh must preserve the authoritative history record reference"
		);
		Session detailBack = sessions.back(
			OTHER_PLAYER,
			33L
		).session().orElseThrow();
		check(
			detailBack.current().pageId().equals(HOME)
				&& detailBack.current().navigationGrant().isEmpty()
				&& detailBack.current().historyRecord().isEmpty(),
			"back from history detail must restore the original list frame"
		);

		checkSamePageHistorySelection(sessions);
		checkTruncationAndAnchorConstraints(sessions);

		sessions.clear();
		check(sessions.snapshot().isEmpty(), "clear must leave no terminal sessions");
		System.out.println("PlayerTerminalSessionsSelfCheck PASS");
	}

	private static void checkSamePageHistorySelection(
		final PlayerTerminalSessions sessions
	) {
		Session root = sessions.open(
			SAME_PAGE_HISTORY_PLAYER,
			21L,
			50L,
			Optional.of(ROUTE),
			HOME,
			false,
			1_500L
		).session().orElseThrow();
		MutationResult rootSelection = sessions.pushHistoryDetail(
			SAME_PAGE_HISTORY_PLAYER,
			HOME,
			"task-instance/root-selection",
			51L
		);
		check(
			!rootSelection.successful()
				&& sessions.find(SAME_PAGE_HISTORY_PLAYER).orElseThrow().equals(root),
			"the root frame must reject same-page history state without mutating the session"
		);

		Session historyList = sessions.pushAuthorized(
			SAME_PAGE_HISTORY_PLAYER,
			HELP,
			root.current().instanceId(),
			"open_history",
			OPEN_HELP,
			52L
		).session().orElseThrow();
		NavigationGrant listGrant =
			historyList.current().navigationGrant().orElseThrow();
		UUID listInstance = historyList.current().instanceId();
		Session selected = sessions.pushHistoryDetail(
			SAME_PAGE_HISTORY_PLAYER,
			HELP,
			"task-instance/checkpoint/same-page",
			53L
		).session().orElseThrow();
		check(
			selected.stack().size() == historyList.stack().size()
				&& selected.stack().size() == 2
				&& selected.current().pageId().equals(HELP)
				&& !selected.current().instanceId().equals(listInstance)
				&& selected.current().navigationGrant().equals(Optional.of(listGrant))
				&& selected.current().historyRecord()
					.orElseThrow()
					.recordKey()
					.equals("task-instance/checkpoint/same-page")
				&& selected.stateRevision() == 53L,
			"same-page history selection must replace the list frame, preserve its navigation grant, and issue a fresh instance"
		);

		Session reissued = sessions.reissue(
			SAME_PAGE_HISTORY_PLAYER,
			21L,
			54L,
			false
		).session().orElseThrow();
		check(
			reissued.stack().size() == 2
				&& reissued.current().navigationGrant().equals(Optional.of(listGrant))
				&& reissued.current().historyRecord()
					.equals(selected.current().historyRecord()),
			"same-page history reissue must preserve both authorization anchors"
		);

		Session returned = sessions.back(
			SAME_PAGE_HISTORY_PLAYER,
			55L
		).session().orElseThrow();
		check(
			returned.stack().size() == 1
				&& returned.current().pageId().equals(HOME)
				&& returned.current().navigationGrant().isEmpty()
				&& returned.current().historyRecord().isEmpty(),
			"back from a same-page history detail must return directly to the terminal home"
		);
	}

	private static void checkTruncationAndAnchorConstraints(
		final PlayerTerminalSessions sessions
	) {
		Session root = sessions.open(
			TRUNCATE_PLAYER,
			30L,
			40L,
			Optional.of(ROUTE),
			HOME,
			false,
			2_000L
		).session().orElseThrow();
		Session sequenced = sessions.acceptSequence(
			TRUNCATE_PLAYER,
			root.sessionId(),
			0L,
			2_001L
		).session().orElseThrow();
		Session help = sessions.pushAuthorized(
			TRUNCATE_PLAYER,
			HELP,
			sequenced.current().instanceId(),
			"open_help",
			OPEN_HELP,
			41L
		).session().orElseThrow();
		NavigationGrant helpGrant = help.current().navigationGrant().orElseThrow();
		UUID helpInstance = help.current().instanceId();
		Session task = sessions.pushAuthorized(
			TRUNCATE_PLAYER,
			TASK,
			helpInstance,
			"open_task",
			OPEN_TASK,
			42L
		).session().orElseThrow();

		check(
			!sessions.truncateToDepth(TRUNCATE_PLAYER, 0, 43L, true).successful()
				&& !sessions.truncateToDepth(
					TRUNCATE_PLAYER,
					task.stack().size() + 1,
					43L,
					true
				).successful()
				&& sessions.find(TRUNCATE_PLAYER).orElseThrow().equals(task),
			"invalid retained depths must reject without mutating the session"
		);

		Session truncated = sessions.truncateToDepth(
			TRUNCATE_PLAYER,
			2,
			43L,
			true
		).session().orElseThrow();
		check(
			truncated.sessionId().equals(root.sessionId())
				&& truncated.definitionGeneration() == 30L
				&& truncated.stateRevision() == 43L
				&& truncated.routeRevision() == 0L
				&& truncated.nextRequestSequence() == 1L
				&& truncated.lastUserActivityTick() == 2_001L
				&& truncated.takeoverAllowed()
				&& truncated.stack().size() == 2
				&& truncated.current().pageId().equals(HELP)
				&& !truncated.current().instanceId().equals(helpInstance)
				&& truncated.current().navigationGrant().equals(Optional.of(helpGrant))
				&& truncated.current().historyRecord().isEmpty(),
			"truncation must retain the longest legal prefix and reissue its visible frame"
		);

		PlayerTerminalSessions.HistoryRecordRef history =
			new PlayerTerminalSessions.HistoryRecordRef("history/checkpoint");
		PlayerTerminalSessions.Frame combined =
			new PlayerTerminalSessions.Frame(
				HELP,
				UUID.randomUUID(),
				Optional.of(helpGrant),
				Optional.of(history)
			);
		Session combinedSession = new Session(
			TRUNCATE_PLAYER,
			UUID.randomUUID(),
			1L,
			1L,
			0L,
			0L,
			Optional.of(ROUTE),
			HOME,
			List.of(
				new PlayerTerminalSessions.Frame(
					HOME,
					helpGrant.sourcePageInstanceId()
				),
				combined
			),
			false,
			1L
		);
		check(
			combinedSession.current().navigationGrant().equals(Optional.of(helpGrant))
				&& combinedSession.current().historyRecord().equals(Optional.of(history)),
			"a non-root frame may combine its navigation grant with a history record"
		);
		expectIllegalArgument(
			() -> new NavigationGrant(
				HOME,
				UUID.randomUUID(),
				" ",
				OPEN_HELP
			),
			"a navigation grant must reject a blank source node"
		);
		expectIllegalArgument(
			() -> new Session(
				TRUNCATE_PLAYER,
				UUID.randomUUID(),
				1L,
				1L,
				0L,
				0L,
				Optional.of(ROUTE),
				HOME,
				List.of(
					new PlayerTerminalSessions.Frame(HOME, UUID.randomUUID()),
					new PlayerTerminalSessions.Frame(HELP, UUID.randomUUID())
				),
				false,
				1L
			),
			"a non-root frame must carry at least one authorization anchor"
		);
		NavigationGrant mismatched = new NavigationGrant(
			TASK,
			UUID.randomUUID(),
			"open_help",
			OPEN_HELP
		);
		expectIllegalArgument(
			() -> new Session(
				TRUNCATE_PLAYER,
				UUID.randomUUID(),
				1L,
				1L,
				0L,
				0L,
				Optional.of(ROUTE),
				HOME,
				List.of(
					new PlayerTerminalSessions.Frame(HOME, UUID.randomUUID()),
					new PlayerTerminalSessions.Frame(
						HELP,
						UUID.randomUUID(),
						Optional.of(mismatched),
						Optional.empty()
					)
				),
				false,
				1L
			),
			"a navigation grant must identify its immediate parent frame"
		);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("test", path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void expectIllegalArgument(
		final Runnable action,
		final String message
	) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}
}
