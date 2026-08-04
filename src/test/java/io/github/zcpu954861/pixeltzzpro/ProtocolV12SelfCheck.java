package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Alignment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.CharacterSound;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ChatInterrupt;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockAnchor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Conflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Cursor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.DimensionExit;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ExternalConflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Fade;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldReservation;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Gradient;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Motion;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Obscured;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Overflow;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Pulse;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedOrigin;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedPolicies;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResourceReload;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Respawn;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ReducedMotion;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.RestoreMode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Scramble;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ScreenStart;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ScreenTimeoutAction;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Typewriter;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.ControlEvent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.FieldDeltaEvent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.OfferResult;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.WireEvent;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload.ChatUpdateMode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload.Action;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload.Surface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Protocol-v12 codec, least-privilege projection, and ordered-inbox check.
 */
public final class ProtocolV12SelfCheck {
	private static final UUID INSTANCE = UUID.fromString(
		"00000000-0000-0000-0000-000000000301"
	);
	private static final UUID SPEAKER = UUID.fromString(
		"00000000-0000-0000-0000-000000000302"
	);
	private static final UUID FINISH_INSTANCE = UUID.fromString(
		"00000000-0000-0000-0000-000000000304"
	);
	private static final Identifier CUE = Identifier.parse(
		"pixel_tzz:acceptance/visible"
	);

	private ProtocolV12SelfCheck() {
	}

	public static void main(final String[] args) {
		check(
			NetworkProtocol.CURRENT_VERSION == 12,
			"dynamic message wire requires protocol v12"
		);
		checkAllPayloadRoundTrips();
		checkStrictDocumentDecoding();
		checkComponentBounds();
		checkAggregatePlanBound();
		checkLeastPrivilegeWireBytes();
		checkStrictSequenceAndPreConsumptionBuffer();
		checkIdempotentCreateRetransmission();
		checkFinalizeCompleteness();
		checkNaturalFinishCompleteness();
		checkRefreshAndReplace();
		checkInboxBounds();
		MessageAssetProtocolSelfCheck.run();
		System.out.println("PROTOCOL_V12_SELF_CHECK=PASS");
	}

	private static void checkAllPayloadRoundTrips() {
		MessagePlaybackPlan plan = representativePlan(INSTANCE, 7L, 10L);
		MessagePlanS2CPayload planPayload = new MessagePlanS2CPayload(plan);
		MessageFieldDeltaS2CPayload delta = new MessageFieldDeltaS2CPayload(
			INSTANCE,
			7L,
			11L,
			List.of(new FieldValue(0, text("实时值甲")))
		);
		MessageControlS2CPayload control = new MessageControlS2CPayload(
			INSTANCE,
			7L,
			12L,
			15_000L,
			Action.PAUSE,
			List.of()
		);
		MessageCapabilitiesC2SPayload capabilities =
			MessageCapabilitiesC2SPayload.current(4L, ChatUpdateMode.IN_PLACE);
		MessageScreenStateC2SPayload screen = new MessageScreenStateC2SPayload(
			5L,
			Surface.FORCED_MOD_SCREEN,
			false,
			true,
			true
		);

		check(
			roundTrip(MessagePlanS2CPayload.STREAM_CODEC, planPayload)
				.equals(planPayload),
			"message plan payload round-trip failed"
		);
		check(
			roundTrip(MessageFieldDeltaS2CPayload.STREAM_CODEC, delta)
				.equals(delta),
			"message field delta round-trip failed"
		);
		check(
			roundTrip(MessageControlS2CPayload.STREAM_CODEC, control)
				.equals(control),
			"message control round-trip failed"
		);
		check(
			roundTrip(
				MessageCapabilitiesC2SPayload.STREAM_CODEC,
				capabilities
			).equals(capabilities),
			"message capabilities round-trip failed"
		);
		check(
			roundTrip(MessageScreenStateC2SPayload.STREAM_CODEC, screen)
				.equals(screen),
			"message screen state round-trip failed"
		);
		check(
			MessagePlaybackPlan.decode(plan.encode()).equals(plan),
			"inner playback plan round-trip failed"
		);
	}

