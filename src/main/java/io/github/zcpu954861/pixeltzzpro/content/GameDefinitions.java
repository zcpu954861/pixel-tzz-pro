package io.github.zcpu954861.pixeltzzpro.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskTimeline;
import net.minecraft.resources.Identifier;

/**
 * Immutable, data-pack-owned game definitions.
 *
 * <p>Visible labels and concrete Pixel TZZ rules live here instead of Java enums. The records are
 * deliberately behavior-free; the compiler validates them before they enter a snapshot.
 */
public final class GameDefinitions {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public static final int MIN_API_VERSION = 1;
	public static final int CURRENT_API_VERSION = 2;

	private GameDefinitions() {
	}

	public record RichText(String json, String plainText) {
	}

	public enum TabPrefixMode {
		APPEND,
		REPLACE,
		INHERIT
	}

	public enum TabColorMode {
		REPLACE,
		INHERIT
	}

	public record TabStyle(
		Optional<RichText> prefix,
		Optional<String> color,
		Optional<Integer> sortOrder,
		TabPrefixMode prefixMode,
		TabColorMode colorMode
	) {
		public TabStyle {
			prefix = prefix == null ? Optional.empty() : prefix;
			color = color == null ? Optional.empty() : color;
			sortOrder = sortOrder == null ? Optional.empty() : sortOrder;
		}

		public TabStyle(final Optional<RichText> prefix, final Optional<String> color) {
			this(
				prefix,
				color,
				Optional.empty(),
				prefix != null && prefix.isPresent() ? TabPrefixMode.APPEND : TabPrefixMode.INHERIT,
				color != null && color.isPresent() ? TabColorMode.REPLACE : TabColorMode.INHERIT
			);
		}
	}

	public record GameDefinition(
		Identifier id,
		int apiVersion,
		int contentVersion,
		RichText name,
		Identifier initialPhase,
		Identifier defaultRole,
		Identifier defaultLifeState,
		Optional<TaskTimeline> taskTimeline,
		Optional<ReadinessDefinition> readiness
	) {
		public GameDefinition {
			taskTimeline = taskTimeline == null ? Optional.empty() : taskTimeline;
			readiness = readiness == null ? Optional.empty() : readiness;
		}

		public GameDefinition(
			final Identifier id,
			final int apiVersion,
			final int contentVersion,
			final RichText name,
			final Identifier initialPhase,
			final Identifier defaultRole,
			final Identifier defaultLifeState,
			final Optional<TaskTimeline> taskTimeline
		) {
			this(
				id,
				apiVersion,
				contentVersion,
				name,
				initialPhase,
				defaultRole,
				defaultLifeState,
				taskTimeline,
				Optional.empty()
			);
		}

		public GameDefinition(
			final Identifier id,
			final int contentVersion,
			final RichText name,
			final Identifier initialPhase,
			final Identifier defaultRole,
			final Identifier defaultLifeState
		) {
			this(
				id,
				MIN_API_VERSION,
				contentVersion,
				name,
				initialPhase,
				defaultRole,
				defaultLifeState,
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	/**
	 * Data-pack binding for the one authoritative player-readiness session of a game instance.
	 *
	 * <p>The referenced action remains a normal required {@code start_flow} definition so its
	 * pages, theme, audience, and BossBar stay fully data-driven. The runtime hides that action
	 * from the ordinary host action list and opens it atomically when {@code phase} is entered.
	 */
	public record ReadinessDefinition(
		Identifier phase,
		Identifier action,
		boolean disconnectInvalidates,
		boolean hostCanForce
	) {
	}

	public record RoleDefinition(
		Identifier id,
		Identifier game,
		RichText name,
		Optional<RichText> description,
		Optional<Identifier> initializationFlow,
		Set<Identifier> tags,
		TabStyle tab
	) {
		public RoleDefinition {
			description = description == null ? Optional.empty() : description;
			initializationFlow = initializationFlow == null ? Optional.empty() : initializationFlow;
			tags = Set.copyOf(tags);
		}

		public RoleDefinition(
			final Identifier id,
			final Identifier game,
			final RichText name,
			final Optional<RichText> description,
			final Set<Identifier> tags,
			final TabStyle tab
		) {
			this(id, game, name, description, Optional.empty(), tags, tab);
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
		Set<Identifier> tags,
		TabStyle tab
	) {
		public LifeStateDefinition {
			description = description == null ? Optional.empty() : description;
			tags = Set.copyOf(tags);
		}

		public LifeStateDefinition(
			final Identifier id,
			final Identifier game,
			final RichText name,
			final Optional<RichText> description,
			final Set<Identifier> tags
		) {
			this(id, game, name, description, tags, new TabStyle(Optional.empty(), Optional.empty()));
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
		MULTI_CHOICE,
		EXCLUSIVE_CHOICE
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

	public enum ReservationScope {
		GAME_INSTANCE
	}

	public record ExclusiveChoiceOption(
		String value,
		RichText name,
		Optional<RichText> description,
		Optional<Identifier> icon,
		Optional<Identifier> previewPage
	) {
		public ExclusiveChoiceOption {
			description = description == null ? Optional.empty() : description;
			icon = icon == null ? Optional.empty() : icon;
			previewPage = previewPage == null ? Optional.empty() : previewPage;
		}
	}

	public record ExclusiveChoiceDefinition(
		ReservationScope reservationScope,
		boolean releaseWhenRoleMismatch,
		boolean showOccupant,
		List<ExclusiveChoiceOption> options
	) {
		public ExclusiveChoiceDefinition {
			options = List.copyOf(options);
		}

		public List<String> values() {
			return this.options.stream().map(ExclusiveChoiceOption::value).toList();
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
		FieldConstraints constraints,
		Optional<ExclusiveChoiceDefinition> exclusiveChoice
	) {
		public FieldDefinition {
			description = description == null ? Optional.empty() : description;
			defaultJson = defaultJson == null ? Optional.empty() : defaultJson;
			roles = Set.copyOf(roles);
			phases = Set.copyOf(phases);
			visibleWhen = visibleWhen == null ? Optional.empty() : visibleWhen;
			exclusiveChoice = exclusiveChoice == null ? Optional.empty() : exclusiveChoice;
		}

		public FieldDefinition(
			final Identifier id,
			final Identifier game,
			final int version,
			final RichText name,
			final Optional<RichText> description,
			final FieldScope scope,
			final FieldType type,
			final boolean required,
			final Optional<String> defaultJson,
			final EditPolicy editableBy,
			final boolean invalidatesReady,
			final Set<Identifier> roles,
			final Set<Identifier> phases,
			final Optional<Identifier> visibleWhen,
			final FieldMigrationStrategy migration,
			final FieldConstraints constraints
		) {
			this(
				id,
				game,
				version,
				name,
				description,
				scope,
				type,
				required,
				defaultJson,
				editableBy,
				invalidatesReady,
				roles,
				phases,
				visibleWhen,
				migration,
				constraints,
				Optional.empty()
			);
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

	public record ConfirmNode(
		String id,
		Identifier page,
		String next,
		Optional<String> back
	) implements FlowNode {
		public ConfirmNode {
			back = back == null ? Optional.empty() : back;
		}

		@Override
		public NodeScope scope() {
			return NodeScope.PLAYER;
		}

		@Override
		public List<String> outgoing() {
			return this.back
				.map(value -> List.of(this.next, value))
				.orElseGet(() -> List.of(this.next));
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
