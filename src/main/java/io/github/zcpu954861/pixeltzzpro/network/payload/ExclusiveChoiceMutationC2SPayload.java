package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Holds or releases one exclusive option on the caller's current choice page without advancing it.
 */
public record ExclusiveChoiceMutationC2SPayload(
	StatefulRequest request,
	Identifier flowId,
	int flowVersion,
	String nodeId,
	long instanceRevision,
	long memberRevision,
	Identifier fieldId,
	Operation operation,
	Optional<String> value
) implements CustomPacketPayload {
	public static final int MAX_NODE_ID_LENGTH = 128;
	public static final int MAX_OPTION_VALUE_LENGTH = 128;
	public static final int MAX_OPERATION_LENGTH = 16;
	public static final Type<ExclusiveChoiceMutationC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("exclusive_choice_mutation_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ExclusiveChoiceMutationC2SPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			ExclusiveChoiceMutationC2SPayload::write,
			ExclusiveChoiceMutationC2SPayload::new
		);

	public ExclusiveChoiceMutationC2SPayload {
		request = Objects.requireNonNull(request, "request");
		if (request.flowInstanceId().isEmpty() || request.pageInstanceId().isEmpty()) {
			throw new IllegalArgumentException(
				"exclusive choice mutation requires flow and page instance IDs"
			);
		}
		flowId = requireIdentifier(flowId, "flowId");
		if (flowVersion <= 0) {
			throw new IllegalArgumentException("flowVersion must be positive");
		}
		nodeId = requireLocalValue(nodeId, MAX_NODE_ID_LENGTH, "nodeId");
		if (instanceRevision < 0L || memberRevision < 0L) {
			throw new IllegalArgumentException(
				"instanceRevision and memberRevision must be non-negative"
			);
		}
		fieldId = requireIdentifier(fieldId, "fieldId");
		operation = Objects.requireNonNull(operation, "operation");
		value = Objects.requireNonNull(value, "value")
			.map(candidate ->
				requireLocalValue(candidate, MAX_OPTION_VALUE_LENGTH, "value")
			);
		if ((operation == Operation.HOLD) != value.isPresent()) {
			throw new IllegalArgumentException("hold requires one value and release must omit it");
		}
	}

	private ExclusiveChoiceMutationC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			StatefulRequest.read(buffer),
			readIdentifier(buffer, "flowId"),
			buffer.readVarInt(),
			buffer.readUtf(MAX_NODE_ID_LENGTH),
			buffer.readLong(),
			buffer.readLong(),
			readIdentifier(buffer, "fieldId"),
			Operation.read(buffer.readUtf(MAX_OPERATION_LENGTH)),
			buffer.readBoolean()
				? Optional.of(buffer.readUtf(MAX_OPTION_VALUE_LENGTH))
				: Optional.empty()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		this.request.write(buffer);
		writeIdentifier(buffer, this.flowId);
		buffer.writeVarInt(this.flowVersion);
		buffer.writeUtf(this.nodeId, MAX_NODE_ID_LENGTH);
		buffer.writeLong(this.instanceRevision);
		buffer.writeLong(this.memberRevision);
		writeIdentifier(buffer, this.fieldId);
		buffer.writeUtf(this.operation.serializedName, MAX_OPERATION_LENGTH);
		buffer.writeBoolean(this.value.isPresent());
		this.value.ifPresent(candidate -> buffer.writeUtf(candidate, MAX_OPTION_VALUE_LENGTH));
	}

	@Override
	public Type<ExclusiveChoiceMutationC2SPayload> type() {
		return TYPE;
	}

	public enum Operation {
		HOLD("hold"),
		RELEASE("release");

		private final String serializedName;

		Operation(final String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return this.serializedName;
		}

		private static Operation read(final String value) {
			for (Operation operation : values()) {
				if (operation.serializedName.equals(value)) {
					return operation;
				}
			}
			throw new IllegalArgumentException("unknown exclusive choice operation " + value);
		}
	}

	private static String requireLocalValue(
		final String value,
		final int maximumLength,
		final String field
	) {
		String bounded = requireBounded(value, maximumLength, field);
		if (
			bounded.isBlank()
				|| !bounded.equals(bounded.toLowerCase(Locale.ROOT))
				|| !bounded.matches("[a-z0-9_.-]+")
		) {
			throw new IllegalArgumentException(field + " must match [a-z0-9_.-]+");
		}
		return bounded;
	}
}