	private static void checkStrictDocumentDecoding() {
		MessagePlanS2CPayload plan = new MessagePlanS2CPayload(
			representativePlan(INSTANCE, 7L, 10L)
		);
		MessageFieldDeltaS2CPayload delta = new MessageFieldDeltaS2CPayload(
			INSTANCE,
			7L,
			11L,
			List.of(new FieldValue(0, text("值")))
		);
		MessageControlS2CPayload control = new MessageControlS2CPayload(
			INSTANCE,
			7L,
			12L,
			1L,
			Action.PAUSE,
			List.of()
		);
		MessageCapabilitiesC2SPayload capabilities =
			MessageCapabilitiesC2SPayload.current(1L, ChatUpdateMode.STATIC_ONLY);
		MessageScreenStateC2SPayload screen = new MessageScreenStateC2SPayload(
			1L,
			Surface.GAMEPLAY,
			false,
			false,
			true
		);

		expectTrailingRejected(
			MessagePlanS2CPayload.STREAM_CODEC,
			plan,
			"message plan must reject inner trailing data"
		);
		expectTrailingRejected(
			MessageFieldDeltaS2CPayload.STREAM_CODEC,
			delta,
			"message delta must reject inner trailing data"
		);
		expectTrailingRejected(
			MessageControlS2CPayload.STREAM_CODEC,
			control,
			"message control must reject inner trailing data"
		);
		expectTrailingRejected(
			MessageCapabilitiesC2SPayload.STREAM_CODEC,
			capabilities,
			"message capabilities must reject inner trailing data"
		);
		expectTrailingRejected(
			MessageScreenStateC2SPayload.STREAM_CODEC,
			screen,
			"message screen state must reject inner trailing data"
		);

		expectCodecRejected(
			MessageCapabilitiesC2SPayload.STREAM_CODEC,
			wrapDocument(
				document(buffer -> {
					buffer.writeVarLong(1L);
					buffer.writeVarInt(MessagePlaybackPlan.FORMAT_VERSION);
					buffer.writeVarInt(99);
					buffer.writeBoolean(true);
					buffer.writeBoolean(true);
				})
			),
			"message capabilities must reject an unknown chat mode"
		);
		expectCodecRejected(
			MessageScreenStateC2SPayload.STREAM_CODEC,
			wrapDocument(
				document(buffer -> {
					buffer.writeVarLong(1L);
					buffer.writeVarInt(99);
					buffer.writeBoolean(false);
					buffer.writeBoolean(false);
					buffer.writeBoolean(true);
				})
			),
			"message screen state must reject an unknown surface"
		);
		expectCodecRejected(
			MessageControlS2CPayload.STREAM_CODEC,
			wrapDocument(
				document(buffer -> {
					buffer.writeUUID(INSTANCE);
					buffer.writeVarLong(7L);
					buffer.writeVarLong(12L);
					buffer.writeVarLong(1L);
					buffer.writeVarInt(99);
					buffer.writeVarInt(0);
				})
			),
			"message control must reject an unknown action"
		);
		expectRejected(
			() -> MessagePlaybackPlan.decode(unknownNodePlanDocument()),
			"message plan must reject an unknown node type"
		);
		expectRejected(
			() -> MessagePlaybackPlan.decode(unknownPolicyPlanDocument()),
			"message plan must reject an unknown resolved policy"
		);

		expectRejected(
			() -> new MessageCapabilitiesC2SPayload(
				0L,
				0,
				ChatUpdateMode.STATIC_ONLY,
				true,
				true
			),
			"message capabilities must reject an invalid plan format"
		);
		expectRejected(
			() -> new MessageScreenStateC2SPayload(
				-1L,
				Surface.GAMEPLAY,
				false,
				false,
				true
			),
			"message screen state must reject a negative sequence"
		);
		expectRejected(
			() -> new MessageFieldDeltaS2CPayload(
				INSTANCE,
				7L,
				11L,
				List.of(
					new FieldValue(0, text("甲")),
					new FieldValue(0, text("乙"))
				)
			),
			"message field delta must reject duplicate slots"
		);
		expectRejected(
			() -> new FieldValue(
				MessagePlaybackPlan.MAX_FIELD_SLOTS,
				text("越界")
			),
			"message field delta must reject an out-of-range slot"
		);
		expectRejected(
			() -> new MessageControlS2CPayload(
				INSTANCE,
				7L,
				12L,
				1L,
				Action.RESUME,
				List.of(new FieldValue(0, text("非法")))
			),
			"non-final controls must reject field values"
		);
		expectRejected(
			() -> new MessagePlaybackPlan(
				INSTANCE,
				CUE,
				7L,
				10L,
				0L,
				PlanDisposition.CREATE,
				Optional.empty(),
				new ClockAnchor(ClockKind.GAME_TICK, 0L, 0L),
				ResolvedPolicies.standard(),
				origin(),
				List.of()
			),
			"message plan must reject an empty node list"
		);
		expectRejected(
			() -> new ResolvedOrigin(
				Identifier.parse("minecraft:overworld"),
				Double.NaN,
				64.0D,
				0.0D
			),
			"message origin must reject non-finite coordinates"
		);
		expectRejected(
			() -> representativePlan(
				INSTANCE,
				7L,
				10L,
				0L,
				PlanDisposition.REFRESH,
				Optional.empty()
			),
			"a refresh plan must advance to a positive cycle"
		);
	}

