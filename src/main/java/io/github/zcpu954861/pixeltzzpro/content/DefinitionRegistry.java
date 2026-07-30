package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
		if (entries.size() > DefinitionCompiler.MAX_DEFINITIONS) {
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
						"definition count " + entries.size() + " exceeds " + DefinitionCompiler.MAX_DEFINITIONS
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
				String json = readBounded(reader);
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
						json
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
		return Set.copyOf(result);
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
