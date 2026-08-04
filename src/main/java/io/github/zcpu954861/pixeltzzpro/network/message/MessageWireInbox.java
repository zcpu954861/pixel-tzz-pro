package io.github.zcpu954861.pixeltzzpro.network.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded client-side inbox for one strictly ordered stream per authorized message instance.
 *
 * <p>Fabric play payloads are ordered, but the renderer may not consume a plan before its field
 * deltas and controls arrive. This inbox keeps that short gap lossless without accepting
 * out-of-order, cross-generation, unknown-slot, or post-terminal data. It contains no server
 * definition, predicate, audience, callback, or parameter-source model.
 */
public final class MessageWireInbox {
	public static final int MAX_ACTIVE_INSTANCES = 32;
	public static final int MAX_PENDING_EVENTS_PER_INSTANCE = 128;
	public static final int MAX_PENDING_EVENTS = 512;
	public static final int MAX_BUFFERED_BYTES = 4 * 1024 * 1024;
	private static final int MAX_RETIRED_CYCLES = 128;

	private final int maximumActiveInstances;
	private final int maximumPendingEventsPerInstance;
	private final int maximumPendingEvents;
	private final int maximumBufferedBytes;
	private final Map<UUID, StreamState> streams = new LinkedHashMap<>();
	private final Map<UUID, CycleStamp> retiredCycles = new LinkedHashMap<>();
	private int pendingEvents;
	private int bufferedBytes;

	public MessageWireInbox() {
		this(
			MAX_ACTIVE_INSTANCES,
			MAX_PENDING_EVENTS_PER_INSTANCE,
			MAX_PENDING_EVENTS,
			MAX_BUFFERED_BYTES
		);
	}

	/**
	 * Explicit limits keep the production defaults fixed while allowing small deterministic tests.
	 */
	public MessageWireInbox(
		final int maximumActiveInstances,
		final int maximumPendingEventsPerInstance,
		final int maximumPendingEvents,
		final int maximumBufferedBytes
	) {
		if (
			maximumActiveInstances < 1
				|| maximumActiveInstances > MAX_ACTIVE_INSTANCES
		) {
			throw new IllegalArgumentException(
				"maximumActiveInstances must remain within 1.."
					+ MAX_ACTIVE_INSTANCES
			);
		}
		if (
			maximumPendingEventsPerInstance < 1
				|| maximumPendingEventsPerInstance
					> MAX_PENDING_EVENTS_PER_INSTANCE
		) {
			throw new IllegalArgumentException(
				"maximumPendingEventsPerInstance must remain within 1.."
					+ MAX_PENDING_EVENTS_PER_INSTANCE
			);
		}
		if (
			maximumPendingEvents < 1
				|| maximumPendingEvents > MAX_PENDING_EVENTS
				|| maximumPendingEvents < maximumPendingEventsPerInstance
		) {
			throw new IllegalArgumentException(
				"maximumPendingEvents must cover one instance and remain within 1.."
					+ MAX_PENDING_EVENTS
			);
		}
		if (
			maximumBufferedBytes < MessagePlaybackPlan.MAX_PLAN_BYTES
				|| maximumBufferedBytes > MAX_BUFFERED_BYTES
		) {
			throw new IllegalArgumentException(
				"maximumBufferedBytes must remain within "
					+ MessagePlaybackPlan.MAX_PLAN_BYTES
					+ ".."
					+ MAX_BUFFERED_BYTES
			);
		}
		this.maximumActiveInstances = maximumActiveInstances;
		this.maximumPendingEventsPerInstance =
			maximumPendingEventsPerInstance;
		this.maximumPendingEvents = maximumPendingEvents;
		this.maximumBufferedBytes = maximumBufferedBytes;
	}

