package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EffectUse;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.IdentifierKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.PlayerDataParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextVariant;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookSet;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSourceType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOpenPageOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.IntermissionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.PlayerCallback;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ComparisonCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConcatenatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.DurationTicksText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ExistsCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NotCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PlayerHeadContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ProgressContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextTemplate;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TranslatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Computes the exact data-pack definition closure required by frozen message hooks.
 *
 * <p>The closure starts only at hooks retained by a caller's execution/timeline snapshot. It then
 * follows message effects, conditions, callbacks, personal bindings, and the ordinary definitions
 * needed for strict recompilation. Optional message definitions that were already isolated remain
 * isolated by retaining their original source document; they never become executable by freezing.
 */
final class MessageHookFreezeClosure {
	private MessageHookFreezeClosure() {
	}

	static Closure collect(
		final DefinitionSnapshot definitions,
		final Collection<MessageHookSet> roots
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(roots, "roots");
		Builder builder = new Builder(definitions);
		roots.forEach(builder::addHooks);
		return builder.build();
	}

	/** Computes the frozen dependency closure of one already-validated cue instance. */
	static Closure collectCue(
		final DefinitionSnapshot definitions,
		final MessageCueDefinition cue
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(cue, "cue");
		MessageCueDefinition registered = definitions.messageCatalog().messageCues().get(cue.id());
		if (registered == null || !registered.canonicalDocument().equals(cue.canonicalDocument())) {
			throw new IllegalArgumentException(
				"message cue is not the registered definition in its frozen snapshot: " + cue.id()
			);
		}
		Builder builder = new Builder(definitions);
		builder.addCue(cue.id());
		return builder.build();
	}

	record Closure(
		Set<DocumentKey> sourceKeys,
		Set<Identifier> predicates,
		Set<Identifier> functions,
		Set<Identifier> playerTerminalGames
	) {
		Closure {
			sourceKeys = Set.copyOf(sourceKeys);
			predicates = Set.copyOf(predicates);
			functions = Set.copyOf(functions);
			playerTerminalGames = Set.copyOf(playerTerminalGames);
		}
	}

	private static final class Builder {
		private final DefinitionSnapshot definitions;
		private final Set<DocumentKey> sourceKeys = new LinkedHashSet<>();
		private final Set<Identifier> predicates = new LinkedHashSet<>();
		private final Set<Identifier> functions = new LinkedHashSet<>();
		private final Set<Identifier> playerTerminalGames = new LinkedHashSet<>();
		private final ArrayDeque<Identifier> cues = new ArrayDeque<>();
		private final ArrayDeque<Identifier> playerData = new ArrayDeque<>();
		private final ArrayDeque<Identifier> tasks = new ArrayDeque<>();
		private final ArrayDeque<Identifier> fields = new ArrayDeque<>();
		private final ArrayDeque<Identifier> pages = new ArrayDeque<>();
		private final ArrayDeque<Identifier> playerActions = new ArrayDeque<>();
		private Identifier bindingGameContext;

		private Builder(final DefinitionSnapshot definitions) {
			this.definitions = definitions;
		}

		private Closure build() {
			while (
				!this.cues.isEmpty()
					|| !this.playerData.isEmpty()
					|| !this.tasks.isEmpty()
					|| !this.fields.isEmpty()
					|| !this.pages.isEmpty()
					|| !this.playerActions.isEmpty()
			) {
				if (!this.cues.isEmpty()) {
					collectCue(this.cues.removeFirst());
				} else if (!this.playerData.isEmpty()) {
					collectPlayerData(this.playerData.removeFirst());
				} else if (!this.tasks.isEmpty()) {
					collectTask(this.tasks.removeFirst());
				} else if (!this.fields.isEmpty()) {
					collectField(this.fields.removeFirst());
				} else if (!this.pages.isEmpty()) {
					collectPage(this.pages.removeFirst());
				} else {
					collectPlayerAction(this.playerActions.removeFirst());
				}
			}
			for (DocumentKey key : this.sourceKeys) {
				if (!this.definitions.sourceDocuments().containsKey(key)) {
					throw new IllegalArgumentException(
						"message hook dependency has no retained source document: "
							+ key.type().directory()
							+ "/"
							+ key.id()
					);
				}
			}
			for (Identifier predicate : this.predicates) {
				if (!this.definitions.predicates().contains(predicate)) {
					throw new IllegalArgumentException(
						"message hook dependency references missing predicate " + predicate
					);
				}
				if (!this.definitions.predicateDocuments().containsKey(predicate)) {
					throw new IllegalArgumentException(
						"message hook dependency predicate has no retained document: " + predicate
					);
				}
			}
			for (Identifier function : this.functions) {
				if (!this.definitions.functions().contains(function)) {
					throw new IllegalArgumentException(
						"message hook dependency references missing function " + function
					);
				}
			}
			return new Closure(
				this.sourceKeys,
				this.predicates,
				this.functions,
				this.playerTerminalGames
			);
		}

