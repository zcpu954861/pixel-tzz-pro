package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DeliveryPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EmptyAudienceMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LateJoinMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ReconnectMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-message server authority for offline delivery, reconnect, late join, and offline TTL.
 *
 * <p>This class never renders, sends packets, or runs callbacks. It emits bounded directives for
 * the runtime. Every reconnect/late-join directive explicitly forbids replaying server callbacks;
 * continue additionally seeks without replaying sounds whose server time has already passed.
 */
public final class MessageDeliveryLifecycleAuthority {
	public static final int MAX_TARGETS = 4_096;
	public static final int MAX_PENDING_OFFLINE = 4_096;
	public static final int MAX_DIAGNOSTICS = 64;
	public static final long MAX_OFFLINE_TTL_NANOS = Duration.ofDays(7L).toNanos();

	private static final Comparator<UUID> TARGET_ORDER = Comparator
		.comparingLong(UUID::getMostSignificantBits)
		.thenComparingLong(UUID::getLeastSignificantBits);

	private final DeliveryPolicy delivery;
	private final LifecyclePolicy lifecycle;
	private final AudienceEvolution audienceEvolution;
	private final Set<UUID> originalTargets;
	private final Set<UUID> skippedTargets;
	private final Set<UUID> settledTargets = new LinkedHashSet<>();
	private final Set<UUID> knownLiveTargets = new LinkedHashSet<>();
	private final Set<UUID> activeTargets = new LinkedHashSet<>();
	private final Map<UUID, PendingTarget> pendingTargets = new LinkedHashMap<>();
	private final InitialDecision initialDecision;
	private boolean terminal;

	private MessageDeliveryLifecycleAuthority(
		final DeliveryPolicy delivery,
		final LifecyclePolicy lifecycle,
		final AudienceEvolution audienceEvolution,
		final Set<UUID> originalTargets,
		final Set<UUID> onlineTargets,
		final Optional<Integer> configuredPendingLimit,
		final long nowNanos
	) {
		this.delivery = Objects.requireNonNull(delivery, "delivery");
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
		this.audienceEvolution = Objects.requireNonNull(
			audienceEvolution,
			"audienceEvolution"
		);
		this.originalTargets = immutableTargets(originalTargets, "originalTargets");
		Set<UUID> online = immutableTargets(onlineTargets, "onlineTargets");
		if (!this.originalTargets.containsAll(online)) {
			throw new IllegalArgumentException(
				"onlineTargets must be a subset of originalTargets"
			);
		}
		if (nowNanos < 0L) {
			throw new IllegalArgumentException("nowNanos must be non-negative");
		}

		InitialBuild build = buildInitial(
			this.delivery,
			this.originalTargets,
			online,
			configuredPendingLimit,
			nowNanos
		);
		this.initialDecision = build.decision;
		this.skippedTargets = build.decision.skipped();
		this.activeTargets.addAll(build.decision.onlineStart());
		this.pendingTargets.putAll(build.pending);
		this.terminal = switch (build.decision.instanceDirective()) {
			case FAIL, COMPLETE, CALLBACKS_ONLY -> true;
			case ACTIVE, WAIT_PENDING, NONE -> false;
		};
	}

	private MessageDeliveryLifecycleAuthority(
		final DeliveryPolicy delivery,
		final LifecyclePolicy lifecycle,
		final AudienceEvolution audienceEvolution,
		final RestoredState restored
	) {
		this.delivery = Objects.requireNonNull(delivery, "delivery");
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
		this.audienceEvolution = Objects.requireNonNull(
			audienceEvolution,
			"audienceEvolution"
		);
		RestoredState state = Objects.requireNonNull(restored, "restored");
		this.originalTargets = state.originalTargets();
		this.skippedTargets = state.skippedTargets();
		this.activeTargets.addAll(state.activeTargets());
		this.settledTargets.addAll(state.settledTargets());
		this.pendingTargets.putAll(state.pendingTargets());
		/* Recovery never re-emits initial delivery directives. */
		this.initialDecision = new InitialDecision(
			InstanceDirective.NONE,
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Map.of(),
			List.of()
		);
		this.terminal = this.activeTargets.isEmpty() && this.pendingTargets.isEmpty();
	}

