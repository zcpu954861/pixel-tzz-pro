package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.MessageCatalog;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AssetSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundRole;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DurationSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EffectUse;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageAssetType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodeHeader;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RepeatSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextLayout;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload.Resolution;
import io.github.zcpu954861.pixeltzzpro.server.MessageServerAssetGateSelfCheck;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority.DiagnosticCode;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority.IssueStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority.ReportStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure aggregation, per-connection sequence, and game start-gate checks. */
public final class MessageAssetAuthoritySelfCheck {
	private static final Identifier GAME = Identifier.parse("pixel_tzz:main");
	private static final Identifier OTHER_GAME = Identifier.parse(
		"pixel_tzz:other"
	);
	private static final Identifier FONT = Identifier.parse("pixel_tzz:display");
	private static final Identifier SOUND = Identifier.parse("pixel_tzz:alert");
	private static final Identifier LATE_SOUND = Identifier.parse("pixel_tzz:late");
	private static final Identifier FALLBACK_SOUND = Identifier.parse("minecraft:ui.button.click");

	private MessageAssetAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_ASSET_AUTHORITY_SELF_CHECK=PASS");
	}

	public static void run() {
		checkDedupSequenceAndGameGate();
		checkConflictsFailClosed();
		checkManifestLimitFailsClosed();
		checkConnectionLimitAndImmutability();
		checkImplicitBlockStartSound();
		checkImplicitFallbackAndSilentSounds();
		checkImplicitCharacterSound();
		MessageAssetPlaybackResolverSelfCheck.run();
		MessageServerAssetGateSelfCheck.run();
		MessageDeliveryLifecycleAuthoritySelfCheck.run();
		HostMessagePreviewSanitizerSelfCheck.run();
		MessageHistoryMaterializerSelfCheck.run();
	}

	private static void checkDedupSequenceAndGameGate() {
		AssetSpec font = new AssetSpec(
			MessageAssetType.FONT,
			FONT,
			MissingAssetMode.FALLBACK,
			Optional.of(Identifier.parse("minecraft:uniform")),
			true,
			false
		);
		AssetSpec sound = requiredSound(SOUND);
		MessageCueDefinition first = cue(
			"pixel_tzz:first",
			Optional.of(GAME),
			List.of(font, sound)
		);
		MessageCueDefinition duplicate = cue(
			"pixel_tzz:duplicate",
			Optional.of(GAME),
			List.of(font)
		);
		MessageCueDefinition late = cue(
			"pixel_tzz:late",
			Optional.of(GAME),
			List.of(
				new AssetSpec(
					MessageAssetType.SOUND,
					LATE_SOUND,
					MissingAssetMode.SILENT,
					Optional.empty(),
					false,
					false
				)
			)
		);
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(7L, List.of(first, duplicate, late))
		);
		check(!authority.catalog().blocked(), "valid assets must compile a manifest");
		check(
			authority.catalog().manifest().orElseThrow().entries().size() == 3,
			"identical type+id declarations must deduplicate"
		);

		UUID connection = UUID.fromString(
			"00000000-0000-0000-0000-000000000401"
		);
		var issue = authority.openConnection(connection);
		check(issue.status() == IssueStatus.ISSUED, "connection must receive manifest");
		check(
			!authority.requiredAssetsForGame(connection, GAME).ready()
				&& authority.requiredAssetsForGame(connection, GAME)
					.diagnostics().getFirst().code() == DiagnosticCode.REPORT_PENDING,
			"required assets must wait for this connection's report"
		);

		MessageAssetManifestS2CPayload manifest = issue.manifest().orElseThrow();
		check(
			manifest.manifestSequence() == 1L
				&& manifest.entries().size() == 2
				&& manifest.entries().stream().noneMatch(
					entry -> entry.id().equals(LATE_SOUND)
				),
			"connection issue must contain only preload and required assets"
		);
		Map<AssetKey, Resolution> initialResolutions = new LinkedHashMap<>();
		initialResolutions.put(
			new AssetKey(MessageAssetManifestS2CPayload.AssetType.FONT, FONT),
			Resolution.FALLBACK_USED
		);
		initialResolutions.put(
			new AssetKey(MessageAssetManifestS2CPayload.AssetType.SOUND, SOUND),
			Resolution.MISSING
		);
		var missingSound = report(manifest, 1L, initialResolutions);
		check(
			authority.acceptReport(connection, missingSound).status()
				== ReportStatus.ACCEPTED,
			"matching first report must be accepted"
		);
		var effective = authority.effectiveAssets(connection).orElseThrow();
		check(
			effective.get(new AssetKey(MessageAssetManifestS2CPayload.AssetType.FONT, FONT))
				.equals(Optional.of(Identifier.parse("minecraft:uniform")))
				&& effective.get(new AssetKey(MessageAssetManifestS2CPayload.AssetType.SOUND, SOUND))
				.isEmpty(),
			"accepted reports must retain the actual fallback and silent playback choices"
		);
		var blocked = authority.requiredAssetsForGame(connection, GAME);
		check(
			!blocked.ready()
				&& blocked.requiredCount() == 1
				&& blocked.missingCount() == 1
				&& blocked.diagnostics().getFirst().code()
					== DiagnosticCode.REQUIRED_ASSET_MISSING
				&& blocked.diagnostics().getFirst().message()
					.equals("缺少开局必需消息资源：sound pixel_tzz:alert"),
			"game gate must expose a stable missing-required diagnostic"
		);
		check(
			authority.acceptReport(connection, missingSound).status()
				== ReportStatus.NON_INCREASING_SEQUENCE,
			"duplicate report sequence must be rejected"
		);

		MessageAssetReportC2SPayload wrongGeneration = new MessageAssetReportC2SPayload(
			2L,
			8L,
			missingSound.entries()
		);
		check(
			authority.acceptReport(connection, wrongGeneration).status()
				== ReportStatus.GENERATION_MISMATCH,
			"a report for an unissued generation must be rejected"
		);
		check(
			authority.acceptReport(
				connection,
				report(manifest, 2L, Map.of())
			).status() == ReportStatus.NON_INCREASING_SEQUENCE,
			"a structurally rejected sequence must still be consumed"
		);
		check(
			authority.acceptReport(
				connection,
				report(manifest, 3L, Map.of())
			).status() == ReportStatus.ACCEPTED
				&& authority.requiredAssetsForGame(connection, GAME).ready(),
			"a later reload report must recover the game gate"
		);
		check(
			authority.markReportPending(connection)
				&& !authority.requiredAssetsForGame(connection, GAME).ready()
				&& authority.acceptReport(
					connection,
					report(manifest, 4L, Map.of())
				).status() == ReportStatus.ACCEPTED,
			"resource reload must invalidate readiness until a higher-sequence report arrives"
		);
		check(
			authority.requiredAssetsForGame(connection, OTHER_GAME).ready(),
			"game-specific required assets must not leak into another game"
		);
		var extension = authority.declareCueAssets(
			connection,
			Identifier.parse("pixel_tzz:late")
		);
		check(
			extension.status() == IssueStatus.ISSUED
				&& extension.manifest().orElseThrow().manifestSequence() == 2L
				&& extension.manifest().orElseThrow().entries().size() == 3
				&& extension.manifest().orElseThrow().entries().stream().anyMatch(
					entry -> entry.id().equals(LATE_SOUND) && !entry.preload()
				),
			"first cue playback must issue one cumulative asset extension"
		);
		check(
			authority.declareCueAssets(
				connection,
				Identifier.parse("pixel_tzz:late")
			).status() == IssueStatus.ALREADY_DECLARED,
			"repeated cue playback must not reissue an unchanged manifest"
		);
		check(
			authority.acceptReport(
				connection,
				report(manifest, 5L, Map.of())
			).status() == ReportStatus.MANIFEST_SEQUENCE_MISMATCH,
			"a late report for the superseded connection manifest must be rejected"
		);
		check(
			authority.acceptReport(
				connection,
				report(extension.manifest().orElseThrow(), 6L, Map.of())
			).status() == ReportStatus.ACCEPTED,
			"the newest cumulative manifest report must recover readiness"
		);
		check(
			authority.acceptReport(UUID.randomUUID(), report(manifest, 1L, Map.of()))
				.status() == ReportStatus.MANIFEST_NOT_ISSUED,
			"reports must match a manifest issued to the same connection"
		);
	}

	private static void checkConflictsFailClosed() {
		AssetSpec required = requiredSound(SOUND);
		AssetSpec optional = new AssetSpec(
			MessageAssetType.SOUND,
			SOUND,
			MissingAssetMode.SILENT,
			Optional.empty(),
			false,
			false
		);
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(
				8L,
				List.of(
					cue("pixel_tzz:a", Optional.of(GAME), List.of(required)),
					cue("pixel_tzz:b", Optional.of(GAME), List.of(optional))
				)
			)
		);
		check(
			authority.catalog().blocked()
				&& authority.catalog().diagnostics().getFirst().code()
					== DiagnosticCode.ASSET_DECLARATION_CONFLICT
				&& authority.openConnection(UUID.randomUUID()).status()
					== IssueStatus.CATALOG_BLOCKED,
			"conflicting type+id declarations must fail closed before sending"
		);
	}

	private static void checkManifestLimitFailsClosed() {
		List<AssetSpec> first = new ArrayList<>();
		for (int index = 0; index < MessageAssetAuthority.MAX_MANIFEST_ENTRIES; index++) {
			first.add(
				new AssetSpec(
					MessageAssetType.FONT,
					Identifier.parse("pixel_tzz:font_" + index),
					MissingAssetMode.SILENT,
					Optional.empty(),
					false,
					false
				)
			);
		}
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(
				9L,
				List.of(
					cue("pixel_tzz:a", Optional.of(GAME), first),
					cue(
						"pixel_tzz:b",
						Optional.of(GAME),
						List.of(
							new AssetSpec(
								MessageAssetType.SOUND,
								Identifier.parse("pixel_tzz:overflow"),
								MissingAssetMode.SILENT,
								Optional.empty(),
								false,
								false
							)
						)
					)
				)
			)
		);
		check(
			authority.catalog().blocked()
				&& authority.catalog().diagnostics().stream().anyMatch(
					diagnostic -> diagnostic.code()
						== DiagnosticCode.MANIFEST_LIMIT_EXCEEDED
				),
			"a 65th unique asset must explicitly block the catalog"
		);
	}

	private static void checkConnectionLimitAndImmutability() {
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(10L, List.of())
		);
		for (int index = 0; index < MessageAssetAuthority.MAX_CONNECTIONS; index++) {
			check(
				authority.openConnection(new UUID(0L, index + 1L)).status()
					== IssueStatus.ISSUED,
				"connections within the hard limit must be admitted"
			);
		}
		check(
			authority.openConnection(new UUID(1L, 1L)).status()
				== IssueStatus.CONNECTION_LIMIT,
			"the connection authority must enforce its hard cap"
		);
		expectUnsupported(
			() -> authority.catalog().diagnostics().add(null),
			"catalog diagnostics must be immutable"
		);
		expectUnsupported(
			() -> authority.catalog().requiredAssetsByGame().put(GAME, Set.of()),
			"game asset map must be immutable"
		);
	}

	private static void checkImplicitBlockStartSound() {
		SoundNode sound = new SoundNode(
			new NodeHeader(
				"required_sound",
				new AtCueTime(new TimeSpan(0L)),
				NodePolicy.inherited(),
				Optional.empty(),
				RepeatSpec.once()
			),
			SOUND,
			SoundCategory.UI,
			1.0F,
			1.0F,
			SoundAttachment.PLAYER,
			MissingAssetMode.BLOCK_START,
			Optional.empty()
		);
		MessageCueDefinition cue = new MessageCueDefinition(
			Identifier.parse("pixel_tzz:implicit_required"),
			Optional.of(GAME),
			false,
			CuePolicies.standard(),
			Map.of(),
			Map.of(),
			List.of(sound),
			Optional.empty(),
			Optional.empty(),
			List.of(),
			SoftLimits.empty(),
			"{}"
		);
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(11L, List.of(cue))
		);
		UUID connection = new UUID(3L, 1L);
		MessageAssetManifestS2CPayload manifest = authority.openConnection(connection)
			.manifest()
			.orElseThrow();
		check(
			manifest.entries().size() == 1
				&& manifest.entries().getFirst().id().equals(SOUND)
				&& manifest.entries().getFirst().missing()
					== MessageAssetManifestS2CPayload.MissingMode.BLOCK_START
				&& manifest.entries().getFirst().requiredForStart(),
			"a sound-node block_start policy must enter the initial required manifest"
		);
		authority.acceptReport(
			connection,
			report(manifest, 1L, Map.of(manifest.entries().getFirst().key(), Resolution.MISSING))
		);
		check(
			!authority.requiredAssetsForGame(connection, GAME).ready(),
			"an implicitly declared block_start sound must fail the authoritative start gate"
		);
	}

	private static void checkImplicitFallbackAndSilentSounds() {
		Identifier silentId = Identifier.parse("pixel_tzz:optional_silent");
		SoundNode fallback = soundNode(
			"fallback_sound",
			LATE_SOUND,
			MissingAssetMode.FALLBACK,
			Optional.of(FALLBACK_SOUND)
		);
		SoundNode silent = soundNode(
			"silent_sound",
			silentId,
			MissingAssetMode.SILENT,
			Optional.empty()
		);
		MessageCueDefinition cue = new MessageCueDefinition(
			Identifier.parse("pixel_tzz:implicit_optional"),
			Optional.of(GAME),
			false,
			CuePolicies.standard(),
			Map.of(),
			Map.of(),
			List.of(fallback, silent),
			Optional.empty(),
			Optional.empty(),
			List.of(),
			SoftLimits.empty(),
			"{}"
		);
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(12L, List.of(cue))
		);
		UUID connection = new UUID(3L, 2L);
		check(
			authority.openConnection(connection).manifest().orElseThrow().entries().isEmpty(),
			"non-preloaded sound uses must not leak into the connection manifest"
		);
		MessageAssetManifestS2CPayload manifest = authority.declareCueAssets(
			connection,
			cue.id()
		).manifest().orElseThrow();
		check(
			manifest.entries().size() == 2,
			"first playback must extend the manifest with every used optional sound"
		);
		Map<AssetKey, Resolution> outcomes = new LinkedHashMap<>();
		outcomes.put(
			new AssetKey(MessageAssetManifestS2CPayload.AssetType.SOUND, LATE_SOUND),
			Resolution.FALLBACK_USED
		);
		outcomes.put(
			new AssetKey(MessageAssetManifestS2CPayload.AssetType.SOUND, silentId),
			Resolution.MISSING
		);
		authority.acceptReport(connection, report(manifest, 1L, outcomes));
		var effective = authority.effectiveAssets(connection).orElseThrow();
		check(
			effective.get(new AssetKey(MessageAssetManifestS2CPayload.AssetType.SOUND, LATE_SOUND))
				.equals(Optional.of(FALLBACK_SOUND))
				&& effective.get(new AssetKey(MessageAssetManifestS2CPayload.AssetType.SOUND, silentId))
				.isEmpty(),
			"implicit fallback and silent sound uses must produce real playback selections"
		);
	}

	private static SoundNode soundNode(
		final String id,
		final Identifier sound,
		final MissingAssetMode missing,
		final Optional<Identifier> fallback
	) {
		return new SoundNode(
			new NodeHeader(
				id,
				new AtCueTime(new TimeSpan(0L)),
				NodePolicy.inherited(),
				Optional.empty(),
				RepeatSpec.once()
			),
			sound,
			SoundCategory.UI,
			1.0F,
			1.0F,
			SoundAttachment.PLAYER,
			missing,
			fallback
		);
	}

	private static void checkImplicitCharacterSound() {
		Identifier effectId = Identifier.parse("pixel_tzz:implicit_character_effect");
		Identifier soundId = Identifier.parse("pixel_tzz:implicit_character_sound");
		TextEffectDefinition effect = new TextEffectDefinition(
			effectId,
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.of(
				new CharacterSoundSpec(
					soundId,
					SoundCategory.UI,
					1,
					true,
					true,
					true,
					1.0F,
					1.0F,
					0.0F,
					SoundAttachment.PLAYER,
					MissingAssetMode.FALLBACK,
					Optional.of(FALLBACK_SOUND)
				)
			),
			16,
			"{}"
		);
		TextContent content = new TextContent(
			new LocalizedTemplate(
				new ComponentTemplate(
					List.of(
						new StaticComponentPart(
							new RichComponent("{\"text\":\"字符音\"}", "字符音")
						)
					)
				),
				Map.of()
			),
			Map.of(),
			Optional.of(new EffectUse(Optional.of(effectId), TextEffectPatch.empty())),
			TextLayout.standard(),
			DurationSpec.contentDriven(),
			SpeakerSpec.system(),
			CharacterSoundRole.PRIMARY,
			Optional.empty()
		);
		TextNode text = new TextNode(
			new NodeHeader(
				"character_text",
				new AtCueTime(new TimeSpan(0L)),
				NodePolicy.inherited(),
				Optional.empty(),
				RepeatSpec.once()
			),
			TextChannel.SUBTITLE,
			content,
			List.of()
		);
		MessageCueDefinition cue = new MessageCueDefinition(
			Identifier.parse("pixel_tzz:implicit_character"),
			Optional.of(GAME),
			false,
			CuePolicies.standard(),
			Map.of(),
			Map.of(),
			List.of(text),
			Optional.empty(),
			Optional.empty(),
			List.of(),
			SoftLimits.empty(),
			"{}"
		);
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(13L, List.of(cue), Map.of(effectId, effect))
		);
		UUID connection = new UUID(3L, 3L);
		authority.openConnection(connection);
		MessageAssetManifestS2CPayload manifest = authority.declareCueAssets(
			connection,
			cue.id()
		).manifest().orElseThrow();
		check(
			manifest.entries().size() == 1
				&& manifest.entries().getFirst().id().equals(soundId)
				&& manifest.entries().getFirst().fallback().equals(Optional.of(FALLBACK_SOUND)),
			"a primary character sound must enter the cue manifest with its fallback policy"
		);
	}

	private static AssetSpec requiredSound(final Identifier id) {
		return new AssetSpec(
			MessageAssetType.SOUND,
			id,
			MissingAssetMode.BLOCK_START,
			Optional.empty(),
			true,
			true
		);
	}

	private static MessageAssetReportC2SPayload report(
		final MessageAssetManifestS2CPayload manifest,
		final long sequence,
		final Map<AssetKey, Resolution> overrides
	) {
		return new MessageAssetReportC2SPayload(
			sequence,
			manifest.generation(),
			manifest.manifestSequence(),
			manifest.entries().stream()
				.map(
					entry -> new MessageAssetReportC2SPayload.Entry(
						entry.type(),
						entry.id(),
							overrides.getOrDefault(entry.key(), Resolution.PRESENT)
					)
				)
				.toList()
		);
	}

	private static MessageCueDefinition cue(
		final String id,
		final Optional<Identifier> game,
		final List<AssetSpec> assets
	) {
		Identifier cueId = Identifier.parse(id);
		return new MessageCueDefinition(
			cueId,
			game,
			false,
			CuePolicies.standard(),
			Map.of(),
			Map.of(),
			List.of(),
			Optional.empty(),
			Optional.empty(),
			assets,
			SoftLimits.empty(),
			"{}"
		);
	}

	private static DefinitionSnapshot snapshot(
		final long generation,
		final List<MessageCueDefinition> cues
	) {
		return snapshot(generation, cues, Map.of());
	}

	private static DefinitionSnapshot snapshot(
		final long generation,
		final List<MessageCueDefinition> cues,
		final Map<Identifier, TextEffectDefinition> effects
	) {
		Map<Identifier, MessageCueDefinition> indexed = new LinkedHashMap<>();
		for (MessageCueDefinition cue : cues) {
			indexed.put(cue.id(), cue);
		}
		return new DefinitionSnapshot(
			generation,
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			new MessageCatalog(effects, indexed, Map.of(), Set.of()),
			Map.of(),
			Set.of(),
			Set.of(),
			Map.of()
		);
	}

	private static void expectUnsupported(
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected immutable result.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
