package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.AfterCommitResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.DispatchPlan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.EventRequest;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.FlowTransition;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.HookRegistration;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.Invocation;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract.Origin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure JVM checks for the authoritative post-commit hook-dispatch boundary. */
public final class MessageHookDispatchContractSelfCheck {
	private MessageHookDispatchContractSelfCheck() {
	}

	public static void main(final String[] args) {
		checkAutomaticArgumentsOverrideFixedArguments();
		checkUnregisteredHookIsInert();
		checkFlowCompletionOrder();
		checkReadinessOnlyEmitsOnRisingEdge();
		checkReadinessAndInitialPhaseRuntimeWiring();
		checkMaintenanceOriginsAreSilent();
		checkPresentationFailureCannotRollbackCommit();

		System.out.println("MESSAGE_HOOK_DISPATCH_CONTRACT_SELF_CHECK=PASS");
	}

	private static void checkAutomaticArgumentsOverrideFixedArguments() {
		HookRegistration hook = new HookRegistration(
			"pixel_tzz:self_check/flow_complete",
			Map.of(
				"game_id", "data_pack_spoof",
				"shared", "fixed",
				"fixed_only", "preserved",
				"hook_event", "data_pack_spoof"
			)
		);
		DispatchPlan plan = MessageHookDispatchContract.plan(
			Origin.COMMITTED_TRANSACTION,
			Map.of(MessageHookDispatchContract.ALL_COMPLETE, hook),
			List.of(
				new EventRequest(
					MessageHookDispatchContract.ALL_COMPLETE,
					Map.of(
						"game_id", "pixel_tzz:trusted_game",
						"shared", "automatic",
						"server_tick", "420"
					)
				)
			)
		);
		Map<String, String> arguments = only(plan).arguments();
		checkEquals(
			"pixel_tzz:trusted_game",
			arguments.get("game_id"),
			"server-authored game_id must override the fixed data-pack value"
		);
		checkEquals(
			"automatic",
			arguments.get("shared"),
			"every overlapping automatic argument must override its fixed value"
		);
		checkEquals(
			"preserved",
			arguments.get("fixed_only"),
			"non-conflicting fixed arguments must be retained"
		);
		checkEquals(
			MessageHookDispatchContract.ALL_COMPLETE,
			arguments.get("hook_event"),
			"the contract must author the canonical hook_event last"
		);
	}

	private static void checkUnregisteredHookIsInert() {
		DispatchPlan plan = MessageHookDispatchContract.plan(
			Origin.COMMITTED_TRANSACTION,
			Map.of(),
			List.of(new EventRequest(MessageHookDispatchContract.COMPLETE, Map.of()))
		);
		check(plan.isEmpty(), "an unregistered hook must not create an invocation");
		AtomicInteger attempts = new AtomicInteger();
		MessageHookDispatchContract.deliverAfterCommit(
			"committed",
			plan,
			invocation -> {
				attempts.incrementAndGet();
				return true;
			}
		);
		checkEquals(0, attempts.get(), "an empty plan must perform no delivery work");
	}

	private static void checkFlowCompletionOrder() {
		List<String> events = MessageHookDispatchContract.orderedFlowEvents(
			new FlowTransition(true, true, true, true)
		);
		checkEquals(
			List.of(
				MessageHookDispatchContract.ROLE_CHANGED,
				MessageHookDispatchContract.INITIALIZATION,
				MessageHookDispatchContract.PLAYER_COMPLETE,
				MessageHookDispatchContract.ALL_COMPLETE
			),
			events,
			"flow completion milestones must retain their authoritative order"
		);

		Map<String, HookRegistration> hooks = new LinkedHashMap<>();
		List<EventRequest> requests = new ArrayList<>();
		for (String event : events) {
			hooks.put(event, new HookRegistration("pixel_tzz:self_check/" + event, Map.of()));
			requests.add(new EventRequest(event, Map.of("state_revision", "9")));
		}
		DispatchPlan plan = MessageHookDispatchContract.plan(
			Origin.COMMITTED_TRANSACTION,
			hooks,
			requests
		);
		checkEquals(
			events,
			plan.invocations().stream().map(Invocation::event).toList(),
			"hook resolution must not reorder the committed flow events"
		);
	}

	private static void checkReadinessOnlyEmitsOnRisingEdge() {
		check(
			MessageHookDispatchContract.readinessCompletionEvent(false, false).isEmpty(),
			"incomplete readiness must be silent"
		);
		checkEquals(
			MessageHookDispatchContract.COMPLETE,
			MessageHookDispatchContract.readinessCompletionEvent(false, true).orElseThrow(),
			"the incomplete-to-complete edge must emit readiness COMPLETE"
		);
		check(
			MessageHookDispatchContract.readinessCompletionEvent(true, true).isEmpty(),
			"re-reading a completed readiness instance must not emit twice"
		);
		check(
			MessageHookDispatchContract.readinessCompletionEvent(true, false).isEmpty(),
			"a readiness regression must not masquerade as completion"
		);
	}

