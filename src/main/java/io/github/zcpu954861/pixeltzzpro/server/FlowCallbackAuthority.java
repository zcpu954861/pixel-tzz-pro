package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackFailure;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackReferences;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure authoritative transactions around data-pack flow callbacks.
 *
 * <p>Automatic callback preparation commits its at-most-once marker before the caller invokes
 * {@link FlowCallbackRunner}. Callback failure can therefore block further unsafe progress and
 * record a diagnostic without rolling member completion, completion history, or role changes back.
 * Explicit retries update callback metadata and audit state, then finalize terminal flow state
 * after the last failure is cleared.
 */
public final class FlowCallbackAuthority {
	private static final int MAX_FAILURES = 128;
	private static final int MAX_ERROR_LENGTH = 4_096;
	private static final int MAX_AUDIT_SUMMARY_LENGTH = 1_024;

	private FlowCallbackAuthority() {
	}

	/**
	 * Marks the frozen {@code on_start} callback before its first automatic invocation.
	 */
	public static PrepareResult prepareOnStart(
		final WorldStateV2 state,
		final long occurredAtEpochMillis
	) {
		return prepareAutomatic(
			state,
			CallbackKind.ON_START,
			Optional.empty(),
			occurredAtEpochMillis
		);
	}

	/**
	 * Marks {@code on_player_complete} only for a member naturally completed in this instance.
	 */
	public static PrepareResult preparePlayerComplete(
		final WorldStateV2 state,
		final UUID playerId,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(playerId, "playerId");
		return prepareAutomatic(
			state,
			CallbackKind.ON_PLAYER_COMPLETE,
			Optional.of(playerId),
			occurredAtEpochMillis
		);
	}

	/**
	 * Marks {@code on_all_complete} only after the instance core state is complete.
	 */
	public static PrepareResult prepareAllComplete(
		final WorldStateV2 state,
		final long occurredAtEpochMillis
	) {
		return prepareAutomatic(
			state,
			CallbackKind.ON_ALL_COMPLETE,
			Optional.empty(),
			occurredAtEpochMillis
		);
	}

