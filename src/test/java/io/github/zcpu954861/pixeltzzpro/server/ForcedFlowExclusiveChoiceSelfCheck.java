package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ConnectedPlayer;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ExclusiveChoiceMutation;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ExclusiveChoiceMutationOperation;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ExclusiveContext;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.FieldInput;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.Submission;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.Actor;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * End-to-end check for the schema-v3 forced-flow/exclusive-choice transaction bridge.
 */
final class ForcedFlowExclusiveChoiceSelfCheck {
	private static final Identifier GAME = id("bridge:game");
	private static final Identifier PHASE = id("bridge:setup");
	private static final Identifier ROLE = id("bridge:runner");
	private static final Identifier HUNTER = id("bridge:hunter");
	private static final Identifier SPECTATOR = id("bridge:spectator");
	private static final Identifier FLOW = id("bridge:spawn_init");
	private static final Identifier FIELD = id("bridge:spawn");
	private static final Identifier ACTION = id("bridge:start");
	private static final UUID SCOPE = uuid(1);
	private static final UUID HOST = uuid(2);
	private static final UUID PLAYER_A = uuid(3);
	private static final UUID PLAYER_B = uuid(4);
	private static final UUID PLAYER_C = uuid(5);

	private ForcedFlowExclusiveChoiceSelfCheck() {
	}

	static void run() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		WorldStateV3 initial = WorldStateV3.initial()
			.beginGameInstance(GAME, PHASE, SCOPE);
		initial = initial.withCore(
			HostAuthority.claim(
				initial.core(),
				new Actor(HOST, "Host", true),
				1L
			).nextState().orElseThrow()
		);
		List<ConnectedPlayer> connected = List.of(
			connected(HOST, "Host"),
			connected(PLAYER_A, "Alpha"),
			connected(PLAYER_B, "Bravo"),
			connected(PLAYER_C, "Charlie")
		);

		var legacy = ForcedFlowAuthority.start(
			initial.core(),
			definitions,
			definitions.panelActions().get(ACTION),
			HOST,
			connected,
			List.of(PLAYER_A),
			uuid(10),
			UUID::randomUUID,
			10L
		);
		check(
			legacy.code() == OperationCode.UNSUPPORTED_OPERATION,
			"schema-v2 entry must reject an exclusive flow"
		);

		var insufficient = ForcedFlowAuthority.start(
			initial,
			definitions,
			definitions.panelActions().get(ACTION),
			HOST,
			connected,
			List.of(PLAYER_A, PLAYER_B, PLAYER_C),
			uuid(11),
			UUID::randomUUID,
			11L,
			context(11L)
		);
		check(
			insufficient.code() == OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE
				&& insufficient.nextState().isEmpty()
				&& insufficient.message().equals(
					"当前无法发起『Spawn initialization』：『Spawn』当前剩余 2 个可用位置，无法为 3 名玩家分配。"
				),
			"multi-target start must reject insufficient exclusive capacity"
		);

		UUID firstFlow = uuid(12);
		var started = ForcedFlowAuthority.start(
			initial,
			definitions,
			definitions.panelActions().get(ACTION),
			HOST,
			connected,
			List.of(PLAYER_A),
			firstFlow,
			UUID::randomUUID,
			12L,
			context(12L)
		);
		check(started.successful(), "schema-v3 exclusive flow must start: " + started.message());
		WorldStateV3 choosing = started.nextState().orElseThrow();
		check(
			choosing.core()
				.players()
				.get(PLAYER_A)
				.pendingRoleChange()
				.filter(change -> change.status() == PendingRoleChangeStatus.PENDING)
				.filter(change -> change.previousRoleId().equals(ROLE))
				.filter(change -> change.targetRoleId().equals(HUNTER))
				.isPresent(),
			"role initialization flow must defer the hunter role change"
		);
		var choosingFlow = choosing.core().activeForcedFlow().orElseThrow();
		var choosingMember = choosingFlow.runtime().members().get(PLAYER_A);
		var choosingPlayer = choosing.core().players().get(PLAYER_A);
		var choosingField = definitions.fields().get(FIELD);
		var choosingContext = new ForcedFlowAuthority.PredicateContext(
			PLAYER_A,
			choosingFlow.identity().instanceId(),
			choosingFlow.identity().gameId(),
			choosingFlow.identity().flowId(),
			choosingFlow.identity().flowVersion(),
			choosingFlow.identity().phaseIdAtCreation(),
			choosingMember.flowFields(),
			choosingPlayer.persistentFields()
		);
		check(
			choosingPlayer.roleId().equals(ROLE),
			"the persisted player role must remain runner while the flow is active"
		);
		check(
			!ForcedFlowAuthority.playerMayEditField(
				choosingField,
				choosingPlayer,
				PHASE,
				(predicate, context) -> true,
				choosingContext
			),
			"the old persisted role alone must not authorize the hunter field"
		);
		check(
			ForcedFlowAuthority.playerMayEditFieldInFlow(
				choosingField,
				choosingPlayer,
				choosingFlow,
				(predicate, context) -> true,
				choosingContext
			),
			"the frozen hunter initialization flow must expose its current hunter field"
		);

