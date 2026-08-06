package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.CountdownControlContract;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Runnable check for ledger-bound, expiration-bound countdown callback retry review. */
public final class CountdownCallbackRetryConfirmationSelfCheck {
	private static final Path SERVER_RUNTIME_SOURCE = Path.of(
		"src/main/java/io/github/zcpu954861/pixeltzzpro/server/PixelTzzServerRuntime.java"
	);
	private static final Path COUNTDOWN_RUNTIME_SOURCE = Path.of(
		"src/main/java/io/github/zcpu954861/pixeltzzpro/server/CountdownServerRuntime.java"
	);
	private static final Path OPENING_APPROVAL_SOURCE = Path.of(
		"src/main/java/io/github/zcpu954861/pixeltzzpro/server/OpeningCountdownApproval.java"
	);
	private static final Path CLIENT_CONSOLE_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/ClientConsoleState.java"
	);

	private CountdownCallbackRetryConfirmationSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		checkOperationIdentity();
		checkLedgerBoundChallenge();
		checkIntegratedRetryRoute();
		checkSeparatedHighRiskAndHandoffRoutes();
		checkCancellationAndClearRecoveryRoute();
		System.out.println("COUNTDOWN_CALLBACK_RETRY_CONFIRMATION_SELF_CHECK=PASS");
	}

	private static void checkCancellationAndClearRecoveryRoute() throws IOException {
		String server = readSource(SERVER_RUNTIME_SOURCE);
		String cancellation = section(
			server,
			"private static void commitCountdownCancellationConfirmation(",
			"private static void commitCountdownCallbackRetryConfirmation("
		);
		checkOrdered(
			cancellation,
			"countdown cancellation commit remains bound to the reviewed lifecycle",
			"CountdownCancellationConfirmation.validate(",
			"payload.request().stateRevision() == pending.challenge().lifecycleStateVersion()",
			"countdownCancellationBinding(",
			"CONFIRMATIONS.consume(",
			"CountdownServerRuntime.requestCancel("
		);

		String execution = section(
			server,
			"private static void executeConfirmedOperation(",
			"private static Optional<TimelineServerRuntime.RuntimeResult> executeConfirmedTimelineOperation("
		);
		check(
			count(execution, "CountdownProjectionRuntime.countdownRetired(server, resetCountdown);") == 1,
			"clear-all must retire the active countdown projection exactly once"
		);
		checkOrdered(
			execution,
			"clear-all countdown state and projection retirement ordering",
			"if (clearAllOperation(binding.operation()))",
			"wrapper.commitV5(",
			"current -> clearAllEnvelope(current, confirmedCandidate)",
			"if (committedRoot.isEmpty())",
			"return;",
			"CountdownProjectionRuntime.countdownRetired(server, resetCountdown);",
			"COUNTDOWN_CANCELLATION_CONFIRMATIONS.clear();",
			"COUNTDOWN_CALLBACK_RETRY_CONFIRMATIONS.clear();"
		);
		String clearEnvelope = section(
			server,
			"static WorldStateV5 clearAllEnvelope(",
			"private static Optional<TimelineCallbackRetryKey> timelineCallbackRetryKey("
		);
		checkOrdered(
			clearEnvelope,
			"clear-all rebuilds every schema-v4 ledger from an empty envelope",
			"WorldStateV4.initial().withCore(clearedCore)",
			"current.withCore(emptyV4).withCountdown(Optional.empty())"
		);
	}

	private static void checkSeparatedHighRiskAndHandoffRoutes() throws IOException {
		check(
			CountdownControlContract.isRetryUnknownCallbacksOperation(
				CountdownControlContract.RETRY_UNKNOWN_CALLBACKS_OPERATION_TYPE,
				CountdownControlContract.RETRY_UNKNOWN_CALLBACKS_OPERATION_ID
			),
			"OUTCOME_UNKNOWN retry must have a distinct operation identity"
		);
		check(
			CountdownControlContract.isRetryHandoffOperation(
				CountdownControlContract.RETRY_HANDOFF_OPERATION_TYPE,
				CountdownControlContract.RETRY_HANDOFF_OPERATION_ID
			)
				&& CountdownControlContract.isAbandonHandoffOperation(
					CountdownControlContract.ABANDON_HANDOFF_OPERATION_TYPE,
					CountdownControlContract.ABANDON_HANDOFF_OPERATION_ID
				),
			"failed handoff retry and abandonment must use separate confirmation identities"
		);
		Instance started = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(),
				Restrictions.defaults(),
				List.of(),
				List.of(new CallbackDefinition(
					CallbackSlot.START,
					"unknown_only",
					CallbackScope.GLOBAL,
					CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
					true
				))
			)
		).instance().orElseThrow();
		var key = started.callbackLedger().entries().getFirst().key();
		var prepared = CountdownAuthority.prepareCallback(started, key, 1_001L, false);
		Instance unknown = CountdownAuthority.enterRecoveryWait(
			prepared.instance().orElseThrow(), 1_100L, "fixture crash"
		).instance().orElseThrow();
		check(
			CountdownCallbackRetryConfirmation.retryableEntries(unknown).isEmpty()
				&& CountdownCallbackRetryConfirmation.outcomeUnknownEntries(unknown).size() == 1,
			"ordinary retry must exclude every OUTCOME_UNKNOWN entry"
		);
		check(
			!CountdownAuthority.prepareCallback(unknown, key, 1_101L, true).prepared()
				&& CountdownAuthority.prepareUnknownCallback(unknown, key, 1_101L, true).prepared(),
			"only separate high-risk authorization may prepare an unknown callback"
		);

		String server = readSource(SERVER_RUNTIME_SOURCE);
		String runtime = readSource(COUNTDOWN_RUNTIME_SOURCE);
		String opening = readSource(OPENING_APPROVAL_SOURCE);
		check(
			server.contains("高风险重试结果未知回调")
				&& server.contains("再次执行可能重复世界副作用")
				&& server.contains("重试冻结任务交接")
				&& server.contains("放弃失败交接并返回等待批准")
				&& server.contains("审阅无倒计时开局"),
			"host controls must expose explicit high-risk unknown, handoff, and degraded-direct reviews"
		);
		check(
			server.contains("出生点预约保持批准时的值，不能改选")
				&& server.contains("玩家准备与字段提交不能再更改")
				&& server.contains("身份、字段和流程提交不能再更改")
				&& server.contains("prepared.target() instanceof RunFunction")
				&& server.contains("倒计时期间只允许只读页面导航和倒计时专用操作"),
			"countdown lifetime must reject old 2D identity, field, readiness, reservation, and function writes"
		);
		check(
			runtime.contains("if (!HANDOFF_FAILURES.containsKey(current.countdownInstanceId()))")
				&& runtime.contains("retryFrozenHandoff(")
				&& runtime.contains("abandonFailedHandoff(")
				&& runtime.contains("HANDOFF_FAILURES.clear();")
				&& runtime.contains("HANDOFF_FAILURES.keySet().removeIf("),
			"failed completed handoff must stop automatic per-Tick retries and expose explicit recovery"
		);
		check(
			opening.contains("OperationCode.DEGRADED_DIRECT")
				&& opening.contains("普通批准不会直接开局")
				&& opening.contains("allowOptionalDegradedDirect")
				&& opening.contains("freezeApprovedCandidate(")
				&& runtime.contains("startExactFrozenCandidate("),
			"invalid optional countdown must require a separate degraded-direct approval"
		);
	}

	private static void checkOperationIdentity() {
		check(
			"retry_countdown_callbacks".equals(
				CountdownControlContract.RETRY_CALLBACKS_OPERATION_TYPE
			),
			"countdown callback retry operation type must remain stable"
		);
		check(
			"pixel-tzz-pro".equals(
				CountdownControlContract.RETRY_CALLBACKS_OPERATION_ID.getNamespace()
			)
				&& "countdown/callback/retry_failed".equals(
					CountdownControlContract.RETRY_CALLBACKS_OPERATION_ID.getPath()
				),
			"countdown callback retry operation path must remain stable"
		);
		check(
			CountdownControlContract.isRetryCallbacksOperation(
				CountdownControlContract.RETRY_CALLBACKS_OPERATION_TYPE,
				CountdownControlContract.RETRY_CALLBACKS_OPERATION_ID
			),
			"shared countdown control contract must recognize the retry operation"
		);
	}

	private static void checkLedgerBoundChallenge() {
		Instance failed = failedStartCallbacks();
		var issued = CountdownCallbackRetryConfirmation.issue(failed, 2_000L);
		check(issued.successful(), "failed callbacks must produce a retry challenge");
		var challenge = issued.challenge().orElseThrow();
		check(
			CountdownCallbackRetryConfirmation.validate(failed, challenge, 2_300L).successful(),
			"unchanged callback ledger must validate"
		);
		check(
			CountdownCallbackRetryConfirmation.validate(failed, challenge, 2_601L).code()
				== OperationCode.CONFIRMATION_EXPIRED,
			"retry challenge must expire after 30 seconds"
		);
		check(
			CountdownCallbackRetryConfirmation.validate(
				failed,
				new CountdownCallbackRetryConfirmation.Challenge(
					UUID.fromString("00000000-0000-0000-0000-000000003cff"),
					challenge.lifecycleStateVersion(),
					challenge.callbackLedgerDigest(),
					challenge.deadlineServerTick()
				),
				2_300L
			).code() == OperationCode.STATE_REVISION_STALE,
			"a challenge must reject a replacement countdown instance"
		);

		var first = CountdownCallbackRetryConfirmation.retryableEntries(failed).getFirst();
		var prepared = CountdownAuthority.prepareCallback(
			failed,
			first.key(),
			2_301L,
			true
		);
		Instance changed = prepared.instance().orElseThrow();
		check(
			CountdownCallbackRetryConfirmation.validate(changed, challenge, 2_302L).code()
				== OperationCode.ACTION_UNAVAILABLE,
			"a prepared callback must block concurrent retry review"
		);
		Instance succeeded = CountdownAuthority.recordCallbackOutcome(
			changed,
			first.key(),
			prepared.callback().orElseThrow().attempt(),
			true,
			Optional.empty(),
			2_303L
		).instance().orElseThrow();
		check(
			CountdownCallbackRetryConfirmation.validate(succeeded, challenge, 2_304L).code()
				== OperationCode.STATE_REVISION_STALE,
			"any ledger outcome change must invalidate the reviewed digest"
		);
		check(
			CountdownCallbackRetryConfirmation.retryableEntries(succeeded).size() == 1,
			"successful entries must be removed from the retry set"
		);
		check(
			CountdownCallbackRetryConfirmation.retryableEntries(succeeded).stream()
				.noneMatch(value -> value.key().equals(first.key())),
			"a successful callback key must never be selected for replay"
		);
	}

	private static void checkIntegratedRetryRoute() throws IOException {
		String server = readSource(SERVER_RUNTIME_SOURCE);
		String countdown = readSource(COUNTDOWN_RUNTIME_SOURCE);
		String client = readSource(CLIENT_CONSOLE_SOURCE);

		check(
			server.contains(
				"RETRY_COUNTDOWN_CALLBACKS_OPERATION =\n\t\tCountdownControlContract.RETRY_CALLBACKS_OPERATION_ID;"
			)
				&& server.contains(
					"RETRY_COUNTDOWN_CALLBACKS =\n\t\tCountdownControlContract.RETRY_CALLBACKS_OPERATION_TYPE;"
				),
			"host console must use the shared callback retry operation identity"
		);

		String actions = section(
			server,
			"private static void addTimelineControlActions(",
			"private static List<WorldStateV3.CallbackStep> failedTimelineCallbackSteps("
		);
		checkOrdered(
			actions,
			"host console callback retry action",
			"CountdownCallbackRetryConfirmation.retryableEntries(countdown)",
			"CountdownCallbackRetryConfirmation.issue(",
			"controlAction(",
			"RETRY_COUNTDOWN_CALLBACKS,",
			"RETRY_COUNTDOWN_CALLBACKS_OPERATION,"
		);

		String prepare = section(
			server,
			"private static void onPrepareOperation(",
			"private static void executeImmediateTimelineOperation("
		);
		checkOrdered(
			prepare,
			"countdown callback retry PREPARE binding",
			"CountdownControlContract.isRetryCallbacksOperation(",
			"CountdownCallbackRetryConfirmation.issue(",
			"countdownCallbackRetryChallenge = challenge.challenge().orElseThrow();",
			"IssuedConfirmation issued = CONFIRMATIONS.issue(",
			"COUNTDOWN_CALLBACK_RETRY_CONFIRMATIONS.put(",
			"new PendingCountdownCallbackRetry("
		);

		String commitRoute = section(
			server,
			"private static void onCommitConfirmation(",
			"private static void commitCountdownCancellationConfirmation("
		);
		checkOrdered(
			commitRoute,
			"countdown callback retry COMMIT route",
			"CountdownControlContract.isRetryCallbacksOperation(",
			"commitCountdownCallbackRetryConfirmation("
		);

		String commit = section(
			server,
			"private static void commitCountdownCallbackRetryConfirmation(",
			"private static void onCancelConfirmation("
		);
		check(
			commit.contains("pending == null || !pending.playerId().equals(actor.getUUID())")
				&& commit.contains("OperationCode.CONFIRMATION_MISSING"),
			"missing or foreign callback retry challenges must be rejected"
		);
		checkOrdered(
			commit,
			"countdown callback retry challenge consumption",
			"COUNTDOWN_CALLBACK_RETRY_CONFIRMATIONS.get(",
			"CountdownCallbackRetryConfirmation.validate(",
			"pending.challenge(),",
			"COUNTDOWN_CALLBACK_RETRY_CONFIRMATIONS.remove(payload.tokenId());",
			"CountdownServerRuntime.retryFailedCallbacks("
		);

		String retry = section(
			countdown,
			"public static RetryResult retryFailedCallbacks(",
			"public static RestrictionDecision restrictionDecision("
		);
		checkOrdered(
			retry,
			"ledger-bound retry and terminal finalization",
			"CountdownCallbackRetryConfirmation.validate(",
			"CountdownCallbackRetryConfirmation.retryableEntries(current)",
			"for (var entry : retryable)",
			"new CallbackWork(entry.key(), entry.functionId(), entry.required()),",
			"true",
			"finalizeIfReady(server, wrapper);"
		);

		String session = section(
			client,
			"public static synchronized void acceptSessionProjection(",
			"public static synchronized Optional<ConsoleSnapshotS2CPayload> snapshot()"
		);
		checkOrdered(
			session,
			"client recovery confirmation revision lifetime",
			"CountdownControlContract.isRevisionStableHostOperation(",
			"value.definitionGeneration() == payload.definitionGeneration()",
			"return;",
			"value.stateRevision() == payload.stateRevision()",
			"confirmation = null;"
		);
	}

	private static String readSource(final Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static String section(
		final String source,
		final String startMarker,
		final String endMarker
	) {
		int start = source.indexOf(startMarker);
		int end = source.indexOf(endMarker, Math.max(0, start + startMarker.length()));
		check(start >= 0 && end > start, "source contract section is unavailable: " + startMarker);
		return source.substring(start, end);
	}

	private static void checkOrdered(
		final String source,
		final String label,
		final String... fragments
	) {
		int cursor = 0;
		for (String fragment : fragments) {
			int next = source.indexOf(fragment, cursor);
			check(next >= 0, label + " is missing or reordered at: " + fragment);
			cursor = next + fragment.length();
		}
	}

	private static int count(final String source, final String fragment) {
		int result = 0;
		int cursor = 0;
		while ((cursor = source.indexOf(fragment, cursor)) >= 0) {
			result++;
			cursor += fragment.length();
		}
		return result;
	}

	private static Instance failedStartCallbacks() {
		List<CallbackDefinition> callbacks = List.of(
			new CallbackDefinition(
				CallbackSlot.START,
				"first",
				CallbackScope.GLOBAL,
				CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
				true
			),
			new CallbackDefinition(
				CallbackSlot.START,
				"second",
				CallbackScope.GLOBAL,
				CountdownSelfCheckFixtures.CALLBACK_FUNCTION,
				false
			)
		);
		var started = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(),
				Restrictions.defaults(),
				List.of(),
				callbacks
			)
		);
		Instance current = started.instance().orElseThrow();
		long tick = 1_100L;
		for (var work : started.callbackWork()) {
			var prepared = CountdownAuthority.prepareCallback(current, work.key(), tick++, false);
			current = CountdownAuthority.recordCallbackOutcome(
				prepared.instance().orElseThrow(),
				work.key(),
				prepared.callback().orElseThrow().attempt(),
				false,
				Optional.of("fixture failure"),
				tick++
			).instance().orElseThrow();
		}
		return current;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
