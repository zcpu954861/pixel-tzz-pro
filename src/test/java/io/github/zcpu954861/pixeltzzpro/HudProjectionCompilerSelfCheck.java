package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingRequest;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ClockValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ComponentValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.DecimalValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.StringValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.Value;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.CountdownProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.Projection;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionCode;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudRouteAuthority;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** End-to-end checks for least-privilege V3C HUD/countdown projection documents. */
public final class HudProjectionCompilerSelfCheck {
	private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final Identifier GAME = id("test:main");

	private HudProjectionCompilerSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		checkResolvedDock(definitions);
		checkDeniedBindingDoesNotRead(definitions);
		checkEmptyAndFailClosedTrees(definitions);
		checkCountdownProjection(definitions);
		checkDocumentLimit(definitions);
		System.out.println("HUD projection compiler self-check passed");
	}

	private static void checkResolvedDock(final DefinitionSnapshot definitions) {
		RecordingAccess access = RecordingAccess.values(Map.of(
			"title", new ComponentValue(Component.literal("备用电源")),
			"current", new DecimalValue(7.0D),
			"maximum", new DecimalValue(9.0D),
			"clock", new ClockValue(1_000L, 245.0D, 1.0D, false)
		));
		Projection projection = HudProjectionCompiler.projectDock(
			definitions,
			id("test:main"),
			projectionContext(access)
		);
		check(projection.code() == ProjectionCode.REPLACE, "resolved dock must replace: " + projection.diagnostics());
		check(access.resolveCalls == 4, "each authorized dynamic field must resolve exactly once");
		JsonObject document = document(projection);
		check(document.get("surface").getAsString().equals("dock"), "dock surface missing");
		check(document.get("layout_id").getAsString().equals("test:dock"), "dock layout mismatch");
		JsonObject transition = document.getAsJsonArray("layout_variants")
			.get(0)
			.getAsJsonObject()
			.getAsJsonObject("transition");
		check(
			transition.getAsJsonObject("enter_sound").get("event").getAsString()
				.equals("minecraft:block.note_block.pling")
				&& transition.getAsJsonObject("enter_sound").get("volume").getAsDouble() == 0.4D,
			"HUD enter sound was not preserved in the least-privilege projection"
		);
		check(
			transition.getAsJsonObject("change_sound").get("event").getAsString()
				.equals("minecraft:block.note_block.hat"),
			"HUD change sound was not preserved in the least-privilege projection"
		);

		JsonObject text = node(document, "text");
		Component rendered = ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, text.get("value"))
			.getOrThrow();
		check(rendered.getString().equals("当前任务 备用电源"), "dynamic text field was not resolved");

		JsonObject progress = node(document, "progress");
		check(progress.get("current").getAsDouble() == 7.0D, "progress current mismatch");
		check(progress.get("maximum").getAsDouble() == 9.0D, "progress maximum mismatch");
		check(progress.get("label").toString().contains("7/9"), "progress label mismatch");

		JsonObject timer = node(document, "timer").getAsJsonObject("timer");
		check(timer.get("server_tick").getAsLong() == 1_000L, "timer server tick mismatch");
		check(timer.get("base_ticks").getAsDouble() == 245.0D, "timer base tick mismatch");
		check(timer.get("rate").getAsDouble() == -1.0D, "timer rate mismatch");
		check(!timer.get("paused").getAsBoolean(), "timer paused flag mismatch");

		Set<String> keys = new HashSet<>();
		collectKeys(document, keys);
		for (String forbidden : Set.of(
			"source", "objective", "holder", "storage", "path", "target", "selector", "function"
		)) {
			check(!keys.contains(forbidden), "server-only key leaked into HUD projection: " + forbidden);
		}
		String encoded = document.toString();
		check(!encoded.contains("test:private_state"), "storage identifier leaked into HUD projection");
		check(!encoded.contains("secret.payload"), "storage path leaked into HUD projection");
		check(!encoded.contains("private_score"), "score objective leaked into HUD projection");
	}

	private static void checkDeniedBindingDoesNotRead(final DefinitionSnapshot definitions) {
		RecordingAccess access = RecordingAccess.values(Map.of(
			"secret", new StringValue("must-not-be-read")
		));
		Projection projection = HudProjectionCompiler.projectDock(
			definitions,
			id("test:denied"),
			projectionContext(access)
		);
		check(projection.code() == ProjectionCode.FAIL_CLOSED, "missing critical content must fail closed");
		check(access.authorizedCalls == 0, "binding audience denial must precede source authorization");
		check(access.resolveCalls == 0, "binding audience denial must never read source value");
	}

	private static void checkEmptyAndFailClosedTrees(final DefinitionSnapshot definitions) {
		Projection empty = HudProjectionCompiler.projectDock(
			definitions,
			id("test:empty"),
			projectionContext(RecordingAccess.denied())
		);
		check(empty.code() == ProjectionCode.CLEAR, "fully hidden optional tree must clear");

		Projection degraded = HudProjectionCompiler.projectDock(
			definitions,
			id("test:degrade"),
			projectionContext(RecordingAccess.denied())
		);
		check(degraded.code() == ProjectionCode.FAIL_CLOSED, "failed degradation tree must fail closed");
		check(
			degraded.diagnostics().stream().anyMatch(value -> value.contains("critical HUD content")),
			"failed degradation must retain a bounded diagnostic"
		);
	}

	private static void checkCountdownProjection(final DefinitionSnapshot definitions) {
		CountdownDefinition countdown = definitions.hudCatalog().countdowns().get(id("test:opening"));
		RecordingAccess delegate = RecordingAccess.denied();
		Projection projection = HudProjectionCompiler.projectCountdown(
			definitions,
			countdown,
			new CountdownProjectionContext(
				projectionContext(delegate),
				UUID.fromString("20000000-0000-0000-0000-000000000001"),
				4_000L,
				200L,
				100L,
				false,
				"running",
				"",
				0,
				countdown.name()
			)
		);
		check(projection.code() == ProjectionCode.REPLACE, "countdown must replace: " + projection.diagnostics());
		check(delegate.authorizedCalls == 0, "built-in countdown clock must not consult delegate authorization");
		check(delegate.resolveCalls == 0, "built-in countdown clock must not consult delegate values");
		JsonObject document = document(projection);
		check(document.get("surface").getAsString().equals("countdown"), "countdown surface missing");
		check(document.get("total_ticks").getAsLong() == 200L, "countdown total mismatch");
		JsonObject remaining = document.getAsJsonObject("remaining");
		check(remaining.get("server_tick").getAsLong() == 4_000L, "remaining server tick mismatch");
		check(remaining.get("base_ticks").getAsDouble() == 100.0D, "remaining base ticks mismatch");
		check(remaining.get("rate").getAsDouble() == -1.0D, "remaining rate mismatch");
		check(!remaining.get("paused").getAsBoolean(), "remaining paused mismatch");
		JsonObject nodeTimer = node(document, "timer").getAsJsonObject("timer");
		check(nodeTimer.equals(remaining), "countdown clock binding and authoritative remaining anchor diverged");
	}

	private static void checkDocumentLimit(final DefinitionSnapshot definitions) {
		Map<String, Value> values = new java.util.LinkedHashMap<>();
		for (int index = 0; index < 8; index++) {
			values.put("large_" + index, new StringValue("界".repeat(8_192)));
		}
		Projection projection = HudProjectionCompiler.projectDock(
			definitions,
			id("test:oversized"),
			projectionContext(RecordingAccess.values(values))
		);
		check(projection.code() == ProjectionCode.FAIL_CLOSED, "oversized projection must fail closed");
		check(projection.document().isEmpty(), "oversized projection must not retain partial bytes");
		check(
			projection.diagnostics().stream().anyMatch(value -> value.contains("131072 UTF-8 bytes")),
			"oversized projection diagnostic missing"
		);
	}

	private static DefinitionSnapshot definitions() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"hud_profile\": \"test:main\",\n  \"opening_countdown\": {"+
				"\"definition\":\"test:opening\",\"required\":true}"
		));
		sources.add(source(DefinitionType.HUD_COMPONENT, "task_title", """
			{
			  "format_version": 1,
			  "type": "text",
			  "bindings": {
			    "title": {
			      "value_type": "component",
			      "source": {"type":"storage","storage":"test:private_state","path":"secret.payload"},
			      "on_missing": {"mode":"hide_component"}
			    }
			  },
			  "text": {"parts":[
			    {"component":{"text":"当前任务 "}},
			    {"field":{"scope":"component","id":"title"}}
			  ]},
			  "layout": {"alignment":"left","max_lines":2,"overflow":"wrap"}
			}
			"""));
		sources.add(source(DefinitionType.HUD_COMPONENT, "task_progress", """
			{
			  "format_version": 1,
			  "type": "progress",
			  "bindings": {
			    "current": {
			      "value_type":"decimal",
			      "source":{"type":"score","objective":"private_score","holder":"#hud"},
			      "on_missing":{"mode":"hide_component"}
			    },
			    "maximum": {
			      "value_type":"decimal",
			      "source":{"type":"task_fact","value":"progress_maximum"},
			      "on_missing":{"mode":"hide_component"}
			    }
			  },
			  "current":{"field":"current"},
			  "maximum":{"field":"maximum"},
			  "orientation":"horizontal",
			  "segments":0,
			  "label":"current_over_max",
			  "interpolation":"smooth"
			}
			"""));
		sources.add(source(DefinitionType.HUD_COMPONENT, "task_clock", timer("task")));
		sources.add(source(DefinitionType.HUD_COMPONENT, "root", """
			{
			  "format_version":1,
			  "type":"column",
			  "children":["test:task_title","test:task_progress","test:task_clock"],
			  "gap":4,
			  "alignment":"stretch"
			}
			"""));
		sources.add(layout("dock", "dock", "test:root"));
		sources.add(profile("main", "test:dock"));

		sources.add(source(DefinitionType.HUD_COMPONENT, "countdown_clock", timer("countdown")));
		sources.add(source(DefinitionType.HUD_COMPONENT, "countdown_root", """
			{
			  "format_version":1,
			  "type":"column",
			  "children":["test:countdown_clock"],
			  "gap":0,
			  "alignment":"center"
			}
			"""));
		sources.add(layout("countdown", "countdown", "test:countdown_root"));
		sources.add(source(DefinitionType.COUNTDOWN, "opening", """
			{
			  "format_version":1,
			  "name":{"text":"正式开局"},
			  "duration":"10s",
			  "layout":"test:countdown"
			}
			"""));

		sources.add(source(DefinitionType.HUD_COMPONENT, "denied_text", """
			{
			  "format_version":1,
			  "type":"text",
			  "priority":"critical",
			  "bindings":{
			    "secret":{
			      "value_type":"string",
			      "source":{"type":"storage","storage":"test:secret","path":"payload"},
			      "audience":{"source":"current_host"},
			      "on_missing":{"mode":"hide_component"}
			    }
			  },
			  "text":{"parts":[{"field":{"scope":"component","id":"secret"}}]},
			  "layout":{"alignment":"left","max_lines":1,"overflow":"ellipsis"}
			}
			"""));
		sources.add(layout("denied", "dock", "test:denied_text"));
		sources.add(profile("denied", "test:denied"));

		sources.add(source(DefinitionType.HUD_COMPONENT, "empty_text", missingText(
			"hide_component",
			""
		)));
		sources.add(layout("empty", "dock", "test:empty_text"));
		sources.add(profile("empty", "test:empty"));

		sources.add(source(DefinitionType.HUD_COMPONENT, "degrade_source", missingText(
			"degradation_layout",
			",\"layout\":\"test:bad_degraded\""
		)));
		sources.add(source(DefinitionType.HUD_COMPONENT, "bad_critical", """
			{
			  "format_version":1,
			  "type":"text",
			  "priority":"critical",
			  "bindings":{
			    "value":{
			      "value_type":"string",
			      "source":{"type":"task_fact","value":"unavailable"},
			      "on_missing":{"mode":"hide_component"}
			    }
			  },
			  "text":{"parts":[{"field":{"scope":"component","id":"value"}}]},
			  "layout":{"alignment":"left","max_lines":1,"overflow":"ellipsis"}
			}
			"""));
		sources.add(layout("degrade_start", "dock", "test:degrade_source"));
		sources.add(layout("bad_degraded", "dock", "test:bad_critical"));
		sources.add(profile("degrade", "test:degrade_start"));

		sources.add(source(DefinitionType.HUD_COMPONENT, "oversized_text", oversizedText()));
		sources.add(layout("oversized", "dock", "test:oversized_text"));
		sources.add(profile("oversized", "test:oversized"));

		DefinitionCompiler.Compilation compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(compilation.valid(), "projection catalog must compile: " + compilation.problems());
		DefinitionSnapshot snapshot = compilation.snapshot().orElseThrow().withGeneration(31L);
		for (String profile : List.of("main", "denied", "empty", "degrade", "oversized")) {
			check(snapshot.hudCatalog().profileUsable(id("test:" + profile)), "profile disabled: " + profile);
		}
		check(snapshot.hudCatalog().countdownUsable(id("test:opening")), "countdown disabled");
		return snapshot;
	}

	private static String timer(final String clock) {
		return """
			{
			  "format_version":1,
			  "type":"timer",
			  "bindings":{
			    "clock":{
			      "value_type":"clock",
			      "source":{"type":"clock","value":"%s"},
			      "on_missing":{"mode":"hide_component"}
			    }
			  },
			  "clock":{"field":"clock"},
			  "direction":"remaining",
			  "format":"m:ss"
			}
			""".formatted(clock);
	}

	private static String missingText(final String mode, final String extra) {
		return """
			{
			  "format_version":1,
			  "type":"text",
			  "bindings":{
			    "value":{
			      "value_type":"string",
			      "source":{"type":"task_fact","value":"missing"},
			      "on_missing":{"mode":"%s"%s}
			    }
			  },
			  "text":{"parts":[{"field":{"scope":"component","id":"value"}}]},
			  "layout":{"alignment":"left","max_lines":1,"overflow":"ellipsis"}
			}
			""".formatted(mode, extra);
	}

	private static String oversizedText() {
		StringBuilder bindings = new StringBuilder();
		StringBuilder parts = new StringBuilder();
		for (int index = 0; index < 8; index++) {
			if (index > 0) {
				bindings.append(',');
				parts.append(',');
			}
			bindings.append("\"large_").append(index).append("\":{")
				.append("\"value_type\":\"string\",")
				.append("\"source\":{\"type\":\"game_fact\",\"value\":\"large_")
				.append(index).append("\"},")
				.append("\"on_missing\":{\"mode\":\"hide_component\"}} ");
			parts.append("{\"field\":{\"scope\":\"component\",\"id\":\"large_")
				.append(index).append("\"}}");
		}
		return "{"+
			"\"format_version\":1,"+
			"\"type\":\"text\","+
			"\"bindings\":{" + bindings + "},"+
			"\"text\":{\"parts\":[" + parts + "]},"+
			"\"layout\":{\"alignment\":\"left\",\"max_lines\":2,\"overflow\":\"wrap\"}"+
			"}";
	}

	private static DefinitionCompiler.Source layout(
		final String id,
		final String surface,
		final String root
	) {
		String growth = surface.equals("dock") ? "up" : "center";
		return source(DefinitionType.HUD_LAYOUT, id, """
			{
			  "format_version":1,
			  "surface":"%s",
			  "root":"%s",
			  "size":{
			    "minimum_width":100,
			    "preferred_width":120,
			    "maximum_width":160,
			    "maximum_height":80,
			    "growth":"%s"
			  },
			  "transition":{
			    "enter":"fade",
			    "change":"crossfade_values",
			    "exit":"fade",
			    "enter_sound":{
			      "event":"minecraft:block.note_block.pling",
			      "volume":0.4,
			      "pitch":1.1
			    },
			    "change_sound":{
			      "event":"minecraft:block.note_block.hat",
			      "volume":0.2,
			      "pitch":1.0
			    }
			  }
			}
			""".formatted(surface, root, growth));
	}

	private static DefinitionCompiler.Source profile(final String id, final String layout) {
		return source(DefinitionType.HUD_PROFILE, id, """
			{"format_version":1,"default_layout":"%s"}
			""".formatted(layout));
	}

	private static DefinitionCompiler.Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		return HudSelfCheckFixtures.source(type, path, json);
	}

	private static ProjectionContext projectionContext(final BindingSourceAccess access) {
		return new ProjectionContext(new HudRouteAuthority.Context(
			PLAYER,
			GAME,
			Optional.of(id("test:running")),
			Optional.of(id("test:warmup")),
			Optional.of(TaskKind.WARMUP),
			Optional.of(TaskStatus.RUNNING),
			Optional.of(id("test:runner")),
			Optional.empty(),
			Optional.of(id("test:alive")),
			Set.of(id("test:active_participant")),
			Set.of(),
			Set.of(),
			false,
			true,
			true,
			false,
			false,
			Set.of(PLAYER)
		), access);
	}

	private static JsonObject document(final Projection projection) {
		return JsonParser.parseString(new String(
			projection.document().orElseThrow(),
			StandardCharsets.UTF_8
		)).getAsJsonObject();
	}

	private static JsonObject node(final JsonObject document, final String type) {
		for (JsonElement element : document.getAsJsonArray("nodes")) {
			JsonObject node = element.getAsJsonObject();
			if (node.get("type").getAsString().equals(type)) {
				return node;
			}
		}
		throw new AssertionError("missing projected node type " + type);
	}

	private static void collectKeys(final JsonElement value, final Set<String> keys) {
		if (value.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
				keys.add(entry.getKey());
				collectKeys(entry.getValue(), keys);
			}
		} else if (value.isJsonArray()) {
			value.getAsJsonArray().forEach(element -> collectKeys(element, keys));
		}
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class RecordingAccess implements BindingSourceAccess {
		private final boolean authorized;
		private final Map<String, Value> values;
		private int authorizedCalls;
		private int resolveCalls;

		private RecordingAccess(final boolean authorized, final Map<String, Value> values) {
			this.authorized = authorized;
			this.values = Map.copyOf(values);
		}

		private static RecordingAccess values(final Map<String, Value> values) {
			return new RecordingAccess(true, values);
		}

		private static RecordingAccess denied() {
			return new RecordingAccess(false, Map.of());
		}

		@Override
		public boolean authorized(final BindingRequest request) {
			this.authorizedCalls++;
			return this.authorized;
		}

		@Override
		public SourceValue resolve(final BindingRequest request) {
			this.resolveCalls++;
			return Optional.ofNullable(this.values.get(request.binding().id()))
				.map(SourceValue::current)
				.orElseGet(SourceValue::missing);
		}
	}
}
