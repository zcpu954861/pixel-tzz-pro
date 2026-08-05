package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.OpeningCountdownReference;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionParser.Issue;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.*;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/** Compiles the four V3C resource directories into one isolated immutable catalog. */
public final class HudCatalogCompiler {
	private HudCatalogCompiler() {
	}

	public static Result compile(
		final List<Source> sources,
		final DefinitionSnapshot core,
		final Set<Identifier> functions
	) {
		return new Compiler(sources, core, functions).compile();
	}

	public record Result(
		HudCatalog catalog,
		Map<DocumentKey, SourceDocument> sourceDocuments
	) {
		public Result {
			catalog = Objects.requireNonNull(catalog, "catalog");
			sourceDocuments = Map.copyOf(sourceDocuments);
		}
	}

	private static final class Compiler {
		private final List<Source> sources;
		private final DefinitionSnapshot core;
		private final Set<Identifier> functions;
		private final Map<HudResourceKey, Source> origins = new LinkedHashMap<>();
		private final Map<DocumentKey, SourceDocument> documents = new LinkedHashMap<>();
		private final Map<HudResourceKey, DisabledHudDefinition> disabled = new LinkedHashMap<>();
		private final Map<Identifier, HudComponentDefinition> components = new LinkedHashMap<>();
		private final Map<Identifier, HudLayoutDefinition> layouts = new LinkedHashMap<>();
		private final Map<Identifier, HudProfileDefinition> profiles = new LinkedHashMap<>();
		private final Map<Identifier, CountdownDefinition> countdowns = new LinkedHashMap<>();
		private final Map<Identifier, List<Problem>> gameDiagnostics = new LinkedHashMap<>();
		private final Map<Identifier, RequiredCountdownFailure> requiredFailures = new LinkedHashMap<>();

		private Compiler(
			final List<Source> sources,
			final DefinitionSnapshot core,
			final Set<Identifier> functions
		) {
			this.sources = List.copyOf(sources);
			this.core = Objects.requireNonNull(core, "core");
			this.functions = Set.copyOf(functions);
		}

		private Result compile() {
			parseSources();
			validateResourceClosures();
			validateProfiles();
			validateCountdowns();
			validateGames();
			return new Result(
				new HudCatalog(
					this.components,
					this.layouts,
					this.profiles,
					this.countdowns,
					this.disabled,
					this.gameDiagnostics,
					this.requiredFailures
				),
				this.documents
			);
		}

		private void parseSources() {
			List<Source> ordered = this.sources.stream()
				.filter(source -> kind(source.type()) != null)
				.sorted(
					Comparator.comparing((Source source) -> source.type().ordinal())
						.thenComparing(source -> source.id().toString())
						.thenComparing(source -> source.resource().toString())
				)
				.toList();
			Map<HudResourceKey, List<Source>> grouped = new LinkedHashMap<>();
			for (Source source : ordered) {
				HudResourceKey key = key(source);
				grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(source);
			}
			Map<HudResourceKind, Integer> accepted = new EnumMap<>(HudResourceKind.class);
			for (Map.Entry<HudResourceKey, List<Source>> entry : grouped.entrySet()) {
				HudResourceKey key = entry.getKey();
				List<Source> definitions = entry.getValue();
				Source source = definitions.getFirst();
				this.origins.put(key, source);
				if (definitions.size() > 1) {
					List<Problem> diagnostics = new ArrayList<>();
					for (Source duplicate : definitions) {
						diagnostics.add(problem(
							duplicate,
							"DUPLICATE_DEFINITION",
							"",
							"multiple effective sources supplied the same HUD definition id"
						));
					}
					disable(key, Optional.empty(), diagnostics, diagnostics.size());
					continue;
				}
				int next = accepted.getOrDefault(key.kind(), 0) + 1;
				accepted.put(key.kind(), next);
				if (next > maximum(key.kind())) {
					Problem diagnostic = problem(
						source,
						"RESOURCE_LIMIT",
						"",
						serialized(key.kind()) + " resource count exceeds " + maximum(key.kind())
					);
					disable(key, Optional.empty(), List.of(diagnostic), 1);
					continue;
				}
				parseSource(source, key);
			}
		}

