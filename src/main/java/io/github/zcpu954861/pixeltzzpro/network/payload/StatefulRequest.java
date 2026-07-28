package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Shared bounded context for every state-changing protocol-v7 request.
 *
 * <p>The connection supplies the operator UUID. It is intentionally absent here.
 */
public record StatefulRequest(
	int protocolVersion,
	long requestSequence,
	Optional<Identifier> gameId,
	long definitionGeneration,
	long stateRevision,
	Optional<UUID> flowInstanceId,
	Optional<UUID> pageInstanceId
) {
	public StatefulRequest {
		if (protocolVersion < 0) {
			throw new IllegalArgumentException("protocolVersion must be non-negative");
		}
		if (requestSequence < 0L) {
			throw new IllegalArgumentException("requestSequence must be non-negative");
		}
		gameId = Objects.requireNonNull(gameId, "gameId");
		if (definitionGeneration < 0L) {
			throw new IllegalArgumentException("definitionGeneration must be non-negative");
		}
		if (stateRevision < 0L) {
			throw new IllegalArgumentException("stateRevision must be non-negative");
		}
		flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
		pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
		if (pageInstanceId.isPresent() && flowInstanceId.isEmpty()) {
			throw new IllegalArgumentException("pageInstanceId requires flowInstanceId");
		}
	}

	static StatefulRequest read(final RegistryFriendlyByteBuf buffer) {
		return new StatefulRequest(
			buffer.readVarInt(),
			buffer.readVarLong(),
			readOptionalIdentifier(buffer, "gameId"),
			buffer.readLong(),
			buffer.readLong(),
			readOptionalUuid(buffer),
			readOptionalUuid(buffer)
		);
	}

	void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		writeOptionalIdentifier(buffer, this.gameId);
		buffer.writeLong(this.definitionGeneration);
		buffer.writeLong(this.stateRevision);
		writeOptionalUuid(buffer, this.flowInstanceId);
		writeOptionalUuid(buffer, this.pageInstanceId);
	}

	private static Optional<Identifier> readOptionalIdentifier(
		final RegistryFriendlyByteBuf buffer,
		final String field
	) {
		return buffer.readBoolean()
			? Optional.of(readIdentifier(buffer, field))
			: Optional.empty();
	}

	private static void writeOptionalIdentifier(
		final RegistryFriendlyByteBuf buffer,
		final Optional<Identifier> value
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(identifier -> writeIdentifier(buffer, identifier));
	}

	private static Optional<UUID> readOptionalUuid(final RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
	}

	private static void writeOptionalUuid(
		final RegistryFriendlyByteBuf buffer,
		final Optional<UUID> value
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(buffer::writeUUID);
	}
}
