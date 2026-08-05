package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.AllowOnly;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.BlockMode;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownCallbackScope;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownCallbackSlot;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.DamageMode;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.DisconnectAction;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.FreezeMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Strict parser and integrated reference checks for complete V3C countdown definitions. */
public final class CountdownDefinitionSelfCheck {
	private CountdownDefinitionSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkCompleteDefinition();
		checkStrictFieldRejections();
		checkCountdownLayoutSurface();
		checkRequiredCallbackResolution();
		System.out.println("Countdown definition self-check passed");
	}

	private static void checkCompleteDefinition() {
		var parsed = HudDefinitionParser.parseCountdown(
			HudSelfCheckFixtures.id("test:complete"),
			completeCountdown()
		);
		check(parsed.valid(), "complete countdown must parse: " + parsed.issues());
		var countdown = parsed.value().orElseThrow();
		check(countdown.durationTicks() == 200L, "countdown duration string was not normalized to ticks");
		check(countdown.layout().equals(HudSelfCheckFixtures.id("test:countdown")), "countdown layout was lost");

		check(countdown.displayAudience().selectors().size() == 2, "display audience union was not retained");
		var participantAudience = countdown.displayAudience().selectors().getFirst();
		check(
			participantAudience.source() == AudienceSource.FROZEN_PARTICIPANTS
				&& participantAudience.participants()
				&& participantAudience.onlineOnly(),
			"frozen participant display audience was not normalized"
		);
		check(
			countdown.displayAudience().selectors().get(1).source() == AudienceSource.CURRENT_HOST,
			"host display audience branch was not retained"
		);

		check(
			countdown.disconnect().requiredPlayer() == DisconnectAction.PAUSE
				&& countdown.disconnect().host() == DisconnectAction.CONTINUE,
			"disconnect policy was not retained"
		);
		var restrictions = countdown.restrictions();
		check(restrictions.movement() == FreezeMode.ALLOW, "movement restriction was not retained");
		check(restrictions.attack() == BlockMode.ALLOW, "attack restriction was not retained");
		check(restrictions.interact() == BlockMode.BLOCK, "interaction restriction was not retained");
		check(restrictions.items().use() == BlockMode.ALLOW, "item-use restriction was not retained");
		check(restrictions.items().drop() == BlockMode.BLOCK, "item-drop restriction was not retained");
		check(restrictions.items().swap() == BlockMode.ALLOW, "item-swap restriction was not retained");
		check(restrictions.items().inventory() == BlockMode.BLOCK, "inventory restriction was not retained");
		check(restrictions.damage() == DamageMode.NORMAL, "damage restriction was not retained");
		check(
			restrictions.camera() == AllowOnly.ALLOW
				&& restrictions.chat() == AllowOnly.ALLOW
				&& restrictions.escape() == AllowOnly.ALLOW,
			"camera/chat/escape allow-only guarantees were not retained"
		);

		check(countdown.checkpoints().size() == 2, "countdown checkpoints were not retained");
		var fiveSeconds = countdown.checkpoints().getFirst();
		check(
			fiveSeconds.id().equals("five_seconds") && fiveSeconds.remainingTicks() == 100L,
			"checkpoints must be normalized in descending remaining-time order"
		);
		check(
			fiveSeconds.sound().orElseThrow().event().equals(
				HudSelfCheckFixtures.id("minecraft:block.note_block.hat")
			)
				&& close(fiveSeconds.sound().orElseThrow().volume(), 0.75D)
				&& close(fiveSeconds.sound().orElseThrow().pitch(), 0.9D),
			"checkpoint sound was not retained"
		);
		check(
			fiveSeconds.cue().orElseThrow().equals(HudSelfCheckFixtures.id("test:five_second_cue")),
			"checkpoint cue reference was not retained"
		);

		var start = countdown.callbacks().get(CountdownCallbackSlot.START);
		check(
			start.size() == 1
				&& start.getFirst().id().equals("audit_start")
				&& start.getFirst().scope() == CountdownCallbackScope.GLOBAL
				&& start.getFirst().required(),
			"start callback was not retained"
		);
		var complete = countdown.callbacks().get(CountdownCallbackSlot.COMPLETE);
		check(
			complete.size() == 1
				&& complete.getFirst().scope() == CountdownCallbackScope.EACH_PARTICIPANT
				&& !complete.getFirst().required(),
			"participant callback or optionality was not retained"
		);
		for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
			check(countdown.callbacks().containsKey(slot), "callback map omitted slot " + slot);
		}
	}

	private static void checkStrictFieldRejections() {
		expectIssue("""
			{"format_version":1,"name":{"text":"Bad"},"duration":"0t","layout":"test:countdown"}
			""", "/duration", "OUT_OF_RANGE", "zero duration");
		expectIssue("""
			{"format_version":1,"name":{"text":"Bad"},"duration":"1s 5t","layout":"test:countdown"}
			""", "/duration", "TYPE_MISMATCH", "compound duration");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "display_audience":{
			    "source":"all",
			    "any_of":[{"source":"current_host"}]
			  }
			}
			""", "/display_audience", "INVALID_ONE_OF", "mixed audience form");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "disconnect":{"required_player":"ignore","host":"continue"}
			}
			""", "/disconnect/required_player", "INVALID_ENUM", "disconnect action");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "restrictions":{"camera":"block"}
			}
			""", "/restrictions/camera", "INVALID_ENUM", "camera allow-only restriction");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "checkpoints":[
			    {"id":"same","remaining":"5s"},
			    {"id":"same","remaining":"4s"}
			  ]
			}
			""", "/checkpoints/1/id", "DUPLICATE_VALUE", "duplicate checkpoint id");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "checkpoints":[{"id":"late","remaining":"11s"}]
			}
			""", "/checkpoints/0/remaining", "OUT_OF_RANGE", "checkpoint beyond duration");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "callbacks":{
			    "start":[
			      {"id":"same","scope":"global","function":"test:first"},
			      {"id":"same","scope":"global","function":"test:second"}
			    ]
			  }
			}
			""", "/callbacks/start/1/id", "DUPLICATE_VALUE", "duplicate callback id");
		expectIssue("""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "callbacks":{"finish":[]}
			}
			""", "/callbacks/finish", "UNKNOWN_KEY", "unknown callback slot");
	}

	private static void checkCountdownLayoutSurface() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"opening_countdown\": {\"definition\":\"test:opening\",\"required\":true}"
		));
		sources.add(HudSelfCheckFixtures.validHud().get(0));
		sources.add(HudSelfCheckFixtures.validHud().get(1));
		sources.add(HudSelfCheckFixtures.source(DefinitionType.COUNTDOWN, "opening", """
			{
			  "format_version":1,
			  "name":{"text":"Wrong surface"},
			  "duration":"10s",
			  "layout":"test:dock"
			}
			"""));
		var compilation = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(compilation.valid(), "HUD catalog-local layout failure must not reject reload");
		var catalog = compilation.snapshot().orElseThrow().hudCatalog();
		check(
			!catalog.countdownUsable(HudSelfCheckFixtures.id("test:opening")),
			"countdown must reject a dock-surface layout"
		);
		check(
			catalog.gameStartBlocked(HudSelfCheckFixtures.id("test:main")),
			"required countdown with a wrong surface must block approval"
		);
		check(
			catalog.requiredCountdownFailures().get(HudSelfCheckFixtures.id("test:main"))
				.diagnostics().stream()
				.anyMatch(problem -> problem.message().contains("surface dock") && problem.message().contains("countdown")),
			"wrong countdown layout surface must remain diagnosable"
		);
	}

	private static void checkRequiredCallbackResolution() {
		List<DefinitionCompiler.Source> sources = new ArrayList<>(HudSelfCheckFixtures.core(
			",\n  \"opening_countdown\": {\"definition\":\"test:opening\",\"required\":true}"
		));
		sources.add(HudSelfCheckFixtures.validHud().get(0));
		sources.add(HudSelfCheckFixtures.validHud().get(2));
		sources.add(HudSelfCheckFixtures.source(DefinitionType.COUNTDOWN, "opening", """
			{
			  "format_version":1,
			  "name":{"text":"Callback resolution"},
			  "duration":"10s",
			  "layout":"test:countdown",
			  "callbacks":{
			    "start":[
			      {"id":"gate","scope":"global","function":"test:required_gate","required":true}
			    ]
			  }
			}
			"""));

		var missing = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(
			missing.snapshot().orElseThrow().hudCatalog().gameStartBlocked(HudSelfCheckFixtures.id("test:main")),
			"missing required callback function must fail closed"
		);
		var present = DefinitionCompiler.compile(
			sources,
			Set.of(HudSelfCheckFixtures.id("test:required_gate")),
			Set.of()
		);
		check(present.valid(), "declared callback function should compile");
		check(
			present.snapshot().orElseThrow().hudCatalog().countdownUsable(HudSelfCheckFixtures.id("test:opening"))
				&& !present.snapshot().orElseThrow().hudCatalog().gameStartBlocked(HudSelfCheckFixtures.id("test:main")),
			"resolved required callback must leave the countdown executable"
		);
	}

	private static String completeCountdown() {
		return """
			{
			  "format_version": 1,
			  "name": {"text": "Complete countdown"},
			  "duration": "10s",
			  "layout": "test:countdown",
			  "display_audience": {
			    "any_of": [
			      {"source": "frozen_participants", "online_only": true},
			      {"source": "current_host", "online_only": true}
			    ]
			  },
			  "disconnect": {"required_player": "pause", "host": "continue"},
			  "restrictions": {
			    "movement": "allow",
			    "attack": "allow",
			    "interact": "block",
			    "items": {"use": "allow", "drop": "block", "swap": "allow", "inventory": "block"},
			    "damage": "normal",
			    "camera": "allow",
			    "chat": "allow",
			    "escape": "allow"
			  },
			  "checkpoints": [
			    {"id": "start", "remaining": "0t"},
			    {
			      "id": "five_seconds",
			      "remaining": "5s",
			      "sound": {"event": "minecraft:block.note_block.hat", "volume": 0.75, "pitch": 0.9},
			      "cue": "test:five_second_cue"
			    }
			  ],
			  "callbacks": {
			    "start": [
			      {"id": "audit_start", "scope": "global", "function": "test:audit_start", "required": true}
			    ],
			    "complete": [
			      {"id": "audit_player", "scope": "each_participant", "function": "test:audit_player", "required": false}
			    ]
			  }
			}
			""";
	}

	private static void expectIssue(
		final String json,
		final String path,
		final String code,
		final String label
	) {
		var parsed = HudDefinitionParser.parseCountdown(HudSelfCheckFixtures.id("test:invalid"), json);
		check(!parsed.valid(), label + " must be rejected");
		check(
			parsed.issues().stream().anyMatch(issue -> issue.path().equals(path) && issue.code().equals(code)),
			label + " rejection did not retain " + code + " at " + path + ": " + parsed.issues()
		);
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < 0.0001D;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
