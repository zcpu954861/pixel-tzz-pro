package io.github.zcpu954861.pixeltzzpro.server.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure-Java contract for projecting committed gameplay transitions into optional message hooks.
 *
 * <p>The contract deliberately contains no Minecraft or data-pack classes. Runtime adapters may
 * translate their hook registrations into {@link HookRegistration} values, but the trusted rules
 * remain testable without booting a game: only ordinary committed transactions may emit, missing
 * registrations are inert, automatic facts override fixed arguments, and presentation failures
 * cannot alter a committed business result.</p>
 */
public final class MessageHookDispatchContract {
	public static final String ROLE_CHANGED = "role_changed";
	public static final String INITIALIZATION = "initialization";
	public static final String PLAYER_COMPLETE = "player_complete";
	public static final String ALL_COMPLETE = "all_complete";
	public static final String COMPLETE = "complete";

	private static final int MAX_FAILURE_DETAIL_LENGTH = 256;

	private MessageHookDispatchContract() {
	}

	/** The transaction path that observed the state change. */
	public enum Origin {
		COMMITTED_TRANSACTION,
		RESET,
		RECOVERY,
		RELOAD,
		CHECKPOINT;

		public boolean emitsHooks() {
			return this == COMMITTED_TRANSACTION;
		}
	}

	/** Immutable data-pack-owned cue reference and its fixed arguments. */
	public record HookRegistration(String cue, Map<String, String> fixedArguments) {
		public HookRegistration {
			cue = requireNonBlank(cue, "cue");
			fixedArguments = copyArguments(fixedArguments, "fixedArguments");
		}
	}

	/** One authoritative event observed after its owning transaction committed. */
	public record EventRequest(String event, Map<String, String> automaticArguments) {
		public EventRequest {
			event = canonicalEvent(event);
			automaticArguments = copyArguments(
				automaticArguments,
				"automaticArguments"
			);
		}
	}

	/** Ordered flow milestones; false milestones are omitted without disturbing later ordering. */
	public record FlowTransition(
		boolean roleChanged,
		boolean initialization,
		boolean playerComplete,
		boolean allComplete
	) {
	}

	/** A fully resolved presentation attempt. */
	public record Invocation(
		String event,
		String cue,
		Map<String, String> arguments
	) {
		public Invocation {
			event = canonicalEvent(event);
			cue = requireNonBlank(cue, "cue");
			arguments = copyArguments(arguments, "arguments");
		}
	}

	/** Ordered hook work; an empty plan is a successful no-op. */
	public record DispatchPlan(List<Invocation> invocations) {
		public DispatchPlan {
			invocations = List.copyOf(invocations);
		}

		public static DispatchPlan empty() {
			return new DispatchPlan(List.of());
		}

		public boolean isEmpty() {
			return this.invocations.isEmpty();
		}
	}

	@FunctionalInterface
	public interface Delivery {
		/** Returns false when this presentation attempt was rejected without throwing. */
		boolean deliver(Invocation invocation) throws Exception;
	}

	public record DeliveryFailure(String event, String cue, String detail) {
		public DeliveryFailure {
			event = canonicalEvent(event);
			cue = requireNonBlank(cue, "cue");
			detail = boundedDetail(detail);
		}
	}

	public record DispatchReport(
		int attempted,
		int delivered,
		List<DeliveryFailure> failures
	) {
		public DispatchReport {
			if (attempted < 0 || delivered < 0 || delivered > attempted) {
				throw new IllegalArgumentException("invalid hook delivery counts");
			}
			failures = List.copyOf(failures);
			if (delivered + failures.size() != attempted) {
				throw new IllegalArgumentException(
					"every hook attempt must be either delivered or failed"
				);
			}
		}
	}

	/**
	 * Result of best-effort presentation after a business commit.
	 *
	 * <p>{@code committedResult} is returned unchanged even when every hook fails.</p>
	 */
	public record AfterCommitResult<T>(T committedResult, DispatchReport report) {
		public AfterCommitResult {
			Objects.requireNonNull(committedResult, "committedResult");
			Objects.requireNonNull(report, "report");
		}
	}