		long initialMemberRevision = choosingMember.memberRevision();
		long initialInstanceRevision = choosingFlow.runtime().instanceRevision();
		var immediateHeld = ForcedFlowAuthority.mutateExclusiveChoice(
			choosing,
			definitions,
			mutation(
				choosing,
				PLAYER_A,
				0L,
				ExclusiveChoiceMutationOperation.HOLD,
				"north"
			),
			19L,
			context(19L)
		);
		check(
			immediateHeld.successful(),
			"card click must create an authoritative hold: " + immediateHeld.message()
		);
		WorldStateV3 heldOnChoice = immediateHeld.nextState().orElseThrow();
		var heldMember = heldOnChoice.core()
			.activeForcedFlow()
			.orElseThrow()
			.runtime()
			.members()
			.get(PLAYER_A);
		check(
			heldMember.currentNodeId().filter("choose"::equals).isPresent()
				&& heldMember.memberRevision() == initialMemberRevision + 1L
				&& heldOnChoice.core()
					.activeForcedFlow()
					.orElseThrow()
					.runtime()
					.instanceRevision() == initialInstanceRevision + 1L
				&& heldMember.requestSequenceWindow().highestAcceptedSequence() == 0L,
			"hold must persist request, member, and instance revisions without advancing"
		);
		check(
			heldOnChoice.exclusiveReservations().stream()
				.anyMatch(value ->
					value.ownerId().equals(PLAYER_A)
						&& value.value().equals("north")
						&& value.status() == ReservationStatus.HELD
				),
			"immediate hold must own the selected option"
		);
		var replayed = ForcedFlowAuthority.mutateExclusiveChoice(
			heldOnChoice,
			definitions,
			mutation(
				heldOnChoice,
				PLAYER_A,
				0L,
				ExclusiveChoiceMutationOperation.HOLD,
				"north"
			),
			19L,
			context(19L)
		);
		check(
			replayed.code() == OperationCode.REQUEST_REPLAYED,
			"accepted exclusive mutation sequence must reject replay"
		);
		var released = ForcedFlowAuthority.mutateExclusiveChoice(
			heldOnChoice,
			definitions,
			mutation(
				heldOnChoice,
				PLAYER_A,
				1L,
				ExclusiveChoiceMutationOperation.RELEASE,
				null
			),
			20L,
			context(20L)
		);
		check(
			released.successful()
				&& released.nextState().orElseThrow().exclusiveReservations().isEmpty()
				&& released.nextState()
					.orElseThrow()
					.core()
					.activeForcedFlow()
					.orElseThrow()
					.runtime()
					.members()
					.get(PLAYER_A)
					.currentNodeId()
					.filter("choose"::equals)
					.isPresent(),
			"second click must release only the held value without advancing"
		);
		WorldStateV3 releasedState = released.nextState().orElseThrow();
		var heldSouth = ForcedFlowAuthority.mutateExclusiveChoice(
			releasedState,
			definitions,
			mutation(
				releasedState,
				PLAYER_A,
				2L,
				ExclusiveChoiceMutationOperation.HOLD,
				"south"
			),
			21L,
			context(21L)
		);
		check(heldSouth.successful(), "first atomic hold must succeed");
		var changedToNorth = ForcedFlowAuthority.mutateExclusiveChoice(
			heldSouth.nextState().orElseThrow(),
			definitions,
			mutation(
				heldSouth.nextState().orElseThrow(),
				PLAYER_A,
				3L,
				ExclusiveChoiceMutationOperation.HOLD,
				"north"
			),
			22L,
			context(22L)
		);
		check(
			changedToNorth.successful()
				&& changedToNorth.nextState().orElseThrow().exclusiveReservations().size() == 1
				&& changedToNorth.nextState()
					.orElseThrow()
					.exclusiveReservations()
					.getFirst()
					.value()
					.equals("north"),
			"changing choices must atomically replace the existing hold"
		);
		var held = ForcedFlowAuthority.advance(
			changedToNorth.nextState().orElseThrow(),
			definitions,
			submission(
				changedToNorth.nextState().orElseThrow(),
				PLAYER_A,
				4L,
				"submit",
				"north"
			),
			uuid(20),
			23L,
			context(23L)
		);
		check(held.successful(), "exclusive selection must advance: " + held.message());
		WorldStateV3 confirming = held.nextState().orElseThrow();
		check(
			confirming.exclusiveReservations().stream()
				.anyMatch(value ->
					value.ownerId().equals(PLAYER_A)
						&& value.value().equals("north")
						&& value.status() == ReservationStatus.HELD
				),
			"choice submission must create a temporary hold"
		);
		var returned = ForcedFlowAuthority.advance(
			confirming,
			definitions,
			submission(confirming, PLAYER_A, 5L, "back", null),
			uuid(22),
			24L,
			context(24L)
		);
		check(
			returned.successful()
				&& returned.nextState()
					.orElseThrow()
					.core()
					.activeForcedFlow()
					.orElseThrow()
					.runtime()
					.members()
					.get(PLAYER_A)
					.currentNodeId()
					.filter("choose"::equals)
					.isPresent()
				&& returned.nextState().orElseThrow().exclusiveReservations().stream()
					.anyMatch(value ->
						value.ownerId().equals(PLAYER_A)
							&& value.value().equals("north")
							&& value.status() == ReservationStatus.HELD
					),
			"confirm back must return to choice while retaining the temporary hold"
		);
		var reconfirming = ForcedFlowAuthority.advance(
			returned.nextState().orElseThrow(),
			definitions,
			submission(
				returned.nextState().orElseThrow(),
				PLAYER_A,
				6L,
				"submit",
				"north"
			),
			uuid(23),
			25L,
			context(25L)
		);
		check(reconfirming.successful(), "returned choice must advance again");

