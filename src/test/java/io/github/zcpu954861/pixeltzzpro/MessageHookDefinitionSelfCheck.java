package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.DatapackFixtureLoader.LoadedPacks;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Pure checks for owner-local declarative V3B message-hook parsing and reference closure. */
public final class MessageHookDefinitionSelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier CUE = id("self_check/hook");

	private MessageHookDefinitionSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkAllOwnerShapesAndShorthand();
		checkOwnerWhitelist();
		checkLimitsAndReservedArguments();
		checkReferenceClosure();
		checkFixedArgumentContract();
		checkDisabledCueIsolation();

		System.out.println("MESSAGE_HOOK_DEFINITION_SELF_CHECK=PASS");
	}

	private static void checkAllOwnerShapesAndShorthand() {
		CompilationFixture fixture = fixture();
		List<Source> sources = fixture.sources().stream().map(source -> {
			if (matches(source, DefinitionType.GAME, "main")) {
				return mutate(source, root -> {
					root.add("message_hooks", hooks("start", CUE.toString()));
					root.getAsJsonObject("readiness")
						.add("message_hooks", hooks("complete", CUE.toString()));
				});
			}
			if (matches(source, DefinitionType.ROLE, "runner")) {
				return mutate(source, root ->
					root.add("message_hooks", hooks("role_changed", CUE.toString()))
				);
			}
			if (matches(source, DefinitionType.PHASE, "setup")) {
				return mutate(source, root ->
					root.add("message_hooks", hooks("enter", CUE.toString()))
				);
			}
			if (matches(source, DefinitionType.FLOW, "general_tutorial")) {
				return mutate(source, root ->
					root.add("message_hooks", hooks("player_complete", CUE.toString()))
				);
			}
			if (matches(source, DefinitionType.TASK, "acceptance/main_branch")) {
				return mutate(source, root -> {
					JsonObject reference = new JsonObject();
					reference.addProperty("cue", CUE.toString());
					JsonObject arguments = new JsonObject();
					arguments.addProperty("label", "任务开始");
					arguments.addProperty("priority", 7);
					arguments.addProperty("quiet", false);
					reference.add("arguments", arguments);
					JsonObject taskHooks = new JsonObject();
					taskHooks.add("start", reference);
					root.add("message_hooks", taskHooks);
					root.getAsJsonArray("events")
						.get(0)
						.getAsJsonObject()
						.add("message_hooks", hooks("trigger", CUE.toString()));
				});
			}
			return source;
		}).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		sources.add(cueSource(CUE, validCueJson(true)));

		var compilation = DefinitionCompiler.compile(sources, fixture.functions(), fixture.predicates());
		check(compilation.valid(), "all owner hook shapes must compile: " + compilation.problems());
		var snapshot = compilation.snapshot().orElseThrow();
		check(
			snapshot.games().get(GAME).messageHooks().get(MessageHookEvent.START).isPresent(),
			"game shorthand hook must be retained"
		);
		check(
			snapshot.games().get(GAME).readiness().orElseThrow().messageHooks()
				.get(MessageHookEvent.COMPLETE).isPresent(),
			"nested readiness hook must be retained"
		);
		var taskHook = snapshot.tasks().get(id("acceptance/main_branch"))
			.messageHooks()
			.get(MessageHookEvent.START)
			.orElseThrow();
		check(taskHook.arguments().get("priority").equals("7"), "numeric fixed arguments must canonicalize");
		check(taskHook.arguments().get("quiet").equals("false"), "boolean fixed arguments must canonicalize");
		check(
			snapshot.tasks().get(id("acceptance/main_branch")).events().get(0)
				.messageHooks().get(MessageHookEvent.TRIGGER).isPresent(),
			"task-event hook must be retained"
		);
	}

	private static void checkOwnerWhitelist() {
		var compilation = compileWithGameHooks(hooks("trigger", CUE.toString()), validCueJson(true));
		check(!compilation.valid(), "game definitions must reject task-event hooks");
		check(
			compilation.problems().stream().anyMatch(problem ->
				problem.code().equals("INVALID_COMBINATION")
					&& problem.pointer().equals("/message_hooks/trigger")
			),
			"owner whitelist failure must retain the exact hook pointer"
		);
	}

	private static void checkLimitsAndReservedArguments() {
		JsonObject tooManyHooks = new JsonObject();
		for (int index = 0; index < 9; index++) {
			tooManyHooks.addProperty("unknown_" + index, CUE.toString());
		}
		var hookLimit = compileWithGameHooks(tooManyHooks, validCueJson(true));
		check(
			hookLimit.problems().stream().anyMatch(problem ->
				problem.code().equals("RESOURCE_LIMIT")
					&& problem.pointer().equals("/message_hooks")
			),
			"hook count above eight must be rejected"
		);

		JsonObject reference = new JsonObject();
		reference.addProperty("cue", CUE.toString());
		JsonObject arguments = new JsonObject();
		arguments.addProperty("game_id", "cannot-shadow");
		for (int index = 0; index < 32; index++) {
			arguments.addProperty("custom_" + index, index);
		}
		reference.add("arguments", arguments);
		JsonObject hooks = new JsonObject();
		hooks.add("start", reference);
		var argumentLimits = compileWithGameHooks(hooks, validCueJson(true));
		check(
			argumentLimits.problems().stream().anyMatch(problem ->
				problem.code().equals("RESOURCE_LIMIT")
					&& problem.pointer().equals("/message_hooks/start/arguments")
			),
			"fixed argument count above 32 must be rejected"
		);
		check(
			argumentLimits.problems().stream().anyMatch(problem ->
				problem.code().equals("INVALID_COMBINATION")
					&& problem.pointer().equals("/message_hooks/start/arguments/game_id")
			),
			"fixed arguments must not shadow authoritative automatic parameters"
		);
	}

	private static void checkReferenceClosure() {
		var missing = compileWithGameHooks(hooks("start", id("missing").toString()), validCueJson(true));
		check(
			missing.problems().stream().anyMatch(problem ->
				problem.code().equals("MISSING_REFERENCE")
					&& problem.pointer().equals("/message_hooks/start")
			),
			"missing cue references must reject the owning core definition"
		);

		var unscoped = compileWithGameHooks(hooks("start", CUE.toString()), validCueJson(false));
		check(
			unscoped.problems().stream().anyMatch(problem ->
				problem.code().equals("CROSS_GAME_REFERENCE")
					&& problem.pointer().equals("/message_hooks/start")
			),
			"hook cues must explicitly belong to the owner's game"
		);
	}

	private static void checkFixedArgumentContract() {
		JsonObject undeclaredReference = new JsonObject();
		undeclaredReference.addProperty("cue", CUE.toString());
		JsonObject undeclaredArguments = new JsonObject();
		undeclaredArguments.addProperty("unknown", "value");
		undeclaredReference.add("arguments", undeclaredArguments);
		JsonObject undeclaredHooks = new JsonObject();
		undeclaredHooks.add("start", undeclaredReference);
		var undeclared = compileWithGameHooks(undeclaredHooks, validCueJson(true));
		check(
			undeclared.problems().stream().anyMatch(problem ->
				problem.code().equals("UNDECLARED_ARGUMENT")
					&& problem.pointer().equals("/message_hooks/start/arguments/unknown")
			),
			"fixed hook arguments must be declared by the referenced cue"
		);

		JsonObject invalidTypeReference = new JsonObject();
		invalidTypeReference.addProperty("cue", CUE.toString());
		JsonObject invalidTypeArguments = new JsonObject();
		invalidTypeArguments.addProperty("priority", "not-an-integer");
		invalidTypeReference.add("arguments", invalidTypeArguments);
		JsonObject invalidTypeHooks = new JsonObject();
		invalidTypeHooks.add("start", invalidTypeReference);
		var invalidType = compileWithGameHooks(invalidTypeHooks, validCueJson(true));
		check(
			invalidType.problems().stream().anyMatch(problem ->
				problem.code().equals("TYPE_MISMATCH")
					&& problem.pointer().equals("/message_hooks/start/arguments/priority")
			),
			"fixed hook values must match their cue parameter types"
		);

		JsonObject cue = JsonParser.parseString(validCueJson(true)).getAsJsonObject();
		JsonObject fieldParameter = new JsonObject();
		fieldParameter.addProperty("type", "identifier");
		fieldParameter.addProperty("identifier_kind", "field");
		cue.getAsJsonObject("parameters").add("field_id", fieldParameter);
		JsonObject missingReference = new JsonObject();
		missingReference.addProperty("cue", CUE.toString());
		JsonObject missingArguments = new JsonObject();
		missingArguments.addProperty("field_id", "pixel_tzz:missing_field");
		missingReference.add("arguments", missingArguments);
		JsonObject missingHooks = new JsonObject();
		missingHooks.add("start", missingReference);
		var missingIdentifier = compileWithGameHooks(missingHooks, cue.toString());
		check(
			missingIdentifier.problems().stream().anyMatch(problem ->
				problem.code().equals("MISSING_REFERENCE")
					&& problem.pointer().equals("/message_hooks/start/arguments/field_id")
			),
			"fixed identifier arguments must resolve inside the same definition generation"
		);
	}

	private static void checkDisabledCueIsolation() {
		String invalidCue = """
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "nodes": "invalid"
			}
			""";
		var compilation = compileWithGameHooks(hooks("start", CUE.toString()), invalidCue);
		check(compilation.valid(), "an optional disabled cue must not reject its hook owner");
		check(
			compilation.snapshot().orElseThrow().messageCatalog().disabled().keySet().stream()
				.anyMatch(key -> key.type() == DefinitionType.MESSAGE_CUE && key.id().equals(CUE)),
			"the disabled cue must remain visible in the isolated message catalog"
		);
	}

	private static DefinitionCompiler.Compilation compileWithGameHooks(
		final JsonObject hooks,
		final String cueJson
	) {
		CompilationFixture fixture = fixture();
		List<Source> sources = fixture.sources().stream().map(source ->
			matches(source, DefinitionType.GAME, "main")
				? mutate(source, root -> root.add("message_hooks", hooks))
				: source
		).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		sources.add(cueSource(CUE, cueJson));
		return DefinitionCompiler.compile(sources, fixture.functions(), fixture.predicates());
	}

	private static CompilationFixture fixture() {
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(
			Path.of("examples", "pixel-tzz-base-datapack")
		);
		return new CompilationFixture(
			new ArrayList<>(loaded.sources().values()),
			loaded.functions(),
			loaded.predicates()
		);
	}

	private static Source mutate(final Source source, final Consumer<JsonObject> mutation) {
		JsonObject root = JsonParser.parseString(source.json()).getAsJsonObject();
		mutation.accept(root);
		return new Source(
			source.type(),
			source.id(),
			source.resource(),
			source.sourcePack(),
			root.toString()
		);
	}

	private static Source cueSource(final Identifier cue, final String json) {
		return new Source(
			DefinitionType.MESSAGE_CUE,
			cue,
			Identifier.fromNamespaceAndPath(
				cue.getNamespace(),
				"pixel_tzz_pro/message_cues/" + cue.getPath() + ".json"
			),
			"message-hook-self-check",
			json
		);
	}

	private static JsonObject hooks(final String event, final String cue) {
		JsonObject hooks = new JsonObject();
		hooks.addProperty(event, cue);
		return hooks;
	}

	private static boolean matches(
		final Source source,
		final DefinitionType type,
		final String path
	) {
		return source.type() == type && source.id().equals(id(path));
	}

	private static String validCueJson(final boolean scoped) {
		return """
			{
			  "format_version": 1,
			  %s
			  "parameters": {
			    "label": {"type": "string"},
			    "priority": {"type": "integer"},
			    "quiet": {"type": "boolean"}
			  },
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "text": {
			        "parts": [
			          {"component": {"text": "hook"}}
			        ]
			      }
			    }
			  ]
			}
			""".formatted(scoped ? "\"game\": \"pixel_tzz:main\"," : "");
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record CompilationFixture(
		List<Source> sources,
		java.util.Set<Identifier> functions,
		java.util.Set<Identifier> predicates
	) {
	}
}
