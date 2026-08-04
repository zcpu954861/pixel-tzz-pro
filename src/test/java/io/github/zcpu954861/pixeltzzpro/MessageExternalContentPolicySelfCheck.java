package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.message.MessageExternalContentPolicy;
import io.github.zcpu954861.pixeltzzpro.message.MessageExternalContentPolicy.Directive;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ExternalConflict;

/** Behavioral checks for the V3B HUD external-content decision matrix. */
public final class MessageExternalContentPolicySelfCheck {
	private MessageExternalContentPolicySelfCheck() {
	}

	public static void main(final String[] args) {
		for (ExternalConflict mode : ExternalConflict.values()) {
			expect(
				false,
				mode,
				Directive.RENDER,
				true,
				false,
				false
			);
		}

		expect(
			true,
			ExternalConflict.YIELD,
			Directive.HIDE_CONTINUE,
			false,
			false,
			false
		);
		expect(
			true,
			ExternalConflict.PAUSE,
			Directive.HIDE_PAUSE,
			false,
			true,
			false
		);
		expect(
			true,
			ExternalConflict.OFFSET,
			Directive.RENDER_OFFSET,
			true,
			false,
			true
		);
		expect(
			true,
			ExternalConflict.OVERLAY,
			Directive.RENDER,
			true,
			false,
			false
		);

		System.out.println("MESSAGE_EXTERNAL_CONTENT_POLICY_SELF_CHECK=PASS");
	}

	private static void expect(
		final boolean externalContentVisible,
		final ExternalConflict mode,
		final Directive expected,
		final boolean visible,
		final boolean pauseClock,
		final boolean offset
	) {
		Directive actual = MessageExternalContentPolicy.resolve(
			externalContentVisible,
			mode
		);
		check(
			actual == expected,
			"unexpected directive for external="
				+ externalContentVisible
				+ ", mode="
				+ mode
				+ ": "
				+ actual
		);
		check(
			actual.visible() == visible
				&& actual.pauseClock() == pauseClock
				&& actual.offset() == offset,
			"unexpected directive flags for " + actual
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
