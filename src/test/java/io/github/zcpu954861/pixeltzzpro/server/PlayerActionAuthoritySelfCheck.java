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
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOpenPageOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTerminalConfig;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.UsageScope;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Breakpoints;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StaticText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiAction;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ActionRequest;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Code;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ConfirmedRequest;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ExecutionResult;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.NavigationValidation;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Preparation;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.SessionAuthorization;
import io.github.zcpu954861.pixeltzzpro.server.PlayerTerminalSessions.NavigationGrant;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionInvocationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Runnable checks for registered player-action authorization and callback macro isolation.
 */
public final class PlayerActionAuthoritySelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier PHASE = id("phase/lobby");
	private static final Identifier OTHER_PHASE = id("phase/other");
	private static final Identifier ROLE = id("role/runner");
	private static final Identifier LIFE = id("life/alive");
	private static final Identifier PAGE = id("page/player");
	private static final Identifier FUNCTION = id("function/player_action");
	private static final Identifier PREDICATE = id("predicate/can_use");
	private static final Identifier PERSONAL = id("action/personal");
	private static final Identifier GLOBAL = id("action/global");
	private static final Identifier COOLDOWN = id("action/cooldown");
	private static final Identifier FUNCTION_ACTION = id("action/function");
	private static final Identifier CONFIRMED_ACTION = id("action/confirmed");
	private static final Identifier PREDICATE_ACTION = id("action/predicate");
	private static final Identifier NAVIGATION_COMPLEX = id("action/navigation_complex");
	private static final Identifier TASK_NAVIGATION = id("action/task_navigation");
	private static final Identifier TASK_STATE_NAVIGATION = id("action/task_state_navigation");
	private static final Identifier REQUIRED_TASK = id("task/required");
	private static final UUID GAME_INSTANCE =
		UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID PLAYER_A =
		UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final UUID PLAYER_B =
		UUID.fromString("10000000-0000-0000-0000-000000000003");
	private static final UUID SESSION_A =
		UUID.fromString("10000000-0000-0000-0000-000000000004");
	private static final UUID SESSION_B =
		UUID.fromString("10000000-0000-0000-0000-000000000005");
	private static final UUID PAGE_A =
		UUID.fromString("10000000-0000-0000-0000-000000000006");
	private static final UUID PAGE_B =
		UUID.fromString("10000000-0000-0000-0000-000000000007");
	private static final long GENERATION = 11L;
	private static final long ROUTE_REVISION = 3L;

	private PlayerActionAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		WorldStateV2.initial();
		DefinitionSnapshot definitions = definitions();
		checkDerivedRequestIds();
		checkNavigationGrantValidation(definitions);
		checkPersonalLimit(definitions);
		checkGlobalUsageAndPlayerCooldown(definitions);
		checkGlobalCooldown(definitions);
		checkReplayAndOutcomeUnknown(definitions);
		checkFailureAndAudit(definitions);
		checkForgedPageNode(definitions);
		checkConfirmationBoundary(definitions);
		checkConditions(definitions);
		checkFunctionContext(definitions);
		System.out.println("PLAYER_ACTION_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkDerivedRequestIds() {
		UUID first = PlayerActionAuthority.deriveRequestId(SESSION_A, 7L);
		UUID repeated = PlayerActionAuthority.deriveRequestId(SESSION_A, 7L);
		UUID next = PlayerActionAuthority.deriveRequestId(SESSION_A, 8L);
		UUID otherSession = PlayerActionAuthority.deriveRequestId(SESSION_B, 7L);
		check(
			first.equals(repeated),
			"the same server session and accepted request sequence must derive one stable ID"
		);
		check(
			!first.equals(next),
			"different accepted request sequences must derive different IDs"
		);
		check(
			!first.equals(otherSession),
			"different server terminal sessions must derive different IDs"
		);
		check(
			first.version() == 5 && first.variant() == 2,
			"derived request IDs must retain RFC 4122 version-5-like and IETF variant bits"
		);
		expectRejected(
			() -> PlayerActionAuthority.deriveRequestId(SESSION_A, -1L),
			"negative accepted request sequences must be rejected"
		);
	}

	private static void checkNavigationGrantValidation(
		final DefinitionSnapshot definitions
	) {
		WorldStateV4 state = state(PHASE);
		NavigationGrant personal = new NavigationGrant(
			PAGE,
			PAGE_A,
			"personal",
			PERSONAL
		);
		NavigationValidation allowed = PlayerActionAuthority.validateNavigationGrant(
			state,
			definitions,
			PLAYER_A,
			true,
			personal,
			PAGE,
			PlayerActionAuthority.PredicateEvaluator.allowAll()
		);
		check(
			allowed.allowed()
				&& allowed.denialCode().isEmpty()
				&& allowed.message().isEmpty(),
			"current registered open-page ancestry must remain valid"
		);
		check(
			state.usages().isEmpty() && state.invocations().isEmpty(),
			"navigation validation must not mutate usage or invocation ledgers"
		);

		NavigationValidation missingPlayer =
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				UUID.fromString("10000000-0000-0000-0000-000000000099"),
				true,
				personal,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			);
		checkNavigationDenied(
			missingPlayer,
			Code.PLAYER_NOT_REGISTERED,
			"navigation must require a registered player"
		);

		WorldStateV4 hostState = state.withCore(
			state.core().withCore(
				state.core().core().withHost(
					Optional.of(HostRecord.migratedLegacy(PLAYER_A))
				)
			)
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				hostState,
				definitions,
				PLAYER_A,
				true,
				personal,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.HOST_FORBIDDEN,
			"the host must not retain ordinary-player navigation frames"
		);

		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				false,
				personal,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.AUDIENCE_MISMATCH,
			"navigation audience conditions must be re-evaluated"
		);

		NavigationGrant missingPage = new NavigationGrant(
			id("page/missing"),
			PAGE_A,
			"personal",
			PERSONAL
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				missingPage,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.PAGE_UNAVAILABLE,
			"navigation must re-resolve its source page"
		);

		NavigationGrant missingNode = new NavigationGrant(
			PAGE,
			PAGE_A,
			"missing",
			PERSONAL
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				missingNode,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.NODE_MISMATCH,
			"navigation must rebind its exact source node"
		);

		NavigationGrant mismatchedAction = new NavigationGrant(
			PAGE,
			PAGE_A,
			"personal",
			GLOBAL
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				mismatchedAction,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.NODE_MISMATCH,
			"navigation must rebind the action declared by the source node"
		);

		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				personal,
				id("page/missing"),
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.ACTION_UNAVAILABLE,
			"navigation must reject a target that differs from the open-page operation"
		);

		NavigationGrant function = new NavigationGrant(
			PAGE,
			PAGE_A,
			"function",
			FUNCTION_ACTION
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				function,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.ACTION_UNAVAILABLE,
			"run-function actions must never authorize a retained page frame"
		);

		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state(OTHER_PHASE),
				definitions,
				PLAYER_A,
				true,
				personal,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.PHASE_MISMATCH,
			"navigation phase conditions must be re-evaluated"
		);

		NavigationGrant task = new NavigationGrant(
			PAGE,
			PAGE_A,
			"task_navigation",
			TASK_NAVIGATION
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				task,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.TASK_MISMATCH,
			"navigation task conditions must be re-evaluated"
		);

		NavigationGrant taskState = new NavigationGrant(
			PAGE,
			PAGE_A,
			"task_state_navigation",
			TASK_STATE_NAVIGATION
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				taskState,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			),
			Code.TASK_STATE_MISMATCH,
			"navigation task-state conditions must be re-evaluated"
		);

		NavigationGrant predicate = new NavigationGrant(
			PAGE,
			PAGE_A,
			"predicate",
			PREDICATE_ACTION
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				predicate,
				PAGE,
				(ignored, context) -> false
			),
			Code.PREDICATE_DENIED,
			"navigation predicates must be re-evaluated"
		);
		checkNavigationDenied(
			PlayerActionAuthority.validateNavigationGrant(
				state,
				definitions,
				PLAYER_A,
				true,
				predicate,
				PAGE,
				(ignored, context) -> {
					throw new IllegalStateException("fixture failure");
				}
			),
			Code.PREDICATE_FAILED,
			"navigation predicate failures must fail closed"
		);

		PlayerActionUsage exhausted = new PlayerActionUsage(
			GAME_INSTANCE,
			NAVIGATION_COMPLEX,
			Optional.of(PLAYER_A),
			1L,
			100L
		);
		WorldStateV4 exhaustedState = state.withUsages(List.of(exhausted));
		NavigationGrant complex = new NavigationGrant(
			PAGE,
			PAGE_A,
			"navigation_complex",
			NAVIGATION_COMPLEX
		);
		NavigationValidation ignoredExecutionPolicies =
			PlayerActionAuthority.validateNavigationGrant(
				exhaustedState,
				definitions,
				PLAYER_A,
				true,
				complex,
				PAGE,
				PlayerActionAuthority.PredicateEvaluator.allowAll()
			);
		check(
			ignoredExecutionPolicies.allowed()
				&& exhaustedState.usages().equals(List.of(exhausted))
				&& exhaustedState.invocations().isEmpty(),
			"navigation validation must ignore and preserve usage, cooldown, confirmation, and ledgers"
		);
	}

	private static void checkNavigationDenied(
		final NavigationValidation validation,
		final Code expected,
		final String message
	) {
		check(
			!validation.allowed()
				&& validation.denialCode().equals(Optional.of(expected))
				&& !validation.message().isBlank(),
			message + ": " + validation
		);
	}

	private static void checkPersonalLimit(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		Preparation first = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"personal",
			PERSONAL,
			requestId(1),
			100L
		);
		check(first.accepted(), "first player-limited action must prepare");
		check(
			first.nextState()
				.orElseThrow()
				.usage(GAME_INSTANCE, PERSONAL, Optional.of(PLAYER_A))
				.orElseThrow()
				.uses() == 1L,
			"player usage bucket must increment at PREPARED"
		);
		state = finish(first, true, "page_opened", "opened");
		Preparation second = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			1L,
			2L,
			"personal",
			PERSONAL,
			requestId(2),
			101L
		);
		check(second.code() == Code.USAGE_EXHAUSTED, "personal max uses must be enforced");
	}

	private static void checkGlobalUsageAndPlayerCooldown(
		final DefinitionSnapshot definitions
	) {
		WorldStateV4 state = state(PHASE);
		Preparation first = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"global",
			GLOBAL,
			requestId(10),
			100L
		);
		check(first.accepted(), "first global action must prepare");
		WorldStateV4 prepared = first.nextState().orElseThrow();
		check(
			prepared.usage(GAME_INSTANCE, GLOBAL, Optional.empty()).orElseThrow().uses() == 1L,
			"global usage bucket missing"
		);
		check(
			prepared.usage(GAME_INSTANCE, GLOBAL, Optional.of(PLAYER_A)).isPresent(),
			"player cooldown bucket missing"
		);
		state = finish(first, true, "page_opened", "opened");

		Preparation secondPlayer = prepare(
			state,
			definitions,
			PLAYER_B,
			SESSION_B,
			PAGE_B,
			0L,
			1L,
			"global",
			GLOBAL,
			requestId(11),
			100L
		);
		check(secondPlayer.accepted(), "player-scoped cooldown must not block another player");
		check(
			secondPlayer.nextState()
				.orElseThrow()
				.usage(GAME_INSTANCE, GLOBAL, Optional.empty())
				.orElseThrow()
				.uses() == 2L,
			"global use count must include both players"
		);
		state = finish(secondPlayer, true, "page_opened", "opened");

		Preparation exhausted = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			1L,
			2L,
			"global",
			GLOBAL,
			requestId(12),
			111L
		);
		check(exhausted.code() == Code.USAGE_EXHAUSTED, "global max must block all players");
	}

	private static void checkGlobalCooldown(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		Preparation first = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"cooldown",
			COOLDOWN,
			requestId(20),
			200L
		);
		state = finish(first, true, "page_opened", "opened");
		Preparation blocked = prepare(
			state,
			definitions,
			PLAYER_B,
			SESSION_B,
			PAGE_B,
			0L,
			1L,
			"cooldown",
			COOLDOWN,
			requestId(21),
			219L
		);
		check(blocked.code() == Code.COOLDOWN_ACTIVE, "global cooldown must block another player");
		Preparation elapsed = prepare(
			state,
			definitions,
			PLAYER_B,
			SESSION_B,
			PAGE_B,
			0L,
			1L,
			"cooldown",
			COOLDOWN,
			requestId(22),
			220L
		);
		check(elapsed.accepted(), "global cooldown must unlock at its exact boundary");
	}

	private static void checkReplayAndOutcomeUnknown(
		final DefinitionSnapshot definitions
	) {
		WorldStateV4 initial = state(PHASE);
		UUID requestId = requestId(30);
		ActionRequest originalRequest = request(
			initial,
			SESSION_A,
			PAGE_A,
			1L,
			"function",
			FUNCTION_ACTION,
			requestId
		);
		Preparation prepared = PlayerActionAuthority.prepare(
			initial,
			definitions,
			session(initial, PLAYER_A, SESSION_A, PAGE_A, 1L),
			originalRequest,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			300L
		);
		check(prepared.accepted(), "function action must prepare");
		WorldStateV4 committedPrepared = commit(prepared.nextState().orElseThrow());

		Preparation unknown = PlayerActionAuthority.prepare(
			committedPrepared,
			definitions,
			session(committedPrepared, PLAYER_A, SESSION_A, PAGE_A, 2L),
			originalRequest,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			301L
		);
		check(unknown.code() == Code.OUTCOME_UNKNOWN, "PREPARED replay must be outcome-unknown");

		var finalized = PlayerActionAuthority.finalizeInvocation(
			committedPrepared,
			prepared.prepared().orElseThrow(),
			ExecutionResult.succeeded("function_succeeded", "ok")
		);
		WorldStateV4 terminal = commit(finalized.nextState().orElseThrow());
		Preparation replayed = PlayerActionAuthority.prepare(
			terminal,
			definitions,
			session(terminal, PLAYER_A, SESSION_A, PAGE_A, 2L),
			originalRequest,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			302L
		);
		check(
			replayed.code() == Code.REQUEST_REPLAYED
				&& replayed.replay().orElseThrow().status()
					== PlayerActionInvocationStatus.SUCCEEDED,
			"terminal duplicate request must return its durable result"
		);
	}

	private static void checkFailureAndAudit(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		Preparation prepared = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"function",
			FUNCTION_ACTION,
			requestId(40),
			400L
		);
		WorldStateV4 committed = commit(prepared.nextState().orElseThrow());
		var failed = PlayerActionAuthority.finalizeInvocation(
			committed,
			prepared.prepared().orElseThrow(),
			ExecutionResult.failed("function_failed", "expected self-check failure")
		);
		check(failed.finalized(), "failed function must still finalize");
		check(
			failed.invocation().orElseThrow().status() == PlayerActionInvocationStatus.FAILED,
			"failed function must persist FAILED"
		);
		check(failed.audit().isPresent(), "ALL audit policy must expose a bounded audit entry");
	}

	private static void checkForgedPageNode(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		Preparation missingNode = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"not-a-button",
			PERSONAL,
			requestId(50),
			500L
		);
		check(missingNode.code() == Code.NODE_MISMATCH, "forged node ID must fail closed");
		Preparation mismatchedBinding = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"personal",
			GLOBAL,
			requestId(51),
			500L
		);
		check(
			mismatchedBinding.code() == Code.NODE_MISMATCH,
			"button/action mismatch must fail closed"
		);
	}

	private static void checkConfirmationBoundary(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		ActionRequest request = request(
			state,
			SESSION_A,
			PAGE_A,
			1L,
			"confirmed",
			CONFIRMED_ACTION,
			requestId(60)
		);
		SessionAuthorization session = session(state, PLAYER_A, SESSION_A, PAGE_A, 1L);
		Preparation challenge = PlayerActionAuthority.prepare(
			state,
			definitions,
			session,
			request,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			600L
		);
		check(
			challenge.code() == Code.CONFIRMATION_REQUIRED
				&& challenge.confirmation().isPresent()
				&& challenge.nextState().isEmpty()
				&& state.usages().isEmpty(),
			"confirmation challenge must not mutate usage or invocation state"
		);
		var metadata = challenge.confirmation().orElseThrow();
		ConfirmedRequest confirmed = new ConfirmedRequest(
			metadata.sessionId(),
			metadata.pageInstanceId(),
			metadata.nodeId(),
			metadata.actionId(),
			metadata.requestId(),
			metadata.requestSequence(),
			metadata.definitionGeneration(),
			metadata.routeRevision()
		);
		Preparation accepted = PlayerActionAuthority.prepare(
			state,
			definitions,
			session,
			request,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			600L,
			Optional.of(confirmed)
		);
		check(accepted.accepted(), "matching consumed confirmation must authorize prepare");

		ConfirmedRequest forged = new ConfirmedRequest(
			metadata.sessionId(),
			metadata.pageInstanceId(),
			metadata.nodeId(),
			metadata.actionId(),
			requestId(999),
			metadata.requestSequence(),
			metadata.definitionGeneration(),
			metadata.routeRevision()
		);
		Preparation rejected = PlayerActionAuthority.prepare(
			state,
			definitions,
			session,
			request,
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			600L,
			Optional.of(forged)
		);
		check(
			rejected.code() == Code.CONFIRMATION_INVALID,
			"confirmation for another request must fail closed"
		);
	}

	private static void checkConditions(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		Preparation predicateDenied = PlayerActionAuthority.prepare(
			state,
			definitions,
			session(state, PLAYER_A, SESSION_A, PAGE_A, 1L),
			request(
				state,
				SESSION_A,
				PAGE_A,
				1L,
				"predicate",
				PREDICATE_ACTION,
				requestId(70)
			),
			(predicate, context) -> false,
			700L
		);
		check(predicateDenied.code() == Code.PREDICATE_DENIED, "predicate denial must fail closed");

		WorldStateV4 wrongPhase = state(OTHER_PHASE);
		Preparation phaseDenied = PlayerActionAuthority.prepare(
			wrongPhase,
			definitions,
			session(wrongPhase, PLAYER_A, SESSION_A, PAGE_A, 1L),
			request(
				wrongPhase,
				SESSION_A,
				PAGE_A,
				1L,
				"predicate",
				PREDICATE_ACTION,
				requestId(71)
			),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			700L
		);
		check(phaseDenied.code() == Code.PHASE_MISMATCH, "phase filter must be authoritative");
	}

	private static void checkFunctionContext(final DefinitionSnapshot definitions) {
		WorldStateV4 state = state(PHASE);
		Preparation prepared = prepare(
			state,
			definitions,
			PLAYER_A,
			SESSION_A,
			PAGE_A,
			0L,
			1L,
			"function",
			FUNCTION_ACTION,
			requestId(80),
			800L
		);
		RegisteredPlayerFunctionRunner.Context context = prepared.prepared()
			.orElseThrow()
			.functionContext(1L);
		CompoundTag macro = context.toMacroArguments();
		check(macro.getString("game_id").orElseThrow().equals(GAME.toString()), "game macro");
		check(
			macro.getString("game_instance_id").orElseThrow().equals(GAME_INSTANCE.toString()),
			"game instance macro"
		);
		check(
			macro.getString("action_id").orElseThrow().equals(FUNCTION_ACTION.toString()),
			"action macro"
		);
		check(
			macro.getString("player_uuid").orElseThrow().equals(PLAYER_A.toString()),
			"clicking player macro"
		);
		check(macro.getString("node_id").orElseThrow().equals("function"), "node macro");
		check(
			macro.getString("execution_context").orElseThrow().equals("player"),
			"execution context macro"
		);
		check(!macro.contains("player_field"), "runner must not expose arbitrary personal fields");

		expectRejected(
			() -> new RegisteredPlayerFunctionRunner.Context(
				GAME,
				GAME_INSTANCE,
				FUNCTION_ACTION,
				requestId(81),
				1L,
				PLAYER_A,
				"PlayerA",
				PHASE,
				PAGE,
				"function",
				SESSION_A,
				Optional.of(id("task/one")),
				Optional.empty(),
				0L,
				0L,
				GENERATION,
				1L,
				800L,
				ExecutionContext.SERVER
			),
			"half-present task function context must be rejected"
		);
	}

	private static Preparation prepare(
		final WorldStateV4 state,
		final DefinitionSnapshot definitions,
		final UUID player,
		final UUID sessionId,
		final UUID pageInstanceId,
		final long acceptedSequenceCount,
		final long requestSequence,
		final String nodeId,
		final Identifier actionId,
		final UUID requestId,
		final long worldTick
	) {
		return PlayerActionAuthority.prepare(
			state,
			definitions,
			session(
				state,
				player,
				sessionId,
				pageInstanceId,
				requestSequence
			),
			request(
				state,
				sessionId,
				pageInstanceId,
				requestSequence,
				nodeId,
				actionId,
				requestId
			),
			PlayerActionAuthority.PredicateEvaluator.allowAll(),
			worldTick
		);
	}

	private static SessionAuthorization session(
		final WorldStateV4 state,
		final UUID player,
		final UUID sessionId,
		final UUID pageInstanceId,
		final long nextSequence
	) {
		return new SessionAuthorization(
			GAME,
			player,
			sessionId,
			pageInstanceId,
			PAGE,
			GENERATION,
			state.stateRevision(),
			ROUTE_REVISION,
			nextSequence,
			true
		);
	}

	private static ActionRequest request(
		final WorldStateV4 state,
		final UUID sessionId,
		final UUID pageInstanceId,
		final long sequence,
		final String nodeId,
		final Identifier actionId,
		final UUID requestId
	) {
		return new ActionRequest(
			sessionId,
			pageInstanceId,
			GENERATION,
			state.stateRevision(),
			ROUTE_REVISION,
			sequence,
			requestId,
			nodeId,
			actionId
		);
	}

	private static WorldStateV4 finish(
		final Preparation prepared,
		final boolean success,
		final String code,
		final String summary
	) {
		check(prepared.accepted(), "cannot finish a denied preparation");
		WorldStateV4 committedPrepared = commit(prepared.nextState().orElseThrow());
		var finalized = PlayerActionAuthority.finalizeInvocation(
			committedPrepared,
			prepared.prepared().orElseThrow(),
			new ExecutionResult(success, code, summary)
		);
		check(finalized.finalized(), "prepared invocation must finalize");
		return commit(finalized.nextState().orElseThrow());
	}

	private static WorldStateV4 commit(final WorldStateV4 candidate) {
		long revision = Math.incrementExact(candidate.stateRevision());
		return candidate.withCore(
			candidate.core().withCore(candidate.core().core().withStateRevision(revision))
		);
	}

	private static WorldStateV4 state(final Identifier phase) {
		PlayerRecord a = player(PLAYER_A, "PlayerA");
		PlayerRecord b = player(PLAYER_B, "PlayerB");
		WorldStateV2 core = WorldStateV2.initial()
			.withActivity(GAME, phase)
			.withPlayers(Map.of(PLAYER_A, a, PLAYER_B, b));
		WorldStateV3 v3 = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(GAME_INSTANCE),
			Optional.empty(),
			List.of(),
			List.of()
		);
		return new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			v3,
			List.of(),
			List.of()
		);
	}

	private static PlayerRecord player(final UUID id, final String name) {
		return new PlayerRecord(
			id,
			name,
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
	}

	private static DefinitionSnapshot definitions() {
		Map<Identifier, PlayerActionDefinition> actions = Map.of(
			PERSONAL,
			action(
				PERSONAL,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.PLAYER,
				1,
				0L,
				CooldownScope.PLAYER,
				Optional.empty(),
				Optional.empty()
			),
			GLOBAL,
			action(
				GLOBAL,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.GAME,
				2,
				10L,
				CooldownScope.PLAYER,
				Optional.empty(),
				Optional.empty()
			),
			COOLDOWN,
			action(
				COOLDOWN,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.UNLIMITED,
				0,
				20L,
				CooldownScope.GAME,
				Optional.empty(),
				Optional.empty()
			),
			FUNCTION_ACTION,
			action(
				FUNCTION_ACTION,
				new PlayerRunFunctionOperation(FUNCTION),
				UsageScope.UNLIMITED,
				0,
				0L,
				CooldownScope.PLAYER,
				Optional.empty(),
				Optional.empty()
			),
			CONFIRMED_ACTION,
			action(
				CONFIRMED_ACTION,
				new PlayerRunFunctionOperation(FUNCTION),
				UsageScope.PLAYER,
				2,
				0L,
				CooldownScope.PLAYER,
				Optional.of(
					new Confirmation(text("确认执行"), List.of(text("将运行测试函数")))
				),
				Optional.empty()
			),
			PREDICATE_ACTION,
			action(
				PREDICATE_ACTION,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.UNLIMITED,
				0,
				0L,
				CooldownScope.PLAYER,
				Optional.empty(),
				Optional.of(PREDICATE)
			),
			NAVIGATION_COMPLEX,
			action(
				NAVIGATION_COMPLEX,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.PLAYER,
				1,
				200L,
				CooldownScope.PLAYER,
				Optional.of(
					new Confirmation(text("确认打开"), List.of(text("将打开测试页面")))
				),
				Optional.empty()
			),
			TASK_NAVIGATION,
			action(
				TASK_NAVIGATION,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.UNLIMITED,
				0,
				0L,
				CooldownScope.PLAYER,
				Optional.empty(),
				Optional.empty(),
				Set.of(REQUIRED_TASK),
				Set.of()
			),
			TASK_STATE_NAVIGATION,
			action(
				TASK_STATE_NAVIGATION,
				new PlayerOpenPageOperation(PAGE),
				UsageScope.UNLIMITED,
				0,
				0L,
				CooldownScope.PLAYER,
				Optional.empty(),
				Optional.empty(),
				Set.of(),
				Set.of(PlayerTaskState.RUNNING)
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
						button("personal", PERSONAL),
						button("global", GLOBAL),
						button("cooldown", COOLDOWN),
						button("function", FUNCTION_ACTION),
						button("confirmed", CONFIRMED_ACTION),
						button("predicate", PREDICATE_ACTION),
						button("navigation_complex", NAVIGATION_COMPLEX),
						button("task_navigation", TASK_NAVIGATION),
						button("task_state_navigation", TASK_STATE_NAVIGATION)
					)
				),
				"/root"
			),
			List.of(),
			"{}",
			"page-hash"
		);
		GameDefinition game = new GameDefinition(
			GAME,
			3,
			1,
			text("测试游戏"),
			PHASE,
			ROLE,
			LIFE,
			Optional.empty(),
			Optional.empty(),
			Optional.of(new PlayerTerminalConfig(PAGE, true))
		);
		RoleDefinition role = new RoleDefinition(
			ROLE,
			GAME,
			text("逃走者"),
			Optional.empty(),
			Set.of(),
			new TabStyle(Optional.empty(), Optional.empty())
		);
		LifeStateDefinition life = new LifeStateDefinition(
			LIFE,
			GAME,
			text("存活"),
			Optional.empty(),
			Set.of()
		);
		PhaseDefinition phase = new PhaseDefinition(
			PHASE,
			GAME,
			text("大厅"),
			Set.of(),
			Optional.empty(),
			Optional.empty()
		);
		PhaseDefinition otherPhase = new PhaseDefinition(
			OTHER_PHASE,
			GAME,
			text("其他"),
			Set.of(),
			Optional.empty(),
			Optional.empty()
		);
		return new DefinitionSnapshot(
			GENERATION,
			Map.of(GAME, game),
			Map.of(ROLE, role),
			Map.of(),
			Map.of(LIFE, life),
			Map.of(PHASE, phase, OTHER_PHASE, otherPhase),
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
			Set.of(PREDICATE),
			Map.of(PREDICATE, "{}")
		);
	}

	private static PlayerActionDefinition action(
		final Identifier id,
		final io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOperation operation,
		final UsageScope usage,
		final int maximum,
		final long cooldown,
		final CooldownScope cooldownScope,
		final Optional<Confirmation> confirmation,
		final Optional<Identifier> predicate
	) {
		return action(
			id,
			operation,
			usage,
			maximum,
			cooldown,
			cooldownScope,
			confirmation,
			predicate,
			Set.of(),
			Set.of()
		);
	}

	private static PlayerActionDefinition action(
		final Identifier id,
		final io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOperation operation,
		final UsageScope usage,
		final int maximum,
		final long cooldown,
		final CooldownScope cooldownScope,
		final Optional<Confirmation> confirmation,
		final Optional<Identifier> predicate,
		final Set<Identifier> tasks,
		final Set<PlayerTaskState> taskStates
	) {
		return new PlayerActionDefinition(
			id,
			GAME,
			operation,
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
			tasks,
			taskStates,
			predicate,
			usage,
			maximum,
			cooldown,
			cooldownScope,
			confirmation,
			ExecutionContext.PLAYER,
			PlayerActionFeedback.empty(),
			AuditPolicy.ALL
		);
	}

	private static NodeDefinition button(final String nodeId, final Identifier actionId) {
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
				new UiAction(ActionType.REGISTERED, Optional.empty(), Optional.of(actionId))
			),
			"/root/" + nodeId
		);
	}

	private static RichText text(final String value) {
		return new RichText("{\"text\":\"" + value + "\"}", value);
	}

	private static UUID requestId(final long value) {
		return new UUID(0x2000000000000000L, value);
	}

	private static Identifier id(final String path) {
		return Identifier.parse("pixel_tzz:" + path);
	}

	private static void expectRejected(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// Expected contract rejection.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
