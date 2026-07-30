package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRouteDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import java.util.Map;
import java.util.Set;
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
	Map<Identifier, TaskDefinition> tasks,
	Map<Identifier, PanelActionDefinition> panelActions,
	Map<Identifier, PlayerRouteDefinition> playerRoutes,
	Map<Identifier, PlayerDataDefinition> playerData,
	Map<Identifier, PlayerActionDefinition> playerActions,
	Map<Identifier, PageDefinition> pages,
	Map<Identifier, ThemeDefinition> themes,
	Map<DocumentKey, SourceDocument> sourceDocuments,
	Set<Identifier> functions,
	Set<Identifier> predicates,
	Map<Identifier, String> predicateDocuments
) {
	public DefinitionSnapshot {
		games = Map.copyOf(games);
		roles = Map.copyOf(roles);
		teams = Map.copyOf(teams);
		lifeStates = Map.copyOf(lifeStates);
		phases = Map.copyOf(phases);
		fields = Map.copyOf(fields);
		flows = Map.copyOf(flows);
		tasks = Map.copyOf(tasks);
		panelActions = Map.copyOf(panelActions);
		playerRoutes = Map.copyOf(playerRoutes);
		playerData = Map.copyOf(playerData);
		playerActions = Map.copyOf(playerActions);
		pages = Map.copyOf(pages);
		themes = Map.copyOf(themes);
		sourceDocuments = Map.copyOf(sourceDocuments);
		functions = Set.copyOf(functions);
		predicates = Set.copyOf(predicates);
		predicateDocuments = Map.copyOf(predicateDocuments);
	}

	public DefinitionSnapshot(
		final long generation,
		final Map<Identifier, GameDefinition> games,
		final Map<Identifier, RoleDefinition> roles,
		final Map<Identifier, TeamDefinition> teams,
		final Map<Identifier, LifeStateDefinition> lifeStates,
		final Map<Identifier, PhaseDefinition> phases,
		final Map<Identifier, FieldDefinition> fields,
		final Map<Identifier, FlowDefinition> flows,
		final Map<Identifier, TaskDefinition> tasks,
		final Map<Identifier, PanelActionDefinition> panelActions,
		final Map<Identifier, PageDefinition> pages,
		final Map<Identifier, ThemeDefinition> themes,
		final Map<DocumentKey, SourceDocument> sourceDocuments,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final Map<Identifier, String> predicateDocuments
	) {
		this(
			generation,
			games,
			roles,
			teams,
			lifeStates,
			phases,
			fields,
			flows,
			tasks,
			panelActions,
			Map.of(),
			Map.of(),
			Map.of(),
			pages,
			themes,
			sourceDocuments,
			functions,
			predicates,
			predicateDocuments
		);
	}

	public DefinitionSnapshot(
		final long generation,
		final Map<Identifier, GameDefinition> games,
		final Map<Identifier, RoleDefinition> roles,
		final Map<Identifier, TeamDefinition> teams,
		final Map<Identifier, LifeStateDefinition> lifeStates,
		final Map<Identifier, PhaseDefinition> phases,
		final Map<Identifier, FieldDefinition> fields,
		final Map<Identifier, FlowDefinition> flows,
		final Map<Identifier, PanelActionDefinition> panelActions,
		final Map<Identifier, PageDefinition> pages,
		final Map<Identifier, ThemeDefinition> themes,
		final Map<DocumentKey, SourceDocument> sourceDocuments,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final Map<Identifier, String> predicateDocuments
	) {
		this(
			generation,
			games,
			roles,
			teams,
			lifeStates,
			phases,
			fields,
			flows,
			Map.of(),
			panelActions,
			Map.of(),
			Map.of(),
			Map.of(),
			pages,
			themes,
			sourceDocuments,
			functions,
			predicates,
			predicateDocuments
		);
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
			+ this.tasks.size()
			+ this.panelActions.size()
			+ this.playerRoutes.size()
			+ this.playerData.size()
			+ this.playerActions.size()
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
			this.tasks,
			this.panelActions,
			this.playerRoutes,
			this.playerData,
			this.playerActions,
			this.pages,
			this.themes,
			this.sourceDocuments,
			this.functions,
			this.predicates,
			this.predicateDocuments
		);
	}

	public DefinitionSnapshot withPredicateDocuments(
		final Map<Identifier, String> documents
	) {
		return new DefinitionSnapshot(
			this.generation,
			this.games,
			this.roles,
			this.teams,
			this.lifeStates,
			this.phases,
			this.fields,
			this.flows,
			this.tasks,
			this.panelActions,
			this.playerRoutes,
			this.playerData,
			this.playerActions,
			this.pages,
			this.themes,
			this.sourceDocuments,
			this.functions,
			this.predicates,
			documents
		);
	}

	public record DocumentKey(DefinitionType type, Identifier id) {
	}

	public record SourceDocument(
		DocumentKey key,
		Identifier resource,
		String sourcePack,
		String canonicalJson,
		String sha256
	) {
	}
}
