package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.nio.charset.StandardCharsets;
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
 * Produces the bounded semantic dependency manifest used by persistent V3B message instances.
 *
 * <p>The whole registry is intentionally not fingerprinted. Forced-flow and timeline snapshots
 * prune unrelated game and role data, so comparing their complete source set with the registry
 * rebuilt after a JVM restart rejects an unchanged data pack. This manifest stores the exact key
 * set reached by one cue and normalizes the few definitions that the message resolver reads only
 * partially. On cold start, the saved key set is projected from the newly accepted registry and
 * compared byte-for-byte; extra unrelated definitions do not matter, while a changed cue,
 * effect, predicate, callback identity, task/personal definition, identity tag/name, field
 * version, or initialization credential fails closed.</p>
 */
public final class MessageDependencySnapshotCompiler {
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_EXTERNAL_REFERENCES = 16_384;
	private static final Set<String> ROOT_KEYS = Set.of(
		"format_version",
		"cue_id",
		"cue_document",
		"sources",
		"functions",
		"predicate_documents"
	);
	private static final Set<String> SOURCE_KEYS = Set.of("type", "id", "projection");

	private MessageDependencySnapshotCompiler() {
	}

	public static FreezeResult freeze(
		final DefinitionSnapshot definitions,
		final MessageCueDefinition cue
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(cue, "cue");
		try {
			MessageHookFreezeClosure.Closure closure = MessageHookFreezeClosure.collectCue(
				definitions,
				cue
			);
			List<DocumentKey> ordered = closure.sourceKeys().stream()
				.sorted(
					Comparator.comparing((DocumentKey key) -> key.type().ordinal())
						.thenComparing(key -> key.id().toString())
				)
				.toList();
			if (ordered.size() > DefinitionCompiler.MAX_DEFINITIONS) {
				return FreezeResult.rejected("dependency count exceeds compiler limit");
			}
			if (
				closure.functions().size() > MAX_EXTERNAL_REFERENCES
					|| closure.predicates().size() > MAX_EXTERNAL_REFERENCES
			) {
				return FreezeResult.rejected("external dependency count exceeds limit");
			}

			Set<DocumentKey> keys = Set.copyOf(ordered);
			JsonObject root = new JsonObject();
			root.addProperty("format_version", FORMAT_VERSION);
			root.addProperty("cue_id", cue.id().toString());
			root.addProperty("cue_document", cue.canonicalDocument());
			JsonArray sources = new JsonArray();
			for (DocumentKey key : ordered) {
				JsonObject source = new JsonObject();
				source.addProperty("type", key.type().directory());
				source.addProperty("id", key.id().toString());
				source.add("projection", project(definitions, key, keys));
				sources.add(source);
			}
			root.add("sources", sources);

			JsonArray functions = new JsonArray();
			closure.functions().stream().map(Identifier::toString).sorted()
				.forEach(functions::add);
			root.add("functions", functions);
			JsonObject predicates = new JsonObject();
			for (Identifier predicate : closure.predicates().stream().sorted().toList()) {
				String document = definitions.predicateDocuments().get(predicate);
				if (document == null) {
					return FreezeResult.rejected(
						"predicate has no retained document: " + predicate
					);
				}
				predicates.addProperty(predicate.toString(), document);
			}
			root.add("predicate_documents", predicates);
			String canonical = root.toString();
			if (
				canonical.getBytes(StandardCharsets.UTF_8).length
					> WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
			) {
				return FreezeResult.rejected("dependency snapshot exceeds persistence limit");
			}
			return FreezeResult.success(FrozenDocument.of(canonical));
		} catch (RuntimeException error) {
			return FreezeResult.rejected(message(error));
		}
	}

	/** Compares one persisted manifest with the currently accepted registry generation. */
	public static MatchResult matches(
		final DefinitionSnapshot active,
		final Identifier expectedCueId,
		final FrozenDocument frozen
	) {
		return matches(active, expectedCueId, frozen, Optional.empty());
	}