		private void addHooks(final MessageHookSet hooks) {
			if (hooks == null) {
				return;
			}
			hooks.hooks().values().forEach(reference -> {
				addCue(reference.cue());
				MessageCueDefinition cue = this.definitions.messageCatalog()
					.messageCues()
					.get(reference.cue());
				if (cue == null) {
					// Optional malformed cues remain isolated and have no executable parameter model.
					return;
				}
				reference.arguments().forEach((name, value) -> {
					ParameterDefinition parameter = cue.parameters().get(name);
					if (
						parameter != null
							&& parameter.type() == ParameterType.IDENTIFIER
							&& parameter.identifierKind() != IdentifierKind.ANY
					) {
						collectIdentifierValue(value, parameter.identifierKind());
					}
				});
			});
		}

		private void addCue(final Identifier id) {
			if (addKey(DefinitionType.MESSAGE_CUE, id)) {
				this.cues.addLast(id);
			}
		}

		private void collectCue(final Identifier id) {
			MessageCueDefinition cue = this.definitions.messageCatalog().messageCues().get(id);
			if (cue == null) {
				DocumentKey key = new DocumentKey(DefinitionType.MESSAGE_CUE, id);
				if (this.definitions.messageCatalog().disabled().containsKey(key)) {
					return;
				}
				throw new IllegalArgumentException("message hook references missing cue " + id);
			}
			Identifier previousGameContext = this.bindingGameContext;
			this.bindingGameContext = cue.game().orElseThrow(() ->
				new IllegalArgumentException("message hook cue has no owning game " + id)
			);

			cue.game().ifPresent(game -> addKey(DefinitionType.GAME, game));
			cue.policies().context().games().forEach(game -> addKey(DefinitionType.GAME, game));
			cue.policies().context().phases().forEach(phase -> addKey(DefinitionType.PHASE, phase));
			cue.policies().context().tasks().forEach(this::addTask);
			collectAudience(cue.policies().audience().audience());

			for (ParameterDefinition parameter : cue.parameters().values()) {
				parameter.sources().stream()
					.filter(PlayerDataParameterSource.class::isInstance)
					.map(PlayerDataParameterSource.class::cast)
					.map(PlayerDataParameterSource::field)
					.forEach(this::addPlayerData);
				parameter.audience().ifPresent(this::collectAudience);
				if (
					parameter.type() == ParameterType.IDENTIFIER
						&& parameter.identifierKind() != IdentifierKind.ANY
				) {
					/*
					 * A task identifier may be captured from game context, storage, player data,
					 * or a call argument only after this snapshot has been frozen. Keep the
					 * owning game's reachable plan so a task that was valid in this generation
					 * is not rejected merely because a later /reload replaced the live registry.
					 */
					if (parameter.identifierKind() == IdentifierKind.TASK) {
						addReachableTaskPlan(this.bindingGameContext);
					}
					parameter.canonicalDefault().ifPresent(
						value -> collectIdentifierLiteral(value, parameter.identifierKind())
					);
					parameter.onError().canonicalFallback().ifPresent(
						value -> collectIdentifierLiteral(value, parameter.identifierKind())
					);
				}
			}

			cue.fields().values().forEach(this::collectDynamicField);
			for (CueNode node : cue.nodes()) {
				node.header().when().ifPresent(this::collectCondition);
				node.policy().audience().ifPresent(this::collectAudience);
				if (node instanceof TextNode text) {
					collectTextContent(text.content());
					for (TextVariant variant : text.variants()) {
						collectCondition(variant.when());
						collectTextContent(variant.content());
					}
				} else if (node instanceof CallbackNode callback) {
					this.functions.add(callback.function());
				}
			}
			cue.history().ifPresent(history -> {
				collectAudience(history.audience());
				if (history.taskSource() == HistoryTaskSource.CURRENT_TASK) {
					addCurrentTaskPlan(this.bindingGameContext);
				}
			});
			this.bindingGameContext = previousGameContext;
		}

