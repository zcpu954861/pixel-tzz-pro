package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Requests a server-authored confirmation for one high-risk operation.
 */
public record PrepareOperationC2SPayload(
	StatefulRequest request,
	String operationType,
	Identifier operationId,
	List<UUID> targetIds,
	Optional<String> note
) implements CustomPacketPayload {
	public static final int MAX_OPERATION_TYPE_LENGTH = 64;
	public static final int MAX_TARGETS = 64;
	public static final int MAX_NOTE_LENGTH = 512;
	public static final Type<PrepareOperationC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("prepare_operation_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PrepareOperationC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(PrepareOperationC2SPayload::write, PrepareOperationC2SPayload::new);

	public PrepareOperationC2SPayload {
		request = Objects.requireNonNull(request, "request");
		operationType = requireBounded(operationType, MAX_OPERATION_TYPE_LENGTH, "operationType");
		if (
			operationType.isBlank()
				|| !operationType.equals(operationType.toLowerCase(Locale.ROOT))
		) {
			throw new IllegalArgumentException("operationType must be non-blank lowercase text");
		}
		operationId = requireIdentifier(operationId, "operationId");
		Objects.requireNonNull(targetIds, "targetIds");
		if (targetIds.size() > MAX_TARGETS || targetIds.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("targetIds exceeds the bounded target limit");
		}
		List<UUID> sorted = targetIds.stream().sorted(Comparator.naturalOrder()).toList();
		if (sorted.stream().distinct().count() != sorted.size()) {
			throw new IllegalArgumentException("targetIds contains duplicates");
		}
		targetIds = sorted;
		note = Objects.requireNonNull(note, "note")
			.map(value -> requireBounded(value, MAX_NOTE_LENGTH, "note"));
	}

	private PrepareOperationC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			StatefulRequest.read(buffer),
			buffer.readUtf(MAX_OPERATION_TYPE_LENGTH),
			readIdentifier(buffer, "operationId"),
			readList(buffer, MAX_TARGETS, "targetIds", value -> value.readUUID()),
			buffer.readBoolean() ? Optional.of(buffer.readUtf(MAX_NOTE_LENGTH)) : Optional.empty()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		this.request.write(buffer);
		buffer.writeUtf(this.operationType, MAX_OPERATION_TYPE_LENGTH);
		writeIdentifier(buffer, this.operationId);
		writeList(buffer, this.targetIds, (target, value) -> target.writeUUID(value));
		buffer.writeBoolean(this.note.isPresent());
		this.note.ifPresent(value -> buffer.writeUtf(value, MAX_NOTE_LENGTH));
	}

	@Override
	public Type<PrepareOperationC2SPayload> type() {
		return TYPE;
	}
}
