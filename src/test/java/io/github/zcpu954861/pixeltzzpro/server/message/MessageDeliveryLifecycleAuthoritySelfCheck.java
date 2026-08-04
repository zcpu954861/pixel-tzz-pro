package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudiencePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DeliveryPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EmptyAudienceMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LateJoinMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ReconnectMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.DeliveryDirective;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.DiagnosticCode;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.InstanceDirective;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.JoinKind;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.PendingMode;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.PendingState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure-JVM matrix for bounded offline delivery, reconnect, late join, and TTL state. */
public final class MessageDeliveryLifecycleAuthoritySelfCheck {
	private static final UUID PLAYER_A = uuid(1L);
	private static final UUID PLAYER_B = uuid(2L);
	private static final UUID PLAYER_C = uuid(3L);
	private static final long TTL_NANOS = 100L;

	private MessageDeliveryLifecycleAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_DELIVERY_LIFECYCLE_AUTHORITY_SELF_CHECK=PASS");
	}

	public static void run() {
		checkInitialOfflineModes();
		checkEmptyAudienceModes();
		checkTtlValidationAndPendingLimits();
		checkReconnectModes();
		checkLateJoinModesAndEvolution();
		checkExpiryAndCompletion();
		checkPendingSnapshotsAndRestoreBehavior();
		checkRestoreValidation();
		checkDefensiveCopiesAndHardTargetLimit();
	}

	private static void checkInitialOfflineModes() {
		Set<UUID> original = Set.of(PLAYER_A, PLAYER_B);
		Set<UUID> online = Set.of(PLAYER_A);

		var skip = begin(
			policy(EmptyAudienceMode.FAIL, OfflineTargetMode.SKIP, Optional.empty()),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			original,
			online,
			0L
		);
		check(
			skip.initialDecision().instanceDirective() == InstanceDirective.ACTIVE
				&& skip.initialDecision().onlineStart().equals(Set.of(PLAYER_A))
				&& skip.initialDecision().skipped().equals(Set.of(PLAYER_B))
				&& skip.initialDecision().pending().isEmpty()
				&& skip.initialDecision().failed().isEmpty()
				&& skip.initialDecision().directives().equals(
					Map.of(
						PLAYER_A,
						DeliveryDirective.START_AT_SHARED_ANCHOR,
						PLAYER_B,
						DeliveryDirective.SKIP_OFFLINE
					)
				),
			"skip must start online targets and permanently partition offline targets"
		);

		var fail = begin(
			policy(EmptyAudienceMode.FAIL, OfflineTargetMode.FAIL, Optional.empty()),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			original,
			online,
			0L
		);
		check(
			fail.initialDecision().instanceDirective() == InstanceDirective.FAIL
				&& fail.initialDecision().failed().equals(original)
				&& fail.initialDecision().onlineStart().isEmpty()
				&& fail.initialDecision().directives().values().stream().allMatch(
					directive -> directive == DeliveryDirective.FAIL_INSTANCE
				)
				&& diagnostic(fail.initialDecision().diagnostics())
					== DiagnosticCode.OFFLINE_TARGET_FAILED,
			"fail must reject the entire initial delivery instead of partially starting it"
		);

		var queue = begin(
			policy(
				EmptyAudienceMode.FAIL,
				OfflineTargetMode.QUEUE,
				Optional.of(new TimeSpan(TTL_NANOS))
			),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			original,
			online,
			10L
		);
		check(
			queue.initialDecision().instanceDirective() == InstanceDirective.ACTIVE
				&& queue.initialDecision().pending().equals(Set.of(PLAYER_B))
				&& queue.initialDecision().directives().get(PLAYER_B)
					== DeliveryDirective.QUEUE_UNTIL_TTL
				&& queue.activeCount() == 1
				&& queue.pendingCount() == 1
				&& queue.pendingSnapshot().equals(
					List.of(new PendingState(PLAYER_B, PendingMode.QUEUE, 110L))
				),
			"queue must retain offline targets while starting online targets"
		);

		var finalOnly = begin(
			policy(
				EmptyAudienceMode.FAIL,
				OfflineTargetMode.FINAL_ONLY,
				Optional.of(new TimeSpan(TTL_NANOS))
			),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			original,
			online,
			10L
		);
		check(
			finalOnly.initialDecision().pending().equals(Set.of(PLAYER_B))
				&& finalOnly.initialDecision().directives().get(PLAYER_B)
					== DeliveryDirective.FINAL_ONLY_UNTIL_TTL,
			"final_only must retain a distinct pending delivery directive"
		);
		var finalJoin = finalOnly.onJoin(PLAYER_B, true, 20L);
		check(
			finalJoin.directive()
				== DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY
				&& finalJoin.removedFromPending()
				&& finalOnly.pendingCount() == 0,
			"final_only pending targets must never enter animated delivery"
		);
	}

	private static void checkEmptyAudienceModes() {
		for (EmptyAudienceMode mode : EmptyAudienceMode.values()) {
			var authority = begin(
				policy(mode, OfflineTargetMode.SKIP, Optional.empty()),
				AudienceEvolution.SNAPSHOT,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				Optional.empty(),
				Set.of(PLAYER_A),
				Set.of(),
				0L
			);
			InstanceDirective expected = switch (mode) {
				case FAIL -> InstanceDirective.FAIL;
				case COMPLETE -> InstanceDirective.COMPLETE;
				case CALLBACKS_ONLY -> InstanceDirective.CALLBACKS_ONLY;
			};
			check(
				authority.initialDecision().instanceDirective() == expected
					&& authority.initialDecision().skipped().equals(Set.of(PLAYER_A))
					&& authority.activeCount() == 0
					&& authority.pendingCount() == 0,
				"empty audience mode must emit its exact terminal directive: " + mode
			);
			check(
				(mode != EmptyAudienceMode.FAIL
						&& authority.initialDecision().diagnostics().isEmpty())
					|| diagnostic(authority.initialDecision().diagnostics())
						== DiagnosticCode.EMPTY_AUDIENCE,
				"only fail may diagnose an empty audience"
			);
			check(
				authority.onJoin(PLAYER_A, true, 1L).kind()
					== JoinKind.NOT_DELIVERABLE,
				"terminal empty-audience instances must not revive on join"
			);
		}
	}

	private static void checkTtlValidationAndPendingLimits() {
		for (OfflineTargetMode mode : Set.of(
			OfflineTargetMode.QUEUE,
			OfflineTargetMode.FINAL_ONLY
		)) {
			var missing = begin(
				policy(EmptyAudienceMode.FAIL, mode, Optional.empty()),
				AudienceEvolution.SNAPSHOT,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				Optional.empty(),
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				0L
			);
			check(
				missing.initialDecision().instanceDirective() == InstanceDirective.FAIL
					&& diagnostic(missing.initialDecision().diagnostics())
						== DiagnosticCode.OFFLINE_TTL_REQUIRED,
				"queue/final_only must require TTL even when every initial target is online"
			);
		}

		for (long ttl : new long[] {
			0L,
			MessageDeliveryLifecycleAuthority.MAX_OFFLINE_TTL_NANOS + 1L
		}) {
			var invalid = begin(
				policy(
					EmptyAudienceMode.FAIL,
					OfflineTargetMode.QUEUE,
					Optional.of(new TimeSpan(ttl))
				),
				AudienceEvolution.SNAPSHOT,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				Optional.empty(),
				Set.of(PLAYER_A),
				Set.of(),
				0L
			);
			check(
				invalid.initialDecision().instanceDirective() == InstanceDirective.FAIL
					&& diagnostic(invalid.initialDecision().diagnostics())
						== DiagnosticCode.OFFLINE_TTL_REQUIRED,
				"zero or overlong offline TTL must fail closed"
			);
		}

		var overflow = begin(
			policy(
				EmptyAudienceMode.FAIL,
				OfflineTargetMode.QUEUE,
				Optional.of(new TimeSpan(20L))
			),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			Set.of(PLAYER_A),
			Set.of(),
			Long.MAX_VALUE - 10L
		);
		check(
			diagnostic(overflow.initialDecision().diagnostics())
				== DiagnosticCode.OFFLINE_TTL_INVALID,
			"overflowing absolute TTL deadlines must fail closed"
		);

		var zeroLimitOnline = begin(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.of(0),
			Set.of(PLAYER_A),
			Set.of(PLAYER_A),
			0L
		);
		check(
			zeroLimitOnline.initialDecision().instanceDirective()
				== InstanceDirective.ACTIVE,
			"max_pending_offline=0 must still allow an all-online audience"
		);

		for (Optional<Integer> limit : Set.of(Optional.of(-1), Optional.of(4_097))) {
			var invalidLimit = begin(
				queuedPolicy(),
				AudienceEvolution.SNAPSHOT,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				limit,
				Set.of(PLAYER_A),
				Set.of(),
				0L
			);
			check(
				diagnostic(invalidLimit.initialDecision().diagnostics())
					== DiagnosticCode.PENDING_LIMIT_INVALID,
				"pending limit outside the authority hard range must fail closed"
			);
		}

		for (int limit : new int[] { 0, 1 }) {
			Set<UUID> targets = limit == 0
				? Set.of(PLAYER_A)
				: Set.of(PLAYER_A, PLAYER_B);
			var exceeded = begin(
				queuedPolicy(),
				AudienceEvolution.SNAPSHOT,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				Optional.of(limit),
				targets,
				Set.of(),
				0L
			);
			check(
				exceeded.initialDecision().instanceDirective() == InstanceDirective.FAIL
					&& exceeded.initialDecision().pending().isEmpty()
					&& exceeded.initialDecision().failed().equals(targets)
					&& diagnostic(exceeded.initialDecision().diagnostics())
						== DiagnosticCode.PENDING_LIMIT_EXCEEDED,
				"pending overflow must reject the whole initial delivery"
			);
		}
	}

	private static void checkReconnectModes() {
		for (ReconnectMode mode : ReconnectMode.values()) {
			var authority = begin(
				policy(EmptyAudienceMode.FAIL, OfflineTargetMode.SKIP, Optional.empty()),
				AudienceEvolution.SNAPSHOT,
				mode,
				LateJoinMode.DROP,
				Optional.empty(),
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				0L
			);
			var joined = authority.onJoin(PLAYER_A, true, 1L);
			DeliveryDirective expected = switch (mode) {
				case CONTINUE -> DeliveryDirective.SEEK_NO_PAST_SOUNDS;
				case RESTART ->
					DeliveryDirective.RESTART_NEW_VISUAL_ANCHOR_NO_CALLBACK_REPLAY;
				case FINAL_ONLY -> DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY;
				case DROP -> DeliveryDirective.DROP;
			};
			check(
				joined.kind() == JoinKind.ORIGINAL_RECONNECT
					&& joined.directive() == expected
					&& !joined.removedFromPending()
					&& !joined.directive().replayPastSounds()
					&& !joined.directive().replayServerCallbacks(),
				"reconnect must emit exact no-replay semantics: " + mode
			);
			check(
				joined.directive().seek() == (mode == ReconnectMode.CONTINUE)
					&& joined.directive().newVisualAnchor()
						== (mode == ReconnectMode.RESTART),
				"continue seeks while restart creates a new visual anchor"
			);
			if (mode == ReconnectMode.CONTINUE || mode == ReconnectMode.RESTART) {
				check(
					authority.activeCount() == 1
						&& joined.instanceDirective() == InstanceDirective.NONE,
					"animated reconnect must replace, not duplicate, active delivery"
				);
				var complete = authority.markDeliveryComplete(PLAYER_A);
				check(
					complete.removedActive()
						&& complete.instanceDirective() == InstanceDirective.COMPLETE,
					"last animated reconnect must explicitly complete the instance"
				);
			} else {
				check(
					authority.activeCount() == 0
						&& joined.instanceDirective() == InstanceDirective.COMPLETE,
					"final/drop reconnect must remove the previous connection's active delivery"
				);
			}
			check(
				authority.onJoin(PLAYER_A, true, 2L).kind()
					== JoinKind.NOT_DELIVERABLE
					&& !authority.markDeliveryComplete(PLAYER_A).removedActive(),
				"settled reconnect targets and duplicate completion reports must be idempotent"
			);
		}

		var queued = begin(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			Set.of(PLAYER_A),
			Set.of(),
			0L
		);
		var queuedJoin = queued.onJoin(PLAYER_A, true, 1L);
		check(
			queuedJoin.removedFromPending()
				&& queuedJoin.directive() == DeliveryDirective.SEEK_NO_PAST_SOUNDS
				&& queued.pendingCount() == 0
				&& queued.activeCount() == 1,
			"queued original targets must leave pending exactly once when they reconnect"
		);
	}

	private static void checkLateJoinModesAndEvolution() {
		for (LateJoinMode mode : LateJoinMode.values()) {
			var authority = begin(
				policy(EmptyAudienceMode.FAIL, OfflineTargetMode.SKIP, Optional.empty()),
				AudienceEvolution.LIVE_ADD,
				ReconnectMode.CONTINUE,
				mode,
				Optional.empty(),
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				0L
			);
			var joined = authority.onJoin(PLAYER_B, true, 1L);
			DeliveryDirective expected = switch (mode) {
				case CONTINUE -> DeliveryDirective.SEEK_NO_PAST_SOUNDS;
				case FROM_START ->
					DeliveryDirective.FROM_START_NEW_VISUAL_ANCHOR_NO_CALLBACK_REPLAY;
				case FINAL_ONLY -> DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY;
				case DROP -> DeliveryDirective.DROP;
			};
			check(
				joined.kind() == JoinKind.LIVE_NEW_TARGET
					&& joined.directive() == expected
					&& !joined.directive().replayPastSounds()
					&& !joined.directive().replayServerCallbacks(),
				"late join must emit exact no-replay semantics: " + mode
			);
			check(
				joined.directive().seek() == (mode == LateJoinMode.CONTINUE)
					&& joined.directive().newVisualAnchor()
						== (mode == LateJoinMode.FROM_START),
				"continue seeks while from_start creates a new visual anchor"
			);
			int expectedActive = mode == LateJoinMode.CONTINUE
				|| mode == LateJoinMode.FROM_START
				? 2
				: 1;
			check(
				authority.activeCount() == expectedActive,
				"only animated late-join directives may enter active delivery"
			);
			check(
				authority.onJoin(PLAYER_B, true, 2L).kind()
					== JoinKind.NOT_DELIVERABLE,
				"the same live target must not be added twice"
			);
			if (expectedActive == 2) {
				authority.markDeliveryComplete(PLAYER_B);
			}
			check(
				authority.markDeliveryComplete(PLAYER_A).instanceDirective()
					== InstanceDirective.COMPLETE,
				"the last remaining live target must complete the instance"
			);
		}

		var snapshot = baseLive(AudienceEvolution.SNAPSHOT);
		check(
			snapshot.onJoin(PLAYER_B, true, 1L).kind()
				== JoinKind.NOT_DELIVERABLE,
			"snapshot audiences must reject every new target"
		);

		var liveAdd = baseLive(AudienceEvolution.LIVE_ADD);
		check(
			liveAdd.onJoin(PLAYER_B, false, 1L).kind()
				== JoinKind.NOT_DELIVERABLE
				&& liveAdd.onJoin(PLAYER_B, true, 2L).kind()
					== JoinKind.LIVE_NEW_TARGET,
			"live_add must admit only a freshly eligible new target"
		);

		var liveStrict = baseLive(AudienceEvolution.LIVE_STRICT);
		check(
			liveStrict.onJoin(PLAYER_B, true, 1L).kind()
				== JoinKind.LIVE_NEW_TARGET,
			"live_strict must admit a freshly eligible new target"
		);
	}

	private static void checkExpiryAndCompletion() {
		var expiry = begin(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			Set.of(PLAYER_A, PLAYER_B),
			Set.of(),
			1_000L
		);
		var before = expiry.expire(1_099L, Set.of(PLAYER_A));
		check(
			before.removed().isEmpty()
				&& before.instanceDirective() == InstanceDirective.NONE
				&& expiry.pendingCount() == 2,
			"TTL must not fire one nanosecond early"
		);

		var due = expiry.expire(1_100L, Set.of(PLAYER_A));
		check(
			due.removed().equals(Set.of(PLAYER_A, PLAYER_B))
				&& due.finalTargets().equals(Set.of(PLAYER_A))
				&& due.dropped().equals(Set.of(PLAYER_B))
				&& due.directives().equals(
					Map.of(
						PLAYER_A,
						DeliveryDirective.EXPIRE_FINAL_AND_REMOVE,
						PLAYER_B,
						DeliveryDirective.EXPIRE_DROP_AND_REMOVE
					)
				)
				&& due.instanceDirective() == InstanceDirective.COMPLETE
				&& expiry.pendingCount() == 0,
			"due TTL must final online targets, drop offline targets, and remove both"
		);
		check(
			expiry.expire(1_101L, Set.of(PLAYER_A, PLAYER_B)).removed().isEmpty()
				&& expiry.onJoin(PLAYER_A, true, 1_102L).kind()
					== JoinKind.NOT_DELIVERABLE,
			"expired targets must never receive a second terminal directive"
		);

		var joinAtDeadline = begin(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.RESTART,
			LateJoinMode.DROP,
			Optional.empty(),
			Set.of(PLAYER_A),
			Set.of(),
			10L
		);
		var exactJoin = joinAtDeadline.onJoin(PLAYER_A, true, 110L);
		check(
			exactJoin.directive() == DeliveryDirective.EXPIRE_FINAL_AND_REMOVE
				&& exactJoin.removedFromPending()
				&& exactJoin.instanceDirective() == InstanceDirective.COMPLETE
				&& diagnostic(exactJoin.diagnostics())
					== DiagnosticCode.OFFLINE_TTL_EXPIRED,
			"joining exactly at the deadline must use the TTL terminal result"
		);

		var activeAndPending = begin(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			Set.of(PLAYER_A, PLAYER_B),
			Set.of(PLAYER_A),
			0L
		);
		check(
			activeAndPending.markDeliveryComplete(PLAYER_A).instanceDirective()
				== InstanceDirective.NONE,
			"an offline pending target must keep the instance open after active delivery ends"
		);
		check(
			activeAndPending.expire(100L, Set.of()).instanceDirective()
				== InstanceDirective.COMPLETE,
			"clearing the final pending target must explicitly complete the instance"
		);
	}

	private static void checkPendingSnapshotsAndRestoreBehavior() {
		MessageCueDefinition queuedCue = cue(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty()
		);
		List<PendingState> callerPending = new ArrayList<>(
			List.of(new PendingState(PLAYER_B, PendingMode.QUEUE, 100L))
		);
		var restored = MessageDeliveryLifecycleAuthority.restore(
			queuedCue,
			Set.of(PLAYER_A, PLAYER_B, PLAYER_C),
			Set.of(PLAYER_A),
			Set.of(PLAYER_C),
			callerPending,
			25L
		);
		callerPending.clear();
		check(
			restored.initialDecision().instanceDirective() == InstanceDirective.NONE
				&& restored.activeCount() == 1
				&& restored.pendingCount() == 1
				&& restored.pendingSnapshot().equals(
					List.of(new PendingState(PLAYER_B, PendingMode.QUEUE, 100L))
				),
			"restore must copy partitions and expose the exact stable pending checkpoint"
		);
		expectUnsupported(
			() -> restored.pendingSnapshot().add(
				new PendingState(PLAYER_A, PendingMode.QUEUE, 100L)
			),
			"pending snapshots must be immutable"
		);
		check(
			restored.onJoin(PLAYER_C, true, 30L).kind()
				== JoinKind.NOT_DELIVERABLE,
			"a settled target must remain terminal after restore"
		);
		var queuedJoin = restored.onJoin(PLAYER_B, true, 50L);
		check(
			queuedJoin.kind() == JoinKind.ORIGINAL_RECONNECT
				&& queuedJoin.directive() == DeliveryDirective.SEEK_NO_PAST_SOUNDS
				&& queuedJoin.removedFromPending()
				&& restored.pendingSnapshot().isEmpty()
				&& restored.activeCount() == 2,
			"a restored queue target must reconnect exactly like a target created by begin"
		);
		check(
			restored.markDeliveryComplete(PLAYER_A).instanceDirective()
				== InstanceDirective.NONE
				&& restored.markDeliveryComplete(PLAYER_B).instanceDirective()
					== InstanceDirective.COMPLETE,
			"restored active targets must settle and complete with begin semantics"
		);

		var restoredExpiry = MessageDeliveryLifecycleAuthority.restore(
			queuedCue,
			Set.of(PLAYER_A, PLAYER_B),
			Set.of(PLAYER_A),
			Set.of(),
			List.of(new PendingState(PLAYER_B, PendingMode.QUEUE, 100L)),
			90L
		);
		check(
			restoredExpiry.expire(99L, Set.of(PLAYER_B)).removed().isEmpty(),
			"a restored deadline must not expire early"
		);
		var expired = restoredExpiry.expire(100L, Set.of(PLAYER_B));
		check(
			expired.finalTargets().equals(Set.of(PLAYER_B))
				&& expired.instanceDirective() == InstanceDirective.NONE
				&& restoredExpiry.markDeliveryComplete(PLAYER_A).instanceDirective()
					== InstanceDirective.COMPLETE,
			"restored TTL and remaining active delivery must retain exact completion order"
		);

		var alreadyDue = MessageDeliveryLifecycleAuthority.restore(
			queuedCue,
			Set.of(PLAYER_A),
			Set.of(),
			Set.of(),
			List.of(new PendingState(PLAYER_A, PendingMode.QUEUE, 50L)),
			100L
		);
		var dueJoin = alreadyDue.onJoin(PLAYER_A, true, 100L);
		check(
			dueJoin.directive() == DeliveryDirective.EXPIRE_FINAL_AND_REMOVE
				&& dueJoin.instanceDirective() == InstanceDirective.COMPLETE,
			"an overdue checkpoint must restore so the first join can settle it immediately"
		);

		MessageCueDefinition finalOnlyCue = cue(
			policy(
				EmptyAudienceMode.FAIL,
				OfflineTargetMode.FINAL_ONLY,
				Optional.of(new TimeSpan(TTL_NANOS))
			),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty()
		);
		var restoredFinalOnly = MessageDeliveryLifecycleAuthority.restore(
			finalOnlyCue,
			Set.of(PLAYER_A),
			Set.of(),
			Set.of(),
			List.of(new PendingState(PLAYER_A, PendingMode.FINAL_ONLY, 100L)),
			25L
		);
		check(
			restoredFinalOnly.onJoin(PLAYER_A, true, 50L).directive()
				== DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY,
			"restored final_only targets must never be promoted to animated delivery"
		);

		MessageCueDefinition skipCue = cue(
			policy(EmptyAudienceMode.FAIL, OfflineTargetMode.SKIP, Optional.empty()),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty()
		);
		var restoredSkip = MessageDeliveryLifecycleAuthority.restore(
			skipCue,
			Set.of(PLAYER_A, PLAYER_B),
			Set.of(PLAYER_A),
			Set.of(),
			List.of(),
			0L
		);
		check(
			restoredSkip.onJoin(PLAYER_B, true, 1L).kind()
				== JoinKind.ORIGINAL_RECONNECT
				&& restoredSkip.markDeliveryComplete(PLAYER_A).instanceDirective()
					== InstanceDirective.COMPLETE,
			"unpartitioned skip targets must retain their permanent skip behavior"
		);
	}

	private static void checkRestoreValidation() {
		MessageCueDefinition queuedCue = cue(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty()
		);
		PendingState pendingB = new PendingState(PLAYER_B, PendingMode.QUEUE, 100L);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				Set.of(),
				List.of(),
				-1L
			),
			"restore must reject a negative recovery clock"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(PLAYER_B),
				Set.of(),
				List.of(),
				0L
			),
			"restored active targets must be a subset of original targets"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(),
				Set.of(PLAYER_B),
				List.of(),
				0L
			),
			"restored settled targets must be a subset of original targets"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				List.of(),
				0L
			),
			"restored active and settled targets must be disjoint"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				Set.of(),
				List.of(pendingB),
				0L
			),
			"restored pending targets must be a subset of original targets"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A, PLAYER_B),
				Set.of(PLAYER_B),
				Set.of(),
				List.of(pendingB),
				0L
			),
			"restored active and pending targets must be disjoint"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A, PLAYER_B),
				Set.of(PLAYER_A),
				Set.of(),
				List.of(pendingB, pendingB),
				0L
			),
			"restored pending targets must be unique"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(),
				Set.of(),
				List.of(new PendingState(PLAYER_A, PendingMode.FINAL_ONLY, 100L)),
				0L
			),
			"restored pending mode must agree with the cue"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(),
				Set.of(),
				List.of(new PendingState(PLAYER_A, PendingMode.QUEUE, 101L)),
				0L
			),
			"a future restored deadline must not exceed the cue TTL"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> new PendingState(PLAYER_A, PendingMode.QUEUE, -1L),
			"pending state deadlines must use a non-negative clock"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A, PLAYER_B),
				Set.of(PLAYER_A),
				Set.of(),
				List.of(),
				0L
			),
			"queue restore must account for every original target"
		);

		MessageCueDefinition skipCue = cue(
			policy(EmptyAudienceMode.FAIL, OfflineTargetMode.SKIP, Optional.empty()),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty()
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				skipCue,
				Set.of(PLAYER_A),
				Set.of(),
				Set.of(),
				List.of(new PendingState(PLAYER_A, PendingMode.QUEUE, 100L)),
				0L
			),
			"skip restore must reject an impossible pending partition"
		);

		MessageCueDefinition zeroPendingCue = cue(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.of(0)
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				zeroPendingCue,
				Set.of(PLAYER_A),
				Set.of(),
				Set.of(),
				List.of(new PendingState(PLAYER_A, PendingMode.QUEUE, 100L)),
				0L
			),
			"restore must enforce the cue pending soft limit"
		);

		MessageCueDefinition missingTtlCue = cue(
			policy(EmptyAudienceMode.FAIL, OfflineTargetMode.QUEUE, Optional.empty()),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty()
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				missingTtlCue,
				Set.of(PLAYER_A),
				Set.of(PLAYER_A),
				Set.of(),
				List.of(),
				0L
			),
			"restore must fail closed on an invalid queue TTL even without pending targets"
		);

		List<PendingState> tooManyPending = Collections.nCopies(
			MessageDeliveryLifecycleAuthority.MAX_PENDING_OFFLINE + 1,
			new PendingState(PLAYER_A, PendingMode.QUEUE, 100L)
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageDeliveryLifecycleAuthority.restore(
				queuedCue,
				Set.of(PLAYER_A),
				Set.of(),
				Set.of(),
				tooManyPending,
				0L
			),
			"restore must reject more than 4096 pending records before materializing them"
		);

		Set<UUID> maximum = targets(MessageDeliveryLifecycleAuthority.MAX_TARGETS);
		List<PendingState> maximumPending = maximum.stream()
			.map(target -> new PendingState(target, PendingMode.QUEUE, 100L))
			.toList();
		var boundary = MessageDeliveryLifecycleAuthority.restore(
			queuedCue,
			maximum,
			Set.of(),
			Set.of(),
			maximumPending,
			0L
		);
		check(
			boundary.pendingSnapshot().size()
				== MessageDeliveryLifecycleAuthority.MAX_PENDING_OFFLINE,
			"the exact 4096-record restore boundary must remain accepted"
		);
	}

	private static void checkDefensiveCopiesAndHardTargetLimit() {
		Set<UUID> original = new LinkedHashSet<>(Set.of(PLAYER_A, PLAYER_B));
		Set<UUID> online = new LinkedHashSet<>(Set.of(PLAYER_A));
		var copied = begin(
			queuedPolicy(),
			AudienceEvolution.SNAPSHOT,
			ReconnectMode.CONTINUE,
			LateJoinMode.DROP,
			Optional.empty(),
			original,
			online,
			0L
		);
		original.clear();
		online.clear();
		check(
			copied.initialDecision().onlineStart().equals(Set.of(PLAYER_A))
				&& copied.initialDecision().pending().equals(Set.of(PLAYER_B)),
			"authority state must not retain caller-owned collection aliases"
		);
		expectUnsupported(
			() -> copied.initialDecision().onlineStart().add(PLAYER_C),
			"initial online partition must be immutable"
		);
		expectUnsupported(
			() -> copied.initialDecision().directives().put(
				PLAYER_C,
				DeliveryDirective.DROP
			),
			"initial directive map must be immutable"
		);
		expectUnsupported(
			() -> copied.initialDecision().diagnostics().add(null),
			"initial diagnostics must be immutable"
		);
		var expired = copied.expire(100L, Set.of());
		expectUnsupported(
			() -> expired.removed().add(PLAYER_C),
			"expiry partitions must be immutable"
		);
		expectUnsupported(
			() -> expired.directives().put(PLAYER_C, DeliveryDirective.DROP),
			"expiry directives must be immutable"
		);

		Set<UUID> maximum = targets(MessageDeliveryLifecycleAuthority.MAX_TARGETS);
		var boundary = begin(
			queuedPolicy(),
			AudienceEvolution.LIVE_ADD,
			ReconnectMode.CONTINUE,
			LateJoinMode.CONTINUE,
			Optional.empty(),
			maximum,
			Set.of(),
			0L
		);
		check(
			boundary.initialDecision().instanceDirective()
				== InstanceDirective.WAIT_PENDING
				&& boundary.pendingCount()
					== MessageDeliveryLifecycleAuthority.MAX_PENDING_OFFLINE
				&& boundary.initialDecision().directives().size()
					== MessageDeliveryLifecycleAuthority.MAX_TARGETS,
			"the exact 4096-target/pending boundary must be accepted"
		);
		var capped = boundary.onJoin(uuid(9_000L), true, 1L);
		check(
			capped.directive() == DeliveryDirective.DROP
				&& diagnostic(capped.diagnostics())
					== DiagnosticCode.TARGET_LIMIT_EXCEEDED,
			"live audience growth beyond 4096 total targets must be rejected"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> begin(
				queuedPolicy(),
				AudienceEvolution.SNAPSHOT,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				Optional.empty(),
				targets(MessageDeliveryLifecycleAuthority.MAX_TARGETS + 1),
				Set.of(),
				0L
			),
			"a 4097th original target must be rejected at the public boundary"
		);
	}

	private static MessageDeliveryLifecycleAuthority baseLive(
		final AudienceEvolution evolution
	) {
		return begin(
			policy(EmptyAudienceMode.FAIL, OfflineTargetMode.SKIP, Optional.empty()),
			evolution,
			ReconnectMode.CONTINUE,
			LateJoinMode.CONTINUE,
			Optional.empty(),
			Set.of(PLAYER_A),
			Set.of(PLAYER_A),
			0L
		);
	}

	private static MessageDeliveryLifecycleAuthority begin(
		final DeliveryPolicy delivery,
		final AudienceEvolution evolution,
		final ReconnectMode reconnect,
		final LateJoinMode lateJoin,
		final Optional<Integer> maxPending,
		final Set<UUID> original,
		final Set<UUID> online,
		final long nowNanos
	) {
		return MessageDeliveryLifecycleAuthority.begin(
			delivery,
			lifecycle(reconnect, lateJoin),
			evolution,
			maxPending,
			original,
			online,
			nowNanos
		);
	}

	private static MessageCueDefinition cue(
		final DeliveryPolicy delivery,
		final AudienceEvolution evolution,
		final ReconnectMode reconnect,
		final LateJoinMode lateJoin,
		final Optional<Integer> maxPendingOffline
	) {
		CuePolicies standard = CuePolicies.standard();
		AudiencePolicy standardAudience = standard.audience();
		CuePolicies policies = new CuePolicies(
			standard.timing(),
			new AudiencePolicy(
				standardAudience.audience(),
				evolution,
				standardAudience.sensitive(),
				standardAudience.onLoss()
			),
			standard.context(),
			standard.concurrency(),
			delivery,
			lifecycle(reconnect, lateJoin),
			standard.accessibility(),
			standard.controlGroups(),
			standard.capture(),
			standard.defaultConflict()
		);
		SoftLimits limits = new SoftLimits(
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			maxPendingOffline
		);
		return new MessageCueDefinition(
			Identifier.parse("pixel_tzz:self_check/delivery_restore"),
			Optional.empty(),
			false,
			policies,
			Map.of(),
			Map.of(),
			List.of(),
			Optional.empty(),
			Optional.empty(),
			List.of(),
			limits,
			"{}"
		);
	}

	private static DeliveryPolicy queuedPolicy() {
		return policy(
			EmptyAudienceMode.FAIL,
			OfflineTargetMode.QUEUE,
			Optional.of(new TimeSpan(TTL_NANOS))
		);
	}

	private static DeliveryPolicy policy(
		final EmptyAudienceMode empty,
		final OfflineTargetMode offline,
		final Optional<TimeSpan> ttl
	) {
		return new DeliveryPolicy(empty, offline, ttl);
	}

	private static LifecyclePolicy lifecycle(
		final ReconnectMode reconnect,
		final LateJoinMode lateJoin
	) {
		LifecyclePolicy standard = LifecyclePolicy.standard();
		return new LifecyclePolicy(
			standard.screenStart(),
			standard.obscured(),
			standard.screenWaitTimeout(),
			standard.screenTimeoutAction(),
			standard.maxPause(),
			standard.reload(),
			standard.restart(),
			standard.externalConflict(),
			reconnect,
			lateJoin,
			standard.resourceReload(),
			standard.respawn(),
			standard.attachment(),
			standard.dimensionExit()
		);
	}

	private static Set<UUID> targets(final int count) {
		Set<UUID> result = new LinkedHashSet<>();
		for (int index = 0; index < count; index++) {
			result.add(uuid(10_000L + index));
		}
		return result;
	}

	private static UUID uuid(final long value) {
		return new UUID(0L, value);
	}

	private static DiagnosticCode diagnostic(
		final java.util.List<MessageDeliveryLifecycleAuthority.Diagnostic> diagnostics
	) {
		check(diagnostics.size() == 1, "expected exactly one diagnostic");
		return diagnostics.getFirst().code();
	}

	private static void expectUnsupported(
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected immutable result.
		}
	}

	private static void expectThrows(
		final Class<? extends Throwable> expected,
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (Throwable thrown) {
			if (!expected.isInstance(thrown)) {
				throw new AssertionError(message, thrown);
			}
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
