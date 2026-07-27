package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.BuiltInUiResources;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.ReloadOutcome;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.View;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageCloseC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
	private static long loadSequence;
	private static final String MOD_VERSION = FabricLoader.getInstance()
		.getModContainer(PixelTzzPro.MOD_ID)
		.orElseThrow()
		.getMetadata()
		.getVersion()
		.getFriendlyString();

	private PixelTzzServerRuntime() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(PixelTzzServerRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			clearConnections();
			DefinitionRegistry.INSTANCE.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(PixelTzzServerRuntime::onServerTick);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(PixelTzzServerRuntime::onDataPackReloaded);
		ServerPlayConnectionEvents.JOIN.register(PixelTzzServerRuntime::onPlayerJoined);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> removeConnection(listener.player.getUUID()));
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
	}

	private static void onServerStarted(final MinecraftServer server) {
		clearConnections();
		loadSequence = 0L;
		ReloadOutcome definitionLoad = DefinitionRegistry.INSTANCE.reload(server.getResourceManager());
		if (!definitionLoad.healthy()) {
			PixelTzzPro.LOGGER.warn(
				"Pixel TZZ game definitions are not ready at server start: {}",
				definitionLoad.status().serializedName()
			);
		}
		PixelTzzWorldState state = PixelTzzWorldState.get(server);
		if (state.isSchemaCompatible()) {
			PixelTzzPro.LOGGER.info(
				"Loaded Pixel TZZ world state: phase={}, stateRevision={}",
				state.phase().getSerializedName(),
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
		removeConnection(playerId);
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
		sendSnapshot(context.server(), context.player(), true);
		PixelTzzPro.LOGGER.info(
			"Verified Pixel TZZ client {} (mod {}, protocol {})",
			context.player().getPlainTextName(),
			payload.modVersion(),
			payload.protocolVersion()
		);
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
		PreviewPageInstance instance = PREVIEW_INSTANCES.get(context.player().getUUID());
		if (
			instance == null
				|| !instance.id.equals(payload.instanceId())
				|| instance.snapshot.generation() != payload.generation()
				|| !instance.page.id().equals(payload.pageId())
		) {
			return;
		}

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
		if (!success) {
			PixelTzzPro.LOGGER.warn("Data-pack reload failed; keeping the previous Pixel TZZ state and resources");
			DefinitionRegistry.INSTANCE.markPlatformReloadFailed();
			sendSnapshots(server, false);
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
		sendSnapshots(server, animateLoad);

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
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (VERIFIED_PLAYERS.contains(player.getUUID()) && ServerPlayNetworking.canSend(player, SessionSnapshotS2CPayload.TYPE)) {
				sendSnapshot(server, player, animateLoad);
			}
		}
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
				state.phase().getSerializedName(),
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

	private static String truncate(final String value, final int maximumLength) {
		return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
	}

	private static boolean verified(final ServerPlayer player) {
		return VERIFIED_PLAYERS.contains(player.getUUID());
	}

	private static boolean isAdminEligible(final ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static void removeConnection(final UUID playerId) {
		HANDSHAKE_TICKS_REMAINING.remove(playerId);
		VERIFIED_PLAYERS.remove(playerId);
		LAST_ADMIN_ELIGIBILITY.remove(playerId);
		PREVIEW_INSTANCES.remove(playerId);
	}

	private static void clearConnections() {
		HANDSHAKE_TICKS_REMAINING.clear();
		VERIFIED_PLAYERS.clear();
		LAST_ADMIN_ELIGIBILITY.clear();
		PREVIEW_INSTANCES.clear();
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