		private void parseSource(final Source source, final HudResourceKey key) {
			switch (key.kind()) {
				case COMPONENT -> storeParseResult(source, key, HudDefinitionParser.parseComponent(source.id(), source.json()));
				case LAYOUT -> storeParseResult(source, key, HudDefinitionParser.parseLayout(source.id(), source.json()));
				case PROFILE -> storeParseResult(source, key, HudDefinitionParser.parseProfile(source.id(), source.json()));
				case COUNTDOWN -> storeParseResult(source, key, HudDefinitionParser.parseCountdown(source.id(), source.json()));
			}
		}

		private <T> void storeParseResult(
			final Source source,
			final HudResourceKey key,
			final ParseResult<T> result
		) {
			Optional<SourceDocument> document = result.value().flatMap(value -> sourceDocument(source, value));
			document.ifPresent(value -> this.documents.put(value.key(), value));
			if (!result.valid()) {
				List<Problem> diagnostics = result.issues().stream()
					.map(issue -> problem(source, issue))
					.toList();
				disable(key, document, diagnostics, result.totalIssueCount());
				return;
			}
			T value = result.value().orElseThrow();
			switch (key.kind()) {
				case COMPONENT -> this.components.put(key.id(), (HudComponentDefinition)value);
				case LAYOUT -> this.layouts.put(key.id(), (HudLayoutDefinition)value);
				case PROFILE -> this.profiles.put(key.id(), (HudProfileDefinition)value);
				case COUNTDOWN -> this.countdowns.put(key.id(), (CountdownDefinition)value);
			}
		}

		private Optional<SourceDocument> sourceDocument(final Source source, final Object value) {
			String canonical;
			String sha256;
			if (value instanceof HudComponentDefinition component) {
				canonical = component.canonicalDocument();
				sha256 = component.sha256();
			} else if (value instanceof HudLayoutDefinition layout) {
				canonical = layout.canonicalDocument();
				sha256 = layout.sha256();
			} else if (value instanceof HudProfileDefinition profile) {
				canonical = profile.canonicalDocument();
				sha256 = profile.sha256();
			} else if (value instanceof CountdownDefinition countdown) {
				canonical = countdown.canonicalDocument();
				sha256 = countdown.sha256();
			} else {
				return Optional.empty();
			}
			DocumentKey documentKey = new DocumentKey(source.type(), source.id());
			return Optional.of(
				new SourceDocument(
					documentKey,
					source.resource(),
					source.sourcePack(),
					canonical,
					sha256
				)
			);
		}

		private void validateResourceClosures() {
			while (true) {
				Map<HudResourceKey, List<Problem>> failures = new LinkedHashMap<>();
				this.components.keySet().stream().sorted().forEach(id -> {
					HudResourceKey key = new HudResourceKey(HudResourceKind.COMPONENT, id);
					List<Problem> diagnostics = validateClosure(key, Optional.empty());
					if (!diagnostics.isEmpty()) {
						failures.put(key, diagnostics);
					}
				});
				this.layouts.keySet().stream().sorted().forEach(id -> {
					HudResourceKey key = new HudResourceKey(HudResourceKind.LAYOUT, id);
					HudLayoutDefinition layout = this.layouts.get(id);
					List<Problem> diagnostics = validateClosure(key, Optional.of(layout.surface()));
					if (!diagnostics.isEmpty()) {
						failures.put(key, diagnostics);
					}
				});
				if (failures.isEmpty()) {
					return;
				}
				failures.forEach((key, diagnostics) ->
					disable(key, document(key), diagnostics, diagnostics.size())
				);
			}
		}

