package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Alignment;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ContainerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeMeta;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeStyle;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Overflow;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TextNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Type;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.ComponentOrderKey;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Effective;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Exclusion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Motion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.ProfileKey;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Snapshot;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Anchor;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ClientPolicy;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Priority;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TabCollision;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Profile isolation, migration, policy narrowing, and bounded persistence checks for V3C. */
public final class HudClientPreferencesSelfCheck {
	private static Path preferencesFile;

	private HudClientPreferencesSelfCheck() {
	}

	public static void main(final String[] args) throws ReflectiveOperationException, IOException {
		initializeFabricLoaderPathsAndLegacyFile();
		checkLegacyFileRemainsUnscoped();
		checkSnapshotBounds();
		checkProfileDefaultsPolicyNarrowingAndMigration();
		checkProfileStoreBound();
		checkPolicyConstructorBounds();
		System.out.println("HUD client preferences self-check passed");
	}

	private static void initializeFabricLoaderPathsAndLegacyFile()
		throws ReflectiveOperationException, IOException {
		Class<?> implementation = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
		Field instanceField = implementation.getField("INSTANCE");
		Object instance = instanceField.get(null);
		Path testRoot = Path.of("build", "hud-preferences-self-check").toAbsolutePath().normalize();
		for (String name : List.of("gameDir", "configDir")) {
			Field pathField = implementation.getDeclaredField(name);
			pathField.setAccessible(true);
			if (pathField.get(instance) == null) {
				pathField.set(instance, name.equals("configDir") ? testRoot.resolve("config") : testRoot);
			}
		}
		Path config = testRoot.resolve("config");
		Files.createDirectories(config);
		preferencesFile = config.resolve("pixel-tzz-pro-hud.properties");
		Files.writeString(
			preferencesFile,
			"hud_visible=false\nanchor=TOP_LEFT\nscale=1.2\nopacity=0.7\n"
				+ "hidden_components=legacy:only\n"
		);
	}

	private static void checkLegacyFileRemainsUnscoped() {
		Snapshot legacy = ClientHudPreferences.snapshot();
		check(!legacy.hudVisible(), "the old flat file was not loaded as the unscoped compatibility snapshot");
		check(legacy.anchor() == Anchor.TOP_LEFT, "the legacy anchor was not retained");
		check(legacy.hiddenComponents().contains("legacy:only"), "the legacy hidden component was not retained");
	}

