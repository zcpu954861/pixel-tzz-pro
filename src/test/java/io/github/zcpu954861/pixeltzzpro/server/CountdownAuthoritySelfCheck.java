package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.DisplayAudience;
import io.github.zcpu954861.pixeltzzpro.network.CountdownControlContract;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.TransitionResult;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicy;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.LaunchPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Runnable pure-state check for Tick, disconnect, reload, recovery and terminal exclusion. */
public final class CountdownAuthoritySelfCheck {
	private CountdownAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		checkTickAndCheckpoints();
		checkPauseReconnectReloadAndRecovery();
		checkDisconnectPoliciesAndTerminalExclusion();
		checkCombinedDisconnectPolicies();
		checkLegacyLaunchPlanCompatibility();
		checkHostCancellationConfirmation();
		checkProjectionAudienceIsolation();
		checkRecoveryCoordinatorAndPendingWork();
		checkAllRequiredOccurrencesGateClock();
		checkHostRebindingPreservesFrozenState();
		System.out.println("COUNTDOWN_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkHostRebindingPreservesFrozenState() {
		UUID replacementHost = UUID.fromString("00000000-0000-0000-0000-000000003c08");
		Instance original = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.PAUSE, DisconnectPolicy.CANCEL),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow();
		Instance rebound = original.withHostId(Optional.of(replacementHost));

