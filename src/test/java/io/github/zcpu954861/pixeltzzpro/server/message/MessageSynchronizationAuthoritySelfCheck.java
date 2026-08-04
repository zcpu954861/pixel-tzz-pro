package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LateArrivalMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationTimeoutMode;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageSynchronizationAuthority.Decision;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageSynchronizationAuthority.Directive;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageSynchronizationAuthority.Policy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/** Executable matrix for V3B multiplayer visual synchronization policy. */
public final class MessageSynchronizationAuthoritySelfCheck {
	private static final UUID A = new UUID(0L, 1L);
	private static final UUID B = new UUID(0L, 2L);

	private MessageSynchronizationAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		checkDistinctSynchronizationModes();
		checkLateArrivalMatrix();
		checkTimeoutMatrix();
		checkSlowRecipientIsolation();
		checkRuntimeCallbackIsolation();
		checkPendingDueNodeCannotPreemptInitialProjection();
		checkScreenStartGateRemainsPresentationOnly();
		System.out.println("MESSAGE_SYNCHRONIZATION_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkDistinctSynchronizationModes() {
		MessageSynchronizationAuthority immediate = authority(
			SynchronizationMode.IMMEDIATE,
			0L,
			LateArrivalMode.SEEK,
			SynchronizationTimeoutMode.START_AVAILABLE
		);
		MessageSynchronizationAuthority synchronizedPlayback = authority(
			SynchronizationMode.SYNCHRONIZED,
			20L,
			LateArrivalMode.SEEK,
			SynchronizationTimeoutMode.START_AVAILABLE
		);
		MessageSynchronizationAuthority perPlayer = authority(
			SynchronizationMode.PER_PLAYER,
			0L,
			LateArrivalMode.SEEK,
			SynchronizationTimeoutMode.START_AVAILABLE
		);

		checkDecision(immediate.markReady(A, 5L), Directive.START_NOW, 5L);
		checkDecision(
			synchronizedPlayback.markReady(A, 5L),
			Directive.START_SHARED,
			20L
		);
		checkDecision(perPlayer.markReady(A, 12L), Directive.START_NOW, 12L);
		checkDecision(perPlayer.markReady(B, 17L), Directive.START_NOW, 17L);
		check(
			perPlayer.decision(A).visualStartElapsedNanos().orElseThrow()
				!= perPlayer.decision(B).visualStartElapsedNanos().orElseThrow(),
			"per-player recipients must freeze independent visual anchors"
		);
	}

	private static void checkLateArrivalMatrix() {
		checkDecision(
			authority(
				SynchronizationMode.SYNCHRONIZED,
				20L,
				LateArrivalMode.SEEK,
				SynchronizationTimeoutMode.START_AVAILABLE
			).markReady(A, 25L),
			Directive.SEEK_SHARED,
			20L
		);
		checkDecision(
			authority(
				SynchronizationMode.SYNCHRONIZED,
				20L,
				LateArrivalMode.WAIT,
				SynchronizationTimeoutMode.START_AVAILABLE
			).markReady(A, 25L),
			Directive.START_NOW,
			25L
		);
		Decision staticFinal = authority(
			SynchronizationMode.SYNCHRONIZED,
			20L,
			LateArrivalMode.STATIC_FINAL,
			SynchronizationTimeoutMode.START_AVAILABLE
		).markReady(A, 25L);
		check(
			staticFinal.directive() == Directive.STATIC_FINAL
				&& staticFinal.visualStartElapsedNanos().isEmpty(),
			"static-final late arrival must not receive an animation anchor"
		);
	}

