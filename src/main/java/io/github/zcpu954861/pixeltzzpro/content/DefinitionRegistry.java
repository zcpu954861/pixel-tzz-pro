package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.MessageEnvelopeHint;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Atomically publishes validated definition generations and retains the last failure report.
 */
public final class DefinitionRegistry {
	public static final DefinitionRegistry INSTANCE = new DefinitionRegistry();
	private static final String ROOT = "pixel_tzz_pro/";

	private volatile State state = State.initial();

	public DefinitionRegistry() {
	}

	public synchronized ReloadOutcome reload(final ResourceManager resourceManager) {
		Compilation compilation;
		try {
			compilation = readAndCompile(resourceManager);
		} catch (IOException | RuntimeException | StackOverflowError error) {
			Problem problem = new Problem(
				"RESOURCE_READ_FAILED",
				DefinitionType.GAME,
				PixelTzzPro.id("registry"),
				PixelTzzPro.id("registry"),
				"unknown",
				"",
				error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
			);
			compilation = new Compilation(java.util.Optional.empty(), List.of(problem));
		}

		if (!compilation.valid()) {
			List<Problem> limitedProblems = compilation.problems()
				.stream()
				.limit(DefinitionCompiler.MAX_DIAGNOSTIC_DETAILS)
				.toList();
			this.state = new State(
				this.state.active(),
				ReloadStatus.INVALID,
				false,
				limitedProblems,
				compilation.totalProblemCount()
			);
			logProblems(compilation);
			return outcome(false);
		}

		long generation = Math.incrementExact(this.state.active().generation());
		DefinitionSnapshot next = compilation.snapshot().orElseThrow().withGeneration(generation);
		ReloadStatus status = next.usable() ? ReloadStatus.READY : ReloadStatus.EMPTY;
		this.state = new State(next, status, next.usable(), List.of(), 0);
		if (next.usable()) {
			PixelTzzPro.LOGGER.info(
				"Applied Pixel TZZ definition generation {}: {} game(s), {} total definition(s)",
				next.generation(),
				next.games().size(),
				next.definitionCount()
			);
		} else {
			PixelTzzPro.LOGGER.info(
				"Applied empty Pixel TZZ definition generation {}; no game profile is currently registered",
				next.generation()
			);
		}
		return outcome(true);
	}

	public synchronized ReloadOutcome markPlatformReloadFailed() {
		this.state = new State(
			this.state.active(),
			ReloadStatus.PLATFORM_RELOAD_FAILED,
			this.state.healthy(),
			this.state.problems(),
			this.state.totalProblemCount()
		);
		return outcome(false);
	}

	public synchronized void reset() {
		this.state = State.initial();
	}

	public View view() {
		State current = this.state;
		return new View(
			current.active(),
			current.status(),
			current.healthy(),
			current.problems(),
			current.totalProblemCount()
		);
	}

	/**
	 * Applies an already compiled candidate. Used by the runnable self-check to prove atomicity.
	 */
	public synchronized ReloadOutcome apply(final Compilation compilation) {
		if (!compilation.valid()) {
			this.state = new State(
				this.state.active(),
				ReloadStatus.INVALID,
				false,
				compilation.problems()
					.stream()
					.limit(DefinitionCompiler.MAX_DIAGNOSTIC_DETAILS)
					.toList(),
				compilation.totalProblemCount()
			);
			return outcome(false);
		}
		long generation = Math.incrementExact(this.state.active().generation());
		DefinitionSnapshot next = compilation.snapshot().orElseThrow().withGeneration(generation);
		this.state = new State(
			next,
			next.usable() ? ReloadStatus.READY : ReloadStatus.EMPTY,
			next.usable(),
			List.of(),
			0
		);
		return outcome(true);
	}

	private ReloadOutcome outcome(final boolean applied) {
		State current = this.state;
		return new ReloadOutcome(
			applied,
			current.active().usable(),
			current.healthy(),
			current.status(),
			current.active().generation(),
			current.active().games().size(),
			current.active().definitionCount(),
			current.problems(),
			current.totalProblemCount()
		);
	}

