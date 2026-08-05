package io.github.zcpu954861.pixeltzzpro;

import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionCodecs;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Pure-Java contract checks for the strict V3C parser, codecs and frozen closure. */
public final class HudDefinitionSelfCheck {
	private HudDefinitionSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkStrictParserAndCodec();
		checkIntegratedCatalogAndFreeze();
		System.out.println("HUD definition self-check passed");
	}

	private static void checkStrictParserAndCodec() {
		var duplicate = HudDefinitionParser.parseComponent(
			HudSelfCheckFixtures.id("test:duplicate"),
			"{\"format_version\":1,\"type\":\"separator\",\"type\":\"separator\","+
				"\"orientation\":\"horizontal\",\"thickness\":1,\"color\":\"#40505F\"}"
		);
		HudSelfCheckFixtures.check(
			!duplicate.valid() && duplicate.issues().stream().anyMatch(value -> value.code().equals("JSON_SYNTAX")),
			"duplicate keys must be rejected"
		);
		var unknown = HudDefinitionParser.parseComponent(
			HudSelfCheckFixtures.id("test:unknown"),
			"{\"format_version\":1,\"type\":\"separator\",\"orientation\":\"horizontal\","+
				"\"thickness\":1,\"color\":\"#40505F\",\"mystery\":true}"
		);
		HudSelfCheckFixtures.check(
			!unknown.valid() && unknown.issues().stream().anyMatch(value -> value.code().equals("UNKNOWN_KEY")),
			"unknown keys must be rejected"
		);

		var parsed = HudDefinitionParser.parseComponent(
			HudSelfCheckFixtures.id("test:line"),
			HudSelfCheckFixtures.validHud().getFirst().json()
		);
		HudSelfCheckFixtures.check(parsed.valid(), "valid component must parse: " + parsed.issues());
		var encoded = HudDefinitionCodecs.COMPONENT
			.encodeStart(JsonOps.INSTANCE, parsed.value().orElseThrow())
			.result()
			.orElseThrow();
		var decoded = HudDefinitionCodecs.COMPONENT
			.parse(JsonOps.INSTANCE, encoded)
			.result()
			.orElseThrow();
		HudSelfCheckFixtures.check(
			decoded.equals(parsed.value().orElseThrow()),
			"component codec must round-trip through the strict parser"
		);
	}

	private static void checkIntegratedCatalogAndFreeze() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"hud_profile\": \"test:main\",\n  \"opening_countdown\": {"+
				"\"definition\":\"test:opening\",\"required\":true}"
		));
		sources.addAll(HudSelfCheckFixtures.validHud());
		var compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		HudSelfCheckFixtures.check(
			compilation.valid(),
			"valid V3C catalog must compile: " + compilation.problems()
		);
		var snapshot = compilation.snapshot().orElseThrow().withGeneration(17L);
		HudSelfCheckFixtures.check(snapshot.hudCatalog().components().size() == 1, "component catalog count failed");
		HudSelfCheckFixtures.check(snapshot.hudCatalog().layouts().size() == 2, "layout catalog count failed");
		var dockTransition = snapshot.hudCatalog().layouts()
			.get(HudSelfCheckFixtures.id("test:dock"))
			.transition();
		HudSelfCheckFixtures.check(
			dockTransition.enterSound().orElseThrow().event()
				.equals(HudSelfCheckFixtures.id("minecraft:block.note_block.pling"))
				&& dockTransition.enterSound().orElseThrow().volume() == 0.4D
				&& dockTransition.changeSound().orElseThrow().pitch() == 1.0D,
			"layout transition sounds must parse into the frozen catalog"
		);
		HudSelfCheckFixtures.check(snapshot.hudCatalog().profileUsable(HudSelfCheckFixtures.id("test:main")), "profile must be usable");
		HudSelfCheckFixtures.check(snapshot.hudCatalog().countdownUsable(HudSelfCheckFixtures.id("test:opening")), "countdown must be usable");

		var game = snapshot.games().get(HudSelfCheckFixtures.id("test:main"));
		var countdown = snapshot.hudCatalog().countdowns().get(HudSelfCheckFixtures.id("test:opening"));
		var frozen = CountdownSnapshotCompiler.freeze(snapshot, game, countdown);
		HudSelfCheckFixtures.check(frozen.success(), "countdown closure must freeze: " + frozen.message());
		var restored = CountdownSnapshotCompiler.restore(
			game.id(),
			countdown.id(),
			frozen.frozen().orElseThrow()
		);
		HudSelfCheckFixtures.check(restored.success(), "countdown closure must restore: " + restored.message());
		HudSelfCheckFixtures.check(
			restored.restored().orElseThrow().definitions().generation() == 17L,
			"frozen generation must survive restore"
		);
		HudSelfCheckFixtures.check(
			restored.restored().orElseThrow().timelineSnapshot().tasks().containsKey(HudSelfCheckFixtures.id("test:warmup")),
			"frozen closure must retain the complete timeline"
		);
	}
}
