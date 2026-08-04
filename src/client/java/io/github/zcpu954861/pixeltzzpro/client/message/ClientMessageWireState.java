package io.github.zcpu954861.pixeltzzpro.client.message;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState;
import io.github.zcpu954861.pixeltzzpro.client.screen.DataDrivenPageScreen;
import io.github.zcpu954861.pixeltzzpro.client.screen.ForcedFlowPauseScreen;
import io.github.zcpu954861.pixeltzzpro.client.screen.TransitioningConsoleScreen;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.OfferResult;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload.ChatUpdateMode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageNodeAppendS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessagePlanS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload.Surface;
import java.util.Objects;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Client boundary for V3B wire data and presentation-only capability/screen reports.
 *
 * <p>Rejected packets are diagnostic-only. They never open a screen or render a red transient
 * message, and client reports never advance a server callback.</p>
 */
public final class ClientMessageWireState {
	private static final MessageWireInbox INBOX = new MessageWireInbox();

	private static long reportSequence;
	private static boolean capabilitiesSent;
	private static boolean resourceReloading;
	private static int resourceReloadReleaseTicks;
	private static ScreenSnapshot lastScreen;
	private static ChatUpdateMode chatUpdateMode;
	private static boolean chatUpdateFailed;

	private ClientMessageWireState() {
	}

	public static MessageWireInbox inbox() {
		return INBOX;
	}

	public static void beginConnection() {
		INBOX.clear();
		reportSequence = 0L;
		capabilitiesSent = false;
		resourceReloading = false;
		resourceReloadReleaseTicks = 0;
		lastScreen = null;
		chatUpdateMode = null;
		chatUpdateFailed = false;
	}

	public static void disconnect() {
		beginConnection();
	}

	public static void accept(final MessagePlanS2CPayload payload) {
		Objects.requireNonNull(payload, "payload");
		OfferResult result = INBOX.offer(payload);
		var plan = payload.plan();
		PixelTzzPro.LOGGER.info(
			"V3B plan receive result={} instance={} generation={} sequence={} cycle={} "
				+ "disposition={} clock={}/{} ordinals={} slots={} bodyHash={}",
			result,
			plan.instanceId(),
			plan.generation(),
			plan.sequence(),
			plan.cycle(),
			plan.disposition(),
			plan.clock().serverNow(),
			plan.clock().startAt(),
			plan.nodes().stream().map(node -> node.ordinal()).toList(),
			plan.nodes().stream()
				.filter(io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode.class::isInstance)
				.map(io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode.class::cast)
				.flatMap(node -> node.segments().stream())
				.filter(io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment.class::isInstance)
				.map(io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment.class::cast)
				.map(segment -> segment.slot())
				.distinct()
				.sorted()
				.toList(),
			Integer.toHexString(java.util.Arrays.hashCode(plan.encode()))
		);
		reportOffer("plan", result);
	}

	public static void accept(final MessageFieldDeltaS2CPayload payload) {
		Objects.requireNonNull(payload, "payload");
		OfferResult result = INBOX.offer(payload);
		if (result != OfferResult.ACCEPTED) {
			PixelTzzPro.LOGGER.warn(
				"Rejected V3B field envelope instance={} generation={} sequence={} slots={}",
				payload.instanceId(),
				payload.generation(),
				payload.sequence(),
				payload.values().stream().map(value -> value.slot()).toList()
			);
		}
		reportOffer("field delta", result);
	}

	public static void accept(final MessageNodeAppendS2CPayload payload) {
		reportOffer("node append", INBOX.offer(Objects.requireNonNull(payload, "payload")));
	}

	public static void accept(final MessageControlS2CPayload payload) {
		reportOffer("control", INBOX.offer(Objects.requireNonNull(payload, "payload")));
	}

	public static void beginResourceReload() {
		resourceReloading = true;
		resourceReloadReleaseTicks = 0;
	}

	public static void finishResourceReload() {
		// Keep the transition observable for two client ticks. A synchronous resource listener can
		// otherwise set and clear the flag between reports, making the server see no reload at all.
		resourceReloading = true;
		resourceReloadReleaseTicks = Math.max(resourceReloadReleaseTicks, 2);
	}

	public static void tick(final Minecraft client) {
		Objects.requireNonNull(client, "client");
		sendCapabilities(client);
		sendScreenStateIfChanged(client);
		if (resourceReloadReleaseTicks > 0 && --resourceReloadReleaseTicks == 0) {
			resourceReloading = false;
		}
	}