	/**
	 * Compares a persisted manifest and also proves that its embedded cue document belongs to the
	 * same frozen recovery unit. The manifest and {@code FrozenCue}-style
	 * cue document are persisted as separate bounded documents, so recovery must reject a corrupted
	 * or incorrectly assembled pair instead of validating one cue and playing the other.
	 */
	public static MatchResult matches(
		final DefinitionSnapshot active,
		final Identifier expectedCueId,
		final FrozenDocument frozen,
		final FrozenDocument expectedCueDocument
	) {
		Objects.requireNonNull(expectedCueDocument, "expectedCueDocument");
		return matches(
			active,
			expectedCueId,
			frozen,
			Optional.of(expectedCueDocument.normalizedJson())
		);
	}

	private static MatchResult matches(
		final DefinitionSnapshot active,
		final Identifier expectedCueId,
		final FrozenDocument frozen,
		final Optional<String> expectedCueDocument
	) {
		Objects.requireNonNull(active, "active");
		Objects.requireNonNull(expectedCueId, "expectedCueId");
		Objects.requireNonNull(frozen, "frozen");
		Objects.requireNonNull(expectedCueDocument, "expectedCueDocument");
		try {
			if (!FrozenDocument.of(frozen.normalizedJson()).sha256().equals(frozen.sha256())) {
				return MatchResult.rejected("dependency snapshot hash mismatch");
			}
			if (
				frozen.normalizedJson().getBytes(StandardCharsets.UTF_8).length
					> WorldStateV2.MAX_SNAPSHOT_UTF8_BYTES
			) {
				return MatchResult.rejected("dependency snapshot exceeds persistence limit");
			}
			JsonObject root = requireObject(JsonParser.parseString(frozen.normalizedJson()), "root");
			requireKnownKeys(root, ROOT_KEYS, "root");
			if (requireInt(root, "format_version") != FORMAT_VERSION) {
				return MatchResult.rejected("unsupported dependency snapshot format");
			}
			Identifier cueId = requireIdentifier(root, "cue_id");
			if (!cueId.equals(expectedCueId)) {
				return MatchResult.rejected("dependency snapshot cue id mismatch");
			}
			String manifestCueDocument = requireString(root, "cue_document");
			if (
				expectedCueDocument.isPresent()
					&& !expectedCueDocument.orElseThrow().equals(manifestCueDocument)
			) {
				return MatchResult.rejected("frozen cue document does not match dependency snapshot");
			}
			MessageCueDefinition activeCue = active.messageCatalog().messageCues().get(cueId);
			if (
				activeCue == null
					|| !activeCue.canonicalDocument().equals(manifestCueDocument)
			) {
				return MatchResult.rejected("cue definition changed or is unavailable");
			}

			JsonArray sources = requireArray(root, "sources");
			if (sources.size() > DefinitionCompiler.MAX_DEFINITIONS) {
				return MatchResult.rejected("dependency count exceeds compiler limit");
			}
			Map<DocumentKey, JsonElement> expected = new LinkedHashMap<>();
			for (JsonElement element : sources) {
				JsonObject source = requireObject(element, "source");
				requireKnownKeys(source, SOURCE_KEYS, "source");
				DefinitionType type = DefinitionType.byDirectory(requireString(source, "type"))
					.orElseThrow(() -> new IllegalArgumentException("unknown dependency type"));
				DocumentKey key = new DocumentKey(type, requireIdentifier(source, "id"));
				if (expected.putIfAbsent(key, require(source, "projection")) != null) {
					return MatchResult.rejected("duplicate dependency key " + key);
				}
			}
			Set<DocumentKey> keys = Set.copyOf(expected.keySet());
			MessageHookFreezeClosure.Closure activeClosure =
				MessageHookFreezeClosure.collectCue(active, activeCue);
			if (!activeClosure.sourceKeys().equals(keys)) {
				return MatchResult.rejected("definition dependency closure changed");
			}
			for (Map.Entry<DocumentKey, JsonElement> entry : expected.entrySet()) {
				JsonElement current = project(active, entry.getKey(), keys);
				if (!current.equals(entry.getValue())) {
					return MatchResult.rejected(
						"definition dependency changed: "
							+ entry.getKey().type().directory()
							+ "/"
							+ entry.getKey().id()
					);
				}
			}

			JsonArray functions = requireArray(root, "functions");
			if (functions.size() > MAX_EXTERNAL_REFERENCES) {
				return MatchResult.rejected("function dependency count exceeds limit");
			}
			Set<Identifier> functionIds = new LinkedHashSet<>();
			for (JsonElement element : functions) {
				Identifier function = parseIdentifier(
					requireString(element, "function"),
					"function"
				);
				if (!functionIds.add(function)) {
					return MatchResult.rejected("duplicate function dependency " + function);
				}
			}
			if (!activeClosure.functions().equals(functionIds)) {
				return MatchResult.rejected("callback function dependency closure changed");
			}

			JsonObject predicates = requireObject(require(root, "predicate_documents"), "predicates");
			if (predicates.size() > MAX_EXTERNAL_REFERENCES) {
				return MatchResult.rejected("predicate dependency count exceeds limit");
			}
			for (Map.Entry<String, JsonElement> entry : predicates.entrySet()) {
				Identifier predicate = parseIdentifier(entry.getKey(), "predicate");
				if (
					!entry.getValue().isJsonPrimitive()
						|| !entry.getValue().getAsJsonPrimitive().isString()
						|| !entry.getValue().getAsString().equals(
							active.predicateDocuments().get(predicate)
						)
				) {
					return MatchResult.rejected(
						"predicate dependency changed or is unavailable: " + predicate
					);
				}
			}
			Set<Identifier> predicateIds = predicates.keySet()
				.stream()
				.map(value -> parseIdentifier(value, "predicate"))
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
			if (!activeClosure.predicates().equals(predicateIds)) {
				return MatchResult.rejected("predicate dependency closure changed");
			}
			return MatchResult.success();
		} catch (RuntimeException error) {
			return MatchResult.rejected(message(error));
		}
	}

