package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.BuiltInUiResources;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.ReloadOutcome;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.View;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ApplyTiming;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignRoleOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BranchNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChangeStateNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ConfirmNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FunctionNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.WaitPlayersNode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.lifecycle.GamePhase;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.CancelConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CommitConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActiveFlowSummary;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.Diagnostic;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.HostInfo;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.MemberSummary;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostUiStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageCloseC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PrepareOperationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.StatefulRequest;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.FlowCompletionStatus;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Accepted;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Binding;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.ConsumeResult;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.IssuedConfirmation;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.OperationKey;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Prompt;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Rejected;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.Rejection;
import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackAuthority.OutcomeResult;
import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackAuthority.PrepareResult;
import io.github.zcpu954861.pixeltzzpro.server.FlowCallbackAuthority.PreparedInvocation;
import io.github.zcpu954861.pixeltzzpro.server.FlowRosterAuthority.OnlineMember;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ConnectedPlayer;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PanelActionAuthorization;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateContext;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.StartResult;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.Actor;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.OnlineTarget;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldStateMigration;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackFailure;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CallbackKind;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FieldValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowInstanceStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FlowMemberStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.ForcedFlowInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerFlowParticipation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContextDocument;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.Permissions;

/**
 * Owns server lifecycle, handshake validation, snapshots, and persisted reload revisions.
 */
public final class PixelTzzServerRuntime {
	private static final int HANDSHAKE_TIMEOUT_TICKS = 200;
	private static final int PERMISSION_REFRESH_INTERVAL_TICKS = 20;
	private static final Set<UUID> VERIFIED_PLAYERS = new HashSet<>();
	private static final Map<UUID, Integer> HANDSHAKE_TICKS_REMAINING = new HashMap<>();
	private static final Map<UUID, Boolean> LAST_ADMIN_ELIGIBILITY = new HashMap<>();
	// ponytail: 2B permits one preview per player; replace this map with a page stack when real nested flows arrive.
	private static final Map<UUID, PreviewPageInstance> PREVIEW_INSTANCES = new HashMap<>();
	private static final ConfirmationTokens CONFIRMATIONS = new ConfirmationTokens();
	private static final ControlRequestGate CONTROL_REQUEST_GATE = new ControlRequestGate();
	private static final FlowActionRequestGate FLOW_ACTION_REQUEST_GATE =
		new FlowActionRequestGate();
	private static final ResourceReportGate RESOURCE_REPORT_GATE = new ResourceReportGate();
	private static final HostFlowBossBar HOST_FLOW_BOSS_BAR = new HostFlowBossBar();
	private static boolean hostConsoleRefreshPending;
	private static final Identifier CLAIM_HOST_OPERATION = PixelTzzPro.id("host/claim");
	private static final Identifier TRANSFER_HOST_OPERATION = PixelTzzPro.id("host/transfer");
	private static final Identifier TAKEOVER_HOST_OPERATION = PixelTzzPro.id("host/takeover");
	private static final Identifier ADD_FLOW_MEMBERS_OPERATION = PixelTzzPro.id("flow/add_members");
	private static final Identifier REMOVE_FLOW_MEMBERS_OPERATION = PixelTzzPro.id("flow/remove_members");
	private static final Identifier CANCEL_FLOW_OPERATION = PixelTzzPro.id("flow/cancel");
	private static final String ADD_FLOW_MEMBERS = "add_flow_members";
	private static final String REMOVE_FLOW_MEMBERS = "remove_flow_members";
	private static final String CANCEL_FLOW = "cancel_flow";
	private static final String MEMBER_REMOVAL_REASON = "主持人通过控制台移除";
	private static final String FLOW_CANCELLATION_REASON = "主持人通过控制台取消";
	private static final String RETRY_CALLBACK_OPERATION = "retry_callback";
	private static final String RETRY_CALLBACK_PATH_PREFIX = "callback/retry/";
	private static long loadSequence;
	private static final String MOD_VERSION = FabricLoader.getInstance()
		.getModContainer(PixelTzzPro.MOD_ID)
		.map(container -> container.getMetadata().getVersion().getFriendlyString())
		.orElse("development");

