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
 * The only server signal that releases a matching forced page without a replacement bundle.
 */
public record ForcedPageReleaseS2CPayload(
	int protocolVersion,
	UUID flowInstanceId,
	UUID pageInstanceId,
	String reason,
	long stateRevision
) implements CustomPacketPayload {
	public static final int MAX_REASON_LENGTH = 64;
	public static final Type<ForcedPageReleaseS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("forced_page_release_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ForcedPageReleaseS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(
			ForcedPageReleaseS2CPayload::write,
			ForcedPageReleaseS2CPayload::new
		);

	public ForcedPageReleaseS2CPayload {
		if (protocolVersion < 0 || stateRevision < 0L) {
			throw new IllegalArgumentException("protocolVersion and stateRevision must be non-negative");
		}
		flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
		pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
		reason = requireBounded(reason, MAX_REASON_LENGTH, "reason");
		if (
			reason.isBlank()
				|| !reason.equals(reason.toLowerCase(Locale.ROOT))
				|| !reason.matches("[a-z0-9_.-]+")
		) {
			throw new IllegalArgumentException("reason must match [a-z0-9_.-]+");
		}
	}

	private ForcedPageReleaseS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readUUID(),
			buffer.readUUID(),
			buffer.readUtf(MAX_REASON_LENGTH),
			buffer.readLong()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeUUID(this.flowInstanceId);
		buffer.writeUUID(this.pageInstanceId);
		buffer.writeUtf(this.reason, MAX_REASON_LENGTH);
		buffer.writeLong(this.stateRevision);
	}

	@Override
	public Type<ForcedPageReleaseS2CPayload> type() {
		return TYPE;
	}
}
