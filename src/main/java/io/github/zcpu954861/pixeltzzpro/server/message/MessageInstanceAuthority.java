package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EmptyAudienceMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OnCueEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.Ledger;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.PreparedCallback;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.AudienceResolution;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.AudienceResolver;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.ParameterResolution;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.ParameterResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Pure server-authoritative V3B message-instance transactions.
 *
 * <p>This class never reads Minecraft state, wall-clock time, chat history or network state. The
 * caller supplies one immutable definition snapshot, resolved canonical parameters, a resolved
 * audience snapshot and a monotonic clock sample. Every returned projection is safe to publish
 * only after the matching {@link RuntimeState} has been committed.
 */
public final class MessageInstanceAuthority {
	public static final int HARD_MAX_INSTANCES = 512;
	public static final int HARD_MAX_QUEUE = 1_024;
	public static final int HARD_MAX_COOLDOWNS = 2_048;
	public static final int HARD_MAX_REFRESH_KEYS = 1_024;
	public static final int HARD_MAX_PARAMETERS = 128;
	public static final int HARD_MAX_TARGETS = 512;
	public static final int HARD_MAX_TOTAL_NODE_TARGETS = 4_096;
	public static final int HARD_MAX_OCCURRENCES = 4_096;
	private static final int MAX_ARGUMENT_LENGTH = 4_096;
	private static final int MAX_TOTAL_ARGUMENT_CHARACTERS = 65_536;
	private static final int MAX_REASON_LENGTH = 1_024;

	private MessageInstanceAuthority() {
	}

	public enum PlayDisposition {
		REJECTED,
		IGNORED,
		CREATED,
		REFRESHED,
		QUEUED
	}

	public enum InstanceStatus {
		ACTIVE,
		PAUSED,
		COMPLETING,
		CANCELLING,
		COMPLETED,
		CANCELLED;

		public boolean active() {
			return this == ACTIVE
				|| this == PAUSED
				|| this == COMPLETING
				|| this == CANCELLING;
		}

		public boolean terminal() {
			return this == COMPLETED || this == CANCELLED;
		}
	}

	public enum ProjectionKind {
		CREATE,
		REFRESH,
		REPLACE,
		NODE_DUE,
		PAUSE,
		RESUME,
		TARGET_COMPLETE,
		COMPLETE,
		CANCEL,
		/** Natural authoritative completion; the client may drain its authorized local queue. */
		FINISH
	}

	public enum ControlAction {
		PAUSE,
		RESUME,
		COMPLETE,
		CANCEL
	}

	public record ClockSample(long gameTick, long presentationNanos) {
		public ClockSample {
			if (
				gameTick < 0L
					|| gameTick > Long.MAX_VALUE / TimeSpan.NANOS_PER_TICK
					|| presentationNanos < 0L
			) {
				throw new IllegalArgumentException("message clock sample is outside its safe range");
			}
		}

		public long value(final ClockMode mode) {
			Objects.requireNonNull(mode, "mode");
			return mode == ClockMode.GAME_TIME
				? Math.multiplyExact(this.gameTick, TimeSpan.NANOS_PER_TICK)
				: this.presentationNanos;
		}
	}

	/**
	 * Trusted invocation metadata. Minecraft selectors and command arguments remain outside this
	 * core and are copied before a resolver may inspect them.
	 */
	public record InvocationContext(
		UUID requestedInstanceId,
		boolean authorized,
		Optional<UUID> invokerId,
		Set<UUID> callTargets,
		Optional<Identifier> gameId,
		Optional<Identifier> phaseId,
		Optional<Identifier> taskId,
		Map<String, String> arguments,
		long definitionGeneration,
		boolean bypassContext,
		boolean externallyTimed
	) {
		public InvocationContext {
			requestedInstanceId = Objects.requireNonNull(
				requestedInstanceId,
				"requestedInstanceId"
			);
			invokerId = Objects.requireNonNull(invokerId, "invokerId");
			callTargets = boundedUuidSet(callTargets, HARD_MAX_TARGETS, "call targets");
			gameId = Objects.requireNonNull(gameId, "gameId");
			phaseId = Objects.requireNonNull(phaseId, "phaseId");
			taskId = Objects.requireNonNull(taskId, "taskId");
			arguments = boundedStringMap(arguments, "invocation arguments");
			if (definitionGeneration < 0L) {
				throw new IllegalArgumentException("definition generation cannot be negative");
			}
		}

		/** Compatibility constructor for ordinary invocations that enforce registered context. */
		public InvocationContext(
			final UUID requestedInstanceId,
			final boolean authorized,
			final Optional<UUID> invokerId,
			final Set<UUID> callTargets,
			final Optional<Identifier> gameId,
			final Optional<Identifier> phaseId,
			final Optional<Identifier> taskId,
			final Map<String, String> arguments,
			final long definitionGeneration,
			final boolean externallyTimed
		) {
			this(
				requestedInstanceId,
				authorized,
				invokerId,
				callTargets,
				gameId,
				phaseId,
				taskId,
				arguments,
				definitionGeneration,
				false,
				externallyTimed
			);
		}
	}

	public record DedupeKey(
		Identifier cueId,
		Set<UUID> targets,
		Map<String, String> parameters
	) {
		public DedupeKey {
			cueId = Objects.requireNonNull(cueId, "cueId");
			targets = boundedUuidSet(targets, HARD_MAX_TARGETS, "dedupe targets");
			parameters = boundedStringMap(parameters, "dedupe parameters");
		}
	}

	/**
	 * Stable refresh address. It is built only from the registered refresh-key parameter and never
	 * discovered by scanning chat history.
	 */
	public record RefreshKey(Identifier cueId, String parameter, String canonicalValue) {
		public RefreshKey {
			cueId = Objects.requireNonNull(cueId, "cueId");
			parameter = boundedToken(parameter, "refresh parameter");
			canonicalValue = boundedValue(canonicalValue, "refresh value");
		}
	}

	public record EventKey(CallbackTrigger trigger, Optional<UUID> targetId) {
		public EventKey {
			trigger = Objects.requireNonNull(trigger, "trigger");
			targetId = Objects.requireNonNull(targetId, "targetId");
			if ((trigger == CallbackTrigger.TARGET_COMPLETE) != targetId.isPresent()) {
				throw new IllegalArgumentException(
					"only target-complete events carry a target"
				);
			}
		}
	}

	public record OccurrenceKey(
		String nodeId,
		int occurrence,
		CallbackTrigger trigger,
		Optional<UUID> targetId
	) {
		public OccurrenceKey {
			nodeId = boundedToken(nodeId, "node id");
			trigger = Objects.requireNonNull(trigger, "trigger");
			targetId = Objects.requireNonNull(targetId, "targetId");
			if (occurrence < 0) {
				throw new IllegalArgumentException("node occurrence cannot be negative");
			}
			if ((trigger == CallbackTrigger.TARGET_COMPLETE) != targetId.isPresent()) {
				throw new IllegalArgumentException(
					"only target-complete occurrences carry a target"
				);
			}
		}
	}

	public record ProjectionEvent(
		ProjectionKind kind,
		UUID instanceId,
		long cycle,
		Optional<String> nodeId,
		int occurrence,
		Set<UUID> recipients,
		Optional<UUID> targetId,
		Optional<UUID> replacedInstanceId,
		String reason
	) {
		public ProjectionEvent {
			kind = Objects.requireNonNull(kind, "kind");
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			recipients = boundedUuidSet(recipients, HARD_MAX_TARGETS, "projection recipients");
			targetId = Objects.requireNonNull(targetId, "targetId");
			replacedInstanceId = Objects.requireNonNull(
				replacedInstanceId,
				"replacedInstanceId"
			);
			reason = boundedReason(reason);
			if (cycle < 0L || occurrence < 0) {
				throw new IllegalArgumentException(
					"projection cycle and occurrence cannot be negative"
				);
			}
			if ((kind == ProjectionKind.NODE_DUE) != nodeId.isPresent()) {
				throw new IllegalArgumentException(
					"only node-due projections carry a node id"
				);
			}
			if ((kind == ProjectionKind.REPLACE) != replacedInstanceId.isPresent()) {
				throw new IllegalArgumentException(
					"only replace projections carry a replaced instance"
				);
			}
		}
	}

	public record CooldownStamp(ClockMode clock, long expiresAtNanos) {
		public CooldownStamp {
			clock = Objects.requireNonNull(clock, "clock");
			if (expiresAtNanos < 0L) {
				throw new IllegalArgumentException("cooldown expiry cannot be negative");
			}
		}
	}

	public record QueuedInvocation(
		UUID instanceId,
		MessageCueDefinition cue,
		InvocationContext context,
		Map<String, String> canonicalParameters,
		Map<String, TimeSpan> authoritativeNodeDurations,
		Set<UUID> cueTargets,
		Map<String, Set<UUID>> nodeTargets,
		DedupeKey dedupeKey,
		Optional<RefreshKey> refreshKey,
		ClockMode clockMode,
		long enqueuedAtNanos
	) {
		public QueuedInvocation {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			cue = Objects.requireNonNull(cue, "cue");
			context = Objects.requireNonNull(context, "context");
			canonicalParameters = boundedStringMap(
				canonicalParameters,
				"queued canonical parameters"
			);
			authoritativeNodeDurations = boundedDurationMap(
				authoritativeNodeDurations,
				cue
			);
			cueTargets = boundedUuidSet(cueTargets, HARD_MAX_TARGETS, "queued cue targets");
			nodeTargets = boundedNodeTargets(nodeTargets, cue);
			dedupeKey = Objects.requireNonNull(dedupeKey, "dedupeKey");
			refreshKey = Objects.requireNonNull(refreshKey, "refreshKey");
			clockMode = Objects.requireNonNull(clockMode, "clockMode");
			if (enqueuedAtNanos < 0L) {
				throw new IllegalArgumentException("queue timestamp cannot be negative");
			}
		}
	}

	public record MessageInstance(
		UUID instanceId,
		MessageCueDefinition cue,
		InvocationContext context,
		Map<String, String> canonicalParameters,
		Map<String, TimeSpan> authoritativeNodeDurations,
		Set<UUID> cueTargets,
		Map<String, Set<UUID>> nodeTargets,
		DedupeKey dedupeKey,
		Optional<RefreshKey> refreshKey,
		ClockMode clockMode,
		long cycle,
		InstanceStatus status,
		Optional<InstanceStatus> resumeStatus,
		long anchorClockNanos,
		long accumulatedElapsedNanos,
		Set<OccurrenceKey> emittedOccurrences,
		Set<UUID> completedTargets,
		Map<EventKey, Long> eventOffsets,
		boolean forcedCompletion,
		long instanceRevision
	) {
		public MessageInstance {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			cue = Objects.requireNonNull(cue, "cue");
			context = Objects.requireNonNull(context, "context");
			canonicalParameters = boundedStringMap(
				canonicalParameters,
				"instance canonical parameters"
			);
			authoritativeNodeDurations = boundedDurationMap(
				authoritativeNodeDurations,
				cue
			);
			cueTargets = boundedUuidSet(cueTargets, HARD_MAX_TARGETS, "instance cue targets");
			nodeTargets = boundedNodeTargets(nodeTargets, cue);
			dedupeKey = Objects.requireNonNull(dedupeKey, "dedupeKey");
			refreshKey = Objects.requireNonNull(refreshKey, "refreshKey");
			clockMode = Objects.requireNonNull(clockMode, "clockMode");
			status = Objects.requireNonNull(status, "status");
			resumeStatus = Objects.requireNonNull(resumeStatus, "resumeStatus");
			emittedOccurrences = Set.copyOf(
				Objects.requireNonNull(emittedOccurrences, "emittedOccurrences")
			);
			completedTargets = boundedUuidSet(
				completedTargets,
				HARD_MAX_TARGETS,
				"completed targets"
			);
			eventOffsets = immutableEventMap(eventOffsets);
			if (
				cycle < 0L
					|| anchorClockNanos < 0L
					|| accumulatedElapsedNanos < 0L
					|| instanceRevision < 0L
					|| emittedOccurrences.size() > HARD_MAX_OCCURRENCES
					|| !cueTargets.containsAll(completedTargets)
			) {
				throw new IllegalArgumentException("message instance contains invalid bounded state");
			}
			if (
				(status == InstanceStatus.PAUSED) != resumeStatus.isPresent()
					|| resumeStatus.filter(candidate -> candidate == InstanceStatus.PAUSED).isPresent()
			) {
				throw new IllegalArgumentException("message instance pause state is inconsistent");
			}
		}

		public long elapsedAt(final ClockSample sample) {
			Objects.requireNonNull(sample, "sample");
			if (this.status == InstanceStatus.PAUSED || this.status.terminal()) {
				return this.accumulatedElapsedNanos;
			}
			long now = sample.value(this.clockMode);
			if (now < this.anchorClockNanos) {
				throw new IllegalArgumentException("message clock moved backwards");
			}
			return Math.addExact(
				this.accumulatedElapsedNanos,
				now - this.anchorClockNanos
			);
		}
	}

