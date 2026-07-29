package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceRoute;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.EditPolicy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ExclusiveChoiceDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ExclusiveChoiceOption;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldConstraints;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldMigrationStrategy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.NodeScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ReservationScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.FlowBlockAuthority;
import io.github.zcpu954861.pixeltzzpro.server.FlowRosterAuthority;
import io.github.zcpu954861.pixeltzzpro.server.FlowRosterAuthority.OnlineMember;
import io.github.zcpu954861.pixeltzzpro.server.FlowRosterAuthority.Result;
import io.github.zcpu954861.pixeltzzpro.server.FlowRosterAuthority.V3Result;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.BlockDiagnostic;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackFailure;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackKind;
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
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostOperation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChange;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RemovalRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.RequestSequenceWindow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Runnable contract check for authoritative roster edits and immediate role assignment.
 */
public final class FlowRosterAuthoritySelfCheck {
	private static final UUID HOST_ID = uuid(1);
	private static final UUID MEMBER_ID = uuid(2);
	private static final UUID SECOND_MEMBER_ID = uuid(3);
	private static final UUID NEW_MEMBER_ID = uuid(4);
	private static final UUID HISTORY_MEMBER_ID = uuid(5);
	private static final UUID REMOVED_MEMBER_ID = uuid(6);
	private static final UUID INSTANCE_ID = uuid(100);
	private static final UUID OTHER_INSTANCE_ID = uuid(101);
	private static final UUID NEW_PAGE_ID = uuid(200);
	private static final Identifier GAME_ID = id("test:game");
	private static final Identifier OTHER_GAME_ID = id("test:other_game");
	private static final Identifier PHASE_ID = id("test:setup");
	private static final Identifier FLOW_ID = id("test:flow");
	private static final Identifier ACTION_ID = id("test:action");
	private static final Identifier CALLBACK_FUNCTION_ID = id("test:callback");
	private static final Identifier RUNNER_ROLE_ID = id("test:runner");
	private static final Identifier HUNTER_ROLE_ID = id("test:hunter");
	private static final Identifier FOREIGN_ROLE_ID = id("test:foreign");
	private static final Identifier LIFE_STATE_ID = id("test:alive");
	private static final Identifier DEAD_LIFE_STATE_ID = id("test:dead");
	private static final long STATE_REVISION = 7L;
	private static final long INSTANCE_REVISION = 3L;
	private static final long UPDATED_AT = 100L;
	private static final long NOW = 200L;