	private static void checkSnapshotBounds() {
		Set<String> hidden = new LinkedHashSet<>();
		Set<String> compact = new LinkedHashSet<>();
		for (int index = 0; index < ClientHudPreferences.MAX_HIDDEN_COMPONENTS + 24; index++) {
			hidden.add("component_" + index);
			compact.add("compact_" + index);
		}
		List<Exclusion> exclusions = new ArrayList<>();
		for (int index = 0; index < ClientHudPreferences.MAX_EXCLUSIONS + 4; index++) {
			exclusions.add(new Exclusion("zone_" + index, "Zone " + index, true, 0.9D, 0.9D, 0.8D, 0.8D));
		}
		Map<ComponentOrderKey, List<String>> order = new LinkedHashMap<>();
		for (int index = 0; index < ClientHudPreferences.MAX_REORDER_GROUPS + 4; index++) {
			order.put(
				new ComponentOrderKey("parent_" + index, "group_" + index),
				List.of("item_" + index + "_a", "item_" + index + "_b")
			);
		}
		Snapshot bounded = new Snapshot(
			false,
			Anchor.TOP_LEFT,
			999,
			-999,
			9.0F,
			-3.0F,
			true,
			Motion.STATIC,
			4.0F,
			-2.0F,
			hidden,
			compact,
			order,
			exclusions,
			true
		).validated();

		check(!bounded.hudVisible(), "snapshot validation must not override the raw visibility choice");
		check(bounded.anchor() == Anchor.TOP_LEFT, "snapshot validation changed a valid anchor");
		check(bounded.offsetX() == 192 && bounded.offsetY() == -192, "snapshot offsets were not bounded");
		check(close(bounded.scale(), 1.5D), "snapshot scale was not bounded");
		check(close(bounded.opacity(), 0.35D), "snapshot opacity was not bounded");
		check(close(bounded.hudVolume(), 1.0D), "HUD volume was not bounded");
		check(close(bounded.countdownVolume(), 0.0D), "countdown volume was not bounded");
		check(
			bounded.hiddenComponents().size() == ClientHudPreferences.MAX_HIDDEN_COMPONENTS,
			"hidden component set exceeded its bound"
		);
		check(
			bounded.compactComponents().size() == ClientHudPreferences.MAX_COMPACT_COMPONENTS,
			"compact component set exceeded its bound"
		);
		check(
			bounded.componentOrder().size() == ClientHudPreferences.MAX_REORDER_GROUPS,
			"reorder group map exceeded its bound"
		);
		check(
			bounded.exclusions().size() == ClientHudPreferences.MAX_EXCLUSIONS,
			"exclusion list exceeded its bound"
		);
		for (Exclusion exclusion : bounded.exclusions()) {
			check(
				close(exclusion.x(), 0.2D)
					&& close(exclusion.y(), 0.2D)
					&& close(exclusion.width(), 0.8D)
					&& close(exclusion.height(), 0.8D),
				"exclusion rectangle was not clamped into the normalized screen"
			);
		}

		Snapshot finiteFallback = new Snapshot(
			true,
			Anchor.BOTTOM_RIGHT,
			0,
			0,
			Float.NaN,
			Float.POSITIVE_INFINITY,
			false,
			Motion.FULL,
			Float.NaN,
			Float.NEGATIVE_INFINITY,
			Set.of(),
			List.of(),
			false
		).validated();
		check(close(finiteFallback.scale(), 1.0D), "non-finite scale did not use its safe fallback");
		check(close(finiteFallback.opacity(), 0.92D), "non-finite opacity did not use its safe fallback");
		check(close(finiteFallback.hudVolume(), 1.0D), "non-finite HUD volume did not use its safe fallback");
		check(close(finiteFallback.countdownVolume(), 1.0D), "non-finite countdown volume did not use its safe fallback");
	}

