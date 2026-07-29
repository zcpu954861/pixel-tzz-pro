package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TimelineApprovalAuthority.ApprovalContext;
import io.github.zcpu954861.pixeltzzpro.server.TimelineApprovalAuthority.ApprovalResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackReferences;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ExecutionSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowIdentity;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Runnable check for the all-or-nothing host approval transaction.
 */
public final class TimelineApprovalAuthoritySelfCheck {
	private static final Identifier GAME = id("test:main");
	private static final Identifier APPROVAL_PHASE = id("test:approval");
	private static final Identifier START_PHASE = id("test:start");
	private static final Identifier HUNTER = id("test:hunter");
	private static final Identifier RUNNER = id("test:runner");
	private static final Identifier ALIVE = id("test:alive");
	private static final Identifier SPAWN = id("test:hunter_spawn");
	private static final Identifier CALL_SIGN = id("test:call_sign");
	private static final UUID GAME_INSTANCE = uuid(1);
	private static final UUID HOST = uuid(2);
	private static final UUID HUNTER_A = uuid(3);
	private static final UUID HUNTER_B = uuid(4);
	private static final UUID RUNNER_A = uuid(5);
	private static final UUID TIMELINE_INSTANCE = uuid(6);
	private static final UUID TASK_INSTANCE = uuid(7);
	private static final long STATE_REVISION = 12L;
	private static final long DEFINITION_GENERATION = 27L;

	private TimelineApprovalAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		WorldStateV3 ready = readyState();
		ApprovalResult approved = TimelineApprovalAuthority.approve(
			ready,
			definitions,
			context(HOST, STATE_REVISION, DEFINITION_GENERATION)
		);
		check(
			approved.successful(),
			"ready game must approve: " + approved.code() + " " + approved.message()
		);
		WorldStateV3 started = approved.nextState().orElseThrow();
		check(
			started.core().activePhaseId().equals(Optional.of(APPROVAL_PHASE)),
			"approval phase must remain active until its persisted callbacks succeed"
		);
		check(started.stateRevision() == STATE_REVISION + 1L, "approval must advance core revision once");
		check(
			started.activeGameInstanceId().equals(Optional.of(GAME_INSTANCE)),
			"approval must retain the game-instance reservation scope"
		);
		check(
			started.timeline().orElseThrow().status() == TimelineStatus.STARTING,
			"first task must be created in starting"
		);
		check(
			started.timeline()
				.orElseThrow()
				.currentTask()
				.orElseThrow()
				.phaseTransitionTrigger()
				.filter(trigger ->
					trigger.fromPhaseId().equals(APPROVAL_PHASE)
						&& trigger.toPhaseId().equals(START_PHASE)
				)
				.isPresent(),
			"approval must persist the start-phase transition before callbacks run"
		);
		List<UUID> expectedParticipants = List.of(HUNTER_A, HUNTER_B, RUNNER_A)
			.stream()
			.sorted()
			.toList();
		check(
			started.timeline()
				.orElseThrow()
				.currentTask()
				.orElseThrow()
				.participants()
				.equals(expectedParticipants),
			"first-task participants must be frozen from the frozen audience"
		);
		check(
			approved.frozenParticipants().equals(expectedParticipants),
			"approval result participant summary"
		);
		check(
			ready.timeline().isEmpty()
				&& ready.core().activePhaseId().equals(Optional.of(APPROVAL_PHASE))
				&& ready.stateRevision() == STATE_REVISION,
			"approval must not mutate its input"
		);
		check(
			TimelineSnapshotCompiler.restore(
				GAME,
				started.timeline().orElseThrow().snapshot()
			).success(),
			"approved timeline snapshot must restore independently"
		);

