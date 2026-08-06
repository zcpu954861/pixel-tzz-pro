package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Throttled request for a complete authoritative countdown frame after a sequence gap. */
public record CountdownResyncRequestC2SPayload(
	UUID gameInstanceId,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long appliedSequence
) implements CustomPacketPayload {
	public static final Type<CountdownResyncRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("countdown_resync_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CountdownResyncRequestC2SPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			CountdownResyncRequestC2SPayload::write,
			CountdownResyncRequestC2SPayload::new
		);

	public CountdownResyncRequestC2SPayload {
		gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
		if (
			definitionGeneration < 0L
				|| epoch < 0L
				|| contextVersion < 0L
				|| appliedSequence < 0L
		) {
			throw new IllegalArgumentException("countdown resync versions must be non-negative");
		}
	}

	private CountdownResyncRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readVarLong()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.gameInstanceId);
		buffer.writeVarLong(this.definitionGeneration);
		buffer.writeVarLong(this.epoch);
		buffer.writeVarLong(this.contextVersion);
		buffer.writeVarLong(this.appliedSequence);
	}

	@Override
	public Type<CountdownResyncRequestC2SPayload> type() {
		return TYPE;
	}
}
