package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistorySpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generation-frozen reachability graph from client-visible message surfaces to cue parameters.
 *
 * <p>The canonical parameter map remains one server-authoritative invocation snapshot. This graph
 * answers the separate question of which parameter authorization must be intersected before one
 * recipient may observe a node or persisted history record. In particular, it follows
 * {@code argument.*} bindings, variant/header conditions, player speakers, and {@code after_node}
 * dependencies so those indirect uses cannot bypass a parameter's audience or
 * {@code allowed_nodes} boundary.</p>
 */
public final class MessageParameterUseGraph {
	private static final String ARGUMENT_PREFIX = "argument.";
	private static final BindingRuntime BINDINGS = new BindingRuntime();

	private MessageParameterUseGraph() {
	}

	public static UseGraph compile(final MessageCueDefinition cue) {
		Objects.requireNonNull(cue, "cue");
		return compile(
			cue.parameters(),
			cue.fields(),
			cue.nodes(),
			cue.history()
		);
	}

	/**
	 * Builds the same graph while a cue is still being parsed and has not yet been assembled into
	 * its immutable definition record.
	 */
	static UseGraph compile(
		final Map<String, ParameterDefinition> parameterDefinitions,
		final Map<String, DynamicFieldDefinition> cueFields,
		final List<CueNode> nodes,
		final Optional<HistorySpec> history
	) {
		Objects.requireNonNull(parameterDefinitions, "parameterDefinitions");
		Objects.requireNonNull(cueFields, "cueFields");
		Objects.requireNonNull(nodes, "nodes");
		Objects.requireNonNull(history, "history");
		Map<String, CueNode> nodesById = new LinkedHashMap<>();
		Map<String, Set<String>> direct = new LinkedHashMap<>();
		for (CueNode node : nodes) {
			nodesById.put(node.id(), node);
			LinkedHashSet<String> usedParameters = new LinkedHashSet<>();
			node.header().when().ifPresent(condition ->
				collectCondition(condition, usedParameters)
			);
			if (node instanceof TextNode text) {
				collectContent(
					text.content(),
					cueFields,
					usedParameters
				);
				text.variants().forEach(variant -> {
					collectCondition(variant.when(), usedParameters);
					collectContent(
						variant.content(),
						cueFields,
						usedParameters
					);
				});
			}
			usedParameters.retainAll(parameterDefinitions.keySet());
			direct.put(node.id(), Set.copyOf(usedParameters));
		}

		Map<String, Set<String>> reachable = new LinkedHashMap<>();
		for (CueNode node : nodes) {
			reachable.put(
				node.id(),
				resolveNodeParameters(
					node,
					nodesById,
					direct,
					reachable,
					new LinkedHashSet<>()
				)
			);
		}

		LinkedHashSet<String> historyParameters = new LinkedHashSet<>();
		history.ifPresent(value ->
			collectHistory(value, cueFields, historyParameters)
		);
		historyParameters.retainAll(parameterDefinitions.keySet());
		return new UseGraph(reachable, Set.copyOf(historyParameters));
	}

	private static Set<String> resolveNodeParameters(
		final CueNode node,
		final Map<String, CueNode> nodesById,
		final Map<String, Set<String>> direct,
		final Map<String, Set<String>> resolved,
		final Set<String> visiting
	) {
		Set<String> cached = resolved.get(node.id());
		if (cached != null) {
			return cached;
		}
		if (!visiting.add(node.id())) {
			/* The parser/compiler rejects schedule cycles; fail closed if an unchecked model leaks in. */
			return Set.copyOf(direct.getOrDefault(node.id(), Set.of()));
		}
		LinkedHashSet<String> parameters = new LinkedHashSet<>(
			direct.getOrDefault(node.id(), Set.of())
		);
		if (node.header().schedule() instanceof AfterNode after) {
			CueNode dependency = nodesById.get(after.nodeId());
			if (dependency != null) {
				parameters.addAll(
					resolveNodeParameters(
						dependency,
						nodesById,
						direct,
						resolved,
						visiting
					)
				);
			}
		}
		visiting.remove(node.id());
		Set<String> frozen = Set.copyOf(parameters);
		resolved.put(node.id(), frozen);
		return frozen;
	}

