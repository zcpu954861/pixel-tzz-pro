package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.ExclusiveChoiceAuthority.ReservationOwner;
import io.github.zcpu954861.pixeltzzpro.server.ExclusiveChoiceAuthority.Result;
import io.github.zcpu954861.pixeltzzpro.server.ExclusiveChoiceAuthority.Selection;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable check for concurrent holds, atomic reselection, lock, and safe reinitialization.
 */
public final class ExclusiveChoiceAuthoritySelfCheck {
	private static final Identifier GAME = Identifier.parse("pixel_tzz:main");
	private static final Identifier PHASE = Identifier.parse("pixel_tzz:setup");
	private static final Identifier FIELD = Identifier.parse("pixel_tzz:hunter_spawn");
	private static final UUID SCOPE =
		UUID.fromString("00000000-0000-0000-0000-000000000701");
	private static final UUID PLAYER_A =
		UUID.fromString("00000000-0000-0000-0000-000000000702");
	private static final UUID PLAYER_B =
		UUID.fromString("00000000-0000-0000-0000-000000000703");
	private static final UUID FLOW_A =
		UUID.fromString("00000000-0000-0000-0000-000000000704");
	private static final UUID FLOW_B =
		UUID.fromString("00000000-0000-0000-0000-000000000705");
	private static final Set<String> OPTIONS = Set.of("north_gate", "station", "plaza");

	private ExclusiveChoiceAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		WorldStateV3 initial = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			WorldStateV2.initial().withActivity(GAME, PHASE),
			java.util.Optional.of(SCOPE),
			java.util.Optional.empty(),
			java.util.List.of(),
			java.util.List.of()
		);
		Selection aNorth = selection(PLAYER_A, FLOW_A, "north_gate");
		Result first = ExclusiveChoiceAuthority.hold(
			initial,
			aNorth,
			OPTIONS,
			UUID.fromString("00000000-0000-0000-0000-000000000711"),
			10L
		);
		check(first.changed(), "first player must hold a free option");
		WorldStateV3 aHeld = first.nextState().orElseThrow();

		Result conflict = ExclusiveChoiceAuthority.hold(
			aHeld,
			selection(PLAYER_B, FLOW_B, "north_gate"),
			OPTIONS,
			UUID.fromString("00000000-0000-0000-0000-000000000712"),
			11L
		);
		check(
			conflict.code() == OperationCode.EXCLUSIVE_OPTION_TAKEN
				&& conflict.conflict().orElseThrow().ownerId().equals(PLAYER_A),
			"only one concurrent player may hold an option"
		);

		Result reselect = ExclusiveChoiceAuthority.hold(
			aHeld,
			selection(PLAYER_A, FLOW_A, "station"),
			OPTIONS,
			UUID.fromString("00000000-0000-0000-0000-000000000713"),
			12L
		);
		check(
			reselect.changed()
				&& reselect.nextState().orElseThrow().exclusiveReservations().size() == 1
				&& reselect.nextState().orElseThrow().exclusiveReservations().getFirst().value().equals("station"),
			"reselection must atomically replace the previous temporary hold"
		);

		Result locked = ExclusiveChoiceAuthority.lock(
			reselect.nextState().orElseThrow(),
			selection(PLAYER_A, FLOW_A, "station"),
			OPTIONS,
			13L
		);
		check(locked.changed(), "flow completion must lock the selected value");
		WorldStateV3 aLocked = locked.nextState().orElseThrow();
		check(
			ExclusiveChoiceAuthority.lockedValue(
				aLocked,
				new ReservationOwner(GAME, FIELD, SCOPE, PLAYER_A)
			).orElseThrow().equals("station"),
			"locked value lookup failed"
		);

		Selection aReplacement = selection(PLAYER_A, FLOW_B, "plaza");
		Result replacement = ExclusiveChoiceAuthority.hold(
			aLocked,
			aReplacement,
			OPTIONS,
			UUID.fromString("00000000-0000-0000-0000-000000000714"),
			14L
		);
		check(
			replacement.nextState().orElseThrow().exclusiveReservations().size() == 2,
			"reinitialization must retain the old lock while holding a replacement"
		);
		Result canceled = ExclusiveChoiceAuthority.cancelHeld(
			replacement.nextState().orElseThrow(),
			new ReservationOwner(GAME, FIELD, SCOPE, PLAYER_A),
			FLOW_B,
			15L
		);
		check(
			ExclusiveChoiceAuthority.lockedValue(
				canceled.nextState().orElseThrow(),
				new ReservationOwner(GAME, FIELD, SCOPE, PLAYER_A)
			).orElseThrow().equals("station"),
			"canceling reinitialization must preserve the old locked value"
		);

		Result replacementAgain = ExclusiveChoiceAuthority.hold(
			aLocked,
			aReplacement,
			OPTIONS,
			UUID.fromString("00000000-0000-0000-0000-000000000715"),
			16L
		);
		Result swapped = ExclusiveChoiceAuthority.lock(
			replacementAgain.nextState().orElseThrow(),
			aReplacement,
			OPTIONS,
			17L
		);
		check(
			swapped.nextState().orElseThrow().exclusiveReservations().size() == 1
				&& ExclusiveChoiceAuthority.lockedValue(
					swapped.nextState().orElseThrow(),
					new ReservationOwner(GAME, FIELD, SCOPE, PLAYER_A)
				).orElseThrow().equals("plaza"),
			"successful reinitialization must atomically swap the old lock"
		);

		check(
			ExclusiveChoiceAuthority.capacity(
				swapped.nextState().orElseThrow(),
				GAME,
				FIELD,
				SCOPE,
				OPTIONS,
				Set.of(PLAYER_A, PLAYER_B)
			).sufficient(),
			"targets must be allowed to reuse their own locked capacity"
		);
		Result released = ExclusiveChoiceAuthority.releaseIneligibleLocked(
			swapped.nextState().orElseThrow(),
			GAME,
			FIELD,
			SCOPE,
			Set.of(PLAYER_B),
			18L
		);
		check(
			released.changed() && released.nextState().orElseThrow().exclusiveReservations().isEmpty(),
			"role mismatch must release a configured locked value"
		);
		ForcedFlowExclusiveChoiceSelfCheck.run();
		System.out.println("EXCLUSIVE_CHOICE_AUTHORITY_SELF_CHECK=PASS");
	}

	private static Selection selection(
		final UUID player,
		final UUID flow,
		final String value
	) {
		return new Selection(GAME, FIELD, SCOPE, player, flow, value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
