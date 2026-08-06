package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonObject;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.AllowOnly;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.BlockMode;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownCallbackScope;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownCallbackSlot;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.DamageMode;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.DigitTransition;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.DisconnectAction;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.Easing;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.FreezeMode;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.ProgressMode;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.RollDirection;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.SurfaceTransition;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.TimeFormat;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.TimePrecision;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.ReloadStatus;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec.ProjectionFrame;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Strict parser and isolated catalog checks for standalone V3C countdown definitions. */
public final class CountdownDefinitionSelfCheck {
	private static final Identifier GAME_ID = id("test:main");
	private static final Identifier COUNTDOWN_ID = id("test:opening");
	private static final Identifier REQUIRED_CALLBACK = id("test:required_gate");

	private CountdownDefinitionSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkCompleteDefinition();
		checkStrictFieldRejections();
		checkCatalogIsolationAndRequiredClosure();
		checkRegistryDiagnosticsAndLastKnownGoodFallback();
		checkProjectionBudgetIsolation();
		checkProjectionBudgetBoundaryAndNormalExamples();
		checkRuntimeEncodingFailsClosed();
		System.out.println("COUNTDOWN_DEFINITION_SELF_CHECK=PASS");
	}

	private static void checkCompleteDefinition() {
		var parsed = CountdownDefinitionParser.parse(id("test:complete"), completeCountdown());
		check(parsed.valid(), "complete countdown must parse: " + parsed.issues());
		check(parsed.totalIssueCount() == 0, "valid countdown retained diagnostics");
		check(parsed.canonical().startsWith("{"), "canonical countdown document was not retained");
		check(parsed.sha256().length() == 64, "countdown SHA-256 identity was not retained");

		var countdown = parsed.definition().orElseThrow();
		check(countdown.durationTicks() == 200L, "duration string was not normalized to ticks");
		check(!countdown.displayAudience().includeHost(), "host display policy was not retained");
		check(countdown.displayAudience().includeObservers(), "observer display policy was not retained");

		var presentation = countdown.presentation();
		check(presentation.title().plainText().equals("正式开局"), "title was not retained");
		check(presentation.prefix().orElseThrow().plainText().equals("T-"), "prefix was not retained");
		check(presentation.suffix().orElseThrow().plainText().equals("秒"), "suffix was not retained");
		check(presentation.waitingText().plainText().equals("等待玩家"), "waiting text was not retained");
		check(presentation.pausedText().plainText().equals("已暂停"), "paused text was not retained");
		check(presentation.completeText().plainText().equals("开始"), "completion text was not retained");

		var time = presentation.time();
		check(
			time.format() == TimeFormat.SECONDS
				&& time.precision() == TimePrecision.HUNDREDTHS
				&& !time.leadingZero()
				&& time.separator().equals("·")
				&& time.color() == 0xFF112233
				&& close(time.scale(), 1.25D)
				&& !time.shadow(),
			"time presentation was not retained"
		);
		var panel = presentation.panel();
		check(
			panel.backgroundColor() == 0x80081018
				&& panel.borderColor() == 0xFF33424E
				&& panel.accentColor() == 0xFFF2C14E
				&& close(panel.opacity(), 0.9D)
				&& panel.paddingX() == 8
				&& panel.paddingY() == 4
				&& panel.gapAboveActionBar() == 7
				&& panel.maxWidth() == 300,
			"panel presentation was not retained"
		);
		check(
			presentation.progress().mode() == ProgressMode.LINE
				&& presentation.progress().thickness() == 2
				&& !presentation.progress().smooth(),
			"progress presentation was not retained"
		);
		var digits = presentation.digits();
		check(
			digits.transition() == DigitTransition.ROLL
				&& digits.direction() == RollDirection.UP
				&& digits.durationMs() == 180
				&& digits.distance() == 12
				&& digits.easing() == Easing.EASE_IN_OUT_CUBIC
				&& digits.staggerMs() == 8
				&& digits.animateFractional(),
			"digit presentation was not retained"
		);
		check(
			presentation.enter().mode() == SurfaceTransition.SLIDE
				&& presentation.exit().mode() == SurfaceTransition.INSTANT
				&& presentation.completion().holdMs() == 900,
			"surface lifecycle presentation was not retained"
		);
		check(
			presentation.lifecycleSounds().get(CountdownCallbackSlot.START).orElseThrow().event()
				.equals(id("minecraft:block.note_block.pling")),
			"lifecycle sound was not retained"
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
		check(restrictions.damage() == DamageMode.ALLOW, "damage restriction was not retained");
		check(
			restrictions.camera() == AllowOnly.ALLOW
				&& restrictions.chat() == AllowOnly.ALLOW
				&& restrictions.escape() == AllowOnly.ALLOW,
			"camera/chat/escape guarantees were not retained"
		);

		check(countdown.checkpoints().size() == 2, "countdown checkpoints were not retained");
		var fiveSeconds = countdown.checkpoints().getFirst();
		check(
			fiveSeconds.id().equals("five_seconds") && fiveSeconds.remainingTicks() == 100L,
			"checkpoints were not normalized in descending remaining-time order"
		);
		check(
			fiveSeconds.sound().orElseThrow().event().equals(id("minecraft:block.note_block.hat"))
				&& close(fiveSeconds.sound().orElseThrow().volume(), 0.75D)
				&& close(fiveSeconds.sound().orElseThrow().pitch(), 0.9D)
				&& fiveSeconds.cue().orElseThrow().equals(id("test:five_second_cue"))
				&& fiveSeconds.text().orElseThrow().plainText().equals("五秒")
				&& fiveSeconds.color().orElseThrow() == 0xFFFFCC00
				&& close(fiveSeconds.scale().orElseThrow(), 1.2D),
			"checkpoint presentation was not retained"
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
			check(presentation.lifecycleSounds().containsKey(slot), "sound map omitted slot " + slot);
		}
	}

	private static void checkStrictFieldRejections() {
		expectIssue(
			"{\"format_version\":1,\"name\":{\"text\":\"Bad\"},\"duration\":\"0t\"}",
			"/duration",
			"OUT_OF_RANGE",
			"zero duration"
		);
		expectIssue(
			"{\"format_version\":1,\"name\":{\"text\":\"Bad\"},\"duration\":\"1s 5t\"}",
			"/duration",
			"INVALID_DURATION",
			"compound duration"
		);
		expectIssue(
			"{\"format_version\":1,\"name\":{\"text\":\"Bad\"},\"layout\":\"test:legacy\"}",
			"/layout",
			"UNKNOWN_FIELD",
			"legacy HUD layout"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "display_audience":{"source":"all"}
			}
			""",
			"/display_audience/source",
			"UNKNOWN_FIELD",
			"legacy HUD audience"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "disconnect":{"required_player":"ignore","host":"continue"}
			}
			""",
			"/disconnect/required_player",
			"INVALID_ENUM",
			"disconnect action"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "restrictions":{"camera":"block"}
			}
			""",
			"/restrictions/camera",
			"INVALID_ENUM",
			"camera allow-only restriction"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "presentation":{"panel":{"gap_above_actionbar":1}}
			}
			""",
			"/presentation/panel",
			"OUT_OF_RANGE",
			"unsafe ActionBar gap"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "checkpoints":[
			    {"id":"same","remaining":"5s"},
			    {"id":"same","remaining":"4s"}
			  ]
			}
			""",
			"/checkpoints/1/id",
			"DUPLICATE_VALUE",
			"duplicate checkpoint id"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "duration":"10s",
			  "checkpoints":[{"id":"late","remaining":"11s"}]
			}
			""",
			"/checkpoints/0/remaining",
			"OUT_OF_RANGE",
			"checkpoint beyond duration"
		);
		expectIssue(
			"""
			{
			  "format_version":1,
			  "name":{"text":"Bad"},
			  "callbacks":{"finish":[]}
			}
			""",
			"/callbacks/finish",
			"UNKNOWN_FIELD",
			"unknown callback slot"
		);
	}

	private static void checkCatalogIsolationAndRequiredClosure() {
		List<Source> sources = new ArrayList<>(minimalCore(true));
		sources.add(source(DefinitionType.COUNTDOWN, "opening", catalogCountdown()));

		var missing = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(missing.valid(), "invalid countdown must remain catalog-local: " + missing.problems());
		var missingCatalog = missing.snapshot().orElseThrow().countdownCatalog();
		check(!missingCatalog.countdownUsable(COUNTDOWN_ID), "invalid countdown must be quarantined");
		check(missingCatalog.disabled().containsKey(COUNTDOWN_ID), "disabled countdown diagnostics were lost");
		check(missingCatalog.gameStartBlocked(GAME_ID), "required invalid countdown must block game start");
		check(
			missingCatalog.disabled().get(COUNTDOWN_ID).diagnostics().stream()
				.anyMatch(problem -> problem.code().equals("MISSING_REFERENCE")
					&& problem.message().contains(REQUIRED_CALLBACK.toString())),
			"missing required callback must remain diagnosable"
		);

		var present = DefinitionCompiler.compile(sources, Set.of(REQUIRED_CALLBACK), Set.of());
		check(present.valid(), "resolved required callback should compile: " + present.problems());
		var presentCatalog = present.snapshot().orElseThrow().countdownCatalog();
		check(presentCatalog.countdownUsable(COUNTDOWN_ID), "resolved countdown must be executable");
		check(presentCatalog.disabled().isEmpty(), "resolved countdown must not remain disabled");
		check(!presentCatalog.gameStartBlocked(GAME_ID), "resolved required countdown must allow game start");
		check(presentCatalog.definitionCount() == 1, "countdown catalog definition count changed");

		List<Source> optionalSources = new ArrayList<>(minimalCore(false));
		optionalSources.add(source(DefinitionType.COUNTDOWN, "opening", catalogCountdown()));
		var optional = DefinitionCompiler.compile(optionalSources, Set.of(), Set.of());
		check(optional.valid(), "optional invalid countdown must remain catalog-local");
		check(
			!optional.snapshot().orElseThrow().countdownCatalog().gameStartBlocked(GAME_ID),
			"optional invalid countdown must not block game start"
		);
	}

	private static void checkRegistryDiagnosticsAndLastKnownGoodFallback() {
		List<Source> missingSources = new ArrayList<>(minimalCore(true));
		var missingCompilation = DefinitionCompiler.compile(missingSources, Set.of(), Set.of());
		check(missingCompilation.valid(), "missing countdown must remain catalog-local");
		var missingCatalog = missingCompilation.snapshot().orElseThrow().countdownCatalog();
		check(
			missingCatalog.disabled().containsKey(COUNTDOWN_ID),
			"a referenced missing countdown must publish a diagnostic entry"
		);
		check(missingCatalog.gameStartBlocked(GAME_ID), "missing required countdown must block approval");

		DefinitionRegistry missingRegistry = new DefinitionRegistry();
		var missingOutcome = missingRegistry.apply(missingCompilation);
		check(missingOutcome.applied(), "isolated missing countdown must still publish the core generation");
		check(
			missingOutcome.status() == ReloadStatus.READY_WITH_WARNINGS,
			"isolated missing countdown must publish an explicit warning status"
		);
		check(missingOutcome.healthy(), "isolated countdown failure must not poison the core registry");
		String missingSummary = missingRegistry.view().diagnosticSummary();
		check(
			missingSummary.contains(COUNTDOWN_ID.toString())
				&& missingSummary.contains("不存在或不可用")
				&& missingSummary.contains("/reload"),
			"host missing-countdown diagnostic must be bounded, Chinese, identifiable, and actionable: "
				+ missingSummary
		);

		List<Source> validSources = new ArrayList<>(minimalCore(true));
		validSources.add(source(DefinitionType.COUNTDOWN, "opening", fallbackCountdown("10s", 300)));
		DefinitionRegistry registry = new DefinitionRegistry();
		check(
			registry.apply(DefinitionCompiler.compile(validSources, Set.of(), Set.of())).applied(),
			"valid countdown generation must apply"
		);
		var generationOne = registry.view().active();
		String generationOneSha = generationOne.countdownCatalog().countdowns().get(COUNTDOWN_ID).sha256();

		List<Source> invalidSources = new ArrayList<>(minimalCore(true));
		invalidSources.add(source(DefinitionType.COUNTDOWN, "opening", fallbackCountdown("12s", 999)));
		var isolatedCandidate = DefinitionCompiler.compile(invalidSources, Set.of(), Set.of());
		check(isolatedCandidate.valid(), "bad replacement countdown must remain catalog-local");
		var warningOutcome = registry.apply(isolatedCandidate);
		var warningView = registry.view();
		var warningCatalog = warningView.active().countdownCatalog();
		check(warningOutcome.applied(), "core generation with a bad replacement must still apply");
		check(warningView.active().generation() == 2L, "isolated warning must advance the core generation once");
		check(
			warningOutcome.status() == ReloadStatus.READY_WITH_WARNINGS && warningOutcome.healthy(),
			"bad replacement must publish a healthy warning generation"
		);
		check(
			warningCatalog.countdownUsable(COUNTDOWN_ID)
				&& warningCatalog.usingRetainedFallback(COUNTDOWN_ID)
				&& warningCatalog.disabled().containsKey(COUNTDOWN_ID),
			"bad replacement must retain executable old content alongside candidate diagnostics"
		);
		check(
			warningCatalog.countdowns().get(COUNTDOWN_ID).sha256().equals(generationOneSha),
			"bad replacement changed the last-known-good countdown document"
		);
		check(
			warningCatalog.definitionCount() == 1,
			"one fallback plus its candidate diagnostic must still count as one definition identity"
		);
		check(
			!warningCatalog.gameStartBlocked(GAME_ID) && warningView.canStartNewFlows(),
			"validated fallback must keep required opening approval available"
		);
		String warningSummary = warningView.diagnosticSummary();
		check(
			warningSummary.contains("/presentation/panel")
				&& warningSummary.contains("96–480")
				&& warningSummary.contains("上一代已验证版本"),
			"host fallback diagnostic must identify the field, safe range, and recovery behavior: "
				+ warningSummary
		);
		check(
			warningSummary.codePointCount(0, warningSummary.length()) <= 768,
			"host countdown warning exceeded its projection bound"
		);

		List<Source> correctedSources = new ArrayList<>(minimalCore(true));
		correctedSources.add(source(DefinitionType.COUNTDOWN, "opening", fallbackCountdown("12s", 320)));
		var corrected = registry.apply(DefinitionCompiler.compile(correctedSources, Set.of(), Set.of()));
		var correctedCatalog = registry.view().active().countdownCatalog();
		check(corrected.status() == ReloadStatus.READY, "corrected countdown must clear warning status");
		check(correctedCatalog.disabled().isEmpty(), "corrected countdown retained stale diagnostics");
		check(correctedCatalog.retainedFallbacks().isEmpty(), "corrected countdown retained stale fallback state");
		check(
			correctedCatalog.countdowns().get(COUNTDOWN_ID).durationTicks() == 240L,
			"corrected countdown did not replace the retained generation"
		);

		List<Source> callbackSources = new ArrayList<>(minimalCore(true));
		callbackSources.add(source(DefinitionType.COUNTDOWN, "opening", catalogCountdown()));
		DefinitionRegistry closureRegistry = new DefinitionRegistry();
		check(
			closureRegistry.apply(
				DefinitionCompiler.compile(callbackSources, Set.of(REQUIRED_CALLBACK), Set.of())
			).applied(),
			"callback-backed baseline must apply"
		);
		closureRegistry.apply(DefinitionCompiler.compile(minimalCore(true), Set.of(), Set.of()));
		var closureCatalog = closureRegistry.view().active().countdownCatalog();
		check(
			!closureCatalog.countdownUsable(COUNTDOWN_ID)
				&& closureCatalog.gameStartBlocked(GAME_ID)
				&& !closureCatalog.usingRetainedFallback(COUNTDOWN_ID),
			"last-known-good countdown must not survive when its required callback closure disappeared"
		);
		check(
			closureCatalog.disabled().get(COUNTDOWN_ID).totalDiagnosticCount() >= 2,
			"failed fallback closure must retain both candidate and revalidation diagnostics"
		);
	}

	private static void checkProjectionBudgetIsolation() {
		String aggregate = aggregateProjectionCountdown(450);
		var aggregateParsed = CountdownDefinitionParser.parse(id("test:aggregate"), aggregate);
		check(
			aggregateParsed.valid(),
			"six individually legal text components must parse before aggregate budget audit: "
				+ aggregateParsed.issues()
		);
		String aggregateComponent = paddedTextComponent(450);
		check(
			aggregateComponent.getBytes(StandardCharsets.UTF_8).length
				<= io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.MAX_TEXT_COMPONENT_BYTES,
			"aggregate fixture must keep every individual component within its parser limit"
		);
		var aggregateBudget = CountdownProjectionDocumentCodec.auditDefinition(
			aggregateParsed.definition().orElseThrow()
		);
		check(!aggregateBudget.encodable(), "aggregate Replace overflow must be detected");
		check(
			aggregateBudget.maximumReplaceBytes() > CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES,
			"aggregate fixture did not cross the real Replace wire limit"
		);

		List<Source> aggregateSources = new ArrayList<>(minimalCore(true));
		aggregateSources.add(source(DefinitionType.COUNTDOWN, "opening", aggregate));
		var aggregateCompilation = DefinitionCompiler.compile(aggregateSources, Set.of(), Set.of());
		check(aggregateCompilation.valid(), "projection overflow must remain catalog-local");
		var aggregateCatalog = aggregateCompilation.snapshot().orElseThrow().countdownCatalog();
		check(!aggregateCatalog.countdownUsable(COUNTDOWN_ID), "oversized Replace must be quarantined");
		check(aggregateCatalog.gameStartBlocked(GAME_ID), "required oversized Replace must block approval");
		check(
			aggregateCatalog.disabled().get(COUNTDOWN_ID).diagnostics().stream().anyMatch(problem ->
				problem.code().equals("RESOURCE_LIMIT")
					&& problem.message().contains("countdown Replace")
					&& problem.message().contains("UTF-8 bytes")
			),
			"aggregate Replace rejection must retain measured diagnostic"
		);

		String checkpoint = checkpointProjectionCountdown(650);
		var checkpointParsed = CountdownDefinitionParser.parse(id("test:checkpoint"), checkpoint);
		check(
			checkpointParsed.valid(),
			"individually legal checkpoint component must parse before envelope audit: "
				+ checkpointParsed.issues()
		);
		check(
			paddedTextComponent(650).getBytes(StandardCharsets.UTF_8).length
				<= io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.MAX_TEXT_COMPONENT_BYTES,
			"checkpoint fixture must remain within the component limit"
		);
		var checkpointBudget = CountdownProjectionDocumentCodec.auditDefinition(
			checkpointParsed.definition().orElseThrow()
		);
		check(!checkpointBudget.encodable(), "checkpoint plus Patch envelope overflow must be detected");
		check(
			checkpointBudget.maximumPatchBytes() > CountdownPatchS2CPayload.MAX_PATCH_BYTES,
			"checkpoint fixture did not cross the real Patch wire limit"
		);

		List<Source> checkpointSources = new ArrayList<>(minimalCore(true));
		checkpointSources.add(source(DefinitionType.COUNTDOWN, "opening", checkpoint));
		var checkpointCompilation = DefinitionCompiler.compile(checkpointSources, Set.of(), Set.of());
		check(checkpointCompilation.valid(), "checkpoint overflow must remain catalog-local");
		var checkpointCatalog = checkpointCompilation.snapshot().orElseThrow().countdownCatalog();
		check(!checkpointCatalog.countdownUsable(COUNTDOWN_ID), "oversized Patch must be quarantined");
		check(checkpointCatalog.gameStartBlocked(GAME_ID), "required oversized Patch must block approval");
		check(
			checkpointCatalog.disabled().get(COUNTDOWN_ID).diagnostics().stream().anyMatch(problem ->
				problem.code().equals("RESOURCE_LIMIT")
					&& problem.pointer().startsWith("/checkpoints")
					&& problem.message().contains("countdown Patch")
			),
			"checkpoint Patch rejection must retain measured diagnostic"
		);
	}

	private static void checkProjectionBudgetBoundaryAndNormalExamples() {
		var normal = CountdownDefinitionParser.parse(id("test:normal-budget"), completeCountdown());
		check(normal.valid(), "normal projection budget fixture must parse");
		var normalBudget = CountdownProjectionDocumentCodec.auditDefinition(
			normal.definition().orElseThrow()
		);
		check(normalBudget.encodable(), "normal complete countdown must fit every projection variant");

		String nearBoundary = aggregateProjectionCountdown(400);
		var boundary = CountdownDefinitionParser.parse(id("test:near-boundary"), nearBoundary);
		check(boundary.valid(), "near-boundary projection fixture must parse: " + boundary.issues());
		var boundaryBudget = CountdownProjectionDocumentCodec.auditDefinition(
			boundary.definition().orElseThrow()
		);
		check(boundaryBudget.encodable(), "near-boundary legal Replace must remain accepted");
		check(
			boundaryBudget.maximumReplaceBytes() > 58_000
				&& boundaryBudget.maximumReplaceBytes() <= CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES,
			"near-boundary fixture must exercise the actual upper budget: "
				+ boundaryBudget.maximumReplaceBytes()
		);

		List<Source> sources = new ArrayList<>(minimalCore(true));
		sources.add(source(DefinitionType.COUNTDOWN, "opening", nearBoundary));
		var compiled = DefinitionCompiler.compile(sources, Set.of(), Set.of());
		check(compiled.valid(), "near-boundary definition compilation must remain healthy");
		var catalog = compiled.snapshot().orElseThrow().countdownCatalog();
		check(catalog.countdownUsable(COUNTDOWN_ID), "near-boundary definition must be retained");
		check(!catalog.gameStartBlocked(GAME_ID), "near-boundary required definition must allow approval");
	}

	private static void checkRuntimeEncodingFailsClosed() {
		JsonObject oversizedCheckpoint = new JsonObject();
		oversizedCheckpoint.addProperty("id", "runtime_fail_closed");
		oversizedCheckpoint.addProperty("text", "界".repeat(CountdownPatchS2CPayload.MAX_PATCH_BYTES));
		oversizedCheckpoint.addProperty("color", 0xFFFFFFFF);
		oversizedCheckpoint.addProperty("scale", 1.0D);
		ProjectionFrame frame = new ProjectionFrame(
			UUID.fromString("00000000-0000-0000-0000-000000000001"),
			"opening",
			200L,
			"running",
			1L,
			200.0D,
			-1.0D,
			false,
			true,
			Optional.of(oversizedCheckpoint)
		);
		var rejected = CountdownProjectionDocumentCodec.encodePatch(frame);
		check(!rejected.success(), "runtime oversized bytes must return a fail-closed result");
		check(rejected.document().isEmpty(), "runtime overflow must not expose a partial document");
		check(
			rejected.utf8Bytes() > CountdownPatchS2CPayload.MAX_PATCH_BYTES
				&& rejected.diagnostic().orElseThrow().contains("countdown Patch"),
			"runtime overflow must retain its measured diagnostic"
		);

		ProjectionFrame normalFrame = new ProjectionFrame(
			frame.countdownInstanceId(),
			frame.purpose(),
			frame.totalTicks(),
			frame.state(),
			frame.serverTick(),
			frame.baseRemainingTicks(),
			frame.rate(),
			frame.paused(),
			frame.inventoryBlocked(),
			Optional.empty()
		);
		var normal = CountdownProjectionDocumentCodec.encodeReplace(normalFrame, Optional.empty());
		check(normal.success(), "runtime fallback Replace must remain encodable");
		check(
			normal.document().orElseThrow().length == normal.utf8Bytes(),
			"runtime encoding result must retain exact bytes"
		);
	}

	private static List<Source> minimalCore(final boolean requiredCountdown) {
		return List.of(
			source(DefinitionType.GAME, "main", """
				{
				  "format_version":1,
				  "api_version":4,
				  "content_version":1,
				  "name":{"text":"Countdown test"},
				  "initial_phase":"test:setup",
				  "default_role":"test:runner",
				  "default_life_state":"test:alive",
				  "opening_countdown":{"definition":"test:opening","required":%s}
				}
				""".formatted(requiredCountdown)),
			source(DefinitionType.ROLE, "runner", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "name":{"text":"Runner"},
				  "tags":[]
				}
				"""),
			source(DefinitionType.LIFE_STATE, "alive", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "name":{"text":"Alive"},
				  "tags":[]
				}
				"""),
			source(DefinitionType.PHASE, "setup", """
				{
				  "format_version":1,
				  "game":"test:main",
				  "name":{"text":"Setup"},
				  "transitions":[]
				}
				""")
		);
	}

	private static String catalogCountdown() {
		return """
			{
			  "format_version":1,
			  "name":{"text":"Opening"},
			  "duration":"10s",
			  "callbacks":{
			    "start":[
			      {"id":"gate","scope":"global","function":"test:required_gate","required":true}
			    ]
			  }
			}
			""";
	}

	private static String fallbackCountdown(final String duration, final int maxWidth) {
		return """
			{
			  "format_version":1,
			  "name":{"text":"Opening fallback"},
			  "duration":"%s",
			  "presentation":{"panel":{"max_width":%d}}
			}
			""".formatted(duration, maxWidth);
	}

	private static String aggregateProjectionCountdown(final int extraComponentsPerField) {
		String component = paddedTextComponent(extraComponentsPerField);
		return """
			{
			  "format_version":1,
			  "name":{"text":"Aggregate projection budget"},
			  "duration":"10s",
			  "presentation":{
			    "title":%s,
			    "prefix":%s,
			    "suffix":%s,
			    "waiting_text":%s,
			    "paused_text":%s,
			    "complete_text":%s
			  }
			}
			""".formatted(component, component, component, component, component, component);
	}

	private static String checkpointProjectionCountdown(final int extraComponents) {
		return """
			{
			  "format_version":1,
			  "name":{"text":"Checkpoint projection budget"},
			  "duration":"10s",
			  "checkpoints":[
			    {
			      "id":"heavy",
			      "remaining":"5s",
			      "text":%s
			    }
			  ]
			}
			""".formatted(paddedTextComponent(extraComponents));
	}

	private static String paddedTextComponent(final int extraComponents) {
		StringBuilder result = new StringBuilder("{\"text\":\"X\",\"extra\":[");
		for (int index = 0; index < extraComponents; index++) {
			if (index > 0) {
				result.append(',');
			}
			result.append("{\"text\":\"\",\"bold\":false}");
		}
		return result.append("]}").toString();
	}

	private static String completeCountdown() {
		return """
			{
			  "format_version":1,
			  "name":{"text":"Complete countdown"},
			  "duration":"10s",
			  "display_audience":{"include_host":false,"include_observers":true},
			  "presentation":{
			    "title":{"text":"正式开局"},
			    "prefix":{"text":"T-"},
			    "suffix":{"text":"秒"},
			    "waiting_text":{"text":"等待玩家"},
			    "paused_text":{"text":"已暂停"},
			    "complete_text":{"text":"开始"},
			    "time":{
			      "format":"seconds",
			      "precision":"hundredths",
			      "leading_zero":false,
			      "separator":"·",
			      "color":"#112233",
			      "scale":1.25,
			      "shadow":false
			    },
			    "panel":{
			      "background_color":"#08101880",
			      "border_color":"#33424E",
			      "accent_color":"#F2C14E",
			      "opacity":0.9,
			      "padding_x":8,
			      "padding_y":4,
			      "gap_above_actionbar":7,
			      "max_width":300
			    },
			    "progress":{"mode":"line","thickness":2,"smooth":false},
			    "digits":{
			      "transition":"roll",
			      "direction":"up",
			      "duration_ms":180,
			      "distance":12,
			      "easing":"ease_in_out_cubic",
			      "stagger_ms":8,
			      "animate_fractional":true
			    },
			    "enter":{"mode":"slide","duration_ms":160,"easing":"linear"},
			    "exit":{"mode":"instant","duration_ms":0,"easing":"linear"},
			    "completion":{"hold_ms":900,"color":"#F2C14E"},
			    "sounds":{
			      "start":{"event":"minecraft:block.note_block.pling","volume":0.65,"pitch":0.9}
			    }
			  },
			  "disconnect":{"required_player":"pause","host":"continue"},
			  "restrictions":{
			    "movement":"allow",
			    "attack":"allow",
			    "interact":"block",
			    "items":{"use":"allow","drop":"block","swap":"allow","inventory":"block"},
			    "damage":"allow",
			    "camera":"allow",
			    "chat":"allow",
			    "escape":"allow"
			  },
			  "checkpoints":[
			    {"id":"start","remaining":"0t"},
			    {
			      "id":"five_seconds",
			      "remaining":"5s",
			      "sound":{"event":"minecraft:block.note_block.hat","volume":0.75,"pitch":0.9},
			      "cue":"test:five_second_cue",
			      "text":{"text":"五秒"},
			      "color":"#FFCC00",
			      "scale":1.2
			    }
			  ],
			  "callbacks":{
			    "start":[
			      {"id":"audit_start","scope":"global","function":"test:audit_start","required":true}
			    ],
			    "complete":[
			      {"id":"audit_player","scope":"each_participant","function":"test:audit_player","required":false}
			    ]
			  }
			}
			""";
	}

	private static void expectIssue(
		final String json,
		final String pointer,
		final String code,
		final String label
	) {
		var parsed = CountdownDefinitionParser.parse(id("test:invalid"), json);
		check(!parsed.valid(), label + " must be rejected");
		check(
			parsed.issues().stream().anyMatch(issue -> issue.pointer().equals(pointer) && issue.code().equals(code)),
			label + " rejection did not retain " + code + " at " + pointer + ": " + parsed.issues()
		);
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
			"countdown-self-check",
			json
		);
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
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
