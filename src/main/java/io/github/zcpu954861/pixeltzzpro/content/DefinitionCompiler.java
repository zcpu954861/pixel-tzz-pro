package io.github.zcpu954861.pixeltzzpro.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ApplyTiming;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignLifeStateOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignRoleOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignTeamOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarColor;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarCompletionFeedback;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarStyle;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchCase;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChangeStateNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceRoute;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Confirmation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ConfirmNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.EditPolicy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ExclusiveChoiceDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ExclusiveChoiceOption;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldConstraints;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldMigrationStrategy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FunctionNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.HostBossBar;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.NodeScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelSurface;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ReadinessDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ReservationScope;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StateAxis;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabColorMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabPrefixMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetFilter;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetSelection;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TransitionPhaseOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.WaitPlayersNode;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.CandidateVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.FutureVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.HistoryVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.IntermissionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.LiveVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.PlayerCallback;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RequiredFieldRequirement;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultSemantic;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.ResultStyle;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCallbacks;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskCompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventPolicy;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskRoute;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticType;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticWrite;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskTimeline;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TransitionTiming;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser.Issue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BoundText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ComparisonCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConcatenatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ExistsCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LiteralValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionProperty;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NotCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PlayerHeadContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ProgressContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextTemplate;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TranslatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiAction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;

/**
 * Pure compiler from resolved data-pack JSON to one validated immutable definition snapshot.
 *
 * <p>It has no server lifecycle dependency, so the same trust-boundary checks run in the build
 * self-check and during a live {@code /reload}.
 */
public final class DefinitionCompiler {
	public static final int MAX_DEFINITIONS = 1_024;
	public static final int MAX_DEFINITION_CHARACTERS = 262_144;
	public static final int MAX_TOTAL_DEFINITION_CHARACTERS = 8_388_608;
	public static final int MAX_FLOW_NODES = 256;
	public static final int MAX_TASKS_PER_GAME = 256;
	public static final int MAX_TASK_RESULTS = 32;
	public static final int MAX_TASK_EVENTS = 64;
	public static final int MAX_TASK_STATISTICS = 64;
	public static final int MAX_TASK_CALLBACK_FIELDS = 32;
	public static final int MAX_EXCLUSIVE_OPTIONS = 128;
	public static final int MAX_REPEATABLE_EVENT_RECORDS = 4_096;
	public static final long MAX_TASK_DURATION_TICKS = 51_840_000L;
	public static final long MAX_INTERMISSION_DURATION_TICKS = 12_096_000L;
	public static final int MAX_TEXT_JSON_LENGTH = 8_192;
	public static final int MAX_JSON_DEPTH = 64;
	public static final int MAX_DIAGNOSTIC_DETAILS = 100;
	private static final int MAX_SHORT_STRING_LENGTH = 1_024;
	private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern CALLBACK_MACRO = Pattern.compile("[a-z][a-z0-9_]{0,31}");
	private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Set<String> BUILT_IN_UI_STYLES = Set.of(
		"primary_button",
		"secondary_button",
		"navigation_button",
		"danger_button",
		"card",
		"text",
		"progress",
		"image",
		"player_head",
		"field_input"
	);
	private static final Set<String> BUILT_IN_UI_MOTIONS = Set.copyOf(
		BuiltInUiResources.defaultTheme().motions().keySet()
	);
	private static final Set<String> BUILT_IN_UI_SOUNDS = Set.copyOf(
		BuiltInUiResources.defaultTheme().soundCues().keySet()
	);

	private DefinitionCompiler() {
	}

	public enum DefinitionType {
		GAME("games"),
		ROLE("roles"),
		TEAM("teams"),
		LIFE_STATE("life_states"),
		PHASE("phases"),
		FIELD("fields"),
		FLOW("flows"),
		TASK("tasks"),
		PANEL_ACTION("panel_actions"),
		PAGE("pages"),
		THEME("themes");

		private final String directory;

		DefinitionType(final String directory) {
			this.directory = directory;
		}

		public String directory() {
			return this.directory;
		}

		public static Optional<DefinitionType> byDirectory(final String directory) {
			for (DefinitionType type : values()) {
				if (type.directory.equals(directory)) {
					return Optional.of(type);
				}
			}
			return Optional.empty();
		}
	}

	public record Source(
		DefinitionType type,
		Identifier id,
		Identifier resource,
		String sourcePack,
		String json
	) {
		public Source {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(resource, "resource");
			sourcePack = sourcePack == null ? "unknown" : sourcePack;
			Objects.requireNonNull(json, "json");
		}
	}

	public record Problem(
		String code,
		DefinitionType type,
		Identifier definitionId,
		Identifier resource,
		String sourcePack,
		String pointer,
		String message
	) {
		public String summary() {
			return this.code
				+ " "
				+ this.resource
				+ this.pointer
				+ ": "
				+ this.message;
		}
	}

	public record Compilation(
		Optional<DefinitionSnapshot> snapshot,
		List<Problem> problems,
		int totalProblemCount
	) {
		public Compilation(
			final Optional<DefinitionSnapshot> snapshot,
			final List<Problem> problems
		) {
			this(snapshot, problems, problems.size());
		}

		public Compilation {
			snapshot = snapshot == null ? Optional.empty() : snapshot;
			problems = List.copyOf(problems);
			if (totalProblemCount < problems.size()) {
				throw new IllegalArgumentException("total problem count cannot be smaller than retained details");
			}
		}

		public boolean valid() {
			return this.snapshot.isPresent() && this.totalProblemCount == 0;
		}

		public boolean problemsTruncated() {
			return this.totalProblemCount > this.problems.size();
		}
	}

	public static Compilation compile(
		final List<Source> sources,
		final Set<Identifier> functions,
		final Set<Identifier> predicates
	) {
		Compiler compiler = new Compiler(functions, predicates);
		return compiler.compile(sources);
	}

	private static final class Compiler {
		private final Set<Identifier> functions;
		private final Set<Identifier> predicates;
		private final ProblemCollector problems = new ProblemCollector();
		private final Map<DefinitionKey, Source> origins = new HashMap<>();
		private final Map<Identifier, GameDefinition> games = new LinkedHashMap<>();
		private final Map<Identifier, RoleDefinition> roles = new LinkedHashMap<>();
		private final Map<Identifier, TeamDefinition> teams = new LinkedHashMap<>();
		private final Map<Identifier, LifeStateDefinition> lifeStates = new LinkedHashMap<>();
		private final Map<Identifier, PhaseDefinition> phases = new LinkedHashMap<>();
		private final Map<Identifier, FieldDefinition> fields = new LinkedHashMap<>();
		private final Map<Identifier, FlowDefinition> flows = new LinkedHashMap<>();
		private final Map<Identifier, TaskDefinition> tasks = new LinkedHashMap<>();
		private final Map<Identifier, PanelActionDefinition> panelActions = new LinkedHashMap<>();
		private final Map<Identifier, PageDefinition> pages = new LinkedHashMap<>();
		private final Map<Identifier, ThemeDefinition> themes = new LinkedHashMap<>();
		private final Map<DocumentKey, SourceDocument> sourceDocuments = new LinkedHashMap<>();

		private Compiler(final Set<Identifier> functions, final Set<Identifier> predicates) {
			this.functions = Set.copyOf(functions);
			this.predicates = Set.copyOf(predicates);
		}

		private Compilation compile(final List<Source> sources) {
			if (sources.size() > MAX_DEFINITIONS) {
				Source source = sources.isEmpty() ? null : sources.getFirst();
				add(
					source,
					"RESOURCE_LIMIT",
					"",
					"definition count " + sources.size() + " exceeds " + MAX_DEFINITIONS
				);
				return new Compilation(Optional.empty(), this.problems, this.problems.totalCount());
			}
			long totalCharacters = 0L;
			for (Source source : sources) {
				if (source.json().length() > MAX_DEFINITION_CHARACTERS) {
					add(
						source,
						"RESOURCE_LIMIT",
						"",
						"definition exceeds " + MAX_DEFINITION_CHARACTERS + " characters"
					);
					return new Compilation(Optional.empty(), this.problems, this.problems.totalCount());
				}
				totalCharacters += source.json().length();
				if (totalCharacters > MAX_TOTAL_DEFINITION_CHARACTERS) {
					add(
						source,
						"RESOURCE_LIMIT",
						"",
						"total definition size exceeds " + MAX_TOTAL_DEFINITION_CHARACTERS + " characters"
					);
					return new Compilation(Optional.empty(), this.problems, this.problems.totalCount());
				}
			}

			for (Source source : sources) {
				DefinitionKey key = new DefinitionKey(source.type(), source.id());
				Source previous = this.origins.putIfAbsent(key, source);
				if (previous != null) {
					add(
						source,
						"DUPLICATE_DEFINITION",
						"",
						"definition already supplied by " + previous.resource()
					);
					continue;
				}
				parse(source);
			}

			if (this.problems.isEmpty()) {
				validateReferences();
			}
			if (!this.problems.isEmpty()) {
				return new Compilation(Optional.empty(), this.problems, this.problems.totalCount());
			}

			return new Compilation(
				Optional.of(
					new DefinitionSnapshot(
						0L,
						this.games,
						this.roles,
						this.teams,
						this.lifeStates,
						this.phases,
						this.fields,
						this.flows,
						this.tasks,
						this.panelActions,
						this.pages,
						this.themes,
						this.sourceDocuments,
						this.functions,
						this.predicates,
						Map.of()
					)
				),
				List.of()
			);
		}

		private void parse(final Source source) {
			if (source.type() == DefinitionType.PAGE || source.type() == DefinitionType.THEME) {
				parseUiDefinition(source);
				return;
			}
			JsonElement root;
			try {
				root = parseStrict(source.json());
			} catch (IOException | JsonParseException | NumberFormatException error) {
				add(source, "JSON_SYNTAX", "", error.getMessage());
				return;
			}
			if (!root.isJsonObject()) {
				add(source, "TYPE_MISMATCH", "", "definition root must be an object");
				return;
			}

			ObjectReader reader = new ObjectReader(source, "", root.getAsJsonObject(), this.problems);
			Integer formatVersion = reader.requiredInt("format_version");
			if (formatVersion != null && formatVersion != GameDefinitions.CURRENT_FORMAT_VERSION) {
				add(
					source,
					"UNSUPPORTED_FORMAT",
					"/format_version",
					"expected " + GameDefinitions.CURRENT_FORMAT_VERSION + " but found " + formatVersion
				);
			}

			switch (source.type()) {
				case GAME -> parseGame(source, reader);
				case ROLE -> parseRole(source, reader);
				case TEAM -> parseTeam(source, reader);
				case LIFE_STATE -> parseLifeState(source, reader);
				case PHASE -> parsePhase(source, reader);
				case FIELD -> parseField(source, reader);
				case FLOW -> parseFlow(source, reader);
				case TASK -> parseTask(source, reader);
				case PANEL_ACTION -> parsePanelAction(source, reader);
				case PAGE, THEME -> throw new IllegalStateException("UI definitions use the shared UI parser");
			}
			reader.finish();
			String canonicalJson = canonical(root);
			DocumentKey key = new DocumentKey(source.type(), source.id());
			this.sourceDocuments.put(
				key,
				new SourceDocument(
					key,
					source.resource(),
					source.sourcePack(),
					canonicalJson,
					sha256(canonicalJson)
				)
			);
		}

		private void parseUiDefinition(final Source source) {
			if (source.type() == DefinitionType.PAGE) {
				ParseResult<PageDefinition> result = UiDefinitionParser.parsePage(source.id(), source.json());
				reportUiIssues(source, result.issues());
				result.value().ifPresent(
					page -> {
						if (canonicalDocumentFits(page.canonicalDocument())) {
							this.pages.put(source.id(), page);
							storeUiDocument(source, page.canonicalDocument(), page.sha256());
						} else {
							add(
								source,
								"RESOURCE_LIMIT",
								"",
								"canonical page document exceeds "
									+ UiDefinitions.MAX_CANONICAL_DOCUMENT_BYTES
									+ " UTF-8 bytes"
							);
						}
					}
				);
				return;
			}
			ParseResult<ThemeDefinition> result = UiDefinitionParser.parseTheme(source.id(), source.json());
			reportUiIssues(source, result.issues());
			result.value().ifPresent(
				theme -> {
					if (canonicalDocumentFits(theme.canonicalDocument())) {
						this.themes.put(source.id(), theme);
						storeUiDocument(source, theme.canonicalDocument(), theme.sha256());
					} else {
						add(
							source,
							"RESOURCE_LIMIT",
							"",
							"canonical theme document exceeds "
								+ UiDefinitions.MAX_CANONICAL_DOCUMENT_BYTES
								+ " UTF-8 bytes"
						);
					}
				}
			);
		}

		private void storeUiDocument(
			final Source source,
			final String canonicalJson,
			final String sha256
		) {
			DocumentKey key = new DocumentKey(source.type(), source.id());
			this.sourceDocuments.put(
				key,
				new SourceDocument(
					key,
					source.resource(),
					source.sourcePack(),
					canonicalJson,
					sha256
				)
			);
		}

		private static boolean canonicalDocumentFits(final String document) {
			return document.getBytes(StandardCharsets.UTF_8).length
				<= UiDefinitions.MAX_CANONICAL_DOCUMENT_BYTES;
		}

		private void reportUiIssues(final Source source, final List<Issue> issues) {
			for (Issue issue : issues) {
				add(source, issue.code(), issue.pointer(), issue.message());
			}
		}

		private void parseGame(final Source source, final ObjectReader reader) {
			Integer apiVersion = reader.requiredInt("api_version");
			Integer contentVersion = reader.requiredInt("content_version");
			RichText name = reader.requiredText("name");
			Identifier initialPhase = reader.requiredIdentifier("initial_phase");
			Identifier defaultRole = reader.requiredIdentifier("default_role");
			Identifier defaultLifeState = reader.requiredIdentifier("default_life_state");
			Optional<TaskTimeline> taskTimeline = parseTaskTimeline(reader);
			Optional<ReadinessDefinition> readiness = parseReadiness(reader);
			if (
				apiVersion != null
					&& (
						apiVersion < GameDefinitions.MIN_API_VERSION
							|| apiVersion > GameDefinitions.CURRENT_API_VERSION
					)
			) {
				add(
					source,
					"UNSUPPORTED_API",
					"/api_version",
					"expected "
						+ GameDefinitions.MIN_API_VERSION
						+ ".."
						+ GameDefinitions.CURRENT_API_VERSION
						+ " but found "
						+ apiVersion
				);
			}
			if (
				apiVersion != null
					&& apiVersion < 2
					&& taskTimeline.isPresent()
			) {
				add(
					source,
					"UNSUPPORTED_API",
					"/task_timeline",
					"task_timeline requires api_version 2"
				);
			}
			if (contentVersion != null && contentVersion < 1) {
				add(source, "OUT_OF_RANGE", "/content_version", "content version must be positive");
			}
			if (
				apiVersion != null
					&& apiVersion >= GameDefinitions.MIN_API_VERSION
					&& apiVersion <= GameDefinitions.CURRENT_API_VERSION
					&& contentVersion != null
					&& contentVersion > 0
					&& name != null
					&& initialPhase != null
					&& defaultRole != null
					&& defaultLifeState != null
			) {
				this.games.put(
					source.id(),
					new GameDefinition(
						source.id(),
						apiVersion,
						contentVersion,
						name,
						initialPhase,
						defaultRole,
						defaultLifeState,
						taskTimeline,
						readiness
					)
				);
			}
		}

		private Optional<ReadinessDefinition> parseReadiness(final ObjectReader parent) {
			JsonObject object = parent.optionalObject("readiness");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("readiness", object);
			Identifier phase = reader.requiredIdentifier("phase");
			Identifier action = reader.requiredIdentifier("action");
			boolean disconnectInvalidates = reader.optionalBoolean(
				"disconnect_invalidates",
				true
			);
			boolean hostCanForce = reader.optionalBoolean("host_can_force", false);
			reader.finish();
			return phase == null || action == null
				? Optional.empty()
				: Optional.of(
					new ReadinessDefinition(
						phase,
						action,
						disconnectInvalidates,
						hostCanForce
					)
				);
		}