		private void collectTextContent(final TextContent content) {
			content.fields().values().forEach(this::collectDynamicField);
			content.effect().ifPresent(this::collectEffect);
		}

		private void collectEffect(final EffectUse effect) {
			effect.preset().ifPresent(id -> {
				if (!this.definitions.messageCatalog().textEffects().containsKey(id)) {
					throw new IllegalArgumentException(
						"message hook cue references missing text effect " + id
					);
				}
				addKey(DefinitionType.TEXT_EFFECT, id);
			});
		}

		private void collectCondition(final ConditionSpec condition) {
			condition.predicate().ifPresent(this.predicates::add);
			condition.expression().ifPresent(this::collectConditionExpression);
		}

		private void collectConditionExpression(final ConditionExpression condition) {
			if (condition instanceof ComparisonCondition comparison) {
				collectValue(comparison.left());
				collectValue(comparison.right());
			} else if (condition instanceof JunctionCondition junction) {
				junction.conditions().forEach(this::collectConditionExpression);
			} else if (condition instanceof NotCondition not) {
				collectConditionExpression(not.condition());
			} else if (condition instanceof ExistsCondition exists) {
				collectBinding(exists.binding().path());
			}
		}

		private void collectDynamicField(final DynamicFieldDefinition field) {
			if (field.source() instanceof BindingSource binding) {
				collectBinding(binding.path());
			}
		}

		private void collectBinding(final String path) {
			if (path.equals("player.role.name")) {
				addAllGameIdentities(DefinitionType.ROLE);
				return;
			}
			if (path.equals("player.team.name")) {
				addAllGameIdentities(DefinitionType.TEAM);
				return;
			}
			if (path.equals("player.life_state.name")) {
				addAllGameIdentities(DefinitionType.LIFE_STATE);
				return;
			}
			if (path.equals("task/current") || path.startsWith("task/current/")) {
				if (this.bindingGameContext == null) {
					throw new IllegalArgumentException(
						"message current-task binding has no owning game context: " + path
					);
				}
				addCurrentTaskPlan(this.bindingGameContext);
				return;
			}
			if (path.startsWith("personal/")) {
				addPlayerData(requireIdentifier(path.substring("personal/".length()), path));
				return;
			}
			if (path.startsWith("personal_meta/")) {
				String suffix = path.substring("personal_meta/".length());
				int separator = suffix.lastIndexOf('/');
				if (separator > 0) {
					addPlayerData(requireIdentifier(suffix.substring(0, separator), path));
				}
				return;
			}
			if (path.startsWith("fields/")) {
				addField(requireIdentifier(path.substring("fields/".length()), path));
			} else if (path.startsWith("flow/fields/")) {
				addField(requireIdentifier(path.substring("flow/fields/".length()), path));
			}
		}

		private void collectIdentifierLiteral(
			final String canonicalValue,
			final IdentifierKind kind
		) {
			JsonElement parsed = JsonParser.parseString(canonicalValue);
			if (!parsed.isJsonPrimitive() || !parsed.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("message identifier dependency is not a string");
			}
			collectIdentifierValue(parsed.getAsString(), kind);
		}

