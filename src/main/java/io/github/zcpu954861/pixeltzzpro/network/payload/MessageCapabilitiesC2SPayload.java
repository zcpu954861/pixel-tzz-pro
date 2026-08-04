package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Bounded client rendering capabilities used only to select a safe presentation fallback.
 *
 * <p>This report is deliberately incapable of granting permission, choosing an audience,
 * resolving a field, or advancing a callback. The server may only use it to replace unsupported
 * animation with an already-authorized static projection.
 */
public record MessageCapabilitiesC2SPayload(
	long reportSequence,
	int planFormatVersion,
	ChatUpdateMode chatUpdateMode,
	boolean subTickAnimation,
	boolean advancedTextLayout
) implements CustomPacketPayload {
	public static final int MAX_DOCUMENT_BYTES = 64;
	public static final int MAX_PLAN_FORMAT_VERSION = 64;
	public static final Type<MessageCapabilitiesC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_capabilities_c2s")
	);
	public static final StreamCodec<
		RegistryFriendlyByteBuf,
		MessageCapabilitiesC2SPayload
	> STREAM_CODEC = CustomPacketPayload.codec(
		MessageCapabilitiesC2SPayload::write,
		MessageCapabilitiesC2SPayload::new
	);

	public MessageCapabilitiesC2SPayload {
		if (reportSequence < 0L) {
			throw new IllegalArgumentException("reportSequence must be non-negative");
		}
		if (
			planFormatVersion < 1
				|| planFormatVersion > MAX_PLAN_FORMAT_VERSION
		) {
			throw new IllegalArgumentException(
				"planFormatVersion must remain within 1.."
					+ MAX_PLAN_FORMAT_VERSION
			);
		}
		chatUpdateMode = Objects.requireNonNull(chatUpdateMode, "chatUpdateMode");
	}

	public static MessageCapabilitiesC2SPayload current(
		final long reportSequence,
		final ChatUpdateMode chatUpdateMode
	) {
		return new MessageCapabilitiesC2SPayload(
			reportSequence,
			MessagePlaybackPlan.FORMAT_VERSION,
			chatUpdateMode,
			false,
			true
		);
	}

	private MessageCapabilitiesC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			decodeDocument(
				readByteArray(
					buffer,
					MAX_DOCUMENT_BYTES,
					"messageCapabilities"
				)
			)
		);
	}

	private MessageCapabilitiesC2SPayload(final Decoded decoded) {
		this(
			decoded.reportSequence,
			decoded.planFormatVersion,
			decoded.chatUpdateMode,
			decoded.subTickAnimation,
			decoded.advancedTextLayout
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, encodeDocument());
	}

	private byte[] encodeDocument() {
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.buffer());
		try {
			document.writeVarLong(this.reportSequence);
			document.writeVarInt(this.planFormatVersion);
			document.writeVarInt(this.chatUpdateMode.ordinal());
			document.writeBoolean(this.subTickAnimation);
			document.writeBoolean(this.advancedTextLayout);
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded message capabilities exceeds " + MAX_DOCUMENT_BYTES
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
			int planFormatVersion = document.readVarInt();
			int modeOrdinal = document.readVarInt();
			if (
				modeOrdinal < 0
					|| modeOrdinal >= ChatUpdateMode.values().length
			) {
				throw new DecoderException(
					"unknown chat update mode " + modeOrdinal
				);
			}
			Decoded decoded = new Decoded(
				reportSequence,
				planFormatVersion,
				ChatUpdateMode.values()[modeOrdinal],
				document.readBoolean(),
				document.readBoolean()
			);
			if (document.isReadable()) {
				throw new DecoderException(
					"message capabilities contains trailing data"
				);
			}
			return decoded;
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message capabilities", error);
		} finally {
			document.release();
		}
	}

	@Override
	public Type<MessageCapabilitiesC2SPayload> type() {
		return TYPE;
	}

	public enum ChatUpdateMode {
		/**
		 * Insert one authorized final component and never rewrite the logical entry.
		 */
		STATIC_ONLY,
		/**
		 * Preserve one logical chat entry while replacing its rendered frame in place.
		 */
		IN_PLACE
	}

	private record Decoded(
		long reportSequence,
		int planFormatVersion,
		ChatUpdateMode chatUpdateMode,
		boolean subTickAnimation,
		boolean advancedTextLayout
	) {
	}
}
