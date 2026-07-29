package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload.TaskView;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.TimelineClockProjection;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

/**
 * Read-only timeline request sequencer and client projection.
 */
public final class ClientTimelineState {
	private static final long REQUEST_TIMEOUT_MILLIS = 5_000L;
	private static final long MINIMUM_REQUEST_INTERVAL_MILLIS = 400L;
	private static long nextRequestSequence = 1L;
	private static long pendingSequence = -1L;
	private static long pendingSinceMillis;
	private static boolean pendingVisible;
	private static long lastRequestMillis = Long.MIN_VALUE;
	private static long acceptedAtMillis;
	private static long revision;
	private static TimelineViewS2CPayload snapshot;

	private ClientTimelineState() {
	}

	public static synchronized void beginConnection() {
		reset();
	}

	public static synchronized void disconnect() {
		reset();
	}

	public static synchronized boolean request() {
		return request(false, true);
	}

	public static synchronized boolean requestIfStale(final long maximumAgeMillis) {
		long now = Util.getMillis();
		if (snapshot != null && now - acceptedAtMillis < Math.max(0L, maximumAgeMillis)) {
			return false;
		}
		return request(false, false);
	}

	public static synchronized boolean probe() {
		return request(false, false);
	}

	private static boolean request(final boolean force, final boolean visible) {
		if (!ClientPlayNetworking.canSend(TimelineViewRequestC2SPayload.TYPE)) {
			return false;
		}
		long now = Util.getMillis();
		if (pendingSequence >= 0L && now - pendingSinceMillis < REQUEST_TIMEOUT_MILLIS) {
			if (visible && !pendingVisible) {
				pendingVisible = true;
				revision++;
			}
			return false;
		}
		if (!force && lastRequestMillis != Long.MIN_VALUE
			&& now - lastRequestMillis < MINIMUM_REQUEST_INTERVAL_MILLIS) {
			return false;
		}
		long sequence = nextRequestSequence++;
		pendingSequence = sequence;
		pendingSinceMillis = now;
		pendingVisible = visible;
		lastRequestMillis = now;
		ClientPlayNetworking.send(
			new TimelineViewRequestC2SPayload(NetworkProtocol.CURRENT_VERSION, sequence)
		);
		if (visible) {
			revision++;
		}
		return true;
	}

	public static synchronized void accept(final TimelineViewS2CPayload payload) {
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			return;
		}
		boolean pushed = payload.requestSequence() == 0L;
		if (!pushed && payload.requestSequence() != pendingSequence) {
			return;
		}
		snapshot = payload;
		acceptedAtMillis = Util.getMillis();
		if (pushed || payload.requestSequence() == pendingSequence) {
			pendingSequence = -1L;
			pendingSinceMillis = 0L;
			pendingVisible = false;
		}
		revision++;
	}

	public static synchronized Optional<TimelineViewS2CPayload> snapshot() {
		return Optional.ofNullable(snapshot);
	}

	public static synchronized long displayedGameElapsedTicks() {
		if (snapshot == null || !snapshot.status().equals("ready")) {
			return 0L;
		}
		return project(snapshot.gameElapsedTicks(), gameClockAdvancing(snapshot));
	}

	public static synchronized long displayedTaskElapsedTicks(final TaskView task) {
		boolean advancing = snapshot != null
			&& snapshot.status().equals("ready")
			&& !snapshot.paused()
			&& snapshot.timelineStatus().equals("running")
			&& task.routeState().equals("current")
			&& task.taskStatus().equals("running");
		long projected = project(task.elapsedTicks(), advancing);
		return task.durationTicks().map(duration -> Math.min(duration, projected)).orElse(projected);
	}

	public static synchronized long displayedIntermissionElapsedTicks(final TaskView task) {
		if (task.intermission().isEmpty()) {
			return 0L;
		}
		var intermission = task.intermission().orElseThrow();
		boolean advancing = snapshot != null
			&& snapshot.status().equals("ready")
			&& !snapshot.paused()
			&& snapshot.timelineStatus().equals("intermission")
			&& task.routeState().equals("current")
			&& !intermission.paused();
		return Math.min(
			intermission.durationTicks(),
			project(intermission.elapsedTicks(), advancing)
		);
	}

	public static synchronized boolean recapAvailable() {
		return snapshot != null
			&& snapshot.status().equals("ready")
			&& snapshot.recapView();
	}

	public static synchronized boolean pending() {
		return pendingVisible
			&& pendingSequence >= 0L
			&& Util.getMillis() - pendingSinceMillis < REQUEST_TIMEOUT_MILLIS;
	}

	public static synchronized boolean synchronizedResponse() {
		return snapshot != null;
	}

	public static synchronized long revision() {
		return revision;
	}

	private static boolean gameClockAdvancing(final TimelineViewS2CPayload payload) {
		if (payload.paused()) {
			return false;
		}
		Optional<TaskView> current = payload.tasks()
			.stream()
			.filter(task -> task.routeState().equals("current"))
			.findFirst();
		return switch (payload.timelineStatus()) {
			case "running" -> current.map(TaskView::countsTowardGameTime).orElse(false);
			case "intermission" -> current
				.flatMap(TaskView::intermission)
				.filter(value -> !value.paused())
				.map(value -> value.countsTowardGameTime())
				.orElse(false);
			default -> false;
		};
	}

	private static long project(final long baseTicks, final boolean advancing) {
		return TimelineClockProjection.project(
			baseTicks,
			acceptedAtMillis,
			Util.getMillis(),
			advancing
		);
	}

	private static void reset() {
		nextRequestSequence = 1L;
		pendingSequence = -1L;
		pendingSinceMillis = 0L;
		pendingVisible = false;
		lastRequestMillis = Long.MIN_VALUE;
		acceptedAtMillis = 0L;
		snapshot = null;
		revision++;
	}
}
