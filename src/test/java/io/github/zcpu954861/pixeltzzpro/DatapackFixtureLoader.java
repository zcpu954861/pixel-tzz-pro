package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Reads example data packs with the same whole-resource priority rule used by Minecraft.
 *
 * <p>Packs are supplied from lowest to highest priority. A later JSON resource with the same
 * definition type and identifier completely replaces the earlier source; functions and
 * predicates are accumulated because the definition compiler only needs their final identifiers.
 */
final class DatapackFixtureLoader {
	private DatapackFixtureLoader() {
	}

	static LoadedPacks loadInPriorityOrder(final Path... packs) {
		Map<DefinitionKey, Source> sources = new LinkedHashMap<>();
		Set<Identifier> functions = new HashSet<>();
		Set<Identifier> predicates = new HashSet<>();
		for (Path pack : packs) {
			loadPack(pack, sources, functions, predicates);
		}
		return new LoadedPacks(Map.copyOf(sources), Set.copyOf(functions), Set.copyOf(predicates));
	}

	private static void loadPack(
		final Path pack,
		final Map<DefinitionKey, Source> sources,
		final Set<Identifier> functions,
		final Set<Identifier> predicates
	) {
		Path root = pack.resolve("data");
		check(Files.isDirectory(root), "data root is missing: " + pack);
		try (var paths = Files.walk(root)) {
			for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
				Path relative = root.relativize(file);
				check(relative.getNameCount() >= 3, "invalid data path: " + relative);
				String namespace = relative.getName(0).toString();
				String rootDirectory = relative.getName(1).toString();
				String relativeFile = relative.subpath(2, relative.getNameCount())
					.toString()
					.replace('\\', '/');
				if (rootDirectory.equals("function") && relativeFile.endsWith(".mcfunction")) {
					functions.add(identifier(namespace, stripSuffix(relativeFile, ".mcfunction")));
					continue;
				}
				if (rootDirectory.equals("predicate") && relativeFile.endsWith(".json")) {
					predicates.add(identifier(namespace, stripSuffix(relativeFile, ".json")));
					continue;
				}
				check(rootDirectory.equals("pixel_tzz_pro"), "unexpected data root: " + relative);
				check(relative.getNameCount() >= 4, "definition path has no type: " + relative);
				String typeDirectory = relative.getName(2).toString();
				DefinitionType type = DefinitionType.byDirectory(typeDirectory)
					.orElseThrow(() -> new AssertionError("unknown definition type: " + typeDirectory));
				String definitionFile = relative.subpath(3, relative.getNameCount())
					.toString()
					.replace('\\', '/');
				check(definitionFile.endsWith(".json"), "definition is not JSON: " + relative);
				Identifier definitionId = identifier(
					namespace,
					stripSuffix(definitionFile, ".json")
				);
				Source source = new Source(
					type,
					definitionId,
					identifier(
						namespace,
						"pixel_tzz_pro/" + typeDirectory + "/" + definitionFile
					),
					pack.getFileName().toString(),
					Files.readString(file, StandardCharsets.UTF_8)
				);
				sources.put(new DefinitionKey(type, definitionId), source);
			}
		} catch (IOException error) {
			throw new AssertionError("failed to read data pack: " + pack, error);
		}
	}

	private static Identifier identifier(final String namespace, final String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	private static String stripSuffix(final String value, final String suffix) {
		return value.substring(0, value.length() - suffix.length());
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	record DefinitionKey(DefinitionType type, Identifier id) {
	}

	record LoadedPacks(
		Map<DefinitionKey, Source> sources,
		Set<Identifier> functions,
		Set<Identifier> predicates
	) {
		Source source(final DefinitionType type, final Identifier id) {
			return this.sources.get(new DefinitionKey(type, id));
		}
	}
}