	public synchronized OfferResult offer(final MessagePlanS2CPayload payload) {
		Objects.requireNonNull(payload, "payload");
		MessagePlaybackPlan plan = payload.plan();
		StreamState existing = this.streams.get(plan.instanceId());
		if (existing != null) {
			if (plan.disposition() != MessagePlaybackPlan.PlanDisposition.REFRESH) {
				/*
				 * A verified server may defensively retransmit an otherwise byte-identical CREATE with
				 * the next sequence while reconciling an integrated connection. It must not restart the
				 * presentation, but rejecting it without consuming that sequence poisons every later
				 * field/control packet. Accept only the exact same logical body and advance the envelope.
				 */
				if (isIdempotentCreate(existing, plan)) {
					existing.lastSequence = plan.sequence();
					return OfferResult.ACCEPTED;
				}
				return OfferResult.DUPLICATE_INSTANCE;
			}
			if (plan.generation() != existing.plan.generation()) {
				return OfferResult.GENERATION_MISMATCH;
			}
			if (plan.cycle() <= existing.plan.cycle()) {
				return OfferResult.STALE_CYCLE;
			}
			return replaceStream(existing, plan);
		}
		if (plan.disposition() == MessagePlaybackPlan.PlanDisposition.REFRESH) {
			CycleStamp retired = this.retiredCycles.get(plan.instanceId());
			if (retired == null) {
				return OfferResult.UNKNOWN_INSTANCE;
			}
			if (plan.generation() != retired.generation()) {
				return OfferResult.GENERATION_MISMATCH;
			}
			if (plan.cycle() <= retired.cycle()) {
				return OfferResult.STALE_CYCLE;
			}
		}
		StreamState replaced = plan.replacedInstanceId()
			.map(this.streams::get)
			.orElse(null);
		int resultingInstances = this.streams.size()
			- (replaced == null ? 0 : 1)
			+ 1;
		if (resultingInstances > this.maximumActiveInstances) {
			return OfferResult.ACTIVE_INSTANCE_LIMIT;
		}
		int encodedBytes = plan.encode().length;
		int releasedBytes = retainedBytes(replaced);
		if (!hasByteCapacityAfterRelease(encodedBytes, releasedBytes)) {
			return OfferResult.BUFFER_CAPACITY;
		}
		if (replaced != null) {
			removeStream(replaced);
		}
		plan.replacedInstanceId().ifPresent(this.retiredCycles::remove);
		this.retiredCycles.remove(plan.instanceId());
		this.streams.put(
			plan.instanceId(),
			new StreamState(plan, encodedBytes, declaredSlots(plan))
		);
		this.bufferedBytes += encodedBytes;
		return OfferResult.ACCEPTED;
	}

	private static boolean isIdempotentCreate(
		final StreamState existing,
		final MessagePlaybackPlan incoming
	) {
		MessagePlaybackPlan current = existing.plan;
		return existing.lastSequence != Long.MAX_VALUE
			&& incoming.sequence() == existing.lastSequence + 1L
			&& incoming.generation() == current.generation()
			&& incoming.cycle() == current.cycle()
			&& incoming.disposition() == current.disposition()
			&& incoming.cueId().equals(current.cueId())
			&& incoming.replacedInstanceId().equals(current.replacedInstanceId())
			&& incoming.clock().equals(current.clock())
			&& incoming.policies().equals(current.policies())
			&& incoming.origin().equals(current.origin())
			&& incoming.nodes().equals(current.nodes());
	}

	private OfferResult replaceStream(
		final StreamState existing,
		final MessagePlaybackPlan plan
	) {
		int encodedBytes = plan.encode().length;
		int releasedBytes = retainedBytes(existing);
		if (!hasByteCapacityAfterRelease(encodedBytes, releasedBytes)) {
			return OfferResult.BUFFER_CAPACITY;
		}
		removeStream(existing);
		this.retiredCycles.remove(plan.instanceId());
		this.streams.put(
			plan.instanceId(),
			new StreamState(plan, encodedBytes, declaredSlots(plan))
		);
		this.bufferedBytes += encodedBytes;
		return OfferResult.ACCEPTED;
	}

