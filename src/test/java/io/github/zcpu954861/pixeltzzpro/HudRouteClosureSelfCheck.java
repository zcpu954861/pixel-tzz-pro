package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudResourceKey;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudResourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Pure-Java checks for HUD closure isolation, route ties and required countdown gating. */
public final class HudRouteClosureSelfCheck {
	private HudRouteClosureSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkMissingAndCycleIsolation();
		checkRouteTie();
		checkRequiredAndOptionalCountdowns();
		checkCallbackIsolation();
		System.out.println("HUD route closure self-check passed");
	}

	private static void checkMissingAndCycleIsolation() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(""));
		sources.add(HudSelfCheckFixtures.source(DefinitionType.HUD_COMPONENT, "broken", """
			{
			  "format_version": 1,
			  "type": "row",
			  "children": ["test:missing"],
			  "gap": 0,
			  "alignment": "stretch"
			}
			"""));
		sources.add(HudSelfCheckFixtures.source(DefinitionType.HUD_LAYOUT, "broken", """
			{
			  "format_version": 1,
			  "surface": "dock",
			  "root": "test:broken",
			  "size": {
			    "minimum_width": 100,
			    "preferred_width": 120,
			    "maximum_width": 140,
			    "maximum_height": 40,
			    "growth": "up"
			  }
			}
			"""));
		var compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		HudSelfCheckFixtures.check(compilation.valid(), "optional broken HUD must not reject the game generation");
		var catalog = compilation.snapshot().orElseThrow().hudCatalog();
		HudSelfCheckFixtures.check(
			catalog.disabled().containsKey(new HudResourceKey(HudResourceKind.COMPONENT, HudSelfCheckFixtures.id("test:broken"))),
			"missing child must disable its component"
		);
		HudSelfCheckFixtures.check(
			catalog.disabled().containsKey(new HudResourceKey(HudResourceKind.LAYOUT, HudSelfCheckFixtures.id("test:broken"))),
			"component failure must propagate to its layout"
		);
	}

	private static void checkRouteTie() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"hud_profile\": \"test:tied\""
		));
		sources.addAll(HudSelfCheckFixtures.validHud().subList(0, 2));
		sources.add(HudSelfCheckFixtures.source(DefinitionType.HUD_PROFILE, "tied", """
			{
			  "format_version": 1,
			  "routes": [
			    {"id":"a","tier":"context","priority":10,"layout":"test:dock"},
			    {"id":"b","tier":"context","priority":10,"layout":"test:dock"}
			  ]
			}
			"""));
		var compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		HudSelfCheckFixtures.check(compilation.valid(), "ambiguous optional profile must remain isolated");
		var catalog = compilation.snapshot().orElseThrow().hudCatalog();
		HudSelfCheckFixtures.check(!catalog.profileUsable(HudSelfCheckFixtures.id("test:tied")), "tied profile must fail closed");
		HudSelfCheckFixtures.check(
			catalog.gameDiagnostics().containsKey(HudSelfCheckFixtures.id("test:main")),
			"game referencing a tied profile must receive diagnostics"
		);
	}

	private static void checkRequiredAndOptionalCountdowns() {
		var required = DefinitionCompiler.compile(
			HudSelfCheckFixtures.core(",\n  \"opening_countdown\": {\"definition\":\"test:missing\",\"required\":true}"),
			Set.of(),
			Set.of()
		);
		HudSelfCheckFixtures.check(required.valid(), "required catalog error must not reject reload");
		HudSelfCheckFixtures.check(
			required.snapshot().orElseThrow().hudCatalog().gameStartBlocked(HudSelfCheckFixtures.id("test:main")),
			"required missing countdown must block approval"
		);

		var optional = DefinitionCompiler.compile(
			HudSelfCheckFixtures.core(",\n  \"opening_countdown\": {\"definition\":\"test:missing\",\"required\":false}"),
			Set.of(),
			Set.of()
		);
		HudSelfCheckFixtures.check(optional.valid(), "optional missing countdown must compile");
		HudSelfCheckFixtures.check(
			!optional.snapshot().orElseThrow().hudCatalog().gameStartBlocked(HudSelfCheckFixtures.id("test:main")),
			"optional missing countdown must degrade to immediate start"
		);
	}

	private static void checkCallbackIsolation() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"opening_countdown\": {\"definition\":\"test:opening\",\"required\":true}"
		));
		sources.addAll(HudSelfCheckFixtures.validHud().subList(0, 3));
		sources.add(HudSelfCheckFixtures.source(DefinitionType.COUNTDOWN, "opening", """
			{
			  "format_version": 1,
			  "name": {"text": "Opening"},
			  "duration": "10s",
			  "layout": "test:countdown",
			  "callbacks": {
			    "start": [
			      {"id":"required","scope":"global","function":"test:missing_required","required":true},
			      {"id":"optional","scope":"global","function":"test:missing_optional","required":false}
			    ]
			  }
			}
			"""));
		var missingRequired = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		HudSelfCheckFixtures.check(missingRequired.valid(), "callback failure must remain catalog-local");
		HudSelfCheckFixtures.check(
			missingRequired.snapshot().orElseThrow().hudCatalog().gameStartBlocked(HudSelfCheckFixtures.id("test:main")),
			"missing required callback must block approval"
		);

		var optionalOnly = new ArrayList<>(sources);
		optionalOnly.set(optionalOnly.size() - 1, HudSelfCheckFixtures.source(DefinitionType.COUNTDOWN, "opening", """
			{
			  "format_version": 1,
			  "name": {"text": "Opening"},
			  "duration": "10s",
			  "layout": "test:countdown",
			  "callbacks": {
			    "start": [
			      {"id":"optional","scope":"global","function":"test:missing_optional","required":false}
			    ]
			  }
			}
			"""));
		var optional = DefinitionCompiler.compile(optionalOnly, Set.of(), Set.of());
		HudSelfCheckFixtures.check(optional.valid(), "optional callback must be removable");
		var countdown = optional.snapshot().orElseThrow().hudCatalog().countdowns().get(HudSelfCheckFixtures.id("test:opening"));
		HudSelfCheckFixtures.check(countdown != null, "optional callback must not disable countdown");
		HudSelfCheckFixtures.check(
			countdown.callbacks().values().stream().flatMap(List::stream).findAny().isEmpty(),
			"missing optional callback must be removed from executable definition"
		);
	}
}
