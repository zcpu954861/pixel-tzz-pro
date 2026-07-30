package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.HistorySource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerOpenPageOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Compiles the disposable 3A pressure overlay at the same priority it has in Minecraft.
 *
 * <p>The base example remains the release-facing fixture. The overlay intentionally replaces
 * only a few resources and adds enough synthetic data to exercise scrolling and long-text
 * boundaries that normal play does not naturally produce.
 */
public final class PressureFixtureSelfCheck {
	private PressureFixtureSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		Path overlay = Path.of("examples", "pixel-tzz-3a-pressure-datapack");
		check(Files.isDirectory(base), "base example data pack is missing");
		check(Files.isDirectory(overlay), "3A pressure overlay is missing");

		LoadedPacks loaded = loadInPriorityOrder(base, overlay);
		var compilation = DefinitionCompiler.compile(
			loaded.sources().values().stream().toList(),
			loaded.functions(),
			loaded.predicates()
		);
		check(compilation.valid(), "3A pressure overlay must compile: " + compilation.problems());
		var snapshot = compilation.snapshot().orElseThrow();

		Identifier gameId = id("main");
		Identifier branchTaskId = id("acceptance/main_branch");
		Identifier pressurePageId = id("player/profile_pressure");
		Identifier profileActionId = id("open_profile");
		Identifier spawnFieldId = id("hunter_spawn");

		check(
			snapshot.games().get(gameId).playerTerminal().orElseThrow().historySource()
				== HistorySource.EVENTS,
			"pressure overlay must switch player history to event rows"
		);

		var branch = snapshot.tasks().get(branchTaskId);
		Set<String> pressureEvents = new HashSet<>();
		branch.events().stream()
			.map(event -> event.id())
			.filter(eventId -> eventId.startsWith("pressure_"))
			.forEach(pressureEvents::add);
		check(pressureEvents.size() == 12, "pressure overlay must register twelve distinct history events");
		for (int index = 1; index <= 12; index++) {
			check(
				pressureEvents.contains("pressure_%02d".formatted(index)),
				"pressure history event is missing: " + index
			);
		}
		check(
			branch.events().stream()
				.filter(event -> event.id().startsWith("pressure_"))
				.allMatch(event -> event.playerHistory().isPresent()),
			"every pressure event must explicitly opt into player history"
		);

		var profileAction = snapshot.playerActions().get(profileActionId);
		check(
			profileAction.operation() instanceof PlayerOpenPageOperation openPage
				&& openPage.page().equals(pressurePageId),
			"the pressure overlay must route Personal Information to its isolated test page"
		);
		var pressurePage = snapshot.pages().get(pressurePageId);
		check(pressurePage != null, "pressure profile page is missing");
		String pressureDocument = pressurePage.canonicalDocument();
		check(
			count(pressureDocument, "\"type\":\"scroll\"") == 2
				&& pressureDocument.contains("\"id\":\"pressure_profile_left_scroll\"")
				&& pressureDocument.contains("\"id\":\"pressure_profile_right_scroll\""),
			"pressure profile must contain exactly two independently identified scroll regions"
		);
		for (int index = 1; index <= 10; index++) {
			String definitionPath = "pressure/profile_%02d".formatted(index);
			check(
				snapshot.playerData().containsKey(id(definitionPath))
					&& pressureDocument.contains("personal/pixel_tzz:" + definitionPath),
				"pressure profile grant or binding is missing: " + definitionPath
			);
		}

		var spawn = snapshot.fields().get(spawnFieldId).exclusiveChoice().orElseThrow();
		check(
			spawn.options().size() == 3
				&& spawn.options().stream().allMatch(option -> option.name().plainText().length() >= 20),
			"all three pressure spawn choices must retain long player-facing Chinese names"
		);
		check(
			loaded.functions().contains(id("acceptance_3a/pressure/record_twelve"))
				&& loaded.functions().contains(id("acceptance_3a/pressure/record_twelve_macro")),
			"pressure history generator functions are missing"
		);

		System.out.println("PRESSURE_FIXTURE_SELF_CHECK=PASS");
	}

	private static LoadedPacks loadInPriorityOrder(final Path... packs) {
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
					functions.add(
						Identifier.fromNamespaceAndPath(
							namespace,
							stripSuffix(relativeFile, ".mcfunction")
						)
					);
					continue;
				}
				if (rootDirectory.equals("predicate") && relativeFile.endsWith(".json")) {
					predicates.add(
						Identifier.fromNamespaceAndPath(namespace, stripSuffix(relativeFile, ".json"))
					);
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
				String definitionPath = stripSuffix(definitionFile, ".json");
				Identifier definitionId = Identifier.fromNamespaceAndPath(namespace, definitionPath);
				Source source = new Source(
					type,
					definitionId,
					Identifier.fromNamespaceAndPath(
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

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static String stripSuffix(final String value, final String suffix) {
		return value.substring(0, value.length() - suffix.length());
	}

	private static int count(final String value, final String token) {
		int result = 0;
		int cursor = 0;
		while ((cursor = value.indexOf(token, cursor)) >= 0) {
			result++;
			cursor += token.length();
		}
		return result;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record DefinitionKey(DefinitionType type, Identifier id) {
	}

	private record LoadedPacks(
		Map<DefinitionKey, Source> sources,
		Set<Identifier> functions,
		Set<Identifier> predicates
	) {
	}
}
