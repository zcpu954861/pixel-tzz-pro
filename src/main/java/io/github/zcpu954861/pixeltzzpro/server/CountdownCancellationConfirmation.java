package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure authority for the stable, server-Tick-bounded host cancellation review. */
public final class CountdownCancellationConfirmation {
	public static final long VALID_FOR_TICKS = 600L;

	private CountdownCancellationConfirmation() {
	}

	public static IssueResult issue(final Instance countdown, final long serverTick) {
		Validation validation = validateCancellable(countdown);
		if (!validation.successful()) {
			return IssueResult.rejected(validation.code(), validation.message());
		}
		if (serverTick < 0L || serverTick > Long.MAX_VALUE - VALID_FOR_TICKS) {
			return IssueResult.rejected(OperationCode.INTERNAL_ERROR, "服务端 Tick 无法创建安全确认期限");
		}
		return IssueResult.issued(
			new Challenge(
				countdown.countdownInstanceId(),
				countdown.lifecycle().stateVersion(),
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
			return Validation.rejected(OperationCode.CONFIRMATION_EXPIRED, "确认已超过 30 秒，请重新审阅当前倒计时");
		}
		Validation cancellable = validateCancellable(countdown);
		if (!cancellable.successful()) {
			return cancellable;
		}
		if (
			!countdown.countdownInstanceId().equals(challenge.countdownInstanceId())
				|| countdown.lifecycle().stateVersion() != challenge.lifecycleStateVersion()
		) {
			return Validation.rejected(
				OperationCode.STATE_REVISION_STALE,
				"倒计时实例或生命周期已经变化，请重新审阅"
			);
		}
		return Validation.accepted();
	}

	private static Validation validateCancellable(final Instance countdown) {
		if (countdown == null || countdown.lifecycle().state().terminal()) {
			return Validation.rejected(OperationCode.ACTION_UNAVAILABLE, "当前没有可取消的正式开局倒计时");
		}
		CountdownState state = countdown.lifecycle().state();
		if (state == CountdownState.COMPLETING) {
			return Validation.rejected(OperationCode.ACTION_UNAVAILABLE, "倒计时已进入完成提交，不能再取消");
		}
		if (state == CountdownState.CANCELING) {
			return Validation.rejected(OperationCode.ACTION_UNAVAILABLE, "倒计时已经处于取消提交中");
		}
		return Validation.accepted();
	}

	public record Challenge(
		UUID countdownInstanceId,
		long lifecycleStateVersion,
		long deadlineServerTick
	) {
		public Challenge {
			Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			if (lifecycleStateVersion < 0L || deadlineServerTick < 0L) {
				throw new IllegalArgumentException("countdown cancellation challenge values cannot be negative");
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
				throw new IllegalArgumentException("countdown cancellation issue result is inconsistent");
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