	public record RuntimeState(
		long revision,
		Map<UUID, MessageInstance> instances,
		List<QueuedInvocation> queue,
		Map<DedupeKey, UUID> activeDedupe,
		Map<RefreshKey, UUID> refreshIndex,
		Map<DedupeKey, CooldownStamp> cooldowns,
		Ledger callbackLedger
	) {
		public RuntimeState {
			if (revision < 0L) {
				throw new IllegalArgumentException("message runtime revision cannot be negative");
			}
			instances = immutableMap(instances, "instances");
			queue = List.copyOf(Objects.requireNonNull(queue, "queue"));
			activeDedupe = immutableMap(activeDedupe, "activeDedupe");
			refreshIndex = immutableMap(refreshIndex, "refreshIndex");
			cooldowns = immutableMap(cooldowns, "cooldowns");
			callbackLedger = Objects.requireNonNull(callbackLedger, "callbackLedger");
			if (
				instances.size() > HARD_MAX_INSTANCES
					|| queue.size() > HARD_MAX_QUEUE
					|| cooldowns.size() > HARD_MAX_COOLDOWNS
					|| refreshIndex.size() > HARD_MAX_REFRESH_KEYS
			) {
				throw new IllegalArgumentException("message runtime state exceeds a hard bound");
			}
			Set<UUID> reserved = new LinkedHashSet<>(instances.keySet());
			for (QueuedInvocation invocation : queue) {
				if (invocation == null || !reserved.add(invocation.instanceId())) {
					throw new IllegalArgumentException(
						"message queue contains a null or duplicate reserved instance id"
					);
				}
			}
			for (Map.Entry<DedupeKey, UUID> entry : activeDedupe.entrySet()) {
				MessageInstance instance = instances.get(entry.getValue());
				if (
					instance == null
						|| !instance.status().active()
						|| !instance.dedupeKey().equals(entry.getKey())
				) {
					throw new IllegalArgumentException("active dedupe index is stale");
				}
			}
			for (Map.Entry<RefreshKey, UUID> entry : refreshIndex.entrySet()) {
				MessageInstance instance = instances.get(entry.getValue());
				if (
					instance == null
						|| instance.refreshKey().filter(entry.getKey()::equals).isEmpty()
				) {
					throw new IllegalArgumentException("refresh index is stale");
				}
			}
		}

		public static RuntimeState empty(final int callbackCapacity) {
			return new RuntimeState(
				0L,
				Map.of(),
				List.of(),
				Map.of(),
				Map.of(),
				Map.of(),
				Ledger.empty(callbackCapacity)
			);
		}
	}

	public sealed interface ControlSelector permits InstanceSelector,
		CueSelector,
		GroupSelector,
		TargetSelector {
		boolean matches(MessageInstance instance);
	}

	public record InstanceSelector(UUID instanceId) implements ControlSelector {
		public InstanceSelector {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
		}

		@Override
		public boolean matches(final MessageInstance instance) {
			return this.instanceId.equals(instance.instanceId());
		}
	}

	public record CueSelector(Identifier cueId) implements ControlSelector {
		public CueSelector {
			cueId = Objects.requireNonNull(cueId, "cueId");
		}

		@Override
		public boolean matches(final MessageInstance instance) {
			return instance.status().active()
				&& this.cueId.equals(instance.cue().id());
		}
	}

	public record GroupSelector(String group) implements ControlSelector {
		public GroupSelector {
			group = boundedToken(group, "control group");
		}

		@Override
		public boolean matches(final MessageInstance instance) {
			return instance.cue().policies().controlGroups().contains(this.group)
				|| instance.cue().policies().concurrency().groups().contains(this.group);
		}
	}

	public record TargetSelector(UUID targetId) implements ControlSelector {
		public TargetSelector {
			targetId = Objects.requireNonNull(targetId, "targetId");
		}

		@Override
		public boolean matches(final MessageInstance instance) {
			return instance.cueTargets().contains(this.targetId);
		}
	}

	public record PlayResult(
		PlayDisposition disposition,
		String message,
		RuntimeState nextState,
		Optional<UUID> instanceId,
		List<ProjectionEvent> projections,
		List<PreparedCallback> callbacks
	) {
		public PlayResult {
			disposition = Objects.requireNonNull(disposition, "disposition");
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			projections = List.copyOf(Objects.requireNonNull(projections, "projections"));
			callbacks = List.copyOf(Objects.requireNonNull(callbacks, "callbacks"));
			if (
				(disposition == PlayDisposition.REJECTED
						|| disposition == PlayDisposition.IGNORED)
					&& (
						instanceId.isPresent()
							|| !projections.isEmpty()
							|| !callbacks.isEmpty()
					)
			) {
				throw new IllegalArgumentException(
					"rejected or ignored play result cannot expose new work"
				);
			}
		}
	}

	public record AdvanceResult(
		boolean accepted,
		boolean changed,
		String message,
		RuntimeState nextState,
		List<ProjectionEvent> projections,
		List<PreparedCallback> callbacks
	) {
		public AdvanceResult {
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			projections = List.copyOf(Objects.requireNonNull(projections, "projections"));
			callbacks = List.copyOf(Objects.requireNonNull(callbacks, "callbacks"));
			if (!accepted && (changed || !projections.isEmpty() || !callbacks.isEmpty())) {
				throw new IllegalArgumentException(
					"rejected advancement cannot expose partial work"
				);
			}
		}
	}

	public record ControlResult(
		boolean accepted,
		int matched,
		int changed,
		String message,
		RuntimeState nextState,
		List<ProjectionEvent> projections,
		List<PreparedCallback> callbacks
	) {
		public ControlResult {
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			projections = List.copyOf(Objects.requireNonNull(projections, "projections"));
			callbacks = List.copyOf(Objects.requireNonNull(callbacks, "callbacks"));
			if (
				matched < 0
					|| changed < 0
					|| changed > matched
					|| (!accepted && (changed > 0 || !projections.isEmpty() || !callbacks.isEmpty()))
			) {
				throw new IllegalArgumentException("control result counters are inconsistent");
			}
		}
	}

	public record CallbackOutcomeResult(
		boolean accepted,
		boolean changed,
		String message,
		RuntimeState nextState
	) {
		public CallbackOutcomeResult {
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (!accepted && changed) {
				throw new IllegalArgumentException(
					"rejected callback outcome cannot change runtime state"
				);
			}
		}
	}

	public record ReauthorizationResult(
		boolean accepted,
		boolean changed,
		String message,
		RuntimeState nextState,
		List<ProjectionEvent> projections,
		List<PreparedCallback> callbacks
	) {
		public ReauthorizationResult {
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			projections = List.copyOf(projections);
			callbacks = List.copyOf(callbacks);
			if (!accepted && changed) {
				throw new IllegalArgumentException(
					"rejected reauthorization cannot change runtime state"
				);
			}
		}
	}

	/**
	 * Atomic removal result for invocations that have reserved an id but have not started.
	 *
	 * <p>The transaction deliberately has no projection or callback channel: removing queued work
	 * cannot prepare a callback, advance another instance or make the removed invocation visible.
	 */
	public record QueueRemovalResult(
		boolean accepted,
		String message,
		RuntimeState nextState,
		Set<UUID> removedInstanceIds
	) {
		public QueueRemovalResult {
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			removedInstanceIds = boundedUuidSet(
				removedInstanceIds,
				HARD_MAX_QUEUE,
				"removed queued instance ids"
			);
			if (!accepted && !removedInstanceIds.isEmpty()) {
				throw new IllegalArgumentException(
					"rejected queue removal cannot expose removed instance ids"
				);
			}
		}

		public boolean changed() {
			return !this.removedInstanceIds.isEmpty();
		}
	}

	/**
	 * Atomic compaction result for completed or cancelled instances.
	 *
	 * <p>Callback-ledger entries are intentionally not part of the retired set. They remain in the
	 * returned state as durable proof that a world-changing callback must not replay.
	 */
	public record RetirementResult(
		boolean accepted,
		String message,
		RuntimeState nextState,
		Set<UUID> retiredInstanceIds
	) {
		public RetirementResult {
			message = boundedReason(message);
			nextState = Objects.requireNonNull(nextState, "nextState");
			retiredInstanceIds = boundedUuidSet(
				retiredInstanceIds,
				HARD_MAX_INSTANCES,
				"retired message instance ids"
			);
			if (!accepted && !retiredInstanceIds.isEmpty()) {
				throw new IllegalArgumentException(
					"rejected retirement cannot expose retired instance ids"
				);
			}
		}

		public boolean changed() {
			return !this.retiredInstanceIds.isEmpty();
		}
	}

	public static PlayResult play(
		final RuntimeState state,
		final MessageCueDefinition cue,
		final InvocationContext context,
		final ClockSample clock,
		final ParameterResolver parameterResolver,
		final AudienceResolver audienceResolver
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(clock, "clock");
		Objects.requireNonNull(parameterResolver, "parameterResolver");
		Objects.requireNonNull(audienceResolver, "audienceResolver");
		if (!context.authorized()) {
			return rejectedPlay(state, "message invocation is not authorized");
		}
		if (reservedInstanceId(state, context.requestedInstanceId())) {
			return rejectedPlay(state, "requested message instance id is already reserved");
		}
		String contextError = contextError(cue, context);
		if (contextError != null) {
			return rejectedPlay(state, contextError);
		}

		ParameterResolution parameters;
		try {
			parameters = parameterResolver.resolve(cue, context);
		} catch (RuntimeException error) {
			return rejectedPlay(
				state,
				"parameter resolver failed: " + safeMessage(error)
			);
		}
		if (parameters == null || !parameters.resolved()) {
			return rejectedPlay(
				state,
				parameters == null
					? "parameter resolver returned no result"
					: parameters.message()
			);
		}
		String parameterError = validateParameters(
			cue,
			parameters.canonicalValues(),
			parameters.authoritativeNodeDurations()
		);
		if (parameterError != null) {
			return rejectedPlay(state, parameterError);
		}

		AudienceResolution audience;
		try {
			audience = audienceResolver.resolve(
				cue.policies().audience().audience(),
				cue,
				context,
				parameters.canonicalValues()
			);
		} catch (RuntimeException error) {
			return rejectedPlay(state, "audience resolver failed: " + safeMessage(error));
		}
		if (audience == null || !audience.resolved()) {
			return rejectedPlay(
				state,
				audience == null ? "audience resolver returned no result" : audience.message()
			);
		}
		AudienceResolution normalizedAudience;
		try {
			normalizedAudience = normalizedAudience(cue, audience);
		} catch (RuntimeException error) {
			return rejectedPlay(
				state,
				"message audience normalization failed: " + safeMessage(error)
			);
		}
		String audienceError = validateAudience(cue, normalizedAudience);
		if (audienceError != null) {
			return rejectedPlay(state, audienceError);
		}
		if (
			normalizedAudience.cueTargets().isEmpty()
				&& cue.policies().delivery().emptyAudience() == EmptyAudienceMode.FAIL
		) {
			return rejectedPlay(state, "message cue resolved an empty audience");
		}

		InvocationContext effectiveContext = context;
		if (
			normalizedAudience.cueTargets().isEmpty()
				&& cue.policies().delivery().emptyAudience()
					== EmptyAudienceMode.CALLBACKS_ONLY
				&& context.externallyTimed()
		) {
			/*
			 * With no recipient stream there is no external driver that could prepare timed
			 * callback nodes. Switch this one server-only instance to the authority's internal
			 * clock so callbacks_only remains a real callback timeline instead of hanging.
			 */
				effectiveContext = new InvocationContext(
					context.requestedInstanceId(),
					context.authorized(),
				context.invokerId(),
				context.callTargets(),
				context.gameId(),
				context.phaseId(),
					context.taskId(),
					context.arguments(),
					context.definitionGeneration(),
					context.bypassContext(),
					false
				);
		}
		ClockMode clockMode = effectiveClock(cue);
		DedupeKey dedupeKey = dedupeKey(
			cue,
			normalizedAudience.cueTargets(),
			parameters.canonicalValues()
		);
		Optional<RefreshKey> refreshKey;
		try {
			refreshKey = refreshKey(cue, parameters.canonicalValues());
		} catch (IllegalArgumentException error) {
			return rejectedPlay(state, error.getMessage());
		}
		CooldownStamp cooldown = state.cooldowns().get(dedupeKey);
		if (
			cooldown != null
				&& clock.value(cooldown.clock()) < cooldown.expiresAtNanos()
		) {
			return ignoredPlay(state, "message invocation is inside its registered cooldown");
		}

		ResolvedInvocation resolved = new ResolvedInvocation(
			effectiveContext.requestedInstanceId(),
			cue,
			effectiveContext,
			parameters.canonicalValues(),
			parameters.authoritativeNodeDurations(),
			normalizedAudience.cueTargets(),
			normalizedAudience.nodeTargets(),
			dedupeKey,
			refreshKey,
			clockMode
		);
		if (cue.policies().concurrency().duplicate() == DuplicateMode.REFRESH) {
			if (refreshKey.isEmpty()) {
				return rejectedPlay(
					state,
					"refresh duplicate mode requires a registered stable refresh key"
				);
			}
			UUID existingId = state.refreshIndex().get(refreshKey.orElseThrow());
			if (existingId != null) {
				return refreshExisting(state, existingId, resolved, clock);
			}
		}

		UUID duplicateId = state.activeDedupe().get(dedupeKey);
		DuplicateMode duplicateMode = cue.policies().concurrency().duplicate();
		if (duplicateId != null) {
			return switch (duplicateMode) {
				case IGNORE_WHILE_ACTIVE -> ignoredPlay(
					state,
					"matching message instance is already active"
				);
				case QUEUE -> enqueue(state, resolved, clock);
				case RESTART -> restart(state, duplicateId, resolved, clock);
				case ALLOW, REFRESH -> create(state, resolved, clock, Optional.empty());
			};
		}

		if (activeCueCount(state, cue.id()) >= activeLimit(cue)) {
			return duplicateMode == DuplicateMode.QUEUE
				? enqueue(state, resolved, clock)
				: rejectedPlay(state, "message cue reached its active-instance limit");
		}
		return create(state, resolved, clock, Optional.empty());
	}