		var completed = ForcedFlowAuthority.advance(
			reconfirming.nextState().orElseThrow(),
			definitions,
			submission(
				reconfirming.nextState().orElseThrow(),
				PLAYER_A,
				7L,
				"confirm",
				null
			),
			uuid(21),
			26L,
			context(26L)
		);
		check(
			completed.successful() && completed.memberCompleted() && completed.flowCompleted(),
			"complete node must finish the member and flow: " + completed.message()
		);
		WorldStateV3 locked = completed.nextState().orElseThrow();
		check(
			locked.exclusiveReservations().stream()
				.anyMatch(value ->
					value.ownerId().equals(PLAYER_A)
						&& value.value().equals("north")
						&& value.status() == ReservationStatus.LOCKED
				),
			"member completion must lock the held value"
		);
		check(
			locked.core()
				.players()
				.get(PLAYER_A)
				.persistentFields()
				.get(FIELD)
				.value()
				.stringValue()
				.orElseThrow()
				.equals("north"),
			"member completion must publish the locked value to the player field"
		);
		check(
			locked.core()
				.players()
				.get(PLAYER_A)
				.pendingRoleChange()
				.filter(change -> change.status() == PendingRoleChangeStatus.APPLIED)
				.isPresent(),
			"same-role initialization completion must publish an applied credential"
		);

