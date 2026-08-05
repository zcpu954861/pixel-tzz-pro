package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.network.CountdownControlContract;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.TransitionResult;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicy;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.LaunchPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/** Runnable pure-state check for Tick, disconnect, reload, recovery and terminal exclusion. */
public final class CountdownAuthoritySelfCheck {
	private CountdownAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		checkTickAndCheckpoints();
		checkPauseReconnectReloadAndRecovery();
		checkDisconnectPoliciesAndTerminalExclusion();
		checkLegacyLaunchPlanCompatibility();
		checkHostCancellationConfirmation();
		System.out.println("COUNTDOWN_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkLegacyLaunchPlanCompatibility() {
		Instance current = CountdownSelfCheckFixtures.start();
		JsonElement encoded = Instance.CODEC.encodeStart(JsonOps.INSTANCE, current).getOrThrow();
		encoded.getAsJsonObject().remove("launch_plan");
		Instance legacy = Instance.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(legacy.launchPlan().isEmpty(), "legacy schema-v5 countdown must decode without launch_plan");
		Identifier approvalPhase = Identifier.parse("pixel_tzz:test/approval");
		LaunchPlan first = CountdownServerRuntime.resolveLegacyLaunchPlan(legacy, approvalPhase);
		LaunchPlan recovered = CountdownServerRuntime.resolveLegacyLaunchPlan(legacy, approvalPhase);
		check(first.equals(recovered), "legacy launch IDs must be stable across restart recovery");
		check(
			first.participantIds().equals(
				List.of(CountdownSelfCheckFixtures.PLAYER_B, CountdownSelfCheckFixtures.PLAYER_C)
					.stream().sorted().toList()
			),
			"legacy launch recovery must preserve the frozen participant list"
		);
	}

	private static void checkHostCancellationConfirmation() {
		check(
			CountdownControlContract.isCancelOperation(
				CountdownControlContract.CANCEL_OPERATION_TYPE,
				CountdownControlContract.CANCEL_OPERATION_ID
			),
			"countdown cancellation must have one fixed protocol identity"
		);
		Instance running = CountdownSelfCheckFixtures.start();
		var issued = CountdownCancellationConfirmation.issue(running, 1_000L);
		check(issued.successful(), "running countdown must issue a cancellation review");
		var challenge = issued.challenge().orElseThrow();
		check(
			challenge.deadlineServerTick() == 1_000L
				+ CountdownCancellationConfirmation.VALID_FOR_TICKS,
			"cancellation review must use the fixed 30-second server-Tick deadline"
		);

		Instance advanced = CountdownAuthority.tick(running, 1_050L).instance().orElseThrow();
		check(
			advanced.lifecycle().remainingTicks() < running.lifecycle().remainingTicks(),
			"fixture must advance remaining time"
		);
		check(
			CountdownCancellationConfirmation.validate(advanced, challenge, 1_050L).successful(),
			"remaining-time checkpoints must not invalidate a stable cancellation review"
		);

		Instance lifecycleChanged = CountdownAuthority.requiredParticipantDisconnected(
			running,
			CountdownSelfCheckFixtures.PLAYER_C,
			1_020L
		).instance().orElseThrow();
		check(
			CountdownCancellationConfirmation.validate(lifecycleChanged, challenge, 1_020L).code()
				== OperationCode.STATE_REVISION_STALE,
			"a lifecycle stateVersion change must invalidate the cancellation review"
		);
		check(
			CountdownCancellationConfirmation.validate(
				advanced,
				challenge,
				challenge.deadlineServerTick() + 1L
			).code() == OperationCode.CONFIRMATION_EXPIRED,
			"server Tick deadline must expire the review"
		);

		Instance canceling = CountdownAuthority.requestCancel(
			running,
			1_010L,
			"test"
		).instance().orElseThrow();
		check(
			!CountdownCancellationConfirmation.issue(canceling, 1_011L).successful(),
			"canceling countdown must not issue another cancellation review"
		);
		Instance completing = CountdownAuthority.tick(running, 1_300L).instance().orElseThrow();
		check(
			!CountdownCancellationConfirmation.issue(completing, 1_301L).successful(),
			"completion path must not expose cancellation"
		);
	}