	private static Compilation readAndCompile(final ResourceManager resourceManager) throws IOException {
		List<Problem> readProblems = new ArrayList<>();
		List<Source> sources = new ArrayList<>();
		Map<Identifier, Resource> resources = resourceManager.listResources(
			"pixel_tzz_pro",
			id -> id.getPath().endsWith(".json")
		);
		List<Map.Entry<Identifier, Resource>> entries = resources.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.toList();
		int maximumDefinitions = DefinitionCompiler.MAX_DEFINITIONS
			+ HudDefinitions.MAX_COMPONENTS
			+ HudDefinitions.MAX_LAYOUTS
			+ HudDefinitions.MAX_PROFILES
			+ HudDefinitions.MAX_COUNTDOWNS;
		if (entries.size() > maximumDefinitions) {
			Map.Entry<Identifier, Resource> first = entries.getFirst();
			return new Compilation(
				java.util.Optional.empty(),
				List.of(
					new Problem(
						"RESOURCE_LIMIT",
						null,
						null,
						first.getKey(),
						first.getValue().sourcePackId(),
						"",
						"definition count " + entries.size() + " exceeds " + maximumDefinitions
					)
				)
			);
		}
		int totalCharacters = 0;
		for (Map.Entry<Identifier, Resource> entry : entries) {
			Identifier resourceId = entry.getKey();
			Resource resource = entry.getValue();
			String path = resourceId.getPath();
			String relative = path.startsWith(ROOT) ? path.substring(ROOT.length()) : "";
			int separator = relative.indexOf('/');
			String directory = separator < 0 ? relative : relative.substring(0, separator);
			String logicalPath = separator < 0
				? ""
				: relative.substring(separator + 1, relative.length() - ".json".length());
			DefinitionType type = DefinitionType.byDirectory(directory).orElse(null);
			if (type == null || logicalPath.isEmpty()) {
				readProblems.add(
					new Problem(
						"UNKNOWN_DEFINITION_PATH",
						type,
						null,
						resourceId,
						resource.sourcePackId(),
						"",
						"expected pixel_tzz_pro/<known type>/<path>.json"
					)
				);
				continue;
			}

			Identifier definitionId = Identifier.fromNamespaceAndPath(resourceId.getNamespace(), logicalPath);
			try (Reader reader = resource.openAsReader()) {
				ReadDefinition definition = readDefinition(reader, type);
				String json = definition.json();
				totalCharacters += json.length();
				if (totalCharacters > DefinitionCompiler.MAX_TOTAL_DEFINITION_CHARACTERS) {
					readProblems.add(
						new Problem(
							"RESOURCE_LIMIT",
							type,
							definitionId,
							resourceId,
							resource.sourcePackId(),
							"",
							"total definition size exceeds "
								+ DefinitionCompiler.MAX_TOTAL_DEFINITION_CHARACTERS
								+ " characters"
						)
					);
					break;
				}
				sources.add(
					new Source(
						type,
						definitionId,
						resourceId,
						resource.sourcePackId(),
						json,
						definition.envelopeHint()
					)
				);
			} catch (DefinitionLimitException error) {
				readProblems.add(
					new Problem(
						"RESOURCE_LIMIT",
						type,
						definitionId,
						resourceId,
						resource.sourcePackId(),
						"",
						error.getMessage()
					)
				);
			} catch (IOException | RuntimeException error) {
				readProblems.add(
					new Problem(
						"RESOURCE_READ_FAILED",
						type,
						definitionId,
						resourceId,
						resource.sourcePackId(),
						"",
						error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
					)
				);
			}
		}

		if (!readProblems.isEmpty()) {
			return new Compilation(java.util.Optional.empty(), readProblems);
		}
		Set<Identifier> functions = listExternalResources(resourceManager, "function", ".mcfunction");
		Map<Identifier, Resource> predicates = mapExternalResources(
			resourceManager,
			"predicate",
			".json"
		);
		Compilation compilation = DefinitionCompiler.compile(sources, functions, predicates.keySet());
		if (!compilation.valid()) {
			return compilation;
		}
		DefinitionSnapshot snapshot = compilation.snapshot().orElseThrow();
		Map<Identifier, String> predicateDocuments = new LinkedHashMap<>();
		for (Identifier predicateId : referencedPredicates(snapshot)) {
			Resource resource = predicates.get(predicateId);
			if (resource == null) {
				continue;
			}
			try (Reader reader = resource.openAsReader()) {
				predicateDocuments.put(
					predicateId,
					JsonParser.parseString(readBounded(reader)).toString()
				);
			} catch (IOException | RuntimeException error) {
				Identifier resourceId = Identifier.fromNamespaceAndPath(
					predicateId.getNamespace(),
					"predicate/" + predicateId.getPath() + ".json"
				);
				return new Compilation(
					java.util.Optional.empty(),
					List.of(
						new Problem(
							"PREDICATE_READ_FAILED",
							null,
							predicateId,
							resourceId,
							resource.sourcePackId(),
							"",
							error.getMessage() == null
								? error.getClass().getSimpleName()
								: error.getMessage()
						)
					)
				);
			}
		}
		return new Compilation(
			java.util.Optional.of(snapshot.withPredicateDocuments(predicateDocuments)),
			List.of()
		);
	}

