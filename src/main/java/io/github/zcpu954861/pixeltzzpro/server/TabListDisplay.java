package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the vanilla player-list display name from authoritative Pixel TZZ state.
 */
public final class TabListDisplay {
	private static final int HOST_GOLD = 0xD7B45A;
	private static final Identifier BACKSTAGE_TAG = Identifier.fromNamespaceAndPath(
		"pixel_tzz",
		"backstage"
	);
	private static final Identifier SPECTATOR_TAG = Identifier.fromNamespaceAndPath(
		"pixel_tzz",
		"spectator"
	);

	private TabListDisplay() {
	}

	public static @Nullable Component resolve(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return null;
		}
		var state = PixelTzzWorldState.get(server).currentV2().orElse(null);
		if (state == null) {
			return null;
		}
		if (state.host().filter(host -> host.playerId().equals(player.getUUID())).isPresent()) {
			return format("[主持人]", player.getPlainTextName(), HOST_GOLD);
		}
		PlayerRecord record = state.players().get(player.getUUID());
		if (record == null) {
			return null;
		}
		var definitions = DefinitionRegistry.INSTANCE.view().active();
		var role = definitions.roles().get(record.roleId());
		var team = record.teamId().map(definitions.teams()::get).orElse(null);
		boolean spectator = role != null && role.tags().contains(SPECTATOR_TAG);
		boolean explicitRole = state.activeGameId()
			.map(definitions.games()::get)
			.map(GameDefinition::defaultRole)
			.filter(defaultRole -> !defaultRole.equals(record.roleId()))
			.isPresent();
		if (!shouldExposeRole(
			spectator,
			record.completionSummary().naturalCompletionCount(),
			explicitRole
		)) {
			return null;
		}
		if (role == null && team == null) {
			return null;
		}
		TabStyle roleStyle = role == null
			? new TabStyle(Optional.empty(), Optional.empty())
			: role.tab();
		TabStyle teamStyle = team == null
			? new TabStyle(Optional.empty(), Optional.empty())
			: team.tab();
		String prefix = roleStyle.prefix().map(value -> value.plainText()).orElse("");
		String teamPrefix = teamStyle.prefix().map(value -> value.plainText()).orElse("");
		if (!teamPrefix.isBlank()) {
			prefix = prefix.isBlank() ? teamPrefix : prefix + teamPrefix;
		}
		int color = parseColor(
			teamStyle.color().or(() -> roleStyle.color()).orElse("")
		).orElse(0xFFFFFF);
		return prefix.isBlank() && color == 0xFFFFFF
			? null
			: format(prefix, player.getPlainTextName(), color);
	}

	public static int resolveOrder(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return 0;
		}
		var state = PixelTzzWorldState.get(server).currentV2().orElse(null);
		if (state == null) {
			return 0;
		}
		boolean host = state.host()
			.filter(value -> value.playerId().equals(player.getUUID()))
			.isPresent();
		PlayerRecord record = state.players().get(player.getUUID());
		if (record == null) {
			return sortOrder(host, false, false, false);
		}
		var definitions = DefinitionRegistry.INSTANCE.view().active();
		var role = definitions.roles().get(record.roleId());
		boolean backstage = role != null && role.tags().contains(BACKSTAGE_TAG);
		boolean spectator = role != null && role.tags().contains(SPECTATOR_TAG);
		boolean explicitRole = state.activeGameId()
			.map(definitions.games()::get)
			.map(GameDefinition::defaultRole)
			.filter(defaultRole -> !defaultRole.equals(record.roleId()))
			.isPresent();
		boolean initialized = shouldExposeRole(
			spectator,
			record.completionSummary().naturalCompletionCount(),
			explicitRole
		);
		return sortOrder(host, backstage, spectator, initialized);
	}

	static boolean shouldExposeRole(
		final boolean spectator,
		final long naturalCompletionCount,
		final boolean explicitRole
	) {
		return spectator || naturalCompletionCount > 0L || explicitRole;
	}

	static int sortOrder(
		final boolean host,
		final boolean backstage,
		final boolean spectator,
		final boolean initialized
	) {
		if (host) {
			return 400;
		}
		if (backstage) {
			return 300;
		}
		if (spectator) {
			return 0;
		}
		return initialized ? 200 : 100;
	}

	public static Component format(
		final String prefix,
		final String playerName,
		final int color
	) {
		MutableComponent result = Component.empty();
		if (!prefix.isBlank()) {
			result.append(Component.literal(prefix + " ").withColor(color));
		}
		return result.append(Component.literal(playerName).withColor(color));
	}

	private static Optional<Integer> parseColor(final String value) {
		if (!value.matches("#[0-9a-fA-F]{6}")) {
			return Optional.empty();
		}
		return Optional.of(Integer.parseInt(value.substring(1), 16));
	}
}
