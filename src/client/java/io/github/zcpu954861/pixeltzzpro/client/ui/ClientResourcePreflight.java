package io.github.zcpu954861.pixeltzzpro.client.ui;

import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetReference;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.AssetType;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.PageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Checks client resource-pack capabilities before a data-driven page is shown.
 */
public final class ClientResourcePreflight {
	public static final int MAX_DIAGNOSTIC_DETAILS = 50;

	private ClientResourcePreflight() {
	}

	public static Report check(final PageDefinition page, final ThemeDefinition theme) {
		Objects.requireNonNull(page, "page");
		Objects.requireNonNull(theme, "theme");
		Minecraft minecraft = Minecraft.getInstance();
		ResourceManager resources = minecraft.getResourceManager();
		Map<ResourceKey, Diagnostic> missing = new LinkedHashMap<>();
		Map<ResourceKey, String> resolved = new LinkedHashMap<>();
		List<AssetReference> references = new ArrayList<>(page.assets().size() + theme.assets().size());
		references.addAll(page.assets());
		references.addAll(theme.assets());

		for (AssetReference reference : references) {
			ResourceKey key = ResourceKey.of(reference);
			if (present(reference.type(), reference.id(), resources, minecraft)) {
				resolved.put(key, reference.id());
				continue;
			}
			boolean fallbackUsed = reference.fallback()
				.filter(fallback -> present(reference.type(), fallback, resources, minecraft))
				.map(
					fallback -> {
						resolved.put(key, fallback);
						return true;
					}
				)
				.orElse(false);
			String code = reference.required()
				? "MISSING_REQUIRED_ASSET"
				: fallbackUsed ? "OPTIONAL_ASSET_FALLBACK" : "MISSING_OPTIONAL_ASSET";
			missing.putIfAbsent(
				key,
				new Diagnostic(
					reference.type(),
					reference.id(),
					reference.pointer(),
					reference.required(),
					fallbackUsed,
					code
				)
			);
		}

		List<Diagnostic> details = missing.values()
			.stream()
			.limit(MAX_DIAGNOSTIC_DETAILS)
			.toList();
		boolean requiredMissing = missing.values().stream().anyMatch(Diagnostic::required);
		return new Report(missing.size(), details, requiredMissing, resolved);
	}

	private static boolean present(
		final AssetType type,
		final String id,
		final ResourceManager resources,
		final Minecraft minecraft
	) {
		return switch (type) {
			case TRANSLATION -> Language.getInstance().has(id);
			case TEXTURE -> parse(id)
				.map(identifier -> resources.getResource(identifier).isPresent())
				.orElse(false);
			case FONT -> parse(id)
				.map(ClientResourcePreflight::fontAvailable)
				.orElse(false);
			case SOUND -> parse(id)
				.map(identifier -> minecraft.getSoundManager().getSoundEvent(identifier) != null)
				.orElse(false);
		};
	}

	public static boolean fontAvailable(final Identifier font) {
		return Minecraft.getInstance()
			.getResourceManager()
			.getResource(fontResource(font))
			.isPresent();
	}

	private static java.util.Optional<Identifier> parse(final String value) {
		return java.util.Optional.ofNullable(Identifier.tryParse(value));
	}

	private static Identifier fontResource(final Identifier font) {
		String path = font.getPath();
		if (!path.startsWith("font/")) {
			path = "font/" + path;
		}
		if (!path.endsWith(".json")) {
			path += ".json";
		}
		return Identifier.fromNamespaceAndPath(font.getNamespace(), path);
	}

	public record ResourceKey(AssetType type, String id, String pointer) {
		public ResourceKey {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(pointer, "pointer");
		}

		public static ResourceKey of(final AssetReference reference) {
			return new ResourceKey(reference.type(), reference.id(), reference.pointer());
		}
	}

	public record Diagnostic(
		AssetType type,
		String id,
		String pointer,
		boolean required,
		boolean fallbackUsed,
		String code
	) {
		public String serializedType() {
			return this.type.name().toLowerCase(Locale.ROOT);
		}
	}

	public record Report(
		int totalMissing,
		List<Diagnostic> details,
		boolean requiredMissing,
		Map<ResourceKey, String> resolvedAssets
	) {
		public Report {
			if (totalMissing < details.size()) {
				throw new IllegalArgumentException("total missing count cannot be smaller than retained details");
			}
			details = List.copyOf(details);
			resolvedAssets = Map.copyOf(resolvedAssets);
		}

		public boolean truncated() {
			return this.totalMissing > this.details.size();
		}

		public String resolved(final AssetReference reference) {
			return this.resolvedAssets.get(ResourceKey.of(reference));
		}
	}
}
