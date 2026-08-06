package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonObject;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownCallbackSlot;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownSound;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.DisplayAudience;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler.RestoredCountdown;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownCheckpointSoundS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec.EncodingResult;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec.ProjectionFrame;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownResyncRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV5;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * Dedicated server projection for the single authoritative countdown surface.
 *
 * <p>This runtime owns only bounded Replace/Patch/Clear delivery. It never reads the live countdown
 * catalog after approval: presentation, audience and checkpoint cues are restored from the frozen
 * closure retained by the persisted instance.</p>
 */
public final class CountdownProjectionRuntime {
	private static final Identifier SPECTATOR_ROLE_TAG = Identifier.parse("pixel_tzz:spectator");
	private static final long HEARTBEAT_INTERVAL_TICKS = 20L;
	private static final long RESYNC_THROTTLE_TICKS = 20L;
	private static final long CHECKPOINT_PRESENTATION_TICKS = 20L;

	private static MinecraftServer boundServer;
	private static final Set<UUID> READY_PLAYERS = new HashSet<>();
	private static final Map<UUID, RecipientChannel> CHANNELS = new HashMap<>();
	private static final Map<UUID, Long> LAST_RESYNC_TICKS = new HashMap<>();
	private static final Map<UUID, String> PROJECTION_DIAGNOSTICS = new HashMap<>();
	private static UUID trackedCountdownId;
	private static long epoch;
	private static long lastHeartbeatTick = Long.MIN_VALUE;
	private static RestoreCache restoreCache;
	private static Optional<JsonObject> projectedCheckpoint = Optional.empty();
	private static long checkpointExpiresAtTick = Long.MIN_VALUE;
	private static String handoffFailure = "";

	private CountdownProjectionRuntime() {
	}

	public static void initializeForServer(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		clearForServer();
		boundServer = server;
		synchronizeIdentity(activeCountdown(server));
	}

	public static void clearForServer() {
		boundServer = null;
		READY_PLAYERS.clear();
		CHANNELS.clear();
		LAST_RESYNC_TICKS.clear();
		PROJECTION_DIAGNOSTICS.clear();
		trackedCountdownId = null;
		epoch = 0L;
		lastHeartbeatTick = Long.MIN_VALUE;
		restoreCache = null;
		projectedCheckpoint = Optional.empty();
		checkpointExpiresAtTick = Long.MIN_VALUE;
		handoffFailure = "";
	}

	public static void tick(final MinecraftServer server) {
		ensureBound(server);
		Instance countdown = activeCountdown(server);
		boolean identityChanged = synchronizeIdentity(countdown);
		long tick = server.getTickCount();
		boolean checkpointExpired = projectedCheckpoint.isPresent()
			&& handoffFailure.isEmpty()
			&& tick >= checkpointExpiresAtTick;
		if (checkpointExpired) {
			projectedCheckpoint = Optional.empty();
			checkpointExpiresAtTick = Long.MIN_VALUE;
		}
		if (identityChanged) {
			refreshAll(server, RefreshCause.COUNTDOWN_TRANSITION);
			return;
		}
		if (countdown == null) {
			clearVisibleRecipients();
			return;
		}
		if (
			checkpointExpired
				|| lastHeartbeatTick == Long.MIN_VALUE
				|| tick - lastHeartbeatTick >= HEARTBEAT_INTERVAL_TICKS
		) {
			lastHeartbeatTick = tick;
			projectAll(server, countdown, false);
		}
	}

	public static void playerAuthorityReady(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		ensureBound(server);
		Objects.requireNonNull(player, "player");
		READY_PLAYERS.add(player.getUUID());
		Instance countdown = activeCountdown(server);
		synchronizeIdentity(countdown);
		projectRecipient(server, countdown, player, true);
	}

	public static void playerDisconnected(final UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		READY_PLAYERS.remove(playerId);
		CHANNELS.remove(playerId);
		LAST_RESYNC_TICKS.remove(playerId);
		PROJECTION_DIAGNOSTICS.remove(playerId);
	}

