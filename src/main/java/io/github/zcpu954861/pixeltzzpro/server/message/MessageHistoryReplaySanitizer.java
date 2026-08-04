package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceLossMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudiencePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConcurrencyPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DeliveryPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.IdentifierKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodeHeader;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterErrorMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterErrorPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Produces a side-effect-free replay cue for one already-authorized history viewer.
 *
 * <p>Only visual and sound nodes survive. The replay cannot execute callbacks, write a second
 * history record, persist across a restart, join a control group, select another audience or
 * resolve a live speaker identity. Cue-level dynamic fields are supplied separately from the
 * history record; runtime code must use their frozen values and must not re-read live field
 * sources for this sanitized definition.
 */
public final class MessageHistoryReplaySanitizer {
	private static final String SANITIZED_CANONICAL_DOCUMENT =
		"{\"pixel_tzz_pro\":\"message_history_replay\"}";
	private static final String FROZEN_FIELD_PARAMETER_PREFIX =
		"history_replay_field_";
	private static final String MISSING_FIELD_PARAMETER =
		"history_replay_missing";
	private static final AudienceSpec VIEWER_AUDIENCE = new AudienceSpec(
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

	private MessageHistoryReplaySanitizer() {
	}

	public static ReplayCue sanitize(
		final MessageCueDefinition source,
		final UUID viewer,
		final Map<String, String> savedFields
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(viewer, "viewer");
		Objects.requireNonNull(savedFields, "savedFields");

		Set<String> removedNodeIds = new LinkedHashSet<>();
		for (CueNode node : source.nodes()) {
			if (
				node instanceof CallbackNode
					|| node.header().when().isPresent()
					|| (!(node instanceof TextNode) && !(node instanceof SoundNode))
			) {
				removedNodeIds.add(node.id());
			}
		}
		for (CueNode node : source.nodes()) {
			if (
				!removedNodeIds.contains(node.id())
					&& node.header().schedule() instanceof AfterNode after
					&& removedNodeIds.contains(after.nodeId())
			) {
				throw new IllegalArgumentException(
					"history replay node '"
						+ node.id()
						+ "' depends on removed conditional or side-effect node '"
						+ after.nodeId()
						+ "'"
				);
			}
		}

		List<CueNode> nodes = new ArrayList<>();
		for (CueNode node : source.nodes()) {
			if (removedNodeIds.contains(node.id())) {
				continue;
			}
			if (node instanceof TextNode text) {
				nodes.add(
					new TextNode(
						sanitizeHeader(text.header()),
						text.channel(),
						sanitizeContent(text.content()),
						List.of()
					)
				);
			} else if (node instanceof SoundNode sound) {
				nodes.add(
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

		FrozenCueFields frozenFields = freezeCueFields(source.fields(), savedFields);
		MessageCueDefinition definition = new MessageCueDefinition(
			source.id(),
			Optional.empty(),
			false,
			sanitizePolicies(source.policies()),
			frozenFields.parameters(),
			frozenFields.fields(),
			nodes,
			Optional.empty(),
			source.staticFallback(),
			source.assets(),
			source.softLimits(),
			SANITIZED_CANONICAL_DOCUMENT
		);
		return new ReplayCue(
			viewer,
			Set.of(viewer),
			definition,
			frozenFields.savedFields()
		);
	}

	private static FrozenCueFields freezeCueFields(
		final Map<String, DynamicFieldDefinition> sourceFields,
		final Map<String, String> savedFields
	) {
		Map<String, ParameterDefinition> parameters = new LinkedHashMap<>();
		Map<String, DynamicFieldDefinition> fields = new LinkedHashMap<>();
		Map<String, String> retained = new LinkedHashMap<>();
		for (Map.Entry<String, DynamicFieldDefinition> entry : sourceFields.entrySet()) {
			String parameter = MISSING_FIELD_PARAMETER;
			if (savedFields.containsKey(entry.getKey())) {
				String canonical = Objects.requireNonNull(
					savedFields.get(entry.getKey()),
					"saved field value"
				);
				ResolvedComponent.parseCanonical(canonical);
				parameter = frozenFieldParameter(entry.getKey());
				parameters.put(
					parameter,
					new ParameterDefinition(
						ParameterType.COMPONENT,
						true,
						Optional.of(canonical),
						List.of(),
						ParameterErrorPolicy.fail(),
						false,
						Set.of(),
						Optional.empty(),
						false,
						IdentifierKind.ANY
					)
				);
				retained.put(entry.getKey(), canonical);
			}
			fields.put(
				entry.getKey(),
				freezeField(entry.getValue(), parameter)
			);
		}
		return new FrozenCueFields(
			Map.copyOf(parameters),
			Map.copyOf(fields),
			Map.copyOf(retained)
		);
	}

	private static DynamicFieldDefinition freezeField(
		final DynamicFieldDefinition source,
		final String parameter
	) {
		return new DynamicFieldDefinition(
			source.capture(),
			new ArgumentSource(parameter),
			source.fallback(),
			source.reservation()
		);
	}

	private static String frozenFieldParameter(final String fieldId) {
		return FROZEN_FIELD_PARAMETER_PREFIX + fieldId;
	}

	private static CuePolicies sanitizePolicies(final CuePolicies source) {
		ConcurrencyPolicy concurrency = source.concurrency();
		return new CuePolicies(
			source.timing(),
			new AudiencePolicy(
				VIEWER_AUDIENCE,
				AudienceEvolution.SNAPSHOT,
				false,
				AudienceLossMode.FINALIZE
			),
			ContextPolicy.unrestricted(),
			new ConcurrencyPolicy(
				DuplicateMode.ALLOW,
				Optional.empty(),
				false,
				Set.of(),
				Optional.empty(),
				concurrency.chatInterrupt(),
				concurrency.priority(),
				Set.of()
			),
			DeliveryPolicy.standard(),
			transientLifecycle(source.lifecycle()),
			source.accessibility(),
			Set.of(),
			source.capture(),
			source.defaultConflict()
		);
	}

	private static LifecyclePolicy transientLifecycle(final LifecyclePolicy source) {
		return new LifecyclePolicy(
			source.screenStart(),
			source.obscured(),
			source.screenWaitTimeout(),
			source.screenTimeoutAction(),
			source.maxPause(),
			source.reload(),
			RestartMode.TRANSIENT,
			source.externalConflict(),
			source.reconnect(),
			source.lateJoin(),
			source.resourceReload(),
			source.respawn(),
			source.attachment(),
			source.dimensionExit()
		);
	}

	private static NodeHeader sanitizeHeader(final NodeHeader source) {
		return new NodeHeader(
			source.id(),
			source.schedule(),
			new NodePolicy(
				Optional.of(VIEWER_AUDIENCE),
				source.policy().priority(),
				source.policy().conflict()
			),
			Optional.empty(),
			source.repeat()
		);
	}

	private static TextContent sanitizeContent(final TextContent source) {
		Map<String, DynamicFieldDefinition> localFields = new LinkedHashMap<>();
		source.fields().forEach((fieldId, field) ->
			localFields.put(fieldId, freezeField(field, MISSING_FIELD_PARAMETER))
		);
		return new TextContent(
			source.text(),
			Map.copyOf(localFields),
			source.effect(),
			source.layout(),
			source.duration(),
			SpeakerSpec.system(),
			source.characterSoundRole(),
			source.hold()
		);
	}

	private static boolean frozenFieldsMatch(
		final MessageCueDefinition definition,
		final Map<String, String> savedFields
	) {
		if (
			definition.parameters().size() != savedFields.size()
				|| !definition.fields().keySet().containsAll(savedFields.keySet())
		) {
			return false;
		}
		for (Map.Entry<String, DynamicFieldDefinition> entry : definition.fields().entrySet()) {
			if (!(entry.getValue().source() instanceof ArgumentSource argument)) {
				return false;
			}
			String saved = savedFields.get(entry.getKey());
			if (saved == null) {
				if (!argument.parameter().equals(MISSING_FIELD_PARAMETER)) {
					return false;
				}
				continue;
			}
			String parameterName = frozenFieldParameter(entry.getKey());
			ParameterDefinition parameter = definition.parameters().get(parameterName);
			if (
				!argument.parameter().equals(parameterName)
					|| parameter == null
					|| parameter.type() != ParameterType.COMPONENT
					|| !parameter.required()
					|| parameter.canonicalDefault().filter(saved::equals).isEmpty()
					|| !parameter.sources().isEmpty()
					|| parameter.onError().mode() != ParameterErrorMode.FAIL
					|| parameter.dedupe()
					|| !parameter.allowedNodes().isEmpty()
					|| parameter.audience().isPresent()
					|| parameter.sensitive()
					|| parameter.identifierKind() != IdentifierKind.ANY
			) {
				return false;
			}
			try {
				ResolvedComponent.parseCanonical(saved);
			} catch (RuntimeException error) {
				return false;
			}
		}
		return true;
	}

	private static boolean localFieldsAreFrozen(final TextContent content) {
		return content.fields()
			.values()
			.stream()
			.allMatch(field ->
				field.source() instanceof ArgumentSource argument
					&& argument.parameter().equals(MISSING_FIELD_PARAMETER)
			);
	}

	private static boolean isViewerAudience(final AudienceSpec audience) {
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

	public record ReplayCue(
		UUID viewer,
		Set<UUID> callTargets,
		MessageCueDefinition definition,
		Map<String, String> savedFields
	) {
		public ReplayCue {
			viewer = Objects.requireNonNull(viewer, "viewer");
			callTargets = Set.copyOf(callTargets);
			definition = Objects.requireNonNull(definition, "definition");
			savedFields = Map.copyOf(savedFields);
			if (!callTargets.equals(Set.of(viewer))) {
				throw new IllegalArgumentException(
					"history replay callTargets must contain exactly its authorized viewer"
				);
			}
			if (
				definition.game().isPresent()
					|| definition.required()
					|| definition.history().isPresent()
					|| !definition.canonicalDocument().equals(
						SANITIZED_CANONICAL_DOCUMENT
					)
					|| !isViewerAudience(definition.policies().audience().audience())
					|| definition.policies().audience().evolution()
						!= AudienceEvolution.SNAPSHOT
					|| definition.policies().audience().sensitive()
					|| definition.policies().audience().onLoss()
						!= AudienceLossMode.FINALIZE
					|| !definition.policies().context().equals(
						ContextPolicy.unrestricted()
					)
					|| definition.policies().concurrency().duplicate()
						!= DuplicateMode.ALLOW
					|| definition.policies().concurrency().cooldown().isPresent()
					|| definition.policies().concurrency().dedupeTargets()
					|| !definition.policies().concurrency().dedupeParameters().isEmpty()
					|| definition.policies().concurrency().refreshKey().isPresent()
					|| !definition.policies().delivery().equals(DeliveryPolicy.standard())
					|| definition.policies().lifecycle().restart()
						!= RestartMode.TRANSIENT
					|| !definition.policies().controlGroups().isEmpty()
					|| !definition.policies().concurrency().groups().isEmpty()
					|| definition.nodes().stream().anyMatch(CallbackNode.class::isInstance)
					|| definition.nodes().stream().anyMatch(node ->
						node.header().when().isPresent()
							||
						node.policy().audience().isEmpty()
							|| !isViewerAudience(node.policy().audience().orElseThrow())
					)
					|| definition.nodes().stream()
						.filter(TextNode.class::isInstance)
						.map(TextNode.class::cast)
						.anyMatch(node ->
							node.content().speaker().kind()
								!= io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind.SYSTEM
								|| !node.variants().isEmpty()
								|| !localFieldsAreFrozen(node.content())
						)
					|| !frozenFieldsMatch(definition, savedFields)
			) {
				throw new IllegalArgumentException(
					"history replay definition is not structurally sanitized"
				);
			}
		}
	}

	private record FrozenCueFields(
		Map<String, ParameterDefinition> parameters,
		Map<String, DynamicFieldDefinition> fields,
		Map<String, String> savedFields
	) {
		private FrozenCueFields {
			parameters = Map.copyOf(parameters);
			fields = Map.copyOf(fields);
			savedFields = Map.copyOf(savedFields);
		}
	}
}