	private static void collectContent(
		final TextContent content,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Set<String> parameters
	) {
		/* Local fields belong to this node even if a locale currently omits their slot. */
		content.fields().values().forEach(field -> collectField(field, parameters));
		collectTemplate(
			content.text(),
			content.fields(),
			cueFields,
			parameters
		);
		if (content.speaker().kind() == SpeakerKind.PLAYER_PARAMETER) {
			content.speaker().parameter()
				.ifPresent(parameters::add);
		}
	}

	private static void collectTemplate(
		final LocalizedTemplate localized,
		final Map<String, DynamicFieldDefinition> localFields,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Set<String> parameters
	) {
		collectTemplate(
			localized.fallback(),
			localFields,
			cueFields,
			parameters
		);
		localized.locales().values().forEach(template ->
			collectTemplate(template, localFields, cueFields, parameters)
		);
	}

	private static void collectTemplate(
		final ComponentTemplate template,
		final Map<String, DynamicFieldDefinition> localFields,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Set<String> parameters
	) {
		for (ComponentPart part : template.parts()) {
			if (!(part instanceof FieldPart fieldPart)) {
				continue;
			}
			DynamicFieldDefinition field = fieldPart.field().scope() == FieldScope.CUE
				? cueFields.get(fieldPart.field().id())
				: localFields.get(fieldPart.field().id());
			collectField(field, parameters);
		}
	}

	private static void collectField(
		final DynamicFieldDefinition field,
		final Set<String> parameters
	) {
		if (field == null) {
			return;
		}
		if (field.source() instanceof ArgumentSource argument) {
			parameters.add(argument.parameter());
			return;
		}
		if (field.source() instanceof BindingSource binding) {
			parameterFromBinding(binding.path())
				.ifPresent(parameters::add);
		}
	}

	private static void collectCondition(
		final ConditionSpec condition,
		final Set<String> parameters
	) {
		condition.expression().ifPresent(expression ->
			BINDINGS.dependencies(expression).stream()
				.map(MessageParameterUseGraph::parameterFromBinding)
				.flatMap(Optional::stream)
				.forEach(parameters::add)
		);
	}

	private static void collectHistory(
		final HistorySpec history,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Set<String> parameters
	) {
		collectHistoryTemplate(history.title(), cueFields, parameters);
		collectHistoryTemplate(history.body(), cueFields, parameters);
		history.savedFields().forEach(field ->
			collectField(cueFields.get(field), parameters)
		);
		if (history.taskSource() == HistoryTaskSource.PARAMETER) {
			history.taskParameter()
				.ifPresent(parameters::add);
		}
	}

	private static void collectHistoryTemplate(
		final LocalizedTemplate localized,
		final Map<String, DynamicFieldDefinition> cueFields,
		final Set<String> parameters
	) {
		collectTemplate(
			localized.fallback(),
			Map.of(),
			cueFields,
			parameters
		);
		localized.locales().values().forEach(template ->
			collectTemplate(template, Map.of(), cueFields, parameters)
		);
	}

	public static Optional<String> parameterFromBinding(final String path) {
		if (
			path == null
				|| !path.startsWith(ARGUMENT_PREFIX)
				|| path.length() == ARGUMENT_PREFIX.length()
		) {
			return Optional.empty();
		}
		String parameter = path.substring(ARGUMENT_PREFIX.length());
		return parameter.indexOf('.') >= 0 || parameter.indexOf('/') >= 0
			? Optional.empty()
			: Optional.of(parameter);
	}

	public record UseGraph(
		Map<String, Set<String>> nodeParameters,
		Set<String> historyParameters
	) {
		public UseGraph {
			Map<String, Set<String>> copied = new LinkedHashMap<>();
			nodeParameters.forEach((nodeId, parameters) ->
				copied.put(
					Objects.requireNonNull(nodeId, "nodeId"),
					Set.copyOf(parameters)
				)
			);
			nodeParameters = Map.copyOf(copied);
			historyParameters = Set.copyOf(historyParameters);
		}

		public Set<String> parametersForNode(final String nodeId) {
			return this.nodeParameters.getOrDefault(
				Objects.requireNonNull(nodeId, "nodeId"),
				Set.of()
			);
		}
	}
}
