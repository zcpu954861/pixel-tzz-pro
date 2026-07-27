package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight;
import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight.Diagnostic;
import io.github.zcpu954861.pixeltzzpro.client.ui.ClientResourcePreflight.Report;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageCloseC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload.CachedDocuments;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache.Key;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache.ParsedDocuments;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

/**
 * Client projection of the administrator page-preview protocol.
 */
public final class ClientPageState {
	private static final PageDocumentCache CACHE = new PageDocumentCache();

	private static Catalog catalog = Catalog.idle();
	private static PageStatus pageStatus = PageStatus.IDLE;
	private static String pageMessage = "";
	private static ActivePage activePage;
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
		if (!ClientPlayNetworking.canSend(PreviewPageRequestC2SPayload.TYPE)) {
			pageStatus = PageStatus.ERROR;
			pageMessage = "页面同步协议当前不可用";
			revision++;
			return;
		}
		releaseActive(true);
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

	public static synchronized void acceptPageBundle(final PageBundleS2CPayload payload) {
		releaseActive(false);
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
				resources
			);
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
		}
		revision++;
	}

	public static synchronized void retryResourcePreflight() {
		if (activePage == null) {
			return;
		}
		Report report = ClientResourcePreflight.check(activePage.page, activePage.theme);
		activePage = activePage.withResources(report);
		pageStatus = report.requiredMissing() ? PageStatus.RESOURCE_ERROR : PageStatus.READY;
		pageMessage = report.requiredMissing() ? "缺少页面必需资源，已进入安全错误页" : "";
		sendResourceReport(activePage);
		revision++;
	}

	public static synchronized void closeActivePage() {
		releaseActive(true);
		pageStatus = PageStatus.IDLE;
		pageMessage = "";
		revision++;
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

	public static synchronized long revision() {
		return revision;
	}

	public static synchronized int cacheEntries() {
		return CACHE.size();
	}

	public static synchronized int cacheBytes() {
		return CACHE.byteSize();
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
				entries
			)
		);
	}

	private static void releaseActive(final boolean notifyServer) {
		if (activePage == null) {
			return;
		}
		if (notifyServer && ClientPlayNetworking.canSend(PageCloseC2SPayload.TYPE)) {
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
		revision++;
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
		Report resources
	) {
		public ActivePage {
			fields = Map.copyOf(fields);
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
				updated
			);
		}

		public List<Diagnostic> resourceDiagnostics() {
			return this.resources.details();
		}
	}
}
