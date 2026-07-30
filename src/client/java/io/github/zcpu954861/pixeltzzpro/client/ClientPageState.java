package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight;
import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight.Diagnostic;
import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight.Report;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.ExclusiveChoiceMutationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ExclusiveChoiceMutationC2SPayload.Operation;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FlowContext;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.PagePurpose;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageCloseC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload.CachedDocuments;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.StatefulRequest;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalBindingDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalInvalidationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalIntentC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalIntentC2SPayload.Intent;
import io.github.zcpu954861.pixeltzzpro.network.payload.TerminalOpenC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.TerminalContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache.Key;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache.ParsedDocuments;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContextDocument;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

/**
 * Client projection of the administrator page-preview protocol.
 */
public final class ClientPageState {
	private static final PageDocumentCache CACHE = new PageDocumentCache();
	private static final String TERMINAL_HOST_CONTROL_NODE_ID = "system_host_control";
	private static final Identifier CLAIM_HOST_OPERATION = Identifier.parse(
		"pixel-tzz-pro:host/claim"
	);
	private static final Identifier TAKEOVER_HOST_OPERATION = Identifier.parse(
		"pixel-tzz-pro:host/takeover"
	);

	private static Catalog catalog = Catalog.idle();
	private static PageStatus pageStatus = PageStatus.IDLE;
	private static String pageMessage = "";
	private static ActivePage activePage;
	private static PagePurpose pagePurpose = PagePurpose.PREVIEW;
	private static long nextFlowRequestSequence;
	private static PendingFlowAction pendingFlowAction;
	private static OperationResultS2CPayload flowActionResult;
	private static PendingExclusiveChoiceMutation pendingExclusiveChoiceMutation;
	private static OperationResultS2CPayload exclusiveChoiceMutationResult;
	private static long nextTerminalOpenRequestSequence;
	private static Long pendingTerminalOpenRequestSequence;
	private static long nextTerminalRequestSequence;
	private static PendingTerminalIntent pendingTerminalIntent;
	private static OperationResultS2CPayload terminalIntentResult;
	private static TerminalRelease terminalRelease;
	private static boolean terminalReleaseConsumed;
	private static OperationCode fatalFlowBlock;
	private static long revision;

	private ClientPageState() {
	}

	public static synchronized void beginConnection() {
		reset(false);
	}

	public static synchronized void disconnect() {
		reset(false);
	}

	public static synchronized void requestCatalog() {
		if (!ClientPlayNetworking.canSend(PreviewCatalogRequestC2SPayload.TYPE)) {
			catalog = new Catalog(CatalogStatus.ERROR, 0L, "页面预览协议当前不可用", List.of());
			revision++;
			return;
		}
		catalog = new Catalog(CatalogStatus.LOADING, catalog.generation, "", List.of());
		revision++;
		ClientPlayNetworking.send(PreviewCatalogRequestC2SPayload.INSTANCE);
	}

