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
import java.util.Set;
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
	List<FieldSchema> fields,
	PagePurpose purpose,
	Optional<FlowContext> flowContext,
	Optional<TerminalContext> terminalContext,
	byte[] bindingJson
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
	public static final int MAX_EXCLUSIVE_OPTION_NAME_LENGTH = 1_024;
	public static final int MAX_EXCLUSIVE_OPTION_DESCRIPTION_LENGTH = 2_048;
	public static final int MAX_EXCLUSIVE_OPTION_STATE_LENGTH = 32;
	public static final int MAX_OCCUPANT_NAME_LENGTH = 64;
	public static final int MAX_BINDING_BYTES = 65_536;
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
		purpose = Objects.requireNonNull(purpose, "purpose");
		flowContext = Objects.requireNonNull(flowContext, "flowContext");
		terminalContext = Objects.requireNonNull(terminalContext, "terminalContext");
		bindingJson = copyBinding(bindingJson);
		boolean validContext = switch (purpose) {
			case PREVIEW -> flowContext.isEmpty() && terminalContext.isEmpty();
			case FORCED_FLOW -> flowContext.isPresent() && terminalContext.isEmpty();
			case NORMAL -> flowContext.isEmpty() && terminalContext.isPresent();
		};
		if (!validContext) {
			throw new IllegalArgumentException(
				"PREVIEW forbids contexts, FORCED_FLOW requires only flow context, "
					+ "and NORMAL requires only terminal context"
			);
		}
		if (
			purpose == PagePurpose.FORCED_FLOW
				&& flowContext.orElseThrow().closable()
		) {
			throw new IllegalArgumentException("forced-flow pages cannot be client-closable");
		}
		if (purpose == PagePurpose.FORCED_FLOW && bindingJson.length == 0) {
			throw new IllegalArgumentException("forced-flow pages require a server binding snapshot");
		}
	}

	public PageBundleS2CPayload(
		final UUID instanceId,
		final long generation,
		final long stateRevision,
		final Identifier pageId,
		final Identifier themeId,
		final byte[] pageHash,
		final byte[] pageJson,
		final byte[] themeHash,
		final byte[] themeJson,
		final List<FieldSchema> fields
	) {
		this(
			instanceId,
			generation,
			stateRevision,
			pageId,
			themeId,
			pageHash,
			pageJson,
			themeHash,
			themeJson,
			fields,
			PagePurpose.PREVIEW,
			Optional.empty(),
			Optional.empty(),
			new byte[0]
		);
	}

	/**
	 * Compatibility constructor for existing v10 forced-flow and preview callers.
	 */
	public PageBundleS2CPayload(
		final UUID instanceId,
		final long generation,
		final long stateRevision,
		final Identifier pageId,
		final Identifier themeId,
		final byte[] pageHash,
		final byte[] pageJson,
		final byte[] themeHash,
		final byte[] themeJson,
		final List<FieldSchema> fields,
		final PagePurpose purpose,
		final Optional<FlowContext> flowContext,
		final byte[] bindingJson
	) {
		this(
			instanceId,
			generation,
			stateRevision,
			pageId,
			themeId,
			pageHash,
			pageJson,
			themeHash,
			themeJson,
			fields,
			purpose,
			flowContext,
			Optional.empty(),
			bindingJson
		);
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
			readList(buffer, MAX_FIELDS, "fields", FieldSchema::read),
			PagePurpose.read(buffer),
			readFlowContext(buffer),
			readTerminalContext(buffer),
			readByteArray(buffer, MAX_BINDING_BYTES, "bindingJson")
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
		buffer.writeUtf(this.purpose.serializedName, 32);
		buffer.writeBoolean(this.flowContext.isPresent());
		this.flowContext.ifPresent(context -> context.write(buffer));
		buffer.writeBoolean(this.terminalContext.isPresent());
		this.terminalContext.ifPresent(context -> context.write(buffer));
		writeByteArray(buffer, this.bindingJson);
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

	@Override
	public byte[] bindingJson() {
		return this.bindingJson.clone();
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

	private static byte[] copyBinding(final byte[] value) {
		Objects.requireNonNull(value, "bindingJson");
		if (value.length > MAX_BINDING_BYTES) {
			throw new IllegalArgumentException("bindingJson length exceeds " + MAX_BINDING_BYTES);
		}
		return value.clone();
	}

	private static Optional<FlowContext> readFlowContext(
		final RegistryFriendlyByteBuf buffer
	) {
		return buffer.readBoolean() ? Optional.of(FlowContext.read(buffer)) : Optional.empty();
	}

	private static Optional<TerminalContext> readTerminalContext(
		final RegistryFriendlyByteBuf buffer
	) {
		return buffer.readBoolean()
			? Optional.of(TerminalContext.read(buffer))
			: Optional.empty();
	}

	public enum PagePurpose {
		PREVIEW("preview"),
		NORMAL("normal"),
		FORCED_FLOW("forced_flow");

		private final String serializedName;

		PagePurpose(final String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return this.serializedName;
		}

		private static PagePurpose read(final RegistryFriendlyByteBuf buffer) {
			String name = buffer.readUtf(32);
			for (PagePurpose value : values()) {
				if (value.serializedName.equals(name)) {
					return value;
				}
			}
			throw new IllegalArgumentException("unknown page purpose " + name);
		}
	}

	public record FlowContext(
		UUID flowInstanceId,
		Identifier flowId,
		int flowVersion,
		String nodeId,
		long instanceRevision,
		long memberRevision,
		long nextRequestSequence,
		int completed,
		int total,
		boolean closable
	) {
		private static final int MAX_NODE_ID_LENGTH = 128;

		public FlowContext {
			flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			flowId = requireIdentifier(flowId, "flowId");
			nodeId = requireBounded(nodeId, MAX_NODE_ID_LENGTH, "nodeId");
			if (flowVersion <= 0) {
				throw new IllegalArgumentException("flowVersion must be positive");
			}
			if (
				nodeId.isBlank()
					|| instanceRevision < 0L
					|| memberRevision < 0L
					|| nextRequestSequence < 0L
					|| completed < 0
					|| total < 0
					|| completed > total
			) {
				throw new IllegalArgumentException("invalid forced-flow page context");
			}
		}

		private static FlowContext read(final RegistryFriendlyByteBuf buffer) {
			return new FlowContext(
				buffer.readUUID(),
				readIdentifier(buffer, "flowId"),
				buffer.readVarInt(),
				buffer.readUtf(MAX_NODE_ID_LENGTH),
				buffer.readLong(),
				buffer.readLong(),
				buffer.readVarLong(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readBoolean()
			);
		}

		private void write(final RegistryFriendlyByteBuf buffer) {
			buffer.writeUUID(this.flowInstanceId);
			writeIdentifier(buffer, this.flowId);
			buffer.writeVarInt(this.flowVersion);
			buffer.writeUtf(this.nodeId, MAX_NODE_ID_LENGTH);
			buffer.writeLong(this.instanceRevision);
			buffer.writeLong(this.memberRevision);
			buffer.writeVarLong(this.nextRequestSequence);
			buffer.writeVarInt(this.completed);
			buffer.writeVarInt(this.total);
			buffer.writeBoolean(this.closable);
		}
	}

	public record TerminalContext(
		UUID sessionId,
		long routeRevision,
		int stackDepth,
		long nextRequestSequence,
		boolean takeoverAllowed
	) {
		public TerminalContext {
			sessionId = Objects.requireNonNull(sessionId, "sessionId");
			if (routeRevision < 0L || stackDepth <= 0 || nextRequestSequence < 0L) {
				throw new IllegalArgumentException("invalid normal-terminal page context");
			}
		}

		private static TerminalContext read(final RegistryFriendlyByteBuf buffer) {
			return new TerminalContext(
				buffer.readUUID(),
				buffer.readLong(),
				buffer.readVarInt(),
				buffer.readVarLong(),
				buffer.readBoolean()
			);
		}

		private void write(final RegistryFriendlyByteBuf buffer) {
			buffer.writeUUID(this.sessionId);
			buffer.writeLong(this.routeRevision);
			buffer.writeVarInt(this.stackDepth);
			buffer.writeVarLong(this.nextRequestSequence);
			buffer.writeBoolean(this.takeoverAllowed);
		}
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
		OptionalInt maximumSelections,
		List<ExclusiveOptionState> exclusiveOptions
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
			exclusiveOptions = List.copyOf(exclusiveOptions);
			if (exclusiveOptions.size() > MAX_FIELD_OPTIONS) {
				throw new IllegalArgumentException(
					"field exclusive options exceed " + MAX_FIELD_OPTIONS
				);
			}
			if ("exclusive_choice".equals(type)) {
				if (
					exclusiveOptions.size() != options.size()
						|| !exclusiveOptions.stream()
							.map(ExclusiveOptionState::value)
							.toList()
							.equals(options)
				) {
					throw new IllegalArgumentException(
						"exclusive field option metadata must match field options"
					);
				}
			} else if (!exclusiveOptions.isEmpty()) {
				throw new IllegalArgumentException(
					"only exclusive_choice fields may carry exclusive option metadata"
				);
			}
		}

		public FieldSchema(
			final Identifier id,
			final String type,
			final String name,
			final String description,
			final boolean required,
			final Optional<String> defaultJson,
			final OptionalLong minimum,
			final OptionalLong maximum,
			final OptionalInt minimumLength,
			final OptionalInt maximumLength,
			final List<String> options,
			final OptionalInt minimumSelections,
			final OptionalInt maximumSelections
		) {
			this(
				id,
				type,
				name,
				description,
				required,
				defaultJson,
				minimum,
				maximum,
				minimumLength,
				maximumLength,
				options,
				minimumSelections,
				maximumSelections,
				List.of()
			);
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
				readOptionalInt(buffer),
				readList(
					buffer,
					MAX_FIELD_OPTIONS,
					"field.exclusiveOptions",
					ExclusiveOptionState::read
				)
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
			writeList(buffer, field.exclusiveOptions, ExclusiveOptionState::write);
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

	public record ExclusiveOptionState(
		String value,
		String nameJson,
		String descriptionJson,
		Optional<Identifier> icon,
		Optional<Identifier> previewPage,
		String state,
		Optional<UUID> occupantId,
		Optional<String> occupantName,
		boolean occupantOnline
	) {
		private static final Set<String> STATES = Set.of(
			"available",
			"held_by_self",
			"locked_by_self",
			"held_by_other",
			"locked_by_other"
		);

		public ExclusiveOptionState {
			value = requireBounded(value, MAX_FIELD_OPTION_LENGTH, "exclusiveOption.value");
			nameJson = requireBounded(
				nameJson,
				MAX_EXCLUSIVE_OPTION_NAME_LENGTH,
				"exclusiveOption.nameJson"
			);
			descriptionJson = requireBounded(
				descriptionJson,
				MAX_EXCLUSIVE_OPTION_DESCRIPTION_LENGTH,
				"exclusiveOption.descriptionJson"
			);
			icon = Objects.requireNonNull(icon, "exclusiveOption.icon");
			previewPage = Objects.requireNonNull(previewPage, "exclusiveOption.previewPage");
			state = requireBounded(
				state,
				MAX_EXCLUSIVE_OPTION_STATE_LENGTH,
				"exclusiveOption.state"
			);
			if (!STATES.contains(state)) {
				throw new IllegalArgumentException("unknown exclusive option state " + state);
			}
			occupantId = Objects.requireNonNull(occupantId, "exclusiveOption.occupantId");
			occupantName = Objects.requireNonNull(occupantName, "exclusiveOption.occupantName")
				.map(name -> requireBounded(
					name,
					MAX_OCCUPANT_NAME_LENGTH,
					"exclusiveOption.occupantName"
				));
			if (occupantId.isPresent() != occupantName.isPresent()) {
				throw new IllegalArgumentException(
					"exclusive option occupant ID and name must be paired"
				);
			}
			if (occupantOnline && occupantId.isEmpty()) {
				throw new IllegalArgumentException(
					"exclusive option without an occupant cannot be online"
				);
			}
			if (state.equals("available") && occupantId.isPresent()) {
				throw new IllegalArgumentException(
					"available exclusive option cannot expose an occupant"
				);
			}
		}

		private static ExclusiveOptionState read(final RegistryFriendlyByteBuf buffer) {
			return new ExclusiveOptionState(
				buffer.readUtf(MAX_FIELD_OPTION_LENGTH),
				buffer.readUtf(MAX_EXCLUSIVE_OPTION_NAME_LENGTH),
				buffer.readUtf(MAX_EXCLUSIVE_OPTION_DESCRIPTION_LENGTH),
				buffer.readBoolean()
					? Optional.of(readIdentifier(buffer, "exclusiveOption.icon"))
					: Optional.empty(),
				buffer.readBoolean()
					? Optional.of(readIdentifier(buffer, "exclusiveOption.previewPage"))
					: Optional.empty(),
				buffer.readUtf(MAX_EXCLUSIVE_OPTION_STATE_LENGTH),
				buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty(),
				buffer.readBoolean()
					? Optional.of(buffer.readUtf(MAX_OCCUPANT_NAME_LENGTH))
					: Optional.empty(),
				buffer.readBoolean()
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final ExclusiveOptionState option
		) {
			buffer.writeUtf(option.value, MAX_FIELD_OPTION_LENGTH);
			buffer.writeUtf(option.nameJson, MAX_EXCLUSIVE_OPTION_NAME_LENGTH);
			buffer.writeUtf(option.descriptionJson, MAX_EXCLUSIVE_OPTION_DESCRIPTION_LENGTH);
			buffer.writeBoolean(option.icon.isPresent());
			option.icon.ifPresent(value -> writeIdentifier(buffer, value));
			buffer.writeBoolean(option.previewPage.isPresent());
			option.previewPage.ifPresent(value -> writeIdentifier(buffer, value));
			buffer.writeUtf(option.state, MAX_EXCLUSIVE_OPTION_STATE_LENGTH);
			buffer.writeBoolean(option.occupantId.isPresent());
			option.occupantId.ifPresent(buffer::writeUUID);
			buffer.writeBoolean(option.occupantName.isPresent());
			option.occupantName.ifPresent(value ->
				buffer.writeUtf(value, MAX_OCCUPANT_NAME_LENGTH)
			);
			buffer.writeBoolean(option.occupantOnline);
		}
	}
}
