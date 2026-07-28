package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Bounded client-side LRU for server-validated page bundles.
 *
 * <p>The class is client-neutral so cache and hash behavior can be checked without
 * starting Minecraft's renderer. Active page instances pin their entry until the
 * page closes.
 */
public final class PageDocumentCache {
	public static final int MAX_ENTRIES = 64;
	public static final int MAX_BYTES = 8 * 1024 * 1024;
	public static final int MAX_DOCUMENT_BYTES = UiDefinitions.MAX_CANONICAL_DOCUMENT_BYTES;
	private static final int SHA_256_BYTES = 32;

	private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
	private int totalBytes;

	public synchronized Key store(
		final long generation,
		final Identifier pageId,
		final byte[] pageHash,
		final byte[] pageDocument,
		final Identifier themeId,
		final byte[] themeHash,
		final byte[] themeDocument
	) {
		requireGeneration(generation);
		Objects.requireNonNull(pageId, "pageId");
		Objects.requireNonNull(themeId, "themeId");
		requireDocument(pageDocument, "page document");
		requireDocument(themeDocument, "theme document");
		requireHash(pageHash, pageDocument, "page");
		requireHash(themeHash, themeDocument, "theme");

		Key key = new Key(generation, pageId, HexFormat.of().formatHex(pageHash));
		Entry replacement = new Entry(
			new CachedPage(
				generation,
				pageId,
				pageHash,
				pageDocument,
				themeId,
				themeHash,
				themeDocument
			),
			0
		);
		Entry previous = this.entries.remove(key);
		if (previous != null) {
			if (previous.pinCount > 0) {
				this.entries.put(key, previous);
				return key;
			}
			this.totalBytes -= previous.page.byteSize();
		}

		this.entries.put(key, replacement);
		this.totalBytes += replacement.page.byteSize();
		evictToBounds(key);
		return key;
	}

	public synchronized Optional<CachedPage> get(final Key key) {
		Entry entry = this.entries.get(Objects.requireNonNull(key, "key"));
		return entry == null ? Optional.empty() : Optional.of(entry.page);
	}

	public synchronized Optional<CachedPage> find(
		final long generation,
		final Identifier pageId
	) {
		requireGeneration(generation);
		Objects.requireNonNull(pageId, "pageId");
		Key matched = null;
		// ponytail: this is bounded to 64 entries; add a secondary index only if that cap grows.
		for (Key candidate : this.entries.keySet()) {
			if (candidate.generation == generation && candidate.pageId.equals(pageId)) {
				matched = candidate;
				break;
			}
		}
		if (matched == null) {
			return Optional.empty();
		}
		return Optional.of(this.entries.get(matched).page);
	}

	public synchronized Optional<ParsedDocuments> parsed(final Key key) {
		Entry entry = this.entries.get(Objects.requireNonNull(key, "key"));
		return entry == null ? Optional.empty() : Optional.ofNullable(entry.parsedDocuments);
	}

	public synchronized ParsedDocuments attachParsed(
		final Key key,
		final PageDefinition page,
		final ThemeDefinition theme
	) {
		Entry entry = this.entries.get(Objects.requireNonNull(key, "key"));
		if (entry == null) {
			throw new IllegalStateException("cannot attach parsed documents to a missing cache entry");
		}
		requireParsedDocuments(entry.page, page, theme);
		if (entry.parsedDocuments == null) {
			entry.parsedDocuments = new ParsedDocuments(page, theme);
		}
		return entry.parsedDocuments;
	}

	public synchronized boolean pin(final Key key) {
		Entry entry = this.entries.get(Objects.requireNonNull(key, "key"));
		if (entry == null) {
			return false;
		}
		entry.pinCount = Math.incrementExact(entry.pinCount);
		return true;
	}

	public synchronized void unpin(final Key key) {
		Entry entry = this.entries.get(Objects.requireNonNull(key, "key"));
		if (entry == null) {
			return;
		}
		if (entry.pinCount <= 0) {
			throw new IllegalStateException("page cache entry is not pinned: " + key);
		}
		entry.pinCount--;
		evictToBounds(null);
	}

