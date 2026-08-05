package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.ClearReason;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Identity;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Mode;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Scenario;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.CountdownProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.Projection;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionCode;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionMode;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Host-only, read-only authority for the isolated V3C development preview namespace.
 *
 * <p>Opening a surface freezes the currently accepted definition generation and active game
 * instance into a short-lived server-memory session. Refresh requests may select only the closed
 * enum presets and registered identifiers carried by {@link HudPreviewRequestC2SPayload}. This
 * runtime never commits world state, invokes a function or callback, appends history, emits a
 * countdown checkpoint, or touches the formal HUD epochs owned by {@link HudServerRuntime}.</p>
 */
public final class HudPreviewServerRuntime {
	/** A forgotten development preview cannot remain authorized indefinitely. */
	public static final long SESSION_TTL_TICKS = 20L * 60L * 10L;

	private static final Map<UUID, PlayerSessions> PLAYERS = new HashMap<>();

	private HudPreviewServerRuntime() {
	}

	public static void initializeForServer(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		clearForServer();
	}

	/** Process-local state only; stopping a server does not require a terminal packet. */
	public static void clearForServer() {
		PLAYERS.clear();
	}

	/**
	 * Reload invalidates every frozen preview generation but deliberately retains request replay
	 * gates and epoch counters for the still-connected clients.
	 */
	public static void dataPackReloaded(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		for (Map.Entry<UUID, PlayerSessions> entry : PLAYERS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			for (Surface surface : Surface.values()) {
				closeSession(player, entry.getValue().slot(surface), surface, ClearReason.REPLACED);
			}
		}
	}

	public static void playerDisconnected(final UUID playerId) {
		PLAYERS.remove(Objects.requireNonNull(playerId, "playerId"));
	}

	/** Caller must first enforce the Pixel TZZ handshake boundary. */
	public static void onRequest(
		final HudPreviewRequestC2SPayload request,
		final ServerPlayNetworking.Context context
	) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(context, "context");
		ServerPlayer player = context.player();
		MinecraftServer server = context.server();
		if (!NetworkProtocol.isCompatible(request.protocolVersion())) {
			return;
		}
		if (!currentHost(server, player.getUUID())) {
			revokePlayer(player, ClearReason.REVOKED);
			return;
		}
		if (
			!ServerPlayNetworking.canSend(player, HudPreviewReplaceS2CPayload.TYPE)
				|| !ServerPlayNetworking.canSend(player, HudPreviewClearS2CPayload.TYPE)
		) {
			return;
		}

		PlayerSessions playerSessions = PLAYERS.computeIfAbsent(
			player.getUUID(),
			ignored -> new PlayerSessions()
		);
		SurfaceSlot slot = playerSessions.slot(request.surface());
		if (request.requestSequence() <= slot.lastRequestSequence) {
			return;
		}
		slot.lastRequestSequence = request.requestSequence();

