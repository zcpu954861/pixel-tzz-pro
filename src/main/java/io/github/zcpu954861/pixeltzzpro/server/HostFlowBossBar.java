package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarColor;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarCompletionFeedback;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarStyle;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.HostBossBar;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

/**
 * One non-persistent host-only BossBar projection of the persisted active flow.
 */
public final class HostFlowBossBar {
	private ServerBossEvent event;
	private UUID flowInstanceId;
	private UUID viewerId;
	private int completionHoldTicksRemaining;
	private int completionFadeTicksRemaining;
	private int completionFadeTicksTotal;
	private final Set<UUID> consoleViewers = new HashSet<>();
	private UUID completionHostId;
	private net.minecraft.resources.Identifier completionSound;
	private boolean completionSoundPending;

	public void showActive(
		final MinecraftServer server,
		final Optional<UUID> hostId,
		final UUID instanceId,
		final RichText flowName,
		final HostBossBar definition,
		final int completed,
		final int total
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(hostId, "hostId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(flowName, "flowName");
		Objects.requireNonNull(definition, "definition");
		requireProgress(completed, total);
		ensureEvent(instanceId, definition);
		resetCompletionTimer();
		this.event.setName(render(definition.name(), flowName.plainText(), completed, total));
		this.event.setColor(color(definition.color()));
		this.event.setOverlay(overlay(definition.style()));
		this.event.setProgress(total == 0 ? 0.0F : (float)completed / total);
		this.event.setVisible(true);
		syncViewer(server, hostId);
	}

	public void showCompletion(
		final MinecraftServer server,
		final Optional<UUID> hostId,
		final UUID instanceId,
		final RichText flowName,
		final HostBossBar definition,
		final int completed,
		final int total
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(hostId, "hostId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(flowName, "flowName");
		Objects.requireNonNull(definition, "definition");
		requireProgress(completed, total);
		Optional<BossBarCompletionFeedback> feedback = definition.completionFeedback();
		if (
			feedback.isEmpty()
				|| (
					feedback.orElseThrow().holdTicks() == 0
						&& feedback.orElseThrow().fadeTicks() == 0
				)
		) {
			clear();
			return;
		}
		BossBarCompletionFeedback value = feedback.orElseThrow();
		ensureEvent(instanceId, definition);
		this.event.setName(render(value.name(), flowName.plainText(), completed, total));
		this.event.setColor(color(value.color()));
		this.event.setOverlay(overlay(definition.style()));
		this.event.setProgress(1.0F);
		this.event.setVisible(true);
		this.completionHoldTicksRemaining = value.holdTicks();
		this.completionFadeTicksRemaining = value.fadeTicks();
		this.completionFadeTicksTotal = value.fadeTicks();
		this.completionHostId = hostId.orElse(null);
		this.completionSound = value.sound().orElse(null);
		this.completionSoundPending = this.completionSound != null;
		syncViewer(server, hostId);
		playCompletionSoundIfReady(server, hostId);
	}

	/**
	 * Keeps the sole viewer current and expires completion feedback without rebuilding the bar.
	 */
	public void tick(final MinecraftServer server, final Optional<UUID> hostId) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(hostId, "hostId");
		if (this.event == null) {
			return;
		}
		syncViewer(server, hostId);
		if (
			hostId.filter(this.consoleViewers::contains).isPresent()
				&& (
					this.completionHoldTicksRemaining > 0
						|| this.completionFadeTicksRemaining > 0
				)
		) {
			return;
		}
		playCompletionSoundIfReady(server, hostId);
		if (this.completionHoldTicksRemaining > 0) {
			this.completionHoldTicksRemaining--;
			if (
				this.completionHoldTicksRemaining == 0
					&& this.completionFadeTicksTotal == 0
			) {
				clear();
			}
			return;
		}
		if (this.completionFadeTicksRemaining > 0) {
			this.completionFadeTicksRemaining--;
			this.event.setProgress(
				fadeProgress(
					this.completionFadeTicksRemaining,
					this.completionFadeTicksTotal
				)
			);
			if (this.completionFadeTicksRemaining > 0) {
				return;
			}
		}
		if (this.completionFadeTicksTotal > 0) {
			clear();
		}
	}

	public void clear() {
		if (this.event != null) {
			this.event.removeAllPlayers();
			this.event.setVisible(false);
		}
		this.event = null;
		this.flowInstanceId = null;
		this.viewerId = null;
		resetCompletionTimer();
	}

	public void reset() {
		clear();
		this.consoleViewers.clear();
	}

	public void setViewerInConsole(final UUID playerId, final boolean visible) {
		Objects.requireNonNull(playerId, "playerId");
		if (visible) {
			this.consoleViewers.add(playerId);
		} else {
			this.consoleViewers.remove(playerId);
		}
	}

	public void removeViewer(final UUID playerId) {
		this.consoleViewers.remove(Objects.requireNonNull(playerId, "playerId"));
	}

	public Optional<UUID> displayedInstanceId() {
		return Optional.ofNullable(this.flowInstanceId);
	}

	public int completionTicksRemaining() {
		return this.completionHoldTicksRemaining + this.completionFadeTicksRemaining;
	}

	static float fadeProgress(final int remaining, final int total) {
		if (remaining < 0 || total <= 0 || remaining > total) {
			throw new IllegalArgumentException("invalid BossBar fade progress");
		}
		return (float)remaining / total;
	}

	private void resetCompletionTimer() {
		this.completionHoldTicksRemaining = 0;
		this.completionFadeTicksRemaining = 0;
		this.completionFadeTicksTotal = 0;
		this.completionHostId = null;
		this.completionSound = null;
		this.completionSoundPending = false;
	}

	private void playCompletionSoundIfReady(
		final MinecraftServer server,
		final Optional<UUID> hostId
	) {
		if (
			!this.completionSoundPending
				|| this.completionSound == null
				|| hostId.filter(this.consoleViewers::contains).isPresent()
		) {
			return;
		}
		UUID host = hostId.filter(id -> Objects.equals(id, this.completionHostId)).orElse(null);
		ServerPlayer player = host == null ? null : server.getPlayerList().getPlayer(host);
		if (player != null) {
			play(player, this.completionSound);
		}
		this.completionSoundPending = false;
	}

	private void ensureEvent(
		final UUID instanceId,
		final HostBossBar definition
	) {
		if (this.event != null && instanceId.equals(this.flowInstanceId)) {
			return;
		}
		clear();
		this.flowInstanceId = instanceId;
		this.event = new ServerBossEvent(
			instanceId,
			Component.empty(),
			color(definition.color()),
			overlay(definition.style())
		);
	}

	private void syncViewer(
		final MinecraftServer server,
		final Optional<UUID> hostId
	) {
		UUID nextViewer = hostId
			.filter(id -> server.getPlayerList().getPlayer(id) != null)
			.orElse(null);
		if (Objects.equals(this.viewerId, nextViewer)) {
			return;
		}
		this.event.removeAllPlayers();
		this.viewerId = nextViewer;
		if (nextViewer != null) {
			this.event.addPlayer(server.getPlayerList().getPlayer(nextViewer));
		}
	}

	static Component render(
		final RichText template,
		final String event,
		final int completed,
		final int total
	) {
		Map<String, String> replacements = Map.of(
			"{event}",
			Objects.requireNonNull(event, "event"),
			"{completed}",
			Integer.toString(completed),
			"{total}",
			Integer.toString(total)
		);
		try {
			JsonElement json = replaceStrings(
				JsonParser.parseString(template.json()),
				replacements
			);
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
				.result()
				.orElseGet(
					() -> Component.literal(
						replace(template.plainText(), replacements)
					)
				);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn("Failed to render Pixel TZZ host BossBar text", error);
			return Component.literal(replace(template.plainText(), replacements));
		}
	}

	private static JsonElement replaceStrings(
		final JsonElement value,
		final Map<String, String> replacements
	) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			return new JsonPrimitive(replace(value.getAsString(), replacements));
		}
		if (value.isJsonArray()) {
			JsonArray result = new JsonArray();
			value.getAsJsonArray().forEach(
				child -> result.add(replaceStrings(child, replacements))
			);
			return result;
		}
		if (value.isJsonObject()) {
			JsonObject result = new JsonObject();
			value.getAsJsonObject().entrySet().forEach(
				entry -> result.add(
					entry.getKey(),
					replaceStrings(entry.getValue(), replacements)
				)
			);
			return result;
		}
		return value.deepCopy();
	}

	private static String replace(
		final String value,
		final Map<String, String> replacements
	) {
		String result = value;
		for (Map.Entry<String, String> replacement : replacements.entrySet()) {
			result = result.replace(replacement.getKey(), replacement.getValue());
		}
		return result;
	}

	private static BossEvent.BossBarColor color(final BossBarColor color) {
		return switch (color) {
			case PINK -> BossEvent.BossBarColor.PINK;
			case BLUE -> BossEvent.BossBarColor.BLUE;
			case RED -> BossEvent.BossBarColor.RED;
			case GREEN -> BossEvent.BossBarColor.GREEN;
			case YELLOW -> BossEvent.BossBarColor.YELLOW;
			case PURPLE -> BossEvent.BossBarColor.PURPLE;
			case WHITE -> BossEvent.BossBarColor.WHITE;
		};
	}

	private static BossEvent.BossBarOverlay overlay(final BossBarStyle style) {
		return switch (style) {
			case PROGRESS -> BossEvent.BossBarOverlay.PROGRESS;
			case NOTCHED_6 -> BossEvent.BossBarOverlay.NOTCHED_6;
			case NOTCHED_10 -> BossEvent.BossBarOverlay.NOTCHED_10;
			case NOTCHED_12 -> BossEvent.BossBarOverlay.NOTCHED_12;
			case NOTCHED_20 -> BossEvent.BossBarOverlay.NOTCHED_20;
		};
	}

	private static void play(final ServerPlayer player, final net.minecraft.resources.Identifier id) {
		BuiltInRegistries.SOUND_EVENT.get(id).ifPresent(
			sound -> player.connection.send(
				new ClientboundSoundPacket(
					sound,
					SoundSource.MASTER,
					player.getX(),
					player.getY(),
					player.getZ(),
					0.75F,
					1.0F,
					player.getRandom().nextLong()
				)
			)
		);
	}

	private static void requireProgress(final int completed, final int total) {
		if (completed < 0 || total < 0 || completed > total) {
			throw new IllegalArgumentException("invalid BossBar progress");
		}
	}
}
