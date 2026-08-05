package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

final class HudSelfCheckFixtures {
	private HudSelfCheckFixtures() {
	}

	static List<Source> core(final String gameTail) {
		List<Source> sources = new ArrayList<>();
		sources.add(source(DefinitionType.GAME, "main", """
			{
			  "format_version": 1,
			  "api_version": 4,
			  "content_version": 1,
			  "name": {"text": "HUD self-check"},
			  "initial_phase": "test:setup",
			  "default_role": "test:runner",
			  "default_life_state": "test:alive",
			  "task_timeline": {
			    "initial_task": "test:warmup",
			    "approval_phase": "test:setup",
			    "start_phase": "test:running"
			  }%s
			}
			""".formatted(gameTail)));
		sources.add(source(DefinitionType.ROLE, "runner", """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Runner"},
			  "tags": ["test:active_participant"]
			}
			"""));
		sources.add(source(DefinitionType.LIFE_STATE, "alive", """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Alive"}
			}
			"""));
		sources.add(source(DefinitionType.PHASE, "setup", """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Setup"},
			  "transitions": ["test:running"]
			}
			"""));
		sources.add(source(DefinitionType.PHASE, "running", """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Running"},
			  "transitions": ["test:ended"]
			}
			"""));
		sources.add(source(DefinitionType.PHASE, "ended", """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Ended"},
			  "transitions": []
			}
			"""));
		sources.add(source(DefinitionType.TASK, "warmup", """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "version": 1,
			  "kind": "warmup",
			  "name": {"text": "Warmup"},
			  "audience": {
			    "role_tags": ["test:active_participant"],
			    "exclude_host": true,
			    "online_only": false
			  },
			  "completion_policy": "event_only",
			  "counts_toward_game_time": false,
			  "results": [
			    {
			      "id": "done",
			      "name": {"text": "Done"},
			      "semantic": "success",
			      "route": {"end_phase": "test:ended"}
			    }
			  ]
			}
			"""));
		return sources;
	}

	static List<Source> validHud() {
		return List.of(
			source(DefinitionType.HUD_COMPONENT, "line", """
				{
				  "format_version": 1,
				  "type": "separator",
				  "orientation": "horizontal",
				  "thickness": 1,
				  "color": "#40505F"
				}
				"""),
			source(DefinitionType.HUD_LAYOUT, "dock", """
				{
				  "format_version": 1,
				  "surface": "dock",
				  "root": "test:line",
				  "size": {
				    "minimum_width": 100,
				    "preferred_width": 120,
				    "maximum_width": 140,
				    "maximum_height": 40,
				    "growth": "up"
				  },
				  "transition": {
				    "enter": "fade",
				    "change": "crossfade_values",
				    "exit": "fade",
				    "enter_sound": {
				      "event": "minecraft:block.note_block.pling",
				      "volume": 0.4,
				      "pitch": 1.1
				    },
				    "change_sound": {
				      "event": "minecraft:block.note_block.hat",
				      "volume": 0.2,
				      "pitch": 1.0
				    }
				  }
				}
				"""),
			source(DefinitionType.HUD_LAYOUT, "countdown", """
				{
				  "format_version": 1,
				  "surface": "countdown",
				  "root": "test:line",
				  "size": {
				    "minimum_width": 100,
				    "preferred_width": 120,
				    "maximum_width": 140,
				    "maximum_height": 40,
				    "growth": "center"
				  },
				  "transition": {
				    "enter": "focus_fade",
				    "change": "crossfade_values",
				    "exit": "fade",
				    "enter_sound": {
				      "event": "minecraft:block.note_block.pling",
				      "volume": 0.6,
				      "pitch": 1.2
				    }
				  }
				}
				"""),
			source(DefinitionType.HUD_PROFILE, "main", """
				{
				  "format_version": 1,
				  "default_layout": "test:dock"
				}
				"""),
			source(DefinitionType.COUNTDOWN, "opening", """
				{
				  "format_version": 1,
				  "name": {"text": "Opening"},
				  "duration": "10s",
				  "layout": "test:countdown"
				}
				""")
		);
	}

	static Source source(final DefinitionType type, final String path, final String json) {
		return new Source(
			type,
			Identifier.fromNamespaceAndPath("test", path),
			Identifier.fromNamespaceAndPath(
				"test",
				"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
			),
			"hud-self-check",
			json
		);
	}

	static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
