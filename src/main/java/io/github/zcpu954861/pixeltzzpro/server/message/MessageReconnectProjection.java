package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.NodeOccurrence;
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

/**
 * Produces the minimum playback state that a new physical connection may receive.
 *
 * <p>The server keeps a richer logical stream across disconnects so callbacks, frozen fields, and
 * completion remain authoritative. That logical stream is not itself safe to resend: live-strict
 * authorization may have removed nodes since the previous connection, and its last wire sequence
 * belongs to the old client inbox. This projection therefore filters by current node authority,
 * derives the exact declared field slots, filters the locked snapshot to those slots, and rebases
 * the fresh plan as a standalone {@link PlanDisposition#CREATE} packet.</p>
 */
public final class MessageReconnectProjection {
	private MessageReconnectProjection() {
	}

	public static Optional<Projection> project(
		final MessagePlaybackPlan logicalPlan,
		final Map<NodeOccurrence, ? extends Collection<ResolvedNode>> nodesByOccurrence,
		final Set<String> authorizedNodeIds,
		final Map<Integer, MessagePlaybackPlan.ResolvedComponent> lockedValues,
		final long connectionPlanSequence
	) {
		return project(
			logicalPlan,
			nodesByOccurrence,
			authorizedNodeIds,
			nodesByOccurrence.keySet(),
			lockedValues,
			connectionPlanSequence
		);
	}

