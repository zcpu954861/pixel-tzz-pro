package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Authoritative result for a console or forced-flow request.
 */
public record OperationResultS2CPayload(
	int protocolVersion,
	long requestSequence,
	OperationCode code,
	String message,
	long stateRevision,
	long definitionGeneration,
	boolean refreshConsole
) implements CustomPacketPayload {
	public static final int MAX_CODE_LENGTH = 64;
	public static final int MAX_MESSAGE_LENGTH = 1_024;
	public static final Type<OperationResultS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("operation_result_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, OperationResultS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(OperationResultS2CPayload::write, OperationResultS2CPayload::new);

	public OperationResultS2CPayload {
		if (protocolVersion < 0 || requestSequence < 0L) {
			throw new IllegalArgumentException("protocolVersion and requestSequence must be non-negative");
		}
		code = Objects.requireNonNull(code, "code");
		message = requireBounded(message, MAX_MESSAGE_LENGTH, "message");
		if (stateRevision < 0L || definitionGeneration < 0L) {
			throw new IllegalArgumentException("revision and generation must be non-negative");
		}
	}

	private OperationResultS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readVarLong(),
			readCode(buffer),
			buffer.readUtf(MAX_MESSAGE_LENGTH),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readBoolean()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeUtf(this.code.serializedName(), MAX_CODE_LENGTH);
		buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.definitionGeneration);
		buffer.writeBoolean(this.refreshConsole);
	}

	@Override
	public Type<OperationResultS2CPayload> type() {
		return TYPE;
	}

	public boolean success() {
		return this.code == OperationCode.SUCCESS;
	}

	private static OperationCode readCode(final RegistryFriendlyByteBuf buffer) {
		String name = buffer.readUtf(MAX_CODE_LENGTH);
		return OperationCode.bySerializedName(name)
			.orElseThrow(() -> new IllegalArgumentException("unknown operation code " + name));
	}
}