		private List<Problem> validateClosure(
			final HudResourceKey start,
			final Optional<Surface> expectedSurface
		) {
			Source source = origin(start);
			if (source == null) {
				return List.of();
			}
			ClosureWalk walk = new ClosureWalk(source, expectedSurface);
			walk.visit(start, 0, 0, new LinkedHashSet<>());
			return walk.diagnostics();
		}

		private final class ClosureWalk {
			private final Source source;
			private final Optional<Surface> expectedSurface;
			private final List<Problem> diagnostics = new ArrayList<>();
			private int expandedComponents;

			private ClosureWalk(final Source source, final Optional<Surface> expectedSurface) {
				this.source = source;
				this.expectedSurface = expectedSurface;
			}

			private void visit(
				final HudResourceKey key,
				final int componentDepth,
				final int degradationDepth,
				final LinkedHashSet<HudResourceKey> active
			) {
				if (!this.diagnostics.isEmpty()) {
					return;
				}
				if (!active.add(key)) {
					this.diagnostics.add(problem(
						this.source,
						"HUD_REFERENCE_CYCLE",
						"",
						"HUD reference cycle reaches " + serialized(key.kind()) + " " + key.id()
					));
					return;
				}

				if (key.kind() == HudResourceKind.COMPONENT) {
					HudComponentDefinition component = components.get(key.id());
					if (component == null) {
						missing(key);
						active.remove(key);
						return;
					}
					if (componentDepth > HudDefinitions.MAX_COMPONENT_DEPTH) {
						this.diagnostics.add(problem(
							this.source,
							"HUD_LAYOUT_UNRESOLVABLE",
							"",
							"component depth exceeds " + HudDefinitions.MAX_COMPONENT_DEPTH
						));
						active.remove(key);
						return;
					}
					this.expandedComponents++;
					if (this.expandedComponents > HudDefinitions.MAX_EXPANDED_COMPONENTS) {
						this.diagnostics.add(problem(
							this.source,
							"HUD_LAYOUT_UNRESOLVABLE",
							"",
							"expanded component count exceeds " + HudDefinitions.MAX_EXPANDED_COMPONENTS
						));
						active.remove(key);
						return;
					}
					for (Identifier child : component.body().componentReferences().stream().sorted().toList()) {
						visit(
							new HudResourceKey(HudResourceKind.COMPONENT, child),
							componentDepth + 1,
							degradationDepth,
							active
						);
					}
					for (Identifier degradation : componentDegradationLayouts(component)) {
						visit(
							new HudResourceKey(HudResourceKind.LAYOUT, degradation),
							componentDepth,
							degradationDepth + 1,
							active
						);
					}
				} else if (key.kind() == HudResourceKind.LAYOUT) {
					HudLayoutDefinition layout = layouts.get(key.id());
					if (layout == null) {
						missing(key);
						active.remove(key);
						return;
					}
					if (degradationDepth > HudDefinitions.MAX_LAYOUT_DEGRADATION_DEPTH) {
						this.diagnostics.add(problem(
							this.source,
							"HUD_LAYOUT_UNRESOLVABLE",
							"/degradation_layout",
							"layout degradation depth exceeds " + HudDefinitions.MAX_LAYOUT_DEGRADATION_DEPTH
						));
						active.remove(key);
						return;
					}
					if (this.expectedSurface.isPresent() && layout.surface() != this.expectedSurface.orElseThrow()) {
						this.diagnostics.add(problem(
							this.source,
							"HUD_LAYOUT_UNRESOLVABLE",
							"/degradation_layout",
							"referenced layout " + layout.id() + " uses surface "
								+ serialized(layout.surface()) + " instead of "
								+ serialized(this.expectedSurface.orElseThrow())
						));
						active.remove(key);
						return;
					}
					List<Identifier> roots = new ArrayList<>();
					roots.add(layout.root());
					layout.compactRoot().ifPresent(roots::add);
					layout.summaryRoot().ifPresent(roots::add);
					for (Identifier root : roots) {
						visit(
							new HudResourceKey(HudResourceKind.COMPONENT, root),
							1,
							degradationDepth,
							active
						);
					}
					layout.degradationLayout().ifPresent(degradation -> visit(
						new HudResourceKey(HudResourceKind.LAYOUT, degradation),
						componentDepth,
						degradationDepth + 1,
						active
					));
				}
				active.remove(key);
			}

