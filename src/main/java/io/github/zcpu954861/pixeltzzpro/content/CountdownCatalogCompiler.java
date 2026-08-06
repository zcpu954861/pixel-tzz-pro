package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitionParser.Issue;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownCatalog;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.DisabledCountdown;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.OpeningCountdownReference;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Isolated compiler for the dedicated countdown catalog.
 *
 * <p>An invalid optional countdown is quarantined without rejecting the otherwise healthy data-pack
 * generation. A game that marks its opening countdown required is recorded in the fail-closed set
 * and is rejected later by the approval authority.</p>
 */
public final class CountdownCatalogCompiler {
	private CountdownCatalogCompiler() {
	}

	public static Result compile(
		final List<Source> sources,
		final DefinitionSnapshot core,
		final Set<Identifier> functions
	) {
		Objects.requireNonNull(sources, "sources");
		Objects.requireNonNull(core, "core");
		Objects.requireNonNull(functions, "functions");

		Map<Identifier, List<Source>> grouped = new LinkedHashMap<>();
		sources.stream()
			.sorted(sourceOrder())
			.forEach(source -> {
				if (source.type() != DefinitionType.COUNTDOWN) {
					throw new IllegalArgumentException(
						"countdown catalog received non-countdown source " + source.type()
					);
				}
				grouped.computeIfAbsent(source.id(), ignored -> new ArrayList<>()).add(source);
			});

		Map<Identifier, CountdownDefinition> definitions = new LinkedHashMap<>();
		Map<Identifier, DisabledCountdown> disabled = new LinkedHashMap<>();
		Map<DocumentKey, SourceDocument> documents = new LinkedHashMap<>();
		int retainedDefinitions = 0;
		for (Map.Entry<Identifier, List<Source>> entry : grouped.entrySet()) {
			Identifier id = entry.getKey();
			List<Source> candidates = entry.getValue();
			if (retainedDefinitions >= CountdownDefinitions.MAX_COUNTDOWNS) {
				continue;
			}
			retainedDefinitions++;
			if (candidates.size() != 1) {
				List<Problem> diagnostics = candidates.stream()
					.limit(CountdownDefinitionParser.MAX_ISSUE_DETAILS)
					.map(source -> problem(
						source,
						"DUPLICATE_DEFINITION",
						"",
						"countdown definition is supplied by more than one resource"
					))
					.toList();
				disabled.put(id, new DisabledCountdown(id, diagnostics, candidates.size()));
				continue;
			}

			Source source = candidates.getFirst();
			ParseResult parsed = CountdownDefinitionParser.parse(id, source.json());
			List<Problem> diagnostics = new ArrayList<>();
			for (Issue issue : parsed.issues()) {
				diagnostics.add(problem(source, issue.code(), issue.pointer(), issue.message()));
			}
			CountdownDefinition definition = parsed.definition().orElse(null);
			if (definition != null) {
				validateReferences(source, definition, core, functions, diagnostics);
				validateProjectionBudget(source, definition, diagnostics);
			}
			if (definition == null || !diagnostics.isEmpty()) {
				disabled.put(
					id,
					new DisabledCountdown(
						id,
						diagnostics.stream()
							.limit(CountdownDefinitionParser.MAX_ISSUE_DETAILS)
							.toList(),
						Math.max(parsed.totalIssueCount(), diagnostics.size())
					)
				);
				continue;
			}

			definitions.put(id, definition);
			DocumentKey key = new DocumentKey(DefinitionType.COUNTDOWN, id);
			documents.put(
				key,
				new SourceDocument(
					key,
					source.resource(),
					source.sourcePack(),
					parsed.canonical(),
					parsed.sha256()
				)
			);
		}

		Set<Identifier> requiredFailures = new LinkedHashSet<>();
		for (GameDefinition game : core.games().values().stream()
			.sorted(Comparator.comparing(value -> value.id().toString()))
			.toList()) {
			game.openingCountdown().ifPresent(reference -> {
				if (!definitions.containsKey(reference.definition())) {
					disabled.computeIfAbsent(
						reference.definition(),
						ignored -> missingDefinition(game, reference, core)
					);
				}
				if (reference.required() && !definitions.containsKey(reference.definition())) {
					requiredFailures.add(game.id());
				}
			});
		}

		return new Result(
			new CountdownCatalog(definitions, disabled, requiredFailures, Set.of()),
			documents
		);
	}