	public static Surface surface(final Minecraft client) {
		return screenSnapshot(Objects.requireNonNull(client, "client")).surface();
	}

	public static boolean resourceReloading() {
		return resourceReloading;
	}

	public static ChatUpdateMode chatUpdateMode() {
		return chatUpdateMode == null ? ChatUpdateMode.STATIC_ONLY : chatUpdateMode;
	}

	static void downgradeChatUpdates() {
		if (!chatUpdateFailed || chatUpdateMode != ChatUpdateMode.STATIC_ONLY) {
			chatUpdateFailed = true;
			chatUpdateMode = ChatUpdateMode.STATIC_ONLY;
			// Re-advertise the lower capability on the next tick. This report can
			// only remove a presentation feature; it cannot grant authority.
			capabilitiesSent = false;
		}
	}

	private static void sendCapabilities(final Minecraft client) {
		if (
			capabilitiesSent
				|| !ClientPlayNetworking.canSend(MessageCapabilitiesC2SPayload.TYPE)
		) {
			return;
		}
		chatUpdateMode = !chatUpdateFailed && DynamicChatEntry.supportsInPlace(client)
			? ChatUpdateMode.IN_PLACE
			: ChatUpdateMode.STATIC_ONLY;
		ClientPlayNetworking.send(
			MessageCapabilitiesC2SPayload.current(nextSequence(), chatUpdateMode)
		);
		capabilitiesSent = true;
	}

	private static void sendScreenStateIfChanged(final Minecraft client) {
		if (!ClientPlayNetworking.canSend(MessageScreenStateC2SPayload.TYPE)) {
			return;
		}
		ScreenSnapshot current = screenSnapshot(client);
		if (current.equals(lastScreen)) {
			return;
		}
		lastScreen = current;
		ClientPlayNetworking.send(
			new MessageScreenStateC2SPayload(
				nextSequence(),
				current.surface(),
				current.worldPaused(),
				current.resourceReloading(),
				current.chatVisible()
			)
		);
	}

	private static ScreenSnapshot screenSnapshot(final Minecraft client) {
		Surface surface;
		if (
			ClientPageState.forcedPageActive()
				|| client.gui.screen() instanceof ForcedFlowPauseScreen
		) {
			/*
			 * Authoritative forced-flow state must win even during the one-tick hand-off
			 * before LivePageSupervisor installs the actual page. Otherwise that gap is
			 * reported as GAMEPLAY and a wait_gameplay node can start underneath the
			 * mandatory flow before the screen exists.
			 */
			surface = Surface.FORCED_MOD_SCREEN;
		} else if (client.gui.screen() == null) {
			surface = Surface.GAMEPLAY;
		} else if (
			client.gui.screen() instanceof TransitioningConsoleScreen
				|| client.gui.screen() instanceof DataDrivenPageScreen
		) {
			surface = Surface.MOD_SCREEN;
		} else if (client.gui.screen() instanceof PauseScreen) {
			surface = Surface.PAUSE_MENU;
		} else {
			surface = Surface.OTHER_SCREEN;
		}
		return new ScreenSnapshot(
			surface,
			client.isPaused(),
			resourceReloading,
			client.options.chatVisibility().get() != ChatVisiblity.HIDDEN
		);
	}

	private static long nextSequence() {
		if (reportSequence == Long.MAX_VALUE) {
			reportSequence = 0L;
		}
		return reportSequence++;
	}

	private static void reportOffer(final String kind, final OfferResult result) {
		if (result != OfferResult.ACCEPTED) {
			/*
			 * A rejected ordered-stream packet can strand a visible field at its reveal gate and
			 * keep every later queued cue silent.  Keep this at WARN during normal operation: the
			 * inbox is bounded, so a malformed peer cannot turn it into an unbounded log source,
			 * and silently hiding the exact rejected envelope made integrated-client failures
			 * impossible to distinguish from a rendering problem.
			 */
			PixelTzzPro.LOGGER.warn(
				"Rejected V3B {} packet at the client boundary: {}",
				kind,
				result
			);
		}
	}

	private record ScreenSnapshot(
		Surface surface,
		boolean worldPaused,
		boolean resourceReloading,
		boolean chatVisible
	) {
		private ScreenSnapshot {
			surface = Objects.requireNonNull(surface, "surface");
		}
	}
}
