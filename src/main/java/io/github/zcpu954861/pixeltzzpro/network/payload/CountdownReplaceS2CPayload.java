package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownFrameCodec.Header;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Complete atomic replacement of one player's authorized countdown projection. */
public record CountdownReplaceS2CPayload(
	UUID gameInstanceId,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long sequence,
	byte[] snapshotJson
) implements CustomPacketPayload {
	public static final int MAX_SNAPSHOT_BYTES = CountdownFrameCodec.MAX_REPLACE_BYTES;
	public static final Type<CountdownReplaceS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("countdown_replace_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CountdownReplaceS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(CountdownReplaceS2CPayload::write, CountdownReplaceS2CPayload::new);

	public CountdownReplaceS2CPayload {
		CountdownFrameCodec.requireHeader(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
		snapshotJson = CountdownFrameCodec.requireDocument(
			snapshotJson,
			MAX_SNAPSHOT_BYTES,
			"snapshotJson"
		);
	}

	private CountdownReplaceS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(CountdownFrameCodec.readHeader(buffer), CountdownFrameCodec.readDocument(
			buffer,
			MAX_SNAPSHOT_BYTES,
			"snapshotJson"
		));
	}

	private CountdownReplaceS2CPayload(final Header header, final byte[] document) {
		this(
			header.gameInstanceId(),
			header.definitionGeneration(),
			header.epoch(),
			header.contextVersion(),
			header.sequence(),
			document
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		CountdownFrameCodec.writeHeader(buffer, CountdownFrameCodec.requireHeader(
			this.gameInstanceId,
			this.definitionGeneration,
			this.epoch,
			this.contextVersion,
			this.sequence
		));
		CountdownFrameCodec.writeDocument(buffer, this.snapshotJson);
	}

	@Override
	public byte[] snapshotJson() {
		return this.snapshotJson.clone();
	}

	@Override
	public Type<CountdownReplaceS2CPayload> type() {
		return TYPE;
	}
}
