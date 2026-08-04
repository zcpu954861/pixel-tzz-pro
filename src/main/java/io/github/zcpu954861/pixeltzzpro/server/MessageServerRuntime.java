package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AttachmentMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionEvaluation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ObscuredMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OnCueEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScreenStartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScreenTimeoutAction;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.content.MessageDependencySnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.MessageParameterUseGraph;
import io.github.zcpu954861.pixeltzzpro.content.MessageParameterUseGraph.UseGraph;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.SegmentSpan;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockAnchor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedOrigin;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload.Surface;
import io.github.zcpu954861.pixeltzzpro.server.MessageMinecraftResolver.InvocationEnvironment;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.CaptureBinding;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.CaptureIdentity;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.CompileRequest;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.NodeOccurrence;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.Projection;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.PreparedCallback;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority.Diagnostic;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority.IssueStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthority.ReportStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetPlaybackResolver;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.AdvanceResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.CallbackOutcomeResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ControlAction;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ControlResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ControlSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.CueSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.EventKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.GroupSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.PlayDisposition;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.PlayResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ProjectionEvent;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ProjectionKind;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ReauthorizationResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RuntimeState;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.TargetSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.DeliveryDirective;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.ExpiryDecision;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.InitialDecision;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.InstanceDirective;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.JoinDecision;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.PendingMode;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDeliveryLifecycleAuthority.PendingState;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageDynamicCaptureScheduler;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalProjectionPlanner;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalOnlyHandshakePolicy;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageOccurrencePlayback;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageOccurrencePlayback.ObscuredAction;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageOccurrencePlayback.Step;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.TimestampCandidates;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.TimestampValue;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryReplaySanitizer;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog;
import io.github.zcpu954861.pixeltzzpro.server.message.HostMessagePreviewSanitizer.HostPreview;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLifecyclePolicyRouter;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLifecyclePolicyRouter.Directive;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLifecyclePolicyRouter.Event;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLiveContextAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLiveContextAuthority.CurrentContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageReconnectProjection;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecipientStateCodec;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecipientStateCodec.NodeConditionAddress;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecipientStateCodec.RecipientOccurrence;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecipientStateCodec.RecipientStateSnapshot;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceMapper;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryActionType;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecoveredFinalDeliveryBoundary;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecoveredFinalQueue;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecoveredFinalProjectionPolicy;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecoveryDefinitionPolicy;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecoveryProjectionPolicy;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimeFallbacks;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimeFallbacks.CompilationGate;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimeFallbacks.OccurrenceKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageSynchronizationAuthority;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageSynchronizationAuthority.Decision;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageSynchronizationAuthority.Policy;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.AudienceResolution;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft integration for the pure V3B message-instance authority.
 *
 * <p>Every mutation follows the same transaction order: accept an authority result, publish its
 * immutable {@code nextState} to this runtime, then emit network projections, then execute only
 * callbacks whose PREPARED ledger entry is already part of that committed state.
 */
public final class MessageServerRuntime {
	private static final int CALLBACK_CAPACITY = 4_096;
	private static final int MAX_ASSET_GATE_PARTICIPANTS = 4_096;
	private static final int MAX_ASSET_GATE_DIAGNOSTICS = 64;
	private static final int MAX_ASSET_GATE_DIAGNOSTIC_LENGTH = 256;
	private static final int MAX_ASSET_GATE_MESSAGE_LENGTH = 1_024;
	private static final Map<UUID, InvocationEnvironment> ENVIRONMENTS =
		new HashMap<>();
	private static final Map<UUID, DefinitionSnapshot> DEFINITIONS =
		new HashMap<>();
	private static final Map<UUID, UseGraph> PARAMETER_USES =
		new HashMap<>();
	private static final Map<Long, MessageAssetAuthority> ASSET_AUTHORITIES =
		new LinkedHashMap<>();
	private static final Map<StreamKey, RecipientStream> STREAMS =
		new LinkedHashMap<>();
	private static final Map<UUID, MessageSynchronizationAuthority> SYNCHRONIZATIONS =
		new LinkedHashMap<>();
	private static final Map<UUID, CapabilityReport> CAPABILITIES =
		new HashMap<>();
	private static final Map<UUID, ScreenReport> SCREENS =
		new HashMap<>();
	private static final Map<UUID, String> INTERRUPTION_REASONS =
		new HashMap<>();
	private static final Map<UUID, DeliverySession> DELIVERIES =
		new LinkedHashMap<>();
	private static final Set<UUID> QUEUED_DELIVERY_IDS = new LinkedHashSet<>();
	private static final Map<UUID, Set<UUID>> QUEUED_START_SETTLEMENTS =
		new LinkedHashMap<>();
	private static final Set<UUID> DELIVERY_BLOCKED_CALLBACKS =
		new LinkedHashSet<>();
	private static final Set<UUID> OFFLINE_PLAYERS = new LinkedHashSet<>();
	private static final MessageRecoveredFinalQueue<RecoveredStaticFinal> RECOVERED_STATIC_FINALS =
		new MessageRecoveredFinalQueue<>();
	private static final Set<UUID> TERMINAL_HISTORY_COMMITTED =
		new LinkedHashSet<>();
	private static boolean retiringTerminalInstances;

	private static RuntimeState runtimeState = RuntimeState.empty(CALLBACK_CAPACITY);
	private static volatile MessageAssetAuthority assetAuthority;
	private static MinecraftServer activeServer;
	private static long presentationOriginNanos;
	private static long presentationClockOffsetNanos;
	private static long lastPresentationNanos;
	private static boolean startupRecoveryAttempted;