	public static MessageDeliveryLifecycleAuthority begin(
		final MessageCueDefinition cue,
		final Set<UUID> originalTargets,
		final Set<UUID> onlineTargets,
		final long nowNanos
	) {
		Objects.requireNonNull(cue, "cue");
		return begin(
			cue.policies().delivery(),
			cue.policies().lifecycle(),
			cue.policies().audience().evolution(),
			cue.softLimits().maxPendingOffline(),
			originalTargets,
			onlineTargets,
			nowNanos
		);
	}

	public static MessageDeliveryLifecycleAuthority begin(
		final DeliveryPolicy delivery,
		final LifecyclePolicy lifecycle,
		final AudienceEvolution audienceEvolution,
		final Optional<Integer> maxPendingOffline,
		final Set<UUID> originalTargets,
		final Set<UUID> onlineTargets,
		final long nowNanos
	) {
		return new MessageDeliveryLifecycleAuthority(
			delivery,
			lifecycle,
			audienceEvolution,
			originalTargets,
			onlineTargets,
			maxPendingOffline,
			nowNanos
		);
	}

	/**
	 * Restores one non-rendering delivery authority from a stable checkpoint.
	 *
	 * <p>All mutable recipient partitions are supplied explicitly and are validated before any
	 * state is constructed. A restored authority emits no initial directives; callers resume their
	 * own recipient streams and then use {@link #onJoin(UUID, boolean, long)}, {@link #expire(long,
	 * Set)}, and {@link #markDeliveryComplete(UUID)} exactly as they would after {@link #begin}.
	 */
	public static MessageDeliveryLifecycleAuthority restore(
		final MessageCueDefinition cue,
		final Set<UUID> originalTargets,
		final Set<UUID> activeTargets,
		final Set<UUID> settledTargets,
		final Collection<PendingState> pendingStates,
		final long nowNanos
	) {
		Objects.requireNonNull(cue, "cue");
		return new MessageDeliveryLifecycleAuthority(
			cue.policies().delivery(),
			cue.policies().lifecycle(),
			cue.policies().audience().evolution(),
			buildRestoredState(
				cue.policies().delivery(),
				cue.softLimits().maxPendingOffline(),
				originalTargets,
				activeTargets,
				settledTargets,
				pendingStates,
				nowNanos
			)
		);
	}

	public InitialDecision initialDecision() {
		return this.initialDecision;
	}

	public synchronized int pendingCount() {
		return this.pendingTargets.size();
	}

	public synchronized int activeCount() {
		return this.activeTargets.size();
	}

