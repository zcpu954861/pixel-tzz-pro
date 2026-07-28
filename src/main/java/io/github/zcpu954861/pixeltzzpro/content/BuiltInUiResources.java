package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ThemeDefinition;

/**
 * Safe UI resources that remain available when a data pack omits an optional theme
 * or a client-side asset check fails.
 */
public final class BuiltInUiResources {
	private static final ThemeDefinition DEFAULT_THEME = parseDefaultTheme();

	private BuiltInUiResources() {
	}

	public static ThemeDefinition defaultTheme() {
		return DEFAULT_THEME;
	}

	private static ThemeDefinition parseDefaultTheme() {
		String source = """
			{
			  "format_version": 1,
			  "tokens": {
			    "colors": {
			      "brand": "#F2C14E",
			      "info": "#55D7E6",
			      "text": "#F1F5F7",
			      "secondary_text": "#AAB4BE",
			      "success": "#5FD18A",
			      "warning": "#F2B84B",
			      "danger": "#D94B5B",
			      "panel": "#1A232E",
			      "surface": "#222E3A"
			    },
			    "spacing": {
			      "small": 4,
			      "medium": 8,
			      "large": 16
			    },
			    "fonts": {
			      "body": {
			        "asset": "minecraft:default",
			        "required": false,
			        "fallback": "minecraft:default"
			      }
			    }
			  },
			  "styles": {},
			  "motions": {
			    "panel_enter": {
			      "duration_ms": 180,
			      "delay_ms": 0,
			      "easing": "ease_out_cubic",
			      "tracks": [
			        {
			          "property": "opacity",
			          "keyframes": [
			            {"at": 0.0, "value": 0.0},
			            {"at": 1.0, "value": 1.0}
			          ]
			        },
			        {
			          "property": "translate_y",
			          "keyframes": [
			            {"at": 0.0, "value": 6.0},
			            {"at": 1.0, "value": 0.0}
			          ]
			        }
			      ],
			      "reduced_motion": "fade"
			    },
			    "panel_exit": {
			      "duration_ms": 140,
			      "delay_ms": 0,
			      "easing": "ease_in_cubic",
			      "tracks": [
			        {
			          "property": "opacity",
			          "keyframes": [
			            {"at": 0.0, "value": 1.0},
			            {"at": 1.0, "value": 0.0}
			          ]
			        },
			        {
			          "property": "translate_y",
			          "keyframes": [
			            {"at": 0.0, "value": 0.0},
			            {"at": 1.0, "value": 4.0}
			          ]
			        }
			      ],
			      "reduced_motion": "final"
			    },
			    "navigation_hover_in": {
			      "duration_ms": 180,
			      "delay_ms": 0,
			      "easing": "ease_out_cubic",
			      "tracks": [{
			        "property": "translate_x",
			        "keyframes": [
			          {"at": 0.0, "value": 0.0},
			          {"at": 1.0, "value": 7.0}
			        ]
			      }],
			      "reduced_motion": "final"
			    },
			    "navigation_hover_out": {
			      "duration_ms": 180,
			      "delay_ms": 0,
			      "easing": "ease_in_out_cubic",
			      "tracks": [{
			        "property": "translate_x",
			        "keyframes": [
			          {"at": 0.0, "value": 7.0},
			          {"at": 1.0, "value": 0.0}
			        ]
			      }],
			      "reduced_motion": "final"
			    },
			    "button_press": {
			      "duration_ms": 120,
			      "delay_ms": 0,
			      "easing": "ease_out_quart",
			      "tracks": [{
			        "property": "scale",
			        "keyframes": [
			          {"at": 0.0, "value": 1.0},
			          {"at": 0.45, "value": 0.96},
			          {"at": 1.0, "value": 1.0}
			        ]
			      }],
			      "reduced_motion": "final"
			    }
			  },
			  "sound_cues": {
			    "panel_open": {
			      "sound": "minecraft:ui.button.click",
			      "delay_ms": 0,
			      "volume": 0.45,
			      "pitch": 0.9,
			      "required": false
			    },
			    "button_hover": {
			      "sound": "minecraft:ui.button.click",
			      "delay_ms": 0,
			      "volume": 0.25,
			      "pitch": 1.2,
			      "required": false
			    },
			    "button_press": {
			      "sound": "minecraft:ui.button.click",
			      "delay_ms": 0,
			      "volume": 0.65,
			      "pitch": 1.0,
			      "required": false
			    },
			    "success": {
			      "sound": "minecraft:entity.experience_orb.pickup",
			      "delay_ms": 0,
			      "volume": 0.5,
			      "pitch": 1.15,
			      "required": false
			    },
			    "error": {
			      "sound": "minecraft:block.note_block.bass",
			      "delay_ms": 0,
			      "volume": 0.45,
			      "pitch": 0.8,
			      "required": false
			    }
			  }
			}
			""";
		UiDefinitionParser.ParseResult<ThemeDefinition> result = UiDefinitionParser.parseTheme(
			UiDefinitions.DEFAULT_THEME,
			source
		);
		if (!result.valid()) {
			throw new ExceptionInInitializerError("invalid built-in Pixel TZZ UI theme: " + result.issues());
		}
		return result.value().orElseThrow();
	}
}