		UUID redoFlow = uuid(30);
		var redoStarted = ForcedFlowAuthority.start(
			locked,
			definitions,
			definitions.panelActions().get(ACTION),
			HOST,
			connected,
			List.of(PLAYER_A),
			redoFlow,
			UUID::randomUUID,
			30L,
			context(30L)
		);
		check(redoStarted.successful(), "exclusive reinitialization must start");
		var replacement = ForcedFlowAuthority.advance(
			redoStarted.nextState().orElseThrow(),
			definitions,
			submission(redoStarted.nextState().orElseThrow(), PLAYER_A, 0L, "submit", "south"),
			uuid(31),
			31L,
			context(31L)
		);
		check(
			replacement.successful()
				&& replacement.nextState().orElseThrow().exclusiveReservations().size() == 2,
			"reinitialization must retain the old lock while holding its replacement"
		);
		WorldStateV3 replacementState = replacement.nextState().orElseThrow();
		var canceled = FlowRosterAuthority.cancelFlow(
			replacementState,
			HOST,
			redoFlow,
			replacementState.core()
				.activeForcedFlow()
				.orElseThrow()
				.runtime()
				.instanceRevision(),
			"self-check cancellation",
			32L,
			32L
		);
		check(canceled.successful(), "schema-v3 cancellation must succeed: " + canceled.message());
		WorldStateV3 canceledState = canceled.nextState().orElseThrow();
		check(
			canceledState.exclusiveReservations().size() == 1
				&& canceledState.exclusiveReservations().getFirst().status() == ReservationStatus.LOCKED
				&& canceledState.exclusiveReservations().getFirst().value().equals("north")
				&& canceledState.core()
					.players()
					.get(PLAYER_A)
					.persistentFields()
					.get(FIELD)
					.value()
					.stringValue()
					.orElseThrow()
					.equals("north"),
			"canceling reinitialization must release only the replacement hold"
		);
		var changedRole = FlowRosterAuthority.assignRoleImmediate(
			canceledState,
			definitions,
			HOST,
			canceledState.core().stateRevision(),
			SPECTATOR,
			List.of(PLAYER_A),
			33L,
			SCOPE,
			33L
		);
		check(changedRole.successful(), "schema-v3 role transaction must succeed");
		check(
			changedRole.nextState().orElseThrow().exclusiveReservations().isEmpty()
				&& !changedRole.nextState()
					.orElseThrow()
					.core()
					.players()
					.get(PLAYER_A)
					.persistentFields()
					.containsKey(FIELD),
			"role mismatch must release both the lock and its published player field"
		);
	}

	private static Submission submission(
		final WorldStateV3 state,
		final UUID playerId,
		final long sequence,
		final String action,
		final String value
	) {
		var instance = state.core().activeForcedFlow().orElseThrow();
		var member = instance.runtime().members().get(playerId);
		List<FieldInput> fields = value == null
			? List.of()
			: List.of(new FieldInput(FIELD, "exclusive_choice", "\"" + value + "\""));
		return new Submission(
			playerId,
			instance.identity().instanceId(),
			member.pageInstanceId().orElseThrow(),
			instance.identity().flowId(),
			instance.identity().flowVersion(),
			member.currentNodeId().orElseThrow(),
			instance.runtime().instanceRevision(),
			member.memberRevision(),
			sequence,
			action,
			fields
		);
	}

	private static ExclusiveChoiceMutation mutation(
		final WorldStateV3 state,
		final UUID playerId,
		final long sequence,
		final ExclusiveChoiceMutationOperation operation,
		final String value
	) {
		var instance = state.core().activeForcedFlow().orElseThrow();
		var member = instance.runtime().members().get(playerId);
		return new ExclusiveChoiceMutation(
			playerId,
			instance.identity().instanceId(),
			member.pageInstanceId().orElseThrow(),
			instance.identity().flowId(),
			instance.identity().flowVersion(),
			member.currentNodeId().orElseThrow(),
			instance.runtime().instanceRevision(),
			member.memberRevision(),
			sequence,
			FIELD,
			operation,
			value == null ? java.util.Optional.empty() : java.util.Optional.of(value)
		);
	}

	private static ExclusiveContext context(final long serverTick) {
		return new ExclusiveContext(SCOPE, serverTick, UUID::randomUUID);
	}

	private static ConnectedPlayer connected(final UUID playerId, final String name) {
		return new ConnectedPlayer(playerId, name, true, true);
	}

	private static DefinitionSnapshot definitions() {
		List<Source> sources = List.of(
			source(DefinitionType.GAME, GAME, """
				{"format_version":1,"api_version":2,"content_version":1,
				"name":{"text":"Bridge"},"initial_phase":"bridge:setup",
				"default_role":"bridge:runner","default_life_state":"bridge:alive",
				"task_timeline":{"initial_task":"bridge:dummy","pause_when_host_offline":false,
				"approval_phase":"bridge:setup","start_phase":"bridge:running",
				"pre_start_requirements":[]}}
				"""),
			source(DefinitionType.ROLE, ROLE, """
				{"format_version":1,"game":"bridge:game","name":{"text":"Runner"},
				"initialization_flow":"bridge:spawn_init","tags":["bridge:participant"],"tab":{}}
				"""),
			source(DefinitionType.ROLE, HUNTER, """
				{"format_version":1,"game":"bridge:game","name":{"text":"Hunter"},
				"initialization_flow":"bridge:spawn_init","tags":["bridge:participant"],"tab":{}}
				"""),
			source(DefinitionType.ROLE, SPECTATOR, """
				{"format_version":1,"game":"bridge:game","name":{"text":"Spectator"},
				"tags":[],"tab":{}}
				"""),
			source(DefinitionType.LIFE_STATE, id("bridge:alive"), """
				{"format_version":1,"game":"bridge:game","name":{"text":"Alive"},"tags":[]}
				"""),
			source(DefinitionType.PHASE, PHASE, """
				{"format_version":1,"game":"bridge:game","name":{"text":"Setup"},
				"transitions":["bridge:running"]}
				"""),
			source(DefinitionType.PHASE, id("bridge:running"), """
				{"format_version":1,"game":"bridge:game","name":{"text":"Running"},
				"transitions":["bridge:ended"]}
				"""),
			source(DefinitionType.PHASE, id("bridge:ended"), """
				{"format_version":1,"game":"bridge:game","name":{"text":"Ended"},"transitions":[]}
				"""),
			source(DefinitionType.FIELD, FIELD, """
				{"format_version":1,"game":"bridge:game","version":1,
				"name":{"text":"Spawn"},"scope":"player","type":"exclusive_choice",
				"required":true,"editable_by":"player","roles":["bridge:hunter"],
				"phases":["bridge:setup"],"reservation_scope":"game_instance",
				"release_when_role_mismatch":true,
				"options":[{"value":"north","name":{"text":"North"}},
				{"value":"south","name":{"text":"South"}}]}
				"""),
			source(DefinitionType.PAGE, id("bridge:choose"), """
				{"format_version":1,"game":"bridge:game","title":{"text":"Choose"},
				"theme":"pixel-tzz-pro:default","root":{"type":"column","children":[
				{"type":"field_input","id":"spawn","field":"bridge:spawn",
				"presentation":"choice_cards"},{"type":"button","id":"submit",
				"label":{"text":"Submit"},"action":{"type":"flow","name":"submit"}}]}}
				"""),
			source(DefinitionType.PAGE, id("bridge:confirm"), """
				{"format_version":1,"game":"bridge:game","title":{"text":"Confirm"},
				"theme":"pixel-tzz-pro:default","root":{"type":"row","children":[
				{"type":"button","id":"back","label":{"text":"Back"},
				"action":{"type":"flow","name":"back"}},
				{"type":"button","id":"confirm","label":{"text":"Confirm"},
				"action":{"type":"flow","name":"confirm"}}]}}
				"""),
			source(DefinitionType.FLOW, FLOW, """
				{"format_version":1,"game":"bridge:game","version":1,
				"name":{"text":"Spawn initialization"},"entry":"choose",
				"audience":{"roles":["bridge:runner","bridge:hunter"],"exclude_host":true,"online_only":true},
				"required":true,"host_bossbar":{"name":{"text":"{event}: {completed}/{total}"},
				"color":"purple","style":"progress","priority":100},"nodes":[
				{"id":"choose","type":"choice","page":"bridge:choose","field":"bridge:spawn",
				"choices":[{"value":"north","next":"confirm"},{"value":"south","next":"confirm"}]},
				{"id":"confirm","type":"confirm","page":"bridge:confirm","next":"done","back":"choose"},
				{"id":"done","type":"complete"}]}
				"""),
			source(DefinitionType.PANEL_ACTION, ACTION, """
				{"format_version":1,"game":"bridge:game","surface":"host","section":"primary",
				"order":1,"label":{"text":"Start"},"description":{"text":"Start"},
				"phases":["bridge:setup"],"target":{"mode":"multiple","min":1,"max":3,
				"filter":{"roles":["bridge:runner","bridge:hunter"]}},"confirmation":{"title":{"text":"Confirm"},
				"consequences":[{"text":"Starts"}]},"operation":{"type":"assign_role",
				"role":"bridge:hunter","flow":"bridge:spawn_init","apply":"after_flow",
				"completion_policy":"always"}}
				"""),
			source(DefinitionType.TASK, id("bridge:dummy"), """
				{"format_version":1,"game":"bridge:game","version":1,"kind":"main",
				"name":{"text":"Dummy"},"completion_policy":"event_only",
				"counts_toward_game_time":true,"results":[{"id":"done",
				"name":{"text":"Done"},"semantic":"neutral",
				"route":{"end_phase":"bridge:ended"}}]}
				""")
		);
		var compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(
			compilation.valid(),
			"exclusive bridge fixture compile: "
				+ compilation.problems().stream().map(DefinitionCompiler.Problem::summary).toList()
		);
		return compilation.snapshot().orElseThrow().withGeneration(3L);
	}

	private static Source source(
		final DefinitionType type,
		final Identifier id,
		final String json
	) {
		return new Source(
			type,
			id,
			Identifier.fromNamespaceAndPath(
				id.getNamespace(),
				"pixel_tzz_pro/" + type.directory() + "/" + id.getPath() + ".json"
			),
			"self-check",
			json
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
