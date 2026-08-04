package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter boundary around the pure V3B message-instance authority.
 *
 * <p>Minecraft command arguments, scoreboard/storage access, player lookups and packet codecs stay
 * outside the authority. An integration layer resolves them into these immutable values, commits
 * the returned state, and only then forwards projection events to a sink.
 */
public final class MessageRuntimePorts {
	private MessageRuntimePorts() {
	}

	@FunctionalInterface
	public interface ParameterResolver {
		ParameterResolution resolve(
			MessageCueDefinition cue,
			MessageInstanceAuthority.InvocationContext context
		);
	}

	@FunctionalInterface
	public interface AudienceResolver {
		AudienceResolution resolve(
			AudienceSpec cueAudience,
			MessageCueDefinition cue,
			MessageInstanceAuthority.InvocationContext context,
			Map<String, String> canonicalParameters
		);
	}

	/**
	 * Network or local-render adapter invoked only after its matching authority state is committed.
	 */
	@FunctionalInterface
	public interface ProjectionSink {
		void publish(List<MessageInstanceAuthority.ProjectionEvent> events);
	}

	public record ParameterResolution(
		boolean resolved,
		Map<String, String> canonicalValues,
		Map<String, TimeSpan> authoritativeNodeDurations,
		String message
	) {
		public ParameterResolution {
			canonicalValues = immutableMap(canonicalValues);
			authoritativeNodeDurations = immutableMap(authoritativeNodeDurations);
			message = requireMessage(message);
			if (!resolved && (!canonicalValues.isEmpty() || !authoritativeNodeDurations.isEmpty())) {
				throw new IllegalArgumentException(
					"failed parameter resolution cannot expose partial values"
				);
			}
		}

		public static ParameterResolution resolved(
			final Map<String, String> canonicalValues,
			final Map<String, TimeSpan> authoritativeNodeDurations
		) {
			return new ParameterResolution(
				true,
				canonicalValues,
				authoritativeNodeDurations,
				"parameters resolved"
			);
		}

		public static ParameterResolution rejected(final String message) {
			return new ParameterResolution(false, Map.of(), Map.of(), message);
		}
	}

	/**
	 * A server-authoritative audience snapshot.
	 *
	 * <p>{@code nodeTargets} contains only explicit per-node overrides. Missing entries inherit
	 * {@code cueTargets}; all collections are copied before entering instance state.
	 */
	public record AudienceResolution(
		boolean resolved,
		Set<UUID> cueTargets,
		Map<String, Set<UUID>> nodeTargets,
		String message
	) {
		public AudienceResolution {
			cueTargets = immutableSet(cueTargets);
			Objects.requireNonNull(nodeTargets, "nodeTargets");
			Map<String, Set<UUID>> copied = new LinkedHashMap<>();
			nodeTargets.forEach(
				(node, targets) -> copied.put(
					Objects.requireNonNull(node, "node id"),
					immutableSet(targets)
				)
			);
			nodeTargets = Map.copyOf(copied);
			message = requireMessage(message);
			if (!resolved && (!cueTargets.isEmpty() || !nodeTargets.isEmpty())) {
				throw new IllegalArgumentException(
					"failed audience resolution cannot expose partial targets"
				);
			}
		}

		public static AudienceResolution resolved(
			final Set<UUID> cueTargets,
			final Map<String, Set<UUID>> nodeTargets
		) {
			return new AudienceResolution(true, cueTargets, nodeTargets, "audience resolved");
		}

		public static AudienceResolution rejected(final String message) {
			return new AudienceResolution(false, Set.of(), Map.of(), message);
		}
	}

	private static String requireMessage(final String value) {
		String message = Objects.requireNonNull(value, "message").strip();
		if (message.isEmpty() || message.length() > 512) {
			throw new IllegalArgumentException("port result message must contain 1..512 characters");
		}
		return message;
	}

	private static <K, V> Map<K, V> immutableMap(final Map<K, V> values) {
		Objects.requireNonNull(values, "values");
		LinkedHashMap<K, V> copied = new LinkedHashMap<>();
		values.forEach(
			(key, value) -> copied.put(
				Objects.requireNonNull(key, "map key"),
				Objects.requireNonNull(value, "map value")
			)
		);
		return Map.copyOf(copied);
	}

	private static <T> Set<T> immutableSet(final Set<T> values) {
		Objects.requireNonNull(values, "values");
		LinkedHashSet<T> copied = new LinkedHashSet<>();
		for (T value : values) {
			copied.add(Objects.requireNonNull(value, "set value"));
		}
		return Set.copyOf(copied);
	}
}
