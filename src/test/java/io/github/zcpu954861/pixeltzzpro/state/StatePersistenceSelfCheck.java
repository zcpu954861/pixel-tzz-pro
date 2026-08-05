package io.github.zcpu954861.pixeltzzpro.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.ReloadStatus;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.View;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState.CommitResult;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState.CommitV3Result;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState.CommitV4Result;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState.LoadKind;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldStateMigration.MigrationResult;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldStateMigration.MigrationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.BlockDiagnostic;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackFailure;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackReferences;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ExecutionSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowIdentity;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDefinitionType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenSourceDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostOperation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChange;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RecoverySnapshotMetadata;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RemovalRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStepStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.IntermissionState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionInvocation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionInvocationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.PlayerActionUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/**
 * Runnable schema-v4 envelope, legacy migration, and recovery contract check without a test
 * framework.
 */
public final class StatePersistenceSelfCheck {
	private static final UUID HOST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID REMOVED_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
	private static final UUID PAGE_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
	private static final UUID TIMELINE_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
	private static final UUID TASK_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
	private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
	private static final UUID SECOND_RESERVATION_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000009");
	private static final UUID SECOND_SCOPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID GAME_INSTANCE_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000011");
	private static final UUID ACTION_REQUEST_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000012");
	private static final UUID NEXT_GAME_INSTANCE_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000013");
	private static final Identifier GAME_ID = id("test:game");
	private static final Identifier PHASE_ID = id("test:setup");
	private static final Identifier ROLE_ID = id("test:runner");
	private static final Identifier HUNTER_ROLE_ID = id("test:hunter");
	private static final Identifier LIFE_STATE_ID = id("test:alive");
	private static final Identifier FLOW_ID = id("test:tutorial");
	private static final Identifier ACTION_ID = id("test:start_tutorial");

	private StatePersistenceSelfCheck() {
	}

	public static void main(final String[] args) throws Exception {
		checkInitialAndCommit();
		checkCompleteV2RoundTrip();
		checkCompleteV3RoundTrip();
		checkPlayerActionLedgerRoundTripAndScopeRotation();
		checkMessageHistoryCompatibilityAndRoundTrip();
		checkOversizedFrozenDocumentBinaryNbtRoundTrip();
		checkMissingTimelineSnapshotRepair();
		checkDevelopmentV3ScopeRepair();
		checkSnapshotSizeIncludesMetadata();
		checkTerminalSnapshotCompaction();
		checkLegacyAndOpaqueClassification();
		checkConservativeMigration();
		System.out.println("STATE_PERSISTENCE_SELF_CHECK=PASS");
	}

	private static void checkInitialAndCommit() {
		PixelTzzWorldState state = PixelTzzWorldState.initial();
		check(state.loadKind() == LoadKind.CURRENT_V5, "new worlds must use schema v5");
		check(state.schemaVersion() == WorldStateV5.SCHEMA_VERSION, "new world schema");
		check(state.isSchemaCompatible(), "new schema-v5 state must be writable");
		check(state.stateRevision() == 0L, "new state revision must be zero");
		check(state.activeGameId().isEmpty() && state.activePhaseId().isEmpty(), "new worlds must not invent an activity");

		CommitV3Result committed = state.commitV3(
			0L,
			value -> value.beginGameInstance(GAME_ID, PHASE_ID, GAME_INSTANCE_ID)
		);
		check(committed.committed(), "valid expected-revision commit must succeed: " + committed.reason());
		check(state.stateRevision() == 1L, "commit must increment state revision exactly once");
		check(state.isDirty(), "commit must mark SavedData dirty");
		check(state.activeGameId().orElseThrow().equals(GAME_ID), "commit must publish the complete candidate");
		check(
			state.currentV3().orElseThrow().activeGameInstanceId().orElseThrow().equals(GAME_INSTANCE_ID),
			"game activity initialization must publish one persistent instance scope"
		);

		CommitResult stale = state.commit(0L, value -> value.withHost(Optional.of(HostRecord.migratedLegacy(HOST_ID))));
		check(!stale.committed(), "stale expected revision must be rejected");
		check(state.stateRevision() == 1L && state.hostId().isEmpty(), "rejected commit must not partially mutate state");
		check(
			state.currentV3().orElseThrow().timeline().isEmpty()
				&& state.currentV3().orElseThrow().exclusiveReservations().isEmpty(),
			"legacy core commit must preserve empty v3 extensions"
		);
		check(
			state.currentV4().orElseThrow().usages().isEmpty()
				&& state.currentV4().orElseThrow().invocations().isEmpty(),
			"legacy adapters must preserve empty v4 action ledgers"
		);
	}

	private static void checkCompleteV2RoundTrip() {
		WorldStateV2 value = completeFixture();
		JsonElement encoded = WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
		WorldStateV2 decoded = WorldStateV2.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(decoded.equals(value), "complete schema-v2 payload must round-trip exactly");
		check(
			decoded.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID).memberRevision() == 3L,
			"member revision must persist"
		);
		check(
			decoded.activeForcedFlow().orElseThrow().runtime().totalCount() == 1,
			"removed members must not remain in the active denominator"
		);
		check(
			decoded.activeForcedFlow().orElseThrow().runtime().executionSnapshot().supportingDefinitions().size() == 2,
			"snapshot supporting definition closure must persist"
		);

		Map<UUID, PlayerRecord> mutablePlayers = new LinkedHashMap<>(value.players());
		WorldStateV2 copied = value.withPlayers(mutablePlayers);
		mutablePlayers.clear();
		check(copied.players().size() == 1, "v2 copies must not retain mutable map aliases");
		check(
			value.withHost(Optional.empty()).players().equals(value.players()),
			"copy helpers must preserve unrelated payload fields"
		);

