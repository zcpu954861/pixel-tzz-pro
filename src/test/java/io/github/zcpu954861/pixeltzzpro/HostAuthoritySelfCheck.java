package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.Actor;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.OnlineTarget;
import io.github.zcpu954861.pixeltzzpro.server.HostAuthority.Result;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostOperation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Small runnable contract check for authoritative single-host transactions.
 */
public final class HostAuthoritySelfCheck {
	private static final UUID HOST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private HostAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		checkClaim();
		checkTransfer();
		checkTakeover();
		checkPreflightFailures();
		checkInputBoundary();
		System.out.println("HOST_AUTHORITY_SELF_CHECK=PASS");
	}

	private static void checkClaim() {
		WorldStateV2 initial = WorldStateV2.initial();
		Actor host = new Actor(HOST_ID, "Host", true);
		Result claimed = HostAuthority.claim(initial, host, 1_000L);
		WorldStateV2 state = successfulState(
			initial,
			claimed,
			HOST_ID,
			"Host",
			HOST_ID,
			HostOperation.CLAIM,
			AuditEventType.HOST_CLAIMED,
			1_000L,
			"claim"
		);
		check(initial.host().isEmpty(), "successful claim must not mutate its input");

		WorldStateV2 unowned = WorldStateV2.initial();
		assertFailedWithoutMutation(
			unowned,
			() -> HostAuthority.claim(
				unowned,
				new Actor(OTHER_ID, "Non-operator", false),
				1_001L
			),
			OperationCode.FORBIDDEN,
			"non-OP claim"
		);
		assertFailedWithoutMutation(
			state,
			() -> HostAuthority.claim(state, new Actor(OTHER_ID, "Other", true), 1_002L),
			OperationCode.HOST_ALREADY_ASSIGNED,
			"repeat claim"
		);
	}

	private static void checkTransfer() {
		WorldStateV2 claimed = claimedState();
		Actor nonOpHost = new Actor(HOST_ID, "Host", false);
		Result transferred = HostAuthority.transfer(
			claimed,
			nonOpHost,
			new OnlineTarget(TARGET_ID, "Runner", true),
			2_000L
		);
		successfulState(
			claimed,
			transferred,
			TARGET_ID,
			"Runner",
			HOST_ID,
			HostOperation.TRANSFER,
			AuditEventType.HOST_TRANSFERRED,
			2_000L,
			"transfer"
		);

		assertFailedWithoutMutation(
			claimed,
			() -> HostAuthority.transfer(
				claimed,
				new Actor(OTHER_ID, "Other", true),
				new OnlineTarget(TARGET_ID, "Runner", true),
				2_001L
			),
			OperationCode.NOT_HOST,
			"non-host transfer"
		);
		assertFailedWithoutMutation(
			claimed,
			() -> HostAuthority.transfer(
				claimed,
				nonOpHost,
				new OnlineTarget(TARGET_ID, "Runner", false),
				2_002L
			),
			OperationCode.TARGET_OFFLINE,
			"offline-target transfer"
		);
		assertFailedWithoutMutation(
			claimed,
			() -> HostAuthority.transfer(
				claimed,
				nonOpHost,
				new OnlineTarget(HOST_ID, "Host", true),
				2_003L
			),
			OperationCode.TARGET_INVALID,
			"self transfer"
		);
	}

	private static void checkTakeover() {
		WorldStateV2 claimed = claimedState();
		Actor operator = new Actor(OTHER_ID, "Recovery operator", true);
		Result takenOver = HostAuthority.takeover(claimed, operator, 3_000L);
		successfulState(
			claimed,
			takenOver,
			OTHER_ID,
			"Recovery operator",
			OTHER_ID,
			HostOperation.TAKEOVER,
			AuditEventType.HOST_TAKEN_OVER,
			3_000L,
			"takeover"
		);

		assertFailedWithoutMutation(
			claimed,
			() -> HostAuthority.takeover(
				claimed,
				new Actor(OTHER_ID, "Non-operator", false),
				3_001L
			),
			OperationCode.FORBIDDEN,
			"non-OP takeover"
		);
		WorldStateV2 unowned = WorldStateV2.initial();
		assertFailedWithoutMutation(
			unowned,
			() -> HostAuthority.takeover(unowned, operator, 3_002L),
			OperationCode.ACTION_UNAVAILABLE,
			"takeover without an existing host"
		);
		assertFailedWithoutMutation(
			claimed,
			() -> HostAuthority.takeover(
				claimed,
				new Actor(HOST_ID, "Host", true),
				3_003L
			),
			OperationCode.ACTION_UNAVAILABLE,
			"current-host takeover"
		);
	}

	private static void checkPreflightFailures() {
		Actor operator = new Actor(HOST_ID, "Host", true);
		WorldStateV2 revisionOverflow = WorldStateV2.initial().withStateRevision(Long.MAX_VALUE);
		assertFailedWithoutMutation(
			revisionOverflow,
			() -> HostAuthority.claim(revisionOverflow, operator, 4_000L),
			OperationCode.INTERNAL_ERROR,
			"revision overflow"
		);

		WorldStateV2 auditOverflow = WorldStateV2.initial().withAudit(
			new AuditLog(Long.MAX_VALUE, List.of())
		);
		assertFailedWithoutMutation(
			auditOverflow,
			() -> HostAuthority.claim(auditOverflow, operator, 4_001L),
			OperationCode.INTERNAL_ERROR,
			"audit overflow"
		);

		WorldStateV2 valid = WorldStateV2.initial();
		assertFailedWithoutMutation(
			valid,
			() -> HostAuthority.claim(valid, operator, -1L),
			OperationCode.INTERNAL_ERROR,
			"negative operation time"
		);

		WorldStateV2 invalidSchema = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION + 1,
			0L,
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Map.of(),
			Optional.empty(),
			List.of(),
			AuditLog.empty(),
			Optional.empty()
		);
		Result schemaBlocked = HostAuthority.claim(invalidSchema, operator, 4_002L);
		check(schemaBlocked.code() == OperationCode.SCHEMA_BLOCKED, "invalid schema must fail closed");
		check(schemaBlocked.nextState().isEmpty(), "invalid schema must not produce state");
		check(invalidSchema.stateRevision() == 0L, "invalid schema rejection must not mutate revision");
		check(
			WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, invalidSchema).error().isPresent(),
			"invalid schema fixture must remain rejected by the authoritative codec"
		);
	}

	private static void checkInputBoundary() {
		String maximumName = "x".repeat(64);
		Actor boundaryActor = new Actor(HOST_ID, maximumName, true);
		Result claimed = HostAuthority.claim(WorldStateV2.initial(), boundaryActor, 5_000L);
		check(
			claimed.nextState().orElseThrow().host().orElseThrow().lastKnownName().orElseThrow()
				.equals(maximumName),
			"64-character names must be retained"
		);
		new OnlineTarget(TARGET_ID, maximumName, true);

		expectIllegalArgument(
			() -> new Actor(HOST_ID, "", true),
			"blank actor names must be rejected"
		);
		expectIllegalArgument(
			() -> new Actor(HOST_ID, "x".repeat(65), true),
			"oversized actor names must be rejected"
		);
		expectIllegalArgument(
			() -> new OnlineTarget(TARGET_ID, " ", true),
			"blank target names must be rejected"
		);
		expectIllegalArgument(
			() -> new OnlineTarget(TARGET_ID, "x".repeat(65), true),
			"oversized target names must be rejected"
		);
		expectNullPointer(
			() -> new Actor(null, "Host", true),
			"actor UUID must be required"
		);
		expectNullPointer(
			() -> new OnlineTarget(null, "Runner", true),
			"target UUID must be required"
		);
	}

	private static WorldStateV2 claimedState() {
		return HostAuthority.claim(
			WorldStateV2.initial(),
			new Actor(HOST_ID, "Host", true),
			900L
		).nextState().orElseThrow();
	}

	private static WorldStateV2 successfulState(
		final WorldStateV2 before,
		final Result result,
		final UUID expectedHostId,
		final String expectedHostName,
		final UUID expectedActorId,
		final HostOperation expectedOperation,
		final AuditEventType expectedAuditType,
		final long expectedTime,
		final String label
	) {
		check(result.code() == OperationCode.SUCCESS, label + " must succeed");
		check(result.successful(), label + " success helper must agree with its code");
		WorldStateV2 next = result.nextState().orElseThrow();
		check(
			next.stateRevision() == before.stateRevision() + 1L,
			label + " must increment state revision exactly once"
		);
		check(
			next.audit().totalEvents() == before.audit().totalEvents() + 1L,
			label + " must increment the audit count exactly once"
		);

		HostRecord host = next.host().orElseThrow();
		check(host.playerId().equals(expectedHostId), label + " must persist the new host UUID");
		check(
			host.lastKnownName().equals(Optional.of(expectedHostName)),
			label + " must persist the new host name"
		);
		check(
			host.assignedAtEpochMillis().equals(Optional.of(expectedTime)),
			label + " must persist the assignment time"
		);
		check(
			host.lastActorId().equals(Optional.of(expectedActorId)),
			label + " must persist the actor UUID"
		);
		check(
			host.lastOperation().equals(Optional.of(expectedOperation)),
			label + " must persist the operation type"
		);

		AuditEvent event = next.audit().recentEvents().getLast();
		check(
			event.sequence() == before.audit().totalEvents(),
			label + " audit sequence must use the pre-commit total"
		);
		check(event.type() == expectedAuditType, label + " must persist the audit event type");
		check(event.occurredAtEpochMillis() == expectedTime, label + " must persist the audit time");
		check(
			event.actorId().equals(Optional.of(expectedActorId)),
			label + " must persist the audit actor"
		);
		check(
			event.targetPlayerId().equals(Optional.of(expectedHostId)),
			label + " must persist the audit target"
		);
		check(event.flowInstanceId().isEmpty(), label + " audit must not invent a flow instance");
		check(!event.summary().isBlank(), label + " audit summary must be meaningful");
		check(next.validated().result().isPresent(), label + " output must satisfy schema v2");
		return next;
	}

	private static void assertFailedWithoutMutation(
		final WorldStateV2 state,
		final Supplier<Result> action,
		final OperationCode expectedCode,
		final String label
	) {
		JsonElement before = encode(state);
		long revision = state.stateRevision();
		Result result = action.get();
		check(result.code() == expectedCode, label + " returned " + result.code());
		check(!result.successful(), label + " must not report success");
		check(result.nextState().isEmpty(), label + " must not produce a candidate state");
		check(state.stateRevision() == revision, label + " must retain its input revision");
		check(encode(state).equals(before), label + " must preserve the complete encoded input");
	}

	private static JsonElement encode(final WorldStateV2 value) {
		return WorldStateV2.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
	}

	private static void expectIllegalArgument(final Runnable action, final String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void expectNullPointer(final Runnable action, final String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (NullPointerException expected) {
			// Expected.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
