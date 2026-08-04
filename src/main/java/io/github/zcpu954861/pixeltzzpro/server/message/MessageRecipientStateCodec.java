package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionEvaluation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OnCueEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stable, pure adapter for the private per-recipient playback state owned by the server runtime.
 *
 * <p>Recipient node occurrences do not have callback triggers, while the persisted occurrence DTO
 * is shared with the callback ledger. This adapter therefore owns the only legal sentinel encoding
 * ({@code START} with no target) and rejects every other representation during recovery.
 *
 * <p>Condition decisions never persist {@link ConditionSpec#toString()} or object hash codes.
 * Conditions receive a deterministic ordinal by walking the frozen cue's node array, then each
 * node header condition followed by text variants. Three disjoint key scopes preserve the three
 * private runtime caches without allowing them to alias after a restart.
 */
public final class MessageRecipientStateCodec {
	private static final String KEY_VERSION = "v1";
	private static final String CUE_START_SCOPE = "c";
	private static final String FROZEN_CALLBACK_SCOPE = "f";
	private static final String NODE_CONDITION_SCOPE = "n";
	private static final Comparator<RecipientOccurrence> OCCURRENCE_ORDER = Comparator
		.comparing(RecipientOccurrence::nodeId)
		.thenComparingInt(RecipientOccurrence::occurrence);

	private MessageRecipientStateCodec() {
	}

	/** Converts structured runtime-facing state into its canonical persistence representation. */
	public static PersistedMessageRuntime.RecipientState encode(
		final MessageCueDefinition cue,
		final RecipientStateSnapshot snapshot
	) {
		return encode(CueIndex.create(cue), snapshot);
	}

	/**
	 * Decodes and validates one persisted recipient against the frozen cue. Invalid occurrence
	 * sentinels, condition keys, condition scopes, node references, repeat indexes, or rich
	 * components reject the entire recipient state.
	 */
	public static RecipientStateSnapshot decode(
		final MessageCueDefinition cue,
		final PersistedMessageRuntime.RecipientState persisted
	) {
		Objects.requireNonNull(persisted, "persisted");
		CueIndex index = CueIndex.create(cue);
		Set<RecipientOccurrence> projected = decodeOccurrences(
			index,
			persisted.projectedOccurrences()
		);
		Set<RecipientOccurrence> decided = decodeOccurrences(
			index,
			persisted.decidedOccurrences()
		);
		if (!decided.containsAll(projected)) {
			throw new IllegalArgumentException(
				"projected recipient occurrences must also be decided"
			);
		}

		Map<Integer, ResolvedComponent> locked = new LinkedHashMap<>();
		for (PersistedMessageRuntime.LockedField value : persisted.lockedFields()) {
			ResolvedComponent previous = locked.put(
				value.slot(),
				ResolvedComponent.parseCanonical(value.canonicalComponent())
			);
			if (previous != null) {
				throw new IllegalArgumentException("duplicate locked recipient field slot");
			}
		}

		Map<ConditionSpec, Boolean> cueStart = new LinkedHashMap<>();
		Map<RecipientOccurrence, Boolean> frozenCallbacks = new LinkedHashMap<>();
		Map<NodeConditionAddress, Boolean> nodeConditions = new LinkedHashMap<>();
		for (PersistedMessageRuntime.ConditionDecision value : persisted.conditionDecisions()) {
			decodeDecision(
				index,
				value,
				cueStart,
				frozenCallbacks,
				nodeConditions
			);
		}

		RecipientStateSnapshot restored = new RecipientStateSnapshot(
			persisted.recipientId(),
			persisted.createdElapsedNanos(),
			persisted.perPlayerAnchorElapsedNanos(),
			projected,
			decided,
			locked,
			cueStart,
			frozenCallbacks,
			nodeConditions,
			persisted.completionAtNanos(),
			persisted.completionCommitted()
		);
		if (!encode(index, restored).equals(persisted)) {
			throw new IllegalArgumentException(
				"recipient state is not in the canonical stable encoding"
			);
		}
		return restored;
	}

	private static PersistedMessageRuntime.RecipientState encode(
		final CueIndex index,
		final RecipientStateSnapshot snapshot
	) {
		Objects.requireNonNull(snapshot, "snapshot");
		if (!snapshot.decidedOccurrences().containsAll(snapshot.projectedOccurrences())) {
			throw new IllegalArgumentException(
				"projected recipient occurrences must also be decided"
			);
		}

		List<PersistedMessageRuntime.Occurrence> projected = snapshot
			.projectedOccurrences()
			.stream()
			.sorted(OCCURRENCE_ORDER)
			.map(value -> encodeOccurrence(index, value))
			.toList();
		List<PersistedMessageRuntime.Occurrence> decided = snapshot
			.decidedOccurrences()
			.stream()
			.sorted(OCCURRENCE_ORDER)
			.map(value -> encodeOccurrence(index, value))
			.toList();
		List<PersistedMessageRuntime.LockedField> locked = snapshot.lockedFields()
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new PersistedMessageRuntime.LockedField(
				entry.getKey(),
				entry.getValue().canonicalJson()
			))
			.toList();

		List<PersistedMessageRuntime.ConditionDecision> conditions = new ArrayList<>();
		snapshot.cueStartConditionDecisions().entrySet().stream()
			.sorted(Comparator.comparingInt(entry -> index.requireCueStart(entry.getKey())))
			.forEach(entry -> conditions.add(
				new PersistedMessageRuntime.ConditionDecision(
					cueStartKey(index.requireCueStart(entry.getKey())),
					entry.getValue()
				)
			));
		snapshot.frozenCallbackDecisions().entrySet().stream()
			.sorted(Map.Entry.comparingByKey(OCCURRENCE_ORDER))
			.forEach(entry -> {
				index.requireFrozenCallback(entry.getKey());
				conditions.add(new PersistedMessageRuntime.ConditionDecision(
					frozenCallbackKey(entry.getKey()),
					entry.getValue()
				));
			});
		snapshot.nodeConditionDecisions().entrySet().stream()
			.sorted(Comparator
				.comparing(
					(Map.Entry<NodeConditionAddress, Boolean> entry) ->
						entry.getKey().occurrence(),
					OCCURRENCE_ORDER
				)
				.thenComparingInt(entry -> index.requireNodeCondition(
					entry.getKey().occurrence(),
					entry.getKey().condition()
				)))
			.forEach(entry -> {
				int ordinal = index.requireNodeCondition(
					entry.getKey().occurrence(),
					entry.getKey().condition()
				);
				conditions.add(new PersistedMessageRuntime.ConditionDecision(
					nodeConditionKey(entry.getKey().occurrence(), ordinal),
					entry.getValue()
				));
			});

		return new PersistedMessageRuntime.RecipientState(
			snapshot.recipientId(),
			snapshot.createdElapsedNanos(),
			snapshot.perPlayerAnchorElapsedNanos(),
			projected,
			decided,
			locked,
			conditions,
			snapshot.completionAtNanos(),
			snapshot.completionCommitted()
		);
	}

	private static PersistedMessageRuntime.Occurrence encodeOccurrence(
		final CueIndex index,
		final RecipientOccurrence value
	) {
		index.requireOccurrence(value);
		return new PersistedMessageRuntime.Occurrence(
			value.nodeId(),
			value.occurrence(),
			PersistedMessageRuntime.CallbackTrigger.START,
			Optional.empty()
		);
	}

	private static Set<RecipientOccurrence> decodeOccurrences(
		final CueIndex index,
		final Collection<PersistedMessageRuntime.Occurrence> values
	) {
		LinkedHashSet<RecipientOccurrence> result = new LinkedHashSet<>();
		for (PersistedMessageRuntime.Occurrence value : values) {
			if (
				value.trigger() != PersistedMessageRuntime.CallbackTrigger.START
					|| value.targetId().isPresent()
			) {
				throw new IllegalArgumentException(
					"recipient occurrence does not use the stable START/no-target sentinel"
				);
			}
			RecipientOccurrence occurrence = new RecipientOccurrence(
				value.nodeId(),
				value.occurrence()
			);
			index.requireOccurrence(occurrence);
			if (!result.add(occurrence)) {
				throw new IllegalArgumentException("duplicate recipient occurrence");
			}
		}
		return Set.copyOf(result);
	}

	private static void decodeDecision(
		final CueIndex index,
		final PersistedMessageRuntime.ConditionDecision value,
		final Map<ConditionSpec, Boolean> cueStart,
		final Map<RecipientOccurrence, Boolean> frozenCallbacks,
		final Map<NodeConditionAddress, Boolean> nodeConditions
	) {
		String[] parts = value.decisionKey().split("\\|", -1);
		if (parts.length < 3 || !parts[0].equals(KEY_VERSION)) {
			throw new IllegalArgumentException("unknown recipient condition decision key");
		}
		switch (parts[1]) {
			case CUE_START_SCOPE -> {
				if (parts.length != 3) {
					throw new IllegalArgumentException("invalid cue-start condition key");
				}
				ConditionSpec condition = index.condition(parseCanonicalInt(parts[2]));
				index.requireCueStart(condition);
				if (cueStart.put(condition, value.matched()) != null) {
					throw new IllegalArgumentException("duplicate cue-start condition decision");
				}
			}
			case FROZEN_CALLBACK_SCOPE -> {
				if (parts.length != 4) {
					throw new IllegalArgumentException("invalid frozen-callback condition key");
				}
				RecipientOccurrence occurrence = new RecipientOccurrence(
					parts[2],
					parseCanonicalInt(parts[3])
				);
				index.requireFrozenCallback(occurrence);
				if (frozenCallbacks.put(occurrence, value.matched()) != null) {
					throw new IllegalArgumentException("duplicate frozen-callback decision");
				}
			}
			case NODE_CONDITION_SCOPE -> {
				if (parts.length != 5) {
					throw new IllegalArgumentException("invalid node condition key");
				}
				RecipientOccurrence occurrence = new RecipientOccurrence(
					parts[2],
					parseCanonicalInt(parts[3])
				);
				ConditionSpec condition = index.condition(parseCanonicalInt(parts[4]));
				index.requireNodeCondition(occurrence, condition);
				NodeConditionAddress address = new NodeConditionAddress(
					occurrence,
					condition
				);
				if (nodeConditions.put(address, value.matched()) != null) {
					throw new IllegalArgumentException("duplicate node condition decision");
				}
			}
			default -> throw new IllegalArgumentException(
				"unknown recipient condition decision scope"
			);
		}
	}

	private static String cueStartKey(final int ordinal) {
		return KEY_VERSION + '|' + CUE_START_SCOPE + '|' + ordinal;
	}

	private static String frozenCallbackKey(final RecipientOccurrence occurrence) {
		return KEY_VERSION + '|' + FROZEN_CALLBACK_SCOPE + '|'
			+ occurrence.nodeId() + '|' + occurrence.occurrence();
	}

	private static String nodeConditionKey(
		final RecipientOccurrence occurrence,
		final int ordinal
	) {
		return KEY_VERSION + '|' + NODE_CONDITION_SCOPE + '|'
			+ occurrence.nodeId() + '|' + occurrence.occurrence() + '|' + ordinal;
	}

	private static int parseCanonicalInt(final String value) {
		if (!value.matches("0|[1-9][0-9]*")) {
			throw new IllegalArgumentException("condition decision integer is not canonical");
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("condition decision integer is out of range", error);
		}
	}

	/** Runtime-facing node occurrence without the persistence-only callback sentinel. */
	public record RecipientOccurrence(String nodeId, int occurrence) {
		public RecipientOccurrence {
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			if (nodeId.isBlank() || nodeId.indexOf('|') >= 0 || occurrence < 0) {
				throw new IllegalArgumentException("invalid recipient occurrence");
			}
		}
	}

	/** Stable address of one node-scoped condition cache entry. */
	public record NodeConditionAddress(
		RecipientOccurrence occurrence,
		ConditionSpec condition
	) {
		public NodeConditionAddress {
			Objects.requireNonNull(occurrence, "occurrence");
			Objects.requireNonNull(condition, "condition");
		}
	}

	/**
	 * Complete structured state needed to checkpoint or rebuild one private recipient stream.
	 * Every collection is copied and null entries are rejected at this boundary.
	 */
	public record RecipientStateSnapshot(
		UUID recipientId,
		long createdElapsedNanos,
		Optional<Long> perPlayerAnchorElapsedNanos,
		Set<RecipientOccurrence> projectedOccurrences,
		Set<RecipientOccurrence> decidedOccurrences,
		Map<Integer, ResolvedComponent> lockedFields,
		Map<ConditionSpec, Boolean> cueStartConditionDecisions,
		Map<RecipientOccurrence, Boolean> frozenCallbackDecisions,
		Map<NodeConditionAddress, Boolean> nodeConditionDecisions,
		Optional<Long> completionAtNanos,
		boolean completionCommitted
	) {
		public RecipientStateSnapshot {
			Objects.requireNonNull(recipientId, "recipientId");
			if (createdElapsedNanos < 0L) {
				throw new IllegalArgumentException("recipient creation elapsed cannot be negative");
			}
			perPlayerAnchorElapsedNanos = nonNegativeOptional(
				perPlayerAnchorElapsedNanos,
				"perPlayerAnchorElapsedNanos"
			);
			projectedOccurrences = immutableSet(projectedOccurrences, "projectedOccurrences");
			decidedOccurrences = immutableSet(decidedOccurrences, "decidedOccurrences");
			lockedFields = immutableMap(lockedFields, "lockedFields");
			cueStartConditionDecisions = immutableMap(
				cueStartConditionDecisions,
				"cueStartConditionDecisions"
			);
			frozenCallbackDecisions = immutableMap(
				frozenCallbackDecisions,
				"frozenCallbackDecisions"
			);
			nodeConditionDecisions = immutableMap(
				nodeConditionDecisions,
				"nodeConditionDecisions"
			);
			completionAtNanos = nonNegativeOptional(completionAtNanos, "completionAtNanos");
			if (completionCommitted && completionAtNanos.isEmpty()) {
				throw new IllegalArgumentException(
					"committed recipient completion requires a frozen completion point"
				);
			}
		}
	}

	private static final class CueIndex {
		private final Map<String, CueNode> nodes;
		private final Map<ConditionSpec, Integer> conditionOrdinals;
		private final List<ConditionSpec> conditions;
		private final Map<String, Set<ConditionSpec>> nodeConditions;

		private CueIndex(
			final Map<String, CueNode> nodes,
			final Map<ConditionSpec, Integer> conditionOrdinals,
			final List<ConditionSpec> conditions,
			final Map<String, Set<ConditionSpec>> nodeConditions
		) {
			this.nodes = Map.copyOf(nodes);
			this.conditionOrdinals = Map.copyOf(conditionOrdinals);
			this.conditions = List.copyOf(conditions);
			this.nodeConditions = Map.copyOf(nodeConditions);
		}

		private static CueIndex create(final MessageCueDefinition cue) {
			Objects.requireNonNull(cue, "cue");
			Map<String, CueNode> nodes = new LinkedHashMap<>();
			Map<ConditionSpec, Integer> ordinals = new LinkedHashMap<>();
			List<ConditionSpec> conditions = new ArrayList<>();
			Map<String, Set<ConditionSpec>> byNode = new LinkedHashMap<>();
			for (CueNode node : cue.nodes()) {
				if (nodes.put(node.id(), node) != null) {
					throw new IllegalArgumentException("frozen cue contains duplicate node ids");
				}
				LinkedHashSet<ConditionSpec> local = new LinkedHashSet<>();
				node.header().when().ifPresent(local::add);
				if (node instanceof TextNode text) {
					text.variants().forEach(variant -> local.add(variant.when()));
				}
				byNode.put(node.id(), Set.copyOf(local));
				for (ConditionSpec condition : local) {
					if (!ordinals.containsKey(condition)) {
						ordinals.put(condition, conditions.size());
						conditions.add(condition);
					}
				}
			}
			return new CueIndex(nodes, ordinals, conditions, byNode);
		}

		private void requireOccurrence(final RecipientOccurrence occurrence) {
			CueNode node = this.nodes.get(occurrence.nodeId());
			if (
				node == null
					|| occurrence.occurrence() >= node.header().repeat().count()
			) {
				throw new IllegalArgumentException(
					"recipient occurrence does not belong to the frozen cue"
				);
			}
		}

		private int requireCueStart(final ConditionSpec condition) {
			Integer ordinal = this.conditionOrdinals.get(condition);
			if (
				ordinal == null
					|| condition.evaluateAt() != ConditionEvaluation.CUE_START
			) {
				throw new IllegalArgumentException(
					"cue-start decision does not belong to a frozen cue-start condition"
				);
			}
			return ordinal;
		}

		private void requireFrozenCallback(final RecipientOccurrence occurrence) {
			requireOccurrence(occurrence);
			CueNode node = this.nodes.get(occurrence.nodeId());
			if (
				!(node instanceof CallbackNode)
					|| node.header().schedule() instanceof OnCueEvent
					|| node.header().when()
						.filter(condition ->
							condition.evaluateAt() == ConditionEvaluation.CUE_START
						)
						.isEmpty()
			) {
				throw new IllegalArgumentException(
					"frozen callback decision does not match a cue-start callback"
				);
			}
		}

		private int requireNodeCondition(
			final RecipientOccurrence occurrence,
			final ConditionSpec condition
		) {
			requireOccurrence(occurrence);
			if (!this.nodeConditions.get(occurrence.nodeId()).contains(condition)) {
				throw new IllegalArgumentException(
					"node condition decision does not belong to its frozen cue node"
				);
			}
			return this.conditionOrdinals.get(condition);
		}

		private ConditionSpec condition(final int ordinal) {
			if (ordinal < 0 || ordinal >= this.conditions.size()) {
				throw new IllegalArgumentException("condition ordinal is outside the frozen cue");
			}
			return this.conditions.get(ordinal);
		}
	}

	private static Optional<Long> nonNegativeOptional(
		final Optional<Long> value,
		final String field
	) {
		Objects.requireNonNull(value, field);
		if (value.filter(item -> item < 0L).isPresent()) {
			throw new IllegalArgumentException(field + " cannot be negative");
		}
		return value;
	}

	private static <T> Set<T> immutableSet(
		final Collection<T> values,
		final String field
	) {
		Objects.requireNonNull(values, field);
		LinkedHashSet<T> copy = new LinkedHashSet<>();
		for (T value : values) {
			copy.add(Objects.requireNonNull(value, field + " entry"));
		}
		return Set.copyOf(copy);
	}

	private static <K, V> Map<K, V> immutableMap(
		final Map<K, V> values,
		final String field
	) {
		Objects.requireNonNull(values, field);
		Map<K, V> copy = new LinkedHashMap<>();
		values.forEach((key, value) -> copy.put(
			Objects.requireNonNull(key, field + " key"),
			Objects.requireNonNull(value, field + " value")
		));
		return Map.copyOf(copy);
	}
}
