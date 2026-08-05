package io.github.zcpu954861.pixeltzzpro.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Alignment;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BackgroundNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BadgeNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ContainerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.CounterNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ImageNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ImageFit;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Insets;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeMeta;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeStyle;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Orientation;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Overflow;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.PlayerHeadNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ProgressNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.SeparatorNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TextNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TimerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Type;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Anchor;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ClientPolicy;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.FieldValue;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ChangeTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.EnterTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ExitTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Growth;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.HudSound;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutSize;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutVariant;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Mode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Priority;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ProgressValue;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.SpineValue;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TabCollision;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerFormat;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerValue;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Version;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.ProfileKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload.Surface;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Single client wire boundary for V3C HUD, countdown and isolated preview projections.
 *
 * <p>Every public accept method fully decodes and validates into a new immutable model before one
 * atomic swap. Invalid, duplicate, stale, cross-epoch and base-mismatched frames leave the current
 * projection untouched.</p>
 */
public final class ClientHudRuntime {
	public static final int MAX_JSON_BYTES = 131_072;
	public static final int MAX_NODES = 256;
	private static final int MAX_DEPTH = 16;
	private static final int MAX_RESYNC_NAMESPACES = 128;
	private static final long RESYNC_THROTTLE_NANOS = 2_000_000_000L;
	private static final double NANOS_PER_TICK = 50_000_000.0D;
	private static final double CLOCK_HARD_SNAP_TICKS = 4.0D;
	private static final double CLOCK_RESPONSE_PER_SECOND = 1.25D;
	private static final double CLOCK_MAX_SLEW_TICKS_PER_SECOND = 0.75D;
	private static final AtomicReference<DockState> DOCK = new AtomicReference<>();
	private static final AtomicReference<CountdownState> COUNTDOWN = new AtomicReference<>();
	private static final AtomicReference<PreviewDockState> HUD_PREVIEW = new AtomicReference<>();
	private static final AtomicReference<PreviewCountdownState> COUNTDOWN_PREVIEW = new AtomicReference<>();
	/** Preview packets are accepted only for the settings session that explicitly owns them. */
	private static UUID activePreviewId;
	private static final Set<String> PLAYED_COUNTDOWN_CHECKPOINTS = new LinkedHashSet<>();
	private static final LinkedHashMap<ResyncNamespace, Long> RESYNC_ATTEMPTS =
		new LinkedHashMap<>(16, 0.75F, true);
	/** Exact namespaces waiting for a full recovery frame after a client resync request. */
	private static final LinkedHashSet<ResyncNamespace> PENDING_RESYNC_REPLACES = new LinkedHashSet<>();
	/** Recovered namespaces whose next rendered state must settle without enter/change effects. */
	private static final LinkedHashSet<ResyncNamespace> PENDING_PRESENTATION_SETTLES = new LinkedHashSet<>();
	private static final Object SERVER_CLOCK_LOCK = new Object();
	private static ServerClock serverClock;

	private ClientHudRuntime() {
	}

	public static FrameAcceptance acceptReplace(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence,
		final byte[] json
	) {
		return acceptReplace(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence,
			json,
			System.nanoTime()
		);
	}

	public static FrameAcceptance acceptReplace(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence,
		final byte[] json,
		final long receivedAtNanos
	) {
		Version version = version(gameInstanceId, definitionGeneration, epoch, contextVersion, sequence);
		DockState observed = DOCK.get();
		if (!replaceAllowed(observed == null ? null : observed.version(), version)) {
			return FrameAcceptance.STALE;
		}
		final JsonObject document;
		final ClientHudSnapshot decoded;
		try {
			document = document(json);
			decoded = parseDock(document, version, receivedAtNanos);
		} catch (RuntimeException rejected) {
			return FrameAcceptance.DECODE_INVALID;
		}
		while (true) {
			DockState current = DOCK.get();
			if (!replaceAllowed(current == null ? null : current.version(), version)) {
				return FrameAcceptance.STALE;
			}
			DockState next = new DockState(version, Optional.of(decoded), document);
			if (DOCK.compareAndSet(current, next)) {
				boolean settlePresentation = markAcceptedRecoveryFrame(Surface.HUD, version);
				onDockAccepted(
					current == null ? null : current.snapshot().orElse(null),
					decoded,
					settlePresentation
				);
				return FrameAcceptance.ACCEPTED;
			}
		}
	}

	public static FrameAcceptance acceptPatch(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long baseSequence,
		final long sequence,
		final byte[] json
	) {
		return acceptPatch(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			baseSequence,
			sequence,
			json,
			System.nanoTime()
		);
	}

	public static FrameAcceptance acceptPatch(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long baseSequence,
		final long sequence,
		final byte[] json,
		final long receivedAtNanos
	) {
		Version nextVersion = version(gameInstanceId, definitionGeneration, epoch, contextVersion, sequence);
		JsonObject patch = null;
		while (true) {
			DockState current = DOCK.get();
			FrameAcceptance prerequisite = patchPrerequisite(
				current == null ? null : current.version(),
				current == null || current.snapshot().isEmpty(),
				nextVersion,
				baseSequence
			);
			if (prerequisite != FrameAcceptance.ACCEPTED) {
				return prerequisite;
			}
			try {
				if (patch == null) {
					patch = document(json);
					validatePatchEnvelope(patch, "dock");
				}
				JsonObject merged = merge(current.document(), patch, DOCK_PATCH_KEYS);
				ClientHudSnapshot decoded = parseDock(merged, nextVersion, receivedAtNanos);
				if (!sameStructure(current.snapshot().orElseThrow(), decoded)) {
					return FrameAcceptance.STRUCTURE_MISMATCH;
				}
				DockState next = new DockState(nextVersion, Optional.of(decoded), merged);
				if (DOCK.compareAndSet(current, next)) {
					onDockAccepted(
						current.snapshot().orElse(null),
						decoded,
						presentationSettlePending(Surface.HUD, nextVersion)
					);
					return FrameAcceptance.ACCEPTED;
				}
			} catch (RuntimeException rejected) {
				return FrameAcceptance.DECODE_INVALID;
			}
		}
	}

	public static boolean acceptClear(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence
	) {
		Version version = version(gameInstanceId, definitionGeneration, epoch, contextVersion, sequence);
		while (true) {
			DockState current = DOCK.get();
			if (!replaceAllowed(current == null ? null : current.version(), version)) {
				return false;
			}
			DockState next = new DockState(version, Optional.empty(), new JsonObject());
			if (DOCK.compareAndSet(current, next)) {
				markAcceptedRecoveryFrame(Surface.HUD, version);
				ClientHudPreferences.deactivateProfile();
				return true;
			}
		}
	}

	public static FrameAcceptance acceptCountdownReplace(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence,
		final byte[] json
	) {
		return acceptCountdownReplace(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			sequence,
			json,
			System.nanoTime()
		);
	}

	public static FrameAcceptance acceptCountdownReplace(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence,
		final byte[] json,
		final long receivedAtNanos
	) {
		Version version = version(gameInstanceId, definitionGeneration, epoch, contextVersion, sequence);
		CountdownState observed = COUNTDOWN.get();
		if (!replaceAllowed(observed == null ? null : observed.version(), version)) {
			return FrameAcceptance.STALE;
		}
		final JsonObject document;
		final ClientCountdownSnapshot decoded;
		try {
			document = document(json);
			decoded = parseCountdown(document, version, receivedAtNanos);
		} catch (RuntimeException rejected) {
			return FrameAcceptance.DECODE_INVALID;
		}
		while (true) {
			CountdownState current = COUNTDOWN.get();
			if (!replaceAllowed(current == null ? null : current.version(), version)) {
				return FrameAcceptance.STALE;
			}
			CountdownState next = new CountdownState(version, Optional.of(decoded), document);
			if (COUNTDOWN.compareAndSet(current, next)) {
				boolean settlePresentation = markAcceptedRecoveryFrame(Surface.COUNTDOWN, version);
				onCountdownAccepted(
					current == null ? null : current.snapshot().orElse(null),
					decoded,
					settlePresentation
				);
				return FrameAcceptance.ACCEPTED;
			}
		}
	}

	public static FrameAcceptance acceptCountdownPatch(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long baseSequence,
		final long sequence,
		final byte[] json
	) {
		return acceptCountdownPatch(
			gameInstanceId,
			definitionGeneration,
			epoch,
			contextVersion,
			baseSequence,
			sequence,
			json,
			System.nanoTime()
		);
	}

