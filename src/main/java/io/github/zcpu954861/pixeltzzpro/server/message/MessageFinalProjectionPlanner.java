package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionEvaluation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OnCueEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScheduleSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.NodeOccurrence;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.EventKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Builds the complete text occurrence set that a recipient's final projection may contain.
 * Event-driven nodes are admitted only from authoritative facts already recorded on the instance;
 * sound and callback side effects are never replayed. Condition evaluation, sensitive
 * authorization, compilation, and transport remain runtime duties.
 */
public final class MessageFinalProjectionPlanner {
	private MessageFinalProjectionPlanner() {
	}

	/**
	 * Returns only occurrences that are now authorized by final facts and are not already present.
	 * The returned set preserves cue declaration order and then repeat occurrence order.
	 */
	public static Set<NodeOccurrence> plan(
		final MessageInstance instance,
		final UUID recipient,
		final Set<NodeOccurrence> currentlyAuthorized
	) {
		Plan plan = plan(instance, recipient, currentlyAuthorized, Set.of());
		return Collections.unmodifiableSet(new LinkedHashSet<>(plan.newOccurrences()));
	}

	public static Plan plan(
		final MessageInstance instance,
		final UUID recipient,
		final Set<NodeOccurrence> alreadyProjected,
		final Set<NodeOccurrence> alreadyDecided
	) {
		Objects.requireNonNull(instance, "instance");
		return plan(
			instance.cue(),
			recipient,
			instance.cueTargets(),
			instance.nodeTargets(),
			instance.eventOffsets(),
			alreadyProjected,
			alreadyDecided
		);
	}

	/**
	 * Pure planning entry point for recovery/finalization tests and runtime adapters.
	 */
	public static Plan plan(
		final MessageCueDefinition cue,
		final UUID recipient,
		final Set<UUID> cueTargets,
		final Map<String, Set<UUID>> nodeTargets,
		final Map<EventKey, Long> eventOffsets,
		final Set<NodeOccurrence> alreadyProjected,
		final Set<NodeOccurrence> alreadyDecided
	) {
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(recipient, "recipient");
		Objects.requireNonNull(cueTargets, "cueTargets");
		Objects.requireNonNull(nodeTargets, "nodeTargets");
		Objects.requireNonNull(eventOffsets, "eventOffsets");
		Set<NodeOccurrence> projected = Set.copyOf(
			Objects.requireNonNull(alreadyProjected, "alreadyProjected")
		);
		Set<NodeOccurrence> decided = Set.copyOf(
			Objects.requireNonNull(alreadyDecided, "alreadyDecided")
		);
		Map<String, CueNode> nodesById = new java.util.LinkedHashMap<>();
		for (CueNode node : cue.nodes()) {
			nodesById.put(node.id(), node);
		}

		List<NodeOccurrence> ordered = new ArrayList<>();
		Set<NodeOccurrence> seen = new LinkedHashSet<>();
		for (CueNode node : cue.nodes()) {
			/*
			 * Final projection repairs visible text only. Re-admitting a sound or callback here
			 * would replay a side effect merely because a recipient reconnected or recovered.
			 */
			if (!(node instanceof TextNode)) {
				continue;
			}
			Set<UUID> targets = Objects.requireNonNull(
				nodeTargets.getOrDefault(node.id(), cueTargets),
				"targets for node " + node.id()
			);
			if (
				!targets.contains(recipient)
					|| !scheduleOccurred(
						node,
						recipient,
						eventOffsets,
						nodesById,
						new LinkedHashSet<>()
					)
			) {
				continue;
			}
			for (int occurrence = 0; occurrence < node.header().repeat().count(); occurrence++) {
				NodeOccurrence candidate = new NodeOccurrence(node.id(), occurrence);
				if (seen.add(candidate)) {
					ordered.add(candidate);
				}
			}
		}

		List<NodeOccurrence> fresh = ordered.stream()
			.filter(occurrence -> !projected.contains(occurrence))
			.filter(occurrence -> !decided.contains(occurrence))
			.toList();
		return new Plan(ordered, fresh);
	}