		private Optional<TaskTimeline> parseTaskTimeline(final ObjectReader parent) {
			JsonObject object = parent.optionalObject("task_timeline");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("task_timeline", object);
			Identifier initialTask = reader.requiredIdentifier("initial_task");
			boolean pauseWhenHostOffline = reader.optionalBoolean("pause_when_host_offline", false);
			Identifier approvalPhase = reader.requiredIdentifier("approval_phase");
			Identifier startPhase = reader.requiredIdentifier("start_phase");
			JsonArray requirements = reader.optionalArray("pre_start_requirements");
			List<RequiredFieldRequirement> parsedRequirements = new ArrayList<>();
			if (requirements != null) {
				for (int index = 0; index < requirements.size(); index++) {
					JsonElement element = requirements.get(index);
					String pointer = reader.pointer("/pre_start_requirements/" + index);
					if (!element.isJsonObject()) {
						add(parent.source, "TYPE_MISMATCH", pointer, "pre-start requirement must be an object");
						continue;
					}
					ObjectReader requirementReader = new ObjectReader(
						parent.source,
						pointer,
						element.getAsJsonObject(),
						this.problems
					);
					String type = requirementReader.requiredString("type");
					JsonObject audienceObject = requirementReader.requiredObject("audience");
					Audience audience = audienceObject == null
						? null
						: parseAudienceObject(
							requirementReader,
							"audience",
							audienceObject,
							false
						);
					Identifier field = requirementReader.requiredIdentifier("field");
					requirementReader.finish();
					if (type != null && !"required_field".equals(type)) {
						add(
							parent.source,
							"INVALID_ENUM",
							pointer + "/type",
							"only required_field is supported"
						);
					} else if (audience != null && field != null) {
						parsedRequirements.add(new RequiredFieldRequirement(audience, field));
					}
				}
			}
			reader.finish();
			return initialTask == null || approvalPhase == null || startPhase == null
				? Optional.empty()
				: Optional.of(
					new TaskTimeline(
						initialTask,
						pauseWhenHostOffline,
						approvalPhase,
						startPhase,
						parsedRequirements
					)
				);
		}

		private void parseRole(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			RichText name = reader.requiredText("name");
			Optional<RichText> description = reader.optionalText("description");
			Optional<Identifier> initializationFlow = reader.optionalIdentifier("initialization_flow");
			Set<Identifier> tags = reader.optionalIdentifierSet("tags");
			TabStyle tab = parseTab(reader, true);
			if (game != null && name != null) {
				this.roles.put(
					source.id(),
					new RoleDefinition(
						source.id(),
						game,
						name,
						description,
						initializationFlow,
						tags,
						tab
					)
				);
			}
		}

		private void parseTeam(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			RichText name = reader.requiredText("name");
			Optional<RichText> description = reader.optionalText("description");
			Set<Identifier> allowedRoles = reader.requiredIdentifierSet("allowed_roles");
			Set<Identifier> tags = reader.optionalIdentifierSet("tags");
			TabStyle tab = parseTab(reader, false);
			if (allowedRoles.isEmpty()) {
				add(source, "OUT_OF_RANGE", "/allowed_roles", "a team must allow at least one role");
			}
			if (game != null && name != null && !allowedRoles.isEmpty()) {
				this.teams.put(
					source.id(),
					new TeamDefinition(source.id(), game, name, description, allowedRoles, tags, tab)
				);
			}
		}

		private void parseLifeState(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			RichText name = reader.requiredText("name");
			Optional<RichText> description = reader.optionalText("description");
			Set<Identifier> tags = reader.optionalIdentifierSet("tags");
			TabStyle tab = parseTab(reader, false);
			if (game != null && name != null) {
				this.lifeStates.put(
					source.id(),
					new LifeStateDefinition(source.id(), game, name, description, tags, tab)
				);
			}
		}

		private TabStyle parseTab(final ObjectReader parent, final boolean roleLayer) {
			JsonObject object = parent.optionalObject("tab");
			if (object == null) {
				return new TabStyle(
					Optional.empty(),
					Optional.empty(),
					roleLayer ? Optional.of(0) : Optional.empty(),
					TabPrefixMode.INHERIT,
					TabColorMode.INHERIT
				);
			}
			ObjectReader reader = parent.child("tab", object);
			Optional<RichText> prefix = reader.optionalText("prefix");
			Optional<String> color = reader.optionalString("color");
			Optional<Integer> sortOrder = reader.optionalInteger("sort_order");
			TabPrefixMode prefixMode = reader.optionalEnum(
				"prefix_mode",
				TabPrefixMode.class,
				prefix.isPresent() ? TabPrefixMode.APPEND : TabPrefixMode.INHERIT
			);
			TabColorMode colorMode = reader.optionalEnum(
				"color_mode",
				TabColorMode.class,
				color.isPresent() ? TabColorMode.REPLACE : TabColorMode.INHERIT
			);
			color.ifPresent(value -> {
				if (!HEX_COLOR.matcher(value).matches()) {
					add(parent.source, "INVALID_COLOR", reader.pointer("/color"), "expected #RRGGBB");
				}
			});
			if (sortOrder.filter(value -> value < -10_000 || value > 10_000).isPresent()) {
				add(
					parent.source,
					"OUT_OF_RANGE",
					reader.pointer("/sort_order"),
					"sort_order must be between -10000 and 10000"
				);
			}
			if (prefix.isPresent() == (prefixMode == TabPrefixMode.INHERIT)) {
				add(
					parent.source,
					"INVALID_COMBINATION",
					reader.pointer("/prefix_mode"),
					"inherit requires no prefix; append/replace require prefix"
				);
			}
			if (color.isPresent() == (colorMode == TabColorMode.INHERIT)) {
				add(
					parent.source,
					"INVALID_COMBINATION",
					reader.pointer("/color_mode"),
					"inherit requires no color; replace requires color"
				);
			}
			reader.finish();
			return new TabStyle(
				prefix,
				color,
				sortOrder.isPresent() ? sortOrder : roleLayer ? Optional.of(0) : Optional.empty(),
				prefixMode,
				colorMode
			);
		}

		private void parsePhase(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			RichText name = reader.requiredText("name");
			Set<Identifier> transitions = reader.optionalIdentifierSet("transitions");
			Optional<Identifier> onEnter = reader.optionalIdentifier("on_enter");
			Optional<Identifier> onExit = reader.optionalIdentifier("on_exit");
			if (game != null && name != null) {
				this.phases.put(
					source.id(),
					new PhaseDefinition(source.id(), game, name, transitions, onEnter, onExit)
				);
			}
		}

		private void parseField(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			Integer version = reader.requiredInt("version");
			RichText name = reader.requiredText("name");
			Optional<RichText> description = reader.optionalText("description");
			FieldScope scope = reader.optionalEnum("scope", FieldScope.class, FieldScope.PLAYER);
			FieldType type = reader.requiredEnum("type", FieldType.class);
			boolean required = reader.optionalBoolean("required", false);
			JsonElement defaultValue = reader.optionalElement("default");
			EditPolicy editableBy = reader.optionalEnum("editable_by", EditPolicy.class, EditPolicy.NONE);
			boolean invalidatesReady = reader.optionalBoolean("invalidates_ready", false);
			Set<Identifier> roles = reader.optionalIdentifierSet("roles");
			Set<Identifier> phases = reader.optionalIdentifierSet("phases");
			Optional<Identifier> visibleWhen = reader.optionalIdentifier("visible_when");
			FieldMigrationStrategy migration = reader.optionalEnum(
				"migration",
				FieldMigrationStrategy.class,
				FieldMigrationStrategy.PRESERVE
			);
			Optional<Long> minimum = reader.optionalLong("min");
			Optional<Long> maximum = reader.optionalLong("max");
			Optional<Integer> minimumLength = reader.optionalInteger("min_length");
			Optional<Integer> maximumLength = reader.optionalInteger("max_length");
			JsonArray optionsArray = reader.optionalArray("options");
			Optional<ExclusiveChoiceDefinition> exclusiveChoice = type == FieldType.EXCLUSIVE_CHOICE
				? parseExclusiveChoice(source, reader, optionsArray)
				: Optional.empty();
			List<String> options = type == FieldType.EXCLUSIVE_CHOICE
				? exclusiveChoice.map(ExclusiveChoiceDefinition::values).orElse(List.of())
				: parseStringOptions(source, reader.pointer("/options"), optionsArray);
			Optional<Integer> minimumSelections = reader.optionalInteger("min_selected");
			Optional<Integer> maximumSelections = reader.optionalInteger("max_selected");

			FieldConstraints constraints = new FieldConstraints(
				minimum,
				maximum,
				minimumLength,
				maximumLength,
				options,
				minimumSelections,
				maximumSelections
			);
			if (type != null) {
				validateField(source, scope, type, defaultValue, constraints, exclusiveChoice);
			}
			if (version != null && version < 1) {
				add(source, "OUT_OF_RANGE", "/version", "field version must be positive");
			}
			if (migration == FieldMigrationStrategy.RESET_TO_DEFAULT && defaultValue == null) {
				add(
					source,
					"MISSING_KEY",
					"/default",
					"reset_to_default migration requires an explicit default"
				);
			}
			if (game != null && version != null && version > 0 && name != null && type != null) {
				this.fields.put(
					source.id(),
					new FieldDefinition(
						source.id(),
						game,
						version,
						name,
						description,
						scope,
						type,
						required,
						Optional.ofNullable(defaultValue).map(JsonElement::toString),
						editableBy,
						invalidatesReady,
						roles,
						phases,
						visibleWhen,
						migration,
						constraints,
						exclusiveChoice
					)
				);
			}
		}

		private Optional<ExclusiveChoiceDefinition> parseExclusiveChoice(
			final Source source,
			final ObjectReader reader,
			final JsonArray optionsArray
		) {
			ReservationScope reservationScope = reader.optionalEnum(
				"reservation_scope",
				ReservationScope.class,
				ReservationScope.GAME_INSTANCE
			);
			boolean releaseWhenRoleMismatch = reader.optionalBoolean(
				"release_when_role_mismatch",
				false
			);
			boolean showOccupant = reader.optionalBoolean("show_occupant", true);
			List<ExclusiveChoiceOption> options = new ArrayList<>();
			Set<String> values = new HashSet<>();
			if (optionsArray == null || optionsArray.isEmpty()) {
				add(source, "MISSING_KEY", "/options", "exclusive_choice requires non-empty options");
			} else if (optionsArray.size() > MAX_EXCLUSIVE_OPTIONS) {
				add(
					source,
					"RESOURCE_LIMIT",
					"/options",
					"exclusive choice option count exceeds " + MAX_EXCLUSIVE_OPTIONS
				);
			} else {
				for (int index = 0; index < optionsArray.size(); index++) {
					JsonElement element = optionsArray.get(index);
					String pointer = reader.pointer("/options/" + index);
					if (!element.isJsonObject()) {
						add(source, "TYPE_MISMATCH", pointer, "exclusive choice option must be an object");
						continue;
					}
					ObjectReader optionReader = new ObjectReader(
						source,
						pointer,
						element.getAsJsonObject(),
						this.problems
					);
					String value = optionReader.requiredLocalId("value");
					RichText name = optionReader.requiredText("name");
					Optional<RichText> description = optionReader.optionalText("description");
					Optional<Identifier> icon = optionReader.optionalIdentifier("icon");
					Optional<Identifier> previewPage = optionReader.optionalIdentifier("preview_page");
					optionReader.finish();
					if (value != null && !values.add(value)) {
						add(
							source,
							"DUPLICATE_VALUE",
							pointer + "/value",
							"duplicate exclusive choice value " + value
						);
					} else if (value != null && name != null) {
						options.add(
							new ExclusiveChoiceOption(
								value,
								name,
								description,
								icon,
								previewPage
							)
						);
					}
				}
			}
			return reservationScope == null || options.isEmpty()
				? Optional.empty()
				: Optional.of(
					new ExclusiveChoiceDefinition(
						reservationScope,
						releaseWhenRoleMismatch,
						showOccupant,
						options
					)
				);
		}

