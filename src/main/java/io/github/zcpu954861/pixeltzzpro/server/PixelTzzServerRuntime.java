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
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ExclusiveChoiceOption;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FlowNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FunctionNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ReadinessDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TransitionPhaseOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.WaitPlayersNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageHookDefinitions.MessageHookEvent;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskResult;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ActionType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
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
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.MemberFieldSummary;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.MemberSummary;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.PhaseEntry;
import io.github.zcpu954861.pixeltzzpro.network.payload.ExclusiveChoiceMutationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostUiStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.ExclusiveOptionState;
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
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalBindingDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalInvalidationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalIntentC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalOpenC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
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
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ExclusiveChoiceMutation;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ExclusiveChoiceMutationOperation;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.ExclusiveChoiceMutationResult;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PanelActionAuthorization;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateContext;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.Actor;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.OnlineTarget;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ActionRequest;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ConfirmedRequest;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.ExecutionResult;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Finalization;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.Preparation;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.PreparedAction;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.PushPage;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.RunFunction;
import io.github.zcpu954861.pixeltzzpro.server.PlayerActionAuthority.SessionAuthorization;
import io.github.zcpu954861.pixeltzzpro.server.TimelineApprovalAuthority.ApprovalContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHookDispatchContract;
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
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReadinessInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContextDocument;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
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
	private static final Map<UUID, Integer> LAST_TIMELINE_VIEW_REQUEST_TICK = new HashMap<>();
	// ponytail: 2B permits one preview per player; replace this map with a page stack when real nested flows arrive.
	private static final Map<UUID, PreviewPageInstance> PREVIEW_INSTANCES = new HashMap<>();
	private static final PlayerTerminalSessions PLAYER_TERMINAL_SESSIONS =
		new PlayerTerminalSessions();
	private static final ControlRequestGate TERMINAL_OPEN_REQUEST_GATE =
		new ControlRequestGate();
	private static final Map<UUID, String> LAST_TERMINAL_BINDING_HASHES =
		new HashMap<>();
	private static final Map<UUID, PendingPlayerActionConfirmation>
		PLAYER_ACTION_CONFIRMATIONS = new HashMap<>();
	private static final Map<UUID, PendingTerminalHostConfirmation>
		TERMINAL_HOST_CONFIRMATIONS = new HashMap<>();
	private static final ConfirmationTokens CONFIRMATIONS = new ConfirmationTokens();
	private static final ControlRequestGate CONTROL_REQUEST_GATE = new ControlRequestGate();
	private static final FlowActionRequestGate FLOW_ACTION_REQUEST_GATE =
		new FlowActionRequestGate();
	private static final ResourceReportGate RESOURCE_REPORT_GATE = new ResourceReportGate();
	private static final HostFlowBossBar HOST_FLOW_BOSS_BAR = new HostFlowBossBar();
	private static boolean hostConsoleRefreshPending;
	private static boolean serverStopping;
	private static int lastTerminalProjectionTick = Integer.MIN_VALUE;
	private static TerminalFrozenSnapshot terminalFrozenSnapshot;
	private static final int TERMINAL_PROJECTION_INTERVAL_TICKS = 20;
	private static final String PLAYER_ACTION_OPERATION_TYPE = "player_action";
	private static final String TERMINAL_HOST_CONTROL_NODE_ID = "system_host_control";
	private static final Identifier CLAIM_HOST_OPERATION = PixelTzzPro.id("host/claim");
	private static final Identifier TRANSFER_HOST_OPERATION = PixelTzzPro.id("host/transfer");
	private static final Identifier TAKEOVER_HOST_OPERATION = PixelTzzPro.id("host/takeover");
	private static final Identifier ADD_FLOW_MEMBERS_OPERATION = PixelTzzPro.id("flow/add_members");
	private static final Identifier REMOVE_FLOW_MEMBERS_OPERATION = PixelTzzPro.id("flow/remove_members");
	private static final Identifier CANCEL_FLOW_OPERATION = PixelTzzPro.id("flow/cancel");
	private static final Identifier APPROVE_TIMELINE_OPERATION =
		PixelTzzPro.id("timeline/approve");
	private static final Identifier RESET_GAME_PROGRESS_OPERATION =
		PixelTzzPro.id("reset/game_progress");
	private static final Identifier CLEAR_ALL_STATE_OPERATION =
		PixelTzzPro.id("reset/all");
	private static final Identifier PAUSE_TIMELINE_OPERATION =
		PixelTzzPro.id("timeline/pause");
	private static final Identifier RESUME_TIMELINE_OPERATION =
		PixelTzzPro.id("timeline/resume");
	private static final Identifier EXTEND_INTERMISSION_OPERATION =
		PixelTzzPro.id("timeline/intermission/extend");
	private static final Identifier FINISH_INTERMISSION_OPERATION =
		PixelTzzPro.id("timeline/intermission/finish");
	private static final Identifier INTERRUPT_TIMELINE_OPERATION =
		PixelTzzPro.id("timeline/interrupt");
	private static final String ADD_FLOW_MEMBERS = "add_flow_members";
	private static final String REMOVE_FLOW_MEMBERS = "remove_flow_members";
	private static final String CANCEL_FLOW = "cancel_flow";
	private static final String APPROVE_TIMELINE = "approve_timeline";
	private static final String RESET_GAME_PROGRESS = "reset_game_progress";
	private static final String CLEAR_ALL_STATE = "clear_all_state";
	private static final String PAUSE_TIMELINE = "pause_timeline";
	private static final String RESUME_TIMELINE = "resume_timeline";
	private static final String EXTEND_INTERMISSION = "extend_intermission";
	private static final String FINISH_INTERMISSION = "finish_intermission";
	private static final String INTERRUPT_TIMELINE = "interrupt_timeline";
	private static final String RETRY_TIMELINE_CALLBACK = "retry_timeline_callback";
	private static final String HOST_FALLBACK_RESULT = "host_fallback_result";
	private static final String TIMELINE_CALLBACK_RETRY_PATH_PREFIX =
		"timeline/callback/retry/";
	private static final String TIMELINE_RESULT_PATH_PREFIX = "timeline/result/";
	private static final long INTERMISSION_EXTENSION_TICKS = 1_200L;
	private static final String HOST_PAUSE_REASON = "pixel_tzz:host_control";
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

	static void timelineStateChanged(final MinecraftServer server) {
		hostConsoleRefreshPending = true;
		pushTimelineViews(server);
		sendSnapshots(server, false);
		refreshTabListDisplay(server);
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(PixelTzzServerRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> serverStopping = true);
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
			ExclusiveChoiceMutationC2SPayload.TYPE,
			PixelTzzServerRuntime::onExclusiveChoiceMutation
		);
		ServerPlayNetworking.registerGlobalReceiver(
			HostUiStateC2SPayload.TYPE,
			(payload, context) -> {
				ServerPlayer player = context.player();
				if (!verified(player)) {
					return;
				}
				HOST_FLOW_BOSS_BAR.setViewerInConsole(
					player.getUUID(),
					payload.visible() && canViewConsole(context.server(), player)
				);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			TimelineViewRequestC2SPayload.TYPE,
			PixelTzzServerRuntime::onTimelineViewRequest
		);
		ServerPlayNetworking.registerGlobalReceiver(
			TerminalOpenC2SPayload.TYPE,
			PixelTzzServerRuntime::onTerminalOpen
		);
		ServerPlayNetworking.registerGlobalReceiver(
			TerminalIntentC2SPayload.TYPE,
			PixelTzzServerRuntime::onTerminalIntent
		);
	}

	private static void onServerStarted(final MinecraftServer server) {
		serverStopping = false;
		clearConnections();
		CONFIRMATIONS.clear();
		HOST_FLOW_BOSS_BAR.reset();
		loadSequence = 0L;
		MessageServerRuntime.initializeForServer(server);
		ReloadOutcome definitionLoad = DefinitionRegistry.INSTANCE.reload(server.getResourceManager());
		MessageServerRuntime.rebuildAssetAuthority(
			server,
			DefinitionRegistry.INSTANCE.view().active()
		);
		if (!definitionLoad.healthy()) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ game definitions are not ready at server start: {}",
				definitionLoad.status().serializedName()
			);
		}
		repairMissingTimelineSnapshot(server, DefinitionRegistry.INSTANCE.view());
		PixelTzzWorldStateMigration.migrateOnServerStart(
			server,
			DefinitionRegistry.INSTANCE.view()
		);
		initializeNewWorldActivity(server);
		MessageServerRuntime.recoverPersistedRuntime(
			server,
			DefinitionRegistry.INSTANCE.view().active()
		);
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

	private static void repairMissingTimelineSnapshot(
		final MinecraftServer server,
		final View definitions
	) {
		PixelTzzWorldState state = PixelTzzWorldState.get(server);
		if (!state.needsTimelineSnapshotRepair()) {
			return;
		}
		WorldStateV3 root = state.currentV3().orElseThrow();
		WorldStateV3.TimelineInstance timeline = root.timeline().orElseThrow();
		GameDefinitions.GameDefinition game = definitions.active().games().get(timeline.gameId());
		if (!definitions.healthy() || game == null) {
			PixelTzzPro.LOGGER.error(
				"Protected timeline snapshot cannot be rebuilt because game definitions are unavailable: game={}, definitions={}",
				timeline.gameId(),
				definitions.diagnosticSummary()
			);
			return;
		}
		TimelineSnapshotCompiler.FreezeResult frozen = TimelineSnapshotCompiler.freeze(
			definitions.active(),
			game
		);
		if (!frozen.success()) {
			PixelTzzPro.LOGGER.error(
				"Protected timeline snapshot could not be rebuilt: {} ({})",
				frozen.message(),
				frozen.code()
			);
			return;
		}
		PixelTzzWorldState.TimelineSnapshotRepairResult repaired = state
			.repairMissingTimelineSnapshot(frozen.frozen().orElseThrow());
		if (!repaired.committed()) {
			PixelTzzPro.LOGGER.error(
				"Protected timeline snapshot did not pass exact SHA-256 recovery: {}",
				repaired.reason()
			);
			return;
		}
		PixelTzzPro.LOGGER.warn(
			"Recovered a timeline snapshot blanked by the legacy NBT UTF limit: game={}, timeline={}, sha256={}",
			timeline.gameId(),
			timeline.instanceId(),
			frozen.frozen().orElseThrow().sha256()
		);
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
		ensureReadinessSession(context.server());
		updateReadinessConnection(context.server(), context.player(), true);
		restoreMemberOnline(context.server(), context.player());
		resumePendingCallbacks(context.server());
		MessageServerRuntime.playerAuthorityReady(context.player());
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
			PixelTzzWorldState.get(context.server())
				.currentV3()
				.map(value -> !hasCurrentForcedPage(value, playerId))
				.orElse(true)
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
		PixelTzzWorldState.CommitV3Result result = wrapper.commitV3(
			state.stateRevision(),
			current -> current.beginGameInstance(
				game.id(),
				game.initialPhase(),
				UUID.randomUUID()
			)
		);
		if (!result.committed()) {
			PixelTzzPro.LOGGER.warn(
				"Could not initialize the unconfigured Pixel TZZ world activity: {}",
				result.reason()
			);
			return;
		}
		dispatchInitialPhaseEnterMessageHook(
			server,
			result.state().orElseThrow(),
			definitions.active()
		);
	}

	/**
	 * Emits the initial phase ENTER edge exactly once, after the new game scope is committed.
	 * Existing worlds, reload recovery, and cold-start restoration never pass the initialization
	 * guard above and therefore cannot replay this hook.
	 */
	private static void dispatchInitialPhaseEnterMessageHook(
		final MinecraftServer server,
		final WorldStateV3 state,
		final DefinitionSnapshot definitions
	) {
		Identifier phaseId = state.core().activePhaseId().orElse(null);
		PhaseDefinition phase = phaseId == null ? null : definitions.phases().get(phaseId);
		if (phase == null) {
			return;
		}
		LinkedHashMap<String, String> arguments = new LinkedHashMap<>(
			gameHookArguments(server, state, Optional.empty())
		);
		arguments.put("phase_id", phaseId.toString());
		MessageHookDispatcher.dispatch(
			server,
			definitions,
			phase.messageHooks(),
			MessageHookEvent.ENTER,
			Set.copyOf(state.core().players().keySet()),
			Optional.empty(),
			arguments
		);
	}

	static boolean canInitializeWorldActivity(final WorldStateV2 state) {
		return state.activeGameId().isEmpty()
			&& state.activePhaseId().isEmpty()
			&& state.players().isEmpty()
			&& state.activeForcedFlow().isEmpty()
			&& state.completionHistory().isEmpty();
	}

	private static ReadinessAuthority.OpenResult openReadinessIfRequired(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID initiatedBy,
		final Set<UUID> onlinePlayerIds,
		final long occurredAtEpochMillis
	) {
		Identifier gameId = state.core().activeGameId().orElse(null);
		Identifier phaseId = state.core().activePhaseId().orElse(null);
		var game = gameId == null ? null : definitions.games().get(gameId);
		var readiness = game == null ? null : game.readiness().orElse(null);
		if (readiness == null || !readiness.phase().equals(phaseId)) {
			return new ReadinessAuthority.OpenResult(
				OperationCode.SUCCESS,
				"",
				Optional.empty()
			);
		}
		if (state.readiness().isPresent()) {
			return new ReadinessAuthority.OpenResult(
				OperationCode.SUCCESS,
				"",
				Optional.of(state)
			);
		}
		return ReadinessAuthority.open(
			state,
			definitions,
			initiatedBy,
			onlinePlayerIds,
			UUID.randomUUID(),
			UUID::randomUUID,
			occurredAtEpochMillis
		);
	}

	/**
	 * One-time semantic recovery for worlds that were already in the ready phase before readiness
	 * became a persisted schema-v3 session.
	 */
	private static void ensureReadinessSession(final MinecraftServer server) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV3 state = wrapper.currentV3().orElse(null);
		View view = DefinitionRegistry.INSTANCE.view();
		if (
			state == null
				|| state.readiness().isPresent()
				|| state.timeline().isPresent()
				|| !view.healthy()
				|| state.core().host().isEmpty()
		) {
			return;
		}
		ReadinessAuthority.OpenResult opened = openReadinessIfRequired(
			state,
			view.active(),
			state.core().host().orElseThrow().playerId(),
			onlinePlayerIds(server),
			System.currentTimeMillis()
		);
		if (!opened.successful() || opened.nextState().isEmpty()) {
			if (!opened.successful()) {
				PixelTzzPro.LOGGER.warn(
					"Could not recover readiness session: {} ({})",
					opened.message(),
					opened.code().serializedName()
				);
			}
			return;
		}
		PixelTzzWorldState.CommitV3Result committed = wrapper.commitV3(
			state.stateRevision(),
			ignored -> opened.nextState().orElseThrow()
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.warn(
				"Could not persist recovered readiness session: {}",
				committed.reason()
			);
			return;
		}
		refreshFlowProjection(server, false);
		sendForcedPages(server);
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
				"只有当前主持人可以打开主持人控制台；非主持人管理员请使用玩家终端中的接管入口。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		sendConsoleSnapshot(context.server(), context.player(), payload.request().requestSequence());
	}

	private static void onTimelineViewRequest(
		final TimelineViewRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.protocolVersion())) {
			return;
		}
		int currentTick = context.server().getTickCount();
		Integer previousTick = LAST_TIMELINE_VIEW_REQUEST_TICK.put(player.getUUID(), currentTick);
		if (previousTick != null && currentTick - previousTick < 2) {
			return;
		}
		sendTimelineView(context.server(), player, payload.requestSequence());
	}

	private static void sendTimelineView(
		final MinecraftServer server,
		final ServerPlayer player,
		final long requestSequence
	) {
		if (!ServerPlayNetworking.canSend(player, TimelineViewS2CPayload.TYPE)) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		boolean host = wrapper.hostId().filter(player.getUUID()::equals).isPresent();
		TimelineViewS2CPayload payload = root == null
			? TimelineViewS2CPayload.error(
				NetworkProtocol.CURRENT_VERSION,
				requestSequence,
				"世界状态当前不可读取。"
			)
			: root.timeline().isEmpty() && host && DefinitionRegistry.INSTANCE.view().healthy()
				? TimelineViewBuilder.preview(
					root,
					DefinitionRegistry.INSTANCE.view().active(),
					requestSequence
				)
			: TimelineViewBuilder.build(
				root,
				player.getUUID(),
				host,
				requestSequence,
				playerId -> server.getPlayerList().getPlayer(playerId) != null
			);
		ServerPlayNetworking.send(player, payload);
	}

	private static void pushTimelineViews(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (verified(player)) {
				sendTimelineView(server, player, 0L);
			}
		}
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
		OperationKey operation = new OperationKey(
			payload.operationType(),
			payload.operationId()
		);
		if (immediateTimelineOperation(operation)) {
			executeImmediateTimelineOperation(
				context.server(),
				player,
				payload.request().requestSequence(),
				operation,
				payload.targetIds()
			);
			return;
		}
		PreparedOperation prepared = prepareOperation(
			context.server(),
			player,
			operation,
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
		clearPendingPlayerActionConfirmations(player.getUUID());
		clearPendingTerminalHostConfirmationMetadata(player.getUUID());
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
				sortedTargets(audited.targets()),
				audited.contextSummary(),
				issued.validForMilliseconds(),
				binding.stateRevision(),
				binding.definitionGeneration()
			)
		);
	}

	private static void executeImmediateTimelineOperation(
		final MinecraftServer server,
		final ServerPlayer actor,
		final long requestSequence,
		final OperationKey operation,
		final List<UUID> targets
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV2 state = wrapper.currentV2().orElse(null);
		OperationCode rejection = state == null
			? OperationCode.SCHEMA_BLOCKED
			: state.host()
				.filter(host -> host.playerId().equals(actor.getUUID()))
				.isEmpty()
					? OperationCode.NOT_HOST
					: !targets.isEmpty()
						? OperationCode.TARGET_COUNT_INVALID
						: OperationCode.SUCCESS;
		if (rejection != OperationCode.SUCCESS) {
			sendOperationResult(
				actor,
				requestSequence,
				rejection,
				rejection == OperationCode.NOT_HOST
					? "只有当前主持人可以控制任务时间线。"
					: rejection == OperationCode.TARGET_COUNT_INVALID
						? "暂停与继续不接受目标玩家。"
						: "世界状态当前不可写。",
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				true
			);
			return;
		}
		if (
			!persistConfirmationAudit(
				wrapper,
				AuditEventType.TIMELINE_CONTROL,
				Optional.of(actor.getUUID()),
				Optional.empty(),
				"requested " + operation.type() + "/" + operation.id(),
				System.currentTimeMillis()
			)
		) {
			sendOperationResult(
				actor,
				requestSequence,
				OperationCode.INTERNAL_ERROR,
				"无法写入时间线控制审计，操作未执行。",
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				true
			);
			return;
		}
		TimelineServerRuntime.RuntimeResult result = operation.type().equals(PAUSE_TIMELINE)
			? TimelineServerRuntime.hostPause(server, HOST_PAUSE_REASON)
			: TimelineServerRuntime.hostResume(server);
		sendOperationResult(
			actor,
			requestSequence,
			result.code(),
			result.successful()
				? operation.type().equals(PAUSE_TIMELINE)
					? "任务时间线已暂停。"
					: "任务时间线已继续。"
				: timelineControlFailure(result),
			wrapper.stateRevision(),
			DefinitionRegistry.INSTANCE.view().active().generation(),
			true
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
		if (TERMINAL_HOST_CONFIRMATIONS.containsKey(payload.tokenId())) {
			commitTerminalHostConfirmation(
				context.server(),
				player,
				payload,
				pending
			);
			return;
		}
		if (pending.operation().type().equals(PLAYER_ACTION_OPERATION_TYPE)) {
			commitPlayerActionConfirmation(
				context.server(),
				player,
				payload,
				pending
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
		PLAYER_ACTION_CONFIRMATIONS.remove(payload.tokenId());
		TERMINAL_HOST_CONFIRMATIONS.remove(payload.tokenId());
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
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		WorldStateV2 state = root == null ? null : root.core();
		if (state == null || root.activeGameInstanceId().isEmpty()) {
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
		if (
			request.flowInstanceId()
				.filter(instanceId -> ReadinessAuthority.matchesInstance(root, instanceId))
				.isPresent()
		) {
			onReadinessAction(payload, context, wrapper, root);
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
		ForcedFlowAuthority.V3ActionResult advanced = ForcedFlowAuthority.advance(
			root,
			restored.snapshot().orElseThrow(),
			submission,
			new FrozenPredicateEvaluator(
				context.server(),
				instance.runtime().executionSnapshot()
			),
			UUID.randomUUID(),
			System.currentTimeMillis(),
			exclusiveContext(root, context.server())
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
			if (advanced.code() == OperationCode.EXCLUSIVE_OPTION_TAKEN) {
				sendForcedPages(context.server());
			}
			return;
		}
		WorldStateV3 advancedCandidate = advanced.nextState().orElseThrow();
		PlayerRecord beforePlayer = state.players().get(player.getUUID());
		PlayerRecord afterPlayer = advancedCandidate.core().players().get(player.getUUID());
		boolean identityChanged = beforePlayer != null
			&& afterPlayer != null
			&& (
				!beforePlayer.roleId().equals(afterPlayer.roleId())
					|| !beforePlayer.teamId().equals(afterPlayer.teamId())
			);
		boolean invalidatingFieldChanged = payload.fieldValues().stream().anyMatch(value ->
			Optional.ofNullable(restored.snapshot().orElseThrow().fields().get(value.fieldId()))
				.map(FieldDefinition::invalidatesReady)
				.orElse(false)
				&& beforePlayer != null
				&& afterPlayer != null
				&& !Objects.equals(
					beforePlayer.persistentFields().get(value.fieldId()),
					afterPlayer.persistentFields().get(value.fieldId())
				)
		);
		if (identityChanged || invalidatingFieldChanged) {
			ReadinessAuthority.MutationResult reconciled = ReadinessAuthority.reconcilePlayer(
				advancedCandidate,
				player.getUUID(),
				true,
				UUID.randomUUID(),
				System.currentTimeMillis(),
				identityChanged
					? "Identity changed during readiness"
					: "A readiness-sensitive field changed"
			);
			if (!reconciled.successful()) {
				sendOperationResult(
					player,
					request.requestSequence(),
					reconciled.code(),
					reconciled.message(),
					root.stateRevision(),
					instance.identity().definitionGeneration(),
					false
				);
				return;
			}
			advancedCandidate = reconciled.state();
		}
		WorldStateV3 committedCandidate = advancedCandidate;
		boolean exclusiveReservationsChanged = !root.exclusiveReservations()
			.equals(committedCandidate.exclusiveReservations());
		PixelTzzWorldState.CommitV3Result committed = wrapper.commitV3(
			root.stateRevision(),
			ignored -> committedCandidate
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
		WorldStateV3 flowCommittedRoot = committed.state().orElseThrow();
		WorldStateV2 next = flowCommittedRoot.core();
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
		dispatchFlowAdvanceMessageHooks(
			context.server(),
			root,
			flowCommittedRoot,
			restored.snapshot().orElseThrow(),
			instance,
			flowCommittedRoot.core().activeForcedFlow().orElse(null),
			player.getUUID(),
			advanced.memberCompleted(),
			advanced.flowCompleted()
		);
		dispatchReadinessCompletionMessageHook(
			context.server(),
			root,
			flowCommittedRoot,
			Optional.of(player.getUUID())
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
		} else if (!exclusiveReservationsChanged) {
			sendCurrentForcedPage(context.server(), player);
		}
		if (exclusiveReservationsChanged) {
			// A final lock or atomic replacement changes the availability projected to every
			// player currently viewing the same exclusive field, not only to the submitter.
			sendForcedPages(context.server());
		}
		if (identityChanged || invalidatingFieldChanged) {
			sendCurrentForcedPage(context.server(), player);
		}
		if (!advanced.flowCompleted()) {
			refreshFlowProjection(context.server(), false);
		} else if (
			wrapper.currentV3()
				.flatMap(WorldStateV3::readiness)
				.map(ReadinessInstance::flow)
				.map(ForcedFlowInstance::runtime)
				.map(ForcedFlowRuntime::status)
				.filter(FlowInstanceStatus.ACTIVE::equals)
				.isPresent()
		) {
			refreshFlowProjection(context.server(), false);
		} else {
			pushHostConsoleSnapshot(context.server());
		}
		sendSnapshots(context.server(), false);
	}

	private static void onExclusiveChoiceMutation(
		final ExclusiveChoiceMutationC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		StatefulRequest request = payload.request();
		if (!verified(player) || !NetworkProtocol.isCompatible(request.protocolVersion())) {
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(context.server());
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		ForcedFlowInstance instance = root == null
			? null
			: root.core().activeForcedFlow().orElse(null);
		if (
			root == null
				|| root.activeGameInstanceId().isEmpty()
				|| instance == null
				|| request.stateRevision() > root.stateRevision()
				|| request.gameId().filter(instance.identity().gameId()::equals).isEmpty()
				|| request.flowInstanceId()
					.filter(instance.identity().instanceId()::equals)
					.isEmpty()
				|| request.pageInstanceId().isEmpty()
				|| request.definitionGeneration() != instance.identity().definitionGeneration()
		) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				"出生点选择页面已经变化，请以服务端恢复后的页面为准。",
				wrapper.stateRevision(),
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
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.SNAPSHOT_INVALID,
				"当前流程的冻结页面定义无法恢复。",
				root.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			return;
		}
		DefinitionSnapshot frozen = restored.snapshot().orElseThrow();
		FlowDefinition flow = frozen.flows().get(instance.identity().flowId());
		FlowNode node = flow == null ? null : flow.nodes().get(payload.nodeId());
		PageDefinition page = node instanceof ChoiceNode choice
			? frozen.pages().get(choice.page())
			: null;
		Set<Identifier> exposedFields = new LinkedHashSet<>();
		if (page != null) {
			collectFieldIds(page.root(), exposedFields);
		}
		if (
			!(node instanceof ChoiceNode choice)
				|| !choice.field().equals(payload.fieldId())
				|| !exposedFields.contains(payload.fieldId())
		) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.SNAPSHOT_INVALID,
				"当前页面没有公开这个独占选择字段。",
				root.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			sendCurrentForcedPage(context.server(), player);
			return;
		}

		ExclusiveChoiceMutation mutation = new ExclusiveChoiceMutation(
			player.getUUID(),
			request.flowInstanceId().orElseThrow(),
			request.pageInstanceId().orElseThrow(),
			payload.flowId(),
			payload.flowVersion(),
			payload.nodeId(),
			payload.instanceRevision(),
			payload.memberRevision(),
			request.requestSequence(),
			payload.fieldId(),
			payload.operation() == ExclusiveChoiceMutationC2SPayload.Operation.HOLD
				? ExclusiveChoiceMutationOperation.HOLD
				: ExclusiveChoiceMutationOperation.RELEASE,
			payload.value()
		);
		ExclusiveChoiceMutationResult mutated = ForcedFlowAuthority.mutateExclusiveChoice(
			root,
			frozen,
			mutation,
			System.currentTimeMillis(),
			exclusiveContext(root, context.server())
		);
		if (!mutated.successful()) {
			sendOperationResult(
				player,
				request.requestSequence(),
				mutated.code(),
				mutated.message(),
				root.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			sendForcedPages(context.server());
			return;
		}
		PixelTzzWorldState.CommitV3Result committed = wrapper.commitV3(
			root.stateRevision(),
			ignored -> mutated.nextState().orElseThrow()
		);
		if (!committed.committed()) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				"选择状态已经变化，请在刷新后的页面中重试。",
				wrapper.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			sendForcedPages(context.server());
			return;
		}
		WorldStateV3 finalRoot = committed.state().orElseThrow();
		sendOperationResult(
			player,
			request.requestSequence(),
			OperationCode.SUCCESS,
			payload.operation() == ExclusiveChoiceMutationC2SPayload.Operation.HOLD
				? "出生点已暂时预约。"
				: "出生点预约已释放。",
			finalRoot.stateRevision(),
			instance.identity().definitionGeneration(),
			false
		);
		sendForcedPages(context.server());
		refreshFlowProjection(context.server(), false);
		sendSnapshots(context.server(), false);
	}

	private static void onReadinessAction(
		final FlowActionC2SPayload payload,
		final ServerPlayNetworking.Context context,
		final PixelTzzWorldState wrapper,
		final WorldStateV3 root
	) {
		ServerPlayer player = context.player();
		StatefulRequest request = payload.request();
		ReadinessInstance readiness = root.readiness().orElse(null);
		ForcedFlowInstance instance = readiness == null ? null : readiness.flow();
		if (
			instance == null
				|| request.stateRevision() > root.stateRevision()
				|| request.flowInstanceId().filter(instance.identity().instanceId()::equals).isEmpty()
				|| request.pageInstanceId().isEmpty()
				|| request.gameId().filter(instance.identity().gameId()::equals).isEmpty()
				|| request.definitionGeneration() != instance.identity().definitionGeneration()
		) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.STATE_REVISION_STALE,
				"准备页面已经变化，请等待服务端恢复当前页面。",
				root.stateRevision(),
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
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				root.stateRevision(),
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
				.map(value -> new ForcedFlowAuthority.FieldInput(
					value.fieldId(),
					value.type(),
					value.canonicalJson()
				))
				.toList()
		);
		ReadinessAuthority.SubmitResult advanced = ReadinessAuthority.submit(
			root,
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
			sendOperationResult(
				player,
				request.requestSequence(),
				advanced.code(),
				advanced.message(),
				root.stateRevision(),
				instance.identity().definitionGeneration(),
				false
			);
			sendCurrentForcedPage(context.server(), player);
			return;
		}
		PixelTzzWorldState.CommitV3Result committed = wrapper.commitV3(
			root.stateRevision(),
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
		WorldStateV3 finalRoot = committed.state().orElseThrow();
		sendOperationResult(
			player,
			request.requestSequence(),
			OperationCode.SUCCESS,
			advanced.message(),
			finalRoot.stateRevision(),
			instance.identity().definitionGeneration(),
			false
		);
		dispatchFlowAdvanceMessageHooks(
			context.server(),
			root,
			finalRoot,
			restored.snapshot().orElseThrow(),
			instance,
			finalRoot.readiness().map(ReadinessInstance::flow).orElse(null),
			player.getUUID(),
			advanced.memberCompleted(),
			false
		);
		dispatchReadinessCompletionMessageHook(
			context.server(),
			root,
			finalRoot,
			Optional.of(player.getUUID())
		);
		if (
			advanced.memberCompleted()
				&& ServerPlayNetworking.canSend(player, ForcedPageReleaseS2CPayload.TYPE)
		) {
			ServerPlayNetworking.send(
				player,
				new ForcedPageReleaseS2CPayload(
					NetworkProtocol.CURRENT_VERSION,
					instance.identity().instanceId(),
					request.pageInstanceId().orElseThrow(),
					advanced.allCompleted() ? "readiness_completed" : "member_ready",
					finalRoot.stateRevision()
				)
			);
		} else {
			sendCurrentForcedPage(context.server(), player);
		}
		refreshFlowProjection(context.server(), advanced.allCompleted());
		sendSnapshots(context.server(), false);
	}

	private static void dispatchFlowAdvanceMessageHooks(
		final MinecraftServer server,
		final WorldStateV3 before,
		final WorldStateV3 after,
		final DefinitionSnapshot definitions,
		final ForcedFlowInstance flowBefore,
		final ForcedFlowInstance flowAfter,
		final UUID submittingPlayer,
		final boolean memberCompleted,
		final boolean flowCompleted
	) {
		FlowDefinition flow = definitions.flows().get(flowBefore.identity().flowId());
		if (flow == null || flowAfter == null) {
			return;
		}
		Optional<UUID> invoker = Optional.of(submittingPlayer);
		Map<String, String> common = gameHookArguments(
			server,
			after,
			invoker
		);
		PlayerRecord beforePlayer = before.core().players().get(submittingPlayer);
		PlayerRecord afterPlayer = after.core().players().get(submittingPlayer);
		if (
			beforePlayer != null
				&& afterPlayer != null
				&& !beforePlayer.roleId().equals(afterPlayer.roleId())
		) {
			dispatchRoleMessageHook(
				server,
				definitions,
				common,
				beforePlayer,
				afterPlayer,
				MessageHookEvent.ROLE_CHANGED,
				false,
				invoker
			);
		}
		if (
			memberCompleted
				&& afterPlayer != null
				&& Optional.ofNullable(definitions.roles().get(afterPlayer.roleId()))
					.flatMap(RoleDefinition::initializationFlow)
					.filter(flow.id()::equals)
					.isPresent()
		) {
			dispatchRoleMessageHook(
				server,
				definitions,
				common,
				beforePlayer,
				afterPlayer,
				MessageHookEvent.INITIALIZATION,
				true,
				invoker
			);
		}
		if (memberCompleted) {
			LinkedHashMap<String, String> arguments = new LinkedHashMap<>(
				flowHookArguments(common, flowAfter)
			);
			arguments.put("player", submittingPlayer.toString());
			arguments.put("target", submittingPlayer.toString());
			if (afterPlayer != null) {
				arguments.put("player_name", afterPlayer.lastKnownName());
			}
			MessageHookDispatcher.dispatch(
				server,
				definitions,
				flow.messageHooks(),
				MessageHookEvent.PLAYER_COMPLETE,
				Set.of(submittingPlayer),
				invoker,
				arguments
			);
		}
		if (flowCompleted) {
			MessageHookDispatcher.dispatch(
				server,
				definitions,
				flow.messageHooks(),
				MessageHookEvent.ALL_COMPLETE,
				flowTargets(flowAfter),
				invoker,
				flowHookArguments(common, flowAfter)
			);
		}
	}

	private static boolean readinessAllCompleted(final WorldStateV3 state) {
		return state.readiness()
			.map(ReadinessInstance::flow)
			.map(ForcedFlowInstance::runtime)
			.map(ForcedFlowRuntime::status)
			.filter(FlowInstanceStatus.COMPLETED::equals)
			.isPresent();
	}

	private static void dispatchReadinessCompleteMessageHook(
		final MinecraftServer server,
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final Optional<UUID> invoker
	) {
		ReadinessInstance readiness = state.readiness().orElse(null);
		if (readiness == null) {
			return;
		}
		ForcedFlowInstance flow = readiness.flow();
		GameDefinition game = definitions.games().get(flow.identity().gameId());
		ReadinessDefinition declared = game == null
			? null
			: game.readiness().orElse(null);
		if (declared == null) {
			return;
		}
		LinkedHashMap<String, String> arguments = new LinkedHashMap<>(
			flowHookArguments(gameHookArguments(server, state, invoker), flow)
		);
		arguments.put("readiness_instance_id", flow.identity().instanceId().toString());
		MessageHookDispatcher.dispatch(
			server,
			definitions,
			declared.messageHooks(),
			MessageHookEvent.COMPLETE,
			flowTargets(flow),
			invoker,
			arguments
		);
	}

	/**
	 * Emits the dedicated readiness COMPLETE hook only on the committed incomplete-to-complete
	 * edge. The hook and cue are always restored from readiness's own execution snapshot: an
	 * ordinary forced flow or a later data-pack reload must never replace this generation.
	 */
	private static void dispatchReadinessCompletionMessageHook(
		final MinecraftServer server,
		final WorldStateV3 before,
		final WorldStateV3 after,
		final Optional<UUID> invoker
	) {
		if (
			MessageHookDispatchContract.readinessCompletionEvent(
				readinessAllCompleted(before),
				readinessAllCompleted(after)
			).isEmpty()
		) {
			return;
		}
		ReadinessInstance readiness = after.readiness().orElse(null);
		if (readiness == null) {
			return;
		}
		ForcedFlowInstance flow = readiness.flow();
		var restored = ExecutionSnapshotCompiler.restore(
			flow.identity().flowId(),
			flow.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			PixelTzzPro.LOGGER.warn(
				"V3B readiness COMPLETE hook was skipped because frozen snapshot {} "
					+ "could not be restored: {}",
				flow.identity().instanceId(),
				restored.message()
			);
			return;
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition flowDefinition = definitions.flows().get(flow.identity().flowId());
		if (flowDefinition != null) {
			MessageHookDispatcher.dispatch(
				server,
				definitions,
				flowDefinition.messageHooks(),
				MessageHookEvent.ALL_COMPLETE,
				flowTargets(flow),
				invoker,
				flowHookArguments(gameHookArguments(server, after, invoker), flow)
			);
		}
		dispatchReadinessCompleteMessageHook(
			server,
			after,
			definitions,
			invoker
		);
	}

	/** Dispatches the Flow START hook for a readiness session created by this committed action. */
	private static void dispatchReadinessFlowStartMessageHook(
		final MinecraftServer server,
		final WorldStateV3 state,
		final Optional<UUID> invoker
	) {
		ReadinessInstance readiness = state.readiness().orElse(null);
		if (readiness == null) {
			return;
		}
		ForcedFlowInstance flow = readiness.flow();
		var restored = ExecutionSnapshotCompiler.restore(
			flow.identity().flowId(),
			flow.runtime().executionSnapshot()
		);
		if (!restored.success()) {
			PixelTzzPro.LOGGER.warn(
				"V3B readiness Flow START hook was skipped because frozen snapshot {} "
					+ "could not be restored: {}",
				flow.identity().instanceId(),
				restored.message()
			);
			return;
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition definition = definitions.flows().get(flow.identity().flowId());
		if (definition == null) {
			return;
		}
		MessageHookDispatcher.dispatch(
			server,
			definitions,
			definition.messageHooks(),
			MessageHookEvent.START,
			flowTargets(flow),
			invoker,
			flowHookArguments(gameHookArguments(server, state, invoker), flow)
		);
	}

	private static void dispatchConfirmedOperationMessageHooks(
		final MinecraftServer server,
		final WorldStateV3 before,
		final WorldStateV3 after,
		final DefinitionSnapshot definitions,
		final UUID actor,
		final Set<UUID> explicitTargets,
		final boolean startedFlow,
		final boolean readinessOpened,
		final boolean immediateRoleChanged,
		final boolean rosterCompletedFlow,
		final ActiveFlowDefinitions flowBefore
	) {
		Optional<UUID> invoker = Optional.of(actor);
		Map<String, String> common = gameHookArguments(server, after, invoker);
		Identifier previousPhase = before.core().activePhaseId().orElse(null);
		Identifier currentPhase = after.core().activePhaseId().orElse(null);
		if (!Objects.equals(previousPhase, currentPhase)) {
			LinkedHashMap<String, String> arguments = new LinkedHashMap<>(common);
			if (previousPhase != null) {
				arguments.put("previous_phase_id", previousPhase.toString());
			}
			if (currentPhase != null) {
				arguments.put("phase_id", currentPhase.toString());
			}
			Set<UUID> targets = Set.copyOf(after.core().players().keySet());
			PhaseDefinition exited = previousPhase == null
				? null
				: definitions.phases().get(previousPhase);
			if (exited != null) {
				MessageHookDispatcher.dispatch(
					server,
					definitions,
					exited.messageHooks(),
					MessageHookEvent.EXIT,
					targets,
					invoker,
					arguments
				);
			}
			PhaseDefinition entered = currentPhase == null
				? null
				: definitions.phases().get(currentPhase);
			if (entered != null) {
				MessageHookDispatcher.dispatch(
					server,
					definitions,
					entered.messageHooks(),
					MessageHookEvent.ENTER,
					targets,
					invoker,
					arguments
				);
			}
		}
		if (immediateRoleChanged) {
			for (UUID target : explicitTargets) {
				PlayerRecord previous = before.core().players().get(target);
				PlayerRecord current = after.core().players().get(target);
				if (
					previous == null
						|| current == null
						|| previous.roleId().equals(current.roleId())
				) {
					continue;
				}
				dispatchRoleMessageHook(
					server,
					definitions,
					common,
					previous,
					current,
					MessageHookEvent.ROLE_CHANGED,
					false,
					invoker
				);
			}
		}
		if (startedFlow) {
			ActiveFlowDefinitions active = activeFlowDefinitions(after.core()).orElse(null);
			if (active != null) {
				MessageHookDispatcher.dispatch(
					server,
					active.definitions(),
					active.flow().messageHooks(),
					MessageHookEvent.START,
					flowTargets(active.instance()),
					invoker,
					flowHookArguments(common, active.instance())
				);
			}
		}
		if (readinessOpened) {
			dispatchReadinessFlowStartMessageHook(server, after, invoker);
		}
		if (rosterCompletedFlow && flowBefore != null) {
			ForcedFlowInstance completed = after.core().activeForcedFlow()
				.filter(value ->
					value.identity().instanceId().equals(
						flowBefore.instance().identity().instanceId()
					)
				)
				.orElse(flowBefore.instance());
			MessageHookDispatcher.dispatch(
				server,
				flowBefore.definitions(),
				flowBefore.flow().messageHooks(),
				MessageHookEvent.ALL_COMPLETE,
				flowTargets(completed),
				invoker,
				flowHookArguments(common, completed)
			);
		}
		dispatchReadinessCompletionMessageHook(server, before, after, invoker);
	}

	private static void dispatchRoleMessageHook(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final Map<String, String> common,
		final PlayerRecord before,
		final PlayerRecord after,
		final MessageHookEvent event,
		final boolean initialization,
		final Optional<UUID> invoker
	) {
		RoleDefinition role = definitions.roles().get(after.roleId());
		if (role == null) {
			return;
		}
		LinkedHashMap<String, String> arguments = new LinkedHashMap<>(common);
		if (before != null) {
			arguments.put("previous_role_id", before.roleId().toString());
		}
		arguments.put("role_id", after.roleId().toString());
		arguments.put("initialization", Boolean.toString(initialization));
		arguments.put("player", after.playerId().toString());
		arguments.put("target", after.playerId().toString());
		arguments.put("player_name", after.lastKnownName());
		MessageHookDispatcher.dispatch(
			server,
			definitions,
			role.messageHooks(),
			event,
			Set.of(after.playerId()),
			invoker,
			arguments
		);
	}

	private static Map<String, String> gameHookArguments(
		final MinecraftServer server,
		final WorldStateV3 state,
		final Optional<UUID> invoker
	) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		state.core().activeGameId()
			.ifPresent(value -> result.put("game_id", value.toString()));
		state.activeGameInstanceId()
			.ifPresent(value -> result.put("game_instance_id", value.toString()));
		state.core().activePhaseId()
			.ifPresent(value -> result.put("phase_id", value.toString()));
		result.put("state_revision", Long.toString(state.stateRevision()));
		result.put("server_tick", Integer.toUnsignedString(server.getTickCount()));
		result.put(
			"game_elapsed_ticks",
			Long.toString(
				state.timeline()
					.map(WorldStateV3.TimelineInstance::gameElapsedTicks)
					.orElse(0L)
			)
		);
		invoker.ifPresent(value -> result.put("initiator", value.toString()));
		return Map.copyOf(result);
	}

	private static Map<String, String> flowHookArguments(
		final Map<String, String> common,
		final ForcedFlowInstance flow
	) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>(common);
		result.put("flow_id", flow.identity().flowId().toString());
		result.put("flow_version", Integer.toString(flow.identity().flowVersion()));
		result.put("flow_instance_id", flow.identity().instanceId().toString());
		result.put("source_action_id", flow.identity().sourceActionId().toString());
		result.put("initiated_by", flow.identity().initiatedBy().toString());
		result.put("completed", Integer.toString(flow.runtime().completedCount()));
		result.put("total", Integer.toString(flow.runtime().totalCount()));
		return Map.copyOf(result);
	}

	private static Set<UUID> flowTargets(final ForcedFlowInstance flow) {
		return flow.runtime().members().values()
			.stream()
			.filter(value -> value.status() != FlowMemberStatus.REMOVED)
			.map(FlowMemberState::playerId)
			.collect(
				java.util.stream.Collectors.toCollection(LinkedHashSet::new)
			);
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
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		WorldStateV2 state = root == null ? null : root.core();
		View view = DefinitionRegistry.INSTANCE.view();
		if (state == null) {
			return PreparedOperation.failed(OperationCode.SCHEMA_BLOCKED, "世界状态当前不可写。");
		}
		Optional<CallbackRetryKey> callbackRetry = callbackRetryKey(operation);
		Optional<TimelineCallbackRetryKey> timelineCallbackRetry =
			timelineCallbackRetryKey(root, operation);
		Optional<String> fallbackResult = timelineFallbackResult(operation);
		if (
			timelineOperation(operation)
				&& state.host()
					.filter(host -> host.playerId().equals(actor.getUUID()))
					.isEmpty()
		) {
			return PreparedOperation.failed(
				OperationCode.NOT_HOST,
				"只有当前主持人可以控制任务时间线。"
			);
		}
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
		List<RichText> runtimeConsequences = List.of();
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
		} else if (
			operation.type().equals(EXTEND_INTERMISSION)
				&& operation.id().equals(EXTEND_INTERMISSION_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"延长间隔不接受目标玩家。"
				);
			}
			WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
			if (timeline == null || timeline.currentTask().isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TIMELINE_NOT_ACTIVE,
					"当前没有活动任务时间线。"
				);
			}
			var preview = TimelineAuthority.extendIntermission(
				timeline,
				timelineActionContext(timeline),
				INTERMISSION_EXTENSION_TICKS
			);
			if (!preview.successful()) {
				return PreparedOperation.failed(
					preview.code(),
					timelineControlFailure(
						new TimelineServerRuntime.RuntimeResult(
							preview.code(),
							preview.message(),
							false
						)
					)
				);
			}
			prompt = new Prompt(
				richText("确认延长任务间隔"),
				List.of(
					richText("当前间隔将延长 60 秒，已经流逝的时间不会回退。"),
					richText("下一任务不会改变；数据包已冻结的结果和路线也不会改变。"),
					richText("操作会立即写入本局状态，/reload 与重启后继续保留。"),
					richText(timelinePromptContext(root, timeline))
				)
			);
			targetLabels = List.of();
		} else if (
			operation.type().equals(FINISH_INTERMISSION)
				&& operation.id().equals(FINISH_INTERMISSION_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"提前结束间隔不接受目标玩家。"
				);
			}
			WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
			if (timeline == null || timeline.currentTask().isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TIMELINE_NOT_ACTIVE,
					"当前没有活动任务时间线。"
				);
			}
			var preview = TimelineAuthority.finishIntermissionEarly(
				timeline,
				timelineActionContext(timeline)
			);
			if (!preview.successful()) {
				return PreparedOperation.failed(
					preview.code(),
					timelineControlFailure(
						new TimelineServerRuntime.RuntimeResult(
							preview.code(),
							preview.message(),
							false
						)
					)
				);
			}
			prompt = new Prompt(
				richText("确认提前结束任务间隔"),
				List.of(
					richText("当前间隔会立即归零，并执行数据包注册的间隔结束回调。"),
					richText("回调成功后才会进入已冻结的下一任务或结束阶段。"),
					richText("该操作不会跳过任务，也不会更改已经冻结的结果。"),
					richText(timelinePromptContext(root, timeline))
				)
			);
			targetLabels = List.of();
		} else if (
			operation.type().equals(INTERRUPT_TIMELINE)
				&& operation.id().equals(INTERRUPT_TIMELINE_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"紧急终止不接受目标玩家。"
				);
			}
			WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
			if (timeline == null || timeline.currentTask().isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TIMELINE_NOT_ACTIVE,
					"当前没有可终止的任务时间线。"
				);
			}
			var preview = TimelineAuthority.interrupt(
				timeline,
				timelineActionContext(timeline),
				server.getTickCount()
			);
			if (!preview.successful()) {
				return PreparedOperation.failed(
					preview.code(),
					timelineControlFailure(
						new TimelineServerRuntime.RuntimeResult(
							preview.code(),
							preview.message(),
							false
						)
					)
				);
			}
			prompt = new Prompt(
				richText("确认紧急终止整局"),
				List.of(
					richText("当前任务会记录为「已中断」，不会伪造成功或失败结果。"),
					richText("任务时间线立即停止，且不会启动任何后续任务。"),
					richText("已成功的数据包回调和世界效果不会自动撤销。"),
					richText("赛后回顾仍可查看；之后只能显式重置本局进程。")
				)
			);
			targetLabels = timeline.currentTask()
				.orElseThrow()
				.participants()
				.stream()
				.map(playerId -> {
					PlayerRecord record = state.players().get(playerId);
					return confirmationTarget(
						server,
						state,
						view.active(),
						playerId,
						record == null ? playerId.toString() : record.lastKnownName(),
						Set.of()
					);
				})
				.toList();
		} else if (operation.type().equals(RETRY_TIMELINE_CALLBACK)) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"回调重试不接受手动选择玩家。"
				);
			}
			TimelineCallbackRetryKey retry = timelineCallbackRetry.orElse(null);
			WorldStateV3.CallbackStep step = retry == null
				? null
				: timelineCallbackStep(root, retry).orElse(null);
			if (
				retry == null
					|| step == null
					|| root.timeline()
						.filter(timeline -> timeline.status() == WorldStateV3.TimelineStatus.BLOCKED)
						.isEmpty()
			) {
				return PreparedOperation.failed(
					OperationCode.ACTION_UNAVAILABLE,
					"对应的时间线回调已经不可重试。"
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
			prompt = new Prompt(
				richText("确认重试任务回调"),
				List.of(
					richText("将重新执行数据包函数 " + step.functionId() + "。"),
					richText("失败步骤：" + step.stepKey() + "；已尝试 " + step.attemptCount() + " 次。"),
					richText("已经成功的回调不会重放，任务结果和路线也不会更改。"),
					richText(
						step.lastError().map(error -> "上次失败：" + truncate(error, 256))
							.orElse("上次执行结果未能确认。")
					)
				)
			);
			targetLabels = retry.playerId()
				.map(playerId -> {
					PlayerRecord record = state.players().get(playerId);
					return List.of(
						confirmationTarget(
							server,
							state,
							view.active(),
							playerId,
							record == null ? playerId.toString() : record.lastKnownName(),
							Set.of()
						)
					);
				})
				.orElseGet(List::of);
		} else if (operation.type().equals(HOST_FALLBACK_RESULT)) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"人工指定结果不接受目标玩家。"
				);
			}
			String resultId = fallbackResult.orElse(null);
			WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
			TaskDefinition task = frozenCurrentTask(root).orElse(null);
			TaskResult result = task == null || resultId == null
				? null
				: task.results().get(resultId);
			if (
				timeline == null
					|| task == null
					|| !TimelineServerRuntime.hostFallbackEligible(timeline, result)
			) {
				return PreparedOperation.failed(
					OperationCode.ACTION_UNAVAILABLE,
					"当前结算异常不允许人工指定该结果。"
				);
			}
			prompt = new Prompt(
				richText("确认人工指定任务结果"),
				List.of(
					richText("将把当前任务结果冻结为「" + result.name().plainText() + "」。"),
					richText("只允许数据包显式开放的兜底结果；冻结后不能更换或覆盖。"),
					richText("随后仍会执行该结果的结算回调、间隔与已注册路线。"),
					richText(timelinePromptContext(root, timeline))
				)
			);
			targetLabels = List.of();
		} else if (
			operation.type().equals(APPROVE_TIMELINE)
				&& operation.id().equals(APPROVE_TIMELINE_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"批准开局不接受手动选择玩家。"
				);
			}
			if (root == null || root.activeGameInstanceId().isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.SCHEMA_BLOCKED,
					"当前游戏实例不可用。"
				);
			}
			var approval = TimelineApprovalAuthority.approve(
				root,
				view.active(),
				new ApprovalContext(
					actor.getUUID(),
					root.activeGameInstanceId().orElseThrow(),
					root.stateRevision(),
					view.active().generation(),
					new UUID(0L, 1L),
					new UUID(0L, 2L),
					server.getTickCount(),
					onlinePlayerIds(server)
				)
			);
			if (!approval.successful()) {
				return PreparedOperation.failed(approval.code(), approval.message());
			}
			var assetGate = MessageServerRuntime.requiredAssetsForGame(
				server,
				root.core().activeGameId().orElseThrow(),
				Set.copyOf(approval.frozenParticipants())
			);
			if (!assetGate.ready()) {
				return PreparedOperation.failed(
					OperationCode.RESOURCE_BLOCKED,
					assetGate.message()
				);
			}
			targetLabels = approval.frozenParticipants()
				.stream()
				.map(playerId -> {
					PlayerRecord record = state.players().get(playerId);
					return confirmationTarget(
						server,
						state,
						view.active(),
						playerId,
						record == null ? playerId.toString() : record.lastKnownName(),
						Set.of()
					);
				})
				.toList();
			String firstTask = root.core()
				.activeGameId()
				.map(view.active().games()::get)
				.flatMap(game -> game.taskTimeline())
				.map(timeline -> view.active().tasks().get(timeline.initialTask()))
				.map(task -> task.name().plainText())
				.orElse("首个任务");
			prompt = new Prompt(
				richText("确认批准开局"),
				List.of(
					richText("将冻结当前任务计划并创建唯一时间线实例。"),
					richText("阶段将切换到数据包注册的开局阶段，随后启动「" + firstTask + "」。"),
					richText("本局参与者共 " + approval.frozenParticipants().size() + " 人；/reload 不会替换本局快照。"),
					richText("批准后不能跳过任务；若需停止，只能使用紧急终止。")
				)
			);
		} else if (
			operation.type().equals(RESET_GAME_PROGRESS)
				&& operation.id().equals(RESET_GAME_PROGRESS_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"重置本局进程不接受手动选择玩家。"
				);
			}
			boolean terminal = root != null
				&& root.timeline()
					.map(timeline ->
						timeline.status() == WorldStateV3.TimelineStatus.COMPLETED
							|| timeline.status() == WorldStateV3.TimelineStatus.INTERRUPTED
					)
					.orElse(true);
			boolean activeForcedFlow = state.activeForcedFlow()
				.map(flow -> unfinishedFlow(flow.runtime().status()))
				.orElse(false);
			if (root == null || !terminal || activeForcedFlow) {
				return PreparedOperation.failed(
					OperationCode.ACTION_UNAVAILABLE,
					"仍有执行中的任务或强制流程，当前不是安全重置点。"
				);
			}
			var reset = WorldResetAuthority.resetGameProgress(
				root,
				view.active(),
				actor.getUUID(),
				new UUID(0L, 3L)
			);
			if (!reset.successful()) {
				return PreparedOperation.failed(reset.code(), reset.message());
			}
			targetLabels = resetTargets(server, state, view.active());
			prompt = new Prompt(
				richText("确认重置本局进程"),
				List.of(
					richText("将清除任务时间线、回顾、猎人出生点等独占选择、强制流程和完成记录。"),
					richText("玩家身份、队伍、生存状态和持久字段将恢复默认；已准备状态也会全部清除。"),
					richText("当前主持人与玩家注册名单会保留，并返回游戏初始阶段。"),
					richText("该操作不会通过 /reload 自动发生，执行后不可撤销。")
				)
			);
		} else if (
			operation.type().equals(CLEAR_ALL_STATE)
				&& operation.id().equals(CLEAR_ALL_STATE_OPERATION)
		) {
			if (!targets.isEmpty()) {
				return PreparedOperation.failed(
					OperationCode.TARGET_COUNT_INVALID,
					"清空全部数据不接受手动选择玩家。"
				);
			}
			var clear = root == null
				? null
				: WorldResetAuthority.clearAll(root, actor.getUUID());
			if (clear == null || !clear.successful()) {
				return PreparedOperation.failed(
					clear == null ? OperationCode.SCHEMA_BLOCKED : clear.code(),
					clear == null ? "世界状态当前不可写。" : clear.message()
				);
			}
			targetLabels = resetTargets(server, state, view.active());
			prompt = new Prompt(
				richText("确认清空 Pixel TZZ 全部数据"),
				List.of(
					richText("将清除主持人、活动游戏、全部玩家、流程、任务、回顾、预约和审计状态。"),
					richText("世界会回到未认领状态；/reload 与重启都不会恢复这些数据。"),
					richText("不会删除原版物品、地图或世界文件，也不会删除数据包文件。"),
					richText("这是最高风险操作；执行后当前控制台权限也会立即失效。")
				)
			);
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
			FlowRosterAuthority.V3Result result = FlowRosterAuthority.addMembers(
				root,
				active.definitions(),
				actor.getUUID(),
				active.instance().identity().instanceId(),
				active.instance().runtime().instanceRevision(),
				selected.stream()
					.map(player -> new OnlineMember(player.playerId(), player.name(), true))
					.toList(),
				UUID::randomUUID,
				System.currentTimeMillis(),
				root.activeGameInstanceId().orElseThrow()
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
			FlowRosterAuthority.V3Result result = FlowRosterAuthority.removeMembers(
				root,
				actor.getUUID(),
				instance.identity().instanceId(),
				instance.runtime().instanceRevision(),
				targets,
				MEMBER_REMOVAL_REASON,
				System.currentTimeMillis(),
				server.getTickCount()
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
			FlowRosterAuthority.V3Result result = FlowRosterAuthority.cancelFlow(
				root,
				actor.getUUID(),
				instance.identity().instanceId(),
				instance.runtime().instanceRevision(),
				FLOW_CANCELLATION_REASON,
				System.currentTimeMillis(),
				server.getTickCount()
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
					|| state.activeGameId()
						.map(view.active().games()::get)
						.flatMap(game -> game == null ? Optional.empty() : game.readiness())
						.map(readiness -> readiness.action().equals(operation.id()))
						.orElse(false)
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
			Optional<String> blockingFlow = panelActionFlowBlockReason(root);
			if (blockingFlow.isPresent()) {
				return PreparedOperation.failed(
					OperationCode.ACTIVE_FLOW_EXISTS,
					blockingFlow.orElseThrow()
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
			if (action.operation() instanceof TransitionPhaseOperation) {
				if (action.target().mode() != SelectionMode.NONE || !targets.isEmpty()) {
					return PreparedOperation.failed(
						OperationCode.TARGET_COUNT_INVALID,
						"阶段迁移不接受目标玩家。"
					);
				}
				PhaseTransitionAuthority.Result validation = PhaseTransitionAuthority.transition(
					root,
					view.active(),
					action,
					actor.getUUID(),
					state.stateRevision(),
					System.currentTimeMillis()
				);
				if (!validation.successful()) {
					return PreparedOperation.failed(validation.code(), validation.message());
				}
				targets = List.of();
				ReadinessAuthority.OpenResult readinessPreview = openReadinessIfRequired(
					validation.nextState().orElseThrow(),
					view.active(),
					actor.getUUID(),
					onlinePlayerIds(server),
					System.currentTimeMillis()
				);
				if (!readinessPreview.successful()) {
					return PreparedOperation.failed(
						readinessPreview.code(),
						readinessPreview.message()
					);
				}
				ReadinessInstance preview = readinessPreview.nextState()
					.flatMap(WorldStateV3::readiness)
					.orElse(null);
				if (preview == null) {
					targetLabels = List.of();
				} else {
					targetLabels = preview.flow()
						.runtime()
						.members()
						.values()
						.stream()
						.map(member ->
							confirmationTarget(
								server,
								state,
								view.active(),
								member.playerId(),
								member.lockedName(),
								Set.of()
							)
						)
						.toList();
					runtimeConsequences = List.of(
						richText("这些玩家将立即进入不可关闭的准备页。"),
						richText("主持人不受影响，不会进入准备页，可继续查看准备进度。")
					);
				}
			} else {
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
					FlowRosterAuthority.V3Result roleResult = FlowRosterAuthority.assignRoleImmediate(
						root,
						view.active(),
						actor.getUUID(),
						state.stateRevision(),
						assign.role(),
						targets,
						System.currentTimeMillis(),
						root.activeGameInstanceId().orElseThrow(),
						server.getTickCount()
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
			}
			var confirmation = action.confirmation().orElse(null);
			Prompt authoredPrompt = confirmation == null
				? new Prompt(
					action.label(),
					List.of(action.description())
				)
				: new Prompt(confirmation.title(), confirmation.consequences());
			if (runtimeConsequences.isEmpty()) {
				prompt = authoredPrompt;
			} else {
				if (
					authoredPrompt.consequences().size() + runtimeConsequences.size()
						> ConfirmationTokens.MAX_CONSEQUENCES
				) {
					return PreparedOperation.failed(
						OperationCode.DEFINITION_UNAVAILABLE,
						"确认后果条目过多，无法加入服务端准备影响说明。"
					);
				}
				List<RichText> consequences = new ArrayList<>(authoredPrompt.consequences());
				consequences.addAll(runtimeConsequences);
				prompt = new Prompt(authoredPrompt.title(), consequences);
			}
			panelAction = Optional.of(action);
		}
		Binding binding = new Binding(
			operation,
			targets,
			state.activeGameId(),
			state.activePhaseId(),
			view.active().generation(),
			state.stateRevision(),
			relatedStateDigest(server, root, operation, targets)
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
			+ (
				targetLabels.isEmpty()
					? targets.isEmpty() ? "无需选择玩家" : targets.size() + " 名目标玩家"
					: targetLabels.size() + " 名受影响玩家"
			);
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
		WorldStateV3 root = wrapper.currentV3().orElseThrow();
		WorldStateV2 state = root.core();
		Optional<TimelineServerRuntime.RuntimeResult> timelineControl =
			executeConfirmedTimelineOperation(server, root, binding.operation());
		if (timelineControl.isPresent()) {
			TimelineServerRuntime.RuntimeResult result = timelineControl.orElseThrow();
			sendOperationResult(
				actor,
				request.requestSequence(),
				result.code(),
				result.successful()
					? timelineControlSuccess(binding.operation(), root)
					: timelineControlFailure(result),
				wrapper.stateRevision(),
				definitions.active().generation(),
				true
			);
			if (!result.changed()) {
				pushHostConsoleSnapshot(server);
			}
			return;
		}
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
		Optional<WorldStateV3> candidate;
		boolean startedFlow = false;
		boolean rosterChanged = false;
		boolean rosterCompletedFlow = false;
		boolean canceledFlow = false;
		boolean immediateRoleChanged = false;
		boolean readinessOpened = false;
		boolean readinessChanged = false;
		boolean timelineChanged = false;
		boolean worldReset = resetOperation(binding.operation());
		Set<UUID> resetForcedPageMembers = worldReset
			? state.activeForcedFlow()
				.map(ForcedFlowInstance::runtime)
				.map(ForcedFlowRuntime::members)
				.map(Map::keySet)
				.map(Set::copyOf)
				.orElseGet(Set::of)
			: Set.of();
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
			candidate = result.nextState().map(root::withCore);
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
				candidate = result.nextState().map(root::withCore);
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
			candidate = result.nextState().map(root::withCore);
		} else if (
			binding.operation().type().equals(APPROVE_TIMELINE)
				&& binding.operation().id().equals(APPROVE_TIMELINE_OPERATION)
		) {
			if (root.activeGameInstanceId().isEmpty()) {
				code = OperationCode.SCHEMA_BLOCKED;
				message = "当前游戏实例不可用。";
				candidate = Optional.empty();
			} else {
				var result = TimelineApprovalAuthority.approve(
					root,
					definitions.active(),
					new ApprovalContext(
						actor.getUUID(),
						root.activeGameInstanceId().orElseThrow(),
						root.stateRevision(),
						definitions.active().generation(),
						UUID.randomUUID(),
						UUID.randomUUID(),
						server.getTickCount(),
						onlinePlayerIds(server)
					)
				);
				if (result.successful()) {
					var assetGate = MessageServerRuntime.requiredAssetsForGame(
						server,
						root.core().activeGameId().orElseThrow(),
						Set.copyOf(result.frozenParticipants())
					);
					if (!assetGate.ready()) {
						code = OperationCode.RESOURCE_BLOCKED;
						message = assetGate.message();
						candidate = Optional.empty();
						timelineChanged = false;
					} else {
						code = result.code();
						message = result.message();
						candidate = result.nextState();
						timelineChanged = true;
					}
				} else {
					code = result.code();
					message = result.message();
					candidate = result.nextState();
					timelineChanged = false;
				}
			}
		} else if (
			binding.operation().type().equals(RESET_GAME_PROGRESS)
				&& binding.operation().id().equals(RESET_GAME_PROGRESS_OPERATION)
		) {
			boolean terminal = root.timeline()
				.map(timeline ->
					timeline.status() == WorldStateV3.TimelineStatus.COMPLETED
						|| timeline.status() == WorldStateV3.TimelineStatus.INTERRUPTED
				)
				.orElse(true);
			boolean activeForcedFlow = state.activeForcedFlow()
				.map(flow -> unfinishedFlow(flow.runtime().status()))
				.orElse(false);
			if (!terminal || activeForcedFlow) {
				code = OperationCode.ACTION_UNAVAILABLE;
				message = "仍有执行中的任务或强制流程，当前不是安全重置点。";
				candidate = Optional.empty();
			} else {
				var result = WorldResetAuthority.resetGameProgress(
					root,
					definitions.active(),
					actor.getUUID(),
					UUID.randomUUID()
				);
				code = result.code();
				message = result.message();
				candidate = result.nextState();
				timelineChanged = result.successful();
			}
		} else if (
			binding.operation().type().equals(CLEAR_ALL_STATE)
				&& binding.operation().id().equals(CLEAR_ALL_STATE_OPERATION)
		) {
			var result = WorldResetAuthority.clearAll(root, actor.getUUID());
			code = result.code();
			message = result.message();
			candidate = result.nextState();
			timelineChanged = result.successful();
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
				ActiveFlowDefinitions active = activeFlowDefinitions(state).orElse(null);
				FlowRosterAuthority.V3Result result = active == null
					? new FlowRosterAuthority.V3Result(
						OperationCode.SNAPSHOT_INVALID,
						"当前流程的冻结定义不可恢复。",
						Optional.empty(),
						false
					)
					: FlowRosterAuthority.addMembers(
					root,
					active.definitions(),
					actor.getUUID(),
					instance.identity().instanceId(),
					instance.runtime().instanceRevision(),
					selected.stream()
						.map(player -> new OnlineMember(player.playerId(), player.name(), true))
						.toList(),
					UUID::randomUUID,
					System.currentTimeMillis(),
					root.activeGameInstanceId().orElseThrow()
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
				FlowRosterAuthority.V3Result result = FlowRosterAuthority.removeMembers(
					root,
					actor.getUUID(),
					instance.identity().instanceId(),
					instance.runtime().instanceRevision(),
					binding.targetIds(),
					MEMBER_REMOVAL_REASON,
					System.currentTimeMillis(),
					server.getTickCount()
				);
				code = result.code();
				message = result.message();
				candidate = result.nextState();
				rosterChanged = result.successful();
				rosterCompletedFlow = result.becameCompleted();
				canceledFlow = result.nextState()
					.map(WorldStateV3::core)
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
				FlowRosterAuthority.V3Result result = FlowRosterAuthority.cancelFlow(
					root,
					actor.getUUID(),
					instance.identity().instanceId(),
					instance.runtime().instanceRevision(),
					FLOW_CANCELLATION_REASON,
					System.currentTimeMillis(),
					server.getTickCount()
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
				} else if (action.operation() instanceof TransitionPhaseOperation) {
					PhaseTransitionAuthority.Result result = PhaseTransitionAuthority.transition(
						root,
						definitions.active(),
						action,
						actor.getUUID(),
						state.stateRevision(),
						System.currentTimeMillis()
					);
					code = result.code();
					message = result.message();
					candidate = result.nextState();
					if (result.successful() && candidate.isPresent()) {
						ReadinessAuthority.OpenResult opened = openReadinessIfRequired(
							candidate.orElseThrow(),
							definitions.active(),
							actor.getUUID(),
							onlinePlayerIds(server),
							System.currentTimeMillis()
						);
						code = opened.code();
						if (!opened.successful()) {
							message = opened.message();
							candidate = Optional.empty();
						} else if (opened.nextState().isPresent()) {
							message = result.message() + " 玩家准备已开放。";
							candidate = opened.nextState();
							readinessOpened = root.readiness().isEmpty()
								&& candidate.orElseThrow().readiness().isPresent();
						}
					}
				} else if (immediateRoleAction(action)) {
					AssignRoleOperation assign = (AssignRoleOperation) action.operation();
					FlowRosterAuthority.V3Result result = FlowRosterAuthority.assignRoleImmediate(
						root,
						definitions.active(),
						actor.getUUID(),
						state.stateRevision(),
						assign.role(),
						binding.targetIds(),
						System.currentTimeMillis(),
						root.activeGameInstanceId().orElseThrow(),
						server.getTickCount()
					);
					code = result.code();
					message = result.message();
					candidate = result.nextState();
					immediateRoleChanged = result.successful();
					if (result.successful() && candidate.isPresent()) {
						ReadinessAuthority.MutationResult reconciled =
							reconcileReadinessPlayers(
								candidate.orElseThrow(),
								binding.targetIds(),
								onlinePlayerIds(server),
								System.currentTimeMillis(),
								"Identity changed during readiness"
							);
						code = reconciled.code();
						message = reconciled.successful() ? message : reconciled.message();
						candidate = reconciled.successful()
							? Optional.of(reconciled.state())
							: Optional.empty();
						readinessChanged = reconciled.successful() && reconciled.changed();
					}
				} else {
					List<UUID> explicit = action.target().mode() == SelectionMode.NONE
						? List.of()
						: binding.targetIds();
					ForcedFlowAuthority.V3StartResult result = ForcedFlowAuthority.start(
						root,
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
						),
						exclusiveContext(root, server)
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
		WorldStateV3 confirmedCandidate = candidate.orElseThrow();
		PixelTzzWorldState.CommitV3Result committed = wrapper.commitV3(
			root.stateRevision(),
			ignored -> confirmedCandidate
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
		WorldStateV3 confirmedCommittedRoot = committed.state().orElseThrow();
		MessageCommands.refreshPlayerCommandTreesIfHostChanged(
			server,
			root.core().host().map(WorldStateV2.HostRecord::playerId),
			confirmedCommittedRoot.core().host().map(WorldStateV2.HostRecord::playerId)
		);
		WorldStateV2 next = confirmedCommittedRoot.core();
		if (readinessChanged) {
			refreshReadinessPages(
				server,
				root,
				committed.state().orElseThrow(),
				Set.copyOf(binding.targetIds()),
				"readiness_changed"
			);
		}
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
		if (!worldReset) {
			dispatchConfirmedOperationMessageHooks(
				server,
				root,
				confirmedCommittedRoot,
				definitions.active(),
				actor.getUUID(),
				Set.copyOf(binding.targetIds()),
				startedFlow,
				readinessOpened,
				immediateRoleChanged,
				rosterCompletedFlow,
				activeBeforeCommit
			);
		}
		if (worldReset) {
			releaseForcedPages(
				server,
				state,
				resetForcedPageMembers,
				binding.operation().type().equals(CLEAR_ALL_STATE)
					? "all_state_cleared"
					: "world_reset"
			);
			HOST_FLOW_BOSS_BAR.clear();
			CONFIRMATIONS.clear();
			PLAYER_ACTION_CONFIRMATIONS.clear();
			TERMINAL_HOST_CONFIRMATIONS.clear();
		}
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
		if (timelineChanged) {
			timelineStateChanged(server);
		}
		if (startedFlow || rosterChanged || readinessOpened || readinessChanged) {
			sendForcedPages(server);
			if (canceledFlow) {
				if (
					wrapper.currentV3()
						.flatMap(WorldStateV3::readiness)
						.map(ReadinessInstance::flow)
						.map(ForcedFlowInstance::runtime)
						.map(ForcedFlowRuntime::status)
						.filter(FlowInstanceStatus.ACTIVE::equals)
						.isPresent()
				) {
					refreshFlowProjection(server, false);
				} else {
					HOST_FLOW_BOSS_BAR.clear();
				}
			} else if (
				!rosterCompletedFlow
					|| readinessOpened
					|| wrapper.currentV3()
						.flatMap(WorldStateV3::readiness)
						.map(ReadinessInstance::flow)
						.map(ForcedFlowInstance::runtime)
						.map(ForcedFlowRuntime::status)
						.filter(FlowInstanceStatus.ACTIVE::equals)
						.isPresent()
			) {
				refreshFlowProjection(server, false);
			}
		}
	}

	private static Optional<TimelineServerRuntime.RuntimeResult> executeConfirmedTimelineOperation(
		final MinecraftServer server,
		final WorldStateV3 root,
		final OperationKey operation
	) {
		if (
			operation.type().equals(EXTEND_INTERMISSION)
				&& operation.id().equals(EXTEND_INTERMISSION_OPERATION)
		) {
			return Optional.of(
				TimelineServerRuntime.extendIntermission(server, INTERMISSION_EXTENSION_TICKS)
			);
		}
		if (
			operation.type().equals(FINISH_INTERMISSION)
				&& operation.id().equals(FINISH_INTERMISSION_OPERATION)
		) {
			return Optional.of(TimelineServerRuntime.finishIntermissionEarly(server));
		}
		if (
			operation.type().equals(INTERRUPT_TIMELINE)
				&& operation.id().equals(INTERRUPT_TIMELINE_OPERATION)
		) {
			return Optional.of(TimelineServerRuntime.interrupt(server, server.getTickCount()));
		}
		if (operation.type().equals(RETRY_TIMELINE_CALLBACK)) {
			TimelineCallbackRetryKey retry = timelineCallbackRetryKey(root, operation).orElse(null);
			return Optional.of(
				retry == null
					? new TimelineServerRuntime.RuntimeResult(
						OperationCode.ACTION_UNAVAILABLE,
						"callback retry is no longer available",
						false
					)
					: TimelineServerRuntime.retryCurrentCallback(
						server,
						retry.stepKey(),
						retry.playerId()
					)
			);
		}
		if (operation.type().equals(HOST_FALLBACK_RESULT)) {
			String resultId = timelineFallbackResult(operation).orElse(null);
			return Optional.of(
				resultId == null
					? new TimelineServerRuntime.RuntimeResult(
						OperationCode.ACTION_UNAVAILABLE,
						"host fallback result is no longer available",
						false
					)
					: TimelineServerRuntime.hostFallbackResult(server, resultId)
			);
		}
		return Optional.empty();
	}

	private static String timelineControlSuccess(
		final OperationKey operation,
		final WorldStateV3 root
	) {
		if (operation.type().equals(EXTEND_INTERMISSION)) {
			return root.timeline().filter(WorldStateV3.TimelineInstance::paused).isPresent()
				? "任务间隔已延长 60 秒，并继续保持暂停。"
				: "任务间隔已延长 60 秒。";
		}
		if (operation.type().equals(FINISH_INTERMISSION)) {
			return root.timeline().filter(WorldStateV3.TimelineInstance::paused).isPresent()
				? "任务间隔已标记结束；继续时间线后将执行间隔结束回调。"
				: "任务间隔已提前结束，时间线正在继续。";
		}
		if (operation.type().equals(INTERRUPT_TIMELINE)) {
			return "本局任务时间线已紧急终止。";
		}
		if (operation.type().equals(RETRY_TIMELINE_CALLBACK)) {
			return "任务回调已重试，时间线状态已更新。";
		}
		if (operation.type().equals(HOST_FALLBACK_RESULT)) {
			return "人工兜底结果已冻结，时间线正在继续结算。";
		}
		return "时间线操作已完成。";
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

	private static Set<UUID> onlinePlayerIds(final MinecraftServer server) {
		return server.getPlayerList()
			.getPlayers()
			.stream()
			.map(ServerPlayer::getUUID)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private static ForcedFlowAuthority.ExclusiveContext exclusiveContext(
		final WorldStateV3 state,
		final MinecraftServer server
	) {
		return new ForcedFlowAuthority.ExclusiveContext(
			state.activeGameInstanceId().orElseThrow(
				() -> new IllegalStateException("active game instance is unavailable")
			),
			server.getTickCount(),
			UUID::randomUUID
		);
	}

	private static List<Target> resetTargets(
		final MinecraftServer server,
		final WorldStateV2 state,
		final DefinitionSnapshot definitions
	) {
		return state.players()
			.values()
			.stream()
			.sorted(Comparator.comparing(PlayerRecord::playerId))
			.map(player ->
				confirmationTarget(
					server,
					state,
					definitions,
					player.playerId(),
					player.lastKnownName(),
					Set.of()
				)
			)
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
		final WorldStateV3 root,
		final DefinitionSnapshot definitions,
		final ServerPlayer actor,
		final PanelActionDefinition action
	) {
		WorldStateV2 state = root.core();
		if (action.operation() instanceof TransitionPhaseOperation) {
			PhaseTransitionAuthority.Result validation = PhaseTransitionAuthority.transition(
				root,
				definitions,
				action,
				actor.getUUID(),
				state.stateRevision(),
				0L
			);
			return validation.successful()
				? PanelActionAvailability.available()
				: PanelActionAvailability.disabled(validation.message());
		}
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

	/**
	 * The ordinary forced-flow slot and the readiness slot are mutually exclusive authoring
	 * surfaces even though schema v3 stores them separately. Projecting both through one reason
	 * keeps console availability and prepare-time validation consistent.
	 */
	private static Optional<String> panelActionFlowBlockReason(final WorldStateV3 root) {
		boolean readinessActive = root.readiness()
			.map(ReadinessInstance::flow)
			.map(ForcedFlowInstance::runtime)
			.map(ForcedFlowRuntime::status)
			.filter(PixelTzzServerRuntime::unfinishedFlow)
			.isPresent();
		if (readinessActive) {
			return Optional.of("当前正在进行玩家准备；全部参与者完成或主持人终止该流程后才能发起新的强制流程。");
		}
		boolean ordinaryActive = root.core()
			.activeForcedFlow()
			.map(ForcedFlowInstance::runtime)
			.map(ForcedFlowRuntime::status)
			.filter(PixelTzzServerRuntime::unfinishedFlow)
			.isPresent();
		return ordinaryActive
			? Optional.of("必须先结束当前强制流程。")
			: Optional.empty();
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

	static boolean supportedPanelAction(final PanelActionDefinition action) {
		return action.operation() instanceof StartFlowOperation
			|| action.operation() instanceof TransitionPhaseOperation
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
		) || (
			operation.type().equals(APPROVE_TIMELINE)
				&& operation.id().equals(APPROVE_TIMELINE_OPERATION)
		) || (
			operation.type().equals(RESET_GAME_PROGRESS)
				&& operation.id().equals(RESET_GAME_PROGRESS_OPERATION)
		) || (
			operation.type().equals(CLEAR_ALL_STATE)
				&& operation.id().equals(CLEAR_ALL_STATE_OPERATION)
		) || timelineOperation(operation);
	}

	static boolean timelineOperation(final OperationKey operation) {
		return immediateTimelineOperation(operation)
			|| (
				operation.type().equals(EXTEND_INTERMISSION)
					&& operation.id().equals(EXTEND_INTERMISSION_OPERATION)
			)
			|| (
				operation.type().equals(FINISH_INTERMISSION)
					&& operation.id().equals(FINISH_INTERMISSION_OPERATION)
			)
			|| (
				operation.type().equals(INTERRUPT_TIMELINE)
					&& operation.id().equals(INTERRUPT_TIMELINE_OPERATION)
			)
			|| operation.type().equals(RETRY_TIMELINE_CALLBACK)
			|| operation.type().equals(HOST_FALLBACK_RESULT);
	}

	static boolean immediateTimelineOperation(final OperationKey operation) {
		return (
			operation.type().equals(PAUSE_TIMELINE)
				&& operation.id().equals(PAUSE_TIMELINE_OPERATION)
		) || (
			operation.type().equals(RESUME_TIMELINE)
				&& operation.id().equals(RESUME_TIMELINE_OPERATION)
		);
	}

	private static boolean resetOperation(final OperationKey operation) {
		return (
			operation.type().equals(RESET_GAME_PROGRESS)
				&& operation.id().equals(RESET_GAME_PROGRESS_OPERATION)
		) || (
			operation.type().equals(CLEAR_ALL_STATE)
				&& operation.id().equals(CLEAR_ALL_STATE_OPERATION)
		);
	}

	private static Optional<TimelineCallbackRetryKey> timelineCallbackRetryKey(
		final WorldStateV3 root,
		final OperationKey operation
	) {
		if (root == null) {
			return Optional.empty();
		}
		return decodeTimelineCallbackOperation(operation)
			.filter(key -> timelineCallbackStep(root, key).isPresent());
	}

	static Optional<TimelineCallbackRetryKey> decodeTimelineCallbackOperation(
		final OperationKey operation
	) {
		if (
			!operation.type().equals(RETRY_TIMELINE_CALLBACK)
				|| !operation.id().getNamespace().equals(PixelTzzPro.MOD_ID)
				|| !operation.id().getPath().startsWith(TIMELINE_CALLBACK_RETRY_PATH_PREFIX)
		) {
			return Optional.empty();
		}
		String encoded = operation.id()
			.getPath()
			.substring(TIMELINE_CALLBACK_RETRY_PATH_PREFIX.length());
		int separator = encoded.lastIndexOf('/');
		if (separator <= 0 || separator == encoded.length() - 1) {
			return Optional.empty();
		}
		String stepKey = encoded.substring(0, separator);
		String playerKey = encoded.substring(separator + 1);
		Optional<UUID> playerId;
		if (playerKey.equals("global")) {
			playerId = Optional.empty();
		} else {
			try {
				playerId = Optional.of(UUID.fromString(playerKey));
			} catch (IllegalArgumentException error) {
				return Optional.empty();
			}
		}
		return Optional.of(new TimelineCallbackRetryKey(stepKey, playerId));
	}

	private static Optional<WorldStateV3.CallbackStep> timelineCallbackStep(
		final WorldStateV3 root,
		final TimelineCallbackRetryKey key
	) {
		if (root == null || key == null) {
			return Optional.empty();
		}
		List<WorldStateV3.CallbackStep> matches = root.timeline()
			.stream()
			.flatMap(timeline -> java.util.stream.Stream.concat(
				timeline.currentTask().stream().flatMap(task -> task.callbackLedger().stream()),
				timeline.callbackLedger().stream()
			))
			.filter(step ->
				step.stepKey().equals(key.stepKey())
					&& step.playerId().equals(key.playerId())
			)
			.limit(2)
			.toList();
		if (matches.size() != 1) {
			return Optional.empty();
		}
		WorldStateV3.CallbackStep match = matches.getFirst();
		return match.status() == WorldStateV3.CallbackStepStatus.FAILED
				|| match.status() == WorldStateV3.CallbackStepStatus.OUTCOME_UNKNOWN
			? Optional.of(match)
			: Optional.empty();
	}

	static Optional<String> timelineFallbackResult(final OperationKey operation) {
		if (
			!operation.type().equals(HOST_FALLBACK_RESULT)
				|| !operation.id().getNamespace().equals(PixelTzzPro.MOD_ID)
				|| !operation.id().getPath().startsWith(TIMELINE_RESULT_PATH_PREFIX)
		) {
			return Optional.empty();
		}
		String resultId = operation.id()
			.getPath()
			.substring(TIMELINE_RESULT_PATH_PREFIX.length());
		return resultId.isBlank() ? Optional.empty() : Optional.of(resultId);
	}

	private static Optional<TaskDefinition> frozenCurrentTask(final WorldStateV3 root) {
		if (root == null) {
			return Optional.empty();
		}
		return root.timeline().flatMap(timeline -> {
			var restored = TimelineSnapshotCompiler.restore(timeline.gameId(), timeline.snapshot());
			if (!restored.success() || timeline.currentTask().isEmpty()) {
				return Optional.empty();
			}
			return Optional.ofNullable(
				restored.snapshot()
					.orElseThrow()
					.tasks()
					.get(timeline.currentTask().orElseThrow().taskId())
			);
		});
	}

	private static TimelineAuthority.ActionContext timelineActionContext(
		final WorldStateV3.TimelineInstance timeline
	) {
		WorldStateV3.TaskInstance task = timeline.currentTask().orElseThrow();
		return new TimelineAuthority.ActionContext(
			timeline.instanceId(),
			task.taskInstanceId(),
			task.taskId(),
			timeline.timelineRevision()
		);
	}

	private static String timelinePromptContext(
		final WorldStateV3 root,
		final WorldStateV3.TimelineInstance timeline
	) {
		WorldStateV3.TaskInstance task = timeline.currentTask().orElseThrow();
		String taskName = frozenCurrentTask(root)
			.map(definition -> definition.name().plainText())
			.filter(name -> !name.isBlank())
			.orElse("当前任务");
		return "当前任务：" + taskName + " · 当前状态：" + timelineStatusName(timeline);
	}

	private static String timelineStatusName(final WorldStateV3.TimelineInstance timeline) {
		WorldStateV3.TimelineStatus status = timeline.status() == WorldStateV3.TimelineStatus.BLOCKED
			? timeline.resumeStatus().orElse(WorldStateV3.TimelineStatus.BLOCKED)
			: timeline.status();
		String name = switch (status) {
			case PRE_START -> "等待开始";
			case STARTING -> "正在启动";
			case RUNNING -> "任务进行中";
			case SETTLING -> "正在结算";
			case INTERMISSION -> "任务间隔";
			case BLOCKED -> "异常阻塞";
			case COMPLETED -> "整局完成";
			case INTERRUPTED -> "整局已中断";
		};
		return timeline.status() == WorldStateV3.TimelineStatus.BLOCKED
			? name + "（异常阻塞）"
			: timeline.paused() ? name + "（已暂停）" : name;
	}

	private static String timelineControlFailure(
		final TimelineServerRuntime.RuntimeResult result
	) {
		return switch (result.code()) {
			case TIMELINE_NOT_ACTIVE -> "当前没有可控制的任务时间线。";
			case TIMELINE_COMPLETED -> "任务时间线已经结束。";
			case INTERMISSION_NOT_ACTIVE -> "当前不在任务间隔中。";
			case CALLBACK_FAILED -> "数据包回调执行失败，请在控制台查看可重试步骤。";
			case ACTION_UNAVAILABLE, TASK_STATE_MISMATCH -> "当前时间线状态不允许执行该操作。";
			case STATE_REVISION_STALE, REVISION_MISMATCH -> "时间线状态已经变化，请刷新后重试。";
			default -> "时间线操作未能完成，请刷新后重试；若持续出现请查看开发与诊断。";
		};
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

	private static boolean unfinishedFlow(final FlowInstanceStatus status) {
		return status == FlowInstanceStatus.ACTIVE || status == FlowInstanceStatus.BLOCKED;
	}

	private static String relatedStateDigest(
		final MinecraftServer server,
		final WorldStateV3 root,
		final OperationKey operation,
		final List<UUID> targets
	) {
		WorldStateV2 state = root.core();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digestPart(digest, operation.type());
			digestPart(digest, operation.id().toString());
			digestPart(
				digest,
				root.activeGameInstanceId().map(UUID::toString).orElse("")
			);
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
			root.timeline().ifPresent(timeline -> {
				digestPart(digest, timeline.instanceId().toString());
				digestPart(digest, timeline.status().getSerializedName());
				digestPart(
					digest,
					timeline.resumeStatus()
						.map(WorldStateV3.TimelineStatus::getSerializedName)
						.orElse("")
				);
				digestPart(digest, Boolean.toString(timeline.paused()));
				digestPart(digest, timeline.pauseReason().orElse(""));
				digestPart(digest, Long.toString(timeline.timelineRevision()));
				timeline.currentTask().ifPresent(task -> {
					digestPart(digest, task.taskInstanceId().toString());
					digestPart(digest, task.taskId().toString());
					digestPart(digest, task.status().getSerializedName());
					digestPart(
						digest,
						task.resumeStatus()
							.map(WorldStateV3.TaskStatus::getSerializedName)
							.orElse("")
					);
					digestPart(
						digest,
						task.result().map(WorldStateV3.FrozenResult::resultId).orElse("")
					);
					digestPart(
						digest,
						task.lifecycleCallbackTrigger()
							.map(trigger ->
								trigger.kind().getSerializedName()
									+ ":"
									+ trigger.triggerRevision()
							)
							.orElse("")
					);
					digestPart(
						digest,
						task.phaseTransitionTrigger()
							.map(trigger ->
								trigger.transitionId()
									+ ":"
									+ trigger.fromPhaseId()
									+ ":"
									+ trigger.toPhaseId()
									+ ":"
									+ trigger.cause().getSerializedName()
									+ ":"
									+ trigger.triggerRevision()
							)
							.orElse("")
					);
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
			default -> "主持人操作未能完成，请刷新后重试；若持续出现请查看开发与诊断。";
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
		if (operation.type().equals("transition_phase")) {
			return "已进入新的游戏阶段。";
		}
		if (
			operation.type().equals(APPROVE_TIMELINE)
				&& operation.id().equals(APPROVE_TIMELINE_OPERATION)
		) {
			return "已批准开局，任务时间线开始运行。";
		}
		if (
			operation.type().equals(RESET_GAME_PROGRESS)
				&& operation.id().equals(RESET_GAME_PROGRESS_OPERATION)
		) {
			return "本局进程已重置。";
		}
		if (
			operation.type().equals(CLEAR_ALL_STATE)
				&& operation.id().equals(CLEAR_ALL_STATE_OPERATION)
		) {
			return "Pixel TZZ 数据已清空。";
		}
		return immediateRoleChanged ? "身份已立即更新。" : "操作已完成。";
	}

	static String operationType(final PanelActionDefinition action) {
		return switch (action.operation()) {
			case StartFlowOperation ignored -> "start_flow";
			case AssignRoleOperation ignored -> "assign_role";
			case TransitionPhaseOperation ignored -> "transition_phase";
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
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		WorldStateV2 state = root == null ? null : root.core();
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
					List.of(),
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
				.orElseGet(() ->
					root.readiness()
						.map(ReadinessInstance::flow)
						.filter(flow -> flow.runtime().status() == FlowInstanceStatus.ACTIVE)
						.map(flow -> "正在等待玩家逐一确认准备。")
						.orElse("")
				);
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
				consolePhaseRoute(state, view.active()),
				isAdminEligible(player),
				currentHost,
				host,
				consolePlayers(server, root, view.active()),
				consoleActions(server, player, root, view),
				activeFlowSummary(server, root),
				recentFlowSummary(server, root),
				state.audit().totalEvents(),
				diagnostics.stream().limit(ConsoleSnapshotS2CPayload.MAX_DIAGNOSTICS).toList()
			)
		);
	}

	/**
	 * Projects a stable, bounded phase route for the host console.
	 *
	 * <p>The compact rail is only allowed to claim completed/future ordering when the reachable
	 * phase graph is a single linear chain. A branched or cyclic graph cannot be truthfully
	 * flattened without persisted route history, so it degrades to the authoritative current
	 * phase instead of presenting an invented order.</p>
	 */
	static List<PhaseEntry> consolePhaseRoute(
		final WorldStateV2 state,
		final DefinitionSnapshot definitions
	) {
		Identifier gameId = state.activeGameId().orElse(null);
		Identifier currentPhase = state.activePhaseId().orElse(null);
		GameDefinition game = gameId == null ? null : definitions.games().get(gameId);
		if (game == null || currentPhase == null) {
			return List.of();
		}
		List<Identifier> ordered = new ArrayList<>();
		Set<Identifier> visited = new LinkedHashSet<>();
		Identifier cursor = game.initialPhase();
		boolean linear = true;
		while (ordered.size() < ConsoleSnapshotS2CPayload.MAX_PHASES) {
			if (!visited.add(cursor)) {
				linear = false;
				break;
			}
			PhaseDefinition phase = definitions.phases().get(cursor);
			if (phase == null || !phase.game().equals(gameId)) {
				linear = false;
				break;
			}
			ordered.add(cursor);
			List<Identifier> nextPhases = phase.transitions()
				.stream()
				.filter(next -> {
					PhaseDefinition target = definitions.phases().get(next);
					return target != null && target.game().equals(gameId);
				})
				.sorted(Comparator.comparing(Identifier::toString))
				.toList();
			if (nextPhases.size() > 1) {
				linear = false;
				break;
			}
			if (nextPhases.isEmpty()) {
				break;
			}
			cursor = nextPhases.getFirst();
		}
		if (!linear || !ordered.contains(currentPhase)) {
			PhaseDefinition current = definitions.phases().get(currentPhase);
			return current == null || !current.game().equals(gameId)
				? List.of()
				: List.of(new PhaseEntry(currentPhase, current.name().json(), "current"));
		}
		int currentIndex = ordered.indexOf(currentPhase);
		List<PhaseEntry> route = new ArrayList<>(ordered.size());
		for (int index = 0; index < ordered.size(); index++) {
			Identifier phaseId = ordered.get(index);
			PhaseDefinition phase = definitions.phases().get(phaseId);
			String relation = index < currentIndex
				? "completed"
				: index == currentIndex ? "current" : "future";
			route.add(new PhaseEntry(phaseId, phase.name().json(), relation));
		}
		return List.copyOf(route);
	}

	private static List<ActionEntry> consoleActions(
		final MinecraftServer server,
		final ServerPlayer player,
		final WorldStateV3 root,
		final View view
	) {
		WorldStateV2 state = root.core();
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
			addTimelineControlActions(server, player, root, view, result);
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
		Optional<String> blockingFlow = panelActionFlowBlockReason(root);
		boolean activeFlow = blockingFlow.isPresent();
		FrozenPredicateEvaluator predicateEvaluator = new FrozenPredicateEvaluator(
			server,
			view.active().predicateDocuments()
		);
		PredicateContext predicateContext = panelPredicateContext(state, player);
		view.active().panelActions().values()
			.stream()
			.filter(action -> state.activeGameId().filter(action.game()::equals).isPresent())
			.filter(action ->
				state.activeGameId()
					.map(view.active().games()::get)
					.flatMap(game -> game == null ? Optional.empty() : game.readiness())
					.map(readiness -> !readiness.action().equals(action.id()))
					.orElse(true)
			)
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
						root,
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
									? blockingFlow.orElseThrow()
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

	private static void addTimelineControlActions(
		final MinecraftServer server,
		final ServerPlayer player,
		final WorldStateV3 root,
		final View view,
		final List<ActionEntry> actions
	) {
		WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
		if (
			timeline == null
				&& view.healthy()
				&& view.active().usable()
				&& root.activeGameInstanceId().isPresent()
		) {
			var approval = TimelineApprovalAuthority.approve(
				root,
				view.active(),
				new ApprovalContext(
					player.getUUID(),
					root.activeGameInstanceId().orElseThrow(),
					root.stateRevision(),
					view.active().generation(),
					new UUID(0L, 11L),
					new UUID(0L, 12L),
					server.getTickCount(),
					onlinePlayerIds(server)
				)
			);
			if (approval.successful()) {
				actions.add(
					controlAction(
						APPROVE_TIMELINE,
						APPROVE_TIMELINE_OPERATION,
						"primary",
						-900,
						"批准开局",
						"冻结本局任务计划与参与者，并正式启动首个任务。",
						"#D7B45A",
						true
					)
				);
			}
		}
		if (timeline == null) {
			actions.add(
				controlAction(
					CLEAR_ALL_STATE,
					CLEAR_ALL_STATE_OPERATION,
					"system",
					1_000,
					"清空全部数据",
					"清除 Pixel TZZ 在该世界中的主持人、玩家、流程、任务与审计数据。",
					"#E94F64",
					true
				)
			);
			return;
		}

		WorldStateV3.TaskInstance task = timeline.currentTask().orElse(null);
		boolean callbackIdle = task != null
			&& task.lifecycleCallbackTrigger().isEmpty()
			&& task.phaseTransitionTrigger().isEmpty();
		boolean pausable = callbackIdle
			&& !timeline.paused()
			&& (
				timeline.status() == WorldStateV3.TimelineStatus.RUNNING
					|| timeline.status() == WorldStateV3.TimelineStatus.INTERMISSION
			);
		boolean hostPaused = callbackIdle
			&& timeline.paused()
			&& timeline.pauseReason().filter(HOST_PAUSE_REASON::equals).isPresent();
		if (pausable) {
			actions.add(
				controlAction(
					PAUSE_TIMELINE,
					PAUSE_TIMELINE_OPERATION,
					"primary",
					-900,
					"暂停任务时间线",
					"暂停正式游戏计时或当前任务间隔；可由主持人继续。",
					"#55D7E6",
					false
				)
			);
		} else if (hostPaused) {
			actions.add(
				controlAction(
					RESUME_TIMELINE,
					RESUME_TIMELINE_OPERATION,
					"primary",
					-900,
					"继续任务时间线",
					"结束主持人暂停，并从原状态继续任务与计时。",
					"#55D7E6",
					false
				)
			);
		}

		if (
			timeline.status() == WorldStateV3.TimelineStatus.INTERMISSION
				&& callbackIdle
		) {
			actions.add(
				controlAction(
					EXTEND_INTERMISSION,
					EXTEND_INTERMISSION_OPERATION,
					"primary",
					-850,
					"延长间隔 60 秒",
					"保留下一任务与当前结果，仅延长本次任务间隔。",
					"#55D7E6",
					true
				)
			);
			actions.add(
				controlAction(
					FINISH_INTERMISSION,
					FINISH_INTERMISSION_OPERATION,
					"primary",
					-840,
					"提前结束间隔",
					"立即执行间隔结束回调，并进入已冻结的下一任务或结束阶段。",
					"#D7B45A",
					true
				)
			);
		}

		if (timeline.status() == WorldStateV3.TimelineStatus.BLOCKED) {
			failedTimelineCallbackSteps(timeline).forEach(step -> {
				TimelineCallbackRetryKey retryKey = new TimelineCallbackRetryKey(
					step.stepKey(),
					step.playerId()
				);
				if (timelineCallbackStep(root, retryKey).filter(step::equals).isEmpty()) {
					return;
				}
				Identifier operationId = timelineCallbackOperationId(step);
				if (operationId == null) {
					return;
				}
				String owner = step.playerId()
					.map(playerId -> callbackPlayerName(server, root.core(), playerId))
					.map(name -> " · " + name)
					.orElse("");
				boolean available = step.playerId()
					.map(playerId -> server.getPlayerList().getPlayer(playerId) != null)
					.orElse(true);
				actions.add(
					controlAction(
						RETRY_TIMELINE_CALLBACK,
						operationId,
						"primary",
						-880,
						"重试任务回调",
						step.functionId() + owner + " · 已尝试 " + step.attemptCount() + " 次",
						"#E94F64",
						true,
						available,
						available ? "" : "对应玩家当前离线，重连后才能重试该回调。"
					)
				);
			});
		}

		TaskDefinition frozenTask = frozenCurrentTask(root).orElse(null);
		if (
			task != null
				&& frozenTask != null
				&& timeline.status() == WorldStateV3.TimelineStatus.SETTLING
				&& task.result().isEmpty()
		) {
			frozenTask.results()
				.values()
				.stream()
				.filter(result -> TimelineServerRuntime.hostFallbackEligible(timeline, result))
				.sorted(Comparator.comparing(TaskResult::id))
				.forEach(result -> {
					Identifier operationId = Identifier.tryParse(
						PixelTzzPro.MOD_ID + ":" + TIMELINE_RESULT_PATH_PREFIX + result.id()
					);
					if (operationId != null) {
						actions.add(
							controlAction(
								HOST_FALLBACK_RESULT,
								operationId,
								"primary",
								-870,
								"人工指定结果：" + result.name().plainText(),
								"仅用于数据包显式开放的结算兜底；确认后结果不可更换。",
								"#E94F64",
								true
							)
						);
					}
				});
		}

		boolean terminal = timeline.status() == WorldStateV3.TimelineStatus.COMPLETED
			|| timeline.status() == WorldStateV3.TimelineStatus.INTERRUPTED;
		if (terminal) {
			boolean activeForcedFlow = root.core()
				.activeForcedFlow()
				.map(flow -> unfinishedFlow(flow.runtime().status()))
				.orElse(false);
			if (!activeForcedFlow) {
				actions.add(
					controlAction(
						RESET_GAME_PROGRESS,
						RESET_GAME_PROGRESS_OPERATION,
						"primary",
						-900,
						"重置本局进程",
						"保留主持人与注册名单，清除本局任务、回顾、流程及玩家游戏状态。",
						"#D7B45A",
						true
					)
				);
			}
		} else {
			actions.add(
				controlAction(
					INTERRUPT_TIMELINE,
					INTERRUPT_TIMELINE_OPERATION,
					"system",
					900,
					"紧急终止整局",
					"立即中断当前任务并保留回顾；已经发生的世界效果不会自动撤销。",
					"#E94F64",
					true
				)
			);
		}
		actions.add(
			controlAction(
				CLEAR_ALL_STATE,
				CLEAR_ALL_STATE_OPERATION,
				"system",
				1_000,
				"清空全部数据",
				"清除 Pixel TZZ 在该世界中的主持人、玩家、流程、任务与审计数据。",
				"#E94F64",
				true
			)
		);
	}

	private static List<WorldStateV3.CallbackStep> failedTimelineCallbackSteps(
		final WorldStateV3.TimelineInstance timeline
	) {
		return java.util.stream.Stream.concat(
			timeline.currentTask().stream().flatMap(task -> task.callbackLedger().stream()),
			timeline.callbackLedger().stream()
		)
			.filter(step ->
				step.status() == WorldStateV3.CallbackStepStatus.FAILED
					|| step.status() == WorldStateV3.CallbackStepStatus.OUTCOME_UNKNOWN
			)
			.sorted(
				Comparator.comparing(WorldStateV3.CallbackStep::stepKey)
					.thenComparing(step -> step.playerId().map(UUID::toString).orElse(""))
			)
			.limit(ConsoleSnapshotS2CPayload.MAX_ACTIONS / 2L)
			.toList();
	}

	static Identifier timelineCallbackOperationId(
		final WorldStateV3.CallbackStep step
	) {
		String owner = step.playerId().map(UUID::toString).orElse("global");
		return Identifier.tryParse(
			PixelTzzPro.MOD_ID
				+ ":"
				+ TIMELINE_CALLBACK_RETRY_PATH_PREFIX
				+ step.stepKey()
				+ "/"
				+ owner
		);
	}

	private static ActionEntry controlAction(
		final String type,
		final Identifier id,
		final String section,
		final int order,
		final String label,
		final String description,
		final String color,
		final boolean requiresConfirmation
	) {
		return controlAction(
			type,
			id,
			section,
			order,
			label,
			description,
			color,
			requiresConfirmation,
			true,
			""
		);
	}

	private static ActionEntry controlAction(
		final String type,
		final Identifier id,
		final String section,
		final int order,
		final String label,
		final String description,
		final String color,
		final boolean requiresConfirmation,
		final boolean enabled,
		final String disabledReason
	) {
		return new ActionEntry(
			type,
			id,
			section,
			order,
			jsonText(label),
			jsonText(description),
			color,
			enabled,
			enabled ? "" : jsonText(disabledReason),
			"none",
			0,
			0,
			requiresConfirmation
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
		final WorldStateV3 state
	) {
		Optional<ActiveFlowSummary> ordinary = flowSummary(
			server,
			state,
			state.core().activeForcedFlow(),
			Set.of(FlowInstanceStatus.ACTIVE, FlowInstanceStatus.BLOCKED)
		);
		return ordinary.or(() ->
			flowSummary(
				server,
				state,
				state.readiness().map(ReadinessInstance::flow),
				Set.of(FlowInstanceStatus.ACTIVE)
			)
		);
	}

	private static Optional<ActiveFlowSummary> recentFlowSummary(
		final MinecraftServer server,
		final WorldStateV3 state
	) {
		Optional<ActiveFlowSummary> readiness = flowSummary(
			server,
			state,
			state.readiness().map(ReadinessInstance::flow),
			Set.of(FlowInstanceStatus.COMPLETED)
		);
		return readiness.or(() -> flowSummary(
			server,
			state,
			state.core().activeForcedFlow(),
			Set.of(FlowInstanceStatus.COMPLETED, FlowInstanceStatus.CANCELED)
		));
	}

	private static List<Target> consolePlayers(
		final MinecraftServer server,
		final WorldStateV3 root,
		final DefinitionSnapshot definitions
	) {
		WorldStateV2 state = root.core();
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
				root.readiness()
					.map(ReadinessInstance::flow)
					.map(ForcedFlowInstance::runtime)
					.map(ForcedFlowRuntime::members)
					.map(members -> members.get(record.playerId()))
					.map(member -> member.status().getSerializedName())
					.orElseGet(() ->
						record.activeFlowParticipation()
							.map(participation -> participation.status().getSerializedName())
							.orElse("none")
					),
				displayFlows,
				true,
				""
			))
			.toList();
	}

	private static Optional<ActiveFlowSummary> flowSummary(
		final MinecraftServer server,
		final WorldStateV3 root,
		final Optional<ForcedFlowInstance> source,
		final Set<FlowInstanceStatus> statuses
	) {
		WorldStateV2 state = root.core();
		return source
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
						memberFieldSummaries(
							root,
							flow,
							definition,
							presentationDefinitions,
							member
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

	private static List<MemberFieldSummary> memberFieldSummaries(
		final WorldStateV3 root,
		final ForcedFlowInstance instance,
		final FlowDefinition flow,
		final DefinitionSnapshot definitions,
		final FlowMemberState member
	) {
		if (flow == null) {
			return List.of();
		}
		Set<Identifier> fieldIds = flow.nodes()
			.values()
			.stream()
			.filter(ChoiceNode.class::isInstance)
			.map(ChoiceNode.class::cast)
			.map(ChoiceNode::field)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<MemberFieldSummary> summaries = new ArrayList<>();
		for (Identifier fieldId : fieldIds) {
			FieldDefinition field = definitions.fields().get(fieldId);
			if (field == null || field.exclusiveChoice().isEmpty()) {
				continue;
			}
			ExclusiveReservation held = root.exclusiveReservations()
				.stream()
				.filter(reservation ->
					reservation.gameId().equals(instance.identity().gameId())
						&& reservation.fieldId().equals(fieldId)
						&& reservation.ownerId().equals(member.playerId())
						&& reservation.status() == ReservationStatus.HELD
						&& reservation.flowInstanceId()
							.filter(instance.identity().instanceId()::equals)
							.isPresent()
				)
				.findFirst()
				.orElse(null);
			boolean completed = member.status() == FlowMemberStatus.COMPLETED
				|| member.status() == FlowMemberStatus.ALREADY_COMPLETE;
			ExclusiveReservation selected = held != null
				? held
				: completed
					? root.exclusiveReservations()
						.stream()
						.filter(reservation ->
							reservation.gameId().equals(instance.identity().gameId())
								&& reservation.fieldId().equals(fieldId)
								&& reservation.ownerId().equals(member.playerId())
								&& reservation.status() == ReservationStatus.LOCKED
						)
						.findFirst()
						.orElse(null)
					: null;
			String valueName = selected == null
				? ""
				: field.exclusiveChoice()
					.orElseThrow()
					.options()
					.stream()
					.filter(option -> option.value().equals(selected.value()))
					.map(ExclusiveChoiceOption::name)
					.map(RichText::json)
					.findFirst()
					.orElseGet(() -> jsonText(selected.value()));
			summaries.add(
				new MemberFieldSummary(
					field.id(),
					field.name().json(),
					valueName,
					selected == null
						? "unselected"
						: selected.status() == ReservationStatus.HELD ? "held" : "locked"
				)
			);
			if (summaries.size() >= ConsoleSnapshotS2CPayload.MAX_MEMBER_FIELDS) {
				break;
			}
		}
		return List.copyOf(summaries);
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
				WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
				ActiveFlowDefinitions active = activeFlowDefinitions(state).orElse(null);
				FlowRosterAuthority.V3Result addResult =
					instance == null || root == null || active == null
					? null
					: FlowRosterAuthority.addMembers(
						root,
						active.definitions(),
						actor.getUUID(),
						instance.identity().instanceId(),
						instance.runtime().instanceRevision(),
						List.of(new OnlineMember(connected.playerId(), connected.name(), true)),
						UUID::randomUUID,
						System.currentTimeMillis(),
						root.activeGameInstanceId().orElseThrow()
					);
				eligible = addResult != null && addResult.successful();
				reason = eligible
					? ""
					: addResult == null ? "当前没有活动流程" : addResult.message();
			} else if (eligible && immediateRoleAction(action)) {
				AssignRoleOperation assign = (AssignRoleOperation) action.operation();
				WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElseThrow();
				FlowRosterAuthority.V3Result roleResult = FlowRosterAuthority.assignRoleImmediate(
					root,
					definitions,
					actor.getUUID(),
					state.stateRevision(),
					assign.role(),
					List.of(connected.playerId()),
					System.currentTimeMillis(),
					root.activeGameInstanceId().orElseThrow(),
					server.getTickCount()
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
				sortedTargets(targets)
			)
		);
	}

	private static List<Target> sortedTargets(final List<Target> targets) {
		return targets.stream()
			.sorted(
				Comparator.<Target>comparingInt(target -> target.eligible() ? 0 : 1)
					.thenComparingInt(target -> target.online() ? 0 : 1)
					.thenComparing(Target::name, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(Target::playerId)
			)
			.toList();
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
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		if (root == null) {
			return;
		}
		Set<UUID> members = new LinkedHashSet<>();
		root.core().activeForcedFlow()
			.map(ForcedFlowInstance::runtime)
			.map(ForcedFlowRuntime::members)
			.map(Map::keySet)
			.ifPresent(members::addAll);
		root.readiness()
			.map(ReadinessInstance::flow)
			.map(ForcedFlowInstance::runtime)
			.map(ForcedFlowRuntime::members)
			.map(Map::keySet)
			.ifPresent(members::addAll);
		members.forEach(playerId -> {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null && verified(player)) {
				sendCurrentForcedPage(server, player);
			}
		});
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

	private static void refreshReadinessPages(
		final MinecraftServer server,
		final WorldStateV3 previous,
		final WorldStateV3 next,
		final Set<UUID> playerIds,
		final String reason
	) {
		ForcedFlowInstance previousFlow = previous.readiness()
			.map(ReadinessInstance::flow)
			.orElse(null);
		if (previousFlow == null) {
			return;
		}
		long revision = next.stateRevision();
		for (UUID playerId : playerIds) {
			FlowMemberState oldMember = previousFlow.runtime().members().get(playerId);
			Optional<UUID> oldPage = oldMember == null
				? Optional.empty()
				: oldMember.pageInstanceId();
			Optional<UUID> nextPage = next.readiness()
				.map(ReadinessInstance::flow)
				.map(ForcedFlowInstance::runtime)
				.map(ForcedFlowRuntime::members)
				.map(members -> members.get(playerId))
				.flatMap(member -> member == null ? Optional.empty() : member.pageInstanceId());
			if (oldPage.isEmpty() || oldPage.equals(nextPage)) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (
				player != null
					&& verified(player)
					&& ServerPlayNetworking.canSend(player, ForcedPageReleaseS2CPayload.TYPE)
			) {
				ServerPlayNetworking.send(
					player,
					new ForcedPageReleaseS2CPayload(
						NetworkProtocol.CURRENT_VERSION,
						previousFlow.identity().instanceId(),
						oldPage.orElseThrow(),
						reason,
						revision
					)
				);
			}
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
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		/*
		 * A page sent to a verified live connection must also be writable by that connection.
		 * Server startup deliberately marks persisted members offline before clients handshake.
		 * Reconcile that marker again at the page-delivery boundary so a missed/ordered-late
		 * handshake reconciliation cannot restore a visually valid but permanently rejected page.
		 */
		FlowMemberState ordinaryMember = root == null
			? null
			: root.core()
				.activeForcedFlow()
				.map(ForcedFlowInstance::runtime)
				.map(ForcedFlowRuntime::members)
				.map(members -> members.get(player.getUUID()))
				.orElse(null);
		if (
			ordinaryMember != null
				&& ordinaryMember.status() == FlowMemberStatus.OFFLINE
		) {
			if (!restoreMemberOnline(server, player)) {
				PixelTzzPro.LOGGER.warn(
					"Refusing to send an unwritable offline forced page to {}",
					player.getUUID()
				);
				return;
			}
			wrapper = PixelTzzWorldState.get(server);
			root = wrapper.currentV3().orElse(null);
		}
		WorldStateV2 state = root == null ? null : root.core();
		ForcedFlowInstance instance = root == null
			? null
			: currentPageFlow(root, player.getUUID()).orElse(null);
		if (state == null || instance == null) {
			return;
		}
		boolean readinessPage = root.readiness()
			.map(ReadinessInstance::flow)
			.map(ForcedFlowInstance::identity)
			.map(WorldStateV2.ForcedFlowIdentity::instanceId)
			.filter(instance.identity().instanceId()::equals)
			.isPresent();
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
			persistPageFlowBlock(
				server,
				wrapper,
				state,
				readinessPage,
				OperationCode.SNAPSHOT_INVALID,
				restored.message(),
				Optional.of(player.getUUID())
			);
			return;
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		FlowDefinition flow = definitions.flows().get(instance.identity().flowId());
		if (flow == null) {
			persistPageFlowBlock(
				server,
				wrapper,
				state,
				readinessPage,
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
			persistPageFlowBlock(
				server,
				wrapper,
				state,
				readinessPage,
				OperationCode.SNAPSHOT_INVALID,
				"current forced-flow node is missing or is not displayable",
				Optional.of(player.getUUID())
			);
			return;
		}
		PageDefinition page = definitions.pages().get(pageId);
		if (page == null) {
			persistPageFlowBlock(
				server,
				wrapper,
				state,
				readinessPage,
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
			persistPageFlowBlock(
				server,
				wrapper,
				state,
				readinessPage,
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
				persistPageFlowBlock(
					server,
					wrapper,
					state,
					readinessPage,
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
			persistPageFlowBlock(
				server,
				wrapper,
				state,
				readinessPage,
				OperationCode.SNAPSHOT_INVALID,
				"forced page bundle failed: " + message,
				Optional.of(player.getUUID())
			);
		}
	}

	private static boolean hasCurrentForcedPage(
		final WorldStateV3 state,
		final UUID playerId
	) {
		return currentPageFlow(state, playerId).isPresent();
	}

	private static Optional<ForcedFlowInstance> currentPageFlow(
		final WorldStateV3 state,
		final UUID playerId
	) {
		ForcedFlowInstance ordinary = state.core().activeForcedFlow().orElse(null);
		if (hasPage(ordinary, playerId)) {
			return Optional.of(ordinary);
		}
		ForcedFlowInstance readiness = state.readiness()
			.map(ReadinessInstance::flow)
			.orElse(null);
		return hasPage(readiness, playerId) ? Optional.of(readiness) : Optional.empty();
	}

	private static boolean hasPage(
		final ForcedFlowInstance instance,
		final UUID playerId
	) {
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

	private static void persistPageFlowBlock(
		final MinecraftServer server,
		final PixelTzzWorldState wrapper,
		final WorldStateV2 state,
		final boolean readinessPage,
		final OperationCode code,
		final String message,
		final Optional<UUID> playerId
	) {
		if (readinessPage) {
			PixelTzzPro.LOGGER.error(
				"Readiness page snapshot failed for {}: {} ({})",
				playerId.map(UUID::toString).orElse("unknown"),
				message,
				code.serializedName()
			);
			return;
		}
		persistFlowBlock(server, wrapper, state, code, message, playerId);
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
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElse(null);
		WorldStateV2 state = root == null ? null : root.core();
		pushHostConsoleSnapshot(server);
		if (state == null) {
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		ForcedFlowInstance ordinary = state.activeForcedFlow().orElse(null);
		ForcedFlowInstance readiness = root.readiness()
			.map(ReadinessInstance::flow)
			.orElse(null);
		ForcedFlowInstance instance = ordinary != null
				&& (
					ordinary.runtime().status() == FlowInstanceStatus.ACTIVE
						|| ordinary.runtime().status() == FlowInstanceStatus.BLOCKED
				)
			? ordinary
			: readiness != null ? readiness : ordinary;
		if (instance == null) {
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		boolean readinessProjection = readiness != null
			&& readiness.identity().instanceId().equals(instance.identity().instanceId());
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
			if (!readinessProjection) {
				persistFlowBlock(
					server,
					PixelTzzWorldState.get(server),
					state,
					OperationCode.SNAPSHOT_INVALID,
					restored.message(),
					Optional.empty(),
					false
				);
			}
			HOST_FLOW_BOSS_BAR.clear();
			return;
		}
		FlowDefinition flow = restored.snapshot().orElseThrow().flows().get(instance.identity().flowId());
		if (flow == null) {
			if (!readinessProjection) {
				persistFlowBlock(
					server,
					PixelTzzWorldState.get(server),
					state,
					OperationCode.SNAPSHOT_INVALID,
					"frozen flow definition is unavailable",
					Optional.empty(),
					false
				);
			}
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

	private static void onTerminalOpen(
		final TerminalOpenC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.protocolVersion())) {
			return;
		}
		ControlRequestGate.Decision admitted = TERMINAL_OPEN_REQUEST_GATE.admit(
			player.getUUID(),
			payload.requestSequence(),
			System.currentTimeMillis()
		);
		if (admitted != ControlRequestGate.Decision.ACCEPTED) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				admitted == ControlRequestGate.Decision.RATE_LIMITED
					? OperationCode.RATE_LIMITED
					: OperationCode.REQUEST_REPLAYED,
				admitted == ControlRequestGate.Decision.RATE_LIMITED
					? "玩家终端打开请求过于频繁，请稍后再试。"
					: "玩家终端打开请求已经失效，请重新打开暂停菜单。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}

		TerminalAuthorityResult resolved = resolveTerminalAuthority(
			context.server(),
			player
		);
		if (!resolved.successful()) {
			sendTerminalOpenFailure(
				context.server(),
				player,
				payload.requestSequence(),
				resolved
			);
			return;
		}
		TerminalAuthority authority = resolved.authority().orElseThrow();
		PlayerTerminalSessions.Session previousVisibleSession =
			PLAYER_TERMINAL_SESSIONS.find(player.getUUID()).orElse(null);
		PlayerTerminalSessions.OpenResult opened = PLAYER_TERMINAL_SESSIONS.open(
			player.getUUID(),
			authority.definitions().generation(),
			authority.root().stateRevision(),
			authority.route().routeId(),
			authority.route().pageId().orElseThrow(),
			authority.takeoverAllowed(),
			context.server().getTickCount()
		);
		if (!opened.successful()) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.RESOURCE_BLOCKED,
				opened.message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerTerminalSessions.Session session = opened.session().orElseThrow();
		TerminalPageBundleResult delivery = sendTerminalPageBundle(
			context.server(),
			player,
			authority,
			session
		);
		if (!delivery.successful()) {
			failTerminalPageMutationDelivery(
				context.server(),
				player,
				authority,
				previousVisibleSession == null ? session : previousVisibleSession,
				Optional.of(payload.requestSequence()),
				delivery
			);
		}
	}

	private static void sendTerminalOpenFailure(
		final MinecraftServer server,
		final ServerPlayer player,
		final long requestSequence,
		final TerminalAuthorityResult result
	) {
		sendOperationResult(
			player,
			requestSequence,
			result.code(),
			result.message(),
			PixelTzzWorldState.get(server).stateRevision(),
			DefinitionRegistry.INSTANCE.view().active().generation(),
			false
		);
	}

	private static void onTerminalIntent(
		final TerminalIntentC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		ServerPlayer player = context.player();
		if (!verified(player) || !NetworkProtocol.isCompatible(payload.protocolVersion())) {
			return;
		}
		PlayerTerminalSessions.Session session = PLAYER_TERMINAL_SESSIONS.find(
			player.getUUID()
		).orElse(null);
		if (session == null || !terminalEnvelopeMatches(payload, session)) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				session == null
					? OperationCode.ACTION_UNAVAILABLE
					: terminalEnvelopeFailure(payload, session),
				session == null
					? "玩家终端会话已经关闭，请重新打开。"
					: "玩家终端页面或状态已经变化，本次操作未执行。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		PlayerTerminalSessions.SequenceResult sequence = PLAYER_TERMINAL_SESSIONS.acceptSequence(
			player.getUUID(),
			payload.terminalSessionId(),
			payload.requestSequence(),
			context.server().getTickCount()
		);
		if (sequence.status() != PlayerTerminalSessions.SequenceStatus.ACCEPTED) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				sequence.status() == PlayerTerminalSessions.SequenceStatus.REPLAYED
					? OperationCode.REQUEST_REPLAYED
					: OperationCode.REVISION_MISMATCH,
				sequence.status() == PlayerTerminalSessions.SequenceStatus.REPLAYED
					? "该玩家终端请求已经处理过。"
					: "玩家终端请求顺序不一致，请重新打开终端。",
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		session = sequence.session().orElseThrow();
		cancelPendingPlayerActionConfirmations(player.getUUID());
		cancelPendingTerminalHostConfirmations(player.getUUID());

		TerminalAuthorityResult resolved = resolveTerminalAuthority(
			context.server(),
			player
		);
		if (!resolved.successful()) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				resolved.code(),
				resolved.message(),
				PixelTzzWorldState.get(context.server()).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			invalidateTerminalSession(
				context.server(),
				player,
				session,
				"authority_unavailable",
				resolved.message()
			);
			return;
		}
		TerminalAuthority authority = resolved.authority().orElseThrow();
		if (
			resynchronizeTerminalIntentPage(
				context.server(),
				player,
				payload.requestSequence(),
				session,
				authority
			)
		) {
			return;
		}
		switch (payload.intent()) {
			case BACK -> handleTerminalBack(
				context.server(),
				player,
				payload,
				authority
			);
			case REFRESH -> handleTerminalRefresh(
				context.server(),
				player,
				payload,
				authority
			);
			case ACTION -> handleTerminalRegisteredAction(
				context.server(),
				player,
				payload,
				authority
			);
			case HISTORY_DETAIL -> handleTerminalHistoryDetail(
				context.server(),
				player,
				payload,
				authority
			);
			case HISTORY_REPLAY -> handleTerminalHistoryReplay(
				context.server(),
				player,
				payload,
				authority
			);
		}
	}

	/**
	 * Reconciles a clicked terminal page with the current route and every server-owned child-page
	 * authorization before the intent is dispatched.
	 *
	 * <p>The request sequence has already been consumed at this point. If authority changed between
	 * periodic refreshes, the visible page is replaced first and this click is rejected neutrally;
	 * the player may then act against the freshly issued page instance.
	 */
	private static boolean resynchronizeTerminalIntentPage(
		final MinecraftServer server,
		final ServerPlayer player,
		final long requestSequence,
		final PlayerTerminalSessions.Session session,
		final TerminalAuthority authority
	) {
		Identifier rootPage = authority.route().pageId().orElseThrow();
		if (terminalRouteChanged(session, authority, rootPage)) {
			PlayerTerminalSessions.MutationResult replaced =
				PLAYER_TERMINAL_SESSIONS.replaceRoute(
					player.getUUID(),
					authority.definitions().generation(),
					authority.root().stateRevision(),
					authority.route().routeId(),
					rootPage,
					authority.takeoverAllowed()
				);
			if (!replaced.successful()) {
				failTerminalPageMutation(
					server,
					player,
					session,
					Optional.of(requestSequence),
					OperationCode.SNAPSHOT_INVALID,
					authority.root().stateRevision(),
					authority.definitions().generation(),
					"intent_route_replace_failed",
					replaced.message()
				);
				return true;
			}
			TerminalPageBundleResult delivery = sendTerminalPageBundle(
				server,
				player,
				authority,
				replaced.session().orElseThrow()
			);
			if (!delivery.successful()) {
				failTerminalPageMutationDelivery(
					server,
					player,
					authority,
					session,
					Optional.of(requestSequence),
					delivery
				);
				return true;
			}
			clearPendingPlayerActionConfirmations(player.getUUID());
			clearPendingTerminalHostConfirmationMetadata(player.getUUID());
			CONFIRMATIONS.cancelOperator(player.getUUID());
			sendOperationResult(
				player,
				requestSequence,
				OperationCode.REVISION_MISMATCH,
				"玩家终端入口已经变化，页面已更新，请重新操作。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return true;
		}

		int retainedDepth = authorizedTerminalStackDepth(
			server,
			player,
			authority,
			session
		);
		if (retainedDepth >= session.stack().size()) {
			return false;
		}
		PlayerTerminalSessions.MutationResult truncated =
			PLAYER_TERMINAL_SESSIONS.truncateToDepth(
				player.getUUID(),
				retainedDepth,
				authority.root().stateRevision(),
				authority.takeoverAllowed()
			);
		if (!truncated.successful()) {
			failTerminalPageMutation(
				server,
				player,
				session,
				Optional.of(requestSequence),
				OperationCode.SNAPSHOT_INVALID,
				authority.root().stateRevision(),
				authority.definitions().generation(),
				"intent_navigation_truncate_failed",
				truncated.message()
			);
			return true;
		}
		TerminalPageBundleResult delivery = sendTerminalPageBundle(
			server,
			player,
			authority,
			truncated.session().orElseThrow()
		);
		if (!delivery.successful()) {
			failTerminalPageMutationDelivery(
				server,
				player,
				authority,
				session,
				Optional.of(requestSequence),
				delivery
			);
			return true;
		}
		clearPendingPlayerActionConfirmations(player.getUUID());
		clearPendingTerminalHostConfirmationMetadata(player.getUUID());
		CONFIRMATIONS.cancelOperator(player.getUUID());
		sendOperationResult(
			player,
			requestSequence,
			OperationCode.ACTION_UNAVAILABLE,
			"当前页面已不再适用于你的身份或游戏阶段，已返回上一级。",
			authority.root().stateRevision(),
			authority.definitions().generation(),
			false
		);
		return true;
	}

	private static boolean terminalEnvelopeMatches(
		final TerminalIntentC2SPayload payload,
		final PlayerTerminalSessions.Session session
	) {
		return session.sessionId().equals(payload.terminalSessionId())
			&& session.current().instanceId().equals(payload.pageInstanceId())
			&& session.definitionGeneration() == payload.definitionGeneration()
			&& session.stateRevision() == payload.stateRevision()
			&& session.routeRevision() == payload.routeRevision();
	}

	private static OperationCode terminalEnvelopeFailure(
		final TerminalIntentC2SPayload payload,
		final PlayerTerminalSessions.Session session
	) {
		if (session.definitionGeneration() != payload.definitionGeneration()) {
			return OperationCode.DEFINITION_GENERATION_STALE;
		}
		if (session.stateRevision() != payload.stateRevision()) {
			return OperationCode.STATE_REVISION_STALE;
		}
		if (
			!session.sessionId().equals(payload.terminalSessionId())
				|| !session.current().instanceId().equals(payload.pageInstanceId())
		) {
			return OperationCode.NODE_MISMATCH;
		}
		return OperationCode.REVISION_MISMATCH;
	}

	private static void handleTerminalBack(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalIntentC2SPayload payload,
		final TerminalAuthority authority
	) {
		PlayerTerminalSessions.Session previousVisibleSession =
			PLAYER_TERMINAL_SESSIONS.find(player.getUUID()).orElse(null);
		if (previousVisibleSession == null) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"玩家终端会话已经关闭，请重新打开。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerTerminalSessions.MutationResult result = PLAYER_TERMINAL_SESSIONS.back(
			player.getUUID(),
			authority.root().stateRevision()
		);
		if (!result.successful()) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				result.message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerTerminalSessions.Session updated = result.session().orElseThrow();
		TerminalPageBundleResult delivery = sendTerminalPageBundle(
			server,
			player,
			authority,
			updated
		);
		if (!delivery.successful()) {
			failTerminalPageMutationDelivery(
				server,
				player,
				authority,
				previousVisibleSession,
				Optional.of(payload.requestSequence()),
				delivery
			);
			return;
		}
		sendOperationResult(
			player,
			payload.requestSequence(),
			OperationCode.SUCCESS,
			"已返回上一级页面。",
			authority.root().stateRevision(),
			authority.definitions().generation(),
			false
		);
	}

	private static void handleTerminalRefresh(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalIntentC2SPayload payload,
		final TerminalAuthority authority
	) {
		PlayerTerminalSessions.Session previousVisibleSession =
			PLAYER_TERMINAL_SESSIONS.find(player.getUUID()).orElse(null);
		if (previousVisibleSession == null) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"玩家终端会话已经关闭，请重新打开。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerTerminalSessions.MutationResult result = PLAYER_TERMINAL_SESSIONS.reissue(
			player.getUUID(),
			authority.definitions().generation(),
			authority.root().stateRevision(),
			authority.takeoverAllowed()
		);
		if (!result.successful()) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.SNAPSHOT_INVALID,
				result.message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		TerminalPageBundleResult delivery = sendTerminalPageBundle(
			server,
			player,
			authority,
			result.session().orElseThrow()
		);
		if (!delivery.successful()) {
			failTerminalPageMutationDelivery(
				server,
				player,
				authority,
				previousVisibleSession,
				Optional.of(payload.requestSequence()),
				delivery
			);
			return;
		}
		sendOperationResult(
			player,
			payload.requestSequence(),
			OperationCode.SUCCESS,
			"玩家终端内容已同步。",
			authority.root().stateRevision(),
			authority.definitions().generation(),
			false
		);
	}

	private static void handleTerminalRegisteredAction(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalIntentC2SPayload payload,
		final TerminalAuthority authority
	) {
		PlayerTerminalSessions.Session session = PLAYER_TERMINAL_SESSIONS.find(
			player.getUUID()
		).orElse(null);
		if (session == null) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"玩家终端会话已经关闭，请重新打开。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		if (terminalHostControlRequest(payload)) {
			handleTerminalHostControl(
				server,
				player,
				payload,
				authority,
				session
			);
			return;
		}
		SessionAuthorization authorization = terminalActionAuthorization(
			authority,
			player,
			session,
			payload.requestSequence()
		);
		ActionRequest request = terminalActionRequest(payload, authorization);
		Preparation prepared = PlayerActionAuthority.prepare(
			authority.root(),
			authority.definitions(),
			authorization,
			request,
			terminalActionPredicates(server, player, authority),
			playerActionWorldTick(server)
		);
		if (prepared.confirmation().isPresent()) {
			issuePlayerActionConfirmation(
				server,
				player,
				authority,
				session,
				authorization,
				request,
				prepared
			);
			return;
		}
		if (!prepared.accepted()) {
			sendPlayerActionRejection(
				player,
				payload.requestSequence(),
				prepared,
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		executePreparedPlayerAction(
			server,
			player,
			payload.requestSequence(),
			prepared
		);
	}

	private static boolean terminalHostControlRequest(
		final TerminalIntentC2SPayload payload
	) {
		return payload.nodeId()
			.filter(TERMINAL_HOST_CONTROL_NODE_ID::equals)
			.isPresent()
			&& payload.actionId()
				.filter(operation ->
					operation.equals(CLAIM_HOST_OPERATION)
						|| operation.equals(TAKEOVER_HOST_OPERATION)
				)
				.isPresent();
	}

	private static Optional<OperationKey> terminalHostOperation(
		final TerminalAuthority authority
	) {
		if (!authority.takeoverAllowed()) {
			return Optional.empty();
		}
		return authority.state().host().isEmpty()
			? Optional.of(new OperationKey("claim_host", CLAIM_HOST_OPERATION))
			: Optional.of(new OperationKey("takeover_host", TAKEOVER_HOST_OPERATION));
	}

	private static void handleTerminalHostControl(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalIntentC2SPayload payload,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session
	) {
		OperationKey operation = terminalHostOperation(authority).orElse(null);
		if (
			operation == null
				|| payload.actionId().filter(operation.id()::equals).isEmpty()
		) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"主持人状态或你的管理员权限已经变化，请查看更新后的玩家终端。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PreparedOperation prepared = prepareOperation(
			server,
			player,
			operation,
			List.of()
		);
		if (!prepared.successful()) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				prepared.code(),
				prepared.message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		if (!ServerPlayNetworking.canSend(player, ConfirmationS2CPayload.TYPE)) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"客户端不支持主持人操作所需的二次确认页面。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}

		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		Binding initialBinding = prepared.binding().orElseThrow();
		boolean replacing = CONFIRMATIONS.hasPending(player.getUUID());
		if (
			!persistConfirmationAudit(
				wrapper,
				AuditEventType.CONFIRMATION_ISSUED,
				Optional.of(player.getUUID()),
				Optional.of(initialBinding),
				replacing
					? "issued a replacement terminal host confirmation"
					: "issued a terminal host confirmation",
				System.currentTimeMillis()
			)
		) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.INTERNAL_ERROR,
				"无法持久化确认审计，主持人操作未开始。",
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}

		PreparedOperation audited = prepareOperation(
			server,
			player,
			operation,
			List.of()
		);
		if (!audited.successful()) {
			clearPendingTerminalHostConfirmationMetadata(player.getUUID());
			CONFIRMATIONS.cancelOperator(player.getUUID());
			sendOperationResult(
				player,
				payload.requestSequence(),
				audited.code(),
				audited.message(),
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}

		Binding terminalBinding = terminalHostControlBinding(
			audited.binding().orElseThrow(),
			session,
			payload.requestSequence()
		);
		clearPendingPlayerActionConfirmations(player.getUUID());
		clearPendingTerminalHostConfirmationMetadata(player.getUUID());
		IssuedConfirmation issued = CONFIRMATIONS.issue(
			player.getUUID(),
			terminalBinding,
			audited.prompt().orElseThrow()
		);
		TERMINAL_HOST_CONFIRMATIONS.put(
			issued.tokenId(),
			new PendingTerminalHostConfirmation(
				player.getUUID(),
				session.sessionId(),
				session.current().instanceId(),
				session.definitionGeneration(),
				session.routeRevision(),
				payload.requestSequence(),
				operation
			)
		);
		ServerPlayNetworking.send(
			player,
			new ConfirmationS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				payload.requestSequence(),
				issued.tokenId(),
				operation.type(),
				operation.id(),
				issued.prompt().title().json(),
				issued.prompt().consequences().stream().map(RichText::json).toList(),
				sortedTargets(audited.targets()),
				audited.contextSummary(),
				issued.validForMilliseconds(),
				terminalBinding.stateRevision(),
				terminalBinding.definitionGeneration()
			)
		);
	}

	private static void handleTerminalHistoryDetail(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalIntentC2SPayload payload,
		final TerminalAuthority authority
	) {
		PlayerTerminalSessions.Session session = PLAYER_TERMINAL_SESSIONS.find(
			player.getUUID()
		).orElse(null);
		String nodeId = payload.nodeId().orElse("");
		String recordKey = payload.historyRecordKey().orElse("");
		PageDefinition sourcePage = session == null
			? null
			: authority.definitions().pages().get(session.current().pageId());
		if (
			session == null
				|| sourcePage == null
				|| !sourcePage.game().equals(authority.game().id())
				|| !historyDetailButtonMatches(sourcePage.root(), nodeId, false)
		) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.NODE_MISMATCH,
				"历史详情入口已经变化，本次请求未执行。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerTerminalProjector.HistoryDetailTarget target =
			PlayerTerminalProjector.resolveHistoryDetail(
				server,
				authority.definitions(),
				authority.root(),
				player,
				new PlayerTerminalProjector.SessionMetadata(
					session.sessionId(),
					session.current().instanceId(),
					session.current().pageId(),
					session.routeId(),
					session.definitionGeneration(),
					session.stateRevision(),
					true,
					session.current()
						.historyRecord()
						.map(PlayerTerminalSessions.HistoryRecordRef::recordKey)
				),
				recordKey
			).orElse(null);
		if (target == null) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"这条历史记录已不可见，详情未打开。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerTerminalSessions.MutationResult pushed =
			PLAYER_TERMINAL_SESSIONS.pushHistoryDetail(
				player.getUUID(),
				target.pageId(),
				target.recordKey(),
				authority.root().stateRevision()
			);
		if (!pushed.successful()) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.SNAPSHOT_INVALID,
				"历史详情页面当前不可用，请返回后重试。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		TerminalPageBundleResult delivery = sendTerminalPageBundle(
			server,
			player,
			authority,
			pushed.session().orElseThrow()
		);
		if (!delivery.successful()) {
			failTerminalPageMutationDelivery(
				server,
				player,
				authority,
				session,
				Optional.of(payload.requestSequence()),
				delivery
			);
			return;
		}
		sendOperationResult(
			player,
			payload.requestSequence(),
			OperationCode.SUCCESS,
			"已打开该事件的公开详情。",
			authority.root().stateRevision(),
			authority.definitions().generation(),
			false
		);
	}

	private static void handleTerminalHistoryReplay(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalIntentC2SPayload payload,
		final TerminalAuthority authority
	) {
		PlayerTerminalSessions.Session session = PLAYER_TERMINAL_SESSIONS.find(
			player.getUUID()
		).orElse(null);
		String nodeId = payload.nodeId().orElse("");
		String recordKey = payload.historyRecordKey().orElse("");
		PageDefinition sourcePage = session == null
			? null
			: authority.definitions().pages().get(session.current().pageId());
		if (
			session == null
				|| sourcePage == null
				|| !sourcePage.game().equals(authority.game().id())
				|| !historyReplayButtonMatches(sourcePage.root(), nodeId)
		) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.NODE_MISMATCH,
				"历史重播入口已经变化，本次请求未执行。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		MessageHistoryRecord record = PlayerTerminalProjector.resolveMessageHistoryReplay(
			server,
			authority.definitions(),
			authority.root(),
			player,
			new PlayerTerminalProjector.SessionMetadata(
				session.sessionId(),
				session.current().instanceId(),
				session.current().pageId(),
				session.routeId(),
				session.definitionGeneration(),
				session.stateRevision(),
				true,
				session.current()
					.historyRecord()
					.map(PlayerTerminalSessions.HistoryRecordRef::recordKey)
			),
			recordKey
		).orElse(null);
		if (record == null) {
			sendOperationResult(
				player,
				payload.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"这条历史记录当前不可重播。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		var replay = MessageServerRuntime.replayHistory(player, record);
		sendOperationResult(
			player,
			payload.requestSequence(),
			replay.successful()
				? OperationCode.SUCCESS
				: OperationCode.ACTION_UNAVAILABLE,
			replay.successful()
				? "该记录已进入安全重播调度。"
				: "这条历史记录当前无法重播，请稍后重试。",
			authority.root().stateRevision(),
			authority.definitions().generation(),
			false
		);
	}

	static boolean historyDetailButtonMatches(
		final NodeDefinition node,
		final String nodeId,
		final boolean historyItemScope
	) {
		return historyActionButtonMatches(
			node,
			nodeId,
			historyItemScope,
			ActionType.HISTORY_DETAIL,
			true
		);
	}

	static boolean historyReplayButtonMatches(
		final NodeDefinition node,
		final String nodeId
	) {
		return historyActionButtonMatches(
			node,
			nodeId,
			false,
			ActionType.HISTORY_REPLAY,
			false
		);
	}

	private static boolean historyActionButtonMatches(
		final NodeDefinition node,
		final String nodeId,
		final boolean historyItemScope,
		final ActionType actionType,
		final boolean requireHistoryItemScope
	) {
		if (
			node.id().filter(nodeId::equals).isPresent()
				&& node.content() instanceof ButtonContent button
		) {
			return (!requireHistoryItemScope || historyItemScope)
				&& button.action().type() == actionType;
		}
		if (node.content() instanceof ChildrenContent children) {
			return children.children()
				.stream()
				.anyMatch(child ->
					historyActionButtonMatches(
						child,
						nodeId,
						historyItemScope,
						actionType,
						requireHistoryItemScope
					)
				);
		}
		if (node.content() instanceof SingleChildContent single) {
			return historyActionButtonMatches(
				single.child(),
				nodeId,
				historyItemScope,
				actionType,
				requireHistoryItemScope
			);
		}
		if (node.content() instanceof RepeatContent repeat) {
			boolean templateScope =
				repeat.items() instanceof BindingValue binding
					&& binding.path().equals("history.items");
			return historyActionButtonMatches(
				repeat.template(),
				nodeId,
				templateScope,
				actionType,
				requireHistoryItemScope
			);
		}
		return false;
	}

	private static SessionAuthorization terminalActionAuthorization(
		final TerminalAuthority authority,
		final ServerPlayer player,
		final PlayerTerminalSessions.Session session,
		final long acceptedRequestSequence
	) {
		return new SessionAuthorization(
			authority.game().id(),
			player.getUUID(),
			session.sessionId(),
			session.current().instanceId(),
			session.current().pageId(),
			session.definitionGeneration(),
			session.stateRevision(),
			session.routeRevision(),
			acceptedRequestSequence,
			true
		);
	}

	private static ActionRequest terminalActionRequest(
		final TerminalIntentC2SPayload payload,
		final SessionAuthorization authorization
	) {
		return new ActionRequest(
			payload.terminalSessionId(),
			payload.pageInstanceId(),
			payload.definitionGeneration(),
			payload.stateRevision(),
			payload.routeRevision(),
			payload.requestSequence(),
			PlayerActionAuthority.deriveRequestId(
				authorization.sessionId(),
				authorization.nextRequestSequence()
			),
			payload.nodeId().orElseThrow(),
			payload.actionId().orElseThrow()
		);
	}

	private static PlayerActionAuthority.PredicateEvaluator terminalActionPredicates(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority
	) {
		FrozenPredicateEvaluator evaluator = new FrozenPredicateEvaluator(
			server,
			authority.definitions().predicateDocuments()
		);
		PredicateContext context = panelPredicateContext(authority.state(), player);
		return (predicate, ignored) -> evaluator.evaluate(predicate, context);
	}

	private static Binding terminalHostControlBinding(
		final Binding base,
		final PlayerTerminalSessions.Session session,
		final long acceptedRequestSequence
	) {
		String material = String.join(
			"\n",
			base.relatedStateSha256(),
			session.sessionId().toString(),
			session.current().instanceId().toString(),
			session.current().pageId().toString(),
			Long.toString(session.definitionGeneration()),
			Long.toString(session.routeRevision()),
			Long.toString(acceptedRequestSequence),
			base.operation().type(),
			base.operation().id().toString()
		);
		return new Binding(
			base.operation(),
			base.targetIds(),
			base.gameId(),
			base.phaseId(),
			base.definitionGeneration(),
			base.stateRevision(),
			sha256(material.getBytes(StandardCharsets.UTF_8))
		);
	}

	static boolean terminalHostControlSequenceIsCurrent(
		final PlayerTerminalSessions.Session session,
		final UUID expectedSessionId,
		final UUID expectedPageInstanceId,
		final long expectedDefinitionGeneration,
		final long expectedRouteRevision,
		final long acceptedRequestSequence
	) {
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(expectedSessionId, "expectedSessionId");
		Objects.requireNonNull(expectedPageInstanceId, "expectedPageInstanceId");
		return acceptedRequestSequence < Long.MAX_VALUE
			&& session.sessionId().equals(expectedSessionId)
			&& session.current().instanceId().equals(expectedPageInstanceId)
			&& session.definitionGeneration() == expectedDefinitionGeneration
			&& session.routeRevision() == expectedRouteRevision
			&& session.nextRequestSequence() == acceptedRequestSequence + 1L;
	}

	private static void commitTerminalHostConfirmation(
		final MinecraftServer server,
		final ServerPlayer player,
		final CommitConfirmationC2SPayload payload,
		final Binding pendingBinding
	) {
		PendingTerminalHostConfirmation pending =
			TERMINAL_HOST_CONFIRMATIONS.remove(payload.tokenId());
		if (pending == null || !pending.playerId().equals(player.getUUID())) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				"玩家终端主持人确认上下文已经失效，请返回后重新操作。",
				PixelTzzWorldState.get(server).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}

		TerminalAuthorityResult resolved = resolveTerminalAuthority(server, player);
		PlayerTerminalSessions.Session session = PLAYER_TERMINAL_SESSIONS.find(
			player.getUUID()
		).orElse(null);
		if (
			!resolved.successful()
				|| session == null
				|| !terminalHostControlSequenceIsCurrent(
					session,
					pending.sessionId(),
					pending.pageInstanceId(),
					pending.definitionGeneration(),
					pending.routeRevision(),
					pending.acceptedRequestSequence()
				)
		) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				resolved.successful() && session != null
					? "二次确认期间玩家终端已经处理了其他操作，请重新发起并审阅。"
					: resolved.successful()
						? "玩家终端会话已经关闭，请重新打开。"
						: resolved.message(),
				PixelTzzWorldState.get(server).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}

		TerminalAuthority authority = resolved.authority().orElseThrow();
		OperationKey operation = terminalHostOperation(authority).orElse(null);
		if (operation == null || !operation.equals(pending.operation())) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				"主持人状态或你的管理员权限已经变化，请重新发起并审阅。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}

		PreparedOperation current = prepareOperation(
			server,
			player,
			operation,
			List.of()
		);
		if (!current.successful()) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				current.code(),
				current.message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		Binding currentBinding = terminalHostControlBinding(
			current.binding().orElseThrow(),
			session,
			pending.acceptedRequestSequence()
		);
		ConsumeResult consumed = CONFIRMATIONS.consume(
			player.getUUID(),
			payload.tokenId(),
			currentBinding
		);
		if (consumed instanceof Rejected rejected) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				confirmationCode(rejected),
				rejected.reason().message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		if (!(consumed instanceof Accepted) || !pendingBinding.equals(currentBinding)) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				"玩家终端主持人确认内容已经变化，请重新审阅。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}

		Binding executable = current.binding().orElseThrow();
		StatefulRequest authoritativeRequest = new StatefulRequest(
			NetworkProtocol.CURRENT_VERSION,
			payload.request().requestSequence(),
			executable.gameId(),
			executable.definitionGeneration(),
			executable.stateRevision(),
			Optional.empty(),
			Optional.empty()
		);
		executeConfirmedOperation(server, player, authoritativeRequest, current);
	}

	private static void issuePlayerActionConfirmation(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session,
		final SessionAuthorization authorization,
		final ActionRequest request,
		final Preparation preparation
	) {
		if (!ServerPlayNetworking.canSend(player, ConfirmationS2CPayload.TYPE)) {
			sendOperationResult(
				player,
				request.requestSequence(),
				OperationCode.ACTION_UNAVAILABLE,
				"客户端不支持该操作所需的二次确认页面。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		PlayerActionAuthority.ConfirmationChallenge challenge =
			preparation.confirmation().orElseThrow();
		Binding binding = playerActionConfirmationBinding(
			authority,
			session,
			challenge
		);
		clearPendingPlayerActionConfirmations(player.getUUID());
		clearPendingTerminalHostConfirmationMetadata(player.getUUID());
		IssuedConfirmation issued = CONFIRMATIONS.issue(
			player.getUUID(),
			binding,
			new Prompt(
				challenge.confirmation().title(),
				challenge.confirmation().consequences()
			)
		);
		PLAYER_ACTION_CONFIRMATIONS.put(
			issued.tokenId(),
			new PendingPlayerActionConfirmation(
				player.getUUID(),
				authorization,
				request,
				challenge
			)
		);
		Target target = confirmationTarget(
			server,
			authority.state(),
			authority.definitions(),
			player.getUUID(),
			player.getPlainTextName(),
			Set.of()
		);
		ServerPlayNetworking.send(
			player,
			new ConfirmationS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				request.requestSequence(),
				issued.tokenId(),
				PLAYER_ACTION_OPERATION_TYPE,
				challenge.actionId(),
				issued.prompt().title().json(),
				issued.prompt().consequences().stream().map(RichText::json).toList(),
				List.of(target),
				truncate(
					"『"
						+ authority.game().name().plainText()
						+ "』 · 玩家终端 · "
						+ player.getPlainTextName(),
					ConfirmationS2CPayload.MAX_CONTEXT_LENGTH
				),
				issued.validForMilliseconds(),
				binding.stateRevision(),
				binding.definitionGeneration()
			)
		);
	}

	private static Binding playerActionConfirmationBinding(
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session,
		final PlayerActionAuthority.ConfirmationChallenge challenge
	) {
		Identifier phaseId = authority.state().activePhaseId().orElseThrow();
		UUID gameInstanceId = authority.root()
			.core()
			.activeGameInstanceId()
			.orElseThrow();
		String material = String.join(
			"\n",
			authority.game().id().toString(),
			gameInstanceId.toString(),
			phaseId.toString(),
			session.sessionId().toString(),
			session.current().instanceId().toString(),
			session.current().pageId().toString(),
			Long.toString(session.routeRevision()),
			challenge.nodeId(),
			challenge.actionId().toString(),
			challenge.requestId().toString(),
			Long.toString(challenge.requestSequence()),
			Long.toString(challenge.definitionGeneration())
		);
		return new Binding(
			new OperationKey(PLAYER_ACTION_OPERATION_TYPE, challenge.actionId()),
			List.of(authority.player().playerId()),
			Optional.of(authority.game().id()),
			Optional.of(phaseId),
			authority.definitions().generation(),
			authority.root().stateRevision(),
			sha256(material.getBytes(StandardCharsets.UTF_8))
		);
	}

	static boolean playerActionConfirmationSequenceIsCurrent(
		final PlayerTerminalSessions.Session session,
		final PlayerActionAuthority.ConfirmationChallenge challenge
	) {
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(challenge, "challenge");
		return challenge.requestSequence() < Long.MAX_VALUE
			&& session.nextRequestSequence() == challenge.requestSequence() + 1L;
	}

	private static void commitPlayerActionConfirmation(
		final MinecraftServer server,
		final ServerPlayer player,
		final CommitConfirmationC2SPayload payload,
		final Binding pendingBinding
	) {
		PendingPlayerActionConfirmation pending =
			PLAYER_ACTION_CONFIRMATIONS.remove(payload.tokenId());
		if (pending == null || !pending.playerId().equals(player.getUUID())) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				"玩家终端确认上下文已经失效，请返回后重新操作。",
				PixelTzzWorldState.get(server).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		TerminalAuthorityResult resolved = resolveTerminalAuthority(server, player);
		PlayerTerminalSessions.Session session = PLAYER_TERMINAL_SESSIONS.find(
			player.getUUID()
		).orElse(null);
		if (!resolved.successful() || session == null) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				resolved.successful()
					? "玩家终端会话已经关闭，请重新打开。"
					: resolved.message(),
				PixelTzzWorldState.get(server).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		TerminalAuthority authority = resolved.authority().orElseThrow();
		PlayerActionAuthority.ConfirmationChallenge challenge = pending.challenge();
		if (!playerActionConfirmationSequenceIsCurrent(session, challenge)) {
			CONFIRMATIONS.cancel(player.getUUID(), payload.tokenId());
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				"二次确认期间玩家终端已经处理了其他操作，请重新发起并审阅。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		Binding currentBinding = playerActionConfirmationBinding(
			authority,
			session,
			challenge
		);
		ConsumeResult consumed = CONFIRMATIONS.consume(
			player.getUUID(),
			payload.tokenId(),
			currentBinding
		);
		if (consumed instanceof Rejected rejected) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				confirmationCode(rejected),
				rejected.reason().message(),
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		if (
			!(consumed instanceof Accepted)
				|| !pendingBinding.equals(currentBinding)
		) {
			sendOperationResult(
				player,
				payload.request().requestSequence(),
				OperationCode.CONFIRMATION_CONTEXT_CHANGED,
				"玩家终端确认内容已经变化，请重新审阅。",
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}

		SessionAuthorization authorization = terminalActionAuthorization(
			authority,
			player,
			session,
			pending.request().requestSequence()
		);
		ConfirmedRequest confirmed = new ConfirmedRequest(
			challenge.sessionId(),
			challenge.pageInstanceId(),
			challenge.nodeId(),
			challenge.actionId(),
			challenge.requestId(),
			challenge.requestSequence(),
			challenge.definitionGeneration(),
			challenge.routeRevision()
		);
		Preparation preparation = PlayerActionAuthority.prepare(
			authority.root(),
			authority.definitions(),
			authorization,
			pending.request(),
			terminalActionPredicates(server, player, authority),
			playerActionWorldTick(server),
			Optional.of(confirmed)
		);
		if (!preparation.accepted()) {
			sendPlayerActionRejection(
				player,
				payload.request().requestSequence(),
				preparation,
				authority.root().stateRevision(),
				authority.definitions().generation(),
				false
			);
			return;
		}
		executePreparedPlayerAction(
			server,
			player,
			payload.request().requestSequence(),
			preparation
		);
	}

	private static void executePreparedPlayerAction(
		final MinecraftServer server,
		final ServerPlayer player,
		final long responseSequence,
		final Preparation preparation
	) {
		PreparedAction prepared = preparation.prepared().orElseThrow();
		boolean opensPage = prepared.target() instanceof PushPage;
		PlayerTerminalSessions.Session previousVisibleSession = opensPage
			? PLAYER_TERMINAL_SESSIONS.find(player.getUUID()).orElse(null)
			: null;
		if (opensPage && previousVisibleSession == null) {
			sendOperationResult(
				player,
				responseSequence,
				OperationCode.ACTION_UNAVAILABLE,
				"玩家终端会话已经关闭，本次页面操作未执行。",
				PixelTzzWorldState.get(server).stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		PixelTzzWorldState.CommitV4Result reserved = wrapper.commitV4(
			prepared.authorizedStateRevision(),
			ignored -> preparation.nextState().orElseThrow()
		);
		if (!reserved.committed()) {
			sendOperationResult(
				player,
				responseSequence,
				OperationCode.STATE_REVISION_STALE,
				"游戏状态在执行前发生变化，本次操作未开始。",
				wrapper.stateRevision(),
				DefinitionRegistry.INSTANCE.view().active().generation(),
				false
			);
			return;
		}

		ExecutionResult execution;
		boolean pagePushed = false;
		if (prepared.target() instanceof PushPage push) {
			PlayerTerminalSessions.MutationResult pushed =
				PLAYER_TERMINAL_SESSIONS.pushAuthorized(
				player.getUUID(),
				push.pageId(),
				prepared.pageInstanceId(),
				prepared.nodeId(),
				prepared.actionId(),
				reserved.state().orElseThrow().stateRevision()
			);
			pagePushed = pushed.successful();
			execution = pagePushed
				? ExecutionResult.pageOpened(push.pageId())
				: ExecutionResult.failed("page_push_failed", pushed.message());
		} else if (prepared.target() instanceof RunFunction run) {
			RegisteredPlayerFunctionRunner.Result invoked =
				RegisteredPlayerFunctionRunner.invoke(
					server,
					run.functionId(),
					prepared.functionContext(
						reserved.state().orElseThrow().stateRevision()
					),
					player
				);
			execution = invoked.authorityResult();
		} else {
			execution = ExecutionResult.failed(
				"target_unavailable",
				"服务器无法识别已授权的玩家操作目标"
			);
		}

		WorldStateV4 latest = wrapper.currentV4().orElse(null);
		Finalization finalized = latest == null
			? null
			: PlayerActionAuthority.finalizeInvocation(latest, prepared, execution);
		if (
			finalized == null
				|| (!finalized.finalized() && !finalized.alreadyFinalized())
		) {
			String message =
				"玩家操作已进入结果未知状态；为防止重复执行，服务器不会自动重试。";
			if (pagePushed) {
				failTerminalPageMutation(
					server,
					player,
					previousVisibleSession,
					Optional.of(responseSequence),
					OperationCode.INTERNAL_ERROR,
					wrapper.stateRevision(),
					DefinitionRegistry.INSTANCE.view().active().generation(),
					"registered_page_finalize_failed",
					message
				);
			} else {
				sendOperationResult(
					player,
					responseSequence,
					OperationCode.INTERNAL_ERROR,
					message,
					wrapper.stateRevision(),
					DefinitionRegistry.INSTANCE.view().active().generation(),
					false
				);
			}
			return;
		}
		WorldStateV4 finalState = latest;
		if (finalized.finalized()) {
			WorldStateV4 candidate = finalized.nextState().orElseThrow();
			if (finalized.audit().isPresent()) {
				candidate = withPlayerActionAudit(
					candidate,
					finalized.audit().orElseThrow()
				);
			}
			final WorldStateV4 committedCandidate = candidate;
			PixelTzzWorldState.CommitV4Result committed = wrapper.commitV4(
				latest.stateRevision(),
				ignored -> committedCandidate
			);
			if (!committed.committed()) {
				String message =
					"玩家操作结果无法持久化；为防止重复执行，服务器不会自动重试。";
				if (pagePushed) {
					failTerminalPageMutation(
						server,
						player,
						previousVisibleSession,
						Optional.of(responseSequence),
						OperationCode.INTERNAL_ERROR,
						wrapper.stateRevision(),
						DefinitionRegistry.INSTANCE.view().active().generation(),
						"registered_page_commit_failed",
						message
					);
				} else {
					sendOperationResult(
						player,
						responseSequence,
						OperationCode.INTERNAL_ERROR,
						message,
						wrapper.stateRevision(),
						DefinitionRegistry.INSTANCE.view().active().generation(),
						false
					);
				}
				return;
			}
			finalState = committed.state().orElseThrow();
		}

		TerminalAuthorityResult resolved = resolveTerminalAuthority(server, player);
		if (pagePushed) {
			if (!resolved.successful()) {
				failTerminalPageMutation(
					server,
					player,
					previousVisibleSession,
					Optional.of(responseSequence),
					resolved.code(),
					finalState.stateRevision(),
					DefinitionRegistry.INSTANCE.view().active().generation(),
					"registered_page_authority_unavailable",
					resolved.message()
				);
				return;
			}
			TerminalAuthority authority = resolved.authority().orElseThrow();
			PlayerTerminalSessions.MutationResult touched =
				PLAYER_TERMINAL_SESSIONS.touchProjection(
					player.getUUID(),
					finalState.stateRevision(),
					authority.takeoverAllowed()
				);
			if (!touched.successful()) {
				failTerminalPageMutation(
					server,
					player,
					previousVisibleSession,
					Optional.of(responseSequence),
					OperationCode.SNAPSHOT_INVALID,
					finalState.stateRevision(),
					authority.definitions().generation(),
					"registered_page_projection_reissue_failed",
					"目标页面已经入栈，但服务端无法建立新的页面实例。"
				);
				return;
			}
			TerminalPageBundleResult delivery = sendTerminalPageBundle(
				server,
				player,
				authority,
				touched.session().orElseThrow()
			);
			if (!delivery.successful()) {
				failTerminalPageMutationDelivery(
					server,
					player,
					authority,
					previousVisibleSession,
					Optional.of(responseSequence),
					delivery
				);
				return;
			}
		} else {
			refreshOpenPlayerTerminals(server, false);
		}

		boolean successful = execution.success();
		sendOperationResult(
			player,
			responseSequence,
			successful
				? OperationCode.SUCCESS
				: execution.code().equals("function_failed")
					? OperationCode.CALLBACK_FAILED
					: OperationCode.ACTION_UNAVAILABLE,
			playerActionFeedback(preparation, execution, successful),
			finalState.stateRevision(),
			DefinitionRegistry.INSTANCE.view().active().generation(),
			false
		);
	}

	private static WorldStateV4 withPlayerActionAudit(
		final WorldStateV4 state,
		final PlayerActionAuthority.AuditEntry audit
	) {
		WorldStateV2 core = state.core().core();
		Optional<UUID> flowInstanceId = core.activeForcedFlow()
			.map(ForcedFlowInstance::identity)
			.map(WorldStateV2.ForcedFlowIdentity::instanceId);
		WorldStateV2 revised = core.withAudit(
			core.audit().append(
				new AuditEvent(
					core.audit().totalEvents(),
					AuditEventType.PLAYER_ACTION,
					System.currentTimeMillis(),
					Optional.of(audit.playerId()),
					Optional.of(audit.playerId()),
					flowInstanceId,
					truncate(
						audit.actionId()
							+ " request="
							+ audit.requestId()
							+ "; "
							+ audit.summary(),
						1_024
					)
				)
			)
		);
		return state.withCore(state.core().withCore(revised));
	}

	private static String playerActionFeedback(
		final Preparation preparation,
		final ExecutionResult execution,
		final boolean successful
	) {
		Optional<RichText> registered = preparation.feedback().flatMap(feedback ->
			successful ? feedback.success() : feedback.failed()
		);
		return registered
			.map(RichText::plainText)
			.filter(value -> !value.isBlank())
			.orElseGet(() ->
				execution.summary().isBlank()
					? successful ? "操作已完成。" : "操作未能完成。"
					: execution.summary()
			);
	}

	private static void sendPlayerActionRejection(
		final ServerPlayer player,
		final long responseSequence,
		final Preparation preparation,
		final long stateRevision,
		final long definitionGeneration,
		final boolean refreshRequired
	) {
		String message = preparation.feedback()
			.flatMap(feedback -> feedback.denied())
			.map(RichText::plainText)
			.filter(value -> !value.isBlank())
			.orElse(preparation.message());
		sendOperationResult(
			player,
			responseSequence,
			playerActionOperationCode(preparation.code()),
			message,
			stateRevision,
			definitionGeneration,
			refreshRequired
		);
	}

	private static OperationCode playerActionOperationCode(
		final PlayerActionAuthority.Code code
	) {
		return switch (code) {
			case DEFINITION_GENERATION_STALE -> OperationCode.DEFINITION_GENERATION_STALE;
			case STATE_REVISION_STALE -> OperationCode.STATE_REVISION_STALE;
			case ROUTE_REVISION_STALE, SESSION_STALE -> OperationCode.REVISION_MISMATCH;
			case REQUEST_REPLAYED, OUTCOME_UNKNOWN -> OperationCode.REQUEST_REPLAYED;
			case SCHEMA_BLOCKED -> OperationCode.SCHEMA_BLOCKED;
			case PLAYER_OFFLINE -> OperationCode.TARGET_OFFLINE;
			case HOST_FORBIDDEN, AUDIENCE_MISMATCH, PREDICATE_DENIED ->
				OperationCode.PERMISSION_DENIED;
			case PHASE_MISMATCH, GAME_MISMATCH -> OperationCode.PHASE_MISMATCH;
			case TASK_MISMATCH, TASK_STATE_MISMATCH -> OperationCode.TASK_STATE_MISMATCH;
			case NODE_MISMATCH -> OperationCode.NODE_MISMATCH;
			case CONFIRMATION_REQUIRED -> OperationCode.CONFIRMATION_MISSING;
			case CONFIRMATION_INVALID -> OperationCode.CONFIRMATION_CONTEXT_CHANGED;
			case CLOCK_INVALID, LEDGER_FULL, INVOCATION_MISSING, INVOCATION_FINAL,
				INTERNAL_ERROR, PREDICATE_FAILED -> OperationCode.INTERNAL_ERROR;
			case PREPARED, FINALIZED, ALREADY_FINALIZED -> OperationCode.SUCCESS;
			case PLAYER_NOT_REGISTERED, PAGE_UNAVAILABLE, ACTION_UNAVAILABLE,
				USAGE_EXHAUSTED, COOLDOWN_ACTIVE -> OperationCode.ACTION_UNAVAILABLE;
		};
	}

	private static void cancelPendingPlayerActionConfirmations(final UUID playerId) {
		List<UUID> tokenIds = PLAYER_ACTION_CONFIRMATIONS.entrySet()
			.stream()
			.filter(entry -> entry.getValue().playerId().equals(playerId))
			.map(Map.Entry::getKey)
			.toList();
		for (UUID tokenId : tokenIds) {
			PLAYER_ACTION_CONFIRMATIONS.remove(tokenId);
			CONFIRMATIONS.cancel(playerId, tokenId);
		}
	}

	private static void cancelPendingTerminalHostConfirmations(final UUID playerId) {
		List<UUID> tokenIds = TERMINAL_HOST_CONFIRMATIONS.entrySet()
			.stream()
			.filter(entry -> entry.getValue().playerId().equals(playerId))
			.map(Map.Entry::getKey)
			.toList();
		for (UUID tokenId : tokenIds) {
			TERMINAL_HOST_CONFIRMATIONS.remove(tokenId);
			CONFIRMATIONS.cancel(playerId, tokenId);
		}
	}

	private static void clearPendingPlayerActionConfirmations(final UUID playerId) {
		PLAYER_ACTION_CONFIRMATIONS.entrySet().removeIf(
			entry -> entry.getValue().playerId().equals(playerId)
		);
	}

	private static void clearPendingTerminalHostConfirmationMetadata(
		final UUID playerId
	) {
		TERMINAL_HOST_CONFIRMATIONS.entrySet().removeIf(
			entry -> entry.getValue().playerId().equals(playerId)
		);
	}

	private static TerminalAuthorityResult resolveTerminalAuthority(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV4 root = wrapper.currentV4().orElse(null);
		if (!wrapper.isSchemaCompatible() || root == null) {
			return TerminalAuthorityResult.rejected(
				OperationCode.SCHEMA_BLOCKED,
				"世界数据正在迁移或版本不兼容，玩家终端暂不可用。"
			);
		}
		if (hasCurrentForcedPage(root.core(), player.getUUID())) {
			return TerminalAuthorityResult.rejected(
				OperationCode.ACTIVE_FLOW_EXISTS,
				"请先完成当前强制流程。"
			);
		}
		WorldStateV2 state = root.core().core();
		if (
			state.host()
				.map(WorldStateV2.HostRecord::playerId)
				.filter(player.getUUID()::equals)
				.isPresent()
		) {
			return TerminalAuthorityResult.rejected(
				OperationCode.NOT_HOST,
				"主持人应打开主持人控制台。"
			);
		}
		View live = DefinitionRegistry.INSTANCE.view();
		boolean frozenTimeline = root.core().timeline().isPresent();
		if (!frozenTimeline && (!live.healthy() || !live.active().usable())) {
			return TerminalAuthorityResult.rejected(
				OperationCode.DEFINITION_UNAVAILABLE,
				"玩家终端定义当前不可用，请联系主持人查看数据包诊断。"
			);
		}
		Identifier gameId = state.activeGameId().orElse(null);
		Identifier phaseId = state.activePhaseId().orElse(null);
		PlayerRecord record = state.players().get(player.getUUID());
		if (gameId == null || phaseId == null || record == null) {
			return TerminalAuthorityResult.rejected(
				OperationCode.DEFINITION_UNAVAILABLE,
				"当前世界尚未建立完整的玩家终端上下文。"
			);
		}
		DefinitionSnapshot definitions;
		try {
			definitions = terminalDefinitionSnapshot(root.core(), live.active());
		} catch (IllegalStateException error) {
			return TerminalAuthorityResult.rejected(
				OperationCode.SNAPSHOT_INVALID,
				"本局冻结的玩家终端定义无法恢复，请查看服务端日志。"
			);
		}
		GameDefinition game = definitions.games().get(gameId);
		if (game == null) {
			return TerminalAuthorityResult.rejected(
				OperationCode.DEFINITION_UNAVAILABLE,
				"当前游戏没有可用的玩家终端定义。"
			);
		}
		Optional<Identifier> taskId = root.core()
			.timeline()
			.flatMap(WorldStateV3.TimelineInstance::currentTask)
			.map(WorldStateV3.TaskInstance::taskId);
		Optional<PlayerTaskState> taskState = terminalTaskState(root.core());
		FrozenPredicateEvaluator predicates = new FrozenPredicateEvaluator(
			server,
			definitions.predicateDocuments()
		);
		PredicateContext predicateContext = panelPredicateContext(state, player);
		PlayerTerminalRouter.Resolution route = PlayerTerminalRouter.resolve(
			definitions,
			game,
			new PlayerTerminalRouter.Context(
				gameId,
				phaseId,
				record,
				state.host().map(WorldStateV2.HostRecord::playerId),
				true,
				taskId,
				taskState
			),
			(predicate, ignored) -> {
				try {
					return predicates.evaluate(predicate, predicateContext);
				} catch (RuntimeException error) {
					PixelTzzPro.LOGGER.warn(
						"Player-terminal route predicate {} failed for {}",
						predicate,
						player.getUUID(),
						error
					);
					return false;
				}
			}
		);
		if (route.pageId().isEmpty()) {
			return TerminalAuthorityResult.rejected(
				OperationCode.DEFINITION_UNAVAILABLE,
				route.message().isBlank() ? "当前没有适用于你的玩家终端页面。" : route.message()
			);
		}
		PageDefinition page = definitions.pages().get(route.pageId().orElseThrow());
		if (page == null || !page.game().equals(gameId)) {
			return TerminalAuthorityResult.rejected(
				OperationCode.SNAPSHOT_INVALID,
				"玩家终端路由引用了不可用页面。"
			);
		}
		/*
		 * A non-host OP stays inside the ordinary player terminal. This flag means that the
		 * terminal may expose exactly one built-in host-ownership action: Claim while no host
		 * exists, or Take over while another player is host. The client chooses the matching
		 * fixed operation from the authoritative SessionSnapshot; both operations are still
		 * revalidated and confirmed by the server.
		 */
		boolean takeoverAllowed = isAdminEligible(player)
			&& state.host()
				.map(host -> !host.playerId().equals(player.getUUID()))
				.orElse(true);
		return TerminalAuthorityResult.resolved(
			new TerminalAuthority(
				root,
				state,
				definitions,
				game,
				record,
				route,
				taskId,
				taskState,
				takeoverAllowed
			)
		);
	}

	private static DefinitionSnapshot terminalDefinitionSnapshot(
		final WorldStateV3 root,
		final DefinitionSnapshot live
	) {
		WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
		if (timeline == null) {
			return live;
		}
		String sha256 = timeline.snapshot().sha256();
		TerminalFrozenSnapshot cached = terminalFrozenSnapshot;
		if (
			cached != null
				&& cached.timelineInstanceId().equals(timeline.instanceId())
				&& cached.sha256().equals(sha256)
		) {
			return cached.definitions();
		}
		TimelineSnapshotCompiler.RestoreResult restored = TimelineSnapshotCompiler.restore(
			timeline.gameId(),
			timeline.snapshot()
		);
		if (!restored.success()) {
			throw new IllegalStateException(restored.code() + ": " + restored.message());
		}
		DefinitionSnapshot definitions = restored.snapshot().orElseThrow();
		terminalFrozenSnapshot = new TerminalFrozenSnapshot(
			timeline.instanceId(),
			sha256,
			definitions
		);
		return definitions;
	}

	private static Optional<PlayerTaskState> terminalTaskState(final WorldStateV3 root) {
		WorldStateV3.TimelineInstance timeline = root.timeline().orElse(null);
		WorldStateV3.TaskInstance task = timeline == null
			? null
			: timeline.currentTask().orElse(null);
		if (timeline == null || task == null) {
			return Optional.empty();
		}
		if (
			timeline.status() == WorldStateV3.TimelineStatus.BLOCKED
				|| task.status() == WorldStateV3.TaskStatus.BLOCKED
		) {
			return Optional.of(PlayerTaskState.BLOCKED);
		}
		if (timeline.paused()) {
			return Optional.of(PlayerTaskState.PAUSED);
		}
		if (timeline.status() == WorldStateV3.TimelineStatus.INTERMISSION) {
			return Optional.of(PlayerTaskState.INTERMISSION);
		}
		return Optional.of(
			switch (task.status()) {
				case STARTING -> PlayerTaskState.STARTING;
				case RUNNING -> PlayerTaskState.RUNNING;
				case SETTLING -> PlayerTaskState.SETTLING;
				case SETTLED -> PlayerTaskState.SETTLED;
				case INTERRUPTED -> PlayerTaskState.INTERRUPTED;
				case BLOCKED -> PlayerTaskState.BLOCKED;
			}
		);
	}

	private static TerminalPageBundleResult sendTerminalPageBundle(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session
	) {
		if (!ServerPlayNetworking.canSend(player, PageBundleS2CPayload.TYPE)) {
			return TerminalPageBundleResult.failed(
				"page_bundle_unsupported",
				"客户端当前无法接收玩家终端页面。"
			);
		}
		PageDefinition page = authority.definitions().pages().get(
			session.current().pageId()
		);
		if (page == null || !page.game().equals(authority.game().id())) {
			return TerminalPageBundleResult.failed(
				"page_unavailable",
				"玩家终端目标页面当前不可用。"
			);
		}
		ThemeDefinition theme = page.theme().equals(BuiltInUiResources.defaultTheme().id())
			? BuiltInUiResources.defaultTheme()
			: authority.definitions().themes().get(page.theme());
		if (theme == null) {
			return TerminalPageBundleResult.failed(
				"theme_unavailable",
				"玩家终端页面主题当前不可用。"
			);
		}
		TerminalBindingResult bindingResult = terminalBindingDocument(
			server,
			player,
			authority,
			session
		);
		if (!bindingResult.successful()) {
			return TerminalPageBundleResult.failed(
				bindingResult.status(),
				bindingResult.message()
			);
		}
		byte[] binding = bindingResult.document().orElseThrow();
		try {
			PageBundleS2CPayload.TerminalContext terminal =
				new PageBundleS2CPayload.TerminalContext(
					session.sessionId(),
					session.routeRevision(),
					session.stack().size(),
					session.nextRequestSequence(),
					authority.takeoverAllowed()
				);
			ServerPlayNetworking.send(
				player,
				new PageBundleS2CPayload(
					session.current().instanceId(),
					authority.definitions().generation(),
					authority.root().stateRevision(),
					page.id(),
					theme.id(),
					HexFormat.of().parseHex(page.sha256()),
					page.canonicalDocument().getBytes(StandardCharsets.UTF_8),
					HexFormat.of().parseHex(theme.sha256()),
					theme.canonicalDocument().getBytes(StandardCharsets.UTF_8),
					collectFieldSchemas(authority.definitions(), page),
					PageBundleS2CPayload.PagePurpose.NORMAL,
					Optional.empty(),
					Optional.of(terminal),
					binding
				)
			);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"Could not send player-terminal page bundle {} to {}",
				page.id(),
				player.getUUID(),
				error
			);
			return TerminalPageBundleResult.failed(
				"page_bundle_send_failed",
				"玩家终端页面下发失败。"
			);
		}
		LAST_TERMINAL_BINDING_HASHES.put(player.getUUID(), sha256(binding));
		return TerminalPageBundleResult.sent();
	}

	private static TerminalBindingResult terminalBindingDocument(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session
	) {
		PlayerTerminalProjector.Projection projection;
		try {
			projection = PlayerTerminalProjector.project(
				server,
				authority.definitions(),
				authority.root(),
				player,
				new PlayerTerminalProjector.SessionMetadata(
					session.sessionId(),
					session.current().instanceId(),
					session.current().pageId(),
					session.routeId(),
					session.definitionGeneration(),
					session.stateRevision(),
					true,
					session.current()
						.historyRecord()
						.map(PlayerTerminalSessions.HistoryRecordRef::recordKey),
					session.current().navigationGrant().isPresent()
				)
			);
		} catch (RuntimeException | StackOverflowError error) {
			PixelTzzPro.LOGGER.warn(
				"Could not project player terminal for {}",
				player.getUUID(),
				error
			);
			return TerminalBindingResult.failed(
				"projection_failed",
				terminalProjectionFailureMessage("projection_failed")
			);
		}
		if (!projection.summary().status().equals("ok")) {
			PixelTzzPro.LOGGER.warn(
				"Rejected closed player-terminal projection for {} with status {}",
				player.getUUID(),
				projection.summary().status()
			);
			return TerminalBindingResult.failed(
				projection.summary().status(),
				terminalProjectionFailureMessage(projection.summary().status())
			);
		}
		return TerminalBindingResult.projected(projection.bindingDocument());
	}

	static String terminalProjectionFailureMessage(final String status) {
		Objects.requireNonNull(status, "status");
		return switch (status) {
			case "binding_limit" -> "玩家终端公开数据超过安全上限，页面已关闭。";
			case "frozen_snapshot_invalid" ->
				"当前游戏的冻结定义无法安全恢复，玩家终端已关闭。";
			case "projection_failed" -> "玩家终端公开数据生成失败，页面已关闭。";
			default -> "玩家终端公开数据当前不可用，页面已关闭。";
		};
	}

	private static void failTerminalPageMutationDelivery(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session previousVisibleSession,
		final Optional<Long> requestSequence,
		final TerminalPageBundleResult delivery
	) {
		if (delivery.successful()) {
			throw new IllegalArgumentException(
				"successful terminal page delivery cannot enter failure closure"
			);
		}
		failTerminalPageMutation(
			server,
			player,
			previousVisibleSession,
			requestSequence,
			OperationCode.SNAPSHOT_INVALID,
			authority.root().stateRevision(),
			authority.definitions().generation(),
			delivery.status(),
			delivery.message()
		);
	}

	private static void failTerminalPageMutation(
		final MinecraftServer server,
		final ServerPlayer player,
		final PlayerTerminalSessions.Session previousVisibleSession,
		final Optional<Long> requestSequence,
		final OperationCode operationCode,
		final long stateRevision,
		final long definitionGeneration,
		final String reason,
		final String message
	) {
		requestSequence.ifPresent(sequence ->
			sendOperationResult(
				player,
				sequence,
				operationCode,
				message,
				stateRevision,
				definitionGeneration,
				false
			)
		);
		invalidateTerminalSession(
			server,
			player,
			previousVisibleSession,
			reason,
			message
		);
	}

	private static String sha256(final byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static boolean terminalRouteChanged(
		final PlayerTerminalSessions.Session session,
		final TerminalAuthority authority,
		final Identifier rootPage
	) {
		return !session.routeId().equals(authority.route().routeId())
			|| !session.rootPage().equals(rootPage);
	}

	/**
	 * Returns the number of leading frames that remain authorized by the current game state.
	 *
	 * <p>Navigation grants are rebound to their source buttons without consuming action usage or
	 * cooldown. History frames are retained only while the same opaque record remains visible and
	 * still resolves to the same registered detail page.
	 */
	private static int authorizedTerminalStackDepth(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session
	) {
		List<PlayerTerminalSessions.Frame> stack = session.stack();
		PlayerActionAuthority.PredicateEvaluator predicates =
			terminalActionPredicates(server, player, authority);
		for (int index = 1; index < stack.size(); index++) {
			PlayerTerminalSessions.Frame frame = stack.get(index);
			if (frame.navigationGrant().isPresent()) {
				PlayerTerminalSessions.NavigationGrant grant =
					frame.navigationGrant().orElseThrow();
				PlayerActionAuthority.NavigationValidation validation =
					PlayerActionAuthority.validateNavigationGrant(
						authority.root(),
						authority.definitions(),
						player.getUUID(),
						true,
						grant,
						frame.pageId(),
						predicates
					);
				if (!validation.allowed()) {
					PixelTzzPro.LOGGER.debug(
						"Player terminal navigation grant became unavailable for {} at depth {}: {} ({})",
						player.getUUID(),
						index,
						validation.message(),
						validation.denialCode().map(Enum::name).orElse("unknown")
					);
					return index;
				}
			}
			PlayerTerminalSessions.HistoryRecordRef history =
				frame.historyRecord().orElse(null);
			if (
				history != null
					&& !historyDetailFrameStillAuthorized(
						server,
						player,
						authority,
						session,
						frame,
						history
					)
			) {
				PixelTzzPro.LOGGER.debug(
					"Player terminal history frame became unavailable for {} at depth {}",
					player.getUUID(),
					index
				);
				if (frame.navigationGrant().isPresent()) {
					// A same-page history selection is secondary to the still-valid page grant.
					// Keep the page and let the projector recover to another visible record.
					continue;
				}
				return index;
			}
			if (frame.navigationGrant().isEmpty() && history == null) {
				PixelTzzPro.LOGGER.debug(
					"Player terminal frame has no authorization anchor for {} at depth {}",
					player.getUUID(),
					index
				);
				return index;
			}
		}
		return stack.size();
	}

	private static boolean historyDetailFrameStillAuthorized(
		final MinecraftServer server,
		final ServerPlayer player,
		final TerminalAuthority authority,
		final PlayerTerminalSessions.Session session,
		final PlayerTerminalSessions.Frame frame,
		final PlayerTerminalSessions.HistoryRecordRef history
	) {
		PlayerTerminalProjector.HistoryDetailTarget target =
			PlayerTerminalProjector.resolveHistoryDetail(
				server,
				authority.definitions(),
				authority.root(),
				player,
				new PlayerTerminalProjector.SessionMetadata(
					session.sessionId(),
					frame.instanceId(),
					frame.pageId(),
					session.routeId(),
					authority.definitions().generation(),
					authority.root().stateRevision(),
					true,
					Optional.of(history.recordKey())
				),
				history.recordKey()
			).orElse(null);
		return target != null
			&& target.recordKey().equals(history.recordKey())
			&& target.pageId().equals(frame.pageId());
	}

	private static void refreshOpenPlayerTerminals(
		final MinecraftServer server,
		final boolean forcePageBundle
	) {
		for (PlayerTerminalSessions.Session existing : PLAYER_TERMINAL_SESSIONS.snapshot()) {
			ServerPlayer player = server.getPlayerList().getPlayer(existing.playerId());
			if (player == null || !verified(player)) {
				PLAYER_TERMINAL_SESSIONS.remove(existing.playerId());
				LAST_TERMINAL_BINDING_HASHES.remove(existing.playerId());
				continue;
			}
			if (terminalTransitionsToParticipantRecap(server, player)) {
				releaseTerminalSessionForRecap(server, player, existing);
				continue;
			}
			TerminalAuthorityResult resolved = resolveTerminalAuthority(server, player);
			if (!resolved.successful()) {
				invalidateTerminalSession(
					server,
					player,
					existing,
					"route_unavailable",
					resolved.message()
				);
				continue;
			}
			TerminalAuthority authority = resolved.authority().orElseThrow();
			Identifier rootPage = authority.route().pageId().orElseThrow();
			boolean routeChanged = terminalRouteChanged(existing, authority, rootPage);
			if (routeChanged) {
				PlayerTerminalSessions.MutationResult replaced =
					PLAYER_TERMINAL_SESSIONS.replaceRoute(
						player.getUUID(),
						authority.definitions().generation(),
						authority.root().stateRevision(),
						authority.route().routeId(),
						rootPage,
						authority.takeoverAllowed()
					);
				if (!replaced.successful()) {
					invalidateTerminalSession(
						server,
						player,
						existing,
						"route_replace_failed",
						replaced.message()
					);
					continue;
				}
				TerminalPageBundleResult delivery = sendTerminalPageBundle(
					server,
					player,
					authority,
					replaced.session().orElseThrow()
				);
				if (!delivery.successful()) {
					failTerminalPageMutationDelivery(
						server,
						player,
						authority,
						existing,
						Optional.empty(),
						delivery
					);
				} else {
					clearPendingPlayerActionConfirmations(player.getUUID());
					clearPendingTerminalHostConfirmationMetadata(player.getUUID());
					CONFIRMATIONS.cancelOperator(player.getUUID());
				}
				continue;
			}
			int retainedDepth = authorizedTerminalStackDepth(
				server,
				player,
				authority,
				existing
			);
			if (retainedDepth < existing.stack().size()) {
				PlayerTerminalSessions.MutationResult truncated =
					PLAYER_TERMINAL_SESSIONS.truncateToDepth(
						player.getUUID(),
						retainedDepth,
						authority.root().stateRevision(),
						authority.takeoverAllowed()
					);
				if (!truncated.successful()) {
					invalidateTerminalSession(
						server,
						player,
						existing,
						"navigation_truncate_failed",
						truncated.message()
					);
					continue;
				}
				PlayerTerminalSessions.MutationResult reissued =
					PLAYER_TERMINAL_SESSIONS.reissue(
						player.getUUID(),
						authority.definitions().generation(),
						authority.root().stateRevision(),
						authority.takeoverAllowed()
					);
				if (!reissued.successful()) {
					invalidateTerminalSession(
						server,
						player,
						existing,
						"page_reissue_failed",
						reissued.message()
					);
					continue;
				}
				TerminalPageBundleResult delivery = sendTerminalPageBundle(
					server,
					player,
					authority,
					reissued.session().orElseThrow()
				);
				if (!delivery.successful()) {
					failTerminalPageMutationDelivery(
						server,
						player,
						authority,
						existing,
						Optional.empty(),
						delivery
					);
					continue;
				}
				clearPendingPlayerActionConfirmations(player.getUUID());
				clearPendingTerminalHostConfirmationMetadata(player.getUUID());
				CONFIRMATIONS.cancelOperator(player.getUUID());
				continue;
			}
			if (
				forcePageBundle
					|| existing.takeoverAllowed() != authority.takeoverAllowed()
			) {
				PlayerTerminalSessions.MutationResult reissued =
					PLAYER_TERMINAL_SESSIONS.reissue(
						player.getUUID(),
						authority.definitions().generation(),
						authority.root().stateRevision(),
						authority.takeoverAllowed()
					);
				if (!reissued.successful()) {
					invalidateTerminalSession(
						server,
						player,
						existing,
						"page_reissue_failed",
						reissued.message()
					);
					continue;
				}
				TerminalPageBundleResult delivery = sendTerminalPageBundle(
					server,
					player,
					authority,
					reissued.session().orElseThrow()
				);
				if (!delivery.successful()) {
					failTerminalPageMutationDelivery(
						server,
						player,
						authority,
						existing,
						Optional.empty(),
						delivery
					);
				} else {
					clearPendingPlayerActionConfirmations(player.getUUID());
					clearPendingTerminalHostConfirmationMetadata(player.getUUID());
					CONFIRMATIONS.cancelOperator(player.getUUID());
				}
				continue;
			}
			TerminalBindingResult bindingResult = terminalBindingDocument(
				server,
				player,
				authority,
				existing
			);
			if (!bindingResult.successful()) {
				failTerminalPageMutation(
					server,
					player,
					existing,
					Optional.empty(),
					OperationCode.SNAPSHOT_INVALID,
					authority.root().stateRevision(),
					authority.definitions().generation(),
					bindingResult.status(),
					bindingResult.message()
				);
				continue;
			}
			byte[] binding = bindingResult.document().orElseThrow();
			String bindingHash = sha256(binding);
			boolean changed = existing.stateRevision() != authority.root().stateRevision()
				|| !bindingHash.equals(LAST_TERMINAL_BINDING_HASHES.get(player.getUUID()));
			if (changed) {
				if (
					!ServerPlayNetworking.canSend(
						player,
						TerminalBindingDeltaS2CPayload.TYPE
					)
				) {
					failTerminalPageMutation(
						server,
						player,
						existing,
						Optional.empty(),
						OperationCode.SNAPSHOT_INVALID,
						authority.root().stateRevision(),
						authority.definitions().generation(),
						"binding_delta_unsupported",
						"客户端当前无法接收玩家终端数据更新。"
					);
					continue;
				}
				try {
					ServerPlayNetworking.send(
						player,
						new TerminalBindingDeltaS2CPayload(
							existing.sessionId(),
							existing.current().instanceId(),
							existing.definitionGeneration(),
							authority.root().stateRevision(),
							existing.routeRevision(),
							binding
						)
					);
				} catch (RuntimeException error) {
					PixelTzzPro.LOGGER.warn(
						"Could not send player-terminal binding delta to {}",
						player.getUUID(),
						error
					);
					failTerminalPageMutation(
						server,
						player,
						existing,
						Optional.empty(),
						OperationCode.SNAPSHOT_INVALID,
						authority.root().stateRevision(),
						authority.definitions().generation(),
						"binding_delta_send_failed",
						"玩家终端数据更新下发失败。"
					);
					continue;
				}
				PlayerTerminalSessions.MutationResult touched =
					PLAYER_TERMINAL_SESSIONS.touchProjection(
						player.getUUID(),
						authority.root().stateRevision(),
						authority.takeoverAllowed()
					);
				if (!touched.successful()) {
					failTerminalPageMutation(
						server,
						player,
						existing,
						Optional.empty(),
						OperationCode.SNAPSHOT_INVALID,
						authority.root().stateRevision(),
						authority.definitions().generation(),
						"binding_delta_commit_failed",
						"玩家终端数据已变化，但服务端无法确认新的页面状态。"
					);
					continue;
				}
				LAST_TERMINAL_BINDING_HASHES.put(player.getUUID(), bindingHash);
			}
		}
	}

	/**
	 * Narrow V3B bridge: a committed per-viewer message-history batch can refresh an already-open
	 * player terminal without exposing the terminal session registry or its mutation surface.
	 */
	static void refreshPlayerTerminalsAfterMessageHistory(
		final MinecraftServer server
	) {
		refreshOpenPlayerTerminals(server, false);
	}

	private static boolean terminalTransitionsToParticipantRecap(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		if (
			root == null
				|| wrapper.hostId().filter(player.getUUID()::equals).isPresent()
				|| root.timeline()
					.filter(timeline ->
						timeline.status() == WorldStateV3.TimelineStatus.COMPLETED
							|| timeline.status() == WorldStateV3.TimelineStatus.INTERRUPTED
					)
					.isEmpty()
		) {
			return false;
		}
		TimelineViewS2CPayload recap = TimelineViewBuilder.build(
			root,
			player.getUUID(),
			false,
			0L,
			playerId -> server.getPlayerList().getPlayer(playerId) != null
		);
		return recap.status().equals("ready") && recap.recapView();
	}

	private static void releaseTerminalSessionForRecap(
		final MinecraftServer server,
		final ServerPlayer player,
		final PlayerTerminalSessions.Session session
	) {
		sendTerminalInvalidation(
			player,
			session,
			PixelTzzWorldState.get(server).stateRevision(),
			"timeline_terminal",
			"本局已结束，赛后回顾已准备就绪。"
		);
		PLAYER_TERMINAL_SESSIONS.remove(player.getUUID());
		LAST_TERMINAL_BINDING_HASHES.remove(player.getUUID());
		clearPendingPlayerActionConfirmations(player.getUUID());
		clearPendingTerminalHostConfirmationMetadata(player.getUUID());
		CONFIRMATIONS.cancelOperator(player.getUUID());
		PixelTzzPro.LOGGER.info(
			"Closed player terminal {} for {} because the participant recap is ready",
			session.sessionId(),
			player.getUUID()
		);
	}

	private static void invalidateTerminalSession(
		final MinecraftServer server,
		final ServerPlayer player,
		final PlayerTerminalSessions.Session session,
		final String reason,
		final String message
	) {
		sendTerminalInvalidation(
			player,
			session,
			PixelTzzWorldState.get(server).stateRevision(),
			reason,
			message
		);
		PLAYER_TERMINAL_SESSIONS.remove(player.getUUID());
		LAST_TERMINAL_BINDING_HASHES.remove(player.getUUID());
		clearPendingPlayerActionConfirmations(player.getUUID());
		clearPendingTerminalHostConfirmationMetadata(player.getUUID());
		CONFIRMATIONS.cancelOperator(player.getUUID());
		PixelTzzPro.LOGGER.warn(
			"Invalidated player terminal {} for {}: {} ({})",
			session.sessionId(),
			player.getUUID(),
			message,
			reason
		);
	}

	private static void sendTerminalInvalidation(
		final ServerPlayer player,
		final PlayerTerminalSessions.Session session,
		final long stateRevision,
		final String reason,
		final String message
	) {
		if (!ServerPlayNetworking.canSend(player, TerminalInvalidationS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(
			player,
			new TerminalInvalidationS2CPayload(
				session.sessionId(),
				session.current().instanceId(),
				Math.max(stateRevision, session.stateRevision()),
				reason,
				truncate(message, TerminalInvalidationS2CPayload.MAX_MESSAGE_LENGTH)
			)
		);
	}

	private static void onPreviewCatalogRequest(
		final PreviewCatalogRequestC2SPayload payload,
		final ServerPlayNetworking.Context context
	) {
		if (!verified(context.player())) {
			return;
		}
		sendPreviewCatalog(context.server(), context.player());
	}

	private static void sendPreviewCatalog(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		if (!ServerPlayNetworking.canSend(player, PreviewCatalogS2CPayload.TYPE)) {
			return;
		}
		View definitions = DefinitionRegistry.INSTANCE.view();
		if (!canViewConsole(server, player)) {
			sendPreviewCatalog(
				player,
				definitions.active().generation(),
				"forbidden",
				"只有当前主持人可以打开页面预览器",
				List.of()
			);
			return;
		}
		if (!definitions.healthy() || !definitions.active().usable()) {
			sendPreviewCatalog(
				player,
				definitions.active().generation(),
				"unavailable",
				sessionDiagnosticForViewer(
					true,
					definitions.healthy(),
					definitions.diagnosticSummary()
				),
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
			!canViewConsole(context.server(), player)
				|| !definitions.healthy()
				|| !definitions.active().usable()
				|| !ServerPlayNetworking.canSend(player, PageBundleS2CPayload.TYPE)
		) {
			sendPreviewCatalog(context.server(), player);
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
		WorldStateV3 root = PixelTzzWorldState.get(server).currentV3().orElseThrow();
		return fieldIds.stream()
			.map(snapshot.fields()::get)
			.filter(Objects::nonNull)
			.filter(field ->
				ForcedFlowAuthority.playerMayEditFieldInFlow(
					field,
					player,
					instance,
					evaluator,
					context
				)
			)
			.map(field ->
				fieldSchema(
					field,
					exclusiveOptionStates(server, root, member, field)
				)
			)
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
		return fieldSchema(
			field,
			field.exclusiveChoice()
				.map(definition ->
					definition.options()
						.stream()
						.map(option ->
							new ExclusiveOptionState(
								option.value(),
								option.name().json(),
								option.description()
									.map(GameDefinitions.RichText::json)
									.orElse(""),
								option.icon(),
								option.previewPage(),
								"available",
								Optional.empty(),
								Optional.empty(),
								false
							)
						)
						.toList()
				)
				.orElse(List.of())
		);
	}

	private static PageBundleS2CPayload.FieldSchema fieldSchema(
		final FieldDefinition field,
		final List<ExclusiveOptionState> exclusiveOptions
	) {
		List<String> options = field.exclusiveChoice()
			.map(GameDefinitions.ExclusiveChoiceDefinition::values)
			.orElse(field.constraints().options());
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
			options,
			optionalInt(field.constraints().minimumSelections()),
			optionalInt(field.constraints().maximumSelections()),
			exclusiveOptions
		);
	}

	private static List<ExclusiveOptionState> exclusiveOptionStates(
		final MinecraftServer server,
		final WorldStateV3 root,
		final FlowMemberState member,
		final FieldDefinition field
	) {
		if (field.exclusiveChoice().isEmpty()) {
			return List.of();
		}
		UUID scopeId = root.activeGameInstanceId().orElseThrow();
		var definition = field.exclusiveChoice().orElseThrow();
		Map<String, WorldStateV3.ExclusiveReservation> reservations = root
			.exclusiveReservations()
			.stream()
			.filter(reservation -> reservation.gameId().equals(field.game()))
			.filter(reservation -> reservation.fieldId().equals(field.id()))
			.filter(reservation -> reservation.scopeId().equals(scopeId))
			.collect(java.util.stream.Collectors.toMap(
				WorldStateV3.ExclusiveReservation::value,
				java.util.function.Function.identity()
			));
		return definition.options()
			.stream()
			.map(option -> {
				WorldStateV3.ExclusiveReservation reservation = reservations.get(option.value());
				if (reservation == null) {
					return new ExclusiveOptionState(
						option.value(),
						option.name().json(),
						option.description().map(GameDefinitions.RichText::json).orElse(""),
						option.icon(),
						option.previewPage(),
						"available",
						Optional.empty(),
						Optional.empty(),
						false
					);
				}
				boolean self = reservation.ownerId().equals(member.playerId());
				String state = reservation.status() == WorldStateV3.ReservationStatus.HELD
					? self ? "held_by_self" : "held_by_other"
					: self ? "locked_by_self" : "locked_by_other";
				boolean exposeOccupant = self || definition.showOccupant();
				PlayerRecord occupant = root.core().players().get(reservation.ownerId());
				Optional<String> occupantName = exposeOccupant
					? Optional.of(
						occupant == null
							? reservation.ownerId().toString()
							: occupant.lastKnownName()
					)
					: Optional.empty();
				return new ExclusiveOptionState(
					option.value(),
					option.name().json(),
					option.description().map(GameDefinitions.RichText::json).orElse(""),
					option.icon(),
					option.previewPage(),
					state,
					exposeOccupant ? Optional.of(reservation.ownerId()) : Optional.empty(),
					occupantName,
					exposeOccupant
						&& server.getPlayerList().getPlayer(reservation.ownerId()) != null
				);
			})
			.toList();
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
		if (
			PLAYER_TERMINAL_SESSIONS.close(
				context.player().getUUID(),
				payload.instanceId()
			)
		) {
			LAST_TERMINAL_BINDING_HASHES.remove(context.player().getUUID());
			clearPendingPlayerActionConfirmations(context.player().getUUID());
			clearPendingTerminalHostConfirmationMetadata(context.player().getUUID());
			CONFIRMATIONS.cancelOperator(context.player().getUUID());
			return;
		}
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
		PlayerTerminalSessions.Session terminal = PLAYER_TERMINAL_SESSIONS.find(
			context.player().getUUID()
		).orElse(null);
		if (
			terminal != null
				&& terminal.current().instanceId().equals(payload.instanceId())
				&& terminal.definitionGeneration() == payload.generation()
				&& terminal.current().pageId().equals(payload.pageId())
		) {
			onTerminalResourceReport(payload, context, terminal);
			return;
		}
		onForcedResourceReport(payload, context);
	}

	private static void onTerminalResourceReport(
		final ResourceReportC2SPayload payload,
		final ServerPlayNetworking.Context context,
		final PlayerTerminalSessions.Session session
	) {
		if (!payload.requiredMissing() && payload.renderError().isEmpty()) {
			return;
		}
		String message = payload.renderError()
			.map(value -> "玩家终端页面无法渲染：" + value)
			.orElse("玩家终端缺少数据包标记为必需的客户端资源。");
		invalidateTerminalSession(
			context.server(),
			context.player(),
			session,
			payload.renderError().isPresent()
				? "render_failed"
				: "required_resource_missing",
			message
		);
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

		if (
			lastTerminalProjectionTick == Integer.MIN_VALUE
				|| currentTick < lastTerminalProjectionTick
				|| currentTick - lastTerminalProjectionTick
					>= TERMINAL_PROJECTION_INTERVAL_TICKS
		) {
			lastTerminalProjectionTick = currentTick;
			refreshOpenPlayerTerminals(server, false);
			Map<UUID, PlayerTerminalSessions.Session> expiring =
				PLAYER_TERMINAL_SESSIONS.snapshot()
					.stream()
					.collect(
						java.util.stream.Collectors.toMap(
							PlayerTerminalSessions.Session::playerId,
							value -> value
						)
					);
			for (UUID playerId : PLAYER_TERMINAL_SESSIONS.expire(currentTick)) {
				LAST_TERMINAL_BINDING_HASHES.remove(playerId);
				clearPendingPlayerActionConfirmations(playerId);
				clearPendingTerminalHostConfirmationMetadata(playerId);
				CONFIRMATIONS.cancelOperator(playerId);
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				PlayerTerminalSessions.Session expired = expiring.get(playerId);
				if (player != null && expired != null) {
					sendTerminalInvalidation(
						player,
						expired,
						PixelTzzWorldState.get(server).stateRevision(),
						"session_expired",
						"玩家终端长时间未操作，已安全关闭；请从暂停菜单重新打开。"
					);
				}
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
		PLAYER_ACTION_CONFIRMATIONS.clear();
		TERMINAL_HOST_CONFIRMATIONS.clear();
		terminalFrozenSnapshot = null;
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
			refreshOpenPlayerTerminals(server, true);
			sendSnapshots(server, false);
			pushHostConsoleSnapshot(server);
			sendForcedPages(server);
			return;
		}

		ReloadOutcome definitionLoad = DefinitionRegistry.INSTANCE.reload(resourceManager);
		if (definitionLoad.applied()) {
			MessageServerRuntime.rebuildAssetAuthority(
				server,
				DefinitionRegistry.INSTANCE.view().active()
			);
		}
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
		refreshOpenPlayerTerminals(server, true);
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
		refreshOpenPlayerTerminals(server, false);
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
				state.activePhaseId(),
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
				sessionDiagnosticForViewer(
					currentPlayerHost,
					definitions.healthy(),
					definitions.diagnosticSummary()
				),
				animateLoad
					&& state.isSchemaCompatible()
					&& definitions.canStartNewFlows()
			)
		);
	}

	private static String truncateDiagnostic(final String diagnostic) {
		return diagnostic.length() <= 1_024 ? diagnostic : diagnostic.substring(0, 1_021) + "...";
	}

	static String sessionDiagnosticForViewer(
		final boolean currentPlayerHost,
		final boolean definitionsHealthy,
		final String diagnostic
	) {
		Objects.requireNonNull(diagnostic, "diagnostic");
		if (currentPlayerHost) {
			return truncateDiagnostic(diagnostic);
		}
		return definitionsHealthy
			? "数据包定义已就绪。"
			: "数据包定义当前不可用，请联系主持人查看详情。";
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

	/**
	 * Durable clock for persisted player-action usage and cooldown records.
	 *
	 * <p>{@link MinecraftServer#getTickCount()} restarts from zero for every server process and
	 * would therefore make a persisted cooldown appear to come from the future after a restart.
	 * The overworld game time is saved with the world and remains monotonic across normal restarts.
	 */
	private static long playerActionWorldTick(final MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static boolean canViewConsole(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		/*
		 * V3A deliberately removes the administrator-console view from non-host OP players.
		 * Their only elevated UI is the fixed Claim/Take-over action in the least-privilege
		 * player terminal; returning a full console snapshot here would leak the host roster,
		 * actions, diagnostics, and flow state even if the client did not render them.
		 */
		return PixelTzzWorldState.get(server)
			.hostId()
			.filter(player.getUUID()::equals)
			.isPresent();
	}

	private static void onPlayerDisconnected(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		if (!serverStopping) {
			updateReadinessConnection(server, player, false);
		}
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

	private static boolean restoreMemberOnline(
		final MinecraftServer server,
		final ServerPlayer player
	) {
		return updateMemberConnection(server, player.getUUID(), player.getPlainTextName(), true);
	}

	private static boolean updateReadinessConnection(
		final MinecraftServer server,
		final ServerPlayer player,
		final boolean online
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV3 state = wrapper.currentV3().orElse(null);
		if (state == null) {
			return false;
		}
		ReadinessAuthority.MutationResult result = ReadinessAuthority.setOnline(
			state,
			player.getUUID(),
			player.getPlainTextName(),
			online,
			UUID.randomUUID(),
			System.currentTimeMillis()
		);
		if (!result.successful()) {
			PixelTzzPro.LOGGER.warn(
				"Could not update readiness connection for {}: {} ({})",
				player.getUUID(),
				result.message(),
				result.code().serializedName()
			);
			return false;
		}
		if (!result.changed()) {
			return false;
		}
		PixelTzzWorldState.CommitV3Result committed = wrapper.commitV3(
			state.stateRevision(),
			ignored -> result.state()
		);
		if (!committed.committed()) {
			PixelTzzPro.LOGGER.warn(
				"Could not persist readiness connection for {}: {}",
				player.getUUID(),
				committed.reason()
			);
			return false;
		}
		return true;
	}

	private static ReadinessAuthority.MutationResult reconcileReadinessPlayers(
		final WorldStateV3 state,
		final List<UUID> playerIds,
		final Set<UUID> onlinePlayerIds,
		final long occurredAtEpochMillis,
		final String reason
	) {
		WorldStateV3 candidate = state;
		boolean changed = false;
		boolean invalidated = false;
		for (UUID playerId : playerIds.stream().distinct().sorted().toList()) {
			ReadinessAuthority.MutationResult result = ReadinessAuthority.reconcilePlayer(
				candidate,
				playerId,
				onlinePlayerIds.contains(playerId),
				UUID.randomUUID(),
				occurredAtEpochMillis,
				reason
			);
			if (!result.successful()) {
				return result;
			}
			candidate = result.state();
			changed |= result.changed();
			invalidated |= result.invalidated();
		}
		return new ReadinessAuthority.MutationResult(
			OperationCode.SUCCESS,
			"",
			candidate,
			changed,
			invalidated
		);
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
		LAST_TIMELINE_VIEW_REQUEST_TICK.remove(playerId);
		PREVIEW_INSTANCES.remove(playerId);
		PLAYER_TERMINAL_SESSIONS.remove(playerId);
		LAST_TERMINAL_BINDING_HASHES.remove(playerId);
		clearPendingPlayerActionConfirmations(playerId);
		clearPendingTerminalHostConfirmationMetadata(playerId);
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
		TERMINAL_OPEN_REQUEST_GATE.remove(playerId);
		FLOW_ACTION_REQUEST_GATE.remove(playerId);
		RESOURCE_REPORT_GATE.remove(playerId);
	}

	private static void clearConnections() {
		HANDSHAKE_TICKS_REMAINING.clear();
		VERIFIED_PLAYERS.clear();
		LAST_ADMIN_ELIGIBILITY.clear();
		LAST_TIMELINE_VIEW_REQUEST_TICK.clear();
		PREVIEW_INSTANCES.clear();
		PLAYER_TERMINAL_SESSIONS.clear();
		LAST_TERMINAL_BINDING_HASHES.clear();
		PLAYER_ACTION_CONFIRMATIONS.clear();
		TERMINAL_HOST_CONFIRMATIONS.clear();
		terminalFrozenSnapshot = null;
		lastTerminalProjectionTick = Integer.MIN_VALUE;
		hostConsoleRefreshPending = false;
		CONTROL_REQUEST_GATE.clear();
		TERMINAL_OPEN_REQUEST_GATE.clear();
		FLOW_ACTION_REQUEST_GATE.clear();
		RESOURCE_REPORT_GATE.clear();
	}

	private record TerminalBindingResult(
		Optional<byte[]> document,
		String status,
		String message
	) {
		private TerminalBindingResult {
			document = Objects.requireNonNull(document, "document");
			status = Objects.requireNonNull(status, "status");
			message = Objects.requireNonNull(message, "message");
			if (
				document.isPresent() != status.equals("ok")
					|| (document.isEmpty() && message.isBlank())
			) {
				throw new IllegalArgumentException(
					"terminal binding result has mismatched status"
				);
			}
		}

		private boolean successful() {
			return this.document.isPresent();
		}

		private static TerminalBindingResult projected(final byte[] document) {
			return new TerminalBindingResult(
				Optional.of(Objects.requireNonNull(document, "document")),
				"ok",
				""
			);
		}

		private static TerminalBindingResult failed(
			final String status,
			final String message
		) {
			if (status.equals("ok")) {
				throw new IllegalArgumentException(
					"failed terminal binding cannot use ok status"
				);
			}
			return new TerminalBindingResult(Optional.empty(), status, message);
		}
	}

	private record TerminalPageBundleResult(
		boolean successful,
		String status,
		String message
	) {
		private TerminalPageBundleResult {
			status = Objects.requireNonNull(status, "status");
			message = Objects.requireNonNull(message, "message");
			if (successful != status.equals("ok") || (!successful && message.isBlank())) {
				throw new IllegalArgumentException(
					"terminal page-bundle result has mismatched status"
				);
			}
		}

		private static TerminalPageBundleResult sent() {
			return new TerminalPageBundleResult(true, "ok", "");
		}

		private static TerminalPageBundleResult failed(
			final String status,
			final String message
		) {
			if (status.equals("ok")) {
				throw new IllegalArgumentException(
					"failed terminal page bundle cannot use ok status"
				);
			}
			return new TerminalPageBundleResult(false, status, message);
		}
	}

	private record TerminalAuthority(
		WorldStateV4 root,
		WorldStateV2 state,
		DefinitionSnapshot definitions,
		GameDefinition game,
		PlayerRecord player,
		PlayerTerminalRouter.Resolution route,
		Optional<Identifier> taskId,
		Optional<PlayerTaskState> taskState,
		boolean takeoverAllowed
	) {
		private TerminalAuthority {
			Objects.requireNonNull(root, "root");
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(definitions, "definitions");
			Objects.requireNonNull(game, "game");
			Objects.requireNonNull(player, "player");
			Objects.requireNonNull(route, "route");
			taskId = Objects.requireNonNull(taskId, "taskId");
			taskState = Objects.requireNonNull(taskState, "taskState");
			if (route.pageId().isEmpty()) {
				throw new IllegalArgumentException(
					"terminal authority requires an executable page route"
				);
			}
		}
	}

	private record TerminalAuthorityResult(
		Optional<TerminalAuthority> authority,
		OperationCode code,
		String message
	) {
		private TerminalAuthorityResult {
			authority = Objects.requireNonNull(authority, "authority");
			Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			if (
				authority.isPresent() != (code == OperationCode.SUCCESS)
					|| (authority.isEmpty() && message.isBlank())
			) {
				throw new IllegalArgumentException(
					"terminal authority result has mismatched status"
				);
			}
		}

		private boolean successful() {
			return this.authority.isPresent();
		}

		private static TerminalAuthorityResult resolved(
			final TerminalAuthority authority
		) {
			return new TerminalAuthorityResult(
				Optional.of(authority),
				OperationCode.SUCCESS,
				""
			);
		}

		private static TerminalAuthorityResult rejected(
			final OperationCode code,
			final String message
		) {
			if (code == OperationCode.SUCCESS) {
				throw new IllegalArgumentException(
					"rejected terminal authority cannot use SUCCESS"
				);
			}
			return new TerminalAuthorityResult(Optional.empty(), code, message);
		}
	}

	private record TerminalFrozenSnapshot(
		UUID timelineInstanceId,
		String sha256,
		DefinitionSnapshot definitions
	) {
		private TerminalFrozenSnapshot {
			Objects.requireNonNull(timelineInstanceId, "timelineInstanceId");
			sha256 = Objects.requireNonNull(sha256, "sha256");
			Objects.requireNonNull(definitions, "definitions");
			if (!sha256.matches("[0-9a-f]{64}")) {
				throw new IllegalArgumentException(
					"terminal frozen snapshot hash is invalid"
				);
			}
		}
	}

	private record PendingPlayerActionConfirmation(
		UUID playerId,
		SessionAuthorization authorization,
		ActionRequest request,
		PlayerActionAuthority.ConfirmationChallenge challenge
	) {
		private PendingPlayerActionConfirmation {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(authorization, "authorization");
			Objects.requireNonNull(request, "request");
			Objects.requireNonNull(challenge, "challenge");
			if (
				!playerId.equals(authorization.playerId())
					|| !authorization.sessionId().equals(request.sessionId())
					|| !authorization.pageInstanceId().equals(request.pageInstanceId())
					|| !challenge.sessionId().equals(request.sessionId())
					|| !challenge.pageInstanceId().equals(request.pageInstanceId())
					|| !challenge.requestId().equals(request.requestId())
			) {
				throw new IllegalArgumentException(
					"player-action confirmation has mismatched authority"
				);
			}
		}
	}

	private record PendingTerminalHostConfirmation(
		UUID playerId,
		UUID sessionId,
		UUID pageInstanceId,
		long definitionGeneration,
		long routeRevision,
		long acceptedRequestSequence,
		OperationKey operation
	) {
		private PendingTerminalHostConfirmation {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(sessionId, "sessionId");
			Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			Objects.requireNonNull(operation, "operation");
			if (
				definitionGeneration < 0L
					|| routeRevision < 0L
					|| acceptedRequestSequence < 0L
					|| (
						!operation.equals(
							new OperationKey("claim_host", CLAIM_HOST_OPERATION)
						)
							&& !operation.equals(
								new OperationKey(
									"takeover_host",
									TAKEOVER_HOST_OPERATION
								)
							)
					)
			) {
				throw new IllegalArgumentException(
					"terminal host confirmation has invalid authority"
				);
			}
		}
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

	record TimelineCallbackRetryKey(
		String stepKey,
		Optional<UUID> playerId
	) {
		TimelineCallbackRetryKey {
			Objects.requireNonNull(stepKey, "stepKey");
			playerId = Objects.requireNonNull(playerId, "playerId");
			if (stepKey.isBlank()) {
				throw new IllegalArgumentException("stepKey cannot be blank");
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