	private static void checkProfileDefaultsPolicyNarrowingAndMigration()
		throws IOException, ReflectiveOperationException {
		ClientPolicy firstPolicy = policy(
			true,
			Anchor.TOP_LEFT,
			Set.of(Anchor.TOP_LEFT, Anchor.BOTTOM_RIGHT),
			96,
			96,
			0.75D,
			0.85D,
			1.25D,
			0.55D,
			0.78D,
			1.0D,
			true
		);
		Fixture first = fixture(true, true, true);
		ProfileKey oldVersion = new ProfileKey("test:profile", 900L);
		Snapshot defaults = ClientHudPreferences.activateProfile(
			oldVersion,
			firstPolicy,
			first.controls(),
			first.nodes()
		);
		check(defaults.hudVisible(), "a new profile inherited legacy hidden-HUD state");
		check(defaults.anchor() == Anchor.TOP_LEFT, "new profile did not use policy default_anchor");
		check(close(defaults.scale(), 0.85D), "new profile did not use policy default_scale");
		check(close(defaults.opacity(), 0.78D), "new profile did not use policy default_opacity");
		check(defaults.hiddenComponents().isEmpty(), "a new profile inherited legacy component IDs");

		ComponentOrderKey details = new ComponentOrderKey("test:root", "details");
		Snapshot raw = new Snapshot(
			false,
			Anchor.TOP_LEFT,
			96,
			-96,
			1.25F,
			0.55F,
			true,
			Motion.SIMPLIFIED,
			0.4F,
			0.3F,
			Set.of("test:a", "test:b", "test:unknown"),
			Set.of("test:a", "test:c", "test:unknown"),
			Map.of(details, List.of("test:b", "test:a", "test:unknown")),
			List.of(),
			true
		).validated();
		ClientHudPreferences.apply(oldVersion, raw);

		ClientPolicy restrictive = policy(
			false,
			Anchor.BOTTOM_RIGHT,
			Set.of(Anchor.BOTTOM_RIGHT),
			10,
			8,
			0.9D,
			1.0D,
			1.1D,
			0.8D,
			0.9D,
			0.95D,
			false
		);
		Snapshot narrowed = ClientHudPreferences.effectiveSnapshot(
			raw,
			restrictive,
			first.controls(),
			first.nodes()
		);
		check(narrowed.hudVisible(), "allow_hide=false did not force the effective HUD visible");
		check(narrowed.anchor() == Anchor.BOTTOM_RIGHT, "an unapproved anchor did not fall back to the policy default");
		check(narrowed.offsetX() == 10 && narrowed.offsetY() == -8, "policy offsets were not narrowed");
		check(close(narrowed.scale(), 1.1D), "policy maximum scale was not enforced");
		check(close(narrowed.opacity(), 0.8D), "policy minimum opacity was not enforced");
		check(narrowed.hiddenComponents().isEmpty(), "management lock did not suppress hidden IDs");
		check(narrowed.compactComponents().isEmpty(), "management lock did not suppress compact IDs");
		check(narrowed.componentOrder().isEmpty(), "management lock did not suppress reorder preferences");
		check(raw.hiddenComponents().contains("test:unknown"), "effective narrowing mutated the raw hidden set");
		check(raw.compactComponents().contains("test:unknown"), "effective narrowing mutated the raw compact set");
		Snapshot rawAfterPolicyRefresh = ClientHudPreferences.activateProfile(
			oldVersion,
			restrictive,
			first.controls(),
			first.nodes()
		);
		check(rawAfterPolicyRefresh.equals(raw), "same-version policy tightening overwrote raw stored preferences");

		Snapshot restored = ClientHudPreferences.effectiveSnapshot(
			raw,
			firstPolicy,
			first.controls(),
			first.nodes()
		);
		check(
			restored.hiddenComponents().equals(Set.of("test:a", "test:b")),
			"policy relaxation did not restore authorized raw hidden choices or leaked an unknown ID"
		);
		check(
			restored.compactComponents().equals(Set.of("test:a")),
			"compact authorization filtering is incorrect"
		);
		check(
			restored.componentOrder().get(details).equals(List.of("test:b", "test:a")),
			"same-parent same-group order was not restored and filtered"
		);
		Effective overlayView = ClientHudPreferences.effective(firstPolicy);
		check(
			overlayView.compactComponents().equals(restored.compactComponents()),
			"the production overlay boundary dropped effective compact preferences"
		);
		check(
			overlayView.componentOrder().equals(restored.componentOrder()),
			"the production overlay boundary dropped effective component order"
		);
		checkDuplicateParentDisablesReorder(first, details);

		// A different profile ID must start from its own policy defaults, not the active profile.
		ClientPolicy otherPolicy = policy(
			true,
			Anchor.BOTTOM_RIGHT,
			Set.of(Anchor.BOTTOM_RIGHT),
			32,
			32,
			0.75D,
			1.05D,
			1.25D,
			0.55D,
			0.88D,
			1.0D,
			true
		);
		Snapshot other = ClientHudPreferences.activateProfile(
			new ProfileKey("test:other", 1L),
			otherPolicy,
			first.controls(),
			first.nodes()
		);
		check(other.hudVisible(), "a different profile inherited hidden-HUD state");
		check(other.anchor() == Anchor.BOTTOM_RIGHT, "a different profile inherited the prior anchor");
		check(close(other.scale(), 1.05D) && close(other.opacity(), 0.88D), "a different profile missed policy defaults");

		// Make the opaque 900 version last-used, then migrate to a numerically smaller opaque value.
		ClientHudPreferences.activateProfile(oldVersion, firstPolicy, first.controls(), first.nodes());
		ClientPolicy upgradedPolicy = policy(
			true,
			Anchor.BOTTOM_RIGHT,
			Set.of(Anchor.BOTTOM_RIGHT),
			24,
			24,
			0.9D,
			1.0D,
			1.1D,
			0.7D,
			0.9D,
			0.95D,
			true
		);
		Fixture upgraded = fixture(false, true, true);
		ProfileKey newVersion = new ProfileKey("test:profile", 12L);
		Snapshot migrated = ClientHudPreferences.activateProfile(
			newVersion,
			upgradedPolicy,
			upgraded.controls(),
			upgraded.nodes()
		);
		check(migrated.hudVisible() == raw.hudVisible(), "a still-legal visibility choice was not migrated");
		check(migrated.anchor() == Anchor.BOTTOM_RIGHT, "an illegal old anchor was not replaced during version migration");
		check(migrated.offsetX() == 24 && migrated.offsetY() == -24, "version migration did not clamp offsets");
		check(close(migrated.scale(), 1.1D) && close(migrated.opacity(), 0.7D), "version migration did not clamp scalar choices");
		check(
			migrated.hiddenComponents().equals(Set.of("test:b")),
			"version migration retained a newly locked or unknown hidden component"
		);
		check(
			migrated.compactComponents().equals(Set.of("test:a")),
			"version migration lost a still-authorized compact component"
		);
		check(
			migrated.componentOrder().get(details).equals(List.of("test:b", "test:a")),
			"opaque lower-valued content version did not migrate from the last-used same-ID profile"
		);

		Properties persisted = new Properties();
		try (var reader = Files.newBufferedReader(preferencesFile)) {
			persisted.load(reader);
		}
		check("2".equals(persisted.getProperty("format_version")), "profile persistence format was not upgraded atomically");
		check(persisted.stringPropertyNames().stream().anyMatch(value -> value.endsWith("profile_content_version")), "profile content version was not persisted");
		check(Files.size(preferencesFile) > 0L, "profile preference file was not persisted");
		checkPersistedProfileRoundTrip(newVersion, migrated);
	}

