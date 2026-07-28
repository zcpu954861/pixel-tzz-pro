package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Submits one action against the caller's current forced-flow page.
 */
public record FlowActionC2SPayload(
	StatefulRequest request,
	Identifier flowId,
	int flowVersion,
	String nodeId,
	long instanceRevision,
	long memberRevision,
	String actionId,
	List<FieldValue> fieldValues
) implements CustomPacketPayload {
	public static final int MAX_NODE_ID_LENGTH = 128;
	public static final int MAX_ACTION_ID_LENGTH = 128;
	public static final int MAX_FIELD_VALUES = 256;
	public static final int MAX_FIELD_TYPE_LENGTH = 32;
	public static final int MAX_FIELD_JSON_LENGTH = 65_536;
	public static final int MAX_FIELD_PAYLOAD_BYTES = 65_536;
	public static final Type<FlowActionC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("flow_action_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, FlowActionC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(FlowActionC2SPayload::write, FlowActionC2SPayload::new);

	public FlowActionC2SPayload {
		request = Objects.requireNonNull(request, "request");
		if (request.flowInstanceId().isEmpty() || request.pageInstanceId().isEmpty()) {
			throw new IllegalArgumentException("flow action requires flow and page instance IDs");
		}
		flowId = requireIdentifier(flowId, "flowId");
		if (flowVersion <= 0) {
			throw new IllegalArgumentException("flowVersion must be positive");
		}
		nodeId = requireLocalValue(nodeId, MAX_NODE_ID_LENGTH, "nodeId");
		if (instanceRevision < 0L || memberRevision < 0L) {
			throw new IllegalArgumentException("instanceRevision and memberRevision must be non-negative");
		}
		actionId = requireLocalValue(actionId, MAX_ACTION_ID_LENGTH, "actionId");
		Objects.requireNonNull(fieldValues, "fieldValues");
		if (fieldValues.size() > MAX_FIELD_VALUES) {
			throw new IllegalArgumentException("field value count exceeds " + MAX_FIELD_VALUES);
		}
		List<FieldValue> sorted = fieldValues.stream()
			.sorted(Comparator.comparing(FieldValue::fieldId))
			.toList();
		if (sorted.stream().map(FieldValue::fieldId).distinct().count() != sorted.size()) {
			throw new IllegalArgumentException("fieldValues contains duplicate field IDs");
		}
		int bytes = sorted.stream().mapToInt(FieldValue::estimatedBytes).sum();
		if (bytes > MAX_FIELD_PAYLOAD_BYTES) {
			throw new IllegalArgumentException("field value payload exceeds " + MAX_FIELD_PAYLOAD_BYTES + " bytes");
		}
		fieldValues = sorted;
	}

	private FlowActionC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			StatefulRequest.read(buffer),
			readIdentifier(buffer, "flowId"),
			buffer.readVarInt(),
			buffer.readUtf(MAX_NODE_ID_LENGTH),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readUtf(MAX_ACTION_ID_LENGTH),
			readList(buffer, MAX_FIELD_VALUES, "fieldValues", FieldValue::read)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		this.request.write(buffer);
		writeIdentifier(buffer, this.flowId);
		buffer.writeVarInt(this.flowVersion);
		buffer.writeUtf(this.nodeId, MAX_NODE_ID_LENGTH);
		buffer.writeLong(this.instanceRevision);
		buffer.writeLong(this.memberRevision);
		buffer.writeUtf(this.actionId, MAX_ACTION_ID_LENGTH);
		writeList(buffer, this.fieldValues, FieldValue::write);
	}

	@Override
	public Type<FlowActionC2SPayload> type() {
		return TYPE;
	}

	public record FieldValue(Identifier fieldId, String type, String canonicalJson) {
		public FieldValue {
			fieldId = requireIdentifier(fieldId, "fieldId");
			type = requireLocalValue(type, MAX_FIELD_TYPE_LENGTH, "field.type");
			canonicalJson = requireBounded(canonicalJson, MAX_FIELD_JSON_LENGTH, "field.canonicalJson");
			if (canonicalJson.isBlank()) {
				throw new IllegalArgumentException("field.canonicalJson cannot be blank");
			}
		}

		private static FieldValue read(final RegistryFriendlyByteBuf buffer) {
			return new FieldValue(
				readIdentifier(buffer, "fieldId"),
				buffer.readUtf(MAX_FIELD_TYPE_LENGTH),
				buffer.readUtf(MAX_FIELD_JSON_LENGTH)
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final FieldValue value
		) {
			writeIdentifier(buffer, value.fieldId);
			buffer.writeUtf(value.type, MAX_FIELD_TYPE_LENGTH);
			buffer.writeUtf(value.canonicalJson, MAX_FIELD_JSON_LENGTH);
		}

		private int estimatedBytes() {
			return this.fieldId.toString().getBytes(StandardCharsets.UTF_8).length
				+ this.type.getBytes(StandardCharsets.UTF_8).length
				+ this.canonicalJson.getBytes(StandardCharsets.UTF_8).length;
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
