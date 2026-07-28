package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Runnable guard for reload-time recovery of an otherwise empty host-only world.
 */
public final class WorldActivityInitializationSelfCheck {
	private WorldActivityInitializationSelfCheck() {
	}

	public static void main(final String[] args) {
		WorldStateV2 initial = WorldStateV2.initial();
		check(
			PixelTzzServerRuntime.canInitializeWorldActivity(initial),
			"a pristine state must accept its sole registered game"
		);

		WorldStateV2 hostOnly = initial
			.withHost(Optional.of(HostRecord.migratedLegacy(UUID.randomUUID())))
			.withStateRevision(2L);
		check(
			PixelTzzServerRuntime.canInitializeWorldActivity(hostOnly),
			"host assignment and a raised revision must not strand an otherwise empty world"
		);

		WorldStateV2 configured = hostOnly.withActivity(
			Identifier.parse("pixel_tzz:game"),
			Identifier.parse("pixel_tzz:lobby")
		);
		check(
			!PixelTzzServerRuntime.canInitializeWorldActivity(configured),
			"an already configured world must never be rebound during reload"
		);
		System.out.println("WORLD_ACTIVITY_INITIALIZATION_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
