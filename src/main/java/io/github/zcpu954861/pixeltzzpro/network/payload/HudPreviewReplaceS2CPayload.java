package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Isolated local-preview projection; it never replaces the authoritative dock namespace. */
public record HudPreviewReplaceS2CPayload(
	UUID previewId,
	Surface surface,
	long epoch,
	long sequence,
	byte[] snapshotJson
) implements CustomPacketPayload {
	public static final int MAX_SNAPSHOT_BYTES = HudFrameCodec.MAX_REPLACE_BYTES;
	public static final Type<HudPreviewReplaceS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("hud_preview_replace_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HudPreviewReplaceS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HudPreviewReplaceS2CPayload::write, HudPreviewReplaceS2CPayload::new);

	public HudPreviewReplaceS2CPayload {
		previewId = Objects.requireNonNull(previewId, "previewId");
		surface = Objects.requireNonNull(surface, "surface");
		if (epoch < 0L || sequence < 0L) {
			throw new IllegalArgumentException("preview epoch and sequence must be non-negative");
		}
		snapshotJson = HudFrameCodec.requireDocument(
			snapshotJson,
			MAX_SNAPSHOT_BYTES,
			"snapshotJson"
		);
	}

	private HudPreviewReplaceS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			HudPreviewContract.byOrdinal(Surface.class, buffer.readVarInt(), "preview surface"),
			buffer.readVarLong(),
			buffer.readVarLong(),
			HudFrameCodec.readDocument(buffer, MAX_SNAPSHOT_BYTES, "snapshotJson")
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.previewId);
		buffer.writeVarInt(this.surface.ordinal());
		buffer.writeVarLong(this.epoch);
		buffer.writeVarLong(this.sequence);
		HudFrameCodec.writeDocument(buffer, this.snapshotJson);
	}

	@Override
	public byte[] snapshotJson() {
		return this.snapshotJson.clone();
	}

	@Override
	public Type<HudPreviewReplaceS2CPayload> type() {
		return TYPE;
	}
}