	public synchronized OfferResult offer(
		final MessageFieldDeltaS2CPayload payload
	) {
		Objects.requireNonNull(payload, "payload");
		StreamState stream = this.streams.get(payload.instanceId());
		OfferResult envelope = validateEnvelope(
			stream,
			payload.generation(),
			payload.sequence()
		);
		if (envelope != OfferResult.ACCEPTED) {
			return envelope;
		}
		for (FieldValue value : payload.values()) {
			if (!stream.declaredSlots.contains(value.slot())) {
				return OfferResult.UNKNOWN_FIELD_SLOT;
			}
			if (stream.lockedSlots.contains(value.slot())) {
				return OfferResult.FIELD_ALREADY_LOCKED;
			}
		}
		int encodedBytes = estimatedDeltaBytes(payload.values());
		OfferResult capacity = validateEventCapacity(stream, encodedBytes);
		if (capacity != OfferResult.ACCEPTED) {
			return capacity;
		}
		payload.values().forEach(value -> stream.lockedSlots.add(value.slot()));
		appendEvent(stream, new FieldDeltaEvent(payload, encodedBytes));
		return OfferResult.ACCEPTED;
	}

	public synchronized OfferResult offer(
		final MessageNodeAppendS2CPayload payload
	) {
		Objects.requireNonNull(payload, "payload");
		StreamState stream = this.streams.get(payload.instanceId());
		OfferResult envelope = validateEnvelope(
			stream,
			payload.generation(),
			payload.sequence()
		);
		if (envelope != OfferResult.ACCEPTED) {
			return envelope;
		}
		Set<Integer> appendedOrdinals = new HashSet<>();
		Set<Integer> appendedSlots = new HashSet<>();
		int appendedSegments = 0;
		for (MessagePlaybackPlan.ResolvedNode node : payload.nodes()) {
			if (
				!appendedOrdinals.add(node.ordinal())
					|| stream.nodeOrdinals.contains(node.ordinal())
			) {
				return OfferResult.DUPLICATE_NODE_ORDINAL;
			}
			if (node instanceof ResolvedTextNode text) {
				appendedSegments = Math.addExact(
					appendedSegments,
					text.segments().size()
				);
				for (ResolvedSegment segment : text.segments()) {
					if (segment instanceof FieldSlotSegment field) {
						appendedSlots.add(field.slot());
					}
				}
			}
		}
		if (
			stream.nodeOrdinals.size() + appendedOrdinals.size()
				> MessagePlaybackPlan.MAX_NODES
		) {
			return OfferResult.NODE_LIMIT;
		}
		if (
			stream.segmentCount + appendedSegments
				> MessagePlaybackPlan.MAX_TOTAL_SEGMENTS
		) {
			return OfferResult.SEGMENT_LIMIT;
		}
		Set<Integer> resultingSlots = new HashSet<>(stream.declaredSlots);
		resultingSlots.addAll(appendedSlots);
		if (resultingSlots.size() > MessagePlaybackPlan.MAX_FIELD_SLOTS) {
			return OfferResult.FIELD_SLOT_LIMIT;
		}
		int encodedBytes = payload.encodedSize();
		OfferResult capacity = validateEventCapacity(stream, encodedBytes);
		if (capacity != OfferResult.ACCEPTED) {
			return capacity;
		}
		stream.nodeOrdinals.addAll(appendedOrdinals);
		stream.declaredSlots.addAll(appendedSlots);
		stream.segmentCount += appendedSegments;
		appendEvent(stream, new NodeAppendEvent(payload, encodedBytes));
		return OfferResult.ACCEPTED;
	}

