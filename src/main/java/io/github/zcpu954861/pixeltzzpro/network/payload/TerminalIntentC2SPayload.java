package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One replay-protected navigation or registered-action intent for a normal terminal page.
 */
public record TerminalIntentC2SPayload(
	int protocolVersion,
	UUID terminalSessionId,
	UUID pageInstanceId,
	long definitionGeneration,
	long stateRevision,
	long routeRevision,
	long requestSequence,
	Intent intent,
	Optional<String> nodeId,
	Optional<Identifier> actionId,
	Optional<String> historyRecordKey
) implements CustomPacketPayload {
	public static final int MAX_INTENT_LENGTH = 32;
	public static final int MAX_NODE_ID_LENGTH = 128;
	public static final int MAX_HISTORY_RECORD_KEY_LENGTH = 256;
	public static final Type<TerminalIntentC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("terminal_intent_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalIntentC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(TerminalIntentC2SPayload::write, TerminalIntentC2SPayload::new);

	public TerminalIntentC2SPayload {
		if (
			protocolVersion < 0
				|| definitionGeneration < 0L
				|| stateRevision < 0L
				|| routeRevision < 0L
				|| requestSequence < 0L
		) {
			throw new IllegalArgumentException(
				"protocolVersion, generation, revisions, and requestSequence must be non-negative"
			);
		}
		terminalSessionId = Objects.requireNonNull(terminalSessionId, "terminalSessionId");
		pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
		intent = Objects.requireNonNull(intent, "intent");
		nodeId = Objects.requireNonNull(nodeId, "nodeId")
			.map(value -> requireLocalValue(value, MAX_NODE_ID_LENGTH, "nodeId"));
		actionId = Objects.requireNonNull(actionId, "actionId")
			.map(value -> requireIdentifier(value, "actionId"));
		historyRecordKey = Objects.requireNonNull(
			historyRecordKey,
			"historyRecordKey"
		).map(value -> {
			String bounded = requireBounded(
				value,
				MAX_HISTORY_RECORD_KEY_LENGTH,
				"historyRecordKey"
			);
			if (bounded.isBlank()) {
				throw new IllegalArgumentException("historyRecordKey must not be blank");
			}
			return bounded;
		});
		boolean action = intent == Intent.ACTION;
		boolean historyDetail = intent == Intent.HISTORY_DETAIL;
		if (
			action
				!= (
					nodeId.isPresent()
						&& actionId.isPresent()
						&& historyRecordKey.isEmpty()
				)
				|| historyDetail
					!= (
						nodeId.isPresent()
							&& actionId.isEmpty()
							&& historyRecordKey.isPresent()
					)
				|| (
					!action
						&& !historyDetail
						&& (
							nodeId.isPresent()
								|| actionId.isPresent()
								|| historyRecordKey.isPresent()
						)
				)
		) {
			throw new IllegalArgumentException(
				"ACTION requires nodeId/actionId; HISTORY_DETAIL requires nodeId/historyRecordKey; BACK and REFRESH forbid all three"
			);
		}
	}

	public TerminalIntentC2SPayload(
		final int protocolVersion,
		final UUID terminalSessionId,
		final UUID pageInstanceId,
		final long definitionGeneration,
		final long stateRevision,
		final long routeRevision,
		final long requestSequence,
		final Intent intent,
		final Optional<String> nodeId,
		final Optional<Identifier> actionId
	) {
		this(
			protocolVersion,
			terminalSessionId,
			pageInstanceId,
			definitionGeneration,
			stateRevision,
			routeRevision,
			requestSequence,
			intent,
			nodeId,
			actionId,
			Optional.empty()
		);
	}

	private TerminalIntentC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readUUID(),
			buffer.readUUID(),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readVarLong(),
			Intent.read(buffer),
			readOptionalNodeId(buffer),
			readOptionalActionId(buffer),
			readOptionalHistoryRecordKey(buffer)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeUUID(this.terminalSessionId);
		buffer.writeUUID(this.pageInstanceId);
		buffer.writeLong(this.definitionGeneration);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.routeRevision);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeUtf(this.intent.serializedName, MAX_INTENT_LENGTH);
		buffer.writeBoolean(this.nodeId.isPresent());
		this.nodeId.ifPresent(value -> buffer.writeUtf(value, MAX_NODE_ID_LENGTH));
		buffer.writeBoolean(this.actionId.isPresent());
		this.actionId.ifPresent(value -> writeIdentifier(buffer, value));
		buffer.writeBoolean(this.historyRecordKey.isPresent());
		this.historyRecordKey.ifPresent(
			value -> buffer.writeUtf(value, MAX_HISTORY_RECORD_KEY_LENGTH)
		);
	}

	@Override
	public Type<TerminalIntentC2SPayload> type() {
		return TYPE;
	}

	private static Optional<String> readOptionalNodeId(final RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean()
			? Optional.of(buffer.readUtf(MAX_NODE_ID_LENGTH))
			: Optional.empty();
	}

	private static Optional<Identifier> readOptionalActionId(
		final RegistryFriendlyByteBuf buffer
	) {
		return buffer.readBoolean()
			? Optional.of(readIdentifier(buffer, "actionId"))
			: Optional.empty();
	}

	private static Optional<String> readOptionalHistoryRecordKey(
		final RegistryFriendlyByteBuf buffer
	) {
		return buffer.readBoolean()
			? Optional.of(buffer.readUtf(MAX_HISTORY_RECORD_KEY_LENGTH))
			: Optional.empty();
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

	public enum Intent {
		BACK("back"),
		ACTION("action"),
		HISTORY_DETAIL("history_detail"),
		REFRESH("refresh");

		private final String serializedName;

		Intent(final String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return this.serializedName;
		}

		private static Intent read(final RegistryFriendlyByteBuf buffer) {
			String name = buffer.readUtf(MAX_INTENT_LENGTH);
			for (Intent value : values()) {
				if (value.serializedName.equals(name)) {
					return value;
				}
			}
			throw new IllegalArgumentException("unknown terminal intent " + name);
		}
	}
}
