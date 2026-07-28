package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * Executes one frozen data-pack callback with isolated macro arguments.
 *
 * <p>The caller commits core state and callback markers before invoking this class. A failed
 * callback therefore reports a diagnostic but never rolls authoritative flow state back.
 */
public final class FlowCallbackRunner {
	private static final int MAX_PLAYER_NAME_LENGTH = 64;
	private static final int MAX_ERROR_LENGTH = 4_096;

	private FlowCallbackRunner() {
	}

	public static Invocation invoke(
		final MinecraftServer server,
		final Identifier functionId,
		final Context context,
		final Optional<ServerPlayer> player
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(functionId, "functionId");
		Objects.requireNonNull(context, "context");
		Optional<ServerPlayer> sourcePlayer = Objects.requireNonNull(player, "player");
		if (!server.isSameThread()) {
			return Invocation.failed("callback must execute on the server thread");
		}
		if (sourcePlayer.isPresent() != context.playerId().isPresent()) {
			return Invocation.failed("player callback context does not match its command source");
		}
		if (
			sourcePlayer.isPresent()
				&& !sourcePlayer.orElseThrow().getUUID().equals(context.playerId().orElseThrow())
		) {
			return Invocation.failed("player callback source UUID does not match its frozen context");
		}

		CommandFunction<CommandSourceStack> function = server.getFunctions()
			.get(functionId)
			.orElse(null);
		if (function == null) {
			return Invocation.failed("callback function is unavailable: " + functionId);
		}

		final InstantiatedFunction<CommandSourceStack> instantiated;
		try {
			instantiated = function.instantiate(
				context.toMacroArguments(),
				server.getFunctions().getDispatcher()
			);
		} catch (FunctionInstantiationException | RuntimeException error) {
			return Invocation.failed(
				"callback instantiation failed for " + functionId + ": " + safeMessage(error)
			);
		}

		AtomicBoolean callbackReceived = new AtomicBoolean();
		AtomicBoolean commandSucceeded = new AtomicBoolean(true);
		AtomicInteger resultValue = new AtomicInteger();
		CommandResultCallback resultCallback = (success, value) -> {
			callbackReceived.set(true);
			commandSucceeded.compareAndSet(true, success);
			resultValue.set(value);
		};
		CommandSourceStack source = sourcePlayer
			.map(ServerPlayer::createCommandSourceStack)
			.orElseGet(() -> server.createCommandSourceStack())
			.withMaximumPermission(LevelBasedPermissionSet.GAMEMASTER)
			.withSuppressedOutput()
			.withCallback(resultCallback);
		try {
			Commands.executeCommandInContext(
				source,
				execution -> ExecutionContext.queueInitialFunctionCall(
					execution,
					instantiated,
					source,
					resultCallback
				)
			);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ callback {} failed for flow instance {}",
				functionId,
				context.instanceId(),
				error
			);
			return Invocation.failed(
				"callback execution failed for " + functionId + ": " + safeMessage(error)
			);
		}
		if (callbackReceived.get() && !commandSucceeded.get()) {
			return Invocation.failed(
				"callback reported failure for " + functionId + " (result " + resultValue.get() + ")"
			);
		}
		return Invocation.succeeded(callbackReceived.get(), resultValue.get());
	}

	public record Context(
		Identifier gameId,
		Identifier flowId,
		int flowVersion,
		UUID instanceId,
		Optional<UUID> playerId,
		Optional<String> playerName,
		int completed,
		int total,
		long stateRevision
	) {
		public Context {
			gameId = Objects.requireNonNull(gameId, "gameId");
			flowId = Objects.requireNonNull(flowId, "flowId");
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			playerId = Objects.requireNonNull(playerId, "playerId");
			playerName = Objects.requireNonNull(playerName, "playerName");
			if (playerId.isPresent() != playerName.isPresent()) {
				throw new IllegalArgumentException(
					"playerId and playerName must both be present or absent"
				);
			}
			if (
				flowVersion <= 0
					|| completed < 0
					|| total < 0
					|| completed > total
					|| stateRevision < 0L
			) {
				throw new IllegalArgumentException("callback counters are invalid");
			}
			playerName.ifPresent(name -> {
				if (name.isBlank() || name.length() > MAX_PLAYER_NAME_LENGTH) {
					throw new IllegalArgumentException(
						"playerName must be non-blank and at most "
							+ MAX_PLAYER_NAME_LENGTH
							+ " characters"
					);
				}
			});
		}

		CompoundTag toMacroArguments() {
			CompoundTag arguments = new CompoundTag();
			arguments.putString("game_id", this.gameId.toString());
			arguments.putString("flow_id", this.flowId.toString());
			arguments.putInt("flow_version", this.flowVersion);
			arguments.putString("instance_id", this.instanceId.toString());
			arguments.putString(
				"player_uuid",
				this.playerId.map(UUID::toString).orElse("")
			);
			arguments.putString("player_name", this.playerName.orElse(""));
			arguments.putInt("completed", this.completed);
			arguments.putInt("total", this.total);
			arguments.putLong("state_revision", this.stateRevision);
			return arguments;
		}
	}

	public record Invocation(
		boolean success,
		boolean resultReported,
		int resultValue,
		String error
	) {
		public Invocation {
			error = Objects.requireNonNull(error, "error");
			if (success == !error.isEmpty()) {
				throw new IllegalArgumentException(
					"successful callbacks cannot carry an error and failures require one"
				);
			}
			if (!success && (resultReported || resultValue != 0)) {
				throw new IllegalArgumentException(
					"failed callbacks cannot expose a successful command result"
				);
			}
		}

		private static Invocation succeeded(
			final boolean resultReported,
			final int resultValue
		) {
			return new Invocation(true, resultReported, resultValue, "");
		}

		private static Invocation failed(final String error) {
			String bounded = Objects.requireNonNull(error, "error");
			if (bounded.isBlank()) {
				bounded = "unknown callback failure";
			}
			if (bounded.length() > MAX_ERROR_LENGTH) {
				bounded = bounded.substring(0, MAX_ERROR_LENGTH - 3) + "...";
			}
			return new Invocation(false, false, 0, bounded);
		}
	}

	private static String safeMessage(final Throwable error) {
		String message = error.getMessage();
		return message == null || message.isBlank()
			? error.getClass().getSimpleName()
			: message;
	}
}
