package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Accepted;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Binding;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.CancelResult;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.ConsumeResult;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.IssuedConfirmation;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.OperationKey;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Prompt;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Rejected;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Rejection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.resources.Identifier;

/**
 * Small runnable contract check for the 2C in-memory confirmation boundary.
 */
public final class ConfirmationTokensSelfCheck {
	private static final UUID OPERATOR_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID TARGET_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID TARGET_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final String DIGEST_A = "01".repeat(32);
	private static final String DIGEST_B = "02".repeat(32);

	private ConfirmationTokensSelfCheck() {
	}

	public static void main(final String[] arguments) {
		checkBindingBoundary();
		checkReplacementAndCancellation();
		checkNoTimeLimitAndSingleUse();
		checkEveryBindingField();
		checkConsumedWindowBound();
		checkClear();
		System.out.println("CONFIRMATION_TOKENS_SELF_CHECK=PASS");
	}

	private static void checkBindingBoundary() {
		List<UUID> mutableTargets = new ArrayList<>(List.of(TARGET_B, TARGET_A));
		Binding binding = binding(mutableTargets);
		mutableTargets.clear();
		check(
			binding.targetIds().equals(List.of(TARGET_A, TARGET_B)),
			"targets must be copied and sorted"
		);
		expectUnsupported(
			() -> binding.targetIds().add(TARGET_A),
			"sorted targets must be immutable"
		);

		expectIllegalArgument(
			() -> binding(List.of(TARGET_A, TARGET_A)),
			"duplicate targets must be rejected"
		);
		List<UUID> excessiveTargets = new ArrayList<>();
		for (int index = 0; index <= ConfirmationTokens.MAX_TARGETS; index++) {
			excessiveTargets.add(new UUID(0L, index + 10L));
		}
		expectIllegalArgument(
			() -> binding(excessiveTargets),
			"target count must be bounded"
		);
		expectIllegalArgument(
			() -> new Binding(
				operation("start_flow"),
				List.of(),
				Optional.of(id("game")),
				Optional.empty(),
				1L,
				2L,
				DIGEST_A
			),
			"game and phase bindings must be present together"
		);
		expectIllegalArgument(
			() -> new Binding(
				operation("start_flow"),
				List.of(),
				Optional.empty(),
				Optional.empty(),
				-1L,
				2L,
				DIGEST_A
			),
			"negative generations must be rejected"
		);
		expectIllegalArgument(
			() -> new Binding(
				operation("start_flow"),
				List.of(),
				Optional.empty(),
				Optional.empty(),
				1L,
				-1L,
				DIGEST_A
			),
			"negative revisions must be rejected"
		);
		expectIllegalArgument(
			() -> new Binding(
				operation("start_flow"),
				List.of(),
				Optional.empty(),
				Optional.empty(),
				1L,
				2L,
				"not-a-digest"
			),
			"related state digest must be a SHA-256 value"
		);
		expectIllegalArgument(
			() -> new OperationKey("START_FLOW", id("operation/start")),
			"operation types must use stable lowercase identifiers"
		);

		List<RichText> mutableConsequences = new ArrayList<>(
			List.of(text("将启动强制流程"), text("目标名单将被锁定"))
		);
		Prompt prompt = new Prompt(text("确认启动"), mutableConsequences);
		mutableConsequences.clear();
		check(prompt.consequences().size() == 2, "prompt consequences must be copied");
		expectUnsupported(
			() -> prompt.consequences().clear(),
			"prompt consequences must be immutable"
		);
		expectIllegalArgument(
			() -> new Prompt(text("确认"), List.of()),
			"confirmation must explain at least one consequence"
		);
	}

