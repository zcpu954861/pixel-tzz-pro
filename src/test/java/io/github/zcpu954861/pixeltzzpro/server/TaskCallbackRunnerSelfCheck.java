package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackRunner.Context;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Runnable check for the task callback macro and bound-player-field contract.
 */
public final class TaskCallbackRunnerSelfCheck {
	private static final UUID TIMELINE =
		UUID.fromString("00000000-0000-0000-0000-000000000501");
	private static final UUID TASK =
		UUID.fromString("00000000-0000-0000-0000-000000000502");
	private static final UUID PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000503");

	private TaskCallbackRunnerSelfCheck() {
	}

	public static void main(final String[] args) {
		WorldStateV2.initial();
		Context player = new Context(
			Identifier.parse("pixel_tzz:main"),
			TIMELINE,
			Identifier.parse("pixel_tzz:task/first"),
			TASK,
			Optional.of("success"),
			1_234L,
			456L,
			Optional.of(PLAYER),
			Optional.of("Hunter"),
			Map.of(
				"spawn_point",
				StoredValue.singleChoice("north_gate"),
				"batch",
				StoredValue.ofInteger(2),
				"flags",
				StoredValue.multiChoice(List.of("alpha", "beta"))
			)
		);
		CompoundTag arguments = player.toMacroArguments();
		check(
			arguments.getString("game_id").orElseThrow().equals("pixel_tzz:main"),
			"game id macro failed"
		);
		check(
			arguments.getString("timeline_instance_id").orElseThrow().equals(TIMELINE.toString()),
			"timeline instance macro failed"
		);
		check(
			arguments.getString("task_id").orElseThrow().equals("pixel_tzz:task/first"),
			"task id macro failed"
		);
		check(
			arguments.getString("task_instance_id").orElseThrow().equals(TASK.toString()),
			"task instance macro failed"
		);
		check(
			arguments.getString("result_id").orElseThrow().equals("success"),
			"result id macro failed"
		);
		check(arguments.getLong("game_elapsed_ticks").orElseThrow() == 1_234L, "game tick macro failed");
		check(arguments.getLong("task_elapsed_ticks").orElseThrow() == 456L, "task tick macro failed");
		CompoundTag fields = arguments.getCompound("player_field").orElseThrow();
		check(
			fields.getString("spawn_point").orElseThrow().equals("north_gate"),
			"choice field macro failed"
		);
		check(fields.getInt("batch").orElseThrow() == 2, "integer field macro failed");
		check(fields.getList("flags").orElseThrow().size() == 2, "multi-choice field macro failed");

		Context global = new Context(
			Identifier.parse("pixel_tzz:main"),
			TIMELINE,
			Identifier.parse("pixel_tzz:task/first"),
			TASK,
			Optional.empty(),
			0L,
			0L,
			Optional.empty(),
			Optional.empty(),
			Map.of()
		);
		check(!global.toMacroArguments().contains("result_id"), "absent result must stay absent");
		check(!global.toMacroArguments().contains("player_uuid"), "global callback must not fake a player");

		expectRejected(
			() -> new Context(
				Identifier.parse("pixel_tzz:main"),
				TIMELINE,
				Identifier.parse("pixel_tzz:task/first"),
				TASK,
				Optional.empty(),
				0L,
				0L,
				Optional.empty(),
				Optional.empty(),
				Map.of("spawn_point", StoredValue.singleChoice("north_gate"))
			),
			"global callbacks must reject player fields"
		);
		expectRejected(
			() -> new Context(
				Identifier.parse("pixel_tzz:main"),
				TIMELINE,
				Identifier.parse("pixel_tzz:task/first"),
				TASK,
				Optional.empty(),
				0L,
				0L,
				Optional.of(PLAYER),
				Optional.of("Hunter"),
				Map.of("bad-name", StoredValue.singleChoice("north_gate"))
			),
			"invalid field macro names must be rejected"
		);
		System.out.println("TASK_CALLBACK_RUNNER_SELF_CHECK=PASS");
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