		checkCode(
			TimelineApprovalAuthority.approve(
				started,
				definitions,
				context(HOST, STATE_REVISION + 1L, DEFINITION_GENERATION)
			),
			OperationCode.TIMELINE_ALREADY_ACTIVE,
			"retained timeline cannot be overwritten"
		);
		checkCode(
			TimelineApprovalAuthority.approve(
				ready,
				definitions,
				context(RUNNER_A, STATE_REVISION, DEFINITION_GENERATION)
			),
			OperationCode.NOT_HOST,
			"non-host approval"
		);
		checkCode(
			TimelineApprovalAuthority.approve(
				ready.withCore(ready.core().withActivity(GAME, START_PHASE)),
				definitions,
				context(HOST, STATE_REVISION, DEFINITION_GENERATION)
			),
			OperationCode.PHASE_MISMATCH,
			"approval outside approval phase"
		);
		checkCode(
			TimelineApprovalAuthority.approve(
				ready,
				definitions,
				context(HOST, STATE_REVISION, DEFINITION_GENERATION + 1L)
			),
			OperationCode.DEFINITION_GENERATION_STALE,
			"stale definition confirmation"
		);

		WorldStateV3 missingLock = ready.withExclusiveReservations(
			ready.exclusiveReservations()
				.stream()
				.filter(value -> !value.ownerId().equals(HUNTER_B))
				.toList()
		);
		checkCode(
			TimelineApprovalAuthority.approve(
				missingLock,
				definitions,
				context(HOST, STATE_REVISION, DEFINITION_GENERATION)
			),
			OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE,
			"stored exclusive value without matching lock"
		);

		WorldStateV3 missingRegularField = withPlayerFields(
			ready,
			RUNNER_A,
			Map.of()
		);
		checkCode(
			TimelineApprovalAuthority.approve(
				missingRegularField,
				definitions,
				context(HOST, STATE_REVISION, DEFINITION_GENERATION)
			),
			OperationCode.ACTION_UNAVAILABLE,
			"ordinary required field"
		);

