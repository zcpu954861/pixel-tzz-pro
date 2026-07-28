package io.github.zcpu954861.pixeltzzpro.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable, data-pack-owned game definitions.
 *
 * <p>Visible labels and concrete Pixel TZZ rules live here instead of Java enums. The records are
 * deliberately behavior-free; the compiler validates them before they enter a snapshot.
 */
public final class GameDefinitions {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public static final int CURRENT_API_VERSION = 1;

	private GameDefinitions() {
	}

	public record RichText(String json, String plainText) {
	}

	public record TabStyle(Optional<RichText> prefix, Optional<String> color) {
		public TabStyle {
			prefix = prefix == null ? Optional.empty() : prefix;
			color = color == null ? Optional.empty() : color;
		}
	}

	public record GameDefinition(
		Identifier id,
		int contentVersion,
		RichText name,
		Identifier initialPhase,
		Identifier defaultRole,
		Identifier defaultLifeState
	) {
	}

	public record RoleDefinition(
		Identifier id,
		Identifier game,
		RichText name,
		Optional<RichText> description,
		Set<Identifier> tags,
		TabStyle tab
	) {
		public RoleDefinition {
			description = description == null ? Optional.empty() : description;
			tags = Set.copyOf(tags);
		}
	}

	public record TeamDefinition(
		Identifier id,
		Identifier game,
		RichText name,
		Optional<RichText> description,
		Set<Identifier> allowedRoles,
		Set<Identifier> tags,
		TabStyle tab
	) {
		public TeamDefinition {
			description = description == null ? Optional.empty() : description;
			allowedRoles = Set.copyOf(allowedRoles);
			tags = Set.copyOf(tags);
		}
	}

	public record LifeStateDefinition(
		Identifier id,
		Identifier game,
		RichText name,
		Optional<RichText> description,
		Set<Identifier> tags
	) {
		public LifeStateDefinition {
			description = description == null ? Optional.empty() : description;
			tags = Set.copyOf(tags);
		}
	}

	public record PhaseDefinition(
		Identifier id,
		Identifier game,
		RichText name,
		Set<Identifier> transitions,
		Optional<Identifier> onEnter,
		Optional<Identifier> onExit
	) {
		public PhaseDefinition {
			transitions = Set.copyOf(transitions);
			onEnter = onEnter == null ? Optional.empty() : onEnter;
			onExit = onExit == null ? Optional.empty() : onExit;
		}
	}

	public enum FieldType {
		BOOLEAN,
		INTEGER,
		STRING,
		IDENTIFIER,
		SINGLE_CHOICE,
		MULTI_CHOICE
	}

	public enum FieldScope {
		FLOW,
		PLAYER
	}

	public enum EditPolicy {
		NONE,
		PLAYER,
		HOST,
		BOTH
	}

	public enum FieldMigrationStrategy {
		PRESERVE,
		RESET_TO_DEFAULT
	}

	public record FieldConstraints(
		Optional<Long> minimum,
		Optional<Long> maximum,
		Optional<Integer> minimumLength,
		Optional<Integer> maximumLength,
		List<String> options,
		Optional<Integer> minimumSelections,
		Optional<Integer> maximumSelections
	) {
		public FieldConstraints {
			minimum = minimum == null ? Optional.empty() : minimum;
			maximum = maximum == null ? Optional.empty() : maximum;
			minimumLength = minimumLength == null ? Optional.empty() : minimumLength;
			maximumLength = maximumLength == null ? Optional.empty() : maximumLength;
			options = List.copyOf(options);
			minimumSelections = minimumSelections == null ? Optional.empty() : minimumSelections;
			maximumSelections = maximumSelections == null ? Optional.empty() : maximumSelections;
		}
	}

	public record FieldDefinition(
		Identifier id,
		Identifier game,
		int version,
		RichText name,
		Optional<RichText> description,
		FieldScope scope,
		FieldType type,
		boolean required,
		Optional<String> defaultJson,
		EditPolicy editableBy,
		boolean invalidatesReady,
		Set<Identifier> roles,
		Set<Identifier> phases,
		Optional<Identifier> visibleWhen,
		FieldMigrationStrategy migration,
		FieldConstraints constraints
	) {
		public FieldDefinition {
			description = description == null ? Optional.empty() : description;
			defaultJson = defaultJson == null ? Optional.empty() : defaultJson;
			roles = Set.copyOf(roles);
			phases = Set.copyOf(phases);
			visibleWhen = visibleWhen == null ? Optional.empty() : visibleWhen;
		}
	}