	private static void checkComponentBounds() {
		ResolvedComponent normalized = ResolvedComponent.canonicalizeResolved(
			"{\"text\":\"正文\",\"color\":\"gold\"}"
		);
		check(
			normalized.canonicalJson().equals("{\"color\":\"gold\",\"text\":\"正文\"}"),
			"trusted resolved components must normalize Minecraft codec key order"
		);
		expectRejected(
			() -> text("a".repeat(MessagePlaybackPlan.MAX_COMPONENT_CHARACTERS)),
			"component UTF-16 character limit must be enforced"
		);
		expectRejected(
			() -> text("界".repeat(6_000)),
			"component UTF-8 byte limit must be enforced independently"
		);
		expectRejected(
			() -> ResolvedComponent.parseCanonical(
				"{\"score\":{\"name\":\"@s\",\"objective\":\"secret\"}}"
			),
			"unresolved score components must not cross the wire"
		);
		expectRejected(
			() -> ResolvedComponent.parseCanonical(
				"{\"text\":\"正文\",\"color\":\"gold\"}"
			),
			"non-canonical component object order must be rejected"
		);
		expectRejected(
			() -> ResolvedComponent.parseCanonical(
				"{\"text\":\"甲\",\"text\":\"乙\"}"
			),
			"duplicate component keys must be rejected"
		);
		for (String dynamicKey : List.of("score", "selector", "nbt")) {
			expectRejected(
				() -> ResolvedComponent.canonicalizeResolved(
					"{\"" + dynamicKey + "\":\"still-dynamic\"}"
				),
				"trusted canonicalization must reject unresolved " + dynamicKey + " components"
			);
		}
	}

	private static void checkAggregatePlanBound() {
		ResolvedComponent large = text("a".repeat(15_000));
		List<ResolvedSegment> segments = new ArrayList<>();
		for (int index = 0; index < 18; index++) {
			segments.add(new StaticSegment(large, SegmentTiming.none()));
		}
		expectRejected(
			() -> new MessagePlaybackPlan(
				UUID.randomUUID(),
				CUE,
				1L,
				0L,
				0L,
				PlanDisposition.CREATE,
				Optional.empty(),
				new ClockAnchor(
					ClockKind.PRESENTATION_NANOS,
					1_000L,
					1_000L
				),
				ResolvedPolicies.standard(),
				origin(),
				List.of(simpleTextNode(segments))
			),
			"whole-plan aggregate bytes must be bounded at construction"
		);
	}

