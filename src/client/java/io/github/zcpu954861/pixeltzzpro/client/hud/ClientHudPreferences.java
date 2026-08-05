package io.github.zcpu954861.pixeltzzpro.client.hud;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Anchor;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ClientPolicy;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-only V3C preferences with profile isolation and bounded, fail-soft persistence.
 *
 * <p>Raw preferences are stored by {@code profile_id + profile_content_version}. Server policy is
 * applied only when an effective view is requested, so a temporary policy tightening never
 * destroys the player's previous choice. A new content version migrates solely from the locally
 * last-used version of the same profile ID; the opaque content version is never ordered as a
 * number.</p>
 */
public final class ClientHudPreferences {
	public static final int MAX_EXCLUSIONS = 16;
	public static final int MAX_HIDDEN_COMPONENTS = 256;
	public static final int MAX_COMPACT_COMPONENTS = 256;
	public static final int MAX_REORDER_GROUPS = 64;
	public static final int MAX_REORDERED_COMPONENTS = 256;
	public static final int MAX_PROFILES = 16;
	private static final int FORMAT_VERSION = 2;
	private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
	private static Path file;
	private static boolean initialized;
	private static Store store = emptyStore();
	/** Compatibility view used by countdown volume and callers without a live HUD profile. */
	private static Snapshot current = store.legacy;
	private static ProfileKey activeProfile;
	private static ClientPolicy activePolicy = ClientPolicy.safeDefaults();
	private static List<ComponentControl> activeComponents = List.of();
	private static Map<String, ClientHudNode> activeNodes = Map.of();