	public static FrameAcceptance acceptCountdownPatch(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long baseSequence,
		final long sequence,
		final byte[] json,
		final long receivedAtNanos
	) {
		Version nextVersion = version(gameInstanceId, definitionGeneration, epoch, contextVersion, sequence);
		JsonObject patch = null;
		while (true) {
			CountdownState current = COUNTDOWN.get();
			FrameAcceptance prerequisite = patchPrerequisite(
				current == null ? null : current.version(),
				current == null || current.snapshot().isEmpty(),
				nextVersion,
				baseSequence
			);
			if (prerequisite != FrameAcceptance.ACCEPTED) {
				return prerequisite;
			}
			try {
				if (patch == null) {
					patch = document(json);
					validatePatchEnvelope(patch, "countdown");
				}
				JsonObject merged = merge(current.document(), patch, COUNTDOWN_PATCH_KEYS);
				ClientCountdownSnapshot decoded = parseCountdown(merged, nextVersion, receivedAtNanos);
				if (!current.snapshot().orElseThrow().structureKey().equals(decoded.structureKey())) {
					return FrameAcceptance.STRUCTURE_MISMATCH;
				}
				CountdownState next = new CountdownState(nextVersion, Optional.of(decoded), merged);
				if (COUNTDOWN.compareAndSet(current, next)) {
					onCountdownAccepted(
						current.snapshot().orElse(null),
						decoded,
						presentationSettlePending(Surface.COUNTDOWN, nextVersion)
					);
					return FrameAcceptance.ACCEPTED;
				}
			} catch (RuntimeException rejected) {
				return FrameAcceptance.DECODE_INVALID;
			}
		}
	}

	public static boolean acceptCountdownClear(
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long sequence
	) {
		Version version = version(gameInstanceId, definitionGeneration, epoch, contextVersion, sequence);
		while (true) {
			CountdownState current = COUNTDOWN.get();
			if (!replaceAllowed(current == null ? null : current.version(), version)) {
				return false;
			}
			CountdownState next = new CountdownState(version, Optional.empty(), new JsonObject());
			if (COUNTDOWN.compareAndSet(current, next)) {
				markAcceptedRecoveryFrame(Surface.COUNTDOWN, version);
				return true;
			}
		}
	}

	public static synchronized boolean acceptPreviewReplace(
		final io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface surface,
		final UUID previewId,
		final long epoch,
		final long sequence,
		final byte[] json
	) {
		Objects.requireNonNull(previewId, "previewId");
		if (epoch < 0L || sequence < 0L) {
			return false;
		}
		if (!previewId.equals(activePreviewId)) {
			return false;
		}
		Objects.requireNonNull(surface, "surface");
		Version synthetic = new Version(previewId, 0L, epoch, 0L, sequence);
		try {
			JsonObject document = document(json);
			return switch (surface) {
				case HUD -> acceptHudPreview(
					previewId,
					epoch,
					sequence,
					parseDock(document, synthetic, System.nanoTime())
				);
				case COUNTDOWN -> acceptCountdownPreview(
					previewId,
					epoch,
					sequence,
					parseCountdown(document, synthetic, System.nanoTime())
				);
			};
		} catch (RuntimeException rejected) {
			return false;
		}
	}

	private static boolean acceptHudPreview(
		final UUID previewId,
		final long epoch,
		final long sequence,
		final ClientHudSnapshot decoded
	) {
		while (true) {
			PreviewDockState current = HUD_PREVIEW.get();
			if (!previewAllowed(current, previewId, epoch, sequence)) {
				return false;
			}
			PreviewDockState next = new PreviewDockState(
				previewId,
				epoch,
				sequence,
				Optional.of(decoded)
			);
			if (HUD_PREVIEW.compareAndSet(current, next)) {
				return true;
			}
		}
	}

	private static boolean acceptCountdownPreview(
		final UUID previewId,
		final long epoch,
		final long sequence,
		final ClientCountdownSnapshot decoded
	) {
		while (true) {
			PreviewCountdownState current = COUNTDOWN_PREVIEW.get();
			if (!previewAllowed(current, previewId, epoch, sequence)) {
				return false;
			}
			PreviewCountdownState next = new PreviewCountdownState(
				previewId,
				epoch,
				sequence,
				Optional.of(decoded)
			);
			if (COUNTDOWN_PREVIEW.compareAndSet(current, next)) {
				return true;
			}
		}
	}

	private static boolean previewAllowed(
		final PreviewVersion current,
		final UUID previewId,
		final long epoch,
		final long sequence
	) {
		return current == null
			|| current.previewId().equals(previewId) && (
				epoch > current.epoch()
					|| epoch == current.epoch() && sequence > current.sequence()
			);
	}

	public static Optional<ClientHudSnapshot> snapshot() {
		DockState value = DOCK.get();
		return value == null ? Optional.empty() : value.snapshot();
	}

	public static Optional<Version> dockVersion() {
		DockState value = DOCK.get();
		return value == null ? Optional.empty() : Optional.of(value.version());
	}

	public static Optional<ClientCountdownSnapshot> countdownSnapshot() {
		CountdownState value = COUNTDOWN.get();
		return value == null ? Optional.empty() : value.snapshot();
	}

	public static Optional<Version> countdownVersion() {
		CountdownState value = COUNTDOWN.get();
		return value == null ? Optional.empty() : Optional.of(value.version());
	}

	/**
	 * Reserves one bounded full-frame recovery request for an exact wire namespace.
	 *
	 * <p>The returned sequence is meaningful only when the installed projection belongs to that
	 * exact namespace. A Patch for a new game, generation, epoch or context therefore requests a
	 * full Replace from sequence zero instead of leaking the previous namespace's sequence.</p>
	 */
	public static synchronized OptionalLong prepareResync(
		final Surface surface,
		final UUID gameInstanceId,
		final long definitionGeneration,
		final long epoch,
		final long contextVersion,
		final long nowNanos
	) {
		Objects.requireNonNull(surface, "surface");
		Version requested = version(gameInstanceId, definitionGeneration, epoch, contextVersion, 0L);
		ResyncNamespace namespace = new ResyncNamespace(
			surface,
			requested.gameInstanceId(),
			requested.definitionGeneration(),
			requested.epoch(),
			requested.contextVersion()
		);
		Long previous = RESYNC_ATTEMPTS.get(namespace);
		if (
			previous != null
				&& nowNanos >= previous
				&& nowNanos - previous < RESYNC_THROTTLE_NANOS
		) {
			return OptionalLong.empty();
		}

		Version installed = (surface == Surface.HUD ? dockVersion() : countdownVersion()).orElse(null);
		long appliedSequence = installed != null && sameNamespace(installed, requested)
			? installed.sequence()
			: 0L;
		RESYNC_ATTEMPTS.put(namespace, nowNanos);
		PENDING_RESYNC_REPLACES.add(namespace);
		while (RESYNC_ATTEMPTS.size() > MAX_RESYNC_NAMESPACES) {
			var iterator = RESYNC_ATTEMPTS.entrySet().iterator();
			iterator.next();
			iterator.remove();
		}
		trimNamespaces(PENDING_RESYNC_REPLACES);
		return OptionalLong.of(appliedSequence);
	}

	/**
	 * Consumes the one-shot visual settle attached to an accepted resync response.
	 *
	 * <p>Package-private by design: only the client HUD renderer may translate wire recovery into
	 * presentation state. A later Patch in the same namespace is also covered when the render loop
	 * did not observe the recovery Replace yet.</p>
	 */
	static synchronized boolean consumePresentationSettle(
		final Surface surface,
		final Version version
	) {
		Objects.requireNonNull(surface, "surface");
		Objects.requireNonNull(version, "version");
		return PENDING_PRESENTATION_SETTLES.remove(resyncNamespace(surface, version));
	}

	static synchronized boolean markAcceptedRecoveryFrame(
		final Surface surface,
		final Version version
	) {
		ResyncNamespace namespace = resyncNamespace(surface, version);
		if (PENDING_RESYNC_REPLACES.remove(namespace)) {
			PENDING_PRESENTATION_SETTLES.add(namespace);
			trimNamespaces(PENDING_PRESENTATION_SETTLES);
			return true;
		}
		return PENDING_PRESENTATION_SETTLES.contains(namespace);
	}

	private static synchronized boolean presentationSettlePending(
		final Surface surface,
		final Version version
	) {
		return PENDING_PRESENTATION_SETTLES.contains(resyncNamespace(surface, version));
	}

	private static ResyncNamespace resyncNamespace(final Surface surface, final Version version) {
		return new ResyncNamespace(
			surface,
			version.gameInstanceId(),
			version.definitionGeneration(),
			version.epoch(),
			version.contextVersion()
		);
	}

	private static void trimNamespaces(final LinkedHashSet<ResyncNamespace> namespaces) {
		while (namespaces.size() > MAX_RESYNC_NAMESPACES) {
			var iterator = namespaces.iterator();
			iterator.next();
			iterator.remove();
		}
	}