	private static Set<Identifier> listExternalResources(
		final ResourceManager resourceManager,
		final String directory,
		final String suffix
	) {
		return mapExternalResources(resourceManager, directory, suffix).keySet();
	}

	private static Map<Identifier, Resource> mapExternalResources(
		final ResourceManager resourceManager,
		final String directory,
		final String suffix
	) {
		String prefix = directory + "/";
		return resourceManager.listResources(directory, id -> id.getPath().endsWith(suffix))
			.entrySet()
			.stream()
			.collect(
				Collectors.toUnmodifiableMap(
					entry -> {
						Identifier resourceId = entry.getKey();
					String path = resourceId.getPath();
					String logicalPath = path.substring(prefix.length(), path.length() - suffix.length());
					return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), logicalPath);
					},
					Map.Entry::getValue
				)
			);
	}

	static Set<Identifier> referencedPredicates(
		final DefinitionSnapshot snapshot
	) {
		return referencedPredicates(snapshot, ignored -> true);
	}

	static Set<Identifier> referencedPredicates(
		final DefinitionSnapshot snapshot,
		final Identifier gameId
	) {
		Objects.requireNonNull(gameId, "gameId");
		return referencedPredicates(snapshot, gameId::equals);
	}

	private static Set<Identifier> referencedPredicates(
		final DefinitionSnapshot snapshot,
		final Predicate<Identifier> includesGame
	) {
		Set<Identifier> result = new LinkedHashSet<>();
		snapshot.fields().values().stream()
			.filter(field -> includesGame.test(field.game()))
			.forEach(field -> field.visibleWhen().ifPresent(result::add));
		snapshot.panelActions().values().stream()
			.filter(action -> includesGame.test(action.game()))
			.forEach(action -> {
				action.visibleWhen().ifPresent(result::add);
				action.enabledWhen().ifPresent(result::add);
			});
		snapshot.playerRoutes().values().stream()
			.filter(route -> includesGame.test(route.game()))
			.forEach(route -> route.predicate().ifPresent(result::add));
		snapshot.playerData().values().stream()
			.filter(data -> includesGame.test(data.game()))
			.forEach(data -> data.predicate().ifPresent(result::add));
		snapshot.playerActions().values().stream()
			.filter(action -> includesGame.test(action.game()))
			.forEach(action -> action.predicate().ifPresent(result::add));
		snapshot.flows().values().stream()
			.filter(flow -> includesGame.test(flow.game()))
			.forEach(flow ->
				flow.nodes().values().stream()
					.filter(BranchNode.class::isInstance)
					.map(BranchNode.class::cast)
					.forEach(branch ->
						branch.cases().forEach(branchCase -> result.add(branchCase.predicate()))
					)
			);
		/*
		 * Message predicates are validated as external resource identities during compilation,
		 * but playback and persistence deliberately evaluate their retained JSON instead of the
		 * live resource manager. A cue can be reached directly or indirectly through a message
		 * hook, so every executable cue in the selected game scope must contribute its node and
		 * text-variant predicate documents to the immutable registry generation.
		 */
		snapshot.messageCatalog().messageCues().values().stream()
			.filter(cue -> cue.game().map(includesGame::test).orElse(true))
			.forEach(cue -> cue.nodes().forEach(node -> {
				node.header().when().ifPresent(
					condition -> condition.predicate().ifPresent(result::add)
				);
				if (node instanceof TextNode text) {
					text.variants().forEach(variant ->
						variant.when().predicate().ifPresent(result::add)
					);
				}
			}));
		return Set.copyOf(result);
	}

	private static ReadDefinition readDefinition(
		final Reader reader,
		final DefinitionType type
	) throws IOException {
		if (!isMessageDefinition(type) && !isHudDefinition(type)) {
			return new ReadDefinition(readBounded(reader), Optional.empty());
		}
		CapturingReader capturing = new CapturingReader(
			reader,
			DefinitionCompiler.MAX_DEFINITION_CHARACTERS + 1
		);
		if (isHudDefinition(type)) {
			capturing.drain();
			return new ReadDefinition(capturing.retained(), Optional.empty());
		}
		Optional<MessageEnvelopeHint> envelopeHint = Optional.empty();
		try {
			envelopeHint = readMessageEnvelope(capturing);
		} catch (IOException error) {
			if (capturing.failure() != null) {
				throw capturing.failure();
			}
		} catch (JsonParseException | IllegalStateException | NumberFormatException error) {
			// Malformed content is isolated by the message compiler. Its envelope is not trusted.
		}
		capturing.drain();
		return new ReadDefinition(capturing.retained(), envelopeHint);
	}

	private static Optional<MessageEnvelopeHint> readMessageEnvelope(
		final CapturingReader source
	) throws IOException {
		JsonReader reader = new JsonReader(source);
		reader.setStrictness(Strictness.STRICT);
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			readStrictValue(reader, 0);
			requireEndDocument(reader);
			return Optional.empty();
		}

		reader.beginObject();
		Set<String> names = new HashSet<>();
		boolean trusted = true;
		boolean required = false;
		Optional<Identifier> game = Optional.empty();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (!names.add(name)) {
				throw new JsonParseException("duplicate object key " + name);
			}
			switch (name) {
				case "required" -> {
					JsonToken token = reader.peek();
					if (token == JsonToken.NULL) {
						reader.nextNull();
					} else if (token == JsonToken.BOOLEAN) {
						required = reader.nextBoolean();
					} else {
						trusted = false;
						readStrictValue(reader, 1);
					}
				}
				case "game" -> {
					JsonToken token = reader.peek();
					if (token == JsonToken.NULL) {
						reader.nextNull();
					} else if (token == JsonToken.STRING) {
						String value = reader.nextString();
						Identifier parsed = value.length() <= 1_024
							? parseExplicitIdentifier(value)
							: null;
						if (parsed == null) {
							trusted = false;
						} else {
							game = Optional.of(parsed);
						}
					} else {
						trusted = false;
						readStrictValue(reader, 1);
					}
				}
				default -> readStrictValue(reader, 1);
			}
		}
		reader.endObject();
		requireEndDocument(reader);
		return trusted
			? Optional.of(new MessageEnvelopeHint(required, game))
			: Optional.empty();
	}

	private static void requireEndDocument(final JsonReader reader) throws IOException {
		if (reader.peek() != JsonToken.END_DOCUMENT) {
			throw new JsonParseException("trailing content after JSON value");
		}
	}

	private static void readStrictValue(
		final JsonReader reader,
		final int depth
	) throws IOException {
		if (depth > DefinitionCompiler.MAX_JSON_DEPTH) {
			throw new JsonParseException(
				"JSON nesting exceeds " + DefinitionCompiler.MAX_JSON_DEPTH
			);
		}
		switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				Set<String> names = new HashSet<>();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (!names.add(name)) {
						throw new JsonParseException("duplicate object key " + name);
					}
					readStrictValue(reader, depth + 1);
				}
				reader.endObject();
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				while (reader.hasNext()) {
					readStrictValue(reader, depth + 1);
				}
				reader.endArray();
			}
			case STRING, NUMBER -> reader.skipValue();
			case BOOLEAN -> reader.nextBoolean();
			case NULL -> reader.nextNull();
			default -> throw new JsonParseException("unexpected JSON token " + reader.peek());
		}
	}

	private static Identifier parseExplicitIdentifier(final String value) {
		if (value == null || !value.contains(":")) {
			return null;
		}
		try {
			return Identifier.parse(value);
		} catch (RuntimeException error) {
			return null;
		}
	}

	private static boolean isMessageDefinition(final DefinitionType type) {
		return type == DefinitionType.TEXT_EFFECT || type == DefinitionType.MESSAGE_CUE;
	}

	private static boolean isHudDefinition(final DefinitionType type) {
		return type == DefinitionType.HUD_COMPONENT
			|| type == DefinitionType.HUD_LAYOUT
			|| type == DefinitionType.HUD_PROFILE
			|| type == DefinitionType.COUNTDOWN;
	}

	private static String readBounded(final Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[8_192];
		int read;
		while ((read = reader.read(buffer)) >= 0) {
			if (builder.length() + read > DefinitionCompiler.MAX_DEFINITION_CHARACTERS) {
				throw new DefinitionLimitException(
					"definition exceeds " + DefinitionCompiler.MAX_DEFINITION_CHARACTERS + " characters"
				);
			}
			builder.append(buffer, 0, read);
		}
		return builder.toString();
	}

	private static void logProblems(final Compilation compilation) {
		List<Problem> reported = compilation.problems()
			.stream()
			.limit(DefinitionCompiler.MAX_DIAGNOSTIC_DETAILS)
			.sorted(Comparator.comparing(Problem::summary))
			.toList();
		reported.forEach(
			problem -> PixelTzzPro.LOGGER.error("Pixel TZZ definition error: {}", problem.summary())
		);
		int omitted = compilation.totalProblemCount() - reported.size();
		if (omitted > 0) {
			PixelTzzPro.LOGGER.error(
				"Pixel TZZ definition error report truncated: {} additional problem(s)",
				omitted
			);
		}
	}

	private static final class DefinitionLimitException extends IOException {
		private DefinitionLimitException(final String message) {
			super(message);
		}
	}

	private record ReadDefinition(
		String json,
		Optional<MessageEnvelopeHint> envelopeHint
	) {
		private ReadDefinition {
			Objects.requireNonNull(json, "json");
			envelopeHint = envelopeHint == null ? Optional.empty() : envelopeHint;
		}
	}

	private static final class CapturingReader extends Reader {
		private final Reader delegate;
		private final int retainedLimit;
		private final StringBuilder retained;
		private IOException failure;

		private CapturingReader(final Reader delegate, final int retainedLimit) {
			this.delegate = Objects.requireNonNull(delegate, "delegate");
			this.retainedLimit = retainedLimit;
			this.retained = new StringBuilder(Math.min(retainedLimit, 8_192));
		}

		@Override
		public int read(final char[] buffer, final int offset, final int length) throws IOException {
			try {
				int read = this.delegate.read(buffer, offset, length);
				if (read > 0 && this.retained.length() < this.retainedLimit) {
					int retainedCount = Math.min(
						read,
						this.retainedLimit - this.retained.length()
					);
					this.retained.append(buffer, offset, retainedCount);
				}
				return read;
			} catch (IOException error) {
				this.failure = error;
				throw error;
			}
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}

		private IOException failure() {
			return this.failure;
		}

		private String retained() {
			return this.retained.toString();
		}

		private void drain() throws IOException {
			char[] buffer = new char[8_192];
			while (this.read(buffer, 0, buffer.length) >= 0) {
				// Continue to EOF so oversized files are detected without retaining their tail.
			}
		}
	}

	public enum ReloadStatus {
		EMPTY("empty"),
		READY("ready"),
		INVALID("invalid"),
		PLATFORM_RELOAD_FAILED("platform_reload_failed");

		private final String serializedName;

		ReloadStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return this.serializedName;
		}
	}

	private record State(
		DefinitionSnapshot active,
		ReloadStatus status,
		boolean healthy,
		List<Problem> problems,
		int totalProblemCount
	) {
		private State {
			problems = List.copyOf(problems);
			if (totalProblemCount < problems.size()) {
				throw new IllegalArgumentException("total problem count cannot be smaller than retained details");
			}
		}

		private static State initial() {
			return new State(DefinitionSnapshot.empty(), ReloadStatus.EMPTY, false, List.of(), 0);
		}
	}

	public record View(
		DefinitionSnapshot active,
		ReloadStatus status,
		boolean healthy,
		List<Problem> problems,
		int totalProblemCount
	) {
		public View {
			problems = List.copyOf(problems);
			if (totalProblemCount < problems.size()) {
				throw new IllegalArgumentException("total problem count cannot be smaller than retained details");
			}
		}

		public boolean canStartNewFlows() {
			return this.healthy && this.active.usable();
		}

		public String diagnosticSummary() {
			if (!this.problems.isEmpty()) {
				Problem first = this.problems.getFirst();
				return first.summary()
					+ (this.totalProblemCount == 1 ? "" : " (+" + (this.totalProblemCount - 1) + ")");
			}
			return switch (this.status) {
				case READY -> this.active.games().size()
					+ " game(s), "
					+ this.active.definitionCount()
					+ " definition(s)";
				case EMPTY -> "no game definition";
				case INVALID -> "definition compilation failed";
				case PLATFORM_RELOAD_FAILED -> "Minecraft data-pack reload failed; previous resources retained";
			};
		}
	}

	public record ReloadOutcome(
		boolean applied,
		boolean activeUsable,
		boolean healthy,
		ReloadStatus status,
		long generation,
		int gameCount,
		int definitionCount,
		List<Problem> problems,
		int totalProblemCount
	) {
		public ReloadOutcome {
			problems = List.copyOf(problems);
			if (totalProblemCount < problems.size()) {
				throw new IllegalArgumentException("total problem count cannot be smaller than retained details");
			}
		}
	}
}