	private static void checkReplacementAndCancellation() {
		FakeClock clock = new FakeClock();
		ConfirmationTokens tokens = tokens(clock);
		Prompt prompt = prompt();
		Binding binding = binding(List.of(TARGET_A, TARGET_B));

		IssuedConfirmation first = tokens.issue(OPERATOR_A, binding, prompt);
		check(!first.replacedPrevious(), "first issue must not report replacement");
		check(
			first.validForMilliseconds() == 30_000L,
			"protocol-v8 compatibility validity must remain bounded"
		);
		check(first.tokenId().version() == 4, "secure token must use UUID version 4 layout");
		check(first.tokenId().variant() == 2, "secure token must use the RFC 4122 variant");
		check(
			tokens.pendingBinding(OPERATOR_A, first.tokenId()).equals(Optional.of(binding)),
			"the operator must be able to recompute its pending server binding"
		);
		check(tokens.hasPending(OPERATOR_A), "operator-level pending lookup must find the token");
		check(
			tokens.pendingBinding(OPERATOR_A).equals(Optional.of(binding)),
			"operator-level binding lookup must return the frozen binding"
		);
		check(
			tokens.pendingBinding(OPERATOR_B, first.tokenId()).isEmpty(),
			"pending binding inspection must not disclose another operator's token"
		);
		check(
			tokens.rejectionIfUnavailable(OPERATOR_A, first.tokenId()).isEmpty(),
			"a legitimate pending token must remain available"
		);
		check(
			tokens.rejectionIfUnavailable(OPERATOR_B, first.tokenId())
				.equals(Optional.of(Rejection.MISSING)),
			"availability inspection must not disclose another operator's token"
		);

		IssuedConfirmation replacement = tokens.issue(OPERATOR_A, binding, prompt);
		check(replacement.replacedPrevious(), "new issue must replace the operator's old token");
		check(tokens.size() == 1, "one operator may retain only one token");
		check(
			rejection(tokens.consume(OPERATOR_A, first.tokenId(), binding)) == Rejection.MISSING,
			"replaced token must be invalid immediately"
		);
		check(
			tokens.hasPending(OPERATOR_A, replacement.tokenId()),
			"stale submission must not delete the replacement"
		);

		check(
			tokens.cancel(OPERATOR_B, replacement.tokenId()) == CancelResult.NOT_FOUND,
			"another operator cannot cancel a token"
		);
		check(
			tokens.hasPending(OPERATOR_A, replacement.tokenId()),
			"failed cancellation must retain the token"
		);
		check(
			tokens.cancel(OPERATOR_A, UUID.randomUUID()) == CancelResult.NOT_FOUND,
			"stale token id cannot cancel the current token"
		);
		check(
			tokens.cancel(OPERATOR_A, replacement.tokenId()) == CancelResult.CANCELED,
			"matching cancellation must succeed"
		);
		check(tokens.size() == 0, "cancellation must release the token");
		check(!tokens.hasPending(OPERATOR_A), "operator-level pending lookup must clear");
		check(
			tokens.pendingBinding(OPERATOR_A).isEmpty(),
			"operator-level binding lookup must clear"
		);

		IssuedConfirmation disconnectToken = tokens.issue(OPERATOR_A, binding, prompt);
		check(
			tokens.cancelOperator(OPERATOR_A) == CancelResult.CANCELED,
			"operator lifecycle cancellation must release its current token"
		);
		check(
			rejection(tokens.consume(OPERATOR_A, disconnectToken.tokenId(), binding))
				== Rejection.MISSING,
			"operator lifecycle cancellation must invalidate the token"
		);
		check(
			tokens.cancelOperator(OPERATOR_A) == CancelResult.NOT_FOUND,
			"operator lifecycle cancellation must be idempotent"
		);
	}