	private MessageServerRuntime() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(MessageServerRuntime::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		ServerTickEvents.END_SERVER_TICK.register(MessageServerRuntime::onServerTick);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
			(server, resourceManager, success) -> onDataPackReload(server, success)
		);
		ServerPlayConnectionEvents.JOIN.register(
			(handler, sender, server) -> onPlayerJoined(handler.player)
		);
		ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> onPlayerDisconnected(handler.player)
		);
		ServerPlayerEvents.AFTER_RESPAWN.register(
			(oldPlayer, newPlayer, alive) -> onPlayerRespawned(newPlayer)
		);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
			MessageServerRuntime::onPlayerChangedLevel
		);
		ServerPlayNetworking.registerGlobalReceiver(
			MessageCapabilitiesC2SPayload.TYPE,
			(payload, context) -> receiveCapabilities(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			MessageAssetReportC2SPayload.TYPE,
			(payload, context) -> receiveAssetReport(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			MessageScreenStateC2SPayload.TYPE,
			(payload, context) -> receiveScreenState(context.player(), payload)
		);
	}

	/**
	 * Initializes process-local state before the authoritative definition loader starts.
	 *
	 * <p>This is called explicitly by {@link PixelTzzServerRuntime}'s server-start transaction.
	 * Message recovery cannot be a separate {@code SERVER_STARTED} listener: listener order would
	 * otherwise allow it to compare persisted instances against the reset, empty registry.</p>
	 */
	static void initializeForServer(final MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		clear();
		activeServer = server;
		presentationOriginNanos = System.nanoTime();
		presentationClockOffsetNanos = 0L;
		lastPresentationNanos = 0L;
	}

	private static void onServerStopping(final MinecraftServer server) {
		if (server != activeServer) {
			return;
		}
		boolean hasPersistentProgress = runtimeState.instances().values().stream().anyMatch(
			instance ->
				!instance.status().terminal()
					&& instance.cue().policies().lifecycle().restart()
						!= io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode.TRANSIENT
		);
		if (
			hasPersistentProgress
				&& !checkpointRuntime(server, clock(server), "server stopping final message progress")
		) {
			PixelTzzPro.LOGGER.error(
				"V3B final message checkpoint failed while the server was stopping"
			);
		}
	}

	private static void clear() {
		activeServer = null;
		assetAuthority = null;
		ASSET_AUTHORITIES.clear();
		runtimeState = RuntimeState.empty(CALLBACK_CAPACITY);
		ENVIRONMENTS.clear();
		DEFINITIONS.clear();
		PARAMETER_USES.clear();
		STREAMS.clear();
		SYNCHRONIZATIONS.clear();
		CAPABILITIES.clear();
		SCREENS.clear();
		INTERRUPTION_REASONS.clear();
		DELIVERIES.clear();
		QUEUED_DELIVERY_IDS.clear();
		QUEUED_START_SETTLEMENTS.clear();
		DELIVERY_BLOCKED_CALLBACKS.clear();
		OFFLINE_PLAYERS.clear();
		RECOVERED_STATIC_FINALS.clear();
		TERMINAL_HISTORY_COMMITTED.clear();
		retiringTerminalInstances = false;
		presentationOriginNanos = 0L;
		presentationClockOffsetNanos = 0L;
		lastPresentationNanos = 0L;
		startupRecoveryAttempted = false;
	}

	/**
	 * Rebuilds only restart=continue instances from the stable v4 checkpoint. PREPARED callback
	 * entries have already become OUTCOME_UNKNOWN in the pure recovery authority before the
	 * reconciled snapshot is committed here, so no recovery action can replay uncertain logic.
	 */
	/**
	 * Restores durable V3B state only after definitions were accepted and world migration finished.
	 */
	static void recoverPersistedRuntime(
		final MinecraftServer server,
		final DefinitionSnapshot activeDefinitions
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(activeDefinitions, "activeDefinitions");
		if (server != activeServer) {
			throw new IllegalStateException(
				"message recovery requires the initialized active server"
			);
		}
		if (startupRecoveryAttempted) {
			throw new IllegalStateException(
				"message recovery may run only once per server start"
			);
		}
		startupRecoveryAttempted = true;
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV4 world = wrapper.currentV4().orElse(null);
		if (world == null || world.messageRuntime().isEmpty()) {
			return;
		}
		UUID activeGameInstanceId = world.core()
			.activeGameInstanceId()
			.orElse(null);
		if (activeGameInstanceId == null) {
			PixelTzzPro.LOGGER.warn(
				"Ignored persisted V3B message runtime without an active game instance"
			);
			return;
		}
		PersistedMessageRuntime.Snapshot persisted = world.messageRuntime()
			.orElseThrow();
		var definitionReconciliation = MessageRecoveryDefinitionPolicy.reconcile(
			persisted,
			activeDefinitions
		);
		PersistedMessageRuntime.Snapshot recoveryInput = definitionReconciliation.snapshot();
		definitionReconciliation.finalizedInstanceIds().forEach(instanceId ->
			PixelTzzPro.LOGGER.error(
				"V3B restart=continue instance {} cannot prove its frozen dependency generation; "
					+ "using the frozen static final instead of active definitions",
				instanceId
			)
		);
		definitionReconciliation.cancelledInstanceIds().forEach(instanceId ->
			PixelTzzPro.LOGGER.error(
				"V3B restart=continue instance {} cannot prove its frozen dependency generation "
					+ "and has no static fallback; cancelling it",
				instanceId
			)
		);
		presentationClockOffsetNanos = recoveryInput.instances()
			.stream()
			.filter(instance ->
				instance.restart() == PersistedMessageRuntime.RestartMode.CONTINUE
					&& instance.timing().clock()
						== PersistedMessageRuntime.ClockMode.PRESENTATION_TIME
			)
			.mapToLong(instance -> instance.progress().standardElapsedNanos())
			.max()
			.orElse(0L);
		lastPresentationNanos = presentationClockOffsetNanos;
		ClockSample recoveryClock = clock(server);
		var plan = MessageRuntimePersistenceAuthority.recover(
			recoveryInput,
			activeGameInstanceId,
			recoveryClock.gameTick()
		);
		if (!plan.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B message recovery plan was rejected: {}",
				plan.message()
			);
			return;
		}
		var mapped = MessageRuntimePersistenceMapper.recover(
			plan,
			recoveryClock,
			activeDefinitions.generation()
		);
		if (!mapped.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B message recovery mapping was rejected: {}",
				mapped.message()
			);
			return;
		}
		var checkpoint = wrapper.messageRuntimeCheckpoint(
			activeGameInstanceId,
			OptionalLong.of(persisted.runtimeRevision()),
			mapped.reconciledSnapshot()
		);
		if (!checkpoint.committed()) {
			PixelTzzPro.LOGGER.error(
				"V3B reconciled recovery checkpoint was rejected: {}",
				checkpoint.reason()
			);
			return;
		}

		runtimeState = mapped.runtimeState().orElseThrow();
		Map<UUID, List<PersistedMessageRuntime.PendingOfflineDelivery>> pendingByInstance =
			mapped.reconciledSnapshot()
				.orElseThrow()
				.pendingOffline()
				.stream()
				.collect(
					java.util.stream.Collectors.groupingBy(
						PersistedMessageRuntime.PendingOfflineDelivery::instanceId,
						LinkedHashMap::new,
						java.util.stream.Collectors.toList()
					)
				);
		Map<UUID, List<PersistedMessageRuntime.PendingOfflineDelivery>> expiredByInstance =
			plan.expiredPendingOffline()
				.stream()
				.collect(
					java.util.stream.Collectors.groupingBy(
						PersistedMessageRuntime.PendingOfflineDelivery::instanceId,
						LinkedHashMap::new,
						java.util.stream.Collectors.toList()
					)
				);
		Map<UUID, PersistedMessageRuntime.FrozenCue> recoveredFrozenCues =
			new LinkedHashMap<>();
		for (PersistedMessageRuntime.Instance value : recoveryInput.instances()) {
			recoveredFrozenCues.put(value.instanceId(), value.frozenCue());
		}
		for (MessageRuntimePersistenceMapper.RestoredAction action : mapped.actions()) {
			if (action.type() == RecoveryActionType.RESTORE_CONTINUE) {
				restoreContinuingAction(
					server,
					action,
					activeDefinitions,
					pendingByInstance.getOrDefault(
						action.instance().instanceId(),
						List.of()
					),
					expiredByInstance.getOrDefault(
						action.instance().instanceId(),
						List.of()
					),
					recoveryClock
				);
			} else if (action.type() == RecoveryActionType.FINALIZE_STATIC) {
				queueRecoveredStaticFinal(
					action.instance(),
					Objects.requireNonNull(
						recoveredFrozenCues.get(action.instance().instanceId()),
						"recovered final frozen cue"
					),
					action.environment(),
					activeDefinitions.generation()
				);
			}
			/*
			 * CANCEL deliberately has no client projection on a fresh connection. Both CANCEL and
			 * FINALIZE_STATIC are absent from runtimeState, so neither can execute a callback.
			 */
		}
		for (var rejected : mapped.rejectedInstances()) {
			PixelTzzPro.LOGGER.error(
				"V3B persisted message {} was cancelled during recovery: {}",
				rejected.instanceId(),
				rejected.reason()
			);
		}
		for (
			PersistedMessageRuntime.PendingOfflineDelivery expired :
				plan.expiredPendingOffline()
		) {
			MessageInstance instance = runtimeState.instances().get(expired.instanceId());
			if (instance == null || !instance.status().active()) {
				continue;
			}
			if (
				expired.mode() == PersistedMessageRuntime.OfflineMode.FINAL_ONLY
					&& server.getPlayerList().getPlayer(expired.recipientId()) != null
			) {
				deliverFinalOnly(
					server,
					instance,
					expired.recipientId(),
					recoveryClock
				);
			} else {
				settleDeliveryTarget(
					server,
					instance,
					expired.recipientId(),
					recoveryClock
				);
			}
		}
	}

	private static void restoreContinuingAction(
		final MinecraftServer server,
		final MessageRuntimePersistenceMapper.RestoredAction action,
		final DefinitionSnapshot activeDefinitions,
		final List<PersistedMessageRuntime.PendingOfflineDelivery> pending,
		final List<PersistedMessageRuntime.PendingOfflineDelivery> expired,
		final ClockSample recoveryClock
	) {
		MessageInstance instance = action.instance();
		var restoredEnvironment = action.environment();
		PersistedMessageRuntime.Origin origin = restoredEnvironment.origin()
			.orElseThrow();
		ResourceKey<Level> level = ResourceKey.create(
			Registries.DIMENSION,
			origin.dimensionId()
		);
		ENVIRONMENTS.put(
			instance.instanceId(),
			new InvocationEnvironment(
				level,
				new Vec3(origin.x(), origin.y(), origin.z()),
				restoredEnvironment.invokerId(),
				restoredEnvironment.callTargets(),
				restoredEnvironment.arguments()
			)
		);
		DEFINITIONS.put(instance.instanceId(), activeDefinitions);
		if (
			activeDefinitions.generation()
				!= instance.context().definitionGeneration()
		) {
			PixelTzzPro.LOGGER.warn(
				"V3B message {} resumed from frozen cue generation {} while external "
					+ "definition generation is {}; the frozen cue remains authoritative",
				instance.instanceId(),
				instance.context().definitionGeneration(),
				activeDefinitions.generation()
			);
		}
		Set<UUID> settledTargets = new LinkedHashSet<>(instance.completedTargets());
		expired.forEach(value -> settledTargets.add(value.recipientId()));
		Set<UUID> pendingTargets = pending.stream()
			.map(PersistedMessageRuntime.PendingOfflineDelivery::recipientId)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<UUID> activeTargets = new LinkedHashSet<>(instance.cueTargets());
		activeTargets.removeAll(settledTargets);
		activeTargets.removeAll(pendingTargets);
		List<PendingState> pendingStates = pending.stream()
			.map(value -> new PendingState(
				value.recipientId(),
				value.mode() == PersistedMessageRuntime.OfflineMode.FINAL_ONLY
					? PendingMode.FINAL_ONLY
					: PendingMode.QUEUE,
				recoveredPendingExpiry(value, recoveryClock)
			))
			.toList();
		MessageDeliveryLifecycleAuthority deliveryAuthority =
			MessageDeliveryLifecycleAuthority.restore(
				instance.cue(),
				instance.cueTargets(),
				Set.copyOf(activeTargets),
				Set.copyOf(settledTargets),
				pendingStates,
				recoveryClock.presentationNanos()
			);
		DELIVERIES.put(
			instance.instanceId(),
			DeliverySession.restore(
				deliveryAuthority,
				instance.cueTargets(),
				pending,
				instance.cue()
			)
		);
		for (PersistedMessageRuntime.RecipientState persisted : action.recipientStates()) {
			RecipientStateSnapshot restored = MessageRecipientStateCodec.decode(
				instance.cue(),
				persisted
			);
			RecipientStream stream = new RecipientStream(
				new StreamKey(instance.instanceId(), restored.recipientId()),
				0L,
				PlanDisposition.CREATE,
				Optional.empty(),
				restored.createdElapsedNanos(),
				instance.cue().policies().timing().synchronization()
					.orElse(SynchronizationMode.SYNCHRONIZED),
				recipientScreenGateMode(server, instance, restored.recipientId())
			);
			stream.perPlayerAnchorElapsed = restored.perPlayerAnchorElapsedNanos()
				.orElse(null);
			MessageSynchronizationAuthority synchronization = synchronizationAuthority(
				instance
			);
			synchronization.addRecipient(restored.recipientId());
			if (stream.perPlayerAnchorElapsed != null) {
				synchronization.restore(
					restored.recipientId(),
					Decision.anchored(
						stream.synchronizationMode == SynchronizationMode.SYNCHRONIZED
							? MessageSynchronizationAuthority.Directive.START_SHARED
							: MessageSynchronizationAuthority.Directive.START_NOW,
						stream.perPlayerAnchorElapsed
					)
				);
			}
			stream.lockedValues.putAll(restored.lockedFields());
			stream.lockedSlots.addAll(restored.lockedFields().keySet());
			stream.cueStartConditionDecisions.putAll(
				restored.cueStartConditionDecisions()
			);
			restored.frozenCallbackDecisions().forEach(
				(occurrence, matched) ->
					stream.frozenCallbackDecisions.put(
						nodeOccurrence(occurrence),
						matched
					)
			);
			restored.nodeConditionDecisions().forEach(
				(address, matched) ->
					stream.conditionDecisions.put(
						new ConditionDecisionKey(
							nodeOccurrence(address.occurrence()),
							address.condition()
						),
						matched
					)
			);
			stream.targetCompletionCommitted = restored.completionCommitted();
			stream.terminal = restored.completionCommitted();
			stream.freshPhysicalConnectionPlan = true;
			stream.needsLockedSnapshot = !stream.lockedValues.isEmpty();
			if (!stream.terminal) {
				long visualAnchor = MessageRecoveryProjectionPolicy.recoveredVisualAnchor(
					instance.anchorClockNanos(),
					instance.accumulatedElapsedNanos()
				);
				stream.visualAnchorNanos = visualAnchor;
			}
			STREAMS.put(stream.key, stream);
			if (!stream.terminal) {
				try {
					rebuildRecoveredStream(
						server,
						instance,
						stream,
						restored,
						recoveryClock
					);
				} catch (RuntimeException error) {
					PixelTzzPro.LOGGER.error(
						"V3B recovered projection {} / {} could not be rebuilt; "
							+ "the recipient will receive the registered static fallback",
						instance.instanceId(),
						restored.recipientId(),
						error
					);
					degradeRecoveredStream(stream, restored);
				}
			}
		}
	}

	private static long recoveredPendingExpiry(
		final PersistedMessageRuntime.PendingOfflineDelivery pending,
		final ClockSample recoveryClock
	) {
		long deadline = pending.expiresAtGameTick().orElseThrow(
			() -> new IllegalArgumentException(
				"persistent offline delivery is missing its TTL deadline"
			)
		);
		return MessageRecoveryProjectionPolicy.pendingPresentationExpiry(
			deadline,
			recoveryClock
		);
	}

	/**
	 * Rebuilds the private wire plan that existed before a restart without sending it yet.
	 *
	 * <p>The original initial batch is compiled first, followed by already-due occurrences in
	 * their stable schedule order. Preserving those batch boundaries also preserves opaque field
	 * slot assignment, so persisted locked values cannot drift onto a different dynamic field.
	 * The fresh connection receives this plan only after reconnect policy and capability
	 * negotiation have run; its old sounds are therefore skipped by the restored seek anchor.
	 */
	private static void rebuildRecoveredStream(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final RecipientStateSnapshot restored,
		final ClockSample recoveryClock
	) {
		Set<NodeOccurrence> expected = restored.projectedOccurrences()
			.stream()
			.map(MessageServerRuntime::nodeOccurrence)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<NodeOccurrence> initial = new LinkedHashSet<>(
			initialProjectionOccurrences(server, instance, restored.recipientId())
		);
		initial.retainAll(expected);
		if (!initial.isEmpty()) {
			compileRecoveredBatch(
				server,
				instance,
				stream,
				Set.copyOf(initial),
				recoveryClock
			);
		}

		Map<String, Integer> nodeOrder = new LinkedHashMap<>();
		for (int index = 0; index < instance.cue().nodes().size(); index++) {
			nodeOrder.put(instance.cue().nodes().get(index).id(), index);
		}
		List<NodeOccurrence> incremental = expected.stream()
			.filter(value -> !initial.contains(value))
			.sorted(
				Comparator
					.comparingLong((NodeOccurrence value) ->
						occurrenceScheduledAt(instance, stream, value)
							.orElse(Long.MAX_VALUE)
					)
					.thenComparingInt(value ->
						nodeOrder.getOrDefault(value.nodeId(), Integer.MAX_VALUE)
					)
					.thenComparingInt(NodeOccurrence::occurrence)
			)
			.toList();
		for (NodeOccurrence occurrence : incremental) {
			stream.dueConditionalNodes.add(occurrence.nodeId());
			try {
				compileRecoveredBatch(
					server,
					instance,
					stream,
					Set.of(occurrence),
					recoveryClock
				);
			} finally {
				stream.dueConditionalNodes.remove(occurrence.nodeId());
			}
		}
		if (!stream.projectedOccurrences.equals(expected)) {
			Set<NodeOccurrence> missing = new LinkedHashSet<>(expected);
			missing.removeAll(stream.projectedOccurrences);
			throw new IllegalStateException(
				"recovered projection could not reproduce occurrences " + missing
			);
		}
		if (
			stream.lockedValues.keySet().stream().anyMatch(slot ->
				slot < 0 || slot >= stream.nextSlot
			)
		) {
			throw new IllegalStateException(
				"recovered locked field slots no longer match the frozen projection"
			);
		}

		stream.decidedOccurrences.addAll(
			restored.decidedOccurrences()
				.stream()
				.map(MessageServerRuntime::nodeOccurrence)
				.toList()
		);
		stream.initialProjectionAttempted = stream.synchronizationMode
			!= SynchronizationMode.PER_PLAYER
			|| stream.perPlayerAnchorElapsed != null
			|| !stream.decidedOccurrences.isEmpty();
		freezeCueStartProjections(server, instance, stream, recoveryClock);
		if (stream.plan != null) {
			updateOccurrenceDurations(
				server,
				instance,
				stream,
				recoveryClock,
				instance.elapsedAt(recoveryClock)
			);
		}
	}

	private static void compileRecoveredBatch(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final Set<NodeOccurrence> requested,
		final ClockSample recoveryClock
	) {
		if (requested.isEmpty()) {
			return;
		}
		Projection projection;
		try {
			stream.compilingOccurrence = requested.size() == 1
				? requested.iterator().next()
				: null;
			projection = MessageProjectionCompiler.compileOccurrences(
				compileRequest(server, instance, stream, recoveryClock),
				requested
			);
		} finally {
			stream.compilingOccurrence = null;
		}
		if (projection.plan().isEmpty()) {
			throw new IllegalStateException(
				"persisted projected occurrence no longer produces a wire node"
			);
		}
		Map<NodeOccurrence, Long> startOverrides = new LinkedHashMap<>();
		for (NodeOccurrence occurrence : requested) {
			occurrenceScheduledAt(instance, stream, occurrence)
				.ifPresent(value -> startOverrides.put(occurrence, value));
		}
		NormalizedProjection normalized = normalizeProjection(
			stream,
			projection,
			startOverrides
		);
		if (!normalized.occurrences().containsAll(requested)) {
			throw new IllegalStateException(
				"persisted projection batch was only partially rebuilt"
			);
		}
		MessagePlaybackPlan source = projection.plan().orElseThrow();
		if (stream.plan == null) {
			stream.plan = copyPlanWithNodes(source, normalized.nodes());
		} else {
			List<ResolvedNode> merged = new ArrayList<>(stream.plan.nodes());
			merged.addAll(normalized.nodes());
			stream.plan = copyPlanWithNodes(stream.plan, merged);
		}
		mergeCaptures(stream, normalized.captures());
		recordProjectedNodes(stream, normalized);
		stream.projectedOccurrences.addAll(normalized.occurrences());
		stream.lastSequence = stream.plan.sequence();
	}

	private static void degradeRecoveredStream(
		final RecipientStream stream,
		final RecipientStateSnapshot restored
	) {
		stream.plan = null;
		stream.planSent = false;
		stream.nodesByOccurrence.clear();
		stream.occurrenceDurations.clear();
		stream.occurrencePlaybacks.clear();
		stream.frozenCueStartProjections.clear();
		stream.slotIds.clear();
		stream.captures.clear();
		stream.projectedOccurrences.clear();
		restored.projectedOccurrences().forEach(value ->
			stream.projectedOccurrences.add(nodeOccurrence(value))
		);
		stream.decidedOccurrences.clear();
		restored.decidedOccurrences().forEach(value ->
			stream.decidedOccurrences.add(nodeOccurrence(value))
		);
		stream.nextOrdinal = 0;
		stream.nextSlot = stream.lockedValues.keySet()
			.stream()
			.mapToInt(Integer::intValue)
			.max()
			.orElse(-1) + 1;
		stream.initialProjectionAttempted = true;
		stream.recoveryFallbackPending = true;
	}

	private static NodeOccurrence nodeOccurrence(
		final RecipientOccurrence occurrence
	) {
		return new NodeOccurrence(occurrence.nodeId(), occurrence.occurrence());
	}

	private static void queueRecoveredStaticFinal(
		final MessageInstance instance,
		final PersistedMessageRuntime.FrozenCue frozenCue,
		final MessageRuntimePersistenceMapper.RestoredInvocationEnvironment environment,
		final long authorizedDefinitionGeneration
	) {
		Objects.requireNonNull(frozenCue, "frozenCue");
		Objects.requireNonNull(environment, "environment");
		if (instance.cue().staticFallback().isEmpty()) {
			PixelTzzPro.LOGGER.warn(
				"V3B restart=finalize instance {} has no static_fallback to deliver",
				instance.instanceId()
			);
			return;
		}
		Set<UUID> pendingRecipients = new LinkedHashSet<>(instance.cueTargets());
		pendingRecipients.removeAll(instance.completedTargets());
		if (pendingRecipients.isEmpty()) {
			return;
		}
		RECOVERED_STATIC_FINALS.enqueue(
			instance.instanceId(),
			pendingRecipients,
			new RecoveredStaticFinal(
				instance,
				frozenCue,
				environment,
				authorizedDefinitionGeneration
			)
		);
	}

	/**
	 * Materializes at most one history record for the player whose restored authority is currently
	 * being handled. The history audience is deliberately resolved here, after registration and
	 * online state restoration, so {@code online_only}, roles and other live selectors cannot be
	 * evaluated against startup placeholders.
	 */
	private static MessageRecoveredFinalDeliveryBoundary.GateDecision persistRecoveredFinalHistoryForRecipient(
		final MinecraftServer server,
		final RecoveredStaticFinal recovered,
		final ServerPlayer player
	) {
		MessageInstance instance = recovered.instance();
		if (instance.cue().history().isEmpty()) {
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED;
		}
		DefinitionSnapshot definitions = DefinitionRegistry.INSTANCE.view().active();
		if (definitions.generation() != recovered.authorizedDefinitionGeneration()) {
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV4 root = wrapper.currentV4().orElse(null);
		WorldStateV3 world = wrapper.currentV3().orElse(null);
		UUID gameInstanceId = world == null
			? null
			: world.activeGameInstanceId().orElse(null);
		if (root == null || world == null || gameInstanceId == null) {
			PixelTzzPro.LOGGER.error(
				"V3B restart=finalize history could not resolve the active game scope for {} / {}",
				instance.instanceId(),
				player.getUUID()
			);
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
		}
		var history = instance.cue().history().orElseThrow();
		AudienceResolution audience = MessageMinecraftResolver.resolveAudience(
			server,
			definitions,
			world,
			instance.cue(),
			instance.context(),
			history.audience()
		);
		if (!audience.resolved()) {
			PixelTzzPro.LOGGER.error(
				"V3B restart=finalize history audience failed for {} / {}: {}",
				instance.instanceId(),
				player.getUUID(),
				audience.message()
			);
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
		}
		UUID viewer = player.getUUID();
		if (
			!audience.cueTargets().contains(viewer)
				|| !recipientAuthorizedForHistory(
					server,
					definitions,
					world,
					instance,
					viewer
				)
		) {
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED;
		}
		PersistedMessageRuntime.Origin origin = recovered.environment()
			.origin()
			.orElse(null);
		if (origin == null) {
			PixelTzzPro.LOGGER.error(
				"V3B restart=finalize history {} / {} has no frozen invocation origin",
				instance.instanceId(),
				viewer
			);
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
		}
		InvocationEnvironment environment = new InvocationEnvironment(
			ResourceKey.create(Registries.DIMENSION, origin.dimensionId()),
			new Vec3(origin.x(), origin.y(), origin.z()),
			recovered.environment().invokerId(),
			recovered.environment().callTargets(),
			recovered.environment().arguments()
		);
		ClockSample recoveryClock = clock(server);
		Optional<Identifier> currentTask = world.timeline()
			.flatMap(WorldStateV3.TimelineInstance::currentTask)
			.map(WorldStateV3.TaskInstance::taskId);
		long gameElapsedTicks = world.timeline()
			.map(WorldStateV3.TimelineInstance::gameElapsedTicks)
			.orElse(recoveryClock.gameTick());
		long cueElapsedNanos = instance.elapsedAt(recoveryClock);
		long cueStartTicks = Math.max(
			0L,
			gameElapsedTicks - cueElapsedNanos / TimeSpan.NANOS_PER_TICK
		);
		TimestampCandidates timestamps = new TimestampCandidates(
			TimestampValue.ticks(gameElapsedTicks),
			TimestampValue.nanos(recoveryClock.presentationNanos()),
			TimestampValue.ticks(cueStartTicks),
			TimestampValue.ticks(gameElapsedTicks)
		);
		Map<String, String> fields = frozenHistoryFields(
			server,
			definitions,
			world,
			instance,
			environment,
			viewer
		);
		var materialized = MessageHistoryMaterializer.materialize(
			new MessageHistoryMaterializer.Request(
				instance.cue(),
				history,
				gameInstanceId,
				instance.instanceId(),
				viewer,
				player.clientInformation().language(),
				fields,
				instance.canonicalParameters(),
				currentTask,
				timestamps
			)
		);
		if (!materialized.successful()) {
			PixelTzzPro.LOGGER.error(
				"V3B restart=finalize history failed for {} / {}: {}",
				instance.instanceId(),
				viewer,
				materialized.diagnostics().stream()
					.map(MessageHistoryMaterializer.Diagnostic::message)
					.reduce((left, right) -> left + "; " + right)
					.orElse("unknown history error")
			);
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
		}
		MessageHistoryAuthority.BatchTransaction batch = MessageHistoryAuthority.appendAll(
			root,
			List.of(materialized.record().orElseThrow())
		);
		if (!batch.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B restart=finalize history append was rejected for {} / {}: {}",
				instance.instanceId(),
				viewer,
				batch.message()
			);
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
		}
		if (batch.appendedRecords() == 0 && batch.evictedRecords() == 0) {
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED;
		}
		var committed = wrapper.commitV4(
			root.stateRevision(),
			ignored -> batch.state()
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.error(
				"V3B restart=finalize history commit was rejected for {} / {}: {}",
				instance.instanceId(),
				viewer,
				committed.reason()
			);
			return MessageRecoveredFinalDeliveryBoundary.GateDecision.RETRY;
		}
		PixelTzzServerRuntime.refreshPlayerTerminalsAfterMessageHistory(server);
		return MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED;
	}

	private static MessageRecoveredFinalDeliveryBoundary.ConsumeDecision prepareRecoveredFinalDelivery(
		final MinecraftServer server,
		final RecoveredStaticFinal recovered,
		final UUID recipient
	) {
		MessageInstance instance = recovered.instance();
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV4 world = wrapper.currentV4().orElse(null);
		UUID gameInstanceId = world == null
			? null
			: world.core().activeGameInstanceId().orElse(null);
		PersistedMessageRuntime.Snapshot persisted = world == null
			? null
			: world.messageRuntime().orElse(null);
		if (
			gameInstanceId == null
				|| persisted == null
				|| !persisted.gameInstanceId().equals(gameInstanceId)
		) {
			PixelTzzPro.LOGGER.error(
				"V3B recovered final {} / {} has no matching durable game scope",
				instance.instanceId(),
				recipient
			);
			return MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.ALREADY_SETTLED;
		}
		var consumed = MessageRuntimePersistenceAuthority.consumeRecoveredFinal(
			persisted,
			gameInstanceId,
			instance.instanceId(),
			recipient,
			clock(server).gameTick()
		);
		if (!consumed.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B recovered final {} / {} could not be consumed: {}",
				instance.instanceId(),
				recipient,
				consumed.message()
			);
			return MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.ALREADY_SETTLED;
		}
		if (!consumed.deliveryRequired()) {
			return MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.ALREADY_SETTLED;
		}
		PersistedMessageRuntime.Snapshot next = consumed.snapshot().orElseThrow();
		var committed = wrapper.messageRuntimeCheckpoint(
			gameInstanceId,
			OptionalLong.of(persisted.runtimeRevision()),
			Optional.of(next)
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.error(
				"V3B recovered final {} / {} checkpoint failed: {}",
				instance.instanceId(),
				recipient,
				committed.reason()
			);
			return MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.RETRY;
		}
		if (runtimeState.revision() > next.runtimeRevision()) {
			throw new IllegalStateException(
				"live message runtime revision is ahead of recovered final persistence"
			);
		}
		if (runtimeState.revision() != next.runtimeRevision()) {
			runtimeState = new RuntimeState(
				next.runtimeRevision(),
				runtimeState.instances(),
				runtimeState.queue(),
				runtimeState.activeDedupe(),
				runtimeState.refreshIndex(),
				runtimeState.cooldowns(),
				runtimeState.callbackLedger()
			);
		}
		return MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.CONSUMED;
	}

	/**
	 * Reauthorizes a frozen static final at the actual join boundary before any durable consume or
	 * history write. A deterministic rejection is subsequently retired through the same consume
	 * checkpoint without disclosure; an exceptional check remains retryable. Startup reconciliation
	 * may have
	 * deliberately downgraded a changed CONTINUE definition to its frozen FINALIZE fallback, so the
	 * original dependency set is not required to become equal again. Instead, the exact definition
	 * generation that approved that downgrade must still be active, then context, live audience,
	 * node visibility, parameter audience, and complete-static-fallback coverage are rechecked.
	 */
	private static boolean recoveredStaticFinalAuthorized(
		final MinecraftServer server,
		final RecoveredStaticFinal recovered,
		final UUID recipient
	) {
		MessageInstance instance = recovered.instance();
		DefinitionSnapshot definitions = DefinitionRegistry.INSTANCE.view().active();
		if (definitions.generation() != recovered.authorizedDefinitionGeneration()) {
			return false;
		}
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (world == null) {
			return false;
		}
		CurrentContext currentContext = new CurrentContext(
			world.core().activeGameId(),
			world.core().activePhaseId(),
			world.timeline()
				.flatMap(WorldStateV3.TimelineInstance::currentTask)
				.map(WorldStateV3.TaskInstance::taskId)
		);
		if (
			MessageLiveContextAuthority.resolve(
				instance.cue().game(),
				instance.cue().policies().context(),
				instance.context().bypassContext(),
				currentContext
			) == MessageLiveContextAuthority.Directive.CANCEL
		) {
			return false;
		}

		boolean liveCheck = instance.cue().policies().audience().sensitive()
			|| instance.cue().policies().audience().evolution()
				== AudienceEvolution.LIVE_STRICT;
		MessageFinalProjectionPlanner.FinalAudienceView snapshot =
			new MessageFinalProjectionPlanner.FinalAudienceView(
				instance.cueTargets(),
				instance.nodeTargets()
			);
		Optional<MessageFinalProjectionPlanner.FinalAudienceView> liveView = Optional.empty();
		if (liveCheck) {
			final AudienceResolution resolved;
			try {
				resolved = MessageMinecraftResolver.resolveAudience(
					server,
					definitions,
					world,
					instance.cue(),
					instance.context(),
					instance.cue().policies().audience().audience()
				);
			} catch (RuntimeException error) {
				return false;
			}
			if (!resolved.resolved()) {
				return false;
			}
			liveView = Optional.of(
				new MessageFinalProjectionPlanner.FinalAudienceView(
					resolved.cueTargets(),
					resolved.nodeTargets()
				)
			);
		}
		Optional<Set<String>> authorized = MessageFinalProjectionPlanner.authorizedNodeIds(
			instance.cue(),
			recipient,
			snapshot,
			liveCheck,
			liveView
		);
		if (authorized.isEmpty()) {
			return false;
		}
		UseGraph uses = MessageParameterUseGraph.compile(instance.cue());
		Set<String> authorizedNodes = authorized.orElseThrow()
			.stream()
			.filter(nodeId -> {
				Set<String> parameters = uses.parametersForNode(nodeId);
				if (
					parameters.stream().anyMatch(parameterId -> {
						var definition = instance.cue().parameters().get(parameterId);
						return definition == null
							|| (
								!definition.allowedNodes().isEmpty()
									&& !definition.allowedNodes().contains(nodeId)
							);
					})
				) {
					return false;
				}
				return parameters.stream().allMatch(parameterId ->
					MessageMinecraftResolver.parameterAuthorized(
						server,
						definitions,
						world,
						instance,
						recipient,
						parameterId
					)
				);
			})
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		return MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
			instance,
			recipient,
			Set.copyOf(authorizedNodes)
		);
	}

	/**
	 * Rebuilds the generation-scoped client-asset authority from the definition snapshot that was
	 * actually accepted by {@link DefinitionRegistry}. Every live play connection receives that
	 * generation's manifest again and must submit a fresh report before start approval can pass.
	 */
	public static void rebuildAssetAuthority(
		final MinecraftServer server,
		final DefinitionSnapshot snapshot
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(snapshot, "snapshot");
		if (!server.isSameThread()) {
			throw new IllegalStateException(
				"message asset authority must be rebuilt on the server thread"
			);
		}
		MessageAssetAuthority rebuilt = MessageAssetAuthority.fromSnapshot(snapshot);
		assetAuthority = rebuilt;
		ASSET_AUTHORITIES.put(snapshot.generation(), rebuilt);
		if (rebuilt.catalog().blocked()) {
			PixelTzzPro.LOGGER.warn(
				"V3B message asset catalog generation {} is blocked: {}",
				snapshot.generation(),
				assetDiagnosticSummary(rebuilt.catalog().diagnostics())
			);
		}
		server.getPlayerList()
			.getPlayers()
			.stream()
			.sorted(Comparator.comparing(player -> player.getUUID().toString()))
			.forEach(player -> issueAssetManifest(rebuilt, player));
		pruneAssetAuthorities();
	}

	private static MessageAssetAuthority assetAuthorityFor(final MessageInstance instance) {
		return ASSET_AUTHORITIES.get(instance.context().definitionGeneration());
	}

	/** Keeps an old generation only while a live/queued instance can still project from it. */
	private static void pruneAssetAuthorities() {
		MessageAssetAuthority active = assetAuthority;
		long activeGeneration = active == null ? Long.MIN_VALUE : active.catalog().generation();
		Set<Long> retained = new LinkedHashSet<>();
		runtimeState.instances().values().stream()
			.filter(instance -> !instance.status().terminal())
			.map(instance -> instance.context().definitionGeneration())
			.forEach(retained::add);
		runtimeState.queue().stream()
			.map(invocation -> invocation.context().definitionGeneration())
			.forEach(retained::add);
		ASSET_AUTHORITIES.keySet().removeIf(generation ->
			generation != activeGeneration && !retained.contains(generation)
		);
	}

	/**
	 * Aggregates the mandatory per-participant resource preflight used by both approval review and
	 * final execution revalidation. This method never sends UI; callers surface only its bounded
	 * Chinese summary through their normal operation result.
	 */
	public static RequiredAssetsGate requiredAssetsForGame(
		final MinecraftServer server,
		final Identifier gameId,
		final Set<UUID> frozenParticipants
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(gameId, "gameId");
		Objects.requireNonNull(frozenParticipants, "frozenParticipants");
		MessageAssetAuthority authority = assetAuthority;
		if (authority == null || activeServer != server) {
			return RequiredAssetsGate.blocked(
				frozenParticipants.size(),
				frozenParticipants.size(),
				List.of("消息资源检查服务尚未就绪，请稍后重新审阅。")
			);
		}

		Set<UUID> online = new LinkedHashSet<>();
		Map<UUID, String> labels = new LinkedHashMap<>();
		PixelTzzWorldState worldState = PixelTzzWorldState.get(server);
		for (UUID participantId : frozenParticipants) {
			ServerPlayer player = server.getPlayerList().getPlayer(participantId);
			if (player != null) {
				online.add(participantId);
				labels.put(participantId, player.getScoreboardName());
				continue;
			}
			PlayerRecord record = worldState.currentV3()
				.map(root -> root.core().players().get(participantId))
				.orElse(null);
			if (record != null) {
				labels.put(participantId, record.lastKnownName());
			}
		}
		return evaluateRequiredAssetsForGame(
			authority,
			gameId,
			frozenParticipants,
			online,
			labels
		);
	}

	static RequiredAssetsGate evaluateRequiredAssetsForGame(
		final MessageAssetAuthority authority,
		final Identifier gameId,
		final Set<UUID> frozenParticipants,
		final Set<UUID> onlineParticipants,
		final Map<UUID, String> participantLabels
	) {
		Objects.requireNonNull(authority, "authority");
		Objects.requireNonNull(gameId, "gameId");
		Objects.requireNonNull(frozenParticipants, "frozenParticipants");
		Objects.requireNonNull(onlineParticipants, "onlineParticipants");
		Objects.requireNonNull(participantLabels, "participantLabels");
		if (frozenParticipants.size() > MAX_ASSET_GATE_PARTICIPANTS) {
			return RequiredAssetsGate.blocked(
				frozenParticipants.size(),
				frozenParticipants.size(),
				List.of(
					"冻结参与者数量超过消息资源检查上限 "
						+ MAX_ASSET_GATE_PARTICIPANTS
						+ "。"
				)
			);
		}
		if (authority.catalog().blocked()) {
			List<String> diagnostics = authority.catalog()
				.diagnostics()
				.stream()
				.map(Diagnostic::message)
				.map(MessageServerRuntime::boundedAssetDiagnostic)
				.limit(MAX_ASSET_GATE_DIAGNOSTICS)
				.toList();
			if (diagnostics.isEmpty()) {
				diagnostics = List.of("消息资源目录尚未就绪，请稍后重新审阅。");
			}
			return RequiredAssetsGate.blocked(
				frozenParticipants.size(),
				frozenParticipants.size(),
				diagnostics
			);
		}

		List<UUID> orderedParticipants = frozenParticipants.stream()
			.sorted(Comparator.comparing(UUID::toString))
			.toList();
		List<String> diagnostics = new ArrayList<>();
		int blockedParticipants = 0;
		for (UUID participantId : orderedParticipants) {
			String label = participantLabel(participantId, participantLabels);
			if (!onlineParticipants.contains(participantId)) {
				blockedParticipants++;
				retainAssetDiagnostic(diagnostics, label + "：玩家当前不在线。");
				continue;
			}
			var gate = authority.requiredAssetsForGame(participantId, gameId);
			if (gate.ready()) {
				continue;
			}
			blockedParticipants++;
			if (gate.diagnostics().isEmpty()) {
				retainAssetDiagnostic(diagnostics, label + "：消息资源检查未通过。");
				continue;
			}
			for (Diagnostic diagnostic : gate.diagnostics()) {
				retainAssetDiagnostic(
					diagnostics,
					label + "：" + diagnostic.message()
				);
			}
		}
		if (blockedParticipants == 0) {
			return RequiredAssetsGate.ready(orderedParticipants.size());
		}
		return RequiredAssetsGate.blocked(
			orderedParticipants.size(),
			blockedParticipants,
			diagnostics
		);
	}

	private static void issueAssetManifest(
		final MessageAssetAuthority authority,
		final ServerPlayer player
	) {
		UUID playerId = player.getUUID();
		if (!ServerPlayNetworking.canSend(player, MessageAssetManifestS2CPayload.TYPE)) {
			authority.closeConnection(playerId);
			PixelTzzPro.LOGGER.warn(
				"V3B message asset manifest channel is unavailable for {}",
				player.getScoreboardName()
			);
			return;
		}
		var issue = authority.openConnection(playerId);
		if (issue.status() != IssueStatus.ISSUED) {
			PixelTzzPro.LOGGER.warn(
				"Could not issue V3B message asset manifest to {}: status={}, diagnostics={}",
				player.getScoreboardName(),
				issue.status(),
				assetDiagnosticSummary(issue.diagnostics())
			);
			return;
		}
		try {
			ServerPlayNetworking.send(player, issue.manifest().orElseThrow());
		} catch (RuntimeException sendFailure) {
			authority.closeConnection(playerId);
			PixelTzzPro.LOGGER.warn(
				"Could not send V3B message asset manifest to {}: {}",
				player.getScoreboardName(),
				safeMessage(sendFailure)
			);
		}
	}

	/** Sends the cumulative cue-asset extension before that recipient's first playback plan. */
	private static boolean cueAssetsReady(
		final MessageAssetAuthority authority,
		final ServerPlayer player,
		final Identifier cueId
	) {
		if (!ServerPlayNetworking.canSend(player, MessageAssetManifestS2CPayload.TYPE)) {
			authority.closeConnection(player.getUUID());
			return false;
		}
		var issue = authority.declareCueAssets(player.getUUID(), cueId);
		if (issue.status() == IssueStatus.ALREADY_DECLARED) {
			return authority.effectiveAssets(player.getUUID()).isPresent();
		}
		if (issue.status() != IssueStatus.ISSUED) {
			PixelTzzPro.LOGGER.warn(
				"Could not extend V3B message asset manifest for {} / {}: status={}, diagnostics={}",
				player.getScoreboardName(),
				cueId,
				issue.status(),
				assetDiagnosticSummary(issue.diagnostics())
			);
			return false;
		}
		try {
			ServerPlayNetworking.send(player, issue.manifest().orElseThrow());
		} catch (RuntimeException sendFailure) {
			authority.closeConnection(player.getUUID());
			PixelTzzPro.LOGGER.warn(
				"Failed to extend V3B message asset manifest for {} / {}",
				player.getScoreboardName(),
				cueId,
				sendFailure
			);
		}
		// A newly extended cumulative manifest invalidates the previous observation. The plan
		// must wait for the matching report so it cannot leak the original ID before fallback or
		// silent resolution is known.
		return false;
	}

	private static String participantLabel(
		final UUID participantId,
		final Map<UUID, String> participantLabels
	) {
		String candidate = participantLabels.get(participantId);
		if (candidate == null || candidate.isBlank()) {
			candidate = participantId.toString();
		}
		return boundedText(candidate, 80);
	}

	private static void retainAssetDiagnostic(
		final List<String> diagnostics,
		final String diagnostic
	) {
		if (diagnostics.size() < MAX_ASSET_GATE_DIAGNOSTICS) {
			diagnostics.add(boundedAssetDiagnostic(diagnostic));
		}
	}

	private static String boundedAssetDiagnostic(final String value) {
		return boundedText(value, MAX_ASSET_GATE_DIAGNOSTIC_LENGTH);
	}

	private static String boundedText(final String value, final int maxLength) {
		String normalized = Objects.requireNonNull(value, "value")
			.replace('\r', ' ')
			.replace('\n', ' ')
			.replace('\t', ' ')
			.strip();
		if (normalized.isEmpty()) {
			normalized = "未知原因";
		}
		if (normalized.length() <= maxLength) {
			return normalized;
		}
		return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
	}

	private static String assetDiagnosticSummary(
		final List<Diagnostic> diagnostics
	) {
		if (diagnostics.isEmpty()) {
			return "none";
		}
		return diagnostics.stream()
			.map(Diagnostic::message)
			.map(MessageServerRuntime::boundedAssetDiagnostic)
			.limit(4L)
			.reduce((left, right) -> left + "; " + right)
			.orElse("none");
	}

	/**
	 * Allows trusted server/data-pack sources and the current host, but never grants authority
	 * merely because a non-host player is an operator.
	 */
	public static boolean authorizedSource(final CommandSourceStack source) {
		Objects.requireNonNull(source, "source");
		PermissionContext context = source.getPermissionContext();
		return authorizedPermissionContextLazy(
			context.type(),
			context.uuid(),
			() -> {
				MinecraftServer server = source.getServer();
				return server == null
					? Optional.empty()
					: PixelTzzWorldState.get(server).hostId();
			}
		);
	}

	/**
	 * Resolves persisted host state only for a real player provenance. Function parsing and
	 * command-tree projection use synthetic SYSTEM sources whose server field is deliberately
	 * null; those paths must remain provenance-only and never touch a world save.
	 */
	static boolean authorizedPermissionContextLazy(
		final PermissionContext.Type type,
		final UUID ownerId,
		final Supplier<Optional<UUID>> hostIdSupplier
	) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(hostIdSupplier, "hostIdSupplier");
		return switch (type) {
			case SYSTEM -> true;
			case ENTITY, OTHER -> false;
			case PLAYER -> authorizedPermissionContext(
				PermissionContext.Type.PLAYER,
				ownerId,
				Objects.requireNonNull(hostIdSupplier.get(), "hostId")
			);
		};
	}

	/**
	 * Keeps command provenance separate from the entity selected as {@code @s}. Data-pack
	 * functions remain SYSTEM even after {@code execute as}; a non-host operator remains PLAYER
	 * and therefore cannot acquire message authority merely through vanilla permission level.
	 */
	static boolean authorizedPermissionContext(
		final PermissionContext.Type type,
		final UUID ownerId,
		final Optional<UUID> hostId
	) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(hostId, "hostId");
		return switch (type) {
			case SYSTEM -> true;
			case PLAYER -> hostId.filter(ownerId::equals).isPresent();
			case ENTITY, OTHER -> false;
		};
	}

	/**
	 * Context bypass is deliberately narrower than ordinary message authority. A host player is
	 * allowed to play and preview messages, but only a provenance-preserving SYSTEM source such as
	 * the dedicated server console or a data-pack function may bypass registered game context.
	 */
	static boolean authorizedContextBypassSource(final CommandSourceStack source) {
		Objects.requireNonNull(source, "source");
		return authorizedContextBypassPermissionContext(
			source.getPermissionContext().type()
		);
	}

	static boolean authorizedContextBypassPermissionContext(
		final PermissionContext.Type type
	) {
		return Objects.requireNonNull(type, "type") == PermissionContext.Type.SYSTEM;
	}

	public static PlayCommandResult play(
		final CommandSourceStack source,
		final Identifier cueId,
		final Set<UUID> requestedTargets,
		final Optional<StorageArguments> storage
	) {
		return play(source, cueId, requestedTargets, storage, false);
	}

	public static PlayCommandResult play(
		final CommandSourceStack source,
		final Identifier cueId,
		final Set<UUID> requestedTargets,
		final Optional<StorageArguments> storage,
		final boolean bypassContext
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(cueId, "cueId");
		Objects.requireNonNull(requestedTargets, "requestedTargets");
		Objects.requireNonNull(storage, "storage");
		MinecraftServer server = source.getServer();
		if (!server.isSameThread()) {
			return PlayCommandResult.failed("动态消息只能在服务端线程发起。");
		}
		if (!authorizedSource(source)) {
			return PlayCommandResult.failed("只有当前主持人或数据包函数可以发起动态消息。");
		}
		if (bypassContext && !authorizedContextBypassSource(source)) {
			return PlayCommandResult.failed(
				"只有受信任的数据包函数或服务端控制台可以绕过游戏上下文。"
			);
		}
		DefinitionSnapshot definitions = DefinitionRegistry.INSTANCE.view().active();
		MessageCueDefinition cue = definitions.messageCatalog().messageCues().get(cueId);
		if (cue == null) {
			return disabledCueMessage(definitions, cueId);
		}
		return playResolvedCue(
			source,
			definitions,
			cue,
			requestedTargets,
			storage,
			bypassContext,
			false
		);
	}

	/**
	 * Trusted hook entry point bound to the definition snapshot owned by the committing
	 * transaction.
	 *
	 * <p>Callers restoring a frozen flow or timeline must pass that restored snapshot so a later
	 * data-pack reload cannot silently substitute a different cue definition. It never reads
	 * command storage, never enters the context-bypass path, and does not depend on Brigadier
	 * permission state.</p>
	 */
	public static boolean playHook(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final Identifier cueId,
		final Set<UUID> requestedTargets,
		final Optional<UUID> invoker,
		final Map<String, String> arguments
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(cueId, "cueId");
		Objects.requireNonNull(requestedTargets, "requestedTargets");
		Objects.requireNonNull(invoker, "invoker");
		Objects.requireNonNull(arguments, "arguments");
		if (!server.isSameThread()) {
			logHookFailure(cueId, "动态消息 Hook 只能在服务端线程发起。");
			return false;
		}
		try {
			MessageCueDefinition cue = definitions.messageCatalog().messageCues().get(cueId);
			if (cue == null) {
				PlayCommandResult disabled = disabledCueMessage(definitions, cueId);
				logHookFailure(cueId, disabled.message());
				return false;
			}
			PlayCommandResult result = playResolvedCue(
				server.createCommandSourceStack(),
				definitions,
				cue,
				requestedTargets,
				Map.copyOf(arguments),
				invoker,
				false
			);
			if (!result.successful()) {
				logHookFailure(cueId, result.message());
			}
			return result.successful();
		} catch (RuntimeException error) {
			logHookFailure(
				cueId,
				error.getClass().getSimpleName() + ": " + safeMessage(error)
			);
			return false;
		}
	}

	private static void logHookFailure(
		final Identifier cueId,
		final String diagnostic
	) {
		PixelTzzPro.LOGGER.warn(
			"V3B declarative message hook {} was skipped: {}",
			cueId,
			boundedText(
				Objects.requireNonNullElse(diagnostic, "unknown hook failure"),
				256
			)
		);
	}

	/**
	 * Starts one isolated preview for the current online host.
	 *
	 * <p>The catalog sanitizer removes callbacks, history writes, context restrictions, and every
	 * concurrency/control-group relationship before this method enters the shared play pipeline.
	 * No command form accepts an alternate preview audience.
	 */
	public static PlayCommandResult preview(
		final CommandSourceStack source,
		final Identifier cueId,
		final Optional<StorageArguments> storage
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(cueId, "cueId");
		Objects.requireNonNull(storage, "storage");
		MinecraftServer server = source.getServer();
		if (!server.isSameThread()) {
			return PlayCommandResult.failed("动态消息只能在服务端线程预览。");
		}
		if (!authorizedSource(source)) {
			return PlayCommandResult.failed(
				"只有当前主持人或数据包函数可以预览动态消息。"
			);
		}

		UUID host = PixelTzzWorldState.get(server).hostId().orElse(null);
		if (host == null) {
			return PlayCommandResult.failed("当前世界尚未指定主持人，不能预览动态消息。");
		}
		if (server.getPlayerList().getPlayer(host) == null) {
			return PlayCommandResult.failed("当前主持人不在线，不能预览动态消息。");
		}

		DefinitionSnapshot definitions = DefinitionRegistry.INSTANCE.view().active();
		var catalogResult = MessageCommandCatalog.preview(definitions, cueId, host);
		if (!catalogResult.accepted()) {
			return PlayCommandResult.failed(catalogResult.message());
		}
		HostPreview preview = catalogResult.value().orElseThrow();
		return playResolvedCue(
			source,
			definitions,
			preview.definition(),
			preview.callTargets(),
			storage,
			false,
			true
		);
	}

	/**
	 * Replays one already-authorized history record for its original viewer.
	 *
	 * <p>The caller performs terminal-session and opaque-record-key authorization. This second
	 * boundary rechecks the persisted record, active game scope, viewer and cue catalog on the
	 * server thread, then enters the ordinary scheduler only through a sanitized, transient,
	 * callback-free single-viewer definition.
	 */
	public static PlayCommandResult replayHistory(
		final ServerPlayer viewer,
		final MessageHistoryRecord requestedRecord
	) {
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(requestedRecord, "requestedRecord");
		MinecraftServer server = viewer.level().getServer();
		if (!server.isSameThread()) {
			return PlayCommandResult.failed("历史重播只能在服务端线程发起。");
		}
		WorldStateV4 root = PixelTzzWorldState.get(server).currentV4().orElse(null);
		UUID activeGameInstance = root == null
			? null
			: root.core().activeGameInstanceId().orElse(null);
		if (
			root == null
				|| activeGameInstance == null
				|| !requestedRecord.viewerId().equals(viewer.getUUID())
				|| !requestedRecord.gameInstanceId().equals(activeGameInstance)
				|| !requestedRecord.replayAllowed()
				|| root.messageHistoryFor(viewer.getUUID())
					.stream()
					.noneMatch(requestedRecord::equals)
		) {
			return PlayCommandResult.failed("这条公开记录当前不可重播。");
		}
		DefinitionSnapshot definitions = DefinitionRegistry.INSTANCE.view().active();
		MessageCueDefinition source = definitions.messageCatalog()
			.messageCues()
			.get(requestedRecord.cueId());
		if (source == null) {
			return PlayCommandResult.failed("这条记录对应的演出当前不可用。");
		}
		final MessageHistoryReplaySanitizer.ReplayCue replay;
		try {
			replay = MessageHistoryReplaySanitizer.sanitize(
				source,
				viewer.getUUID(),
				requestedRecord.savedFields()
			);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"V3B history replay {} for {} was rejected by the replay sanitizer",
				requestedRecord.messageInstanceId(),
				viewer.getUUID(),
				error
			);
			return PlayCommandResult.failed("这条记录无法安全重播。");
		}
		return playResolvedCue(
			server.createCommandSourceStack(),
			definitions,
			replay.definition(),
			replay.callTargets(),
			Optional.empty(),
			false,
			false
		);
	}

	private static PlayCommandResult playResolvedCue(
		final CommandSourceStack source,
		final DefinitionSnapshot definitions,
		final MessageCueDefinition cue,
		final Set<UUID> requestedTargets,
		final Optional<StorageArguments> storage,
		final boolean bypassContext,
		final boolean hostPreview
	) {
		Identifier cueId = cue.id();
		MinecraftServer server = source.getServer();
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (world == null) {
			return PlayCommandResult.failed("当前世界状态不可用于动态消息。");
		}
		Map<String, String> arguments;
		try {
			arguments = storage.isPresent()
				? readStorageArguments(server, storage.orElseThrow())
				: Map.of();
		} catch (RuntimeException error) {
			return PlayCommandResult.failed("无法读取动态消息参数：" + safeMessage(error));
		}
		Optional<UUID> invoker = Optional.ofNullable(source.getPlayer())
			.map(ServerPlayer::getUUID);
		return playResolvedCue(
			source,
			definitions,
			cue,
			requestedTargets,
			arguments,
			invoker,
			bypassContext,
			hostPreview
		);
	}

	private static PlayCommandResult playResolvedCue(
		final CommandSourceStack source,
		final DefinitionSnapshot definitions,
		final MessageCueDefinition cue,
		final Set<UUID> requestedTargets,
		final Map<String, String> arguments,
		final Optional<UUID> invoker,
		final boolean hostPreview
	) {
		Objects.requireNonNull(arguments, "arguments");
		Objects.requireNonNull(invoker, "invoker");
		final boolean bypassContext = false;
		return playResolvedCue(
			source,
			definitions,
			cue,
			requestedTargets,
			arguments,
			invoker,
			bypassContext,
			hostPreview
		);
	}

	private static PlayCommandResult playResolvedCue(
		final CommandSourceStack source,
		final DefinitionSnapshot definitions,
		final MessageCueDefinition cue,
		final Set<UUID> requestedTargets,
		final Map<String, String> arguments,
		final Optional<UUID> invoker,
		final boolean bypassContext,
		final boolean hostPreview
	) {
		Identifier cueId = cue.id();
		MinecraftServer server = source.getServer();
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (world == null) {
			return PlayCommandResult.failed("当前世界状态不可用于动态消息。");
		}
		LinkedHashMap<String, String> effectiveArguments = new LinkedHashMap<>(arguments);
		if (requestedTargets.size() == 1) {
			effectiveArguments.putIfAbsent(
				"target",
				requestedTargets.iterator().next().toString()
			);
		}
		UUID requestedInstanceId = UUID.randomUUID();
		MessageInstanceAuthority.InvocationContext context =
			new MessageInstanceAuthority.InvocationContext(
				requestedInstanceId,
				true,
				invoker,
				Set.copyOf(requestedTargets),
				world.core().activeGameId(),
				world.core().activePhaseId(),
				world.timeline()
					.flatMap(WorldStateV3.TimelineInstance::currentTask)
					.map(WorldStateV3.TaskInstance::taskId),
				Map.copyOf(effectiveArguments),
				definitions.generation(),
				bypassContext,
				true
			);
		InvocationEnvironment environment = InvocationEnvironment.from(
			source,
			requestedTargets,
			effectiveArguments
		);
		ClockSample clock = clock(server);
		DeliveryPreparation[] preparedDelivery = new DeliveryPreparation[1];
		PlayResult result = MessageInstanceAuthority.play(
			runtimeState,
			cue,
			context,
			clock,
			(ignoredCue, ignoredContext) ->
				MessageMinecraftResolver.resolveParameters(
					server,
					definitions,
					world,
					cue,
					context,
					environment
				),
			(audience, ignoredCue, ignoredContext, canonicalParameters) -> {
				AudienceResolution resolved = MessageMinecraftResolver.resolveAudience(
					server,
					definitions,
					world,
					cue,
					context,
					audience
				);
				DeliveryPreparation prepared = prepareInitialDelivery(
					server,
					cue,
					resolved,
					clock
				);
				preparedDelivery[0] = prepared;
				return prepared.effectiveAudience();
			}
		);
		if (
			result.disposition() == PlayDisposition.REJECTED
				|| result.disposition() == PlayDisposition.IGNORED
		) {
			return PlayCommandResult.failed(localizedAuthorityMessage(result.message()));
		}

		/*
		 * Store frozen integration context before committing the authority result so a projection
		 * belonging to a queued invocation can resolve immediately when that queue later drains.
		 */
		UUID reservedId = result.instanceId().orElse(requestedInstanceId);
		ENVIRONMENTS.put(reservedId, environment);
		DEFINITIONS.put(reservedId, definitions);
		if (
			result.disposition() != PlayDisposition.REFRESHED
				&& preparedDelivery[0] != null
		) {
			preparedDelivery[0].session().ifPresent(session -> {
				session.bindInstanceId(reservedId);
				DELIVERIES.put(reservedId, session);
			});
		}
		if (result.disposition() == PlayDisposition.QUEUED) {
			QUEUED_DELIVERY_IDS.add(reservedId);
		}
		runtimeState = result.nextState();
		processCommittedWork(server, result.projections(), result.callbacks(), clock);

		MessageInstance instance = runtimeState.instances().get(reservedId);
		int affected = instance == null
			? preparedDelivery[0] == null
				? requestedTargets.size()
				: preparedDelivery[0].effectiveAudience().cueTargets().size()
			: instance.cueTargets().size();
		String message = switch (result.disposition()) {
			case CREATED -> hostPreview
				? "已向主持人发起动态消息预览「" + cueId + "」。"
				: "已发起动态消息「" + cueId + "」，目标 " + affected + " 人。";
			case REFRESHED -> hostPreview
				? "已原位刷新动态消息预览「" + cueId + "」。"
				: "已原位刷新动态消息「" + cueId + "」。";
			case QUEUED -> hostPreview
				? "动态消息预览「" + cueId + "」已进入受控队列。"
				: "动态消息「" + cueId + "」已进入受控队列。";
			default -> localizedAuthorityMessage(result.message());
		};
		return PlayCommandResult.succeeded(message, affected, reservedId);
	}

	public static ControlCommandResult controlInstance(
		final CommandSourceStack source,
		final UUID instanceId,
		final ControlAction action
	) {
		Objects.requireNonNull(instanceId, "instanceId");
		return control(
			source,
			new InstanceSelector(instanceId),
			action,
			"message instance controlled by command"
		);
	}

	public static ControlCommandResult controlCue(
		final CommandSourceStack source,
		final Identifier cueId,
		final ControlAction action
	) {
		Objects.requireNonNull(cueId, "cueId");
		return control(
			source,
			new CueSelector(cueId),
			action,
			"active message cue instances controlled by command"
		);
	}

	public static ControlCommandResult controlGroup(
		final CommandSourceStack source,
		final String group,
		final ControlAction action
	) {
		Objects.requireNonNull(group, "group");
		final GroupSelector selector;
		try {
			selector = new GroupSelector(group);
		} catch (IllegalArgumentException ignored) {
			return ControlCommandResult.failed(
				"动态消息控制分组必须包含 1～128 个字符。"
			);
		}
		return control(
			source,
			selector,
			action,
			"message control group controlled by command"
		);
	}

	public static ControlCommandResult controlTarget(
		final CommandSourceStack source,
		final UUID targetId,
		final ControlAction action
	) {
		Objects.requireNonNull(targetId, "targetId");
		return control(
			source,
			new TargetSelector(targetId),
			action,
			"message target instances controlled by command"
		);
	}

	private static ControlCommandResult control(
		final CommandSourceStack source,
		final ControlSelector selector,
		final ControlAction action,
		final String reason
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(selector, "selector");
		Objects.requireNonNull(action, "action");
		MinecraftServer server = source.getServer();
		if (!server.isSameThread()) {
			return ControlCommandResult.failed(
				"动态消息只能在服务端线程控制。"
			);
		}
		if (!authorizedSource(source)) {
			return ControlCommandResult.failed(
				"只有当前主持人或数据包函数可以控制动态消息。"
			);
		}
		ClockSample clock = clock(server);
		pruneInvalidQueuedInvocations(server);
		ControlResult result = MessageInstanceAuthority.control(
			runtimeState,
			selector,
			action,
			clock,
			reason
		);
		if (!result.accepted()) {
			return ControlCommandResult.failed(
				localizedAuthorityMessage(result.message())
			);
		}
		runtimeState = result.nextState();
		processCommittedWork(server, result.projections(), result.callbacks(), clock);
		String actionName = switch (action) {
			case PAUSE -> "暂停";
			case RESUME -> "继续";
			case COMPLETE -> "完成";
			case CANCEL -> "取消";
		};
		String message = result.changed() == 0
			? "匹配的动态消息已处于目标状态，未重复执行。"
			: "已" + actionName + "动态消息实例 "
				+ result.changed() + " 个。";
		return ControlCommandResult.succeeded(
			message,
			result.matched(),
			result.changed()
		);
	}

	private static DeliveryPreparation prepareInitialDelivery(
		final MinecraftServer server,
		final MessageCueDefinition cue,
		final AudienceResolution resolved,
		final ClockSample clock
	) {
		if (!resolved.resolved()) {
			return new DeliveryPreparation(resolved, Optional.empty());
		}
		if (
			cue.policies().delivery().offlineTargets()
					== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode.FINAL_ONLY
				&& cue.staticFallback().isEmpty()
		) {
			return new DeliveryPreparation(
				AudienceResolution.rejected(
					"offline_targets=final_only 需要注册 static_fallback，"
						+ "以便掉线和重启后仍能投递冻结最终内容"
				),
				Optional.empty()
			);
		}
		Set<UUID> online = new LinkedHashSet<>();
		for (UUID target : resolved.cueTargets()) {
			if (server.getPlayerList().getPlayer(target) != null) {
				online.add(target);
			}
		}
		MessageDeliveryLifecycleAuthority authority =
			MessageDeliveryLifecycleAuthority.begin(
				cue,
				resolved.cueTargets(),
				online,
				clock.presentationNanos()
			);
		InitialDecision initial = authority.initialDecision();
		if (initial.instanceDirective() == InstanceDirective.FAIL) {
			String diagnostic = initial.diagnostics().isEmpty()
				? "消息投递策略拒绝了当前受众"
				: initial.diagnostics().getFirst().message();
			return new DeliveryPreparation(
				AudienceResolution.rejected(diagnostic),
				Optional.empty()
			);
		}

		Set<UUID> effectiveTargets = new LinkedHashSet<>(initial.onlineStart());
		effectiveTargets.addAll(initial.pending());
		Map<String, Set<UUID>> effectiveNodeTargets = new LinkedHashMap<>();
		resolved.nodeTargets().forEach((nodeId, targets) -> {
			Set<UUID> filtered = new LinkedHashSet<>(targets);
			filtered.retainAll(effectiveTargets);
			if (!filtered.equals(effectiveTargets)) {
				effectiveNodeTargets.put(nodeId, Set.copyOf(filtered));
			}
		});
		AudienceResolution effective = AudienceResolution.resolved(
			Set.copyOf(effectiveTargets),
			Map.copyOf(effectiveNodeTargets)
		);
		return new DeliveryPreparation(
			effective,
			Optional.of(
				new DeliverySession(
					authority,
					resolved.cueTargets(),
					initial.directives(),
					initial.pending(),
					clock.gameTick(),
					cue
				)
			)
		);
	}

	private static void onServerTick(final MinecraftServer server) {
		if (activeServer != server) {
			return;
		}
		ClockSample clock = clock(server);
		pruneInvalidQueuedInvocations(server);
		expireOfflineDeliveries(server, clock);
		reauthorizeLiveInstances(server, clock);
		advancePendingFinalOnlyDeliveries(server, clock);
		advanceRecipientSchedules(server, clock);
		captureDueFields(server, clock);
		advanceRecipientSchedules(server, clock);
		AdvanceResult result = MessageInstanceAuthority.advance(runtimeState, clock);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B message runtime advancement rejected: {}",
				result.message()
			);
			return;
		}
		if (result.changed()) {
			runtimeState = result.nextState();
			processCommittedWork(server, result.projections(), result.callbacks(), clock);
		}
		captureDueFields(server, clock);
		if (
			server.getTickCount() % 20 == 0
				&& runtimeState.instances().values().stream().anyMatch(instance ->
					!instance.status().terminal()
						&& instance.cue().policies().lifecycle().restart()
							!= io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode.TRANSIENT
				)
		) {
			checkpointRuntime(
				server,
				clock,
				"periodic persistent message progress"
			);
		}
	}

	private static void pruneInvalidQueuedInvocations(
		final MinecraftServer server
	) {
		if (runtimeState.queue().isEmpty()) {
			return;
		}
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		Set<UUID> rejected = new LinkedHashSet<>();
		for (MessageInstanceAuthority.QueuedInvocation queued : runtimeState.queue()) {
			boolean contextAllowed = queued.context().bypassContext()
				|| world != null && queuedContextStillAllowed(queued, world);
			if (
				!contextAllowed
					|| queuedDeliveryMustFail(server, queued)
			) {
				rejected.add(queued.instanceId());
			}
		}
		if (rejected.isEmpty()) {
			return;
		}
		var removal = MessageInstanceAuthority.removeQueuedInvocations(
			runtimeState,
			rejected
		);
		if (!removal.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B queued message revalidation could not be committed: {}",
				removal.message()
			);
			return;
		}
		runtimeState = removal.nextState();
		for (UUID instanceId : removal.removedInstanceIds()) {
			ENVIRONMENTS.remove(instanceId);
			DEFINITIONS.remove(instanceId);
			PARAMETER_USES.remove(instanceId);
			DELIVERIES.remove(instanceId);
			SYNCHRONIZATIONS.remove(instanceId);
			QUEUED_DELIVERY_IDS.remove(instanceId);
		}
	}

	private static boolean queuedContextStillAllowed(
		final MessageInstanceAuthority.QueuedInvocation queued,
		final WorldStateV3 world
	) {
		if (queued.context().bypassContext()) {
			return true;
		}
		var policy = queued.cue().policies().context();
		Optional<Identifier> game = world.core().activeGameId();
		Optional<Identifier> phase = world.core().activePhaseId();
		Optional<Identifier> task = world.timeline()
			.flatMap(WorldStateV3.TimelineInstance::currentTask)
			.map(WorldStateV3.TaskInstance::taskId);
		return (
			queued.cue().game().isEmpty() || queued.cue().game().equals(game)
		)
			&& matchesContext(policy.games(), game)
			&& matchesContext(policy.phases(), phase)
			&& matchesContext(policy.tasks(), task);
	}

	private static boolean matchesContext(
		final Set<Identifier> allowed,
		final Optional<Identifier> actual
	) {
		return allowed.isEmpty() || actual.filter(allowed::contains).isPresent();
	}

	private static boolean queuedDeliveryMustFail(
		final MinecraftServer server,
		final MessageInstanceAuthority.QueuedInvocation queued
	) {
		Set<UUID> offline = new LinkedHashSet<>(queued.cueTargets());
		offline.removeIf(target -> server.getPlayerList().getPlayer(target) != null);
		if (offline.isEmpty()) {
			return false;
		}
		var delivery = queued.cue().policies().delivery();
		if (
			delivery.offlineTargets()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode.FAIL
		) {
			return true;
		}
		if (
			delivery.offlineTargets()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode.SKIP
				&& offline.size() == queued.cueTargets().size()
		) {
			return delivery.emptyAudience()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EmptyAudienceMode.FAIL;
		}
		return false;
	}

	private static void expireOfflineDeliveries(
		final MinecraftServer server,
		final ClockSample clock
	) {
		Set<UUID> online = server.getPlayerList()
			.getPlayers()
			.stream()
			.map(ServerPlayer::getUUID)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		for (Map.Entry<UUID, DeliverySession> entry : List.copyOf(DELIVERIES.entrySet())) {
			MessageInstance instance = runtimeState.instances().get(entry.getKey());
			if (instance == null || !instance.status().active()) {
				continue;
			}
			ExpiryDecision expired = entry.getValue()
				.authority
				.expire(clock.presentationNanos(), online);
			for (UUID target : expired.removed()) {
				entry.getValue().removePending(target);
			}
			for (UUID target : expired.finalTargets()) {
				deliverFinalOnly(server, instance, target, clock);
			}
			for (UUID target : expired.dropped()) {
				settleDeliveryTarget(server, instance, target, clock);
			}
			if (
				expired.instanceDirective() == InstanceDirective.COMPLETE
					&& runtimeState.instances()
						.getOrDefault(instance.instanceId(), instance)
						.status()
						.active()
			) {
				controlInstance(
					server,
					instance.instanceId(),
					ControlAction.COMPLETE,
					clock,
					"offline delivery TTL expired"
				);
			}
		}
	}

	private static void settleDeliveryTarget(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID target,
		final ClockSample clock
	) {
		MessageInstance current = runtimeState.instances().get(instance.instanceId());
		if (
			current == null
				|| !current.status().active()
				|| !current.cueTargets().contains(target)
				|| current.completedTargets().contains(target)
		) {
			return;
		}
		RecipientStream stream = STREAMS.get(
			new StreamKey(instance.instanceId(), target)
		);
		if (stream == null) {
			stream = new RecipientStream(
				new StreamKey(instance.instanceId(), target),
				0L,
				PlanDisposition.CREATE,
				Optional.empty(),
				current.elapsedAt(clock),
				SynchronizationMode.IMMEDIATE,
				Optional.empty()
			);
			STREAMS.put(stream.key, stream);
		}
		commitTargetCompletion(
			server,
			instance.instanceId(),
			target,
			clock,
			stream
		);
	}

	private static void deliverFinalOnly(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID target,
		final ClockSample clock
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(target);
		if (player == null) {
			settleDeliveryTarget(server, instance, target, clock);
			RecipientStream settled = STREAMS.get(
				new StreamKey(instance.instanceId(), target)
			);
			if (settled != null) {
				settled.pendingFinalOnly = false;
				settled.finalOnlyHandshakeDeadlineGameTick = OptionalLong.empty();
				settled.terminal = true;
			}
			return;
		}
		RecipientStream stream = STREAMS.get(
			new StreamKey(instance.instanceId(), target)
		);
		if (stream != null && stream.terminal) {
			settleDeliveryTarget(server, instance, target, clock);
			return;
		}
		if (stream == null) {
			stream = new RecipientStream(
				new StreamKey(instance.instanceId(), target),
				0L,
				PlanDisposition.CREATE,
				Optional.empty(),
				instance.elapsedAt(clock),
				SynchronizationMode.IMMEDIATE,
				Optional.empty()
			);
			STREAMS.put(stream.key, stream);
		}
		MessageFinalOnlyHandshakePolicy.Decision handshake =
			MessageFinalOnlyHandshakePolicy.evaluate(
				finalOnlyHandshakePending(server, instance, stream),
				clock.gameTick(),
				stream.finalOnlyHandshakeDeadlineGameTick
			);
		if (handshake.directive() == MessageFinalOnlyHandshakePolicy.Directive.WAIT) {
			stream.pendingFinalOnly = true;
			stream.finalOnlyHandshakeDeadlineGameTick = handshake.deadlineGameTick();
			return;
		}
		stream.pendingFinalOnly = false;
		stream.finalOnlyHandshakeDeadlineGameTick = OptionalLong.empty();
		boolean staticHandshakeFallback =
			handshake.directive()
				== MessageFinalOnlyHandshakePolicy.Directive.DELIVER_STATIC;
		DeferredCompletionWork completion = commitTargetCompletionDeferred(
			server,
			instance.instanceId(),
			target,
			clock,
			stream
		);
		if (!completion.accepted()) {
			return;
		}
		MessageInstance current = runtimeState.instances().get(instance.instanceId());
		if (current == null) {
			processCommittedWork(
				server,
				completion.projections(),
				completion.callbacks(),
				clock
			);
			return;
		}
		boolean completionCheckpointed = checkpointRuntime(
			server,
			clock,
			"target/all-complete facts before final-only projection"
		);
		stream.perPlayerAnchorElapsed = stream.perPlayerAnchorElapsed == null
			? stream.createdElapsed
			: stream.perPlayerAnchorElapsed;
		stream.visualAnchorNanos = clock.value(current.clockMode());

		Optional<Set<String>> authorized = authorizedFinalNodes(
			server,
			current,
			target
		);
		FinalProjectionResult projection = FinalProjectionResult.ZERO_VISIBLE;
		boolean visibleTextAuthorized = false;
		boolean staticFallbackAuthorized = false;
		if (authorized.isPresent() && !authorized.orElseThrow().isEmpty()) {
			staticFallbackAuthorized =
				MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
					current,
					target,
					authorized.orElseThrow()
				);
			MessageFinalProjectionPlanner.Plan planned = completeFinalPlan(
				current,
				target,
				authorized.orElseThrow(),
				stream.projectedOccurrences,
				stream.decidedOccurrences
			);
			visibleTextAuthorized = !planned.orderedOccurrences().isEmpty();
			if (completionCheckpointed && visibleTextAuthorized) {
				projection = recipientCanReceiveFinalPlan(server, current, stream)
					? compileAndSendCompleteFinalProjection(
						server,
						current,
						stream,
						authorized.orElseThrow(),
						clock
					)
					: FinalProjectionResult.FAILED;
			}
		}
		if (staticHandshakeFallback) {
			/*
			 * A client that never completes the capability/asset handshake may receive only the
			 * registered vanilla fallback, and only after the same current node/parameter
			 * authorization used by the dynamic final projection succeeded. Zero authorized text
			 * remains completely silent.
			 */
			settleFinalOnlyProjection(
				server,
				current,
				stream,
				clock,
				completionCheckpointed && staticFallbackAuthorized
			);
		} else if (projection == FinalProjectionResult.READY) {
			if (
				!sendFinalControlIfPossible(
					server,
					current,
					stream,
					clock,
					true
				)
			) {
				settleFinalOnlyProjection(
					server,
					current,
					stream,
					clock,
					staticFallbackAuthorized
				);
			}
		} else {
			settleFinalOnlyProjection(
				server,
				current,
				stream,
				clock,
				projection == FinalProjectionResult.FAILED
					&& staticFallbackAuthorized
			);
		}
		processCommittedWork(
			server,
			completion.projections(),
			completion.callbacks(),
			clock
		);
	}

	private static void settleFinalOnlyProjection(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock,
		final boolean deliverStaticFallback
	) {
		if (deliverStaticFallback) {
			sendStaticFallback(server, instance, stream.key.recipientId());
		}
		scrubFinalConnection(server, instance, stream, clock);
		stream.terminal = true;
	}

	private static void advancePendingFinalOnlyDeliveries(
		final MinecraftServer server,
		final ClockSample clock
	) {
		for (RecipientStream stream : List.copyOf(STREAMS.values())) {
			if (!stream.pendingFinalOnly || stream.terminal) {
				continue;
			}
			MessageInstance instance = runtimeState.instances().get(stream.key.instanceId());
			if (instance == null || !instance.status().active()) {
				stream.pendingFinalOnly = false;
				stream.finalOnlyHandshakeDeadlineGameTick = OptionalLong.empty();
				stream.terminal = true;
				continue;
			}
			deliverFinalOnly(server, instance, stream.key.recipientId(), clock);
		}
	}

	private static void reauthorizeLiveInstances(
		final MinecraftServer server,
		final ClockSample clock
	) {
		for (MessageInstance snapshot : List.copyOf(runtimeState.instances().values())) {
			if (!snapshot.status().active()) {
				continue;
			}
			WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
			CurrentContext currentContext = world == null
				? CurrentContext.empty()
				: new CurrentContext(
					world.core().activeGameId(),
					world.core().activePhaseId(),
					world.timeline()
						.flatMap(WorldStateV3.TimelineInstance::currentTask)
						.map(WorldStateV3.TaskInstance::taskId)
				);
			if (
				MessageLiveContextAuthority.resolve(
					snapshot.cue().game(),
					snapshot.cue().policies().context(),
					snapshot.context().bypassContext(),
					currentContext
				) == MessageLiveContextAuthority.Directive.CANCEL
			) {
				controlInstance(
					server,
					snapshot.instanceId(),
					ControlAction.CANCEL,
					clock,
					"live-strict message context is no longer authorized"
				);
				continue;
			}
			if (
				snapshot.cue().policies().audience().evolution()
					== AudienceEvolution.SNAPSHOT
			) {
				continue;
			}
			DefinitionSnapshot definitions = DEFINITIONS.get(snapshot.instanceId());
			InvocationEnvironment environment = ENVIRONMENTS.get(snapshot.instanceId());
			if (definitions == null || environment == null || world == null) {
				if (snapshot.cue().policies().audience().sensitive()) {
					applyReauthorization(
						server,
						snapshot,
						AudienceResolution.resolved(Set.of(), Map.of()),
						clock
					);
				}
				continue;
			}
			AudienceResolution audience;
			try {
				audience = MessageMinecraftResolver.resolveAudience(
					server,
					definitions,
					world,
					snapshot.cue(),
					snapshot.context(),
					snapshot.cue().policies().audience().audience()
				);
			} catch (RuntimeException error) {
				audience = AudienceResolution.rejected(
					"live audience resolver failed"
				);
			}
			if (
				!audience.resolved()
					&& snapshot.cue().policies().audience().sensitive()
			) {
				audience = AudienceResolution.resolved(Set.of(), Map.of());
			}
			if (audience.resolved()) {
				applyReauthorization(server, snapshot, audience, clock);
			}
		}
	}

	private static void applyReauthorization(
		final MinecraftServer server,
		final MessageInstance snapshot,
		final AudienceResolution audience,
		final ClockSample clock
	) {
		LiveAudienceDecision deliveryDecision = authorizeLiveAudience(
			server,
			snapshot,
			audience,
			clock
		);
		ReauthorizationResult result = MessageInstanceAuthority.reauthorize(
			runtimeState,
			snapshot.instanceId(),
			deliveryDecision.audience(),
			clock
		);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.warn(
				"V3B audience reauthorization rejected for {}: {}",
				snapshot.instanceId(),
				result.message()
			);
			return;
		}
		if (!result.changed()) {
			return;
		}
		runtimeState = result.nextState();
		processCommittedWork(server, result.projections(), result.callbacks(), clock);
		MessageInstance current = runtimeState.instances().get(snapshot.instanceId());
		if (current != null) {
			for (UUID target : deliveryDecision.finalOnlyTargets()) {
				deliverFinalOnly(server, current, target, clock);
			}
		}
	}

	private static LiveAudienceDecision authorizeLiveAudience(
		final MinecraftServer server,
		final MessageInstance instance,
		final AudienceResolution resolved,
		final ClockSample clock
	) {
		DeliverySession delivery = DELIVERIES.get(instance.instanceId());
		if (delivery == null || !resolved.resolved()) {
			return new LiveAudienceDecision(resolved, Set.of());
		}
		Set<UUID> effective = new LinkedHashSet<>(resolved.cueTargets());
		Set<UUID> finalOnly = new LinkedHashSet<>();
		Set<UUID> added = new LinkedHashSet<>(effective);
		added.removeAll(instance.cueTargets());
		for (UUID target : added) {
			if (server.getPlayerList().getPlayer(target) == null) {
				effective.remove(target);
				continue;
			}
			JoinDecision decision = delivery.authority.onJoin(
				target,
				true,
				clock.presentationNanos()
			);
			if (decision.directive().startsActiveDelivery()) {
				delivery.rememberJoin(decision);
				continue;
			}
			if (
				decision.directive() == DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY
					|| decision.directive()
						== DeliveryDirective.EXPIRE_FINAL_AND_REMOVE
			) {
				delivery.joinDirectives.put(
					target,
					DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY
				);
				finalOnly.add(target);
				continue;
			}
			effective.remove(target);
		}
		Map<String, Set<UUID>> nodes = new LinkedHashMap<>();
		resolved.nodeTargets().forEach((nodeId, targets) -> {
			Set<UUID> filtered = new LinkedHashSet<>(targets);
			filtered.retainAll(effective);
			if (!filtered.equals(effective)) {
				nodes.put(nodeId, Set.copyOf(filtered));
			}
		});
		return new LiveAudienceDecision(
			AudienceResolution.resolved(Set.copyOf(effective), Map.copyOf(nodes)),
			Set.copyOf(finalOnly)
		);
	}

	/**
	 * Rechecks sensitive/live-strict content immediately before a final projection. This is
	 * intentionally non-mutating: losing access means zero projection for this recipient, while the
	 * ordinary reauthorization transaction remains responsible for changing the authoritative
	 * instance on its normal server-tick path.
	 */
	private static Optional<Set<String>> authorizedFinalNodes(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		CurrentContext currentContext = world == null
			? CurrentContext.empty()
			: new CurrentContext(
				world.core().activeGameId(),
				world.core().activePhaseId(),
				world.timeline()
					.flatMap(WorldStateV3.TimelineInstance::currentTask)
					.map(WorldStateV3.TaskInstance::taskId)
			);
		if (
			MessageLiveContextAuthority.resolve(
				instance.cue().game(),
				instance.cue().policies().context(),
				instance.context().bypassContext(),
				currentContext
			) == MessageLiveContextAuthority.Directive.CANCEL
		) {
			return Optional.empty();
		}

		boolean liveCheck = instance.cue().policies().audience().sensitive()
			|| instance.cue().policies().audience().evolution()
				== AudienceEvolution.LIVE_STRICT;
		MessageFinalProjectionPlanner.FinalAudienceView snapshot =
			new MessageFinalProjectionPlanner.FinalAudienceView(
				instance.cueTargets(),
				instance.nodeTargets()
			);
		Optional<MessageFinalProjectionPlanner.FinalAudienceView> liveView =
			Optional.empty();
		if (liveCheck) {
			DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
			InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
			if (definitions == null || environment == null || world == null) {
				return Optional.empty();
			}
			final AudienceResolution resolved;
			try {
				resolved = MessageMinecraftResolver.resolveAudience(
					server,
					definitions,
					world,
					instance.cue(),
					instance.context(),
					instance.cue().policies().audience().audience()
				);
			} catch (RuntimeException error) {
				return Optional.empty();
			}
			if (!resolved.resolved()) {
				return Optional.empty();
			}
			liveView = Optional.of(
				new MessageFinalProjectionPlanner.FinalAudienceView(
					resolved.cueTargets(),
					resolved.nodeTargets()
				)
			);
		}
		Optional<Set<String>> authorized = MessageFinalProjectionPlanner.authorizedNodeIds(
			instance.cue(),
			recipient,
			snapshot,
			liveCheck,
			liveView
		);
		if (authorized.isEmpty()) {
			return Optional.empty();
		}
		Set<String> filtered = authorized.orElseThrow()
			.stream()
			.filter(nodeId ->
				recipientAuthorizedForNodeParameters(
					server,
					instance,
					recipient,
					nodeId
				)
			)
			.collect(
				java.util.stream.Collectors.toCollection(LinkedHashSet::new)
			);
		return Optional.of(Set.copyOf(filtered));
	}

	private static UseGraph parameterUses(final MessageInstance instance) {
		return PARAMETER_USES.computeIfAbsent(
			instance.instanceId(),
			ignored -> MessageParameterUseGraph.compile(instance.cue())
		);
	}

	private static boolean recipientAuthorizedForNode(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient,
		final String nodeId
	) {
		return instance.cueTargets().contains(recipient)
			&& instance.nodeTargets()
				.getOrDefault(nodeId, instance.cueTargets())
				.contains(recipient)
			&& recipientAuthorizedForNodeParameters(
				server,
				instance,
				recipient,
				nodeId
			);
	}

	private static Set<String> authorizedNodeIdsForRecipient(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		Set<String> authorized = new LinkedHashSet<>();
		for (CueNode node : instance.cue().nodes()) {
			if (
				recipientAuthorizedForNode(
					server,
					instance,
					recipient,
					node.id()
				)
			) {
				authorized.add(node.id());
			}
		}
		return Set.copyOf(authorized);
	}

	private static boolean recipientHasAuthorizedTextNode(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		return instance.cue().nodes().stream().anyMatch(node ->
			node instanceof TextNode
				&& recipientAuthorizedForNode(
					server,
					instance,
					recipient,
					node.id()
				)
		);
	}

	private static boolean recipientAuthorizedForNodeParameters(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient,
		final String nodeId
	) {
		Set<String> parameters = parameterUses(instance).parametersForNode(nodeId);
		if (
			parameters.stream().anyMatch(parameterId -> {
				var definition = instance.cue().parameters().get(parameterId);
				return definition == null
					|| (
						!definition.allowedNodes().isEmpty()
							&& !definition.allowedNodes().contains(nodeId)
					);
			})
		) {
			return false;
		}
		return recipientAuthorizedForParameters(
			server,
			instance,
			recipient,
			parameters
		);
	}

	private static boolean recipientAuthorizedForHistory(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		return definitions != null
			&& world != null
			&& recipientAuthorizedForHistory(
				server,
				definitions,
				world,
				instance,
				recipient
			);
	}

	private static boolean recipientAuthorizedForHistory(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 world,
		final MessageInstance instance,
		final UUID recipient
	) {
		Set<String> parameters = parameterUses(instance).historyParameters();
		if (
			parameters.stream().anyMatch(parameterId -> {
				var definition = instance.cue().parameters().get(parameterId);
				return definition == null || !definition.allowedNodes().isEmpty();
			})
		) {
			return false;
		}
		return parameters.stream().allMatch(parameterId ->
			MessageMinecraftResolver.parameterAuthorized(
				server,
				definitions,
				world,
				instance,
				recipient,
				parameterId
			)
		);
	}

	private static boolean recipientAuthorizedForParameters(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient,
		final Set<String> parameterIds
	) {
		if (parameterIds.isEmpty()) {
			return true;
		}
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (definitions == null || world == null) {
			return false;
		}
		return parameterIds.stream().allMatch(parameterId ->
			MessageMinecraftResolver.parameterAuthorized(
				server,
				definitions,
				world,
				instance,
				recipient,
				parameterId
			)
		);
	}

	private static boolean recipientCanReceiveFinalPlan(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| CAPABILITIES.get(stream.key.recipientId()) == null
				|| !ServerPlayNetworking.canSend(player, MessagePlanS2CPayload.TYPE)
		) {
			return false;
		}
		MessageAssetAuthority authority = assetAuthorityFor(instance);
		return authority != null && cueAssetsReady(authority, player, instance.cue().id());
	}

	private static boolean finalOnlyHandshakePending(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (player == null) {
			return false;
		}
		if (CAPABILITIES.get(stream.key.recipientId()) == null) {
			return true;
		}
		MessageAssetAuthority authority = assetAuthorityFor(instance);
		return authority != null
			&& authority.effectiveAssets(stream.key.recipientId()).isEmpty();
	}

	private static void advanceRecipientSchedules(
		final MinecraftServer server,
		final ClockSample clock
	) {
		for (RecipientStream stream : List.copyOf(STREAMS.values())) {
			MessageInstance instance = runtimeState.instances().get(stream.key.instanceId());
			if (
				instance == null
					|| !instance.context().externallyTimed()
					|| !instance.status().active()
					|| stream.terminal
					|| stream.pendingFinalOnly
			) {
				continue;
			}
			long elapsed;
			try {
				elapsed = instance.elapsedAt(clock);
			} catch (RuntimeException error) {
				continue;
			}
			advanceAuthoritativeCallbacks(server, instance, stream, elapsed, clock);
			if (!ensureSynchronizationDecision(server, instance, stream, clock)) {
				if (stream.synchronizationStaticFinalSent) {
					commitStaticFinalTargetWhenDue(server, instance, stream, elapsed, clock);
				}
				continue;
			}
			if (!stream.initialProjectionAttempted) {
				compileAndSendPlan(server, instance, stream, clock);
			}
			flushPendingDueOccurrences(server, instance, stream, clock);
			if (stream.terminal) {
				continue;
			}
			boolean progressed;
			int passes = 0;
			do {
				progressed = false;
				updateOccurrenceDurations(
					server,
					instance,
					stream,
					clock,
					elapsed
				);
				if (stream.terminal) {
					break;
				}
				for (CueNode node : instance.cue().nodes()) {
					if (
						node.header().schedule() instanceof OnCueEvent
							|| node instanceof CallbackNode
							|| !recipientAuthorizedForNode(
								server,
								instance,
								stream.key.recipientId(),
								node.id()
							)
					) {
						continue;
					}
					Optional<Long> base = exactNodeBase(
							instance,
							stream,
							node,
							new LinkedHashSet<>()
						);
					if (base.isEmpty()) {
						continue;
					}
					for (
						int occurrence = 0;
						occurrence < node.header().repeat().count();
						occurrence++
					) {
						NodeOccurrence key = new NodeOccurrence(node.id(), occurrence);
						if (stream.decidedOccurrences.contains(key)) {
							continue;
						}
						long dueAt = Math.addExact(
							base.orElseThrow(),
							Math.multiplyExact(
								node.header().repeat().interval().nanoseconds(),
								occurrence
							)
						);
						if (dueAt > elapsed) {
							continue;
						}
						if (
							node.header().when()
								.filter(condition ->
									condition.evaluateAt()
										== ConditionEvaluation.CUE_START
								)
								.isPresent()
								&& headerConditionRejected(stream, node, key)
						) {
							if (node instanceof CallbackNode) {
								resolveExternalCallback(
									server,
									instance,
									stream,
									node,
									key,
									clock
								);
							} else {
								skipOccurrence(stream, key);
							}
							progressed = true;
							continue;
						}
						Optional<Long> actualStart = node instanceof CallbackNode
							? Optional.of(dueAt)
							: resolveOccurrenceStart(
								server,
								instance,
								stream,
								node,
								key,
								dueAt,
								elapsed,
								clock
							);
						if (actualStart.isEmpty() || stream.terminal) {
							continue;
						}
						freezeOccurrenceConditions(
							server,
							instance,
							stream,
							node,
							key,
							ConditionEvaluation.NODE_START
						);
						if (headerConditionRejected(stream, node, key)) {
							if (node instanceof CallbackNode) {
								resolveExternalCallback(
									server,
									instance,
									stream,
									node,
									key,
									clock
								);
							} else {
								skipOccurrence(stream, key);
							}
							progressed = true;
							continue;
						}
						if (
							requiresFieldConditionDecision(stream, node, key)
						) {
							long decisionDelay = fieldConditionDelay(
								server,
								instance,
								stream,
								node
							);
							MessageOccurrencePlayback playback =
								stream.occurrencePlaybacks.get(key);
							if (
								playback == null
									|| (
										!playback.complete()
											&& playback.playbackElapsed() < decisionDelay
									)
							) {
								continue;
							}
							freezeOccurrenceConditions(
								server,
								instance,
								stream,
								node,
								key,
								ConditionEvaluation.FIELD_CAPTURE
							);
						}
						if (node instanceof CallbackNode) {
							resolveExternalCallback(
								server,
								instance,
								stream,
								node,
								key,
								clock
							);
						} else {
							compileDueOccurrence(
								server,
								instance,
								stream,
								key,
								Optional.of(
									occurrenceProjectionStart(
										stream,
										key,
										actualStart.orElseThrow()
									)
								),
								clock
							);
						}
						progressed = true;
					}
				}
				passes++;
			} while (progressed && passes <= instance.cue().nodes().size());
			updateOccurrenceDurations(
				server,
				instance,
				stream,
				clock,
				elapsed
			);
			if (stream.terminal) {
				continue;
			}
			Optional<Long> completion = exactTargetCompletion(server, instance, stream);
			if (
				completion.isPresent()
					&& completion.orElseThrow() <= elapsed
					&& !stream.targetCompletionCommitted
			) {
				commitTargetCompletion(
					server,
					instance.instanceId(),
					stream.key.recipientId(),
					clock,
					stream
				);
			}
		}
	}

	private static void freezeOccurrenceConditions(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node,
		final NodeOccurrence occurrence,
		final ConditionEvaluation evaluation
	) {
		for (ConditionSpec condition : nodeConditions(node)) {
			if (condition.evaluateAt() != evaluation) {
				continue;
			}
			ConditionDecisionKey key = new ConditionDecisionKey(
				occurrence,
				condition
			);
			stream.conditionDecisions.computeIfAbsent(
				key,
				ignored -> conditionMatches(
					server,
					instance,
					stream.key.recipientId(),
					condition
				)
			);
		}
	}

	private static boolean headerConditionRejected(
		final RecipientStream stream,
		final CueNode node,
		final NodeOccurrence occurrence
	) {
		return node.header().when()
			.map(condition ->
				Boolean.FALSE.equals(
					stream.conditionDecisions.get(
						new ConditionDecisionKey(occurrence, condition)
					)
				)
			)
			.orElse(false);
	}

	private static boolean requiresFieldConditionDecision(
		final RecipientStream stream,
		final CueNode node,
		final NodeOccurrence occurrence
	) {
		if (
			node.header().when()
				.filter(condition ->
					condition.evaluateAt() == ConditionEvaluation.FIELD_CAPTURE
				)
				.isPresent()
		) {
			return true;
		}
		if (!(node instanceof TextNode text)) {
			return false;
		}
		for (var variant : text.variants()) {
			ConditionSpec condition = variant.when();
			if (condition.evaluateAt() == ConditionEvaluation.FIELD_CAPTURE) {
				return true;
			}
			if (
				Boolean.TRUE.equals(
					stream.conditionDecisions.get(
						new ConditionDecisionKey(occurrence, condition)
					)
				)
			) {
				return false;
			}
		}
		return false;
	}

	private static long fieldConditionDelay(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node
	) {
		if (!(node instanceof TextNode text)) {
			return 0L;
		}
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		if (definitions == null) {
			return 0L;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(
			stream.key.recipientId()
		);
		String locale = player == null
			? "zh_cn"
			: player.clientInformation().language();
		return MessageProjectionCompiler.fieldConditionDelay(
			instance.cue(),
			text,
			locale,
			definitions.messageCatalog().textEffects()
		);
	}

	private static void skipOccurrence(
		final RecipientStream stream,
		final NodeOccurrence occurrence
	) {
		stream.decidedOccurrences.add(occurrence);
		stream.occurrenceDurations.put(occurrence, 0L);
	}

	private static List<ConditionSpec> nodeConditions(final CueNode node) {
		List<ConditionSpec> conditions = new ArrayList<>();
		node.header().when().ifPresent(conditions::add);
		if (node instanceof TextNode text) {
			text.variants().forEach(variant -> conditions.add(variant.when()));
		}
		return List.copyOf(conditions);
	}

	private static Optional<ScreenStartMode> recipientScreenGateMode(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		boolean closeSafe = false;
		for (CueNode node : instance.cue().nodes()) {
			if (
				!(node instanceof TextNode text)
					|| !recipientAuthorizedForNode(
						server,
						instance,
						recipient,
						node.id()
					)
			) {
				continue;
			}
			ScreenStartMode mode = screenStartMode(instance, text);
			if (mode == ScreenStartMode.WAIT_GAMEPLAY) {
				return Optional.of(ScreenStartMode.WAIT_GAMEPLAY);
			}
			if (mode == ScreenStartMode.CLOSE_SAFE_SCREEN) {
				closeSafe = true;
			}
		}
		return closeSafe
			? Optional.of(ScreenStartMode.CLOSE_SAFE_SCREEN)
			: Optional.empty();
	}

	private static MessageSynchronizationAuthority synchronizationAuthority(
		final MessageInstance instance
	) {
		return SYNCHRONIZATIONS.computeIfAbsent(
			instance.instanceId(),
			ignored -> {
				var timing = instance.cue().policies().timing();
				SynchronizationMode mode = timing.synchronization()
					.orElse(SynchronizationMode.SYNCHRONIZED);
				long wait = mode == SynchronizationMode.SYNCHRONIZED
					? timing.synchronizedWait().map(TimeSpan::nanoseconds).orElse(0L)
					: 0L;
				return new MessageSynchronizationAuthority(
					new Policy(
						mode,
						wait,
						timing.lateArrival(),
						timing.synchronizationTimeout()
					),
					0L,
					instance.cueTargets()
				);
			}
		);
	}

	private static boolean recipientSynchronizationReady(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| CAPABILITIES.get(stream.key.recipientId()) == null
				|| !ServerPlayNetworking.canSend(player, MessagePlanS2CPayload.TYPE)
		) {
			return false;
		}
		MessageAssetAuthority authority = assetAuthorityFor(instance);
		if (
			authority == null
				|| !cueAssetsReady(authority, player, instance.cue().id())
		) {
			return false;
		}
		/*
		 * Screen-start policy is a per-node presentation gate, not a capability or asset
		 * readiness failure. The server may safely project the authorized plan while a screen
		 * is open; both the client start gate and resolveOccurrenceStart keep wait_gameplay HUD
		 * nodes dormant. Folding the screen into this synchronization gate used to consume the
		 * short synchronized_wait window and turn a forced-page wait into late-arrival seeking.
		 */
		return true;
	}

	private static boolean ensureSynchronizationDecision(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		if (stream.synchronizationStaticFinal) {
			return applySynchronizationDecision(
				server,
				instance,
				stream,
				Decision.withoutAnchor(
					MessageSynchronizationAuthority.Directive.STATIC_FINAL
				),
				clock
			);
		}
		if (stream.perPlayerAnchorElapsed != null) {
			return true;
		}
		long elapsed;
		try {
			elapsed = instance.elapsedAt(clock);
		} catch (RuntimeException error) {
			return false;
		}
		MessageSynchronizationAuthority authority = synchronizationAuthority(instance);
		authority.addRecipient(stream.key.recipientId());
		authority.advance(elapsed);
		Decision decision = authority.decision(stream.key.recipientId());
		if (decision.directive() != MessageSynchronizationAuthority.Directive.PENDING) {
			return applySynchronizationDecision(
				server,
				instance,
				stream,
				decision,
				clock
			);
		}
		if (
			decision.directive() == MessageSynchronizationAuthority.Directive.PENDING
				&& recipientSynchronizationReady(server, instance, stream)
		) {
			decision = authority.markReady(stream.key.recipientId(), elapsed);
		}
		return applySynchronizationDecision(
			server,
			instance,
			stream,
			decision,
			clock
		);
	}

	private static boolean applySynchronizationDecision(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final Decision decision,
		final ClockSample clock
	) {
		switch (decision.directive()) {
			case START_SHARED, START_NOW, SEEK_SHARED -> {
				stream.perPlayerAnchorElapsed = decision.visualStartElapsedNanos()
					.orElseThrow();
				return true;
			}
			case STATIC_FINAL -> {
				stream.synchronizationStaticFinal = true;
				if (!stream.synchronizationStaticFinalSent) {
					Optional<Set<String>> authorized = authorizedFinalNodes(
						server,
						instance,
						stream.key.recipientId()
					);
					MessageFinalProjectionPlanner.Plan planned =
						authorized.isPresent()
							? completeFinalPlan(
								instance,
								stream.key.recipientId(),
								authorized.orElseThrow(),
								stream.projectedOccurrences,
								stream.decidedOccurrences
							)
							: new MessageFinalProjectionPlanner.Plan(
								List.of(),
								List.of()
							);
					boolean visibleTextAuthorized =
						!planned.orderedOccurrences().isEmpty();
					boolean staticFallbackAuthorized = authorized.isPresent()
						&& MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
							instance,
							stream.key.recipientId(),
							authorized.orElseThrow()
						);
					if (!visibleTextAuthorized) {
						settleSynchronizationStaticProjection(
							server,
							instance,
							stream,
							clock,
							false
						);
					} else if (
						recipientCanReceiveFinalPlan(server, instance, stream)
					) {
						stream.perPlayerAnchorElapsed = instance.elapsedAt(clock);
						FinalProjectionResult projection =
							compileAndSendCompleteFinalProjection(
								server,
								instance,
								stream,
								authorized.orElseThrow(),
								clock
							);
						if (projection == FinalProjectionResult.READY) {
							if (sendFinalControlIfPossible(
								server,
								instance,
								stream,
								clock,
								false
							)) {
								stream.synchronizationStaticFinalSent = true;
							} else {
								settleSynchronizationStaticProjection(
									server,
									instance,
									stream,
									clock,
									staticFallbackAuthorized
								);
							}
						} else if (
							projection == FinalProjectionResult.FAILED
						) {
							settleSynchronizationStaticProjection(
								server,
								instance,
								stream,
								clock,
								staticFallbackAuthorized
							);
						} else {
							settleSynchronizationStaticProjection(
								server,
								instance,
								stream,
								clock,
								false
							);
						}
					} else {
						/*
						 * A synchronization timeout may itself be caused by missing capabilities or
						 * assets. The vanilla fallback must therefore bypass the V3B readiness gate.
						 */
						settleSynchronizationStaticProjection(
							server,
							instance,
							stream,
							clock,
							staticFallbackAuthorized
						);
					}
				}
				return false;
			}
			case CANCELLED -> {
				if (!stream.synchronizationCancelApplied) {
					stream.synchronizationCancelApplied = true;
					controlInstance(
						server,
						instance.instanceId(),
						ControlAction.CANCEL,
						clock,
						"message synchronization timeout cancelled the cue"
					);
				}
				return false;
			}
			case PENDING -> {
				return false;
			}
		}
		throw new IllegalStateException("unhandled synchronization decision");
	}

	private static void settleSynchronizationStaticProjection(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock,
		final boolean deliverStaticFallback
	) {
		if (deliverStaticFallback) {
			sendStaticFallback(server, instance, stream.key.recipientId());
		}
		scrubConnectionProjection(server, instance, stream, clock);
		stream.synchronizationStaticFinalSent = true;
	}

	private static Optional<Long> resolveOccurrenceStart(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node,
		final NodeOccurrence occurrence,
		final long scheduledAt,
		final long elapsed,
		final ClockSample clock
	) {
		ScreenStartMode mode = node instanceof TextNode text
			? screenStartMode(instance, text)
			: ScreenStartMode.IMMEDIATE;
		if (
			mode == ScreenStartMode.IMMEDIATE
				|| mode == ScreenStartMode.FINAL_CHAT_ONLY
		) {
			long actual = stream.occurrenceActualStarts.computeIfAbsent(
				occurrence,
				ignored -> scheduledAt
			);
			if (node instanceof TextNode text) {
				advanceOccurrencePlayback(
					server,
					instance,
					stream,
					text,
					occurrence,
					scheduledAt,
					actual,
					elapsed,
					OptionalLong.empty(),
					clock
				);
			}
			return stream.terminal ? Optional.empty() : Optional.of(actual);
		}
		Long resolved = stream.occurrenceActualStarts.get(occurrence);
		if (resolved != null) {
			if (node instanceof TextNode text) {
				advanceOccurrencePlayback(
					server,
					instance,
					stream,
					text,
					occurrence,
					scheduledAt,
					resolved,
					elapsed,
					OptionalLong.empty(),
					clock
				);
			}
			if (stream.terminal) {
				return Optional.empty();
			}
			return Optional.of(resolved);
		}
		if (screenAllows(stream.key.recipientId(), mode)) {
			long actual = Math.max(scheduledAt, elapsed);
			stream.occurrenceActualStarts.put(occurrence, actual);
			shiftProjectedOccurrenceCaptures(
				stream,
				occurrence,
				actual - scheduledAt
			);
			if (node instanceof TextNode text) {
				advanceOccurrencePlayback(
					server,
					instance,
					stream,
					text,
					occurrence,
					scheduledAt,
					actual,
					elapsed,
					OptionalLong.empty(),
					clock
				);
			}
			if (stream.terminal) {
				return Optional.empty();
			}
			return Optional.of(actual);
		}
		Optional<TimeSpan> timeout = instance.cue()
			.policies()
			.lifecycle()
			.screenWaitTimeout();
		if (
			timeout.isPresent()
				&& elapsed - scheduledAt >= timeout.orElseThrow().nanoseconds()
		) {
			applyScreenTimeout(server, instance, stream, clock);
		}
		return Optional.empty();
	}

	private static long occurrenceProjectionStart(
		final RecipientStream stream,
		final NodeOccurrence occurrence,
		final long fallback
	) {
		MessageOccurrencePlayback playback = stream.occurrencePlaybacks.get(
			occurrence
		);
		return playback == null ? fallback : playback.projectedStart();
	}

	private static ScreenStartMode screenStartMode(
		final MessageInstance instance,
		final TextNode text
	) {
		return instance.cue()
			.policies()
			.lifecycle()
			.screenStart()
			.getOrDefault(text.channel(), ScreenStartMode.IMMEDIATE);
	}

	private static boolean screenAllows(
		final UUID recipient,
		final ScreenStartMode mode
	) {
		if (OFFLINE_PLAYERS.contains(recipient)) {
			return true;
		}
		ScreenReport report = SCREENS.get(recipient);
		if (report == null || report.payload().resourceReloading()) {
			return false;
		}
		MessageScreenStateC2SPayload payload = report.payload();
		return switch (mode) {
			case IMMEDIATE, FINAL_CHAT_ONLY -> true;
			case WAIT_GAMEPLAY ->
				payload.surface() == Surface.GAMEPLAY && !payload.worldPaused();
			case CLOSE_SAFE_SCREEN ->
				(
					payload.surface() == Surface.GAMEPLAY
						&& !payload.worldPaused()
				)
					|| payload.surface() == Surface.MOD_SCREEN;
		};
	}

	private static Step advanceOccurrencePlayback(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final TextNode text,
		final NodeOccurrence occurrence,
		final long scheduledAt,
		final long actualStart,
		final long elapsed,
		final OptionalLong visualDuration,
		final ClockSample clock
	) {
		MessageOccurrencePlayback playback = stream.occurrencePlaybacks
			.computeIfAbsent(
				occurrence,
				ignored -> new MessageOccurrencePlayback(
					scheduledAt,
					actualStart
				)
		);
		Step step;
		if (screenStartMode(instance, text) == ScreenStartMode.FINAL_CHAT_ONLY) {
			step = visualDuration.isPresent()
				? playback.finalizeWithoutAnimation(elapsed)
				: playback.advance(
					elapsed,
					ObscuredAction.CONTINUE,
					OptionalLong.empty(),
					OptionalLong.empty()
				);
		} else {
			step = playback.advance(
				elapsed,
				occurrenceObscuredAction(
					instance,
					stream.key.recipientId(),
					text.channel()
				),
				visualDuration,
				maxPause(instance)
			);
		}
		if (
			step.pausedDelta() > 0L
				&& stream.projectedOccurrences.contains(occurrence)
		) {
			shiftPendingOccurrenceCaptures(
				stream,
				occurrence,
				step.pausedDelta()
			);
		}
		if (step.pauseTimedOut()) {
			applyScreenTimeout(server, instance, stream, clock);
			return step;
		}
		step.completedAt().ifPresent(
			completedAt -> stream.occurrenceDurations.putIfAbsent(
				occurrence,
				Math.max(0L, completedAt - scheduledAt)
			)
		);
		return step;
	}

	private static ObscuredAction occurrenceObscuredAction(
		final MessageInstance instance,
		final UUID recipient,
		final TextChannel channel
	) {
		ScreenReport report = SCREENS.get(recipient);
		if (report == null || report.payload().resourceReloading()) {
			/*
			 * Start gates fail closed because no content has been exposed yet. Once an
			 * occurrence has begun, a missing/reloading report must not pause callbacks
			 * forever; dedicated reload/reconnect policies own those lifecycle events.
			 */
			return ObscuredAction.CONTINUE;
		}
		MessageScreenStateC2SPayload payload = report.payload();
		if (
			payload.surface() == Surface.GAMEPLAY
				&& !payload.worldPaused()
		) {
			return ObscuredAction.CONTINUE;
		}
		ObscuredMode mode = instance.cue()
			.policies()
			.lifecycle()
			.obscured()
			.getOrDefault(
				channel,
				channel == TextChannel.CHAT
					? ObscuredMode.CONTINUE
					: ObscuredMode.PAUSE
			);
		return switch (mode) {
			case CONTINUE -> ObscuredAction.CONTINUE;
			case PAUSE -> ObscuredAction.PAUSE;
			case FINALIZE -> ObscuredAction.FINALIZE;
			case CANCEL -> ObscuredAction.CANCEL;
		};
	}

	private static OptionalLong maxPause(final MessageInstance instance) {
		Optional<TimeSpan> configured = instance.cue()
			.policies()
			.lifecycle()
			.maxPause();
		return configured.isPresent()
			? OptionalLong.of(configured.orElseThrow().nanoseconds())
			: OptionalLong.empty();
	}

	private static void shiftProjectedOccurrenceCaptures(
		final RecipientStream stream,
		final NodeOccurrence occurrence,
		final long delay
	) {
		if (
			delay <= 0L
				|| !stream.projectedOccurrences.contains(occurrence)
				|| !stream.captureGateShifted.add(occurrence)
		) {
			return;
		}
		shiftPendingOccurrenceCaptures(stream, occurrence, delay);
	}

	private static void shiftPendingOccurrenceCaptures(
		final RecipientStream stream,
		final NodeOccurrence occurrence,
		final long delay
	) {
		if (delay <= 0L) {
			return;
		}
		for (
			Map.Entry<Integer, CaptureBinding> entry :
				List.copyOf(stream.captures.entrySet())
		) {
			CaptureBinding binding = entry.getValue();
			if (
				!binding.nodeId().equals(occurrence.nodeId())
					|| binding.occurrence() != occurrence.occurrence()
					|| stream.lockedSlots.contains(binding.slot())
			) {
				continue;
			}
			stream.captures.put(
				entry.getKey(),
				new CaptureBinding(
					binding.slot(),
					binding.identity(),
					binding.field(),
					binding.nodeId(),
					binding.occurrence(),
					Math.addExact(binding.captureOffsetNanos(), delay)
				)
			);
		}
	}

	private static void applyScreenTimeout(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		if (stream.screenTimeoutApplied || stream.terminal) {
			return;
		}
		stream.screenTimeoutApplied = true;
		ScreenTimeoutAction action = instance.cue()
			.policies()
			.lifecycle()
			.screenTimeoutAction();
		if (action == ScreenTimeoutAction.CANCEL) {
			sendControl(
				server,
				instance,
				Set.of(stream.key.recipientId()),
				MessageControlS2CPayload.Action.CANCEL,
				clock,
				false
			);
			stream.terminal = true;
			cancelTimedOutTarget(server, instance, stream, clock);
			return;
		}
		if (action == ScreenTimeoutAction.FINAL_CHAT_ONLY) {
			if (
				recipientHasAuthorizedTextNode(
					server,
					instance,
					stream.key.recipientId()
				)
			) {
				sendStaticFallback(server, instance, stream.key.recipientId());
			}
			sendControl(
				server,
				instance,
				Set.of(stream.key.recipientId()),
				MessageControlS2CPayload.Action.CANCEL,
				clock,
				false
			);
		} else {
			if (stream.plan == null) {
				if (
					recipientHasAuthorizedTextNode(
						server,
						instance,
						stream.key.recipientId()
					)
				) {
					sendStaticFallback(server, instance, stream.key.recipientId());
				}
			} else {
				sendControl(
					server,
					instance,
					Set.of(stream.key.recipientId()),
					MessageControlS2CPayload.Action.FINALIZE,
					clock,
					true
				);
			}
		}
		stream.terminal = true;
		if (!stream.targetCompletionCommitted) {
			commitTargetCompletion(
				server,
				instance.instanceId(),
				stream.key.recipientId(),
				clock,
				stream
			);
		}
	}

	private static void cancelTimedOutTarget(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		ControlResult result = MessageInstanceAuthority.cancelTarget(
			runtimeState,
			instance.instanceId(),
			stream.key.recipientId(),
			clock,
			"message screen wait timed out"
		);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.warn(
				"V3B screen-timeout target cancellation was rejected for {}: {}",
				stream.key.recipientId(),
				result.message()
			);
			return;
		}
		runtimeState = result.nextState();
		processCommittedWork(server, result.projections(), result.callbacks(), clock);
	}

	private static UUID callbackDriver(
		final MessageInstance instance,
		final CueNode node
	) {
		return instance.nodeTargets()
			.getOrDefault(node.id(), instance.cueTargets())
			.stream()
			.min(Comparator.comparing(UUID::toString))
			.orElseGet(() ->
				instance.context().invokerId()
					.orElse(instance.instanceId())
			);
	}

	private static void advanceAuthoritativeCallbacks(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final long elapsed,
		final ClockSample clock
	) {
		for (CueNode node : instance.cue().nodes()) {
			if (
				!(node instanceof CallbackNode)
					|| node.header().schedule() instanceof OnCueEvent
					|| !callbackDriver(instance, node).equals(stream.key.recipientId())
			) {
				continue;
			}
			Optional<Long> base = exactCallbackBase(instance, node);
			if (base.isEmpty()) {
				continue;
			}
			for (int occurrence = 0; occurrence < node.header().repeat().count(); occurrence++) {
				NodeOccurrence key = new NodeOccurrence(node.id(), occurrence);
				if (stream.decidedOccurrences.contains(key)) {
					continue;
				}
				long dueAt = Math.addExact(
					base.orElseThrow(),
					Math.multiplyExact(
						node.header().repeat().interval().nanoseconds(),
						occurrence
					)
				);
				if (dueAt > elapsed) {
					continue;
				}
				freezeOccurrenceConditions(
					server,
					instance,
					stream,
					node,
					key,
					ConditionEvaluation.NODE_START
				);
				resolveExternalCallback(server, instance, stream, node, key, clock);
				stream.decidedOccurrences.add(key);
			}
		}
	}

	private static Optional<Long> exactCallbackBase(
		final MessageInstance instance,
		final CueNode callback
	) {
		return exactAuthoritativeNodeBase(
			instance,
			callback,
			new LinkedHashSet<>()
		);
	}

	private static Optional<Long> exactAuthoritativeNodeBase(
		final MessageInstance instance,
		final CueNode node,
		final Set<String> visiting
	) {
		if (node.header().schedule() instanceof AtCueTime at) {
			return Optional.of(at.offset().nanoseconds());
		}
		if (!(node.header().schedule() instanceof AfterNode after)) {
			return Optional.empty();
		}
		if (!visiting.add(node.id())) {
			throw new IllegalArgumentException("message callback schedule contains a cycle");
		}
		CueNode dependency = instance.cue().nodes().stream()
			.filter(candidate -> candidate.id().equals(after.nodeId()))
			.findFirst()
			.orElse(null);
		if (dependency == null) {
			visiting.remove(node.id());
			return Optional.empty();
		}
		Optional<Long> dependencyBase = exactAuthoritativeNodeBase(
			instance,
			dependency,
			visiting
		);
		visiting.remove(node.id());
		if (dependencyBase.isEmpty()) {
			return Optional.empty();
		}
		long dependencyLastStart = Math.addExact(
			dependencyBase.orElseThrow(),
			Math.multiplyExact(
				dependency.header().repeat().interval().nanoseconds(),
				dependency.header().repeat().count() - 1L
			)
		);
		long dependencyEnd = Math.addExact(
			dependencyLastStart,
			instance.authoritativeNodeDurations()
				.getOrDefault(dependency.id(), new TimeSpan(0L))
				.nanoseconds()
		);
		return Optional.of(Math.addExact(dependencyEnd, after.offset().nanoseconds()));
	}

	private static void resolveExternalCallback(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node,
		final NodeOccurrence occurrence,
		final ClockSample clock
	) {
		Boolean frozen = stream.frozenCallbackDecisions.remove(occurrence);
		boolean included = node.header().when()
			.map(condition -> {
				Boolean decision = stream.conditionDecisions.get(
					new ConditionDecisionKey(occurrence, condition)
				);
				return decision != null
					? decision
					: frozen != null
						? frozen
						: false;
			})
			.orElse(true);
		AdvanceResult result = MessageInstanceAuthority.resolveExternalCallback(
			runtimeState,
			instance.instanceId(),
			node.id(),
			occurrence.occurrence(),
			included,
			clock
		);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.warn(
				"Externally timed callback {} could not be resolved: {}",
				occurrence,
				result.message()
			);
			return;
		}
		runtimeState = result.nextState();
		stream.decidedOccurrences.add(occurrence);
		stream.occurrenceDurations.put(occurrence, 0L);
		processCommittedWork(server, result.projections(), result.callbacks(), clock);
	}

	private static boolean conditionMatches(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient,
		final ConditionSpec condition
	) {
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (definitions == null || world == null) {
			return false;
		}
		return MessageMinecraftResolver.conditionMatches(
			server,
			definitions,
			world,
			instance,
			recipient,
			Optional.of(condition)
		);
	}

	private static Optional<Long> exactTargetCompletion(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		long completion = 0L;
		for (CueNode node : instance.cue().nodes()) {
			if (
				node instanceof CallbackNode
					|| node.header().schedule() instanceof OnCueEvent
					|| !recipientAuthorizedForNode(
						server,
						instance,
						stream.key.recipientId(),
						node.id()
					)
			) {
				continue;
			}
			Optional<Long> base = exactNodeBase(
				instance,
				stream,
				node,
				new LinkedHashSet<>()
			);
			if (base.isEmpty()) {
				return Optional.empty();
			}
			for (
				int occurrence = 0;
				occurrence < node.header().repeat().count();
				occurrence++
			) {
				NodeOccurrence key = new NodeOccurrence(node.id(), occurrence);
				Long duration = stream.occurrenceDurations.get(key);
				if (
					!stream.decidedOccurrences.contains(key)
						|| duration == null
				) {
					return Optional.empty();
				}
				long end = Math.addExact(
					Math.addExact(
						base.orElseThrow(),
						Math.multiplyExact(
							node.header().repeat().interval().nanoseconds(),
							occurrence
						)
					),
					duration
				);
				completion = Math.max(completion, end);
			}
		}
		return Optional.of(completion);
	}

	private static void commitStaticFinalTargetWhenDue(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final long elapsed,
		final ClockSample clock
	) {
		if (stream.targetCompletionCommitted) {
			return;
		}
		Optional<Long> completion = exactAuthoritativeTargetCompletion(
			server,
			instance,
			stream
		);
		if (completion.isPresent() && completion.orElseThrow() <= elapsed) {
			commitTargetCompletion(
				server,
				instance.instanceId(),
				stream.key.recipientId(),
				clock,
				stream
			);
		}
	}

	private static Optional<Long> exactAuthoritativeTargetCompletion(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		long completion = 0L;
		for (CueNode node : instance.cue().nodes()) {
			if (
				node instanceof CallbackNode
					|| node.header().schedule() instanceof OnCueEvent
					|| !recipientAuthorizedForNode(
						server,
						instance,
						stream.key.recipientId(),
						node.id()
					)
			) {
				continue;
			}
			Optional<Long> base = exactAuthoritativeNodeBase(
				instance,
				node,
				new LinkedHashSet<>()
			);
			if (base.isEmpty()) {
				return Optional.empty();
			}
			long lastStart = Math.addExact(
				base.orElseThrow(),
				Math.multiplyExact(
					node.header().repeat().interval().nanoseconds(),
					node.header().repeat().count() - 1L
				)
			);
			completion = Math.max(
				completion,
				Math.addExact(
					lastStart,
					instance.authoritativeNodeDurations()
						.getOrDefault(node.id(), new TimeSpan(0L))
						.nanoseconds()
				)
			);
		}
		return Optional.of(completion);
	}

	private static Optional<Long> exactNodeBase(
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node,
		final Set<String> visiting
	) {
		if (node.header().schedule() instanceof AtCueTime at) {
			return stream.perPlayerAnchorElapsed == null
				? Optional.empty()
				: Optional.of(
					Math.addExact(
						stream.perPlayerAnchorElapsed,
						at.offset().nanoseconds()
					)
				);
		}
		if (!(node.header().schedule() instanceof AfterNode after)) {
			return Optional.empty();
		}
		if (!visiting.add(node.id())) {
			throw new IllegalArgumentException(
				"message occurrence schedule contains a cycle"
			);
		}
		CueNode dependency = instance.cue().nodes().stream()
			.filter(candidate -> candidate.id().equals(after.nodeId()))
			.findFirst()
			.orElse(null);
		if (dependency == null) {
			return Optional.empty();
		}
		Optional<Long> dependencyBase = exactNodeBase(
			instance,
			stream,
			dependency,
			visiting
		);
		if (dependencyBase.isEmpty()) {
			visiting.remove(node.id());
			return Optional.empty();
		}
		long dependencyEnd = dependencyBase.orElseThrow();
		for (
			int occurrence = 0;
			occurrence < dependency.header().repeat().count();
			occurrence++
		) {
			Long duration = stream.occurrenceDurations.get(
				new NodeOccurrence(dependency.id(), occurrence)
			);
			if (duration == null) {
				visiting.remove(node.id());
				return Optional.empty();
			}
			dependencyEnd = Math.max(
				dependencyEnd,
				Math.addExact(
					Math.addExact(
						dependencyBase.orElseThrow(),
						Math.multiplyExact(
							dependency.header().repeat().interval().nanoseconds(),
							occurrence
						)
					),
					duration
				)
			);
		}
		visiting.remove(node.id());
		return Optional.of(
			Math.addExact(dependencyEnd, after.offset().nanoseconds())
		);
	}

	private static void commitTargetCompletion(
		final MinecraftServer server,
		final UUID instanceId,
		final UUID recipient,
		final ClockSample clock,
		final RecipientStream stream
	) {
		DeferredCompletionWork deferred = commitTargetCompletionDeferred(
			server,
			instanceId,
			recipient,
			clock,
			stream
		);
		if (deferred.accepted()) {
			processCommittedWork(
				server,
				deferred.projections(),
				deferred.callbacks(),
				clock
			);
		}
	}

	private static DeferredCompletionWork commitTargetCompletionDeferred(
		final MinecraftServer server,
		final UUID instanceId,
		final UUID recipient,
		final ClockSample clock,
		final RecipientStream stream
	) {
		ControlResult result = MessageInstanceAuthority.completeTarget(
			runtimeState,
			instanceId,
			recipient,
			clock
		);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.warn(
				"Exact V3B target completion was rejected for {}: {}",
				recipient,
				result.message()
			);
			return DeferredCompletionWork.rejected();
		}
		runtimeState = result.nextState();
		stream.targetCompletionCommitted = true;
		DeliverySession delivery = DELIVERIES.get(instanceId);
		if (delivery != null) {
			delivery.authority.markDeliveryComplete(recipient);
		}
		return new DeferredCompletionWork(
			true,
			result.projections(),
			result.callbacks()
		);
	}

	private static void processCommittedWork(
		final MinecraftServer server,
		final List<ProjectionEvent> projections,
		final List<PreparedCallback> callbacks,
		final ClockSample clock
	) {
		boolean historyReady = persistTerminalHistory(server, clock);
		boolean checkpointed = historyReady
			&& checkpointRuntime(
				server,
				clock,
				callbacks.isEmpty()
					? "authority mutation before projection"
					: "PREPARED callback ledger before callback execution"
			);
		for (ProjectionEvent projection : projections) {
			publishProjection(server, projection, clock);
		}
		settleQueuedStartTargets(server, clock);
		for (PreparedCallback callback : callbacks) {
			if (
				!checkpointed
			) {
				PixelTzzPro.LOGGER.error(
					"V3B callback {} was not executed because its PREPARED marker "
						+ "could not be checkpointed",
					callback.key()
				);
			} else if (
				DELIVERY_BLOCKED_CALLBACKS.contains(callback.key().instanceId())
			) {
				recordCallbackFailure(
					server,
					callback,
					"delivery failed before the queued instance could start"
				);
			} else {
				executePreparedCallback(server, callback);
			}
		}
		if (historyReady && checkpointed) {
			retireCommittedTerminalInstances();
		}
	}

	private static void rebuildQueuedDeliveryAtStart(
		final MinecraftServer server,
		final MessageInstance instance,
		final ClockSample clock
	) {
		AudienceResolution frozen = AudienceResolution.resolved(
			instance.cueTargets(),
			instance.nodeTargets()
		);
		DeliveryPreparation prepared = prepareInitialDelivery(
			server,
			instance.cue(),
			frozen,
			clock
		);
		if (prepared.session().isEmpty()) {
			PixelTzzPro.LOGGER.error(
				"V3B queued delivery {} became invalid at actual start",
				instance.instanceId()
			);
			DELIVERY_BLOCKED_CALLBACKS.add(instance.instanceId());
			QUEUED_START_SETTLEMENTS.put(
				instance.instanceId(),
				instance.cueTargets()
			);
			return;
		}
		DeliverySession delivery = prepared.session().orElseThrow();
		delivery.bindInstanceId(instance.instanceId());
		DELIVERIES.put(instance.instanceId(), delivery);
		Set<UUID> skipped = new LinkedHashSet<>(instance.cueTargets());
		skipped.removeAll(prepared.effectiveAudience().cueTargets());
		if (!skipped.isEmpty()) {
			QUEUED_START_SETTLEMENTS.put(
				instance.instanceId(),
				Set.copyOf(skipped)
			);
		}
	}

	private static void settleQueuedStartTargets(
		final MinecraftServer server,
		final ClockSample clock
	) {
		if (QUEUED_START_SETTLEMENTS.isEmpty()) {
			return;
		}
		Map<UUID, Set<UUID>> pending = new LinkedHashMap<>(
			QUEUED_START_SETTLEMENTS
		);
		QUEUED_START_SETTLEMENTS.clear();
		for (Map.Entry<UUID, Set<UUID>> entry : pending.entrySet()) {
			MessageInstance instance = runtimeState.instances().get(entry.getKey());
			if (instance == null) {
				continue;
			}
			for (UUID target : entry.getValue()) {
				settleDeliveryTarget(server, instance, target, clock);
				instance = runtimeState.instances().get(entry.getKey());
				if (instance == null || !instance.status().active()) {
					break;
				}
			}
		}
	}

	private static void publishProjection(
		final MinecraftServer server,
		final ProjectionEvent event,
		final ClockSample clock
	) {
		MessageInstance instance = runtimeState.instances().get(event.instanceId());
		if (instance == null) {
			PixelTzzPro.LOGGER.warn(
				"V3B projection references missing committed instance {}",
				event.instanceId()
			);
			return;
		}
		if (
			event.kind() == ProjectionKind.CREATE
				&& QUEUED_DELIVERY_IDS.remove(event.instanceId())
		) {
			rebuildQueuedDeliveryAtStart(server, instance, clock);
		}
		switch (event.kind()) {
			case CREATE, REFRESH, REPLACE -> {
				for (UUID recipient : event.recipients()) {
					prepareRecipientStream(
						server,
						instance,
						recipient,
						event.kind(),
						event.replacedInstanceId(),
						clock
					);
				}
			}
			case NODE_DUE -> {
				for (UUID recipient : event.recipients()) {
					NodeOccurrence occurrence = new NodeOccurrence(
						event.nodeId().orElseThrow(),
						event.occurrence()
					);
					StreamKey key = new StreamKey(event.instanceId(), recipient);
					RecipientStream stream = STREAMS.get(key);
					if (stream == null) {
						prepareRecipientStream(
							server,
							instance,
							recipient,
							ProjectionKind.CREATE,
							Optional.empty(),
							clock
						);
						stream = STREAMS.get(key);
					}
					if (stream == null) {
						continue;
					}
					/*
					 * A local integrated client can publish its capabilities one server tick after
					 * remote clients. Do not let a due-at-zero node establish a partial CREATE while
					 * synchronization is still pending; the later full initial projection would become
					 * a conflicting second CREATE for the same logical instance.
					 */
					if (!ensureSynchronizationDecision(server, instance, stream, clock)) {
						stream.pendingDueOccurrences.add(occurrence);
						continue;
					}
					if (!stream.initialProjectionAttempted) {
						compileAndSendPlan(server, instance, stream, clock);
					}
					stream.pendingDueOccurrences.remove(occurrence);
					compileDueOccurrence(
						server,
						instance,
						stream,
						occurrence,
						clock
					);
				}
				captureDueFields(server, clock);
			}
			case PAUSE -> sendControl(
				server,
				instance,
				event.recipients(),
				MessageControlS2CPayload.Action.PAUSE,
				clock,
				false
			);
			case RESUME -> sendControl(
				server,
				instance,
				event.recipients(),
				MessageControlS2CPayload.Action.RESUME,
				clock,
				false
			);
			case COMPLETE -> sendControl(
				server,
				instance,
				event.recipients(),
				MessageControlS2CPayload.Action.FINALIZE,
				clock,
				true
			);
			case FINISH -> sendControl(
				server,
				instance,
				event.recipients(),
				MessageControlS2CPayload.Action.FINISH,
				clock,
				true
			);
			case CANCEL -> {
				INTERRUPTION_REASONS.put(instance.instanceId(), event.reason());
				sendControl(
					server,
					instance,
					event.recipients(),
					MessageControlS2CPayload.Action.CANCEL,
					clock,
					false
				);
			}
			case TARGET_COMPLETE -> {
				// Target completion is already represented by the authoritative plan clock.
			}
		}
	}

	private static void prepareRecipientStream(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient,
		final ProjectionKind kind,
		final Optional<UUID> replacedInstanceId,
		final ClockSample clock
	) {
		DeliverySession delivery = DELIVERIES.get(instance.instanceId());
		DeliveryDirective directive = delivery == null
			? DeliveryDirective.START_AT_SHARED_ANCHOR
			: delivery.takeProjectionDirective(recipient);
		if (!directive.startsActiveDelivery()) {
			return;
		}
		StreamKey key = new StreamKey(instance.instanceId(), recipient);
		RecipientStream previous = STREAMS.get(key);
		/*
		 * CREATE may be emitted once for the frozen cue audience and again for a due-at-zero
		 * audience edge in the same authority commit. Replacing an already-established stream here
		 * sends a second CREATE plan with a higher sequence; the client correctly rejects that plan,
		 * after which every real field delta appears to skip a sequence. Treat CREATE as idempotent
		 * for an active logical instance. Reconnect uses the existing stream and REFRESH/REPLACE keep
		 * their explicit replacement semantics.
		 */
		if (kind == ProjectionKind.CREATE && previous != null && !previous.terminal) {
			return;
		}
		long sequence = previous == null ? 0L : safeIncrement(previous.lastSequence);
		if (kind == ProjectionKind.REPLACE) {
			replacedInstanceId.ifPresent(oldId ->
				STREAMS.remove(new StreamKey(oldId, recipient))
			);
		}
		RecipientStream stream = new RecipientStream(
			key,
			sequence,
			switch (kind) {
				case REFRESH -> PlanDisposition.REFRESH;
				case REPLACE -> PlanDisposition.REPLACE;
				default -> PlanDisposition.CREATE;
			},
			replacedInstanceId,
			instance.elapsedAt(clock),
			instance.cue().policies().timing().synchronization()
				.orElse(SynchronizationMode.SYNCHRONIZED),
			recipientScreenGateMode(server, instance, recipient)
		);
		if (previous != null) {
			stream.compilationFallbacks.inherit(previous.compilationFallbacks);
		}
		if (directive.newVisualAnchor()) {
			stream.visualAnchorNanos = clock.value(instance.clockMode());
		}
		STREAMS.put(key, stream);
		freezeCueStartProjections(server, instance, stream, clock);
		if (ensureSynchronizationDecision(server, instance, stream, clock)) {
			compileAndSendPlan(server, instance, stream, clock);
		}
	}

	private static void freezeCueStartProjections(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		for (CueNode node : instance.cue().nodes()) {
			if (
				!(node instanceof CallbackNode)
					&& !recipientAuthorizedForNode(
						server,
						instance,
						stream.key.recipientId(),
						node.id()
					)
			) {
				continue;
			}
			freezeCueStartConditions(server, instance, stream, node);
			if (
				node instanceof CallbackNode
					&& !(node.header().schedule() instanceof OnCueEvent)
					&& callbackDriver(instance, node).equals(stream.key.recipientId())
					&& node.header().when()
						.filter(value ->
							value.evaluateAt() == ConditionEvaluation.CUE_START
						)
						.isPresent()
			) {
				ConditionSpec condition = node.header().when().orElseThrow();
				boolean included = Boolean.TRUE.equals(
					stream.cueStartConditionDecisions.get(condition)
				);
				for (
					int occurrence = 0;
					occurrence < node.header().repeat().count();
					occurrence++
				) {
					stream.frozenCallbackDecisions.put(
						new NodeOccurrence(node.id(), occurrence),
						included
					);
				}
			}
			if (
				node instanceof CallbackNode
					|| !(node.header().schedule() instanceof AfterNode)
					|| deferredCondition(node)
			) {
				continue;
			}
			for (
				int occurrence = 0;
				occurrence < node.header().repeat().count();
				occurrence++
			) {
				NodeOccurrence key = new NodeOccurrence(node.id(), occurrence);
				try {
					stream.compilingOccurrence = key;
					Projection projection = MessageProjectionCompiler.compileOccurrences(
						compileRequest(server, instance, stream, clock),
						Set.of(key)
					);
					if (projection.plan().isPresent()) {
						stream.frozenCueStartProjections.put(key, projection);
					} else {
						stream.decidedOccurrences.add(key);
						stream.occurrenceDurations.put(key, 0L);
					}
				} catch (RuntimeException error) {
					PixelTzzPro.LOGGER.error(
						"Could not freeze cue-start projection {} for {}",
						key,
						stream.key.recipientId(),
						error
					);
					sendCompilationFallbackOnce(
						server,
						instance,
						stream,
						Set.of(key)
					);
					stream.decidedOccurrences.add(key);
					stream.occurrenceDurations.put(key, 0L);
				} finally {
					stream.compilingOccurrence = null;
				}
			}
		}
	}

	private static void freezeCueStartConditions(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node
	) {
		for (ConditionSpec condition : nodeConditions(node)) {
			if (condition.evaluateAt() != ConditionEvaluation.CUE_START) {
				continue;
			}
			boolean included = stream.cueStartConditionDecisions.computeIfAbsent(
				condition,
				ignored -> conditionMatches(
					server,
					instance,
					stream.key.recipientId(),
					condition
				)
			);
			for (
				int occurrence = 0;
				occurrence < node.header().repeat().count();
				occurrence++
			) {
				stream.conditionDecisions.put(
					new ConditionDecisionKey(
						new NodeOccurrence(node.id(), occurrence),
						condition
					),
					included
				);
			}
		}
	}

	private static void compileAndSendPlan(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		if (stream.initialProjectionAttempted) {
			return;
		}
		stream.initialProjectionAttempted = true;
		Set<NodeOccurrence> requested = initialProjectionOccurrences(
			server,
			instance,
			stream.key.recipientId()
		);
		if (requested.isEmpty()) {
			return;
		}
		Projection projection;
		try {
			projection = MessageProjectionCompiler.compileOccurrences(
				compileRequest(server, instance, stream, clock),
				requested
			);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.error(
				"Could not compile V3B projection {} for {}",
				instance.cue().id(),
				stream.key.recipientId(),
				error
			);
			sendCompilationFallbackOnce(
				server,
				instance,
				stream,
				requested
			);
			return;
		}
		if (projection.plan().isEmpty()) {
			requested.forEach(occurrence -> {
				stream.decidedOccurrences.add(occurrence);
				stream.occurrenceDurations.put(occurrence, 0L);
			});
			return;
		}
		Map<NodeOccurrence, Long> startOverrides = new LinkedHashMap<>();
		for (NodeOccurrence occurrence : requested) {
			occurrenceScheduledAt(instance, stream, occurrence)
				.ifPresent(value -> startOverrides.put(occurrence, value));
		}
		NormalizedProjection normalized = normalizeProjection(
			stream,
			projection,
			startOverrides
		);
		if (normalized.nodes().isEmpty()) {
			return;
		}
		MessagePlaybackPlan source = projection.plan().orElseThrow();
		stream.plan = copyPlanWithNodes(source, normalized.nodes());
		mergeCaptures(stream, normalized.captures());
		recordProjectedNodes(stream, normalized);
		stream.projectedOccurrences.addAll(normalized.occurrences());
		stream.decidedOccurrences.addAll(requested);
		requested.stream()
			.filter(occurrence -> !normalized.occurrences().contains(occurrence))
			.forEach(occurrence -> stream.occurrenceDurations.put(occurrence, 0L));
		stream.lastSequence = stream.plan.sequence();
		sendPlan(server, stream, "INITIAL_COMPILE");
		captureStreamDueFields(server, instance, stream, clock);
		updateOccurrenceDurations(
			server,
			instance,
			stream,
			clock,
			instance.elapsedAt(clock)
		);
	}

	private static void flushPendingDueOccurrences(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		if (
			stream.pendingDueOccurrences.isEmpty()
				|| stream.terminal
				|| !stream.initialProjectionAttempted
		) {
			return;
		}
		for (NodeOccurrence occurrence : List.copyOf(stream.pendingDueOccurrences)) {
			stream.pendingDueOccurrences.remove(occurrence);
			compileDueOccurrence(server, instance, stream, occurrence, clock);
		}
	}

	/**
	 * Builds the recipient's complete, already-authorized final presentation before emitting any new
	 * wire data. The ordinary initial compiler intentionally omits {@link AfterNode}, cue-event and
	 * deferred-condition content; reusing it for static-final delivery therefore produces incomplete
	 * text and undeclared field slots.
	 *
	 * <p>Every not-yet-decided condition is frozen exactly once at this finalization boundary.
	 * CUE_START conditions may only reuse the decision captured when the stream was created. Missing
	 * CUE_START evidence fails closed instead of reading current secret state.</p>
	 */
	private static FinalProjectionResult compileAndSendCompleteFinalProjection(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final Set<String> authorizedNodeIds,
		final ClockSample clock
	) {
		if (stream.terminal) {
			return FinalProjectionResult.FAILED;
		}
		/*
		 * planSent describes this connection, not merely whether a server-side plan exists. Keep
		 * that fact stable while final content is compiled: an established client must receive one
		 * APPEND followed by FINALIZE, while a fresh connection needs one complete PLAN, its frozen
		 * field snapshot, and then FINALIZE.
		 */
		boolean clientInstanceEstablished = stream.planSent;
		MessageFinalProjectionPlanner.Plan finalPlan = completeFinalPlan(
			instance,
			stream.key.recipientId(),
			authorizedNodeIds,
			stream.projectedOccurrences,
			stream.decidedOccurrences
		);
		List<NodeOccurrence> requested = finalPlan.newOccurrences();
		List<ResolvedNode> additions = new ArrayList<>();
		MessagePlaybackPlan firstSource = null;
		for (NodeOccurrence occurrence : requested) {
			if (stream.projectedOccurrences.contains(occurrence)) {
				stream.decidedOccurrences.add(occurrence);
				continue;
			}
			CueNode node = instance.cue().nodes()
				.stream()
				.filter(candidate -> candidate.id().equals(occurrence.nodeId()))
				.findFirst()
				.orElse(null);
			if (node == null || node instanceof CallbackNode) {
				continue;
			}
			freezeFinalBoundaryConditions(
				server,
				instance,
				stream,
				node,
				occurrence
			);
			if (headerConditionRejected(stream, node, occurrence)) {
				skipOccurrence(stream, occurrence);
				continue;
			}

			final Projection projection;
			try {
				stream.dueConditionalNodes.add(node.id());
				stream.compilingOccurrence = occurrence;
				projection = MessageProjectionCompiler.compileOccurrences(
					compileRequest(server, instance, stream, clock),
					Set.of(occurrence)
				);
			} catch (RuntimeException error) {
				PixelTzzPro.LOGGER.error(
					"Could not compile complete V3B final occurrence {} for {}",
					occurrence,
					stream.key.recipientId(),
					error
				);
				return FinalProjectionResult.FAILED;
			} finally {
				stream.compilingOccurrence = null;
				stream.dueConditionalNodes.remove(node.id());
			}
			stream.decidedOccurrences.add(occurrence);
			if (projection.plan().isEmpty()) {
				stream.occurrenceDurations.put(occurrence, 0L);
				continue;
			}
			NormalizedProjection normalized = normalizeProjection(
				stream,
				projection,
				Map.of()
			);
			if (normalized.nodes().isEmpty()) {
				stream.occurrenceDurations.putIfAbsent(occurrence, 0L);
				continue;
			}
			if (firstSource == null) {
				firstSource = projection.plan().orElseThrow();
			}
			additions.addAll(normalized.nodes());
			mergeCaptures(stream, normalized.captures());
			recordProjectedNodes(stream, normalized);
			stream.projectedOccurrences.addAll(normalized.occurrences());
			stream.occurrenceDurations.put(
				occurrence,
				instance.authoritativeNodeDurations()
					.getOrDefault(node.id(), new TimeSpan(0L))
					.nanoseconds()
			);
		}
		stream.initialProjectionAttempted = true;
		if (!additions.isEmpty()) {
			if (stream.plan == null) {
				stream.plan = copyPlanWithNodes(
					Objects.requireNonNull(firstSource, "firstSource"),
					additions
				);
			} else {
				stream.plan = copyPlanWithNodes(
					stream.plan,
					mergeResolvedNodes(stream.plan.nodes(), additions)
				);
			}
		}
		Set<NodeOccurrence> visibleFinalOccurrences = finalPlan.orderedOccurrences()
			.stream()
			.filter(stream.projectedOccurrences::contains)
			.collect(
				java.util.stream.Collectors.toCollection(LinkedHashSet::new)
			);
		List<ResolvedNode> desiredFinalNodes = resolvedNodesForOccurrences(
			stream,
			visibleFinalOccurrences
		);
		Set<Integer> desiredFinalOrdinals = nodeOrdinals(desiredFinalNodes);
		if (clientInstanceEstablished) {
			if (desiredFinalNodes.isEmpty()) {
				return scrubFinalConnection(server, instance, stream, clock)
					? FinalProjectionResult.ZERO_VISIBLE
					: FinalProjectionResult.FAILED;
			}
			boolean containsRevokedOrHistoricalNodes = stream.connectionNodeOrdinals
				.stream()
				.anyMatch(ordinal -> !desiredFinalOrdinals.contains(ordinal));
			if (containsRevokedOrHistoricalNodes) {
				if (!sendFinalProjectionRefresh(
					server,
					instance,
					stream,
					desiredFinalNodes,
					clock
				)) {
					return scrubFinalConnection(server, instance, stream, clock)
						? FinalProjectionResult.ZERO_VISIBLE
						: FinalProjectionResult.FAILED;
				}
			} else {
				List<ResolvedNode> missing = desiredFinalNodes.stream()
					.filter(node -> !stream.connectionNodeOrdinals.contains(node.ordinal()))
					.toList();
				if (
					!missing.isEmpty()
						&& !sendNodeAppend(server, instance, stream, missing)
				) {
					return scrubFinalConnection(server, instance, stream, clock)
						? FinalProjectionResult.ZERO_VISIBLE
						: FinalProjectionResult.FAILED;
				}
			}
			if (!stream.connectionNodeOrdinals.equals(desiredFinalOrdinals)) {
				return scrubFinalConnection(server, instance, stream, clock)
					? FinalProjectionResult.ZERO_VISIBLE
					: FinalProjectionResult.FAILED;
			}
			stream.needsLockedSnapshot = stream.lockedValues.keySet().stream().anyMatch(slot ->
				stream.connectionFieldSlots.contains(slot)
					&& !stream.connectionLockedSlots.contains(slot)
			);
			sendLockedSnapshot(server, stream);
			if (stream.needsLockedSnapshot) {
				return scrubFinalConnection(server, instance, stream, clock)
					? FinalProjectionResult.ZERO_VISIBLE
					: FinalProjectionResult.FAILED;
			}
		}
		if (!clientInstanceEstablished) {
			if (stream.plan == null || visibleFinalOccurrences.isEmpty()) {
				return FinalProjectionResult.ZERO_VISIBLE;
			}
			sendPlan(
				server,
				stream,
				Optional.of(Set.copyOf(visibleFinalOccurrences)),
				"FINAL_STANDALONE"
			);
			if (!stream.planSent) {
				return FinalProjectionResult.FAILED;
			}
		}
		if (!clientInstanceEstablished) {
			stream.needsLockedSnapshot =
				stream.lockedValues.keySet().stream().anyMatch(slot ->
					stream.connectionFieldSlots.contains(slot)
						&& !stream.connectionLockedSlots.contains(slot)
				);
			sendLockedSnapshot(server, stream);
			if (stream.needsLockedSnapshot) {
				return FinalProjectionResult.FAILED;
			}
		}
		return FinalProjectionResult.READY;
	}

	private static MessageFinalProjectionPlanner.Plan completeFinalPlan(
		final MessageInstance instance,
		final UUID recipient,
		final Set<String> authorizedNodeIds,
		final Set<NodeOccurrence> alreadyProjected,
		final Set<NodeOccurrence> alreadyDecided
	) {
		Map<String, Set<UUID>> finalNodeTargets = new LinkedHashMap<>();
		for (CueNode node : instance.cue().nodes()) {
			finalNodeTargets.put(
				node.id(),
				authorizedNodeIds.contains(node.id())
					? Set.of(recipient)
					: Set.of()
			);
		}
		return MessageFinalProjectionPlanner.plan(
			instance.cue(),
			recipient,
			Set.of(recipient),
			Map.copyOf(finalNodeTargets),
			instance.eventOffsets(),
			alreadyProjected,
			alreadyDecided
		);
	}

	private static List<ResolvedNode> resolvedNodesForOccurrences(
		final RecipientStream stream,
		final Set<NodeOccurrence> occurrences
	) {
		if (stream.plan == null || occurrences.isEmpty()) {
			return List.of();
		}
		Set<Integer> ordinals = new LinkedHashSet<>();
		for (NodeOccurrence occurrence : occurrences) {
			for (ResolvedNode node : stream.nodesByOccurrence.getOrDefault(
				occurrence,
				List.of()
			)) {
				if (!ordinals.add(node.ordinal())) {
					throw new IllegalArgumentException(
						"final occurrence projection reuses node ordinal " + node.ordinal()
					);
				}
			}
		}
		return stream.plan.nodes()
			.stream()
			.filter(node -> ordinals.contains(node.ordinal()))
			.toList();
	}

	private static void freezeFinalBoundaryConditions(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final CueNode node,
		final NodeOccurrence occurrence
	) {
		for (ConditionSpec condition : nodeConditions(node)) {
			ConditionDecisionKey key = new ConditionDecisionKey(
				occurrence,
				condition
			);
			MessageFinalProjectionPlanner.freezeConditionDecision(
				stream.conditionDecisions,
				key,
				condition.evaluateAt(),
				Optional.ofNullable(
					stream.cueStartConditionDecisions.get(condition)
				),
				() -> conditionMatches(
					server,
					instance,
					stream.key.recipientId(),
					condition
				)
			);
		}
	}

	private static Optional<Long> occurrenceScheduledAt(
		final MessageInstance instance,
		final RecipientStream stream,
		final NodeOccurrence occurrence
	) {
		CueNode node = instance.cue().nodes()
			.stream()
			.filter(candidate -> candidate.id().equals(occurrence.nodeId()))
			.findFirst()
			.orElse(null);
		if (node == null) {
			return Optional.empty();
		}
		return exactNodeBase(instance, stream, node, new LinkedHashSet<>())
			.map(base ->
				Math.addExact(
					base,
					Math.multiplyExact(
						node.header().repeat().interval().nanoseconds(),
						occurrence.occurrence()
					)
				)
			);
	}

	private static Set<NodeOccurrence> initialProjectionOccurrences(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		if (
			instance.cue().policies().audience().sensitive()
				|| instance.cue().policies().audience().evolution()
					== AudienceEvolution.LIVE_STRICT
		) {
			/*
			 * Strict content is projected only when its occurrence becomes due, after the live
			 * audience has been rechecked on that same server tick.
			 */
			return Set.of();
		}
		Set<NodeOccurrence> result = new LinkedHashSet<>();
		for (CueNode node : instance.cue().nodes()) {
			if (
				node instanceof CallbackNode
					|| !(node.header().schedule() instanceof AtCueTime)
					|| !recipientAuthorizedForNode(
						server,
						instance,
						recipient,
						node.id()
					)
					|| deferredCondition(node)
			) {
				continue;
			}
			for (
				int occurrence = 0;
				occurrence < node.header().repeat().count();
				occurrence++
			) {
				result.add(new NodeOccurrence(node.id(), occurrence));
			}
		}
		return Set.copyOf(result);
	}

	private static boolean deferredCondition(final CueNode node) {
		if (
			node.header().when()
				.filter(value -> value.evaluateAt() != ConditionEvaluation.CUE_START)
				.isPresent()
		) {
			return true;
		}
		return node instanceof TextNode text
			&& text.variants().stream().anyMatch(
				value -> value.when().evaluateAt() != ConditionEvaluation.CUE_START
			);
	}

	private static void compileDueOccurrence(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final NodeOccurrence occurrence,
		final ClockSample clock
	) {
		compileDueOccurrence(
			server,
			instance,
			stream,
			occurrence,
			Optional.empty(),
			clock
		);
	}

	private static void compileDueOccurrence(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final NodeOccurrence occurrence,
		final Optional<Long> exactStartOffset,
		final ClockSample clock
	) {
		if (stream.terminal || stream.connectionProjectionTerminal) {
			return;
		}
		if (
			!recipientAuthorizedForNode(
				server,
				instance,
				stream.key.recipientId(),
				occurrence.nodeId()
			)
		) {
			skipOccurrence(stream, occurrence);
			return;
		}
		if (!stream.decidedOccurrences.add(occurrence)) {
			return;
		}
		stream.dueConditionalNodes.add(occurrence.nodeId());
		final Projection projection;
		try {
			Projection frozen = stream.frozenCueStartProjections.remove(occurrence);
			stream.compilingOccurrence = occurrence;
			projection = frozen == null
				? MessageProjectionCompiler.compileOccurrences(
					compileRequest(server, instance, stream, clock),
					Set.of(occurrence)
				)
				: frozen;
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.error(
				"Could not compile due V3B occurrence {} for {}",
				occurrence,
				stream.key.recipientId(),
				error
			);
			sendCompilationFallbackOnce(
				server,
				instance,
				stream,
				Set.of(occurrence)
			);
			return;
		} finally {
			stream.compilingOccurrence = null;
			stream.dueConditionalNodes.remove(occurrence.nodeId());
		}
		if (projection.plan().isEmpty()) {
			stream.occurrenceDurations.put(occurrence, 0L);
			return;
		}
		NormalizedProjection normalized = normalizeProjection(
			stream,
			projection,
			exactStartOffset
				.map(value -> Map.of(occurrence, value))
				.orElseGet(Map::of)
		);
		if (normalized.nodes().isEmpty()) {
			stream.occurrenceDurations.putIfAbsent(occurrence, 0L);
			return;
		}
		mergeCaptures(stream, normalized.captures());
		recordProjectedNodes(stream, normalized);
		stream.projectedOccurrences.addAll(normalized.occurrences());
		if (stream.plan == null) {
			MessagePlaybackPlan source = projection.plan().orElseThrow();
			stream.plan = copyPlanWithNodes(source, normalized.nodes());
			stream.lastSequence = stream.plan.sequence();
			sendPlan(server, stream, "DUE_ESTABLISH");
		} else {
			sendNodeAppend(server, instance, stream, normalized.nodes());
		}
		captureStreamDueFields(server, instance, stream, clock);
		updateOccurrenceDurations(
			server,
			instance,
			stream,
			clock,
			instance.elapsedAt(clock)
		);
	}

	private static void mergeCaptures(
		final RecipientStream stream,
		final Map<Integer, CaptureBinding> additions
	) {
		additions.forEach((slot, binding) -> {
			CaptureBinding current = stream.captures.get(slot);
			if (
				current == null
					|| binding.captureOffsetNanos() < current.captureOffsetNanos()
			) {
				stream.captures.put(slot, binding);
			}
		});
	}

	private static void recordProjectedNodes(
		final RecipientStream stream,
		final NormalizedProjection projection
	) {
		projection.nodesByOccurrence().forEach(
			(occurrence, nodes) ->
				stream.nodesByOccurrence.put(occurrence, List.copyOf(nodes))
		);
	}

	private static CompileRequest compileRequest(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (definitions == null || environment == null || world == null) {
			throw new IllegalStateException(
				"message instance has no frozen projection context"
			);
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		String locale = player == null ? "zh_cn" : player.clientInformation().language();
		return new CompileRequest(
			instance,
			stream.planSequence,
			stream.disposition,
			stream.replacedInstanceId,
			clock,
			new ResolvedOrigin(
				environment.level().identifier(),
				environment.origin().x(),
				environment.origin().y(),
				environment.origin().z()
			),
			locale,
			stream.key.recipientId(),
			definitions.messageCatalog().textEffects(),
			condition -> {
				NodeOccurrence occurrence = stream.compilingOccurrence;
				Boolean frozen = occurrence == null
					? null
					: stream.conditionDecisions.get(
						new ConditionDecisionKey(occurrence, condition)
					);
				if (
					frozen == null
						&& condition.evaluateAt() == ConditionEvaluation.CUE_START
				) {
					frozen = stream.cueStartConditionDecisions.get(condition);
				}
				return frozen != null
					? frozen
					: MessageMinecraftResolver.conditionMatches(
						server,
						definitions,
						world,
						instance,
						stream.key.recipientId(),
						Optional.of(condition)
					);
			},
			speaker -> resolveSpeaker(
				server,
				definitions,
				world,
				instance,
				stream.key.recipientId(),
				speaker
			),
			stream.dueConditionalNodes,
			stream.visualAnchorNanos == null
				? OptionalLong.empty()
				: OptionalLong.of(stream.visualAnchorNanos)
		);
	}

	private static NormalizedProjection normalizeProjection(
		final RecipientStream stream,
		final Projection projection,
		final Map<NodeOccurrence, Long> startOverrides
	) {
		MessagePlaybackPlan plan = projection.plan().orElseThrow();
		Map<CaptureIdentity, Integer> slotIds = new LinkedHashMap<>(stream.slotIds);
		int nextOrdinal = stream.nextOrdinal;
		int nextSlot = stream.nextSlot;
		List<ResolvedNode> nodes = new ArrayList<>();
		Map<NodeOccurrence, List<ResolvedNode>> nodesByOccurrence = new LinkedHashMap<>();
		Map<Integer, CaptureBinding> captures = new LinkedHashMap<>();
		Set<NodeOccurrence> occurrences = new LinkedHashSet<>();
		for (ResolvedNode node : plan.nodes()) {
			NodeOccurrence occurrence = projection.occurrences().get(node.ordinal());
			if (
				occurrence == null
					|| stream.projectedOccurrences.contains(occurrence)
			) {
				continue;
			}
			long normalizedStart = startOverrides.getOrDefault(
				occurrence,
				node.startOffsetNanos()
			);
			long startShift = normalizedStart - node.startOffsetNanos();
			Map<Integer, Integer> remappedSlots = new LinkedHashMap<>();
			if (node instanceof ResolvedTextNode text) {
				for (ResolvedSegment segment : text.segments()) {
					if (!(segment instanceof FieldSlotSegment field)) {
						continue;
					}
					CaptureBinding binding = projection.captures().get(field.slot());
					if (binding == null) {
						throw new IllegalArgumentException(
							"projected field slot has no server capture binding"
						);
					}
					Integer assigned = slotIds.get(binding.identity());
					if (assigned == null) {
						if (nextSlot >= MessagePlaybackPlan.MAX_FIELD_SLOTS) {
							throw new IllegalArgumentException(
								"recipient projection exceeds its opaque slot limit"
							);
						}
						assigned = nextSlot++;
						slotIds.put(binding.identity(), assigned);
					}
					remappedSlots.put(field.slot(), assigned);
					CaptureBinding normalized = new CaptureBinding(
						assigned,
						binding.identity(),
						binding.field(),
						binding.nodeId(),
						binding.occurrence(),
						Math.addExact(
							binding.captureOffsetNanos(),
							startShift
						)
					);
					CaptureBinding current = captures.get(assigned);
					if (
						current == null
							|| normalized.captureOffsetNanos()
								< current.captureOffsetNanos()
					) {
						captures.put(assigned, normalized);
					}
				}
			}
			ResolvedNode remapped = remapNode(
				node,
				nextOrdinal++,
				normalizedStart,
				remappedSlots
			);
			nodes.add(remapped);
			nodesByOccurrence
				.computeIfAbsent(occurrence, ignored -> new ArrayList<>())
				.add(remapped);
			occurrences.add(occurrence);
		}
		stream.slotIds.clear();
		stream.slotIds.putAll(slotIds);
		stream.nextOrdinal = nextOrdinal;
		stream.nextSlot = nextSlot;
		return new NormalizedProjection(
			nodes,
			captures,
			occurrences,
			nodesByOccurrence
		);
	}

	private static ResolvedNode remapNode(
		final ResolvedNode node,
		final int ordinal,
		final long startOffsetNanos,
		final Map<Integer, Integer> slots
	) {
		if (node instanceof ResolvedSoundNode sound) {
			return new ResolvedSoundNode(
				ordinal,
				startOffsetNanos,
				sound.priority(),
				sound.conflict(),
				sound.sound(),
				sound.category(),
				sound.volume(),
				sound.pitch(),
				sound.attachment()
			);
		}
		ResolvedTextNode text = (ResolvedTextNode)node;
		List<ResolvedSegment> segments = new ArrayList<>(text.segments().size());
		for (ResolvedSegment segment : text.segments()) {
			if (segment instanceof FieldSlotSegment field) {
				Integer slot = slots.get(field.slot());
				if (slot == null) {
					throw new IllegalArgumentException(
						"projected field segment has no normalized slot"
					);
				}
				segments.add(
					new FieldSlotSegment(
						slot,
						field.fallback(),
						field.reservation(),
						field.timing()
					)
				);
			} else {
				segments.add(segment);
			}
		}
		return new ResolvedTextNode(
			ordinal,
			startOffsetNanos,
			text.priority(),
			text.conflict(),
			text.channel(),
			segments,
			text.effect(),
			text.layout(),
			text.duration(),
			text.speaker()
		);
	}

	private static MessagePlaybackPlan copyPlanWithNodes(
		final MessagePlaybackPlan source,
		final List<ResolvedNode> nodes
	) {
		List<ResolvedNode> sorted = nodes.stream()
			.sorted(
				Comparator.comparingLong(ResolvedNode::startOffsetNanos)
					.thenComparingInt(ResolvedNode::ordinal)
			)
			.toList();
		return new MessagePlaybackPlan(
			source.instanceId(),
			source.cueId(),
			source.generation(),
			source.sequence(),
			source.cycle(),
			source.disposition(),
			source.replacedInstanceId(),
			source.clock(),
			source.policies(),
			source.origin(),
			sorted
		);
	}

	private static List<ResolvedNode> mergeResolvedNodes(
		final List<ResolvedNode> existing,
		final List<ResolvedNode> additions
	) {
		Map<Integer, ResolvedNode> merged = new LinkedHashMap<>();
		for (ResolvedNode node : existing) {
			merged.put(node.ordinal(), node);
		}
		for (ResolvedNode node : additions) {
			ResolvedNode previous = merged.putIfAbsent(node.ordinal(), node);
			if (previous != null && !previous.equals(node)) {
				throw new IllegalArgumentException(
					"message node ordinal changed within one logical stream: " + node.ordinal()
				);
			}
		}
		return List.copyOf(merged.values());
	}

	private static boolean sendNodeAppend(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final List<ResolvedNode> nodes
	) {
		if (stream.terminal || stream.connectionProjectionTerminal) {
			return false;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		List<ResolvedNode> merged = mergeResolvedNodes(stream.plan.nodes(), nodes);
		if (player == null) {
			stream.plan = copyPlanWithNodes(stream.plan, merged);
			stream.planSent = false;
			return false;
		}
		if (!stream.planSent) {
			// The first projection may have contained only sounds resolved to silent. Once a later
			// conditional node becomes visible, establish the client instance with the complete
			// resolved plan instead of sending an append to an instance the client never received.
			stream.plan = copyPlanWithNodes(stream.plan, merged);
			sendPlan(server, stream, "APPEND_ESTABLISH");
			return stream.planSent;
		}
		if (!ServerPlayNetworking.canSend(player, MessageNodeAppendS2CPayload.TYPE)) {
			return false;
		}
		List<ResolvedNode> authorizedNodes = authorizedConnectionNodes(
			server,
			instance,
			stream,
			nodes
		);
		if (authorizedNodes.isEmpty()) {
			stream.plan = copyPlanWithNodes(stream.plan, merged);
			return true;
		}
		MessageAssetAuthority authority = assetAuthorityFor(instance);
		if (authority == null) {
			/*
			 * An empty mapping means "no substitutions were required", not "the instance has no
			 * asset authority". Treating a missing generation as an empty mapping would leak the
			 * original sound identifier into an append without completing that generation's gate.
			 */
			return false;
		}
		Map<AssetKey, Optional<Identifier>> effective = authority.effectiveAssets(
			player.getUUID()
		).orElse(null);
		if (effective == null) {
			return false;
		}
		List<ResolvedNode> resolvedNodes = MessageAssetPlaybackResolver.resolveNodes(
			authorizedNodes,
			effective
		);
		if (resolvedNodes.isEmpty()) {
			stream.plan = copyPlanWithNodes(stream.plan, merged);
			return true;
		}
		if (
			!checkpointRuntime(
				server,
				clock(server),
				"recipient node append before network projection"
			)
		) {
			return false;
		}
		long sequence = safeIncrement(stream.lastSequence);
		ServerPlayNetworking.send(
			player,
			new MessageNodeAppendS2CPayload(
				instance.instanceId(),
				instance.context().definitionGeneration(),
				sequence,
				resolvedNodes
			)
		);
		stream.plan = copyPlanWithNodes(stream.plan, merged);
		stream.lastSequence = sequence;
		stream.connectionNodeOrdinals.addAll(nodeOrdinals(resolvedNodes));
		stream.connectionFieldSlots.addAll(declaredFieldSlots(resolvedNodes));
		if (
			stream.lockedValues.keySet().stream().anyMatch(slot ->
				stream.connectionFieldSlots.contains(slot)
					&& !stream.connectionLockedSlots.contains(slot)
			)
		) {
			stream.needsLockedSnapshot = true;
			sendLockedSnapshot(server, stream);
		}
		return true;
	}

	/**
	 * Atomically replaces an established connection's visible projection with the exact currently
	 * authorized final text set. REFRESH is required here: APPEND cannot retract a historical or
	 * newly revoked node that the old physical connection already owns.
	 */
	private static boolean sendFinalProjectionRefresh(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final List<ResolvedNode> desiredNodes,
		final ClockSample clock
	) {
		if (
			desiredNodes.isEmpty()
				|| stream.plan == null
				|| !stream.planSent
				|| stream.terminal
				|| stream.connectionProjectionTerminal
		) {
			return false;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| !ServerPlayNetworking.canSend(player, MessagePlanS2CPayload.TYPE)
		) {
			return false;
		}
		List<ResolvedNode> authorized = authorizedConnectionNodes(
			server,
			instance,
			stream,
			desiredNodes
		);
		if (!nodeOrdinals(authorized).equals(nodeOrdinals(desiredNodes))) {
			return false;
		}
		MessageAssetAuthority authority = assetAuthorityFor(instance);
		if (authority == null) {
			return false;
		}
		Map<AssetKey, Optional<Identifier>> effective = authority.effectiveAssets(
			player.getUUID()
		).orElse(null);
		if (effective == null) {
			return false;
		}
		List<ResolvedNode> resolved = MessageAssetPlaybackResolver.resolveNodes(
			authorized,
			effective
		);
		if (resolved.isEmpty()) {
			return false;
		}
		if (
			!checkpointRuntime(
				server,
				clock,
				"authorized final refresh before connection projection"
			)
		) {
			return false;
		}
		final long sequence;
		final long cycle;
		try {
			sequence = safeIncrement(stream.lastSequence);
			cycle = safeIncrement(
				Math.max(stream.connectionPlanCycle, stream.plan.cycle())
			);
		} catch (ArithmeticException exhausted) {
			return false;
		}
		MessagePlaybackPlan refresh = new MessagePlaybackPlan(
			instance.instanceId(),
			stream.plan.cueId(),
			instance.context().definitionGeneration(),
			sequence,
			cycle,
			PlanDisposition.REFRESH,
			Optional.empty(),
			stream.plan.clock(),
			stream.plan.policies(),
			stream.plan.origin(),
			resolved
		);
		logPlanProjection(player, refresh, "FINAL_REFRESH");
		ServerPlayNetworking.send(player, new MessagePlanS2CPayload(refresh));
		stream.lastSequence = sequence;
		stream.connectionPlanCycle = cycle;
		stream.connectionNodeOrdinals.clear();
		stream.connectionNodeOrdinals.addAll(nodeOrdinals(resolved));
		stream.connectionFieldSlots.clear();
		stream.connectionFieldSlots.addAll(declaredFieldSlots(resolved));
		stream.connectionLockedSlots.clear();
		stream.pendingConnectionLockedSnapshot = stream.lockedValues.entrySet()
			.stream()
			.filter(entry -> stream.connectionFieldSlots.contains(entry.getKey()))
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new FieldValue(entry.getKey(), entry.getValue()))
			.toList();
		stream.needsLockedSnapshot = !stream.pendingConnectionLockedSnapshot.isEmpty();
		sendLockedSnapshot(server, stream);
		return !stream.needsLockedSnapshot;
	}

	/**
	 * Last-resort fail-closed cleanup for a connection that already owns content no longer admitted
	 * by the final projection. CANCEL contains no text or fields, so it is still safe to emit if a
	 * persistence checkpoint is temporarily unavailable; leaving revoked text active is not.
	 */
	private static boolean scrubFinalConnection(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		return closeConnectionProjection(server, instance, stream, clock, true);
	}

	/**
	 * Clears only the current physical connection while keeping the logical recipient stream alive.
	 * This is used by synchronized static-final settlement: authoritative callbacks must still run,
	 * but the client must never retain or receive more visual content from the retired projection.
	 */
	private static boolean scrubConnectionProjection(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		return closeConnectionProjection(server, instance, stream, clock, false);
	}

	private static boolean closeConnectionProjection(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock,
		final boolean markStreamTerminal
	) {
		if (stream.terminal) {
			return true;
		}
		if (stream.connectionProjectionTerminal) {
			if (markStreamTerminal) {
				stream.terminal = true;
			}
			return true;
		}
		if (stream.plan == null || !stream.planSent) {
			stream.connectionProjectionTerminal = true;
			stream.connectionTerminalAction = null;
			if (markStreamTerminal) {
				stream.terminal = true;
			}
			return true;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| !ServerPlayNetworking.canSend(player, MessageControlS2CPayload.TYPE)
		) {
			stream.connectionProjectionTerminal = true;
			stream.connectionTerminalAction = null;
			if (markStreamTerminal) {
				stream.terminal = true;
			}
			return false;
		}
		if (
			!checkpointRuntime(
				server,
				clock,
				"authorization scrub before terminal connection control"
			)
		) {
			PixelTzzPro.LOGGER.warn(
				"V3B authorization scrub for {} could not checkpoint; sending text-free CANCEL fail-closed",
				instance.instanceId()
			);
		}
		long sequence = safeIncrement(stream.lastSequence);
		ServerPlayNetworking.send(
			player,
			new MessageControlS2CPayload(
				instance.instanceId(),
				instance.context().definitionGeneration(),
				sequence,
				effectiveClock(instance, clock),
				MessageControlS2CPayload.Action.CANCEL,
				List.of()
			)
		);
		stream.lastSequence = sequence;
		stream.connectionProjectionTerminal = true;
		stream.connectionTerminalAction = MessageControlS2CPayload.Action.CANCEL;
		if (markStreamTerminal) {
			stream.terminal = true;
		}
		return true;
	}

	private static List<ResolvedNode> authorizedConnectionNodes(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final List<ResolvedNode> candidates
	) {
		Set<Integer> authorizedOrdinals = new LinkedHashSet<>();
		stream.nodesByOccurrence.forEach((occurrence, resolved) -> {
			if (
				recipientAuthorizedForNode(
					server,
					instance,
					stream.key.recipientId(),
					occurrence.nodeId()
				)
			) {
				resolved.forEach(node -> authorizedOrdinals.add(node.ordinal()));
			}
		});
		return candidates.stream()
			.filter(node -> authorizedOrdinals.contains(node.ordinal()))
			.toList();
	}

	private static void sendPlan(
		final MinecraftServer server,
		final RecipientStream stream
	) {
		sendPlan(server, stream, Optional.empty(), "UNSPECIFIED");
	}

	private static void sendPlan(
		final MinecraftServer server,
		final RecipientStream stream,
		final String reason
	) {
		sendPlan(server, stream, Optional.empty(), reason);
	}

	private static void sendPlan(
		final MinecraftServer server,
		final RecipientStream stream,
		final Optional<Set<NodeOccurrence>> standaloneOccurrences,
		final String reason
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| stream.plan == null
				|| stream.terminal
				|| stream.connectionProjectionTerminal
				|| !ServerPlayNetworking.canSend(player, MessagePlanS2CPayload.TYPE)
		) {
			stream.planSent = false;
			return;
		}
		MessageInstance instance = runtimeState.instances().get(stream.key.instanceId());
		MessageAssetAuthority authority = instance == null
			? null
			: assetAuthorityFor(instance);
		if (instance != null && authority != null) {
			if (!cueAssetsReady(authority, player, instance.cue().id())) {
				stream.planSent = false;
				return;
			}
		}
		if (instance != null && authority == null) {
			stream.planSent = false;
			return;
		}
		MessagePlaybackPlan projectedPlan = stream.plan;
		List<FieldValue> reconnectSnapshot = List.of();
		boolean standalone = stream.freshPhysicalConnectionPlan
			|| standaloneOccurrences.isPresent();
		if (standalone) {
			if (instance == null) {
				stream.planSent = false;
				return;
			}
			Optional<MessageReconnectProjection.Projection> reconnect =
				standaloneOccurrences.isPresent()
					? MessageReconnectProjection.project(
						stream.plan,
						stream.nodesByOccurrence,
						authorizedNodeIdsForRecipient(
							server,
							instance,
							stream.key.recipientId()
						),
						standaloneOccurrences.orElseThrow(),
						stream.lockedValues,
						stream.lastSequence
					)
					: MessageReconnectProjection.project(
						stream.plan,
						stream.nodesByOccurrence,
						authorizedNodeIdsForRecipient(
							server,
							instance,
							stream.key.recipientId()
						),
						stream.lockedValues,
						stream.lastSequence
					);
			if (reconnect.isEmpty()) {
				stream.planSent = false;
				stream.connectionNodeOrdinals.clear();
				stream.connectionFieldSlots.clear();
				stream.connectionLockedSlots.clear();
				stream.connectionPlanCycle = -1L;
				stream.pendingConnectionLockedSnapshot = List.of();
				stream.needsLockedSnapshot = false;
				return;
			}
			projectedPlan = reconnect.orElseThrow().plan();
			reconnectSnapshot = reconnect.orElseThrow().lockedSnapshot();
		}
		if (
			!checkpointRuntime(
				server,
				clock(server),
				"recipient playback plan before network projection"
			)
		) {
			stream.planSent = false;
			return;
		}
		if (authority != null) {
			var effective = authority.effectiveAssets(player.getUUID());
			if (effective.isEmpty()) {
				stream.planSent = false;
				return;
			}
			var resolved = MessageAssetPlaybackResolver.resolvePlan(
				projectedPlan,
				effective.orElseThrow()
			);
			if (resolved.isEmpty()) {
				// A sound-only silent plan has no client-visible projection. Keep the stream unsent
				// so a later conditionally appended text node can still establish the instance.
				stream.planSent = false;
				return;
			}
			projectedPlan = resolved.orElseThrow();
		}
		logPlanProjection(player, projectedPlan, reason);
		ServerPlayNetworking.send(player, new MessagePlanS2CPayload(projectedPlan));
		stream.planSent = true;
		stream.freshPhysicalConnectionPlan = false;
		stream.lastSequence = projectedPlan.sequence();
		stream.connectionPlanCycle = projectedPlan.cycle();
		stream.connectionNodeOrdinals.clear();
		stream.connectionNodeOrdinals.addAll(nodeOrdinals(projectedPlan.nodes()));
		stream.connectionFieldSlots.clear();
		stream.connectionFieldSlots.addAll(declaredFieldSlots(projectedPlan.nodes()));
		stream.connectionLockedSlots.clear();
		Set<Integer> declared = stream.connectionFieldSlots;
		Map<Integer, MessagePlaybackPlan.ResolvedComponent> pendingSnapshot =
			new LinkedHashMap<>();
		reconnectSnapshot.stream()
			.filter(value -> declared.contains(value.slot()))
			.forEach(value ->
				pendingSnapshot.put(value.slot(), value.component())
			);
		stream.lockedValues.forEach((slot, value) -> {
			if (declared.contains(slot)) {
				pendingSnapshot.putIfAbsent(slot, value);
			}
		});
		stream.pendingConnectionLockedSnapshot = pendingSnapshot.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new FieldValue(entry.getKey(), entry.getValue()))
			.toList();
		stream.needsLockedSnapshot =
			!stream.pendingConnectionLockedSnapshot.isEmpty();
		sendLockedSnapshot(server, stream);
	}

	private static void logPlanProjection(
		final ServerPlayer player,
		final MessagePlaybackPlan plan,
		final String reason
	) {
		PixelTzzPro.LOGGER.info(
			"V3B plan send reason={} target={} instance={} generation={} sequence={} cycle={} "
				+ "disposition={} clock={}/{} ordinals={} slots={} bodyHash={}",
			reason,
			player.getScoreboardName(),
			plan.instanceId(),
			plan.generation(),
			plan.sequence(),
			plan.cycle(),
			plan.disposition(),
			plan.clock().serverNow(),
			plan.clock().startAt(),
			nodeOrdinals(plan.nodes()),
			declaredFieldSlots(plan.nodes()),
			Integer.toHexString(java.util.Arrays.hashCode(plan.encode()))
		);
	}

	private static void sendLockedSnapshot(
		final MinecraftServer server,
		final RecipientStream stream
	) {
		if (
			!stream.needsLockedSnapshot
				|| !stream.planSent
				|| stream.terminal
				|| stream.connectionProjectionTerminal
		) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| !ServerPlayNetworking.canSend(
					player,
					MessageFieldDeltaS2CPayload.TYPE
				)
		) {
			return;
		}
		Map<Integer, MessagePlaybackPlan.ResolvedComponent> pending =
			new LinkedHashMap<>();
		stream.pendingConnectionLockedSnapshot.forEach(value ->
			pending.put(value.slot(), value.component())
		);
		stream.lockedValues.forEach((slot, value) -> {
			if (stream.connectionFieldSlots.contains(slot)) {
				pending.putIfAbsent(slot, value);
			}
		});
		List<FieldValue> values = pending.entrySet()
			.stream()
			.filter(entry ->
				stream.connectionFieldSlots.contains(entry.getKey())
					&& !stream.connectionLockedSlots.contains(entry.getKey())
			)
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new FieldValue(entry.getKey(), entry.getValue()))
			.toList();
		if (values.isEmpty()) {
			stream.pendingConnectionLockedSnapshot = List.of();
			stream.needsLockedSnapshot = false;
			return;
		}
		long sequence = safeIncrement(stream.lastSequence);
		ServerPlayNetworking.send(
			player,
			new MessageFieldDeltaS2CPayload(
				stream.key.instanceId(),
				stream.plan.generation(),
				sequence,
				values
			)
		);
		stream.lastSequence = sequence;
		values.forEach(value -> stream.connectionLockedSlots.add(value.slot()));
		stream.pendingConnectionLockedSnapshot = stream.pendingConnectionLockedSnapshot
			.stream()
			.filter(value -> !stream.connectionLockedSlots.contains(value.slot()))
			.toList();
		stream.needsLockedSnapshot =
			!stream.pendingConnectionLockedSnapshot.isEmpty()
				|| stream.lockedValues.keySet().stream().anyMatch(slot ->
					stream.connectionFieldSlots.contains(slot)
						&& !stream.connectionLockedSlots.contains(slot)
				);
	}

	private static Set<Integer> declaredFieldSlots(
		final List<ResolvedNode> nodes
	) {
		Set<Integer> slots = new LinkedHashSet<>();
		for (ResolvedNode node : nodes) {
			if (!(node instanceof ResolvedTextNode text)) {
				continue;
			}
			for (ResolvedSegment segment : text.segments()) {
				if (segment instanceof FieldSlotSegment field) {
					slots.add(field.slot());
				}
			}
		}
		return Set.copyOf(slots);
	}

	private static Set<Integer> nodeOrdinals(final List<ResolvedNode> nodes) {
		Set<Integer> ordinals = new LinkedHashSet<>();
		for (ResolvedNode node : nodes) {
			if (!ordinals.add(node.ordinal())) {
				throw new IllegalArgumentException(
					"connection projection contains duplicate node ordinal " + node.ordinal()
				);
			}
		}
		return Set.copyOf(ordinals);
	}

	private static void captureDueFields(
		final MinecraftServer server,
		final ClockSample clock
	) {
		for (RecipientStream stream : List.copyOf(STREAMS.values())) {
			MessageInstance instance = runtimeState.instances().get(stream.key.instanceId());
			if (
				instance != null
					&& stream.plan != null
					&& !stream.terminal
					&& !stream.connectionProjectionTerminal
			) {
				captureStreamDueFields(server, instance, stream, clock);
			}
		}
	}

	private static void captureStreamDueFields(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock
	) {
		if (stream.terminal || stream.connectionProjectionTerminal) {
			return;
		}
		long elapsed;
		try {
			elapsed = instance.elapsedAt(clock);
		} catch (RuntimeException error) {
			return;
		}
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (definitions == null || environment == null || world == null) {
			return;
		}
		List<FieldValue> values = new ArrayList<>();
		while (true) {
			List<CaptureBinding> due = stream.captures.values()
				.stream()
				.filter(binding -> !stream.lockedSlots.contains(binding.slot()))
				.filter(binding ->
					recipientAuthorizedForNode(
						server,
						instance,
						stream.key.recipientId(),
						binding.nodeId()
					)
				)
				.filter(binding -> captureGateReady(instance, stream, binding))
				.filter(binding -> effectiveCaptureOffset(stream, binding) <= elapsed)
				.sorted(Comparator.comparingInt(CaptureBinding::slot))
				.toList();
			if (due.isEmpty()) {
				break;
			}
			for (CaptureBinding binding : due) {
				FieldValue value = new FieldValue(
					binding.slot(),
					MessageMinecraftResolver.resolveField(
						server,
						definitions,
						world,
						instance,
						environment,
						stream.key.recipientId(),
						binding.field()
					)
				);
				values.add(value);
				stream.lockedSlots.add(value.slot());
				stream.lockedValues.put(value.slot(), value.component());
			}
		}
		if (values.isEmpty()) {
			return;
		}
		updateOccurrenceDurations(server, instance, stream, clock, elapsed);
		if (
			!checkpointRuntime(
				server,
				clock,
				"locked dynamic fields before field-delta projection"
			)
		) {
			return;
		}
		List<FieldValue> wireValues = values.stream()
			.filter(value ->
				stream.planSent
					&& stream.connectionFieldSlots.contains(value.slot())
					&& !stream.connectionLockedSlots.contains(value.slot())
			)
			.toList();
		if (wireValues.isEmpty()) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| !ServerPlayNetworking.canSend(player, MessageFieldDeltaS2CPayload.TYPE)
		) {
			return;
		}
		long sequence = safeIncrement(stream.lastSequence);
		ServerPlayNetworking.send(
			player,
			new MessageFieldDeltaS2CPayload(
				instance.instanceId(),
				instance.context().definitionGeneration(),
				sequence,
				wireValues
			)
		);
		stream.lastSequence = sequence;
		wireValues.forEach(value -> stream.connectionLockedSlots.add(value.slot()));
	}

	private static long effectiveCaptureOffset(
		final RecipientStream stream,
		final CaptureBinding binding
	) {
		List<ResolvedNode> occurrenceNodes = stream.nodesByOccurrence.get(
			new NodeOccurrence(binding.nodeId(), binding.occurrence())
		);
		if (occurrenceNodes == null) {
			return binding.captureOffsetNanos();
		}
		for (ResolvedNode node : occurrenceNodes) {
			if (
				node instanceof ResolvedTextNode text
					&& text.segments().stream().anyMatch(segment ->
						segment instanceof FieldSlotSegment field
							&& field.slot() == binding.slot()
					)
			) {
				try {
					return MessageDynamicCaptureScheduler.captureOffset(
						text,
						binding.slot(),
						binding.field().capture(),
						stream.lockedValues
					).orElse(binding.captureOffsetNanos());
				} catch (RuntimeException invalidProjection) {
					return binding.captureOffsetNanos();
				}
			}
		}
		return binding.captureOffsetNanos();
	}

	private static boolean captureGateReady(
		final MessageInstance instance,
		final RecipientStream stream,
		final CaptureBinding binding
	) {
		CueNode node = cueNode(instance, binding.nodeId());
		if (!(node instanceof TextNode text)) {
			return true;
		}
		ScreenStartMode mode = screenStartMode(instance, text);
		if (
			mode == ScreenStartMode.IMMEDIATE
				|| mode == ScreenStartMode.FINAL_CHAT_ONLY
		) {
			return true;
		}
		return stream.occurrenceActualStarts.containsKey(
			new NodeOccurrence(binding.nodeId(), binding.occurrence())
		);
	}

	private static void updateOccurrenceDurations(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock,
		final long elapsed
	) {
		for (
			Map.Entry<NodeOccurrence, List<ResolvedNode>> entry :
				stream.nodesByOccurrence.entrySet()
		) {
			if (stream.occurrenceDurations.containsKey(entry.getKey())) {
				continue;
			}
			CueNode source = cueNode(instance, entry.getKey().nodeId());
			if (source == null) {
				continue;
			}
			Optional<Long> scheduled = occurrenceScheduledAt(
				instance,
				stream,
				entry.getKey()
			);
			if (scheduled.isEmpty() || elapsed < scheduled.orElseThrow()) {
				continue;
			}
			Optional<Long> actual = resolveOccurrenceStart(
				server,
				instance,
				stream,
				source,
				entry.getKey(),
				scheduled.orElseThrow(),
				elapsed,
				clock
			);
			if (stream.terminal) {
				return;
			}
			if (actual.isEmpty()) {
				continue;
			}
			if (source instanceof io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode) {
				stream.occurrenceDurations.put(entry.getKey(), 0L);
				continue;
			}
			long visualDuration = 0L;
			boolean fieldsResolved = true;
			for (ResolvedNode node : entry.getValue()) {
				Optional<Long> nodeDuration = exactNodeDuration(
					node,
					stream.lockedValues
				);
				if (nodeDuration.isEmpty()) {
					fieldsResolved = false;
					break;
				}
				visualDuration = Math.max(
					visualDuration,
					nodeDuration.orElseThrow()
				);
			}
			TextNode text = (TextNode)source;
			advanceOccurrencePlayback(
				server,
				instance,
				stream,
				text,
				entry.getKey(),
				scheduled.orElseThrow(),
				actual.orElseThrow(),
				elapsed,
				fieldsResolved
					? OptionalLong.of(visualDuration)
					: OptionalLong.empty(),
				clock
			);
			if (stream.terminal) {
				return;
			}
		}
	}

	private static CueNode cueNode(
		final MessageInstance instance,
		final String nodeId
	) {
		return instance.cue().nodes()
			.stream()
			.filter(candidate -> candidate.id().equals(nodeId))
			.findFirst()
			.orElse(null);
	}

	private static Optional<Long> exactNodeDuration(
		final ResolvedNode node,
		final Map<Integer, MessagePlaybackPlan.ResolvedComponent> fields
	) {
		if (node instanceof ResolvedSoundNode) {
			return Optional.of(0L);
		}
		ResolvedTextNode text = (ResolvedTextNode)node;
		MutableComponent combined = Component.empty();
		List<SegmentSpan> spans = new ArrayList<>(text.segments().size());
		for (ResolvedSegment segment : text.segments()) {
			Component value;
			if (
				segment
					instanceof io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment fixed
			) {
				value = parseResolvedComponent(fixed.component());
			} else {
				FieldSlotSegment field = (FieldSlotSegment)segment;
				MessagePlaybackPlan.ResolvedComponent resolved = fields.get(field.slot());
				if (resolved == null) {
					return Optional.empty();
				}
				value = parseResolvedComponent(resolved);
			}
			combined.append(value.copy());
			spans.add(
				new SegmentSpan(
					StyledTypewriter.flatten(value.copy()).graphemes().size(),
					segment.timing()
				)
			);
		}
		return Optional.of(
			MessageFrameTimeline.compile(
				combined,
				text.effect(),
				text.duration(),
				spans
			).completeAtNanos()
		);
	}

	private static Component parseResolvedComponent(
		final MessagePlaybackPlan.ResolvedComponent component
	) {
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(component.canonicalJson()))
			.getOrThrow();
	}

	private static void sendControl(
		final MinecraftServer server,
		final MessageInstance instance,
		final Set<UUID> recipients,
		final MessageControlS2CPayload.Action action,
		final ClockSample clock,
		final boolean resolveFinalValues
	) {
		sendControl(
			server,
			instance,
			recipients,
			action,
			clock,
			resolveFinalValues,
			true
		);
	}

	private static void sendControl(
		final MinecraftServer server,
		final MessageInstance instance,
		final Set<UUID> recipients,
		final MessageControlS2CPayload.Action action,
		final ClockSample clock,
		final boolean resolveFinalValues,
		final boolean markStreamTerminal
	) {
		Map<UUID, List<FieldValue>> valuesByRecipient = new LinkedHashMap<>();
		Set<UUID> terminalWithoutWire = new LinkedHashSet<>();
		for (UUID recipient : recipients) {
			RecipientStream stream = STREAMS.get(new StreamKey(instance.instanceId(), recipient));
			if (stream == null || stream.terminal) {
				continue;
			}
			if (stream.connectionProjectionTerminal) {
				if (
					action == MessageControlS2CPayload.Action.CANCEL
						|| (
							action.finalizes()
								&& markStreamTerminal
						)
				) {
					terminalWithoutWire.add(recipient);
				}
				continue;
			}
			if (
				action.finalizes()
					&& connectionContainsUnauthorizedNodes(server, instance, stream)
			) {
				scrubFinalConnection(server, instance, stream, clock);
				continue;
			}
			if (stream.plan == null || !stream.planSent) {
				if (
					action.finalizes()
						|| action == MessageControlS2CPayload.Action.CANCEL
				) {
					/*
					 * A strict cue may be cancelled before its first authorized occurrence has
					 * produced a client plan. No packet is needed, but this stream must never
					 * become eligible for a later projection.
					 */
					if (
						action == MessageControlS2CPayload.Action.CANCEL
							|| markStreamTerminal
					) {
						terminalWithoutWire.add(recipient);
					}
				}
				continue;
			}
			List<FieldValue> finalValues = resolveFinalValues
				? unresolvedFinalValues(server, instance, stream)
				: List.of();
			if (
				action.finalizes()
					&& !finalValuesCoverConnection(stream, finalValues)
			) {
				scrubFinalConnection(server, instance, stream, clock);
				continue;
			}
			valuesByRecipient.put(recipient, finalValues);
		}
		boolean checkpointRequired =
			!valuesByRecipient.isEmpty() || !terminalWithoutWire.isEmpty();
		boolean checkpointed = !checkpointRequired
			|| checkpointRuntime(
				server,
				clock,
				"terminal recipient fields before control projection"
			);
		if (!checkpointed) {
			if (action == MessageControlS2CPayload.Action.CANCEL) {
				PixelTzzPro.LOGGER.warn(
					"V3B CANCEL for {} could not checkpoint; continuing with text-free connection cleanup",
					instance.instanceId()
				);
			} else if (action.finalizes()) {
				/*
				 * The authoritative instance may already be terminal. A failed FINALIZE
				 * checkpoint must therefore retract any established client projection instead
				 * of returning and leaving it orphaned forever.
				 */
				for (UUID recipient : valuesByRecipient.keySet()) {
					RecipientStream stream = STREAMS.get(
						new StreamKey(instance.instanceId(), recipient)
					);
					if (stream != null) {
						closeConnectionProjection(
							server,
							instance,
							stream,
							clock,
							markStreamTerminal
						);
					}
				}
				for (UUID recipient : terminalWithoutWire) {
					RecipientStream stream = STREAMS.get(
						new StreamKey(instance.instanceId(), recipient)
					);
					if (stream != null) {
						stream.connectionProjectionTerminal = true;
						stream.connectionTerminalAction = null;
						if (markStreamTerminal) {
							stream.terminal = true;
						}
					}
				}
				return;
			} else {
				return;
			}
		}
		for (UUID recipient : terminalWithoutWire) {
			RecipientStream stream = STREAMS.get(
				new StreamKey(instance.instanceId(), recipient)
			);
			if (stream != null) {
				stream.connectionProjectionTerminal = true;
				stream.connectionTerminalAction = null;
				stream.terminal = true;
			}
		}
		for (Map.Entry<UUID, List<FieldValue>> entry : valuesByRecipient.entrySet()) {
			UUID recipient = entry.getKey();
			RecipientStream stream = STREAMS.get(
				new StreamKey(instance.instanceId(), recipient)
			);
			if (stream == null) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(recipient);
			if (
				player == null
					|| !ServerPlayNetworking.canSend(player, MessageControlS2CPayload.TYPE)
			) {
				stream.connectionProjectionTerminal = true;
				stream.connectionTerminalAction = null;
				if (
					action == MessageControlS2CPayload.Action.CANCEL
						|| (
							action.finalizes()
								&& markStreamTerminal
						)
				) {
					stream.terminal = true;
				}
				continue;
			}
			long sequence = safeIncrement(stream.lastSequence);
			ServerPlayNetworking.send(
				player,
				new MessageControlS2CPayload(
					instance.instanceId(),
					instance.context().definitionGeneration(),
					sequence,
					effectiveClock(instance, clock),
					action,
					entry.getValue()
				)
			);
			stream.lastSequence = sequence;
			if (action.finalizes()) {
				entry.getValue().forEach(value -> {
					stream.lockedSlots.add(value.slot());
					stream.lockedValues.put(value.slot(), value.component());
					stream.connectionLockedSlots.add(value.slot());
				});
				stream.connectionProjectionTerminal = true;
				stream.connectionTerminalAction = action;
				if (markStreamTerminal) {
					stream.terminal = true;
				}
			} else if (action == MessageControlS2CPayload.Action.CANCEL) {
				stream.connectionProjectionTerminal = true;
				stream.connectionTerminalAction = MessageControlS2CPayload.Action.CANCEL;
				stream.terminal = true;
			}
		}
	}

	/**
	 * Emits a terminal FINALIZE only after the current connection is known to own the instance and
	 * every precondition for the packet has succeeded. This deliberately avoids the ordinary
	 * multi-recipient control path, which also represents server-side lifecycle transitions where a
	 * temporarily offline recipient may still be retired logically.
	 */
	private static boolean sendFinalControlIfPossible(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final ClockSample clock,
		final boolean markStreamTerminal
	) {
		if (stream.terminal || stream.plan == null || !stream.planSent) {
			return false;
		}
		if (stream.connectionProjectionTerminal) {
			boolean finalized = stream.connectionTerminalAction != null
				&& stream.connectionTerminalAction.finalizes();
			if (finalized && markStreamTerminal) {
				stream.terminal = true;
			}
			return finalized;
		}
		if (connectionContainsUnauthorizedNodes(server, instance, stream)) {
			return closeConnectionProjection(
				server,
				instance,
				stream,
				clock,
				markStreamTerminal
			);
		}
		ServerPlayer player = server.getPlayerList().getPlayer(stream.key.recipientId());
		if (
			player == null
				|| !ServerPlayNetworking.canSend(player, MessageControlS2CPayload.TYPE)
		) {
			return false;
		}
		List<FieldValue> finalValues = unresolvedFinalValues(
			server,
			instance,
			stream
		);
		if (!finalValuesCoverConnection(stream, finalValues)) {
			return scrubFinalConnection(server, instance, stream, clock);
		}
		if (
			!checkpointRuntime(
				server,
				clock,
				"terminal recipient fields before reliable final projection"
			)
		) {
			return false;
		}
		long sequence = safeIncrement(stream.lastSequence);
		ServerPlayNetworking.send(
			player,
			new MessageControlS2CPayload(
				instance.instanceId(),
				instance.context().definitionGeneration(),
				sequence,
				effectiveClock(instance, clock),
				MessageControlS2CPayload.Action.FINALIZE,
				finalValues
			)
		);
		finalValues.forEach(value -> {
			stream.lockedSlots.add(value.slot());
			stream.lockedValues.put(value.slot(), value.component());
			stream.connectionLockedSlots.add(value.slot());
		});
		stream.lastSequence = sequence;
		stream.connectionProjectionTerminal = true;
		stream.connectionTerminalAction = MessageControlS2CPayload.Action.FINALIZE;
		if (markStreamTerminal) {
			stream.terminal = true;
		}
		return true;
	}

	private static List<FieldValue> unresolvedFinalValues(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
		InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
		WorldStateV3 world = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (definitions == null || environment == null || world == null) {
			return List.of();
		}
		List<FieldValue> values = new ArrayList<>();
		for (Integer slot : stream.connectionFieldSlots.stream().sorted().toList()) {
			if (stream.connectionLockedSlots.contains(slot)) {
				continue;
			}
			CaptureBinding binding = stream.captures.get(slot);
			if (
				binding == null
					|| !recipientAuthorizedForNode(
						server,
						instance,
						stream.key.recipientId(),
						binding.nodeId()
					)
			) {
				continue;
			}
			MessagePlaybackPlan.ResolvedComponent locked =
				stream.lockedValues.get(slot);
			values.add(
				new FieldValue(
					slot,
					locked == null
						? MessageMinecraftResolver.resolveField(
							server,
							definitions,
							world,
							instance,
							environment,
							stream.key.recipientId(),
							binding.field()
						)
						: locked
				)
			);
		}
		return List.copyOf(values);
	}

	private static boolean finalValuesCoverConnection(
		final RecipientStream stream,
		final List<FieldValue> values
	) {
		Set<Integer> remaining = new LinkedHashSet<>(stream.connectionFieldSlots);
		remaining.removeAll(stream.connectionLockedSlots);
		Set<Integer> supplied = new LinkedHashSet<>();
		for (FieldValue value : values) {
			if (!supplied.add(value.slot())) {
				return false;
			}
		}
		return supplied.equals(remaining);
	}

	private static boolean connectionContainsUnauthorizedNodes(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream
	) {
		if (stream.connectionNodeOrdinals.isEmpty()) {
			return false;
		}
		Set<Integer> accounted = new LinkedHashSet<>();
		for (
			Map.Entry<NodeOccurrence, List<ResolvedNode>> entry :
				stream.nodesByOccurrence.entrySet()
		) {
			for (ResolvedNode node : entry.getValue()) {
				if (!stream.connectionNodeOrdinals.contains(node.ordinal())) {
					continue;
				}
				accounted.add(node.ordinal());
				if (
					!recipientAuthorizedForNode(
						server,
						instance,
						stream.key.recipientId(),
						entry.getKey().nodeId()
					)
				) {
					return true;
				}
			}
		}
		return !accounted.equals(stream.connectionNodeOrdinals);
	}

	private static long effectiveClock(
		final MessageInstance instance,
		final ClockSample clock
	) {
		return instance.clockMode() == ClockMode.GAME_TIME
			? clock.gameTick()
			: clock.presentationNanos();
	}

	private static void executePreparedCallback(
		final MinecraftServer server,
		final PreparedCallback prepared
	) {
		MessageInstance instance = runtimeState.instances().get(prepared.key().instanceId());
		InvocationEnvironment environment = ENVIRONMENTS.get(prepared.key().instanceId());
		if (instance == null || environment == null) {
			recordCallbackFailure(
				server,
				prepared,
				"prepared callback lost its frozen invocation context"
			);
			return;
		}
		Optional<UUID> sourceId = switch (prepared.execution()) {
			case AS_SERVER -> Optional.empty();
			case AS_INVOKER -> instance.context().invokerId();
			case AS_TARGET -> prepared.key().targetId().or(() ->
				single(instance.cueTargets())
			);
		};
		if (prepared.execution() != CallbackExecution.AS_SERVER && sourceId.isEmpty()) {
			recordCallbackFailure(
				server,
				prepared,
				"callback execution requires a player source that is unavailable"
			);
			return;
		}
		Optional<ServerPlayer> sourcePlayer = sourceId
			.map(server.getPlayerList()::getPlayer);
		if (sourceId.isPresent() && sourcePlayer.isEmpty()) {
			recordCallbackFailure(
				server,
				prepared,
				"callback player source is offline"
			);
			return;
		}
		Optional<FlowCallbackRunner.ExecutionPosition> position = callbackPosition(
			server,
			prepared,
			instance,
			environment,
			sourceId
		);
		if (position.isEmpty()) {
			recordCallbackFailure(
				server,
				prepared,
				"callback target location is unavailable"
			);
			return;
		}
		FlowCallbackRunner.Invocation invocation = FlowCallbackRunner.invokePrepared(
			server,
			prepared.functionId(),
			callbackArguments(server, instance, prepared),
			"message instance " + instance.instanceId(),
			sourceId,
			sourcePlayer,
			position
		);
		recordCallbackOutcome(
			server,
			prepared,
			invocation.success(),
			invocation.success() ? Optional.empty() : Optional.of(invocation.error())
		);
	}

	private static Optional<FlowCallbackRunner.ExecutionPosition> callbackPosition(
		final MinecraftServer server,
		final PreparedCallback prepared,
		final MessageInstance instance,
		final InvocationEnvironment environment,
		final Optional<UUID> sourceId
	) {
		if (prepared.location() == CallbackLocation.ORIGIN) {
			ServerLevel level = server.getLevel(environment.level());
			return level == null
				? Optional.empty()
				: Optional.of(
					new FlowCallbackRunner.ExecutionPosition(level, environment.origin())
				);
		}
		Optional<UUID> target = prepared.key().targetId()
			.or(() -> sourceId)
			.or(() -> single(instance.cueTargets()));
		ServerPlayer player = target.map(server.getPlayerList()::getPlayer).orElse(null);
		return player == null
			? Optional.empty()
			: Optional.of(
				new FlowCallbackRunner.ExecutionPosition(
					player.level(),
					player.position()
				)
			);
	}

	private static CompoundTag callbackArguments(
		final MinecraftServer server,
		final MessageInstance instance,
		final PreparedCallback prepared
	) {
		CompoundTag arguments = new CompoundTag();
		arguments.putString("message_id", instance.cue().id().toString());
		arguments.putString("cue_id", instance.cue().id().toString());
		arguments.putString("instance_id", instance.instanceId().toString());
		arguments.putLong("cycle", instance.cycle());
		arguments.putString("node_id", prepared.key().nodeId());
		arguments.putInt("occurrence", prepared.key().occurrence());
		arguments.putString(
			"trigger",
			prepared.key().trigger().name().toLowerCase(java.util.Locale.ROOT)
		);
		arguments.putString(
			"target_uuid",
			prepared.key().targetId().map(UUID::toString).orElse("")
		);
		arguments.putString(
			"invoker_uuid",
			instance.context().invokerId().map(UUID::toString).orElse("")
		);
		arguments.putString(
			"game_id",
			instance.context().gameId().map(Identifier::toString).orElse("")
		);
		arguments.putString(
			"phase_id",
			instance.context().phaseId().map(Identifier::toString).orElse("")
		);
		arguments.putString(
			"task_id",
			instance.context().taskId().map(Identifier::toString).orElse("")
		);
		arguments.putBoolean(
			"context_bypassed",
			instance.context().bypassContext()
		);
		arguments.putString(
			"interrupt_reason",
			INTERRUPTION_REASONS.getOrDefault(instance.instanceId(), "")
		);
		CompoundTag canonical = new CompoundTag();
		instance.canonicalParameters().forEach(canonical::putString);
		arguments.put("parameters", canonical);
		return arguments;
	}

	private static void recordCallbackFailure(
		final MinecraftServer server,
		final PreparedCallback prepared,
		final String diagnostic
	) {
		recordCallbackOutcome(
			server,
			prepared,
			false,
			Optional.of(diagnostic)
		);
	}

	private static void recordCallbackOutcome(
		final MinecraftServer server,
		final PreparedCallback prepared,
		final boolean succeeded,
		final Optional<String> diagnostic
	) {
		CallbackOutcomeResult outcome = MessageInstanceAuthority.recordCallbackOutcome(
			runtimeState,
			prepared,
			succeeded,
			diagnostic,
			clock(server).gameTick()
		);
		if (!outcome.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B callback outcome could not be committed for {}: {}",
				prepared.key(),
				outcome.message()
			);
			return;
		}
		runtimeState = outcome.nextState();
		checkpointRuntime(
			server,
			clock(server),
			"callback outcome after callback execution"
		);
	}

	private static boolean persistTerminalHistory(
		final MinecraftServer server,
		final ClockSample clock
	) {
		if (retiringTerminalInstances) {
			return true;
		}
		List<MessageInstance> terminal = runtimeState.instances()
			.values()
			.stream()
			.filter(instance -> instance.status().terminal())
			.filter(instance -> !TERMINAL_HISTORY_COMMITTED.contains(instance.instanceId()))
			.toList();
		if (terminal.isEmpty()) {
			return true;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV4 root = wrapper.currentV4().orElse(null);
		WorldStateV3 world = wrapper.currentV3().orElse(null);
		UUID gameInstanceId = world == null
			? null
			: world.activeGameInstanceId().orElse(null);
		if (root == null || world == null || gameInstanceId == null) {
			PixelTzzPro.LOGGER.error(
				"V3B terminal history could not resolve the active game scope"
			);
			return false;
		}

		List<MessageHistoryRecord> records = new ArrayList<>();
		Set<UUID> resolvedInstances = new LinkedHashSet<>();
		for (MessageInstance instance : terminal) {
			if (
				instance.status()
					== MessageInstanceAuthority.InstanceStatus.CANCELLED
				|| instance.cue().history().isEmpty()
			) {
				resolvedInstances.add(instance.instanceId());
				continue;
			}
			DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
			InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
			if (definitions == null || environment == null) {
				PixelTzzPro.LOGGER.error(
					"V3B history skipped for {} because its frozen integration context "
						+ "is unavailable",
					instance.instanceId()
				);
				resolvedInstances.add(instance.instanceId());
				continue;
			}
			var history = instance.cue().history().orElseThrow();
			AudienceResolution audience = MessageMinecraftResolver.resolveAudience(
				server,
				definitions,
				world,
				instance.cue(),
				instance.context(),
				history.audience()
			);
			if (!audience.resolved()) {
				PixelTzzPro.LOGGER.error(
					"V3B history audience failed for {}: {}",
					instance.instanceId(),
					audience.message()
				);
				resolvedInstances.add(instance.instanceId());
				continue;
			}
			Optional<Identifier> currentTask = world.timeline()
				.flatMap(WorldStateV3.TimelineInstance::currentTask)
				.map(WorldStateV3.TaskInstance::taskId);
			long gameElapsedTicks = world.timeline()
				.map(WorldStateV3.TimelineInstance::gameElapsedTicks)
				.orElse(clock.gameTick());
			long cueElapsedNanos = instance.elapsedAt(clock);
			long cueStartTicks = Math.max(
				0L,
				gameElapsedTicks - cueElapsedNanos / TimeSpan.NANOS_PER_TICK
			);
			TimestampCandidates timestamps = new TimestampCandidates(
				TimestampValue.ticks(gameElapsedTicks),
				TimestampValue.nanos(clock.presentationNanos()),
				TimestampValue.ticks(cueStartTicks),
				TimestampValue.ticks(gameElapsedTicks)
			);
			for (UUID viewer : audience.cueTargets().stream().sorted().toList()) {
				if (!recipientAuthorizedForHistory(server, instance, viewer)) {
					continue;
				}
				ServerPlayer online = server.getPlayerList().getPlayer(viewer);
				String locale = online == null
					? "zh_cn"
					: online.clientInformation().language();
				Map<String, String> fields = frozenHistoryFields(
					server,
					definitions,
					world,
					instance,
					environment,
					viewer
				);
				var materialized = MessageHistoryMaterializer.materialize(
					new MessageHistoryMaterializer.Request(
						instance.cue(),
						history,
						gameInstanceId,
						instance.instanceId(),
						viewer,
						locale,
						fields,
						instance.canonicalParameters(),
						currentTask,
						timestamps
					)
				);
				if (!materialized.successful()) {
					PixelTzzPro.LOGGER.error(
						"V3B history materialization failed for {} / {}: {}",
						instance.instanceId(),
						viewer,
						materialized.diagnostics().stream()
							.map(MessageHistoryMaterializer.Diagnostic::message)
							.reduce((left, right) -> left + "; " + right)
							.orElse("unknown history error")
					);
					continue;
				}
				records.add(materialized.record().orElseThrow());
			}
			resolvedInstances.add(instance.instanceId());
		}

		if (records.isEmpty()) {
			TERMINAL_HISTORY_COMMITTED.addAll(resolvedInstances);
			return true;
		}
		MessageHistoryAuthority.BatchTransaction batch =
			MessageHistoryAuthority.appendAll(root, records);
		if (!batch.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B terminal history batch was rejected: {}",
				batch.message()
			);
			return false;
		}
		if (batch.appendedRecords() > 0 || batch.evictedRecords() > 0) {
			var committed = wrapper.commitV4(
				root.stateRevision(),
				ignored -> batch.state()
			);
			if (!committed.committed()) {
				PixelTzzPro.LOGGER.error(
					"V3B terminal history commit was rejected: {}",
					committed.reason()
				);
				return false;
			}
			PixelTzzServerRuntime.refreshPlayerTerminalsAfterMessageHistory(server);
		}
		TERMINAL_HISTORY_COMMITTED.addAll(resolvedInstances);
		return true;
	}

	private static Map<String, String> frozenHistoryFields(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 world,
		final MessageInstance instance,
		final InvocationEnvironment environment,
		final UUID viewer
	) {
		Map<String, String> values = new LinkedHashMap<>();
		RecipientStream stream = STREAMS.get(
			new StreamKey(instance.instanceId(), viewer)
		);
		if (stream != null) {
			for (CaptureBinding binding : stream.captures.values()) {
				if (
					binding.identity().scope()
						!= io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldScope.CUE
				) {
					continue;
				}
				MessagePlaybackPlan.ResolvedComponent locked =
					stream.lockedValues.get(binding.slot());
				if (locked != null) {
					values.putIfAbsent(
						binding.identity().fieldId(),
						locked.canonicalJson()
					);
				}
			}
		}
		instance.cue().fields().forEach((fieldId, field) ->
			values.computeIfAbsent(
				fieldId,
				ignored -> MessageMinecraftResolver.resolveField(
					server,
					definitions,
					world,
					instance,
					environment,
					viewer,
					field
				).canonicalJson()
			)
		);
		return Map.copyOf(values);
	}

	private static void retireCommittedTerminalInstances() {
		if (retiringTerminalInstances) {
			return;
		}
		Set<UUID> terminalIds = runtimeState.instances()
			.values()
			.stream()
			.filter(instance -> instance.status().terminal())
			.map(MessageInstance::instanceId)
			.filter(TERMINAL_HISTORY_COMMITTED::contains)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		if (terminalIds.isEmpty()) {
			return;
		}
		retiringTerminalInstances = true;
		try {
			var retired = MessageInstanceAuthority.retireTerminalInstances(
				runtimeState,
				terminalIds
			);
			if (!retired.accepted()) {
				PixelTzzPro.LOGGER.error(
					"V3B terminal instances could not be retired: {}",
					retired.message()
				);
				return;
			}
			runtimeState = retired.nextState();
			for (UUID instanceId : retired.retiredInstanceIds()) {
				ENVIRONMENTS.remove(instanceId);
				DEFINITIONS.remove(instanceId);
				PARAMETER_USES.remove(instanceId);
				DELIVERIES.remove(instanceId);
				SYNCHRONIZATIONS.remove(instanceId);
				QUEUED_DELIVERY_IDS.remove(instanceId);
				QUEUED_START_SETTLEMENTS.remove(instanceId);
				DELIVERY_BLOCKED_CALLBACKS.remove(instanceId);
				INTERRUPTION_REASONS.remove(instanceId);
				STREAMS.keySet().removeIf(key -> key.instanceId().equals(instanceId));
				TERMINAL_HISTORY_COMMITTED.remove(instanceId);
			}
			pruneAssetAuthorities();
		} finally {
			retiringTerminalInstances = false;
		}
	}

	/**
	 * Converts the live authority plus private recipient streams into the narrow world-state
	 * checkpoint. This method never increments the global lifecycle revision, so routine message
	 * progress cannot invalidate host confirmation tokens or player-terminal sessions.
	 */
	private static boolean checkpointRuntime(
		final MinecraftServer server,
		final ClockSample clock,
		final String reason
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV4 world = wrapper.currentV4().orElse(null);
		UUID gameInstanceId = world == null
			? null
			: world.core().activeGameInstanceId().orElse(null);
		if (world == null || gameInstanceId == null) {
			PixelTzzPro.LOGGER.error(
				"V3B message checkpoint skipped without an active v4 game scope: {}",
				reason
			);
			return false;
		}

		Map<UUID, PersistedMessageRuntime.Origin> origins = new LinkedHashMap<>();
		Map<UUID, FrozenDocument> dependencySnapshots = new LinkedHashMap<>();
		Map<DefinitionSnapshot, Map<Identifier, FrozenDocument>> snapshotDependencies =
			new IdentityHashMap<>();
		Map<UUID, List<PersistedMessageRuntime.RecipientState>> recipients =
			new LinkedHashMap<>();
		for (MessageInstance instance : runtimeState.instances().values()) {
			if (
				instance.status().terminal()
					|| instance.cue().policies().lifecycle().restart()
						== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode.TRANSIENT
			) {
				continue;
			}
			DefinitionSnapshot definitions = DEFINITIONS.get(instance.instanceId());
			if (definitions != null) {
				Map<Identifier, FrozenDocument> cached = snapshotDependencies.computeIfAbsent(
					definitions,
					ignored -> new LinkedHashMap<>()
				);
				FrozenDocument dependency = cached.get(instance.cue().id());
				if (dependency == null) {
					var frozen = MessageDependencySnapshotCompiler.freeze(
						definitions,
						instance.cue()
					);
					if (!frozen.success()) {
						PixelTzzPro.LOGGER.error(
							"V3B message {} dependency snapshot failed: {}",
							instance.instanceId(),
							frozen.message()
						);
						return false;
					}
					dependency = frozen.snapshot().orElseThrow();
					cached.put(instance.cue().id(), dependency);
				}
				dependencySnapshots.put(instance.instanceId(), dependency);
			}
			InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
			if (environment != null) {
				origins.put(
					instance.instanceId(),
					new PersistedMessageRuntime.Origin(
						environment.level().identifier(),
						environment.origin().x(),
						environment.origin().y(),
						environment.origin().z()
					)
				);
			}
			List<PersistedMessageRuntime.RecipientState> instanceRecipients =
				new ArrayList<>(instance.cueTargets().size());
			for (UUID recipient : instance.cueTargets().stream().sorted().toList()) {
				instanceRecipients.add(
					persistedRecipientState(instance, recipient)
				);
			}
			recipients.put(instance.instanceId(), List.copyOf(instanceRecipients));
		}

		List<PersistedMessageRuntime.PendingOfflineDelivery> pending =
			new ArrayList<>();
		for (Map.Entry<UUID, DeliverySession> entry : DELIVERIES.entrySet()) {
			MessageInstance instance = runtimeState.instances().get(entry.getKey());
			if (instance == null || instance.status().terminal()) {
				continue;
			}
			DeliverySession delivery = entry.getValue();
			for (UUID recipient : delivery.pendingTargets.stream().sorted().toList()) {
				PersistedMessageRuntime.PendingOfflineDelivery checkpoint =
					delivery.pendingCheckpoints.get(recipient);
				if (checkpoint == null) {
					PixelTzzPro.LOGGER.error(
						"V3B pending delivery {} / {} has no stable checkpoint",
						entry.getKey(),
						recipient
					);
					return false;
				}
				pending.add(checkpoint);
			}
		}

		List<PersistedMessageRuntime.Tombstone> tombstones = world.messageRuntime()
			.filter(snapshot -> snapshot.gameInstanceId().equals(gameInstanceId))
			.map(PersistedMessageRuntime.Snapshot::tombstones)
			.orElse(List.of());
		var mapped = MessageRuntimePersistenceMapper.checkpoint(
			new MessageRuntimePersistenceMapper.CheckpointInput(
				gameInstanceId,
				clock,
				runtimeState,
				dependencySnapshots,
				origins,
				recipients,
				pending,
				tombstones
			)
		);
		if (!mapped.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B message checkpoint mapping failed ({}): {}",
				reason,
				mapped.message()
			);
			return false;
		}
		PersistedMessageRuntime.Snapshot checkpoint = mergeRecoveredFinals(
			mapped.snapshot().orElseThrow(),
			world.messageRuntime()
				.filter(snapshot -> snapshot.gameInstanceId().equals(gameInstanceId))
				.orElse(null)
		);
		var bounded = MessageRuntimePersistenceAuthority.checkpoint(checkpoint);
		if (!bounded.accepted()) {
			PixelTzzPro.LOGGER.error(
				"V3B durable-final checkpoint merge failed ({}): {}",
				reason,
				bounded.message()
			);
			return false;
		}
		OptionalLong expectedRevision = world.messageRuntime()
			.filter(snapshot -> snapshot.gameInstanceId().equals(gameInstanceId))
			.map(snapshot -> OptionalLong.of(snapshot.runtimeRevision()))
			.orElseGet(OptionalLong::empty);
		var committed = wrapper.messageRuntimeCheckpoint(
			gameInstanceId,
			expectedRevision,
			bounded.snapshot()
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.error(
				"V3B message checkpoint commit failed ({}): {}",
				reason,
				committed.reason()
			);
			return false;
		}
		return true;
	}

	/**
	 * Keeps recovered {@code restart=finalize} work in the durable checkpoint even though those
	 * instances deliberately do not re-enter the live authority. Their completed-target set is
	 * the consume-once source of truth across any number of later server stops.
	 */
	private static PersistedMessageRuntime.Snapshot mergeRecoveredFinals(
		final PersistedMessageRuntime.Snapshot live,
		final PersistedMessageRuntime.Snapshot persisted
	) {
		if (persisted == null) {
			return live;
		}
		Map<UUID, PersistedMessageRuntime.Instance> instances = new LinkedHashMap<>();
		for (PersistedMessageRuntime.Instance value : live.instances()) {
			instances.put(value.instanceId(), value);
		}
		for (PersistedMessageRuntime.Instance value : persisted.instances()) {
			if (value.restart() == PersistedMessageRuntime.RestartMode.FINALIZE) {
				instances.putIfAbsent(value.instanceId(), value);
			}
		}
		if (instances.size() == live.instances().size()) {
			return live;
		}
		return new PersistedMessageRuntime.Snapshot(
			live.gameInstanceId(),
			live.snapshotGameTick(),
			live.runtimeRevision(),
			List.copyOf(instances.values()),
			live.pendingOffline(),
			live.tombstones(),
			live.callbackLedger()
		);
	}

	private static PersistedMessageRuntime.RecipientState persistedRecipientState(
		final MessageInstance instance,
		final UUID recipient
	) {
		RecipientStream stream = STREAMS.get(
			new StreamKey(instance.instanceId(), recipient)
		);
		Set<RecipientOccurrence> projected = stream == null
			? Set.of()
			: stream.projectedOccurrences.stream()
				.map(MessageServerRuntime::recipientOccurrence)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<RecipientOccurrence> decided = stream == null
			? Set.of()
			: stream.decidedOccurrences.stream()
				.map(MessageServerRuntime::recipientOccurrence)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Map<RecipientOccurrence, Boolean> frozenCallbacks = new LinkedHashMap<>();
		Map<NodeConditionAddress, Boolean> nodeConditions = new LinkedHashMap<>();
		if (stream != null) {
			stream.frozenCallbackDecisions.forEach(
				(occurrence, matched) ->
					frozenCallbacks.put(recipientOccurrence(occurrence), matched)
			);
			stream.conditionDecisions.forEach(
				(key, matched) -> nodeConditions.put(
					new NodeConditionAddress(
						recipientOccurrence(key.occurrence()),
						key.condition()
					),
					matched
				)
			);
		}
		Optional<Long> completionAt = Optional.ofNullable(
			instance.eventOffsets().get(
				new EventKey(CallbackTrigger.TARGET_COMPLETE, Optional.of(recipient))
			)
		);
		boolean completed = instance.completedTargets().contains(recipient);
		return MessageRecipientStateCodec.encode(
			instance.cue(),
			new RecipientStateSnapshot(
				recipient,
				stream == null ? 0L : stream.createdElapsed,
				stream == null
					? instance.cue().policies().timing().synchronization()
							.orElse(SynchronizationMode.SYNCHRONIZED)
							== SynchronizationMode.PER_PLAYER
						? Optional.empty()
						: Optional.of(0L)
					: Optional.ofNullable(stream.perPlayerAnchorElapsed),
				projected,
				decided,
				stream == null ? Map.of() : stream.lockedValues,
				stream == null ? Map.of() : stream.cueStartConditionDecisions,
				frozenCallbacks,
				nodeConditions,
				completionAt,
				completed
			)
		);
	}

	private static RecipientOccurrence recipientOccurrence(
		final NodeOccurrence occurrence
	) {
		return new RecipientOccurrence(
			occurrence.nodeId(),
			occurrence.occurrence()
		);
	}

	private static ResolvedSpeaker resolveSpeaker(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 world,
		final MessageInstance instance,
		final UUID recipient,
		final SpeakerSpec speaker
	) {
		return switch (speaker.kind()) {
			case SYSTEM -> ResolvedSpeaker.system();
			case NARRATOR -> new ResolvedSpeaker(
				MessagePlaybackPlan.SpeakerKind.NARRATOR,
				Optional.empty(),
				Optional.empty()
			);
			case PLAYER_PARAMETER -> {
				String parameterId = speaker.parameter().orElse(null);
				String parameter = parameterId != null
						&& MessageMinecraftResolver.parameterAuthorized(
							server,
							definitions,
							world,
							instance,
							recipient,
							parameterId
						)
					? instance.canonicalParameters().get(parameterId)
					: null;
				UUID playerId;
				try {
					playerId = parameter == null ? null : UUID.fromString(parameter);
				} catch (IllegalArgumentException error) {
					playerId = null;
				}
				if (playerId == null) {
					yield ResolvedSpeaker.system();
				}
				PlayerRecord record = world.core().players().get(playerId);
				String name = record == null
					? server.services().nameToIdCache()
						.get(playerId)
						.map(value -> value.name())
						.orElse(playerId.toString())
					: record.lastKnownName();
				yield new ResolvedSpeaker(
					MessagePlaybackPlan.SpeakerKind.PLAYER,
					Optional.of(playerId),
					Optional.of(name)
				);
			}
			case REGISTERED_ENTITY -> resolveRegisteredEntitySpeaker(speaker);
		};
	}

	/**
	 * Resolves only real server-registered entity types. Generic entity speakers deliberately carry
	 * the registry type's visible description and no profile identity: the client needs only that
	 * verified label, and must never invent a player UUID, skin, or hat layer for an entity type.
	 *
	 * <p>An absent registry entry or unusable description fails closed to the anonymous system
	 * speaker. This preserves the text node without exposing a data-pack-controlled raw identifier as
	 * a trusted visible identity.</p>
	 */
	static ResolvedSpeaker resolveRegisteredEntitySpeaker(final SpeakerSpec speaker) {
		Identifier entityId = speaker.entity().orElse(null);
		if (
			entityId == null
				|| !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)
		) {
			return ResolvedSpeaker.system();
		}
		return BuiltInRegistries.ENTITY_TYPE.get(entityId)
			.map(reference -> reference.value().getDescription().getString())
			.filter(name -> !name.isBlank())
			.flatMap(name -> {
				try {
					return Optional.of(new ResolvedSpeaker(
						MessagePlaybackPlan.SpeakerKind.ENTITY,
						Optional.empty(),
						Optional.of(name)
					));
				} catch (IllegalArgumentException invalidDescription) {
					return Optional.empty();
				}
			})
			.orElseGet(ResolvedSpeaker::system);
	}

	private static boolean sendStaticFallback(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(recipient);
		if (
			player == null
				|| instance.cue().staticFallback().isEmpty()
				|| !staticFallbackAuthorized(server, instance, recipient)
		) {
			return false;
		}
		var fallback = instance.cue().staticFallback().orElseThrow();
		sendStaticFallback(player, fallback.channel(), fallback.component().canonicalJson());
		return true;
	}

	private static boolean staticFallbackAuthorized(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID recipient
	) {
		try {
			Optional<Set<String>> authorized = authorizedFinalNodes(
				server,
				instance,
				recipient
			);
			return authorized.isPresent()
				&& MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
					instance,
					recipient,
					authorized.orElseThrow()
				);
		} catch (RuntimeException invalidAuthority) {
			PixelTzzPro.LOGGER.warn(
				"Suppressed V3B static fallback {} for {} because current authorization could not be proven: {}",
				instance.instanceId(),
				recipient,
				safeMessage(invalidAuthority)
			);
			return false;
		}
	}

	private static void sendStaticFallback(
		final ServerPlayer player,
		final TextChannel channel,
		final String canonicalComponent
	) {
		sendStaticFallback(
			player,
			channel,
			MessageRuntimeFallbacks.decodeCanonical(canonicalComponent)
		);
	}

	private static void sendStaticFallback(
		final ServerPlayer player,
		final TextChannel channel,
		final Component component
	) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(component, "component");
		switch (channel) {
			case CHAT -> player.sendSystemMessage(component);
			case TITLE -> player.connection.send(
				new ClientboundSetTitleTextPacket(component)
			);
			case SUBTITLE -> player.connection.send(
				new ClientboundSetSubtitleTextPacket(component)
			);
			case ACTION_BAR -> player.connection.send(
				new ClientboundSetActionBarTextPacket(component)
			);
		}
	}

	private static void sendCompilationFallbackOnce(
		final MinecraftServer server,
		final MessageInstance instance,
		final RecipientStream stream,
		final Set<NodeOccurrence> failedOccurrences
	) {
		List<OccurrenceKey> keys = failedOccurrences.stream()
			.sorted(
				Comparator.comparing(NodeOccurrence::nodeId)
					.thenComparingInt(NodeOccurrence::occurrence)
			)
			.map(value -> new OccurrenceKey(value.nodeId(), value.occurrence()))
			.toList();
		boolean visibleFailure = failedOccurrences.stream().anyMatch(occurrence -> {
			CueNode node = cueNode(instance, occurrence.nodeId());
			return node instanceof TextNode
				&& recipientAuthorizedForNode(
					server,
					instance,
					stream.key.recipientId(),
					occurrence.nodeId()
				);
		});
		if (visibleFailure && stream.compilationFallbacks.markFailed(keys)) {
			sendStaticFallback(server, instance, stream.key.recipientId());
		}
	}

	private static void receiveAssetReport(
		final ServerPlayer player,
		final MessageAssetReportC2SPayload payload
	) {
		MessageAssetAuthority authority = ASSET_AUTHORITIES.get(payload.generation());
		if (authority == null) {
			PixelTzzPro.LOGGER.warn(
				"Ignored V3B message asset report from {} before the authority was ready",
				player.getScoreboardName()
			);
			return;
		}
		var result = authority.acceptReport(player.getUUID(), payload);
		if (result.status() != ReportStatus.ACCEPTED) {
			PixelTzzPro.LOGGER.warn(
				"Rejected V3B message asset report from {}: status={}, diagnostics={}",
				player.getScoreboardName(),
				result.status(),
				assetDiagnosticSummary(result.diagnostics())
			);
			return;
		}
		reevaluateRecipientSynchronization(player);
	}

	private static void receiveCapabilities(
		final ServerPlayer player,
		final MessageCapabilitiesC2SPayload payload
	) {
		CapabilityReport previous = CAPABILITIES.get(player.getUUID());
		if (previous != null && payload.reportSequence() <= previous.sequence()) {
			return;
		}
		CAPABILITIES.put(
			player.getUUID(),
			new CapabilityReport(payload.reportSequence(), payload)
		);
		reevaluateRecipientSynchronization(player);
	}

	private static void receiveScreenState(
		final ServerPlayer player,
		final MessageScreenStateC2SPayload payload
	) {
		ScreenReport previous = SCREENS.get(player.getUUID());
		if (previous != null && payload.reportSequence() <= previous.sequence()) {
			return;
		}
		boolean resourceReloadStarted =
			payload.resourceReloading()
				&& (previous == null || !previous.payload().resourceReloading());
		SCREENS.put(player.getUUID(), new ScreenReport(payload.reportSequence(), payload));
		if (resourceReloadStarted) {
			ASSET_AUTHORITIES.values().forEach(authority ->
				authority.markReportPending(player.getUUID())
			);
		}
		reevaluateRecipientSynchronization(player);
	}

	private static void reevaluateRecipientSynchronization(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ClockSample clock = clock(server);
		for (RecipientStream stream : List.copyOf(STREAMS.values())) {
			if (
				!stream.key.recipientId().equals(player.getUUID())
					|| stream.terminal
			) {
				continue;
			}
			MessageInstance instance = runtimeState.instances().get(stream.key.instanceId());
			if (instance == null || !instance.status().active()) {
				continue;
			}
			if (stream.pendingFinalOnly) {
				if (!finalOnlyHandshakePending(server, instance, stream)) {
					deliverFinalOnly(
						server,
						instance,
						stream.key.recipientId(),
						clock
					);
				}
				continue;
			}
			if (stream.recoveryFallbackPending) {
				if (
					!recipientHasAuthorizedTextNode(
						server,
						instance,
						player.getUUID()
					)
				) {
					stream.recoveryFallbackPending = false;
				} else if (sendStaticFallback(server, instance, player.getUUID())) {
					stream.recoveryFallbackPending = false;
				}
				continue;
			}
			if (!ensureSynchronizationDecision(server, instance, stream, clock)) {
				continue;
			}
			if (!stream.initialProjectionAttempted) {
				compileAndSendPlan(server, instance, stream, clock);
			} else if (stream.plan != null && !stream.planSent) {
				sendPlan(server, stream, "READINESS_RETRY");
			}
			flushPendingDueOccurrences(server, instance, stream, clock);
			sendLockedSnapshot(server, stream);
			if (stream.planSent) {
				MessageRecoveryProjectionPolicy.reconnectControl(instance.status())
					.ifPresent(action -> sendControl(
						server,
						instance,
						Set.of(player.getUUID()),
						action,
						clock,
						action.finalizes()
					));
			}
		}
	}

	/**
	 * Delivers cold-start static finals only after the core handshake has restored this player's
	 * authoritative registration, online state, role and forced-flow state. The ordinary JOIN event
	 * runs before that restoration and must not consume durable work on a stale live-strict check.
	 */
	static void playerAuthorityReady(final ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		RECOVERED_STATIC_FINALS.markAuthorityReady(player.getUUID());
		MinecraftServer server = player.level().getServer();
		MessageRecoveredFinalDeliveryBoundary.process(
			RECOVERED_STATIC_FINALS.drainReady(player.getUUID()),
			recovered -> {
				boolean authorized = recoveredStaticFinalAuthorized(
					server,
					recovered,
					player.getUUID()
				);
				if (!authorized) {
					PixelTzzPro.LOGGER.warn(
						"Suppressed unauthorized V3B recovered static final {} for {}",
						recovered.instance().instanceId(),
						player.getUUID()
					);
				}
				return authorized
					? MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED
					: MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
			},
			recovered -> persistRecoveredFinalHistoryForRecipient(
				server,
				recovered,
				player
			),
			recovered -> {
				try {
					return MessageRecoveredFinalDeliveryBoundary.DecodeDecision.decoded(
						MessageRuntimeFallbacks.decodeCanonical(
							recovered.instance()
								.cue()
								.staticFallback()
								.orElseThrow()
								.component()
								.canonicalJson()
						)
					);
				} catch (RuntimeException invalidFallback) {
					PixelTzzPro.LOGGER.error(
						"V3B recovered final {} / {} has an invalid frozen static fallback: {}",
						recovered.instance().instanceId(),
						player.getUUID(),
						safeMessage(invalidFallback),
						invalidFallback
					);
					return MessageRecoveredFinalDeliveryBoundary.DecodeDecision.permanentReject();
				}
			},
			recovered -> prepareRecoveredFinalDelivery(
				server,
				recovered,
				player.getUUID()
			),
			(recovered, component) -> sendStaticFallback(
				player,
				recovered.instance().cue().staticFallback().orElseThrow().channel(),
				component
			),
			recovered -> RECOVERED_STATIC_FINALS.enqueue(
				recovered.instance().instanceId(),
				List.of(player.getUUID()),
				recovered
			),
			(recovered, stage, error) -> PixelTzzPro.LOGGER.error(
				"V3B recovered final {} / {} failed closed during {}: {}",
				recovered.instance().instanceId(),
				player.getUUID(),
				stage,
				safeMessage(error),
				error
			)
		);
	}

	private static void onPlayerJoined(final ServerPlayer player) {
		RECOVERED_STATIC_FINALS.beginConnection(player.getUUID());
		OFFLINE_PLAYERS.remove(player.getUUID());
		CAPABILITIES.remove(player.getUUID());
		SCREENS.remove(player.getUUID());
		/*
		 * A join is a new physical client connection. Clear connection-local delivery facts before
		 * any lifecycle decision can project final content; otherwise a final-only reconnect may
		 * APPEND to an instance that exists only on the previous connection.
		 */
		for (RecipientStream stream : STREAMS.values()) {
			if (stream.key.recipientId().equals(player.getUUID()) && !stream.terminal) {
				stream.planSent = false;
				stream.freshPhysicalConnectionPlan = true;
				stream.connectionFieldSlots.clear();
				stream.connectionLockedSlots.clear();
				stream.connectionNodeOrdinals.clear();
				stream.connectionPlanCycle = -1L;
				stream.pendingConnectionLockedSnapshot = List.of();
				stream.needsLockedSnapshot = true;
				stream.connectionProjectionTerminal = false;
				stream.connectionTerminalAction = null;
			}
		}
		ASSET_AUTHORITIES.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.forEach(authority -> issueAssetManifest(authority, player));
		ClockSample clock = clock(player.level().getServer());
		for (Map.Entry<UUID, DeliverySession> entry : List.copyOf(DELIVERIES.entrySet())) {
			MessageInstance instance = runtimeState.instances().get(entry.getKey());
			if (
				instance == null
					|| !instance.status().active()
					|| (
						!entry.getValue().originalTarget(player.getUUID())
							&& !entry.getValue().pending(player.getUUID())
							&& !instance.cueTargets().contains(player.getUUID())
					)
			) {
				continue;
			}
			JoinDecision decision = entry.getValue()
				.authority
				.onJoin(player.getUUID(), true, clock.presentationNanos());
			applyJoinDecision(
				player.level().getServer(),
				instance,
				entry.getValue(),
				decision,
				clock
			);
		}
	}

	private static void applyJoinDecision(
		final MinecraftServer server,
		final MessageInstance instance,
		final DeliverySession session,
		final JoinDecision decision,
		final ClockSample clock
	) {
		DeliveryDirective directive = decision.directive();
		boolean finalDeliveryDeferred = false;
		if (directive.startsActiveDelivery()) {
			session.rememberJoin(decision);
			StreamKey key = new StreamKey(instance.instanceId(), decision.target());
			RecipientStream stream = STREAMS.get(key);
			boolean reconnectingExistingStream = stream != null && !stream.terminal;
			if (stream == null || stream.terminal) {
				prepareRecipientStream(
					server,
					instance,
					decision.target(),
					ProjectionKind.CREATE,
					Optional.empty(),
					clock
				);
				stream = STREAMS.get(key);
			}
			if (reconnectingExistingStream && stream != null && !stream.terminal) {
				/*
				 * prepareRecipientStream already consumes the join directive and may synchronously
				 * establish the new physical stream. Clearing planSent immediately afterwards causes
				 * the readiness reevaluator to send a second, re-anchored CREATE to the integrated
				 * client. Only an actually pre-existing logical stream needs reconnect re-anchoring.
				 */
				reanchorPlanForDelivery(instance, stream, directive, clock);
				stream.planSent = false;
				stream.needsLockedSnapshot = true;
			}
		} else if (
			directive == DeliveryDirective.FINAL_ONLY_NO_CALLBACK_REPLAY
				|| directive == DeliveryDirective.EXPIRE_FINAL_AND_REMOVE
		) {
			deliverFinalOnly(server, instance, decision.target(), clock);
			RecipientStream stream = STREAMS.get(
				new StreamKey(instance.instanceId(), decision.target())
			);
			finalDeliveryDeferred = stream != null && stream.pendingFinalOnly;
		} else if (
			directive == DeliveryDirective.DROP
				|| directive == DeliveryDirective.EXPIRE_DROP_AND_REMOVE
		) {
			settleDeliveryTarget(server, instance, decision.target(), clock);
		}
		if (
			!finalDeliveryDeferred
				&& decision.instanceDirective() == InstanceDirective.COMPLETE
				&& runtimeState.instances()
					.getOrDefault(instance.instanceId(), instance)
					.status()
					.active()
		) {
			controlInstance(
				server,
				instance.instanceId(),
				ControlAction.COMPLETE,
				clock,
				"message delivery reconnect settled the final recipient"
			);
		}
	}

	private static void reanchorPlanForDelivery(
		final MessageInstance instance,
		final RecipientStream stream,
		final DeliveryDirective directive,
		final ClockSample clock
	) {
		if (stream.plan == null) {
			if (directive.newVisualAnchor()) {
				stream.visualAnchorNanos = clock.value(instance.clockMode());
			}
			return;
		}
		ClockKind kind = stream.plan.clock().kind();
		long serverNow = kind == ClockKind.GAME_TICK
			? clock.gameTick()
			: clock.presentationNanos();
		long startAt = directive.newVisualAnchor()
			? serverNow
			: stream.plan.clock().startAt();
		if (directive.newVisualAnchor()) {
			stream.visualAnchorNanos = clock.value(instance.clockMode());
		}
		stream.plan = new MessagePlaybackPlan(
			stream.plan.instanceId(),
			stream.plan.cueId(),
			stream.plan.generation(),
			stream.plan.sequence(),
			stream.plan.cycle(),
			stream.plan.disposition(),
			stream.plan.replacedInstanceId(),
			new ClockAnchor(kind, serverNow, startAt),
			stream.plan.policies(),
			stream.plan.origin(),
			stream.plan.nodes()
		);
	}

	private static void onPlayerDisconnected(final ServerPlayer player) {
		OFFLINE_PLAYERS.add(player.getUUID());
		CAPABILITIES.remove(player.getUUID());
		SCREENS.remove(player.getUUID());
		ASSET_AUTHORITIES.values().forEach(authority ->
			authority.closeConnection(player.getUUID())
		);
		for (RecipientStream stream : STREAMS.values()) {
			if (stream.key.recipientId().equals(player.getUUID())) {
				stream.planSent = false;
				stream.freshPhysicalConnectionPlan = true;
				stream.connectionFieldSlots.clear();
				stream.connectionLockedSlots.clear();
				stream.connectionNodeOrdinals.clear();
				stream.connectionPlanCycle = -1L;
				stream.pendingConnectionLockedSnapshot = List.of();
				stream.needsLockedSnapshot = true;
				stream.connectionProjectionTerminal = false;
				stream.connectionTerminalAction = null;
			}
		}
	}

	private static void onPlayerRespawned(final ServerPlayer player) {
		applyPlayerLifecycle(player, Event.RESPAWN, null, null);
	}

	private static void onPlayerChangedLevel(
		final ServerPlayer player,
		final ServerLevel previous,
		final ServerLevel current
	) {
		if (previous.dimension().equals(current.dimension())) {
			return;
		}
		applyPlayerLifecycle(player, Event.DIMENSION_EXIT, previous, current);
	}

	private static void applyPlayerLifecycle(
		final ServerPlayer player,
		final Event event,
		final ServerLevel previous,
		final ServerLevel current
	) {
		MinecraftServer server = player.level().getServer();
		ClockSample clock = clock(server);
		UUID target = player.getUUID();
		for (MessageInstance snapshot : List.copyOf(runtimeState.instances().values())) {
			MessageInstance instance = runtimeState.instances().get(snapshot.instanceId());
			if (
				instance == null
					|| (
						instance.status() != InstanceStatus.ACTIVE
							&& instance.status() != InstanceStatus.PAUSED
					)
					|| !instance.cueTargets().contains(target)
					|| instance.completedTargets().contains(target)
			) {
				continue;
			}
			boolean leftOriginDimension = event == Event.DIMENSION_EXIT
				&& instance.cue().policies().lifecycle().attachment()
					== AttachmentMode.WORLD
				&& leftFrozenOriginDimension(instance, previous, current);
			Directive directive = MessageLifecyclePolicyRouter.resolve(
				instance.cue().policies().lifecycle(),
				event,
				leftOriginDimension
			);
			switch (directive) {
				case CONTINUE -> {
				}
				case FINALIZE -> finalizeLifecycleTarget(
					server,
					instance,
					target,
					clock
				);
				case CANCEL -> cancelLifecycleTarget(
					server,
					instance,
					target,
					clock,
					lifecycleReason(event)
				);
			}
		}
	}

	private static boolean leftFrozenOriginDimension(
		final MessageInstance instance,
		final ServerLevel previous,
		final ServerLevel current
	) {
		if (previous == null || current == null) {
			return false;
		}
		InvocationEnvironment environment = ENVIRONMENTS.get(instance.instanceId());
		if (environment == null) {
			PixelTzzPro.LOGGER.warn(
				"V3B world-attached message {} has no frozen origin during a dimension change",
				instance.instanceId()
			);
			/* Fail closed through the cue's own dimension_exit policy. */
			return true;
		}
		return previous.dimension().equals(environment.level())
			&& !current.dimension().equals(environment.level());
	}

	private static void finalizeLifecycleTarget(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID target,
		final ClockSample clock
	) {
		RecipientStream stream = STREAMS.get(
			new StreamKey(instance.instanceId(), target)
		);
		if (stream == null || !stream.terminal) {
			deliverFinalOnly(server, instance, target, clock);
			return;
		}
		settleDeliveryTarget(server, instance, target, clock);
	}

	private static void cancelLifecycleTarget(
		final MinecraftServer server,
		final MessageInstance instance,
		final UUID target,
		final ClockSample clock,
		final String reason
	) {
		ControlResult result = MessageInstanceAuthority.cancelTarget(
			runtimeState,
			instance.instanceId(),
			target,
			clock,
			reason
		);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.warn(
				"V3B lifecycle target cancellation was rejected for {} in {}: {}",
				target,
				instance.instanceId(),
				result.message()
			);
			return;
		}
		runtimeState = result.nextState();
		DeliverySession delivery = DELIVERIES.get(instance.instanceId());
		if (delivery != null) {
			delivery.authority.markDeliveryComplete(target);
		}
		processCommittedWork(server, result.projections(), result.callbacks(), clock);
	}

	private static String lifecycleReason(final Event event) {
		return switch (event) {
			case DATA_PACK_RELOAD -> "data-pack resources reloaded";
			case RESPAWN -> "recipient respawned";
			case DIMENSION_EXIT ->
				"world-attached recipient left the frozen origin dimension";
		};
	}

	private static void onDataPackReload(
		final MinecraftServer server,
		final boolean successful
	) {
		if (!successful) {
			return;
		}
		ClockSample clock = clock(server);
		for (MessageInstance instance : List.copyOf(runtimeState.instances().values())) {
			if (!instance.status().active()) {
				continue;
			}
			Directive directive = MessageLifecyclePolicyRouter.resolve(
				instance.cue().policies().lifecycle(),
				Event.DATA_PACK_RELOAD,
				false
			);
			if (directive != Directive.CONTINUE) {
				controlInstance(
					server,
					instance.instanceId(),
					directive == Directive.FINALIZE
						? ControlAction.COMPLETE
						: ControlAction.CANCEL,
					clock,
					lifecycleReason(Event.DATA_PACK_RELOAD)
				);
			}
		}
	}

	private static void controlInstance(
		final MinecraftServer server,
		final UUID instanceId,
		final ControlAction action,
		final ClockSample clock,
		final String reason
	) {
		ControlResult result = MessageInstanceAuthority.control(
			runtimeState,
			new InstanceSelector(instanceId),
			action,
			clock,
			reason
		);
		if (!result.accepted()) {
			PixelTzzPro.LOGGER.warn(
				"V3B lifecycle control rejected for {}: {}",
				instanceId,
				result.message()
			);
			return;
		}
		runtimeState = result.nextState();
		processCommittedWork(server, result.projections(), result.callbacks(), clock);
	}

	private static ClockSample clock(final MinecraftServer server) {
		long elapsed = System.nanoTime() - presentationOriginNanos;
		long presentation;
		try {
			presentation = Math.addExact(
				presentationClockOffsetNanos,
				Math.max(0L, elapsed)
			);
		} catch (ArithmeticException overflow) {
			presentation = Long.MAX_VALUE;
		}
		if (presentation < lastPresentationNanos) {
			presentation = lastPresentationNanos;
		}
		lastPresentationNanos = presentation;
		return new ClockSample(server.overworld().getGameTime(), presentation);
	}

	private static Map<String, String> readStorageArguments(
		final MinecraftServer server,
		final StorageArguments storage
	) {
		Tag root = server.getCommandStorage().get(storage.storage());
		final List<Tag> matches;
		try {
			matches = storage.path().get(root);
		} catch (CommandSyntaxException error) {
			throw new IllegalArgumentException("参数路径不存在", error);
		}
		if (matches.size() != 1 || !(matches.getFirst() instanceof CompoundTag compound)) {
			throw new IllegalArgumentException("参数路径必须唯一指向一个对象");
		}
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (String key : compound.keySet()) {
			Tag value = compound.get(key);
			if (value == null) {
				continue;
			}
			String canonical = value.asString()
				.or(() -> value.asNumber().map(Object::toString))
				.orElseGet(() -> {
					JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value);
					return json.toString();
				});
			result.put(key, canonical);
		}
		return Map.copyOf(result);
	}

	private static PlayCommandResult disabledCueMessage(
		final DefinitionSnapshot definitions,
		final Identifier cueId
	) {
		var disabled = definitions.messageCatalog().disabled().entrySet()
			.stream()
			.filter(entry -> entry.getKey().id().equals(cueId))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
		if (disabled == null) {
			return PlayCommandResult.failed("未注册动态消息「" + cueId + "」。");
		}
		String diagnostic = disabled.diagnostics().isEmpty()
			? "定义不可用"
			: disabled.diagnostics().getFirst().message();
		return PlayCommandResult.failed(
			"动态消息「" + cueId + "」已被隔离：" + diagnostic
		);
	}

	static String localizedAuthorityMessage(final String message) {
		String normalized = message == null ? "" : message.strip();
		if (
			!normalized.isEmpty()
				&& normalized.codePoints().anyMatch(codePoint ->
					Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
				)
		) {
			return normalized;
		}
		String localized = switch (normalized) {
			case "message invocation is not authorized" ->
				"当前来源没有发起动态消息的权限。";
			case "requested message instance id is already reserved" ->
				"动态消息实例标识发生冲突，请重新执行。";
			case "message cue does not belong to the active game" ->
				"该动态消息不属于当前游戏。";
			case "message invocation is outside its registered game context" ->
				"当前游戏、阶段或任务不允许发起该动态消息。";
			case "message cue resolved an empty audience" -> "没有符合此消息受众规则的玩家。";
			case "matching message instance is already active" -> "相同动态消息正在播放。";
			case "message invocation is inside its registered cooldown" -> "动态消息仍在冷却时间内。";
			case "parameter resolver returned no result" ->
				"动态消息参数没有产生可用结果。";
			case "audience resolver returned no result" ->
				"动态消息受众没有产生可用结果。";
			case "refresh duplicate mode requires a registered stable refresh key" ->
				"该动态消息启用了原位刷新，但没有注册稳定刷新键。";
			case "registered refresh-key parameter has no canonical value" ->
				"动态消息缺少稳定刷新键所需的参数值。";
			case "message cue reached its active-instance limit" ->
				"该动态消息的活动实例已达上限。";
			case "message runtime reached its hard instance capacity" ->
				"动态消息运行时已达实例容量上限。";
			case "message refresh index reached its hard capacity" ->
				"动态消息刷新索引已达容量上限。";
			case "message refresh counter is exhausted" ->
				"动态消息刷新次数已达到安全上限。";
			case "message queue reached its bounded capacity" ->
				"动态消息队列已满。";
			case "control selector matched no message instance" ->
				"没有匹配的动态消息实例。";
			case "control reason is invalid" ->
				"动态消息控制请求无效。";
			default -> {
				if (normalized.startsWith("required canonical parameter is missing: ")) {
					yield "缺少必填动态消息参数："
						+ normalized.substring(
							"required canonical parameter is missing: ".length()
						);
				}
				if (normalized.startsWith("parameter resolver returned undeclared parameter ")) {
					yield "动态消息参数解析器返回了未声明参数："
						+ normalized.substring(
							"parameter resolver returned undeclared parameter ".length()
						);
				}
				if (
					normalized.startsWith(
						"content-driven text node has no authoritative duration: "
					)
				) {
					yield "动态消息文本节点缺少权威时长："
						+ normalized.substring(
							"content-driven text node has no authoritative duration: ".length()
						);
				}
				if (normalized.startsWith("audience resolver returned targets for undeclared node ")) {
					yield "动态消息受众解析器引用了未声明节点："
						+ normalized.substring(
							"audience resolver returned targets for undeclared node ".length()
						);
				}
				yield null;
			}
		};
		if (localized != null) {
			return localized;
		}
		String diagnostic = normalized.isEmpty() ? "missing authority diagnostic" : normalized;
		PixelTzzPro.LOGGER.warn(
			"V3B authority rejected a command; detailed diagnostic: {}",
			diagnostic.length() > 512 ? diagnostic.substring(0, 512) : diagnostic
		);
		if (
			normalized.startsWith("parameter resolver failed:")
				|| normalized.startsWith("message timeline is invalid:")
				|| normalized.startsWith("message refresh timeline is invalid:")
		) {
			return "动态消息参数或时间线无法解析，请查看服务端日志。";
		}
		if (
			normalized.startsWith("audience resolver failed:")
				|| normalized.startsWith("message audience normalization failed:")
		) {
			return "动态消息受众无法解析，请查看服务端日志。";
		}
		return "动态消息未能执行，请查看服务端日志中的详细诊断。";
	}

	private static Optional<UUID> single(final Set<UUID> values) {
		return values.size() == 1
			? Optional.of(values.iterator().next())
			: Optional.empty();
	}

	private static long safeIncrement(final long value) {
		return Math.incrementExact(value);
	}

	private static long ceilTicks(final long nanoseconds) {
		if (nanoseconds <= 0L) {
			throw new IllegalArgumentException(
				"positive duration required for tick conversion"
			);
		}
		return Math.floorDiv(nanoseconds - 1L, TimeSpan.NANOS_PER_TICK) + 1L;
	}

	private static String safeMessage(final Throwable error) {
		String message = error.getMessage();
		return message == null || message.isBlank()
			? error.getClass().getSimpleName()
			: message.length() > 512 ? message.substring(0, 512) : message;
	}

	public record RequiredAssetsGate(
		boolean ready,
		int participantCount,
		int blockedParticipantCount,
		List<String> diagnostics
	) {
		public RequiredAssetsGate {
			if (
				participantCount < 0
					|| blockedParticipantCount < 0
					|| blockedParticipantCount > participantCount
			) {
				throw new IllegalArgumentException("invalid asset-gate participant counts");
			}
			diagnostics = List.copyOf(diagnostics);
			if (diagnostics.size() > MAX_ASSET_GATE_DIAGNOSTICS) {
				throw new IllegalArgumentException("asset-gate diagnostics exceed limit");
			}
			if (diagnostics.stream().anyMatch(value -> value.length() > MAX_ASSET_GATE_DIAGNOSTIC_LENGTH)) {
				throw new IllegalArgumentException("asset-gate diagnostic exceeds length limit");
			}
			if (ready != (blockedParticipantCount == 0 && diagnostics.isEmpty())) {
				throw new IllegalArgumentException("asset-gate state and diagnostics disagree");
			}
		}

		private static RequiredAssetsGate ready(final int participantCount) {
			return new RequiredAssetsGate(true, participantCount, 0, List.of());
		}

		private static RequiredAssetsGate blocked(
			final int participantCount,
			final int blockedParticipantCount,
			final List<String> diagnostics
		) {
			List<String> bounded = diagnostics.stream()
				.map(MessageServerRuntime::boundedAssetDiagnostic)
				.limit(MAX_ASSET_GATE_DIAGNOSTICS)
				.toList();
			if (bounded.isEmpty()) {
				bounded = List.of("消息资源检查未通过，请稍后重新审阅。");
			}
			return new RequiredAssetsGate(
				false,
				participantCount,
				blockedParticipantCount,
				bounded
			);
		}

		public String message() {
			if (this.ready) {
				return "开局必需消息资源检查已通过。";
			}
			StringBuilder message = new StringBuilder(
				"开局消息资源检查未通过：受阻 "
					+ this.blockedParticipantCount
					+ "/"
					+ this.participantCount
					+ " 人。"
			);
			for (String diagnostic : this.diagnostics) {
				if (message.length() >= MAX_ASSET_GATE_MESSAGE_LENGTH) {
					break;
				}
				message.append(" ").append(diagnostic);
			}
			return boundedText(message.toString(), MAX_ASSET_GATE_MESSAGE_LENGTH);
		}
	}

	public record StorageArguments(
		Identifier storage,
		NbtPathArgument.NbtPath path
	) {
		public StorageArguments {
			storage = Objects.requireNonNull(storage, "storage");
			path = Objects.requireNonNull(path, "path");
		}
	}

	public record PlayCommandResult(
		boolean successful,
		String message,
		int affectedTargets,
		Optional<UUID> instanceId
	) {
		public PlayCommandResult {
			message = Objects.requireNonNull(message, "message");
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			if (affectedTargets < 0 || (!successful && (affectedTargets != 0 || instanceId.isPresent()))) {
				throw new IllegalArgumentException("play command result is inconsistent");
			}
		}

		private static PlayCommandResult succeeded(
			final String message,
			final int targets,
			final UUID instanceId
		) {
			return new PlayCommandResult(
				true,
				message,
				targets,
				Optional.of(instanceId)
			);
		}

		private static PlayCommandResult failed(final String message) {
			return new PlayCommandResult(false, message, 0, Optional.empty());
		}
	}

	public record ControlCommandResult(
		boolean successful,
		String message,
		int matched,
		int changed
	) {
		public ControlCommandResult {
			message = Objects.requireNonNull(message, "message");
			if (
				matched < 0
					|| changed < 0
					|| changed > matched
					|| (!successful && (matched != 0 || changed != 0))
			) {
				throw new IllegalArgumentException(
					"control command result is inconsistent"
				);
			}
		}

		private static ControlCommandResult succeeded(
			final String message,
			final int matched,
			final int changed
		) {
			return new ControlCommandResult(true, message, matched, changed);
		}

		private static ControlCommandResult failed(final String message) {
			return new ControlCommandResult(false, message, 0, 0);
		}
	}

	private record DeliveryPreparation(
		AudienceResolution effectiveAudience,
		Optional<DeliverySession> session
	) {
		private DeliveryPreparation {
			effectiveAudience = Objects.requireNonNull(
				effectiveAudience,
				"effectiveAudience"
			);
			session = Objects.requireNonNull(session, "session");
		}
	}

	private record LiveAudienceDecision(
		AudienceResolution audience,
		Set<UUID> finalOnlyTargets
	) {
		private LiveAudienceDecision {
			audience = Objects.requireNonNull(audience, "audience");
			finalOnlyTargets = Set.copyOf(finalOnlyTargets);
		}
	}

	private static final class DeliverySession {
		private final MessageDeliveryLifecycleAuthority authority;
		private final Set<UUID> originalTargets;
		private final Map<UUID, DeliveryDirective> initialDirectives;
		private final Set<UUID> pendingTargets;
		private final Map<UUID, PersistedMessageRuntime.PendingOfflineDelivery>
			pendingCheckpoints = new LinkedHashMap<>();
		private final Map<UUID, PendingCheckpointTemplate> pendingTemplates =
			new LinkedHashMap<>();
		private final Set<UUID> initialConsumed = new LinkedHashSet<>();
		private final Map<UUID, DeliveryDirective> joinDirectives =
			new LinkedHashMap<>();

		private DeliverySession(
			final MessageDeliveryLifecycleAuthority authority,
			final Set<UUID> originalTargets,
			final Map<UUID, DeliveryDirective> initialDirectives,
			final Set<UUID> pendingTargets,
			final long pendingQueuedAtGameTick,
			final MessageCueDefinition cue
		) {
			this.authority = Objects.requireNonNull(authority, "authority");
			this.originalTargets = Set.copyOf(originalTargets);
			this.initialDirectives = Map.copyOf(initialDirectives);
			this.pendingTargets = new LinkedHashSet<>(pendingTargets);
			if (pendingQueuedAtGameTick < 0L) {
				throw new IllegalArgumentException(
					"pending delivery checkpoint tick cannot be negative"
				);
			}
			Optional<Long> expiry = pendingTargets.isEmpty()
				? Optional.empty()
				: Optional.of(
					Math.addExact(
						pendingQueuedAtGameTick,
						ceilTicks(
							cue.policies()
								.delivery()
								.offlineTtl()
								.orElseThrow()
								.nanoseconds()
						)
					)
				);
			Optional<String> finalCanonicalComponent = cue.staticFallback()
				.map(value -> value.component().canonicalJson());
			for (UUID target : this.pendingTargets) {
				DeliveryDirective directive = this.initialDirectives.get(target);
				PersistedMessageRuntime.OfflineMode mode =
					directive == DeliveryDirective.FINAL_ONLY_UNTIL_TTL
						? PersistedMessageRuntime.OfflineMode.FINAL_ONLY
						: PersistedMessageRuntime.OfflineMode.QUEUE;
				Optional<String> finalComponent =
					mode == PersistedMessageRuntime.OfflineMode.FINAL_ONLY
						? finalCanonicalComponent
						: Optional.empty();
				if (
					mode == PersistedMessageRuntime.OfflineMode.FINAL_ONLY
						&& finalComponent.isEmpty()
				) {
					throw new IllegalArgumentException(
						"final-only pending delivery requires a static fallback"
					);
				}
				this.pendingTemplates.put(
					target,
					new PendingCheckpointTemplate(
						mode,
						pendingQueuedAtGameTick,
						expiry,
						finalComponent
					)
				);
			}
		}

		private static DeliverySession restore(
			final MessageDeliveryLifecycleAuthority authority,
			final Set<UUID> originalTargets,
			final List<PersistedMessageRuntime.PendingOfflineDelivery> pending,
			final MessageCueDefinition cue
		) {
			return new DeliverySession(authority, originalTargets, pending, cue);
		}

		private DeliverySession(
			final MessageDeliveryLifecycleAuthority authority,
			final Set<UUID> originalTargets,
			final List<PersistedMessageRuntime.PendingOfflineDelivery> pending,
			final MessageCueDefinition cue
		) {
			this.authority = Objects.requireNonNull(authority, "authority");
			this.originalTargets = Set.copyOf(originalTargets);
			Objects.requireNonNull(cue, "cue");
			Optional<String> expectedFinal = cue.staticFallback()
				.map(fallback -> fallback.component().canonicalJson());
			Map<UUID, DeliveryDirective> directives = new LinkedHashMap<>();
			for (UUID target : this.originalTargets) {
				directives.put(target, DeliveryDirective.START_AT_SHARED_ANCHOR);
			}
			for (PersistedMessageRuntime.PendingOfflineDelivery value : pending) {
				if (
					!this.originalTargets.contains(value.recipientId())
						|| this.pendingCheckpoints.put(value.recipientId(), value) != null
				) {
					throw new IllegalArgumentException(
						"restored pending delivery is duplicated or outside its audience"
					);
				}
				if (
					value.mode() == PersistedMessageRuntime.OfflineMode.FINAL_ONLY
						&& !expectedFinal.equals(value.finalCanonicalComponent())
				) {
					throw new IllegalArgumentException(
						"restored final-only delivery no longer matches static fallback"
					);
				}
				directives.put(
					value.recipientId(),
					value.mode() == PersistedMessageRuntime.OfflineMode.FINAL_ONLY
						? DeliveryDirective.FINAL_ONLY_UNTIL_TTL
						: DeliveryDirective.QUEUE_UNTIL_TTL
				);
			}
			this.initialDirectives = Map.copyOf(directives);
			this.pendingTargets = new LinkedHashSet<>(this.pendingCheckpoints.keySet());
		}

		private void bindInstanceId(final UUID instanceId) {
			Objects.requireNonNull(instanceId, "instanceId");
			if (!this.pendingCheckpoints.isEmpty()) {
				if (
					this.pendingCheckpoints.values().stream().anyMatch(value ->
						!value.instanceId().equals(instanceId)
					)
				) {
					throw new IllegalArgumentException(
						"pending delivery checkpoint belongs to another instance"
					);
				}
				return;
			}
			this.pendingTemplates.forEach((target, template) ->
				this.pendingCheckpoints.put(
					target,
					new PersistedMessageRuntime.PendingOfflineDelivery(
						instanceId,
						target,
						template.mode(),
						template.queuedAtGameTick(),
						template.expiresAtGameTick(),
						template.finalCanonicalComponent()
					)
				)
			);
		}

		private DeliveryDirective takeProjectionDirective(final UUID target) {
			DeliveryDirective joined = this.joinDirectives.remove(target);
			if (joined != null) {
				removePending(target);
				this.initialConsumed.add(target);
				return joined;
			}
			if (this.pendingTargets.contains(target)) {
				return this.initialDirectives.getOrDefault(
					target,
					DeliveryDirective.QUEUE_UNTIL_TTL
				);
			}
			if (this.initialConsumed.add(target)) {
				return this.initialDirectives.getOrDefault(
					target,
					DeliveryDirective.START_AT_SHARED_ANCHOR
				);
			}
			return DeliveryDirective.START_AT_SHARED_ANCHOR;
		}

		private void rememberJoin(final JoinDecision decision) {
			removePending(decision.target());
			this.joinDirectives.put(decision.target(), decision.directive());
		}

		private void removePending(final UUID target) {
			this.pendingTargets.remove(target);
			this.pendingCheckpoints.remove(target);
			this.pendingTemplates.remove(target);
		}

		private boolean originalTarget(final UUID target) {
			return this.originalTargets.contains(target);
		}

		private boolean pending(final UUID target) {
			return this.pendingTargets.contains(target);
		}

		private Optional<String> frozenFinalComponent(final UUID target) {
			PersistedMessageRuntime.PendingOfflineDelivery checkpoint =
				this.pendingCheckpoints.get(target);
			if (checkpoint != null) {
				return checkpoint.finalCanonicalComponent();
			}
			PendingCheckpointTemplate template = this.pendingTemplates.get(target);
			return template == null
				? Optional.empty()
				: template.finalCanonicalComponent();
		}
	}

	private record PendingCheckpointTemplate(
		PersistedMessageRuntime.OfflineMode mode,
		long queuedAtGameTick,
		Optional<Long> expiresAtGameTick,
		Optional<String> finalCanonicalComponent
	) {
		private PendingCheckpointTemplate {
			Objects.requireNonNull(mode, "mode");
			expiresAtGameTick = Objects.requireNonNull(
				expiresAtGameTick,
				"expiresAtGameTick"
			);
			finalCanonicalComponent = Objects.requireNonNull(
				finalCanonicalComponent,
				"finalCanonicalComponent"
			);
		}
	}

	private record RecoveredStaticFinal(
		MessageInstance instance,
		PersistedMessageRuntime.FrozenCue frozenCue,
		MessageRuntimePersistenceMapper.RestoredInvocationEnvironment environment,
		long authorizedDefinitionGeneration
	) {
		private RecoveredStaticFinal {
			instance = Objects.requireNonNull(instance, "instance");
			frozenCue = Objects.requireNonNull(frozenCue, "frozenCue");
			environment = Objects.requireNonNull(environment, "environment");
			if (authorizedDefinitionGeneration < 0L) {
				throw new IllegalArgumentException(
					"recovered final definition generation cannot be negative"
				);
			}
		}
	}

	private record StreamKey(UUID instanceId, UUID recipientId) {
		private StreamKey {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			recipientId = Objects.requireNonNull(recipientId, "recipientId");
		}
	}

	private record ConditionDecisionKey(
		NodeOccurrence occurrence,
		ConditionSpec condition
	) {
		private ConditionDecisionKey {
			occurrence = Objects.requireNonNull(occurrence, "occurrence");
			condition = Objects.requireNonNull(condition, "condition");
		}
	}

	private static final class RecipientStream {
		private final StreamKey key;
		private final long planSequence;
		private final PlanDisposition disposition;
		private final Optional<UUID> replacedInstanceId;
		private final long createdElapsed;
		private final SynchronizationMode synchronizationMode;
		private final Optional<ScreenStartMode> perPlayerGateMode;
		private final Set<String> dueConditionalNodes = new LinkedHashSet<>();
		private final Set<NodeOccurrence> decidedOccurrences = new LinkedHashSet<>();
		private final Set<NodeOccurrence> projectedOccurrences = new LinkedHashSet<>();
		private final Set<NodeOccurrence> pendingDueOccurrences = new LinkedHashSet<>();
		private final Set<NodeOccurrence> captureGateShifted = new LinkedHashSet<>();
		private final Map<NodeOccurrence, Long> occurrenceActualStarts =
			new LinkedHashMap<>();
		private final Map<NodeOccurrence, Projection> frozenCueStartProjections =
			new LinkedHashMap<>();
		private final Map<NodeOccurrence, Boolean> frozenCallbackDecisions =
			new LinkedHashMap<>();
		private final Map<ConditionSpec, Boolean> cueStartConditionDecisions =
			new LinkedHashMap<>();
		private final Map<ConditionDecisionKey, Boolean> conditionDecisions =
			new LinkedHashMap<>();
		private final Map<NodeOccurrence, MessageOccurrencePlayback> occurrencePlaybacks =
			new LinkedHashMap<>();
		private final CompilationGate compilationFallbacks = new CompilationGate();
		private final Map<NodeOccurrence, List<ResolvedNode>> nodesByOccurrence =
			new LinkedHashMap<>();
		private final Map<NodeOccurrence, Long> occurrenceDurations =
			new LinkedHashMap<>();
		private final Map<CaptureIdentity, Integer> slotIds = new LinkedHashMap<>();
		private final Map<Integer, CaptureBinding> captures = new LinkedHashMap<>();
		private final Set<Integer> lockedSlots = new LinkedHashSet<>();
		private final Map<Integer, MessagePlaybackPlan.ResolvedComponent> lockedValues =
			new LinkedHashMap<>();
		private final Set<Integer> connectionNodeOrdinals = new LinkedHashSet<>();
		private final Set<Integer> connectionFieldSlots = new LinkedHashSet<>();
		private final Set<Integer> connectionLockedSlots = new LinkedHashSet<>();
		private int nextOrdinal;
		private int nextSlot;
		private MessagePlaybackPlan plan;
		private long lastSequence;
		private long connectionPlanCycle = -1L;
		private Long perPlayerAnchorElapsed;
		private Long visualAnchorNanos;
		private NodeOccurrence compilingOccurrence;
		private boolean initialProjectionAttempted;
		private boolean screenTimeoutApplied;
		private boolean planSent;
		private boolean freshPhysicalConnectionPlan;
		private boolean needsLockedSnapshot;
		private List<FieldValue> pendingConnectionLockedSnapshot = List.of();
		private boolean connectionProjectionTerminal;
		private MessageControlS2CPayload.Action connectionTerminalAction;
		private boolean terminal;
		private boolean pendingFinalOnly;
		private OptionalLong finalOnlyHandshakeDeadlineGameTick = OptionalLong.empty();
		private boolean targetCompletionCommitted;
		private boolean recoveryFallbackPending;
		private boolean synchronizationStaticFinal;
		private boolean synchronizationStaticFinalSent;
		private boolean synchronizationCancelApplied;

		private RecipientStream(
			final StreamKey key,
			final long planSequence,
			final PlanDisposition disposition,
			final Optional<UUID> replacedInstanceId,
			final long createdElapsed,
			final SynchronizationMode synchronizationMode,
			final Optional<ScreenStartMode> perPlayerGateMode
		) {
			this.key = Objects.requireNonNull(key, "key");
			this.planSequence = planSequence;
			this.disposition = Objects.requireNonNull(disposition, "disposition");
			this.replacedInstanceId = Objects.requireNonNull(
				replacedInstanceId,
				"replacedInstanceId"
			);
			if (createdElapsed < 0L) {
				throw new IllegalArgumentException(
					"recipient stream creation time cannot be negative"
				);
			}
			this.createdElapsed = createdElapsed;
			this.synchronizationMode = Objects.requireNonNull(
				synchronizationMode,
				"synchronizationMode"
			);
			this.perPlayerGateMode = Objects.requireNonNull(
				perPlayerGateMode,
				"perPlayerGateMode"
			);
			this.perPlayerAnchorElapsed = null;
			this.lastSequence = planSequence;
		}
	}

	private record NormalizedProjection(
		List<ResolvedNode> nodes,
		Map<Integer, CaptureBinding> captures,
		Set<NodeOccurrence> occurrences,
		Map<NodeOccurrence, List<ResolvedNode>> nodesByOccurrence
	) {
		private NormalizedProjection {
			nodes = List.copyOf(nodes);
			captures = Map.copyOf(captures);
			occurrences = Set.copyOf(occurrences);
			Map<NodeOccurrence, List<ResolvedNode>> copied = new LinkedHashMap<>();
			nodesByOccurrence.forEach(
				(key, value) -> copied.put(key, List.copyOf(value))
			);
			nodesByOccurrence = Map.copyOf(copied);
		}
	}

	private enum FinalProjectionResult {
		READY,
		ZERO_VISIBLE,
		FAILED
	}

	private record DeferredCompletionWork(
		boolean accepted,
		List<ProjectionEvent> projections,
		List<PreparedCallback> callbacks
	) {
		private DeferredCompletionWork {
			projections = List.copyOf(projections);
			callbacks = List.copyOf(callbacks);
		}

		private static DeferredCompletionWork rejected() {
			return new DeferredCompletionWork(false, List.of(), List.of());
		}
	}

	private record CapabilityReport(
		long sequence,
		MessageCapabilitiesC2SPayload payload
	) {
	}

	private record ScreenReport(
		long sequence,
		MessageScreenStateC2SPayload payload
	) {
	}
}
