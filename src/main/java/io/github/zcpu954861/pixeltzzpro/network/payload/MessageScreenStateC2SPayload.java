package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Coarse client screen state for presentation-only start and pause policies.
 *
 * <p>The report intentionally does not expose a screen class, page identifier, player-selected
 * target, or any authority-bearing state. It cannot delay the server callback clock.
 */
public record MessageScreenStateC2SPayload(
	long reportSequence,
	Surface surface,
	boolean worldPaused,
	boolean resourceReloading,
	boolean chatVisible
) implements CustomPacketPayload {
	public static final int MAX_DOCUMENT_BYTES = 64;
	public static final Type<MessageScreenStateC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_screen_state_c2s")
	);
	public static final StreamCodec<
		RegistryFriendlyByteBuf,
		MessageScreenStateC2SPayload
	> STREAM_CODEC = CustomPacketPayload.codec(
		MessageScreenStateC2SPayload::write,
		MessageScreenStateC2SPayload::new
	);

	public MessageScreenStateC2SPayload {
		if (reportSequence < 0L) {
			throw new IllegalArgumentException("reportSequence must be non-negative");
		}
		surface = Objects.requireNonNull(surface, "surface");
	}

	private MessageScreenStateC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			decodeDocument(
				readByteArray(
					buffer,
					MAX_DOCUMENT_BYTES,
					"messageScreenState"
				)
			)
		);
	}

	private MessageScreenStateC2SPayload(final Decoded decoded) {
		this(
			decoded.reportSequence,
			decoded.surface,
			decoded.worldPaused,
			decoded.resourceReloading,
			decoded.chatVisible
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, encodeDocument());
	}

	private byte[] encodeDocument() {
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.buffer());
		try {
			document.writeVarLong(this.reportSequence);
			document.writeVarInt(this.surface.ordinal());
			document.writeBoolean(this.worldPaused);
			document.writeBoolean(this.resourceReloading);
			document.writeBoolean(this.chatVisible);
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded message screen state exceeds " + MAX_DOCUMENT_BYTES
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
			int surfaceOrdinal = document.readVarInt();
			if (
				surfaceOrdinal < 0
					|| surfaceOrdinal >= Surface.values().length
			) {
				throw new DecoderException(
					"unknown message screen surface " + surfaceOrdinal
				);
			}
			Decoded decoded = new Decoded(
				reportSequence,
				Surface.values()[surfaceOrdinal],
				document.readBoolean(),
				document.readBoolean(),
				document.readBoolean()
			);
			if (document.isReadable()) {
				throw new DecoderException(
					"message screen state contains trailing data"
				);
			}
			return decoded;
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message screen state", error);
		} finally {
			document.release();
		}
	}

	@Override
	public Type<MessageScreenStateC2SPayload> type() {
		return TYPE;
	}

	public enum Surface {
		/**
		 * No menu is covering the in-world presentation surface.
		 */
		GAMEPLAY,
		/**
		 * The vanilla pause or escape menu is visible.
		 */
		PAUSE_MENU,
		/**
		 * A non-forced Pixel TZZ screen is visible.
		 */
		MOD_SCREEN,
		/**
		 * A forced Pixel TZZ flow is visible and cannot be closed by a message.
		 */
		FORCED_MOD_SCREEN,
		/**
		 * Another GUI is visible; no implementation class name is sent.
		 */
		OTHER_SCREEN
	}

	private record Decoded(
		long reportSequence,
		Surface surface,
		boolean worldPaused,
		boolean resourceReloading,
		boolean chatVisible
	) {
	}
}
