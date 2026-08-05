package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.RestrictedAction;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runnable check for the pure event gate and idempotent marker-only restoration boundary. */
public final class CountdownRestrictionSelfCheck {
	private CountdownRestrictionSelfCheck() {
	}

	public static void main(final String[] args) {
		Instance active = CountdownSelfCheckFixtures.start();
		for (RestrictedAction action : List.of(
			RestrictedAction.MOVEMENT,
			RestrictedAction.ATTACK,
			RestrictedAction.INTERACT,
			RestrictedAction.ITEM_USE,
			RestrictedAction.ITEM_DROP,
			RestrictedAction.ITEM_SWAP,
			RestrictedAction.INVENTORY,
			RestrictedAction.DAMAGE
		)) {
			check(
				CountdownAuthority.restrictionDecision(
					active,
					CountdownSelfCheckFixtures.PLAYER_B,
					action
				).blocked(),
				"safe default must block or immunize " + action
			);
		}
		for (RestrictedAction action : List.of(
			RestrictedAction.CAMERA,
			RestrictedAction.CHAT,
			RestrictedAction.ESCAPE
		)) {
			check(
				!CountdownAuthority.restrictionDecision(
					active,
					CountdownSelfCheckFixtures.PLAYER_B,
					action
				).blocked(),
				"camera, chat and ESC must remain available"
			);
		}
		check(
			!CountdownAuthority.restrictionDecision(
				active,
				UUID.fromString("00000000-0000-0000-0000-000000003cff"),
				RestrictedAction.ATTACK
			).blocked(),
			"restriction must not expand beyond frozen participants"
		);

		Instance oneReleased = CountdownAuthority.releaseRestrictions(
			active,
			Set.of(CountdownSelfCheckFixtures.PLAYER_B)
		).instance().orElseThrow();
		check(
			!CountdownAuthority.restrictionDecision(
				oneReleased,
				CountdownSelfCheckFixtures.PLAYER_B,
				RestrictedAction.ATTACK
			).blocked(),
			"released player marker must stop gating"
		);
		check(
			CountdownAuthority.restrictionDecision(
				oneReleased,
				CountdownSelfCheckFixtures.PLAYER_C,
				RestrictedAction.ATTACK
			).blocked(),
			"other frozen participant must remain gated"
		);
		Instance releasedAgain = CountdownAuthority.releaseRestrictions(
			oneReleased,
			Set.of(CountdownSelfCheckFixtures.PLAYER_B)
		).instance().orElseThrow();
		check(releasedAgain.equals(oneReleased), "repeated release must be idempotent");

		Instance canceled = CountdownAuthority.finalizeCancel(
			CountdownAuthority.requestCancel(active, 1_010L, "fixture cancel").instance().orElseThrow(),
			1_011L
		).instance().orElseThrow();
		check(canceled.lifecycle().state() == CountdownState.CANCELED, "fixture must cancel");
		check(
			canceled.restrictionMarkers().stream().noneMatch(value -> value.active()),
			"terminal boundary must release every marker"
		);

		Restrictions allowAll = new Restrictions(false, false, false, false, false, false, false, false);
		Instance permissive = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(), allowAll, List.of(), List.of()
			)
		).instance().orElseThrow();
		check(
			!CountdownAuthority.restrictionDecision(
				permissive,
				CountdownSelfCheckFixtures.PLAYER_B,
				RestrictedAction.MOVEMENT
			).blocked(),
			"frozen definition may explicitly allow a supported action"
		);
		System.out.println("COUNTDOWN_RESTRICTION_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