	private static void checkNoTimeLimitAndSingleUse() {
		FakeClock clock = new FakeClock();
		ConfirmationTokens tokens = tokens(clock);
		Binding binding = binding(List.of(TARGET_A));

		IssuedConfirmation issued = tokens.issue(OPERATOR_A, binding, prompt());
		clock.advance(Long.MAX_VALUE);
		ConsumeResult accepted = tokens.consume(OPERATOR_A, issued.tokenId(), binding);
		check(accepted instanceof Accepted, "token must remain valid while its operation context is unchanged");
		Accepted acceptedOperation = (Accepted)accepted;
		check(
			acceptedOperation.operation().binding().equals(binding),
			"accepted result must return the stored server binding"
		);
		check(
			acceptedOperation.operation().prompt().equals(prompt()),
			"accepted result must return the frozen server prompt"
		);
		check(
			rejection(tokens.consume(OPERATOR_A, issued.tokenId(), binding))
				== Rejection.CONSUMED,
			"accepted token replay must report that it was consumed"
		);
		check(
			tokens.rejectionIfUnavailable(OPERATOR_A, issued.tokenId())
				.equals(Optional.of(Rejection.CONSUMED)),
			"runtime lookup must preserve the consumed rejection"
		);

		IssuedConfirmation wrongOperator = tokens.issue(OPERATOR_A, binding, prompt());
		check(
			rejection(tokens.consume(OPERATOR_B, wrongOperator.tokenId(), binding))
				== Rejection.MISSING,
			"token must be bound to its operator"
		);
		check(
			tokens.hasPending(OPERATOR_A, wrongOperator.tokenId()),
			"another operator must not delete a legitimate pending token"
		);
		check(
			tokens.consume(OPERATOR_A, wrongOperator.tokenId(), binding) instanceof Accepted,
			"legitimate operator must still be able to consume the token"
		);
		check(
			rejection(tokens.consume(OPERATOR_B, wrongOperator.tokenId(), binding))
				== Rejection.MISSING,
			"consumed status must not be disclosed to another operator"
		);
		check(
			!Rejection.CONTEXT_CHANGED.message().contains(DIGEST_B),
			"structured rejection must not echo submitted context"
		);
	}

	private static void checkEveryBindingField() {
		FakeClock clock = new FakeClock();
		ConfirmationTokens tokens = tokens(clock);
		Binding expected = binding(List.of(TARGET_A, TARGET_B));
		List<Binding> changedBindings = List.of(
			new Binding(
				operation("assign_role"),
				expected.targetIds(),
				expected.gameId(),
				expected.phaseId(),
				expected.definitionGeneration(),
				expected.stateRevision(),
				expected.relatedStateSha256()
			),
			new Binding(
				new OperationKey("start_flow", id("operation/other")),
				expected.targetIds(),
				expected.gameId(),
				expected.phaseId(),
				expected.definitionGeneration(),
				expected.stateRevision(),
				expected.relatedStateSha256()
			),
			new Binding(
				expected.operation(),
				List.of(TARGET_A),
				expected.gameId(),
				expected.phaseId(),
				expected.definitionGeneration(),
				expected.stateRevision(),
				expected.relatedStateSha256()
			),
			new Binding(
				expected.operation(),
				expected.targetIds(),
				Optional.of(id("other_game")),
				Optional.of(id("phase/lobby")),
				expected.definitionGeneration(),
				expected.stateRevision(),
				expected.relatedStateSha256()
			),
			new Binding(
				expected.operation(),
				expected.targetIds(),
				expected.gameId(),
				Optional.of(id("phase/running")),
				expected.definitionGeneration(),
				expected.stateRevision(),
				expected.relatedStateSha256()
			),
			new Binding(
				expected.operation(),
				expected.targetIds(),
				expected.gameId(),
				expected.phaseId(),
				expected.definitionGeneration() + 1L,
				expected.stateRevision(),
				expected.relatedStateSha256()
			),
			new Binding(
				expected.operation(),
				expected.targetIds(),
				expected.gameId(),
				expected.phaseId(),
				expected.definitionGeneration(),
				expected.stateRevision() + 1L,
				expected.relatedStateSha256()
			),
			new Binding(
				expected.operation(),
				expected.targetIds(),
				expected.gameId(),
				expected.phaseId(),
				expected.definitionGeneration(),
				expected.stateRevision(),
				DIGEST_B
			)
		);

		for (Binding changed : changedBindings) {
			IssuedConfirmation issued = tokens.issue(OPERATOR_A, expected, prompt());
			check(
				rejection(tokens.consume(OPERATOR_A, issued.tokenId(), changed))
					== Rejection.CONTEXT_CHANGED,
				"every bound context field must invalidate a confirmation"
			);
			check(
				rejection(tokens.consume(OPERATOR_A, issued.tokenId(), expected))
					== Rejection.CONSUMED,
				"context mismatch must consume the token before returning"
			);
		}
	}

