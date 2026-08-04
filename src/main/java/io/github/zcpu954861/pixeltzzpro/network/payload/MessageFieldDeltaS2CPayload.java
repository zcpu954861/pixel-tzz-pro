package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Final resolved values for opaque field slots in one plan.
 *
 * <p>The sequence is instance-local and must be the immediate successor of the prior plan,
 * delta, or control sequence. The bounded client inbox enforces that ordering independently of
 * whether the renderer has consumed the plan yet.
 */
public record MessageFieldDeltaS2CPayload(
	UUID instanceId,
	long generation,
	long sequence,
	List<FieldValue> values
) implements CustomPacketPayload {
	public static final int MAX_VALUES = MessagePlaybackPlan.MAX_FIELD_SLOTS;
	public static final int MAX_DOCUMENT_BYTES = 128 * 1024;
	public static final Type<MessageFieldDeltaS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_field_delta_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, MessageFieldDeltaS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			MessageFieldDeltaS2CPayload::write,
			MessageFieldDeltaS2CPayload::new
		);

	public MessageFieldDeltaS2CPayload {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
		if (generation < 0L || sequence < 0L) {
			throw new IllegalArgumentException(
				"generation and sequence must be non-negative"
			);
		}
		values = List.copyOf(values);
		if (values.isEmpty() || values.size() > MAX_VALUES) {
			throw new IllegalArgumentException(
				"field delta size must be between 1 and " + MAX_VALUES
			);
		}
		Set<Integer> slots = new HashSet<>();
		long estimatedBytes = 64L;
		for (FieldValue value : values) {
			Objects.requireNonNull(value, "field value");
			if (!slots.add(value.slot())) {
				throw new IllegalArgumentException(
					"field delta cannot repeat an opaque slot"
				);
			}
			estimatedBytes += 16L + value.component().utf8Bytes();
		}
		if (estimatedBytes > MAX_DOCUMENT_BYTES) {
			throw new IllegalArgumentException(
				"field delta exceeds " + MAX_DOCUMENT_BYTES + " encoded bytes"
			);
		}
	}

	private MessageFieldDeltaS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(decodeDocument(readByteArray(buffer, MAX_DOCUMENT_BYTES, "messageFieldDelta")));
	}

	private MessageFieldDeltaS2CPayload(final Decoded decoded) {
		this(decoded.instanceId, decoded.generation, decoded.sequence, decoded.values);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, encodeDocument());
	}

	private byte[] encodeDocument() {
		FriendlyByteBuf document = new FriendlyByteBuf(Unpooled.buffer());
		try {
			document.writeUUID(this.instanceId);
			document.writeVarLong(this.generation);
			document.writeVarLong(this.sequence);
			document.writeVarInt(this.values.size());
			for (FieldValue value : this.values) {
				document.writeVarInt(value.slot());
				document.writeUtf(
					value.component().canonicalJson(),
					MessagePlaybackPlan.MAX_COMPONENT_CHARACTERS
				);
			}
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded field delta exceeds " + MAX_DOCUMENT_BYTES
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
			int count = document.readVarInt();
			if (count < 1 || count > MAX_VALUES) {
				throw new DecoderException(
					"field delta count " + count + " exceeds " + MAX_VALUES
				);
			}
			List<FieldValue> values = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				values.add(
					new FieldValue(
						document.readVarInt(),
						ResolvedComponent.parseCanonical(
							document.readUtf(
								MessagePlaybackPlan.MAX_COMPONENT_CHARACTERS
							)
						)
					)
				);
			}
			if (document.isReadable()) {
				throw new DecoderException("field delta contains trailing data");
			}
			return new Decoded(instanceId, generation, sequence, values);
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message field delta", error);
		} finally {
			document.release();
		}
	}

	@Override
	public Type<MessageFieldDeltaS2CPayload> type() {
		return TYPE;
	}

	public record FieldValue(int slot, ResolvedComponent component) {
		public FieldValue {
			if (slot < 0 || slot >= MessagePlaybackPlan.MAX_FIELD_SLOTS) {
				throw new IllegalArgumentException(
					"slot must be between 0 and "
						+ (MessagePlaybackPlan.MAX_FIELD_SLOTS - 1)
				);
			}
			component = Objects.requireNonNull(component, "component");
		}
	}

	private record Decoded(
		UUID instanceId,
		long generation,
		long sequence,
		List<FieldValue> values
	) {
	}
}
