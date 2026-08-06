package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV5;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.network.CountdownControlContract;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.OperationKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Verifies the exact keep/clear boundary of both reset levels.
 */
public final class WorldResetAuthoritySelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier INITIAL_PHASE = id("setup");
	private static final Identifier DEFAULT_ROLE = id("runner");
	private static final Identifier DEFAULT_LIFE = id("alive");
	private static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private WorldResetAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		WorldStateV3 state = state();
		UUID newScope = UUID.fromString("00000000-0000-0000-0000-000000000099");
		var reset = WorldResetAuthority.resetGameProgress(
			state,
			definitions(),
			HOST,
			newScope
		);
		check(reset.successful(), "progress reset");
		WorldStateV3 next = reset.nextState().orElseThrow();
		check(next.core().host().orElseThrow().playerId().equals(HOST), "host retained");
		check(next.activeGameInstanceId().orElseThrow().equals(newScope), "new instance scope");
		check(next.timeline().isEmpty() && next.exclusiveReservations().isEmpty(), "runtime cleared");
		PlayerRecord player = next.core().players().get(PLAYER);
		check(player != null, "registered player retained");
		check(player.roleId().equals(DEFAULT_ROLE), "role reset");
		check(player.teamId().isEmpty() && player.persistentFields().isEmpty(), "player fields reset");
		check(
			!WorldResetAuthority.resetGameProgress(state, definitions(), PLAYER, UUID.randomUUID())
				.successful(),
			"non-host rejected"
		);
		WorldStateV3 cleared = WorldResetAuthority.clearAll(state, HOST)
			.nextState()
			.orElseThrow();
		check(cleared.core().activeGameId().isEmpty(), "full reset game");
		check(cleared.core().host().isEmpty() && cleared.core().players().isEmpty(), "full reset owners");
		checkActiveCountdownClearIsAtomic();
		System.out.println("WORLD_RESET_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkActiveCountdownClearIsAtomic() {
		WorldStateV3 seeded = state();
		UUID gameInstanceId = seeded.activeGameInstanceId().orElseThrow();
		Instance source = CountdownSelfCheckFixtures.start();
		Instance countdown = new Instance(
			gameInstanceId,
			source.countdownInstanceId(),
			source.purpose(),
			source.definition(),
			source.launchPlan(),
			source.lifecycle(),
			source.totalTicks(),
			source.participants(),
			source.hostId(),
			source.disconnectPolicies(),
			source.restrictions(),
			source.restrictionMarkers(),
			source.checkpoints(),
			source.emittedCheckpointIds(),
			source.callbackDefinitions(),
			source.callbackLedger()
		);
		PixelTzzWorldState wrapper = PixelTzzWorldState.initial();
		check(
			wrapper.commitV3(0L, ignored -> seeded).committed(),
			"countdown reset fixture core must commit"
		);
		MessageHistoryRecord history = history(gameInstanceId);
		check(
			wrapper.commitV4(
				1L,
				current -> current.withMessageHistory(List.of(history))
			).committed(),
			"countdown reset fixture must retain a schema-v4 history ledger"
		);
		check(
			wrapper.commitV5(2L, current -> current.withCountdown(Optional.of(countdown))).committed(),
			"countdown reset fixture must freeze an active countdown"
		);
		String clearDigest = PixelTzzServerRuntime.clearAllCountdownDigest(seeded, countdown);
		Instance advanced = CountdownAuthority.tick(
			countdown,
			countdown.lifecycle().lastServerTickAnchor() + 1L
		).instance().orElseThrow();
		check(
			advanced.lifecycle().stateVersion() == countdown.lifecycle().stateVersion()
				&& advanced.lifecycle().remainingTicks() < countdown.lifecycle().remainingTicks()
				&& clearDigest.equals(
					PixelTzzServerRuntime.clearAllCountdownDigest(seeded, advanced)
				),
			"clear-all review must survive routine remaining-time advancement"
		);
		check(
			PixelTzzServerRuntime.confirmationRequestRevisionMatches(
				new OperationKey(
					CountdownControlContract.CLEAR_ALL_STATE_OPERATION_TYPE,
					CountdownControlContract.CLEAR_ALL_STATE_OPERATION_ID
				),
				countdown.lifecycle().stateVersion(),
				wrapper.stateRevision()
			),
			"clear-all commit must use its frozen countdown binding instead of global revision"
		);
		check(
			!PixelTzzServerRuntime.confirmationRequestRevisionMatches(
				new OperationKey("reset_game_progress", id("reset/game_progress")),
				countdown.lifecycle().stateVersion(),
				wrapper.stateRevision()
			),
			"ordinary reset commit must still reject a mismatched global revision"
		);
		WorldStateV3 before = wrapper.currentV3().orElseThrow();
		long beforeRevision = before.stateRevision();
		WorldStateV3 cleared = WorldResetAuthority.clearAll(before, HOST)
			.nextState().orElseThrow();
		var committed = wrapper.commitV5(
			beforeRevision,
			current -> PixelTzzServerRuntime.clearAllEnvelope(current, cleared)
		);
		check(committed.committed(), "full reset and countdown retirement must commit atomically");
		var after = committed.state().orElseThrow();
		check(after.countdown().isEmpty(), "full reset must not leave an orphaned countdown");
		check(
			after.core().usages().isEmpty()
				&& after.core().invocations().isEmpty()
				&& after.core().messageHistory().isEmpty()
				&& after.core().messageRuntime().isEmpty(),
			"full reset must clear every schema-v4 action and message ledger"
		);
		check(
			after.core().core().core().host().isEmpty()
				&& after.core().core().core().players().isEmpty()
				&& after.core().core().core().activeGameId().isEmpty(),
			"full reset must clear host, players, and active game in the same transaction"
		);
		check(
			after.stateRevision() == beforeRevision + 1L,
			"atomic countdown clear must advance the global revision exactly once"
		);

		WorldStateV4 legacyResidue = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			WorldStateV3.initial(),
			List.of(),
			List.of(),
			List.of(history),
			Optional.empty()
		);
		WorldStateV5 noScopeResidue = new WorldStateV5(
			WorldStateV5.SCHEMA_VERSION,
			legacyResidue,
			Optional.empty()
		);
		WorldStateV5 scrubbed = PixelTzzServerRuntime.clearAllEnvelope(
			noScopeResidue,
			WorldStateV3.initial()
		);
		check(
			scrubbed.core().messageHistory().isEmpty()
				&& scrubbed.validated().error().isEmpty(),
			"clear-all must scrub residual v4 history even when both game-instance scopes are empty"
		);
	}

	private static MessageHistoryRecord history(final UUID gameInstanceId) {
		return new MessageHistoryRecord(
			gameInstanceId,
			PLAYER,
			id("test/history"),
			UUID.fromString("00000000-0000-0000-0000-000000000077"),
			Optional.empty(),
			100L,
			"历史记录",
			"清空全部数据时必须一并移除。",
			Map.of("fixture", "clear_all"),
			true
		);
	}

	private static WorldStateV3 state() {
		PlayerRecord player = new PlayerRecord(
			PLAYER,
			"PlayerB",
			id("hunter"),
			Optional.of(id("red")),
			id("captured"),
			Map.of(),
			Optional.empty(),
			Optional.empty(),
			new CompletionSummary(3L, Optional.of(100L)),
			1L,
			100L
		);
		WorldStateV2 core = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			7L,
			Optional.of(GAME),
			Optional.of(id("ended")),
			Optional.of(HostRecord.migratedLegacy(HOST)),
			Map.of(PLAYER, player),
			Optional.empty(),
			List.of(),
			AuditLog.empty(),
			Optional.empty()
		);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
			Optional.empty(),
			List.of(),
			List.of()
		);
	}

	private static DefinitionSnapshot definitions() {
		GameDefinition game = new GameDefinition(
			GAME,
			2,
			1,
			new RichText("{\"text\":\"Game\"}", "Game"),
			INITIAL_PHASE,
			DEFAULT_ROLE,
			DEFAULT_LIFE,
			Optional.empty()
		);
		return new DefinitionSnapshot(
			1L,
			Map.of(GAME, game),
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
			Set.of(),
			Set.of(),
			Map.of()
		);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void check(final boolean condition, final String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