	private static void checkLeastPrivilegeWireBytes() {
		byte[] planFrame = encodePayload(
			MessagePlanS2CPayload.STREAM_CODEC,
			new MessagePlanS2CPayload(representativePlan(INSTANCE, 7L, 10L))
		);
		byte[] deltaFrame = encodePayload(
			MessageFieldDeltaS2CPayload.STREAM_CODEC,
			new MessageFieldDeltaS2CPayload(
				INSTANCE,
				7L,
				11L,
				List.of(new FieldValue(0, text("仅本人可见的已解析值")))
			)
		);
		byte[] controlFrame = encodePayload(
			MessageControlS2CPayload.STREAM_CODEC,
			new MessageControlS2CPayload(
				INSTANCE,
				7L,
				12L,
				1L,
				Action.PAUSE,
				List.of()
			)
		);
		byte[] all = new byte[
			planFrame.length + deltaFrame.length + controlFrame.length
		];
		System.arraycopy(planFrame, 0, all, 0, planFrame.length);
		System.arraycopy(
			deltaFrame,
			0,
			all,
			planFrame.length,
			deltaFrame.length
		);
		System.arraycopy(
			controlFrame,
			0,
			all,
			planFrame.length + deltaFrame.length,
			controlFrame.length
		);
		String ascii = new String(all, StandardCharsets.ISO_8859_1)
			.toLowerCase(Locale.ROOT);
		for (
			String forbidden : List.of(
				"predicate",
				"callback",
				"function",
				"storage",
				"score",
				"selector",
				"nbt",
				"secret_variant"
			)
		) {
			check(
				!ascii.contains(forbidden),
				"least-privilege wire leaked forbidden server data: " + forbidden
			);
		}
	}