	private static JsonElement project(
		final DefinitionSnapshot definitions,
		final DocumentKey key,
		final Set<DocumentKey> retainedKeys
	) {
		return switch (key.type()) {
			case GAME -> presence(definitions.games().containsKey(key.id()), key);
			case PHASE -> presence(definitions.phases().containsKey(key.id()), key);
			case ROLE -> roleProjection(definitions, key, retainedKeys);
			case TEAM -> teamProjection(definitions, key);
			case LIFE_STATE -> lifeStateProjection(definitions, key);
			case FLOW -> flowProjection(definitions, key);
			case FIELD -> fieldProjection(definitions, key);
			default -> sourceProjection(definitions, key);
		};
	}

	private static JsonElement presence(final boolean present, final DocumentKey key) {
		if (!present) {
			throw new IllegalArgumentException("missing dependency " + key);
		}
		JsonObject value = new JsonObject();
		value.addProperty("present", true);
		return value;
	}

	private static JsonElement roleProjection(
		final DefinitionSnapshot definitions,
		final DocumentKey key,
		final Set<DocumentKey> retainedKeys
	) {
		var role = Optional.ofNullable(definitions.roles().get(key.id())).orElseThrow(() ->
			new IllegalArgumentException("missing role dependency " + key.id())
		);
		JsonObject value = new JsonObject();
		value.addProperty("game", role.game().toString());
		value.addProperty("name", role.name().plainText());
		value.add("tags", identifiers(role.tags()));
		role.initializationFlow()
			.filter(flow -> retainedKeys.contains(new DocumentKey(DefinitionType.FLOW, flow)))
			.ifPresent(flow -> value.addProperty("initialization_flow", flow.toString()));
		return value;
	}

	private static JsonElement teamProjection(
		final DefinitionSnapshot definitions,
		final DocumentKey key
	) {
		var team = Optional.ofNullable(definitions.teams().get(key.id())).orElseThrow(() ->
			new IllegalArgumentException("missing team dependency " + key.id())
		);
		JsonObject value = new JsonObject();
		value.addProperty("game", team.game().toString());
		value.addProperty("name", team.name().plainText());
		value.add("tags", identifiers(team.tags()));
		return value;
	}

	private static JsonElement lifeStateProjection(
		final DefinitionSnapshot definitions,
		final DocumentKey key
	) {
		var state = Optional.ofNullable(definitions.lifeStates().get(key.id())).orElseThrow(() ->
			new IllegalArgumentException("missing life-state dependency " + key.id())
		);
		JsonObject value = new JsonObject();
		value.addProperty("game", state.game().toString());
		value.addProperty("name", state.name().plainText());
		value.add("tags", identifiers(state.tags()));
		return value;
	}

