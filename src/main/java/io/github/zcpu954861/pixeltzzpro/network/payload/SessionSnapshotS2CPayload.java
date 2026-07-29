package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SessionSnapshotS2CPayload(
	int protocolVersion,
	Optional<Identifier> phaseId,
	boolean adminEligible,
	boolean hostAssigned,
	boolean currentPlayerHost,
	boolean schemaCompatible,
	long stateRevision,
	long loadSequence,
	String definitionStatus,
	boolean definitionHealthy,
	long definitionGeneration,
	int gameDefinitionCount,
	int definitionCount,
	String definitionDiagnostic,
	boolean animateLoad
) implements CustomPacketPayload {
	private static final int MAX_DEFINITION_STATUS_LENGTH = 32;
	private static final int MAX_DEFINITION_DIAGNOSTIC_LENGTH = 1_024;
	public static final Type<SessionSnapshotS2CPayload> TYPE = new Type<>(PixelTzzPro.id("session_snapshot_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SessionSnapshotS2CPayload> STREAM_CODEC = CustomPacketPayload.codec(
		SessionSnapshotS2CPayload::write,
		SessionSnapshotS2CPayload::new
	);

	public SessionSnapshotS2CPayload {
		phaseId = Objects.requireNonNull(phaseId, "phaseId");
		phaseId.ifPresent(value -> requireIdentifier(value, "phaseId"));
	}

	private SessionSnapshotS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readBoolean()
				? Optional.of(readIdentifier(buffer, "phaseId"))
				: Optional.empty(),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readUtf(MAX_DEFINITION_STATUS_LENGTH),
			buffer.readBoolean(),
			buffer.readLong(),
			buffer.readVarInt(),
			buffer.readVarInt(),
			buffer.readUtf(MAX_DEFINITION_DIAGNOSTIC_LENGTH),
			buffer.readBoolean()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeBoolean(this.phaseId.isPresent());
		this.phaseId.ifPresent(value -> writeIdentifier(buffer, value));
		buffer.writeBoolean(this.adminEligible);
		buffer.writeBoolean(this.hostAssigned);
		buffer.writeBoolean(this.currentPlayerHost);
		buffer.writeBoolean(this.schemaCompatible);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.loadSequence);
		buffer.writeUtf(this.definitionStatus, MAX_DEFINITION_STATUS_LENGTH);
		buffer.writeBoolean(this.definitionHealthy);
		buffer.writeLong(this.definitionGeneration);
		buffer.writeVarInt(this.gameDefinitionCount);
		buffer.writeVarInt(this.definitionCount);
		buffer.writeUtf(this.definitionDiagnostic, MAX_DEFINITION_DIAGNOSTIC_LENGTH);
		buffer.writeBoolean(this.animateLoad);
	}

	@Override
	public Type<SessionSnapshotS2CPayload> type() {
		return TYPE;
	}
}