	public synchronized void removeUnpinnedBefore(final long generation) {
		requireGeneration(generation);
		var iterator = this.entries.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Key, Entry> candidate = iterator.next();
			if (candidate.getKey().generation < generation && candidate.getValue().pinCount == 0) {
				this.totalBytes -= candidate.getValue().page.byteSize();
				iterator.remove();
			}
		}
	}

	public synchronized void clear() {
		this.entries.clear();
		this.totalBytes = 0;
	}

	public synchronized int size() {
		return this.entries.size();
	}

	public synchronized int byteSize() {
		return this.totalBytes;
	}

	private void evictToBounds(final Key justInserted) {
		while (this.entries.size() > MAX_ENTRIES || this.totalBytes > MAX_BYTES) {
			Key evictedKey = null;
			Entry evictedEntry = null;
			for (Map.Entry<Key, Entry> candidate : this.entries.entrySet()) {
				if (candidate.getValue().pinCount == 0 && !candidate.getKey().equals(justInserted)) {
					evictedKey = candidate.getKey();
					evictedEntry = candidate.getValue();
					break;
				}
			}
			if (evictedKey == null) {
				if (justInserted != null) {
					Entry inserted = this.entries.remove(justInserted);
					if (inserted != null) {
						this.totalBytes -= inserted.page.byteSize();
					}
				}
				throw new IllegalStateException("page cache is full of pinned entries");
			}
			this.entries.remove(evictedKey);
			this.totalBytes -= evictedEntry.page.byteSize();
		}
	}

	private static void requireGeneration(final long generation) {
		if (generation < 0L) {
			throw new IllegalArgumentException("generation must be non-negative");
		}
	}

	private static void requireDocument(final byte[] document, final String name) {
		Objects.requireNonNull(document, name);
		if (document.length == 0 || document.length > MAX_DOCUMENT_BYTES) {
			throw new IllegalArgumentException(
				name + " length must be within 1.." + MAX_DOCUMENT_BYTES + " bytes"
			);
		}
	}

	private static void requireHash(final byte[] expected, final byte[] document, final String name) {
		Objects.requireNonNull(expected, name + " hash");
		if (expected.length != SHA_256_BYTES) {
			throw new IllegalArgumentException(name + " hash must contain exactly 32 bytes");
		}
		if (!MessageDigest.isEqual(expected, sha256(document))) {
			throw new IllegalArgumentException(name + " document SHA-256 does not match");
		}
	}

	private static byte[] sha256(final byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static void requireParsedDocuments(
		final CachedPage cached,
		final PageDefinition page,
		final ThemeDefinition theme
	) {
		Objects.requireNonNull(page, "page");
		Objects.requireNonNull(theme, "theme");
		String pageHash = HexFormat.of().formatHex(cached.pageHash);
		String themeHash = HexFormat.of().formatHex(cached.themeHash);
		if (
			!cached.pageId.equals(page.id())
				|| !cached.themeId.equals(theme.id())
				|| !theme.id().equals(page.theme())
				|| !pageHash.equals(page.sha256())
				|| !themeHash.equals(theme.sha256())
				|| page.canonicalDocument() == null
				|| theme.canonicalDocument() == null
				|| !MessageDigest.isEqual(
					cached.pageDocument,
					page.canonicalDocument().getBytes(StandardCharsets.UTF_8)
				)
				|| !MessageDigest.isEqual(
					cached.themeDocument,
					theme.canonicalDocument().getBytes(StandardCharsets.UTF_8)
				)
		) {
			throw new IllegalArgumentException("parsed documents do not match the cached envelope");
		}
	}

	public record Key(long generation, Identifier pageId, String pageHash) {
		public Key {
			requireGeneration(generation);
			Objects.requireNonNull(pageId, "pageId");
			Objects.requireNonNull(pageHash, "pageHash");
			if (pageHash.length() != SHA_256_BYTES * 2) {
				throw new IllegalArgumentException("pageHash must be a lowercase SHA-256 hex string");
			}
		}
	}

	public record ParsedDocuments(PageDefinition page, ThemeDefinition theme) {
		public ParsedDocuments {
			Objects.requireNonNull(page, "page");
			Objects.requireNonNull(theme, "theme");
		}
	}

	public static final class CachedPage {
		private final long generation;
		private final Identifier pageId;
		private final byte[] pageHash;
		private final byte[] pageDocument;
		private final Identifier themeId;
		private final byte[] themeHash;
		private final byte[] themeDocument;

		private CachedPage(
			final long generation,
			final Identifier pageId,
			final byte[] pageHash,
			final byte[] pageDocument,
			final Identifier themeId,
			final byte[] themeHash,
			final byte[] themeDocument
		) {
			this.generation = generation;
			this.pageId = pageId;
			this.pageHash = pageHash.clone();
			this.pageDocument = pageDocument.clone();
			this.themeId = themeId;
			this.themeHash = themeHash.clone();
			this.themeDocument = themeDocument.clone();
		}

		public long generation() {
			return this.generation;
		}

		public Identifier pageId() {
			return this.pageId;
		}

		public byte[] pageHash() {
			return this.pageHash.clone();
		}

		public byte[] pageDocument() {
			return this.pageDocument.clone();
		}

		public Identifier themeId() {
			return this.themeId;
		}

		public byte[] themeHash() {
			return this.themeHash.clone();
		}

		public byte[] themeDocument() {
			return this.themeDocument.clone();
		}

		public int byteSize() {
			return this.pageDocument.length + this.themeDocument.length;
		}

		public boolean matchesEnvelope(
			final long expectedGeneration,
			final Identifier expectedPageId,
			final Identifier expectedThemeId,
			final byte[] expectedPageHash,
			final byte[] expectedThemeHash
		) {
			return this.generation == expectedGeneration
				&& this.pageId.equals(expectedPageId)
				&& this.themeId.equals(expectedThemeId)
				&& MessageDigest.isEqual(
					this.pageHash,
					Objects.requireNonNull(expectedPageHash, "expectedPageHash")
				)
				&& MessageDigest.isEqual(
					this.themeHash,
					Objects.requireNonNull(expectedThemeHash, "expectedThemeHash")
				);
		}
	}

	private static final class Entry {
		private final CachedPage page;
		private int pinCount;
		private ParsedDocuments parsedDocuments;

		private Entry(final CachedPage page, final int pinCount) {
			this.page = page;
			this.pinCount = pinCount;
		}
	}
}