	private static void checkDuplicateParentDisablesReorder(
		final Fixture fixture,
		final ComponentOrderKey originalGroup
	) {
		ComponentControl secondRoot = control("test:second_root", false, false, Optional.empty());
		Map<String, ClientHudNode> nodes = new LinkedHashMap<>(fixture.nodes());
		nodes.put(
			secondRoot.id(),
			new ContainerNode(
				new NodeMeta(secondRoot.id(), Type.COLUMN, Priority.CRITICAL, NodeStyle.defaults(), secondRoot),
				List.of("test:a"),
				0,
				Alignment.START,
				List.of()
			)
		);
		List<ComponentControl> controls = new ArrayList<>(fixture.controls());
		controls.add(secondRoot);
		check(
			!ClientHudPreferences.reorderGroups(controls, nodes).containsKey(originalGroup),
			"a component referenced by multiple parents was incorrectly exposed for reorder"
		);
	}

	private static void checkPersistedProfileRoundTrip(
		final ProfileKey expectedKey,
		final Snapshot expected
	) throws ReflectiveOperationException {
		Method load = ClientHudPreferences.class.getDeclaredMethod("load");
		load.setAccessible(true);
		Object loadedStore = load.invoke(null);
		Field profilesField = loadedStore.getClass().getDeclaredField("profiles");
		profilesField.setAccessible(true);
		Map<?, ?> profiles = (Map<?, ?>)profilesField.get(loadedStore);
		Object stored = profiles.get(expectedKey);
		check(stored != null, "persisted profile key did not survive a fresh bounded load");
		Method snapshot = stored.getClass().getDeclaredMethod("snapshot");
		snapshot.setAccessible(true);
		check(expected.equals(snapshot.invoke(stored)), "persisted profile raw snapshot did not round-trip");
	}

	private static void checkProfileStoreBound() throws IOException {
		ClientPolicy policy = ClientPolicy.safeDefaults();
		Fixture fixture = fixture(true, true, true);
		for (int index = 0; index < ClientHudPreferences.MAX_PROFILES + 5; index++) {
			ClientHudPreferences.activateProfile(
				new ProfileKey("test:bounded_" + index, index),
				policy,
				fixture.controls(),
				fixture.nodes()
			);
		}
		Properties persisted = new Properties();
		try (var reader = Files.newBufferedReader(preferencesFile)) {
			persisted.load(reader);
		}
		check(
			Integer.parseInt(persisted.getProperty("profile_count")) <= ClientHudPreferences.MAX_PROFILES,
			"profile preference store exceeded its LRU bound"
		);
	}

