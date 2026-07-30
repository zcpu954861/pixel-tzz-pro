package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.AuditPolicy;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.CooldownScope;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.ExecutionContext;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionFeedback;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOpenPageOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.UsageScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Confirmation;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionInvocation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionInvocationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionUsage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure, server-authoritative transaction layer for data-pack registered player actions.
 *
 * <p>The client never authorizes an operation. It only names the button it believes it pressed.
 * This class rebinds that node against the currently published page tree, resolves the referenced
 * action again, and re-evaluates every game/player/audience/phase/task/predicate constraint.
 *
 * <p>Accepted actions are deliberately split into two durable transactions:
 *
 * <ol>
 *   <li>{@link #prepare} appends a {@code PREPARED} invocation and consumes usage/cooldown.</li>
 *   <li>The runtime commits that state, performs the external operation, then calls
 *       {@link #finalizeInvocation} and commits the terminal result.</li>
 * </ol>
 *
 * <p>A persisted {@code PREPARED} request is outcome-unknown. It is never executed a second time.
 * Failed callbacks still consume their accepted attempt; rolling a usage counter back would make
 * a crash or deliberately failing function a limit-bypass primitive.
 */
public final class PlayerActionAuthority {
	public static final int MAX_MESSAGE_LENGTH = 512;
	public static final int MAX_AUDIT_SUMMARY_LENGTH = 512;
	private static final byte[] REQUEST_ID_DOMAIN =
		"pixel-tzz-pro/player-action-request/v1\u0000".getBytes(StandardCharsets.UTF_8);

	private PlayerActionAuthority() {
	}

	/**
	 * Derives the durable invocation ID from server-owned terminal session state.
	 *
	 * <p>The runtime must call this only after selecting the accepted request sequence from its
	 * session ledger. Neither input may be replaced by a client-selected persistent UUID. The
	 * domain separator and RFC 4122 version/variant bits keep the result stable while preventing
	 * accidental reuse of another UUID derivation namespace.
	 */
	public static UUID deriveRequestId(
		final UUID terminalSessionId,
		final long acceptedRequestSequence
	) {
		Objects.requireNonNull(terminalSessionId, "terminalSessionId");
		if (acceptedRequestSequence < 0L) {
			throw new IllegalArgumentException("acceptedRequestSequence must be non-negative");
		}
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
		digest.update(REQUEST_ID_DOMAIN);
		ByteBuffer input = ByteBuffer.allocate(Long.BYTES * 3);
		input.putLong(terminalSessionId.getMostSignificantBits());
		input.putLong(terminalSessionId.getLeastSignificantBits());
		input.putLong(acceptedRequestSequence);
		byte[] hash = digest.digest(input.array());
		hash[6] = (byte)((hash[6] & 0x0F) | 0x50);
		hash[8] = (byte)((hash[8] & 0x3F) | 0x80);
		ByteBuffer uuid = ByteBuffer.wrap(hash);
		return new UUID(uuid.getLong(), uuid.getLong());
	}

	/**
	 * Revalidates the registered {@code open_page} ancestry for one retained navigation frame.
	 *
	 * <p>This check is deliberately side-effect free. It rebinds the source page, node, action and
	 * target page against the supplied definition generation, then re-evaluates the current player,
	 * host, audience, phase, task, task-state and predicate constraints. It does not consume or
	 * inspect usage, cooldown, confirmation challenges, invocation ledgers or audit records.
	 *
	 * <p>The caller remains responsible for verifying that
	 * {@link PlayerTerminalSessions.NavigationGrant#sourcePageInstanceId()} identifies the actual
	 * parent frame in its server-owned session stack.
	 */
	public static NavigationValidation validateNavigationGrant(
		final WorldStateV4 state,
		final DefinitionSnapshot definitions,
		final UUID playerId,
		final boolean online,
		final PlayerTerminalSessions.NavigationGrant grant,
		final Identifier targetPageId,
		final PredicateEvaluator predicates
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(grant, "grant");
		Objects.requireNonNull(targetPageId, "targetPageId");
		Objects.requireNonNull(predicates, "predicates");
		if (state.validated().error().isPresent()) {
			return NavigationValidation.denied(Code.SCHEMA_BLOCKED, "当前世界状态不可读");
		}
		Identifier gameId = state.core().core().activeGameId().orElse(null);
		UUID gameInstanceId = state.core().activeGameInstanceId().orElse(null);
		if (gameId == null || gameInstanceId == null) {
			return NavigationValidation.denied(
				Code.GAME_MISMATCH,
				"当前游戏实例与玩家终端不一致"
			);
		}
		PlayerRecord player = state.core().core().players().get(playerId);
		if (player == null) {
			return NavigationValidation.denied(
				Code.PLAYER_NOT_REGISTERED,
				"玩家尚未注册到当前游戏"
			);
		}
		if (
			state.core().core().host()
				.map(host -> host.playerId().equals(playerId))
				.orElse(false)
		) {
			return NavigationValidation.denied(
				Code.HOST_FORBIDDEN,
				"主持人不能保留普通玩家终端子页面"
			);
		}

		var sourcePage = definitions.pages().get(grant.sourcePageId());
		if (sourcePage == null || !sourcePage.game().equals(gameId)) {
			return NavigationValidation.denied(
				Code.PAGE_UNAVAILABLE,
				"玩家终端来源页面已不可用"
			);
		}
		ReboundButton rebound = rebind(
			sourcePage.root(),
			grant.sourceNodeId(),
			grant.actionId()
		);
		if (!rebound.matched()) {
			return NavigationValidation.denied(Code.NODE_MISMATCH, rebound.message());
		}

		PlayerActionDefinition action = definitions.playerActions().get(grant.actionId());
		if (action == null || !action.game().equals(gameId)) {
			return NavigationValidation.denied(
				Code.ACTION_UNAVAILABLE,
				"页面跳转操作已不可用"
			);
		}
		if (
			!(action.operation() instanceof PlayerOpenPageOperation openPage)
				|| !openPage.page().equals(targetPageId)
				|| action.executionContext() != ExecutionContext.PLAYER
		) {
			return NavigationValidation.denied(
				Code.ACTION_UNAVAILABLE,
				"页面跳转目标与注册操作不一致"
			);
		}
		var targetPage = definitions.pages().get(targetPageId);
		if (targetPage == null || !targetPage.game().equals(gameId)) {
			return NavigationValidation.denied(
				Code.PAGE_UNAVAILABLE,
				"玩家终端目标页面已不可用"
			);
		}
		if (
			!AudienceMatcher.matches(
				definitions,
				action.audience(),
				player,
				state.core().core().host().map(host -> host.playerId()),
				online
			)
		) {
			return NavigationValidation.denied(
				Code.AUDIENCE_MISMATCH,
				"当前玩家身份不满足页面跳转的受众条件"
			);
		}

		Identifier phaseId = state.core().core().activePhaseId().orElse(null);
		if (
			phaseId == null
				|| (
					!action.phases().isEmpty()
						&& !action.phases().contains(phaseId)
				)
		) {
			return NavigationValidation.denied(
				Code.PHASE_MISMATCH,
				"当前游戏阶段不允许保留该页面"
			);
		}
		TaskContext task = currentTask(state);
		if (
			!action.tasks().isEmpty()
				&& task.taskId().filter(action.tasks()::contains).isEmpty()
		) {
			return NavigationValidation.denied(
				Code.TASK_MISMATCH,
				"当前任务不允许保留该页面"
			);
		}
		if (
			!action.taskStates().isEmpty()
				&& task.state().filter(action.taskStates()::contains).isEmpty()
		) {
			return NavigationValidation.denied(
				Code.TASK_STATE_MISMATCH,
				"当前任务状态不允许保留该页面"
			);
		}
		if (action.predicate().isPresent()) {
			boolean allowed;
			try {
				allowed = predicates.test(
					action.predicate().orElseThrow(),
					new PredicateContext(
						gameId,
						gameInstanceId,
						phaseId,
						player,
						task.taskId(),
						task.state()
					)
				);
			} catch (RuntimeException error) {
				return NavigationValidation.denied(
					Code.PREDICATE_FAILED,
					"页面跳转条件校验失败"
				);
			}
			if (!allowed) {
				return NavigationValidation.denied(
					Code.PREDICATE_DENIED,
					"当前条件不允许保留该页面"
				);
			}
		}
		return NavigationValidation.permitted();
	}

	/**
	 * Revalidates and reserves one action invocation.
	 *
	 * <p>The returned state intentionally keeps the same core state revision. The caller must pass
	 * it through {@code PixelTzzWorldState.commitV4}, which performs the optimistic revision bump.
	 */
	public static Preparation prepare(
		final WorldStateV4 state,
		final DefinitionSnapshot definitions,
		final SessionAuthorization session,
		final ActionRequest request,
		final PredicateEvaluator predicates,
		final long worldTick
	) {
		return prepare(
			state,
			definitions,
			session,
			request,
			predicates,
			worldTick,
			Optional.empty()
		);
	}

	/**
	 * Revalidates and reserves an action after the runtime optionally consumes a confirmation
	 * challenge.
	 *
	 * <p>{@code confirmedRequest} is a server-owned authorization, not a packet field. The runtime
	 * may create it only after consuming a one-time challenge token. Every bound identity is checked
	 * again here so a token issued for another page, button, action, or request cannot be reused.
	 */
	public static Preparation prepare(
		final WorldStateV4 state,
		final DefinitionSnapshot definitions,
		final SessionAuthorization session,
		final ActionRequest request,
		final PredicateEvaluator predicates,
		final long worldTick,
		final Optional<ConfirmedRequest> confirmedRequest
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(predicates, "predicates");
		Objects.requireNonNull(confirmedRequest, "confirmedRequest");
		if (worldTick < 0L) {
			return Preparation.denied(Code.CLOCK_INVALID, "服务器动作时钟无效");
		}
		if (state.validated().error().isPresent()) {
			return Preparation.denied(Code.SCHEMA_BLOCKED, "当前世界状态不可写");
		}

		/*
		 * A packet may be retried after the PREPARED commit advanced the state revision and the
		 * in-memory session sequence. Authenticate its stable session/page/action bindings first,
		 * then return the durable result before applying fresh-request revision checks.
		 */
		Preparation durableReplay = durableReplay(
			state,
			definitions,
			session,
			request
		);
		if (durableReplay != null) {
			return durableReplay;
		}

		Preparation sessionFailure = validateSession(state, definitions, session, request);
		if (sessionFailure != null) {
			return sessionFailure;
		}

		PlayerRecord player = state.core().core().players().get(session.playerId());
		if (player == null) {
			return Preparation.denied(Code.PLAYER_NOT_REGISTERED, "玩家尚未注册到当前游戏");
		}
		if (
			state.core().core().host()
				.map(host -> host.playerId().equals(player.playerId()))
				.orElse(false)
		) {
			return Preparation.denied(Code.HOST_FORBIDDEN, "主持人不能通过普通玩家终端执行此操作");
		}

		var page = definitions.pages().get(session.pageId());
		if (page == null || !page.game().equals(session.gameId())) {
			return Preparation.denied(Code.PAGE_UNAVAILABLE, "当前玩家页面已失效");
		}
		ReboundButton rebound = rebind(page.root(), request.nodeId(), request.actionId());
		if (!rebound.matched()) {
			return Preparation.denied(Code.NODE_MISMATCH, rebound.message());
		}

		PlayerActionDefinition action = definitions.playerActions().get(request.actionId());
		if (action == null || !action.game().equals(session.gameId())) {
			return Preparation.denied(Code.ACTION_UNAVAILABLE, "该页面操作已不可用");
		}
		Optional<PlayerActionFeedback> feedback = Optional.of(action.feedback());
		if (
			action.cooldownTicks() < 0L
				|| (
					action.usageScope() == UsageScope.UNLIMITED
						&& action.maximumUses() != 0
				)
				|| (
					action.usageScope() != UsageScope.UNLIMITED
						&& action.maximumUses() < 1
				)
		) {
			return Preparation.denied(
				Code.ACTION_UNAVAILABLE,
				"该操作的次数或冷却定义无效",
				feedback
			);
		}

		if (
			!AudienceMatcher.matches(
				definitions,
				action.audience(),
				player,
				state.core().core().host().map(host -> host.playerId()),
				session.online()
			)
		) {
			return Preparation.denied(
				Code.AUDIENCE_MISMATCH,
				"当前玩家身份不满足该操作的受众条件",
				feedback
			);
		}

		Identifier phaseId = state.core().core().activePhaseId().orElse(null);
		if (
			phaseId == null
				|| (
					!action.phases().isEmpty()
						&& !action.phases().contains(phaseId)
				)
		) {
			return Preparation.denied(
				Code.PHASE_MISMATCH,
				"当前游戏阶段不允许执行该操作",
				feedback
			);
		}

		TaskContext task = currentTask(state);
		if (
			!action.tasks().isEmpty()
				&& task.taskId().filter(action.tasks()::contains).isEmpty()
		) {
			return Preparation.denied(
				Code.TASK_MISMATCH,
				"当前任务不允许执行该操作",
				feedback
			);
		}
		if (
			!action.taskStates().isEmpty()
				&& task.state().filter(action.taskStates()::contains).isEmpty()
		) {
			return Preparation.denied(
				Code.TASK_STATE_MISMATCH,
				"当前任务状态不允许执行该操作",
				feedback
			);
		}

		if (action.predicate().isPresent()) {
			boolean allowed;
			try {
				allowed = predicates.test(
					action.predicate().orElseThrow(),
					new PredicateContext(
						session.gameId(),
						state.core().activeGameInstanceId().orElseThrow(),
						phaseId,
						player,
						task.taskId(),
						task.state()
					)
				);
			} catch (RuntimeException error) {
				return Preparation.denied(
					Code.PREDICATE_FAILED,
					"操作条件校验失败",
					feedback
				);
			}
			if (!allowed) {
				return Preparation.denied(
					Code.PREDICATE_DENIED,
					"当前条件不允许执行该操作",
					feedback
				);
			}
		}

		PreparedTarget target = target(definitions, action);
		if (target == null) {
			return Preparation.denied(
				Code.ACTION_UNAVAILABLE,
				"该操作引用的页面或函数已不可用",
				feedback
			);
		}

		UUID gameInstanceId = state.core().activeGameInstanceId().orElseThrow();
		PlayerActionInvocation duplicate = duplicateInvocation(
			state,
			gameInstanceId,
			player.playerId(),
			request.requestId()
		);
		if (duplicate != null) {
			if (!duplicate.actionId().equals(action.id())) {
				return Preparation.denied(
					Code.REQUEST_REPLAYED,
					"该请求编号已被其他操作使用",
					feedback
				);
			}
			if (duplicate.status() == PlayerActionInvocationStatus.PREPARED) {
				return Preparation.outcomeUnknown(duplicate, feedback);
			}
			return Preparation.replayed(duplicate, feedback);
		}

		if (action.confirmation().isPresent()) {
			ConfirmedRequest confirmed = confirmedRequest.orElse(null);
			if (confirmed == null) {
				return Preparation.confirmationRequired(
					new ConfirmationChallenge(
						session.sessionId(),
						session.pageInstanceId(),
						request.nodeId(),
						action.id(),
						request.requestId(),
						request.requestSequence(),
						definitions.generation(),
						session.routeRevision(),
						action.confirmation().orElseThrow()
					),
					feedback
				);
			}
			if (!confirmed.matches(session, request, action.id(), definitions.generation())) {
				return Preparation.denied(
					Code.CONFIRMATION_INVALID,
					"二次确认凭据与当前页面操作不匹配",
					feedback
				);
			}
		} else if (confirmedRequest.isPresent()) {
			return Preparation.denied(
				Code.CONFIRMATION_INVALID,
				"该操作不接受二次确认凭据",
				feedback
			);
		}

		Optional<UUID> usageBucket = switch (action.usageScope()) {
			case UNLIMITED -> null;
			case PLAYER -> Optional.of(player.playerId());
			case GAME -> Optional.empty();
		};
		Optional<UUID> cooldownBucket = action.cooldownTicks() == 0L
			? null
			: switch (action.cooldownScope()) {
				case PLAYER -> Optional.of(player.playerId());
				case GAME -> Optional.empty();
			};

		if (usageBucket != null) {
			long uses = state.usage(gameInstanceId, action.id(), usageBucket)
				.map(PlayerActionUsage::uses)
				.orElse(0L);
			if (uses >= action.maximumUses()) {
				return Preparation.denied(
					Code.USAGE_EXHAUSTED,
					"该操作的可用次数已耗尽",
					feedback
				);
			}
		}
		if (cooldownBucket != null) {
			PlayerActionUsage cooldown = state.usage(
				gameInstanceId,
				action.id(),
				cooldownBucket
			).orElse(null);
			if (cooldown != null) {
				if (worldTick < cooldown.lastUsedWorldTick()) {
					return Preparation.denied(
						Code.CLOCK_INVALID,
						"服务器动作时钟发生回退，操作已安全阻止",
						feedback
					);
				}
				if (worldTick - cooldown.lastUsedWorldTick() < action.cooldownTicks()) {
					return Preparation.denied(
						Code.COOLDOWN_ACTIVE,
						"该操作仍在冷却中",
						feedback
					);
				}
			}
		}

		Set<Optional<UUID>> buckets = new LinkedHashSet<>();
		if (usageBucket != null) {
			buckets.add(usageBucket);
		}
		if (cooldownBucket != null) {
			buckets.add(cooldownBucket);
		}
		long newBuckets = buckets.stream()
			.filter(bucket -> state.usage(gameInstanceId, action.id(), bucket).isEmpty())
			.count();
		if (state.usages().size() + newBuckets > WorldStateV4.MAX_PLAYER_ACTION_USAGES) {
			return Preparation.denied(
				Code.LEDGER_FULL,
				"玩家操作计数账本已满，操作已安全阻止",
				feedback
			);
		}

		final List<PlayerActionUsage> usages;
		try {
			usages = updateUsages(
				state.usages(),
				gameInstanceId,
				action.id(),
				buckets,
				worldTick
			);
		} catch (ArithmeticException error) {
			return Preparation.denied(
				Code.LEDGER_FULL,
				"玩家操作计数已达到安全上限",
				feedback
			);
		}

		List<PlayerActionInvocation> invocations = makeInvocationRoom(state.invocations());
		if (invocations == null) {
			return Preparation.denied(
				Code.LEDGER_FULL,
				"玩家操作结果账本已满，存在不能安全淘汰的待定请求",
				feedback
			);
		}
		invocations.add(
			new PlayerActionInvocation(
				gameInstanceId,
				player.playerId(),
				action.id(),
				request.requestId(),
				PlayerActionInvocationStatus.PREPARED,
				Optional.empty(),
				Optional.empty(),
				worldTick
			)
		);
		WorldStateV4 candidate = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			state.core(),
			usages,
			invocations
		);
		if (candidate.validated().error().isPresent()) {
			return Preparation.denied(
				Code.INTERNAL_ERROR,
				"玩家操作预提交状态未通过校验",
				feedback
			);
		}

		TimelineInstance timeline = state.core().timeline().orElse(null);
		TaskInstance taskInstance = timeline == null
			? null
			: timeline.currentTask().orElse(null);
		PreparedAction prepared = new PreparedAction(
			gameInstanceId,
			session.gameId(),
			player.playerId(),
			player.lastKnownName(),
			action.id(),
			request.requestId(),
			request.requestSequence(),
			session.sessionId(),
			session.pageId(),
			session.pageInstanceId(),
			request.nodeId(),
			phaseId,
			task.taskId(),
			Optional.ofNullable(taskInstance).map(TaskInstance::taskInstanceId),
			timeline == null ? 0L : timeline.gameElapsedTicks(),
			taskInstance == null ? 0L : taskInstance.elapsedTicks(),
			definitions.generation(),
			state.stateRevision(),
			worldTick,
			target,
			action.feedback(),
			action.audit()
		);
		return Preparation.prepared(candidate, prepared);
	}

	/**
	 * Replaces exactly one matching PREPARED ledger record with a terminal result.
	 *
	 * <p>The method is safe to retry against a newer state after an optimistic commit conflict. A
	 * matching terminal record is treated as idempotent; a conflicting result is rejected.
	 */
	public static Finalization finalizeInvocation(
		final WorldStateV4 state,
		final PreparedAction prepared,
		final ExecutionResult execution
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(prepared, "prepared");
		Objects.requireNonNull(execution, "execution");
		if (state.validated().error().isPresent()) {
			return Finalization.rejected(Code.SCHEMA_BLOCKED, "当前世界状态不可写");
		}
		if (
			state.core().activeGameInstanceId().filter(prepared.gameInstanceId()::equals).isEmpty()
				|| state.core().core().activeGameId().filter(prepared.gameId()::equals).isEmpty()
		) {
			return Finalization.rejected(Code.GAME_MISMATCH, "玩家操作所属的游戏实例已结束");
		}

		int found = -1;
		for (int index = 0; index < state.invocations().size(); index++) {
			PlayerActionInvocation value = state.invocations().get(index);
			if (
				value.gameInstanceId().equals(prepared.gameInstanceId())
					&& value.playerId().equals(prepared.playerId())
					&& value.actionId().equals(prepared.actionId())
					&& value.requestId().equals(prepared.requestId())
			) {
				found = index;
				break;
			}
		}
		if (found < 0) {
			return Finalization.rejected(Code.INVOCATION_MISSING, "找不到待完成的玩家操作");
		}

		PlayerActionInvocation current = state.invocations().get(found);
		PlayerActionInvocationStatus status = execution.success()
			? PlayerActionInvocationStatus.SUCCEEDED
			: PlayerActionInvocationStatus.FAILED;
		Optional<String> summary = execution.summary().isBlank()
			? Optional.empty()
			: Optional.of(execution.summary());
		if (current.status() != PlayerActionInvocationStatus.PREPARED) {
			boolean same = current.status() == status
				&& current.resultCode().equals(Optional.of(execution.code()))
				&& current.resultSummary().equals(summary);
			return same
				? Finalization.alreadyFinalized(state, current, audit(prepared, execution))
				: Finalization.rejected(
					Code.INVOCATION_FINAL,
					"玩家操作已经记录了不同的最终结果"
				);
		}

		PlayerActionInvocation terminal = new PlayerActionInvocation(
			current.gameInstanceId(),
			current.playerId(),
			current.actionId(),
			current.requestId(),
			status,
			Optional.of(execution.code()),
			summary,
			current.usedAtWorldTick()
		);
		List<PlayerActionInvocation> invocations = new ArrayList<>(state.invocations());
		invocations.set(found, terminal);
		WorldStateV4 candidate = state.withInvocations(invocations);
		if (candidate.validated().error().isPresent()) {
			return Finalization.rejected(
				Code.INTERNAL_ERROR,
				"玩家操作最终状态未通过校验"
			);
		}
		return Finalization.finalized(candidate, terminal, audit(prepared, execution));
	}

	private static Preparation validateSession(
		final WorldStateV4 state,
		final DefinitionSnapshot definitions,
		final SessionAuthorization session,
		final ActionRequest request
	) {
		if (
			!session.sessionId().equals(request.sessionId())
				|| !session.pageInstanceId().equals(request.pageInstanceId())
		) {
			return Preparation.denied(Code.SESSION_STALE, "玩家终端会话已经变化");
		}
		if (
			session.definitionGeneration() != definitions.generation()
				|| request.definitionGeneration() != definitions.generation()
		) {
			return Preparation.denied(Code.DEFINITION_GENERATION_STALE, "数据包定义已经更新");
		}
		if (
			session.routeRevision() != request.routeRevision()
		) {
			return Preparation.denied(Code.ROUTE_REVISION_STALE, "玩家终端路由已经更新");
		}
		if (
			session.stateRevision() != request.stateRevision()
				|| request.stateRevision() != state.stateRevision()
		) {
			return Preparation.denied(Code.STATE_REVISION_STALE, "游戏状态已经更新");
		}
		if (session.nextRequestSequence() != request.requestSequence()) {
			return Preparation.denied(Code.REQUEST_REPLAYED, "终端请求序号已经使用或尚未到达");
		}
		if (!session.online()) {
			return Preparation.denied(Code.PLAYER_OFFLINE, "玩家当前不在线");
		}
		if (
			state.core().core().activeGameId().filter(session.gameId()::equals).isEmpty()
				|| state.core().activeGameInstanceId().isEmpty()
		) {
			return Preparation.denied(Code.GAME_MISMATCH, "当前游戏实例与终端会话不一致");
		}
		return null;
	}

	private static Preparation durableReplay(
		final WorldStateV4 state,
		final DefinitionSnapshot definitions,
		final SessionAuthorization session,
		final ActionRequest request
	) {
		if (
			!session.sessionId().equals(request.sessionId())
				|| !session.pageInstanceId().equals(request.pageInstanceId())
				|| session.definitionGeneration() != definitions.generation()
				|| request.definitionGeneration() != definitions.generation()
				|| session.routeRevision() != request.routeRevision()
				|| state.core().core().activeGameId().filter(session.gameId()::equals).isEmpty()
				|| state.core().activeGameInstanceId().isEmpty()
		) {
			return null;
		}
		var page = definitions.pages().get(session.pageId());
		if (
			page == null
				|| !page.game().equals(session.gameId())
				|| !rebind(page.root(), request.nodeId(), request.actionId()).matched()
		) {
			return null;
		}
		PlayerActionDefinition action = definitions.playerActions().get(request.actionId());
		if (action == null || !action.game().equals(session.gameId())) {
			return null;
		}
		PlayerActionInvocation duplicate = duplicateInvocation(
			state,
			state.core().activeGameInstanceId().orElseThrow(),
			session.playerId(),
			request.requestId()
		);
		if (duplicate == null) {
			return null;
		}
		Optional<PlayerActionFeedback> feedback = Optional.of(action.feedback());
		if (!duplicate.actionId().equals(action.id())) {
			return Preparation.denied(
				Code.REQUEST_REPLAYED,
				"该请求编号已被其他操作使用",
				feedback
			);
		}
		return duplicate.status() == PlayerActionInvocationStatus.PREPARED
			? Preparation.outcomeUnknown(duplicate, feedback)
			: Preparation.replayed(duplicate, feedback);
	}

	private static ReboundButton rebind(
		final NodeDefinition root,
		final String nodeId,
		final Identifier actionId
	) {
		List<NodeDefinition> matches = new ArrayList<>();
		collectNodes(root, nodeId, matches);
		if (matches.size() != 1) {
			return ReboundButton.failed(
				matches.isEmpty()
					? "当前页面不存在该按钮"
					: "当前页面存在重复按钮标识，已安全阻止操作"
			);
		}
		if (!(matches.getFirst().content() instanceof ButtonContent button)) {
			return ReboundButton.failed("该页面节点不是可执行按钮");
		}
		if (
			button.action().type() != ActionType.REGISTERED
				|| button.action().registeredAction().filter(actionId::equals).isEmpty()
		) {
			return ReboundButton.failed("按钮绑定的操作与请求不一致");
		}
		return ReboundButton.success();
	}

	private static void collectNodes(
		final NodeDefinition node,
		final String nodeId,
		final List<NodeDefinition> matches
	) {
		if (node.id().filter(nodeId::equals).isPresent()) {
			matches.add(node);
		}
		switch (node.content()) {
			case ChildrenContent children ->
				children.children().forEach(child -> collectNodes(child, nodeId, matches));
			case SingleChildContent child -> collectNodes(child.child(), nodeId, matches);
			case RepeatContent repeat -> collectNodes(repeat.template(), nodeId, matches);
			default -> {
				// Leaf node.
			}
		}
	}

	private static PreparedTarget target(
		final DefinitionSnapshot definitions,
		final PlayerActionDefinition action
	) {
		if (action.operation() instanceof PlayerOpenPageOperation openPage) {
			var page = definitions.pages().get(openPage.page());
			return page != null
					&& page.game().equals(action.game())
					&& action.executionContext() == ExecutionContext.PLAYER
				? new PushPage(openPage.page())
				: null;
		}
		if (action.operation() instanceof PlayerRunFunctionOperation runFunction) {
			return definitions.functions().contains(runFunction.function())
				? new RunFunction(runFunction.function(), action.executionContext())
				: null;
		}
		return null;
	}

	private static TaskContext currentTask(final WorldStateV4 state) {
		TimelineInstance timeline = state.core().timeline().orElse(null);
		if (timeline == null) {
			return TaskContext.empty();
		}
		TaskInstance task = timeline.currentTask().orElse(null);
		Optional<Identifier> taskId = Optional.ofNullable(task).map(TaskInstance::taskId);
		if (timeline.paused() && task != null) {
			return new TaskContext(taskId, Optional.of(PlayerTaskState.PAUSED));
		}
		if (task != null) {
			PlayerTaskState taskState = switch (task.status()) {
				case STARTING -> PlayerTaskState.STARTING;
				case RUNNING -> PlayerTaskState.RUNNING;
				case SETTLING -> PlayerTaskState.SETTLING;
				case SETTLED -> task.intermission().isPresent()
					? PlayerTaskState.INTERMISSION
					: PlayerTaskState.SETTLED;
				case INTERRUPTED -> PlayerTaskState.INTERRUPTED;
				case BLOCKED -> PlayerTaskState.BLOCKED;
			};
			return new TaskContext(taskId, Optional.of(taskState));
		}
		PlayerTaskState terminalState = switch (timeline.status()) {
			case COMPLETED -> PlayerTaskState.COMPLETED;
			case INTERRUPTED -> PlayerTaskState.INTERRUPTED;
			default -> null;
		};
		return new TaskContext(Optional.empty(), Optional.ofNullable(terminalState));
	}

	private static PlayerActionInvocation duplicateInvocation(
		final WorldStateV4 state,
		final UUID gameInstanceId,
		final UUID playerId,
		final UUID requestId
	) {
		return state.invocations()
			.stream()
			.filter(value -> value.gameInstanceId().equals(gameInstanceId))
			.filter(value -> value.playerId().equals(playerId))
			.filter(value -> value.requestId().equals(requestId))
			.findFirst()
			.orElse(null);
	}

	private static List<PlayerActionUsage> updateUsages(
		final List<PlayerActionUsage> current,
		final UUID gameInstanceId,
		final Identifier actionId,
		final Set<Optional<UUID>> buckets,
		final long worldTick
	) {
		List<PlayerActionUsage> result = new ArrayList<>(current);
		for (Optional<UUID> bucket : buckets) {
			int found = -1;
			for (int index = 0; index < result.size(); index++) {
				PlayerActionUsage value = result.get(index);
				if (
					value.gameInstanceId().equals(gameInstanceId)
						&& value.actionId().equals(actionId)
						&& value.playerId().equals(bucket)
				) {
					found = index;
					break;
				}
			}
			if (found < 0) {
				result.add(
					new PlayerActionUsage(
						gameInstanceId,
						actionId,
						bucket,
						1L,
						worldTick
					)
				);
			} else {
				PlayerActionUsage previous = result.get(found);
				result.set(
					found,
					new PlayerActionUsage(
						gameInstanceId,
						actionId,
						bucket,
						Math.incrementExact(previous.uses()),
						worldTick
					)
				);
			}
		}
		return result;
	}

	/**
	 * Makes exactly one slot by evicting the oldest terminal record. PREPARED records are never
	 * evicted because doing so could turn an outcome-unknown external mutation into a replay.
	 */
	private static List<PlayerActionInvocation> makeInvocationRoom(
		final List<PlayerActionInvocation> current
	) {
		List<PlayerActionInvocation> result = new ArrayList<>(current);
		if (result.size() < WorldStateV4.MAX_PLAYER_ACTION_INVOCATIONS) {
			return result;
		}
		PlayerActionInvocation removable = result.stream()
			.filter(value -> value.status() != PlayerActionInvocationStatus.PREPARED)
			.min(
				Comparator
					.comparingLong(PlayerActionInvocation::usedAtWorldTick)
					.thenComparing(value -> value.requestId().toString())
			)
			.orElse(null);
		if (removable == null) {
			return null;
		}
		result.remove(removable);
		return result;
	}

	private static Optional<AuditEntry> audit(
		final PreparedAction prepared,
		final ExecutionResult execution
	) {
		if (
			prepared.audit() == AuditPolicy.NONE
				|| (
					prepared.audit() == AuditPolicy.FAILURES
						&& execution.success()
				)
		) {
			return Optional.empty();
		}
		String summary = execution.summary().isBlank()
			? execution.code()
			: execution.code() + ": " + execution.summary();
		return Optional.of(
			new AuditEntry(
				prepared.gameInstanceId(),
				prepared.playerId(),
				prepared.actionId(),
				prepared.requestId(),
				execution.success(),
				bounded(summary, MAX_AUDIT_SUMMARY_LENGTH)
			)
		);
	}

	private static String bounded(final String value, final int maximum) {
		String normalized = Objects.requireNonNull(value, "value");
		return normalized.length() <= maximum
			? normalized
			: normalized.substring(0, maximum - 3) + "...";
	}

	@FunctionalInterface
	public interface PredicateEvaluator {
		boolean test(Identifier predicate, PredicateContext context);

		static PredicateEvaluator allowAll() {
			return (predicate, context) -> true;
		}
	}

	public record PredicateContext(
		Identifier gameId,
		UUID gameInstanceId,
		Identifier phaseId,
		PlayerRecord player,
		Optional<Identifier> taskId,
		Optional<PlayerTaskState> taskState
	) {
		public PredicateContext {
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(phaseId, "phaseId");
			Objects.requireNonNull(player, "player");
			taskId = Objects.requireNonNull(taskId, "taskId");
			taskState = Objects.requireNonNull(taskState, "taskState");
		}
	}

	/**
	 * Server-owned normal-terminal page identity. Do not construct this record from packet values.
	 */
	public record SessionAuthorization(
		Identifier gameId,
		UUID playerId,
		UUID sessionId,
		UUID pageInstanceId,
		Identifier pageId,
		long definitionGeneration,
		long stateRevision,
		long routeRevision,
		long nextRequestSequence,
		boolean online
	) {
		public SessionAuthorization {
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(sessionId, "sessionId");
			Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(pageId, "pageId");
			if (
				definitionGeneration < 0L
					|| stateRevision < 0L
					|| routeRevision < 0L
					|| nextRequestSequence < 0L
			) {
				throw new IllegalArgumentException("terminal session revisions cannot be negative");
			}
		}
	}

	/**
	 * Untrusted packet claims plus a server-derived durable request UUID.
	 *
	 * <p>The wire protocol carries no request UUID. The runtime must create {@code requestId} with
	 * {@link #deriveRequestId} from the server-owned session ID and its accepted request sequence;
	 * it must never accept a persistent ID from the client or allocate a fresh UUID while retrying
	 * the same packet.
	 */
	public record ActionRequest(
		UUID sessionId,
		UUID pageInstanceId,
		long definitionGeneration,
		long stateRevision,
		long routeRevision,
		long requestSequence,
		UUID requestId,
		String nodeId,
		Identifier actionId
	) {
		public ActionRequest {
			Objects.requireNonNull(sessionId, "sessionId");
			Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(requestId, "requestId");
			Objects.requireNonNull(nodeId, "nodeId");
			Objects.requireNonNull(actionId, "actionId");
			if (
				definitionGeneration < 0L
					|| stateRevision < 0L
					|| routeRevision < 0L
					|| requestSequence < 0L
			) {
				throw new IllegalArgumentException("action request revisions cannot be negative");
			}
			if (nodeId.isBlank() || nodeId.length() > 128) {
				throw new IllegalArgumentException("action node ID must be non-blank and bounded");
			}
		}
	}

	/**
	 * Metadata returned before any usage, cooldown, or PREPARED ledger mutation is written.
	 */
	public record ConfirmationChallenge(
		UUID sessionId,
		UUID pageInstanceId,
		String nodeId,
		Identifier actionId,
		UUID requestId,
		long requestSequence,
		long definitionGeneration,
		long routeRevision,
		Confirmation confirmation
	) {
		public ConfirmationChallenge {
			Objects.requireNonNull(sessionId, "sessionId");
			Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(nodeId, "nodeId");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
			Objects.requireNonNull(confirmation, "confirmation");
			if (
				nodeId.isBlank()
					|| requestSequence < 0L
					|| definitionGeneration < 0L
					|| routeRevision < 0L
			) {
				throw new IllegalArgumentException("confirmation challenge context is invalid");
			}
		}
	}

	/**
	 * Server-owned proof that the matching one-time confirmation challenge was consumed.
	 *
	 * <p>The opaque challenge/token itself stays in the runtime token store and never enters world
	 * state. Constructing this record directly from client payload data would violate the API
	 * contract.
	 */
	public record ConfirmedRequest(
		UUID sessionId,
		UUID pageInstanceId,
		String nodeId,
		Identifier actionId,
		UUID requestId,
		long requestSequence,
		long definitionGeneration,
		long routeRevision
	) {
		public ConfirmedRequest {
			Objects.requireNonNull(sessionId, "sessionId");
			Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(nodeId, "nodeId");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
			if (
				nodeId.isBlank()
					|| requestSequence < 0L
					|| definitionGeneration < 0L
					|| routeRevision < 0L
			) {
				throw new IllegalArgumentException("confirmed request context is invalid");
			}
		}

		private boolean matches(
			final SessionAuthorization session,
			final ActionRequest request,
			final Identifier authoritativeActionId,
			final long authoritativeGeneration
		) {
			return this.sessionId.equals(session.sessionId())
				&& this.pageInstanceId.equals(session.pageInstanceId())
				&& this.nodeId.equals(request.nodeId())
				&& this.actionId.equals(authoritativeActionId)
				&& this.actionId.equals(request.actionId())
				&& this.requestId.equals(request.requestId())
				&& this.requestSequence == request.requestSequence()
				&& this.definitionGeneration == authoritativeGeneration
				&& this.definitionGeneration == request.definitionGeneration()
				&& this.routeRevision == session.routeRevision()
				&& this.routeRevision == request.routeRevision();
		}
	}

	public sealed interface PreparedTarget permits PushPage, RunFunction {
	}

	/**
	 * The only legal page-stack mutation for a registered open_page action.
	 */
	public record PushPage(Identifier pageId) implements PreparedTarget {
		public PushPage {
			Objects.requireNonNull(pageId, "pageId");
		}
	}

	public record RunFunction(
		Identifier functionId,
		ExecutionContext executionContext
	) implements PreparedTarget {
		public RunFunction {
			Objects.requireNonNull(functionId, "functionId");
			Objects.requireNonNull(executionContext, "executionContext");
		}
	}

	public record PreparedAction(
		UUID gameInstanceId,
		Identifier gameId,
		UUID playerId,
		String playerName,
		Identifier actionId,
		UUID requestId,
		long requestSequence,
		UUID terminalSessionId,
		Identifier pageId,
		UUID pageInstanceId,
		String nodeId,
		Identifier phaseId,
		Optional<Identifier> taskId,
		Optional<UUID> taskInstanceId,
		long gameElapsedTicks,
		long taskElapsedTicks,
		long definitionGeneration,
		long authorizedStateRevision,
		long worldTick,
		PreparedTarget target,
		PlayerActionFeedback feedback,
		AuditPolicy audit
	) {
		public PreparedAction {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(playerName, "playerName");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
			Objects.requireNonNull(terminalSessionId, "terminalSessionId");
			Objects.requireNonNull(pageId, "pageId");
			Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(nodeId, "nodeId");
			Objects.requireNonNull(phaseId, "phaseId");
			taskId = Objects.requireNonNull(taskId, "taskId");
			taskInstanceId = Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(feedback, "feedback");
			Objects.requireNonNull(audit, "audit");
			if (
				playerName.isBlank()
					|| playerName.length() > 64
					|| requestSequence < 0L
					|| gameElapsedTicks < 0L
					|| taskElapsedTicks < 0L
					|| definitionGeneration < 0L
					|| authorizedStateRevision < 0L
					|| worldTick < 0L
			) {
				throw new IllegalArgumentException("prepared player action context is invalid");
			}
			if (taskId.isPresent() != taskInstanceId.isPresent()) {
				throw new IllegalArgumentException("task ID and instance ID must be paired");
			}
		}

		public RegisteredPlayerFunctionRunner.Context functionContext(
			final long committedStateRevision
		) {
			if (!(this.target instanceof RunFunction runFunction)) {
				throw new IllegalStateException("open_page action has no function context");
			}
			return new RegisteredPlayerFunctionRunner.Context(
				this.gameId,
				this.gameInstanceId,
				this.actionId,
				this.requestId,
				this.requestSequence,
				this.playerId,
				this.playerName,
				this.phaseId,
				this.pageId,
				this.nodeId,
				this.terminalSessionId,
				this.taskId,
				this.taskInstanceId,
				this.gameElapsedTicks,
				this.taskElapsedTicks,
				this.definitionGeneration,
				committedStateRevision,
				this.worldTick,
				runFunction.executionContext()
			);
		}
	}

	public record Replay(
		PlayerActionInvocationStatus status,
		Optional<String> resultCode,
		Optional<String> resultSummary
	) {
		public Replay {
			Objects.requireNonNull(status, "status");
			resultCode = Objects.requireNonNull(resultCode, "resultCode");
			resultSummary = Objects.requireNonNull(resultSummary, "resultSummary");
		}
	}

	/**
	 * Side-effect-free result for retaining one registered navigation frame.
	 */
	public record NavigationValidation(
		boolean allowed,
		Optional<Code> denialCode,
		String message
	) {
		public NavigationValidation {
			denialCode = Objects.requireNonNull(denialCode, "denialCode");
			message = bounded(Objects.requireNonNull(message, "message"), MAX_MESSAGE_LENGTH);
			if (
				allowed != denialCode.isEmpty()
					|| allowed != message.isEmpty()
			) {
				throw new IllegalArgumentException(
					"navigation validation result is inconsistent"
				);
			}
		}

		private static NavigationValidation permitted() {
			return new NavigationValidation(true, Optional.empty(), "");
		}

		private static NavigationValidation denied(
			final Code code,
			final String message
		) {
			return new NavigationValidation(
				false,
				Optional.of(Objects.requireNonNull(code, "code")),
				message
			);
		}
	}

	public record Preparation(
		boolean accepted,
		Code code,
		String message,
		Optional<WorldStateV4> nextState,
		Optional<PreparedAction> prepared,
		Optional<Replay> replay,
		Optional<PlayerActionFeedback> feedback,
		Optional<ConfirmationChallenge> confirmation
	) {
		public Preparation {
			Objects.requireNonNull(code, "code");
			message = bounded(Objects.requireNonNull(message, "message"), MAX_MESSAGE_LENGTH);
			nextState = Objects.requireNonNull(nextState, "nextState");
			prepared = Objects.requireNonNull(prepared, "prepared");
			replay = Objects.requireNonNull(replay, "replay");
			feedback = Objects.requireNonNull(feedback, "feedback");
			confirmation = Objects.requireNonNull(confirmation, "confirmation");
			if (
				accepted != (code == Code.PREPARED)
					|| accepted != (nextState.isPresent() && prepared.isPresent())
					|| (accepted && replay.isPresent())
					|| (confirmation.isPresent() != (code == Code.CONFIRMATION_REQUIRED))
			) {
				throw new IllegalArgumentException("player action preparation payload is inconsistent");
			}
		}

		private static Preparation prepared(
			final WorldStateV4 nextState,
			final PreparedAction prepared
		) {
			return new Preparation(
				true,
				Code.PREPARED,
				"",
				Optional.of(nextState),
				Optional.of(prepared),
				Optional.empty(),
				Optional.of(prepared.feedback()),
				Optional.empty()
			);
		}

		private static Preparation denied(final Code code, final String message) {
			return denied(code, message, Optional.empty());
		}

		private static Preparation denied(
			final Code code,
			final String message,
			final Optional<PlayerActionFeedback> feedback
		) {
			return new Preparation(
				false,
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				feedback,
				Optional.empty()
			);
		}

		private static Preparation confirmationRequired(
			final ConfirmationChallenge challenge,
			final Optional<PlayerActionFeedback> feedback
		) {
			return new Preparation(
				false,
				Code.CONFIRMATION_REQUIRED,
				"该操作需要二次确认",
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				feedback,
				Optional.of(challenge)
			);
		}

		private static Preparation outcomeUnknown(
			final PlayerActionInvocation invocation,
			final Optional<PlayerActionFeedback> feedback
		) {
			return new Preparation(
				false,
				Code.OUTCOME_UNKNOWN,
				"该请求已在执行前持久化，但服务器无法证明其外部结果；为防止重复执行，必须停止",
				Optional.empty(),
				Optional.empty(),
				Optional.of(
					new Replay(
						invocation.status(),
						invocation.resultCode(),
						invocation.resultSummary()
					)
				),
				feedback,
				Optional.empty()
			);
		}

		private static Preparation replayed(
			final PlayerActionInvocation invocation,
			final Optional<PlayerActionFeedback> feedback
		) {
			return new Preparation(
				false,
				Code.REQUEST_REPLAYED,
				"该玩家操作请求已经处理",
				Optional.empty(),
				Optional.empty(),
				Optional.of(
					new Replay(
						invocation.status(),
						invocation.resultCode(),
						invocation.resultSummary()
					)
				),
				feedback,
				Optional.empty()
			);
		}
	}

	public record ExecutionResult(boolean success, String code, String summary) {
		public ExecutionResult {
			code = Objects.requireNonNull(code, "code");
			summary = bounded(Objects.requireNonNull(summary, "summary"), WorldStateV4.MAX_ACTION_RESULT_SUMMARY_LENGTH);
			if (
				code.isBlank()
					|| code.length() > WorldStateV4.MAX_ACTION_RESULT_CODE_LENGTH
			) {
				throw new IllegalArgumentException("execution result code must be non-blank and bounded");
			}
		}

		public static ExecutionResult succeeded(final String code, final String summary) {
			return new ExecutionResult(true, code, summary);
		}

		public static ExecutionResult failed(final String code, final String summary) {
			return new ExecutionResult(false, code, summary);
		}

		public static ExecutionResult pageOpened(final Identifier pageId) {
			return succeeded("page_opened", "pushed " + pageId);
		}
	}

	public record AuditEntry(
		UUID gameInstanceId,
		UUID playerId,
		Identifier actionId,
		UUID requestId,
		boolean success,
		String summary
	) {
		public AuditEntry {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
			summary = bounded(Objects.requireNonNull(summary, "summary"), MAX_AUDIT_SUMMARY_LENGTH);
			if (summary.isBlank()) {
				throw new IllegalArgumentException("audit summary cannot be blank");
			}
		}
	}

	public record Finalization(
		boolean finalized,
		boolean alreadyFinalized,
		Code code,
		String message,
		Optional<WorldStateV4> nextState,
		Optional<PlayerActionInvocation> invocation,
		Optional<AuditEntry> audit
	) {
		public Finalization {
			Objects.requireNonNull(code, "code");
			message = bounded(Objects.requireNonNull(message, "message"), MAX_MESSAGE_LENGTH);
			nextState = Objects.requireNonNull(nextState, "nextState");
			invocation = Objects.requireNonNull(invocation, "invocation");
			audit = Objects.requireNonNull(audit, "audit");
			if (
				(finalized || alreadyFinalized) != invocation.isPresent()
					|| (finalized && alreadyFinalized)
					|| (finalized != nextState.isPresent())
			) {
				throw new IllegalArgumentException("player action finalization payload is inconsistent");
			}
		}

		private static Finalization finalized(
			final WorldStateV4 nextState,
			final PlayerActionInvocation invocation,
			final Optional<AuditEntry> audit
		) {
			return new Finalization(
				true,
				false,
				Code.FINALIZED,
				"",
				Optional.of(nextState),
				Optional.of(invocation),
				audit
			);
		}

		private static Finalization alreadyFinalized(
			final WorldStateV4 state,
			final PlayerActionInvocation invocation,
			final Optional<AuditEntry> audit
		) {
			return new Finalization(
				false,
				true,
				Code.ALREADY_FINALIZED,
				"",
				Optional.empty(),
				Optional.of(invocation),
				audit
			);
		}

		private static Finalization rejected(final Code code, final String message) {
			return new Finalization(
				false,
				false,
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public enum Code {
		PREPARED,
		FINALIZED,
		ALREADY_FINALIZED,
		SCHEMA_BLOCKED,
		SESSION_STALE,
		DEFINITION_GENERATION_STALE,
		STATE_REVISION_STALE,
		ROUTE_REVISION_STALE,
		REQUEST_REPLAYED,
		OUTCOME_UNKNOWN,
		GAME_MISMATCH,
		PLAYER_NOT_REGISTERED,
		PLAYER_OFFLINE,
		HOST_FORBIDDEN,
		PAGE_UNAVAILABLE,
		NODE_MISMATCH,
		ACTION_UNAVAILABLE,
		AUDIENCE_MISMATCH,
		PHASE_MISMATCH,
		TASK_MISMATCH,
		TASK_STATE_MISMATCH,
		PREDICATE_DENIED,
		PREDICATE_FAILED,
		CONFIRMATION_REQUIRED,
		CONFIRMATION_INVALID,
		USAGE_EXHAUSTED,
		COOLDOWN_ACTIVE,
		CLOCK_INVALID,
		LEDGER_FULL,
		INVOCATION_MISSING,
		INVOCATION_FINAL,
		INTERNAL_ERROR
	}

	private record TaskContext(
		Optional<Identifier> taskId,
		Optional<PlayerTaskState> state
	) {
		private TaskContext {
			taskId = Objects.requireNonNull(taskId, "taskId");
			state = Objects.requireNonNull(state, "state");
		}

		private static TaskContext empty() {
			return new TaskContext(Optional.empty(), Optional.empty());
		}
	}

	private record ReboundButton(boolean matched, String message) {
		private ReboundButton {
			Objects.requireNonNull(message, "message");
		}

		private static ReboundButton success() {
			return new ReboundButton(true, "");
		}

		private static ReboundButton failed(final String message) {
			return new ReboundButton(false, message);
		}
	}
}