	private ClientHudPreferences() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		final Path configDirectory;
		try {
			configDirectory = FabricLoader.getInstance().getConfigDir();
		} catch (RuntimeException ignored) {
			// Pure protocol/self-check runtimes do not always bootstrap Fabric's game directory.
			// Keep the bounded in-memory defaults and retry when a real client context exists.
			return;
		}
		if (configDirectory == null) {
			return;
		}
		file = configDirectory.resolve("pixel-tzz-pro-hud.properties");
		store = load();
		activeProfile = null;
		activePolicy = ClientPolicy.safeDefaults();
		activeComponents = List.of();
		activeNodes = Map.of();
		current = store.legacy;
		initialized = true;
	}

	/** Returns the raw active-profile snapshot, or the legacy/unscoped snapshot without a profile. */
	public static synchronized Snapshot snapshot() {
		initialize();
		return current;
	}

	/**
	 * Activates a profile and returns its raw preferences.
	 *
	 * <p>First use of a profile version creates a bounded entry. A same-ID version migrates from the
	 * last locally active version after filtering against the new policy and component topology. A
	 * different profile ID never inherits the old unscoped file or another profile's choices.</p>
	 */
	public static synchronized Snapshot activateProfile(
		final ProfileKey key,
		final ClientPolicy policy,
		final List<ComponentControl> components,
		final Map<String, ClientHudNode> nodes
	) {
		initialize();
		ProfileKey safeKey = Objects.requireNonNull(key, "key");
		ClientPolicy safePolicy = Objects.requireNonNull(policy, "policy");
		List<ComponentControl> safeComponents = boundedControls(components);
		Map<String, ClientHudNode> safeNodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
		StoredProfile existing = store.profiles.get(safeKey);
		boolean profileChanged = !safeKey.equals(activeProfile);
		boolean persist = false;
		Snapshot raw;
		if (existing == null) {
			Optional<StoredProfile> source = lastUsedVersion(safeKey.profileId());
			raw = source
				.map(value -> migrate(value.snapshot(), safePolicy, safeComponents, safeNodes))
				.orElseGet(() -> Snapshot.defaults(safePolicy));
			store.profiles.put(safeKey, new StoredProfile(raw, nextUsage()));
			pruneProfiles(safeKey);
			persist = true;
		} else {
			raw = existing.snapshot();
			if (profileChanged) {
				store.profiles.put(safeKey, new StoredProfile(raw, nextUsage()));
				persist = true;
			}
		}

		activeProfile = safeKey;
		activePolicy = safePolicy;
		activeComponents = safeComponents;
		activeNodes = safeNodes;
		current = raw;
		if (persist) {
			persist();
		}
		return raw;
	}

	public static synchronized Snapshot activateProfile(
		final ProfileKey key,
		final ClientPolicy policy,
		final List<ComponentControl> components
	) {
		return activateProfile(key, policy, components, Map.of());
	}

	public static synchronized void deactivateProfile() {
		activeProfile = null;
		activePolicy = ClientPolicy.safeDefaults();
		activeComponents = List.of();
		activeNodes = Map.of();
		current = store.legacy;
	}

	public static synchronized void apply(final Snapshot value) {
		initialize();
		Snapshot next = Objects.requireNonNull(value, "value").validated();
		if (activeProfile != null) {
			apply(activeProfile, next);
			return;
		}
		applyUnscoped(next);
	}

	/** Writes only the legacy/unscoped bucket even while another profile is active. */
	public static synchronized void applyUnscoped(final Snapshot value) {
		initialize();
		Snapshot next = Objects.requireNonNull(value, "value").validated();
		if (next.equals(store.legacy)) {
			return;
		}
		store.legacy = next;
		if (activeProfile == null) {
			current = next;
		}
		persist();
	}

	public static synchronized void apply(final ProfileKey key, final Snapshot value) {
		initialize();
		ProfileKey safeKey = Objects.requireNonNull(key, "key");
		Snapshot next = Objects.requireNonNull(value, "value").validated();
		StoredProfile previous = store.profiles.get(safeKey);
		if (previous != null && previous.snapshot().equals(next)) {
			if (safeKey.equals(activeProfile)) {
				current = next;
			}
			return;
		}
		store.profiles.put(safeKey, new StoredProfile(next, nextUsage()));
		pruneProfiles(safeKey);
		if (safeKey.equals(activeProfile)) {
			current = next;
		}
		persist();
	}

	public static synchronized void update(final UnaryOperator<Snapshot> change) {
		apply(Objects.requireNonNull(change, "change").apply(current));
	}

	public static synchronized void resetAll() {
		apply(Snapshot.defaults(activeProfile == null ? ClientPolicy.safeDefaults() : activePolicy));
	}

	/** Effective active-profile view retained for the production overlay compatibility boundary. */
	public static synchronized Effective effective(final ClientPolicy policy) {
		initialize();
		Snapshot value = effectiveSnapshot(current, policy, activeComponents, activeNodes);
		return new Effective(
			value.hudVisible(),
			value.anchor(),
			value.offsetX(),
			value.offsetY(),
			value.scale(),
			value.opacity(),
			value.highContrast(),
			value.motion(),
			value.hudVolume(),
			value.countdownVolume(),
			value.hiddenComponents(),
			value.compactComponents(),
			value.componentOrder(),
			value.exclusions(),
			value.rawDiagnostics()
		);
	}

	/** Pure policy view. The supplied raw snapshot is never mutated or persisted. */
	public static Snapshot effectiveSnapshot(
		final Snapshot raw,
		final ClientPolicy policy,
		final List<ComponentControl> components,
		final Map<String, ClientHudNode> nodes
	) {
		Snapshot local = Objects.requireNonNull(raw, "raw").validated();
		ClientPolicy allowed = Objects.requireNonNull(policy, "policy");
		List<ComponentControl> controls = boundedControls(components);
		Map<String, ClientHudNode> safeNodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
		Map<String, ComponentControl> byId = new LinkedHashMap<>();
		for (ComponentControl control : controls) {
			if (safeNodes.isEmpty() || safeNodes.containsKey(control.id())) {
				byId.put(control.id(), control);
			}
		}

		Set<String> hidden = new LinkedHashSet<>();
		Set<String> compact = new LinkedHashSet<>();
		Map<ComponentOrderKey, List<String>> order = new LinkedHashMap<>();
		if (allowed.allowComponentManagement()) {
			for (String id : local.hiddenComponents()) {
				ComponentControl control = byId.get(id);
				if (control != null && control.visible() && control.allowHide()) {
					hidden.add(id);
				}
			}
			for (String id : local.compactComponents()) {
				ComponentControl control = byId.get(id);
				if (control != null && control.visible() && control.allowCompact()) {
					compact.add(id);
				}
			}
			Map<ComponentOrderKey, List<String>> groups = reorderGroups(controls, safeNodes);
			for (Map.Entry<ComponentOrderKey, List<String>> entry : local.componentOrder().entrySet()) {
				List<String> permitted = groups.get(entry.getKey());
				if (permitted == null) {
					continue;
				}
				LinkedHashSet<String> normalized = new LinkedHashSet<>();
				for (String id : entry.getValue()) {
					if (permitted.contains(id)) {
						normalized.add(id);
					}
				}
				normalized.addAll(permitted);
				if (normalized.size() >= 2) {
					order.put(entry.getKey(), List.copyOf(normalized));
				}
			}
		}

		Anchor anchor = allowed.allowedAnchors().contains(local.anchor())
			? local.anchor()
			: allowed.defaultAnchor();
		return new Snapshot(
			allowed.allowHide() ? local.hudVisible() : true,
			anchor,
			Math.clamp(local.offsetX(), -allowed.maximumOffsetX(), allowed.maximumOffsetX()),
			Math.clamp(local.offsetY(), -allowed.maximumOffsetY(), allowed.maximumOffsetY()),
			(float)Math.clamp(local.scale(), allowed.minimumScale(), allowed.maximumScale()),
			(float)Math.clamp(local.opacity(), allowed.minimumOpacity(), allowed.maximumOpacity()),
			local.highContrast(),
			local.motion(),
			local.hudVolume(),
			local.countdownVolume(),
			hidden,
			compact,
			order,
			local.exclusions(),
			local.rawDiagnostics()
		).validated();
	}

	public static Snapshot effectiveSnapshot(
		final Snapshot raw,
		final ClientPolicy policy,
		final List<ComponentControl> components
	) {
		return effectiveSnapshot(raw, policy, components, Map.of());
	}

	/** Returns only topology-safe reorder groups: one parent container and same non-empty group. */
	public static Map<ComponentOrderKey, List<String>> reorderGroups(
		final List<ComponentControl> components,
		final Map<String, ClientHudNode> nodes
	) {
		List<ComponentControl> controls = boundedControls(components);
		Map<String, ClientHudNode> safeNodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
		if (safeNodes.isEmpty()) {
			return Map.of();
		}
		Map<String, ComponentControl> byId = new LinkedHashMap<>();
		for (ComponentControl control : controls) {
			byId.put(control.id(), control);
		}
		Map<String, Integer> parentCounts = new LinkedHashMap<>();
		for (ClientHudNode node : safeNodes.values()) {
			for (String child : node.childIds()) {
				parentCounts.merge(child, 1, Integer::sum);
			}
		}

		Map<ComponentOrderKey, List<String>> result = new LinkedHashMap<>();
		for (ClientHudNode node : safeNodes.values()) {
			if (!(node instanceof ClientHudNode.ContainerNode container)) {
				continue;
			}
			Map<String, List<String>> localGroups = new LinkedHashMap<>();
			for (String child : container.children()) {
				ComponentControl control = byId.get(child);
				if (
					control == null
						|| !control.visible()
						|| parentCounts.getOrDefault(child, 0) != 1
						|| control.reorderGroup().isEmpty()
				) {
					continue;
				}
				localGroups.computeIfAbsent(control.reorderGroup().orElseThrow(), ignored -> new ArrayList<>())
					.add(child);
			}
			for (Map.Entry<String, List<String>> entry : localGroups.entrySet()) {
				if (entry.getValue().size() >= 2) {
					result.put(
						new ComponentOrderKey(container.id(), entry.getKey()),
						List.copyOf(entry.getValue())
					);
				}
			}
		}
		return immutableOrder(result);
	}

	private static Snapshot migrate(
		final Snapshot source,
		final ClientPolicy policy,
		final List<ComponentControl> components,
		final Map<String, ClientHudNode> nodes
	) {
		return effectiveSnapshot(source, policy, components, nodes);
	}

	private static Optional<StoredProfile> lastUsedVersion(final String profileId) {
		return store.profiles.entrySet().stream()
			.filter(entry -> entry.getKey().profileId().equals(profileId))
			.max(Comparator.comparingLong(entry -> entry.getValue().lastUsed()))
			.map(Map.Entry::getValue);
	}

	private static long nextUsage() {
		if (store.usageCounter == Long.MAX_VALUE) {
			List<Map.Entry<ProfileKey, StoredProfile>> ordered = store.profiles.entrySet().stream()
				.sorted(Comparator.comparingLong(entry -> entry.getValue().lastUsed()))
				.toList();
			long value = 0L;
			for (Map.Entry<ProfileKey, StoredProfile> entry : ordered) {
				store.profiles.put(entry.getKey(), new StoredProfile(entry.getValue().snapshot(), ++value));
			}
			store.usageCounter = value;
		}
		return ++store.usageCounter;
	}

	private static void pruneProfiles(final ProfileKey protectedKey) {
		while (store.profiles.size() > MAX_PROFILES) {
			Optional<ProfileKey> oldest = store.profiles.entrySet().stream()
				.filter(entry -> !entry.getKey().equals(protectedKey))
				.min(Comparator.comparingLong(entry -> entry.getValue().lastUsed()))
				.map(Map.Entry::getKey);
			if (oldest.isEmpty()) {
				break;
			}
			store.profiles.remove(oldest.orElseThrow());
		}
	}

	private static List<ComponentControl> boundedControls(final List<ComponentControl> values) {
		Objects.requireNonNull(values, "components");
		LinkedHashMap<String, ComponentControl> result = new LinkedHashMap<>();
		for (ComponentControl value : values) {
			ComponentControl control = Objects.requireNonNull(value, "component");
			result.putIfAbsent(control.id(), control);
			if (result.size() >= MAX_HIDDEN_COMPONENTS) {
				break;
			}
		}
		return List.copyOf(result.values());
	}

	private static Store load() {
		Snapshot defaults = Snapshot.defaults();
		Path source = file;
		if (source == null || !Files.isRegularFile(source)) {
			return new Store(defaults, new LinkedHashMap<>(), 0L);
		}
		try {
			if (Files.size(source) > MAX_FILE_BYTES) {
				return new Store(defaults, new LinkedHashMap<>(), 0L);
			}
			Properties properties = new Properties();
			try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
			int format = integer(properties, "format_version", 1);
			if (format < FORMAT_VERSION) {
				// Old flat files remain an unscoped compatibility profile and are never inherited.
				return new Store(
					readSnapshot(properties, "", defaults, false),
					new LinkedHashMap<>(),
					0L
				);
			}
			if (format != FORMAT_VERSION) {
				return new Store(defaults, new LinkedHashMap<>(), 0L);
			}
			Snapshot legacy = readSnapshot(properties, "legacy.", defaults, true);
			LinkedHashMap<ProfileKey, StoredProfile> profiles = new LinkedHashMap<>();
			long usage = Math.max(0L, longInteger(properties, "usage_counter", 0L));
			int count = Math.clamp(integer(properties, "profile_count", 0), 0, MAX_PROFILES);
			for (int index = 0; index < count; index++) {
				String prefix = "profile." + index + ".";
				try {
					String profileId = decodeBounded(properties.getProperty(prefix + "profile_id"), 256);
					long contentVersion = longInteger(properties, prefix + "profile_content_version", -1L);
					long lastUsed = Math.max(0L, longInteger(properties, prefix + "last_used", 0L));
					ProfileKey key = new ProfileKey(profileId, contentVersion);
					Snapshot snapshot = readSnapshot(properties, prefix, defaults, true);
					profiles.put(key, new StoredProfile(snapshot, lastUsed));
					usage = Math.max(usage, lastUsed);
				} catch (IllegalArgumentException ignored) {
					// One malformed bounded profile must not discard the remaining local preferences.
				}
			}
			return new Store(legacy, profiles, usage);
		} catch (IOException | RuntimeException ignored) {
			return new Store(defaults, new LinkedHashMap<>(), 0L);
		}
	}

	private static Snapshot readSnapshot(
		final Properties properties,
		final String prefix,
		final Snapshot defaults,
		final boolean encodedIds
	) {
		Anchor anchor = parseEnum(Anchor.class, properties.getProperty(prefix + "anchor"), defaults.anchor());
		Motion motion = parseEnum(Motion.class, properties.getProperty(prefix + "motion"), defaults.motion());
		Set<String> hidden = idSet(properties.getProperty(prefix + "hidden_components", ""), encodedIds, MAX_HIDDEN_COMPONENTS);
		Set<String> compact = idSet(properties.getProperty(prefix + "compact_components", ""), encodedIds, MAX_COMPACT_COMPONENTS);
		Map<ComponentOrderKey, List<String>> order = new LinkedHashMap<>();
		int orderCount = Math.clamp(integer(properties, prefix + "reorder_count", 0), 0, MAX_REORDER_GROUPS);
		for (int index = 0; index < orderCount; index++) {
			String orderPrefix = prefix + "reorder." + index + ".";
			try {
				ComponentOrderKey key = new ComponentOrderKey(
					decodeBounded(properties.getProperty(orderPrefix + "parent"), 256),
					decodeBounded(properties.getProperty(orderPrefix + "group"), 256)
				);
				List<String> values = List.copyOf(idSet(
					properties.getProperty(orderPrefix + "components", ""),
					true,
					MAX_REORDERED_COMPONENTS
				));
				if (values.size() >= 2) {
					order.put(key, values);
				}
			} catch (IllegalArgumentException ignored) {
				// Skip only this malformed order group.
			}
		}
		List<Exclusion> exclusions = new ArrayList<>();
		int count = Math.clamp(integer(properties, prefix + "exclusion_count", 0), 0, MAX_EXCLUSIONS);
		for (int index = 0; index < count; index++) {
			Exclusion decoded = decodeExclusion(properties.getProperty(prefix + "exclusion." + index));
			if (decoded != null) {
				exclusions.add(decoded);
			}
		}
		return new Snapshot(
			bool(properties, prefix + "hud_visible", defaults.hudVisible()),
			anchor,
			integer(properties, prefix + "offset_x", defaults.offsetX()),
			integer(properties, prefix + "offset_y", defaults.offsetY()),
			decimal(properties, prefix + "scale", defaults.scale()),
			decimal(properties, prefix + "opacity", defaults.opacity()),
			bool(properties, prefix + "high_contrast", defaults.highContrast()),
			motion,
			decimal(properties, prefix + "hud_volume", defaults.hudVolume()),
			decimal(properties, prefix + "countdown_volume", defaults.countdownVolume()),
			hidden,
			compact,
			order,
			exclusions,
			bool(properties, prefix + "raw_diagnostics", defaults.rawDiagnostics())
		).validated();
	}

	private static void persist() {
		Path target = file;
		if (target == null) {
			return;
		}
		Properties properties = new Properties();
		properties.setProperty("format_version", Integer.toString(FORMAT_VERSION));
		properties.setProperty("usage_counter", Long.toString(store.usageCounter));
		writeSnapshot(properties, "legacy.", store.legacy);
		List<Map.Entry<ProfileKey, StoredProfile>> profiles = store.profiles.entrySet().stream()
			.sorted(Comparator.comparingLong(entry -> entry.getValue().lastUsed()))
			.toList();
		properties.setProperty("profile_count", Integer.toString(profiles.size()));
		for (int index = 0; index < profiles.size(); index++) {
			Map.Entry<ProfileKey, StoredProfile> entry = profiles.get(index);
			String prefix = "profile." + index + ".";
			properties.setProperty(prefix + "profile_id", encode(entry.getKey().profileId()));
			properties.setProperty(
				prefix + "profile_content_version",
				Long.toString(entry.getKey().profileContentVersion())
			);
			properties.setProperty(prefix + "last_used", Long.toString(entry.getValue().lastUsed()));
			writeSnapshot(properties, prefix, entry.getValue().snapshot());
		}

		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(target.getParent());
			try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				properties.store(writer, "Pixel TZZ Pro profile-scoped HUD and countdown preferences");
			}
			if (Files.size(temporary) > MAX_FILE_BYTES) {
				Files.deleteIfExists(temporary);
				return;
			}
			try {
				Files.move(
					temporary,
					target,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				// Never trade a known-good file for a non-atomic overwrite. The in-memory draft
				// remains usable for this session and a later supported write can persist it.
				Files.deleteIfExists(temporary);
			}
		} catch (IOException ignored) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException suppressed) {
				// Presentation-only preferences deliberately fail soft.
			}
		}
	}

	private static Store emptyStore() {
		return new Store(Snapshot.defaults(), new LinkedHashMap<>(), 0L);
	}

	private static void writeSnapshot(
		final Properties properties,
		final String prefix,
		final Snapshot value
	) {
		properties.setProperty(prefix + "hud_visible", Boolean.toString(value.hudVisible()));
		properties.setProperty(prefix + "anchor", value.anchor().name());
		properties.setProperty(prefix + "offset_x", Integer.toString(value.offsetX()));
		properties.setProperty(prefix + "offset_y", Integer.toString(value.offsetY()));
		properties.setProperty(prefix + "scale", Float.toString(value.scale()));
		properties.setProperty(prefix + "opacity", Float.toString(value.opacity()));
		properties.setProperty(prefix + "high_contrast", Boolean.toString(value.highContrast()));
		properties.setProperty(prefix + "motion", value.motion().name());
		properties.setProperty(prefix + "hud_volume", Float.toString(value.hudVolume()));
		properties.setProperty(prefix + "countdown_volume", Float.toString(value.countdownVolume()));
		properties.setProperty(prefix + "hidden_components", encodeIds(value.hiddenComponents()));
		properties.setProperty(prefix + "compact_components", encodeIds(value.compactComponents()));
		properties.setProperty(prefix + "raw_diagnostics", Boolean.toString(value.rawDiagnostics()));
		properties.setProperty(prefix + "reorder_count", Integer.toString(value.componentOrder().size()));
		int orderIndex = 0;
		for (Map.Entry<ComponentOrderKey, List<String>> entry : value.componentOrder().entrySet()) {
			String orderPrefix = prefix + "reorder." + orderIndex++ + ".";
			properties.setProperty(orderPrefix + "parent", encode(entry.getKey().parentId()));
			properties.setProperty(orderPrefix + "group", encode(entry.getKey().reorderGroup()));
			properties.setProperty(orderPrefix + "components", encodeIds(entry.getValue()));
		}
		properties.setProperty(prefix + "exclusion_count", Integer.toString(value.exclusions().size()));
		for (int index = 0; index < value.exclusions().size(); index++) {
			properties.setProperty(prefix + "exclusion." + index, encodeExclusion(value.exclusions().get(index)));
		}
	}

	private static String encodeExclusion(final Exclusion value) {
		return encode(value.id())
			+ ";" + encode(value.description())
			+ ";" + value.enabled()
			+ ";" + value.x()
			+ ";" + value.y()
			+ ";" + value.width()
			+ ";" + value.height();
	}

	private static Exclusion decodeExclusion(final String raw) {
		if (raw == null || raw.length() > 1024) {
			return null;
		}
		try {
			String[] parts = raw.split(";", -1);
			if (parts.length != 7) {
				return null;
			}
			return new Exclusion(
				decodeBounded(parts[0], 64),
				decodeBounded(parts[1], 96),
				Boolean.parseBoolean(parts[2]),
				Double.parseDouble(parts[3]),
				Double.parseDouble(parts[4]),
				Double.parseDouble(parts[5]),
				Double.parseDouble(parts[6])
			);
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	private static String encodeIds(final Iterable<String> values) {
		List<String> encoded = new ArrayList<>();
		for (String value : values) {
			encoded.add(encode(value));
		}
		return String.join(",", encoded);
	}

	private static Set<String> idSet(final String raw, final boolean encoded, final int maximum) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String part : raw.split(",")) {
			try {
				String value = encoded ? decodeBounded(part, 256) : part.strip();
				if (!value.isEmpty() && value.length() <= 256) {
					result.add(value);
				}
			} catch (IllegalArgumentException ignored) {
				// Skip a malformed identifier without discarding the rest.
			}
			if (result.size() >= maximum) {
				break;
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static String encode(final String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String decodeBounded(final String raw, final int maximum) {
		if (raw == null || raw.isEmpty() || raw.length() > maximum * 4) {
			throw new IllegalArgumentException("encoded preference value is invalid");
		}
		String value = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8).strip();
		if (value.isEmpty() || value.length() > maximum) {
			throw new IllegalArgumentException("decoded preference value is invalid");
		}
		return value;
	}

	private static boolean bool(final Properties values, final String key, final boolean fallback) {
		String raw = values.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		if (raw.equalsIgnoreCase("true")) {
			return true;
		}
		if (raw.equalsIgnoreCase("false")) {
			return false;
		}
		return fallback;
	}

	private static int integer(final Properties values, final String key, final int fallback) {
		try {
			return Integer.parseInt(values.getProperty(key, Integer.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static long longInteger(final Properties values, final String key, final long fallback) {
		try {
			return Long.parseLong(values.getProperty(key, Long.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static float decimal(final Properties values, final String key, final float fallback) {
		try {
			return Float.parseFloat(values.getProperty(key, Float.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static <E extends Enum<E>> E parseEnum(
		final Class<E> type,
		final String raw,
		final E fallback
	) {
		if (raw == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, raw);
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}

	private static Map<ComponentOrderKey, List<String>> immutableOrder(
		final Map<ComponentOrderKey, ? extends List<String>> values
	) {
		LinkedHashMap<ComponentOrderKey, List<String>> result = new LinkedHashMap<>();
		for (Map.Entry<ComponentOrderKey, ? extends List<String>> entry : values.entrySet()) {
			result.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Collections.unmodifiableMap(result);
	}

	public enum Motion {
		FULL,
		SIMPLIFIED,
		STATIC
	}

	public record ProfileKey(String profileId, long profileContentVersion) {
		public ProfileKey {
			profileId = bounded(profileId, "profileId", 256);
			if (profileContentVersion < 0L) {
				throw new IllegalArgumentException("profileContentVersion must be non-negative");
			}
		}
	}

	/** Parent-qualified because the same reorder-group name may appear in multiple containers. */
	public record ComponentOrderKey(String parentId, String reorderGroup) {
		public ComponentOrderKey {
			parentId = bounded(parentId, "parentId", 256);
			reorderGroup = bounded(reorderGroup, "reorderGroup", 256);
		}
	}

	public record Snapshot(
		boolean hudVisible,
		Anchor anchor,
		int offsetX,
		int offsetY,
		float scale,
		float opacity,
		boolean highContrast,
		Motion motion,
		float hudVolume,
		float countdownVolume,
		Set<String> hiddenComponents,
		Set<String> compactComponents,
		Map<ComponentOrderKey, List<String>> componentOrder,
		List<Exclusion> exclusions,
		boolean rawDiagnostics
	) {
		public Snapshot {
			anchor = Objects.requireNonNull(anchor, "anchor");
			motion = Objects.requireNonNull(motion, "motion");
			hiddenComponents = Collections.unmodifiableSet(new LinkedHashSet<>(hiddenComponents));
			compactComponents = Collections.unmodifiableSet(new LinkedHashSet<>(compactComponents));
			componentOrder = immutableOrder(componentOrder);
			exclusions = List.copyOf(exclusions);
		}

		/** Source-compatible constructor for pre-profile V3C callers and fixtures. */
		public Snapshot(
			final boolean hudVisible,
			final Anchor anchor,
			final int offsetX,
			final int offsetY,
			final float scale,
			final float opacity,
			final boolean highContrast,
			final Motion motion,
			final float hudVolume,
			final float countdownVolume,
			final Set<String> hiddenComponents,
			final List<Exclusion> exclusions,
			final boolean rawDiagnostics
		) {
			this(
				hudVisible,
				anchor,
				offsetX,
				offsetY,
				scale,
				opacity,
				highContrast,
				motion,
				hudVolume,
				countdownVolume,
				hiddenComponents,
				Set.of(),
				Map.of(),
				exclusions,
				rawDiagnostics
			);
		}

		public Snapshot validated() {
			Set<String> hidden = boundedIds(this.hiddenComponents, MAX_HIDDEN_COMPONENTS);
			Set<String> compact = boundedIds(this.compactComponents, MAX_COMPACT_COMPONENTS);
			LinkedHashMap<ComponentOrderKey, List<String>> order = new LinkedHashMap<>();
			int total = 0;
			for (Map.Entry<ComponentOrderKey, List<String>> entry : this.componentOrder.entrySet()) {
				if (order.size() >= MAX_REORDER_GROUPS || total >= MAX_REORDERED_COMPONENTS) {
					break;
				}
				LinkedHashSet<String> values = new LinkedHashSet<>();
				for (String raw : entry.getValue()) {
					String id = bounded(raw, "ordered component", 256);
					values.add(id);
					if (total + values.size() >= MAX_REORDERED_COMPONENTS) {
						break;
					}
				}
				if (values.size() >= 2) {
					order.put(entry.getKey(), List.copyOf(values));
					total += values.size();
				}
			}
			return new Snapshot(
				this.hudVisible,
				this.anchor,
				Math.clamp(this.offsetX, -192, 192),
				Math.clamp(this.offsetY, -192, 192),
				Math.clamp(finite(this.scale, 1.0F), 0.5F, 1.5F),
				Math.clamp(finite(this.opacity, 0.92F), 0.35F, 1.0F),
				this.highContrast,
				this.motion,
				Math.clamp(finite(this.hudVolume, 1.0F), 0.0F, 1.0F),
				Math.clamp(finite(this.countdownVolume, 1.0F), 0.0F, 1.0F),
				hidden,
				compact,
				order,
				this.exclusions.stream().limit(MAX_EXCLUSIONS).map(Exclusion::validated).toList(),
				this.rawDiagnostics
			);
		}

		private static float finite(final float value, final float fallback) {
			return Float.isFinite(value) ? value : fallback;
		}

		public static Snapshot defaults() {
			return defaults(ClientPolicy.safeDefaults());
		}

		public static Snapshot defaults(final ClientPolicy policy) {
			ClientPolicy allowed = Objects.requireNonNull(policy, "policy");
			return new Snapshot(
				true,
				allowed.defaultAnchor(),
				0,
				0,
				(float)allowed.defaultScale(),
				(float)allowed.defaultOpacity(),
				false,
				Motion.FULL,
				1.0F,
				1.0F,
				Set.of(),
				Set.of(),
				Map.of(),
				List.of(),
				false
			).validated();
		}

		public Snapshot withHudVisible(final boolean value) {
			return copy(value, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withAnchor(final Anchor value) {
			return copy(this.hudVisible, value, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withOffset(final int x, final int y) {
			return copy(this.hudVisible, this.anchor, x, y, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withScale(final float value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, value, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withOpacity(final float value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, value,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withHighContrast(final boolean value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				value, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withMotion(final Motion value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, value, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withVolumes(final float hud, final float countdown) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, hud, countdown,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withHiddenComponents(final Set<String> value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				value, this.compactComponents, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withCompactComponents(final Set<String> value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, value, this.componentOrder, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withComponentOrder(final Map<ComponentOrderKey, List<String>> value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, value, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withComponentPreferences(
			final Set<String> hidden,
			final Set<String> compact,
			final Map<ComponentOrderKey, List<String>> order
		) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				hidden, compact, order, this.exclusions, this.rawDiagnostics);
		}

		public Snapshot withExclusions(final List<Exclusion> value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, value, this.rawDiagnostics);
		}

		public Snapshot withRawDiagnostics(final boolean value) {
			return copy(this.hudVisible, this.anchor, this.offsetX, this.offsetY, this.scale, this.opacity,
				this.highContrast, this.motion, this.hudVolume, this.countdownVolume,
				this.hiddenComponents, this.compactComponents, this.componentOrder, this.exclusions, value);
		}

		public Snapshot resetLayout() {
			return resetLayout(ClientPolicy.safeDefaults());
		}

		public Snapshot resetLayout(final ClientPolicy policy) {
			Snapshot defaults = defaults(policy);
			return copy(
				this.hudVisible,
				defaults.anchor,
				defaults.offsetX,
				defaults.offsetY,
				defaults.scale,
				defaults.opacity,
				this.highContrast,
				this.motion,
				this.hudVolume,
				this.countdownVolume,
				this.hiddenComponents,
				this.compactComponents,
				this.componentOrder,
				this.exclusions,
				this.rawDiagnostics
			);
		}

		private static Snapshot copy(
			final boolean hudVisible,
			final Anchor anchor,
			final int offsetX,
			final int offsetY,
			final float scale,
			final float opacity,
			final boolean highContrast,
			final Motion motion,
			final float hudVolume,
			final float countdownVolume,
			final Set<String> hiddenComponents,
			final Set<String> compactComponents,
			final Map<ComponentOrderKey, List<String>> componentOrder,
			final List<Exclusion> exclusions,
			final boolean rawDiagnostics
		) {
			return new Snapshot(
				hudVisible,
				anchor,
				offsetX,
				offsetY,
				scale,
				opacity,
				highContrast,
				motion,
				hudVolume,
				countdownVolume,
				hiddenComponents,
				compactComponents,
				componentOrder,
				exclusions,
				rawDiagnostics
			).validated();
		}
	}

	public record Exclusion(
		String id,
		String description,
		boolean enabled,
		double x,
		double y,
		double width,
		double height
	) {
		public Exclusion {
			id = bounded(id, "exclusion id", 64);
			description = bounded(description, "exclusion description", 96);
			if (
				!Double.isFinite(x)
					|| !Double.isFinite(y)
					|| !Double.isFinite(width)
					|| !Double.isFinite(height)
					|| width <= 0.0D
					|| height <= 0.0D
			) {
				throw new IllegalArgumentException("exclusion rectangle is invalid");
			}
		}

		public Exclusion validated() {
			double safeWidth = Math.clamp(this.width, 0.02D, 1.0D);
			double safeHeight = Math.clamp(this.height, 0.02D, 1.0D);
			return new Exclusion(
				this.id,
				this.description,
				this.enabled,
				Math.clamp(this.x, 0.0D, 1.0D - safeWidth),
				Math.clamp(this.y, 0.0D, 1.0D - safeHeight),
				safeWidth,
				safeHeight
			);
		}

		public Exclusion withEnabled(final boolean value) {
			return new Exclusion(this.id, this.description, value, this.x, this.y, this.width, this.height);
		}

		public Exclusion moved(final double dx, final double dy) {
			return new Exclusion(
				this.id,
				this.description,
				this.enabled,
				this.x + dx,
				this.y + dy,
				this.width,
				this.height
			).validated();
		}
	}

	public record Effective(
		boolean visible,
		Anchor anchor,
		int offsetX,
		int offsetY,
		double scale,
		double opacity,
		boolean highContrast,
		Motion motion,
		float hudVolume,
		float countdownVolume,
		Set<String> hiddenComponents,
		Set<String> compactComponents,
		Map<ComponentOrderKey, List<String>> componentOrder,
		List<Exclusion> exclusions,
		boolean rawDiagnostics
	) {
		public Effective {
			anchor = Objects.requireNonNull(anchor, "anchor");
			motion = Objects.requireNonNull(motion, "motion");
			hiddenComponents = Set.copyOf(hiddenComponents);
			compactComponents = Set.copyOf(compactComponents);
			componentOrder = immutableOrder(componentOrder);
			exclusions = List.copyOf(exclusions);
		}
	}

	private static Set<String> boundedIds(final Set<String> values, final int maximum) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			result.add(bounded(value, "component", 256));
			if (result.size() >= maximum) {
				break;
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static String bounded(final String raw, final String label, final int maximum) {
		String value = Objects.requireNonNull(raw, label).strip();
		if (value.isEmpty() || value.length() > maximum) {
			throw new IllegalArgumentException(label + " is outside its bounded length");
		}
		return value;
	}

	private record StoredProfile(Snapshot snapshot, long lastUsed) {
		private StoredProfile {
			snapshot = Objects.requireNonNull(snapshot, "snapshot").validated();
			if (lastUsed < 0L) {
				throw new IllegalArgumentException("lastUsed must be non-negative");
			}
		}
	}

	private static final class Store {
		private Snapshot legacy;
		private final LinkedHashMap<ProfileKey, StoredProfile> profiles;
		private long usageCounter;

		private Store(
			final Snapshot legacy,
			final LinkedHashMap<ProfileKey, StoredProfile> profiles,
			final long usageCounter
		) {
			this.legacy = Objects.requireNonNull(legacy, "legacy").validated();
			this.profiles = new LinkedHashMap<>(profiles);
			this.usageCounter = Math.max(0L, usageCounter);
		}
	}
}