	private PixelTzzServerRuntime() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(PixelTzzServerRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			clearConnections();
			CONFIRMATIONS.clear();
			HOST_FLOW_BOSS_BAR.reset();
			DefinitionRegistry.INSTANCE.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(PixelTzzServerRuntime::onServerTick);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(PixelTzzServerRuntime::onDataPackReloaded);
		ServerPlayConnectionEvents.JOIN.register(PixelTzzServerRuntime::onPlayerJoined);
		ServerPlayConnectionEvents.DISCONNECT.register(
			(listener, server) -> onPlayerDisconnected(server, listener.player)
		);
		ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.TYPE, PixelTzzServerRuntime::onHandshakeResponse);
		ServerPlayNetworking.registerGlobalReceiver(
			PreviewCatalogRequestC2SPayload.TYPE,
			PixelTzzServerRuntime::onPreviewCatalogRequest
		);
		ServerPlayNetworking.registerGlobalReceiver(
			PreviewPageRequestC2SPayload.TYPE,
			PixelTzzServerRuntime::onPreviewPageRequest
		);
		ServerPlayNetworking.registerGlobalReceiver(PageCloseC2SPayload.TYPE, PixelTzzServerRuntime::onPageClose);
		ServerPlayNetworking.registerGlobalReceiver(
			ResourceReportC2SPayload.TYPE,
			PixelTzzServerRuntime::onResourceReport
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ConsoleRequestC2SPayload.TYPE,
			PixelTzzServerRuntime::onConsoleRequest
		);
		ServerPlayNetworking.registerGlobalReceiver(
			TargetSnapshotRequestC2SPayload.TYPE,
			PixelTzzServerRuntime::onTargetSnapshotRequest
		);
		ServerPlayNetworking.registerGlobalReceiver(
			PrepareOperationC2SPayload.TYPE,
			PixelTzzServerRuntime::onPrepareOperation
		);
		ServerPlayNetworking.registerGlobalReceiver(
			CommitConfirmationC2SPayload.TYPE,
			PixelTzzServerRuntime::onCommitConfirmation
		);
		ServerPlayNetworking.registerGlobalReceiver(
			CancelConfirmationC2SPayload.TYPE,
			PixelTzzServerRuntime::onCancelConfirmation
		);
		ServerPlayNetworking.registerGlobalReceiver(
			FlowActionC2SPayload.TYPE,
			PixelTzzServerRuntime::onFlowAction
		);
		ServerPlayNetworking.registerGlobalReceiver(
			HostUiStateC2SPayload.TYPE,
			(payload, context) ->
				HOST_FLOW_BOSS_BAR.setViewerInConsole(context.player().getUUID(), payload.visible())
		);
	}

	private static void onServerStarted(final MinecraftServer server) {
		clearConnections();
		CONFIRMATIONS.clear();
		HOST_FLOW_BOSS_BAR.reset();
		loadSequence = 0L;
		ReloadOutcome definitionLoad = DefinitionRegistry.INSTANCE.reload(server.getResourceManager());
		if (!definitionLoad.healthy()) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ game definitions are not ready at server start: {}",
				definitionLoad.status().serializedName()
			);
		}
		PixelTzzWorldStateMigration.migrateOnServerStart(
			server,
			DefinitionRegistry.INSTANCE.view()
		);
		initializeNewWorldActivity(server);
		PixelTzzWorldState state = PixelTzzWorldState.get(server);
		if (state.isSchemaCompatible()) {
			PixelTzzPro.LOGGER.info(
				"Loaded Pixel TZZ world state: game={}, phase={}, stateRevision={}",
				state.activeGameId().map(Identifier::toString).orElse("none"),
				state.activePhaseId().map(Identifier::toString).orElse("none"),
				state.stateRevision()
			);
		} else {
			PixelTzzPro.LOGGER.error(
				"World state is read-only: schema={}, supportedSchema={}, reason={}",
				state.schemaVersion(),
				PixelTzzWorldState.CURRENT_SCHEMA_VERSION,
				state.incompatibilityReason()
			);
		}
		markDisconnectedMembersOffline(server);
		CallbackDispatch resumed = resumePendingCallbacks(server);
		if (!resumed.successful()) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ pending callback recovery stopped: {} ({})",
				resumed.message(),
				resumed.code().serializedName()
			);
		}
		refreshFlowProjection(server, false);
	}

	private static void onPlayerJoined(
		final ServerGamePacketListenerImpl listener,
		final net.fabricmc.fabric.api.networking.v1.PacketSender sender,
		final MinecraftServer server
	) {
		if (!ServerPlayNetworking.canSend(listener, HandshakeS2CPayload.TYPE)) {
			sender.disconnect(Component.literal("需要安装 全员逃走中-扩展 客户端模组 / Pixel TZZ Pro client mod is required."));
			return;
		}

		UUID playerId = listener.player.getUUID();
		removeConnection(server, playerId);
		HANDSHAKE_TICKS_REMAINING.put(playerId, HANDSHAKE_TIMEOUT_TICKS);
		sender.sendPacket(new HandshakeS2CPayload(NetworkProtocol.CURRENT_VERSION, MOD_VERSION));
	}

	private static void onHandshakeResponse(
		final HandshakeC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		UUID playerId = context.player().getUUID();
		if (HANDSHAKE_TICKS_REMAINING.remove(playerId) == null) {
			context.responseSender()
				.disconnect(Component.literal("全员逃走中-扩展握手状态无效，请重新连接。"));
			return;
		}

		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			context.responseSender()
				.disconnect(
					Component.literal(
						"全员逃走中-扩展协议不兼容：服务端 "
							+ NetworkProtocol.CURRENT_VERSION
							+ "，客户端 "
							+ payload.protocolVersion()
					)
				);
			return;
		}

		VERIFIED_PLAYERS.add(playerId);
		long previousRevision = PixelTzzWorldState.get(context.server()).stateRevision();
		ensurePlayerRegistered(context.server(), context.player());
		restoreMemberOnline(context.server(), context.player());
		resumePendingCallbacks(context.server());
		WorldStateV2 currentState = PixelTzzWorldState.get(context.server())
			.currentV2()
			.orElse(null);
		if (currentState != null && currentState.stateRevision() != previousRevision) {
			refreshFlowProjection(
				context.server(),
				currentState.activeForcedFlow()
					.map(ForcedFlowInstance::runtime)
					.map(ForcedFlowRuntime::status)
					.filter(FlowInstanceStatus.COMPLETED::equals)
					.isPresent()
			);
			sendSnapshots(context.server(), false);
		}
		sendSnapshot(
			context.server(),
			context.player(),
			currentState == null || !hasCurrentForcedPage(currentState, playerId)
		);
		pushHostConsoleSnapshot(context.server());
		refreshTabListDisplay(context.server());
		sendCurrentForcedPage(context.server(), context.player());
		PixelTzzPro.LOGGER.info(
			"Verified Pixel TZZ client {} (mod {}, protocol {})",
			context.player().getPlainTextName(),
			payload.modVersion(),
			payload.protocolVersion()
		);
	}

	private static void initializeNewWorldActivity(final MinecraftServer server) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		View definitions = DefinitionRegistry.INSTANCE.view();
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		if (
			!wrapper.isSchemaCompatible()
				|| state == null
				|| !canInitializeWorldActivity(state)
				|| !definitions.healthy()
				|| definitions.active().games().size() != 1
		) {
			return;
		}
		var game = definitions.active().games().values().iterator().next();
		PixelTzzWorldState.CommitResult result = wrapper.commit(
			state.stateRevision(),
			current -> current.withActivity(game.id(), game.initialPhase())
		);
		if (!result.committed()) {
			PixelTzzPro.LOGGER.warn(
				"Could not initialize the unconfigured Pixel TZZ world activity: {}",
				result.reason()
			);
		}
	}

	static boolean canInitializeWorldActivity(final WorldStateV2 state) {
		return state.activeGameId().isEmpty()
			&& state.activePhaseId().isEmpty()
			&& state.players().isEmpty()
			&& state.activeForcedFlow().isEmpty()
			&& state.completionHistory().isEmpty();
	}

	private static void onConsoleRequest(
		final ConsoleRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		if (!verified(context.player()) || !NetworkProtocol.isCompatible(payload.request().protocolVersion())) {
			return;
		}
		if (!admitControlRequest(context.server(), context.player(), payload.request().requestSequence())) {
			return;
		}
		if (!canViewConsole(context.server(), context.player())) {
			sendOperationResult(
				context.player(),
				payload.request().requestSequence(),
				OperationCode.FORBIDDEN,
				"只有当前主持人或具备管理权限的玩家可以打开控制台。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		sendConsoleSnapshot(context.server(), context.player(), payload.request().requestSequence());
	}

	private static void onTargetSnapshotRequest(
		final TargetSnapshotRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.request().protocolVersion())) {
			return;
		}
		if (!admitControlRequest(context.server(), player, payload.request().requestSequence())) {
			return;
		}
		if (
			PixelTzzWorldState.get(context.server())
				.hostId()
				.filter(player.getUUID()::equals)
				.isEmpty()
		) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.NOT_HOST,
				"只有当前主持人可以查询操作目标。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		OperationCode contextCode = validateControlRequest(
			payload.request(),
			PixelTzzWorldState.get(context.server()),
			DefinitionRegistry.INSTANCE.view()
		);
		if (contextCode != OperationCode.SUCCESS) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				contextCode,
				"控制台状态已经变化，请刷新后重试。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				true
			);
			return;
		}
		sendTargetSnapshot(
			context.server(),
			player,
			payload.request().requestSequence(),
			new OperationKey(payload.operationType(), payload.operationId())
		);
	}

	private static void onPrepareOperation(
		final PrepareOperationC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.request().protocolVersion())) {
			return;
		}
		if (!admitControlRequest(context.server(), player, payload.request().requestSequence())) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
		View definitions = DefinitionRegistry.INSTANCE.view();
		OperationCode contextCode = validateControlRequest(payload.request(), wrapper, definitions);
		if (contextCode != OperationCode.SUCCESS) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				contextCode,
				"操作上下文已经变化，请刷新控制台。",
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		PreparedOperation prepared = prepareOperation(
			context.server(),
			player,
			new OperationKey(payload.operationType(), payload.operationId()),
			payload.targetIds()
		);
		if (!prepared.successful()) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				prepared.code(),
				prepared.message(),
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		if (!ServerPlayNetworking.canSend(player, ConfirmationS2CPayload.TYPE)) {
			return;
		}
		Binding initialBinding = prepared.binding().orElseThrow();
		boolean replacing = CONFIRMATIONS.hasPending(player.getUUID());
		if (
			!persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_ISSUED,
				Optional.of(player.getUUID()),
				Optional.of(initialBinding),
				replacing
					? "issued a replacement confirmation"
					: "issued a confirmation",
				System.currentTimeMillis()
			)
		) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.INTERNAL_ERROR,
				"无法持久化确认审计，操作未开始。",
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		PreparedOperation audited = prepareOperation(
			context.server(),
			player,
			initialBinding.operation(),
			initialBinding.targetIds()
		);
		if (!audited.successful()) {
			CONFIRMATIONS.cancelOperator(player.getUUID());
			persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(player.getUUID()),
				Optional.of(initialBinding),
				"confirmation issue aborted: " + audited.code().serializedName(),
				System.currentTimeMillis()
			);
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				audited.code(),
				audited.message(),
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		IssuedConfirmation issued = CONFIRMATIONS.issue(
			player.getUUID(),
			audited.binding().orElseThrow(),
			audited.prompt().orElseThrow()
		);
		Binding binding = audited.binding().orElseThrow();
		ServerPlayNetworking.send(
			player,
			new ConfirmationS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				payload.request().requestSequence(),
				issued.tokenId(),
				binding.operation().type(),
				binding.operation().id(),
				issued.prompt().title().json(),
				issued.prompt().consequences().stream().map(RichText::json).toList(),
				audited.targets(),
				audited.contextSummary(),
				issued.validForMilliseconds(),
				binding.stateRevision(),
				binding.definitionGeneration()
			)
		);
	}

	private static void onCommitConfirmation(
		final CommitConfirmationC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.request().protocolVersion())) {
			return;
		}
		if (!admitControlRequest(context.server(), player, payload.request().requestSequence())) {
			return;
		}
		Binding pending = CONFIRMATIONS.pendingBinding(player.getUUID(), payload.tokenId()).orElse(null);
		if (pending == null) {
			Rejection unavailable = CONFIRMATIONS.rejectionIfUnavailable(
				player.getUUID(),
				payload.tokenId()
			).orElse(Rejection.MISSING);
			PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
			persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(player.getUUID()),
				Optional.empty(),
				"confirmation commit rejected: " + unavailable.code(),
				System.currentTimeMillis()
			);
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.bySerializedName(unavailable.code())
					.orElse(OperationCode.CONFIRMATION_MISSING),
				unavailable.message(),
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				true
			);
			return;
		}
		PreparedOperation current = prepareOperation(
			context.server(),
			player,
			pending.operation(),
			pending.targetIds()
		);
		Binding currentBinding = current.binding().orElse(pending);
		ConsumeResult consumed = CONFIRMATIONS.consume(
			player.getUUID(),
			payload.tokenId(),
			currentBinding
		);
		if (consumed instanceof Rejected rejected) {
			PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
			persistConfirmationAudit(
				wrapper,
				rejected.reason() == Rejection.EXPIRED
					? AuditEventType.CONFIRMATION_EXPIRED
					: AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(player.getUUID()),
				Optional.of(pending),
				"confirmation commit rejected: " + rejected.reason().code(),
				System.currentTimeMillis()
			);
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				confirmationCode(rejected),
				rejected.reason().message(),
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				true
			);
			return;
		}
		if (!current.successful() || !(consumed instanceof Accepted)) {
			PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
			persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(player.getUUID()),
				Optional.of(pending),
				"confirmed operation rejected: " + current.code().serializedName(),
				System.currentTimeMillis()
			);
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				current.code(),
				current.message(),
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				true
			);
			return;
		}
		executeConfirmedOperation(
			context.server(),
			player,
			payload.request(),
			current
		);
	}

	private static void onCancelConfirmation(
		final CancelConfirmationC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.protocolVersion())) {
			return;
		}
		if (!admitControlRequest(context.server(), player, payload.requestSequence())) {
			return;
		}
		Binding binding = CONFIRMATIONS.pendingBinding(
			player.getUUID(),
			payload.tokenId()
		).orElse(null);
		Rejection unavailable = CONFIRMATIONS.rejectionIfUnavailable(
			player.getUUID(),
			payload.tokenId()
		).orElse(Rejection.MISSING);
		boolean canceled = CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId())
			== ConfirmationTokens.CancelResult.CANCELED;
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
		persistConfirmationAudit(
			wrapper,
			canceled
				? AuditEventType.CONFIRMATION_CANCELED
				: AuditEventType.CONFIRMATION_REJECTED,
			Optional.of(player.getUUID()),
			Optional.ofNullable(binding),
			canceled
				? "confirmation canceled by operator"
				: "confirmation cancel rejected: " + unavailable.code(),
			System.currentTimeMillis()
		);
		sendOperationResult(
			player,
			payload.requestSequence(),
			canceled
				? OperationCode.SUCCESS
				: OperationCode.bySerializedName(unavailable.code())
					.orElse(OperationCode.CONFIRMATION_MISSING),
			canceled ? "已取消本次操作。" : unavailable.message(),
			wrapper.stateRevision(),
			DefinitionRegistry.INSTANCE.view().active().generation(),
			true
		);
	}

	private static void onFlowAction(
		final FlowActionC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		StatefulRequest request = payload.request();
		if (!verified(player) || !NetworkProtocol.isCompatible(request.protocolVersion())) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		if (state == null) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.SCHEMA_BLOCKED,
				"世界状态当前不可写。",
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (
			request.stateRevision() > state.stateRevision()
				|| instance == null
				|| request.flowInstanceId().filter(instance.identity().instanceId()::equals).isEmpty()
				|| request.pageInstanceId().isEmpty()
				|| request.gameId().filter(instance.identity().gameId()::equals).isEmpty()
				|| request.definitionGeneration() != instance.identity().definitionGeneration()
		) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				"流程页面已经变化，请等待服务端恢复当前页面。",
				state.stateRevision(),
				instance == null
					? DefinitionRegistry.INSTANCE.view().active().generation()
					: instance.identity().definitionGeneration(),
				false
			);
			sendCurrentForcedPage(context.server(), player);
			return;
		}
		if (
			!admitFlowActionRequest(
				context.server(),
				player,
				instance.identity().instanceId(),
				request.requestSequence()
			)
		) {
			return;
		}
		var restored = ExecutionSnapshotCompiler.restore(
			instance.identity().flowId(),
			instance.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			WorldStateV2 blocked = persistFlowBlock(
				context.server(),
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				Optional.of(player.getUUID())
			);
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				blocked.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			return;
		}
		ForcedFlowAuthority.Submission submission = new ForcedFlowAuthority.Submission(
			player.getUUID(),
			request.flowInstanceId().orElseThrow(),
			request.pageInstanceId().orElseThrow(),
			payload.flowId(),
			payload.flowVersion(),
			payload.nodeId(),
			payload.instanceRevision(),
			payload.memberRevision(),
			request.requestSequence(),
			payload.actionId(),
			payload.fieldValues()
				.stream()
				.map(
					value -> new ForcedFlowAuthority.FieldInput(
						value.fieldId(),
						value.type(),
						value.canonicalJson()
					)
				)
				.toList()
		);
		ForcedFlowAuthority.ActionResult advanced = ForcedFlowAuthority.advance(
			state,
			restored.snapshot().orElseThrow(),
			submission,
			new FrozenPredicateEvaluator(
				context.server(),
				instance.runtime().executionSnapshot()
			),
			UUID.randomUUID(),
			System.currentTimeMillis()
		);
		if (!advanced.successful()) {
			WorldStateV2 responseState = fatalFlowFailure(advanced.code())
				? persistFlowBlock(
					context.server(),
					wrapper,
					state,
					advanced.code(),
					advanced.message(),
					Optional.of(player.getUUID())
				)
				: state;
			sendOperationResult(
				player,
				request.requestSequence(),
				advanced.code(),
				advanced.message(),
				responseState.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			return;
		}
		PixelTzzWorldState.CommitResult committed = wrapper.commit(
			state.stateRevision(),
			ignored -> advanced.nextState().orElseThrow()
		);
		if (!committed.committed()) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				committed.reason(),
				wrapper.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			sendCurrentForcedPage(context.server(), player);
			return;
		}
		WorldStateV2 next = committed.state().orElseThrow();
		if (advanced.flowCompleted()) {
			FlowDefinition completedFlow = restored.snapshot()
				.orElseThrow()
				.flows()
				.get(instance.identity().flowId());
			if (completedFlow != null) {
				showFlowCompletion(
					context.server(),
					next,
					instance.identity().instanceId(),
					completedFlow
				);
			}
		}
		CallbackDispatch callback = CallbackDispatch.succeeded("");
		if (advanced.memberCompleted()) {
			callback = executePreparedCallback(
				context.server(),
				FlowCallbackAuthority.preparePlayerComplete(
					next,
					player.getUUID(),
					System.currentTimeMillis()
				)
			);
			if (callback.successful()) {
				callback = resumePendingCallbacks(context.server());
			}
		}
		WorldStateV2 finalState = wrapper.currentV2().orElse(next);
		sendOperationResult(
			player,
			request.requestSequence(),
			callback.code(),
			callback.message(),
			finalState.stateRevision(),
			instance.identity().definitionGeneration(),
			false
		);
		if (advanced.memberCompleted()) {
			if (ServerPlayNetworking.canSend(player, ForcedPageReleaseS2CPayload.TYPE)) {
				ServerPlayNetworking.send(
					player,
					new ForcedPageReleaseS2CPayload(
						NetworkProtocol.CURRENT_VERSION,
						instance.identity().instanceId(),
						request.pageInstanceId().orElseThrow(),
						advanced.flowCompleted() ? "flow_completed" : "member_completed",
						finalState.stateRevision()
					)
				);
			}
		} else {
			sendCurrentForcedPage(context.server(), player);
		}
		if (!advanced.flowCompleted()) {
			refreshFlowProjection(context.server(), false);
		} else {
			pushHostConsoleSnapshot(context.server());
		}
		sendSnapshots(context.server(), false);
	}

	private static OperationCode validateControlRequest(
		final StatefulRequest request,
		final PixelTzzWorldState state,
		final View definitions
	) {
		if (!NetworkProtocol.isCompatible(request.protocolVersion())) {
			return OperationCode.UNSUPPORTED_OPERATION;
		}
		if (!state.isSchemaCompatible()) {
			return OperationCode.SCHEMA_BLOCKED;
		}
		if (request.stateRevision() != state.stateRevision()) {
			return OperationCode.STATE_REVISION_STALE;
		}
		if (request.definitionGeneration() != definitions.active().generation()) {
			return OperationCode.DEFINITION_GENERATION_STALE;
		}
		if (!request.gameId().equals(state.activeGameId())) {
			return OperationCode.PHASE_MISMATCH;
		}
		if (
			request.pageInstanceId().isPresent()
				|| !controlFlowContextMatches(request.flowInstanceId(), state)
		) {
			return OperationCode.FLOW_INSTANCE_MISMATCH;
		}
		return OperationCode.SUCCESS;
	}

	private static boolean controlFlowContextMatches(
		final Optional<UUID> requestedInstanceId,
		final PixelTzzWorldState state
	) {
		if (requestedInstanceId.isEmpty()) {
			return true;
		}
		return state.currentV2()
			.flatMap(WorldStateV2::activeForcedFlow)
			.map(ForcedFlowInstance::identity)
			.map(WorldStateV2.ForcedFlowIdentity::instanceId)
			.filter(requestedInstanceId.orElseThrow()::equals)
			.isPresent();
	}

	private static PreparedOperation prepareOperation(
		final MinecraftServer server,
		final ServerPlayer actor,
		final OperationKey operation,
		final List<UUID> requestedTargets
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		View view = DefinitionRegistry.INSTANCE.view();
		if (state == null) {
			return PreparedOperation.failed(OperationCode.SCHEMA_BLOCKED, "世界状态当前不可写。");
		}
		Optional<CallbackRetryKey> callbackRetry = callbackRetryKey(operation);
		if (
			callbackRetry.isEmpty()
				&& !hostOperation(operation)
				&& !flowRosterOperation(operation)
				&& (!view.healthy() || !view.active().usable())
		) {
			return PreparedOperation.failed(
				OperationCode.DEFINITION_UNAVAILABLE,
				"当前数据包定义不可用。"
			);
		}
		List<UUID> targets = requestedTargets.stream().sorted().toList();
		Prompt prompt;
		List<Target> targetLabels;
		Optional<PanelActionDefinition> panelAction = Optional.empty();
		if (
			operation.type().equals("claim_host")
				&& operation.id().equals(CLAIM_HOST_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"认领主持人不接受目标玩家。"
				);
			}
			HostAuthority.Result result = HostAuthority.claim(
				state,
				actor(actor),
				System.currentTimeMillis()
			);
			if (!result.successful()) {
				return PreparedOperation.failed(result.code(), hostFailureMessage(result.code()));
			}
			prompt = new Prompt(
				richText("确认认领主持人"),
				List.of(
					richText("你将成为该世界唯一的全员逃走中主持人。"),
					richText("你将获得模组控制台的高风险操作权限，但不会改变原版 OP 权限。")
				)
			);
			targetLabels = List.of();
		} else if (
			operation.type().equals("transfer_host")
				&& operation.id().equals(TRANSFER_HOST_OPERATION)
		) {
			if (targets.size() != 1) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"转交主持人必须选择一名在线玩家。"
				);
			}
			ServerPlayer target = server.getPlayerList().getPlayer(targets.getFirst());
			if (target == null || !verified(target)) {
				return PreparedOperation.failed(OperationCode.TARGET_OFFLINE, "目标玩家当前不在线。");
			}
			HostAuthority.Result result = HostAuthority.transfer(
				state,
				actor(actor),
				new OnlineTarget(target.getUUID(), target.getPlainTextName(), true),
				System.currentTimeMillis()
			);
			if (!result.successful()) {
				return PreparedOperation.failed(result.code(), hostFailureMessage(result.code()));
			}
			prompt = new Prompt(
				richText("确认转交主持人"),
				List.of(
					richText("主持人权限将立即转交给 " + target.getPlainTextName() + "。"),
					richText("你将立即失去模组主持人权限；目标不会自动获得原版 OP。")
				)
			);
			targetLabels = List.of(
				confirmationTarget(
					server,
					state,
					view.active(),
					target.getUUID(),
					target.getPlainTextName(),
					Set.of()
				)
			);
		} else if (
			operation.type().equals("takeover_host")
				&& operation.id().equals(TAKEOVER_HOST_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"接管主持人不接受目标玩家。"
				);
			}
			HostAuthority.Result result = HostAuthority.takeover(
				state,
				actor(actor),
				System.currentTimeMillis()
			);
			if (!result.successful()) {
				return PreparedOperation.failed(result.code(), hostFailureMessage(result.code()));
			}
			String previous = state.host()
				.flatMap(WorldStateV2.HostRecord::lastKnownName)
				.orElse("原主持人");
			prompt = new Prompt(
				richText("确认接管主持人"),
				List.of(
					richText("你将立即替换 " + previous + " 成为唯一主持人。"),
					richText("本次接管会进入世界审计；原主持人只失去模组权限。")
				)
			);
			targetLabels = List.of();
		} else if (addFlowMembersOperation(operation)) {
			ActiveFlowDefinitions active = activeFlowDefinitions(state).orElse(null);
			if (active == null) {
				return PreparedOperation.failed(
					OperationCode.SNAPSHOT_INVALID,
					"当前流程的冻结定义不可恢复，不能补充成员。"
				);
			}
			List<ConnectedPlayer> selected = connectedTargets(server, targets);
			if (selected.size() != targets.size()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_OFFLINE,
					"补充成员必须全部在线并完成客户端握手。"
				);
			}
			for (ConnectedPlayer candidate : selected) {
				var eligibility = ForcedFlowAuthority.resolveEligibleCandidate(
					state,
					active.definitions(),
					active.sourceAction(),
					active.instance().identity().initiatedBy(),
					candidate,
					System.currentTimeMillis()
				);
				if (!eligibility.successful()) {
					return PreparedOperation.failed(eligibility.code(), eligibility.message());
				}
			}
			FlowRosterAuthority.Result result = FlowRosterAuthority.addMembers(
				state,
				actor.getUUID(),
				active.instance().identity().instanceId(),
				active.instance().runtime().instanceRevision(),
				selected.stream()
					.map(player -> new OnlineMember(player.playerId(), player.name(), true))
					.toList(),
				UUID::randomUUID,
				System.currentTimeMillis()
			);
			if (!result.successful()) {
				return PreparedOperation.failed(result.code(), result.message());
			}
			prompt = new Prompt(
				richText("确认补充流程成员"),
				List.of(
					richText("将 " + selected.size() + " 名玩家加入当前强制流程。"),
					richText("新成员从冻结入口开始；符合跳过条件者会直接记为已完成。"),
					richText("流程总人数和主持人进度条将立即更新。")
				)
			);
			targetLabels = selected.stream()
				.map(player -> confirmationTarget(
					server,
					state,
					active.definitions(),
					player.playerId(),
					player.name(),
					presentationStatusFlowIds(active.sourceAction())
				))
				.toList();
		} else if (removeFlowMembersOperation(operation)) {
			ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
			if (instance == null) {
				return PreparedOperation.failed(
					OperationCode.FLOW_NOT_ACTIVE,
					"当前没有可以调整的强制流程。"
				);
			}
			FlowRosterAuthority.Result result = FlowRosterAuthority.removeMembers(
				state,
				actor.getUUID(),
				instance.identity().instanceId(),
				instance.runtime().instanceRevision(),
				targets,
				MEMBER_REMOVAL_REASON,
				System.currentTimeMillis()
			);
			if (!result.successful()) {
				return PreparedOperation.failed(result.code(), result.message());
			}
			targetLabels = targets.stream()
				.map(instance.runtime().members()::get)
				.filter(Objects::nonNull)
				.map(
					member -> confirmationTarget(
						server,
						state,
						view.active(),
						member.playerId(),
						member.lockedName(),
						Set.of(instance.identity().flowId())
					)
				)
				.toList();
			prompt = new Prompt(
				richText("确认移除流程成员"),
				List.of(
					richText("将 " + targets.size() + " 名玩家移出当前强制流程。"),
					richText("移除不会生成完成记录，并会取消这些玩家尚未提交的身份变更。"),
					richText("有效总人数会立即减少；若剩余成员均已完成，流程将随即完成。")
				)
			);
		} else if (cancelFlowOperation(operation)) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"取消流程不接受目标玩家。"
				);
			}
			ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
			if (instance == null) {
				return PreparedOperation.failed(
					OperationCode.FLOW_NOT_ACTIVE,
					"当前没有可以取消的强制流程。"
				);
			}
			FlowRosterAuthority.Result result = FlowRosterAuthority.cancelFlow(
				state,
				actor.getUUID(),
				instance.identity().instanceId(),
				instance.runtime().instanceRevision(),
				FLOW_CANCELLATION_REASON,
				System.currentTimeMillis()
			);
			if (!result.successful()) {
				return PreparedOperation.failed(result.code(), result.message());
			}
			prompt = new Prompt(
				richText("确认取消当前流程"),
				List.of(
					richText("所有未完成玩家的强制页面会立即关闭。"),
					richText("尚未提交的身份变更将取消；已经完成的记录不会回滚。"),
					richText("已经成功执行的数据包回调不会撤销，取消后可发起下一流程。")
				)
			);
			targetLabels = List.of();
		} else if (callbackRetry.isPresent()) {
			CallbackRetryKey retry = callbackRetry.orElseThrow();
			List<UUID> expectedTargets = retry.playerId().stream().toList();
			if (!targets.isEmpty() && !targets.equals(expectedTargets)) {
				return PreparedOperation.failed(
					OperationCode.TARGET_INVALID,
					"回调重试目标与失败记录不一致。"
				);
			}
			if (
				retry.playerId()
					.map(server.getPlayerList()::getPlayer)
					.filter(Objects::nonNull)
					.isEmpty()
					&& retry.playerId().isPresent()
			) {
				return PreparedOperation.failed(
					OperationCode.TARGET_OFFLINE,
					"玩家回调只能在对应玩家在线时重试。"
				);
			}
			PrepareResult result = FlowCallbackAuthority.prepareRetry(
				state,
				actor.getUUID(),
				retry.kind(),
				retry.playerId(),
				System.currentTimeMillis()
			);
			if (!result.successful() || !result.prepared()) {
				return PreparedOperation.failed(result.code(), result.message());
			}
			CallbackFailure failure = callbackFailure(state, retry).orElse(null);
			if (failure == null) {
				return PreparedOperation.failed(
					OperationCode.ACTION_UNAVAILABLE,
					"对应的回调失败记录已经不存在。"
				);
			}
			targets = expectedTargets;
			String targetName = retry.playerId()
				.map(id -> callbackPlayerName(server, state, id))
				.orElse("实例级回调");
			prompt = new Prompt(
				richText("确认重试流程回调"),
				List.of(
					richText("将重新执行数据包函数 " + failure.functionId() + "。"),
					richText("回调目标：" + targetName + "；当前尝试次数：" + failure.attemptCount() + "。"),
					richText("核心流程、完成记录和身份变更不会回滚或重复应用。"),
					richText("上次失败：" + truncate(failure.error(), 256))
				)
			);
			targetLabels = retry.playerId()
				.map(
					id -> List.of(
						confirmationTarget(
							server,
							state,
							view.active(),
							id,
							callbackPlayerName(server, state, id),
							Set.of()
						)
					)
				)
				.orElseGet(List::of);
		} else {
			PanelActionDefinition action = view.active().panelActions().get(operation.id());
			if (
				action == null
					|| !operationType(action).equals(operation.type())
					|| state.host().map(host -> host.playerId().equals(actor.getUUID())).orElse(false)
						== false
					|| state.activeGameId().filter(action.game()::equals).isEmpty()
					|| (
						!action.phases().isEmpty()
							&& state.activePhaseId().filter(action.phases()::contains).isEmpty()
					)
			) {
				return PreparedOperation.failed(
					OperationCode.ACTION_UNAVAILABLE,
					"该数据包操作当前不可用。"
				);
			}
			if (!supportedPanelAction(action)) {
				return PreparedOperation.failed(
					OperationCode.UNSUPPORTED_OPERATION,
					"该操作尚未接入当前服务端执行切片。"
				);
			}
			PanelActionAvailability availability = panelActionAvailability(
				server,
				state,
				view.active(),
				actor,
				action
			);
			if (!availability.visible() || !availability.enabled()) {
				return PreparedOperation.failed(
					OperationCode.ACTION_UNAVAILABLE,
					availability.message()
				);
			}
			List<UUID> explicit = action.target().mode() == SelectionMode.NONE
				? List.of()
				: targets;
			boolean immediateRole = immediateRoleAction(action);
			var resolved = immediateRole
				? resolveImmediateTargets(
					state,
					view.active(),
					action,
					actor.getUUID(),
					connectedPlayers(server),
					explicit,
					System.currentTimeMillis()
				)
				: ForcedFlowAuthority.resolveTargets(
					state,
					view.active(),
					action,
					actor.getUUID(),
					connectedPlayers(server),
					explicit,
					System.currentTimeMillis()
				);
			if (!resolved.successful() || resolved.targets().isEmpty()) {
				boolean noEligibleTargets = resolved.code() == OperationCode.NO_ELIGIBLE_TARGETS
					|| resolved.successful() && resolved.targets().isEmpty();
				return PreparedOperation.failed(
					noEligibleTargets ? OperationCode.NO_ELIGIBLE_TARGETS : resolved.code(),
					noEligibleTargets ? "当前没有符合条件的目标玩家。" : resolved.message()
				);
			}
			targets = resolved.targets().stream().map(ConnectedPlayer::playerId).toList();
			if (immediateRole) {
				AssignRoleOperation assign = (AssignRoleOperation) action.operation();
				FlowRosterAuthority.Result roleResult = FlowRosterAuthority.assignRoleImmediate(
					state,
					view.active(),
					actor.getUUID(),
					state.stateRevision(),
					assign.role(),
					targets,
					System.currentTimeMillis()
				);
				if (!roleResult.successful()) {
					return PreparedOperation.failed(roleResult.code(), roleResult.message());
				}
			}
			targetLabels = resolved.targets()
				.stream()
				.map(
					target -> confirmationTarget(
						server,
						state,
						view.active(),
						target.playerId(),
						target.name(),
						statusFlowIds(action)
					)
				)
				.toList();
			var confirmation = action.confirmation().orElse(null);
			prompt = confirmation == null
				? new Prompt(
					action.label(),
					List.of(action.description())
				)
				: new Prompt(confirmation.title(), confirmation.consequences());
			panelAction = Optional.of(action);
		}
		Binding binding = new Binding(
			operation,
			targets,
			state.activeGameId(),
			state.activePhaseId(),
			view.active().generation(),
			state.stateRevision(),
			relatedStateDigest(server, state, operation, targets)
		);
		String gameName = state.activeGameId()
			.map(view.active().games()::get)
			.map(game -> game.name().plainText())
			.orElse("未配置游戏");
		String phaseName = state.activePhaseId()
			.map(view.active().phases()::get)
			.map(phase -> phase.name().plainText())
			.orElse("未配置阶段");
		String context = gameName + "  ·  " + phaseName + "  ·  "
			+ (targets.isEmpty() ? "无需选择玩家" : targets.size() + " 名目标玩家");
		return PreparedOperation.succeeded(
			binding,
			prompt,
			targetLabels,
			context,
			panelAction
		);
	}

	private static void executeConfirmedOperation(
		final MinecraftServer server,
		final ServerPlayer actor,
		final StatefulRequest request,
		final PreparedOperation prepared
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		View definitions = DefinitionRegistry.INSTANCE.view();
		Binding binding = prepared.binding().orElseThrow();
		if (
			request.stateRevision() != wrapper.stateRevision()
				|| request.definitionGeneration() != definitions.active().generation()
				|| !request.gameId().equals(wrapper.activeGameId())
				|| request.pageInstanceId().isPresent()
				|| !controlFlowContextMatches(request.flowInstanceId(), wrapper)
		) {
			persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(actor.getUUID()),
				Optional.of(binding),
				"confirmed operation context changed before execution",
				System.currentTimeMillis()
			);
			sendOperationResult(
				actor,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				"确认后的状态已经变化，请重新操作。",
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		if (
			!persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_CONSUMED,
				Optional.of(actor.getUUID()),
				Optional.of(binding),
				"confirmation accepted for execution",
				System.currentTimeMillis()
			)
		) {
			sendOperationResult(
				actor,
				request.requestSequence(),
				OperationCode.INTERNAL_ERROR,
				"无法持久化确认消费记录，操作未执行。",
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		WorldStateV2 state = wrapper.currentV2().orElseThrow();
		Optional<CallbackRetryKey> callbackRetry = callbackRetryKey(binding.operation());
		if (callbackRetry.isPresent()) {
			CallbackRetryKey retry = callbackRetry.orElseThrow();
			PrepareResult retryPreparation = FlowCallbackAuthority.prepareRetry(
				state,
				actor.getUUID(),
				retry.kind(),
				retry.playerId(),
				System.currentTimeMillis()
			);
			CallbackDispatch callback = executePreparedCallback(server, retryPreparation);
			if (callback.successful()) {
				callback = resumePendingCallbacks(server);
			}
			WorldStateV2 finalState = wrapper.currentV2().orElse(state);
			sendOperationResult(
				actor,
				request.requestSequence(),
				callback.code(),
				callback.message(),
				finalState.stateRevision(),
				definitions.active().generation(),
				true
			);
			sendSnapshots(server, false);
			sendForcedPages(server);
			refreshFlowProjection(
				server,
				finalState.activeForcedFlow()
					.map(ForcedFlowInstance::runtime)
					.map(ForcedFlowRuntime::status)
					.filter(FlowInstanceStatus.COMPLETED::equals)
					.isPresent()
			);
			return;
		}
		OperationCode code;
		String message;
		Optional<WorldStateV2> candidate;
		boolean startedFlow = false;
		boolean rosterChanged = false;
		boolean rosterCompletedFlow = false;
		boolean canceledFlow = false;
		boolean immediateRoleChanged = false;
		ActiveFlowDefinitions activeBeforeCommit = activeFlowDefinitions(state).orElse(null);
		if (
			binding.operation().type().equals("claim_host")
				&& binding.operation().id().equals(CLAIM_HOST_OPERATION)
		) {
			HostAuthority.Result result = HostAuthority.claim(
				state,
				actor(actor),
				System.currentTimeMillis()
			);
			code = result.code();
			message = hostFailureMessage(code);
			candidate = result.nextState();
		} else if (
			binding.operation().type().equals("transfer_host")
				&& binding.operation().id().equals(TRANSFER_HOST_OPERATION)
		) {
			ServerPlayer target = binding.targetIds().size() == 1
				? server.getPlayerList().getPlayer(binding.targetIds().getFirst())
				: null;
			if (target == null) {
				code = OperationCode.TARGET_OFFLINE;
				message = "目标玩家已经离线。";
				candidate = Optional.empty();
			} else {
				HostAuthority.Result result = HostAuthority.transfer(
					state,
					actor(actor),
					new OnlineTarget(target.getUUID(), target.getPlainTextName(), true),
					System.currentTimeMillis()
				);
				code = result.code();
				message = hostFailureMessage(code);
				candidate = result.nextState();
			}
		} else if (
			binding.operation().type().equals("takeover_host")
				&& binding.operation().id().equals(TAKEOVER_HOST_OPERATION)
		) {
			HostAuthority.Result result = HostAuthority.takeover(
				state,
				actor(actor),
				System.currentTimeMillis()
			);
			code = result.code();
			message = hostFailureMessage(code);
			candidate = result.nextState();
		} else if (addFlowMembersOperation(binding.operation())) {
			ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
			List<ConnectedPlayer> selected = connectedTargets(server, binding.targetIds());
			if (instance == null || selected.size() != binding.targetIds().size()) {
				code = instance == null
					? OperationCode.FLOW_NOT_ACTIVE
					: OperationCode.TARGET_OFFLINE;
				message = instance == null
					? "当前没有可以补充成员的流程。"
					: "至少一名补充目标已经离线。";
				candidate = Optional.empty();
			} else {
				FlowRosterAuthority.Result result = FlowRosterAuthority.addMembers(
					state,
					actor.getUUID(),
					instance.identity().instanceId(),
					instance.runtime().instanceRevision(),
					selected.stream()
						.map(player -> new OnlineMember(player.playerId(), player.name(), true))
						.toList(),
					UUID::randomUUID,
					System.currentTimeMillis()
				);
				code = result.code();
				message = result.message();
				candidate = result.nextState();
				rosterChanged = result.successful();
			}
		} else if (removeFlowMembersOperation(binding.operation())) {
			ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
			if (instance == null) {
				code = OperationCode.FLOW_NOT_ACTIVE;
				message = "当前没有可以移除成员的流程。";
				candidate = Optional.empty();
			} else {
				FlowRosterAuthority.Result result = FlowRosterAuthority.removeMembers(
					state,
					actor.getUUID(),
					instance.identity().instanceId(),
					instance.runtime().instanceRevision(),
					binding.targetIds(),
					MEMBER_REMOVAL_REASON,
					System.currentTimeMillis()
				);
				code = result.code();
				message = result.message();
				candidate = result.nextState();
				rosterChanged = result.successful();
				rosterCompletedFlow = result.becameCompleted();
				canceledFlow = result.nextState()
					.flatMap(WorldStateV2::activeForcedFlow)
					.map(ForcedFlowInstance::runtime)
					.map(ForcedFlowRuntime::status)
					.filter(FlowInstanceStatus.CANCELED::equals)
					.isPresent();
			}
		} else if (cancelFlowOperation(binding.operation())) {
			ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
			if (instance == null) {
				code = OperationCode.FLOW_NOT_ACTIVE;
				message = "当前没有可以取消的流程。";
				candidate = Optional.empty();
			} else {
				FlowRosterAuthority.Result result = FlowRosterAuthority.cancelFlow(
					state,
					actor.getUUID(),
					instance.identity().instanceId(),
					instance.runtime().instanceRevision(),
					FLOW_CANCELLATION_REASON,
					System.currentTimeMillis()
				);
				code = result.code();
				message = result.message();
				candidate = result.nextState();
				rosterChanged = result.successful();
				canceledFlow = result.successful();
			}
		} else {
			PanelActionDefinition action = prepared.panelAction().orElse(null);
			if (action == null) {
				code = OperationCode.ACTION_UNAVAILABLE;
				message = "数据包操作已经不可用。";
				candidate = Optional.empty();
			} else {
				PanelActionAvailability availability = panelActionAvailability(
					server,
					state,
					definitions.active(),
					actor,
					action
				);
				if (!availability.visible() || !availability.enabled()) {
					code = OperationCode.ACTION_UNAVAILABLE;
					message = availability.message();
					candidate = Optional.empty();
				} else if (immediateRoleAction(action)) {
					AssignRoleOperation assign = (AssignRoleOperation) action.operation();
					FlowRosterAuthority.Result result = FlowRosterAuthority.assignRoleImmediate(
						state,
						definitions.active(),
						actor.getUUID(),
						state.stateRevision(),
						assign.role(),
						binding.targetIds(),
						System.currentTimeMillis()
					);
					code = result.code();
					message = result.message();
					candidate = result.nextState();
					immediateRoleChanged = result.successful();
				} else {
					List<UUID> explicit = action.target().mode() == SelectionMode.NONE
						? List.of()
						: binding.targetIds();
					StartResult result = ForcedFlowAuthority.start(
						state,
						definitions.active(),
						action,
						actor.getUUID(),
						connectedPlayers(server),
						explicit,
						UUID.randomUUID(),
						UUID::randomUUID,
						System.currentTimeMillis(),
						new PanelActionAuthorization(
							action.id(),
							definitions.active().generation(),
							state.stateRevision(),
							actor.getUUID(),
							availability.visible(),
							availability.enabled()
						)
					);
					code = result.code();
					message = result.message();
					candidate = result.nextState();
					startedFlow = result.successful() && !result.completedImmediately();
					if (result.successful() && result.completedImmediately()) {
						sendOperationResult(
							actor,
							request.requestSequence(),
							OperationCode.SUCCESS,
							result.message(),
							state.stateRevision(),
							definitions.active().generation(),
							true
						);
						return;
					}
				}
			}
		}
		if (code != OperationCode.SUCCESS || candidate.isEmpty()) {
			persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(actor.getUUID()),
				Optional.of(binding),
				"confirmed operation failed: " + code.serializedName(),
				System.currentTimeMillis()
			);
			sendOperationResult(
				actor,
				request.requestSequence(),
				code,
				message,
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		PixelTzzWorldState.CommitResult committed = wrapper.commit(
			state.stateRevision(),
			ignored -> candidate.orElseThrow()
		);
		if (!committed.committed()) {
			persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_REJECTED,
				Optional.of(actor.getUUID()),
				Optional.of(binding),
				"confirmed operation commit failed: " + committed.reason(),
				System.currentTimeMillis()
			);
			sendOperationResult(
				actor,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				committed.reason(),
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			return;
		}
		WorldStateV2 next = committed.state().orElseThrow();
		if (rosterCompletedFlow && activeBeforeCommit != null) {
			showFlowCompletion(
				server,
				next,
				activeBeforeCommit.instance().identity().instanceId(),
				activeBeforeCommit.flow()
			);
		}
		CallbackDispatch callback = startedFlow
			? executePreparedCallback(
				server,
				FlowCallbackAuthority.prepareOnStart(next, System.currentTimeMillis())
			)
			: CallbackDispatch.succeeded("");
		if (callback.successful() && rosterCompletedFlow) {
			callback = resumePendingCallbacks(server);
		}
		WorldStateV2 finalState = wrapper.currentV2().orElse(next);
		sendOperationResult(
			actor,
			request.requestSequence(),
			callback.code(),
			callback.message().isBlank()
				? operationSuccessMessage(binding.operation(), immediateRoleChanged)
				: callback.message(),
			finalState.stateRevision(),
			definitions.active().generation(),
			true
		);
		if (removeFlowMembersOperation(binding.operation())) {
			releaseForcedPages(
				server,
				state,
				Set.copyOf(binding.targetIds()),
				"member_removed"
			);
		} else if (cancelFlowOperation(binding.operation())) {
			releaseForcedPages(
				server,
				state,
				state.activeForcedFlow()
					.map(ForcedFlowInstance::runtime)
					.map(ForcedFlowRuntime::members)
					.map(Map::keySet)
					.map(Set::copyOf)
					.orElseGet(Set::of),
				"flow_canceled"
			);
		}
		sendSnapshots(server, false);
		pushHostConsoleSnapshot(server);
		if (startedFlow || rosterChanged) {
			sendForcedPages(server);
			if (canceledFlow) {
				HOST_FLOW_BOSS_BAR.clear();
			} else if (!rosterCompletedFlow) {
				refreshFlowProjection(server, false);
			}
		}
	}

	private static Actor actor(final ServerPlayer player) {
		return new Actor(
			player.getUUID(),
			player.getPlainTextName(),
			isAdminEligible(player)
		);
	}

	private static List<ConnectedPlayer> connectedPlayers(final MinecraftServer server) {
		return server.getPlayerList()
			.getPlayers()
			.stream()
			.map(
				player -> new ConnectedPlayer(
					player.getUUID(),
					player.getPlainTextName(),
					true,
					VERIFIED_PLAYERS.contains(player.getUUID())
				)
			)
			.sorted(Comparator.comparing(ConnectedPlayer::playerId))
			.toList();
	}

	private static List<ConnectedPlayer> connectedTargets(
		final MinecraftServer server,
		final List<UUID> targetIds
	) {
		Map<UUID, ConnectedPlayer> connected = connectedPlayers(server)
			.stream()
			.collect(java.util.stream.Collectors.toMap(ConnectedPlayer::playerId, player -> player));
		return targetIds.stream()
			.map(connected::get)
			.filter(Objects::nonNull)
			.filter(ConnectedPlayer::clientReady)
			.toList();
	}

	private static ForcedFlowAuthority.TargetResult resolveImmediateTargets(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final PanelActionDefinition action,
		final UUID initiatedBy,
		final List<ConnectedPlayer> connectedPlayers,
		final List<UUID> explicitTargetIds,
		final long occurredAtEpochMillis
	) {
		List<ConnectedPlayer> candidates;
		if (action.target().mode() == SelectionMode.NONE) {
			if (!explicitTargetIds.isEmpty()) {
				return new ForcedFlowAuthority.TargetResult(
					OperationCode.TARGET_COUNT_INVALID,
					"该操作不接受手动目标。",
					List.of()
				);
			}
			candidates = connectedPlayers.stream()
				.filter(ConnectedPlayer::online)
				.filter(ConnectedPlayer::clientReady)
				.toList();
		} else {
			List<UUID> sorted = explicitTargetIds.stream().sorted().toList();
			if (
				sorted.size() < action.target().minimum()
					|| sorted.size() > action.target().maximum()
					|| sorted.stream().distinct().count() != sorted.size()
			) {
				return new ForcedFlowAuthority.TargetResult(
					OperationCode.TARGET_COUNT_INVALID,
					"选择人数不符合操作限制。",
					List.of()
				);
			}
			Map<UUID, ConnectedPlayer> connected = connectedPlayers.stream()
				.collect(java.util.stream.Collectors.toMap(ConnectedPlayer::playerId, player -> player));
			candidates = sorted.stream().map(connected::get).filter(Objects::nonNull).toList();
			if (candidates.size() != sorted.size()) {
				return new ForcedFlowAuthority.TargetResult(
					OperationCode.TARGET_OFFLINE,
					"所有目标都必须在线并完成客户端握手。",
					List.of()
				);
			}
		}
		List<ConnectedPlayer> eligible = new ArrayList<>();
		for (ConnectedPlayer candidate : candidates) {
			var result = ForcedFlowAuthority.resolveEligibleCandidate(
				state,
				definitions,
				action,
				initiatedBy,
				candidate,
				occurredAtEpochMillis
			);
			if (result.successful()) {
				eligible.add(candidate);
			} else if (action.target().mode() != SelectionMode.NONE) {
				return result;
			}
		}
		if (eligible.size() > WorldStateV2.MAX_INSTANCE_MEMBERS) {
			return new ForcedFlowAuthority.TargetResult(
				OperationCode.TARGET_COUNT_INVALID,
				"符合条件的玩家超过流程成员上限。",
				List.of()
			);
		}
		eligible.sort(Comparator.comparing(ConnectedPlayer::playerId));
		return new ForcedFlowAuthority.TargetResult(OperationCode.SUCCESS, "", eligible);
	}

	private static PanelActionAvailability panelCandidateAvailability(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final ServerPlayer actor,
		final PanelActionDefinition action
	) {
		List<ConnectedPlayer> connected = connectedPlayers(server);
		long now = System.currentTimeMillis();
		if (action.target().mode() == SelectionMode.NONE) {
			var resolved = immediateRoleAction(action)
				? resolveImmediateTargets(
					state,
					definitions,
					action,
					actor.getUUID(),
					connected,
					List.of(),
					now
				)
				: ForcedFlowAuthority.resolveTargets(
					state,
					definitions,
					action,
					actor.getUUID(),
					connected,
					List.of(),
					now
				);
			boolean noEligibleTargets = resolved.code() == OperationCode.NO_ELIGIBLE_TARGETS
				|| resolved.successful() && resolved.targets().isEmpty();
			return resolved.successful() && !resolved.targets().isEmpty()
				? PanelActionAvailability.available()
				: PanelActionAvailability.disabled(
					noEligibleTargets || resolved.message().isBlank()
						? "当前没有符合此操作条件的玩家。"
						: resolved.message()
				);
		}
		long eligible = eligibleActionTargets(
			server,
			state,
			definitions,
			actor,
			new OperationKey(operationType(action), action.id()),
			action
		).size();
		return eligible >= action.target().minimum()
			? PanelActionAvailability.available()
			: PanelActionAvailability.disabled("当前没有符合此操作条件的玩家。");
	}

	private static List<TargetSnapshotS2CPayload.Target> eligibleActionTargets(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final ServerPlayer actor,
		final OperationKey operation,
		final PanelActionDefinition action
	) {
		return connectedPlayers(server)
			.stream()
			.filter(ConnectedPlayer::clientReady)
			.limit(TargetSnapshotS2CPayload.MAX_TARGETS)
			.map(connected -> targetEntry(
				server,
				state,
				definitions,
				actor,
				operation,
				action,
				connected
			))
			.filter(TargetSnapshotS2CPayload.Target::eligible)
			.toList();
	}

	private static Optional<ActiveFlowDefinitions> activeFlowDefinitions(
		final WorldStateV2 state
	) {
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (instance == null) {
			return Optional.empty();
		}
		var restored = ExecutionSnapshotCompiler.restore(
			instance.identity().flowId(),
			instance.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			return Optional.empty();
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition flow = definitions.flows().get(instance.identity().flowId());
		PanelActionDefinition action = definitions.panelActions()
			.get(instance.identity().sourceActionId());
		return flow == null || action == null
			? Optional.empty()
			: Optional.of(new ActiveFlowDefinitions(instance, definitions, flow, action));
	}

	private static boolean supportedPanelAction(final PanelActionDefinition action) {
		return action.operation() instanceof StartFlowOperation
			|| (
				action.operation() instanceof AssignRoleOperation assign
					&& (
						assign.apply() == ApplyTiming.IMMEDIATE
							|| assign.flow().isPresent()
					)
			);
	}

	private static boolean immediateRoleAction(final PanelActionDefinition action) {
		return action.operation() instanceof AssignRoleOperation assign
			&& assign.apply() == ApplyTiming.IMMEDIATE;
	}

	private static PanelActionAvailability panelActionAvailability(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final ServerPlayer actor,
		final PanelActionDefinition action
	) {
		return panelActionAvailability(
			new FrozenPredicateEvaluator(server, definitions.predicateDocuments()),
			panelPredicateContext(state, actor),
			action
		);
	}

	private static PanelActionAvailability panelActionAvailability(
		final FrozenPredicateEvaluator evaluator,
		final PredicateContext context,
		final PanelActionDefinition action
	) {
		try {
			if (
				action.visibleWhen().isPresent()
					&& !evaluator.evaluate(action.visibleWhen().orElseThrow(), context)
			) {
				return PanelActionAvailability.hidden();
			}
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"Could not evaluate visible_when for panel action {}",
				action.id(),
				error
			);
			return PanelActionAvailability.hidden();
		}
		try {
			if (
				action.enabledWhen().isPresent()
					&& !evaluator.evaluate(action.enabledWhen().orElseThrow(), context)
			) {
				return PanelActionAvailability.disabled(
					action.disabledReason()
						.map(RichText::plainText)
						.orElse("当前条件不允许执行该操作。")
				);
			}
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"Could not evaluate enabled_when for panel action {}",
				action.id(),
				error
			);
			return PanelActionAvailability.disabled("操作条件检查失败，请查看服务端日志。");
		}
		return PanelActionAvailability.available();
	}

	private static PredicateContext panelPredicateContext(
		final WorldStateV2 state,
		final ServerPlayer actor
	) {
		ForcedFlowInstance flow = state.activeForcedFlow().orElse(null);
		PlayerRecord player = state.players().get(actor.getUUID());
		Map<Identifier, StoredValue> flowFields = flow == null
			? Map.of()
			: Optional.ofNullable(flow.runtime().members().get(actor.getUUID()))
				.map(FlowMemberState::flowFields)
				.orElseGet(Map::of);
		return new PredicateContext(
			actor.getUUID(),
			flow == null ? new UUID(0L, 0L) : flow.identity().instanceId(),
			state.activeGameId().orElse(PixelTzzPro.id("game/none")),
			flow == null ? PixelTzzPro.id("panel/predicate") : flow.identity().flowId(),
			flow == null ? 1 : flow.identity().flowVersion(),
			state.activePhaseId().orElse(PixelTzzPro.id("phase/none")),
			flowFields,
			player == null ? Map.of() : player.persistentFields()
		);
	}

	private static boolean hostOperation(final OperationKey operation) {
		return (
			operation.type().equals("claim_host")
				&& operation.id().equals(CLAIM_HOST_OPERATION)
		) || (
			operation.type().equals("transfer_host")
				&& operation.id().equals(TRANSFER_HOST_OPERATION)
		) || (
			operation.type().equals("takeover_host")
				&& operation.id().equals(TAKEOVER_HOST_OPERATION)
		);
	}

	private static boolean flowRosterOperation(final OperationKey operation) {
		return addFlowMembersOperation(operation)
			|| removeFlowMembersOperation(operation)
			|| cancelFlowOperation(operation);
	}

	private static boolean addFlowMembersOperation(final OperationKey operation) {
		return operation.type().equals(ADD_FLOW_MEMBERS)
			&& operation.id().equals(ADD_FLOW_MEMBERS_OPERATION);
	}

	private static boolean removeFlowMembersOperation(final OperationKey operation) {
		return operation.type().equals(REMOVE_FLOW_MEMBERS)
			&& operation.id().equals(REMOVE_FLOW_MEMBERS_OPERATION);
	}

	private static boolean cancelFlowOperation(final OperationKey operation) {
		return operation.type().equals(CANCEL_FLOW)
			&& operation.id().equals(CANCEL_FLOW_OPERATION);
	}

	private static boolean unfinishedMember(final FlowMemberState member) {
		return switch (member.status()) {
			case NOT_STARTED, IN_PROGRESS, OFFLINE, BLOCKED -> true;
			case ALREADY_COMPLETE, COMPLETED, REMOVED -> false;
		};
	}

	private static String relatedStateDigest(
		final MinecraftServer server,
		final WorldStateV2 state,
		final OperationKey operation,
		final List<UUID> targets
	) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digestPart(digest, operation.type());
			digestPart(digest, operation.id().toString());
			digestPart(digest, state.activeGameId().map(Identifier::toString).orElse(""));
			digestPart(digest, state.activePhaseId().map(Identifier::toString).orElse(""));
			digestPart(
				digest,
				state.host().map(host -> host.playerId().toString()).orElse("")
			);
			state.activeForcedFlow().ifPresent(flow -> {
				digestPart(digest, flow.identity().instanceId().toString());
				digestPart(digest, flow.runtime().status().getSerializedName());
				digestPart(digest, Long.toString(flow.runtime().instanceRevision()));
				flow.runtime().callbacks().failures().forEach(failure -> {
					digestPart(digest, failure.kind().getSerializedName());
					digestPart(digest, failure.functionId().toString());
					digestPart(digest, failure.playerId().map(UUID::toString).orElse(""));
					digestPart(digest, Integer.toString(failure.attemptCount()));
					digestPart(digest, failure.error());
				});
			});
			for (UUID targetId : targets.stream().sorted().toList()) {
				digestPart(digest, targetId.toString());
				ServerPlayer online = server.getPlayerList().getPlayer(targetId);
				digestPart(digest, online == null ? "offline" : "online:" + online.getPlainTextName());
				PlayerRecord record = state.players().get(targetId);
				if (record == null) {
					digestPart(digest, "unregistered");
					continue;
				}
				digestPart(digest, record.roleId().toString());
				digestPart(digest, record.teamId().map(Identifier::toString).orElse(""));
				digestPart(digest, record.lifeStateId().toString());
				digestPart(
					digest,
					record.pendingRoleChange()
						.map(change -> change.status().getSerializedName())
						.orElse("")
				);
				digestPart(
					digest,
					record.activeFlowParticipation()
						.map(participation -> participation.status().getSerializedName())
						.orElse("")
				);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static void digestPart(final MessageDigest digest, final String value) {
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
		digest.update(encoded);
	}

	private static CallbackDispatch executePreparedCallback(
		final MinecraftServer server,
		final PrepareResult preparation
	) {
		if (!preparation.successful()) {
			return CallbackDispatch.failed(preparation.code(), preparation.message());
		}
		if (!preparation.prepared()) {
			return CallbackDispatch.succeeded("");
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 before = wrapper.currentV2().orElse(null);
		if (before == null) {
			return CallbackDispatch.failed(
				OperationCode.SCHEMA_BLOCKED,
				"世界状态当前不可写。"
			);
		}
		PixelTzzWorldState.CommitResult markerCommit = wrapper.commit(
			before.stateRevision(),
			ignored -> preparation.nextState().orElseThrow()
		);
		if (!markerCommit.committed()) {
			return CallbackDispatch.failed(
				OperationCode.STATE_REVISION_STALE,
				"回调标记提交失败：" + markerCommit.reason()
			);
		}
		PreparedInvocation prepared = preparation.invocation().orElseThrow();
		Optional<ServerPlayer> sourcePlayer = prepared.playerId()
			.map(server.getPlayerList()::getPlayer);
		FlowCallbackRunner.Invocation invocation = FlowCallbackRunner.invoke(
			server,
			prepared.functionId(),
			prepared.context(),
			sourcePlayer
		);
		WorldStateV2 marked = markerCommit.state().orElseThrow();
		OutcomeResult outcome = FlowCallbackAuthority.recordOutcome(
			marked,
			prepared,
			invocation,
			System.currentTimeMillis()
		);
		if (outcome.nextState().isPresent()) {
			PixelTzzWorldState.CommitResult outcomeCommit = wrapper.commit(
				marked.stateRevision(),
				ignored -> outcome.nextState().orElseThrow()
			);
			if (!outcomeCommit.committed()) {
				PixelTzzPro.LOGGER.error(
					"Callback {} outcome for flow {} could not be persisted: {}",
					prepared.kind().getSerializedName(),
					prepared.instanceId(),
					outcomeCommit.reason()
				);
				return CallbackDispatch.failed(
					OperationCode.STATE_REVISION_STALE,
					"回调结果提交失败：" + outcomeCommit.reason()
				);
			}
		}
		return outcome.invocationSucceeded()
			? CallbackDispatch.succeeded("")
			: CallbackDispatch.failed(outcome.code(), outcome.message());
	}

	private static CallbackDispatch resumePendingCallbacks(final MinecraftServer server) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		if (state == null || state.activeForcedFlow().isEmpty()) {
			return CallbackDispatch.succeeded("");
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElseThrow();
		if (!instance.runtime().callbacks().failures().isEmpty()) {
			CallbackFailure failure = instance.runtime().callbacks().failures().getLast();
			return CallbackDispatch.failed(OperationCode.CALLBACK_FAILED, failure.error());
		}
		if (
			instance.runtime().status() != FlowInstanceStatus.ACTIVE
				&& instance.runtime().status() != FlowInstanceStatus.COMPLETED
		) {
			return CallbackDispatch.succeeded("");
		}

		CallbackDispatch dispatched = executePreparedCallback(
			server,
			FlowCallbackAuthority.prepareOnStart(state, System.currentTimeMillis())
		);
		if (!dispatched.successful()) {
			return dispatched;
		}
		state = wrapper.currentV2().orElse(state);
		instance = state.activeForcedFlow().orElse(null);
		if (instance == null) {
			return CallbackDispatch.succeeded("");
		}
		boolean hasPlayerCallback = instance.runtime()
			.executionSnapshot()
			.callbackReferences()
			.onPlayerComplete()
			.isPresent();
		if (hasPlayerCallback) {
			for (
				FlowMemberState member
				: instance.runtime().members().values()
					.stream()
					.filter(value -> value.status() == FlowMemberStatus.COMPLETED)
					.sorted(Comparator.comparing(FlowMemberState::playerId))
					.toList()
			) {
				state = wrapper.currentV2().orElse(state);
				instance = state.activeForcedFlow().orElse(null);
				if (
					instance == null
						|| instance.runtime().callbacks().onPlayerCompleteInvoked()
							.contains(member.playerId())
				) {
					continue;
				}
				if (server.getPlayerList().getPlayer(member.playerId()) == null) {
					return CallbackDispatch.succeeded("");
				}
				dispatched = executePreparedCallback(
					server,
					FlowCallbackAuthority.preparePlayerComplete(
						state,
						member.playerId(),
						System.currentTimeMillis()
					)
				);
				if (!dispatched.successful()) {
					return dispatched;
				}
			}
		}

		state = wrapper.currentV2().orElse(state);
		instance = state.activeForcedFlow().orElse(null);
		if (
			instance != null
				&& instance.runtime().status() == FlowInstanceStatus.COMPLETED
		) {
			return executePreparedCallback(
				server,
				FlowCallbackAuthority.prepareAllComplete(
					state,
					System.currentTimeMillis()
				)
			);
		}
		return CallbackDispatch.succeeded("");
	}

	private static Optional<CallbackRetryKey> callbackRetryKey(
		final OperationKey operation
	) {
		if (
			!operation.type().equals(RETRY_CALLBACK_OPERATION)
				|| !operation.id().getNamespace().equals(PixelTzzPro.MOD_ID)
				|| !operation.id().getPath().startsWith(RETRY_CALLBACK_PATH_PREFIX)
		) {
			return Optional.empty();
		}
		String[] parts = operation.id()
			.getPath()
			.substring(RETRY_CALLBACK_PATH_PREFIX.length())
			.split("/", -1);
		try {
			return switch (parts[0]) {
				case "on_start" -> parts.length == 1
					? Optional.of(new CallbackRetryKey(CallbackKind.ON_START, Optional.empty()))
					: Optional.empty();
				case "on_player_complete" -> parts.length == 2
					? Optional.of(
						new CallbackRetryKey(
							CallbackKind.ON_PLAYER_COMPLETE,
							Optional.of(UUID.fromString(parts[1]))
						)
					)
					: Optional.empty();
				case "on_all_complete" -> parts.length == 1
					? Optional.of(new CallbackRetryKey(CallbackKind.ON_ALL_COMPLETE, Optional.empty()))
					: Optional.empty();
				default -> Optional.empty();
			};
		} catch (IllegalArgumentException error) {
			return Optional.empty();
		}
	}

	private static Identifier callbackRetryOperationId(final CallbackFailure failure) {
		return PixelTzzPro.id(
			RETRY_CALLBACK_PATH_PREFIX
				+ failure.kind().getSerializedName()
				+ failure.playerId().map(id -> "/" + id).orElse("")
		);
	}

	private static Optional<CallbackFailure> callbackFailure(
		final WorldStateV2 state,
		final CallbackRetryKey retry
	) {
		return state.activeForcedFlow()
			.stream()
			.flatMap(flow -> flow.runtime().callbacks().failures().stream())
			.filter(
				failure -> failure.kind() == retry.kind()
					&& failure.playerId().equals(retry.playerId())
			)
			.reduce((first, second) -> second);
	}

	private static String callbackPlayerName(
		final MinecraftServer server,
		final WorldStateV2 state,
		final UUID playerId
	) {
		ServerPlayer online = server.getPlayerList().getPlayer(playerId);
		if (online != null) {
			return online.getPlainTextName();
		}
		return Optional.ofNullable(state.players().get(playerId))
			.map(PlayerRecord::lastKnownName)
			.orElse(playerId.toString());
	}

	private static ActionEntry callbackRetryAction(
		final MinecraftServer server,
		final WorldStateV2 state,
		final CallbackFailure failure
	) {
		boolean online = failure.playerId()
			.map(id -> server.getPlayerList().getPlayer(id) != null)
			.orElse(true);
		String target = failure.playerId()
			.map(id -> callbackPlayerName(server, state, id))
			.orElse("实例");
		String kind = switch (failure.kind()) {
			case ON_START -> "启动";
			case ON_PLAYER_COMPLETE -> "玩家完成";
			case ON_ALL_COMPLETE -> "全员完成";
		};
		return new ActionEntry(
			RETRY_CALLBACK_OPERATION,
			callbackRetryOperationId(failure),
			"diagnostics",
			-90,
			jsonText("重试" + kind + "回调"),
			jsonText(target + " · " + failure.functionId() + " · 第 " + failure.attemptCount() + " 次失败"),
			"#E94F64",
			online,
			online ? "" : jsonText("对应玩家离线，暂时不能作为回调执行实体。"),
			"none",
			0,
			0,
			true
		);
	}

	private static RichText richText(final String text) {
		JsonObject json = new JsonObject();
		json.addProperty("text", text);
		return new RichText(json.toString(), text);
	}

	private static OperationCode confirmationCode(final Rejected rejected) {
		return OperationCode.bySerializedName(rejected.reason().code())
			.orElse(OperationCode.CONFIRMATION_MISSING);
	}

	private static String hostFailureMessage(final OperationCode code) {
		return switch (code) {
			case SUCCESS -> "";
			case FORBIDDEN -> "需要至少 2 级原版 OP 权限。";
			case NOT_HOST -> "只有当前主持人可以执行该操作。";
			case HOST_ALREADY_ASSIGNED -> "该世界已经有主持人。";
			case TARGET_OFFLINE -> "目标玩家已经离线。";
			case TARGET_INVALID -> "目标玩家无效。";
			case ACTION_UNAVAILABLE -> "该主持人操作当前不可用。";
			default -> "主持人操作失败：" + code.serializedName();
		};
	}

	private static String operationSuccessMessage(
		final OperationKey operation,
		final boolean immediateRoleChanged
	) {
		if (addFlowMembersOperation(operation)) {
			return "已补充流程成员。";
		}
		if (removeFlowMembersOperation(operation)) {
			return "已移除流程成员。";
		}
		if (cancelFlowOperation(operation)) {
			return "已取消当前流程。";
		}
		return immediateRoleChanged ? "身份已立即更新。" : "操作已完成。";
	}

	private static String operationType(final PanelActionDefinition action) {
		return switch (action.operation()) {
			case StartFlowOperation ignored -> "start_flow";
			case AssignRoleOperation ignored -> "assign_role";
			default -> "unsupported";
		};
	}

	private record ActiveFlowDefinitions(
		ForcedFlowInstance instance,
		DefinitionSnapshot definitions,
		FlowDefinition flow,
		PanelActionDefinition sourceAction
	) {
		private ActiveFlowDefinitions {
			Objects.requireNonNull(instance, "instance");
			Objects.requireNonNull(definitions, "definitions");
			Objects.requireNonNull(flow, "flow");
			Objects.requireNonNull(sourceAction, "sourceAction");
		}
	}

	private record PanelActionAvailability(
		boolean visible,
		boolean enabled,
		String message
	) {
		private PanelActionAvailability {
			Objects.requireNonNull(message, "message");
			if (!visible && enabled) {
				throw new IllegalArgumentException("a hidden panel action cannot be enabled");
			}
		}

		private static PanelActionAvailability available() {
			return new PanelActionAvailability(true, true, "");
		}

		private static PanelActionAvailability disabled(final String message) {
			return new PanelActionAvailability(true, false, message);
		}

		private static PanelActionAvailability hidden() {
			return new PanelActionAvailability(false, false, "该操作当前不可见。");
		}
	}

	private record PreparedOperation(
		OperationCode code,
		String message,
		Optional<Binding> binding,
		Optional<Prompt> prompt,
		List<Target> targets,
		String contextSummary,
		Optional<PanelActionDefinition> panelAction
	) {
		private PreparedOperation {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
			binding = Objects.requireNonNull(binding, "binding");
			prompt = Objects.requireNonNull(prompt, "prompt");
			targets = List.copyOf(targets);
			Objects.requireNonNull(contextSummary, "contextSummary");
			panelAction = Objects.requireNonNull(panelAction, "panelAction");
		}

		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static PreparedOperation succeeded(
			final Binding binding,
			final Prompt prompt,
			final List<Target> targets,
			final String contextSummary,
			final Optional<PanelActionDefinition> panelAction
		) {
			return new PreparedOperation(
				OperationCode.SUCCESS,
				"",
				Optional.of(binding),
				Optional.of(prompt),
				targets,
				contextSummary,
				panelAction
			);
		}

		private static PreparedOperation failed(
			final OperationCode code,
			final String message
		) {
			return new PreparedOperation(
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				List.of(),
				"",
				Optional.empty()
			);
		}
	}

	private static void sendConsoleSnapshot(
		final MinecraftServer server,
		final ServerPlayer player,
		final long requestSequence
	) {
		if (!ServerPlayNetworking.canSend(player, ConsoleSnapshotS2CPayload.TYPE)) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		View view = DefinitionRegistry.INSTANCE.view();
		if (state == null) {
			ServerPlayNetworking.send(
				player,
				new ConsoleSnapshotS2CPayload(
					NetworkProtocol.CURRENT_VERSION,
					requestSequence,
					wrapper.stateRevision(),
					view.active().generation(),
					"schema_blocked",
					truncate(wrapper.incompatibilityReason(), ConsoleSnapshotS2CPayload.MAX_MESSAGE_LENGTH),
					Optional.empty(),
					Optional.empty(),
					jsonText("未配置游戏"),
					jsonText("状态只读"),
					isAdminEligible(player),
					false,
					Optional.empty(),
					List.of(),
					List.of(),
					Optional.empty(),
					Optional.empty(),
					0L,
					List.of(
						new Diagnostic(
							"schema_blocked",
							truncate(
								wrapper.incompatibilityReason(),
								ConsoleSnapshotS2CPayload.MAX_MESSAGE_LENGTH
							)
						)
					)
				)
			);
			return;
		}
		Optional<Identifier> gameId = state.activeGameId();
		Optional<Identifier> phaseId = state.activePhaseId();
		String gameName = gameId
			.map(view.active().games()::get)
			.map(game -> game.name().json())
			.orElseGet(() -> jsonText("未配置游戏"));
		String phaseName = phaseId
			.map(view.active().phases()::get)
			.map(phase -> phase.name().json())
			.orElseGet(() -> jsonText("未配置阶段"));
		boolean currentHost = state.host()
			.map(host -> host.playerId().equals(player.getUUID()))
			.orElse(false);
		Optional<HostInfo> host = state.host().map(record -> new HostInfo(
			record.playerId(),
			hostName(server, state, record),
			server.getPlayerList().getPlayer(record.playerId()) != null
		));
		String status;
		String message;
		if (!view.healthy() || !view.active().usable()) {
			status = "definitions_blocked";
			message = truncateDiagnostic(view.diagnosticSummary());
		} else if (state.host().isEmpty()) {
			status = "host_unassigned";
			message = "当前世界尚未认领主持人。";
		} else {
			status = "ready";
			message = state.activeForcedFlow()
				.filter(flow -> flow.runtime().status() == FlowInstanceStatus.ACTIVE)
				.map(flow -> "当前强制流程进行中。")
				.orElse("");
		}
		List<Diagnostic> diagnostics = new ArrayList<>();
		if (!view.healthy()) {
			diagnostics.add(
				new Diagnostic(
					"definitions_blocked",
					truncateDiagnostic(view.diagnosticSummary())
				)
			);
		}
		state.activeForcedFlow()
			.flatMap(flow -> flow.runtime().blockDiagnostic())
			.ifPresent(
				diagnostic -> diagnostics.add(
					new Diagnostic(
						diagnostic.code().toLowerCase(java.util.Locale.ROOT),
						truncate(
							diagnostic.message(),
							ConsoleSnapshotS2CPayload.MAX_MESSAGE_LENGTH
						)
					)
				)
			);
		ServerPlayNetworking.send(
			player,
			new ConsoleSnapshotS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				state.stateRevision(),
				view.active().generation(),
				status,
				message,
				gameId,
				phaseId,
				gameName,
				phaseName,
				isAdminEligible(player),
				currentHost,
				host,
				consolePlayers(server, state, view.active()),
				consoleActions(server, player, state, view),
				activeFlowSummary(server, state),
				recentFlowSummary(server, state),
				state.audit().totalEvents(),
				diagnostics.stream().limit(ConsoleSnapshotS2CPayload.MAX_DIAGNOSTICS).toList()
			)
		);
	}

	private static List<ActionEntry> consoleActions(
		final MinecraftServer server,
		final ServerPlayer player,
		final WorldStateV2 state,
		final View view
	) {
		List<ActionEntry> result = new ArrayList<>();
		boolean admin = isAdminEligible(player);
		boolean currentHost = state.host()
			.map(host -> host.playerId().equals(player.getUUID()))
			.orElse(false);
		if (state.host().isEmpty() && admin) {
			result.add(
				systemAction(
					"claim_host",
					CLAIM_HOST_OPERATION,
					"认领主持人",
					"成为该世界唯一的全员逃走中主持人。",
					"#D7B45A",
					"none",
					0,
					0
				)
			);
		}
		if (currentHost) {
			boolean transferTargetAvailable = server.getPlayerList()
				.getPlayers()
				.stream()
				.anyMatch(candidate ->
					!candidate.getUUID().equals(player.getUUID()) && verified(candidate)
				);
			result.add(
				systemAction(
					"transfer_host",
					TRANSFER_HOST_OPERATION,
					"转交主持人",
					"将模组主持人权限原子转交给一名在线玩家。",
					"#55D7E6",
					"single",
					1,
					1,
					transferTargetAvailable,
					"当前没有其他已连接并通过模组验证的在线玩家。"
				)
			);
		} else if (state.host().isPresent() && admin) {
			result.add(
				systemAction(
					"takeover_host",
					TAKEOVER_HOST_OPERATION,
					"接管主持人",
					"以至少 2 级 OP 权限接管离线或失去控制的主持人。",
					"#E94F64",
					"none",
					0,
					0
				)
			);
		}
		if (currentHost) {
			state.activeForcedFlow()
				.filter(flow -> flow.runtime().status() == FlowInstanceStatus.BLOCKED)
				.stream()
				.flatMap(flow -> flow.runtime().callbacks().failures().stream())
				.sorted(
					Comparator.comparing((CallbackFailure failure) -> failure.kind().getSerializedName())
						.thenComparing(failure -> failure.playerId().map(UUID::toString).orElse(""))
				)
				.forEach(failure -> result.add(callbackRetryAction(server, state, failure)));
			state.activeForcedFlow()
				.filter(
					flow -> flow.runtime().status() == FlowInstanceStatus.ACTIVE
						|| flow.runtime().status() == FlowInstanceStatus.BLOCKED
				)
				.ifPresent(flow -> {
					int remainingCapacity = WorldStateV2.MAX_INSTANCE_MEMBERS
						- flow.runtime().members().size();
					boolean recoverableBlock = FlowBlockAuthority.isRecoverableResourceBlock(flow);
					boolean canAdd = (
						flow.runtime().status() == FlowInstanceStatus.ACTIVE
							|| recoverableBlock
					)
						&& remainingCapacity > 0
						&& activeFlowDefinitions(state).isPresent();
					result.add(
						systemAction(
							ADD_FLOW_MEMBERS,
							ADD_FLOW_MEMBERS_OPERATION,
							"补充流程成员",
							"将在线玩家加入当前冻结流程，并立即更新有效总人数。",
							"#55D7E6",
							"multiple",
							canAdd ? 1 : 0,
							canAdd ? remainingCapacity : 0,
							canAdd,
							flow.runtime().status() == FlowInstanceStatus.BLOCKED
								&& !recoverableBlock
								? "流程已阻塞，修复后才能补充成员。"
								: "当前流程已达到成员上限或冻结定义不可恢复。"
						)
					);
					long removable = flow.runtime().members()
						.values()
						.stream()
						.filter(PixelTzzServerRuntime::unfinishedMember)
						.count();
					boolean canRemove = removable > 0;
					result.add(
						systemAction(
							REMOVE_FLOW_MEMBERS,
							REMOVE_FLOW_MEMBERS_OPERATION,
							"移除流程成员",
							"移除未完成成员、减少有效总人数，并取消其待提交身份变更。",
							"#D7B45A",
							"multiple",
							canRemove ? 1 : 0,
							canRemove ? (int) removable : 0,
							canRemove,
							"当前没有可移除的未完成成员。"
						)
					);
					result.add(
						systemAction(
							CANCEL_FLOW,
							CANCEL_FLOW_OPERATION,
							"取消当前流程",
							"关闭全部未完成页面并取消依赖本流程的待提交身份变更。",
							"#E94F64",
							"none",
							0,
							0,
							true,
							""
						)
					);
				});
		}
		if (!currentHost || !view.healthy()) {
			return result.stream()
				.limit(ConsoleSnapshotS2CPayload.MAX_ACTIONS)
				.toList();
		}
		boolean activeFlow = state.activeForcedFlow()
			.map(flow -> flow.runtime().status())
			.filter(status -> status == FlowInstanceStatus.ACTIVE || status == FlowInstanceStatus.BLOCKED)
			.isPresent();
		FrozenPredicateEvaluator predicateEvaluator = new FrozenPredicateEvaluator(
			server,
			view.active().predicateDocuments()
		);
		PredicateContext predicateContext = panelPredicateContext(state, player);
		view.active().panelActions().values()
			.stream()
			.filter(action -> state.activeGameId().filter(action.game()::equals).isPresent())
			.filter(
				action -> action.phases().isEmpty()
					|| state.activePhaseId().filter(action.phases()::contains).isPresent()
			)
			.filter(PixelTzzServerRuntime::supportedPanelAction)
			.sorted(
				Comparator.comparing(PanelActionDefinition::section)
					.thenComparingInt(PanelActionDefinition::order)
					.thenComparing(action -> action.id().toString())
			)
			.forEach(action -> {
				PanelActionAvailability availability = panelActionAvailability(
					predicateEvaluator,
					predicateContext,
					action
				);
				if (!availability.visible()) {
					return;
				}
				PanelActionAvailability effective = !activeFlow && availability.enabled()
					? panelCandidateAvailability(
						server,
						state,
						view.active(),
						player,
						action
					)
					: availability;
				boolean enabled = !activeFlow && effective.enabled();
				result.add(
					new ActionEntry(
						operationType(action),
						action.id(),
						action.section(),
						action.order(),
						action.label().json(),
						action.description().json(),
						action.color().orElse("#D7B45A"),
						enabled,
						enabled
							? ""
							: jsonText(
								activeFlow
									? "必须先结束当前强制流程。"
									: effective.message()
							),
						action.target().mode().name().toLowerCase(java.util.Locale.ROOT),
						action.target().minimum(),
						action.target().maximum(),
						true
					)
				);
			});
		return result.stream()
			.limit(ConsoleSnapshotS2CPayload.MAX_ACTIONS)
			.toList();
	}

	private static ActionEntry systemAction(
		final String type,
		final Identifier id,
		final String label,
		final String description,
		final String color,
		final String targetMode,
		final int minimum,
		final int maximum
	) {
		return systemAction(
			type,
			id,
			label,
			description,
			color,
			targetMode,
			minimum,
			maximum,
			true,
			""
		);
	}

	private static ActionEntry systemAction(
		final String type,
		final Identifier id,
		final String label,
		final String description,
		final String color,
		final String targetMode,
		final int minimum,
		final int maximum,
		final boolean enabled,
		final String disabledReason
	) {
		return new ActionEntry(
			type,
			id,
			"system",
			-100,
			jsonText(label),
			jsonText(description),
			color,
			enabled,
			enabled ? "" : jsonText(disabledReason),
			targetMode,
			minimum,
			maximum,
			true
		);
	}

	private static Optional<ActiveFlowSummary> activeFlowSummary(
		final MinecraftServer server,
		final WorldStateV2 state
	) {
		return flowSummary(
			server,
			state,
			Set.of(FlowInstanceStatus.ACTIVE, FlowInstanceStatus.BLOCKED)
		);
	}

	private static Optional<ActiveFlowSummary> recentFlowSummary(
		final MinecraftServer server,
		final WorldStateV2 state
	) {
		return flowSummary(
			server,
			state,
			Set.of(FlowInstanceStatus.COMPLETED, FlowInstanceStatus.CANCELED)
		);
	}

	private static List<Target> consolePlayers(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions
	) {
		Set<Identifier> displayFlows = definitions.flows()
			.values()
			.stream()
			.filter(flow -> state.activeGameId().filter(flow.game()::equals).isPresent())
			.map(FlowDefinition::id)
			.sorted()
			.limit(TargetSnapshotS2CPayload.MAX_COMPLETION_STATUSES)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		return state.players()
			.values()
			.stream()
			.filter(record -> state.host()
				.map(host -> !host.playerId().equals(record.playerId()))
				.orElse(true))
			.sorted(
				Comparator.comparing(PlayerRecord::lastKnownName, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(PlayerRecord::playerId)
			)
			.limit(ConsoleSnapshotS2CPayload.MAX_MEMBERS)
			.map(record -> targetFromRecord(
				definitions,
				state,
				record,
				record.lastKnownName(),
				server.getPlayerList().getPlayer(record.playerId()) != null,
				record.activeFlowParticipation()
					.map(participation -> participation.status().getSerializedName())
					.orElse("none"),
				displayFlows,
				true,
				""
			))
			.toList();
	}

	private static Optional<ActiveFlowSummary> flowSummary(
		final MinecraftServer server,
		final WorldStateV2 state,
		final Set<FlowInstanceStatus> statuses
	) {
		return state.activeForcedFlow()
			.filter(flow -> statuses.contains(flow.runtime().status()))
			.map(flow -> {
			var restored = ExecutionSnapshotCompiler.restore(
				flow.identity().flowId(),
				flow.runtime().executionSnapshot()
			);
			DefinitionSnapshot presentationDefinitions = restored.success()
				? restored.snapshot().orElseThrow()
				: DefinitionRegistry.INSTANCE.view().active();
			FlowDefinition definition = presentationDefinitions.flows()
				.get(flow.identity().flowId());
			String flowName = definition == null
				? jsonText("强制流程记录")
				: definition.name().json();
			String initiatedByName = Optional.ofNullable(state.players().get(flow.identity().initiatedBy()))
				.map(PlayerRecord::lastKnownName)
				.orElseGet(() -> Optional.ofNullable(
					server.getPlayerList().getPlayer(flow.identity().initiatedBy())
				).map(ServerPlayer::getPlainTextName).orElse("未知主持人"));
			PanelActionDefinition sourceAction = presentationDefinitions.panelActions()
				.get(flow.identity().sourceActionId());
			Set<Identifier> displayFlows = new LinkedHashSet<>(
				presentationStatusFlowIds(sourceAction)
			);
			displayFlows.add(flow.identity().flowId());
			Target initiatedBy = confirmationTarget(
				server,
				state,
				presentationDefinitions,
				flow.identity().initiatedBy(),
				initiatedByName,
				displayFlows
			);
			Optional<UUID> blockedPlayer = flow.runtime()
				.blockDiagnostic()
				.flatMap(WorldStateV2.BlockDiagnostic::playerId);
			List<MemberSummary> members = flow.runtime().members()
				.values()
				.stream()
				.sorted(Comparator.comparing(FlowMemberState::playerId))
				.map(
					member -> new MemberSummary(
						targetFromRecord(
							presentationDefinitions,
							state,
							Objects.requireNonNull(
								state.players().get(member.playerId()),
								"flow member player record"
							),
							member.lockedName(),
							server.getPlayerList().getPlayer(member.playerId()) != null,
							member.status().getSerializedName(),
							displayFlows,
							true,
							""
						),
						member.status().getSerializedName(),
						member.currentNodeId().orElse(""),
						nodeNameJson(
							definition,
							presentationDefinitions,
							member.currentNodeId()
						),
						member.status() == FlowMemberStatus.BLOCKED
							|| blockedPlayer.filter(member.playerId()::equals).isPresent()
					)
				)
				.toList();
			return new ActiveFlowSummary(
				flow.identity().instanceId(),
				flow.identity().flowId(),
				flowName,
				flow.runtime().status().getSerializedName(),
				initiatedBy,
				flow.identity().createdAtEpochMillis(),
				flow.runtime().completedCount(),
				flow.runtime().totalCount(),
				flow.runtime().instanceRevision(),
				members,
				!flow.runtime().callbacks().failures().isEmpty(),
				FlowBlockAuthority.isRecoverableResourceBlock(flow)
			);
		});
	}

	private static String nodeNameJson(
		final FlowDefinition flow,
		final DefinitionSnapshot definitions,
		final Optional<String> nodeId
	) {
		if (flow == null || nodeId.isEmpty()) {
			return jsonText("尚未进入流程页面");
		}
		FlowNode node = flow.nodes().get(nodeId.orElseThrow());
		Identifier pageId = node instanceof PageNode page
			? page.page()
			: node instanceof ChoiceNode choice
				? choice.page()
				: node instanceof ConfirmNode confirm ? confirm.page() : null;
		if (pageId != null) {
			var page = definitions.pages().get(pageId);
			return page == null ? jsonText("流程页面") : page.title().json();
		}
		return jsonText(
			node instanceof BranchNode
				? "正在判定下一步"
				: node instanceof FunctionNode
					? "正在执行流程回调"
					: node instanceof WaitPlayersNode
						? "等待其他玩家"
						: node instanceof ChangeStateNode
							? "正在更新游戏状态"
							: node instanceof CompleteNode ? "正在完成流程" : "流程节点"
		);
	}

	private static void sendTargetSnapshot(
		final MinecraftServer server,
		final ServerPlayer actor,
		final long requestSequence,
		final OperationKey operation
	) {
		if (!ServerPlayNetworking.canSend(actor, TargetSnapshotS2CPayload.TYPE)) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		View view = DefinitionRegistry.INSTANCE.view();
		if (state == null || state.activeGameId().isEmpty()) {
			sendOperationResult(
				actor,
				requestSequence,
				OperationCode.SCHEMA_BLOCKED,
				"世界状态当前不可用。",
				wrapper.stateRevision(),
				view.active().generation(),
				true
			);
			return;
		}
		PanelActionDefinition action = view.active().panelActions().get(operation.id());
		DefinitionSnapshot targetDefinitions = view.active();
		String targetMode;
		int minimum;
		int maximum;
		if (
			operation.type().equals("transfer_host")
				&& operation.id().equals(TRANSFER_HOST_OPERATION)
		) {
			targetMode = "single";
			minimum = 1;
			maximum = 1;
		} else if (addFlowMembersOperation(operation)) {
			ActiveFlowDefinitions active = activeFlowDefinitions(state).orElse(null);
			if (active == null) {
				sendOperationResult(
					actor,
					requestSequence,
					OperationCode.SNAPSHOT_INVALID,
					"当前流程的冻结目标规则不可恢复。",
					state.stateRevision(),
					view.active().generation(),
					true
				);
				return;
			}
			action = active.sourceAction();
			targetDefinitions = active.definitions();
			targetMode = "multiple";
			minimum = 1;
			maximum = Math.max(
				1,
				WorldStateV2.MAX_INSTANCE_MEMBERS - active.instance().runtime().members().size()
			);
		} else if (removeFlowMembersOperation(operation)) {
			ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
			if (instance == null) {
				sendOperationResult(
					actor,
					requestSequence,
					OperationCode.FLOW_NOT_ACTIVE,
					"当前没有可以调整的流程。",
					state.stateRevision(),
					view.active().generation(),
					true
				);
				return;
			}
			List<TargetSnapshotS2CPayload.Target> memberTargets = instance.runtime()
				.members()
				.values()
				.stream()
				.sorted(Comparator.comparing(FlowMemberState::playerId))
				.limit(TargetSnapshotS2CPayload.MAX_TARGETS)
				.map(member -> rosterRemovalTarget(server, state, view.active(), member))
				.filter(TargetSnapshotS2CPayload.Target::eligible)
				.toList();
			if (memberTargets.isEmpty()) {
				sendOperationResult(
					actor,
					requestSequence,
					OperationCode.NO_ELIGIBLE_TARGETS,
					"当前没有可移除的未完成成员。",
					state.stateRevision(),
					view.active().generation(),
					false
				);
				return;
			}
			sendTargetSnapshotPayload(
				actor,
				requestSequence,
				operation,
				state,
				view.active().generation(),
				"multiple",
				1,
				memberTargets.size(),
				memberTargets
			);
			return;
		} else if (action != null && operationType(action).equals(operation.type())) {
			targetMode = action.target().mode().name().toLowerCase(java.util.Locale.ROOT);
			minimum = action.target().minimum();
			maximum = action.target().maximum();
		} else {
			sendOperationResult(
				actor,
				requestSequence,
				OperationCode.ACTION_UNAVAILABLE,
				"目标操作已经不可用。",
				state.stateRevision(),
				view.active().generation(),
				true
			);
			return;
		}
		DefinitionSnapshot effectiveDefinitions = targetDefinitions;
		PanelActionDefinition effectiveAction = action;
		List<TargetSnapshotS2CPayload.Target> targets = eligibleActionTargets(
			server,
			state,
			effectiveDefinitions,
			actor,
			operation,
			effectiveAction
		);
		if (targets.size() < minimum) {
			sendOperationResult(
				actor,
				requestSequence,
				OperationCode.NO_ELIGIBLE_TARGETS,
				"当前没有符合此操作条件的玩家。",
				state.stateRevision(),
				view.active().generation(),
				false
			);
			return;
		}
		sendTargetSnapshotPayload(
			actor,
			requestSequence,
			operation,
			state,
			view.active().generation(),
			targetMode,
			minimum,
			Math.min(maximum, targets.size()),
			targets
		);
	}

	private static TargetSnapshotS2CPayload.Target targetEntry(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final ServerPlayer actor,
		final OperationKey operation,
		final PanelActionDefinition action,
		final ConnectedPlayer connected
	) {
		PlayerRecord record = ForcedFlowAuthority.currentPlayer(
			state,
			definitions,
			connected,
			System.currentTimeMillis()
		);
		boolean eligible;
		String reason;
		if (
			operation.type().equals("transfer_host")
				&& operation.id().equals(TRANSFER_HOST_OPERATION)
		) {
			eligible = !connected.playerId().equals(actor.getUUID());
			reason = eligible ? "" : "不能把主持人转交给自己";
		} else if (action == null || action.target().mode() == SelectionMode.NONE) {
			eligible = false;
			reason = "该操作不使用手动目标";
		} else {
			UUID audienceHost = addFlowMembersOperation(operation)
				? state.activeForcedFlow()
					.map(flow -> flow.identity().initiatedBy())
					.orElse(actor.getUUID())
				: actor.getUUID();
			var result = ForcedFlowAuthority.resolveEligibleCandidate(
				state,
				definitions,
				action,
				audienceHost,
				connected,
				System.currentTimeMillis()
			);
			eligible = result.successful();
			reason = eligible ? "" : result.message();
			if (eligible && addFlowMembersOperation(operation)) {
				ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
				FlowRosterAuthority.Result addResult = instance == null
					? null
					: FlowRosterAuthority.addMembers(
						state,
						actor.getUUID(),
						instance.identity().instanceId(),
						instance.runtime().instanceRevision(),
						List.of(new OnlineMember(connected.playerId(), connected.name(), true)),
						UUID::randomUUID,
						System.currentTimeMillis()
					);
				eligible = addResult != null && addResult.successful();
				reason = eligible
					? ""
					: addResult == null ? "当前没有活动流程" : addResult.message();
			} else if (eligible && immediateRoleAction(action)) {
				AssignRoleOperation assign = (AssignRoleOperation) action.operation();
				FlowRosterAuthority.Result roleResult = FlowRosterAuthority.assignRoleImmediate(
					state,
					definitions,
					actor.getUUID(),
					state.stateRevision(),
					assign.role(),
					List.of(connected.playerId()),
					System.currentTimeMillis()
				);
				eligible = roleResult.successful();
				reason = eligible ? "" : roleResult.message();
			}
		}
		var role = definitions.roles().get(record.roleId());
		var team = record.teamId().map(definitions.teams()::get).orElse(null);
		var life = definitions.lifeStates().get(record.lifeStateId());
		return new TargetSnapshotS2CPayload.Target(
			record.playerId(),
			record.lastKnownName(),
			connected.online(),
			record.roleId(),
			role == null ? jsonText(record.roleId().toString()) : role.name().json(),
			role == null ? "" : role.tab().color().orElse(""),
			record.teamId(),
			team == null ? "" : team.name().json(),
			record.lifeStateId(),
			life == null ? jsonText(record.lifeStateId().toString()) : life.name().json(),
			record.activeFlowParticipation()
				.map(participation -> participation.status().getSerializedName())
				.orElse("none"),
			completionStatuses(definitions, state, record, statusFlowIds(action)),
			eligible,
			truncate(reason, TargetSnapshotS2CPayload.MAX_REASON_LENGTH)
		);
	}

	private static TargetSnapshotS2CPayload.Target rosterRemovalTarget(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final FlowMemberState member
	) {
		PlayerRecord record = state.players().get(member.playerId());
		if (record == null) {
			throw new IllegalStateException("flow member is missing its player record");
		}
		boolean eligible = unfinishedMember(member);
		return targetFromRecord(
			definitions,
			state,
			record,
			member.lockedName(),
			server.getPlayerList().getPlayer(member.playerId()) != null,
			member.status().getSerializedName(),
			Set.of(),
			eligible,
			eligible ? "" : "只能移除尚未结束流程的成员"
		);
	}

	private static TargetSnapshotS2CPayload.Target targetFromRecord(
		final DefinitionSnapshot definitions,
		final WorldStateV2 state,
		final PlayerRecord record,
		final String name,
		final boolean online,
		final String flowStatus,
		final Set<Identifier> statusFlowIds,
		final boolean eligible,
		final String reason
	) {
		var role = definitions.roles().get(record.roleId());
		var team = record.teamId().map(definitions.teams()::get).orElse(null);
		var life = definitions.lifeStates().get(record.lifeStateId());
		return new TargetSnapshotS2CPayload.Target(
			record.playerId(),
			name,
			online,
			record.roleId(),
			role == null ? jsonText(record.roleId().toString()) : role.name().json(),
			role == null ? "" : role.tab().color().orElse(""),
			record.teamId(),
			team == null ? "" : team.name().json(),
			record.lifeStateId(),
			life == null ? jsonText(record.lifeStateId().toString()) : life.name().json(),
			flowStatus,
			completionStatuses(definitions, state, record, statusFlowIds),
			eligible,
			truncate(reason, TargetSnapshotS2CPayload.MAX_REASON_LENGTH)
		);
	}

	private static Target confirmationTarget(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions,
		final UUID playerId,
		final String name,
		final Set<Identifier> statusFlowIds
	) {
		PlayerRecord record = state.players().get(playerId);
		if (record == null) {
			ServerPlayer online = server.getPlayerList().getPlayer(playerId);
			if (online != null) {
				record = ForcedFlowAuthority.currentPlayer(
					state,
					definitions,
					new ConnectedPlayer(playerId, name, true, verified(online)),
					System.currentTimeMillis()
				);
			}
		}
		if (record == null) {
			var game = state.activeGameId().map(definitions.games()::get).orElse(null);
			if (game == null) {
				throw new IllegalStateException("confirmation target has no displayable game profile");
			}
			var role = definitions.roles().get(game.defaultRole());
			var life = definitions.lifeStates().get(game.defaultLifeState());
			return new Target(
				playerId,
				name,
				false,
				game.defaultRole(),
				role == null ? jsonText(game.defaultRole().toString()) : role.name().json(),
				role == null ? "" : role.tab().color().orElse(""),
				Optional.empty(),
				"",
				game.defaultLifeState(),
				life == null ? jsonText(game.defaultLifeState().toString()) : life.name().json(),
				"none",
				List.of(),
				true,
				""
			);
		}
		return targetFromRecord(
			definitions,
			state,
			record,
			name,
			server.getPlayerList().getPlayer(playerId) != null,
			record.activeFlowParticipation()
				.map(participation -> participation.status().getSerializedName())
				.orElse("none"),
			statusFlowIds,
			true,
			""
		);
	}

	private static Set<Identifier> statusFlowIds(final PanelActionDefinition action) {
		if (action == null) {
			return Set.of();
		}
		Set<Identifier> result = new LinkedHashSet<>();
		result.addAll(action.target().filter().completedFlows());
		result.addAll(action.target().filter().incompleteFlows());
		result.addAll(action.target().filter().statusFlows());
		return result.stream()
			.sorted()
			.limit(TargetSnapshotS2CPayload.MAX_COMPLETION_STATUSES)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private static Set<Identifier> presentationStatusFlowIds(
		final PanelActionDefinition frozenAction
	) {
		if (frozenAction == null) {
			return Set.of();
		}
		PanelActionDefinition liveAction = DefinitionRegistry.INSTANCE.view()
			.active()
			.panelActions()
			.get(frozenAction.id());
		return liveAction != null && liveAction.game().equals(frozenAction.game())
			? statusFlowIds(liveAction)
			: statusFlowIds(frozenAction);
	}

	private static List<FlowCompletionStatus> completionStatuses(
		final DefinitionSnapshot definitions,
		final WorldStateV2 state,
		final PlayerRecord record,
		final Set<Identifier> flowIds
	) {
		return flowIds.stream()
			.sorted()
			.map(flowId -> {
				FlowDefinition flow = definitions.flows().get(flowId);
				if (flow == null) {
					return null;
				}
				int completedVersion = state.completionHistory()
					.stream()
					.filter(completion -> completion.playerId().equals(record.playerId()))
					.filter(completion -> completion.flowId().equals(flowId))
					.max(Comparator.comparingLong(WorldStateV2.CompletionRecord::completedAtEpochMillis))
					.map(WorldStateV2.CompletionRecord::flowVersion)
					.orElse(0);
				boolean current = state.completionHistory()
					.stream()
					.anyMatch(completion ->
						completion.playerId().equals(record.playerId())
							&& completion.flowId().equals(flowId)
							&& completion.flowVersion() == flow.version()
					);
				return new FlowCompletionStatus(
					flowId,
					flow.name().json(),
					flow.version(),
					current ? flow.version() : completedVersion,
					current ? "current" : completedVersion == 0 ? "incomplete" : "outdated"
				);
			})
			.filter(Objects::nonNull)
			.toList();
	}

	private static void sendTargetSnapshotPayload(
		final ServerPlayer actor,
		final long requestSequence,
		final OperationKey operation,
		final WorldStateV2 state,
		final long definitionGeneration,
		final String targetMode,
		final int minimum,
		final int maximum,
		final List<TargetSnapshotS2CPayload.Target> targets
	) {
		ServerPlayNetworking.send(
			actor,
			new TargetSnapshotS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				operation.type(),
				operation.id(),
				state.stateRevision(),
				definitionGeneration,
				targetMode,
				minimum,
				maximum,
				targets
			)
		);
	}

	private static void sendOperationResult(
		final ServerPlayer player,
		final long requestSequence,
		final OperationCode code,
		final String message,
		final long stateRevision,
		final long generation,
		final boolean refreshConsole
	) {
		if (!ServerPlayNetworking.canSend(player, OperationResultS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(
			player,
			new OperationResultS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				code,
				truncate(message, OperationResultS2CPayload.MAX_MESSAGE_LENGTH),
				stateRevision,
				generation,
				refreshConsole
			)
		);
	}

	private static boolean admitControlRequest(
		final MinecraftServer server,
		final ServerPlayer player,
		final long requestSequence
	) {
		return admitRequest(
			server,
			player,
			requestSequence,
			CONTROL_REQUEST_GATE,
			"控制"
		);
	}

	private static boolean admitFlowActionRequest(
		final MinecraftServer server,
		final ServerPlayer player,
		final UUID flowInstanceId,
		final long requestSequence
	) {
		ControlRequestGate.Decision decision = FLOW_ACTION_REQUEST_GATE.admit(
			player.getUUID(),
			flowInstanceId,
			requestSequence,
			System.currentTimeMillis()
		);
		if (decision == ControlRequestGate.Decision.ACCEPTED) {
			return true;
		}
		OperationCode code = decision == ControlRequestGate.Decision.RATE_LIMITED
			? OperationCode.RATE_LIMITED
			: OperationCode.REQUEST_REPLAYED;
		String message = switch (decision) {
			case REPLAYED -> "该流程请求已经处理过。";
			case RATE_LIMITED -> "流程请求过于频繁，请稍后再试。";
			case INVALID -> "流程请求序号无效。";
			case ACCEPTED -> "";
		};
		sendOperationResult(
			player,
			requestSequence,
			code,
			message,
			PixelTzzWorldState.get(server).stateRevision(),
			DefinitionRegistry.INSTANCE.view().active().generation(),
			false
		);
		return false;
	}

	private static boolean admitRequest(
		final MinecraftServer server,
		final ServerPlayer player,
		final long requestSequence,
		final ControlRequestGate gate,
		final String requestName
	) {
		ControlRequestGate.Decision decision = gate.admit(
			player.getUUID(),
			requestSequence,
			System.currentTimeMillis()
		);
		if (decision == ControlRequestGate.Decision.ACCEPTED) {
			return true;
		}
		OperationCode code = decision == ControlRequestGate.Decision.RATE_LIMITED
			? OperationCode.RATE_LIMITED
			: OperationCode.REQUEST_REPLAYED;
		String message = switch (decision) {
			case REPLAYED -> "该" + requestName + "请求已经处理过。";
			case RATE_LIMITED -> requestName + "请求过于频繁，请稍后再试。";
			case INVALID -> requestName + "请求序号无效。";
			case ACCEPTED -> "";
		};
		sendOperationResult(
			player,
			requestSequence,
			code,
			message,
			PixelTzzWorldState.get(server).stateRevision(),
			DefinitionRegistry.INSTANCE.view().active().generation(),
			false
		);
		return false;
	}

	private static boolean fatalFlowFailure(final OperationCode code) {
		return code == OperationCode.SNAPSHOT_INVALID
			|| code == OperationCode.RESOURCE_BLOCKED;
	}

	private static WorldStateV2 persistFlowBlock(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final WorldStateV2 expectedState,
		final OperationCode code,
		final String message,
		final Optional<UUID> playerId
	) {
		return persistFlowBlock(
			server,
			wrapper,
			expectedState,
			code,
			message,
			playerId,
			true
		);
	}

	private static WorldStateV2 persistFlowBlock(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final WorldStateV2 expectedState,
		final OperationCode code,
		final String message,
		final Optional<UUID> playerId,
		final boolean refreshProjection
	) {
		FlowBlockAuthority.Result transition = FlowBlockAuthority.block(
			expectedState,
			code,
			message,
			playerId,
			System.currentTimeMillis()
		);
		if (!transition.changed()) {
			if (!transition.successful()) {
				PixelTzzPro.LOGGER.warn(
					"Could not persist {} forced-flow block: {}",
					code.serializedName(),
					transition.message()
				);
			}
			return wrapper.currentV2().orElse(expectedState);
		}
		PixelTzzWorldState.CommitResult committed = wrapper.commit(
			expectedState.stateRevision(),
			ignored -> transition.nextState().orElseThrow()
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.warn(
				"Could not commit {} forced-flow block: {}",
				code.serializedName(),
				committed.reason()
			);
			return wrapper.currentV2().orElse(expectedState);
		}
		WorldStateV2 blocked = committed.state().orElseThrow();
		if (refreshProjection) {
			refreshFlowProjection(server, false);
		}
		sendSnapshots(server, false);
		return blocked;
	}

	private static void sendForcedPages(final MinecraftServer server) {
		PixelTzzWorldState.get(server)
			.currentV2()
			.flatMap(WorldStateV2::activeForcedFlow)
			.ifPresent(
				flow -> flow.runtime().members().keySet().forEach(playerId -> {
					ServerPlayer player = server.getPlayerList().getPlayer(playerId);
					if (player != null && verified(player)) {
						sendCurrentForcedPage(server, player);
					}
				})
			);
	}

	private static void releaseForcedPages(
		final MinecraftServer server,
		final WorldStateV2 previousState,
		final Set<UUID> playerIds,
		final String reason
	) {
		ForcedFlowInstance instance = previousState.activeForcedFlow().orElse(null);
		if (instance == null) {
			return;
		}
		long stateRevision = PixelTzzWorldState.get(server).stateRevision();
		for (UUID playerId : playerIds) {
			FlowMemberState member = instance.runtime().members().get(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (
				member == null
					|| member.pageInstanceId().isEmpty()
					|| player == null
					|| !verified(player)
					|| !ServerPlayNetworking.canSend(player, ForcedPageReleaseS2CPayload.TYPE)
			) {
				continue;
			}
			ServerPlayNetworking.send(
				player,
				new ForcedPageReleaseS2CPayload(
					NetworkProtocol.CURRENT_VERSION,
					instance.identity().instanceId(),
					member.pageInstanceId().orElseThrow(),
					reason,
					stateRevision
				)
			);
		}
	}

	private static void sendCurrentForcedPage(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		if (!verified(player) || !ServerPlayNetworking.canSend(player, PageBundleS2CPayload.TYPE)) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		if (state == null || !hasCurrentForcedPage(state, player.getUUID())) {
			return;
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		FlowMemberState member = instance.runtime().members().get(player.getUUID());
		try {
		var restored = ExecutionSnapshotCompiler.restore(
			instance.identity().flowId(),
			instance.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			PixelTzzPro.LOGGER.error(
				"Cannot restore forced flow {} for {}: {}",
				instance.identity().instanceId(),
				player.getPlainTextName(),
				restored.message()
			);
			persistFlowBlock(
				server,
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				Optional.of(player.getUUID())
			);
			return;
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition flow = definitions.flows().get(instance.identity().flowId());
		if (flow == null) {
			persistFlowBlock(
				server,
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"frozen flow definition is unavailable",
				Optional.of(player.getUUID())
			);
			return;
		}
		var node = flow.nodes().get(member.currentNodeId().orElseThrow());
		Identifier pageId = node == null
			? null
			: switch (node) {
				case PageNode page -> page.page();
				case ChoiceNode choice -> choice.page();
				case ConfirmNode confirm -> confirm.page();
				default -> null;
		};
		if (pageId == null) {
			persistFlowBlock(
				server,
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"current forced-flow node is missing or is not displayable",
				Optional.of(player.getUUID())
			);
			return;
		}
		PageDefinition page = definitions.pages().get(pageId);
		if (page == null) {
			persistFlowBlock(
				server,
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"frozen forced page definition is unavailable",
				Optional.of(player.getUUID())
			);
			return;
		}
		ThemeDefinition theme = page.theme().equals(BuiltInUiResources.defaultTheme().id())
			? BuiltInUiResources.defaultTheme()
			: definitions.themes().get(page.theme());
		var frozenPage = instance.runtime().executionSnapshot().pages().get(pageId);
		var frozenTheme = instance.runtime().executionSnapshot().themes().get(page.theme());
		if (theme == null || frozenPage == null) {
			persistFlowBlock(
				server,
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"frozen forced page or theme document is unavailable",
				Optional.of(player.getUUID())
			);
			return;
		}
		byte[] themeJson;
		byte[] themeHash;
		if (page.theme().equals(BuiltInUiResources.defaultTheme().id())) {
			themeJson = theme.canonicalDocument().getBytes(StandardCharsets.UTF_8);
			themeHash = HexFormat.of().parseHex(theme.sha256());
		} else {
			if (frozenTheme == null) {
				persistFlowBlock(
					server,
					wrapper,
					state,
					OperationCode.SNAPSHOT_INVALID,
					"frozen forced theme document is unavailable",
					Optional.of(player.getUUID())
				);
				return;
			}
			themeJson = frozenTheme.normalizedJson().getBytes(StandardCharsets.UTF_8);
			themeHash = HexFormat.of().parseHex(frozenTheme.sha256());
		}
		long nextSequence = member.requestSequenceWindow().highestAcceptedSequence() < 0L
			? 0L
			: Math.incrementExact(member.requestSequenceWindow().highestAcceptedSequence());
		ServerPlayNetworking.send(
			player,
			new PageBundleS2CPayload(
				member.pageInstanceId().orElseThrow(),
				instance.identity().definitionGeneration(),
				state.stateRevision(),
				page.id(),
				theme.id(),
				HexFormat.of().parseHex(frozenPage.sha256()),
				frozenPage.normalizedJson().getBytes(StandardCharsets.UTF_8),
				themeHash,
				themeJson,
				collectForcedFieldSchemas(
					server,
					definitions,
					page,
					instance,
					member,
					state.players().get(member.playerId())
				),
				PageBundleS2CPayload.PagePurpose.FORCED_FLOW,
				Optional.of(
					new PageBundleS2CPayload.FlowContext(
						instance.identity().instanceId(),
						instance.identity().flowId(),
						instance.identity().flowVersion(),
						member.currentNodeId().orElseThrow(),
						instance.runtime().instanceRevision(),
						member.memberRevision(),
						nextSequence,
						instance.runtime().completedCount(),
						instance.runtime().totalCount(),
						false
					)
				),
				forcedBindingDocument(server, state, instance, member, definitions)
			)
		);
		} catch (RuntimeException error) {
			String message = error.getMessage() == null
				? error.getClass().getSimpleName()
				: error.getMessage();
			PixelTzzPro.LOGGER.error(
				"Could not build forced page {} for {}",
				instance.identity().instanceId(),
				player.getPlainTextName(),
				error
			);
			persistFlowBlock(
				server,
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"forced page bundle failed: " + message,
				Optional.of(player.getUUID())
			);
		}
	}

	private static boolean hasCurrentForcedPage(
		final WorldStateV2 state,
		final UUID playerId
	) {
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (
			instance == null
				|| (
					instance.runtime().status() != FlowInstanceStatus.ACTIVE
						&& !FlowBlockAuthority.isRecoverableResourceBlock(instance)
				)
		) {
			return false;
		}
		FlowMemberState member = instance.runtime().members().get(playerId);
		boolean recoverableBlockedMember = FlowBlockAuthority.isRecoverableResourceBlock(instance)
			&& instance.runtime()
				.blockDiagnostic()
				.flatMap(WorldStateV2.BlockDiagnostic::playerId)
				.filter(playerId::equals)
				.isPresent()
			&& member != null
			&& member.status() == FlowMemberStatus.BLOCKED;
		return member != null
			&& (
				member.status() == FlowMemberStatus.IN_PROGRESS
					|| recoverableBlockedMember
			)
			&& member.pageInstanceId().isPresent()
			&& member.currentNodeId().isPresent();
	}

	private static byte[] forcedBindingDocument(
		final MinecraftServer server,
		final WorldStateV2 state,
		final ForcedFlowInstance instance,
		final FlowMemberState member,
		final DefinitionSnapshot definitions
	) {
		JsonObject root = new JsonObject();
		JsonObject viewer = new JsonObject();
		viewer.addProperty("uuid", member.playerId().toString());
		viewer.addProperty("name", member.lockedName());
		ServerPlayer onlineViewer = server.getPlayerList().getPlayer(member.playerId());
		viewer.addProperty("online", onlineViewer != null);
		viewer.addProperty("admin", onlineViewer != null && isAdminEligible(onlineViewer));
		viewer.addProperty(
			"host",
			state.host()
				.map(WorldStateV2.HostRecord::playerId)
				.filter(member.playerId()::equals)
				.isPresent()
		);
		PlayerRecord player = state.players().get(member.playerId());
		if (player != null) {
			addPlayerDefinitionBindings(viewer, definitions, player);
			viewer.addProperty("ready", false);
		}
		JsonObject session = new JsonObject();
		state.activeGameId().ifPresent(gameId -> {
			session.addProperty("game", gameId.toString());
			session.addProperty(
				"game_name",
				Optional.ofNullable(definitions.games().get(gameId))
					.map(GameDefinitions.GameDefinition::name)
					.map(RichText::plainText)
					.orElse(gameId.getPath())
			);
		});
		state.activePhaseId().ifPresent(phaseId -> {
			session.addProperty("phase", phaseId.toString());
			session.addProperty(
				"phase_name",
				Optional.ofNullable(definitions.phases().get(phaseId))
					.map(GameDefinitions.PhaseDefinition::name)
					.map(RichText::plainText)
					.orElse(phaseId.getPath())
			);
		});
		session.addProperty("revision", state.stateRevision());
		session.addProperty("generation", instance.identity().definitionGeneration());
		session.addProperty("connected", true);
		JsonArray players = new JsonArray();
		instance.runtime().members().values().stream()
			.sorted(Comparator.comparing(FlowMemberState::playerId))
			.forEach(value -> {
				JsonObject entry = new JsonObject();
				entry.addProperty("uuid", value.playerId().toString());
				entry.addProperty("name", value.lockedName());
				ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(value.playerId());
				entry.addProperty("online", onlinePlayer != null);
				entry.addProperty(
					"admin",
					onlinePlayer != null && isAdminEligible(onlinePlayer)
				);
				entry.addProperty(
					"host",
					state.host()
						.map(WorldStateV2.HostRecord::playerId)
						.filter(value.playerId()::equals)
						.isPresent()
				);
				entry.addProperty("flow_status", value.status().getSerializedName());
				PlayerRecord record = state.players().get(value.playerId());
				if (record != null) {
					addPlayerDefinitionBindings(entry, definitions, record);
					entry.addProperty("ready", false);
				}
				players.add(entry);
			});
		session.add("players", players);
		JsonObject flow = new JsonObject();
		flow.addProperty("instance_id", instance.identity().instanceId().toString());
		flow.addProperty("id", instance.identity().flowId().toString());
		flow.addProperty("version", instance.identity().flowVersion());
		flow.addProperty("node", member.currentNodeId().orElse(""));
		flow.addProperty("completed", instance.runtime().completedCount());
		flow.addProperty("total", instance.runtime().totalCount());
		flow.addProperty(
			"remaining",
			instance.runtime().totalCount() - instance.runtime().completedCount()
		);
		flow.addProperty(
			"percent",
			instance.runtime().totalCount() == 0
				? 0
				: (instance.runtime().completedCount() * 100) / instance.runtime().totalCount()
		);
		JsonObject fields = new JsonObject();
		if (player != null) {
			player.persistentFields().forEach(
				(id, value) -> fields.add(id.toString(), storedValueJson(value.value()))
			);
		}
		JsonObject flowFields = new JsonObject();
		member.flowFields().forEach(
			(id, value) -> flowFields.add(id.toString(), storedValueJson(value))
		);
		root.add("viewer", viewer);
		root.add("session", session);
		root.add("flow", flow);
		root.add("fields", fields);
		root.add("flow_fields", flowFields);
		return BindingContextDocument.encode(root);
	}

	private static void addPlayerDefinitionBindings(
		final JsonObject target,
		final DefinitionSnapshot definitions,
		final PlayerRecord player
	) {
		target.addProperty("role", player.roleId().toString());
		target.addProperty(
			"role_name",
			Optional.ofNullable(definitions.roles().get(player.roleId()))
				.map(GameDefinitions.RoleDefinition::name)
				.map(RichText::plainText)
				.orElse(player.roleId().getPath())
		);
		target.addProperty("life_state", player.lifeStateId().toString());
		target.addProperty(
			"life_state_name",
			Optional.ofNullable(definitions.lifeStates().get(player.lifeStateId()))
				.map(GameDefinitions.LifeStateDefinition::name)
				.map(RichText::plainText)
				.orElse(player.lifeStateId().getPath())
		);
		target.addProperty("team", player.teamId().map(Identifier::toString).orElse(""));
		target.addProperty(
			"team_name",
			player.teamId()
				.map(definitions.teams()::get)
				.map(GameDefinitions.TeamDefinition::name)
				.map(RichText::plainText)
				.orElse("")
		);
	}

	private static com.google.gson.JsonElement storedValueJson(final StoredValue value) {
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

	private static void showFlowCompletion(
		final MinecraftServer server,
		final WorldStateV2 state,
		final UUID instanceId,
		final FlowDefinition flow
	) {
		ForcedFlowInstance instance = state.activeForcedFlow()
			.filter(value -> value.identity().instanceId().equals(instanceId))
			.orElse(null);
		if (instance == null || flow.hostBossBar().isEmpty()) {
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		HOST_FLOW_BOSS_BAR.showCompletion(
			server,
			state.host().map(WorldStateV2.HostRecord::playerId),
			instanceId,
			flow.name(),
			flow.hostBossBar().orElseThrow(),
			instance.runtime().completedCount(),
			instance.runtime().totalCount()
		);
	}

	private static void refreshFlowProjection(
		final MinecraftServer server,
		final boolean completionTransition
	) {
		WorldStateV2 state = PixelTzzWorldState.get(server).currentV2().orElse(null);
		pushHostConsoleSnapshot(server);
		if (state == null || state.activeForcedFlow().isEmpty()) {
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElseThrow();
		var restored = ExecutionSnapshotCompiler.restore(
			instance.identity().flowId(),
			instance.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			if (
				instance.runtime().status() == FlowInstanceStatus.COMPLETED
					|| instance.runtime().status() == FlowInstanceStatus.CANCELED
					|| (
						instance.runtime().status() == FlowInstanceStatus.BLOCKED
							&& instance.runtime().completedCount() == instance.runtime().totalCount()
					)
			) {
				// Terminal snapshots are intentionally compacted; keep an already scheduled completion hold.
				return;
			}
			persistFlowBlock(
				server,
				PixelTzzWorldState.get(server),
				state,
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				Optional.empty(),
				false
			);
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		FlowDefinition flow = restored.snapshot().orElseThrow().flows().get(instance.identity().flowId());
		if (flow == null) {
			persistFlowBlock(
				server,
				PixelTzzWorldState.get(server),
				state,
				OperationCode.SNAPSHOT_INVALID,
				"frozen flow definition is unavailable",
				Optional.empty(),
				false
			);
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		if (flow.hostBossBar().isEmpty()) {
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		if (instance.runtime().status() == FlowInstanceStatus.ACTIVE) {
			HOST_FLOW_BOSS_BAR.showActive(
				server,
				state.host().map(WorldStateV2.HostRecord::playerId),
				instance.identity().instanceId(),
				flow.name(),
				flow.hostBossBar().orElseThrow(),
				instance.runtime().completedCount(),
				instance.runtime().totalCount()
			);
		} else if (completionTransition && instance.runtime().status() == FlowInstanceStatus.COMPLETED) {
			HOST_FLOW_BOSS_BAR.showCompletion(
				server,
				state.host().map(WorldStateV2.HostRecord::playerId),
				instance.identity().instanceId(),
				flow.name(),
				flow.hostBossBar().orElseThrow(),
				instance.runtime().completedCount(),
				instance.runtime().totalCount()
			);
		} else {
			HOST_FLOW_BOSS_BAR.clear();
		}
	}

	private static void pushHostConsoleSnapshot(final MinecraftServer server) {
		PixelTzzWorldState.get(server)
			.hostId()
			.map(server.getPlayerList()::getPlayer)
			.filter(Objects::nonNull)
			.filter(PixelTzzServerRuntime::verified)
			.ifPresent(host -> sendConsoleSnapshot(server, host, 0L));
	}

	private static String hostName(
		final MinecraftServer server,
		final WorldStateV2 state,
		final WorldStateV2.HostRecord host
	) {
		ServerPlayer online = server.getPlayerList().getPlayer(host.playerId());
		if (online != null) {
			return online.getPlainTextName();
		}
		PlayerRecord player = state.players().get(host.playerId());
		return host.lastKnownName()
			.orElseGet(() -> player == null ? host.playerId().toString() : player.lastKnownName());
	}

	private static String jsonText(final String text) {
		JsonObject value = new JsonObject();
		value.addProperty("text", text);
		return value.toString();
	}

	private static void onPreviewCatalogRequest(
		final PreviewCatalogRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		if (!verified(context.player())) {
			return;
		}
		sendPreviewCatalog(context.player());
	}

	private static void sendPreviewCatalog(final ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, PreviewCatalogS2CPayload.TYPE)) {
			return;
		}
		View definitions = DefinitionRegistry.INSTANCE.view();
		if (!isAdminEligible(player)) {
			sendPreviewCatalog(player, definitions.active().generation(), "forbidden", "只有管理员可以打开页面预览器", List.of());
			return;
		}
		if (!definitions.healthy() || !definitions.active().usable()) {
			sendPreviewCatalog(
				player,
				definitions.active().generation(),
				"unavailable",
				truncateDiagnostic(definitions.diagnosticSummary()),
				List.of()
			);
			return;
		}

		List<PreviewCatalogS2CPayload.Entry> allEntries = definitions.active()
			.pages()
			.values()
			.stream()
			.sorted(Comparator.comparing((PageDefinition page) -> page.game().toString()).thenComparing(page -> page.id().toString()))
			.map(
				page -> new PreviewCatalogS2CPayload.Entry(
					page.id(),
					page.game(),
					truncate(page.title().plainText(), PreviewCatalogS2CPayload.MAX_TITLE_LENGTH)
				)
			)
			.toList();
		boolean truncated = allEntries.size() > PreviewCatalogS2CPayload.MAX_ENTRIES;
		List<PreviewCatalogS2CPayload.Entry> entries = truncated
			? allEntries.subList(0, PreviewCatalogS2CPayload.MAX_ENTRIES)
			: allEntries;
		sendPreviewCatalog(
			player,
			definitions.active().generation(),
			truncated ? "truncated" : "ready",
			truncated
				? "页面数量超过预览器目录上限，仅显示前 " + PreviewCatalogS2CPayload.MAX_ENTRIES + " 项"
				: entries.isEmpty() ? "当前健康 generation 没有注册页面" : "",
			entries
		);
	}

	private static void sendPreviewCatalog(
		final ServerPlayer player,
		final long generation,
		final String status,
		final String message,
		final List<PreviewCatalogS2CPayload.Entry> entries
	) {
		if (!ServerPlayNetworking.canSend(player, PreviewCatalogS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(
			player,
			new PreviewCatalogS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				generation,
				status,
				message,
				entries
			)
		);
	}

	private static void onPreviewPageRequest(
		final PreviewPageRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player)) {
			return;
		}
		View definitions = DefinitionRegistry.INSTANCE.view();
		if (
			!isAdminEligible(player)
				|| !definitions.healthy()
				|| !definitions.active().usable()
				|| !ServerPlayNetworking.canSend(player, PageBundleS2CPayload.TYPE)
		) {
			sendPreviewCatalog(player);
			return;
		}

		DefinitionSnapshot snapshot = definitions.active();
		PageDefinition page = snapshot.pages().get(payload.pageId());
		if (page == null) {
			sendPreviewCatalog(
				player,
				snapshot.generation(),
				"missing",
				"请求的页面不在当前健康 generation 中",
				List.of()
			);
			return;
		}
		ThemeDefinition theme = page.theme().equals(BuiltInUiResources.defaultTheme().id())
			? BuiltInUiResources.defaultTheme()
			: snapshot.themes().get(page.theme());
		if (theme == null) {
			sendPreviewCatalog(
				player,
				snapshot.generation(),
				"missing",
				"页面引用的主题不在当前健康 generation 中",
				List.of()
			);
			return;
		}

		long stateRevision = PixelTzzWorldState.get(context.server()).stateRevision();
		PreviewPageInstance instance = new PreviewPageInstance(
			UUID.randomUUID(),
			snapshot,
			page,
			theme,
			stateRevision
		);
		PREVIEW_INSTANCES.put(player.getUUID(), instance);
		byte[] pageHash = HexFormat.of().parseHex(page.sha256());
		byte[] themeHash = HexFormat.of().parseHex(theme.sha256());
		boolean documentsCached = payload.cachedDocuments()
			.filter(cached -> cached.generation() == snapshot.generation())
			.filter(cached -> payload.pageId().equals(page.id()))
			.filter(cached -> cached.themeId().equals(theme.id()))
			.filter(cached -> MessageDigest.isEqual(cached.pageHash(), pageHash))
			.filter(cached -> MessageDigest.isEqual(cached.themeHash(), themeHash))
			.isPresent();
		ServerPlayNetworking.send(
			player,
			new PageBundleS2CPayload(
				instance.id,
				snapshot.generation(),
				stateRevision,
				page.id(),
				theme.id(),
				pageHash,
				documentsCached
					? new byte[0]
					: page.canonicalDocument().getBytes(StandardCharsets.UTF_8),
				themeHash,
				documentsCached
					? new byte[0]
					: theme.canonicalDocument().getBytes(StandardCharsets.UTF_8),
				collectFieldSchemas(snapshot, page)
			)
		);
	}

	private static List<PageBundleS2CPayload.FieldSchema> collectFieldSchemas(
		final DefinitionSnapshot snapshot,
		final PageDefinition page
	) {
		Set<net.minecraft.resources.Identifier> fieldIds = new LinkedHashSet<>();
		collectFieldIds(page.root(), fieldIds);
		return fieldIds.stream()
			.map(snapshot.fields()::get)
			.filter(java.util.Objects::nonNull)
			.map(PixelTzzServerRuntime::fieldSchema)
			.toList();
	}

	private static List<PageBundleS2CPayload.FieldSchema> collectForcedFieldSchemas(
		final MinecraftServer server,
		final DefinitionSnapshot snapshot,
		final PageDefinition page,
		final ForcedFlowInstance instance,
		final FlowMemberState member,
		final PlayerRecord player
	) {
		if (player == null) {
			throw new IllegalArgumentException("forced-flow member lost its player record");
		}
		Set<Identifier> fieldIds = new LinkedHashSet<>();
		collectFieldIds(page.root(), fieldIds);
		PredicateContext context = new PredicateContext(
			member.playerId(),
			instance.identity().instanceId(),
			instance.identity().gameId(),
			instance.identity().flowId(),
			instance.identity().flowVersion(),
			instance.identity().phaseIdAtCreation(),
			member.flowFields(),
			player.persistentFields()
		);
		FrozenPredicateEvaluator evaluator = new FrozenPredicateEvaluator(
			server,
			instance.runtime().executionSnapshot()
		);
		return fieldIds.stream()
			.map(snapshot.fields()::get)
			.filter(Objects::nonNull)
			.filter(field ->
				ForcedFlowAuthority.playerMayEditField(
					field,
					player,
					instance.identity().phaseIdAtCreation(),
					evaluator,
					context
				)
			)
			.map(PixelTzzServerRuntime::fieldSchema)
			.toList();
	}

	private static void collectFieldIds(
		final NodeDefinition node,
		final Set<net.minecraft.resources.Identifier> result
	) {
		switch (node.content()) {
			case FieldInputContent input -> result.add(input.field());
			case ChildrenContent children -> children.children().forEach(child -> collectFieldIds(child, result));
			case SingleChildContent child -> collectFieldIds(child.child(), result);
			case RepeatContent repeat -> collectFieldIds(repeat.template(), result);
			default -> {
			}
		}
	}

	private static PageBundleS2CPayload.FieldSchema fieldSchema(final FieldDefinition field) {
		return new PageBundleS2CPayload.FieldSchema(
			field.id(),
			field.type().name().toLowerCase(java.util.Locale.ROOT),
			truncate(field.name().plainText(), PageBundleS2CPayload.MAX_FIELD_NAME_LENGTH),
			truncate(
				field.description().map(description -> description.plainText()).orElse(""),
				PageBundleS2CPayload.MAX_FIELD_DESCRIPTION_LENGTH
			),
			field.required(),
			field.defaultJson(),
			optionalLong(field.constraints().minimum()),
			optionalLong(field.constraints().maximum()),
			optionalInt(field.constraints().minimumLength()),
			optionalInt(field.constraints().maximumLength()),
			field.constraints().options(),
			optionalInt(field.constraints().minimumSelections()),
			optionalInt(field.constraints().maximumSelections())
		);
	}

	private static OptionalLong optionalLong(final java.util.Optional<Long> value) {
		return value.isPresent() ? OptionalLong.of(value.orElseThrow()) : OptionalLong.empty();
	}

	private static OptionalInt optionalInt(final java.util.Optional<Integer> value) {
		return value.isPresent() ? OptionalInt.of(value.orElseThrow()) : OptionalInt.empty();
	}

	private static void onPageClose(
		final PageCloseC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		PreviewPageInstance instance = PREVIEW_INSTANCES.get(context.player().getUUID());
		if (instance != null && instance.id.equals(payload.instanceId())) {
			PREVIEW_INSTANCES.remove(context.player().getUUID());
		}
	}

	private static void onResourceReport(
		final ResourceReportC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		if (
			!verified(context.player())
				|| !RESOURCE_REPORT_GATE.admit(
					context.player().getUUID(),
					System.currentTimeMillis()
				)
		) {
			return;
		}
		PreviewPageInstance instance = PREVIEW_INSTANCES.get(context.player().getUUID());
		if (
			instance != null
				&& instance.id.equals(payload.instanceId())
				&& instance.snapshot.generation() == payload.generation()
				&& instance.page.id().equals(payload.pageId())
		) {
			onPreviewResourceReport(payload, context, instance);
			return;
		}
		onForcedResourceReport(payload, context);
	}

	private static void onPreviewResourceReport(
		final ResourceReportC2SPayload payload,
		final ServerPlayNetworking.Context context,
		final PreviewPageInstance instance
	) {
		int currentTick = context.server().getTickCount();
		Set<ResourceKey> expected = new HashSet<>();
		instance.page.assets().forEach(asset -> expected.add(ResourceKey.of(asset)));
		instance.theme.assets().forEach(asset -> expected.add(ResourceKey.of(asset)));
		if (payload.totalMissing() > expected.size()) {
			PixelTzzPro.LOGGER.warn(
				"Ignored impossible Pixel TZZ resource report from {} for {}: {} missing exceeds {} expected reference(s)",
				context.player().getPlainTextName(),
				payload.pageId(),
				payload.totalMissing(),
				expected.size()
			);
			return;
		}
		List<ResourceReportC2SPayload.Entry> recognized = payload.entries()
			.stream()
			.filter(entry -> expected.contains(ResourceKey.of(entry)))
			.toList();
		PreviewResourceReport report = new PreviewResourceReport(payload.totalMissing(), recognized);
		if (
			instance.lastResourceReportTick != Integer.MIN_VALUE
				&& currentTick - instance.lastResourceReportTick < 20
		) {
			instance.pendingResourceReport = report;
			instance.pendingResourceReportDueTick = instance.lastResourceReportTick + 20;
			return;
		}
		instance.pendingResourceReport = null;
		instance.lastResourceReportTick = currentTick;
		applyResourceReport(instance, report, context.player().getPlainTextName());
	}

	private static void onForcedResourceReport(
		final ResourceReportC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		ForcedFlowInstance flow = state == null
			? null
			: state.activeForcedFlow().orElse(null);
		if (
			flow == null
				|| (
					flow.runtime().status() != FlowInstanceStatus.ACTIVE
						&& !FlowBlockAuthority.isRecoverableResourceBlock(flow)
				)
				|| flow.identity().definitionGeneration() != payload.generation()
		) {
			return;
		}
		FlowMemberState member = flow.runtime().members().get(context.player().getUUID());
		if (
			member == null
				|| member.pageInstanceId().filter(payload.instanceId()::equals).isEmpty()
				|| member.currentNodeId().isEmpty()
		) {
			return;
		}
		var restored = ExecutionSnapshotCompiler.restore(
			flow.identity().flowId(),
			flow.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			persistFlowBlock(
				context.server(),
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				Optional.of(context.player().getUUID())
			);
			return;
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition definition = definitions.flows().get(flow.identity().flowId());
		var node = definition == null
			? null
			: definition.nodes().get(member.currentNodeId().orElseThrow());
		Identifier pageId = switch (node) {
			case PageNode page -> page.page();
			case ChoiceNode choice -> choice.page();
			case ConfirmNode confirm -> confirm.page();
			default -> null;
		};
		if (pageId == null || !pageId.equals(payload.pageId())) {
			return;
		}
		PageDefinition page = definitions.pages().get(pageId);
		if (page == null) {
			persistFlowBlock(
				context.server(),
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"current forced page is unavailable in the frozen snapshot",
				Optional.of(context.player().getUUID())
			);
			return;
		}
		ThemeDefinition theme = page.theme().equals(BuiltInUiResources.defaultTheme().id())
			? BuiltInUiResources.defaultTheme()
			: definitions.themes().get(page.theme());
		if (theme == null) {
			persistFlowBlock(
				context.server(),
				wrapper,
				state,
				OperationCode.SNAPSHOT_INVALID,
				"current forced page theme is unavailable in the frozen snapshot",
				Optional.of(context.player().getUUID())
			);
			return;
		}

		Map<ResourceKey, AssetReference> expected = new LinkedHashMap<>();
		page.assets().forEach(asset -> expected.putIfAbsent(ResourceKey.of(asset), asset));
		theme.assets().forEach(asset -> expected.putIfAbsent(ResourceKey.of(asset), asset));
		if (payload.totalMissing() > expected.size()) {
			PixelTzzPro.LOGGER.warn(
				"Ignored impossible forced-page resource report from {} for {}: {} missing exceeds {} expected reference(s)",
				context.player().getPlainTextName(),
				payload.pageId(),
				payload.totalMissing(),
				expected.size()
			);
			return;
		}
		List<ResourceReportC2SPayload.Entry> recognized = payload.entries()
			.stream()
			.filter(entry -> expected.containsKey(ResourceKey.of(entry)))
			.toList();
		if (payload.requiredMissing()) {
			Optional<ResourceReportC2SPayload.Entry> required = recognized.stream()
				.filter(entry -> expected.get(ResourceKey.of(entry)).required())
				.findFirst();
			if (required.isEmpty()) {
				PixelTzzPro.LOGGER.warn(
					"Ignored unproven required-resource report from {} for {}",
					context.player().getPlainTextName(),
					payload.pageId()
				);
				return;
			}
			ResourceReportC2SPayload.Entry missing = required.orElseThrow();
			persistFlowBlock(
				context.server(),
				wrapper,
				state,
				OperationCode.RESOURCE_BLOCKED,
				FlowBlockAuthority.CLIENT_PAGE_DIAGNOSTIC_PREFIX
					+ "required asset missing on "
					+ payload.pageId()
					+ ": "
					+ missing.type()
					+ " "
					+ missing.id()
					+ " at "
					+ missing.jsonPointer()
					+ " ("
					+ payload.totalMissing()
					+ " missing)",
				Optional.of(context.player().getUUID())
			);
			return;
		}
		if (payload.renderError().isPresent()) {
			persistFlowBlock(
				context.server(),
				wrapper,
				state,
				OperationCode.RESOURCE_BLOCKED,
				FlowBlockAuthority.CLIENT_PAGE_DIAGNOSTIC_PREFIX
					+ "render failed on "
					+ payload.pageId()
					+ ": "
					+ payload.renderError().orElseThrow(),
				Optional.of(context.player().getUUID())
			);
			return;
		}
		if (payload.renderReady() && FlowBlockAuthority.isRecoverableResourceBlock(flow)) {
			FlowBlockAuthority.Result recovery = FlowBlockAuthority.recoverResourceBlock(
				state,
				context.player().getUUID(),
				System.currentTimeMillis()
			);
			if (recovery.changed()) {
				PixelTzzWorldState.CommitResult committed = wrapper.commit(
					state.stateRevision(),
					ignored -> recovery.nextState().orElseThrow()
				);
				if (committed.committed()) {
					refreshFlowProjection(context.server(), false);
					sendSnapshots(context.server(), false);
					sendCurrentForcedPage(context.server(), context.player());
				}
			}
		}
	}

	private static void applyResourceReport(
		final PreviewPageInstance instance,
		final PreviewResourceReport report,
		final String reporter
	) {
		instance.resourceReport = report;
		if (report.totalMissing() > 0) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ preview resource report from {} for {}: {} missing, {} recognized detail(s)",
				reporter,
				instance.page.id(),
				report.totalMissing(),
				report.recognizedEntries().size()
			);
		}
	}

	private static void onServerTick(final MinecraftServer server) {
		int currentTick = server.getTickCount();
		HOST_FLOW_BOSS_BAR.tick(server, PixelTzzWorldState.get(server).hostId());
		if (hostConsoleRefreshPending) {
			hostConsoleRefreshPending = false;
			pushHostConsoleSnapshot(server);
		}
		for (Map.Entry<UUID, PreviewPageInstance> entry : PREVIEW_INSTANCES.entrySet()) {
			PreviewPageInstance instance = entry.getValue();
			if (
				instance.pendingResourceReport == null
					|| currentTick < instance.pendingResourceReportDueTick
			) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			String reporter = player == null ? entry.getKey().toString() : player.getPlainTextName();
			PreviewResourceReport pending = instance.pendingResourceReport;
			instance.pendingResourceReport = null;
			instance.lastResourceReportTick = currentTick;
			applyResourceReport(instance, pending, reporter);
		}
		HANDSHAKE_TICKS_REMAINING.replaceAll((playerId, ticksRemaining) -> ticksRemaining - 1);
		List<UUID> expiredHandshakes = HANDSHAKE_TICKS_REMAINING
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue() <= 0)
			.map(Map.Entry::getKey)
			.toList();
		for (UUID playerId : expiredHandshakes) {
			HANDSHAKE_TICKS_REMAINING.remove(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				player.connection.disconnect(
					Component.literal("全员逃走中-扩展客户端握手超时，请确认模组版本后重新连接。")
				);
			}
		}

		if (currentTick % PERMISSION_REFRESH_INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			if (!VERIFIED_PLAYERS.contains(playerId)) {
				continue;
			}

			boolean adminEligible = isAdminEligible(player);
			Boolean previousEligibility = LAST_ADMIN_ELIGIBILITY.get(playerId);
			if (previousEligibility != null && previousEligibility != adminEligible) {
				sendSnapshot(server, player, false);
			}
		}
	}

	private static void onDataPackReloaded(
		final MinecraftServer server,
		final net.minecraft.server.packs.resources.CloseableResourceManager resourceManager,
		final boolean success
	) {
		int canceledConfirmations = CONFIRMATIONS.clear();
		if (canceledConfirmations > 0) {
			persistConfirmationAudit(
				PixelTzzWorldState.get(server),
				AuditEventType.CONFIRMATION_CANCELED,
				Optional.empty(),
				Optional.empty(),
				"data-pack reload canceled " + canceledConfirmations + " pending confirmations",
				System.currentTimeMillis()
			);
		}
		if (!success) {
			PixelTzzPro.LOGGER.warn("Data-pack reload failed; keeping the previous Pixel TZZ state and resources");
			DefinitionRegistry.INSTANCE.markPlatformReloadFailed();
			sendSnapshots(server, false);
			pushHostConsoleSnapshot(server);
			sendForcedPages(server);
			return;
		}

		ReloadOutcome definitionLoad = DefinitionRegistry.INSTANCE.reload(resourceManager);
		PixelTzzWorldState state = PixelTzzWorldState.get(server);
		boolean animateLoad = state.isSchemaCompatible()
			&& definitionLoad.applied()
			&& definitionLoad.healthy()
			&& definitionLoad.activeUsable();
		if (!state.isSchemaCompatible()) {
			PixelTzzPro.LOGGER.warn("Resources reloaded while Pixel TZZ world state remains read-only");
		}

		if (definitionLoad.applied()) {
			loadSequence = Math.incrementExact(loadSequence);
		}
		if (definitionLoad.healthy() && definitionLoad.activeUsable()) {
			initializeNewWorldActivity(server);
			server.getPlayerList()
				.getPlayers()
				.stream()
				.filter(PixelTzzServerRuntime::verified)
				.forEach(player -> ensurePlayerRegistered(server, player));
		}
		sendSnapshots(server, animateLoad);
		pushHostConsoleSnapshot(server);
		// A reload must preserve the active frozen flow and reassert its mandatory page.
		sendForcedPages(server);

		if (definitionLoad.applied()) {
			PixelTzzPro.LOGGER.info(
				"Data packs reloaded; Pixel TZZ definition status={}, load sequence={}",
				definitionLoad.status().serializedName(),
				loadSequence
			);
		} else {
			PixelTzzPro.LOGGER.warn(
				"Data packs reloaded, but Pixel TZZ definitions were rejected; generation {} remains active",
				definitionLoad.generation()
			);
		}
	}

	private static void sendSnapshots(final MinecraftServer server, final boolean animateLoad) {
		refreshTabListDisplay(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (VERIFIED_PLAYERS.contains(player.getUUID()) && ServerPlayNetworking.canSend(player, SessionSnapshotS2CPayload.TYPE)) {
				sendSnapshot(server, player, animateLoad);
			}
		}
	}

	private static void refreshTabListDisplay(final MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		server.getPlayerList().broadcastAll(
			new ClientboundPlayerInfoUpdatePacket(
				EnumSet.of(
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER
				),
				players
			)
		);
	}

	private static void sendSnapshot(
		final MinecraftServer server,
		final ServerPlayer player,
		final boolean animateLoad
	) {
		PixelTzzWorldState state = PixelTzzWorldState.get(server);
		boolean hostAssigned = state.hostId().isPresent();
		boolean currentPlayerHost = state.hostId().filter(player.getUUID()::equals).isPresent();
		boolean adminEligible = isAdminEligible(player);
		View definitions = DefinitionRegistry.INSTANCE.view();
		LAST_ADMIN_ELIGIBILITY.put(player.getUUID(), adminEligible);
		ServerPlayNetworking.send(
			player,
			new SessionSnapshotS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				state.activePhaseId()
					.map(Identifier::getPath)
					.flatMap(GamePhase::bySerializedName)
					.orElse(GamePhase.IDLE)
					.getSerializedName(),
				adminEligible,
				hostAssigned,
				currentPlayerHost,
				state.isSchemaCompatible(),
				state.stateRevision(),
				loadSequence,
				definitions.status().serializedName(),
				definitions.healthy(),
				definitions.active().generation(),
				definitions.active().games().size(),
				definitions.active().definitionCount(),
				truncateDiagnostic(definitions.diagnosticSummary()),
				animateLoad
					&& state.isSchemaCompatible()
					&& definitions.canStartNewFlows()
			)
		);
	}

	private static String truncateDiagnostic(final String diagnostic) {
		return diagnostic.length() <= 1_024 ? diagnostic : diagnostic.substring(0, 1_021) + "...";
	}

	private static boolean persistConfirmationAudit(
		final PixelTzzWorldState wrapper,
		final AuditEventType type,
		final Optional<UUID> actorId,
		final Optional<Binding> binding,
		final String detail,
		final long occurredAtEpochMillis
	) {
		Objects.requireNonNull(wrapper, "wrapper");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(actorId, "actorId");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(detail, "detail");
		WorldStateV2 current = wrapper.currentV2().orElse(null);
		if (current == null) {
			return false;
		}
		PixelTzzWorldState.CommitResult committed = wrapper.commit(
			current.stateRevision(),
			state -> withConfirmationAudit(
				state,
				type,
				actorId,
				binding,
				detail,
				occurredAtEpochMillis
			)
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.warn(
				"Could not persist {} confirmation audit: {}",
				type.getSerializedName(),
				committed.reason()
			);
		}
		return committed.committed();
	}

	private static WorldStateV2 withConfirmationAudit(
		final WorldStateV2 state,
		final AuditEventType type,
		final Optional<UUID> actorId,
		final Optional<Binding> binding,
		final String detail,
		final long occurredAtEpochMillis
	) {
		String operation = binding
			.map(value -> value.operation().type() + "/" + value.operation().id())
			.orElse("lifecycle");
		int targetCount = binding.map(value -> value.targetIds().size()).orElse(0);
		String summary = truncate(
			operation + " targets=" + targetCount + "; " + detail,
			1_024
		);
		Optional<UUID> targetPlayerId = binding
			.map(Binding::targetIds)
			.filter(targets -> targets.size() == 1)
			.map(List::getFirst);
		Optional<UUID> flowInstanceId = state.activeForcedFlow()
			.map(ForcedFlowInstance::identity)
			.map(WorldStateV2.ForcedFlowIdentity::instanceId);
		return state.withAudit(
			state.audit().append(
				new AuditEvent(
					state.audit().totalEvents(),
					type,
					occurredAtEpochMillis,
					actorId,
					targetPlayerId,
					flowInstanceId,
					summary
				)
			)
		);
	}

	private static String truncate(final String value, final int maximumLength) {
		return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
	}

	private static boolean verified(final ServerPlayer player) {
		return VERIFIED_PLAYERS.contains(player.getUUID());
	}

	private static boolean isAdminEligible(final ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static boolean canViewConsole(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		return isAdminEligible(player)
			|| PixelTzzWorldState.get(server).hostId().filter(player.getUUID()::equals).isPresent();
	}

	private static void onPlayerDisconnected(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		updateMemberConnection(server, player.getUUID(), player.getPlainTextName(), false);
		removeConnection(server, player.getUUID());
		refreshFlowProjection(server, false);
		sendSnapshots(server, false);
		pushHostConsoleSnapshot(server);
		// The disconnect event fires before vanilla removes this player from its online list.
		hostConsoleRefreshPending = true;
	}

	private static void markDisconnectedMembersOffline(final MinecraftServer server) {
		PixelTzzWorldState.get(server)
			.currentV2()
			.flatMap(WorldStateV2::activeForcedFlow)
			.stream()
			.flatMap(flow -> flow.runtime().members().values().stream())
			.filter(member ->
				member.status() == FlowMemberStatus.IN_PROGRESS
					|| member.status() == FlowMemberStatus.BLOCKED
			)
			.map(FlowMemberState::playerId)
			.toList()
			.forEach(playerId -> updateMemberConnection(server, playerId, "", false));
	}

	private static void restoreMemberOnline(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		updateMemberConnection(server, player.getUUID(), player.getPlainTextName(), true);
	}

	private static boolean ensurePlayerRegistered(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		if (state == null || state.players().containsKey(player.getUUID())) {
			return false;
		}
		try {
			PlayerRecord record = ForcedFlowAuthority.currentPlayer(
				state,
				DefinitionRegistry.INSTANCE.view().active(),
				new ConnectedPlayer(
					player.getUUID(),
					player.getPlainTextName(),
					true,
					true
				),
				System.currentTimeMillis()
			);
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			players.put(record.playerId(), record);
			PixelTzzWorldState.CommitResult committed = wrapper.commit(
				state.stateRevision(),
				current -> current.withPlayers(players)
			);
			if (!committed.committed()) {
				PixelTzzPro.LOGGER.warn(
					"Could not register verified Pixel TZZ player {}: {}",
					player.getUUID(),
					committed.reason()
				);
			}
			return committed.committed();
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.error(
				"Could not register verified Pixel TZZ player {}",
				player.getUUID(),
				error
			);
			return false;
		}
	}

	private static boolean updateMemberConnection(
		final MinecraftServer server,
		final UUID playerId,
		final String currentName,
		final boolean online
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		if (state == null) {
			return false;
		}
		ForcedFlowInstance instance = state.activeForcedFlow().orElse(null);
		if (
			instance == null
				|| (
					instance.runtime().status() != FlowInstanceStatus.ACTIVE
						&& instance.runtime().status() != FlowInstanceStatus.BLOCKED
				)
		) {
			return false;
		}
		FlowMemberState member = instance.runtime().members().get(playerId);
		boolean recoverableBlockedMember = member != null
			&& member.status() == FlowMemberStatus.BLOCKED
			&& FlowBlockAuthority.isRecoverableResourceBlock(instance)
			&& instance.runtime()
				.blockDiagnostic()
				.flatMap(WorldStateV2.BlockDiagnostic::playerId)
				.filter(playerId::equals)
				.isPresent();
		if (
			member == null
				|| (
					recoverableBlockedMember
						? member.offline() != online
						: online
							? member.status() != FlowMemberStatus.OFFLINE || !member.offline()
							: member.status() != FlowMemberStatus.IN_PROGRESS || member.offline()
				)
		) {
			return false;
		}
		try {
			long now = System.currentTimeMillis();
			FlowMemberStatus nextStatus = recoverableBlockedMember
				? FlowMemberStatus.BLOCKED
				: online ? FlowMemberStatus.IN_PROGRESS : FlowMemberStatus.OFFLINE;
			FlowMemberState nextMember = new FlowMemberState(
				member.playerId(),
				member.lockedName(),
				member.currentNodeId(),
				nextStatus,
				Math.incrementExact(member.memberRevision()),
				member.flowFields(),
				online ? Optional.of(UUID.randomUUID()) : member.pageInstanceId(),
				member.requestSequenceWindow(),
				member.lastCommittedAtEpochMillis(),
				!online,
				Optional.of(now),
				member.completedAtEpochMillis(),
				member.removal()
			);
			Map<UUID, FlowMemberState> members = new LinkedHashMap<>(
				instance.runtime().members()
			);
			members.put(playerId, nextMember);
			ForcedFlowRuntime runtime = new ForcedFlowRuntime(
				instance.runtime().status(),
				members,
				instance.runtime().completedCount(),
				instance.runtime().totalCount(),
				Math.incrementExact(instance.runtime().instanceRevision()),
				instance.runtime().executionSnapshot(),
				instance.runtime().callbacks(),
				instance.runtime().blockDiagnostic(),
				now
			);
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.players());
			PlayerRecord record = players.get(playerId);
			if (record != null) {
				players.put(
					playerId,
					new PlayerRecord(
						record.playerId(),
						online && !currentName.isBlank()
							? currentName
							: record.lastKnownName(),
						record.roleId(),
						record.teamId(),
						record.lifeStateId(),
						record.persistentFields(),
						record.pendingRoleChange(),
						Optional.of(
							new PlayerFlowParticipation(
								instance.identity().instanceId(),
								nextStatus
							)
						),
						record.completionSummary(),
						record.firstSeenAtEpochMillis(),
						Math.max(now, record.lastSeenAtEpochMillis())
					)
				);
			}
			PixelTzzWorldState.CommitResult committed = wrapper.commit(
				state.stateRevision(),
				ignored -> state
					.withPlayers(players)
					.withActiveForcedFlow(
						Optional.of(new ForcedFlowInstance(instance.identity(), runtime))
					)
			);
			if (!committed.committed()) {
				PixelTzzPro.LOGGER.warn(
					"Could not persist {} member {}: {}",
					online ? "reconnected" : "offline",
					playerId,
					committed.reason()
				);
			}
			return committed.committed();
		} catch (ArithmeticException | IllegalArgumentException error) {
			PixelTzzPro.LOGGER.error(
				"Could not update Pixel TZZ member connection state for {}",
				playerId,
				error
			);
			return false;
		}
	}

	private static void removeConnection(
		final MinecraftServer server,
		final UUID playerId
	) {
		HANDSHAKE_TICKS_REMAINING.remove(playerId);
		VERIFIED_PLAYERS.remove(playerId);
		LAST_ADMIN_ELIGIBILITY.remove(playerId);
		PREVIEW_INSTANCES.remove(playerId);
		HOST_FLOW_BOSS_BAR.removeViewer(playerId);
		Optional<Binding> canceledBinding = CONFIRMATIONS.pendingBinding(playerId);
		if (
			CONFIRMATIONS.cancelOperator(playerId)
				== ConfirmationTokens.CancelResult.CANCELED
		) {
			persistConfirmationAudit(
				PixelTzzWorldState.get(server),
				AuditEventType.CONFIRMATION_CANCELED,
				Optional.of(playerId),
				canceledBinding,
				"connection lifecycle canceled the pending confirmation",
				System.currentTimeMillis()
			);
		}
		CONTROL_REQUEST_GATE.remove(playerId);
		FLOW_ACTION_REQUEST_GATE.remove(playerId);
		RESOURCE_REPORT_GATE.remove(playerId);
	}

	private static void clearConnections() {
		HANDSHAKE_TICKS_REMAINING.clear();
		VERIFIED_PLAYERS.clear();
		LAST_ADMIN_ELIGIBILITY.clear();
		PREVIEW_INSTANCES.clear();
		hostConsoleRefreshPending = false;
		CONTROL_REQUEST_GATE.clear();
		FLOW_ACTION_REQUEST_GATE.clear();
		RESOURCE_REPORT_GATE.clear();
	}

	private record CallbackDispatch(OperationCode code, String message) {
		private CallbackDispatch {
			Objects.requireNonNull(code, "code");
			Objects.requireNonNull(message, "message");
		}

		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static CallbackDispatch succeeded(final String message) {
			return new CallbackDispatch(OperationCode.SUCCESS, message);
		}

		private static CallbackDispatch failed(
			final OperationCode code,
			final String message
		) {
			return new CallbackDispatch(code, message);
		}
	}

	private record CallbackRetryKey(
		CallbackKind kind,
		Optional<UUID> playerId
	) {
		private CallbackRetryKey {
			Objects.requireNonNull(kind, "kind");
			playerId = Objects.requireNonNull(playerId, "playerId");
			if ((kind == CallbackKind.ON_PLAYER_COMPLETE) != playerId.isPresent()) {
				throw new IllegalArgumentException("callback retry target shape is invalid");
			}
		}
	}

	private static final class PreviewPageInstance {
		private final UUID id;
		private final DefinitionSnapshot snapshot;
		private final PageDefinition page;
		private final ThemeDefinition theme;
		private final long stateRevision;
		private int lastResourceReportTick = Integer.MIN_VALUE;
		private int pendingResourceReportDueTick;
		private PreviewResourceReport resourceReport = PreviewResourceReport.empty();
		private PreviewResourceReport pendingResourceReport;

		private PreviewPageInstance(
			final UUID id,
			final DefinitionSnapshot snapshot,
			final PageDefinition page,
			final ThemeDefinition theme,
			final long stateRevision
		) {
			this.id = id;
			this.snapshot = snapshot;
			this.page = page;
			this.theme = theme;
			this.stateRevision = stateRevision;
		}
	}

	private record PreviewResourceReport(
		int totalMissing,
		List<ResourceReportC2SPayload.Entry> recognizedEntries
	) {
		private PreviewResourceReport {
			recognizedEntries = List.copyOf(recognizedEntries);
		}

		private static PreviewResourceReport empty() {
			return new PreviewResourceReport(0, List.of());
		}
	}

	private record ResourceKey(String type, String id, String pointer) {
		private static ResourceKey of(final AssetReference asset) {
			return new ResourceKey(asset.type().name().toLowerCase(java.util.Locale.ROOT), asset.id(), asset.pointer());
		}

		private static ResourceKey of(final ResourceReportC2SPayload.Entry entry) {
			return new ResourceKey(entry.type(), entry.id(), entry.jsonPointer());
		}
	}
}
