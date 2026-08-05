package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudFrameCodec.Header;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Complete atomic replacement of one player's authorized dock projection. */
public record HudReplaceS2CPayload(
	UUID gameInstanceId,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long sequence,
	byte[] snapshotJson
) implements CustomPacketPayload {
	public static final int MAX_SNAPSHOT_BYTES = HudFrameCodec.MAX_REPLACE_BYTES;
	public static final Type<HudReplaceS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("hud_replace_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HudReplaceS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HudReplaceS2CPayload::write, HudReplaceS2CPayload::new);

	public HudReplaceS2CPayload {
		HudFrameCodec.requireHeader(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
		snapshotJson = HudFrameCodec.requireDocument(
			snapshotJson,
			MAX_SNAPSHOT_BYTES,
			"snapshotJson"
		);
	}

	private HudReplaceS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(HudFrameCodec.readHeader(buffer), HudFrameCodec.readDocument(
			buffer,
			MAX_SNAPSHOT_BYTES,
			"snapshotJson"
		));
	}

	private HudReplaceS2CPayload(final Header header, final byte[] document) {
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
		HudFrameCodec.writeHeader(buffer, HudFrameCodec.requireHeader(
			this.gameInstanceId,
			this.definitionGeneration,
			this.epoch,
			this.contextVersion,
			this.sequence
		));
		HudFrameCodec.writeDocument(buffer, this.snapshotJson);
	}

	@Override
	public byte[] snapshotJson() {
		return this.snapshotJson.clone();
	}

	@Override
	public Type<HudReplaceS2CPayload> type() {
		return TYPE;
	}
}
