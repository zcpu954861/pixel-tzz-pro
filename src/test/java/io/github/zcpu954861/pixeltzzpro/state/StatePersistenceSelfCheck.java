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
import net.minecraft.resources.Identifier;

/**
 * Runnable schema-v2, migration, and recovery contract check without a test framework.
 */
public final class StatePersistenceSelfCheck {
	private static final UUID HOST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID REMOVED_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
	private static final UUID PAGE_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
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
		checkSnapshotSizeIncludesMetadata();
		checkTerminalSnapshotCompaction();
		checkLegacyAndOpaqueClassification();
		checkConservativeMigration();
		System.out.println("STATE_PERSISTENCE_SELF_CHECK=PASS");
	}

	private static void checkInitialAndCommit() {
		PixelTzzWorldState state = PixelTzzWorldState.initial();
		check(state.loadKind() == LoadKind.CURRENT_V2, "new worlds must use schema v2");
		check(state.isSchemaCompatible(), "new schema-v2 state must be writable");
		check(state.stateRevision() == 0L, "new state revision must be zero");
		check(state.activeGameId().isEmpty() && state.activePhaseId().isEmpty(), "new worlds must not invent an activity");

		CommitResult committed = state.commit(0L, value -> value.withActivity(GAME_ID, PHASE_ID));
		check(committed.committed(), "valid expected-revision commit must succeed: " + committed.reason());
		check(state.stateRevision() == 1L, "commit must increment state revision exactly once");
		check(state.isDirty(), "commit must mark SavedData dirty");
		check(state.activeGameId().orElseThrow().equals(GAME_ID), "commit must publish the complete candidate");

		CommitResult stale = state.commit(0L, value -> value.withHost(Optional.of(HostRecord.migratedLegacy(HOST_ID))));
		check(!stale.committed(), "stale expected revision must be rejected");
		check(state.stateRevision() == 1L && state.hostId().isEmpty(), "rejected commit must not partially mutate state");
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
		check(wrapper.currentV2().orElseThrow().equals(value), "SavedData wrapper must retain the complete v2 payload");
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
			{"schema_version":3,"future":{"keep":true},"state_revision":9}
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
			check(legacy.loadKind() == LoadKind.CURRENT_V2 && legacy.isDirty(), "migration must atomically publish dirty v2");
			WorldStateV2 migratedState = legacy.currentV2().orElseThrow();
			check(migratedState.stateRevision() == 5L, "migration must preserve and increment revision");
			check(migratedState.activeGameId().orElseThrow().equals(GAME_ID), "migration must bind the unique game");
			check(migratedState.activePhaseId().orElseThrow().equals(PHASE_ID), "migration must bind initial phase");
			check(migratedState.host().orElseThrow().playerId().equals(HOST_ID), "migration must preserve legacy host UUID");
			check(migratedState.players().isEmpty(), "migration must not invent offline player records");
			RecoverySnapshotMetadata metadata = migrated.recoverySnapshot().orElseThrow();
			Path backup = recoveryDirectory.resolve(metadata.fileName());
			check(Files.isRegularFile(backup), "migration must create a recovery file");
			check(java.util.Arrays.equals(Files.readAllBytes(backup), original), "recovery copy must match source bytes");
			check(metadata.sha256().equals(sha256(original)), "recovery metadata hash must match source bytes");
			check(WorldStateV2.CODEC.parse(JsonOps.INSTANCE, WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, migratedState).getOrThrow()).getOrThrow().equals(migratedState), "migrated v2 must round-trip");

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
			Files.write(collisionDirectory.resolve("world_state-v1-to-v2-2005.dat"), new byte[] { 1, 2, 3 });
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
