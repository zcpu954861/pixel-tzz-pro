package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.ControlRequestGate.Decision;
import java.util.UUID;

/**
 * Runnable replay and rate-window check without a test framework.
 */
public final class ControlRequestGateSelfCheck {
	private static final UUID PLAYER = new UUID(0L, 1L);

	private ControlRequestGateSelfCheck() {
	}

	public static void main(final String[] args) {
		ControlRequestGate gate = new ControlRequestGate();
		check(gate.admit(PLAYER, 0L, 1_000L) == Decision.ACCEPTED, "first request");
		check(gate.admit(PLAYER, 0L, 1_001L) == Decision.REPLAYED, "exact replay");
		for (long sequence = 1L; sequence < ControlRequestGate.MAX_REQUESTS_PER_WINDOW; sequence++) {
			check(
				gate.admit(PLAYER, sequence, 1_010L + sequence) == Decision.ACCEPTED,
				"normal request " + sequence
			);
		}
		check(
			gate.admit(
				PLAYER,
				ControlRequestGate.MAX_REQUESTS_PER_WINDOW,
				1_100L
			) == Decision.RATE_LIMITED,
			"burst limit"
		);
		check(
			gate.admit(
				PLAYER,
				ControlRequestGate.MAX_REQUESTS_PER_WINDOW + 1L,
				2_100L
			) == Decision.ACCEPTED,
			"new time window"
		);
		gate.remove(PLAYER);
		check(gate.admit(PLAYER, 0L, 2_101L) == Decision.ACCEPTED, "disconnect reset");
		ControlRequestGate flowActions = new ControlRequestGate();
		check(
			gate.admit(PLAYER, 1L, 2_102L) == Decision.ACCEPTED
				&& flowActions.admit(PLAYER, 0L, 2_102L) == Decision.ACCEPTED,
			"independent protocol sequence spaces"
		);
		FlowActionRequestGate scopedFlowActions = new FlowActionRequestGate();
		UUID firstFlow = new UUID(0L, 10L);
		UUID secondFlow = new UUID(0L, 11L);
		check(
			scopedFlowActions.admit(PLAYER, firstFlow, 0L, 2_200L) == Decision.ACCEPTED,
			"first flow sequence"
		);
		check(
			scopedFlowActions.admit(PLAYER, firstFlow, 0L, 2_201L) == Decision.REPLAYED,
			"same flow replay"
		);
		check(
			scopedFlowActions.admit(PLAYER, secondFlow, 0L, 2_202L) == Decision.ACCEPTED,
			"new flow resets its sequence space"
		);
		ResourceReportGate reports = new ResourceReportGate();
		for (int index = 0; index < ResourceReportGate.MAX_REPORTS_PER_WINDOW; index++) {
			check(reports.admit(PLAYER, 3_000L + index), "normal resource report " + index);
		}
		check(!reports.admit(PLAYER, 3_100L), "resource report burst limit");
		check(reports.admit(PLAYER, 4_100L), "resource report window reset");
		reports.remove(PLAYER);
		check(reports.admit(PLAYER, 4_101L), "resource report disconnect reset");
		System.out.println("CONTROL_REQUEST_GATE_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
