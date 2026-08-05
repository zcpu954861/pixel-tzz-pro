package io.github.zcpu954861.pixeltzzpro.client.screen;

import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState;
import io.github.zcpu954861.pixeltzzpro.client.ClientSessionState.ConnectionStatus;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Action;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Boundary;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Identity;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Mode;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Scenario;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewRequestC2SPayload;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * One bounded, host-only development-preview namespace owned by a HUD settings session.
 *
 * <p>The UUID remains stable while the settings session is alive. Request sequences are shared
 * across settings sessions so reopening a surface on the same play connection can never reuse an
 * older counter. No caller-supplied text, selector, path, score, UUID target, or raw identifier is
 * accepted here; registered definition identifiers are deliberately left to the server's safe
 * automatic selection.</p>
 */
final class HudDeveloperPreviewSession {
	private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
	private static final long OPEN_TIMEOUT_NANOS = 5_000_000_000L;

	private final UUID previewId = UUID.randomUUID();
	private final EnumMap<Surface, SurfaceSession> surfaces = new EnumMap<>(Surface.class);
	private boolean available;

	HudDeveloperPreviewSession() {
		for (Surface surface : Surface.values()) {
			this.surfaces.put(surface, new SurfaceSession(Selection.defaults(surface)));
		}
		ClientHudRuntime.activatePreview(this.previewId);
		this.available = authorityAvailable();
	}

	UUID previewId() {
		return this.previewId;
	}

	boolean available() {
		return this.available;
	}

	State state(final Surface surface) {
		return surface(surface).state;
	}

	boolean canRequest(final Action action, final Surface surface) {
		if (!this.available) {
			return false;
		}
		return switch (Objects.requireNonNull(action, "action")) {
			case OPEN -> state(surface) == State.CLOSED;
			case REFRESH -> state(surface) == State.OPEN;
			case CLOSE -> state(surface) == State.OPENING || state(surface) == State.OPEN;
		};
	}

	/** Refreshes both the host/protocol gate and server-driven preview clear state. */
	boolean refresh() {
		boolean changed = false;
		boolean nextAvailable = authorityAvailable();
		if (this.available && !nextAvailable) {
			closeAllInternal();
			changed = true;
		}
		if (this.available != nextAvailable) {
			this.available = nextAvailable;
			changed = true;
		}
		if (!this.available) {
			return changed;
		}

		long now = System.nanoTime();
		for (Surface surface : Surface.values()) {
			SurfaceSession session = surface(surface);
			boolean projectionPresent = projectionPresent(surface);
			State next = switch (session.state) {
				case CLOSED -> State.CLOSED;
				case OPENING -> projectionPresent
					? State.OPEN
					: now - session.requestedAtNanos >= OPEN_TIMEOUT_NANOS ? State.CLOSED : State.OPENING;
				case OPEN -> projectionPresent ? State.OPEN : State.CLOSED;
				case CLOSING -> projectionPresent ? State.CLOSING : State.CLOSED;
			};
			if (next != session.state) {
				session.state = next;
				changed = true;
			}
		}
		return changed;
	}

	boolean request(
		final Action action,
		final Surface surface,
		final Selection selection
	) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(surface, "surface");
		Selection safeSelection = Objects.requireNonNull(selection, "selection").validated(surface);
		refresh();
		if (!canRequest(action, surface)) {
			return false;
		}
		if (!send(action, surface, safeSelection)) {
			return false;
		}

		SurfaceSession session = surface(surface);
		session.selection = safeSelection;
		session.requestedAtNanos = System.nanoTime();
		session.state = switch (action) {
			case OPEN -> State.OPENING;
			case REFRESH -> State.OPEN;
			case CLOSE -> projectionPresent(surface) ? State.CLOSING : State.CLOSED;
		};
		return true;
	}

	/** Closes both isolated surfaces and immediately removes their local projections. */
	void closeAll() {
		closeAllInternal();
	}

	private void closeAllInternal() {
		for (Surface surface : Surface.values()) {
			SurfaceSession session = surface(surface);
			if (session.state != State.CLOSED || projectionPresent(surface)) {
				send(Action.CLOSE, surface, session.selection);
			}
			session.state = State.CLOSED;
			session.requestedAtNanos = 0L;
		}
		ClientHudRuntime.clearPreview(this.previewId);
	}

	private boolean send(
		final Action action,
		final Surface surface,
		final Selection selection
	) {
		if (!channelAvailable()) {
			return false;
		}
		long sequence = nextRequestSequence();
		var snapshot = ClientSessionState.snapshot();
		try {
			ClientPlayNetworking.send(new HudPreviewRequestC2SPayload(
				NetworkProtocol.CURRENT_VERSION,
				sequence,
				this.previewId,
				action,
				surface,
				selection.scenario(),
				selection.identity(),
				selection.mode(),
				selection.boundary(),
				snapshot.phaseId(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			));
			return true;
		} catch (IllegalStateException ignored) {
			return false;
		}
	}

	private static long nextRequestSequence() {
		long value = REQUEST_SEQUENCE.updateAndGet(current -> current == Long.MAX_VALUE
			? Long.MAX_VALUE
			: current + 1L);
		if (value == Long.MAX_VALUE) {
			throw new IllegalStateException("HUD preview request sequence exhausted");
		}
		return value;
	}

	private static boolean authorityAvailable() {
		var snapshot = ClientSessionState.snapshot();
		return snapshot.status() == ConnectionStatus.READY
			&& snapshot.currentPlayerHost()
			&& NetworkProtocol.isCompatible(snapshot.serverProtocolVersion())
			&& channelAvailable();
	}

	private static boolean channelAvailable() {
		try {
			return ClientPlayNetworking.canSend(HudPreviewRequestC2SPayload.TYPE);
		} catch (IllegalStateException ignored) {
			return false;
		}
	}

	private static boolean projectionPresent(final Surface surface) {
		return switch (surface) {
			case HUD -> ClientHudRuntime.previewSnapshot().isPresent();
			case COUNTDOWN -> ClientHudRuntime.previewCountdownSnapshot().isPresent();
		};
	}

	private SurfaceSession surface(final Surface surface) {
		return this.surfaces.get(Objects.requireNonNull(surface, "surface"));
	}

	enum State {
		CLOSED,
		OPENING,
		OPEN,
		CLOSING
	}

	record Selection(
		Scenario scenario,
		Identity identity,
		Mode mode,
		Boundary boundary
	) {
		Selection {
			scenario = Objects.requireNonNull(scenario, "scenario");
			identity = Objects.requireNonNull(identity, "identity");
			mode = Objects.requireNonNull(mode, "mode");
			boundary = Objects.requireNonNull(boundary, "boundary");
		}

		Selection validated(final Surface surface) {
			if (scenario.countdown() != (surface == Surface.COUNTDOWN)) {
				throw new IllegalArgumentException("preview scenario does not match surface");
			}
			return this;
		}

		static Selection defaults(final Surface surface) {
			return new Selection(
				surface == Surface.HUD ? Scenario.TASK_RUNNING : Scenario.COUNTDOWN_START,
				Identity.HOST,
				Mode.NORMAL,
				Boundary.NORMAL
			);
		}
	}

	private static final class SurfaceSession {
		private State state = State.CLOSED;
		private Selection selection;
		private long requestedAtNanos;

		private SurfaceSession(final Selection selection) {
			this.selection = Objects.requireNonNull(selection, "selection");
		}
	}
}
