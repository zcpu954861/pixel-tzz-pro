package io.github.zcpu954861.pixeltzzpro.client.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Local-only V3B accessibility preferences.
 *
 * <p>These values may make an authorized presentation quieter, faster or less animated. They
 * never suppress its final content, alter its audience, or delay a server callback. Invalid
 * local files fail silently to safe defaults and are reported only to the development log.</p>
 */
public final class ClientMessagePreferences {
	private static final int FORMAT_VERSION = 1;
	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();
	private static final Path PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("pixel-tzz-pro-message-accessibility.json");
	private static final Path TEMP_PATH = PATH.resolveSibling(PATH.getFileName() + ".tmp");
	private static final Snapshot DEFAULTS = new Snapshot(
		1.0F,
		false,
		true,
		1.0F,
		true,
		false
	);

	private static Snapshot current = DEFAULTS;
	private static boolean initialized;

	private ClientMessagePreferences() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		current = read();
	}

	public static synchronized Snapshot snapshot() {
		initialize();
		return current;
	}

	public static synchronized Snapshot update(final UnaryOperator<Snapshot> change) {
		initialize();
		Snapshot next = Objects.requireNonNull(
			Objects.requireNonNull(change, "change").apply(current),
			"updated preferences"
		);
		if (!next.equals(current)) {
			current = next;
			write(next);
		}
		return current;
	}

	public static synchronized Snapshot resetDefaults() {
		return update(ignored -> DEFAULTS);
	}

	private static Snapshot read() {
		if (!Files.isRegularFile(PATH)) {
			return DEFAULTS;
		}
		try {
			JsonObject root = JsonParser.parseString(
				Files.readString(PATH, StandardCharsets.UTF_8)
			).getAsJsonObject();
			int format = root.has("format_version")
				? root.get("format_version").getAsInt()
				: 0;
			if (format != FORMAT_VERSION) {
				throw new IllegalArgumentException(
					"unsupported preference format " + format
				);
			}
			return new Snapshot(
				number(root, "animation_speed", DEFAULTS.animationSpeed()),
				bool(root, "reduced_motion", DEFAULTS.reducedMotion()),
				bool(root, "character_sound", DEFAULTS.characterSound()),
				number(root, "character_sound_volume", DEFAULTS.characterSoundVolume()),
				bool(root, "cursor_blink", DEFAULTS.cursorBlink()),
				bool(root, "high_contrast", DEFAULTS.highContrast())
			);
		} catch (RuntimeException | IOException error) {
			PixelTzzPro.LOGGER.warn(
				"Ignoring invalid local V3B accessibility preferences: {}",
				error.getMessage()
			);
			return DEFAULTS;
		}
	}

	private static void write(final Snapshot snapshot) {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", FORMAT_VERSION);
		root.addProperty("animation_speed", snapshot.animationSpeed());
		root.addProperty("reduced_motion", snapshot.reducedMotion());
		root.addProperty("character_sound", snapshot.characterSound());
		root.addProperty("character_sound_volume", snapshot.characterSoundVolume());
		root.addProperty("cursor_blink", snapshot.cursorBlink());
		root.addProperty("high_contrast", snapshot.highContrast());
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(
				TEMP_PATH,
				GSON.toJson(root) + System.lineSeparator(),
				StandardCharsets.UTF_8
			);
			try {
				Files.move(
					TEMP_PATH,
					PATH,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
				);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(
					TEMP_PATH,
					PATH,
					StandardCopyOption.REPLACE_EXISTING
				);
			}
		} catch (IOException error) {
			PixelTzzPro.LOGGER.warn(
				"Could not save local V3B accessibility preferences: {}",
				error.getMessage()
			);
		}
	}

	private static float number(
		final JsonObject root,
		final String key,
		final float fallback
	) {
		return root.has(key) ? root.get(key).getAsFloat() : fallback;
	}

	private static boolean bool(
		final JsonObject root,
		final String key,
		final boolean fallback
	) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}

	public record Snapshot(
		float animationSpeed,
		boolean reducedMotion,
		boolean characterSound,
		float characterSoundVolume,
		boolean cursorBlink,
		boolean highContrast
	) {
		public Snapshot {
			if (
				!Float.isFinite(animationSpeed)
					|| animationSpeed < 0.5F
					|| animationSpeed > 3.0F
			) {
				throw new IllegalArgumentException(
					"animationSpeed must remain within 0.5..3.0"
				);
			}
			if (
				!Float.isFinite(characterSoundVolume)
					|| characterSoundVolume < 0.0F
					|| characterSoundVolume > 1.0F
			) {
				throw new IllegalArgumentException(
					"characterSoundVolume must remain within 0..1"
				);
			}
		}

		public Snapshot withAnimationSpeed(final float value) {
			return new Snapshot(
				value,
				this.reducedMotion,
				this.characterSound,
				this.characterSoundVolume,
				this.cursorBlink,
				this.highContrast
			);
		}

		public Snapshot withReducedMotion(final boolean value) {
			return new Snapshot(
				this.animationSpeed,
				value,
				this.characterSound,
				this.characterSoundVolume,
				this.cursorBlink,
				this.highContrast
			);
		}

		public Snapshot withCharacterSound(final boolean value) {
			return new Snapshot(
				this.animationSpeed,
				this.reducedMotion,
				value,
				this.characterSoundVolume,
				this.cursorBlink,
				this.highContrast
			);
		}

		public Snapshot withCharacterSoundVolume(final float value) {
			return new Snapshot(
				this.animationSpeed,
				this.reducedMotion,
				this.characterSound,
				value,
				this.cursorBlink,
				this.highContrast
			);
		}

		public Snapshot withCursorBlink(final boolean value) {
			return new Snapshot(
				this.animationSpeed,
				this.reducedMotion,
				this.characterSound,
				this.characterSoundVolume,
				value,
				this.highContrast
			);
		}

		public Snapshot withHighContrast(final boolean value) {
			return new Snapshot(
				this.animationSpeed,
				this.reducedMotion,
				this.characterSound,
				this.characterSoundVolume,
				this.cursorBlink,
				value
			);
		}
	}
}