			private void missing(final HudResourceKey key) {
				this.diagnostics.add(problem(
					this.source,
					"HUD_REFERENCE_MISSING",
					"",
					"referenced " + serialized(key.kind()) + " is missing or invalid: " + key.id()
				));
			}

			private List<Problem> diagnostics() {
				return List.copyOf(this.diagnostics);
			}
		}

		private List<Identifier> componentDegradationLayouts(
			final HudComponentDefinition component
		) {
			Set<Identifier> result = new LinkedHashSet<>();
			for (HudBinding binding : component.bindings().values()) {
				binding.onMissing().degradationLayout().ifPresent(result::add);
			}
			if (component.body() instanceof ImageBody image) {
				image.onAssetMissing().degradationLayout().ifPresent(result::add);
			}
			return result.stream().sorted().toList();
		}

		private void validateProfiles() {
			for (Identifier id : this.profiles.keySet().stream().sorted().toList()) {
				HudProfileDefinition profile = this.profiles.get(id);
				HudResourceKey key = new HudResourceKey(HudResourceKind.PROFILE, id);
				List<Problem> diagnostics = new ArrayList<>();
				Optional<Identifier> defaultLayout = profile.defaultLayout().filter(layout ->
					validSurface(layout, Surface.DOCK, key, "/default_layout", diagnostics)
				);
				List<HudRoute> routes = new ArrayList<>();
				for (int index = 0; index < profile.routes().size(); index++) {
					HudRoute route = profile.routes().get(index);
					if (validSurface(
						route.layout(),
						Surface.DOCK,
						key,
						"/routes/" + index + "/layout",
						diagnostics
					)) {
						routes.add(route);
					}
				}

				for (int left = 0; left < routes.size(); left++) {
					for (int right = left + 1; right < routes.size(); right++) {
						HudRoute first = routes.get(left);
						HudRoute second = routes.get(right);
						if (
							first.tier() == second.tier()
								&& first.priority() == second.priority()
								&& conditionsMayOverlap(first.when(), second.when())
								&& audiencesMayOverlap(first.audience(), second.audience())
						) {
							Problem conflict = problem(
								origin(key),
								"HUD_ROUTE_AMBIGUOUS",
								"/routes",
								"routes " + first.id() + " and " + second.id()
									+ " may tie at tier " + serialized(first.tier())
									+ " and priority " + first.priority()
							);
							disable(key, document(key), List.of(conflict), 1);
							routes = null;
							break;
						}
					}
					if (routes == null) {
						break;
					}
				}
				if (routes == null) {
					continue;
				}
				if (!diagnostics.isEmpty()) {
					this.retainedDiagnostics.put(key, List.copyOf(diagnostics));
				}
				if (!defaultLayout.equals(profile.defaultLayout()) || routes.size() != profile.routes().size()) {
					this.profiles.put(id, new HudProfileDefinition(
						profile.id(),
						defaultLayout,
						routes,
						profile.clientPolicy(),
						profile.canonicalDocument(),
						profile.sha256()
					));
				}
			}
		}

