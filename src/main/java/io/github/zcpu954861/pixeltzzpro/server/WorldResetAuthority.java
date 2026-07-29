package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure transactions for the two explicit destructive reset levels.
 */
public final class WorldResetAuthority {
	private WorldResetAuthority() {
	}

	public static Result resetGameProgress(
		final WorldStateV3 current,
		final DefinitionSnapshot definitions,
		final UUID actorId,
		final UUID nextGameInstanceId
	) {
		String contextFailure = contextFailure(current, actorId);
		if (contextFailure != null) {
			return Result.failed(OperationCode.NOT_HOST, contextFailure);
		}
		if (nextGameInstanceId == null) {
			return Result.failed(OperationCode.TARGET_INVALID, "new game-instance ID is missing");
		}
		var gameId = current.core().activeGameId().orElse(null);
		GameDefinition game = gameId == null ? null : definitions.games().get(gameId);
		if (game == null) {
			return Result.failed(
				OperationCode.DEFINITION_UNAVAILABLE,
				"active game definition is unavailable"
			);
		}

		Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
		current.core().players().forEach(
			(playerId, player) -> players.put(
				playerId,
				new PlayerRecord(
					player.playerId(),
					player.lastKnownName(),
					game.defaultRole(),
					Optional.empty(),
					game.defaultLifeState(),
					Map.of(),
					Optional.empty(),
					Optional.empty(),
					new CompletionSummary(0L, Optional.empty()),
					player.firstSeenAtEpochMillis(),
					player.lastSeenAtEpochMillis()
				)
			)
		);
		WorldStateV2 core = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			current.stateRevision(),
			Optional.of(game.id()),
			Optional.of(game.initialPhase()),
			current.core().host(),
			players,
			Optional.empty(),
			List.of(),
			current.core().audit(),
			current.core().recoverySnapshotMetadata()
		);
		return Result.changed(
			new WorldStateV3(
				WorldStateV3.SCHEMA_VERSION,
				core,
				Optional.of(nextGameInstanceId),
				Optional.empty(),
				List.of(),
				current.recoverySnapshots()
			),
			"game progress reset"
		);
	}

	public static Result clearAll(final WorldStateV3 current, final UUID actorId) {
		String contextFailure = contextFailure(current, actorId);
		if (contextFailure != null) {
			return Result.failed(OperationCode.NOT_HOST, contextFailure);
		}
		return Result.changed(WorldStateV3.initial(), "all Pixel TZZ state cleared");
	}

	private static String contextFailure(final WorldStateV3 current, final UUID actorId) {
		if (current == null || actorId == null) {
			return "reset context is missing";
		}
		return current.core().host()
			.filter(host -> host.playerId().equals(actorId))
			.isPresent()
			? null
			: "only the current host can reset Pixel TZZ state";
	}

	public record Result(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState
	) {
		public Result {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			if (code != OperationCode.SUCCESS && nextState.isPresent()) {
				throw new IllegalArgumentException("failed reset cannot carry state");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static Result changed(final WorldStateV3 next, final String message) {
			return new Result(OperationCode.SUCCESS, message, Optional.of(next));
		}

		private static Result failed(final OperationCode code, final String message) {
			return new Result(code, message, Optional.empty());
		}
	}
}
