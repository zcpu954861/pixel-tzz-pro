package io.github.zcpu954861.pixeltzzpro.server.message;

import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ChatInterruptMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConcurrencyPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.Ledger;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.PrepareDisposition;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.AdvanceResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ControlAction;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ControlResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.GroupSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InvocationContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.PlayDisposition;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.PlayResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ProjectionKind;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.QueueRemovalResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RetirementResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RuntimeState;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.TargetSelector;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.AudienceResolution;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.ParameterResolution;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Pure JVM acceptance gate for V3B server-authoritative message instances.
 */
public final class MessageInstanceAuthoritySelfCheck {
	private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final Identifier GAME = id("main");

	private MessageInstanceAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		MessageCueDefinition parsed = parseCue();
		checkExplicitContextBypass(parsed);
		checkPlayDispositionsAndDefensiveCopies(parsed);
		checkAudienceNormalizationAndCallbacksOnly(parsed);
		checkRestartQueueCooldownAndRefresh(parsed);
		checkDagsEventsAndControls(parsed);
		checkCallbackLedgerCrashSafety();
		checkExternalConditionFragment();
		checkOccurrencePlaybackClock();
		checkRuntimeFallbackBoundary();
		checkRuntimeSoftLimits(parsed);
		checkQueuedRemovalAndTerminalRetirement(parsed);
		checkRetirementReclaimsInstanceCapacity(parsed);

		System.out.println("MESSAGE_INSTANCE_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkExplicitContextBypass(
		final MessageCueDefinition parsed
	) {
		InvocationContext outsideContext = contextWithGame(
			uuid(180),
			true,
			false,
			id("other_game")
		);
		PlayResult rejected = play(
			RuntimeState.empty(64),
			parsed,
			outsideContext,
			clock(0),
			Map.of("key", "context", "body", "normal"),
			Set.of(PLAYER_A)
		);
		check(
			rejected.disposition() == PlayDisposition.REJECTED,
			"ordinary invocation outside the registered game must be rejected"
		);

		InvocationContext bypass = contextWithGame(
			uuid(181),
			true,
			true,
			id("other_game")
		);
		PlayResult accepted = play(
			RuntimeState.empty(64),
			parsed,
			bypass,
			clock(0),
			Map.of("key", "context", "body", "bypassed"),
			Set.of(PLAYER_A)
		);
		check(
			accepted.disposition() == PlayDisposition.CREATED
				&& accepted.nextState().instances().values().stream()
					.findFirst().orElseThrow()
					.context().bypassContext(),
			"authorized explicit bypass must skip only registered game context and freeze on instance"
		);
		PlayResult missingParameter = play(
			RuntimeState.empty(64),
			parsed,
			contextWithGame(uuid(182), true, true, id("other_game")),
			clock(0),
			Map.of("key", "context"),
			Set.of(PLAYER_A)
		);
		check(
			missingParameter.disposition() == PlayDisposition.REJECTED,
			"context bypass must not skip parameter validation"
		);
		PlayResult unauthorized = play(
			RuntimeState.empty(64),
			parsed,
			contextWithGame(uuid(183), false, true, id("other_game")),
			clock(0),
			Map.of("key", "context", "body", "unauthorized"),
			Set.of(PLAYER_A)
		);
		check(
			unauthorized.disposition() == PlayDisposition.REJECTED,
			"context bypass flag must not grant invocation authority"
		);

		MessageCueDefinition queueCue = withConcurrency(
			parsed,
			id("context_bypass_queue"),
			DuplicateMode.QUEUE,
			Optional.empty(),
			Optional.empty(),
			1,
			4
		);
		PlayResult predecessor = play(
			RuntimeState.empty(64),
			queueCue,
			context(uuid(184), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "predecessor"),
			Set.of(PLAYER_A)
		);
		PlayResult queued = play(
			predecessor.nextState(),
			queueCue,
			contextWithGame(uuid(185), true, true, id("other_game")),
			clock(0),
			Map.of("key", "same", "body", "queued"),
			Set.of(PLAYER_A)
		);
		check(
			queued.disposition() == PlayDisposition.QUEUED
				&& queued.nextState().queue().getFirst().context().bypassContext(),
			"queued invocation must freeze context bypass for later server rechecks"
		);
	}