	private FlowRosterAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		WorldStateV2.initial();
		checkAddMembers();
		checkAddFailures();
		checkExclusiveCapacityOnAdd();
		checkFrozenTargetPolicy();
		checkRemoval();
		checkCallbackFailureRemovalFence();
		checkCancellation();
		checkImmediateRoleAssignment();
		checkWriteFences();
		System.out.println("FLOW_ROSTER_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkAddMembers() {
		WorldStateV2 active = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(
				MEMBER_ID,
				member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS),
				REMOVED_MEMBER_ID,
				member(REMOVED_MEMBER_ID, FlowMemberStatus.REMOVED)
			),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of()
		);
		JsonElement before = encode(active);
		Result result = FlowRosterAuthority.addMembers(
			active,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(NEW_MEMBER_ID, "New Runner", true)),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(result.successful(), "online member must be added: " + result.message());
		check(encode(active).equals(before), "successful add mutated its input");
		WorldStateV2 next = result.nextState().orElseThrow();
		ForcedFlowRuntime runtime = next.activeForcedFlow().orElseThrow().runtime();
		FlowMemberState added = runtime.members().get(NEW_MEMBER_ID);
		check(runtime.members().size() == 3, "removed roster entry was discarded");
		check(runtime.totalCount() == 2 && runtime.completedCount() == 0, "add counts");
		check(runtime.instanceRevision() == INSTANCE_REVISION + 1L, "add instance revision");
		check(next.stateRevision() == STATE_REVISION + 1L, "add state revision");
		check(added.status() == FlowMemberStatus.IN_PROGRESS, "added member status");
		check(added.currentNodeId().equals(Optional.of("entry")), "added member entry");
		check(added.pageInstanceId().equals(Optional.of(NEW_PAGE_ID)), "added member page ID");
		check(
			next.players().get(NEW_MEMBER_ID).activeFlowParticipation()
				.map(PlayerFlowParticipation::instanceId)
				.equals(Optional.of(INSTANCE_ID)),
			"added member participation"
		);
		check(
			next.players().get(NEW_MEMBER_ID).lastKnownName().equals("New Runner"),
			"online name snapshot"
		);
		check(hasAudit(next, AuditEventType.MEMBER_ADDED), "member-added audit");
		encode(next);

		WorldStateV2 resourceBlocked = FlowBlockAuthority.block(
			active,
			OperationCode.RESOURCE_BLOCKED,
			FlowBlockAuthority.CLIENT_PAGE_DIAGNOSTIC_PREFIX + "missing texture",
			Optional.of(MEMBER_ID),
			NOW
		).nextState().orElseThrow();
		Result blockedAddition = FlowRosterAuthority.addMembers(
			resourceBlocked,
			HOST_ID,
			INSTANCE_ID,
			resourceBlocked.activeForcedFlow().orElseThrow().runtime().instanceRevision(),
			List.of(new OnlineMember(NEW_MEMBER_ID, "Blocked Addition", true)),
			() -> NEW_PAGE_ID,
			NOW + 1L
		);
		check(blockedAddition.successful(), "recoverable resource block accepts additions");
		WorldStateV2 blockedAdded = blockedAddition.nextState().orElseThrow();
		check(
			blockedAdded.activeForcedFlow().orElseThrow().runtime().status()
				== FlowInstanceStatus.BLOCKED,
			"blocked addition preserves instance status"
		);
		check(
			FlowBlockAuthority.isRecoverableResourceBlock(
				blockedAdded.activeForcedFlow().orElseThrow()
			),
			"blocked addition preserves resource diagnostic"
		);

		WorldStateV2 roleFlow = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of(),
			true
		);
		Result roleAddResult = FlowRosterAuthority.addMembers(
			roleFlow,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(NEW_MEMBER_ID, "Future Hunter", true)),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(roleAddResult.successful(), "after-flow role member must be addable");
		WorldStateV2 roleAdded = roleAddResult.nextState().orElseThrow();
		PendingRoleChange pending = roleAdded.players().get(NEW_MEMBER_ID)
			.pendingRoleChange()
			.orElseThrow();
		check(pending.targetRoleId().equals(HUNTER_ROLE_ID), "added target role");
		check(pending.flowInstanceId().equals(INSTANCE_ID), "added role flow binding");
		check(pending.sourceActionId().equals(ACTION_ID), "added role source action");
		check(pending.status() == PendingRoleChangeStatus.PENDING, "added pending role status");
		check(hasAudit(roleAdded, AuditEventType.ROLE_CHANGE_CREATED), "role-created audit");

		CompletionRecord previous = completion(HISTORY_MEMBER_ID, 80L);
		WorldStateV2 incompleteOnly = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.IF_INCOMPLETE,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(HISTORY_MEMBER_ID),
			List.of(previous)
		);
		int[] pageRequests = {0};
		assertFailurePreserves(
			incompleteOnly,
			() -> FlowRosterAuthority.addMembers(
				incompleteOnly,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(HISTORY_MEMBER_ID, "Returning Runner", true)),
				() -> {
					pageRequests[0]++;
					return NEW_PAGE_ID;
				},
				NOW
			),
			OperationCode.TARGET_INVALID,
			"history-complete member addition"
		);
		check(pageRequests[0] == 0, "history-complete rejection allocated a page");

		WorldStateV2 firstSeen = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(),
			List.of()
		);
		check(!firstSeen.players().containsKey(NEW_MEMBER_ID), "first-seen fixture already has player");
		Result firstSeenResult = FlowRosterAuthority.addMembers(
			firstSeen,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(NEW_MEMBER_ID, "First Seen Runner", true)),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(
			firstSeenResult.successful(),
			"first-seen online member must be initialized from frozen defaults: "
				+ firstSeenResult.message()
		);
		PlayerRecord initialized = firstSeenResult.nextState().orElseThrow()
			.players()
			.get(NEW_MEMBER_ID);
		check(initialized != null, "first-seen member record was not persisted");
		check(initialized.roleId().equals(RUNNER_ROLE_ID), "first-seen default role");
		check(initialized.lifeStateId().equals(LIFE_STATE_ID), "first-seen default life state");
		check(
			initialized.firstSeenAtEpochMillis() == NOW
				&& initialized.lastSeenAtEpochMillis() == NOW,
			"first-seen timestamps"
		);
	}

	private static void checkExclusiveCapacityOnAdd() {
		WorldStateV2 core = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of()
		);
		UUID gameScopeId = uuid(300);
		WorldStateV3 state = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(gameScopeId),
			Optional.empty(),
			List.of(),
			List.of()
		);
		V3Result result = FlowRosterAuthority.addMembers(
			state,
			exclusiveDefinitions(),
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(NEW_MEMBER_ID, "New Runner", true)),
			() -> NEW_PAGE_ID,
			NOW,
			gameScopeId
		);
		check(
			result.code() == OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE,
			"exclusive capacity must reject a roster larger than its option set"
		);
		check(result.nextState().isEmpty(), "failed exclusive-capacity add produced state");
		check(state.core().equals(core), "failed exclusive-capacity add mutated its input");
	}

	private static void checkAddFailures() {
		WorldStateV2 active = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of()
		);
		OnlineMember online = new OnlineMember(NEW_MEMBER_ID, "New Runner", true);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.addMembers(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(online, online),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"duplicate additions"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.addMembers(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(MEMBER_ID, "Existing", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"existing member addition"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.addMembers(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "Offline", false)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_OFFLINE,
			"offline member addition"
		);
		WorldStateV2 filterMismatch = withIdentity(
			active,
			NEW_MEMBER_ID,
			HUNTER_ROLE_ID,
			LIFE_STATE_ID
		);
		assertFailurePreserves(
			filterMismatch,
			() -> FlowRosterAuthority.addMembers(
				filterMismatch,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "Hunter", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"frozen source filter"
		);
		WorldStateV2 audienceMismatch = withIdentity(
			active,
			NEW_MEMBER_ID,
			RUNNER_ROLE_ID,
			DEAD_LIFE_STATE_ID
		);
		assertFailurePreserves(
			audienceMismatch,
			() -> FlowRosterAuthority.addMembers(
				audienceMismatch,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "Dead Runner", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"frozen flow audience"
		);
		UUID existingPage = active.activeForcedFlow().orElseThrow().runtime()
			.members()
			.get(MEMBER_ID)
			.pageInstanceId()
			.orElseThrow();
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.addMembers(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(online),
				() -> existingPage,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"duplicate page ID"
		);

		WorldStateV2 full = fullRosterState();
		assertFailurePreserves(
			full,
			() -> FlowRosterAuthority.addMembers(
				full,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "Sixty Fifth", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_COUNT_INVALID,
			"64-member capacity"
		);

		WorldStateV2 completed = flowState(
			FlowInstanceStatus.COMPLETED,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.COMPLETED)),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of(completion(MEMBER_ID, 80L))
		);
		assertFailurePreserves(
			completed,
			() -> FlowRosterAuthority.addMembers(
				completed,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(online),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.FLOW_NOT_ACTIVE,
			"terminal flow addition"
		);

		WorldStateV2 blocked = flowState(
			FlowInstanceStatus.BLOCKED,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.BLOCKED)),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of()
		);
		JsonElement blockedBefore = encode(blocked);
		Result blockedResult = FlowRosterAuthority.addMembers(
			blocked,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(online),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(blockedResult.code() == OperationCode.FLOW_NOT_ACTIVE, "blocked add code");
		check(
			blockedResult.message().contains("recovered")
				&& blockedResult.message().contains("canceled"),
			"blocked add must explain its fail-closed recovery boundary"
		);
		check(blockedResult.nextState().isEmpty(), "blocked add produced a candidate");
		check(encode(blocked).equals(blockedBefore), "blocked add mutated input");
	}

	private static void checkFrozenTargetPolicy() {
		WorldStateV2 transferredHost = withHost(
			flowState(
				FlowInstanceStatus.ACTIVE,
				CompletionPolicy.ALWAYS,
				Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
				Map.of(),
				Set.of(HOST_ID, SECOND_MEMBER_ID),
				List.of()
			),
			SECOND_MEMBER_ID
		);
		assertFailurePreserves(
			transferredHost,
			() -> FlowRosterAuthority.addMembers(
				transferredHost,
				SECOND_MEMBER_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(HOST_ID, "Original Host", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"frozen initiating host exclusion"
		);
		Result currentHostResult = FlowRosterAuthority.addMembers(
			transferredHost,
			SECOND_MEMBER_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(SECOND_MEMBER_ID, "Current Host", true)),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(
			currentHostResult.successful(),
			"audience must exclude the frozen initiator, not a later host: "
				+ currentHostResult.message()
		);

		ExecutionSnapshot completedFilter = snapshot(
			CompletionPolicy.ALWAYS,
			false,
			"""
			"roles":["test:runner"],
			"completed_flows":["test:flow"]
			"""
		);
		WorldStateV2 oldCompleted = withSnapshot(
			flowState(
				FlowInstanceStatus.ACTIVE,
				CompletionPolicy.ALWAYS,
				Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
				Map.of(),
				Set.of(NEW_MEMBER_ID),
				List.of(completion(NEW_MEMBER_ID, FLOW_ID, 2, 80L))
			),
			completedFilter
		);
		assertFailurePreserves(
			oldCompleted,
			() -> FlowRosterAuthority.addMembers(
				oldCompleted,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "Old Completion", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"old-version completed filter"
		);
		WorldStateV2 currentCompleted = withSnapshot(
			flowState(
				FlowInstanceStatus.ACTIVE,
				CompletionPolicy.ALWAYS,
				Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
				Map.of(),
				Set.of(NEW_MEMBER_ID),
				List.of(completion(NEW_MEMBER_ID, FLOW_ID, 1, 80L))
			),
			completedFilter
		);
		Result completedResult = FlowRosterAuthority.addMembers(
			currentCompleted,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(NEW_MEMBER_ID, "Current Completion", true)),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(
			completedResult.successful(),
			"frozen current-version completion must satisfy completed_flows: "
				+ completedResult.message()
		);

		ExecutionSnapshot incompleteFilter = snapshot(
			CompletionPolicy.ALWAYS,
			false,
			"""
			"roles":["test:runner"],
			"incomplete_flows":["test:flow"]
			"""
		);
		WorldStateV2 oldVersionIsIncomplete = withSnapshot(oldCompleted, incompleteFilter);
		Result incompleteResult = FlowRosterAuthority.addMembers(
			oldVersionIsIncomplete,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(new OnlineMember(NEW_MEMBER_ID, "Old Version Is Incomplete", true)),
			() -> NEW_PAGE_ID,
			NOW
		);
		check(
			incompleteResult.successful(),
			"old completion version must remain incomplete for the frozen flow: "
				+ incompleteResult.message()
		);
		WorldStateV2 currentVersionIsComplete = withSnapshot(
			currentCompleted,
			incompleteFilter
		);
		assertFailurePreserves(
			currentVersionIsComplete,
			() -> FlowRosterAuthority.addMembers(
				currentVersionIsComplete,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "Current Version Is Complete", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.TARGET_INVALID,
			"current-version incomplete filter"
		);
	}

	private static void checkRemoval() {
		WorldStateV2 active = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(
				MEMBER_ID,
				member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS),
				SECOND_MEMBER_ID,
				member(SECOND_MEMBER_ID, FlowMemberStatus.IN_PROGRESS)
			),
			Map.of(MEMBER_ID, pending(MEMBER_ID)),
			Set.of(),
			List.of()
		);
		Result removedResult = FlowRosterAuthority.removeMembers(
			active,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(MEMBER_ID),
			"left the event",
			NOW
		);
		check(removedResult.successful(), "unfinished member must be removable");
		WorldStateV2 removed = removedResult.nextState().orElseThrow();
		ForcedFlowRuntime runtime = removed.activeForcedFlow().orElseThrow().runtime();
		FlowMemberState removedMember = runtime.members().get(MEMBER_ID);
		check(runtime.status() == FlowInstanceStatus.ACTIVE, "remaining member keeps flow active");
		check(runtime.totalCount() == 1 && runtime.completedCount() == 0, "removal denominator");
		check(removedMember.status() == FlowMemberStatus.REMOVED, "removed member status");
		check(removedMember.currentNodeId().isEmpty(), "removed member retained node");
		check(removedMember.pageInstanceId().isEmpty(), "removed member retained page");
		check(
			removedMember.removal().map(RemovalRecord::reason).equals(Optional.of("left the event")),
			"removal reason"
		);
		check(
			removed.players().get(MEMBER_ID).pendingRoleChange().orElseThrow().status()
				== PendingRoleChangeStatus.CANCELED,
			"removal canceled pending role"
		);
		check(
			removed.players().get(MEMBER_ID).activeFlowParticipation().isEmpty(),
			"removal cleared participation"
		);
		check(removed.completionHistory().isEmpty(), "removal manufactured completion history");
		check(!archived(runtime.executionSnapshot()), "active removal compacted snapshot");

		CompletionRecord previous = completion(SECOND_MEMBER_ID, 80L);
		WorldStateV2 oneComplete = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(
				MEMBER_ID,
				member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS),
				SECOND_MEMBER_ID,
				member(SECOND_MEMBER_ID, FlowMemberStatus.COMPLETED)
			),
			Map.of(MEMBER_ID, pending(MEMBER_ID)),
			Set.of(),
			List.of(previous)
		);
		Result completedResult = FlowRosterAuthority.removeMembers(
			oneComplete,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(MEMBER_ID),
			"remove unfinished",
			NOW
		);
		check(
			completedResult.successful() && completedResult.becameCompleted(),
			"removal must complete an all-complete remainder"
		);
		WorldStateV2 completed = completedResult.nextState().orElseThrow();
		ForcedFlowRuntime completedRuntime = completed.activeForcedFlow().orElseThrow().runtime();
		check(completedRuntime.status() == FlowInstanceStatus.COMPLETED, "removal completion status");
		check(
			completedRuntime.totalCount() == 1 && completedRuntime.completedCount() == 1,
			"removal completion counts"
		);
		check(
			completed.players().values().stream()
				.allMatch(player -> player.activeFlowParticipation().isEmpty()),
			"terminal removal retained participation"
		);
		check(completed.completionHistory().equals(List.of(previous)), "terminal removal changed history");
		check(archived(completedRuntime.executionSnapshot()), "terminal removal kept full snapshot");
		check(hasAudit(completed, AuditEventType.FLOW_COMPLETED), "terminal removal audit");

		String maximumReason = "x".repeat(1_024);
		WorldStateV2 soleMember = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(MEMBER_ID, pending(MEMBER_ID)),
			Set.of(),
			List.of()
		);
		Result canceledResult = FlowRosterAuthority.removeMembers(
			soleMember,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(MEMBER_ID),
			maximumReason,
			NOW
		);
		check(canceledResult.successful(), "last member removal must succeed");
		WorldStateV2 canceled = canceledResult.nextState().orElseThrow();
		ForcedFlowRuntime canceledRuntime = canceled.activeForcedFlow().orElseThrow().runtime();
		check(canceledRuntime.status() == FlowInstanceStatus.CANCELED, "all-removed status");
		check(canceledRuntime.totalCount() == 0, "all-removed denominator");
		check(!canceledResult.becameCompleted(), "all-removed falsely completed");
		check(
			canceledRuntime.members().get(MEMBER_ID).removal().orElseThrow().reason()
				.equals(maximumReason),
			"maximum removal reason was truncated"
		);
		check(archived(canceledRuntime.executionSnapshot()), "all-removed kept full snapshot");
		check(
			canceled.audit().recentEvents().stream()
				.allMatch(event -> event.summary().length() <= 1_024),
			"audit summary exceeded persisted limit"
		);
		encode(canceled);
	}

	private static void checkCallbackFailureRemovalFence() {
		WorldStateV2 callbackBlocked = withCallbackFailure(
			flowState(
				FlowInstanceStatus.BLOCKED,
				CompletionPolicy.ALWAYS,
				Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
				Map.of(),
				Set.of(),
				List.of()
			)
		);
		JsonElement before = encode(callbackBlocked);
		Result result = FlowRosterAuthority.removeMembers(
			callbackBlocked,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			List.of(MEMBER_ID),
			"must not erase callback failure",
			NOW
		);
		check(result.successful(), "callback-blocked removal must remain available");
		WorldStateV2 callbackRemoved = result.nextState().orElseThrow();
		ForcedFlowRuntime callbackRuntime = callbackRemoved.activeForcedFlow()
			.orElseThrow()
			.runtime();
		check(
			callbackRuntime.status() == FlowInstanceStatus.BLOCKED,
			"callback-blocked removal lost blocked status"
		);
		check(
			!callbackRuntime.callbacks().failures().isEmpty()
				&& callbackRuntime.blockDiagnostic().isPresent(),
			"callback-blocked removal lost retry diagnostics"
		);
		check(
			callbackRuntime.members().get(MEMBER_ID).status() == FlowMemberStatus.REMOVED,
			"callback-blocked removal did not remove target"
		);
		check(!result.becameCompleted(), "callback-blocked removal reported completion");
		check(encode(callbackBlocked).equals(before), "callback-blocked removal mutated input");

		WorldStateV2 active = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(),
			List.of()
		);
		WorldStateV2 resourceBlocked = FlowBlockAuthority.block(
			active,
			OperationCode.RESOURCE_BLOCKED,
			FlowBlockAuthority.CLIENT_PAGE_DIAGNOSTIC_PREFIX + "missing texture",
			Optional.of(MEMBER_ID),
			NOW
		).nextState().orElseThrow();
		Result resourceRemoval = FlowRosterAuthority.removeMembers(
			resourceBlocked,
			HOST_ID,
			INSTANCE_ID,
			resourceBlocked.activeForcedFlow().orElseThrow().runtime().instanceRevision(),
			List.of(MEMBER_ID),
			"must recover first",
			NOW + 1L
		);
		check(resourceRemoval.successful(), "resource-blocked owner must be removable");
		ForcedFlowRuntime resourceRuntime = resourceRemoval.nextState()
			.orElseThrow()
			.activeForcedFlow()
			.orElseThrow()
			.runtime();
		check(
			resourceRuntime.status() == FlowInstanceStatus.CANCELED
				&& resourceRuntime.blockDiagnostic().isEmpty(),
			"removing resource-block owner must clear the recoverable block"
		);
	}

	private static void checkCancellation() {
		checkCancellation(FlowInstanceStatus.ACTIVE, FlowMemberStatus.IN_PROGRESS);
		checkCancellation(FlowInstanceStatus.BLOCKED, FlowMemberStatus.BLOCKED);
	}

	private static void checkCancellation(
		final FlowInstanceStatus instanceStatus,
		final FlowMemberStatus memberStatus
	) {
		CompletionRecord previous = completion(SECOND_MEMBER_ID, 80L);
		WorldStateV2 state = flowState(
			instanceStatus,
			CompletionPolicy.ALWAYS,
			Map.of(
				MEMBER_ID,
				member(MEMBER_ID, memberStatus),
				SECOND_MEMBER_ID,
				member(SECOND_MEMBER_ID, FlowMemberStatus.COMPLETED)
			),
			Map.of(MEMBER_ID, pending(MEMBER_ID)),
			Set.of(),
			List.of(previous)
		);
		Result result = FlowRosterAuthority.cancelFlow(
			state,
			HOST_ID,
			INSTANCE_ID,
			INSTANCE_REVISION,
			"host canceled",
			NOW
		);
		check(result.successful(), instanceStatus + " flow must be cancelable");
		WorldStateV2 canceled = result.nextState().orElseThrow();
		ForcedFlowRuntime runtime = canceled.activeForcedFlow().orElseThrow().runtime();
		check(runtime.status() == FlowInstanceStatus.CANCELED, "explicit cancellation status");
		check(runtime.members().get(MEMBER_ID).status() == FlowMemberStatus.REMOVED, "cancel removal");
		check(
			runtime.members().get(SECOND_MEMBER_ID).status() == FlowMemberStatus.COMPLETED,
			"cancel changed completed member"
		);
		check(canceled.completionHistory().equals(List.of(previous)), "cancel changed history");
		check(
			canceled.players().get(MEMBER_ID).pendingRoleChange().orElseThrow().status()
				== PendingRoleChangeStatus.CANCELED,
			"cancel retained pending role"
		);
		check(
			canceled.players().values().stream()
				.allMatch(player -> player.activeFlowParticipation().isEmpty()),
			"cancel retained participation"
		);
		check(archived(runtime.executionSnapshot()), "cancel kept full snapshot");
		check(hasAudit(canceled, AuditEventType.FLOW_CANCELED), "cancel audit");
		check(!result.becameCompleted(), "cancel falsely completed");
	}

	private static void checkImmediateRoleAssignment() {
		DefinitionSnapshot definitions = definitions();
		CompletionRecord previous = completion(MEMBER_ID, 80L);
		WorldStateV2 idle = stateWithoutFlow(Set.of(MEMBER_ID, SECOND_MEMBER_ID), List.of(previous));
		Result result = FlowRosterAuthority.assignRoleImmediate(
			idle,
			definitions,
			HOST_ID,
			STATE_REVISION,
			HUNTER_ROLE_ID,
			List.of(MEMBER_ID, SECOND_MEMBER_ID),
			NOW
		);
		check(result.successful(), "immediate role assignment must succeed: " + result.message());
		WorldStateV2 assigned = result.nextState().orElseThrow();
		check(
			assigned.players().get(MEMBER_ID).roleId().equals(HUNTER_ROLE_ID)
				&& assigned.players().get(SECOND_MEMBER_ID).roleId().equals(HUNTER_ROLE_ID),
			"immediate roles"
		);
		check(assigned.activeForcedFlow().isEmpty(), "immediate role created a flow");
		check(assigned.completionHistory().equals(List.of(previous)), "immediate role changed history");
		check(assigned.stateRevision() == STATE_REVISION + 1L, "immediate role revision");
		check(
			assigned.audit().recentEvents().stream()
				.filter(event -> event.type() == AuditEventType.ROLE_CHANGE_APPLIED)
				.count() == 2L,
			"immediate role audit count"
		);

		WorldStateV2 active = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(),
			List.of()
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.assignRoleImmediate(
				active,
				definitions,
				HOST_ID,
				STATE_REVISION,
				HUNTER_ROLE_ID,
				List.of(MEMBER_ID),
				NOW
			),
			OperationCode.ACTIVE_FLOW_EXISTS,
			"immediate role during active flow"
		);

		WorldStateV2 blocked = flowState(
			FlowInstanceStatus.BLOCKED,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.BLOCKED)),
			Map.of(),
			Set.of(),
			List.of()
		);
		assertFailurePreserves(
			blocked,
			() -> FlowRosterAuthority.assignRoleImmediate(
				blocked,
				definitions,
				HOST_ID,
				STATE_REVISION,
				HUNTER_ROLE_ID,
				List.of(MEMBER_ID),
				NOW
			),
			OperationCode.ACTIVE_FLOW_EXISTS,
			"immediate role during blocked flow"
		);
		assertFailurePreserves(
			idle,
			() -> FlowRosterAuthority.assignRoleImmediate(
				idle,
				definitions,
				HOST_ID,
				STATE_REVISION,
				FOREIGN_ROLE_ID,
				List.of(MEMBER_ID),
				NOW
			),
			OperationCode.TARGET_INVALID,
			"foreign-game role"
		);
		assertFailurePreserves(
			idle,
			() -> FlowRosterAuthority.assignRoleImmediate(
				idle,
				definitions,
				HOST_ID,
				STATE_REVISION,
				HUNTER_ROLE_ID,
				List.of(MEMBER_ID, MEMBER_ID),
				NOW
			),
			OperationCode.TARGET_INVALID,
			"duplicate immediate targets"
		);
		assertFailurePreserves(
			idle,
			() -> FlowRosterAuthority.assignRoleImmediate(
				idle,
				definitions,
				HOST_ID,
				STATE_REVISION - 1L,
				HUNTER_ROLE_ID,
				List.of(MEMBER_ID),
				NOW
			),
			OperationCode.STATE_REVISION_STALE,
			"stale immediate revision"
		);
		assertFailurePreserves(
			idle,
			() -> FlowRosterAuthority.assignRoleImmediate(
				idle,
				definitions,
				uuid(999),
				STATE_REVISION,
				HUNTER_ROLE_ID,
				List.of(MEMBER_ID),
				NOW
			),
			OperationCode.NOT_HOST,
			"non-host immediate role"
		);
	}

	private static void checkWriteFences() {
		WorldStateV2 active = flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			Map.of(MEMBER_ID, member(MEMBER_ID, FlowMemberStatus.IN_PROGRESS)),
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of()
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.addMembers(
				active,
				uuid(999),
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "New Runner", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.NOT_HOST,
			"non-host roster edit"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.addMembers(
				active,
				HOST_ID,
				OTHER_INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(new OnlineMember(NEW_MEMBER_ID, "New Runner", true)),
				() -> NEW_PAGE_ID,
				NOW
			),
			OperationCode.FLOW_INSTANCE_MISMATCH,
			"wrong flow instance"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.removeMembers(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION - 1L,
				List.of(MEMBER_ID),
				"stale",
				NOW
			),
			OperationCode.STATE_REVISION_STALE,
			"stale flow revision"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.cancelFlow(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				"old clock",
				UPDATED_AT - 1L
			),
			OperationCode.INTERNAL_ERROR,
			"backdated operation"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.removeMembers(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				List.of(MEMBER_ID, MEMBER_ID),
				"duplicate",
				NOW
			),
			OperationCode.TARGET_INVALID,
			"duplicate removal targets"
		);
		assertFailurePreserves(
			active,
			() -> FlowRosterAuthority.cancelFlow(
				active,
				HOST_ID,
				INSTANCE_ID,
				INSTANCE_REVISION,
				" ",
				NOW
			),
			OperationCode.TARGET_INVALID,
			"blank cancellation reason"
		);
	}

	private static WorldStateV2 flowState(
		final FlowInstanceStatus status,
		final CompletionPolicy policy,
		final Map<UUID, FlowMemberState> members,
		final Map<UUID, PendingRoleChange> pendingChanges,
		final Set<UUID> extraPlayers,
		final List<CompletionRecord> completionHistory
	) {
		return flowState(
			status,
			policy,
			members,
			pendingChanges,
			extraPlayers,
			completionHistory,
			false
		);
	}

	private static WorldStateV2 flowState(
		final FlowInstanceStatus status,
		final CompletionPolicy policy,
		final Map<UUID, FlowMemberState> members,
		final Map<UUID, PendingRoleChange> pendingChanges,
		final Set<UUID> extraPlayers,
		final List<CompletionRecord> completionHistory,
		final boolean roleOperation
	) {
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
		boolean terminal = status == FlowInstanceStatus.COMPLETED
			|| status == FlowInstanceStatus.CANCELED;
		for (FlowMemberState member : members.values()) {
			Optional<PlayerFlowParticipation> participation =
				terminal || member.status() == FlowMemberStatus.REMOVED
					? Optional.empty()
					: Optional.of(new PlayerFlowParticipation(INSTANCE_ID, member.status()));
			players.put(
				member.playerId(),
				player(
					member.playerId(),
					Optional.ofNullable(pendingChanges.get(member.playerId())),
					participation,
					isComplete(member.status())
				)
			);
		}
		for (UUID playerId : extraPlayers) {
			players.putIfAbsent(
				playerId,
				player(playerId, Optional.empty(), Optional.empty(), false)
			);
		}
		int total = (int) members.values().stream()
			.filter(member -> member.status() != FlowMemberStatus.REMOVED)
			.count();
		int completed = (int) members.values().stream()
			.filter(member -> member.status() != FlowMemberStatus.REMOVED)
			.filter(member -> isComplete(member.status()))
			.count();
		ExecutionSnapshot snapshot = snapshot(policy, roleOperation);
		if (terminal) {
			snapshot = snapshot.compactedForTerminalState();
		}
		ForcedFlowInstance instance = new ForcedFlowInstance(
			new ForcedFlowIdentity(
				INSTANCE_ID,
				GAME_ID,
				FLOW_ID,
				1,
				ACTION_ID,
				HOST_ID,
				10L,
				PHASE_ID,
				1L,
				policy
			),
			new ForcedFlowRuntime(
				status,
				members,
				completed,
				total,
				INSTANCE_REVISION,
				snapshot,
				CallbackState.initial(),
				Optional.empty(),
				UPDATED_AT
			)
		);
		WorldStateV2 state = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			STATE_REVISION,
			Optional.of(GAME_ID),
			Optional.of(PHASE_ID),
			Optional.of(host()),
			players,
			Optional.of(instance),
			completionHistory,
			AuditLog.empty(),
			Optional.empty()
		);
		check(state.validated().result().isPresent(), "flow fixture must be valid");
		return state;
	}

	private static WorldStateV2 fullRosterState() {
		Map<UUID, FlowMemberState> members = new LinkedHashMap<>();
		for (int index = 0; index < WorldStateV2.MAX_INSTANCE_MEMBERS; index++) {
			UUID playerId = uuid(1_000L + index);
			members.put(
				playerId,
				member(
					playerId,
					index == WorldStateV2.MAX_INSTANCE_MEMBERS - 1
						? FlowMemberStatus.REMOVED
						: FlowMemberStatus.IN_PROGRESS
				)
			);
		}
		return flowState(
			FlowInstanceStatus.ACTIVE,
			CompletionPolicy.ALWAYS,
			members,
			Map.of(),
			Set.of(NEW_MEMBER_ID),
			List.of()
		);
	}

	private static WorldStateV2 stateWithoutFlow(
		final Set<UUID> playerIds,
		final List<CompletionRecord> completionHistory
	) {
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
		for (UUID playerId : playerIds) {
			players.put(playerId, player(playerId, Optional.empty(), Optional.empty(), false));
		}
		WorldStateV2 state = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			STATE_REVISION,
			Optional.of(GAME_ID),
			Optional.of(PHASE_ID),
			Optional.of(host()),
			players,
			Optional.empty(),
			completionHistory,
			AuditLog.empty(),
			Optional.empty()
		);
		check(state.validated().result().isPresent(), "idle fixture must be valid");
		return state;
	}

	private static WorldStateV2 withHost(
		final WorldStateV2 state,
		final UUID hostId
	) {
		WorldStateV2 next = state.withHost(
			Optional.of(
				new HostRecord(
					hostId,
					Optional.of("Transferred Host"),
					Optional.of(20L),
					Optional.of(HOST_ID),
					Optional.of(HostOperation.TRANSFER)
				)
			)
		);
		check(next.validated().result().isPresent(), "transferred-host fixture must be valid");
		return next;
	}

	private static WorldStateV2 withSnapshot(
		final WorldStateV2 state,
		final ExecutionSnapshot snapshot
	) {
		ForcedFlowInstance instance = state.activeForcedFlow().orElseThrow();
		ForcedFlowRuntime runtime = instance.runtime();
		WorldStateV2 next = state.withActiveForcedFlow(
			Optional.of(
				new ForcedFlowInstance(
					instance.identity(),
					new ForcedFlowRuntime(
						runtime.status(),
						runtime.members(),
						runtime.completedCount(),
						runtime.totalCount(),
						runtime.instanceRevision(),
						snapshot,
						runtime.callbacks(),
						runtime.blockDiagnostic(),
						runtime.updatedAtEpochMillis()
					)
				)
			)
		);
		check(next.validated().result().isPresent(), "custom-snapshot fixture must be valid");
		return next;
	}

	private static WorldStateV2 withCallbackFailure(final WorldStateV2 state) {
		ForcedFlowInstance instance = state.activeForcedFlow().orElseThrow();
		ForcedFlowRuntime runtime = instance.runtime();
		CallbackFailure failure = new CallbackFailure(
			CallbackKind.ON_START,
			CALLBACK_FUNCTION_ID,
			Optional.empty(),
			"expected callback failure",
			UPDATED_AT,
			1
		);
		WorldStateV2 next = state.withActiveForcedFlow(
			Optional.of(
				new ForcedFlowInstance(
					instance.identity(),
					new ForcedFlowRuntime(
						FlowInstanceStatus.BLOCKED,
						runtime.members(),
						runtime.completedCount(),
						runtime.totalCount(),
						runtime.instanceRevision(),
						runtime.executionSnapshot(),
						new CallbackState(true, Set.of(), false, List.of(failure)),
						Optional.of(
							new BlockDiagnostic(
								OperationCode.CALLBACK_FAILED.serializedName(),
								failure.error(),
								UPDATED_AT,
								Optional.empty(),
								Optional.of(CALLBACK_FUNCTION_ID)
							)
						),
						runtime.updatedAtEpochMillis()
					)
				)
			)
		);
		check(next.validated().result().isPresent(), "callback-blocked fixture must be valid");
		return next;
	}

	private static WorldStateV2 withIdentity(
		final WorldStateV2 state,
		final UUID playerId,
		final Identifier roleId,
		final Identifier lifeStateId
	) {
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
		PlayerRecord player = players.get(playerId);
		players.put(
			playerId,
			new PlayerRecord(
				player.playerId(),
				player.lastKnownName(),
				roleId,
				player.teamId(),
				lifeStateId,
				player.persistentFields(),
				player.pendingRoleChange(),
				player.activeFlowParticipation(),
				player.completionSummary(),
				player.firstSeenAtEpochMillis(),
				player.lastSeenAtEpochMillis()
			)
		);
		WorldStateV2 next = state.withPlayers(players);
		check(next.validated().result().isPresent(), "identity fixture must be valid");
		return next;
	}

	private static HostRecord host() {
		return new HostRecord(
			HOST_ID,
			Optional.of("Host"),
			Optional.of(10L),
			Optional.of(HOST_ID),
			Optional.of(HostOperation.CLAIM)
		);
	}

	private static PlayerRecord player(
		final UUID playerId,
		final Optional<PendingRoleChange> pendingChange,
		final Optional<PlayerFlowParticipation> participation,
		final boolean completed
	) {
		return new PlayerRecord(
			playerId,
			"Player" + playerId.getLeastSignificantBits(),
			RUNNER_ROLE_ID,
			Optional.empty(),
			LIFE_STATE_ID,
			Map.of(),
			pendingChange,
			participation,
			completed
				? new CompletionSummary(1L, Optional.of(80L))
				: CompletionSummary.empty(),
			0L,
			UPDATED_AT
		);
	}

	private static PendingRoleChange pending(final UUID playerId) {
		return new PendingRoleChange(
			playerId,
			HUNTER_ROLE_ID,
			ACTION_ID,
			INSTANCE_ID,
			HOST_ID,
			50L,
			RUNNER_ROLE_ID,
			STATE_REVISION - 1L,
			PendingRoleChangeStatus.PENDING
		);
	}

	private static FlowMemberState member(
		final UUID playerId,
		final FlowMemberStatus status
	) {
		boolean completed = isComplete(status);
		boolean removed = status == FlowMemberStatus.REMOVED;
		boolean unfinished = !completed && !removed;
		return new FlowMemberState(
			playerId,
			"Player" + playerId.getLeastSignificantBits(),
			unfinished ? Optional.of("entry") : Optional.empty(),
			status,
			0L,
			Map.of(),
			unfinished
				? Optional.of(uuid(10_000L + playerId.getLeastSignificantBits()))
				: Optional.empty(),
			RequestSequenceWindow.empty(),
			Optional.empty(),
			status == FlowMemberStatus.OFFLINE,
			Optional.of(UPDATED_AT),
			completed ? Optional.of(80L) : Optional.empty(),
			removed
				? Optional.of(new RemovalRecord(HOST_ID, 90L, "fixture removal"))
				: Optional.empty()
		);
	}

	private static CompletionRecord completion(final UUID playerId, final long completedAt) {
		return completion(playerId, FLOW_ID, 1, completedAt);
	}

	private static CompletionRecord completion(
		final UUID playerId,
		final Identifier flowId,
		final int flowVersion,
		final long completedAt
	) {
		return new CompletionRecord(
			playerId,
			flowId,
			flowVersion,
			OTHER_INSTANCE_ID,
			completedAt,
			ACTION_ID,
			false,
			1L
		);
	}

	private static ExecutionSnapshot snapshot(
		final CompletionPolicy policy,
		final boolean roleOperation
	) {
		return snapshot(policy, roleOperation, "\"roles\":[\"test:runner\"]");
	}

	private static ExecutionSnapshot snapshot(
		final CompletionPolicy policy,
		final boolean roleOperation,
		final String targetFilter
	) {
		String policyName = switch (policy) {
			case IF_INCOMPLETE -> "if_incomplete";
			case ALWAYS -> "always";
			case RESUME_ONLY -> "resume_only";
		};
		String operation = roleOperation
			? """
				{"type":"assign_role","role":"test:hunter","flow":"test:flow",
				"apply":"after_flow","completion_policy":"%s"}
				""".formatted(policyName)
			: """
				{"type":"start_flow","flow":"test:flow","completion_policy":"%s"}
				""".formatted(policyName);
		List<Source> sources = List.of(
			definitionSource(
				DefinitionType.GAME,
				GAME_ID,
				"""
				{"format_version":1,"api_version":1,"content_version":1,
				"name":{"text":"Test"},"initial_phase":"test:setup",
				"default_role":"test:runner","default_life_state":"test:alive"}
				"""
			),
			definitionSource(
				DefinitionType.ROLE,
				RUNNER_ROLE_ID,
				"""
				{"format_version":1,"game":"test:game","name":{"text":"Runner"},
				"tags":["test:participant"],"tab":{}}
				"""
			),
			definitionSource(
				DefinitionType.ROLE,
				HUNTER_ROLE_ID,
				"""
				{"format_version":1,"game":"test:game","name":{"text":"Hunter"},
				"tags":["test:participant"],"tab":{}}
				"""
			),
			definitionSource(
				DefinitionType.LIFE_STATE,
				LIFE_STATE_ID,
				"""
				{"format_version":1,"game":"test:game","name":{"text":"Alive"},
				"tags":["test:living"]}
				"""
			),
			definitionSource(
				DefinitionType.LIFE_STATE,
				DEAD_LIFE_STATE_ID,
				"""
				{"format_version":1,"game":"test:game","name":{"text":"Dead"},"tags":[]}
				"""
			),
			definitionSource(
				DefinitionType.PHASE,
				PHASE_ID,
				"""
				{"format_version":1,"game":"test:game","name":{"text":"Setup"},
				"transitions":[]}
				"""
			),
			definitionSource(
				DefinitionType.FLOW,
				FLOW_ID,
				"""
				{"format_version":1,"game":"test:game","version":1,
				"name":{"text":"Roster self-check"},"entry":"entry",
				"audience":{"life_states":["test:alive"],
				"role_tags":["test:participant"],"exclude_host":true,"online_only":true},
				"required":true,
				"host_bossbar":{"name":{"text":"{event}: {completed}/{total}"},
				"color":"red","style":"progress","priority":1},
				"nodes":[{"id":"entry","type":"complete"}]}
				"""
			),
			definitionSource(
				DefinitionType.PANEL_ACTION,
				ACTION_ID,
				"""
				{"format_version":1,"game":"test:game","surface":"host",
				"section":"primary","order":1,"label":{"text":"Start"},
				"description":{"text":"Roster self-check"},"phases":["test:setup"],
				"target":{"mode":"multiple","min":1,"max":64,
				"filter":{%s}},
				"confirmation":{"title":{"text":"Confirm"},
				"consequences":[{"text":"Runs the self-check flow."}]},
				"operation":%s}
				""".formatted(targetFilter, operation)
			)
		);
		DefinitionCompiler.Compilation compilation = DefinitionCompiler.compile(
			sources,
			Set.of(),
			Set.of()
		);
		check(
			compilation.valid(),
			"snapshot definitions: "
				+ (compilation.problems().isEmpty()
					? "unknown compiler failure"
					: compilation.problems().getFirst().summary())
		);
		DefinitionSnapshot definitions = compilation.snapshot().orElseThrow().withGeneration(1L);
		var frozen = ExecutionSnapshotCompiler.freeze(
			definitions,
			definitions.flows().get(FLOW_ID),
			definitions.panelActions().get(ACTION_ID)
		);
		check(frozen.success(), "snapshot freeze: " + frozen.message());
		return frozen.frozen().orElseThrow();
	}

	private static Source definitionSource(
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
			"roster-self-check",
			json
		);
	}

	private static DefinitionSnapshot definitions() {
		DefinitionSnapshot empty = DefinitionSnapshot.empty();
		Map<Identifier, RoleDefinition> roles = Map.of(
			HUNTER_ROLE_ID,
			role(HUNTER_ROLE_ID, GAME_ID, "Hunter"),
			FOREIGN_ROLE_ID,
			role(FOREIGN_ROLE_ID, OTHER_GAME_ID, "Foreign")
		);
		return new DefinitionSnapshot(
			1L,
			empty.games(),
			roles,
			empty.teams(),
			empty.lifeStates(),
			empty.phases(),
			empty.fields(),
			empty.flows(),
			empty.panelActions(),
			empty.pages(),
			empty.themes(),
			empty.sourceDocuments(),
			empty.functions(),
			empty.predicates(),
			empty.predicateDocuments()
		);
	}

	private static DefinitionSnapshot exclusiveDefinitions() {
		Identifier fieldId = id("test:spawn");
		RichText name = new RichText("{\"text\":\"Spawn\"}", "Spawn");
		ExclusiveChoiceOption option = new ExclusiveChoiceOption(
			"only",
			new RichText("{\"text\":\"Only\"}", "Only"),
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
		FieldDefinition field = new FieldDefinition(
			fieldId,
			GAME_ID,
			1,
			name,
			Optional.empty(),
			FieldScope.PLAYER,
			FieldType.EXCLUSIVE_CHOICE,
			true,
			Optional.empty(),
			EditPolicy.PLAYER,
			false,
			Set.of(RUNNER_ROLE_ID),
			Set.of(PHASE_ID),
			Optional.empty(),
			FieldMigrationStrategy.PRESERVE,
			new FieldConstraints(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				List.of("only"),
				Optional.empty(),
				Optional.empty()
			),
			Optional.of(
				new ExclusiveChoiceDefinition(
					ReservationScope.GAME_INSTANCE,
					true,
					false,
					List.of(option)
				)
			)
		);
		FlowDefinition flow = new FlowDefinition(
			FLOW_ID,
			GAME_ID,
			1,
			name,
			"entry",
			Audience.everyoneExceptHost(),
			true,
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Map.of(
				"entry",
				new ChoiceNode(
					"entry",
					id("test:choose"),
					fieldId,
					List.of(new ChoiceRoute("only", "done"))
				),
				"done",
				new CompleteNode("done", NodeScope.PLAYER)
			)
		);
		DefinitionSnapshot empty = DefinitionSnapshot.empty();
		return new DefinitionSnapshot(
			1L,
			empty.games(),
			empty.roles(),
			empty.teams(),
			empty.lifeStates(),
			empty.phases(),
			Map.of(fieldId, field),
			Map.of(FLOW_ID, flow),
			empty.panelActions(),
			empty.pages(),
			empty.themes(),
			empty.sourceDocuments(),
			empty.functions(),
			empty.predicates(),
			empty.predicateDocuments()
		);
	}

	private static RoleDefinition role(
		final Identifier roleId,
		final Identifier gameId,
		final String name
	) {
		return new RoleDefinition(
			roleId,
			gameId,
			new RichText("{\"text\":\"" + name + "\"}", name),
			Optional.empty(),
			Set.of(),
			new TabStyle(Optional.empty(), Optional.empty())
		);
	}

	private static void assertFailurePreserves(
		final WorldStateV2 state,
		final Supplier<Result> operation,
		final OperationCode expected,
		final String label
	) {
		JsonElement before = encode(state);
		Result result = operation.get();
		check(result.code() == expected, label + " returned " + result.code());
		check(result.nextState().isEmpty(), label + " produced a candidate state");
		check(!result.becameCompleted(), label + " reported completion");
		check(encode(state).equals(before), label + " mutated its input");
	}

	private static boolean hasAudit(
		final WorldStateV2 state,
		final AuditEventType type
	) {
		return state.audit().recentEvents().stream().anyMatch(event -> event.type() == type);
	}

	private static boolean archived(final ExecutionSnapshot snapshot) {
		return snapshot.flow().normalizedJson().equals("{\"archived\":true}");
	}

	private static boolean isComplete(final FlowMemberStatus status) {
		return status == FlowMemberStatus.COMPLETED
			|| status == FlowMemberStatus.ALREADY_COMPLETE;
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
