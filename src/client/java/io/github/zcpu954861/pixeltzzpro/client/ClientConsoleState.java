package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.CountdownControlContract;
import io.github.zcpu954861.pixeltzzpro.network.payload.CancelConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CommitConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PrepareOperationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.StatefulRequest;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

/**
 * Client projection and request sequencer for the authoritative administrator console.
 */
public final class ClientConsoleState {
	private static long nextRequestSequence = 1L;
	private static long revision;
	private static long consoleResponseRevision;
	private static long lastConsoleSequence = -1L;
	private static Pending pending;
	private static ConsoleSnapshotS2CPayload snapshot;
	private static TargetSnapshotS2CPayload targets;
	private static TimedConfirmation confirmation;
	private static long terminalHostReviewSequence = -1L;
	private static Optional<Identifier> terminalHostReviewOperation = Optional.empty();
	private static String feedback = "";
	private static boolean feedbackError;
	private static OperationResultContext lastResult;

	private ClientConsoleState() {
	}

	public static synchronized void beginConnection() {
		reset();
	}

	public static synchronized void disconnect() {
		reset();
	}

	public static synchronized boolean requestConsole() {
		if (pending != null) {
			return false;
		}
		if (!ClientSessionState.snapshot().currentPlayerHost()) {
			fail("只有当前主持人可以打开主持人控制台");
			return false;
		}
		if (!ClientPlayNetworking.canSend(ConsoleRequestC2SPayload.TYPE)) {
			fail("控制台协议当前不可用");
			return false;
		}
		long sequence = nextSequence();
		pending = new Pending(sequence, RequestKind.CONSOLE, Optional.empty());
		ClientPlayNetworking.send(
			new ConsoleRequestC2SPayload(request(sequence, currentStateRevision(), currentGeneration()))
		);
		return true;
	}

	public static synchronized boolean requestTargets(final ActionEntry action) {
		if (busy()) {
			inform("上一项请求仍在同步，请稍候。");
			return false;
		}
		if (!ClientSessionState.snapshot().currentPlayerHost()) {
			fail("当前玩家已经不是主持人");
			return false;
		}
		if (!ClientPlayNetworking.canSend(TargetSnapshotRequestC2SPayload.TYPE)) {
			fail("目标名单协议当前不可用");
			return false;
		}
		long sequence = nextSequence();
		clearFeedback();
		lastResult = null;
		targets = null;
		pending = new Pending(
			sequence,
			RequestKind.TARGETS,
			Optional.of(action.operationId())
		);
		ClientPlayNetworking.send(
			new TargetSnapshotRequestC2SPayload(
				request(sequence, currentStateRevision(), currentGeneration()),
				action.operationType(),
				action.operationId()
			)
		);
		return true;
	}

	public static synchronized boolean prepareOperation(
		final ActionEntry action,
		final List<UUID> targetIds
	) {
		return prepareOperation(action, targetIds, false);
	}

	public static synchronized boolean refreshOperation(
		final ActionEntry action,
		final List<UUID> targetIds
	) {
		if (
			ClientPageState.isTerminalHostControlOperation(
				action.operationType(),
				action.operationId()
			)
		) {
			return refreshTerminalHostOperation(action, targetIds);
		}
		return prepareOperation(action, renewalTargetIds(action, targetIds), true);
	}

	private static boolean refreshTerminalHostOperation(
		final ActionEntry action,
		final List<UUID> targetIds
	) {
		if (!targetIds.isEmpty()) {
			fail("主持人认领或接管不接受目标玩家");
			return false;
		}
		if (pending != null || terminalHostReviewSequence >= 0L) {
			inform("上一项请求仍在同步，请稍候。");
			return false;
		}
		ClientPageState.TerminalSubmission submission =
			ClientPageState.submitTerminalHostControl(action.operationId());
		if (!submission.sent()) {
			fail(submission.message());
			return false;
		}
		clearFeedback();
		lastResult = null;
		terminalHostReviewSequence = submission.requestSequence();
		terminalHostReviewOperation = Optional.of(action.operationId());
		revision++;
		return true;
	}