		WorldStateV3 retainedCompletedFlow = ready.withCore(
			ready.core()
				.withActiveForcedFlow(Optional.of(forcedFlow(FlowInstanceStatus.COMPLETED)))
		);
		check(
			TimelineApprovalAuthority.approve(
				retainedCompletedFlow,
				definitions,
				context(HOST, STATE_REVISION, DEFINITION_GENERATION)
			).successful(),
			"a retained terminal flow must not block approval"
		);
		WorldStateV3 activeFlow = ready.withCore(
			ready.core()
				.withActiveForcedFlow(Optional.of(forcedFlow(FlowInstanceStatus.ACTIVE)))
		);
		checkCode(
			TimelineApprovalAuthority.approve(
				activeFlow,
				definitions,
				context(HOST, STATE_REVISION, DEFINITION_GENERATION)
			),
			OperationCode.ACTIVE_FLOW_EXISTS,
			"unfinished forced flow"
		);
		System.out.println("TIMELINE_APPROVAL_AUTHORITY_SELF_CHECK=PASS");
	}

	private static WorldStateV3 readyState() {
		Map<Identifier, PersistentFieldValue> hunterAFields = fields("north_gate", "HA");
		Map<Identifier, PersistentFieldValue> hunterBFields = fields("station", "HB");
		Map<Identifier, PersistentFieldValue> runnerFields = Map.of(
			CALL_SIGN,
			new PersistentFieldValue(1, StoredValue.ofString("RA"))
		);
		Map<UUID, PlayerRecord> players = Map.of(
			HOST,
			player(HOST, "Host", RUNNER, Map.of()),
			HUNTER_A,
			player(HUNTER_A, "HunterA", HUNTER, hunterAFields),
			HUNTER_B,
			player(HUNTER_B, "HunterB", HUNTER, hunterBFields),
			RUNNER_A,
			player(RUNNER_A, "RunnerA", RUNNER, runnerFields)
		);
		WorldStateV2 core = WorldStateV2.initial()
			.withActivity(GAME, APPROVAL_PHASE)
			.withHost(
				Optional.of(
					new HostRecord(
						HOST,
						Optional.of("Host"),
						Optional.of(1L),
						Optional.empty(),
						Optional.empty()
					)
				)
			)
			.withPlayers(players)
			.withStateRevision(STATE_REVISION);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(GAME_INSTANCE),
			Optional.empty(),
			List.of(
				locked(uuid(31), HUNTER_A, "north_gate"),
				locked(uuid(32), HUNTER_B, "station")
			),
			List.of()
		);
	}

	private static Map<Identifier, PersistentFieldValue> fields(
		final String spawn,
		final String callSign
	) {
		return Map.of(
			SPAWN,
			new PersistentFieldValue(1, StoredValue.singleChoice(spawn)),
			CALL_SIGN,
			new PersistentFieldValue(1, StoredValue.ofString(callSign))
		);
	}

	private static PlayerRecord player(
		final UUID playerId,
		final String name,
		final Identifier role,
		final Map<Identifier, PersistentFieldValue> fields
	) {
		return new PlayerRecord(
			playerId,
			name,
			role,
			Optional.empty(),
			ALIVE,
			fields,
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			1L,
			1L
		);
	}

	private static ExclusiveReservation locked(
		final UUID reservationId,
		final UUID ownerId,
		final String value
	) {
		return new ExclusiveReservation(
			reservationId,
			GAME,
			SPAWN,
			GAME_INSTANCE,
			value,
			ownerId,
			ReservationStatus.LOCKED,
			Optional.empty(),
			1L,
			2L,
			0L
		);
	}

	private static WorldStateV3 withPlayerFields(
		final WorldStateV3 state,
		final UUID playerId,
		final Map<Identifier, PersistentFieldValue> fields
	) {
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.core().players());
		PlayerRecord player = players.get(playerId);
		players.put(
			playerId,
			new PlayerRecord(
				player.playerId(),
				player.lastKnownName(),
				player.roleId(),
				player.teamId(),
				player.lifeStateId(),
				fields,
				player.pendingRoleChange(),
				player.activeFlowParticipation(),
				player.completionSummary(),
				player.firstSeenAtEpochMillis(),
				player.lastSeenAtEpochMillis()
			)
		);
		return state.withCore(state.core().withPlayers(players));
	}

	private static ForcedFlowInstance forcedFlow(final FlowInstanceStatus status) {
		ExecutionSnapshot execution = new ExecutionSnapshot(
			DEFINITION_GENERATION,
			FrozenDocument.of("{}"),
			Map.of(),
			Map.of(),
			Map.of(),
			List.of(),
			Set.of(),
			Map.of(),
			Set.of(),
			new CallbackReferences(Optional.empty(), Optional.empty(), Optional.empty()),
			Set.of(),
			Set.of(),
			"",
			""
		).withComputedContentSha256();
		return new ForcedFlowInstance(
			new ForcedFlowIdentity(
				uuid(40),
				GAME,
				id("test:blocking_flow"),
				1,
				id("test:blocking_action"),
				HOST,
				1L,
				APPROVAL_PHASE,
				DEFINITION_GENERATION,
				CompletionPolicy.ALWAYS
			),
			new ForcedFlowRuntime(
				status,
				Map.of(),
				0,
				0,
				0L,
				execution,
				CallbackState.initial(),
				Optional.empty(),
				1L
			)
		);
	}

	private static ApprovalContext context(
		final UUID actor,
		final long revision,
		final long generation
	) {
		return new ApprovalContext(
			actor,
			GAME_INSTANCE,
			revision,
			generation,
			TIMELINE_INSTANCE,
			TASK_INSTANCE,
			100L,
			Set.of(HOST, HUNTER_A, RUNNER_A)
		);
	}

	private static DefinitionSnapshot definitions() {
		List<Source> sources = List.of(
			source(
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 2,
				  "content_version": 1,
				  "name": {"text": "Approval fixture"},
				  "initial_phase": "test:approval",
				  "default_role": "test:runner",
				  "default_life_state": "test:alive",
				  "task_timeline": {
				    "initial_task": "test:warmup",
				    "approval_phase": "test:approval",
				    "start_phase": "test:start",
				    "pre_start_requirements": [
				      {
				        "type": "required_field",
				        "audience": {
				          "role_tags": ["test:hunter"],
				          "exclude_host": true,
				          "online_only": false
				        },
				        "field": "test:hunter_spawn"
				      },
				      {
				        "type": "required_field",
				        "audience": {
				          "role_tags": ["test:active_participant"],
				          "exclude_host": true,
				          "online_only": false
				        },
				        "field": "test:call_sign"
				      }
				    ]
				  }
				}
				"""
			),
			source(
				DefinitionType.ROLE,
				"runner",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Runner"},
				  "tags": ["test:active_participant"]
				}
				"""
			),
			source(
				DefinitionType.ROLE,
				"hunter",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Hunter"},
				  "tags": ["test:hunter", "test:active_participant"]
				}
				"""
			),
			source(
				DefinitionType.LIFE_STATE,
				"alive",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Alive"}
				}
				"""
			),
			source(
				DefinitionType.PHASE,
				"approval",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Awaiting approval"},
				  "transitions": ["test:start"]
				}
				"""
			),
			source(
				DefinitionType.PHASE,
				"start",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Starting"},
				  "transitions": ["test:ended"]
				}
				"""
			),
			source(
				DefinitionType.PHASE,
				"ended",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Ended"},
				  "transitions": []
				}
				"""
			),
			source(
				DefinitionType.FIELD,
				"hunter_spawn",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Hunter spawn"},
				  "scope": "player",
				  "type": "exclusive_choice",
				  "required": true,
				  "editable_by": "player",
				  "invalidates_ready": true,
				  "roles": ["test:hunter"],
				  "phases": ["test:approval"],
				  "migration": "preserve",
				  "reservation_scope": "game_instance",
				  "release_when_role_mismatch": true,
				  "options": [
				    {"value": "north_gate", "name": {"text": "North gate"}},
				    {"value": "station", "name": {"text": "Station"}}
				  ]
				}
				"""
			),
			source(
				DefinitionType.FIELD,
				"call_sign",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Call sign"},
				  "scope": "player",
				  "type": "string",
				  "required": true,
				  "editable_by": "player",
				  "invalidates_ready": true,
				  "roles": [],
				  "phases": ["test:approval"],
				  "migration": "preserve",
				  "min_length": 2,
				  "max_length": 16
				}
				"""
			),
			source(
				DefinitionType.TASK,
				"warmup",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "kind": "warmup",
				  "name": {"text": "Warmup"},
				  "audience": {
				    "role_tags": ["test:active_participant"],
				    "exclude_host": true,
				    "online_only": false
				  },
				  "completion_policy": "event_only",
				  "counts_toward_game_time": false,
				  "results": [
				    {
				      "id": "done",
				      "name": {"text": "Done"},
				      "semantic": "success",
				      "route": {"end_phase": "test:ended"}
				    }
				  ]
				}
				"""
			)
		);
		Compilation compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(compilation.valid(), "approval fixture must compile: " + compilation.problems());
		return compilation.snapshot().orElseThrow().withGeneration(DEFINITION_GENERATION);
	}

	private static Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		return new Source(
			type,
			Identifier.fromNamespaceAndPath("test", path),
			Identifier.fromNamespaceAndPath(
				"test",
				"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
			),
			"timeline-approval-self-check",
			json
		);
	}

	private static void checkCode(
		final ApprovalResult result,
		final OperationCode expected,
		final String label
	) {
		check(
			result.code() == expected && result.nextState().isEmpty(),
			label + ": expected " + expected + " but got " + result.code() + " " + result.message()
		);
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static UUID uuid(final long value) {
		return new UUID(0L, value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
