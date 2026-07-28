package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateContext;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateEvaluator;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ExecutionSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Evaluates the predicate documents frozen into one active flow snapshot.
 */
public final class FrozenPredicateEvaluator implements PredicateEvaluator {
	private final MinecraftServer server;
	private final Map<Identifier, String> predicateDocuments;
	private final Map<Identifier, LootItemCondition> parsed = new HashMap<>();

	public FrozenPredicateEvaluator(
		final MinecraftServer server,
		final ExecutionSnapshot snapshot
	) {
		this(
			server,
			Objects.requireNonNull(snapshot, "snapshot")
				.predicateDocuments()
				.entrySet()
				.stream()
				.collect(
					Collectors.toUnmodifiableMap(
						Map.Entry::getKey,
						entry -> entry.getValue().normalizedJson()
					)
				)
		);
	}

	/**
	 * Evaluates the currently active, already validated data-pack predicate documents.
	 */
	public FrozenPredicateEvaluator(
		final MinecraftServer server,
		final Map<Identifier, String> predicateDocuments
	) {
		this.server = Objects.requireNonNull(server, "server");
		this.predicateDocuments = Map.copyOf(
			Objects.requireNonNull(predicateDocuments, "predicateDocuments")
		);
	}

	@Override
	public boolean evaluate(
		final Identifier predicateId,
		final PredicateContext context
	) {
		Objects.requireNonNull(predicateId, "predicateId");
		Objects.requireNonNull(context, "context");
		if (!this.server.isSameThread()) {
			throw new IllegalStateException("flow predicates must run on the server thread");
		}
		ServerPlayer player = this.server.getPlayerList().getPlayer(context.playerId());
		if (player == null) {
			throw new IllegalStateException("branch player is no longer online");
		}
		LootItemCondition condition = this.parsed.computeIfAbsent(
			predicateId,
			this::parse
		);
		LootParams parameters = new LootParams.Builder(player.level())
			.withParameter(LootContextParams.ORIGIN, player.position())
			.withOptionalParameter(LootContextParams.THIS_ENTITY, player)
			.create(LootContextParamSets.COMMAND);
		LootContext lootContext = new LootContext.Builder(parameters)
			.create(java.util.Optional.empty());
		LootContext.VisitedEntry<LootItemCondition> visited = LootContext.createVisitedEntry(
			condition
		);
		if (!lootContext.pushVisitedElement(visited)) {
			throw new IllegalStateException("branch predicate contains a recursive loop");
		}
		try {
			return condition.test(lootContext);
		} finally {
			lootContext.popVisitedElement(visited);
		}
	}

	private LootItemCondition parse(final Identifier predicateId) {
		String document = this.predicateDocuments.get(predicateId);
		if (document == null) {
			throw new IllegalStateException(
				"frozen predicate document is unavailable: " + predicateId
			);
		}
		var parsedCondition = LootItemCondition.DIRECT_CODEC.parse(
			RegistryOps.create(JsonOps.INSTANCE, this.server.registryAccess()),
			JsonParser.parseString(document)
		);
		return parsedCondition.result().orElseThrow(
			() -> new IllegalStateException(
				"frozen predicate is invalid: "
					+ predicateId
					+ ": "
					+ parsedCondition.error().map(error -> error.message()).orElse("unknown error")
			)
		);
	}
}