		PixelTzzWorldState wrapper = PixelTzzWorldState.CODEC
			.parse(JsonOps.INSTANCE, encoded)
			.getOrThrow();
		check(wrapper.loadKind() == LoadKind.LEGACY_V2, "schema v2 must be classified as migratable legacy");
		check(wrapper.currentV2().isEmpty(), "legacy schema v2 must not be exposed as writable current state");
		check(wrapper.legacyV2().orElseThrow().equals(value), "SavedData wrapper must retain the complete v2 payload");
		check(encode(wrapper).equals(encoded), "unmigrated schema v2 must be preserved exactly");
	}

	private static void checkCompleteV3RoundTrip() {
		WorldStateV2 core = completeFixture();
		CallbackStep callback = new CallbackStep(
			"result/apply/global",
			id("test:apply_result"),
			Optional.empty(),
			CallbackStepStatus.SUCCEEDED,
			1,
			Optional.empty(),
			500L
		);
		FrozenResult result = new FrozenResult(
			"success",
			Optional.of(id("test:next_task")),
			Optional.empty(),
			500L
		);
		TaskInstance task = new TaskInstance(
			TASK_INSTANCE_ID,
			id("test:task"),
			TaskKind.MAIN,
			TaskStatus.SETTLED,
			Optional.empty(),
			400L,
			Optional.of(result),
			Optional.of(new IntermissionState(200L, 20L, true, false)),
			List.of(PLAYER_ID),
			100L,
			Optional.of(500L),
			List.of(callback)
		);
		TimelineInstance timeline = new TimelineInstance(
			TIMELINE_INSTANCE_ID,
			GAME_ID,
			1,
			FrozenDocument.of("{\"initial_task\":\"test:task\"}"),
			TimelineStatus.INTERMISSION,
			Optional.empty(),
			Optional.of(task),
			List.of(),
			500L,
			false,
			Optional.empty(),
			10L,
			Optional.of(20L),
			Optional.empty(),
			3L,
			List.of()
		);
		ExclusiveReservation reservation = new ExclusiveReservation(
			RESERVATION_ID,
			GAME_ID,
			id("test:hunter_spawn"),
			TIMELINE_INSTANCE_ID,
			"north_gate",
			PLAYER_ID,
			ReservationStatus.HELD,
			Optional.of(INSTANCE_ID),
			10L,
			20L,
			1L
		);
		ExclusiveReservation sameOptionInAnotherScope = new ExclusiveReservation(
			SECOND_RESERVATION_ID,
			GAME_ID,
			reservation.fieldId(),
			SECOND_SCOPE_ID,
			reservation.value(),
			REMOVED_PLAYER_ID,
			ReservationStatus.LOCKED,
			Optional.empty(),
			10L,
			20L,
			1L
		);
		RecoverySnapshotMetadata recovery = new RecoverySnapshotMetadata(
			"world_state-v2-to-v3-800.dat",
			800L,
			2,
			100L,
			"b".repeat(64)
		);
		WorldStateV3 value = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(reservation.scopeId()),
			Optional.of(timeline),
			List.of(reservation, sameOptionInAnotherScope),
			List.of(core.recoverySnapshotMetadata().orElseThrow(), recovery)
		);
		check(
			value.validated().error().isEmpty(),
			"the same option value must remain available in another reservation scope"
		);
		ExclusiveReservation duplicateInSameScope = new ExclusiveReservation(
			SECOND_RESERVATION_ID,
			GAME_ID,
			reservation.fieldId(),
			reservation.scopeId(),
			reservation.value(),
			REMOVED_PLAYER_ID,
			ReservationStatus.LOCKED,
			Optional.empty(),
			10L,
			20L,
			1L
		);
		check(
			value.withExclusiveReservations(List.of(reservation, duplicateInSameScope))
				.validated()
				.error()
				.isPresent(),
			"an option value must remain exclusive inside one game, field, and scope"
		);
		JsonElement encoded = WorldStateV3.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
		WorldStateV3 decoded = WorldStateV3.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(decoded.equals(value), "complete schema-v3 payload must round-trip exactly");

		PixelTzzWorldState legacyWrapper = decode(encoded);
		check(legacyWrapper.loadKind() == LoadKind.LEGACY_V3, "schema v3 must be migratable legacy");
		check(
			legacyWrapper.currentV3().isEmpty()
				&& legacyWrapper.legacyV3().orElseThrow().equals(value),
			"legacy schema v3 must not be exposed as writable current state"
		);
		check(encode(legacyWrapper).equals(encoded), "unmigrated schema v3 must be preserved exactly");

		WorldStateV4 root = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			value,
			List.of(),
			List.of()
		);
		PixelTzzWorldState wrapper = decode(currentV5Json(root));
		check(wrapper.loadKind() == LoadKind.CURRENT_V5, "schema v5 must be current");
		var encodedNbt = PixelTzzWorldState.CODEC
			.encodeStart(NbtOps.INSTANCE, wrapper)
			.getOrThrow();
		PixelTzzWorldState decodedNbt = PixelTzzWorldState.CODEC
			.parse(NbtOps.INSTANCE, encodedNbt)
			.getOrThrow();
		check(
			decodedNbt.isSchemaCompatible()
				&& decodedNbt.currentV4().orElseThrow().equals(root),
			"real SavedData NBT round-trip must preserve schema-v4 and nested v3 booleans"
		);
		CommitResult coreCommit = wrapper.commit(
			core.stateRevision(),
			current -> current.withHost(Optional.empty())
		);
		check(coreCommit.committed(), "schema-v2 core adapter commit must work inside v3");
		check(
			wrapper.currentV3().orElseThrow().timeline().equals(Optional.of(timeline))
				&& wrapper.currentV3().orElseThrow().exclusiveReservations().size() == 2
				&& wrapper.currentV4().orElseThrow().usages().isEmpty(),
			"core adapter commit must preserve v3 extensions and v4 ledgers"
		);
		long checkpointCoreRevision = wrapper.stateRevision();
		var checkpoint = wrapper.timelineCheckpoint(
			timeline.timelineRevision(),
			current -> withGameElapsedTicks(current, current.gameElapsedTicks() + 1L)
		);
		check(checkpoint.committed(), "routine timeline checkpoint must succeed: " + checkpoint.reason());
		check(
			wrapper.stateRevision() == checkpointCoreRevision,
			"routine timeline checkpoint must not increment the global state revision"
		);
		check(
			checkpoint.timeline().orElseThrow().timelineRevision() == timeline.timelineRevision()
				&& checkpoint.timeline().orElseThrow().gameElapsedTicks() == timeline.gameElapsedTicks() + 1L,
			"routine timeline checkpoint must preserve lifecycle revision and publish the clock"
		);
		var staleCheckpoint = wrapper.timelineCheckpoint(
			timeline.timelineRevision() + 1L,
			current -> withGameElapsedTicks(current, current.gameElapsedTicks() + 1L)
		);
		check(!staleCheckpoint.committed(), "stale timeline checkpoint must be rejected");
		check(
			wrapper.currentV3().orElseThrow().timeline().orElseThrow().gameElapsedTicks()
				== timeline.gameElapsedTicks() + 1L,
			"rejected timeline checkpoint must not partially mutate the clock"
		);
		long nextRevision = wrapper.stateRevision();
		var rootCommit = wrapper.commitV3(
			nextRevision,
			current -> current.withTimeline(Optional.empty())
		);
		check(rootCommit.committed(), "native schema-v3 commit must succeed");
		check(wrapper.stateRevision() == nextRevision + 1L, "native v3 commit must increment core revision once");
		check(wrapper.currentV3().orElseThrow().timeline().isEmpty(), "native v3 commit must publish extensions");
		check(
			!wrapper.timelineCheckpoint(0L, current -> current).committed(),
			"timeline checkpoint must reject worlds without an active timeline"
		);
	}

	private static void checkPlayerActionLedgerRoundTripAndScopeRotation() {
		WorldStateV3 core = WorldStateV3.initial()
			.beginGameInstance(GAME_ID, PHASE_ID, GAME_INSTANCE_ID);
		PlayerActionUsage playerUsage = new PlayerActionUsage(
			GAME_INSTANCE_ID,
			ACTION_ID,
			Optional.of(PLAYER_ID),
			1L,
			100L
		);
		PlayerActionUsage globalUsage = new PlayerActionUsage(
			GAME_INSTANCE_ID,
			ACTION_ID,
			Optional.empty(),
			2L,
			101L
		);
		PlayerActionInvocation prepared = new PlayerActionInvocation(
			GAME_INSTANCE_ID,
			PLAYER_ID,
			ACTION_ID,
			ACTION_REQUEST_ID,
			PlayerActionInvocationStatus.PREPARED,
			Optional.empty(),
			Optional.empty(),
			100L
		);
		WorldStateV4 value = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			core,
			List.of(playerUsage, globalUsage),
			List.of(prepared)
		);
		check(
			value.usages().equals(List.of(globalUsage, playerUsage)),
			"v4 usage serialization order must be stable with the global bucket first"
		);
		JsonElement encoded = WorldStateV4.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
		WorldStateV4 decoded = WorldStateV4.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(decoded.equals(value), "schema-v4 player action ledgers must round-trip exactly");
		check(
			decoded.usage(GAME_INSTANCE_ID, ACTION_ID, Optional.of(PLAYER_ID))
				.orElseThrow()
				.equals(playerUsage),
			"player-scoped usage must remain queryable"
		);
		check(
			decoded.invocation(GAME_INSTANCE_ID, PLAYER_ID, ACTION_ID, ACTION_REQUEST_ID)
				.orElseThrow()
				.outcomeUnknown(),
			"a persisted PREPARED invocation must remain outcome-unknown after reload"
		);

		PixelTzzWorldState wrapper = decode(currentV5Json(value));
		CommitV4Result terminalCommit = wrapper.commitV4(
			0L,
			current -> current
				.withUsages(
					List.of(
						globalUsage,
						new PlayerActionUsage(
							GAME_INSTANCE_ID,
							ACTION_ID,
							Optional.of(PLAYER_ID),
							2L,
							120L
						)
					)
				)
				.withInvocations(
					List.of(
						new PlayerActionInvocation(
							GAME_INSTANCE_ID,
							PLAYER_ID,
							ACTION_ID,
							ACTION_REQUEST_ID,
							PlayerActionInvocationStatus.SUCCEEDED,
							Optional.of("ok"),
							Optional.of("Door opened"),
							120L
						)
					)
				)
		);
		check(
			terminalCommit.committed(),
			"native v4 action-ledger commit must succeed: " + terminalCommit.reason()
		);
		check(
			wrapper.currentV4().orElseThrow()
				.invocation(GAME_INSTANCE_ID, PLAYER_ID, ACTION_ID, ACTION_REQUEST_ID)
				.orElseThrow()
				.status() == PlayerActionInvocationStatus.SUCCEEDED,
			"the terminal idempotency result must replace PREPARED durably"
		);

		var encodedNbt = PixelTzzWorldState.CODEC
			.encodeStart(NbtOps.INSTANCE, wrapper)
			.getOrThrow();
		PixelTzzWorldState reloaded = PixelTzzWorldState.CODEC
			.parse(NbtOps.INSTANCE, encodedNbt)
			.getOrThrow();
		check(
			reloaded.currentV4().orElseThrow().equals(wrapper.currentV4().orElseThrow()),
			"action usage and invocation results must survive SavedData NBT reload"
		);

		CommitV3Result nextGame = reloaded.commitV3(
			reloaded.stateRevision(),
			current -> current.beginGameInstance(GAME_ID, PHASE_ID, NEXT_GAME_INSTANCE_ID)
		);
		check(nextGame.committed(), "starting another game instance must succeed");
		check(
			reloaded.currentV4().orElseThrow().usages().isEmpty()
				&& reloaded.currentV4().orElseThrow().invocations().isEmpty(),
			"starting another game instance must clear action state from the old scope"
		);

		CommitV4Result secondScopeUsage = reloaded.commitV4(
			reloaded.stateRevision(),
			current -> current.withUsages(
				List.of(
					new PlayerActionUsage(
						NEXT_GAME_INSTANCE_ID,
						ACTION_ID,
						Optional.of(PLAYER_ID),
						1L,
						200L
					)
				)
			)
		);
		check(secondScopeUsage.committed(), "the new game scope must accept fresh action usage");
		CommitV3Result reset = reloaded.commitV3(
			reloaded.stateRevision(),
			ignored -> WorldStateV3.initial()
		);
		check(reset.committed(), "game-state reset must succeed through the v3 compatibility API");
		check(
			reloaded.currentV4().orElseThrow().usages().isEmpty()
				&& reloaded.currentV4().orElseThrow().invocations().isEmpty(),
			"game-state reset must clear action ledgers"
		);

		check(
			new WorldStateV4(
				WorldStateV4.SCHEMA_VERSION,
				core,
				List.of(playerUsage, playerUsage),
				List.of()
			).validated().error().isPresent(),
			"duplicate action usage keys must be rejected"
		);
		check(
			PlayerActionInvocation.CODEC.encodeStart(
				JsonOps.INSTANCE,
				new PlayerActionInvocation(
					GAME_INSTANCE_ID,
					PLAYER_ID,
					ACTION_ID,
					ACTION_REQUEST_ID,
					PlayerActionInvocationStatus.PREPARED,
					Optional.of("invalid"),
					Optional.empty(),
					100L
				)
			).error().isPresent(),
			"PREPARED invocation must not claim a terminal result"
		);
	}

	private static void checkMessageHistoryCompatibilityAndRoundTrip() {
		WorldStateV3 core = WorldStateV3.initial()
			.beginGameInstance(GAME_ID, PHASE_ID, GAME_INSTANCE_ID);
		MessageHistoryRecord record = new MessageHistoryRecord(
			GAME_INSTANCE_ID,
			PLAYER_ID,
			id("test:message_notice"),
			UUID.nameUUIDFromBytes("message-history".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
			Optional.of(id("test:opening_task")),
			240L,
			"『任务提示回顾』",
			"玩家只会看到服务器已经为自己解析并冻结的正文。",
			Map.of("target_name", "Player"),
			false
		);
		WorldStateV4 value = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			core,
			List.of(),
			List.of(),
			List.of(record)
		);
		JsonElement encoded = WorldStateV4.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
		WorldStateV4 decoded = WorldStateV4.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(decoded.equals(value), "resolved per-viewer message history must round-trip exactly");
		PixelTzzWorldState persisted = decode(currentV5Json(value));
		Tag encodedNbt = PixelTzzWorldState.CODEC
			.encodeStart(NbtOps.INSTANCE, persisted)
			.getOrThrow();
		PixelTzzWorldState reloaded = PixelTzzWorldState.CODEC
			.parse(NbtOps.INSTANCE, encodedNbt)
			.getOrThrow();
		check(
			reloaded.currentV4().orElseThrow().equals(value),
			"resolved message history must survive the real SavedData NBT path"
		);
		check(
			decoded.messageHistoryFor(PLAYER_ID).equals(List.of(record))
				&& decoded.messageHistoryFor(HOST_ID).isEmpty(),
			"message history lookup must preserve its exact authorized viewer boundary"
		);

		com.google.gson.JsonObject oldV4 = encoded.deepCopy().getAsJsonObject();
		oldV4.remove("message_history");
		WorldStateV4 decodedOldV4 = WorldStateV4.CODEC
			.parse(JsonOps.INSTANCE, oldV4)
			.getOrThrow();
		check(
			decodedOldV4.messageHistory().isEmpty()
				&& decodedOldV4.core().equals(core),
			"schema-v4 worlds written before V3B must decode with an empty optional history ledger"
		);
		check(
			new WorldStateV4(
				WorldStateV4.SCHEMA_VERSION,
				core,
				List.of(),
				List.of()
			).messageHistory().isEmpty(),
			"the retained four-argument constructor must default message history to empty"
		);
		check(
			value.withCore(
				WorldStateV3.initial().beginGameInstance(
					GAME_ID,
					PHASE_ID,
					NEXT_GAME_INSTANCE_ID
				)
			).messageHistory().isEmpty(),
			"rotating the active game instance must not inherit old message history"
		);
	}

	private static void checkOversizedFrozenDocumentBinaryNbtRoundTrip() throws Exception {
		FrozenDocument oversized = FrozenDocument.of(
			"{\"payload\":\"" + "验".repeat(30_000) + "\"}"
		);
		check(
			oversized.normalizedJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65_535,
			"binary NBT regression fixture must exceed the modified-UTF string limit"
		);
		WorldStateV3 value = timelineFixture(oversized);
		PixelTzzWorldState wrapper = decode(
			currentV5Json(
				new WorldStateV4(WorldStateV4.SCHEMA_VERSION, value, List.of(), List.of())
			)
		);
		Tag encoded = PixelTzzWorldState.CODEC
			.encodeStart(NbtOps.INSTANCE, wrapper)
			.getOrThrow();
		check(encoded instanceof CompoundTag, "SavedData must encode to an NBT compound");

		Path temporaryFile = Files.createTempFile("pixel-tzz-oversized-snapshot-", ".dat");
		try {
			NbtIo.writeCompressed((CompoundTag) encoded, temporaryFile);
			CompoundTag reloaded = NbtIo.readCompressed(
				temporaryFile,
				NbtAccounter.unlimitedHeap()
			);
			PixelTzzWorldState decoded = PixelTzzWorldState.CODEC
				.parse(NbtOps.INSTANCE, reloaded)
				.getOrThrow();
			FrozenDocument restored = decoded.currentV3()
				.orElseThrow()
				.timeline()
				.orElseThrow()
				.snapshot();
			check(decoded.isSchemaCompatible(), "chunked binary NBT state must remain writable");
			check(restored.equals(oversized), "binary NBT must preserve oversized snapshot content and SHA-256");
			check(!restored.normalizedJson().isEmpty(), "binary NBT must not silently blank oversized content");
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	private static void checkMissingTimelineSnapshotRepair() {
		FrozenDocument original = FrozenDocument.of(
			"{\"payload\":\"" + "验".repeat(30_000) + "\"}"
		);
		WorldStateV3 value = timelineFixture(original);
		com.google.gson.JsonObject damaged = WorldStateV4.CODEC
			.encodeStart(
				JsonOps.INSTANCE,
				new WorldStateV4(WorldStateV4.SCHEMA_VERSION, value, List.of(), List.of())
			)
			.getOrThrow()
			.getAsJsonObject();
		com.google.gson.JsonObject snapshot = damaged
			.getAsJsonObject("core")
			.getAsJsonObject("timeline")
			.getAsJsonObject("snapshot");
		snapshot.remove("normalized_json_chunks");
		snapshot.addProperty("normalized_json", "");

		PixelTzzWorldState protectedState = decode(damaged);
		check(
			protectedState.loadKind() == LoadKind.LEGACY_V4,
			"legacy oversized-string damage must retain the complete schema-v4 envelope"
		);
		check(
			protectedState.needsTimelineSnapshotRepair(),
			"legacy oversized-string damage must be classified as repairable"
		);
		check(
			!protectedState.isSchemaCompatible(),
			"repairable damage must remain read-only before exact recovery"
		);
		long protectedRevision = protectedState.stateRevision();

		var rejected = protectedState.repairMissingTimelineSnapshot(
			FrozenDocument.of("{\"payload\":\"different\"}")
		);
		check(!rejected.committed(), "different snapshot SHA-256 must be rejected");
		check(
			protectedState.needsTimelineSnapshotRepair()
				&& protectedState.stateRevision() == protectedRevision,
			"rejected repair must preserve the protected payload and revision"
		);

		var repaired = protectedState.repairMissingTimelineSnapshot(original);
		check(repaired.committed(), "exact snapshot SHA-256 must repair legacy NBT damage");
		check(!protectedState.isSchemaCompatible(), "legacy v4 remains read-only until verified v5 migration");
		check(!protectedState.needsTimelineSnapshotRepair(), "successful exact repair must clear repair mode");
		check(
			protectedState.stateRevision() == protectedRevision,
			"startup-only snapshot repair must not revise the source before its verified backup"
		);
		check(
			protectedState.legacyV4()
				.orElseThrow()
				.core()
				.timeline()
				.orElseThrow()
				.snapshot()
				.equals(original),
			"successful exact repair must restore the complete snapshot"
		);
	}

	private static WorldStateV3 timelineFixture(final FrozenDocument snapshot) {
		WorldStateV2 core = completeFixture();
		TimelineInstance timeline = new TimelineInstance(
			TIMELINE_INSTANCE_ID,
			GAME_ID,
			1,
			snapshot,
			TimelineStatus.PRE_START,
			Optional.empty(),
			Optional.empty(),
			List.of(),
			0L,
			false,
			Optional.empty(),
			10L,
			Optional.empty(),
			Optional.empty(),
			1L,
			List.of()
		);
		WorldStateV3 value = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(TIMELINE_INSTANCE_ID),
			Optional.of(timeline),
			List.of(),
			List.of(core.recoverySnapshotMetadata().orElseThrow())
		);
		check(value.validated().error().isEmpty(), "timeline persistence fixture must be valid");
		return value;
	}

	private static void checkDevelopmentV3ScopeRepair() {
		WorldStateV3 value = WorldStateV3.initial()
			.beginGameInstance(GAME_ID, PHASE_ID, GAME_INSTANCE_ID);
		com.google.gson.JsonObject legacy = WorldStateV3.CODEC
			.encodeStart(JsonOps.INSTANCE, value)
			.getOrThrow()
			.getAsJsonObject();
		legacy.remove("active_game_instance_id");
		PixelTzzWorldState repaired = decode(legacy);
		check(repaired.loadKind() == LoadKind.LEGACY_V3, "pre-scope schema v3 must remain migratable");
		check(!repaired.isDirty(), "startup migration must own persistence of the v3 scope repair");
		check(
			repaired.legacyV3().orElseThrow().activeGameInstanceId().isPresent(),
			"v3 migration candidate must receive a persistent active game-instance ID"
		);
		check(encode(repaired).equals(legacy), "unmigrated v3 bytes must remain logically unchanged");
	}

	private static TimelineInstance withGameElapsedTicks(
		final TimelineInstance value,
		final long gameElapsedTicks
	) {
		return new TimelineInstance(
			value.instanceId(),
			value.gameId(),
			value.contentVersion(),
			value.snapshot(),
			value.status(),
			value.resumeStatus(),
			value.currentTask(),
			value.taskHistory(),
			gameElapsedTicks,
			value.paused(),
			value.pauseReason(),
			value.createdAtServerTick(),
			value.startedAtServerTick(),
			value.completedAtServerTick(),
			value.timelineRevision(),
			value.callbackLedger()
		);
	}

	private static WorldStateV2 completeFixture() {
		FrozenDocument flowDocument = FrozenDocument.of("{\"game\":\"test:game\",\"entry\":\"intro\"}");
		FrozenDocument pageDocument = FrozenDocument.of("{\"game\":\"test:game\",\"title\":\"Tutorial\"}");
		FrozenDocument themeDocument = FrozenDocument.of("{\"colors\":{\"accent\":\"#d6af52\"}}");
		FrozenDocument fieldDocument = FrozenDocument.of("{\"game\":\"test:game\",\"type\":\"boolean\"}");
		ExecutionSnapshot snapshot = new ExecutionSnapshot(
			9L,
			flowDocument,
			Map.of(id("test:tutorial_page"), pageDocument),
			Map.of(id("test:theme"), themeDocument),
			Map.of(id("test:accepted"), fieldDocument),
			List.of(
				new FrozenSourceDocument(
					FrozenDefinitionType.GAME,
					GAME_ID,
					FrozenDocument.of("{\"initial_phase\":\"test:setup\"}")
				),
				new FrozenSourceDocument(
					FrozenDefinitionType.ROLE,
					ROLE_ID,
					FrozenDocument.of("{\"game\":\"test:game\"}")
				)
			),
			Set.of(id("test:can_continue")),
			Map.of(
				id("test:can_continue"),
				FrozenDocument.of("{\"condition\":\"minecraft:random_chance\",\"chance\":1.0}")
			),
			Set.of(
				id("test:on_phase_enter"),
				id("test:on_start"),
				id("test:on_player_complete"),
				id("test:on_all_complete")
			),
			new CallbackReferences(
				Optional.of(id("test:on_start")),
				Optional.of(id("test:on_player_complete")),
				Optional.of(id("test:on_all_complete"))
			),
			Set.of("pixel_tzz.test.tutorial"),
			Set.of(id("minecraft:block.note_block.pling")),
			"Starts the short 2C acceptance tutorial.",
			"0".repeat(64)
		).withComputedContentSha256();
		check(
			snapshot.computeContentSha256().equals(snapshot.contentSha256()),
			"snapshot aggregate hash must include the complete frozen closure"
		);

		FlowMemberState completedMember = new FlowMemberState(
			PLAYER_ID,
			"Runner",
			Optional.of("complete"),
			FlowMemberStatus.COMPLETED,
			3L,
			Map.of(id("test:accepted"), StoredValue.ofBoolean(true)),
			Optional.of(PAGE_INSTANCE_ID),
			new RequestSequenceWindow(7L, 0b101L),
			Optional.of(1_100L),
			false,
			Optional.of(1_090L),
			Optional.of(1_100L),
			Optional.empty()
		);
		FlowMemberState removedMember = new FlowMemberState(
			REMOVED_PLAYER_ID,
			"Removed",
			Optional.of("intro"),
			FlowMemberStatus.REMOVED,
			2L,
			Map.of(),
			Optional.empty(),
			RequestSequenceWindow.empty(),
			Optional.of(1_080L),
			true,
			Optional.of(1_070L),
			Optional.empty(),
			Optional.of(new RemovalRecord(HOST_ID, 1_080L, "Removed by host during acceptance check"))
		);
		CallbackState callbacks = new CallbackState(
			true,
			Set.of(PLAYER_ID),
			false,
			List.of(
				new CallbackFailure(
					CallbackKind.ON_PLAYER_COMPLETE,
					id("test:on_player_complete"),
					Optional.of(PLAYER_ID),
					"Acceptance fixture callback failure",
					1_101L,
					1
				)
			)
		);
		ForcedFlowInstance forcedFlow = new ForcedFlowInstance(
			new ForcedFlowIdentity(
				INSTANCE_ID,
				GAME_ID,
				FLOW_ID,
				2,
				ACTION_ID,
				HOST_ID,
				1_000L,
				PHASE_ID,
				9L,
				CompletionPolicy.ALWAYS
			),
			new ForcedFlowRuntime(
				FlowInstanceStatus.BLOCKED,
				Map.of(PLAYER_ID, completedMember, REMOVED_PLAYER_ID, removedMember),
				1,
				1,
				5L,
				snapshot,
				callbacks,
				Optional.of(
					new BlockDiagnostic(
						"CALLBACK_FAILED",
						"Player completion callback failed and awaits host retry.",
						1_101L,
						Optional.of(PLAYER_ID),
						Optional.of(id("test:on_player_complete"))
					)
				),
				1_101L
			)
		);

		PendingRoleChange pendingRoleChange = new PendingRoleChange(
			PLAYER_ID,
			HUNTER_ROLE_ID,
			ACTION_ID,
			INSTANCE_ID,
			HOST_ID,
			1_000L,
			ROLE_ID,
			6L,
			PendingRoleChangeStatus.PENDING
		);
		PlayerRecord player = new PlayerRecord(
			PLAYER_ID,
			"Runner",
			ROLE_ID,
			Optional.empty(),
			LIFE_STATE_ID,
			Map.of(id("test:accepted"), new PersistentFieldValue(1, StoredValue.ofBoolean(true))),
			Optional.of(pendingRoleChange),
			Optional.of(new PlayerFlowParticipation(INSTANCE_ID, FlowMemberStatus.COMPLETED)),
			new CompletionSummary(1L, Optional.of(1_100L)),
			900L,
			1_100L
		);
		CompletionRecord completion = new CompletionRecord(
			PLAYER_ID,
			FLOW_ID,
			2,
			INSTANCE_ID,
			1_100L,
			ACTION_ID,
			true,
			9L
		);
		AuditLog audit = new AuditLog(
			2L,
			List.of(
				new AuditEvent(
					0L,
					AuditEventType.FLOW_CREATED,
					1_000L,
					Optional.of(HOST_ID),
					Optional.empty(),
					Optional.of(INSTANCE_ID),
					"Created acceptance fixture flow"
				),
				new AuditEvent(
					1L,
					AuditEventType.CALLBACK_FAILED,
					1_101L,
					Optional.empty(),
					Optional.of(PLAYER_ID),
					Optional.of(INSTANCE_ID),
					"Player callback failed"
				)
			)
		);
		return new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			7L,
			Optional.of(GAME_ID),
			Optional.of(PHASE_ID),
			Optional.of(
				new HostRecord(
					HOST_ID,
					Optional.of("Host"),
					Optional.of(800L),
					Optional.of(HOST_ID),
					Optional.of(HostOperation.CLAIM)
				)
			),
			Map.of(PLAYER_ID, player),
			Optional.of(forcedFlow),
			List.of(completion),
			audit,
			Optional.of(
				new RecoverySnapshotMetadata(
					"world_state-v1-to-v2-700.dat",
					700L,
					1,
					90L,
					"a".repeat(64)
				)
			)
		);
	}

	private static void checkSnapshotSizeIncludesMetadata() {
		ExecutionSnapshot oversized = new ExecutionSnapshot(
			1L,
			FrozenDocument.of("x".repeat(WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES)),
			Map.of(),
			Map.of(),
			Map.of(),
			List.of(),
			Set.of(id("test:predicate")),
			Map.of(),
			Set.of(),
			new CallbackReferences(Optional.empty(), Optional.empty(), Optional.empty()),
			Set.of(),
			Set.of(),
			"",
			"0".repeat(64)
		).withComputedContentSha256();
		check(
			ExecutionSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, oversized).error().isPresent(),
			"snapshot UTF-8 limit must include reference metadata, not only normalized documents"
		);
	}

	private static void checkTerminalSnapshotCompaction() {
		ExecutionSnapshot active = completeFixture()
			.activeForcedFlow()
			.orElseThrow()
			.runtime()
			.executionSnapshot();
		ExecutionSnapshot compacted = active.compactedForTerminalState();
		check(compacted.pages().isEmpty(), "terminal snapshot must discard pages");
		check(compacted.themes().isEmpty(), "terminal snapshot must discard themes");
		check(compacted.fields().isEmpty(), "terminal snapshot must discard field definitions");
		check(
			compacted.predicateDocuments().isEmpty(),
			"terminal snapshot must discard frozen predicates"
		);
		check(
			compacted.callbackReferences().equals(active.callbackReferences()),
			"terminal snapshot must retain callback identity for explicit retry"
		);
		check(
			ExecutionSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, compacted).error().isEmpty(),
			"terminal snapshot must remain persistable"
		);
	}

	private static void checkLegacyAndOpaqueClassification() {
		JsonElement legacyJson = legacyJson("idle", 4L);
		PixelTzzWorldState legacy = decode(legacyJson);
		check(legacy.loadKind() == LoadKind.LEGACY_V1, "valid schema v1 must be explicitly classified as legacy");
		check(!legacy.isSchemaCompatible(), "legacy state must remain read-only until migration");
		check(encode(legacy).equals(legacyJson), "unmigrated schema v1 must be preserved exactly");

		JsonElement futureJson = JsonParser.parseString(
			"""
			{"schema_version":5,"future":{"keep":true},"state_revision":9}
			"""
		);
		PixelTzzWorldState future = decode(futureJson);
		check(future.loadKind() == LoadKind.OPAQUE, "future schema must be opaque");
		check(encode(future).equals(futureJson), "future schema must be preserved exactly");

		JsonElement invalidV1 = legacyJson("idle", -1L);
		PixelTzzWorldState invalid = decode(invalidV1);
		check(invalid.loadKind() == LoadKind.OPAQUE, "invalid schema v1 must not masquerade as migratable");
		check(encode(invalid).equals(invalidV1), "invalid schema v1 must be preserved exactly");
	}

	private static void checkConservativeMigration() throws Exception {
		Path temporaryRoot = Files.createTempDirectory("pixel-tzz-state-self-check-").toAbsolutePath().normalize();
		try {
			Path stateFile = temporaryRoot.resolve("world_state.dat");
			Path recoveryDirectory = temporaryRoot.resolve("recovery");
			byte[] original = "schema-v1-compressed-placeholder".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			Files.write(stateFile, original);

			PixelTzzWorldState legacy = decode(legacyJson("idle", 4L));
			MigrationResult migrated = PixelTzzWorldStateMigration.migrate(
				legacy,
				healthyView(1, false),
				stateFile,
				recoveryDirectory,
				2_000L
			);
			check(migrated.status() == MigrationStatus.MIGRATED, "unambiguous v1 migration must succeed: " + migrated.reason());
			check(legacy.loadKind() == LoadKind.CURRENT_V5 && legacy.isDirty(), "migration must atomically publish dirty v5");
			WorldStateV3 migratedRoot = legacy.currentV3().orElseThrow();
			WorldStateV2 migratedState = legacy.currentV2().orElseThrow();
			check(migratedState.stateRevision() == 5L, "migration must preserve and increment revision");
			check(migratedState.activeGameId().orElseThrow().equals(GAME_ID), "migration must bind the unique game");
			check(migratedState.activePhaseId().orElseThrow().equals(PHASE_ID), "migration must bind initial phase");
			check(
				migratedRoot.activeGameInstanceId().isPresent(),
				"v1 migration must create an active game-instance scope"
			);
			check(migratedState.host().orElseThrow().playerId().equals(HOST_ID), "migration must preserve legacy host UUID");
			check(migratedState.players().isEmpty(), "migration must not invent offline player records");
			RecoverySnapshotMetadata metadata = migrated.recoverySnapshot().orElseThrow();
			Path backup = recoveryDirectory.resolve(metadata.fileName());
			check(Files.isRegularFile(backup), "migration must create a recovery file");
			check(java.util.Arrays.equals(Files.readAllBytes(backup), original), "recovery copy must match source bytes");
			check(metadata.sha256().equals(sha256(original)), "recovery metadata hash must match source bytes");
			check(
				migratedRoot.timeline().isEmpty()
					&& migratedRoot.exclusiveReservations().isEmpty()
					&& migratedRoot.recoverySnapshots().equals(List.of(metadata)),
				"v1 migration must initialize only empty v3 extensions and recovery history"
			);
			check(
				WorldStateV4.CODEC.parse(
					JsonOps.INSTANCE,
					WorldStateV4.CODEC.encodeStart(
						JsonOps.INSTANCE,
						legacy.currentV4().orElseThrow()
					).getOrThrow()
				).getOrThrow().equals(legacy.currentV4().orElseThrow()),
				"migrated v4 must round-trip"
			);

			byte[] v2Original = "schema-v2-compressed-placeholder".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			Files.write(stateFile, v2Original);
			WorldStateV2 v2Core = completeFixture();
			JsonElement v2Json = WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, v2Core).getOrThrow();
			PixelTzzWorldState v2Legacy = decode(v2Json);
			Path v2RecoveryDirectory = recoveryDirectory.resolve("v2");
			MigrationResult v2Migrated = PixelTzzWorldStateMigration.migrate(
				v2Legacy,
				new View(DefinitionSnapshot.empty(), ReloadStatus.INVALID, false, List.of(), 0),
				stateFile,
				v2RecoveryDirectory,
				2_010L
			);
			check(
				v2Migrated.status() == MigrationStatus.MIGRATED,
				"schema v2 migration must not depend on healthy definitions: " + v2Migrated.reason()
			);
			WorldStateV3 v2Root = v2Legacy.currentV3().orElseThrow();
			check(
				v2Root.core().equals(v2Core.withStateRevision(v2Core.stateRevision() + 1L)),
				"v2 migration must preserve the complete accepted core except revision"
			);
			check(
				v2Root.timeline().isEmpty() && v2Root.exclusiveReservations().isEmpty(),
				"v2 migration must not invent timeline or reservation state"
			);
			check(
				v2Root.activeGameInstanceId().isPresent(),
				"active schema-v2 migration must create an active game-instance scope"
			);
			check(
				v2Root.recoverySnapshots().size() == 2
					&& v2Root.recoverySnapshots().getFirst().equals(v2Core.recoverySnapshotMetadata().orElseThrow())
					&& v2Root.recoverySnapshots().getLast().equals(v2Migrated.recoverySnapshot().orElseThrow()),
				"v2 migration must preserve prior recovery metadata and append its own"
			);
			check(
				java.util.Arrays.equals(
					Files.readAllBytes(v2RecoveryDirectory.resolve(v2Migrated.recoverySnapshot().orElseThrow().fileName())),
					v2Original
				),
				"v2 recovery copy must match its source bytes"
			);
			check(
				v2Legacy.currentV4().orElseThrow().usages().isEmpty()
					&& v2Legacy.currentV4().orElseThrow().invocations().isEmpty(),
				"v2 migration must initialize empty v4 player-action ledgers"
			);

			byte[] v3Original = "schema-v3-compressed-placeholder".getBytes(
				java.nio.charset.StandardCharsets.UTF_8
			);
			Files.write(stateFile, v3Original);
			WorldStateV3 v3Core = timelineFixture(FrozenDocument.of("{\"task\":\"legacy\"}"));
			JsonElement v3Json = WorldStateV3.CODEC
				.encodeStart(JsonOps.INSTANCE, v3Core)
				.getOrThrow();
			PixelTzzWorldState v3Legacy = decode(v3Json);
			check(v3Legacy.loadKind() == LoadKind.LEGACY_V3, "schema v3 must await startup migration");
			Path v3RecoveryDirectory = recoveryDirectory.resolve("v3");
			MigrationResult v3Migrated = PixelTzzWorldStateMigration.migrate(
				v3Legacy,
				new View(DefinitionSnapshot.empty(), ReloadStatus.INVALID, false, List.of(), 0),
				stateFile,
				v3RecoveryDirectory,
				2_020L
			);
			check(
				v3Migrated.status() == MigrationStatus.MIGRATED,
				"schema v3 migration must not depend on current definitions: " + v3Migrated.reason()
			);
			check(v3Legacy.loadKind() == LoadKind.CURRENT_V5, "v3 migration must publish schema v5");
			WorldStateV4 v4FromV3 = v3Legacy.currentV4().orElseThrow();
			check(
				v4FromV3.core().core()
					.equals(v3Core.core().withStateRevision(v3Core.stateRevision() + 1L)),
				"v3 migration must preserve the accepted core except revision"
			);
			check(
				v4FromV3.core().timeline().equals(v3Core.timeline())
					&& v4FromV3.core().exclusiveReservations().equals(v3Core.exclusiveReservations()),
				"v3 migration must preserve timeline and exclusive-choice state"
			);
			check(
				v4FromV3.core().recoverySnapshots().getLast()
					.equals(v3Migrated.recoverySnapshot().orElseThrow()),
				"v3 migration must append its verified recovery metadata"
			);
			check(
				v4FromV3.usages().isEmpty() && v4FromV3.invocations().isEmpty(),
				"v3 migration must initialize empty action ledgers"
			);
			check(
				java.util.Arrays.equals(
					Files.readAllBytes(
						v3RecoveryDirectory.resolve(
							v3Migrated.recoverySnapshot().orElseThrow().fileName()
						)
					),
					v3Original
				),
				"v3 recovery copy must match its source bytes"
			);

			assertBlockedAndPreserved(
				decode(legacyJson("running", 1L)),
				healthyView(1, false),
				stateFile,
				recoveryDirectory.resolve("non-idle"),
				2_001L,
				"non-IDLE legacy state"
			);
			assertBlockedAndPreserved(
				decode(legacyJson("idle", 1L)),
				healthyView(2, false),
				stateFile,
				recoveryDirectory.resolve("multi-game"),
				2_002L,
				"multiple games"
			);
			assertBlockedAndPreserved(
				decode(legacyJson("idle", 1L)),
				healthyView(1, true),
				stateFile,
				recoveryDirectory.resolve("missing-definition"),
				2_003L,
				"missing default definition"
			);
			assertBlockedAndPreserved(
				decode(legacyJson("idle", Long.MAX_VALUE)),
				healthyView(1, false),
				stateFile,
				recoveryDirectory.resolve("revision-overflow"),
				2_004L,
				"revision overflow"
			);

			Path collisionDirectory = recoveryDirectory.resolve("collision");
			Files.createDirectories(collisionDirectory);
			Files.write(collisionDirectory.resolve("world_state-v1-to-v5-2005.dat"), new byte[] { 1, 2, 3 });
			PixelTzzWorldState collisionState = decode(legacyJson("idle", 2L));
			JsonElement beforeCollision = encode(collisionState);
			MigrationResult collision = PixelTzzWorldStateMigration.migrate(
				collisionState,
				healthyView(1, false),
				stateFile,
				collisionDirectory,
				2_005L
			);
			check(collision.status() == MigrationStatus.FAILED, "backup collision must fail closed");
			check(!collisionState.isDirty(), "failed backup must not dirty legacy state");
			check(encode(collisionState).equals(beforeCollision), "failed backup must preserve legacy payload");
		} finally {
			deleteVerifiedTemporaryDirectory(temporaryRoot);
		}
	}

	private static void assertBlockedAndPreserved(
		final PixelTzzWorldState state,
		final View definitions,
		final Path stateFile,
		final Path recoveryDirectory,
		final long now,
		final String label
	) {
		JsonElement before = encode(state);
		MigrationResult result = PixelTzzWorldStateMigration.migrate(
			state,
			definitions,
			stateFile,
			recoveryDirectory,
			now
		);
		check(result.status() == MigrationStatus.BLOCKED, label + " must block automatic migration");
		check(state.loadKind() == LoadKind.LEGACY_V1 && !state.isDirty(), label + " must remain read-only legacy");
		check(encode(state).equals(before), label + " must preserve exact schema-v1 payload");
		check(!Files.exists(recoveryDirectory), label + " must not create a recovery copy before validation");
	}

	private static View healthyView(final int gameCount, final boolean omitDefaultLifeState) {
		Map<Identifier, GameDefinition> games = new LinkedHashMap<>();
		Map<Identifier, RoleDefinition> roles = new LinkedHashMap<>();
		Map<Identifier, LifeStateDefinition> lifeStates = new LinkedHashMap<>();
		Map<Identifier, PhaseDefinition> phases = new LinkedHashMap<>();
		for (int index = 0; index < gameCount; index++) {
			String suffix = index == 0 ? "" : "_" + index;
			Identifier gameId = index == 0 ? GAME_ID : id("test:game" + suffix);
			Identifier phaseId = index == 0 ? PHASE_ID : id("test:setup" + suffix);
			Identifier roleId = index == 0 ? ROLE_ID : id("test:runner" + suffix);
			Identifier lifeStateId = index == 0 ? LIFE_STATE_ID : id("test:alive" + suffix);
			games.put(
				gameId,
				new GameDefinition(gameId, 1, new RichText("\"Game\"", "Game"), phaseId, roleId, lifeStateId)
			);
			roles.put(
				roleId,
				new RoleDefinition(
					roleId,
					gameId,
					new RichText("\"Runner\"", "Runner"),
					Optional.empty(),
					Set.of(),
					new TabStyle(Optional.empty(), Optional.empty())
				)
			);
			if (!(omitDefaultLifeState && index == 0)) {
				lifeStates.put(
					lifeStateId,
					new LifeStateDefinition(
						lifeStateId,
						gameId,
						new RichText("\"Alive\"", "Alive"),
						Optional.empty(),
						Set.of()
					)
				);
			}
			phases.put(
				phaseId,
				new PhaseDefinition(
					phaseId,
					gameId,
					new RichText("\"Setup\"", "Setup"),
					Set.of(),
					Optional.empty(),
					Optional.empty()
				)
			);
		}
		DefinitionSnapshot snapshot = new DefinitionSnapshot(
			1L,
			games,
			roles,
			Map.of(),
			lifeStates,
			phases,
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Set.of(),
			Set.of(),
			Map.of()
		);
		return new View(snapshot, ReloadStatus.READY, true, List.of(), 0);
	}

	private static JsonElement legacyJson(final String phase, final long revision) {
		return JsonParser.parseString(
			"{\"schema_version\":1,\"phase\":\""
				+ phase
				+ "\",\"host_id\":\""
				+ HOST_ID
				+ "\",\"state_revision\":"
				+ revision
				+ "}"
		);
	}

	private static PixelTzzWorldState decode(final JsonElement value) {
		return PixelTzzWorldState.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
	}

	private static JsonElement currentV5Json(final WorldStateV4 core) {
		return WorldStateV5.CODEC.encodeStart(
			JsonOps.INSTANCE,
			new WorldStateV5(WorldStateV5.SCHEMA_VERSION, core, Optional.empty())
		).getOrThrow();
	}

	private static JsonElement encode(final PixelTzzWorldState value) {
		return PixelTzzWorldState.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static String sha256(final byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException(error);
		}
	}

	private static void deleteVerifiedTemporaryDirectory(final Path directory) throws IOException {
		Path normalized = directory.toAbsolutePath().normalize();
		Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
		if (!normalized.startsWith(temporaryRoot) || normalized.equals(temporaryRoot)) {
			throw new IllegalStateException("refusing to clean an unverified temporary path: " + normalized);
		}
		try (Stream<Path> paths = Files.walk(normalized)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
