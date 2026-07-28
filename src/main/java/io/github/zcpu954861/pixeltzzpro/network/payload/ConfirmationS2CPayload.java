package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Frozen, server-authored confirmation content. The client only returns {@code tokenId}.
 */
public record ConfirmationS2CPayload(
	int protocolVersion,
	long requestSequence,
	UUID tokenId,
	String operationType,
	Identifier operationId,
	String titleJson,
	List<String> consequenceJson,
	List<Target> targets,
	String contextSummary,
	long validForMilliseconds,
	long stateRevision,
	long definitionGeneration
) implements CustomPacketPayload {
	public static final int MAX_OPERATION_TYPE_LENGTH = 64;
	public static final int MAX_TEXT_LENGTH = 8_192;
	public static final int MAX_CONSEQUENCES = 64;
	public static final int MAX_TARGETS = 64;
	public static final int MAX_CONTEXT_LENGTH = 2_048;
	public static final Type<ConfirmationS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("confirmation_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmationS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(ConfirmationS2CPayload::write, ConfirmationS2CPayload::new);

	public ConfirmationS2CPayload {
		if (protocolVersion < 0 || requestSequence < 0L) {
			throw new IllegalArgumentException("protocolVersion and requestSequence must be non-negative");
		}
		tokenId = Objects.requireNonNull(tokenId, "tokenId");
		operationType = requireBounded(operationType, MAX_OPERATION_TYPE_LENGTH, "operationType");
		if (
			operationType.isBlank()
				|| !operationType.equals(operationType.toLowerCase(Locale.ROOT))
		) {
			throw new IllegalArgumentException("operationType must be non-blank lowercase text");
		}
		operationId = requireIdentifier(operationId, "operationId");
		titleJson = requireBounded(titleJson, MAX_TEXT_LENGTH, "titleJson");
		Objects.requireNonNull(consequenceJson, "consequenceJson");
		if (consequenceJson.isEmpty() || consequenceJson.size() > MAX_CONSEQUENCES) {
			throw new IllegalArgumentException("consequence count must be within 1.." + MAX_CONSEQUENCES);
		}
		consequenceJson = consequenceJson.stream()
			.map(value -> requireBounded(value, MAX_TEXT_LENGTH, "consequenceJson"))
			.toList();
		Objects.requireNonNull(targets, "targets");
		if (targets.size() > MAX_TARGETS) {
			throw new IllegalArgumentException("target count exceeds " + MAX_TARGETS);
		}
		targets = List.copyOf(targets);
		contextSummary = requireBounded(contextSummary, MAX_CONTEXT_LENGTH, "contextSummary");
		if (validForMilliseconds <= 0L || validForMilliseconds > 30_000L) {
			throw new IllegalArgumentException("validForMilliseconds must be within 1..30000");
		}
		if (stateRevision < 0L || definitionGeneration < 0L) {
			throw new IllegalArgumentException("revision and generation must be non-negative");
		}
	}

	private ConfirmationS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readVarLong(),
			buffer.readUUID(),
			buffer.readUtf(MAX_OPERATION_TYPE_LENGTH),
			readIdentifier(buffer, "operationId"),
			buffer.readUtf(MAX_TEXT_LENGTH),
			readList(buffer, MAX_CONSEQUENCES, "consequences", value -> value.readUtf(MAX_TEXT_LENGTH)),
			readList(buffer, MAX_TARGETS, "targets", TargetSnapshotS2CPayload.Target::read),
			buffer.readUtf(MAX_CONTEXT_LENGTH),
			buffer.readVarLong(),
			buffer.readLong(),
			buffer.readLong()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeUUID(this.tokenId);
		buffer.writeUtf(this.operationType, MAX_OPERATION_TYPE_LENGTH);
		writeIdentifier(buffer, this.operationId);
		buffer.writeUtf(this.titleJson, MAX_TEXT_LENGTH);
		writeList(
			buffer,
			this.consequenceJson,
			(target, value) -> target.writeUtf(value, MAX_TEXT_LENGTH)
		);
		writeList(buffer, this.targets, TargetSnapshotS2CPayload.Target::write);
		buffer.writeUtf(this.contextSummary, MAX_CONTEXT_LENGTH);
		buffer.writeVarLong(this.validForMilliseconds);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.definitionGeneration);
	}

	@Override
	public Type<ConfirmationS2CPayload> type() {
		return TYPE;
	}
}
