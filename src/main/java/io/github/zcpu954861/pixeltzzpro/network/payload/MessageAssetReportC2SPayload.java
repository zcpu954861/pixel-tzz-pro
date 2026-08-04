package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client observation of one {@link MessageAssetManifestS2CPayload}.
 *
 * <p>The report echoes only manifest identities and a three-state resolution. It cannot add a cue,
 * reveal cue content, grant authority, or advance a callback. {@code reportSequence} is scoped to
 * one play connection so the server can reject replayed or reordered observations. The echoed
 * {@code manifestSequence} prevents a late report for a smaller cumulative manifest from being
 * accepted after a cue-start extension.
 */
public record MessageAssetReportC2SPayload(
	long reportSequence,
	long generation,
	long manifestSequence,
	List<Entry> entries
) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = MessageAssetManifestS2CPayload.MAX_ENTRIES;
	public static final int MAX_DOCUMENT_BYTES = 24 * 1_024;
	public static final Type<MessageAssetReportC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_asset_report_c2s")
	);
	public static final StreamCodec<
		RegistryFriendlyByteBuf,
		MessageAssetReportC2SPayload
	> STREAM_CODEC = CustomPacketPayload.codec(
		MessageAssetReportC2SPayload::write,
		MessageAssetReportC2SPayload::new
	);

	public MessageAssetReportC2SPayload {
		if (reportSequence < 0L) {
			throw new IllegalArgumentException(
				"reportSequence must be non-negative"
			);
		}
		if (generation < 0L) {
			throw new IllegalArgumentException("generation must be non-negative");
		}
		if (manifestSequence < 0L) {
			throw new IllegalArgumentException(
				"manifestSequence must be non-negative"
			);
		}
		entries = List.copyOf(entries);
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException(
				"entries size exceeds " + MAX_ENTRIES
			);
		}
		Set<AssetKey> identities = new HashSet<>();
		for (Entry entry : entries) {
			if (!identities.add(entry.key())) {
				throw new IllegalArgumentException(
					"duplicate message asset report " + entry.type + ":" + entry.id
				);
			}
		}
	}

	/** Convenience constructor retained for catalog-template tests. */
	public MessageAssetReportC2SPayload(
		final long reportSequence,
		final long generation,
		final List<Entry> entries
	) {
		this(reportSequence, generation, 0L, entries);
	}

	private MessageAssetReportC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			decodeDocument(
				readByteArray(buffer, MAX_DOCUMENT_BYTES, "messageAssetReport")
			)
		);
	}

	private MessageAssetReportC2SPayload(final Decoded decoded) {
		this(
			decoded.reportSequence,
			decoded.generation,
			decoded.manifestSequence,
			decoded.entries
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, encodeDocument());
	}

	private byte[] encodeDocument() {
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.buffer());
		try {
			document.writeVarLong(this.reportSequence);
			document.writeVarLong(this.generation);
			document.writeVarLong(this.manifestSequence);
			document.writeVarInt(this.entries.size());
			for (Entry entry : this.entries) {
				entry.write(document);
			}
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded message asset report exceeds " + MAX_DOCUMENT_BYTES
				);
			}
			byte[] result = new byte[length];
			document.getBytes(document.readerIndex(), result);
			return result;
		} finally {
			document.release();
		}
	}

	private static Decoded decodeDocument(final byte[] encoded) {
		FriendlyByteBuf document = new FriendlyByteBuf(
			Unpooled.wrappedBuffer(encoded)
		);
		try {
			long reportSequence = document.readVarLong();
			long generation = document.readVarLong();
			long manifestSequence = document.readVarLong();
			int count = document.readVarInt();
			if (count < 0 || count > MAX_ENTRIES) {
				throw new DecoderException(
					"message asset report count " + count + " exceeds " + MAX_ENTRIES
				);
			}
			java.util.ArrayList<Entry> entries = new java.util.ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				entries.add(Entry.read(document));
			}
			if (document.isReadable()) {
				throw new DecoderException(
					"message asset report contains trailing data"
				);
			}
			return new Decoded(
				reportSequence,
				generation,
				manifestSequence,
				List.copyOf(entries)
			);
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message asset report", error);
		} finally {
			document.release();
		}
	}

	@Override
	public Type<MessageAssetReportC2SPayload> type() {
		return TYPE;
	}

	public enum Resolution {
		/** The requested identifier is loaded. */
		PRESENT,
		/** The requested identifier is absent and its declared fallback is loaded. */
		FALLBACK_USED,
		/** Neither a requested asset nor a usable declared fallback is loaded. */
		MISSING
	}

	public record Entry(AssetType type, Identifier id, Resolution resolution) {
		public Entry {
			type = Objects.requireNonNull(type, "type");
			id = requireIdentifier(id);
			resolution = Objects.requireNonNull(resolution, "resolution");
		}

		public AssetKey key() {
			return new AssetKey(this.type, this.id);
		}

		private static Entry read(final FriendlyByteBuf document) {
			return new Entry(
				readEnum(document, AssetType.values(), "asset type"),
				readIdentifier(document),
				readEnum(document, Resolution.values(), "asset resolution")
			);
		}

		private void write(final FriendlyByteBuf document) {
			document.writeVarInt(this.type.ordinal());
			document.writeUtf(
				this.id.toString(),
				MessageAssetManifestS2CPayload.MAX_IDENTIFIER_LENGTH
			);
			document.writeVarInt(this.resolution.ordinal());
		}
	}

	private static Identifier requireIdentifier(final Identifier identifier) {
		Objects.requireNonNull(identifier, "id");
		if (
			identifier.toString().length()
				> MessageAssetManifestS2CPayload.MAX_IDENTIFIER_LENGTH
		) {
			throw new IllegalArgumentException(
				"id length exceeds "
					+ MessageAssetManifestS2CPayload.MAX_IDENTIFIER_LENGTH
			);
		}
		return identifier;
	}

	private static Identifier readIdentifier(final FriendlyByteBuf document) {
		Identifier identifier = Identifier.tryParse(
			document.readUtf(
				MessageAssetManifestS2CPayload.MAX_IDENTIFIER_LENGTH
			)
		);
		if (identifier == null) {
			throw new DecoderException("asset id is not a valid identifier");
		}
		return identifier;
	}

	private static <T extends Enum<T>> T readEnum(
		final FriendlyByteBuf document,
		final T[] values,
		final String field
	) {
		int ordinal = document.readVarInt();
		if (ordinal < 0 || ordinal >= values.length) {
			throw new DecoderException("unknown " + field + " " + ordinal);
		}
		return values[ordinal];
	}

	private record Decoded(
		long reportSequence,
		long generation,
		long manifestSequence,
		List<Entry> entries
	) {
	}
}
