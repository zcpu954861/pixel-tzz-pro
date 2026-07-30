package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Additive world-state schema v4 envelope.
 *
 * <p>The accepted schema-v3 state remains the complete game-state core. This envelope adds the
 * bounded, server-authoritative ledgers required by registered player actions without changing the
 * behavior or storage layout of any existing v3 authority.
 */
public record WorldStateV4(
	int schemaVersion,
	WorldStateV3 core,
	List<PlayerActionUsage> usages,
	List<PlayerActionInvocation> invocations
) {
	public static final int SCHEMA_VERSION = 4;
	public static final int MAX_PLAYER_ACTION_USAGES = 16_384;
	public static final int MAX_PLAYER_ACTION_INVOCATIONS = 16_384;
	public static final int MAX_ACTION_RESULT_CODE_LENGTH = 128;
	public static final int MAX_ACTION_RESULT_SUMMARY_LENGTH = 512;

	private static final Codec<List<PlayerActionUsage>> USAGES_CODEC =
		PlayerActionUsage.CODEC.sizeLimitedListOf(MAX_PLAYER_ACTION_USAGES);
	private static final Codec<List<PlayerActionInvocation>> INVOCATIONS_CODEC =
		PlayerActionInvocation.CODEC.sizeLimitedListOf(MAX_PLAYER_ACTION_INVOCATIONS);

	public static final Codec<WorldStateV4> CODEC = Codec.lazyInitialized(
		() -> RecordCodecBuilder.<WorldStateV4>create(
			instance -> instance.group(
					Codec.INT.fieldOf("schema_version").forGetter(WorldStateV4::schemaVersion),
					WorldStateV3.CODEC.fieldOf("core").forGetter(WorldStateV4::core),
					USAGES_CODEC.fieldOf("player_action_usages").forGetter(WorldStateV4::usages),
					INVOCATIONS_CODEC
						.fieldOf("player_action_invocations")
						.forGetter(WorldStateV4::invocations)
				)
				.apply(instance, WorldStateV4::new)
		).validate(WorldStateV4::validated)
	);

	public WorldStateV4 {
		Objects.requireNonNull(core, "core");
		usages = Objects.requireNonNull(usages, "usages").stream()
			.sorted(PlayerActionUsage.ORDER)
			.toList();
		invocations = Objects.requireNonNull(invocations, "invocations").stream()
			.sorted(PlayerActionInvocation.ORDER)
			.toList();
	}

	public static WorldStateV4 initial() {
		return new WorldStateV4(
			SCHEMA_VERSION,
			WorldStateV3.initial(),
			List.of(),
			List.of()
		);
	}

	public long stateRevision() {
		return this.core.stateRevision();
	}

	/**
	 * Replaces the v3 core while preserving action ledgers only inside the same game-instance
	 * scope. Starting, resetting, or clearing a game therefore cannot inherit old action limits or
	 * replay results.
	 */
	public WorldStateV4 withCore(final WorldStateV3 value) {
		Objects.requireNonNull(value, "value");
		boolean sameScope = this.core.activeGameInstanceId().equals(value.activeGameInstanceId());
		return new WorldStateV4(
			this.schemaVersion,
			value,
			sameScope ? this.usages : List.of(),
			sameScope ? this.invocations : List.of()
		);
	}

	public WorldStateV4 withUsages(final List<PlayerActionUsage> value) {
		return new WorldStateV4(
			this.schemaVersion,
			this.core,
			value,
			this.invocations
		);
	}

	public WorldStateV4 withInvocations(final List<PlayerActionInvocation> value) {
		return new WorldStateV4(
			this.schemaVersion,
			this.core,
			this.usages,
			value
		);
	}

	public Optional<PlayerActionUsage> usage(
		final UUID gameInstanceId,
		final Identifier actionId,
		final Optional<UUID> playerId
	) {
		PlayerActionUsageKey key = new PlayerActionUsageKey(
			gameInstanceId,
			actionId,
			Objects.requireNonNull(playerId, "playerId")
		);
		return this.usages.stream().filter(value -> value.key().equals(key)).findFirst();
	}

	public Optional<PlayerActionInvocation> invocation(
		final UUID gameInstanceId,
		final UUID playerId,
		final Identifier actionId,
		final UUID requestId
	) {
		PlayerActionInvocationKey key = new PlayerActionInvocationKey(
			gameInstanceId,
			playerId,
			actionId,
			requestId
		);
		return this.invocations.stream().filter(value -> value.key().equals(key)).findFirst();
	}

	public DataResult<WorldStateV4> validated() {
		String error = validationError();
		return error == null ? DataResult.success(this) : DataResult.error(() -> error);
	}

	private String validationError() {
		if (this.schemaVersion != SCHEMA_VERSION) {
			return "schema v4 payload declared schema " + this.schemaVersion;
		}
		DataResult<WorldStateV3> coreValidation = this.core.validated();
		if (coreValidation.error().isPresent()) {
			return "schema v4 core is invalid: " + coreValidation.error().orElseThrow().message();
		}
		if (this.usages.size() > MAX_PLAYER_ACTION_USAGES) {
			return "player action usage count exceeds " + MAX_PLAYER_ACTION_USAGES;
		}
		if (this.invocations.size() > MAX_PLAYER_ACTION_INVOCATIONS) {
			return "player action invocation count exceeds " + MAX_PLAYER_ACTION_INVOCATIONS;
		}

		Optional<UUID> activeScope = this.core.activeGameInstanceId();
		Set<PlayerActionUsageKey> usageKeys = new HashSet<>();
		for (PlayerActionUsage usage : this.usages) {
			String nested = usage.validationError();
			if (nested != null) {
				return "player action usage: " + nested;
			}
			if (activeScope.filter(usage.gameInstanceId()::equals).isEmpty()) {
				return "player action usage does not belong to the active game instance";
			}
			if (!usageKeys.add(usage.key())) {
				return "duplicate player action usage key";
			}
		}

		Set<PlayerActionInvocationKey> invocationKeys = new HashSet<>();
		for (PlayerActionInvocation invocation : this.invocations) {
			String nested = invocation.validationError();
			if (nested != null) {
				return "player action invocation: " + nested;
			}
			if (activeScope.filter(invocation.gameInstanceId()::equals).isEmpty()) {
				return "player action invocation does not belong to the active game instance";
			}
			if (!invocationKeys.add(invocation.key())) {
				return "duplicate player action invocation key";
			}
		}
		return null;
	}

	/**
	 * Aggregate use count for either one player or the whole game instance.
	 *
	 * <p>An empty {@code playerId} is a deliberate global bucket, not an unknown player.
	 */
	public record PlayerActionUsage(
		UUID gameInstanceId,
		Identifier actionId,
		Optional<UUID> playerId,
		long uses,
		long lastUsedWorldTick
	) {
		private static final Comparator<PlayerActionUsage> ORDER = Comparator
			.comparing(PlayerActionUsage::gameInstanceId)
			.thenComparing(value -> value.actionId().toString())
			.thenComparing(value -> value.playerId().map(UUID::toString).orElse(""));

		public static final Codec<PlayerActionUsage> CODEC =
			RecordCodecBuilder.<PlayerActionUsage>create(
				instance -> instance.group(
						UUIDUtil.STRING_CODEC
							.fieldOf("game_instance_id")
							.forGetter(PlayerActionUsage::gameInstanceId),
						Identifier.CODEC.fieldOf("action_id").forGetter(PlayerActionUsage::actionId),
						UUIDUtil.STRING_CODEC
							.optionalFieldOf("player_id")
							.forGetter(PlayerActionUsage::playerId),
						Codec.LONG.fieldOf("uses").forGetter(PlayerActionUsage::uses),
						Codec.LONG
							.fieldOf("last_used_world_tick")
							.forGetter(PlayerActionUsage::lastUsedWorldTick)
					)
					.apply(instance, PlayerActionUsage::new)
			).validate(PlayerActionUsage::validated);

		public PlayerActionUsage {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(actionId, "actionId");
			playerId = Objects.requireNonNull(playerId, "playerId");
		}

		public PlayerActionUsageKey key() {
			return new PlayerActionUsageKey(this.gameInstanceId, this.actionId, this.playerId);
		}

		private DataResult<PlayerActionUsage> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.uses <= 0L) {
				return "uses must be positive";
			}
			return this.lastUsedWorldTick < 0L ? "last-used world tick cannot be negative" : null;
		}
	}

	/**
	 * Durable idempotency result for one accepted player request.
	 *
	 * <p>A persisted {@link PlayerActionInvocationStatus#PREPARED} record means the server cannot
	 * prove whether an external world mutation completed before shutdown. The caller must return an
	 * outcome-unknown refusal for that request instead of executing it again.
	 */
	public record PlayerActionInvocation(
		UUID gameInstanceId,
		UUID playerId,
		Identifier actionId,
		UUID requestId,
		PlayerActionInvocationStatus status,
		Optional<String> resultCode,
		Optional<String> resultSummary,
		long usedAtWorldTick
	) {
		private static final Comparator<PlayerActionInvocation> ORDER = Comparator
			.comparing(PlayerActionInvocation::gameInstanceId)
			.thenComparing(PlayerActionInvocation::playerId)
			.thenComparing(value -> value.actionId().toString())
			.thenComparing(PlayerActionInvocation::requestId);

		public static final Codec<PlayerActionInvocation> CODEC =
			RecordCodecBuilder.<PlayerActionInvocation>create(
				instance -> instance.group(
						UUIDUtil.STRING_CODEC
							.fieldOf("game_instance_id")
							.forGetter(PlayerActionInvocation::gameInstanceId),
						UUIDUtil.STRING_CODEC.fieldOf("player_id").forGetter(PlayerActionInvocation::playerId),
						Identifier.CODEC.fieldOf("action_id").forGetter(PlayerActionInvocation::actionId),
						UUIDUtil.STRING_CODEC.fieldOf("request_id").forGetter(PlayerActionInvocation::requestId),
						PlayerActionInvocationStatus.CODEC
							.fieldOf("status")
							.forGetter(PlayerActionInvocation::status),
						Codec.sizeLimitedString(MAX_ACTION_RESULT_CODE_LENGTH)
							.optionalFieldOf("result_code")
							.forGetter(PlayerActionInvocation::resultCode),
						Codec.sizeLimitedString(MAX_ACTION_RESULT_SUMMARY_LENGTH)
							.optionalFieldOf("result_summary")
							.forGetter(PlayerActionInvocation::resultSummary),
						Codec.LONG
							.fieldOf("used_at_world_tick")
							.forGetter(PlayerActionInvocation::usedAtWorldTick)
					)
					.apply(instance, PlayerActionInvocation::new)
			).validate(PlayerActionInvocation::validated);

		public PlayerActionInvocation {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
			Objects.requireNonNull(status, "status");
			resultCode = Objects.requireNonNull(resultCode, "resultCode");
			resultSummary = Objects.requireNonNull(resultSummary, "resultSummary");
		}

		public PlayerActionInvocationKey key() {
			return new PlayerActionInvocationKey(
				this.gameInstanceId,
				this.playerId,
				this.actionId,
				this.requestId
			);
		}

		public boolean outcomeUnknown() {
			return this.status == PlayerActionInvocationStatus.PREPARED;
		}

		private DataResult<PlayerActionInvocation> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.usedAtWorldTick < 0L) {
				return "used-at world tick cannot be negative";
			}
			if (this.resultCode.filter(String::isBlank).isPresent()) {
				return "result code cannot be blank";
			}
			if (this.resultSummary.filter(String::isBlank).isPresent()) {
				return "result summary cannot be blank";
			}
			if (
				this.status == PlayerActionInvocationStatus.PREPARED
					&& (this.resultCode.isPresent() || this.resultSummary.isPresent())
			) {
				return "prepared invocation cannot claim a result";
			}
			if (
				this.status != PlayerActionInvocationStatus.PREPARED
					&& this.resultCode.isEmpty()
			) {
				return "terminal invocation must carry a result code";
			}
			return null;
		}
	}

	private record PlayerActionUsageKey(
		UUID gameInstanceId,
		Identifier actionId,
		Optional<UUID> playerId
	) {
		private PlayerActionUsageKey {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(actionId, "actionId");
			playerId = Objects.requireNonNull(playerId, "playerId");
		}
	}

	private record PlayerActionInvocationKey(
		UUID gameInstanceId,
		UUID playerId,
		Identifier actionId,
		UUID requestId
	) {
		private PlayerActionInvocationKey {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(actionId, "actionId");
			Objects.requireNonNull(requestId, "requestId");
		}
	}

	public enum PlayerActionInvocationStatus implements StringRepresentable {
		PREPARED("prepared"),
		SUCCEEDED("succeeded"),
		FAILED("failed");

		public static final Codec<PlayerActionInvocationStatus> CODEC =
			StringRepresentable.fromEnum(PlayerActionInvocationStatus::values);

		private final String serializedName;

		PlayerActionInvocationStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}
}
