package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockAnchor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Conflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldReservation;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedOrigin;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedPolicies;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.OfferResult;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler.NodeOccurrence;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageReconnectProjection.Projection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Fresh-connection regression checks backed by the strict client wire inbox.
 *
 * <p>This deliberately verifies the production helper and also requires the server runtime to call
 * it. A standalone green helper is not evidence that reconnect packets are safe.</p>
 */
public final class MessageReconnectProjectionSelfCheck {
	private static final UUID INSTANCE = UUID.fromString(
		"00000000-0000-0000-0000-0000000003c0"
	);
	private static final long GENERATION = 11L;
	private static final String PUBLIC_NODE = "public_status";
	private static final String SECRET_NODE = "secret_identity";

	private MessageReconnectProjectionSelfCheck() {
	}

	public static void main(final String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkRevokedNodeAndLockedFieldNeverReachNewConnection();
		checkFreshPlanRebasesRefreshDisposition();
		checkFreshPlanRebasesReplaceDisposition();
		checkRepeatedFreshProjectionIsIndependent();
		checkFullyRevokedProjectionFailsClosed();
		checkExactOccurrenceAllowlist();
		checkAmbiguousOccurrenceOwnershipFailsClosed();
		checkProjectionRecordEnforcesConnectionContract();
		checkFinalOnlyPlannerExcludesHistoricalSideEffects();
		checkRuntimeRoutesReconnectThroughProductionProjection();
		checkTerminalWireGuards();

		System.out.println("MESSAGE_RECONNECT_PROJECTION_SELF_CHECK=PASS");
	}