	/**
	 * Resolves registered hooks in request order. Silent origins and absent registrations produce
	 * no invocations and therefore cannot accidentally execute a presentation side effect.
	 */
	public static DispatchPlan plan(
		final Origin origin,
		final Map<String, HookRegistration> registrations,
		final List<EventRequest> requests
	) {
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(registrations, "registrations");
		Objects.requireNonNull(requests, "requests");
		if (!origin.emitsHooks()) {
			return DispatchPlan.empty();
		}
		List<Invocation> invocations = new ArrayList<>();
		for (EventRequest request : requests) {
			Objects.requireNonNull(request, "request");
			HookRegistration registration = registrations.get(request.event());
			if (registration == null) {
				continue;
			}
			invocations.add(
				new Invocation(
					request.event(),
					registration.cue(),
					effectiveArguments(
						registration.fixedArguments(),
						request.automaticArguments(),
						request.event()
					)
				)
			);
		}
		return new DispatchPlan(invocations);
	}

	/**
	 * Copies fixed arguments first, then overlays authoritative facts and the canonical event name.
	 */
	public static Map<String, String> effectiveArguments(
		final Map<String, String> fixedArguments,
		final Map<String, String> automaticArguments,
		final String event
	) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>(
			copyArguments(fixedArguments, "fixedArguments")
		);
		result.putAll(copyArguments(automaticArguments, "automaticArguments"));
		result.put("hook_event", canonicalEvent(event));
		return Map.copyOf(result);
	}

	/** Produces the only accepted order for a forced-flow completion transition. */
	public static List<String> orderedFlowEvents(final FlowTransition transition) {
		Objects.requireNonNull(transition, "transition");
		List<String> events = new ArrayList<>(4);
		if (transition.roleChanged()) {
			events.add(ROLE_CHANGED);
		}
		if (transition.initialization()) {
			events.add(INITIALIZATION);
		}
		if (transition.playerComplete()) {
			events.add(PLAYER_COMPLETE);
		}
		if (transition.allComplete()) {
			events.add(ALL_COMPLETE);
		}
		return List.copyOf(events);
	}

	/**
	 * Readiness COMPLETE is an edge event, never a level event. Repeated snapshots at 100% and
	 * regressions are deliberately silent.
	 */
	public static Optional<String> readinessCompletionEvent(
		final boolean wasAllComplete,
		final boolean isAllComplete
	) {
		return !wasAllComplete && isAllComplete
			? Optional.of(COMPLETE)
			: Optional.empty();
	}

	/**
	 * Performs best-effort presentation without allowing rejection or exceptions to escape back
	 * into the already-committed gameplay transaction. Later hooks are still attempted.
	 */
	public static <T> AfterCommitResult<T> deliverAfterCommit(
		final T committedResult,
		final DispatchPlan plan,
		final Delivery delivery
	) {
		Objects.requireNonNull(committedResult, "committedResult");
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(delivery, "delivery");
		int delivered = 0;
		List<DeliveryFailure> failures = new ArrayList<>();
		for (Invocation invocation : plan.invocations()) {
			try {
				if (delivery.deliver(invocation)) {
					delivered++;
				} else {
					failures.add(
						new DeliveryFailure(
							invocation.event(),
							invocation.cue(),
							"delivery rejected"
						)
					);
				}
			} catch (Exception exception) {
				failures.add(
					new DeliveryFailure(
						invocation.event(),
						invocation.cue(),
						exception.getClass().getSimpleName()
							+ ": "
							+ String.valueOf(exception.getMessage())
					)
				);
			}
		}
		return new AfterCommitResult<>(
			committedResult,
			new DispatchReport(plan.invocations().size(), delivered, failures)
		);
	}

	private static Map<String, String> copyArguments(
		final Map<String, String> arguments,
		final String name
	) {
		Objects.requireNonNull(arguments, name);
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		arguments.forEach((key, value) -> {
			result.put(
				requireNonBlank(key, name + " key"),
				Objects.requireNonNull(value, name + " value")
			);
		});
		return Map.copyOf(result);
	}

	private static String canonicalEvent(final String event) {
		return requireNonBlank(event, "event").toLowerCase(Locale.ROOT);
	}

	private static String boundedDetail(final String detail) {
		String value = Objects.requireNonNull(detail, "detail");
		return value.length() <= MAX_FAILURE_DETAIL_LENGTH
			? value
			: value.substring(0, MAX_FAILURE_DETAIL_LENGTH);
	}

	private static String requireNonBlank(final String value, final String name) {
		String result = Objects.requireNonNull(value, name).trim();
		if (result.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return result;
	}
}