	private static void checkTickAndCheckpoints() {
		TransitionResult started = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(),
				Restrictions.defaults(),
				List.of(
					new CheckpointDefinition("five_seconds", 100L),
					new CheckpointDefinition("seven_seconds", 150L),
					new CheckpointDefinition("start", 0L)
				),
				List.of()
			)
		);
		check(started.accepted(), "valid countdown must start");
		Instance running = started.instance().orElseThrow();
		check(running.lifecycle().state() == CountdownState.RUNNING, "countdown starts running");
		check(running.launchPlan().isPresent(), "new countdowns must persist the approved launch plan");

		TransitionResult advanced = CountdownAuthority.tick(running, 1_110L);
		Instance atNinety = advanced.instance().orElseThrow();
		check(atNinety.lifecycle().remainingTicks() == 90L, "server Tick delta must be authoritative");
		check(
			advanced.checkpointToProject().orElseThrow().id().equals("five_seconds"),
			"crossing multiple checkpoints must project only the final meaningful one"
		);
		check(
			atNinety.emittedCheckpointIds().containsAll(List.of("seven_seconds", "five_seconds")),
			"all crossed checkpoints must be durably consumed"
		);
		check(
			CountdownAuthority.tick(atNinety, 1_110L).instance().orElseThrow().equals(atNinety),
			"duplicate server Tick must be idempotent"
		);

		Instance completing = CountdownAuthority.tick(atNinety, 1_250L).instance().orElseThrow();
		check(
			completing.lifecycle().state() == CountdownState.COMPLETING
				&& completing.lifecycle().remainingTicks() == 0L,
			"zero enters completing exactly once"
		);
		Instance completed = CountdownAuthority.finalizeComplete(completing, 1_251L)
			.instance().orElseThrow();
		check(completed.lifecycle().state() == CountdownState.COMPLETED, "completion must commit");
		check(
			!CountdownAuthority.enterRecoveryWait(completed, 1_300L, "restart")
				.instance().orElseThrow().lifecycle().state().equals(CountdownState.RECOVERY_WAIT),
			"completed instance must never recover"
		);
	}

	private static void checkPauseReconnectReloadAndRecovery() {
		Instance running = CountdownSelfCheckFixtures.start();
		Instance paused = CountdownAuthority.requiredParticipantDisconnected(
			running,
			CountdownSelfCheckFixtures.PLAYER_C,
			1_020L
		).instance().orElseThrow();
		check(paused.lifecycle().state() == CountdownState.WAITING_FOR_PLAYERS, "required loss pauses");
		check(paused.launchPlan().equals(running.launchPlan()), "pause must preserve the launch plan");
		check(
			CountdownAuthority.tick(paused, 1_100L).instance().orElseThrow().lifecycle().remainingTicks()
				== paused.lifecycle().remainingTicks(),
			"waiting state must freeze remaining ticks"
		);
		Set<java.util.UUID> allOnline = Set.of(
			CountdownSelfCheckFixtures.HOST_ID,
			CountdownSelfCheckFixtures.PLAYER_B,
			CountdownSelfCheckFixtures.PLAYER_C
		);
		Instance resumed = CountdownAuthority.reconcileConnections(paused, allOnline, 1_100L)
			.instance().orElseThrow();
		check(resumed.lifecycle().state() == CountdownState.RUNNING, "same UUID reconnect resumes");

		Instance reloaded = CountdownAuthority.reload(resumed).instance().orElseThrow();
		check(reloaded.equals(resumed), "reload must preserve the exact frozen instance");
		Instance recovery = CountdownAuthority.enterRecoveryWait(resumed, 1_150L, "normal restart")
			.instance().orElseThrow();
		check(
			recovery.lifecycle().state() == CountdownState.RECOVERY_WAIT
				&& recovery.lifecycle().remainingTicks() == resumed.lifecycle().remainingTicks(),
			"restart must enter recovery_wait without wall-clock deduction"
		);
		Instance recovered = CountdownAuthority.resumeRecovery(recovery, allOnline, 9_000L)
			.instance().orElseThrow();
		check(
			recovered.lifecycle().state() == CountdownState.RUNNING
				&& recovered.lifecycle().remainingTicks() == recovery.lifecycle().remainingTicks(),
			"connection reconciliation resumes the same remaining value"
		);
		check(
			CountdownAuthority.hostDisconnected(recovered, CountdownSelfCheckFixtures.HOST_ID, 9_001L)
				.instance().orElseThrow().equals(recovered),
			"default host disconnect policy must continue"
		);
	}

	private static void checkDisconnectPoliciesAndTerminalExclusion() {
		Instance cancelOnLoss = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.CANCEL, DisconnectPolicy.CONTINUE),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow();
		Instance canceling = CountdownAuthority.requiredParticipantDisconnected(
			cancelOnLoss,
			CountdownSelfCheckFixtures.PLAYER_C,
			1_010L
		).instance().orElseThrow();
		check(canceling.lifecycle().state() == CountdownState.CANCELING, "cancel policy must cancel");
		check(
			canceling.launchPlan().equals(cancelOnLoss.launchPlan()),
			"cancel policy must not rewrite the frozen launch plan"
		);
		check(
			!CountdownAuthority.tick(canceling, 1_100L).instance().orElseThrow().lifecycle().state()
				.equals(CountdownState.RUNNING),
			"canceling must never return to running"
		);
		Instance canceled = CountdownAuthority.finalizeCancel(canceling, 1_011L)
			.instance().orElseThrow();
		check(canceled.lifecycle().state() == CountdownState.CANCELED, "cancel must commit");
		check(
			!CountdownAuthority.finalizeComplete(canceled, 1_012L).accepted(),
			"one instance cannot complete after cancellation"
		);

		Instance continueOnLoss = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.CONTINUE, DisconnectPolicy.CONTINUE),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow();
		check(
			CountdownAuthority.requiredParticipantDisconnected(
				continueOnLoss,
				CountdownSelfCheckFixtures.PLAYER_C,
				1_010L
			).instance().orElseThrow().equals(continueOnLoss),
			"continue policy must retain the frozen roster and state"
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
