package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One deduplicated opening-countdown checkpoint sound.
 *
 * <p>The client applies its independent countdown volume after validating the frozen instance and
 * checkpoint identity. This intentionally does not enter the V3B message scheduler.</p>
 */
public record CountdownCheckpointSoundS2CPayload(
	UUID countdownInstanceId,
	long stateVersion,
	String checkpointId,
	Identifier soundId,
	float volume,
	float pitch
) implements CustomPacketPayload {
	/** Matches the persisted/data-pack local-ID contract. */
	public static final int MAX_CHECKPOINT_ID_LENGTH = 128;
	public static final Type<CountdownCheckpointSoundS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("countdown_checkpoint_sound_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CountdownCheckpointSoundS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			CountdownCheckpointSoundS2CPayload::write,
			CountdownCheckpointSoundS2CPayload::new
		);

	public CountdownCheckpointSoundS2CPayload {
		countdownInstanceId = Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
		if (stateVersion < 0L) {
			throw new IllegalArgumentException("countdown checkpoint stateVersion must be non-negative");
		}
		checkpointId = requireBounded(
			checkpointId,
			MAX_CHECKPOINT_ID_LENGTH,
			"checkpointId"
		);
		if (checkpointId.isBlank()) {
			throw new IllegalArgumentException("countdown checkpoint ID cannot be blank");
		}
		soundId = Objects.requireNonNull(soundId, "soundId");
		if (!Float.isFinite(volume) || volume < 0.0F || volume > 4.0F) {
			throw new IllegalArgumentException("countdown checkpoint volume must be between 0 and 4");
		}
		if (!Float.isFinite(pitch) || pitch < 0.5F || pitch > 2.0F) {
			throw new IllegalArgumentException("countdown checkpoint pitch must be between 0.5 and 2");
		}
	}

	private CountdownCheckpointSoundS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			buffer.readVarLong(),
			buffer.readUtf(MAX_CHECKPOINT_ID_LENGTH),
			readIdentifier(buffer, "soundId"),
			buffer.readFloat(),
			buffer.readFloat()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.countdownInstanceId);
		buffer.writeVarLong(this.stateVersion);
		buffer.writeUtf(this.checkpointId, MAX_CHECKPOINT_ID_LENGTH);
		writeIdentifier(buffer, this.soundId);
		buffer.writeFloat(this.volume);
		buffer.writeFloat(this.pitch);
	}

	@Override
	public Type<CountdownCheckpointSoundS2CPayload> type() {
		return TYPE;
	}
}
