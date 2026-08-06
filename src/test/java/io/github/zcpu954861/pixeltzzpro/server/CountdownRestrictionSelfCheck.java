package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownProjectionDocumentCodec.ProjectionFrame;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownResyncRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.RestrictedAction;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownState;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Runnable check for the authoritative event gates and the narrow client inventory-open guard. */
public final class CountdownRestrictionSelfCheck {
	private CountdownRestrictionSelfCheck() {
	}

	public static void main(final String[] args) throws Exception {
		Instance active = CountdownSelfCheckFixtures.start();
		for (RestrictedAction action : List.of(
			RestrictedAction.MOVEMENT,
			RestrictedAction.ATTACK,
			RestrictedAction.INTERACT,
			RestrictedAction.ITEM_USE,
			RestrictedAction.ITEM_DROP,
			RestrictedAction.ITEM_SWAP,
			RestrictedAction.INVENTORY,
			RestrictedAction.DAMAGE
		)) {
			check(
				CountdownAuthority.restrictionDecision(
					active,
					CountdownSelfCheckFixtures.PLAYER_B,
					action
				).blocked(),
				"safe default must block or immunize " + action
			);
		}
		for (RestrictedAction action : List.of(
			RestrictedAction.CAMERA,
			RestrictedAction.CHAT,
			RestrictedAction.ESCAPE
		)) {
			check(
				!CountdownAuthority.restrictionDecision(
					active,
					CountdownSelfCheckFixtures.PLAYER_B,
					action
				).blocked(),
				"camera, chat and ESC must remain available"
			);
		}
		check(
			!CountdownAuthority.restrictionDecision(
				active,
				UUID.fromString("00000000-0000-0000-0000-000000003cff"),
				RestrictedAction.ATTACK
			).blocked(),
			"restriction must not expand beyond frozen participants"
		);
		check(
			!CountdownAuthority.restrictionDecision(
				null,
				CountdownSelfCheckFixtures.PLAYER_B,
				RestrictedAction.INVENTORY
			).blocked(),
			"inventory must remain untouched when no countdown exists"
		);

		Instance oneReleased = CountdownAuthority.releaseRestrictions(
			active,
			Set.of(CountdownSelfCheckFixtures.PLAYER_B)
		).instance().orElseThrow();
		check(
			!CountdownAuthority.restrictionDecision(
				oneReleased,
				CountdownSelfCheckFixtures.PLAYER_B,
				RestrictedAction.ATTACK
			).blocked(),
			"released player marker must stop gating"
		);
		check(
			CountdownAuthority.restrictionDecision(
				oneReleased,
				CountdownSelfCheckFixtures.PLAYER_C,
				RestrictedAction.ATTACK
			).blocked(),
			"other frozen participant must remain gated"
		);
		Instance releasedAgain = CountdownAuthority.releaseRestrictions(
			oneReleased,
			Set.of(CountdownSelfCheckFixtures.PLAYER_B)
		).instance().orElseThrow();
		check(releasedAgain.equals(oneReleased), "repeated release must be idempotent");

		Instance canceled = CountdownAuthority.finalizeCancel(
			CountdownAuthority.requestCancel(active, 1_010L, "fixture cancel").instance().orElseThrow(),
			1_011L
		).instance().orElseThrow();
		check(canceled.lifecycle().state() == CountdownState.CANCELED, "fixture must cancel");
		check(
			canceled.restrictionMarkers().stream().noneMatch(value -> value.active()),
			"terminal boundary must release every marker"
		);

		Restrictions allowAll = new Restrictions(false, false, false, false, false, false, false, false);
		Instance permissive = CountdownAuthority.start(
			CountdownSelfCheckFixtures.request(
				DisconnectPolicies.defaults(), allowAll, List.of(), List.of()
			)
		).instance().orElseThrow();
		check(
			!CountdownAuthority.restrictionDecision(
				permissive,
				CountdownSelfCheckFixtures.PLAYER_B,
				RestrictedAction.MOVEMENT
			).blocked(),
			"frozen definition may explicitly allow a supported action"
		);
		checkInventoryOpenGuardContract();
		checkResyncSequenceContract();
		System.out.println("COUNTDOWN_RESTRICTION_SELF_CHECK=PASS");
	}

