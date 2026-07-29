package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TransitionPhaseOperation;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure server-authoritative transaction for data-pack registered phase changes.
 *
 * <p>The panel action identifies the requested edge, while the active phase graph remains the
 * authority. This prevents an old confirmation, a forged target phase, or a panel action from a
 * different game from advancing the world.
 */
public final class PhaseTransitionAuthority {
	private PhaseTransitionAuthority() {
	}

	public static Result transition(
		final WorldStateV3 root,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition action,
		final UUID actorId,
		final long expectedStateRevision,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(root, "root");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(actorId, "actorId");
		WorldStateV2 state = root.core();
		if (state.validated().result().isEmpty() || root.validated().result().isEmpty()) {
			return Result.failed(OperationCode.SCHEMA_BLOCKED, "世界状态当前不可写。");
		}
		if (state.stateRevision() != expectedStateRevision) {
			return Result.failed(OperationCode.STATE_REVISION_STALE, "游戏阶段已经变化，请刷新后重试。");
		}
		if (occurredAtEpochMillis < 0L || state.audit().totalEvents() == Long.MAX_VALUE) {
			return Result.failed(OperationCode.INTERNAL_ERROR, "无法记录本次阶段迁移。");
		}
		if (state.host().map(WorldStateV2.HostRecord::playerId).filter(actorId::equals).isEmpty()) {
			return Result.failed(OperationCode.NOT_HOST, "只有当前主持人可以推进游戏阶段。");
		}
		if (action.target().mode() != SelectionMode.NONE) {
			return Result.failed(OperationCode.TARGET_COUNT_INVALID, "阶段迁移不接受目标玩家。");
		}
		TransitionPhaseOperation operation = action.operation() instanceof TransitionPhaseOperation value
			? value
			: null;
		if (operation == null) {
			return Result.failed(OperationCode.UNSUPPORTED_OPERATION, "该面板操作不是阶段迁移。");
		}
		Identifier gameId = state.activeGameId().orElse(null);
		Identifier sourceId = state.activePhaseId().orElse(null);
		if (
			gameId == null
				|| sourceId == null
				|| !action.game().equals(gameId)
				|| (!action.phases().isEmpty() && !action.phases().contains(sourceId))
		) {
			return Result.failed(OperationCode.PHASE_MISMATCH, "该操作不属于当前游戏阶段。");
		}
		PhaseDefinition source = definitions.phases().get(sourceId);
		PhaseDefinition target = definitions.phases().get(operation.phase());
		if (
			source == null
				|| target == null
				|| !source.game().equals(gameId)
				|| !target.game().equals(gameId)
				|| sourceId.equals(target.id())
				|| !source.transitions().contains(target.id())
		) {
			return Result.failed(OperationCode.PHASE_MISMATCH, "数据包没有注册当前阶段到目标阶段的迁移。");
		}
		boolean activeFlow = state.activeForcedFlow()
			.map(flow -> flow.runtime().status())
			.filter(status -> status == FlowInstanceStatus.ACTIVE || status == FlowInstanceStatus.BLOCKED)
			.isPresent();
		if (activeFlow) {
			return Result.failed(OperationCode.ACTIVE_FLOW_EXISTS, "必须先结束当前强制流程。");
		}
		if (root.timeline().isPresent()) {
			return Result.failed(OperationCode.ACTION_UNAVAILABLE, "任务时间线已经建立，阶段应由时间线推进。");
		}
		try {
			AuditEvent event = new AuditEvent(
				state.audit().totalEvents(),
				AuditEventType.PHASE_TRANSITIONED,
				occurredAtEpochMillis,
				Optional.of(actorId),
				Optional.empty(),
				Optional.empty(),
				"Phase transitioned from " + sourceId + " to " + target.id()
			);
			WorldStateV2 nextCore = state
				.withActivity(gameId, target.id())
				.withAudit(state.audit().append(event))
				.withStateRevision(Math.incrementExact(state.stateRevision()));
			WorldStateV3 next = root.withCore(nextCore);
			if (next.validated().result().isEmpty()) {
				return Result.failed(OperationCode.INTERNAL_ERROR, "阶段迁移后的状态未通过校验。");
			}
			return Result.succeeded(next, target.name().plainText());
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Result.failed(OperationCode.INTERNAL_ERROR, "无法提交本次阶段迁移。");
		}
	}

	public record Result(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState
	) {
		public Result {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if ((code == OperationCode.SUCCESS) != nextState.isPresent()) {
				throw new IllegalArgumentException("only successful phase transitions carry next state");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static Result succeeded(final WorldStateV3 nextState, final String phaseName) {
			return new Result(
				OperationCode.SUCCESS,
				"已进入“" + phaseName + "”阶段。",
				Optional.of(nextState)
			);
		}

		private static Result failed(final OperationCode code, final String message) {
			return new Result(code, message, Optional.empty());
		}
	}
}
