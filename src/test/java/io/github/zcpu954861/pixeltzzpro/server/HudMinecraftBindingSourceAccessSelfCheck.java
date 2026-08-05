package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBinding;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBindingSource;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBindingSourceType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudValueType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.MissingMode;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.MissingPolicy;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateContext;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingRequest;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ClockValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ComponentValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.IntegerValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ObjectValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.PlayerValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ResolveContext;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.Result;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.StringValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.Value;
import io.github.zcpu954861.pixeltzzpro.server.HudMinecraftBindingSourceAccess.PlayerIdentity;
import io.github.zcpu954861.pixeltzzpro.server.HudMinecraftBindingSourceAccess.SourceBackend;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.IntermissionState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecordState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Focused executable checks for V3C's Minecraft HUD source authority boundary. */
public final class HudMinecraftBindingSourceAccessSelfCheck {
	private static final UUID VIEWER = UUID.fromString("10000000-0000-0000-0000-000000003c01");
	private static final UUID HOST = UUID.fromString("10000000-0000-0000-0000-000000003c02");
	private static final UUID GAME_INSTANCE = UUID.fromString("10000000-0000-0000-0000-000000003c03");
	private static final UUID TIMELINE = UUID.fromString("10000000-0000-0000-0000-000000003c04");
	private static final UUID TASK_INSTANCE = UUID.fromString("10000000-0000-0000-0000-000000003c05");
	private static final Identifier GAME = id("test:main");
	private static final Identifier PHASE = id("test:running");
	private static final Identifier TASK = id("test:branch");
	private static final Identifier ROLE = id("test:runner");
	private static final Identifier LIFE = id("test:alive");
	private static final Identifier FIELD = id("test:personal_note");
	private static final Identifier HUD_DATA = id("test:hud_note");
	private static final Identifier TERMINAL_ONLY_DATA = id("test:terminal_only");
	private static final Identifier WRONG_AUDIENCE_DATA = id("test:wrong_audience");
	private static final Identifier PREDICATE = id("test:hud_allowed");
	private static final Identifier STORAGE = id("test:secret_store");
	private static final HudRouteAuthority.Context CONTEXT = new HudRouteAuthority.Context(
		VIEWER,
		GAME,
		Optional.of(PHASE),
		Optional.of(TASK),
		Optional.of(TaskKind.MAIN),
		Optional.of(TaskStatus.INTERVAL),
		Optional.of(ROLE),
		Optional.empty(),
		Optional.of(LIFE),
		Set.of(id("test:active_participant")),
		Set.of(),
		Set.of(),
		false,
		true,
		true,
		false,
		false,
		Set.of(VIEWER)
	);

	private HudMinecraftBindingSourceAccessSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		WorldStateV3 state = state();
		checkAllDocumentedSources(definitions, state);
		checkAuthorizationPrecedesReads(definitions, state);
		checkPlayerDataIntersection(definitions, state);
		checkSafeTokens(definitions, state);
		checkHistoryDoesNotBecomeCurrentFacts(definitions, state);
		checkTypedMissingAndSecretFreeDiagnostics(definitions, state);
		System.out.println("HUD_MINECRAFT_BINDING_SOURCE_ACCESS_SELF_CHECK=PASS");
	}

	private static void checkAllDocumentedSources(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		FakeBackend backend = backend();
		BindingSourceAccess access = access(definitions, state, backend);
		check(resolve(access, binding("game", HudValueType.COMPONENT, gameFact("name"))) instanceof ComponentValue,
			"game_fact must resolve a typed component");
		check(resolve(access, binding("phase", HudValueType.IDENTIFIER, phaseFact("id"))) != null,
			"phase_fact must resolve");
		check(resolve(access, binding("task", HudValueType.COMPONENT, taskFact("name"))) instanceof ComponentValue,
			"task_fact must resolve");
		check(resolve(access, binding("player", HudValueType.PLAYER, playerFact("self"))) instanceof PlayerValue,
			"player_fact must resolve a player");
		check(integer(resolve(access, binding("event", HudValueType.INTEGER, event("opened", "count")))) == 1L,
			"event count mismatch");
		check(integer(resolve(access, binding("statistic", HudValueType.INTEGER, statistic("alive_runners")))) == 3L,
			"statistic mismatch");
		check(resolve(access, binding("result", HudValueType.COMPONENT, result("name"))) instanceof ComponentValue,
			"result must resolve its public name");
		check(resolve(access, binding("clock", HudValueType.CLOCK, clock("game"))) instanceof ClockValue,
			"clock must resolve an interpolation anchor");
		check(string(resolve(access, binding("player_data", HudValueType.STRING, playerData(HUD_DATA)))).equals("alpha"),
			"player_data mismatch");
		check(integer(resolve(access, binding("score", HudValueType.INTEGER, score("public_score", "self")))) == 17L,
			"score mismatch");
		check(string(resolve(access, binding("storage", HudValueType.STRING, storage(STORAGE, "hud.text")))).equals("safe"),
			"storage mismatch");
		check(integer(resolve(access, binding("entity", HudValueType.INTEGER, entity("self", "Health")))) == 20L,
			"entity_data mismatch");
		check(backend.scoreReads == 1, "one score binding must read exactly once");
		check(backend.storageReads == 1, "one storage binding must read exactly once");
		check(backend.entityReads == 1, "one entity binding must read exactly once");
	}

	private static void checkAuthorizationPrecedesReads(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		FakeBackend backend = backend();
		BindingSourceAccess access = access(definitions, state, backend);
		BindingRequest score = request(binding("direct_score", HudValueType.INTEGER, score("public_score", "self")));
		check(access.resolve(score).value().isEmpty(), "resolve without a one-shot authorization must fail closed");
		check(backend.scoreReads == 0, "direct resolve must not touch the scoreboard");
		check(access.authorized(score), "safe score should authorize");
		check(access.resolve(score).value().isPresent(), "authorized score should resolve");
		check(access.resolve(score).value().isEmpty(), "authorization permit must be one shot");
		check(backend.scoreReads == 1, "one-shot permit must prevent duplicate reads");
	}

	private static void checkPlayerDataIntersection(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		FakeBackend backend = backend();
		BindingSourceAccess access = access(definitions, state, backend);
		BindingRequest good = request(binding("good_data", HudValueType.STRING, playerData(HUD_DATA)));
		check(access.authorized(good), "HUD player_data with matching live gates must authorize");
		check(backend.predicateReads == 1, "authorized player_data predicate must run once");
		check(access.resolve(good).value().isPresent(), "authorized player_data must resolve");

		BindingRequest terminalOnly = request(binding(
			"terminal_only",
			HudValueType.STRING,
			playerData(TERMINAL_ONLY_DATA)
		));
		check(!access.authorized(terminalOnly), "terminal-only player_data must never authorize for HUD");
		check(backend.predicateReads == 1, "surface denial must precede predicates");

		BindingRequest wrongAudience = request(binding(
			"wrong_audience",
			HudValueType.STRING,
			playerData(WRONG_AUDIENCE_DATA)
		));
		check(!access.authorized(wrongAudience), "player_data audience mismatch must fail closed");
		check(backend.predicateReads == 1, "audience denial must precede predicates");

		FakeBackend deniedPredicate = backend();
		deniedPredicate.predicateAllowed = false;
		BindingSourceAccess deniedAccess = access(definitions, state, deniedPredicate);
		check(!deniedAccess.authorized(good), "failed player_data predicate must deny the source");
		check(deniedAccess.resolve(good).value().isEmpty(), "denied player_data must not resolve later");
	}

	private static void checkSafeTokens(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		FakeBackend backend = backend();
		BindingSourceAccess access = access(definitions, state, backend);
		BindingRequest selectorScore = request(binding(
			"unsafe_score",
			HudValueType.INTEGER,
			score("public_score", "@a")
		));
		check(!access.authorized(selectorScore), "score selectors must be rejected");
		check(access.resolve(selectorScore).value().isEmpty() && backend.scoreReads == 0,
			"rejected score selector must never read");

		Value fixed = resolve(access, binding("fixed_score", HudValueType.INTEGER, score("public_score", "#global")));
		check(integer(fixed) == 29L && backend.lastScoreHolder.equals("#global"),
			"fixed score holder must remain literal");

		BindingRequest unsafeEntity = request(binding(
			"unsafe_entity",
			HudValueType.INTEGER,
			entity("nearest", "Health")
		));
		check(!access.authorized(unsafeEntity), "arbitrary entity target tokens must be rejected");
		check(access.resolve(unsafeEntity).value().isEmpty() && backend.entityReads == 0,
			"rejected entity target must never read");

		Value hostHealth = resolve(access, binding("host_entity", HudValueType.INTEGER, entity("host", "Health")));
		check(integer(hostHealth) == 18L && backend.lastEntity.equals(HOST),
			"host entity token must bind only the persisted host UUID");
	}

	private static void checkHistoryDoesNotBecomeCurrentFacts(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		TimelineInstance active = state.timeline().orElseThrow();
		TaskInstance completed = active.currentTask().orElseThrow();
		TimelineInstance terminal = new TimelineInstance(
			active.instanceId(),
			active.gameId(),
			active.contentVersion(),
			active.snapshot(),
			TimelineStatus.COMPLETED,
			Optional.empty(),
			Optional.empty(),
			List.of(completed),
			active.gameElapsedTicks(),
			false,
			Optional.empty(),
			active.createdAtServerTick(),
			active.startedAtServerTick(),
			Optional.of(2_000L),
			active.timelineRevision() + 1L,
			active.callbackLedger()
		);
		WorldStateV3 historical = state.withTimeline(Optional.of(terminal));
		check(historical.validated().error().isEmpty(), "history-only source state must be valid");
		HudRouteAuthority.Context noCurrentTask = new HudRouteAuthority.Context(
			VIEWER,
			GAME,
			Optional.of(PHASE),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.of(ROLE),
			Optional.empty(),
			Optional.of(LIFE),
			Set.of(id("test:active_participant")),
			Set.of(),
			Set.of(),
			false,
			true,
			true,
			false,
			false,
			Set.of()
		);
		BindingSourceAccess access = HudMinecraftBindingSourceAccess.createForTesting(
			definitions,
			historical,
			VIEWER,
			backend()
		);
		for (HudBinding binding : List.of(
			binding("historical_task", HudValueType.COMPONENT, taskFact("name")),
			binding("historical_event", HudValueType.INTEGER, event("opened", "count")),
			binding("historical_statistic", HudValueType.INTEGER, statistic("alive_runners")),
			binding("historical_result", HudValueType.COMPONENT, result("name"))
		)) {
			BindingRequest request = request(binding, noCurrentTask);
			check(!access.authorized(request), "history must not authorize a current HUD fact: " + binding.id());
			check(access.resolve(request).value().isEmpty(), "history fact denial must remain missing: " + binding.id());
		}
	}

	private static void checkTypedMissingAndSecretFreeDiagnostics(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		FakeBackend backend = backend();
		BindingSourceAccess access = access(definitions, state, backend);
		HudBinding wrongType = binding(
			"public_field",
			HudValueType.INTEGER,
			storage(STORAGE, "private.payload")
		);
		Result result = HudBindingResolver.resolve(
			Map.of(wrongType.id(), wrongType),
			new ResolveContext(CONTEXT, access)
		);
		check(result.values().isEmpty(), "wrong NBT type must be missing, not coerced");
		String diagnostic = result.diagnostics().toString();
		for (String secret : List.of(
			"secret_store", "private.payload", "public_score", "#global", "Health", "host"
		)) {
			check(!diagnostic.contains(secret), "diagnostic leaked a source descriptor: " + secret);
		}

		CompoundTag object = new CompoundTag();
		object.putString("label", "visible");
		object.putInt("count", 4);
		backend.storage.put(key(STORAGE, "hud.object"), object);
		Value typedObject = resolve(
			access,
			binding("object", HudValueType.OBJECT, storage(STORAGE, "hud.object"))
		);
		check(typedObject instanceof ObjectValue, "bounded compound NBT must remain a typed object");
		check(!typedObject.toString().contains("hud.object"), "resolved value must not retain its source path");
	}

	private static DefinitionSnapshot definitions() {
		List<Source> sources = List.of(
			source(DefinitionType.GAME, "main", """
				{
				  "format_version":1,
				  "api_version":4,
				  "content_version":1,
				  "name":{"text":"Source check"},
				  "initial_phase":"test:running",
				  "default_role":"test:runner",
				  "default_life_state":"test:alive",
				  "task_timeline":{
				    "initial_task":"test:branch",
				    "approval_phase":"test:running",
				    "start_phase":"test:running"
				  },
				  "player_terminal":{
				    "default_page":"test:home",
				    "history_enabled":true
				  }
				}
				"""),
			source(DefinitionType.PAGE, "home", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "title":{"text":"Source check"},
				  "root":{"type":"column","children":[{"type":"text","text":{"text":"HUD"}}]}
				}
				"""),
			source(DefinitionType.ROLE, "runner", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "name":{"text":"Runner"},
				  "tags":["test:active_participant"]
				}
				"""),
			source(DefinitionType.ROLE, "hunter", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "name":{"text":"Hunter"},
				  "tags":["test:hunter"]
				}
				"""),
			source(DefinitionType.LIFE_STATE, "alive", """
				{"format_version":1,"game":"test:main","name":{"text":"Alive"}}
				"""),
			source(DefinitionType.PHASE, "running", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "name":{"text":"Running"},
				  "transitions":[]
				}
				"""),
			source(DefinitionType.FIELD, "personal_note", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "version":1,
				  "name":{"text":"Personal note"},
				  "scope":"player",
				  "type":"string",
				  "required":false,
				  "editable_by":"none",
				  "invalidates_ready":false,
				  "migration":"preserve"
				}
				"""),
			source(DefinitionType.TASK, "branch", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "version":1,
				  "kind":"main",
				  "name":{"text":"Branch task"},
				  "description":{"text":"Public objective"},
				  "audience":{"role_tags":["test:active_participant"],"exclude_host":true,"online_only":false},
				  "completion_policy":"early_or_timeout",
				  "duration_ticks":200,
				  "counts_toward_game_time":true,
				  "recap_visibility":"participants",
				  "results":[{
				    "id":"success",
				    "name":{"text":"Success"},
				    "semantic":"success",
				    "recap":{"text":"Completed"},
				    "route":{"end_phase":"test:running","intermission":{"duration_ticks":200,"counts_toward_game_time":false,"name":{"text":"Wait"}}}
				  }],
				  "events":[{
				    "id":"opened",
				    "name":{"text":"Opened"},
				    "policy":"repeatable",
				    "max_records":8,
				    "allowed_states":["running"],
				    "recap_visibility":"participants",
				    "player_history":{
				      "release":"immediate",
				      "audience":{"role_tags":["test:active_participant"],"exclude_host":true,"online_only":true},
				      "title":{"text":"Opened"},
				      "show_game_time":true,
				      "show_task":true,
				      "show_actor":true,
				      "show_targets":false
				    }
				  }],
				  "statistics":[{
				    "id":"alive_runners",
				    "name":{"text":"Alive runners"},
				    "type":"integer",
				    "min":0,
				    "max":64,
				    "write":"replace",
				    "recap_visibility":"participants"
				  }]
				}
				"""),
			playerDataSource("hud_note", "[\"hud\"]", "{\"role_tags\":[\"test:active_participant\"],\"exclude_host\":true,\"online_only\":true}", true),
			playerDataSource("terminal_only", "[\"terminal\"]", "{\"role_tags\":[\"test:active_participant\"],\"exclude_host\":true,\"online_only\":true}", true),
			playerDataSource("wrong_audience", "[\"hud\"]", "{\"roles\":[\"test:hunter\"],\"exclude_host\":true,\"online_only\":true}", true)
		);
		DefinitionCompiler.Compilation compilation = DefinitionCompiler.compile(
			sources,
			Set.of(),
			Set.of(PREDICATE)
		);
		check(compilation.valid(), "source self-check definitions must compile: " + compilation.problems());
		return compilation.snapshot().orElseThrow().withGeneration(31L);
	}

	private static Source playerDataSource(
		final String id,
		final String surfaces,
		final String audience,
		final boolean predicate
	) {
		return source(DefinitionType.PLAYER_DATA, id, """
			{
			  "format_version":1,
			  "game":"test:main",
			  "source":{"type":"field","id":"test:personal_note"},
			  "surfaces":%s,
			  "audience":%s,
			  "phases":["test:running"],
			  "tasks":["test:branch"],
			  "task_states":["intermission"]%s
			}
			""".formatted(
				surfaces,
				audience,
				predicate ? ",\n  \"predicate\":\"test:hud_allowed\"" : ""
			));
	}

	private static WorldStateV3 state() {
		PlayerRecord player = new PlayerRecord(
			VIEWER,
			"PlayerB",
			ROLE,
			Optional.empty(),
			LIFE,
			Map.of(FIELD, new PersistentFieldValue(1, StoredValue.ofString("alpha"))),
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			1L,
			2L
		);
		WorldStateV2 core = WorldStateV2.initial()
			.withActivity(GAME, PHASE)
			.withHost(Optional.of(HostRecord.migratedLegacy(HOST)))
			.withPlayers(Map.of(VIEWER, player));
		TaskInstance task = new TaskInstance(
			TASK_INSTANCE,
			TASK,
			WorldStateV3.TaskKind.MAIN,
			WorldStateV3.TaskStatus.SETTLED,
			Optional.empty(),
			80L,
			Optional.of(new FrozenResult(
				"success",
				Optional.empty(),
				Optional.of(PHASE),
				Optional.empty(),
				Optional.empty(),
				120L
			)),
			Optional.of(new IntermissionState(200L, 40L, false, false)),
			List.of(VIEWER),
			40L,
			Optional.of(120L),
			List.of(),
			List.of(new TaskEventRecord(
				"opened",
				100L,
				35L,
				TaskEventRecordState.RUNNING,
				Optional.of(new FrozenPlayerValue(VIEWER, "PlayerB"))
			)),
			List.of(TaskStatisticValue.integer("alive_runners", 3L))
		);
		TimelineInstance timeline = new TimelineInstance(
			TIMELINE,
			GAME,
			1,
			WorldStateV2.FrozenDocument.of("{}"),
			TimelineStatus.INTERMISSION,
			Optional.empty(),
			Optional.of(task),
			List.of(),
			120L,
			false,
			Optional.empty(),
			1_000L,
			Optional.of(1_001L),
			Optional.empty(),
			7L,
			List.of()
		);
		WorldStateV3 result = new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(GAME_INSTANCE),
			Optional.of(timeline),
			Optional.empty(),
			List.of(),
			List.of()
		);
		check(result.validated().error().isEmpty(), "source self-check state must be valid");
		return result;
	}

	private static FakeBackend backend() {
		FakeBackend backend = new FakeBackend();
		backend.players.put(VIEWER, new PlayerIdentity(VIEWER, "PlayerB", true));
		backend.players.put(HOST, new PlayerIdentity(HOST, "Player972", true));
		backend.scores.put("public_score\u0000PlayerB", 17L);
		backend.scores.put("public_score\u0000#global", 29L);
		backend.storage.put(key(STORAGE, "hud.text"), StringTag.valueOf("safe"));
		backend.storage.put(key(STORAGE, "private.payload"), StringTag.valueOf("private"));
		backend.entities.put(key(VIEWER, "Health"), IntTag.valueOf(20));
		backend.entities.put(key(HOST, "Health"), IntTag.valueOf(18));
		return backend;
	}

	private static BindingSourceAccess access(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final FakeBackend backend
	) {
		return HudMinecraftBindingSourceAccess.createForTesting(definitions, state, VIEWER, backend);
	}

	private static Value resolve(final BindingSourceAccess access, final HudBinding binding) {
		Result result = HudBindingResolver.resolve(
			Map.of(binding.id(), binding),
			new ResolveContext(CONTEXT, access)
		);
		check(!result.hideComponent(), "binding unexpectedly hid its component: " + result.diagnostics());
		check(result.diagnostics().isEmpty(), "binding emitted diagnostics: " + result.diagnostics());
		return result.values().get(binding.id()).value();
	}

	private static BindingRequest request(final HudBinding binding) {
		return request(binding, CONTEXT);
	}

	private static BindingRequest request(
		final HudBinding binding,
		final HudRouteAuthority.Context context
	) {
		return new BindingRequest(binding, binding.source(), context, Optional.empty());
	}

	private static HudBinding binding(
		final String id,
		final HudValueType type,
		final HudBindingSource source
	) {
		return new HudBinding(
			id,
			type,
			Optional.empty(),
			source,
			Optional.empty(),
			false,
			false,
			new MissingPolicy(
				MissingMode.HIDE_FIELD,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			)
		);
	}

	private static HudBindingSource gameFact(final String value) {
		return valueSource(HudBindingSourceType.GAME_FACT, value);
	}

	private static HudBindingSource phaseFact(final String value) {
		return valueSource(HudBindingSourceType.PHASE_FACT, value);
	}

	private static HudBindingSource taskFact(final String value) {
		return valueSource(HudBindingSourceType.TASK_FACT, value);
	}

	private static HudBindingSource playerFact(final String value) {
		return valueSource(HudBindingSourceType.PLAYER_FACT, value);
	}

	private static HudBindingSource result(final String value) {
		return valueSource(HudBindingSourceType.RESULT, value);
	}

	private static HudBindingSource clock(final String value) {
		return valueSource(HudBindingSourceType.CLOCK, value);
	}

	private static HudBindingSource valueSource(
		final HudBindingSourceType type,
		final String value
	) {
		return source(type, Optional.of(value), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static HudBindingSource event(final String id, final String value) {
		return source(HudBindingSourceType.EVENT, Optional.of(value), Optional.of(id("test:" + id)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static HudBindingSource statistic(final String id) {
		return source(HudBindingSourceType.STATISTIC, Optional.empty(), Optional.of(id("test:" + id)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static HudBindingSource playerData(final Identifier field) {
		return source(HudBindingSourceType.PLAYER_DATA, Optional.empty(), Optional.empty(), Optional.of(field), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static HudBindingSource score(final String objective, final String holder) {
		return source(HudBindingSourceType.SCORE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(objective), Optional.of(holder), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static HudBindingSource storage(final Identifier storage, final String path) {
		return source(HudBindingSourceType.STORAGE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(storage), Optional.of(path), Optional.empty());
	}

	private static HudBindingSource entity(final String target, final String path) {
		return source(HudBindingSourceType.ENTITY_DATA, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(path), Optional.of(target));
	}

	private static HudBindingSource source(
		final HudBindingSourceType type,
		final Optional<String> value,
		final Optional<Identifier> id,
		final Optional<Identifier> field,
		final Optional<String> objective,
		final Optional<String> holder,
		final Optional<Identifier> storage,
		final Optional<String> path,
		final Optional<String> target
	) {
		return new HudBindingSource(type, value, id, field, objective, holder, storage, path, target);
	}

	private static Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		return new Source(
			type,
			id("test:" + path),
			id("test:pixel_tzz_pro/" + type.directory() + "/" + path + ".json"),
			"hud-minecraft-source-self-check",
			json
		);
	}

	private static long integer(final Value value) {
		return ((IntegerValue)value).value();
	}

	private static String string(final Value value) {
		return ((StringValue)value).value();
	}

	private static String key(final Identifier id, final String path) {
		return id + "\u0000" + path;
	}

	private static String key(final UUID id, final String path) {
		return id + "\u0000" + path;
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeBackend implements SourceBackend {
		private final Map<UUID, PlayerIdentity> players = new LinkedHashMap<>();
		private final Map<String, Long> scores = new LinkedHashMap<>();
		private final Map<String, Tag> storage = new LinkedHashMap<>();
		private final Map<String, Tag> entities = new LinkedHashMap<>();
		private boolean predicateAllowed = true;
		private int predicateReads;
		private int scoreReads;
		private int storageReads;
		private int entityReads;
		private String lastScoreHolder = "";
		private UUID lastEntity;

		@Override
		public long serverTick() {
			return 2_000L;
		}

		@Override
		public boolean online(final UUID playerId) {
			return this.players.getOrDefault(playerId, new PlayerIdentity(playerId, playerId.toString(), false)).online();
		}

		@Override
		public Optional<PlayerIdentity> player(final UUID playerId) {
			return Optional.ofNullable(this.players.get(playerId));
		}

		@Override
		public boolean predicate(final Identifier predicate, final PredicateContext context) {
			this.predicateReads++;
			return this.predicateAllowed && predicate.equals(PREDICATE) && context.playerId().equals(VIEWER);
		}

		@Override
		public OptionalLong score(final String objective, final String holder) {
			this.scoreReads++;
			this.lastScoreHolder = holder;
			Long value = this.scores.get(objective + "\u0000" + holder);
			return value == null ? OptionalLong.empty() : OptionalLong.of(value);
		}

		@Override
		public Optional<Tag> storage(final Identifier storage, final String path) {
			this.storageReads++;
			return Optional.ofNullable(this.storage.get(key(storage, path))).map(Tag::copy);
		}

		@Override
		public Optional<Tag> entity(final UUID playerId, final String path) {
			this.entityReads++;
			this.lastEntity = playerId;
			return Optional.ofNullable(this.entities.get(key(playerId, path))).map(Tag::copy);
		}
	}
}
