package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetFilter;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetSelection;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.FlowBlockAuthority;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ActionResult;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ConnectedPlayer;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.FieldInput;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PanelActionAuthorization;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.StartResult;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.Submission;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.Actor;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Runnable end-to-end contract check for the first persisted forced-flow execution slice.
 */
public final class ForcedFlowAuthoritySelfCheck {
	private static final UUID HOST_ID = uuid(1);
	private static final UUID PLAYER_ID = uuid(2);
	private static final UUID SECOND_PLAYER_ID = uuid(3);
	private static final UUID INSTANCE_A = uuid(101);
	private static final UUID INSTANCE_B = uuid(102);
	private static final UUID PAGE_A = uuid(201);
	private static final UUID PAGE_B = uuid(202);
	private static final UUID PAGE_C = uuid(203);
	private static final UUID PAGE_D = uuid(204);
	private static final Identifier GAME_ID = id("test:game");
	private static final Identifier PHASE_ID = id("test:setup");
	private static final Identifier FLOW_ID = id("test:short");
	private static final Identifier FIELD_FLOW_ID = id("test:fields");
	private static final Identifier FILTER_FLOW_ID = id("test:filter_marker");
	private static final Identifier COMPLETED_FILTER_ACTION_ID = id("test:requires_current_complete");
	private static final Identifier INCOMPLETE_FILTER_ACTION_ID = id("test:requires_current_incomplete");
	private static final Identifier GATED_ACTION_ID = id("test:start_gated");
	private static final Identifier BRANCH_PREDICATE = id("test:branch");
	private static final Identifier FIELD_VISIBILITY_PREDICATE = id("test:field_visible");
	private static final Identifier AGE_FIELD = id("test:age");
	private static final Identifier NICKNAME_FIELD = id("test:nickname");
	private static final Identifier BADGE_FIELD = id("test:badge");
	private static final Identifier ACCEPT_FIELD = id("test:accepted");
	private static final Identifier PATH_FIELD = id("test:path");
	private static final Identifier TAGS_FIELD = id("test:tags");
	private static final Identifier RUNNER_ONLY_FIELD = id("test:runner_only");
	private static final Identifier OTHER_PHASE_FIELD = id("test:other_phase_only");
	private static final Identifier HOST_ONLY_FIELD = id("test:host_only");
	private static final Identifier CONDITIONAL_FIELD = id("test:conditional");
	private static final Identifier RUNNER_ID = id("test:runner");
	private static final Identifier HUNTER_ID = id("test:hunter");

	private ForcedFlowAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		WorldStateV2 initial = HostAuthority.claim(
			WorldStateV2.initial().withActivity(GAME_ID, PHASE_ID),
			new Actor(HOST_ID, "Host", true),
			100L
		).nextState().orElseThrow();
		List<ConnectedPlayer> connected = List.of(
			new ConnectedPlayer(HOST_ID, "Host", true, true),
			new ConnectedPlayer(PLAYER_ID, "Runner", true, true)
		);
		checkPredicateGatedStart(initial, definitions, connected);
		checkAutomaticTargetFilter(initial, definitions, connected);
		checkImmediateRoleCandidates(initial, definitions, connected);

		StartResult started = ForcedFlowAuthority.start(
			initial,
			definitions,
			definitions.panelActions().get(id("test:start")),
			HOST_ID,
			connected,
			List.of(),
			INSTANCE_A,
			ids(PAGE_A, PAGE_B),
			200L
		);
		check(started.successful(), "required flow must start: " + started.message());
		WorldStateV2 active = started.nextState().orElseThrow();
		var activeFlow = active.activeForcedFlow().orElseThrow();
		var firstMember = activeFlow.runtime().members().get(PLAYER_ID);
		check(active.stateRevision() == initial.stateRevision() + 1L, "start revision");
		check(activeFlow.runtime().status() == FlowInstanceStatus.ACTIVE, "active status");
		check(activeFlow.runtime().totalCount() == 1, "host must be excluded");
		check(firstMember.status() == FlowMemberStatus.IN_PROGRESS, "member must be in progress");
		check(active.players().get(PLAYER_ID).roleId().equals(RUNNER_ID), "default role");
		check(active.players().get(PLAYER_ID).lifeStateId().equals(id("test:alive")), "default life state");
		checkFatalBlockTransitions(active);

		assertFailurePreserves(
			active,
			ForcedFlowAuthority.start(
				active,
				definitions,
				definitions.panelActions().get(id("test:start")),
				HOST_ID,
				connected,
				List.of(),
				uuid(999),
				UUID::randomUUID,
				201L
			),
			OperationCode.ACTIVE_FLOW_EXISTS,
			"second active flow"
		);

