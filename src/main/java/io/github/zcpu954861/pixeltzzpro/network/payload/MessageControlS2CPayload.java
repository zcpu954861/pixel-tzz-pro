package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
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
 * Authoritative pause/resume/finalize/finish/cancel transition for one message instance.
 *
 * <p>FINALIZE and FINISH carry the final or registered fallback component for every
 * still-unlocked slot. FINALIZE collapses the local presentation to its final frame immediately;
 * FINISH closes the authoritative stream while allowing already-authorized client presentation
 * work to drain normally. Other controls are forbidden from carrying field values.
 */
public record MessageControlS2CPayload(
	UUID instanceId,
	long generation,
	long sequence,
	long effectiveClock,
	Action action,
	List<FieldValue> finalValues
) implements CustomPacketPayload {
	public static final int MAX_DOCUMENT_BYTES = MessageFieldDeltaS2CPayload.MAX_DOCUMENT_BYTES;
	public static final Type<MessageControlS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_control_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, MessageControlS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			MessageControlS2CPayload::write,
			MessageControlS2CPayload::new
		);

	public MessageControlS2CPayload {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
		if (generation < 0L || sequence < 0L || effectiveClock < 0L) {
			throw new IllegalArgumentException(
				"generation, sequence, and effectiveClock must be non-negative"
			);
		}
		action = Objects.requireNonNull(action, "action");
		finalValues = List.copyOf(finalValues);
		if (finalValues.size() > MessagePlaybackPlan.MAX_FIELD_SLOTS) {
			throw new IllegalArgumentException(
				"finalValues exceeds " + MessagePlaybackPlan.MAX_FIELD_SLOTS
			);
		}
		if (!action.finalizes() && !finalValues.isEmpty()) {
			throw new IllegalArgumentException(
				"only FINALIZE or FINISH may carry final field values"
			);
		}
		Set<Integer> slots = new HashSet<>();
		long estimatedBytes = 64L;
		for (FieldValue value : finalValues) {
			Objects.requireNonNull(value, "final field value");
			if (!slots.add(value.slot())) {
				throw new IllegalArgumentException(
					"final control cannot repeat an opaque slot"
				);
			}
			estimatedBytes += 16L + value.component().utf8Bytes();
		}
		if (estimatedBytes > MAX_DOCUMENT_BYTES) {
			throw new IllegalArgumentException(
				"final control exceeds " + MAX_DOCUMENT_BYTES + " encoded bytes"
			);
		}
	}

	private MessageControlS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(decodeDocument(readByteArray(buffer, MAX_DOCUMENT_BYTES, "messageControl")));
	}

	private MessageControlS2CPayload(final Decoded decoded) {
		this(
			decoded.instanceId,
			decoded.generation,
			decoded.sequence,
			decoded.effectiveClock,
			decoded.action,
			decoded.finalValues
		);
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
			document.writeVarLong(this.effectiveClock);
			document.writeVarInt(this.action.ordinal());
			document.writeVarInt(this.finalValues.size());
			for (FieldValue value : this.finalValues) {
				document.writeVarInt(value.slot());
				document.writeUtf(
					value.component().canonicalJson(),
					MessagePlaybackPlan.MAX_COMPONENT_CHARACTERS
				);
			}
			int length = document.readableBytes();
			if (length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException(
					"encoded message control exceeds " + MAX_DOCUMENT_BYTES
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
			long effectiveClock = document.readVarLong();
			Action action = readAction(document);
			int count = document.readVarInt();
			if (count < 0 || count > MessagePlaybackPlan.MAX_FIELD_SLOTS) {
				throw new DecoderException(
					"final value count exceeds " + MessagePlaybackPlan.MAX_FIELD_SLOTS
				);
			}
			List<FieldValue> values = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				values.add(
					new FieldValue(
						document.readVarInt(),
						MessagePlaybackPlan.ResolvedComponent.parseCanonical(
							document.readUtf(
								MessagePlaybackPlan.MAX_COMPONENT_CHARACTERS
							)
						)
					)
				);
			}
			if (document.isReadable()) {
				throw new DecoderException("message control contains trailing data");
			}
			return new Decoded(
				instanceId,
				generation,
				sequence,
				effectiveClock,
				action,
				values
			);
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message control", error);
		} finally {
			document.release();
		}
	}

	private static Action readAction(final FriendlyByteBuf buffer) {
		int ordinal = buffer.readVarInt();
		if (ordinal < 0 || ordinal >= Action.values().length) {
			throw new DecoderException("unknown message control action " + ordinal);
		}
		return Action.values()[ordinal];
	}

	@Override
	public Type<MessageControlS2CPayload> type() {
		return TYPE;
	}

	public enum Action {
		PAUSE,
		RESUME,
		FINALIZE,
		CANCEL,
		/** Authoritative natural completion that must not consume queued client animation time. */
		FINISH;

		public boolean finalizes() {
			return this == FINALIZE || this == FINISH;
		}

		public boolean drainsPresentation() {
			return this == FINISH;
		}
	}

	private record Decoded(
		UUID instanceId,
		long generation,
		long sequence,
		long effectiveClock,
		Action action,
		List<FieldValue> finalValues
	) {
	}
}
