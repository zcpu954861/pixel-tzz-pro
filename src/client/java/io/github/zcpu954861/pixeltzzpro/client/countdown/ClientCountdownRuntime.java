package io.github.zcpu954861.pixeltzzpro.client.countdown;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.State;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Version;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sequence-safe client clock for the independent V3C countdown surface.
 *
 * <p>Only immutable snapshots cross the networking/render boundary. Routine 20 Hz timing patches
 * adjust the local double-tick clock without rebuilding the surface; small clock error is slewed,
 * while identity changes, pauses and large corrections settle every digit immediately.</p>
 */
public final class ClientCountdownRuntime {
	private static final double TICK_NANOS = 50_000_000.0D;
	private static final double SMALL_CORRECTION_TICKS = 1.5D;
	private static final double MAX_ANIMATABLE_PATCH_CORRECTION_TICKS = 20.0D;
	private static final long CORRECTION_SLEW_NANOS = 300_000_000L;
	private static final long RESYNC_THROTTLE_NANOS = 1_000_000_000L;
	private static final int MAX_SOUND_KEYS = 128;

	private static ClientCountdownSnapshot active;
	private static Clock clock;
	private static long settleGeneration;
	private static long surfaceGeneration;
	private static long surfaceStartedAtNanos;
	private static ClearRequest clearRequest;
	private static Tombstone tombstone;
	private static ResyncKey lastResyncKey;
	private static long lastResyncAtNanos = Long.MIN_VALUE;
	private static final Set<SoundKey> PLAYED_SOUNDS = new LinkedHashSet<>();

	private ClientCountdownRuntime() {
	}

	public static synchronized void reset() {
		active = null;
		clock = null;
		clearRequest = null;
		tombstone = null;
		lastResyncKey = null;
		lastResyncAtNanos = Long.MIN_VALUE;
		PLAYED_SOUNDS.clear();
		settleGeneration++;
		surfaceGeneration++;
	}

