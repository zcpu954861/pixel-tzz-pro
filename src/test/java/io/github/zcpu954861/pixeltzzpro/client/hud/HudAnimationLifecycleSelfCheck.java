package io.github.zcpu954861.pixeltzzpro.client.hud;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeMeta;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeStyle;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Orientation;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ProgressNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TimerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Type;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Motion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ChangeTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.EnterTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ExitTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Priority;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerFormat;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerValue;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Version;
import io.github.zcpu954861.pixeltzzpro.client.hud.InformationDockOverlay.MotionSample;
import io.github.zcpu954861.pixeltzzpro.client.hud.InformationDockOverlay.SurfaceMotion;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload.Surface;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/** Executable checks for the V3C HUD/Countdown animation lifecycle and quiet-refresh contract. */
public final class HudAnimationLifecycleSelfCheck {
	private static final UUID GAME = UUID.fromString("3c000000-0000-0000-0000-000000000001");
	private static final LayoutTransition RISE = transition(EnterTransition.RISE_FADE);
	private static final LayoutTransition FOCUS = transition(EnterTransition.FOCUS_FADE);

	private HudAnimationLifecycleSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("HUD_ANIMATION_LIFECYCLE_SELF_CHECK=PASS");
	}

	public static void run() {
		checkHeartbeatDoesNotRestartEnter();
		checkMotionStrengthDegradation();
		checkHiddenAndLateRecoverySettle();
		checkExitReleasesOutgoingSnapshot();
		checkResyncSettleIsNamespacedAndOneShot();
		checkTimerAndProgressAnchorsAreVisuallyQuiet();
	}

	private static void checkHeartbeatDoesNotRestartEnter() {
		SurfaceMotion<Fixture> motion = motion();
		Fixture initial = fixture(1L, "layout", 10, RISE, 0L);
		MotionSample<Fixture> started = motion.sample(Optional.of(initial), Motion.FULL, 0L, true, false);
		check(started.current().isPresent() && close(started.currentOpacity(), 0.0F),
			"a real first frame must start the registered enter transition");

		Fixture heartbeat = fixture(2L, "layout", 10, RISE, 120_000_000L);
		motion.sample(Optional.of(heartbeat), Motion.FULL, 120_000_000L, true, false);
		MotionSample<Fixture> finished = motion.sample(
			Optional.of(heartbeat),
			Motion.FULL,
			230_000_000L,
			true,
			false
		);
		check(
			finished.previous().isEmpty()
				&& finished.current().orElseThrow().version().sequence() == 2L
				&& close(finished.currentOpacity(), 1.0F)
				&& close(finished.translateY(), 0.0F),
			"same-visual heartbeat restarted or prolonged enter motion"
		);

		Fixture changed = fixture(3L, "layout", 11, RISE, 240_000_000L);
		MotionSample<Fixture> change = motion.sample(
			Optional.of(changed),
			Motion.FULL,
			240_000_000L,
			true,
			false
		);
		check(change.previous().isPresent() && change.current().isPresent(),
			"a real visual change must retain one crossfade pair");
	}

	private static void checkMotionStrengthDegradation() {
		SurfaceMotion<Fixture> simplified = motion();
		Fixture focus = fixture(1L, "layout", 1, FOCUS, 0L);
		simplified.sample(Optional.of(focus), Motion.SIMPLIFIED, 0L, true, false);
		MotionSample<Fixture> safe = simplified.sample(
			Optional.of(focus),
			Motion.SIMPLIFIED,
			60_000_000L,
			true,
			false
		);
		check(
			safe.currentOpacity() > 0.0F
				&& safe.currentOpacity() < 1.0F
				&& close(safe.translateY(), 0.0F)
				&& close(safe.focusScale(), 1.0F),
			"Simplified must degrade registered movement/scale to a short opacity-only transition"
		);

		SurfaceMotion<Fixture> statik = motion();
		MotionSample<Fixture> initial = statik.sample(Optional.of(focus), Motion.STATIC, 0L, true, false);
		checkSteady(initial, "Static first frame");
		MotionSample<Fixture> changed = statik.sample(
			Optional.of(fixture(2L, "other", 2, RISE, 1L)),
			Motion.STATIC,
			1L,
			true,
			false
		);
		checkSteady(changed, "Static changed frame");
		MotionSample<Fixture> cleared = statik.sample(Optional.empty(), Motion.STATIC, 2L, true, false);
		check(cleared.previous().isEmpty() && cleared.current().isEmpty() && !cleared.visible(),
			"Static clear retained a fading or flashing snapshot");
	}

	private static void checkHiddenAndLateRecoverySettle() {
		SurfaceMotion<Fixture> hidden = motion();
		Fixture initial = fixture(1L, "layout", 1, RISE, 0L);
		hidden.sample(Optional.of(initial), Motion.FULL, 0L, true, false);
		MotionSample<Fixture> obscured = hidden.sample(
			Optional.of(fixture(2L, "changed", 2, RISE, 50_000_000L)),
			Motion.FULL,
			50_000_000L,
			false,
			false
		);
		check(!obscured.visible(), "a hidden Screen/F1 frame remained renderable");
		MotionSample<Fixture> restored = hidden.sample(
			Optional.of(fixture(2L, "changed", 2, RISE, 50_000_000L)),
			Motion.FULL,
			60_000_000L,
			true,
			false
		);
		checkSteady(restored, "Screen/F1 restoration");

		SurfaceMotion<Fixture> lowFps = motion();
		Fixture first = fixture(1L, "layout", 1, RISE, 0L);
		lowFps.sample(Optional.of(first), Motion.FULL, 0L, true, false);
		lowFps.sample(Optional.of(first), Motion.FULL, 230_000_000L, true, false);
		MotionSample<Fixture> caughtUp = lowFps.sample(
			Optional.of(fixture(2L, "changed", 2, RISE, 900_000_000L)),
			Motion.FULL,
			900_000_000L,
			true,
			false
		);
		checkSteady(caughtUp, "long render-gap recovery");

		SurfaceMotion<Fixture> firstSeenLate = motion();
		MotionSample<Fixture> late = firstSeenLate.sample(
			Optional.of(fixture(1L, "layout", 1, RISE, 0L)),
			Motion.FULL,
			600_000_000L,
			true,
			false
		);
		checkSteady(late, "first observation after a long hidden interval");

		SurfaceMotion<Fixture> resync = motion();
		resync.sample(Optional.of(first), Motion.FULL, 0L, true, false);
		resync.sample(Optional.of(first), Motion.FULL, 230_000_000L, true, false);
		MotionSample<Fixture> recovered = resync.sample(
			Optional.of(fixture(2L, "recovered", 2, RISE, 240_000_000L)),
			Motion.FULL,
			240_000_000L,
			true,
			true
		);
		checkSteady(recovered, "resync Replace");
	}

	private static void checkExitReleasesOutgoingSnapshot() {
		SurfaceMotion<Fixture> motion = motion();
		Fixture initial = fixture(1L, "layout", 1, RISE, 0L);
		motion.sample(Optional.of(initial), Motion.FULL, 0L, true, false);
		motion.sample(Optional.of(initial), Motion.FULL, 230_000_000L, true, false);
		MotionSample<Fixture> exiting = motion.sample(Optional.empty(), Motion.FULL, 240_000_000L, true, false);
		check(exiting.previous().isPresent() && exiting.current().isEmpty(),
			"registered exit fade did not retain the outgoing snapshot for its bounded lifetime");
		MotionSample<Fixture> released = motion.sample(Optional.empty(), Motion.FULL, 410_000_000L, true, false);
		check(released.previous().isEmpty() && released.current().isEmpty() && !released.visible(),
			"finished exit fade retained the old snapshot");
	}

	private static void checkResyncSettleIsNamespacedAndOneShot() {
		ClientHudRuntime.clearAll();
		Version version = version(7L);
		check(ClientHudRuntime.prepareResync(
			Surface.HUD,
			version.gameInstanceId(),
			version.definitionGeneration(),
			version.epoch(),
			version.contextVersion(),
			1L
		).isPresent(), "resync setup failed");
		check(ClientHudRuntime.markAcceptedRecoveryFrame(Surface.HUD, version),
			"accepted recovery Replace was not marked for a quiet settle");
		check(!ClientHudRuntime.consumePresentationSettle(Surface.COUNTDOWN, version),
			"HUD recovery leaked into the Countdown presentation namespace");
		check(ClientHudRuntime.consumePresentationSettle(Surface.HUD, version),
			"renderer could not consume the accepted recovery settle");
		check(!ClientHudRuntime.consumePresentationSettle(Surface.HUD, version),
			"recovery settle was not one-shot");
		ClientHudRuntime.clearAll();
	}

	private static void checkTimerAndProgressAnchorsAreVisuallyQuiet() {
		ProgressNode progressA = new ProgressNode(
			meta("progress", Type.PROGRESS),
			2.0D,
			10.0D,
			Orientation.HORIZONTAL,
			Optional.of(Component.literal("进度")),
			0,
			true,
			false
		);
		ProgressNode progressB = new ProgressNode(
			progressA.meta(),
			8.0D,
			10.0D,
			progressA.orientation(),
			progressA.label(),
			progressA.segments(),
			progressA.smooth(),
			progressA.spine()
		);
		check(HudVisualFingerprint.of(Map.of("progress", progressA))
			== HudVisualFingerprint.of(Map.of("progress", progressB)),
			"progress interpolation anchor entered the visual-change fingerprint");

		TimerNode timerA = new TimerNode(
			meta("timer", Type.TIMER),
			new TimerValue(100L, 1_200.0D, -1.0D, false, TimerFormat.MINUTES_SECONDS, Optional.empty()),
			Optional.of(Component.literal("剩余"))
		);
		TimerNode timerB = new TimerNode(
			timerA.meta(),
			new TimerValue(140L, 1_160.0D, -1.0D, false, TimerFormat.MINUTES_SECONDS, Optional.empty()),
			timerA.label()
		);
		check(HudVisualFingerprint.of(Map.of("timer", timerA))
			== HudVisualFingerprint.of(Map.of("timer", timerB)),
			"timer heartbeat anchor entered the visual-change fingerprint");
	}

	private static SurfaceMotion<Fixture> motion() {
		return new SurfaceMotion<>(
			Fixture::version,
			Fixture::structure,
			Fixture::fingerprint,
			Fixture::transition,
			Fixture::receivedAtNanos
		);
	}

	private static Fixture fixture(
		final long sequence,
		final String structure,
		final int fingerprint,
		final LayoutTransition transition,
		final long receivedAtNanos
	) {
		return new Fixture(version(sequence), structure, fingerprint, transition, receivedAtNanos);
	}

	private static Version version(final long sequence) {
		return new Version(GAME, 3L, 4L, 5L, sequence);
	}

	private static LayoutTransition transition(final EnterTransition enter) {
		return new LayoutTransition(
			enter,
			ChangeTransition.CROSSFADE_VALUES,
			ExitTransition.FADE,
			Optional.empty(),
			Optional.empty()
		);
	}

	private static NodeMeta meta(final String id, final Type type) {
		return new NodeMeta(
			id,
			type,
			Priority.PRIMARY,
			NodeStyle.defaults(),
			new ComponentControl(id, Component.literal(id), true, false, false, Optional.empty())
		);
	}

	private static void checkSteady(final MotionSample<Fixture> sample, final String label) {
		check(
			sample.previous().isEmpty()
				&& sample.current().isPresent()
				&& close(sample.currentOpacity(), 1.0F)
				&& close(sample.translateY(), 0.0F)
				&& close(sample.focusScale(), 1.0F),
			label + " replayed enter/change movement, scale or opacity"
		);
	}

	private static boolean close(final float actual, final float expected) {
		return Math.abs(actual - expected) < 0.0001F;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record Fixture(
		Version version,
		String structure,
		int fingerprint,
		LayoutTransition transition,
		long receivedAtNanos
	) {
	}
}
