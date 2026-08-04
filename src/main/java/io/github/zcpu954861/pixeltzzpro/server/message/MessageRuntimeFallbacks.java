package io.github.zcpu954861.pixeltzzpro.server.message;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Small pure boundary shared by the Minecraft runtime and its JVM self-checks.
 *
 * <p>Static fallbacks are already canonicalized and validated with the vanilla component codec at
 * data-pack compile time. Decoding that canonical document here preserves styles and interaction
 * metadata that would be destroyed by rebuilding a literal from {@code plainText}.
 */
public final class MessageRuntimeFallbacks {
	private MessageRuntimeFallbacks() {
	}

	public static Component decode(final RichComponent component) {
		Objects.requireNonNull(component, "component");
		return decodeCanonical(component.canonicalJson());
	}

	/**
	 * Decodes the already-validated canonical component retained by a durable final-only delivery.
	 */
	public static Component decodeCanonical(final String canonicalComponent) {
		Objects.requireNonNull(canonicalComponent, "canonicalComponent");
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(canonicalComponent))
			.getOrThrow();
	}

	/**
	 * Recipient-stream-local and refresh-stable de-duplication for compile-failure fallbacks.
	 */
	public static final class CompilationGate {
		private final Set<OccurrenceKey> reported = new LinkedHashSet<>();

		/**
		 * Marks every occurrence covered by one failed compile and returns whether one fallback should
		 * be emitted. A bulk initial compile therefore emits once while suppressing later per-node
		 * retries for the same occurrences.
		 */
		public boolean markFailed(final Collection<OccurrenceKey> occurrences) {
			Objects.requireNonNull(occurrences, "occurrences");
			boolean newlyReported = false;
			for (OccurrenceKey occurrence : occurrences) {
				newlyReported |= this.reported.add(
					Objects.requireNonNull(occurrence, "occurrence")
				);
			}
			return newlyReported;
		}

		/** Carries the gate across an in-place stream refresh of the same instance and recipient. */
		public void inherit(final CompilationGate previous) {
			Objects.requireNonNull(previous, "previous");
			this.reported.addAll(previous.reported);
		}

		public Set<OccurrenceKey> snapshot() {
			return Set.copyOf(this.reported);
		}
	}

	public record OccurrenceKey(String nodeId, int occurrence) {
		public OccurrenceKey {
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			if (nodeId.isBlank()) {
				throw new IllegalArgumentException("nodeId cannot be blank");
			}
			if (occurrence < 0) {
				throw new IllegalArgumentException("occurrence must be non-negative");
			}
		}
	}
}