	private static JsonElement flowProjection(
		final DefinitionSnapshot definitions,
		final DocumentKey key
	) {
		var flow = Optional.ofNullable(definitions.flows().get(key.id())).orElseThrow(() ->
			new IllegalArgumentException("missing flow dependency " + key.id())
		);
		JsonObject value = new JsonObject();
		value.addProperty("game", flow.game().toString());
		value.addProperty("version", flow.version());
		return value;
	}

	private static JsonElement fieldProjection(
		final DefinitionSnapshot definitions,
		final DocumentKey key
	) {
		var field = Optional.ofNullable(definitions.fields().get(key.id())).orElseThrow(() ->
			new IllegalArgumentException("missing field dependency " + key.id())
		);
		JsonObject value = new JsonObject();
		value.addProperty("game", field.game().toString());
		value.addProperty("version", field.version());
		value.addProperty("type", field.type().name());
		return value;
	}

	private static JsonElement sourceProjection(
		final DefinitionSnapshot definitions,
		final DocumentKey key
	) {
		DefinitionSnapshot.SourceDocument source = definitions.sourceDocuments().get(key);
		if (source == null) {
			throw new IllegalArgumentException("missing source dependency " + key);
		}
		return JsonParser.parseString(source.canonicalJson());
	}

	private static JsonArray identifiers(final Set<Identifier> values) {
		JsonArray result = new JsonArray();
		values.stream().map(Identifier::toString).sorted().forEach(result::add);
		return result;
	}

	private static JsonElement require(final JsonObject root, final String key) {
		JsonElement value = root.get(key);
		if (value == null || value.isJsonNull()) {
			throw new IllegalArgumentException("missing dependency snapshot field " + key);
		}
		return value;
	}

	private static JsonObject requireObject(final JsonElement value, final String context) {
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(context + " must be an object");
		}
		return value.getAsJsonObject();
	}

	private static JsonArray requireArray(final JsonObject root, final String key) {
		JsonElement value = require(root, key);
		if (!value.isJsonArray()) {
			throw new IllegalArgumentException(key + " must be an array");
		}
		return value.getAsJsonArray();
	}

	private static String requireString(final JsonObject root, final String key) {
		return requireString(require(root, key), key);
	}

	private static String requireString(final JsonElement value, final String context) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException(context + " must be a string");
		}
		return value.getAsString();
	}

	private static int requireInt(final JsonObject root, final String key) {
		JsonElement value = require(root, key);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(key + " must be an integer");
		}
		try {
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(key + " must be an integer", error);
		}
	}

	private static Identifier requireIdentifier(final JsonObject root, final String key) {
		return parseIdentifier(requireString(root, key), key);
	}

	private static Identifier parseIdentifier(final String value, final String context) {
		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			throw new IllegalArgumentException(context + " is not a valid identifier");
		}
		return id;
	}

	private static void requireKnownKeys(
		final JsonObject root,
		final Set<String> keys,
		final String context
	) {
		for (String key : root.keySet()) {
			if (!keys.contains(key)) {
				throw new IllegalArgumentException(context + " contains unknown key " + key);
			}
		}
	}

	private static String message(final Throwable error) {
		String value = error.getMessage();
		return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
	}

	public record FreezeResult(Optional<FrozenDocument> snapshot, String message) {
		public FreezeResult {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank()) {
				throw new IllegalArgumentException("dependency freeze message cannot be blank");
			}
		}

		public boolean success() {
			return this.snapshot.isPresent();
		}

		private static FreezeResult success(final FrozenDocument snapshot) {
			return new FreezeResult(Optional.of(snapshot), "message dependency snapshot frozen");
		}

		private static FreezeResult rejected(final String message) {
			return new FreezeResult(Optional.empty(), message);
		}
	}

	public record MatchResult(boolean matched, String message) {
		public MatchResult {
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank()) {
				throw new IllegalArgumentException("dependency match message cannot be blank");
			}
		}

		private static MatchResult success() {
			return new MatchResult(true, "message dependencies match the active registry");
		}

		private static MatchResult rejected(final String message) {
			return new MatchResult(false, message);
		}
	}
}