		private void validateCountdowns() {
			for (Identifier id : this.countdowns.keySet().stream().sorted().toList()) {
				CountdownDefinition countdown = this.countdowns.get(id);
				HudResourceKey key = new HudResourceKey(HudResourceKind.COUNTDOWN, id);
				List<Problem> diagnostics = new ArrayList<>();
				if (!validSurface(countdown.layout(), Surface.COUNTDOWN, key, "/layout", diagnostics)) {
					disable(key, document(key), diagnostics, diagnostics.size());
					continue;
				}

				boolean changed = false;
				List<CountdownCheckpoint> checkpoints = new ArrayList<>();
				for (int index = 0; index < countdown.checkpoints().size(); index++) {
					CountdownCheckpoint checkpoint = countdown.checkpoints().get(index);
					Optional<Identifier> cue = checkpoint.cue();
					if (cue.isPresent()) {
						MessageCueDefinition definition = core.messageCatalog().messageCues().get(cue.orElseThrow());
						String reason = null;
						if (definition == null) {
							reason = "checkpoint cue is missing or invalid";
						} else if (definition.nodes().stream().anyMatch(CallbackNode.class::isInstance)) {
							reason = "checkpoint cue contains a server callback";
						} else if (definition.history().isPresent()) {
							reason = "checkpoint cue writes message history";
						}
						if (reason != null) {
							diagnostics.add(problem(
								origin(key),
								"HUD_RESOURCE_INVALID",
								"/checkpoints/" + index + "/cue",
								reason + ": " + cue.orElseThrow()
							));
							cue = Optional.empty();
							changed = true;
						}
					}
					checkpoints.add(new CountdownCheckpoint(
						checkpoint.id(),
						checkpoint.remainingTicks(),
						checkpoint.sound(),
						cue
					));
				}

				Map<CountdownCallbackSlot, List<CountdownCallback>> callbacks = new EnumMap<>(
					CountdownCallbackSlot.class
				);
				List<Problem> requiredFailures = new ArrayList<>();
				for (CountdownCallbackSlot slot : CountdownCallbackSlot.values()) {
					List<CountdownCallback> accepted = new ArrayList<>();
					List<CountdownCallback> declared = countdown.callbacks().getOrDefault(slot, List.of());
					for (int index = 0; index < declared.size(); index++) {
						CountdownCallback callback = declared.get(index);
						if (this.functions.contains(callback.function())) {
							accepted.add(callback);
							continue;
						}
						Problem missing = problem(
							origin(key),
							"HUD_REFERENCE_MISSING",
							"/callbacks/" + serialized(slot) + "/" + index + "/function",
							"countdown callback function is missing: " + callback.function()
						);
						if (callback.required()) {
							requiredFailures.add(missing);
						} else {
							diagnostics.add(missing);
							changed = true;
						}
					}
					callbacks.put(slot, List.copyOf(accepted));
				}
				if (!requiredFailures.isEmpty()) {
					disable(key, document(key), requiredFailures, requiredFailures.size());
					continue;
				}
				if (!diagnostics.isEmpty()) {
					this.retainedDiagnostics.put(key, List.copyOf(diagnostics));
				}
				if (changed) {
					this.countdowns.put(id, new CountdownDefinition(
						countdown.id(),
						countdown.name(),
						countdown.durationTicks(),
						countdown.layout(),
						countdown.displayAudience(),
						countdown.disconnect(),
						countdown.restrictions(),
						checkpoints,
						callbacks,
						countdown.canonicalDocument(),
						countdown.sha256()
					));
				}
			}
		}

