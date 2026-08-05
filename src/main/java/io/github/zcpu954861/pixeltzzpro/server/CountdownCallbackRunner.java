package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.CallbackWork;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenParticipant;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Executes one already-prepared countdown callback with the frozen, bounded macro surface. */
public final class CountdownCallbackRunner {
	private CountdownCallbackRunner() {
	}

	public static Result invoke(
		final MinecraftServer server,
		final Instance countdown,
		final CallbackWork work,
		final String reason
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(work, "work");
		String boundedReason = Objects.requireNonNull(reason, "reason").strip();
		if (boundedReason.length() > 1_024) {
			boundedReason = boundedReason.substring(0, 1_024);
		}
		CompoundTag arguments = new CompoundTag();
		arguments.putString("game_instance_id", countdown.gameInstanceId().toString());
		arguments.putString("countdown_id", countdown.definition().definitionId().toString());
		arguments.putString("countdown_instance_id", countdown.countdownInstanceId().toString());
		arguments.putString("callback_slot", work.key().slot().getSerializedName());
		arguments.putString("callback_id", work.key().callbackId());
		arguments.putString("reason", boundedReason);
		arguments.putLong("total_ticks", countdown.totalTicks());
		arguments.putLong("remaining_ticks", countdown.lifecycle().remainingTicks());
		work.key().participantId().ifPresent(participantId -> {
			arguments.putString("participant_uuid", participantId.toString());
			String name = countdown.participants()
				.stream()
				.filter(value -> value.playerId().equals(participantId))
				.map(FrozenParticipant::frozenName)
				.findFirst()
				.orElse(participantId.toString());
			arguments.putString("participant_name", name);
		});

		FlowCallbackRunner.Invocation invocation = FlowCallbackRunner.invokePrepared(
			server,
			work.functionId(),
			arguments,
			"countdown " + countdown.countdownInstanceId() + " callback " + work.key().callbackId(),
			Optional.empty(),
			Optional.empty()
		);
		return invocation.success()
			? new Result(true, "")
			: new Result(false, boundedError(invocation.error()));
	}

	private static String boundedError(final String value) {
		String error = value == null || value.isBlank() ? "倒计时回调执行失败" : value.strip();
		return error.length() <= 1_024 ? error : error.substring(0, 1_024);
	}

	public record Result(boolean success, String error) {
		public Result {
			error = Objects.requireNonNull(error, "error");
			if (success != error.isEmpty()) {
				throw new IllegalArgumentException("countdown callback result is inconsistent");
			}
		}
	}
}
