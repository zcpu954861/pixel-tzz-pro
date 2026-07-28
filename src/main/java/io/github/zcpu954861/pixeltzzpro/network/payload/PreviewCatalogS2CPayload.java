package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bounded page summaries for the administrator preview catalog.
 */
public record PreviewCatalogS2CPayload(
	int protocolVersion,
	long generation,
	String status,
	String message,
	List<Entry> entries
) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 512;
	public static final int MAX_STATUS_LENGTH = 32;
	public static final int MAX_MESSAGE_LENGTH = 1_024;
	public static final int MAX_TITLE_LENGTH = 256;
	public static final Type<PreviewCatalogS2CPayload> TYPE = new Type<>(PixelTzzPro.id("preview_catalog_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PreviewCatalogS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(PreviewCatalogS2CPayload::write, PreviewCatalogS2CPayload::new);

	public PreviewCatalogS2CPayload {
		if (protocolVersion < 0) {
			throw new IllegalArgumentException("protocolVersion must be non-negative");
		}
		if (generation < 0L) {
			throw new IllegalArgumentException("generation must be non-negative");
		}
		status = requireBounded(status, MAX_STATUS_LENGTH, "status");
		message = requireBounded(message, MAX_MESSAGE_LENGTH, "message");
		entries = List.copyOf(entries);
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException("entries size exceeds " + MAX_ENTRIES);
		}
	}

	private PreviewCatalogS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readLong(),
			buffer.readUtf(MAX_STATUS_LENGTH),
			buffer.readUtf(MAX_MESSAGE_LENGTH),
			readList(buffer, MAX_ENTRIES, "entries", Entry::read)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeLong(this.generation);
		buffer.writeUtf(this.status, MAX_STATUS_LENGTH);
		buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
		writeList(buffer, this.entries, Entry::write);
	}

	@Override
	public Type<PreviewCatalogS2CPayload> type() {
		return TYPE;
	}

	public record Entry(Identifier pageId, Identifier gameId, String title) {
		public Entry {
			pageId = requireIdentifier(pageId, "pageId");
			gameId = requireIdentifier(gameId, "gameId");
			title = requireBounded(title, MAX_TITLE_LENGTH, "title");
		}

		private static Entry read(final RegistryFriendlyByteBuf buffer) {
			return new Entry(
				readIdentifier(buffer, "pageId"),
				readIdentifier(buffer, "gameId"),
				buffer.readUtf(MAX_TITLE_LENGTH)
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final Entry entry) {
			writeIdentifier(buffer, entry.pageId);
			writeIdentifier(buffer, entry.gameId);
			buffer.writeUtf(entry.title, MAX_TITLE_LENGTH);
		}
	}
}
