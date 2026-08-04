package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.DatapackFixtureLoader.LoadedPacks;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistryTestAccess;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.MessageDependencySnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDefinitionType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Regression checks for the definition closure retained by frozen V3B message hooks. */
public final class MessageHookFreezeClosureSelfCheck {
	private static final Path BASE_PACK = Path.of("examples", "pixel-tzz-base-datapack");
	private static final Identifier GAME = id("main");
	private static final Identifier FLOW = id("general_tutorial");
	private static final Identifier ACTION = id("initialize_runner");
	private static final Identifier POLICY_CUE = id("acceptance/policy_matrix");
	private static final Identifier FIELD_CUE = id("acceptance/field_capture");
	private static final Identifier HISTORY_CUE = id("acceptance/history_replay");
	private static final Identifier CLOSURE_CUE = id("self_check/closure");
	private static final Identifier CURRENT_TASK_SOURCE_CUE = id("self_check/current_task_source");
	private static final Identifier DYNAMIC_TASK_SOURCE_CUE = id("self_check/dynamic_task_source");
	private static final Identifier UNUSED_CUE = id("self_check/unused");
	private static final Identifier DISABLED_CUE = id("self_check/disabled");
	private static final Identifier ACCEPTANCE_EFFECT = id("acceptance");
	private static final Identifier DECODE_EFFECT = id("decode_notice");
	private static final Identifier MESSAGE_PREDICATE = id("acceptance_3b/is_hunter");
	private static final Identifier MESSAGE_CALLBACK = id("acceptance_3b/message_complete");
	private static final Identifier PLAYER_ROLE = id("self_check/player_role");
	private static final Identifier UNRELATED_PLAYER_DATA = id("self_check/unused_data");
	private static final Identifier EXTRA_FLOW_TASK = id("self_check/extra_task");
	private static final Identifier INITIAL_TIMELINE_TASK = id("acceptance/warmup");
	private static final Identifier FIXED_FIELD_CUE = id("self_check/fixed_field");
	private static final Identifier HUNTER_SPAWN_FIELD = id("hunter_spawn");

	private MessageHookFreezeClosureSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkExecutionClosure();
		checkTimelineClosureAndV1FunctionSuperset();
		checkDisabledCueRemainsDisabled();
		checkDynamicTaskParameterClosure();
		checkFixedIdentifierArgumentClosure();
		checkDependencySnapshotClosureMatching();

