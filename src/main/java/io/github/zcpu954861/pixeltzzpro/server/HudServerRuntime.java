package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownCheckpoint;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownSound;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudComponentDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudLayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudProfileDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownCheckpointSoundS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload.Surface;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.CountdownProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.Projection;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionCode;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionContext;
import io.github.zcpu954861.pixeltzzpro.server.HudProjectionCompiler.ProjectionMode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV5;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * Per-player transport authority for the single persistent dock and the single opening-countdown
 * overlay.
 *
 * <p>This boundary owns ordering, replacement/patch decisions, revocation clears and resync
 * throttling. It never executes a data-pack function and never sends binding source descriptors.
 * The only bytes admitted here are the already-minimized documents produced by
 * {@link HudProjectionCompiler}.</p>
 */
public final class HudServerRuntime {
	private static final int PERIODIC_REFRESH_TICKS = 20;
	private static final int RESYNC_THROTTLE_TICKS = 40;
	private static final Map<UUID, PlayerState> PLAYERS = new HashMap<>();
	private static final Set<UUID> AUTHORIZED_PLAYERS = new LinkedHashSet<>();
	private static int lastPeriodicTick = Integer.MIN_VALUE;

	private HudServerRuntime() {
	}

	public static void initializeForServer(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		clearForServer();
	}

	public static void clearForServer() {
		PLAYERS.clear();
		AUTHORIZED_PLAYERS.clear();
		lastPeriodicTick = Integer.MIN_VALUE;
	}

	public static void playerAuthorityReady(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(player, "player");
		AUTHORIZED_PLAYERS.add(player.getUUID());
		PLAYERS.remove(player.getUUID());
		refreshPlayer(server, player, RefreshCause.HANDSHAKE);
	}

	public static void playerDisconnected(final UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		AUTHORIZED_PLAYERS.remove(playerId);
		PLAYERS.remove(playerId);
	}

	public static void tick(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		int currentTick = server.getTickCount();
		if (
			lastPeriodicTick != Integer.MIN_VALUE
				&& currentTick >= lastPeriodicTick
				&& currentTick - lastPeriodicTick < PERIODIC_REFRESH_TICKS
		) {
			return;
		}
		lastPeriodicTick = currentTick;
		forEachAuthorized(server, player -> refreshDock(server, player, RefreshCause.PERIODIC));
	}

	public static void refreshAll(
		final MinecraftServer server,
		final RefreshCause cause
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(cause, "cause");
		forEachAuthorized(server, player -> refreshPlayer(server, player, cause));
	}

	public static void refreshPlayer(
		final MinecraftServer server,
		final ServerPlayer player,
		final RefreshCause cause
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(cause, "cause");
		if (!AUTHORIZED_PLAYERS.contains(player.getUUID())) {
			return;
		}
		refreshDock(server, player, cause);
		refreshCountdown(server, player, cause);
	}

