package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookEvent;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract;
import io.github.zcpu954861.pixeltzzpro.server.TimelineApprovalAuthority.FrozenActivationContext;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDefinitionType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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
 * End-to-end pure checks for opening, completing, invalidating, restoring, and reconciling the
 * dedicated readiness transaction against the shipped data-driven page.
 */
public final class ReadinessAuthoritySelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier READY = id("ready");
	private static final Identifier RUNNER = id("runner");
	private static final Identifier HUNTER = id("hunter");
	private static final Identifier SPECTATOR = id("spectator");
	private static final Identifier ALIVE = id("alive");
	private static final UUID HOST = uuid(1);
	private static final UUID PLAYER = uuid(2);
	private static final UUID HUNTER_PLAYER = uuid(3);
	private static final UUID GAME_INSTANCE = uuid(4);
	private static final UUID READINESS_INSTANCE = uuid(5);

	private ReadinessAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = exampleDefinitions();
		checkEmptyAudienceCompletionEdge(definitions);
		WorldStateV3 initial = state();
		var opened = ReadinessAuthority.open(
			initial,
			definitions,
			HOST,
			Set.of(HOST, PLAYER, HUNTER_PLAYER),
			READINESS_INSTANCE,
			new Uuids(100)::next,
			100L
		);
		check(
			opened.successful(),
			"readiness must open from the registered ready phase: "
				+ opened.code()
				+ " "
				+ opened.message()
		);
		WorldStateV3 ready = opened.nextState().orElseThrow();
		JsonElement encoded = WorldStateV3.CODEC.encodeStart(JsonOps.INSTANCE, ready)
			.result()
			.orElseThrow();
		ready = WorldStateV3.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
		check(ready.readiness().isPresent(), "readiness must survive schema-v3 persistence");
		var runtime = ready.readiness().orElseThrow().flow().runtime();
		check(runtime.totalCount() == 2, "all non-host ready_participant members must enter the denominator");
		check(runtime.members().containsKey(PLAYER), "runner must enter readiness");
		check(runtime.members().containsKey(HUNTER_PLAYER), "hunter must enter shared readiness");
		checkFrozenReadinessHookEnvelope(ready, definitions);
		check(
			!TimelineApprovalAuthority.approve(
				ready,
				definitions,
				approvalContext(ready)
			).successful(),
			"timeline approval must remain unavailable while one readiness member is waiting"
		);

		WorldStateV3 runnerReady = submit(ready, PLAYER, 0L, 110L);
		check(!ReadinessAuthority.complete(runnerReady), "one waiting hunter must keep readiness active");
		checkReconcileLastIncompleteCompletionEdge(runnerReady);
		WorldStateV3 completed = submit(runnerReady, HUNTER_PLAYER, 0L, 111L);
		check(ReadinessAuthority.complete(completed), "runner and hunter confirmations must complete readiness");
		WorldStateV3 approvalState = completed.withCore(
			completed.core().withPlayers(Map.of(PLAYER, completed.core().players().get(PLAYER)))
		);
		var approval = TimelineApprovalAuthority.approve(
			approvalState,
			definitions,
			approvalContext(approvalState)
		);
		check(
			approval.successful(),
			"all-ready state must unlock the host's final timeline approval: "
				+ approval.code()
				+ " "
				+ approval.message()
		);
		check(
			completed.readiness().orElseThrow().flow().runtime().executionSnapshot().pages().size() == 1,
			"completed readiness must retain its frozen page for later invalidation"
		);

		var disconnected = ReadinessAuthority.setOnline(
			completed,
			PLAYER,
			"PlayerB",
			false,
			uuid(200),
			120L
		);
		check(disconnected.successful() && disconnected.invalidated(), "disconnect must invalidate ready");
		WorldStateV3 offline = disconnected.state();
		check(!ReadinessAuthority.complete(offline), "invalidated readiness must block approval");
		check(
			offline.readiness().orElseThrow().flow().runtime().members().get(PLAYER).status()
				== FlowMemberStatus.OFFLINE,
			"disconnected readiness member must be classified offline"
		);
		var approvedTimeline = approval.nextState().orElseThrow().timeline().orElseThrow();
		var frozenTimeline = TimelineSnapshotCompiler.restore(GAME, approvedTimeline.snapshot());
		check(frozenTimeline.success(), "approved timeline snapshot must restore for delayed launch");
		var delayedLaunch = TimelineApprovalAuthority.activateFrozen(
			offline,
			frozenTimeline.snapshot().orElseThrow(),
			approvedTimeline.snapshot(),
			new FrozenActivationContext(
				GAME_INSTANCE,
				READY,
				approvedTimeline.instanceId(),
				approvedTimeline.currentTask().orElseThrow().taskInstanceId(),
				121L,
				approval.frozenParticipants()
			)
		);
		check(
			delayedLaunch.successful(),
			"an approved countdown must launch after a continue-policy disconnect without rechecking readiness: "
				+ delayedLaunch.code()
				+ " "
				+ delayedLaunch.message()
		);
		check(
			delayedLaunch.nextState().orElseThrow().timeline().orElseThrow()
				.currentTask().orElseThrow().participants().equals(approval.frozenParticipants()),
			"delayed launch must use the exact participant list accepted before the countdown"
		);

		var reconnected = ReadinessAuthority.setOnline(
			offline,
			PLAYER,
			"PlayerB",
			true,
			uuid(201),
			130L
		);
		check(reconnected.changed(), "reconnect must restore the forced readiness page");
		WorldStateV3 completedAgain = submit(reconnected.state(), PLAYER, 0L, 140L);
		check(ReadinessAuthority.complete(completedAgain), "reconfirmed member must become ready again");

		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(completedAgain.core().players());
		players.put(PLAYER, copyRole(players.get(PLAYER), SPECTATOR));
		WorldStateV3 spectator = completedAgain.withCore(completedAgain.core().withPlayers(players));
		var reconciled = ReadinessAuthority.reconcilePlayer(
			spectator,
			PLAYER,
			true,
			uuid(202),
			150L,
			"became spectator"
		);
		check(reconciled.successful() && reconciled.changed(), "identity change must reconcile readiness");
		runtime = reconciled.state().readiness().orElseThrow().flow().runtime();
		check(
			runtime.totalCount() == 1
				&& runtime.completedCount() == 1
				&& runtime.status() == FlowInstanceStatus.COMPLETED,
			"spectator must be removed while the completed hunter remains in the denominator"
		);
		System.out.println("READINESS_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkEmptyAudienceCompletionEdge(
		final DefinitionSnapshot definitions
	) {
		WorldStateV3 base = state();
		Map<UUID, PlayerRecord> spectators = new LinkedHashMap<>();
		base.core().players().forEach((id, player) ->
			spectators.put(id, copyRole(player, SPECTATOR))
		);
		WorldStateV3 initial = base.withCore(base.core().withPlayers(spectators));
		var opened = ReadinessAuthority.open(
			initial,
			definitions,
			HOST,
			Set.of(HOST, PLAYER, HUNTER_PLAYER),
			uuid(6),
			new Uuids(600)::next,
			90L
		);
		check(opened.successful(), "zero-member readiness must still open successfully");
		WorldStateV3 completed = opened.nextState().orElseThrow();
		check(
			ReadinessAuthority.complete(completed),
			"zero-member readiness must complete in its opening transaction"
		);
		check(
			MessageHookDispatchContract.readinessCompletionEvent(
				ReadinessAuthority.complete(initial),
				ReadinessAuthority.complete(completed)
			).isPresent(),
			"zero-member opening must expose one readiness COMPLETE rising edge"
		);
	}

	private static void checkReconcileLastIncompleteCompletionEdge(
		final WorldStateV3 runnerReady
	) {
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(runnerReady.core().players());
		players.put(HUNTER_PLAYER, copyRole(players.get(HUNTER_PLAYER), SPECTATOR));
		WorldStateV3 spectator = runnerReady.withCore(runnerReady.core().withPlayers(players));
		var reconciled = ReadinessAuthority.reconcilePlayer(
			spectator,
			HUNTER_PLAYER,
			true,
			uuid(601),
			109L,
			"last incomplete member became spectator"
		);
		check(
			reconciled.successful() && reconciled.changed(),
			"removing the last incomplete member must reconcile readiness"
		);
		WorldStateV3 completed = reconciled.state();
		check(
			ReadinessAuthority.complete(completed),
			"removing the last incomplete member must complete the remaining denominator"
		);
		check(
			MessageHookDispatchContract.readinessCompletionEvent(
				ReadinessAuthority.complete(runnerReady),
				ReadinessAuthority.complete(completed)
			).isPresent(),
			"last-member reconciliation must expose one readiness COMPLETE rising edge"
		);
	}

	private static void checkFrozenReadinessHookEnvelope(
		final WorldStateV3 ready,
		final DefinitionSnapshot definitions
	) {
		var readiness = ready.readiness().orElseThrow();
		var frozen = readiness.flow().runtime().executionSnapshot();
		JsonObject gameDocument = frozenGameDocument(frozen);
		JsonObject hookEnvelope = gameDocument.getAsJsonObject("readiness");
		check(hookEnvelope != null, "readiness flow snapshot must retain its readiness definition");
		check(
			hookEnvelope.get("action").getAsString()
				.equals(readiness.flow().identity().sourceActionId().toString()),
			"frozen readiness definition must belong to the snapshot's source action"
		);
		check(
			hookEnvelope.getAsJsonObject("message_hooks").has("complete"),
			"frozen readiness envelope must retain the COMPLETE hook registration"
		);

		var restored = ExecutionSnapshotCompiler.restore(
			readiness.flow().identity().flowId(),
			frozen
		);
		check(restored.success(), "readiness hook snapshot must restore: " + restored.message());
		var completeHook = restored.snapshot().orElseThrow().games().get(GAME)
			.readiness().orElseThrow()
			.messageHooks().get(MessageHookEvent.COMPLETE)
			.orElseThrow(() -> new AssertionError(
				"restored readiness definition must expose the frozen COMPLETE hook"
			));
		check(
			completeHook.cue().equals(id("lifecycle/progress_notice"))
				&& "『玩家准备』全员就绪".equals(completeHook.arguments().get("message")),
			"restored COMPLETE hook must preserve its cue and fixed data-pack arguments"
		);
		var declared = definitions.games().get(GAME).readiness().orElseThrow();
		check(
			readiness.disconnectInvalidates() == declared.disconnectInvalidates()
				&& readiness.hostCanForce() == declared.hostCanForce(),
			"runtime readiness controls must remain owned by the persisted readiness instance"
		);

		var ordinary = ExecutionSnapshotCompiler.freeze(
			definitions,
			definitions.flows().get(id("general_tutorial")),
			definitions.panelActions().get(id("start_general_initialization"))
		);
		check(
			ordinary.success(),
			"ordinary forced flow must still freeze without readiness ownership: "
				+ ordinary.code()
				+ " "
				+ ordinary.message()
		);
		check(
			!frozenGameDocument(ordinary.frozen().orElseThrow()).has("readiness"),
			"ordinary forced-flow snapshots must not inherit readiness lifecycle metadata"
		);
	}

	private static JsonObject frozenGameDocument(
		final WorldStateV2.ExecutionSnapshot snapshot
	) {
		return snapshot.supportingDefinitions().stream()
			.filter(source -> source.type() == FrozenDefinitionType.GAME)
			.filter(source -> source.id().equals(GAME))
			.findFirst()
			.map(source -> JsonParser.parseString(source.document().normalizedJson()).getAsJsonObject())
			.orElseThrow(() -> new AssertionError("execution snapshot lost its frozen game document"));
	}

	private static WorldStateV3 submit(
		final WorldStateV3 state,
		final UUID playerId,
		final long sequence,
		final long now
	) {
		var instance = state.readiness().orElseThrow().flow();
		var member = instance.runtime().members().get(playerId);
		var restored = ExecutionSnapshotCompiler.restore(
			instance.identity().flowId(),
			instance.runtime().executionSnapshot()
		);
		check(restored.success(), "readiness snapshot must restore");
		var result = ReadinessAuthority.submit(
			state,
			restored.snapshot().orElseThrow(),
			new ForcedFlowAuthority.Submission(
				playerId,
				instance.identity().instanceId(),
				member.pageInstanceId().orElseThrow(),
				instance.identity().flowId(),
				instance.identity().flowVersion(),
				member.currentNodeId().orElseThrow(),
				instance.runtime().instanceRevision(),
				member.memberRevision(),
				sequence,
				"confirm",
				List.of()
			),
			(predicate, context) -> false,
			uuid((int)now + 1),
			now
		);
		check(result.successful(), "readiness confirmation must succeed: " + result.message());
		return result.nextState().orElseThrow();
	}

	private static WorldStateV3 state() {
		PlayerRecord runner = player(PLAYER, "PlayerB", RUNNER);
		PlayerRecord hunter = player(HUNTER_PLAYER, "PlayerC", HUNTER);
		WorldStateV2 core = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			7L,
			Optional.of(GAME),
			Optional.of(READY),
			Optional.of(HostRecord.migratedLegacy(HOST)),
			Map.of(PLAYER, runner, HUNTER_PLAYER, hunter),
			Optional.empty(),
			List.of(),
			AuditLog.empty(),
			Optional.empty()
		);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(GAME_INSTANCE),
			Optional.empty(),
			List.of(),
			List.of()
		);
	}

	private static PlayerRecord player(
		final UUID playerId,
		final String name,
		final Identifier role
	) {
		return new PlayerRecord(
			playerId,
			name,
			role,
			Optional.empty(),
			ALIVE,
			role.equals(HUNTER)
				? Map.of(
					id("hunter_spawn"),
					new PersistentFieldValue(
						1,
						StoredValue.singleChoice("north_gate")
					)
				)
				: Map.of(),
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			1L,
			10L
		);
	}

	private static TimelineApprovalAuthority.ApprovalContext approvalContext(
		final WorldStateV3 state
	) {
		return new TimelineApprovalAuthority.ApprovalContext(
			HOST,
			GAME_INSTANCE,
			state.stateRevision(),
			3L,
			uuid(300),
			uuid(301),
			200L,
			Set.of(HOST, PLAYER, HUNTER_PLAYER)
		);
	}

	private static PlayerRecord copyRole(final PlayerRecord source, final Identifier role) {
		return new PlayerRecord(
			source.playerId(),
			source.lastKnownName(),
			role,
			source.teamId(),
			source.lifeStateId(),
			source.persistentFields(),
			source.pendingRoleChange(),
			source.activeFlowParticipation(),
			source.completionSummary(),
			source.firstSeenAtEpochMillis(),
			source.lastSeenAtEpochMillis()
		);
	}

	private static DefinitionSnapshot exampleDefinitions() {
		Path root = Path.of("examples", "pixel-tzz-base-datapack", "data");
		List<Source> sources = new ArrayList<>();
		Set<Identifier> functions = new HashSet<>();
		Set<Identifier> predicates = new HashSet<>();
		try (var paths = Files.walk(root)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				Path relative = root.relativize(file);
				String namespace = relative.getName(0).toString();
				String directory = relative.getName(1).toString();
				String tail = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
				if (directory.equals("function") && tail.endsWith(".mcfunction")) {
					functions.add(Identifier.fromNamespaceAndPath(
						namespace,
						tail.substring(0, tail.length() - ".mcfunction".length())
					));
					continue;
				}
				if (directory.equals("predicate") && tail.endsWith(".json")) {
					predicates.add(Identifier.fromNamespaceAndPath(
						namespace,
						tail.substring(0, tail.length() - ".json".length())
					));
					continue;
				}
				if (!directory.equals("pixel_tzz_pro") || relative.getNameCount() < 4) {
					continue;
				}
				DefinitionType type = DefinitionType.byDirectory(relative.getName(2).toString())
					.orElseThrow();
				String fileName = relative.subpath(3, relative.getNameCount())
					.toString()
					.replace('\\', '/');
				String idPath = fileName.substring(0, fileName.length() - ".json".length());
				sources.add(new Source(
					type,
					Identifier.fromNamespaceAndPath(namespace, idPath),
					Identifier.fromNamespaceAndPath(namespace, "test/" + fileName),
					"readiness-self-check",
					Files.readString(file, StandardCharsets.UTF_8)
				));
			}
		} catch (IOException error) {
			throw new AssertionError("could not load example definitions", error);
		}
		var compilation = DefinitionCompiler.compile(sources, functions, predicates);
		check(compilation.valid(), "example definitions must compile: " + compilation.problems());
		return compilation.snapshot().orElseThrow().withGeneration(3L);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static UUID uuid(final int value) {
		return new UUID(0L, value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class Uuids {
		private int next;

		private Uuids(final int first) {
			this.next = first;
		}

		private UUID next() {
			return uuid(this.next++);
		}
	}
}
