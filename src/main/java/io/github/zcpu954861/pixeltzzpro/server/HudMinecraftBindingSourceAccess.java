package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBindingSourceType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudValueType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.HistoryRelease;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSourceType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerHistoryPresentation;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerSurface;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticType;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateContext;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingRequest;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BooleanValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ClockValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ComponentValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.DecimalValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.DurationValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.IdentifierValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.IntegerValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ListValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ObjectValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.PlayerValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.StringValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.Value;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FieldValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskEventRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

/**
 * Minecraft-backed, least-privilege source adapter for V3C HUD bindings.
 *
 * <p>The adapter is deliberately created for one player and one immutable state/definition
 * projection. Source authorization and source reads are separate operations: a read receives a
 * one-shot permit only after {@link #authorized(BindingRequest)} has accepted the same request.
 * Consequently a caller cannot use this adapter as an unrestricted scoreboard, storage or entity
 * NBT reader. Source descriptors and lookup failures are never included in returned values or
 * exception messages.
 */
public final class HudMinecraftBindingSourceAccess implements BindingSourceAccess {
	private static final UUID ZERO_UUID = new UUID(0L, 0L);
	private static final int MAX_NBT_ENTRIES = 64;
	private static final int MAX_NBT_DEPTH = 8;
	private static final Pattern SCORE_OBJECTIVE = Pattern.compile("[A-Za-z0-9_.+\\-]{1,16}");
	private static final Pattern SCORE_HOLDER = Pattern.compile("[A-Za-z0-9_#.+:\\-]{1,128}");
	private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_./{}\\[\\]\\-]{1,256}");
	private static final Set<String> SAFE_ENTITY_TARGETS = Set.of("self", "host");

	private final DefinitionSnapshot definitions;
	private final WorldStateV3 state;
	private final UUID viewerId;
	private final SourceBackend backend;
	private final Set<BindingRequest> oneShotGrants = new HashSet<>();

	private HudMinecraftBindingSourceAccess(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final UUID viewerId,
		final SourceBackend backend
	) {
		this.definitions = Objects.requireNonNull(definitions, "definitions");
		this.state = Objects.requireNonNull(state, "state");
		this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
		this.backend = Objects.requireNonNull(backend, "backend");
	}

	/** Creates one source adapter for a single authoritative player projection. */
	public static BindingSourceAccess create(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final ServerPlayer player
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(player, "player");
		return new HudMinecraftBindingSourceAccess(
			definitions,
			state,
			player.getUUID(),
			new MinecraftSourceBackend(server, definitions)
		);
	}

	static BindingSourceAccess createForTesting(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final UUID viewerId,
		final SourceBackend backend
	) {
		return new HudMinecraftBindingSourceAccess(definitions, state, viewerId, backend);
	}

	@Override
	public boolean authorized(final BindingRequest request) {
		Objects.requireNonNull(request, "request");
		boolean allowed;
		try {
			allowed = contextCoherent(request) && sourceAuthorized(request);
		} catch (RuntimeException ignored) {
			allowed = false;
		}
		if (allowed) {
			this.oneShotGrants.add(request);
		} else {
			this.oneShotGrants.remove(request);
		}
		return allowed;
	}

	@Override
	public SourceValue resolve(final BindingRequest request) {
		Objects.requireNonNull(request, "request");
		if (!this.oneShotGrants.remove(request)) {
			return SourceValue.missing();
		}
		try {
			return resolveValue(request).map(SourceValue::current).orElseGet(SourceValue::missing);
		} catch (RuntimeException ignored) {
			return SourceValue.missing();
		}
	}

	private boolean contextCoherent(final BindingRequest request) {
		HudRouteAuthority.Context context = request.audience();
		WorldStateV2 core = this.state.core();
		if (
			!context.playerId().equals(this.viewerId)
				|| core.activeGameId().filter(context.game()::equals).isEmpty()
				|| !core.activePhaseId().equals(context.phase())
				|| context.host() != core.host().map(value -> value.playerId().equals(this.viewerId)).orElse(false)
				|| context.online() != this.backend.online(this.viewerId)
		) {
			return false;
		}
		Optional<Identifier> currentTask = this.state.timeline()
			.flatMap(TimelineInstance::currentTask)
			.map(TaskInstance::taskId);
		if (!currentTask.equals(context.task())) {
			return false;
		}
		PlayerRecord record = core.players().get(this.viewerId);
		if (record == null) {
			return context.role().isEmpty() && context.team().isEmpty() && context.lifeState().isEmpty();
		}
		return context.role().filter(record.roleId()::equals).isPresent()
			&& context.team().equals(record.teamId())
			&& context.lifeState().filter(record.lifeStateId()::equals).isPresent();
	}

	private boolean sourceAuthorized(final BindingRequest request) {
		return switch (request.source().type()) {
			case GAME_FACT -> gameFactAuthorized(request);
			case PHASE_FACT -> phaseFactAuthorized(request);
			case TASK_FACT -> taskFactAuthorized(request);
			case PLAYER_FACT -> playerFactAuthorized(request);
			case EVENT -> eventAuthorized(request);
			case STATISTIC -> statisticAuthorized(request);
			case RESULT -> resultAuthorized(request);
			case CLOCK -> clockAuthorized(request);
			case PLAYER_DATA -> playerDataAuthorized(request);
			case SCORE -> scoreAuthorized(request);
			case STORAGE -> storageAuthorized(request);
			case ENTITY_DATA -> entityDataAuthorized(request);
		};
	}

	private boolean gameFactAuthorized(final BindingRequest request) {
		String value = sourceValue(request);
		return switch (value) {
			case "id" -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case "name" -> textType(request);
			case "state", "status" -> type(request, HudValueType.STRING, HudValueType.IDENTIFIER);
			case "active", "paused" -> type(request, HudValueType.BOOLEAN);
			case "elapsed_ticks", "game_elapsed_ticks" -> numericType(request, true);
			case "content_version", "participant_count" -> numericType(request, false);
			default -> false;
		};
	}

	private boolean phaseFactAuthorized(final BindingRequest request) {
		String value = sourceValue(request);
		return switch (value) {
			case "id" -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case "name" -> textType(request);
			case "active" -> type(request, HudValueType.BOOLEAN);
			default -> false;
		};
	}

	private boolean taskFactAuthorized(final BindingRequest request) {
		if (taskForContext(request).isEmpty()) {
			return false;
		}
		String value = sourceValue(request);
		return switch (value) {
			case "id" -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case "name", "description" -> textType(request);
			case "kind", "state", "status" -> type(request, HudValueType.STRING, HudValueType.IDENTIFIER);
			case "paused", "counts_toward_game_time" -> type(request, HudValueType.BOOLEAN);
			case "elapsed_ticks", "remaining_ticks", "duration_ticks" -> numericType(request, true);
			case "progress_current", "progress_maximum" -> numericType(request, false);
			case "timeline_position", "timeline_extent", "participant_count" -> numericType(request, false);
			case "public_objectives" -> listType(request, HudValueType.COMPONENT, HudValueType.STRING);
			default -> false;
		};
	}

	private boolean playerFactAuthorized(final BindingRequest request) {
		String value = sourceValue(request);
		return switch (value) {
			case "self" -> type(request, HudValueType.PLAYER);
			case "uuid", "name" -> type(request, HudValueType.STRING);
			case "online", "host", "is_host", "participant", "initialized", "ready" ->
				type(request, HudValueType.BOOLEAN);
			case "role", "team", "life_state" -> type(request, HudValueType.IDENTIFIER);
			case "role_name", "team_name", "life_state_name" -> textType(request);
			default -> false;
		};
	}

	private boolean eventAuthorized(final BindingRequest request) {
		TaskInstance task = taskForFacts(request).orElse(null);
		TaskDefinition definition = definition(task);
		TaskEventDefinition event = definition == null ? null : event(definition, request);
		if (task == null || event == null || !eventVisible(task, event, request)) {
			return false;
		}
		return switch (sourceValue(request)) {
			case "occurred" -> type(request, HudValueType.BOOLEAN);
			case "count" -> numericType(request, false);
			case "name" -> textType(request);
			case "latest_game_time", "latest_game_ticks", "latest_task_time", "latest_task_ticks" ->
				numericType(request, true);
			case "latest_actor" -> type(request, HudValueType.PLAYER);
			default -> false;
		};
	}

	private boolean statisticAuthorized(final BindingRequest request) {
		TaskInstance task = taskForFacts(request).orElse(null);
		TaskDefinition definition = definition(task);
		TaskStatisticDefinition statistic = definition == null ? null : statistic(definition, request);
		return task != null
			&& statistic != null
			&& recapVisible(statistic.recapVisibility(), task, request)
			&& statisticType(request, statistic.type());
	}

	private boolean resultAuthorized(final BindingRequest request) {
		TaskInstance task = taskForFacts(request).orElse(null);
		TaskDefinition definition = definition(task);
		if (
			task == null
				|| definition == null
				|| task.result().isEmpty()
				|| !recapVisible(definition.recapVisibility(), task, request)
		) {
			return false;
		}
		return switch (sourceValue(request)) {
			case "id", "semantic" -> type(request, HudValueType.STRING, HudValueType.IDENTIFIER);
			case "name", "recap" -> textType(request);
			case "frozen_at_game_tick" -> numericType(request, true);
			default -> false;
		};
	}

	private boolean clockAuthorized(final BindingRequest request) {
		if (!type(request, HudValueType.CLOCK)) {
			return false;
		}
		return switch (sourceValue(request)) {
			case "game" -> this.state.timeline().isPresent();
			case "task" -> taskForContext(request).isPresent();
			case "interval" -> taskForContext(request).flatMap(TaskInstance::intermission).isPresent();
			case "warmup" -> taskForContext(request)
				.map(TaskInstance::kind)
				.filter(WorldStateV3.TaskKind.WARMUP::equals)
				.isPresent();
			case "countdown" -> false;
			default -> false;
		};
	}

	private boolean playerDataAuthorized(final BindingRequest request) {
		Identifier field = request.source().field().orElse(null);
		PlayerDataDefinition data = field == null ? null : this.definitions.playerData().get(field);
		PlayerRecord player = this.state.core().players().get(this.viewerId);
		Identifier game = this.state.core().activeGameId().orElse(null);
		Identifier phase = this.state.core().activePhaseId().orElse(null);
		Optional<TaskInstance> currentTask = this.state.timeline().flatMap(TimelineInstance::currentTask);
		if (
			data == null
				|| player == null
				|| game == null
				|| phase == null
				|| !data.game().equals(game)
				|| !data.surfaces().contains(PlayerSurface.HUD)
				|| !AudienceMatcher.matches(
					this.definitions,
					data.audience(),
					player,
					this.state.core().host().map(WorldStateV2.HostRecord::playerId),
					this.backend.online(this.viewerId)
				)
				|| (!data.phases().isEmpty() && !data.phases().contains(phase))
				|| (
					!data.tasks().isEmpty()
						&& currentTask.map(TaskInstance::taskId).filter(data.tasks()::contains).isEmpty()
				)
				|| (
					!data.taskStates().isEmpty()
						&& taskState(this.state.timeline()).filter(data.taskStates()::contains).isEmpty()
				)
				|| !playerDataType(request, data)
		) {
			return false;
		}
		if (data.predicate().isEmpty()) {
			return true;
		}
		return this.backend.predicate(
			data.predicate().orElseThrow(),
			new PredicateContext(
				this.viewerId,
				this.state.activeGameInstanceId().orElse(ZERO_UUID),
				game,
				data.id(),
				1,
				phase,
				Map.of(),
				player.persistentFields()
			)
		);
	}

	private boolean scoreAuthorized(final BindingRequest request) {
		String objective = request.source().objective().orElse("");
		String holder = request.source().holder().orElse("");
		return SCORE_OBJECTIVE.matcher(objective).matches()
			&& SCORE_HOLDER.matcher(holder).matches()
			&& numericType(request, true);
	}

	private boolean storageAuthorized(final BindingRequest request) {
		return request.source().storage().isPresent()
			&& request.source().path().filter(value -> SAFE_PATH.matcher(value).matches()).isPresent()
			&& request.binding().valueType() != HudValueType.CLOCK;
	}

	private boolean entityDataAuthorized(final BindingRequest request) {
		return request.source().target().filter(SAFE_ENTITY_TARGETS::contains).isPresent()
			&& request.source().path().filter(value -> SAFE_PATH.matcher(value).matches()).isPresent()
			&& request.binding().valueType() != HudValueType.CLOCK;
	}

	private Optional<Value> resolveValue(final BindingRequest request) {
		return switch (request.source().type()) {
			case GAME_FACT -> gameFact(request);
			case PHASE_FACT -> phaseFact(request);
			case TASK_FACT -> taskFact(request);
			case PLAYER_FACT -> playerFact(request);
			case EVENT -> eventValue(request);
			case STATISTIC -> statisticValue(request);
			case RESULT -> resultValue(request);
			case CLOCK -> clockValue(request);
			case PLAYER_DATA -> playerDataValue(request);
			case SCORE -> scoreValue(request);
			case STORAGE -> storageValue(request);
			case ENTITY_DATA -> entityDataValue(request);
		};
	}

	private Optional<Value> gameFact(final BindingRequest request) {
		Identifier gameId = this.state.core().activeGameId().orElse(null);
		GameDefinition game = gameId == null ? null : this.definitions.games().get(gameId);
		TimelineInstance timeline = this.state.timeline().orElse(null);
		return switch (sourceValue(request)) {
			case "id" -> identifier(request, gameId);
			case "name" -> game == null ? Optional.empty() : richText(request, game.name());
			case "state", "status" -> scalarText(
				request,
				timeline == null ? "setup" : publicTimelineStatus(timeline)
			);
			case "active" -> booleanValue(request, gameId != null);
			case "paused" -> booleanValue(request, timeline != null && timelinePaused(timeline));
			case "elapsed_ticks", "game_elapsed_ticks" -> timeline == null
				? Optional.empty()
				: numeric(request, timeline.gameElapsedTicks());
			case "content_version" -> game == null
				? Optional.empty()
				: numeric(request, game.contentVersion());
			case "participant_count" -> numeric(
				request,
				timeline == null
					? 0L
					: timeline.currentTask().map(value -> (long)value.participants().size()).orElse(0L)
			);
			default -> Optional.empty();
		};
	}

	private Optional<Value> phaseFact(final BindingRequest request) {
		Identifier phaseId = this.state.core().activePhaseId().orElse(null);
		PhaseDefinition phase = phaseId == null ? null : this.definitions.phases().get(phaseId);
		return switch (sourceValue(request)) {
			case "id" -> identifier(request, phaseId);
			case "name" -> phase == null ? Optional.empty() : richText(request, phase.name());
			case "active" -> booleanValue(request, phase != null);
			default -> Optional.empty();
		};
	}

	private Optional<Value> taskFact(final BindingRequest request) {
		TaskInstance task = taskForContext(request).orElse(null);
		TaskDefinition definition = definition(task);
		if (task == null || definition == null) {
			return Optional.empty();
		}
		return switch (sourceValue(request)) {
			case "id" -> identifier(request, task.taskId());
			case "name" -> richText(request, definition.name());
			case "description" -> definition.description().flatMap(value -> richText(request, value));
			case "kind" -> scalarText(request, task.kind().getSerializedName());
			case "state", "status" -> scalarText(request, publicTaskStatus(this.state.timeline().orElseThrow(), task));
			case "paused" -> booleanValue(request, timelinePaused(this.state.timeline().orElseThrow()));
			case "counts_toward_game_time" -> booleanValue(request, definition.countsTowardGameTime());
			case "elapsed_ticks" -> numeric(request, task.elapsedTicks());
			case "remaining_ticks" -> definition.durationTicks().isEmpty()
				? Optional.empty()
				: numeric(request, Math.max(0L, definition.durationTicks().orElseThrow() - task.elapsedTicks()));
			case "duration_ticks" -> definition.durationTicks().isEmpty()
				? Optional.empty()
				: numeric(request, definition.durationTicks().orElseThrow());
			case "progress_current" -> progress(task, definition, false).flatMap(value -> numeric(request, value));
			case "progress_maximum" -> progress(task, definition, true).flatMap(value -> numeric(request, value));
			case "timeline_position" -> numeric(
				request,
				this.state.timeline().orElseThrow().taskHistory().size() + 1L
			);
			case "timeline_extent" -> numeric(
				request,
				this.definitions.tasks().values().stream()
					.filter(value -> value.game().equals(definition.game()))
					.count()
			);
			case "participant_count" -> numeric(request, task.participants().size());
			case "public_objectives" -> publicObjectives(request, definition);
			default -> Optional.empty();
		};
	}

	private Optional<Value> playerFact(final BindingRequest request) {
		PlayerRecord record = this.state.core().players().get(this.viewerId);
		PlayerIdentity identity = this.backend.player(this.viewerId).orElseGet(() ->
			new PlayerIdentity(
				this.viewerId,
				record == null ? this.viewerId.toString() : record.lastKnownName(),
				this.backend.online(this.viewerId)
			)
		);
		return switch (sourceValue(request)) {
			case "self" -> Optional.of(new PlayerValue(identity.playerId(), identity.name(), identity.online()));
			case "uuid" -> Optional.of(new StringValue(this.viewerId.toString()));
			case "name" -> Optional.of(new StringValue(identity.name()));
			case "online" -> Optional.of(new BooleanValue(identity.online()));
			case "host", "is_host" -> Optional.of(new BooleanValue(isHost()));
			case "participant" -> Optional.of(new BooleanValue(request.audience().participant()));
			case "initialized" -> record == null
				? Optional.empty()
				: Optional.of(new BooleanValue(playerInitialized(record)));
			case "ready" -> record == null
				? Optional.empty()
				: Optional.of(new BooleanValue(playerReady(this.viewerId)));
			case "role" -> record == null ? Optional.empty() : Optional.of(new IdentifierValue(record.roleId()));
			case "team" -> record == null ? Optional.empty() : record.teamId().map(IdentifierValue::new);
			case "life_state" -> record == null
				? Optional.empty()
				: Optional.of(new IdentifierValue(record.lifeStateId()));
			case "role_name" -> record == null
				? Optional.empty()
				: Optional.ofNullable(this.definitions.roles().get(record.roleId()))
					.flatMap(value -> richText(request, value.name()));
			case "team_name" -> record == null
				? Optional.empty()
				: record.teamId().map(this.definitions.teams()::get)
					.flatMap(value -> value == null ? Optional.empty() : richText(request, value.name()));
			case "life_state_name" -> record == null
				? Optional.empty()
				: Optional.ofNullable(this.definitions.lifeStates().get(record.lifeStateId()))
					.flatMap(value -> richText(request, value.name()));
			default -> Optional.empty();
		};
	}

	private Optional<Value> eventValue(final BindingRequest request) {
		TaskInstance task = taskForFacts(request).orElse(null);
		TaskDefinition definition = definition(task);
		TaskEventDefinition event = definition == null ? null : event(definition, request);
		if (task == null || event == null) {
			return Optional.empty();
		}
		List<TaskEventRecord> records = task.events().stream()
			.filter(value -> value.eventId().equals(event.id()))
			.toList();
		TaskEventRecord latest = records.isEmpty() ? null : records.getLast();
		return switch (sourceValue(request)) {
			case "occurred" -> Optional.of(new BooleanValue(!records.isEmpty()));
			case "count" -> numeric(request, records.size());
			case "name" -> richText(request, event.name());
			case "latest_game_time", "latest_game_ticks" -> latest == null
				? Optional.empty()
				: numeric(request, latest.gameElapsedTicks());
			case "latest_task_time", "latest_task_ticks" -> latest == null
				? Optional.empty()
				: numeric(request, latest.taskElapsedTicks());
			case "latest_actor" -> latest == null
				? Optional.empty()
				: latest.player().map(this::playerValue);
			default -> Optional.empty();
		};
	}

	private Optional<Value> statisticValue(final BindingRequest request) {
		TaskInstance task = taskForFacts(request).orElse(null);
		TaskDefinition definition = definition(task);
		TaskStatisticDefinition declared = definition == null ? null : statistic(definition, request);
		if (task == null || declared == null) {
			return Optional.empty();
		}
		return task.statistics().stream()
			.filter(value -> value.statisticId().equals(declared.id()))
			.findFirst()
			.flatMap(value -> statisticValue(request, value));
	}

	private Optional<Value> resultValue(final BindingRequest request) {
		TaskInstance task = taskForFacts(request).orElse(null);
		TaskDefinition definition = definition(task);
		if (task == null || definition == null || task.result().isEmpty()) {
			return Optional.empty();
		}
		WorldStateV3.FrozenResult frozen = task.result().orElseThrow();
		TaskResult declared = definition.results().get(frozen.resultId());
		if (declared == null) {
			return Optional.empty();
		}
		return switch (sourceValue(request)) {
			case "id" -> scalarText(request, frozen.resultId());
			case "semantic" -> scalarText(request, declared.style().semantic().name().toLowerCase(Locale.ROOT));
			case "name" -> richText(request, declared.name());
			case "recap" -> declared.recap().flatMap(value -> richText(request, value));
			case "frozen_at_game_tick" -> numeric(request, frozen.frozenAtGameTick());
			default -> Optional.empty();
		};
	}

	private Optional<Value> clockValue(final BindingRequest request) {
		TimelineInstance timeline = this.state.timeline().orElse(null);
		TaskInstance task = taskForContext(request).orElse(null);
		if (timeline == null) {
			return Optional.empty();
		}
		long serverTick = Math.max(0L, this.backend.serverTick());
		boolean blocked = timeline.status() == TimelineStatus.BLOCKED;
		return switch (sourceValue(request)) {
			case "game" -> {
				TaskDefinition definition = definition(task);
				boolean running = !timeline.paused()
					&& !blocked
					&& timeline.status() == TimelineStatus.RUNNING
					&& task != null
					&& definition != null
					&& definition.countsTowardGameTime();
				yield Optional.of(new ClockValue(
					serverTick,
					timeline.gameElapsedTicks(),
					running ? 1.0D : 0.0D,
					timeline.paused() || blocked
				));
			}
			case "task" -> task == null
				? Optional.empty()
				: Optional.of(new ClockValue(
					serverTick,
					task.elapsedTicks(),
					!timeline.paused() && !blocked && timeline.status() == TimelineStatus.RUNNING ? 1.0D : 0.0D,
					timeline.paused() || blocked
				));
			case "warmup" -> task == null || task.kind() != WorldStateV3.TaskKind.WARMUP
				? Optional.empty()
				: Optional.of(new ClockValue(
					serverTick,
					task.elapsedTicks(),
					!timeline.paused() && !blocked && timeline.status() == TimelineStatus.RUNNING ? 1.0D : 0.0D,
					timeline.paused() || blocked
				));
			case "interval" -> task == null || task.intermission().isEmpty()
				? Optional.empty()
				: Optional.of(new ClockValue(
					serverTick,
					task.intermission().orElseThrow().elapsedTicks(),
					!timeline.paused()
						&& !blocked
						&& !task.intermission().orElseThrow().paused()
						&& timeline.status() == TimelineStatus.INTERMISSION
						? 1.0D
						: 0.0D,
					timeline.paused() || blocked || task.intermission().orElseThrow().paused()
				));
			default -> Optional.empty();
		};
	}

	private Optional<Value> playerDataValue(final BindingRequest request) {
		PlayerDataDefinition data = request.source().field().map(this.definitions.playerData()::get).orElse(null);
		PlayerRecord player = this.state.core().players().get(this.viewerId);
		if (data == null || player == null) {
			return Optional.empty();
		}
		PlayerDataSource source = data.source();
		return switch (source.type()) {
			case ROLE -> identifier(request, player.roleId());
			case TEAM -> player.teamId().flatMap(value -> identifier(request, value));
			case LIFE_STATE -> identifier(request, player.lifeStateId());
			case INITIALIZED -> booleanValue(request, playerInitialized(player));
			case READY -> booleanValue(request, playerReady(player.playerId()));
			case FIELD, EXCLUSIVE_CHOICE -> source.id()
				.flatMap(value -> persistentValue(request, player, value, source.type() == PlayerDataSourceType.EXCLUSIVE_CHOICE));
			case GAME_ELAPSED_TICKS -> this.state.timeline()
				.flatMap(value -> numeric(request, value.gameElapsedTicks()));
			case TASK_ID,
				TASK_NAME,
				TASK_DESCRIPTION,
				TASK_STATUS,
				TASK_ELAPSED_TICKS,
				TASK_REMAINING_TICKS,
				TASK_PROGRESS,
				TASK_RESULT,
				TASK_STATISTIC -> playerTaskDataValue(request, source);
		};
	}

	private Optional<Value> scoreValue(final BindingRequest request) {
		String holder = request.source().holder().orElseThrow();
		String resolvedHolder = holder.equals("self")
			? this.backend.player(this.viewerId)
				.map(PlayerIdentity::name)
				.orElseGet(() -> this.state.core().players().get(this.viewerId).lastKnownName())
			: holder;
		OptionalLong value = this.backend.score(
			request.source().objective().orElseThrow(),
			resolvedHolder
		);
		return value.isPresent() ? numeric(request, value.orElseThrow()) : Optional.empty();
	}

	private Optional<Value> storageValue(final BindingRequest request) {
		return this.backend.storage(
			request.source().storage().orElseThrow(),
			request.source().path().orElseThrow()
		).flatMap(value -> tagValue(request, value));
	}

	private Optional<Value> entityDataValue(final BindingRequest request) {
		UUID target = switch (request.source().target().orElseThrow()) {
			case "self" -> this.viewerId;
			case "host" -> this.state.core().host().map(WorldStateV2.HostRecord::playerId).orElse(null);
			default -> null;
		};
		return target == null
			? Optional.empty()
			: this.backend.entity(target, request.source().path().orElseThrow())
				.flatMap(value -> tagValue(request, value));
	}

	private Optional<Value> playerTaskDataValue(
		final BindingRequest request,
		final PlayerDataSource source
	) {
		TaskInstance task = findTask(this.state.timeline(), source.id()).orElse(null);
		TaskDefinition definition = definition(task);
		if (task == null || definition == null) {
			return Optional.empty();
		}
		return switch (source.type()) {
			case TASK_ID -> identifier(request, task.taskId());
			case TASK_NAME -> richText(request, definition.name());
			case TASK_DESCRIPTION -> definition.description().flatMap(value -> richText(request, value));
			case TASK_STATUS -> scalarText(request, publicTaskStatus(this.state.timeline().orElseThrow(), task));
			case TASK_ELAPSED_TICKS -> numeric(request, task.elapsedTicks());
			case TASK_REMAINING_TICKS -> definition.durationTicks().isEmpty()
				? Optional.empty()
				: numeric(request, Math.max(0L, definition.durationTicks().orElseThrow() - task.elapsedTicks()));
			case TASK_PROGRESS -> source.key()
				.flatMap(key -> numericStatistic(task, key))
				.flatMap(value -> numeric(request, value));
			case TASK_RESULT -> task.result()
				.filter(value -> source.key().map(value.resultId()::equals).orElse(true))
				.flatMap(value -> scalarText(request, value.resultId()));
			case TASK_STATISTIC -> source.key()
				.flatMap(key -> task.statistics().stream()
					.filter(value -> value.statisticId().equals(key))
					.findFirst())
				.flatMap(value -> statisticValue(request, value));
			default -> Optional.empty();
		};
	}

	private Optional<Value> persistentValue(
		final BindingRequest request,
		final PlayerRecord player,
		final Identifier fieldId,
		final boolean requireExclusive
	) {
		FieldDefinition field = this.definitions.fields().get(fieldId);
		PersistentFieldValue persisted = player.persistentFields().get(fieldId);
		if (
			field == null
				|| persisted == null
				|| this.state.core().activeGameId().filter(field.game()::equals).isEmpty()
				|| persisted.fieldVersion() != field.version()
				|| (requireExclusive && field.type() != FieldType.EXCLUSIVE_CHOICE)
				|| (
					requireExclusive
						&& this.state.exclusiveReservations().stream().noneMatch(value ->
							value.fieldId().equals(fieldId)
								&& value.ownerId().equals(player.playerId())
								&& value.status() == ReservationStatus.LOCKED
						)
				)
		) {
			return Optional.empty();
		}
		return storedValue(request, persisted.value());
	}

	private Optional<Value> storedValue(final BindingRequest request, final StoredValue value) {
		return switch (value.type()) {
			case BOOLEAN -> booleanValue(request, value.booleanValue().orElseThrow());
			case INTEGER -> numeric(request, value.integerValue().orElseThrow());
			case STRING, SINGLE_CHOICE -> scalarText(request, value.stringValue().orElseThrow());
			case IDENTIFIER -> identifier(request, value.identifierValue().orElseThrow());
			case MULTI_CHOICE -> stringList(request, value.choices());
		};
	}

	private Optional<Value> statisticValue(
		final BindingRequest request,
		final TaskStatisticValue value
	) {
		return switch (value.type()) {
			case BOOLEAN -> value.booleanValue().flatMap(item -> booleanValue(request, item));
			case INTEGER, DURATION_TICKS -> value.longValue().flatMap(item -> numeric(request, item));
			case STRING -> value.stringValue().flatMap(item -> scalarText(request, item));
			case IDENTIFIER -> value.identifierValue().flatMap(item -> identifier(request, item));
			case PLAYER -> value.playerValue().map(this::playerValue);
		};
	}

	private Optional<Value> tagValue(final BindingRequest request, final Tag tag) {
		return tagValue(request.binding().valueType(), request.binding().itemType(), tag, 0, new EntryBudget());
	}

	private Optional<Value> tagValue(
		final HudValueType type,
		final Optional<HudValueType> itemType,
		final Tag tag,
		final int depth,
		final EntryBudget budget
	) {
		if (tag == null || depth > MAX_NBT_DEPTH || !budget.take()) {
			return Optional.empty();
		}
		return switch (type) {
			case STRING -> tag.asString().map(StringValue::new);
			case INTEGER -> integral(tag).map(IntegerValue::new);
			case DECIMAL -> tag.asNumber().map(Number::doubleValue).filter(Double::isFinite).map(DecimalValue::new);
			case BOOLEAN -> booleanTag(tag).map(BooleanValue::new);
			case IDENTIFIER -> tag.asString().flatMap(HudMinecraftBindingSourceAccess::identifierValue);
			case PLAYER -> tag.asString().flatMap(this::playerFromString);
			case COMPONENT -> tag.asString().flatMap(HudMinecraftBindingSourceAccess::componentValue);
			case DURATION -> integral(tag).filter(value -> value >= 0L).map(DurationValue::new);
			case CLOCK -> Optional.empty();
			case LIST -> listValue(itemType.orElse(null), tag, depth, budget);
			case OBJECT -> objectValue(tag, depth, budget);
		};
	}

	private Optional<Value> listValue(
		final HudValueType itemType,
		final Tag tag,
		final int depth,
		final EntryBudget budget
	) {
		if (itemType == null || !(tag instanceof ListTag list) || list.size() > MAX_NBT_ENTRIES) {
			return Optional.empty();
		}
		List<Value> values = new ArrayList<>(list.size());
		for (int index = 0; index < list.size(); index++) {
			Optional<Value> value = tagValue(itemType, Optional.empty(), list.get(index), depth + 1, budget);
			if (value.isEmpty()) {
				return Optional.empty();
			}
			values.add(value.orElseThrow());
		}
		return Optional.of(new ListValue(itemType, values));
	}

	private Optional<Value> objectValue(
		final Tag tag,
		final int depth,
		final EntryBudget budget
	) {
		if (!(tag instanceof CompoundTag compound) || compound.size() > MAX_NBT_ENTRIES) {
			return Optional.empty();
		}
		Map<String, Value> values = new LinkedHashMap<>();
		for (String key : compound.keySet().stream().sorted().toList()) {
			Tag child = compound.get(key);
			Optional<Value> value = naturalTagValue(child, depth + 1, budget);
			if (value.isEmpty()) {
				return Optional.empty();
			}
			values.put(key, value.orElseThrow());
		}
		return Optional.of(new ObjectValue(values));
	}

	private Optional<Value> naturalTagValue(
		final Tag tag,
		final int depth,
		final EntryBudget budget
	) {
		if (tag == null || depth > MAX_NBT_DEPTH || !budget.take()) {
			return Optional.empty();
		}
		if (tag instanceof CompoundTag) {
			return objectValue(tag, depth, budget);
		}
		if (tag instanceof ListTag list) {
			if (list.size() > MAX_NBT_ENTRIES) {
				return Optional.empty();
			}
			if (list.isEmpty()) {
				return Optional.of(new ListValue(HudValueType.STRING, List.of()));
			}
			List<Value> values = new ArrayList<>(list.size());
			HudValueType itemType = null;
			for (int index = 0; index < list.size(); index++) {
				Optional<Value> value = naturalTagValue(list.get(index), depth + 1, budget);
				if (value.isEmpty()) {
					return Optional.empty();
				}
				Value resolved = value.orElseThrow();
				if (itemType == null) {
					itemType = resolved.type();
				} else if (itemType != resolved.type()) {
					return Optional.empty();
				}
				values.add(resolved);
			}
			return Optional.of(new ListValue(itemType, values));
		}
		Optional<Number> number = tag.asNumber();
		if (number.isPresent()) {
			double decimal = number.orElseThrow().doubleValue();
			long integer = number.orElseThrow().longValue();
			return Double.isFinite(decimal) && decimal == integer
				? Optional.of(new IntegerValue(integer))
				: Double.isFinite(decimal) ? Optional.of(new DecimalValue(decimal)) : Optional.empty();
		}
		return tag.asString().map(StringValue::new);
	}

	private Optional<Value> publicObjectives(
		final BindingRequest request,
		final TaskDefinition definition
	) {
		List<RichText> objectives = definition.events().stream()
			.filter(value -> value.playerHistory().isPresent())
			.map(TaskEventDefinition::name)
			.toList();
		if (request.binding().itemType().filter(HudValueType.COMPONENT::equals).isPresent()) {
			List<Value> values = new ArrayList<>();
			for (RichText objective : objectives) {
				Optional<ComponentValue> value = component(objective);
				if (value.isEmpty()) {
					return Optional.empty();
				}
				values.add(value.orElseThrow());
			}
			return Optional.of(new ListValue(HudValueType.COMPONENT, values));
		}
		return stringList(request, objectives.stream().map(RichText::plainText).toList());
	}

	private Optional<Value> stringList(final BindingRequest request, final List<String> values) {
		if (
			request.binding().valueType() != HudValueType.LIST
				|| request.binding().itemType().filter(HudValueType.STRING::equals).isEmpty()
				|| values.size() > MAX_NBT_ENTRIES
		) {
			return Optional.empty();
		}
		return Optional.of(new ListValue(
			HudValueType.STRING,
			values.stream().map(StringValue::new).map(Value.class::cast).toList()
		));
	}

	private Optional<Value> numeric(final BindingRequest request, final long value) {
		return switch (request.binding().valueType()) {
			case INTEGER -> Optional.of(new IntegerValue(value));
			case DECIMAL -> Optional.of(new DecimalValue(value));
			case DURATION -> value < 0L ? Optional.empty() : Optional.of(new DurationValue(value));
			default -> Optional.empty();
		};
	}

	private Optional<Value> booleanValue(final BindingRequest request, final boolean value) {
		return type(request, HudValueType.BOOLEAN)
			? Optional.of(new BooleanValue(value))
			: Optional.empty();
	}

	private Optional<Value> identifier(final BindingRequest request, final Identifier value) {
		if (value == null) {
			return Optional.empty();
		}
		return switch (request.binding().valueType()) {
			case IDENTIFIER -> Optional.of(new IdentifierValue(value));
			case STRING -> Optional.of(new StringValue(value.toString()));
			default -> Optional.empty();
		};
	}

	private Optional<Value> scalarText(final BindingRequest request, final String value) {
		if (value == null) {
			return Optional.empty();
		}
		return switch (request.binding().valueType()) {
			case STRING -> Optional.of(new StringValue(value));
			case IDENTIFIER -> identifierValue(value).map(Value.class::cast);
			case COMPONENT -> Optional.of(new ComponentValue(Component.literal(value)));
			default -> Optional.empty();
		};
	}

	private Optional<Value> richText(final BindingRequest request, final RichText value) {
		return switch (request.binding().valueType()) {
			case STRING -> Optional.of(new StringValue(value.plainText()));
			case COMPONENT -> component(value).map(Value.class::cast);
			default -> Optional.empty();
		};
	}

	private PlayerValue playerValue(final FrozenPlayerValue value) {
		return this.backend.player(value.playerId())
			.map(player -> new PlayerValue(player.playerId(), player.name(), player.online()))
			.orElseGet(() -> new PlayerValue(value.playerId(), value.playerName(), false));
	}

	private Optional<PlayerValue> playerFromString(final String value) {
		try {
			UUID id = UUID.fromString(value);
			return this.backend.player(id)
				.map(player -> new PlayerValue(player.playerId(), player.name(), player.online()));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	private boolean playerDataType(final BindingRequest request, final PlayerDataDefinition data) {
		PlayerDataSource source = data.source();
		return switch (source.type()) {
			case ROLE, TEAM, LIFE_STATE, TASK_ID -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case INITIALIZED, READY -> type(request, HudValueType.BOOLEAN);
			case FIELD, EXCLUSIVE_CHOICE -> source.id()
				.map(this.definitions.fields()::get)
				.map(value -> fieldType(request, value))
				.orElse(false);
			case TASK_NAME, TASK_DESCRIPTION -> textType(request);
			case TASK_STATUS, TASK_RESULT -> type(request, HudValueType.STRING, HudValueType.COMPONENT);
			case TASK_ELAPSED_TICKS, TASK_REMAINING_TICKS, GAME_ELAPSED_TICKS -> numericType(request, true);
			case TASK_PROGRESS -> numericType(request, false);
			case TASK_STATISTIC -> playerDataStatisticType(request, source);
		};
	}

	private boolean playerDataStatisticType(
		final BindingRequest request,
		final PlayerDataSource source
	) {
		TaskInstance task = findTask(this.state.timeline(), source.id()).orElse(null);
		TaskDefinition definition = definition(task);
		if (definition == null || source.key().isEmpty()) {
			return false;
		}
		return definition.statistics().stream()
			.filter(value -> value.id().equals(source.key().orElseThrow()))
			.findFirst()
			.map(value -> statisticType(request, value.type()))
			.orElse(false);
	}

	private boolean fieldType(final BindingRequest request, final FieldDefinition field) {
		return switch (field.type()) {
			case BOOLEAN -> type(request, HudValueType.BOOLEAN);
			case INTEGER -> numericType(request, false);
			case STRING, SINGLE_CHOICE, EXCLUSIVE_CHOICE -> type(request, HudValueType.STRING, HudValueType.COMPONENT);
			case IDENTIFIER -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case MULTI_CHOICE -> listType(request, HudValueType.STRING);
		};
	}

	private boolean statisticType(final BindingRequest request, final TaskStatisticType type) {
		return switch (type) {
			case BOOLEAN -> type(request, HudValueType.BOOLEAN);
			case INTEGER -> numericType(request, false);
			case DURATION_TICKS -> numericType(request, true);
			case STRING -> type(request, HudValueType.STRING, HudValueType.COMPONENT);
			case IDENTIFIER -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case PLAYER -> type(request, HudValueType.PLAYER);
		};
	}

	private boolean eventVisible(
		final TaskInstance task,
		final TaskEventDefinition event,
		final BindingRequest request
	) {
		PlayerHistoryPresentation presentation = event.playerHistory().orElse(null);
		if (presentation == null) {
			return recapVisible(event.recapVisibility(), task, request);
		}
		PlayerRecord player = this.state.core().players().get(this.viewerId);
		return player != null
			&& released(this.state.timeline().orElseThrow(), task, presentation.release())
			&& AudienceMatcher.matches(
				this.definitions,
				presentation.audience(),
				player,
				this.state.core().host().map(WorldStateV2.HostRecord::playerId),
				this.backend.online(this.viewerId)
			);
	}

	private boolean recapVisible(
		final RecapVisibility visibility,
		final TaskInstance task,
		final BindingRequest request
	) {
		return switch (visibility) {
			case PARTICIPANTS -> task.participants().contains(this.viewerId) || request.audience().participant();
			case HOST_ONLY -> isHost();
			case HIDDEN -> false;
		};
	}

	private boolean released(
		final TimelineInstance timeline,
		final TaskInstance task,
		final HistoryRelease release
	) {
		return switch (release) {
			case IMMEDIATE -> true;
			case TASK_END -> task.status() == TaskStatus.SETTLED || task.status() == TaskStatus.INTERRUPTED;
			case GAME_END -> {
				TimelineStatus status = timeline.status() == TimelineStatus.BLOCKED
					? timeline.resumeStatus().orElse(TimelineStatus.BLOCKED)
					: timeline.status();
				yield status == TimelineStatus.COMPLETED || status == TimelineStatus.INTERRUPTED;
			}
		};
	}

	private TaskEventDefinition event(
		final TaskDefinition definition,
		final BindingRequest request
	) {
		String id = localSourceId(request);
		return definition.events().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
	}

	private TaskStatisticDefinition statistic(
		final TaskDefinition definition,
		final BindingRequest request
	) {
		String id = localSourceId(request);
		return definition.statistics().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
	}

	private String localSourceId(final BindingRequest request) {
		Identifier id = request.source().id().orElseThrow();
		String path = id.getPath();
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	private TaskDefinition definition(final TaskInstance task) {
		return task == null ? null : this.definitions.tasks().get(task.taskId());
	}

	private Optional<TaskInstance> taskForContext(final BindingRequest request) {
		return request.audience().task().flatMap(id -> findTask(this.state.timeline(), Optional.of(id)));
	}

	private Optional<TaskInstance> taskForFacts(final BindingRequest request) {
		// V3C HUD facts are live-current facts. Historical task data is projected only by
		// explicitly authorized history surfaces, never by silently reusing the last task here.
		return taskForContext(request);
	}

	private Optional<TaskInstance> findTask(
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

	private Optional<Long> progress(
		final TaskInstance task,
		final TaskDefinition definition,
		final boolean maximum
	) {
		List<String> keys = maximum
			? List.of("progress_maximum", "progress_max")
			: List.of("progress_current", "progress");
		for (String key : keys) {
			Optional<Long> value = numericStatistic(task, key);
			if (value.isPresent()) {
				return value;
			}
		}
		if (maximum) {
			return definition.statistics().stream()
				.filter(value -> value.id().equals("progress_current") || value.id().equals("progress"))
				.map(TaskStatisticDefinition::maximum)
				.flatMap(Optional::stream)
				.findFirst();
		}
		return Optional.empty();
	}

	private Optional<Long> numericStatistic(final TaskInstance task, final String key) {
		return task.statistics().stream()
			.filter(value -> value.statisticId().equals(key))
			.filter(value -> value.type() == TaskStatisticValueType.INTEGER || value.type() == TaskStatisticValueType.DURATION_TICKS)
			.map(TaskStatisticValue::longValue)
			.flatMap(Optional::stream)
			.findFirst();
	}

	private Optional<PlayerTaskState> taskState(final Optional<TimelineInstance> timeline) {
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

	private boolean playerInitialized(final PlayerRecord player) {
		var role = this.definitions.roles().get(player.roleId());
		return role != null
			&& TabListDisplay.hasCurrentIdentityCredential(
				player.playerId(),
				player.roleId(),
				role.initializationFlow(),
				role.initializationFlow().map(this.definitions.flows()::get).map(value -> value.version()),
				player.pendingRoleChange(),
				this.state.core().completionHistory()
			);
	}

	private boolean playerReady(final UUID playerId) {
		return this.state.readiness()
			.map(value -> value.flow().runtime().members().get(playerId))
			.map(WorldStateV2.FlowMemberState::status)
			.filter(value -> value == WorldStateV2.FlowMemberStatus.COMPLETED || value == WorldStateV2.FlowMemberStatus.ALREADY_COMPLETE)
			.isPresent();
	}

	private boolean isHost() {
		return this.state.core().host().map(value -> value.playerId().equals(this.viewerId)).orElse(false);
	}

	private static String publicTimelineStatus(final TimelineInstance timeline) {
		if (timeline.status() == TimelineStatus.BLOCKED) {
			return "blocked";
		}
		if (timeline.paused()) {
			return "paused";
		}
		return timeline.status().getSerializedName();
	}

	private static String publicTaskStatus(
		final TimelineInstance timeline,
		final TaskInstance task
	) {
		if (timeline.status() == TimelineStatus.BLOCKED || task.status() == TaskStatus.BLOCKED) {
			return "blocked";
		}
		if (timeline.paused()) {
			return "paused";
		}
		if (timeline.status() == TimelineStatus.INTERMISSION) {
			return "interval";
		}
		return task.status().getSerializedName();
	}

	private static boolean timelinePaused(final TimelineInstance timeline) {
		return timeline.paused() || timeline.status() == TimelineStatus.BLOCKED;
	}

	private static String sourceValue(final BindingRequest request) {
		return request.source().value().orElse("").toLowerCase(Locale.ROOT);
	}

	private static boolean type(final BindingRequest request, final HudValueType... values) {
		for (HudValueType value : values) {
			if (request.binding().valueType() == value) {
				return true;
			}
		}
		return false;
	}

	private static boolean textType(final BindingRequest request) {
		return type(request, HudValueType.STRING, HudValueType.COMPONENT);
	}

	private static boolean numericType(final BindingRequest request, final boolean duration) {
		return type(
			request,
			duration ? HudValueType.DURATION : HudValueType.INTEGER,
			HudValueType.INTEGER,
			HudValueType.DECIMAL
		);
	}

	private static boolean listType(
		final BindingRequest request,
		final HudValueType... itemTypes
	) {
		if (request.binding().valueType() != HudValueType.LIST || request.binding().itemType().isEmpty()) {
			return false;
		}
		for (HudValueType value : itemTypes) {
			if (request.binding().itemType().filter(value::equals).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private static Optional<ComponentValue> component(final RichText value) {
		try {
			JsonElement json = JsonParser.parseString(value.json());
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
				.result()
				.map(ComponentValue::new);
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<ComponentValue> componentValue(final String value) {
		try {
			JsonElement json = JsonParser.parseString(value);
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
				.result()
				.map(ComponentValue::new);
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<IdentifierValue> identifierValue(final String value) {
		try {
			return Optional.of(new IdentifierValue(Identifier.parse(value)));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Long> integral(final Tag tag) {
		return tag.asNumber().flatMap(value -> {
			double decimal = value.doubleValue();
			long integer = value.longValue();
			return Double.isFinite(decimal) && decimal == integer
				? Optional.of(integer)
				: Optional.empty();
		});
	}

	private static Optional<Boolean> booleanTag(final Tag tag) {
		Optional<Number> number = tag.asNumber();
		if (number.isPresent()) {
			int value = number.orElseThrow().intValue();
			return value == 0 || value == 1 ? Optional.of(value == 1) : Optional.empty();
		}
		return tag.asString().flatMap(value -> switch (value.toLowerCase(Locale.ROOT)) {
			case "true" -> Optional.of(true);
			case "false" -> Optional.of(false);
			default -> Optional.empty();
		});
	}

	private static boolean statisticType(
		final BindingRequest request,
		final TaskStatisticValueType type
	) {
		return switch (type) {
			case BOOLEAN -> type(request, HudValueType.BOOLEAN);
			case INTEGER -> numericType(request, false);
			case DURATION_TICKS -> numericType(request, true);
			case STRING -> type(request, HudValueType.STRING, HudValueType.COMPONENT);
			case IDENTIFIER -> type(request, HudValueType.IDENTIFIER, HudValueType.STRING);
			case PLAYER -> type(request, HudValueType.PLAYER);
		};
	}

	interface SourceBackend {
		long serverTick();

		boolean online(UUID playerId);

		Optional<PlayerIdentity> player(UUID playerId);

		boolean predicate(Identifier predicate, PredicateContext context);

		OptionalLong score(String objective, String holder);

		Optional<Tag> storage(Identifier storage, String path);

		Optional<Tag> entity(UUID playerId, String path);
	}

	record PlayerIdentity(UUID playerId, String name, boolean online) {
		PlayerIdentity {
			Objects.requireNonNull(playerId, "playerId");
			name = Objects.requireNonNull(name, "name");
		}
	}

	private static final class MinecraftSourceBackend implements SourceBackend {
		private final MinecraftServer server;
		private final DefinitionSnapshot definitions;

		private MinecraftSourceBackend(
			final MinecraftServer server,
			final DefinitionSnapshot definitions
		) {
			this.server = server;
			this.definitions = definitions;
		}

		@Override
		public long serverTick() {
			return this.server.getTickCount();
		}

		@Override
		public boolean online(final UUID playerId) {
			return this.server.getPlayerList().getPlayer(playerId) != null;
		}

		@Override
		public Optional<PlayerIdentity> player(final UUID playerId) {
			ServerPlayer online = this.server.getPlayerList().getPlayer(playerId);
			if (online != null) {
				return Optional.of(new PlayerIdentity(playerId, online.getScoreboardName(), true));
			}
			return this.server.services().nameToIdCache().get(playerId)
				.map(profile -> new PlayerIdentity(playerId, profile.name(), false));
		}

		@Override
		public boolean predicate(final Identifier predicate, final PredicateContext context) {
			try {
				return new FrozenPredicateEvaluator(this.server, this.definitions.predicateDocuments())
					.evaluate(predicate, context);
			} catch (RuntimeException ignored) {
				return false;
			}
		}

		@Override
		public OptionalLong score(final String objectiveName, final String holder) {
			try {
				Objective objective = this.server.getScoreboard().getObjective(objectiveName);
				if (objective == null) {
					return OptionalLong.empty();
				}
				ReadOnlyScoreInfo info = this.server.getScoreboard()
					.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective);
				return info == null ? OptionalLong.empty() : OptionalLong.of(info.value());
			} catch (RuntimeException ignored) {
				return OptionalLong.empty();
			}
		}

		@Override
		public Optional<Tag> storage(final Identifier storage, final String path) {
			try {
				Tag root = this.server.getCommandStorage().get(storage);
				List<Tag> matches = NbtPathArgument.NbtPath.of(path).get(root);
				return matches.size() == 1 ? Optional.of(matches.getFirst().copy()) : Optional.empty();
			} catch (CommandSyntaxException | RuntimeException ignored) {
				return Optional.empty();
			}
		}

		@Override
		public Optional<Tag> entity(final UUID playerId, final String path) {
			ServerPlayer player = this.server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				return Optional.empty();
			}
			try {
				CompoundTag root = new EntityDataAccessor(player).getData();
				List<Tag> matches = NbtPathArgument.NbtPath.of(path).get(root);
				return matches.size() == 1 ? Optional.of(matches.getFirst().copy()) : Optional.empty();
			} catch (CommandSyntaxException | RuntimeException ignored) {
				return Optional.empty();
			}
		}
	}

	private static final class EntryBudget {
		private int remaining = MAX_NBT_ENTRIES;

		private boolean take() {
			return this.remaining-- > 0;
		}
	}
}