	public static synchronized void acceptCatalog(final PreviewCatalogS2CPayload payload) {
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			catalog = new Catalog(CatalogStatus.ERROR, payload.generation(), "页面预览协议版本不兼容", List.of());
			failPendingPageRequest("页面预览协议版本不兼容");
			revision++;
			return;
		}
		CatalogStatus status = switch (payload.status()) {
			case "ready", "truncated" -> CatalogStatus.READY;
			default -> CatalogStatus.ERROR;
		};
		catalog = new Catalog(status, payload.generation(), payload.message(), payload.entries());
		if (status == CatalogStatus.ERROR) {
			failPendingPageRequest(
				payload.message().isEmpty() ? "页面未能从当前健康 generation 下发" : payload.message()
			);
		}
		revision++;
	}

	private static void failPendingPageRequest(final String message) {
		if (pageStatus == PageStatus.LOADING) {
			pageStatus = PageStatus.ERROR;
			pageMessage = message;
		}
	}

	public static synchronized void requestPage(final Identifier pageId) {
		Objects.requireNonNull(pageId, "pageId");
		if (forcedPageActive()) {
			return;
		}
		if (!ClientPlayNetworking.canSend(PreviewPageRequestC2SPayload.TYPE)) {
			pageStatus = PageStatus.ERROR;
			pageMessage = "页面同步协议当前不可用";
			revision++;
			return;
		}
		releaseActive(true);
		clearTerminalOpenPending();
		clearTerminalIntentState();
		pagePurpose = PagePurpose.PREVIEW;
		pageStatus = PageStatus.LOADING;
		pageMessage = "";
		revision++;
		Optional<CachedDocuments> cachedDocuments = CACHE.find(catalog.generation(), pageId)
			.map(
				cached -> new CachedDocuments(
					cached.generation(),
					cached.themeId(),
					cached.pageHash(),
					cached.themeHash()
				)
			);
		ClientPlayNetworking.send(new PreviewPageRequestC2SPayload(pageId, cachedDocuments));
	}

	/**
	 * Opens a normal player terminal only after an explicit local user action.
	 */
	public static synchronized boolean openTerminal() {
		if (forcedPageActive()) {
			return false;
		}
		if (pendingTerminalOpenRequestSequence != null) {
			return false;
		}
		if (!ClientPlayNetworking.canSend(TerminalOpenC2SPayload.TYPE)) {
			releaseActive(true);
			clearTerminalOpenPending();
			pagePurpose = PagePurpose.NORMAL;
			pageStatus = PageStatus.ERROR;
			pageMessage = "玩家终端协议当前不可用";
			revision++;
			return false;
		}
		releaseActive(true);
		clearTerminalIntentState();
		pagePurpose = PagePurpose.NORMAL;
		pageStatus = PageStatus.LOADING;
		pageMessage = "";
		long sequence = nextTerminalOpenRequestSequence++;
		try {
			ClientPlayNetworking.send(
				new TerminalOpenC2SPayload(NetworkProtocol.CURRENT_VERSION, sequence)
			);
		} catch (RuntimeException error) {
			pageStatus = PageStatus.ERROR;
			pageMessage = "玩家终端请求未能发送";
			revision++;
			return false;
		}
		pendingTerminalOpenRequestSequence = sequence;
		revision++;
		return true;
	}

	public static synchronized void acceptPageBundle(final PageBundleS2CPayload payload) {
		if (!replacementAllowed(payload)) {
			return;
		}
		if (
			payload.purpose() == PagePurpose.NORMAL
				|| (
					pagePurpose == PagePurpose.NORMAL
						&& payload.purpose() == PagePurpose.FORCED_FLOW
				)
		) {
			clearTerminalOpenPending();
		}
		boolean sameFlowPage = activePage != null
			&& activePage.purpose == PagePurpose.FORCED_FLOW
			&& payload.purpose() == PagePurpose.FORCED_FLOW
			&& activePage.instanceId.equals(payload.instanceId())
			&& activePage.flowContext.map(FlowContext::flowInstanceId)
				.equals(payload.flowContext().map(FlowContext::flowInstanceId));
		boolean sameTerminalSession = activePage != null
			&& activePage.purpose == PagePurpose.NORMAL
			&& payload.purpose() == PagePurpose.NORMAL
			&& activePage.terminalContext.map(TerminalContext::sessionId)
				.equals(payload.terminalContext().map(TerminalContext::sessionId));
		boolean sameTerminalGeneration = sameTerminalSession
			&& activePage.generation == payload.generation();
		long localNextFlowSequence = nextFlowRequestSequence;
		long localNextTerminalSequence = nextTerminalRequestSequence;
		releaseActive(false);
		terminalRelease = null;
		terminalReleaseConsumed = false;
		pagePurpose = payload.purpose();
		fatalFlowBlock = null;
		Key pinnedKey = null;
		try {
			byte[] pageHash = payload.pageHash();
			byte[] themeHash = payload.themeHash();
			byte[] pageDocument = payload.pageJson();
			byte[] themeDocument = payload.themeJson();
			Key key;
			Optional<ParsedDocuments> cachedParsed = Optional.empty();
			if (payload.documentsOmitted()) {
				key = new Key(
					payload.generation(),
					payload.pageId(),
					HexFormat.of().formatHex(pageHash)
				);
				var cached = CACHE.get(key)
					.orElseThrow(
						() -> new IllegalArgumentException(
							"服务端省略了页面文档，但本地没有对应缓存"
						)
					);
				if (
					!cached.matchesEnvelope(
						payload.generation(),
						payload.pageId(),
						payload.themeId(),
						pageHash,
						themeHash
					)
				) {
					throw new IllegalArgumentException("服务端省略的页面文档与本地缓存摘要不一致");
				}
				cachedParsed = CACHE.parsed(key);
				if (cachedParsed.isEmpty()) {
					pageDocument = cached.pageDocument();
					themeDocument = cached.themeDocument();
				}
			} else {
				key = CACHE.store(
					payload.generation(),
					payload.pageId(),
					pageHash,
					pageDocument,
					payload.themeId(),
					themeHash,
					themeDocument
				);
			}
			PageDefinition page;
			ThemeDefinition theme;
			if (cachedParsed.isPresent()) {
				ParsedDocuments parsed = cachedParsed.orElseThrow();
				page = parsed.page();
				theme = parsed.theme();
			} else {
				String pageJson = decodeUtf8(pageDocument, "page");
				String themeJson = decodeUtf8(themeDocument, "theme");
				ParseResult<PageDefinition> parsedPage = UiDefinitionParser.parsePage(
					payload.pageId(),
					pageJson
				);
				ParseResult<ThemeDefinition> parsedTheme = UiDefinitionParser.parseTheme(
					payload.themeId(),
					themeJson
				);
				if (!parsedPage.valid() || !parsedTheme.valid()) {
					throw new IllegalArgumentException(
						"页面文档解析失败: " + summarizeIssues(parsedPage, parsedTheme)
					);
				}
				page = parsedPage.value().orElseThrow();
				theme = parsedTheme.value().orElseThrow();
				if (!page.theme().equals(theme.id())) {
					throw new IllegalArgumentException("页面主题 ID 与同步文档不一致");
				}
				String pageHashHex = HexFormat.of().formatHex(pageHash);
				String themeHashHex = HexFormat.of().formatHex(themeHash);
				if (!page.sha256().equals(pageHashHex) || !theme.sha256().equals(themeHashHex)) {
					throw new IllegalArgumentException("页面规范化 SHA-256 与同步信封不一致");
				}
				CACHE.attachParsed(key, page, theme);
			}
			if (!CACHE.pin(key)) {
				throw new IllegalStateException("页面缓存条目在激活前已被回收");
			}
			pinnedKey = key;
			Report resources = ClientResourcePreflight.check(page, theme);
			BindingContext serverContext = payload.bindingJson().length == 0
				? BindingContext.empty()
				: BindingContextDocument.decode(payload.bindingJson());
			activePage = new ActivePage(
				payload.instanceId(),
				payload.generation(),
				payload.stateRevision(),
				page,
				theme,
				payload.fields()
					.stream()
					.collect(
						java.util.stream.Collectors.toUnmodifiableMap(
							PageBundleS2CPayload.FieldSchema::id,
							field -> field
						)
					),
				key,
				resources,
				payload.purpose(),
				payload.flowContext(),
				payload.terminalContext(),
				serverContext
			);
			nextFlowRequestSequence = payload.flowContext()
				.map(FlowContext::nextRequestSequence)
				.map(serverNext ->
					sameFlowPage ? Math.max(localNextFlowSequence, serverNext) : serverNext
				)
				.orElse(0L);
			nextTerminalRequestSequence = payload.terminalContext()
				.map(TerminalContext::nextRequestSequence)
				.map(serverNext ->
					sameTerminalGeneration
						? Math.max(localNextTerminalSequence, serverNext)
						: serverNext
				)
				.orElse(0L);
			if (
				payload.purpose() == PagePurpose.NORMAL
					&& (!sameTerminalSession || !sameTerminalGeneration)
			) {
				pendingTerminalIntent = null;
				terminalIntentResult = null;
			}
			acceptExclusiveMutationBundle(activePage);
			pageStatus = resources.requiredMissing() ? PageStatus.RESOURCE_ERROR : PageStatus.READY;
			pageMessage = resources.requiredMissing()
				? "缺少页面必需资源，已进入安全错误页"
				: "";
			CACHE.removeUnpinnedBefore(payload.generation());
			sendResourceReport(activePage);
		} catch (RuntimeException error) {
			if (pinnedKey != null) {
				CACHE.unpin(pinnedKey);
			}
			activePage = null;
			pageStatus = PageStatus.ERROR;
			pageMessage = truncate(
				error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
				512
			);
			if (payload.purpose() == PagePurpose.FORCED_FLOW) {
				sendRenderReport(
					payload.instanceId(),
					payload.generation(),
					payload.pageId(),
					false,
					Optional.of(pageMessage)
				);
			}
		}
		revision++;
	}

	/**
	 * Applies a binding-only terminal update without replacing the page definition or instance.
	 */
	public static synchronized boolean acceptTerminalBindingDelta(
		final TerminalBindingDeltaS2CPayload payload
	) {
		Objects.requireNonNull(payload, "payload");
		if (
			activePage == null
				|| activePage.purpose != PagePurpose.NORMAL
				|| pageStatus != PageStatus.READY
				|| terminalRelease != null
				|| activePage.generation != payload.generation()
				|| !activePage.instanceId.equals(payload.pageInstanceId())
				|| payload.stateRevision() < activePage.stateRevision
		) {
			return false;
		}
		TerminalContext terminal = activePage.terminalContext.orElseThrow();
		if (
			!terminal.sessionId().equals(payload.sessionId())
				|| terminal.routeRevision() != payload.routeRevision()
		) {
			return false;
		}
		try {
			BindingContext serverContext = BindingContextDocument.decode(payload.bindingJson());
			activePage = activePage.withServerContext(payload.stateRevision(), serverContext);
			revision++;
			return true;
		} catch (RuntimeException error) {
			return false;
		}
	}

	/**
	 * Applies only a matching NORMAL-terminal invalidation. A completed timeline retains the live
	 * render plan for its hide motion; every other reason enters the closeable safety page.
	 */
	public static synchronized boolean acceptTerminalInvalidation(
		final TerminalInvalidationS2CPayload payload
	) {
		Objects.requireNonNull(payload, "payload");
		if (
			activePage == null
				|| activePage.purpose != PagePurpose.NORMAL
				|| payload.stateRevision() < activePage.stateRevision
				|| !activePage.instanceId.equals(payload.pageInstanceId())
		) {
			return false;
		}
		TerminalContext terminal = activePage.terminalContext.orElse(null);
		if (terminal == null || !terminal.sessionId().equals(payload.sessionId())) {
			return false;
		}
		if (payload.reason().equals("timeline_terminal")) {
			pageMessage = "";
			fatalFlowBlock = null;
			clearFlowActionState();
			clearTerminalOpenPending();
			clearTerminalIntentState();
			terminalRelease = new TerminalRelease(payload.reason(), payload.message());
			terminalReleaseConsumed = false;
			revision++;
			return true;
		}
		releaseActive(false);
		pagePurpose = PagePurpose.NORMAL;
		pageStatus = PageStatus.ERROR;
		pageMessage = payload.message();
		terminalRelease = null;
		terminalReleaseConsumed = false;
		fatalFlowBlock = null;
		clearFlowActionState();
		clearTerminalOpenPending();
		clearTerminalIntentState();
		revision++;
		return true;
	}

	public static synchronized void retryResourcePreflight() {
		if (activePage == null || fatalFlowBlock != null) {
			return;
		}
		Report report = ClientResourcePreflight.check(activePage.page, activePage.theme);
		activePage = activePage.withResources(report);
		pageStatus = report.requiredMissing() ? PageStatus.RESOURCE_ERROR : PageStatus.READY;
		pageMessage = report.requiredMissing() ? "缺少页面必需资源，已进入安全错误页" : "";
		sendResourceReport(activePage);
		revision++;
	}

	public static synchronized void reportRenderReady(final UUID pageInstanceId) {
		if (
			activePage == null
				|| !activePage.instanceId.equals(pageInstanceId)
				|| activePage.purpose != PagePurpose.FORCED_FLOW
				|| pageStatus != PageStatus.READY
		) {
			return;
		}
		sendRenderReport(
			activePage.instanceId,
			activePage.generation,
			activePage.page.id(),
			true,
			Optional.empty()
		);
	}

	public static synchronized void reportRenderFailure(
		final UUID pageInstanceId,
		final String message
	) {
		Objects.requireNonNull(pageInstanceId, "pageInstanceId");
		Objects.requireNonNull(message, "message");
		if (
			activePage == null
				|| !activePage.instanceId.equals(pageInstanceId)
				|| activePage.purpose != PagePurpose.FORCED_FLOW
		) {
			return;
		}
		sendRenderReport(
			activePage.instanceId,
			activePage.generation,
			activePage.page.id(),
			false,
			Optional.of(truncate(message.isBlank() ? "unknown render failure" : message, 512))
		);
	}

	public static synchronized ExclusiveMutationSubmission mutateExclusiveChoice(
		final Identifier fieldId,
		final Operation operation,
		final Optional<String> value
	) {
		Objects.requireNonNull(fieldId, "fieldId");
		Objects.requireNonNull(operation, "operation");
		Objects.requireNonNull(value, "value");
		if (
			activePage == null
				|| activePage.purpose != PagePurpose.FORCED_FLOW
				|| pageStatus != PageStatus.READY
		) {
			return ExclusiveMutationSubmission.rejected(
				"当前页面不是可修改的强制流程页面"
			);
		}
		PageBundleS2CPayload.FieldSchema field = activePage.fields.get(fieldId);
		if (field == null || !"exclusive_choice".equals(field.type())) {
			return ExclusiveMutationSubmission.rejected("当前页面没有该独占选择字段");
		}
		if (pendingFlowAction != null || pendingExclusiveChoiceMutation != null) {
			return ExclusiveMutationSubmission.rejected("上一项操作仍在等待服务端确认");
		}
		if (!ClientPlayNetworking.canSend(ExclusiveChoiceMutationC2SPayload.TYPE)) {
			return ExclusiveMutationSubmission.rejected("独占选择同步协议当前不可用");
		}
		FlowContext flow = activePage.flowContext.orElseThrow();
		long sequence = nextFlowRequestSequence;
		try {
			ExclusiveChoiceMutationC2SPayload payload =
				new ExclusiveChoiceMutationC2SPayload(
					new StatefulRequest(
						NetworkProtocol.CURRENT_VERSION,
						sequence,
						Optional.of(activePage.page.game()),
						activePage.generation,
						activePage.stateRevision,
						Optional.of(flow.flowInstanceId()),
						Optional.of(activePage.instanceId)
					),
					flow.flowId(),
					flow.flowVersion(),
					flow.nodeId(),
					flow.instanceRevision(),
					flow.memberRevision(),
					fieldId,
					operation,
					value
				);
			ClientPlayNetworking.send(payload);
		} catch (RuntimeException error) {
			return ExclusiveMutationSubmission.rejected(
				truncate(
					error.getMessage() == null
						? error.getClass().getSimpleName()
						: error.getMessage(),
					256
				)
			);
		}
		nextFlowRequestSequence = sequence + 1L;
		pendingExclusiveChoiceMutation = new PendingExclusiveChoiceMutation(
			sequence,
			activePage.instanceId,
			fieldId,
			operation,
			value,
			flow.memberRevision(),
			false,
			false
		);
		exclusiveChoiceMutationResult = null;
		revision++;
		return ExclusiveMutationSubmission.sent(sequence);
	}

	public static synchronized FlowSubmission submitFlowAction(
		final String actionId,
		final List<FieldValue> fieldValues
	) {
		Objects.requireNonNull(actionId, "actionId");
		Objects.requireNonNull(fieldValues, "fieldValues");
		if (
			activePage == null
				|| activePage.purpose != PagePurpose.FORCED_FLOW
				|| pageStatus != PageStatus.READY
		) {
			return FlowSubmission.rejected("当前页面不是可提交的强制流程页面");
		}
		if (pendingFlowAction != null || pendingExclusiveChoiceMutation != null) {
			return FlowSubmission.rejected("上一项操作仍在等待服务端确认");
		}
		if (!ClientPlayNetworking.canSend(FlowActionC2SPayload.TYPE)) {
			return FlowSubmission.rejected("流程动作协议当前不可用");
		}
		FlowContext flow = activePage.flowContext.orElseThrow();
		long sequence = nextFlowRequestSequence;
		try {
			FlowActionC2SPayload payload = new FlowActionC2SPayload(
				new StatefulRequest(
					NetworkProtocol.CURRENT_VERSION,
					sequence,
					Optional.of(activePage.page.game()),
					activePage.generation,
					activePage.stateRevision,
					Optional.of(flow.flowInstanceId()),
					Optional.of(activePage.instanceId)
				),
				flow.flowId(),
				flow.flowVersion(),
				flow.nodeId(),
				flow.instanceRevision(),
				flow.memberRevision(),
				actionId,
				fieldValues
			);
			ClientPlayNetworking.send(payload);
		} catch (RuntimeException error) {
			return FlowSubmission.rejected(
				truncate(
					error.getMessage() == null
						? error.getClass().getSimpleName()
						: error.getMessage(),
					256
				)
			);
		}
		nextFlowRequestSequence = sequence + 1L;
		pendingFlowAction = new PendingFlowAction(sequence, activePage.instanceId);
		flowActionResult = null;
		revision++;
		return FlowSubmission.sent(sequence);
	}

	public static synchronized TerminalSubmission submitTerminalAction(
		final String nodeId,
		final Identifier actionId
	) {
		return submitTerminalIntent(
			Intent.ACTION,
			Optional.of(Objects.requireNonNull(nodeId, "nodeId")),
			Optional.of(Objects.requireNonNull(actionId, "actionId")),
			Optional.empty()
		);
	}

	/**
	 * Submits the one mod-owned host-ownership action exposed by an ordinary player terminal.
	 *
	 * <p>The reserved node id is never sourced from a data-pack page. The server recognizes this
	 * exact node/action pair before registered player actions, re-derives whether claim or takeover
	 * is currently legal, and binds the confirmation to this terminal request sequence.
	 */
	public static synchronized TerminalSubmission submitTerminalHostControl(
		final Identifier operationId
	) {
		Objects.requireNonNull(operationId, "operationId");
		if (
			!operationId.equals(CLAIM_HOST_OPERATION)
				&& !operationId.equals(TAKEOVER_HOST_OPERATION)
		) {
			return TerminalSubmission.rejected("未知的主持人系统操作");
		}
		if (
			activePage == null
				|| activePage.purpose != PagePurpose.NORMAL
				|| activePage.terminalContext
					.filter(TerminalContext::takeoverAllowed)
					.isEmpty()
		) {
			return TerminalSubmission.rejected("当前玩家终端未授权主持人认领或接管");
		}
		return submitTerminalIntent(
			Intent.ACTION,
			Optional.of(TERMINAL_HOST_CONTROL_NODE_ID),
			Optional.of(operationId),
			Optional.empty()
		);
	}

	public static boolean isTerminalHostControlOperation(
		final String operationType,
		final Identifier operationId
	) {
		Objects.requireNonNull(operationType, "operationType");
		Objects.requireNonNull(operationId, "operationId");
		return (
			operationType.equals("claim_host")
				&& operationId.equals(CLAIM_HOST_OPERATION)
		) || (
			operationType.equals("takeover_host")
				&& operationId.equals(TAKEOVER_HOST_OPERATION)
		);
	}

	public static synchronized TerminalSubmission submitTerminalHistoryDetail(
		final String nodeId,
		final String historyRecordKey
	) {
		return submitTerminalIntent(
			Intent.HISTORY_DETAIL,
			Optional.of(Objects.requireNonNull(nodeId, "nodeId")),
			Optional.empty(),
			Optional.of(Objects.requireNonNull(historyRecordKey, "historyRecordKey"))
		);
	}

	public static synchronized TerminalSubmission submitTerminalBack() {
		return submitTerminalIntent(
			Intent.BACK,
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
	}

	public static synchronized TerminalSubmission refreshTerminal() {
		return submitTerminalIntent(
			Intent.REFRESH,
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
	}

	/**
	 * Hands one matched terminal action over to the shared confirmation controller.
	 *
	 * <p>The confirmation payload is still validated by {@link ClientConsoleState}; this method
	 * only clears the NORMAL-page request that has now received a server-authored review instead
	 * of an operation result. Canceling the review therefore returns to an immediately usable
	 * terminal rather than leaving its button permanently pending.
	 */
	public static synchronized boolean acceptTerminalConfirmation(
		final long requestSequence,
		final Identifier actionId
	) {
		Objects.requireNonNull(actionId, "actionId");
		if (
			pendingTerminalIntent == null
				|| pendingTerminalIntent.intent != Intent.ACTION
				|| pendingTerminalIntent.requestSequence != requestSequence
				|| pendingTerminalIntent.actionId.filter(actionId::equals).isEmpty()
		) {
			return false;
		}
		pendingTerminalIntent = null;
		terminalIntentResult = null;
		revision++;
		return true;
	}

	private static TerminalSubmission submitTerminalIntent(
		final Intent intent,
		final Optional<String> nodeId,
		final Optional<Identifier> actionId,
		final Optional<String> historyRecordKey
	) {
		if (
			activePage == null
				|| activePage.purpose != PagePurpose.NORMAL
				|| pageStatus != PageStatus.READY
				|| terminalRelease != null
		) {
			return TerminalSubmission.rejected("当前页面不是可操作的玩家终端");
		}
		if (pendingTerminalIntent != null) {
			return TerminalSubmission.rejected("相关终端操作仍在等待服务端确认");
		}
		if (!ClientPlayNetworking.canSend(TerminalIntentC2SPayload.TYPE)) {
			return TerminalSubmission.rejected("玩家终端操作协议当前不可用");
		}
		TerminalContext terminal = activePage.terminalContext.orElseThrow();
		long sequence = nextTerminalRequestSequence;
		try {
			ClientPlayNetworking.send(
				new TerminalIntentC2SPayload(
					NetworkProtocol.CURRENT_VERSION,
					terminal.sessionId(),
					activePage.instanceId,
					activePage.generation,
					activePage.stateRevision,
					terminal.routeRevision(),
					sequence,
					intent,
					nodeId,
					actionId,
					historyRecordKey
				)
			);
		} catch (RuntimeException error) {
			return TerminalSubmission.rejected(
				truncate(
					error.getMessage() == null
						? error.getClass().getSimpleName()
						: error.getMessage(),
					256
				)
			);
		}
		nextTerminalRequestSequence = sequence + 1L;
		pendingTerminalIntent = new PendingTerminalIntent(
			sequence,
			activePage.instanceId,
			intent,
			nodeId,
			actionId,
			historyRecordKey
		);
		terminalIntentResult = null;
		revision++;
		return TerminalSubmission.sent(sequence);
	}

	public static synchronized void acceptOperationResult(
		final OperationResultS2CPayload payload
	) {
		boolean terminalOpenResult = pendingTerminalOpenRequestSequence != null
			&& pendingTerminalOpenRequestSequence == payload.requestSequence();
		boolean flowResult = pendingFlowAction != null
			&& pendingFlowAction.requestSequence == payload.requestSequence();
		boolean exclusiveResult = pendingExclusiveChoiceMutation != null
			&& pendingExclusiveChoiceMutation.requestSequence == payload.requestSequence();
		boolean terminalResult = pendingTerminalIntent != null
			&& pendingTerminalIntent.requestSequence == payload.requestSequence();
		if (
			!NetworkProtocol.isCompatible(payload.protocolVersion())
				|| (
					!terminalOpenResult
						&& !flowResult
						&& !exclusiveResult
						&& !terminalResult
				)
		) {
			return;
		}
		if (terminalOpenResult) {
			if (payload.success()) {
				// A successful open is completed by the authoritative NORMAL PageBundle.
				return;
			}
			clearTerminalOpenPending();
			if (pagePurpose == PagePurpose.NORMAL && pageStatus == PageStatus.LOADING) {
				pageStatus = PageStatus.ERROR;
				pageMessage = payload.message().isBlank()
					? "服务端未能打开玩家终端"
					: payload.message();
				revision++;
			}
			return;
		}
		if (flowResult) {
			pendingFlowAction = null;
			flowActionResult = payload;
		} else if (exclusiveResult) {
			exclusiveChoiceMutationResult = payload;
			if (payload.success()) {
				PendingExclusiveChoiceMutation accepted =
					pendingExclusiveChoiceMutation.withResultAccepted();
				pendingExclusiveChoiceMutation = accepted.authoritativeBundleAccepted
					? null
					: accepted;
			} else {
				pendingExclusiveChoiceMutation = null;
			}
		} else {
			pendingTerminalIntent = null;
			terminalIntentResult = payload;
		}
		if (
			activePage != null
				&& activePage.purpose == PagePurpose.FORCED_FLOW
				&& (
					payload.code() == OperationCode.SNAPSHOT_INVALID
						|| payload.code() == OperationCode.RESOURCE_BLOCKED
				)
		) {
			fatalFlowBlock = payload.code();
			pageStatus = payload.code() == OperationCode.RESOURCE_BLOCKED
				? PageStatus.RESOURCE_ERROR
				: PageStatus.ERROR;
			pageMessage = payload.message().isEmpty()
				? "服务端已阻塞当前强制流程。"
				: payload.message();
		}
		revision++;
	}

	public static synchronized Optional<OperationResultS2CPayload> consumeFlowActionResult(
		final long requestSequence
	) {
		if (
			flowActionResult == null
				|| flowActionResult.requestSequence() != requestSequence
		) {
			return Optional.empty();
		}
		OperationResultS2CPayload result = flowActionResult;
		flowActionResult = null;
		return Optional.of(result);
	}

	public static synchronized Optional<OperationResultS2CPayload>
		consumeExclusiveChoiceMutationResult(final long requestSequence) {
		if (
			exclusiveChoiceMutationResult == null
				|| exclusiveChoiceMutationResult.requestSequence() != requestSequence
				|| (
					pendingExclusiveChoiceMutation != null
						&& pendingExclusiveChoiceMutation.requestSequence == requestSequence
				)
		) {
			return Optional.empty();
		}
		OperationResultS2CPayload result = exclusiveChoiceMutationResult;
		exclusiveChoiceMutationResult = null;
		return Optional.of(result);
	}

	public static synchronized boolean flowActionPending() {
		return pendingFlowAction != null;
	}

	public static synchronized boolean exclusiveChoiceMutationPending() {
		return pendingExclusiveChoiceMutation != null;
	}

	public static synchronized boolean exclusiveChoiceMutationPending(final Identifier fieldId) {
		return pendingExclusiveChoiceMutation != null
			&& pendingExclusiveChoiceMutation.fieldId.equals(fieldId);
	}

	public static synchronized Optional<OperationResultS2CPayload> consumeTerminalIntentResult(
		final long requestSequence
	) {
		if (
			terminalIntentResult == null
				|| terminalIntentResult.requestSequence() != requestSequence
		) {
			return Optional.empty();
		}
		OperationResultS2CPayload result = terminalIntentResult;
		terminalIntentResult = null;
		return Optional.of(result);
	}

	public static synchronized boolean terminalIntentPending() {
		return pendingTerminalIntent != null;
	}

	public static synchronized boolean terminalOpenPending() {
		return pendingTerminalOpenRequestSequence != null;
	}

	public static synchronized boolean terminalIntentPending(final String nodeId) {
		return pendingTerminalIntent != null
			&& pendingTerminalIntent.nodeId.filter(nodeId::equals).isPresent();
	}

	public static synchronized boolean terminalIntentPending(final Intent intent) {
		return pendingTerminalIntent != null && pendingTerminalIntent.intent == intent;
	}

	public static synchronized boolean terminalSyncPending() {
		return pagePurpose == PagePurpose.NORMAL
			&& (pageStatus == PageStatus.LOADING || pendingTerminalIntent != null);
	}

	/**
	 * Returns one server-authored, non-error terminal closure exactly once to the live-screen
	 * supervisor. The active page remains pinned until its hide motion has completed.
	 */
	public static synchronized Optional<TerminalRelease> consumeTerminalRelease() {
		if (terminalRelease == null || terminalReleaseConsumed) {
			return Optional.empty();
		}
		terminalReleaseConsumed = true;
		return Optional.of(terminalRelease);
	}

	public static synchronized boolean terminalReleasePending() {
		return terminalRelease != null;
	}

	public static synchronized void completeTerminalRelease() {
		if (terminalRelease == null) {
			return;
		}
		releaseActive(false);
		pagePurpose = PagePurpose.PREVIEW;
		pageStatus = PageStatus.IDLE;
		pageMessage = "";
		terminalRelease = null;
		terminalReleaseConsumed = false;
		fatalFlowBlock = null;
		clearFlowActionState();
		clearTerminalOpenPending();
		clearTerminalIntentState();
		revision++;
	}

	public static synchronized boolean acceptForcedRelease(
		final ForcedPageReleaseS2CPayload payload
	) {
		if (
			!NetworkProtocol.isCompatible(payload.protocolVersion())
				|| activePage == null
				|| activePage.purpose != PagePurpose.FORCED_FLOW
				|| payload.stateRevision() < activePage.stateRevision
		) {
			return false;
		}
		FlowContext flow = activePage.flowContext.orElseThrow();
		if (
			!flow.flowInstanceId().equals(payload.flowInstanceId())
				|| !activePage.instanceId.equals(payload.pageInstanceId())
		) {
			return false;
		}
		releaseActive(false);
		pagePurpose = PagePurpose.PREVIEW;
		pageStatus = PageStatus.IDLE;
		pageMessage = "";
		fatalFlowBlock = null;
		clearFlowActionState();
		clearTerminalOpenPending();
		clearTerminalIntentState();
		revision++;
		return true;
	}

	public static synchronized boolean closeActivePage() {
		if (forcedPageActive()) {
			return false;
		}
		if (terminalRelease != null) {
			completeTerminalRelease();
			return true;
		}
		releaseActive(true);
		pagePurpose = PagePurpose.PREVIEW;
		pageStatus = PageStatus.IDLE;
		pageMessage = "";
		clearFlowActionState();
		clearTerminalOpenPending();
		clearTerminalIntentState();
		revision++;
		return true;
	}

	public static synchronized Catalog catalog() {
		return catalog;
	}

	public static synchronized PageStatus pageStatus() {
		return pageStatus;
	}

	public static synchronized String pageMessage() {
		return pageMessage;
	}

	public static synchronized Optional<ActivePage> activePage() {
		return Optional.ofNullable(activePage);
	}

	public static synchronized PagePurpose pagePurpose() {
		return pagePurpose;
	}

	public static synchronized boolean forcedPageActive() {
		return pagePurpose == PagePurpose.FORCED_FLOW && pageStatus != PageStatus.IDLE;
	}

	public static synchronized long revision() {
		return revision;
	}

	public static synchronized int cacheEntries() {
		return CACHE.size();
	}

	public static synchronized int cacheBytes() {
		return CACHE.byteSize();
	}

	private static void acceptExclusiveMutationBundle(final ActivePage page) {
		if (
			pendingExclusiveChoiceMutation == null
				|| !pendingExclusiveChoiceMutation.pageInstanceId.equals(page.instanceId)
				|| page.flowContext.isEmpty()
				|| page.flowContext.orElseThrow().memberRevision()
					<= pendingExclusiveChoiceMutation.memberRevision
		) {
			return;
		}
		PendingExclusiveChoiceMutation accepted =
			pendingExclusiveChoiceMutation.withAuthoritativeBundleAccepted();
		pendingExclusiveChoiceMutation = accepted.resultAccepted ? null : accepted;
	}

	private static void sendResourceReport(final ActivePage page) {
		if (!ClientPlayNetworking.canSend(ResourceReportC2SPayload.TYPE)) {
			return;
		}
		List<ResourceReportC2SPayload.Entry> entries = page.resources.details()
			.stream()
			.map(
				diagnostic -> new ResourceReportC2SPayload.Entry(
					diagnostic.serializedType(),
					diagnostic.id(),
					diagnostic.pointer()
				)
			)
			.toList();
		ClientPlayNetworking.send(
			new ResourceReportC2SPayload(
				page.instanceId,
				page.generation,
				page.page.id(),
				page.resources.totalMissing(),
				entries,
				page.resources.requiredMissing(),
				false,
				Optional.empty()
			)
		);
	}

	private static void sendRenderReport(
		final UUID instanceId,
		final long generation,
		final Identifier pageId,
		final boolean ready,
		final Optional<String> error
	) {
		if (!ClientPlayNetworking.canSend(ResourceReportC2SPayload.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(
			new ResourceReportC2SPayload(
				instanceId,
				generation,
				pageId,
				0,
				List.of(),
				false,
				ready,
				error
			)
		);
	}

	private static boolean replacementAllowed(final PageBundleS2CPayload payload) {
		if (
			pagePurpose == PagePurpose.NORMAL
				&& payload.purpose() == PagePurpose.PREVIEW
		) {
			return false;
		}
		if (!forcedPageActive()) {
			return true;
		}
		if (payload.purpose() != PagePurpose.FORCED_FLOW) {
			return false;
		}
		if (activePage == null) {
			return true;
		}
		return activePage.flowContext
			.map(FlowContext::flowInstanceId)
			.equals(payload.flowContext().map(FlowContext::flowInstanceId));
	}

	private static void releaseActive(final boolean notifyServer) {
		if (activePage == null) {
			return;
		}
		if (
			notifyServer
				&& activePage.purpose != PagePurpose.FORCED_FLOW
				&& ClientPlayNetworking.canSend(PageCloseC2SPayload.TYPE)
		) {
			ClientPlayNetworking.send(new PageCloseC2SPayload(activePage.instanceId));
		}
		CACHE.unpin(activePage.cacheKey);
		activePage = null;
	}

	private static void reset(final boolean notifyServer) {
		releaseActive(notifyServer);
		CACHE.clear();
		catalog = Catalog.idle();
		pageStatus = PageStatus.IDLE;
		pageMessage = "";
		pagePurpose = PagePurpose.PREVIEW;
		fatalFlowBlock = null;
		terminalRelease = null;
		terminalReleaseConsumed = false;
		clearFlowActionState();
		clearTerminalOpenPending();
		clearTerminalIntentState();
		nextTerminalOpenRequestSequence = 0L;
		revision++;
	}

	private static void clearFlowActionState() {
		nextFlowRequestSequence = 0L;
		pendingFlowAction = null;
		flowActionResult = null;
		pendingExclusiveChoiceMutation = null;
		exclusiveChoiceMutationResult = null;
	}

	private static void clearTerminalIntentState() {
		nextTerminalRequestSequence = 0L;
		pendingTerminalIntent = null;
		terminalIntentResult = null;
	}

	private static void clearTerminalOpenPending() {
		pendingTerminalOpenRequestSequence = null;
	}

	private static String decodeUtf8(final byte[] bytes, final String document) {
		try {
			return StandardCharsets.UTF_8
				.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		} catch (java.nio.charset.CharacterCodingException error) {
			throw new IllegalArgumentException(document + " document is not valid UTF-8", error);
		}
	}

	private static String summarizeIssues(
		final ParseResult<PageDefinition> page,
		final ParseResult<ThemeDefinition> theme
	) {
		return java.util.stream.Stream.concat(page.issues().stream(), theme.issues().stream())
			.limit(3)
			.map(issue -> issue.code() + " " + issue.pointer())
			.reduce((left, right) -> left + "; " + right)
			.orElse("unknown parser error");
	}

	private static String truncate(final String value, final int maximumLength) {
		return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
	}

	public enum CatalogStatus {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	public enum PageStatus {
		IDLE,
		LOADING,
		READY,
		RESOURCE_ERROR,
		ERROR
	}

	public record Catalog(
		CatalogStatus status,
		long generation,
		String message,
		List<PreviewCatalogS2CPayload.Entry> entries
	) {
		public Catalog {
			entries = List.copyOf(entries);
		}

		private static Catalog idle() {
			return new Catalog(CatalogStatus.IDLE, 0L, "", List.of());
		}
	}

	public record ActivePage(
		java.util.UUID instanceId,
		long generation,
		long stateRevision,
		PageDefinition page,
		ThemeDefinition theme,
		Map<Identifier, PageBundleS2CPayload.FieldSchema> fields,
		Key cacheKey,
		Report resources,
		PagePurpose purpose,
		Optional<FlowContext> flowContext,
		Optional<TerminalContext> terminalContext,
		BindingContext serverContext
	) {
		public ActivePage {
			fields = Map.copyOf(fields);
			purpose = Objects.requireNonNull(purpose, "purpose");
			flowContext = Objects.requireNonNull(flowContext, "flowContext");
			terminalContext = Objects.requireNonNull(terminalContext, "terminalContext");
			serverContext = Objects.requireNonNull(serverContext, "serverContext");
		}

		private ActivePage withResources(final Report updated) {
			return new ActivePage(
				this.instanceId,
				this.generation,
				this.stateRevision,
				this.page,
				this.theme,
				this.fields,
				this.cacheKey,
				updated,
				this.purpose,
				this.flowContext,
				this.terminalContext,
				this.serverContext
			);
		}

		private ActivePage withServerContext(
			final long updatedStateRevision,
			final BindingContext updatedServerContext
		) {
			return new ActivePage(
				this.instanceId,
				this.generation,
				updatedStateRevision,
				this.page,
				this.theme,
				this.fields,
				this.cacheKey,
				this.resources,
				this.purpose,
				this.flowContext,
				this.terminalContext,
				updatedServerContext
			);
		}

		public List<Diagnostic> resourceDiagnostics() {
			return this.resources.details();
		}
	}

	public record FlowSubmission(boolean sent, long requestSequence, String message) {
		private static FlowSubmission sent(final long sequence) {
			return new FlowSubmission(true, sequence, "");
		}

		private static FlowSubmission rejected(final String message) {
			return new FlowSubmission(false, -1L, message);
		}
	}

	public record ExclusiveMutationSubmission(
		boolean sent,
		long requestSequence,
		String message
	) {
		private static ExclusiveMutationSubmission sent(final long sequence) {
			return new ExclusiveMutationSubmission(true, sequence, "");
		}

		private static ExclusiveMutationSubmission rejected(final String message) {
			return new ExclusiveMutationSubmission(false, -1L, message);
		}
	}

	public record TerminalSubmission(boolean sent, long requestSequence, String message) {
		private static TerminalSubmission sent(final long sequence) {
			return new TerminalSubmission(true, sequence, "");
		}

		private static TerminalSubmission rejected(final String message) {
			return new TerminalSubmission(false, -1L, message);
		}
	}

	public record TerminalRelease(String reason, String message) {
		public TerminalRelease {
			reason = Objects.requireNonNull(reason, "reason");
			message = Objects.requireNonNull(message, "message");
			if (reason.isBlank() || message.isBlank()) {
				throw new IllegalArgumentException("terminal release must be visible and attributable");
			}
		}
	}

	private record PendingFlowAction(long requestSequence, java.util.UUID pageInstanceId) {
	}

	private record PendingExclusiveChoiceMutation(
		long requestSequence,
		java.util.UUID pageInstanceId,
		Identifier fieldId,
		Operation operation,
		Optional<String> value,
		long memberRevision,
		boolean resultAccepted,
		boolean authoritativeBundleAccepted
	) {
		private PendingExclusiveChoiceMutation {
			value = Objects.requireNonNull(value, "value");
		}

		private PendingExclusiveChoiceMutation withResultAccepted() {
			return new PendingExclusiveChoiceMutation(
				this.requestSequence,
				this.pageInstanceId,
				this.fieldId,
				this.operation,
				this.value,
				this.memberRevision,
				true,
				this.authoritativeBundleAccepted
			);
		}

		private PendingExclusiveChoiceMutation withAuthoritativeBundleAccepted() {
			return new PendingExclusiveChoiceMutation(
				this.requestSequence,
				this.pageInstanceId,
				this.fieldId,
				this.operation,
				this.value,
				this.memberRevision,
				this.resultAccepted,
				true
			);
		}
	}

	private record PendingTerminalIntent(
		long requestSequence,
		UUID pageInstanceId,
		Intent intent,
		Optional<String> nodeId,
		Optional<Identifier> actionId,
		Optional<String> historyRecordKey
	) {
		private PendingTerminalIntent {
			pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
			intent = Objects.requireNonNull(intent, "intent");
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			actionId = Objects.requireNonNull(actionId, "actionId");
			historyRecordKey = Objects.requireNonNull(
				historyRecordKey,
				"historyRecordKey"
			);
		}
	}
}
