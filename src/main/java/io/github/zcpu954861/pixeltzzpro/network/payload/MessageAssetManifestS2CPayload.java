package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bounded, non-sensitive client-resource preflight manifest for one definition generation.
 *
 * <p>The manifest contains only font and sound identifiers plus their already-public fallback
 * policy. Cue text, arguments, conditions, audience rules, callbacks, and resolved field values
 * never cross this packet. A server can therefore send it at connection time without revealing a
 * future cue. {@code manifestSequence} is scoped to one play connection: sequence 1 is the
 * connection/reload predeclaration and later sequences are cumulative cue-start extensions.
 */
public record MessageAssetManifestS2CPayload(
	long generation,
	long manifestSequence,
	List<Entry> entries
) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 64;
	public static final int MAX_IDENTIFIER_LENGTH = 256;
	public static final int MAX_DOCUMENT_BYTES = 48 * 1_024;
	public static final Type<MessageAssetManifestS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_asset_manifest_s2c")
	);
	public static final StreamCodec<
		RegistryFriendlyByteBuf,
		MessageAssetManifestS2CPayload
	> STREAM_CODEC = CustomPacketPayload.codec(
		MessageAssetManifestS2CPayload::write,
		MessageAssetManifestS2CPayload::new
	);

	public MessageAssetManifestS2CPayload {
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
					"duplicate message asset " + entry.type + ":" + entry.id
				);
			}
		}
	}

	/** Convenience constructor for immutable catalog templates and tests. */
	public MessageAssetManifestS2CPayload(
		final long generation,
		final List<Entry> entries
	) {
		this(generation, 0L, entries);
	}

	private MessageAssetManifestS2CPayload(
		final RegistryFriendlyByteBuf buffer
	) {
		this(
			decodeDocument(
				readByteArray(buffer, MAX_DOCUMENT_BYTES, "messageAssetManifest")
			)
		);
	}

	private MessageAssetManifestS2CPayload(final Decoded decoded) {
		this(decoded.generation, decoded.manifestSequence, decoded.entries);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, encodeDocument());
	}

	private byte[] encodeDocument() {
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.buffer());
		try {
			document.writeVarLong(this.generation);
			document.writeVarLong(this.manifestSequence);
			document.writeVarInt(this.entries.size());
			for (Entry entry : this.entries) {
				entry.write(document);
			}
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded message asset manifest exceeds " + MAX_DOCUMENT_BYTES
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
			long generation = document.readVarLong();
			long manifestSequence = document.readVarLong();
			int count = document.readVarInt();
			if (count < 0 || count > MAX_ENTRIES) {
				throw new DecoderException(
					"message asset count " + count + " exceeds " + MAX_ENTRIES
				);
			}
			java.util.ArrayList<Entry> entries = new java.util.ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				entries.add(Entry.read(document));
			}
			if (document.isReadable()) {
				throw new DecoderException(
					"message asset manifest contains trailing data"
				);
			}
			return new Decoded(
				generation,
				manifestSequence,
				List.copyOf(entries)
			);
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message asset manifest", error);
		} finally {
			document.release();
		}
	}

	@Override
	public Type<MessageAssetManifestS2CPayload> type() {
		return TYPE;
	}

	public enum AssetType {
		FONT,
		SOUND
	}

	public enum MissingMode {
		SILENT,
		FALLBACK,
		BLOCK_START
	}

	public record AssetKey(AssetType type, Identifier id) {
		public AssetKey {
			type = Objects.requireNonNull(type, "type");
			id = requireIdentifier(id, "id");
		}
	}

	public record Entry(
		AssetType type,
		Identifier id,
		MissingMode missing,
		Optional<Identifier> fallback,
		boolean preload,
		boolean requiredForStart
	) {
		public Entry {
			type = Objects.requireNonNull(type, "type");
			id = requireIdentifier(id, "id");
			missing = Objects.requireNonNull(missing, "missing");
			fallback = Objects.requireNonNull(fallback, "fallback")
				.map(value -> requireIdentifier(value, "fallback"));
			if ((missing == MissingMode.FALLBACK) != fallback.isPresent()) {
				throw new IllegalArgumentException(
					"fallback must be present exactly when missing mode is FALLBACK"
				);
			}
			if (missing == MissingMode.BLOCK_START && !requiredForStart) {
				throw new IllegalArgumentException(
					"BLOCK_START assets must be required for start"
				);
			}
		}

		/** Compatibility constructor for declarations that do not request eager preloading. */
		public Entry(
			final AssetType type,
			final Identifier id,
			final MissingMode missing,
			final Optional<Identifier> fallback,
			final boolean requiredForStart
		) {
			this(type, id, missing, fallback, false, requiredForStart);
		}

		public AssetKey key() {
			return new AssetKey(this.type, this.id);
		}

		private static Entry read(final FriendlyByteBuf document) {
			AssetType type = readEnum(document, AssetType.values(), "asset type");
			Identifier id = readIdentifier(document, "asset id");
			MissingMode missing = readEnum(
				document,
				MissingMode.values(),
				"missing mode"
			);
			Optional<Identifier> fallback = document.readBoolean()
				? Optional.of(readIdentifier(document, "fallback"))
				: Optional.empty();
			return new Entry(
				type,
				id,
				missing,
				fallback,
				document.readBoolean(),
				document.readBoolean()
			);
		}

		private void write(final FriendlyByteBuf document) {
			document.writeVarInt(this.type.ordinal());
			document.writeUtf(this.id.toString(), MAX_IDENTIFIER_LENGTH);
			document.writeVarInt(this.missing.ordinal());
			document.writeBoolean(this.fallback.isPresent());
			this.fallback.ifPresent(
				value -> document.writeUtf(value.toString(), MAX_IDENTIFIER_LENGTH)
			);
			document.writeBoolean(this.preload);
			document.writeBoolean(this.requiredForStart);
		}
	}

	private static Identifier requireIdentifier(
		final Identifier identifier,
		final String field
	) {
		Objects.requireNonNull(identifier, field);
		if (identifier.toString().length() > MAX_IDENTIFIER_LENGTH) {
			throw new IllegalArgumentException(
				field + " length exceeds " + MAX_IDENTIFIER_LENGTH
			);
		}
		return identifier;
	}

	private static Identifier readIdentifier(
		final FriendlyByteBuf document,
		final String field
	) {
		Identifier identifier = Identifier.tryParse(
			document.readUtf(MAX_IDENTIFIER_LENGTH)
		);
		if (identifier == null) {
			throw new DecoderException(field + " is not a valid identifier");
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
		long generation,
		long manifestSequence,
		List<Entry> entries
	) {
	}
}