		check(
			rebound.gameInstanceId().equals(original.gameInstanceId())
				&& rebound.countdownInstanceId().equals(original.countdownInstanceId())
				&& rebound.definition().equals(original.definition())
				&& rebound.launchPlan().equals(original.launchPlan())
				&& rebound.lifecycle().equals(original.lifecycle())
				&& rebound.totalTicks() == original.totalTicks()
				&& rebound.participants().equals(original.participants())
				&& rebound.restrictions().equals(original.restrictions())
				&& rebound.checkpoints().equals(original.checkpoints())
				&& rebound.callbackLedger().equals(original.callbackLedger()),
			"host ownership changes must preserve every gameplay-frozen countdown field"
		);
		check(
			rebound.hostId().filter(replacementHost::equals).isPresent(),
			"host ownership changes must rebind the frozen host-disconnect authority"
		);
		check(
			CountdownAuthority.hostDisconnected(
				rebound,
				CountdownSelfCheckFixtures.HOST_ID,
				1_001L
			).instance().filter(rebound::equals).isPresent(),
			"the former host must no longer trigger the countdown host-disconnect policy"
		);
		check(
			CountdownAuthority.hostDisconnected(rebound, replacementHost, 1_001L)
				.instance().filter(rebound::equals).isEmpty(),
			"the replacement host must own the countdown host-disconnect policy"
		);
	}

	private static void checkRecoveryCoordinatorAndPendingWork() {
		var startCallback = new io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition(
			io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot.START,
			"recover_start",
			io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope.GLOBAL,
			CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
			true
		);
		TransitionResult started = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(), Restrictions.defaults(), List.of(), List.of(startCallback)
			)
		);
		Instance recovery = CountdownAuthority.enterRecoveryWait(
			started.instance().orElseThrow(), 1_010L, "fixture restart"
		).instance().orElseThrow();
		Set<UUID> all = Set.of(
			CountdownSelfCheckFixtures.HOST_ID,
			CountdownSelfCheckFixtures.PLAYER_B,
			CountdownSelfCheckFixtures.PLAYER_C
		);
		check(
			!CountdownAuthority.reconcileConnections(recovery, all, 1_011L).accepted(),
			"recovery_wait must not bypass resumeRecovery"
		);
		TransitionResult resumed = CountdownAuthority.resumeRecovery(recovery, all, 1_011L);
		check(
			resumed.callbackWork().size() == 1
				&& resumed.callbackWork().getFirst().key().equals(started.callbackWork().getFirst().key())
				&& resumed.callbackWork().getFirst().key().occurrence() == 0L,
			"cold start must resume the original pending callback occurrence"
		);
		var originalDefinition = recovery.definition();
		Instance corrupt = new Instance(
			recovery.gameInstanceId(), recovery.countdownInstanceId(), recovery.purpose(),
			new FrozenDefinition(
				originalDefinition.definitionId(), originalDefinition.generation(),
				new FrozenDocument(originalDefinition.closure().normalizedJson(), "0".repeat(64))
			),
			recovery.launchPlan(), recovery.lifecycle(), recovery.totalTicks(), recovery.participants(),
			recovery.hostId(), recovery.disconnectPolicies(), recovery.restrictions(),
			recovery.restrictionMarkers(), recovery.checkpoints(), recovery.emittedCheckpointIds(),
			recovery.callbackDefinitions(), recovery.callbackLedger()
		);
		check(
			!CountdownAuthority.resumeRecovery(corrupt, all, 1_012L).accepted()
				&& corrupt.lifecycle().state() == CountdownState.RECOVERY_WAIT,
			"missing or corrupt frozen closure must fail closed in recovery_wait"
		);
		check(
			CountdownAuthority.tick(resumed.instance().orElseThrow(), 1_020L)
				.instance().orElseThrow().lifecycle().remainingTicks()
				== resumed.instance().orElseThrow().lifecycle().remainingTicks(),
			"pending required start callback must freeze the authoritative clock"
		);

		check(
			!CountdownServerRuntime.recoverySnapshotStable(recovery, Set.of(), 2_000L),
			"empty verified roster must freeze recovery indefinitely"
		);
		check(
			!CountdownServerRuntime.recoverySnapshotStable(
				recovery, Set.of(CountdownSelfCheckFixtures.PLAYER_B), 2_001L
			),
			"first eligible verified client starts but does not skip the stability window"
		);
		check(
			!CountdownServerRuntime.recoverySnapshotStable(
				recovery, Set.of(CountdownSelfCheckFixtures.PLAYER_B),
				2_001L + CountdownServerRuntime.RECOVERY_CONNECTION_STABILITY_TICKS - 1L
			),
			"recovery must remain frozen before the bounded window elapses"
		);
		check(
			CountdownServerRuntime.recoverySnapshotStable(
				recovery, Set.of(CountdownSelfCheckFixtures.PLAYER_B),
				2_001L + CountdownServerRuntime.RECOVERY_CONNECTION_STABILITY_TICKS
			),
			"stable eligible snapshot may resume only after the full window"
		);
	}

	private static void checkAllRequiredOccurrencesGateClock() {
		var pause = new io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition(
			io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot.PAUSE,
			"pause_gate",
			io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope.GLOBAL,
			CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
			true
		);
		var resume = new io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition(
			io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot.RESUME,
			"resume_gate",
			io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope.GLOBAL,
			CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
			true
		);
		Instance running = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(), Restrictions.defaults(), List.of(), List.of(pause, resume)
			)
		).instance().orElseThrow();
		TransitionResult pausedResult = CountdownAuthority.requiredParticipantDisconnected(
			running, CountdownSelfCheckFixtures.PLAYER_C, 1_010L
		);
		Instance paused = pausedResult.instance().orElseThrow();
		Set<UUID> all = Set.of(
			CountdownSelfCheckFixtures.HOST_ID,
			CountdownSelfCheckFixtures.PLAYER_B,
			CountdownSelfCheckFixtures.PLAYER_C
		);
		check(
			CountdownAuthority.reconcileConnections(paused, all, 1_011L)
				.instance().orElseThrow().lifecycle().state() == CountdownState.WAITING_FOR_PLAYERS,
			"pending required pause occurrence must block resume"
		);
		var pausePrepared = CountdownAuthority.prepareCallback(
			paused, pausedResult.callbackWork().getFirst().key(), 1_012L, false
		);
		Instance pauseSucceeded = CountdownAuthority.recordCallbackOutcome(
			pausePrepared.instance().orElseThrow(), pausePrepared.callback().orElseThrow().key(),
			pausePrepared.callback().orElseThrow().attempt(), true, Optional.empty(), 1_013L
		).instance().orElseThrow();
		TransitionResult resumeResult = CountdownAuthority.reconcileConnections(pauseSucceeded, all, 1_014L);
		Instance resumePending = resumeResult.instance().orElseThrow();
		check(
			resumePending.lifecycle().state() == CountdownState.RUNNING
				&& !CountdownAuthority.clockAdvances(resumePending),
			"new required resume occurrence must gate clock immediately"
		);
		var resumePrepared = CountdownAuthority.prepareCallback(
			resumePending, resumeResult.callbackWork().getFirst().key(), 1_015L, false
		);
		Instance resumeFailed = CountdownAuthority.recordCallbackOutcome(
			resumePrepared.instance().orElseThrow(), resumePrepared.callback().orElseThrow().key(),
			resumePrepared.callback().orElseThrow().attempt(), false,
			Optional.of("fixture resume failure"), 1_016L
		).instance().orElseThrow();
		TransitionResult pausedAgain = CountdownAuthority.requiredParticipantDisconnected(
			resumeFailed, CountdownSelfCheckFixtures.PLAYER_C, 1_017L
		);
		var secondPausePrepared = CountdownAuthority.prepareCallback(
			pausedAgain.instance().orElseThrow(), pausedAgain.callbackWork().getFirst().key(), 1_018L, false
		);
		Instance secondPauseSucceeded = CountdownAuthority.recordCallbackOutcome(
			secondPausePrepared.instance().orElseThrow(), secondPausePrepared.callback().orElseThrow().key(),
			secondPausePrepared.callback().orElseThrow().attempt(), true, Optional.empty(), 1_019L
		).instance().orElseThrow();
		check(
			CountdownAuthority.reconcileConnections(secondPauseSucceeded, all, 1_020L)
				.instance().orElseThrow().lifecycle().state() == CountdownState.WAITING_FOR_PLAYERS,
			"an older failed required resume occurrence cannot be bypassed by later occurrences"
		);
	}

	private static void checkProjectionAudienceIsolation() {
		UUID observer = UUID.fromString("00000000-0000-0000-0000-000000003c08");
		UUID ordinaryOperator = UUID.fromString("00000000-0000-0000-0000-000000003c09");
		UUID formerHost = UUID.fromString("00000000-0000-0000-0000-000000003c0a");
		Set<UUID> ready = Set.of(
			CountdownSelfCheckFixtures.HOST_ID,
			CountdownSelfCheckFixtures.PLAYER_B,
			CountdownSelfCheckFixtures.PLAYER_C,
			observer,
			ordinaryOperator,
			formerHost
		);
		Set<UUID> participants = Set.of(
			CountdownSelfCheckFixtures.PLAYER_B,
			CountdownSelfCheckFixtures.PLAYER_C
		);
		Optional<UUID> host = Optional.of(CountdownSelfCheckFixtures.HOST_ID);
		Set<UUID> observerRoles = Set.of(observer, CountdownSelfCheckFixtures.HOST_ID);

		check(
			CountdownProjectionRuntime.resolveAuthorizedRecipientIds(
				ready,
				participants,
				host,
				new DisplayAudience(false, false),
				observerRoles
			).equals(participants),
			"participants must remain visible without optional audience expansion"
		);
		check(
			CountdownProjectionRuntime.resolveAuthorizedRecipientIds(
				ready,
				participants,
				host,
				new DisplayAudience(true, false),
				observerRoles
			).equals(Set.of(
				CountdownSelfCheckFixtures.HOST_ID,
				CountdownSelfCheckFixtures.PLAYER_B,
				CountdownSelfCheckFixtures.PLAYER_C
			)),
			"include_host must add only the current legal host"
		);
		check(
			CountdownProjectionRuntime.resolveAuthorizedRecipientIds(
				ready,
				participants,
				host,
				new DisplayAudience(false, true),
				observerRoles
			).equals(Set.of(
				CountdownSelfCheckFixtures.PLAYER_B,
				CountdownSelfCheckFixtures.PLAYER_C,
				observer
			)),
			"include_observers must not include an excluded host or an ordinary connected operator"
		);
		check(
			!CountdownProjectionRuntime.resolveAuthorizedRecipientIds(
				Set.of(CountdownSelfCheckFixtures.PLAYER_B),
				participants,
				host,
				new DisplayAudience(true, true),
				observerRoles
			).contains(CountdownSelfCheckFixtures.PLAYER_C),
			"offline or not-yet-authority-ready recipients must not receive a projection"
		);
		check(
			CountdownProjectionRuntime.resolveAuthorizedRecipientIds(
				ready,
				participants,
				host,
				Set.of(formerHost),
				new DisplayAudience(true, false),
				observerRoles
			).equals(Set.of(
				CountdownSelfCheckFixtures.HOST_ID,
				CountdownSelfCheckFixtures.PLAYER_B,
				CountdownSelfCheckFixtures.PLAYER_C,
				formerHost
			)),
			"include_host must retain every legal former host without adding an ordinary operator"
		);
		check(
			CountdownProjectionRuntime.resolveAuthorizedRecipientIds(
				ready,
				participants,
				host,
				Set.of(formerHost),
				new DisplayAudience(false, false),
				observerRoles
			).equals(participants),
			"include_host=false must not expose HUD through current or former host identity"
		);
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
		String reviewedDigest = PixelTzzServerRuntime.countdownCancellationDigest(running);
		check(
			reviewedDigest.equals(PixelTzzServerRuntime.countdownCancellationDigest(advanced)),
			"routine remaining-time checkpoints must retain the reviewed cancellation digest"
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
			!reviewedDigest.equals(
				PixelTzzServerRuntime.countdownCancellationDigest(lifecycleChanged)
			),
			"a lifecycle stateVersion change must also invalidate the token binding digest"
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
		Instance canceled = CountdownAuthority.finalizeCancel(canceling, 1_012L)
			.instance().orElseThrow();
		check(
			canceled.lifecycle().state() == CountdownState.CANCELED
				&& canceled.lifecycle().stateVersion() > challenge.lifecycleStateVersion(),
			"an unchanged reviewed instance must commit cancellation exactly once into CANCELED"
		);
		var duplicateFinalization = CountdownAuthority.finalizeCancel(canceled, 1_013L);
		check(
			duplicateFinalization.accepted()
				&& duplicateFinalization.instance().orElseThrow().equals(canceled)
				&& duplicateFinalization.callbackWork().isEmpty()
				&& duplicateFinalization.restrictionReleases().isEmpty(),
			"a committed cancellation must make duplicate finalization an idempotent no-op"
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

	private static void checkCombinedDisconnectPolicies() {
		Instance hostCancel = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.PAUSE, DisconnectPolicy.CANCEL),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow().withHostId(Optional.of(CountdownSelfCheckFixtures.PLAYER_B));
		Instance hostCanceling = CountdownAuthority.playerDisconnected(
			hostCancel,
			CountdownSelfCheckFixtures.PLAYER_B,
			1_010L
		).instance().orElseThrow();
		check(
			hostCanceling.lifecycle().state() == CountdownState.CANCELING,
			"host cancel must outrank required-participant pause for the same UUID"
		);
		check(
			hostCanceling.lifecycle().stateVersion() == hostCancel.lifecycle().stateVersion() + 1L,
			"a dual-role disconnect must apply exactly one lifecycle transition"
		);

		Instance requiredCancel = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.CANCEL, DisconnectPolicy.PAUSE),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow().withHostId(Optional.of(CountdownSelfCheckFixtures.PLAYER_B));
		check(
			CountdownAuthority.playerDisconnected(
				requiredCancel,
				CountdownSelfCheckFixtures.PLAYER_B,
				1_010L
			).instance().orElseThrow().lifecycle().state() == CountdownState.CANCELING,
			"required-participant cancel must outrank host pause for the same UUID"
		);

		Instance nonHostRequired = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.PAUSE, DisconnectPolicy.CANCEL),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow();
		check(
			CountdownAuthority.playerDisconnected(
				nonHostRequired,
				CountdownSelfCheckFixtures.PLAYER_C,
				1_010L
			).instance().orElseThrow().lifecycle().state()
				== CountdownState.WAITING_FOR_PLAYERS,
			"a non-host required participant must use only the required-participant policy"
		);

		Instance hostOnly = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				new DisconnectPolicies(DisconnectPolicy.CANCEL, DisconnectPolicy.PAUSE),
				Restrictions.defaults(),
				List.of(),
				List.of()
			)
		).instance().orElseThrow();
		check(
			CountdownAuthority.playerDisconnected(
				hostOnly,
				CountdownSelfCheckFixtures.HOST_ID,
				1_010L
			).instance().orElseThrow().lifecycle().state()
				== CountdownState.WAITING_FOR_PLAYERS,
			"a host outside the frozen participant roster must use only the host policy"
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
