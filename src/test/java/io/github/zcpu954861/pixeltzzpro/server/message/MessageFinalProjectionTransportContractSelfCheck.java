package io.github.zcpu954861.pixeltzzpro.server.message;

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
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.OfferResult;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload.Action;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalProjectionTransportContract.DeliveryResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalProjectionTransportContract.Operation;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageFinalProjectionTransportContract.State;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure JVM transport-order checks backed by the strict client wire inbox. */
public final class MessageFinalProjectionTransportContractSelfCheck {
	private static final UUID INSTANCE = UUID.fromString(
		"00000000-0000-0000-0000-0000000003bf"
	);
	private static final long GENERATION = 7L;

	private MessageFinalProjectionTransportContractSelfCheck() {
	}

	public static void main(final String[] args) {
		checkEstablishedAppendThenFinalize();
		checkFreshPlanSnapshotThenFinalize();
		checkAppendFailurePreservesEstablishment();
		checkEstablishedWithoutAdditionsFinalizesDirectly();
		checkFinalizeIsConnectionLocalAndExactlyOnce();
		checkAuthorizationRefreshRetractsRevokedNodes();
		checkCancelClosesOnlyTheCurrentPhysicalConnection();
		checkRuntimeTerminalWiring();

		System.out.println("MESSAGE_FINAL_PROJECTION_TRANSPORT_CONTRACT_SELF_CHECK=PASS");
	}

