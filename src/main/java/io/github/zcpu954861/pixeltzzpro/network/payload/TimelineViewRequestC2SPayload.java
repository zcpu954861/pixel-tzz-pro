package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Requests the caller's server-filtered timeline projection.
 *
 * <p>The request deliberately carries no view mode or player identity. The connection and
 * authoritative world state decide whether the response is a host timeline, a terminal
 * participant recap, or an unavailable shell.</p>
 */
public record TimelineViewRequestC2SPayload(
	int protocolVersion,
	long requestSequence
) implements CustomPacketPayload {
	public static final Type<TimelineViewRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("timeline_view_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TimelineViewRequestC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(TimelineViewRequestC2SPayload::write, TimelineViewRequestC2SPayload::new);

	public TimelineViewRequestC2SPayload {
		if (protocolVersion < 0 || requestSequence <= 0L) {
			throw new IllegalArgumentException("timeline view request counters are invalid");
		}
	}

	private TimelineViewRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readVarLong());
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
	}

	@Override
	public Type<TimelineViewRequestC2SPayload> type() {
		return TYPE;
	}
}
