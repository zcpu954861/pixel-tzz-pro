package io.github.zcpu954861.pixeltzzpro.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tiny client-local preference store for presentation-only console choices.
 */
public final class ClientUiPreferences {
	private static final String CALLBACK_AUDIT_RAW = "callback_audit_raw";
	private static final Path FILE = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("pixel-tzz-pro-client.properties");
	private static boolean callbackAuditRaw = loadCallbackAuditRaw();

	private ClientUiPreferences() {
	}

	public static synchronized boolean callbackAuditRaw() {
		return callbackAuditRaw;
	}

	public static synchronized void setCallbackAuditRaw(final boolean value) {
		if (callbackAuditRaw == value) {
			return;
		}
		callbackAuditRaw = value;
		persist();
	}

	private static boolean loadCallbackAuditRaw() {
		if (!Files.isRegularFile(FILE)) {
			return false;
		}
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			properties.load(reader);
			return Boolean.parseBoolean(properties.getProperty(CALLBACK_AUDIT_RAW, "false"));
		} catch (IOException | RuntimeException ignored) {
			return false;
		}
	}

	private static void persist() {
		Properties properties = new Properties();
		properties.setProperty(CALLBACK_AUDIT_RAW, Boolean.toString(callbackAuditRaw));
		Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		try {
			Files.createDirectories(FILE.getParent());
			try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				properties.store(writer, "Pixel-Tzz-Pro client presentation preferences");
			}
			try {
				Files.move(
					temporary,
					FILE,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ignored) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException suppressed) {
				// Presentation preferences are intentionally fail-soft.
			}
		}
	}
}