	private static Fixture fixture(
		final boolean aCanHide,
		final boolean bCanHide,
		final boolean aCanCompact
	) {
		ComponentControl root = control("test:root", false, false, Optional.empty());
		ComponentControl a = control("test:a", aCanHide, aCanCompact, Optional.of("details"));
		ComponentControl b = control("test:b", bCanHide, false, Optional.of("details"));
		ComponentControl c = control("test:c", false, false, Optional.empty());
		Map<String, ClientHudNode> nodes = new LinkedHashMap<>();
		nodes.put("test:a", text(a));
		nodes.put("test:b", text(b));
		nodes.put("test:c", text(c));
		nodes.put(
			"test:root",
			new ContainerNode(
				new NodeMeta("test:root", Type.COLUMN, Priority.CRITICAL, NodeStyle.defaults(), root),
				List.of("test:a", "test:c", "test:b"),
				2,
				Alignment.START,
				List.of()
			)
		);
		return new Fixture(List.of(a, b, c, root), Map.copyOf(nodes));
	}

	private static ComponentControl control(
		final String id,
		final boolean hide,
		final boolean compact,
		final Optional<String> group
	) {
		return new ComponentControl(id, Component.literal(id), true, hide, compact, group);
	}

	private static TextNode text(final ComponentControl control) {
		return new TextNode(
			new NodeMeta(control.id(), Type.TEXT, Priority.PRIMARY, NodeStyle.defaults(), control),
			Component.literal(control.id()),
			2,
			Overflow.ELLIPSIS,
			Alignment.START
		);
	}

	private static ClientPolicy policy(
		final boolean allowHide,
		final Anchor defaultAnchor,
		final Set<Anchor> anchors,
		final int offsetX,
		final int offsetY,
		final double minimumScale,
		final double defaultScale,
		final double maximumScale,
		final double minimumOpacity,
		final double defaultOpacity,
		final double maximumOpacity,
		final boolean componentManagement
	) {
		return new ClientPolicy(
			allowHide,
			defaultAnchor,
			anchors,
			offsetX,
			offsetY,
			minimumScale,
			defaultScale,
			maximumScale,
			minimumOpacity,
			defaultOpacity,
			maximumOpacity,
			componentManagement,
			TabCollision.DIM
		);
	}

	private static void checkPolicyConstructorBounds() {
		expectRejected(() -> policy(
			true,
			Anchor.BOTTOM_RIGHT,
			Set.of(Anchor.TOP_LEFT),
			96,
			96,
			0.75D,
			1.0D,
			1.25D,
			0.55D,
			0.92D,
			1.0D,
			true
		), "policy must reject a default anchor outside allowed_anchors");

		expectRejected(() -> policy(
			true,
			Anchor.BOTTOM_RIGHT,
			Set.of(Anchor.BOTTOM_RIGHT),
			193,
			96,
			0.75D,
			1.0D,
			1.25D,
			0.55D,
			0.92D,
			1.0D,
			true
		), "policy must reject offsets outside the safe envelope");

		expectRejected(() -> policy(
			true,
			Anchor.BOTTOM_RIGHT,
			Set.of(Anchor.BOTTOM_RIGHT),
			96,
			96,
			1.2D,
			1.0D,
			1.1D,
			0.55D,
			0.92D,
			1.0D,
			true
		), "policy must reject unordered scale bounds");
	}

	private static void expectRejected(final Runnable action, final String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// Expected strict boundary rejection.
		}
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < 0.0001D;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record Fixture(List<ComponentControl> controls, Map<String, ClientHudNode> nodes) {
		private Fixture {
			controls = List.copyOf(controls);
			nodes = Map.copyOf(nodes);
		}
	}
}
