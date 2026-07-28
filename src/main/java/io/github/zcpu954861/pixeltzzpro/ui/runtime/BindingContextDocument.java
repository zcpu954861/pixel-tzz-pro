package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Strict shared contract for a server-authored live-page binding snapshot.
 */
public final class BindingContextDocument {
	public static final int MAX_UTF8_BYTES = 65_536;
	public static final int MAX_BINDINGS_PER_ROOT = 4_096;
	private static final int MAX_JSON_DEPTH = 32;
	private static final Set<String> ROOTS = Set.of(
		"viewer",
		"session",
		"flow",
		"fields",
		"flow_fields"
	);

	private BindingContextDocument() {
	}

	public static byte[] encode(final JsonObject document) {
		byte[] encoded = document.toString().getBytes(StandardCharsets.UTF_8);
		if (encoded.length > MAX_UTF8_BYTES) {
			throw new IllegalArgumentException(
				"binding document exceeds " + MAX_UTF8_BYTES + " UTF-8 bytes"
			);
		}
		// Decode the exact wire form so producer and consumer share one validation path.
		decode(encoded);
		return encoded;
	}

	public static BindingContext decode(final byte[] encoded) {
		if (encoded == null || encoded.length == 0 || encoded.length > MAX_UTF8_BYTES) {
			throw new IllegalArgumentException("binding document has an invalid byte length");
		}
		JsonElement parsed;
		try {
			String json = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(encoded))
				.toString();
			JsonReader reader = new JsonReader(new StringReader(json));
			reader.setStrictness(Strictness.STRICT);
			parsed = readStrictValue(reader, 0);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("trailing data");
			}
		} catch (IOException | JsonParseException error) {
			throw new IllegalArgumentException("invalid binding document", error);
		}
		if (!parsed.isJsonObject()) {
			throw new IllegalArgumentException("binding document root must be an object");
		}

		JsonObject root = parsed.getAsJsonObject();
		for (String name : root.keySet()) {
			if (!ROOTS.contains(name)) {
				throw new IllegalArgumentException("unknown binding root " + name);
			}
		}
		for (String required : ROOTS) {
			JsonElement value = root.get(required);
			if (value == null || !value.isJsonObject()) {
				throw new IllegalArgumentException("binding root " + required + " must be an object");
			}
			if (value.getAsJsonObject().size() > MAX_BINDINGS_PER_ROOT) {
				throw new IllegalArgumentException("binding root " + required + " is too large");
			}
		}

		BindingContext.Builder builder = BindingContext.builder();
		copyProperties(root.getAsJsonObject("viewer"), builder::viewer);
		copyProperties(root.getAsJsonObject("session"), builder::session);
		copyProperties(root.getAsJsonObject("flow"), builder::flow);
		copyIdentifiers(root.getAsJsonObject("fields"), builder::field);
		copyIdentifiers(root.getAsJsonObject("flow_fields"), builder::flowField);
		return builder.build();
	}

	private static JsonElement readStrictValue(
		final JsonReader reader,
		final int depth
	) throws IOException {
		if (depth > MAX_JSON_DEPTH) {
			throw new JsonParseException("binding JSON nesting exceeds " + MAX_JSON_DEPTH);
		}
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				JsonObject object = new JsonObject();
				Set<String> names = new HashSet<>();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (!names.add(name)) {
						throw new JsonParseException("duplicate binding key " + name);
					}
					object.add(name, readStrictValue(reader, depth + 1));
				}
				reader.endObject();
				yield object;
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				JsonArray array = new JsonArray();
				while (reader.hasNext()) {
					array.add(readStrictValue(reader, depth + 1));
				}
				reader.endArray();
				yield array;
			}
			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}
			default -> throw new JsonParseException("unexpected binding JSON token " + reader.peek());
		};
	}

	private static void copyProperties(
		final JsonObject object,
		final java.util.function.BiConsumer<String, JsonElement> consumer
	) {
		object.entrySet().forEach(entry -> {
			if (
				entry.getKey().isBlank()
					|| entry.getKey().length() > 128
					|| !entry.getKey().matches("[a-z0-9_]+")
			) {
				throw new IllegalArgumentException("invalid binding property " + entry.getKey());
			}
			consumer.accept(entry.getKey(), entry.getValue());
		});
	}

	private static void copyIdentifiers(
		final JsonObject object,
		final java.util.function.BiConsumer<Identifier, JsonElement> consumer
	) {
		object.entrySet().forEach(entry -> {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null || entry.getKey().indexOf(':') <= 0) {
				throw new IllegalArgumentException("invalid binding field identifier " + entry.getKey());
			}
			consumer.accept(id, entry.getValue());
		});
	}
}
