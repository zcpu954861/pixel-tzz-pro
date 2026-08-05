package io.github.zcpu954861.pixeltzzpro.server.message;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryActionType;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.AudienceState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackLedger;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.ClockMode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.FrozenCue;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.InvocationContext;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.OfflineMode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.PendingOfflineDelivery;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.ProgressState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.RecipientState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.RestartMode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Snapshot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.TerminalStatus;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.TimingState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Tombstone;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV5;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

/** Runnable codec, checkpoint, bound, restart-action and callback replay-safety contract check. */
public final class MessageRuntimePersistenceAuthoritySelfCheck {
	private static final UUID GAME_INSTANCE = uuid("game-instance");
	private static final UUID NEXT_GAME_INSTANCE = uuid("next-game-instance");
	private static final UUID RECIPIENT = uuid("recipient");
	private static final UUID SECOND_RECIPIENT = uuid("second-recipient");
	private static final Identifier GAME = Identifier.parse("test:game");
	private static final Identifier PHASE = Identifier.parse("test:phase");
	private static final Identifier CUE = Identifier.parse("test:cue");

	private MessageRuntimePersistenceAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		checkCodecAndV4Compatibility();
		checkCheckpointFilteringAndBounds();
		checkRecoveryActionsAndCallbackSafety();
		checkPermanentRejectedFinalDoesNotRevive();
		checkGameScopeReset();
		checkNarrowWorldCheckpoint();
		System.out.println("MESSAGE_RUNTIME_PERSISTENCE_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkCodecAndV4Compatibility() {
		Instance continuing = instance("continue", RestartMode.CONTINUE, 10L);
		Snapshot snapshot = snapshot(
			List.of(continuing),
			List.of(pending(continuing.instanceId(), RECIPIENT, 20L, 40L)),
			List.of(),
			List.of(callback(continuing, CallbackStatus.SUCCEEDED))
		);
		var encodedNbt = Snapshot.CODEC.encodeStart(NbtOps.INSTANCE, snapshot).getOrThrow();
		Snapshot decodedNbt = Snapshot.CODEC.parse(NbtOps.INSTANCE, encodedNbt).getOrThrow();
		check(decodedNbt.equals(snapshot), "message runtime snapshot must round-trip through NBT");

		WorldStateV4 world = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			activeCore(GAME_INSTANCE),
			List.of(),
			List.of(),
			List.of(),
			Optional.of(snapshot)
		);
		var encodedWorld = WorldStateV4.CODEC.encodeStart(JsonOps.INSTANCE, world).getOrThrow();
		WorldStateV4 decodedWorld = WorldStateV4.CODEC
			.parse(JsonOps.INSTANCE, encodedWorld)
			.getOrThrow();
		check(decodedWorld.equals(world), "world v4 must round-trip the optional message runtime");
		JsonObject oldV4 = encodedWorld.deepCopy().getAsJsonObject();
		oldV4.remove("message_runtime");
		WorldStateV4 decodedOld = WorldStateV4.CODEC.parse(JsonOps.INSTANCE, oldV4).getOrThrow();
		check(decodedOld.messageRuntime().isEmpty(), "old v4 without message_runtime must decode");
	}

	private static void checkCheckpointFilteringAndBounds() {
		Instance persistent = instance("persistent", RestartMode.CONTINUE, 5L);
		Instance transientInstance = instance("transient", RestartMode.TRANSIENT, 6L);
		Snapshot live = new Snapshot(
			GAME_INSTANCE,
			100L,
			7L,
			List.of(persistent, transientInstance),
			List.of(
				pending(persistent.instanceId(), RECIPIENT, 10L, 200L),
				pending(transientInstance.instanceId(), uuid("transient-recipient"), 11L, 200L)
			),
			List.of(),
			new CallbackLedger(16, List.of(callback(persistent, CallbackStatus.SUCCEEDED)))
		);
		var checkpoint = MessageRuntimePersistenceAuthority.checkpoint(live);
		check(
			checkpoint.accepted()
				&& checkpoint.snapshot().orElseThrow().instances().equals(List.of(persistent))
				&& checkpoint.discardedTransientInstances().equals(List.of(transientInstance.instanceId()))
				&& checkpoint.snapshot().orElseThrow().pendingOffline().size() == 1,
			"checkpoint must omit transient state and its pending delivery"
		);

		List<Instance> tooManyInstances = new ArrayList<>();
		for (int index = 0; index <= PersistedMessageRuntime.HARD_MAX_INSTANCES; index++) {
			tooManyInstances.add(instance("bounded-" + index, RestartMode.CONTINUE, index));
		}
		var boundedInstances = MessageRuntimePersistenceAuthority.checkpoint(
			new Snapshot(
				GAME_INSTANCE,
				2_000L,
				1L,
				tooManyInstances,
				List.of(),
				List.of(),
				CallbackLedger.empty(16)
			)
		);
		check(
			boundedInstances.accepted()
				&& boundedInstances.snapshot().orElseThrow().instances().size()
					== PersistedMessageRuntime.HARD_MAX_INSTANCES
				&& boundedInstances.evictedPersistentInstances().size() == 1
				&& boundedInstances.snapshot().orElseThrow().tombstones().size() == 1,
			"checkpoint must bound active persistent instances and tombstone every eviction"
		);

		List<PendingOfflineDelivery> tooManyPending = new ArrayList<>();
		for (int index = 0; index <= PersistedMessageRuntime.HARD_MAX_PENDING_OFFLINE; index++) {
			tooManyPending.add(
				pending(persistent.instanceId(), uuid("pending-" + index), index, 30_000L)
			);
		}
		List<Tombstone> tooManyTombstones = new ArrayList<>();
		for (int index = 0; index <= PersistedMessageRuntime.HARD_MAX_TOMBSTONES; index++) {
			tooManyTombstones.add(
				new Tombstone(
					uuid("terminal-" + index),
					CUE,
					TerminalStatus.COMPLETED,
					index,
					"completed"
				)
			);
		}
		var boundedAuxiliary = MessageRuntimePersistenceAuthority.checkpoint(
			new Snapshot(
				GAME_INSTANCE,
				20_000L,
				2L,
				List.of(persistent),
				tooManyPending,
				tooManyTombstones,
				CallbackLedger.empty(16)
			)
		);
		check(
			boundedAuxiliary.accepted()
				&& boundedAuxiliary.snapshot().orElseThrow().pendingOffline().size()
					== PersistedMessageRuntime.HARD_MAX_PENDING_OFFLINE
				&& boundedAuxiliary.snapshot().orElseThrow().tombstones().size()
					== PersistedMessageRuntime.HARD_MAX_TOMBSTONES
				&& boundedAuxiliary.evictedPendingOffline().size() == 1
				&& boundedAuxiliary.evictedTombstones() == 1,
			"pending offline and terminal tombstones must remain hard bounded"
		);
	}

	private static void checkRecoveryActionsAndCallbackSafety() {
		Instance continuing = instance("recover-continue", RestartMode.CONTINUE, 10L);
		Instance finalizing = instance(
			"recover-finalize",
			RestartMode.FINALIZE,
			11L,
			List.of(RECIPIENT, SECOND_RECIPIENT)
		);
		Instance cancelling = instance("recover-cancel", RestartMode.CANCEL, 12L);
		PendingOfflineDelivery livePending = pending(
			continuing.instanceId(),
			RECIPIENT,
			20L,
			500L
		);
		PendingOfflineDelivery expiredPending = pending(
			continuing.instanceId(),
			uuid("expired"),
			20L,
			100L
		);
		Snapshot persisted = snapshot(
			List.of(continuing, finalizing, cancelling),
			List.of(
				livePending,
				expiredPending,
				pending(finalizing.instanceId(), uuid("finalize-pending"), 20L, 500L)
			),
			List.of(),
			List.of(
				callback(continuing, CallbackStatus.PREPARED),
				callback(finalizing, CallbackStatus.PREPARED)
			)
		);
		var plan = MessageRuntimePersistenceAuthority.recover(
			persisted,
			GAME_INSTANCE,
			150L
		);
		check(plan.accepted(), "valid persisted runtime must produce a recovery plan");
		check(
			plan.actions().stream().map(value -> value.type()).toList().equals(
				List.of(
					RecoveryActionType.RESTORE_CONTINUE,
					RecoveryActionType.FINALIZE_STATIC,
					RecoveryActionType.CANCEL
				)
			),
			"continue, finalize and cancel must become distinct explicit actions"
		);
		Snapshot next = plan.nextSnapshot().orElseThrow();
		check(
			next.instances().equals(List.of(continuing, finalizing))
				&& next.pendingOffline().equals(List.of(livePending))
				&& plan.expiredPendingOffline().equals(List.of(expiredPending)),
			"continue state and its live pending delivery plus durable final work must survive recovery"
		);
		check(
			plan.recoveredPreparedCallbacks() == 2
				&& next.callbackLedger().entries().size() == 1
				&& next.callbackLedger().entries().getFirst().status()
					== CallbackStatus.OUTCOME_UNKNOWN,
			"PREPARED callbacks must become OUTCOME_UNKNOWN and never be replayed"
		);
		check(
			next.tombstones().stream().anyMatch(
					value -> value.instanceId().equals(cancelling.instanceId())
						&& value.status() == TerminalStatus.CANCELLED
				)
				&& next.tombstones().stream().noneMatch(
					value -> value.instanceId().equals(finalizing.instanceId())
				)
				&& next.tombstones().stream().noneMatch(
					value -> value.instanceId().equals(continuing.instanceId())
				),
			"cancel must tombstone immediately while an unconsumed final remains durable"
		);

		var secondPlan = MessageRuntimePersistenceAuthority.recover(
			next,
			GAME_INSTANCE,
			175L
		);
		check(
			secondPlan.accepted()
				&& secondPlan.nextSnapshot().orElseThrow().instances()
					.equals(List.of(continuing, finalizing))
				&& secondPlan.actions().stream().map(value -> value.type()).toList().equals(
					List.of(
						RecoveryActionType.RESTORE_CONTINUE,
						RecoveryActionType.FINALIZE_STATIC
					)
				),
			"an offline restart=finalize recipient must survive a second full restart"
		);

		Snapshot beforeConsumption = secondPlan.nextSnapshot().orElseThrow();
		var firstConsumed = MessageRuntimePersistenceAuthority.consumeRecoveredFinal(
			beforeConsumption,
			GAME_INSTANCE,
			finalizing.instanceId(),
			RECIPIENT,
			200L
		);
		check(
			firstConsumed.accepted()
				&& firstConsumed.deliveryRequired()
				&& !firstConsumed.instanceRetired()
				&& firstConsumed.snapshot().orElseThrow().instances().stream().anyMatch(
					value -> value.instanceId().equals(finalizing.instanceId())
						&& value.progress().completedTargets().equals(List.of(RECIPIENT))
				),
			"a partial final consume must persist the player and retain work for offline targets"
		);
		var secondShutdown = MessageRuntimePersistenceAuthority.checkpoint(
			firstConsumed.snapshot().orElseThrow()
		);
		check(
			secondShutdown.accepted()
				&& secondShutdown.snapshot().orElseThrow().instances().stream().anyMatch(
					value -> value.instanceId().equals(finalizing.instanceId())
						&& value.progress().completedTargets().equals(List.of(RECIPIENT))
				),
			"a complete second shutdown must persist the partial final consumption"
		);
		var thirdPlan = MessageRuntimePersistenceAuthority.recover(
			secondShutdown.snapshot().orElseThrow(),
			GAME_INSTANCE,
			225L
		);
		check(
			thirdPlan.accepted()
				&& thirdPlan.actions().stream().anyMatch(value ->
					value.type() == RecoveryActionType.FINALIZE_STATIC
						&& value.instance().progress().completedTargets().equals(List.of(RECIPIENT))
				),
			"remaining final recipients and prior consumption must survive another restart"
		);
		var lastConsumed = MessageRuntimePersistenceAuthority.consumeRecoveredFinal(
			thirdPlan.nextSnapshot().orElseThrow(),
			GAME_INSTANCE,
			finalizing.instanceId(),
			SECOND_RECIPIENT,
			250L
		);
		check(
			lastConsumed.accepted()
				&& lastConsumed.deliveryRequired()
				&& lastConsumed.instanceRetired()
				&& lastConsumed.snapshot().orElseThrow().instances().equals(List.of(continuing))
				&& lastConsumed.snapshot().orElseThrow().tombstones().stream().anyMatch(
					value -> value.instanceId().equals(finalizing.instanceId())
						&& value.status() == TerminalStatus.COMPLETED
				),
			"the last final recipient must be durably recorded before one projection is sent"
		);
		var repeated = MessageRuntimePersistenceAuthority.consumeRecoveredFinal(
			lastConsumed.snapshot().orElseThrow(),
			GAME_INSTANCE,
			finalizing.instanceId(),
			SECOND_RECIPIENT,
			251L
		);
		check(
			repeated.accepted() && !repeated.deliveryRequired(),
			"repeating a durable final consume must not project the content twice"
		);
	}

	private static void checkGameScopeReset() {
		Instance continuing = instance("scope", RestartMode.CONTINUE, 10L);
		Snapshot snapshot = snapshot(List.of(continuing), List.of(), List.of(), List.of());
		WorldStateV4 world = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			activeCore(GAME_INSTANCE),
			List.of(),
			List.of(),
			List.of(),
			Optional.of(snapshot)
		);
		check(
			world.withCore(activeCore(NEXT_GAME_INSTANCE)).messageRuntime().isEmpty(),
			"rotating the active game must clear the persisted message runtime"
		);
		var plan = MessageRuntimePersistenceAuthority.recover(snapshot, NEXT_GAME_INSTANCE, 200L);
		check(
			plan.accepted()
				&& plan.scopeCleared()
				&& plan.actions().isEmpty()
				&& plan.nextSnapshot().orElseThrow().gameInstanceId().equals(NEXT_GAME_INSTANCE)
				&& plan.nextSnapshot().orElseThrow().instances().isEmpty(),
			"direct recovery across game scopes must clear without emitting stale actions"
		);
	}

	private static void checkPermanentRejectedFinalDoesNotRevive() {
		Instance rejected = instance(
			"permanent-reject",
			RestartMode.FINALIZE,
			30L,
			List.of(RECIPIENT)
		);
		var firstRestart = MessageRuntimePersistenceAuthority.recover(
			snapshot(List.of(rejected), List.of(), List.of(), List.of()),
			GAME_INSTANCE,
			100L
		);
		check(
			firstRestart.accepted()
				&& firstRestart.actions().stream().map(value -> value.type()).toList()
					.equals(List.of(RecoveryActionType.FINALIZE_STATIC)),
			"an unconsumed deterministic-reject fixture must initially recover as a final"
		);
		var retired = MessageRuntimePersistenceAuthority.consumeRecoveredFinal(
			firstRestart.nextSnapshot().orElseThrow(),
			GAME_INSTANCE,
			rejected.instanceId(),
			RECIPIENT,
			110L
		);
		check(
			retired.accepted()
				&& retired.deliveryRequired()
				&& retired.instanceRetired()
				&& retired.snapshot().orElseThrow().instances().isEmpty()
				&& retired.snapshot().orElseThrow().tombstones().stream().anyMatch(value ->
					value.instanceId().equals(rejected.instanceId())
						&& value.status() == TerminalStatus.COMPLETED
				),
			"permanent authorization rejection must durably retire without a projection"
		);
		var secondRestart = MessageRuntimePersistenceAuthority.recover(
			retired.snapshot().orElseThrow(),
			GAME_INSTANCE,
			120L
		);
		var thirdRestart = MessageRuntimePersistenceAuthority.recover(
			secondRestart.nextSnapshot().orElseThrow(),
			GAME_INSTANCE,
			130L
		);
		check(
			secondRestart.accepted()
				&& secondRestart.actions().isEmpty()
				&& thirdRestart.accepted()
				&& thirdRestart.actions().isEmpty()
				&& thirdRestart.nextSnapshot().orElseThrow().instances().isEmpty()
				&& thirdRestart.nextSnapshot().orElseThrow().tombstones().stream().anyMatch(value ->
					value.instanceId().equals(rejected.instanceId())
				),
			"a durably rejected final must not revive on the second or later server restart"
		);
	}

	private static void checkNarrowWorldCheckpoint() {
		WorldStateV4 v4Core = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			activeCore(GAME_INSTANCE),
			List.of(),
			List.of()
		);
		WorldStateV5 root = new WorldStateV5(
			WorldStateV5.SCHEMA_VERSION,
			v4Core,
			Optional.empty()
		);
		PixelTzzWorldState state = PixelTzzWorldState.CODEC.parse(
			JsonOps.INSTANCE,
			WorldStateV5.CODEC.encodeStart(JsonOps.INSTANCE, root).getOrThrow()
		).getOrThrow();
		long globalRevision = state.currentV4().orElseThrow().stateRevision();
		Instance continuing = instance("narrow-checkpoint", RestartMode.CONTINUE, 20L);
		Snapshot first = snapshot(List.of(continuing), List.of(), List.of(), List.of());

		var initial = state.messageRuntimeCheckpoint(
			GAME_INSTANCE,
			OptionalLong.empty(),
			Optional.of(first)
		);
		check(
			initial.committed()
				&& state.currentV4().orElseThrow().messageRuntime().equals(Optional.of(first))
				&& state.currentV4().orElseThrow().stateRevision() == globalRevision
				&& state.currentV4().orElseThrow().core().equals(v4Core.core()),
			"message checkpoint must replace only the optional runtime without global revision churn"
		);
		check(
			!state.messageRuntimeCheckpoint(
				GAME_INSTANCE,
				OptionalLong.empty(),
				Optional.of(first)
			).committed(),
			"a stale absent-runtime expectation must be rejected"
		);

		Snapshot later = first.withContent(
			first.instances(),
			first.pendingOffline(),
			first.tombstones(),
			first.callbackLedger(),
			60L,
			first.runtimeRevision()
		);
		check(
			state.messageRuntimeCheckpoint(
				GAME_INSTANCE,
				OptionalLong.of(first.runtimeRevision()),
				Optional.of(later)
			).committed()
				&& state.currentV4().orElseThrow().stateRevision() == globalRevision,
			"same-runtime-revision clock progress may checkpoint without changing global revision"
		);
		check(
			!state.messageRuntimeCheckpoint(
				GAME_INSTANCE,
				OptionalLong.of(later.runtimeRevision()),
				Optional.of(first)
			).committed(),
			"a checkpoint tick must not move backwards"
		);
		Snapshot foreign = new Snapshot(
			NEXT_GAME_INSTANCE,
			70L,
			later.runtimeRevision(),
			later.instances(),
			later.pendingOffline(),
			later.tombstones(),
			later.callbackLedger()
		);
		check(
			!state.messageRuntimeCheckpoint(
				GAME_INSTANCE,
				OptionalLong.of(later.runtimeRevision()),
				Optional.of(foreign)
			).committed(),
			"the narrow transaction must reject a foreign game scope"
		);
		check(
			state.messageRuntimeCheckpoint(
				GAME_INSTANCE,
				OptionalLong.of(later.runtimeRevision()),
				Optional.empty()
			).committed()
				&& state.currentV4().orElseThrow().messageRuntime().isEmpty()
				&& state.currentV4().orElseThrow().stateRevision() == globalRevision,
			"the narrow transaction may clear runtime state without touching lifecycle revisions"
		);
	}

	private static Snapshot snapshot(
		final List<Instance> instances,
		final List<PendingOfflineDelivery> pending,
		final List<Tombstone> tombstones,
		final List<CallbackEntry> callbacks
	) {
		return new Snapshot(
			GAME_INSTANCE,
			50L,
			3L,
			instances,
			pending,
			tombstones,
			new CallbackLedger(16, callbacks)
		);
	}

	private static Instance instance(
		final String key,
		final RestartMode restart,
		final long updatedAt
	) {
		return instance(key, restart, updatedAt, List.of(RECIPIENT));
	}

	private static Instance instance(
		final String key,
		final RestartMode restart,
		final long updatedAt,
		final List<UUID> recipients
	) {
		UUID instanceId = uuid(key);
		return new Instance(
			instanceId,
			CUE,
			restart,
			new FrozenCue(1L, FrozenDocument.of("{\"format_version\":1,\"id\":\"" + key + "\"}")),
			new InvocationContext(
				Optional.empty(),
				recipients,
				Optional.of(GAME),
				Optional.of(PHASE),
				Optional.empty(),
				Map.of("source", "self_check"),
				false
			),
			Map.of("name", "Player"),
			new AudienceState(recipients, List.of()),
			new TimingState(ClockMode.GAME_TIME, Map.of(), Optional.of(2_000_000L)),
			new ProgressState(
				InstanceStatus.ACTIVE,
				Optional.empty(),
				0L,
				1_000_000L,
				List.of(),
				List.of(),
				List.of(),
				false,
				1L
			),
			recipients.stream().map(recipient ->
				new RecipientState(
					recipient,
					0L,
					Optional.empty(),
					List.of(),
					List.of(),
					List.of(),
					List.of(),
					Optional.empty(),
					false
				)
			).toList(),
			Optional.empty(),
			Optional.empty(),
			updatedAt
		);
	}

	private static PendingOfflineDelivery pending(
		final UUID instanceId,
		final UUID recipientId,
		final long queuedAt,
		final long expiresAt
	) {
		return new PendingOfflineDelivery(
			instanceId,
			recipientId,
			OfflineMode.QUEUE,
			queuedAt,
			Optional.of(expiresAt),
			Optional.empty()
		);
	}

	private static CallbackEntry callback(
		final Instance instance,
		final CallbackStatus status
	) {
		return new CallbackEntry(
			new CallbackKey(
				instance.instanceId(),
				0L,
				"root",
				0,
				CallbackTrigger.START,
				Optional.empty()
			),
			Identifier.parse("test:callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN,
			status,
			1,
			25L,
			status == CallbackStatus.FAILED || status == CallbackStatus.OUTCOME_UNKNOWN
				? Optional.of("diagnostic")
				: Optional.empty()
		);
	}

	private static WorldStateV3 activeCore(final UUID gameInstanceId) {
		return WorldStateV3.initial().beginGameInstance(GAME, PHASE, gameInstanceId);
	}

	private static UUID uuid(final String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