	/**
	 * Starts one explicit host retry for an existing callback failure.
	 *
	 * <p>The returned state already contains the incremented attempt and
	 * {@link AuditEventType#CALLBACK_RETRIED} event. It must be committed before invoking the
	 * returned callback.
	 */
	public static PrepareResult prepareRetry(
		final WorldStateV2 state,
		final UUID actorId,
		final CallbackKind kind,
		final Optional<UUID> playerId,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(playerId, "playerId");
		OperationCode preflight = preflight(state, occurredAtEpochMillis, true);
		if (preflight != OperationCode.SUCCESS) {
			return PrepareResult.failed(preflight, "callback retry state is not writable");
		}
		if (!validPlayerShape(kind, playerId)) {
			return PrepareResult.failed(OperationCode.TARGET_INVALID, "callback target shape is invalid");
		}
		if (
			state.host()
				.map(HostRecord::playerId)
				.filter(actorId::equals)
				.isEmpty()
		) {
			return PrepareResult.failed(OperationCode.NOT_HOST, "only the current host may retry callbacks");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (instance == null || instance.runtime().status() != FlowInstanceStatus.BLOCKED) {
			return PrepareResult.failed(OperationCode.FLOW_NOT_ACTIVE, "no callback-blocked flow is active");
		}
		Optional<CallbackFailure> matching = failure(
			instance.runtime().callbacks().failures(),
			kind,
			playerId
		);
		if (matching.isEmpty()) {
			return PrepareResult.failed(OperationCode.ACTION_UNAVAILABLE, "the callback has no recorded failure");
		}
		CallbackFailure previous = matching.orElseThrow();
		if (!reference(instance, kind).equals(Optional.of(previous.functionId()))) {
			return PrepareResult.failed(
				OperationCode.SNAPSHOT_INVALID,
				"the recorded callback no longer matches the frozen snapshot"
			);
		}
		if (!eligibleForRetry(instance, kind, playerId)) {
			return PrepareResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"the callback target is no longer eligible for retry"
			);
		}

		try {
			int attempt = Math.incrementExact(previous.attemptCount());
			long nextStateRevision = Math.incrementExact(state.stateRevision());
			CallbackFailure attempted = new CallbackFailure(
				previous.kind(),
				previous.functionId(),
				previous.playerId(),
				previous.error(),
				previous.failedAtEpochMillis(),
				attempt
			);
			CallbackState callbacks = new CallbackState(
				instance.runtime().callbacks().onStartInvoked(),
				instance.runtime().callbacks().onPlayerCompleteInvoked(),
				instance.runtime().callbacks().onAllCompleteInvoked(),
				upsertFailure(instance.runtime().callbacks().failures(), attempted)
			);
			AuditLog audit = appendAudit(
				state.audit(),
				AuditEventType.CALLBACK_RETRIED,
				occurredAtEpochMillis,
				Optional.of(actorId),
				playerId,
				instance.identity().instanceId(),
				"Retrying " + kind.getSerializedName() + " callback " + previous.functionId()
			);
			WorldStateV2 next = replaceRuntime(
				state,
				instance,
				new ForcedFlowRuntime(
					instance.runtime().status(),
					instance.runtime().members(),
					instance.runtime().completedCount(),
					instance.runtime().totalCount(),
					instance.runtime().instanceRevision(),
					instance.runtime().executionSnapshot(),
					callbacks,
					instance.runtime().blockDiagnostic(),
					occurredAtEpochMillis
				),
				audit,
				nextStateRevision
			);
			if (next.validated().result().isEmpty()) {
				return PrepareResult.failed(OperationCode.INTERNAL_ERROR, "callback retry state failed validation");
			}
			PreparedInvocation invocation = invocation(
				next,
				instance,
				kind,
				previous.functionId(),
				playerId,
				attempt,
				true,
				Optional.of(actorId)
			);
			return PrepareResult.prepared(next, invocation);
		} catch (ArithmeticException | IllegalArgumentException error) {
			return PrepareResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Records the result of a callback whose prepared marker/attempt was already committed.
	 *
	 * <p>A successful first invocation needs no second write. A successful retry removes only its
	 * matching failure. A failure preserves all core state, blocks unsafe progress, and appends a
	 * bounded diagnostic and audit event.
	 */
	public static OutcomeResult recordOutcome(
		final WorldStateV2 state,
		final PreparedInvocation prepared,
		final FlowCallbackRunner.Invocation outcome,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(prepared, "prepared");
		Objects.requireNonNull(outcome, "outcome");
		OperationCode preflight = preflight(state, occurredAtEpochMillis, false);
		if (preflight != OperationCode.SUCCESS) {
			return OutcomeResult.failed(preflight, "callback outcome state is not writable");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (
			instance == null
				|| !instance.identity().instanceId().equals(prepared.instanceId())
		) {
			return OutcomeResult.failed(OperationCode.FLOW_INSTANCE_MISMATCH, "callback instance changed");
		}
		if (state.stateRevision() != prepared.preparedStateRevision()) {
			return OutcomeResult.failed(OperationCode.STATE_REVISION_STALE, "callback preparation is stale");
		}
		if (
			!reference(instance, prepared.kind()).equals(Optional.of(prepared.functionId()))
				|| !markerPresent(instance.runtime().callbacks(), prepared.kind(), prepared.playerId())
		) {
			return OutcomeResult.failed(OperationCode.SNAPSHOT_INVALID, "callback marker or reference changed");
		}
		if (prepared.retry()) {
			Optional<CallbackFailure> current = failure(
				instance.runtime().callbacks().failures(),
				prepared.kind(),
				prepared.playerId()
			);
			if (
				current.isEmpty()
					|| current.orElseThrow().attemptCount() != prepared.attemptCount()
					|| !current.orElseThrow().functionId().equals(prepared.functionId())
			) {
				return OutcomeResult.failed(OperationCode.STATE_REVISION_STALE, "callback retry attempt changed");
			}
		} else if (
			failure(
				instance.runtime().callbacks().failures(),
				prepared.kind(),
				prepared.playerId()
			).isPresent()
		) {
			return OutcomeResult.failed(OperationCode.STATE_REVISION_STALE, "callback already has a failure");
		}

		if (outcome.success()) {
			return prepared.retry()
				? clearSuccessfulRetry(state, instance, prepared, occurredAtEpochMillis)
				: OutcomeResult.unchanged();
		}
		return recordFailure(state, instance, prepared, outcome.error(), occurredAtEpochMillis);
	}

	private static PrepareResult prepareAutomatic(
		final WorldStateV2 state,
		final CallbackKind kind,
		final Optional<UUID> playerId,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(playerId, "playerId");
		OperationCode preflight = preflight(state, occurredAtEpochMillis, false);
		if (preflight != OperationCode.SUCCESS) {
			return PrepareResult.failed(preflight, "callback state is not writable");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (instance == null) {
			return PrepareResult.failed(OperationCode.FLOW_NOT_ACTIVE, "no forced flow is active");
		}
		if (markerPresent(instance.runtime().callbacks(), kind, playerId)) {
			return PrepareResult.skipped("callback was already prepared");
		}
		Optional<Identifier> functionId = reference(instance, kind);
		if (functionId.isEmpty()) {
			return PrepareResult.skipped("the frozen flow does not register this callback");
		}
		if (!instance.runtime().callbacks().failures().isEmpty()) {
			return PrepareResult.failed(
				OperationCode.CALLBACK_FAILED,
				"an earlier callback failure must be retried first"
			);
		}
		if (!eligibleForAutomatic(state, instance, kind, playerId)) {
			return PrepareResult.failed(
				OperationCode.ACTION_UNAVAILABLE,
				"core flow state is not eligible for this callback"
			);
		}

		try {
			long nextStateRevision = Math.incrementExact(state.stateRevision());
			CallbackState callbacks = mark(
				instance.runtime().callbacks(),
				kind,
				playerId
			);
			WorldStateV2 next = replaceRuntime(
				state,
				instance,
				new ForcedFlowRuntime(
					instance.runtime().status(),
					instance.runtime().members(),
					instance.runtime().completedCount(),
					instance.runtime().totalCount(),
					instance.runtime().instanceRevision(),
					instance.runtime().executionSnapshot(),
					callbacks,
					instance.runtime().blockDiagnostic(),
					occurredAtEpochMillis
				),
				state.audit(),
				nextStateRevision
			);
			if (next.validated().result().isEmpty()) {
				return PrepareResult.failed(OperationCode.INTERNAL_ERROR, "callback marker failed validation");
			}
			return PrepareResult.prepared(
				next,
				invocation(
					next,
					instance,
					kind,
					functionId.orElseThrow(),
					playerId,
					1,
					false,
					Optional.empty()
				)
			);
		} catch (ArithmeticException | IllegalArgumentException error) {
			return PrepareResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	private static boolean eligibleForAutomatic(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		return switch (kind) {
			case ON_START ->
				playerId.isEmpty()
					&& instance.runtime().status() == FlowInstanceStatus.ACTIVE;
			case ON_PLAYER_COMPLETE ->
				playerId.filter(id -> naturallyCompleted(state, instance, id)).isPresent()
					&& (
						instance.runtime().status() == FlowInstanceStatus.ACTIVE
							|| instance.runtime().status() == FlowInstanceStatus.COMPLETED
					);
			case ON_ALL_COMPLETE ->
				playerId.isEmpty()
					&& instance.runtime().status() == FlowInstanceStatus.COMPLETED
					&& instance.runtime().totalCount() > 0
					&& instance.runtime().completedCount() == instance.runtime().totalCount();
		};
	}

	private static boolean eligibleForRetry(
		final ForcedFlowInstance instance,
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		if (instance.runtime().status() != FlowInstanceStatus.BLOCKED) {
			return false;
		}
		return switch (kind) {
			case ON_START -> playerId.isEmpty();
			case ON_PLAYER_COMPLETE ->
				playerId
					.map(instance.runtime().members()::get)
					.filter(member -> member.status() == FlowMemberStatus.COMPLETED)
					.isPresent();
			case ON_ALL_COMPLETE ->
				playerId.isEmpty()
					&& instance.runtime().totalCount() > 0
					&& instance.runtime().completedCount() == instance.runtime().totalCount();
		};
	}

	private static boolean naturallyCompleted(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final UUID playerId
	) {
		FlowMemberState member = instance.runtime().members().get(playerId);
		if (member == null || member.status() != FlowMemberStatus.COMPLETED) {
			return false;
		}
		return state.completionHistory()
			.stream()
			.anyMatch(completion -> sameCompletion(completion, instance, playerId));
	}

	private static boolean sameCompletion(
		final CompletionRecord completion,
		final ForcedFlowInstance instance,
		final UUID playerId
	) {
		return completion.playerId().equals(playerId)
			&& completion.instanceId().equals(instance.identity().instanceId())
			&& completion.flowId().equals(instance.identity().flowId())
			&& completion.flowVersion() == instance.identity().flowVersion();
	}

	private static PreparedInvocation invocation(
		final WorldStateV2 preparedState,
		final ForcedFlowInstance instance,
		final CallbackKind kind,
		final Identifier functionId,
		final Optional<UUID> playerId,
		final int attemptCount,
		final boolean retry,
		final Optional<UUID> retryActorId
	) {
		Optional<String> playerName = playerId.map(
			id -> instance.runtime().members().get(id).lockedName()
		);
		FlowCallbackRunner.Context context = new FlowCallbackRunner.Context(
			instance.identity().gameId(),
			instance.identity().flowId(),
			instance.identity().flowVersion(),
			instance.identity().instanceId(),
			playerId,
			playerName,
			instance.runtime().completedCount(),
			instance.runtime().totalCount(),
			preparedState.stateRevision()
		);
		return new PreparedInvocation(
			instance.identity().instanceId(),
			kind,
			functionId,
			playerId,
			context,
			attemptCount,
			retry,
			retryActorId,
			preparedState.stateRevision()
		);
	}

	private static OutcomeResult recordFailure(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final PreparedInvocation prepared,
		final String error,
		final long occurredAtEpochMillis
	) {
		try {
			String boundedError = bounded(error, MAX_ERROR_LENGTH, "unknown callback failure");
			CallbackFailure failure = new CallbackFailure(
				prepared.kind(),
				prepared.functionId(),
				prepared.playerId(),
				boundedError,
				occurredAtEpochMillis,
				prepared.attemptCount()
			);
			CallbackState callbacks = new CallbackState(
				instance.runtime().callbacks().onStartInvoked(),
				instance.runtime().callbacks().onPlayerCompleteInvoked(),
				instance.runtime().callbacks().onAllCompleteInvoked(),
				upsertFailure(instance.runtime().callbacks().failures(), failure)
			);
			AuditLog audit = appendAudit(
				state.audit(),
				AuditEventType.CALLBACK_FAILED,
				occurredAtEpochMillis,
				prepared.retryActorId(),
				prepared.playerId(),
				instance.identity().instanceId(),
				"Callback " + prepared.kind().getSerializedName() + " failed: " + boundedError
			);
			WorldStateV2 next = replaceRuntime(
				state,
				instance,
				new ForcedFlowRuntime(
					FlowInstanceStatus.BLOCKED,
					instance.runtime().members(),
					instance.runtime().completedCount(),
					instance.runtime().totalCount(),
					instance.runtime().instanceRevision(),
					instance.runtime().executionSnapshot(),
					callbacks,
					Optional.of(
						new WorldStateV2.BlockDiagnostic(
							OperationCode.CALLBACK_FAILED.serializedName(),
							boundedError,
							occurredAtEpochMillis,
							prepared.playerId(),
							Optional.of(prepared.functionId())
						)
					),
					occurredAtEpochMillis
				),
				audit,
				Math.incrementExact(state.stateRevision())
			);
			if (next.validated().result().isEmpty()) {
				return OutcomeResult.failed(OperationCode.INTERNAL_ERROR, "callback failure state failed validation");
			}
			return OutcomeResult.changed(OperationCode.CALLBACK_FAILED, next, boundedError);
		} catch (ArithmeticException | IllegalArgumentException exception) {
			return OutcomeResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(exception));
		}
	}

	private static OutcomeResult clearSuccessfulRetry(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final PreparedInvocation prepared,
		final long occurredAtEpochMillis
	) {
		try {
			List<CallbackFailure> remaining = instance.runtime().callbacks().failures()
				.stream()
				.filter(candidate -> !sameFailure(candidate, prepared.kind(), prepared.playerId()))
				.toList();
			CallbackState callbacks = new CallbackState(
				instance.runtime().callbacks().onStartInvoked(),
				instance.runtime().callbacks().onPlayerCompleteInvoked(),
				instance.runtime().callbacks().onAllCompleteInvoked(),
				remaining
			);
			FlowInstanceStatus status = remaining.isEmpty()
				? coreStatus(instance.runtime())
				: FlowInstanceStatus.BLOCKED;
			boolean terminal = status == FlowInstanceStatus.COMPLETED
				|| status == FlowInstanceStatus.CANCELED;
			Optional<WorldStateV2.BlockDiagnostic> diagnostic = remaining.isEmpty()
				? Optional.empty()
				: Optional.of(diagnostic(remaining.getLast()));
			WorldStateV2 next = replaceRuntime(
				state,
				instance,
				new ForcedFlowRuntime(
					status,
					instance.runtime().members(),
					instance.runtime().completedCount(),
					instance.runtime().totalCount(),
					instance.runtime().instanceRevision(),
					terminal
						? instance.runtime().executionSnapshot().compactedForTerminalState()
						: instance.runtime().executionSnapshot(),
					callbacks,
					diagnostic,
					occurredAtEpochMillis
				),
				state.audit(),
				Math.incrementExact(state.stateRevision())
			);
			if (terminal) {
				next = next.withPlayers(
					FlowRosterAuthority.clearParticipations(
						next.players(),
						instance.identity().instanceId()
					)
				);
			}
			if (next.validated().result().isEmpty()) {
				return OutcomeResult.failed(OperationCode.INTERNAL_ERROR, "callback recovery state failed validation");
			}
			return OutcomeResult.changed(OperationCode.SUCCESS, next, "callback retry succeeded");
		} catch (ArithmeticException | IllegalArgumentException error) {
			return OutcomeResult.failed(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	private static FlowInstanceStatus coreStatus(final ForcedFlowRuntime runtime) {
		if (runtime.totalCount() == 0) {
			return FlowInstanceStatus.CANCELED;
		}
		return runtime.completedCount() == runtime.totalCount()
			? FlowInstanceStatus.COMPLETED
			: FlowInstanceStatus.ACTIVE;
	}

	private static WorldStateV2.BlockDiagnostic diagnostic(final CallbackFailure failure) {
		return new WorldStateV2.BlockDiagnostic(
			OperationCode.CALLBACK_FAILED.serializedName(),
			failure.error(),
			failure.failedAtEpochMillis(),
			failure.playerId(),
			Optional.of(failure.functionId())
		);
	}

	private static CallbackState mark(
		final CallbackState callbacks,
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		boolean onStart = callbacks.onStartInvoked();
		Set<UUID> players = callbacks.onPlayerCompleteInvoked();
		boolean onAll = callbacks.onAllCompleteInvoked();
		switch (kind) {
			case ON_START -> onStart = true;
			case ON_PLAYER_COMPLETE -> {
				Set<UUID> next = new LinkedHashSet<>(players);
				next.add(playerId.orElseThrow());
				players = next;
			}
			case ON_ALL_COMPLETE -> onAll = true;
		}
		return new CallbackState(onStart, players, onAll, callbacks.failures());
	}

	private static boolean markerPresent(
		final CallbackState callbacks,
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		if (!validPlayerShape(kind, playerId)) {
			return false;
		}
		return switch (kind) {
			case ON_START -> callbacks.onStartInvoked();
			case ON_PLAYER_COMPLETE ->
				callbacks.onPlayerCompleteInvoked().contains(playerId.orElseThrow());
			case ON_ALL_COMPLETE -> callbacks.onAllCompleteInvoked();
		};
	}

	private static boolean validPlayerShape(
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		return (kind == CallbackKind.ON_PLAYER_COMPLETE) == playerId.isPresent();
	}

	private static Optional<Identifier> reference(
		final ForcedFlowInstance instance,
		final CallbackKind kind
	) {
		CallbackReferences references = instance.runtime()
			.executionSnapshot()
			.callbackReferences();
		return switch (kind) {
			case ON_START -> references.onStart();
			case ON_PLAYER_COMPLETE -> references.onPlayerComplete();
			case ON_ALL_COMPLETE -> references.onAllComplete();
		};
	}

	private static Optional<CallbackFailure> failure(
		final List<CallbackFailure> failures,
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		return failures.stream()
			.filter(candidate -> sameFailure(candidate, kind, playerId))
			.reduce((first, second) -> second);
	}

	private static boolean sameFailure(
		final CallbackFailure failure,
		final CallbackKind kind,
		final Optional<UUID> playerId
	) {
		return failure.kind() == kind && failure.playerId().equals(playerId);
	}

	private static List<CallbackFailure> upsertFailure(
		final List<CallbackFailure> failures,
		final CallbackFailure failure
	) {
		List<CallbackFailure> next = new ArrayList<>(Math.min(MAX_FAILURES, failures.size() + 1));
		for (CallbackFailure candidate : failures) {
			if (!sameFailure(candidate, failure.kind(), failure.playerId())) {
				next.add(candidate);
			}
		}
		next.add(failure);
		if (next.size() > MAX_FAILURES) {
			next = new ArrayList<>(next.subList(next.size() - MAX_FAILURES, next.size()));
		}
		return List.copyOf(next);
	}

	private static AuditLog appendAudit(
		final AuditLog audit,
		final AuditEventType type,
		final long occurredAtEpochMillis,
		final Optional<UUID> actorId,
		final Optional<UUID> targetPlayerId,
		final UUID flowInstanceId,
		final String summary
	) {
		return audit.append(
			new AuditEvent(
				audit.totalEvents(),
				type,
				occurredAtEpochMillis,
				actorId,
				targetPlayerId,
				Optional.of(flowInstanceId),
				bounded(summary, MAX_AUDIT_SUMMARY_LENGTH, "Callback event")
			)
		);
	}

	private static WorldStateV2 replaceRuntime(
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final ForcedFlowRuntime runtime,
		final AuditLog audit,
		final long stateRevision
	) {
		return state
			.withActiveForcedFlow(
				Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
			)
			.withAudit(audit)
			.withStateRevision(stateRevision);
	}

	private static OperationCode preflight(
		final WorldStateV2 state,
		final long occurredAtEpochMillis,
		final boolean writesAudit
	) {
		if (state.validated().result().isEmpty()) {
			return OperationCode.SCHEMA_BLOCKED;
		}
		if (
			occurredAtEpochMillis < 0L
				|| state.stateRevision() == Long.MAX_VALUE
				|| (writesAudit && state.audit().totalEvents() == Long.MAX_VALUE)
		) {
			return OperationCode.INTERNAL_ERROR;
		}
		return OperationCode.SUCCESS;
	}

	private static String bounded(
		final String value,
		final int maximumLength,
		final String fallback
	) {
		String bounded = Objects.requireNonNull(value, "value");
		if (bounded.isBlank()) {
			bounded = fallback;
		}
		return bounded.length() <= maximumLength
			? bounded
			: bounded.substring(0, maximumLength - 3) + "...";
	}

	private static String safeMessage(final Throwable error) {
		String message = error.getMessage();
		return message == null || message.isBlank()
			? error.getClass().getSimpleName()
			: message;
	}

	/**
	 * One callback invocation whose marker or retry attempt is already in durable candidate state.
	 */
	public record PreparedInvocation(
		UUID instanceId,
		CallbackKind kind,
		Identifier functionId,
		Optional<UUID> playerId,
		FlowCallbackRunner.Context context,
		int attemptCount,
		boolean retry,
		Optional<UUID> retryActorId,
		long preparedStateRevision
	) {
		public PreparedInvocation {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			kind = Objects.requireNonNull(kind, "kind");
			functionId = Objects.requireNonNull(functionId, "functionId");
			playerId = Objects.requireNonNull(playerId, "playerId");
			context = Objects.requireNonNull(context, "context");
			retryActorId = Objects.requireNonNull(retryActorId, "retryActorId");
			if (
				!validPlayerShape(kind, playerId)
					|| attemptCount <= 0
					|| retry != retryActorId.isPresent()
					|| preparedStateRevision < 0L
					|| !instanceId.equals(context.instanceId())
					|| !playerId.equals(context.playerId())
					|| preparedStateRevision != context.stateRevision()
			) {
				throw new IllegalArgumentException("prepared callback identity is invalid");
			}
		}
	}

	public record PrepareResult(
		OperationCode code,
		String message,
		Optional<WorldStateV2> nextState,
		Optional<PreparedInvocation> invocation
	) {
		public PrepareResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			invocation = Objects.requireNonNull(invocation, "invocation");
			if (
				message.isBlank()
					|| nextState.isPresent() != invocation.isPresent()
					|| (code != OperationCode.SUCCESS && nextState.isPresent())
			) {
				throw new IllegalArgumentException("callback preparation result is inconsistent");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		public boolean prepared() {
			return this.invocation.isPresent();
		}

		private static PrepareResult prepared(
			final WorldStateV2 nextState,
			final PreparedInvocation invocation
		) {
			return new PrepareResult(
				OperationCode.SUCCESS,
				"callback prepared",
				Optional.of(nextState),
				Optional.of(invocation)
			);
		}

		private static PrepareResult skipped(final String message) {
			return new PrepareResult(
				OperationCode.SUCCESS,
				message,
				Optional.empty(),
				Optional.empty()
			);
		}

		private static PrepareResult failed(
			final OperationCode code,
			final String message
		) {
			if (code == OperationCode.SUCCESS) {
				throw new IllegalArgumentException("failed callback preparation requires an error code");
			}
			return new PrepareResult(code, bounded(message, MAX_ERROR_LENGTH, "callback preparation failed"), Optional.empty(), Optional.empty());
		}
	}

	public record OutcomeResult(
		OperationCode code,
		String message,
		Optional<WorldStateV2> nextState
	) {
		public OutcomeResult {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (message.isBlank() || (code != OperationCode.SUCCESS && code != OperationCode.CALLBACK_FAILED && nextState.isPresent())) {
				throw new IllegalArgumentException("callback outcome result is inconsistent");
			}
		}

		public boolean invocationSucceeded() {
			return this.code == OperationCode.SUCCESS;
		}

		private static OutcomeResult unchanged() {
			return new OutcomeResult(OperationCode.SUCCESS, "callback succeeded", Optional.empty());
		}

		private static OutcomeResult changed(
			final OperationCode code,
			final WorldStateV2 nextState,
			final String message
		) {
			return new OutcomeResult(code, bounded(message, MAX_ERROR_LENGTH, "callback outcome recorded"), Optional.of(nextState));
		}

		private static OutcomeResult failed(
			final OperationCode code,
			final String message
		) {
			if (code == OperationCode.SUCCESS || code == OperationCode.CALLBACK_FAILED) {
				throw new IllegalArgumentException("failed callback outcome requires an authority error code");
			}
			return new OutcomeResult(code, bounded(message, MAX_ERROR_LENGTH, "callback outcome rejected"), Optional.empty());
		}
	}
}
