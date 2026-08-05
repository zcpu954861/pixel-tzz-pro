package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Shared bounded header and document codec for protocol-v13 HUD frames. */
final class HudFrameCodec {
	static final int MAX_REPLACE_BYTES = 131_072;
	static final int MAX_PATCH_BYTES = 65_536;

	private HudFrameCodec() {
	}

	static Header requireHeader(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence
	) {
		return new Header(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
	}

	static Header readHeader(final RegistryFriendlyByteBuf buffer) {
		return requireHeader(
			buffer.readUUID(),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readVarLong()
		);
	}

	static void writeHeader(final RegistryFriendlyByteBuf buffer, final Header header) {
		buffer.writeUUID(header.gameInstanceId());
		buffer.writeVarLong(header.definitionGeneration());
		buffer.writeVarLong(header.epoch());
		buffer.writeVarLong(header.contextVersion());
		buffer.writeVarLong(header.sequence());
	}

	static byte[] requireDocument(
		final byte[] document,
		final int maximumBytes,
		final String field
	) {
		Objects.requireNonNull(document, field);
		if (document.length == 0 || document.length > maximumBytes) {
			throw new IllegalArgumentException(
				field + " length must be between 1 and " + maximumBytes
			);
		}
		return document.clone();
	}

	static byte[] readDocument(
		final RegistryFriendlyByteBuf buffer,
		final int maximumBytes,
		final String field
	) {
		byte[] document = readByteArray(buffer, maximumBytes, field);
		return requireDocument(document, maximumBytes, field);
	}

	static void writeDocument(final RegistryFriendlyByteBuf buffer, final byte[] document) {
		writeByteArray(buffer, document);
	}

	record Header(
		UUID gameInstanceId,
		long definitionGeneration,
		long epoch,
		long contextVersion,
		long sequence
	) {
		Header {
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			if (
				definitionGeneration < 0L
					|| epoch < 0L
					|| contextVersion < 0L
					|| sequence < 0L
			) {
				throw new IllegalArgumentException("HUD frame versions must be non-negative");
			}
		}
	}
}