	private static void checkRevokedNodeAndLockedFieldNeverReachNewConnection() {
		ResolvedTextNode publicNode = node(0, 0, "public");
		ResolvedTextNode secretNode = node(1, 1, "secret");
		MessagePlaybackPlan logical = plan(
			10L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(publicNode, secretNode)
		);
		Map<NodeOccurrence, List<ResolvedNode>> ownership = ownership(
			publicNode,
			secretNode
		);
		Map<Integer, ResolvedComponent> locked = new LinkedHashMap<>();
		locked.put(0, text("public-locked"));
		locked.put(1, text("secret-locked"));

		MessageWireInbox oldConnection = new MessageWireInbox();
		check(
			oldConnection.offer(new MessagePlanS2CPayload(logical))
				== OfferResult.ACCEPTED,
			"the old physical connection must establish the full historical plan"
		);
		check(
			oldConnection.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					11L,
					List.of(
						new FieldValue(0, locked.get(0)),
						new FieldValue(1, locked.get(1))
					)
				)
			) == OfferResult.ACCEPTED,
			"the old connection setup must prove that the secret slot existed previously"
		);

		Projection reconnect = MessageReconnectProjection.project(
			logical,
			ownership,
			Set.of(PUBLIC_NODE),
			locked,
			40L
		).orElseThrow();
		check(
			reconnect.plan().sequence() == 40L
				&& reconnect.plan().nodes().equals(List.of(publicNode)),
			"fresh PLAN must contain only the currently authorized node"
		);
		check(
			reconnect.declaredSlots().equals(Set.of(0))
				&& reconnect.lockedSnapshot().equals(
					List.of(new FieldValue(0, text("public-locked")))
				),
			"fresh locked snapshot must contain only slots declared by the filtered PLAN"
		);
		check(
			logical.nodes().size() == 2 && locked.size() == 2,
			"connection projection must not erase authoritative logical history"
		);

		MessageWireInbox fresh = new MessageWireInbox();
		check(
			fresh.offer(new MessagePlanS2CPayload(reconnect.plan()))
				== OfferResult.ACCEPTED,
			"the new client inbox must accept the re-authorized PLAN"
		);
		long sequence = reconnect.nextSequence();
		check(
			fresh.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					sequence,
					reconnect.lockedSnapshot()
				)
			) == OfferResult.ACCEPTED,
			"locked snapshot must immediately follow the resent PLAN"
		);
		ResolvedTextNode appended = node(2, 2, "authorized-later");
		sequence = MessageReconnectProjection.nextSequence(sequence);
		check(
			fresh.offer(
				new MessageNodeAppendS2CPayload(
					INSTANCE,
					GENERATION,
					sequence,
					List.of(appended)
				)
			) == OfferResult.ACCEPTED,
			"APPEND must use the immediate successor of the reconnect snapshot"
		);
		sequence = MessageReconnectProjection.nextSequence(sequence);
		check(
			fresh.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					GENERATION,
					sequence,
					0L,
					MessageControlS2CPayload.Action.FINALIZE,
					List.of(new FieldValue(2, text("authorized-final")))
				)
			) == OfferResult.ACCEPTED,
			"FINALIZE must remain consecutive after PLAN, FieldDelta, and APPEND"
		);
	}

	private static void checkFreshPlanRebasesRefreshDisposition() {
		ResolvedTextNode publicNode = node(0, 0, "refresh-public");
		MessagePlaybackPlan refreshed = plan(
			70L,
			3L,
			PlanDisposition.REFRESH,
			Optional.empty(),
			List.of(publicNode)
		);
		Projection reconnect = MessageReconnectProjection.project(
			refreshed,
			Map.of(new NodeOccurrence(PUBLIC_NODE, 0), List.of(publicNode)),
			Set.of(PUBLIC_NODE),
			Map.of(),
			80L
		).orElseThrow();
		check(
			reconnect.plan().disposition() == PlanDisposition.CREATE
				&& reconnect.plan().replacedInstanceId().isEmpty(),
			"a new inbox cannot receive the old connection's REFRESH/REPLACE relation"
		);
		check(
			new MessageWireInbox().offer(new MessagePlanS2CPayload(reconnect.plan()))
				== OfferResult.ACCEPTED,
			"rebased refresh must be a standalone plan accepted by a fresh inbox"
		);
	}

	private static void checkFreshPlanRebasesReplaceDisposition() {
		ResolvedTextNode publicNode = node(0, 0, "replace-public");
		MessagePlaybackPlan replaced = plan(
			71L,
			0L,
			PlanDisposition.REPLACE,
			Optional.of(
				UUID.fromString("00000000-0000-0000-0000-0000000003bf")
			),
			List.of(publicNode)
		);
		Projection reconnect = MessageReconnectProjection.project(
			replaced,
			Map.of(new NodeOccurrence(PUBLIC_NODE, 0), List.of(publicNode)),
			Set.of(PUBLIC_NODE),
			Map.of(),
			81L
		).orElseThrow();
		check(
			reconnect.plan().disposition() == PlanDisposition.CREATE
				&& reconnect.plan().replacedInstanceId().isEmpty(),
			"a fresh connection must also discard an old REPLACE relation"
		);
		check(
			new MessageWireInbox().offer(new MessagePlanS2CPayload(reconnect.plan()))
				== OfferResult.ACCEPTED,
			"rebased replacement must be a standalone plan accepted by a fresh inbox"
		);
	}

	private static void checkRepeatedFreshProjectionIsIndependent() {
		ResolvedTextNode publicNode = node(0, 0, "repeat-public");
		ResolvedTextNode secretNode = node(1, 1, "repeat-secret");
		MessagePlaybackPlan logical = plan(
			4L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(publicNode, secretNode)
		);
		Map<NodeOccurrence, List<ResolvedNode>> ownership = ownership(
			publicNode,
			secretNode
		);
		Map<Long, String> connectionAuthority = Map.of(
			100L,
			PUBLIC_NODE,
			200L,
			SECRET_NODE
		);
		for (Map.Entry<Long, String> connection : connectionAuthority.entrySet()) {
			long sequence = connection.getKey();
			String authorizedNode = connection.getValue();
			ResolvedTextNode expectedNode = authorizedNode.equals(PUBLIC_NODE)
				? publicNode
				: secretNode;
			int expectedSlot = authorizedNode.equals(PUBLIC_NODE) ? 0 : 1;
			Projection reconnect = MessageReconnectProjection.project(
				logical,
				ownership,
				Set.of(authorizedNode),
				Map.of(0, text("public"), 1, text("secret")),
				sequence
			).orElseThrow();
			MessageWireInbox fresh = new MessageWireInbox();
			check(
				fresh.offer(new MessagePlanS2CPayload(reconnect.plan()))
					== OfferResult.ACCEPTED,
				"every physical connection must establish an independent sequence space"
			);
			check(
				reconnect.plan().nodes().equals(List.of(expectedNode))
					&& reconnect.declaredSlots().equals(Set.of(expectedSlot))
					&& reconnect.lockedSnapshot().equals(
						List.of(
							new FieldValue(
								expectedSlot,
								text(expectedSlot == 0 ? "public" : "secret")
							)
						)
					),
				"every physical connection must re-evaluate current node and slot authority"
			);
		}
	}

	private static void checkFullyRevokedProjectionFailsClosed() {
		ResolvedTextNode secretNode = node(0, 0, "only-secret");
		MessagePlaybackPlan logical = plan(
			1L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(secretNode)
		);
		check(
			MessageReconnectProjection.project(
				logical,
				Map.of(new NodeOccurrence(SECRET_NODE, 0), List.of(secretNode)),
				Set.of(),
				Map.of(0, text("must-not-leak")),
				2L
			).isEmpty(),
			"a fully revoked recipient must receive no fresh PLAN"
		);
	}

	private static void checkExactOccurrenceAllowlist() {
		ResolvedTextNode first = node(0, 0, "repeat-zero");
		ResolvedTextNode second = node(1, 1, "repeat-one");
		MessagePlaybackPlan logical = plan(
			5L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(first, second)
		);
		NodeOccurrence occurrenceZero = new NodeOccurrence("repeated", 0);
		NodeOccurrence occurrenceOne = new NodeOccurrence("repeated", 1);
		Projection projected = MessageReconnectProjection.project(
			logical,
			Map.of(
				occurrenceZero,
				List.of(first),
				occurrenceOne,
				List.of(second)
			),
			Set.of("repeated"),
			Set.of(occurrenceOne),
			Map.of(0, text("zero"), 1, text("one")),
			6L
		).orElseThrow();
		check(
			projected.plan().nodes().equals(List.of(second))
				&& projected.declaredSlots().equals(Set.of(1))
				&& projected.lockedSnapshot().equals(
					List.of(new FieldValue(1, text("one")))
				),
			"a final-only allowlist must select an exact repeat occurrence, not every "
				+ "historical occurrence sharing its node id"
		);
	}

	private static void checkAmbiguousOccurrenceOwnershipFailsClosed() {
		ResolvedTextNode shared = node(0, 0, "shared-ordinal");
		MessagePlaybackPlan logical = plan(
			7L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(shared)
		);
		NodeOccurrence first = new NodeOccurrence("repeated", 0);
		NodeOccurrence second = new NodeOccurrence("repeated", 1);
		boolean rejected = false;
		try {
			MessageReconnectProjection.project(
				logical,
				Map.of(first, List.of(shared), second, List.of(shared)),
				Set.of("repeated"),
				Set.of(second),
				Map.of(0, text("must-not-cross-occurrences")),
				8L
			);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		check(
			rejected,
			"one wire ordinal cannot be owned by two repeat occurrences of the same node; "
				+ "otherwise an exact final-only allowlist could select historical content"
		);
	}

	private static void checkProjectionRecordEnforcesConnectionContract() {
		ResolvedTextNode node = node(0, 0, "record-contract");
		MessagePlaybackPlan standalone = plan(
			9L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(node)
		);
		boolean slotMismatchRejected = false;
		try {
			new Projection(standalone, Set.of(), List.of());
		} catch (IllegalArgumentException expected) {
			slotMismatchRejected = true;
		}
		check(
			slotMismatchRejected,
			"Projection must not claim fewer field slots than its PLAN declares"
		);

		boolean duplicateLockedSlotRejected = false;
		try {
			new Projection(
				standalone,
				Set.of(0),
				List.of(
					new FieldValue(0, text("first")),
					new FieldValue(0, text("second"))
				)
			);
		} catch (IllegalArgumentException expected) {
			duplicateLockedSlotRejected = true;
		}
		check(
			duplicateLockedSlotRejected,
			"Projection must not carry two locked values for one connection slot"
		);
	}

	private static void checkFinalOnlyPlannerExcludesHistoricalSideEffects() {
		MessageCueDefinition cue = parseFinalOnlyCue();
		List<NodeOccurrence> allowed = MessageFinalProjectionPlanner.plan(
			cue,
			INSTANCE,
			Set.of(INSTANCE),
			Map.of(),
			Map.of(),
			Set.of(),
			Set.of()
		).orderedOccurrences();
		check(
			allowed.equals(List.of(new NodeOccurrence("visible_text", 0))),
			"the final planner must authorize visible Text only, excluding sound and callback"
		);

		ResolvedTextNode visible = node(0, 0, "visible-final");
		ResolvedSoundNode historicalSound = soundNode(1);
		MessagePlaybackPlan logical = plan(
			20L,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
			List.of(visible, historicalSound)
		);
		Map<NodeOccurrence, List<ResolvedNode>> ownership = Map.of(
			new NodeOccurrence("visible_text", 0),
			List.of(visible),
			new NodeOccurrence("historical_sound", 0),
			List.of(historicalSound),
			new NodeOccurrence("historical_callback", 0),
			List.of()
		);
		Projection finalOnly = MessageReconnectProjection.project(
			logical,
			ownership,
			Set.of("visible_text", "historical_sound", "historical_callback"),
			Set.copyOf(allowed),
			Map.of(0, text("visible-locked")),
			21L
		).orElseThrow();
		check(
			finalOnly.plan().nodes().equals(List.of(visible))
				&& finalOnly.plan().nodes().stream().noneMatch(ResolvedSoundNode.class::isInstance),
			"final-only reconnect must not replay a historical sound even when its node id "
				+ "remains audience-authorized"
		);
		check(
			MessageReconnectProjection.project(
				logical,
				ownership,
				Set.of("visible_text", "historical_sound", "historical_callback"),
				Set.of(),
				Map.of(0, text("must-not-leak")),
				22L
			).isEmpty(),
			"an empty final occurrence allowlist must produce Optional.empty even when "
				+ "historical nodes remain authorized"
		);
	}

	private static void checkRuntimeRoutesReconnectThroughProductionProjection()
		throws IOException {
		String source = Files.readString(runtimeSource());
		check(
			source.contains("MessageReconnectProjection.project("),
			"MessageServerRuntime must route fresh-connection PLAN projection through "
				+ "MessageReconnectProjection; a green isolated helper is not a production fix"
		);
	}

	private static void checkTerminalWireGuards() throws IOException {
		String source = Files.readString(runtimeSource());
		checkTerminalGuard(
			methodBody(source, "final Optional<Long> exactStartOffset"),
			"compileDueOccurrence",
			"stream.decidedOccurrences.add"
		);
		checkTerminalGuard(
			methodBody(source, "private static boolean sendNodeAppend("),
			"sendNodeAppend",
			"ServerPlayNetworking.send"
		);
		checkTerminalGuard(
			methodBody(source, "private static void captureStreamDueFields("),
			"captureStreamDueFields",
			"stream.lockedSlots.add"
		);
		checkTerminalGuard(
			methodBody(
				source,
				"final Optional<Set<NodeOccurrence>> standaloneOccurrences"
			),
			"sendPlan",
			"ServerPlayNetworking.send"
		);
	}

	private static void checkTerminalGuard(
		final String body,
		final String method,
		final String firstForbiddenWork
	) {
		int terminal = body.indexOf("stream.terminal");
		int connectionTerminal = body.indexOf("stream.connectionProjectionTerminal");
		int work = body.indexOf(firstForbiddenWork);
		check(
			terminal >= 0 && connectionTerminal >= 0,
			method + " must reject both logical-terminal and connection-terminal streams"
		);
		check(
			work < 0 || (terminal < work && connectionTerminal < work),
			method + " must apply its terminal guard before " + firstForbiddenWork
		);
	}

	private static String methodBody(final String source, final String marker) {
		int markerAt = source.indexOf(marker);
		check(markerAt >= 0, "runtime source is missing method marker " + marker);
		int open = source.indexOf('{', markerAt);
		check(open >= 0, "runtime method marker has no body " + marker);
		int depth = 0;
		for (int index = open; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(open + 1, index);
				}
			}
		}
		throw new IllegalStateException("runtime method body is unbalanced for " + marker);
	}

	private static Path runtimeSource() {
		return Path.of(
			"src",
			"main",
			"java",
			"io",
			"github",
			"zcpu954861",
			"pixeltzzpro",
			"server",
			"MessageServerRuntime.java"
		);
	}

	private static MessageCueDefinition parseFinalOnlyCue() {
		MessageDefinitionParser.ParseResult<MessageCueDefinition> parsed =
			MessageDefinitionParser.parseMessageCue(
				Identifier.parse("pixel_tzz:self_check/final_only_reconnect"),
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
				      "id": "visible_text",
				      "type": "text",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "channel": "subtitle",
				      "text": {"parts": [{"component": {"text": "可见正文"}}]}
				    },
				    {
				      "id": "historical_sound",
				      "type": "sound",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "sound": "minecraft:ui.button.click",
				      "category": "ui",
				      "volume": 1.0,
				      "pitch": 1.0
				    },
				    {
				      "id": "historical_callback",
				      "type": "callback",
				      "schedule": {"type": "cue_start", "offset": "0t"},
				      "function": "pixel_tzz:self_check/final_only_callback",
				      "execution": "as_server",
				      "location": "origin"
				    }
				  ]
				}
				"""
			);
		check(
			parsed.valid(),
			"final-only reconnect cue must parse: " + parsed.issues()
		);
		return parsed.value().orElseThrow();
	}

	private static Map<NodeOccurrence, List<ResolvedNode>> ownership(
		final ResolvedNode publicNode,
		final ResolvedNode secretNode
	) {
		Map<NodeOccurrence, List<ResolvedNode>> result = new LinkedHashMap<>();
		result.put(new NodeOccurrence(PUBLIC_NODE, 0), List.of(publicNode));
		result.put(new NodeOccurrence(SECRET_NODE, 0), List.of(secretNode));
		return Map.copyOf(result);
	}

	private static MessagePlaybackPlan plan(
		final long sequence,
		final long cycle,
		final PlanDisposition disposition,
		final Optional<UUID> replaced,
		final List<ResolvedNode> nodes
	) {
		return new MessagePlaybackPlan(
			INSTANCE,
			Identifier.parse("pixel_tzz:self_check/reconnect_projection"),
			GENERATION,
			sequence,
			cycle,
			disposition,
			replaced,
			new ClockAnchor(ClockKind.PRESENTATION_NANOS, 0L, 0L),
			ResolvedPolicies.standard(),
			new ResolvedOrigin(
				Identifier.parse("minecraft:overworld"),
				0.0D,
				64.0D,
				0.0D
			),
			nodes
		);
	}

	private static ResolvedTextNode node(
		final int ordinal,
		final int slot,
		final String label
	) {
		return new ResolvedTextNode(
			ordinal,
			ordinal * 1_000_000L,
			0,
			Conflict.QUEUE,
			Channel.SUBTITLE,
			List.of(
				new StaticSegment(text(label), SegmentTiming.none()),
				new FieldSlotSegment(
					slot,
					Optional.of(text("fallback-" + slot)),
					FieldReservation.none(),
					SegmentTiming.none()
				)
			),
			ResolvedEffect.none(),
			ResolvedLayout.standard(),
			ResolvedDuration.contentDriven(0L),
			ResolvedSpeaker.system()
		);
	}

	private static ResolvedSoundNode soundNode(final int ordinal) {
		return new ResolvedSoundNode(
			ordinal,
			ordinal * 1_000_000L,
			0,
			Conflict.QUEUE,
			Identifier.parse("minecraft:ui.button.click"),
			SoundCategory.UI,
			1.0F,
			1.0F,
			SoundAttachment.PLAYER
		);
	}

	private static ResolvedComponent text(final String value) {
		return ResolvedComponent.parseCanonical("{\"text\":\"" + value + "\"}");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