	private static void checkQueuedRemovalAndTerminalRetirement(
		final MessageCueDefinition parsed
	) {
		MessageCueDefinition queueCue = withConcurrency(
			parsed,
			id("queue_removal"),
			DuplicateMode.QUEUE,
			Optional.empty(),
			Optional.empty(),
			1,
			4
		);
		PlayResult predecessor = play(
			RuntimeState.empty(64),
			queueCue,
			context(uuid(200), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "predecessor"),
			Set.of(PLAYER_A)
		);
		PlayResult queued = play(
			predecessor.nextState(),
			queueCue,
			context(uuid(201), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "must-not-start"),
			Set.of(PLAYER_A)
		);
		UUID queuedId = queued.instanceId().orElseThrow();
		Ledger beforeRemovalLedger = queued.nextState().callbackLedger();
		QueueRemovalResult removed = MessageInstanceAuthority.removeQueuedInvocations(
			queued.nextState(),
			Set.of(queuedId, uuid(299))
		);
		check(
			removed.accepted()
				&& removed.changed()
				&& removed.removedInstanceIds().equals(Set.of(queuedId))
				&& removed.nextState().queue().isEmpty(),
			"queued failure transaction must remove only the matching reserved id"
		);
		check(
			removed.nextState().callbackLedger() == beforeRemovalLedger,
			"queued removal must retain the callback ledger without preparing work"
		);
		ControlResult released = MessageInstanceAuthority.control(
			removed.nextState(),
			new InstanceSelector(predecessor.instanceId().orElseThrow()),
			ControlAction.COMPLETE,
			clock(0),
			"release queue after delivery failure"
		);
		check(
			released.accepted()
				&& released.nextState().queue().isEmpty()
				&& !released.nextState().instances().containsKey(queuedId)
				&& released.projections().stream()
					.noneMatch(event -> event.instanceId().equals(queuedId))
				&& released.callbacks().stream()
					.noneMatch(callback -> callback.key().instanceId().equals(queuedId)),
			"a removed queued invocation must never start or prepare callbacks"
		);

		MessageCueDefinition terminalCue = withConcurrency(
			parsed,
			id("terminal_retirement"),
			DuplicateMode.REFRESH,
			Optional.empty(),
			Optional.of("key"),
			8,
			8
		);
		PlayResult terminalStart = play(
			RuntimeState.empty(64),
			terminalCue,
			context(uuid(210), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "terminal", "body", "terminal"),
			Set.of(PLAYER_A)
		);
		ControlResult terminalComplete = MessageInstanceAuthority.control(
			terminalStart.nextState(),
			new InstanceSelector(terminalStart.instanceId().orElseThrow()),
			ControlAction.COMPLETE,
			clock(0),
			"prepare retirement"
		);
		RuntimeState terminalState = terminalComplete.nextState();
		for (MessageCallbackLedger.PreparedCallback callback : terminalComplete.callbacks()) {
			MessageInstanceAuthority.CallbackOutcomeResult outcome =
				MessageInstanceAuthority.recordCallbackOutcome(
					terminalState,
					callback,
					true,
					Optional.empty(),
					0L
				);
			check(outcome.accepted(), "terminal callback outcome must be recordable");
			terminalState = outcome.nextState();
		}
		AdvanceResult terminalAdvance = MessageInstanceAuthority.advance(
			terminalState,
			clock(0)
		);
		check(terminalAdvance.accepted(), "terminal fixture must finish after callback outcome");
		terminalState = terminalAdvance.nextState();
		UUID terminalId = terminalStart.instanceId().orElseThrow();
		check(
			terminalState.instances().get(terminalId).status().terminal()
				&& terminalState.refreshIndex().containsValue(terminalId),
			"retirement fixture must retain a terminal refresh address"
		);

		MessageCueDefinition activeCue = withConcurrency(
			parsed,
			id("retirement_survivor"),
			DuplicateMode.IGNORE_WHILE_ACTIVE,
			Optional.empty(),
			Optional.empty(),
			8,
			8
		);
		PlayResult survivor = play(
			terminalState,
			activeCue,
			context(uuid(211), Set.of(PLAYER_B), true),
			clock(0),
			Map.of("key", "survivor", "body", "active"),
			Set.of(PLAYER_B)
		);
		UUID survivorId = survivor.instanceId().orElseThrow();
		RuntimeState mixed = survivor.nextState();
		RetirementResult rejected = MessageInstanceAuthority.retireTerminalInstances(
			mixed,
			Set.of(terminalId, survivorId)
		);
		check(
			!rejected.accepted()
				&& !rejected.changed()
				&& rejected.nextState() == mixed
				&& rejected.nextState().instances().containsKey(terminalId),
			"one active id must reject the whole retirement batch atomically"
		);

		int callbackCountBeforeRetirement = mixed.callbackLedger().entries().size();
		RetirementResult retired = MessageInstanceAuthority.retireTerminalInstances(
			mixed,
			Set.of(terminalId, uuid(298))
		);
		check(
			retired.accepted()
				&& retired.retiredInstanceIds().equals(Set.of(terminalId))
				&& !retired.nextState().instances().containsKey(terminalId)
				&& retired.nextState().instances().containsKey(survivorId),
			"terminal retirement must remove exactly the retained terminal instances"
		);
		check(
			callbackCountBeforeRetirement > 0
				&& retired.nextState().callbackLedger().entries().stream()
					.noneMatch(entry -> entry.key().instanceId().equals(terminalId)),
			"retirement must reclaim terminal callback ledger capacity after the "
				+ "instance has been tombstoned"
		);
		check(
			retired.nextState().refreshIndex().values().stream()
				.noneMatch(terminalId::equals)
				&& retired.nextState().activeDedupe().containsValue(survivorId),
			"retirement must rebuild refresh and active-dedupe indexes from survivors"
		);
	}

	private static void checkRuntimeSoftLimits(final MessageCueDefinition parsed) {
		MessageCueDefinition durationLimited = withRuntimeLimits(
			parsed,
			id("duration_limited"),
			DuplicateMode.ALLOW,
			RestartMode.TRANSIENT,
			8,
			8,
			8,
			Optional.of(new TimeSpan(2L * TimeSpan.NANOS_PER_TICK))
		);
		PlayResult tooLong = play(
			RuntimeState.empty(64),
			durationLimited,
			context(uuid(240), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "duration", "body", "too-long"),
			Set.of(PLAYER_A)
		);
		check(
			tooLong.disposition() == PlayDisposition.REJECTED
				&& tooLong.message().contains("max_total_duration"),
			"resolved timeline must enforce max_total_duration before projection"
		);

		MessageCueDefinition persistent = withRuntimeLimits(
			parsed,
			id("persistent_limited"),
			DuplicateMode.ALLOW,
			RestartMode.CONTINUE,
			8,
			8,
			1,
			Optional.empty()
		);
		PlayResult first = play(
			RuntimeState.empty(64),
			persistent,
			context(uuid(241), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "first", "body", "first"),
			Set.of(PLAYER_A)
		);
		PlayResult overPersistentLimit = play(
			first.nextState(),
			persistent,
			context(uuid(242), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "second", "body", "second"),
			Set.of(PLAYER_A)
		);
		check(
			first.disposition() == PlayDisposition.CREATED
				&& overPersistentLimit.disposition() == PlayDisposition.REJECTED
				&& overPersistentLimit.message().contains("persistent-instance"),
			"persistent instances must enforce the cue-local max_persisted_instances reservation"
		);

		MessageCueDefinition transientCue = withRuntimeLimits(
			parsed,
			id("transient_zero_persisted"),
			DuplicateMode.ALLOW,
			RestartMode.TRANSIENT,
			8,
			8,
			0,
			Optional.empty()
		);
		PlayResult transientPlay = play(
			RuntimeState.empty(64),
			transientCue,
			context(uuid(243), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "transient", "body", "transient"),
			Set.of(PLAYER_A)
		);
		check(
			transientPlay.disposition() == PlayDisposition.CREATED,
			"max_persisted_instances must not reject transient-only cues"
		);

		MessageCueDefinition restartCue = withRuntimeLimits(
			parsed,
			id("persistent_restart"),
			DuplicateMode.RESTART,
			RestartMode.CONTINUE,
			1,
			4,
			1,
			Optional.empty()
		);
		PlayResult restartFirst = play(
			RuntimeState.empty(64),
			restartCue,
			context(uuid(244), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "before"),
			Set.of(PLAYER_A)
		);
		PlayResult restarted = play(
			restartFirst.nextState(),
			restartCue,
			context(uuid(245), Set.of(PLAYER_A), true),
			clock(1),
			Map.of("key", "same", "body", "after"),
			Set.of(PLAYER_A)
		);
		check(
			restarted.disposition() == PlayDisposition.CREATED,
			"restart must replace one persistent reservation without exceeding its cap: "
				+ restarted.disposition()
				+ " / "
				+ restarted.message()
		);

		MessageCueDefinition queuedCue = withRuntimeLimits(
			parsed,
			id("persistent_queue"),
			DuplicateMode.QUEUE,
			RestartMode.CONTINUE,
			1,
			4,
			2,
			Optional.empty()
		);
		PlayResult queueFirst = play(
			RuntimeState.empty(64),
			queuedCue,
			context(uuid(246), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "active"),
			Set.of(PLAYER_A)
		);
		PlayResult queueSecond = play(
			queueFirst.nextState(),
			queuedCue,
			context(uuid(247), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "queued"),
			Set.of(PLAYER_A)
		);
		PlayResult queueThird = play(
			queueSecond.nextState(),
			queuedCue,
			context(uuid(248), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "overflow"),
			Set.of(PLAYER_A)
		);
		check(
			queueSecond.disposition() == PlayDisposition.QUEUED
				&& queueThird.disposition() == PlayDisposition.REJECTED,
			"queued persistent work must reserve max_persisted_instances capacity"
		);
	}