		private void collectIdentifierValue(
			final String value,
			final IdentifierKind kind
		) {
			Identifier id = requireIdentifier(value, value);
			switch (kind) {
				case ANY -> {
				}
				case GAME -> addKey(DefinitionType.GAME, id);
				case PHASE -> addKey(DefinitionType.PHASE, id);
				case TASK -> addTask(id);
				case FIELD -> addField(id);
				case ROLE -> addKey(DefinitionType.ROLE, id);
				case TEAM -> addKey(DefinitionType.TEAM, id);
				case LIFE_STATE -> addKey(DefinitionType.LIFE_STATE, id);
				case PLAYER_DATA -> addPlayerData(id);
				case EVENT -> addNestedTask(id, true);
				case STATISTIC -> addNestedTask(id, false);
			}
		}

		private void addNestedTask(final Identifier nestedId, final boolean event) {
			TaskDefinition match = this.definitions.tasks().values().stream()
				.filter(task -> {
					String prefix = task.id().getPath() + "/";
					if (!nestedId.getNamespace().equals(task.id().getNamespace())
						|| !nestedId.getPath().startsWith(prefix)) {
						return false;
					}
					String localId = nestedId.getPath().substring(prefix.length());
					return event
						? task.events().stream().anyMatch(value -> value.id().equals(localId))
						: task.statistics().stream().anyMatch(value -> value.id().equals(localId));
				})
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
					"message identifier dependency references missing "
						+ (event ? "event " : "statistic ")
						+ nestedId
				));
			addTask(match.id());
		}

		private void addPlayerData(final Identifier id) {
			if (addKey(DefinitionType.PLAYER_DATA, id)) {
				this.playerData.addLast(id);
			}
		}

		private void collectPlayerData(final Identifier id) {
			PlayerDataDefinition data = this.definitions.playerData().get(id);
			if (data == null) {
				throw new IllegalArgumentException(
					"message hook dependency references missing player data " + id
				);
			}
			addKey(DefinitionType.GAME, data.game());
			Identifier previousGameContext = this.bindingGameContext;
			this.bindingGameContext = data.game();
			collectAudience(data.audience());
			data.phases().forEach(phase -> addKey(DefinitionType.PHASE, phase));
			data.tasks().forEach(this::addTask);
			data.predicate().ifPresent(this.predicates::add);
			if (data.source().type() == PlayerDataSourceType.INITIALIZED) {
				this.definitions.roles().values().stream()
					.filter(role -> role.game().equals(data.game()))
					.sorted(java.util.Comparator.comparing(value -> value.id().toString()))
					.forEach(role -> {
						addKey(DefinitionType.ROLE, role.id());
						role.initializationFlow().ifPresent(flow ->
							addKey(DefinitionType.FLOW, flow)
						);
					});
			} else if (isTaskSource(data.source().type())) {
				data.source().id().ifPresentOrElse(
					this::addTask,
					() -> addCurrentTaskPlan(data.game())
				);
			} else {
				data.source().id().ifPresent(sourceId -> {
					if (
						data.source().type() == PlayerDataSourceType.FIELD
							|| data.source().type() == PlayerDataSourceType.EXCLUSIVE_CHOICE
					) {
						addField(sourceId);
					}
				});
			}
			this.bindingGameContext = previousGameContext;
		}

		private static boolean isTaskSource(final PlayerDataSourceType type) {
			return switch (type) {
				case TASK_ID,
					TASK_NAME,
					TASK_DESCRIPTION,
					TASK_STATUS,
					TASK_ELAPSED_TICKS,
					TASK_REMAINING_TICKS,
					TASK_PROGRESS,
					TASK_RESULT,
					TASK_STATISTIC -> true;
				case ROLE,
					TEAM,
					LIFE_STATE,
					INITIALIZED,
					READY,
					FIELD,
					EXCLUSIVE_CHOICE,
					GAME_ELAPSED_TICKS -> false;
			};
		}

		private void addTask(final Identifier id) {
			if (addKey(DefinitionType.TASK, id)) {
				this.tasks.addLast(id);
			}
		}

		private void addCurrentTaskPlan(final Identifier gameId) {
			var game = this.definitions.games().get(gameId);
			if (game == null) {
				throw new IllegalArgumentException(
					"message current-task dependency references missing game " + gameId
				);
			}
			if (game.taskTimeline().isPresent()) {
				addTask(game.taskTimeline().orElseThrow().initialTask());
				return;
			}
			/*
			 * A forced-flow snapshot deliberately removes the game's unrelated task_timeline but
			 * retains the task documents reached by its message closure. Recover that bounded set
			 * instead of consulting a later live generation or rejecting the frozen snapshot.
			 */
			List<TaskDefinition> retained = this.definitions.tasks().values().stream()
				.filter(task -> task.game().equals(gameId))
				.sorted(java.util.Comparator.comparing(value -> value.id().toString()))
				.toList();
			if (retained.isEmpty()) {
				throw new IllegalArgumentException(
					"message current-task dependency has no retained task plan for " + gameId
				);
			}
			retained.forEach(task -> addTask(task.id()));
		}

		private void addReachableTaskPlan(final Identifier gameId) {
			var game = this.definitions.games().get(gameId);
			if (game == null) {
				throw new IllegalArgumentException(
					"message task-identifier dependency references missing game " + gameId
				);
			}
			if (game.taskTimeline().isPresent()) {
				addTask(game.taskTimeline().orElseThrow().initialTask());
				return;
			}
			this.definitions.tasks().values().stream()
				.filter(task -> task.game().equals(gameId))
				.sorted(java.util.Comparator.comparing(value -> value.id().toString()))
				.forEach(task -> addTask(task.id()));
		}

		private void collectTask(final Identifier id) {
			TaskDefinition task = this.definitions.tasks().get(id);
			if (task == null) {
				throw new IllegalArgumentException("message hook dependency references missing task " + id);
			}
			Identifier previousGameContext = this.bindingGameContext;
			this.bindingGameContext = task.game();
			addKey(DefinitionType.GAME, task.game());
			collectAudience(task.audience());
			task.page().ifPresent(this::addPage);
			addHooks(task.messageHooks());
			task.events().forEach(event -> {
				addHooks(event.messageHooks());
				event.audience().ifPresent(this::collectAudience);
				event.playerHistory().ifPresent(history -> {
					collectAudience(history.audience());
					history.detailPage().ifPresent(this::addPage);
					requirePlayerTerminal(task.game());
				});
			});
			var callbacks = task.callbacks();
			callbacks.onStart().ifPresent(this.functions::add);
			callbacks.onPause().ifPresent(this.functions::add);
			callbacks.onResume().ifPresent(this.functions::add);
			callbacks.onTimeout().ifPresent(this.functions::add);
			callbacks.onSettled().ifPresent(this.functions::add);
			task.onStartPlayers().ifPresent(this::collectPlayerCallback);
			for (TaskResult result : task.results().values()) {
				result.onApply().ifPresent(this.functions::add);
				result.onApplyPlayers().ifPresent(this::collectPlayerCallback);
				result.route().nextTask().ifPresent(this::addTask);
				result.route().endPhase().ifPresent(phase -> addKey(DefinitionType.PHASE, phase));
				result.route().phase().ifPresent(phase -> addKey(DefinitionType.PHASE, phase));
				result.route().intermission().ifPresent(this::collectIntermission);
			}
			this.bindingGameContext = previousGameContext;
		}

		private void collectPlayerCallback(final PlayerCallback callback) {
			this.functions.add(callback.function());
			callback.audience().ifPresent(this::collectAudience);
			callback.fields().values().forEach(this::addField);
		}

		private void collectIntermission(final IntermissionDefinition intermission) {
			intermission.onStart().ifPresent(this.functions::add);
			intermission.onPause().ifPresent(this.functions::add);
			intermission.onResume().ifPresent(this.functions::add);
			intermission.onComplete().ifPresent(this.functions::add);
		}

		private void requirePlayerTerminal(final Identifier gameId) {
			if (!this.playerTerminalGames.add(gameId)) {
				return;
			}
			var game = this.definitions.games().get(gameId);
			if (game == null || game.playerTerminal().isEmpty()) {
				throw new IllegalArgumentException(
					"message hook task dependency requires missing player terminal for " + gameId
				);
			}
			addPage(game.playerTerminal().orElseThrow().defaultPage());
		}

		private void addField(final Identifier id) {
			if (addKey(DefinitionType.FIELD, id)) {
				this.fields.addLast(id);
			}
		}

		private void collectField(final Identifier id) {
			FieldDefinition field = this.definitions.fields().get(id);
			if (field == null) {
				throw new IllegalArgumentException("message hook dependency references missing field " + id);
			}
			addKey(DefinitionType.GAME, field.game());
			field.roles().forEach(role -> addKey(DefinitionType.ROLE, role));
			field.phases().forEach(phase -> addKey(DefinitionType.PHASE, phase));
			field.visibleWhen().ifPresent(this.predicates::add);
			field.exclusiveChoice().ifPresent(exclusive ->
				exclusive.options().forEach(option -> option.previewPage().ifPresent(this::addPage))
			);
		}

		private void addPage(final Identifier id) {
			if (addKey(DefinitionType.PAGE, id)) {
				this.pages.addLast(id);
			}
		}

		private void collectPage(final Identifier id) {
			PageDefinition page = this.definitions.pages().get(id);
			if (page == null) {
				throw new IllegalArgumentException("message hook dependency references missing page " + id);
			}
			addKey(DefinitionType.GAME, page.game());
			if (!page.theme().equals(UiDefinitions.DEFAULT_THEME)) {
				if (!this.definitions.themes().containsKey(page.theme())) {
					throw new IllegalArgumentException(
						"message hook dependency page references missing theme " + page.theme()
					);
				}
				addKey(DefinitionType.THEME, page.theme());
			}
			Identifier previousGameContext = this.bindingGameContext;
			this.bindingGameContext = page.game();
			collectPageNode(page.root());
			this.bindingGameContext = previousGameContext;
		}

		private void collectPageNode(final NodeDefinition node) {
			node.visibleWhen().ifPresent(this::collectConditionExpression);
			if (node.content() instanceof ChildrenContent children) {
				children.children().forEach(this::collectPageNode);
			} else if (node.content() instanceof SingleChildContent single) {
				collectPageNode(single.child());
			} else if (node.content() instanceof RepeatContent repeat) {
				collectValue(repeat.items());
				collectValue(repeat.itemKey());
				collectPageNode(repeat.template());
			} else if (node.content() instanceof FieldInputContent field) {
				addField(field.field());
			} else if (node.content() instanceof ButtonContent button) {
				collectTextTemplate(button.label());
				button.enabledWhen().ifPresent(this::collectConditionExpression);
				button.disabledReason().ifPresent(this::collectTextTemplate);
				button.action().registeredAction().ifPresent(this::addPlayerAction);
			} else if (node.content() instanceof UiDefinitions.TextContent text) {
				collectTextTemplate(text.text());
			} else if (node.content() instanceof UiDefinitions.ImageContent image) {
				image.alternativeText().ifPresent(this::collectTextTemplate);
			} else if (node.content() instanceof ProgressContent progress) {
				collectValue(progress.value());
				collectValue(progress.minimum());
				collectValue(progress.maximum());
				progress.label().ifPresent(this::collectTextTemplate);
			} else if (node.content() instanceof PlayerHeadContent head) {
				collectValue(head.uuid());
				collectValue(head.name());
				collectValue(head.online());
			}
		}

		private void collectTextTemplate(final TextTemplate text) {
			if (text instanceof UiDefinitions.BoundText bound) {
				collectValue(bound.binding());
				bound.fallback().ifPresent(this::collectTextTemplate);
			} else if (text instanceof DurationTicksText duration) {
				collectValue(duration.value());
				duration.fallback().ifPresent(this::collectTextTemplate);
			} else if (text instanceof TranslatedText translated) {
				translated.arguments().forEach(this::collectValue);
				translated.fallback().ifPresent(this::collectTextTemplate);
			} else if (text instanceof ConcatenatedText concatenated) {
				concatenated.parts().forEach(this::collectTextTemplate);
			}
		}

		private void collectValue(final ValueExpression value) {
			if (value instanceof BindingValue binding) {
				collectBinding(binding.path());
			}
		}

		private void addPlayerAction(final Identifier id) {
			if (addKey(DefinitionType.PLAYER_ACTION, id)) {
				this.playerActions.addLast(id);
			}
		}

		private void collectPlayerAction(final Identifier id) {
			PlayerActionDefinition action = this.definitions.playerActions().get(id);
			if (action == null) {
				throw new IllegalArgumentException(
					"message hook dependency references missing player action " + id
				);
			}
			Identifier previousGameContext = this.bindingGameContext;
			this.bindingGameContext = action.game();
			addKey(DefinitionType.GAME, action.game());
			collectAudience(action.audience());
			action.phases().forEach(phase -> addKey(DefinitionType.PHASE, phase));
			action.tasks().forEach(this::addTask);
			action.predicate().ifPresent(this.predicates::add);
			if (action.operation() instanceof PlayerOpenPageOperation openPage) {
				addPage(openPage.page());
			} else if (action.operation() instanceof PlayerRunFunctionOperation runFunction) {
				this.functions.add(runFunction.function());
			}
			this.bindingGameContext = previousGameContext;
		}

		private void collectAudience(final MessageDefinitions.AudienceSpec audience) {
			audience.roles().forEach(role -> addKey(DefinitionType.ROLE, role));
			audience.teams().forEach(team -> addKey(DefinitionType.TEAM, team));
			audience.lifeStates().forEach(state -> addKey(DefinitionType.LIFE_STATE, state));
			if (!audience.roleTags().isEmpty()) {
				addAllGameIdentities(DefinitionType.ROLE);
			}
			if (!audience.teamTags().isEmpty()) {
				addAllGameIdentities(DefinitionType.TEAM);
			}
			if (!audience.lifeStateTags().isEmpty()) {
				addAllGameIdentities(DefinitionType.LIFE_STATE);
			}
		}

		private void collectAudience(final Audience audience) {
			audience.roles().forEach(role -> addKey(DefinitionType.ROLE, role));
			audience.teams().forEach(team -> addKey(DefinitionType.TEAM, team));
			audience.lifeStates().forEach(state -> addKey(DefinitionType.LIFE_STATE, state));
			if (!audience.roleTags().isEmpty()) {
				addAllGameIdentities(DefinitionType.ROLE);
			}
			if (!audience.teamTags().isEmpty()) {
				addAllGameIdentities(DefinitionType.TEAM);
			}
			if (!audience.lifeStateTags().isEmpty()) {
				addAllGameIdentities(DefinitionType.LIFE_STATE);
			}
		}

		private void addAllGameIdentities(final DefinitionType type) {
			Identifier game = this.bindingGameContext;
			if (game == null) {
				throw new IllegalArgumentException(
					"message identity dependency has no owning game context: " + type
				);
			}
			switch (type) {
				case ROLE -> this.definitions.roles().values().stream()
					.filter(value -> value.game().equals(game))
					.map(value -> value.id())
					.sorted()
					.forEach(id -> addKey(DefinitionType.ROLE, id));
				case TEAM -> this.definitions.teams().values().stream()
					.filter(value -> value.game().equals(game))
					.map(value -> value.id())
					.sorted()
					.forEach(id -> addKey(DefinitionType.TEAM, id));
				case LIFE_STATE -> this.definitions.lifeStates().values().stream()
					.filter(value -> value.game().equals(game))
					.map(value -> value.id())
					.sorted()
					.forEach(id -> addKey(DefinitionType.LIFE_STATE, id));
				default -> throw new IllegalArgumentException(
					"unsupported identity dependency type " + type
				);
			}
		}

		private boolean addKey(final DefinitionType type, final Identifier id) {
			return this.sourceKeys.add(new DocumentKey(type, id));
		}

		private static Identifier requireIdentifier(final String value, final String context) {
			Identifier id = Identifier.tryParse(value);
			if (id == null) {
				throw new IllegalArgumentException(
					"message hook dependency contains invalid identifier " + context
				);
			}
			return id;
		}
	}
}
