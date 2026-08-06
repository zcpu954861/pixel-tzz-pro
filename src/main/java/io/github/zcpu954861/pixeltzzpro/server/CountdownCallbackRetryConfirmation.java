package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure authority for a server-Tick-bounded retry of unresolved countdown callbacks. */
public final class CountdownCallbackRetryConfirmation {
	public static final long VALID_FOR_TICKS = 600L;

	private CountdownCallbackRetryConfirmation() {
	}

	public static IssueResult issue(final Instance countdown, final long serverTick) {
		Validation retryable = validateRetryable(countdown);
		if (!retryable.successful()) {
			return IssueResult.rejected(retryable.code(), retryable.message());
		}
		if (serverTick < 0L || serverTick > Long.MAX_VALUE - VALID_FOR_TICKS) {
			return IssueResult.rejected(OperationCode.INTERNAL_ERROR, "服务端 Tick 无法创建安全确认期限");
		}
		return IssueResult.issued(
			new Challenge(
				countdown.countdownInstanceId(),
				countdown.lifecycle().stateVersion(),
				ledgerDigest(countdown),
				serverTick + VALID_FOR_TICKS
			)
		);
	}

	public static Validation validate(
		final Instance countdown,
		final Challenge challenge,
		final long serverTick
	) {
		Objects.requireNonNull(challenge, "challenge");
		if (serverTick < 0L || serverTick > challenge.deadlineServerTick()) {
			return Validation.rejected(
				OperationCode.CONFIRMATION_EXPIRED,
				"确认已超过 30 秒，请重新审阅当前回调账本"
			);
		}
		Validation retryable = validateRetryable(countdown);
		if (!retryable.successful()) {
			return retryable;
		}
		if (
			!countdown.countdownInstanceId().equals(challenge.countdownInstanceId())
				|| countdown.lifecycle().stateVersion() != challenge.lifecycleStateVersion()
				|| !ledgerDigest(countdown).equals(challenge.callbackLedgerDigest())
		) {
			return Validation.rejected(
				OperationCode.STATE_REVISION_STALE,
				"倒计时实例、生命周期或回调结果已经变化，请重新审阅"
			);
		}
		return Validation.accepted();
	}

	public static List<CallbackEntry> retryableEntries(final Instance countdown) {
		if (countdown == null) {
			return List.of();
		}
		return countdown.callbackLedger().entries().stream()
			.filter(value -> value.status() == CallbackStatus.FAILED)
			.toList();
	}

	public static List<CallbackEntry> outcomeUnknownEntries(final Instance countdown) {
		if (countdown == null) {
			return List.of();
		}
		return countdown.callbackLedger().entries().stream()
			.filter(value -> value.status() == CallbackStatus.OUTCOME_UNKNOWN)
			.toList();
	}

	public static String ledgerDigest(final Instance countdown) {
		Objects.requireNonNull(countdown, "countdown");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digestPart(digest, countdown.countdownInstanceId().toString());
			for (CallbackEntry entry : countdown.callbackLedger().entries()) {
				digestPart(digest, entry.key().countdownInstanceId().toString());
				digestPart(digest, entry.key().slot().getSerializedName());
				digestPart(digest, entry.key().callbackId());
				digestPart(digest, entry.key().scope().getSerializedName());
				digestPart(digest, entry.key().participantId().map(UUID::toString).orElse(""));
				digestPart(digest, Long.toString(entry.key().occurrence()));
				digestPart(digest, entry.functionId().toString());
				digestPart(digest, Boolean.toString(entry.required()));
				digestPart(digest, entry.status().getSerializedName());
				digestPart(digest, Integer.toString(entry.attemptCount()));
				digestPart(digest, entry.preparedAtServerTick().map(String::valueOf).orElse(""));
				digestPart(digest, entry.outcomeAtServerTick().map(String::valueOf).orElse(""));
				digestPart(digest, entry.failureReason().orElse(""));
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static Validation validateRetryable(final Instance countdown) {
		if (countdown == null || countdown.lifecycle().state().terminal()) {
			return Validation.rejected(
				OperationCode.ACTION_UNAVAILABLE,
				"当前没有可处理的正式开局倒计时"
			);
		}
		if (
			countdown.callbackLedger().entries().stream()
				.anyMatch(value -> value.status() == CallbackStatus.PREPARED)
		) {
			return Validation.rejected(
				OperationCode.ACTION_UNAVAILABLE,
				"仍有回调结果尚未确认，请等待恢复或重新连接后再审阅"
			);
		}
		if (retryableEntries(countdown).isEmpty()) {
			return Validation.rejected(
				OperationCode.ACTION_UNAVAILABLE,
				"当前没有已确认失败的倒计时回调；结果未知项必须使用独立高风险操作"
			);
		}
		return Validation.accepted();
	}

	private static void digestPart(final MessageDigest digest, final String value) {
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
		digest.update(encoded);
	}

	public record Challenge(
		UUID countdownInstanceId,
		long lifecycleStateVersion,
		String callbackLedgerDigest,
		long deadlineServerTick
	) {
		public Challenge {
			Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			callbackLedgerDigest = Objects.requireNonNull(
				callbackLedgerDigest,
				"callbackLedgerDigest"
			);
			if (
				lifecycleStateVersion < 0L
					|| deadlineServerTick < 0L
					|| !callbackLedgerDigest.matches("[0-9a-f]{64}")
			) {
				throw new IllegalArgumentException("countdown callback retry challenge is invalid");
			}
		}
	}

	public record IssueResult(
		OperationCode code,
		String message,
		Optional<Challenge> challenge
	) {
		public IssueResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			challenge = Objects.requireNonNull(challenge, "challenge");
			if ((code == OperationCode.SUCCESS) != challenge.isPresent()) {
				throw new IllegalArgumentException("countdown callback retry issue result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static IssueResult issued(final Challenge challenge) {
			return new IssueResult(OperationCode.SUCCESS, "", Optional.of(challenge));
		}

		private static IssueResult rejected(final OperationCode code, final String message) {
			return new IssueResult(code, message, Optional.empty());
		}
	}

	public record Validation(OperationCode code, String message) {
		public Validation {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static Validation accepted() {
			return new Validation(OperationCode.SUCCESS, "");
		}

		private static Validation rejected(final OperationCode code, final String message) {
			return new Validation(code, message);
		}
	}
}