	public static void onResyncRequest(
		final CountdownResyncRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		Objects.requireNonNull(payload, "payload");
		Objects.requireNonNull(context, "context");
		MinecraftServer server = context.server();
		ServerPlayer player = context.player();
		ensureBound(server);
		if (!READY_PLAYERS.contains(player.getUUID())) {
			return;
		}
		long tick = server.getTickCount();
		long previous = LAST_RESYNC_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
		if (previous != Long.MIN_VALUE && tick - previous < RESYNC_THROTTLE_TICKS) {
			return;
		}
		LAST_RESYNC_TICKS.put(player.getUUID(), tick);

		Instance countdown = activeCountdown(server);
		synchronizeIdentity(countdown);
		if (countdown != null && authorizedRecipients(server, countdown).contains(player.getUUID())) {
			if (payload.appliedSequence() >= Long.MAX_VALUE - 1L) {
				return;
			}
			RecipientChannel channel = CHANNELS.computeIfAbsent(
				player.getUUID(),
				ignored -> new RecipientChannel()
			);
			if (resyncEnvelopeMatches(countdown, payload, epoch)) {
				channel.raiseSequenceFloor(countdown, epoch, payload.appliedSequence());
			}
			projectRecipient(server, countdown, player, true);
			return;
		}
		RecipientChannel channel = CHANNELS.get(player.getUUID());
		if (channel != null && channel.visible) {
			sendClear(player, channel);
			return;
		}
		// Never echo an untrusted future envelope into a Clear tombstone.
	}

	static boolean resyncEnvelopeMatches(
		final Instance countdown,
		final CountdownResyncRequestC2SPayload payload,
		final long authoritativeEpoch
	) {
		return countdown.gameInstanceId().equals(payload.gameInstanceId())
			&& countdown.definition().generation() == payload.definitionGeneration()
			&& authoritativeEpoch == payload.epoch()
			&& payload.contextVersion() <= countdown.lifecycle().stateVersion();
	}

	static long authoritativeResyncFloor(
		final long channelSequence,
		final long appliedSequence
	) {
		if (
			channelSequence < 0L
				|| appliedSequence < 0L
				|| appliedSequence >= Long.MAX_VALUE - 1L
		) {
			throw new IllegalArgumentException("countdown resync sequence cannot advance safely");
		}
		return Math.max(channelSequence, appliedSequence);
	}

	public static void refreshAll(
		final MinecraftServer server,
		final RefreshCause cause
	) {
		ensureBound(server);
		Objects.requireNonNull(cause, "cause");
		Instance countdown = activeCountdown(server);
		synchronizeIdentity(countdown);
		projectAll(server, countdown, true);
	}

	public static void countdownTransitionCommitted(
		final MinecraftServer server,
		final Instance before,
		final Instance after,
		final Optional<CheckpointDefinition> checkpoint
	) {
		ensureBound(server);
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(checkpoint, "checkpoint");
		boolean identityChanged = synchronizeIdentity(after);
		Optional<RestoredCountdown> restored = restore(server, after);
		Optional<JsonObject> visual = checkpoint.flatMap(value ->
			checkpointProjection(restored, value)
		);
		if (visual.isPresent()) {
			projectedCheckpoint = visual.map(JsonObject::deepCopy);
			checkpointExpiresAtTick = server.getTickCount() + CHECKPOINT_PRESENTATION_TICKS;
		} else if (after.lifecycle().state().terminal()) {
			projectedCheckpoint = Optional.empty();
			checkpointExpiresAtTick = Long.MIN_VALUE;
		}
		projectAll(server, after, before == null || identityChanged);
		emitLifecycleSound(server, before, after, restored);
		checkpoint.ifPresent(value -> emitCheckpointSideEffects(server, after, restored, value));
	}

