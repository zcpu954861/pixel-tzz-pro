package io.github.zcpu954861.pixeltzzpro.network.payload;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Shared bounded primitives for Pixel TZZ payloads.
 */
final class PayloadCodecUtil {
	static final int MAX_IDENTIFIER_LENGTH = 256;

	private PayloadCodecUtil() {
	}

	static Identifier readIdentifier(final RegistryFriendlyByteBuf buffer, final String field) {
		String value = buffer.readUtf(MAX_IDENTIFIER_LENGTH);
		Identifier identifier = Identifier.tryParse(value);
		if (identifier == null) {
			throw new DecoderException(field + " is not a valid identifier");
		}
		return identifier;
	}

	static void writeIdentifier(final RegistryFriendlyByteBuf buffer, final Identifier identifier) {
		buffer.writeUtf(identifier.toString(), MAX_IDENTIFIER_LENGTH);
	}

	static <T> List<T> readList(
		final RegistryFriendlyByteBuf buffer,
		final int maximumSize,
		final String field,
		final Function<RegistryFriendlyByteBuf, T> decoder
	) {
		int size = buffer.readVarInt();
		if (size < 0 || size > maximumSize) {
			throw new DecoderException(field + " size " + size + " exceeds " + maximumSize);
		}
		List<T> values = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			values.add(decoder.apply(buffer));
		}
		return List.copyOf(values);
	}

	static <T> void writeList(
		final RegistryFriendlyByteBuf buffer,
		final List<T> values,
		final BiConsumer<RegistryFriendlyByteBuf, T> encoder
	) {
		buffer.writeVarInt(values.size());
		for (T value : values) {
			encoder.accept(buffer, value);
		}
	}

	static byte[] readFixedBytes(final RegistryFriendlyByteBuf buffer, final int length) {
		byte[] value = new byte[length];
		buffer.readBytes(value);
		return value;
	}

	static byte[] readByteArray(
		final RegistryFriendlyByteBuf buffer,
		final int maximumLength,
		final String field
	) {
		int length = buffer.readVarInt();
		if (length < 0 || length > maximumLength) {
			throw new DecoderException(field + " length " + length + " exceeds " + maximumLength);
		}
		return readFixedBytes(buffer, length);
	}

	static void writeByteArray(final RegistryFriendlyByteBuf buffer, final byte[] value) {
		buffer.writeVarInt(value.length);
		buffer.writeBytes(value);
	}

	static String requireBounded(final String value, final int maximumLength, final String field) {
		if (value == null) {
			throw new NullPointerException(field);
		}
		if (value.length() > maximumLength) {
			throw new IllegalArgumentException(field + " length exceeds " + maximumLength);
		}
		return value;
	}

	static Identifier requireIdentifier(final Identifier value, final String field) {
		if (value == null) {
			throw new NullPointerException(field);
		}
		if (value.toString().length() > MAX_IDENTIFIER_LENGTH) {
			throw new IllegalArgumentException(field + " length exceeds " + MAX_IDENTIFIER_LENGTH);
		}
		return value;
	}
}