	public static AdvanceResult advance(
		final RuntimeState state,
		final ClockSample clock
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(clock, "clock");
		AdvanceResult advanced = advanceInstances(state, clock);
		if (!advanced.accepted()) {
			return advanced;
		}
		AdvanceResult drained = drainQueue(advanced.nextState(), clock);
		if (!drained.accepted()) {
			return new AdvanceResult(
				false,
				false,
				drained.message(),
				state,
				List.of(),
				List.of()
			);
		}
		List<ProjectionEvent> projections = concat(
			advanced.projections(),
			drained.projections()
		);
		List<PreparedCallback> callbacks = concat(
			advanced.callbacks(),
			drained.callbacks()
		);
		return new AdvanceResult(
			true,
			advanced.changed() || drained.changed(),
			advanced.changed() || drained.changed()
				? "message runtime advanced"
				: "message runtime has no due work",
			drained.nextState(),
			projections,
			callbacks
		);
	}

	public static ControlResult control(
		final RuntimeState state,
		final ControlSelector selector,
		final ControlAction action,
		final ClockSample clock,
		final String reason
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(selector, "selector");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(clock, "clock");
		String boundedReason;
		try {
			boundedReason = boundedReason(reason);
		} catch (RuntimeException error) {
			return new ControlResult(
				false,
				0,
				0,
				"control reason is invalid",
				state,
				List.of(),
				List.of()
			);
		}
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		List<ProjectionEvent> projections = new ArrayList<>();
		int matched = 0;
		int changed = 0;
		for (MessageInstance instance : state.instances().values()) {
			if (!selector.matches(instance)) {
				continue;
			}
			matched++;
			MessageInstance replacement = switch (action) {
				case PAUSE -> pauseInstance(instance, clock);
				case RESUME -> resumeInstance(instance, clock);
				case COMPLETE -> completeInstance(instance, clock, true);
				case CANCEL -> cancelInstance(instance, clock);
			};
			if (replacement == instance) {
				continue;
			}
			changed++;
			instances.put(instance.instanceId(), replacement);
			projections.add(
				controlProjection(action, replacement, boundedReason)
			);
		}
		if (matched == 0) {
			return new ControlResult(
				false,
				0,
				0,
				"control selector matched no message instance",
				state,
				List.of(),
				List.of()
			);
		}
		if (changed == 0) {
			return new ControlResult(
				true,
				matched,
				0,
				"matching message instances already satisfy the requested control state",
				state,
				List.of(),
				List.of()
			);
		}
		RuntimeState candidate;
		try {
			candidate = copyState(
				state,
				increment(state.revision()),
				instances,
				state.queue(),
				state.refreshIndex(),
				state.cooldowns(),
				state.callbackLedger()
			);
		} catch (RuntimeException error) {
			return new ControlResult(
				false,
				matched,
				0,
				"message control state could not be committed: " + safeMessage(error),
				state,
				List.of(),
				List.of()
			);
		}
		AdvanceResult advanced = advance(candidate, clock);
		if (!advanced.accepted()) {
			return new ControlResult(
				false,
				matched,
				0,
				advanced.message(),
				state,
				List.of(),
				List.of()
			);
		}
		return new ControlResult(
			true,
			matched,
			changed,
			"message control applied",
			advanced.nextState(),
			concat(projections, advanced.projections()),
			advanced.callbacks()
		);
	}