	public record Audience(
		Set<Identifier> roles,
		Set<Identifier> teams,
		Set<Identifier> lifeStates,
		Set<Identifier> roleTags,
		Set<Identifier> teamTags,
		Set<Identifier> lifeStateTags,
		boolean excludeHost,
		boolean onlineOnly
	) {
		public Audience {
			roles = Set.copyOf(roles);
			teams = Set.copyOf(teams);
			lifeStates = Set.copyOf(lifeStates);
			roleTags = Set.copyOf(roleTags);
			teamTags = Set.copyOf(teamTags);
			lifeStateTags = Set.copyOf(lifeStateTags);
		}

		public static Audience everyoneExceptHost() {
			return new Audience(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), true, true);
		}
	}

	public enum NodeScope {
		PLAYER,
		EVENT
	}

	public sealed interface FlowNode permits
		PageNode,
		ChoiceNode,
		ConfirmNode,
		BranchNode,
		FunctionNode,
		WaitPlayersNode,
		ChangeStateNode,
		CompleteNode {
		String id();

		NodeScope scope();

		List<String> outgoing();
	}

	public record PageNode(String id, Identifier page, String next) implements FlowNode {
		@Override
		public NodeScope scope() {
			return NodeScope.PLAYER;
		}

		@Override
		public List<String> outgoing() {
			return List.of(this.next);
		}
	}

	public record ChoiceRoute(String value, String next) {
	}

	public record ChoiceNode(
		String id,
		Identifier page,
		Identifier field,
		List<ChoiceRoute> choices
	) implements FlowNode {
		public ChoiceNode {
			choices = List.copyOf(choices);
		}

		@Override
		public NodeScope scope() {
			return NodeScope.PLAYER;
		}

		@Override
		public List<String> outgoing() {
			return this.choices.stream().map(ChoiceRoute::next).toList();
		}
	}

	public record ConfirmNode(String id, Identifier page, String next) implements FlowNode {
		@Override
		public NodeScope scope() {
			return NodeScope.PLAYER;
		}

		@Override
		public List<String> outgoing() {
			return List.of(this.next);
		}
	}

	public record BranchCase(Identifier predicate, String next) {
	}

	public record BranchNode(
		String id,
		NodeScope scope,
		List<BranchCase> cases,
		String fallback
	) implements FlowNode {
		public BranchNode {
			cases = List.copyOf(cases);
		}

		@Override
		public List<String> outgoing() {
			return java.util.stream.Stream.concat(
					this.cases.stream().map(BranchCase::next),
					java.util.stream.Stream.of(this.fallback)
				)
				.toList();
		}
	}

	public record FunctionNode(
		String id,
		NodeScope scope,
		Identifier function,
		String next
	) implements FlowNode {
		@Override
		public List<String> outgoing() {
			return List.of(this.next);
		}
	}

	public record WaitPlayersNode(String id, String next) implements FlowNode {
		@Override
		public NodeScope scope() {
			return NodeScope.EVENT;
		}

		@Override
		public List<String> outgoing() {
			return List.of(this.next);
		}
	}

	public enum StateAxis {
		ROLE,
		TEAM,
		LIFE_STATE
	}

	public record ChangeStateNode(
		String id,
		NodeScope scope,
		StateAxis axis,
		Identifier value,
		String next
	) implements FlowNode {
		@Override
		public List<String> outgoing() {
			return List.of(this.next);
		}
	}

	public record CompleteNode(String id, NodeScope scope) implements FlowNode {
		@Override
		public List<String> outgoing() {
			return List.of();
		}
	}

	public enum BossBarColor {
		PINK,
		BLUE,
		RED,
		GREEN,
		YELLOW,
		PURPLE,
		WHITE
	}

	public enum BossBarStyle {
		PROGRESS,
		NOTCHED_6,
		NOTCHED_10,
		NOTCHED_12,
		NOTCHED_20
	}

	public record BossBarCompletionFeedback(
		RichText name,
		BossBarColor color,
		int holdTicks,
		int fadeTicks,
		Optional<Identifier> sound
	) {
		public BossBarCompletionFeedback {
			sound = sound == null ? Optional.empty() : sound;
		}
	}

	public record HostBossBar(
		RichText name,
		BossBarColor color,
		BossBarStyle style,
		int priority,
		Optional<BossBarCompletionFeedback> completionFeedback
	) {
		public HostBossBar {
			completionFeedback = completionFeedback == null ? Optional.empty() : completionFeedback;
		}
	}

	public record FlowDefinition(
		Identifier id,
		Identifier game,
		int version,
		RichText name,
		String entry,
		Audience audience,
		boolean required,
		Optional<HostBossBar> hostBossBar,
		Optional<Identifier> onStart,
		Optional<Identifier> onPlayerComplete,
		Optional<Identifier> onAllComplete,
		Map<String, FlowNode> nodes
	) {
		public FlowDefinition {
			hostBossBar = hostBossBar == null ? Optional.empty() : hostBossBar;
			onStart = onStart == null ? Optional.empty() : onStart;
			onPlayerComplete = onPlayerComplete == null ? Optional.empty() : onPlayerComplete;
			onAllComplete = onAllComplete == null ? Optional.empty() : onAllComplete;
			nodes = Map.copyOf(nodes);
		}
	}

	public enum PanelSurface {
		HOST,
		PLAYER
	}

	public enum SelectionMode {
		NONE,
		SINGLE,
		MULTIPLE
	}

	/**
	 * Declarative player filter. Values within each non-empty set are alternatives; all non-empty
	 * sets must match. A flow may not appear in both completion sets.
	 */
	public record TargetFilter(
		Set<Identifier> roles,
		Set<Identifier> teams,
		Set<Identifier> lifeStates,
		Set<Identifier> roleTags,
		Set<Identifier> teamTags,
		Set<Identifier> lifeStateTags,
		Set<Identifier> completedFlows,
		Set<Identifier> incompleteFlows,
		Set<Identifier> statusFlows
	) {
		public TargetFilter {
			roles = Set.copyOf(roles);
			teams = Set.copyOf(teams);
			lifeStates = Set.copyOf(lifeStates);
			roleTags = Set.copyOf(roleTags);
			teamTags = Set.copyOf(teamTags);
			lifeStateTags = Set.copyOf(lifeStateTags);
			completedFlows = Set.copyOf(completedFlows);
			incompleteFlows = Set.copyOf(incompleteFlows);
			statusFlows = Set.copyOf(statusFlows);
		}
	}

	public record TargetSelection(
		SelectionMode mode,
		int minimum,
		int maximum,
		TargetFilter filter
	) {
	}

	public record Confirmation(
		RichText title,
		List<RichText> consequences
	) {
		public Confirmation {
			consequences = List.copyOf(consequences);
		}
	}

	public enum ApplyTiming {
		IMMEDIATE,
		AFTER_FLOW
	}

	public enum CompletionPolicy {
		IF_INCOMPLETE,
		ALWAYS,
		RESUME_ONLY
	}

	public sealed interface PanelOperation permits
		StartFlowOperation,
		AssignRoleOperation,
		AssignTeamOperation,
		AssignLifeStateOperation,
		TransitionPhaseOperation,
		RunFunctionOperation {
	}

	public record StartFlowOperation(
		Identifier flow,
		CompletionPolicy completionPolicy
	) implements PanelOperation {
	}

	public record AssignRoleOperation(
		Identifier role,
		Optional<Identifier> flow,
		ApplyTiming apply,
		CompletionPolicy completionPolicy
	) implements PanelOperation {
		public AssignRoleOperation {
			flow = flow == null ? Optional.empty() : flow;
		}
	}

	public record AssignTeamOperation(
		Identifier team,
		Optional<Identifier> flow,
		ApplyTiming apply
	) implements PanelOperation {
		public AssignTeamOperation {
			flow = flow == null ? Optional.empty() : flow;
		}
	}

	public record AssignLifeStateOperation(
		Identifier lifeState,
		Optional<Identifier> flow,
		ApplyTiming apply
	) implements PanelOperation {
		public AssignLifeStateOperation {
			flow = flow == null ? Optional.empty() : flow;
		}
	}

	public record TransitionPhaseOperation(Identifier phase) implements PanelOperation {
	}

	public record RunFunctionOperation(Identifier function) implements PanelOperation {
	}

	public record PanelActionDefinition(
		Identifier id,
		Identifier game,
		PanelSurface surface,
		String section,
		int order,
		RichText label,
		RichText description,
		Optional<Identifier> icon,
		Optional<String> color,
		Optional<Identifier> visibleWhen,
		Optional<Identifier> enabledWhen,
		Optional<RichText> disabledReason,
		Set<Identifier> phases,
		TargetSelection target,
		Optional<Confirmation> confirmation,
		PanelOperation operation
	) {
		public PanelActionDefinition {
			icon = icon == null ? Optional.empty() : icon;
			color = color == null ? Optional.empty() : color;
			visibleWhen = visibleWhen == null ? Optional.empty() : visibleWhen;
			enabledWhen = enabledWhen == null ? Optional.empty() : enabledWhen;
			disabledReason = disabledReason == null ? Optional.empty() : disabledReason;
			phases = Set.copyOf(phases);
			confirmation = confirmation == null ? Optional.empty() : confirmation;
		}
	}
}