	private static void checkRetirementReclaimsInstanceCapacity(
		final MessageCueDefinition parsed
	) {
		MessageCueDefinition cue = withConcurrency(
			parsed,
			id("retirement_capacity"),
			DuplicateMode.ALLOW,
			Optional.empty(),
			Optional.empty(),
			MessageInstanceAuthority.HARD_MAX_INSTANCES,
			8
		);
		RuntimeState state = RuntimeState.empty(64);
		for (int index = 0; index < MessageInstanceAuthority.HARD_MAX_INSTANCES; index++) {
			long idValue = 1_000L + index;
			PlayResult created = play(
				state,
				cue,
				context(uuid(idValue), Set.of(PLAYER_A), true),
				clock(0),
				Map.of("key", "capacity-" + index, "body", "capacity"),
				Set.of(PLAYER_A)
			);
			check(
				created.disposition() == PlayDisposition.CREATED,
				"capacity fixture must fill every hard instance slot"
			);
			state = created.nextState();
		}
		PlayResult blocked = play(
			state,
			cue,
			context(uuid(2_000), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "blocked", "body", "capacity"),
			Set.of(PLAYER_A)
		);
		check(
			blocked.disposition() == PlayDisposition.REJECTED,
			"hard instance capacity must reject one more live instance"
		);

		UUID firstId = uuid(1_000);
		ControlResult completed = MessageInstanceAuthority.control(
			state,
			new InstanceSelector(firstId),
			ControlAction.COMPLETE,
			clock(0),
			"free one terminal capacity slot"
		);
		RuntimeState capacityState = completed.nextState();
		for (MessageCallbackLedger.PreparedCallback callback : completed.callbacks()) {
			MessageInstanceAuthority.CallbackOutcomeResult outcome =
				MessageInstanceAuthority.recordCallbackOutcome(
					capacityState,
					callback,
					true,
					Optional.empty(),
					0L
				);
			check(outcome.accepted(), "capacity callback outcome must be recordable");
			capacityState = outcome.nextState();
		}
		AdvanceResult capacityAdvance = MessageInstanceAuthority.advance(
			capacityState,
			clock(0)
		);
		check(capacityAdvance.accepted(), "capacity terminal transition must advance");
		capacityState = capacityAdvance.nextState();
		check(
			completed.accepted()
				&& capacityState.instances().get(firstId).status().terminal(),
			"capacity fixture must produce one terminal instance"
		);
		RetirementResult retired = MessageInstanceAuthority.retireTerminalInstances(
			capacityState,
			Set.of(firstId)
		);
		check(
			retired.accepted()
				&& retired.nextState().instances().size()
					== MessageInstanceAuthority.HARD_MAX_INSTANCES - 1,
			"terminal retirement must reclaim one hard-capacity slot"
		);
		PlayResult replacement = play(
			retired.nextState(),
			cue,
			context(uuid(2_000), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "replacement", "body", "capacity"),
			Set.of(PLAYER_A)
		);
		check(
			replacement.disposition() == PlayDisposition.CREATED
				&& replacement.nextState().instances().size()
					== MessageInstanceAuthority.HARD_MAX_INSTANCES,
			"retired capacity must admit the next message instance"
		);
	}

