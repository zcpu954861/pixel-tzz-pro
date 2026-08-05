package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBinding;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Boundary;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingRequest;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ClockValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ComponentValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudPreviewBindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudPreviewBindingSourceAccess.PreviewContext;
import io.github.zcpu954861.pixeltzzpro.server.HudRouteAuthority;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Executable checks for the read-only, registered-definition-only HUD preview source adapter. */
public final class HudPreviewBindingSourceAccessSelfCheck {
	private static final UUID VIEWER = UUID.fromString("10000000-0000-0000-0000-000000003c20");
	private static final Identifier GAME = id("test:main");
	private static final Identifier PHASE = id("test:running");
	private static final Identifier TASK = id("test:warmup");
	private static final Identifier ROLE = id("test:runner");
	private static final Identifier LIFE = id("test:alive");

	private HudPreviewBindingSourceAccessSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		DefinitionSnapshot definitions = definitions();
		checkRegisteredSyntheticValues(definitions);
		checkOneShotAndContextBoundary(definitions);
		checkUnsafeAndSensitiveSourcesFailClosed(definitions);
		checkFixedBoundaryPresets(definitions);
		checkContextMembership(definitions);
		System.out.println("HUD_PREVIEW_BINDING_SOURCE_ACCESS_SELF_CHECK=PASS");
	}

	private static void checkRegisteredSyntheticValues(final DefinitionSnapshot definitions) {
		BindingSourceAccess access = access(definitions, Boundary.NORMAL);
		HudBinding gameName = binding(definitions, "game_name", "value");
		BindingRequest gameRequest = request(gameName, context(definitions));
		check(access.authorized(gameRequest), "registered game fact must authorize");
		SourceValue gameValue = access.resolve(gameRequest);
		check(gameValue.value().orElseThrow() instanceof ComponentValue component
			&& component.value().getString().equals("HUD self-check"),
			"game name preview must use the registered data-pack name");

		HudBinding clock = binding(definitions, "game_clock", "clock");
		BindingRequest clockRequest = request(clock, context(definitions));
		check(access.authorized(clockRequest), "registered clock must authorize");
		check(access.resolve(clockRequest).value().orElseThrow() instanceof ClockValue value
			&& value.serverTick() == 2_000L,
			"preview clock must use the supplied synthetic tick");
	}

	private static void checkOneShotAndContextBoundary(final DefinitionSnapshot definitions) {
		BindingSourceAccess access = access(definitions, Boundary.NORMAL);
		HudBinding binding = binding(definitions, "game_name", "value");
		BindingRequest request = request(binding, context(definitions));
		check(access.resolve(request).value().isEmpty(), "resolve without authorization must fail closed");
		check(access.authorized(request), "registered binding must authorize once");
		check(access.resolve(request).value().isPresent(), "authorized binding must resolve once");
		check(access.resolve(request).value().isEmpty(), "one-shot grant must be consumed");

		HudRouteAuthority.Context wrongAudience = new HudRouteAuthority.Context(
			UUID.randomUUID(),
			GAME,
			Optional.of(PHASE),
			Optional.of(TASK),
			Optional.of(TaskKind.WARMUP),
			Optional.of(TaskStatus.RUNNING),
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
		check(!access.authorized(request(binding, wrongAudience)),
			"a request cannot substitute another preview audience");
	}

	private static void checkUnsafeAndSensitiveSourcesFailClosed(final DefinitionSnapshot definitions) {
		BindingSourceAccess access = access(definitions, Boundary.NORMAL);
		for (String component : List.of("sensitive_name", "score_value", "storage_value")) {
			HudBinding binding = binding(definitions, component, "value");
			BindingRequest request = request(binding, context(definitions));
			check(!access.authorized(request), component + " must fail closed in preview");
			check(access.resolve(request).value().isEmpty(), component + " must remain unreadable");
		}
	}

	private static void checkFixedBoundaryPresets(final DefinitionSnapshot definitions) {
		HudBinding binding = binding(definitions, "game_name", "value");
		BindingRequest request = request(binding, context(definitions));

		BindingSourceAccess missing = access(definitions, Boundary.MISSING);
		check(missing.authorized(request), "missing preset still exercises the registered binding");
		check(missing.resolve(request).value().isEmpty(), "missing preset must supply no value");

		BindingSourceAccess stale = access(definitions, Boundary.STALE);
		check(stale.authorized(request), "stale preset must authorize");
		SourceValue staleValue = stale.resolve(request);
		check(staleValue.stale() && staleValue.ageTicks() == 40L,
			"stale preset must use the fixed server-owned age");

		BindingSourceAccess longText = access(definitions, Boundary.LONG_TEXT);
		check(longText.authorized(request), "long-text preset must authorize");
		ComponentValue value = (ComponentValue)longText.resolve(request).value().orElseThrow();
		check(value.value().getString().contains("服务器固定生成")
			&& value.value().getString().contains("不读取世界"),
			"long-text preset must be a fixed server-owned string");
	}

	private static void checkContextMembership(final DefinitionSnapshot definitions) {
		HudRouteAuthority.Context mismatched = new HudRouteAuthority.Context(
			VIEWER,
			GAME,
			Optional.of(PHASE),
			Optional.of(TASK),
			Optional.of(TaskKind.WARMUP),
			Optional.of(TaskStatus.RUNNING),
			Optional.empty(),
			Optional.empty(),
			Optional.of(LIFE),
			Set.of(),
			Set.of(),
			Set.of(),
			false,
			true,
			true,
			false,
			false,
			Set.of(VIEWER)
		);
		boolean rejected = false;
		try {
			new PreviewContext(
				definitions,
				definitions.games().get(GAME),
				Optional.of(definitions.phases().get(PHASE)),
				Optional.of(definitions.tasks().get(TASK)),
				Optional.of(definitions.roles().get(ROLE)),
				Optional.empty(),
				Optional.of(definitions.lifeStates().get(LIFE)),
				mismatched,
				Boundary.NORMAL,
				2_000L
			);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		check(rejected, "preview context IDs must match registered members of the active game");
	}

	private static BindingSourceAccess access(
		final DefinitionSnapshot definitions,
		final Boundary boundary
	) {
		return HudPreviewBindingSourceAccess.create(new PreviewContext(
			definitions,
			definitions.games().get(GAME),
			Optional.of(definitions.phases().get(PHASE)),
			Optional.of(definitions.tasks().get(TASK)),
			Optional.of(definitions.roles().get(ROLE)),
			Optional.empty(),
			Optional.of(definitions.lifeStates().get(LIFE)),
			context(definitions),
			boundary,
			2_000L
		));
	}

	private static HudRouteAuthority.Context context(final DefinitionSnapshot definitions) {
		return new HudRouteAuthority.Context(
			VIEWER,
			GAME,
			Optional.of(PHASE),
			Optional.of(TASK),
			Optional.of(TaskKind.WARMUP),
			Optional.of(TaskStatus.RUNNING),
			Optional.of(ROLE),
			Optional.empty(),
			Optional.of(LIFE),
			definitions.roles().get(ROLE).tags(),
			Set.of(),
			definitions.lifeStates().get(LIFE).tags(),
			false,
			true,
			true,
			false,
			false,
			Set.of(VIEWER)
		);
	}

	private static DefinitionSnapshot definitions() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(""));
		sources.add(source(DefinitionType.HUD_COMPONENT, "game_name", textComponent("""
			{
			  "value_type":"component",
			  "source":{"type":"game_fact","value":"name"},
			  "on_missing":{"mode":"hide_component"}
			}
			""")));
		sources.add(source(DefinitionType.HUD_COMPONENT, "sensitive_name", textComponent("""
			{
			  "value_type":"component",
			  "source":{"type":"game_fact","value":"name"},
			  "sensitive":true,
			  "on_missing":{"mode":"hide_component"}
			}
			""")));
		sources.add(source(DefinitionType.HUD_COMPONENT, "game_clock", """
			{
			  "format_version":1,
			  "type":"timer",
			  "bindings":{"clock":{
			    "value_type":"clock",
			    "source":{"type":"clock","value":"game"},
			    "on_missing":{"mode":"hide_component"}
			  }},
			  "clock":{"field":"clock"},
			  "direction":"elapsed",
			  "format":"m:ss"
			}
			"""));
		sources.add(source(DefinitionType.HUD_COMPONENT, "score_value", counterComponent("""
			{
			  "value_type":"integer",
			  "source":{"type":"score","objective":"preview_secret","holder":"#hud"},
			  "on_missing":{"mode":"hide_component"}
			}
			""")));
		sources.add(source(DefinitionType.HUD_COMPONENT, "storage_value", textComponent("""
			{
			  "value_type":"component",
			  "source":{"type":"storage","storage":"test:private","path":"secret.value"},
			  "on_missing":{"mode":"hide_component"}
			}
			""")));
		DefinitionCompiler.Compilation compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(compilation.valid(), "preview source definitions must compile: " + compilation.problems());
		return compilation.snapshot().orElseThrow().withGeneration(33L);
	}

	private static String textComponent(final String binding) {
		return """
			{
			  "format_version":1,
			  "type":"text",
			  "bindings":{"value":%s},
			  "text":{"parts":[{"field":{"scope":"component","id":"value"}}]},
			  "layout":{"alignment":"left","max_lines":4,"overflow":"wrap"}
			}
			""".formatted(binding);
	}

	private static String counterComponent(final String binding) {
		return """
			{
			  "format_version":1,
			  "type":"counter",
			  "bindings":{"value":%s},
			  "label":{"text":"预览"},
			  "value":{"field":"value"}
			}
			""".formatted(binding);
	}

	private static HudBinding binding(
		final DefinitionSnapshot definitions,
		final String component,
		final String binding
	) {
		return definitions.hudCatalog().components().get(id("test:" + component)).bindings().get(binding);
	}

	private static BindingRequest request(
		final HudBinding binding,
		final HudRouteAuthority.Context audience
	) {
		return new BindingRequest(binding, binding.source(), audience, Optional.empty());
	}

	private static DefinitionCompiler.Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		return HudSelfCheckFixtures.source(type, path, json);
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
