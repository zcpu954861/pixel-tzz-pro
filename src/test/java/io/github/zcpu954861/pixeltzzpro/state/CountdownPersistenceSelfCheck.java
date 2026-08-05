package io.github.zcpu954861.pixeltzzpro.state;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.ReloadStatus;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry.View;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority;
import io.github.zcpu954861.pixeltzzpro.server.CountdownSelfCheckFixtures;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState.CountdownCheckpointResult;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState.LoadKind;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Runnable schema-v5, v4 migration and narrow-checkpoint contract check. */
public final class CountdownPersistenceSelfCheck {
	private static final Identifier GAME_ID = Identifier.parse("pixel_tzz:test_game");
	private static final Identifier PHASE_ID = Identifier.parse("pixel_tzz:test_phase");

	private CountdownPersistenceSelfCheck() {
	}

	public static void main(final String[] args) throws Exception {
		checkCodecAndRecoveryRoundTrip();
		checkWrapperCheckpointAndScopeRotation();
		checkV4Migration();
		System.out.println("COUNTDOWN_PERSISTENCE_SELF_CHECK=PASS");
	}

	private static void checkCodecAndRecoveryRoundTrip() {
		Instance active = CountdownSelfCheckFixtures.start();
		Instance recovery = CountdownAuthority.enterRecoveryWait(active, 1_020L, "normal restart")
			.instance().orElseThrow();
		JsonElement encodedCountdown = PersistedCountdown.Instance.CODEC
			.encodeStart(JsonOps.INSTANCE, recovery)
			.getOrThrow();
		Instance decodedCountdown = PersistedCountdown.Instance.CODEC
			.parse(JsonOps.INSTANCE, encodedCountdown)
			.getOrThrow();
		check(decodedCountdown.equals(recovery), "countdown DTO must round-trip exactly");
		JsonElement legacyEncodedCountdown = encodedCountdown.deepCopy();
		legacyEncodedCountdown.getAsJsonObject().remove("launch_plan");
		Instance legacyDecodedCountdown = PersistedCountdown.Instance.CODEC
			.parse(JsonOps.INSTANCE, legacyEncodedCountdown)
			.getOrThrow();
		check(
			legacyDecodedCountdown.launchPlan().isEmpty(),
			"schema-v5 countdowns written before launch_plan must remain readable"
		);
		check(
			decodedCountdown.lifecycle().state() == CountdownState.RECOVERY_WAIT
				&& decodedCountdown.lifecycle().remainingTicks() == active.lifecycle().remainingTicks(),
			"recovery wait must persist the same remaining Tick value"
		);

		WorldStateV3 v3 = WorldStateV3.initial().beginGameInstance(
			GAME_ID,
			PHASE_ID,
			CountdownSelfCheckFixtures.GAME_INSTANCE_ID
		);
		WorldStateV4 v4 = new WorldStateV4(
			WorldStateV4.SCHEMA_VERSION,
			v3,
			List.of(),
			List.of()
		);
		WorldStateV5 root = new WorldStateV5(
			WorldStateV5.SCHEMA_VERSION,
			v4,
			Optional.of(recovery)
		);
		JsonElement encodedRoot = WorldStateV5.CODEC.encodeStart(JsonOps.INSTANCE, root).getOrThrow();
		WorldStateV5 decodedRoot = WorldStateV5.CODEC.parse(JsonOps.INSTANCE, encodedRoot).getOrThrow();
		check(decodedRoot.equals(root), "schema-v5 root must round-trip exactly");
	}