	private static void checkEstablishedAppendThenFinalize() {
		State state = MessageFinalProjectionTransportContract.begin(true, true, true);
		checkEquals(
			List.of(Operation.APPEND, Operation.FINALIZE),
			state.successfulPath(),
			"an established connection must append and finalize without PLAN or snapshot"
		);

		MessageWireInbox inbox = new MessageWireInbox();
		MessagePlaybackPlan existing = plan(10L, List.of(node(0, 0, "existing")));
		check(
			inbox.offer(new MessagePlanS2CPayload(existing)) == OfferResult.ACCEPTED,
			"the existing connection plan must be accepted once"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					11L,
					List.of(new FieldValue(0, text("locked-existing")))
				)
			) == OfferResult.ACCEPTED,
			"the existing field must be locked before final projection"
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(existing))
				== OfferResult.DUPLICATE_INSTANCE,
			"re-sending CREATE on the same connection must be observably invalid"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					12L,
					List.of(new FieldValue(0, text("duplicate-locked")))
				)
			) == OfferResult.FIELD_ALREADY_LOCKED,
			"an established connection must not receive a full locked snapshot again"
		);

		ResolvedNode addition = node(1, 1, "final-addition");
		check(
			inbox.offer(
				new MessageNodeAppendS2CPayload(
					INSTANCE,
					GENERATION,
					12L,
					List.of(addition)
				)
			) == OfferResult.ACCEPTED,
			"the final addition must use the next APPEND sequence"
		);
		state = state.record(Operation.APPEND, DeliveryResult.SENT);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					GENERATION,
					13L,
					0L,
					Action.FINALIZE,
					List.of(new FieldValue(1, text("final-value")))
				)
			) == OfferResult.ACCEPTED,
			"FINALIZE must immediately follow APPEND and resolve only the new slot"
		);
		state = state.record(Operation.FINALIZE, DeliveryResult.SENT);
		check(state.terminal(), "a delivered FINALIZE must retire the transport contract");
	}

	private static void checkFreshPlanSnapshotThenFinalize() {
		State state = MessageFinalProjectionTransportContract.begin(false, true, true);
		checkEquals(
			List.of(
				Operation.PLAN,
				Operation.LOCKED_SNAPSHOT,
				Operation.FINALIZE
			),
			state.successfulPath(),
			"a fresh connection must receive one complete plan, snapshot, then FINALIZE"
		);

		MessageWireInbox inbox = new MessageWireInbox();
		MessagePlaybackPlan complete = plan(
			20L,
			List.of(node(0, 0, "existing"), node(1, 1, "final-addition"))
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(complete)) == OfferResult.ACCEPTED,
			"a fresh inbox must accept the complete final PLAN"
		);
		state = state.record(Operation.PLAN, DeliveryResult.SENT);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					21L,
					List.of(new FieldValue(0, text("recovered-locked")))
				)
			) == OfferResult.ACCEPTED,
			"the fresh connection must recover prior locked values after PLAN"
		);
		state = state.record(Operation.LOCKED_SNAPSHOT, DeliveryResult.SENT);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					GENERATION,
					22L,
					0L,
					Action.FINALIZE,
					List.of(new FieldValue(1, text("fresh-final")))
				)
			) == OfferResult.ACCEPTED,
			"the fresh connection FINALIZE must resolve the remaining slot"
		);
		state = state.record(Operation.FINALIZE, DeliveryResult.SENT);
		check(state.terminal(), "the fresh transport must terminate after FINALIZE");
	}

	private static void checkAppendFailurePreservesEstablishment() {
		State state = MessageFinalProjectionTransportContract.begin(true, true, false);
		State failed = state.record(Operation.APPEND, DeliveryResult.RETRYABLE_FAILURE);
		check(
			failed == state
				&& failed.establishedOnConnection()
				&& !failed.planPending()
				&& failed.nextOperation().orElseThrow() == Operation.APPEND,
			"an APPEND failure must stay established and retry APPEND, never PLAN"
		);
		State retried = failed.record(Operation.APPEND, DeliveryResult.SENT);
		checkEquals(
			List.of(Operation.FINALIZE),
			retried.successfulPath(),
			"a successful APPEND retry must continue directly to FINALIZE"
		);

		State fresh = MessageFinalProjectionTransportContract.begin(false, true, false);
		State planFailed = fresh.record(Operation.PLAN, DeliveryResult.RETRYABLE_FAILURE);
		check(
			!planFailed.establishedOnConnection()
				&& planFailed.nextOperation().orElseThrow() == Operation.PLAN,
			"a failed fresh PLAN must remain fresh and retry PLAN"
		);
	}

	private static void checkEstablishedWithoutAdditionsFinalizesDirectly() {
		State state = MessageFinalProjectionTransportContract.begin(true, false, true);
		checkEquals(
			List.of(Operation.FINALIZE),
			state.successfulPath(),
			"an established plan with no additions must not resend PLAN or locked fields"
		);
	}

	private static void checkFinalizeIsConnectionLocalAndExactlyOnce() {
		State established = MessageFinalProjectionTransportContract.begin(
			true,
			false,
			false
		);
		State terminal = established.record(Operation.FINALIZE, DeliveryResult.SENT);
		check(
			terminal.terminal() && terminal.nextOperation().isEmpty(),
			"one delivered FINALIZE must retire this connection's projection"
		);
		expectThrows(
			IllegalStateException.class,
			() -> terminal.record(Operation.FINALIZE, DeliveryResult.SENT),
			"the same physical connection must never receive FINALIZE twice"
		);

		State freshConnection = MessageFinalProjectionTransportContract.begin(
			false,
			false,
			true
		);
		checkEquals(
			List.of(
				Operation.PLAN,
				Operation.LOCKED_SNAPSHOT,
				Operation.FINALIZE
			),
			freshConnection.successfulPath(),
			"a new physical connection must reset connection-local final delivery"
		);
	}

	private static void checkAuthorizationRefreshRetractsRevokedNodes() {
		MessageWireInbox inbox = new MessageWireInbox();
		MessagePlaybackPlan original = plan(
			30L,
			List.of(node(0, 0, "allowed"), node(1, 1, "revoked"))
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(original)) == OfferResult.ACCEPTED,
			"the pre-reauthorization connection plan must be established"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					31L,
					List.of(new FieldValue(0, text("retained-lock")))
				)
			) == OfferResult.ACCEPTED,
			"the retained slot must be locked before REFRESH"
		);

		MessagePlaybackPlan refreshed = new MessagePlaybackPlan(
			INSTANCE,
			original.cueId(),
			GENERATION,
			32L,
			1L,
			PlanDisposition.REFRESH,
			Optional.empty(),
			original.clock(),
			original.policies(),
			original.origin(),
			List.of(node(0, 0, "allowed"))
		);
		check(
			inbox.offer(new MessagePlanS2CPayload(refreshed)) == OfferResult.ACCEPTED,
			"REFRESH must atomically retract the revoked node and its field slot"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					33L,
					List.of(new FieldValue(0, text("retained-lock")))
				)
			) == OfferResult.ACCEPTED,
			"REFRESH must receive a fresh snapshot for each still-declared locked slot"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					34L,
					List.of(new FieldValue(1, text("revoked-lock")))
				)
			) == OfferResult.UNKNOWN_FIELD_SLOT,
			"REFRESH must remove every revoked field slot from the physical connection"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					GENERATION,
					34L,
					0L,
					Action.FINALIZE,
					List.of()
				)
			) == OfferResult.ACCEPTED,
			"FINALIZE after authorization refresh must cover only the refreshed slot set"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					GENERATION,
					35L,
					0L,
					Action.FINALIZE,
					List.of(new FieldValue(1, text("revoked-final")))
				)
			) == OfferResult.TERMINAL_INSTANCE,
			"no packet may restore a revoked slot after the refreshed FINALIZE"
		);
	}

	private static void checkCancelClosesOnlyTheCurrentPhysicalConnection() {
		MessageWireInbox inbox = new MessageWireInbox();
		MessagePlaybackPlan established = plan(40L, List.of(node(0, 0, "visible")));
		check(
			inbox.offer(new MessagePlanS2CPayload(established)) == OfferResult.ACCEPTED,
			"the connection must own a plan before fail-closed cleanup"
		);
		check(
			inbox.offer(
				new MessageControlS2CPayload(
					INSTANCE,
					GENERATION,
					41L,
					0L,
					Action.CANCEL,
					List.of()
				)
			) == OfferResult.ACCEPTED,
			"zero authorization must retract an established plan with text-free CANCEL"
		);
		check(
			inbox.offer(
				new MessageNodeAppendS2CPayload(
					INSTANCE,
					GENERATION,
					42L,
					List.of(node(1, 1, "late"))
				)
			) == OfferResult.TERMINAL_INSTANCE,
			"CANCEL must reject every later APPEND on the same physical connection"
		);
		check(
			inbox.offer(
				new MessageFieldDeltaS2CPayload(
					INSTANCE,
					GENERATION,
					42L,
					List.of(new FieldValue(0, text("late")))
				)
			) == OfferResult.TERMINAL_INSTANCE,
			"CANCEL must reject every later field delta on the same physical connection"
		);
		for (Action action : List.of(Action.PAUSE, Action.FINALIZE)) {
			check(
				inbox.offer(
					new MessageControlS2CPayload(
						INSTANCE,
						GENERATION,
						42L,
						0L,
						action,
						List.of()
					)
				) == OfferResult.TERMINAL_INSTANCE,
				"CANCEL must reject later " + action + " on the same physical connection"
			);
		}

		MessageWireInbox reconnected = new MessageWireInbox();
		check(
			reconnected.offer(
				new MessagePlanS2CPayload(plan(50L, List.of(node(0, 0, "reconnected"))))
			) == OfferResult.ACCEPTED,
			"a new physical connection may establish the logical instance again"
		);
	}

	private static void checkRuntimeTerminalWiring() {
		String source;
		try {
			source = Files.readString(
				Path.of(
					"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageServerRuntime.java"
				)
			);
		} catch (IOException error) {
			throw new IllegalStateException("could not inspect final transport runtime wiring", error);
		}
		String finalOnly = section(
			source,
			"private static void deliverFinalOnly",
			"private static void advancePendingFinalOnlyDeliveries"
		);
		check(
			count(finalOnly, "settleFinalOnlyProjection(") >= 4
				&& finalOnly.contains("scrubFinalConnection("),
			"every non-success final-only exit must converge on connection cleanup"
		);
		String synchronization = section(
			source,
			"private static boolean applySynchronizationDecision",
			"private static Optional<Long> resolveOccurrenceStart"
		);
		check(
			count(synchronization, "settleSynchronizationStaticProjection(") >= 6
				&& synchronization.contains("scrubConnectionProjection("),
			"synchronized static-final failures must close only the physical projection"
		);
		String control = section(
			source,
			"private static void sendControl",
			"private static boolean sendFinalControlIfPossible"
		);
		check(
			control.contains("continuing with text-free connection cleanup")
				&& control.contains("closeConnectionProjection("),
			"CANCEL must survive checkpoint failure and failed FINALIZE must downgrade to cleanup"
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

	private static int count(final String value, final String token) {
		int count = 0;
		for (int offset = value.indexOf(token); offset >= 0; offset = value.indexOf(token, offset + token.length())) {
			count++;
		}
		return count;
	}

	private static MessagePlaybackPlan plan(
		final long sequence,
		final List<ResolvedNode> nodes
	) {
		return new MessagePlaybackPlan(
			INSTANCE,
			Identifier.parse("pixel_tzz:self_check/final_transport"),
			GENERATION,
			sequence,
			0L,
			PlanDisposition.CREATE,
			Optional.empty(),
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

	private static ResolvedComponent text(final String value) {
		return ResolvedComponent.parseCanonical("{\"text\":\"" + value + "\"}");
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

	private static void expectThrows(
		final Class<? extends Throwable> expected,
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
		} catch (Throwable error) {
			if (expected.isInstance(error)) {
				return;
			}
			throw new IllegalStateException(
				message + "; unexpected=" + error,
				error
			);
		}
		throw new IllegalStateException(message + "; no exception was thrown");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
