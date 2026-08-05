package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime.FrameAcceptance;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerFormat;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload.Surface;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Direct recovery, ordering and monotonic-clock checks for the V3C client projection stores. */
public final class HudSnapshotProtocolSelfCheck {
	private static final UUID GAME = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OTHER_GAME = UUID.fromString("10000000-0000-0000-0000-000000000099");
	private static final UUID COUNTDOWN = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID PREVIEW_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID PREVIEW_B = UUID.fromString("30000000-0000-0000-0000-000000000002");
	private static final long TICK_NANOS = 50_000_000L;

	private HudSnapshotProtocolSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkDockAcceptanceClassification();
		checkCountdownAcceptanceClassification();
		checkPreviewAcceptanceClassification();
		checkNamespaceResync();
		checkServerClock();
		ClientHudRuntime.clearAll();
		System.out.println("HUD snapshot protocol self-check passed");
	}

	private static void checkDockAcceptanceClassification() {
		ClientHudRuntime.clearAll();
		checkResult(
			FrameAcceptance.MISSING_BASE,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 0L, 1L, dockPatch("no-base")),
			"a dock Patch without an installed Replace must request recovery"
		);
		checkResult(
			FrameAcceptance.DECODE_INVALID,
			ClientHudRuntime.acceptReplace(GAME, 5L, 7L, 11L, 1L, malformed()),
			"a malformed dock Replace must request recovery"
		);
		check(ClientHudRuntime.snapshot().isEmpty(), "malformed first Replace published a dock");

		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptReplace(GAME, 5L, 7L, 11L, 1L, dock("initial")),
			"first dock Replace must be accepted"
		);
		check(title().equals("initial"), "first dock snapshot was not published");
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptReplace(GAME, 5L, 7L, 11L, 1L, malformed()),
			"a stale Replace must be discarded before decoding"
		);

		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 1L, 2L, dockPatch("patched")),
			"exact-base dock Patch must be accepted"
		);
		check(title().equals("patched"), "accepted dock Patch did not update the value");
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 1L, 2L, malformed()),
			"an old Patch sequence must be discarded before decoding"
		);
		checkResult(
			FrameAcceptance.BASE_GAP,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 1L, 3L, dockPatch("base-gap")),
			"a new Patch with the wrong base must identify the sequence gap"
		);
		checkResult(
			FrameAcceptance.DECODE_INVALID,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 2L, 3L, invalidMergedPatch()),
			"a Patch whose merged document cannot decode must request recovery"
		);
		checkResult(
			FrameAcceptance.STRUCTURE_MISMATCH,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 2L, 3L, structureChangingPatch()),
			"a value Patch must not change the installed node structure"
		);
		check(title().equals("patched"), "rejected dock Patches mutated the snapshot");

		check(
			ClientHudRuntime.acceptClear(GAME, 5L, 7L, 11L, 3L),
			"newer dock Clear must be accepted"
		);
		check(ClientHudRuntime.snapshot().isEmpty(), "dock Clear must remove the visible snapshot");
		checkResult(
			FrameAcceptance.MISSING_BASE,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 11L, 3L, 4L, dockPatch("tombstone")),
			"a Patch must not resurrect a clear tombstone"
		);
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptPatch(GAME, 5L, 7L, 10L, 999L, 1_000L, dockPatch("old-context")),
			"an older Patch namespace must remain stale"
		);
		checkResult(
			FrameAcceptance.NEWER_NAMESPACE,
			ClientHudRuntime.acceptPatch(GAME, 5L, 8L, 1L, 0L, 1L, dockPatch("new-epoch")),
			"a Patch cannot establish a newer epoch without a Replace"
		);
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptPatch(OTHER_GAME, 1L, 1L, 1L, 0L, 1L, dockPatch("new-game")),
			"a Patch from an older game epoch must not overtake the installed game"
		);
		checkResult(
			FrameAcceptance.NEWER_NAMESPACE,
			ClientHudRuntime.acceptPatch(OTHER_GAME, 6L, 8L, 1L, 0L, 1L, dockPatch("new-game")),
			"a different game may request a full Replace only through a newer surface epoch"
		);

		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptReplace(GAME, 5L, 8L, 1L, 1L, dock("new-epoch")),
			"a newer epoch Replace must establish a fresh namespace"
		);
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptReplace(GAME, 5L, 7L, 999L, 999L, dock("old-epoch")),
			"context and sequence must not overtake an older epoch"
		);
		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptReplace(OTHER_GAME, 6L, 9L, 1L, 1L, dock("new-game")),
			"a newer surface epoch must allow the next game to replace the dock"
		);
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptReplace(GAME, 5L, 8L, 999L, 999L, dock("late-old-game")),
			"a late Replace from the previous game must not restore its dock"
		);
		check(
			!ClientHudRuntime.acceptClear(GAME, 5L, 8L, 999L, 1_000L),
			"a late Clear from the previous game must not erase the current dock"
		);
		check(title().equals("new-game"), "late previous-game frames changed the current dock");
	}

	private static void checkCountdownAcceptanceClassification() {
		ClientHudRuntime.clearAll();
		checkResult(
			FrameAcceptance.MISSING_BASE,
			ClientHudRuntime.acceptCountdownPatch(
				GAME, 3L, 4L, 20L, 0L, 1L, countdownPatch(100L, 180.0D)
			),
			"a countdown Patch without a Replace must request recovery"
		);
		checkResult(
			FrameAcceptance.DECODE_INVALID,
			ClientHudRuntime.acceptCountdownReplace(GAME, 3L, 4L, 20L, 1L, malformed()),
			"a malformed countdown Replace must request recovery"
		);
		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptCountdownReplace(
				GAME, 3L, 4L, 20L, 1L, countdown(200L, 200.0D)
			),
			"first countdown Replace must be accepted"
		);
		check(remaining() == 200.0D, "first countdown projection was not published");
		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptCountdownPatch(
				GAME, 3L, 4L, 20L, 1L, 2L, countdownPatch(101L, 180.0D)
			),
			"exact-base countdown Patch must be accepted"
		);
		check(remaining() == 180.0D, "accepted countdown Patch did not update remaining time");
		checkResult(
			FrameAcceptance.BASE_GAP,
			ClientHudRuntime.acceptCountdownPatch(
				GAME, 3L, 4L, 20L, 1L, 3L, countdownPatch(102L, 160.0D)
			),
			"countdown base gap must request recovery"
		);
		checkResult(
			FrameAcceptance.NEWER_NAMESPACE,
			ClientHudRuntime.acceptCountdownPatch(
				GAME, 3L, 4L, 21L, 0L, 1L, countdownPatch(103L, 140.0D)
			),
			"a countdown Patch cannot establish a newer context"
		);
		check(
			ClientHudRuntime.acceptCountdownClear(GAME, 3L, 4L, 20L, 3L),
			"newer countdown Clear must be accepted"
		);
		checkResult(
			FrameAcceptance.MISSING_BASE,
			ClientHudRuntime.acceptCountdownPatch(
				GAME, 3L, 4L, 20L, 3L, 4L, countdownPatch(104L, 120.0D)
			),
			"countdown Patch must not resurrect a clear tombstone"
		);
		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptCountdownReplace(
				OTHER_GAME, 4L, 5L, 1L, 1L, countdown(300L, 300.0D)
			),
			"a newer surface epoch must allow the next game's countdown"
		);
		check(
			!ClientHudRuntime.acceptCountdownClear(GAME, 3L, 4L, 999L, 999L),
			"a late previous-game Clear must not erase the current countdown"
		);
		checkResult(
			FrameAcceptance.STALE,
			ClientHudRuntime.acceptCountdownReplace(
				GAME, 3L, 4L, 999L, 1_000L, countdown(100L, 100.0D)
			),
			"a late previous-game Replace must not restore its countdown"
		);
		check(remaining() == 300.0D, "late previous-game frames changed the current countdown");
	}

	private static void checkPreviewAcceptanceClassification() {
		ClientHudRuntime.clearAll();
		ClientHudRuntime.activatePreview(PREVIEW_A);
		check(
			ClientHudRuntime.acceptPreviewReplace(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.HUD,
				PREVIEW_A,
				1L,
				1L,
				dock("preview-a")
			),
			"the active preview session must accept its HUD Replace"
		);
		check(
			ClientHudRuntime.acceptPreviewClear(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.HUD,
				PREVIEW_A,
				1L,
				2L
			),
			"the active preview session must accept its HUD Clear"
		);
		check(ClientHudRuntime.previewSnapshot().isEmpty(), "preview Clear must hide the HUD preview");
		check(
			!ClientHudRuntime.acceptPreviewReplace(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.HUD,
				PREVIEW_A,
				1L,
				1L,
				dock("stale-after-clear")
			),
			"a delayed Replace must not resurrect a cleared preview tombstone"
		);

		ClientHudRuntime.clearPreview(PREVIEW_A);
		check(
			!ClientHudRuntime.acceptPreviewReplace(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.HUD,
				PREVIEW_A,
				2L,
				1L,
				dock("closed-a")
			),
			"a locally closed preview session must reject delayed frames"
		);

		ClientHudRuntime.activatePreview(PREVIEW_B);
		check(
			!ClientHudRuntime.acceptPreviewReplace(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.HUD,
				PREVIEW_A,
				3L,
				1L,
				dock("old-session")
			),
			"an older preview ID must not overwrite the newly active session"
		);
		check(
			ClientHudRuntime.acceptPreviewReplace(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.COUNTDOWN,
				PREVIEW_B,
				4L,
				1L,
				countdown(200L, 200.0D)
			),
			"the active preview session must accept its Countdown Replace"
		);
		check(
			ClientHudRuntime.acceptPreviewClear(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.COUNTDOWN,
				PREVIEW_B,
				4L,
				2L
			),
			"the active preview session must accept its Countdown Clear"
		);
		check(
			!ClientHudRuntime.acceptPreviewReplace(
				io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface.COUNTDOWN,
				PREVIEW_B,
				4L,
				1L,
				countdown(200L, 180.0D)
			),
			"a delayed Replace must not resurrect a cleared Countdown preview"
		);
		ClientHudRuntime.clearPreview(PREVIEW_B);
	}

	private static void checkNamespaceResync() {
		ClientHudRuntime.clearAll();
		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptReplace(GAME, 9L, 3L, 4L, 1L, dock("resync")),
			"resync fixture Replace failed"
		);
		checkResult(
			FrameAcceptance.ACCEPTED,
			ClientHudRuntime.acceptPatch(GAME, 9L, 3L, 4L, 1L, 2L, dockPatch("resync-2")),
			"resync fixture Patch failed"
		);

		long start = 1_000_000_000L;
		check(
			ClientHudRuntime.prepareResync(Surface.HUD, GAME, 9L, 3L, 4L, start).orElseThrow() == 2L,
			"same-namespace Resync must report the installed sequence"
		);
		check(
			ClientHudRuntime.prepareResync(Surface.HUD, GAME, 9L, 3L, 4L, start + 1_000_000_000L).isEmpty(),
			"the same Resync namespace must be throttled for two seconds"
		);
		check(
			ClientHudRuntime.prepareResync(Surface.HUD, GAME, 9L, 3L, 5L, start + 1_000_000_000L)
				.orElseThrow() == 0L,
			"a different context must neither inherit sequence nor share throttling"
		);
		check(
			ClientHudRuntime.prepareResync(Surface.COUNTDOWN, GAME, 9L, 3L, 4L, start + 1_000_000_000L)
				.orElseThrow() == 0L,
			"HUD and countdown Resync throttles must remain isolated"
		);
		check(
			ClientHudRuntime.prepareResync(Surface.HUD, GAME, 9L, 3L, 4L, start + 2_000_000_000L)
				.orElseThrow() == 2L,
			"the exact namespace must become eligible after the throttle interval"
		);

		ClientHudRuntime.clearAll();
		check(
			ClientHudRuntime.prepareResync(Surface.HUD, GAME, 9L, 3L, 4L, start + 2_000_000_001L)
				.orElseThrow() == 0L,
			"clearAll must reset both the throttle and installed sequence"
		);
	}

	private static void checkServerClock() {
		ClientHudRuntime.clearAll();
		check(
			ClientHudRuntime.estimatedServerTick(0L).isEmpty(),
			"server clock must start empty"
		);
		long firstReceipt = 100L * TICK_NANOS;
		check(
			ClientHudRuntime.observeServerTick(200L, firstReceipt, 25_000_000L),
			"first valid server clock sample was rejected"
		);
		double initial = ClientHudRuntime.estimatedServerTick(firstReceipt).orElseThrow();
		checkClose(200.5D, initial, 0.000_001D, "25 ms one-way delay must add half a tick");

		long secondReceipt = firstReceipt + 1_000_000_000L;
		ClientHudRuntime.observeServerTick(221L, secondReceipt, 25_000_000L);
		double smoothed = ClientHudRuntime.estimatedServerTick(secondReceipt).orElseThrow();
		check(
			smoothed > 220.5D && smoothed < 221.5D,
			"a one-tick offset error must slew instead of snapping"
		);
		double firstGap = 221.5D - smoothed;

		long thirdReceipt = secondReceipt + 1_000_000_000L;
		ClientHudRuntime.observeServerTick(241L, thirdReceipt, 25_000_000L);
		double converged = ClientHudRuntime.estimatedServerTick(thirdReceipt).orElseThrow();
		check(
			converged > 240.5D && 241.5D - converged < firstGap,
			"repeated small-error samples must approach the measured offset"
		);

		long hardReceipt = thirdReceipt + 1_000_000_000L;
		ClientHudRuntime.observeServerTick(270L, hardReceipt, 25_000_000L);
		double hardCorrected = ClientHudRuntime.estimatedServerTick(hardReceipt).orElseThrow();
		checkClose(270.5D, hardCorrected, 0.000_001D, "a large positive error must correct immediately");

		TimerValue countdown = new TimerValue(
			270L,
			100.0D,
			-1.0D,
			false,
			TimerFormat.MINUTES_SECONDS,
			Optional.empty()
		);
		long beforeNegativeCorrection = countdown.ticksAt(hardCorrected);
		long negativeReceipt = hardReceipt + 1_000_000_000L;
		ClientHudRuntime.observeServerTick(260L, negativeReceipt, 25_000_000L);
		double afterNegativeCorrection = ClientHudRuntime.estimatedServerTick(negativeReceipt).orElseThrow();
		check(
			afterNegativeCorrection >= hardCorrected,
			"a large negative correction must not move the visible server clock backwards"
		);
		check(
			countdown.ticksAt(afterNegativeCorrection) <= beforeNegativeCorrection,
			"a negative-rate countdown must not increase after clock correction"
		);

		ClientHudRuntime.clearAll();
		check(
			ClientHudRuntime.estimatedServerTick(negativeReceipt).isEmpty(),
			"clearAll must discard the server clock anchor"
		);
	}

	private static String title() {
		return ClientHudRuntime.snapshot().orElseThrow().title().getString();
	}

	private static double remaining() {
		return ClientHudRuntime.countdownSnapshot().orElseThrow().remaining().baseTicks();
	}

	private static byte[] dock(final String title) {
		return json("""
			{
			  "format_version": 1,
			  "surface": "dock",
			  "profile_id": "test:main",
			  "profile_content_version": 1,
			  "layout_id": "test:dock",
			  "mode": "normal",
			  "title": {"text": "%s"},
			  "body": {"text": "body"}
			}
			""".formatted(title));
	}

	private static byte[] dockPatch(final String title) {
		return json("""
			{
			  "format_version": 1,
			  "surface": "dock",
			  "title": {"text": "%s"}
			}
			""".formatted(title));
	}

	private static byte[] invalidMergedPatch() {
		return json("""
			{
			  "format_version": 1,
			  "surface": "dock",
			  "fields": "not-an-array"
			}
			""");
	}

	private static byte[] structureChangingPatch() {
		return json("""
			{
			  "format_version": 1,
			  "surface": "dock",
			  "body": null
			}
			""");
	}

	private static byte[] countdown(final long totalTicks, final double remaining) {
		return json("""
			{
			  "format_version": 1,
			  "surface": "countdown",
			  "countdown_instance_id": "%s",
			  "layout_id": "test:countdown",
			  "root": "root",
			  "nodes": [
			    {
			      "id": "root",
			      "type": "separator",
			      "priority": "critical",
			      "client_control": {
			        "label": {"text": "Countdown"},
			        "visible": true,
			        "allow_hide": false,
			        "allow_compact": false
			      },
			      "orientation": "horizontal",
			      "thickness": 1,
			      "color": "#40505F"
			    }
			  ],
			  "total_ticks": %d,
			  "remaining": {
			    "server_tick": 100,
			    "base_ticks": %s,
			    "rate": -1.0,
			    "paused": false,
			    "format": "m:ss"
			  }
			}
			""".formatted(COUNTDOWN, totalTicks, Double.toString(remaining)));
	}

	private static byte[] countdownPatch(final long serverTick, final double remaining) {
		return json("""
			{
			  "format_version": 1,
			  "surface": "countdown",
			  "remaining": {
			    "server_tick": %d,
			    "base_ticks": %s,
			    "rate": -1.0,
			    "paused": false,
			    "format": "m:ss"
			  }
			}
			""".formatted(serverTick, Double.toString(remaining)));
	}

	private static byte[] malformed() {
		return json("{not-json");
	}

	private static byte[] json(final String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static void checkResult(
		final FrameAcceptance expected,
		final FrameAcceptance actual,
		final String message
	) {
		if (actual != expected) {
			throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
		}
	}

	private static void checkClose(
		final double expected,
		final double actual,
		final double tolerance,
		final String message
	) {
		if (Math.abs(expected - actual) > tolerance) {
			throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