	/** Commits one accepted formal frame's timer sample to the shared monotonic server clock. */
	public static void observeAcceptedServerTick(
		final Surface surface,
		final long receivedAtNanos,
		final long oneWayDelayNanos
	) {
		Objects.requireNonNull(surface, "surface");
		OptionalLong sample;
		if (surface == Surface.COUNTDOWN) {
			CountdownState value = COUNTDOWN.get();
			ClientCountdownSnapshot snapshot = value == null ? null : value.snapshot().orElse(null);
			sample = snapshot == null
				? OptionalLong.empty()
				: OptionalLong.of(snapshot.remaining().serverTick());
		} else {
			DockState value = DOCK.get();
			ClientHudSnapshot snapshot = value == null ? null : value.snapshot().orElse(null);
			Optional<Long> serverTick = snapshot == null ? Optional.empty() : serverTick(snapshot);
			sample = serverTick.isPresent()
				? OptionalLong.of(serverTick.orElseThrow())
				: OptionalLong.empty();
		}
		sample.ifPresent(serverTick -> observeServerTick(serverTick, receivedAtNanos, oneWayDelayNanos));
	}

	/**
	 * Observes the server tick at packet receipt using a clamped half-RTT estimate.
	 * Small corrections slew smoothly; a four-tick-or-larger discontinuity snaps atomically.
	 */
	public static boolean observeServerTick(
		final long serverTick,
		final long receivedAtNanos,
		final long oneWayDelayNanos
	) {
		if (serverTick < 0L || receivedAtNanos < 0L) {
			return false;
		}
		long boundedDelayNanos = Math.clamp(oneWayDelayNanos, 0L, 500_000_000L);
		double measuredOffset = serverTick
			+ boundedDelayNanos / NANOS_PER_TICK
			- receivedAtNanos / NANOS_PER_TICK;
		synchronized (SERVER_CLOCK_LOCK) {
			ServerClock current = serverClock;
			if (current == null) {
				double estimate = receivedAtNanos / NANOS_PER_TICK + measuredOffset;
				serverClock = new ServerClock(
					measuredOffset,
					receivedAtNanos,
					estimate,
					receivedAtNanos
				);
				return true;
			}
			double error = measuredOffset - current.offsetTicks();
			if (Math.abs(error) >= CLOCK_HARD_SNAP_TICKS) {
				serverClock = new ServerClock(
					measuredOffset,
					receivedAtNanos,
					current.lastEstimateTicks(),
					current.lastEstimateAtNanos()
				);
				return true;
			}
			double elapsedSeconds = Math.max(
				0.0D,
				(receivedAtNanos - current.observedAtNanos()) / 1_000_000_000.0D
			);
			double factor = 1.0D - Math.exp(
				-CLOCK_RESPONSE_PER_SECOND * Math.min(elapsedSeconds, 5.0D)
			);
			double maximumAdjustment = CLOCK_MAX_SLEW_TICKS_PER_SECOND * elapsedSeconds;
			double adjustment = Math.clamp(error * factor, -maximumAdjustment, maximumAdjustment);
			serverClock = new ServerClock(
				current.offsetTicks() + adjustment,
				Math.max(current.observedAtNanos(), receivedAtNanos),
				current.lastEstimateTicks(),
				current.lastEstimateAtNanos()
			);
			return true;
		}
	}

	/** Returns the shared server-tick estimate without mutating snapshot or animation identity. */
	public static OptionalDouble estimatedServerTick(final long nowNanos) {
		synchronized (SERVER_CLOCK_LOCK) {
			if (serverClock == null) {
				return OptionalDouble.empty();
			}
			long effectiveNow = Math.max(nowNanos, serverClock.observedAtNanos());
			double rawEstimate = effectiveNow / NANOS_PER_TICK + serverClock.offsetTicks();
			double monotonicEstimate = Math.max(rawEstimate, serverClock.lastEstimateTicks());
			serverClock = new ServerClock(
				serverClock.offsetTicks(),
				serverClock.observedAtNanos(),
				monotonicEstimate,
				Math.max(effectiveNow, serverClock.lastEstimateAtNanos())
			);
			return OptionalDouble.of(monotonicEstimate);
		}
	}

	public static Optional<ClientHudSnapshot> previewSnapshot() {
		PreviewDockState value = HUD_PREVIEW.get();
		return value == null ? Optional.empty() : value.snapshot();
	}

	public static Optional<ClientCountdownSnapshot> previewCountdownSnapshot() {
		PreviewCountdownState value = COUNTDOWN_PREVIEW.get();
		return value == null ? Optional.empty() : value.snapshot();
	}

	/** Starts one local preview namespace and rejects every packet from older settings sessions. */
	public static synchronized void activatePreview(final UUID previewId) {
		activePreviewId = Objects.requireNonNull(previewId, "previewId");
		HUD_PREVIEW.set(null);
		COUNTDOWN_PREVIEW.set(null);
	}

	/** Closes the matching local preview namespace before any delayed server response can revive it. */
	public static synchronized void clearPreview(final UUID previewId) {
		Objects.requireNonNull(previewId, "previewId");
		if (!previewId.equals(activePreviewId)) {
			return;
		}
		activePreviewId = null;
		HUD_PREVIEW.set(null);
		COUNTDOWN_PREVIEW.set(null);
	}

	public static synchronized boolean acceptPreviewClear(
		final io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface surface,
		final UUID previewId,
		final long epoch,
		final long sequence
	) {
		Objects.requireNonNull(surface, "surface");
		Objects.requireNonNull(previewId, "previewId");
		if (epoch < 0L || sequence < 0L) {
			return false;
		}
		if (!previewId.equals(activePreviewId)) {
			return false;
		}
		return switch (surface) {
			case HUD -> clearHudPreview(previewId, epoch, sequence);
			case COUNTDOWN -> clearCountdownPreview(previewId, epoch, sequence);
		};
	}

	private static boolean clearHudPreview(
		final UUID previewId,
		final long epoch,
		final long sequence
	) {
		while (true) {
			PreviewDockState current = HUD_PREVIEW.get();
			if (!previewAllowed(current, previewId, epoch, sequence)) {
				return false;
			}
			PreviewDockState tombstone = new PreviewDockState(
				previewId,
				epoch,
				sequence,
				Optional.empty()
			);
			if (HUD_PREVIEW.compareAndSet(current, tombstone)) {
				return true;
			}
		}
	}

	private static boolean clearCountdownPreview(
		final UUID previewId,
		final long epoch,
		final long sequence
	) {
		while (true) {
			PreviewCountdownState current = COUNTDOWN_PREVIEW.get();
			if (!previewAllowed(current, previewId, epoch, sequence)) {
				return false;
			}
			PreviewCountdownState tombstone = new PreviewCountdownState(
				previewId,
				epoch,
				sequence,
				Optional.empty()
			);
			if (COUNTDOWN_PREVIEW.compareAndSet(current, tombstone)) {
				return true;
			}
		}
	}