		System.out.println("MESSAGE_HOOK_FREEZE_CLOSURE_SELF_CHECK=PASS");
	}

	private static void checkDependencySnapshotClosureMatching() {
		Fixture fixture = fixture();
		List<Source> sources = withClosureFixture(fixture.sources());
		DefinitionSnapshot frozenGeneration = compile(fixture, sources, 401L);
		var policyFreeze = MessageDependencySnapshotCompiler.freeze(
			frozenGeneration,
			frozenGeneration.messageCatalog().messageCues().get(POLICY_CUE)
		);
		check(
			policyFreeze.success(),
			"policy_matrix must freeze its node predicate document: " + policyFreeze.message()
		);
		var cue = frozenGeneration.messageCatalog().messageCues().get(CLOSURE_CUE);
		var frozen = MessageDependencySnapshotCompiler.freeze(frozenGeneration, cue);
		check(frozen.success(), "dependency manifest must freeze: " + frozen.message());
		FrozenDocument frozenManifest = frozen.snapshot().orElseThrow();
		JsonObject manifest = JsonParser.parseString(frozenManifest.normalizedJson()).getAsJsonObject();
		check(
			manifest.getAsJsonObject("predicate_documents")
				.get(MESSAGE_PREDICATE.toString())
				.getAsString()
				.equals(frozenGeneration.predicateDocuments().get(MESSAGE_PREDICATE)),
			"dependency manifest must persist the exact predicate document retained for the cue"
		);
		check(
			MessageDependencySnapshotCompiler.matches(
				frozenGeneration.withGeneration(9_001L),
				CLOSURE_CUE,
				frozenManifest
			).matched(),
			"cold restart must accept the frozen predicate document independent of JVM generation"
		);

		DefinitionSnapshot missingDocument = frozenGeneration.withPredicateDocuments(Map.of());
		var missingFreeze = MessageDependencySnapshotCompiler.freeze(missingDocument, cue);
		check(
			!missingFreeze.success() && missingFreeze.message().contains("no retained document"),
			"a message predicate without retained JSON must fail closed while freezing"
		);
		var missingMatch = MessageDependencySnapshotCompiler.matches(
			missingDocument,
			CLOSURE_CUE,
			frozenManifest
		);
		check(
			!missingMatch.matched() && missingMatch.message().contains("no retained document"),
			"cold restart must fail closed when the retained predicate document is unavailable"
		);

		Map<Identifier, String> changedDocuments = new LinkedHashMap<>(
			frozenGeneration.predicateDocuments()
		);
		changedDocuments.put(
			MESSAGE_PREDICATE,
			"{\"condition\":\"minecraft:random_chance\",\"chance\":0.0}"
		);
		DefinitionSnapshot reloadedGeneration = frozenGeneration
			.withGeneration(9_002L)
			.withPredicateDocuments(changedDocuments);
		check(
			!MessageDependencySnapshotCompiler.matches(
				reloadedGeneration,
				CLOSURE_CUE,
				frozenManifest
			).matched(),
			"cold restart must reject a predicate document changed by a later data-pack generation"
		);
		check(
			JsonParser.parseString(
				MessageDependencySnapshotCompiler.freeze(frozenGeneration, cue)
					.snapshot()
					.orElseThrow()
					.normalizedJson()
			).getAsJsonObject()
				.getAsJsonObject("predicate_documents")
				.get(MESSAGE_PREDICATE.toString())
				.getAsString()
				.equals(frozenGeneration.predicateDocuments().get(MESSAGE_PREDICATE)),
			"a live instance retained across reload must continue freezing its original predicate JSON"
		);

		List<Source> widenedSources = new ArrayList<>(sources);
		widenedSources.add(roleSource(id("self_check/late_participant")));
		DefinitionSnapshot widenedGeneration = compile(fixture, widenedSources, 402L);
		var widened = MessageDependencySnapshotCompiler.matches(
			widenedGeneration,
			CLOSURE_CUE,
			frozen.snapshot().orElseThrow()
		);
		check(
			!widened.matched() && widened.message().contains("closure changed"),
			"a newly matching role must invalidate a frozen role-tag dependency closure: "
				+ widened.message()
		);
	}

	private static void checkExecutionClosure() {
		Fixture fixture = fixture();
		List<Source> closureSources = withClosureFixture(fixture.sources());
		List<Source> sources = mutateSource(
			closureSources,
			DefinitionType.FLOW,
			FLOW,
			root -> setHook(root, "start", CLOSURE_CUE)
		);
		sources.add(cueSource(UNUSED_CUE, simpleCueJson("unused")));
		DefinitionSnapshot snapshot = compile(fixture, sources, 301L);
		check(
			snapshot.predicateDocuments().containsKey(MESSAGE_PREDICATE),
			"registry retention must follow hook -> cue -> node condition predicate references"
		);
		check(
			snapshot.messageCatalog().messageCues().containsKey(CLOSURE_CUE),
			"closure cue must be executable before freezing: " + snapshot.messageCatalog().disabled()
		);

		var freeze = ExecutionSnapshotCompiler.freeze(
			snapshot,
			snapshot.flows().get(FLOW),
			snapshot.panelActions().get(ACTION)
		);
		check(freeze.success(), "execution hook closure must freeze: " + diagnostic(freeze));
		var frozen = freeze.frozen().orElseThrow();
		Set<FrozenKey> support = new LinkedHashSet<>();
		frozen.supportingDefinitions().forEach(value ->
			support.add(new FrozenKey(value.type(), value.id()))
		);

		check(
			support.contains(new FrozenKey(FrozenDefinitionType.MESSAGE_CUE, CLOSURE_CUE)),
			"execution snapshot must retain its reachable cue"
		);
		check(
			support.contains(new FrozenKey(FrozenDefinitionType.TEXT_EFFECT, ACCEPTANCE_EFFECT)),
			"execution snapshot must retain a reachable text-effect preset"
		);
		check(
			support.contains(new FrozenKey(FrozenDefinitionType.PLAYER_DATA, PLAYER_ROLE)),
			"execution snapshot must retain player-data referenced by a cue parameter"
		);
		check(
			support.contains(new FrozenKey(FrozenDefinitionType.TASK, EXTRA_FLOW_TASK))
				&& support.contains(new FrozenKey(FrozenDefinitionType.TASK, INITIAL_TIMELINE_TASK)),
			"execution snapshot must retain explicit and current-task dependencies outside the forced-flow graph"
		);
		check(
			frozen.predicateReferences().contains(MESSAGE_PREDICATE),
			"execution snapshot must retain a cue predicate"
		);
		check(
			frozen.functionReferences().contains(MESSAGE_CALLBACK),
			"execution snapshot must retain a cue callback"
		);
		check(
			!support.contains(new FrozenKey(FrozenDefinitionType.MESSAGE_CUE, FIELD_CUE))
				&& !support.contains(new FrozenKey(FrozenDefinitionType.MESSAGE_CUE, POLICY_CUE))
				&& !support.contains(new FrozenKey(FrozenDefinitionType.MESSAGE_CUE, UNUSED_CUE)),
			"execution snapshot must exclude cues that no retained hook can reach"
		);
		check(
			!support.contains(new FrozenKey(FrozenDefinitionType.TEXT_EFFECT, DECODE_EFFECT)),
			"an effect used only by an unrelated cue must not enter the execution snapshot"
		);
		check(
			!support.contains(new FrozenKey(FrozenDefinitionType.PLAYER_DATA, UNRELATED_PLAYER_DATA)),
			"unrelated player-data must not enter a forced-flow snapshot"
		);

		var restored = ExecutionSnapshotCompiler.restore(FLOW, frozen);
		check(restored.success(), "execution hook closure must restore: " + restored.message());
		var catalog = restored.snapshot().orElseThrow().messageCatalog();
		check(
			catalog.messageCues().containsKey(CLOSURE_CUE)
				&& catalog.textEffects().containsKey(ACCEPTANCE_EFFECT),
			"execution restore must publish the frozen cue and effect in its own catalog"
		);
		check(
			!catalog.messageCues().containsKey(FIELD_CUE)
				&& !catalog.messageCues().containsKey(POLICY_CUE)
				&& !catalog.messageCues().containsKey(UNUSED_CUE)
				&& !catalog.textEffects().containsKey(DECODE_EFFECT),
			"execution restore must not repopulate unrelated live message definitions"
		);
	}

	private static void checkTimelineClosureAndV1FunctionSuperset() {
		Fixture fixture = fixture();
		List<Source> closureSources = withClosureFixture(fixture.sources());
		List<Source> sources = mutateSource(
			closureSources,
			DefinitionType.GAME,
			GAME,
			root -> setHook(root, "start", CLOSURE_CUE)
		);
		sources = mutateSource(
			sources,
			DefinitionType.TASK,
			id("acceptance/warmup"),
			root -> setHook(root, "start", FIELD_CUE)
		);
		sources = mutateSource(
			sources,
			DefinitionType.TASK,
			id("acceptance/main_branch"),
			root -> root.getAsJsonArray("events")
				.get(0)
				.getAsJsonObject()
				.add("message_hooks", hooks("trigger", HISTORY_CUE))
		);
		sources.add(cueSource(UNUSED_CUE, simpleCueJson("unused")));
		DefinitionSnapshot snapshot = compile(fixture, sources, 302L);

		var freeze = TimelineSnapshotCompiler.freeze(snapshot, snapshot.games().get(GAME));
		check(freeze.success(), "timeline hook closure must freeze: " + diagnostic(freeze));
		var restored = freeze.compiled().orElseThrow();
		var catalog = restored.messageCatalog();
		check(
			catalog.messageCues().containsKey(CLOSURE_CUE),
			"timeline closure must start at game hooks"
		);
		check(
			catalog.messageCues().containsKey(FIELD_CUE),
			"timeline closure must start at reachable task hooks"
		);
		check(
			catalog.messageCues().containsKey(HISTORY_CUE),
			"timeline closure must start at reachable task-event hooks"
		);
		check(
			catalog.textEffects().containsKey(ACCEPTANCE_EFFECT)
				&& catalog.textEffects().containsKey(DECODE_EFFECT),
			"timeline restore must publish all effects reached by frozen cues"
		);
		check(
			restored.playerData().containsKey(PLAYER_ROLE)
				&& restored.tasks().containsKey(EXTRA_FLOW_TASK),
			"timeline closure must retain cue player-data and task dependencies"
		);
		check(
			!catalog.messageCues().containsKey(UNUSED_CUE),
			"timeline snapshot must exclude an unreferenced cue"
		);

		JsonObject envelope = JsonParser.parseString(
			freeze.frozen().orElseThrow().normalizedJson()
		).getAsJsonObject();
		check(
			identifierSet(envelope.getAsJsonArray("functions")).equals(snapshot.functions()),
			"format-version 1 must keep its complete discovered-function envelope"
		);
		Identifier legacyExtra = id("self_check/legacy_unused_function");
		envelope.getAsJsonArray("functions").add(legacyExtra.toString());
		var legacyRestore = TimelineSnapshotCompiler.restore(
			GAME,
			FrozenDocument.of(envelope.toString())
		);
		check(
			legacyRestore.success(),
			"format-version 1 restore must accept a safe legacy function superset: "
				+ legacyRestore.message()
		);
	}

	private static void checkDisabledCueRemainsDisabled() {
		Fixture fixture = fixture();
		List<Source> sources = mutateSource(
			fixture.sources(),
			DefinitionType.FLOW,
			FLOW,
			root -> setHook(root, "start", DISABLED_CUE)
		);
		sources.add(
			cueSource(
				DISABLED_CUE,
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "nodes": "invalid"
				}
				"""
			)
		);
		sources.add(cueSource(UNUSED_CUE, simpleCueJson("unused")));
		DefinitionSnapshot snapshot = compile(fixture, sources, 303L);
		DocumentKey disabledKey = new DocumentKey(DefinitionType.MESSAGE_CUE, DISABLED_CUE);
		check(
			snapshot.messageCatalog().disabled().containsKey(disabledKey),
			"fixture must isolate the malformed optional cue before freezing"
		);

		var freeze = ExecutionSnapshotCompiler.freeze(
			snapshot,
			snapshot.flows().get(FLOW),
			snapshot.panelActions().get(ACTION)
		);
		check(freeze.success(), "a hook to an isolated optional cue must still freeze: " + diagnostic(freeze));
		var frozen = freeze.frozen().orElseThrow();
		check(
			frozen.supportingDefinitions().stream().anyMatch(value ->
				value.type() == FrozenDefinitionType.MESSAGE_CUE && value.id().equals(DISABLED_CUE)
			),
			"the disabled cue's original source must remain in the frozen closure"
		);
		check(
			frozen.supportingDefinitions().stream().noneMatch(value ->
				value.type() == FrozenDefinitionType.MESSAGE_CUE && value.id().equals(UNUSED_CUE)
			),
			"a disabled hook must not widen the closure to unrelated cues"
		);

		var restored = ExecutionSnapshotCompiler.restore(FLOW, frozen);
		check(restored.success(), "the disabled cue source must restore: " + restored.message());
		var restoredCatalog = restored.snapshot().orElseThrow().messageCatalog();
		check(
			restoredCatalog.disabled().containsKey(disabledKey)
				&& !restoredCatalog.messageCues().containsKey(DISABLED_CUE),
			"freezing must never turn an isolated cue back into an executable cue"
		);
	}

	private static void checkDynamicTaskParameterClosure() {
		checkDynamicTaskParameterClosure(
			CURRENT_TASK_SOURCE_CUE,
			"""
			{"type": "game_context", "value": "task"}
			""",
			"game-context task source"
		);
		checkDynamicTaskParameterClosure(
			DYNAMIC_TASK_SOURCE_CUE,
			"""
			{
			  "type": "storage",
			  "storage": "pixel_tzz:self_check",
			  "path": "task.id"
			}
			""",
			"dynamic task identifier source"
		);
	}

	private static void checkDynamicTaskParameterClosure(
		final Identifier cue,
		final String source,
		final String label
	) {
		Fixture fixture = fixture();
		List<Source> sources = mutateSource(
			fixture.sources(),
			DefinitionType.FLOW,
			FLOW,
			root -> setHook(root, "start", cue)
		);
		sources.add(cueSource(cue, taskParameterCueJson(source)));
		DefinitionSnapshot snapshot = compile(fixture, sources, 304L);

		var freeze = ExecutionSnapshotCompiler.freeze(
			snapshot,
			snapshot.flows().get(FLOW),
			snapshot.panelActions().get(ACTION)
		);
		check(freeze.success(), label + " closure must freeze: " + diagnostic(freeze));
		Set<FrozenKey> support = new LinkedHashSet<>();
		freeze.frozen().orElseThrow().supportingDefinitions().forEach(value ->
			support.add(new FrozenKey(value.type(), value.id()))
		);
		check(
			support.contains(new FrozenKey(FrozenDefinitionType.TASK, id("acceptance/warmup")))
				&& support.contains(
					new FrozenKey(FrozenDefinitionType.TASK, id("acceptance/main_branch"))
				)
				&& support.contains(
					new FrozenKey(FrozenDefinitionType.TASK, id("acceptance/timeout_only"))
				),
			label + " must retain the complete reachable task plan"
		);
	}

	private static void checkFixedIdentifierArgumentClosure() {
		Fixture fixture = fixture();
		List<Source> sources = mutateSource(
			fixture.sources(),
			DefinitionType.FLOW,
			FLOW,
			root -> {
				JsonObject reference = new JsonObject();
				reference.addProperty("cue", FIXED_FIELD_CUE.toString());
				JsonObject arguments = new JsonObject();
				arguments.addProperty("field_id", HUNTER_SPAWN_FIELD.toString());
				reference.add("arguments", arguments);
				JsonObject hooks = new JsonObject();
				hooks.add("start", reference);
				root.add("message_hooks", hooks);
			}
		);
		sources.add(
			cueSource(
				FIXED_FIELD_CUE,
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "parameters": {
				    "field_id": {
				      "type": "identifier",
				      "identifier_kind": "field"
				    }
				  },
				  "nodes": [{
				    "id": "notice",
				    "type": "text",
				    "channel": "chat",
				    "text": {"parts": [{"component": {"text": "Field"}}]}
				  }]
				}
				"""
			)
		);
		DefinitionSnapshot snapshot = compile(fixture, sources, 305L);
		var freeze = ExecutionSnapshotCompiler.freeze(
			snapshot,
			snapshot.flows().get(FLOW),
			snapshot.panelActions().get(ACTION)
		);
		check(freeze.success(), "fixed identifier hook closure must freeze: " + diagnostic(freeze));
		check(
			freeze.frozen().orElseThrow().fields().containsKey(HUNTER_SPAWN_FIELD),
			"a fixed identifier hook argument must retain its referenced field"
		);
	}

	private static Fixture fixture() {
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(BASE_PACK);
		Map<Identifier, String> predicateDocuments = new LinkedHashMap<>();
		for (Identifier predicate : loaded.predicates()) {
			Path source = BASE_PACK.resolve("data")
				.resolve(predicate.getNamespace())
				.resolve("predicate")
				.resolve(predicate.getPath() + ".json");
			try {
				predicateDocuments.put(
					predicate,
					JsonParser.parseString(
						Files.readString(source, StandardCharsets.UTF_8)
					).toString()
				);
			} catch (IOException error) {
				throw new AssertionError("failed to read predicate fixture " + source, error);
			}
		}
		return new Fixture(
			new ArrayList<>(loaded.sources().values()),
			loaded.functions(),
			loaded.predicates(),
			Map.copyOf(predicateDocuments)
		);
	}

	private static DefinitionSnapshot compile(
		final Fixture fixture,
		final List<Source> sources,
		final long generation
	) {
		var compilation = DefinitionCompiler.compile(
			sources,
			fixture.functions(),
			fixture.predicates()
		);
		check(compilation.valid(), "message-hook snapshot fixture must compile: " + compilation.problems());
		DefinitionSnapshot snapshot = compilation.snapshot().orElseThrow()
			.withGeneration(generation);
		Map<Identifier, String> retainedDocuments = new LinkedHashMap<>();
		for (Identifier predicate : DefinitionRegistryTestAccess.referencedPredicates(snapshot)) {
			String document = fixture.predicateDocuments().get(predicate);
			check(document != null, "referenced predicate fixture is missing: " + predicate);
			retainedDocuments.put(predicate, document);
		}
		return snapshot.withPredicateDocuments(retainedDocuments);
	}

	private static List<Source> withClosureFixture(final List<Source> sources) {
		List<Source> result = new ArrayList<>(sources);
		Source fieldCue = requireSource(sources, DefinitionType.MESSAGE_CUE, FIELD_CUE);
		Source policyCue = requireSource(sources, DefinitionType.MESSAGE_CUE, POLICY_CUE);
		Source historyCue = requireSource(sources, DefinitionType.MESSAGE_CUE, HISTORY_CUE);
		JsonObject cue = JsonParser.parseString(fieldCue.json()).getAsJsonObject();
		JsonObject policy = JsonParser.parseString(policyCue.json()).getAsJsonObject();
		JsonObject history = JsonParser.parseString(historyCue.json()).getAsJsonObject();

		JsonObject taskParameter = policy.getAsJsonObject("parameters")
			.getAsJsonObject("task_id")
			.deepCopy();
		taskParameter.addProperty("default", EXTRA_FLOW_TASK.toString());
		taskParameter.remove("allowed_nodes");
		JsonObject profileParameter = policy.getAsJsonObject("parameters")
			.getAsJsonObject("profile_value")
			.deepCopy();
		profileParameter.remove("allowed_nodes");
		profileParameter.getAsJsonArray("sources")
			.get(0)
			.getAsJsonObject()
			.addProperty("field", PLAYER_ROLE.toString());
		cue.getAsJsonObject("parameters").add("task_id", taskParameter);
		cue.getAsJsonObject("parameters").add("profile_value", profileParameter);
		cue.getAsJsonObject("fields").add(
			"current_task",
			JsonParser.parseString(
				"""
				{
				  "source": {"binding": "task/current/name"},
				  "fallback": {"text": "No task"},
				  "reservation": {"max_graphemes": 64}
				}
				"""
			).getAsJsonObject()
		);
		cue.add("history", history.get("history").deepCopy());

		JsonObject firstNode = cue.getAsJsonArray("nodes").get(0).getAsJsonObject();
		firstNode.add(
			"when",
			policy.getAsJsonArray("nodes").get(0).getAsJsonObject().get("when").deepCopy()
		);
		for (var nodeValue : cue.getAsJsonArray("nodes")) {
			JsonObject node = nodeValue.getAsJsonObject();
			if (!node.has("content")) {
				continue;
			}
			JsonObject content = node.getAsJsonObject("content");
			if (
				content.has("effect")
					&& content.get("effect").isJsonPrimitive()
					&& content.get("effect").getAsString().equals(DECODE_EFFECT.toString())
			) {
				content.addProperty("effect", ACCEPTANCE_EFFECT.toString());
			}
		}
		result.add(cueSource(CLOSURE_CUE, cue.toString()));
		result.add(taskSource(EXTRA_FLOW_TASK));
		result.add(playerDataSource(PLAYER_ROLE));
		result.add(playerDataSource(UNRELATED_PLAYER_DATA));
		return result;
	}

	private static Source requireSource(
		final List<Source> sources,
		final DefinitionType type,
		final Identifier id
	) {
		return sources.stream()
			.filter(source -> source.type() == type && source.id().equals(id))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				"fixture source is missing: " + type.directory() + "/" + id
			));
	}

	private static Source taskSource(final Identifier task) {
		return new Source(
			DefinitionType.TASK,
			task,
			Identifier.fromNamespaceAndPath(
				task.getNamespace(),
				"pixel_tzz_pro/tasks/" + task.getPath() + ".json"
			),
			"message-hook-freeze-self-check",
			"""
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "version": 1,
			  "kind": "main",
			  "name": {"text": "Freeze closure task"},
			  "completion_policy": "event_only",
			  "counts_toward_game_time": true,
			  "recap_visibility": "host_only",
			  "results": [{
			    "id": "done",
			    "name": {"text": "Done"},
			    "semantic": "neutral",
			    "route": {"end_phase": "pixel_tzz:ended"}
			  }],
			  "events": [],
			  "statistics": []
			}
			"""
		);
	}

	private static Source playerDataSource(final Identifier data) {
		return new Source(
			DefinitionType.PLAYER_DATA,
			data,
			Identifier.fromNamespaceAndPath(
				data.getNamespace(),
				"pixel_tzz_pro/player_data/" + data.getPath() + ".json"
			),
			"message-hook-freeze-self-check",
			"""
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "source": {"type": "role"},
			  "surfaces": ["dynamic_message"],
			  "audience": {"exclude_host": true, "online_only": true},
			  "name": {"text": "Closure role"}
			}
			"""
		);
	}

	private static Source roleSource(final Identifier role) {
		return new Source(
			DefinitionType.ROLE,
			role,
			Identifier.fromNamespaceAndPath(
				role.getNamespace(),
				"pixel_tzz_pro/roles/" + role.getPath() + ".json"
			),
			"message-hook-freeze-self-check",
			"""
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "name": {"text": "Late participant"},
			  "tags": ["pixel_tzz:active_participant"]
			}
			"""
		);
	}

	private static List<Source> mutateSource(
		final List<Source> sources,
		final DefinitionType type,
		final Identifier id,
		final Consumer<JsonObject> mutation
	) {
		List<Source> result = new ArrayList<>(sources.size());
		boolean replaced = false;
		for (Source source : sources) {
			if (source.type() != type || !source.id().equals(id)) {
				result.add(source);
				continue;
			}
			JsonObject root = JsonParser.parseString(source.json()).getAsJsonObject();
			mutation.accept(root);
			result.add(new Source(
				source.type(),
				source.id(),
				source.resource(),
				source.sourcePack(),
				root.toString()
			));
			replaced = true;
		}
		check(replaced, "fixture source is missing: " + type.directory() + "/" + id);
		return result;
	}

	private static void setHook(
		final JsonObject owner,
		final String event,
		final Identifier cue
	) {
		JsonObject hooks = owner.has("message_hooks")
			? owner.getAsJsonObject("message_hooks")
			: new JsonObject();
		hooks.addProperty(event, cue.toString());
		owner.add("message_hooks", hooks);
	}

	private static JsonObject hooks(
		final String event,
		final Identifier cue
	) {
		JsonObject hooks = new JsonObject();
		hooks.addProperty(event, cue.toString());
		return hooks;
	}

	private static Source cueSource(final Identifier cue, final String json) {
		return new Source(
			DefinitionType.MESSAGE_CUE,
			cue,
			Identifier.fromNamespaceAndPath(
				cue.getNamespace(),
				"pixel_tzz_pro/message_cues/" + cue.getPath() + ".json"
			),
			"message-hook-freeze-self-check",
			json
		);
	}

	private static String simpleCueJson(final String text) {
		return """
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "nodes": [{
			    "id": "notice",
			    "type": "text",
			    "channel": "chat",
			    "text": {"parts": [{"component": {"text": "%s"}}]}
			  }]
			}
			""".formatted(text);
	}

	private static String taskParameterCueJson(final String source) {
		return """
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "parameters": {
			    "task_id": {
			      "type": "identifier",
			      "sources": [%s],
			      "on_error": "fail",
			      "identifier_kind": "task"
			    }
			  },
			  "nodes": [{
			    "id": "notice",
			    "type": "text",
			    "channel": "chat",
			    "text": {"parts": [{"component": {"text": "Task source"}}]}
			  }]
			}
			""".formatted(source);
	}

	private static Set<Identifier> identifierSet(final JsonArray values) {
		Set<Identifier> result = new LinkedHashSet<>();
		values.forEach(value -> result.add(Identifier.parse(value.getAsString())));
		return Set.copyOf(result);
	}

	private static String diagnostic(final ExecutionSnapshotCompiler.FreezeResult result) {
		return result.code() + " (" + result.message() + ")";
	}

	private static String diagnostic(final TimelineSnapshotCompiler.FreezeResult result) {
		return result.code() + " (" + result.message() + ")";
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record Fixture(
		List<Source> sources,
		Set<Identifier> functions,
		Set<Identifier> predicates,
		Map<Identifier, String> predicateDocuments
	) {
	}

	private record FrozenKey(FrozenDefinitionType type, Identifier id) {
	}
}