	private static void checkAudienceNormalizationAndCallbacksOnly(
		final MessageCueDefinition parsed
	) {
		MessageCueDefinition audienceCue = withConcurrency(
			parsed,
			id("node_audience"),
			DuplicateMode.ALLOW,
			Optional.empty(),
			Optional.empty(),
			8,
			8
		);
		PlayResult normalized = MessageInstanceAuthority.play(
			RuntimeState.empty(64),
			audienceCue,
			context(uuid(90), Set.of(PLAYER_A, PLAYER_B), true),
			clock(0),
			(ignoredCue, ignoredContext) -> ParameterResolution.resolved(
				Map.of("key", "audience", "body", "normalized"),
				Map.of("intro", new TimeSpan(TimeSpan.NANOS_PER_TICK))
			),
			(audience, ignoredCue, ignoredContext, parameters) ->
				AudienceResolution.resolved(
					Set.of(PLAYER_A),
					Map.of("after", Set.of(PLAYER_B))
				)
		);
		check(
			normalized.disposition() == PlayDisposition.CREATED,
			"node-level audience play must create"
		);
		MessageInstanceAuthority.MessageInstance normalizedInstance =
			normalized.nextState().instances().get(normalized.instanceId().orElseThrow());
		check(
			normalizedInstance.cueTargets().equals(Set.of(PLAYER_A, PLAYER_B)),
			"completion population must include every node-level audience target"
		);
		check(
			normalizedInstance.nodeTargets().get("intro").equals(Set.of(PLAYER_A))
				&& normalizedInstance.nodeTargets().get("after").equals(Set.of(PLAYER_B)),
			"audience normalization must retain each node's exact target set"
		);
		check(
			normalized.projections().stream()
				.filter(event -> event.kind() == ProjectionKind.CREATE)
				.anyMatch(event -> event.recipients().equals(Set.of(PLAYER_A, PLAYER_B))),
			"CREATE must prepare streams for the full affected-target union"
		);

		MessageDefinitionParser.ParseResult<MessageCueDefinition> callbacksOnlyParsed =
			MessageDefinitionParser.parseMessageCue(
				id("callbacks_only"),
				"""
				{
				  "format_version": 1,
				  "policies": {
				    "timing": {"clock": "game_time"},
				    "delivery": {"empty_audience": "callbacks_only"}
				  },
				  "nodes": [
				    {
				      "id": "server_step",
				      "type": "callback",
				      "schedule": {"type": "cue_start", "offset": "1t"},
				      "function": "pixel_tzz:self_check/callback",
				      "execution": "as_server",
				      "location": "origin"
				    }
				  ]
				}
				"""
			);
		check(
			callbacksOnlyParsed.valid(),
			"callbacks-only fixture must parse: " + callbacksOnlyParsed.issues()
		);
		InvocationContext externallyTimed = new InvocationContext(
			uuid(91),
			true,
			Optional.of(PLAYER_A),
			Set.of(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Map.of(),
			1L,
			true
		);
		PlayResult callbacksOnly = MessageInstanceAuthority.play(
			RuntimeState.empty(64),
			callbacksOnlyParsed.value().orElseThrow(),
			externallyTimed,
			clock(0),
			(ignoredCue, ignoredContext) ->
				ParameterResolution.resolved(Map.of(), Map.of()),
			(audience, ignoredCue, ignoredContext, parameters) ->
				AudienceResolution.resolved(Set.of(), Map.of())
		);
		check(
			callbacksOnly.disposition() == PlayDisposition.CREATED
				&& !callbacksOnly.nextState()
					.instances()
					.get(callbacksOnly.instanceId().orElseThrow())
					.context()
					.externallyTimed(),
			"callbacks_only with no recipients must switch to the internal server clock"
		);
		MessageInstanceAuthority.AdvanceResult due = MessageInstanceAuthority.advance(
			callbacksOnly.nextState(),
			clock(1)
		);
		check(
			due.accepted() && due.callbacks().size() == 1,
			"callbacks_only must execute its registered server timeline without a recipient stream"
		);
	}

	private static void checkRuntimeFallbackBoundary() {
		RichComponent source = new RichComponent(
			"""
			{
			  "text": "『静态降级』",
			  "color": "gold",
			  "bold": true,
			  "insertion": "pixel_tzz:fallback",
			  "hover_event": {
			    "action": "show_text",
			    "value": {"text": "保留悬停", "color": "aqua"}
			  },
			  "click_event": {
			    "action": "suggest_command",
			    "command": "/help"
			  }
			}
			""",
			"『静态降级』"
		);
		var decoded = MessageRuntimeFallbacks.decode(source);
		check(
			decoded.getString().equals("『静态降级』")
				&& decoded.getStyle().getColor() != null
				&& decoded.getStyle().getColor().getValue() == 0xFFAA00
				&& decoded.getStyle().isBold()
				&& "pixel_tzz:fallback".equals(
					decoded.getStyle().getInsertion()
				)
				&& decoded.getStyle().getHoverEvent() != null
				&& decoded.getStyle().getClickEvent() != null,
			"static fallback decoding must preserve vanilla style and interaction metadata"
		);

		MessageRuntimeFallbacks.OccurrenceKey first =
			new MessageRuntimeFallbacks.OccurrenceKey("intro", 0);
		MessageRuntimeFallbacks.OccurrenceKey second =
			new MessageRuntimeFallbacks.OccurrenceKey("intro", 1);
		MessageRuntimeFallbacks.OccurrenceKey third =
			new MessageRuntimeFallbacks.OccurrenceKey("outro", 0);
		MessageRuntimeFallbacks.CompilationGate gate =
			new MessageRuntimeFallbacks.CompilationGate();
		check(
			gate.markFailed(List.of(first, second)),
			"first bulk compile failure must emit one fallback"
		);
		check(
			!gate.markFailed(List.of(first))
				&& !gate.markFailed(List.of(second)),
			"per-occurrence retries covered by a bulk failure must not emit again"
		);
		check(
			gate.markFailed(List.of(third)),
			"a distinct failed occurrence may emit its own fallback"
		);

		MessageRuntimeFallbacks.CompilationGate refreshed =
			new MessageRuntimeFallbacks.CompilationGate();
		refreshed.inherit(gate);
		check(
			!refreshed.markFailed(List.of(first))
				&& refreshed.snapshot().equals(Set.of(first, second, third)),
			"an in-place recipient stream refresh must retain compile-fallback de-duplication"
		);
	}

	private static void checkOccurrencePlaybackClock() {
		MessageOccurrencePlayback playback = new MessageOccurrencePlayback(10L, 15L);
		MessageOccurrencePlayback.Step first = playback.advance(
			20L,
			MessageOccurrencePlayback.ObscuredAction.CONTINUE,
			OptionalLong.of(10L),
			OptionalLong.of(8L)
		);
		check(
			first.completedAt().isEmpty() && playback.playbackElapsed() == 5L,
			"safe-screen wait must not count as visual playback"
		);
		MessageOccurrencePlayback.Step paused = playback.advance(
			27L,
			MessageOccurrencePlayback.ObscuredAction.PAUSE,
			OptionalLong.of(10L),
			OptionalLong.of(8L)
		);
		check(
			paused.pausedDelta() == 7L
				&& !paused.pauseTimedOut()
				&& playback.projectedStart() == 22L,
			"obscured pause must freeze progress and shift the projected clock"
		);
		MessageOccurrencePlayback.Step completed = playback.advance(
			32L,
			MessageOccurrencePlayback.ObscuredAction.CONTINUE,
			OptionalLong.of(10L),
			OptionalLong.of(8L)
		);
		check(
			completed.completedAt().orElseThrow() == 32L
				&& completed.completion()
					== MessageOccurrencePlayback.Completion.NATURAL,
			"natural completion must include start wait and obscured pause"
		);
		check(
			completed.completedAt().orElseThrow() - playback.scheduledAt() == 22L,
			"after-node duration must use the authoritative occurrence end"
		);

		MessageOccurrencePlayback timeout = new MessageOccurrencePlayback(0L, 0L);
		timeout.advance(
			2L,
			MessageOccurrencePlayback.ObscuredAction.PAUSE,
			OptionalLong.empty(),
			OptionalLong.of(3L)
		);
		MessageOccurrencePlayback.Step timedOut = timeout.advance(
			6L,
			MessageOccurrencePlayback.ObscuredAction.PAUSE,
			OptionalLong.empty(),
			OptionalLong.of(3L)
		);
		check(
			timedOut.pauseTimedOut()
				&& timedOut.timeoutAt() == 3L
				&& timedOut.pausedDelta() == 1L,
			"max_pause must fire at the exact continuous-pause boundary"
		);

		MessageOccurrencePlayback resumed = new MessageOccurrencePlayback(0L, 0L);
		resumed.advance(
			2L,
			MessageOccurrencePlayback.ObscuredAction.PAUSE,
			OptionalLong.empty(),
			OptionalLong.of(3L)
		);
		resumed.advance(
			3L,
			MessageOccurrencePlayback.ObscuredAction.CONTINUE,
			OptionalLong.empty(),
			OptionalLong.of(3L)
		);
		MessageOccurrencePlayback.Step secondPause = resumed.advance(
			5L,
			MessageOccurrencePlayback.ObscuredAction.PAUSE,
			OptionalLong.empty(),
			OptionalLong.of(3L)
		);
		check(
			!secondPause.pauseTimedOut(),
			"returning to gameplay must reset the continuous max-pause window"
		);

		MessageOccurrencePlayback finalized = new MessageOccurrencePlayback(4L, 4L);
		MessageOccurrencePlayback.Step finalStep = finalized.advance(
			7L,
			MessageOccurrencePlayback.ObscuredAction.FINALIZE,
			OptionalLong.empty(),
			OptionalLong.empty()
		);
		check(
			finalStep.completion()
				== MessageOccurrencePlayback.Completion.FINALIZED
				&& finalStep.completedAt().orElseThrow() == 7L,
			"obscured finalize must end the occurrence immediately"
		);

		MessageOccurrencePlayback canceled = new MessageOccurrencePlayback(4L, 4L);
		MessageOccurrencePlayback.Step cancelStep = canceled.advance(
			8L,
			MessageOccurrencePlayback.ObscuredAction.CANCEL,
			OptionalLong.empty(),
			OptionalLong.empty()
		);
		check(
			cancelStep.completion()
				== MessageOccurrencePlayback.Completion.CANCELED
				&& cancelStep.completedAt().orElseThrow() == 8L,
			"obscured cancel must end only the current occurrence clock"
		);

		MessageOccurrencePlayback finalChat = new MessageOccurrencePlayback(9L, 9L);
		MessageOccurrencePlayback.Step finalChatStep =
			finalChat.finalizeWithoutAnimation(12L);
		check(
			finalChatStep.completedAt().orElseThrow() == 12L
				&& finalChat.playbackElapsed() == 0L,
			"final_chat_only must wait for fields without consuming animation time"
		);
	}

	private static void checkPlayDispositionsAndDefensiveCopies(
		final MessageCueDefinition parsed
	) {
		MessageCueDefinition cue = withConcurrency(
			parsed,
			id("ignore"),
			DuplicateMode.IGNORE_WHILE_ACTIVE,
			Optional.empty(),
			Optional.empty(),
			8,
			8
		);
		RuntimeState state = RuntimeState.empty(64);
		Map<String, String> mutableParameters = new LinkedHashMap<>();
		mutableParameters.put("key", "alpha");
		mutableParameters.put("body", "before");
		Set<UUID> mutableTargets = new LinkedHashSet<>(Set.of(PLAYER_A));
		ParameterResolution parameterResolution = ParameterResolution.resolved(
			mutableParameters,
			Map.of("intro", new TimeSpan(TimeSpan.NANOS_PER_TICK))
		);
		AudienceResolution audienceResolution = AudienceResolution.resolved(
			mutableTargets,
			Map.of()
		);
		InvocationContext context = context(uuid(1), mutableTargets, true);
		mutableParameters.put("body", "mutated-after-port-result");
		mutableTargets.add(PLAYER_B);

		PlayResult created = MessageInstanceAuthority.play(
			state,
			cue,
			context,
			clock(0),
			(ignoredCue, ignoredContext) -> parameterResolution,
			(audience, ignoredCue, ignoredContext, parameters) -> audienceResolution
		);
		check(created.disposition() == PlayDisposition.CREATED, "first play must create");
		check(
			created.projections().stream().anyMatch(event -> event.kind() == ProjectionKind.CREATE),
			"created play must emit CREATE"
		);
		check(
			created.projections().stream().anyMatch(
				event -> event.kind() == ProjectionKind.NODE_DUE
					&& event.nodeId().filter("intro"::equals).isPresent()
					&& event.occurrence() == 0
			),
			"zero-offset first occurrence must be projected"
		);
		MessageInstanceAuthority.MessageInstance instance = created.nextState()
			.instances()
			.get(created.instanceId().orElseThrow());
		check(
			instance.canonicalParameters().get("body").equals("before"),
			"canonical parameter values must be defensively copied"
		);
		check(
			instance.cueTargets().equals(Set.of(PLAYER_A)),
			"audience targets must be defensively copied"
		);

		PlayResult ignored = play(
			created.nextState(),
			cue,
			context(uuid(2), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "alpha", "body", "second"),
			Set.of(PLAYER_A)
		);
		check(
			ignored.disposition() == PlayDisposition.IGNORED,
			"active duplicate must be explicitly ignored"
		);
		check(
			ignored.nextState() == created.nextState(),
			"ignored duplicate must not allocate state"
		);

		PlayResult rejected = play(
			state,
			cue,
			context(uuid(3), Set.of(PLAYER_A), false),
			clock(0),
			Map.of("key", "alpha", "body", "blocked"),
			Set.of(PLAYER_A)
		);
		check(
			rejected.disposition() == PlayDisposition.REJECTED,
			"unauthorized invocation must be rejected"
		);
	}

	private static void checkRestartQueueCooldownAndRefresh(
		final MessageCueDefinition parsed
	) {
		MessageCueDefinition restartCue = withConcurrency(
			parsed,
			id("restart"),
			DuplicateMode.RESTART,
			Optional.empty(),
			Optional.empty(),
			8,
			8
		);
		PlayResult restartFirst = play(
			RuntimeState.empty(64),
			restartCue,
			context(uuid(10), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "first"),
			Set.of(PLAYER_A)
		);
		PlayResult restarted = play(
			restartFirst.nextState(),
			restartCue,
			context(uuid(11), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "second"),
			Set.of(PLAYER_A)
		);
		check(restarted.disposition() == PlayDisposition.CREATED, "restart creates a new instance");
		check(
			restarted.projections().stream().anyMatch(
				event -> event.kind() == ProjectionKind.REPLACE
					&& event.replacedInstanceId().equals(restartFirst.instanceId())
			),
			"restart must emit an explicit REPLACE projection"
		);

		MessageCueDefinition queueCue = withConcurrency(
			parsed,
			id("queue"),
			DuplicateMode.QUEUE,
			Optional.empty(),
			Optional.empty(),
			1,
			2
		);
		PlayResult queueFirst = play(
			RuntimeState.empty(64),
			queueCue,
			context(uuid(20), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "first"),
			Set.of(PLAYER_A)
		);
		PlayResult queued = play(
			queueFirst.nextState(),
			queueCue,
			context(uuid(21), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "second"),
			Set.of(PLAYER_A)
		);
		check(queued.disposition() == PlayDisposition.QUEUED, "duplicate queue mode must queue");
		check(queued.nextState().queue().size() == 1, "queued invocation must be retained");
		ControlResult completed = MessageInstanceAuthority.control(
			queued.nextState(),
			new InstanceSelector(queueFirst.instanceId().orElseThrow()),
			ControlAction.COMPLETE,
			clock(0),
			"self-check completion"
		);
		check(completed.accepted(), "queued predecessor must be completable");
		RuntimeState completedState = completed.nextState();
		for (MessageCallbackLedger.PreparedCallback callback : completed.callbacks()) {
			MessageInstanceAuthority.CallbackOutcomeResult outcome =
				MessageInstanceAuthority.recordCallbackOutcome(
					completedState,
					callback,
					true,
					Optional.empty(),
					0L
				);
			check(outcome.accepted(), "queued predecessor callback must settle");
			completedState = outcome.nextState();
		}
		AdvanceResult drained = MessageInstanceAuthority.advance(
			completedState,
			clock(0)
		);
		check(
			drained.accepted() && drained.nextState().queue().isEmpty(),
			"eligible queued invocation must drain after terminal callbacks settle"
		);
		check(
			drained.projections().stream().anyMatch(
				event -> event.kind() == ProjectionKind.CREATE
					&& event.instanceId().equals(queued.instanceId().orElseThrow())
			),
			"queue drain must create the reserved instance id"
		);

		MessageCueDefinition cooldownCue = withConcurrency(
			parsed,
			id("cooldown"),
			DuplicateMode.ALLOW,
			Optional.of(new TimeSpan(2L * TimeSpan.NANOS_PER_TICK)),
			Optional.empty(),
			8,
			8
		);
		PlayResult cooldownFirst = play(
			RuntimeState.empty(64),
			cooldownCue,
			context(uuid(30), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "same", "body", "first"),
			Set.of(PLAYER_A)
		);
		PlayResult cooling = play(
			cooldownFirst.nextState(),
			cooldownCue,
			context(uuid(31), Set.of(PLAYER_A), true),
			clock(1),
			Map.of("key", "same", "body", "second"),
			Set.of(PLAYER_A)
		);
		check(cooling.disposition() == PlayDisposition.IGNORED, "cooldown must ignore early play");
		PlayResult cooled = play(
			cooling.nextState(),
			cooldownCue,
			context(uuid(32), Set.of(PLAYER_A), true),
			clock(2),
			Map.of("key", "same", "body", "third"),
			Set.of(PLAYER_A)
		);
		check(cooled.disposition() == PlayDisposition.CREATED, "cooldown expiry must allow play");

		MessageCueDefinition refreshCue = withConcurrency(
			parsed,
			id("refresh"),
			DuplicateMode.REFRESH,
			Optional.empty(),
			Optional.of("key"),
			8,
			8
		);
		PlayResult refreshFirst = play(
			RuntimeState.empty(64),
			refreshCue,
			context(uuid(40), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "stable-slot", "body", "old"),
			Set.of(PLAYER_A)
		);
		PlayResult refreshed = play(
			refreshFirst.nextState(),
			refreshCue,
			context(uuid(41), Set.of(PLAYER_A), true),
			clock(1),
			Map.of("key", "stable-slot", "body", "new"),
			Set.of(PLAYER_A)
		);
		check(refreshed.disposition() == PlayDisposition.REFRESHED, "stable key must refresh");
		check(
			refreshed.instanceId().equals(refreshFirst.instanceId()),
			"refresh must preserve the authoritative instance address"
		);
		check(
			refreshed.projections().stream().anyMatch(
				event -> event.kind() == ProjectionKind.REFRESH
			),
			"refresh must emit an explicit REFRESH projection"
		);
		check(
			refreshed.nextState()
				.instances()
				.get(refreshed.instanceId().orElseThrow())
				.canonicalParameters()
				.get("body")
				.equals("new"),
			"refresh must replace canonical values without scanning history"
		);
	}

	private static void checkDagsEventsAndControls(final MessageCueDefinition parsed) {
		MessageCueDefinition cue = withConcurrency(
			parsed,
			id("timeline"),
			DuplicateMode.ALLOW,
			Optional.empty(),
			Optional.empty(),
			8,
			8
		);
		PlayResult created = play(
			RuntimeState.empty(64),
			cue,
			context(uuid(50), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "timeline", "body", "body"),
			Set.of(PLAYER_A)
		);
		check(
			countNode(created.projections(), "intro") == 1,
			"first repeated node occurrence must fire at cue start"
		);
		MessageInstanceAuthority.AdvanceResult tickOne = MessageInstanceAuthority.advance(
			created.nextState(),
			clock(1)
		);
		check(countNode(tickOne.projections(), "intro") == 1, "second repeat must fire at 1t");
		MessageInstanceAuthority.AdvanceResult tickTwo = MessageInstanceAuthority.advance(
			tickOne.nextState(),
			clock(2)
		);
		check(
			countNode(tickTwo.projections(), "after") == 0,
			"after-node occurrence must wait for dependency duration and offset"
		);
		MessageInstanceAuthority.AdvanceResult tickThree = MessageInstanceAuthority.advance(
			tickTwo.nextState(),
			clock(3)
		);
		check(countNode(tickThree.projections(), "after") == 1, "DAG successor must fire at 3t");
		check(
			tickThree.callbacks().stream().anyMatch(
				callback -> callback.key().trigger() == CallbackTrigger.TARGET_COMPLETE
			),
			"target-complete callback must be prepared from server time"
		);
		check(
			tickThree.callbacks().stream().anyMatch(
				callback -> callback.key().trigger() == CallbackTrigger.ALL_COMPLETE
			),
			"all-complete callback must be prepared from server time"
		);
		check(
			tickThree.nextState()
				.instances()
				.get(created.instanceId().orElseThrow())
				.status() == InstanceStatus.COMPLETING,
			"natural finite timeline must wait for terminal callback outcomes"
		);
		RuntimeState timelineState = tickThree.nextState();
		for (MessageCallbackLedger.PreparedCallback callback : tickThree.callbacks()) {
			MessageInstanceAuthority.CallbackOutcomeResult outcome =
				MessageInstanceAuthority.recordCallbackOutcome(
					timelineState,
					callback,
					true,
					Optional.empty(),
					3L
				);
			check(outcome.accepted(), "prepared timeline callback outcome must be recordable");
			timelineState = outcome.nextState();
		}
		AdvanceResult timelineTerminal = MessageInstanceAuthority.advance(
			timelineState,
			clock(3)
		);
		check(
			timelineTerminal.accepted()
				&& timelineTerminal.nextState()
					.instances()
					.get(created.instanceId().orElseThrow())
					.status() == InstanceStatus.COMPLETED,
			"natural finite timeline must complete after terminal callbacks settle"
		);
		check(
			timelineTerminal.projections().stream().anyMatch(event ->
				event.kind() == ProjectionKind.FINISH
			),
			"natural completion must drain authorized client presentation instead of collapsing it"
		);

		PlayResult pausedStart = play(
			RuntimeState.empty(64),
			cue,
			context(uuid(60), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "pause", "body", "body"),
			Set.of(PLAYER_A)
		);
		ControlResult paused = MessageInstanceAuthority.control(
			pausedStart.nextState(),
			new GroupSelector("self_check"),
			ControlAction.PAUSE,
			clock(1),
			"self-check group pause"
		);
		check(paused.changed() == 1, "registered control group must pause its active instance");
		MessageInstanceAuthority.AdvanceResult whilePaused = MessageInstanceAuthority.advance(
			paused.nextState(),
			clock(20)
		);
		check(!whilePaused.changed(), "paused message clock must not advance");
		ControlResult resumed = MessageInstanceAuthority.control(
			whilePaused.nextState(),
			new TargetSelector(PLAYER_A),
			ControlAction.RESUME,
			clock(20),
			"self-check target resume"
		);
		check(resumed.changed() == 1, "target selector must resume matching paused instances");

		PlayResult cancelledStart = play(
			RuntimeState.empty(64),
			cue,
			context(uuid(70), Set.of(PLAYER_A), true),
			clock(0),
			Map.of("key", "cancel", "body", "body"),
			Set.of(PLAYER_A)
		);
		ControlResult cancelled = MessageInstanceAuthority.control(
			cancelledStart.nextState(),
			new InstanceSelector(cancelledStart.instanceId().orElseThrow()),
			ControlAction.CANCEL,
			clock(0),
			"self-check cancel"
		);
		check(
			cancelled.callbacks().stream().anyMatch(
				callback -> callback.key().trigger() == CallbackTrigger.INTERRUPT
			),
			"cancellation must prepare only the registered interrupt callback"
		);
		check(
			cancelled.nextState()
				.instances()
				.get(cancelledStart.instanceId().orElseThrow())
				.status() == InstanceStatus.CANCELLING,
			"zero-offset interrupt timeline must wait for callback outcome"
		);
		ControlResult cancelledAgain = MessageInstanceAuthority.control(
			cancelled.nextState(),
			new InstanceSelector(cancelledStart.instanceId().orElseThrow()),
			ControlAction.CANCEL,
			clock(0),
			"idempotent cancel"
		);
		check(
			cancelledAgain.accepted() && cancelledAgain.changed() == 0,
			"repeated cancellation must be idempotent"
		);
		RuntimeState cancelledState = cancelled.nextState();
		for (MessageCallbackLedger.PreparedCallback callback : cancelled.callbacks()) {
			MessageInstanceAuthority.CallbackOutcomeResult outcome =
				MessageInstanceAuthority.recordCallbackOutcome(
					cancelledState,
					callback,
					true,
					Optional.empty(),
					0L
				);
			check(outcome.accepted(), "interrupt callback outcome must be recordable");
			cancelledState = outcome.nextState();
		}
		AdvanceResult cancelledTerminal = MessageInstanceAuthority.advance(
			cancelledState,
			clock(0)
		);
		check(
			cancelledTerminal.accepted()
				&& cancelledTerminal.nextState()
					.instances()
					.get(cancelledStart.instanceId().orElseThrow())
					.status() == InstanceStatus.CANCELLED,
			"cancellation must become terminal only after callback outcome"
		);
	}

	private static void checkCallbackLedgerCrashSafety() {
		Ledger empty = Ledger.empty(8);
		CallbackKey firstKey = new CallbackKey(
			uuid(80),
			0L,
			"callback",
			0,
			CallbackTrigger.START,
			Optional.empty()
		);
		MessageCallbackLedger.PrepareResult first = MessageCallbackLedger.prepare(
			empty,
			firstKey,
			id("callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN,
			10L
		);
		check(first.disposition() == PrepareDisposition.PREPARED, "callback must prepare once");
		check(
			MessageCallbackLedger.prepare(
				first.nextLedger(),
				firstKey,
				id("callback"),
				CallbackExecution.AS_SERVER,
				CallbackLocation.ORIGIN,
				10L
			).disposition() == PrepareDisposition.REJECTED,
			"prepared callback must not prepare twice"
		);
		MessageCallbackLedger.MutationResult recovered =
			MessageCallbackLedger.recoverPrepared(
				first.nextLedger(),
				11L,
				"simulated crash"
			);
		check(recovered.changed(), "prepared callback must become outcome-unknown on recovery");
		check(
			recovered.nextLedger().find(firstKey).orElseThrow().status()
				== CallbackStatus.OUTCOME_UNKNOWN,
			"crash-left callback must retain explicit unknown outcome"
		);
		check(
			MessageCallbackLedger.prepare(
				recovered.nextLedger(),
				firstKey,
				id("callback"),
				CallbackExecution.AS_SERVER,
				CallbackLocation.ORIGIN,
				12L
			).disposition() == PrepareDisposition.REJECTED,
			"outcome-unknown callback must never replay automatically"
		);

		CallbackKey secondKey = new CallbackKey(
			uuid(81),
			0L,
			"callback",
			0,
			CallbackTrigger.START,
			Optional.empty()
		);
		MessageCallbackLedger.PrepareResult second = MessageCallbackLedger.prepare(
			recovered.nextLedger(),
			secondKey,
			id("callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN,
			12L
		);
		MessageCallbackLedger.MutationResult succeeded = MessageCallbackLedger.recordOutcome(
			second.nextLedger(),
			second.callback().orElseThrow(),
			true,
			Optional.empty(),
			13L
		);
		check(
			succeeded.nextLedger().find(secondKey).orElseThrow().status()
				== CallbackStatus.SUCCEEDED,
			"successful callback outcome must be durable"
		);
		check(
			MessageCallbackLedger.prepare(
				succeeded.nextLedger(),
				secondKey,
				id("callback"),
				CallbackExecution.AS_SERVER,
				CallbackLocation.ORIGIN,
				14L
			).disposition() == PrepareDisposition.ALREADY_SUCCEEDED,
			"successful callback must be skipped without re-execution"
		);

		CallbackKey failedKey = new CallbackKey(
			uuid(82),
			0L,
			"callback",
			0,
			CallbackTrigger.START,
			Optional.empty()
		);
		MessageCallbackLedger.PrepareResult failing = MessageCallbackLedger.prepare(
			succeeded.nextLedger(),
			failedKey,
			id("callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN,
			15L
		);
		MessageCallbackLedger.MutationResult failed = MessageCallbackLedger.recordOutcome(
			failing.nextLedger(),
			failing.callback().orElseThrow(),
			false,
			Optional.of("expected self-check failure"),
			16L
		);
		check(
			failed.nextLedger().find(failedKey).orElseThrow().status()
				== CallbackStatus.FAILED,
			"failed callback outcome must be retained explicitly"
		);
		check(
			MessageCallbackLedger.prepare(
				failed.nextLedger(),
				failedKey,
				id("callback"),
				CallbackExecution.AS_SERVER,
				CallbackLocation.ORIGIN,
				17L
			).disposition() == PrepareDisposition.REJECTED,
			"failed callback must not retry automatically"
		);
	}

	private static void checkExternalConditionFragment() {
		MessageDefinitionParser.ParseResult<MessageCueDefinition> parsed =
			MessageDefinitionParser.parseMessageCue(
				id("external_condition"),
				"""
				{
				  "format_version": 1,
				  "nodes": [
				    {
				      "id": "conditional",
				      "type": "text",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "when": {
				        "evaluate_at": "node_start",
				        "expression": {
				          "eq": [
				            {"bind": "player.role.id"},
				            {"literal": "pixel_tzz:hunter"}
				          ]
				        }
				      },
				      "channel": "subtitle",
				      "content": {
				        "text": {
				          "fallback": {
				            "parts": [
				              {"component": {"text": "condition"}}
				            ]
				          }
				        },
				        "duration": {
				          "mode": "fixed_total",
				          "total": "1t",
				          "language_basis": "fixed"
				        }
				      }
				    }
				  ]
				}
				"""
			);
		check(
			parsed.valid(),
			"message condition fragment must accept runtime-authorized bindings: " + parsed.issues()
		);
		UiDefinitionParser.ParseResult<?> invalid =
			UiDefinitionParser.parseConditionFragment(
				JsonParser.parseString(
					"""
					{"exists":{"bind":"../untrusted storage"}}
					"""
				)
			);
		check(
			invalid.value().isEmpty()
				&& invalid.issues().stream().anyMatch(
					issue -> issue.code().equals("INVALID_BINDING_PATH")
				),
			"external condition fragments must still reject unsafe binding paths"
		);
	}

	private static PlayResult play(
		final RuntimeState state,
		final MessageCueDefinition cue,
		final InvocationContext context,
		final ClockSample clock,
		final Map<String, String> parameters,
		final Set<UUID> targets
	) {
		return MessageInstanceAuthority.play(
			state,
			cue,
			context,
			clock,
			(ignoredCue, ignoredContext) -> ParameterResolution.resolved(
				parameters,
				Map.of("intro", new TimeSpan(TimeSpan.NANOS_PER_TICK))
			),
			(audience, ignoredCue, ignoredContext, canonical) ->
				AudienceResolution.resolved(targets, Map.of())
		);
	}

	private static InvocationContext context(
		final UUID instanceId,
		final Set<UUID> targets,
		final boolean authorized
	) {
		return new InvocationContext(
			instanceId,
			authorized,
			Optional.of(PLAYER_A),
			targets,
			Optional.of(GAME),
			Optional.empty(),
			Optional.empty(),
			Map.of("source", "self-check"),
			1L,
			false
		);
	}

	private static InvocationContext contextWithGame(
		final UUID instanceId,
		final boolean authorized,
		final boolean bypassContext,
		final Identifier gameId
	) {
		return new InvocationContext(
			instanceId,
			authorized,
			Optional.of(PLAYER_A),
			Set.of(PLAYER_A),
			Optional.of(gameId),
			Optional.of(id("other_phase")),
			Optional.of(id("other_task")),
			Map.of("source", "self-check"),
			1L,
			bypassContext,
			false
		);
	}

	private static MessageCueDefinition withConcurrency(
		final MessageCueDefinition source,
		final Identifier id,
		final DuplicateMode mode,
		final Optional<TimeSpan> cooldown,
		final Optional<String> refreshKey,
		final int maxActive,
		final int maxQueue
	) {
		CuePolicies policies = source.policies();
		ConcurrencyPolicy concurrency = new ConcurrencyPolicy(
			mode,
			cooldown,
			true,
			Set.of("key"),
			refreshKey,
			ChatInterruptMode.FINALIZE,
			0,
			Set.of("self_check")
		);
		CuePolicies replacedPolicies = new CuePolicies(
			policies.timing(),
			policies.audience(),
			policies.context(),
			concurrency,
			policies.delivery(),
			policies.lifecycle(),
			policies.accessibility(),
			Set.of("self_check"),
			policies.capture(),
			policies.defaultConflict()
		);
		SoftLimits limits = new SoftLimits(
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.of(maxActive),
			Optional.of(maxQueue),
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
		return new MessageCueDefinition(
			id,
			source.game(),
			source.required(),
			replacedPolicies,
			source.parameters(),
			source.fields(),
			source.nodes(),
			source.history(),
			source.staticFallback(),
			source.assets(),
			limits,
			source.canonicalDocument()
		);
	}

	private static MessageCueDefinition withRuntimeLimits(
		final MessageCueDefinition source,
		final Identifier id,
		final DuplicateMode duplicate,
		final RestartMode restart,
		final int maxActive,
		final int maxQueue,
		final int maxPersisted,
		final Optional<TimeSpan> maxDuration
	) {
		MessageCueDefinition concurrent = withConcurrency(
			source,
			id,
			duplicate,
			Optional.empty(),
			Optional.empty(),
			maxActive,
			maxQueue
		);
		CuePolicies policies = concurrent.policies();
		LifecyclePolicy lifecycle = policies.lifecycle();
		LifecyclePolicy replacedLifecycle = new LifecyclePolicy(
			lifecycle.screenStart(),
			lifecycle.obscured(),
			lifecycle.screenWaitTimeout(),
			lifecycle.screenTimeoutAction(),
			lifecycle.maxPause(),
			lifecycle.reload(),
			restart,
			lifecycle.externalConflict(),
			lifecycle.reconnect(),
			lifecycle.lateJoin(),
			lifecycle.resourceReload(),
			lifecycle.respawn(),
			lifecycle.attachment(),
			lifecycle.dimensionExit()
		);
		CuePolicies replacedPolicies = new CuePolicies(
			policies.timing(),
			policies.audience(),
			policies.context(),
			policies.concurrency(),
			policies.delivery(),
			replacedLifecycle,
			policies.accessibility(),
			policies.controlGroups(),
			policies.capture(),
			policies.defaultConflict()
		);
		SoftLimits current = concurrent.softLimits();
		SoftLimits limits = new SoftLimits(
			current.maxVisibleGraphemes(),
			current.maxLines(),
			current.maxNodes(),
			current.maxSoundNodes(),
			current.maxCallbackNodes(),
			current.maxVariants(),
			current.maxActiveInstances(),
			current.maxQueueDepth(),
			maxDuration,
			Optional.of(maxPersisted),
			current.maxPendingOffline()
		);
		return new MessageCueDefinition(
			concurrent.id(),
			concurrent.game(),
			concurrent.required(),
			replacedPolicies,
			concurrent.parameters(),
			concurrent.fields(),
			concurrent.nodes(),
			concurrent.history(),
			concurrent.staticFallback(),
			concurrent.assets(),
			limits,
			concurrent.canonicalDocument()
		);
	}

	private static MessageCueDefinition parseCue() {
		MessageDefinitionParser.ParseResult<MessageCueDefinition> parsed =
			MessageDefinitionParser.parseMessageCue(
				id("base"),
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "policies": {
				    "timing": {"clock": "game_time"},
				    "audience": {
				      "target": {"source": "call_targets", "online_only": true}
				    },
				    "concurrency": {
				      "duplicate": "ignore_while_active",
				      "dedupe_targets": true,
				      "dedupe_parameters": ["key"]
				    }
				  },
				  "parameters": {
				    "key": {
				      "type": "string",
				      "required": true,
				      "dedupe": true
				    },
				    "body": {
				      "type": "string",
				      "required": true
				    }
				  },
				  "nodes": [
				    {
				      "id": "intro",
				      "type": "text",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "repeat": {"count": 2, "interval": "1t", "capture": "keep"},
				      "channel": "subtitle",
				      "content": {
				        "text": {
				          "fallback": {
				            "parts": [
				              {"component": {"text": "intro"}}
				            ]
				          }
				        },
				        "duration": {
				          "mode": "fixed_total",
				          "total": "1t",
				          "language_basis": "fixed"
				        }
				      }
				    },
				    {
				      "id": "after",
				      "type": "sound",
				      "schedule": {
				        "type": "after_node",
				        "node": "intro",
				        "offset": "1t"
				      },
				      "sound": "minecraft:ui.button.click",
				      "category": "ui",
				      "volume": 1.0,
				      "pitch": 1.0
				    },
				    {
				      "id": "target_complete",
				      "type": "callback",
				      "schedule": {
				        "type": "cue_event",
				        "event": "target_complete",
				        "offset": "0t"
				      },
				      "function": "pixel_tzz:self_check/target_complete",
				      "execution": "as_server",
				      "location": "origin"
				    },
				    {
				      "id": "all_complete",
				      "type": "callback",
				      "schedule": {
				        "type": "cue_event",
				        "event": "all_complete",
				        "offset": "0t"
				      },
				      "function": "pixel_tzz:self_check/all_complete",
				      "execution": "as_server",
				      "location": "origin"
				    },
				    {
				      "id": "interrupt",
				      "type": "callback",
				      "schedule": {
				        "type": "cue_event",
				        "event": "interrupt",
				        "offset": "0t"
				      },
				      "function": "pixel_tzz:self_check/interrupt",
				      "execution": "as_server",
				      "location": "origin"
				    }
				  ]
				}
				"""
			);
		check(parsed.valid(), "authority self-check cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static long countNode(
		final List<MessageInstanceAuthority.ProjectionEvent> projections,
		final String nodeId
	) {
		return projections.stream()
			.filter(event -> event.kind() == ProjectionKind.NODE_DUE)
			.filter(event -> event.nodeId().filter(nodeId::equals).isPresent())
			.count();
	}

	private static ClockSample clock(final long tick) {
		return new ClockSample(tick, tick * TimeSpan.NANOS_PER_TICK);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static UUID uuid(final long value) {
		return new UUID(0L, value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
