package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.DatapackFixtureLoader.LoadedPacks;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EmptyAudienceMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterErrorMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterSourceKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Compiles the release-facing V3B example and its disposable high-priority pressure overlay.
 *
 * <p>The base fixture demonstrates all three capture modes, cue/content field scopes, the four
 * vanilla text channels, sound, callback, and two effect presets. The pressure fixture proves
 * whole-resource replacement and a valid cue exactly at the dynamic-field limit.
 */
public final class MessageFixtureSelfCheck {
	private MessageFixtureSelfCheck() {
	}

	public static void main(final String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		Path pressure = Path.of("examples", "pixel-tzz-3b-pressure-datapack");
		check(Files.isDirectory(base), "base example data pack is missing");
		check(Files.isDirectory(pressure), "3B pressure overlay is missing");
		String acceptanceSetup = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/setup.mcfunction")
		);
		String acceptanceClear = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/clear.mcfunction")
		);
		check(
			acceptanceSetup.contains("pixel_tzz:acceptance_3b all_call set value {target:\"Player972\"")
				&& acceptanceSetup.contains(
					"pixel_tzz:acceptance_3b layout_all_call set value {target:\"Player972\""
				)
				&& acceptanceClear.contains(
					"data remove storage pixel_tzz:acceptance_3b all_call"
				)
				&& acceptanceClear.contains(
					"data remove storage pixel_tzz:acceptance_3b layout_all_call"
				)
				&& acceptanceClear.contains(
					"data remove storage pixel_tzz:acceptance_3b drain_call"
				),
			"acceptance fixtures must provide explicit multi-target arguments and clear transient calls"
		);
		String captureStart = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/capture/start.mcfunction")
		);
		String capturePrepare = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/capture/prepare.mcfunction")
		);
		String conflictRefresh = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/conflict/refresh.mcfunction")
		);
		String accessibilityStaticFinal = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/accessibility/static_final.mcfunction")
		);
		String finalizeGroupStart = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/restart/finalize_start_group_paused.mcfunction")
		);
		String finalizeGenerationStart = Files.readString(
			base.resolve("data/pixel_tzz/function/acceptance_3b/restart/finalize_generation_start_paused.mcfunction")
		);
		check(
			acceptanceSetup.contains("scoreboard players set @a pixel_tzz_demo 10"),
			"3B acceptance setup must seed the score for every connected test client"
		);
		check(
			acceptanceSetup.contains("function pixel_tzz:acceptance_3b/clear"),
			"repeated acceptance setup must clear the previous disposable round first"
		);
		for (String scheduled : java.util.List.of(
			"capture/mutate_score",
			"capture/mutate_storage",
			"capture/cleanup"
		)) {
			check(
				acceptanceClear.contains("schedule clear pixel_tzz:acceptance_3b/" + scheduled)
					&& capturePrepare.contains(
						"schedule clear pixel_tzz:acceptance_3b/" + scheduled
					),
				"reset and capture replay must clear stale schedule: " + scheduled
			);
		}
		for (String cue : java.util.List.of(
			"field_capture",
			"policy_matrix",
			"restart_finalize",
			"history_replay",
			"registered_entity",
			"verified_player_speaker"
		)) {
			check(
				acceptanceClear.contains(
					"message control cue pixel_tzz:acceptance/" + cue + " cancel"
				),
				"acceptance reset must cancel disposable cue: " + cue
			);
		}
		check(
			acceptanceClear.contains("data remove storage pixel_tzz:acceptance_3b bad_call")
				&& captureStart.contains("function pixel_tzz:acceptance_3b/capture/prepare")
				&& capturePrepare.contains(
					"data remove storage pixel_tzz:acceptance_3b last_callback"
				),
			"reset and capture replay must not retain bad input or callback evidence"
		);
		check(
			conflictRefresh.lines()
				.filter(line -> line.startsWith(
					"pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix"
				))
				.count() == 2L
				&& conflictRefresh.lines()
					.filter(line -> line.startsWith(
						"pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix"
					))
					.allMatch(line -> line.contains(" to @s ")),
			"policy_matrix refresh calls must declare the observing player as call_targets"
		);
		check(
			accessibilityStaticFinal.contains(
				"message play pixel_tzz:acceptance/policy_matrix to @s with storage"
			),
			"policy_matrix static-final acceptance must declare the observing player as call_targets"
		);
		check(
			finalizeGroupStart.contains(
				"store result storage pixel_tzz:acceptance_3b restart_finalize_result.play int 1 run pixel_tzz_pro message play pixel_tzz:acceptance/restart_finalize to @a[tag=acceptance_3b_target]"
			)
				&& finalizeGroupStart.contains(
					"restart_finalize_result{play:2} store result storage pixel_tzz:acceptance_3b restart_finalize_result.pause int 1 run pixel_tzz_pro message control cue pixel_tzz:acceptance/restart_finalize pause"
				)
				&& finalizeGroupStart.contains("tag PlayerC remove acceptance_3b_target")
				&& finalizeGroupStart.contains("tag PlayerE remove acceptance_3b_target")
				&& finalizeGenerationStart.contains(
					"store result storage pixel_tzz:acceptance_3b restart_finalize_result.play int 1 run pixel_tzz_pro message play pixel_tzz:acceptance/restart_finalize to PlayerE"
				)
				&& finalizeGenerationStart.contains(
					"restart_finalize_result{play:1} store result storage pixel_tzz:acceptance_3b restart_finalize_result.pause int 1 run pixel_tzz_pro message control cue pixel_tzz:acceptance/restart_finalize pause"
				),
			"restart=finalize acceptance must provide single-command paused entries for both recovery paths"
		);
		checkExplicitFieldCaptureTargets(base);
		checkDatapackOwnedAcceptanceCopy(base);

		BaseFacts baseFacts = checkBaseFixture(
			DatapackFixtureLoader.loadInPriorityOrder(base)
		);
		checkPressureFixture(
			DatapackFixtureLoader.loadInPriorityOrder(base, pressure),
			baseFacts
		);

		System.out.println("MESSAGE_FIXTURE_SELF_CHECK=PASS");
	}

	private static void checkExplicitFieldCaptureTargets(final Path base) throws Exception {
		Path functionRoot = base.resolve("data/pixel_tzz/function/acceptance_3b");
		int fieldCaptureCalls = 0;
		try (var files = Files.walk(functionRoot)) {
			for (Path path : files.filter(value -> value.toString().endsWith(".mcfunction")).toList()) {
				int lineNumber = 0;
				for (String line : Files.readAllLines(path)) {
					lineNumber++;
					String command = line.strip();
					if (
						!command.startsWith(
							"pixel_tzz_pro message play pixel_tzz:acceptance/field_capture"
						)
					) {
						continue;
					}
					fieldCaptureCalls++;
					check(
						command.contains(" to "),
						"positive field_capture acceptance call must declare call_targets: "
							+ path
							+ ":"
							+ lineNumber
					);
				}
			}
		}
		check(fieldCaptureCalls > 0, "field_capture acceptance calls are missing");
	}

	private static void checkDatapackOwnedAcceptanceCopy(final Path base) throws Exception {
		String fieldCapture = Files.readString(
			base.resolve(
				"data/pixel_tzz/pixel_tzz_pro/message_cues/acceptance/field_capture.json"
			)
		);
		check(
			fieldCapture.contains("『动态文本』"),
			"the acceptance label must remain visible in the editable data-pack fixture"
		);
		for (Path sourceRoot : java.util.List.of(Path.of("src/main/java"), Path.of("src/client/java"))) {
			try (var files = Files.walk(sourceRoot)) {
				check(
					files.filter(path -> path.toString().endsWith(".java"))
						.noneMatch(path -> contains(path, "『动态文本』")),
					"『动态文本』 must not be hard-coded in production Java"
				);
			}
		}
	}

	private static boolean contains(final Path path, final String needle) {
		try {
			return Files.readString(path).contains(needle);
		} catch (java.io.IOException error) {
			throw new java.io.UncheckedIOException(error);
		}
	}

	private static BaseFacts checkBaseFixture(final LoadedPacks loaded) {
		var compilation = DefinitionCompiler.compile(
			loaded.sources().values().stream().toList(),
			loaded.functions(),
			loaded.predicates()
		);
		check(compilation.valid(), "V3B base fixture must compile: " + compilation.problems());
		var snapshot = compilation.snapshot().orElseThrow();
		var catalog = snapshot.messageCatalog();
		Identifier acceptanceEffectId = id("acceptance");
		Identifier decodeEffectId = id("decode_notice");
		Identifier captureCueId = id("acceptance/field_capture");
		Identifier policyCueId = id("acceptance/policy_matrix");
		Identifier restartFinalizeCueId = id("acceptance/restart_finalize");
		Identifier replayCueId = id("acceptance/history_replay");
		Identifier registeredEntityCueId = id("acceptance/registered_entity");
		Identifier verifiedPlayerSpeakerCueId = id("acceptance/verified_player_speaker");
		Identifier playerLifecycleCueId = id("lifecycle/player_notice");
		Identifier hunterFlowId = id("hunter_initialization");
		Identifier hunterRoleId = id("hunter");
		Identifier callbackId = id("acceptance_3b/message_complete");
		Identifier predicateId = id("acceptance_3b/is_hunter");

		check(
			catalog.disabled().isEmpty(),
			"base fixture must not publish disabled messages: " + catalog.disabled()
		);
		check(
			catalog.startBlockedGames().isEmpty(),
			"valid required base cue must not block its game"
		);
		check(catalog.textEffects().size() == 2, "base fixture must publish two text effects");
		check(catalog.messageCues().size() == 9, "base fixture must publish nine message cues");
		check(
			snapshot.flows().get(hunterFlowId).messageHooks().isEmpty(),
			"base hunter initialization must retain the accepted 3A subtitle density"
		);
		check(
			snapshot.roles().get(hunterRoleId).messageHooks().isEmpty(),
			"base hunter role must not append V3B subtitles to the accepted 3A feedback"
		);

		var acceptance = catalog.textEffects().get(acceptanceEffectId);
		check(acceptance != null, "base typewriter effect is missing");
		check(
			acceptance.typewriter().orElseThrow().characterInterval().nanoseconds() == 45_000_000L,
			"base typewriter interval must be 45ms"
		);
		check(
			acceptance.typewriter().orElseThrow().cursor().glyph().equals("█"),
			"base cursor glyph must remain the registered block"
		);
		check(
			acceptance.characterSound().isPresent(),
			"base effect must retain its explicit primary character sound"
		);
		check(
			catalog.textEffects().get(decodeEffectId).scramble().isPresent(),
			"base decode effect must retain scramble configuration"
		);

		var cue = catalog.messageCues().get(captureCueId);
		check(cue != null && cue.required(), "base acceptance cue must be present and required");
		check(cue.game().equals(Optional.of(id("main"))), "base cue must belong to pixel_tzz:main");
		check(cue.nodes().size() == 6, "base cue must retain all six timeline nodes");
		var audienceSelectors = cue.policies().audience().audience().selectors();
		check(
			audienceSelectors.size() == 1
				&& audienceSelectors.getFirst().source() == AudienceSource.CALL_TARGETS
				&& audienceSelectors.getFirst().onlineOnly(),
			"base field_capture audience must contain only online call_targets"
		);
		check(
			cue.policies().delivery().emptyAudience() == EmptyAudienceMode.FAIL,
			"field_capture without call_targets must fail instead of falling back to the invoker"
		);
		var playerLifecycleCue = catalog.messageCues().get(playerLifecycleCueId);
		check(playerLifecycleCue != null, "player lifecycle cue is missing");
		var playerLifecycleAudience = playerLifecycleCue.policies()
			.audience()
			.audience()
			.selectors();
		check(
			playerLifecycleAudience.size() == 1
				&& playerLifecycleAudience.getFirst().source() == AudienceSource.CALL_TARGETS
				&& playerLifecycleAudience.getFirst().onlineOnly(),
			"per-player lifecycle notices must stay with their explicit player targets, not fan out to the host"
		);
		var targetParameter = cue.parameters().get("target");
		check(
			targetParameter != null
				&& targetParameter.required()
				&& targetParameter.onError().mode() == ParameterErrorMode.FAIL
				&& targetParameter.sources().stream().map(source -> source.kind()).toList()
					.equals(java.util.List.of(
						ParameterSourceKind.CALL_ARGUMENT,
						ParameterSourceKind.TARGET
					)),
			"field_capture target parameter must resolve from an explicit argument or target, never invoker"
		);
		check(
			cue.fields().get("target_name").source() instanceof ArgumentSource argument
				&& argument.parameter().equals("target"),
			"target_name must render the explicit target parameter"
		);
		check(
			cue.fields().get("target_name").capture() == CaptureMode.ON_DISPLAY
				&& cue.fields().get("score_now").capture() == CaptureMode.ON_FIRST_FIELD
				&& cue.fields().get("storage_note").capture() == CaptureMode.PER_FIELD,
			"base cue must exercise all three dynamic capture modes"
		);

		EnumSet<TextChannel> channels = EnumSet.noneOf(TextChannel.class);
		boolean hasLocalFields = false;
		boolean hasSound = false;
		boolean hasCallback = false;
		for (var node : cue.nodes()) {
			if (node instanceof TextNode textNode) {
				channels.add(textNode.channel());
				hasLocalFields |= !textNode.fields().isEmpty();
			} else if (node instanceof SoundNode) {
				hasSound = true;
			} else if (
				node instanceof CallbackNode callback
					&& callback.function().equals(callbackId)
			) {
				hasCallback = true;
			}
		}
		check(
			channels.equals(EnumSet.allOf(TextChannel.class)),
			"base cue must cover Chat, Title, Subtitle, and ActionBar"
		);
		check(hasLocalFields, "base cue must contain content-local dynamic fields");
		check(hasSound, "base cue must contain a standalone sound node");
		check(hasCallback, "base cue must reference the registered callback function");
		check(
			loaded.functions().contains(callbackId),
			"base callback mcfunction must be discoverable"
		);
		check(
			loaded.predicates().contains(predicateId),
			"base node-condition predicate must be discoverable"
		);

		var policyCue = catalog.messageCues().get(policyCueId);
		check(
			policyCue != null && policyCue.required(),
			"base policy-matrix cue must be present and required"
		);
		check(
			policyCue.nodes().size() == 3
				&& policyCue.parameters().size() == 6
				&& policyCue.history().isPresent()
				&& policyCue.staticFallback().isPresent()
				&& policyCue.assets().size() == 2,
			"base policy cue must retain its complete parameter, timeline, history, and asset AST"
		);
		check(
			policyCue.policies().concurrency().duplicate() == DuplicateMode.REFRESH
				&& policyCue.policies().concurrency().priority() == 12
				&& policyCue.softLimits().maxPendingOffline().orElseThrow() == 8,
			"base policy cue must retain refresh and bounded offline policy"
		);
		check(
			policyCue.parameters().get("history_target").sources().stream()
				.map(source -> source.kind())
				.toList()
				.equals(java.util.List.of(
					ParameterSourceKind.CALL_ARGUMENT,
					ParameterSourceKind.TARGET,
					ParameterSourceKind.INVOKER,
					ParameterSourceKind.ORIGIN
				)),
			"policy-matrix history subject must prefer the unique call target over the invoker"
		);
		var replayCue = catalog.messageCues().get(replayCueId);
		check(
			replayCue != null
				&& replayCue.history().filter(value -> value.replayAllowed()).isPresent()
				&& replayCue.nodes().size() == 3
				&& replayCue.nodes().stream().filter(CallbackNode.class::isInstance).count() == 1L
				&& replayCue.nodes().stream().allMatch(node -> node.header().when().isEmpty()),
			"base fixture must publish one condition-free replay cue with a callback for sanitizer acceptance"
		);
		check(
			policyCue.history().filter(value -> value.replayAllowed()).isEmpty(),
			"the policy-matrix history fixture must remain static-only"
		);
		check(
			policyCue.history().orElseThrow().audience().selectors().stream()
				.allMatch(selector -> selector.source() == AudienceSource.CALL_TARGETS),
			"the policy-matrix history fixture must remain reachable from the target player terminal"
		);
		var restartFinalizeCue = catalog.messageCues().get(restartFinalizeCueId);
		check(
			restartFinalizeCue != null
				&& restartFinalizeCue.policies().lifecycle().restart() == RestartMode.FINALIZE
				&& restartFinalizeCue.policies().audience().evolution()
					== AudienceEvolution.SNAPSHOT
				&& !restartFinalizeCue.policies().audience().sensitive()
				&& restartFinalizeCue.policies().context().phases().isEmpty()
				&& restartFinalizeCue.policies().context().tasks().isEmpty()
				&& restartFinalizeCue.staticFallback().isPresent()
				&& restartFinalizeCue.nodes().stream().noneMatch(CallbackNode.class::isInstance),
			"restart=finalize acceptance must freeze its original targets without weakening live-strict cues"
		);
		var registeredEntityCue = catalog.messageCues().get(registeredEntityCueId);
		check(
			registeredEntityCue != null
				&& registeredEntityCue.nodes().size() == 2
				&& registeredEntityCue.nodes().stream().allMatch(TextNode.class::isInstance),
			"base fixture must publish both registered-entity acceptance entries"
		);
		var registeredEntityNodes = registeredEntityCue.nodes().stream()
			.map(TextNode.class::cast)
			.toList();
		check(
			registeredEntityNodes.stream().allMatch(node ->
				node.content().speaker().kind() == SpeakerKind.REGISTERED_ENTITY
			),
			"registered-entity acceptance entries must exercise the authoritative speaker path"
		);
		check(
			registeredEntityNodes.get(0).content().speaker().entity()
				.filter(Identifier.withDefaultNamespace("villager")::equals).isPresent()
				&& registeredEntityNodes.get(1).content().speaker().entity()
				.filter(id("not_a_registered_entity_type")::equals).isPresent(),
			"registered-entity fixture must retain one valid and one invalid registry ID"
		);
		var verifiedPlayerSpeakerCue = catalog.messageCues().get(verifiedPlayerSpeakerCueId);
		check(
			verifiedPlayerSpeakerCue != null
				&& verifiedPlayerSpeakerCue.nodes().size() == 1
				&& verifiedPlayerSpeakerCue.nodes().getFirst() instanceof TextNode,
			"base fixture must publish one verified-player speaker Chat node"
		);
		var verifiedAudience = verifiedPlayerSpeakerCue.policies().audience().audience().selectors();
		check(
			verifiedAudience.size() == 1
				&& verifiedAudience.getFirst().source() == AudienceSource.CALL_TARGETS
				&& verifiedAudience.getFirst().onlineOnly()
				&& verifiedPlayerSpeakerCue.policies().delivery().emptyAudience()
					== EmptyAudienceMode.FAIL,
			"verified-player speaker audience must contain only online call_targets"
		);
		var verifiedTarget = verifiedPlayerSpeakerCue.parameters().get("target");
		check(
			verifiedTarget != null
				&& verifiedTarget.required()
				&& verifiedTarget.onError().mode() == ParameterErrorMode.FAIL
				&& verifiedTarget.sources().stream().map(source -> source.kind()).toList()
					.equals(java.util.List.of(
						ParameterSourceKind.CALL_ARGUMENT,
						ParameterSourceKind.TARGET
					))
				&& verifiedTarget.allowedNodes().equals(
					java.util.Set.of("verified_player_chat")
				)
				&& verifiedTarget.audience().orElseThrow().selectors().size() == 1
				&& verifiedTarget.audience().orElseThrow().selectors().getFirst().source()
					== AudienceSource.CALL_TARGETS,
			"verified-player speaker must resolve only an explicit or unique call target"
		);
		TextNode verifiedPlayerNode = (TextNode)verifiedPlayerSpeakerCue.nodes().getFirst();
		check(
			verifiedPlayerNode.channel() == TextChannel.CHAT
				&& verifiedPlayerNode.content().speaker().kind() == SpeakerKind.PLAYER_PARAMETER
				&& verifiedPlayerNode.content().speaker().parameter().filter("target"::equals).isPresent()
				&& verifiedPlayerNode.variants().isEmpty()
				&& verifiedPlayerSpeakerCue.history().isEmpty(),
			"verified-player speaker must retain player_parameter(target) without identity variants"
		);

		DocumentKey effectKey = new DocumentKey(DefinitionType.TEXT_EFFECT, acceptanceEffectId);
		DocumentKey cueKey = new DocumentKey(DefinitionType.MESSAGE_CUE, captureCueId);
		DocumentKey policyKey = new DocumentKey(DefinitionType.MESSAGE_CUE, policyCueId);
		check(
			snapshot.sourceDocuments().get(effectKey).sourcePack()
				.equals("pixel-tzz-base-datapack"),
			"base effect source pack must be retained"
		);
		check(
			snapshot.sourceDocuments().get(cueKey).sourcePack()
				.equals("pixel-tzz-base-datapack"),
			"base cue source pack must be retained"
		);
		check(
			snapshot.sourceDocuments().get(policyKey).sourcePack()
				.equals("pixel-tzz-base-datapack"),
			"base policy cue source pack must be retained"
		);
		return new BaseFacts(
			snapshot.sourceDocuments().get(effectKey).sha256(),
			snapshot.sourceDocuments().get(cueKey).sha256(),
			snapshot.sourceDocuments().get(policyKey).sha256()
		);
	}

	private static void checkPressureFixture(
		final LoadedPacks loaded,
		final BaseFacts baseFacts
	) {
		var compilation = DefinitionCompiler.compile(
			loaded.sources().values().stream().toList(),
			loaded.functions(),
			loaded.predicates()
		);
		check(compilation.valid(), "V3B pressure fixture must compile: " + compilation.problems());
		var snapshot = compilation.snapshot().orElseThrow();
		var catalog = snapshot.messageCatalog();
		Identifier acceptanceEffectId = id("acceptance");
		Identifier captureCueId = id("acceptance/field_capture");
		Identifier policyCueId = id("acceptance/policy_matrix");
		Identifier maxFieldsCueId = id("pressure/max_fields");

		check(
			catalog.disabled().isEmpty(),
			"pressure fixture must not disable any message: " + catalog.disabled()
		);
		check(
			catalog.startBlockedGames().isEmpty(),
			"pressure overlay must leave the required cue executable"
		);
		check(catalog.textEffects().size() == 2, "overlay must replace, not duplicate, its effect");
		check(catalog.messageCues().size() == 10, "overlay must replace two cues and add one cue");

		var acceptance = catalog.textEffects().get(acceptanceEffectId);
		check(
			acceptance.typewriter().orElseThrow().characterInterval().nanoseconds() == 80_000_000L,
			"higher-priority effect must fully replace the base 45ms interval"
		);
		check(
			acceptance.characterSound().isEmpty(),
			"whole-resource override must not inherit the base character sound"
		);

		var captureCue = catalog.messageCues().get(captureCueId);
		check(captureCue.nodes().size() == 3, "higher-priority cue must replace all six base nodes");
		check(
			captureCue.policies().audience().audience().selectors().size() == 1
				&& captureCue.policies().audience().audience().selectors().getFirst().source()
					== AudienceSource.CALL_TARGETS
				&& captureCue.policies().audience().audience().selectors().getFirst().onlineOnly(),
			"pressure field_capture override must preserve call_targets-only audience isolation"
		);
		check(
			captureCue.defaults().priority() == 99,
			"higher-priority cue body must come from the pressure pack"
		);
		check(
			captureCue.fields().containsKey("pressure_marker")
				&& !captureCue.fields().containsKey("target_name"),
			"cue override must not deep-merge base fields"
		);

		var policyCue = catalog.messageCues().get(policyCueId);
		check(
			policyCue.nodes().size() == 1
				&& policyCue.nodes().getFirst().id().equals("pressure_notice"),
			"higher-priority policy cue must replace the complete base timeline"
		);
		check(
			policyCue.policies().concurrency().duplicate() == DuplicateMode.ALLOW
				&& policyCue.policies().concurrency().priority() == 88
				&& policyCue.parameters().isEmpty()
				&& policyCue.history().isEmpty()
				&& policyCue.staticFallback().isEmpty()
				&& policyCue.assets().isEmpty(),
			"policy override must not inherit base parameters, history, fallback, or assets"
		);

		var maxFields = catalog.messageCues().get(maxFieldsCueId);
		check(maxFields != null, "MAX_FIELDS pressure cue is missing");
		check(
			maxFields.fields().size() == MessageDefinitionParser.MAX_FIELDS,
			"pressure cue must compile exactly at MAX_FIELDS"
		);
		check(
			maxFields.fields().values().stream()
				.allMatch(field -> field.capture() == CaptureMode.PER_FIELD),
			"pressure fields must inherit the cue-level per_field capture mode"
		);

		DocumentKey effectKey = new DocumentKey(DefinitionType.TEXT_EFFECT, acceptanceEffectId);
		DocumentKey cueKey = new DocumentKey(DefinitionType.MESSAGE_CUE, captureCueId);
		DocumentKey policyKey = new DocumentKey(DefinitionType.MESSAGE_CUE, policyCueId);
		check(
			snapshot.sourceDocuments().get(effectKey).sourcePack()
				.equals("pixel-tzz-3b-pressure-datapack"),
			"effect source must identify the winning pressure pack"
		);
		check(
			snapshot.sourceDocuments().get(cueKey).sourcePack()
				.equals("pixel-tzz-3b-pressure-datapack"),
			"cue source must identify the winning pressure pack"
		);
		check(
			snapshot.sourceDocuments().get(policyKey).sourcePack()
				.equals("pixel-tzz-3b-pressure-datapack"),
			"policy cue source must identify the winning pressure pack"
		);
		check(
			!snapshot.sourceDocuments().get(effectKey).sha256().equals(baseFacts.effectSha256()),
			"overridden effect must have a different canonical hash"
		);
		check(
			!snapshot.sourceDocuments().get(cueKey).sha256().equals(baseFacts.cueSha256()),
			"overridden cue must have a different canonical hash"
		);
		check(
			!snapshot.sourceDocuments().get(policyKey).sha256()
				.equals(baseFacts.policySha256()),
			"overridden policy cue must have a different canonical hash"
		);
		check(
			loaded.source(DefinitionType.TEXT_EFFECT, acceptanceEffectId).sourcePack()
				.equals("pixel-tzz-3b-pressure-datapack"),
			"fixture loader must apply low-to-high pack priority before compilation"
		);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record BaseFacts(
		String effectSha256,
		String cueSha256,
		String policySha256
	) {
	}
}