	/**
	 * A no-target confirmation may still display server-authored affected-player labels. Those
	 * labels are explanatory only and must never become authority-bearing target IDs when the
	 * review is refreshed.
	 */
	private static List<UUID> renewalTargetIds(
		final ActionEntry action,
		final List<UUID> reviewedTargetIds
	) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(reviewedTargetIds, "reviewedTargetIds");
		return action.targetMode().equals("none") ? List.of() : List.copyOf(reviewedTargetIds);
	}

	private static boolean prepareOperation(
		final ActionEntry action,
		final List<UUID> targetIds,
		final boolean preserveConfirmation
	) {
		if (busy()) {
			inform("上一项请求仍在同步，请稍候。");
			return false;
		}
		if (!ClientSessionState.snapshot().currentPlayerHost()) {
			fail("当前玩家已经不是主持人");
			return false;
		}
		if (!ClientPlayNetworking.canSend(PrepareOperationC2SPayload.TYPE)) {
			fail("二次确认协议当前不可用");
			return false;
		}
		long sequence = nextSequence();
		clearFeedback();
		lastResult = null;
		if (!preserveConfirmation) {
			confirmation = null;
		}
		pending = new Pending(
			sequence,
			RequestKind.PREPARE,
			Optional.of(action.operationId())
		);
		ClientPlayNetworking.send(
			new PrepareOperationC2SPayload(
				request(sequence, currentStateRevision(), currentGeneration()),
				action.operationType(),
				action.operationId(),
				targetIds,
				Optional.empty()
			)
		);
		return true;
	}

	public static synchronized boolean commitConfirmation() {
		if (
			confirmation == null
				|| !ClientPlayNetworking.canSend(CommitConfirmationC2SPayload.TYPE)
		) {
			fail("没有可提交的确认");
			return false;
		}
		ConfirmationS2CPayload value = confirmation.payload;
		long sequence = nextSequence();
		clearFeedback();
		pending = new Pending(
			sequence,
			RequestKind.COMMIT,
			Optional.of(value.operationId())
		);
		ClientPlayNetworking.send(
			new CommitConfirmationC2SPayload(
				request(sequence, value.stateRevision(), value.definitionGeneration()),
				value.tokenId()
			)
		);
		return true;
	}

	public static synchronized void cancelConfirmation() {
		if (confirmation == null) {
			return;
		}
		long sequence = nextSequence();
		if (!ClientPlayNetworking.canSend(CancelConfirmationC2SPayload.TYPE)) {
			fail("无法向服务端取消当前确认");
			confirmation = null;
			return;
		}
		Identifier operationId = confirmation.payload.operationId();
		ClientPlayNetworking.send(
			new CancelConfirmationC2SPayload(sequence, confirmation.payload.tokenId())
		);
		confirmation = null;
		pending = new Pending(sequence, RequestKind.CANCEL, Optional.of(operationId));
		lastResult = null;
		revision++;
	}

	public static synchronized void acceptConsole(final ConsoleSnapshotS2CPayload payload) {
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			if (
				pending != null
					&& pending.kind == RequestKind.CONSOLE
					&& pending.sequence == payload.requestSequence()
			) {
				fail("控制台协议版本不兼容");
			}
			return;
		}
		if (
			!payload.currentPlayerHost()
				|| !ClientSessionState.snapshot().currentPlayerHost()
		) {
			return;
		}
		boolean pushed = payload.requestSequence() == 0L;
		if (
			!pushed && payload.requestSequence() < lastConsoleSequence
				|| pushed && snapshot != null && payload.stateRevision() < snapshot.stateRevision()
		) {
			return;
		}
		if (!pushed) {
			lastConsoleSequence = payload.requestSequence();
			consoleResponseRevision++;
		}
		snapshot = payload;
		if (
			pending != null
				&& pending.kind == RequestKind.CONSOLE
				&& !pushed
				&& pending.sequence == payload.requestSequence()
		) {
			pending = null;
		}
		if (!payload.message().isEmpty()) {
			feedback = payload.message();
			feedbackError = !payload.status().equals("ready");
		}
		revision++;
	}

	public static synchronized void acceptTargets(final TargetSnapshotS2CPayload payload) {
		if (!ClientSessionState.snapshot().currentPlayerHost()) {
			return;
		}
		if (
			pending == null
				|| pending.kind != RequestKind.TARGETS
				|| pending.sequence != payload.requestSequence()
		) {
			return;
		}
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			fail("目标名单协议版本不兼容");
			return;
		}
		if (
			pending.operationId.isEmpty()
				|| !pending.operationId.orElseThrow().equals(payload.operationId())
		) {
			fail("服务端返回了不匹配的目标名单");
			return;
		}
		targets = payload;
		pending = null;
		revision++;
	}

	public static synchronized void acceptConfirmation(final ConfirmationS2CPayload payload) {
		boolean terminalAction = (
			payload.operationType().equals("player_action")
				|| ClientPageState.isTerminalHostControlOperation(
					payload.operationType(),
					payload.operationId()
				)
		)
			&& pending == null
			&& NetworkProtocol.isCompatible(payload.protocolVersion())
			&& ClientPageState.acceptTerminalConfirmation(
				payload.requestSequence(),
				payload.operationId()
			);
		if (terminalAction) {
			confirmation = new TimedConfirmation(payload);
			terminalHostReviewSequence = -1L;
			terminalHostReviewOperation = Optional.empty();
			lastResult = null;
			feedback = "";
			feedbackError = false;
			revision++;
			return;
		}
		if (!ClientSessionState.snapshot().currentPlayerHost()) {
			return;
		}
		if (
			pending == null
				|| pending.kind != RequestKind.PREPARE
				|| pending.sequence != payload.requestSequence()
		) {
			return;
		}
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			fail("二次确认协议版本不兼容");
			return;
		}
		if (
			pending.operationId.isEmpty()
				|| !pending.operationId.orElseThrow().equals(payload.operationId())
		) {
			fail("服务端返回了不匹配的二次确认");
			return;
		}
		confirmation = new TimedConfirmation(payload);
		lastResult = null;
		pending = null;
		revision++;
	}

	public static synchronized void acceptOperationResult(
		final OperationResultS2CPayload payload
	) {
		if (
			terminalHostReviewSequence >= 0L
				&& terminalHostReviewSequence == payload.requestSequence()
		) {
			ClientPageState.consumeTerminalIntentResult(payload.requestSequence());
			Optional<Identifier> operationId = terminalHostReviewOperation;
			terminalHostReviewSequence = -1L;
			terminalHostReviewOperation = Optional.empty();
			if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
				fail("操作结果协议版本不兼容");
				return;
			}
			confirmation = null;
			feedback = OperationFeedbackText.resolve(payload.code(), payload.message());
			feedbackError = !payload.success()
				&& payload.code() != OperationCode.NO_ELIGIBLE_TARGETS;
			lastResult = new OperationResultContext(
				RequestKind.PREPARE,
				operationId,
				payload.code(),
				feedback,
				payload.stateRevision(),
				payload.definitionGeneration()
			);
			revision++;
			return;
		}
		if (
			pending == null
				|| pending.sequence != payload.requestSequence()
		) {
			return;
		}
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			fail("操作结果协议版本不兼容");
			return;
		}
		Pending completed = pending;
		pending = null;
		boolean retryableExpiry = completed.kind == RequestKind.COMMIT
			&& (
				payload.code() == OperationCode.CONFIRMATION_EXPIRED
					|| payload.code() == OperationCode.CONFIRMATION_MISSING
			);
		if (!retryableExpiry) {
			confirmation = null;
		}
		feedback = OperationFeedbackText.resolve(payload.code(), payload.message());
		feedbackError = !payload.success()
			&& payload.code() != OperationCode.NO_ELIGIBLE_TARGETS;
		lastResult = new OperationResultContext(
			completed.kind,
			completed.operationId,
			payload.code(),
			feedback,
			payload.stateRevision(),
			payload.definitionGeneration()
		);
		revision++;
		if (
			payload.refreshConsole()
				&& ClientSessionState.snapshot().currentPlayerHost()
		) {
			requestConsole();
		}
	}

	public static synchronized void acceptSessionProjection(
		final SessionSnapshotS2CPayload payload
	) {
		if (!payload.currentPlayerHost() && snapshot != null) {
			/*
			 * Console snapshots contain the host-only operation catalog, player roster, flow
			 * state, and target metadata. A host transfer therefore revokes this projection
			 * immediately; an OP keeps only the ordinary player terminal and its registered
			 * takeover action.
			 *
			 * A transfer COMMIT result may race this session push. Keep only that correlated
			 * request/result long enough for the confirmation screen to report success, while
			 * dropping every reusable host capability and target cache now.
			 */
			boolean commitInFlight =
				pending != null && pending.kind == RequestKind.COMMIT;
			boolean settledCommit =
				lastResult != null && lastResult.kind == RequestKind.COMMIT;
			snapshot = null;
			targets = null;
			lastConsoleSequence = -1L;
			if (!commitInFlight) {
				pending = null;
				confirmation = null;
			}
			if (!commitInFlight && !settledCommit) {
				lastResult = null;
				clearFeedback();
			} else if (commitInFlight) {
				lastResult = null;
				clearFeedback();
			}
			revision++;
		}
		if (confirmation == null) {
			return;
		}
		/*
		 * A committed operation normally changes the world revision before its operation-result
		 * packet reaches the client. Session snapshots are pushed from that same authoritative
		 * transaction, so invalidating the confirmation here would clear the pending COMMIT and
		 * misreport our own successful mutation as an external conflict. While a commit is in
		 * flight, its correlated result is the only source allowed to close or reject the review.
		 */
		if (pending != null && pending.kind == RequestKind.COMMIT) {
			return;
		}
		ConfirmationS2CPayload value = confirmation.payload;
		if (
			CountdownControlContract.isCountdownControl(
				value.operationType(),
				value.operationId()
			)
				&& value.definitionGeneration() == payload.definitionGeneration()
		) {
			/*
			 * Countdown remaining time advances through a narrow checkpoint without changing its
			 * lifecycle stateVersion. The review is bound to that stable version on the server, so
			 * unrelated global-revision pushes must not erase the page while the host is reading it.
			 */
			return;
		}
		if (
			value.stateRevision() == payload.stateRevision()
				&& value.definitionGeneration() == payload.definitionGeneration()
		) {
			return;
		}
		confirmation = null;
		pending = null;
		targets = null;
		lastResult = new OperationResultContext(
			RequestKind.PREPARE,
			Optional.of(value.operationId()),
			payload.definitionGeneration() != value.definitionGeneration()
				? OperationCode.DEFINITION_GENERATION_STALE
				: OperationCode.STATE_REVISION_STALE,
			payload.definitionGeneration() != value.definitionGeneration()
				? "数据包已重新加载，旧确认已失效。"
				: "服务端状态已经变化，旧确认已失效。",
			payload.stateRevision(),
			payload.definitionGeneration()
		);
		feedback = lastResult.message;
		feedbackError = false;
		revision++;
	}

	public static synchronized Optional<ConsoleSnapshotS2CPayload> snapshot() {
		return Optional.ofNullable(snapshot);
	}

	public static synchronized Optional<TargetSnapshotS2CPayload> targets() {
		return Optional.ofNullable(targets);
	}

	public static synchronized Optional<ConfirmationS2CPayload> confirmation() {
		return Optional.ofNullable(confirmation).map(TimedConfirmation::payload);
	}

	public static synchronized long confirmationRemainingMillis() {
		return confirmation == null ? 0L : Long.MAX_VALUE;
	}

	public static synchronized boolean confirmationExpired() {
		return false;
	}

	public static synchronized boolean pending() {
		return pending != null || terminalHostReviewSequence >= 0L;
	}

	/**
	 * Keeps a host-transfer confirmation screen alive only until its correlated COMMIT result has
	 * been presented. All reusable host snapshots and targets are already revoked separately.
	 */
	public static synchronized boolean hostCommitResolutionPending() {
		return pending != null && pending.kind == RequestKind.COMMIT
			|| lastResult != null && lastResult.kind == RequestKind.COMMIT;
	}

	public static synchronized String feedback() {
		return feedback;
	}

	public static synchronized boolean feedbackError() {
		return feedbackError;
	}

	public static synchronized Optional<OperationResultContext> lastResult() {
		return Optional.ofNullable(lastResult);
	}

	public static synchronized void clearResult() {
		lastResult = null;
		clearFeedback();
		revision++;
	}

	public static synchronized long revision() {
		return revision;
	}

	public static synchronized long consoleResponseRevision() {
		return consoleResponseRevision;
	}

	private static StatefulRequest request(
		final long sequence,
		final long stateRevision,
		final long generation
	) {
		Optional<UUID> flowInstance = snapshot == null
			? Optional.empty()
			: snapshot.activeFlow().map(ConsoleSnapshotS2CPayload.ActiveFlowSummary::instanceId);
		return new StatefulRequest(
			NetworkProtocol.CURRENT_VERSION,
			sequence,
			snapshot == null ? Optional.empty() : snapshot.gameId(),
			generation,
			stateRevision,
			flowInstance,
			Optional.empty()
		);
	}

	private static long currentStateRevision() {
		return Math.max(
			snapshot == null ? 0L : snapshot.stateRevision(),
			ClientSessionState.snapshot().stateRevision()
		);
	}

	private static long currentGeneration() {
		return Math.max(
			snapshot == null ? 0L : snapshot.definitionGeneration(),
			ClientSessionState.snapshot().definitionGeneration()
		);
	}

	private static long nextSequence() {
		return nextRequestSequence++;
	}

	private static void fail(final String message) {
		feedback = message;
		feedbackError = true;
		pending = null;
		revision++;
	}

	private static void clearFeedback() {
		feedback = "";
		feedbackError = false;
	}

	private static boolean busy() {
		return terminalHostReviewSequence >= 0L
			|| pending != null && pending.kind != RequestKind.CONSOLE;
	}

	private static void inform(final String message) {
		feedback = message;
		feedbackError = false;
		revision++;
	}

	private static void reset() {
		nextRequestSequence = 1L;
		lastConsoleSequence = -1L;
		consoleResponseRevision = 0L;
		pending = null;
		snapshot = null;
		targets = null;
		confirmation = null;
		terminalHostReviewSequence = -1L;
		terminalHostReviewOperation = Optional.empty();
		feedback = "";
		feedbackError = false;
		lastResult = null;
		revision++;
	}

	public enum RequestKind {
		CONSOLE,
		TARGETS,
		PREPARE,
		COMMIT,
		CANCEL
	}

	private record Pending(
		long sequence,
		RequestKind kind,
		Optional<Identifier> operationId
	) {
	}

	public record OperationResultContext(
		RequestKind kind,
		Optional<Identifier> operationId,
		OperationCode code,
		String message,
		long stateRevision,
		long definitionGeneration
	) {
		public OperationResultContext {
			operationId = Objects.requireNonNull(operationId, "operationId");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean successfulCommit(final Identifier expectedOperation) {
			return this.kind == RequestKind.COMMIT
				&& this.code == OperationCode.SUCCESS
				&& this.operationId.filter(expectedOperation::equals).isPresent();
		}

		public boolean recoverableConfirmationFailure(final Identifier expectedOperation) {
			return this.operationId.filter(expectedOperation::equals).isPresent()
				&& (
					this.code == OperationCode.CONFIRMATION_EXPIRED
						|| this.code == OperationCode.CONFIRMATION_MISSING
						|| this.code == OperationCode.CONFIRMATION_CONTEXT_CHANGED
						|| this.code == OperationCode.STATE_REVISION_STALE
						|| this.code == OperationCode.DEFINITION_GENERATION_STALE
				);
		}
	}

	private record TimedConfirmation(ConfirmationS2CPayload payload) {
	}
}