	private static void checkStrictSequenceAndPreConsumptionBuffer() {
		MessagePlaybackPlan plan = representativePlan(INSTANCE, 7L, 10L);
		MessageWireInbox inbox = new MessageWireInbox();
		check(
			inbox.offer(new MessagePlanS2CPayload(plan)) == OfferResult.ACCEPTED,
			"plan must enter an empty inbox"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					UUID.randomUUID(),
					7L,
					11L,
					List.of(new FieldValue(0, text("无实例")))
				)
			) == OfferResult.UNKNOWN_INSTANCE,
			"a delta cannot create an implicit stream"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					8L,
					11L,
					List.of(new FieldValue(0, text("错代")))
				)
			) == OfferResult.GENERATION_MISMATCH,
			"a cross-generation delta must be rejected"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					7L,
					12L,
					List.of(new FieldValue(0, text("跳号")))
				)
			) == OfferResult.NON_CONSECUTIVE_SEQUENCE,
			"a sequence gap must be rejected"
		);
		MessageFieldDeltaS2CPayload first = new MessageFieldDeltaS2CPayload(
			INSTANCE,
			7L,
			11L,
			List.of(new FieldValue(0, text("实时值甲")))
		);
		check(
			inbox.offer(first) == OfferResult.ACCEPTED,
			"the immediate successor delta must be accepted"
		);
		check(
			inbox.offer(first) == OfferResult.NON_CONSECUTIVE_SEQUENCE,
			"a duplicate sequence must be rejected"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					7L,
					12L,
					100L,
					Action.PAUSE,
					List.of()
				)
			) == OfferResult.ACCEPTED,
			"the immediate successor control must be accepted"
		);
		check(
			inbox.pendingEventCount() == 2
				&& inbox.drainEvents(INSTANCE).isEmpty(),
			"events must remain cached until the renderer consumes the plan"
		);
		check(
			inbox.takePlan(INSTANCE).orElseThrow().equals(plan)
				&& inbox.takePlan(INSTANCE).isEmpty(),
			"the renderer must take the original plan exactly once"
		);
		List<WireEvent> events = inbox.drainEvents(INSTANCE);
		check(
			events.size() == 2
				&& events.get(0) instanceof FieldDeltaEvent
				&& events.get(1) instanceof ControlEvent
				&& events.get(0).sequence() == 11L
				&& events.get(1).sequence() == 12L,
			"pre-consumption events must drain once in strict sequence order"
		);
		check(
			inbox.pendingEventCount() == 0 && inbox.bufferedBytes() == 0,
			"taking and draining must release the bounded inbox budget"
		);
	}

	private static void checkIdempotentCreateRetransmission() {
		MessageWireInbox inbox = new MessageWireInbox();
		check(
			inbox.offer(
				new MessagePlanS2CPayload(representativePlan(INSTANCE, 7L, 10L))
			) == OfferResult.ACCEPTED,
			"initial CREATE must establish the stream"
		);
		check(
			inbox.offer(
				new MessagePlanS2CPayload(representativePlan(INSTANCE, 7L, 11L))
			) == OfferResult.ACCEPTED,
			"an exact next-sequence CREATE retransmission must be idempotent"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					7L,
					12L,
					List.of(new FieldValue(0, text("重传后的实时值")))
				)
			) == OfferResult.ACCEPTED,
			"idempotent CREATE must consume its envelope so the next field stays consecutive"
		);
		check(
			inbox.takePlan(INSTANCE).orElseThrow().sequence() == 10L,
			"idempotent CREATE must not restart or replace the renderer's original plan"
		);
	}

	private static void checkFinalizeCompleteness() {
		MessageWireInbox inbox = new MessageWireInbox();
		inbox.offer(
			new MessagePlanS2CPayload(
				representativePlan(INSTANCE, 7L, 10L)
			)
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					7L,
					11L,
					List.of(new FieldValue(0, text("已锁定")))
				)
			) == OfferResult.ACCEPTED,
			"first field must lock"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					7L,
					12L,
					1L,
					Action.FINALIZE,
					List.of()
				)
			) == OfferResult.FINALIZE_SLOT_MISMATCH,
			"FINALIZE must not omit an unlocked slot"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					7L,
					12L,
					1L,
					Action.FINALIZE,
					List.of(new FieldValue(1, text("字段回退")))
				)
			) == OfferResult.ACCEPTED,
			"FINALIZE must carry the exact unresolved slot set"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					7L,
					13L,
					2L,
					Action.CANCEL,
					List.of()
				)
			) == OfferResult.TERMINAL_INSTANCE,
			"a terminal instance must reject later controls"
		);
	}

	private static void checkNaturalFinishCompleteness() {
		MessageWireInbox inbox = new MessageWireInbox();
		check(
			inbox.offer(
				new MessagePlanS2CPayload(
					representativePlan(FINISH_INSTANCE, 7L, 20L)
				)
			) == OfferResult.ACCEPTED,
			"natural-finish plan must establish its ordered stream"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					FINISH_INSTANCE,
					7L,
					21L,
					List.of(new FieldValue(0, text("已锁定")))
				)
			) == OfferResult.ACCEPTED,
			"natural-finish stream must accept its pre-terminal field"
		);
		MessageControlS2CPayload finish = new MessageControlS2CPayload(
			FINISH_INSTANCE,
			7L,
			22L,
			40L,
			Action.FINISH,
			List.of(new FieldValue(1, text("最终字段")))
		);
		check(
			roundTrip(MessageControlS2CPayload.STREAM_CODEC, finish).equals(finish),
			"natural FINISH control must round-trip with final fields"
		);
		check(
			inbox.offer(finish) == OfferResult.ACCEPTED,
			"natural FINISH must lock the exact unresolved field set"
		);
		inbox.takePlan(FINISH_INSTANCE).orElseThrow();
		List<WireEvent> events = inbox.drainEvents(FINISH_INSTANCE);
		check(
			events.get(events.size() - 1) instanceof ControlEvent control
				&& control.payload().action().drainsPresentation(),
			"natural FINISH must remain distinguishable from immediate FINALIZE on the client"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					FINISH_INSTANCE,
					7L,
					23L,
					41L,
					Action.CANCEL,
					List.of()
				)
			) == OfferResult.TERMINAL_INSTANCE,
			"FINISH must close the authoritative wire stream while the renderer drains locally"
		);
	}

	private static void checkRefreshAndReplace() {
		MessageWireInbox inbox = new MessageWireInbox();
		MessagePlaybackPlan create = representativePlan(INSTANCE, 7L, 10L);
		check(
			inbox.offer(new MessagePlanS2CPayload(create)) == OfferResult.ACCEPTED,
			"CREATE must establish a new wire cycle"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					7L,
					11L,
					List.of(new FieldValue(0, text("旧周期")))
				)
			) == OfferResult.ACCEPTED,
			"old cycle must buffer its event"
		);
		MessagePlaybackPlan refresh = representativePlan(
			INSTANCE,
			7L,
			30L,
			1L,
			PlanDisposition.REFRESH,
			Optional.empty()
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(refresh)) == OfferResult.ACCEPTED,
			"REFRESH must atomically replace the prior cycle"
		);
		check(
			inbox.pendingEventCount() == 0
				&& inbox.activeInstanceCount() == 1
				&& inbox.takePlan(INSTANCE).orElseThrow().equals(refresh),
			"REFRESH must release old events and expose only the new plan"
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(refresh))
				== OfferResult.STALE_CYCLE,
			"duplicate refresh cycles must be rejected"
		);
		check(
			inbox.offer(
				new MessagePlanS2CPayload(
					representativePlan(
						INSTANCE,
						8L,
						40L,
						2L,
						PlanDisposition.REFRESH,
						Optional.empty()
					)
				)
			) == OfferResult.GENERATION_MISMATCH,
			"refresh cannot cross its immutable definition generation"
		);
		check(
			inbox.retire(INSTANCE)
				&& inbox.activeInstanceCount() == 0
				&& inbox.bufferedBytes() == 0,
			"a naturally completed renderer may retain only its bounded cycle stamp"
		);
		MessagePlaybackPlan resumedRefresh = representativePlan(
			INSTANCE,
			7L,
			50L,
			2L,
			PlanDisposition.REFRESH,
			Optional.empty()
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(resumedRefresh))
				== OfferResult.ACCEPTED,
			"a newer refresh must resume from a retired cycle stamp"
		);

		UUID replacementId = UUID.fromString(
			"00000000-0000-0000-0000-000000000303"
		);
		MessagePlaybackPlan replacement = representativePlan(
			replacementId,
			9L,
			0L,
			0L,
			PlanDisposition.REPLACE,
			Optional.of(INSTANCE)
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(replacement))
				== OfferResult.ACCEPTED,
			"REPLACE must establish its new instance"
		);
		check(
			!inbox.contains(INSTANCE)
				&& inbox.contains(replacementId)
				&& inbox.activeInstanceCount() == 1,
			"REPLACE must remove the replaced wire stream atomically"
		);
	}

	private static void checkInboxBounds() {
		MessageWireInbox inbox = new MessageWireInbox(
			1,
			2,
			2,
			MessagePlaybackPlan.MAX_PLAN_BYTES
		);
		check(
			inbox.offer(
				new MessagePlanS2CPayload(
					representativePlan(INSTANCE, 7L, 10L)
				)
			) == OfferResult.ACCEPTED,
			"bounded inbox must accept its first plan"
		);
		check(
			inbox.offer(
				new MessagePlanS2CPayload(
					representativePlan(UUID.randomUUID(), 7L, 10L)
				)
			) == OfferResult.ACTIVE_INSTANCE_LIMIT,
			"bounded inbox must cap active instances"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					7L,
					11L,
					List.of(new FieldValue(0, text("甲")))
				)
			) == OfferResult.ACCEPTED,
			"bounded inbox must accept its first event"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					7L,
					12L,
					1L,
					Action.PAUSE,
					List.of()
				)
			) == OfferResult.ACCEPTED,
			"bounded inbox must accept its second event"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					7L,
					13L,
					2L,
					Action.RESUME,
					List.of()
				)
			) == OfferResult.INSTANCE_EVENT_LIMIT,
			"bounded inbox must reject an event beyond the per-instance cap"
		);
		check(
			inbox.remove(INSTANCE)
				&& inbox.activeInstanceCount() == 0
				&& inbox.pendingEventCount() == 0
				&& inbox.bufferedBytes() == 0,
			"removing an unread stream must release every bounded counter"
		);
	}

	private static MessagePlaybackPlan representativePlan(
		final UUID instanceId,
		final long generation,
		final long sequence
	) {
		return representativePlan(
			instanceId,
			generation,
			sequence,
			0L,
			PlanDisposition.CREATE,
			Optional.empty()
		);
	}

	private static MessagePlaybackPlan representativePlan(
		final UUID instanceId,
		final long generation,
		final long sequence,
		final long cycle,
		final PlanDisposition disposition,
		final Optional<UUID> replacedInstanceId
	) {
		List<ResolvedSegment> segments = List.of(
			new StaticSegment(text("已授权正文"), SegmentTiming.none()),
			new FieldSlotSegment(
				0,
				Optional.of(text("字段甲回退")),
				new FieldReservation(
					OptionalInt.of(16),
					OptionalInt.of(96),
					OptionalInt.of(2)
				),
				new SegmentTiming(10_000_000L, 20_000_000L, true)
			),
			new FieldSlotSegment(
				1,
				Optional.of(text("字段乙回退")),
				FieldReservation.none(),
				SegmentTiming.none()
			),
			new FieldSlotSegment(
				0,
				Optional.of(text("字段甲回退")),
				FieldReservation.none(),
				SegmentTiming.none()
			)
		);
		ResolvedEffect effect = new ResolvedEffect(
			Optional.of(
				new Typewriter(
					25_000_000L,
					new Cursor(true, "█", "#FFFFFF", true, 500_000_000L),
					Optional.of("#80FFFF"),
					3,
					RestoreMode.GRADIENT
				)
			),
			Optional.of(new Fade(100_000_000L, 1_000_000_000L, 200_000_000L)),
			Optional.of(new Scramble(20_000_000L, "?#*")),
			Optional.of(new Gradient("#80FFFF", "#FFFFFF", 300_000_000L)),
			Optional.of(new Motion(2, -1, 0.95F, 120_000_000L)),
			Optional.of(new Pulse(1.05F, 160_000_000L)),
			Optional.of(
				new CharacterSound(
					Identifier.parse("minecraft:ui.button.click"),
					SoundCategory.UI,
					2,
					true,
					true,
					true,
					0.8F,
					1.0F,
					0.1F,
					SoundAttachment.PLAYER
				)
			),
			128
		);
		ResolvedTextNode textNode = new ResolvedTextNode(
			0,
			0L,
			10,
			Conflict.QUEUE,
			Channel.SUBTITLE,
			segments,
			effect,
			new ResolvedLayout(
				Alignment.CENTER,
				0,
				4,
				OptionalInt.of(320),
				OptionalInt.of(3),
				1.1F,
				Overflow.WRAP
			),
			ResolvedDuration.contentDriven(1_500_000_000L),
			new ResolvedSpeaker(
				SpeakerKind.PLAYER,
				Optional.of(SPEAKER),
				Optional.of("PlayerB")
			)
		);
		ResolvedSoundNode soundNode = new ResolvedSoundNode(
			1,
			100_000_000L,
			10,
			Conflict.PARALLEL,
			Identifier.parse("minecraft:block.note_block.pling"),
			SoundCategory.UI,
			0.8F,
			1.2F,
			SoundAttachment.PLAYER
		);
		return new MessagePlaybackPlan(
			instanceId,
			CUE,
			generation,
			sequence,
			cycle,
			disposition,
			replacedInstanceId,
			new ClockAnchor(
				ClockKind.PRESENTATION_NANOS,
				10_000L,
				20_000L
			),
			ResolvedPolicies.standard(),
			origin(),
			List.of(textNode, soundNode)
		);
	}

	private static ResolvedOrigin origin() {
		return new ResolvedOrigin(
			Identifier.parse("minecraft:overworld"),
			12.5D,
			64.0D,
			-8.25D
		);
	}

	private static ResolvedTextNode simpleTextNode(
		final List<ResolvedSegment> segments
	) {
		return new ResolvedTextNode(
			0,
			0L,
			0,
			Conflict.QUEUE,
			Channel.CHAT,
			segments,
			ResolvedEffect.none(),
			ResolvedLayout.standard(),
			ResolvedDuration.contentDriven(0L),
			ResolvedSpeaker.system()
		);
	}

	private static ResolvedComponent text(final String value) {
		return ResolvedComponent.parseCanonical("{\"text\":\"" + value + "\"}");
	}

	private static byte[] unknownNodePlanDocument() {
		return document(buffer -> {
			buffer.writeVarInt(MessagePlaybackPlan.FORMAT_VERSION);
			buffer.writeUUID(INSTANCE);
			buffer.writeUtf(CUE.toString(), 256);
			buffer.writeVarLong(7L);
			buffer.writeVarLong(10L);
			buffer.writeVarLong(0L);
			buffer.writeVarInt(PlanDisposition.CREATE.ordinal());
			buffer.writeBoolean(false);
			buffer.writeVarInt(ClockKind.PRESENTATION_NANOS.ordinal());
			buffer.writeVarLong(10_000L);
			buffer.writeVarLong(20_000L);
			buffer.writeVarInt(ChatInterrupt.FINALIZE.ordinal());
			for (Channel channel : Channel.values()) {
				buffer.writeVarInt(
					(channel == Channel.CHAT
						? ScreenStart.IMMEDIATE
						: ScreenStart.WAIT_GAMEPLAY).ordinal()
				);
			}
			for (Channel channel : Channel.values()) {
				buffer.writeVarInt(
					(channel == Channel.CHAT
						? Obscured.CONTINUE
						: Obscured.PAUSE).ordinal()
				);
			}
			buffer.writeBoolean(false);
			buffer.writeVarInt(ScreenTimeoutAction.FINALIZE.ordinal());
			buffer.writeBoolean(false);
			buffer.writeVarInt(ExternalConflict.YIELD.ordinal());
			buffer.writeVarInt(ResourceReload.FINISH.ordinal());
			buffer.writeVarInt(ReducedMotion.SIMPLIFIED.ordinal());
			buffer.writeBoolean(false);
			buffer.writeVarInt(Respawn.CONTINUE.ordinal());
			buffer.writeVarInt(DimensionExit.CONTINUE.ordinal());
			buffer.writeUtf("minecraft:overworld", 256);
			buffer.writeDouble(0.0D);
			buffer.writeDouble(64.0D);
			buffer.writeDouble(0.0D);
			buffer.writeVarInt(1);
			buffer.writeByte(99);
			buffer.writeVarInt(0);
			buffer.writeVarLong(0L);
			buffer.writeInt(0);
			buffer.writeVarInt(Conflict.QUEUE.ordinal());
		});
	}

	private static byte[] unknownPolicyPlanDocument() {
		return document(buffer -> {
			buffer.writeVarInt(MessagePlaybackPlan.FORMAT_VERSION);
			buffer.writeUUID(INSTANCE);
			buffer.writeUtf(CUE.toString(), 256);
			buffer.writeVarLong(7L);
			buffer.writeVarLong(10L);
			buffer.writeVarLong(0L);
			buffer.writeVarInt(PlanDisposition.CREATE.ordinal());
			buffer.writeBoolean(false);
			buffer.writeVarInt(ClockKind.PRESENTATION_NANOS.ordinal());
			buffer.writeVarLong(10_000L);
			buffer.writeVarLong(20_000L);
			buffer.writeVarInt(99);
		});
	}

	private static byte[] document(final Consumer<FriendlyByteBuf> writer) {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			writer.accept(buffer);
			byte[] result = new byte[buffer.readableBytes()];
			buffer.getBytes(buffer.readerIndex(), result);
			return result;
		} finally {
			buffer.release();
		}
	}

	private static byte[] wrapDocument(final byte[] value) {
		return document(buffer -> {
			buffer.writeVarInt(value.length);
			buffer.writeBytes(value);
		});
	}

	private static byte[] withTrailingDocument(final byte[] payload) {
		FriendlyByteBuf input = new FriendlyByteBuf(
			Unpooled.wrappedBuffer(payload)
		);
		try {
			int length = input.readVarInt();
			check(
				length >= 0 && length == input.readableBytes(),
				"test payload must contain one length-delimited document"
			);
			byte[] original = new byte[length];
			input.readBytes(original);
			byte[] tailed = Arrays.copyOf(original, original.length + 1);
			tailed[tailed.length - 1] = 0x5A;
			return wrapDocument(tailed);
		} finally {
			input.release();
		}
	}

	private static <T> void expectTrailingRejected(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final T value,
		final String message
	) {
		expectCodecRejected(
			codec,
			withTrailingDocument(encodePayload(codec, value)),
			message
		);
	}

	private static <T> byte[] encodePayload(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final T value
	) {
		ByteBuf storage = Unpooled.buffer();
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				storage,
				RegistryAccess.EMPTY
			);
			codec.encode(buffer, value);
			byte[] result = new byte[buffer.readableBytes()];
			buffer.getBytes(buffer.readerIndex(), result);
			return result;
		} finally {
			storage.release();
		}
	}

	private static <T> T decodePayload(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final byte[] encoded
	) {
		ByteBuf storage = Unpooled.wrappedBuffer(encoded);
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				storage,
				RegistryAccess.EMPTY
			);
			T decoded = codec.decode(buffer);
			check(
				!buffer.isReadable(),
				"payload decoder must consume its complete frame"
			);
			return decoded;
		} finally {
			storage.release();
		}
	}

	private static <T> T roundTrip(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final T value
	) {
		return decodePayload(codec, encodePayload(codec, value));
	}

	private static <T> void expectCodecRejected(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final byte[] encoded,
		final String message
	) {
		expectRejected(() -> decodePayload(codec, encoded), message);
	}

	private static void expectRejected(
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (RuntimeException expected) {
			// Expected trust-boundary rejection.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