		switch (request.action()) {
			case CLOSE -> {
				if (slot.session != null && slot.session.previewId.equals(request.previewId())) {
					closeSession(player, slot, request.surface(), ClearReason.CLOSED);
				}
			}
			case OPEN -> open(server, player, slot, request);
			case REFRESH -> refresh(server, player, slot, request);
		}
	}

	public static void tick(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		long currentTick = server.getTickCount();
		UUID currentHost = PixelTzzWorldState.get(server).hostId().orElse(null);
		DefinitionRegistry.View view = DefinitionRegistry.INSTANCE.view();
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);

		for (Map.Entry<UUID, PlayerSessions> entry : PLAYERS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			if (!entry.getKey().equals(currentHost)) {
				for (Surface surface : Surface.values()) {
					closeSession(player, entry.getValue().slot(surface), surface, ClearReason.REVOKED);
				}
				continue;
			}

			for (Surface surface : Surface.values()) {
				SurfaceSlot slot = entry.getValue().slot(surface);
				PreviewSession session = slot.session;
				if (session == null) {
					continue;
				}
				if (expired(currentTick, session.lastActivityTick)) {
					closeSession(player, slot, surface, ClearReason.EXPIRED);
					continue;
				}
				if (!authorityAvailable(view, root, session.authority)) {
					closeSession(player, slot, surface, ClearReason.UNAVAILABLE);
				}
			}
		}
	}

	private static void open(
		final MinecraftServer server,
		final ServerPlayer player,
		final SurfaceSlot slot,
		final HudPreviewRequestC2SPayload request
	) {
		if (slot.session != null) {
			closeSession(player, slot, request.surface(), ClearReason.REPLACED);
		}
		Optional<FrozenAuthority> authority = freezeAuthority(server, request.surface());
		if (authority.isEmpty()) {
			sendUnavailable(player, slot, request);
			return;
		}
		long epoch = increment(slot.lastEpoch, "HUD preview epoch overflow");
		slot.lastEpoch = epoch;
		PreviewSession session = new PreviewSession(
			request.previewId(),
			epoch,
			0L,
			server.getTickCount(),
			authority.orElseThrow()
		);
		slot.session = session;
		publish(player, slot, request, session);
	}

	private static void refresh(
		final MinecraftServer server,
		final ServerPlayer player,
		final SurfaceSlot slot,
		final HudPreviewRequestC2SPayload request
	) {
		PreviewSession session = slot.session;
		if (session == null || !session.previewId.equals(request.previewId())) {
			return;
		}
		session.lastActivityTick = server.getTickCount();
		publish(player, slot, request, session);
	}

	private static void publish(
		final ServerPlayer player,
		final SurfaceSlot slot,
		final HudPreviewRequestC2SPayload request,
		final PreviewSession session
	) {
		Projection projection;
		try {
			projection = project(player, request, session.authority);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"V3C host preview failed closed for {} on {}: {}",
				player.getPlainTextName(),
				request.surface().name().toLowerCase(java.util.Locale.ROOT),
				safeMessage(error)
			);
			projection = null;
		}

		if (projection == null || projection.code() != ProjectionCode.REPLACE) {
			if (projection != null && projection.code() == ProjectionCode.FAIL_CLOSED) {
				PixelTzzPro.LOGGER.warn(
					"V3C host preview projection failed closed for {} on {}: {}",
					player.getPlainTextName(),
					request.surface().name().toLowerCase(java.util.Locale.ROOT),
					projection.diagnostics().stream().limit(4).toList()
				);
			}
			if (session.visible) {
				sendClear(player, session, request.surface(), ClearReason.UNAVAILABLE);
			}
			return;
		}

		byte[] document = projection.document().orElseThrow();
		if (request.surface() == Surface.COUNTDOWN) {
			document = freezeCountdownClocks(document);
		}
		session.sequence = increment(session.sequence, "HUD preview sequence overflow");
		session.visible = true;
		ServerPlayNetworking.send(
			player,
			new HudPreviewReplaceS2CPayload(
				session.previewId,
				request.surface(),
				session.epoch,
				session.sequence,
				document
			)
		);
		slot.session = session;
	}

	private static Projection project(
		final ServerPlayer player,
		final HudPreviewRequestC2SPayload request,
		final FrozenAuthority authority
	) {
		PreviewSelection selection = select(authority, request);
		Set<UUID> frozenParticipants = request.identity() == Identity.PARTICIPANT
			? Set.of(player.getUUID())
			: Set.of();
		HudRouteAuthority.Context audience = new HudRouteAuthority.Context(
			player.getUUID(),
			authority.game.id(),
			selection.phase.map(PhaseDefinition::id),
			selection.task.map(TaskDefinition::id),
			selection.task.map(value -> TaskKind.valueOf(value.kind().name())),
			taskStatus(request.scenario()),
			selection.role.map(RoleDefinition::id),
			selection.team.map(TeamDefinition::id),
			selection.lifeState.map(LifeStateDefinition::id),
			selection.role.map(RoleDefinition::tags).orElse(Set.of()),
			selection.team.map(TeamDefinition::tags).orElse(Set.of()),
			selection.lifeState.map(LifeStateDefinition::tags).orElse(Set.of()),
			request.identity() == Identity.HOST,
			request.identity() == Identity.PARTICIPANT,
			true,
			false,
			false,
			frozenParticipants
		);
		var source = HudPreviewBindingSourceAccess.create(
			authority.definitions,
			authority.game,
			selection.phase,
			selection.task,
			selection.role,
			selection.team,
			selection.lifeState,
			audience,
			request.boundary(),
			Math.max(0L, Objects.requireNonNull(player.level().getServer()).getTickCount())
		);
		ProjectionContext context = new ProjectionContext(
			audience,
			source,
			HudProjectionCompiler.AssetAvailability.assumeAvailable(),
			projectionMode(request.mode())
		);
		if (request.surface() == Surface.HUD) {
			return HudProjectionCompiler.projectDock(authority.definitions, authority.game, context);
		}
		CountdownDefinition countdown = authority.countdown.orElseThrow();
		CountdownFrame frame = countdownFrame(countdown, request.scenario());
		return HudProjectionCompiler.projectCountdown(
			authority.definitions,
			countdown,
			new CountdownProjectionContext(
				context,
				authority.previewCountdownId,
				Math.max(0L, Objects.requireNonNull(player.level().getServer()).getTickCount()),
				countdown.durationTicks(),
				frame.remainingTicks,
				frame.paused,
				frame.state,
				frame.waitingReason,
				frame.missingPlayerCount,
				countdown.name()
			)
		);
	}

	private static Optional<FrozenAuthority> freezeAuthority(
		final MinecraftServer server,
		final Surface surface
	) {
		DefinitionRegistry.View view = DefinitionRegistry.INSTANCE.view();
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (
			root == null
				|| root.activeGameInstanceId().isEmpty()
				|| root.core().activeGameId().isEmpty()
				|| !view.healthy()
				|| !view.active().usable()
		) {
			return Optional.empty();
		}
		DefinitionSnapshot definitions = view.active();
		GameDefinition game = definitions.games().get(root.core().activeGameId().orElseThrow());
		if (game == null || (surface == Surface.HUD && game.hudProfile().isEmpty())) {
			return Optional.empty();
		}
		Optional<CountdownDefinition> countdown = Optional.empty();
		if (surface == Surface.COUNTDOWN) {
			countdown = game.openingCountdown()
				.map(value -> definitions.hudCatalog().countdowns().get(value.definition()));
			if (countdown.isEmpty()) {
				return Optional.empty();
			}
		}
		return Optional.of(new FrozenAuthority(
			definitions,
			game,
			root.activeGameInstanceId().orElseThrow(),
			countdown,
			UUID.randomUUID()
		));
	}

	private static PreviewSelection select(
		final FrozenAuthority authority,
		final HudPreviewRequestC2SPayload request
	) {
		DefinitionSnapshot definitions = authority.definitions;
		Identifier gameId = authority.game.id();
		Optional<PhaseDefinition> phase = member(
			request.phaseId().or(() -> Optional.of(authority.game.initialPhase())),
			definitions.phases(),
			gameId,
			PhaseDefinition::game,
			"phase"
		);
		Optional<RoleDefinition> role = member(
			request.roleId().or(() -> Optional.of(authority.game.defaultRole())),
			definitions.roles(),
			gameId,
			RoleDefinition::game,
			"role"
		);
		Optional<TeamDefinition> team = member(
			request.teamId(),
			definitions.teams(),
			gameId,
			TeamDefinition::game,
			"team"
		);
		Optional<LifeStateDefinition> lifeState = member(
			request.lifeStateId().or(() -> Optional.of(authority.game.defaultLifeState())),
			definitions.lifeStates(),
			gameId,
			LifeStateDefinition::game,
			"life state"
		);

		Optional<TaskDefinition> task = Optional.empty();
		if (request.surface() == Surface.HUD && request.scenario() != Scenario.SETUP) {
			Optional<Identifier> requested = request.taskId();
			if (requested.isEmpty()) {
				requested = authority.game.taskTimeline().map(value -> value.initialTask());
			}
			if (requested.isEmpty()) {
				requested = definitions.tasks().values().stream()
					.filter(value -> value.game().equals(gameId))
					.map(TaskDefinition::id)
					.sorted(Comparator.comparing(Identifier::toString))
					.findFirst();
			}
			task = member(
				requested,
				definitions.tasks(),
				gameId,
				TaskDefinition::game,
				"task"
			);
			if (task.isEmpty()) {
				throw new IllegalArgumentException("preview task is unavailable");
			}
		}
		return new PreviewSelection(phase, task, role, team, lifeState);
	}

	private static <T> Optional<T> member(
		final Optional<Identifier> requested,
		final Map<Identifier, T> registered,
		final Identifier game,
		final java.util.function.Function<T, Identifier> owner,
		final String label
	) {
		if (requested.isEmpty()) {
			return Optional.empty();
		}
		T value = registered.get(requested.orElseThrow());
		if (value == null || !owner.apply(value).equals(game)) {
			throw new IllegalArgumentException("preview " + label + " is outside the active game");
		}
		return Optional.of(value);
	}

	private static Optional<TaskStatus> taskStatus(final Scenario scenario) {
		return switch (scenario) {
			case TASK_RUNNING -> Optional.of(TaskStatus.RUNNING);
			case TASK_PAUSED -> Optional.of(TaskStatus.PAUSED);
			case INTERMISSION -> Optional.of(TaskStatus.INTERVAL);
			case SETUP, COUNTDOWN_START, COUNTDOWN_KEY_SECOND, COUNTDOWN_PAUSED,
				COUNTDOWN_FINAL_TICK -> Optional.empty();
		};
	}

	private static ProjectionMode projectionMode(final Mode mode) {
		return switch (mode) {
			case NORMAL -> ProjectionMode.NORMAL;
			case COMPACT -> ProjectionMode.COMPACT;
			case SUMMARY -> ProjectionMode.SUMMARY;
		};
	}

	private static CountdownFrame countdownFrame(
		final CountdownDefinition countdown,
		final Scenario scenario
	) {
		long total = countdown.durationTicks();
		return switch (scenario) {
			case COUNTDOWN_START -> new CountdownFrame(total, false, "running", "", 0);
			case COUNTDOWN_KEY_SECOND -> new CountdownFrame(
				keySecond(countdown),
				false,
				"running",
				"",
				0
			);
			case COUNTDOWN_PAUSED -> new CountdownFrame(
				Math.max(1L, total / 2L),
				true,
				"waiting_for_players",
				"required_players_offline",
				1
			);
			case COUNTDOWN_FINAL_TICK -> new CountdownFrame(1L, false, "running", "", 0);
			default -> throw new IllegalArgumentException("non-countdown preview scenario");
		};
	}

	private static long keySecond(final CountdownDefinition countdown) {
		long total = countdown.durationTicks();
		long target = Math.min(total, 60L);
		return countdown.checkpoints().stream()
			.mapToLong(value -> value.remainingTicks())
			.filter(value -> value > 0L && value <= total)
			.boxed()
			.min(Comparator.comparingLong(value -> Math.abs(value - target)))
			.orElse(target);
	}

	/** Keep every preview clock at its selected scenario without forging the paused presentation. */
	private static byte[] freezeCountdownClocks(final byte[] document) {
		JsonObject root = JsonParser.parseString(new String(document, StandardCharsets.UTF_8))
			.getAsJsonObject();
		freezeTimer(root.get("remaining"));
		if (root.has("nodes") && root.get("nodes").isJsonArray()) {
			for (JsonElement value : root.getAsJsonArray("nodes")) {
				if (value.isJsonObject()) {
					freezeTimer(value.getAsJsonObject().get("timer"));
				}
			}
		}
		return root.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void freezeTimer(final JsonElement timer) {
		if (timer != null && timer.isJsonObject()) {
			timer.getAsJsonObject().addProperty("rate", 0.0D);
		}
	}

	private static void sendUnavailable(
		final ServerPlayer player,
		final SurfaceSlot slot,
		final HudPreviewRequestC2SPayload request
	) {
		long epoch = increment(slot.lastEpoch, "HUD preview epoch overflow");
		slot.lastEpoch = epoch;
		ServerPlayNetworking.send(
			player,
			new HudPreviewClearS2CPayload(
				request.previewId(),
				request.surface(),
				epoch,
				1L,
				ClearReason.UNAVAILABLE
			)
		);
	}

	private static void closeSession(
		final ServerPlayer player,
		final SurfaceSlot slot,
		final Surface surface,
		final ClearReason reason
	) {
		PreviewSession session = slot.session;
		if (session == null) {
			return;
		}
		if (player != null && session.visible && ServerPlayNetworking.canSend(player, HudPreviewClearS2CPayload.TYPE)) {
			sendClear(player, session, surface, reason);
		}
		slot.session = null;
	}

	private static void sendClear(
		final ServerPlayer player,
		final PreviewSession session,
		final Surface surface,
		final ClearReason reason
	) {
		session.sequence = increment(session.sequence, "HUD preview clear sequence overflow");
		session.visible = false;
		ServerPlayNetworking.send(
			player,
			new HudPreviewClearS2CPayload(
				session.previewId,
				surface,
				session.epoch,
				session.sequence,
				reason
			)
		);
	}

	private static void revokePlayer(final ServerPlayer player, final ClearReason reason) {
		PlayerSessions sessions = PLAYERS.get(player.getUUID());
		if (sessions == null) {
			return;
		}
		for (Surface surface : Surface.values()) {
			closeSession(player, sessions.slot(surface), surface, reason);
		}
	}

	private static boolean currentHost(final MinecraftServer server, final UUID playerId) {
		return PixelTzzWorldState.get(server).hostId().filter(playerId::equals).isPresent();
	}

	private static boolean authorityAvailable(
		final DefinitionRegistry.View view,
		final WorldStateV3 root,
		final FrozenAuthority authority
	) {
		return root != null
			&& view.healthy()
			&& view.active().usable()
			&& view.active().generation() == authority.definitions.generation()
			&& root.activeGameInstanceId().filter(authority.gameInstanceId::equals).isPresent()
			&& root.core().activeGameId().filter(authority.game.id()::equals).isPresent();
	}

	private static boolean expired(final long currentTick, final long lastActivityTick) {
		return currentTick < lastActivityTick
			|| currentTick - lastActivityTick >= SESSION_TTL_TICKS;
	}

	private static long increment(final long value, final String message) {
		try {
			return Math.incrementExact(value);
		} catch (ArithmeticException error) {
			throw new IllegalStateException(message, error);
		}
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return message.length() <= 256 ? message : message.substring(0, 256);
	}

	private record FrozenAuthority(
		DefinitionSnapshot definitions,
		GameDefinition game,
		UUID gameInstanceId,
		Optional<CountdownDefinition> countdown,
		UUID previewCountdownId
	) {
		private FrozenAuthority {
			definitions = Objects.requireNonNull(definitions, "definitions");
			game = Objects.requireNonNull(game, "game");
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			countdown = Objects.requireNonNull(countdown, "countdown");
			previewCountdownId = Objects.requireNonNull(previewCountdownId, "previewCountdownId");
		}
	}

	private record PreviewSelection(
		Optional<PhaseDefinition> phase,
		Optional<TaskDefinition> task,
		Optional<RoleDefinition> role,
		Optional<TeamDefinition> team,
		Optional<LifeStateDefinition> lifeState
	) {
		private PreviewSelection {
			phase = Objects.requireNonNull(phase, "phase");
			task = Objects.requireNonNull(task, "task");
			role = Objects.requireNonNull(role, "role");
			team = Objects.requireNonNull(team, "team");
			lifeState = Objects.requireNonNull(lifeState, "lifeState");
		}
	}

	private record CountdownFrame(
		long remainingTicks,
		boolean paused,
		String state,
		String waitingReason,
		int missingPlayerCount
	) {
	}

	private static final class PreviewSession {
		private final UUID previewId;
		private final long epoch;
		private long sequence;
		private long lastActivityTick;
		private final FrozenAuthority authority;
		private boolean visible;

		private PreviewSession(
			final UUID previewId,
			final long epoch,
			final long sequence,
			final long lastActivityTick,
			final FrozenAuthority authority
		) {
			this.previewId = Objects.requireNonNull(previewId, "previewId");
			this.epoch = epoch;
			this.sequence = sequence;
			this.lastActivityTick = lastActivityTick;
			this.authority = Objects.requireNonNull(authority, "authority");
		}
	}

	private static final class SurfaceSlot {
		private long lastRequestSequence;
		private long lastEpoch;
		private PreviewSession session;
	}

	private static final class PlayerSessions {
		private final EnumMap<Surface, SurfaceSlot> surfaces = new EnumMap<>(Surface.class);

		private SurfaceSlot slot(final Surface surface) {
			return this.surfaces.computeIfAbsent(surface, ignored -> new SurfaceSlot());
		}
	}
}
