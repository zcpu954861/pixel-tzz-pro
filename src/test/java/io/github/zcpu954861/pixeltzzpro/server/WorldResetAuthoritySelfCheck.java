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
		System.out.println("WORLD_RESET_AUTHORITY_SELF_CHECK=PASS");
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
