package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticFallbackSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRouteDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * One immutable generation of validated data-pack definitions.
 *
 * <p>Later flow instances may retain this object while a newer generation becomes active.
 */
public record DefinitionSnapshot(
	long generation,
	Map<Identifier, GameDefinition> games,
	Map<Identifier, RoleDefinition> roles,
	Map<Identifier, TeamDefinition> teams,
	Map<Identifier, LifeStateDefinition> lifeStates,
	Map<Identifier, PhaseDefinition> phases,
	Map<Identifier, FieldDefinition> fields,
	Map<Identifier, FlowDefinition> flows,
	Map<Identifier, TaskDefinition> tasks,
	Map<Identifier, PanelActionDefinition> panelActions,
	Map<Identifier, PlayerRouteDefinition> playerRoutes,
	Map<Identifier, PlayerDataDefinition> playerData,
	Map<Identifier, PlayerActionDefinition> playerActions,
	Map<Identifier, PageDefinition> pages,
	Map<Identifier, ThemeDefinition> themes,
	MessageCatalog messageCatalog,
	HudDefinitions.HudCatalog hudCatalog,
	Map<DocumentKey, SourceDocument> sourceDocuments,
	Set<Identifier> functions,
	Set<Identifier> predicates,
	Map<Identifier, String> predicateDocuments
) {
	public DefinitionSnapshot {
		games = Map.copyOf(games);
		roles = Map.copyOf(roles);
		teams = Map.copyOf(teams);
		lifeStates = Map.copyOf(lifeStates);
		phases = Map.copyOf(phases);
		fields = Map.copyOf(fields);
		flows = Map.copyOf(flows);
		tasks = Map.copyOf(tasks);
		panelActions = Map.copyOf(panelActions);
		playerRoutes = Map.copyOf(playerRoutes);
		playerData = Map.copyOf(playerData);
		playerActions = Map.copyOf(playerActions);
		pages = Map.copyOf(pages);
		themes = Map.copyOf(themes);
		messageCatalog = Objects.requireNonNull(messageCatalog, "messageCatalog");
		hudCatalog = Objects.requireNonNull(hudCatalog, "hudCatalog");
		sourceDocuments = Map.copyOf(sourceDocuments);
		functions = Set.copyOf(functions);
		predicates = Set.copyOf(predicates);
		predicateDocuments = Map.copyOf(predicateDocuments);
	}

	/** Compatibility constructor for callers compiled against the V3B snapshot shape. */
	public DefinitionSnapshot(
		final long generation,
		final Map<Identifier, GameDefinition> games,
		final Map<Identifier, RoleDefinition> roles,
		final Map<Identifier, TeamDefinition> teams,
		final Map<Identifier, LifeStateDefinition> lifeStates,
		final Map<Identifier, PhaseDefinition> phases,
		final Map<Identifier, FieldDefinition> fields,
		final Map<Identifier, FlowDefinition> flows,
		final Map<Identifier, TaskDefinition> tasks,
		final Map<Identifier, PanelActionDefinition> panelActions,
		final Map<Identifier, PlayerRouteDefinition> playerRoutes,
		final Map<Identifier, PlayerDataDefinition> playerData,
		final Map<Identifier, PlayerActionDefinition> playerActions,
		final Map<Identifier, PageDefinition> pages,
		final Map<Identifier, ThemeDefinition> themes,
		final MessageCatalog messageCatalog,
		final Map<DocumentKey, SourceDocument> sourceDocuments,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final Map<Identifier, String> predicateDocuments
	) {
		this(
			generation,
			games,
			roles,
			teams,
			lifeStates,
			phases,
			fields,
			flows,
			tasks,
			panelActions,
			playerRoutes,
			playerData,
			playerActions,
			pages,
			themes,
			messageCatalog,
			HudDefinitions.HudCatalog.empty(),
			sourceDocuments,
			functions,
			predicates,
			predicateDocuments
		);
	}

	/**
	 * Compatibility constructor for callers compiled against the V3A snapshot shape.
	 */
	public DefinitionSnapshot(
		final long generation,
		final Map<Identifier, GameDefinition> games,
		final Map<Identifier, RoleDefinition> roles,
		final Map<Identifier, TeamDefinition> teams,
		final Map<Identifier, LifeStateDefinition> lifeStates,
		final Map<Identifier, PhaseDefinition> phases,
		final Map<Identifier, FieldDefinition> fields,
		final Map<Identifier, FlowDefinition> flows,
		final Map<Identifier, TaskDefinition> tasks,
		final Map<Identifier, PanelActionDefinition> panelActions,
		final Map<Identifier, PlayerRouteDefinition> playerRoutes,
		final Map<Identifier, PlayerDataDefinition> playerData,
		final Map<Identifier, PlayerActionDefinition> playerActions,
		final Map<Identifier, PageDefinition> pages,
		final Map<Identifier, ThemeDefinition> themes,
		final Map<DocumentKey, SourceDocument> sourceDocuments,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final Map<Identifier, String> predicateDocuments
	) {
		this(
			generation,
			games,
			roles,
			teams,
			lifeStates,
			phases,
			fields,
			flows,
			tasks,
			panelActions,
			playerRoutes,
			playerData,
			playerActions,
			pages,
			themes,
			MessageCatalog.empty(),
			sourceDocuments,
			functions,
			predicates,
			predicateDocuments
		);
	}

	public DefinitionSnapshot(
		final long generation,
		final Map<Identifier, GameDefinition> games,
		final Map<Identifier, RoleDefinition> roles,
		final Map<Identifier, TeamDefinition> teams,
		final Map<Identifier, LifeStateDefinition> lifeStates,
		final Map<Identifier, PhaseDefinition> phases,
		final Map<Identifier, FieldDefinition> fields,
		final Map<Identifier, FlowDefinition> flows,
		final Map<Identifier, TaskDefinition> tasks,
		final Map<Identifier, PanelActionDefinition> panelActions,
		final Map<Identifier, PageDefinition> pages,
		final Map<Identifier, ThemeDefinition> themes,
		final Map<DocumentKey, SourceDocument> sourceDocuments,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final Map<Identifier, String> predicateDocuments
	) {
		this(
			generation,
			games,
			roles,
			teams,
			lifeStates,
			phases,
			fields,
			flows,
			tasks,
			panelActions,
			Map.of(),
			Map.of(),
			Map.of(),
			pages,
			themes,
			sourceDocuments,
			functions,
			predicates,
			predicateDocuments
		);
	}

	public DefinitionSnapshot(
		final long generation,
		final Map<Identifier, GameDefinition> games,
		final Map<Identifier, RoleDefinition> roles,
		final Map<Identifier, TeamDefinition> teams,
		final Map<Identifier, LifeStateDefinition> lifeStates,
		final Map<Identifier, PhaseDefinition> phases,
		final Map<Identifier, FieldDefinition> fields,
		final Map<Identifier, FlowDefinition> flows,
		final Map<Identifier, PanelActionDefinition> panelActions,
		final Map<Identifier, PageDefinition> pages,
		final Map<Identifier, ThemeDefinition> themes,
		final Map<DocumentKey, SourceDocument> sourceDocuments,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final Map<Identifier, String> predicateDocuments
	) {
		this(
			generation,
			games,
			roles,
			teams,
			lifeStates,
			phases,
			fields,
			flows,
			Map.of(),
			panelActions,
			Map.of(),
			Map.of(),
			Map.of(),
			pages,
			themes,
			sourceDocuments,
			functions,
			predicates,
			predicateDocuments
		);
	}

	public static DefinitionSnapshot empty() {
		return new DefinitionSnapshot(
			0L,
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			MessageCatalog.empty(),
			Map.of(),
			Set.of(),
			Set.of(),
			Map.of()
		);
	}

	public boolean usable() {
		return !this.games.isEmpty();
	}

	public int definitionCount() {
		return this.games.size()
			+ this.roles.size()
			+ this.teams.size()
			+ this.lifeStates.size()
			+ this.phases.size()
			+ this.fields.size()
			+ this.flows.size()
			+ this.tasks.size()
			+ this.panelActions.size()
			+ this.playerRoutes.size()
			+ this.playerData.size()
			+ this.playerActions.size()
			+ this.pages.size()
			+ this.themes.size()
			+ this.messageCatalog.definitionCount()
			+ this.hudCatalog.definitionCount();
	}

	/**
	 * Returns a generation-independent digest of every effective definition input that message
	 * playback may consult after an invocation has started.
	 *
	 * <p>Registry generation numbers restart with the JVM and therefore cannot prove that a
	 * persisted {@code restart=continue} instance is being rebuilt against the same data-pack
	 * contents. This digest deliberately excludes the generation counter and includes the stable
	 * source-document digests, function/predicate identities, and predicate documents in a
	 * deterministic order. A content-identical server restart produces the same value; any
	 * definition change fails closed instead of letting an old instance read a new generation.
	 */
	public String stableContentFingerprint() {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			this.sourceDocuments.entrySet().stream()
				.sorted(
					Map.Entry.<DocumentKey, SourceDocument>comparingByKey(
						java.util.Comparator
							.comparing((DocumentKey key) -> key.type().name())
							.thenComparing(key -> key.id().toString())
					)
				)
				.forEach(entry -> {
					updateDigest(digest, "document");
					updateDigest(digest, entry.getKey().type().name());
					updateDigest(digest, entry.getKey().id().toString());
					updateDigest(digest, entry.getValue().sha256());
				});
			this.functions.stream().map(Identifier::toString).sorted().forEach(value -> {
				updateDigest(digest, "function");
				updateDigest(digest, value);
			});
			this.predicates.stream().map(Identifier::toString).sorted().forEach(value -> {
				updateDigest(digest, "predicate");
				updateDigest(digest, value);
			});
			this.predicateDocuments.entrySet().stream()
				.sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Identifier::toString)))
				.forEach(entry -> {
					updateDigest(digest, "predicate_document");
					updateDigest(digest, entry.getKey().toString());
					updateDigest(digest, entry.getValue());
				});
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static void updateDigest(final MessageDigest digest, final String value) {
		byte[] bytes = Objects.requireNonNull(value, "digest value")
			.getBytes(StandardCharsets.UTF_8);
		digest.update((byte)(bytes.length >>> 24));
		digest.update((byte)(bytes.length >>> 16));
		digest.update((byte)(bytes.length >>> 8));
		digest.update((byte)bytes.length);
		digest.update(bytes);
	}

	public DefinitionSnapshot withGeneration(final long newGeneration) {
		return new DefinitionSnapshot(
			newGeneration,
			this.games,
			this.roles,
			this.teams,
			this.lifeStates,
			this.phases,
			this.fields,
			this.flows,
			this.tasks,
			this.panelActions,
			this.playerRoutes,
			this.playerData,
			this.playerActions,
			this.pages,
			this.themes,
			this.messageCatalog,
			this.hudCatalog,
			this.sourceDocuments,
			this.functions,
			this.predicates,
			this.predicateDocuments
		);
	}

	public DefinitionSnapshot withPredicateDocuments(
		final Map<Identifier, String> documents
	) {
		return new DefinitionSnapshot(
			this.generation,
			this.games,
			this.roles,
			this.teams,
			this.lifeStates,
			this.phases,
			this.fields,
			this.flows,
			this.tasks,
			this.panelActions,
			this.playerRoutes,
			this.playerData,
			this.playerActions,
			this.pages,
			this.themes,
			this.messageCatalog,
			this.hudCatalog,
			this.sourceDocuments,
			this.functions,
			this.predicates,
			documents
		);
	}

	/** Publishes an isolated V3C catalog and its canonical source documents atomically. */
	public DefinitionSnapshot withHudCatalog(
		final HudDefinitions.HudCatalog catalog,
		final Map<DocumentKey, SourceDocument> hudDocuments
	) {
		Map<DocumentKey, SourceDocument> documents = new java.util.LinkedHashMap<>(this.sourceDocuments);
		documents.putAll(hudDocuments);
		return new DefinitionSnapshot(
			this.generation,
			this.games,
			this.roles,
			this.teams,
			this.lifeStates,
			this.phases,
			this.fields,
			this.flows,
			this.tasks,
			this.panelActions,
			this.playerRoutes,
			this.playerData,
			this.playerActions,
			this.pages,
			this.themes,
			this.messageCatalog,
			catalog,
			documents,
			this.functions,
			this.predicates,
			this.predicateDocuments
		);
	}

	public record DocumentKey(DefinitionType type, Identifier id) {
	}

	public record SourceDocument(
		DocumentKey key,
		Identifier resource,
		String sourcePack,
		String canonicalJson,
		String sha256
	) {
	}

	/**
	 * V3B message resources share the parent snapshot generation but isolate their own failures.
	 *
	 * <p>Only valid definitions contribute to {@link #definitionCount()}. A disabled entry keeps
	 * bounded diagnostics and source identity so one bad optional cue never rejects unrelated
	 * game definitions. A cue that parsed successfully before an external-reference failure may
	 * also retain its validated static fallback; the fallback is never reconstructed from malformed
	 * or truncated source text.
	 */
	public record MessageCatalog(
		Map<Identifier, TextEffectDefinition> textEffects,
		Map<Identifier, MessageCueDefinition> messageCues,
		Map<DocumentKey, DisabledMessageDefinition> disabled,
		Set<Identifier> startBlockedGames
	) {
		public MessageCatalog {
			textEffects = Map.copyOf(textEffects);
			messageCues = Map.copyOf(messageCues);
			disabled = Map.copyOf(disabled);
			startBlockedGames = Set.copyOf(startBlockedGames);
		}

		public static MessageCatalog empty() {
			return new MessageCatalog(Map.of(), Map.of(), Map.of(), Set.of());
		}

		public int definitionCount() {
			return this.textEffects.size() + this.messageCues.size();
		}
	}

	public record DisabledMessageDefinition(
		SourceDocument sourceDocument,
		boolean required,
		Optional<Identifier> game,
		Optional<StaticFallbackSpec> staticFallback,
		List<Problem> diagnostics,
		int totalDiagnosticCount
	) {
		public DisabledMessageDefinition {
			sourceDocument = Objects.requireNonNull(sourceDocument, "sourceDocument");
			game = game == null ? Optional.empty() : game;
			staticFallback = staticFallback == null ? Optional.empty() : staticFallback;
			diagnostics = List.copyOf(diagnostics);
			if (totalDiagnosticCount < diagnostics.size()) {
				throw new IllegalArgumentException(
					"total diagnostic count cannot be smaller than retained diagnostics"
				);
			}
		}

		public DisabledMessageDefinition(
			final SourceDocument sourceDocument,
			final boolean required,
			final Optional<Identifier> game,
			final List<Problem> diagnostics,
			final int totalDiagnosticCount
		) {
			this(
				sourceDocument,
				required,
				game,
				Optional.empty(),
				diagnostics,
				totalDiagnosticCount
			);
		}

		public boolean requiredFallbackSatisfiesStart() {
			return this.staticFallback
				.filter(StaticFallbackSpec::fallbackSatisfiesRequired)
				.isPresent();
		}

		public boolean diagnosticsTruncated() {
			return this.totalDiagnosticCount > this.diagnostics.size();
		}
	}
}
