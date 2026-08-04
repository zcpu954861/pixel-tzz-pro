package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.MessageCatalog;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AssetSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageAssetType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload.Resolution;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure checks for the bounded multi-participant start gate used by host approval. */
public final class MessageServerAssetGateSelfCheck {
	private static final Identifier GAME = Identifier.parse("pixel_tzz:main");
	private static final Identifier SOUND = Identifier.parse("pixel_tzz:required_alert");

	private MessageServerAssetGateSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_SERVER_ASSET_GATE_SELF_CHECK=PASS");
	}

	public static void run() {
		checkEmptyManifestStillRequiresReport();
		checkOfflinePendingAndMissingBlockTogether();
		checkParticipantAndDiagnosticBounds();
	}

	private static void checkEmptyManifestStillRequiresReport() {
		MessageAssetAuthority authority = MessageAssetAuthority.fromSnapshot(
			snapshot(21L, List.of())
		);
		UUID participant = new UUID(0L, 1L);
		var issue = authority.openConnection(participant);
		check(
			issue.manifest().orElseThrow().entries().isEmpty(),
			"a valid empty catalog must issue an empty manifest"
		);
		var pending = MessageServerRuntime.evaluateRequiredAssetsForGame(
			authority,
			GAME,
			Set.of(participant),
			Set.of(participant),
			Map.of(participant, "playerB")
		);
		check(
			!pending.ready() && pending.blockedParticipantCount() == 1,
			"an empty manifest must still wait for this connection's report"
		);
		check(
			authority.acceptReport(
				participant,
				new MessageAssetReportC2SPayload(1L, 21L, 1L, List.of())
			).status() == MessageAssetAuthority.ReportStatus.ACCEPTED,
			"the matching empty report must be accepted"
		);
		check(
			MessageServerRuntime.evaluateRequiredAssetsForGame(
				authority,
				GAME,
				Set.of(participant),
				Set.of(participant),
				Map.of(participant, "playerB")
			).ready(),
			"the empty catalog must become ready only after its empty report"
		);
	}

	private static void checkOfflinePendingAndMissingBlockTogether() {
		MessageAssetAuthority authority = requiredAuthority(22L);
		UUID ready = new UUID(0L, 11L);
		UUID pending = new UUID(0L, 12L);
		UUID offline = new UUID(0L, 13L);
		UUID missing = new UUID(0L, 14L);
		MessageAssetManifestS2CPayload manifest = authority.openConnection(ready)
			.manifest()
			.orElseThrow();
		authority.openConnection(pending);
		authority.openConnection(missing);
		authority.acceptReport(ready, report(manifest, 1L, Resolution.PRESENT));
		authority.acceptReport(missing, report(manifest, 1L, Resolution.MISSING));

		Set<UUID> participants = Set.of(ready, pending, offline, missing);
		Set<UUID> online = Set.of(ready, pending, missing);
		Map<UUID, String> labels = Map.of(
			ready, "playerB",
			pending, "playerC",
			offline, "playerD",
			missing, "playerE"
		);
		var blocked = MessageServerRuntime.evaluateRequiredAssetsForGame(
			authority,
			GAME,
			participants,
			online,
			labels
		);
		check(
			!blocked.ready()
				&& blocked.participantCount() == 4
				&& blocked.blockedParticipantCount() == 3
				&& blocked.diagnostics().stream().anyMatch(value -> value.startsWith("playerC："))
				&& blocked.diagnostics().stream().anyMatch(value -> value.equals("playerD：玩家当前不在线。"))
				&& blocked.diagnostics().stream().anyMatch(value -> value.startsWith("playerE：")),
			"offline, pending, and missing participants must all block with named diagnostics"
		);

		authority.acceptReport(pending, report(manifest, 1L, Resolution.PRESENT));
		authority.acceptReport(missing, report(manifest, 2L, Resolution.PRESENT));
		authority.openConnection(offline);
		authority.acceptReport(offline, report(manifest, 1L, Resolution.PRESENT));
		check(
			MessageServerRuntime.evaluateRequiredAssetsForGame(
				authority,
				GAME,
				participants,
				participants,
				labels
			).ready(),
			"the aggregate gate must pass once every frozen participant is online and verified"
		);
	}

	private static void checkParticipantAndDiagnosticBounds() {
		MessageAssetAuthority authority = requiredAuthority(23L);
		Set<UUID> tooMany = new LinkedHashSet<>();
		for (int index = 0; index < 4_097; index++) {
			tooMany.add(new UUID(1L, index + 1L));
		}
		var overflow = MessageServerRuntime.evaluateRequiredAssetsForGame(
			authority,
			GAME,
			tooMany,
			Set.of(),
			Map.of()
		);
		check(
			!overflow.ready()
				&& overflow.participantCount() == 4_097
				&& overflow.diagnostics().size() == 1,
			"the aggregate gate must reject participant overflow before iterating it"
		);

		Set<UUID> pending = new LinkedHashSet<>();
		Map<UUID, String> labels = new LinkedHashMap<>();
		for (int index = 0; index < 65; index++) {
			UUID participant = new UUID(2L, index + 1L);
			pending.add(participant);
			labels.put(participant, "x".repeat(300));
			authority.openConnection(participant);
		}
		var bounded = MessageServerRuntime.evaluateRequiredAssetsForGame(
			authority,
			GAME,
			pending,
			pending,
			labels
		);
		check(
			bounded.blockedParticipantCount() == 65
				&& bounded.diagnostics().size() == 64
				&& bounded.diagnostics().stream().allMatch(value -> value.length() <= 256)
				&& bounded.message().length() <= 1_024,
			"diagnostic count, item length, and summary length must stay bounded"
		);
	}

	private static MessageAssetAuthority requiredAuthority(final long generation) {
		AssetSpec sound = new AssetSpec(
			MessageAssetType.SOUND,
			SOUND,
			MissingAssetMode.BLOCK_START,
			Optional.empty(),
			true,
			true
		);
		return MessageAssetAuthority.fromSnapshot(
			snapshot(
				generation,
				List.of(cue("pixel_tzz:required", List.of(sound)))
			)
		);
	}

	private static MessageAssetReportC2SPayload report(
		final MessageAssetManifestS2CPayload manifest,
		final long sequence,
		final Resolution resolution
	) {
		return new MessageAssetReportC2SPayload(
			sequence,
			manifest.generation(),
			manifest.manifestSequence(),
			manifest.entries().stream()
				.map(entry -> new MessageAssetReportC2SPayload.Entry(entry.type(), entry.id(), resolution))
				.toList()
		);
	}

	private static MessageCueDefinition cue(
		final String id,
		final List<AssetSpec> assets
	) {
		Identifier cueId = Identifier.parse(id);
		return new MessageCueDefinition(
			cueId,
			Optional.of(GAME),
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
		Map<Identifier, MessageCueDefinition> indexed = new LinkedHashMap<>();
		for (MessageCueDefinition cue : cues) {
			indexed.put(cue.id(), cue);
		}
		return new DefinitionSnapshot(
			generation,
			Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
			Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
			new MessageCatalog(Map.of(), indexed, Map.of(), Set.of()),
			Map.of(), Set.of(), Set.of(), Map.of()
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