	/** Returns a deterministic immutable checkpoint of every pending offline target. */
	public synchronized List<PendingState> pendingSnapshot() {
		return this.pendingTargets.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey(TARGET_ORDER))
			.map(entry -> new PendingState(
				entry.getKey(),
				entry.getValue().mode(),
				entry.getValue().expiresAtNanos()
			))
			.toList();
	}

	/**
	 * Resolves a join as either an original-target reconnect or a new live audience member.
	 * {@code currentlyEligible} must come from a fresh server-side audience evaluation.
	 */
	public synchronized JoinDecision onJoin(
		final UUID target,
		final boolean currentlyEligible,
		final long nowNanos
	) {
		Objects.requireNonNull(target, "target");
		if (nowNanos < 0L) {
			throw new IllegalArgumentException("nowNanos must be non-negative");
		}
		if (this.terminal || this.settledTargets.contains(target)) {
			return joinDrop(target, JoinKind.NOT_DELIVERABLE);
		}
		if (this.skippedTargets.contains(target)) {
			this.settledTargets.add(target);
			return joinDrop(target, JoinKind.ORIGINAL_RECONNECT);
		}

		PendingTarget pending = this.pendingTargets.get(target);
		if (pending != null && nowNanos >= pending.expiresAtNanos) {
			this.pendingTargets.remove(target);
			this.settledTargets.add(target);
			boolean complete = completeIfIdle();
			return new JoinDecision(
				target,
				JoinKind.ORIGINAL_RECONNECT,
				DeliveryDirective.EXPIRE_FINAL_AND_REMOVE,
				true,
				complete ? InstanceDirective.COMPLETE : InstanceDirective.NONE,
				List.of(
					diagnostic(
						DiagnosticCode.OFFLINE_TTL_EXPIRED,
						"离线消息等待已到期，目标仅接收最终内容",
						target
					)
				)
			);
		}

		if (pending != null) {
			this.pendingTargets.remove(target);
			if (pending.mode == PendingMode.FINAL_ONLY) {
				this.settledTargets.add(target);
				return new JoinDecision(
					target,
					JoinKind.ORIGINAL_RECONNECT,
					DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY,
					true,
					completeIfIdle()
						? InstanceDirective.COMPLETE
						: InstanceDirective.NONE,
					List.of()
				);
			}
			return applyReconnect(target, true);
		}

		if (this.originalTargets.contains(target)) {
			return applyReconnect(target, false);
		}
		if (
			!currentlyEligible
				|| this.audienceEvolution == AudienceEvolution.SNAPSHOT
				|| this.knownLiveTargets.contains(target)
		) {
			return joinDrop(target, JoinKind.NOT_DELIVERABLE);
		}
		if (this.knownLiveTargets.size() + this.originalTargets.size() >= MAX_TARGETS) {
			return new JoinDecision(
				target,
				JoinKind.LIVE_NEW_TARGET,
				DeliveryDirective.DROP,
				false,
				InstanceDirective.NONE,
				List.of(
					diagnostic(
						DiagnosticCode.TARGET_LIMIT_EXCEEDED,
						"消息实时受众超过上限 " + MAX_TARGETS,
						target
					)
				)
			);
		}
		this.knownLiveTargets.add(target);
		return applyLateJoin(target);
	}

	/** Removes a recipient whose client-side delivery has reached its terminal frame. */
	public synchronized CompletionDecision markDeliveryComplete(final UUID target) {
		Objects.requireNonNull(target, "target");
		boolean removed = this.activeTargets.remove(target);
		if (removed) {
			this.settledTargets.add(target);
		}
		boolean complete = removed && completeIfIdle();
		return new CompletionDecision(
			target,
			removed,
			complete ? InstanceDirective.COMPLETE : InstanceDirective.NONE
		);
	}

	/**
	 * Expires every due offline target. An online target receives only the authorized final frame;
	 * an offline target is dropped. Both are removed from the pending set. Completion is explicit.
	 */
	public synchronized ExpiryDecision expire(
		final long nowNanos,
		final Set<UUID> onlineNow
	) {
		if (nowNanos < 0L) {
			throw new IllegalArgumentException("nowNanos must be non-negative");
		}
		Set<UUID> online = immutableTargets(onlineNow, "onlineNow");
		List<UUID> due = this.pendingTargets.entrySet()
			.stream()
			.filter(entry -> nowNanos >= entry.getValue().expiresAtNanos)
			.map(Map.Entry::getKey)
			.sorted(TARGET_ORDER)
			.toList();
		Map<UUID, DeliveryDirective> directives = new LinkedHashMap<>();
		Set<UUID> removed = new LinkedHashSet<>();
		Set<UUID> finalTargets = new LinkedHashSet<>();
		Set<UUID> dropped = new LinkedHashSet<>();
		for (UUID target : due) {
			this.pendingTargets.remove(target);
			this.settledTargets.add(target);
			removed.add(target);
			if (online.contains(target)) {
				finalTargets.add(target);
				directives.put(
					target,
					DeliveryDirective.EXPIRE_FINAL_AND_REMOVE
				);
			} else {
				dropped.add(target);
				directives.put(
					target,
					DeliveryDirective.EXPIRE_DROP_AND_REMOVE
				);
			}
		}
		boolean complete = !due.isEmpty() && completeIfIdle();
		return new ExpiryDecision(
			removed,
			finalTargets,
			dropped,
			directives,
			complete ? InstanceDirective.COMPLETE : InstanceDirective.NONE
		);
	}

	private JoinDecision applyReconnect(
		final UUID target,
		final boolean removedFromPending
	) {
		// A reconnect replaces any delivery that belonged to the previous connection.
		// CONTINUE/RESTART add the target back below; FINAL_ONLY/DROP intentionally do not.
		this.activeTargets.remove(target);
		DeliveryDirective directive = switch (this.lifecycle.reconnect()) {
			case CONTINUE -> DeliveryDirective.SEEK_NO_PAST_SOUNDS;
			case RESTART ->
				DeliveryDirective.RESTART_NEW_VISUAL_ANCHOR_NO_CALLBACK_REPLAY;
			case FINAL_ONLY -> DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY;
			case DROP -> DeliveryDirective.DROP;
		};
		trackJoinDirective(target, directive);
		return new JoinDecision(
			target,
			JoinKind.ORIGINAL_RECONNECT,
			directive,
			removedFromPending,
			completeIfIdle() ? InstanceDirective.COMPLETE : InstanceDirective.NONE,
			List.of()
		);
	}

	private JoinDecision applyLateJoin(final UUID target) {
		DeliveryDirective directive = switch (this.lifecycle.lateJoin()) {
			case CONTINUE -> DeliveryDirective.SEEK_NO_PAST_SOUNDS;
			case FROM_START ->
				DeliveryDirective.FROM_START_NEW_VISUAL_ANCHOR_NO_CALLBACK_REPLAY;
			case FINAL_ONLY -> DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY;
			case DROP -> DeliveryDirective.DROP;
		};
		trackJoinDirective(target, directive);
		return new JoinDecision(
			target,
			JoinKind.LIVE_NEW_TARGET,
			directive,
			false,
			completeIfIdle() ? InstanceDirective.COMPLETE : InstanceDirective.NONE,
			List.of()
		);
	}

	private void trackJoinDirective(
		final UUID target,
		final DeliveryDirective directive
	) {
		if (directive.startsActiveDelivery()) {
			this.activeTargets.add(target);
		} else {
			this.settledTargets.add(target);
		}
	}

	private JoinDecision joinDrop(final UUID target, final JoinKind kind) {
		return new JoinDecision(
			target,
			kind,
			DeliveryDirective.DROP,
			false,
			InstanceDirective.NONE,
			List.of()
		);
	}

	private boolean completeIfIdle() {
		if (
			!this.terminal
				&& this.activeTargets.isEmpty()
				&& this.pendingTargets.isEmpty()
		) {
			this.terminal = true;
			return true;
		}
		return false;
	}

	private static RestoredState buildRestoredState(
		final DeliveryPolicy delivery,
		final Optional<Integer> configuredPendingLimit,
		final Set<UUID> originalTargets,
		final Set<UUID> activeTargets,
		final Set<UUID> settledTargets,
		final Collection<PendingState> pendingStates,
		final long nowNanos
	) {
		Objects.requireNonNull(delivery, "delivery");
		Objects.requireNonNull(configuredPendingLimit, "configuredPendingLimit");
		if (nowNanos < 0L) {
			throw new IllegalArgumentException("nowNanos must be non-negative");
		}

		Set<UUID> original = immutableTargets(originalTargets, "originalTargets");
		Set<UUID> active = immutableTargets(activeTargets, "activeTargets");
		Set<UUID> settled = immutableTargets(settledTargets, "settledTargets");
		if (!original.containsAll(active)) {
			throw new IllegalArgumentException(
				"activeTargets must be a subset of originalTargets"
			);
		}
		if (!original.containsAll(settled)) {
			throw new IllegalArgumentException(
				"settledTargets must be a subset of originalTargets"
			);
		}
		if (!Collections.disjoint(active, settled)) {
			throw new IllegalArgumentException(
				"activeTargets and settledTargets must be disjoint"
			);
		}

		Collection<PendingState> suppliedPending = Objects.requireNonNull(
			pendingStates,
			"pendingStates"
		);
		if (suppliedPending.size() > MAX_PENDING_OFFLINE) {
			throw new IllegalArgumentException(
				"pendingStates exceeds " + MAX_PENDING_OFFLINE
			);
		}
		int pendingLimit = configuredPendingLimit.orElse(MAX_PENDING_OFFLINE);
		if (pendingLimit < 0 || pendingLimit > MAX_PENDING_OFFLINE) {
			throw new IllegalArgumentException(
				"maxPendingOffline must be within 0.." + MAX_PENDING_OFFLINE
			);
		}
		if (suppliedPending.size() > pendingLimit) {
			throw new IllegalArgumentException(
				"pendingStates exceeds configured maxPendingOffline " + pendingLimit
			);
		}

		OfflineTargetMode offlineMode = delivery.offlineTargets();
		boolean waitsOffline = offlineMode == OfflineTargetMode.QUEUE
			|| offlineMode == OfflineTargetMode.FINAL_ONLY;
		Optional<TimeSpan> ttl = delivery.offlineTtl();
		if (
			waitsOffline
				&& (
					ttl.isEmpty()
						|| ttl.orElseThrow().nanoseconds() <= 0L
						|| ttl.orElseThrow().nanoseconds() > MAX_OFFLINE_TTL_NANOS
				)
		) {
			throw new IllegalArgumentException(
				"queue and final_only restore requires a valid offline_ttl"
			);
		}
		if (!waitsOffline && !suppliedPending.isEmpty()) {
			throw new IllegalArgumentException(
				"pendingStates are incompatible with offline target mode " + offlineMode
			);
		}

		PendingMode expectedMode = offlineMode == OfflineTargetMode.QUEUE
			? PendingMode.QUEUE
			: PendingMode.FINAL_ONLY;
		long ttlNanos = waitsOffline ? ttl.orElseThrow().nanoseconds() : 0L;
		Map<UUID, PendingTarget> pending = new LinkedHashMap<>();
		for (PendingState supplied : suppliedPending) {
			PendingState state = Objects.requireNonNull(
				supplied,
				"pendingStates entry"
			);
			if (!original.contains(state.target())) {
				throw new IllegalArgumentException(
					"pending target must be a subset of originalTargets"
				);
			}
			if (active.contains(state.target()) || settled.contains(state.target())) {
				throw new IllegalArgumentException(
					"active, settled, and pending targets must be disjoint"
				);
			}
			if (state.mode() != expectedMode) {
				throw new IllegalArgumentException(
					"pending mode does not match cue offline target mode"
				);
			}
			if (
				state.expiresAtNanos() > nowNanos
					&& state.expiresAtNanos() - nowNanos > ttlNanos
			) {
				throw new IllegalArgumentException(
					"pending expiry exceeds the cue offline_ttl from nowNanos"
				);
			}
			PendingTarget previous = pending.put(
				state.target(),
				new PendingTarget(state.mode(), state.expiresAtNanos())
			);
			if (previous != null) {
				throw new IllegalArgumentException(
					"pendingStates contains a duplicate target"
				);
			}
		}

		Set<UUID> accounted = new LinkedHashSet<>(active);
		accounted.addAll(settled);
		accounted.addAll(pending.keySet());
		Set<UUID> skipped = new LinkedHashSet<>(original);
		skipped.removeAll(accounted);
		if (offlineMode != OfflineTargetMode.SKIP && !skipped.isEmpty()) {
			throw new IllegalArgumentException(
				"every original target must be active, settled, or pending"
			);
		}

		Map<UUID, PendingTarget> orderedPending = new LinkedHashMap<>();
		for (UUID target : sorted(pending.keySet())) {
			orderedPending.put(target, pending.get(target));
		}
		return new RestoredState(
			original,
			active,
			settled,
			immutableTargets(skipped, "skippedTargets"),
			orderedPending
		);
	}

	private static InitialBuild buildInitial(
		final DeliveryPolicy delivery,
		final Set<UUID> original,
		final Set<UUID> online,
		final Optional<Integer> configuredPendingLimit,
		final long nowNanos
	) {
		int pendingLimit = configuredPendingLimit.orElse(MAX_PENDING_OFFLINE);
		if (pendingLimit < 0 || pendingLimit > MAX_PENDING_OFFLINE) {
			return failedInitial(
				original,
				DiagnosticCode.PENDING_LIMIT_INVALID,
				"max_pending_offline 必须位于 0.." + MAX_PENDING_OFFLINE
			);
		}

		boolean waitsOffline = delivery.offlineTargets() == OfflineTargetMode.QUEUE
			|| delivery.offlineTargets() == OfflineTargetMode.FINAL_ONLY;
		Optional<TimeSpan> ttl = delivery.offlineTtl();
		if (
			waitsOffline
				&& (
					ttl.isEmpty()
						|| ttl.orElseThrow().nanoseconds() <= 0L
						|| ttl.orElseThrow().nanoseconds() > MAX_OFFLINE_TTL_NANOS
				)
		) {
			return failedInitial(
				original,
				DiagnosticCode.OFFLINE_TTL_REQUIRED,
				"queue 与 final_only 离线投递必须声明有效 offline_ttl"
			);
		}

		Set<UUID> offline = new LinkedHashSet<>(original);
		offline.removeAll(online);
		if (waitsOffline && offline.size() > pendingLimit) {
			return failedInitial(
				original,
				DiagnosticCode.PENDING_LIMIT_EXCEEDED,
				"离线等待目标超过 max_pending_offline 上限 " + pendingLimit
			);
		}

		Map<UUID, DeliveryDirective> directives = new LinkedHashMap<>();
		Set<UUID> onlineStart = new LinkedHashSet<>();
		Set<UUID> pending = new LinkedHashSet<>();
		Set<UUID> skipped = new LinkedHashSet<>();
		Set<UUID> failed = new LinkedHashSet<>();
		Map<UUID, PendingTarget> pendingRecords = new LinkedHashMap<>();
		for (UUID target : sorted(online)) {
			onlineStart.add(target);
			directives.put(target, DeliveryDirective.START_AT_SHARED_ANCHOR);
		}

		if (delivery.offlineTargets() == OfflineTargetMode.FAIL && !offline.isEmpty()) {
			return failedInitial(
				original,
				DiagnosticCode.OFFLINE_TARGET_FAILED,
				"初始受众包含离线目标，offline_targets=fail"
			);
		}

		long deadline = -1L;
		if (waitsOffline) {
			try {
				deadline = Math.addExact(nowNanos, ttl.orElseThrow().nanoseconds());
			} catch (ArithmeticException overflow) {
				return failedInitial(
					original,
					DiagnosticCode.OFFLINE_TTL_INVALID,
					"offline_ttl 截止时刻无法安全表示"
				);
			}
		}

		for (UUID target : sorted(offline)) {
			switch (delivery.offlineTargets()) {
				case SKIP -> {
					skipped.add(target);
					directives.put(target, DeliveryDirective.SKIP_OFFLINE);
				}
				case FAIL -> throw new IllegalStateException("fail handled above");
				case QUEUE -> {
					pending.add(target);
					directives.put(target, DeliveryDirective.QUEUE_UNTIL_TTL);
					pendingRecords.put(
						target,
						new PendingTarget(PendingMode.QUEUE, deadline)
					);
				}
				case FINAL_ONLY -> {
					pending.add(target);
					directives.put(target, DeliveryDirective.FINAL_ONLY_UNTIL_TTL);
					pendingRecords.put(
						target,
						new PendingTarget(PendingMode.FINAL_ONLY, deadline)
					);
				}
			}
		}

		InstanceDirective instanceDirective;
		if (!onlineStart.isEmpty()) {
			instanceDirective = InstanceDirective.ACTIVE;
		} else if (!pending.isEmpty()) {
			instanceDirective = InstanceDirective.WAIT_PENDING;
		} else {
			instanceDirective = switch (delivery.emptyAudience()) {
				case FAIL -> InstanceDirective.FAIL;
				case COMPLETE -> InstanceDirective.COMPLETE;
				case CALLBACKS_ONLY -> InstanceDirective.CALLBACKS_ONLY;
			};
		}

		List<Diagnostic> diagnostics = instanceDirective == InstanceDirective.FAIL
			? List.of(
				diagnostic(
					DiagnosticCode.EMPTY_AUDIENCE,
					"消息受众为空，empty_audience=fail",
					null
				)
			)
			: List.of();
		return new InitialBuild(
			new InitialDecision(
				instanceDirective,
				onlineStart,
				pending,
				skipped,
				failed,
				directives,
				diagnostics
			),
			pendingRecords
		);
	}

	private static InitialBuild failedInitial(
		final Set<UUID> original,
		final DiagnosticCode code,
		final String message
	) {
		Map<UUID, DeliveryDirective> directives = new LinkedHashMap<>();
		for (UUID target : sorted(original)) {
			directives.put(target, DeliveryDirective.FAIL_INSTANCE);
		}
		return new InitialBuild(
			new InitialDecision(
				InstanceDirective.FAIL,
				Set.of(),
				Set.of(),
				Set.of(),
				original,
				directives,
				List.of(diagnostic(code, message, null))
			),
			Map.of()
		);
	}

	private static Diagnostic diagnostic(
		final DiagnosticCode code,
		final String message,
		final UUID target
	) {
		return new Diagnostic(code, message, Optional.ofNullable(target));
	}

	private static Set<UUID> immutableTargets(
		final Collection<UUID> targets,
		final String field
	) {
		Objects.requireNonNull(targets, field);
		if (targets.size() > MAX_TARGETS) {
			throw new IllegalArgumentException(field + " exceeds " + MAX_TARGETS);
		}
		LinkedHashSet<UUID> copy = new LinkedHashSet<>();
		for (UUID target : targets) {
			copy.add(Objects.requireNonNull(target, field + " entry"));
		}
		return Collections.unmodifiableSet(
			new LinkedHashSet<>(sorted(copy))
		);
	}

	private static List<UUID> sorted(final Collection<UUID> targets) {
		return targets.stream().sorted(TARGET_ORDER).toList();
	}

	/** Stable offline-wait semantics persisted beside one pending target. */
	public enum PendingMode {
		QUEUE,
		FINAL_ONLY
	}

	/**
	 * Stable, target-addressed offline wait state. Deadlines use the same non-negative monotonic
	 * presentation clock passed to {@link #begin} and {@link #restore}.
	 */
	public record PendingState(
		UUID target,
		PendingMode mode,
		long expiresAtNanos
	) {
		public PendingState {
			target = Objects.requireNonNull(target, "target");
			mode = Objects.requireNonNull(mode, "mode");
			if (expiresAtNanos < 0L) {
				throw new IllegalArgumentException(
					"expiresAtNanos must be non-negative"
				);
			}
		}
	}

	private record PendingTarget(PendingMode mode, long expiresAtNanos) {
	}

	private record RestoredState(
		Set<UUID> originalTargets,
		Set<UUID> activeTargets,
		Set<UUID> settledTargets,
		Set<UUID> skippedTargets,
		Map<UUID, PendingTarget> pendingTargets
	) {
		private RestoredState {
			originalTargets = immutableTargets(originalTargets, "originalTargets");
			activeTargets = immutableTargets(activeTargets, "activeTargets");
			settledTargets = immutableTargets(settledTargets, "settledTargets");
			skippedTargets = immutableTargets(skippedTargets, "skippedTargets");
			if (pendingTargets.size() > MAX_PENDING_OFFLINE) {
				throw new IllegalArgumentException(
					"pendingTargets exceeds " + MAX_PENDING_OFFLINE
				);
			}
			pendingTargets = Collections.unmodifiableMap(
				new LinkedHashMap<>(pendingTargets)
			);
		}
	}

	private record InitialBuild(
		InitialDecision decision,
		Map<UUID, PendingTarget> pending
	) {
		private InitialBuild {
			pending = Map.copyOf(pending);
		}
	}

	public enum InstanceDirective {
		NONE,
		ACTIVE,
		WAIT_PENDING,
		FAIL,
		COMPLETE,
		CALLBACKS_ONLY
	}

	public enum JoinKind {
		ORIGINAL_RECONNECT,
		LIVE_NEW_TARGET,
		NOT_DELIVERABLE
	}

	public enum DeliveryDirective {
		START_AT_SHARED_ANCHOR(false, false, false, true),
		QUEUE_UNTIL_TTL(false, false, false, false),
		FINAL_ONLY_UNTIL_TTL(false, false, false, false),
		SKIP_OFFLINE(false, false, false, false),
		FAIL_INSTANCE(false, false, false, false),
		SEEK_NO_PAST_SOUNDS(true, false, false, true),
		RESTART_NEW_VISUAL_ANCHOR_NO_CALLBACK_REPLAY(false, true, false, true),
		FROM_START_NEW_VISUAL_ANCHOR_NO_CALLBACK_REPLAY(false, true, false, true),
		FINAL_ONLY_NO_CALLBACK_REPLAY(false, false, false, false),
		DROP(false, false, false, false),
		EXPIRE_FINAL_AND_REMOVE(false, false, false, false),
		EXPIRE_DROP_AND_REMOVE(false, false, false, false);

		private final boolean seek;
		private final boolean newVisualAnchor;
		private final boolean replayServerCallbacks;
		private final boolean startsActiveDelivery;

		DeliveryDirective(
			final boolean seek,
			final boolean newVisualAnchor,
			final boolean replayServerCallbacks,
			final boolean startsActiveDelivery
		) {
			this.seek = seek;
			this.newVisualAnchor = newVisualAnchor;
			this.replayServerCallbacks = replayServerCallbacks;
			this.startsActiveDelivery = startsActiveDelivery;
		}

		public boolean seek() {
			return this.seek;
		}

		public boolean newVisualAnchor() {
			return this.newVisualAnchor;
		}

		public boolean replayPastSounds() {
			return false;
		}

		public boolean replayServerCallbacks() {
			return this.replayServerCallbacks;
		}

		public boolean startsActiveDelivery() {
			return this.startsActiveDelivery;
		}
	}

	public enum DiagnosticCode {
		OFFLINE_TTL_REQUIRED,
		OFFLINE_TTL_INVALID,
		PENDING_LIMIT_INVALID,
		PENDING_LIMIT_EXCEEDED,
		OFFLINE_TARGET_FAILED,
		EMPTY_AUDIENCE,
		OFFLINE_TTL_EXPIRED,
		TARGET_LIMIT_EXCEEDED
	}

	public record Diagnostic(
		DiagnosticCode code,
		String message,
		Optional<UUID> target
	) {
		public Diagnostic {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			if (message.isBlank()) {
				throw new IllegalArgumentException("diagnostic message cannot be blank");
			}
			target = Objects.requireNonNull(target, "target");
		}
	}

	public record InitialDecision(
		InstanceDirective instanceDirective,
		Set<UUID> onlineStart,
		Set<UUID> pending,
		Set<UUID> skipped,
		Set<UUID> failed,
		Map<UUID, DeliveryDirective> directives,
		List<Diagnostic> diagnostics
	) {
		public InitialDecision {
			instanceDirective = Objects.requireNonNull(
				instanceDirective,
				"instanceDirective"
			);
			onlineStart = immutableTargets(onlineStart, "onlineStart");
			pending = immutableTargets(pending, "pending");
			skipped = immutableTargets(skipped, "skipped");
			failed = immutableTargets(failed, "failed");
			directives = immutableDirectives(directives);
			diagnostics = immutableDiagnostics(diagnostics);
			Set<UUID> partition = new LinkedHashSet<>();
			for (Set<UUID> part : List.of(onlineStart, pending, skipped, failed)) {
				for (UUID target : part) {
					if (!partition.add(target)) {
						throw new IllegalArgumentException(
							"initial target appears in more than one partition"
						);
					}
				}
			}
			if (!directives.keySet().equals(partition)) {
				throw new IllegalArgumentException(
					"every initial target must have exactly one directive"
				);
			}
		}
	}

	public record JoinDecision(
		UUID target,
		JoinKind kind,
		DeliveryDirective directive,
		boolean removedFromPending,
		InstanceDirective instanceDirective,
		List<Diagnostic> diagnostics
	) {
		public JoinDecision {
			target = Objects.requireNonNull(target, "target");
			kind = Objects.requireNonNull(kind, "kind");
			directive = Objects.requireNonNull(directive, "directive");
			instanceDirective = Objects.requireNonNull(
				instanceDirective,
				"instanceDirective"
			);
			diagnostics = immutableDiagnostics(diagnostics);
		}
	}

	public record CompletionDecision(
		UUID target,
		boolean removedActive,
		InstanceDirective instanceDirective
	) {
		public CompletionDecision {
			target = Objects.requireNonNull(target, "target");
			instanceDirective = Objects.requireNonNull(
				instanceDirective,
				"instanceDirective"
			);
		}
	}

	public record ExpiryDecision(
		Set<UUID> removed,
		Set<UUID> finalTargets,
		Set<UUID> dropped,
		Map<UUID, DeliveryDirective> directives,
		InstanceDirective instanceDirective
	) {
		public ExpiryDecision {
			removed = immutableTargets(removed, "removed");
			finalTargets = immutableTargets(finalTargets, "finalTargets");
			dropped = immutableTargets(dropped, "dropped");
			directives = immutableDirectives(directives);
			instanceDirective = Objects.requireNonNull(
				instanceDirective,
				"instanceDirective"
			);
			if (
				!removed.containsAll(finalTargets)
					|| !removed.containsAll(dropped)
					|| finalTargets.size() + dropped.size() != removed.size()
					|| !directives.keySet().equals(removed)
			) {
				throw new IllegalArgumentException("expiry target partitions disagree");
			}
		}
	}

	private static Map<UUID, DeliveryDirective> immutableDirectives(
		final Map<UUID, DeliveryDirective> directives
	) {
		Objects.requireNonNull(directives, "directives");
		if (directives.size() > MAX_TARGETS) {
			throw new IllegalArgumentException("directives exceeds " + MAX_TARGETS);
		}
		Map<UUID, DeliveryDirective> copy = new LinkedHashMap<>();
		for (UUID target : sorted(directives.keySet())) {
			copy.put(
				Objects.requireNonNull(target, "directive target"),
				Objects.requireNonNull(directives.get(target), "directive")
			);
		}
		return Collections.unmodifiableMap(copy);
	}

	private static List<Diagnostic> immutableDiagnostics(
		final List<Diagnostic> diagnostics
	) {
		Objects.requireNonNull(diagnostics, "diagnostics");
		if (diagnostics.size() > MAX_DIAGNOSTICS) {
			throw new IllegalArgumentException(
				"diagnostics exceeds " + MAX_DIAGNOSTICS
			);
		}
		return List.copyOf(diagnostics);
	}
}