	public static synchronized FrameAcceptance acceptReplace(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence,
		final byte[] snapshotJson,
		final long receivedAtNanos,
		final long estimatedOneWayDelayNanos
	) {
		Version version = new Version(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
		if (active != null && !versionIsStrictlyNewer(version, active.version())) {
			return FrameAcceptance.stale();
		}
		if (tombstone != null && !versionIsStrictlyNewer(version, tombstone.version())) {
			PixelTzzPro.LOGGER.debug(
				"Countdown Replace requires resync: received context={}, sequence={} behind clear context={}, sequence={}",
				version.contextVersion(),
				version.sequence(),
				tombstone.version().contextVersion(),
				tombstone.version().sequence()
			);
			return FrameAcceptance.resync();
		}
		final ClientCountdownSnapshot parsed;
		try {
			parsed = ClientCountdownSnapshot.parseReplace(version, snapshotJson, receivedAtNanos);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn("Rejected invalid V3C countdown Replace: {}", error.getMessage());
			return FrameAcceptance.resync();
		}
		/* A strictly newer full Replace is also the server's explicit re-authorization grant. */

		boolean sameInstance = active != null
			&& active.countdownInstanceId().equals(parsed.countdownInstanceId());
		boolean stateChanged = active == null || active.state() != parsed.state();
		ClockUpdate update = updateClock(parsed, receivedAtNanos, estimatedOneWayDelayNanos, sameInstance);
		active = parsed;
		clock = update.clock();
		clearRequest = null;
		tombstone = null;
		lastResyncKey = null;
		if (!sameInstance) {
			PLAYED_SOUNDS.clear();
			surfaceStartedAtNanos = receivedAtNanos;
			surfaceGeneration++;
		}
		if (!sameInstance || stateChanged || update.hardSnap()) {
			settleGeneration++;
		}
		return FrameAcceptance.accepted(update.hardSnap());
	}

	public static synchronized FrameAcceptance acceptPatch(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long baseSequence,
		final long sequence,
		final byte[] patchJson,
		final long receivedAtNanos,
		final long estimatedOneWayDelayNanos
	) {
		Version next = new Version(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
		if (active == null) {
			if (tombstone != null && !versionIsStrictlyNewer(next, tombstone.version())) {
				return FrameAcceptance.stale();
			}
			return FrameAcceptance.resync();
		}
		if (tombstone != null) {
			/* Patch has no countdown identity and therefore cannot replace a Clear tombstone. */
			return versionIsStrictlyNewer(next, tombstone.version())
				? FrameAcceptance.resync()
				: FrameAcceptance.stale();
		}
		Version current = active.version();
		boolean sameEnvelope = current.gameInstanceId().equals(next.gameInstanceId())
			&& current.definitionGeneration() == next.definitionGeneration()
			&& current.epoch() == next.epoch();
		if (!sameEnvelope) {
			return contextIsOlder(next, current)
				? FrameAcceptance.stale()
				: FrameAcceptance.resync();
		}
		if (next.contextVersion() < current.contextVersion()) {
			return FrameAcceptance.stale();
		}
		if (sequence <= current.sequence()) {
			return FrameAcceptance.stale();
		}
		if (baseSequence != current.sequence()) {
			return FrameAcceptance.resync();
		}

		final ClientCountdownSnapshot parsed;
		try {
			parsed = active.applyPatch(next, patchJson, receivedAtNanos);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn("Rejected invalid V3C countdown Patch: {}", error.getMessage());
			return FrameAcceptance.resync();
		}
		boolean stateChanged = active.state() != parsed.state();
		boolean pauseChanged = active.timing().paused() != parsed.timing().paused();
		ClockUpdate update = updateClock(parsed, receivedAtNanos, estimatedOneWayDelayNanos, true);
		active = parsed;
		clock = update.clock();
		clearRequest = null;
		lastResyncKey = null;
		if (stateChanged || pauseChanged || update.settleDigits()) {
			settleGeneration++;
		}
		return FrameAcceptance.accepted(update.hardSnap());
	}

	public static synchronized boolean acceptClear(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence,
		final long receivedAtNanos
	) {
		Version clear = new Version(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence
		);
		if (active == null) {
			if (tombstone == null) {
				tombstone = new Tombstone(clear);
				lastResyncKey = null;
				return true;
			}
			if (!versionIsStrictlyNewer(clear, tombstone.version())) {
				return false;
			}
			tombstone = new Tombstone(clear);
			lastResyncKey = null;
			return true;
		}
		Version current = active.version();
		Version floor = tombstone == null ? current : tombstone.version();
		if (!versionIsStrictlyNewer(clear, floor)) {
			return false;
		}
		tombstone = new Tombstone(clear);
		long holdNanos = active.state() == State.COMPLETED
			? active.presentation().completion().holdMs() * 1_000_000L
			: 0L;
		long exitAt = clearRequest == null
			? Math.max(receivedAtNanos, active.receivedAtNanos() + holdNanos)
			: clearRequest.exitAtNanos();
		clearRequest = new ClearRequest(clear, exitAt);
		lastResyncKey = null;
		settleGeneration++;
		return true;
	}

	/** Effective server-projected input gate; Clear disables it before the HUD exit animation ends. */
	public static synchronized boolean inventoryBlocked() {
		return active != null
			&& clearRequest == null
			&& !active.state().terminal()
			&& active.inventoryBlocked();
	}

	public static synchronized Optional<View> view(final long nowNanos) {
		if (active == null || clock == null) {
			return Optional.empty();
		}
		double remaining = Math.clamp(clock.remainingAt(nowNanos), 0.0D, active.totalTicks());
		double surfaceProgress = 1.0D;
		boolean exiting = false;
		if (clearRequest != null && nowNanos >= clearRequest.exitAtNanos()) {
			exiting = true;
			int durationMs = ClientCountdownPreferences.snapshot().motion()
				== ClientCountdownPreferences.Motion.STATIC
				? 0
				: active.presentation().exit().durationMs();
			if (durationMs <= 0) {
				finishClear();
				return Optional.empty();
			}
			double elapsed = (nowNanos - clearRequest.exitAtNanos()) / (durationMs * 1_000_000.0D);
			if (elapsed >= 1.0D) {
				finishClear();
				return Optional.empty();
			}
			surfaceProgress = 1.0D - Math.clamp(elapsed, 0.0D, 1.0D);
		} else {
			int durationMs = active.presentation().enter().durationMs();
			if (durationMs > 0) {
				surfaceProgress = Math.clamp(
					(nowNanos - surfaceStartedAtNanos) / (durationMs * 1_000_000.0D),
					0.0D,
					1.0D
				);
			}
		}
		return Optional.of(new View(
			active,
			remaining,
			settleGeneration,
			surfaceGeneration,
			surfaceProgress,
			exiting
		));
	}

	public static synchronized OptionalLong prepareResync(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long nowNanos
	) {
		Version appliedVersion = active != null
			? active.version()
			: tombstone != null ? tombstone.version() : null;
		boolean matchingEnvelope = appliedVersion != null
			&& appliedVersion.gameInstanceId().equals(gameInstanceId)
			&& appliedVersion.definitionGeneration() == definitionGeneration
			&& appliedVersion.epoch() == epoch;
		long appliedSequence = matchingEnvelope ? appliedVersion.sequence() : 0L;
		ResyncKey key = new ResyncKey(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			appliedSequence
		);
		if (
			key.equals(lastResyncKey)
				&& nowNanos - lastResyncAtNanos >= 0L
				&& nowNanos - lastResyncAtNanos < RESYNC_THROTTLE_NANOS
		) {
			return OptionalLong.empty();
		}
		lastResyncKey = key;
		lastResyncAtNanos = nowNanos;
		return OptionalLong.of(appliedSequence);
	}

	public static synchronized void playCheckpointSound(
		final UUID countdownInstanceId,
		final long stateVersion,
		final String checkpointId,
		final Identifier soundId,
		final float volume,
		final float pitch
	) {
		Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
		Objects.requireNonNull(checkpointId, "checkpointId");
		Objects.requireNonNull(soundId, "soundId");
		if (active == null || !active.countdownInstanceId().equals(countdownInstanceId)) {
			return;
		}
		SoundKey key = new SoundKey(countdownInstanceId, stateVersion, checkpointId);
		if (!PLAYED_SOUNDS.add(key)) {
			return;
		}
		while (PLAYED_SOUNDS.size() > MAX_SOUND_KEYS) {
			PLAYED_SOUNDS.remove(PLAYED_SOUNDS.iterator().next());
		}
		float localVolume = volume * ClientCountdownPreferences.snapshot().volume();
		if (localVolume <= 0.0F) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getSoundManager().getSoundEvent(soundId) == null) {
			return;
		}
		client.getSoundManager().play(
			SimpleSoundInstance.forUI(
				SoundEvent.createVariableRangeEvent(soundId),
				pitch,
				localVolume
			)
		);
	}

	private static ClockUpdate updateClock(
		final ClientCountdownSnapshot snapshot,
		final long receivedAtNanos,
		final long estimatedOneWayDelayNanos,
		final boolean sameInstance
	) {
		double rate = snapshot.timing().paused() ? 0.0D : snapshot.timing().rate();
		double authoritativeNow = snapshot.timing().baseRemainingTicks()
			+ rate * Math.max(0L, estimatedOneWayDelayNanos) / TICK_NANOS;
		authoritativeNow = Math.clamp(authoritativeNow, 0.0D, snapshot.totalTicks());
		if (!sameInstance || clock == null) {
			return new ClockUpdate(
				new Clock(authoritativeNow, receivedAtNanos, rate, 0.0D, 0L),
				true,
				true
			);
		}
		double predicted = Math.clamp(clock.remainingAt(receivedAtNanos), 0.0D, snapshot.totalTicks());
		double error = authoritativeNow - predicted;
		boolean rateChanged = Math.abs(clock.rate() - rate) > 0.000_001D;
		if (!rateChanged && Math.abs(error) <= SMALL_CORRECTION_TICKS) {
			return new ClockUpdate(
				new Clock(predicted, receivedAtNanos, rate, error, CORRECTION_SLEW_NANOS),
				false,
				false
			);
		}
		/*
		 * The first heartbeat after a freshly authorized countdown can be one whole
		 * second away from the optimistic local anchor. Snapping the clock is still
		 * correct, but settling the reel at the same time suppresses the first visible
		 * 01:02 -> 01:01 transition. A bounded Patch may therefore keep the current
		 * digit presentation and animate once toward the newest value. Replace, state
		 * and pause boundaries still settle at their callers, while larger corrections
		 * remain hard visual snaps so reconnect/resync never replays missed seconds.
		 */
		boolean sameRate = !rateChanged;
		boolean openingGateReleased = clock.rate() == 0.0D && rate < 0.0D;
		boolean boundedRunningCorrection = snapshot.state() == State.RUNNING
			&& rate < 0.0D
			&& Math.abs(error)
			<= MAX_ANIMATABLE_PATCH_CORRECTION_TICKS
			&& (sameRate || openingGateReleased);
		return new ClockUpdate(
			new Clock(authoritativeNow, receivedAtNanos, rate, 0.0D, 0L),
			true,
			!boundedRunningCorrection
		);
	}

	private static boolean versionIsStrictlyNewer(final Version candidate, final Version current) {
		if (!candidate.gameInstanceId().equals(current.gameInstanceId())) {
			/*
			 * Game UUIDs have no intrinsic order. A different game may replace the current one only
			 * when the server's monotonic generation/epoch envelope proves it is newer. Equal
			 * envelopes are ambiguous and therefore fail closed instead of allowing a delayed old-game
			 * frame to overwrite the active projection.
			 */
			int generation = Long.compare(
				candidate.definitionGeneration(),
				current.definitionGeneration()
			);
			if (generation != 0) {
				return generation > 0;
			}
			return candidate.epoch() > current.epoch();
		}
		int context = compareContext(candidate, current);
		return context > 0 || context == 0 && candidate.sequence() > current.sequence();
	}

	private static boolean contextIsOlder(final Version candidate, final Version current) {
		return candidate.gameInstanceId().equals(current.gameInstanceId())
			&& compareContext(candidate, current) < 0;
	}

	private static int compareContext(final Version left, final Version right) {
		int generation = Long.compare(left.definitionGeneration(), right.definitionGeneration());
		if (generation != 0) {
			return generation;
		}
		int epoch = Long.compare(left.epoch(), right.epoch());
		if (epoch != 0) {
			return epoch;
		}
		return Long.compare(left.contextVersion(), right.contextVersion());
	}

	private static void finishClear() {
		active = null;
		clock = null;
		clearRequest = null;
		PLAYED_SOUNDS.clear();
		surfaceGeneration++;
		settleGeneration++;
	}

	public record FrameAcceptance(boolean accepted, boolean requiresResync, boolean hardSnap) {
		private static FrameAcceptance accepted(final boolean hardSnap) {
			return new FrameAcceptance(true, false, hardSnap);
		}

		private static FrameAcceptance stale() {
			return new FrameAcceptance(false, false, false);
		}

		private static FrameAcceptance resync() {
			return new FrameAcceptance(false, true, true);
		}
	}

	public record View(
		ClientCountdownSnapshot snapshot,
		double remainingTicks,
		long settleGeneration,
		long surfaceGeneration,
		double surfaceProgress,
		boolean exiting
	) {
		public View {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			if (!Double.isFinite(remainingTicks) || remainingTicks < 0.0D
				|| !Double.isFinite(surfaceProgress) || surfaceProgress < 0.0D || surfaceProgress > 1.0D) {
				throw new IllegalArgumentException("countdown view is invalid");
			}
		}
	}

	private record Clock(
		double remainingAtAnchor,
		long anchorNanos,
		double rate,
		double correctionTicks,
		long correctionDurationNanos
	) {
		private double remainingAt(final long nowNanos) {
			long elapsed = Math.max(0L, nowNanos - this.anchorNanos);
			double correctionProgress = this.correctionDurationNanos <= 0L
				? 1.0D
				: Math.clamp((double)elapsed / this.correctionDurationNanos, 0.0D, 1.0D);
			return this.remainingAtAnchor
				+ this.rate * elapsed / TICK_NANOS
				+ this.correctionTicks * correctionProgress;
		}
	}

	private record ClockUpdate(Clock clock, boolean hardSnap, boolean settleDigits) {
	}

	private record ClearRequest(Version version, long exitAtNanos) {
	}

	private record Tombstone(Version version) {
		private Tombstone {
			version = Objects.requireNonNull(version, "version");
		}
	}

	private record ResyncKey(
		UUID gameInstanceId,
		long definitionGeneration,
		long epoch,
		long contextVersion,
		long appliedSequence
	) {
	}

	private record SoundKey(UUID countdownInstanceId, long stateVersion, String checkpointId) {
	}
}
