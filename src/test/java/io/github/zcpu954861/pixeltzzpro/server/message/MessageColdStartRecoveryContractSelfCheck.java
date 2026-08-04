package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.message.MessagePlaybackSeekPolicy;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.Ledger;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.DedupeKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InvocationContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RuntimeState;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRecipientStateCodec.RecipientStateSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Executable integration contracts for V3B cold-start seek, TTL, and terminal recovery. */
public final class MessageColdStartRecoveryContractSelfCheck {
	private static final Identifier CUE_ID = Identifier.parse("test:recovery_contract");
	private static final Identifier GAME_ID = Identifier.parse("test:game");
	private static final Identifier PHASE_ID = Identifier.parse("test:phase");
	private static final UUID GAME_INSTANCE = uuid("game-instance");
	private static final UUID RECIPIENT_A = uuid("recipient-a");
	private static final UUID RECIPIENT_B = uuid("recipient-b");

	private MessageColdStartRecoveryContractSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkPendingTtlDoesNotReset();
		checkRecoveredSeekSkipsOnlyPastSounds();
		checkReconnectTerminalControls();
		checkRecoveredFinalIsConsumedOnce();
		checkRecoveredFinalBoundaryOrderingAndIsolation();
		checkRecoveredFinalRuntimeWiring();
		checkRecoveredFinalRequiresEveryFrozenTextNode();
		checkLockedSlotCanonicalOrder();
		checkFinalizeHistoryRetryIsIdempotent();
		checkTerminalRetirementReclaimsOnlyOwnedCallbacks();
		System.out.println("MESSAGE_COLD_START_RECOVERY_CONTRACT_SELF_CHECK=PASS");
	}

	private static void checkPendingTtlDoesNotReset() {
		long first = MessageRecoveryProjectionPolicy.pendingPresentationExpiry(
			160L,
			new ClockSample(100L, 10_000_000_000L)
		);
		long resumed = MessageRecoveryProjectionPolicy.pendingPresentationExpiry(
			160L,
			new ClockSample(130L, 11_500_000_000L)
		);
		check(
			first == 13_000_000_000L && resumed == first,
			"repeated recovery must preserve the original absolute offline TTL deadline"
		);
		check(
			MessageRecoveryProjectionPolicy.pendingPresentationExpiry(
				160L,
				new ClockSample(170L, 14_000_000_000L)
			) == 14_000_000_000L,
			"an already-expired offline TTL must become due immediately"
		);
	}

	private static void checkRecoveredSeekSkipsOnlyPastSounds() {
		long recoveredNow = 300L * TimeSpan.NANOS_PER_TICK;
		long persistedElapsed = 120L * TimeSpan.NANOS_PER_TICK;
		long visualAnchor = MessageRecoveryProjectionPolicy.recoveredVisualAnchor(
			recoveredNow,
			persistedElapsed
		);
		long clientSeekElapsed = (
			300L - visualAnchor / TimeSpan.NANOS_PER_TICK
		) * TimeSpan.NANOS_PER_TICK;
		check(
			clientSeekElapsed == persistedElapsed,
			"the rebuilt plan clock must seek to the persisted elapsed position"
		);
		check(
			MessagePlaybackSeekPolicy.suppressPastSound(clientSeekElapsed, 0L),
			"a recovered plan must not replay old sound nodes"
		);
		check(
			!MessagePlaybackSeekPolicy.suppressPastSound(
				clientSeekElapsed,
				clientSeekElapsed - MessagePlaybackSeekPolicy.LIVE_SOUND_GRACE_NANOS
			),
			"the live transport grace boundary must remain audible"
		);
		check(
			MessagePlaybackSeekPolicy.suppressPastSound(
				clientSeekElapsed,
				clientSeekElapsed - MessagePlaybackSeekPolicy.LIVE_SOUND_GRACE_NANOS - 1L
			),
			"a sound one nanosecond beyond the grace boundary must be suppressed"
		);
		checkThrows(
			() -> MessageRecoveryProjectionPolicy.recoveredVisualAnchor(1L, 2L),
			"invalid negative recovered anchors must fail closed"
		);
	}

	private static void checkReconnectTerminalControls() {
		check(
			MessageRecoveryProjectionPolicy.reconnectControl(InstanceStatus.PAUSED)
				.equals(Optional.of(MessageControlS2CPayload.Action.PAUSE))
				&& MessageRecoveryProjectionPolicy.reconnectControl(InstanceStatus.COMPLETING)
					.equals(Optional.of(MessageControlS2CPayload.Action.FINALIZE))
				&& MessageRecoveryProjectionPolicy.reconnectControl(InstanceStatus.CANCELLING)
					.equals(Optional.of(MessageControlS2CPayload.Action.CANCEL)),
			"paused, completing, and cancelling recovery must resend their control packet"
		);
		check(
			MessageRecoveryProjectionPolicy.reconnectControl(InstanceStatus.ACTIVE).isEmpty()
				&& MessageRecoveryProjectionPolicy.reconnectControl(InstanceStatus.COMPLETED).isEmpty()
				&& MessageRecoveryProjectionPolicy.reconnectControl(InstanceStatus.CANCELLED).isEmpty(),
			"active and already-terminal plans must not receive a duplicate recovery control"
		);
	}

	private static void checkRecoveredFinalIsConsumedOnce() {
		MessageRecoveredFinalQueue<String> queue = new MessageRecoveredFinalQueue<>();
		UUID firstId = uuid("final-first");
		UUID secondId = uuid("final-second");
		queue.enqueue(firstId, List.of(RECIPIENT_A, RECIPIENT_B), "first");
		queue.enqueue(firstId, List.of(RECIPIENT_A), "duplicate-retry");
		queue.enqueue(secondId, List.of(RECIPIENT_A), "second");
		queue.beginConnection(RECIPIENT_A);
		check(
			queue.drainReady(RECIPIENT_A).isEmpty()
				&& queue.pendingRecipients() == 2,
			"a physical join must not consume durable finals before player authority is restored"
		);
		queue.markAuthorityReady(RECIPIENT_A);
		check(
			queue.drainReady(RECIPIENT_A).equals(List.of("first", "second"))
				&& queue.drainReady(RECIPIENT_A).isEmpty(),
			"the process-local final index must de-duplicate retries within one drain batch"
		);
		queue.beginConnection(RECIPIENT_B);
		queue.markAuthorityReady(RECIPIENT_B);
		check(
			queue.drainReady(RECIPIENT_B).equals(List.of("first"))
				&& queue.pendingRecipients() == 0,
			"one recipient drain must not consume another recipient's final projection"
		);
	}

	private static void checkRecoveredFinalBoundaryOrderingAndIsolation() {
		List<String> events = new ArrayList<>();
		List<String> retained = new ArrayList<>();
		List<MessageRecoveredFinalDeliveryBoundary.Stage> failures = new ArrayList<>();
		var summary = MessageRecoveredFinalDeliveryBoundary.process(
			List.of(
				"permanent_authorization",
				"authorization_error",
				"history_permanent",
				"history_retry",
				"history_error",
				"decode_permanent",
				"decode_error",
				"consume_retry",
				"consume_error",
				"already_settled",
				"send_error",
				"success"
			),
			item -> {
				events.add(item + ":authorize");
				if (item.equals("authorization_error")) {
					throw new IllegalStateException("authorization");
				}
				return item.equals("permanent_authorization")
					? MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT
					: MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED;
			},
			item -> {
				events.add(item + ":history");
				if (item.equals("history_error")) {
					throw new IllegalStateException("history");
				}
				if (item.equals("history_permanent")) {
					return MessageRecoveredFinalDeliveryBoundary.GateDecision.PERMANENT_REJECT;
				}
				return item.equals("history_retry")
					? MessageRecoveredFinalDeliveryBoundary.GateDecision.RETRY
					: MessageRecoveredFinalDeliveryBoundary.GateDecision.PROCEED;
			},
			item -> {
				events.add(item + ":decode");
				if (item.equals("decode_error")) {
					throw new IllegalStateException("decode");
				}
				return item.equals("decode_permanent")
					? MessageRecoveredFinalDeliveryBoundary.DecodeDecision.permanentReject()
					: MessageRecoveredFinalDeliveryBoundary.DecodeDecision.decoded(
						item + ":decoded"
					);
			},
			item -> {
				events.add(item + ":consume");
				if (item.equals("consume_error")) {
					throw new IllegalStateException("consume");
				}
				if (item.equals("consume_retry")) {
					return MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.RETRY;
				}
				return item.equals("already_settled")
					? MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.ALREADY_SETTLED
					: MessageRecoveredFinalDeliveryBoundary.ConsumeDecision.CONSUMED;
			},
			(item, decoded) -> {
				events.add(item + ":send:" + decoded);
				if (item.equals("send_error")) {
					throw new IllegalStateException("send");
				}
			},
			retained::add,
			(item, stage, error) -> failures.add(stage)
		);
		check(
			summary.attempted() == 12
				&& summary.delivered() == 1
				&& summary.retired() == 4
				&& summary.retryable() == 6
				&& summary.failed() == 1,
			"recovered-final boundary must classify every isolated item exactly once"
		);
		check(
			failures.equals(List.of(
				MessageRecoveredFinalDeliveryBoundary.Stage.AUTHORIZATION,
				MessageRecoveredFinalDeliveryBoundary.Stage.HISTORY,
				MessageRecoveredFinalDeliveryBoundary.Stage.DECODE,
				MessageRecoveredFinalDeliveryBoundary.Stage.CONSUME,
				MessageRecoveredFinalDeliveryBoundary.Stage.SEND
			)),
			"every recovered-final stage must report its own failure without aborting the batch"
		);
		check(
			!events.contains("permanent_authorization:history")
				&& events.contains("permanent_authorization:consume")
				&& events.contains("history_permanent:consume")
				&& events.contains("decode_permanent:consume")
				&& !events.contains("history_error:decode")
				&& !events.contains("decode_error:consume")
				&& !events.stream().anyMatch(value -> value.startsWith("consume_error:send")),
			"permanent rejection must retire without disclosure and retryable failure must stop"
		);
		check(
			retained.equals(List.of(
				"authorization_error",
				"history_retry",
				"history_error",
				"decode_error",
				"consume_retry",
				"consume_error"
			)),
			"only retryable pre-send failures and checkpoint conflicts may return to the queue"
		);
		check(
			events.indexOf("success:authorize") < events.indexOf("success:history")
				&& events.indexOf("success:history") < events.indexOf("success:decode")
				&& events.indexOf("success:decode") < events.indexOf("success:consume")
				&& events.indexOf("success:consume")
					< events.indexOf("success:send:success:decoded"),
			"authorized history and decoding must complete before durable consume and send"
		);
	}

	private static void checkRecoveredFinalRuntimeWiring() {
		final String source;
		final String coreRuntime;
		try {
			source = Files.readString(Path.of(
				"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageServerRuntime.java"
			));
			coreRuntime = Files.readString(Path.of(
				"src/main/java/io/github/zcpu954861/pixeltzzpro/server/PixelTzzServerRuntime.java"
			));
		} catch (IOException error) {
			throw new IllegalStateException("could not inspect recovered-final runtime wiring", error);
		}
		String startup = section(
			source,
			"static void recoverPersistedRuntime",
			"private static void restoreContinuingAction"
		);
		check(
			!startup.contains("persistRecoveredFinalHistoryForRecipient(")
				&& !startup.contains("MessageHistoryAuthority.append"),
			"server startup must only queue recovered finals and never materialize history"
		);
		String history = section(
			source,
			"persistRecoveredFinalHistoryForRecipient",
			"prepareRecoveredFinalDelivery"
		);
		check(
			history.contains("UUID viewer = player.getUUID();")
				&& history.contains("audience.cueTargets().contains(viewer)")
				&& history.contains("recipientAuthorizedForHistory(")
				&& history.contains("List.of(materialized.record().orElseThrow())")
				&& !history.contains("for (UUID viewer"),
			"recovered history must authorize and append only the current handshake recipient"
		);
		String ready = section(
			source,
			"static void playerAuthorityReady",
			"private static void onPlayerJoined"
		);
		int authorization = ready.indexOf("recoveredStaticFinalAuthorized(");
		int materialization = ready.indexOf("persistRecoveredFinalHistoryForRecipient(");
		int decoding = ready.indexOf("MessageRuntimeFallbacks.decodeCanonical(");
		int consume = ready.indexOf("prepareRecoveredFinalDelivery(");
		int send = ready.indexOf("sendStaticFallback(");
		check(
			authorization >= 0
				&& authorization < materialization
				&& materialization < decoding
				&& decoding < consume
				&& consume < send,
			"authority-ready delivery must authorize, persist history, decode, consume and send in order"
		);
		check(
			ready.contains("GateDecision.PERMANENT_REJECT")
				&& ready.contains("RECOVERED_STATIC_FINALS.enqueue("),
			"deterministic rejection must retire durably while retryable work stays queued"
		);
		String recoveredAuthorization = section(
			source,
			"private static boolean recoveredStaticFinalAuthorized",
			"private static Optional<Set<String>> authorizedFinalNodes"
		);
		int generationGate = recoveredAuthorization.indexOf(
			"definitions.generation() != recovered.authorizedDefinitionGeneration()"
		);
		int liveAudienceGate = recoveredAuthorization.indexOf(
			"MessageMinecraftResolver.resolveAudience("
		);
		check(
			generationGate >= 0
				&& liveAudienceGate > generationGate
				&& recoveredAuthorization.substring(
					generationGate,
					liveAudienceGate
				).contains("return false;"),
			"a changed definition generation must fail closed before live recovered-final disclosure"
		);
		String handshake = section(
			coreRuntime,
			"private static void onHandshakeResponse",
			"private static void onConsoleRequest"
		);
		check(
			handshake.indexOf("restoreMemberOnline(context.server(), context.player())") >= 0
				&& handshake.indexOf("restoreMemberOnline(context.server(), context.player())")
					< handshake.indexOf("MessageServerRuntime.playerAuthorityReady(context.player())"),
			"online player authority must be restored before online_only recovered history is resolved"
		);
	}

	private static String section(
		final String source,
		final String startMarker,
		final String endMarker
	) {
		int start = source.indexOf(startMarker);
		int end = source.indexOf(endMarker, start);
		check(start >= 0 && end > start, "runtime source boundary is missing: " + startMarker);
		return source.substring(start, end);
	}

	private static void checkRecoveredFinalRequiresEveryFrozenTextNode() {
		MessageCueDefinition cue = recoveredFinalCue();
		MessageInstance instance = instance(
			cue,
			uuid("recovered-final-authorization"),
			RECIPIENT_A,
			InstanceStatus.ACTIVE
		);
		check(
			MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
				instance,
				RECIPIENT_A,
				Set.of("public_notice", "private_role")
			),
			"a recovered static final may be sent only when every frozen visible node remains authorized"
		);
		check(
			!MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
				instance,
				RECIPIENT_A,
				Set.of("public_notice")
			),
			"one public node must not authorize a whole-cue fallback after another node is revoked"
		);
		check(
			!MessageRecoveredFinalProjectionPolicy.maySendStaticFallback(
				instance,
				RECIPIENT_B,
				Set.of("public_notice", "private_role")
			),
			"a recipient outside the frozen cue audience must never receive its recovered fallback"
		);
	}

	private static void checkLockedSlotCanonicalOrder() {
		MessageCueDefinition cue = cue();
		Map<Integer, ResolvedComponent> shuffled = new LinkedHashMap<>();
		shuffled.put(7, ResolvedComponent.parseCanonical("{\"text\":\"seven\"}"));
		shuffled.put(1, ResolvedComponent.parseCanonical("{\"text\":\"one\"}"));
		shuffled.put(4, ResolvedComponent.parseCanonical("{\"text\":\"four\"}"));
		RecipientStateSnapshot snapshot = new RecipientStateSnapshot(
			RECIPIENT_A,
			0L,
			Optional.of(0L),
			Set.of(),
			Set.of(),
			shuffled,
			Map.of(),
			Map.of(),
			Map.of(),
			Optional.empty(),
			false
		);
		PersistedMessageRuntime.RecipientState encoded = MessageRecipientStateCodec.encode(
			cue,
			snapshot
		);
		check(
			encoded.lockedFields().stream()
				.map(PersistedMessageRuntime.LockedField::slot)
				.toList()
				.equals(List.of(1, 4, 7))
				&& MessageRecipientStateCodec.encode(
					cue,
					MessageRecipientStateCodec.decode(cue, encoded)
				).equals(encoded),
			"locked fields must survive recovery in canonical numeric slot order"
		);

		List<PersistedMessageRuntime.LockedField> reversed = new ArrayList<>(
			encoded.lockedFields()
		);
		java.util.Collections.reverse(reversed);
		PersistedMessageRuntime.RecipientState nonCanonical = new PersistedMessageRuntime.RecipientState(
			encoded.recipientId(),
			encoded.createdElapsedNanos(),
			encoded.perPlayerAnchorElapsedNanos(),
			encoded.projectedOccurrences(),
			encoded.decidedOccurrences(),
			reversed,
			encoded.conditionDecisions(),
			encoded.completionAtNanos(),
			encoded.completionCommitted()
		);
		check(
			nonCanonical.lockedFields().stream()
				.map(PersistedMessageRuntime.LockedField::slot)
				.toList()
				.equals(List.of(1, 4, 7))
				&& MessageRecipientStateCodec.encode(
					cue,
					MessageRecipientStateCodec.decode(cue, nonCanonical)
				).equals(encoded),
			"persisted locked fields must canonicalize shuffled input before recovery"
		);
	}

	private static void checkFinalizeHistoryRetryIsIdempotent() {
		WorldStateV4 empty = activeState();
		MessageHistoryRecord first = history(RECIPIENT_A);
		MessageHistoryRecord second = history(RECIPIENT_B);
		var committed = MessageHistoryAuthority.appendAll(empty, List.of(first, second));
		check(committed.accepted(), "restart=finalize history fixture must append");
		var retry = MessageHistoryAuthority.appendAll(
			committed.state(),
			List.of(first, second)
		);
		check(
			retry.accepted()
				&& retry.appendedRecords() == 0
				&& retry.duplicateRecords() == 2
				&& retry.state().equals(committed.state()),
			"a repeated restart=finalize materialization must not duplicate history"
		);
	}

	private static void checkTerminalRetirementReclaimsOnlyOwnedCallbacks() {
		MessageCueDefinition cue = cue();
		UUID terminalId = uuid("terminal");
		UUID survivorId = uuid("survivor");
		MessageInstance terminal = instance(cue, terminalId, RECIPIENT_A, InstanceStatus.COMPLETED);
		MessageInstance survivor = instance(cue, survivorId, RECIPIENT_B, InstanceStatus.ACTIVE);
		RuntimeState state = new RuntimeState(
			1L,
			Map.of(terminalId, terminal, survivorId, survivor),
			List.of(),
			Map.of(survivor.dedupeKey(), survivorId),
			Map.of(),
			Map.of(),
			new Ledger(2, List.of(callback(terminalId), callback(survivorId)))
		);
		var retired = MessageInstanceAuthority.retireTerminalInstances(
			state,
			Set.of(terminalId)
		);
		check(
			retired.accepted()
				&& retired.nextState().callbackLedger().capacity() == 2
				&& retired.nextState().callbackLedger().entries().size() == 1
				&& retired.nextState().callbackLedger().entries().getFirst().key()
					.instanceId().equals(survivorId),
			"terminal retirement must reclaim its callback entry and retain survivor evidence"
		);
	}

	private static MessageInstance instance(
		final MessageCueDefinition cue,
		final UUID instanceId,
		final UUID recipient,
		final InstanceStatus status
	) {
		DedupeKey dedupe = new DedupeKey(cue.id(), Set.of(recipient), Map.of());
		Map<String, TimeSpan> durations = new LinkedHashMap<>();
		Map<String, Set<UUID>> nodeTargets = new LinkedHashMap<>();
		cue.nodes().forEach(node -> {
			durations.put(node.id(), new TimeSpan(0L));
			nodeTargets.put(node.id(), Set.of(recipient));
		});
		return new MessageInstance(
			instanceId,
			cue,
			new InvocationContext(
				instanceId,
				true,
				Optional.empty(),
				Set.of(recipient),
				Optional.of(GAME_ID),
				Optional.of(PHASE_ID),
				Optional.empty(),
				Map.of(),
				1L,
				false,
				false
			),
			Map.of(),
			durations,
			Set.of(recipient),
			nodeTargets,
			dedupe,
			Optional.empty(),
			ClockMode.GAME_TIME,
			0L,
			status,
			Optional.empty(),
			0L,
			0L,
			Set.of(),
			status.terminal() ? Set.of(recipient) : Set.of(),
			Map.of(),
			status == InstanceStatus.COMPLETED,
			1L
		);
	}

	private static CallbackEntry callback(final UUID instanceId) {
		return new CallbackEntry(
			new CallbackKey(
				instanceId,
				0L,
				"callback",
				0,
				CallbackTrigger.START,
				Optional.empty()
			),
			Identifier.parse("test:callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN,
			CallbackStatus.SUCCEEDED,
			1,
			10L,
			Optional.empty()
		);
	}

	private static MessageCueDefinition cue() {
		var parsed = MessageDefinitionParser.parseMessageCue(
			CUE_ID,
			"""
			{
			  "format_version": 1,
			  "policies": {
			    "timing": {"clock": "game_time"},
			    "audience": {"target": {"source": "call_targets", "online_only": false}},
			    "lifecycle": {"restart": "continue", "attachment": "world"}
			  },
			  "nodes": [
			    {
			      "id": "callback",
			      "type": "callback",
			      "schedule": {"type": "cue_start", "offset": "0t"},
			      "function": "test:callback",
			      "execution": "as_server",
			      "location": "origin"
			    }
			  ]
			}
			"""
		);
		check(parsed.valid(), "recovery contract cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static MessageCueDefinition recoveredFinalCue() {
		var parsed = MessageDefinitionParser.parseMessageCue(
			Identifier.parse("test:recovered_final_authorization"),
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "public_notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {"text": {"parts": [{"component": {"text": "公开"}}]}}
			    },
			    {
			      "id": "private_role",
			      "type": "text",
			      "channel": "subtitle",
			      "content": {"text": {"parts": [{"component": {"text": "身份"}}]}}
			    }
			  ],
			  "static_fallback": {
			    "channel": "chat",
			    "component": {"text": "冻结最终内容"},
			    "fallback_satisfies_required": true
			  }
			}
			"""
		);
		check(parsed.valid(), "recovered-final authorization cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static WorldStateV4 activeState() {
		WorldStateV3 core = WorldStateV3.initial().beginGameInstance(
			GAME_ID,
			PHASE_ID,
			GAME_INSTANCE
		);
		return new WorldStateV4(WorldStateV4.SCHEMA_VERSION, core, List.of(), List.of());
	}

	private static MessageHistoryRecord history(final UUID viewer) {
		return new MessageHistoryRecord(
			GAME_INSTANCE,
			viewer,
			CUE_ID,
			uuid("history-instance"),
			Optional.empty(),
			20L,
			"『恢复回顾』",
			"静态最终内容",
			Map.of(),
			false
		);
	}

	private static UUID uuid(final String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void checkThrows(final Runnable action, final String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}
}
