package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionEvaluation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.NodeOccurrence;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalProjectionPlanner.Plan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalProjectionPlanner.FinalAudienceView;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.DedupeKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.EventKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InvocationContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Pure JVM checks for final occurrence admission and stable append ordering. */
public final class MessageFinalProjectionPlannerSelfCheck {
	private static final UUID PLAYER_A = uuid(0xa1L);
	private static final UUID PLAYER_B = uuid(0xb2L);

	private MessageFinalProjectionPlannerSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		MessageCueDefinition cue = parseCue();
		checkFixedTimelineAndRepeat(cue);
		checkExactEventFacts(cue);
		checkAudienceAndAfterOnly(cue);
		checkAfterEventRequiresExactFact();
		checkSideEffectsExcluded(cue);
		checkExistingOccurrenceDedupe(cue);
		checkInstanceEntryPoint(cue);
		checkLiveAuthorizationFailsClosed(cue);
		checkFinalConditionFreeze();

		System.out.println("MESSAGE_FINAL_PROJECTION_PLANNER_SELF_CHECK=PASS");
	}

	private static void checkFixedTimelineAndRepeat(final MessageCueDefinition cue) {
		Plan plan = plan(cue, Map.of(), Map.of(), Set.of(), Set.of());
		List<NodeOccurrence> fixed = List.of(
			occurrence("fixed", 0),
			occurrence("fixed", 1),
			occurrence("fixed", 2),
			occurrence("after", 0),
			occurrence("after", 1),
			occurrence("start_event", 0)
		);
		checkEquals(
			fixed,
			plan.orderedOccurrences(),
			"fixed cue-time and after-node occurrences must fully expand in source order"
		);
		checkEquals(
			fixed,
			plan.newOccurrences(),
			"an empty existing ledger must leave every fixed occurrence new"
		);
		check(
			plan.orderedOccurrences().stream()
				.noneMatch(value ->
					value.nodeId().startsWith("sound")
						|| value.nodeId().startsWith("callback")
				),
			"sound and callback side effects must never enter a final projection plan"
		);
	}

	private static void checkExactEventFacts(final MessageCueDefinition cue) {
		Map<EventKey, Long> wrongTarget = Map.of(
			event(CallbackTrigger.TARGET_COMPLETE, PLAYER_B),
			3L
		);
		checkEquals(
			List.of(
				occurrence("fixed", 0),
				occurrence("fixed", 1),
				occurrence("fixed", 2),
				occurrence("after", 0),
				occurrence("after", 1),
				occurrence("start_event", 0)
			),
			plan(cue, Map.of(), wrongTarget, Set.of(), Set.of()).orderedOccurrences(),
			"another target's completion must not authorize this recipient's event nodes"
		);

		Map<EventKey, Long> facts = allFactsFor(PLAYER_A);
		checkEquals(
			List.of(
				occurrence("fixed", 0),
				occurrence("fixed", 1),
				occurrence("fixed", 2),
				occurrence("after", 0),
				occurrence("after", 1),
				occurrence("target_event", 0),
				occurrence("target_event", 1),
				occurrence("all_event", 0),
				occurrence("interrupt_event", 0),
				occurrence("start_event", 0)
			),
			plan(cue, Map.of(), facts, Set.of(), Set.of()).orderedOccurrences(),
			"event nodes must appear only after their exact authoritative fact is present"
		);
	}

	private static void checkAudienceAndAfterOnly(final MessageCueDefinition cue) {
		Map<String, Set<UUID>> nodeTargets = Map.of(
			"fixed",
			Set.of(PLAYER_B),
			"after",
			Set.of(PLAYER_A)
		);
		checkEquals(
			List.of(
				occurrence("after", 0),
				occurrence("after", 1),
				occurrence("start_event", 0)
			),
			plan(cue, nodeTargets, Map.of(), Set.of(), Set.of()).orderedOccurrences(),
			"node-specific audience must override the cue audience per recipient"
		);

		MessageCueDefinition afterOnly = parseAfterOnlyCue();
		checkEquals(
			List.of(occurrence("after_sound", 0), occurrence("after_sound", 1)),
			plan(afterOnly, Map.of(), Map.of(), Set.of(), Set.of()).orderedOccurrences(),
			"a valid cue whose only visible final content follows a sound node must remain non-empty"
		);

		MessageCueDefinition fixedOnly = withNodes(cue, List.of(node(cue, "fixed")));
		check(
			plan(
				fixedOnly,
				Map.of("fixed", Set.of(PLAYER_B)),
				Map.of(),
				Set.of(),
				Set.of()
			).orderedOccurrences().isEmpty(),
			"a node whose exact audience excludes the recipient must be omitted"
		);

		checkEquals(
			List.of(
				occurrence("fixed", 0),
				occurrence("fixed", 1),
				occurrence("fixed", 2),
				occurrence("start_event", 0)
			),
			plan(
				cue,
				PLAYER_B,
				nodeTargets,
				Map.of(),
				Set.of(),
				Set.of()
			).orderedOccurrences(),
			"the same exact node audiences must project a different non-leaking view for another recipient"
		);
	}

	private static void checkAfterEventRequiresExactFact() {
		MessageCueDefinition cue = parseAfterEventCue();
		check(
			plan(cue, Map.of(), Map.of(), Set.of(), Set.of())
				.orderedOccurrences()
				.isEmpty(),
			"an after-node chain rooted at an unfired cue event must stay absent"
		);
		checkEquals(
			List.of(occurrence("after_target_event", 0)),
			plan(
				cue,
				Map.of(),
				Map.of(
					event(CallbackTrigger.TARGET_COMPLETE, PLAYER_A),
					1L
				),
				Set.of(),
				Set.of()
			).orderedOccurrences(),
			"an after-node chain may enter the final plan only after its exact event fact exists"
		);
		check(
			plan(
				cue,
				Map.of(),
				Map.of(
					event(CallbackTrigger.TARGET_COMPLETE, PLAYER_B),
					1L
				),
				Set.of(),
				Set.of()
			).orderedOccurrences().isEmpty(),
			"another recipient's target-complete fact must not unlock an after-node chain"
		);
	}

	private static void checkSideEffectsExcluded(final MessageCueDefinition cue) {
		Map<EventKey, Long> facts = allFactsFor(PLAYER_A);
		Plan plan = plan(cue, Map.of(), facts, Set.of(), Set.of());
		check(
			plan.orderedOccurrences().stream().noneMatch(occurrence ->
				occurrence.nodeId().startsWith("sound")
					|| occurrence.nodeId().startsWith("callback")
			),
			"authorized fixed and occurred-event side effects must stay out of final text projection"
		);
		check(
			cue.nodes().stream().anyMatch(node -> node.id().equals("sound_fixed"))
				&& cue.nodes().stream().anyMatch(node -> node.id().equals("sound_event"))
				&& cue.nodes().stream().anyMatch(node -> node.id().equals("callback_fixed"))
				&& cue.nodes().stream().anyMatch(node -> node.id().equals("callback_event")),
			"the side-effect exclusion fixture must contain fixed and event-driven sound/callback nodes"
		);
	}

	private static void checkExistingOccurrenceDedupe(final MessageCueDefinition cue) {
		Plan plan = plan(
			cue,
			Map.of(),
			Map.of(),
			Set.of(occurrence("fixed", 1), occurrence("foreign", 0)),
			Set.of(occurrence("fixed", 1), occurrence("after", 0))
		);
		checkEquals(
			List.of(
				occurrence("fixed", 0),
				occurrence("fixed", 1),
				occurrence("fixed", 2),
				occurrence("after", 0),
				occurrence("after", 1),
				occurrence("start_event", 0)
			),
			plan.orderedOccurrences(),
			"existing and foreign occurrences must not alter the complete ordered plan"
		);
		checkEquals(
			List.of(
				occurrence("fixed", 0),
				occurrence("fixed", 2),
				occurrence("after", 1),
				occurrence("start_event", 0)
			),
			plan.newOccurrences(),
			"projected and decided occurrences must be removed once from the append set"
		);
	}

	private static void checkInstanceEntryPoint(final MessageCueDefinition cue) {
		Map<EventKey, Long> facts = allFactsFor(PLAYER_A);
		Set<UUID> targets = Set.of(PLAYER_A, PLAYER_B);
		UUID instanceId = uuid(0x3bL);
		MessageInstance instance = new MessageInstance(
			instanceId,
			cue,
			new InvocationContext(
				instanceId,
				true,
				Optional.of(PLAYER_A),
				targets,
				cue.game(),
				Optional.empty(),
				Optional.empty(),
				Map.of(),
				1L,
				false,
				false
			),
			Map.of(),
			Map.of(),
			targets,
			Map.of(),
			new DedupeKey(cue.id(), targets, Map.of()),
			Optional.empty(),
			cue.defaults().clock().orElse(ClockMode.GAME_TIME),
			0L,
			InstanceStatus.ACTIVE,
			Optional.empty(),
			0L,
			0L,
			Set.of(),
			Set.of(PLAYER_A),
			facts,
			false,
			1L
		);
		Set<NodeOccurrence> newOccurrences = MessageFinalProjectionPlanner.plan(
			instance,
			PLAYER_A,
			Set.of(occurrence("fixed", 0), occurrence("target_event", 0))
		);
		checkEquals(
			List.of(
				occurrence("fixed", 1),
				occurrence("fixed", 2),
				occurrence("after", 0),
				occurrence("after", 1),
				occurrence("target_event", 1),
				occurrence("all_event", 0),
				occurrence("interrupt_event", 0),
				occurrence("start_event", 0)
			),
			new ArrayList<>(newOccurrences),
			"the runtime-facing entry point must return a stable ordered append set"
		);
	}

	private static void checkLiveAuthorizationFailsClosed(
		final MessageCueDefinition cue
	) {
		FinalAudienceView snapshot = new FinalAudienceView(
			Set.of(PLAYER_A, PLAYER_B),
			Map.of()
		);
		check(
			MessageFinalProjectionPlanner.authorizedNodeIds(
				cue,
				PLAYER_A,
				snapshot,
				true,
				Optional.empty()
			).isEmpty(),
			"sensitive/live-strict final projection must fail closed without a fresh view"
		);

		FinalAudienceView revoked = new FinalAudienceView(
			Set.of(PLAYER_B),
			Map.of()
		);
		check(
			MessageFinalProjectionPlanner.authorizedNodeIds(
				cue,
				PLAYER_A,
				snapshot,
				true,
				Optional.of(revoked)
			).isEmpty(),
			"a stale snapshot must not leak final text after live authorization is revoked"
		);

		FinalAudienceView nodeRestricted = new FinalAudienceView(
			Set.of(PLAYER_A),
			Map.of("target_event", Set.of(PLAYER_B))
		);
		Set<String> authorized = MessageFinalProjectionPlanner.authorizedNodeIds(
			cue,
			PLAYER_A,
			snapshot,
			true,
			Optional.of(nodeRestricted)
		).orElseThrow();
		check(
			authorized.contains("fixed") && !authorized.contains("target_event"),
			"the fresh node-level audience must remove only unauthorized final nodes"
		);
		check(
			MessageFinalProjectionPlanner.authorizedNodeIds(
				cue,
				PLAYER_A,
				snapshot,
				false,
				Optional.of(revoked)
			).orElseThrow().contains("target_event"),
			"snapshot audiences must remain stable when no live recheck is required"
		);
	}

	private static void checkFinalConditionFreeze() {
		Map<String, Boolean> decisions = new LinkedHashMap<>();
		AtomicInteger liveReads = new AtomicInteger();
		boolean cueStart = MessageFinalProjectionPlanner.freezeConditionDecision(
			decisions,
			"cue-start",
			ConditionEvaluation.CUE_START,
			Optional.of(false),
			() -> {
				liveReads.incrementAndGet();
				return true;
			}
		);
		check(
			!cueStart && liveReads.get() == 0,
			"final projection must reuse a cue-start decision without sampling live state"
		);
		boolean stillFrozen = MessageFinalProjectionPlanner.freezeConditionDecision(
			decisions,
			"cue-start",
			ConditionEvaluation.CUE_START,
			Optional.of(true),
			() -> {
				liveReads.incrementAndGet();
				return true;
			}
		);
		check(
			!stillFrozen && liveReads.get() == 0,
			"a final condition already frozen false must never be re-evaluated"
		);
		check(
			!MessageFinalProjectionPlanner.freezeConditionDecision(
				decisions,
				"missing-cue-start",
				ConditionEvaluation.CUE_START,
				Optional.empty(),
				() -> {
					liveReads.incrementAndGet();
					return true;
				}
			),
			"missing cue-start evidence must fail closed"
		);
		check(liveReads.get() == 0, "missing cue-start evidence must not read current state");

		boolean nodeStart = MessageFinalProjectionPlanner.freezeConditionDecision(
			decisions,
			"node-start",
			ConditionEvaluation.NODE_START,
			Optional.empty(),
			() -> liveReads.incrementAndGet() == 1
		);
		boolean repeated = MessageFinalProjectionPlanner.freezeConditionDecision(
			decisions,
			"node-start",
			ConditionEvaluation.NODE_START,
			Optional.empty(),
			() -> {
				liveReads.incrementAndGet();
				return false;
			}
		);
		check(
			nodeStart && repeated && liveReads.get() == 1,
			"a later-mode final condition may read once, then must stay frozen"
		);
	}

	private static Plan plan(
		final MessageCueDefinition cue,
		final Map<String, Set<UUID>> nodeTargets,
		final Map<EventKey, Long> facts,
		final Set<NodeOccurrence> projected,
		final Set<NodeOccurrence> decided
	) {
		return plan(
			cue,
			PLAYER_A,
			nodeTargets,
			facts,
			projected,
			decided
		);
	}

	private static Plan plan(
		final MessageCueDefinition cue,
		final UUID recipient,
		final Map<String, Set<UUID>> nodeTargets,
		final Map<EventKey, Long> facts,
		final Set<NodeOccurrence> projected,
		final Set<NodeOccurrence> decided
	) {
		return MessageFinalProjectionPlanner.plan(
			cue,
			recipient,
			Set.of(PLAYER_A, PLAYER_B),
			nodeTargets,
			facts,
			projected,
			decided
		);
	}

	private static Map<EventKey, Long> allFactsFor(final UUID recipient) {
		Map<EventKey, Long> facts = new LinkedHashMap<>();
		facts.put(event(CallbackTrigger.TARGET_COMPLETE, recipient), 1L);
		facts.put(event(CallbackTrigger.ALL_COMPLETE), 2L);
		facts.put(event(CallbackTrigger.INTERRUPT), 3L);
		return facts;
	}

	private static EventKey event(final CallbackTrigger trigger) {
		return new EventKey(trigger, Optional.empty());
	}

	private static EventKey event(final CallbackTrigger trigger, final UUID target) {
		return new EventKey(trigger, Optional.of(target));
	}

	private static CueNode node(final MessageCueDefinition cue, final String id) {
		return cue.nodes().stream()
			.filter(candidate -> candidate.id().equals(id))
			.findFirst()
			.orElseThrow();
	}

	private static MessageCueDefinition withNodes(
		final MessageCueDefinition source,
		final List<CueNode> nodes
	) {
		return new MessageCueDefinition(
			source.id(),
			source.game(),
			source.required(),
			source.policies(),
			source.parameters(),
			source.fields(),
			nodes,
			source.history(),
			source.staticFallback(),
			source.assets(),
			source.softLimits(),
			source.canonicalDocument()
		);
	}

	private static MessageCueDefinition parseCue() {
		MessageDefinitionParser.ParseResult<MessageCueDefinition> parsed =
			MessageDefinitionParser.parseMessageCue(
				id("final_projection"),
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "policies": {
				    "timing": {"clock": "game_time"},
				    "audience": {
				      "target": {"source": "call_targets", "online_only": true}
				    }
				  },
				  "nodes": [
				    {
				      "id": "fixed",
				      "type": "text",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "repeat": {"count": 3, "interval": "1t", "capture": "keep"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "固定正文"}}]}
				    },
				    {
				      "id": "after",
				      "type": "text",
				      "schedule": {"type": "after_node", "node": "fixed", "offset": "1t"},
				      "repeat": {"count": 2, "interval": "1t", "capture": "per_repeat"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "后继正文"}}]}
				    },
				    {
				      "id": "target_event",
				      "type": "text",
				      "schedule": {"type": "cue_event", "event": "target_complete", "offset": "0t"},
				      "repeat": {"count": 2, "interval": "1t", "capture": "keep"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "目标完成正文"}}]}
				    },
				    {
				      "id": "all_event",
				      "type": "text",
				      "schedule": {"type": "cue_event", "event": "all_complete", "offset": "0t"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "全员完成正文"}}]}
				    },
				    {
				      "id": "interrupt_event",
				      "type": "text",
				      "schedule": {"type": "cue_event", "event": "interrupt", "offset": "0t"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "中断正文"}}]}
				    },
				    {
				      "id": "start_event",
				      "type": "text",
				      "schedule": {"type": "cue_event", "event": "start", "offset": "0t"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "开始正文"}}]}
				    },
				    {
				      "id": "sound_fixed",
				      "type": "sound",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "sound": "minecraft:ui.button.click",
				      "category": "ui",
				      "volume": 1.0,
				      "pitch": 1.0
				    },
				    {
				      "id": "sound_event",
				      "type": "sound",
				      "schedule": {"type": "cue_event", "event": "all_complete", "offset": "0t"},
				      "sound": "minecraft:ui.button.click",
				      "category": "ui",
				      "volume": 1.0,
				      "pitch": 1.0
				    },
				    {
				      "id": "callback_fixed",
				      "type": "callback",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "function": "pixel_tzz:self_check/fixed",
				      "execution": "as_server",
				      "location": "origin"
				    },
				    {
				      "id": "callback_event",
				      "type": "callback",
				      "schedule": {"type": "cue_event", "event": "start", "offset": "0t"},
				      "function": "pixel_tzz:self_check/event",
				      "execution": "as_server",
				      "location": "origin"
				    }
				  ]
				}
				"""
			);
		check(parsed.valid(), "final projection cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static MessageCueDefinition parseAfterOnlyCue() {
		MessageDefinitionParser.ParseResult<MessageCueDefinition> parsed =
			MessageDefinitionParser.parseMessageCue(
				id("final_projection_after_only"),
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "policies": {
				    "timing": {"clock": "game_time"},
				    "audience": {
				      "target": {"source": "call_targets", "online_only": true}
				    }
				  },
				  "nodes": [
				    {
				      "id": "sound_anchor",
				      "type": "sound",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "sound": "minecraft:ui.button.click",
				      "category": "ui",
				      "volume": 1.0,
				      "pitch": 1.0
				    },
				    {
				      "id": "after_sound",
				      "type": "text",
				      "schedule": {"type": "after_node", "node": "sound_anchor", "offset": "1t"},
				      "repeat": {"count": 2, "interval": "1t", "capture": "keep"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "声音后的正文"}}]}
				    }
				  ]
				}
				"""
			);
		check(parsed.valid(), "after-only final projection cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static MessageCueDefinition parseAfterEventCue() {
		MessageDefinitionParser.ParseResult<MessageCueDefinition> parsed =
			MessageDefinitionParser.parseMessageCue(
				id("final_projection_after_event"),
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "policies": {
				    "timing": {"clock": "game_time"},
				    "audience": {
				      "target": {"source": "call_targets", "online_only": true}
				    }
				  },
				  "nodes": [
				    {
				      "id": "target_event_anchor",
				      "type": "sound",
				      "schedule": {
				        "type": "cue_event",
				        "event": "target_complete",
				        "offset": "0t"
				      },
				      "sound": "minecraft:ui.button.click",
				      "category": "ui",
				      "volume": 1.0,
				      "pitch": 1.0
				    },
				    {
				      "id": "after_target_event",
				      "type": "text",
				      "schedule": {
				        "type": "after_node",
				        "node": "target_event_anchor",
				        "offset": "1t"
				      },
				      "channel": "subtitle",
				      "text": {
				        "parts": [{"component": {"text": "事件后的正文"}}]
				      }
				    }
				  ]
				}
				"""
			);
		check(
			parsed.valid(),
			"after-event final projection cue must parse: " + parsed.issues()
		);
		return parsed.value().orElseThrow();
	}

	private static NodeOccurrence occurrence(final String nodeId, final int occurrence) {
		return new NodeOccurrence(nodeId, occurrence);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static UUID uuid(final long value) {
		return new UUID(0L, value);
	}

	private static void checkEquals(
		final Object expected,
		final Object actual,
		final String message
	) {
		if (!expected.equals(actual)) {
			throw new IllegalStateException(
				message + "; expected=" + expected + ", actual=" + actual
			);
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
