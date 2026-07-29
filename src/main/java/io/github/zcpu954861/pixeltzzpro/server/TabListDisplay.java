package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabColorMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabPrefixMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChange;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the vanilla player-list display name from authoritative Pixel TZZ state.
 *
 * <p>All ordinary player styling is data-driven. A role gates the whole composed identity until
 * its current role-assignment flow has a matching successful instance credential; team and life
 * state then layer on top. The host is the sole system-owned final override.
 */
public final class TabListDisplay {
	private static final int HOST_GOLD = 0xD7B45A;
	private static final int HOST_SORT_ORDER = -10_001;
	private static final RichText HOST_PREFIX = new RichText(
		"{\"text\":\"[主持人] \"}",
		"[主持人] "
	);

	private TabListDisplay() {
	}

	public static @Nullable Component resolve(final ServerPlayer player) {
		Optional<ResolvedStyle> style = resolveStyle(player);
		if (style.isEmpty() || !style.orElseThrow().changesDisplayName()) {
			return null;
		}
		return render(style.orElseThrow(), player.getPlainTextName());
	}

	public static int resolveOrder(final ServerPlayer player) {
		return resolveStyle(player)
			.map(ResolvedStyle::sortOrder)
			.map(TabListDisplay::toVanillaOrder)
			.orElse(0);
	}

