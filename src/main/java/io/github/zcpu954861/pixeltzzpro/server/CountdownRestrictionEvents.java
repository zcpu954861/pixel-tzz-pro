package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.RestrictedAction;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV5;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

/** Fabric event gates and the minimal runtime anchor required by movement freeze. */
public final class CountdownRestrictionEvents {
	private static final Map<UUID, MovementAnchor> MOVEMENT_ANCHORS = new HashMap<>();

	private CountdownRestrictionEvents() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
			blocked(player, RestrictedAction.ATTACK) ? InteractionResult.FAIL : InteractionResult.PASS
		);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			blocked(player, RestrictedAction.ATTACK) ? InteractionResult.FAIL : InteractionResult.PASS
		);
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
			blocked(player, RestrictedAction.INTERACT) ? InteractionResult.FAIL : InteractionResult.PASS
		);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			blocked(player, RestrictedAction.INTERACT) ? InteractionResult.FAIL : InteractionResult.PASS
		);
		UseItemCallback.EVENT.register((player, level, hand) ->
			blocked(player, RestrictedAction.ITEM_USE) ? InteractionResult.FAIL : InteractionResult.PASS
		);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			!blocked(player, RestrictedAction.ATTACK)
		);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || !blocked(player, RestrictedAction.DAMAGE)
		);
	}

	public static boolean blocked(final ServerPlayer player, final RestrictedAction action) {
		return CountdownServerRuntime.restrictionDecision(
			player.level().getServer(),
			player.getUUID(),
			action
		).blocked();
	}

	private static boolean blocked(
		final net.minecraft.world.entity.player.Player player,
		final RestrictedAction action
	) {
		return player instanceof ServerPlayer serverPlayer && blocked(serverPlayer, action);
	}

	public static void tickMovement(final MinecraftServer server) {
		var countdown = PixelTzzWorldState.get(server)
			.currentV5()
			.flatMap(WorldStateV5::countdown)
			.orElse(null);
		if (countdown == null || !countdown.restrictions().freezeMovement()) {
			MOVEMENT_ANCHORS.clear();
			return;
		}
		Set<UUID> active = countdown.restrictionMarkers()
			.stream()
			.filter(value -> value.active())
			.map(value -> value.participantId())
			.collect(java.util.stream.Collectors.toCollection(HashSet::new));
		MOVEMENT_ANCHORS.keySet().removeIf(playerId -> !active.contains(playerId));
		for (UUID playerId : active) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}
			MovementAnchor anchor = MOVEMENT_ANCHORS.get(playerId);
			if (
				anchor == null
					|| !anchor.countdownInstanceId().equals(countdown.countdownInstanceId())
			) {
				MOVEMENT_ANCHORS.put(
					playerId,
					new MovementAnchor(
						countdown.countdownInstanceId(),
						(ServerLevel) player.level(),
						player.position()
					)
				);
				player.setDeltaMovement(Vec3.ZERO);
				continue;
			}
			if (
				player.level() != anchor.level()
					|| player.position().distanceToSqr(anchor.position()) > 0.000_001D
			) {
				player.teleportTo(
					anchor.level(),
					anchor.position().x,
					anchor.position().y,
					anchor.position().z,
					Set.of(),
					player.getYRot(),
					player.getXRot(),
					true
				);
			}
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0.0F;
		}
	}

	public static void clear() {
		MOVEMENT_ANCHORS.clear();
	}

	private record MovementAnchor(
		UUID countdownInstanceId,
		ServerLevel level,
		Vec3 position
	) {
	}
}
