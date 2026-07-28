package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackRunner.Context;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Runnable check for the isolated callback macro contract.
 */
public final class FlowCallbackRunnerSelfCheck {
	private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000401");
	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000402");

	private FlowCallbackRunnerSelfCheck() {
	}

	public static void main(final String[] args) {
		Context player = new Context(
			Identifier.parse("pixel_tzz:main"),
			Identifier.parse("pixel_tzz:acceptance_flow"),
			2,
			INSTANCE,
			Optional.of(PLAYER),
			Optional.of("Runner"),
			3,
			8,
			17L
		);
		CompoundTag arguments = player.toMacroArguments();
		check(arguments.getString("game_id").orElseThrow().equals("pixel_tzz:main"), "game_id macro failed");
		check(
			arguments.getString("flow_id").orElseThrow().equals("pixel_tzz:acceptance_flow"),
			"flow_id macro failed"
		);
		check(arguments.getInt("flow_version").orElseThrow() == 2, "flow_version macro failed");
		check(arguments.getString("instance_id").orElseThrow().equals(INSTANCE.toString()), "instance_id macro failed");
		check(arguments.getString("player_uuid").orElseThrow().equals(PLAYER.toString()), "player_uuid macro failed");
		check(arguments.getString("player_name").orElseThrow().equals("Runner"), "player_name macro failed");
		check(arguments.getInt("completed").orElseThrow() == 3, "completed macro failed");
		check(arguments.getInt("total").orElseThrow() == 8, "total macro failed");
		check(arguments.getLong("state_revision").orElseThrow() == 17L, "state_revision macro failed");

		Context instance = new Context(
			Identifier.parse("pixel_tzz:main"),
			Identifier.parse("pixel_tzz:acceptance_flow"),
			1,
			INSTANCE,
			Optional.empty(),
			Optional.empty(),
			8,
			8,
			18L
		);
		check(
			instance.toMacroArguments().getString("player_uuid").orElseThrow().isEmpty(),
			"instance callback must expose an empty player UUID"
		);
		expectRejected(
			() -> new Context(
				Identifier.parse("pixel_tzz:main"),
				Identifier.parse("pixel_tzz:acceptance_flow"),
				1,
				INSTANCE,
				Optional.of(PLAYER),
				Optional.empty(),
				0,
				1,
				0L
			),
			"half-present player callback context must be rejected"
		);
		expectRejected(
			() -> new Context(
				Identifier.parse("pixel_tzz:main"),
				Identifier.parse("pixel_tzz:acceptance_flow"),
				1,
				INSTANCE,
				Optional.empty(),
				Optional.empty(),
				2,
				1,
				0L
			),
			"invalid completion counters must be rejected"
		);
		System.out.println("FLOW_CALLBACK_RUNNER_SELF_CHECK=PASS");
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