		private void validateGames() {
			for (GameDefinition game : this.core.games().values().stream()
				.sorted(Comparator.comparing(GameDefinition::id))
				.toList()) {
				List<Problem> diagnostics = new ArrayList<>();
				game.hudProfile().ifPresent(profile -> {
					HudResourceKey key = new HudResourceKey(HudResourceKind.PROFILE, profile);
					if (!this.profiles.containsKey(profile)) {
						diagnostics.add(gameProblem(
							game.id(),
							"HUD_RESOURCE_INVALID",
							"/hud_profile",
							"HUD profile is missing or invalid: " + profile
						));
					} else {
						diagnostics.addAll(this.retainedDiagnostics.getOrDefault(key, List.of()));
					}
				});
				game.openingCountdown().ifPresent(reference -> {
					Identifier id = reference.definition();
					HudResourceKey key = new HudResourceKey(HudResourceKind.COUNTDOWN, id);
					if (this.countdowns.containsKey(id)) {
						diagnostics.addAll(this.retainedDiagnostics.getOrDefault(key, List.of()));
						return;
					}
					Problem invalid = gameProblem(
						game.id(),
						reference.required() ? "COUNTDOWN_REQUIRED_INVALID" : "HUD_RESOURCE_INVALID",
						"/opening_countdown/definition",
						"opening countdown is missing or invalid: " + id
					);
					diagnostics.add(invalid);
					if (reference.required()) {
						List<Problem> failureDiagnostics = new ArrayList<>();
						failureDiagnostics.add(invalid);
						DisabledHudDefinition disabledCountdown = this.disabled.get(key);
						if (disabledCountdown != null) {
							failureDiagnostics.addAll(disabledCountdown.diagnostics());
						}
						this.requiredFailures.put(
							game.id(),
							new RequiredCountdownFailure(game.id(), id, failureDiagnostics)
						);
					}
				});
				if (!diagnostics.isEmpty()) {
					this.gameDiagnostics.put(game.id(), List.copyOf(diagnostics));
				}
			}
		}

		private final Map<HudResourceKey, List<Problem>> retainedDiagnostics = new LinkedHashMap<>();

		private boolean validSurface(
			final Identifier layoutId,
			final Surface expected,
			final HudResourceKey owner,
			final String pointer,
			final List<Problem> diagnostics
		) {
			HudLayoutDefinition layout = this.layouts.get(layoutId);
			if (layout == null) {
				diagnostics.add(problem(
					origin(owner),
					"HUD_REFERENCE_MISSING",
					pointer,
					"referenced layout is missing or invalid: " + layoutId
				));
				return false;
			}
			if (layout.surface() != expected) {
				diagnostics.add(problem(
					origin(owner),
					"HUD_LAYOUT_UNRESOLVABLE",
					pointer,
					"layout " + layoutId + " uses surface " + serialized(layout.surface())
						+ " instead of " + serialized(expected)
				));
				return false;
			}
			return true;
		}

		private boolean conditionsMayOverlap(
			final HudCondition left,
			final HudCondition right
		) {
			if (!left.simpleConjunction() || !right.simpleConjunction()) {
				return false;
			}
			return setsMayOverlap(left.games(), right.games())
				&& setsMayOverlap(left.phases(), right.phases())
				&& setsMayOverlap(left.tasks(), right.tasks())
				&& setsMayOverlap(left.taskKinds(), right.taskKinds())
				&& setsMayOverlap(left.taskStatuses(), right.taskStatuses())
				&& setsMayOverlap(left.roles(), right.roles())
				&& setsMayOverlap(left.teams(), right.teams())
				&& setsMayOverlap(left.lifeStates(), right.lifeStates())
				&& optionMayOverlap(left.host(), right.host())
				&& optionMayOverlap(left.online(), right.online());
		}

		private boolean audiencesMayOverlap(
			final Optional<HudAudience> left,
			final Optional<HudAudience> right
		) {
			if (left.isEmpty() || right.isEmpty()) {
				return true;
			}
			for (HudAudienceSelector first : left.orElseThrow().selectors()) {
				for (HudAudienceSelector second : right.orElseThrow().selectors()) {
					if (
						setsMayOverlap(first.roles(), second.roles())
							&& setsMayOverlap(first.teams(), second.teams())
							&& setsMayOverlap(first.lifeStates(), second.lifeStates())
					) {
						return true;
					}
				}
			}
			return false;
		}

		private static <T> boolean setsMayOverlap(final Set<T> left, final Set<T> right) {
			if (left.isEmpty() || right.isEmpty()) {
				return true;
			}
			return left.stream().anyMatch(right::contains);
		}

		private static boolean optionMayOverlap(
			final Optional<Boolean> left,
			final Optional<Boolean> right
		) {
			return left.isEmpty() || right.isEmpty() || left.equals(right);
		}

