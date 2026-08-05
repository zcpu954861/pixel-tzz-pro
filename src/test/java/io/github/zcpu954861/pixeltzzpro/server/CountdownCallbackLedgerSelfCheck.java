package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.CallbackWork;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.PrepareCallbackResult;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import java.util.List;
import java.util.Optional;

/** Runnable check for durable prepare/outcome ordering, retry, recovery and terminal blocking. */
public final class CountdownCallbackLedgerSelfCheck {
	private CountdownCallbackLedgerSelfCheck() {
	}

	public static void main(final String[] args) {
		checkFailureRetryAndUnknownRecovery();
		checkCompleteAndCancelRequiredGates();
		System.out.println("COUNTDOWN_CALLBACK_LEDGER_SELF_CHECK=PASS");
	}

	private static void checkFailureRetryAndUnknownRecovery() {
		List<CallbackDefinition> callbacks = List.of(
			new CallbackDefinition(
				CallbackSlot.START,
				"global_start",
				CallbackScope.GLOBAL,
				CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
				true
			),
			new CallbackDefinition(
				CallbackSlot.START,
				"player_start",
				CallbackScope.EACH_PARTICIPANT,
				CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
				true
			)
		);
		var started = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(), Restrictions.defaults(), List.of(), callbacks
			)
		);
		check(started.callbackWork().size() == 3, "global and each-participant callbacks must expand once");
		Instance instance = started.instance().orElseThrow();
		CallbackKey global = started.callbackWork().stream()
			.filter(value -> value.key().scope() == CallbackScope.GLOBAL)
			.findFirst().orElseThrow().key();
		PrepareCallbackResult prepared = CountdownAuthority.prepareCallback(instance, global, 1_001L, false);
		check(prepared.prepared(), "pending callback must prepare");
		instance = prepared.instance().orElseThrow();
		int firstAttempt = prepared.callback().orElseThrow().attempt();
		instance = CountdownAuthority.recordCallbackOutcome(
			instance,
			global,
			firstAttempt,
			false,
			Optional.of("fixture failure"),
			1_002L
		).instance().orElseThrow();
		check(
			!CountdownAuthority.prepareCallback(instance, global, 1_003L, false).prepared(),
			"failed callback must not auto-retry"
		);
		PrepareCallbackResult retry = CountdownAuthority.prepareCallback(instance, global, 1_003L, true);
		check(retry.callback().orElseThrow().attempt() == 2, "explicit retry increments attempt");
		instance = CountdownAuthority.recordCallbackOutcome(
			retry.instance().orElseThrow(),
			global,
			2,
			true,
			Optional.empty(),
			1_004L
		).instance().orElseThrow();
		check(
			!CountdownAuthority.prepareCallback(instance, global, 1_005L, true).prepared(),
			"successful callback must never replay"
		);

		CallbackWork playerWork = started.callbackWork().stream()
			.filter(value -> value.key().participantId().filter(
				CountdownSelfCheckFixtures.PLAYER_B::equals
			).isPresent())
			.findFirst().orElseThrow();
		PrepareCallbackResult playerPrepared = CountdownAuthority.prepareCallback(
			instance,
			playerWork.key(),
			1_006L,
			false
		);
		Instance recovery = CountdownAuthority.enterRecoveryWait(
			playerPrepared.instance().orElseThrow(),
			2_000L,
			"fixture restart"
		).instance().orElseThrow();
		CallbackEntry unknown = recovery.callbackLedger().entries().stream()
			.filter(value -> value.key().equals(playerWork.key()))
			.findFirst().orElseThrow();
		check(unknown.status() == CallbackStatus.OUTCOME_UNKNOWN, "prepared becomes outcome_unknown");
		check(
			!CountdownAuthority.prepareCallback(recovery, playerWork.key(), 2_001L, false).prepared(),
			"outcome_unknown requires explicit retry"
		);
		check(
			CountdownAuthority.prepareCallback(recovery, playerWork.key(), 2_001L, true).prepared(),
			"outcome_unknown can be explicitly retried in frozen context"
		);
		check(
			CountdownAuthority.reload(recovery).instance().orElseThrow().callbackLedger()
				.equals(recovery.callbackLedger()),
			"reload/HUD-style reprojection must not create ledger entries"
		);
	}

	private static void checkCompleteAndCancelRequiredGates() {
		CallbackDefinition complete = new CallbackDefinition(
			CallbackSlot.COMPLETE,
			"open_warmup",
			CallbackScope.GLOBAL,
			CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
			true
		);
		Instance running = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(), Restrictions.defaults(), List.of(), List.of(complete)
			)
		).instance().orElseThrow();
		var completingTransition = CountdownAuthority.tick(running, 1_250L);
		Instance completing = completingTransition.instance().orElseThrow();
		check(completing.lifecycle().state() == CountdownState.COMPLETING, "fixture must complete");
		CallbackKey completeKey = completingTransition.callbackWork().getFirst().key();
		PrepareCallbackResult prepared = CountdownAuthority.prepareCallback(
			completing, completeKey, 1_251L, false
		);
		Instance failed = CountdownAuthority.recordCallbackOutcome(
			prepared.instance().orElseThrow(), completeKey, 1, false,
			Optional.of("fixture failure"), 1_252L
		).instance().orElseThrow();
		check(
			!CountdownAuthority.finalizeComplete(failed, 1_253L).accepted(),
			"required complete failure must block warmup commit"
		);
		PrepareCallbackResult retry = CountdownAuthority.prepareCallback(failed, completeKey, 1_254L, true);
		Instance succeeded = CountdownAuthority.recordCallbackOutcome(
			retry.instance().orElseThrow(), completeKey, 2, true, Optional.empty(), 1_255L
		).instance().orElseThrow();
		check(
			CountdownAuthority.finalizeComplete(succeeded, 1_256L)
				.instance().orElseThrow().lifecycle().state() == CountdownState.COMPLETED,
			"all required complete callbacks permit exactly one terminal commit"
		);

		CallbackDefinition cancel = new CallbackDefinition(
			CallbackSlot.CANCEL,
			"return_to_approval",
			CallbackScope.GLOBAL,
			CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
			true
		);
		Instance canceling = CountdownAuthority.requestCancel(
			CountdownAuthority.start(
				CountdownSelfCheckFixtures.request(
					DisconnectPolicies.defaults(), Restrictions.defaults(), List.of(), List.of(cancel)
				)
			).instance().orElseThrow(),
			1_010L,
			"fixture cancel"
		).instance().orElseThrow();
		check(
			!CountdownAuthority.finalizeCancel(canceling, 1_011L).accepted(),
			"pending required cancel callback must hold canceling"
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