	private static Optional<ResolvedStyle> resolveStyle(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return Optional.empty();
		}
		WorldStateV2 state = PixelTzzWorldState.get(server).currentV2().orElse(null);
		if (state == null) {
			return Optional.empty();
		}
		return resolveStyle(
			player.getUUID(),
			state,
			DefinitionRegistry.INSTANCE.view().active()
		);
	}

	static Optional<ResolvedStyle> resolveStyle(
		final UUID playerId,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions
	) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		if (state.host().filter(host -> host.playerId().equals(playerId)).isPresent()) {
			return Optional.of(hostStyle());
		}

		PlayerRecord player = state.players().get(playerId);
		if (player == null) {
			return Optional.empty();
		}
		RoleDefinition role = definitions.roles().get(player.roleId());
		if (
			role == null
				|| state.activeGameId().filter(role.game()::equals).isEmpty()
				|| !hasCurrentIdentityCredential(
					player.playerId(),
					player.roleId(),
					role.initializationFlow(),
					role.initializationFlow()
						.map(definitions.flows()::get)
						.map(flow -> flow.version()),
					player.pendingRoleChange(),
					state.completionHistory()
				)
		) {
			return Optional.empty();
		}

		TeamDefinition team = player.teamId()
			.map(definitions.teams()::get)
			.filter(value -> value.game().equals(role.game()))
			.filter(value -> value.allowedRoles().contains(role.id()))
			.orElse(null);
		LifeStateDefinition lifeState = Optional.ofNullable(
				definitions.lifeStates().get(player.lifeStateId())
			)
			.filter(value -> value.game().equals(role.game()))
			.orElse(null);
		return Optional.of(
			compose(
				role.tab(),
				team == null ? null : team.tab(),
				lifeState == null ? null : lifeState.tab()
			)
		);
	}

	static ResolvedStyle hostStyle() {
		return new ResolvedStyle(List.of(HOST_PREFIX), Optional.of(HOST_GOLD), HOST_SORT_ORDER);
	}

	/**
	 * A historical completion alone is deliberately insufficient: the applied role transaction and
	 * the completion must point at the same successful flow instance.
	 */
	static boolean hasCurrentIdentityCredential(
		final UUID playerId,
		final Identifier roleId,
		final Optional<Identifier> requiredFlowId,
		final Optional<Integer> requiredFlowVersion,
		final Optional<PendingRoleChange> pendingRoleChange,
		final List<CompletionRecord> completionHistory
	) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(roleId, "roleId");
		Objects.requireNonNull(requiredFlowId, "requiredFlowId");
		Objects.requireNonNull(requiredFlowVersion, "requiredFlowVersion");
		Objects.requireNonNull(pendingRoleChange, "pendingRoleChange");
		Objects.requireNonNull(completionHistory, "completionHistory");
		if (requiredFlowId.isEmpty()) {
			return true;
		}
		if (requiredFlowVersion.isEmpty()) {
			return false;
		}
		Identifier flowId = requiredFlowId.orElseThrow();
		int flowVersion = requiredFlowVersion.orElseThrow();
		List<CompletionRecord> matchingCompletions = completionHistory.stream().filter(
			completion -> completion.playerId().equals(playerId)
				&& completion.flowId().equals(flowId)
				&& completion.flowVersion() == flowVersion
		).toList();
		if (pendingRoleChange.isEmpty()) {
			// The world's default role can use a normal initialization flow without creating a
			// role-change transaction. Its current-version completion is the initial disclosure
			// credential; a world reset clears that history and hides the candidate role again.
			return !matchingCompletions.isEmpty();
		}
		PendingRoleChange change = pendingRoleChange.orElseThrow();
		if (!change.targetPlayerId().equals(playerId)) {
			return false;
		}
		if (change.status() == PendingRoleChangeStatus.PENDING) {
			/*
			 * A future role transaction must not erase the identity the player still owns. This
			 * matters both when a runner is being initialized as a hunter and when an existing
			 * hunter is re-running the same flow to change a persistent field. The new identity
			 * remains hidden until the reducer atomically applies it; the previous role keeps its
			 * already-earned current-version credential in the meantime.
			 */
			return change.previousRoleId().equals(roleId) && !matchingCompletions.isEmpty();
		}
		PendingRoleChange credential = change.status() == PendingRoleChangeStatus.APPLIED
				&& change.targetRoleId().equals(roleId)
			? change
			: null;
		return credential != null
			&& matchingCompletions.stream().anyMatch(
				completion -> completion.instanceId().equals(credential.flowInstanceId())
			);
	}

	static ResolvedStyle compose(
		final TabStyle role,
		final @Nullable TabStyle team,
		final @Nullable TabStyle lifeState
	) {
		Objects.requireNonNull(role, "role");
		ResolvedStyle result = ResolvedStyle.empty();
		result = apply(result, role);
		if (team != null) {
			result = apply(result, team);
		}
		if (lifeState != null) {
			result = apply(result, lifeState);
		}
		return result;
	}

	private static ResolvedStyle apply(
		final ResolvedStyle current,
		final TabStyle layer
	) {
		List<RichText> prefixes = current.prefixes();
		if (layer.prefixMode() != TabPrefixMode.INHERIT && layer.prefix().isPresent()) {
			if (layer.prefixMode() == TabPrefixMode.REPLACE) {
				prefixes = List.of(layer.prefix().orElseThrow());
			} else {
				List<RichText> appended = new ArrayList<>(prefixes);
				appended.add(layer.prefix().orElseThrow());
				prefixes = List.copyOf(appended);
			}
		}
		Optional<Integer> color = current.color();
		if (layer.colorMode() == TabColorMode.REPLACE && layer.color().isPresent()) {
			color = parseColor(layer.color().orElseThrow());
		}
		return new ResolvedStyle(
			prefixes,
			color,
			layer.sortOrder().orElse(current.sortOrder())
		);
	}

	static int toVanillaOrder(final int dataPackSortOrder) {
		// Vanilla sorts larger list-order values first; the data-pack contract uses smaller first.
		return -dataPackSortOrder;
	}

	static Component render(final ResolvedStyle style, final String playerName) {
		MutableComponent result = Component.empty();
		for (RichText prefix : style.prefixes()) {
			MutableComponent rendered = parse(prefix).copy();
			style.color().ifPresent(rendered::withColor);
			result.append(rendered);
		}
		MutableComponent name = Component.literal(playerName);
		style.color().ifPresent(name::withColor);
		return result.append(name);
	}

	/**
	 * Retained for the small foundation smoke check and integrations that used the 2C helper.
	 */
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

	private static Component parse(final RichText text) {
		try {
			return ComponentSerialization.CODEC.parse(
				JsonOps.INSTANCE,
				JsonParser.parseString(text.json())
			)
				.result()
				.orElseGet(() -> Component.literal(text.plainText()));
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn("Failed to render Pixel TZZ TAB prefix", error);
			return Component.literal(text.plainText());
		}
	}

	private static Optional<Integer> parseColor(final String value) {
		if (!value.matches("#[0-9a-fA-F]{6}")) {
			return Optional.empty();
		}
		return Optional.of(Integer.parseInt(value.substring(1), 16));
	}

	record ResolvedStyle(
		List<RichText> prefixes,
		Optional<Integer> color,
		int sortOrder
	) {
		ResolvedStyle {
			prefixes = List.copyOf(prefixes);
			color = color == null ? Optional.empty() : color;
		}

		static ResolvedStyle empty() {
			return new ResolvedStyle(List.of(), Optional.empty(), 0);
		}

		boolean changesDisplayName() {
			return !this.prefixes.isEmpty() || this.color.isPresent();
		}

		String plainPrefix() {
			StringBuilder text = new StringBuilder();
			this.prefixes.forEach(prefix -> text.append(prefix.plainText()));
			return text.toString();
		}
	}
}
