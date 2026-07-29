package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.MutationResult;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.PrepareResult;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.PreparedInvocation;
import io.github.zcpu954861.pixeltzzpro.server.TaskCallbackLedgerAuthority.StepIdentity;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStepStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable check for callback success replay, explicit retry, and crash-unknown semantics.
 */
public final class TaskCallbackLedgerAuthoritySelfCheck {
	private static final UUID PLAYER =
		UUID.fromString("00000000-0000-0000-0000-000000000601");
	private static final Identifier FUNCTION = Identifier.parse("pixel_tzz:task/on_start");

	private TaskCallbackLedgerAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		StepIdentity global = new StepIdentity("task.on_start", FUNCTION, Optional.empty());
		PrepareResult first = TaskCallbackLedgerAuthority.prepareAutomatic(List.of(), global, 10L);
		check(first.prepared(), "first callback step must prepare");
		List<CallbackStep> preparedLedger = first.nextLedger().orElseThrow();
		PreparedInvocation firstInvocation = first.invocation().orElseThrow();
		check(
			preparedLedger.getFirst().status() == CallbackStepStatus.PREPARED,
			"prepare marker must be persisted before invocation"
		);
		check(
			TaskCallbackLedgerAuthority.prepareAutomatic(preparedLedger, global, 10L).code()
				== OperationCode.CALLBACK_FAILED,
			"an in-flight callback must not replay"
		);

		MutationResult failed = TaskCallbackLedgerAuthority.recordOutcome(
			preparedLedger,
			firstInvocation,
			false,
			Optional.of("fixture failure"),
			11L
		);
		check(failed.code() == OperationCode.CALLBACK_FAILED, "failure must remain diagnosable");
		List<CallbackStep> failedLedger = failed.nextLedger().orElseThrow();
		check(
			failedLedger.getFirst().status() == CallbackStepStatus.FAILED,
			"failed callback status was not recorded"
		);
		check(
			!TaskCallbackLedgerAuthority.prepareAutomatic(failedLedger, global, 12L).prepared(),
			"failed callback must require an explicit retry"
		);

		PrepareResult retry = TaskCallbackLedgerAuthority.prepareRetry(failedLedger, global, 13L);
		check(retry.prepared(), "failed callback must allow an explicit retry");
		check(retry.invocation().orElseThrow().attemptCount() == 2, "retry attempt must increment");
		MutationResult succeeded = TaskCallbackLedgerAuthority.recordOutcome(
			retry.nextLedger().orElseThrow(),
			retry.invocation().orElseThrow(),
			true,
			Optional.empty(),
			14L
		);
		List<CallbackStep> succeededLedger = succeeded.nextLedger().orElseThrow();
		check(
			TaskCallbackLedgerAuthority.allSucceeded(succeededLedger, List.of(global)),
			"successful callback must satisfy the required step"
		);
		check(
			!TaskCallbackLedgerAuthority.prepareAutomatic(succeededLedger, global, 15L).prepared(),
			"successful callback must never replay"
		);
		check(
			TaskCallbackLedgerAuthority.prepareRetry(succeededLedger, global, 15L).code()
				== OperationCode.ACTION_UNAVAILABLE,
			"successful callback must reject explicit retry"
		);

		StepIdentity player = new StepIdentity("task.on_start_players", FUNCTION, Optional.of(PLAYER));
		PrepareResult playerPrepared = TaskCallbackLedgerAuthority.prepareAutomatic(
			succeededLedger,
			player,
			16L
		);
		MutationResult unknown = TaskCallbackLedgerAuthority.markPreparedUnknown(
			playerPrepared.nextLedger().orElseThrow(),
			17L,
			"server restarted before the outcome was persisted"
		);
		CallbackStep recovered = unknown.nextLedger().orElseThrow().getLast();
		check(
			recovered.status() == CallbackStepStatus.OUTCOME_UNKNOWN,
			"prepared callback must become outcome_unknown during recovery"
		);
		check(
			!TaskCallbackLedgerAuthority.prepareAutomatic(
				unknown.nextLedger().orElseThrow(),
				player,
				18L
			).prepared(),
			"outcome_unknown must not automatically replay"
		);
		check(
			TaskCallbackLedgerAuthority.prepareRetry(
				unknown.nextLedger().orElseThrow(),
				player,
				18L
			).prepared(),
			"outcome_unknown must support an explicit confirmed retry"
		);

		StepIdentity changedFunction = new StepIdentity(
			"task.on_start",
			Identifier.parse("pixel_tzz:task/replacement"),
			Optional.empty()
		);
		check(
			TaskCallbackLedgerAuthority.prepareAutomatic(succeededLedger, changedFunction, 19L).code()
				== OperationCode.SNAPSHOT_INVALID,
			"the same step key must not switch functions"
		);
		System.out.println("TASK_CALLBACK_LEDGER_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
