package io.github.zcpu954861.pixeltzzpro.server.message;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ordered, per-item failure boundary for cold-start {@code restart=finalize} delivery.
 *
 * <p>Authorization and history materialization happen before the durable consume. The fallback is
 * decoded before consuming as well. A deterministic rejection is durably consumed without being
 * sent, while a retryable stage or checkpoint conflict is retained in the process queue. Once
 * consume commits, delivery remains at-most-once: a send failure is recorded but never retried.
 * Every stage is isolated so one damaged recovered instance cannot abort the player's handshake
 * or suppress a later instance in the same drain.
 */
public final class MessageRecoveredFinalDeliveryBoundary {
	private MessageRecoveredFinalDeliveryBoundary() {
	}

	public static <T, D> Summary process(
		final List<T> items,
		final Function<T, GateDecision> authorize,
		final Function<T, GateDecision> persistHistory,
		final Function<T, DecodeDecision<D>> decode,
		final Function<T, ConsumeDecision> consume,
		final BiConsumer<T, D> send,
		final Consumer<T> retainForRetry,
		final FailureHandler<T> failureHandler
	) {
		List<T> checkedItems = List.copyOf(items);
		Objects.requireNonNull(authorize, "authorize");
		Objects.requireNonNull(persistHistory, "persistHistory");
		Objects.requireNonNull(decode, "decode");
		Objects.requireNonNull(consume, "consume");
		Objects.requireNonNull(send, "send");
		Objects.requireNonNull(retainForRetry, "retainForRetry");
		Objects.requireNonNull(failureHandler, "failureHandler");

		int retired = 0;
		int retryable = 0;
		int failed = 0;
		int delivered = 0;
		for (T item : checkedItems) {
			Objects.requireNonNull(item, "recovered final item");
			GateDecision authorization;
			try {
				authorization = Objects.requireNonNull(authorize.apply(item), "authorization decision");
			} catch (RuntimeException error) {
				failureHandler.failed(item, Stage.AUTHORIZATION, error);
				retainForRetry.accept(item);
				retryable++;
				continue;
			}
			if (authorization == GateDecision.RETRY) {
				retainForRetry.accept(item);
				retryable++;
				continue;
			}
			if (authorization == GateDecision.PERMANENT_REJECT) {
				ConsumeDecision retirement = consumeSafely(
					item,
					consume,
					retainForRetry,
					failureHandler
				);
				if (retirement == ConsumeDecision.RETRY) {
					retryable++;
				} else {
					retired++;
				}
				continue;
			}

			GateDecision history;
			try {
				history = Objects.requireNonNull(persistHistory.apply(item), "history decision");
			} catch (RuntimeException error) {
				failureHandler.failed(item, Stage.HISTORY, error);
				retainForRetry.accept(item);
				retryable++;
				continue;
			}
			if (history == GateDecision.RETRY) {
				retainForRetry.accept(item);
				retryable++;
				continue;
			}
			if (history == GateDecision.PERMANENT_REJECT) {
				ConsumeDecision retirement = consumeSafely(
					item,
					consume,
					retainForRetry,
					failureHandler
				);
				if (retirement == ConsumeDecision.RETRY) {
					retryable++;
				} else {
					retired++;
				}
				continue;
			}

			final DecodeDecision<D> decoded;
			try {
				decoded = Objects.requireNonNull(decode.apply(item), "decode decision");
			} catch (RuntimeException error) {
				failureHandler.failed(item, Stage.DECODE, error);
				retainForRetry.accept(item);
				retryable++;
				continue;
			}
			if (decoded.decision() == GateDecision.RETRY) {
				retainForRetry.accept(item);
				retryable++;
				continue;
			}
			if (decoded.decision() == GateDecision.PERMANENT_REJECT) {
				ConsumeDecision retirement = consumeSafely(
					item,
					consume,
					retainForRetry,
					failureHandler
				);
				if (retirement == ConsumeDecision.RETRY) {
					retryable++;
				} else {
					retired++;
				}
				continue;
			}

			ConsumeDecision consumed = consumeSafely(
				item,
				consume,
				retainForRetry,
				failureHandler
			);
			if (consumed == ConsumeDecision.RETRY) {
				retryable++;
				continue;
			}
			if (consumed == ConsumeDecision.ALREADY_SETTLED) {
				retired++;
				continue;
			}
			try {
				send.accept(item, decoded.value());
				delivered++;
			} catch (RuntimeException error) {
				failed++;
				failureHandler.failed(item, Stage.SEND, error);
			}
		}
		return new Summary(checkedItems.size(), delivered, retired, retryable, failed);
	}

	private static <T> ConsumeDecision consumeSafely(
		final T item,
		final Function<T, ConsumeDecision> consume,
		final Consumer<T> retainForRetry,
		final FailureHandler<T> failureHandler
	) {
		try {
			ConsumeDecision decision = Objects.requireNonNull(
				consume.apply(item),
				"consume decision"
			);
			if (decision == ConsumeDecision.RETRY) {
				retainForRetry.accept(item);
			}
			return decision;
		} catch (RuntimeException error) {
			failureHandler.failed(item, Stage.CONSUME, error);
			retainForRetry.accept(item);
			return ConsumeDecision.RETRY;
		}
	}

	public enum GateDecision {
		PROCEED,
		PERMANENT_REJECT,
		RETRY
	}

	public enum ConsumeDecision {
		CONSUMED,
		ALREADY_SETTLED,
		RETRY
	}

	public record DecodeDecision<D>(GateDecision decision, D value) {
		public DecodeDecision {
			decision = Objects.requireNonNull(decision, "decision");
			if (decision == GateDecision.PROCEED) {
				value = Objects.requireNonNull(value, "value");
			} else if (value != null) {
				throw new IllegalArgumentException("non-proceed decode cannot carry a value");
			}
		}

		public static <D> DecodeDecision<D> decoded(final D value) {
			return new DecodeDecision<>(GateDecision.PROCEED, value);
		}

		public static <D> DecodeDecision<D> permanentReject() {
			return new DecodeDecision<>(GateDecision.PERMANENT_REJECT, null);
		}

		public static <D> DecodeDecision<D> retry() {
			return new DecodeDecision<>(GateDecision.RETRY, null);
		}
	}

	public enum Stage {
		AUTHORIZATION,
		HISTORY,
		DECODE,
		CONSUME,
		SEND
	}

	@FunctionalInterface
	public interface FailureHandler<T> {
		void failed(T item, Stage stage, RuntimeException error);
	}

	public record Summary(
		int attempted,
		int delivered,
		int retired,
		int retryable,
		int failed
	) {
		public Summary {
			if (
				attempted < 0
					|| delivered < 0
					|| retired < 0
					|| retryable < 0
					|| failed < 0
					|| delivered + retired + retryable + failed != attempted
			) {
				throw new IllegalArgumentException("invalid recovered-final batch summary");
			}
		}
	}
}
