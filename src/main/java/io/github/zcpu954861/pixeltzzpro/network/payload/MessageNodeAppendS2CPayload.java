package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Strictly ordered, append-only reveal of newly authorized FORMAT 2 nodes.
 *
 * <p>This payload is the only legal way to reveal content whose condition is evaluated at
 * {@code node_start} or {@code field_capture}. It never replaces the plan, advances the refresh
 * cycle or restarts already visible nodes. The instance-local sequence is shared with field
 * deltas and controls.
 */
public record MessageNodeAppendS2CPayload(
	UUID instanceId,
	long generation,
	long sequence,
	List<ResolvedNode> nodes
) implements CustomPacketPayload {
	public static final int MAX_DOCUMENT_BYTES = MessagePlaybackPlan.MAX_PLAN_BYTES + 128;
	public static final Type<MessageNodeAppendS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_node_append_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, MessageNodeAppendS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			MessageNodeAppendS2CPayload::write,
			MessageNodeAppendS2CPayload::new
		);

	public MessageNodeAppendS2CPayload {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
		if (generation < 0L || sequence < 0L) {
			throw new IllegalArgumentException(
				"generation and sequence must be non-negative"
			);
		}
		nodes = MessagePlaybackPlan.decodeNodeFragment(
			MessagePlaybackPlan.encodeNodeFragment(nodes)
		);
	}

	private MessageNodeAppendS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(decodeDocument(readByteArray(buffer, MAX_DOCUMENT_BYTES, "messageNodeAppend")));
	}

	private MessageNodeAppendS2CPayload(final Decoded decoded) {
		this(decoded.instanceId, decoded.generation, decoded.sequence, decoded.nodes);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, encodeDocument());
	}

	public int encodedSize() {
		return encodeDocument().length;
	}

	private byte[] encodeDocument() {
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.buffer());
		try {
			document.writeUUID(this.instanceId);
			document.writeVarLong(this.generation);
			document.writeVarLong(this.sequence);
			document.writeByteArray(MessagePlaybackPlan.encodeNodeFragment(this.nodes));
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded node append exceeds " + MAX_DOCUMENT_BYTES
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
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
		try {
			UUID instanceId = document.readUUID();
			long generation = document.readVarLong();
			long sequence = document.readVarLong();
			List<ResolvedNode> nodes = MessagePlaybackPlan.decodeNodeFragment(
				document.readByteArray(MessagePlaybackPlan.MAX_PLAN_BYTES)
			);
			if (document.isReadable()) {
				throw new DecoderException("node append contains trailing data");
			}
			return new Decoded(instanceId, generation, sequence, nodes);
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message node append", error);
		} finally {
			document.release();
		}
	}

	@Override
	public Type<MessageNodeAppendS2CPayload> type() {
		return TYPE;
	}

	private record Decoded(
		UUID instanceId,
		long generation,
		long sequence,
		List<ResolvedNode> nodes
	) {
	}
}
