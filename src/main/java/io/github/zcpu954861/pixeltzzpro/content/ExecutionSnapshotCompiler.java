package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookSet;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookFreezeClosure.Closure;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChangeStateNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ConfirmNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FunctionNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.NodeScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.WaitPlayersNode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiAction;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackReferences;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ExecutionSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDefinitionType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenSourceDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Freezes and restores the executable 2C subset through the existing strict definition compiler.
 */
public final class ExecutionSnapshotCompiler {
	private static final Identifier REFERENCE_CONDITION = Identifier.fromNamespaceAndPath(
		"minecraft",
		"reference"
	);

	private ExecutionSnapshotCompiler() {
	}

	public static FreezeResult freeze(
		final DefinitionSnapshot definitions,
		final FlowDefinition flow,
		final PanelActionDefinition sourceAction
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(flow, "flow");
		Objects.requireNonNull(sourceAction, "sourceAction");
		if (definitions.generation() < 0L) {
			return FreezeResult.rejected("snapshot_invalid", "definition generation is negative", "");
		}
		if (!sourceAction.game().equals(flow.game())) {
			return FreezeResult.rejected(
				"snapshot_invalid",
				"source action and flow belong to different games",
				""
			);
		}

		for (FlowNode node : flow.nodes().values()) {
			String reason = unsupportedReason(node);
			if (reason != null) {
				return FreezeResult.rejected("unsupported_flow_node", reason, node.id());
			}
		}

		Map<Identifier, PageDefinition> pages = new LinkedHashMap<>();
		Set<Identifier> choiceFields = new LinkedHashSet<>();
		Set<Identifier> predicates = new LinkedHashSet<>();
		for (FlowNode node : flow.nodes().values()) {
			Identifier pageId = switch (node) {
				case PageNode page -> page.page();
				case ConfirmNode confirm -> confirm.page();
				case ChoiceNode choice -> choice.page();
				default -> null;
			};
			if (pageId != null) {
				PageDefinition page = definitions.pages().get(pageId);
				if (page == null) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"flow node references missing page " + pageId,
						node.id()
					);
				}
				String pageContractError = validatePageContract(node, page);
				if (pageContractError != null) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						pageContractError,
						node.id()
					);
				}
				pages.put(pageId, page);
			}
			if (node instanceof ChoiceNode choice) {
				choiceFields.add(choice.field());
			}
			if (node instanceof BranchNode branch) {
				branch.cases().forEach(branchCase -> predicates.add(branchCase.predicate()));
			}
		}

		Set<Identifier> fieldIds = new LinkedHashSet<>(choiceFields);
		pages.values().forEach(page -> collectFieldIds(page.root(), fieldIds));
		List<Identifier> pendingFields = new ArrayList<>(fieldIds);
		Map<Identifier, FieldDefinition> fields = new LinkedHashMap<>();
		for (int index = 0; index < pendingFields.size(); index++) {
			Identifier fieldId = pendingFields.get(index);
			FieldDefinition field = definitions.fields().get(fieldId);
			if (field == null) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"flow page references missing field " + fieldId,
					""
				);
			}
			fields.put(fieldId, field);
			field.visibleWhen().ifPresent(predicates::add);
			if (field.exclusiveChoice().isEmpty()) {
				continue;
			}
			for (var option : field.exclusiveChoice().orElseThrow().options()) {
				if (option.previewPage().isEmpty()) {
					continue;
				}
				Identifier previewId = option.previewPage().orElseThrow();
				PageDefinition preview = definitions.pages().get(previewId);
				if (preview == null) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"exclusive choice references missing preview page " + previewId,
						""
					);
				}
				if (pages.putIfAbsent(previewId, preview) != null) {
					continue;
				}
				Set<Identifier> previewFields = new LinkedHashSet<>();
				collectFieldIds(preview.root(), previewFields);
				for (Identifier previewField : previewFields) {
					if (fieldIds.add(previewField)) {
						pendingFields.add(previewField);
					}
				}
			}
		}
		sourceAction.visibleWhen().ifPresent(predicates::add);
		sourceAction.enabledWhen().ifPresent(predicates::add);

		Closure messageClosure;
		try {
			messageClosure = MessageHookFreezeClosure.collect(
				definitions,
				executionMessageHooks(definitions, flow, sourceAction)
			);
		} catch (IllegalArgumentException error) {
			return FreezeResult.rejected("snapshot_invalid", message(error), "");
		}
		predicates.addAll(messageClosure.predicates());
		for (DocumentKey key : messageClosure.sourceKeys()) {
			if (key.type() == DefinitionType.FIELD) {
				FieldDefinition field = definitions.fields().get(key.id());
				if (field != null) {
					fields.putIfAbsent(key.id(), field);
				}
			} else if (key.type() == DefinitionType.PAGE) {
				PageDefinition page = definitions.pages().get(key.id());
				if (page != null) {
					pages.putIfAbsent(key.id(), page);
				}
			}
		}

		Map<Identifier, ThemeDefinition> themes = new LinkedHashMap<>();
		for (PageDefinition page : pages.values()) {
			ThemeDefinition theme = page.theme().equals(BuiltInUiResources.defaultTheme().id())
				? BuiltInUiResources.defaultTheme()
				: definitions.themes().get(page.theme());
			if (theme == null) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"flow page references missing theme " + page.theme(),
					""
				);
			}
			themes.put(theme.id(), theme);
		}

		SourceDocument flowDocument = definitions.sourceDocuments()
			.get(new DocumentKey(DefinitionType.FLOW, flow.id()));
		if (flowDocument == null) {
			return FreezeResult.rejected(
				"snapshot_invalid",
				"flow has no retained canonical source document",
				""
			);
		}

		Map<Identifier, FrozenDocument> frozenPages = new LinkedHashMap<>();
		pages.forEach(
			(id, page) -> frozenPages.put(id, new FrozenDocument(page.canonicalDocument(), page.sha256()))
		);
		Map<Identifier, FrozenDocument> frozenThemes = new LinkedHashMap<>();
		themes.forEach(
			(id, theme) -> frozenThemes.put(id, new FrozenDocument(theme.canonicalDocument(), theme.sha256()))
		);
		Map<Identifier, FrozenDocument> frozenFields = new LinkedHashMap<>();
		for (Identifier fieldId : fields.keySet()) {
			SourceDocument document = definitions.sourceDocuments()
				.get(new DocumentKey(DefinitionType.FIELD, fieldId));
			if (document == null) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"field has no retained canonical source document: " + fieldId,
					""
				);
			}
			frozenFields.put(fieldId, frozen(document));
		}

		List<FrozenSourceDocument> support = freezeSupportingDefinitions(
			definitions,
			flow.game(),
			flow.id(),
			sourceAction,
			messageClosure
		);
		if (support == null) {
			return FreezeResult.rejected(
				"snapshot_invalid",
				"required game support definition has no canonical source document",
				""
			);
		}

		Set<String> textReferences = new LinkedHashSet<>();
		textReferences.add(flow.name().json());
		textReferences.add(sourceAction.label().json());
		textReferences.add(sourceAction.description().json());
		pages.values().forEach(page -> textReferences.add(page.title().json()));
		sourceAction.confirmation().ifPresent(confirmation -> {
			textReferences.add(confirmation.title().json());
			confirmation.consequences().forEach(text -> textReferences.add(text.json()));
		});

		Set<Identifier> sounds = new LinkedHashSet<>();
		themes.values().forEach(theme -> {
			theme.soundCues().values().forEach(cue -> sounds.add(cue.sound()));
			theme.assets()
				.stream()
				.filter(asset -> asset.type() == AssetType.SOUND)
				.map(asset -> Identifier.tryParse(asset.id()))
				.filter(Objects::nonNull)
				.forEach(sounds::add);
		});
		pages.values().forEach(
			page -> page.assets()
				.stream()
				.filter(asset -> asset.type() == AssetType.SOUND)
				.map(asset -> Identifier.tryParse(asset.id()))
				.filter(Objects::nonNull)
				.forEach(sounds::add)
		);

		String consequences = sourceAction.confirmation()
			.map(
				confirmation -> String.join(
					"\n",
					confirmation.consequences()
						.stream()
						.map(GameDefinitions.RichText::plainText)
						.toList()
				)
			)
			.orElse("");
		if (consequences.length() > 4_096) {
			return FreezeResult.rejected(
				"snapshot_too_large",
				"confirmation consequence summary exceeds 4096 characters",
				""
			);
		}

		Set<Identifier> functions = new LinkedHashSet<>();
		flow.onStart().ifPresent(functions::add);
		flow.onPlayerComplete().ifPresent(functions::add);
		flow.onAllComplete().ifPresent(functions::add);
		flow.nodes().values().stream()
			.filter(FunctionNode.class::isInstance)
			.map(FunctionNode.class::cast)
			.map(FunctionNode::function)
			.forEach(functions::add);
		definitions.phases().values().stream()
			.filter(phase -> phase.game().equals(flow.game()))
			.forEach(phase -> {
				phase.onEnter().ifPresent(functions::add);
				phase.onExit().ifPresent(functions::add);
			});
		if (sourceAction.operation() instanceof RunFunctionOperation runFunction) {
			functions.add(runFunction.function());
		}
		functions.addAll(messageClosure.functions());
		Map<Identifier, FrozenDocument> frozenPredicates = new LinkedHashMap<>();
		for (Identifier id : predicates) {
			String json = definitions.predicateDocuments().get(id);
			if (json == null) {
				/*
				 * Pre-V3B execution snapshots accepted legacy predicate identifiers without a
				 * retained document and delegated their evaluation to the active server resource
				 * manager. Keep that compatibility path for the flow/action graph. A declarative
				 * message hook, however, must freeze its complete dependency closure because the
				 * cue may outlive the data-pack generation that started this flow.
				 */
				if (messageClosure.predicates().contains(id)) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"predicate " + id + " has no retained document",
						""
					);
				}
				continue;
			}
			try {
				if (containsReferenceCondition(JsonParser.parseString(json))) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"predicate " + id + " uses minecraft:reference and is not self-contained",
						""
					);
				}
			} catch (RuntimeException error) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"predicate " + id + " is not valid JSON: " + message(error),
					""
				);
			}
			frozenPredicates.put(id, FrozenDocument.of(json));
		}

		ExecutionSnapshot candidate;
		try {
			candidate = new ExecutionSnapshot(
				definitions.generation(),
				frozen(flowDocument),
				frozenPages,
				frozenThemes,
				frozenFields,
				support,
				predicates,
				frozenPredicates,
				functions,
				new CallbackReferences(
					flow.onStart(),
					flow.onPlayerComplete(),
					flow.onAllComplete()
				),
				textReferences,
				sounds,
				consequences,
				"0".repeat(64)
			).withComputedContentSha256();
		} catch (ArithmeticException | IllegalArgumentException error) {
			return FreezeResult.rejected(
				"snapshot_too_large",
				message(error),
				""
			);
		}
		if (ExecutionSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, candidate).result().isEmpty()) {
			return FreezeResult.rejected(
				"snapshot_invalid",
				"frozen execution snapshot failed its persisted codec validation",
				""
			);
		}
		RestoreResult restored = restore(flow.id(), candidate);
		if (!restored.success()) {
			return FreezeResult.rejected(restored.code(), restored.message(), "");
		}
		return FreezeResult.success(candidate, restored.snapshot().orElseThrow());
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

	public static RestoreResult restore(
		final Identifier flowId,
		final ExecutionSnapshot frozen
	) {
		Objects.requireNonNull(flowId, "flowId");
		Objects.requireNonNull(frozen, "frozen");
		if (!frozen.contentSha256().equals(frozen.computeContentSha256())) {
			return RestoreResult.rejected("snapshot_invalid", "execution snapshot hash mismatch");
		}

		List<Source> sources = new ArrayList<>();
		sources.add(source(DefinitionType.FLOW, flowId, frozen.flow()));
		frozen.pages().forEach(
			(id, document) -> sources.add(source(DefinitionType.PAGE, id, document))
		);
		frozen.themes().forEach(
			(id, document) -> sources.add(source(DefinitionType.THEME, id, document))
		);
		frozen.fields().forEach(
			(id, document) -> sources.add(source(DefinitionType.FIELD, id, document))
		);
		frozen.supportingDefinitions().stream()
			.forEach(
				support -> sources.add(
					source(definitionType(support.type()), support.id(), support.document())
				)
			);

		Compilation compilation = DefinitionCompiler.compile(
			sources,
			frozen.functionReferences(),
			frozen.predicateReferences()
		);
		if (!compilation.valid()) {
			String diagnostic = compilation.problems().isEmpty()
				? "frozen definitions did not compile"
				: compilation.problems().getFirst().summary();
			return RestoreResult.rejected("snapshot_invalid", diagnostic);
		}
		DefinitionSnapshot restored = compilation.snapshot()
			.orElseThrow()
			.withGeneration(frozen.definitionGeneration());
		if (!frozen.predicateDocuments().isEmpty()) {
			Map<Identifier, String> predicateDocuments = new LinkedHashMap<>();
			frozen.predicateDocuments().forEach(
				(id, document) -> predicateDocuments.put(id, document.normalizedJson())
			);
			restored = restored.withPredicateDocuments(predicateDocuments);
		}
		if (!restored.flows().containsKey(flowId)) {
			return RestoreResult.rejected("snapshot_invalid", "restored snapshot lost its flow");
		}
		return RestoreResult.success(restored);
	}

	private static List<MessageHookSet> executionMessageHooks(
		final DefinitionSnapshot definitions,
		final FlowDefinition flow,
		final PanelActionDefinition sourceAction
	) {
		var game = definitions.games().get(flow.game());
		if (game == null) {
			throw new IllegalArgumentException("flow references missing game " + flow.game());
		}
		List<MessageHookSet> hooks = new ArrayList<>();
		hooks.add(game.messageHooks());
		game.readiness()
			.filter(readiness -> readiness.action().equals(sourceAction.id()))
			.ifPresent(readiness -> hooks.add(readiness.messageHooks()));
		definitions.roles().values().stream()
			.filter(role -> role.game().equals(flow.game()))
			.sorted(Comparator.comparing(GameDefinitions.RoleDefinition::id))
			.map(GameDefinitions.RoleDefinition::messageHooks)
			.forEach(hooks::add);
		definitions.phases().values().stream()
			.filter(phase -> phase.game().equals(flow.game()))
			.sorted(Comparator.comparing(GameDefinitions.PhaseDefinition::id))
			.map(GameDefinitions.PhaseDefinition::messageHooks)
			.forEach(hooks::add);
		hooks.add(flow.messageHooks());
		return List.copyOf(hooks);
	}

	private static List<FrozenSourceDocument> freezeSupportingDefinitions(
		final DefinitionSnapshot definitions,
		final Identifier gameId,
		final Identifier flowId,
		final PanelActionDefinition sourceAction,
		final Closure messageClosure
	) {
		Set<DocumentKey> keys = new LinkedHashSet<>();
		keys.add(new DocumentKey(DefinitionType.GAME, gameId));
		definitions.roles().values().stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.ROLE, value.id())));
		definitions.teams().values().stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.TEAM, value.id())));
		definitions.lifeStates().values().stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.LIFE_STATE, value.id())));
		definitions.phases().values().stream()
			.filter(value -> value.game().equals(gameId))
			.forEach(value -> keys.add(new DocumentKey(DefinitionType.PHASE, value.id())));
		messageClosure.sourceKeys().stream()
			.filter(key -> switch (key.type()) {
				case GAME,
					ROLE,
					TEAM,
					LIFE_STATE,
					PHASE,
					FLOW,
					TASK,
					PLAYER_DATA,
					PLAYER_ACTION,
					TEXT_EFFECT,
					MESSAGE_CUE -> true;
				case FIELD,
					PANEL_ACTION,
					PLAYER_ROUTE,
					PAGE,
					THEME -> false;
			})
			.filter(key -> key.type() != DefinitionType.FLOW || !key.id().equals(flowId))
			.forEach(keys::add);
		List<DocumentKey> orderedKeys = keys.stream().sorted(
			Comparator.comparing((DocumentKey key) -> key.type().ordinal())
				.thenComparing(DocumentKey::id)
		).toList();

		List<FrozenSourceDocument> result = new ArrayList<>();
		for (DocumentKey key : orderedKeys) {
			SourceDocument document = definitions.sourceDocuments().get(key);
			if (document == null) {
				return null;
			}
			FrozenDocument frozenDocument = switch (key.type()) {
				case GAME -> frozenFlowGame(
					document,
					sourceAction.id(),
					messageClosure.playerTerminalGames().contains(key.id())
				);
				case ROLE -> frozenFlowRole(
					document,
					flowId,
					messageClosure.sourceKeys()
				);
				case FLOW -> frozenInitializationFlow(document);
				default -> frozen(document);
			};
			result.add(
				new FrozenSourceDocument(
					frozenDefinitionType(key.type()),
					key.id(),
					frozenDocument
				)
			);
		}
		SourceDocument actionDocument = definitions.sourceDocuments()
			.get(new DocumentKey(DefinitionType.PANEL_ACTION, sourceAction.id()));
		if (actionDocument == null) {
			return null;
		}
		result.add(
			new FrozenSourceDocument(
				FrozenDefinitionType.PANEL_ACTION,
				sourceAction.id(),
				frozenPanelAction(actionDocument)
			)
		);
		return List.copyOf(result);
	}

	private static FrozenDocument frozenPanelAction(final SourceDocument document) {
		JsonObject root = JsonParser.parseString(document.canonicalJson()).getAsJsonObject();
		JsonElement target = root.get("target");
		if (target != null && target.isJsonObject()) {
			JsonElement filter = target.getAsJsonObject().get("filter");
			if (filter != null && filter.isJsonObject()) {
				// ponytail: status_flows is presentation-only. Freezing its complete flow graph
				// would bloat every execution snapshot; active UI reads this hint from live definitions.
				filter.getAsJsonObject().remove("status_flows");
			}
		}
		return FrozenDocument.of(root.toString());
	}

	private static FrozenDocument frozenFlowGame(
		final SourceDocument document,
		final Identifier sourceActionId,
		final boolean retainPlayerTerminal
	) {
		JsonObject root = JsonParser.parseString(document.canonicalJson()).getAsJsonObject();
		// ponytail: a forced-flow snapshot owns one flow, not the task plan. Keep API v2 for
		// exclusive_choice, but leave the unrelated task DAG to TimelineSnapshotCompiler.
		root.remove("task_timeline");
		JsonElement readinessElement = root.get("readiness");
		if (readinessElement != null && readinessElement.isJsonObject()) {
			JsonObject readiness = readinessElement.getAsJsonObject();
			JsonElement action = readiness.get("action");
			// Only the readiness flow owns this nested hook definition. Its source action is
			// already frozen and validated against the owned flow; every other flow must drop
			// readiness entirely instead of importing unrelated lifecycle/control semantics.
			boolean ownsReadiness = action != null
				&& action.isJsonPrimitive()
				&& action.getAsJsonPrimitive().isString()
				&& sourceActionId.toString().equals(action.getAsString());
			if (!ownsReadiness) {
				// Ordinary forced flows must not acquire readiness lifecycle semantics.
				root.remove("readiness");
			}
		}
		// The ordinary player terminal is resolved from the live game definition. A forced-flow
		// snapshot does not freeze that terminal or its page graph, so retaining this entry would
		// leave default_page pointing at a page that intentionally is not part of the snapshot.
		if (!retainPlayerTerminal) {
			root.remove("player_terminal");
		}
		return FrozenDocument.of(root.toString());
	}

	private static FrozenDocument frozenFlowRole(
		final SourceDocument document,
		final Identifier flowId,
		final Set<DocumentKey> messageDependencies
	) {
		JsonObject root = JsonParser.parseString(document.canonicalJson()).getAsJsonObject();
		JsonElement initializationFlow = root.get("initialization_flow");
		if (
			initializationFlow != null
				&& (
					!initializationFlow.isJsonPrimitive()
						|| !initializationFlow.getAsJsonPrimitive().isString()
					|| (
						!initializationFlow.getAsString().equals(flowId.toString())
							&& !messageDependencies.contains(
								new DocumentKey(
									DefinitionType.FLOW,
									Identifier.parse(initializationFlow.getAsString())
								)
							)
					)
				)
		) {
			// ponytail: one execution snapshot owns one flow; unrelated role bootstrap flows stay live.
			root.remove("initialization_flow");
		}
		return FrozenDocument.of(root.toString());
	}

	/**
	 * Retains only the identity credential read by dynamic player data: flow id plus version.
	 * Initialization checks never execute this supporting graph; keeping its pages, functions and
	 * hooks would import an unrelated second forced-flow runtime into the owning snapshot.
	 */
	private static FrozenDocument frozenInitializationFlow(final SourceDocument document) {
		JsonObject source = JsonParser.parseString(document.canonicalJson()).getAsJsonObject();
		JsonObject root = new JsonObject();
		root.addProperty("format_version", source.get("format_version").getAsInt());
		root.addProperty("game", source.get("game").getAsString());
		root.addProperty("version", source.get("version").getAsInt());
		root.add("name", source.get("name").deepCopy());
		root.addProperty("entry", "dependency_complete");
		JsonObject audience = new JsonObject();
		audience.addProperty("exclude_host", false);
		audience.addProperty("online_only", false);
		root.add("audience", audience);
		root.addProperty("required", false);
		com.google.gson.JsonArray nodes = new com.google.gson.JsonArray();
		JsonObject complete = new JsonObject();
		complete.addProperty("id", "dependency_complete");
		complete.addProperty("type", "complete");
		nodes.add(complete);
		root.add("nodes", nodes);
		return FrozenDocument.of(root.toString());
	}

	private static String validatePageContract(
		final FlowNode node,
		final PageDefinition page
	) {
		List<UiAction> actions = new ArrayList<>();
		Set<Identifier> fields = new LinkedHashSet<>();
		collectPageContract(page.root(), actions, fields);
		if (node instanceof ConfirmNode confirm) {
			boolean confirms = actions.stream().anyMatch(
				action -> action.type() == UiDefinitions.ActionType.FLOW
					&& action.name().filter("confirm"::equals).isPresent()
			);
			if (!confirms) {
				return "confirm node page requires a flow action named confirm";
			}
			boolean returns = confirm.back().isEmpty()
				|| actions.stream().anyMatch(
					action -> action.type() == UiDefinitions.ActionType.FLOW
						&& action.name().filter("back"::equals).isPresent()
				);
			return returns
				? null
				: "confirm node with back requires a flow action named back";
		}
		if (node instanceof ChoiceNode choice) {
			if (!fields.contains(choice.field())) {
				return "choice node page does not expose its routed field " + choice.field();
			}
			return actions.stream().anyMatch(
				action -> action.type() == UiDefinitions.ActionType.FLOW
					&& action.name().filter("submit"::equals).isPresent()
			)
				? null
				: "choice node page requires a flow action named submit";
		}
		return actions.stream().anyMatch(action -> action.type() == UiDefinitions.ActionType.FLOW)
			? null
			: "page node requires at least one flow action";
	}

	private static void collectPageContract(
		final NodeDefinition node,
		final List<UiAction> actions,
		final Set<Identifier> fields
	) {
		switch (node.content()) {
			case ButtonContent button -> actions.add(button.action());
			case FieldInputContent field -> fields.add(field.field());
			case ChildrenContent children -> children.children()
				.forEach(child -> collectPageContract(child, actions, fields));
			case SingleChildContent single -> collectPageContract(single.child(), actions, fields);
			case RepeatContent repeat -> collectPageContract(repeat.template(), actions, fields);
			default -> {
			}
		}
	}

	private static void collectFieldIds(
		final NodeDefinition node,
		final Set<Identifier> fields
	) {
		switch (node.content()) {
			case FieldInputContent field -> fields.add(field.field());
			case ChildrenContent children -> children.children()
				.forEach(child -> collectFieldIds(child, fields));
			case SingleChildContent single -> collectFieldIds(single.child(), fields);
			case RepeatContent repeat -> collectFieldIds(repeat.template(), fields);
			default -> {
			}
		}
	}

	private static String unsupportedReason(final FlowNode node) {
		return switch (node) {
			case PageNode ignored -> null;
			case ConfirmNode ignored -> null;
			case ChoiceNode ignored -> null;
			case BranchNode branch -> branch.scope() == NodeScope.PLAYER
				? null
				: "event-scope branch is not executable in milestone 2C";
			case CompleteNode complete -> complete.scope() == NodeScope.PLAYER
				? null
				: "event-scope complete is not executable in milestone 2C";
			case FunctionNode ignored -> "function node is not executable in milestone 2C";
			case WaitPlayersNode ignored -> "wait_players node is not executable in milestone 2C";
			case ChangeStateNode ignored -> "change_state node is not executable in milestone 2C";
		};
	}

	private static FrozenDocument frozen(final SourceDocument document) {
		return new FrozenDocument(document.canonicalJson(), document.sha256());
	}

	private static Source source(
		final DefinitionType type,
		final Identifier id,
		final FrozenDocument document
	) {
		return new Source(
			type,
			id,
			Identifier.fromNamespaceAndPath(
				id.getNamespace(),
				"pixel_tzz_pro/frozen/" + type.directory() + "/" + id.getPath() + ".json"
			),
			"pixel-tzz-pro:frozen",
			document.normalizedJson()
		);
	}

	private static FrozenDefinitionType frozenDefinitionType(final DefinitionType type) {
		return switch (type) {
			case GAME -> FrozenDefinitionType.GAME;
			case ROLE -> FrozenDefinitionType.ROLE;
			case TEAM -> FrozenDefinitionType.TEAM;
			case LIFE_STATE -> FrozenDefinitionType.LIFE_STATE;
			case PHASE -> FrozenDefinitionType.PHASE;
			case TASK -> FrozenDefinitionType.TASK;
			case FLOW -> FrozenDefinitionType.FLOW;
			case PANEL_ACTION -> FrozenDefinitionType.PANEL_ACTION;
			case PLAYER_DATA -> FrozenDefinitionType.PLAYER_DATA;
			case PLAYER_ACTION -> FrozenDefinitionType.PLAYER_ACTION;
			case TEXT_EFFECT -> FrozenDefinitionType.TEXT_EFFECT;
			case MESSAGE_CUE -> FrozenDefinitionType.MESSAGE_CUE;
			case FIELD,
				PLAYER_ROUTE,
				PAGE,
				THEME -> throw new IllegalArgumentException(
				"type is not a supporting definition: " + type
			);
		};
	}

	private static DefinitionType definitionType(final FrozenDefinitionType type) {
		return switch (type) {
			case GAME -> DefinitionType.GAME;
			case ROLE -> DefinitionType.ROLE;
			case TEAM -> DefinitionType.TEAM;
			case LIFE_STATE -> DefinitionType.LIFE_STATE;
			case PHASE -> DefinitionType.PHASE;
			case TASK -> DefinitionType.TASK;
			case FLOW -> DefinitionType.FLOW;
			case PANEL_ACTION -> DefinitionType.PANEL_ACTION;
			case PLAYER_DATA -> DefinitionType.PLAYER_DATA;
			case PLAYER_ACTION -> DefinitionType.PLAYER_ACTION;
			case TEXT_EFFECT -> DefinitionType.TEXT_EFFECT;
			case MESSAGE_CUE -> DefinitionType.MESSAGE_CUE;
		};
	}

	private static String message(final RuntimeException error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}

	public record FreezeResult(
		Optional<ExecutionSnapshot> frozen,
		Optional<DefinitionSnapshot> compiled,
		String code,
		String message,
		String nodeId
	) {
		public FreezeResult {
			frozen = Objects.requireNonNull(frozen, "frozen");
			compiled = Objects.requireNonNull(compiled, "compiled");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
		}

		public boolean success() {
			return frozen.isPresent() && compiled.isPresent();
		}

		private static FreezeResult success(
			final ExecutionSnapshot frozen,
			final DefinitionSnapshot compiled
		) {
			return new FreezeResult(
				Optional.of(frozen),
				Optional.of(compiled),
				"success",
				"",
				""
			);
		}

		private static FreezeResult rejected(
			final String code,
			final String message,
			final String nodeId
		) {
			return new FreezeResult(
				Optional.empty(),
				Optional.empty(),
				code,
				message,
				nodeId
			);
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
			return snapshot.isPresent();
		}

		private static RestoreResult success(final DefinitionSnapshot snapshot) {
			return new RestoreResult(Optional.of(snapshot), "success", "");
		}

		private static RestoreResult rejected(final String code, final String message) {
			return new RestoreResult(Optional.empty(), code, message);
		}
	}
}
