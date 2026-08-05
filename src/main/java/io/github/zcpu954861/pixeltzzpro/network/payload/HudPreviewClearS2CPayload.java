package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.ClearReason;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Clears one isolated preview surface without touching its formal HUD namespace. */
public record HudPreviewClearS2CPayload(
	UUID previewId,
	Surface surface,
	long epoch,
	long sequence,
	ClearReason reason
) implements CustomPacketPayload {
	public static final Type<HudPreviewClearS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("hud_preview_clear_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HudPreviewClearS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HudPreviewClearS2CPayload::write, HudPreviewClearS2CPayload::new);

	public HudPreviewClearS2CPayload {
		previewId = Objects.requireNonNull(previewId, "previewId");
		surface = Objects.requireNonNull(surface, "surface");
		reason = Objects.requireNonNull(reason, "reason");
		if (epoch < 0L || sequence < 0L) {
			throw new IllegalArgumentException("preview clear counters must be non-negative");
		}
	}

	private HudPreviewClearS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			HudPreviewContract.byOrdinal(Surface.class, buffer.readVarInt(), "preview surface"),
			buffer.readVarLong(),
			buffer.readVarLong(),
			HudPreviewContract.byOrdinal(ClearReason.class, buffer.readVarInt(), "preview clear reason")
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.previewId);
		buffer.writeVarInt(this.surface.ordinal());
		buffer.writeVarLong(this.epoch);
		buffer.writeVarLong(this.sequence);
		buffer.writeVarInt(this.reason.ordinal());
	}

	@Override
	public Type<HudPreviewClearS2CPayload> type() {
		return TYPE;
	}
}