	public synchronized OfferResult offer(
		final MessageControlS2CPayload payload
	) {
		Objects.requireNonNull(payload, "payload");
		StreamState stream = this.streams.get(payload.instanceId());
		OfferResult envelope = validateEnvelope(
			stream,
			payload.generation(),
			payload.sequence()
		);
		if (envelope != OfferResult.ACCEPTED) {
			return envelope;
		}
		if (payload.action().finalizes()) {
			Set<Integer> finalSlots = new HashSet<>();
			for (FieldValue value : payload.finalValues()) {
				if (!stream.declaredSlots.contains(value.slot())) {
					return OfferResult.UNKNOWN_FIELD_SLOT;
				}
				if (stream.lockedSlots.contains(value.slot())) {
					return OfferResult.FIELD_ALREADY_LOCKED;
				}
				finalSlots.add(value.slot());
			}
			Set<Integer> remaining = new HashSet<>(stream.declaredSlots);
			remaining.removeAll(stream.lockedSlots);
			if (!finalSlots.equals(remaining)) {
				return OfferResult.FINALIZE_SLOT_MISMATCH;
			}
		}
		int encodedBytes = estimatedControlBytes(payload.finalValues());
		OfferResult capacity = validateEventCapacity(stream, encodedBytes);
		if (capacity != OfferResult.ACCEPTED) {
			return capacity;
		}
		if (payload.action().finalizes()) {
			payload.finalValues()
				.forEach(value -> stream.lockedSlots.add(value.slot()));
		}
		appendEvent(stream, new ControlEvent(payload, encodedBytes));
		if (
			payload.action().finalizes()
				|| payload.action() == MessageControlS2CPayload.Action.CANCEL
		) {
			stream.terminal = true;
		}
		return OfferResult.ACCEPTED;
	}

	/**
	 * Takes the plan exactly once. Events received beforehand remain available to drain.
	 */
	public synchronized Optional<MessagePlaybackPlan> takePlan(
		final UUID instanceId
	) {
		StreamState stream = this.streams.get(
			Objects.requireNonNull(instanceId, "instanceId")
		);
		if (stream == null || stream.planConsumed) {
			return Optional.empty();
		}
		stream.planConsumed = true;
		this.bufferedBytes -= stream.planBytes;
		return Optional.of(stream.plan);
	}

	/**
	 * Drains only after the corresponding plan has been consumed.
	 */
	public synchronized List<WireEvent> drainEvents(final UUID instanceId) {
		StreamState stream = this.streams.get(
			Objects.requireNonNull(instanceId, "instanceId")
		);
		if (
			stream == null
				|| !stream.planConsumed
				|| stream.pending.isEmpty()
		) {
			return List.of();
		}
		List<WireEvent> result = new ArrayList<>(stream.pending.size());
		while (!stream.pending.isEmpty()) {
			WireEvent event = stream.pending.removeFirst();
			result.add(event);
			stream.pendingBytes -= event.encodedBytes();
			this.bufferedBytes -= event.encodedBytes();
			this.pendingEvents--;
		}
		return List.copyOf(result);
	}

	public synchronized boolean remove(final UUID instanceId) {
		StreamState removed = this.streams.get(
			Objects.requireNonNull(instanceId, "instanceId")
		);
		if (removed == null) {
			return false;
		}
		removeStream(removed);
		return true;
	}

	/**
	 * Releases rendered state while retaining only the last accepted cycle so a later
	 * authoritative REFRESH of the same logical instance can still reset the stream.
	 */
	public synchronized boolean retire(final UUID instanceId) {
		StreamState removed = this.streams.get(
			Objects.requireNonNull(instanceId, "instanceId")
		);
		if (removed == null) {
			return false;
		}
		removeStream(removed);
		this.retiredCycles.put(
			instanceId,
			new CycleStamp(removed.plan.generation(), removed.plan.cycle())
		);
		while (this.retiredCycles.size() > MAX_RETIRED_CYCLES) {
			Iterator<UUID> iterator = this.retiredCycles.keySet().iterator();
			iterator.next();
			iterator.remove();
		}
		return true;
	}

	public synchronized void clear() {
		this.streams.clear();
		this.retiredCycles.clear();
		this.pendingEvents = 0;
		this.bufferedBytes = 0;
	}

