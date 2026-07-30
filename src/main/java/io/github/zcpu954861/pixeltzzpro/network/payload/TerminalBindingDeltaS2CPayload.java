package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Binding-only update for an already open terminal page instance.
 */
public record TerminalBindingDeltaS2CPayload(
	UUID sessionId,
	UUID pageInstanceId,
	long generation,
	long stateRevision,
	long routeRevision,
	byte[] bindingJson
) implements CustomPacketPayload {
	public static final int MAX_BINDING_BYTES = PageBundleS2CPayload.MAX_BINDING_BYTES;
	public static final Type<TerminalBindingDeltaS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("terminal_binding_delta_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalBindingDeltaS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			TerminalBindingDeltaS2CPayload::write,
			TerminalBindingDeltaS2CPayload::new
		);

	public TerminalBindingDeltaS2CPayload {
		sessionId = Objects.requireNonNull(sessionId, "sessionId");
		pageInstanceId = Objects.requireNonNull(pageInstanceId, "pageInstanceId");
		if (generation < 0L || stateRevision < 0L || routeRevision < 0L) {
			throw new IllegalArgumentException("generation and revisions must be non-negative");
		}
		Objects.requireNonNull(bindingJson, "bindingJson");
		if (bindingJson.length == 0 || bindingJson.length > MAX_BINDING_BYTES) {
			throw new IllegalArgumentException(
				"bindingJson length must be between 1 and " + MAX_BINDING_BYTES
			);
		}
		bindingJson = bindingJson.clone();
	}

	private TerminalBindingDeltaS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			buffer.readUUID(),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readLong(),
			readByteArray(buffer, MAX_BINDING_BYTES, "bindingJson")
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.sessionId);
		buffer.writeUUID(this.pageInstanceId);
		buffer.writeLong(this.generation);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.routeRevision);
		writeByteArray(buffer, this.bindingJson);
	}

	@Override
	public byte[] bindingJson() {
		return this.bindingJson.clone();
	}

	@Override
	public Type<TerminalBindingDeltaS2CPayload> type() {
		return TYPE;
	}
}
