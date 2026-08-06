package io.github.zcpu954861.pixeltzzpro.client.countdown;

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
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Local accessibility preferences for the one V3C countdown surface.
 *
 * <p>There is intentionally no visibility, position, scale, opacity, or component switch here.
 * An authorized countdown remains visible; these values only reduce motion, strengthen contrast,
 * or scale its local sound.</p>
 */
public final class ClientCountdownPreferences {
	private static final int FORMAT_VERSION = 1;
	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();
	private static final Path PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("pixel-tzz-pro-countdown.json");
	private static final Path TEMP_PATH = PATH.resolveSibling(PATH.getFileName() + ".tmp");
	private static final Snapshot DEFAULTS = new Snapshot(Motion.FULL, false, 1.0F);

	private static Snapshot current = DEFAULTS;
	private static boolean initialized;

	private ClientCountdownPreferences() {
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
			"updated countdown preferences"
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
			if (requiredInt(root, "format_version") != FORMAT_VERSION) {
				throw new IllegalArgumentException("unsupported countdown preference format");
			}
			Motion motion = root.has("motion")
				? Motion.valueOf(root.get("motion").getAsString().toUpperCase(Locale.ROOT))
				: DEFAULTS.motion();
			boolean highContrast = root.has("high_contrast")
				? root.get("high_contrast").getAsBoolean()
				: DEFAULTS.highContrast();
			float volume = root.has("volume")
				? root.get("volume").getAsFloat()
				: DEFAULTS.volume();
			return new Snapshot(motion, highContrast, volume);
		} catch (RuntimeException | IOException error) {
			PixelTzzPro.LOGGER.warn(
				"Ignoring invalid local V3C countdown preferences: {}",
				error.getMessage()
			);
			return DEFAULTS;
		}
	}

	private static int requiredInt(final JsonObject root, final String key) {
		if (!root.has(key)) {
			throw new IllegalArgumentException("missing " + key);
		}
		return root.get(key).getAsInt();
	}

	private static void write(final Snapshot snapshot) {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", FORMAT_VERSION);
		root.addProperty("motion", snapshot.motion().name().toLowerCase(Locale.ROOT));
		root.addProperty("high_contrast", snapshot.highContrast());
		root.addProperty("volume", snapshot.volume());
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
				Files.move(TEMP_PATH, PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException error) {
			PixelTzzPro.LOGGER.warn(
				"Could not save local V3C countdown preferences: {}",
				error.getMessage()
			);
		}
	}

	public enum Motion {
		FULL,
		SIMPLIFIED,
		STATIC;

		public Motion next() {
			return switch (this) {
				case FULL -> SIMPLIFIED;
				case SIMPLIFIED -> STATIC;
				case STATIC -> FULL;
			};
		}
	}

	public record Snapshot(Motion motion, boolean highContrast, float volume) {
		public Snapshot {
			motion = Objects.requireNonNull(motion, "motion");
			if (!Float.isFinite(volume) || volume < 0.0F || volume > 1.0F) {
				throw new IllegalArgumentException("countdown volume must remain within 0..1");
			}
		}

		public Snapshot withMotion(final Motion value) {
			return new Snapshot(value, this.highContrast, this.volume);
		}

		public Snapshot withHighContrast(final boolean value) {
			return new Snapshot(this.motion, value, this.volume);
		}

		public Snapshot withVolume(final float value) {
			return new Snapshot(this.motion, this.highContrast, value);
		}
	}
}
