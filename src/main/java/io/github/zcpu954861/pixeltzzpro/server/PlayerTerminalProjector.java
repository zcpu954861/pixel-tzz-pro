package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.HistoryRelease;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.HistorySource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSourceType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerHistoryPresentation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerSurface;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenResult;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContextDocument;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TickTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Builds the least-privilege binding document for one ordinary player-terminal session.
 *
 * <p>This class is intentionally read-only. It never returns a raw player field, task fact, or
 * history record merely because that value exists in world state. Personal and task values require
 * a matching {@code player_data} definition with the {@link PlayerSurface#TERMINAL} surface, while
 * history additionally requires both the game switch and one event-level player-history policy.
 *
 * <p>An active timeline is restored through {@link TimelineSnapshotCompiler}; task labels, history
 * policies, and player-data grants therefore remain pinned to the generation approved for that
 * game instance. A malformed or unavailable frozen snapshot closes the protected roots instead of
 * falling back to a newer live definition.
 */
public final class PlayerTerminalProjector {
	private static final Identifier TERMINAL_CONTEXT_ID =
		Identifier.fromNamespaceAndPath("pixel-tzz-pro", "player_terminal");
	private static final int MAX_VISIBLE_TEXT_LENGTH = 2_048;
	private static final int MAX_HISTORY_ITEMS = 64;
	private static final int MAX_PERSONAL_BINDINGS = 256;
	private static final String HISTORY_RECORD_KEY_DOMAIN =
		"pixel-tzz-pro/history-record/v1";
	private static final String HISTORY_TASK_RECORD_KEY_DOMAIN =
		"pixel-tzz-pro/history-task/v1";
	private static final String HISTORY_MESSAGE_RECORD_KEY_DOMAIN =
		"pixel-tzz-pro/history-message/v1";
	/*
	 * Keep a meaningful reserve for the fixed roots and JSON escaping. The exact wire form is still
	 * checked by BindingContextDocument.encode before it leaves this class.
	 */
	private static final int OPTIONAL_ROOT_BUDGET_BYTES = 48 * 1_024;

	private PlayerTerminalProjector() {
	}

	public static Projection project(
		final MinecraftServer server,
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV4 state,
		final ServerPlayer viewer,
		final SessionMetadata session
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		ViewerSnapshot viewerSnapshot = new ViewerSnapshot(
			viewer.getUUID(),
			viewer.getPlainTextName(),
			true,
			viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
		);
		return projectDetached(
			liveDefinitions,
			state,
			viewerSnapshot,
			session,
			productionPredicates(server, liveDefinitions, state.core(), session)
		);
	}

	public static Projection project(
		final MinecraftServer server,
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final ServerPlayer viewer,
		final SessionMetadata session
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		ViewerSnapshot viewerSnapshot = new ViewerSnapshot(
			viewer.getUUID(),
			viewer.getPlainTextName(),
			true,
			viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
		);
		return projectDetached(
			liveDefinitions,
			state,
			viewerSnapshot,
			session,
			productionPredicates(server, liveDefinitions, state, session)
		);
	}

	/**
	 * Pure projection entry used by the runnable self-check.
	 *
	 * <p>The production overload above is the public server API. Keeping this package-private seam
	 * makes authorization, frozen-definition, and byte-boundary behavior testable without starting
	 * a Minecraft client or integrated server.
	 */
	static Projection projectDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV4 state,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates
	) {
		Objects.requireNonNull(state, "state");
		return projectDetached(
			liveDefinitions,
			state.core(),
			state.messageHistory(),
			viewer,
			session,
			predicates
		);
	}

	static Projection projectDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates
	) {
		return projectDetached(
			liveDefinitions,
			state,
			List.of(),
			viewer,
			session,
			predicates
		);
	}

	private static Projection projectDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final List<MessageHistoryRecord> messageHistory,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates
	) {
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(messageHistory, "messageHistory");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(predicates, "predicates");
		try {
			return projectChecked(
				liveDefinitions,
				state,
				messageHistory,
				viewer,
				session,
				predicates
			);
		} catch (RuntimeException | StackOverflowError error) {
			return closedProjection(state, viewer, session, "projection_failed");
		}
	}

	private static Projection projectChecked(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final List<MessageHistoryRecord> messageHistory,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates
	) {
		WorldStateV2 core = state.core();
		PlayerRecord player = core.players().get(viewer.playerId());
		Optional<UUID> hostId = core.host().map(WorldStateV2.HostRecord::playerId);
		if (hostId.filter(viewer.playerId()::equals).isPresent()) {
			return closedProjection(state, viewer, session, "host_terminal_forbidden");
		}
		if (player == null || core.activeGameId().isEmpty() || core.activePhaseId().isEmpty()) {
			return closedProjection(state, viewer, session, "player_context_unavailable");
		}

		Identifier gameId = core.activeGameId().orElseThrow();
		DefinitionSelection selected = selectDefinitions(liveDefinitions, state, gameId);
		if (selected.definitions().isEmpty()) {
			return closedProjection(state, viewer, session, selected.status());
		}
		DefinitionSnapshot definitions = selected.definitions().orElseThrow();
		GameDefinition game = definitions.games().get(gameId);
		if (
			game == null
				|| game.playerTerminal().isEmpty()
				|| session.definitionGeneration() != definitions.generation()
		) {
			return closedProjection(state, viewer, session, "terminal_generation_stale");
		}

		ProjectionContext context = new ProjectionContext(
			definitions,
			state,
			core,
			game,
			core.activePhaseId().orElseThrow(),
			player,
			hostId,
			viewer,
			session,
			predicates,
			currentTaskState(state.timeline()),
			messageHistory
		);
		JsonObject root = baseDocument(context);
		OptionalRootsProjection optionalRoots = projectOptionalRoots(context);
		TaskProjection task = optionalRoots.task();
		JsonObject personal = optionalRoots.personal();
		JsonObject personalMetadata = optionalRoots.personalMetadata();
		DetailProjection detail = optionalRoots.detail();
		HistoryProjection history = optionalRoots.history();
		root.add("task", task.document());
		root.add("history", history.document());
		root.add("detail", detail.document());
		root.add("personal", personal);
		root.add("personal_meta", personalMetadata);

		byte[] encoded;
		try {
			encoded = BindingContextDocument.encode(root);
		} catch (IllegalArgumentException tooLargeOrInvalid) {
			return closedProjection(state, viewer, session, "binding_limit");
		}
		return new Projection(
			encoded,
			new ProjectionSummary(
				"ok",
				sha256(encoded),
				encoded.length,
				definitions.generation(),
				core.stateRevision(),
				Optional.of(gameId),
				Optional.of(context.phaseId()),
				Optional.of(session.pageId()),
				context.currentTask().map(TaskInstance::taskId),
				personal.size(),
				history.visibleItems(),
				task.truncated()
					|| history.truncated()
					|| detail.truncated()
					|| optionalRoots.budgetTruncated()
			)
		);
	}

	public static Optional<HistoryDetailTarget> resolveHistoryDetail(
		final MinecraftServer server,
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV4 state,
		final ServerPlayer viewer,
		final SessionMetadata session,
		final String recordKey
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(recordKey, "recordKey");
		ViewerSnapshot viewerSnapshot = new ViewerSnapshot(
			viewer.getUUID(),
			viewer.getPlainTextName(),
			true,
			viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
		);
		return resolveHistoryDetailDetached(
			liveDefinitions,
			state,
			viewerSnapshot,
			session,
			productionPredicates(server, liveDefinitions, state.core(), session),
			recordKey
		);
	}

	public static Optional<HistoryDetailTarget> resolveHistoryDetail(
		final MinecraftServer server,
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final ServerPlayer viewer,
		final SessionMetadata session,
		final String recordKey
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(recordKey, "recordKey");
		ViewerSnapshot viewerSnapshot = new ViewerSnapshot(
			viewer.getUUID(),
			viewer.getPlainTextName(),
			true,
			viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
		);
		return resolveHistoryDetailDetached(
			liveDefinitions,
			state,
			viewerSnapshot,
			session,
			productionPredicates(server, liveDefinitions, state, session),
			recordKey
		);
	}

	static Optional<HistoryDetailTarget> resolveHistoryDetailDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV4 state,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates,
		final String recordKey
	) {
		Objects.requireNonNull(state, "state");
		return resolveHistoryDetailDetached(
			liveDefinitions,
			state.core(),
			state.messageHistory(),
			viewer,
			session,
			predicates,
			recordKey
		);
	}

	/**
	 * Resolves one message-history replay without exposing a cue, page, or record identifier to the
	 * client as authority.
	 *
	 * <p>The existing detail resolver first proves that the opaque key still belongs to a record
	 * visible in this exact terminal session. Only then is the viewer-scoped persisted message
	 * record recovered, and records without the explicit replay opt-in remain unavailable.
	 */
	public static Optional<MessageHistoryRecord> resolveMessageHistoryReplay(
		final MinecraftServer server,
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV4 state,
		final ServerPlayer viewer,
		final SessionMetadata session,
		final String recordKey
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(recordKey, "recordKey");
		ViewerSnapshot viewerSnapshot = new ViewerSnapshot(
			viewer.getUUID(),
			viewer.getPlainTextName(),
			true,
			viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
		);
		return resolveMessageHistoryReplayDetached(
			liveDefinitions,
			state,
			viewerSnapshot,
			session,
			productionPredicates(server, liveDefinitions, state.core(), session),
			recordKey
		);
	}

	static Optional<MessageHistoryRecord> resolveMessageHistoryReplayDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV4 state,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates,
		final String recordKey
	) {
		Objects.requireNonNull(state, "state");
		if (
			resolveHistoryDetailDetached(
				liveDefinitions,
				state,
				viewer,
				session,
				predicates,
				recordKey
			).isEmpty()
		) {
			return Optional.empty();
		}
		UUID activeGameInstance = state.core().activeGameInstanceId().orElse(null);
		if (activeGameInstance == null) {
			return Optional.empty();
		}
		return state.messageHistoryFor(viewer.playerId())
			.stream()
			.filter(MessageHistoryRecord::replayAllowed)
			.filter(record -> record.gameInstanceId().equals(activeGameInstance))
			.filter(record ->
				historyRecordKey(HistoryViewCandidate.message(record)).equals(recordKey)
			)
			.findFirst();
	}

	static Optional<HistoryDetailTarget> resolveHistoryDetailDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates,
		final String recordKey
	) {
		return resolveHistoryDetailDetached(
			liveDefinitions,
			state,
			List.of(),
			viewer,
			session,
			predicates,
			recordKey
		);
	}

	private static Optional<HistoryDetailTarget> resolveHistoryDetailDetached(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final List<MessageHistoryRecord> messageHistory,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final PredicateGate predicates,
		final String recordKey
	) {
		Objects.requireNonNull(liveDefinitions, "liveDefinitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(messageHistory, "messageHistory");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(predicates, "predicates");
		Objects.requireNonNull(recordKey, "recordKey");
		if (
			!recordKey.matches("h1_[0-9a-f]{64}")
				|| recordKey.length() > PlayerTerminalSessions.MAX_HISTORY_RECORD_KEY_LENGTH
		) {
			return Optional.empty();
		}
		try {
			WorldStateV2 core = state.core();
			PlayerRecord player = core.players().get(viewer.playerId());
			Optional<UUID> hostId = core.host().map(WorldStateV2.HostRecord::playerId);
			if (
				player == null
					|| hostId.filter(viewer.playerId()::equals).isPresent()
					|| core.activeGameId().isEmpty()
					|| core.activePhaseId().isEmpty()
			) {
				return Optional.empty();
			}
			Identifier gameId = core.activeGameId().orElseThrow();
			DefinitionSelection selected = selectDefinitions(
				liveDefinitions,
				state,
				gameId
			);
			if (selected.definitions().isEmpty()) {
				return Optional.empty();
			}
			DefinitionSnapshot definitions = selected.definitions().orElseThrow();
			GameDefinition game = definitions.games().get(gameId);
			if (
				game == null
					|| game.playerTerminal().isEmpty()
					|| !game.playerTerminal().orElseThrow().historyEnabled()
					|| session.definitionGeneration() != definitions.generation()
			) {
				return Optional.empty();
			}
			ProjectionContext context = new ProjectionContext(
				definitions,
				state,
				core,
				game,
				core.activePhaseId().orElseThrow(),
				player,
				hostId,
				viewer,
				session,
				predicates,
				currentTaskState(state.timeline()),
				messageHistory
			);
			return projectOptionalRoots(context)
				.history()
				.visibleCandidates()
				.stream()
				.filter(candidate -> historyRecordKey(candidate).equals(recordKey))
				.findFirst()
				.flatMap(candidate -> historyDetailPage(context, candidate))
				.map(pageId -> new HistoryDetailTarget(recordKey, pageId));
		} catch (RuntimeException | StackOverflowError ignored) {
			return Optional.empty();
		}
	}

	private static OptionalRootsProjection projectOptionalRoots(
		final ProjectionContext context
	) {
		ByteBudget optionalBudget = new ByteBudget(OPTIONAL_ROOT_BUDGET_BYTES);
		TaskProjection task;
		PersonalProjection personalProjection;
		JsonObject personalMetadata;
		HistoryBundle historyBundle;
		if (currentPageUsesHistory(context)) {
			/*
			 * A data-heavy personal/task projection must not make the history page internally
			 * inconsistent. Reserve its list and same-page detail first; ordinary task/profile
			 * pages keep the established task -> personal -> history priority below.
			 */
			historyBundle = projectHistory(context, optionalBudget);
			task = projectTask(context, optionalBudget);
			personalProjection = projectPersonal(context, optionalBudget);
			personalMetadata = projectPersonalMetadata(
				context,
				personalProjection.projectedDefinitions(),
				optionalBudget
			);
		} else {
			task = projectTask(context, optionalBudget);
			personalProjection = projectPersonal(context, optionalBudget);
			personalMetadata = projectPersonalMetadata(
				context,
				personalProjection.projectedDefinitions(),
				optionalBudget
			);
			historyBundle = projectHistory(context, optionalBudget);
		}
		return new OptionalRootsProjection(
			task,
			personalProjection.document(),
			personalMetadata,
			historyBundle.detail(),
			historyBundle.history(),
			optionalBudget.truncated()
		);
	}

	private static boolean currentPageUsesHistory(final ProjectionContext context) {
		PageDefinition page = context.definitions().pages().get(context.session().pageId());
		if (page == null) {
			return false;
		}
		try {
			return containsHistoryBinding(JsonParser.parseString(page.canonicalDocument()));
		} catch (RuntimeException ignored) {
			// Compiled pages are valid JSON; a corrupted frozen document keeps the old safe priority.
			return false;
		}
	}

	private static boolean containsHistoryBinding(final JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsHistoryBinding(child)) {
					return true;
				}
			}
			return false;
		}
		if (!element.isJsonObject()) {
			return false;
		}
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			JsonElement child = entry.getValue();
			if (
				entry.getKey().equals("bind")
					&& child.isJsonPrimitive()
					&& child.getAsJsonPrimitive().isString()
			) {
				String path = child.getAsString();
				if (
					path.equals("history")
						|| path.startsWith("history.")
						|| path.equals("detail")
						|| path.startsWith("detail.")
				) {
					return true;
				}
			}
			if (containsHistoryBinding(child)) {
				return true;
			}
		}
		return false;
	}

	private static DefinitionSelection selectDefinitions(
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final Identifier gameId
	) {
		TimelineInstance timeline = state.timeline().orElse(null);
		if (timeline == null) {
			return liveDefinitions.games().containsKey(gameId)
				? DefinitionSelection.available(liveDefinitions, "live")
				: DefinitionSelection.unavailable("game_definition_unavailable");
		}
		if (!timeline.gameId().equals(gameId)) {
			return DefinitionSelection.unavailable("timeline_game_mismatch");
		}
		TimelineSnapshotCompiler.RestoreResult restored = TimelineSnapshotCompiler.restore(
			gameId,
			timeline.snapshot()
		);
		return restored.success()
			? DefinitionSelection.available(restored.snapshot().orElseThrow(), "frozen")
			: DefinitionSelection.unavailable("frozen_snapshot_invalid");
	}

	private static JsonObject baseDocument(final ProjectionContext context) {
		JsonObject root = new JsonObject();
		JsonObject viewer = new JsonObject();
		viewer.addProperty("uuid", context.viewer().playerId().toString());
		viewer.addProperty("name", bounded(context.viewer().name()));
		viewer.addProperty("online", context.viewer().online());
		viewer.addProperty("admin", context.viewer().admin());
		viewer.addProperty(
			"host",
			context.hostId().filter(context.viewer().playerId()::equals).isPresent()
		);
		viewer.addProperty("initialized", initialized(context));
		viewer.addProperty("ready", ready(context));
		addAuthorizedViewerState(context, viewer);

		JsonObject session = new JsonObject();
		session.addProperty("game", context.game().id().toString());
		session.addProperty("game_name", bounded(context.game().name().plainText()));
		session.addProperty("phase", context.phaseId().toString());
		session.addProperty(
			"phase_name",
			Optional.ofNullable(context.definitions().phases().get(context.phaseId()))
				.map(PhaseDefinition::name)
				.map(RichText::plainText)
				.map(PlayerTerminalProjector::bounded)
				.orElse(context.phaseId().getPath())
		);
		session.addProperty("revision", context.core().stateRevision());
		session.addProperty("generation", context.definitions().generation());
		session.addProperty("connected", context.session().connected());
		session.addProperty("session_id", context.session().sessionId().toString());
		session.addProperty("page_instance_id", context.session().pageInstanceId().toString());
		session.addProperty("page", context.session().pageId().toString());
		session.addProperty(
			"route",
			context.session().routeId().map(Identifier::toString).orElse("")
		);

		root.add("viewer", viewer);
		root.add("session", session);
		root.add("flow", new JsonObject());
		root.add("fields", new JsonObject());
		root.add("flow_fields", new JsonObject());
		return root;
	}

	/**
	 * Adds shell-level identity labels only when an eligible terminal data grant authorizes the
	 * corresponding source.
	 *
	 * <p>A freshly registered player may already carry the game's default role internally. That
	 * implementation default must not be shown as a confirmed identity before initialization, so
	 * all role/team/life-state fields stay absent until initialization has completed. Pages and the
	 * fixed terminal header can therefore share the same authorized binding without creating an
	 * identity side channel.</p>
	 */
	private static void addAuthorizedViewerState(
		final ProjectionContext context,
		final JsonObject viewer
	) {
		if (!initialized(context)) {
			return;
		}
		List<PlayerDataDefinition> grants = eligibleData(context);
		if (hasSourceType(grants, PlayerDataSourceType.ROLE)) {
			Identifier roleId = context.player().roleId();
			viewer.addProperty("role", roleId.toString());
			viewer.addProperty(
				"role_name",
				Optional.ofNullable(context.definitions().roles().get(roleId))
					.map(RoleDefinition::name)
					.map(RichText::plainText)
					.map(PlayerTerminalProjector::bounded)
					.orElse(roleId.getPath())
			);
		}
		if (hasSourceType(grants, PlayerDataSourceType.TEAM)) {
			context.player().teamId().ifPresent(teamId -> {
				viewer.addProperty("team", teamId.toString());
				viewer.addProperty(
					"team_name",
					Optional.ofNullable(context.definitions().teams().get(teamId))
						.map(TeamDefinition::name)
						.map(RichText::plainText)
						.map(PlayerTerminalProjector::bounded)
						.orElse(teamId.getPath())
				);
			});
		}
		if (hasSourceType(grants, PlayerDataSourceType.LIFE_STATE)) {
			Identifier lifeStateId = context.player().lifeStateId();
			viewer.addProperty("life_state", lifeStateId.toString());
			viewer.addProperty(
				"life_state_name",
				Optional.ofNullable(context.definitions().lifeStates().get(lifeStateId))
					.map(LifeStateDefinition::name)
					.map(RichText::plainText)
					.map(PlayerTerminalProjector::bounded)
					.orElse(lifeStateId.getPath())
			);
		}
	}

	private static boolean hasSourceType(
		final List<PlayerDataDefinition> grants,
		final PlayerDataSourceType type
	) {
		return grants.stream().anyMatch(value -> value.source().type() == type);
	}

	private static TaskProjection projectTask(
		final ProjectionContext context,
		final ByteBudget budget
	) {
		JsonObject task = new JsonObject();
		TaskInstance current = context.currentTask().orElse(null);
		TaskDefinition definition = current == null
			? null
			: context.definitions().tasks().get(current.taskId());
		if (current == null || definition == null) {
			task.addProperty("exists", false);
			return new TaskProjection(task, false);
		}

		List<PlayerDataDefinition> grants = eligibleData(context);
		/*
		 * The existence of a hidden current task is itself protected information. A route may use
		 * the task server-side without granting any task field to this viewer, so task.exists must
		 * not become a side channel. At least one eligible task-source grant targeting the current
		 * task is required before the standard task root is opened.
		 */
		boolean visible = grants.stream()
			.map(PlayerDataDefinition::source)
			.anyMatch(source -> taskSourceTargetsCurrent(source, current.taskId()));
		task.addProperty("exists", visible);
		if (!visible) {
			return new TaskProjection(task, false);
		}
		boolean truncated = false;
		if (hasSource(grants, PlayerDataSourceType.TASK_ID, current.taskId(), null)) {
			task.addProperty("id", current.taskId().toString());
		}
		if (hasSource(grants, PlayerDataSourceType.TASK_NAME, current.taskId(), null)) {
			task.addProperty("name", bounded(definition.name().plainText()));
		}
		if (hasSource(grants, PlayerDataSourceType.TASK_DESCRIPTION, current.taskId(), null)) {
			definition.description()
				.map(RichText::plainText)
				.map(PlayerTerminalProjector::bounded)
				.ifPresent(value -> task.addProperty("description", value));
		}
		if (hasSource(grants, PlayerDataSourceType.TASK_STATUS, current.taskId(), null)) {
			task.addProperty("status", taskStatus(context, current));
			task.addProperty("paused", context.timeline().map(TimelineInstance::paused).orElse(false));
		}
		if (hasSource(grants, PlayerDataSourceType.TASK_ELAPSED_TICKS, current.taskId(), null)) {
			task.addProperty("elapsed_ticks", current.elapsedTicks());
		}
		if (hasSource(grants, PlayerDataSourceType.TASK_REMAINING_TICKS, current.taskId(), null)) {
			definition.durationTicks().ifPresent(duration -> {
				task.addProperty("duration_ticks", duration);
				task.addProperty("remaining_ticks", Math.max(0L, duration - current.elapsedTicks()));
			});
		}
		if (hasSource(grants, PlayerDataSourceType.GAME_ELAPSED_TICKS, current.taskId(), null)) {
			context.timeline()
				.ifPresent(value -> task.addProperty("game_elapsed_ticks", value.gameElapsedTicks()));
		}

		PlayerDataDefinition progressGrant = grants.stream()
			.filter(value -> sourceTargets(value.source(), PlayerDataSourceType.TASK_PROGRESS, current.taskId(), null))
			.sorted(Comparator.comparing(PlayerDataDefinition::id))
			.findFirst()
			.orElse(null);
		if (progressGrant != null) {
			progressGrant.source()
				.key()
				.flatMap(key -> numericStatistic(current, key))
				.ifPresent(value -> task.addProperty("progress", value));
		}

		if (hasSource(grants, PlayerDataSourceType.TASK_RESULT, current.taskId(), null)) {
			resultName(definition, current, Optional.empty())
				.ifPresent(value -> task.addProperty("result", bounded(value)));
		}

		JsonArray statistics = new JsonArray();
		Map<String, PlayerDataDefinition> statisticGrants = new LinkedHashMap<>();
		grants.stream()
			.filter(value -> value.source().type() == PlayerDataSourceType.TASK_STATISTIC)
			.filter(value -> value.source().id().filter(current.taskId()::equals).isPresent())
			.sorted(Comparator.comparing(PlayerDataDefinition::id))
			.forEach(value ->
				value.source().key().ifPresent(key -> statisticGrants.putIfAbsent(key, value))
			);
		for (TaskStatisticValue statistic : current.statistics()) {
			PlayerDataDefinition grant = statisticGrants.get(statistic.statisticId());
			if (grant == null) {
				continue;
			}
			JsonObject entry = statisticEntry(definition, statistic);
			if (entry == null || !budget.tryReserve(entry)) {
				truncated = true;
				continue;
			}
			statistics.add(entry);
		}
		task.add("statistics", statistics);
		return new TaskProjection(task, truncated);
	}

	private static boolean taskSourceTargetsCurrent(
		final PlayerDataSource source,
		final Identifier currentTaskId
	) {
		return switch (source.type()) {
			case TASK_ID,
				TASK_NAME,
				TASK_DESCRIPTION,
				TASK_STATUS,
				TASK_ELAPSED_TICKS,
				TASK_REMAINING_TICKS,
				TASK_PROGRESS,
				TASK_RESULT,
				TASK_STATISTIC -> source.id().map(currentTaskId::equals).orElse(true);
			case ROLE,
				TEAM,
				LIFE_STATE,
				INITIALIZED,
				READY,
				FIELD,
				EXCLUSIVE_CHOICE,
				GAME_ELAPSED_TICKS -> false;
		};
	}

	private static PersonalProjection projectPersonal(
		final ProjectionContext context,
		final ByteBudget budget
	) {
		JsonObject personal = new JsonObject();
		List<PlayerDataDefinition> projectedDefinitions = new ArrayList<>();
		int accepted = 0;
		for (PlayerDataDefinition data : eligibleData(context)) {
			if (accepted >= MAX_PERSONAL_BINDINGS) {
				budget.markTruncated();
				break;
			}
			Optional<JsonElement> raw = sourceValue(context, data.source());
			Optional<JsonElement> presented = presentValue(context, data, raw);
			if (presented.isEmpty()) {
				continue;
			}
			JsonElement value = presented.orElseThrow();
			if (!budget.tryReserve(data.id().toString(), value)) {
				continue;
			}
			personal.add(data.id().toString(), value);
			projectedDefinitions.add(data);
			accepted++;
		}
		return new PersonalProjection(personal, List.copyOf(projectedDefinitions));
	}

	private static JsonObject projectPersonalMetadata(
		final ProjectionContext context,
		final List<PlayerDataDefinition> projectedDefinitions,
		final ByteBudget budget
	) {
		JsonObject metadata = new JsonObject();
		for (PlayerDataDefinition data : projectedDefinitions) {
			String name = data.name().map(RichText::plainText).orElse(null);
			JsonObject entry = new JsonObject();
			if (
				name != null
					&& name.length()
						<= BindingContextDocument.MAX_PERSONAL_METADATA_NAME_LENGTH
			) {
				entry.addProperty("name", name);
			}
			exclusiveChoiceValueName(context, data.source())
				.filter(value ->
					value.length()
						<= BindingContextDocument.MAX_PERSONAL_METADATA_NAME_LENGTH
				)
				.ifPresent(value -> entry.addProperty("value_name", value));
			if (entry.isEmpty()) {
				continue;
			}
			if (!budget.tryReserve(data.id().toString(), entry)) {
				continue;
			}
			metadata.add(data.id().toString(), entry);
		}
		return metadata;
	}

	private static Optional<String> exclusiveChoiceValueName(
		final ProjectionContext context,
		final PlayerDataSource source
	) {
		if (source.type() != PlayerDataSourceType.EXCLUSIVE_CHOICE) {
			return Optional.empty();
		}
		Identifier fieldId = source.id().orElse(null);
		FieldDefinition field = fieldId == null
			? null
			: context.definitions().fields().get(fieldId);
		if (
			field == null
				|| field.type() != FieldType.EXCLUSIVE_CHOICE
				|| field.exclusiveChoice().isEmpty()
		) {
			return Optional.empty();
		}
		String selected = sourceValue(context, source)
			.filter(JsonElement::isJsonPrimitive)
			.map(JsonElement::getAsJsonPrimitive)
			.filter(JsonPrimitive::isString)
			.map(JsonPrimitive::getAsString)
			.orElse(null);
		if (selected == null) {
			return Optional.empty();
		}
		return field.exclusiveChoice()
			.orElseThrow()
			.options()
			.stream()
			.filter(option -> option.value().equals(selected))
			.findFirst()
			.map(option -> bounded(option.name().plainText()));
	}

	private static List<PlayerDataDefinition> eligibleData(final ProjectionContext context) {
		return context.definitions()
			.playerData()
			.values()
			.stream()
			.filter(value -> value.game().equals(context.game().id()))
			.filter(value -> value.surfaces().contains(PlayerSurface.TERMINAL))
			.filter(value -> conditionsMatch(context, value))
			.sorted(Comparator.comparing(PlayerDataDefinition::id))
			.toList();
	}

	private static boolean conditionsMatch(
		final ProjectionContext context,
		final PlayerDataDefinition data
	) {
		if (
			!AudienceMatcher.matches(
				context.definitions(),
				data.audience(),
				context.player(),
				context.hostId(),
				context.viewer().online()
			)
				|| (!data.phases().isEmpty() && !data.phases().contains(context.phaseId()))
				|| (
					!data.tasks().isEmpty()
						&& context.currentTask()
							.map(TaskInstance::taskId)
							.filter(data.tasks()::contains)
							.isEmpty()
				)
				|| (
					!data.taskStates().isEmpty()
						&& context.taskState().filter(data.taskStates()::contains).isEmpty()
				)
		) {
			return false;
		}
		return data.predicate()
			.map(value -> safePredicate(context, value))
			.orElse(true);
	}

	private static Optional<JsonElement> sourceValue(
		final ProjectionContext context,
		final PlayerDataSource source
	) {
		return switch (source.type()) {
			case ROLE -> Optional.of(new JsonPrimitive(context.player().roleId().toString()));
			case TEAM -> context.player()
				.teamId()
				.map(Identifier::toString)
				.map(JsonPrimitive::new);
			case LIFE_STATE -> Optional.of(
				new JsonPrimitive(context.player().lifeStateId().toString())
			);
			case INITIALIZED -> Optional.of(new JsonPrimitive(initialized(context)));
			case READY -> Optional.of(new JsonPrimitive(ready(context)));
			case FIELD -> source.id().flatMap(id -> persistentValue(context, id, false));
			case EXCLUSIVE_CHOICE -> source.id().flatMap(id -> persistentValue(context, id, true));
			case GAME_ELAPSED_TICKS -> context.timeline()
				.map(TimelineInstance::gameElapsedTicks)
				.map(JsonPrimitive::new);
			case TASK_ID,
				TASK_NAME,
				TASK_DESCRIPTION,
				TASK_STATUS,
				TASK_ELAPSED_TICKS,
				TASK_REMAINING_TICKS,
				TASK_PROGRESS,
				TASK_RESULT,
				TASK_STATISTIC -> taskSourceValue(context, source);
		};
	}

	private static Optional<JsonElement> persistentValue(
		final ProjectionContext context,
		final Identifier fieldId,
		final boolean requireExclusive
	) {
		FieldDefinition field = context.definitions().fields().get(fieldId);
		PersistentFieldValue persisted = context.player().persistentFields().get(fieldId);
		if (
			field == null
				|| persisted == null
				|| !field.game().equals(context.game().id())
				|| persisted.fieldVersion() != field.version()
				|| (requireExclusive && field.type() != FieldType.EXCLUSIVE_CHOICE)
		) {
			return Optional.empty();
		}
		if (
			requireExclusive
				&& context.state()
					.exclusiveReservations()
					.stream()
					.noneMatch(value ->
						value.gameId().equals(context.game().id())
							&& value.fieldId().equals(fieldId)
							&& value.ownerId().equals(context.player().playerId())
							&& value.status() == ReservationStatus.LOCKED
					)
		) {
			return Optional.empty();
		}
		return Optional.of(storedValueJson(persisted.value()));
	}

	private static Optional<JsonElement> taskSourceValue(
		final ProjectionContext context,
		final PlayerDataSource source
	) {
		TaskInstance task = findTask(context.timeline(), source.id()).orElse(null);
		if (task == null) {
			return Optional.empty();
		}
		TaskDefinition definition = context.definitions().tasks().get(task.taskId());
		if (definition == null) {
			return Optional.empty();
		}
		return switch (source.type()) {
			case TASK_ID -> Optional.of(new JsonPrimitive(task.taskId().toString()));
			case TASK_NAME -> Optional.of(new JsonPrimitive(bounded(definition.name().plainText())));
			case TASK_DESCRIPTION -> definition.description()
				.map(RichText::plainText)
				.map(PlayerTerminalProjector::bounded)
				.map(JsonPrimitive::new);
			case TASK_STATUS -> Optional.of(new JsonPrimitive(taskStatus(context, task)));
			case TASK_ELAPSED_TICKS -> Optional.of(new JsonPrimitive(task.elapsedTicks()));
			case TASK_REMAINING_TICKS -> definition.durationTicks().isPresent()
				? Optional.of(
					new JsonPrimitive(
						Math.max(0L, definition.durationTicks().orElseThrow() - task.elapsedTicks())
					)
				)
				: Optional.empty();
			case TASK_PROGRESS -> source.key()
				.flatMap(key -> numericStatistic(task, key))
				.map(JsonPrimitive::new);
			case TASK_RESULT -> resultName(definition, task, source.key())
				.map(PlayerTerminalProjector::bounded)
				.map(JsonPrimitive::new);
			case TASK_STATISTIC -> source.key()
				.flatMap(key -> statisticValue(task, key));
			case ROLE,
				TEAM,
				LIFE_STATE,
				INITIALIZED,
				READY,
				FIELD,
				EXCLUSIVE_CHOICE,
				GAME_ELAPSED_TICKS -> Optional.empty();
		};
	}

	private static Optional<JsonElement> presentValue(
		final ProjectionContext context,
		final PlayerDataDefinition definition,
		final Optional<JsonElement> raw
	) {
		boolean stringSource = stringSource(context.definitions(), definition.source());
		if (raw.isEmpty()) {
			return stringSource
				? definition.maskedFallback()
					.map(RichText::plainText)
					.map(PlayerTerminalProjector::bounded)
					.map(JsonPrimitive::new)
				: Optional.empty();
		}
		JsonElement value = raw.orElseThrow();
		if (!stringSource || definition.format().isEmpty()) {
			if (
				value.isJsonPrimitive()
					&& value.getAsJsonPrimitive().isString()
					&& value.getAsString().length() > MAX_VISIBLE_TEXT_LENGTH
			) {
				return Optional.empty();
			}
			return Optional.of(value);
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			return Optional.empty();
		}
		String format = definition.format().orElseThrow();
		if (
			format.indexOf("{value}") < 0
				|| format.indexOf("{value}") != format.lastIndexOf("{value}")
		) {
			return Optional.empty();
		}
		String formatted = format.replace("{value}", value.getAsString());
		return formatted.length() <= MAX_VISIBLE_TEXT_LENGTH
			? Optional.of(new JsonPrimitive(formatted))
			: Optional.empty();
	}

	private static boolean stringSource(
		final DefinitionSnapshot definitions,
		final PlayerDataSource source
	) {
		return switch (source.type()) {
			case TASK_NAME, TASK_DESCRIPTION, TASK_STATUS, TASK_RESULT -> true;
			case FIELD, EXCLUSIVE_CHOICE -> source.id()
				.map(definitions.fields()::get)
				.map(FieldDefinition::type)
				.filter(value ->
					value == FieldType.STRING
						|| value == FieldType.SINGLE_CHOICE
						|| value == FieldType.EXCLUSIVE_CHOICE
				)
				.isPresent();
			default -> false;
		};
	}

	private static HistoryBundle projectHistory(
		final ProjectionContext context,
		final ByteBudget budget
	) {
		JsonObject history = new JsonObject();
		var terminal = context.game().playerTerminal().orElse(null);
		boolean enabled = terminal != null && terminal.historyEnabled();
		history.addProperty("enabled", enabled);
		history.addProperty(
			"source",
			terminal != null && terminal.historySource() == HistorySource.TASKS
				? "tasks"
				: "events"
		);
		JsonArray items = new JsonArray();
		if (!enabled || (context.timeline().isEmpty() && context.messageHistory().isEmpty())) {
			history.addProperty("empty", true);
			history.addProperty("count", 0);
			history.add("items", items);
			JsonObject detail = new JsonObject();
			detail.addProperty("exists", false);
			detail.addProperty("status", enabled ? "empty" : "disabled");
			return new HistoryBundle(
				new HistoryProjection(history, 0, false, List.of()),
				new DetailProjection(detail, false)
			);
		}

		List<HistoryViewCandidate> candidates = combinedHistoryCandidates(context);
		int from = Math.max(0, candidates.size() - MAX_HISTORY_ITEMS);
		List<HistoryViewCandidate> window = candidates.subList(from, candidates.size());
		HistoryDetailSelection detailSelection = selectHistoryDetail(context, window);
		List<ReservedHistoryItem> retained = new ArrayList<>();
		boolean truncated = candidates.size() > MAX_HISTORY_ITEMS;
		DetailProjection detail = closedHistoryDetail(detailSelection.status(), false);

		/*
		 * The selected/defaulted/recovered record is one logical master-detail unit. Reserve both
		 * copies before any other list entry so a visible left-side default cannot end up with an
		 * unavailable right side merely because earlier list rows consumed the shared budget.
		 */
		HistoryViewCandidate detailCandidate = detailSelection.candidate();
		if (detailCandidate != null) {
			int index = window.indexOf(detailCandidate);
			JsonObject item = historyItem(context, detailCandidate);
			JsonObject detailDocument = historyDetailDocument(
				item,
				detailSelection.status()
			);
			if (budget.tryReserveAll(item, detailDocument)) {
				retained.add(new ReservedHistoryItem(index, detailCandidate, item));
				detail = new DetailProjection(detailDocument, false);
			} else {
				truncated = true;
				detail = closedHistoryDetail("unavailable", true);
			}
		}

		// Compete newest-first, then restore the existing chronological wire order below.
		for (int index = window.size() - 1; index >= 0; index--) {
			HistoryViewCandidate candidate = window.get(index);
			if (candidate == detailCandidate) {
				continue;
			}
			JsonObject item = historyItem(context, candidate);
			if (!budget.tryReserve(item)) {
				truncated = true;
				continue;
			}
			retained.add(new ReservedHistoryItem(index, candidate, item));
		}
		retained.sort(Comparator.comparingInt(ReservedHistoryItem::index));
		List<HistoryViewCandidate> visibleCandidates = new ArrayList<>(retained.size());
		for (ReservedHistoryItem reserved : retained) {
			items.add(reserved.item());
			visibleCandidates.add(reserved.candidate());
		}
		history.addProperty("empty", items.isEmpty());
		history.addProperty("count", items.size());
		history.add("items", items);
		return new HistoryBundle(
			new HistoryProjection(
				history,
				items.size(),
				truncated,
				List.copyOf(visibleCandidates)
			),
			detail
		);
	}

	private static HistoryDetailSelection selectHistoryDetail(
		final ProjectionContext context,
		final List<HistoryViewCandidate> window
	) {
		String recordKey = context.session().historyRecordKey().orElse(null);
		List<HistoryViewCandidate> pageCandidates = window.stream()
			.filter(value ->
				historyDetailPage(context, value)
					.filter(context.session().pageId()::equals)
					.isPresent()
			)
			.toList();
		HistoryViewCandidate candidate = recordKey == null
			? null
			: pageCandidates.stream()
				.filter(value -> historyRecordKey(value).equals(recordKey))
				.findFirst()
				.orElse(null);
		String status = "selected";
		if (candidate == null && recordKey == null) {
			candidate = lastCandidate(pageCandidates).orElse(null);
			status = candidate == null ? "empty" : "defaulted";
		} else if (
			candidate == null
				&& context.session().historyFallbackAllowed()
		) {
			candidate = lastCandidate(pageCandidates).orElse(null);
			status = candidate == null ? "unavailable" : "recovered";
		} else if (candidate == null) {
			status = "unavailable";
		}
		return new HistoryDetailSelection(candidate, status);
	}

	private static JsonObject historyDetailDocument(
		final JsonObject item,
		final String status
	) {
		JsonObject detail = new JsonObject();
		detail.addProperty("exists", true);
		detail.addProperty("status", status);
		item.entrySet().forEach(
			entry -> detail.add(entry.getKey(), entry.getValue().deepCopy())
		);
		JsonArray metadata = historyMetadata(item);
		if (!metadata.isEmpty()) {
			detail.add("metadata", metadata);
		}
		return detail;
	}

	private static DetailProjection closedHistoryDetail(
		final String status,
		final boolean truncated
	) {
		JsonObject detail = new JsonObject();
		detail.addProperty("exists", false);
		detail.addProperty("status", status);
		return new DetailProjection(detail, truncated);
	}

	private static List<HistoryViewCandidate> combinedHistoryCandidates(
		final ProjectionContext context
	) {
		List<HistoryViewCandidate> result = new ArrayList<>();
		if (context.timeline().isPresent()) {
			historyCandidates(context).stream()
				.map(HistoryViewCandidate::taskOrEvent)
				.forEach(result::add);
		}
		Optional<UUID> activeScope = context.state().activeGameInstanceId();
		context.messageHistory().stream()
			.filter(value -> value.viewerId().equals(context.viewer().playerId()))
			.filter(value -> activeScope.filter(value.gameInstanceId()::equals).isPresent())
			.map(HistoryViewCandidate::message)
			.forEach(result::add);
		return result.stream()
			.sorted(
				Comparator.comparingLong(
					(HistoryViewCandidate value) -> historyGameTime(value)
				)
					.thenComparingInt(PlayerTerminalProjector::historySourceOrder)
					.thenComparingInt(PlayerTerminalProjector::historyStableOrder)
					.thenComparing(PlayerTerminalProjector::historyMessageStableKey)
			)
			.toList();
	}

	private static List<HistoryCandidate> historyCandidates(final ProjectionContext context) {
		HistorySource source = context.game()
			.playerTerminal()
			.map(value -> value.historySource())
			.orElse(HistorySource.EVENTS);
		return source == HistorySource.TASKS
			? historyTaskCandidates(context)
			: historyEventCandidates(context);
	}

	private static List<HistoryCandidate> historyEventCandidates(
		final ProjectionContext context
	) {
		TimelineInstance timeline = context.timeline().orElseThrow();
		List<TaskInstance> tasks = new ArrayList<>(timeline.taskHistory());
		timeline.currentTask().ifPresent(tasks::add);
		return historyEventCandidates(context, tasks);
	}

	private static List<HistoryCandidate> historyEventCandidates(
		final ProjectionContext context,
		final List<TaskInstance> tasks
	) {
		TimelineInstance timeline = context.timeline().orElseThrow();
		List<HistoryCandidate> result = new ArrayList<>();
		int stableOrder = 0;
		for (TaskInstance task : tasks) {
			TaskDefinition definition = context.definitions().tasks().get(task.taskId());
			if (definition == null) {
				continue;
			}
			Map<String, TaskEventDefinition> events = new LinkedHashMap<>();
			definition.events().forEach(value -> events.put(value.id(), value));
			for (TaskEventRecord record : task.events()) {
				int recordOrder = stableOrder++;
				TaskEventDefinition event = events.get(record.eventId());
				PlayerHistoryPresentation presentation = event == null
					? null
					: event.playerHistory().orElse(null);
				if (
					presentation == null
						|| !released(timeline, task, presentation.release())
						|| !AudienceMatcher.matches(
							context.definitions(),
							presentation.audience(),
							context.player(),
							context.hostId(),
							context.viewer().online()
						)
				) {
					continue;
				}
				result.add(
					new HistoryCandidate(
						task,
						definition,
						record,
						event,
						presentation,
						recordOrder,
						HistoryCandidateKind.EVENT,
						1
					)
				);
			}
		}
		return result.stream()
			.sorted(
				Comparator.comparingLong(
					(HistoryCandidate value) -> value.record().gameElapsedTicks()
				).thenComparingInt(HistoryCandidate::stableOrder)
			)
			.toList();
	}

	private static List<HistoryCandidate> historyTaskCandidates(
		final ProjectionContext context
	) {
		TimelineInstance timeline = context.timeline().orElseThrow();
		List<TaskInstance> completedTasks = List.copyOf(timeline.taskHistory());
		Map<UUID, List<HistoryCandidate>> visibleByTask = new LinkedHashMap<>();
		historyEventCandidates(context, completedTasks).stream()
			.filter(candidate -> candidate.presentation().showTask())
			.forEach(candidate ->
				visibleByTask.computeIfAbsent(
					candidate.task().taskInstanceId(),
					ignored -> new ArrayList<>()
				).add(candidate)
			);
		List<HistoryCandidate> result = new ArrayList<>();
		for (TaskInstance task : completedTasks) {
			List<HistoryCandidate> visible = visibleByTask.get(task.taskInstanceId());
			if (visible == null || visible.isEmpty()) {
				continue;
			}
			HistoryCandidate representative = visible.reversed()
				.stream()
				.filter(candidate -> candidate.presentation().detailPage().isPresent())
				.findFirst()
				.orElse(visible.getLast());
			result.add(
				new HistoryCandidate(
					representative.task(),
					representative.definition(),
					representative.record(),
					representative.event(),
					representative.presentation(),
					representative.stableOrder(),
					HistoryCandidateKind.TASK,
					visible.size()
				)
			);
		}
		return result.stream()
			.sorted(
				Comparator.comparingLong(
					(HistoryCandidate value) -> historyGameTime(value)
				).thenComparingInt(HistoryCandidate::stableOrder)
			)
			.toList();
	}

	private static JsonObject historyItem(
		final ProjectionContext context,
		final HistoryViewCandidate candidate
	) {
		return candidate.message() == null
			? historyItem(context, candidate.taskOrEvent())
			: messageHistoryItem(context, candidate.message());
	}

	private static JsonObject messageHistoryItem(
		final ProjectionContext context,
		final MessageHistoryRecord record
	) {
		JsonObject item = new JsonObject();
		item.addProperty("id", historyRecordKey(HistoryViewCandidate.message(record)));
		item.addProperty("source", "message");
		item.addProperty("source_name", "消息回顾");
		item.addProperty("title", bounded(record.title()));
		item.addProperty("summary", bounded(record.body()));
		item.addProperty("details", bounded(record.body()));
		item.addProperty("cue", record.cueId().toString());
		item.addProperty("message_instance", record.messageInstanceId().toString());
		item.addProperty("timestamp", record.timestampTicks());
		item.addProperty("timestamp_ticks", record.timestampTicks());
		item.addProperty("timestamp_text", formatHistoryTicks(record.timestampTicks()));
		// Existing history layouts bind these names. The selected V3B timestamp is already resolved
		// by the server, so this compatibility alias does not claim a new client-side clock source.
		item.addProperty("game_time", record.timestampTicks());
		item.addProperty("game_time_ticks", record.timestampTicks());
		item.addProperty("game_time_text", "发生于 " + formatHistoryTicks(record.timestampTicks()));
		record.taskId().ifPresent(taskId -> {
			item.addProperty("task", taskId.toString());
			TaskDefinition task = context.definitions().tasks().get(taskId);
			item.addProperty(
				"task_name",
				bounded(task == null ? taskId.toString() : task.name().plainText())
			);
		});
		JsonObject savedFields = new JsonObject();
		record.savedFields().forEach(
			(key, value) -> savedFields.addProperty(key, bounded(value))
		);
		item.add("saved_fields", savedFields);
		item.addProperty("saved_field_count", savedFields.size());
		item.addProperty("replay_allowed", record.replayAllowed());
		item.addProperty(
			"detail_available",
			historyDetailPage(context, HistoryViewCandidate.message(record)).isPresent()
		);
		return item;
	}

	private static JsonObject historyItem(
		final ProjectionContext context,
		final HistoryCandidate candidate
	) {
		PlayerHistoryPresentation presentation = candidate.presentation();
		JsonObject item = new JsonObject();
		item.addProperty(
			"id",
			historyRecordKey(candidate)
		);
		item.addProperty(
			"source",
			candidate.kind() == HistoryCandidateKind.TASK ? "task" : "event"
		);
		item.addProperty(
			"source_name",
			candidate.kind() == HistoryCandidateKind.TASK ? "任务回顾" : "事件记录"
		);
		item.addProperty(
			"title",
			bounded(
				candidate.kind() == HistoryCandidateKind.TASK
					? candidate.definition().name().plainText()
					: presentation.title().plainText()
			)
		);
		if (candidate.kind() == HistoryCandidateKind.TASK) {
			item.addProperty("event_count", candidate.eventCount());
		}
		presentation.summary()
			.map(RichText::plainText)
			.map(PlayerTerminalProjector::bounded)
			.ifPresent(value -> item.addProperty("summary", value));
		presentation.category()
			.map(PlayerTerminalProjector::bounded)
			.ifPresent(value -> item.addProperty("category", value));
		presentation.color()
			.map(PlayerTerminalProjector::bounded)
			.ifPresent(value -> item.addProperty("color", value));
		presentation.icon()
			.map(Identifier::toString)
			.ifPresent(value -> item.addProperty("icon", value));
		if (presentation.showGameTime()) {
			long gameTime = historyGameTime(candidate);
			long taskTime = historyTaskTime(candidate);
			item.addProperty("game_time", gameTime);
			item.addProperty("task_time", taskTime);
			item.addProperty("game_time_ticks", gameTime);
			item.addProperty("task_time_ticks", taskTime);
			item.addProperty(
				"game_time_text",
				"开局后 " + formatHistoryTicks(gameTime)
			);
			item.addProperty(
				"task_time_text",
				"任务开始后 " + formatHistoryTicks(taskTime)
			);
		}
		if (presentation.showTask()) {
			item.addProperty("task", candidate.task().taskId().toString());
			item.addProperty("task_name", bounded(candidate.definition().name().plainText()));
		}
		if (
			candidate.kind() == HistoryCandidateKind.EVENT
				&& presentation.showActor()
		) {
			candidate.record().player().ifPresent(value -> addPlayer(item, "actor", value));
		}
		if (
			candidate.kind() == HistoryCandidateKind.EVENT
				&& presentation.showTargets()
		) {
			// The current authoritative event record has no target payload. Never infer one.
			item.add("targets", new JsonArray());
		}
		if (!presentation.statistics().isEmpty()) {
			JsonArray statistics = new JsonArray();
			candidate.task()
				.statistics()
				.stream()
				.filter(value -> presentation.statistics().contains(value.statisticId()))
				.map(value -> statisticEntry(candidate.definition(), value))
				.filter(Objects::nonNull)
				.forEach(statistics::add);
			item.add("statistics", statistics);
		}
		if (
			presentation.showPersonalResult()
				&& candidate.task().participants().contains(context.player().playerId())
		) {
			resultName(candidate.definition(), candidate.task(), Optional.empty())
				.map(PlayerTerminalProjector::bounded)
				.ifPresent(value -> item.addProperty("result", value));
		}
		presentation.details()
			.map(RichText::plainText)
			.map(PlayerTerminalProjector::bounded)
			.ifPresent(value -> item.addProperty("details", value));
		item.addProperty(
			"detail_available",
			historyDetailPage(context, candidate).isPresent()
		);
		return item;
	}

	private static Optional<Identifier> historyDetailPage(
		final ProjectionContext context,
		final HistoryViewCandidate candidate
	) {
		if (candidate.message() == null) {
			return historyDetailPage(context, candidate.taskOrEvent());
		}
		PageDefinition page = context.definitions().pages().get(context.session().pageId());
		return page != null
			&& page.game().equals(context.game().id())
			&& currentPageUsesHistory(context)
			? Optional.of(context.session().pageId())
			: Optional.empty();
	}

	private static Optional<Identifier> historyDetailPage(
		final ProjectionContext context,
		final HistoryCandidate candidate
	) {
		return candidate.presentation()
			.detailPage()
			.filter(pageId -> {
				PageDefinition page = context.definitions().pages().get(pageId);
				return page != null && page.game().equals(context.game().id());
			});
	}

	private static Optional<HistoryViewCandidate> lastCandidate(
		final List<HistoryViewCandidate> candidates
	) {
		return candidates.isEmpty()
			? Optional.empty()
			: Optional.of(candidates.getLast());
	}

	private static String historyRecordKey(final HistoryCandidate candidate) {
		String material = candidate.kind() == HistoryCandidateKind.TASK
			? HISTORY_TASK_RECORD_KEY_DOMAIN
				+ "\0"
				+ candidate.task().taskInstanceId()
			: HISTORY_RECORD_KEY_DOMAIN
				+ "\0"
				+ candidate.task().taskInstanceId()
				+ "\0"
				+ candidate.record().eventId()
				+ "\0"
				+ candidate.stableOrder();
		return "h1_" + sha256(material.getBytes(StandardCharsets.UTF_8));
	}

	private static String historyRecordKey(final HistoryViewCandidate candidate) {
		if (candidate.message() == null) {
			return historyRecordKey(candidate.taskOrEvent());
		}
		MessageHistoryRecord record = candidate.message();
		String material = HISTORY_MESSAGE_RECORD_KEY_DOMAIN
			+ "\0"
			+ record.gameInstanceId()
			+ "\0"
			+ record.viewerId()
			+ "\0"
			+ record.cueId()
			+ "\0"
			+ record.messageInstanceId();
		return "h1_" + sha256(material.getBytes(StandardCharsets.UTF_8));
	}

	private static long historyGameTime(final HistoryViewCandidate candidate) {
		return candidate.message() == null
			? historyGameTime(candidate.taskOrEvent())
			: candidate.message().timestampTicks();
	}

	private static int historySourceOrder(final HistoryViewCandidate candidate) {
		return candidate.message() == null ? 0 : 1;
	}

	private static int historyStableOrder(final HistoryViewCandidate candidate) {
		return candidate.message() == null ? candidate.taskOrEvent().stableOrder() : 0;
	}

	private static String historyMessageStableKey(final HistoryViewCandidate candidate) {
		return candidate.message() == null
			? ""
			: candidate.message().cueId() + ":" + candidate.message().messageInstanceId();
	}

	private static long historyGameTime(final HistoryCandidate candidate) {
		return candidate.kind() == HistoryCandidateKind.TASK
			? candidate.task()
				.settledAtGameTick()
				.orElse(candidate.record().gameElapsedTicks())
			: candidate.record().gameElapsedTicks();
	}

	private static long historyTaskTime(final HistoryCandidate candidate) {
		return candidate.kind() == HistoryCandidateKind.TASK
			? candidate.task().elapsedTicks()
			: candidate.record().taskElapsedTicks();
	}

	static String formatHistoryTicks(final long ticks) {
		return TickTimeFormatter.format(ticks);
	}

	private static JsonArray historyMetadata(final JsonObject item) {
		JsonArray metadata = new JsonArray();
		addHistoryMetadata(metadata, "game_time", "发生时间", string(item, "game_time_text"));
		addHistoryMetadata(metadata, "task_time", "任务内时间", string(item, "task_time_text"));
		addHistoryMetadata(metadata, "task", "所属任务", string(item, "task_name"));
		if (item.has("event_count")) {
			addHistoryMetadata(
				metadata,
				"event_count",
				"公开记录",
				Optional.of(item.get("event_count").getAsInt() + " 条")
			);
		}
		addHistoryMetadata(metadata, "result", "本人结果", string(item, "result"));
		if (item.has("statistics") && item.get("statistics").isJsonArray()) {
			for (JsonElement value : item.getAsJsonArray("statistics")) {
				if (!value.isJsonObject()) {
					continue;
				}
				JsonObject statistic = value.getAsJsonObject();
				Optional<String> label = string(statistic, "name");
				Optional<String> presented = historyStatisticValue(statistic);
				if (label.isPresent() && presented.isPresent()) {
					addHistoryMetadata(
						metadata,
						"statistic_" + metadata.size(),
						label.orElseThrow(),
						presented
					);
				}
			}
		}
		return metadata;
	}

	private static void addHistoryMetadata(
		final JsonArray metadata,
		final String id,
		final String label,
		final Optional<String> value
	) {
		value.map(PlayerTerminalProjector::bounded)
			.filter(text -> !text.isBlank())
			.ifPresent(text -> {
				JsonObject entry = new JsonObject();
				entry.addProperty("id", id);
				entry.addProperty("label", bounded(label));
				entry.addProperty("value", text);
				metadata.add(entry);
			});
	}

	private static Optional<String> historyStatisticValue(final JsonObject statistic) {
		String type = string(statistic, "type").orElse("");
		if (type.equals(TaskStatisticValueType.PLAYER.getSerializedName())) {
			return string(statistic, "player_name");
		}
		JsonElement value = statistic.get("value");
		if (value == null || !value.isJsonPrimitive()) {
			return Optional.empty();
		}
		return switch (type) {
			case "boolean" -> Optional.of(value.getAsBoolean() ? "是" : "否");
			case "duration_ticks" -> Optional.of(formatHistoryTicks(value.getAsLong()));
			default -> Optional.of(value.getAsString());
		};
	}

	private static Optional<String> string(final JsonObject object, final String property) {
		JsonElement value = object.get(property);
		return value != null
				&& value.isJsonPrimitive()
				&& value.getAsJsonPrimitive().isString()
			? Optional.of(value.getAsString())
			: Optional.empty();
	}

	private static boolean released(
		final TimelineInstance timeline,
		final TaskInstance task,
		final HistoryRelease release
	) {
		return switch (release) {
			case IMMEDIATE -> true;
			case TASK_END -> task.status() == TaskStatus.SETTLED
				|| task.status() == TaskStatus.INTERRUPTED;
			case GAME_END -> {
				TimelineStatus status = timeline.status() == TimelineStatus.BLOCKED
					? timeline.resumeStatus().orElse(TimelineStatus.BLOCKED)
					: timeline.status();
				yield status == TimelineStatus.COMPLETED || status == TimelineStatus.INTERRUPTED;
			}
		};
	}

	private static boolean hasSource(
		final List<PlayerDataDefinition> grants,
		final PlayerDataSourceType type,
		final Identifier taskId,
		final String key
	) {
		return grants.stream().anyMatch(value -> sourceTargets(value.source(), type, taskId, key));
	}

	private static boolean sourceTargets(
		final PlayerDataSource source,
		final PlayerDataSourceType type,
		final Identifier taskId,
		final String key
	) {
		return source.type() == type
			&& source.id().map(taskId::equals).orElse(true)
			&& (key == null || source.key().filter(key::equals).isPresent());
	}

	private static Optional<TaskInstance> findTask(
		final Optional<TimelineInstance> timeline,
		final Optional<Identifier> requested
	) {
		if (timeline.isEmpty()) {
			return Optional.empty();
		}
		TimelineInstance value = timeline.orElseThrow();
		if (requested.isEmpty()) {
			return value.currentTask();
		}
		Identifier id = requested.orElseThrow();
		if (value.currentTask().map(TaskInstance::taskId).filter(id::equals).isPresent()) {
			return value.currentTask();
		}
		for (int index = value.taskHistory().size() - 1; index >= 0; index--) {
			TaskInstance candidate = value.taskHistory().get(index);
			if (candidate.taskId().equals(id)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private static Optional<Long> numericStatistic(
		final TaskInstance task,
		final String key
	) {
		return task.statistics()
			.stream()
			.filter(value -> value.statisticId().equals(key))
			.filter(value ->
				value.type() == TaskStatisticValueType.INTEGER
					|| value.type() == TaskStatisticValueType.DURATION_TICKS
			)
			.map(TaskStatisticValue::longValue)
			.flatMap(Optional::stream)
			.findFirst();
	}

	private static Optional<JsonElement> statisticValue(
		final TaskInstance task,
		final String key
	) {
		return task.statistics()
			.stream()
			.filter(value -> value.statisticId().equals(key))
			.findFirst()
			.flatMap(PlayerTerminalProjector::statisticJson);
	}

	private static Optional<JsonElement> statisticJson(final TaskStatisticValue value) {
		return switch (value.type()) {
			case BOOLEAN -> value.booleanValue().map(JsonPrimitive::new);
			case INTEGER, DURATION_TICKS -> value.longValue().map(JsonPrimitive::new);
			case STRING -> value.stringValue()
				.filter(text -> text.length() <= MAX_VISIBLE_TEXT_LENGTH)
				.map(JsonPrimitive::new);
			case IDENTIFIER -> value.identifierValue()
				.map(Identifier::toString)
				.map(JsonPrimitive::new);
			case PLAYER -> value.playerValue()
				.map(FrozenPlayerValue::playerId)
				.map(UUID::toString)
				.map(JsonPrimitive::new);
		};
	}

	private static JsonObject statisticEntry(
		final TaskDefinition task,
		final TaskStatisticValue value
	) {
		JsonElement payload = statisticJson(value).orElse(null);
		if (payload == null) {
			return null;
		}
		JsonObject result = new JsonObject();
		result.addProperty("id", value.statisticId());
		task.statistics()
			.stream()
			.filter(definition -> definition.id().equals(value.statisticId()))
			.findFirst()
			.map(definition -> definition.name().plainText())
			.map(PlayerTerminalProjector::bounded)
			.ifPresent(name -> result.addProperty("name", name));
		result.addProperty("type", value.type().getSerializedName());
		result.add("value", payload);
		value.playerValue().ifPresent(player -> {
			result.addProperty("player_uuid", player.playerId().toString());
			result.addProperty("player_name", bounded(player.playerName()));
		});
		return result;
	}

	private static Optional<String> resultName(
		final TaskDefinition definition,
		final TaskInstance task,
		final Optional<String> expected
	) {
		FrozenResult result = task.result().orElse(null);
		if (
			result == null
				|| expected.filter(value -> !value.equals(result.resultId())).isPresent()
		) {
			return Optional.empty();
		}
		return Optional.ofNullable(definition.results().get(result.resultId()))
			.map(TaskResult::name)
			.map(RichText::plainText);
	}

	private static String taskStatus(
		final ProjectionContext context,
		final TaskInstance task
	) {
		TimelineInstance timeline = context.timeline().orElse(null);
		if (
			timeline != null
				&& timeline.currentTask()
					.map(TaskInstance::taskInstanceId)
					.filter(task.taskInstanceId()::equals)
					.isPresent()
		) {
			if (timeline.status() == TimelineStatus.BLOCKED || task.status() == TaskStatus.BLOCKED) {
				return "blocked";
			}
			if (timeline.paused()) {
				return "paused";
			}
			if (timeline.status() == TimelineStatus.INTERMISSION) {
				return "intermission";
			}
		}
		return task.status().getSerializedName();
	}

	private static Optional<PlayerTaskState> currentTaskState(
		final Optional<TimelineInstance> timeline
	) {
		if (timeline.isEmpty()) {
			return Optional.empty();
		}
		TimelineInstance value = timeline.orElseThrow();
		if (value.status() == TimelineStatus.BLOCKED) {
			return Optional.of(PlayerTaskState.BLOCKED);
		}
		if (value.paused()) {
			return Optional.of(PlayerTaskState.PAUSED);
		}
		return switch (value.status()) {
			case PRE_START -> Optional.empty();
			case STARTING -> Optional.of(PlayerTaskState.STARTING);
			case RUNNING -> Optional.of(PlayerTaskState.RUNNING);
			case SETTLING -> Optional.of(PlayerTaskState.SETTLING);
			case INTERMISSION -> Optional.of(PlayerTaskState.INTERMISSION);
			case COMPLETED -> Optional.of(PlayerTaskState.COMPLETED);
			case INTERRUPTED -> Optional.of(PlayerTaskState.INTERRUPTED);
			case BLOCKED -> Optional.of(PlayerTaskState.BLOCKED);
		};
	}

	private static boolean initialized(final ProjectionContext context) {
		RoleDefinition role = context.definitions().roles().get(context.player().roleId());
		return role != null
			&& role.game().equals(context.game().id())
			&& TabListDisplay.hasCurrentIdentityCredential(
				context.player().playerId(),
				context.player().roleId(),
				role.initializationFlow(),
				role.initializationFlow()
					.map(context.definitions().flows()::get)
					.map(value -> value.version()),
				context.player().pendingRoleChange(),
				context.core().completionHistory()
			);
	}

	private static boolean ready(final ProjectionContext context) {
		return context.state()
			.readiness()
			.map(value -> value.flow().runtime().members().get(context.player().playerId()))
			.map(FlowMemberState::status)
			.filter(value ->
				value == FlowMemberStatus.COMPLETED
					|| value == FlowMemberStatus.ALREADY_COMPLETE
			)
			.isPresent();
	}

	private static boolean safePredicate(
		final ProjectionContext context,
		final Identifier predicate
	) {
		try {
			return context.predicates().test(predicate, context.player());
		} catch (RuntimeException | StackOverflowError error) {
			return false;
		}
	}

	private static PredicateGate productionPredicates(
		final MinecraftServer server,
		final DefinitionSnapshot liveDefinitions,
		final WorldStateV3 state,
		final SessionMetadata session
	) {
		Identifier gameId = state.core().activeGameId().orElse(null);
		Identifier phaseId = state.core().activePhaseId().orElse(null);
		if (gameId == null || phaseId == null) {
			return PredicateGate.denyAll();
		}
		DefinitionSelection selected = selectDefinitions(liveDefinitions, state, gameId);
		if (selected.definitions().isEmpty()) {
			return PredicateGate.denyAll();
		}
		DefinitionSnapshot definitions = selected.definitions().orElseThrow();
		Map<Identifier, String> documents = definitions.predicateDocuments();
		if (
			documents.isEmpty()
				&& definitions.generation() == liveDefinitions.generation()
		) {
			documents = liveDefinitions.predicateDocuments();
		}
		if (documents.isEmpty()) {
			return PredicateGate.denyAll();
		}
		FrozenPredicateEvaluator evaluator = new FrozenPredicateEvaluator(server, documents);
		UUID scopeId = state.activeGameInstanceId().orElse(session.sessionId());
		return (predicate, player) -> evaluator.evaluate(
			predicate,
			new ForcedFlowAuthority.PredicateContext(
				player.playerId(),
				scopeId,
				gameId,
				session.routeId().orElse(TERMINAL_CONTEXT_ID),
				1,
				phaseId,
				Map.of(),
				player.persistentFields()
			)
		);
	}

	private static JsonElement storedValueJson(final StoredValue value) {
		return switch (value.type()) {
			case BOOLEAN -> new JsonPrimitive(value.booleanValue().orElseThrow());
			case INTEGER -> new JsonPrimitive(value.integerValue().orElseThrow());
			case STRING, SINGLE_CHOICE -> new JsonPrimitive(value.stringValue().orElseThrow());
			case IDENTIFIER -> new JsonPrimitive(value.identifierValue().orElseThrow().toString());
			case MULTI_CHOICE -> {
				JsonArray result = new JsonArray();
				value.choices().forEach(result::add);
				yield result;
			}
		};
	}

	private static void addPlayer(
		final JsonObject object,
		final String key,
		final FrozenPlayerValue player
	) {
		JsonObject value = new JsonObject();
		value.addProperty("uuid", player.playerId().toString());
		value.addProperty("name", bounded(player.playerName()));
		object.add(key, value);
	}

	private static Projection closedProjection(
		final WorldStateV3 state,
		final ViewerSnapshot viewer,
		final SessionMetadata session,
		final String status
	) {
		JsonObject root = new JsonObject();
		JsonObject viewerRoot = new JsonObject();
		viewerRoot.addProperty("uuid", viewer.playerId().toString());
		viewerRoot.addProperty("name", bounded(viewer.name()));
		viewerRoot.addProperty("online", viewer.online());
		viewerRoot.addProperty("admin", viewer.admin());
		viewerRoot.addProperty("host", false);
		viewerRoot.addProperty("ready", false);
		viewerRoot.addProperty("initialized", false);
		JsonObject sessionRoot = new JsonObject();
		sessionRoot.addProperty("revision", state.stateRevision());
		sessionRoot.addProperty("generation", session.definitionGeneration());
		sessionRoot.addProperty("connected", session.connected());
		sessionRoot.addProperty("page", session.pageId().toString());
		sessionRoot.addProperty("route", session.routeId().map(Identifier::toString).orElse(""));
		JsonObject task = new JsonObject();
		task.addProperty("exists", false);
		JsonObject history = new JsonObject();
		history.addProperty("enabled", false);
		history.addProperty("empty", true);
		history.addProperty("count", 0);
		history.add("items", new JsonArray());
		root.add("viewer", viewerRoot);
		root.add("session", sessionRoot);
		root.add("flow", new JsonObject());
		root.add("task", task);
		root.add("history", history);
		JsonObject detail = new JsonObject();
		detail.addProperty("exists", false);
		root.add("detail", detail);
		root.add("fields", new JsonObject());
		root.add("flow_fields", new JsonObject());
		root.add("personal", new JsonObject());
		root.add("personal_meta", new JsonObject());
		byte[] encoded = BindingContextDocument.encode(root);
		return new Projection(
			encoded,
			new ProjectionSummary(
				status,
				sha256(encoded),
				encoded.length,
				session.definitionGeneration(),
				state.stateRevision(),
				state.core().activeGameId(),
				state.core().activePhaseId(),
				Optional.of(session.pageId()),
				Optional.empty(),
				0,
				0,
				false
			)
		);
	}

	private static String bounded(final String value) {
		Objects.requireNonNull(value, "value");
		return value.length() <= MAX_VISIBLE_TEXT_LENGTH
			? value
			: value.substring(0, MAX_VISIBLE_TEXT_LENGTH - 3) + "...";
	}

	private static String sha256(final byte[] value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(value)
			);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	public record SessionMetadata(
		UUID sessionId,
		UUID pageInstanceId,
		Identifier pageId,
		Optional<Identifier> routeId,
		long definitionGeneration,
		long openedAtStateRevision,
		boolean connected,
		Optional<String> historyRecordKey,
		boolean historyFallbackAllowed
	) {
		public SessionMetadata(
			final UUID sessionId,
			final UUID pageInstanceId,
			final Identifier pageId,
			final Optional<Identifier> routeId,
			final long definitionGeneration,
			final long openedAtStateRevision,
			final boolean connected
		) {
			this(
				sessionId,
				pageInstanceId,
				pageId,
				routeId,
				definitionGeneration,
				openedAtStateRevision,
				connected,
				Optional.empty(),
				false
			);
		}

		public SessionMetadata(
			final UUID sessionId,
			final UUID pageInstanceId,
			final Identifier pageId,
			final Optional<Identifier> routeId,
			final long definitionGeneration,
			final long openedAtStateRevision,
			final boolean connected,
			final Optional<String> historyRecordKey
		) {
			this(
				sessionId,
				pageInstanceId,
				pageId,
				routeId,
				definitionGeneration,
				openedAtStateRevision,
				connected,
				historyRecordKey,
				false
			);
		}

		public SessionMetadata {
			sessionId = Objects.requireNonNull(sessionId, "sessionId");
			pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			pageId = Objects.requireNonNull(pageId, "pageId");
			routeId = Objects.requireNonNull(routeId, "routeId");
			historyRecordKey = Objects.requireNonNull(
				historyRecordKey,
				"historyRecordKey"
			);
			if (definitionGeneration < 0L || openedAtStateRevision < 0L) {
				throw new IllegalArgumentException("terminal generation and revision cannot be negative");
			}
		}
	}

	public record HistoryDetailTarget(String recordKey, Identifier pageId) {
		public HistoryDetailTarget {
			recordKey = Objects.requireNonNull(recordKey, "recordKey");
			Objects.requireNonNull(pageId, "pageId");
		}
	}

	public record ViewerSnapshot(
		UUID playerId,
		String name,
		boolean online,
		boolean admin
	) {
		public ViewerSnapshot {
			playerId = Objects.requireNonNull(playerId, "playerId");
			name = Objects.requireNonNull(name, "name");
			if (name.isBlank() || name.length() > 64) {
				throw new IllegalArgumentException("viewer name must contain 1..64 characters");
			}
		}
	}

	public record Projection(byte[] bindingDocument, ProjectionSummary summary) {
		public Projection {
			bindingDocument = Objects.requireNonNull(bindingDocument, "bindingDocument").clone();
			summary = Objects.requireNonNull(summary, "summary");
			if (!summary.sha256().equals(sha256(bindingDocument))) {
				throw new IllegalArgumentException("projection summary hash does not match its document");
			}
		}

		@Override
		public byte[] bindingDocument() {
			return this.bindingDocument.clone();
		}
	}

	/**
	 * Stable equality-friendly summary for delta suppression and diagnostics.
	 */
	public record ProjectionSummary(
		String status,
		String sha256,
		int utf8Bytes,
		long definitionGeneration,
		long stateRevision,
		Optional<Identifier> gameId,
		Optional<Identifier> phaseId,
		Optional<Identifier> pageId,
		Optional<Identifier> taskId,
		int personalBindings,
		int historyItems,
		boolean truncated
	) {
		public ProjectionSummary {
			status = Objects.requireNonNull(status, "status");
			sha256 = Objects.requireNonNull(sha256, "sha256");
			gameId = Objects.requireNonNull(gameId, "gameId");
			phaseId = Objects.requireNonNull(phaseId, "phaseId");
			pageId = Objects.requireNonNull(pageId, "pageId");
			taskId = Objects.requireNonNull(taskId, "taskId");
			if (
				status.isBlank()
					|| !sha256.matches("[0-9a-f]{64}")
					|| utf8Bytes <= 0
					|| utf8Bytes > BindingContextDocument.MAX_UTF8_BYTES
					|| definitionGeneration < 0L
					|| stateRevision < 0L
					|| personalBindings < 0
					|| historyItems < 0
			) {
				throw new IllegalArgumentException("invalid player-terminal projection summary");
			}
		}
	}

	@FunctionalInterface
	interface PredicateGate {
		boolean test(Identifier predicate, PlayerRecord player);

		static PredicateGate denyAll() {
			return (predicate, player) -> false;
		}

		static PredicateGate allowAll() {
			return (predicate, player) -> true;
		}
	}

	private record ProjectionContext(
		DefinitionSnapshot definitions,
		WorldStateV3 state,
		WorldStateV2 core,
		GameDefinition game,
		Identifier phaseId,
		PlayerRecord player,
		Optional<UUID> hostId,
		ViewerSnapshot viewer,
		SessionMetadata session,
		PredicateGate predicates,
		Optional<PlayerTaskState> taskState,
		List<MessageHistoryRecord> messageHistory
	) {
		private ProjectionContext {
			hostId = Objects.requireNonNull(hostId, "hostId");
			taskState = Objects.requireNonNull(taskState, "taskState");
			messageHistory = List.copyOf(messageHistory);
		}

		private Optional<TimelineInstance> timeline() {
			return this.state.timeline();
		}

		private Optional<TaskInstance> currentTask() {
			return this.timeline().flatMap(TimelineInstance::currentTask);
		}
	}

	private record DefinitionSelection(
		Optional<DefinitionSnapshot> definitions,
		String status
	) {
		private DefinitionSelection {
			definitions = Objects.requireNonNull(definitions, "definitions");
			status = Objects.requireNonNull(status, "status");
		}

		private static DefinitionSelection available(
			final DefinitionSnapshot definitions,
			final String status
		) {
			return new DefinitionSelection(Optional.of(definitions), status);
		}

		private static DefinitionSelection unavailable(final String status) {
			return new DefinitionSelection(Optional.empty(), status);
		}
	}

	private record TaskProjection(JsonObject document, boolean truncated) {
	}

	private record PersonalProjection(
		JsonObject document,
		List<PlayerDataDefinition> projectedDefinitions
	) {
	}

	private record OptionalRootsProjection(
		TaskProjection task,
		JsonObject personal,
		JsonObject personalMetadata,
		DetailProjection detail,
		HistoryProjection history,
		boolean budgetTruncated
	) {
	}

	private record HistoryProjection(
		JsonObject document,
		int visibleItems,
		boolean truncated,
		List<HistoryViewCandidate> visibleCandidates
	) {
	}

	private record HistoryBundle(
		HistoryProjection history,
		DetailProjection detail
	) {
	}

	private record DetailProjection(JsonObject document, boolean truncated) {
		private DetailProjection {
			document = Objects.requireNonNull(document, "document");
		}
	}

	private record HistoryDetailSelection(
		HistoryViewCandidate candidate,
		String status
	) {
		private HistoryDetailSelection {
			status = Objects.requireNonNull(status, "status");
		}
	}

	private record ReservedHistoryItem(
		int index,
		HistoryViewCandidate candidate,
		JsonObject item
	) {
	}

	private record HistoryViewCandidate(
		HistoryCandidate taskOrEvent,
		MessageHistoryRecord message
	) {
		private HistoryViewCandidate {
			if ((taskOrEvent == null) == (message == null)) {
				throw new IllegalArgumentException(
					"history candidate must contain exactly one authoritative source"
				);
			}
		}

		private static HistoryViewCandidate taskOrEvent(final HistoryCandidate value) {
			return new HistoryViewCandidate(Objects.requireNonNull(value, "value"), null);
		}

		private static HistoryViewCandidate message(final MessageHistoryRecord value) {
			return new HistoryViewCandidate(null, Objects.requireNonNull(value, "value"));
		}
	}

	private record HistoryCandidate(
		TaskInstance task,
		TaskDefinition definition,
		TaskEventRecord record,
		TaskEventDefinition event,
		PlayerHistoryPresentation presentation,
		int stableOrder,
		HistoryCandidateKind kind,
		int eventCount
	) {
	}

	private enum HistoryCandidateKind {
		EVENT,
		TASK
	}

	private static final class ByteBudget {
		private int remaining;
		private boolean truncated;

		private ByteBudget(final int maximum) {
			this.remaining = maximum;
		}

		private boolean tryReserve(final JsonElement value) {
			return tryReserve("", value);
		}

		private boolean tryReserve(final String key, final JsonElement value) {
			int bytes = reservationBytes(key, value);
			if (bytes > this.remaining) {
				this.truncated = true;
				return false;
			}
			this.remaining -= bytes;
			return true;
		}

		private boolean tryReserveAll(final JsonElement... values) {
			long bytes = 0L;
			for (JsonElement value : values) {
				bytes += reservationBytes("", value);
			}
			if (bytes > this.remaining) {
				this.truncated = true;
				return false;
			}
			this.remaining -= (int) bytes;
			return true;
		}

		private static int reservationBytes(final String key, final JsonElement value) {
			return key.getBytes(StandardCharsets.UTF_8).length
				+ value.toString().getBytes(StandardCharsets.UTF_8).length
				+ 8;
		}

		private void markTruncated() {
			this.truncated = true;
		}

		private boolean truncated() {
			return this.truncated;
		}
	}
}
