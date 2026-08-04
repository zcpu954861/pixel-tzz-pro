package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FunctionNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookSet;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookFreezeClosure.Closure;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.IntermissionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.PlayerCallback;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Freezes the validated task plan selected by one game definition.
 *
 * <p>The envelope stores canonical source documents and restores them through
 * {@link DefinitionCompiler}; it intentionally does not maintain a second task parser.
 */
public final class TimelineSnapshotCompiler {
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_EXTERNAL_REFERENCES = 16_384;
	private static final Identifier REFERENCE_CONDITION = Identifier.fromNamespaceAndPath(
		"minecraft",
		"reference"
	);
	private static final Set<String> ROOT_KEYS = Set.of(
		"format_version",
		"definition_generation",
		"game",
		"sources",
		"functions",
		"predicates",
		"predicate_documents"
	);
	private static final Set<String> SOURCE_KEYS = Set.of("type", "id", "document", "document_json");

	private TimelineSnapshotCompiler() {
	}

	public static FreezeResult freeze(
		final DefinitionSnapshot definitions,
		final GameDefinition game
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(game, "game");
		if (definitions.generation() < 0L) {
			return FreezeResult.rejected("snapshot_invalid", "definition generation is negative");
		}
		if (!game.equals(definitions.games().get(game.id()))) {
			return FreezeResult.rejected(
				"snapshot_invalid",
				"game is not part of the supplied definition generation"
			);
		}
		if (game.taskTimeline().isEmpty()) {
			return FreezeResult.rejected("timeline_not_registered", "game has no task timeline");
		}

		Set<Identifier> reachable;
		try {
			reachable = reachableTasks(definitions, game);
		} catch (IllegalArgumentException error) {
			return FreezeResult.rejected("snapshot_invalid", message(error));
		}
		Closure messageClosure;
		try {
			messageClosure = MessageHookFreezeClosure.collect(
				definitions,
				timelineMessageHooks(definitions, game, reachable)
			);
		} catch (IllegalArgumentException error) {
			return FreezeResult.rejected("snapshot_invalid", message(error));
		}

		List<DocumentKey> keys = supportingKeys(
			definitions,
			game.id(),
			reachable,
			messageClosure
		);
		if (keys.size() > DefinitionCompiler.MAX_DEFINITIONS) {
			return FreezeResult.rejected(
				"snapshot_too_large",
				"timeline definition count exceeds " + DefinitionCompiler.MAX_DEFINITIONS
			);
		}

		JsonObject root = new JsonObject();
		root.addProperty("format_version", FORMAT_VERSION);
		root.addProperty("definition_generation", definitions.generation());
		root.addProperty("game", game.id().toString());
		JsonArray sources = new JsonArray();
		for (DocumentKey key : keys) {
			SourceDocument document = definitions.sourceDocuments().get(key);
			if (document == null) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"definition has no retained canonical source document: "
						+ key.type().directory()
						+ "/"
						+ key.id()
				);
			}
			JsonObject source = new JsonObject();
			source.addProperty("type", key.type().directory());
			source.addProperty("id", key.id().toString());
			try {
				JsonElement parsed = JsonParser.parseString(document.canonicalJson());
				if (parsed.isJsonObject()) {
					source.add("document", parsed);
				} else if (isMessageType(key.type())) {
					source.addProperty("document_json", document.canonicalJson());
				} else {
					throw new IllegalArgumentException("definition document must be an object");
				}
			} catch (RuntimeException error) {
				if (isMessageType(key.type())) {
					source.addProperty("document_json", document.canonicalJson());
					sources.add(source);
					continue;
				}
				return FreezeResult.rejected(
					"snapshot_invalid",
					"canonical source document is invalid JSON: " + key.id()
				);
			}
			sources.add(source);
		}
		root.add("sources", sources);
		// FORMAT_VERSION 1 has always serialized the complete discovered function set. Keep that
		// envelope contract so active 2D/V3A timelines remain restorable after this closure landed.
		Set<Identifier> functions = new LinkedHashSet<>(definitions.functions());
		Set<Identifier> predicates = new LinkedHashSet<>(referencedPredicates(definitions, keys));
		predicates.addAll(messageClosure.predicates());
		if (
			functions.size() > MAX_EXTERNAL_REFERENCES
				|| predicates.size() > MAX_EXTERNAL_REFERENCES
		) {
			return FreezeResult.rejected(
				"snapshot_too_large",
				"external reference count exceeds " + MAX_EXTERNAL_REFERENCES
			);
		}
		root.add("functions", identifiers(functions));
		root.add("predicates", identifiers(predicates));
		JsonObject predicateDocuments = new JsonObject();
		for (Identifier predicate : predicates.stream().sorted().toList()) {
			String json = definitions.predicateDocuments().get(predicate);
			if (json == null) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"referenced predicate has no retained document: " + predicate
				);
			}
			if (json.length() > DefinitionCompiler.MAX_DEFINITION_CHARACTERS) {
				return FreezeResult.rejected(
					"snapshot_too_large",
					"predicate "
						+ predicate
						+ " exceeds "
						+ DefinitionCompiler.MAX_DEFINITION_CHARACTERS
						+ " characters"
				);
			}
			try {
				JsonElement document = JsonParser.parseString(json);
				if (!document.isJsonObject()) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"predicate document must be an object: " + predicate
					);
				}
				if (containsReferenceCondition(document)) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"predicate "
							+ predicate
							+ " uses minecraft:reference and is not self-contained"
					);
				}
				predicateDocuments.add(predicate.toString(), document);
			} catch (RuntimeException error) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"predicate " + predicate + " is not valid JSON: " + message(error)
				);
			}
		}
		root.add("predicate_documents", predicateDocuments);

		String normalizedJson = root.toString();
		if (
			normalizedJson.getBytes(StandardCharsets.UTF_8).length
				> WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
		) {
			return FreezeResult.rejected(
				"snapshot_too_large",
				"timeline snapshot exceeds "
					+ WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
					+ " UTF-8 bytes"
			);
		}
		FrozenDocument frozen = FrozenDocument.of(normalizedJson);
		RestoreResult restored = restore(game.id(), frozen);
		if (!restored.success()) {
			return FreezeResult.rejected(restored.code(), restored.message());
		}
		return FreezeResult.success(frozen, restored.snapshot().orElseThrow());
	}

	public static RestoreResult restore(
		final Identifier expectedGame,
		final FrozenDocument frozen
	) {
		Objects.requireNonNull(expectedGame, "expectedGame");
		Objects.requireNonNull(frozen, "frozen");
		if (!FrozenDocument.of(frozen.normalizedJson()).sha256().equals(frozen.sha256())) {
			return RestoreResult.rejected("snapshot_invalid", "timeline snapshot hash mismatch");
		}
		if (
			frozen.normalizedJson().getBytes(StandardCharsets.UTF_8).length
				> WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
		) {
			return RestoreResult.rejected(
				"snapshot_too_large",
				"timeline snapshot exceeds "
					+ WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
					+ " UTF-8 bytes"
			);
		}

		try {
			JsonElement parsed = JsonParser.parseString(frozen.normalizedJson());
			if (!parsed.isJsonObject()) {
				return RestoreResult.rejected("snapshot_invalid", "timeline snapshot root must be an object");
			}
			JsonObject root = parsed.getAsJsonObject();
			String unknownRootKey = unknownKey(root, ROOT_KEYS);
			if (unknownRootKey != null) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"unknown timeline snapshot key " + unknownRootKey
				);
			}
			if (
				requiredInt(root, "format_version") != FORMAT_VERSION
					|| requiredLong(root, "definition_generation") < 0L
			) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"unsupported timeline snapshot format or generation"
				);
			}
			long generation = requiredLong(root, "definition_generation");
			Identifier gameId = requiredIdentifier(root, "game");
			if (!expectedGame.equals(gameId)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"timeline snapshot belongs to " + gameId + ", not " + expectedGame
				);
			}

			JsonArray sourceArray = requiredArray(root, "sources");
			if (
				sourceArray.isEmpty()
					|| sourceArray.size() > DefinitionCompiler.MAX_DEFINITIONS
			) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"timeline snapshot source count is invalid"
				);
			}
			List<Source> sources = new ArrayList<>(sourceArray.size());
			Set<DocumentKey> sourceKeys = new HashSet<>();
			for (JsonElement element : sourceArray) {
				if (!element.isJsonObject()) {
					return RestoreResult.rejected(
						"snapshot_invalid",
						"timeline snapshot source must be an object"
					);
				}
				JsonObject entry = element.getAsJsonObject();
				String unknownSourceKey = unknownKey(entry, SOURCE_KEYS);
				if (unknownSourceKey != null) {
					return RestoreResult.rejected(
						"snapshot_invalid",
						"unknown timeline source key " + unknownSourceKey
					);
				}
				DefinitionType type = DefinitionType.byDirectory(requiredString(entry, "type"))
					.orElseThrow(() -> new IllegalArgumentException("unknown definition type"));
				Identifier id = requiredIdentifier(entry, "id");
				JsonElement document = entry.get("document");
				JsonElement rawDocument = entry.get("document_json");
				if ((document == null) == (rawDocument == null)) {
					throw new IllegalArgumentException(
						"source must contain exactly one document representation"
					);
				}
				String sourceJson;
				if (document != null) {
					if (!document.isJsonObject()) {
						throw new IllegalArgumentException("source document must be an object");
					}
					sourceJson = document.toString();
				} else {
					if (
						!isMessageType(type)
							|| !rawDocument.isJsonPrimitive()
							|| !rawDocument.getAsJsonPrimitive().isString()
					) {
						throw new IllegalArgumentException(
							"raw source documents are valid only for message definitions"
						);
					}
					sourceJson = rawDocument.getAsString();
				}
				if (!sourceKeys.add(new DocumentKey(type, id))) {
					throw new IllegalArgumentException("duplicate timeline source " + type + " " + id);
				}
				sources.add(
					new Source(
						type,
						id,
						Identifier.fromNamespaceAndPath(
							id.getNamespace(),
							"pixel_tzz_pro/frozen_timeline/"
								+ type.directory()
								+ "/"
								+ id.getPath()
								+ ".json"
						),
						"pixel-tzz-pro:timeline-frozen",
						sourceJson
					)
				);
			}
			Set<Identifier> functions = identifierSet(
				requiredArray(root, "functions"),
				"function"
			);
			Set<Identifier> predicates = identifierSet(
				requiredArray(root, "predicates"),
				"predicate"
			);
			boolean hasPredicateDocuments = root.has("predicate_documents");
			Map<Identifier, String> predicateDocuments = hasPredicateDocuments
				? predicateDocuments(requiredObject(root, "predicate_documents"))
				: Map.of();
			if (
				hasPredicateDocuments
					&& !predicateDocuments.keySet().equals(predicates)
			) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"predicate documents must exactly cover predicate references"
				);
			}

			Compilation compilation = DefinitionCompiler.compile(sources, functions, predicates);
			if (!compilation.valid()) {
				String diagnostic = compilation.problems().isEmpty()
					? "frozen timeline definitions did not compile"
					: compilation.problems().getFirst().summary();
				return RestoreResult.rejected("snapshot_invalid", diagnostic);
			}
			DefinitionSnapshot restored = compilation.snapshot()
				.orElseThrow()
				.withGeneration(generation);
			if (hasPredicateDocuments) {
				restored = restored.withPredicateDocuments(predicateDocuments);
			}
			if (!restored.games().keySet().equals(Set.of(expectedGame))) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"timeline snapshot must contain exactly its owning game"
				);
			}
			GameDefinition restoredGame = restored.games().get(expectedGame);
			if (restoredGame.taskTimeline().isEmpty()) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"restored game lost its task timeline"
				);
			}
			Set<Identifier> reachable = reachableTasks(restored, restoredGame);
			Closure messageClosure = MessageHookFreezeClosure.collect(
				restored,
				timelineMessageHooks(restored, restoredGame, reachable)
			);
			Set<DocumentKey> expectedSourceKeys = Set.copyOf(
				supportingKeys(restored, expectedGame, reachable, messageClosure)
			);
			if (!sourceKeys.equals(expectedSourceKeys)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"timeline snapshot source closure does not match its reachable plan"
				);
			}
			Set<Identifier> expectedPredicates = new LinkedHashSet<>(
				referencedPredicates(restored, expectedSourceKeys)
			);
			expectedPredicates.addAll(messageClosure.predicates());
			if (hasPredicateDocuments && !expectedPredicates.equals(predicates)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"predicate references do not match the restored timeline definitions"
				);
			}
			Set<Identifier> expectedFunctions = new LinkedHashSet<>(
				referencedFunctions(restored, expectedSourceKeys)
			);
			expectedFunctions.addAll(messageClosure.functions());
			// v1 snapshots may legitimately contain unrelated discovered functions. The required
			// closure must be present, but an existing safe superset is backward compatible.
			if (!functions.containsAll(expectedFunctions)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"function references do not match the restored timeline definitions"
				);
			}
			return RestoreResult.success(restored);
		} catch (RuntimeException | StackOverflowError error) {
			return RestoreResult.rejected("snapshot_invalid", message(error));
		}
	}

	private static Set<Identifier> reachableTasks(
		final DefinitionSnapshot definitions,
		final GameDefinition game
	) {
		Identifier initialTask = game.taskTimeline()
			.orElseThrow(() -> new IllegalArgumentException("game has no task timeline"))
			.initialTask();
		Set<Identifier> result = new LinkedHashSet<>();
		ArrayDeque<Identifier> pending = new ArrayDeque<>();
		pending.add(initialTask);
		while (!pending.isEmpty()) {
			Identifier taskId = pending.removeFirst();
			if (!result.add(taskId)) {
				continue;
			}
			TaskDefinition task = definitions.tasks().get(taskId);
			if (task == null || !task.game().equals(game.id())) {
				throw new IllegalArgumentException(
					"reachable task " + taskId + " is missing or belongs to another game"
				);
			}
			task.results()
				.values()
				.stream()
				.map(TaskResult::route)
				.map(route -> route.nextTask())
				.flatMap(Optional::stream)
				.sorted()
				.forEach(pending::addLast);
			if (result.size() > DefinitionCompiler.MAX_TASKS_PER_GAME) {
				throw new IllegalArgumentException(
					"reachable task count exceeds " + DefinitionCompiler.MAX_TASKS_PER_GAME
				);
			}
		}
		return Set.copyOf(result);
	}

	private static List<MessageHookSet> timelineMessageHooks(
		final DefinitionSnapshot definitions,
		final GameDefinition game,
		final Set<Identifier> reachableTasks
	) {
		List<MessageHookSet> hooks = new ArrayList<>();
		hooks.add(game.messageHooks());
		game.readiness().ifPresent(readiness -> hooks.add(readiness.messageHooks()));
		definitions.roles().values().stream()
			.filter(role -> role.game().equals(game.id()))
			.sorted(Comparator.comparing(GameDefinitions.RoleDefinition::id))
			.map(GameDefinitions.RoleDefinition::messageHooks)
			.forEach(hooks::add);
		definitions.phases().values().stream()
			.filter(phase -> phase.game().equals(game.id()))
			.sorted(Comparator.comparing(GameDefinitions.PhaseDefinition::id))
			.map(GameDefinitions.PhaseDefinition::messageHooks)
			.forEach(hooks::add);
		definitions.flows().values().stream()
			.filter(flow -> flow.game().equals(game.id()))
			.sorted(Comparator.comparing(GameDefinitions.FlowDefinition::id))
			.map(GameDefinitions.FlowDefinition::messageHooks)
			.forEach(hooks::add);
		reachableTasks.stream().sorted().forEach(taskId -> {
			TaskDefinition task = definitions.tasks().get(taskId);
			if (task == null) {
				throw new IllegalArgumentException("reachable task is missing: " + taskId);
			}
			hooks.add(task.messageHooks());
			task.events().forEach(event -> hooks.add(event.messageHooks()));
		});
		return List.copyOf(hooks);
	}

	private static Set<Identifier> referencedPredicates(
		final DefinitionSnapshot definitions,
		final Collection<DocumentKey> keys
	) {
		Set<Identifier> result = new LinkedHashSet<>();
		for (DocumentKey key : keys) {
			switch (key.type()) {
				case FIELD -> Optional.ofNullable(definitions.fields().get(key.id()))
					.flatMap(GameDefinitions.FieldDefinition::visibleWhen)
					.ifPresent(result::add);
				case FLOW -> Optional.ofNullable(definitions.flows().get(key.id()))
					.ifPresent(flow -> flow.nodes().values().stream()
						.filter(BranchNode.class::isInstance)
						.map(BranchNode.class::cast)
						.forEach(branch -> branch.cases().forEach(value -> result.add(value.predicate()))));
				case PANEL_ACTION -> Optional.ofNullable(definitions.panelActions().get(key.id()))
					.ifPresent(action -> {
						action.visibleWhen().ifPresent(result::add);
						action.enabledWhen().ifPresent(result::add);
					});
				case PLAYER_ROUTE -> Optional.ofNullable(definitions.playerRoutes().get(key.id()))
					.flatMap(PlayerTerminalDefinitions.PlayerRouteDefinition::predicate)
					.ifPresent(result::add);
				case PLAYER_DATA -> Optional.ofNullable(definitions.playerData().get(key.id()))
					.flatMap(PlayerTerminalDefinitions.PlayerDataDefinition::predicate)
					.ifPresent(result::add);
				case PLAYER_ACTION -> Optional.ofNullable(definitions.playerActions().get(key.id()))
					.flatMap(PlayerTerminalDefinitions.PlayerActionDefinition::predicate)
					.ifPresent(result::add);
				case GAME,
					ROLE,
					TEAM,
					LIFE_STATE,
					PHASE,
					TASK,
					PAGE,
					THEME,
					TEXT_EFFECT,
					MESSAGE_CUE -> {
				}
			}
		}
		return Set.copyOf(result);
	}

	private static Set<Identifier> referencedFunctions(
		final DefinitionSnapshot definitions,
		final Collection<DocumentKey> keys
	) {
		Set<Identifier> result = new LinkedHashSet<>();
		for (DocumentKey key : keys) {
			switch (key.type()) {
				case PHASE -> Optional.ofNullable(definitions.phases().get(key.id()))
					.ifPresent(phase -> {
						phase.onEnter().ifPresent(result::add);
						phase.onExit().ifPresent(result::add);
					});
				case FLOW -> Optional.ofNullable(definitions.flows().get(key.id()))
					.ifPresent(flow -> {
						flow.onStart().ifPresent(result::add);
						flow.onPlayerComplete().ifPresent(result::add);
						flow.onAllComplete().ifPresent(result::add);
						flow.nodes().values().stream()
							.filter(FunctionNode.class::isInstance)
							.map(FunctionNode.class::cast)
							.map(FunctionNode::function)
							.forEach(result::add);
					});
				case TASK -> Optional.ofNullable(definitions.tasks().get(key.id()))
					.ifPresent(task -> collectTaskFunctions(task, result));
				case PANEL_ACTION -> Optional.ofNullable(definitions.panelActions().get(key.id()))
					.map(GameDefinitions.PanelActionDefinition::operation)
					.filter(RunFunctionOperation.class::isInstance)
					.map(RunFunctionOperation.class::cast)
					.map(RunFunctionOperation::function)
					.ifPresent(result::add);
				case PLAYER_ACTION -> Optional.ofNullable(definitions.playerActions().get(key.id()))
					.map(PlayerTerminalDefinitions.PlayerActionDefinition::operation)
					.filter(PlayerRunFunctionOperation.class::isInstance)
					.map(PlayerRunFunctionOperation.class::cast)
					.map(PlayerRunFunctionOperation::function)
					.ifPresent(result::add);
				case GAME,
					ROLE,
					TEAM,
					LIFE_STATE,
					FIELD,
					PLAYER_ROUTE,
					PLAYER_DATA,
					PAGE,
					THEME,
					TEXT_EFFECT,
					MESSAGE_CUE -> {
				}
			}
		}
		return Set.copyOf(result);
	}

	private static void collectTaskFunctions(
		final TaskDefinition task,
		final Set<Identifier> result
	) {
		var callbacks = task.callbacks();
		callbacks.onStart().ifPresent(result::add);
		callbacks.onPause().ifPresent(result::add);
		callbacks.onResume().ifPresent(result::add);
		callbacks.onTimeout().ifPresent(result::add);
		callbacks.onSettled().ifPresent(result::add);
		task.onStartPlayers().map(PlayerCallback::function).ifPresent(result::add);
		for (TaskResult taskResult : task.results().values()) {
			taskResult.onApply().ifPresent(result::add);
			taskResult.onApplyPlayers().map(PlayerCallback::function).ifPresent(result::add);
			taskResult.route().intermission().ifPresent(intermission -> {
				intermission.onStart().ifPresent(result::add);
				intermission.onPause().ifPresent(result::add);
				intermission.onResume().ifPresent(result::add);
				intermission.onComplete().ifPresent(result::add);
			});
		}
	}

	private static boolean isMessageType(final DefinitionType type) {
		return type == DefinitionType.TEXT_EFFECT || type == DefinitionType.MESSAGE_CUE;
	}

	private static List<DocumentKey> supportingKeys(
		final DefinitionSnapshot definitions,
		final Identifier gameId,
		final Set<Identifier> reachableTasks,
		final Closure messageClosure
	) {
		List<DocumentKey> keys = new ArrayList<>();
		keys.add(new DocumentKey(DefinitionType.GAME, gameId));
		definitions.roles()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.ROLE, value.id())));
		definitions.teams()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.TEAM, value.id())));
		definitions.lifeStates()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.LIFE_STATE, value.id())));
		definitions.phases()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.PHASE, value.id())));
		definitions.fields()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.FIELD, value.id())));
		definitions.flows()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.FLOW, value.id())));
		reachableTasks.forEach(id -> keys.add(new DocumentKey(DefinitionType.TASK, id)));
		definitions.panelActions()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.PANEL_ACTION, value.id())));
		definitions.playerRoutes()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.PLAYER_ROUTE, value.id())));
		// Player-data definitions are part of the timeline's pre-existing support set: the
		// active-game terminal, history and personal-field projections may read any same-game
		// entry even when no message cue references it. Message closure only adds dependencies.
		definitions.playerData()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.PLAYER_DATA, value.id())));
		definitions.playerActions()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.PLAYER_ACTION, value.id())));
		Set<Identifier> themes = new LinkedHashSet<>();
		definitions.pages()
			.values()
			.stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> {
				keys.add(new DocumentKey(DefinitionType.PAGE, value.id()));
				if (!value.theme().equals(UiDefinitions.DEFAULT_THEME)) {
					themes.add(value.theme());
				}
			});
		themes.forEach(id -> keys.add(new DocumentKey(DefinitionType.THEME, id)));
		keys.addAll(messageClosure.sourceKeys());
		// ponytail: v1 freezes the same-game support set so restore can reuse the strict compiler.
		// If the 2 MiB ceiling becomes practical, replace this with a transitive support closure.
		return keys.stream()
			.distinct()
			.sorted(
				Comparator.comparing((DocumentKey key) -> key.type().ordinal())
					.thenComparing(DocumentKey::id)
			)
			.toList();
	}

	private static Map<Identifier, String> predicateDocuments(final JsonObject values) {
		if (values.size() > MAX_EXTERNAL_REFERENCES) {
			throw new IllegalArgumentException("predicate document count exceeds limit");
		}
		Map<Identifier, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null || result.containsKey(id)) {
				throw new IllegalArgumentException(
					"invalid or duplicate predicate document identifier"
				);
			}
			JsonElement document = entry.getValue();
			if (!document.isJsonObject()) {
				throw new IllegalArgumentException(
					"predicate document must be an object: " + id
				);
			}
			if (containsReferenceCondition(document)) {
				throw new IllegalArgumentException(
					"predicate "
						+ id
						+ " uses minecraft:reference and is not self-contained"
				);
			}
			String normalized = document.toString();
			if (normalized.length() > DefinitionCompiler.MAX_DEFINITION_CHARACTERS) {
				throw new IllegalArgumentException(
					"predicate document exceeds character limit: " + id
				);
			}
			result.put(id, normalized);
		}
		return Map.copyOf(result);
	}

	private static boolean containsReferenceCondition(final JsonElement element) {
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsReferenceCondition(child)) {
					return true;
				}
			}
			return false;
		}
		if (!element.isJsonObject()) {
			return false;
		}
		JsonElement condition = element.getAsJsonObject().get("condition");
		if (
			condition != null
				&& condition.isJsonPrimitive()
				&& condition.getAsJsonPrimitive().isString()
				&& REFERENCE_CONDITION.equals(Identifier.tryParse(condition.getAsString()))
		) {
			return true;
		}
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (containsReferenceCondition(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private static JsonArray identifiers(final Set<Identifier> values) {
		JsonArray result = new JsonArray();
		values.stream().sorted().map(Identifier::toString).forEach(result::add);
		return result;
	}

	private static Set<Identifier> identifierSet(
		final JsonArray values,
		final String label
	) {
		if (values.size() > MAX_EXTERNAL_REFERENCES) {
			throw new IllegalArgumentException(label + " reference count exceeds limit");
		}
		Set<Identifier> result = new LinkedHashSet<>();
		for (JsonElement element : values) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException(label + " reference must be a string");
			}
			Identifier id = Identifier.tryParse(element.getAsString());
			if (id == null || !result.add(id)) {
				throw new IllegalArgumentException("invalid or duplicate " + label + " reference");
			}
		}
		return Set.copyOf(result);
	}

	private static JsonArray requiredArray(final JsonObject object, final String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonArray()) {
			throw new IllegalArgumentException(key + " must be an array");
		}
		return value.getAsJsonArray();
	}

	private static JsonObject requiredObject(final JsonObject object, final String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(key + " must be an object");
		}
		return value.getAsJsonObject();
	}

	private static String requiredString(final JsonObject object, final String key) {
		JsonElement value = object.get(key);
		if (
			value == null
				|| !value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isString()
		) {
			throw new IllegalArgumentException(key + " must be a string");
		}
		return value.getAsString();
	}

	private static int requiredInt(final JsonObject object, final String key) {
		JsonElement value = object.get(key);
		if (
			value == null
				|| !value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isNumber()
		) {
			throw new IllegalArgumentException(key + " must be an integer");
		}
		try {
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(key + " must be an integer", error);
		}
	}

	private static long requiredLong(final JsonObject object, final String key) {
		JsonElement value = object.get(key);
		if (
			value == null
				|| !value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isNumber()
		) {
			throw new IllegalArgumentException(key + " must be an integer");
		}
		try {
			return value.getAsBigDecimal().longValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(key + " must be an integer", error);
		}
	}

	private static Identifier requiredIdentifier(final JsonObject object, final String key) {
		Identifier value = Identifier.tryParse(requiredString(object, key));
		if (value == null) {
			throw new IllegalArgumentException(key + " must be an identifier");
		}
		return value;
	}

	private static String unknownKey(final JsonObject object, final Set<String> allowed) {
		return object.keySet()
			.stream()
			.filter(key -> !allowed.contains(key))
			.sorted()
			.findFirst()
			.orElse(null);
	}

	private static String message(final Throwable error) {
		return error.getMessage() == null
			? error.getClass().getSimpleName().toLowerCase(Locale.ROOT)
			: error.getMessage();
	}

	public record FreezeResult(
		Optional<FrozenDocument> frozen,
		Optional<DefinitionSnapshot> compiled,
		String code,
		String message
	) {
		public FreezeResult {
			frozen = Objects.requireNonNull(frozen, "frozen");
			compiled = Objects.requireNonNull(compiled, "compiled");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean success() {
			return this.frozen.isPresent() && this.compiled.isPresent();
		}

		private static FreezeResult success(
			final FrozenDocument frozen,
			final DefinitionSnapshot compiled
		) {
			return new FreezeResult(
				Optional.of(frozen),
				Optional.of(compiled),
				"success",
				""
			);
		}

		private static FreezeResult rejected(final String code, final String message) {
			return new FreezeResult(Optional.empty(), Optional.empty(), code, message);
		}
	}

	public record RestoreResult(
		Optional<DefinitionSnapshot> snapshot,
		String code,
		String message
	) {
		public RestoreResult {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean success() {
			return this.snapshot.isPresent();
		}

		private static RestoreResult success(final DefinitionSnapshot snapshot) {
			return new RestoreResult(Optional.of(snapshot), "success", "");
		}

		private static RestoreResult rejected(final String code, final String message) {
			return new RestoreResult(Optional.empty(), code, message);
		}
	}
}