	private static void checkTimeoutMatrix() {
		MessageSynchronizationAuthority available = authority(
			SynchronizationMode.SYNCHRONIZED,
			20L,
			LateArrivalMode.WAIT,
			SynchronizationTimeoutMode.START_AVAILABLE
		);
		available.markReady(A, 5L);
		available.advance(21L);
		check(
			available.decision(A).directive() == Directive.START_SHARED
				&& available.decision(B).directive() == Directive.PENDING,
			"start-available must release ready recipients without deciding slow recipients"
		);
		checkDecision(available.markReady(B, 30L), Directive.START_NOW, 30L);

		MessageSynchronizationAuthority staticFinal = authority(
			SynchronizationMode.SYNCHRONIZED,
			20L,
			LateArrivalMode.SEEK,
			SynchronizationTimeoutMode.STATIC_FINAL
		);
		staticFinal.markReady(A, 5L);
		staticFinal.advance(21L);
		check(
			staticFinal.decision(A).directive() == Directive.START_SHARED
				&& staticFinal.decision(B).directive() == Directive.STATIC_FINAL,
			"static-final timeout must preserve ready playback and downgrade only pending players"
		);

		MessageSynchronizationAuthority cancel = authority(
			SynchronizationMode.SYNCHRONIZED,
			20L,
			LateArrivalMode.SEEK,
			SynchronizationTimeoutMode.CANCEL
		);
		cancel.markReady(A, 5L);
		cancel.advance(21L);
		check(
			cancel.cancelled()
				&& cancel.decision(A).directive() == Directive.CANCELLED
				&& cancel.decision(B).directive() == Directive.CANCELLED,
			"cancel timeout must cancel the synchronized group atomically"
		);
	}

	private static void checkSlowRecipientIsolation() {
		MessageSynchronizationAuthority authority = authority(
			SynchronizationMode.SYNCHRONIZED,
			10L,
			LateArrivalMode.SEEK,
			SynchronizationTimeoutMode.START_AVAILABLE
		);
		authority.markReady(A, 1L);
		authority.advance(11L);
		check(
			authority.decision(A).directive() == Directive.START_SHARED
				&& authority.decision(B).directive() == Directive.PENDING,
			"one unready client must not rewrite an already-ready client's decision"
		);
	}

	private static void checkRuntimeCallbackIsolation() {
		String source;
		try {
			source = Files.readString(
				Path.of(
					"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageServerRuntime.java"
				),
				StandardCharsets.UTF_8
			);
		} catch (IOException error) {
			throw new AssertionError("could not inspect the message runtime synchronization bridge", error);
		}
		int callbackAdvance = source.indexOf(
			"advanceAuthoritativeCallbacks(server, instance, stream, elapsed, clock);"
		);
		int visualGate = source.indexOf(
			"ensureSynchronizationDecision(server, instance, stream, clock)",
			callbackAdvance
		);
		check(
			callbackAdvance >= 0 && visualGate > callbackAdvance,
			"authoritative callbacks must advance before any recipient visual readiness gate"
		);
		int callbackBase = source.indexOf("private static Optional<Long> exactCallbackBase(");
		int callbackResolver = source.indexOf(
			"private static void resolveExternalCallback(",
			callbackBase
		);
		String body = source.substring(callbackBase, callbackResolver);
		check(
			body.contains("exactAuthoritativeNodeBase(")
				&& !body.contains("perPlayerAnchorElapsed"),
			"callback scheduling must use the cue authority clock, never a recipient visual anchor"
		);
	}

	private static void checkPendingDueNodeCannotPreemptInitialProjection() {
		String source;
		try {
			source = Files.readString(
				Path.of(
					"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageServerRuntime.java"
				),
				StandardCharsets.UTF_8
			);
		} catch (IOException error) {
			throw new AssertionError("could not inspect the message runtime due-node bridge", error);
		}
		int nodeDue = source.indexOf("case NODE_DUE -> {");
		int nextCase = source.indexOf("case PAUSE ->", nodeDue);
		check(nodeDue >= 0 && nextCase > nodeDue, "NODE_DUE projection branch is missing");
		String body = source.substring(nodeDue, nextCase);
		int readinessGate = body.indexOf(
			"if (!ensureSynchronizationDecision(server, instance, stream, clock))"
		);
		int pendingRetention = body.indexOf("stream.pendingDueOccurrences.add(occurrence)");
		int initialProjection = body.indexOf(
			"compileAndSendPlan(server, instance, stream, clock)"
		);
		int dueProjection = body.indexOf("compileDueOccurrence(");
		check(
			readinessGate >= 0
				&& pendingRetention > readinessGate
				&& initialProjection > pendingRetention
				&& dueProjection > initialProjection,
			"a due-at-zero node must wait for readiness and initial projection establishment"
		);
		check(
			source.contains("flushPendingDueOccurrences(server, instance, stream, clock);")
				&& source.contains(
					"private final Set<NodeOccurrence> pendingDueOccurrences = new LinkedHashSet<>();"
				),
			"pending due nodes must be retained and flushed after client readiness"
		);
	}

