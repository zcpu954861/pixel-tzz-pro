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
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownCallback;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookFreezeClosure.Closure;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Freezes one dedicated opening countdown and the complete approved 2D timeline.
 *
 * <p>The persisted envelope is self-contained. Restore never consults the active registry and
 * therefore cannot silently combine an old countdown instance with a new reload generation.
 */
public final class CountdownSnapshotCompiler {
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_EXTERNAL_REFERENCES = 16_384;
	private static final Set<String> ROOT_KEYS = Set.of(
		"format_version",
		"definition_generation",
		"game",
		"countdown",
		"timeline",
		"sources",
		"functions",
		"predicate_documents"
	);
	private static final Set<String> SOURCE_KEYS = Set.of("type", "id", "document");
	private static final Set<String> TIMELINE_ROOT_KEYS = Set.of(
		"format_version",
		"definition_generation",
		"game",
		"sources",
		"functions",
		"predicates",
		"predicate_documents"
	);
	private static final Set<String> TIMELINE_SOURCE_KEYS = Set.of(
		"type",
		"id",
		"document",
		"document_json"
	);

	private CountdownSnapshotCompiler() {
	}

	public static FreezeResult freeze(
		final DefinitionSnapshot definitions,
		final GameDefinition game,
		final CountdownDefinition countdown
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(game, "game");
		Objects.requireNonNull(countdown, "countdown");
		try {
			if (definitions.generation() < 0L) {
				return FreezeResult.rejected("snapshot_invalid", "definition generation is negative");
			}
			if (!game.equals(definitions.games().get(game.id()))) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"game is not registered in the supplied definition generation"
				);
			}
			if (!countdown.equals(definitions.countdownCatalog().countdowns().get(countdown.id()))) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"countdown is not the usable definition in the supplied generation"
				);
			}
			if (
				game.openingCountdown().isEmpty()
					|| !game.openingCountdown().orElseThrow().definition().equals(countdown.id())
			) {
				return FreezeResult.rejected(
					"snapshot_invalid",
					"game does not reference the supplied opening countdown"
				);
			}

			TimelineSnapshotCompiler.FreezeResult timeline = TimelineSnapshotCompiler.freeze(
				definitions,
				game
			);
			if (!timeline.success()) {
				return FreezeResult.rejected(timeline.code(), timeline.message());
			}
			FrozenDocument timelineDocument = timeline.frozen().orElseThrow();
			JsonElement timelineJson = JsonParser.parseString(timelineDocument.normalizedJson());
			if (!timelineJson.isJsonObject()) {
				return FreezeResult.rejected("snapshot_invalid", "timeline snapshot is not an object");
			}

			DependencyClosure closure = collectClosure(definitions, countdown);
			if (closure.sourceKeys().size() > DefinitionCompiler.MAX_DEFINITIONS) {
				return FreezeResult.rejected(
					"snapshot_too_large",
					"countdown dependency count exceeds " + DefinitionCompiler.MAX_DEFINITIONS
				);
			}
			if (
				closure.functions().size() > MAX_EXTERNAL_REFERENCES
					|| closure.predicates().size() > MAX_EXTERNAL_REFERENCES
			) {
				return FreezeResult.rejected(
					"snapshot_too_large",
					"countdown external reference count exceeds " + MAX_EXTERNAL_REFERENCES
				);
			}

			JsonObject root = new JsonObject();
			root.addProperty("format_version", FORMAT_VERSION);
			root.addProperty("definition_generation", definitions.generation());
			root.addProperty("game", game.id().toString());
			root.addProperty("countdown", countdown.id().toString());
			root.add("timeline", timelineJson);
			JsonArray sources = new JsonArray();
			for (DocumentKey key : closure.sourceKeys().stream()
				.sorted(keyComparator())
				.toList()) {
				SourceDocument document = definitions.sourceDocuments().get(key);
				if (document == null) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"countdown dependency has no retained source document: " + key
					);
				}
				JsonElement parsed = JsonParser.parseString(document.canonicalJson());
				if (!parsed.isJsonObject()) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"countdown dependency source is not an object: " + key
					);
				}
				JsonObject source = new JsonObject();
				source.addProperty("type", key.type().directory());
				source.addProperty("id", key.id().toString());
				source.add("document", parsed);
				sources.add(source);
			}
			root.add("sources", sources);
			root.add("functions", identifiers(closure.functions()));
			JsonObject predicates = new JsonObject();
			for (Identifier predicate : closure.predicates().stream().sorted().toList()) {
				String document = definitions.predicateDocuments().get(predicate);
				if (document == null) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"countdown predicate has no retained document: " + predicate
					);
				}
				JsonElement parsed = JsonParser.parseString(document);
				if (!parsed.isJsonObject()) {
					return FreezeResult.rejected(
						"snapshot_invalid",
						"countdown predicate is not an object: " + predicate
					);
				}
				predicates.add(predicate.toString(), parsed);
			}
			root.add("predicate_documents", predicates);

			String normalized = root.toString();
			if (
				normalized.getBytes(StandardCharsets.UTF_8).length
					> WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
			) {
				return FreezeResult.rejected(
					"snapshot_too_large",
					"countdown snapshot exceeds " + WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES + " UTF-8 bytes"
				);
			}
			FrozenDocument frozen = FrozenDocument.of(normalized);
			RestoreResult restored = restore(game.id(), countdown.id(), frozen);
			if (!restored.success()) {
				return FreezeResult.rejected(restored.code(), restored.message());
			}
			return FreezeResult.success(frozen, restored.restored().orElseThrow());
		} catch (RuntimeException | StackOverflowError error) {
			return FreezeResult.rejected("snapshot_invalid", message(error));
		}
	}

	public static RestoreResult restore(
		final Identifier expectedGame,
		final Identifier expectedCountdown,
		final FrozenDocument frozen
	) {
		Objects.requireNonNull(expectedGame, "expectedGame");
		Objects.requireNonNull(expectedCountdown, "expectedCountdown");
		Objects.requireNonNull(frozen, "frozen");
		try {
			if (!FrozenDocument.of(frozen.normalizedJson()).sha256().equals(frozen.sha256())) {
				return RestoreResult.rejected("snapshot_invalid", "countdown snapshot hash mismatch");
			}
			if (
				frozen.normalizedJson().getBytes(StandardCharsets.UTF_8).length
					> WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
			) {
				return RestoreResult.rejected(
					"snapshot_too_large",
					"countdown snapshot exceeds persistence limit"
				);
			}
			JsonObject root = requiredObject(JsonParser.parseString(frozen.normalizedJson()), "root");
			requireKnownKeys(root, ROOT_KEYS, "root");
			if (requiredInt(root, "format_version") != FORMAT_VERSION) {
				return RestoreResult.rejected("snapshot_invalid", "unsupported countdown snapshot format");
			}
			long generation = requiredLong(root, "definition_generation");
			if (generation < 0L) {
				return RestoreResult.rejected("snapshot_invalid", "definition generation is negative");
			}
			Identifier gameId = requiredIdentifier(root, "game");
			Identifier countdownId = requiredIdentifier(root, "countdown");
			if (!gameId.equals(expectedGame) || !countdownId.equals(expectedCountdown)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"countdown snapshot identity does not match the persisted instance"
				);
			}

			JsonObject timelineRoot = requiredObject(root.get("timeline"), "timeline");
			requireKnownKeys(timelineRoot, TIMELINE_ROOT_KEYS, "timeline");
			FrozenDocument timelineDocument = FrozenDocument.of(timelineRoot.toString());
			TimelineSnapshotCompiler.RestoreResult timeline = TimelineSnapshotCompiler.restore(
				expectedGame,
				timelineDocument
			);
			if (!timeline.success()) {
				return RestoreResult.rejected(timeline.code(), timeline.message());
			}

			Map<DocumentKey, Source> sources = new LinkedHashMap<>();
			readTimelineSources(timelineRoot, sources);
			Set<DocumentKey> outerKeys = readSources(requiredArray(root, "sources"), sources);
			Set<Identifier> functions = identifierSet(
				requiredArray(timelineRoot, "functions"),
				"timeline function"
			);
			Set<Identifier> outerFunctions = identifierSet(
				requiredArray(root, "functions"),
				"countdown function"
			);
			Set<Identifier> allFunctions = new LinkedHashSet<>(functions);
			allFunctions.addAll(outerFunctions);

			Map<Identifier, String> predicateDocuments = predicateDocuments(
				requiredObject(timelineRoot.get("predicate_documents"), "timeline predicate_documents")
			);
			Map<Identifier, String> outerPredicateDocuments = predicateDocuments(
				requiredObject(root.get("predicate_documents"), "predicate_documents")
			);
			for (Map.Entry<Identifier, String> entry : outerPredicateDocuments.entrySet()) {
				String previous = predicateDocuments.putIfAbsent(entry.getKey(), entry.getValue());
				if (previous != null && !previous.equals(entry.getValue())) {
					return RestoreResult.rejected(
						"snapshot_invalid",
						"predicate document differs between timeline and countdown closure: " + entry.getKey()
					);
				}
			}
			Set<Identifier> predicates = identifierSet(
				requiredArray(timelineRoot, "predicates"),
				"timeline predicate"
			);
			predicates = new LinkedHashSet<>(predicates);
			predicates.addAll(outerPredicateDocuments.keySet());

			Compilation compilation = DefinitionCompiler.compile(
				sources.values().stream().sorted(sourceComparator()).toList(),
				allFunctions,
				predicates
			);
			if (!compilation.valid()) {
				String diagnostic = compilation.problems().isEmpty()
					? "frozen countdown definitions could not be compiled"
					: compilation.problems().getFirst().summary();
				return RestoreResult.rejected("snapshot_invalid", diagnostic);
			}
			DefinitionSnapshot restored = compilation.snapshot()
				.orElseThrow()
				.withPredicateDocuments(predicateDocuments)
				.withGeneration(generation);
			if (restored.games().size() != 1 || !restored.games().containsKey(expectedGame)) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"countdown snapshot must restore exactly its owning game"
				);
			}
			CountdownDefinition countdown = restored.countdownCatalog().countdowns().get(expectedCountdown);
			if (countdown == null) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"frozen countdown is missing or invalid"
				);
			}
			GameDefinition game = restored.games().get(expectedGame);
			if (
				game.openingCountdown().isEmpty()
					|| !game.openingCountdown().orElseThrow().definition().equals(expectedCountdown)
			) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"frozen game no longer references the countdown"
				);
			}

			DependencyClosure expected = collectClosure(restored, countdown);
			if (!outerKeys.equals(expected.sourceKeys())) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"countdown source closure does not match the restored definition"
				);
			}
			if (!outerFunctions.equals(expected.functions())) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"countdown function closure does not match the restored definition"
				);
			}
			if (!outerPredicateDocuments.keySet().equals(expected.predicates())) {
				return RestoreResult.rejected(
					"snapshot_invalid",
					"countdown predicate closure does not match the restored definition"
				);
			}
			return RestoreResult.success(new RestoredCountdown(
				restored,
				game,
				countdown,
				timelineDocument,
				timeline.snapshot().orElseThrow()
			));
		} catch (RuntimeException | StackOverflowError error) {
			return RestoreResult.rejected("snapshot_invalid", message(error));
		}
	}

	private static DependencyClosure collectClosure(
		final DefinitionSnapshot definitions,
		final CountdownDefinition countdown
	) {
		Set<DocumentKey> keys = new LinkedHashSet<>();
		Set<Identifier> functions = new LinkedHashSet<>();
		Set<Identifier> predicates = new LinkedHashSet<>();
		keys.add(new DocumentKey(DefinitionType.COUNTDOWN, countdown.id()));

		for (List<CountdownCallback> callbacks : countdown.callbacks().values()) {
			callbacks.forEach(callback -> functions.add(callback.function()));
		}
		for (Identifier cueId : countdown.checkpoints().stream()
			.map(CountdownDefinitions.CountdownCheckpoint::cue)
			.flatMap(Optional::stream)
			.distinct()
			.sorted()
			.toList()) {
			var cue = definitions.messageCatalog().messageCues().get(cueId);
			if (cue == null) {
				throw new IllegalArgumentException("countdown checkpoint cue is missing " + cueId);
			}
			Closure cueClosure = MessageHookFreezeClosure.collectCue(definitions, cue);
			keys.addAll(cueClosure.sourceKeys());
			functions.addAll(cueClosure.functions());
			predicates.addAll(cueClosure.predicates());
		}
		return new DependencyClosure(Set.copyOf(keys), Set.copyOf(functions), Set.copyOf(predicates));
	}

	private static void readTimelineSources(
		final JsonObject timeline,
		final Map<DocumentKey, Source> result
	) {
		JsonArray values = requiredArray(timeline, "sources");
		if (values.size() > DefinitionCompiler.MAX_DEFINITIONS) {
			throw new IllegalArgumentException("timeline source count exceeds compiler limit");
		}
		for (JsonElement element : values) {
			JsonObject source = requiredObject(element, "timeline source");
			requireKnownKeys(source, TIMELINE_SOURCE_KEYS, "timeline source");
			DefinitionType type = DefinitionType.byDirectory(requiredString(source, "type"))
				.orElseThrow(() -> new IllegalArgumentException("unknown timeline source type"));
			Identifier id = requiredIdentifier(source, "id");
			JsonElement document = source.get("document");
			String json;
			if (document != null) {
				if (!document.isJsonObject()) {
					throw new IllegalArgumentException("timeline source document must be an object");
				}
				json = document.toString();
			} else {
				json = requiredString(source, "document_json");
			}
			putSource(result, type, id, json);
		}
	}

	private static Set<DocumentKey> readSources(
		final JsonArray values,
		final Map<DocumentKey, Source> result
	) {
		if (values.size() > DefinitionCompiler.MAX_DEFINITIONS) {
			throw new IllegalArgumentException("countdown source count exceeds compiler limit");
		}
		Set<DocumentKey> keys = new LinkedHashSet<>();
		for (JsonElement element : values) {
			JsonObject source = requiredObject(element, "countdown source");
			requireKnownKeys(source, SOURCE_KEYS, "countdown source");
			DefinitionType type = DefinitionType.byDirectory(requiredString(source, "type"))
				.orElseThrow(() -> new IllegalArgumentException("unknown countdown source type"));
			Identifier id = requiredIdentifier(source, "id");
			JsonObject document = requiredObject(source.get("document"), "countdown source document");
			DocumentKey key = new DocumentKey(type, id);
			if (!keys.add(key)) {
				throw new IllegalArgumentException("duplicate countdown source key " + key);
			}
			putSource(result, type, id, document.toString());
		}
		return Set.copyOf(keys);
	}

	private static void putSource(
		final Map<DocumentKey, Source> result,
		final DefinitionType type,
		final Identifier id,
		final String json
	) {
		DocumentKey key = new DocumentKey(type, id);
		Source source = frozenSource(type, id, json);
		Source previous = result.putIfAbsent(key, source);
		if (previous != null && !previous.json().equals(json)) {
			throw new IllegalArgumentException("frozen source differs across closures: " + key);
		}
	}

	private static Source frozenSource(
		final DefinitionType type,
		final Identifier id,
		final String json
	) {
		Identifier resource = Identifier.fromNamespaceAndPath(
			id.getNamespace(),
			"pixel_tzz_pro/" + type.directory() + "/" + id.getPath() + ".json"
		);
		return new Source(type, id, resource, "frozen_countdown", json);
	}

	private static Map<Identifier, String> predicateDocuments(final JsonObject values) {
		if (values.size() > MAX_EXTERNAL_REFERENCES) {
			throw new IllegalArgumentException("predicate document count exceeds limit");
		}
		Map<Identifier, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null || result.putIfAbsent(id, requiredObject(entry.getValue(), "predicate").toString()) != null) {
				throw new IllegalArgumentException("invalid or duplicate predicate document identifier");
			}
		}
		return result;
	}

	private static JsonArray identifiers(final Set<Identifier> values) {
		JsonArray result = new JsonArray();
		values.stream().sorted().map(Identifier::toString).forEach(result::add);
		return result;
	}

	private static Set<Identifier> identifierSet(final JsonArray values, final String label) {
		if (values.size() > MAX_EXTERNAL_REFERENCES) {
			throw new IllegalArgumentException(label + " count exceeds limit");
		}
		Set<Identifier> result = new LinkedHashSet<>();
		for (JsonElement element : values) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException(label + " must be a string");
			}
			Identifier id = Identifier.tryParse(element.getAsString());
			if (id == null || !result.add(id)) {
				throw new IllegalArgumentException("invalid or duplicate " + label);
			}
		}
		return Set.copyOf(result);
	}

	private static Comparator<DocumentKey> keyComparator() {
		return Comparator.comparing((DocumentKey key) -> key.type().ordinal())
			.thenComparing(key -> key.id().toString());
	}

	private static Comparator<Source> sourceComparator() {
		return Comparator.comparing((Source source) -> source.type().ordinal())
			.thenComparing(source -> source.id().toString());
	}

	private static JsonArray requiredArray(final JsonObject object, final String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonArray()) {
			throw new IllegalArgumentException(key + " must be an array");
		}
		return value.getAsJsonArray();
	}

	private static JsonObject requiredObject(final JsonElement value, final String label) {
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(label + " must be an object");
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
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
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
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(key + " must be an integer");
		}
		try {
			return value.getAsBigDecimal().longValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(key + " must be an integer", error);
		}
	}

	private static Identifier requiredIdentifier(final JsonObject object, final String key) {
		String value = requiredString(object, key);
		if (!value.contains(":")) {
			throw new IllegalArgumentException(key + " must use an explicit namespace");
		}
		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			throw new IllegalArgumentException(key + " must be an identifier");
		}
		return id;
	}

	private static void requireKnownKeys(
		final JsonObject object,
		final Set<String> allowed,
		final String label
	) {
		String unknown = object.keySet().stream()
			.filter(key -> !allowed.contains(key))
			.sorted()
			.findFirst()
			.orElse(null);
		if (unknown != null) {
			throw new IllegalArgumentException(label + " contains unknown key " + unknown);
		}
	}

	private static String message(final Throwable error) {
		String value = error.getMessage();
		return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
	}

	private record DependencyClosure(
		Set<DocumentKey> sourceKeys,
		Set<Identifier> functions,
		Set<Identifier> predicates
	) {
		private DependencyClosure {
			sourceKeys = Set.copyOf(sourceKeys);
			functions = Set.copyOf(functions);
			predicates = Set.copyOf(predicates);
		}
	}

	public record RestoredCountdown(
		DefinitionSnapshot definitions,
		GameDefinition game,
		CountdownDefinition countdown,
		FrozenDocument timelineDocument,
		DefinitionSnapshot timelineSnapshot
	) {
		public RestoredCountdown {
			definitions = Objects.requireNonNull(definitions, "definitions");
			game = Objects.requireNonNull(game, "game");
			countdown = Objects.requireNonNull(countdown, "countdown");
			timelineDocument = Objects.requireNonNull(timelineDocument, "timelineDocument");
			timelineSnapshot = Objects.requireNonNull(timelineSnapshot, "timelineSnapshot");
		}
	}

	public record FreezeResult(
		Optional<FrozenDocument> frozen,
		Optional<RestoredCountdown> restored,
		String code,
		String message
	) {
		public FreezeResult {
			frozen = Objects.requireNonNull(frozen, "frozen");
			restored = Objects.requireNonNull(restored, "restored");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean success() {
			return this.frozen.isPresent() && this.restored.isPresent();
		}

		private static FreezeResult success(
			final FrozenDocument frozen,
			final RestoredCountdown restored
		) {
			return new FreezeResult(
				Optional.of(frozen),
				Optional.of(restored),
				"success",
				""
			);
		}

		private static FreezeResult rejected(final String code, final String message) {
			return new FreezeResult(Optional.empty(), Optional.empty(), code, message);
		}
	}

	public record RestoreResult(
		Optional<RestoredCountdown> restored,
		String code,
		String message
	) {
		public RestoreResult {
			restored = Objects.requireNonNull(restored, "restored");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
		}

		public boolean success() {
			return this.restored.isPresent();
		}

		private static RestoreResult success(final RestoredCountdown restored) {
			return new RestoreResult(Optional.of(restored), "success", "");
		}

		private static RestoreResult rejected(final String code, final String message) {
			return new RestoreResult(Optional.empty(), code, message);
		}
	}
}
