package io.github.zcpu954861.pixeltzzpro.client.message;

import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetType;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.Entry;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload.Resolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Silently checks the latest server-issued V3B font/sound manifest.
 *
 * <p>Checks run on the first client tick after receipt or resource reload. Deferring the reload
 * report until a tick ensures both Minecraft's font resources and SoundManager registry have
 * completed their reload before they are observed. No toast, chat line, or error screen is shown.
 */
public final class ClientMessageAssetReporter {
	private static final Map<Long, MessageAssetManifestS2CPayload> MANIFESTS =
		new LinkedHashMap<>();
	private static final Set<Long> REPORT_PENDING = new LinkedHashSet<>();
	private static long reportSequence;

	private ClientMessageAssetReporter() {
	}

	public static synchronized void beginConnection() {
		MANIFESTS.clear();
		REPORT_PENDING.clear();
		reportSequence = 0L;
	}

	public static synchronized void disconnect() {
		MANIFESTS.clear();
		REPORT_PENDING.clear();
	}

	/**
	 * Accepts one cumulative manifest stream per definition generation. Multiple generations may
	 * coexist while a pre-reload cue is still active, so an old generation is not discarded merely
	 * because the current data pack has already issued a newer one.
	 */
	public static synchronized boolean accept(
		final MessageAssetManifestS2CPayload next
	) {
		MessageAssetManifestS2CPayload previous = MANIFESTS.get(next.generation());
		if (previous != null) {
			if (next.manifestSequence() <= previous.manifestSequence()) {
				return false;
			}
			if (!next.entries().containsAll(previous.entries())) {
				return false;
			}
		} else if (next.manifestSequence() == 0L) {
			return false;
		}
		MANIFESTS.put(next.generation(), next);
		REPORT_PENDING.add(next.generation());
		return true;
	}

	/** Marks the current manifest for a fresh observation after Minecraft finishes resource reload. */
	public static synchronized void resourcesReloaded() {
		REPORT_PENDING.addAll(MANIFESTS.keySet());
	}

	public static void tick(final Minecraft client) {
		MessageAssetManifestS2CPayload current;
		long sequence;
		synchronized (ClientMessageAssetReporter.class) {
			if (
				REPORT_PENDING.isEmpty()
					|| !ClientPlayNetworking.canSend(MessageAssetReportC2SPayload.TYPE)
			) {
				return;
			}
			Long generation = REPORT_PENDING.iterator().next();
			current = MANIFESTS.get(generation);
			if (current == null) {
				REPORT_PENDING.remove(generation);
				return;
			}
			if (reportSequence == Long.MAX_VALUE) {
				return;
			}
			sequence = ++reportSequence;
			REPORT_PENDING.remove(generation);
		}

		List<MessageAssetReportC2SPayload.Entry> observed = check(
			current,
			client
		);
		ClientPlayNetworking.send(
			new MessageAssetReportC2SPayload(
				sequence,
				current.generation(),
				current.manifestSequence(),
				observed
			)
		);
	}

	private static List<MessageAssetReportC2SPayload.Entry> check(
		final MessageAssetManifestS2CPayload current,
		final Minecraft client
	) {
		ResourceManager resources = client.getResourceManager();
		List<MessageAssetReportC2SPayload.Entry> observed = new ArrayList<>(
			current.entries().size()
		);
		for (Entry expected : current.entries()) {
			Resolution resolution;
			if (available(expected.type(), expected.id(), resources, client)) {
				resolution = Resolution.PRESENT;
			} else if (
				expected.fallback()
					.filter(
						fallback -> available(
							expected.type(),
							fallback,
							resources,
							client
						)
					)
					.isPresent()
			) {
				resolution = Resolution.FALLBACK_USED;
			} else {
				resolution = Resolution.MISSING;
			}
			observed.add(
				new MessageAssetReportC2SPayload.Entry(
					expected.type(),
					expected.id(),
					resolution
				)
			);
		}
		return List.copyOf(observed);
	}

	private static boolean available(
		final AssetType type,
		final Identifier id,
		final ResourceManager resources,
		final Minecraft client
	) {
		try {
			return switch (type) {
				case FONT -> resources.getResource(fontResource(id)).isPresent();
				case SOUND -> client.getSoundManager().getSoundEvent(id) != null;
			};
		} catch (RuntimeException ignored) {
			// A broken client resource is reported as unavailable; it never opens an error UI here.
			return false;
		}
	}

	/** Minecraft 26.2 FontManager resolves identifiers through FileToIdConverter.json("font"). */
	private static Identifier fontResource(final Identifier font) {
		String path = font.getPath();
		if (!path.startsWith("font/")) {
			path = "font/" + path;
		}
		if (!path.endsWith(".json")) {
			path += ".json";
		}
		return Identifier.fromNamespaceAndPath(
			font.getNamespace(),
			path
		);
	}
}