	private static void checkConsumedWindowBound() {
		FakeClock clock = new FakeClock();
		ConfirmationTokens tokens = tokens(clock);
		Binding binding = binding(List.of());
		UUID firstToken = null;
		UUID lastToken = null;
		for (int index = 0; index <= ConfirmationTokens.MAX_RECENTLY_CONSUMED; index++) {
			IssuedConfirmation issued = tokens.issue(OPERATOR_A, binding, prompt());
			if (firstToken == null) {
				firstToken = issued.tokenId();
			}
			lastToken = issued.tokenId();
			check(
				tokens.consume(OPERATOR_A, issued.tokenId(), binding) instanceof Accepted,
				"window fixture token must be accepted"
			);
		}
		check(
			rejection(tokens.consume(OPERATOR_A, firstToken, binding)) == Rejection.MISSING,
			"old consumed markers must be evicted at the fixed bound"
		);
		check(
			rejection(tokens.consume(OPERATOR_A, lastToken, binding)) == Rejection.CONSUMED,
			"newest consumed marker must remain available"
		);
		check(
			Rejection.CONSUMED.code().equals("confirmation_consumed"),
			"consumed rejection code must remain stable"
		);
	}

	private static void checkClear() {
		FakeClock clock = new FakeClock();
		ConfirmationTokens tokens = tokens(clock);
		Binding binding = binding(List.of());
		IssuedConfirmation first = tokens.issue(OPERATOR_A, binding, prompt());
		IssuedConfirmation second = tokens.issue(OPERATOR_B, binding, prompt());
		UUID consumedTokenId = first.tokenId();
		check(
			tokens.consume(OPERATOR_A, first.tokenId(), binding) instanceof Accepted,
			"clear fixture must create one consumed marker"
		);
		first = tokens.issue(OPERATOR_A, binding, prompt());
		check(tokens.clear() == 2, "clear must report every invalidated token");
		check(tokens.size() == 0, "clear must empty the store");
		check(
			rejection(tokens.consume(OPERATOR_A, first.tokenId(), binding)) == Rejection.MISSING,
			"cleared first token must be invalid"
		);
		check(
			rejection(tokens.consume(OPERATOR_B, second.tokenId(), binding)) == Rejection.MISSING,
			"cleared second token must be invalid"
		);
		check(
			rejection(tokens.consume(OPERATOR_A, consumedTokenId, binding)) == Rejection.MISSING,
			"clear must also remove consumed-token history"
		);
		check(tokens.clear() == 0, "clearing an empty store must be harmless");
	}

	private static ConfirmationTokens tokens(final FakeClock clock) {
		return new ConfirmationTokens(new CountingSecureRandom(), clock);
	}

	private static Binding binding(final List<UUID> targetIds) {
		return new Binding(
			operation("start_flow"),
			targetIds,
			Optional.of(id("game")),
			Optional.of(id("phase/lobby")),
			3L,
			7L,
			DIGEST_A
		);
	}

	private static OperationKey operation(final String type) {
		return new OperationKey(type, id("operation/" + type));
	}

	private static Prompt prompt() {
		return new Prompt(
			text("确认启动"),
			List.of(text("将启动强制流程"), text("目标名单将被锁定"))
		);
	}

	private static RichText text(final String value) {
		return new RichText("\"" + value + "\"", value);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("test", path);
	}

	private static Rejection rejection(final ConsumeResult result) {
		if (result instanceof Rejected rejected) {
			return rejected.reason();
		}
		throw new AssertionError("expected rejection but received " + result);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void expectIllegalArgument(final Runnable action, final String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void expectUnsupported(final Runnable action, final String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	private static final class FakeClock implements LongSupplier {
		private long nowNanos;

		@Override
		public long getAsLong() {
			return this.nowNanos;
		}

		private void advance(final long nanoseconds) {
			this.nowNanos += nanoseconds;
		}
	}

	private static final class CountingSecureRandom extends SecureRandom {
		private int sequence = 1;

		@Override
		public void nextBytes(final byte[] bytes) {
			Arrays.fill(bytes, (byte)0);
			int value = this.sequence++;
			for (int index = 0; index < Integer.BYTES; index++) {
				bytes[bytes.length - 1 - index] = (byte)(value >>> (index * Byte.SIZE));
			}
		}
	}
}
