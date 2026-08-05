package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TextNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Mode;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingRequest;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.CountdownProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.Projection;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionCode;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudRouteAuthority;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Executable authority checks for data-pack registered HUD layout variants on the v13 wire. */
public final class HudLayoutVariantProtocolSelfCheck {
	private static final UUID PLAYER = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID GAME_INSTANCE = UUID.fromString("30000000-0000-0000-0000-000000000002");
	private static final UUID COUNTDOWN_INSTANCE = UUID.fromString("30000000-0000-0000-0000-000000000003");

	private HudLayoutVariantProtocolSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		checkDockForestAndPatch(definitions);
		checkMalformedClosuresFailClosed(definitions);
		checkCountdownVariants(definitions);
		ClientHudRuntime.clearAll();
		System.out.println("HUD_LAYOUT_VARIANT_PROTOCOL_SELF_CHECK=PASS");
	}

	private static void checkDockForestAndPatch(final DefinitionSnapshot definitions) {
		Projection projection = HudProjectionCompiler.projectDock(
			definitions,
			id("test:main"),
			context()
		);
		check(projection.code() == ProjectionCode.REPLACE, "dock variants must project: " + projection.diagnostics());
		JsonObject document = document(projection);
		JsonArray variants = document.getAsJsonArray("layout_variants");
		check(variants.size() == 2, "only the authorized degradation chain may cross the wire");
		JsonObject primary = variants.get(0).getAsJsonObject();
		JsonObject minimal = variants.get(1).getAsJsonObject();
		check(primary.get("layout_id").getAsString().equals("test:main_layout"), "primary layout id missing");
		check(primary.has("normal_root") && primary.has("compact_root") && primary.has("summary_root"),
			"all registered semantic roots must be projected");
		check(primary.get("degradation_layout").getAsString().equals("test:minimal"),
			"authorized degradation edge missing");
		check(primary.getAsJsonObject("size").get("preferred_width").getAsInt() == 212,
			"data-pack layout size did not reach the wire");
		check(primary.getAsJsonObject("transition").get("enter").getAsString().equals("rise_fade"),
			"data-pack transition did not reach the wire");
		check(minimal.get("layout_id").getAsString().equals("test:minimal"), "minimal layout missing");
		check(!minimal.has("degradation_layout"), "unauthorized degradation edge must be removed");
		String encoded = document.toString();
		check(!encoded.contains("test:secret") && !encoded.contains("SECRET"),
			"unauthorized variant subtree leaked into the shared node forest");

		ClientHudRuntime.clearAll();
		check(ClientHudRuntime.acceptReplace(GAME_INSTANCE, 31L, 1L, 1L, 1L, bytes(document)).accepted(),
			"client rejected an authorized variant forest");
		ClientHudSnapshot snapshot = ClientHudRuntime.snapshot().orElseThrow();
		check(snapshot.layoutVariants().size() == 2, "client lost the authoritative variant chain");
		check(snapshot.layoutVariants().getFirst().root(Mode.COMPACT).isPresent(),
			"client lost the registered compact root");

		JsonArray patchedNodes = document.getAsJsonArray("nodes").deepCopy();
		boolean changed = false;
		for (JsonElement element : patchedNodes) {
			JsonObject node = element.getAsJsonObject();
			if (node.toString().contains("COMPACT")) {
				node.add("value", JsonParser.parseString("{\"text\":\"COMPACT UPDATED\"}"));
				changed = true;
				break;
			}
		}
		check(changed, "compact variant test node not found");
		JsonObject patch = new JsonObject();
		patch.addProperty("format_version", 1);
		patch.addProperty("surface", "dock");
		patch.add("nodes", patchedNodes);
		check(ClientHudRuntime.acceptPatch(GAME_INSTANCE, 31L, 1L, 1L, 1L, 2L, bytes(patch)).accepted(),
			"value-only patch must update the entire shared variant forest");
		check(ClientHudRuntime.snapshot().orElseThrow().nodes().values().stream()
			.filter(TextNode.class::isInstance)
			.map(TextNode.class::cast)
			.anyMatch(value -> value.value().getString().equals("COMPACT UPDATED")),
			"patched compact value did not reach the client model");
	}

	private static void checkMalformedClosuresFailClosed(final DefinitionSnapshot definitions) {
		JsonObject valid = document(HudProjectionCompiler.projectDock(definitions, id("test:main"), context()));

		JsonObject missingRoot = valid.deepCopy();
		missingRoot.getAsJsonArray("layout_variants")
			.get(0).getAsJsonObject()
			.addProperty("compact_root", "test:unknown@variant");
		ClientHudRuntime.clearAll();
		check(!ClientHudRuntime.acceptReplace(GAME_INSTANCE, 31L, 1L, 1L, 1L, bytes(missingRoot)).accepted(),
			"unknown registered root must fail closed");

		JsonObject cycle = valid.deepCopy();
		JsonArray variants = cycle.getAsJsonArray("layout_variants");
		variants.get(1).getAsJsonObject().addProperty("degradation_layout", "test:main_layout");
		ClientHudRuntime.clearAll();
		check(!ClientHudRuntime.acceptReplace(GAME_INSTANCE, 31L, 1L, 1L, 1L, bytes(cycle)).accepted(),
			"cyclic degradation closure must fail closed");

		JsonObject orphan = valid.deepCopy();
		JsonObject secret = new JsonObject();
		secret.addProperty("id", "test:secret@orphan");
		secret.addProperty("type", "text");
		secret.addProperty("priority", "primary");
		secret.add("style", new JsonObject());
		JsonObject control = new JsonObject();
		control.add("label", JsonParser.parseString("{\"text\":\"secret\"}"));
		control.addProperty("visible", true);
		control.addProperty("allow_hide", false);
		control.addProperty("allow_compact", false);
		secret.add("client_control", control);
		secret.add("value", JsonParser.parseString("{\"text\":\"SECRET\"}"));
		secret.addProperty("max_lines", 1);
		secret.addProperty("overflow", "ellipsis");
		secret.addProperty("alignment", "start");
		orphan.getAsJsonArray("nodes").add(secret);
		ClientHudRuntime.clearAll();
		check(!ClientHudRuntime.acceptReplace(GAME_INSTANCE, 31L, 1L, 1L, 1L, bytes(orphan)).accepted(),
			"unrooted variant subtree must fail closed");
	}

	private static void checkCountdownVariants(final DefinitionSnapshot definitions) {
		CountdownDefinition countdown = definitions.hudCatalog().countdowns().get(id("test:opening"));
		Projection projection = HudProjectionCompiler.projectCountdown(
			definitions,
			countdown,
			new CountdownProjectionContext(
				context(),
				COUNTDOWN_INSTANCE,
				2_000L,
				200L,
				120L,
				false,
				"running",
				"",
				0,
				countdown.name()
			)
		);
		check(projection.code() == ProjectionCode.REPLACE, "countdown variants must project");
		JsonObject document = document(projection);
		check(document.getAsJsonArray("layout_variants").get(0).getAsJsonObject().has("compact_root"),
			"countdown compact root did not reach the wire");
		ClientHudRuntime.clearAll();
		check(ClientHudRuntime.acceptCountdownReplace(GAME_INSTANCE, 31L, 1L, 1L, 1L, bytes(document)).accepted(),
			"client rejected countdown variants");
		check(ClientHudRuntime.countdownSnapshot().orElseThrow().layoutVariants().getFirst()
			.root(Mode.COMPACT).isPresent(), "client lost countdown compact root");
	}

	private static DefinitionSnapshot definitions() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"hud_profile\":\"test:main\",\n  \"opening_countdown\":{"+
				"\"definition\":\"test:opening\",\"required\":true}"
		));
		for (String name : List.of("normal", "compact", "summary", "minimal_root", "secret_root", "countdown_root", "countdown_compact")) {
			sources.add(text(name, name.toUpperCase(java.util.Locale.ROOT)));
		}
		sources.add(source(DefinitionType.HUD_LAYOUT, "main_layout", """
			{
			  "format_version":1,
			  "surface":"dock",
			  "root":"test:normal",
			  "compact_root":"test:compact",
			  "summary_root":"test:summary",
			  "degradation_layout":"test:minimal",
			  "size":{"minimum_width":132,"preferred_width":212,"maximum_width":244,"maximum_height":124,"growth":"up"},
			  "transition":{"enter":"rise_fade","change":"crossfade_values","exit":"fade"}
			}
			"""));
		sources.add(source(DefinitionType.HUD_LAYOUT, "minimal", """
			{
			  "format_version":1,
			  "surface":"dock",
			  "root":"test:minimal_root",
			  "degradation_layout":"test:secret",
			  "size":{"minimum_width":100,"preferred_width":132,"maximum_width":176,"maximum_height":48,"growth":"up"},
			  "transition":{"enter":"fade","change":"none","exit":"fade"}
			}
			"""));
		sources.add(source(DefinitionType.HUD_LAYOUT, "secret", """
			{
			  "format_version":1,
			  "surface":"dock",
			  "audience":{"source":"current_host"},
			  "root":"test:secret_root",
			  "size":{"minimum_width":100,"preferred_width":120,"maximum_width":160,"maximum_height":40,"growth":"up"}
			}
			"""));
		sources.add(source(DefinitionType.HUD_PROFILE, "main", """
			{"format_version":1,"default_layout":"test:main_layout"}
			"""));
		sources.add(source(DefinitionType.HUD_LAYOUT, "countdown", """
			{
			  "format_version":1,
			  "surface":"countdown",
			  "root":"test:countdown_root",
			  "compact_root":"test:countdown_compact",
			  "size":{"minimum_width":132,"preferred_width":176,"maximum_width":224,"maximum_height":104,"growth":"center"},
			  "transition":{"enter":"focus_fade","change":"crossfade_values","exit":"fade"}
			}
			"""));
		sources.add(source(DefinitionType.COUNTDOWN, "opening", """
			{"format_version":1,"name":{"text":"Opening"},"duration":"10s","layout":"test:countdown"}
			"""));
		DefinitionCompiler.Compilation compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(compilation.valid(), "variant definitions must compile: " + compilation.problems());
		return compilation.snapshot().orElseThrow().withGeneration(31L);
	}

	private static DefinitionCompiler.Source text(final String id, final String value) {
		return source(DefinitionType.HUD_COMPONENT, id, """
			{
			  "format_version":1,
			  "type":"text",
			  "text":{"parts":[{"component":{"text":"%s"}}]},
			  "layout":{"alignment":"left","max_lines":1,"overflow":"ellipsis"}
			}
			""".formatted(value));
	}

	private static DefinitionCompiler.Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		return HudSelfCheckFixtures.source(type, path, json);
	}

	private static ProjectionContext context() {
		return new ProjectionContext(new HudRouteAuthority.Context(
			PLAYER,
			id("test:main"),
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
		), DeniedAccess.INSTANCE);
	}

	private static JsonObject document(final Projection projection) {
		check(projection.code() == ProjectionCode.REPLACE, "projection is not a replace");
		return JsonParser.parseString(new String(
			projection.document().orElseThrow(),
			StandardCharsets.UTF_8
		)).getAsJsonObject();
	}

	private static byte[] bytes(final JsonObject value) {
		return value.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private enum DeniedAccess implements BindingSourceAccess {
		INSTANCE;

		@Override
		public boolean authorized(final BindingRequest request) {
			return false;
		}

		@Override
		public SourceValue resolve(final BindingRequest request) {
			return SourceValue.missing();
		}
	}
}
