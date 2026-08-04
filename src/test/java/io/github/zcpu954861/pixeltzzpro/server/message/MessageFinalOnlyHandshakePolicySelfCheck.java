package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalOnlyHandshakePolicy.Decision;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalOnlyHandshakePolicy.Directive;
import java.util.OptionalLong;

/** Deterministic checks for the bounded final-only capability/asset handshake. */
public final class MessageFinalOnlyHandshakePolicySelfCheck {
	private MessageFinalOnlyHandshakePolicySelfCheck() {}

	public static void main(final String[] arguments) {
		Decision ready = MessageFinalOnlyHandshakePolicy.evaluate(
			false,
			40L,
			OptionalLong.empty(),
			5L
		);
		check(
			ready.directive() == Directive.DELIVER_DYNAMIC
				&& ready.deadlineGameTick().isEmpty(),
			"a completed handshake must deliver dynamically without retaining a deadline"
		);

		Decision initial = MessageFinalOnlyHandshakePolicy.evaluate(
			true,
			40L,
			OptionalLong.empty(),
			5L
		);
		check(
			initial.directive() == Directive.WAIT
				&& initial.deadlineGameTick().orElseThrow() == 45L,
			"the first pending observation must freeze one bounded deadline"
		);

		Decision repeated = MessageFinalOnlyHandshakePolicy.evaluate(
			true,
			44L,
			initial.deadlineGameTick(),
			5L
		);
		check(
			repeated.directive() == Directive.WAIT
				&& repeated.deadlineGameTick().orElseThrow() == 45L,
			"repeated reports must not slide the handshake deadline"
		);

		Decision expired = MessageFinalOnlyHandshakePolicy.evaluate(
			true,
			45L,
			repeated.deadlineGameTick(),
			5L
		);
		check(
			expired.directive() == Directive.DELIVER_STATIC
				&& expired.deadlineGameTick().isEmpty(),
			"the exact deadline must choose the authorized static fallback path"
		);

		System.out.println("MESSAGE_FINAL_ONLY_HANDSHAKE_POLICY_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
