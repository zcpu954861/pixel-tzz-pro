package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
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
import net.minecraft.util.Util;

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
		return prepareOperation(action, targetIds, true);
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
		confirmation = new TimedConfirmation(payload, Util.getMillis());
		lastResult = null;
		pending = null;
		revision++;
	}

	public static synchronized void acceptOperationResult(
		final OperationResultS2CPayload payload
	) {
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
		feedback = payload.message().isEmpty()
			? payload.code().serializedName()
			: payload.message();
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
		if (payload.refreshConsole()) {
			requestConsole();
		}
	}

	public static synchronized void acceptSessionProjection(
		final SessionSnapshotS2CPayload payload
	) {
		if (confirmation == null) {
			return;
		}
		ConfirmationS2CPayload value = confirmation.payload;
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
		return confirmation == null ? 0L : confirmation.remaining(Util.getMillis());
	}

	public static synchronized boolean confirmationExpired() {
		return confirmation != null && confirmation.expired(Util.getMillis());
	}

	public static synchronized boolean pending() {
		return pending != null;
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
		return pending != null && pending.kind != RequestKind.CONSOLE;
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

	private record TimedConfirmation(
		ConfirmationS2CPayload payload,
		long receivedAtMillis
	) {
		private long remaining(final long now) {
			return Math.max(0L, this.payload.validForMilliseconds() - (now - this.receivedAtMillis));
		}

		private boolean expired(final long now) {
			return remaining(now) == 0L;
		}
	}
}
