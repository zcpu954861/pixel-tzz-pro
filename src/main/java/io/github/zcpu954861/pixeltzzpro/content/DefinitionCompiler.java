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
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.OpeningCountdownReference;
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
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookReference;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookSet;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AssetSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSelector;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundPatch;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundRole;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EffectUse;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldReference;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistorySpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.IdentifierKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.PlayerDataParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticFallbackSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextVariant;
import io.github.zcpu954861.pixeltzzpro.message.GraphemeClusters;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.AuditPolicy;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.CooldownScope;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.ExecutionContext;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.HistoryRelease;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.HistorySource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerActionFeedback;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSourceType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerHistoryPresentation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOpenPageOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRouteDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerRunFunctionOperation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerSurface;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTerminalConfig;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.UsageScope;
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
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BoundText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ComparisonCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConcatenatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.DurationTicksText;
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
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DisabledMessageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.MessageCatalog;
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
	public static final int MAX_PLAYER_ROUTES_PER_GAME = 128;
	public static final int MAX_PLAYER_DATA_PER_GAME = 256;
	public static final int MAX_PLAYER_ACTIONS_PER_GAME = 256;
	public static final int MAX_PLAYER_HISTORY_FIELDS = 64;
	public static final int MAX_PLAYER_DATA_NAME_LENGTH = 256;
	public static final int MAX_PLAYER_ACTION_USES = 1_000_000;
	public static final long MAX_PLAYER_ACTION_COOLDOWN_TICKS = 12_096_000L;
	public static final int MAX_EXCLUSIVE_OPTIONS = 128;
	public static final int MAX_REPEATABLE_EVENT_RECORDS = 4_096;
	public static final long MAX_TASK_DURATION_TICKS = 51_840_000L;
	public static final long MAX_INTERMISSION_DURATION_TICKS = 12_096_000L;
	public static final int MAX_TEXT_JSON_LENGTH = 8_192;
	public static final int MAX_JSON_DEPTH = 64;
	public static final int MAX_DIAGNOSTIC_DETAILS = 100;
	private static final int MAX_SHORT_STRING_LENGTH = 1_024;
	private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern MESSAGE_HOOK_ARGUMENT = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
	private static final Pattern MESSAGE_HOOK_DURATION = Pattern.compile(
		"[0-9]+(?:\\.[0-9]+)?(?:ns|us|ms|s|t)"
	);
	private static final Set<MessageHookEvent> GAME_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.START,
		MessageHookEvent.PAUSE,
		MessageHookEvent.RESUME,
		MessageHookEvent.END
	);
	private static final Set<MessageHookEvent> PHASE_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.ENTER,
		MessageHookEvent.EXIT
	);
	private static final Set<MessageHookEvent> TASK_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.START,
		MessageHookEvent.COMPLETE,
		MessageHookEvent.INTERRUPT
	);
	private static final Set<MessageHookEvent> TASK_EVENT_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.TRIGGER
	);
	private static final Set<MessageHookEvent> ROLE_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.INITIALIZATION,
		MessageHookEvent.ROLE_CHANGED
	);
	private static final Set<MessageHookEvent> READINESS_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.COMPLETE
	);
	private static final Set<MessageHookEvent> FLOW_MESSAGE_HOOKS = Set.of(
		MessageHookEvent.START,
		MessageHookEvent.PLAYER_COMPLETE,
		MessageHookEvent.ALL_COMPLETE
	);
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
		PLAYER_ROUTE("player_routes"),
		PLAYER_DATA("player_data"),
		PLAYER_ACTION("player_actions"),
		PAGE("pages"),
		THEME("themes"),
		TEXT_EFFECT("text_effects"),
		MESSAGE_CUE("message_cues"),
		COUNTDOWN("countdowns");

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
		String json,
		Optional<MessageEnvelopeHint> messageEnvelopeHint
	) {
		public Source(
			final DefinitionType type,
			final Identifier id,
			final Identifier resource,
			final String sourcePack,
			final String json
		) {
			this(type, id, resource, sourcePack, json, Optional.empty());
		}

		public Source {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(resource, "resource");
			sourcePack = sourcePack == null ? "unknown" : sourcePack;
			Objects.requireNonNull(json, "json");
			messageEnvelopeHint = messageEnvelopeHint == null
				? Optional.empty()
				: messageEnvelopeHint;
		}
	}

	/**
	 * Strictly verified top-level cue fields retained while an oversized document is streamed.
	 *
	 * <p>The hint is trusted only when the registry consumed one complete strict JSON document,
	 * rejected duplicate keys at every depth, and verified both envelope field types.
	 */
	public record MessageEnvelopeHint(
		boolean required,
		Optional<Identifier> game
	) {
		public MessageEnvelopeHint {
			game = game == null ? Optional.empty() : game;
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
		List<Source> coreSources = new ArrayList<>();
		List<Source> countdownSources = new ArrayList<>();
		for (Source source : sources) {
			if (source.type() == DefinitionType.COUNTDOWN) {
				countdownSources.add(source);
			} else {
				coreSources.add(source);
			}
		}
		Compiler compiler = new Compiler(functions, predicates);
		Compilation core = compiler.compile(coreSources);
		if (core.snapshot().isEmpty()) {
			return core;
		}
		DefinitionSnapshot snapshot = core.snapshot().orElseThrow();
		CountdownCatalogCompiler.Result countdowns = CountdownCatalogCompiler.compile(
			countdownSources,
			snapshot,
			functions
		);
		return new Compilation(
			Optional.of(snapshot.withCountdownCatalog(
				countdowns.catalog(),
				countdowns.sourceDocuments()
			)),
			core.problems(),
			core.totalProblemCount()
		);
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
		private final Map<Identifier, PlayerRouteDefinition> playerRoutes = new LinkedHashMap<>();
		private final Map<Identifier, PlayerDataDefinition> playerData = new LinkedHashMap<>();
		private final Map<Identifier, PlayerActionDefinition> playerActions = new LinkedHashMap<>();
		private final Map<Identifier, PageDefinition> pages = new LinkedHashMap<>();
		private final Map<Identifier, ThemeDefinition> themes = new LinkedHashMap<>();
		private final Map<Identifier, TextEffectDefinition> textEffects = new LinkedHashMap<>();
		private final Map<Identifier, MessageCueDefinition> messageCues = new LinkedHashMap<>();
		private final Map<DocumentKey, DisabledMessageDefinition> disabledMessages = new LinkedHashMap<>();
		private final Set<Identifier> startBlockedGames = new LinkedHashSet<>();
		private final Set<DocumentKey> skippedMessages = new HashSet<>();
		private final Set<DocumentKey> multiGameMessageFailures = new HashSet<>();
		private final Map<DocumentKey, Set<Identifier>> duplicateMessageGames = new HashMap<>();
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
					if (!isMessageDefinition(source.type())) {
						add(
							source,
							"RESOURCE_LIMIT",
							"",
							"definition exceeds " + MAX_DEFINITION_CHARACTERS + " characters"
						);
						return new Compilation(Optional.empty(), this.problems, this.problems.totalCount());
					}
				}
				totalCharacters += Math.min(
					source.json().length(),
					MAX_DEFINITION_CHARACTERS + 1L
				);
				if (totalCharacters > MAX_TOTAL_DEFINITION_CHARACTERS) {
					add(
						source,
						"RESOURCE_LIMIT",
						"",
						"total definition size exceeds " + MAX_TOTAL_DEFINITION_CHARACTERS + " characters"
					);
					return new Compilation(Optional.empty(), this.problems, this.problems.totalCount());
				}
				if (source.json().length() > MAX_DEFINITION_CHARACTERS) {
					DocumentKey key = new DocumentKey(source.type(), source.id());
					MessageIdentity identity = messageIdentity(source);
					List<Problem> diagnostics = new ArrayList<>();
					diagnostics.add(
						messageProblem(
							source,
							"RESOURCE_LIMIT",
							"",
							"definition exceeds " + MAX_DEFINITION_CHARACTERS + " characters"
						)
					);
					if (
						source.type() == DefinitionType.MESSAGE_CUE
							&& !identity.trusted()
					) {
						diagnostics.add(
							messageProblem(
								source,
								"UNTRUSTED_ENVELOPE",
								"",
								"oversized cue envelope cannot be verified; treating it as optional"
							)
						);
					}
					disableMessage(
						source,
						identity,
						diagnostics,
						diagnostics.size()
					);
					this.skippedMessages.add(key);
				}
			}

			for (Source source : sources) {
				DefinitionKey key = new DefinitionKey(source.type(), source.id());
				Source previous = this.origins.putIfAbsent(key, source);
				if (previous != null) {
					if (isMessageDefinition(source.type())) {
						disableDuplicateMessage(previous, source);
						continue;
					}
					add(
						source,
						"DUPLICATE_DEFINITION",
						"",
						"definition already supplied by " + previous.resource()
					);
					continue;
				}
				if (this.skippedMessages.contains(new DocumentKey(source.type(), source.id()))) {
					continue;
				}
				parse(source);
			}

			if (this.problems.isEmpty()) {
				validateReferences();
			}
			if (this.problems.isEmpty()) {
				validateMessageReferences();
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
						this.playerRoutes,
						this.playerData,
						this.playerActions,
						this.pages,
						this.themes,
						new MessageCatalog(
							this.textEffects,
							this.messageCues,
							this.disabledMessages,
							this.startBlockedGames
						),
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
			if (isMessageDefinition(source.type())) {
				parseMessageDefinition(source);
				return;
			}
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
				case PLAYER_ROUTE -> parsePlayerRoute(source, reader);
				case PLAYER_DATA -> parsePlayerData(source, reader);
				case PLAYER_ACTION -> parsePlayerAction(source, reader);
				case PAGE, THEME -> throw new IllegalStateException("UI definitions use the shared UI parser");
				case TEXT_EFFECT, MESSAGE_CUE -> throw new IllegalStateException(
					"message definitions use the shared message parser"
				);
				case COUNTDOWN -> throw new IllegalStateException(
					"countdown definitions use the isolated countdown catalog compiler"
				);
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

		private void parseMessageDefinition(final Source source) {
			MessageIdentity declaredIdentity = messageIdentity(source);
			storeMessageDocument(source, canonicalMessageSource(source));
			if (source.type() == DefinitionType.TEXT_EFFECT) {
				MessageDefinitionParser.ParseResult<TextEffectDefinition> result =
					MessageDefinitionParser.parseTextEffect(source.id(), source.json());
				List<Problem> diagnostics = messageProblems(source, result.issues());
				if (!result.valid()) {
					disableMessage(
						source,
						declaredIdentity,
						diagnostics,
						result.totalIssueCount()
					);
					return;
				}
				TextEffectDefinition effect = result.value().orElse(null);
				if (effect == null) {
					disableMessage(
						source,
						declaredIdentity,
						List.of(
							messageProblem(
								source,
								"INVALID_DEFINITION",
								"",
								"text effect parser returned no definition"
							)
						),
						1
					);
					return;
				}
				this.textEffects.put(source.id(), effect);
				storeMessageDocument(source, effect.canonicalDocument());
				return;
			}

			MessageDefinitionParser.ParseResult<MessageCueDefinition> result =
				MessageDefinitionParser.parseMessageCue(source.id(), source.json());
			List<Problem> diagnostics = new ArrayList<>(
				messageProblems(source, result.issues())
			);
			if (!declaredIdentity.trusted()) {
				diagnostics.add(
					messageProblem(
						source,
						"UNTRUSTED_ENVELOPE",
						"",
						"cue envelope cannot be verified; treating it as optional"
					)
				);
			}
			if (!result.valid()) {
				disableMessage(
					source,
					declaredIdentity,
					diagnostics,
					result.totalIssueCount() + (declaredIdentity.trusted() ? 0 : 1)
				);
				return;
			}
			MessageCueDefinition cue = result.value().orElse(null);
			if (cue == null) {
				disableMessage(
					source,
					declaredIdentity,
					List.of(
						messageProblem(
							source,
							"INVALID_DEFINITION",
							"",
							"message cue parser returned no definition"
						)
					),
					1
				);
				return;
			}
			this.messageCues.put(source.id(), cue);
			storeMessageDocument(source, cue.canonicalDocument());
		}

		private void validateMessageReferences() {
			for (MessageCueDefinition cue : List.copyOf(this.messageCues.values())) {
				Source source = origin(DefinitionType.MESSAGE_CUE, cue.id());
				MessageDiagnostics diagnostics = new MessageDiagnostics(source);
				validateMessageGame(cue, diagnostics);
				validateMessageContext(cue, diagnostics);
				validateMessageAudience(
					cue.policies().audience().audience(),
					"/policies/audience/target",
					cue.game(),
					diagnostics
				);
				if (
					cue.policies().audience().sensitive()
						&& cue.policies().audience().evolution() != AudienceEvolution.LIVE_STRICT
				) {
					diagnostics.add(
						"INSECURE_POLICY",
						"/policies/audience/evolution",
						"sensitive audiences require live_strict evolution"
					);
				}

				Set<String> nodeIds = new LinkedHashSet<>();
				for (CueNode node : cue.nodes()) {
					nodeIds.add(node.id());
				}
				validateMessageParameters(cue, nodeIds, diagnostics);
				validateMessageParameterAllowedNodeClosure(cue, diagnostics);
				validateMessageFields(
					cue,
					cue.fields(),
					"/fields",
					Optional.empty(),
					diagnostics
				);
				validateMessageStructure(cue, diagnostics);
				for (int index = 0; index < cue.nodes().size(); index++) {
					CueNode node = cue.nodes().get(index);
					String nodePointer = "/nodes/" + index;
					node.policy()
						.audience()
						.ifPresent(
							audience -> validateMessageAudience(
								audience,
								nodePointer + "/audience",
								cue.game(),
								diagnostics
							)
						);
					node.header()
						.when()
						.ifPresent(
							condition -> validateMessageCondition(
								cue,
								condition,
								nodePointer + "/when",
								diagnostics
							)
						);
					if (node instanceof TextNode textNode) {
						validateMessageTextContent(
							cue,
							textNode.id(),
							textNode.content(),
							nodePointer + "/content",
							diagnostics
						);
						for (int variantIndex = 0;
							variantIndex < textNode.variants().size();
							variantIndex++) {
							TextVariant variant = textNode.variants().get(variantIndex);
							String variantPointer = nodePointer + "/variants/" + variantIndex;
							validateMessageCondition(
								cue,
								variant.when(),
								variantPointer + "/when",
								diagnostics
							);
							validateMessageTextContent(
								cue,
								textNode.id(),
								variant.content(),
								variantPointer + "/content",
								diagnostics
							);
						}
					} else if (
						node instanceof CallbackNode callbackNode
							&& !this.functions.contains(callbackNode.function())
					) {
						diagnostics.add(
							"MISSING_EXTERNAL_RESOURCE",
							nodePointer + "/function",
							"function " + callbackNode.function() + " does not exist"
						);
					} else if (node instanceof SoundNode soundNode) {
						validateMessageMissingFallback(
							soundNode.missing(),
							soundNode.fallback(),
							nodePointer + "/missing",
							nodePointer + "/fallback",
							diagnostics
						);
					}
				}
				cue.history()
					.ifPresent(history -> validateMessageHistory(cue, history, diagnostics));
				validateMessageAssets(cue, diagnostics);
				if (!diagnostics.isEmpty()) {
					disableMessage(
						source,
						new MessageIdentity(cue.required(), cue.game(), true),
						diagnostics.retained(),
						diagnostics.totalCount(),
						cue.staticFallback()
					);
				}
			}
			validateDeclaredMessageHooks();
			if (this.games.size() == 1) {
				Identifier onlyGame = this.games.keySet().iterator().next();
				boolean hasUnscopedRequiredFailure = this.disabledMessages.entrySet()
					.stream()
					.anyMatch(
						entry -> entry.getValue().required()
							&& entry.getValue().game().isEmpty()
							&& !entry.getValue().requiredFallbackSatisfiesStart()
							&& !this.multiGameMessageFailures.contains(entry.getKey())
					);
				if (hasUnscopedRequiredFailure) {
					this.startBlockedGames.add(onlyGame);
				}
			}
		}

		private void validateDeclaredMessageHooks() {
			for (GameDefinition game : this.games.values()) {
				validateMessageHookSet(
					DefinitionType.GAME,
					game.id(),
					game.id(),
					"/message_hooks",
					game.messageHooks()
				);
				game.readiness().ifPresent(readiness ->
					validateMessageHookSet(
						DefinitionType.GAME,
						game.id(),
						game.id(),
						"/readiness/message_hooks",
						readiness.messageHooks()
					)
				);
			}
			for (RoleDefinition role : this.roles.values()) {
				validateMessageHookSet(
					DefinitionType.ROLE,
					role.id(),
					role.game(),
					"/message_hooks",
					role.messageHooks()
				);
			}
			for (PhaseDefinition phase : this.phases.values()) {
				validateMessageHookSet(
					DefinitionType.PHASE,
					phase.id(),
					phase.game(),
					"/message_hooks",
					phase.messageHooks()
				);
			}
			for (FlowDefinition flow : this.flows.values()) {
				validateMessageHookSet(
					DefinitionType.FLOW,
					flow.id(),
					flow.game(),
					"/message_hooks",
					flow.messageHooks()
				);
			}
			for (TaskDefinition task : this.tasks.values()) {
				validateMessageHookSet(
					DefinitionType.TASK,
					task.id(),
					task.game(),
					"/message_hooks",
					task.messageHooks()
				);
				for (int index = 0; index < task.events().size(); index++) {
					validateMessageHookSet(
						DefinitionType.TASK,
						task.id(),
						task.game(),
						"/events/" + index + "/message_hooks",
						task.events().get(index).messageHooks()
					);
				}
			}
		}

		private void validateMessageHookSet(
			final DefinitionType ownerType,
			final Identifier ownerId,
			final Identifier ownerGame,
			final String pointer,
			final MessageHookSet hooks
		) {
			for (Map.Entry<MessageHookEvent, MessageHookReference> entry : hooks.hooks().entrySet()) {
				MessageHookReference reference = entry.getValue();
				Identifier cueId = reference.cue();
				String hookPointer = pointer
					+ "/"
					+ entry.getKey().name().toLowerCase(Locale.ROOT);
				MessageCueDefinition cue = this.messageCues.get(cueId);
				if (cue == null) {
					DocumentKey key = new DocumentKey(DefinitionType.MESSAGE_CUE, cueId);
					if (this.disabledMessages.containsKey(key)) {
						continue;
					}
					add(
						origin(ownerType, ownerId),
						"MISSING_REFERENCE",
						hookPointer,
						"message cue " + cueId + " does not exist"
					);
					continue;
				}
				if (cue.game().filter(ownerGame::equals).isEmpty()) {
					add(
						origin(ownerType, ownerId),
						"CROSS_GAME_REFERENCE",
						hookPointer,
						cue.game().isEmpty()
							? "message hook cue " + cueId + " is not scoped to game " + ownerGame
							: "message hook cue " + cueId + " belongs to " + cue.game().orElseThrow()
					);
				}
				validateMessageHookArguments(
					ownerType,
					ownerId,
					hookPointer,
					cue,
					reference
				);
			}
		}

		/**
		 * Rejects fixed hook values that can never be consumed by the referenced cue. This keeps a
		 * bad lifecycle hook from surviving compilation only to fail after its owning gameplay
		 * transaction has already committed. Identifier arguments are also checked against this
		 * generation so the freeze compiler can retain the exact referenced definition.
		 */
		private void validateMessageHookArguments(
			final DefinitionType ownerType,
			final Identifier ownerId,
			final String hookPointer,
			final MessageCueDefinition cue,
			final MessageHookReference reference
		) {
			for (Map.Entry<String, String> argument : reference.arguments().entrySet()) {
				String pointer = hookPointer + "/arguments/" + escapePointer(argument.getKey());
				ParameterDefinition parameter = cue.parameters().get(argument.getKey());
				if (parameter == null) {
					add(
						origin(ownerType, ownerId),
						"UNDECLARED_ARGUMENT",
						pointer,
						"message hook argument "
							+ argument.getKey()
							+ " is not declared by cue "
							+ cue.id()
					);
					continue;
				}
				if (!fixedHookArgumentMatches(argument.getValue(), parameter)) {
					add(
						origin(ownerType, ownerId),
						"TYPE_MISMATCH",
						pointer,
						"fixed message hook argument does not match parameter type "
							+ parameter.type().name().toLowerCase(Locale.ROOT)
					);
					continue;
				}
				if (parameter.type() == ParameterType.IDENTIFIER) {
					Identifier identifier = Identifier.tryParse(argument.getValue().strip());
					if (
						identifier != null
							&& !messageIdentifierReferenceExists(
								identifier,
								parameter.identifierKind()
							)
					) {
						add(
							origin(ownerType, ownerId),
							"MISSING_REFERENCE",
							pointer,
							"message hook identifier argument references missing "
								+ parameter.identifierKind().name().toLowerCase(Locale.ROOT)
								+ " "
								+ identifier
						);
					}
				}
			}
		}

		private static boolean fixedHookArgumentMatches(
			final String value,
			final ParameterDefinition parameter
		) {
			try {
				return switch (parameter.type()) {
					case STRING, PLAYER -> true;
					case INTEGER -> {
						Long.parseLong(value.strip());
						yield true;
					}
					case DECIMAL -> {
						new BigDecimal(value.strip());
						yield true;
					}
					case BOOLEAN -> {
						String normalized = value.strip().toLowerCase(Locale.ROOT);
						yield normalized.equals("true") || normalized.equals("false");
					}
					case IDENTIFIER -> Identifier.tryParse(value.strip()) != null;
					case COMPONENT -> {
						JsonElement parsed = JsonParser.parseString(value);
						yield !parsed.isJsonNull();
					}
					case DURATION -> MESSAGE_HOOK_DURATION.matcher(
						value.strip().toLowerCase(Locale.ROOT)
					).matches();
				};
			} catch (RuntimeException invalid) {
				return false;
			}
		}

		private boolean messageIdentifierReferenceExists(
			final Identifier id,
			final IdentifierKind kind
		) {
			return switch (kind) {
				case ANY -> true;
				case GAME -> this.games.containsKey(id);
				case PHASE -> this.phases.containsKey(id);
				case TASK -> this.tasks.containsKey(id);
				case FIELD -> this.fields.containsKey(id);
				case ROLE -> this.roles.containsKey(id);
				case TEAM -> this.teams.containsKey(id);
				case LIFE_STATE -> this.lifeStates.containsKey(id);
				case PLAYER_DATA -> this.playerData.containsKey(id);
				case EVENT -> nestedTaskMemberExists(id, true);
				case STATISTIC -> nestedTaskMemberExists(id, false);
			};
		}

		private boolean nestedTaskMemberExists(final Identifier nestedId, final boolean event) {
			return this.tasks.values().stream().anyMatch(task -> {
				String prefix = task.id().getPath() + "/";
				if (
					!nestedId.getNamespace().equals(task.id().getNamespace())
						|| !nestedId.getPath().startsWith(prefix)
				) {
					return false;
				}
				String localId = nestedId.getPath().substring(prefix.length());
				return event
					? task.events().stream().anyMatch(value -> value.id().equals(localId))
					: task.statistics().stream().anyMatch(value -> value.id().equals(localId));
			});
		}

		private void validateMessageGame(
			final MessageCueDefinition cue,
			final MessageDiagnostics diagnostics
		) {
			if (cue.required() && cue.game().isEmpty()) {
				diagnostics.add(
					"MISSING_REQUIRED",
					"/game",
					"required message cues must declare an owning game"
				);
			}
			cue.game().ifPresent(game -> {
				if (!this.games.containsKey(game)) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/game",
						"game " + game + " does not exist"
					);
				}
			});
		}

		private void validateMessageContext(
			final MessageCueDefinition cue,
			final MessageDiagnostics diagnostics
		) {
			Set<Identifier> contextGames = cue.policies().context().games();
			for (Identifier game : contextGames) {
				if (!this.games.containsKey(game)) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/policies/context/games",
						"game " + game + " does not exist"
					);
				} else if (cue.game().isPresent() && !cue.game().orElseThrow().equals(game)) {
					diagnostics.add(
						"CROSS_GAME_REFERENCE",
						"/policies/context/games",
						"context game " + game + " differs from cue game " + cue.game().orElseThrow()
					);
				}
			}
			for (Identifier phaseId : cue.policies().context().phases()) {
				PhaseDefinition phase = this.phases.get(phaseId);
				if (phase == null) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/policies/context/phases",
						"phase " + phaseId + " does not exist"
					);
					continue;
				}
				validateMessageContextGame(
					cue,
					contextGames,
					phase.game(),
					"/policies/context/phases",
					"phase " + phaseId,
					diagnostics
				);
			}
			for (Identifier taskId : cue.policies().context().tasks()) {
				TaskDefinition task = this.tasks.get(taskId);
				if (task == null) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/policies/context/tasks",
						"task " + taskId + " does not exist"
					);
					continue;
				}
				validateMessageContextGame(
					cue,
					contextGames,
					task.game(),
					"/policies/context/tasks",
					"task " + taskId,
					diagnostics
				);
			}
		}

		private void validateMessageContextGame(
			final MessageCueDefinition cue,
			final Set<Identifier> contextGames,
			final Identifier targetGame,
			final String pointer,
			final String target,
			final MessageDiagnostics diagnostics
		) {
			if (cue.game().isPresent() && !cue.game().orElseThrow().equals(targetGame)) {
				diagnostics.add(
					"CROSS_GAME_REFERENCE",
					pointer,
					target + " belongs to " + targetGame
				);
			} else if (!contextGames.isEmpty() && !contextGames.contains(targetGame)) {
				diagnostics.add(
					"CROSS_GAME_REFERENCE",
					pointer,
					target + " is outside the declared context games " + contextGames
				);
			}
		}

		private void validateMessageAudience(
			final AudienceSpec audience,
			final String pointer,
			final Optional<Identifier> game,
			final MessageDiagnostics diagnostics
		) {
			for (int index = 0; index < audience.selectors().size(); index++) {
				AudienceSelector selector = audience.selectors().get(index);
				String selectorPointer = audience.selectors().size() == 1
					? pointer
					: pointer + "/any_of/" + index;
				for (Identifier role : selector.roles()) {
					validateMessageOwnedReference(
						role,
						this.roles,
						RoleDefinition::game,
						game,
						selectorPointer + "/roles",
						"role",
						diagnostics
					);
				}
				for (Identifier team : selector.teams()) {
					validateMessageOwnedReference(
						team,
						this.teams,
						TeamDefinition::game,
						game,
						selectorPointer + "/teams",
						"team",
						diagnostics
					);
				}
				for (Identifier lifeState : selector.lifeStates()) {
					validateMessageOwnedReference(
						lifeState,
						this.lifeStates,
						LifeStateDefinition::game,
						game,
						selectorPointer + "/life_states",
						"life state",
						diagnostics
					);
				}
				for (Identifier tag : selector.roleTags()) {
					validateMessageTag(
						tag,
						this.roles,
						RoleDefinition::game,
						RoleDefinition::tags,
						game,
						selectorPointer + "/role_tags",
						"role",
						diagnostics
					);
				}
				for (Identifier tag : selector.teamTags()) {
					validateMessageTag(
						tag,
						this.teams,
						TeamDefinition::game,
						TeamDefinition::tags,
						game,
						selectorPointer + "/team_tags",
						"team",
						diagnostics
					);
				}
				for (Identifier tag : selector.lifeStateTags()) {
					validateMessageTag(
						tag,
						this.lifeStates,
						LifeStateDefinition::game,
						LifeStateDefinition::tags,
						game,
						selectorPointer + "/life_state_tags",
						"life-state",
						diagnostics
					);
				}
			}
		}

		private <T> void validateMessageOwnedReference(
			final Identifier targetId,
			final Map<Identifier, T> targets,
			final java.util.function.Function<T, Identifier> gameGetter,
			final Optional<Identifier> expectedGame,
			final String pointer,
			final String targetType,
			final MessageDiagnostics diagnostics
		) {
			T target = targets.get(targetId);
			if (target == null) {
				diagnostics.add(
					"MISSING_REFERENCE",
					pointer,
					targetType + " " + targetId + " does not exist"
				);
			} else if (
				expectedGame.isPresent()
					&& !expectedGame.orElseThrow().equals(gameGetter.apply(target))
			) {
				diagnostics.add(
					"CROSS_GAME_REFERENCE",
					pointer,
					targetType + " " + targetId + " belongs to " + gameGetter.apply(target)
				);
			}
		}

		private <T> void validateMessageTag(
			final Identifier tag,
			final Map<Identifier, T> targets,
			final java.util.function.Function<T, Identifier> gameGetter,
			final java.util.function.Function<T, Set<Identifier>> tagGetter,
			final Optional<Identifier> expectedGame,
			final String pointer,
			final String targetType,
			final MessageDiagnostics diagnostics
		) {
			List<T> matches = targets.values()
				.stream()
				.filter(target -> tagGetter.apply(target).contains(tag))
				.toList();
			if (matches.isEmpty()) {
				diagnostics.add(
					"MISSING_REFERENCE",
					pointer,
					targetType + " tag " + tag + " is not declared"
				);
			} else if (
				expectedGame.isPresent()
					&& matches.stream().noneMatch(
						target -> expectedGame.orElseThrow().equals(gameGetter.apply(target))
					)
			) {
				diagnostics.add(
					"CROSS_GAME_REFERENCE",
					pointer,
					targetType + " tag " + tag + " is not declared by " + expectedGame.orElseThrow()
				);
			}
		}

		private void validateMessageParameters(
			final MessageCueDefinition cue,
			final Set<String> nodeIds,
			final MessageDiagnostics diagnostics
		) {
			for (Map.Entry<String, ParameterDefinition> entry : cue.parameters().entrySet()) {
				String name = entry.getKey();
				ParameterDefinition parameter = entry.getValue();
				String pointer = "/parameters/" + escapePointer(name);
				parameter.audience()
					.ifPresent(
						audience -> validateMessageAudience(
							audience,
							pointer + "/audience",
							cue.game(),
							diagnostics
						)
					);
				for (String node : parameter.allowedNodes()) {
					if (!nodeIds.contains(node)) {
						diagnostics.add(
							"MISSING_REFERENCE",
							pointer + "/allowed_nodes",
							"allowed node " + node + " is not declared"
						);
					}
				}
				if (
					parameter.sensitive()
						&& parameter.audience().isEmpty()
				) {
					diagnostics.add(
						"INSECURE_POLICY",
						pointer + "/audience",
						"sensitive parameters must declare their authorized audience"
					);
				}
				if (
					parameter.sensitive()
						&& cue.policies().audience().evolution() != AudienceEvolution.LIVE_STRICT
				) {
					diagnostics.add(
						"INSECURE_POLICY",
						pointer + "/sensitive",
						"sensitive parameters require live_strict audience evolution"
					);
				}
				for (int sourceIndex = 0; sourceIndex < parameter.sources().size(); sourceIndex++) {
					if (
						parameter.sources().get(sourceIndex)
							instanceof PlayerDataParameterSource playerDataSource
					) {
						validateMessagePlayerData(
							playerDataSource.field(),
							cue.game(),
							pointer + "/sources/" + sourceIndex + "/field",
							diagnostics
						);
					}
				}
				if (
					parameter.type() == ParameterType.IDENTIFIER
						&& parameter.identifierKind() != IdentifierKind.ANY
				) {
					parameter.canonicalDefault()
						.ifPresent(
							value -> validateMessageIdentifierLiteral(
								value,
								parameter.identifierKind(),
								cue.game(),
								pointer + "/default",
								diagnostics
							)
						);
					parameter.onError()
						.canonicalFallback()
						.ifPresent(
							value -> validateMessageIdentifierLiteral(
								value,
								parameter.identifierKind(),
								cue.game(),
								pointer + "/on_error/fallback",
								diagnostics
							)
						);
				}
			}
			for (String parameter : cue.policies().concurrency().dedupeParameters()) {
				if (!cue.parameters().containsKey(parameter)) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/policies/concurrency/dedupe_parameters",
						"dedupe parameter " + parameter + " is not declared"
					);
				}
			}
			cue.policies().concurrency().refreshKey().ifPresent(parameter -> {
				if (!cue.parameters().containsKey(parameter)) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/policies/concurrency/refresh_key",
						"refresh key parameter " + parameter + " is not declared"
					);
				}
			});
		}

		private void validateMessageIdentifierLiteral(
			final String canonicalValue,
			final IdentifierKind kind,
			final Optional<Identifier> game,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			Identifier value;
			try {
				JsonElement element = JsonParser.parseString(canonicalValue);
				value = element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
					? parseIdentifier(element.getAsString())
					: null;
			} catch (JsonParseException error) {
				value = null;
			}
			if (value == null) {
				diagnostics.add(
					"TYPE_MISMATCH",
					pointer,
					"identifier default or fallback must be a namespaced identifier"
				);
				return;
			}
			switch (kind) {
				case ANY -> {
				}
				case GAME -> validateMessagePlainReference(
					value,
					this.games,
					pointer,
					"game",
					diagnostics
				);
				case PHASE -> validateMessageOwnedReference(
					value,
					this.phases,
					PhaseDefinition::game,
					game,
					pointer,
					"phase",
					diagnostics
				);
				case TASK -> validateMessageOwnedReference(
					value,
					this.tasks,
					TaskDefinition::game,
					game,
					pointer,
					"task",
					diagnostics
				);
				case FIELD -> validateMessageOwnedReference(
					value,
					this.fields,
					FieldDefinition::game,
					game,
					pointer,
					"field",
					diagnostics
				);
				case ROLE -> validateMessageOwnedReference(
					value,
					this.roles,
					RoleDefinition::game,
					game,
					pointer,
					"role",
					diagnostics
				);
				case TEAM -> validateMessageOwnedReference(
					value,
					this.teams,
					TeamDefinition::game,
					game,
					pointer,
					"team",
					diagnostics
				);
				case LIFE_STATE -> validateMessageOwnedReference(
					value,
					this.lifeStates,
					LifeStateDefinition::game,
					game,
					pointer,
					"life state",
					diagnostics
				);
				case PLAYER_DATA -> validateMessagePlayerData(
					value,
					game,
					pointer,
					diagnostics
				);
				case EVENT -> validateMessageNestedTaskIdentifier(
					value,
					game,
					true,
					pointer,
					diagnostics
				);
				case STATISTIC -> validateMessageNestedTaskIdentifier(
					value,
					game,
					false,
					pointer,
					diagnostics
				);
			}
		}

		private void validateMessageParameterAllowedNodeClosure(
			final MessageCueDefinition cue,
			final MessageDiagnostics diagnostics
		) {
			MessageParameterUseGraph.UseGraph uses = MessageParameterUseGraph.compile(cue);
			for (Map.Entry<String, ParameterDefinition> entry : cue.parameters().entrySet()) {
				String parameterId = entry.getKey();
				Set<String> allowedNodes = entry.getValue().allowedNodes();
				if (allowedNodes.isEmpty()) {
					continue;
				}
				String pointer = "/parameters/"
					+ escapePointer(parameterId)
					+ "/allowed_nodes";
				for (CueNode node : cue.nodes()) {
					if (
						uses.parametersForNode(node.id()).contains(parameterId)
							&& !allowedNodes.contains(node.id())
					) {
						diagnostics.add(
							"UNAUTHORIZED_REFERENCE",
							pointer,
							"parameter "
								+ parameterId
								+ " is used by node "
								+ node.id()
								+ " but that node is not listed in allowed_nodes"
						);
					}
				}
				if (uses.historyParameters().contains(parameterId)) {
					/* History has no node ID and cannot opt into a non-empty node allowlist. */
					diagnostics.add(
						"UNAUTHORIZED_REFERENCE",
						pointer,
						"parameter "
							+ parameterId
							+ " restricts allowed_nodes and cannot be referenced by history"
					);
				}
			}
		}

		private void validateMessagePlainReference(
			final Identifier target,
			final Map<Identifier, ?> targets,
			final String pointer,
			final String targetType,
			final MessageDiagnostics diagnostics
		) {
			if (!targets.containsKey(target)) {
				diagnostics.add(
					"MISSING_REFERENCE",
					pointer,
					targetType + " " + target + " does not exist"
				);
			}
		}

		private void validateMessageNestedTaskIdentifier(
			final Identifier target,
			final Optional<Identifier> game,
			final boolean event,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			List<TaskDefinition> matches = this.tasks.values()
				.stream()
				.filter(task -> game.isEmpty() || game.orElseThrow().equals(task.game()))
				.filter(task -> event
					? task.events().stream().anyMatch(
						value -> nestedTaskIdentifier(task.id(), value.id()).equals(target)
					)
					: task.statistics().stream().anyMatch(
						value -> nestedTaskIdentifier(task.id(), value.id()).equals(target)
					))
				.toList();
			if (matches.isEmpty()) {
				diagnostics.add(
					"MISSING_REFERENCE",
					pointer,
					(event ? "event " : "statistic ")
						+ target
						+ " does not exist; expected <task-id>/"
						+ (event ? "<event-id>" : "<statistic-id>")
				);
			}
		}

		private static Identifier nestedTaskIdentifier(
			final Identifier task,
			final String localId
		) {
			return Identifier.fromNamespaceAndPath(
				task.getNamespace(),
				task.getPath() + "/" + localId
			);
		}

		private void validateMessageFields(
			final MessageCueDefinition cue,
			final Map<String, DynamicFieldDefinition> fields,
			final String pointer,
			final Optional<String> nodeId,
			final MessageDiagnostics diagnostics
		) {
			for (Map.Entry<String, DynamicFieldDefinition> entry : fields.entrySet()) {
				String fieldPointer = pointer + "/" + escapePointer(entry.getKey()) + "/source";
				DynamicFieldDefinition field = entry.getValue();
				if (field.source() instanceof ArgumentSource argument) {
					ParameterDefinition parameter = cue.parameters().get(argument.parameter());
					if (parameter == null) {
						diagnostics.add(
							"MISSING_REFERENCE",
							fieldPointer + "/argument",
							"parameter " + argument.parameter() + " is not declared"
						);
					}
				} else if (field.source() instanceof BindingSource binding) {
					validateMessageBinding(
						binding.path(),
						cue.game(),
						fieldPointer + "/binding",
						diagnostics
					);
				}
			}
		}

		private void validateMessagePlayerData(
			final Identifier id,
			final Optional<Identifier> game,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			PlayerDataDefinition data = this.playerData.get(id);
			if (data == null) {
				diagnostics.add(
					"MISSING_REFERENCE",
					pointer,
					"player data " + id + " does not exist"
				);
				return;
			}
			if (game.isPresent() && !game.orElseThrow().equals(data.game())) {
				diagnostics.add(
					"CROSS_GAME_REFERENCE",
					pointer,
					"player data " + id + " belongs to " + data.game()
				);
			}
			if (!data.surfaces().contains(PlayerSurface.DYNAMIC_MESSAGE)) {
				diagnostics.add(
					"UNAUTHORIZED_REFERENCE",
					pointer,
					"player data " + id + " is not authorized for dynamic_message"
				);
			}
		}

		private void validateMessageBinding(
			final String path,
			final Optional<Identifier> game,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			if (path.startsWith("personal/")) {
				String rawId = path.substring("personal/".length());
				Identifier id = parseIdentifier(rawId);
				if (id == null) {
					diagnostics.add(
						"INVALID_IDENTIFIER",
						pointer,
						"personal binding must contain a namespaced player-data ID"
					);
				} else {
					validateMessagePlayerData(id, game, pointer, diagnostics);
				}
				return;
			}
			if (!path.startsWith("personal_meta/")) {
				/*
				 * Other paths are resolved by the active game's existing BindingRuntime and
				 * BindingContext. Do not freeze a second, message-only binding registry here.
				 */
				return;
			}
			String suffix = path.substring("personal_meta/".length());
			int propertySeparator = suffix.lastIndexOf('/');
			String property = propertySeparator <= 0
				? ""
				: suffix.substring(propertySeparator + 1);
			if (
				propertySeparator <= 0
					|| (!property.equals("name") && !property.equals("value_name"))
			) {
				diagnostics.add(
					"UNKNOWN_BINDING",
					pointer,
					"personal metadata exposes only /name and /value_name"
				);
				return;
			}
			Identifier id = parseIdentifier(suffix.substring(0, propertySeparator));
			if (id == null) {
				diagnostics.add(
					"INVALID_IDENTIFIER",
					pointer,
					"personal metadata binding must contain a namespaced player-data ID"
				);
				return;
			}
			PlayerDataDefinition data = this.playerData.get(id);
			validateMessagePlayerData(id, game, pointer, diagnostics);
			if (data == null) {
				return;
			}
			if (property.equals("name") && data.name().isEmpty()) {
				diagnostics.add(
					"MISSING_REFERENCE",
					pointer,
					"player data " + id + " does not declare a name"
				);
			}
			if (property.equals("value_name")) {
				Identifier fieldId = data.source().id().orElse(null);
				FieldDefinition field = fieldId == null ? null : this.fields.get(fieldId);
				if (
					data.source().type() != PlayerDataSourceType.EXCLUSIVE_CHOICE
						|| field == null
						|| field.type() != FieldType.EXCLUSIVE_CHOICE
				) {
					diagnostics.add(
						"TYPE_MISMATCH",
						pointer,
						"player data " + id + " must expose an exclusive-choice value"
					);
				}
			}
		}

		private void validateMessageTextContent(
			final MessageCueDefinition cue,
			final String nodeId,
			final MessageDefinitions.TextContent content,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			validateMessageFields(
				cue,
				content.fields(),
				pointer + "/fields",
				Optional.of(nodeId),
				diagnostics
			);
			validateMessageLocalizedTemplate(
				cue,
				content.text(),
				content.fields(),
				Optional.of(nodeId),
				pointer + "/text",
				diagnostics
			);
			content.effect().ifPresent(
				effect -> validateMessageEffect(
					effect,
					pointer + "/effect",
					diagnostics
				)
			);
			validateMessageSpeaker(cue, nodeId, content, pointer + "/speaker", diagnostics);
		}

		private void validateMessageLocalizedTemplate(
			final MessageCueDefinition cue,
			final LocalizedTemplate localized,
			final Map<String, DynamicFieldDefinition> localFields,
			final Optional<String> nodeId,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			validateMessageTemplate(
				cue,
				localized.fallback().parts(),
				localFields,
				nodeId,
				pointer + "/fallback",
				diagnostics
			);
			localized.locales().forEach(
				(locale, template) -> validateMessageTemplate(
					cue,
					template.parts(),
					localFields,
					nodeId,
					pointer + "/locales/" + escapePointer(locale),
					diagnostics
				)
			);
		}

		private void validateMessageTemplate(
			final MessageCueDefinition cue,
			final List<ComponentPart> parts,
			final Map<String, DynamicFieldDefinition> localFields,
			final Optional<String> nodeId,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			for (int index = 0; index < parts.size(); index++) {
				ComponentPart part = parts.get(index);
				if (!(part instanceof FieldPart fieldPart)) {
					continue;
				}
				FieldReference reference = fieldPart.field();
				Map<String, DynamicFieldDefinition> scope =
					reference.scope() == MessageDefinitions.FieldScope.CUE
						? cue.fields()
						: localFields;
				DynamicFieldDefinition field = scope.get(reference.id());
				String fieldPointer = pointer + "/parts/" + index + "/field/id";
				if (field == null) {
					diagnostics.add(
						"MISSING_REFERENCE",
						fieldPointer,
						"field " + reference.id() + " is not declared in " + reference.scope()
					);
					continue;
				}
			}
		}

		private void validateMessageEffect(
			final EffectUse effect,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			TextEffectDefinition preset = effect.preset()
				.map(this.textEffects::get)
				.orElse(null);
			effect.preset().ifPresent(id -> {
				if (preset == null) {
					diagnostics.add(
						"MISSING_REFERENCE",
						pointer + "/preset",
						"text effect " + id + " does not exist"
					);
				}
			});
			CharacterSoundPatch patch = effect.overrides().characterSound().orElse(null);
			if (patch == null) {
				return;
			}
			CharacterSoundSpec base = preset == null
				? null
				: preset.characterSound().orElse(null);
			if (patch.sound().isEmpty() && base == null) {
				diagnostics.add(
					"MISSING_REQUIRED",
					pointer + "/overrides/character_sound/sound",
					"character_sound requires a sound when no preset supplies one"
				);
			}
			MissingAssetMode missing = patch.missing()
				.orElse(base == null ? MissingAssetMode.SILENT : base.missing());
			Optional<Identifier> fallback = patch.fallback().isPresent()
				? patch.fallback()
				: base == null ? Optional.empty() : base.fallback();
			if (missing == MissingAssetMode.FALLBACK && fallback.isEmpty()) {
				diagnostics.add(
					"MISSING_REQUIRED",
					pointer + "/overrides/character_sound/fallback",
					"fallback mode requires a fallback sound"
				);
			} else if (
				missing != MissingAssetMode.FALLBACK
					&& patch.fallback().isPresent()
			) {
				diagnostics.add(
					"CONTRADICTORY_VALUE",
					pointer + "/overrides/character_sound/missing",
					"an explicit fallback sound requires fallback missing mode"
				);
			}
		}

		private void validateMessageSpeaker(
			final MessageCueDefinition cue,
			final String nodeId,
			final MessageDefinitions.TextContent content,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			switch (content.speaker().kind()) {
				case PLAYER_PARAMETER -> {
					String parameterId = content.speaker().parameter().orElse(null);
					ParameterDefinition parameter = parameterId == null
						? null
						: cue.parameters().get(parameterId);
					if (parameter == null) {
						diagnostics.add(
							"MISSING_REFERENCE",
							pointer + "/parameter",
							"speaker player parameter is not declared"
						);
					} else {
						if (parameter.type() != ParameterType.PLAYER) {
							diagnostics.add(
								"TYPE_MISMATCH",
								pointer + "/parameter",
								"speaker parameter must have player type"
							);
						}
					}
				}
				case REGISTERED_ENTITY -> {
					if (content.speaker().entity().isEmpty()) {
						diagnostics.add(
							"MISSING_REQUIRED",
							pointer + "/entity",
							"registered_entity speaker requires an entity ID"
						);
					}
				}
				case SYSTEM, NARRATOR -> {
					if (content.speaker().parameter().isPresent()) {
						diagnostics.add(
							"CONTRADICTORY_VALUE",
							pointer + "/parameter",
							"parameter is only valid for player_parameter speakers"
						);
					}
					if (content.speaker().entity().isPresent()) {
						diagnostics.add(
							"CONTRADICTORY_VALUE",
							pointer + "/entity",
							"entity is only valid for registered_entity speakers"
						);
					}
				}
			}
		}

		private void validateMessageCondition(
			final MessageCueDefinition cue,
			final ConditionSpec condition,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			condition.predicate().ifPresent(predicate -> {
				if (!this.predicates.contains(predicate)) {
					diagnostics.add(
						"MISSING_EXTERNAL_RESOURCE",
						pointer + "/predicate",
						"predicate " + predicate + " does not exist"
					);
				}
			});
			condition.expression()
				.ifPresent(
					expression -> validateMessageConditionExpression(
						cue,
						expression,
						pointer + "/expression",
						diagnostics
					)
				);
		}

		private void validateMessageConditionExpression(
			final MessageCueDefinition cue,
			final ConditionExpression condition,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			if (condition instanceof ComparisonCondition comparison) {
				validateMessageConditionValue(cue, comparison.left(), pointer + "/left", diagnostics);
				validateMessageConditionValue(cue, comparison.right(), pointer + "/right", diagnostics);
			} else if (condition instanceof JunctionCondition junction) {
				for (int index = 0; index < junction.conditions().size(); index++) {
					validateMessageConditionExpression(
						cue,
						junction.conditions().get(index),
						pointer + "/" + index,
						diagnostics
					);
				}
			} else if (condition instanceof NotCondition not) {
				validateMessageConditionExpression(
					cue,
					not.condition(),
					pointer + "/not",
					diagnostics
				);
			} else if (condition instanceof ExistsCondition exists) {
				validateMessageConditionValue(
					cue,
					exists.binding(),
					pointer + "/exists",
					diagnostics
				);
			}
		}

		private void validateMessageConditionValue(
			final MessageCueDefinition cue,
			final ValueExpression value,
			final String pointer,
			final MessageDiagnostics diagnostics
		) {
			if (value instanceof BindingValue binding) {
				validateMessageBinding(binding.path(), cue.game(), pointer, diagnostics);
			}
		}

		private void validateMessageStructure(
			final MessageCueDefinition cue,
			final MessageDiagnostics diagnostics
		) {
			if (cue.nodes().size() > MessageDefinitionParser.MAX_NODES) {
				diagnostics.add(
					"RESOURCE_LIMIT",
					"/nodes",
					"node count exceeds " + MessageDefinitionParser.MAX_NODES
				);
			}
			Map<String, Integer> nodeIndexes = new LinkedHashMap<>();
			Map<String, String> dependencies = new LinkedHashMap<>();
			long expandedNodes = 0L;
			int totalVariants = 0;
			int primaryCharacterTracks = 0;
			long soundNodes = 0L;
			long callbackNodes = 0L;
			for (int index = 0; index < cue.nodes().size(); index++) {
				CueNode node = cue.nodes().get(index);
				Integer previous = nodeIndexes.putIfAbsent(node.id(), index);
				if (previous != null) {
					diagnostics.add(
						"DUPLICATE_VALUE",
						"/nodes/" + index + "/id",
						"node ID " + node.id() + " was already declared at index " + previous
					);
				}
				int repeat = node.header().repeat().count();
				if (repeat < 1 || repeat > MessageDefinitionParser.MAX_REPEAT_COUNT) {
					diagnostics.add(
						"OUT_OF_RANGE",
						"/nodes/" + index + "/repeat/count",
						"repeat count must be between 1 and "
							+ MessageDefinitionParser.MAX_REPEAT_COUNT
					);
				}
				expandedNodes += Math.max(0, repeat);
				if (node.header().schedule() instanceof AfterNode after) {
					dependencies.put(node.id(), after.nodeId());
				}
				if (node instanceof TextNode text) {
					totalVariants += text.variants().size();
					if (text.variants().size() > MessageDefinitionParser.MAX_VARIANTS_PER_NODE) {
						diagnostics.add(
							"RESOURCE_LIMIT",
							"/nodes/" + index + "/variants",
							"text variants exceed "
								+ MessageDefinitionParser.MAX_VARIANTS_PER_NODE
						);
					}
					boolean primary = text.content().characterSoundRole()
						== CharacterSoundRole.PRIMARY;
					primary |= text.variants()
						.stream()
						.anyMatch(
							variant -> variant.content().characterSoundRole()
								== CharacterSoundRole.PRIMARY
						);
					primaryCharacterTracks += primary ? 1 : 0;
				} else if (node instanceof SoundNode) {
					soundNodes++;
				} else if (node instanceof CallbackNode) {
					callbackNodes++;
				}
			}
			if (expandedNodes > MessageDefinitionParser.MAX_EXPANDED_NODES) {
				diagnostics.add(
					"RESOURCE_LIMIT",
					"/nodes",
					"repeat expansion exceeds "
						+ MessageDefinitionParser.MAX_EXPANDED_NODES
						+ " nodes"
				);
			}
			if (totalVariants > MessageDefinitionParser.MAX_TOTAL_VARIANTS) {
				diagnostics.add(
					"RESOURCE_LIMIT",
					"/nodes",
					"total text variants exceed "
						+ MessageDefinitionParser.MAX_TOTAL_VARIANTS
				);
			}
			if (primaryCharacterTracks > 1) {
				diagnostics.add(
					"MULTIPLE_PRIMARY_TRACKS",
					"/nodes",
					"message cue may declare only one primary character-sound track"
				);
			}
			for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
				if (!nodeIndexes.containsKey(dependency.getValue())) {
					int index = nodeIndexes.getOrDefault(dependency.getKey(), 0);
					diagnostics.add(
						"MISSING_REFERENCE",
						"/nodes/" + index + "/schedule/node",
						"scheduled node " + dependency.getValue() + " is not declared"
					);
				}
			}
			validateMessageScheduleCycles(dependencies, nodeIndexes, diagnostics);
			final long retainedSoundNodes = soundNodes;
			final long retainedCallbackNodes = callbackNodes;
			final int retainedVariantCount = totalVariants;
			cue.softLimits().maxNodes().ifPresent(maximum -> {
				if (cue.nodes().size() > maximum) {
					diagnostics.add(
						"SOFT_LIMIT_EXCEEDED",
						"/soft_limits/max_nodes",
						"node count " + cue.nodes().size() + " exceeds " + maximum
					);
				}
			});
			cue.softLimits().maxSoundNodes().ifPresent(maximum -> {
				if (retainedSoundNodes > maximum) {
					diagnostics.add(
						"SOFT_LIMIT_EXCEEDED",
						"/soft_limits/max_sound_nodes",
						"sound node count " + retainedSoundNodes + " exceeds " + maximum
					);
				}
			});
			cue.softLimits().maxCallbackNodes().ifPresent(maximum -> {
				if (retainedCallbackNodes > maximum) {
					diagnostics.add(
						"SOFT_LIMIT_EXCEEDED",
						"/soft_limits/max_callback_nodes",
						"callback node count " + retainedCallbackNodes + " exceeds " + maximum
					);
				}
			});
			cue.softLimits().maxVariants().ifPresent(maximum -> {
				if (retainedVariantCount > maximum) {
					diagnostics.add(
						"SOFT_LIMIT_EXCEEDED",
						"/soft_limits/max_variants",
						"variant count " + retainedVariantCount + " exceeds " + maximum
					);
				}
			});
			validateMessageTextSoftLimits(cue, diagnostics);
		}

		private void validateMessageTextSoftLimits(
			final MessageCueDefinition cue,
			final MessageDiagnostics diagnostics
		) {
			if (
				cue.softLimits().maxVisibleGraphemes().isEmpty()
					&& cue.softLimits().maxLines().isEmpty()
			) {
				return;
			}
			long visibleGraphemes = 0L;
			long visibleLines = 0L;
			boolean unboundedGraphemes = false;
			boolean unboundedLines = false;
			for (CueNode candidate : cue.nodes()) {
				if (!(candidate instanceof TextNode node)) {
					continue;
				}
				TextBudget nodeBudget = textBudget(cue, node.content());
				for (TextVariant variant : node.variants()) {
					nodeBudget = nodeBudget.maximum(textBudget(cue, variant.content()));
				}
				long repeats = node.header().repeat().count();
				visibleGraphemes = saturatedAdd(
					visibleGraphemes,
					saturatedMultiply(nodeBudget.graphemes(), repeats)
				);
				visibleLines = saturatedAdd(
					visibleLines,
					saturatedMultiply(nodeBudget.lines(), repeats)
				);
				unboundedGraphemes |= nodeBudget.unboundedGraphemes();
				unboundedLines |= nodeBudget.unboundedLines();
			}
			final long retainedGraphemes = visibleGraphemes;
			final long retainedLines = visibleLines;
			final boolean retainedUnboundedGraphemes = unboundedGraphemes;
			final boolean retainedUnboundedLines = unboundedLines;
			cue.softLimits().maxVisibleGraphemes().ifPresent(maximum -> {
				if (retainedUnboundedGraphemes) {
					diagnostics.add(
						"UNBOUNDED_SOFT_LIMIT",
						"/soft_limits/max_visible_graphemes",
						"visible grapheme budget contains a field without reservation.max_graphemes"
					);
				} else if (retainedGraphemes > maximum) {
					diagnostics.add(
						"SOFT_LIMIT_EXCEEDED",
						"/soft_limits/max_visible_graphemes",
						"worst-case visible grapheme count "
							+ retainedGraphemes
							+ " exceeds "
							+ maximum
					);
				}
			});
			cue.softLimits().maxLines().ifPresent(maximum -> {
				if (retainedUnboundedLines) {
					diagnostics.add(
						"UNBOUNDED_SOFT_LIMIT",
						"/soft_limits/max_lines",
						"visible line budget contains a field without reservation.max_lines "
							+ "or a content layout max_lines"
					);
				} else if (retainedLines > maximum) {
					diagnostics.add(
						"SOFT_LIMIT_EXCEEDED",
						"/soft_limits/max_lines",
						"worst-case visible line count "
							+ retainedLines
							+ " exceeds "
							+ maximum
					);
				}
			});
		}

		private TextBudget textBudget(
			final MessageCueDefinition cue,
			final MessageDefinitions.TextContent content
		) {
			TextBudget result = templateBudget(cue, content, content.text().fallback());
			for (var localized : content.text().locales().values()) {
				result = result.maximum(templateBudget(cue, content, localized));
			}
			if (content.layout().maxLines().isPresent()) {
				result = new TextBudget(
					result.graphemes(),
					Math.min(
						result.lines(),
						content.layout().maxLines().orElseThrow().longValue()
					),
					result.unboundedGraphemes(),
					false
				);
			}
			return result;
		}

		private TextBudget templateBudget(
			final MessageCueDefinition cue,
			final MessageDefinitions.TextContent content,
			final MessageDefinitions.ComponentTemplate template
		) {
			long graphemes = 0L;
			long lines = 0L;
			boolean hasContent = false;
			boolean unboundedGraphemes = false;
			boolean unboundedLines = false;
			for (ComponentPart part : template.parts()) {
				if (part instanceof StaticComponentPart fixed) {
					String plainText = fixed.component().plainText();
					if (!plainText.isEmpty()) {
						hasContent = true;
						graphemes = saturatedAdd(
							graphemes,
							GraphemeClusters.count(plainText)
						);
						lines = saturatedAdd(lines, newlineCount(plainText));
					}
					continue;
				}
				FieldPart reference = (FieldPart)part;
				DynamicFieldDefinition field =
					reference.field().scope() == MessageDefinitions.FieldScope.CUE
						? cue.fields().get(reference.field().id())
						: content.fields().get(reference.field().id());
				if (field == null) {
					continue;
				}
				hasContent = true;
				Optional<Integer> maxGraphemes = field.reservation()
					.flatMap(MessageDefinitions.FieldReservation::maxGraphemes);
				Optional<Integer> maxLines = field.reservation()
					.flatMap(MessageDefinitions.FieldReservation::maxLines);
				graphemes = saturatedAdd(
					graphemes,
					maxGraphemes.map(Integer::longValue)
						.orElseGet(() ->
							field.fallback()
								.map(value ->
									(long)GraphemeClusters.count(value.plainText())
								)
								.orElse(0L)
						)
				);
				lines = saturatedAdd(
					lines,
					Math.max(
						0L,
						maxLines.map(Integer::longValue)
							.orElseGet(() ->
								field.fallback()
									.map(value -> newlineCount(value.plainText()) + 1L)
									.orElse(1L)
							)
							- 1L
					)
				);
				unboundedGraphemes |= maxGraphemes.isEmpty();
				unboundedLines |= maxLines.isEmpty();
			}
			return new TextBudget(
				graphemes,
				hasContent ? saturatedAdd(lines, 1L) : 0L,
				unboundedGraphemes,
				unboundedLines
			);
		}

		private static long newlineCount(final String value) {
			return value.chars().filter(character -> character == '\n').count();
		}

		private static long saturatedAdd(final long left, final long right) {
			if (left >= Long.MAX_VALUE - right) {
				return Long.MAX_VALUE;
			}
			return left + right;
		}

		private static long saturatedMultiply(final long left, final long right) {
			if (left == 0L || right == 0L) {
				return 0L;
			}
			return left > Long.MAX_VALUE / right
				? Long.MAX_VALUE
				: left * right;
		}

		private record TextBudget(
			long graphemes,
			long lines,
			boolean unboundedGraphemes,
			boolean unboundedLines
		) {
			private TextBudget maximum(final TextBudget other) {
				return new TextBudget(
					Math.max(this.graphemes, other.graphemes),
					Math.max(this.lines, other.lines),
					this.unboundedGraphemes || other.unboundedGraphemes,
					this.unboundedLines || other.unboundedLines
				);
			}
		}

		private void validateMessageScheduleCycles(
			final Map<String, String> dependencies,
			final Map<String, Integer> nodeIndexes,
			final MessageDiagnostics diagnostics
		) {
			Set<String> complete = new HashSet<>();
			Set<String> visiting = new LinkedHashSet<>();
			for (String node : dependencies.keySet()) {
				validateMessageScheduleNode(
					node,
					dependencies,
					nodeIndexes,
					visiting,
					complete,
					diagnostics
				);
			}
		}

		private void validateMessageScheduleNode(
			final String node,
			final Map<String, String> dependencies,
			final Map<String, Integer> nodeIndexes,
			final Set<String> visiting,
			final Set<String> complete,
			final MessageDiagnostics diagnostics
		) {
			if (complete.contains(node)) {
				return;
			}
			if (!visiting.add(node)) {
				diagnostics.add(
					"CYCLIC_REFERENCE",
					"/nodes/" + nodeIndexes.getOrDefault(node, 0) + "/schedule/node",
					"schedule dependency cycle includes " + node
				);
				return;
			}
			String dependency = dependencies.get(node);
			if (dependency != null && dependencies.containsKey(dependency)) {
				validateMessageScheduleNode(
					dependency,
					dependencies,
					nodeIndexes,
					visiting,
					complete,
					diagnostics
				);
			}
			visiting.remove(node);
			complete.add(node);
		}

		private void validateMessageHistory(
			final MessageCueDefinition cue,
			final HistorySpec history,
			final MessageDiagnostics diagnostics
		) {
			if (
				history.audience().selectors().stream()
					.anyMatch(selector -> selector.source() == AudienceSource.CURRENT_HOST)
			) {
				diagnostics.add(
					"UNREACHABLE_HISTORY_AUDIENCE",
					"/history/audience",
					"current_host cannot be used for message history because the host console "
						+ "and timeline do not consume player-history records; use an audience "
						+ "with a player-terminal projection"
				);
			}
			validateMessageAudience(
				history.audience(),
				"/history/audience",
				cue.game(),
				diagnostics
			);
			validateMessageLocalizedTemplate(
				cue,
				history.title(),
				Map.of(),
				Optional.empty(),
				"/history/title",
				diagnostics
			);
			validateMessageLocalizedTemplate(
				cue,
				history.body(),
				Map.of(),
				Optional.empty(),
				"/history/body",
				diagnostics
			);
			if (history.taskSource() == HistoryTaskSource.PARAMETER) {
				String parameterId = history.taskParameter().orElse(null);
				ParameterDefinition parameter = parameterId == null
					? null
					: cue.parameters().get(parameterId);
				if (parameter == null) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/history/task_parameter",
						"history task parameter is not declared"
					);
				} else if (
					parameter.type() != ParameterType.IDENTIFIER
						|| (
							parameter.identifierKind() != IdentifierKind.ANY
								&& parameter.identifierKind() != IdentifierKind.TASK
						)
				) {
					diagnostics.add(
						"TYPE_MISMATCH",
						"/history/task_parameter",
						"history task parameter must be an identifier parameter of task kind"
					);
				}
			} else if (history.taskParameter().isPresent()) {
				diagnostics.add(
					"CONTRADICTORY_VALUE",
					"/history/task_parameter",
					"task_parameter requires parameter task_source"
				);
			}
			for (String field : history.savedFields()) {
				if (!cue.fields().containsKey(field)) {
					diagnostics.add(
						"MISSING_REFERENCE",
						"/history/saved_fields",
						"saved history field " + field + " is not a cue field"
					);
				}
			}
		}

		private void validateMessageAssets(
			final MessageCueDefinition cue,
			final MessageDiagnostics diagnostics
		) {
			Set<String> identities = new HashSet<>();
			for (int index = 0; index < cue.assets().size(); index++) {
				AssetSpec asset = cue.assets().get(index);
				String pointer = "/assets/" + index;
				String identity = asset.type() + ":" + asset.id();
				if (!identities.add(identity)) {
					diagnostics.add(
						"DUPLICATE_VALUE",
						pointer,
						"asset " + identity + " is declared more than once"
					);
				}
				validateMessageMissingFallback(
					asset.missing(),
					asset.fallback(),
					pointer + "/missing",
					pointer + "/fallback",
					diagnostics
				);
				if (
					asset.missing() == MissingAssetMode.BLOCK_START
						&& !asset.requiredForStart()
				) {
					diagnostics.add(
						"CONTRADICTORY_VALUE",
						pointer + "/required_for_start",
						"block_start assets must be required_for_start"
					);
				}
			}
		}

		private void validateMessageMissingFallback(
			final MissingAssetMode missing,
			final Optional<Identifier> fallback,
			final String missingPointer,
			final String fallbackPointer,
			final MessageDiagnostics diagnostics
		) {
			if (missing == MissingAssetMode.FALLBACK && fallback.isEmpty()) {
				diagnostics.add(
					"MISSING_REQUIRED",
					fallbackPointer,
					"fallback mode requires a fallback resource"
				);
			} else if (missing != MissingAssetMode.FALLBACK && fallback.isPresent()) {
				diagnostics.add(
					"CONTRADICTORY_VALUE",
					missingPointer,
					"fallback resource requires fallback missing mode"
				);
			}
		}

		private final class MessageDiagnostics {
			private final Source source;
			private final List<Problem> retained = new ArrayList<>();
			private int totalCount;

			private MessageDiagnostics(final Source source) {
				this.source = source;
			}

			private void add(
				final String code,
				final String pointer,
				final String message
			) {
				this.totalCount++;
				if (this.retained.size() < MAX_DIAGNOSTIC_DETAILS) {
					this.retained.add(messageProblem(this.source, code, pointer, message));
				}
			}

			private boolean isEmpty() {
				return this.totalCount == 0;
			}

			private List<Problem> retained() {
				return List.copyOf(this.retained);
			}

			private int totalCount() {
				return this.totalCount;
			}
		}

		private List<Problem> messageProblems(
			final Source source,
			final List<MessageDefinitionParser.Issue> issues
		) {
			List<Problem> diagnostics = new ArrayList<>(
				Math.min(issues.size(), MAX_DIAGNOSTIC_DETAILS)
			);
			for (MessageDefinitionParser.Issue issue : issues) {
				if (diagnostics.size() >= MAX_DIAGNOSTIC_DETAILS) {
					break;
				}
				diagnostics.add(
					messageProblem(source, issue.code(), issue.path(), issue.message())
				);
			}
			return List.copyOf(diagnostics);
		}

		private static List<Problem> retainMessageDiagnostics(final List<Problem> diagnostics) {
			if (diagnostics.size() <= MAX_DIAGNOSTIC_DETAILS) {
				return List.copyOf(diagnostics);
			}
			return List.copyOf(diagnostics.subList(0, MAX_DIAGNOSTIC_DETAILS));
		}

		private Problem messageProblem(
			final Source source,
			final String code,
			final String pointer,
			final String message
		) {
			return new Problem(
				code,
				source == null ? null : source.type(),
				source == null ? null : source.id(),
				source == null ? null : source.resource(),
				source == null ? "unknown" : source.sourcePack(),
				pointer == null ? "" : pointer,
				message == null ? "unknown error" : message
			);
		}

		private void disableDuplicateMessage(final Source previous, final Source source) {
			DocumentKey key = new DocumentKey(source.type(), source.id());
			List<Problem> diagnostics = new ArrayList<>();
			diagnostics.add(
				messageProblem(
					source,
					"DUPLICATE_DEFINITION",
					"",
					"definition already supplied by " + previous.resource()
				)
			);
			MessageIdentity identity = MessageIdentity.optional();
			if (source.type() == DefinitionType.MESSAGE_CUE) {
				MessageIdentity previousIdentity = messageIdentity(previous);
				MessageIdentity currentIdentity = messageIdentity(source);
				DisabledMessageDefinition accumulated = this.disabledMessages.get(key);
				boolean required = (
					previousIdentity.trusted() && previousIdentity.required()
				)
					|| (currentIdentity.trusted() && currentIdentity.required())
					|| (accumulated != null && accumulated.required());
				Set<Identifier> declaredGames = this.duplicateMessageGames.computeIfAbsent(
					key,
					ignored -> new LinkedHashSet<>()
				);
				if (declaredGames.isEmpty()) {
					if (previousIdentity.trusted()) {
						previousIdentity.game().ifPresent(declaredGames::add);
					}
					if (accumulated != null) {
						accumulated.game().ifPresent(declaredGames::add);
					}
				}
				if (currentIdentity.trusted()) {
					currentIdentity.game().ifPresent(declaredGames::add);
				} else {
					diagnostics.add(
						messageProblem(
							source,
							"UNTRUSTED_ENVELOPE",
							"",
							"duplicate cue envelope cannot be verified; treating it as optional"
						)
					);
				}
				Optional<Identifier> game = declaredGames.size() == 1
					? Optional.of(declaredGames.iterator().next())
					: Optional.empty();
				if (declaredGames.size() > 1) {
					this.multiGameMessageFailures.add(key);
					diagnostics.add(
						messageProblem(
							source,
							"CONFLICTING_GAME_CONTEXT",
							"/game",
							"duplicate definitions declare conflicting games " + declaredGames
						)
					);
				}
				if (required && declaredGames.size() > 1) {
					this.startBlockedGames.addAll(declaredGames);
				}
				identity = new MessageIdentity(required, game, true);
			}
			disableMessage(source, identity, diagnostics, diagnostics.size());
		}

		private void disableMessage(
			final Source source,
			final MessageIdentity identity,
			final List<Problem> diagnostics,
			final int totalDiagnosticCount
		) {
			disableMessage(
				source,
				identity,
				diagnostics,
				totalDiagnosticCount,
				Optional.empty()
			);
		}

		private void disableMessage(
			final Source source,
			final MessageIdentity identity,
			final List<Problem> diagnostics,
			final int totalDiagnosticCount,
			final Optional<StaticFallbackSpec> staticFallback
		) {
			DocumentKey key = new DocumentKey(source.type(), source.id());
			SourceDocument document = messageSourceDocument(source, canonicalMessageSource(source));
			this.sourceDocuments.put(key, document);
			if (source.type() == DefinitionType.TEXT_EFFECT) {
				this.textEffects.remove(source.id());
			} else if (source.type() == DefinitionType.MESSAGE_CUE) {
				this.messageCues.remove(source.id());
			}
			DisabledMessageDefinition previous = this.disabledMessages.get(key);
			List<Problem> merged = new ArrayList<>();
			int mergedTotal = totalDiagnosticCount;
			if (previous != null) {
				merged.addAll(previous.diagnostics());
				mergedTotal += previous.totalDiagnosticCount();
			}
			merged.addAll(diagnostics);
			Optional<StaticFallbackSpec> retainedFallback = staticFallback == null
				? Optional.empty()
				: staticFallback;
			if (retainedFallback.isEmpty() && previous != null) {
				retainedFallback = previous.staticFallback();
			}
			this.disabledMessages.put(
				key,
				new DisabledMessageDefinition(
					document,
					identity.required(),
					identity.game(),
					retainedFallback,
					retainMessageDiagnostics(merged),
					Math.max(mergedTotal, merged.size())
				)
			);
			boolean fallbackSatisfiesRequired = retainedFallback
				.filter(StaticFallbackSpec::fallbackSatisfiesRequired)
				.isPresent();
			if (identity.required() && !fallbackSatisfiesRequired) {
				identity.game().ifPresent(this.startBlockedGames::add);
				if (
					identity.game().isEmpty()
						&& this.games.size() == 1
						&& !this.multiGameMessageFailures.contains(key)
				) {
					this.startBlockedGames.add(this.games.keySet().iterator().next());
				}
			}
		}

		private MessageIdentity messageIdentity(final Source source) {
			if (source.type() != DefinitionType.MESSAGE_CUE) {
				return MessageIdentity.optional();
			}
			if (source.messageEnvelopeHint().isPresent()) {
				MessageEnvelopeHint hint = source.messageEnvelopeHint().orElseThrow();
				return new MessageIdentity(hint.required(), hint.game(), true);
			}
			if (source.json().length() > MAX_DEFINITION_CHARACTERS) {
				return MessageIdentity.untrusted();
			}
			try {
				JsonElement root = parseStrict(source.json());
				if (!root.isJsonObject()) {
					return MessageIdentity.untrusted();
				}
				JsonObject object = root.getAsJsonObject();
				JsonElement requiredValue = object.get("required");
				boolean trusted = requiredValue == null || requiredValue.isJsonNull();
				boolean required = false;
				if (
					requiredValue != null
						&& !requiredValue.isJsonNull()
						&& requiredValue.isJsonPrimitive()
						&& requiredValue.getAsJsonPrimitive().isBoolean()
				) {
					trusted = true;
					required = requiredValue.getAsBoolean();
				}
				JsonElement gameValue = object.get("game");
				Optional<Identifier> game = Optional.empty();
				if (
					gameValue != null
						&& !gameValue.isJsonNull()
						&& gameValue.isJsonPrimitive()
						&& gameValue.getAsJsonPrimitive().isString()
				) {
					game = Optional.ofNullable(parseIdentifier(gameValue.getAsString()));
					if (game.isEmpty()) {
						trusted = false;
					}
				} else if (gameValue != null && !gameValue.isJsonNull()) {
					trusted = false;
				}
				return trusted
					? new MessageIdentity(required, game, true)
					: MessageIdentity.untrusted();
			} catch (IOException | JsonParseException | NumberFormatException error) {
				return MessageIdentity.untrusted();
			}
		}

		private void storeMessageDocument(final Source source, final String canonicalJson) {
			SourceDocument document = messageSourceDocument(source, canonicalJson);
			this.sourceDocuments.put(document.key(), document);
		}

		private SourceDocument messageSourceDocument(
			final Source source,
			final String canonicalJson
		) {
			DocumentKey key = new DocumentKey(source.type(), source.id());
			return new SourceDocument(
				key,
				source.resource(),
				source.sourcePack(),
				canonicalJson,
				sha256(canonicalJson)
			);
		}

		private String canonicalMessageSource(final Source source) {
			if (source.json().length() > MAX_DEFINITION_CHARACTERS) {
				return source.json().substring(0, MAX_DEFINITION_CHARACTERS + 1);
			}
			try {
				return canonical(parseStrict(source.json()));
			} catch (IOException | JsonParseException | NumberFormatException error) {
				return source.json();
			}
		}

		private static boolean isMessageDefinition(final DefinitionType type) {
			return type == DefinitionType.TEXT_EFFECT || type == DefinitionType.MESSAGE_CUE;
		}

		private record MessageIdentity(
			boolean required,
			Optional<Identifier> game,
			boolean trusted
		) {
			private MessageIdentity {
				game = game == null ? Optional.empty() : game;
			}

			private static MessageIdentity optional() {
				return new MessageIdentity(false, Optional.empty(), true);
			}

			private static MessageIdentity untrusted() {
				return new MessageIdentity(false, Optional.empty(), false);
			}
		}

		private MessageHookSet parseMessageHooks(
			final ObjectReader parent,
			final Set<MessageHookEvent> allowedEvents
		) {
			JsonObject object = parent.optionalObject("message_hooks");
			if (object == null) {
				return MessageHookSet.empty();
			}
			ObjectReader hooksReader = parent.child("message_hooks", object);
			if (object.size() > MessageHookDefinitions.MAX_HOOKS_PER_OWNER) {
				add(
					parent.source,
					"RESOURCE_LIMIT",
					hooksReader.pointer(""),
					"message hook count exceeds "
						+ MessageHookDefinitions.MAX_HOOKS_PER_OWNER
				);
			}
			Map<MessageHookEvent, MessageHookReference> hooks = new LinkedHashMap<>();
			for (String eventName : object.keySet()) {
				JsonElement element = hooksReader.take(eventName);
				String pointer = hooksReader.pointer("/" + escapePointer(eventName));
				MessageHookEvent event;
				try {
					event = MessageHookEvent.valueOf(eventName.toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException error) {
					add(
						parent.source,
						"INVALID_ENUM",
						pointer,
						"unknown message hook event " + eventName
					);
					continue;
				}
				if (!allowedEvents.contains(event)) {
					add(
						parent.source,
						"INVALID_COMBINATION",
						pointer,
						"message hook event " + eventName + " is not allowed for this definition"
					);
					continue;
				}
				MessageHookReference reference = parseMessageHookReference(
					parent.source,
					pointer,
					element
				);
				if (reference != null) {
					hooks.put(event, reference);
				}
			}
			hooksReader.finish();
			return new MessageHookSet(hooks);
		}

		private MessageHookReference parseMessageHookReference(
			final Source source,
			final String pointer,
			final JsonElement element
		) {
			if (element != null && element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isString()) {
				Identifier cue = parseIdentifier(element.getAsString());
				if (cue == null) {
					add(
						source,
						"INVALID_IDENTIFIER",
						pointer,
						"expected an explicitly namespaced message cue identifier"
					);
					return null;
				}
				return new MessageHookReference(cue);
			}
			if (element == null || !element.isJsonObject()) {
				add(
					source,
					"TYPE_MISMATCH",
					pointer,
					"message hook must be a cue identifier string or an object"
				);
				return null;
			}
			ObjectReader reader = new ObjectReader(
				source,
				pointer,
				element.getAsJsonObject(),
				this.problems
			);
			Identifier cue = reader.requiredIdentifier("cue");
			JsonObject argumentsObject = reader.optionalObject("arguments");
			Map<String, String> arguments = parseMessageHookArguments(
				source,
				reader.pointer("/arguments"),
				argumentsObject
			);
			reader.finish();
			return cue == null ? null : new MessageHookReference(cue, arguments);
		}

		private Map<String, String> parseMessageHookArguments(
			final Source source,
			final String pointer,
			final JsonObject object
		) {
			if (object == null) {
				return Map.of();
			}
			if (object.size() > MessageHookDefinitions.MAX_FIXED_ARGUMENTS) {
				add(
					source,
					"RESOURCE_LIMIT",
					pointer,
					"message hook fixed argument count exceeds "
						+ MessageHookDefinitions.MAX_FIXED_ARGUMENTS
				);
			}
			Map<String, String> arguments = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				String name = entry.getKey();
				String argumentPointer = pointer + "/" + escapePointer(name);
				if (!MESSAGE_HOOK_ARGUMENT.matcher(name).matches()) {
					add(
						source,
						"INVALID_LOCAL_ID",
						argumentPointer,
						"expected lowercase [a-z][a-z0-9_.-]{0,63}"
					);
					continue;
				}
				if (MessageHookDefinitions.RESERVED_AUTOMATIC_ARGUMENTS.contains(name)) {
					add(
						source,
						"INVALID_COMBINATION",
						argumentPointer,
						"fixed message hook arguments cannot override an automatic argument"
					);
					continue;
				}
				JsonElement value = entry.getValue();
				if (value == null || !value.isJsonPrimitive()) {
					add(
						source,
						"TYPE_MISMATCH",
						argumentPointer,
						"fixed message hook argument must be a string, number, or boolean"
					);
					continue;
				}
				String canonical = value.getAsJsonPrimitive().getAsString();
				if (canonical.length() > MAX_SHORT_STRING_LENGTH) {
					add(source, "RESOURCE_LIMIT", argumentPointer, "fixed argument is too long");
					continue;
				}
				arguments.put(name, canonical);
			}
			return Map.copyOf(arguments);
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
			Optional<PlayerTerminalConfig> playerTerminal = parsePlayerTerminalConfig(reader);
			Optional<OpeningCountdownReference> openingCountdown = parseOpeningCountdownReference(reader);
			MessageHookSet messageHooks = parseMessageHooks(reader, GAME_MESSAGE_HOOKS);
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
			if (
				apiVersion != null
					&& apiVersion < 3
					&& playerTerminal.isPresent()
			) {
				add(
					source,
					"UNSUPPORTED_API",
					"/player_terminal",
					"player_terminal requires api_version 3"
				);
			}
			if (
				apiVersion != null
					&& apiVersion < 4
					&& openingCountdown.isPresent()
			) {
				add(
					source,
					"UNSUPPORTED_API",
					"/opening_countdown",
					"opening_countdown requires api_version 4"
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
						readiness,
						playerTerminal,
						messageHooks,
						openingCountdown
					)
				);
			}
		}

		private Optional<OpeningCountdownReference> parseOpeningCountdownReference(
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("opening_countdown");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("opening_countdown", object);
			Identifier definition = reader.requiredIdentifier("definition");
			boolean required = reader.optionalBoolean("required", true);
			reader.finish();
			return definition == null
				? Optional.empty()
				: Optional.of(new OpeningCountdownReference(definition, required));
		}

		private Optional<PlayerTerminalConfig> parsePlayerTerminalConfig(
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("player_terminal");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("player_terminal", object);
			Identifier defaultPage = reader.requiredIdentifier("default_page");
			boolean historyEnabled = reader.optionalBoolean("history_enabled", false);
			HistorySource historySource = reader.optionalEnum(
				"history_source",
				HistorySource.class,
				HistorySource.EVENTS
			);
			reader.finish();
			return defaultPage == null || historySource == null
				? Optional.empty()
				: Optional.of(
					new PlayerTerminalConfig(defaultPage, historyEnabled, historySource)
				);
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
			MessageHookSet messageHooks = parseMessageHooks(reader, READINESS_MESSAGE_HOOKS);
			reader.finish();
			return phase == null || action == null
				? Optional.empty()
				: Optional.of(
					new ReadinessDefinition(
						phase,
						action,
						disconnectInvalidates,
						hostCanForce,
						messageHooks
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
			MessageHookSet messageHooks = parseMessageHooks(reader, ROLE_MESSAGE_HOOKS);
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
						tab,
						messageHooks
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
			MessageHookSet messageHooks = parseMessageHooks(reader, PHASE_MESSAGE_HOOKS);
			if (game != null && name != null) {
				this.phases.put(
					source.id(),
					new PhaseDefinition(
						source.id(), game, name, transitions, onEnter, onExit, messageHooks
					)
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
			MessageHookSet messageHooks = parseMessageHooks(reader, FLOW_MESSAGE_HOOKS);
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
						nodes,
						messageHooks
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
			MessageHookSet messageHooks = parseMessageHooks(reader, TASK_MESSAGE_HOOKS);

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
						statistics,
						messageHooks
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
				Optional<PlayerHistoryPresentation> playerHistory = parsePlayerHistory(
					source,
					reader
				);
				MessageHookSet messageHooks = parseMessageHooks(
					reader,
					TASK_EVENT_MESSAGE_HOOKS
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
							recapVisibility,
							playerHistory,
							messageHooks
						)
					);
				}
			}
			return List.copyOf(events);
		}

		private Optional<PlayerHistoryPresentation> parsePlayerHistory(
			final Source source,
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("player_history");
			if (object == null) {
				return Optional.empty();
			}
			ObjectReader reader = parent.child("player_history", object);
			HistoryRelease release = reader.optionalEnum(
				"release",
				HistoryRelease.class,
				HistoryRelease.IMMEDIATE
			);
			JsonObject audienceObject = reader.requiredObject("audience");
			Audience audience = audienceObject == null
				? null
				: parseAudienceObject(reader, "audience", audienceObject, false);
			RichText title = reader.requiredText("title");
			Optional<RichText> summary = reader.optionalText("summary");
			Optional<Identifier> icon = reader.optionalIdentifier("icon");
			Optional<String> color = reader.optionalString("color");
			Optional<String> category = reader.optionalLocalId("category");
			boolean showGameTime = reader.optionalBoolean("show_game_time", true);
			boolean showTask = reader.optionalBoolean("show_task", true);
			boolean showActor = reader.optionalBoolean("show_actor", false);
			boolean showTargets = reader.optionalBoolean("show_targets", false);
			Set<String> parameters = parseBoundedLocalIdSet(
				source,
				reader,
				"parameters",
				MAX_PLAYER_HISTORY_FIELDS
			);
			if (!parameters.isEmpty()) {
				add(
					source,
					"INVALID_CONSTRAINT",
					reader.pointer("/parameters"),
					"non-empty player history parameters are reserved for a future API version"
				);
			}
			Set<String> statistics = parseBoundedLocalIdSet(
				source,
				reader,
				"statistics",
				MAX_PLAYER_HISTORY_FIELDS
			);
			boolean showPersonalResult = reader.optionalBoolean("show_personal_result", false);
			Optional<RichText> details = reader.optionalText("details");
			Optional<Identifier> detailPage = reader.optionalIdentifier("detail_page");
			color.ifPresent(value -> {
				if (!HEX_COLOR.matcher(value).matches()) {
					add(source, "INVALID_COLOR", reader.pointer("/color"), "expected #RRGGBB");
				}
			});
			reader.finish();
			if (release == null || audience == null || title == null) {
				return Optional.empty();
			}
			return Optional.of(
				new PlayerHistoryPresentation(
					release,
					audience,
					title,
					summary,
					icon,
					color,
					category,
					showGameTime,
					showTask,
					showActor,
					showTargets,
					parameters,
					statistics,
					showPersonalResult,
					details,
					detailPage
				)
			);
		}

		private Set<String> parseBoundedLocalIdSet(
			final Source source,
			final ObjectReader reader,
			final String name,
			final int maximum
		) {
			JsonArray array = reader.optionalArray(name);
			if (array == null) {
				return Set.of();
			}
			if (array.size() > maximum) {
				add(
					source,
					"RESOURCE_LIMIT",
					reader.pointer("/" + escapePointer(name)),
					name + " count exceeds " + maximum
				);
			}
			Set<String> result = new LinkedHashSet<>();
			for (int index = 0; index < Math.min(array.size(), maximum); index++) {
				JsonElement element = array.get(index);
				String pointer = reader.pointer("/" + escapePointer(name) + "/" + index);
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					add(source, "TYPE_MISMATCH", pointer, "expected a local identifier string");
					continue;
				}
				String value = element.getAsString();
				if (!LOCAL_ID.matcher(value).matches() || value.length() > MAX_SHORT_STRING_LENGTH) {
					add(source, "INVALID_LOCAL_ID", pointer, "expected bounded lowercase [a-z0-9_.-]+");
				} else if (!result.add(value)) {
					add(source, "DUPLICATE_VALUE", pointer, "duplicate value " + value);
				}
			}
			return Set.copyOf(result);
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

		private void parsePlayerRoute(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			Identifier page = reader.requiredIdentifier("page");
			Integer priority = reader.requiredInt("priority");
			Audience audience = parseRequiredAudience(reader);
			Set<Identifier> phases = reader.optionalIdentifierSet("phases");
			Set<Identifier> tasks = reader.optionalIdentifierSet("tasks");
			Set<PlayerTaskState> taskStates = parseEnumSet(
				source,
				reader,
				"task_states",
				PlayerTaskState.class
			);
			Optional<Identifier> predicate = reader.optionalIdentifier("predicate");
			if (priority != null && (priority < -10_000 || priority > 10_000)) {
				add(source, "OUT_OF_RANGE", "/priority", "priority must be between -10000 and 10000");
			}
			if (
				game != null
					&& page != null
					&& priority != null
					&& priority >= -10_000
					&& priority <= 10_000
					&& audience != null
			) {
				this.playerRoutes.put(
					source.id(),
					new PlayerRouteDefinition(
						source.id(),
						game,
						page,
						priority,
						audience,
						phases,
						tasks,
						taskStates,
						predicate
					)
				);
			}
		}

		private void parsePlayerData(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			PlayerDataSource dataSource = parsePlayerDataSource(source, reader);
			Set<PlayerSurface> surfaces = parseEnumSet(
				source,
				reader,
				"surfaces",
				PlayerSurface.class
			);
			Audience audience = parseRequiredAudience(reader);
			Set<Identifier> phases = reader.optionalIdentifierSet("phases");
			Set<Identifier> tasks = reader.optionalIdentifierSet("tasks");
			Set<PlayerTaskState> taskStates = parseEnumSet(
				source,
				reader,
				"task_states",
				PlayerTaskState.class
			);
			Optional<Identifier> predicate = reader.optionalIdentifier("predicate");
			Optional<RichText> name = reader.optionalText("name");
			Optional<String> format = reader.optionalString("format");
			Optional<RichText> maskedFallback = reader.optionalText("masked_fallback");
			name.ifPresent(value -> {
				if (value.plainText().length() > MAX_PLAYER_DATA_NAME_LENGTH) {
					add(
						source,
						"RESOURCE_LIMIT",
						"/name",
						"player data name exceeds " + MAX_PLAYER_DATA_NAME_LENGTH + " characters"
					);
				}
			});
			if (surfaces.isEmpty()) {
				add(source, "OUT_OF_RANGE", "/surfaces", "player data requires at least one surface");
			}
			if (
				game != null
					&& dataSource != null
					&& !surfaces.isEmpty()
					&& audience != null
			) {
				this.playerData.put(
					source.id(),
					new PlayerDataDefinition(
						source.id(),
						game,
						dataSource,
						surfaces,
						audience,
						phases,
						tasks,
						taskStates,
						predicate,
						name,
						format,
						maskedFallback
					)
				);
			}
		}

		private PlayerDataSource parsePlayerDataSource(
			final Source source,
			final ObjectReader parent
		) {
			JsonObject object = parent.requiredObject("source");
			if (object == null) {
				return null;
			}
			ObjectReader reader = parent.child("source", object);
			PlayerDataSourceType type = reader.requiredEnum("type", PlayerDataSourceType.class);
			Optional<Identifier> id = reader.optionalIdentifier("id");
			Optional<String> key = reader.optionalLocalId("key");
			reader.finish();
			if (type == null) {
				return null;
			}
			boolean needsField = type == PlayerDataSourceType.FIELD
				|| type == PlayerDataSourceType.EXCLUSIVE_CHOICE;
			boolean needsTask = switch (type) {
				case TASK_PROGRESS,
					TASK_RESULT,
					TASK_STATISTIC -> true;
				default -> false;
			};
			boolean acceptsOptionalTask = switch (type) {
				case TASK_ID,
					TASK_NAME,
					TASK_DESCRIPTION,
					TASK_STATUS,
					TASK_ELAPSED_TICKS,
					TASK_REMAINING_TICKS -> true;
				default -> false;
			};
			boolean needsKey = type == PlayerDataSourceType.TASK_PROGRESS
				|| type == PlayerDataSourceType.TASK_STATISTIC;
			if ((needsField || needsTask) && id.isEmpty()) {
				add(
					source,
					"MISSING_KEY",
					reader.pointer("/id"),
					type.name().toLowerCase(Locale.ROOT) + " source requires id"
				);
			}
			if (!needsField && !needsTask && !acceptsOptionalTask && id.isPresent()) {
				add(
					source,
					"INVALID_COMBINATION",
					reader.pointer("/id"),
					type.name().toLowerCase(Locale.ROOT) + " source does not accept id"
				);
			}
			if (needsKey && key.isEmpty()) {
				add(
					source,
					"MISSING_KEY",
					reader.pointer("/key"),
					type.name().toLowerCase(Locale.ROOT) + " source requires key"
				);
			}
			if (!needsKey && type != PlayerDataSourceType.TASK_RESULT && key.isPresent()) {
				add(
					source,
					"INVALID_COMBINATION",
					reader.pointer("/key"),
					type.name().toLowerCase(Locale.ROOT) + " source does not accept key"
				);
			}
			if (
				((needsField || needsTask) && id.isEmpty())
					|| (!needsField && !needsTask && !acceptsOptionalTask && id.isPresent())
					|| (needsKey && key.isEmpty())
					|| (!needsKey && type != PlayerDataSourceType.TASK_RESULT && key.isPresent())
			) {
				return null;
			}
			return new PlayerDataSource(type, id, key);
		}

		private void parsePlayerAction(final Source source, final ObjectReader reader) {
			Identifier game = reader.requiredIdentifier("game");
			PlayerOperation operation = parsePlayerOperation(source, reader);
			Audience audience = parseRequiredAudience(reader);
			Set<Identifier> phases = reader.optionalIdentifierSet("phases");
			Set<Identifier> tasks = reader.optionalIdentifierSet("tasks");
			Set<PlayerTaskState> taskStates = parseEnumSet(
				source,
				reader,
				"task_states",
				PlayerTaskState.class
			);
			Optional<Identifier> predicate = reader.optionalIdentifier("predicate");
			UsageSettings usage = parseUsage(source, reader);
			CooldownSettings cooldown = parseCooldown(source, reader);
			Optional<Confirmation> confirmation = parseConfirmation(reader);
			ExecutionContext executionContext = reader.optionalEnum(
				"execution_context",
				ExecutionContext.class,
				ExecutionContext.PLAYER
			);
			PlayerActionFeedback feedback = parsePlayerActionFeedback(reader);
			AuditPolicy audit = reader.optionalEnum("audit", AuditPolicy.class, AuditPolicy.ALL);
			if (
				operation instanceof PlayerOpenPageOperation
					&& executionContext != null
					&& executionContext != ExecutionContext.PLAYER
			) {
				add(
					source,
					"INVALID_COMBINATION",
					"/execution_context",
					"open_page actions use player execution context"
				);
			}
			if (
				game != null
					&& operation != null
					&& audience != null
					&& usage != null
					&& cooldown != null
					&& executionContext != null
					&& audit != null
					&& (
						!(operation instanceof PlayerOpenPageOperation)
							|| executionContext == ExecutionContext.PLAYER
					)
			) {
				this.playerActions.put(
					source.id(),
					new PlayerActionDefinition(
						source.id(),
						game,
						operation,
						audience,
						phases,
						tasks,
						taskStates,
						predicate,
						usage.scope(),
						usage.maximum(),
						cooldown.ticks(),
						cooldown.scope(),
						confirmation,
						executionContext,
						feedback,
						audit
					)
				);
			}
		}

		private PlayerOperation parsePlayerOperation(
			final Source source,
			final ObjectReader parent
		) {
			JsonObject object = parent.requiredObject("operation");
			if (object == null) {
				return null;
			}
			ObjectReader reader = parent.child("operation", object);
			String type = reader.requiredString("type");
			PlayerOperation operation = null;
			if (type != null) {
				operation = switch (type.toLowerCase(Locale.ROOT)) {
					case "open_page" -> {
						Identifier page = reader.requiredIdentifier("page");
						yield page == null ? null : new PlayerOpenPageOperation(page);
					}
					case "run_function" -> {
						Identifier function = reader.requiredIdentifier("function");
						yield function == null ? null : new PlayerRunFunctionOperation(function);
					}
					default -> {
						add(
							source,
							"UNKNOWN_OPERATION",
							reader.pointer("/type"),
							"unknown player operation type " + type
						);
						yield null;
					}
				};
			}
			reader.finish();
			return operation;
		}

		private UsageSettings parseUsage(
			final Source source,
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("usage");
			if (object == null) {
				return new UsageSettings(UsageScope.UNLIMITED, 0);
			}
			ObjectReader reader = parent.child("usage", object);
			UsageScope scope = reader.requiredEnum("scope", UsageScope.class);
			int maximum = reader.optionalInt("max", 0);
			boolean valid = scope != null;
			if (scope == UsageScope.UNLIMITED && maximum != 0) {
				add(
					source,
					"INVALID_COMBINATION",
					reader.pointer("/max"),
					"unlimited usage requires max 0"
				);
				valid = false;
			} else if (
				scope != null
					&& scope != UsageScope.UNLIMITED
					&& (maximum < 1 || maximum > MAX_PLAYER_ACTION_USES)
			) {
				add(
					source,
					"OUT_OF_RANGE",
					reader.pointer("/max"),
					"limited usage max must be between 1 and " + MAX_PLAYER_ACTION_USES
				);
				valid = false;
			}
			reader.finish();
			return valid ? new UsageSettings(scope, maximum) : null;
		}

		private CooldownSettings parseCooldown(
			final Source source,
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("cooldown");
			if (object == null) {
				return new CooldownSettings(0L, CooldownScope.PLAYER);
			}
			ObjectReader reader = parent.child("cooldown", object);
			Long ticks = reader.requiredLong("ticks");
			CooldownScope scope = reader.optionalEnum(
				"scope",
				CooldownScope.class,
				CooldownScope.PLAYER
			);
			if (
				ticks != null
					&& (ticks < 0L || ticks > MAX_PLAYER_ACTION_COOLDOWN_TICKS)
			) {
				add(
					source,
					"OUT_OF_RANGE",
					reader.pointer("/ticks"),
					"cooldown ticks must be between 0 and "
						+ MAX_PLAYER_ACTION_COOLDOWN_TICKS
				);
			}
			reader.finish();
			return ticks == null
					|| ticks < 0L
					|| ticks > MAX_PLAYER_ACTION_COOLDOWN_TICKS
					|| scope == null
				? null
				: new CooldownSettings(ticks, scope);
		}

		private PlayerActionFeedback parsePlayerActionFeedback(
			final ObjectReader parent
		) {
			JsonObject object = parent.optionalObject("feedback");
			if (object == null) {
				return PlayerActionFeedback.empty();
			}
			ObjectReader reader = parent.child("feedback", object);
			PlayerActionFeedback feedback = new PlayerActionFeedback(
				reader.optionalText("success"),
				reader.optionalText("denied"),
				reader.optionalText("failed")
			);
			reader.finish();
			return feedback;
		}

		private Audience parseRequiredAudience(final ObjectReader parent) {
			JsonObject object = parent.requiredObject("audience");
			return object == null
				? null
				: parseAudienceObject(parent, "audience", object, false);
		}

		private <E extends Enum<E>> Set<E> parseEnumSet(
			final Source source,
			final ObjectReader reader,
			final String name,
			final Class<E> type
		) {
			JsonArray array = reader.optionalArray(name);
			if (array == null) {
				return Set.of();
			}
			if (array.size() > type.getEnumConstants().length) {
				add(
					source,
					"RESOURCE_LIMIT",
					reader.pointer("/" + escapePointer(name)),
					name + " count exceeds " + type.getEnumConstants().length
				);
			}
			Set<E> result = new LinkedHashSet<>();
			for (int index = 0; index < array.size(); index++) {
				JsonElement element = array.get(index);
				String pointer = reader.pointer("/" + escapePointer(name) + "/" + index);
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					add(source, "TYPE_MISMATCH", pointer, "expected an enum string");
					continue;
				}
				E value = parseEnumValue(source, pointer, element.getAsString(), type);
				if (value != null && !result.add(value)) {
					add(source, "DUPLICATE_VALUE", pointer, "duplicate value " + element.getAsString());
				}
			}
			return Set.copyOf(result);
		}

		private record UsageSettings(UsageScope scope, int maximum) {
		}

		private record CooldownSettings(long ticks, CooldownScope scope) {
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
			if (this.games.size() > 1) {
				Identifier conflictingGame = this.games.keySet()
					.stream()
					.skip(1L)
					.findFirst()
					.orElseThrow();
				add(
					origin(DefinitionType.GAME, conflictingGame),
					"MULTIPLE_GAME_PROFILES",
					"",
					"V3 accepts at most one Game Profile per active resource collection; found "
						+ this.games.size()
				);
				return;
			}
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
				game.playerTerminal().ifPresent(config -> validatePlayerTerminal(game, config));
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
				validatePlayerDefinitionCapacity(game);
				validatePlayerRoutePriorities(game);
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
			for (PlayerRouteDefinition route : this.playerRoutes.values()) {
				validatePlayerRoute(route);
			}
			for (PlayerDataDefinition data : this.playerData.values()) {
				validatePlayerData(data);
			}
			for (PlayerActionDefinition action : this.playerActions.values()) {
				validatePlayerAction(action);
			}
		}

		private void validatePlayerTerminal(
			final GameDefinition game,
			final PlayerTerminalConfig config
		) {
			if (game.apiVersion() < 3) {
				add(
					origin(DefinitionType.GAME, game.id()),
					"UNSUPPORTED_API",
					"/player_terminal",
					"player_terminal requires api_version 3"
				);
			}
			requireSameGame(
				DefinitionType.GAME,
				game.id(),
				"/player_terminal/default_page",
				game.id(),
				config.defaultPage(),
				this.pages,
				PageDefinition::game,
				"page"
			);
		}

		private void validatePlayerDefinitionCapacity(final GameDefinition game) {
			validateSameGameCapacity(
				game,
				DefinitionType.PLAYER_ROUTE,
				this.playerRoutes.values().stream().filter(value -> value.game().equals(game.id())).count(),
				MAX_PLAYER_ROUTES_PER_GAME,
				"player route"
			);
			validateSameGameCapacity(
				game,
				DefinitionType.PLAYER_DATA,
				this.playerData.values().stream().filter(value -> value.game().equals(game.id())).count(),
				MAX_PLAYER_DATA_PER_GAME,
				"player data"
			);
			validateSameGameCapacity(
				game,
				DefinitionType.PLAYER_ACTION,
				this.playerActions.values().stream().filter(value -> value.game().equals(game.id())).count(),
				MAX_PLAYER_ACTIONS_PER_GAME,
				"player action"
			);
		}

		private void validateSameGameCapacity(
			final GameDefinition game,
			final DefinitionType type,
			final long count,
			final int maximum,
			final String label
		) {
			if (count <= maximum) {
				return;
			}
			add(
				origin(DefinitionType.GAME, game.id()),
				"RESOURCE_LIMIT",
				"/player_terminal",
				"game " + label + " count exceeds " + maximum + " (" + type.directory() + ")"
			);
		}

		private void validatePlayerRoutePriorities(final GameDefinition game) {
			Map<Integer, Identifier> priorities = new HashMap<>();
			this.playerRoutes.values()
				.stream()
				.filter(route -> route.game().equals(game.id()))
				.sorted(java.util.Comparator.comparing(PlayerRouteDefinition::id))
				.forEach(route -> {
					Identifier previous = priorities.putIfAbsent(route.priority(), route.id());
					if (previous != null) {
						add(
							origin(DefinitionType.PLAYER_ROUTE, route.id()),
							"DUPLICATE_PRIORITY",
							"/priority",
							"priority "
								+ route.priority()
								+ " is already used by "
								+ previous
								+ " in game "
								+ game.id()
						);
					}
				});
		}

		private void validatePlayerRoute(final PlayerRouteDefinition route) {
			requirePlayerApi(DefinitionType.PLAYER_ROUTE, route.id(), route.game());
			requireSameGame(
				DefinitionType.PLAYER_ROUTE,
				route.id(),
				"/page",
				route.game(),
				route.page(),
				this.pages,
				PageDefinition::game,
				"page"
			);
			validatePlayerConditions(
				DefinitionType.PLAYER_ROUTE,
				route.id(),
				route.game(),
				route.audience(),
				route.phases(),
				route.tasks(),
				route.predicate()
			);
		}

		private void validatePlayerData(final PlayerDataDefinition data) {
			requirePlayerApi(DefinitionType.PLAYER_DATA, data.id(), data.game());
			validatePlayerConditions(
				DefinitionType.PLAYER_DATA,
				data.id(),
				data.game(),
				data.audience(),
				data.phases(),
				data.tasks(),
				data.predicate()
			);
			PlayerDataSource source = data.source();
			source.id().ifPresent(id -> {
				switch (source.type()) {
					case FIELD, EXCLUSIVE_CHOICE -> {
						requireSameGame(
							DefinitionType.PLAYER_DATA,
							data.id(),
							"/source/id",
							data.game(),
							id,
							this.fields,
							FieldDefinition::game,
							"field"
						);
						FieldDefinition field = this.fields.get(id);
						if (field != null && field.scope() != FieldScope.PLAYER) {
							add(
								origin(DefinitionType.PLAYER_DATA, data.id()),
								"INVALID_CONSTRAINT",
								"/source/id",
								"player data may only expose a player-scoped field"
							);
						}
						if (
							source.type() == PlayerDataSourceType.EXCLUSIVE_CHOICE
								&& field != null
								&& field.type() != FieldType.EXCLUSIVE_CHOICE
						) {
							add(
								origin(DefinitionType.PLAYER_DATA, data.id()),
								"TYPE_MISMATCH",
								"/source/id",
								"exclusive_choice source requires an exclusive-choice field"
							);
						}
					}
					case TASK_ID,
						TASK_NAME,
						TASK_DESCRIPTION,
						TASK_STATUS,
						TASK_ELAPSED_TICKS,
						TASK_REMAINING_TICKS,
						TASK_PROGRESS,
						TASK_RESULT,
						TASK_STATISTIC -> {
						requireSameGame(
							DefinitionType.PLAYER_DATA,
							data.id(),
							"/source/id",
							data.game(),
							id,
							this.tasks,
							TaskDefinition::game,
							"task"
						);
						validatePlayerDataTaskKey(data, id);
					}
					case ROLE,
						TEAM,
						LIFE_STATE,
						INITIALIZED,
						READY,
						GAME_ELAPSED_TICKS -> {
						// Structural parsing rejects IDs for these sources.
					}
				}
			});
			UiValueType projectedType = playerDataValueType(data);
			if (data.format().isPresent() && projectedType != UiValueType.STRING) {
				add(
					origin(DefinitionType.PLAYER_DATA, data.id()),
					"TYPE_MISMATCH",
					"/format",
					"format is only available for string-valued player data"
				);
			}
			data.format().ifPresent(format -> {
				int first = format.indexOf("{value}");
				if (
					first < 0
						|| first != format.lastIndexOf("{value}")
						|| format.replace("{value}", "").contains("{")
						|| format.replace("{value}", "").contains("}")
				) {
					add(
						origin(DefinitionType.PLAYER_DATA, data.id()),
						"INVALID_FORMAT",
						"/format",
						"format must contain exactly one {value} placeholder and no other braces"
					);
				}
			});
			if (
				data.maskedFallback().isPresent()
					&& projectedType != UiValueType.STRING
			) {
				add(
					origin(DefinitionType.PLAYER_DATA, data.id()),
					"TYPE_MISMATCH",
					"/masked_fallback",
					"masked_fallback is only available for string-valued player data"
				);
			}
		}

		private void validatePlayerDataTaskKey(
			final PlayerDataDefinition data,
			final Identifier taskId
		) {
			TaskDefinition task = this.tasks.get(taskId);
			if (task == null || !task.game().equals(data.game())) {
				return;
			}
			String key = data.source().key().orElse(null);
			if (
				data.source().type() == PlayerDataSourceType.TASK_RESULT
					&& key != null
					&& !task.results().containsKey(key)
			) {
				add(
					origin(DefinitionType.PLAYER_DATA, data.id()),
					"MISSING_REFERENCE",
					"/source/key",
					"task result " + key + " does not exist in " + taskId
				);
			}
			if (
				data.source().type() == PlayerDataSourceType.TASK_STATISTIC
					&& key != null
					&& task.statistics().stream().noneMatch(statistic -> statistic.id().equals(key))
			) {
				add(
					origin(DefinitionType.PLAYER_DATA, data.id()),
					"MISSING_REFERENCE",
					"/source/key",
					"task statistic " + key + " does not exist in " + taskId
				);
			}
			if (data.source().type() == PlayerDataSourceType.TASK_PROGRESS && key != null) {
				TaskStatisticDefinition statistic = task.statistics()
					.stream()
					.filter(value -> value.id().equals(key))
					.findFirst()
					.orElse(null);
				if (statistic == null) {
					add(
						origin(DefinitionType.PLAYER_DATA, data.id()),
						"MISSING_REFERENCE",
						"/source/key",
						"task progress statistic " + key + " does not exist in " + taskId
					);
				} else if (
					statistic.type() != TaskStatisticType.INTEGER
						&& statistic.type() != TaskStatisticType.DURATION_TICKS
				) {
					add(
						origin(DefinitionType.PLAYER_DATA, data.id()),
						"TYPE_MISMATCH",
						"/source/key",
						"task progress requires an integer or duration_ticks statistic"
					);
				}
			}
		}

		private void validatePlayerAction(final PlayerActionDefinition action) {
			requirePlayerApi(DefinitionType.PLAYER_ACTION, action.id(), action.game());
			validatePlayerConditions(
				DefinitionType.PLAYER_ACTION,
				action.id(),
				action.game(),
				action.audience(),
				action.phases(),
				action.tasks(),
				action.predicate()
			);
			if (action.operation() instanceof PlayerOpenPageOperation openPage) {
				requireSameGame(
					DefinitionType.PLAYER_ACTION,
					action.id(),
					"/operation/page",
					action.game(),
					openPage.page(),
					this.pages,
					PageDefinition::game,
					"page"
				);
			} else if (action.operation() instanceof PlayerRunFunctionOperation runFunction) {
				requireFunction(
					DefinitionType.PLAYER_ACTION,
					action.id(),
					"/operation/function",
					runFunction.function()
				);
			}
		}

		private void validatePlayerConditions(
			final DefinitionType ownerType,
			final Identifier owner,
			final Identifier game,
			final Audience audience,
			final Set<Identifier> phases,
			final Set<Identifier> tasks,
			final Optional<Identifier> predicate
		) {
			validateAudience(ownerType, owner, game, audience);
			for (Identifier phase : phases) {
				requireSameGame(
					ownerType,
					owner,
					"/phases",
					game,
					phase,
					this.phases,
					PhaseDefinition::game,
					"phase"
				);
			}
			for (Identifier task : tasks) {
				requireSameGame(
					ownerType,
					owner,
					"/tasks",
					game,
					task,
					this.tasks,
					TaskDefinition::game,
					"task"
				);
			}
			predicate.ifPresent(value ->
				requirePredicate(ownerType, owner, "/predicate", value)
			);
		}

		private void requirePlayerApi(
			final DefinitionType ownerType,
			final Identifier owner,
			final Identifier gameId
		) {
			requireGame(ownerType, owner, gameId);
			GameDefinition game = this.games.get(gameId);
			if (game != null && game.apiVersion() < 3) {
				add(
					origin(ownerType, owner),
					"UNSUPPORTED_API",
					"/game",
					ownerType.directory() + " require a game with api_version 3"
				);
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
				event.playerHistory().ifPresent(
					history -> validatePlayerHistory(task, event, history)
				);
			}
		}

		private void validatePlayerHistory(
			final TaskDefinition task,
			final TaskEventDefinition event,
			final PlayerHistoryPresentation history
		) {
			GameDefinition game = this.games.get(task.game());
			if (
				game == null
					|| game.apiVersion() < 3
					|| game.playerTerminal().isEmpty()
					|| !game.playerTerminal().orElseThrow().historyEnabled()
			) {
				add(
					origin(DefinitionType.TASK, task.id()),
					"INVALID_CONSTRAINT",
					"/events/" + event.id() + "/player_history",
					"player history requires an api_version 3 game with player_terminal.history_enabled"
				);
			}
			validateAudience(
				DefinitionType.TASK,
				task.id(),
				task.game(),
				history.audience()
			);
			history.detailPage().ifPresent(
				page -> requireSameGame(
					DefinitionType.TASK,
					task.id(),
					"/events/" + event.id() + "/player_history/detail_page",
					task.game(),
					page,
					this.pages,
					PageDefinition::game,
					"page"
				)
			);
			for (String statistic : history.statistics()) {
				if (
					task.statistics().stream()
						.noneMatch(definition -> definition.id().equals(statistic))
				) {
					add(
						origin(DefinitionType.TASK, task.id()),
						"MISSING_REFERENCE",
						"/events/" + event.id() + "/player_history/statistics",
						"task statistic " + statistic + " does not exist"
					);
				}
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
			validatePageNode(page, page.root(), nodesById, theme, false);
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
			final ThemeDefinition theme,
			final boolean historyItemScope
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
				if (node.type() == NodeType.CAROUSEL) {
					children.children()
						.stream()
						.filter(child -> child.id().isEmpty())
						.forEach(
							child -> add(
								origin(DefinitionType.PAGE, page.id()),
								"MISSING_KEY",
								child.pointer() + "/id",
								"carousel direct children require stable ids"
							)
						);
				}
				children.children().forEach(
					child -> validatePageNode(
						page,
						child,
						nodesById,
						theme,
						historyItemScope
					)
				);
			} else if (node.content() instanceof SingleChildContent single) {
				validatePageNode(
					page,
					single.child(),
					nodesById,
					theme,
					historyItemScope
				);
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
				validatePageAction(page, node, button.action(), historyItemScope);
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
				boolean historyTemplate =
					repeat.items() instanceof BindingValue binding
						&& binding.path().equals("history.items");
				validatePageNode(
					page,
					repeat.template(),
					nodesById,
					theme,
					historyTemplate
				);
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
			final UiAction action,
			final boolean historyItemScope
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
						this.playerActions,
						PlayerActionDefinition::game,
						"player action"
					)
				);
			if (
				action.type() == ActionType.HISTORY_DETAIL
					&& !historyItemScope
			) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"INVALID_CONSTRAINT",
					node.pointer() + "/action",
					"history_detail requires a Repeat template bound to history.items"
				);
			}
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
			} else if (text instanceof DurationTicksText duration) {
				UiValueType valueType = validateValue(
					page,
					duration.value(),
					nodesById,
					pointer + "/duration_ticks"
				);
				if (valueType != UiValueType.UNKNOWN && valueType != UiValueType.INTEGER) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"TYPE_MISMATCH",
						pointer + "/duration_ticks",
						"duration_ticks text requires an integer tick value"
					);
				}
				duration.fallback()
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
			if (path.startsWith("personal_meta/")) {
				String suffix = path.substring("personal_meta/".length());
				int propertySeparator = suffix.lastIndexOf('/');
				String property = propertySeparator <= 0
					? ""
					: suffix.substring(propertySeparator + 1);
				if (
					propertySeparator <= 0
						|| (!property.equals("name") && !property.equals("value_name"))
				) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"UNKNOWN_BINDING",
						pointer,
						"personal metadata exposes only the /name and /value_name properties"
					);
					return UiValueType.UNKNOWN;
				}
				String rawId = suffix.substring(0, propertySeparator);
				PlayerDataDefinition data = requireTerminalPlayerData(page, rawId, pointer);
				if (data == null) {
					return UiValueType.UNKNOWN;
				}
				if (property.equals("name") && data.name().isEmpty()) {
					add(
						origin(DefinitionType.PAGE, page.id()),
						"MISSING_REFERENCE",
						pointer,
						"player data " + data.id() + " does not declare a name"
					);
					return UiValueType.UNKNOWN;
				}
				if (property.equals("value_name")) {
					if (data.source().type() != PlayerDataSourceType.EXCLUSIVE_CHOICE) {
						add(
							origin(DefinitionType.PAGE, page.id()),
							"TYPE_MISMATCH",
							pointer,
							"player data "
								+ data.id()
								+ " must use an exclusive_choice source to expose value_name"
						);
						return UiValueType.UNKNOWN;
					}
					Identifier fieldId = data.source().id().orElse(null);
					FieldDefinition field = fieldId == null ? null : this.fields.get(fieldId);
					if (field == null) {
						add(
							origin(DefinitionType.PAGE, page.id()),
							"MISSING_REFERENCE",
							pointer,
							"player data "
								+ data.id()
								+ " does not reference a registered exclusive-choice field"
						);
						return UiValueType.UNKNOWN;
					}
					if (field.type() != FieldType.EXCLUSIVE_CHOICE) {
						add(
							origin(DefinitionType.PAGE, page.id()),
							"TYPE_MISMATCH",
							pointer,
							"player data "
								+ data.id()
								+ " does not reference an exclusive-choice field"
						);
						return UiValueType.UNKNOWN;
					}
				}
				return UiValueType.STRING;
			}
			if (path.startsWith("personal/")) {
				String rawId = path.substring("personal/".length());
				PlayerDataDefinition data = requireTerminalPlayerData(page, rawId, pointer);
				if (data == null) {
					return UiValueType.UNKNOWN;
				}
				return playerDataValueType(data);
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
			UiValueType fixed = fixedBindingType(path);
			if (fixed == UiValueType.UNKNOWN) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"UNKNOWN_BINDING",
					pointer,
					"unknown or unauthorized binding " + path
				);
			}
			return fixed;
		}

		private PlayerDataDefinition requireTerminalPlayerData(
			final PageDefinition page,
			final String rawId,
			final String pointer
		) {
			Identifier id = parseIdentifier(rawId);
			PlayerDataDefinition data = id == null ? null : this.playerData.get(id);
			if (data == null) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"MISSING_REFERENCE",
					pointer,
					"player data " + rawId + " does not exist"
				);
				return null;
			}
			if (!page.game().equals(data.game())) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"CROSS_GAME_REFERENCE",
					pointer,
					"player data " + id + " belongs to " + data.game()
				);
				return null;
			}
			if (!data.surfaces().contains(PlayerSurface.TERMINAL)) {
				add(
					origin(DefinitionType.PAGE, page.id()),
					"INVALID_CONSTRAINT",
					pointer,
					"player data " + id + " is not authorized for the terminal surface"
				);
				return null;
			}
			return data;
		}

		private UiValueType playerDataValueType(final PlayerDataDefinition data) {
			return switch (data.source().type()) {
				case INITIALIZED, READY -> UiValueType.BOOLEAN;
				case ROLE,
					TEAM,
					LIFE_STATE,
					TASK_ID -> UiValueType.IDENTIFIER;
				case TASK_ELAPSED_TICKS,
					TASK_REMAINING_TICKS,
					GAME_ELAPSED_TICKS,
					TASK_PROGRESS -> UiValueType.INTEGER;
				case FIELD, EXCLUSIVE_CHOICE -> data.source()
					.id()
					.map(this.fields::get)
					.map(FieldDefinition::type)
					.map(Compiler::fieldValueType)
					.orElse(UiValueType.UNKNOWN);
				case TASK_STATISTIC -> playerStatisticValueType(data);
				case TASK_NAME,
					TASK_DESCRIPTION,
					TASK_STATUS,
					TASK_RESULT -> UiValueType.STRING;
			};
		}

		private UiValueType playerStatisticValueType(final PlayerDataDefinition data) {
			Identifier taskId = data.source().id().orElse(null);
			String key = data.source().key().orElse(null);
			TaskDefinition task = taskId == null ? null : this.tasks.get(taskId);
			if (task == null || key == null) {
				return UiValueType.UNKNOWN;
			}
			TaskStatisticDefinition statistic = task.statistics()
				.stream()
				.filter(value -> value.id().equals(key))
				.findFirst()
				.orElse(null);
			if (statistic == null) {
				return UiValueType.UNKNOWN;
			}
			return switch (statistic.type()) {
				case BOOLEAN -> UiValueType.BOOLEAN;
				case INTEGER, DURATION_TICKS -> UiValueType.INTEGER;
				case IDENTIFIER, PLAYER -> UiValueType.IDENTIFIER;
				case STRING -> UiValueType.STRING;
			};
		}

		private static UiValueType fixedBindingType(final String path) {
			return switch (path) {
				case "viewer.online",
					"viewer.admin",
					"viewer.host",
					"viewer.ready",
					"viewer.initialized",
					"session.connected",
					"terminal.connected",
					"terminal.loading",
					"terminal.history_enabled",
					"task.exists",
					"task.paused",
					"history.enabled",
					"history.empty",
					"detail.exists",
					"detail.detail_available",
					"detail.replay_allowed",
					"item.online",
					"item.admin",
					"item.host",
					"item.ready",
					"item.initialized",
					"item.detail_available",
					"item.replay_allowed" -> UiValueType.BOOLEAN;
				case "session.revision",
					"session.generation",
					"flow.version",
					"flow.completed",
					"flow.total",
					"flow.remaining",
					"flow.percent",
					"task.elapsed_ticks",
					"task.remaining_ticks",
					"task.duration_ticks",
					"task.progress",
					"task.progress_min",
					"task.progress_max",
					"history.count",
					"detail.event_count",
					"detail.game_time",
					"detail.task_time",
					"detail.game_time_ticks",
					"detail.task_time_ticks",
					"item.event_count",
					"item.game_time",
					"item.task_time",
					"item.game_time_ticks",
					"item.task_time_ticks" -> UiValueType.INTEGER;
				case "viewer.role",
					"viewer.team",
					"viewer.life_state",
					"session.game",
					"session.phase",
					"flow.id",
					"flow.flowId",
					"terminal.page",
					"terminal.route",
					"task.id",
					"detail.icon",
					"detail.task",
					"item.icon",
					"item.task",
					"item.role",
					"item.team",
					"item.life_state" -> UiValueType.IDENTIFIER;
				case "session.players",
					"flow.members",
					"task.statistics",
					"history.items",
					"detail.targets",
					"detail.statistics",
					"detail.metadata",
					"item.targets",
					"item.statistics" -> UiValueType.COLLECTION;
				case "viewer.name",
					"viewer.uuid",
					"viewer.role_name",
					"viewer.team_name",
					"viewer.life_state_name",
					"session.game_name",
					"session.phase_name",
					"flow.flowNameJson",
					"flow.initiatedBy",
					"flow.node",
					"flow.status",
					"terminal.status",
					"task.name",
					"task.description",
					"task.status",
					"task.result",
					"history.source",
					"detail.status",
					"detail.id",
					"detail.title",
					"detail.summary",
					"detail.source",
					"detail.source_name",
					"detail.category",
					"detail.color",
					"detail.game_time_text",
					"detail.task_time_text",
					"detail.task_name",
					"detail.actor.uuid",
					"detail.actor.name",
					"detail.result",
					"detail.details",
					"item.id",
					"item.title",
					"item.summary",
					"item.source",
					"item.source_name",
					"item.category",
					"item.color",
					"item.game_time_text",
					"item.task_time_text",
					"item.task_name",
					"item.actor.uuid",
					"item.actor.name",
					"item.result",
					"item.details",
					"item.uuid",
					"item.name",
					"item.label",
					"item.value",
					"item.role_name",
					"item.team_name",
					"item.life_state_name" -> UiValueType.STRING;
				default -> UiValueType.UNKNOWN;
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