	/**
	 * Projects a caller-selected occurrence subset. Final-only delivery uses this overload so a
	 * fresh connection receives only the final planner's visible text occurrences and never
	 * replays a historical sound node retained by the logical stream.
	 */
	public static Optional<Projection> project(
		final MessagePlaybackPlan logicalPlan,
		final Map<NodeOccurrence, ? extends Collection<ResolvedNode>> nodesByOccurrence,
		final Set<String> authorizedNodeIds,
		final Set<NodeOccurrence> allowedOccurrences,
		final Map<Integer, MessagePlaybackPlan.ResolvedComponent> lockedValues,
		final long connectionPlanSequence
	) {
		Objects.requireNonNull(logicalPlan, "logicalPlan");
		Objects.requireNonNull(nodesByOccurrence, "nodesByOccurrence");
		Set<String> authorized = Set.copyOf(
			Objects.requireNonNull(authorizedNodeIds, "authorizedNodeIds")
		);
		Set<NodeOccurrence> allowed = Set.copyOf(
			Objects.requireNonNull(allowedOccurrences, "allowedOccurrences")
		);
		Objects.requireNonNull(lockedValues, "lockedValues");
		if (connectionPlanSequence < 0L || connectionPlanSequence == Long.MAX_VALUE) {
			throw new IllegalArgumentException(
				"connection plan sequence must leave room for a successor"
			);
		}

		Map<Integer, ResolvedNode> logicalByOrdinal = new LinkedHashMap<>();
		for (ResolvedNode node : logicalPlan.nodes()) {
			logicalByOrdinal.put(node.ordinal(), node);
		}
		Map<Integer, NodeOccurrence> owners = new LinkedHashMap<>();
		Set<Integer> allowedOrdinals = new LinkedHashSet<>();
		for (
			Map.Entry<NodeOccurrence, ? extends Collection<ResolvedNode>> entry :
				nodesByOccurrence.entrySet()
		) {
			NodeOccurrence occurrence = Objects.requireNonNull(
				entry.getKey(),
				"node occurrence"
			);
			for (ResolvedNode node : entry.getValue()) {
				ResolvedNode logical = logicalByOrdinal.get(node.ordinal());
				if (logical == null || !logical.equals(node)) {
					throw new IllegalArgumentException(
						"occurrence map does not match logical plan ordinal "
							+ node.ordinal()
					);
				}
				NodeOccurrence previous = owners.putIfAbsent(
					node.ordinal(),
					occurrence
				);
				if (previous != null && !previous.equals(occurrence)) {
					throw new IllegalArgumentException(
						"logical node ordinal belongs to multiple source occurrences: "
							+ node.ordinal()
					);
				}
				if (
					allowed.contains(occurrence)
						&& authorized.contains(occurrence.nodeId())
				) {
					allowedOrdinals.add(node.ordinal());
				}
			}
		}

		List<ResolvedNode> nodes = logicalPlan.nodes()
			.stream()
			.filter(node -> allowedOrdinals.contains(node.ordinal()))
			.toList();
		if (nodes.isEmpty()) {
			return Optional.empty();
		}
		Set<Integer> declaredSlots = declaredSlots(nodes);
		List<FieldValue> lockedSnapshot = lockedValues.entrySet()
			.stream()
			.filter(entry -> declaredSlots.contains(entry.getKey()))
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new FieldValue(entry.getKey(), entry.getValue()))
			.toList();
		MessagePlaybackPlan plan = new MessagePlaybackPlan(
			logicalPlan.instanceId(),
			logicalPlan.cueId(),
			logicalPlan.generation(),
			connectionPlanSequence,
			logicalPlan.cycle(),
			PlanDisposition.CREATE,
			Optional.empty(),
			logicalPlan.clock(),
			logicalPlan.policies(),
			logicalPlan.origin(),
			nodes
		);
		return Optional.of(
			new Projection(plan, declaredSlots, lockedSnapshot)
		);
	}

	private static Set<Integer> declaredSlots(final List<ResolvedNode> nodes) {
		Set<Integer> result = new LinkedHashSet<>();
		for (ResolvedNode node : nodes) {
			if (!(node instanceof ResolvedTextNode text)) {
				continue;
			}
			for (ResolvedSegment segment : text.segments()) {
				if (segment instanceof FieldSlotSegment field) {
					result.add(field.slot());
				}
			}
		}
		return Set.copyOf(result);
	}

	public static long nextSequence(final long acceptedSequence) {
		if (acceptedSequence < 0L || acceptedSequence == Long.MAX_VALUE) {
			throw new IllegalArgumentException(
				"accepted sequence must leave room for a successor"
			);
		}
		return acceptedSequence + 1L;
	}

	public record Projection(
		MessagePlaybackPlan plan,
		Set<Integer> declaredSlots,
		List<FieldValue> lockedSnapshot
	) {
		public Projection {
			plan = Objects.requireNonNull(plan, "plan");
			if (
				plan.disposition() != PlanDisposition.CREATE
					|| plan.replacedInstanceId().isPresent()
			) {
				throw new IllegalArgumentException(
					"a reconnect projection must be a standalone CREATE plan"
				);
			}
			Set<Integer> copiedSlots = Set.copyOf(declaredSlots);
			declaredSlots = copiedSlots;
			if (
				!copiedSlots.equals(
					MessageReconnectProjection.declaredSlots(plan.nodes())
				)
			) {
				throw new IllegalArgumentException(
					"declared reconnect slots must exactly match the projected plan"
				);
			}
			List<FieldValue> sorted = new ArrayList<>(
				Objects.requireNonNull(lockedSnapshot, "lockedSnapshot")
			);
			sorted.sort(Comparator.comparingInt(FieldValue::slot));
			Set<Integer> lockedSlots = new LinkedHashSet<>();
			for (FieldValue value : sorted) {
				if (!lockedSlots.add(value.slot())) {
					throw new IllegalArgumentException(
						"locked reconnect snapshot contains duplicate slots"
					);
				}
			}
			lockedSnapshot = List.copyOf(sorted);
			if (
				lockedSnapshot.stream().anyMatch(value ->
					!copiedSlots.contains(value.slot())
				)
			) {
				throw new IllegalArgumentException(
					"locked reconnect snapshot contains an undeclared slot"
				);
			}
		}

		/** The next event on this connection must use exactly this value. */
		public long nextSequence() {
			return MessageReconnectProjection.nextSequence(this.plan.sequence());
		}
	}
}
