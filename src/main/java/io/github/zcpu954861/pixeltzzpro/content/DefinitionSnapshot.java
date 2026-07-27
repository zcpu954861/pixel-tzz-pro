package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * One immutable generation of validated data-pack definitions.
 *
 * <p>Later flow instances may retain this object while a newer generation becomes active.
 */
public record DefinitionSnapshot(
	long generation,
	Map<Identifier, GameDefinition> games,
	Map<Identifier, RoleDefinition> roles,
	Map<Identifier, TeamDefinition> teams,
	Map<Identifier, LifeStateDefinition> lifeStates,
	Map<Identifier, PhaseDefinition> phases,
	Map<Identifier, FieldDefinition> fields,
	Map<Identifier, FlowDefinition> flows,
	Map<Identifier, PanelActionDefinition> panelActions,
	Map<Identifier, PageDefinition> pages,
	Map<Identifier, ThemeDefinition> themes
) {
	public DefinitionSnapshot {
		games = Map.copyOf(games);
		roles = Map.copyOf(roles);
		teams = Map.copyOf(teams);
		lifeStates = Map.copyOf(lifeStates);
		phases = Map.copyOf(phases);
		fields = Map.copyOf(fields);
		flows = Map.copyOf(flows);
		panelActions = Map.copyOf(panelActions);
		pages = Map.copyOf(pages);
		themes = Map.copyOf(themes);
	}

	public static DefinitionSnapshot empty() {
		return new DefinitionSnapshot(
			0L,
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of()
		);
	}

	public boolean usable() {
		return !this.games.isEmpty();
	}

	public int definitionCount() {
		return this.games.size()
			+ this.roles.size()
			+ this.teams.size()
			+ this.lifeStates.size()
			+ this.phases.size()
			+ this.fields.size()
			+ this.flows.size()
			+ this.panelActions.size()
			+ this.pages.size()
			+ this.themes.size();
	}

	public DefinitionSnapshot withGeneration(final long newGeneration) {
		return new DefinitionSnapshot(
			newGeneration,
			this.games,
			this.roles,
			this.teams,
			this.lifeStates,
			this.phases,
			this.fields,
			this.flows,
			this.panelActions,
			this.pages,
			this.themes
		);
	}
}
