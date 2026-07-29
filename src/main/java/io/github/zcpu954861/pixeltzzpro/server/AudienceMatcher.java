package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Shared audience predicate for flows, task participants, callbacks, and task facts.
 */
public final class AudienceMatcher {
	private AudienceMatcher() {
	}

	public static boolean matches(
		final DefinitionSnapshot definitions,
		final Audience audience,
		final PlayerRecord player,
		final Optional<UUID> hostId,
		final boolean online
	) {
		if (
			(audience.excludeHost() && hostId.filter(player.playerId()::equals).isPresent())
				|| (audience.onlineOnly() && !online)
				|| (!audience.roles().isEmpty() && !audience.roles().contains(player.roleId()))
				|| (
					!audience.teams().isEmpty()
						&& player.teamId().filter(audience.teams()::contains).isEmpty()
				)
				|| (
					!audience.lifeStates().isEmpty()
						&& !audience.lifeStates().contains(player.lifeStateId())
				)
		) {
			return false;
		}

		Set<Identifier> roleTags = Optional.ofNullable(definitions.roles().get(player.roleId()))
			.map(role -> role.tags())
			.orElse(Set.of());
		Set<Identifier> teamTags = player.teamId()
			.map(definitions.teams()::get)
			.map(team -> team.tags())
			.orElse(Set.of());
		Set<Identifier> lifeStateTags = Optional.ofNullable(
			definitions.lifeStates().get(player.lifeStateId())
		).map(lifeState -> lifeState.tags()).orElse(Set.of());
		return intersectsOrEmpty(audience.roleTags(), roleTags)
			&& intersectsOrEmpty(audience.teamTags(), teamTags)
			&& intersectsOrEmpty(audience.lifeStateTags(), lifeStateTags);
	}

	private static boolean intersectsOrEmpty(
		final Set<Identifier> required,
		final Set<Identifier> actual
	) {
		return required.isEmpty() || required.stream().anyMatch(actual::contains);
	}
}