	/** Caller must first enforce the Pixel TZZ handshake boundary. */
	public static void onResyncRequest(
		final HudResyncRequestC2SPayload request,
		final ServerPlayNetworking.Context context
	) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(context, "context");
		ServerPlayer player = context.player();
		if (!AUTHORIZED_PLAYERS.contains(player.getUUID())) {
			return;
		}
		PlayerState state = PLAYERS.get(player.getUUID());
		if (state == null) {
			return;
		}
		SurfaceState surface = state.surface(request.surface());
		if (surface == null) {
			return;
		}
		if (
			!surface.gameInstanceId.equals(request.gameInstanceId())
				|| surface.definitionGeneration != request.definitionGeneration()
				|| surface.epoch != request.epoch()
				|| surface.contextVersion != request.contextVersion()
				|| request.appliedSequence() > surface.sequence
		) {
			return;
		}
		ResyncNamespace namespace = ResyncNamespace.of(request.surface(), surface);
		int currentTick = context.server().getTickCount();
		int previous = state.lastResyncTick.getOrDefault(namespace, Integer.MIN_VALUE);
		if (
			previous != Integer.MIN_VALUE
				&& currentTick >= previous
				&& currentTick - previous < RESYNC_THROTTLE_TICKS
		) {
			return;
		}
		state.lastResyncTick.put(namespace, currentTick);
		long sequence = increment(surface.sequence, "HUD sequence overflow");
		if (sequence < 0L) {
			return;
		}
		surface.sequence = sequence;
		if (surface.cleared || surface.document == null) {
			sendClear(player, request.surface(), surface);
		} else {
			sendReplace(player, request.surface(), surface, surface.document);
		}
	}

	/**
	 * Publishes a committed countdown transition before projecting its optional checkpoint sound or
	 * cue. Resync and ordinary refresh paths never call this method.
	 */
	public static void countdownTransitionCommitted(
		final MinecraftServer server,
		final Instance before,
		final Instance after,
		final Optional<io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition>
			checkpoint
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(checkpoint, "checkpoint");
		if (before != null && !before.countdownInstanceId().equals(after.countdownInstanceId())) {
			throw new IllegalArgumentException("countdown transition changed instance identity");
		}
		refreshAll(server, RefreshCause.COUNTDOWN_TRANSITION);
		checkpoint.ifPresent(value -> projectCheckpoint(server, after, value.id()));
	}

	public static void countdownRetired(
		final MinecraftServer server,
		final Instance terminal
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(terminal, "terminal");
		forEachAuthorized(server, player -> {
			PlayerState playerState = PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
			SurfaceState surface = playerState.countdown;
			if (
				surface == null
					|| !terminal.countdownInstanceId().equals(surface.countdownInstanceId)
					|| surface.cleared
			) {
				return;
			}
			surface.contextVersion = Math.max(
				surface.contextVersion,
				terminal.lifecycle().stateVersion()
			);
			surface.sequence = incrementOrThrow(surface.sequence, "countdown clear sequence overflow");
			surface.cleared = true;
			surface.document = null;
			surface.structureSha256 = "";
			sendClear(player, Surface.COUNTDOWN, surface);
		});
	}

	private static void refreshDock(
		final MinecraftServer server,
		final ServerPlayer player,
		final RefreshCause cause
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		DefinitionRegistry.View view = DefinitionRegistry.INSTANCE.view();
		if (
			root == null
				|| root.activeGameInstanceId().isEmpty()
				|| root.core().activeGameId().isEmpty()
				|| !view.healthy()
				|| !view.active().usable()
		) {
			clearExisting(player, Surface.HUD, root == null ? 0L : root.stateRevision());
			return;
		}
		DefinitionSnapshot definitions = view.active();
		GameDefinition game = definitions.games().get(root.core().activeGameId().orElseThrow());
		if (game == null) {
			clearExisting(player, Surface.HUD, root.stateRevision());
			return;
		}
		HudRouteAuthority.Context audience = audienceContext(
			server,
			definitions,
			root,
			player,
			frozenTimelineParticipants(root)
		);
		Projection projection;
		try {
			projection = HudProjectionCompiler.projectDock(
				definitions,
				game,
				new ProjectionContext(
					audience,
					HudMinecraftBindingSourceAccess.create(server, definitions, root, player),
					HudProjectionCompiler.AssetAvailability.assumeAvailable(),
					ProjectionMode.NORMAL
				)
			);
		} catch (RuntimeException error) {
			projection = failClosed("dock projection failed: " + safeMessage(error));
		}
		applyProjection(
			player,
			Surface.HUD,
			new Identity(root.activeGameInstanceId().orElseThrow(), definitions.generation(), null),
			-1L,
			projection,
			cause
		);
	}

	private static void refreshCountdown(
		final MinecraftServer server,
		final ServerPlayer player,
		final RefreshCause cause
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV5 rootV5 = wrapper.currentV5().orElse(null);
		Instance countdown = rootV5 == null ? null : rootV5.countdown().orElse(null);
		if (countdown == null) {
			clearExisting(player, Surface.COUNTDOWN, wrapper.stateRevision());
			return;
		}
		WorldStateV3 root = rootV5.core().core();
		Identifier activeGameId = root.core().activeGameId().orElse(null);
		if (
			activeGameId == null
				|| root.activeGameInstanceId().filter(countdown.gameInstanceId()::equals).isEmpty()
		) {
			logDiagnostics(
				Surface.COUNTDOWN,
				player,
				List.of("persisted countdown has no matching active game authority")
			);
			clearExisting(player, Surface.COUNTDOWN, countdown.lifecycle().stateVersion());
			return;
		}
		CountdownSnapshotCompiler.RestoreResult restoredResult = CountdownSnapshotCompiler.restore(
			activeGameId,
			countdown.definition().definitionId(),
			countdown.definition().closure()
		);
		if (!restoredResult.success()) {
			logDiagnostics(
				Surface.COUNTDOWN,
				player,
				List.of("frozen countdown restore failed: " + restoredResult.code())
			);
			clearExisting(player, Surface.COUNTDOWN, countdown.lifecycle().stateVersion());
			return;
		}
		var restored = restoredResult.restored().orElseThrow();
		DefinitionSnapshot definitions = restored.definitions();
		CountdownDefinition definition = restored.countdown();
		Set<UUID> frozen = countdown.participants()
			.stream()
			.map(value -> value.playerId())
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		HudRouteAuthority.Context audience = audienceContext(
			server,
			definitions,
			root,
			player,
			frozen
		);
		Set<UUID> online = onlineIds(server);
		int missing = (int)countdown.participants()
			.stream()
			.filter(value -> value.required() && !online.contains(value.playerId()))
			.count();
		CountdownState lifecycle = countdown.lifecycle().state();
		boolean paused = !CountdownAuthority.clockAdvancing(countdown);
		Projection projection;
		try {
			projection = HudProjectionCompiler.projectCountdown(
				definitions,
				definition,
				new CountdownProjectionContext(
					new ProjectionContext(
						audience,
						HudMinecraftBindingSourceAccess.create(server, definitions, root, player),
						HudProjectionCompiler.AssetAvailability.assumeAvailable(),
						ProjectionMode.NORMAL
					),
					countdown.countdownInstanceId(),
					server.getTickCount(),
					countdown.totalTicks(),
					countdown.lifecycle().remainingTicks(),
					paused,
					lifecycle.getSerializedName(),
					waitingReason(lifecycle),
					missing,
					definition.name()
				)
			);
		} catch (RuntimeException error) {
			projection = failClosed("countdown projection failed: " + safeMessage(error));
		}
		applyProjection(
			player,
			Surface.COUNTDOWN,
			new Identity(
				countdown.gameInstanceId(),
				countdown.definition().generation(),
				countdown.countdownInstanceId()
			),
			countdown.lifecycle().stateVersion(),
			projection,
			cause
		);
	}

	private static void applyProjection(
		final ServerPlayer player,
		final Surface surface,
		final Identity identity,
		final long authoritativeContext,
		final Projection projection,
		final RefreshCause cause
	) {
		PlayerState playerState = PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		SurfaceState previous = playerState.surface(surface);
		if (projection.code() != ProjectionCode.REPLACE) {
			if (projection.code() == ProjectionCode.FAIL_CLOSED) {
				logDiagnostics(surface, player, projection.diagnostics());
			}
			if (previous == null || previous.cleared) {
				if (previous == null) {
					SurfaceState clear = fresh(identity, Math.max(0L, authoritativeContext), 1L);
					clear.cleared = true;
					playerState.set(surface, clear);
					sendClear(player, surface, clear);
				}
				return;
			}
			previous.contextVersion = Math.max(
				previous.contextVersion + 1L,
				Math.max(0L, authoritativeContext)
			);
			previous.sequence = incrementOrThrow(previous.sequence, "HUD clear sequence overflow");
			previous.cleared = true;
			previous.document = null;
			previous.structureSha256 = "";
			playerState.retainResyncNamespace(surface, previous);
			sendClear(player, surface, previous);
			return;
		}

		byte[] document = projection.document().orElseThrow();
		String structure = structureSha256(document, surface);
		boolean identityChanged = previous == null || !previous.matches(identity);
		long context = surface == Surface.COUNTDOWN
			? Math.max(0L, authoritativeContext)
			: identityChanged
				? 0L
				: previous.structureSha256.equals(structure)
					? previous.contextVersion
					: incrementOrThrow(previous.contextVersion, "HUD context version overflow");
		if (
			!identityChanged
				&& !previous.cleared
				&& previous.contextVersion == context
				&& Arrays.equals(previous.document, document)
		) {
			return;
		}

		if (identityChanged) {
			long epoch = previous == null
				? 1L
				: incrementOrThrow(previous.epoch, "HUD epoch overflow");
			SurfaceState next = fresh(identity, context, epoch);
			next.document = document.clone();
			next.structureSha256 = structure;
			playerState.set(surface, next);
			sendReplace(player, surface, next, document);
			return;
		}

		boolean patch = !previous.cleared
			&& previous.contextVersion == context
			&& previous.structureSha256.equals(structure)
			&& cause != RefreshCause.HANDSHAKE
			&& (surface == Surface.HUD
				? ServerPlayNetworking.canSend(player, HudPatchS2CPayload.TYPE)
				: ServerPlayNetworking.canSend(player, CountdownPatchS2CPayload.TYPE));
		long baseSequence = previous.sequence;
		long sequence = incrementOrThrow(previous.sequence, "HUD sequence overflow");
		if (patch) {
			byte[] patchDocument = patchDocument(document, surface);
			if (patchDocument.length <= HudPatchS2CPayload.MAX_PATCH_BYTES) {
				previous.sequence = sequence;
				previous.document = document.clone();
				previous.structureSha256 = structure;
				previous.cleared = false;
				if (surface == Surface.HUD) {
					ServerPlayNetworking.send(
						player,
						new HudPatchS2CPayload(
							previous.gameInstanceId,
							previous.definitionGeneration,
							previous.epoch,
							previous.contextVersion,
							baseSequence,
							sequence,
							patchDocument
						)
					);
				} else {
					ServerPlayNetworking.send(
						player,
						new CountdownPatchS2CPayload(
							previous.gameInstanceId,
							previous.definitionGeneration,
							previous.epoch,
							previous.contextVersion,
							baseSequence,
							sequence,
							patchDocument
						)
					);
				}
				return;
			}
		}

		previous.contextVersion = context;
		previous.sequence = sequence;
		previous.document = document.clone();
		previous.structureSha256 = structure;
		previous.cleared = false;
		playerState.retainResyncNamespace(surface, previous);
		sendReplace(player, surface, previous, document);
	}

	private static SurfaceState fresh(
		final Identity identity,
		final long contextVersion,
		final long epoch
	) {
		return new SurfaceState(
			identity.gameInstanceId(),
			identity.definitionGeneration(),
			epoch,
			contextVersion,
			1L,
			identity.countdownInstanceId(),
			false,
			null,
			""
		);
	}

	private static void clearExisting(
		final ServerPlayer player,
		final Surface surface,
		final long contextHint
	) {
		PlayerState state = PLAYERS.get(player.getUUID());
		SurfaceState existing = state == null ? null : state.surface(surface);
		if (existing == null || existing.cleared) {
			return;
		}
		existing.contextVersion = Math.max(
			existing.contextVersion + 1L,
			Math.max(0L, contextHint)
		);
		existing.sequence = incrementOrThrow(existing.sequence, "HUD clear sequence overflow");
		existing.cleared = true;
		existing.document = null;
		existing.structureSha256 = "";
		state.retainResyncNamespace(surface, existing);
		sendClear(player, surface, existing);
	}

	private static void sendReplace(
		final ServerPlayer player,
		final Surface surface,
		final SurfaceState state,
		final byte[] document
	) {
		if (surface == Surface.HUD) {
			if (ServerPlayNetworking.canSend(player, HudReplaceS2CPayload.TYPE)) {
				ServerPlayNetworking.send(
					player,
					new HudReplaceS2CPayload(
						state.gameInstanceId,
						state.definitionGeneration,
						state.epoch,
						state.contextVersion,
						state.sequence,
						document
					)
				);
			}
		} else if (ServerPlayNetworking.canSend(player, CountdownReplaceS2CPayload.TYPE)) {
			ServerPlayNetworking.send(
				player,
				new CountdownReplaceS2CPayload(
					state.gameInstanceId,
					state.definitionGeneration,
					state.epoch,
					state.contextVersion,
					state.sequence,
					document
				)
			);
		}
	}

	private static void sendClear(
		final ServerPlayer player,
		final Surface surface,
		final SurfaceState state
	) {
		if (surface == Surface.HUD) {
			if (ServerPlayNetworking.canSend(player, HudClearS2CPayload.TYPE)) {
				ServerPlayNetworking.send(
					player,
					new HudClearS2CPayload(
						state.gameInstanceId,
						state.definitionGeneration,
						state.epoch,
						state.contextVersion,
						state.sequence
					)
				);
			}
		} else if (ServerPlayNetworking.canSend(player, CountdownClearS2CPayload.TYPE)) {
			ServerPlayNetworking.send(
				player,
				new CountdownClearS2CPayload(
					state.gameInstanceId,
					state.definitionGeneration,
					state.epoch,
					state.contextVersion,
					state.sequence
				)
			);
		}
	}

	private static void projectCheckpoint(
		final MinecraftServer server,
		final Instance countdown,
		final String checkpointId
	) {
		Identifier activeGameId = PixelTzzWorldState.get(server).activeGameId().orElse(null);
		if (activeGameId == null) {
			return;
		}
		var restoredResult = CountdownSnapshotCompiler.restore(
			activeGameId,
			countdown.definition().definitionId(),
			countdown.definition().closure()
		);
		if (!restoredResult.success()) {
			return;
		}
		var restored = restoredResult.restored().orElseThrow();
		CountdownCheckpoint checkpoint = restored.countdown()
			.checkpoints()
			.stream()
			.filter(value -> value.id().equals(checkpointId))
			.findFirst()
			.orElse(null);
		if (checkpoint == null) {
			return;
		}
		Set<UUID> recipients = new LinkedHashSet<>();
		for (UUID playerId : AUTHORIZED_PLAYERS) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			PlayerState playerState = PLAYERS.get(playerId);
			SurfaceState surface = playerState == null ? null : playerState.countdown;
			if (
				player != null
					&& surface != null
					&& !surface.cleared
					&& countdown.countdownInstanceId().equals(surface.countdownInstanceId)
			) {
				recipients.add(playerId);
				checkpoint.sound().ifPresent(sound -> sendCheckpointSound(
					player,
					countdown,
					checkpoint.id(),
					sound
				));
			}
		}
		checkpoint.cue().ifPresent(cue -> MessageServerRuntime.playHook(
			server,
			restored.definitions(),
			cue,
			Set.copyOf(recipients),
			Optional.empty(),
			Map.of(
				"countdown_id", restored.countdown().id().toString(),
				"countdown_instance_id", countdown.countdownInstanceId().toString(),
				"checkpoint_id", checkpoint.id(),
				"remaining_ticks", Long.toString(countdown.lifecycle().remainingTicks())
			)
		));
	}

	private static void sendCheckpointSound(
		final ServerPlayer player,
		final Instance countdown,
		final String checkpointId,
		final CountdownSound sound
	) {
		if (!ServerPlayNetworking.canSend(player, CountdownCheckpointSoundS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(
			player,
			new CountdownCheckpointSoundS2CPayload(
				countdown.countdownInstanceId(),
				countdown.lifecycle().stateVersion(),
				checkpointId,
				sound.event(),
				(float)sound.volume(),
				(float)sound.pitch()
			)
		);
	}

	private static HudRouteAuthority.Context audienceContext(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final ServerPlayer player,
		final Set<UUID> frozenParticipants
	) {
		WorldStateV2 core = state.core();
		PlayerRecord record = core.players().get(player.getUUID());
		var role = record == null ? null : definitions.roles().get(record.roleId());
		var team = record == null || record.teamId().isEmpty()
			? null
			: definitions.teams().get(record.teamId().orElseThrow());
		var life = record == null ? null : definitions.lifeStates().get(record.lifeStateId());
		TaskInstance task = state.timeline().flatMap(TimelineInstance::currentTask).orElse(null);
		return new HudRouteAuthority.Context(
			player.getUUID(),
			core.activeGameId().orElseThrow(),
			core.activePhaseId(),
			Optional.ofNullable(task).map(TaskInstance::taskId),
			Optional.ofNullable(task).map(value -> value.kind() == WorldStateV3.TaskKind.WARMUP
				? TaskKind.WARMUP
				: TaskKind.MAIN),
			taskStatus(state, task),
			Optional.ofNullable(record).map(PlayerRecord::roleId),
			record == null ? Optional.empty() : record.teamId(),
			Optional.ofNullable(record).map(PlayerRecord::lifeStateId),
			role == null ? Set.of() : role.tags(),
			team == null ? Set.of() : team.tags(),
			life == null ? Set.of() : life.tags(),
			core.host().map(value -> value.playerId().equals(player.getUUID())).orElse(false),
			frozenParticipants.contains(player.getUUID()) || record != null,
			server.getPlayerList().getPlayer(player.getUUID()) != null,
			false,
			false,
			frozenParticipants
		);
	}

	private static Optional<TaskStatus> taskStatus(
		final WorldStateV3 state,
		final TaskInstance task
	) {
		if (task == null) {
			return Optional.empty();
		}
		TimelineInstance timeline = state.timeline().orElse(null);
		if (timeline != null && timeline.paused()) {
			return Optional.of(TaskStatus.PAUSED);
		}
		if (timeline != null && timeline.status() == WorldStateV3.TimelineStatus.INTERMISSION) {
			return Optional.of(TaskStatus.INTERVAL);
		}
		return switch (task.status()) {
			case STARTING -> Optional.of(TaskStatus.STARTING);
			case RUNNING -> Optional.of(TaskStatus.RUNNING);
			case SETTLING -> Optional.of(TaskStatus.SETTLING);
			case SETTLED -> Optional.of(TaskStatus.COMPLETED);
			case INTERRUPTED -> Optional.of(TaskStatus.INTERRUPTED);
			case BLOCKED -> Optional.empty();
		};
	}

	private static Set<UUID> frozenTimelineParticipants(final WorldStateV3 state) {
		return state.timeline()
			.flatMap(TimelineInstance::currentTask)
			.map(TaskInstance::participants)
			.map(Set::copyOf)
			.orElseGet(Set::of);
	}

	private static Set<UUID> onlineIds(final MinecraftServer server) {
		return server.getPlayerList()
			.getPlayers()
			.stream()
			.map(ServerPlayer::getUUID)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private static String waitingReason(final CountdownState state) {
		return switch (state) {
			case WAITING_FOR_PLAYERS -> "required_players_offline";
			case RECOVERY_WAIT -> "recovery_coordination";
			case CANCELING -> "cancel_callbacks";
			case COMPLETING -> "complete_callbacks";
			default -> "";
		};
	}

	private static byte[] patchDocument(final byte[] document, final Surface surface) {
		JsonObject full = JsonParser.parseString(new String(document, StandardCharsets.UTF_8))
			.getAsJsonObject();
		JsonObject patch = new JsonObject();
		patch.addProperty("format_version", 1);
		patch.addProperty("surface", surface == Surface.HUD ? "dock" : "countdown");
		patch.add("nodes", full.get("nodes").deepCopy());
		if (surface == Surface.COUNTDOWN) {
			patch.add("remaining", full.get("remaining").deepCopy());
		}
		return patch.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String structureSha256(final byte[] document, final Surface surface) {
		JsonObject root = JsonParser.parseString(new String(document, StandardCharsets.UTF_8))
			.getAsJsonObject();
		if (surface == Surface.COUNTDOWN) {
			root.remove("remaining");
		}
		JsonArray nodes = root.getAsJsonArray("nodes");
		for (JsonElement value : nodes) {
			JsonObject node = value.getAsJsonObject();
			for (String key : List.of(
				"value",
				"player_uuid",
				"player_name",
				"offline",
				"current",
				"maximum",
				"label",
				"color",
				"timer"
			)) {
				node.remove(key);
			}
		}
		return sha256(root.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256(final byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static Projection failClosed(final String diagnostic) {
		return new Projection(
			ProjectionCode.FAIL_CLOSED,
			Optional.empty(),
			Optional.empty(),
			List.of(diagnostic)
		);
	}

	private static void logDiagnostics(
		final Surface surface,
		final ServerPlayer player,
		final List<String> diagnostics
	) {
		if (diagnostics.isEmpty()) {
			return;
		}
		PixelTzzPro.LOGGER.warn(
			"V3C {} projection failed closed for {}: {}",
			surface.name().toLowerCase(java.util.Locale.ROOT),
			player.getPlainTextName(),
			diagnostics.stream().limit(4).toList()
		);
	}

	private static String safeMessage(final RuntimeException error) {
		String value = error.getMessage();
		return value == null || value.isBlank()
			? error.getClass().getSimpleName()
			: value.length() <= 256 ? value : value.substring(0, 256);
	}

	private static long increment(final long value, final String message) {
		try {
			return Math.incrementExact(value);
		} catch (ArithmeticException error) {
			PixelTzzPro.LOGGER.error(message, error);
			return -1L;
		}
	}

	private static long incrementOrThrow(final long value, final String message) {
		try {
			return Math.incrementExact(value);
		} catch (ArithmeticException error) {
			throw new IllegalStateException(message, error);
		}
	}

	private static void forEachAuthorized(
		final MinecraftServer server,
		final java.util.function.Consumer<ServerPlayer> action
	) {
		for (UUID playerId : List.copyOf(AUTHORIZED_PLAYERS)) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				action.accept(player);
			}
		}
	}

	public enum RefreshCause {
		HANDSHAKE,
		AUTHORITY_CHANGE,
		DATA_PACK_RELOAD,
		PERIODIC,
		COUNTDOWN_TRANSITION,
		RESYNC
	}

	private record Identity(
		UUID gameInstanceId,
		long definitionGeneration,
		UUID countdownInstanceId
	) {
		private Identity {
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			if (definitionGeneration < 0L) {
				throw new IllegalArgumentException("definitionGeneration cannot be negative");
			}
		}
	}

	private record ResyncNamespace(
		Surface surface,
		UUID gameInstanceId,
		long definitionGeneration,
		long epoch,
		long contextVersion
	) {
		private static ResyncNamespace of(final Surface surface, final SurfaceState state) {
			return new ResyncNamespace(
				surface,
				state.gameInstanceId,
				state.definitionGeneration,
				state.epoch,
				state.contextVersion
			);
		}
	}

	private static final class SurfaceState {
		private final UUID gameInstanceId;
		private final long definitionGeneration;
		private final long epoch;
		private long contextVersion;
		private long sequence;
		private final UUID countdownInstanceId;
		private boolean cleared;
		private byte[] document;
		private String structureSha256;

		private SurfaceState(
			final UUID gameInstanceId,
			final long definitionGeneration,
			final long epoch,
			final long contextVersion,
			final long sequence,
			final UUID countdownInstanceId,
			final boolean cleared,
			final byte[] document,
			final String structureSha256
		) {
			this.gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			this.definitionGeneration = definitionGeneration;
			this.epoch = epoch;
			this.contextVersion = contextVersion;
			this.sequence = sequence;
			this.countdownInstanceId = countdownInstanceId;
			this.cleared = cleared;
			this.document = document == null ? null : document.clone();
			this.structureSha256 = Objects.requireNonNull(structureSha256, "structureSha256");
		}

		private boolean matches(final Identity identity) {
			return this.gameInstanceId.equals(identity.gameInstanceId())
				&& this.definitionGeneration == identity.definitionGeneration()
				&& Objects.equals(this.countdownInstanceId, identity.countdownInstanceId());
		}
	}

	private static final class PlayerState {
		private SurfaceState dock;
		private SurfaceState countdown;
		private final Map<ResyncNamespace, Integer> lastResyncTick = new HashMap<>();

		private SurfaceState surface(final Surface surface) {
			return surface == Surface.HUD ? this.dock : this.countdown;
		}

		private void set(final Surface surface, final SurfaceState value) {
			this.lastResyncTick.keySet().removeIf(key -> key.surface() == surface);
			if (surface == Surface.HUD) {
				this.dock = value;
			} else {
				this.countdown = value;
			}
		}

		private void retainResyncNamespace(final Surface surface, final SurfaceState value) {
			ResyncNamespace current = ResyncNamespace.of(surface, value);
			this.lastResyncTick.keySet().removeIf(
				key -> key.surface() == surface && !key.equals(current)
			);
		}
	}
}