	/**
	 * Reuses only currently referenced definitions from the previous published generation when the
	 * replacement candidate is missing or invalid. The retained definition is rechecked against the
	 * new generation's callback and cue closure before it is made executable.
	 */
	public static DefinitionSnapshot retainReferencedLastKnownGood(
		final DefinitionSnapshot candidate,
		final DefinitionSnapshot previous
	) {
		Objects.requireNonNull(candidate, "candidate");
		Objects.requireNonNull(previous, "previous");
		CountdownCatalog current = candidate.countdownCatalog();
		if (previous.countdownCatalog().countdowns().isEmpty()) {
			return candidate;
		}

		Map<Identifier, CountdownDefinition> definitions = new LinkedHashMap<>(current.countdowns());
		Map<Identifier, DisabledCountdown> disabled = new LinkedHashMap<>(current.disabled());
		Map<DocumentKey, SourceDocument> retainedDocuments = new LinkedHashMap<>();
		Set<Identifier> retainedFallbacks = new LinkedHashSet<>();
		Set<Identifier> referenced = new LinkedHashSet<>();
		candidate.games().values().stream()
			.sorted(Comparator.comparing(value -> value.id().toString()))
			.forEach(game -> game.openingCountdown().map(OpeningCountdownReference::definition).ifPresent(referenced::add));

		for (Identifier id : referenced) {
			if (definitions.containsKey(id) || !disabled.containsKey(id)) {
				continue;
			}
			CountdownDefinition retained = previous.countdownCatalog().countdowns().get(id);
			DocumentKey key = new DocumentKey(DefinitionType.COUNTDOWN, id);
			SourceDocument document = previous.sourceDocuments().get(key);
			if (retained == null || document == null) {
				continue;
			}
			Source retainedSource = new Source(
				DefinitionType.COUNTDOWN,
				id,
				document.resource(),
				document.sourcePack(),
				document.canonicalJson()
			);
			List<Problem> closureProblems = new ArrayList<>();
			validateReferences(
				retainedSource,
				retained,
				candidate,
				candidate.functions(),
				closureProblems
			);
			validateProjectionBudget(retainedSource, retained, closureProblems);
			if (!closureProblems.isEmpty()) {
				disabled.put(id, mergeDisabled(disabled.get(id), closureProblems));
				continue;
			}
			definitions.put(id, retained);
			retainedDocuments.put(key, document);
			retainedFallbacks.add(id);
		}

		Set<Identifier> requiredFailures = requiredFailures(candidate, definitions);
		CountdownCatalog reconciled = new CountdownCatalog(
			definitions,
			disabled,
			requiredFailures,
			retainedFallbacks
		);
		return candidate.withCountdownCatalog(reconciled, retainedDocuments);
	}

	private static DisabledCountdown missingDefinition(
		final GameDefinition game,
		final OpeningCountdownReference reference,
		final DefinitionSnapshot core
	) {
		Identifier id = reference.definition();
		DocumentKey gameKey = new DocumentKey(DefinitionType.GAME, game.id());
		SourceDocument gameDocument = core.sourceDocuments().get(gameKey);
		Identifier resource = Identifier.fromNamespaceAndPath(
			id.getNamespace(),
			"pixel_tzz_pro/countdowns/" + id.getPath() + ".json"
		);
		Problem problem = new Problem(
			"MISSING_REFERENCE",
			DefinitionType.COUNTDOWN,
			id,
			resource,
			gameDocument == null ? "missing" : gameDocument.sourcePack(),
			"/opening_countdown/definition",
			"game " + game.id() + " references unavailable countdown definition " + id
		);
		return new DisabledCountdown(id, List.of(problem), 1);
	}

	private static Set<Identifier> requiredFailures(
		final DefinitionSnapshot candidate,
		final Map<Identifier, CountdownDefinition> definitions
	) {
		Set<Identifier> failures = new LinkedHashSet<>();
		candidate.games().values().stream()
			.sorted(Comparator.comparing(value -> value.id().toString()))
			.forEach(game -> game.openingCountdown().ifPresent(reference -> {
				if (reference.required() && !definitions.containsKey(reference.definition())) {
					failures.add(game.id());
				}
			}));
		return failures;
	}

	private static DisabledCountdown mergeDisabled(
		final DisabledCountdown current,
		final List<Problem> additional
	) {
		List<Problem> retained = new ArrayList<>(current.diagnostics());
		for (Problem problem : additional) {
			if (retained.size() >= CountdownDefinitionParser.MAX_ISSUE_DETAILS) {
				break;
			}
			retained.add(problem);
		}
		return new DisabledCountdown(
			current.id(),
			retained,
			Math.addExact(current.totalDiagnosticCount(), additional.size())
		);
	}

	private static void validateProjectionBudget(
		final Source source,
		final CountdownDefinition definition,
		final List<Problem> diagnostics
	) {
		CountdownProjectionDocumentCodec.auditDefinition(definition).violations().forEach(violation ->
			diagnostics.add(problem(
				source,
				violation.code(),
				violation.pointer(),
				violation.message()
			))
		);
	}

	private static void validateReferences(
		final Source source,
		final CountdownDefinition definition,
		final DefinitionSnapshot core,
		final Set<Identifier> functions,
		final List<Problem> diagnostics
	) {
		definition.callbacks().forEach((slot, callbacks) -> callbacks.forEach(callback -> {
			if (callback.required() && !functions.contains(callback.function())) {
				diagnostics.add(problem(
					source,
					"MISSING_REFERENCE",
					"/callbacks/" + slot.name().toLowerCase(java.util.Locale.ROOT),
					"required countdown callback function is unavailable: " + callback.function()
				));
			}
		}));
		definition.checkpoints().forEach(checkpoint -> checkpoint.cue().ifPresent(cue -> {
			if (!core.messageCatalog().messageCues().containsKey(cue)) {
				diagnostics.add(problem(
					source,
					"MISSING_REFERENCE",
					"/checkpoints",
					"countdown checkpoint cue is unavailable: " + cue
				));
			}
		}));
	}

	private static Problem problem(
		final Source source,
		final String code,
		final String pointer,
		final String message
	) {
		return new Problem(
			code,
			DefinitionType.COUNTDOWN,
			source.id(),
			source.resource(),
			source.sourcePack(),
			pointer,
			message
		);
	}

	private static Comparator<Source> sourceOrder() {
		return Comparator.comparing((Source source) -> source.id().toString())
			.thenComparing(source -> source.resource().toString())
			.thenComparing(Source::sourcePack);
	}

	public record Result(
		CountdownCatalog catalog,
		Map<DocumentKey, SourceDocument> sourceDocuments
	) {
		public Result {
			catalog = Objects.requireNonNull(catalog, "catalog");
			sourceDocuments = Map.copyOf(sourceDocuments);
		}
	}
}