	public static void countdownRetired(
		final MinecraftServer server,
		final Instance terminal
	) {
		ensureBound(server);
		Objects.requireNonNull(terminal, "terminal");
		for (UUID playerId : Set.copyOf(READY_PLAYERS)) {
			RecipientChannel channel = CHANNELS.get(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (channel != null && channel.visible && player != null) {
				sendClear(player, channel);
			}
		}
		trackedCountdownId = null;
		epoch = safeIncrement(epoch);
		restoreCache = null;
		projectedCheckpoint = Optional.empty();
		checkpointExpiresAtTick = Long.MIN_VALUE;
		handoffFailure = "";
	}

	/** Projects a persistent, player-visible completion handoff fault without changing the schema. */
	public static void handoffFailed(
		final MinecraftServer server,
		final Instance countdown,
		final String diagnostic
	) {
		ensureBound(server);
		Objects.requireNonNull(countdown, "countdown");
		String bounded = boundedDiagnostic(diagnostic);
		if (bounded.equals(handoffFailure)) {
			return;
		}
		handoffFailure = bounded;
		projectedCheckpoint = Optional.of(
			CountdownProjectionDocumentCodec.handoffFailureProjection(bounded)
		);
		checkpointExpiresAtTick = Long.MAX_VALUE;
		projectAll(server, countdown, false);
	}

	private static void projectAll(
		final MinecraftServer server,
		final Instance countdown,
		final boolean forceReplace
	) {
		for (UUID playerId : Set.copyOf(READY_PLAYERS)) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				projectRecipient(server, countdown, player, forceReplace);
			}
		}
	}

	private static void projectRecipient(
		final MinecraftServer server,
		final Instance countdown,
		final ServerPlayer player,
		final boolean forceReplace
	) {
		RecipientChannel channel = CHANNELS.computeIfAbsent(
			player.getUUID(),
			ignored -> new RecipientChannel()
		);
		if (countdown == null || !authorizedRecipients(server, countdown).contains(player.getUUID())) {
			if (channel.visible) {
				sendClear(player, channel);
			}
			return;
		}
		boolean identityMatches = channel.matches(countdown, epoch);
		if (forceReplace || !channel.visible || !identityMatches) {
			sendReplace(server, player, channel, countdown);
		} else {
			sendPatch(server, player, channel, countdown);
		}
	}

	private static void sendReplace(
		final MinecraftServer server,
		final ServerPlayer player,
		final RecipientChannel channel,
		final Instance countdown
	) {
		if (!ServerPlayNetworking.canSend(player, CountdownReplaceS2CPayload.TYPE)) {
			return;
		}
		try {
			boolean sameIdentity = channel.matches(countdown, epoch);
			long sequence = sameIdentity ? safeIncrement(channel.sequence) : 0L;
			EncodingResult encoded = replaceDocument(server, countdown, player.getUUID());
			if (!encoded.success()) {
				projectionFailed(player, channel, countdown, encoded.diagnostic().orElseThrow());
				return;
			}
			ServerPlayNetworking.send(player, new CountdownReplaceS2CPayload(
				countdown.gameInstanceId(),
				countdown.definition().generation(),
				epoch,
				countdown.lifecycle().stateVersion(),
				sequence,
				encoded.document().orElseThrow()
			));
			PROJECTION_DIAGNOSTICS.remove(player.getUUID());
			channel.bind(countdown, epoch, sequence, true);
		} catch (RuntimeException error) {
			projectionFailed(player, channel, countdown, "countdown Replace failed: " + safeMessage(error));
		}
	}

	private static void sendPatch(
		final MinecraftServer server,
		final ServerPlayer player,
		final RecipientChannel channel,
		final Instance countdown
	) {
		if (!ServerPlayNetworking.canSend(player, CountdownPatchS2CPayload.TYPE)) {
			return;
		}
		try {
			long base = channel.sequence;
			long sequence = safeIncrement(base);
			EncodingResult encoded = patchDocument(server, countdown, player.getUUID());
			if (!encoded.success()) {
				projectionFailed(player, channel, countdown, encoded.diagnostic().orElseThrow());
				return;
			}
			ServerPlayNetworking.send(player, new CountdownPatchS2CPayload(
				countdown.gameInstanceId(),
				countdown.definition().generation(),
				epoch,
				countdown.lifecycle().stateVersion(),
				base,
				sequence,
				encoded.document().orElseThrow()
			));
			PROJECTION_DIAGNOSTICS.remove(player.getUUID());
			channel.bind(countdown, epoch, sequence, true);
		} catch (RuntimeException error) {
			projectionFailed(player, channel, countdown, "countdown Patch failed: " + safeMessage(error));
		}
	}

	private static void projectionFailed(
		final ServerPlayer player,
		final RecipientChannel channel,
		final Instance countdown,
		final String diagnostic
	) {
		String previous = PROJECTION_DIAGNOSTICS.put(player.getUUID(), diagnostic);
		if (!diagnostic.equals(previous)) {
			PixelTzzPro.LOGGER.error(
				"Countdown {} projection for player {} was rejected: {}",
				countdown.countdownInstanceId(),
				player.getGameProfile().name(),
				diagnostic
			);
		}
		if (!channel.visible) {
			return;
		}
		try {
			sendClear(player, channel);
		} catch (RuntimeException clearError) {
			channel.visible = false;
			PixelTzzPro.LOGGER.error(
				"Countdown {} fail-closed Clear for player {} also failed",
				countdown.countdownInstanceId(),
				player.getGameProfile().name(),
				clearError
			);
		}
	}

	private static void sendClear(final ServerPlayer player, final RecipientChannel channel) {
		if (!ServerPlayNetworking.canSend(player, CountdownClearS2CPayload.TYPE)) {
			return;
		}
		long sequence = safeIncrement(channel.sequence);
		ServerPlayNetworking.send(player, new CountdownClearS2CPayload(
			channel.gameInstanceId,
			channel.definitionGeneration,
			channel.epoch,
			channel.contextVersion,
			sequence
		));
		channel.sequence = sequence;
		channel.visible = false;
	}

	private static void clearVisibleRecipients() {
		MinecraftServer server = boundServer;
		if (server == null) {
			return;
		}
		for (UUID playerId : Set.copyOf(READY_PLAYERS)) {
			RecipientChannel channel = CHANNELS.get(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (channel != null && channel.visible && player != null) {
				sendClear(player, channel);
			}
		}
	}

	private static Set<UUID> authorizedRecipients(
		final MinecraftServer server,
		final Instance countdown
	) {
		Optional<RestoredCountdown> restored = restore(server, countdown);
		DisplayAudience audience = restored
			.map(value -> value.countdown().displayAudience())
			.orElseGet(DisplayAudience::safeDefault);
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		Set<UUID> participants = new LinkedHashSet<>();
		countdown.participants().forEach(value -> participants.add(value.playerId()));
		Set<UUID> approvedObservers = new LinkedHashSet<>();
		if (audience.includeObservers()) {
			var state = wrapper.currentV2().orElse(null);
			Map<Identifier, RoleDefinition> roles = restored
				.map(RestoredCountdown::definitions)
				.map(value -> value.roles())
				.orElse(Map.of());
			if (state != null && !roles.isEmpty()) {
				for (UUID playerId : READY_PLAYERS) {
					PlayerRecord player = state.players().get(playerId);
					RoleDefinition role = player == null ? null : roles.get(player.roleId());
					if (role != null && role.tags().contains(SPECTATOR_ROLE_TAG)) {
						approvedObservers.add(playerId);
					}
				}
			}
		}
		return resolveAuthorizedRecipientIds(
			READY_PLAYERS,
			participants,
			wrapper.hostId(),
			wrapper.currentV5()
				.map(WorldStateV5::countdownHostAudienceIds)
				.map(Set::copyOf)
				.orElseGet(Set::of),
			audience,
			approvedObservers
		);
	}

	/** Pure audience boundary used by the projection runtime and its regression checks. */
	static Set<UUID> resolveAuthorizedRecipientIds(
		final Set<UUID> readyPlayers,
		final Set<UUID> participants,
		final Optional<UUID> hostId,
		final Set<UUID> retainedHostAudienceIds,
		final DisplayAudience audience,
		final Set<UUID> approvedObservers
	) {
		Objects.requireNonNull(readyPlayers, "readyPlayers");
		Objects.requireNonNull(participants, "participants");
		Objects.requireNonNull(hostId, "hostId");
		Objects.requireNonNull(retainedHostAudienceIds, "retainedHostAudienceIds");
		Objects.requireNonNull(audience, "audience");
		Objects.requireNonNull(approvedObservers, "approvedObservers");
		Set<UUID> result = new LinkedHashSet<>(participants);
		if (audience.includeHost()) {
			hostId.ifPresent(result::add);
			result.addAll(retainedHostAudienceIds);
		}
		if (audience.includeObservers()) {
			approvedObservers.stream()
				.filter(playerId -> hostId.filter(playerId::equals).isEmpty())
				.forEach(result::add);
		}
		result.retainAll(readyPlayers);
		return Set.copyOf(result);
	}

	/** Compatibility overload for checks that do not model a host handoff. */
	static Set<UUID> resolveAuthorizedRecipientIds(
		final Set<UUID> readyPlayers,
		final Set<UUID> participants,
		final Optional<UUID> hostId,
		final DisplayAudience audience,
		final Set<UUID> approvedObservers
	) {
		return resolveAuthorizedRecipientIds(
			readyPlayers,
			participants,
			hostId,
			Set.of(),
			audience,
			approvedObservers
		);
	}

	private static EncodingResult replaceDocument(
		final MinecraftServer server,
		final Instance countdown,
		final UUID recipientId
	) {
		Optional<RestoredCountdown> restored = restore(server, countdown);
		return CountdownProjectionDocumentCodec.encodeReplace(
			projectionFrame(server, countdown, recipientId),
			restored.map(RestoredCountdown::countdown)
		);
	}

	private static EncodingResult patchDocument(
		final MinecraftServer server,
		final Instance countdown,
		final UUID recipientId
	) {
		return CountdownProjectionDocumentCodec.encodePatch(
			projectionFrame(server, countdown, recipientId)
		);
	}

	private static ProjectionFrame projectionFrame(
		final MinecraftServer server,
		final Instance countdown,
		final UUID recipientId
	) {
		boolean advances = CountdownAuthority.clockAdvances(countdown);
		boolean inventoryBlocked = CountdownAuthority.restrictionDecision(
			countdown,
			recipientId,
			CountdownAuthority.RestrictedAction.INVENTORY
		).blocked();
		return new ProjectionFrame(
			countdown.countdownInstanceId(),
			countdown.purpose().getSerializedName(),
			countdown.totalTicks(),
			clientState(countdown),
			server.getTickCount(),
			(double)countdown.lifecycle().remainingTicks(),
			advances ? -1.0D : 0.0D,
			countdown.lifecycle().state()
				== io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.WAITING_FOR_PLAYERS,
			inventoryBlocked,
			projectedCheckpoint
		);
	}

	private static String clientState(final Instance countdown) {
		return switch (countdown.lifecycle().state()) {
			case WAITING_FOR_PLAYERS -> "paused";
			case RECOVERY_WAIT -> "waiting";
			case RUNNING -> "running";
			case COMPLETING -> "completing";
			case COMPLETED -> "completed";
			case CANCELING -> "canceling";
			case CANCELED -> "canceled";
		};
	}

	private static Optional<JsonObject> checkpointProjection(
		final Optional<RestoredCountdown> restored,
		final CheckpointDefinition checkpoint
	) {
		if (restored.isEmpty()) {
			return Optional.empty();
		}
		CountdownDefinition countdown = restored.orElseThrow().countdown();
		var configured = countdown.checkpoints().stream()
			.filter(value -> value.id().equals(checkpoint.id()))
			.findFirst()
			.orElse(null);
		if (configured == null) {
			return Optional.empty();
		}
		return CountdownProjectionDocumentCodec.checkpointProjection(countdown, configured);
	}

	private static void emitCheckpointSideEffects(
		final MinecraftServer server,
		final Instance countdown,
		final Optional<RestoredCountdown> restored,
		final CheckpointDefinition checkpoint
	) {
		if (restored.isEmpty()) {
			return;
		}
		var configured = restored.orElseThrow().countdown().checkpoints().stream()
			.filter(value -> value.id().equals(checkpoint.id()))
			.findFirst()
			.orElse(null);
		if (configured == null) {
			return;
		}
		Set<UUID> recipients = authorizedRecipients(server, countdown);
		configured.sound().ifPresent(sound -> sendCheckpointSound(
			server,
			countdown,
			checkpoint.id(),
			sound,
			recipients
		));
		configured.cue().ifPresent(cue -> MessageServerRuntime.playHook(
			server,
			restored.orElseThrow().definitions(),
			cue,
			recipients,
			Optional.empty(),
			Map.of()
		));
	}

	private static void emitLifecycleSound(
		final MinecraftServer server,
		final Instance before,
		final Instance after,
		final Optional<RestoredCountdown> restored
	) {
		if (restored.isEmpty()) {
			return;
		}
		CountdownCallbackSlot slot = lifecycleSlot(before, after);
		if (slot == null) {
			return;
		}
		restored.orElseThrow().countdown().presentation().lifecycleSounds()
			.getOrDefault(slot, Optional.empty())
			.ifPresent(sound -> sendCheckpointSound(
				server,
				after,
				"lifecycle/" + enumName(slot),
				sound,
				authorizedRecipients(server, after)
			));
	}

	private static CountdownCallbackSlot lifecycleSlot(
		final Instance before,
		final Instance after
	) {
		if (before == null) {
			return CountdownCallbackSlot.START;
		}
		var previous = before.lifecycle().state();
		var next = after.lifecycle().state();
		if (previous == next) {
			return null;
		}
		if (
			(next == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.WAITING_FOR_PLAYERS
				|| next == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.RECOVERY_WAIT)
				&& previous == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.RUNNING
		) {
			return CountdownCallbackSlot.PAUSE;
		}
		if (
			next == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.RUNNING
				&& (previous == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.WAITING_FOR_PLAYERS
					|| previous == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.RECOVERY_WAIT)
		) {
			return CountdownCallbackSlot.RESUME;
		}
		if (
			next == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.CANCELING
		) {
			return CountdownCallbackSlot.CANCEL;
		}
		if (
			next == io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState.COMPLETING
		) {
			return CountdownCallbackSlot.COMPLETE;
		}
		return null;
	}

	private static void sendCheckpointSound(
		final MinecraftServer server,
		final Instance countdown,
		final String checkpointId,
		final CountdownSound sound,
		final Set<UUID> recipients
	) {
		for (UUID playerId : recipients) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (
				player != null
					&& ServerPlayNetworking.canSend(player, CountdownCheckpointSoundS2CPayload.TYPE)
			) {
				ServerPlayNetworking.send(player, new CountdownCheckpointSoundS2CPayload(
					countdown.countdownInstanceId(),
					countdown.lifecycle().stateVersion(),
					checkpointId,
					sound.event(),
					(float)sound.volume(),
					(float)sound.pitch()
				));
			}
		}
	}

	private static Optional<RestoredCountdown> restore(
		final MinecraftServer server,
		final Instance countdown
	) {
		Identifier gameId = PixelTzzWorldState.get(server).activeGameId().orElse(null);
		String sha = countdown.definition().closure().sha256();
		if (
			restoreCache != null
				&& restoreCache.countdownInstanceId().equals(countdown.countdownInstanceId())
				&& Objects.equals(restoreCache.gameId(), gameId)
				&& restoreCache.sha256().equals(sha)
		) {
			return restoreCache.restored();
		}
		Optional<RestoredCountdown> result = Optional.empty();
		String diagnostic = "active game is unavailable";
		if (gameId != null) {
			var restored = CountdownSnapshotCompiler.restore(
				gameId,
				countdown.definition().definitionId(),
				countdown.definition().closure()
			);
			result = restored.restored();
			diagnostic = restored.success() ? "" : restored.message();
		}
		restoreCache = new RestoreCache(
			countdown.countdownInstanceId(),
			gameId,
			sha,
			result,
			diagnostic
		);
		if (result.isEmpty()) {
			PixelTzzPro.LOGGER.error(
				"Countdown {} projection is using the safe fallback: {}",
				countdown.countdownInstanceId(),
				diagnostic
			);
		}
		return result;
	}

	private static boolean synchronizeIdentity(final Instance countdown) {
		UUID next = countdown == null ? null : countdown.countdownInstanceId();
		if (Objects.equals(trackedCountdownId, next)) {
			return false;
		}
		trackedCountdownId = next;
		epoch = safeIncrement(epoch);
		restoreCache = null;
		projectedCheckpoint = Optional.empty();
		checkpointExpiresAtTick = Long.MIN_VALUE;
		handoffFailure = "";
		lastHeartbeatTick = Long.MIN_VALUE;
		return true;
	}

	private static Instance activeCountdown(final MinecraftServer server) {
		return PixelTzzWorldState.get(server)
			.currentV5()
			.flatMap(WorldStateV5::countdown)
			.orElse(null);
	}

	private static void ensureBound(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		if (boundServer == null) {
			boundServer = server;
			return;
		}
		if (boundServer != server) {
			throw new IllegalStateException("countdown projection is bound to another server");
		}
	}

	private static String enumName(final Enum<?> value) {
		return value.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	private static long safeIncrement(final long value) {
		return Math.incrementExact(value);
	}

	private static String boundedDiagnostic(final String value) {
		String result = Objects.requireNonNullElse(value, "未知错误").strip();
		if (result.isEmpty()) {
			result = "未知错误";
		}
		int points = result.codePointCount(0, result.length());
		if (points <= 160) {
			return result;
		}
		int end = result.offsetByCodePoints(0, 157);
		return result.substring(0, end) + "...";
	}

	public enum RefreshCause {
		HANDSHAKE,
		DATA_PACK_RELOAD,
		AUTHORITY_CHANGE,
		COUNTDOWN_TRANSITION
	}

	private static final class RecipientChannel {
		private UUID gameInstanceId;
		private UUID countdownInstanceId;
		private io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownPurpose purpose;
		private long definitionGeneration;
		private long epoch;
		private long contextVersion;
		private long sequence;
		private boolean visible;

		private boolean matches(final Instance countdown, final long expectedEpoch) {
			return this.gameInstanceId != null
				&& this.gameInstanceId.equals(countdown.gameInstanceId())
				&& this.countdownInstanceId.equals(countdown.countdownInstanceId())
				&& this.purpose == countdown.purpose()
				&& this.definitionGeneration == countdown.definition().generation()
				&& this.epoch == expectedEpoch;
		}

		private void bind(
			final Instance countdown,
			final long nextEpoch,
			final long nextSequence,
			final boolean nextVisible
		) {
			this.gameInstanceId = countdown.gameInstanceId();
			this.countdownInstanceId = countdown.countdownInstanceId();
			this.purpose = countdown.purpose();
			this.definitionGeneration = countdown.definition().generation();
			this.epoch = nextEpoch;
			this.contextVersion = countdown.lifecycle().stateVersion();
			this.sequence = nextSequence;
			this.visible = nextVisible;
		}

		private void raiseSequenceFloor(
			final Instance countdown,
			final long nextEpoch,
			final long appliedSequence
		) {
			if (!matches(countdown, nextEpoch)) {
				bind(countdown, nextEpoch, appliedSequence, false);
				return;
			}
			this.sequence = authoritativeResyncFloor(this.sequence, appliedSequence);
		}
	}

	private record RestoreCache(
		UUID countdownInstanceId,
		Identifier gameId,
		String sha256,
		Optional<RestoredCountdown> restored,
		String diagnostic
	) {
		private RestoreCache {
			restored = Objects.requireNonNull(restored, "restored");
			diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
		}
	}
}
