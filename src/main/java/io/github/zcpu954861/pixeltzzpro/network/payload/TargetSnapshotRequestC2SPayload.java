package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Requests the authoritative eligible-target snapshot for one visible console operation.
 */
public record TargetSnapshotRequestC2SPayload(
	StatefulRequest request,
	String operationType,
	Identifier operationId
) implements CustomPacketPayload {
	public static final int MAX_OPERATION_TYPE_LENGTH = 64;
	public static final Type<TargetSnapshotRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("target_snapshot_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TargetSnapshotRequestC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(
			TargetSnapshotRequestC2SPayload::write,
			TargetSnapshotRequestC2SPayload::new
		);

	public TargetSnapshotRequestC2SPayload {
		request = Objects.requireNonNull(request, "request");
		operationType = requireBounded(operationType, MAX_OPERATION_TYPE_LENGTH, "operationType");
		if (
			operationType.isBlank()
				|| !operationType.equals(operationType.toLowerCase(Locale.ROOT))
		) {
			throw new IllegalArgumentException("operationType must be non-blank lowercase text");
		}
		operationId = requireIdentifier(operationId, "operationId");
	}

	private TargetSnapshotRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			StatefulRequest.read(buffer),
			buffer.readUtf(MAX_OPERATION_TYPE_LENGTH),
			readIdentifier(buffer, "operationId")
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		this.request.write(buffer);
		buffer.writeUtf(this.operationType, MAX_OPERATION_TYPE_LENGTH);
		writeIdentifier(buffer, this.operationId);
	}

	@Override
	public Type<TargetSnapshotRequestC2SPayload> type() {
		return TYPE;
	}
}
