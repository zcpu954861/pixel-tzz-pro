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
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
	private static final Set<String> ROOT_KEYS = Set.of(
		"format_version",
		"definition_generation",
		"game",
		"sources",
		"functions",
		"predicates"
	);
	private static final Set<String> SOURCE_KEYS = Set.of("type", "id", "document");

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

		List<DocumentKey> keys = supportingKeys(definitions, game.id(), reachable);
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
				source.add("document", JsonParser.parseString(document.canonicalJson()));
			} catch (RuntimeException error) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"canonical source document is invalid JSON: " + key.id()
				);
			}
			sources.add(source);
		}
		root.add("sources", sources);
		if (
			definitions.functions().size() > MAX_EXTERNAL_REFERENCES
				|| definitions.predicates().size() > MAX_EXTERNAL_REFERENCES
		) {
			return FreezeResult.rejected(
				"snapshot_too_large",
				"external reference count exceeds " + MAX_EXTERNAL_REFERENCES
			);
		}
		root.add("functions", identifiers(definitions.functions()));
		root.add("predicates", identifiers(definitions.predicates()));

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
				if (document == null || !document.isJsonObject()) {
					throw new IllegalArgumentException("source document must be an object");
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
						document.toString()
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
			if (!restored.tasks().keySet().equals(reachable)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"timeline snapshot contains tasks outside its reachable plan"
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

	private static List<DocumentKey> supportingKeys(
		final DefinitionSnapshot definitions,
		final Identifier gameId,
		final Set<Identifier> reachableTasks
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
