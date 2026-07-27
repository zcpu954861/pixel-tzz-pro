package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readFixedBytes;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Requests one page definition from the current healthy generation.
 */
public record PreviewPageRequestC2SPayload(
	Identifier pageId,
	Optional<CachedDocuments> cachedDocuments
) implements CustomPacketPayload {
	public static final int HASH_LENGTH = 32;
	public static final Type<PreviewPageRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("preview_page_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPageRequestC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(PreviewPageRequestC2SPayload::write, PreviewPageRequestC2SPayload::new);

	public PreviewPageRequestC2SPayload {
		pageId = requireIdentifier(pageId, "pageId");
		cachedDocuments = Objects.requireNonNull(cachedDocuments, "cachedDocuments");
	}

	public PreviewPageRequestC2SPayload(final Identifier pageId) {
		this(pageId, Optional.empty());
	}

	private PreviewPageRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(readIdentifier(buffer, "pageId"), readCachedDocuments(buffer));
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeIdentifier(buffer, this.pageId);
		buffer.writeBoolean(this.cachedDocuments.isPresent());
		this.cachedDocuments.ifPresent(cached -> cached.write(buffer));
	}

	@Override
	public Type<PreviewPageRequestC2SPayload> type() {
		return TYPE;
	}

	private static Optional<CachedDocuments> readCachedDocuments(
		final RegistryFriendlyByteBuf buffer
	) {
		return buffer.readBoolean()
			? Optional.of(new CachedDocuments(buffer))
			: Optional.empty();
	}

	public record CachedDocuments(
		long generation,
		Identifier themeId,
		byte[] pageHash,
		byte[] themeHash
	) {
		public CachedDocuments {
			if (generation < 0L) {
				throw new IllegalArgumentException("generation must be non-negative");
			}
			themeId = requireIdentifier(themeId, "themeId");
			pageHash = copyHash(pageHash, "pageHash");
			themeHash = copyHash(themeHash, "themeHash");
		}

		private CachedDocuments(final RegistryFriendlyByteBuf buffer) {
			this(
				buffer.readLong(),
				readIdentifier(buffer, "themeId"),
				readFixedBytes(buffer, HASH_LENGTH),
				readFixedBytes(buffer, HASH_LENGTH)
			);
		}

		private void write(final RegistryFriendlyByteBuf buffer) {
			buffer.writeLong(this.generation);
			writeIdentifier(buffer, this.themeId);
			buffer.writeBytes(this.pageHash);
			buffer.writeBytes(this.themeHash);
		}

		@Override
		public byte[] pageHash() {
			return this.pageHash.clone();
		}

		@Override
		public byte[] themeHash() {
			return this.themeHash.clone();
		}

		private static byte[] copyHash(final byte[] value, final String field) {
			Objects.requireNonNull(value, field);
			if (value.length != HASH_LENGTH) {
				throw new IllegalArgumentException(field + " must contain " + HASH_LENGTH + " bytes");
			}
			return value.clone();
		}
	}
}