	public static ControlResult completeTarget(
		final RuntimeState state,
		final UUID instanceId,
		final UUID targetId,
		final ClockSample clock
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(targetId, "targetId");
		Objects.requireNonNull(clock, "clock");
		MessageInstance instance = state.instances().get(instanceId);
		if (instance == null) {
			return new ControlResult(
				false,
				0,
				0,
				"message instance does not exist",
				state,
				List.of(),
				List.of()
			);
		}
		if (!instance.status().active() || instance.status() == InstanceStatus.CANCELLING) {
			return new ControlResult(
				false,
				1,
				0,
				"message instance cannot accept target completion in its current state",
				state,
				List.of(),
				List.of()
			);
		}
		if (!instance.cueTargets().contains(targetId)) {
			return new ControlResult(
				false,
				1,
				0,
				"target is not part of the authoritative audience snapshot",
				state,
				List.of(),
				List.of()
			);
		}
		if (instance.completedTargets().contains(targetId)) {
			return new ControlResult(
				true,
				1,
				0,
				"target is already complete",
				state,
				List.of(),
				List.of()
			);
		}
		long elapsed;
		try {
			elapsed = instance.elapsedAt(clock);
		} catch (RuntimeException error) {
			return new ControlResult(
				false,
				1,
				0,
				"message clock moved backwards",
				state,
				List.of(),
				List.of()
			);
		}
		MessageInstance replacement = markTargetComplete(instance, targetId, elapsed, false);
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		instances.put(instanceId, replacement);
		RuntimeState candidate = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			state.callbackLedger()
		);
		ProjectionEvent event = new ProjectionEvent(
			ProjectionKind.TARGET_COMPLETE,
			instanceId,
			replacement.cycle(),
			Optional.empty(),
			0,
			Set.of(targetId),
			Optional.of(targetId),
			Optional.empty(),
			"target reached its server-authoritative completion point"
		);
		AdvanceResult advanced = advance(candidate, clock);
		if (!advanced.accepted()) {
			return new ControlResult(
				false,
				1,
				0,
				advanced.message(),
				state,
				List.of(),
				List.of()
			);
		}
		return new ControlResult(
			true,
			1,
			1,
			"target completion recorded",
			advanced.nextState(),
			concat(List.of(event), advanced.projections()),
			advanced.callbacks()
		);
	}

	/**
	 * Removes one timed-out recipient without converting cancellation into target completion.
	 *
	 * <p>This is deliberately narrower than live-audience reauthorization: it is only the
	 * lifecycle escape hatch for a recipient whose bounded screen wait selected {@code cancel}.
	 * Remaining recipients keep their frozen audience and exact schedules. Removing the last
	 * recipient cancels the whole instance so only interrupt callbacks can run.
	 */
	public static ControlResult cancelTarget(
		final RuntimeState state,
		final UUID instanceId,
		final UUID targetId,
		final ClockSample clock,
		final String reason
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(targetId, "targetId");
		Objects.requireNonNull(clock, "clock");
		MessageInstance instance = state.instances().get(instanceId);
		if (instance == null || !instance.status().active()) {
			return new ControlResult(
				false,
				0,
				0,
				"message instance is unavailable for target cancellation",
				state,
				List.of(),
				List.of()
			);
		}
		if (!instance.cueTargets().contains(targetId)) {
			return new ControlResult(
				true,
				1,
				0,
				"target is already outside the authoritative audience",
				state,
				List.of(),
				List.of()
			);
		}
		if (instance.cueTargets().size() == 1) {
			return control(
				state,
				new InstanceSelector(instanceId),
				ControlAction.CANCEL,
				clock,
				reason
			);
		}
		Set<UUID> targets = new LinkedHashSet<>(instance.cueTargets());
		targets.remove(targetId);
		Map<String, Set<UUID>> nodeTargets = new LinkedHashMap<>();
		for (CueNode node : instance.cue().nodes()) {
			Set<UUID> next = new LinkedHashSet<>(
				instance.nodeTargets().getOrDefault(
					node.id(),
					instance.cueTargets()
				)
			);
			next.remove(targetId);
			if (!next.equals(targets)) {
				nodeTargets.put(node.id(), Set.copyOf(next));
			}
		}
		Set<UUID> completed = new LinkedHashSet<>(instance.completedTargets());
		completed.remove(targetId);
		MessageInstance replacement = new MessageInstance(
			instance.instanceId(),
			instance.cue(),
			instance.context(),
			instance.canonicalParameters(),
			instance.authoritativeNodeDurations(),
			Set.copyOf(targets),
			Map.copyOf(nodeTargets),
			instance.dedupeKey(),
			instance.refreshKey(),
			instance.clockMode(),
			instance.cycle(),
			instance.status(),
			instance.resumeStatus(),
			instance.anchorClockNanos(),
			instance.accumulatedElapsedNanos(),
			instance.emittedOccurrences(),
			Set.copyOf(completed),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(
			state.instances()
		);
		instances.put(instanceId, replacement);
		RuntimeState committed = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			state.callbackLedger()
		);
		ProjectionEvent cancelled = new ProjectionEvent(
			ProjectionKind.CANCEL,
			instanceId,
			replacement.cycle(),
			Optional.empty(),
			0,
			Set.of(targetId),
			Optional.empty(),
			Optional.empty(),
			boundedReason(reason)
		);
		AdvanceResult advanced = advance(committed, clock);
		if (!advanced.accepted()) {
			return new ControlResult(
				false,
				1,
				0,
				advanced.message(),
				state,
				List.of(),
				List.of()
			);
		}
		return new ControlResult(
			true,
			1,
			1,
			"target cancellation recorded",
			advanced.nextState(),
			concat(List.of(cancelled), advanced.projections()),
			advanced.callbacks()
		);
	}

	/**
	 * Replaces or expands one live audience only after the caller has resolved it from the current
	 * authoritative world state.
	 */
	public static ReauthorizationResult reauthorize(
		final RuntimeState state,
		final UUID instanceId,
		final AudienceResolution resolvedAudience,
		final ClockSample clock
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(resolvedAudience, "resolvedAudience");
		Objects.requireNonNull(clock, "clock");
		MessageInstance instance = state.instances().get(instanceId);
		if (instance == null || !instance.status().active()) {
			return new ReauthorizationResult(
				false,
				false,
				"message instance is unavailable for reauthorization",
				state,
				List.of(),
				List.of()
			);
		}
		var policy = instance.cue().policies().audience();
		if (
			policy.evolution()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution.SNAPSHOT
		) {
			return new ReauthorizationResult(
				true,
				false,
				"snapshot audience does not evolve",
				state,
				List.of(),
				List.of()
			);
		}
		if (!resolvedAudience.resolved()) {
			return new ReauthorizationResult(
				false,
				false,
				resolvedAudience.message(),
				state,
				List.of(),
				List.of()
			);
		}
		AudienceResolution normalizedAudience;
		try {
			normalizedAudience = normalizedAudience(instance.cue(), resolvedAudience);
		} catch (RuntimeException error) {
			return new ReauthorizationResult(
				false,
				false,
				"live audience normalization failed: " + safeMessage(error),
				state,
				List.of(),
				List.of()
			);
		}
		String audienceError = validateAudience(instance.cue(), normalizedAudience);
		if (audienceError != null) {
			return new ReauthorizationResult(
				false,
				false,
				audienceError,
				state,
				List.of(),
				List.of()
			);
		}
		Set<UUID> currentTargets = instance.cueTargets();
		Set<UUID> nextTargets = new LinkedHashSet<>(currentTargets);
		if (
			policy.evolution()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution.LIVE_ADD
		) {
			nextTargets.addAll(normalizedAudience.cueTargets());
		} else {
			nextTargets.clear();
			nextTargets.addAll(normalizedAudience.cueTargets());
		}
		Map<String, Set<UUID>> nextNodeTargets = new LinkedHashMap<>();
		for (CueNode node : instance.cue().nodes()) {
			Set<UUID> current = instance.nodeTargets().getOrDefault(
				node.id(),
				currentTargets
			);
			Set<UUID> resolved = normalizedAudience.nodeTargets().getOrDefault(
				node.id(),
				normalizedAudience.cueTargets()
			);
			Set<UUID> next = new LinkedHashSet<>(current);
			if (
				policy.evolution()
					== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution.LIVE_ADD
			) {
				next.addAll(resolved);
			} else {
				next.clear();
				next.addAll(resolved);
			}
			if (!next.equals(nextTargets)) {
				nextNodeTargets.put(node.id(), Set.copyOf(next));
			}
		}
		Set<UUID> added = new LinkedHashSet<>(nextTargets);
		added.removeAll(currentTargets);
		Set<UUID> lost = new LinkedHashSet<>(currentTargets);
		lost.removeAll(nextTargets);
		if (
			added.isEmpty()
				&& lost.isEmpty()
				&& nextNodeTargets.equals(instance.nodeTargets())
		) {
			return new ReauthorizationResult(
				true,
				false,
				"live audience is unchanged",
				state,
				List.of(),
				List.of()
			);
		}
		Set<UUID> completed = new LinkedHashSet<>(instance.completedTargets());
		completed.retainAll(nextTargets);
		MessageInstance replacement = new MessageInstance(
			instance.instanceId(),
			instance.cue(),
			instance.context(),
			instance.canonicalParameters(),
			instance.authoritativeNodeDurations(),
			Set.copyOf(nextTargets),
			Map.copyOf(nextNodeTargets),
			instance.dedupeKey(),
			instance.refreshKey(),
			instance.clockMode(),
			instance.cycle(),
			instance.status(),
			instance.resumeStatus(),
			instance.anchorClockNanos(),
			instance.accumulatedElapsedNanos(),
			instance.emittedOccurrences(),
			Set.copyOf(completed),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		instances.put(instanceId, replacement);
		RuntimeState committed = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			state.callbackLedger()
		);
		List<ProjectionEvent> projections = new ArrayList<>();
		if (!added.isEmpty()) {
			projections.add(
				new ProjectionEvent(
					ProjectionKind.CREATE,
					instanceId,
					replacement.cycle(),
					Optional.empty(),
					0,
					Set.copyOf(added),
					Optional.empty(),
					Optional.empty(),
					"players entered the live message audience"
				)
			);
		}
		if (!lost.isEmpty()) {
			ProjectionKind lossKind = policy.onLoss()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceLossMode.FINALIZE
				? ProjectionKind.COMPLETE
				: ProjectionKind.CANCEL;
			projections.add(
				new ProjectionEvent(
					lossKind,
					instanceId,
					replacement.cycle(),
					Optional.empty(),
					0,
					Set.copyOf(lost),
					Optional.empty(),
					Optional.empty(),
					"players left the live message audience"
				)
			);
		}
		AdvanceResult advanced = advance(committed, clock);
		if (!advanced.accepted()) {
			return new ReauthorizationResult(
				false,
				false,
				advanced.message(),
				state,
				List.of(),
				List.of()
			);
		}
		return new ReauthorizationResult(
			true,
			true,
			"live audience reauthorized",
			advanced.nextState(),
			concat(projections, advanced.projections()),
			advanced.callbacks()
		);
	}

	/**
	 * Commits one externally timed cue-start callback decision before the integration layer
	 * executes the returned PREPARED callback.
	 *
	 * <p>Only callback nodes belonging to an {@link InvocationContext#externallyTimed()} instance
	 * are accepted here. A false condition is still recorded as emitted so it cannot be
	 * reconsidered later.
	 */
	public static AdvanceResult resolveExternalCallback(
		final RuntimeState state,
		final UUID instanceId,
		final String nodeId,
		final int occurrence,
		final boolean included,
		final ClockSample clock
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(nodeId, "nodeId");
		Objects.requireNonNull(clock, "clock");
		MessageInstance instance = state.instances().get(instanceId);
		if (
			instance == null
				|| !instance.context().externallyTimed()
				|| !instance.status().active()
		) {
			return new AdvanceResult(
				false,
				false,
				"externally timed callback instance is unavailable",
				state,
				List.of(),
				List.of()
			);
		}
		CueNode rawNode = instance.cue().nodes().stream()
			.filter(node -> node.id().equals(nodeId))
			.findFirst()
			.orElse(null);
		if (
			!(rawNode instanceof CallbackNode callback)
				|| rawNode.header().schedule() instanceof OnCueEvent
				|| occurrence < 0
				|| occurrence >= rawNode.header().repeat().count()
		) {
			return new AdvanceResult(
				false,
				false,
				"external callback occurrence is not registered",
				state,
				List.of(),
				List.of()
			);
		}
		OccurrenceKey key = new OccurrenceKey(
			nodeId,
			occurrence,
			CallbackTrigger.START,
			Optional.empty()
		);
		if (instance.emittedOccurrences().contains(key)) {
			return new AdvanceResult(
				true,
				false,
				"external callback occurrence is already decided",
				state,
				List.of(),
				List.of()
			);
		}
		Set<OccurrenceKey> emitted = new LinkedHashSet<>(instance.emittedOccurrences());
		emitted.add(key);
		Ledger ledger = state.callbackLedger();
		List<PreparedCallback> callbacks = new ArrayList<>();
		if (included) {
			CallbackKey callbackKey = new CallbackKey(
				instance.instanceId(),
				instance.cycle(),
				nodeId,
				occurrence,
				CallbackTrigger.START,
				Optional.empty()
			);
			MessageCallbackLedger.PrepareResult prepared = MessageCallbackLedger.prepare(
				ledger,
				callbackKey,
				callback.function(),
				callback.execution(),
				callback.location(),
				clock.gameTick()
			);
			if (
				prepared.disposition()
					== MessageCallbackLedger.PrepareDisposition.REJECTED
			) {
				return new AdvanceResult(
					false,
					false,
					"external callback could not be prepared: " + prepared.message(),
					state,
					List.of(),
					List.of()
				);
			}
			ledger = prepared.nextLedger();
			prepared.callback().ifPresent(callbacks::add);
		}
		MessageInstance replacement = copyInstance(
			instance,
			instance.status(),
			instance.resumeStatus(),
			instance.anchorClockNanos(),
			instance.accumulatedElapsedNanos(),
			emitted,
			instance.completedTargets(),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		instances.put(instanceId, replacement);
		RuntimeState committed = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			ledger
		);
		AdvanceResult advanced = advance(committed, clock);
		if (!advanced.accepted()) {
			return new AdvanceResult(
				false,
				false,
				advanced.message(),
				state,
				List.of(),
				List.of()
			);
		}
		return new AdvanceResult(
			true,
			true,
			included
				? "external callback occurrence prepared"
				: "external callback occurrence skipped",
			advanced.nextState(),
			advanced.projections(),
			concat(callbacks, advanced.callbacks())
		);
	}

	public static CallbackOutcomeResult recordCallbackOutcome(
		final RuntimeState state,
		final PreparedCallback prepared,
		final boolean succeeded,
		final Optional<String> diagnostic,
		final long gameTick
	) {
		Objects.requireNonNull(state, "state");
		MessageCallbackLedger.MutationResult outcome = MessageCallbackLedger.recordOutcome(
			state.callbackLedger(),
			prepared,
			succeeded,
			diagnostic,
			gameTick
		);
		if (!outcome.accepted()) {
			return new CallbackOutcomeResult(false, false, outcome.message(), state);
		}
		if (!outcome.changed()) {
			return new CallbackOutcomeResult(true, false, outcome.message(), state);
		}
		RuntimeState next = copyState(
			state,
			increment(state.revision()),
			state.instances(),
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			outcome.nextLedger()
		);
		return new CallbackOutcomeResult(true, true, outcome.message(), next);
	}

	public static CallbackOutcomeResult recoverPreparedCallbacks(
		final RuntimeState state,
		final long gameTick,
		final String reason
	) {
		Objects.requireNonNull(state, "state");
		MessageCallbackLedger.MutationResult recovery = MessageCallbackLedger.recoverPrepared(
			state.callbackLedger(),
			gameTick,
			reason
		);
		if (!recovery.accepted()) {
			return new CallbackOutcomeResult(false, false, recovery.message(), state);
		}
		if (!recovery.changed()) {
			return new CallbackOutcomeResult(true, false, recovery.message(), state);
		}
		RuntimeState next = copyState(
			state,
			increment(state.revision()),
			state.instances(),
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			recovery.nextLedger()
		);
		return new CallbackOutcomeResult(true, true, recovery.message(), next);
	}

	/**
	 * Removes only matching queued invocations before they become live instances.
	 *
	 * <p>Unknown ids and ids belonging to already-started instances are harmless no-ops. The
	 * transaction never drains the remaining queue, advances a clock, prepares a callback or emits
	 * a projection. Registered invocation cooldowns are retained because they describe the call
	 * that already happened, not successful visual delivery.
	 */
	public static QueueRemovalResult removeQueuedInvocations(
		final RuntimeState state,
		final Set<UUID> instanceIds
	) {
		Objects.requireNonNull(state, "state");
		Set<UUID> requested = boundedUuidSet(
			instanceIds,
			HARD_MAX_QUEUE,
			"queued removal instance ids"
		);
		if (requested.isEmpty()) {
			return new QueueRemovalResult(
				true,
				"no queued message instance ids were requested",
				state,
				Set.of()
			);
		}

		List<QueuedInvocation> remaining = new ArrayList<>(state.queue().size());
		Set<UUID> removed = new LinkedHashSet<>();
		for (QueuedInvocation invocation : state.queue()) {
			if (requested.contains(invocation.instanceId())) {
				removed.add(invocation.instanceId());
			} else {
				remaining.add(invocation);
			}
		}
		if (removed.isEmpty()) {
			return new QueueRemovalResult(
				true,
				"requested ids matched no queued message invocation",
				state,
				Set.of()
			);
		}

		RuntimeState next;
		try {
			next = copyState(
				state,
				increment(state.revision()),
				state.instances(),
				remaining,
				state.refreshIndex(),
				state.cooldowns(),
				state.callbackLedger()
			);
		} catch (RuntimeException error) {
			return new QueueRemovalResult(
				false,
				"queued message removal could not be committed: " + safeMessage(error),
				state,
				Set.of()
			);
		}
		return new QueueRemovalResult(
			true,
			"queued message invocations removed before start",
			next,
			Set.copyOf(removed)
		);
	}

	/**
	 * Retires a batch of existing terminal instances without discarding callback replay evidence.
	 *
	 * <p>Missing ids are idempotent no-ops. If any requested existing instance is still active,
	 * paused, completing or cancelling, the whole batch is rejected and the original state is
	 * returned unchanged. A successful mutation rebuilds active-dedupe and refresh indexes from the
	 * surviving instances and drops only callback entries owned by the retired instances, reclaiming
	 * their bounded ledger capacity without disturbing survivor replay evidence.
	 */
	public static RetirementResult retireTerminalInstances(
		final RuntimeState state,
		final Set<UUID> instanceIds
	) {
		Objects.requireNonNull(state, "state");
		Set<UUID> requested = boundedUuidSet(
			instanceIds,
			HARD_MAX_INSTANCES,
			"retirement instance ids"
		);
		if (requested.isEmpty()) {
			return new RetirementResult(
				true,
				"no terminal message instance ids were requested",
				state,
				Set.of()
			);
		}

		for (UUID instanceId : requested) {
			MessageInstance instance = state.instances().get(instanceId);
			if (instance != null && !instance.status().terminal()) {
				return new RetirementResult(
					false,
					"retirement batch contains a non-terminal message instance",
					state,
					Set.of()
				);
			}
		}

		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		Set<UUID> retired = new LinkedHashSet<>();
		for (UUID instanceId : requested) {
			MessageInstance removed = instances.remove(instanceId);
			if (removed != null) {
				retired.add(instanceId);
			}
		}
		if (retired.isEmpty()) {
			return new RetirementResult(
				true,
				"requested ids matched no retained terminal message instance",
				state,
				Set.of()
			);
		}
		Ledger retainedCallbacks = new Ledger(
			state.callbackLedger().capacity(),
			state.callbackLedger()
				.entries()
				.stream()
				.filter(entry -> !retired.contains(entry.key().instanceId()))
				.toList()
		);

		RuntimeState next;
		try {
			next = copyState(
				state,
				increment(state.revision()),
				instances,
				state.queue(),
				state.refreshIndex(),
				state.cooldowns(),
				retainedCallbacks
			);
		} catch (RuntimeException error) {
			return new RetirementResult(
				false,
				"terminal message retirement could not be committed: "
					+ safeMessage(error),
				state,
				Set.of()
			);
		}
		return new RetirementResult(
			true,
			"terminal message instances retired",
			next,
			Set.copyOf(retired)
		);
	}

	private static PlayResult create(
		final RuntimeState state,
		final ResolvedInvocation resolved,
		final ClockSample clock,
		final Optional<UUID> replacedInstanceId
	) {
		if (state.instances().size() >= HARD_MAX_INSTANCES) {
			return rejectedPlay(state, "message runtime reached its hard instance capacity");
		}
		String persistenceError = persistentReservationError(
			state,
			resolved.cue(),
			replacedInstanceId
		);
		if (persistenceError != null) {
			return rejectedPlay(state, persistenceError);
		}
		MessageInstance instance;
		try {
			instance = newInstance(resolved, clock, 0L);
		} catch (RuntimeException error) {
			return rejectedPlay(state, "message timeline is invalid: " + safeMessage(error));
		}
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		instances.put(instance.instanceId(), instance);
		Map<RefreshKey, UUID> refresh = new LinkedHashMap<>(state.refreshIndex());
		if (instance.refreshKey().isPresent()) {
			if (
				refresh.size() >= HARD_MAX_REFRESH_KEYS
					&& !refresh.containsKey(instance.refreshKey().orElseThrow())
			) {
				return rejectedPlay(state, "message refresh index reached its hard capacity");
			}
			refresh.put(instance.refreshKey().orElseThrow(), instance.instanceId());
		}
		Map<DedupeKey, CooldownStamp> cooldowns;
		try {
			cooldowns = withCooldown(state.cooldowns(), instance, clock);
		} catch (RuntimeException error) {
			return rejectedPlay(state, error.getMessage());
		}
		RuntimeState candidate;
		try {
			candidate = copyState(
				state,
				increment(state.revision()),
				instances,
				state.queue(),
				refresh,
				cooldowns,
				state.callbackLedger()
			);
		} catch (RuntimeException error) {
			return rejectedPlay(state, "message instance could not be created: " + safeMessage(error));
		}
		ProjectionKind kind = replacedInstanceId.isPresent()
			? ProjectionKind.REPLACE
			: ProjectionKind.CREATE;
		ProjectionEvent start = new ProjectionEvent(
			kind,
			instance.instanceId(),
			instance.cycle(),
			Optional.empty(),
			0,
			instance.cueTargets(),
			Optional.empty(),
			replacedInstanceId,
			replacedInstanceId.isPresent()
				? "matching message instance restarted"
				: "message instance created"
		);
		AdvanceResult advanced = advanceInstances(candidate, clock);
		if (!advanced.accepted()) {
			return rejectedPlay(state, advanced.message());
		}
		return new PlayResult(
			PlayDisposition.CREATED,
			replacedInstanceId.isPresent()
				? "message instance restarted with an explicit replace projection"
				: "message instance created",
			advanced.nextState(),
			Optional.of(instance.instanceId()),
			concat(List.of(start), advanced.projections()),
			advanced.callbacks()
		);
	}

	private static PlayResult restart(
		final RuntimeState state,
		final UUID existingId,
		final ResolvedInvocation resolved,
		final ClockSample clock
	) {
		MessageInstance existing = state.instances().get(existingId);
		if (existing == null || !existing.status().active()) {
			return create(state, resolved, clock, Optional.empty());
		}
		MessageInstance interrupted = cancelInstance(existing, clock);
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		instances.put(existingId, interrupted);
		RuntimeState interruptedState = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			state.callbackLedger()
		);
		AdvanceResult advanced = advanceInstances(interruptedState, clock);
		if (!advanced.accepted()) {
			return rejectedPlay(state, advanced.message());
		}
		PlayResult created = create(
			advanced.nextState(),
			resolved,
			clock,
			Optional.of(existingId)
		);
		if (created.disposition() == PlayDisposition.REJECTED) {
			return rejectedPlay(state, created.message());
		}
		return new PlayResult(
			created.disposition(),
			created.message(),
			created.nextState(),
			created.instanceId(),
			concat(advanced.projections(), created.projections()),
			concat(advanced.callbacks(), created.callbacks())
		);
	}

	private static PlayResult refreshExisting(
		final RuntimeState state,
		final UUID existingId,
		final ResolvedInvocation resolved,
		final ClockSample clock
	) {
		MessageInstance existing = state.instances().get(existingId);
		if (existing == null) {
			return rejectedPlay(state, "stable refresh index points to a missing instance");
		}
		long cycle;
		long instanceRevision;
		try {
			cycle = increment(existing.cycle());
			instanceRevision = increment(existing.instanceRevision());
		} catch (RuntimeException error) {
			return rejectedPlay(state, "message refresh counter is exhausted");
		}
		MessageInstance refreshed;
		try {
			refreshed = newInstance(
				new ResolvedInvocation(
					existing.instanceId(),
					resolved.cue(),
					resolved.context(),
					resolved.canonicalParameters(),
					resolved.authoritativeNodeDurations(),
					resolved.cueTargets(),
					resolved.nodeTargets(),
					resolved.dedupeKey(),
					resolved.refreshKey(),
					resolved.clockMode()
				),
				clock,
				cycle
			);
			refreshed = copyInstance(
				refreshed,
				refreshed.status(),
				refreshed.resumeStatus(),
				refreshed.anchorClockNanos(),
				refreshed.accumulatedElapsedNanos(),
				refreshed.emittedOccurrences(),
				refreshed.completedTargets(),
				refreshed.eventOffsets(),
				false,
				instanceRevision
			);
		} catch (RuntimeException error) {
			return rejectedPlay(state, "message refresh timeline is invalid: " + safeMessage(error));
		}
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		instances.put(existingId, refreshed);
		Map<DedupeKey, CooldownStamp> cooldowns;
		try {
			cooldowns = withCooldown(state.cooldowns(), refreshed, clock);
		} catch (RuntimeException error) {
			return rejectedPlay(state, error.getMessage());
		}
		RuntimeState candidate = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			cooldowns,
			state.callbackLedger()
		);
		ProjectionEvent refresh = new ProjectionEvent(
			ProjectionKind.REFRESH,
			existingId,
			cycle,
			Optional.empty(),
			0,
			refreshed.cueTargets(),
			Optional.empty(),
			Optional.empty(),
			"stable refresh key updated the existing projection"
		);
		AdvanceResult advanced = advanceInstances(candidate, clock);
		if (!advanced.accepted()) {
			return rejectedPlay(state, advanced.message());
		}
		return new PlayResult(
			PlayDisposition.REFRESHED,
			"message instance refreshed through its registered stable key",
			advanced.nextState(),
			Optional.of(existingId),
			concat(List.of(refresh), advanced.projections()),
			advanced.callbacks()
		);
	}

	private static PlayResult enqueue(
		final RuntimeState state,
		final ResolvedInvocation resolved,
		final ClockSample clock
	) {
		int limit = queueLimit(resolved.cue());
		long sameCue = state.queue().stream()
			.filter(item -> item.cue().id().equals(resolved.cue().id()))
			.count();
		if (state.queue().size() >= HARD_MAX_QUEUE || sameCue >= limit) {
			return rejectedPlay(state, "message queue reached its bounded capacity");
		}
		String persistenceError = persistentReservationError(
			state,
			resolved.cue(),
			Optional.empty()
		);
		if (persistenceError != null) {
			return rejectedPlay(state, persistenceError);
		}
		QueuedInvocation queued = new QueuedInvocation(
			resolved.instanceId(),
			resolved.cue(),
			resolved.context(),
			resolved.canonicalParameters(),
			resolved.authoritativeNodeDurations(),
			resolved.cueTargets(),
			resolved.nodeTargets(),
			resolved.dedupeKey(),
			resolved.refreshKey(),
			resolved.clockMode(),
			clock.value(resolved.clockMode())
		);
		List<QueuedInvocation> queue = new ArrayList<>(state.queue());
		queue.add(queued);
		Map<DedupeKey, CooldownStamp> cooldowns;
		try {
			cooldowns = withCooldown(state.cooldowns(), queued, clock);
		} catch (RuntimeException error) {
			return rejectedPlay(state, error.getMessage());
		}
		RuntimeState next = copyState(
			state,
			increment(state.revision()),
			state.instances(),
			queue,
			state.refreshIndex(),
			cooldowns,
			state.callbackLedger()
		);
		return new PlayResult(
			PlayDisposition.QUEUED,
			"message invocation queued behind its matching active instance",
			next,
			Optional.of(queued.instanceId()),
			List.of(),
			List.of()
		);
	}

	private static AdvanceResult drainQueue(
		final RuntimeState state,
		final ClockSample clock
	) {
		RuntimeState current = state;
		List<ProjectionEvent> projections = new ArrayList<>();
		List<PreparedCallback> callbacks = new ArrayList<>();
		boolean changed = false;
		boolean progressed;
		do {
			progressed = false;
			for (int index = 0; index < current.queue().size(); index++) {
				QueuedInvocation queued = current.queue().get(index);
				if (
					current.activeDedupe().containsKey(queued.dedupeKey())
						|| activeCueCount(current, queued.cue().id()) >= activeLimit(queued.cue())
				) {
					continue;
				}
				List<QueuedInvocation> remaining = new ArrayList<>(current.queue());
				remaining.remove(index);
				RuntimeState without = copyState(
					current,
					increment(current.revision()),
					current.instances(),
					remaining,
					current.refreshIndex(),
					current.cooldowns(),
					current.callbackLedger()
				);
				PlayResult created = create(
					without,
					new ResolvedInvocation(
						queued.instanceId(),
						queued.cue(),
						queued.context(),
						queued.canonicalParameters(),
						queued.authoritativeNodeDurations(),
						queued.cueTargets(),
						queued.nodeTargets(),
						queued.dedupeKey(),
						queued.refreshKey(),
						queued.clockMode()
					),
					clock,
					Optional.empty()
				);
				if (created.disposition() == PlayDisposition.REJECTED) {
					return new AdvanceResult(
						false,
						false,
						"queued message could not start: " + created.message(),
						state,
						List.of(),
						List.of()
					);
				}
				current = created.nextState();
				projections.addAll(created.projections());
				callbacks.addAll(created.callbacks());
				changed = true;
				progressed = true;
				break;
			}
		} while (progressed);
		return new AdvanceResult(
			true,
			changed,
			changed ? "eligible queued messages started" : "message queue has no eligible item",
			current,
			projections,
			callbacks
		);
	}

	private static AdvanceResult advanceInstances(
		final RuntimeState state,
		final ClockSample clock
	) {
		Map<UUID, MessageInstance> instances = new LinkedHashMap<>(state.instances());
		Ledger ledger = state.callbackLedger();
		List<ProjectionEvent> projections = new ArrayList<>();
		List<PreparedCallback> callbacks = new ArrayList<>();
		boolean changed = false;
		for (MessageInstance original : state.instances().values()) {
			if (!original.status().active() || original.status() == InstanceStatus.PAUSED) {
				continue;
			}
			InstanceAdvance result;
			try {
				result = advanceInstance(original, ledger, clock);
			} catch (RuntimeException error) {
				return new AdvanceResult(
					false,
					false,
					"message instance advancement failed: " + safeMessage(error),
					state,
					List.of(),
					List.of()
				);
			}
			if (!result.accepted()) {
				return new AdvanceResult(
					false,
					false,
					result.message(),
					state,
					List.of(),
					List.of()
				);
			}
			if (result.instance() != original || result.ledger() != ledger) {
				changed = true;
				instances.put(original.instanceId(), result.instance());
				ledger = result.ledger();
			}
			projections.addAll(result.projections());
			callbacks.addAll(result.callbacks());
		}
		if (!changed) {
			return new AdvanceResult(
				true,
				false,
				"message runtime has no due instance work",
				state,
				List.of(),
				List.of()
			);
		}
		RuntimeState next = copyState(
			state,
			increment(state.revision()),
			instances,
			state.queue(),
			state.refreshIndex(),
			state.cooldowns(),
			ledger
		);
		return new AdvanceResult(
			true,
			true,
			"due message nodes and completions advanced",
			next,
			projections,
			callbacks
		);
	}

	private static InstanceAdvance advanceInstance(
		final MessageInstance original,
		final Ledger initialLedger,
		final ClockSample clock
	) {
		long elapsed = original.elapsedAt(clock);
		MessageInstance instance = original;
		List<ProjectionEvent> lifecycleProjections = new ArrayList<>();
		if (instance.status() == InstanceStatus.ACTIVE) {
			TimelinePlan plan = timelinePlan(instance);
			if (!instance.context().externallyTimed()) {
				for (Map.Entry<UUID, Long> completion : plan.targetCompletionOffsets().entrySet()) {
					if (
						completion.getValue() <= elapsed
							&& !instance.completedTargets().contains(completion.getKey())
					) {
						instance = markTargetComplete(
							instance,
							completion.getKey(),
							completion.getValue(),
							false
						);
						lifecycleProjections.add(
							new ProjectionEvent(
								ProjectionKind.TARGET_COMPLETE,
								instance.instanceId(),
								instance.cycle(),
								Optional.empty(),
								0,
								Set.of(completion.getKey()),
								Optional.of(completion.getKey()),
								Optional.empty(),
								"target reached its server-authoritative completion point"
							)
						);
					}
				}
			}
			if (
				instance.completedTargets().containsAll(instance.cueTargets())
					&& (
						instance.context().externallyTimed()
							|| elapsed >= plan.allStartWorkCompleteAt()
					)
					&& !instance.eventOffsets().containsKey(
						new EventKey(CallbackTrigger.ALL_COMPLETE, Optional.empty())
					)
			) {
				long eventAt = Math.max(
					instance.context().externallyTimed()
						? 0L
						: plan.allStartWorkCompleteAt(),
					latestTargetEvent(instance)
				);
				instance = addEvent(
					instance,
					new EventKey(CallbackTrigger.ALL_COMPLETE, Optional.empty()),
					eventAt
				);
				instance = copyInstance(
					instance,
					InstanceStatus.COMPLETING,
					Optional.empty(),
					instance.anchorClockNanos(),
					instance.accumulatedElapsedNanos(),
					instance.emittedOccurrences(),
					instance.completedTargets(),
					instance.eventOffsets(),
					false,
					increment(instance.instanceRevision())
				);
			}
		}

		List<ScheduledOccurrence> due = dueOccurrences(instance, elapsed);
		if (due.isEmpty()) {
			MessageInstance terminal = finishTerminalIfReady(
				instance,
				elapsed,
				initialLedger
			);
			if (
				terminal.status() == InstanceStatus.COMPLETED
					&& instance.status() != InstanceStatus.COMPLETED
					&& !terminal.forcedCompletion()
			) {
				lifecycleProjections.add(naturalCompletionProjection(terminal));
			}
			return terminal == original
				? InstanceAdvance.unchanged(original, initialLedger)
				: InstanceAdvance.changed(
					terminal,
					initialLedger,
					lifecycleProjections,
					List.of()
				);
		}
		Set<OccurrenceKey> emitted = new LinkedHashSet<>(instance.emittedOccurrences());
		Ledger ledger = initialLedger;
		List<ProjectionEvent> projections = new ArrayList<>(lifecycleProjections);
		List<PreparedCallback> callbacks = new ArrayList<>();
		for (ScheduledOccurrence occurrence : due) {
			if (emitted.contains(occurrence.key())) {
				continue;
			}
			if (occurrence.node() instanceof CallbackNode callbackNode) {
				CallbackKey callbackKey = new CallbackKey(
					instance.instanceId(),
					instance.cycle(),
					occurrence.key().nodeId(),
					occurrence.key().occurrence(),
					occurrence.key().trigger(),
					occurrence.key().targetId()
				);
				MessageCallbackLedger.PrepareResult prepared = MessageCallbackLedger.prepare(
					ledger,
					callbackKey,
					callbackNode.function(),
					callbackNode.execution(),
					callbackNode.location(),
					clock.gameTick()
				);
				if (
					prepared.disposition()
						== MessageCallbackLedger.PrepareDisposition.REJECTED
				) {
					return InstanceAdvance.rejected(
						"message callback could not be prepared: " + prepared.message(),
						original,
						initialLedger
					);
				}
				ledger = prepared.nextLedger();
				prepared.callback().ifPresent(callbacks::add);
			} else {
				projections.add(
					new ProjectionEvent(
						ProjectionKind.NODE_DUE,
						instance.instanceId(),
						instance.cycle(),
						Optional.of(occurrence.key().nodeId()),
						occurrence.key().occurrence(),
						occurrence.recipients(),
						occurrence.key().targetId(),
						Optional.empty(),
						"message node reached its server-authoritative schedule"
					)
				);
			}
			emitted.add(occurrence.key());
		}
		MessageInstance advanced = copyInstance(
			instance,
			instance.status(),
			instance.resumeStatus(),
			instance.anchorClockNanos(),
			instance.accumulatedElapsedNanos(),
			emitted,
			instance.completedTargets(),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
		InstanceStatus beforeTerminal = advanced.status();
		advanced = finishTerminalIfReady(advanced, elapsed, ledger);
		if (
			advanced.status() == InstanceStatus.COMPLETED
				&& beforeTerminal != InstanceStatus.COMPLETED
				&& !advanced.forcedCompletion()
		) {
			projections.add(naturalCompletionProjection(advanced));
		}
		return InstanceAdvance.changed(advanced, ledger, projections, callbacks);
	}

	private static MessageInstance markTargetComplete(
		final MessageInstance instance,
		final UUID targetId,
		final long elapsed,
		final boolean forced
	) {
		if (instance.completedTargets().contains(targetId)) {
			return instance;
		}
		Set<UUID> completed = new LinkedHashSet<>(instance.completedTargets());
		completed.add(targetId);
		Map<EventKey, Long> events = new LinkedHashMap<>(instance.eventOffsets());
		events.put(
			new EventKey(CallbackTrigger.TARGET_COMPLETE, Optional.of(targetId)),
			elapsed
		);
		InstanceStatus status = instance.status();
		if (completed.containsAll(instance.cueTargets()) && forced) {
			events.put(
				new EventKey(CallbackTrigger.ALL_COMPLETE, Optional.empty()),
				elapsed
			);
			status = InstanceStatus.COMPLETING;
		}
		return copyInstance(
			instance,
			status,
			status == InstanceStatus.PAUSED ? instance.resumeStatus() : Optional.empty(),
			instance.anchorClockNanos(),
			instance.accumulatedElapsedNanos(),
			instance.emittedOccurrences(),
			completed,
			events,
			instance.forcedCompletion() || forced,
			increment(instance.instanceRevision())
		);
	}

	private static MessageInstance completeInstance(
		final MessageInstance instance,
		final ClockSample clock,
		final boolean forced
	) {
		if (
			instance.status().terminal()
				|| instance.status() == InstanceStatus.CANCELLING
				|| (
					instance.status() == InstanceStatus.COMPLETING
						&& instance.forcedCompletion()
				)
		) {
			return instance;
		}
		long elapsed = instance.elapsedAt(clock);
		MessageInstance completed = instance;
		for (UUID target : instance.cueTargets()) {
			completed = markTargetComplete(completed, target, elapsed, forced);
		}
		if (instance.cueTargets().isEmpty()) {
			completed = addEvent(
				completed,
				new EventKey(CallbackTrigger.ALL_COMPLETE, Optional.empty()),
				elapsed
			);
		}
		return copyInstance(
			completed,
			InstanceStatus.COMPLETING,
			Optional.empty(),
			completed.anchorClockNanos(),
			completed.accumulatedElapsedNanos(),
			completed.emittedOccurrences(),
			completed.completedTargets(),
			completed.eventOffsets(),
			true,
			increment(completed.instanceRevision())
		);
	}

	private static MessageInstance cancelInstance(
		final MessageInstance instance,
		final ClockSample clock
	) {
		if (
			instance.status() == InstanceStatus.CANCELLED
				|| instance.status() == InstanceStatus.CANCELLING
				|| instance.status() == InstanceStatus.COMPLETED
		) {
			return instance;
		}
		long elapsed = instance.elapsedAt(clock);
		MessageInstance cancelling = addEvent(
			instance,
			new EventKey(CallbackTrigger.INTERRUPT, Optional.empty()),
			elapsed
		);
		return copyInstance(
			cancelling,
			InstanceStatus.CANCELLING,
			Optional.empty(),
			cancelling.anchorClockNanos(),
			cancelling.accumulatedElapsedNanos(),
			cancelling.emittedOccurrences(),
			cancelling.completedTargets(),
			cancelling.eventOffsets(),
			false,
			increment(cancelling.instanceRevision())
		);
	}

	private static MessageInstance pauseInstance(
		final MessageInstance instance,
		final ClockSample clock
	) {
		if (
			instance.status() == InstanceStatus.PAUSED
				|| instance.status().terminal()
				|| instance.status() == InstanceStatus.CANCELLING
		) {
			return instance;
		}
		long elapsed = instance.elapsedAt(clock);
		return copyInstance(
			instance,
			InstanceStatus.PAUSED,
			Optional.of(instance.status()),
			clock.value(instance.clockMode()),
			elapsed,
			instance.emittedOccurrences(),
			instance.completedTargets(),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
	}

	private static MessageInstance resumeInstance(
		final MessageInstance instance,
		final ClockSample clock
	) {
		if (instance.status() != InstanceStatus.PAUSED) {
			return instance;
		}
		return copyInstance(
			instance,
			instance.resumeStatus().orElseThrow(),
			Optional.empty(),
			clock.value(instance.clockMode()),
			instance.accumulatedElapsedNanos(),
			instance.emittedOccurrences(),
			instance.completedTargets(),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
	}

	private static MessageInstance finishTerminalIfReady(
		final MessageInstance instance,
		final long elapsed,
		final Ledger ledger
	) {
		CallbackTrigger requiredTrigger;
		InstanceStatus terminal;
		if (instance.status() == InstanceStatus.COMPLETING) {
			requiredTrigger = CallbackTrigger.ALL_COMPLETE;
			terminal = InstanceStatus.COMPLETED;
		} else if (instance.status() == InstanceStatus.CANCELLING) {
			requiredTrigger = CallbackTrigger.INTERRUPT;
			terminal = InstanceStatus.CANCELLED;
		} else {
			return instance;
		}
		if (
			terminal == InstanceStatus.COMPLETED
				&& !instance.forcedCompletion()
		) {
			for (CueNode node : instance.cue().nodes()) {
				if (
					node.header().schedule() instanceof OnCueEvent
						|| (
							instance.context().externallyTimed()
								&& !(node instanceof CallbackNode)
						)
				) {
					continue;
				}
				for (
					int occurrence = 0;
					occurrence < node.header().repeat().count();
					occurrence++
				) {
					OccurrenceKey key = new OccurrenceKey(
						node.id(),
						occurrence,
						CallbackTrigger.START,
						Optional.empty()
					);
					if (!instance.emittedOccurrences().contains(key)) {
						return instance;
					}
				}
			}
		}
		for (CueNode node : instance.cue().nodes()) {
			if (
				node.header().schedule() instanceof OnCueEvent event
					&& event.trigger() == requiredTrigger
			) {
				for (int occurrence = 0; occurrence < node.header().repeat().count(); occurrence++) {
					OccurrenceKey key = new OccurrenceKey(
						node.id(),
						occurrence,
						requiredTrigger,
						Optional.empty()
					);
					if (!instance.emittedOccurrences().contains(key)) {
						return instance;
					}
					if (node instanceof CallbackNode callback) {
						CallbackKey callbackKey = new CallbackKey(
							instance.instanceId(),
							instance.cycle(),
							node.id(),
							occurrence,
							requiredTrigger,
							Optional.empty()
						);
						var callbackEntry = ledger.find(callbackKey);
						if (
							callbackEntry.isEmpty()
								|| callbackEntry.orElseThrow().status()
									== CallbackStatus.PREPARED
						) {
							/*
							 * Keep the instance in COMPLETING/CANCELLING until the integration
							 * has durably checkpointed PREPARED and then recorded an outcome.
							 * This is the crash boundary that prevents a terminal transition
							 * from discarding the only callback replay evidence.
							 */
							return instance;
						}
						if (!callbackEntry.orElseThrow().functionId().equals(callback.function())) {
							return instance;
						}
					}
				}
			}
		}
		return copyInstance(
			instance,
			terminal,
			Optional.empty(),
			instance.anchorClockNanos(),
			elapsed,
			instance.emittedOccurrences(),
			instance.completedTargets(),
			instance.eventOffsets(),
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
	}

	private static ProjectionEvent naturalCompletionProjection(
		final MessageInstance instance
	) {
		return new ProjectionEvent(
			ProjectionKind.FINISH,
			instance.instanceId(),
			instance.cycle(),
			Optional.empty(),
			0,
			instance.cueTargets(),
			Optional.empty(),
			Optional.empty(),
			"message instance reached natural server-authoritative completion"
		);
	}

	private static List<ScheduledOccurrence> dueOccurrences(
		final MessageInstance instance,
		final long elapsed
	) {
		TimelinePlan plan = timelinePlan(instance);
		List<ScheduledOccurrence> due = new ArrayList<>();
		for (CueNode node : instance.cue().nodes()) {
			if (node.header().schedule() instanceof OnCueEvent onEvent) {
				addEventOccurrences(instance, node, onEvent, elapsed, due);
				continue;
			}
			if (
				instance.context().externallyTimed()
					&& node instanceof CallbackNode
			) {
				continue;
			}
			if (
				instance.status() == InstanceStatus.CANCELLING
					|| (instance.status() == InstanceStatus.COMPLETING
						&& instance.forcedCompletion())
			) {
				continue;
			}
			long base = plan.startOffsets().get(node.id());
			addOccurrences(
				instance,
				node,
				CallbackTrigger.START,
				Optional.empty(),
				base,
				elapsed,
				recipients(instance, node, Optional.empty()),
				due
			);
		}
		due.sort(
			Comparator.comparingLong(ScheduledOccurrence::dueAt)
				.thenComparingInt(value -> nodeOrder(instance.cue(), value.node().id()))
				.thenComparingInt(value -> value.key().occurrence())
				.thenComparing(
					value -> value.key().targetId().map(UUID::toString).orElse("")
				)
		);
		return List.copyOf(due);
	}

	private static void addEventOccurrences(
		final MessageInstance instance,
		final CueNode node,
		final OnCueEvent event,
		final long elapsed,
		final List<ScheduledOccurrence> due
	) {
		if (
			instance.status() == InstanceStatus.CANCELLING
				&& event.trigger() != CallbackTrigger.INTERRUPT
		) {
			return;
		}
		if (
			instance.status() == InstanceStatus.COMPLETING
				&& event.trigger() == CallbackTrigger.INTERRUPT
		) {
			return;
		}
		if (event.trigger() == CallbackTrigger.TARGET_COMPLETE) {
			for (Map.Entry<EventKey, Long> entry : instance.eventOffsets().entrySet()) {
				if (entry.getKey().trigger() != CallbackTrigger.TARGET_COMPLETE) {
					continue;
				}
				UUID target = entry.getKey().targetId().orElseThrow();
				long base = safeAdd(entry.getValue(), event.offset().nanoseconds());
				addOccurrences(
					instance,
					node,
					event.trigger(),
					Optional.of(target),
					base,
					elapsed,
					recipients(instance, node, Optional.of(target)),
					due
				);
			}
			return;
		}
		EventKey key = new EventKey(event.trigger(), Optional.empty());
		Long eventAt = instance.eventOffsets().get(key);
		if (eventAt == null) {
			return;
		}
		long base = safeAdd(eventAt, event.offset().nanoseconds());
		addOccurrences(
			instance,
			node,
			event.trigger(),
			Optional.empty(),
			base,
			elapsed,
			recipients(instance, node, Optional.empty()),
			due
		);
	}

	private static void addOccurrences(
		final MessageInstance instance,
		final CueNode node,
		final CallbackTrigger trigger,
		final Optional<UUID> target,
		final long base,
		final long elapsed,
		final Set<UUID> recipients,
		final List<ScheduledOccurrence> due
	) {
		for (int occurrence = 0; occurrence < node.header().repeat().count(); occurrence++) {
			long dueAt = safeAdd(
				base,
				safeMultiply(
					node.header().repeat().interval().nanoseconds(),
					occurrence
				)
			);
			OccurrenceKey key = new OccurrenceKey(node.id(), occurrence, trigger, target);
			if (dueAt <= elapsed && !instance.emittedOccurrences().contains(key)) {
				due.add(new ScheduledOccurrence(key, node, dueAt, recipients));
			}
		}
	}

	private static Set<UUID> recipients(
		final MessageInstance instance,
		final CueNode node,
		final Optional<UUID> eventTarget
	) {
		Set<UUID> resolved = instance.nodeTargets().getOrDefault(
			node.id(),
			instance.cueTargets()
		);
		if (eventTarget.isEmpty()) {
			return resolved;
		}
		UUID target = eventTarget.orElseThrow();
		return resolved.contains(target) ? Set.of(target) : Set.of();
	}

	private static TimelinePlan timelinePlan(final MessageInstance instance) {
		Map<String, CueNode> nodes = new LinkedHashMap<>();
		for (CueNode node : instance.cue().nodes()) {
			nodes.put(node.id(), node);
		}
		Map<String, Long> offsets = new LinkedHashMap<>();
		Set<String> visiting = new LinkedHashSet<>();
		for (CueNode node : instance.cue().nodes()) {
			if (!(node.header().schedule() instanceof OnCueEvent)) {
				resolveStartOffset(
					node,
					nodes,
					instance.authoritativeNodeDurations(),
					offsets,
					visiting
				);
			}
		}
		Map<UUID, Long> targetOffsets = new LinkedHashMap<>();
		instance.cueTargets().forEach(target -> targetOffsets.put(target, 0L));
		long allStartWork = 0L;
		for (CueNode node : instance.cue().nodes()) {
			if (node.header().schedule() instanceof OnCueEvent) {
				continue;
			}
			long base = offsets.get(node.id());
			long lastStart = safeAdd(
				base,
				safeMultiply(
					node.header().repeat().interval().nanoseconds(),
					node.header().repeat().count() - 1L
				)
			);
			long end = safeAdd(
				lastStart,
				instance.authoritativeNodeDurations()
					.getOrDefault(node.id(), new TimeSpan(0L))
					.nanoseconds()
			);
			allStartWork = Math.max(allStartWork, end);
			if (node instanceof CallbackNode) {
				continue;
			}
			Set<UUID> recipients = instance.nodeTargets().getOrDefault(
				node.id(),
				instance.cueTargets()
			);
			for (UUID target : recipients) {
				if (targetOffsets.containsKey(target)) {
					targetOffsets.put(target, Math.max(targetOffsets.get(target), end));
				}
			}
		}
		return new TimelinePlan(offsets, targetOffsets, allStartWork);
	}

	private static long resolveStartOffset(
		final CueNode node,
		final Map<String, CueNode> nodes,
		final Map<String, TimeSpan> durations,
		final Map<String, Long> offsets,
		final Set<String> visiting
	) {
		Long existing = offsets.get(node.id());
		if (existing != null) {
			return existing;
		}
		if (!visiting.add(node.id())) {
			throw new IllegalArgumentException("message schedule contains a cycle");
		}
		long result;
		if (node.header().schedule() instanceof AtCueTime at) {
			result = at.offset().nanoseconds();
		} else if (node.header().schedule() instanceof AfterNode after) {
			CueNode dependency = nodes.get(after.nodeId());
			if (dependency == null || dependency.header().schedule() instanceof OnCueEvent) {
				throw new IllegalArgumentException(
					"after-node schedule references an unavailable start-timeline node"
				);
			}
			long dependencyStart = resolveStartOffset(
				dependency,
				nodes,
				durations,
				offsets,
				visiting
			);
			long dependencyLastStart = safeAdd(
				dependencyStart,
				safeMultiply(
					dependency.header().repeat().interval().nanoseconds(),
					dependency.header().repeat().count() - 1L
				)
			);
			result = safeAdd(
				safeAdd(
					dependencyLastStart,
					durations.getOrDefault(dependency.id(), new TimeSpan(0L)).nanoseconds()
				),
				after.offset().nanoseconds()
			);
		} else {
			throw new IllegalArgumentException("event schedule has no cue-start offset");
		}
		visiting.remove(node.id());
		offsets.put(node.id(), result);
		return result;
	}

	private static MessageInstance newInstance(
		final ResolvedInvocation resolved,
		final ClockSample clock,
		final long cycle
	) {
		MessageInstance instance = new MessageInstance(
			resolved.instanceId(),
			resolved.cue(),
			resolved.context(),
			resolved.canonicalParameters(),
			resolved.authoritativeNodeDurations(),
			resolved.cueTargets(),
			resolved.nodeTargets(),
			resolved.dedupeKey(),
			resolved.refreshKey(),
			resolved.clockMode(),
			cycle,
			InstanceStatus.ACTIVE,
			Optional.empty(),
			clock.value(resolved.clockMode()),
			0L,
			Set.of(),
			Set.of(),
			Map.of(),
			false,
			0L
		);
		TimelinePlan plan = timelinePlan(instance);
		if (plan.startOffsets().size() > HARD_MAX_OCCURRENCES) {
			throw new IllegalArgumentException("message timeline exceeds its occurrence limit");
		}
		instance.cue().softLimits().maxTotalDuration().ifPresent(maximum -> {
			if (plan.allStartWorkCompleteAt() > maximum.nanoseconds()) {
				throw new IllegalArgumentException(
					"message timeline exceeds configured max_total_duration"
				);
			}
		});
		if (instance.cueTargets().isEmpty()) {
			instance = addEvent(
				instance,
				new EventKey(CallbackTrigger.ALL_COMPLETE, Optional.empty()),
				plan.allStartWorkCompleteAt()
			);
			instance = copyInstance(
				instance,
				InstanceStatus.COMPLETING,
				Optional.empty(),
				instance.anchorClockNanos(),
				instance.accumulatedElapsedNanos(),
				instance.emittedOccurrences(),
				instance.completedTargets(),
				instance.eventOffsets(),
				false,
				increment(instance.instanceRevision())
			);
		}
		return instance;
	}

	private static MessageInstance addEvent(
		final MessageInstance instance,
		final EventKey key,
		final long elapsed
	) {
		if (instance.eventOffsets().containsKey(key)) {
			return instance;
		}
		Map<EventKey, Long> events = new LinkedHashMap<>(instance.eventOffsets());
		events.put(key, elapsed);
		return copyInstance(
			instance,
			instance.status(),
			instance.resumeStatus(),
			instance.anchorClockNanos(),
			instance.accumulatedElapsedNanos(),
			instance.emittedOccurrences(),
			instance.completedTargets(),
			events,
			instance.forcedCompletion(),
			increment(instance.instanceRevision())
		);
	}

	private static MessageInstance copyInstance(
		final MessageInstance source,
		final InstanceStatus status,
		final Optional<InstanceStatus> resumeStatus,
		final long anchorClockNanos,
		final long accumulatedElapsedNanos,
		final Set<OccurrenceKey> emitted,
		final Set<UUID> completedTargets,
		final Map<EventKey, Long> eventOffsets,
		final boolean forcedCompletion,
		final long instanceRevision
	) {
		return new MessageInstance(
			source.instanceId(),
			source.cue(),
			source.context(),
			source.canonicalParameters(),
			source.authoritativeNodeDurations(),
			source.cueTargets(),
			source.nodeTargets(),
			source.dedupeKey(),
			source.refreshKey(),
			source.clockMode(),
			source.cycle(),
			status,
			resumeStatus,
			anchorClockNanos,
			accumulatedElapsedNanos,
			emitted,
			completedTargets,
			eventOffsets,
			forcedCompletion,
			instanceRevision
		);
	}

	private static RuntimeState copyState(
		final RuntimeState source,
		final long revision,
		final Map<UUID, MessageInstance> instances,
		final List<QueuedInvocation> queue,
		final Map<RefreshKey, UUID> refreshIndex,
		final Map<DedupeKey, CooldownStamp> cooldowns,
		final Ledger ledger
	) {
		return new RuntimeState(
			revision,
			instances,
			queue,
			rebuildActiveDedupe(instances),
			pruneRefreshIndex(refreshIndex, instances),
			cooldowns,
			ledger
		);
	}

	private static Map<DedupeKey, UUID> rebuildActiveDedupe(
		final Map<UUID, MessageInstance> instances
	) {
		Map<DedupeKey, UUID> active = new LinkedHashMap<>();
		for (MessageInstance instance : instances.values()) {
			if (
				instance.status().active()
					&& instance.cue().policies().concurrency().duplicate()
						!= DuplicateMode.ALLOW
			) {
				active.put(instance.dedupeKey(), instance.instanceId());
			}
		}
		return active;
	}

	private static Map<RefreshKey, UUID> pruneRefreshIndex(
		final Map<RefreshKey, UUID> refresh,
		final Map<UUID, MessageInstance> instances
	) {
		Map<RefreshKey, UUID> next = new LinkedHashMap<>();
		refresh.forEach((key, value) -> {
			MessageInstance instance = instances.get(value);
			if (instance != null && instance.refreshKey().filter(key::equals).isPresent()) {
				next.put(key, value);
			}
		});
		for (MessageInstance instance : instances.values()) {
			instance.refreshKey().ifPresent(key -> next.put(key, instance.instanceId()));
		}
		return next;
	}

	private static DedupeKey dedupeKey(
		final MessageCueDefinition cue,
		final Set<UUID> targets,
		final Map<String, String> parameters
	) {
		Map<String, String> selected = new LinkedHashMap<>();
		Set<String> configured = new LinkedHashSet<>(
			cue.policies().concurrency().dedupeParameters()
		);
		cue.parameters().forEach((key, definition) -> {
			if (definition.dedupe()) {
				configured.add(key);
			}
		});
		for (String key : configured.stream().sorted().toList()) {
			String value = parameters.get(key);
			if (value != null) {
				selected.put(key, value);
			}
		}
		return new DedupeKey(
			cue.id(),
			cue.policies().concurrency().dedupeTargets() ? targets : Set.of(),
			selected
		);
	}

	private static Optional<RefreshKey> refreshKey(
		final MessageCueDefinition cue,
		final Map<String, String> parameters
	) {
		Optional<String> parameter = cue.policies().concurrency().refreshKey();
		if (parameter.isEmpty()) {
			return Optional.empty();
		}
		String value = parameters.get(parameter.orElseThrow());
		if (value == null) {
			throw new IllegalArgumentException(
				"registered refresh-key parameter has no canonical value"
			);
		}
		return Optional.of(new RefreshKey(cue.id(), parameter.orElseThrow(), value));
	}

	private static Map<DedupeKey, CooldownStamp> withCooldown(
		final Map<DedupeKey, CooldownStamp> existing,
		final MessageInstance instance,
		final ClockSample clock
	) {
		return withCooldown(
			existing,
			instance.dedupeKey(),
			instance.cue(),
			instance.clockMode(),
			clock
		);
	}

	private static Map<DedupeKey, CooldownStamp> withCooldown(
		final Map<DedupeKey, CooldownStamp> existing,
		final QueuedInvocation invocation,
		final ClockSample clock
	) {
		return withCooldown(
			existing,
			invocation.dedupeKey(),
			invocation.cue(),
			invocation.clockMode(),
			clock
		);
	}

	private static Map<DedupeKey, CooldownStamp> withCooldown(
		final Map<DedupeKey, CooldownStamp> existing,
		final DedupeKey key,
		final MessageCueDefinition cue,
		final ClockMode mode,
		final ClockSample clock
	) {
		if (cue.policies().concurrency().cooldown().isEmpty()) {
			return existing;
		}
		Map<DedupeKey, CooldownStamp> next = new LinkedHashMap<>(existing);
		if (!next.containsKey(key) && next.size() >= HARD_MAX_COOLDOWNS) {
			throw new IllegalArgumentException("message cooldown table reached its hard capacity");
		}
		long expires = safeAdd(
			clock.value(mode),
			cue.policies().concurrency().cooldown().orElseThrow().nanoseconds()
		);
		next.put(key, new CooldownStamp(mode, expires));
		return next;
	}

	private static String contextError(
		final MessageCueDefinition cue,
		final InvocationContext context
	) {
		if (context.bypassContext()) {
			return null;
		}
		if (cue.game().isPresent() && !cue.game().equals(context.gameId())) {
			return "message cue does not belong to the active game";
		}
		if (
			!matchesAllowed(cue.policies().context().games(), context.gameId())
				|| !matchesAllowed(cue.policies().context().phases(), context.phaseId())
				|| !matchesAllowed(cue.policies().context().tasks(), context.taskId())
		) {
			return "message invocation is outside its registered game context";
		}
		return null;
	}

	private static boolean matchesAllowed(
		final Set<Identifier> allowed,
		final Optional<Identifier> actual
	) {
		return allowed.isEmpty() || actual.filter(allowed::contains).isPresent();
	}

	private static String validateParameters(
		final MessageCueDefinition cue,
		final Map<String, String> parameters,
		final Map<String, TimeSpan> durations
	) {
		try {
			boundedStringMap(parameters, "canonical parameters");
			boundedDurationMap(durations, cue);
		} catch (RuntimeException error) {
			return safeMessage(error);
		}
		for (String key : parameters.keySet()) {
			if (!cue.parameters().containsKey(key)) {
				return "parameter resolver returned undeclared parameter " + key;
			}
		}
		for (Map.Entry<String, ParameterDefinition> entry : cue.parameters().entrySet()) {
			if (entry.getValue().required() && !parameters.containsKey(entry.getKey())) {
				return "required canonical parameter is missing: " + entry.getKey();
			}
		}
		for (CueNode node : cue.nodes()) {
			if (
				node instanceof TextNode text
					&& text.content().duration().mode()
						== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DurationMode.CONTENT_DRIVEN
					&& !durations.containsKey(node.id())
			) {
				return "content-driven text node has no authoritative duration: " + node.id();
			}
		}
		return null;
	}

	/**
	 * Expands the top-level audience into the complete set of players affected by at least one
	 * node while retaining an exact target set for every node whose audience differs from that
	 * union.
	 *
	 * <p>The runtime uses {@code cueTargets} as its completion population. Without this
	 * normalization, a node-level override could render to a player who is absent from target
	 * completion, allowing {@code all_complete} to fire before that player's content ends.
	 */
	private static AudienceResolution normalizedAudience(
		final MessageCueDefinition cue,
		final AudienceResolution source
	) {
		if (!source.resolved()) {
			return source;
		}
		Set<UUID> base = source.cueTargets();
		Map<String, Set<UUID>> exactByNode = new LinkedHashMap<>();
		Set<UUID> affected = new LinkedHashSet<>(base);
		for (CueNode node : cue.nodes()) {
			Set<UUID> exact = source.nodeTargets().getOrDefault(node.id(), base);
			exactByNode.put(node.id(), Set.copyOf(exact));
			affected.addAll(exact);
		}
		Set<UUID> union = Set.copyOf(affected);
		Map<String, Set<UUID>> overrides = new LinkedHashMap<>();
		for (CueNode node : cue.nodes()) {
			Set<UUID> exact = exactByNode.get(node.id());
			if (!exact.equals(union)) {
				overrides.put(node.id(), exact);
			}
		}
		return AudienceResolution.resolved(union, overrides);
	}

	private static String validateAudience(
		final MessageCueDefinition cue,
		final AudienceResolution audience
	) {
		try {
			boundedUuidSet(audience.cueTargets(), HARD_MAX_TARGETS, "cue targets");
			boundedNodeTargets(audience.nodeTargets(), cue);
		} catch (RuntimeException error) {
			return safeMessage(error);
		}
		for (String nodeId : audience.nodeTargets().keySet()) {
			if (cue.nodes().stream().noneMatch(node -> node.id().equals(nodeId))) {
				return "audience resolver returned targets for undeclared node " + nodeId;
			}
		}
		return null;
	}

	private static ClockMode effectiveClock(final MessageCueDefinition cue) {
		return cue.policies().timing().clock().orElseGet(
			() -> cue.nodes().stream().anyMatch(CallbackNode.class::isInstance)
				? ClockMode.GAME_TIME
				: ClockMode.PRESENTATION_TIME
		);
	}

	private static int activeLimit(final MessageCueDefinition cue) {
		return Math.min(
			HARD_MAX_INSTANCES,
			cue.softLimits().maxActiveInstances().orElse(HARD_MAX_INSTANCES)
		);
	}

	private static int queueLimit(final MessageCueDefinition cue) {
		return Math.min(
			HARD_MAX_QUEUE,
			cue.softLimits().maxQueueDepth().orElse(HARD_MAX_QUEUE)
		);
	}

	private static String persistentReservationError(
		final RuntimeState state,
		final MessageCueDefinition cue,
		final Optional<UUID> replacedInstanceId
	) {
		if (cue.policies().lifecycle().restart() == RestartMode.TRANSIENT) {
			return null;
		}
		int limit = Math.min(
			HARD_MAX_INSTANCES,
			cue.softLimits().maxPersistedInstances().orElse(HARD_MAX_INSTANCES)
		);
		long reserved = state.instances().values().stream()
			.filter(instance ->
				instance.status().active()
					&& instance.cue().id().equals(cue.id())
					&& instance.cue().policies().lifecycle().restart()
						!= RestartMode.TRANSIENT
					&& replacedInstanceId
						.map(replaced -> !instance.instanceId().equals(replaced))
						.orElse(true)
			)
			.count();
		reserved += state.queue().stream()
			.filter(invocation ->
				invocation.cue().id().equals(cue.id())
					&& invocation.cue().policies().lifecycle().restart()
						!= RestartMode.TRANSIENT
			)
			.count();
		return reserved >= limit
			? "message cue reached its configured persistent-instance limit"
			: null;
	}

	private static long activeCueCount(
		final RuntimeState state,
		final Identifier cueId
	) {
		return state.instances().values().stream()
			.filter(instance -> instance.status().active() && instance.cue().id().equals(cueId))
			.count();
	}

	private static boolean reservedInstanceId(
		final RuntimeState state,
		final UUID instanceId
	) {
		return state.instances().containsKey(instanceId)
			|| state.queue().stream().anyMatch(item -> item.instanceId().equals(instanceId));
	}

	private static ProjectionEvent controlProjection(
		final ControlAction action,
		final MessageInstance instance,
		final String reason
	) {
		ProjectionKind kind = switch (action) {
			case PAUSE -> ProjectionKind.PAUSE;
			case RESUME -> ProjectionKind.RESUME;
			case COMPLETE -> ProjectionKind.COMPLETE;
			case CANCEL -> ProjectionKind.CANCEL;
		};
		return new ProjectionEvent(
			kind,
			instance.instanceId(),
			instance.cycle(),
			Optional.empty(),
			0,
			instance.cueTargets(),
			Optional.empty(),
			Optional.empty(),
			reason
		);
	}

	private static long latestTargetEvent(final MessageInstance instance) {
		long latest = 0L;
		for (Map.Entry<EventKey, Long> entry : instance.eventOffsets().entrySet()) {
			if (entry.getKey().trigger() == CallbackTrigger.TARGET_COMPLETE) {
				latest = Math.max(latest, entry.getValue());
			}
		}
		return latest;
	}

	private static int nodeOrder(
		final MessageCueDefinition cue,
		final String nodeId
	) {
		for (int index = 0; index < cue.nodes().size(); index++) {
			if (cue.nodes().get(index).id().equals(nodeId)) {
				return index;
			}
		}
		return Integer.MAX_VALUE;
	}

	private static PlayResult rejectedPlay(
		final RuntimeState state,
		final String message
	) {
		return new PlayResult(
			PlayDisposition.REJECTED,
			message,
			state,
			Optional.empty(),
			List.of(),
			List.of()
		);
	}

	private static PlayResult ignoredPlay(
		final RuntimeState state,
		final String message
	) {
		return new PlayResult(
			PlayDisposition.IGNORED,
			message,
			state,
			Optional.empty(),
			List.of(),
			List.of()
		);
	}

	private static long increment(final long value) {
		return Math.incrementExact(value);
	}

	private static long safeAdd(final long left, final long right) {
		return Math.addExact(left, right);
	}

	private static long safeMultiply(final long value, final long count) {
		return Math.multiplyExact(value, count);
	}

	private static String boundedReason(final String value) {
		String reason = Objects.requireNonNull(value, "reason").strip();
		if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
			throw new IllegalArgumentException(
				"message reason must contain 1.." + MAX_REASON_LENGTH + " characters"
			);
		}
		return reason;
	}

	private static String boundedToken(final String value, final String label) {
		String token = Objects.requireNonNull(value, label).strip();
		if (token.isEmpty() || token.length() > 128) {
			throw new IllegalArgumentException(label + " must contain 1..128 characters");
		}
		return token;
	}

	private static String boundedValue(final String value, final String label) {
		String bounded = Objects.requireNonNull(value, label);
		if (bounded.length() > MAX_ARGUMENT_LENGTH) {
			throw new IllegalArgumentException(
				label + " exceeds " + MAX_ARGUMENT_LENGTH + " characters"
			);
		}
		return bounded;
	}

	private static Map<String, String> boundedStringMap(
		final Map<String, String> values,
		final String label
	) {
		Objects.requireNonNull(values, label);
		if (values.size() > HARD_MAX_PARAMETERS) {
			throw new IllegalArgumentException(label + " exceeds its entry limit");
		}
		Map<String, String> copied = new LinkedHashMap<>();
		int total = 0;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = boundedToken(entry.getKey(), label + " key");
			String value = boundedValue(entry.getValue(), label + " value");
			total = Math.addExact(total, Math.addExact(key.length(), value.length()));
			if (total > MAX_TOTAL_ARGUMENT_CHARACTERS) {
				throw new IllegalArgumentException(label + " exceeds its total character limit");
			}
			copied.put(key, value);
		}
		return Map.copyOf(copied);
	}

	private static Map<String, TimeSpan> boundedDurationMap(
		final Map<String, TimeSpan> values,
		final MessageCueDefinition cue
	) {
		Objects.requireNonNull(values, "authoritative node durations");
		if (values.size() > cue.nodes().size()) {
			throw new IllegalArgumentException(
				"authoritative node durations contain too many entries"
			);
		}
		Set<String> nodeIds = new LinkedHashSet<>();
		cue.nodes().forEach(node -> nodeIds.add(node.id()));
		Map<String, TimeSpan> copied = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			String boundedKey = boundedToken(key, "duration node id");
			if (!nodeIds.contains(boundedKey)) {
				throw new IllegalArgumentException(
					"authoritative duration references undeclared node " + boundedKey
				);
			}
			copied.put(
				boundedKey,
				Objects.requireNonNull(value, "authoritative node duration")
			);
		});
		return Map.copyOf(copied);
	}

	private static Set<UUID> boundedUuidSet(
		final Set<UUID> values,
		final int maximum,
		final String label
	) {
		Objects.requireNonNull(values, label);
		if (values.size() > maximum) {
			throw new IllegalArgumentException(label + " exceeds its entry limit");
		}
		Set<UUID> copied = new LinkedHashSet<>();
		for (UUID value : values) {
			copied.add(Objects.requireNonNull(value, label + " value"));
		}
		return Set.copyOf(copied);
	}

	private static Map<String, Set<UUID>> boundedNodeTargets(
		final Map<String, Set<UUID>> values,
		final MessageCueDefinition cue
	) {
		Objects.requireNonNull(values, "nodeTargets");
		if (values.size() > cue.nodes().size()) {
			throw new IllegalArgumentException("node target map has too many entries");
		}
		int total = 0;
		Map<String, Set<UUID>> copied = new LinkedHashMap<>();
		for (Map.Entry<String, Set<UUID>> entry : values.entrySet()) {
			String key = boundedToken(entry.getKey(), "node target id");
			Set<UUID> targets = boundedUuidSet(
				entry.getValue(),
				HARD_MAX_TARGETS,
				"node targets"
			);
			total = Math.addExact(total, targets.size());
			if (total > HARD_MAX_TOTAL_NODE_TARGETS) {
				throw new IllegalArgumentException("node target map exceeds its total target limit");
			}
			copied.put(key, targets);
		}
		return Map.copyOf(copied);
	}

	private static Map<EventKey, Long> immutableEventMap(
		final Map<EventKey, Long> values
	) {
		Objects.requireNonNull(values, "eventOffsets");
		if (values.size() > HARD_MAX_TARGETS + 2) {
			throw new IllegalArgumentException("message event map exceeds its bounded capacity");
		}
		Map<EventKey, Long> copied = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			Objects.requireNonNull(key, "event key");
			if (value == null || value < 0L) {
				throw new IllegalArgumentException("message event offset cannot be negative");
			}
			copied.put(key, value);
		});
		return Map.copyOf(copied);
	}

	private static <K, V> Map<K, V> immutableMap(
		final Map<K, V> values,
		final String label
	) {
		Objects.requireNonNull(values, label);
		Map<K, V> copied = new LinkedHashMap<>();
		values.forEach(
			(key, value) -> copied.put(
				Objects.requireNonNull(key, label + " key"),
				Objects.requireNonNull(value, label + " value")
			)
		);
		return Map.copyOf(copied);
	}

	private static String safeMessage(final Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			message = error.getClass().getSimpleName();
		}
		return message.length() <= MAX_REASON_LENGTH
			? message
			: message.substring(0, MAX_REASON_LENGTH);
	}

	private static <T> List<T> concat(final List<T> first, final List<T> second) {
		if (first.isEmpty()) {
			return List.copyOf(second);
		}
		if (second.isEmpty()) {
			return List.copyOf(first);
		}
		List<T> result = new ArrayList<>(first.size() + second.size());
		result.addAll(first);
		result.addAll(second);
		return List.copyOf(result);
	}

	private record ResolvedInvocation(
		UUID instanceId,
		MessageCueDefinition cue,
		InvocationContext context,
		Map<String, String> canonicalParameters,
		Map<String, TimeSpan> authoritativeNodeDurations,
		Set<UUID> cueTargets,
		Map<String, Set<UUID>> nodeTargets,
		DedupeKey dedupeKey,
		Optional<RefreshKey> refreshKey,
		ClockMode clockMode
	) {
		private ResolvedInvocation {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			cue = Objects.requireNonNull(cue, "cue");
			context = Objects.requireNonNull(context, "context");
			canonicalParameters = boundedStringMap(
				canonicalParameters,
				"resolved canonical parameters"
			);
			authoritativeNodeDurations = boundedDurationMap(
				authoritativeNodeDurations,
				cue
			);
			cueTargets = boundedUuidSet(cueTargets, HARD_MAX_TARGETS, "resolved cue targets");
			nodeTargets = boundedNodeTargets(nodeTargets, cue);
			dedupeKey = Objects.requireNonNull(dedupeKey, "dedupeKey");
			refreshKey = Objects.requireNonNull(refreshKey, "refreshKey");
			clockMode = Objects.requireNonNull(clockMode, "clockMode");
		}
	}

	private record TimelinePlan(
		Map<String, Long> startOffsets,
		Map<UUID, Long> targetCompletionOffsets,
		long allStartWorkCompleteAt
	) {
		private TimelinePlan {
			startOffsets = Map.copyOf(startOffsets);
			targetCompletionOffsets = Map.copyOf(targetCompletionOffsets);
			if (allStartWorkCompleteAt < 0L) {
				throw new IllegalArgumentException("message timeline completion cannot be negative");
			}
		}
	}

	private record ScheduledOccurrence(
		OccurrenceKey key,
		CueNode node,
		long dueAt,
		Set<UUID> recipients
	) {
		private ScheduledOccurrence {
			key = Objects.requireNonNull(key, "key");
			node = Objects.requireNonNull(node, "node");
			recipients = boundedUuidSet(recipients, HARD_MAX_TARGETS, "node recipients");
			if (dueAt < 0L) {
				throw new IllegalArgumentException("node due time cannot be negative");
			}
		}
	}

	private record InstanceAdvance(
		boolean accepted,
		String message,
		MessageInstance instance,
		Ledger ledger,
		List<ProjectionEvent> projections,
		List<PreparedCallback> callbacks
	) {
		private InstanceAdvance {
			message = boundedReason(message);
			instance = Objects.requireNonNull(instance, "instance");
			ledger = Objects.requireNonNull(ledger, "ledger");
			projections = List.copyOf(projections);
			callbacks = List.copyOf(callbacks);
		}

		private static InstanceAdvance unchanged(
			final MessageInstance instance,
			final Ledger ledger
		) {
			return new InstanceAdvance(
				true,
				"message instance has no due work",
				instance,
				ledger,
				List.of(),
				List.of()
			);
		}

		private static InstanceAdvance changed(
			final MessageInstance instance,
			final Ledger ledger,
			final List<ProjectionEvent> projections,
			final List<PreparedCallback> callbacks
		) {
			return new InstanceAdvance(
				true,
				"message instance advanced",
				instance,
				ledger,
				projections,
				callbacks
			);
		}

		private static InstanceAdvance rejected(
			final String message,
			final MessageInstance instance,
			final Ledger ledger
		) {
			return new InstanceAdvance(
				false,
				message,
				instance,
				ledger,
				List.of(),
				List.of()
			);
		}
	}
}