	private static void checkInventoryOpenGuardContract() throws Exception {
		ProjectionFrame blocked = new ProjectionFrame(
			UUID.fromString("00000000-0000-0000-0000-000000003c01"),
			"opening",
			200L,
			"running",
			1_000L,
			200.0D,
			-1.0D,
			false,
			true,
			Optional.empty()
		);
		JsonObject replace = json(
			CountdownProjectionDocumentCodec.encodeReplace(blocked, Optional.empty())
				.document()
				.orElseThrow()
		);
		JsonObject patch = json(
			CountdownProjectionDocumentCodec.encodePatch(blocked).document().orElseThrow()
		);
		check(
			replace.get("inventory_blocked").getAsBoolean()
				&& patch.get("inventory_blocked").getAsBoolean(),
			"Replace and Patch must retain the per-recipient authoritative inventory gate"
		);
		ProjectionFrame allowed = new ProjectionFrame(
			blocked.countdownInstanceId(),
			blocked.purpose(),
			blocked.totalTicks(),
			blocked.state(),
			blocked.serverTick(),
			blocked.baseRemainingTicks(),
			blocked.rate(),
			blocked.paused(),
			false,
			blocked.checkpoint()
		);
		check(
			!json(
				CountdownProjectionDocumentCodec.encodeReplace(allowed, Optional.empty())
					.document()
					.orElseThrow()
			).get("inventory_blocked").getAsBoolean(),
			"an allowed recipient must not receive an expanded client restriction"
		);

		String projection = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/CountdownProjectionRuntime.java"
		);
		String snapshot = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/ClientCountdownSnapshot.java"
		);
		String runtime = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/ClientCountdownRuntime.java"
		);
		String mixin = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/mixin/CountdownInventoryKeyMixin.java"
		);
		String serverMixin = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/mixin/CountdownInventoryRestrictionMixin.java"
		);
		String mixinConfig = source("src/main/resources/pixel-tzz-pro.mixins.json");
		check(
			projection.contains("replaceDocument(server, countdown, player.getUUID())")
				&& projection.contains("patchDocument(server, countdown, player.getUUID())")
				&& projection.contains("CountdownAuthority.RestrictedAction.INVENTORY"),
			"the client gate must be calculated per recipient by the server restriction authority"
		);
		check(
			snapshot.contains("boolean inventoryBlocked")
				&& snapshot.contains("\"inventory_blocked\"")
				&& snapshot.contains("nextInventoryBlocked"),
			"the strict client Replace/Patch parser must retain the authoritative inventory gate"
		);
		check(
			runtime.contains("public static synchronized boolean inventoryBlocked()")
				&& runtime.contains("clearRequest == null")
				&& runtime.contains("!active.state().terminal()"),
			"Clear and terminal state must immediately disable the client input gate"
		);
		check(
			mixin.contains("method = \"handleKeybinds\"")
				&& mixin.contains("key != this.options.keyInventory")
				&& mixin.contains("ClientCountdownRuntime.inventoryBlocked()")
				&& mixin.contains("boolean clicked = original.call(key)")
				&& !mixin.contains("keyChat")
				&& !mixin.contains("keyTogglePerspective"),
			"the client guard must consume only vanilla's inventory binding and preserve every other input"
		);
		check(
			serverMixin.contains("ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY")
				&& serverMixin.contains("RestrictedAction.INVENTORY")
				&& serverMixin.contains("callback.cancel()"),
			"server-controlled vehicle inventory opening must remain authoritatively blocked"
		);
		check(
			mixinConfig.contains("\"CountdownInventoryKeyMixin\""),
			"the inventory binding guard mixin must be registered only in the client mixin list"
		);
	}

	private static void checkResyncSequenceContract() throws Exception {
		check(
			CountdownProjectionRuntime.authoritativeResyncFloor(12L, 40L) == 40L,
			"authorized resync must raise a lagging server channel to the client tombstone floor"
		);
		check(
			CountdownProjectionRuntime.authoritativeResyncFloor(50L, 40L) == 50L,
			"authorized resync must never move an authoritative channel backwards"
		);
		try {
			CountdownProjectionRuntime.authoritativeResyncFloor(0L, Long.MAX_VALUE);
			throw new AssertionError("future-max resync sequence must fail closed");
		} catch (IllegalArgumentException expected) {
			// Expected trust-boundary rejection.
		}
		Instance countdown = CountdownSelfCheckFixtures.start();
		long epoch = 7L;
		CountdownResyncRequestC2SPayload matching = new CountdownResyncRequestC2SPayload(
			countdown.gameInstanceId(),
			countdown.definition().generation(),
			epoch,
			countdown.lifecycle().stateVersion(),
			40L
		);
		check(
			CountdownProjectionRuntime.resyncEnvelopeMatches(countdown, matching, epoch),
			"only the current authoritative envelope may advance a resync sequence floor"
		);
		CountdownResyncRequestC2SPayload future = new CountdownResyncRequestC2SPayload(
			countdown.gameInstanceId(),
			countdown.definition().generation(),
			epoch,
			countdown.lifecycle().stateVersion() + 1L,
			40L
		);
		check(
			!CountdownProjectionRuntime.resyncEnvelopeMatches(countdown, future, epoch),
			"a client-provided future context must not advance the server channel"
		);
		String clientRuntime = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/ClientCountdownRuntime.java"
		);
		check(
			clientRuntime.contains("Countdown Replace requires resync")
				&& clientRuntime.contains("return FrameAcceptance.resync();"),
			"a full Replace rejected by a Clear tombstone must actively request resynchronization"
		);
		String serverRuntime = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/server/CountdownProjectionRuntime.java"
		);
		check(
			serverRuntime.contains("channel.raiseSequenceFloor(countdown, epoch, payload.appliedSequence())")
				&& serverRuntime.contains("Never echo an untrusted future envelope into a Clear tombstone")
				&& !serverRuntime.contains("payload.gameInstanceId(),\n\t\t\t\tpayload.definitionGeneration(),\n\t\t\t\tpayload.epoch()"),
			"resync must advance only a matching authorized channel and never echo an untrusted envelope"
		);
	}

	private static JsonObject json(final byte[] document) {
		return JsonParser.parseString(new String(document, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static String source(final String path) throws Exception {
		return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