		private Optional<SourceDocument> document(final HudResourceKey key) {
			SourceDocument value = this.documents.get(documentKey(key));
			if (value != null) {
				return Optional.of(value);
			}
			DisabledHudDefinition previous = this.disabled.get(key);
			return previous == null ? Optional.empty() : previous.sourceDocument();
		}

		private Source origin(final HudResourceKey key) {
			return this.origins.get(key);
		}

		private void disable(
			final HudResourceKey key,
			final Optional<SourceDocument> sourceDocument,
			final List<Problem> diagnostics,
			final int totalDiagnosticCount
		) {
			switch (key.kind()) {
				case COMPONENT -> this.components.remove(key.id());
				case LAYOUT -> this.layouts.remove(key.id());
				case PROFILE -> this.profiles.remove(key.id());
				case COUNTDOWN -> this.countdowns.remove(key.id());
			}
			List<Problem> retained = diagnostics.stream()
				.limit(HudDefinitionParser.MAX_ISSUE_DETAILS)
				.toList();
			this.disabled.put(
				key,
				new DisabledHudDefinition(
					key,
					sourceDocument,
					retained,
					Math.max(totalDiagnosticCount, retained.size())
				)
			);
		}

		private Problem problem(final Source source, final Issue issue) {
			return problem(source, issue.code(), issue.path(), issue.message());
		}

		private Problem problem(
			final Source source,
			final String code,
			final String pointer,
			final String message
		) {
			if (source == null) {
				throw new IllegalStateException("HUD diagnostic has no source");
			}
			return new Problem(
				code,
				source.type(),
				source.id(),
				source.resource(),
				source.sourcePack(),
				pointer,
				message
			);
		}

		private Problem gameProblem(
			final Identifier game,
			final String code,
			final String pointer,
			final String message
		) {
			SourceDocument document = this.core.sourceDocuments().get(
				new DocumentKey(DefinitionType.GAME, game)
			);
			Identifier resource = document == null
				? Identifier.fromNamespaceAndPath(
					game.getNamespace(),
					"pixel_tzz_pro/games/" + game.getPath() + ".json"
				)
				: document.resource();
			return new Problem(
				code,
				DefinitionType.GAME,
				game,
				resource,
				document == null ? "unknown" : document.sourcePack(),
				pointer,
				message
			);
		}

		private static HudResourceKey key(final Source source) {
			HudResourceKind kind = kind(source.type());
			if (kind == null) {
				throw new IllegalArgumentException("source is not a HUD resource: " + source.type());
			}
			return new HudResourceKey(kind, source.id());
		}

		private static DocumentKey documentKey(final HudResourceKey key) {
			return new DocumentKey(type(key.kind()), key.id());
		}

		private static HudResourceKind kind(final DefinitionType type) {
			return switch (type) {
				case HUD_COMPONENT -> HudResourceKind.COMPONENT;
				case HUD_LAYOUT -> HudResourceKind.LAYOUT;
				case HUD_PROFILE -> HudResourceKind.PROFILE;
				case COUNTDOWN -> HudResourceKind.COUNTDOWN;
				default -> null;
			};
		}

		private static DefinitionType type(final HudResourceKind kind) {
			return switch (kind) {
				case COMPONENT -> DefinitionType.HUD_COMPONENT;
				case LAYOUT -> DefinitionType.HUD_LAYOUT;
				case PROFILE -> DefinitionType.HUD_PROFILE;
				case COUNTDOWN -> DefinitionType.COUNTDOWN;
			};
		}

		private static int maximum(final HudResourceKind kind) {
			return switch (kind) {
				case COMPONENT -> HudDefinitions.MAX_COMPONENTS;
				case LAYOUT -> HudDefinitions.MAX_LAYOUTS;
				case PROFILE -> HudDefinitions.MAX_PROFILES;
				case COUNTDOWN -> HudDefinitions.MAX_COUNTDOWNS;
			};
		}

		private static String serialized(final Enum<?> value) {
			return value.name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
