package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.ExecutionContext;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Executes one data-pack registered player action through the shared callback engine.
 *
 * <p>This class deliberately owns no mod state and receives no state mutator. A function may use
 * normal Minecraft commands to affect the world, but the PREPARED/SUCCEEDED/FAILED player-action
 * ledger remains exclusively controlled by {@link PlayerActionAuthority} and the server runtime.
 * Both PLAYER and SERVER execution reuse {@link FlowCallbackRunner#invokePrepared}; there is no
 * second command-function executor.
 */
public final class RegisteredPlayerFunctionRunner {
	private static final int MAX_PLAYER_NAME_LENGTH = 64;
	private static final int MAX_NODE_ID_LENGTH = 128;

	private RegisteredPlayerFunctionRunner() {
	}

	public static Result invoke(
		final MinecraftServer server,
		final Identifier functionId,
		final Context context,
		final ServerPlayer clickingPlayer
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(functionId, "functionId");
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(clickingPlayer, "clickingPlayer");
		if (!clickingPlayer.getUUID().equals(context.playerId())) {
			return Result.failed("function_actor_mismatch", "点击玩家与已授权动作不一致");
		}

		Optional<UUID> sourcePlayerId;
		Optional<ServerPlayer> sourcePlayer;
		if (context.executionContext() == ExecutionContext.PLAYER) {
			sourcePlayerId = Optional.of(context.playerId());
			sourcePlayer = Optional.of(clickingPlayer);
		} else {
			sourcePlayerId = Optional.empty();
			sourcePlayer = Optional.empty();
		}
		FlowCallbackRunner.Invocation invocation = FlowCallbackRunner.invokePrepared(
			server,
			functionId,
			context.toMacroArguments(),
			"player action "
				+ context.actionId()
				+ " request "
				+ context.requestId(),
			sourcePlayerId,
			sourcePlayer
		);
		if (!invocation.success()) {
			return Result.failed("function_failed", invocation.error());
		}
		String summary = invocation.resultReported()
			? "命令函数已完成，返回值 " + invocation.resultValue()
			: "命令函数已完成";
		return Result.succeeded(
			"function_succeeded",
			summary,
			invocation.resultReported(),
			invocation.resultValue()
		);
	}

	/**
	 * Fixed, bounded macro surface exposed to registered player functions.
	 *
	 * <p>No arbitrary client value, raw binding document, personal-field map, or mod-state handle is
	 * included. The clicking player's identity remains available as macro data even when
	 * {@code executionContext == SERVER}; only the command source changes.
	 */
	public record Context(
		Identifier gameId,
		UUID gameInstanceId,
		Identifier actionId,
		UUID requestId,
		long requestSequence,
		UUID playerId,
		String playerName,
		Identifier phaseId,
		Identifier pageId,
		String nodeId,
		UUID terminalSessionId,
		Optional<Identifier> taskId,
		Optional<UUID> taskInstanceId,
		long gameElapsedTicks,
		long taskElapsedTicks,
		long definitionGeneration,
		long stateRevision,
		long worldTick,
		ExecutionContext executionContext
	) {
		public Context {
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(playerName, "playerName");
			Objects.requireNonNull(phaseId, "phaseId");
			Objects.requireNonNull(pageId, "pageId");
			Objects.requireNonNull(nodeId, "nodeId");
			Objects.requireNonNull(terminalSessionId, "terminalSessionId");
			taskId = Objects.requireNonNull(taskId, "taskId");
			taskInstanceId = Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			Objects.requireNonNull(executionContext, "executionContext");
			if (
				playerName.isBlank()
					|| playerName.length() > MAX_PLAYER_NAME_LENGTH
					|| nodeId.isBlank()
					|| nodeId.length() > MAX_NODE_ID_LENGTH
					|| requestSequence < 0L
					|| gameElapsedTicks < 0L
					|| taskElapsedTicks < 0L
					|| definitionGeneration < 0L
					|| stateRevision < 0L
					|| worldTick < 0L
			) {
				throw new IllegalArgumentException("registered player function context is invalid");
			}
			if (taskId.isPresent() != taskInstanceId.isPresent()) {
				throw new IllegalArgumentException("task ID and instance ID must be paired");
			}
		}

		CompoundTag toMacroArguments() {
			CompoundTag arguments = new CompoundTag();
			arguments.putString("game_id", this.gameId.toString());
			arguments.putString("game_instance_id", this.gameInstanceId.toString());
			arguments.putString("action_id", this.actionId.toString());
			arguments.putString("request_id", this.requestId.toString());
			arguments.putLong("request_sequence", this.requestSequence);
			arguments.putString("player_uuid", this.playerId.toString());
			arguments.putString("player_name", this.playerName);
			arguments.putString("phase_id", this.phaseId.toString());
			arguments.putString("page_id", this.pageId.toString());
			arguments.putString("node_id", this.nodeId);
			arguments.putString("terminal_session_id", this.terminalSessionId.toString());
			this.taskId.ifPresent(value -> arguments.putString("task_id", value.toString()));
			this.taskInstanceId.ifPresent(
				value -> arguments.putString("task_instance_id", value.toString())
			);
			arguments.putLong("game_elapsed_ticks", this.gameElapsedTicks);
			arguments.putLong("task_elapsed_ticks", this.taskElapsedTicks);
			arguments.putLong("definition_generation", this.definitionGeneration);
			arguments.putLong("state_revision", this.stateRevision);
			arguments.putLong("world_tick", this.worldTick);
			arguments.putString(
				"execution_context",
				this.executionContext.name().toLowerCase(java.util.Locale.ROOT)
			);
			return arguments;
		}
	}

	public record Result(
		boolean success,
		String code,
		String summary,
		boolean resultReported,
		int resultValue
	) {
		public Result {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(summary, "summary");
			if (
				code.isBlank()
					|| code.length() > WorldStateV4.MAX_ACTION_RESULT_CODE_LENGTH
					|| summary.length() > WorldStateV4.MAX_ACTION_RESULT_SUMMARY_LENGTH
			) {
				throw new IllegalArgumentException(
					"registered function result code and summary must be bounded"
				);
			}
			if (!success && (resultReported || resultValue != 0)) {
				throw new IllegalArgumentException(
					"failed registered functions cannot expose a successful command result"
				);
			}
		}

		public PlayerActionAuthority.ExecutionResult authorityResult() {
			return new PlayerActionAuthority.ExecutionResult(
				this.success,
				this.code,
				this.summary
			);
		}

		private static Result succeeded(
			final String code,
			final String summary,
			final boolean resultReported,
			final int resultValue
		) {
			return new Result(true, code, summary, resultReported, resultValue);
		}

		private static Result failed(final String code, final String summary) {
			String bounded = Objects.requireNonNull(summary, "summary");
			if (bounded.isBlank()) {
				bounded = "未知函数执行失败";
			}
			if (bounded.length() > PlayerActionAuthority.MAX_MESSAGE_LENGTH) {
				bounded = bounded.substring(
					0,
					PlayerActionAuthority.MAX_MESSAGE_LENGTH - 3
				) + "...";
			}
			return new Result(false, code, bounded, false, 0);
		}
	}
}
