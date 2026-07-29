package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.BuiltInUiResources;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BoundText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AlignSelf;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Anchor;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ComparisonCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConcatenatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.CrossAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Direction;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ExistsCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Insets;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LiteralValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MainAlign;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NotCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeMode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeSpec;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StaticText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleState;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextTemplate;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TranslatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewCatalogS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PreviewPageRequestC2SPayload.CachedDocuments;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.DiagnosticCode;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.LayoutItem;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.LayoutResult;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.PlacedNode;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContextDocument;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime.Evaluation;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ChoiceCardGrid;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache.CachedPage;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageDocumentCache.Key;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.PageRenderProjection;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TextLineWrapper;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime.Transform;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiStyleRuntime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Small runnable checks for client-neutral parts of the 2B page framework.
 */
public final class PageFrameworkSelfCheck {
	private PageFrameworkSelfCheck() {
	}

	public static void main(final String[] arguments) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkPageCache();
		checkBindingContext();
		checkBindingContextDocument();
		checkBindingRuntime();
		checkBindingDiagnostics();
		checkStyleRuntime();
		checkTextLineWrapper();
		checkPayloadBounds();
		checkLayoutEngine();
		checkViewportFit();
		checkChoiceCardGrid();
		checkPageRenderProjection();
		System.out.println("PAGE_FRAMEWORK_SELF_CHECK=PASS");
	}

	private static void checkChoiceCardGrid() {
		check(
			ChoiceCardGrid.columns("exclusive_choice", 3, 360) == 3,
			"three exclusive choices must share one row when three readable cards fit"
		);
		check(
			ChoiceCardGrid.controlHeight("exclusive_choice", 3, 360) == 48,
			"a one-row exclusive choice grid must not create vertical overflow"
		);
		check(
			ChoiceCardGrid.columns("exclusive_choice", 3, 359) == 2
				&& ChoiceCardGrid.columns("exclusive_choice", 3, 239) == 1,
			"exclusive choices must retain two-column and one-column narrow fallbacks"
		);
		check(
			ChoiceCardGrid.controlHeight("exclusive_choice", 4, 360) > 48,
			"additional exclusive choices must overflow only the bounded card viewport"
		);
	}

	private static void checkPageRenderProjection() {
		PageRenderProjection<String, String> ready = new PageRenderProjection<>(
			"page-bundle-1",
			"ready",
			""
		);
		PageRenderProjection<String, String> pendingOnly = new PageRenderProjection<>(
			"page-bundle-1",
			"ready",
			""
		);
		check(
			!ready.differsFrom(pendingOnly),
			"transport pending must not rebuild an unchanged page projection"
		);
		check(
			ready.differsFrom(new PageRenderProjection<>("page-bundle-2", "ready", "")),
			"a new page bundle or exclusive occupancy projection must rebuild"
		);
		check(
			ready.differsFrom(new PageRenderProjection<>("page-bundle-1", "error", "blocked")),
			"a safety status or message change must rebuild"
		);
	}

	private static void checkViewportFit() {
		check(
			Math.abs(UiLayoutEngine.fitScale(620, 480, 620, 360) - 0.75) < 0.0001,
			"oversized live pages must scale to keep their complete action area visible"
		);
		check(
			UiLayoutEngine.fitScale(400, 240, 620, 360) == 1.0,
			"pages already inside the viewport must not be enlarged"
		);
	}

	private static void checkPayloadBounds() {
		List<String> mutableOptions = new ArrayList<>(List.of("safe", "balanced"));
		PageBundleS2CPayload.FieldSchema field = new PageBundleS2CPayload.FieldSchema(
			id("route"),
			"single_choice",
			"Route",
			"Preview route",
			true,
			Optional.of("\"safe\""),
			OptionalLong.empty(),
			OptionalLong.empty(),
			OptionalInt.empty(),
			OptionalInt.empty(),
			mutableOptions,
			OptionalInt.empty(),
			OptionalInt.empty()
		);
		mutableOptions.add("late");
		check(field.options().size() == 2, "field schemas must copy option lists");
		expectUnsupported(() -> field.options().add("mutated"), "field schema options must be immutable");

		PageBundleS2CPayload.ExclusiveOptionState hiddenOccupant =
			new PageBundleS2CPayload.ExclusiveOptionState(
				"north",
				"{\"text\":\"北侧出生点\"}",
				"{\"text\":\"靠近广场入口\"}",
				Optional.of(id("textures/gui/spawn/north")),
				Optional.of(id("preview/spawn/north")),
				"held_by_other",
				Optional.empty(),
				Optional.empty(),
				false
			);
		PageBundleS2CPayload.FieldSchema exclusiveField =
			new PageBundleS2CPayload.FieldSchema(
				id("spawn"),
				"exclusive_choice",
				"出生点",
				"每个出生点只能由一名玩家选择",
				true,
				Optional.empty(),
				OptionalLong.empty(),
				OptionalLong.empty(),
				OptionalInt.empty(),
				OptionalInt.empty(),
				List.of("north"),
				OptionalInt.empty(),
				OptionalInt.empty(),
				List.of(hiddenOccupant)
			);
		check(
			exclusiveField.exclusiveOptions().getFirst().occupantId().isEmpty(),
			"hidden exclusive occupants must not require identity disclosure"
		);
		expectUnsupported(
			() -> exclusiveField.exclusiveOptions().clear(),
			"exclusive option metadata must be immutable"
		);
		expectIllegalArgument(
			() -> new PageBundleS2CPayload.FieldSchema(
				id("spawn"),
				"exclusive_choice",
				"出生点",
				"",
				true,
				Optional.empty(),
				OptionalLong.empty(),
				OptionalLong.empty(),
				OptionalInt.empty(),
				OptionalInt.empty(),
				List.of("south"),
				OptionalInt.empty(),
				OptionalInt.empty(),
				List.of(hiddenOccupant)
			),
			"exclusive metadata must match stable option values"
		);
		expectIllegalArgument(
			() -> new PageBundleS2CPayload.ExclusiveOptionState(
				"north",
				"{\"text\":\"北侧出生点\"}",
				"",
				Optional.empty(),
				Optional.empty(),
				"available",
				Optional.of(UUID.randomUUID()),
				Optional.of("Unexpected"),
				false
			),
			"available exclusive options must reject occupant leakage"
		);

		byte[] hash = new byte[PageBundleS2CPayload.HASH_LENGTH];
		byte[] page = bytes("{}");
		byte[] theme = bytes("{}");
		List<PageBundleS2CPayload.FieldSchema> fields = new ArrayList<>(List.of(field));
		PageBundleS2CPayload bundle = new PageBundleS2CPayload(
			UUID.randomUUID(),
			1L,
			2L,
			id("page/preview"),
			id("theme/default"),
			hash,
			page,
			hash,
			theme,
			fields
		);
		hash[0] = 1;
		page[0] = 0;
		fields.clear();
		check(bundle.pageHash()[0] == 0, "page bundle must copy hash bytes");
		check(bundle.pageJson()[0] == '{', "page bundle must copy document bytes");
		byte[] returnedPage = bundle.pageJson();
		returnedPage[0] = 0;
		check(bundle.pageJson()[0] == '{', "page bundle accessors must copy document bytes");
		check(bundle.fields().size() == 1, "page bundle must copy field schemas");
		check(!bundle.documentsOmitted(), "full page bundles must expose present documents");
		expectUnsupported(bundle.fields()::clear, "page bundle fields must be immutable");

		PageBundleS2CPayload omitted = new PageBundleS2CPayload(
			UUID.randomUUID(),
			1L,
			2L,
			id("page/preview"),
			id("theme/default"),
			new byte[PageBundleS2CPayload.HASH_LENGTH],
			new byte[0],
			new byte[PageBundleS2CPayload.HASH_LENGTH],
			new byte[0],
			List.of(field)
		);
		check(omitted.documentsOmitted(), "metadata-only page bundles must expose omitted documents");
		expectIllegalArgument(
			() -> new PageBundleS2CPayload(
				UUID.randomUUID(),
				1L,
				2L,
				id("page/preview"),
				id("theme/default"),
				new byte[PageBundleS2CPayload.HASH_LENGTH],
				bytes("{}"),
				new byte[PageBundleS2CPayload.HASH_LENGTH],
				new byte[0],
				List.of()
			),
			"page bundles cannot omit only the theme document"
		);
		expectIllegalArgument(
			() -> new PageBundleS2CPayload(
				UUID.randomUUID(),
				1L,
				2L,
				id("page/preview"),
				id("theme/default"),
				new byte[PageBundleS2CPayload.HASH_LENGTH],
				new byte[0],
				new byte[PageBundleS2CPayload.HASH_LENGTH],
				bytes("{}"),
				List.of()
			),
			"page bundles cannot omit only the page document"
		);

		PreviewPageRequestC2SPayload uncachedRequest = new PreviewPageRequestC2SPayload(
			id("page/preview")
		);
		check(
			uncachedRequest.cachedDocuments().isEmpty(),
			"single-argument page requests must not claim a cache hit"
		);
		byte[] cachedPageHash = new byte[PreviewPageRequestC2SPayload.HASH_LENGTH];
		byte[] cachedThemeHash = new byte[PreviewPageRequestC2SPayload.HASH_LENGTH];
		cachedPageHash[0] = 7;
		cachedThemeHash[0] = 9;
		CachedDocuments cachedDocuments = new CachedDocuments(
			3L,
			id("theme/default"),
			cachedPageHash,
			cachedThemeHash
		);
		PreviewPageRequestC2SPayload cachedRequest = new PreviewPageRequestC2SPayload(
			id("page/preview"),
			Optional.of(cachedDocuments)
		);
		cachedPageHash[0] = 0;
		cachedThemeHash[0] = 0;
		check(
			cachedRequest.cachedDocuments().orElseThrow().pageHash()[0] == 7
				&& cachedRequest.cachedDocuments().orElseThrow().themeHash()[0] == 9,
			"cached page summaries must copy incoming hashes"
		);
		byte[] returnedCachedHash = cachedDocuments.pageHash();
		returnedCachedHash[0] = 0;
		check(cachedDocuments.pageHash()[0] == 7, "cached page summaries must copy returned hashes");
		expectIllegalArgument(
			() -> new CachedDocuments(
				-1L,
				id("theme/default"),
				new byte[PreviewPageRequestC2SPayload.HASH_LENGTH],
				new byte[PreviewPageRequestC2SPayload.HASH_LENGTH]
			),
			"cached page summaries must reject negative generations"
		);
		expectIllegalArgument(
			() -> new CachedDocuments(
				1L,
				id("theme/default"),
				new byte[PreviewPageRequestC2SPayload.HASH_LENGTH - 1],
				new byte[PreviewPageRequestC2SPayload.HASH_LENGTH]
			),
			"cached page summaries must require a full page hash"
		);
		expectIllegalArgument(
			() -> new CachedDocuments(
				1L,
				id("theme/default"),
				new byte[PreviewPageRequestC2SPayload.HASH_LENGTH],
				new byte[PreviewPageRequestC2SPayload.HASH_LENGTH - 1]
			),
			"cached page summaries must require a full theme hash"
		);

		List<PreviewCatalogS2CPayload.Entry> excessiveCatalog = new ArrayList<>();
		for (int index = 0; index <= PreviewCatalogS2CPayload.MAX_ENTRIES; index++) {
			excessiveCatalog.add(
				new PreviewCatalogS2CPayload.Entry(id("page/" + index), id("game"), "Page")
			);
		}
		expectIllegalArgument(
			() -> new PreviewCatalogS2CPayload(4, 1L, "ready", "", excessiveCatalog),
			"catalog payloads must reject excessive entries"
		);
		expectIllegalArgument(
			() -> new ResourceReportC2SPayload(
				UUID.randomUUID(),
				1L,
				id("page/preview"),
				0,
				List.of(new ResourceReportC2SPayload.Entry("texture", "test:missing", "/root"))
			),
			"resource report details cannot exceed the missing total"
		);
		expectIllegalArgument(
			() -> new ResourceReportC2SPayload(
				UUID.randomUUID(),
				1L,
				id("page/preview"),
				ResourceReportC2SPayload.MAX_TOTAL_MISSING + 1,
				List.of()
			),
			"resource report totals must have a protocol bound"
		);
	}

	private static void checkTextLineWrapper() {
		String chinese = "猎人由主持人在控制台中指定";
		List<String> chineseLines = TextLineWrapper.wrap(
			chinese,
			5,
			value -> value.codePointCount(0, value.length())
		);
		check(chineseLines.size() > 1, "continuous Chinese text must wrap");
		check(
			chineseLines.stream().allMatch(
				line -> line.codePointCount(0, line.length()) <= 5
			),
			"wrapped Chinese lines must respect the measured width"
		);
		check(
			String.join("", chineseLines).equals(chinese),
			"wrapping must preserve continuous Chinese text"
		);

		String emoji = "A😀B";
		List<String> emojiLines = TextLineWrapper.wrap(
			emoji,
			1,
			value -> value.codePointCount(0, value.length())
		);
		check(String.join("", emojiLines).equals(emoji), "wrapping must preserve surrogate pairs");
		check(
			emojiLines.stream().noneMatch(
				line -> line.length() == 1 && Character.isSurrogate(line.charAt(0))
			),
			"wrapping must not split a surrogate pair"
		);
	}

	private static void checkPageCache() {
		PageDocumentCache cache = new PageDocumentCache();
		byte[] theme = bytes("{\"format_version\":1,\"tokens\":{},\"styles\":{},\"motions\":{},\"sound_cues\":{}}");
		byte[] themeHash = sha256(theme);
		Key first = null;
		Key second = null;
		for (int index = 0; index < PageDocumentCache.MAX_ENTRIES; index++) {
			byte[] page = bytes("{\"format_version\":1,\"page\":" + index + "}");
			Key key = cache.store(
				1L,
				id("page/" + index),
				sha256(page),
				page,
				id("theme/default"),
				themeHash,
				theme
			);
			if (index == 0) {
				first = key;
			} else if (index == 1) {
				second = key;
			}
		}
		check(cache.size() == PageDocumentCache.MAX_ENTRIES, "cache must reach its entry limit");
		CachedPage firstPage = cache.find(1L, id("page/0")).orElseThrow();
		check(cache.find(2L, id("page/0")).isEmpty(), "cache lookup must include generation");
		check(cache.find(1L, id("page/missing")).isEmpty(), "cache lookup must include page id");
		check(
			firstPage.matchesEnvelope(
				1L,
				id("page/0"),
				id("theme/default"),
				firstPage.pageHash(),
				firstPage.themeHash()
			),
			"matching cache envelopes must be accepted"
		);
		byte[] wrongPageEnvelopeHash = firstPage.pageHash();
		wrongPageEnvelopeHash[0] ^= 1;
		byte[] wrongThemeEnvelopeHash = firstPage.themeHash();
		wrongThemeEnvelopeHash[0] ^= 1;
		check(
			!firstPage.matchesEnvelope(
				1L,
				id("page/0"),
				id("theme/default"),
				wrongPageEnvelopeHash,
				firstPage.themeHash()
			),
			"cache envelopes must reject a wrong page hash"
		);
		check(
			!firstPage.matchesEnvelope(
				1L,
				id("page/0"),
				id("theme/default"),
				firstPage.pageHash(),
				wrongThemeEnvelopeHash
			),
			"cache envelopes must reject a wrong theme hash"
		);
		check(
			!firstPage.matchesEnvelope(
				1L,
				id("page/0"),
				id("theme/other"),
				firstPage.pageHash(),
				firstPage.themeHash()
			),
			"cache envelopes must reject a wrong theme id"
		);

		byte[] overflowPage = bytes("{\"format_version\":1,\"page\":64}");
		cache.store(
			1L,
			id("page/64"),
			sha256(overflowPage),
			overflowPage,
			id("theme/default"),
			themeHash,
			theme
		);
		check(cache.size() == PageDocumentCache.MAX_ENTRIES, "cache must evict at its entry limit");
		check(cache.find(1L, id("page/0")).isPresent(), "recently accessed entry must remain in the LRU");
		check(cache.find(1L, id("page/1")).isEmpty(), "least recently used entry must be evicted");

		check(cache.pin(first), "existing page must be pinnable");
		cache.removeUnpinnedBefore(2L);
		check(cache.size() == 1, "old unpinned generations must be released");
		check(cache.get(first).isPresent(), "active pinned generations must remain readable");
		cache.unpin(first);
		cache.removeUnpinnedBefore(2L);
		check(cache.size() == 0, "unpinning must make an old generation reclaimable");

		byte[] page = bytes("{\"format_version\":1}");
		byte[] wrongHash = sha256(bytes("different"));
		expectIllegalArgument(
			() -> cache.store(
				2L,
				id("page/mismatch"),
				wrongHash,
				page,
				id("theme/default"),
				themeHash,
				theme
			),
			"mismatched page hashes must be rejected"
		);

		byte[] mutablePage = bytes("{\"format_version\":1,\"mutable\":false}");
		byte[] mutableHash = sha256(mutablePage);
		Key defensive = cache.store(
			2L,
			id("page/defensive"),
			mutableHash,
			mutablePage,
			id("theme/default"),
			themeHash,
			theme
		);
		mutablePage[0] = 0;
		byte[] returned = cache.get(defensive).orElseThrow().pageDocument();
		check(returned[0] == '{', "cache must copy incoming document bytes");
		returned[0] = 0;
		check(
			cache.get(defensive).orElseThrow().pageDocument()[0] == '{',
			"cache accessors must not expose mutable document bytes"
		);

		Identifier parsedPageId = id("page/parsed");
		Identifier parsedThemeId = id("theme/parsed");
		PageDefinition parsedPage = UiDefinitionParser.parsePage(
			parsedPageId,
			"""
			{
			  "format_version": 1,
			  "game": "test:game",
			  "title": {"text": "Cached page"},
			  "theme": "test:theme/parsed",
			  "root": {"type": "text", "text": {"text": "Cached"}}
			}
			"""
		).value().orElseThrow();
		ThemeDefinition parsedTheme = UiDefinitionParser.parseTheme(
			parsedThemeId,
			"""
			{
			  "format_version": 1,
			  "tokens": {"colors": {}, "spacing": {}, "fonts": {}},
			  "styles": {},
			  "motions": {},
			  "sound_cues": {}
			}
			"""
		).value().orElseThrow();
		byte[] parsedPageDocument = bytes(parsedPage.canonicalDocument());
		byte[] parsedThemeDocument = bytes(parsedTheme.canonicalDocument());
		PageDocumentCache parsedCache = new PageDocumentCache();
		Key parsedKey = parsedCache.store(
			4L,
			parsedPageId,
			sha256(parsedPageDocument),
			parsedPageDocument,
			parsedThemeId,
			sha256(parsedThemeDocument),
			parsedThemeDocument
		);
		check(parsedCache.parsed(parsedKey).isEmpty(), "new cache entries must start without parsed documents");
		var attached = parsedCache.attachParsed(parsedKey, parsedPage, parsedTheme);
		check(
			attached.page() == parsedPage && attached.theme() == parsedTheme,
			"cache must retain the validated parsed documents"
		);
		check(
			parsedCache.parsed(parsedKey).orElseThrow().page() == parsedPage,
			"parsed cache lookup must reuse the validated page object"
		);
		expectIllegalState(
			() -> parsedCache.attachParsed(
				new Key(5L, parsedPageId, parsedPage.sha256()),
				parsedPage,
				parsedTheme
			),
			"parsed documents cannot attach to a missing cache entry"
		);

		PageDefinition wrongPageId = UiDefinitionParser.parsePage(
			id("page/other"),
			parsedPage.canonicalDocument()
		).value().orElseThrow();
		expectIllegalArgument(
			() -> parsedCache.attachParsed(parsedKey, wrongPageId, parsedTheme),
			"parsed cache attachment must verify page id"
		);
		ThemeDefinition wrongThemeId = UiDefinitionParser.parseTheme(
			id("theme/other"),
			parsedTheme.canonicalDocument()
		).value().orElseThrow();
		expectIllegalArgument(
			() -> parsedCache.attachParsed(parsedKey, parsedPage, wrongThemeId),
			"parsed cache attachment must verify theme id"
		);
		PageDefinition changedPage = UiDefinitionParser.parsePage(
			parsedPageId,
			"""
			{
			  "format_version": 1,
			  "game": "test:game",
			  "title": {"text": "Changed page"},
			  "theme": "test:theme/parsed",
			  "root": {"type": "text", "text": {"text": "Changed"}}
			}
			"""
		).value().orElseThrow();
		expectIllegalArgument(
			() -> parsedCache.attachParsed(parsedKey, changedPage, parsedTheme),
			"parsed cache attachment must verify canonical document hashes"
		);
		check(
			parsedCache.parsed(parsedKey).orElseThrow().page() == parsedPage,
			"failed parsed attachments must not replace the valid cached objects"
		);
	}

	private static void checkBindingContext() {
		Identifier playerField = id("profile/route");
		Identifier flowField = id("temporary/choice");
		JsonObject mutableItem = new JsonObject();
		mutableItem.addProperty("name", "Hunter 01");
		JsonArray players = new JsonArray();
		players.add(mutableItem);

		BindingContext context = BindingContext.builder()
			.viewer("name", string("Alex"))
			.session("connected", bool(true))
			.session("players", players)
			.flow("completed", integer(2))
			.field(playerField, string("safe"))
			.flowField(flowField, integer(9))
			.item("name", mutableItem)
			.ui("route", string("balanced"))
			.build();

		mutableItem.addProperty("name", "mutated");
		players.add("late mutation");
		check(text(context, "viewer.name").equals("Alex"), "viewer binding failed");
		check(context.resolve("session.connected").orElseThrow().getAsBoolean(), "session binding failed");
		check(context.resolve("flow.completed").orElseThrow().getAsLong() == 2L, "flow binding failed");
		check(
			text(context, "fields/test:profile/route").equals("safe"),
			"field identifiers containing a path slash must remain intact"
		);
		check(
			context.resolve("flow/fields/test:temporary/choice").orElseThrow().getAsLong() == 9L,
			"flow-field identifiers containing a path slash must remain intact"
		);
		check(
			context.resolve("session.players").orElseThrow().getAsJsonArray().size() == 1,
			"context construction must deep-copy collection values"
		);
		check(
			context.resolve("item.name").orElseThrow().getAsJsonObject().get("name").getAsString().equals("Hunter 01"),
			"item scope must be isolated from caller mutations"
		);
		JsonObject returnedItem = context.resolve("item.name").orElseThrow().getAsJsonObject();
		returnedItem.addProperty("name", "returned mutation");
		check(
			context.resolve("item.name").orElseThrow().getAsJsonObject().get("name").getAsString().equals("Hunter 01"),
			"context accessors must not expose mutable Gson values"
		);
		check(text(context, "ui/route/value").equals("balanced"), "UI field binding failed");
		JsonObject mutableUi = new JsonObject();
		mutableUi.addProperty("choice", "safe");
		BindingContext withUiValues = context.withUiValues(Map.of("panel", mutableUi, "count", integer(3)));
		mutableUi.addProperty("choice", "mutated");
		check(
			withUiValues.resolve("ui/panel/value")
				.orElseThrow()
				.getAsJsonObject()
				.get("choice")
				.getAsString()
				.equals("safe"),
			"bulk UI values must be copied into one derived context"
		);
		check(
			withUiValues.resolve("ui/count/value").orElseThrow().getAsInt() == 3,
			"bulk UI values must all be visible"
		);
		check(context.resolve("viewer.missing").isEmpty(), "missing bindings must stay absent");
		check(context.resolve("ui/route/value/extra").isEmpty(), "invalid UI binding paths must fail closed");
	}

	private static void checkBindingContextDocument() {
		JsonObject document = new JsonObject();
		JsonObject viewer = new JsonObject();
		viewer.addProperty("name", "Alex");
		JsonObject session = new JsonObject();
		session.addProperty("revision", 9L);
		JsonObject flow = new JsonObject();
		flow.addProperty("completed", 1);
		JsonObject fields = new JsonObject();
		fields.addProperty("test:profile/route", "safe");
		JsonObject flowFields = new JsonObject();
		flowFields.addProperty("test:temporary/choice", 3);
		document.add("viewer", viewer);
		document.add("session", session);
		document.add("flow", flow);
		document.add("fields", fields);
		document.add("flow_fields", flowFields);

		BindingContext decoded = BindingContextDocument.decode(
			BindingContextDocument.encode(document)
		);
		check(text(decoded, "viewer.name").equals("Alex"), "binding document viewer root failed");
		check(
			decoded.resolve("session.revision").orElseThrow().getAsLong() == 9L,
			"binding document session root failed"
		);
		check(
			text(decoded, "fields/test:profile/route").equals("safe"),
			"binding document field identifiers must preserve paths"
		);
		check(
			decoded.resolve("flow/fields/test:temporary/choice").orElseThrow().getAsInt() == 3,
			"binding document flow fields failed"
		);
		expectIllegalArgument(
			() -> BindingContextDocument.decode(
				bytes(
					"""
					{"viewer":{},"viewer":{},"session":{},"flow":{},"fields":{},"flow_fields":{}}
					"""
				)
			),
			"binding documents must reject duplicate keys"
		);
		expectIllegalArgument(
			() -> BindingContextDocument.decode(
				bytes(
					"""
					{"viewer":{},"session":{},"flow":{},"fields":{},"flow_fields":{},"ui":{}}
					"""
				)
			),
			"binding documents must reject client-local roots"
		);
	}

	private static void checkBindingRuntime() {
		BindingContext context = runtimeContext("Alex", 7L, 2L);
		BindingRuntime runtime = new BindingRuntime(
			(key, arguments) -> key.equals("test.progress")
				? Optional.of(arguments.get(0).getAsString() + "/" + arguments.get(1).getAsString())
				: Optional.empty()
		);

		assertCondition(
			runtime,
			context,
			compare(ConditionOperator.EQ, binding("viewer.name"), literal("\"Alex\"")),
			true,
			"eq failed"
		);
		assertCondition(
			runtime,
			context,
			compare(ConditionOperator.NOT_EQ, binding("viewer.name"), literal("\"Steve\"")),
			true,
			"not_eq failed"
		);
		assertCondition(
			runtime,
			context,
			compare(ConditionOperator.GREATER, binding("session.revision"), literal("5")),
			true,
			"greater failed"
		);
		assertCondition(
			runtime,
			context,
			compare(ConditionOperator.GREATER_OR_EQUAL, binding("session.revision"), literal("7")),
			true,
			"greater_or_equal failed"
		);
		assertCondition(
			runtime,
			context,
			compare(ConditionOperator.LESS, binding("flow.completed"), literal("3")),
			true,
			"less failed"
		);
		assertCondition(
			runtime,
			context,
			compare(ConditionOperator.LESS_OR_EQUAL, binding("flow.completed"), literal("2")),
			true,
			"less_or_equal failed"
		);

		ConditionExpression equality = compare(
			ConditionOperator.EQ,
			binding("viewer.online"),
			literal("true")
		);
		ConditionExpression numeric = compare(
			ConditionOperator.GREATER,
			binding("session.revision"),
			literal("0")
		);
		assertCondition(
			runtime,
			context,
			new JunctionCondition(JunctionOperator.ALL, List.of(equality, numeric)),
			true,
			"all failed"
		);
		assertCondition(
			runtime,
			context,
			new JunctionCondition(
				JunctionOperator.ANY,
				List.of(
					compare(ConditionOperator.EQ, binding("viewer.name"), literal("\"missing\"")),
					equality
				)
			),
			true,
			"any failed"
		);
		assertCondition(
			runtime,
			context,
			new NotCondition(compare(ConditionOperator.EQ, binding("viewer.name"), literal("\"Steve\""))),
			true,
			"not failed"
		);
		assertCondition(
			runtime,
			context,
			new ExistsCondition(binding("fields/test:profile/route")),
			true,
			"exists failed"
		);
		Evaluation<Boolean> missingExists = runtime.evaluateCondition(
			new ExistsCondition(binding("fields/test:missing/path")),
			context
		);
		check(!missingExists.value().orElseThrow(), "missing exists must be false");
		check(missingExists.totalDiagnosticCount() == 0, "exists must not diagnose a normal absent value");

		ConditionExpression runtimeTypeMismatch = compare(
			ConditionOperator.GREATER,
			binding("viewer.name"),
			literal("1")
		);
		Evaluation<Boolean> mismatch = runtime.evaluateCondition(runtimeTypeMismatch, context);
		check(!mismatch.value().orElseThrow(), "runtime type mismatch must fail closed");
		check(mismatch.totalDiagnosticCount() == 1, "runtime type mismatch must be diagnosed");
		Evaluation<Boolean> negatedMismatch = runtime.evaluateCondition(
			new NotCondition(runtimeTypeMismatch),
			context
		);
		check(!negatedMismatch.value().orElseThrow(), "not must not turn an evaluation error into true");

		checkText(runtime, context, staticText("静态"), "静态", "static text failed");
		checkText(
			runtime,
			context,
			new BoundText(binding("viewer.name"), Optional.empty()),
			"Alex",
			"bound text failed"
		);
		Evaluation<String> boundFallback = runtime.evaluateText(
			new BoundText(binding("viewer.team_name"), fallback("未分队")),
			context
		);
		check(boundFallback.value().orElseThrow().equals("未分队"), "bound fallback failed");
		check(boundFallback.totalDiagnosticCount() == 1, "missing bound text must be diagnosed");

		TextTemplate translated = new TranslatedText(
			"test.progress",
			List.of(binding("flow.completed"), binding("flow.total")),
			fallback("翻译回退")
		);
		checkText(runtime, context, translated, "2/3", "translated text failed");
		Evaluation<String> translationFallback = runtime.evaluateText(
			new TranslatedText("test.missing", List.of(), fallback("翻译回退")),
			context
		);
		check(translationFallback.value().orElseThrow().equals("翻译回退"), "translation fallback failed");
		check(translationFallback.totalDiagnosticCount() == 1, "missing translation must be diagnosed");

		TextTemplate concatenated = new ConcatenatedText(
			List.of(staticText("玩家："), new BoundText(binding("viewer.name"), Optional.empty()))
		);
		checkText(runtime, context, concatenated, "玩家：Alex", "concatenated text failed");

		TextTemplate dependencyTemplate = new BoundText(
			binding("viewer.team_name"),
			Optional.of(new BoundText(binding("session.phase_name"), fallback("未知阶段")))
		);
		Set<String> expectedDependencies = Set.of("viewer.team_name", "session.phase_name");
		Set<String> dependencies = runtime.dependencies(dependencyTemplate);
		check(dependencies.equals(expectedDependencies), "text dependencies are incomplete");
		check(
			runtime.evaluateText(dependencyTemplate, context).dependencies().equals(expectedDependencies),
			"evaluation dependencies must include fallback branches"
		);
		check(
			runtime.evaluateText(dependencyTemplate, runtimeContext("Steve", 1L, 0L))
				.dependencies()
				.equals(expectedDependencies),
			"dependency sets must not depend on runtime values"
		);
		expectUnsupported(dependencies::clear, "dependency sets must be immutable");

		Set<String> conditionDependencies = runtime.dependencies(
			new JunctionCondition(JunctionOperator.ALL, List.of(equality, numeric))
		);
		check(
			conditionDependencies.equals(Set.of("viewer.online", "session.revision")),
			"condition dependencies are incomplete"
		);

		BindingRuntime failingTranslations = new BindingRuntime(
			(key, arguments) -> {
				throw new IllegalStateException("translation provider failed");
			}
		);
		Evaluation<String> failedTranslation = failingTranslations.evaluateText(translated, context);
		check(failedTranslation.value().orElseThrow().equals("翻译回退"), "translation exceptions need fallback");
		check(failedTranslation.totalDiagnosticCount() == 1, "translation exceptions must be diagnosed");
	}

	private static void checkBindingDiagnostics() {
		BindingRuntime runtime = new BindingRuntime();
		List<ConditionExpression> missing = new ArrayList<>();
		for (int index = 0; index < BindingRuntime.MAX_DIAGNOSTICS + 10; index++) {
			missing.add(
				compare(
					ConditionOperator.EQ,
					binding("viewer.missing_" + index),
					literal("\"value\"")
				)
			);
		}
		Evaluation<Boolean> result = runtime.evaluateCondition(
			new JunctionCondition(JunctionOperator.ALL, missing),
			BindingContext.empty()
		);
		check(!result.value().orElseThrow(), "diagnostic stress expression must fail closed");
		check(
			result.diagnostics().size() == BindingRuntime.MAX_DIAGNOSTICS,
			"runtime diagnostic details must be bounded"
		);
		check(
			result.totalDiagnosticCount() == BindingRuntime.MAX_DIAGNOSTICS + 10,
			"runtime diagnostic total must remain exact"
		);
		check(result.diagnosticsTruncated(), "bounded runtime diagnostics must expose truncation");
	}

	private static void checkStyleRuntime() {
		check(
			UiStyleRuntime.resolve(
				BuiltInUiResources.defaultTheme(),
				null,
				ResponsiveTier.STANDARD,
				StyleState.NORMAL,
				false
			).isEmpty(),
			"virtual Repeat spacers without definitions must resolve to an empty style"
		);
		StyleDefinition style = new StyleDefinition(
			Map.of(
				StyleState.NORMAL,
				Map.of(
					"background_color", styleValue("\"#111111\""),
					"font", styleValue("\"@fonts.body\""),
					"padding", styleValue("8")
				),
				StyleState.HOVER,
				Map.of(
					"background_color", styleValue("\"#222222\""),
					"border_width", styleValue("2"),
					"font_size", styleValue("32"),
					"padding", styleValue("99")
				)
			)
		);
		Map<String, StyleValue> resolved = UiStyleRuntime.resolve(
			style,
			StyleState.HOVER,
			Map.of("text_color", styleValue("\"#EEEEEE\"")),
			Map.of("opacity", styleValue("0.75")),
			true
		);
		check(
			resolved.get("background_color").equals(styleValue("\"#222222\"")),
			"hover paint properties must override normal state"
		);
		check(
			resolved.get("padding").equals(styleValue("8")),
			"interactive geometry must remain at its normal-state value"
		);
		check(!resolved.containsKey("font_size"), "interactive states must not introduce font geometry");
		check(
			resolved.get("border_width").equals(styleValue("2")),
			"interactive border feedback must remain available"
		);
		check(
			resolved.get("text_color").equals(styleValue("\"#EEEEEE\""))
				&& resolved.get("opacity").equals(styleValue("0.75")),
			"node and responsive overrides must remain the final cascade layers"
		);
		check(
			UiStyleRuntime.stringValue(resolved, "font").orElseThrow().equals("@fonts.body"),
			"font token references must survive the style cascade"
		);
		check(
			UiStyleRuntime.interactionState(false, true, true, true) == StyleState.DISABLED,
			"disabled must outrank every interactive state"
		);
		check(
			UiStyleRuntime.interactionState(true, true, true, true) == StyleState.PRESSED,
			"pressed must outrank hover and focus"
		);
		check(
			UiStyleRuntime.interactionState(true, false, true, true) == StyleState.HOVER,
			"hover must outrank keyboard focus"
		);
		check(
			UiStyleRuntime.interactionState(true, false, false, true) == StyleState.FOCUSED,
			"focus must apply when no pointer state is active"
		);
	}

	private static void checkLayoutEngine() {
		checkLinearLayout();
		checkCardLayout();
		checkGridLayout();
		checkFlowAndOverlayLayout();
		checkScrollAndConstraintLayout();
		checkLayoutDiagnosticBounds();
	}

	private static void checkCardLayout() {
		LayoutItem card = item(
			"card",
			NodeType.CARD,
			layout().width(fill()).height(fill()).build(),
			0,
			0,
			List.of(
				item(
					"card_column",
					NodeType.COLUMN,
					LayoutDefinition.empty(),
					0,
					0,
					List.of(
						leaf(
							"wrapped_text",
							layout().width(fill()).height(fixed(10)).build(),
							300,
							10
						)
					),
					0
				)
			),
			0
		);
		LayoutResult result = UiLayoutEngine.layout(card, 100, 40);
		PlacedNode column = result.root().children().getFirst();
		check(
			column.bounds().width() == 100,
			"content-sized card children must not exceed the card content width"
		);
		check(
			column.children().getFirst().bounds().width() == 100,
			"fill-width wrapped text must receive the constrained card width"
		);
	}

	private static void checkLinearLayout() {
		LayoutItem weightedRow = item(
			"weighted_row",
			NodeType.ROW,
			layout().width(fill()).height(fill()).build(),
			0,
			0,
			List.of(
				leaf("fixed", layout().width(fixed(60)).height(fill()).build(), 0, 0),
				leaf("fill", layout().width(fill()).height(fill()).build(), 10, 10),
				leaf("weight", layout().width(weight(2)).height(fill()).build(), 10, 10)
			),
			0
		);
		LayoutResult weighted = UiLayoutEngine.layout(weightedRow, 300, 40);
		List<PlacedNode> rowChildren = weighted.root().children();
		check(rowChildren.get(0).bounds().equals(new Rect(0, 0, 60, 40)), "row fixed allocation failed");
		check(rowChildren.get(1).bounds().equals(new Rect(60, 0, 80, 40)), "row fill allocation failed");
		check(rowChildren.get(2).bounds().equals(new Rect(140, 0, 160, 40)), "row weight allocation failed");
		check(weighted.totalDiagnostics() == 0, "valid weighted row produced diagnostics");
		check(
			weighted.equals(UiLayoutEngine.layout(weightedRow, 300, 40)),
			"identical layout inputs must produce identical results"
		);

		LayoutItem paddedColumn = item(
			"padded_column",
			NodeType.COLUMN,
			layout()
				.width(fill())
				.height(fill())
				.padding(new Insets(5, 7, 5, 7))
				.gap(4)
				.build(),
			0,
			0,
			List.of(
				leaf("first", layout().width(fill()).height(fixed(10)).build(), 0, 0),
				leaf("second", layout().width(fill()).height(fixed(20)).build(), 0, 0)
			),
			0
		);
		LayoutResult column = UiLayoutEngine.layout(paddedColumn, 100, 100);
		check(
			column.root().contentBounds().equals(new Rect(7, 5, 86, 90)),
			"column padding did not define the content box"
		);
		check(
			column.root().children().get(0).bounds().equals(new Rect(7, 5, 86, 10)),
			"first padded column child is misplaced"
		);
		check(
			column.root().children().get(1).bounds().equals(new Rect(7, 19, 86, 20)),
			"column gap was not applied exactly once"
		);
	}

	private static void checkGridLayout() {
		LayoutItem grid = item(
			"grid",
			NodeType.GRID,
			layout()
				.width(fill())
				.height(fill())
				.columns(List.of(fixed(50), weight(1), weight(2)))
				.columnGap(10)
				.rowGap(5)
				.build(),
			0,
			0,
			List.of(
				leaf("first", layout().width(fill()).height(fixed(10)).build(), 0, 0),
				leaf(
					"span",
					layout().width(fill()).height(fixed(10)).columnSpan(2).build(),
					30,
					10
				),
				leaf("second_row_fixed", layout().width(fill()).height(fixed(10)).build(), 0, 0),
				leaf("second_row_weight_one", layout().width(fill()).height(fixed(10)).build(), 0, 0),
				leaf("second_row_weight_two", layout().width(fill()).height(fixed(10)).build(), 0, 0)
			),
			0
		);
		LayoutResult result = UiLayoutEngine.layout(grid, 300, 100);
		List<PlacedNode> children = result.root().children();
		check(children.get(0).bounds().equals(new Rect(0, 0, 50, 10)), "fixed grid track failed");
		check(children.get(1).bounds().equals(new Rect(60, 0, 240, 10)), "grid column span failed");
		check(
			children.get(3).bounds().equals(new Rect(60, 15, 80, 10)),
			"weight-one grid track allocation failed"
		);
		check(
			children.get(4).bounds().equals(new Rect(150, 15, 150, 10)),
			"weight-two grid track allocation failed"
		);
		check(result.totalDiagnostics() == 0, "valid grid produced diagnostics: " + result.diagnostics());
	}

	private static void checkFlowAndOverlayLayout() {
		LayoutDefinition flowChild = layout().width(fixed(30)).height(fixed(10)).build();
		LayoutItem flow = item(
			"flow",
			NodeType.FLOW,
			layout()
				.width(fixed(65))
				.height(fill())
				.direction(Direction.HORIZONTAL)
				.gap(5)
				.lineGap(3)
				.build(),
			0,
			0,
			List.of(
				leaf("one", flowChild, 0, 0),
				leaf("two", flowChild, 0, 0),
				leaf("three", flowChild, 0, 0)
			),
			0
		);
		LayoutResult flowResult = UiLayoutEngine.layout(flow, 100, 40);
		List<PlacedNode> flowChildren = flowResult.root().children();
		check(flowChildren.get(0).bounds().equals(new Rect(0, 0, 30, 10)), "flow first item failed");
		check(
			flowChildren.get(1).bounds().equals(new Rect(35, 0, 30, 10)),
			"flow must keep an exact-boundary item on the current line"
		);
		check(
			flowChildren.get(2).bounds().equals(new Rect(0, 13, 30, 10)),
			"flow must wrap the first item beyond the boundary"
		);

		LayoutItem overlay = item(
			"overlay",
			NodeType.OVERLAY,
			layout().width(fill()).height(fill()).build(),
			0,
			0,
			List.of(
				leaf(
					"anchored",
					layout()
						.width(fixed(20))
						.height(fixed(10))
						.anchor(Anchor.BOTTOM_RIGHT)
						.offset(-3, -4)
						.build(),
					0,
					0
				)
			),
			0
		);
		LayoutResult overlayResult = UiLayoutEngine.layout(overlay, 100, 80);
		check(
			overlayResult.root().children().getFirst().bounds().equals(new Rect(77, 66, 20, 10)),
			"overlay anchor and offsets were not combined correctly"
		);
	}

	private static void checkScrollAndConstraintLayout() {
		LayoutItem scroll = item(
			"scroll",
			NodeType.SCROLL,
			layout().width(fill()).height(fill()).direction(Direction.VERTICAL).build(),
			0,
			0,
			List.of(
				leaf("scroll_content", layout().width(fill()).height(fixed(120)).build(), 0, 0)
			),
			200
		);
		LayoutResult scrollResult = UiLayoutEngine.layout(scroll, 100, 50);
		PlacedNode scrollRoot = scrollResult.root();
		PlacedNode scrollChild = scrollRoot.children().getFirst();
		check(scrollRoot.scrollOffset() == 70, "vertical scroll offset must clamp to the content range");
		check(scrollChild.bounds().equals(new Rect(0, -70, 100, 120)), "scroll content translation failed");
		check(scrollChild.clip().equals(new Rect(0, 0, 100, 50)), "scroll clip must equal the viewport");
		check(
			scrollResult.diagnostics()
				.stream()
				.anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.SCROLL_CLAMPED),
			"clamped scroll must produce a diagnostic"
		);
		Rect logicalTrack = new Rect(96, 0, 4, 200);
		Rect logicalThumb = UiLayoutEngine.scrollbarThumb(
			logicalTrack,
			Direction.VERTICAL,
			400,
			100,
			200
		);
		check(
			logicalThumb.equals(new Rect(96, 50, 4, 100)),
			"scrollbar thumb must be proportional in logical coordinates"
		);
		check(
			new Transform(0.5, 0.0, 0.0).apply(logicalThumb)
				.equals(new Rect(48, 25, 2, 50)),
			"scrollbar thumb must transform as one logical rectangle"
		);

		LayoutItem constrained = item(
			"constraints",
			NodeType.ROW,
			layout().width(fill()).height(fill()).build(),
			0,
			0,
			List.of(
				leaf(
					"minimum",
					layout().width(fixed(-5)).height(fill()).minimumWidth(20).build(),
					-1,
					1
				),
				leaf(
					"maximum",
					layout().width(fixed(100)).height(fill()).maximumWidth(40).build(),
					1,
					1
				)
			),
			0
		);
		LayoutResult constrainedResult = UiLayoutEngine.layout(constrained, 100, 20);
		check(
			constrainedResult.root().children().get(0).bounds().width() == 20,
			"minimum width must clamp a negative fixed size"
		);
		check(
			constrainedResult.root().children().get(1).bounds().width() == 40,
			"maximum width must clamp an oversized fixed size"
		);
		check(
			constrainedResult.diagnostics()
				.stream()
				.anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.NEGATIVE_INTRINSIC_SIZE),
			"negative intrinsic sizes must be diagnosed"
		);

		LayoutResult negativeViewport = UiLayoutEngine.layout(
			leaf("negative_viewport", layout().width(fill()).height(fill()).build(), 0, 0),
			-10,
			-20
		);
		check(negativeViewport.root().bounds().width() == 0, "negative viewport width must clamp to zero");
		check(negativeViewport.root().bounds().height() == 0, "negative viewport height must clamp to zero");
		check(
			negativeViewport.diagnostics()
				.stream()
				.anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.NEGATIVE_VIEWPORT),
			"negative viewports must be diagnosed"
		);
	}

	private static void checkLayoutDiagnosticBounds() {
		List<LayoutItem> overflowing = new ArrayList<>();
		for (int index = 0; index < 60; index++) {
			overflowing.add(
				leaf(
					"overflow_" + index,
					layout().width(fixed(1)).height(fixed(1)).build(),
					10,
					1
				)
			);
		}
		LayoutItem root = item(
			"overflow_root",
			NodeType.ROW,
			layout().width(fill()).height(fill()).build(),
			0,
			0,
			overflowing,
			0
		);
		LayoutResult result = UiLayoutEngine.layout(root, 1, 1);
		check(
			result.diagnostics().size() == UiLayoutEngine.MAX_DIAGNOSTIC_DETAILS,
			"layout diagnostic details must be bounded"
		);
		check(result.totalDiagnostics() == 61, "layout diagnostic total must remain exact");
	}

	private static LayoutBuilder layout() {
		return new LayoutBuilder();
	}

	private static LayoutItem item(
		final String key,
		final NodeType type,
		final LayoutDefinition layout,
		final int intrinsicWidth,
		final int intrinsicHeight,
		final List<LayoutItem> children,
		final int scrollOffset
	) {
		return new LayoutItem(
			key,
			type,
			layout,
			intrinsicWidth,
			intrinsicHeight,
			children,
			"/" + key,
			scrollOffset
		);
	}

	private static LayoutItem leaf(
		final String key,
		final LayoutDefinition layout,
		final int intrinsicWidth,
		final int intrinsicHeight
	) {
		return item(key, NodeType.TEXT, layout, intrinsicWidth, intrinsicHeight, List.of(), 0);
	}

	private static SizeSpec fixed(final double value) {
		return new SizeSpec(SizeMode.FIXED, Optional.of(value));
	}

	private static SizeSpec fill() {
		return new SizeSpec(SizeMode.FILL, Optional.empty());
	}

	private static SizeSpec weight(final double value) {
		return new SizeSpec(SizeMode.WEIGHT, Optional.of(value));
	}

	private static BindingContext runtimeContext(
		final String viewerName,
		final long revision,
		final long completed
	) {
		return BindingContext.builder()
			.viewer("name", string(viewerName))
			.viewer("online", bool(true))
			.session("revision", integer(revision))
			.session("phase_name", string("待机"))
			.flow("completed", integer(completed))
			.flow("total", integer(3))
			.field(id("profile/route"), string("safe"))
			.flowField(id("temporary/choice"), integer(9))
			.item("name", string("Runner 01"))
			.ui("route", string("balanced"))
			.build();
	}

	private static void assertCondition(
		final BindingRuntime runtime,
		final BindingContext context,
		final ConditionExpression expression,
		final boolean expected,
		final String message
	) {
		Evaluation<Boolean> result = runtime.evaluateCondition(expression, context);
		check(result.value().orElseThrow() == expected, message + ": " + result.diagnostics());
		check(result.totalDiagnosticCount() == 0, message + " produced diagnostics: " + result.diagnostics());
	}

	private static void checkText(
		final BindingRuntime runtime,
		final BindingContext context,
		final TextTemplate template,
		final String expected,
		final String message
	) {
		Evaluation<String> result = runtime.evaluateText(template, context);
		check(result.value().orElseThrow().equals(expected), message + ": " + result.diagnostics());
		check(result.totalDiagnosticCount() == 0, message + " produced diagnostics: " + result.diagnostics());
	}

	private static ComparisonCondition compare(
		final ConditionOperator operator,
		final ValueExpression left,
		final ValueExpression right
	) {
		return new ComparisonCondition(operator, left, right);
	}

	private static BindingValue binding(final String path) {
		return new BindingValue(path);
	}

	private static LiteralValue literal(final String canonicalJson) {
		return new LiteralValue(canonicalJson);
	}

	private static StyleValue styleValue(final String canonicalJson) {
		return new StyleValue(canonicalJson);
	}

	private static StaticText staticText(final String value) {
		return new StaticText(new RichText("{\"text\":\"" + value + "\"}", value));
	}

	private static Optional<TextTemplate> fallback(final String value) {
		return Optional.of(staticText(value));
	}

	private static JsonPrimitive string(final String value) {
		return new JsonPrimitive(value);
	}

	private static JsonPrimitive integer(final long value) {
		return new JsonPrimitive(value);
	}

	private static JsonPrimitive bool(final boolean value) {
		return new JsonPrimitive(value);
	}

	private static String text(final BindingContext context, final String path) {
		return context.resolve(path).orElseThrow().getAsString();
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("test", path);
	}

	private static byte[] bytes(final String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] sha256(final byte[] value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value);
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static void expectIllegalArgument(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// Expected trust-boundary rejection.
		}
	}

	private static void expectIllegalState(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (IllegalStateException expected) {
			// Expected invalid cache state rejection.
		}
	}

	private static void expectUnsupported(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected immutable collection.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class LayoutBuilder {
		private Optional<SizeSpec> width = Optional.empty();
		private Optional<SizeSpec> height = Optional.empty();
		private Optional<Integer> minimumWidth = Optional.empty();
		private Optional<Integer> maximumWidth = Optional.empty();
		private Insets padding = Insets.zero();
		private Optional<Integer> gap = Optional.empty();
		private Optional<Integer> lineGap = Optional.empty();
		private Optional<Integer> columnGap = Optional.empty();
		private Optional<Integer> rowGap = Optional.empty();
		private Optional<Direction> direction = Optional.empty();
		private List<SizeSpec> columns = List.of();
		private Optional<Integer> columnSpan = Optional.empty();
		private Optional<Anchor> anchor = Optional.empty();
		private Optional<Integer> offsetX = Optional.empty();
		private Optional<Integer> offsetY = Optional.empty();

		private LayoutBuilder width(final SizeSpec value) {
			this.width = Optional.of(value);
			return this;
		}

		private LayoutBuilder height(final SizeSpec value) {
			this.height = Optional.of(value);
			return this;
		}

		private LayoutBuilder minimumWidth(final int value) {
			this.minimumWidth = Optional.of(value);
			return this;
		}

		private LayoutBuilder maximumWidth(final int value) {
			this.maximumWidth = Optional.of(value);
			return this;
		}

		private LayoutBuilder padding(final Insets value) {
			this.padding = value;
			return this;
		}

		private LayoutBuilder gap(final int value) {
			this.gap = Optional.of(value);
			return this;
		}

		private LayoutBuilder lineGap(final int value) {
			this.lineGap = Optional.of(value);
			return this;
		}

		private LayoutBuilder columnGap(final int value) {
			this.columnGap = Optional.of(value);
			return this;
		}

		private LayoutBuilder rowGap(final int value) {
			this.rowGap = Optional.of(value);
			return this;
		}

		private LayoutBuilder direction(final Direction value) {
			this.direction = Optional.of(value);
			return this;
		}

		private LayoutBuilder columns(final List<SizeSpec> value) {
			this.columns = List.copyOf(value);
			return this;
		}

		private LayoutBuilder columnSpan(final int value) {
			this.columnSpan = Optional.of(value);
			return this;
		}

		private LayoutBuilder anchor(final Anchor value) {
			this.anchor = Optional.of(value);
			return this;
		}

		private LayoutBuilder offset(final int x, final int y) {
			this.offsetX = Optional.of(x);
			this.offsetY = Optional.of(y);
			return this;
		}

		private LayoutDefinition build() {
			return new LayoutDefinition(
				this.width,
				this.height,
				this.minimumWidth,
				this.maximumWidth,
				Optional.empty(),
				Optional.empty(),
				Insets.zero(),
				this.padding,
				AlignSelf.AUTO,
				this.gap,
				this.lineGap,
				this.columnGap,
				this.rowGap,
				Optional.empty(),
				Optional.empty(),
				this.direction,
				this.columns,
				this.columnSpan,
				this.anchor,
				this.offsetX,
				this.offsetY
			);
		}
	}
}
