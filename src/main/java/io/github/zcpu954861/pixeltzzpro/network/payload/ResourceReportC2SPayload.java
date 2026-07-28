package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bounded missing-resource diagnostics for one page instance.
 */
public record ResourceReportC2SPayload(
	UUID instanceId,
	long generation,
	Identifier pageId,
	int totalMissing,
	List<Entry> entries,
	boolean requiredMissing,
	boolean renderReady,
	Optional<String> renderError
) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 50;
	public static final int MAX_TOTAL_MISSING = 2_048;
	public static final int MAX_TYPE_LENGTH = 32;
	public static final int MAX_ASSET_ID_LENGTH = 256;
	public static final int MAX_JSON_POINTER_LENGTH = 1_024;
	public static final int MAX_RENDER_ERROR_LENGTH = 512;
	public static final Type<ResourceReportC2SPayload> TYPE = new Type<>(PixelTzzPro.id("resource_report_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ResourceReportC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(ResourceReportC2SPayload::write, ResourceReportC2SPayload::new);

	public ResourceReportC2SPayload {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
		if (generation < 0L) {
			throw new IllegalArgumentException("generation must be non-negative");
		}
		pageId = requireIdentifier(pageId, "pageId");
		if (totalMissing < 0 || totalMissing > MAX_TOTAL_MISSING) {
			throw new IllegalArgumentException(
				"totalMissing must be within 0.." + MAX_TOTAL_MISSING
			);
		}
		entries = List.copyOf(entries);
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException("entries size exceeds " + MAX_ENTRIES);
		}
		if (entries.size() > totalMissing) {
			throw new IllegalArgumentException("entries cannot exceed totalMissing");
		}
		renderError = Objects.requireNonNull(renderError, "renderError")
			.map(value -> requireBounded(value, MAX_RENDER_ERROR_LENGTH, "renderError"));
		if (renderError.filter(String::isBlank).isPresent()) {
			throw new IllegalArgumentException("renderError cannot be blank");
		}
		if (renderReady && (requiredMissing || renderError.isPresent())) {
			throw new IllegalArgumentException(
				"a ready render cannot contain a fatal resource or render error"
			);
		}
	}

	public ResourceReportC2SPayload(
		final UUID instanceId,
		final long generation,
		final Identifier pageId,
		final int totalMissing,
		final List<Entry> entries
	) {
		this(
			instanceId,
			generation,
			pageId,
			totalMissing,
			entries,
			false,
			false,
			Optional.empty()
		);
	}

	private ResourceReportC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readUUID(),
			buffer.readLong(),
			readIdentifier(buffer, "pageId"),
			buffer.readVarInt(),
			readList(buffer, MAX_ENTRIES, "entries", Entry::read),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readBoolean()
				? Optional.of(buffer.readUtf(MAX_RENDER_ERROR_LENGTH))
				: Optional.empty()
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(this.instanceId);
		buffer.writeLong(this.generation);
		writeIdentifier(buffer, this.pageId);
		buffer.writeVarInt(this.totalMissing);
		writeList(buffer, this.entries, Entry::write);
		buffer.writeBoolean(this.requiredMissing);
		buffer.writeBoolean(this.renderReady);
		buffer.writeBoolean(this.renderError.isPresent());
		this.renderError.ifPresent(
			value -> buffer.writeUtf(value, MAX_RENDER_ERROR_LENGTH)
		);
	}

	@Override
	public Type<ResourceReportC2SPayload> type() {
		return TYPE;
	}

	public record Entry(String type, String id, String jsonPointer) {
		public Entry {
			type = requireBounded(type, MAX_TYPE_LENGTH, "type");
			id = requireBounded(id, MAX_ASSET_ID_LENGTH, "id");
			jsonPointer = requireBounded(jsonPointer, MAX_JSON_POINTER_LENGTH, "jsonPointer");
		}

		private static Entry read(final RegistryFriendlyByteBuf buffer) {
			return new Entry(
				buffer.readUtf(MAX_TYPE_LENGTH),
				buffer.readUtf(MAX_ASSET_ID_LENGTH),
				buffer.readUtf(MAX_JSON_POINTER_LENGTH)
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final Entry entry) {
			buffer.writeUtf(entry.type, MAX_TYPE_LENGTH);
			buffer.writeUtf(entry.id, MAX_ASSET_ID_LENGTH);
			buffer.writeUtf(entry.jsonPointer, MAX_JSON_POINTER_LENGTH);
		}
	}
}