	private static void checkReadinessAndInitialPhaseRuntimeWiring() {
		String runtime = readSource(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/PixelTzzServerRuntime.java"
		);
		String readinessAction = sourceSection(
			runtime,
			"\tprivate static void onReadinessAction(",
			"\tprivate static void dispatchFlowAdvanceMessageHooks("
		);
		int playerFlowHook = readinessAction.indexOf("dispatchFlowAdvanceMessageHooks(");
		int readinessComplete = readinessAction.indexOf(
			"dispatchReadinessCompletionMessageHook("
		);
		check(
			playerFlowHook >= 0 && readinessComplete > playerFlowHook,
			"readiness submission must dispatch Flow PLAYER_COMPLETE before readiness completion"
		);
		String completion = sourceSection(
			runtime,
			"\tprivate static void dispatchReadinessCompletionMessageHook(",
			"\t/** Dispatches the Flow START hook"
		);
		int allComplete = completion.indexOf("MessageHookEvent.ALL_COMPLETE");
		int dedicatedComplete = completion.indexOf("dispatchReadinessCompleteMessageHook(");
		check(
			allComplete >= 0 && dedicatedComplete > allComplete,
			"readiness rising edge must dispatch Flow ALL_COMPLETE before Readiness COMPLETE"
		);
		String confirmed = sourceSection(
			runtime,
			"\tprivate static void dispatchConfirmedOperationMessageHooks(",
			"\tprivate static void dispatchRoleMessageHook("
		);
		check(
			confirmed.contains("if (readinessOpened)")
				&& confirmed.contains("dispatchReadinessFlowStartMessageHook(server, after, invoker)"),
			"a newly committed readiness session must dispatch its frozen Flow START hook"
		);
		String initialization = sourceSection(
			runtime,
			"\tprivate static void initializeNewWorldActivity(",
			"\tstatic boolean canInitializeWorldActivity("
		);
		check(
			initialization.contains("dispatchInitialPhaseEnterMessageHook(")
				&& initialization.contains("MessageHookEvent.ENTER"),
			"the first committed game scope must emit its initial Phase ENTER edge"
		);
		String recovery = sourceSection(
			runtime,
			"\tprivate static void ensureReadinessSession(",
			"\tprivate static void onConsoleRequest("
		);
		check(
			!recovery.contains("dispatchReadinessFlowStartMessageHook("),
			"readiness recovery must not replay Flow START"
		);

		String timelineRuntime = readSource(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/TimelineServerRuntime.java"
		);
		check(
			timelineRuntime.contains("if (!Objects.equals(previousPhase, currentPhase))"),
			"timeline phase routing must support one-sided initial/terminal phase edges"
		);
	}

	private static void checkMaintenanceOriginsAreSilent() {
		Map<String, HookRegistration> hooks = Map.of(
			MessageHookDispatchContract.COMPLETE,
			new HookRegistration("pixel_tzz:self_check/readiness", Map.of())
		);
		List<EventRequest> requests = List.of(
			new EventRequest(MessageHookDispatchContract.COMPLETE, Map.of())
		);
		for (
			Origin origin : List.of(
				Origin.RESET,
				Origin.RECOVERY,
				Origin.RELOAD,
				Origin.CHECKPOINT
			)
		) {
			check(
				MessageHookDispatchContract.plan(origin, hooks, requests).isEmpty(),
				origin + " must never project a message hook"
			);
		}
		check(
			!MessageHookDispatchContract.plan(
				Origin.COMMITTED_TRANSACTION,
				hooks,
				requests
			).isEmpty(),
			"the same registered event must remain available to a real commit"
		);
	}

	private static void checkPresentationFailureCannotRollbackCommit() {
		BusinessResult committed = new BusinessResult(73L, "flow_completed");
		List<EventRequest> requests = List.of(
			new EventRequest(MessageHookDispatchContract.ROLE_CHANGED, Map.of()),
			new EventRequest(MessageHookDispatchContract.INITIALIZATION, Map.of()),
			new EventRequest(MessageHookDispatchContract.PLAYER_COMPLETE, Map.of())
		);
		Map<String, HookRegistration> hooks = new LinkedHashMap<>();
		for (EventRequest request : requests) {
			hooks.put(
				request.event(),
				new HookRegistration("pixel_tzz:self_check/" + request.event(), Map.of())
			);
		}
		DispatchPlan plan = MessageHookDispatchContract.plan(
			Origin.COMMITTED_TRANSACTION,
			hooks,
			requests
		);
		AtomicInteger attempts = new AtomicInteger();
		AfterCommitResult<BusinessResult> result =
			MessageHookDispatchContract.deliverAfterCommit(
				committed,
				plan,
				invocation -> switch (attempts.getAndIncrement()) {
					case 0 -> false;
					case 1 -> throw new IllegalStateException("presentation failed");
					default -> true;
				}
			);
		check(
			result.committedResult() == committed,
			"presentation failure must return the exact committed business result"
		);
		checkEquals(73L, result.committedResult().revision(), "revision must not roll back");
		checkEquals(
			"flow_completed",
			result.committedResult().state(),
			"business state must remain committed"
		);
		checkEquals(3, attempts.get(), "one failed hook must not suppress later hooks");
		checkEquals(3, result.report().attempted(), "all hooks must be accounted for");
		checkEquals(1, result.report().delivered(), "only the final hook should deliver");
		checkEquals(2, result.report().failures().size(), "both failure modes must be isolated");
	}

	private static Invocation only(final DispatchPlan plan) {
		checkEquals(1, plan.invocations().size(), "the plan must contain exactly one hook");
		return plan.invocations().getFirst();
	}

	private static String readSource(final String path) {
		try {
			return Files.readString(Path.of(path), StandardCharsets.UTF_8);
		} catch (IOException error) {
			throw new AssertionError("could not read production source " + path, error);
		}
	}

	private static String sourceSection(
		final String source,
		final String startMarker,
		final String endMarker
	) {
		int start = source.indexOf(startMarker);
		int end = start < 0 ? -1 : source.indexOf(endMarker, start + startMarker.length());
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
				"could not locate production source section " + startMarker.trim()
			);
		}
		return source.substring(start, end);
	}

	private static void checkEquals(
		final Object expected,
		final Object actual,
		final String message
	) {
		if (!Objects.equals(expected, actual)) {
			throw new IllegalStateException(
				message + "; expected=" + expected + ", actual=" + actual
			);
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private record BusinessResult(long revision, String state) {
	}
}
