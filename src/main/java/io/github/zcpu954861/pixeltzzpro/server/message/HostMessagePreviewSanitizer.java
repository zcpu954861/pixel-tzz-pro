package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceLossMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudiencePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConcurrencyPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodeHeader;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Produces a side-effect-free message definition for one explicit host preview target.
 *
 * <p>The result cannot write history or execute data-pack callbacks. Both the cue audience and
 * every retained visual/sound node are replaced with a single {@code call_targets} selector.
 * The caller receives the only legal call-target set together with the sanitized definition, so
 * later runtime integration does not need to reconstruct this security boundary.
 */
public final class HostMessagePreviewSanitizer {
	private static final String SANITIZED_CANONICAL_DOCUMENT =
		"{\"pixel_tzz_pro\":\"host_message_preview\"}";
	private static final AudienceSpec HOST_AUDIENCE = new AudienceSpec(
		AudienceSource.CALL_TARGETS,
		Set.of(),
		Set.of(),
		Set.of(),
		Set.of(),
		Set.of(),
		Set.of(),
		false,
		true
	);

	private HostMessagePreviewSanitizer() {
	}

	/**
	 * Sanitizes {@code source} and binds the invocation to exactly one explicit host UUID.
	 *
	 * @throws IllegalArgumentException when a retained node depends on a removed callback node;
	 *     emitting a broken or differently timed preview would be less safe than failing closed.
	 */
	public static HostPreview sanitize(
		final MessageCueDefinition source,
		final UUID host
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(host, "host");

		Set<String> callbackIds = new LinkedHashSet<>();
		for (CueNode node : source.nodes()) {
			if (node instanceof CallbackNode) {
				callbackIds.add(node.id());
			}
		}
		for (CueNode node : source.nodes()) {
			if (
				!(node instanceof CallbackNode)
					&& node.header().schedule() instanceof AfterNode after
					&& callbackIds.contains(after.nodeId())
			) {
				throw new IllegalArgumentException(
					"host preview node '"
						+ node.id()
						+ "' depends on removed callback node '"
						+ after.nodeId()
						+ "'"
				);
			}
		}

		List<CueNode> sanitizedNodes = new ArrayList<>();
		for (CueNode node : source.nodes()) {
			if (node instanceof TextNode text) {
				sanitizedNodes.add(
					new TextNode(
						sanitizeHeader(text.header()),
						text.channel(),
						text.content(),
						text.variants()
					)
				);
			} else if (node instanceof SoundNode sound) {
				sanitizedNodes.add(
					new SoundNode(
						sanitizeHeader(sound.header()),
						sound.sound(),
						sound.category(),
						sound.volume(),
						sound.pitch(),
						sound.attachment(),
						sound.missing(),
						sound.fallback()
					)
				);
			}
		}

		MessageCueDefinition sanitized = new MessageCueDefinition(
			source.id(),
			Optional.empty(),
			source.required(),
			sanitizePolicies(source.policies()),
			source.parameters(),
			source.fields(),
			sanitizedNodes,
			Optional.empty(),
			source.staticFallback(),
			source.assets(),
			source.softLimits(),
			SANITIZED_CANONICAL_DOCUMENT
		);
		return new HostPreview(host, Set.of(host), sanitized);
	}

	private static CuePolicies sanitizePolicies(final CuePolicies source) {
		ConcurrencyPolicy sourceConcurrency = source.concurrency();
		return new CuePolicies(
			source.timing(),
			new AudiencePolicy(
				HOST_AUDIENCE,
				AudienceEvolution.SNAPSHOT,
				false,
				AudienceLossMode.FINALIZE
			),
			ContextPolicy.unrestricted(),
			new ConcurrencyPolicy(
				/*
				 * A host may press preview repeatedly while checking a cue.  Stacking several
				 * callback-free copies behind the same Chat/Title surfaces makes a later real
				 * invocation appear silent for an arbitrarily long time.  Preview is diagnostic,
				 * so the newest request replaces its previous playback instead of forming a queue.
				 */
				DuplicateMode.RESTART,
				Optional.empty(),
				false,
				Set.of(),
				Optional.empty(),
				sourceConcurrency.chatInterrupt(),
				sourceConcurrency.priority(),
				Set.of()
			),
			source.delivery(),
			source.lifecycle(),
			source.accessibility(),
			Set.of(),
			source.capture(),
			source.defaultConflict()
		);
	}

	private static NodeHeader sanitizeHeader(final NodeHeader source) {
		return new NodeHeader(
			source.id(),
			source.schedule(),
			new NodePolicy(
				Optional.of(HOST_AUDIENCE),
				source.policy().priority(),
				source.policy().conflict()
			),
			source.when(),
			source.repeat()
		);
	}

	private static boolean isHostAudience(final AudienceSpec audience) {
		if (audience.selectors().size() != 1) {
			return false;
		}
		var selector = audience.selectors().getFirst();
		return selector.source() == AudienceSource.CALL_TARGETS
			&& selector.roles().isEmpty()
			&& selector.teams().isEmpty()
			&& selector.lifeStates().isEmpty()
			&& selector.roleTags().isEmpty()
			&& selector.teamTags().isEmpty()
			&& selector.lifeStateTags().isEmpty()
			&& !selector.excludeHost()
			&& selector.onlineOnly();
	}

	/** Immutable invocation bundle that cannot represent a multi-recipient host preview. */
	public record HostPreview(
		UUID host,
		Set<UUID> callTargets,
		MessageCueDefinition definition
	) {
		public HostPreview {
			host = Objects.requireNonNull(host, "host");
			callTargets = Set.copyOf(callTargets);
			definition = Objects.requireNonNull(definition, "definition");
			if (!callTargets.equals(Set.of(host))) {
				throw new IllegalArgumentException(
					"host preview callTargets must contain exactly the explicit host"
				);
			}
			if (
				definition.game().isPresent()
					|| !definition.policies().context().games().isEmpty()
					|| !definition.policies().context().phases().isEmpty()
					|| !definition.policies().context().tasks().isEmpty()
					|| definition.history().isPresent()
					|| !definition.canonicalDocument().equals(
						SANITIZED_CANONICAL_DOCUMENT
					)
					|| !isHostAudience(definition.policies().audience().audience())
					|| definition.policies().audience().evolution()
						!= AudienceEvolution.SNAPSHOT
					|| definition.policies().audience().sensitive()
					|| definition.nodes().stream().anyMatch(CallbackNode.class::isInstance)
					|| definition.nodes().stream().anyMatch(
						node -> node.policy().audience().isEmpty()
							|| !isHostAudience(node.policy().audience().orElseThrow())
					)
			) {
				throw new IllegalArgumentException(
					"host preview definition is not structurally sanitized"
				);
			}
		}
	}
}
