package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-authored terminal invalidation for an already open NORMAL page instance.
 */
public record TerminalInvalidationS2CPayload(
	UUID sessionId,
	UUID pageInstanceId,
	long stateRevision,
	String reason,
	String message
) implements CustomPacketPayload {
	public static final int MAX_REASON_LENGTH = 64;
	public static final int MAX_MESSAGE_LENGTH = 1_024;
	public static final Type<TerminalInvalidationS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("terminal_invalidation_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalInvalidationS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			TerminalInvalidationS2CPayload::write,
			TerminalInvalidationS2CPayload::new
		);

	public TerminalInvalidationS2CPayload {
		sessionId = Objects.requireNonNull(sessionId, "sessionId");
		pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
		if (stateRevision < 0L) {
			throw new IllegalArgumentException("stateRevision must be non-negative");
		}
		reason = requireBounded(reason, MAX_REASON_LENGTH, "reason");
		if (
			reason.isBlank()
				|| !reason.equals(reason.toLowerCase(Locale.ROOT))
				|| !reason.matches("[a-z0-9_.-]+")
		) {
			throw new IllegalArgumentException("reason must match [a-z0-9_.-]+");
		}
		message = requireBounded(message, MAX_MESSAGE_LENGTH, "message");
		if (message.isBlank()) {
			throw new IllegalArgumentException("message must not be blank");
		}
	}

	private TerminalInvalidationS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			buffer.readUUID(),
			buffer.readLong(),
			buffer.readUtf(MAX_REASON_LENGTH),
			buffer.readUtf(MAX_MESSAGE_LENGTH)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.sessionId);
		buffer.writeUUID(this.pageInstanceId);
		buffer.writeLong(this.stateRevision);
		buffer.writeUtf(this.reason, MAX_REASON_LENGTH);
		buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
	}

	@Override
	public Type<TerminalInvalidationS2CPayload> type() {
		return TYPE;
	}
}