		private List<String> parseStringOptions(
			final Source source,
			final String pointer,
			final JsonArray array
		) {
			if (array == null) {
				return List.of();
			}
			List<String> result = new ArrayList<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					add(source, "TYPE_MISMATCH", pointer + "/" + index, "expected a string");
				} else {
					result.add(element.getAsString());
				}
			}
			return List.copyOf(result);
		}

		private void validateField(
			final Source source,
			final FieldScope scope,
			final FieldType type,
			final JsonElement defaultValue,
			final FieldConstraints constraints,
			final Optional<ExclusiveChoiceDefinition> exclusiveChoice
		) {
			if (
				type != FieldType.INTEGER
					&& (constraints.minimum().isPresent() || constraints.maximum().isPresent())
			) {
				add(source, "INVALID_CONSTRAINT", "/min", "min/max are only valid for integer fields");
			}
			if (
				type != FieldType.STRING
					&& (constraints.minimumLength().isPresent() || constraints.maximumLength().isPresent())
			) {
				add(
					source,
					"INVALID_CONSTRAINT",
					"/min_length",
					"length constraints are only valid for string fields"
				);
			}
			if (
				type != FieldType.SINGLE_CHOICE
					&& type != FieldType.MULTI_CHOICE
					&& type != FieldType.EXCLUSIVE_CHOICE
					&& !constraints.options().isEmpty()
			) {
				add(source, "INVALID_CONSTRAINT", "/options", "options are only valid for choice fields");
			}
			if (
				type != FieldType.MULTI_CHOICE
					&& (
						constraints.minimumSelections().isPresent()
							|| constraints.maximumSelections().isPresent()
					)
			) {
				add(
					source,
					"INVALID_CONSTRAINT",
					"/min_selected",
					"selection limits are only valid for multi_choice fields"
				);
			}
			if (
				constraints.minimum().isPresent()
					&& constraints.maximum().isPresent()
					&& constraints.minimum().orElseThrow() > constraints.maximum().orElseThrow()
			) {
				add(source, "OUT_OF_RANGE", "/min", "min must not exceed max");
			}
			if (
				constraints.minimumLength().isPresent()
					&& constraints.maximumLength().isPresent()
					&& constraints.minimumLength().orElseThrow() > constraints.maximumLength().orElseThrow()
			) {
				add(source, "OUT_OF_RANGE", "/min_length", "min_length must not exceed max_length");
			}
			if (
				constraints.minimumLength().filter(value -> value < 0).isPresent()
					|| constraints.maximumLength().filter(value -> value < 0).isPresent()
			) {
				add(source, "OUT_OF_RANGE", "/min_length", "string length constraints must be non-negative");
			}
			if (constraints.options().size() != new HashSet<>(constraints.options()).size()) {
				add(source, "DUPLICATE_VALUE", "/options", "field options must be unique");
			}
			if (
				(
					type == FieldType.SINGLE_CHOICE
						|| type == FieldType.MULTI_CHOICE
						|| type == FieldType.EXCLUSIVE_CHOICE
				)
					&& constraints.options().isEmpty()
			) {
				add(source, "MISSING_KEY", "/options", "choice fields require options");
			}
			if (type == FieldType.MULTI_CHOICE) {
				int minimumSelections = constraints.minimumSelections().orElse(0);
				int maximumSelections = constraints.maximumSelections().orElse(constraints.options().size());
				if (
					minimumSelections < 0
						|| maximumSelections < minimumSelections
						|| maximumSelections > constraints.options().size()
				) {
					add(
						source,
						"OUT_OF_RANGE",
						"/min_selected",
						"selection limits must fit within the option count"
					);
				}
			}
			if (type == FieldType.EXCLUSIVE_CHOICE) {
				if (scope != FieldScope.PLAYER) {
					add(
						source,
						"INVALID_CONSTRAINT",
						"/scope",
						"exclusive_choice must use player scope"
					);
				}
				if (defaultValue != null) {
					add(
						source,
						"INVALID_DEFAULT",
						"/default",
						"exclusive_choice cannot provide a default"
					);
				}
				if (exclusiveChoice.isEmpty()) {
					add(
						source,
						"MISSING_KEY",
						"/options",
						"exclusive_choice requires valid option definitions"
					);
				}
			}
			if (defaultValue == null) {
				return;
			}
			if (type == FieldType.EXCLUSIVE_CHOICE) {
				return;
			}

			boolean typeMatches = switch (type) {
				case BOOLEAN -> defaultValue.isJsonPrimitive() && defaultValue.getAsJsonPrimitive().isBoolean();
				case INTEGER -> isLongIntegral(defaultValue);
				case STRING -> defaultValue.isJsonPrimitive() && defaultValue.getAsJsonPrimitive().isString();
				case IDENTIFIER -> defaultValue.isJsonPrimitive()
					&& defaultValue.getAsJsonPrimitive().isString()
					&& parseIdentifier(defaultValue.getAsString()) != null;
				case SINGLE_CHOICE -> defaultValue.isJsonPrimitive()
					&& defaultValue.getAsJsonPrimitive().isString()
					&& constraints.options().contains(defaultValue.getAsString());
				case MULTI_CHOICE -> defaultValue.isJsonArray()
					&& defaultValue
						.getAsJsonArray()
						.asList()
						.stream()
						.allMatch(
							value -> value.isJsonPrimitive()
								&& value.getAsJsonPrimitive().isString()
								&& constraints.options().contains(value.getAsString())
						);
				case EXCLUSIVE_CHOICE -> throw new IllegalStateException(
					"exclusive choice defaults are rejected before type validation"
				);
			};
			if (!typeMatches) {
				add(source, "INVALID_DEFAULT", "/default", "default does not match field type " + type.name().toLowerCase(Locale.ROOT));
				return;
			}
			if (type == FieldType.INTEGER) {
				long value = defaultValue.getAsLong();
				if (
					constraints.minimum().filter(minimum -> value < minimum).isPresent()
						|| constraints.maximum().filter(maximum -> value > maximum).isPresent()
				) {
					add(source, "INVALID_DEFAULT", "/default", "integer default is outside min/max");
				}
			} else if (type == FieldType.STRING) {
				int length = defaultValue.getAsString().codePointCount(0, defaultValue.getAsString().length());
				if (
					constraints.minimumLength().filter(minimum -> length < minimum).isPresent()
						|| constraints.maximumLength().filter(maximum -> length > maximum).isPresent()
				) {
					add(source, "INVALID_DEFAULT", "/default", "string default is outside length constraints");
				}
			} else if (type == FieldType.MULTI_CHOICE) {
				int selections = defaultValue.getAsJsonArray().size();
				int minimumSelections = constraints.minimumSelections().orElse(0);
				int maximumSelections = constraints.maximumSelections().orElse(constraints.options().size());
				if (selections < minimumSelections || selections > maximumSelections) {
					add(source, "INVALID_DEFAULT", "/default", "multi-choice default violates selection limits");
				}
				Set<String> selected = new HashSet<>();
				for (JsonElement element : defaultValue.getAsJsonArray()) {
					if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
						if (!selected.add(element.getAsString())) {
							add(source, "INVALID_DEFAULT", "/default", "multi-choice default contains duplicates");
							break;
						}
					}
				}
			}
		}

		private void parseFlow(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			Integer version = reader.requiredInt("version");
			RichText name = reader.requiredText("name");
			String entry = reader.requiredLocalId("entry");
			Audience audience = parseAudience(reader);
			boolean required = reader.optionalBoolean("required", false);
			Optional<HostBossBar> hostBossBar = parseHostBossBar(reader);
			Optional<Identifier> onStart = reader.optionalIdentifier("on_start");
			Optional<Identifier> onPlayerComplete = reader.optionalIdentifier("on_player_complete");
			Optional<Identifier> onAllComplete = reader.optionalIdentifier("on_all_complete");
			JsonArray nodesArray = reader.requiredArray("nodes");
			Map<String, FlowNode> nodes;
			if (nodesArray.size() > MAX_FLOW_NODES) {
				add(
					source,
					"RESOURCE_LIMIT",
					"/nodes",
					"flow node count " + nodesArray.size() + " exceeds " + MAX_FLOW_NODES
				);
				nodes = Map.of();
			} else {
				nodes = parseNodes(source, nodesArray);
			}
			if (version != null && version < 1) {
				add(source, "OUT_OF_RANGE", "/version", "flow version must be positive");
			}
			if (required && hostBossBar.isEmpty()) {
				add(
					source,
					"MISSING_KEY",
					"/host_bossbar",
					"required flows must configure a host progress BossBar"
				);
			}
			if (nodes.isEmpty()) {
				add(source, "INVALID_GRAPH", "/nodes", "flow requires at least one node");
			} else if (entry != null) {
				validateFlowGraph(source, entry, nodes);
			}
			if (
				game != null
					&& version != null
					&& version > 0
					&& name != null
					&& entry != null
					&& !nodes.isEmpty()
			) {
				this.flows.put(
					source.id(),
					new FlowDefinition(
						source.id(),
						game,
						version,
						name,
						entry,
						audience,
						required,
						hostBossBar,
						onStart,
						onPlayerComplete,
						onAllComplete,
						nodes
					)
				);
			}
		}

		private Optional<HostBossBar> parseHostBossBar(final ObjectReader parent) {
			JsonObject object = parent.optionalObject("host_bossbar");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("host_bossbar", object);
			RichText name = reader.requiredText("name");
			BossBarColor color = reader.optionalEnum("color", BossBarColor.class, BossBarColor.PURPLE);
			BossBarStyle style = reader.optionalEnum("style", BossBarStyle.class, BossBarStyle.PROGRESS);
			int priority = reader.optionalInt("priority", 0);
			Optional<BossBarCompletionFeedback> completionFeedback = parseBossBarCompletionFeedback(reader);
			if (
				name != null
					&& (
						!name.plainText().contains("{event}")
							|| !name.plainText().contains("{completed}")
							|| !name.plainText().contains("{total}")
					)
			) {
				add(
					parent.source,
					"INVALID_TEMPLATE",
					reader.pointer("/name"),
					"BossBar name must contain {event}, {completed}, and {total}"
				);
			}
			if (priority < -1_000 || priority > 1_000) {
				add(
					parent.source,
					"OUT_OF_RANGE",
					reader.pointer("/priority"),
					"priority must be between -1000 and 1000"
				);
			}
			reader.finish();
			return name == null || color == null || style == null
				? Optional.empty()
				: Optional.of(new HostBossBar(name, color, style, priority, completionFeedback));
		}

		private Optional<BossBarCompletionFeedback> parseBossBarCompletionFeedback(
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("completion_feedback");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("completion_feedback", object);
			RichText name = reader.requiredText("name");
			BossBarColor color = reader.optionalEnum("color", BossBarColor.class, BossBarColor.GREEN);
			int holdTicks = reader.optionalInt("hold_ticks", 40);
			int fadeTicks = reader.optionalInt("fade_ticks", 12);
			Optional<Identifier> sound = reader.optionalIdentifier("sound");
			if (holdTicks < 0 || holdTicks > 1_200) {
				add(
					parent.source,
					"OUT_OF_RANGE",
					reader.pointer("/hold_ticks"),
					"hold_ticks must be between 0 and 1200"
				);
			}
			if (fadeTicks < 0 || fadeTicks > 100) {
				add(
					parent.source,
					"OUT_OF_RANGE",
					reader.pointer("/fade_ticks"),
					"fade_ticks must be between 0 and 100"
				);
			}
			reader.finish();
			return name == null || color == null
				? Optional.empty()
				: Optional.of(
					new BossBarCompletionFeedback(name, color, holdTicks, fadeTicks, sound)
				);
		}

		private Audience parseAudience(final ObjectReader parent) {
			return parseAudience(parent, Audience.everyoneExceptHost());
		}

		private Audience parseAudience(
			final ObjectReader parent,
			final Audience fallback
		) {
			JsonObject object = parent.optionalObject("audience");
			if (object == null) {
				return fallback;
			}
			return parseAudienceObject(parent, "audience", object, fallback.onlineOnly());
		}

		private Audience parseAudienceObject(
			final ObjectReader parent,
			final String name,
			final JsonObject object
		) {
			return parseAudienceObject(parent, name, object, true);
		}

		private Audience parseAudienceObject(
			final ObjectReader parent,
			final String name,
			final JsonObject object,
			final boolean onlineOnlyDefault
		) {
			ObjectReader reader = parent.child(name, object);
			Set<Identifier> roles = reader.optionalIdentifierSet("roles");
			Set<Identifier> teams = reader.optionalIdentifierSet("teams");
			Set<Identifier> lifeStates = reader.optionalIdentifierSet("life_states");
			Set<Identifier> roleTags = reader.optionalIdentifierSet("role_tags");
			Set<Identifier> teamTags = reader.optionalIdentifierSet("team_tags");
			Set<Identifier> lifeStateTags = reader.optionalIdentifierSet("life_state_tags");
			boolean excludeHost = reader.optionalBoolean("exclude_host", true);
			boolean onlineOnly = reader.optionalBoolean("online_only", onlineOnlyDefault);
			reader.finish();
			return new Audience(
				roles,
				teams,
				lifeStates,
				roleTags,
				teamTags,
				lifeStateTags,
				excludeHost,
				onlineOnly
			);
		}

		private Map<String, FlowNode> parseNodes(final Source source, final JsonArray array) {
			Map<String, FlowNode> nodes = new LinkedHashMap<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				String pointer = "/nodes/" + index;
				if (!element.isJsonObject()) {
					add(source, "TYPE_MISMATCH", pointer, "node must be an object");
					continue;
				}
				ObjectReader reader = new ObjectReader(source, pointer, element.getAsJsonObject(), this.problems);
				String id = reader.requiredLocalId("id");
				String type = reader.requiredString("type");
				FlowNode node = id == null || type == null
					? null
					: parseNode(source, reader, id, type.toLowerCase(Locale.ROOT));
				reader.finish();
				if (node != null && nodes.putIfAbsent(node.id(), node) != null) {
					add(source, "DUPLICATE_NODE", pointer + "/id", "duplicate node id " + node.id());
				}
			}
			return nodes;
		}

		private FlowNode parseNode(
			final Source source,
			final ObjectReader reader,
			final String id,
			final String type
		) {
			return switch (type) {
				case "page" -> {
					Identifier page = reader.requiredIdentifier("page");
					String next = reader.requiredLocalId("next");
					yield page == null || next == null ? null : new PageNode(id, page, next);
				}
				case "choice" -> parseChoiceNode(source, reader, id);
				case "confirm" -> {
					Identifier page = reader.requiredIdentifier("page");
					String next = reader.requiredLocalId("next");
					Optional<String> back = reader.optionalLocalId("back");
					yield page == null || next == null
						? null
						: new ConfirmNode(id, page, next, back);
				}
				case "branch" -> parseBranchNode(source, reader, id);
				case "function" -> {
					NodeScope scope = reader.optionalEnum("scope", NodeScope.class, NodeScope.PLAYER);
					Identifier function = reader.requiredIdentifier("function");
					String next = reader.requiredLocalId("next");
					yield function == null || next == null ? null : new FunctionNode(id, scope, function, next);
				}
				case "wait_players" -> {
					String next = reader.requiredLocalId("next");
					yield next == null ? null : new WaitPlayersNode(id, next);
				}
				case "change_state" -> {
					NodeScope scope = reader.optionalEnum("scope", NodeScope.class, NodeScope.PLAYER);
					StateAxis axis = reader.requiredEnum("axis", StateAxis.class);
					Identifier value = reader.requiredIdentifier("value");
					String next = reader.requiredLocalId("next");
					yield axis == null || value == null || next == null
						? null
						: new ChangeStateNode(id, scope, axis, value, next);
				}
				case "complete" -> new CompleteNode(
					id,
					reader.optionalEnum("scope", NodeScope.class, NodeScope.PLAYER)
				);
				default -> {
					add(source, "UNKNOWN_NODE_TYPE", reader.pointer("/type"), "unknown node type " + type);
					yield null;
				}
			};
		}

		private FlowNode parseChoiceNode(
			final Source source,
			final ObjectReader reader,
			final String id
		) {
			Identifier page = reader.requiredIdentifier("page");
			Identifier field = reader.requiredIdentifier("field");
			JsonArray routesArray = reader.requiredArray("choices");
			List<ChoiceRoute> routes = new ArrayList<>();
			Set<String> values = new HashSet<>();
			for (int index = 0; index < routesArray.size(); index++) {
				JsonElement element = routesArray.get(index);
				String pointer = reader.pointer("/choices/" + index);
				if (!element.isJsonObject()) {
					add(source, "TYPE_MISMATCH", pointer, "choice route must be an object");
					continue;
				}
				ObjectReader routeReader = new ObjectReader(source, pointer, element.getAsJsonObject(), this.problems);
				String value = routeReader.requiredString("value");
				String next = routeReader.requiredLocalId("next");
				routeReader.finish();
				if (value != null && next != null) {
					if (!values.add(value)) {
						add(source, "DUPLICATE_VALUE", pointer + "/value", "duplicate choice value " + value);
					} else {
						routes.add(new ChoiceRoute(value, next));
					}
				}
			}
			if (routes.isEmpty()) {
				add(source, "OUT_OF_RANGE", reader.pointer("/choices"), "choice node requires at least one route");
			}
			return page == null || field == null || routes.isEmpty()
				? null
				: new ChoiceNode(id, page, field, routes);
		}

		private FlowNode parseBranchNode(
			final Source source,
			final ObjectReader reader,
			final String id
		) {
			NodeScope scope = reader.optionalEnum("scope", NodeScope.class, NodeScope.PLAYER);
			JsonArray casesArray = reader.requiredArray("cases");
			List<BranchCase> cases = new ArrayList<>();
			for (int index = 0; index < casesArray.size(); index++) {
				JsonElement element = casesArray.get(index);
				String pointer = reader.pointer("/cases/" + index);
				if (!element.isJsonObject()) {
					add(source, "TYPE_MISMATCH", pointer, "branch case must be an object");
					continue;
				}
				ObjectReader caseReader = new ObjectReader(source, pointer, element.getAsJsonObject(), this.problems);
				Identifier predicate = caseReader.requiredIdentifier("predicate");
				String next = caseReader.requiredLocalId("next");
				caseReader.finish();
				if (predicate != null && next != null) {
					cases.add(new BranchCase(predicate, next));
				}
			}
			String fallback = reader.requiredLocalId("fallback");
			if (cases.isEmpty()) {
				add(source, "OUT_OF_RANGE", reader.pointer("/cases"), "branch node requires at least one case");
			}
			return fallback == null || cases.isEmpty() ? null : new BranchNode(id, scope, cases, fallback);
		}

		private void validateFlowGraph(
			final Source source,
			final String entry,
			final Map<String, FlowNode> nodes
		) {
			if (!nodes.containsKey(entry)) {
				add(source, "MISSING_REFERENCE", "/entry", "entry node " + entry + " does not exist");
				return;
			}
			int completeCount = 0;
			for (FlowNode node : nodes.values()) {
				if (node instanceof CompleteNode) {
					completeCount++;
				}
				for (String target : node.outgoing()) {
					if (!nodes.containsKey(target)) {
						add(
							source,
							"MISSING_REFERENCE",
							"/nodes",
							"node " + node.id() + " targets missing node " + target
						);
					}
				}
			}
			if (completeCount == 0) {
				add(source, "INVALID_GRAPH", "/nodes", "flow requires at least one complete node");
			}
			if (this.problems.stream().anyMatch(problem -> problem.resource().equals(source.resource()))) {
				return;
			}

			Set<String> reachable = new LinkedHashSet<>();
			ArrayDeque<String> queue = new ArrayDeque<>();
			queue.add(entry);
			while (!queue.isEmpty()) {
				String current = queue.removeFirst();
				if (!reachable.add(current)) {
					continue;
				}
				queue.addAll(nodes.get(current).outgoing());
			}
			for (String nodeId : nodes.keySet()) {
				if (!reachable.contains(nodeId)) {
					add(source, "INVALID_GRAPH", "/nodes", "node " + nodeId + " is unreachable from entry");
				}
			}

			Map<String, Integer> colors = new HashMap<>();
			if (hasCycle(entry, nodes, colors)) {
				// A confirm "back" edge is interactive navigation, not automatic flow progress.
				// Progress edges must still remain acyclic until a bounded loop node exists.
				add(source, "INVALID_GRAPH", "/nodes", "flow cycles are not supported; use a future bounded loop node");
				return;
			}
			Map<String, Boolean> canComplete = new HashMap<>();
			for (String nodeId : reachable) {
				if (!canReachComplete(nodeId, nodes, canComplete)) {
					add(source, "INVALID_GRAPH", "/nodes", "node " + nodeId + " cannot reach a complete node");
				}
			}
		}

		private static boolean hasCycle(
			final String nodeId,
			final Map<String, FlowNode> nodes,
			final Map<String, Integer> colors
		) {
			int color = colors.getOrDefault(nodeId, 0);
			if (color == 1) {
				return true;
			}
			if (color == 2) {
				return false;
			}
			colors.put(nodeId, 1);
			for (String target : progressTargets(nodes.get(nodeId))) {
				if (hasCycle(target, nodes, colors)) {
					return true;
				}
			}
			colors.put(nodeId, 2);
			return false;
		}

		private static boolean canReachComplete(
			final String nodeId,
			final Map<String, FlowNode> nodes,
			final Map<String, Boolean> memo
		) {
			Boolean cached = memo.get(nodeId);
			if (cached != null) {
				return cached;
			}
			FlowNode node = nodes.get(nodeId);
			boolean result = node instanceof CompleteNode
				|| progressTargets(node).stream().allMatch(target -> canReachComplete(target, nodes, memo));
			memo.put(nodeId, result);
			return result;
		}

		private static List<String> progressTargets(final FlowNode node) {
			return node instanceof ConfirmNode confirm ? List.of(confirm.next()) : node.outgoing();
		}

		private void parseTask(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			Integer version = reader.requiredInt("version");
			TaskKind kind = reader.requiredEnum("kind", TaskKind.class);
			RichText name = reader.requiredText("name");
			Optional<RichText> description = reader.optionalText("description");
			Optional<Identifier> page = reader.optionalIdentifier("page");
			Audience audience = parseAudience(reader, TaskDefinitions.defaultTaskAudience());
			TaskCompletionPolicy completionPolicy = reader.requiredEnum(
				"completion_policy",
				TaskCompletionPolicy.class
			);
			Optional<Long> duration = reader.optionalLong("duration_ticks");
			boolean countsTowardGameTime = reader.optionalBoolean(
				"counts_toward_game_time",
				kind == TaskKind.MAIN
			);
			LiveVisibility liveVisibility = parseLiveVisibility(reader);
			RecapVisibility recapVisibility = reader.optionalEnum(
				"recap_visibility",
				RecapVisibility.class,
				RecapVisibility.PARTICIPANTS
			);
			TaskCallbacks callbacks = parseTaskCallbacks(reader);
			Optional<PlayerCallback> onStartPlayers = parsePlayerCallback(reader, "on_start_players");
			Map<String, TaskResult> results = parseTaskResults(source, reader);
			List<TaskEventDefinition> events = parseTaskEvents(source, reader);
			List<TaskStatisticDefinition> statistics = parseTaskStatistics(source, reader);

			if (version != null && version < 1) {
				add(source, "OUT_OF_RANGE", "/version", "task version must be positive");
			}
			if (
				duration.filter(value -> value <= 0L || value > MAX_TASK_DURATION_TICKS).isPresent()
			) {
				add(
					source,
					"OUT_OF_RANGE",
					"/duration_ticks",
					"duration_ticks must be between 1 and " + MAX_TASK_DURATION_TICKS
				);
			}
			if (
				completionPolicy == TaskCompletionPolicy.EVENT_ONLY
					&& duration.isPresent()
			) {
				add(
					source,
					"INVALID_COMBINATION",
					"/duration_ticks",
					"event_only tasks cannot declare duration_ticks"
				);
			}
			if (
				completionPolicy != null
					&& completionPolicy != TaskCompletionPolicy.EVENT_ONLY
					&& duration.isEmpty()
			) {
				add(
					source,
					"MISSING_KEY",
					"/duration_ticks",
					"timeout task policies require duration_ticks"
				);
			}
			if (results.isEmpty()) {
				add(source, "OUT_OF_RANGE", "/results", "task requires at least one valid result");
			}
			if (
				game != null
					&& version != null
					&& version > 0
					&& kind != null
					&& name != null
					&& completionPolicy != null
					&& !results.isEmpty()
			) {
				this.tasks.put(
					source.id(),
					new TaskDefinition(
						source.id(),
						game,
						version,
						kind,
						name,
						description,
						page,
						audience,
						completionPolicy,
						duration.isPresent()
							? OptionalLong.of(duration.orElseThrow())
							: OptionalLong.empty(),
						countsTowardGameTime,
						liveVisibility,
						recapVisibility,
						callbacks,
						onStartPlayers,
						results,
						events,
						statistics
					)
				);
			}
		}

		private LiveVisibility parseLiveVisibility(final ObjectReader parent) {
			JsonObject object = parent.optionalObject("live_visibility");
			if (object == null) {
				return LiveVisibility.hidden();
			}
			ObjectReader reader = parent.child("live_visibility", object);
			FutureVisibility future = reader.optionalEnum(
				"future",
				FutureVisibility.class,
				FutureVisibility.HIDDEN
			);
			HistoryVisibility history = reader.optionalEnum(
				"history",
				HistoryVisibility.class,
				HistoryVisibility.HIDDEN
			);
			CandidateVisibility candidate = reader.optionalEnum(
				"candidate_visibility",
				CandidateVisibility.class,
				CandidateVisibility.NONE
			);
			reader.finish();
			return new LiveVisibility(future, history, candidate);
		}

		private TaskCallbacks parseTaskCallbacks(final ObjectReader parent) {
			JsonObject object = parent.optionalObject("callbacks");
			if (object == null) {
				return TaskCallbacks.empty();
			}
			ObjectReader reader = parent.child("callbacks", object);
			TaskCallbacks callbacks = new TaskCallbacks(
				reader.optionalIdentifier("on_start"),
				reader.optionalIdentifier("on_pause"),
				reader.optionalIdentifier("on_resume"),
				reader.optionalIdentifier("on_timeout"),
				reader.optionalIdentifier("on_settled")
			);
			reader.finish();
			return callbacks;
		}

		private Optional<PlayerCallback> parsePlayerCallback(
			final ObjectReader parent,
			final String name
		) {
			JsonObject object = parent.optionalObject(name);
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child(name, object);
			Identifier function = reader.requiredIdentifier("function");
			JsonObject audienceObject = reader.optionalObject("audience");
			Optional<Audience> audience = audienceObject == null
				? Optional.empty()
				: Optional.of(parseAudienceObject(reader, "audience", audienceObject, false));
			JsonObject fieldsObject = reader.optionalObject("fields");
			Map<String, Identifier> fields = new LinkedHashMap<>();
			if (fieldsObject != null) {
				if (fieldsObject.size() > MAX_TASK_CALLBACK_FIELDS) {
					add(
						parent.source,
						"RESOURCE_LIMIT",
						reader.pointer("/fields"),
						"callback field count exceeds " + MAX_TASK_CALLBACK_FIELDS
					);
				}
				for (Map.Entry<String, JsonElement> entry : fieldsObject.entrySet()) {
					String macro = entry.getKey();
					String pointer = reader.pointer("/fields/" + escapePointer(macro));
					if (!CALLBACK_MACRO.matcher(macro).matches()) {
						add(
							parent.source,
							"INVALID_LOCAL_ID",
							pointer,
							"callback macro must match [a-z][a-z0-9_]{0,31}"
						);
						continue;
					}
					JsonElement value = entry.getValue();
					if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
						add(parent.source, "TYPE_MISMATCH", pointer, "callback field must be an identifier string");
						continue;
					}
					Identifier field = parseIdentifier(value.getAsString());
					if (field == null) {
						add(parent.source, "INVALID_IDENTIFIER", pointer, "expected an explicitly namespaced identifier");
					} else {
						fields.put(macro, field);
					}
				}
			}
			reader.finish();
			return function == null
				? Optional.empty()
				: Optional.of(new PlayerCallback(function, audience, fields));
		}

		private Map<String, TaskResult> parseTaskResults(
			final Source source,
			final ObjectReader parent
		) {
			JsonArray array = parent.requiredArray("results");
			if (array.size() > MAX_TASK_RESULTS) {
				add(
					source,
					"RESOURCE_LIMIT",
					"/results",
					"task result count exceeds " + MAX_TASK_RESULTS
				);
				return Map.of();
			}
			Map<String, TaskResult> results = new LinkedHashMap<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				String pointer = parent.pointer("/results/" + index);
				if (!element.isJsonObject()) {
					add(source, "TYPE_MISMATCH", pointer, "task result must be an object");
					continue;
				}
				ObjectReader reader = new ObjectReader(
					source,
					pointer,
					element.getAsJsonObject(),
					this.problems
				);
				String id = reader.requiredLocalId("id");
				RichText name = reader.requiredText("name");
				ResultStyle style = parseResultStyle(source, reader);
				Optional<RichText> recap = reader.optionalText("recap");
				Optional<Identifier> onApply = reader.optionalIdentifier("on_apply");
				Optional<PlayerCallback> onApplyPlayers = parsePlayerCallback(reader, "on_apply_players");
				boolean allowHostFallback = reader.optionalBoolean("allow_host_fallback", false);
				TaskRoute route = parseTaskRoute(source, reader);
				reader.finish();
				if ("skipped".equals(id)) {
					add(
						source,
						"INVALID_COMBINATION",
						pointer + "/id",
						"skipped is reserved and cannot be registered as a normal task result"
					);
				}
				if (id != null && name != null && style != null && route != null) {
					TaskResult result = new TaskResult(
						id,
						name,
						style,
						recap,
						onApply,
						onApplyPlayers,
						allowHostFallback,
						route
					);
					if (results.putIfAbsent(id, result) != null) {
						add(source, "DUPLICATE_VALUE", pointer + "/id", "duplicate task result " + id);
					}
				}
			}
			return Map.copyOf(results);
		}

		private ResultStyle parseResultStyle(
			final Source source,
			final ObjectReader reader
		) {
			String value = reader.requiredString("semantic");
			if (value == null) {
				return null;
			}
			return switch (value) {
				case "success" -> new ResultStyle(ResultSemantic.SUCCESS, Optional.empty());
				case "failure" -> new ResultStyle(ResultSemantic.FAILURE, Optional.empty());
				case "neutral" -> new ResultStyle(ResultSemantic.NEUTRAL, Optional.empty());
				default -> {
					Identifier custom = parseIdentifier(value);
					if (custom == null) {
						add(
							source,
							"INVALID_IDENTIFIER",
							reader.pointer("/semantic"),
							"custom result semantic must be an explicitly namespaced identifier"
						);
						yield null;
					}
					yield new ResultStyle(ResultSemantic.CUSTOM, Optional.of(custom));
				}
			};
		}

		private TaskRoute parseTaskRoute(
			final Source source,
			final ObjectReader parent
		) {
			JsonObject object = parent.requiredObject("route");
			if (object == null) {
				return null;
			}
			ObjectReader reader = parent.child("route", object);
			Optional<Identifier> nextTask = reader.optionalIdentifier("next_task");
			Optional<Identifier> endPhase = reader.optionalIdentifier("end_phase");
			Optional<Identifier> phase = reader.optionalIdentifier("phase");
			Optional<String> transitionText = reader.optionalString("transition_timing");
			JsonObject intermissionObject = reader.optionalObject("intermission");
			Optional<IntermissionDefinition> intermission = intermissionObject == null
				? Optional.empty()
				: parseIntermission(source, reader, intermissionObject);
			Optional<TransitionTiming> transitionTiming = Optional.empty();
			if (transitionText.isPresent()) {
				transitionTiming = Optional.ofNullable(
					parseEnumValue(
						source,
						reader.pointer("/transition_timing"),
						transitionText.orElseThrow(),
						TransitionTiming.class
					)
				);
			} else if (intermission.isPresent()) {
				transitionTiming = Optional.of(TransitionTiming.AFTER_INTERMISSION);
			}
			if (nextTask.isPresent() == endPhase.isPresent()) {
				add(
					source,
					"INVALID_COMBINATION",
					reader.pointer(""),
					"route must contain exactly one of next_task or end_phase"
				);
			}
			if (endPhase.isPresent() && phase.isPresent()) {
				add(
					source,
					"INVALID_COMBINATION",
					reader.pointer("/phase"),
					"end_phase cannot be combined with phase"
				);
			}
			if (intermission.isEmpty() && transitionText.isPresent()) {
				add(
					source,
					"INVALID_COMBINATION",
					reader.pointer("/transition_timing"),
					"transition_timing requires intermission"
				);
			}
			reader.finish();
			return nextTask.isPresent() == endPhase.isPresent()
				? null
				: new TaskRoute(
					nextTask,
					endPhase,
					phase,
					transitionTiming,
					intermission
				);
		}

		private Optional<IntermissionDefinition> parseIntermission(
			final Source source,
			final ObjectReader parent,
			final JsonObject object
		) {
			ObjectReader reader = parent.child("intermission", object);
			Long duration = reader.requiredLong("duration_ticks");
			boolean countsTowardGameTime = reader.optionalBoolean("counts_toward_game_time", true);
			RichText name = reader.requiredText("name");
			Optional<Identifier> onStart = reader.optionalIdentifier("on_start");
			Optional<Identifier> onPause = reader.optionalIdentifier("on_pause");
			Optional<Identifier> onResume = reader.optionalIdentifier("on_resume");
			Optional<Identifier> onComplete = reader.optionalIdentifier("on_complete");
			if (
				duration != null
					&& (duration <= 0L || duration > MAX_INTERMISSION_DURATION_TICKS)
			) {
				add(
					source,
					"OUT_OF_RANGE",
					reader.pointer("/duration_ticks"),
					"intermission duration must be between 1 and "
						+ MAX_INTERMISSION_DURATION_TICKS
				);
			}
			reader.finish();
			return duration == null || duration <= 0L || name == null
				? Optional.empty()
				: Optional.of(
					new IntermissionDefinition(
						duration,
						countsTowardGameTime,
						name,
						onStart,
						onPause,
						onResume,
						onComplete
					)
				);
		}

		private List<TaskEventDefinition> parseTaskEvents(
			final Source source,
			final ObjectReader parent
		) {
			JsonArray array = parent.optionalArray("events");
			if (array == null) {
				return List.of();
			}
			if (array.size() > MAX_TASK_EVENTS) {
				add(
					source,
					"RESOURCE_LIMIT",
					"/events",
					"task event count exceeds " + MAX_TASK_EVENTS
				);
				return List.of();
			}
			List<TaskEventDefinition> events = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				String pointer = parent.pointer("/events/" + index);
				if (!element.isJsonObject()) {
					add(source, "TYPE_MISMATCH", pointer, "task event must be an object");
					continue;
				}
				ObjectReader reader = new ObjectReader(
					source,
					pointer,
					element.getAsJsonObject(),
					this.problems
				);
				String id = reader.requiredLocalId("id");
				RichText name = reader.requiredText("name");
				TaskEventPolicy policy = reader.requiredEnum("policy", TaskEventPolicy.class);
				Optional<Integer> maximumRecords = reader.optionalInteger("max_records");
				Set<TaskEventState> allowedStates = parseEventStates(source, reader);
				JsonObject audienceObject = reader.optionalObject("audience");
				Optional<Audience> audience = audienceObject == null
					? Optional.empty()
					: Optional.of(parseAudienceObject(reader, "audience", audienceObject, false));
				RecapVisibility recapVisibility = reader.optionalEnum(
					"recap_visibility",
					RecapVisibility.class,
					RecapVisibility.PARTICIPANTS
				);
				if (
					policy == TaskEventPolicy.REPEATABLE
						&& (
							maximumRecords.isEmpty()
								|| maximumRecords.filter(
									value -> value <= 0 || value > MAX_REPEATABLE_EVENT_RECORDS
								).isPresent()
						)
				) {
					add(
						source,
						"OUT_OF_RANGE",
						reader.pointer("/max_records"),
						"repeatable events require max_records between 1 and "
							+ MAX_REPEATABLE_EVENT_RECORDS
					);
				}
				if (policy != null && policy != TaskEventPolicy.REPEATABLE && maximumRecords.isPresent()) {
					add(
						source,
						"INVALID_COMBINATION",
						reader.pointer("/max_records"),
						"max_records is only valid for repeatable events"
					);
				}
				reader.finish();
				if (id != null && !ids.add(id)) {
					add(source, "DUPLICATE_VALUE", pointer + "/id", "duplicate task event " + id);
				} else if (
					id != null
						&& name != null
						&& policy != null
						&& !allowedStates.isEmpty()
				) {
					events.add(
						new TaskEventDefinition(
							id,
							name,
							policy,
							maximumRecords,
							allowedStates,
							audience,
							recapVisibility
						)
					);
				}
			}
			return List.copyOf(events);
		}

		private Set<TaskEventState> parseEventStates(
			final Source source,
			final ObjectReader reader
		) {
			JsonArray array = reader.optionalArray("allowed_states");
			if (array == null) {
				return Set.of(TaskEventState.RUNNING);
			}
			Set<TaskEventState> result = new LinkedHashSet<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				String pointer = reader.pointer("/allowed_states/" + index);
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					add(source, "TYPE_MISMATCH", pointer, "event state must be a string");
					continue;
				}
				TaskEventState state = parseEnumValue(
					source,
					pointer,
					element.getAsString(),
					TaskEventState.class
				);
				if (state != null && !result.add(state)) {
					add(source, "DUPLICATE_VALUE", pointer, "duplicate event state " + element.getAsString());
				}
			}
			if (result.isEmpty()) {
				add(source, "OUT_OF_RANGE", reader.pointer("/allowed_states"), "allowed_states cannot be empty");
			}
			return Set.copyOf(result);
		}

		private List<TaskStatisticDefinition> parseTaskStatistics(
			final Source source,
			final ObjectReader parent
		) {
			JsonArray array = parent.optionalArray("statistics");
			if (array == null) {
				return List.of();
			}
			if (array.size() > MAX_TASK_STATISTICS) {
				add(
					source,
					"RESOURCE_LIMIT",
					"/statistics",
					"task statistic count exceeds " + MAX_TASK_STATISTICS
				);
				return List.of();
			}
			List<TaskStatisticDefinition> statistics = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				String pointer = parent.pointer("/statistics/" + index);
				if (!element.isJsonObject()) {
					add(source, "TYPE_MISMATCH", pointer, "task statistic must be an object");
					continue;
				}
				ObjectReader reader = new ObjectReader(
					source,
					pointer,
					element.getAsJsonObject(),
					this.problems
				);
				String id = reader.requiredLocalId("id");
				RichText name = reader.requiredText("name");
				TaskStatisticType type = reader.requiredEnum("type", TaskStatisticType.class);
				Optional<Long> minimum = reader.optionalLong("min");
				Optional<Long> maximum = reader.optionalLong("max");
				TaskStatisticWrite write = reader.requiredEnum("write", TaskStatisticWrite.class);
				RecapVisibility recapVisibility = reader.optionalEnum(
					"recap_visibility",
					RecapVisibility.class,
					RecapVisibility.PARTICIPANTS
				);
				boolean numeric = type == TaskStatisticType.INTEGER
					|| type == TaskStatisticType.DURATION_TICKS;
				if (!numeric && (minimum.isPresent() || maximum.isPresent())) {
					add(
						source,
						"INVALID_CONSTRAINT",
						reader.pointer("/min"),
						"min/max are only valid for integer or duration_ticks statistics"
					);
				}
				if (
					minimum.isPresent()
						&& maximum.isPresent()
						&& minimum.orElseThrow() > maximum.orElseThrow()
				) {
					add(source, "OUT_OF_RANGE", reader.pointer("/min"), "min must not exceed max");
				}
				if (write == TaskStatisticWrite.INCREMENT && !numeric) {
					add(
						source,
						"INVALID_COMBINATION",
						reader.pointer("/write"),
						"increment is only valid for integer or duration_ticks statistics"
					);
				}
				reader.finish();
				if (id != null && !ids.add(id)) {
					add(source, "DUPLICATE_VALUE", pointer + "/id", "duplicate task statistic " + id);
				} else if (id != null && name != null && type != null && write != null) {
					statistics.add(
						new TaskStatisticDefinition(
							id,
							name,
							type,
							minimum,
							maximum,
							write,
							recapVisibility
						)
					);
				}
			}
			return List.copyOf(statistics);
		}

		private <E extends Enum<E>> E parseEnumValue(
			final Source source,
			final String pointer,
			final String value,
			final Class<E> type
		) {
			try {
				return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException error) {
				add(
					source,
					"INVALID_ENUM",
					pointer,
					"unknown " + type.getSimpleName() + " value " + value
				);
				return null;
			}
		}

		private void parsePanelAction(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			PanelSurface surface = reader.requiredEnum("surface", PanelSurface.class);
			String section = reader.requiredLocalId("section");
			int order = reader.optionalInt("order", 0);
			RichText label = reader.requiredText("label");
			RichText description = reader.requiredText("description");
			Optional<Identifier> icon = reader.optionalIdentifier("icon");
			Optional<String> color = reader.optionalString("color");
			Optional<Identifier> visibleWhen = reader.optionalIdentifier("visible_when");
			Optional<Identifier> enabledWhen = reader.optionalIdentifier("enabled_when");
			Optional<RichText> disabledReason = reader.optionalText("disabled_reason");
			Set<Identifier> phases = reader.requiredIdentifierSet("phases");
			TargetSelection target = parseTarget(reader);
			Optional<Confirmation> confirmation = parseConfirmation(reader);
			PanelOperation operation = parseOperation(source, reader);
			if (phases.isEmpty()) {
				add(source, "OUT_OF_RANGE", "/phases", "panel action requires at least one phase");
			}
			if (surface == PanelSurface.HOST && confirmation.isEmpty()) {
				add(
					source,
					"MISSING_CONFIRMATION",
					"/confirmation",
					"host state-changing actions require an explicit consequence confirmation"
				);
			}
			color.ifPresent(value -> {
				if (!HEX_COLOR.matcher(value).matches()) {
					add(source, "INVALID_COLOR", "/color", "expected #RRGGBB");
				}
			});
			if (enabledWhen.isPresent() && disabledReason.isEmpty()) {
				add(
					source,
					"MISSING_KEY",
					"/disabled_reason",
					"enabled_when requires a user-visible disabled reason"
				);
			}
			if (order < -10_000 || order > 10_000) {
				add(source, "OUT_OF_RANGE", "/order", "order must be between -10000 and 10000");
			}
			if (
				game != null
					&& surface != null
					&& section != null
					&& label != null
					&& description != null
					&& !phases.isEmpty()
					&& target != null
					&& operation != null
					&& (surface != PanelSurface.HOST || confirmation.isPresent())
					&& (enabledWhen.isEmpty() || disabledReason.isPresent())
			) {
				this.panelActions.put(
					source.id(),
					new PanelActionDefinition(
						source.id(),
						game,
						surface,
						section,
						order,
						label,
						description,
						icon,
						color,
						visibleWhen,
						enabledWhen,
						disabledReason,
						phases,
						target,
						confirmation,
						operation
					)
				);
			}
		}

		private TargetSelection parseTarget(final ObjectReader parent) {
			JsonObject object = parent.requiredObject("target");
			if (object == null) {
				return null;
			}
			ObjectReader reader = parent.child("target", object);
			SelectionMode mode = reader.requiredEnum("mode", SelectionMode.class);
			int defaultMin = mode == SelectionMode.NONE ? 0 : 1;
			int defaultMax = mode == SelectionMode.NONE ? 0 : mode == SelectionMode.SINGLE ? 1 : 64;
			int minimum = reader.optionalInt("min", defaultMin);
			int maximum = reader.optionalInt("max", defaultMax);
			JsonObject filterObject = reader.optionalObject("filter");
			TargetFilter filter = new TargetFilter(
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of()
			);
			if (filterObject != null) {
				ObjectReader filterReader = reader.child("filter", filterObject);
				filter = new TargetFilter(
					filterReader.optionalIdentifierSet("roles"),
					filterReader.optionalIdentifierSet("teams"),
					filterReader.optionalIdentifierSet("life_states"),
					filterReader.optionalIdentifierSet("role_tags"),
					filterReader.optionalIdentifierSet("team_tags"),
					filterReader.optionalIdentifierSet("life_state_tags"),
					filterReader.optionalIdentifierSet("completed_flows"),
					filterReader.optionalIdentifierSet("incomplete_flows"),
					filterReader.optionalIdentifierSet("status_flows")
				);
				Set<Identifier> contradictoryFlows = new HashSet<>(filter.completedFlows());
				contradictoryFlows.retainAll(filter.incompleteFlows());
				if (!contradictoryFlows.isEmpty()) {
					add(
						parent.source,
						"INVALID_CONSTRAINT",
						filterReader.pointer(""),
						"a target cannot require the same flow to be both complete and incomplete"
					);
				}
				filterReader.finish();
			}
			if (mode != null) {
				boolean invalid = minimum < 0 || maximum < minimum || maximum > 64;
				invalid |= mode == SelectionMode.NONE && (minimum != 0 || maximum != 0);
				invalid |= mode == SelectionMode.SINGLE && (minimum != 1 || maximum != 1);
				if (invalid) {
					add(
						parent.source,
						"INVALID_TARGET_RANGE",
						reader.pointer(""),
						"target range does not match selection mode"
					);
				}
				boolean hasFilterCriteria = !filter.roles().isEmpty()
					|| !filter.teams().isEmpty()
					|| !filter.lifeStates().isEmpty()
					|| !filter.roleTags().isEmpty()
					|| !filter.teamTags().isEmpty()
					|| !filter.lifeStateTags().isEmpty()
					|| !filter.completedFlows().isEmpty()
					|| !filter.incompleteFlows().isEmpty();
				if (mode == SelectionMode.NONE && hasFilterCriteria) {
					add(
						parent.source,
						"INVALID_CONSTRAINT",
						reader.pointer("/filter"),
						"target filters are not valid when selection mode is none"
					);
				}
			}
			reader.finish();
			return mode == null ? null : new TargetSelection(mode, minimum, maximum, filter);
		}

		private Optional<Confirmation> parseConfirmation(final ObjectReader parent) {
			JsonObject object = parent.optionalObject("confirmation");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("confirmation", object);
			RichText title = reader.requiredText("title");
			JsonArray consequencesArray = reader.requiredArray("consequences");
			List<RichText> consequences = new ArrayList<>();
			for (int index = 0; index < consequencesArray.size(); index++) {
				RichText consequence = parseText(
					parent.source,
					reader.pointer("/consequences/" + index),
					consequencesArray.get(index),
					this.problems
				);
				if (consequence != null) {
					consequences.add(consequence);
				}
			}
			if (consequences.isEmpty()) {
				add(
					parent.source,
					"OUT_OF_RANGE",
					reader.pointer("/consequences"),
					"confirmation requires at least one consequence"
				);
			}
			reader.finish();
			return title == null || consequences.isEmpty()
				? Optional.empty()
				: Optional.of(new Confirmation(title, consequences));
		}

		private PanelOperation parseOperation(final Source source, final ObjectReader parent) {
			JsonObject object = parent.requiredObject("operation");
			if (object == null) {
				return null;
			}
			ObjectReader reader = parent.child("operation", object);
			String type = reader.requiredString("type");
			PanelOperation operation = null;
			if (type != null) {
				operation = switch (type.toLowerCase(Locale.ROOT)) {
					case "start_flow" -> {
						Identifier flow = reader.requiredIdentifier("flow");
						CompletionPolicy completionPolicy = reader.optionalEnum(
							"completion_policy",
							CompletionPolicy.class,
							CompletionPolicy.IF_INCOMPLETE
						);
						yield flow == null ? null : new StartFlowOperation(flow, completionPolicy);
					}
					case "assign_role" -> {
						Identifier role = reader.requiredIdentifier("role");
						Optional<Identifier> flow = reader.optionalIdentifier("flow");
						ApplyTiming apply = reader.optionalEnum("apply", ApplyTiming.class, ApplyTiming.AFTER_FLOW);
						CompletionPolicy completionPolicy = reader.optionalEnum(
							"completion_policy",
							CompletionPolicy.class,
							CompletionPolicy.ALWAYS
						);
						if (apply == ApplyTiming.AFTER_FLOW && flow.isEmpty()) {
							add(
								source,
								"MISSING_KEY",
								reader.pointer("/flow"),
								"after_flow role assignment requires a flow"
							);
						}
						yield role == null || (apply == ApplyTiming.AFTER_FLOW && flow.isEmpty())
							? null
							: new AssignRoleOperation(role, flow, apply, completionPolicy);
					}
					case "assign_team" -> {
						Identifier team = reader.requiredIdentifier("team");
						Optional<Identifier> flow = reader.optionalIdentifier("flow");
						ApplyTiming apply = reader.optionalEnum("apply", ApplyTiming.class, ApplyTiming.AFTER_FLOW);
						if (apply == ApplyTiming.AFTER_FLOW && flow.isEmpty()) {
							add(
								source,
								"MISSING_KEY",
								reader.pointer("/flow"),
								"after_flow team assignment requires a flow"
							);
						}
						yield team == null || (apply == ApplyTiming.AFTER_FLOW && flow.isEmpty())
							? null
							: new AssignTeamOperation(team, flow, apply);
					}
					case "assign_life_state" -> {
						Identifier lifeState = reader.requiredIdentifier("life_state");
						Optional<Identifier> flow = reader.optionalIdentifier("flow");
						ApplyTiming apply = reader.optionalEnum("apply", ApplyTiming.class, ApplyTiming.IMMEDIATE);
						if (apply == ApplyTiming.AFTER_FLOW && flow.isEmpty()) {
							add(
								source,
								"MISSING_KEY",
								reader.pointer("/flow"),
								"after_flow life-state assignment requires a flow"
							);
						}
						yield lifeState == null || (apply == ApplyTiming.AFTER_FLOW && flow.isEmpty())
							? null
							: new AssignLifeStateOperation(lifeState, flow, apply);
					}
					case "transition_phase" -> {
						Identifier phase = reader.requiredIdentifier("phase");
						yield phase == null ? null : new TransitionPhaseOperation(phase);
					}
					case "run_function" -> {
						Identifier function = reader.requiredIdentifier("function");
						yield function == null ? null : new RunFunctionOperation(function);
					}
					default -> {
						add(source, "UNKNOWN_OPERATION", reader.pointer("/type"), "unknown operation type " + type);
						yield null;
					}
				};
			}
			reader.finish();
			return operation;
		}

		private void validateReferences() {
			for (GameDefinition game : this.games.values()) {
				requireSameGame(
					DefinitionType.GAME,
					game.id(),
					"/initial_phase",
					game.id(),
					game.initialPhase(),
					this.phases,
					PhaseDefinition::game,
					"phase"
				);
				requireSameGame(
					DefinitionType.GAME,
					game.id(),
					"/default_role",
					game.id(),
					game.defaultRole(),
					this.roles,
					RoleDefinition::game,
					"role"
				);
				requireSameGame(
					DefinitionType.GAME,
					game.id(),
					"/default_life_state",
					game.id(),
					game.defaultLifeState(),
					this.lifeStates,
					LifeStateDefinition::game,
					"life state"
				);
				game.taskTimeline().ifPresent(timeline -> validateTaskTimeline(game, timeline));
				game.readiness().ifPresent(readiness -> validateReadiness(game, readiness));
				long taskCount = this.tasks.values()
					.stream()
					.filter(task -> task.game().equals(game.id()))
					.count();
				if (taskCount > MAX_TASKS_PER_GAME) {
					add(
						origin(DefinitionType.GAME, game.id()),
						"RESOURCE_LIMIT",
						"/task_timeline",
						"game task count exceeds " + MAX_TASKS_PER_GAME
					);
				}
				validateTaskGraph(game);
			}
			for (RoleDefinition role : this.roles.values()) {
				requireGame(DefinitionType.ROLE, role.id(), role.game());
				role.initializationFlow().ifPresent(
					flow -> requireSameGame(
						DefinitionType.ROLE,
						role.id(),
						"/initialization_flow",
						role.game(),
						flow,
						this.flows,
						FlowDefinition::game,
						"flow"
					)
				);
			}
			for (TeamDefinition team : this.teams.values()) {
				requireGame(DefinitionType.TEAM, team.id(), team.game());
				for (Identifier role : team.allowedRoles()) {
					requireSameGame(
						DefinitionType.TEAM,
						team.id(),
						"/allowed_roles",
						team.game(),
						role,
						this.roles,
						RoleDefinition::game,
						"role"
					);
				}
			}
			for (LifeStateDefinition lifeState : this.lifeStates.values()) {
				requireGame(DefinitionType.LIFE_STATE, lifeState.id(), lifeState.game());
			}
			for (PhaseDefinition phase : this.phases.values()) {
				requireGame(DefinitionType.PHASE, phase.id(), phase.game());
				for (Identifier transition : phase.transitions()) {
					requireSameGame(
						DefinitionType.PHASE,
						phase.id(),
						"/transitions",
						phase.game(),
						transition,
						this.phases,
						PhaseDefinition::game,
						"phase"
					);
				}
				phase.onEnter().ifPresent(function -> requireFunction(DefinitionType.PHASE, phase.id(), "/on_enter", function));
				phase.onExit().ifPresent(function -> requireFunction(DefinitionType.PHASE, phase.id(), "/on_exit", function));
			}
			for (FieldDefinition field : this.fields.values()) {
				requireGame(DefinitionType.FIELD, field.id(), field.game());
				for (Identifier role : field.roles()) {
					requireSameGame(
						DefinitionType.FIELD,
						field.id(),
						"/roles",
						field.game(),
						role,
						this.roles,
						RoleDefinition::game,
						"role"
					);
				}
				for (Identifier phase : field.phases()) {
					requireSameGame(
						DefinitionType.FIELD,
						field.id(),
						"/phases",
						field.game(),
						phase,
						this.phases,
						PhaseDefinition::game,
						"phase"
					);
				}
				field.visibleWhen()
					.ifPresent(
						predicate -> requirePredicate(
							DefinitionType.FIELD,
							field.id(),
							"/visible_when",
							predicate
						)
					);
				field.exclusiveChoice().ifPresent(exclusive -> {
					GameDefinition game = this.games.get(field.game());
					if (game != null && game.apiVersion() < 2) {
						add(
							origin(DefinitionType.FIELD, field.id()),
							"UNSUPPORTED_API",
							"/type",
							"exclusive_choice requires a game with api_version 2"
						);
					}
					for (ExclusiveChoiceOption option : exclusive.options()) {
						option.previewPage().ifPresent(
							page -> requireSameGame(
								DefinitionType.FIELD,
								field.id(),
								"/options",
								field.game(),
								page,
								this.pages,
								PageDefinition::game,
								"preview page"
							)
						);
					}
				});
			}
			for (PageDefinition page : this.pages.values()) {
				requireGame(DefinitionType.PAGE, page.id(), page.game());
				if (!page.theme().equals(UiDefinitions.DEFAULT_THEME) && !this.themes.containsKey(page.theme())) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"MISSING_REFERENCE",
						"/theme",
						"theme " + page.theme() + " does not exist"
					);
				}
				validatePageReferences(page);
			}
			for (FlowDefinition flow : this.flows.values()) {
				requireGame(DefinitionType.FLOW, flow.id(), flow.game());
				validateAudience(DefinitionType.FLOW, flow.id(), flow.game(), flow.audience());
				flow.onStart().ifPresent(function -> requireFunction(DefinitionType.FLOW, flow.id(), "/on_start", function));
				flow.onPlayerComplete()
					.ifPresent(function -> requireFunction(DefinitionType.FLOW, flow.id(), "/on_player_complete", function));
				flow.onAllComplete()
					.ifPresent(function -> requireFunction(DefinitionType.FLOW, flow.id(), "/on_all_complete", function));
				validateFlowReferences(flow);
			}
			for (TaskDefinition task : this.tasks.values()) {
				validateTaskReferences(task);
			}
			for (PanelActionDefinition action : this.panelActions.values()) {
				requireGame(DefinitionType.PANEL_ACTION, action.id(), action.game());
				action.visibleWhen()
					.ifPresent(
						predicate -> requirePredicate(
							DefinitionType.PANEL_ACTION,
							action.id(),
							"/visible_when",
							predicate
						)
					);
				action.enabledWhen()
					.ifPresent(
						predicate -> requirePredicate(
							DefinitionType.PANEL_ACTION,
							action.id(),
							"/enabled_when",
							predicate
						)
					);
				for (Identifier phase : action.phases()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/phases",
						action.game(),
						phase,
						this.phases,
						PhaseDefinition::game,
						"phase"
					);
				}
				for (Identifier role : action.target().filter().roles()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/target/filter/roles",
						action.game(),
						role,
						this.roles,
						RoleDefinition::game,
						"role"
					);
				}
				for (Identifier team : action.target().filter().teams()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/target/filter/teams",
						action.game(),
						team,
						this.teams,
						TeamDefinition::game,
						"team"
					);
				}
				for (Identifier lifeState : action.target().filter().lifeStates()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/target/filter/life_states",
						action.game(),
						lifeState,
						this.lifeStates,
						LifeStateDefinition::game,
						"life state"
					);
				}
				for (Identifier flow : action.target().filter().completedFlows()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/target/filter/completed_flows",
						action.game(),
						flow,
						this.flows,
						FlowDefinition::game,
						"flow"
					);
				}
				for (Identifier flow : action.target().filter().incompleteFlows()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/target/filter/incomplete_flows",
						action.game(),
						flow,
						this.flows,
						FlowDefinition::game,
						"flow"
					);
				}
				for (Identifier flow : action.target().filter().statusFlows()) {
					requireSameGame(
						DefinitionType.PANEL_ACTION,
						action.id(),
						"/target/filter/status_flows",
						action.game(),
						flow,
						this.flows,
						FlowDefinition::game,
						"flow"
					);
				}
				validateOperation(action);
			}
		}

		private void validateTaskTimeline(
			final GameDefinition game,
			final TaskTimeline timeline
		) {
			if (game.apiVersion() < 2) {
				add(
					origin(DefinitionType.GAME, game.id()),
					"UNSUPPORTED_API",
					"/task_timeline",
					"task_timeline requires api_version 2"
				);
			}
			requireSameGame(
				DefinitionType.GAME,
				game.id(),
				"/task_timeline/initial_task",
				game.id(),
				timeline.initialTask(),
				this.tasks,
				TaskDefinition::game,
				"task"
			);
			requireSameGame(
				DefinitionType.GAME,
				game.id(),
				"/task_timeline/approval_phase",
				game.id(),
				timeline.approvalPhase(),
				this.phases,
				PhaseDefinition::game,
				"phase"
			);
			requireSameGame(
				DefinitionType.GAME,
				game.id(),
				"/task_timeline/start_phase",
				game.id(),
				timeline.startPhase(),
				this.phases,
				PhaseDefinition::game,
				"phase"
			);
			for (RequiredFieldRequirement requirement : timeline.preStartRequirements()) {
				validateAudience(
					DefinitionType.GAME,
					game.id(),
					game.id(),
					requirement.audience()
				);
				requireSameGame(
					DefinitionType.GAME,
					game.id(),
					"/task_timeline/pre_start_requirements",
					game.id(),
					requirement.field(),
					this.fields,
					FieldDefinition::game,
					"field"
				);
				FieldDefinition field = this.fields.get(requirement.field());
				if (
					field != null
						&& (field.scope() != FieldScope.PLAYER || !field.required())
				) {
					add(
						origin(DefinitionType.GAME, game.id()),
						"INVALID_CONSTRAINT",
						"/task_timeline/pre_start_requirements",
						"required_field must reference a required player-scoped field"
					);
				}
			}
		}

		private void validateReadiness(
			final GameDefinition game,
			final ReadinessDefinition readiness
		) {
			requireSameGame(
				DefinitionType.GAME,
				game.id(),
				"/readiness/phase",
				game.id(),
				readiness.phase(),
				this.phases,
				PhaseDefinition::game,
				"phase"
			);
			requireSameGame(
				DefinitionType.GAME,
				game.id(),
				"/readiness/action",
				game.id(),
				readiness.action(),
				this.panelActions,
				PanelActionDefinition::game,
				"panel action"
			);
			PanelActionDefinition action = this.panelActions.get(readiness.action());
			if (action == null) {
				return;
			}
			if (
				action.surface() != PanelSurface.HOST
					|| action.target().mode() != SelectionMode.NONE
					|| !action.phases().contains(readiness.phase())
					|| !(action.operation() instanceof StartFlowOperation start)
					|| start.completionPolicy() != CompletionPolicy.ALWAYS
			) {
				add(
					origin(DefinitionType.GAME, game.id()),
					"INVALID_CONSTRAINT",
					"/readiness/action",
					"readiness action must be a host start_flow action with target mode none, "
						+ "completion_policy always, and the readiness phase"
				);
				return;
			}
			FlowDefinition flow = this.flows.get(start.flow());
			if (flow == null || !flow.game().equals(game.id()) || !flow.required()) {
				add(
					origin(DefinitionType.GAME, game.id()),
					"INVALID_CONSTRAINT",
					"/readiness/action",
					"readiness action must reference a required flow from the same game"
				);
				return;
			}
			if (
				flow.onStart().isPresent()
					|| flow.onPlayerComplete().isPresent()
					|| flow.onAllComplete().isPresent()
			) {
				add(
					origin(DefinitionType.GAME, game.id()),
					"INVALID_CONSTRAINT",
					"/readiness/action",
					"readiness flow callbacks are not supported; use task or phase callbacks instead"
				);
			}
			boolean unsupportedNode = flow.nodes().values().stream().anyMatch(node ->
				!(node instanceof PageNode)
					&& !(node instanceof ConfirmNode)
					&& !(node instanceof CompleteNode)
			);
			if (unsupportedNode) {
				add(
					origin(DefinitionType.GAME, game.id()),
					"INVALID_CONSTRAINT",
					"/readiness/action",
					"readiness flow may contain only page, confirm, and complete nodes"
				);
			}
		}

		private void validateTaskGraph(final GameDefinition game) {
			Map<Identifier, Integer> marks = new HashMap<>();
			Set<Identifier> reported = new HashSet<>();
			for (TaskDefinition task : this.tasks.values()) {
				if (task.game().equals(game.id())) {
					validateTaskGraphNode(game.id(), task.id(), marks, reported);
				}
			}
		}

		private void validateTaskGraphNode(
			final Identifier gameId,
			final Identifier taskId,
			final Map<Identifier, Integer> marks,
			final Set<Identifier> reported
		) {
			int mark = marks.getOrDefault(taskId, 0);
			if (mark == 2) {
				return;
			}
			if (mark == 1) {
				if (reported.add(taskId)) {
					add(
						origin(DefinitionType.TASK, taskId),
						"INVALID_GRAPH",
						"/results",
						"task route graph contains a cycle at " + taskId
					);
				}
				return;
			}
			TaskDefinition task = this.tasks.get(taskId);
			if (task == null || !task.game().equals(gameId)) {
				return;
			}
			marks.put(taskId, 1);
			for (TaskResult result : task.results().values()) {
				result.route().nextTask().ifPresent(
					next -> {
						TaskDefinition target = this.tasks.get(next);
						if (target != null && target.game().equals(gameId)) {
							validateTaskGraphNode(gameId, next, marks, reported);
						}
					}
				);
			}
			marks.put(taskId, 2);
		}

		private void validateTaskReferences(final TaskDefinition task) {
			requireGame(DefinitionType.TASK, task.id(), task.game());
			GameDefinition game = this.games.get(task.game());
			if (game != null && game.apiVersion() < 2) {
				add(
					origin(DefinitionType.TASK, task.id()),
					"UNSUPPORTED_API",
					"/game",
					"tasks require a game with api_version 2"
				);
			}
			validateAudience(DefinitionType.TASK, task.id(), task.game(), task.audience());
			task.page().ifPresent(
				page -> requireSameGame(
					DefinitionType.TASK,
					task.id(),
					"/page",
					task.game(),
					page,
					this.pages,
					PageDefinition::game,
					"page"
				)
			);
			validateTaskCallbacks(task);
			task.onStartPlayers().ifPresent(
				callback -> validatePlayerCallback(task, "/on_start_players", callback)
			);
			for (TaskResult result : task.results().values()) {
				result.onApply().ifPresent(
					function -> requireFunction(
						DefinitionType.TASK,
						task.id(),
						"/results/" + result.id() + "/on_apply",
						function
					)
				);
				result.onApplyPlayers().ifPresent(
					callback -> validatePlayerCallback(
						task,
						"/results/" + result.id() + "/on_apply_players",
						callback
					)
				);
				validateTaskRoute(task, result);
			}
			for (TaskEventDefinition event : task.events()) {
				event.audience().ifPresent(
					audience -> validateAudience(
						DefinitionType.TASK,
						task.id(),
						task.game(),
						audience
					)
				);
			}
		}

		private void validateTaskCallbacks(final TaskDefinition task) {
			TaskCallbacks callbacks = task.callbacks();
			callbacks.onStart().ifPresent(
				function -> requireFunction(DefinitionType.TASK, task.id(), "/callbacks/on_start", function)
			);
			callbacks.onPause().ifPresent(
				function -> requireFunction(DefinitionType.TASK, task.id(), "/callbacks/on_pause", function)
			);
			callbacks.onResume().ifPresent(
				function -> requireFunction(DefinitionType.TASK, task.id(), "/callbacks/on_resume", function)
			);
			callbacks.onTimeout().ifPresent(
				function -> requireFunction(DefinitionType.TASK, task.id(), "/callbacks/on_timeout", function)
			);
			callbacks.onSettled().ifPresent(
				function -> requireFunction(DefinitionType.TASK, task.id(), "/callbacks/on_settled", function)
			);
		}

		private void validatePlayerCallback(
			final TaskDefinition task,
			final String pointer,
			final PlayerCallback callback
		) {
			requireFunction(DefinitionType.TASK, task.id(), pointer + "/function", callback.function());
			callback.audience().ifPresent(
				audience -> validateAudience(
					DefinitionType.TASK,
					task.id(),
					task.game(),
					audience
				)
			);
			for (Map.Entry<String, Identifier> entry : callback.fields().entrySet()) {
				requireSameGame(
					DefinitionType.TASK,
					task.id(),
					pointer + "/fields/" + escapePointer(entry.getKey()),
					task.game(),
					entry.getValue(),
					this.fields,
					FieldDefinition::game,
					"field"
				);
				FieldDefinition field = this.fields.get(entry.getValue());
				if (field != null && field.scope() != FieldScope.PLAYER) {
					add(
						origin(DefinitionType.TASK, task.id()),
						"INVALID_CONSTRAINT",
						pointer + "/fields/" + escapePointer(entry.getKey()),
						"player callback bindings require player-scoped fields"
					);
				}
			}
		}

		private void validateTaskRoute(
			final TaskDefinition task,
			final TaskResult result
		) {
			TaskRoute route = result.route();
			route.nextTask().ifPresent(
				next -> requireSameGame(
					DefinitionType.TASK,
					task.id(),
					"/results/" + result.id() + "/route/next_task",
					task.game(),
					next,
					this.tasks,
					TaskDefinition::game,
					"task"
				)
			);
			route.endPhase().ifPresent(
				phase -> requireSameGame(
					DefinitionType.TASK,
					task.id(),
					"/results/" + result.id() + "/route/end_phase",
					task.game(),
					phase,
					this.phases,
					PhaseDefinition::game,
					"phase"
				)
			);
			route.phase().ifPresent(
				phase -> requireSameGame(
					DefinitionType.TASK,
					task.id(),
					"/results/" + result.id() + "/route/phase",
					task.game(),
					phase,
					this.phases,
					PhaseDefinition::game,
					"phase"
				)
			);
			route.intermission().ifPresent(intermission -> {
				intermission.onStart().ifPresent(
					function -> requireFunction(
						DefinitionType.TASK,
						task.id(),
						"/results/" + result.id() + "/route/intermission/on_start",
						function
					)
				);
				intermission.onPause().ifPresent(
					function -> requireFunction(
						DefinitionType.TASK,
						task.id(),
						"/results/" + result.id() + "/route/intermission/on_pause",
						function
					)
				);
				intermission.onResume().ifPresent(
					function -> requireFunction(
						DefinitionType.TASK,
						task.id(),
						"/results/" + result.id() + "/route/intermission/on_resume",
						function
					)
				);
				intermission.onComplete().ifPresent(
					function -> requireFunction(
						DefinitionType.TASK,
						task.id(),
						"/results/" + result.id() + "/route/intermission/on_complete",
						function
					)
				);
			});
		}

		private void validateAudience(
			final DefinitionType ownerType,
			final Identifier owner,
			final Identifier game,
			final Audience audience
		) {
			for (Identifier role : audience.roles()) {
				requireSameGame(
					ownerType,
					owner,
					"/audience/roles",
					game,
					role,
					this.roles,
					RoleDefinition::game,
					"role"
				);
			}
			for (Identifier team : audience.teams()) {
				requireSameGame(
					ownerType,
					owner,
					"/audience/teams",
					game,
					team,
					this.teams,
					TeamDefinition::game,
					"team"
				);
			}
			for (Identifier lifeState : audience.lifeStates()) {
				requireSameGame(
					ownerType,
					owner,
					"/audience/life_states",
					game,
					lifeState,
					this.lifeStates,
					LifeStateDefinition::game,
					"life state"
				);
			}
		}

		private void validateFlowReferences(final FlowDefinition flow) {
			for (FlowNode node : flow.nodes().values()) {
				if (node instanceof PageNode page) {
					requireSameGame(
						DefinitionType.FLOW,
						flow.id(),
						"/nodes",
						flow.game(),
						page.page(),
						this.pages,
						PageDefinition::game,
						"page"
					);
				} else if (node instanceof ConfirmNode confirm) {
					requireSameGame(
						DefinitionType.FLOW,
						flow.id(),
						"/nodes",
						flow.game(),
						confirm.page(),
						this.pages,
						PageDefinition::game,
						"page"
					);
				} else if (node instanceof ChoiceNode choice) {
					requireSameGame(
						DefinitionType.FLOW,
						flow.id(),
						"/nodes",
						flow.game(),
						choice.page(),
						this.pages,
						PageDefinition::game,
						"page"
					);
					requireSameGame(
						DefinitionType.FLOW,
						flow.id(),
						"/nodes",
						flow.game(),
						choice.field(),
						this.fields,
						FieldDefinition::game,
						"field"
					);
					FieldDefinition field = this.fields.get(choice.field());
					if (
						field != null
							&& field.type() != FieldType.SINGLE_CHOICE
							&& field.type() != FieldType.EXCLUSIVE_CHOICE
					) {
						add(
							origin(DefinitionType.FLOW, flow.id()),
							"TYPE_MISMATCH",
							"/nodes",
							"choice node field must be single_choice or exclusive_choice"
						);
					}
					if (field != null) {
						for (ChoiceRoute route : choice.choices()) {
							if (!field.constraints().options().contains(route.value())) {
								add(
									origin(DefinitionType.FLOW, flow.id()),
									"MISSING_REFERENCE",
									"/nodes",
									"choice value " + route.value() + " is not declared by field " + field.id()
								);
							}
						}
					}
				} else if (node instanceof BranchNode branch) {
					for (BranchCase branchCase : branch.cases()) {
						requirePredicate(
							DefinitionType.FLOW,
							flow.id(),
							"/nodes",
							branchCase.predicate()
						);
					}
				} else if (node instanceof FunctionNode function) {
					requireFunction(DefinitionType.FLOW, flow.id(), "/nodes", function.function());
				} else if (node instanceof ChangeStateNode change) {
					switch (change.axis()) {
						case ROLE -> requireSameGame(
							DefinitionType.FLOW,
							flow.id(),
							"/nodes",
							flow.game(),
							change.value(),
							this.roles,
							RoleDefinition::game,
							"role"
						);
						case TEAM -> requireSameGame(
							DefinitionType.FLOW,
							flow.id(),
							"/nodes",
							flow.game(),
							change.value(),
							this.teams,
							TeamDefinition::game,
							"team"
						);
						case LIFE_STATE -> requireSameGame(
							DefinitionType.FLOW,
							flow.id(),
							"/nodes",
							flow.game(),
							change.value(),
							this.lifeStates,
							LifeStateDefinition::game,
							"life state"
						);
					}
				}
			}
		}

		private void validatePageReferences(final PageDefinition page) {
			Map<String, NodeDefinition> nodesById = new LinkedHashMap<>();
			collectPageNodes(page.root(), nodesById);
			ThemeDefinition theme = this.themes.get(page.theme());
			validatePageNode(page, page.root(), nodesById, theme);
		}

		private void collectPageNodes(
			final NodeDefinition node,
			final Map<String, NodeDefinition> nodesById
		) {
			node.id().ifPresent(id -> nodesById.put(id, node));
			if (node.content() instanceof ChildrenContent children) {
				children.children().forEach(child -> collectPageNodes(child, nodesById));
			} else if (node.content() instanceof SingleChildContent single) {
				collectPageNodes(single.child(), nodesById);
			} else if (node.content() instanceof RepeatContent repeat) {
				collectPageNodes(repeat.template(), nodesById);
			}
		}

		private void validatePageNode(
			final PageDefinition page,
			final NodeDefinition node,
			final Map<String, NodeDefinition> nodesById,
			final ThemeDefinition theme
		) {
			node.style().ifPresent(style -> validatePageStyle(page, node.pointer() + "/style", style, theme));
			node.responsive()
				.forEach(
					(tier, override) -> override.style()
						.ifPresent(
							style -> validatePageStyle(
								page,
								node.pointer() + "/responsive/" + tier.name().toLowerCase(Locale.ROOT) + "/style",
								style,
								theme
							)
						)
				);
			node.events()
				.forEach(
					(event, binding) -> {
						String pointer = node.pointer()
							+ "/events/"
							+ event.name().toLowerCase(Locale.ROOT);
						binding.motion()
							.ifPresent(
								motion -> validatePageMotion(
									page,
									node,
									pointer + "/motion",
									motion,
									theme
								)
							);
						binding.sound()
							.ifPresent(sound -> validatePageSound(page, pointer + "/sound", sound, theme));
					}
				);
			node.visibleWhen()
				.ifPresent(
					condition -> validateCondition(
						page,
						condition,
						nodesById,
						node.pointer() + "/visible_when"
					)
				);

			if (node.content() instanceof ChildrenContent children) {
				children.children().forEach(child -> validatePageNode(page, child, nodesById, theme));
			} else if (node.content() instanceof SingleChildContent single) {
				validatePageNode(page, single.child(), nodesById, theme);
			} else if (node.content() instanceof TextContent text) {
				validateTextBindings(page, text.text(), nodesById, node.pointer() + "/text");
			} else if (node.content() instanceof UiDefinitions.ImageContent image) {
				image.alternativeText()
					.ifPresent(text -> validateTextBindings(page, text, nodesById, node.pointer() + "/alt"));
			} else if (node.content() instanceof ButtonContent button) {
				validateTextBindings(page, button.label(), nodesById, node.pointer() + "/label");
				button.enabledWhen()
					.ifPresent(
						condition -> validateCondition(
							page,
							condition,
							nodesById,
							node.pointer() + "/enabled_when"
						)
					);
				button.disabledReason()
					.ifPresent(
						text -> validateTextBindings(
							page,
							text,
							nodesById,
							node.pointer() + "/disabled_reason"
						)
					);
				validatePageAction(page, node, button.action());
			} else if (node.content() instanceof ProgressContent progress) {
				requireValueType(
					page,
					progress.value(),
					nodesById,
					node.pointer() + "/value",
					UiValueType.INTEGER
				);
				requireValueType(
					page,
					progress.minimum(),
					nodesById,
					node.pointer() + "/min",
					UiValueType.INTEGER
				);
				requireValueType(
					page,
					progress.maximum(),
					nodesById,
					node.pointer() + "/max",
					UiValueType.INTEGER
				);
				progress.label()
					.ifPresent(text -> validateTextBindings(page, text, nodesById, node.pointer() + "/label"));
			} else if (node.content() instanceof PlayerHeadContent playerHead) {
				requireTextLikeValue(page, playerHead.uuid(), nodesById, node.pointer() + "/uuid");
				requireTextLikeValue(page, playerHead.name(), nodesById, node.pointer() + "/name");
				requireValueType(
					page,
					playerHead.online(),
					nodesById,
					node.pointer() + "/online",
					UiValueType.BOOLEAN
				);
			} else if (node.content() instanceof RepeatContent repeat) {
				requireValueType(
					page,
					repeat.items(),
					nodesById,
					node.pointer() + "/items",
					UiValueType.COLLECTION
				);
				UiValueType keyType = validateValue(
					page,
					repeat.itemKey(),
					nodesById,
					node.pointer() + "/item_key"
				);
				if (
					keyType != UiValueType.UNKNOWN
						&& keyType != UiValueType.STRING
						&& keyType != UiValueType.IDENTIFIER
				) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"TYPE_MISMATCH",
						node.pointer() + "/item_key",
						"Repeat.item_key must resolve to a stable text or identifier"
					);
				}
				validatePageNode(page, repeat.template(), nodesById, theme);
			} else if (node.content() instanceof FieldInputContent input) {
				FieldDefinition field = requirePageField(
					page,
					input.field(),
					node.pointer() + "/field"
				);
				if (field != null && !input.supports(field.type())) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"TYPE_MISMATCH",
						node.pointer() + "/presentation",
						"presentation is incompatible with field type "
							+ field.type().name().toLowerCase(Locale.ROOT)
					);
				}
			}
		}

		private void validatePageStyle(
			final PageDefinition page,
			final String pointer,
			final String style,
			final ThemeDefinition theme
		) {
			if (!BUILT_IN_UI_STYLES.contains(style) && (theme == null || !theme.styles().containsKey(style))) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"MISSING_REFERENCE",
					pointer,
					"style " + style + " does not exist in the effective theme"
				);
			}
		}

		private void validatePageMotion(
			final PageDefinition page,
			final NodeDefinition node,
			final String pointer,
			final String motion,
			final ThemeDefinition theme
		) {
			if (!BUILT_IN_UI_MOTIONS.contains(motion) && (theme == null || !theme.motions().containsKey(motion))) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"MISSING_REFERENCE",
					pointer,
					"motion " + motion + " does not exist in the effective theme"
				);
				return;
			}
			if (
				node.type() != NodeType.PROGRESS
					&& theme != null
					&& theme.motions().containsKey(motion)
					&& theme.motions()
						.get(motion)
						.tracks()
						.stream()
						.anyMatch(track -> track.property() == MotionProperty.PROGRESS_VALUE)
			) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"TYPE_MISMATCH",
					pointer,
					"progress_value motion tracks may only be bound to progress nodes"
				);
			}
		}

		private void validatePageSound(
			final PageDefinition page,
			final String pointer,
			final String sound,
			final ThemeDefinition theme
		) {
			if (!BUILT_IN_UI_SOUNDS.contains(sound) && (theme == null || !theme.soundCues().containsKey(sound))) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"MISSING_REFERENCE",
					pointer,
					"sound cue " + sound + " does not exist in the effective theme"
				);
			}
		}

		private void validatePageAction(
			final PageDefinition page,
			final NodeDefinition node,
			final UiAction action
		) {
			if (node.id().isEmpty()) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"MISSING_KEY",
					node.pointer() + "/id",
					"buttons require a stable id so action requests can be revalidated"
				);
			}
			action.registeredAction()
				.ifPresent(
					actionId -> requireSameGame(
						DefinitionType.PAGE,
						page.id(),
						node.pointer() + "/action/action",
						page.game(),
						actionId,
						this.panelActions,
						PanelActionDefinition::game,
						"panel action"
					)
				);
		}

		private FieldDefinition requirePageField(
			final PageDefinition page,
			final Identifier fieldId,
			final String pointer
		) {
			FieldDefinition field = this.fields.get(fieldId);
			if (field == null) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"MISSING_REFERENCE",
					pointer,
					"field " + fieldId + " does not exist"
				);
				return null;
			}
			if (!page.game().equals(field.game())) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"CROSS_GAME_REFERENCE",
					pointer,
					"field " + fieldId + " belongs to " + field.game()
				);
				return null;
			}
			return field;
		}

		private void validateTextBindings(
			final PageDefinition page,
			final TextTemplate text,
			final Map<String, NodeDefinition> nodesById,
			final String pointer
		) {
			if (text instanceof BoundText bound) {
				validateValue(page, bound.binding(), nodesById, pointer + "/bind");
				bound.fallback()
					.ifPresent(fallback -> validateTextBindings(page, fallback, nodesById, pointer + "/fallback"));
			} else if (text instanceof TranslatedText translated) {
				for (int index = 0; index < translated.arguments().size(); index++) {
					validateValue(
						page,
						translated.arguments().get(index),
						nodesById,
						pointer + "/args/" + index
					);
				}
				translated.fallback()
					.ifPresent(fallback -> validateTextBindings(page, fallback, nodesById, pointer + "/fallback"));
			} else if (text instanceof ConcatenatedText concatenated) {
				for (int index = 0; index < concatenated.parts().size(); index++) {
					validateTextBindings(
						page,
						concatenated.parts().get(index),
						nodesById,
						pointer + "/concat/" + index
					);
				}
			}
		}

		private void validateCondition(
			final PageDefinition page,
			final ConditionExpression condition,
			final Map<String, NodeDefinition> nodesById,
			final String pointer
		) {
			if (condition instanceof ComparisonCondition comparison) {
				UiValueType left = validateValue(page, comparison.left(), nodesById, pointer);
				UiValueType right = validateValue(page, comparison.right(), nodesById, pointer);
				boolean numeric = comparison.operator() != ConditionOperator.EQ
					&& comparison.operator() != ConditionOperator.NOT_EQ;
				if (numeric) {
					if (
						left != UiValueType.UNKNOWN
							&& right != UiValueType.UNKNOWN
							&& (left != UiValueType.INTEGER || right != UiValueType.INTEGER)
					) {
						add(
							origin(DefinitionType.PAGE, page.id()),
							"TYPE_MISMATCH",
							pointer,
							"numeric comparisons require integer operands"
						);
					}
				} else if (!compatibleTypes(left, right)) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"TYPE_MISMATCH",
						pointer,
						"comparison operands have incompatible types"
					);
				}
			} else if (condition instanceof JunctionCondition junction) {
				for (int index = 0; index < junction.conditions().size(); index++) {
					validateCondition(
						page,
						junction.conditions().get(index),
						nodesById,
						pointer + "/" + index
					);
				}
			} else if (condition instanceof NotCondition not) {
				validateCondition(page, not.condition(), nodesById, pointer + "/not");
			} else if (condition instanceof ExistsCondition exists) {
				validateValue(page, exists.binding(), nodesById, pointer + "/exists");
			}
		}

		private void requireValueType(
			final PageDefinition page,
			final ValueExpression expression,
			final Map<String, NodeDefinition> nodesById,
			final String pointer,
			final UiValueType required
		) {
			UiValueType actual = validateValue(page, expression, nodesById, pointer);
			if (actual != UiValueType.UNKNOWN && actual != required) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"TYPE_MISMATCH",
					pointer,
					"expected " + required.name().toLowerCase(Locale.ROOT) + " value"
				);
			}
		}

		private void requireTextLikeValue(
			final PageDefinition page,
			final ValueExpression expression,
			final Map<String, NodeDefinition> nodesById,
			final String pointer
		) {
			UiValueType actual = validateValue(page, expression, nodesById, pointer);
			if (
				actual != UiValueType.UNKNOWN
					&& actual != UiValueType.STRING
					&& actual != UiValueType.IDENTIFIER
			) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"TYPE_MISMATCH",
					pointer,
					"expected text or identifier value"
				);
			}
		}

		private UiValueType validateValue(
			final PageDefinition page,
			final ValueExpression expression,
			final Map<String, NodeDefinition> nodesById,
			final String pointer
		) {
			if (expression instanceof LiteralValue literal) {
				return literalType(literal.canonicalJson());
			}
			if (!(expression instanceof BindingValue binding)) {
				return UiValueType.UNKNOWN;
			}
			String path = binding.path();
			if (path.startsWith("fields/")) {
				Identifier id = Identifier.tryParse(path.substring("fields/".length()));
				FieldDefinition field = id == null ? null : requirePageField(page, id, pointer);
				return field == null ? UiValueType.UNKNOWN : fieldValueType(field.type());
			}
			if (path.startsWith("flow/fields/")) {
				Identifier id = Identifier.tryParse(path.substring("flow/fields/".length()));
				FieldDefinition field = id == null ? null : requirePageField(page, id, pointer);
				return field == null ? UiValueType.UNKNOWN : fieldValueType(field.type());
			}
			if (path.startsWith("ui/") || path.startsWith("ui.")) {
				String[] parts = path.startsWith("ui/")
					? path.split("/", -1)
					: path.split("\\.", -1);
				NodeDefinition target = parts.length == 3 ? nodesById.get(parts[1]) : null;
				if (target == null) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"MISSING_REFERENCE",
						pointer,
						"UI field node " + (parts.length > 1 ? parts[1] : "") + " does not exist"
					);
					return UiValueType.UNKNOWN;
				}
				if (!(target.content() instanceof FieldInputContent input)) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"TYPE_MISMATCH",
						pointer,
						"ui binding target must be a field_input node"
					);
					return UiValueType.UNKNOWN;
				}
				FieldDefinition field = requirePageField(page, input.field(), pointer);
				return field == null ? UiValueType.UNKNOWN : fieldValueType(field.type());
			}
			return fixedBindingType(path);
		}

		private static UiValueType fixedBindingType(final String path) {
			return switch (path) {
				case "viewer.online",
					"viewer.admin",
					"viewer.host",
					"viewer.ready",
					"session.connected",
					"item.online",
					"item.admin",
					"item.host",
					"item.ready" -> UiValueType.BOOLEAN;
				case "session.revision",
					"session.generation",
					"flow.version",
					"flow.completed",
					"flow.total",
					"flow.remaining",
					"flow.percent" -> UiValueType.INTEGER;
				case "viewer.role",
					"viewer.team",
					"viewer.life_state",
					"session.game",
					"session.phase",
					"flow.id",
					"item.role",
					"item.team",
					"item.life_state" -> UiValueType.IDENTIFIER;
				case "session.players" -> UiValueType.COLLECTION;
				default -> UiValueType.STRING;
			};
		}

		private static UiValueType fieldValueType(final FieldType type) {
			return switch (type) {
				case BOOLEAN -> UiValueType.BOOLEAN;
				case INTEGER -> UiValueType.INTEGER;
				case IDENTIFIER -> UiValueType.IDENTIFIER;
				case MULTI_CHOICE -> UiValueType.COLLECTION;
				case STRING, SINGLE_CHOICE, EXCLUSIVE_CHOICE -> UiValueType.STRING;
			};
		}

		private static UiValueType literalType(final String canonicalJson) {
			try {
				JsonElement value = JsonParser.parseString(canonicalJson);
				if (value.isJsonNull()) {
					return UiValueType.NULL;
				}
				if (value.isJsonArray()) {
					return UiValueType.COLLECTION;
				}
				if (!value.isJsonPrimitive()) {
					return UiValueType.UNKNOWN;
				}
				JsonPrimitive primitive = value.getAsJsonPrimitive();
				if (primitive.isBoolean()) {
					return UiValueType.BOOLEAN;
				}
				if (primitive.isNumber()) {
					return isLongIntegral(primitive) ? UiValueType.INTEGER : UiValueType.UNKNOWN;
				}
				return parseIdentifier(primitive.getAsString()) == null
					? UiValueType.STRING
					: UiValueType.IDENTIFIER;
			} catch (JsonParseException error) {
				return UiValueType.UNKNOWN;
			}
		}

		private static boolean compatibleTypes(final UiValueType left, final UiValueType right) {
			if (left == UiValueType.UNKNOWN || right == UiValueType.UNKNOWN) {
				return true;
			}
			if (left == UiValueType.NULL || right == UiValueType.NULL) {
				return true;
			}
			if (left == right) {
				return true;
			}
			return (left == UiValueType.STRING && right == UiValueType.IDENTIFIER)
				|| (left == UiValueType.IDENTIFIER && right == UiValueType.STRING);
		}

		private enum UiValueType {
			BOOLEAN,
			INTEGER,
			STRING,
			IDENTIFIER,
			COLLECTION,
			NULL,
			UNKNOWN
		}

		private void validateOperation(final PanelActionDefinition action) {
			PanelOperation operation = action.operation();
			if (operation instanceof StartFlowOperation startFlow) {
				requireSameGame(
					DefinitionType.PANEL_ACTION,
					action.id(),
					"/operation/flow",
					action.game(),
					startFlow.flow(),
					this.flows,
					FlowDefinition::game,
					"flow"
				);
			} else if (operation instanceof AssignRoleOperation assignRole) {
				requireSameGame(
					DefinitionType.PANEL_ACTION,
					action.id(),
					"/operation/role",
					action.game(),
					assignRole.role(),
					this.roles,
					RoleDefinition::game,
					"role"
				);
				assignRole.flow()
					.ifPresent(
						flow -> requireSameGame(
							DefinitionType.PANEL_ACTION,
							action.id(),
							"/operation/flow",
							action.game(),
							flow,
							this.flows,
							FlowDefinition::game,
							"flow"
						)
					);
			} else if (operation instanceof AssignTeamOperation assignTeam) {
				requireSameGame(
					DefinitionType.PANEL_ACTION,
					action.id(),
					"/operation/team",
					action.game(),
					assignTeam.team(),
					this.teams,
					TeamDefinition::game,
					"team"
				);
				assignTeam.flow()
					.ifPresent(
						flow -> requireSameGame(
							DefinitionType.PANEL_ACTION,
							action.id(),
							"/operation/flow",
							action.game(),
							flow,
							this.flows,
							FlowDefinition::game,
							"flow"
						)
					);
			} else if (operation instanceof AssignLifeStateOperation assignLifeState) {
				requireSameGame(
					DefinitionType.PANEL_ACTION,
					action.id(),
					"/operation/life_state",
					action.game(),
					assignLifeState.lifeState(),
					this.lifeStates,
					LifeStateDefinition::game,
					"life state"
				);
				assignLifeState.flow()
					.ifPresent(
						flow -> requireSameGame(
							DefinitionType.PANEL_ACTION,
							action.id(),
							"/operation/flow",
							action.game(),
							flow,
							this.flows,
							FlowDefinition::game,
							"flow"
						)
					);
			} else if (operation instanceof TransitionPhaseOperation transition) {
				requireSameGame(
					DefinitionType.PANEL_ACTION,
					action.id(),
					"/operation/phase",
					action.game(),
					transition.phase(),
					this.phases,
					PhaseDefinition::game,
					"phase"
				);
			} else if (operation instanceof RunFunctionOperation runFunction) {
				requireFunction(
					DefinitionType.PANEL_ACTION,
					action.id(),
					"/operation/function",
					runFunction.function()
				);
			}
		}

		private void requireGame(
			final DefinitionType ownerType,
			final Identifier owner,
			final Identifier game
		) {
			if (!this.games.containsKey(game)) {
				add(
					origin(ownerType, owner),
					"MISSING_REFERENCE",
					"/game",
					"game " + game + " does not exist"
				);
			}
		}

		private void requireFunction(
			final DefinitionType ownerType,
			final Identifier owner,
			final String pointer,
			final Identifier function
		) {
			if (!this.functions.contains(function)) {
				add(
					origin(ownerType, owner),
					"MISSING_EXTERNAL_RESOURCE",
					pointer,
					"function " + function + " does not exist"
				);
			}
		}

		private void requirePredicate(
			final DefinitionType ownerType,
			final Identifier owner,
			final String pointer,
			final Identifier predicate
		) {
			if (!this.predicates.contains(predicate)) {
				add(
					origin(ownerType, owner),
					"MISSING_EXTERNAL_RESOURCE",
					pointer,
					"predicate " + predicate + " does not exist"
				);
			}
		}

		private <T> void requireSameGame(
			final DefinitionType ownerType,
			final Identifier owner,
			final String pointer,
			final Identifier expectedGame,
			final Identifier targetId,
			final Map<Identifier, T> targets,
			final java.util.function.Function<T, Identifier> gameGetter,
			final String targetType
		) {
			T target = targets.get(targetId);
			if (target == null) {
				add(
					origin(ownerType, owner),
					"MISSING_REFERENCE",
					pointer,
					targetType + " " + targetId + " does not exist"
				);
			} else if (!expectedGame.equals(gameGetter.apply(target))) {
				add(
					origin(ownerType, owner),
					"CROSS_GAME_REFERENCE",
					pointer,
					targetType + " " + targetId + " belongs to " + gameGetter.apply(target)
				);
			}
		}

		private Source origin(final DefinitionType type, final Identifier id) {
			return this.origins.get(new DefinitionKey(type, id));
		}

		private void add(
			final Source source,
			final String code,
			final String pointer,
			final String message
		) {
			if (source == null) {
				this.problems.report(
					code,
					null,
					null,
					null,
					"unknown",
					pointer,
					message
				);
				return;
			}
			this.problems.report(
				code,
				source.type(),
				source.id(),
				source.resource(),
				source.sourcePack(),
				pointer,
				message == null ? "unknown error" : message
			);
		}
	}

	private static final class ProblemCollector extends AbstractList<Problem> {
		private final List<Problem> retained = new ArrayList<>();
		private int totalCount;

		private void report(
			final String code,
			final DefinitionType type,
			final Identifier definitionId,
			final Identifier resource,
			final String sourcePack,
			final String pointer,
			final String message
		) {
			this.totalCount++;
			if (this.retained.size() < MAX_DIAGNOSTIC_DETAILS) {
				this.retained.add(
					new Problem(
						code,
						type,
						definitionId,
						resource,
						sourcePack,
						pointer,
						message
					)
				);
			}
		}

		private int totalCount() {
			return this.totalCount;
		}

		@Override
		public Problem get(final int index) {
			return this.retained.get(index);
		}

		@Override
		public int size() {
			return this.retained.size();
		}
	}

	private record DefinitionKey(DefinitionType type, Identifier id) {
	}

	private static final class ObjectReader {
		private final Source source;
		private final String basePointer;
		private final JsonObject object;
		private final ProblemCollector problems;
		private final Set<String> consumed = new HashSet<>();

		private ObjectReader(
			final Source source,
			final String basePointer,
			final JsonObject object,
			final ProblemCollector problems
		) {
			this.source = source;
			this.basePointer = basePointer;
			this.object = object;
			this.problems = problems;
		}

		private ObjectReader child(final String name, final JsonObject child) {
			return new ObjectReader(this.source, pointer("/" + escapePointer(name)), child, this.problems);
		}

		private String pointer(final String suffix) {
			return this.basePointer + suffix;
		}

		private JsonElement take(final String name) {
			this.consumed.add(name);
			return this.object.get(name);
		}

		private JsonElement required(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				add("MISSING_KEY", "/" + escapePointer(name), "required key is missing");
			}
			return value;
		}

		private Integer requiredInt(final String name) {
			JsonElement value = required(name);
			return value == null ? null : integer(name, value);
		}

		private int optionalInt(final String name, final int defaultValue) {
			JsonElement value = take(name);
			Integer parsed = value == null ? null : integer(name, value);
			return parsed == null ? defaultValue : parsed;
		}

		private Optional<Integer> optionalInteger(final String name) {
			JsonElement value = take(name);
			return value == null ? Optional.empty() : Optional.ofNullable(integer(name, value));
		}

		private Optional<Long> optionalLong(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return Optional.empty();
			}
			if (!isIntegral(value)) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an integer");
				return Optional.empty();
			}
			try {
				return Optional.of(value.getAsBigDecimal().longValueExact());
			} catch (ArithmeticException | NumberFormatException error) {
				add("OUT_OF_RANGE", "/" + escapePointer(name), "integer is outside the supported range");
				return Optional.empty();
			}
		}

		private Long requiredLong(final String name) {
			JsonElement value = required(name);
			if (value == null) {
				return null;
			}
			if (!isIntegral(value)) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an integer");
				return null;
			}
			try {
				return value.getAsBigDecimal().longValueExact();
			} catch (ArithmeticException | NumberFormatException error) {
				add("OUT_OF_RANGE", "/" + escapePointer(name), "integer is outside the supported range");
				return null;
			}
		}

		private Integer integer(final String name, final JsonElement value) {
			if (!isIntegral(value)) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an integer");
				return null;
			}
			try {
				return value.getAsBigDecimal().intValueExact();
			} catch (ArithmeticException | NumberFormatException error) {
				add("OUT_OF_RANGE", "/" + escapePointer(name), "integer is outside the supported range");
				return null;
			}
		}

		private boolean optionalBoolean(final String name, final boolean defaultValue) {
			JsonElement value = take(name);
			if (value == null) {
				return defaultValue;
			}
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected a boolean");
				return defaultValue;
			}
			return value.getAsBoolean();
		}

		private String requiredString(final String name) {
			JsonElement value = required(name);
			return value == null ? null : string(name, value);
		}

		private Optional<String> optionalString(final String name) {
			JsonElement value = take(name);
			return value == null ? Optional.empty() : Optional.ofNullable(string(name, value));
		}

		private String string(final String name, final JsonElement value) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected a string");
				return null;
			}
			String result = value.getAsString();
			if (result.length() > MAX_SHORT_STRING_LENGTH) {
				add("RESOURCE_LIMIT", "/" + escapePointer(name), "string is too long");
				return null;
			}
			return result;
		}

		private String requiredLocalId(final String name) {
			String value = requiredString(name);
			if (value != null && !LOCAL_ID.matcher(value).matches()) {
				add("INVALID_LOCAL_ID", "/" + escapePointer(name), "expected lowercase [a-z0-9_.-]+");
				return null;
			}
			return value;
		}

		private Optional<String> optionalLocalId(final String name) {
			Optional<String> value = optionalString(name);
			if (value.isPresent() && !LOCAL_ID.matcher(value.orElseThrow()).matches()) {
				add("INVALID_LOCAL_ID", "/" + escapePointer(name), "expected lowercase [a-z0-9_.-]+");
				return Optional.empty();
			}
			return value;
		}

		private Identifier requiredIdentifier(final String name) {
			String value = requiredString(name);
			return value == null ? null : identifier(name, value);
		}

		private Optional<Identifier> optionalIdentifier(final String name) {
			Optional<String> value = optionalString(name);
			return value.flatMap(text -> Optional.ofNullable(identifier(name, text)));
		}

		private Identifier identifier(final String name, final String value) {
			Identifier identifier = parseIdentifier(value);
			if (identifier == null) {
				add(
					"INVALID_IDENTIFIER",
					"/" + escapePointer(name),
					"expected an explicitly namespaced identifier"
				);
			}
			return identifier;
		}

		private Set<Identifier> requiredIdentifierSet(final String name) {
			JsonArray array = requiredArray(name);
			return identifierSet(name, array);
		}

		private Set<Identifier> optionalIdentifierSet(final String name) {
			JsonArray array = optionalArray(name);
			return array == null ? Set.of() : identifierSet(name, array);
		}

		private Set<Identifier> identifierSet(final String name, final JsonArray array) {
			if (array == null) {
				return Set.of();
			}
			Set<Identifier> result = new LinkedHashSet<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					add(
						"TYPE_MISMATCH",
						"/" + escapePointer(name) + "/" + index,
						"expected an identifier string"
					);
					continue;
				}
				Identifier identifier = parseIdentifier(element.getAsString());
				if (identifier == null) {
					add(
						"INVALID_IDENTIFIER",
						"/" + escapePointer(name) + "/" + index,
						"expected an explicitly namespaced identifier"
					);
				} else if (!result.add(identifier)) {
					add(
						"DUPLICATE_VALUE",
						"/" + escapePointer(name) + "/" + index,
						"duplicate identifier " + identifier
					);
				}
			}
			return Set.copyOf(result);
		}

		private List<String> optionalStringList(final String name) {
			JsonArray array = optionalArray(name);
			if (array == null) {
				return List.of();
			}
			List<String> result = new ArrayList<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					add(
						"TYPE_MISMATCH",
						"/" + escapePointer(name) + "/" + index,
						"expected a string"
					);
				} else {
					result.add(element.getAsString());
				}
			}
			return List.copyOf(result);
		}

		private JsonArray requiredArray(final String name) {
			JsonElement value = required(name);
			if (value == null) {
				return new JsonArray();
			}
			if (!value.isJsonArray()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an array");
				return new JsonArray();
			}
			return value.getAsJsonArray();
		}

		private JsonArray optionalArray(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonArray()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an array");
				return null;
			}
			return value.getAsJsonArray();
		}

		private JsonObject requiredObject(final String name) {
			JsonElement value = required(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonObject()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an object");
				return null;
			}
			return value.getAsJsonObject();
		}

		private JsonObject optionalObject(final String name) {
			JsonElement value = take(name);
			if (value == null) {
				return null;
			}
			if (!value.isJsonObject()) {
				add("TYPE_MISMATCH", "/" + escapePointer(name), "expected an object");
				return null;
			}
			return value.getAsJsonObject();
		}

		private JsonElement optionalElement(final String name) {
			return take(name);
		}

		private RichText requiredText(final String name) {
			JsonElement value = required(name);
			return value == null ? null : parseText(this.source, pointer("/" + escapePointer(name)), value, this.problems);
		}

		private Optional<RichText> optionalText(final String name) {
			JsonElement value = take(name);
			return value == null
				? Optional.empty()
				: Optional.ofNullable(
					parseText(this.source, pointer("/" + escapePointer(name)), value, this.problems)
				);
		}

		private <E extends Enum<E>> E requiredEnum(final String name, final Class<E> type) {
			String value = requiredString(name);
			return value == null ? null : parseEnum(name, value, type);
		}

		private <E extends Enum<E>> E optionalEnum(
			final String name,
			final Class<E> type,
			final E defaultValue
		) {
			Optional<String> value = optionalString(name);
			return value.map(text -> parseEnum(name, text, type)).orElse(defaultValue);
		}

		private <E extends Enum<E>> E parseEnum(
			final String name,
			final String value,
			final Class<E> type
		) {
			try {
				return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException error) {
				add(
					"INVALID_ENUM",
					"/" + escapePointer(name),
					"unknown " + type.getSimpleName() + " value " + value
				);
				return null;
			}
		}

		private void finish() {
			for (String key : this.object.keySet()) {
				if (!this.consumed.contains(key)) {
					add("UNKNOWN_KEY", "/" + escapePointer(key), "unknown key");
				}
			}
		}

		private void add(final String code, final String suffix, final String message) {
			this.problems.report(
				code,
				this.source.type(),
				this.source.id(),
				this.source.resource(),
				this.source.sourcePack(),
				pointer(suffix),
				message
			);
		}
	}

	private static RichText parseText(
		final Source source,
		final String pointer,
		final JsonElement value,
		final ProblemCollector problems
	) {
		String json = value.toString();
		if (json.length() > MAX_TEXT_JSON_LENGTH) {
			problems.report(
				"RESOURCE_LIMIT",
				source.type(),
				source.id(),
				source.resource(),
				source.sourcePack(),
				pointer,
				"text component exceeds " + MAX_TEXT_JSON_LENGTH + " characters"
			);
			return null;
		}
		DataResult<Component> result = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value);
		Optional<Component> component = result.result();
		if (component.isEmpty()) {
			String message = result.error().map(DataResult.Error::message).orElse("invalid text component");
			problems.report(
				"INVALID_TEXT_COMPONENT",
				source.type(),
				source.id(),
				source.resource(),
				source.sourcePack(),
				pointer,
				message
			);
			return null;
		}
		String plainText = component.orElseThrow().getString();
		if (plainText.isBlank()) {
			problems.report(
				"EMPTY_TEXT",
				source.type(),
				source.id(),
				source.resource(),
				source.sourcePack(),
				pointer,
				"visible text must not be blank"
			);
			return null;
		}
		return new RichText(json, plainText);
	}

	private static Identifier parseIdentifier(final String value) {
		int separator = value.indexOf(':');
		if (separator <= 0 || separator == value.length() - 1) {
			return null;
		}
		return Identifier.tryParse(value);
	}

	private static boolean isIntegral(final JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return false;
		}
		try {
			return value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
		} catch (NumberFormatException error) {
			return false;
		}
	}

	private static boolean isLongIntegral(final JsonElement value) {
		if (!isIntegral(value)) {
			return false;
		}
		try {
			value.getAsBigDecimal().longValueExact();
			return true;
		} catch (ArithmeticException | NumberFormatException error) {
			return false;
		}
	}

	private static String escapePointer(final String value) {
		return value.replace("~", "~0").replace("/", "~1");
	}

	private static String canonical(final JsonElement value) {
		if (value.isJsonObject()) {
			StringBuilder result = new StringBuilder("{");
			List<Map.Entry<String, JsonElement>> entries = value.getAsJsonObject()
				.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByKey())
				.toList();
			for (int index = 0; index < entries.size(); index++) {
				if (index > 0) {
					result.append(',');
				}
				Map.Entry<String, JsonElement> entry = entries.get(index);
				result.append(new JsonPrimitive(entry.getKey()))
					.append(':')
					.append(canonical(entry.getValue()));
			}
			return result.append('}').toString();
		}
		if (value.isJsonArray()) {
			StringBuilder result = new StringBuilder("[");
			for (int index = 0; index < value.getAsJsonArray().size(); index++) {
				if (index > 0) {
					result.append(',');
				}
				result.append(canonical(value.getAsJsonArray().get(index)));
			}
			return result.append(']').toString();
		}
		return value.toString();
	}

	private static String sha256(final String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", error);
		}
	}

	private static JsonElement parseStrict(final String json) throws IOException {
		JsonReader reader = new JsonReader(new StringReader(json));
		reader.setStrictness(Strictness.STRICT);
		JsonElement value = readStrictValue(reader, 0);
		if (reader.peek() != JsonToken.END_DOCUMENT) {
			throw new JsonParseException("trailing content after JSON value");
		}
		return value;
	}

	private static JsonElement readStrictValue(final JsonReader reader, final int depth) throws IOException {
		if (depth > MAX_JSON_DEPTH) {
			throw new JsonParseException("JSON nesting exceeds " + MAX_JSON_DEPTH);
		}
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				JsonObject object = new JsonObject();
				Set<String> names = new HashSet<>();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (!names.add(name)) {
						throw new JsonParseException("duplicate object key " + name);
					}
					object.add(name, readStrictValue(reader, depth + 1));
				}
				reader.endObject();
				yield object;
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				JsonArray array = new JsonArray();
				while (reader.hasNext()) {
					array.add(readStrictValue(reader, depth + 1));
				}
				reader.endArray();
				yield array;
			}
			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}
			default -> throw new JsonParseException("unexpected JSON token " + reader.peek());
		};
	}
}