	/**
	 * Plays one locally-volume-scaled countdown checkpoint at most once per countdown instance.
	 * The future network payload remains a narrow adapter around this method.
	 */
	public static synchronized boolean playCountdownCheckpoint(
		final UUID countdownInstanceId,
		final long stateVersion,
		final String checkpointId,
		final Identifier soundId,
		final float volume,
		final float pitch
	) {
		Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
		CountdownState countdownState = COUNTDOWN.get();
		ClientCountdownSnapshot countdown = countdownState == null
			? null
			: countdownState.snapshot().orElse(null);
		if (
			stateVersion < 0L
				|| countdown == null
				|| !countdown.countdownInstanceId().equals(countdownInstanceId)
				|| countdown.version().contextVersion() != stateVersion
		) {
			return false;
		}
		String checkpoint = ClientHudSnapshot.boundedId(checkpointId, "checkpointId");
		Objects.requireNonNull(soundId, "soundId");
		if (!Float.isFinite(volume) || !Float.isFinite(pitch) || volume < 0.0F || volume > 4.0F || pitch <= 0.0F || pitch > 4.0F) {
			return false;
		}
		var event = BuiltInRegistries.SOUND_EVENT.getValue(soundId);
		if (event == null) {
			return false;
		}
		String occurrence = countdownInstanceId + "|" + checkpoint;
		if (PLAYED_COUNTDOWN_CHECKPOINTS.contains(occurrence)) {
			return false;
		}
		if (PLAYED_COUNTDOWN_CHECKPOINTS.size() >= 1024) {
			PLAYED_COUNTDOWN_CHECKPOINTS.clear();
		}
		PLAYED_COUNTDOWN_CHECKPOINTS.add(occurrence);
		float localVolume = ClientHudPreferences.snapshot().countdownVolume();
		if (localVolume > 0.0F && volume > 0.0F) {
			Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(event, pitch, volume * localVolume)
			);
		}
		return true;
	}

	public static synchronized void clearAll() {
		DOCK.set(null);
		COUNTDOWN.set(null);
		HUD_PREVIEW.set(null);
		COUNTDOWN_PREVIEW.set(null);
		activePreviewId = null;
		PLAYED_COUNTDOWN_CHECKPOINTS.clear();
		RESYNC_ATTEMPTS.clear();
		PENDING_RESYNC_REPLACES.clear();
		PENDING_PRESENTATION_SETTLES.clear();
		ClientHudPreferences.deactivateProfile();
		synchronized (SERVER_CLOCK_LOCK) {
			serverClock = null;
		}
	}

	private static void onDockAccepted(
		final ClientHudSnapshot previous,
		final ClientHudSnapshot current,
		final boolean settlePresentation
	) {
		ClientHudPreferences.activateProfile(
			new ProfileKey(current.profileId(), current.profileContentVersion()),
			current.policy(),
			current.components(),
			current.nodes()
		);
		LayoutTransition transition = current.layoutVariants().getFirst().transition();
		Optional<HudSound> sound = settlePresentation
			? Optional.empty()
			: previous == null
			? transition.enterSound()
			: meaningfulChange(previous.nodes(), previous.structureKey(), current.nodes(), current.structureKey())
				? transition.changeSound()
				: Optional.empty();
		sound.ifPresent(value -> playHudSound(value, ClientHudPreferences.snapshot().hudVolume()));
	}

	private static void onCountdownAccepted(
		final ClientCountdownSnapshot previous,
		final ClientCountdownSnapshot current,
		final boolean settlePresentation
	) {
		LayoutTransition transition = current.layoutVariants().getFirst().transition();
		Optional<HudSound> sound = settlePresentation
			? Optional.empty()
			: previous == null
			? transition.enterSound()
			: meaningfulChange(previous.nodes(), previous.structureKey(), current.nodes(), current.structureKey())
				? transition.changeSound()
				: Optional.empty();
		sound.ifPresent(value -> playHudSound(value, ClientHudPreferences.snapshot().countdownVolume()));
	}

	private static boolean meaningfulChange(
		final Map<String, ClientHudNode> previousNodes,
		final String previousStructure,
		final Map<String, ClientHudNode> currentNodes,
		final String currentStructure
	) {
		return !previousStructure.equals(currentStructure)
			|| HudVisualFingerprint.of(previousNodes) != HudVisualFingerprint.of(currentNodes);
	}

	private static void playHudSound(final HudSound sound, final float localVolume) {
		if (localVolume <= 0.0F || sound.volume() <= 0.0F) {
			return;
		}
		var event = BuiltInRegistries.SOUND_EVENT.getValue(sound.event());
		Minecraft client = Minecraft.getInstance();
		if (event == null || client == null) {
			return;
		}
		client.getSoundManager().play(
			SimpleSoundInstance.forUI(event, sound.pitch(), sound.volume() * localVolume)
		);
	}

	private static final Set<String> DOCK_KEYS = Set.of(
		"format_version", "surface", "profile_id", "profile_content_version", "layout_id",
		"mode", "root", "nodes", "eyebrow", "title", "body", "progress", "timer",
		"fields", "spine", "policy", "components", "layout_variants"
	);
	private static final Set<String> DOCK_PATCH_KEYS = Set.of(
		"format_version", "surface", "nodes", "eyebrow", "title", "body", "progress",
		"timer", "fields", "spine"
	);
	private static final Set<String> COUNTDOWN_KEYS = Set.of(
		"format_version", "surface", "countdown_instance_id", "layout_id", "mode", "root", "nodes",
		"layout_variants", "total_ticks", "remaining"
	);
	private static final Set<String> COUNTDOWN_PATCH_KEYS = Set.of(
		"format_version", "surface", "nodes", "remaining"
	);

	private static ClientHudSnapshot parseDock(
		final JsonObject root,
		final Version version,
		final long receivedAtNanos
	) {
		keys(root, DOCK_KEYS);
		version(root);
		string(root, "surface", true, 16).filter("dock"::equals).orElseThrow();
		String profileId = resourceId(string(root, "profile_id", true, 256).orElseThrow());
		long profileVersion = integer(root, "profile_content_version", true, 0L, Long.MAX_VALUE).orElseThrow();
		String layoutId = resourceId(string(root, "layout_id", true, 256).orElseThrow());
		Mode mode = parseEnum(Mode.class, string(root, "mode", true, 32).orElseThrow());

		Optional<Component> eyebrow = component(root, "eyebrow", false);
		Component title = component(root, "title", false).orElse(Component.empty());
		Optional<Component> body = component(root, "body", false);
		Optional<ProgressValue> progress = optionalObject(root, "progress").map(ClientHudRuntime::progress);
		Optional<TimerValue> timer = optionalObject(root, "timer").map(ClientHudRuntime::timer);
		List<FieldValue> fields = fieldValues(optionalArray(root, "fields").orElse(new JsonArray()));
		Optional<SpineValue> spine = optionalObject(root, "spine").map(ClientHudRuntime::spine);
		ClientPolicy policy = optionalObject(root, "policy").map(ClientHudRuntime::policy)
			.orElseGet(ClientPolicy::safeDefaults);

		Map<String, ClientHudNode> nodes;
		String rootId;
		if (root.has("root") || root.has("nodes")) {
			rootId = string(root, "root", true, 256).orElseThrow();
			nodes = nodes(array(root, "nodes"));
		} else {
			Synthesized synthesized = synthesize(
				title, body, progress, timer, fields, spine
			);
			rootId = synthesized.root();
			nodes = synthesized.nodes();
		}
		List<LayoutVariant> variants = root.has("layout_variants")
			? layoutVariants(array(root, "layout_variants"))
			: List.of(legacyVariant(layoutId, mode, rootId, Growth.UP));
		validateForest(variants, nodes);
		List<ComponentControl> controls = root.has("components")
			? componentControls(array(root, "components"))
			: reachable(rootId, nodes).stream()
				.map(nodes::get)
				.map(value -> value.meta().clientControl())
				.toList();
		validateControls(nodes, controls);
		String structure = structure(layoutId, mode, rootId, nodes, variants);
		return new ClientHudSnapshot(
			version,
			profileId,
			profileVersion,
			layoutId,
			mode,
			rootId,
			nodes,
			variants,
			eyebrow,
			title,
			body,
			progress,
			timer,
			fields,
			spine,
			policy,
			controls,
			receivedAtNanos,
			structure,
			root.toString()
		);
	}

	private static ClientCountdownSnapshot parseCountdown(
		final JsonObject root,
		final Version version,
		final long receivedAtNanos
	) {
		keys(root, COUNTDOWN_KEYS);
		version(root);
		string(root, "surface", true, 16).filter("countdown"::equals).orElseThrow();
		String layoutId = resourceId(string(root, "layout_id", true, 256).orElseThrow());
		Mode mode = root.has("mode")
			? parseEnum(Mode.class, string(root, "mode", true, 32).orElseThrow())
			: Mode.NORMAL;
		String rootId = string(root, "root", true, 256).orElseThrow();
		Map<String, ClientHudNode> nodes = nodes(array(root, "nodes"));
		List<LayoutVariant> variants = root.has("layout_variants")
			? layoutVariants(array(root, "layout_variants"))
			: List.of(legacyVariant(layoutId, mode, rootId, Growth.CENTER));
		validateForest(variants, nodes);
		return new ClientCountdownSnapshot(
			version,
			UUID.fromString(string(root, "countdown_instance_id", true, 40).orElseThrow()),
			layoutId,
			mode,
			rootId,
			nodes,
			variants,
			timer(object(root, "remaining")),
			integer(root, "total_ticks", true, 1L, 72_000L).orElseThrow(),
			receivedAtNanos,
			structure(layoutId, mode, rootId, nodes, variants),
			root.toString()
		);
	}

	private static Map<String, ClientHudNode> nodes(final JsonArray values) {
		if (values.isEmpty() || values.size() > MAX_NODES) {
			throw new IllegalArgumentException("HUD node count is invalid");
		}
		Map<String, ClientHudNode> result = new LinkedHashMap<>();
		for (JsonElement element : values) {
			ClientHudNode node = node(requireObject(element));
			if (result.putIfAbsent(node.id(), node) != null) {
				throw new IllegalArgumentException("duplicate HUD node ID");
			}
		}
		return Map.copyOf(result);
	}

	private static ClientHudNode node(final JsonObject value) {
		String id = string(value, "id", true, 256).orElseThrow();
		Type type = parseEnum(Type.class, string(value, "type", true, 32).orElseThrow());
		Priority priority = value.has("priority")
			? parseEnum(Priority.class, string(value, "priority", true, 32).orElseThrow())
			: Priority.PRIMARY;
		NodeStyle style = optionalObject(value, "style").map(ClientHudRuntime::style)
			.orElseGet(NodeStyle::defaults);
		ComponentControl control = clientControl(
			id,
			optionalObject(value, "client_control").orElseThrow()
		);
		NodeMeta meta = new NodeMeta(id, type, priority, style, control);
		return switch (type) {
			case TEXT -> {
				keys(value, common("value", "max_lines", "overflow", "alignment"));
				yield new TextNode(
					meta,
					component(value, "value", true).orElseThrow(),
					(int)integer(value, "max_lines", false, 1L, 8L).orElse(2L).longValue(),
					value.has("overflow")
						? parseEnum(Overflow.class, string(value, "overflow", true, 32).orElseThrow())
						: Overflow.WRAP,
					value.has("alignment")
						? parseEnum(Alignment.class, string(value, "alignment", true, 32).orElseThrow())
						: Alignment.START
				);
			}
			case IMAGE -> {
				keys(value, common("texture", "width", "height", "tint", "alt", "fit"));
				Identifier texture = Identifier.tryParse(string(value, "texture", true, 256).orElseThrow());
				if (texture == null) {
					throw new IllegalArgumentException("invalid HUD texture ID");
				}
				ImageFit fit = value.has("fit")
					? parseEnum(ImageFit.class, string(value, "fit", true, 16).orElseThrow())
					: ImageFit.CONTAIN;
				yield new ImageNode(
					meta,
					texture,
					(int)integer(value, "width", true, 1L, 256L).orElseThrow().longValue(),
					(int)integer(value, "height", true, 1L, 256L).orElseThrow().longValue(),
					color(value, "tint", 0xFFFFFFFF),
					component(value, "alt", true).orElseThrow(),
					fit
				);
			}
			case PLAYER_HEAD -> {
				keys(value, common("player_uuid", "player_name", "size", "show_hat", "offline"));
				yield new PlayerHeadNode(
					meta,
					UUID.fromString(string(value, "player_uuid", true, 40).orElseThrow()),
					string(value, "player_name", false, 64).orElse(""),
					(int)integer(value, "size", false, 8L, 64L).orElse(16L).longValue(),
					bool(value, "show_hat", true),
					bool(value, "offline", false)
				);
			}
			case COUNTER -> {
				keys(value, common("label", "value", "suffix"));
				yield new CounterNode(
					meta,
					component(value, "label", true).orElseThrow(),
					component(value, "value", true).orElseThrow(),
					component(value, "suffix", false)
				);
			}
			case BADGE -> {
				keys(value, common("label", "color"));
				yield new BadgeNode(
					meta,
					component(value, "label", true).orElseThrow(),
					color(value, "color", 0xFF55D7E6)
				);
			}
			case PROGRESS -> {
				keys(value, common("current", "maximum", "orientation", "label", "segments", "smooth", "spine"));
				yield new ProgressNode(
					meta,
					number(value, "current", true, -1.0E12D, 1.0E12D).orElseThrow(),
					number(value, "maximum", true, Double.MIN_NORMAL, 1.0E12D).orElseThrow(),
					value.has("orientation")
						? parseEnum(Orientation.class, string(value, "orientation", true, 32).orElseThrow())
						: Orientation.HORIZONTAL,
					component(value, "label", false),
					(int)integer(value, "segments", false, 0L, 64L).orElse(0L).longValue(),
					bool(value, "smooth", true),
					bool(value, "spine", false)
				);
			}
			case TIMER -> {
				keys(value, common("timer", "label"));
				yield new TimerNode(meta, timer(object(value, "timer")), component(value, "label", false));
			}
			case SEPARATOR -> {
				keys(value, common("orientation", "thickness", "color"));
				yield new SeparatorNode(
					meta,
					parseEnum(Orientation.class, string(value, "orientation", true, 32).orElseThrow()),
					(int)integer(value, "thickness", false, 1L, 8L).orElse(1L).longValue(),
					color(value, "color", 0xFF40505F)
				);
			}
			case BACKGROUND -> {
				keys(value, common("child", "fill", "border_color", "border_width", "padding"));
				yield new BackgroundNode(
					meta,
					string(value, "child", true, 256).orElseThrow(),
					color(value, "fill", 0xE61A232E),
					color(value, "border_color", 0xFF40505F),
					(int)integer(value, "border_width", false, 0L, 8L).orElse(1L).longValue(),
					optionalObject(value, "padding").map(ClientHudRuntime::insets).orElse(Insets.none())
				);
			}
			case ROW, COLUMN, OVERLAY, REPEAT -> {
				String childKey = type == Type.OVERLAY ? "layers" : "children";
				keys(value, common(childKey, "gap", "alignment", "weights"));
				List<String> children = strings(array(value, childKey), 32, 256);
				List<Double> weights = value.has("weights")
					? numbers(array(value, "weights"), children.size())
					: List.of();
				yield new ContainerNode(
					meta,
					children,
					(int)integer(value, "gap", false, 0L, 48L).orElse(0L).longValue(),
					value.has("alignment")
						? parseEnum(Alignment.class, string(value, "alignment", true, 32).orElseThrow())
						: Alignment.START,
					weights
				);
			}
		};
	}

	private static Set<String> common(final String... extra) {
		Set<String> result = new HashSet<>(Set.of("id", "type", "priority", "style", "client_control"));
		result.addAll(List.of(extra));
		return Set.copyOf(result);
	}

	private static NodeStyle style(final JsonObject value) {
		keys(value, Set.of(
			"text_color", "secondary_text_color", "background_color", "border_color",
			"track_color", "fill_color", "marker_color", "opacity", "font_weight",
			"padding", "gap", "width", "height"
		));
		String weight = string(value, "font_weight", false, 16).orElse("normal");
		if (!Set.of("normal", "bold").contains(weight)) {
			throw new IllegalArgumentException("invalid font weight");
		}
		return new NodeStyle(
			color(value, "text_color", 0xFFF1F5F7),
			color(value, "secondary_text_color", 0xFFAAB4BE),
			color(value, "background_color", 0x00000000),
			color(value, "border_color", 0x00000000),
			color(value, "track_color", 0xFF40505F),
			color(value, "fill_color", 0xFF55D7E6),
			color(value, "marker_color", 0xFFF2C14E),
			number(value, "opacity", false, 0.0D, 1.0D).orElse(1.0D),
			weight.equals("bold"),
			optionalObject(value, "padding").map(ClientHudRuntime::insets).orElse(Insets.none()),
			(int)integer(value, "gap", false, 0L, 48L).orElse(0L).longValue(),
			optionalDimension(value, "width"),
			optionalDimension(value, "height")
		);
	}

	private static OptionalInt optionalDimension(final JsonObject value, final String key) {
		if (!value.has(key) || value.get(key).isJsonNull()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of((int)integer(value, key, true, 1L, 640L).orElseThrow().longValue());
	}

	private static Insets insets(final JsonObject value) {
		keys(value, Set.of("left", "top", "right", "bottom"));
		return new Insets(
			(int)integer(value, "left", false, 0L, 48L).orElse(0L).longValue(),
			(int)integer(value, "top", false, 0L, 48L).orElse(0L).longValue(),
			(int)integer(value, "right", false, 0L, 48L).orElse(0L).longValue(),
			(int)integer(value, "bottom", false, 0L, 48L).orElse(0L).longValue()
		);
	}

	private static ComponentControl clientControl(final String id, final JsonObject value) {
		keys(value, Set.of("label", "visible", "allow_hide", "allow_compact", "reorder_group"));
		Optional<String> reorder = optionalString(value, "reorder_group", 64);
		return new ComponentControl(
			id,
			component(value, "label", true).orElseThrow(),
			bool(value, "visible", true),
			bool(value, "allow_hide", false),
			bool(value, "allow_compact", false),
			reorder
		);
	}

	private static List<ComponentControl> componentControls(final JsonArray values) {
		if (values.size() > MAX_NODES) {
			throw new IllegalArgumentException("component-control count is invalid");
		}
		List<ComponentControl> result = new ArrayList<>();
		for (JsonElement element : values) {
			JsonObject object = requireObject(element);
			keys(object, Set.of("id", "label", "visible", "allow_hide", "allow_compact", "reorder_group"));
			String id = string(object, "id", true, 256).orElseThrow();
			result.add(clientControl(id, withoutId(object)));
		}
		return List.copyOf(result);
	}

	private static JsonObject withoutId(final JsonObject value) {
		JsonObject result = value.deepCopy();
		result.remove("id");
		return result;
	}

	private static void validateControls(
		final Map<String, ClientHudNode> nodes,
		final List<ComponentControl> controls
	) {
		Set<String> seen = new HashSet<>();
		for (ComponentControl value : controls) {
			if (!nodes.containsKey(value.id()) || !seen.add(value.id())) {
				throw new IllegalArgumentException("component control references an unknown or duplicate node");
			}
		}
	}

	private static List<LayoutVariant> layoutVariants(final JsonArray values) {
		if (values.isEmpty() || values.size() > 9) {
			throw new IllegalArgumentException("HUD layout variant chain is empty or too deep");
		}
		List<LayoutVariant> result = new ArrayList<>();
		for (JsonElement element : values) {
			JsonObject value = requireObject(element);
			keys(value, Set.of(
				"layout_id",
				"normal_root",
				"compact_root",
				"summary_root",
				"degradation_layout",
				"size",
				"transition"
			));
			result.add(new LayoutVariant(
				resourceId(string(value, "layout_id", true, 256).orElseThrow()),
				string(value, "normal_root", false, 256),
				string(value, "compact_root", false, 256),
				string(value, "summary_root", false, 256),
				string(value, "degradation_layout", false, 256).map(ClientHudRuntime::resourceId),
				layoutSize(object(value, "size")),
				layoutTransition(object(value, "transition"))
			));
		}
		return List.copyOf(result);
	}

	private static LayoutSize layoutSize(final JsonObject value) {
		keys(value, Set.of(
			"minimum_width",
			"preferred_width",
			"maximum_width",
			"maximum_height",
			"growth"
		));
		return new LayoutSize(
			integer(value, "minimum_width", true, 1L, 2_048L).orElseThrow().intValue(),
			integer(value, "preferred_width", true, 1L, 2_048L).orElseThrow().intValue(),
			integer(value, "maximum_width", true, 1L, 2_048L).orElseThrow().intValue(),
			integer(value, "maximum_height", true, 1L, 2_048L).orElseThrow().intValue(),
			parseEnum(Growth.class, string(value, "growth", true, 16).orElseThrow())
		);
	}

	private static LayoutTransition layoutTransition(final JsonObject value) {
		keys(value, Set.of("enter", "change", "exit", "enter_sound", "change_sound"));
		return new LayoutTransition(
			parseEnum(EnterTransition.class, string(value, "enter", true, 32).orElseThrow()),
			parseEnum(ChangeTransition.class, string(value, "change", true, 32).orElseThrow()),
			parseEnum(ExitTransition.class, string(value, "exit", true, 32).orElseThrow()),
			optionalObject(value, "enter_sound").map(ClientHudRuntime::hudSound),
			optionalObject(value, "change_sound").map(ClientHudRuntime::hudSound)
		);
	}

	private static HudSound hudSound(final JsonObject value) {
		keys(value, Set.of("event", "volume", "pitch"));
		Identifier event = Identifier.tryParse(string(value, "event", true, 256).orElseThrow());
		if (event == null) {
			throw new IllegalArgumentException("invalid HUD sound ID");
		}
		return new HudSound(
			event,
			number(value, "volume", false, 0.0D, 4.0D).orElse(1.0D).floatValue(),
			number(value, "pitch", false, 0.5D, 2.0D).orElse(1.0D).floatValue()
		);
	}

	private static LayoutVariant legacyVariant(
		final String layoutId,
		final Mode mode,
		final String root,
		final Growth growth
	) {
		return new LayoutVariant(
			layoutId,
			mode == Mode.NORMAL ? Optional.of(root) : Optional.empty(),
			mode == Mode.COMPACT ? Optional.of(root) : Optional.empty(),
			mode == Mode.SUMMARY ? Optional.of(root) : Optional.empty(),
			Optional.empty(),
			new LayoutSize(1, 1, 2_048, 2_048, growth),
			new LayoutTransition(
				EnterTransition.NONE,
				ChangeTransition.NONE,
				ExitTransition.NONE,
				Optional.empty(),
				Optional.empty()
			)
		);
	}

	private static void validateForest(
		final List<LayoutVariant> variants,
		final Map<String, ClientHudNode> nodes
	) {
		for (ClientHudNode node : nodes.values()) {
			for (String child : node.childIds()) {
				if (!nodes.containsKey(child)) {
					throw new IllegalArgumentException("HUD child reference is unknown");
				}
			}
		}
		Set<String> visiting = new HashSet<>();
		Set<String> visited = new HashSet<>();
		for (LayoutVariant variant : variants) {
			for (String root : variant.roots()) {
				if (!nodes.containsKey(root)) {
					throw new IllegalArgumentException("unknown HUD variant root node");
				}
				visit(root, nodes, visiting, visited, 0);
			}
		}
		if (visited.size() != nodes.size()) {
			throw new IllegalArgumentException("HUD variant forest contains unreachable nodes");
		}
	}

	private static Set<String> reachable(
		final String root,
		final Map<String, ClientHudNode> nodes
	) {
		Set<String> result = new LinkedHashSet<>();
		visit(root, nodes, new HashSet<>(), result, 0);
		return Set.copyOf(result);
	}

	private static void visit(
		final String id,
		final Map<String, ClientHudNode> nodes,
		final Set<String> visiting,
		final Set<String> visited,
		final int depth
	) {
		if (depth > MAX_DEPTH || !visiting.add(id)) {
			throw new IllegalArgumentException("HUD node tree is cyclic or too deep");
		}
		if (!visited.contains(id)) {
			for (String child : nodes.get(id).childIds()) {
				visit(child, nodes, visiting, visited, depth + 1);
			}
			visited.add(id);
		}
		visiting.remove(id);
	}

	private static ProgressValue progress(final JsonObject value) {
		keys(value, Set.of("current", "maximum", "label", "smooth"));
		return new ProgressValue(
			number(value, "current", true, -1.0E12D, 1.0E12D).orElseThrow(),
			number(value, "maximum", true, Double.MIN_NORMAL, 1.0E12D).orElseThrow(),
			component(value, "label", false),
			bool(value, "smooth", true)
		);
	}

	private static TimerValue timer(final JsonObject value) {
		keys(value, Set.of("server_tick", "base_ticks", "rate", "paused", "format", "paused_marker"));
		String format = string(value, "format", true, 32).orElseThrow();
		TimerFormat timerFormat = switch (format) {
			case "m:ss" -> TimerFormat.MINUTES_SECONDS;
			case "h:mm:ss" -> TimerFormat.HOURS_MINUTES_SECONDS;
			case "localized_duration" -> TimerFormat.LOCALIZED_DURATION;
			default -> throw new IllegalArgumentException("unknown timer format");
		};
		return new TimerValue(
			integer(value, "server_tick", true, 0L, Long.MAX_VALUE).orElseThrow(),
			number(value, "base_ticks", true, 0.0D, 1.0E15D).orElseThrow(),
			number(value, "rate", true, -64.0D, 64.0D).orElseThrow(),
			bool(value, "paused", false),
			timerFormat,
			component(value, "paused_marker", false)
		);
	}

	private static List<FieldValue> fieldValues(final JsonArray values) {
		if (values.size() > 16) {
			throw new IllegalArgumentException("HUD field count is invalid");
		}
		List<FieldValue> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (JsonElement element : values) {
			JsonObject value = requireObject(element);
			keys(value, Set.of("id", "label", "value", "priority", "accent"));
			String id = string(value, "id", true, 256).orElseThrow();
			if (!ids.add(id)) {
				throw new IllegalArgumentException("duplicate HUD field ID");
			}
			result.add(new FieldValue(
				id,
				component(value, "label", true).orElseThrow(),
				component(value, "value", true).orElseThrow(),
				value.has("priority")
					? parseEnum(Priority.class, string(value, "priority", true, 32).orElseThrow())
					: Priority.SECONDARY,
				color(value, "accent", 0xFF55D7E6)
			));
		}
		return List.copyOf(result);
	}

	private static SpineValue spine(final JsonObject value) {
		keys(value, Set.of("label", "current", "maximum"));
		return new SpineValue(
			component(value, "label", false),
			number(value, "current", true, -1.0E12D, 1.0E12D).orElseThrow(),
			number(value, "maximum", true, Double.MIN_NORMAL, 1.0E12D).orElseThrow()
		);
	}

	private static ClientPolicy policy(final JsonObject value) {
		keys(value, Set.of(
			"allow_hide", "default_anchor", "allowed_anchors", "max_offset_x", "max_offset_y",
			"min_scale", "default_scale", "max_scale", "min_opacity", "default_opacity",
			"max_opacity", "allow_component_management", "tab_collision"
		));
		Anchor defaultAnchor = parseEnum(Anchor.class, string(value, "default_anchor", true, 32).orElseThrow());
		EnumSet<Anchor> allowed = EnumSet.noneOf(Anchor.class);
		for (String anchor : strings(array(value, "allowed_anchors"), 4, 32)) {
			allowed.add(parseEnum(Anchor.class, anchor));
		}
		return new ClientPolicy(
			bool(value, "allow_hide", true),
			defaultAnchor,
			Set.copyOf(allowed),
			(int)integer(value, "max_offset_x", false, 0L, 192L).orElse(96L).longValue(),
			(int)integer(value, "max_offset_y", false, 0L, 192L).orElse(96L).longValue(),
			number(value, "min_scale", false, 0.5D, 1.5D).orElse(0.75D),
			number(value, "default_scale", false, 0.5D, 1.5D).orElse(1.0D),
			number(value, "max_scale", false, 0.5D, 1.5D).orElse(1.25D),
			number(value, "min_opacity", false, 0.35D, 1.0D).orElse(0.55D),
			number(value, "default_opacity", false, 0.35D, 1.0D).orElse(0.92D),
			number(value, "max_opacity", false, 0.35D, 1.0D).orElse(1.0D),
			bool(value, "allow_component_management", true),
			parseEnum(TabCollision.class, string(value, "tab_collision", false, 16).orElse("dim"))
		);
	}

	private static Synthesized synthesize(
		final Component title,
		final Optional<Component> body,
		final Optional<ProgressValue> progress,
		final Optional<TimerValue> timer,
		final List<FieldValue> fields,
		final Optional<SpineValue> spine
	) {
		Map<String, ClientHudNode> nodes = new LinkedHashMap<>();
		List<String> children = new ArrayList<>();
		addText(nodes, children, "builtin.title", title, Priority.CRITICAL, 2);
		body.ifPresent(value -> addText(nodes, children, "builtin.body", value, Priority.PRIMARY, 3));
		progress.ifPresent(value -> {
			String id = "builtin.progress";
			nodes.put(id, new ProgressNode(
				meta(id, Type.PROGRESS, Priority.PRIMARY, Component.literal("进度"), false),
				value.current(), value.maximum(), Orientation.HORIZONTAL, value.label(), 0, value.smooth(), false
			));
			children.add(id);
		});
		timer.ifPresent(value -> {
			String id = "builtin.timer";
			nodes.put(id, new TimerNode(
				meta(id, Type.TIMER, Priority.PRIMARY, Component.literal("计时"), false),
				value, Optional.empty()
			));
			children.add(id);
		});
		for (FieldValue value : fields) {
			String id = "builtin.field." + value.id();
			nodes.put(id, new CounterNode(
				meta(id, Type.COUNTER, value.priority(), value.label(), true),
				value.label(), value.value(), Optional.empty()
			));
			children.add(id);
		}
		spine.ifPresent(value -> {
			String id = "builtin.spine";
			nodes.put(id, new ProgressNode(
				meta(id, Type.PROGRESS, Priority.PRIMARY, Component.literal("赛程脊线"), false),
				value.current(), value.maximum(), Orientation.HORIZONTAL, value.label(), 0, true, true
			));
			children.add(id);
		});
		String root = "builtin.root";
		nodes.put(root, new ContainerNode(
			meta(root, Type.COLUMN, Priority.PRIMARY, Component.literal("信息坞"), false),
			children,
			4,
			Alignment.STRETCH,
			List.of()
		));
		return new Synthesized(root, Map.copyOf(nodes));
	}

	private static void addText(
		final Map<String, ClientHudNode> nodes,
		final List<String> children,
		final String id,
		final Component value,
		final Priority priority,
		final int lines
	) {
		nodes.put(id, new TextNode(
			meta(id, Type.TEXT, priority, Component.literal("文本"), priority != Priority.CRITICAL),
			value,
			lines,
			Overflow.WRAP,
			Alignment.START
		));
		children.add(id);
	}

	private static NodeMeta meta(
		final String id,
		final Type type,
		final Priority priority,
		final Component label,
		final boolean allowHide
	) {
		return new NodeMeta(
			id,
			type,
			priority,
			NodeStyle.defaults(),
			new ComponentControl(id, label, true, allowHide, true, Optional.empty())
		);
	}

	private static boolean sameStructure(
		final ClientHudSnapshot left,
		final ClientHudSnapshot right
	) {
		return left.structureKey().equals(right.structureKey())
			&& left.policy().equals(right.policy())
			&& left.components().equals(right.components());
	}

	private static Optional<Long> serverTick(final ClientHudSnapshot snapshot) {
		Optional<Long> legacy = snapshot.timer().map(TimerValue::serverTick);
		if (legacy.isPresent()) {
			return legacy;
		}
		return snapshot.nodes()
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.filter(TimerNode.class::isInstance)
			.map(TimerNode.class::cast)
			.map(node -> node.timer().serverTick())
			.findFirst();
	}

	private static String structure(
		final String layout,
		final Mode mode,
		final String root,
		final Map<String, ClientHudNode> nodes,
		final List<LayoutVariant> variants
	) {
		StringBuilder value = new StringBuilder(layout).append('|').append(mode).append('|').append(root);
		for (LayoutVariant variant : variants) {
			value.append("|layout:").append(variant.layoutId())
				.append(':').append(variant.normalRootNodeId().orElse(""))
				.append(':').append(variant.compactRootNodeId().orElse(""))
				.append(':').append(variant.summaryRootNodeId().orElse(""))
				.append('>').append(variant.degradationLayoutId().orElse(""))
				.append(':').append(variant.size())
				.append(':').append(variant.transition());
		}
		nodes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			value.append('|').append(entry.getKey()).append(':').append(entry.getValue().meta().type());
			entry.getValue().childIds().forEach(child -> value.append('>').append(child));
		});
		return value.toString();
	}

	private static JsonObject merge(
		final JsonObject current,
		final JsonObject patch,
		final Set<String> allowed
	) {
		keys(patch, allowed);
		JsonObject merged = current.deepCopy();
		for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
			if (entry.getKey().equals("format_version") || entry.getKey().equals("surface")) {
				continue;
			}
			if (entry.getValue().isJsonNull()) {
				merged.remove(entry.getKey());
			} else {
				merged.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		return merged;
	}

	private static void validatePatchEnvelope(final JsonObject value, final String surface) {
		if (value.has("format_version")) {
			version(value);
		}
		if (value.has("surface")) {
			string(value, "surface", true, 16).filter(surface::equals).orElseThrow();
		}
	}

	private static boolean replaceAllowed(final Version current, final Version next) {
		if (current == null) {
			return true;
		}
		if (next.epoch() != current.epoch()) {
			return next.epoch() > current.epoch();
		}
		if (
			!current.gameInstanceId().equals(next.gameInstanceId())
				|| next.definitionGeneration() != current.definitionGeneration()
		) {
			return false;
		}
		if (next.contextVersion() != current.contextVersion()) {
			return next.contextVersion() > current.contextVersion();
		}
		return next.sequence() > current.sequence();
	}

	private static FrameAcceptance patchPrerequisite(
		final Version current,
		final boolean missingSnapshot,
		final Version next,
		final long baseSequence
	) {
		if (current == null) {
			return FrameAcceptance.MISSING_BASE;
		}
		int namespace = compareNamespace(next, current);
		if (namespace < 0 || (namespace == 0 && next.sequence() <= current.sequence())) {
			return FrameAcceptance.STALE;
		}
		if (namespace > 0) {
			return FrameAcceptance.NEWER_NAMESPACE;
		}
		if (missingSnapshot) {
			return FrameAcceptance.MISSING_BASE;
		}
		if (baseSequence != current.sequence()) {
			return FrameAcceptance.BASE_GAP;
		}
		return FrameAcceptance.ACCEPTED;
	}

	private static int compareNamespace(final Version left, final Version right) {
		int epoch = Long.compare(left.epoch(), right.epoch());
		if (epoch != 0) {
			return epoch;
		}
		if (
			!left.gameInstanceId().equals(right.gameInstanceId())
				|| left.definitionGeneration() != right.definitionGeneration()
		) {
			return -1;
		}
		return Long.compare(left.contextVersion(), right.contextVersion());
	}

	private static boolean sameNamespace(final Version left, final Version right) {
		return left.gameInstanceId().equals(right.gameInstanceId())
			&& left.definitionGeneration() == right.definitionGeneration()
			&& left.epoch() == right.epoch()
			&& left.contextVersion() == right.contextVersion();
	}

	private static Version version(
		final UUID game,
		final long generation,
		final long epoch,
		final long context,
		final long sequence
	) {
		return new Version(game, generation, epoch, context, sequence);
	}

	private static JsonObject document(final byte[] bytes) {
		byte[] value = Objects.requireNonNull(bytes, "json");
		if (value.length == 0 || value.length > MAX_JSON_BYTES) {
			throw new IllegalArgumentException("HUD JSON byte size is invalid");
		}
		try {
			String decoded = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(value))
				.toString();
			return requireObject(JsonParser.parseString(decoded));
		} catch (java.nio.charset.CharacterCodingException error) {
			throw new IllegalArgumentException("HUD JSON is not valid UTF-8", error);
		}
	}

	private static void version(final JsonObject value) {
		if (integer(value, "format_version", true, 1L, 1L).orElseThrow() != 1L) {
			throw new IllegalArgumentException("unsupported HUD format version");
		}
	}

	private static JsonObject requireObject(final JsonElement value) {
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException("expected JSON object");
		}
		return value.getAsJsonObject();
	}

	private static JsonObject object(final JsonObject parent, final String key) {
		return optionalObject(parent, key).orElseThrow();
	}

	private static Optional<JsonObject> optionalObject(final JsonObject parent, final String key) {
		if (!parent.has(key) || parent.get(key) instanceof JsonNull) {
			return Optional.empty();
		}
		return Optional.of(requireObject(parent.get(key)));
	}

	private static JsonArray array(final JsonObject parent, final String key) {
		if (!parent.has(key) || !parent.get(key).isJsonArray()) {
			throw new IllegalArgumentException("expected JSON array: " + key);
		}
		return parent.getAsJsonArray(key);
	}

	private static Optional<JsonArray> optionalArray(final JsonObject parent, final String key) {
		if (!parent.has(key) || parent.get(key).isJsonNull()) {
			return Optional.empty();
		}
		return Optional.of(array(parent, key));
	}

	private static void keys(final JsonObject value, final Set<String> allowed) {
		if (!allowed.containsAll(value.keySet())) {
			throw new IllegalArgumentException("HUD JSON contains an unknown key");
		}
	}

	private static Optional<String> string(
		final JsonObject value,
		final String key,
		final boolean required,
		final int maximum
	) {
		if (!value.has(key) || value.get(key).isJsonNull()) {
			if (required) {
				throw new IllegalArgumentException("missing string: " + key);
			}
			return Optional.empty();
		}
		JsonElement element = value.get(key);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("expected string: " + key);
		}
		String result = element.getAsString();
		if (result.length() > maximum) {
			throw new IllegalArgumentException("string is too long: " + key);
		}
		return Optional.of(result);
	}

	private static Optional<String> optionalString(
		final JsonObject value,
		final String key,
		final int maximum
	) {
		return string(value, key, false, maximum).map(String::strip).filter(raw -> !raw.isEmpty());
	}

	private static Optional<Long> integer(
		final JsonObject value,
		final String key,
		final boolean required,
		final long minimum,
		final long maximum
	) {
		if (!value.has(key) || value.get(key).isJsonNull()) {
			if (required) {
				throw new IllegalArgumentException("missing integer: " + key);
			}
			return Optional.empty();
		}
		JsonElement element = value.get(key);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("expected integer: " + key);
		}
		long result = element.getAsLong();
		if (result < minimum || result > maximum) {
			throw new IllegalArgumentException("integer outside range: " + key);
		}
		return Optional.of(result);
	}

	private static Optional<Double> number(
		final JsonObject value,
		final String key,
		final boolean required,
		final double minimum,
		final double maximum
	) {
		if (!value.has(key) || value.get(key).isJsonNull()) {
			if (required) {
				throw new IllegalArgumentException("missing number: " + key);
			}
			return Optional.empty();
		}
		JsonElement element = value.get(key);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("expected number: " + key);
		}
		double result = element.getAsDouble();
		if (!Double.isFinite(result) || result < minimum || result > maximum) {
			throw new IllegalArgumentException("number outside range: " + key);
		}
		return Optional.of(result);
	}

	private static boolean bool(final JsonObject value, final String key, final boolean fallback) {
		if (!value.has(key) || value.get(key).isJsonNull()) {
			return fallback;
		}
		JsonElement element = value.get(key);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException("expected boolean: " + key);
		}
		return element.getAsBoolean();
	}

	private static Optional<Component> component(
		final JsonObject value,
		final String key,
		final boolean required
	) {
		if (!value.has(key) || value.get(key).isJsonNull()) {
			if (required) {
				throw new IllegalArgumentException("missing component: " + key);
			}
			return Optional.empty();
		}
		Component result = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value.get(key)).getOrThrow();
		if (result.getString().codePointCount(0, result.getString().length()) > 2048) {
			throw new IllegalArgumentException("component is too long: " + key);
		}
		return Optional.of(result);
	}

	private static int color(final JsonObject value, final String key, final int fallback) {
		Optional<String> raw = string(value, key, false, 9);
		if (raw.isEmpty()) {
			return fallback;
		}
		String text = raw.orElseThrow();
		if (text.matches("#[0-9a-fA-F]{6}")) {
			return 0xFF000000 | Integer.parseUnsignedInt(text.substring(1), 16);
		}
		if (text.matches("#[0-9a-fA-F]{8}")) {
			return Integer.parseUnsignedInt(text.substring(1), 16);
		}
		throw new IllegalArgumentException("invalid HUD color");
	}

	private static String resourceId(final String raw) {
		Identifier value = Identifier.tryParse(raw);
		if (value == null || !value.toString().equals(raw)) {
			throw new IllegalArgumentException("invalid resource ID");
		}
		return value.toString();
	}

	private static <E extends Enum<E>> E parseEnum(final Class<E> type, final String raw) {
		try {
			return Enum.valueOf(type, raw.strip().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException error) {
			throw new IllegalArgumentException("unknown HUD enum value", error);
		}
	}

	private static List<String> strings(final JsonArray values, final int maximum, final int maximumLength) {
		if (values.size() > maximum) {
			throw new IllegalArgumentException("string array is too large");
		}
		List<String> result = new ArrayList<>();
		for (JsonElement value : values) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("expected string array item");
			}
			String raw = value.getAsString();
			if (raw.isEmpty() || raw.length() > maximumLength) {
				throw new IllegalArgumentException("string array item is invalid");
			}
			result.add(raw);
		}
		return List.copyOf(result);
	}

	private static List<Double> numbers(final JsonArray values, final int exactSize) {
		if (values.size() != exactSize) {
			throw new IllegalArgumentException("number array has the wrong size");
		}
		List<Double> result = new ArrayList<>();
		for (JsonElement value : values) {
			double number = value.getAsDouble();
			if (!Double.isFinite(number) || number < 0.0D) {
				throw new IllegalArgumentException("number array item is invalid");
			}
			result.add(number);
		}
		return List.copyOf(result);
	}

	public enum FrameAcceptance {
		ACCEPTED(false),
		STALE(false),
		DECODE_INVALID(true),
		MISSING_BASE(true),
		BASE_GAP(true),
		NEWER_NAMESPACE(true),
		STRUCTURE_MISMATCH(true);

		private final boolean requiresResync;

		FrameAcceptance(final boolean requiresResync) {
			this.requiresResync = requiresResync;
		}

		public boolean requiresResync() {
			return this.requiresResync;
		}

		public boolean accepted() {
			return this == ACCEPTED;
		}
	}

	private record ResyncNamespace(
		Surface surface,
		UUID gameInstanceId,
		long definitionGeneration,
		long epoch,
		long contextVersion
	) {
		private ResyncNamespace {
			surface = Objects.requireNonNull(surface, "surface");
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
		}
	}

	private record ServerClock(
		double offsetTicks,
		long observedAtNanos,
		double lastEstimateTicks,
		long lastEstimateAtNanos
	) {
		private ServerClock {
			if (
				!Double.isFinite(offsetTicks)
					|| !Double.isFinite(lastEstimateTicks)
					|| observedAtNanos < 0L
					|| lastEstimateAtNanos < 0L
			) {
				throw new IllegalArgumentException("server clock sample is invalid");
			}
		}
	}

	private record DockState(Version version, Optional<ClientHudSnapshot> snapshot, JsonObject document) {
		private DockState {
			version = Objects.requireNonNull(version, "version");
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			document = Objects.requireNonNull(document, "document").deepCopy();
		}
	}

	private record CountdownState(
		Version version,
		Optional<ClientCountdownSnapshot> snapshot,
		JsonObject document
	) {
		private CountdownState {
			version = Objects.requireNonNull(version, "version");
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			document = Objects.requireNonNull(document, "document").deepCopy();
		}
	}

	private sealed interface PreviewVersion permits PreviewDockState, PreviewCountdownState {
		UUID previewId();

		long epoch();

		long sequence();
	}

	private record PreviewDockState(
		UUID previewId,
		long epoch,
		long sequence,
		Optional<ClientHudSnapshot> snapshot
	)
		implements PreviewVersion {
		private PreviewDockState {
			previewId = Objects.requireNonNull(previewId, "previewId");
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
		}
	}

	private record PreviewCountdownState(
		UUID previewId,
		long epoch,
		long sequence,
		Optional<ClientCountdownSnapshot> snapshot
	) implements PreviewVersion {
		private PreviewCountdownState {
			previewId = Objects.requireNonNull(previewId, "previewId");
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
		}
	}

	private record Synthesized(String root, Map<String, ClientHudNode> nodes) {
		private Synthesized {
			root = Objects.requireNonNull(root, "root");
			nodes = Map.copyOf(nodes);
		}
	}
}
