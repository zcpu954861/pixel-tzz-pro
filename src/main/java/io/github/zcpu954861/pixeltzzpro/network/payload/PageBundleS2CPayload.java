package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readFixedBytes;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One immutable page and theme document pair for a preview page instance.
 */
public record PageBundleS2CPayload(
	UUID instanceId,
	long generation,
	long stateRevision,
	Identifier pageId,
	Identifier themeId,
	byte[] pageHash,
	byte[] pageJson,
	byte[] themeHash,
	byte[] themeJson,
	List<FieldSchema> fields
) implements CustomPacketPayload {
	public static final int HASH_LENGTH = 32;
	public static final int MAX_DOCUMENT_BYTES = UiDefinitions.MAX_CANONICAL_DOCUMENT_BYTES;
	public static final int MAX_FIELDS = 512;
	public static final int MAX_FIELD_TYPE_LENGTH = 32;
	public static final int MAX_FIELD_NAME_LENGTH = 256;
	public static final int MAX_FIELD_DESCRIPTION_LENGTH = 1_024;
	public static final int MAX_FIELD_DEFAULT_LENGTH = 2_048;
	public static final int MAX_FIELD_OPTIONS = 128;
	public static final int MAX_FIELD_OPTION_LENGTH = 256;
	public static final Type<PageBundleS2CPayload> TYPE = new Type<>(PixelTzzPro.id("page_bundle_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PageBundleS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(PageBundleS2CPayload::write, PageBundleS2CPayload::new);

	public PageBundleS2CPayload {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
		if (generation < 0L) {
			throw new IllegalArgumentException("generation must be non-negative");
		}
		if (stateRevision < 0L) {
			throw new IllegalArgumentException("stateRevision must be non-negative");
		}
		pageId = requireIdentifier(pageId, "pageId");
		themeId = requireIdentifier(themeId, "themeId");
		pageHash = copyHash(pageHash, "pageHash");
		pageJson = copyDocument(pageJson, "pageJson");
		themeHash = copyHash(themeHash, "themeHash");
		themeJson = copyDocument(themeJson, "themeJson");
		if ((pageJson.length == 0) != (themeJson.length == 0)) {
			throw new IllegalArgumentException("pageJson and themeJson must both be present or omitted");
		}
		fields = List.copyOf(fields);
		if (fields.size() > MAX_FIELDS) {
			throw new IllegalArgumentException("fields size exceeds " + MAX_FIELDS);
		}
	}

	private PageBundleS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			buffer.readLong(),
			buffer.readLong(),
			readIdentifier(buffer, "pageId"),
			readIdentifier(buffer, "themeId"),
			readFixedBytes(buffer, HASH_LENGTH),
			readByteArray(buffer, MAX_DOCUMENT_BYTES, "pageJson"),
			readFixedBytes(buffer, HASH_LENGTH),
			readByteArray(buffer, MAX_DOCUMENT_BYTES, "themeJson"),
			readList(buffer, MAX_FIELDS, "fields", FieldSchema::read)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.instanceId);
		buffer.writeLong(this.generation);
		buffer.writeLong(this.stateRevision);
		writeIdentifier(buffer, this.pageId);
		writeIdentifier(buffer, this.themeId);
		buffer.writeBytes(this.pageHash);
		writeByteArray(buffer, this.pageJson);
		buffer.writeBytes(this.themeHash);
		writeByteArray(buffer, this.themeJson);
		writeList(buffer, this.fields, FieldSchema::write);
	}

	@Override
	public byte[] pageHash() {
		return this.pageHash.clone();
	}

	@Override
	public byte[] pageJson() {
		return this.pageJson.clone();
	}

	@Override
	public byte[] themeHash() {
		return this.themeHash.clone();
	}

	@Override
	public byte[] themeJson() {
		return this.themeJson.clone();
	}

	public boolean documentsOmitted() {
		return this.pageJson.length == 0;
	}

	@Override
	public Type<PageBundleS2CPayload> type() {
		return TYPE;
	}

	private static byte[] copyHash(final byte[] value, final String field) {
		Objects.requireNonNull(value, field);
		if (value.length != HASH_LENGTH) {
			throw new IllegalArgumentException(field + " must contain " + HASH_LENGTH + " bytes");
		}
		return value.clone();
	}

	private static byte[] copyDocument(final byte[] value, final String field) {
		Objects.requireNonNull(value, field);
		if (value.length > MAX_DOCUMENT_BYTES) {
			throw new IllegalArgumentException(field + " length exceeds " + MAX_DOCUMENT_BYTES);
		}
		return value.clone();
	}

	public record FieldSchema(
		Identifier id,
		String type,
		String name,
		String description,
		boolean required,
		Optional<String> defaultJson,
		OptionalLong minimum,
		OptionalLong maximum,
		OptionalInt minimumLength,
		OptionalInt maximumLength,
		List<String> options,
		OptionalInt minimumSelections,
		OptionalInt maximumSelections
	) {
		public FieldSchema {
			id = requireIdentifier(id, "field.id");
			type = requireBounded(type, MAX_FIELD_TYPE_LENGTH, "field.type");
			name = requireBounded(name, MAX_FIELD_NAME_LENGTH, "field.name");
			description = requireBounded(
				description,
				MAX_FIELD_DESCRIPTION_LENGTH,
				"field.description"
			);
			defaultJson = Objects.requireNonNull(defaultJson, "field.defaultJson")
				.map(value -> requireBounded(value, MAX_FIELD_DEFAULT_LENGTH, "field.defaultJson"));
			minimum = Objects.requireNonNull(minimum, "field.minimum");
			maximum = Objects.requireNonNull(maximum, "field.maximum");
			minimumLength = Objects.requireNonNull(minimumLength, "field.minimumLength");
			maximumLength = Objects.requireNonNull(maximumLength, "field.maximumLength");
			options = List.copyOf(options);
			if (options.size() > MAX_FIELD_OPTIONS) {
				throw new IllegalArgumentException("field options exceed " + MAX_FIELD_OPTIONS);
			}
			for (String option : options) {
				requireBounded(option, MAX_FIELD_OPTION_LENGTH, "field.option");
			}
			minimumSelections = Objects.requireNonNull(minimumSelections, "field.minimumSelections");
			maximumSelections = Objects.requireNonNull(maximumSelections, "field.maximumSelections");
		}

		private static FieldSchema read(final RegistryFriendlyByteBuf buffer) {
			return new FieldSchema(
				readIdentifier(buffer, "field.id"),
				buffer.readUtf(MAX_FIELD_TYPE_LENGTH),
				buffer.readUtf(MAX_FIELD_NAME_LENGTH),
				buffer.readUtf(MAX_FIELD_DESCRIPTION_LENGTH),
				buffer.readBoolean(),
				readOptionalString(buffer, MAX_FIELD_DEFAULT_LENGTH),
				readOptionalLong(buffer),
				readOptionalLong(buffer),
				readOptionalInt(buffer),
				readOptionalInt(buffer),
				readList(
					buffer,
					MAX_FIELD_OPTIONS,
					"field.options",
					value -> value.readUtf(MAX_FIELD_OPTION_LENGTH)
				),
				readOptionalInt(buffer),
				readOptionalInt(buffer)
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final FieldSchema field) {
			writeIdentifier(buffer, field.id);
			buffer.writeUtf(field.type, MAX_FIELD_TYPE_LENGTH);
			buffer.writeUtf(field.name, MAX_FIELD_NAME_LENGTH);
			buffer.writeUtf(field.description, MAX_FIELD_DESCRIPTION_LENGTH);
			buffer.writeBoolean(field.required);
			writeOptionalString(buffer, field.defaultJson, MAX_FIELD_DEFAULT_LENGTH);
			writeOptionalLong(buffer, field.minimum);
			writeOptionalLong(buffer, field.maximum);
			writeOptionalInt(buffer, field.minimumLength);
			writeOptionalInt(buffer, field.maximumLength);
			writeList(
				buffer,
				field.options,
				(target, option) -> target.writeUtf(option, MAX_FIELD_OPTION_LENGTH)
			);
			writeOptionalInt(buffer, field.minimumSelections);
			writeOptionalInt(buffer, field.maximumSelections);
		}

		private static Optional<String> readOptionalString(
			final RegistryFriendlyByteBuf buffer,
			final int maximumLength
		) {
			return buffer.readBoolean() ? Optional.of(buffer.readUtf(maximumLength)) : Optional.empty();
		}

		private static void writeOptionalString(
			final RegistryFriendlyByteBuf buffer,
			final Optional<String> value,
			final int maximumLength
		) {
			buffer.writeBoolean(value.isPresent());
			value.ifPresent(present -> buffer.writeUtf(present, maximumLength));
		}

		private static OptionalLong readOptionalLong(final RegistryFriendlyByteBuf buffer) {
			return buffer.readBoolean() ? OptionalLong.of(buffer.readLong()) : OptionalLong.empty();
		}

		private static void writeOptionalLong(
			final RegistryFriendlyByteBuf buffer,
			final OptionalLong value
		) {
			buffer.writeBoolean(value.isPresent());
			if (value.isPresent()) {
				buffer.writeLong(value.getAsLong());
			}
		}

		private static OptionalInt readOptionalInt(final RegistryFriendlyByteBuf buffer) {
			return buffer.readBoolean() ? OptionalInt.of(buffer.readVarInt()) : OptionalInt.empty();
		}

		private static void writeOptionalInt(
			final RegistryFriendlyByteBuf buffer,
			final OptionalInt value
		) {
			buffer.writeBoolean(value.isPresent());
			if (value.isPresent()) {
				buffer.writeVarInt(value.getAsInt());
			}
		}
	}
}
