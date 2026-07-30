package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Confirmation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.AuditPolicy;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.CooldownScope;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.ExecutionContext;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionFeedback;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTerminalConfig;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.UsageScope;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Breakpoints;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StaticText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiAction;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ActionRequest;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Code;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ConfirmedRequest;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ConfirmationChallenge;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ExecutionResult;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Finalization;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Preparation;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.RunFunction;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.SessionAuthorization;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.Session;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionInvocationStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Cross-layer regression checks for the ordinary-player terminal action bridge.
 *
 * <p>The executable half uses the real session and action-authority implementations. Minecraft's
 * server/player/network objects cannot be constructed by a serverless {@code JavaExec}, so the
 * Fabric runtime and client-screen half is protected by source contracts against the actual
 * registered bridge methods. This deliberately avoids introducing a second action coordinator
 * solely for tests.</p>
 */
public final class PlayerTerminalRuntimeBridgeSelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier PHASE = id("phase/lobby");
	private static final Identifier ROLE = id("role/runner");
	private static final Identifier LIFE = id("life/alive");
	private static final Identifier PAGE = id("page/player");
	private static final Identifier FUNCTION = id("function/player_action");
	private static final Identifier DIRECT_ACTION = id("action/direct");
	private static final Identifier CONFIRMED_ACTION = id("action/confirmed");
	private static final Identifier ROUTE = id("route/home");
	private static final Identifier OTHER_ROUTE = id("route/changed");
	private static final long GENERATION = 11L;
	private static final UUID GAME_INSTANCE =
		UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID PLAYER =
		UUID.fromString("30000000-0000-0000-0000-000000000002");

	private PlayerTerminalRuntimeBridgeSelfCheck() {
	}

	public static void main(final String[] arguments) throws IOException {
		checkExecutableSessionActionChain();
		checkInterleavedTerminalIntentInvalidatesConfirmation();
		checkTerminalHostControlSequenceGate();
		checkHistoryDetailRebinding();
		checkDiagnosticRedaction();
		checkServerRuntimeBridgeSource();
		checkClientBridgeSource();
		System.out.println("PLAYER_TERMINAL_RUNTIME_BRIDGE_SELF_CHECK=PASS");
	}

	private static void checkDiagnosticRedaction() {
		String sensitive = "MISSING_REFERENCE at pixel_tzz_pro/player_routes/internal.json";
		check(
			PixelTzzServerRuntime.sessionDiagnosticForViewer(true, false, sensitive)
				.equals(sensitive),
			"the current host must retain the detailed data-pack diagnostic"
		);
		String ordinaryHealthy = PixelTzzServerRuntime.sessionDiagnosticForViewer(
			false,
			true,
			sensitive
		);
		String ordinaryBlocked = PixelTzzServerRuntime.sessionDiagnosticForViewer(
			false,
			false,
			sensitive
		);
		check(
			ordinaryHealthy.equals("数据包定义已就绪。")
				&& ordinaryBlocked.equals("数据包定义当前不可用，请联系主持人查看详情。")
				&& !ordinaryHealthy.contains("pixel_tzz_pro")
				&& !ordinaryBlocked.contains("pixel_tzz_pro"),
			"ordinary players and non-host OPs must receive only stable generic diagnostics"
		);
		check(
			PixelTzzServerRuntime.terminalProjectionFailureMessage("binding_limit")
				.equals("玩家终端公开数据超过安全上限，页面已关闭。")
				&& PixelTzzServerRuntime.terminalProjectionFailureMessage(
					"frozen_snapshot_invalid"
				).equals("当前游戏的冻结定义无法安全恢复，玩家终端已关闭。")
				&& PixelTzzServerRuntime.terminalProjectionFailureMessage(
					"projection_failed"
				).equals("玩家终端公开数据生成失败，页面已关闭。"),
			"closed projection statuses must have stable, explicit client failure messages"
		);
	}

	private static void checkInterleavedTerminalIntentInvalidatesConfirmation() {
		WorldStateV4 state = state();
		PlayerTerminalSessions sessions = new PlayerTerminalSessions();
		sessions.open(
			PLAYER,
			GENERATION,
			state.stateRevision(),
			Optional.of(ROUTE),
			PAGE,
			false,
			200L
		).session().orElseThrow();

		Attempt confirmationAttempt = attempt(
			sessions,
			state,
			CONFIRMED_ACTION,
			"confirmed",
			requestId(40L),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			201L
		);
		ConfirmationChallenge challenge = confirmationAttempt.preparation()
			.confirmation()
			.orElseThrow();
		Session awaitingCommit = sessions.find(PLAYER).orElseThrow();
		check(
			PixelTzzServerRuntime.playerActionConfirmationSequenceIsCurrent(
				awaitingCommit,
				challenge
			),
			"a freshly issued confirmation must be the immediate successor of its accepted intent"
		);

		long interveningSequence = awaitingCommit.nextRequestSequence();
		PlayerTerminalSessions.SequenceResult interleaved = sessions.acceptSequence(
			PLAYER,
			awaitingCommit.sessionId(),
			interveningSequence,
			202L
		);
		check(
			interleaved.status() == PlayerTerminalSessions.SequenceStatus.ACCEPTED,
			"the test must insert one valid later terminal intent"
		);
		Session afterInterleaving = interleaved.session().orElseThrow();
		check(
			afterInterleaving.nextRequestSequence() == challenge.requestSequence() + 2L
				&& !PixelTzzServerRuntime.playerActionConfirmationSequenceIsCurrent(
					afterInterleaving,
					challenge
				),
			"an intervening terminal intent must make the old confirmation commit gate fail"
		);
	}

	private static void checkTerminalHostControlSequenceGate() {
		WorldStateV4 state = state();
		PlayerTerminalSessions sessions = new PlayerTerminalSessions();
		Session opened = sessions.open(
			PLAYER,
			GENERATION,
			state.stateRevision(),
			Optional.of(ROUTE),
			PAGE,
			true,
			220L
		).session().orElseThrow();
		long acceptedRequestSequence = opened.nextRequestSequence();
		Session accepted = sessions.acceptSequence(
			PLAYER,
			opened.sessionId(),
			acceptedRequestSequence,
			221L
		).session().orElseThrow();
		check(
			PixelTzzServerRuntime.terminalHostControlSequenceIsCurrent(
				accepted,
				opened.sessionId(),
				opened.current().instanceId(),
				opened.definitionGeneration(),
				opened.routeRevision(),
				acceptedRequestSequence
			),
			"a freshly accepted terminal host action must pass its strict sequence gate"
		);

		Session interleaved = sessions.acceptSequence(
			PLAYER,
			accepted.sessionId(),
			accepted.nextRequestSequence(),
			222L
		).session().orElseThrow();
		check(
			!PixelTzzServerRuntime.terminalHostControlSequenceIsCurrent(
				interleaved,
				opened.sessionId(),
				opened.current().instanceId(),
				opened.definitionGeneration(),
				opened.routeRevision(),
				acceptedRequestSequence
			),
			"a later accepted terminal intent must invalidate the older host confirmation"
		);
	}

	private static void checkHistoryDetailRebinding() {
		NodeDefinition button = new NodeDefinition(
			NodeType.BUTTON,
			Optional.of("open_history_detail"),
			LayoutDefinition.empty(),
			Optional.empty(),
			Map.of(),
			Optional.empty(),
			Map.of(),
			Map.of(),
			new ButtonContent(
				new StaticText(text("查看详情")),
				Optional.empty(),
				Optional.empty(),
				new UiAction(
					ActionType.HISTORY_DETAIL,
					Optional.empty(),
					Optional.empty()
				)
			),
			"/root/template"
		);
		NodeDefinition historyRepeat = new NodeDefinition(
			NodeType.REPEAT,
			Optional.of("history"),
			LayoutDefinition.empty(),
			Optional.empty(),
			Map.of(),
			Optional.empty(),
			Map.of(),
			Map.of(),
			new RepeatContent(
				new BindingValue("history.items"),
				new BindingValue("item.id"),
				64,
				28,
				button
			),
			"/root"
		);
		check(
			PixelTzzServerRuntime.historyDetailButtonMatches(
				historyRepeat,
				"open_history_detail",
				false
			),
			"server must rebind history_detail to the exact history.items Repeat template"
		);
		NodeDefinition genericRepeat = new NodeDefinition(
			NodeType.REPEAT,
			Optional.of("players"),
			LayoutDefinition.empty(),
			Optional.empty(),
			Map.of(),
			Optional.empty(),
			Map.of(),
			Map.of(),
			new RepeatContent(
				new BindingValue("session.players"),
				new BindingValue("item.uuid"),
				64,
				28,
				button
			),
			"/root"
		);
		check(
			!PixelTzzServerRuntime.historyDetailButtonMatches(
				genericRepeat,
				"open_history_detail",
				false
			)
				&& !PixelTzzServerRuntime.historyDetailButtonMatches(
					button,
					"open_history_detail",
					false
				),
			"server must reject history_detail outside the authoritative history Repeat scope"
		);
	}

	private static void checkExecutableSessionActionChain() {
		DefinitionSnapshot definitions = definitions();
		WorldStateV4 state = state();
		PlayerTerminalSessions sessions = new PlayerTerminalSessions();
		Session opened = sessions.open(
			PLAYER,
			GENERATION,
			state.stateRevision(),
			Optional.of(ROUTE),
			PAGE,
			false,
			100L
		).session().orElseThrow();

		Attempt directSuccess = attempt(
			sessions,
			state,
			DIRECT_ACTION,
			"direct",
			requestId(1L),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			101L
		);
		check(directSuccess.preparation().accepted(), "direct function action must prepare");
		check(
			directSuccess.preparation().prepared().orElseThrow().target()
				instanceof RunFunction run
				&& run.functionId().equals(FUNCTION),
			"direct action must rebind to the registered function"
		);
		state = finish(
			directSuccess.preparation(),
			ExecutionResult.succeeded("function_succeeded", "direct success")
		);
		sessions.touchProjection(PLAYER, state.stateRevision(), false);

		Attempt directFailure = attempt(
			sessions,
			state,
			DIRECT_ACTION,
			"direct",
			requestId(2L),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			103L
		);
		check(directFailure.preparation().accepted(), "failing function must still prepare once");
		WorldStateV4 committedFailure = commit(
			directFailure.preparation().nextState().orElseThrow()
		);
		Finalization failed = PlayerActionAuthority.finalizeInvocation(
			committedFailure,
			directFailure.preparation().prepared().orElseThrow(),
			ExecutionResult.failed("function_failed", "expected failure")
		);
		check(failed.finalized(), "function failure must finalize its durable invocation");
		check(
			failed.invocation().orElseThrow().status() == PlayerActionInvocationStatus.FAILED,
			"function failure must persist FAILED instead of reopening confirmation"
		);
		state = commit(failed.nextState().orElseThrow());
		sessions.touchProjection(PLAYER, state.stateRevision(), false);

		Attempt deniedBeforeConfirmation = attempt(
			sessions,
			state,
			CONFIRMED_ACTION,
			"confirmed",
			requestId(3L),
			(predicate, context) -> false,
			105L
		);
		check(
			deniedBeforeConfirmation.preparation().code() == Code.PREDICATE_DENIED
				&& deniedBeforeConfirmation.preparation().confirmation().isEmpty(),
			"a denied ordinary action must not enter the confirmation flow"
		);

		Attempt confirmationAttempt = attempt(
			sessions,
			state,
			CONFIRMED_ACTION,
			"confirmed",
			requestId(4L),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			106L
		);
		Preparation challengeResult = confirmationAttempt.preparation();
		check(
			challengeResult.code() == Code.CONFIRMATION_REQUIRED
				&& challengeResult.confirmation().isPresent()
				&& challengeResult.nextState().isEmpty(),
			"confirmation challenge must not reserve usage or invoke the function"
		);
		ConfirmationChallenge challenge = challengeResult.confirmation().orElseThrow();
		check(
			challenge.sessionId().equals(confirmationAttempt.authorization().sessionId())
				&& challenge.pageInstanceId().equals(
					confirmationAttempt.authorization().pageInstanceId()
				)
				&& challenge.nodeId().equals(confirmationAttempt.request().nodeId())
				&& challenge.actionId().equals(confirmationAttempt.request().actionId())
				&& challenge.requestId().equals(confirmationAttempt.request().requestId())
				&& challenge.requestSequence()
					== confirmationAttempt.request().requestSequence()
				&& challenge.definitionGeneration() == GENERATION
				&& challenge.routeRevision()
					== confirmationAttempt.authorization().routeRevision(),
			"confirmation must bind session, page instance, node, action, request UUID, and revisions"
		);
		check(
			confirmationAttempt.authorization().playerId().equals(PLAYER),
			"server-owned confirmation authorization must remain bound to the clicking player"
		);

		ConfirmedRequest forged = confirmed(challenge, requestId(999L));
		Preparation forgedResult = PlayerActionAuthority.prepare(
			state,
			definitions,
			confirmationAttempt.authorization(),
			confirmationAttempt.request(),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			106L,
			Optional.of(forged)
		);
		check(
			forgedResult.code() == Code.CONFIRMATION_INVALID,
			"a confirmation for another request UUID must fail closed"
		);

		ConfirmedRequest confirmed = confirmed(challenge, challenge.requestId());
		Preparation accepted = PlayerActionAuthority.prepare(
			state,
			definitions,
			confirmationAttempt.authorization(),
			confirmationAttempt.request(),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			106L,
			Optional.of(confirmed)
		);
		check(accepted.accepted(), "an exactly bound confirmation must prepare once");
		WorldStateV4 committedPrepared = commit(accepted.nextState().orElseThrow());

		/*
		 * Simulate an external function returning successfully while the final optimistic commit
		 * cannot be persisted. The durable PREPARED state is retained and the same request is
		 * outcome-unknown, so a runtime retry cannot execute the function a second time.
		 */
		Finalization uncommittedResult = PlayerActionAuthority.finalizeInvocation(
			committedPrepared,
			accepted.prepared().orElseThrow(),
			ExecutionResult.succeeded("function_succeeded", "result commit lost")
		);
		check(uncommittedResult.finalized(), "the final candidate must be derivable");
		Preparation unknown = PlayerActionAuthority.prepare(
			committedPrepared,
			definitions,
			confirmationAttempt.authorization(),
			confirmationAttempt.request(),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			107L,
			Optional.of(confirmed)
		);
		check(
			unknown.code() == Code.OUTCOME_UNKNOWN
				&& unknown.prepared().isEmpty()
				&& unknown.nextState().isEmpty(),
			"PREPARED after final-result commit failure must never be executable again"
		);

		state = commit(uncommittedResult.nextState().orElseThrow());
		Session beforeBindingDelta = sessions.touchProjection(
			PLAYER,
			state.stateRevision(),
			false
		).session().orElseThrow();
		Session bindingDelta = sessions.touchProjection(
			PLAYER,
			state.stateRevision() + 1L,
			true
		).session().orElseThrow();
		check(
			bindingDelta.sessionId().equals(beforeBindingDelta.sessionId())
				&& bindingDelta.current().instanceId().equals(
					beforeBindingDelta.current().instanceId()
				)
				&& bindingDelta.stack().equals(beforeBindingDelta.stack())
				&& bindingDelta.nextRequestSequence()
					== beforeBindingDelta.nextRequestSequence(),
			"a binding-only refresh must preserve page objects, navigation, and pending sequence"
		);

		ActionRequest staleRouteRequest = request(
			beforeBindingDelta,
			state,
			beforeBindingDelta.nextRequestSequence(),
			requestId(5L),
			"direct",
			DIRECT_ACTION
		);
		Session rerouted = sessions.replaceRoute(
			PLAYER,
			GENERATION,
			state.stateRevision(),
			Optional.of(OTHER_ROUTE),
			PAGE,
			false
		).session().orElseThrow();
		Preparation staleRoute = PlayerActionAuthority.prepare(
			state,
			definitions,
			authorization(rerouted, staleRouteRequest.requestSequence()),
			staleRouteRequest,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			110L
		);
		check(
			staleRoute.code() == Code.SESSION_STALE
				|| staleRoute.code() == Code.ROUTE_REVISION_STALE,
			"a route replacement must invalidate an old page request"
		);

		ActionRequest staleReloadRequest = request(
			rerouted,
			state,
			rerouted.nextRequestSequence(),
			requestId(6L),
			"direct",
			DIRECT_ACTION
		);
		Session reissued = sessions.reissue(
			PLAYER,
			GENERATION,
			state.stateRevision(),
			false
		).session().orElseThrow();
		Preparation staleReload = PlayerActionAuthority.prepare(
			state,
			definitions,
			authorization(reissued, staleReloadRequest.requestSequence()),
			staleReloadRequest,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			111L
		);
		check(
			staleReload.code() == Code.SESSION_STALE,
			"reload reissue must invalidate an old page-instance request"
		);

		sessions.remove(PLAYER);
		check(sessions.find(PLAYER).isEmpty(), "disconnect must remove the terminal session");
		check(
			!opened.current().instanceId().equals(reissued.current().instanceId()),
			"route/reload replacement must not accidentally reuse a page instance"
		);
	}

	private static Attempt attempt(
		final PlayerTerminalSessions sessions,
		final WorldStateV4 state,
		final Identifier actionId,
		final String nodeId,
		final UUID requestId,
		final PlayerActionAuthority.PredicateEvaluator predicates,
		final long worldTick
	) {
		Session before = sessions.find(PLAYER).orElseThrow();
		check(
			before.stateRevision() == state.stateRevision(),
			"test bridge session must be synchronized before accepting an intent"
		);
		long sequence = before.nextRequestSequence();
		ActionRequest request = request(
			before,
			state,
			sequence,
			requestId,
			nodeId,
			actionId
		);
		Session accepted = sessions.acceptSequence(
			PLAYER,
			before.sessionId(),
			sequence,
			worldTick
		).session().orElseThrow();
		check(
			accepted.nextRequestSequence() == sequence + 1L,
			"runtime sequence gate must advance exactly once"
		);
		SessionAuthorization authorization = authorization(accepted, sequence);
		Preparation preparation = PlayerActionAuthority.prepare(
			state,
			definitions(),
			authorization,
			request,
			predicates,
			worldTick
		);
		return new Attempt(authorization, request, preparation);
	}

	private static ActionRequest request(
		final Session session,
		final WorldStateV4 state,
		final long sequence,
		final UUID requestId,
		final String nodeId,
		final Identifier actionId
	) {
		return new ActionRequest(
			session.sessionId(),
			session.current().instanceId(),
			session.definitionGeneration(),
			state.stateRevision(),
			session.routeRevision(),
			sequence,
			requestId,
			nodeId,
			actionId
		);
	}

	private static SessionAuthorization authorization(
		final Session session,
		final long acceptedRequestSequence
	) {
		return new SessionAuthorization(
			GAME,
			PLAYER,
			session.sessionId(),
			session.current().instanceId(),
			session.current().pageId(),
			session.definitionGeneration(),
			session.stateRevision(),
			session.routeRevision(),
			acceptedRequestSequence,
			true
		);
	}

	private static ConfirmedRequest confirmed(
		final ConfirmationChallenge challenge,
		final UUID requestId
	) {
		return new ConfirmedRequest(
			challenge.sessionId(),
			challenge.pageInstanceId(),
			challenge.nodeId(),
			challenge.actionId(),
			requestId,
			challenge.requestSequence(),
			challenge.definitionGeneration(),
			challenge.routeRevision()
		);
	}

	private static WorldStateV4 finish(
		final Preparation preparation,
		final ExecutionResult execution
	) {
		WorldStateV4 prepared = commit(preparation.nextState().orElseThrow());
		Finalization finalized = PlayerActionAuthority.finalizeInvocation(
			prepared,
			preparation.prepared().orElseThrow(),
			execution
		);
		check(finalized.finalized(), "prepared action must finalize");
		return commit(finalized.nextState().orElseThrow());
	}

	private static WorldStateV4 commit(final WorldStateV4 candidate) {
		long revision = Math.incrementExact(candidate.stateRevision());
		return candidate.withCore(
			candidate.core().withCore(candidate.core().core().withStateRevision(revision))
		);
	}

	private static void checkServerRuntimeBridgeSource() throws IOException {
		String runtime = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/"
				+ "PixelTzzServerRuntime.java"
		);
		String registeredAction = method(
			runtime,
			"private static void handleTerminalRegisteredAction("
		);
		requireInOrder(
			registeredAction,
			"if (terminalHostControlRequest(payload))",
			"handleTerminalHostControl(",
			"return;",
			"terminalActionAuthorization(",
			"terminalActionRequest(payload, authorization)",
			"PlayerActionAuthority.prepare(",
			"terminalActionPredicates(server, player, authority)",
			"playerActionWorldTick(server)",
			"if (prepared.confirmation().isPresent())",
			"issuePlayerActionConfirmation(",
			"if (!prepared.accepted())",
			"sendPlayerActionRejection(",
			"executePreparedPlayerAction("
		);

		String hostRequest = method(
			runtime,
			"private static boolean terminalHostControlRequest("
		);
		requireContains(
			hostRequest,
			"TERMINAL_HOST_CONTROL_NODE_ID",
			"CLAIM_HOST_OPERATION",
			"TAKEOVER_HOST_OPERATION"
		);
		String hostOperation = method(
			runtime,
			"private static Optional<OperationKey> terminalHostOperation("
		);
		requireInOrder(
			hostOperation,
			"if (!authority.takeoverAllowed())",
			"authority.state().host().isEmpty()",
			"new OperationKey(\"claim_host\", CLAIM_HOST_OPERATION)",
			"new OperationKey(\"takeover_host\", TAKEOVER_HOST_OPERATION)"
		);
		String hostPreparation = method(
			runtime,
			"private static void handleTerminalHostControl("
		);
		requireInOrder(
			hostPreparation,
			"terminalHostOperation(authority)",
			"payload.actionId().filter(operation.id()::equals)",
			"prepareOperation(",
			"persistConfirmationAudit(",
			"prepareOperation(",
			"terminalHostControlBinding(",
			"clearPendingPlayerActionConfirmations(player.getUUID())",
			"clearPendingTerminalHostConfirmationMetadata(player.getUUID())",
			"CONFIRMATIONS.issue(",
			"TERMINAL_HOST_CONFIRMATIONS.put(",
			"session.sessionId()",
			"session.current().instanceId()",
			"session.definitionGeneration()",
			"session.routeRevision()",
			"payload.requestSequence()",
			"ServerPlayNetworking.send("
		);
		String hostBinding = method(
			runtime,
			"private static Binding terminalHostControlBinding("
		);
		requireContains(
			hostBinding,
			"session.sessionId().toString()",
			"session.current().instanceId().toString()",
			"session.current().pageId().toString()",
			"Long.toString(session.definitionGeneration())",
			"Long.toString(session.routeRevision())",
			"Long.toString(acceptedRequestSequence)",
			"base.operation().type()",
			"base.operation().id().toString()"
		);
		String hostSequenceGate = method(
			runtime,
			"static boolean terminalHostControlSequenceIsCurrent("
		);
		requireInOrder(
			hostSequenceGate,
			"acceptedRequestSequence < Long.MAX_VALUE",
			"session.sessionId().equals(expectedSessionId)",
			"session.current().instanceId().equals(expectedPageInstanceId)",
			"session.definitionGeneration() == expectedDefinitionGeneration",
			"session.routeRevision() == expectedRouteRevision",
			"session.nextRequestSequence() == acceptedRequestSequence + 1L"
		);
		String hostCommit = method(
			runtime,
			"private static void commitTerminalHostConfirmation("
		);
		requireInOrder(
			hostCommit,
			"TERMINAL_HOST_CONFIRMATIONS.remove(payload.tokenId())",
			"!pending.playerId().equals(player.getUUID())",
			"resolveTerminalAuthority(server, player)",
			"PLAYER_TERMINAL_SESSIONS.find(",
			"terminalHostControlSequenceIsCurrent(",
			"terminalHostOperation(authority)",
			"!operation.equals(pending.operation())",
			"prepareOperation(",
			"terminalHostControlBinding(",
			"CONFIRMATIONS.consume(",
			"!pendingBinding.equals(currentBinding)",
			"new StatefulRequest(",
			"executable.gameId()",
			"executable.definitionGeneration()",
			"executable.stateRevision()",
			"executeConfirmedOperation(server, player, authoritativeRequest, current)"
		);
		String confirmationDispatch = method(
			runtime,
			"private static void onCommitConfirmation("
		);
		requireInOrder(
			confirmationDispatch,
			"TERMINAL_HOST_CONFIRMATIONS.containsKey(payload.tokenId())",
			"commitTerminalHostConfirmation(",
			"return;",
			"pending.operation().type().equals(PLAYER_ACTION_OPERATION_TYPE)",
			"commitPlayerActionConfirmation("
		);

		String terminalIntent = method(
			runtime,
			"private static void onTerminalIntent("
		);
		requireInOrder(
			terminalIntent,
			"PLAYER_TERMINAL_SESSIONS.acceptSequence(",
			"if (sequence.status() != PlayerTerminalSessions.SequenceStatus.ACCEPTED)",
			"return;",
			"session = sequence.session().orElseThrow();",
			"cancelPendingPlayerActionConfirmations(player.getUUID())",
			"cancelPendingTerminalHostConfirmations(player.getUUID())",
			"resolveTerminalAuthority(",
			"resynchronizeTerminalIntentPage(",
			"switch (payload.intent())"
		);

		String intentResynchronization = method(
			runtime,
			"private static boolean resynchronizeTerminalIntentPage("
		);
		requireInOrder(
			intentResynchronization,
			"terminalRouteChanged(session, authority, rootPage)",
			"PLAYER_TERMINAL_SESSIONS.replaceRoute(",
			"sendTerminalPageBundle(",
			"authorizedTerminalStackDepth(",
			"PLAYER_TERMINAL_SESSIONS.truncateToDepth(",
			"sendTerminalPageBundle(",
			"clearPendingPlayerActionConfirmations(player.getUUID())",
			"CONFIRMATIONS.cancelOperator(player.getUUID())",
			"当前页面已不再适用于你的身份或游戏阶段，已返回上一级。"
		);

		String stackAuthorization = method(
			runtime,
			"private static int authorizedTerminalStackDepth("
		);
		requireInOrder(
			stackAuthorization,
			"for (int index = 1; index < stack.size(); index++)",
			"frame.navigationGrant().isPresent()",
			"PlayerActionAuthority.validateNavigationGrant(",
			"if (!validation.allowed())",
			"frame.historyRecord().orElse(null)",
			"historyDetailFrameStillAuthorized(",
			"Player terminal history frame became unavailable",
			"if (frame.navigationGrant().isPresent())",
			"continue;",
			"return index;",
			"return stack.size();"
		);
		String historyAuthorization = method(
			runtime,
			"private static boolean historyDetailFrameStillAuthorized("
		);
		requireInOrder(
			historyAuthorization,
			"PlayerTerminalProjector.resolveHistoryDetail(",
			"Optional.of(history.recordKey())",
			"target.recordKey().equals(history.recordKey())",
			"target.pageId().equals(frame.pageId())"
		);

		String terminalSessions = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/"
				+ "PlayerTerminalSessions.java"
		);
		String historySelection = method(
			terminalSessions,
			"public synchronized MutationResult pushHistoryDetail("
		);
		requireInOrder(
			historySelection,
			"Frame current = session.current()",
			"current.pageId().equals(pageId)",
			"stack.size() <= 1",
			"stack.set(",
			"current.navigationGrant()",
			"Optional.of(history)",
			"session.withStack(stack, stateRevision)",
			"stack.size() >= MAX_STACK_DEPTH",
			"stack.add("
		);
		String terminalHistoryBack = method(
			terminalSessions,
			"public synchronized MutationResult back("
		);
		requireInOrder(
			terminalHistoryBack,
			"session.stack().size() <= 1",
			"session.stack().subList(0, session.stack().size() - 1)",
			"Frame previous = stack.getLast()",
			"previous.navigationGrant()",
			"previous.historyRecord()"
		);

		String cancellation = method(
			runtime,
			"private static void cancelPendingPlayerActionConfirmations("
		);
		requireInOrder(
			cancellation,
			"PLAYER_ACTION_CONFIRMATIONS.entrySet()",
			".filter(entry -> entry.getValue().playerId().equals(playerId))",
			".map(Map.Entry::getKey)",
			"PLAYER_ACTION_CONFIRMATIONS.remove(tokenId)",
			"CONFIRMATIONS.cancel(playerId, tokenId)"
		);
		requireNotContains(cancellation, "cancelOperator(");
		String hostCancellation = method(
			runtime,
			"private static void cancelPendingTerminalHostConfirmations("
		);
		requireInOrder(
			hostCancellation,
			"TERMINAL_HOST_CONFIRMATIONS.entrySet()",
			".filter(entry -> entry.getValue().playerId().equals(playerId))",
			".map(Map.Entry::getKey)",
			"TERMINAL_HOST_CONFIRMATIONS.remove(tokenId)",
			"CONFIRMATIONS.cancel(playerId, tokenId)"
		);
		requireNotContains(hostCancellation, "cancelOperator(");

		String confirmationIssue = method(
			runtime,
			"private static void issuePlayerActionConfirmation("
		);
		requireInOrder(
			confirmationIssue,
			"clearPendingPlayerActionConfirmations(player.getUUID())",
			"CONFIRMATIONS.issue(",
			"PLAYER_ACTION_CONFIRMATIONS.put(",
			"new PendingPlayerActionConfirmation(",
			"player.getUUID()",
			"authorization",
			"request",
			"challenge"
		);

		String confirmationBinding = method(
			runtime,
			"private static Binding playerActionConfirmationBinding("
		);
		requireContains(
			confirmationBinding,
			"List.of(authority.player().playerId())",
			"session.sessionId().toString()",
			"session.current().instanceId().toString()",
			"session.current().pageId().toString()",
			"Long.toString(session.routeRevision())",
			"challenge.nodeId()",
			"challenge.actionId().toString()",
			"challenge.requestId().toString()",
			"Long.toString(challenge.requestSequence())",
			"Long.toString(challenge.definitionGeneration())"
		);

		String sequenceGate = method(
			runtime,
			"static boolean playerActionConfirmationSequenceIsCurrent("
		);
		requireContains(
			sequenceGate,
			"challenge.requestSequence() < Long.MAX_VALUE",
			"session.nextRequestSequence() == challenge.requestSequence() + 1L"
		);

		String confirmationCommit = method(
			runtime,
			"private static void commitPlayerActionConfirmation("
		);
		requireInOrder(
			confirmationCommit,
			"PLAYER_ACTION_CONFIRMATIONS.remove(payload.tokenId())",
			"!pending.playerId().equals(player.getUUID())",
			"resolveTerminalAuthority(server, player)",
			"PlayerActionAuthority.ConfirmationChallenge challenge = pending.challenge()",
			"if (!playerActionConfirmationSequenceIsCurrent(session, challenge))",
			"CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId())",
			"OperationCode.CONFIRMATION_CONTEXT_CHANGED",
			"return;",
			"playerActionConfirmationBinding(",
			"CONFIRMATIONS.consume(",
			"!pendingBinding.equals(currentBinding)",
			"new ConfirmedRequest(",
			"challenge.sessionId()",
			"challenge.pageInstanceId()",
			"challenge.nodeId()",
			"challenge.actionId()",
			"challenge.requestId()",
			"challenge.requestSequence()",
			"challenge.definitionGeneration()",
			"challenge.routeRevision()",
			"PlayerActionAuthority.prepare(",
			"terminalActionPredicates(server, player, authority)",
			"playerActionWorldTick(server)",
			"Optional.of(confirmed)",
			"executePreparedPlayerAction("
		);
		requireNotContains(
			registeredAction,
			"server.getTickCount()"
		);
		String terminalRequest = method(
			runtime,
			"private static ActionRequest terminalActionRequest("
		);
		requireInOrder(
			terminalRequest,
			"payload.terminalSessionId()",
			"payload.pageInstanceId()",
			"payload.requestSequence()",
			"PlayerActionAuthority.deriveRequestId(",
			"authorization.sessionId()",
			"authorization.nextRequestSequence()"
		);
		requireNotContains(terminalRequest, "payload.requestId()");
		requireNotContains(
			confirmationCommit,
			"server.getTickCount()"
		);
		String disconnectCleanup = method(
			runtime,
			"private static void removeConnection("
		);
		requireInOrder(
			disconnectCleanup,
			"clearPendingPlayerActionConfirmations(playerId)",
			"Optional<Binding> canceledBinding = CONFIRMATIONS.pendingBinding(playerId)",
			"CONFIRMATIONS.cancelOperator(playerId)"
		);
		requireNotContains(
			disconnectCleanup,
			"cancelPendingPlayerActionConfirmations(playerId)"
		);
		String playerActionClock = method(
			runtime,
			"private static long playerActionWorldTick("
		);
		requireContains(
			playerActionClock,
			"return server.overworld().getGameTime();"
		);

		String execution = method(
			runtime,
			"private static void executePreparedPlayerAction("
		);
		requireInOrder(
			execution,
			"wrapper.commitV4(",
			"RegisteredPlayerFunctionRunner.invoke(",
			"PlayerActionAuthority.finalizeInvocation(",
			"wrapper.commitV4(",
			"if (!committed.committed())",
			"玩家操作结果无法持久化；为防止重复执行，服务器不会自动重试。",
			"return;",
			"refreshOpenPlayerTerminals(server, false)",
			"sendOperationResult("
		);
		requireInOrder(
			execution,
			"PlayerTerminalSessions.Session previousVisibleSession",
			"PLAYER_TERMINAL_SESSIONS.pushAuthorized(",
			"player.getUUID()",
			"push.pageId()",
			"prepared.pageInstanceId()",
			"prepared.nodeId()",
			"prepared.actionId()",
			"reserved.state().orElseThrow().stateRevision()",
			"if (pagePushed)",
			"PLAYER_TERMINAL_SESSIONS.touchProjection(",
			"sendTerminalPageBundle(",
			"failTerminalPageMutationDelivery(",
			"previousVisibleSession"
		);
		check(
			count(execution, "RegisteredPlayerFunctionRunner.invoke(") == 1,
			"runtime must contain no automatic function retry after PREPARED"
		);

		String reload = method(runtime, "private static void onDataPackReloaded(");
		requireInOrder(
			reload,
			"CONFIRMATIONS.clear()",
			"PLAYER_ACTION_CONFIRMATIONS.clear()",
			"TERMINAL_HOST_CONFIRMATIONS.clear()",
			"refreshOpenPlayerTerminals(server, true)"
		);

		String invalidation = method(
			runtime,
			"private static void invalidateTerminalSession("
		);
		requireInOrder(
			invalidation,
			"sendTerminalInvalidation(",
			"PLAYER_TERMINAL_SESSIONS.remove(player.getUUID())",
			"clearPendingPlayerActionConfirmations(player.getUUID())",
			"clearPendingTerminalHostConfirmationMetadata(player.getUUID())",
			"CONFIRMATIONS.cancelOperator(player.getUUID())"
		);

		String disconnect = method(runtime, "private static void removeConnection(");
		requireInOrder(
			disconnect,
			"PLAYER_TERMINAL_SESSIONS.remove(playerId)",
			"clearPendingPlayerActionConfirmations(playerId)",
			"clearPendingTerminalHostConfirmationMetadata(playerId)",
			"CONFIRMATIONS.cancelOperator(playerId)"
		);

		String pageClose = method(runtime, "private static void onPageClose(");
		requireInOrder(
			pageClose,
			"PLAYER_TERMINAL_SESSIONS.close(",
			"clearPendingPlayerActionConfirmations(context.player().getUUID())",
			"clearPendingTerminalHostConfirmationMetadata(context.player().getUUID())",
			"CONFIRMATIONS.cancelOperator(context.player().getUUID())"
		);
		String explicitCancel = method(
			runtime,
			"private static void onCancelConfirmation("
		);
		requireInOrder(
			explicitCancel,
			"CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId())",
			"PLAYER_ACTION_CONFIRMATIONS.remove(payload.tokenId())",
			"TERMINAL_HOST_CONFIRMATIONS.remove(payload.tokenId())"
		);
		String terminalBack = method(
			runtime,
			"private static void handleTerminalBack("
		);
		requireInOrder(
			terminalBack,
			"PlayerTerminalSessions.Session previousVisibleSession",
			"PLAYER_TERMINAL_SESSIONS.back(",
			"sendTerminalPageBundle(",
			"if (!delivery.successful())",
			"failTerminalPageMutationDelivery(",
			"previousVisibleSession",
			"OperationCode.SUCCESS"
		);
		String terminalRefresh = method(
			runtime,
			"private static void handleTerminalRefresh("
		);
		requireInOrder(
			terminalRefresh,
			"PlayerTerminalSessions.Session previousVisibleSession",
			"PLAYER_TERMINAL_SESSIONS.reissue(",
			"sendTerminalPageBundle(",
			"if (!delivery.successful())",
			"failTerminalPageMutationDelivery(",
			"previousVisibleSession",
			"OperationCode.SUCCESS"
		);

		String refresh = method(
			runtime,
			"private static void refreshOpenPlayerTerminals("
		);
		String routeChange = method(
			runtime,
			"private static boolean terminalRouteChanged("
		);
		requireContains(
			routeChange,
			"!session.routeId().equals(authority.route().routeId())",
			"!session.rootPage().equals(rootPage)"
		);
		requireNotContains(
			routeChange,
			"definitionGeneration()"
		);
		requireContains(
			refresh,
			"terminalTransitionsToParticipantRecap(server, player)",
			"PLAYER_TERMINAL_SESSIONS.replaceRoute(",
			"authorizedTerminalStackDepth(",
			"PLAYER_TERMINAL_SESSIONS.truncateToDepth(",
			"PLAYER_TERMINAL_SESSIONS.reissue(",
			"new TerminalBindingDeltaS2CPayload(",
			"PLAYER_TERMINAL_SESSIONS.touchProjection("
		);
		requireInOrder(
			refresh,
			"terminalRouteChanged(existing, authority, rootPage)",
			"PLAYER_TERMINAL_SESSIONS.replaceRoute(",
			"authorizedTerminalStackDepth(",
			"PLAYER_TERMINAL_SESSIONS.truncateToDepth(",
			"sendTerminalPageBundle(",
			"forcePageBundle",
			"PLAYER_TERMINAL_SESSIONS.reissue("
		);
		check(
			count(refresh, "PLAYER_TERMINAL_SESSIONS.reissue(") >= 2,
			"reload truncation and a retained stack must both reissue at the new generation"
		);
		String recapRelease = method(
			runtime,
			"private static void releaseTerminalSessionForRecap("
		);
		requireInOrder(
			recapRelease,
			"sendTerminalInvalidation(",
			"\"timeline_terminal\"",
			"PLAYER_TERMINAL_SESSIONS.remove(",
			"LAST_TERMINAL_BINDING_HASHES.remove("
		);
		check(
			count(refresh, "failTerminalPageMutationDelivery(") >= 3
				&& count(refresh, "failTerminalPageMutation(") >= 4,
			"background route, reissue, bundle, projection, and delta failures must close the session"
		);
		requireInOrder(
			refresh,
			"PlayerTerminalSessions.Session existing",
			"PLAYER_TERMINAL_SESSIONS.replaceRoute(",
			"TerminalPageBundleResult delivery",
			"failTerminalPageMutationDelivery(",
			"existing"
		);

		String projection = method(
			runtime,
			"private static TerminalBindingResult terminalBindingDocument("
		);
		requireInOrder(
			projection,
			"PlayerTerminalProjector.project(",
			"authority.definitions()",
			"\"projection_failed\"",
			"if (!projection.summary().status().equals(\"ok\"))",
			"projection.summary().status()",
			"return TerminalBindingResult.projected("
		);
		check(
			!projection.contains("DefinitionRegistry.INSTANCE.view().active()"),
			"active timelines must project from their frozen terminal definitions"
		);
		String pageBundle = method(
			runtime,
			"private static TerminalPageBundleResult sendTerminalPageBundle("
		);
		requireInOrder(
			pageBundle,
			"TerminalBindingResult bindingResult = terminalBindingDocument(",
			"if (!bindingResult.successful())",
			"return TerminalPageBundleResult.failed(",
			"ServerPlayNetworking.send(",
			"LAST_TERMINAL_BINDING_HASHES.put(",
			"return TerminalPageBundleResult.sent()"
		);
		String mutationFailure = method(
			runtime,
			"private static void failTerminalPageMutation("
		);
		requireInOrder(
			mutationFailure,
			"requestSequence.ifPresent(",
			"sendOperationResult(",
			"invalidateTerminalSession(",
			"previousVisibleSession"
		);
		String snapshot = method(runtime, "private static void sendSnapshot(");
		requireInOrder(
			snapshot,
			"boolean currentPlayerHost",
			"sessionDiagnosticForViewer(",
			"currentPlayerHost",
			"definitions.healthy()",
			"definitions.diagnosticSummary()"
		);
		requireNotContains(
			snapshot,
			"truncateDiagnostic(definitions.diagnosticSummary())"
		);

		String consoleAccess = method(
			runtime,
			"private static boolean canViewConsole("
		);
		requireContains(
			consoleAccess,
			"PixelTzzWorldState.get(server)",
			"hostId()",
			"filter(player.getUUID()::equals)"
		);
		requireNotContains(consoleAccess, "isAdminEligible(player)");
		String previewCatalog = method(
			runtime,
			"private static void sendPreviewCatalog("
		);
		requireContains(previewCatalog, "canViewConsole(server, player)");
		requireNotContains(previewCatalog, "isAdminEligible(player)");
		String previewPage = method(
			runtime,
			"private static void onPreviewPageRequest("
		);
		requireContains(previewPage, "canViewConsole(context.server(), player)");
		requireNotContains(previewPage, "isAdminEligible(player)");
		String consoleRequest = method(
			runtime,
			"private static void onConsoleRequest("
		);
		requireInOrder(
			consoleRequest,
			"!verified(context.player())",
			"!NetworkProtocol.isCompatible(",
			"!admitControlRequest(",
			"!canViewConsole(",
			"OperationCode.FORBIDDEN",
			"return;",
			"sendConsoleSnapshot("
		);

		String terminalAuthority = method(
			runtime,
			"private static TerminalAuthorityResult resolveTerminalAuthority("
		);
		requireInOrder(
			terminalAuthority,
			"View live = DefinitionRegistry.INSTANCE.view()",
			"boolean frozenTimeline = root.core().timeline().isPresent()",
			"if (!frozenTimeline && (!live.healthy() || !live.active().usable()))",
			"definitions = terminalDefinitionSnapshot(root.core(), live.active())",
			"boolean takeoverAllowed = isAdminEligible(player)",
			"state.host()",
			"!host.playerId().equals(player.getUUID())",
			".orElse(true)"
		);
		requireNotContains(
			terminalAuthority,
			"if (!live.healthy() || !live.active().usable())"
		);

		String historyDetail = method(
			runtime,
			"private static void handleTerminalHistoryDetail("
		);
		requireInOrder(
			historyDetail,
			"payload.historyRecordKey()",
			"historyDetailButtonMatches(sourcePage.root(), nodeId, false)",
			"PlayerTerminalProjector.resolveHistoryDetail(",
			"PLAYER_TERMINAL_SESSIONS.pushHistoryDetail(",
			"target.pageId()",
			"target.recordKey()",
			"sendTerminalPageBundle(",
			"if (!delivery.successful())",
			"failTerminalPageMutationDelivery(",
			"authority,",
			"session,",
			"Optional.of(payload.requestSequence())",
			"return;",
			"OperationCode.SUCCESS"
		);
		requireNotContains(
			historyDetail,
			"Identifier.parse(recordKey)",
			"payload.actionId()"
		);
		String projector = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/"
				+ "PlayerTerminalProjector.java"
		);
		requireInOrder(
			projector,
			"\"pixel-tzz-pro/history-record/v1\"",
			"\"detail_available\"",
			"return \"h1_\" + sha256("
		);
		requireNotContains(
			projector,
			"item.addProperty(\"detail_page\"",
			"item.add(\"detail_page\""
		);
	}

	private static void checkClientBridgeSource() throws IOException {
		String consoleState = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/"
				+ "ClientConsoleState.java"
		);
		String acceptConfirmation = method(
			consoleState,
			"public static synchronized void acceptConfirmation("
		);
		requireInOrder(
			acceptConfirmation,
			"payload.operationType().equals(\"player_action\")",
			"ClientPageState.isTerminalHostControlOperation(",
			"pending == null",
			"ClientPageState.acceptTerminalConfirmation(",
			"payload.requestSequence()",
			"payload.operationId()",
			"if (terminalAction)",
			"confirmation = new TimedConfirmation(payload)"
		);
		String refreshOperation = method(
			consoleState,
			"public static synchronized boolean refreshOperation("
		);
		requireInOrder(
			refreshOperation,
			"ClientPageState.isTerminalHostControlOperation(",
			"return refreshTerminalHostOperation(action, targetIds)",
			"return prepareOperation(action, renewalTargetIds(action, targetIds), true)"
		);
		String refreshHostOperation = method(
			consoleState,
			"private static boolean refreshTerminalHostOperation("
		);
		requireInOrder(
			refreshHostOperation,
			"if (!targetIds.isEmpty())",
			"pending != null || terminalHostReviewSequence >= 0L",
			"ClientPageState.submitTerminalHostControl(action.operationId())",
			"terminalHostReviewSequence = submission.requestSequence()",
			"terminalHostReviewOperation = Optional.of(action.operationId())"
		);

		String pageState = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/"
				+ "ClientPageState.java"
		);
		String pageBundle = method(
			pageState,
			"public static synchronized void acceptPageBundle("
		);
		requireInOrder(
			pageBundle,
			"boolean sameTerminalSession",
			"boolean sameTerminalGeneration",
			"releaseActive(false)",
			"nextTerminalRequestSequence = payload.terminalContext()",
			"sameTerminalGeneration",
			"pendingTerminalIntent = null",
			"terminalIntentResult = null"
		);
		String submitHostControl = method(
			pageState,
			"public static synchronized TerminalSubmission submitTerminalHostControl("
		);
		requireInOrder(
			submitHostControl,
			"!operationId.equals(CLAIM_HOST_OPERATION)",
			"!operationId.equals(TAKEOVER_HOST_OPERATION)",
			"activePage.purpose != PagePurpose.NORMAL",
			".filter(TerminalContext::takeoverAllowed)",
			"submitTerminalIntent(",
			"Intent.ACTION",
			"Optional.of(TERMINAL_HOST_CONTROL_NODE_ID)",
			"Optional.of(operationId)"
		);
		String hostControlClassifier = method(
			pageState,
			"public static boolean isTerminalHostControlOperation("
		);
		requireContains(
			hostControlClassifier,
			"operationType.equals(\"claim_host\")",
			"operationId.equals(CLAIM_HOST_OPERATION)",
			"operationType.equals(\"takeover_host\")",
			"operationId.equals(TAKEOVER_HOST_OPERATION)"
		);
		String handoff = method(
			pageState,
			"public static synchronized boolean acceptTerminalConfirmation("
		);
		requireInOrder(
			handoff,
			"pendingTerminalIntent.intent != Intent.ACTION",
			"pendingTerminalIntent.requestSequence != requestSequence",
			"pendingTerminalIntent.actionId.filter(actionId::equals).isEmpty()",
			"pendingTerminalIntent = null",
			"terminalIntentResult = null"
		);

		String operationResult = method(
			pageState,
			"public static synchronized void acceptOperationResult("
		);
		requireContains(
			operationResult,
			"boolean terminalResult = pendingTerminalIntent != null",
			"pendingTerminalIntent = null",
			"terminalIntentResult = payload"
		);

		String bindingDelta = method(
			pageState,
			"public static synchronized boolean acceptTerminalBindingDelta("
		);
		requireInOrder(
			bindingDelta,
			"activePage.generation != payload.generation()",
			"!activePage.instanceId.equals(payload.pageInstanceId())",
			"!terminal.sessionId().equals(payload.sessionId())",
			"terminal.routeRevision() != payload.routeRevision()",
			"activePage = activePage.withServerContext(",
			"revision++",
			"return true"
		);
		requireNotContains(
			bindingDelta,
			"releaseActive(",
			"clearTerminalIntentState(",
			"clearTerminalOpenPending("
		);
		String terminalInvalidation = method(
			pageState,
			"public static synchronized boolean acceptTerminalInvalidation("
		);
		requireInOrder(
			terminalInvalidation,
			"payload.reason().equals(\"timeline_terminal\")",
			"terminalRelease = new TerminalRelease(",
			"return true;",
			"releaseActive(false)",
			"pageStatus = PageStatus.ERROR"
		);
		String terminalRelease = method(
			pageState,
			"public static synchronized Optional<TerminalRelease> consumeTerminalRelease("
		);
		requireContains(
			terminalRelease,
			"terminalReleaseConsumed",
			"return Optional.of(terminalRelease)"
		);
		String submitHistory = method(
			pageState,
			"public static synchronized TerminalSubmission submitTerminalHistoryDetail("
		);
		requireInOrder(
			submitHistory,
			"Intent.HISTORY_DETAIL",
			"Optional.of(Objects.requireNonNull(nodeId",
			"Optional.empty()",
			"Optional.of(Objects.requireNonNull(historyRecordKey"
		);

		String screen = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/"
				+ "DataDrivenPageScreen.java"
		);
		String liveViewport = method(screen, "private void layoutLiveViewport()");
		requireContains(
			liveViewport,
			"TerminalShellViewport.fit(this.width, this.height)",
			"this.terminalShellViewport.pageViewport(",
			"this.pageLayoutWidth = TerminalShellViewport.REFERENCE_WIDTH"
		);
		String responsiveTier = method(screen, "private ResponsiveTier pageResponsiveTier()");
		requireInOrder(
			responsiveTier,
			"normalTerminalPage()",
			"ResponsiveTier.STANDARD",
			"this.session.responsiveTier()"
		);
		String terminalShell = method(
			screen,
			"private void renderTerminalShellReference("
		);
		requireNotContains(
			terminalShell,
			"ClientConsoleState.pending()"
		);
		String screenTick = method(screen, "public void tick()");
		requireInOrder(
			screenTick,
			"if (pageProjectionChanged())",
			"if (applyTerminalBindingUpdateInPlace())",
			"return;",
			"rebuild();"
		);
		String inPlace = method(
			screen,
			"private boolean applyTerminalBindingUpdateInPlace()"
		);
		requireContains(
			inPlace,
			"previous.widget()",
			"widgetLayouts(",
			"!replacementWidgets.keySet().equals(this.pageWidgets.keySet())",
			"!layout.kind().matches(previous.widget())",
			"layout.bounds()",
			"this.pageWidgets.putAll(refreshedBindings)",
			"this.plan = replacement",
			"refreshButtonStates()",
			"refreshTerminalChromeStates()",
			"synchronizeWidgetBounds(buildMotionLayout(Util.getMillis()))",
			"rememberPageProjection()"
		);
		requireNotContains(
			inPlace,
			"rebuild(",
			"previous.layoutBounds()",
			"bounds().equals(",
			"contentBounds().equals(",
			"playEventSound(",
			"playNamedSound(",
			"pendingSounds.clear(",
			"lastSoundTimes.clear("
		);
		String buttonRefresh = method(screen, "private void refreshButtonStates()");
		requireContains(
			buttonRefresh,
			"button.setMessage(binding.node().styledText())",
			"button.active = actionEnabled(binding.node(), content)",
			"updateButtonTooltip(button, binding.node(), content)"
		);
		String detailClick = method(
			screen,
			"private void submitHistoryDetail("
		);
		requireInOrder(
			detailClick,
			"node.context()",
			".resolve(\"item.id\")",
			"ClientPageState.submitTerminalHistoryDetail(",
			"nodeId",
			"recordKey"
		);
		requireNotContains(
			detailClick,
			"detail_page",
			"active.page().id()"
		);

		String review = method(screen, "private void tickTerminalActionReview()");
		requireInOrder(
			review,
			"value.operationType().equals(\"player_action\")",
			"value.requestSequence() == this.pendingActionSequence",
			"this.pendingActionSequence = -1L",
			"this.pendingActionIsTerminal = false",
			"new OperationConfirmationScreen(this, action)"
		);

		String confirmationScreen = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/"
				+ "OperationConfirmationScreen.java"
		);
		String hostRequirement = method(
			confirmationScreen,
			"boolean requiresCurrentHost()"
		);
		requireInOrder(
			hostRequirement,
			"!this.action.operationType().equals(\"player_action\")",
			"!ClientPageState.isTerminalHostControlOperation("
		);
		String confirmationTick = method(confirmationScreen, "public void tick()");
		requireContains(
			confirmationTick,
			"this.action.operationType().equals(\"player_action\")",
			"OperationSubtitleAnimator.enqueue(message)",
			"closeToParent()"
		);
		check(
			count(confirmationTick, "this.action.operationType().equals(\"player_action\")") >= 2,
			"confirmed success and failure must both return to the player terminal"
		);
		String close = method(confirmationScreen, "public void onClose()");
		requireInOrder(
			close,
			"if (!this.submitted && ClientConsoleState.confirmation().isPresent())",
			"ClientConsoleState.cancelConfirmation()",
			"closeToParent()"
		);

		String hostControl = method(screen, "private static ActionEntry hostControlAction()");
		requireInOrder(
			hostControl,
			"boolean claim = !ClientSessionState.snapshot().hostAssigned()",
			"claim ? \"claim_host\" : \"takeover_host\"",
			"claim ? CLAIM_HOST_OPERATION : TAKEOVER_HOST_OPERATION",
			".filter(action -> action.operationType().equals(operationType))",
			".filter(action -> action.operationId().equals(operationId))",
			".orElse(claim ? CLAIM_HOST_ACTION : TAKEOVER_HOST_ACTION)"
		);
		requireContains(
			hostControl,
			"claim_host",
			"takeover_host",
			"CLAIM_HOST_OPERATION",
			"TAKEOVER_HOST_OPERATION",
			"CLAIM_HOST_ACTION",
			"TAKEOVER_HOST_ACTION"
		);
		String startHostReview = method(
			screen,
			"private void prepareHostControlReview()"
		);
		requireInOrder(
			startHostReview,
			"ClientPageState.submitTerminalHostControl(",
			"action.operationId()",
			"if (!submission.sent())",
			"this.pendingTakeoverReview = true",
			"this.pendingTakeoverSequence = submission.requestSequence()"
		);
		requireNotContains(
			startHostReview,
			"ClientConsoleState.prepareOperation("
		);
		String pollHostReview = method(screen, "private void tickTakeoverReview()");
		requireInOrder(
			pollHostReview,
			"ClientConsoleState.confirmation()",
			"this.pendingTakeoverReview = false",
			"new OperationConfirmationScreen(this, action)",
			"ClientPageState.consumeTerminalIntentResult(this.pendingTakeoverSequence)"
		);

		String supervisor = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/"
				+ "LivePageSupervisor.java"
		);
		String supervisorTick = method(supervisor, "public static void tick(");
		requireInOrder(
			supervisorTick,
			"ClientPageState.consumeTerminalRelease()",
			"OperationSubtitleAnimator.enqueue(",
			"page.releaseFromServer()",
			"ClientPageState.completeTerminalRelease()"
		);
		String pauseEntry = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/"
				+ "PauseMenuEntry.java"
		);
		String pauseState = method(pauseEntry, "private static EntryState entryState()");
		requireInOrder(
			pauseState,
			"String labelKey = forcedFlow",
			": snapshot.currentPlayerHost()",
			": recapAvailable",
			"pixel_tzz_pro.menu.terminal"
		);
	}

	private static String source(final String path) throws IOException {
		return Files.readString(Path.of(path), StandardCharsets.UTF_8);
	}

	private static String method(final String source, final String signature) {
		int start = source.indexOf(signature);
		check(start >= 0, "missing source method: " + signature);
		int opening = source.indexOf('{', start);
		check(opening >= 0, "missing source method body: " + signature);
		int depth = 0;
		for (int index = opening; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(start, index + 1);
				}
			}
		}
		throw new AssertionError("unterminated source method: " + signature);
	}

	private static void requireContains(
		final String source,
		final String... fragments
	) {
		for (String fragment : fragments) {
			check(source.contains(fragment), "missing bridge contract fragment: " + fragment);
		}
	}

	private static void requireNotContains(
		final String source,
		final String... fragments
	) {
		for (String fragment : fragments) {
			check(!source.contains(fragment), "forbidden bridge contract fragment: " + fragment);
		}
	}

	private static void requireInOrder(
		final String source,
		final String... fragments
	) {
		int cursor = 0;
		for (String fragment : fragments) {
			int found = source.indexOf(fragment, cursor);
			check(found >= 0, "missing or out-of-order bridge fragment: " + fragment);
			cursor = found + fragment.length();
		}
	}

	private static int count(final String source, final String fragment) {
		int result = 0;
		int cursor = 0;
		while ((cursor = source.indexOf(fragment, cursor)) >= 0) {
			result++;
			cursor += fragment.length();
		}
		return result;
	}

	private static WorldStateV4 state() {
		PlayerRecord player = new PlayerRecord(
			PLAYER,
			"PlayerBridge",
			ROLE,
			Optional.empty(),
			LIFE,
			Map.of(),
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			1L,
			1L
		);
		WorldStateV2 core = WorldStateV2.initial()
			.withActivity(GAME, PHASE)
			.withPlayers(Map.of(PLAYER, player));
		return new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			new WorldStateV3(
				WorldStateV3.SCHEMA_VERSION,
				core,
				Optional.of(GAME_INSTANCE),
				Optional.empty(),
				List.of(),
				List.of()
			),
			List.of(),
			List.of()
		);
	}

	private static DefinitionSnapshot definitions() {
		Map<Identifier, PlayerActionDefinition> actions = Map.of(
			DIRECT_ACTION,
			action(DIRECT_ACTION, Optional.empty(), Optional.empty()),
			CONFIRMED_ACTION,
			action(
				CONFIRMED_ACTION,
				Optional.of(
					new Confirmation(
						text("确认执行"),
						List.of(text("将运行玩家终端测试函数"))
					)
				),
				Optional.of(id("predicate/can_run"))
			)
		);
		PageDefinition page = new PageDefinition(
			PAGE,
			GAME,
			text("玩家终端"),
			id("theme/default"),
			new Breakpoints(639, 960),
			new NodeDefinition(
				NodeType.COLUMN,
				Optional.of("root"),
				LayoutDefinition.empty(),
				Optional.empty(),
				Map.of(),
				Optional.empty(),
				Map.of(),
				Map.of(),
				new ChildrenContent(
					List.of(
						button("direct", DIRECT_ACTION),
						button("confirmed", CONFIRMED_ACTION)
					)
				),
				"/root"
			),
			List.of(),
			"{}",
			"bridge-page-hash"
		);
		GameDefinition game = new GameDefinition(
			GAME,
			3,
			1,
			text("桥接测试游戏"),
			PHASE,
			ROLE,
			LIFE,
			Optional.empty(),
			Optional.empty(),
			Optional.of(new PlayerTerminalConfig(PAGE, true))
		);
		return new DefinitionSnapshot(
			GENERATION,
			Map.of(GAME, game),
			Map.of(
				ROLE,
				new RoleDefinition(
					ROLE,
					GAME,
					text("逃走者"),
					Optional.empty(),
					Set.of(),
					new TabStyle(Optional.empty(), Optional.empty())
				)
			),
			Map.of(),
			Map.of(
				LIFE,
				new LifeStateDefinition(
					LIFE,
					GAME,
					text("存活"),
					Optional.empty(),
					Set.of()
				)
			),
			Map.of(
				PHASE,
				new PhaseDefinition(
					PHASE,
					GAME,
					text("大厅"),
					Set.of(),
					Optional.empty(),
					Optional.empty()
				)
			),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			actions,
			Map.of(PAGE, page),
			Map.of(),
			Map.of(),
			Set.of(FUNCTION),
			Set.of(id("predicate/can_run")),
			Map.of(id("predicate/can_run"), "{}")
		);
	}

	private static PlayerActionDefinition action(
		final Identifier id,
		final Optional<Confirmation> confirmation,
		final Optional<Identifier> predicate
	) {
		return new PlayerActionDefinition(
			id,
			GAME,
			new PlayerRunFunctionOperation(FUNCTION),
			new Audience(
				Set.of(ROLE),
				Set.of(),
				Set.of(LIFE),
				Set.of(),
				Set.of(),
				Set.of(),
				true,
				true
			),
			Set.of(PHASE),
			Set.of(),
			Set.of(),
			predicate,
			UsageScope.UNLIMITED,
			0,
			0L,
			CooldownScope.PLAYER,
			confirmation,
			ExecutionContext.PLAYER,
			PlayerActionFeedback.empty(),
			AuditPolicy.ALL
		);
	}

	private static NodeDefinition button(
		final String nodeId,
		final Identifier actionId
	) {
		return new NodeDefinition(
			NodeType.BUTTON,
			Optional.of(nodeId),
			LayoutDefinition.empty(),
			Optional.empty(),
			Map.of(),
			Optional.empty(),
			Map.of(),
			Map.of(),
			new ButtonContent(
				new StaticText(text(nodeId)),
				Optional.empty(),
				Optional.empty(),
				new UiAction(
					ActionType.REGISTERED,
					Optional.empty(),
					Optional.of(actionId)
				)
			),
			"/root/" + nodeId
		);
	}

	private static RichText text(final String value) {
		return new RichText("{\"text\":\"" + value + "\"}", value);
	}

	private static UUID requestId(final long value) {
		return new UUID(0x3000000000000000L, value);
	}

	private static Identifier id(final String path) {
		return Identifier.parse("pixel_tzz:" + path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record Attempt(
		SessionAuthorization authorization,
		ActionRequest request,
		Preparation preparation
	) {
	}
}