	public synchronized List<UUID> pendingPlanIds() {
		return this.streams.entrySet()
			.stream()
			.filter(entry -> !entry.getValue().planConsumed)
			.map(Map.Entry::getKey)
			.toList();
	}

	public synchronized boolean contains(final UUID instanceId) {
		return this.streams.containsKey(
			Objects.requireNonNull(instanceId, "instanceId")
		);
	}

	public synchronized int activeInstanceCount() {
		return this.streams.size();
	}

	public synchronized int pendingEventCount() {
		return this.pendingEvents;
	}

	public synchronized int bufferedBytes() {
		return this.bufferedBytes;
	}

	private OfferResult validateEnvelope(
		final StreamState stream,
		final long generation,
		final long sequence
	) {
		if (stream == null) {
			return OfferResult.UNKNOWN_INSTANCE;
		}
		if (stream.plan.generation() != generation) {
			return OfferResult.GENERATION_MISMATCH;
		}
		if (stream.terminal) {
			return OfferResult.TERMINAL_INSTANCE;
		}
		if (
			stream.lastSequence == Long.MAX_VALUE
				|| sequence != stream.lastSequence + 1L
		) {
			return OfferResult.NON_CONSECUTIVE_SEQUENCE;
		}
		return OfferResult.ACCEPTED;
	}

	private OfferResult validateEventCapacity(
		final StreamState stream,
		final int encodedBytes
	) {
		if (
			stream.pending.size()
				>= this.maximumPendingEventsPerInstance
		) {
			return OfferResult.INSTANCE_EVENT_LIMIT;
		}
		if (this.pendingEvents >= this.maximumPendingEvents) {
			return OfferResult.GLOBAL_EVENT_LIMIT;
		}
		if (!hasByteCapacity(encodedBytes)) {
			return OfferResult.BUFFER_CAPACITY;
		}
		return OfferResult.ACCEPTED;
	}

	private boolean hasByteCapacity(final int additionalBytes) {
		return additionalBytes >= 0
			&& this.bufferedBytes <= this.maximumBufferedBytes - additionalBytes;
	}

	private boolean hasByteCapacityAfterRelease(
		final int additionalBytes,
		final int releasedBytes
	) {
		return additionalBytes >= 0
			&& releasedBytes >= 0
			&& this.bufferedBytes - releasedBytes
				<= this.maximumBufferedBytes - additionalBytes;
	}

	private static int retainedBytes(final StreamState stream) {
		if (stream == null) {
			return 0;
		}
		return (stream.planConsumed ? 0 : stream.planBytes) + stream.pendingBytes;
	}

	private void removeStream(final StreamState stream) {
		this.streams.remove(stream.plan.instanceId());
		this.bufferedBytes -= retainedBytes(stream);
		this.pendingEvents -= stream.pending.size();
	}

	private void appendEvent(
		final StreamState stream,
		final WireEvent event
	) {
		stream.pending.addLast(event);
		stream.pendingBytes += event.encodedBytes();
		stream.lastSequence = event.sequence();
		this.pendingEvents++;
		this.bufferedBytes += event.encodedBytes();
	}

	private static Set<Integer> declaredSlots(final MessagePlaybackPlan plan) {
		Set<Integer> result = new HashSet<>();
		for (MessagePlaybackPlan.ResolvedNode node : plan.nodes()) {
			if (!(node instanceof ResolvedTextNode text)) {
				continue;
			}
			for (ResolvedSegment segment : text.segments()) {
				if (segment instanceof FieldSlotSegment field) {
					result.add(field.slot());
				}
			}
		}
		return result;
	}

	private static Set<Integer> nodeOrdinals(final MessagePlaybackPlan plan) {
		Set<Integer> result = new HashSet<>();
		plan.nodes().forEach(node -> result.add(node.ordinal()));
		return result;
	}

	private static int segmentCount(final MessagePlaybackPlan plan) {
		int result = 0;
		for (MessagePlaybackPlan.ResolvedNode node : plan.nodes()) {
			if (node instanceof ResolvedTextNode text) {
				result = Math.addExact(result, text.segments().size());
			}
		}
		return result;
	}