		ActionResult firstPage = ForcedFlowAuthority.advance(
			active,
			definitions,
			submission(active, firstMember, "continue", 0L),
			PAGE_B,
			300L
		);
		check(firstPage.successful(), "page action must advance: " + firstPage.message());
		WorldStateV2 confirm = firstPage.nextState().orElseThrow();
		var confirmMember = confirm.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID);
		check(confirmMember.currentNodeId().equals(Optional.of("confirm")), "next node");
		check(confirmMember.pageInstanceId().equals(Optional.of(PAGE_B)), "rotated page ID");

		ActionResult replay = ForcedFlowAuthority.advance(
			confirm,
			definitions,
			submission(confirm, confirmMember, "confirm", 0L),
			PAGE_C,
			301L
		);
		assertFailurePreserves(confirm, replay, OperationCode.REQUEST_REPLAYED, "request replay");

		ActionResult completed = ForcedFlowAuthority.advance(
			confirm,
			definitions,
			submission(confirm, confirmMember, "confirm", 1L),
			PAGE_C,
			400L
		);
		check(completed.successful() && completed.memberCompleted(), "member completion");
		check(completed.flowCompleted(), "sole member must complete flow");
		WorldStateV2 finished = completed.nextState().orElseThrow();
		check(
			finished.activeForcedFlow().orElseThrow().runtime().status() == FlowInstanceStatus.COMPLETED,
			"completed status"
		);
		check(finished.completionHistory().size() == 1, "completion history");
		check(finished.players().get(PLAYER_ID).activeFlowParticipation().isEmpty(), "participation clear");
		check(
			finished.activeForcedFlow().orElseThrow().runtime().executionSnapshot().pages().isEmpty(),
			"terminal flow must compact frozen UI documents"
		);

		StartResult alreadyComplete = ForcedFlowAuthority.start(
			finished,
			definitions,
			definitions.panelActions().get(id("test:start")),
			HOST_ID,
			connected,
			List.of(),
			uuid(103),
			UUID::randomUUID,
			500L
		);
		check(
			!alreadyComplete.successful()
				&& alreadyComplete.code() == OperationCode.NO_ELIGIBLE_TARGETS,
			"all-complete retry must stop before creating a flow: "
				+ alreadyComplete.code()
				+ " ("
				+ alreadyComplete.message()
				+ ")"
		);
		check(alreadyComplete.nextState().isEmpty(), "all-complete retry must not create an empty flow");
		checkVersionedTargetFilters(finished, definitions);

		StartResult roleStarted = ForcedFlowAuthority.start(
			finished,
			definitions,
			definitions.panelActions().get(id("test:assign_hunter")),
			HOST_ID,
			connected,
			List.of(PLAYER_ID),
			INSTANCE_B,
			ids(PAGE_C, PAGE_D),
			600L
		);
		check(roleStarted.successful(), "after-flow role start: " + roleStarted.message());
		WorldStateV2 roleActive = roleStarted.nextState().orElseThrow();
		check(roleActive.players().get(PLAYER_ID).roleId().equals(RUNNER_ID), "role stays pending");
		check(
			roleActive.players().get(PLAYER_ID).pendingRoleChange().orElseThrow().status()
				== PendingRoleChangeStatus.PENDING,
			"pending role marker"
		);
		WorldStateV2 roleConfirm = advance(roleActive, definitions, "continue", 0L, PAGE_D, 700L);
		WorldStateV2 roleFinished = advance(roleConfirm, definitions, "confirm", 1L, uuid(205), 800L);
		check(roleFinished.players().get(PLAYER_ID).roleId().equals(HUNTER_ID), "role applies");
		check(
			roleFinished.players().get(PLAYER_ID).pendingRoleChange().orElseThrow().status()
				== PendingRoleChangeStatus.APPLIED,
			"applied role marker"
		);
		check(roleFinished.completionHistory().getLast().forcedRedo(), "always is forced redo");
		checkFieldAndBranchSemantics(roleFinished, definitions, connected);

		StartResult offline = ForcedFlowAuthority.start(
			roleFinished,
			definitions,
			definitions.panelActions().get(id("test:assign_hunter")),
			HOST_ID,
			List.of(
				new ConnectedPlayer(HOST_ID, "Host", true, true),
				new ConnectedPlayer(PLAYER_ID, "Runner", false, false)
			),
			List.of(PLAYER_ID),
			uuid(104),
			UUID::randomUUID,
			900L
		);
		assertFailurePreserves(roleFinished, offline, OperationCode.TARGET_OFFLINE, "offline target");
		checkIndependentMemberRevisions(initial, definitions);
		checkSequenceWindow();
		System.out.println("FORCED_FLOW_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkImmediateRoleCandidates(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final List<ConnectedPlayer> connected
	) {
		PanelActionDefinition action = definitions.panelActions().get(INCOMPLETE_FILTER_ACTION_ID);
		var firstSeen = ForcedFlowAuthority.resolveEligibleCandidate(
			state,
			definitions,
			action,
			HOST_ID,
			connected.get(1),
			170L
		);
		check(
			firstSeen.successful(),
			"a first-seen default player must be eligible for an immediate role action"
		);
		var host = ForcedFlowAuthority.resolveEligibleCandidate(
			state,
			definitions,
			action,
			HOST_ID,
			connected.getFirst(),
			170L
		);
		check(
			host.code() == OperationCode.TARGET_INVALID,
			"the initiating host must be excluded from immediate role targets"
		);
	}

	private static void checkFatalBlockTransitions(final WorldStateV2 active) {
		var snapshotBlock = FlowBlockAuthority.block(
			active,
			OperationCode.SNAPSHOT_INVALID,
			"frozen snapshot failed",
			Optional.of(PLAYER_ID),
			250L
		);
		check(snapshotBlock.changed(), "snapshot failure must create a blocked candidate");
		WorldStateV2 snapshotBlocked = snapshotBlock.nextState().orElseThrow();
		var blockedFlow = snapshotBlocked.activeForcedFlow().orElseThrow();
		check(
			blockedFlow.runtime().status() == FlowInstanceStatus.BLOCKED,
			"snapshot failure status"
		);
		check(
			blockedFlow.runtime().blockDiagnostic()
				.map(diagnostic -> diagnostic.code().equals("snapshot_invalid"))
				.orElse(false),
			"snapshot failure diagnostic"
		);
		check(
			snapshotBlocked.audit().recentEvents().getLast().type()
				== AuditEventType.FLOW_BLOCKED,
			"snapshot failure audit"
		);
		check(
			snapshotBlocked.stateRevision() == active.stateRevision() + 1L,
			"snapshot block revision"
		);
		var duplicate = FlowBlockAuthority.block(
			snapshotBlocked,
			OperationCode.SNAPSHOT_INVALID,
			"frozen snapshot failed",
			Optional.of(PLAYER_ID),
			251L
		);
		check(!duplicate.changed() && duplicate.successful(), "duplicate block must be idempotent");
		check(
			!FlowBlockAuthority.recoverResourceBlock(snapshotBlocked, PLAYER_ID, 252L)
				.changed(),
			"snapshot failure must not be resource-recoverable"
		);

		var resourceBlock = FlowBlockAuthority.block(
			active,
			OperationCode.RESOURCE_BLOCKED,
			FlowBlockAuthority.CLIENT_PAGE_DIAGNOSTIC_PREFIX + "missing required texture",
			Optional.of(PLAYER_ID),
			260L
		);
		check(resourceBlock.changed(), "client resource failure must block");
		WorldStateV2 resourceBlocked = resourceBlock.nextState().orElseThrow();
		check(
			resourceBlocked.activeForcedFlow().orElseThrow().runtime().members()
				.get(PLAYER_ID)
				.status() == FlowMemberStatus.BLOCKED,
			"resource failure member status"
		);
		check(
			resourceBlocked.players().get(PLAYER_ID).activeFlowParticipation()
				.map(WorldStateV2.PlayerFlowParticipation::status)
				.filter(FlowMemberStatus.BLOCKED::equals)
				.isPresent(),
			"resource failure participation status"
		);
		check(
			FlowBlockAuthority.isRecoverableResourceBlock(
				resourceBlocked.activeForcedFlow().orElseThrow()
			),
			"client resource block classification"
		);
		check(
			!FlowBlockAuthority.recoverResourceBlock(
				resourceBlocked,
				SECOND_PLAYER_ID,
				261L
			).changed(),
			"another player must not clear a resource block"
		);
		var recovered = FlowBlockAuthority.recoverResourceBlock(
			resourceBlocked,
			PLAYER_ID,
			262L
		);
		check(recovered.changed() && recovered.successful(), "resource block must recover");
		WorldStateV2 resumed = recovered.nextState().orElseThrow();
		check(
			resumed.activeForcedFlow().orElseThrow().runtime().status()
				== FlowInstanceStatus.ACTIVE,
			"resource recovery status"
		);
		check(
			resumed.activeForcedFlow().orElseThrow().runtime().members()
				.get(PLAYER_ID)
				.status() == FlowMemberStatus.IN_PROGRESS,
			"resource recovery member status"
		);
		check(
			resumed.activeForcedFlow().orElseThrow().runtime().blockDiagnostic().isEmpty(),
			"resource recovery clears diagnostic"
		);
	}

	private static void checkPredicateGatedStart(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final List<ConnectedPlayer> connected
	) {
		PanelActionDefinition action = definitions.panelActions().get(GATED_ACTION_ID);
		assertFailurePreserves(
			state,
			ForcedFlowAuthority.start(
				state,
				definitions,
				action,
				HOST_ID,
				connected,
				List.of(),
				uuid(601),
				UUID::randomUUID,
				150L
			),
			OperationCode.ACTION_UNAVAILABLE,
			"predicate-gated default start"
		);

		PanelActionAuthorization allowed = new PanelActionAuthorization(
			GATED_ACTION_ID,
			definitions.generation(),
			state.stateRevision(),
			HOST_ID,
			true,
			true
		);
		StartResult authorized = ForcedFlowAuthority.start(
			state,
			definitions,
			action,
			HOST_ID,
			connected,
			List.of(),
			uuid(602),
			ids(uuid(603)),
			151L,
			allowed
		);
		check(authorized.successful(), "current predicate authorization must start the flow");
		check(
			authorized.nextState().orElseThrow().activeForcedFlow().orElseThrow().identity()
				.sourceActionId().equals(GATED_ACTION_ID),
			"authorized start source action"
		);

		assertGatedAuthorizationFailure(
			state,
			definitions,
			connected,
			new PanelActionAuthorization(
				GATED_ACTION_ID,
				definitions.generation() - 1L,
				state.stateRevision(),
				HOST_ID,
				true,
				true
			),
			"stale definition authorization"
		);
		assertGatedAuthorizationFailure(
			state,
			definitions,
			connected,
			new PanelActionAuthorization(
				GATED_ACTION_ID,
				definitions.generation(),
				state.stateRevision() - 1L,
				HOST_ID,
				true,
				true
			),
			"stale state authorization"
		);
		assertGatedAuthorizationFailure(
			state,
			definitions,
			connected,
			new PanelActionAuthorization(
				id("test:start"),
				definitions.generation(),
				state.stateRevision(),
				HOST_ID,
				true,
				true
			),
			"wrong action authorization"
		);
		assertGatedAuthorizationFailure(
			state,
			definitions,
			connected,
			new PanelActionAuthorization(
				GATED_ACTION_ID,
				definitions.generation(),
				state.stateRevision(),
				SECOND_PLAYER_ID,
				true,
				true
			),
			"wrong actor authorization"
		);
		assertGatedAuthorizationFailure(
			state,
			definitions,
			connected,
			new PanelActionAuthorization(
				GATED_ACTION_ID,
				definitions.generation(),
				state.stateRevision(),
				HOST_ID,
				false,
				true
			),
			"visible predicate denial"
		);
		assertGatedAuthorizationFailure(
			state,
			definitions,
			connected,
			new PanelActionAuthorization(
				GATED_ACTION_ID,
				definitions.generation(),
				state.stateRevision(),
				HOST_ID,
				true,
				false
			),
			"enabled predicate denial"
		);
	}

	private static void assertGatedAuthorizationFailure(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final List<ConnectedPlayer> connected,
		final PanelActionAuthorization authorization,
		final String label
	) {
		assertFailurePreserves(
			state,
			ForcedFlowAuthority.start(
				state,
				definitions,
				definitions.panelActions().get(GATED_ACTION_ID),
				HOST_ID,
				connected,
				List.of(),
				uuid(604),
				UUID::randomUUID,
				152L,
				authorization
			),
			OperationCode.ACTION_UNAVAILABLE,
			label
		);
	}

	private static void checkAutomaticTargetFilter(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final List<ConnectedPlayer> connected
	) {
		PanelActionDefinition action = definitions.panelActions().get(id("test:start"));
		var unfiltered = ForcedFlowAuthority.resolveTargets(
			state,
			definitions,
			action,
			HOST_ID,
			connected,
			List.of(),
			160L
		);
		check(
			unfiltered.successful()
				&& unfiltered.targets().stream().map(ConnectedPlayer::playerId).toList()
					.equals(List.of(PLAYER_ID)),
			"NONE baseline must select the audience member"
		);

		TargetFilter hunterOnly = new TargetFilter(
			Set.of(HUNTER_ID),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of()
		);
		PanelActionDefinition filteredAction = new PanelActionDefinition(
			action.id(),
			action.game(),
			action.surface(),
			action.section(),
			action.order(),
			action.label(),
			action.description(),
			action.icon(),
			action.color(),
			action.visibleWhen(),
			action.enabledWhen(),
			action.disabledReason(),
			action.phases(),
			new TargetSelection(SelectionMode.NONE, 0, 0, hunterOnly),
			action.confirmation(),
			action.operation()
		);
		var filtered = ForcedFlowAuthority.resolveTargets(
			state,
			definitions,
			filteredAction,
			HOST_ID,
			connected,
			List.of(),
			161L
		);
		check(filtered.successful(), "NONE filtered target resolution");
		check(
			filtered.targets().isEmpty(),
			"NONE automatic selection must apply the action target filter"
		);
		assertFailurePreserves(
			state,
			ForcedFlowAuthority.start(
				state,
				definitions,
				filteredAction,
				HOST_ID,
				connected,
				List.of(),
				uuid(605),
				UUID::randomUUID,
				162L
			),
			OperationCode.NO_ELIGIBLE_TARGETS,
			"NONE filtered start"
		);
	}

	private static void checkVersionedTargetFilters(
		final WorldStateV2 base,
		final DefinitionSnapshot definitions
	) {
		ConnectedPlayer candidate = new ConnectedPlayer(PLAYER_ID, "Runner", true, true);
		List<CompletionRecord> oldHistory = new ArrayList<>(base.completionHistory());
		oldHistory.add(
			new CompletionRecord(
				PLAYER_ID,
				FILTER_FLOW_ID,
				1,
				uuid(701),
				810L,
				COMPLETED_FILTER_ACTION_ID,
				false,
				definitions.generation()
			)
		);
		WorldStateV2 oldOnly = base.withCompletionHistory(oldHistory);
		check(oldOnly.validated().result().isPresent(), "old-version filter fixture");
		var completedFromOld = ForcedFlowAuthority.resolveEligibleCandidate(
			oldOnly,
			definitions,
			definitions.panelActions().get(COMPLETED_FILTER_ACTION_ID),
			HOST_ID,
			candidate,
			820L
		);
		check(
			completedFromOld.code() == OperationCode.TARGET_INVALID,
			"old flow version must not satisfy completed_flows"
		);
		var incompleteFromOld = ForcedFlowAuthority.resolveEligibleCandidate(
			oldOnly,
			definitions,
			definitions.panelActions().get(INCOMPLETE_FILTER_ACTION_ID),
			HOST_ID,
			candidate,
			820L
		);
		check(
			incompleteFromOld.successful(),
			"old flow version must satisfy incomplete_flows: " + incompleteFromOld.message()
		);

		List<CompletionRecord> currentHistory = new ArrayList<>(oldHistory);
		currentHistory.add(
			new CompletionRecord(
				PLAYER_ID,
				FILTER_FLOW_ID,
				2,
				uuid(702),
				830L,
				COMPLETED_FILTER_ACTION_ID,
				false,
				definitions.generation()
			)
		);
		WorldStateV2 current = base.withCompletionHistory(currentHistory);
		check(current.validated().result().isPresent(), "current-version filter fixture");
		check(
			ForcedFlowAuthority.resolveEligibleCandidate(
				current,
				definitions,
				definitions.panelActions().get(COMPLETED_FILTER_ACTION_ID),
				HOST_ID,
				candidate,
				840L
			).successful(),
			"current flow version must satisfy completed_flows"
		);
		check(
			ForcedFlowAuthority.resolveEligibleCandidate(
				current,
				definitions,
				definitions.panelActions().get(INCOMPLETE_FILTER_ACTION_ID),
				HOST_ID,
				candidate,
				840L
			).code() == OperationCode.TARGET_INVALID,
			"current flow version must not satisfy incomplete_flows"
		);

		List<CompletionRecord> initializedHistory = new ArrayList<>(currentHistory);
		initializedHistory.add(
			new CompletionRecord(
				PLAYER_ID,
				id("test:short"),
				1,
				uuid(703),
				850L,
				id("test:start"),
				false,
				definitions.generation()
			)
		);
		WorldStateV2 initialized = base.withCompletionHistory(initializedHistory);
		PanelActionDefinition initialize = definitions.panelActions().get(id("test:start"));
		check(
			ForcedFlowAuthority.resolveEligibleCandidate(
				initialized,
				definitions,
				initialize,
				HOST_ID,
				candidate,
				860L
			).code() == OperationCode.TARGET_INVALID,
			"if_incomplete previews must exclude current-version completions"
		);
		var noRemainingTargets = ForcedFlowAuthority.resolveTargets(
			initialized,
			definitions,
			initialize,
			HOST_ID,
			List.of(candidate),
			List.of(),
			860L
		);
		check(
			noRemainingTargets.successful() && noRemainingTargets.targets().isEmpty(),
			"if_incomplete automatic selection must omit completed players"
		);
	}

	private static void checkFieldAndBranchSemantics(
		final WorldStateV2 base,
		final DefinitionSnapshot definitions,
		final List<ConnectedPlayer> connected
	) {
		StartResult started = ForcedFlowAuthority.start(
			base,
			definitions,
			definitions.panelActions().get(id("test:start_fields")),
			HOST_ID,
			connected,
			List.of(),
			uuid(106),
			ids(uuid(301)),
			1_000L
		);
		check(started.successful(), "field flow must start: " + started.message());
		WorldStateV2 form = started.nextState().orElseThrow();
		var formMember = form.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID);

		assertFieldFailure(
			form,
			definitions,
			List.of(),
			OperationCode.TARGET_INVALID,
			"required fields"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "string", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\"")
			),
			OperationCode.TARGET_INVALID,
			"field type"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"nickname-too-long\"")
			),
			OperationCode.TARGET_INVALID,
			"atomic string rejection"
		);
		check(
			formMember.flowFields().isEmpty()
				&& form.players().get(PLAYER_ID).persistentFields().isEmpty(),
			"rejected mixed submission must not partially write fields"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "121"),
				field(NICKNAME_FIELD, "string", "\"Ace\"")
			),
			OperationCode.TARGET_INVALID,
			"integer max"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(BADGE_FIELD, "identifier", "\"not an id\"")
			),
			OperationCode.TARGET_INVALID,
			"identifier"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(TAGS_FIELD, "multi_choice", "[\"fast\",\"fast\"]")
			),
			OperationCode.TARGET_INVALID,
			"multi-choice duplicate"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(TAGS_FIELD, "multi_choice", "[\"fast\",\"smart\",\"brave\"]")
			),
			OperationCode.TARGET_INVALID,
			"multi-choice maximum"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(RUNNER_ONLY_FIELD, "string", "\"blocked\"")
			),
			OperationCode.TARGET_INVALID,
			"role applicability"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(OTHER_PHASE_FIELD, "string", "\"blocked\"")
			),
			OperationCode.TARGET_INVALID,
			"phase applicability"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(HOST_ONLY_FIELD, "string", "\"blocked\"")
			),
			OperationCode.FORBIDDEN,
			"editable_by player"
		);
		assertFieldFailure(
			form,
			definitions,
			List.of(
				field(AGE_FIELD, "integer", "20"),
				field(NICKNAME_FIELD, "string", "\"Ace\""),
				field(CONDITIONAL_FIELD, "string", "\"hidden\"")
			),
			OperationCode.TARGET_INVALID,
			"hidden field submission"
		);

		List<FieldInput> validFields = validFormFields();
		assertFailurePreserves(
			form,
			ForcedFlowAuthority.advance(
				form,
				definitions,
				submission(form, formMember, "continue", 0L, validFields),
				fieldVisibility(true),
				uuid(398),
				1_075L
			),
			OperationCode.TARGET_INVALID,
			"visible required field"
		);
		ActionResult formResult = ForcedFlowAuthority.advance(
			form,
			definitions,
			submission(form, formMember, "continue", 0L, validFields),
			fieldVisibility(false),
			uuid(302),
			1_100L
		);
		check(formResult.successful(), "valid fields must commit: " + formResult.message());
		WorldStateV2 choice = formResult.nextState().orElseThrow();
		var choiceMember = choice.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID);
		check(choiceMember.currentNodeId().equals(Optional.of("choice")), "form must advance to choice");
		check(
			choiceMember.flowFields().get(AGE_FIELD).integerValue().orElseThrow() == 20,
			"flow-scoped integer field"
		);
		check(
			choiceMember.flowFields().get(TAGS_FIELD).choices().equals(List.of("fast", "smart")),
			"multi-choice canonical order"
		);
		check(
			choice.players().get(PLAYER_ID).persistentFields()
				.get(NICKNAME_FIELD)
				.value()
				.stringValue()
				.equals(Optional.of("Ace")),
			"player-scoped string field"
		);

		Submission staleMember = new Submission(
			PLAYER_ID,
			choice.activeForcedFlow().orElseThrow().identity().instanceId(),
			choiceMember.pageInstanceId().orElseThrow(),
			FIELD_FLOW_ID,
			1,
			choiceMember.currentNodeId().orElseThrow(),
			choice.activeForcedFlow().orElseThrow().runtime().instanceRevision(),
			choiceMember.memberRevision() - 1L,
			1L,
			"submit",
			List.of(field(PATH_FIELD, "single_choice", "\"red\""))
		);
		assertFailurePreserves(
			choice,
			ForcedFlowAuthority.advance(
				choice,
				definitions,
				staleMember,
				uuid(303),
				1_101L
			),
			OperationCode.STATE_REVISION_STALE,
			"stale member revision"
		);
		assertFailurePreserves(
			choice,
			ForcedFlowAuthority.advance(
				choice,
				definitions,
				submission(
					choice,
					choiceMember,
					"submit",
					0L,
					List.of(field(PATH_FIELD, "single_choice", "\"red\""))
				),
				uuid(304),
				1_102L
			),
			OperationCode.REQUEST_REPLAYED,
			"field request replay"
		);
		assertFailurePreserves(
			choice,
			ForcedFlowAuthority.advance(
				choice,
				definitions,
				submission(
					choice,
					choiceMember,
					"continue",
					1L,
					List.of(field(PATH_FIELD, "single_choice", "\"red\""))
				),
				uuid(305),
				1_103L
			),
			OperationCode.NODE_MISMATCH,
			"choice action"
		);
		assertFailurePreserves(
			choice,
			ForcedFlowAuthority.advance(
				choice,
				definitions,
				submission(choice, choiceMember, "submit", 1L, List.of()),
				uuid(3051),
				1_103L
			),
			OperationCode.TARGET_INVALID,
			"choice missing field"
		);
		assertFailurePreserves(
			choice,
			ForcedFlowAuthority.advance(
				choice,
				definitions,
				submission(
					choice,
					choiceMember,
					"submit",
					1L,
					List.of(field(PATH_FIELD, "single_choice", "\"green\""))
				),
				uuid(306),
				1_104L
			),
			OperationCode.TARGET_INVALID,
			"choice option"
		);
		Submission redChoice = submission(
			choice,
			choiceMember,
			"submit",
			1L,
			List.of(field(PATH_FIELD, "single_choice", "\"red\""))
		);
		assertFailurePreserves(
			choice,
			ForcedFlowAuthority.advance(
				choice,
				definitions,
				redChoice,
				uuid(307),
				1_105L
			),
			OperationCode.UNSUPPORTED_FLOW_NODE,
			"branch without evaluator"
		);

		int[] predicatesObserved = {0};
		ActionResult trueBranch = ForcedFlowAuthority.advance(
			choice,
			definitions,
			redChoice,
			(predicate, context) -> {
				check(predicate.equals(BRANCH_PREDICATE), "branch predicate ID");
				check(
					context.flowFields().get(PATH_FIELD).stringValue().equals(Optional.of("red"))
						&& context.flowFields().get(AGE_FIELD).integerValue().orElseThrow() == 20,
					"predicate must see staged flow fields"
				);
				check(
					context.playerFields().containsKey(NICKNAME_FIELD),
					"predicate must see staged player fields"
				);
				predicatesObserved[0]++;
				return true;
			},
			uuid(308),
			1_106L
		);
		check(trueBranch.successful(), "true branch must advance: " + trueBranch.message());
		check(predicatesObserved[0] == 2, "continuous branches were not both evaluated");
		WorldStateV2 confirm = trueBranch.nextState().orElseThrow();
		check(
			confirm.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID)
				.currentNodeId()
				.equals(Optional.of("confirm")),
			"true branch route"
		);
		var confirmMember = confirm.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID);
		ActionResult confirmed = ForcedFlowAuthority.advance(
			confirm,
			definitions,
			submission(
				confirm,
				confirmMember,
				"confirm",
				2L,
				List.of(field(ACCEPT_FIELD, "boolean", "false"))
			),
			uuid(309),
			1_200L
		);
		check(
			confirmed.successful() && confirmed.memberCompleted(),
			"confirm must accept validated fields: " + confirmed.message()
		);
		WorldStateV2 firstFinished = confirmed.nextState().orElseThrow();
		check(
			firstFinished.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID)
				.flowFields()
				.get(ACCEPT_FIELD)
				.booleanValue()
				.equals(Optional.of(false)),
			"confirm-time field update"
		);

		StartResult fallbackStarted = ForcedFlowAuthority.start(
			firstFinished,
			definitions,
			definitions.panelActions().get(id("test:start_fields")),
			HOST_ID,
			connected,
			List.of(),
			uuid(107),
			ids(uuid(310)),
			1_300L
		);
		check(fallbackStarted.successful(), "fallback field flow must start");
		WorldStateV2 fallbackForm = fallbackStarted.nextState().orElseThrow();
		var fallbackFormMember = fallbackForm.activeForcedFlow().orElseThrow().runtime()
			.members()
			.get(PLAYER_ID);
		ActionResult fallbackFormResult = ForcedFlowAuthority.advance(
			fallbackForm,
			definitions,
			submission(fallbackForm, fallbackFormMember, "continue", 0L, validFields),
			fieldVisibility(false),
			uuid(311),
			1_400L
		);
		WorldStateV2 fallbackChoice = fallbackFormResult.nextState().orElseThrow();
		var fallbackChoiceMember = fallbackChoice.activeForcedFlow().orElseThrow().runtime()
			.members()
			.get(PLAYER_ID);
		ActionResult falseBranch = ForcedFlowAuthority.advance(
			fallbackChoice,
			definitions,
			submission(
				fallbackChoice,
				fallbackChoiceMember,
				"submit",
				1L,
				List.of(field(PATH_FIELD, "single_choice", "\"red\""))
			),
			(predicate, context) -> false,
			uuid(312),
			1_500L
		);
		check(falseBranch.successful(), "fallback branch must advance: " + falseBranch.message());
		check(
			falseBranch.nextState().orElseThrow().activeForcedFlow().orElseThrow().runtime()
				.members()
				.get(PLAYER_ID)
				.currentNodeId()
				.equals(Optional.of("fallback")),
			"branch fallback route"
		);
	}

	private static void assertFieldFailure(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final List<FieldInput> fields,
		final OperationCode code,
		final String label
	) {
		var member = state.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID);
		assertFailurePreserves(
			state,
			ForcedFlowAuthority.advance(
				state,
				definitions,
				submission(state, member, "continue", 0L, fields),
				fieldVisibility(false),
				uuid(399),
				1_050L
			),
			code,
			label
		);
	}

	private static List<FieldInput> validFormFields() {
		return List.of(
			field(AGE_FIELD, "integer", "20"),
			field(NICKNAME_FIELD, "string", "\"Ace\""),
			field(BADGE_FIELD, "identifier", "\"test:badge\""),
			field(ACCEPT_FIELD, "boolean", "true"),
			field(TAGS_FIELD, "multi_choice", "[\"smart\",\"fast\"]")
		);
	}

	private static FieldInput field(
		final Identifier fieldId,
		final String type,
		final String canonicalJson
	) {
		return new FieldInput(fieldId, type, canonicalJson);
	}

	private static ForcedFlowAuthority.PredicateEvaluator fieldVisibility(
		final boolean visible
	) {
		return (predicate, context) -> {
			check(predicate.equals(FIELD_VISIBILITY_PREDICATE), "field visibility predicate ID");
			return visible;
		};
	}

	private static void checkIndependentMemberRevisions(
		final WorldStateV2 initial,
		final DefinitionSnapshot definitions
	) {
		StartResult started = ForcedFlowAuthority.start(
			initial,
			definitions,
			definitions.panelActions().get(id("test:start")),
			HOST_ID,
			List.of(
				new ConnectedPlayer(HOST_ID, "Host", true, true),
				new ConnectedPlayer(PLAYER_ID, "Runner", true, true),
				new ConnectedPlayer(SECOND_PLAYER_ID, "Second", true, true)
			),
			List.of(),
			uuid(105),
			ids(uuid(205), uuid(206)),
			1_000L
		);
		check(started.successful(), "two-member flow must start");
		WorldStateV2 active = started.nextState().orElseThrow();
		var originalFlow = active.activeForcedFlow().orElseThrow();
		var first = originalFlow.runtime().members().get(PLAYER_ID);
		var second = originalFlow.runtime().members().get(SECOND_PLAYER_ID);
		ActionResult firstResult = ForcedFlowAuthority.advance(
			active,
			definitions,
			submission(active, first, "continue", 0L),
			uuid(207),
			1_100L
		);
		check(firstResult.successful(), "first member must advance");
		check(
			!firstResult.nextState().orElseThrow().activeForcedFlow().orElseThrow().runtime()
				.executionSnapshot()
				.pages()
				.isEmpty(),
			"active multi-member flow must retain frozen UI documents"
		);
		ActionResult secondResult = ForcedFlowAuthority.advance(
			firstResult.nextState().orElseThrow(),
			definitions,
			new Submission(
				SECOND_PLAYER_ID,
				originalFlow.identity().instanceId(),
				second.pageInstanceId().orElseThrow(),
				originalFlow.identity().flowId(),
				originalFlow.identity().flowVersion(),
				second.currentNodeId().orElseThrow(),
				originalFlow.runtime().instanceRevision(),
				second.memberRevision(),
				0L,
				"continue",
				List.of()
			),
			uuid(208),
			1_200L
		);
		check(
			secondResult.successful(),
			"an unrelated member revision must not invalidate this member's page"
		);
	}

	private static WorldStateV2 advance(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final String action,
		final long sequence,
		final UUID nextPage,
		final long now
	) {
		var member = state.activeForcedFlow().orElseThrow().runtime().members().get(PLAYER_ID);
		ActionResult result = ForcedFlowAuthority.advance(
			state,
			definitions,
			submission(state, member, action, sequence),
			nextPage,
			now
		);
		check(result.successful(), action + " must advance: " + result.message());
		return result.nextState().orElseThrow();
	}

	private static Submission submission(
		final WorldStateV2 state,
		final WorldStateV2.FlowMemberState member,
		final String action,
		final long sequence
	) {
		return submission(state, member, action, sequence, List.of());
	}

	private static Submission submission(
		final WorldStateV2 state,
		final WorldStateV2.FlowMemberState member,
		final String action,
		final long sequence,
		final List<FieldInput> fields
	) {
		var flow = state.activeForcedFlow().orElseThrow();
		return new Submission(
			PLAYER_ID,
			flow.identity().instanceId(),
			member.pageInstanceId().orElseThrow(),
			flow.identity().flowId(),
			flow.identity().flowVersion(),
			member.currentNodeId().orElseThrow(),
			flow.runtime().instanceRevision(),
			member.memberRevision(),
			sequence,
			action,
			fields
		);
	}

	private static void checkSequenceWindow() {
		var ten = ForcedFlowAuthority.acceptSequence(
			WorldStateV2.RequestSequenceWindow.empty(),
			10L
		).orElseThrow();
		var twelve = ForcedFlowAuthority.acceptSequence(ten, 12L).orElseThrow();
		var eleven = ForcedFlowAuthority.acceptSequence(twelve, 11L).orElseThrow();
		check(ForcedFlowAuthority.acceptSequence(eleven, 11L).isEmpty(), "window replay");
		check(
			ForcedFlowAuthority.acceptSequence(
				new WorldStateV2.RequestSequenceWindow(100L, 1L),
				0L
			).isEmpty(),
			"window bound"
		);
	}

	private static DefinitionSnapshot definitions() {
		List<Source> sources = List.of(
			source(DefinitionType.GAME, GAME_ID, """
				{"format_version":1,"api_version":1,"content_version":1,
				"name":{"text":"Test"},"initial_phase":"test:setup",
				"default_role":"test:runner","default_life_state":"test:alive"}
				"""),
			source(DefinitionType.ROLE, RUNNER_ID, """
				{"format_version":1,"game":"test:game","name":{"text":"Runner"},
				"tags":["test:participant"],"tab":{}}
				"""),
			source(DefinitionType.ROLE, HUNTER_ID, """
				{"format_version":1,"game":"test:game","name":{"text":"Hunter"},
				"tags":["test:participant"],"tab":{}}
				"""),
			source(DefinitionType.LIFE_STATE, id("test:alive"), """
				{"format_version":1,"game":"test:game","name":{"text":"Alive"},"tags":[]}
				"""),
			source(DefinitionType.PHASE, PHASE_ID, """
				{"format_version":1,"game":"test:game","name":{"text":"Setup"},"transitions":[]}
				"""),
			source(DefinitionType.PHASE, id("test:other"), """
				{"format_version":1,"game":"test:game","name":{"text":"Other"},"transitions":[]}
				"""),
			source(DefinitionType.FIELD, AGE_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Age"},"scope":"flow","type":"integer","required":true,
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"],
				"min":1,"max":120}
				"""),
			source(DefinitionType.FIELD, NICKNAME_FIELD, """
				{"format_version":1,"game":"test:game","version":2,
				"name":{"text":"Nickname"},"scope":"player","type":"string","required":true,
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"],
				"min_length":2,"max_length":8}
				"""),
			source(DefinitionType.FIELD, BADGE_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Badge"},"scope":"flow","type":"identifier",
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"]}
				"""),
			source(DefinitionType.FIELD, ACCEPT_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Accepted"},"scope":"flow","type":"boolean",
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"]}
				"""),
			source(DefinitionType.FIELD, TAGS_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Tags"},"scope":"flow","type":"multi_choice",
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"],
				"options":["fast","smart","brave"],"min_selected":1,"max_selected":2}
				"""),
			source(DefinitionType.FIELD, PATH_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Path"},"scope":"flow","type":"single_choice","required":true,
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"],
				"options":["red","blue"]}
				"""),
			source(DefinitionType.FIELD, RUNNER_ONLY_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Runner only"},"scope":"flow","type":"string",
				"editable_by":"player","roles":["test:runner"],"phases":["test:setup"]}
				"""),
			source(DefinitionType.FIELD, OTHER_PHASE_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Other phase"},"scope":"flow","type":"string",
				"editable_by":"player","roles":["test:hunter"],"phases":["test:other"]}
				"""),
			source(DefinitionType.FIELD, HOST_ONLY_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Host only"},"scope":"flow","type":"string",
				"editable_by":"host","roles":["test:hunter"],"phases":["test:setup"]}
				"""),
			source(DefinitionType.FIELD, CONDITIONAL_FIELD, """
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Conditional"},"scope":"flow","type":"string","required":true,
				"editable_by":"player","roles":["test:hunter"],"phases":["test:setup"],
				"visible_when":"test:field_visible"}
				"""),
			source(DefinitionType.PAGE, id("test:first"), page("First", "continue")),
			source(DefinitionType.PAGE, id("test:confirm"), confirmPage()),
			source(DefinitionType.PAGE, id("test:form"), formPage()),
			source(DefinitionType.PAGE, id("test:choice"), choicePage()),
			source(DefinitionType.PAGE, id("test:fallback"), page("Fallback", "continue")),
			source(DefinitionType.FLOW, FLOW_ID, """
				{"format_version":1,"game":"test:game","version":1,"name":{"text":"Short"},
				"entry":"first","audience":{"role_tags":["test:participant"],
				"exclude_host":true,"online_only":true},"required":true,
				"host_bossbar":{"name":{"text":"{event}: {completed}/{total}"},
				"color":"purple","style":"progress","priority":100},"nodes":[
				{"id":"first","type":"page","page":"test:first","next":"confirm"},
				{"id":"confirm","type":"confirm","page":"test:confirm","next":"done"},
				{"id":"done","type":"complete"}]}
				"""),
			source(DefinitionType.FLOW, FIELD_FLOW_ID, """
				{"format_version":1,"game":"test:game","version":1,"name":{"text":"Fields"},
				"entry":"form","audience":{"role_tags":["test:participant"],
				"exclude_host":true,"online_only":true},"required":true,
				"host_bossbar":{"name":{"text":"{event}: {completed}/{total}"},
				"color":"purple","style":"progress","priority":90},"nodes":[
				{"id":"form","type":"page","page":"test:form","next":"choice"},
				{"id":"choice","type":"choice","page":"test:choice","field":"test:path",
				"choices":[{"value":"red","next":"branch"},{"value":"blue","next":"fallback"}]},
				{"id":"branch","type":"branch","scope":"player",
				"cases":[{"predicate":"test:branch","next":"branch_two"}],"fallback":"fallback"},
				{"id":"branch_two","type":"branch","scope":"player",
				"cases":[{"predicate":"test:branch","next":"confirm"}],"fallback":"fallback"},
				{"id":"fallback","type":"page","page":"test:fallback","next":"confirm"},
				{"id":"confirm","type":"confirm","page":"test:confirm","next":"done"},
				{"id":"done","type":"complete"}]}
				"""),
			source(DefinitionType.FLOW, FILTER_FLOW_ID, """
				{"format_version":1,"game":"test:game","version":2,
				"name":{"text":"Versioned filter marker"},"entry":"done",
				"audience":{"exclude_host":true,"online_only":true},"required":false,
				"nodes":[{"id":"done","type":"complete"}]}
				"""),
			source(DefinitionType.PANEL_ACTION, id("test:start"), """
				{"format_version":1,"game":"test:game","surface":"host","section":"primary",
				"order":1,"label":{"text":"Start"},"description":{"text":"Start flow"},
				"phases":["test:setup"],"target":{"mode":"none"},"confirmation":{
				"title":{"text":"Confirm start"},"consequences":[{"text":"Starts flow"}]},
				"operation":{"type":"start_flow","flow":"test:short",
				"completion_policy":"if_incomplete"}}
				"""),
			source(DefinitionType.PANEL_ACTION, id("test:assign_hunter"), """
				{"format_version":1,"game":"test:game","surface":"host","section":"roles",
				"order":2,"label":{"text":"Assign hunter"},
				"description":{"text":"Assign after flow"},"phases":["test:setup"],
				"target":{"mode":"multiple","min":1,"max":64,
				"filter":{"roles":["test:runner","test:hunter"]}},"confirmation":{
				"title":{"text":"Confirm hunter"},"consequences":[{"text":"Applies after flow"}]},
				"operation":{"type":"assign_role","role":"test:hunter","flow":"test:short",
				"apply":"after_flow","completion_policy":"always"}}
				"""),
			source(DefinitionType.PANEL_ACTION, COMPLETED_FILTER_ACTION_ID, """
				{"format_version":1,"game":"test:game","surface":"host","section":"roles",
				"order":3,"label":{"text":"Needs current completion"},
				"description":{"text":"Versioned completed filter"},"phases":["test:setup"],
				"target":{"mode":"single",
				"filter":{"completed_flows":["test:filter_marker"]}},"confirmation":{
				"title":{"text":"Confirm"},"consequences":[{"text":"Filter check"}]},
				"operation":{"type":"assign_role","role":"test:hunter","apply":"immediate"}}
				"""),
			source(DefinitionType.PANEL_ACTION, INCOMPLETE_FILTER_ACTION_ID, """
				{"format_version":1,"game":"test:game","surface":"host","section":"roles",
				"order":4,"label":{"text":"Needs current incompletion"},
				"description":{"text":"Versioned incomplete filter"},"phases":["test:setup"],
				"target":{"mode":"single",
				"filter":{"incomplete_flows":["test:filter_marker"]}},"confirmation":{
				"title":{"text":"Confirm"},"consequences":[{"text":"Filter check"}]},
				"operation":{"type":"assign_role","role":"test:hunter","apply":"immediate"}}
				"""),
			source(DefinitionType.PANEL_ACTION, id("test:start_fields"), """
				{"format_version":1,"game":"test:game","surface":"host","section":"primary",
				"order":3,"label":{"text":"Start fields"},"description":{"text":"Field flow"},
				"phases":["test:setup"],"target":{"mode":"none"},"confirmation":{
				"title":{"text":"Confirm fields"},"consequences":[{"text":"Starts fields"}]},
				"operation":{"type":"start_flow","flow":"test:fields",
				"completion_policy":"always"}}
				"""),
			source(DefinitionType.PANEL_ACTION, GATED_ACTION_ID, """
				{"format_version":1,"game":"test:game","surface":"host","section":"primary",
				"order":4,"label":{"text":"Start gated"},
				"description":{"text":"Predicate-gated flow"},
				"visible_when":"test:field_visible","enabled_when":"test:field_visible",
				"disabled_reason":{"text":"Conditions not met"},"phases":["test:setup"],
				"target":{"mode":"none"},"confirmation":{
				"title":{"text":"Confirm gated"},"consequences":[{"text":"Starts flow"}]},
				"operation":{"type":"start_flow","flow":"test:short",
				"completion_policy":"always"}}
				""")
		);
		var compilation = DefinitionCompiler.compile(
			sources,
			Set.of(),
			Set.of(BRANCH_PREDICATE, FIELD_VISIBILITY_PREDICATE)
		);
		check(
			compilation.valid(),
			"fixture compile: "
				+ compilation.problems().stream().map(DefinitionCompiler.Problem::summary).toList()
		);
		return compilation.snapshot().orElseThrow().withGeneration(7L);
	}

	private static String page(final String title, final String action) {
		return """
			{"format_version":1,"game":"test:game","title":{"text":"%s"},
			"theme":"pixel-tzz-pro:default","root":{"type":"button","id":"action",
			"label":{"text":"%s"},"action":{"type":"flow","name":"%s"}}}
			""".formatted(title, title, action);
	}

	private static String formPage() {
		return """
			{"format_version":1,"game":"test:game","title":{"text":"Form"},
			"theme":"pixel-tzz-pro:default","root":{"type":"column","children":[
			{"type":"field_input","id":"age","field":"test:age","presentation":"number"},
			{"type":"field_input","id":"nickname","field":"test:nickname","presentation":"text"},
			{"type":"field_input","id":"badge","field":"test:badge","presentation":"identifier_text"},
			{"type":"field_input","id":"accepted","field":"test:accepted","presentation":"toggle"},
			{"type":"field_input","id":"tags","field":"test:tags","presentation":"checkbox_list"},
			{"type":"field_input","id":"runner_only","field":"test:runner_only","presentation":"text"},
			{"type":"field_input","id":"other_phase","field":"test:other_phase_only",
			"presentation":"text"},
			{"type":"field_input","id":"host_only","field":"test:host_only",
			"presentation":"text"},
			{"type":"field_input","id":"conditional","field":"test:conditional",
			"presentation":"text"},
			{"type":"button","id":"continue","label":{"text":"Continue"},
			"action":{"type":"flow","name":"continue"}}]}}
			""";
	}

	private static String choicePage() {
		return """
			{"format_version":1,"game":"test:game","title":{"text":"Choice"},
			"theme":"pixel-tzz-pro:default","root":{"type":"column","children":[
			{"type":"field_input","id":"path","field":"test:path",
			"presentation":"choice_cards"},
			{"type":"button","id":"submit","label":{"text":"Submit"},
			"action":{"type":"flow","name":"submit"}}]}}
			""";
	}

	private static String confirmPage() {
		return """
			{"format_version":1,"game":"test:game","title":{"text":"Confirm"},
			"theme":"pixel-tzz-pro:default","root":{"type":"column","children":[
			{"type":"field_input","id":"accepted","field":"test:accepted",
			"presentation":"toggle"},
			{"type":"button","id":"confirm","label":{"text":"Confirm"},
			"action":{"type":"flow","name":"confirm"}}]}}
			""";
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

	private static Supplier<UUID> ids(final UUID... values) {
		Queue<UUID> queue = new ArrayDeque<>(List.of(values));
		return () -> {
			UUID value = queue.poll();
			if (value == null) {
				throw new AssertionError("page UUID fixture exhausted");
			}
			return value;
		};
	}

	private static void assertFailurePreserves(
		final WorldStateV2 state,
		final StartResult result,
		final OperationCode code,
		final String label
	) {
		JsonElement before = encode(state);
		check(result.code() == code, label + " returned " + result.code());
		check(result.nextState().isEmpty(), label + " must not produce state");
		check(encode(state).equals(before), label + " mutated state");
	}

	private static void assertFailurePreserves(
		final WorldStateV2 state,
		final ActionResult result,
		final OperationCode code,
		final String label
	) {
		JsonElement before = encode(state);
		check(result.code() == code, label + " returned " + result.code());
		check(result.nextState().isEmpty(), label + " must not produce state");
		check(encode(state).equals(before), label + " mutated state");
	}

	private static JsonElement encode(final WorldStateV2 state) {
		return WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
	}

	private static UUID uuid(final long value) {
		return new UUID(0L, value);
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
