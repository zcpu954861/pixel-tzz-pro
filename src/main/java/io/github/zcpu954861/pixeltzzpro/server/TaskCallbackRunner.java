package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the isolated macro context for one frozen task callback.
 *
 * <p>Execution is delegated to {@link FlowCallbackRunner}; task callbacks do not maintain a second
 * command-function executor.
 */
public final class TaskCallbackRunner {
	private static final Pattern FIELD_BINDING = Pattern.compile("[a-z][a-z0-9_]{0,31}");
	private static final int MAX_PLAYER_NAME_LENGTH = 64;
	private static final int MAX_FIELD_BINDINGS = 32;

	private TaskCallbackRunner() {
	}

	public static FlowCallbackRunner.Invocation invoke(
		final MinecraftServer server,
		final Identifier functionId,
		final Context context,
		final Optional<ServerPlayer> player
	) {
		Objects.requireNonNull(context, "context");
		return FlowCallbackRunner.invokePrepared(
			server,
			functionId,
			context.toMacroArguments(),
			"task instance " + context.taskInstanceId(),
			context.playerId(),
			player
		);
	}

	public record Context(
		Identifier gameId,
		UUID timelineInstanceId,
		Identifier taskId,
		UUID taskInstanceId,
		Optional<String> resultId,
		long gameElapsedTicks,
		long taskElapsedTicks,
		Optional<UUID> playerId,
		Optional<String> playerName,
		Map<String, StoredValue> playerFields
	) {
		public Context {
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(timelineInstanceId, "timelineInstanceId");
			Objects.requireNonNull(taskId, "taskId");
			Objects.requireNonNull(taskInstanceId, "taskInstanceId");
			resultId = Objects.requireNonNull(resultId, "resultId");
			playerId = Objects.requireNonNull(playerId, "playerId");
			playerName = Objects.requireNonNull(playerName, "playerName");
			playerFields = Map.copyOf(Objects.requireNonNull(playerFields, "playerFields"));
			if (gameElapsedTicks < 0L || taskElapsedTicks < 0L) {
				throw new IllegalArgumentException("task callback ticks cannot be negative");
			}
			if (playerId.isPresent() != playerName.isPresent()) {
				throw new IllegalArgumentException(
					"playerId and playerName must both be present or absent"
				);
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
			resultId.ifPresent(value -> {
				if (value.isBlank() || value.length() > 1_024) {
					throw new IllegalArgumentException("resultId must be non-blank and bounded");
				}
			});
			if (playerFields.size() > MAX_FIELD_BINDINGS) {
				throw new IllegalArgumentException(
					"player field binding count exceeds " + MAX_FIELD_BINDINGS
				);
			}
			if (playerId.isEmpty() && !playerFields.isEmpty()) {
				throw new IllegalArgumentException(
					"global task callbacks cannot expose player fields"
				);
			}
			for (Map.Entry<String, StoredValue> entry : playerFields.entrySet()) {
				if (!FIELD_BINDING.matcher(entry.getKey()).matches()) {
					throw new IllegalArgumentException(
						"invalid player field macro name " + entry.getKey()
					);
				}
				Objects.requireNonNull(entry.getValue(), "player field value");
			}
		}

		CompoundTag toMacroArguments() {
			CompoundTag arguments = new CompoundTag();
			arguments.putString("game_id", this.gameId.toString());
			arguments.putString("timeline_instance_id", this.timelineInstanceId.toString());
			arguments.putString("task_id", this.taskId.toString());
			arguments.putString("task_instance_id", this.taskInstanceId.toString());
			this.resultId.ifPresent(value -> arguments.putString("result_id", value));
			arguments.putLong("game_elapsed_ticks", this.gameElapsedTicks);
			arguments.putLong("task_elapsed_ticks", this.taskElapsedTicks);
			if (this.playerId.isPresent()) {
				arguments.putString("player_uuid", this.playerId.orElseThrow().toString());
				arguments.putString("player_name", this.playerName.orElseThrow());
			}
			if (!this.playerFields.isEmpty()) {
				CompoundTag fields = new CompoundTag();
				new TreeMap<>(this.playerFields).forEach(
					(name, value) -> putStoredValue(fields, name, value)
				);
				arguments.put("player_field", fields);
			}
			return arguments;
		}
	}

	private static void putStoredValue(
		final CompoundTag target,
		final String name,
		final StoredValue value
	) {
		switch (value.type()) {
			case BOOLEAN -> target.putBoolean(name, value.booleanValue().orElseThrow());
			case INTEGER -> target.putInt(name, value.integerValue().orElseThrow());
			case STRING, SINGLE_CHOICE ->
				target.putString(name, value.stringValue().orElseThrow());
			case IDENTIFIER ->
				target.putString(name, value.identifierValue().orElseThrow().toString());
			case MULTI_CHOICE -> {
				ListTag values = new ListTag();
				value.choices().forEach(choice -> values.add(StringTag.valueOf(choice)));
				target.put(name, values);
			}
		}
	}
}