	private static void checkWrapperCheckpointAndScopeRotation() {
		PixelTzzWorldState wrapper = PixelTzzWorldState.initial();
		check(wrapper.loadKind() == LoadKind.CURRENT_V5, "new world must be schema v5");
		check(wrapper.currentV4().isPresent(), "v4 compatibility view must remain available");
		var game = wrapper.commitV3(
			0L,
			value -> value.beginGameInstance(
				GAME_ID,
				PHASE_ID,
				CountdownSelfCheckFixtures.GAME_INSTANCE_ID
			)
		);
		check(game.committed(), "fixture game scope must start");
		Instance countdown = CountdownSelfCheckFixtures.start();
		var frozen = wrapper.commitV5(1L, value -> value.withCountdown(Optional.of(countdown)));
		check(frozen.committed(), "atomic schema-v5 transaction must freeze countdown");
		long globalRevision = wrapper.stateRevision();
		Instance advanced = CountdownAuthority.tick(countdown, 1_010L).instance().orElseThrow();
		CountdownCheckpointResult checkpoint = wrapper.countdownCheckpoint(
			countdown.gameInstanceId(),
			countdown.countdownInstanceId(),
			countdown.lifecycle().stateVersion(),
			countdown.lifecycle().remainingTicks(),
			countdown.lifecycle().lastServerTickAnchor(),
			Optional.of(advanced)
		);
		check(checkpoint.committed(), "valid narrow countdown checkpoint must commit");
		check(wrapper.stateRevision() == globalRevision, "routine Tick must not change global revision");
		check(
			!wrapper.countdownCheckpoint(
				countdown.gameInstanceId(),
				countdown.countdownInstanceId(),
				countdown.lifecycle().stateVersion(),
				countdown.lifecycle().remainingTicks(),
				countdown.lifecycle().lastServerTickAnchor(),
				Optional.of(advanced)
			).committed(),
			"stale lifecycle checkpoint must be rejected"
		);
		Instance recovery = CountdownAuthority.enterRecoveryWait(
			advanced,
			5L,
			"process tick reset"
		).instance().orElseThrow();
		check(
			wrapper.countdownCheckpoint(
				advanced.gameInstanceId(),
				advanced.countdownInstanceId(),
				advanced.lifecycle().stateVersion(),
				advanced.lifecycle().remainingTicks(),
				advanced.lifecycle().lastServerTickAnchor(),
				Optional.of(recovery)
			).committed(),
			"normal restart must permit only the versioned recovery anchor reset"
		);
		Instance resumed = CountdownAuthority.reconcileConnections(
			recovery,
			Set.of(
				CountdownSelfCheckFixtures.PLAYER_B,
				CountdownSelfCheckFixtures.PLAYER_C
			),
			6L
		).instance().orElseThrow();
		check(
			wrapper.countdownCheckpoint(
				recovery.gameInstanceId(),
				recovery.countdownInstanceId(),
				recovery.lifecycle().stateVersion(),
				recovery.lifecycle().remainingTicks(),
				recovery.lifecycle().lastServerTickAnchor(),
				Optional.of(resumed)
			).committed(),
			"connection reconciliation must continue from the reset recovery anchor"
		);

		UUID nextScope = UUID.fromString("00000000-0000-0000-0000-000000003c99");
		check(
			wrapper.commitV3(
				globalRevision,
				value -> value.beginGameInstance(GAME_ID, PHASE_ID, nextScope)
			).committed(),
			"next game scope must start"
		);
		check(
			wrapper.currentV5().orElseThrow().countdown().isEmpty(),
			"game-scope rotation must clear the old countdown"
		);
	}

	private static void checkV4Migration() throws Exception {
		WorldStateV4 legacyRoot = WorldStateV4.initial();
		JsonElement encodedV4 = WorldStateV4.CODEC.encodeStart(JsonOps.INSTANCE, legacyRoot).getOrThrow();
		PixelTzzWorldState legacy = PixelTzzWorldState.CODEC
			.parse(JsonOps.INSTANCE, encodedV4)
			.getOrThrow();
		check(legacy.loadKind() == LoadKind.LEGACY_V4, "schema v4 must be migratable legacy");
		check(legacy.currentV4().isEmpty(), "unmigrated v4 must not be writable current state");

		Path root = Files.createTempDirectory("pixel-tzz-v4-v5-");
		try {
			Path stateFile = root.resolve("world_state.dat");
			Path recovery = root.resolve("recovery");
			Files.writeString(stateFile, encodedV4.toString());
			var migrated = PixelTzzWorldStateMigration.migrate(
				legacy,
				new View(DefinitionSnapshot.empty(), ReloadStatus.INVALID, false, List.of(), 0),
				stateFile,
				recovery,
				123_456L
			);
			check(
				migrated.status() == PixelTzzWorldStateMigration.MigrationStatus.MIGRATED,
				"schema v4 must migrate additively to v5: " + migrated.reason()
			);
			check(
				legacy.loadKind() == LoadKind.CURRENT_V5
					&& legacy.currentV5().orElseThrow().countdown().isEmpty(),
				"v4 migration must publish writable v5 with no invented countdown"
			);
			check(
				legacy.currentV4().orElseThrow().usages().equals(legacyRoot.usages())
					&& legacy.currentV4().orElseThrow().messageHistory().equals(legacyRoot.messageHistory()),
				"v4 ledgers must survive v5 migration"
			);
			try (var recoveryFiles = Files.list(recovery)) {
				check(
					recoveryFiles.count() == 1L,
					"migration must create one verified recovery copy"
				);
			}
		} finally {
			try (var paths = Files.walk(root)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (java.io.IOException error) {
						throw new IllegalStateException(error);
					}
				});
			}
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
