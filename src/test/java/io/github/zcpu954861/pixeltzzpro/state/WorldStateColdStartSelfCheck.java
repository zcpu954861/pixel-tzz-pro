package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEvent;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import java.util.Optional;

/**
 * Reproduces the real server-start order where migration touches a nested audit enum before CODEC.
 */
public final class WorldStateColdStartSelfCheck {
	private WorldStateColdStartSelfCheck() {
	}

	public static void main(final String[] arguments) {
		AuditEventType firstTouch = AuditEventType.RECOVERY_CREATED;
		check(firstTouch.getSerializedName().equals("recovery_created"), "cold enum initialization");

		AuditLog audit = AuditLog.empty().append(
			new AuditEvent(
				0L,
				firstTouch,
				1L,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				"Cold-start codec check"
			)
		);
		WorldStateV3 initial = WorldStateV3.initial();
		WorldStateV3 state = initial.withCore(initial.core().withAudit(audit));
		var encoded = WorldStateV3.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
		WorldStateV3 decoded = WorldStateV3.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(decoded.core().audit().recentEvents().getFirst().type() == firstTouch, "cold codec round-trip");
		System.out.println("WORLD_STATE_COLD_START_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