	private static int estimatedDeltaBytes(final List<FieldValue> values) {
		return estimatedValueDocumentBytes(values);
	}

	private static int estimatedControlBytes(final List<FieldValue> values) {
		return Math.addExact(32, estimatedValueDocumentBytes(values));
	}

	private static int estimatedValueDocumentBytes(
		final List<FieldValue> values
	) {
		int result = 64;
		for (FieldValue value : values) {
			result = Math.addExact(
				result,
				Math.addExact(16, value.component().utf8Bytes())
			);
		}
		return result;
	}

	public enum OfferResult {
		ACCEPTED,
		DUPLICATE_INSTANCE,
		STALE_CYCLE,
		ACTIVE_INSTANCE_LIMIT,
		BUFFER_CAPACITY,
		UNKNOWN_INSTANCE,
		GENERATION_MISMATCH,
		NON_CONSECUTIVE_SEQUENCE,
		TERMINAL_INSTANCE,
		UNKNOWN_FIELD_SLOT,
		FIELD_ALREADY_LOCKED,
		FINALIZE_SLOT_MISMATCH,
		DUPLICATE_NODE_ORDINAL,
		NODE_LIMIT,
		SEGMENT_LIMIT,
		FIELD_SLOT_LIMIT,
		INSTANCE_EVENT_LIMIT,
		GLOBAL_EVENT_LIMIT
	}

	public sealed interface WireEvent permits NodeAppendEvent, FieldDeltaEvent, ControlEvent {
		long sequence();

		int encodedBytes();
	}

	public record NodeAppendEvent(
		MessageNodeAppendS2CPayload payload,
		int encodedBytes
	) implements WireEvent {
		public NodeAppendEvent {
			payload = Objects.requireNonNull(payload, "payload");
			if (encodedBytes < 0) {
				throw new IllegalArgumentException(
					"encodedBytes must be non-negative"
				);
			}
		}

		@Override
		public long sequence() {
			return this.payload.sequence();
		}
	}

	public record FieldDeltaEvent(
		MessageFieldDeltaS2CPayload payload,
		int encodedBytes
	) implements WireEvent {
		public FieldDeltaEvent {
			payload = Objects.requireNonNull(payload, "payload");
			if (encodedBytes < 0) {
				throw new IllegalArgumentException(
					"encodedBytes must be non-negative"
				);
			}
		}

		@Override
		public long sequence() {
			return this.payload.sequence();
		}
	}

	public record ControlEvent(
		MessageControlS2CPayload payload,
		int encodedBytes
	) implements WireEvent {
		public ControlEvent {
			payload = Objects.requireNonNull(payload, "payload");
			if (encodedBytes < 0) {
				throw new IllegalArgumentException(
					"encodedBytes must be non-negative"
				);
			}
		}

		@Override
		public long sequence() {
			return this.payload.sequence();
		}
	}

	private static final class StreamState {
		private final MessagePlaybackPlan plan;
		private final int planBytes;
		private final Set<Integer> declaredSlots;
		private final Set<Integer> nodeOrdinals;
		private final Set<Integer> lockedSlots = new HashSet<>();
		private final ArrayDeque<WireEvent> pending = new ArrayDeque<>();
		private long lastSequence;
		private int segmentCount;
		private int pendingBytes;
		private boolean planConsumed;
		private boolean terminal;

		private StreamState(
			final MessagePlaybackPlan plan,
			final int planBytes,
			final Set<Integer> declaredSlots
		) {
			this.plan = plan;
			this.planBytes = planBytes;
			this.declaredSlots = new HashSet<>(declaredSlots);
			this.nodeOrdinals = nodeOrdinals(plan);
			this.segmentCount = segmentCount(plan);
			this.lastSequence = plan.sequence();
		}
	}

	private record CycleStamp(long generation, long cycle) {
	}
}
