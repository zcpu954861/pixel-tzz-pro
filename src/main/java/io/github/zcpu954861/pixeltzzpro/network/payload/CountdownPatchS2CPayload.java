package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudFrameCodec.Header;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded value-only patch against one exact countdown sequence. */
public record CountdownPatchS2CPayload(
	UUID gameInstanceId,
	long definitionGeneration,
	long epoch,
	long contextVersion,
	long baseSequence,
	long sequence,
	byte[] patchJson
) implements CustomPacketPayload {
	public static final int MAX_PATCH_BYTES = HudFrameCodec.MAX_PATCH_BYTES;
	public static final Type<CountdownPatchS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("countdown_patch_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CountdownPatchS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(CountdownPatchS2CPayload::write, CountdownPatchS2CPayload::new);

	public CountdownPatchS2CPayload {
		HudFrameCodec.requireHeader(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
		if (baseSequence < 0L || baseSequence >= sequence) {
			throw new IllegalArgumentException("baseSequence must be non-negative and older than sequence");
		}
		patchJson = HudFrameCodec.requireDocument(patchJson, MAX_PATCH_BYTES, "patchJson");
	}

	private CountdownPatchS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			HudFrameCodec.readHeader(buffer),
			buffer.readVarLong(),
			HudFrameCodec.readDocument(buffer, MAX_PATCH_BYTES, "patchJson")
		);
	}

	private CountdownPatchS2CPayload(
		final Header header,
		final long baseSequence,
		final byte[] document
	) {
		this(
			header.gameInstanceId(),
			header.definitionGeneration(),
			header.epoch(),
			header.contextVersion(),
			baseSequence,
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
		buffer.writeVarLong(this.baseSequence);
		HudFrameCodec.writeDocument(buffer, this.patchJson);
	}

	@Override
	public byte[] patchJson() {
		return this.patchJson.clone();
	}

	@Override
	public Type<CountdownPatchS2CPayload> type() {
		return TYPE;
	}
}