	/**
	 * Selects the exact node ids a recipient may receive at a final-projection boundary.
	 * A sensitive/live-strict caller must provide the newly resolved live view; absence fails
	 * closed instead of falling back to the invocation-time snapshot.
	 */
	public static Optional<Set<String>> authorizedNodeIds(
		final MessageCueDefinition cue,
		final UUID recipient,
		final FinalAudienceView snapshot,
		final boolean liveViewRequired,
		final Optional<FinalAudienceView> liveView
	) {
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(recipient, "recipient");
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(liveView, "liveView");
		FinalAudienceView selected;
		if (liveViewRequired) {
			if (liveView.isEmpty()) {
				return Optional.empty();
			}
			selected = liveView.orElseThrow();
		} else {
			selected = snapshot;
		}
		if (!selected.cueTargets().contains(recipient)) {
			return Optional.empty();
		}
		Set<String> authorized = new LinkedHashSet<>();
		for (CueNode node : cue.nodes()) {
			if (
				selected.nodeTargets()
					.getOrDefault(node.id(), selected.cueTargets())
					.contains(recipient)
			) {
				authorized.add(node.id());
			}
		}
		return Optional.of(Set.copyOf(authorized));
	}

	/**
	 * Freezes one condition exactly once for final projection. A missing cue-start decision fails
	 * closed and never samples current state; later evaluation modes may sample current state only
	 * for their first unresolved decision.
	 */
	public static <K> boolean freezeConditionDecision(
		final Map<K, Boolean> occurrenceDecisions,
		final K key,
		final ConditionEvaluation evaluation,
		final Optional<Boolean> cueStartDecision,
		final BooleanSupplier liveDecision
	) {
		Objects.requireNonNull(occurrenceDecisions, "occurrenceDecisions");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(evaluation, "evaluation");
		Objects.requireNonNull(cueStartDecision, "cueStartDecision");
		Objects.requireNonNull(liveDecision, "liveDecision");
		Boolean frozen = occurrenceDecisions.get(key);
		if (frozen != null) {
			return frozen;
		}
		boolean resolved = evaluation == ConditionEvaluation.CUE_START
			? cueStartDecision.orElse(false)
			: liveDecision.getAsBoolean();
		occurrenceDecisions.put(key, resolved);
		return resolved;
	}

	private static boolean scheduleOccurred(
		final CueNode node,
		final UUID recipient,
		final Map<EventKey, Long> eventOffsets,
		final Map<String, CueNode> nodesById,
		final Set<String> visiting
	) {
		ScheduleSpec schedule = node.header().schedule();
		if (schedule instanceof AtCueTime) {
			return true;
		}
		if (schedule instanceof AfterNode after) {
			if (!visiting.add(node.id())) {
				return false;
			}
			CueNode dependency = nodesById.get(after.nodeId());
			boolean occurred = dependency != null
				&& scheduleOccurred(
					dependency,
					recipient,
					eventOffsets,
					nodesById,
					visiting
				);
			visiting.remove(node.id());
			return occurred;
		}
		if (schedule instanceof OnCueEvent onEvent) {
			CallbackTrigger trigger = onEvent.trigger();
			if (trigger == CallbackTrigger.START) {
				return true;
			}
			Optional<UUID> target = trigger == CallbackTrigger.TARGET_COMPLETE
				? Optional.of(recipient)
				: Optional.empty();
			return eventOffsets.containsKey(new EventKey(trigger, target));
		}
		throw new IllegalStateException("unsupported message node schedule " + schedule);
	}

	public record Plan(
		List<NodeOccurrence> orderedOccurrences,
		List<NodeOccurrence> newOccurrences
	) {
		public Plan {
			orderedOccurrences = List.copyOf(orderedOccurrences);
			newOccurrences = List.copyOf(newOccurrences);
		}
	}

	public record FinalAudienceView(
		Set<UUID> cueTargets,
		Map<String, Set<UUID>> nodeTargets
	) {
		public FinalAudienceView {
			cueTargets = Set.copyOf(cueTargets);
			Map<String, Set<UUID>> copied = new java.util.LinkedHashMap<>();
			nodeTargets.forEach((nodeId, targets) ->
				copied.put(
					Objects.requireNonNull(nodeId, "nodeId"),
					Set.copyOf(targets)
				)
			);
			nodeTargets = Map.copyOf(copied);
		}
	}
}