	private static void checkScreenStartGateRemainsPresentationOnly() {
		String runtime;
		String wireState;
		try {
			runtime = Files.readString(
				Path.of(
					"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageServerRuntime.java"
				),
				StandardCharsets.UTF_8
			);
			wireState = Files.readString(
				Path.of(
					"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/ClientMessageWireState.java"
				),
				StandardCharsets.UTF_8
			);
		} catch (IOException error) {
			throw new AssertionError("could not inspect the message screen-priority bridge", error);
		}
		int readinessStart = runtime.indexOf(
			"private static boolean recipientSynchronizationReady("
		);
		int readinessEnd = runtime.indexOf(
			"private static boolean ensureSynchronizationDecision(",
			readinessStart
		);
		check(
			readinessStart >= 0 && readinessEnd > readinessStart,
			"recipient synchronization readiness boundary is missing"
		);
		String readiness = runtime.substring(readinessStart, readinessEnd);
		check(
			readiness.contains("cueAssetsReady(authority, player, instance.cue().id())")
				&& !readiness.contains("screenAllows("),
			"screen_start must not consume the asset synchronization window or force late-arrival seeking"
		);
		int occurrenceStart = runtime.indexOf(
			"private static Optional<Long> resolveOccurrenceStart("
		);
		int occurrenceEnd = runtime.indexOf(
			"private static long occurrenceProjectionStart(",
			occurrenceStart
		);
		check(
			occurrenceStart >= 0 && occurrenceEnd > occurrenceStart,
			"per-node screen-start boundary is missing"
		);
		String occurrence = runtime.substring(occurrenceStart, occurrenceEnd);
		check(
			occurrence.contains("screenAllows(stream.key.recipientId(), mode)")
				&& occurrence.contains("applyScreenTimeout(server, instance, stream, clock)"),
			"authorized projection may arrive early, but each node must retain its gameplay gate and timeout"
		);

		String normalizedWireState = wireState.replaceAll("\\s+", " ");
		int forcedPage = normalizedWireState.indexOf("ClientPageState.forcedPageActive()");
		int gameplay = normalizedWireState.indexOf("client.gui.screen() == null");
		int safeModPage = normalizedWireState.indexOf(
			"client.gui.screen() instanceof TransitioningConsoleScreen || client.gui.screen() instanceof DataDrivenPageScreen"
		);
		check(
			forcedPage >= 0 && gameplay > forcedPage && safeModPage > gameplay,
			"authoritative forced state must be classified before the one-tick null-screen gap and every close-safe mod screen"
		);
	}

	private static MessageSynchronizationAuthority authority(
		final SynchronizationMode mode,
		final long wait,
		final LateArrivalMode late,
		final SynchronizationTimeoutMode timeout
	) {
		return new MessageSynchronizationAuthority(
			new Policy(mode, wait, late, timeout),
			0L,
			Set.of(A, B)
		);
	}

	private static void checkDecision(
		final Decision decision,
		final Directive directive,
		final long anchor
	) {
		check(
			decision.directive() == directive
				&& decision.visualStartElapsedNanos().orElseThrow() == anchor,
			"unexpected synchronization decision: " + decision
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
