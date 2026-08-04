package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.DatapackFixtureLoader.LoadedPacks;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.MessageEnvelopeHint;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Exercises the strict V3B message parser and its isolated catalog failure boundary.
 *
 * <p>This check deliberately stays below the future playback runtime. It proves that 3B-1 can
 * publish valid message resources while preserving bounded diagnostics for optional failures and
 * blocking the owning game when a required cue is unusable.
 */
public final class MessageDefinitionSelfCheck {
	private MessageDefinitionSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkValidEffect();
		checkValidCueAndCaptureModes();
		checkSchemaSafetyBoundaries();
		checkStrictFailures();
		checkComplexityBoundary();
		checkCompilerIsolationAndRequiredBlocking();
		checkCompilerReferenceClosure();
		checkCompilerHistoryAudienceReachability();
		checkCompilerParameterAllowedNodeClosure();
		checkTextSoftLimitClosure();
		checkRequiredStaticFallback();
		checkOversizedEnvelopeIsolation();
		checkCrossTypeIdentityIsolation();
		checkDuplicateRequiredEnvelope();

		System.out.println("MESSAGE_DEFINITION_SELF_CHECK=PASS");
	}

	private static void checkValidEffect() {
		ParseResult<TextEffectDefinition> parsed = MessageDefinitionParser.parseTextEffect(
			id("self_check/full_effect"),
			"""
			{
			  "format_version": 1,
			  "max_character_sound_events": 96,
			  "typewriter": {
			    "character_interval": "37ms",
			    "cursor": {
			      "enabled": true,
			      "glyph": "█",
			      "color": "#77E6FF",
			      "blink": true,
			      "blink_period": "0.4s"
			    },
			    "fresh_color": "#FFF3A6",
			    "restore_after_characters": 4,
			    "restore_mode": "gradient"
			  },
			  "fade": {
			    "enter": "3t",
			    "hold": "1.5s",
			    "exit": "250ms"
			  },
			  "scramble": {
			    "character_interval": "1t",
			    "characters": "█▓▒░ABC123"
			  },
			  "character_sound": {
			    "sound": "minecraft:block.note_block.hat",
			    "category": "ui",
			    "every_characters": 2,
			    "skip_whitespace": true,
			    "skip_punctuation": true,
			    "volume": 0.5,
			    "pitch": 1.2
			  }
			}
			"""
		);
		check(parsed.valid(), "complete text effect must parse: " + parsed.issues());
		TextEffectDefinition effect = parsed.value().orElseThrow();
		check(
			effect.typewriter().orElseThrow().characterInterval().nanoseconds() == 37_000_000L,
			"millisecond timing must retain exact nanoseconds"
		);
		check(
			effect.typewriter().orElseThrow().cursor().blinkPeriod().orElseThrow().nanoseconds()
				== 400_000_000L,
			"decimal-second cursor timing must retain exact nanoseconds"
		);
		check(
			effect.typewriter().orElseThrow().restoreAfterCharacters() == 4,
			"fresh-character restore distance must be retained"
		);
		check(effect.scramble().isPresent(), "scramble primitive must be retained");
		check(effect.characterSound().isPresent(), "character sound track must be retained");
		check(
			effect.maxCharacterSoundEvents() == 96,
			"effect-level character-sound event budget must be retained"
		);
	}

	private static void checkValidCueAndCaptureModes() {
		ParseResult<MessageCueDefinition> parsed = MessageDefinitionParser.parseMessageCue(
			id("self_check/capture_modes"),
			"""
			{
			  "format_version": 1,
			  "game": "pixel_tzz:main",
			  "defaults": {
			    "capture": "on_display",
			    "clock": "game_time",
			    "synchronization": "per_player"
			  },
			  "parameters": {
			    "target": {
			      "type": "player",
			      "required": true
			    }
			  },
			  "fields": {
			    "display_name": {
			      "capture": "on_display",
			      "source": {
			        "argument": "target"
			      }
			    },
			    "first_score": {
			      "capture": "on_first_field",
			      "source": {
			        "component": {
			          "score": {
			            "name": "@s",
			            "objective": "pixel_tzz_demo"
			          }
			        }
			      },
			      "fallback": {
			        "text": "0"
			      }
			    },
			    "storage_value": {
			      "capture": "per_field",
			      "source": {
			        "component": {
			          "nbt": "message",
			          "storage": "pixel_tzz:acceptance_3b",
			          "interpret": false
			        }
			      }
			    }
			  },
			  "nodes": [
			    {
			      "id": "rich_text",
			      "type": "text",
			      "at": "0t",
			      "channel": "chat",
			      "fields": {
			        "nearest": {
			          "capture": "per_field",
			          "source": {
			            "component": {
			              "selector": "@p"
			            }
			          }
			        },
			        "bound_task": {
			          "capture": "on_first_field",
			          "source": {
			            "binding": "task/current/name"
			          }
			        }
			      },
			      "text": {
			        "parts": [
			          {
			            "component": {
			              "text": "『字段验收』",
			              "color": "aqua",
			              "bold": true,
			              "hover_event": {
			                "action": "show_text",
			                "value": {
			                  "text": "完整原版富文本"
			                }
			              },
			              "click_event": {
			                "action": "suggest_command",
			                "command": "/help"
			              },
			              "insertion": "pixel_tzz:self_check/capture_modes"
			            }
			          },
			          {
			            "field": {
			              "scope": "cue",
			              "id": "display_name"
			            }
			          },
			          {
			            "field": {
			              "scope": "cue",
			              "id": "first_score"
			            }
			          },
			          {
			            "field": {
			              "scope": "cue",
			              "id": "storage_value"
			            }
			          },
			          {
			            "field": {
			              "scope": "content",
			              "id": "nearest"
			            }
			          },
			          {
			            "field": {
			              "scope": "content",
			              "id": "bound_task"
			            }
			          }
			        ]
			      }
			    }
			  ]
			}
			"""
		);
		check(parsed.valid(), "capture-mode cue must parse: " + parsed.issues());
		MessageCueDefinition cue = parsed.value().orElseThrow();
		check(cue.defaults().conflict().isEmpty(), "channel conflict must remain unset by default");
		check(
			cue.defaults().clock().orElseThrow() == ClockMode.GAME_TIME,
			"explicit cue clock must be retained"
		);
		check(
			cue.defaults().synchronization().orElseThrow() == SynchronizationMode.PER_PLAYER,
			"explicit cue synchronization must be retained"
		);
		check(
			cue.fields().get("display_name").capture() == CaptureMode.ON_DISPLAY,
			"on_display field capture must be retained"
		);
		check(
			cue.fields().get("first_score").capture() == CaptureMode.ON_FIRST_FIELD,
			"on_first_field capture must be retained"
		);
		check(
			cue.fields().get("storage_value").capture() == CaptureMode.PER_FIELD,
			"per_field capture must be retained"
		);
		TextNode node = (TextNode)cue.nodes().getFirst();
		check(
			node.fields().get("nearest").capture() == CaptureMode.PER_FIELD,
			"content-local per_field capture must be retained"
		);
		check(
			node.fields().get("bound_task").source() instanceof BindingSource,
			"content-local binding source must be retained"
		);
		check(
			node.text().parts().getFirst() instanceof StaticComponentPart staticPart
				&& staticPart.component().canonicalJson().contains("\"click_event\"")
				&& staticPart.component().canonicalJson().contains("\"hover_event\""),
			"vanilla click, hover, insertion, and style metadata must survive canonicalization"
		);
		check(
			node.text().parts().stream().filter(FieldPart.class::isInstance).count() == 5L,
			"cue-level and content-local field references must both survive parsing"
		);
	}

	private static void checkSchemaSafetyBoundaries() {
		ParseResult<MessageCueDefinition> inheritedDefaults = MessageDefinitionParser.parseMessageCue(
			id("self_check/inherited_defaults"),
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "text": {
			        "parts": [
			          {
			            "component": {
			              "text": "默认值"
			            }
			          }
			        ]
			      }
			    }
			  ]
			}
			"""
		);
		check(inheritedDefaults.valid(), "cue with inherited runtime defaults must parse");
		check(
			inheritedDefaults.value().orElseThrow().defaults().clock().isEmpty()
				&& inheritedDefaults.value().orElseThrow().defaults().synchronization().isEmpty(),
			"clock and synchronization must remain unset for runtime-dependent defaults"
		);

		ParseResult<TextEffectDefinition> canonicalA = MessageDefinitionParser.parseTextEffect(
			id("self_check/canonical_a"),
			"""
			{
			  "fade": {
			    "hold": "1s",
			    "exit": "0t",
			    "enter": "0t"
			  },
			  "format_version": 1
			}
			"""
		);
		ParseResult<TextEffectDefinition> canonicalB = MessageDefinitionParser.parseTextEffect(
			id("self_check/canonical_b"),
			"""
			{
			  "format_version": 1,
			  "fade": {
			    "enter": "0t",
			    "hold": "1s",
			    "exit": "0t"
			  }
			}
			"""
		);
		check(canonicalA.valid() && canonicalB.valid(), "canonical-order fixtures must parse");
		check(
			canonicalA.value().orElseThrow().canonicalDocument()
				.equals(canonicalB.value().orElseThrow().canonicalDocument()),
			"canonical documents must not depend on object key order"
		);
		check(
			canonicalA.value().orElseThrow().maxCharacterSoundEvents()
				== MessageDefinitionParser.DEFAULT_MAX_CHARACTER_SOUND_EVENTS,
			"omitted character-sound budget must use the bounded default"
		);

		ParseResult<TextEffectDefinition> namedColor = MessageDefinitionParser.parseTextEffect(
			id("self_check/named_color"),
			"""
			{
			  "format_version": 1,
			  "typewriter": {
			    "character_interval": "1ms",
			    "cursor": {
			      "color": "dark_aqua"
			    }
			  }
			}
			"""
		);
		check(namedColor.valid(), "vanilla named text colors must remain legal");
		assertIssue(
			MessageDefinitionParser.parseTextEffect(
				id("self_check/non_vanilla_color"),
				"""
				{
				  "format_version": 1,
				  "typewriter": {
				    "character_interval": "1ms",
				    "fresh_color": "electric_blue"
				  }
				}
				"""
			),
			"INVALID_COLOR",
			"non-vanilla named colors must be rejected"
		);

		ParseResult<MessageCueDefinition> whitespaceSeparator =
			MessageDefinitionParser.parseMessageCue(
				id("self_check/whitespace_separator"),
				"""
				{
				  "format_version": 1,
				  "fields": {
				    "value": {
				      "source": {
				        "component": {
				          "text": "动态值"
				        }
				      }
				    }
				  },
				  "nodes": [
				    {
				      "id": "notice",
				      "type": "text",
				      "channel": "chat",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": " "
				            }
				          },
				          {
				            "field": {
				              "scope": "cue",
				              "id": "value"
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			);
		check(
			whitespaceSeparator.valid(),
			"whitespace-only static parts must be legal beside dynamic fields: "
				+ whitespaceSeparator.issues()
		);
		assertIssue(
			MessageDefinitionParser.parseMessageCue(
				id("self_check/whitespace_only"),
				"""
				{
				  "format_version": 1,
				  "nodes": [
				    {
				      "id": "notice",
				      "type": "text",
				      "channel": "chat",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "   "
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			),
			"EMPTY_TEXT",
			"a template without a field or visible text must be rejected"
		);
		assertIssue(
			MessageDefinitionParser.parseMessageCue(
				id("self_check/dynamic_static_component"),
				"""
				{
				  "format_version": 1,
				  "nodes": [
				    {
				      "id": "notice",
				      "type": "text",
				      "channel": "chat",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "score": {
				                "name": "@s",
				                "objective": "pixel_tzz_demo"
				              }
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			),
			"DYNAMIC_STATIC_COMPONENT",
			"dynamic vanilla content must cross the FieldSource boundary"
		);

		ParseResult<TextEffectDefinition> missingRequiredIdentifier =
			MessageDefinitionParser.parseTextEffect(
				id("self_check/missing_sound"),
				"""
				{
				  "format_version": 1,
				  "character_sound": {}
				}
				"""
			);
		check(
			missingRequiredIdentifier.issues().stream()
					.filter(issue -> issue.path().equals("/character_sound/sound"))
					.count()
				== 1L
				&& missingRequiredIdentifier.issues().stream()
					.noneMatch(
						issue -> issue.code().equals("TYPE_MISMATCH")
							|| issue.code().equals("INVALID_IDENTIFIER")
					),
			"one missing required identifier must emit only its MISSING_REQUIRED diagnostic"
		);

		assertIssue(
			MessageDefinitionParser.parseTextEffect(
				id("self_check/too_fast"),
				"""
				{
				  "format_version": 1,
				  "typewriter": {
				    "character_interval": "0.999ms"
				  }
				}
				"""
			),
			"OUT_OF_RANGE",
			"sub-millisecond character intervals must be rejected"
		);
		assertIssue(
			MessageDefinitionParser.parseTextEffect(
				id("self_check/sound_budget"),
				"""
				{
				  "format_version": 1,
				  "max_character_sound_events": 0,
				  "fade": {
				    "hold": "1s"
				  }
				}
				"""
			),
			"OUT_OF_RANGE",
			"character-sound event budgets must be positive and bounded"
		);
	}

	private static void checkStrictFailures() {
		assertIssue(
			MessageDefinitionParser.parseTextEffect(
				id("self_check/unknown_key"),
				"""
				{
				  "format_version": 1,
				  "fade": {
				    "hold": "1s"
				  },
				  "mystery": true
				}
				"""
			),
			"UNKNOWN_KEY",
			"unknown top-level key must fail closed"
		);
		assertIssue(
			MessageDefinitionParser.parseTextEffect(
				id("self_check/wrong_format"),
				"""
				{
				  "format_version": 2,
				  "fade": {
				    "hold": "1s"
				  }
				}
				"""
			),
			"UNSUPPORTED_FORMAT",
			"unsupported effect format must be rejected"
		);
		assertIssue(
			MessageDefinitionParser.parseTextEffect(
				id("self_check/bare_time"),
				"""
				{
				  "format_version": 1,
				  "typewriter": {
				    "character_interval": "40"
				  }
				}
				"""
			),
			"INVALID_TIME",
			"bare animation time must be rejected"
		);
		assertIssue(
			MessageDefinitionParser.parseMessageCue(
				id("self_check/required_without_game"),
				"""
				{
				  "format_version": 1,
				  "required": true,
				  "nodes": [
				    {
				      "id": "notice",
				      "type": "text",
				      "channel": "subtitle",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "缺少所属游戏"
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			),
			"MISSING_REQUIRED",
			"required cue without an owning game must be rejected"
		);
	}

	private static void checkComplexityBoundary() {
		ParseResult<MessageCueDefinition> atLimit = MessageDefinitionParser.parseMessageCue(
			id("self_check/max_nodes"),
			nodeBoundaryCue(MessageDefinitionParser.MAX_NODES)
		);
		check(atLimit.valid(), "cue at MAX_NODES must remain valid: " + atLimit.issues());
		check(
			atLimit.value().orElseThrow().nodes().size() == MessageDefinitionParser.MAX_NODES,
			"MAX_NODES cue must retain every legal node"
		);

		assertIssue(
			MessageDefinitionParser.parseMessageCue(
				id("self_check/too_many_nodes"),
				nodeBoundaryCue(MessageDefinitionParser.MAX_NODES + 1)
			),
			"RESOURCE_LIMIT",
			"cue above MAX_NODES must be rejected"
		);
	}

	private static void checkCompilerIsolationAndRequiredBlocking() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		sources.add(
			source(
				"self_check/optional_bad",
				"""
				{
				  "format_version": 1,
				  "nodes": [
				    {
				      "id": "optional_bad",
				      "type": "text",
				      "channel": "chat",
				      "effect": "pixel_tzz:missing_effect",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "普通坏 Cue"
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			)
		);
		sources.add(
			source(
				"self_check/required_bad",
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "required": true,
				  "nodes": [
				    {
				      "id": "required_bad",
				      "type": "callback",
				      "function": "pixel_tzz:missing_callback"
				    }
				  ]
				}
				"""
			)
		);
		sources.add(
			source(
				"self_check/optional_bad_game",
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:missing_game",
				  "nodes": [
				    {
				      "id": "optional_bad_game",
				      "type": "text",
				      "channel": "chat",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "引用不存在的游戏"
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"message failures must remain inside the message catalog: " + compilation.problems()
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		DocumentKey optionalKey = new DocumentKey(
			DefinitionType.MESSAGE_CUE,
			id("self_check/optional_bad")
		);
		DocumentKey requiredKey = new DocumentKey(
			DefinitionType.MESSAGE_CUE,
			id("self_check/required_bad")
		);
		DocumentKey missingGameKey = new DocumentKey(
			DefinitionType.MESSAGE_CUE,
			id("self_check/optional_bad_game")
		);
		check(
			catalog.disabled().containsKey(optionalKey),
			"ordinary bad cue must be disabled as one isolated item"
		);
		check(
			!catalog.disabled().get(optionalKey).required(),
			"ordinary bad cue must not become required during isolation"
		);
		check(
			catalog.disabled().containsKey(requiredKey)
				&& catalog.disabled().get(requiredKey).required(),
			"required bad cue must retain its required envelope"
		);
		check(
			catalog.disabled().containsKey(missingGameKey)
				&& catalog.disabled().get(missingGameKey).diagnostics().stream()
					.anyMatch(problem -> problem.pointer().equals("/game")),
			"missing game reference must isolate only the referencing cue"
		);
		check(
			catalog.startBlockedGames().contains(id("main")),
			"required bad cue must block only its owning game"
		);
		check(
			catalog.messageCues().containsKey(id("acceptance/field_capture")),
			"unrelated valid cue must remain published"
		);
	}

	private static void checkCompilerReferenceClosure() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		String path = "self_check/reference_closure";
		sources.add(
			source(
				path,
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "policies": {
				    "audience": {
				      "target": {
				        "roles": ["pixel_tzz:missing_role"],
				        "role_tags": ["pixel_tzz:missing_role_tag"]
				      }
				    },
				    "context": {
				      "phases": ["pixel_tzz:missing_phase"],
				      "tasks": ["pixel_tzz:missing_task"]
				    }
				  },
				  "parameters": {
				    "private_role": {
				      "type": "identifier",
				      "sources": [
				        {
				          "type": "player_data",
				          "field": "pixel_tzz:player_role"
				        }
				      ],
				      "identifier_kind": "role"
				    },
				    "missing_task": {
				      "type": "identifier",
				      "default": "pixel_tzz:missing_task",
				      "identifier_kind": "task"
				    }
				  },
				  "nodes": [
				    {
				      "id": "notice",
				      "type": "text",
				      "channel": "chat",
				      "when": {
				        "predicate": "pixel_tzz:missing_predicate"
				      },
				      "effect": "pixel_tzz:missing_effect",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "引用闭包"
				            }
				          }
				        ]
				      },
				      "variants": [
				        {
				          "when": {
				            "predicate": "pixel_tzz:missing_variant_predicate"
				          },
				          "content": {
				            "text": {
				              "parts": [
				                {
				                  "component": {
				                    "text": "变体"
				                  }
				                }
				              ]
				            }
				          }
				        }
				      ]
				    },
				    {
				      "id": "callback",
				      "type": "callback",
				      "function": "pixel_tzz:missing_callback"
				    }
				  ]
				}
				"""
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"message reference failures must remain isolated: " + compilation.problems()
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		var disabled = catalog.disabled().get(
			new DocumentKey(DefinitionType.MESSAGE_CUE, id(path))
		);
		check(disabled != null, "invalid message reference closure must disable only its cue");
		check(
			disabled.diagnostics().stream()
				.anyMatch(
					problem -> problem.code().equals("MISSING_REFERENCE")
						&& problem.pointer().equals("/policies/context/phases")
				),
			"missing phase context must be diagnosed"
		);
		check(
			disabled.diagnostics().stream()
				.anyMatch(
					problem -> problem.code().equals("UNAUTHORIZED_REFERENCE")
						&& problem.pointer().contains("/parameters/private_role/sources/0/field")
				),
			"player data without dynamic_message authorization must be rejected"
		);
		check(
			disabled.diagnostics().stream()
				.filter(problem -> problem.code().equals("MISSING_EXTERNAL_RESOURCE"))
				.count() >= 3L,
			"node, variant predicates, and callback functions must all close over live resources"
		);
		check(
			disabled.diagnostics().stream()
				.anyMatch(
					problem -> problem.pointer().equals("/nodes/0/content/effect/preset")
						&& problem.code().equals("MISSING_REFERENCE")
				),
			"base text content must close over its effect preset"
		);
		check(
			!catalog.startBlockedGames().contains(id("main")),
			"an optional cue with bad references must not block game start"
		);
	}

	private static void checkCompilerParameterAllowedNodeClosure() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		String path = "self_check/compiler_parameter_allowed_node_closure";
		sources.add(
			source(
				path,
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "parameters": {
				    "node_secret": {
				      "type": "string",
				      "default": "node",
				      "allowed_nodes": ["anchor"]
				    },
				    "history_secret": {
				      "type": "string",
				      "default": "history",
				      "allowed_nodes": ["anchor"]
				    }
				  },
				  "fields": {
				    "history_field": {
				      "source": {"binding": "argument.history_secret"}
				    }
				  },
				  "nodes": [
				    {
				      "id": "anchor",
				      "type": "text",
				      "channel": "chat",
				      "content": {
				        "fields": {
				          "node_field": {
				            "source": {"binding": "argument.node_secret"}
				          }
				        },
				        "text": {
				          "parts": [
				            {"field": {"scope": "content", "id": "node_field"}}
				          ]
				        }
				      }
				    },
				    {
				      "id": "dependent",
				      "type": "sound",
				      "schedule": {"type": "after_node", "node": "anchor"},
				      "sound": "minecraft:ui.button.click"
				    }
				  ],
				  "history": {
				    "title": {"parts": [{"component": {"text": "History"}}]},
				    "body": {
				      "parts": [
				        {"field": {"scope": "cue", "id": "history_field"}}
				      ]
				    }
				  }
				}
				"""
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"allowed_nodes failures must remain isolated: " + compilation.problems()
		);
		var disabled = compilation.snapshot().orElseThrow().messageCatalog().disabled().get(
			new DocumentKey(DefinitionType.MESSAGE_CUE, id(path))
		);
		check(disabled != null, "allowed_nodes closure failure must disable its cue");
		check(
			disabled.diagnostics().stream().anyMatch(problem ->
				problem.code().equals("UNAUTHORIZED_REFERENCE")
					&& problem.pointer().equals("/parameters/node_secret/allowed_nodes")
					&& problem.message().contains("node dependent")
			),
			"compiler diagnostics must retain transitive after_node authorization failure"
		);
		check(
			disabled.diagnostics().stream().anyMatch(problem ->
				problem.code().equals("UNAUTHORIZED_REFERENCE")
					&& problem.pointer().equals("/parameters/history_secret/allowed_nodes")
					&& problem.message().contains("history")
			),
			"compiler diagnostics must retain history fail-closed authorization failure"
		);
	}

	private static void checkCompilerHistoryAudienceReachability() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		sources.add(
			source(
				"self_check/history_current_host_direct",
				historyAudienceCue("{\"source\":\"current_host\"}", false)
			)
		);
		sources.add(
			source(
				"self_check/history_current_host_union",
				historyAudienceCue(
					"{\"any_of\":[{\"source\":\"call_targets\"},{\"source\":\"current_host\"}]}",
					false
				)
			)
		);
		sources.add(
			source(
				"self_check/history_current_host_inherited",
				historyAudienceCue(null, true)
			)
		);
		sources.add(
			source(
				"self_check/history_call_targets",
				historyAudienceCue("{\"source\":\"call_targets\"}", true)
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"unreachable message-history audiences must remain isolated: "
				+ compilation.problems()
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		for (String path : List.of(
			"self_check/history_current_host_direct",
			"self_check/history_current_host_union",
			"self_check/history_current_host_inherited"
		)) {
			var disabled = catalog.disabled().get(
				new DocumentKey(DefinitionType.MESSAGE_CUE, id(path))
			);
			check(disabled != null, path + " must be disabled");
			check(
				disabled.diagnostics().stream().anyMatch(problem ->
					problem.code().equals("UNREACHABLE_HISTORY_AUDIENCE")
						&& problem.pointer().equals("/history/audience")
						&& problem.message().contains("current_host")
				),
				path + " must retain the exact history-audience diagnostic"
			);
		}
		check(
			catalog.messageCues().containsKey(id("self_check/history_call_targets")),
			"call_targets history must remain publishable"
		);
		check(
			catalog.messageCues()
				.get(id("self_check/history_call_targets"))
				.policies()
				.audience()
				.audience()
				.source()
				.name()
				.equals("CURRENT_HOST"),
			"the reachability gate must not reject current_host as the live cue audience"
		);
	}

	private static String historyAudienceCue(
		final String historyAudience,
		final boolean hostCueAudience
	) {
		String policies = hostCueAudience
			? "\"policies\":{\"audience\":{\"target\":{\"source\":\"current_host\"}}},"
			: "";
		String historyAudienceField = historyAudience == null
			? ""
			: ",\n    \"audience\": " + historyAudience;
		return """
			{
			  "format_version": 1,
			  %s
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "text": {"parts": [{"component": {"text": "ok"}}]}
			    }
			  ],
			  "history": {
			    "title": {"parts": [{"component": {"text": "History"}}]},
			    "body": {"parts": [{"component": {"text": "Body"}}]}%s
			  }
			}
			""".formatted(policies, historyAudienceField);
	}

	private static void checkRequiredStaticFallback() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		String path = "self_check/required_static_fallback";
		sources.add(
			source(
				path,
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "required": true,
				  "nodes": [
				    {
				      "id": "notice",
				      "type": "text",
				      "channel": "chat",
				      "effect": "pixel_tzz:missing_effect",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "主演出"
				            }
				          }
				        ]
				      }
				    }
				  ],
				  "static_fallback": {
				    "channel": "chat",
				    "component": {
				      "text": "静态降级已就绪"
				    },
				    "fallback_satisfies_required": true
				  }
				}
				"""
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"required cue with a legal static fallback must remain catalog-isolated"
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		var disabled = catalog.disabled().get(
			new DocumentKey(DefinitionType.MESSAGE_CUE, id(path))
		);
		check(
			disabled != null
				&& disabled.required()
				&& disabled.requiredFallbackSatisfiesStart()
				&& disabled.staticFallback().orElseThrow().component().plainText()
					.equals("静态降级已就绪"),
			"disabled required cue must retain its executable static fallback"
		);
		check(
			!catalog.startBlockedGames().contains(id("main")),
			"fallback_satisfies_required must satisfy the required start gate"
		);
	}

	private static void checkTextSoftLimitClosure() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		sources.add(
			source(
				"self_check/visible_soft_limit",
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "nodes": [
				    {
				      "id": "repeated",
				      "type": "text",
				      "channel": "chat",
				      "repeat": {"count": 2, "interval": "1t"},
				      "text": {
				        "parts": [
				          {"component": {"text": "ab"}}
				        ]
				      }
				    }
				  ],
				  "soft_limits": {
				    "max_visible_graphemes": 3
				  }
				}
				"""
			)
		);
		sources.add(
			source(
				"self_check/unbounded_line_soft_limit",
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "fields": {
				    "name": {
				      "source": {"binding": "player.name"}
				    }
				  },
				  "nodes": [
				    {
				      "id": "unbounded",
				      "type": "text",
				      "channel": "chat",
				      "text": {
				        "parts": [
				          {"field": {"scope": "cue", "id": "name"}}
				        ]
				      }
				    }
				  ],
				  "soft_limits": {
				    "max_lines": 1
				  }
				}
				"""
			)
		);
		sources.add(
			source(
				"self_check/bounded_text_soft_limit",
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "fields": {
				    "name": {
				      "source": {"binding": "player.name"},
				      "fallback": {"text": "?"},
				      "reservation": {
				        "max_graphemes": 2,
				        "max_lines": 1
				      }
				    }
				  },
				  "nodes": [
				    {
			      "id": "bounded",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "layout": {
			          "max_lines": 1,
			          "overflow": "ellipsis"
			        },
			        "text": {
			          "parts": [
			            {"component": {"text": "a"}},
			            {"field": {"scope": "cue", "id": "name"}}
			          ]
			        }
			      }
			    }
				  ],
				  "soft_limits": {
				    "max_visible_graphemes": 3,
				    "max_lines": 1
				  }
				}
				"""
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"text soft-limit failures must remain isolated: " + compilation.problems()
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		var visible = catalog.disabled().get(
			new DocumentKey(
				DefinitionType.MESSAGE_CUE,
				id("self_check/visible_soft_limit")
			)
		);
		check(
			visible != null
				&& visible.diagnostics().stream().anyMatch(problem ->
					problem.code().equals("SOFT_LIMIT_EXCEEDED")
						&& problem.pointer().equals(
							"/soft_limits/max_visible_graphemes"
						)
				),
			"worst-case repeated grapheme budget must enforce max_visible_graphemes"
		);
		var unboundedLines = catalog.disabled().get(
			new DocumentKey(
				DefinitionType.MESSAGE_CUE,
				id("self_check/unbounded_line_soft_limit")
			)
		);
		check(
			unboundedLines != null
				&& unboundedLines.diagnostics().stream().anyMatch(problem ->
					problem.code().equals("UNBOUNDED_SOFT_LIMIT")
						&& problem.pointer().equals("/soft_limits/max_lines")
				),
			"max_lines must reject fields without a bounded line reservation or layout cap"
		);
		check(
			catalog.messageCues().containsKey(
				id("self_check/bounded_text_soft_limit")
			),
			"bounded dynamic text must remain publishable under matching soft limits"
		);
	}

	private static void checkOversizedEnvelopeIsolation() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);

		String optionalDocument = oversizedCueDocument(false, Optional.empty());
		Source optional = streamedSource(
			DefinitionType.MESSAGE_CUE,
			"self_check/oversized_optional",
			optionalDocument
		);
		check(
			optional.json().length() == DefinitionCompiler.MAX_DEFINITION_CHARACTERS + 1,
			"streaming registry must retain only the bounded oversized prefix"
		);
		check(
			optional.messageEnvelopeHint().equals(
				Optional.of(new MessageEnvelopeHint(false, Optional.empty()))
			),
			"complete oversized optional cue must retain a trusted optional envelope"
		);

		List<Source> optionalSources = new ArrayList<>(loaded.sources().values());
		optionalSources.add(optional);
		var optionalCompilation = DefinitionCompiler.compile(
			optionalSources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			optionalCompilation.valid(),
			"oversized optional cue must remain an isolated catalog failure"
		);
		var optionalCatalog = optionalCompilation.snapshot().orElseThrow().messageCatalog();
		DocumentKey optionalKey = new DocumentKey(
			DefinitionType.MESSAGE_CUE,
			id("self_check/oversized_optional")
		);
		check(
			optionalCatalog.disabled().containsKey(optionalKey)
				&& !optionalCatalog.disabled().get(optionalKey).required(),
			"oversized optional cue must not be promoted to required"
		);
		check(
			!optionalCatalog.startBlockedGames().contains(id("main")),
			"oversized optional cue must not block game start"
		);

		Source malformedOversized = streamedSource(
			DefinitionType.MESSAGE_CUE,
			"self_check/oversized_untrusted",
			"{\"padding\":\""
				+ "x".repeat(DefinitionCompiler.MAX_DEFINITION_CHARACTERS + 128)
		);
		Source malformedSmall = streamedSource(
			DefinitionType.MESSAGE_CUE,
			"self_check/small_untrusted",
			"{\"required\":true"
		);
		check(
			malformedOversized.messageEnvelopeHint().isEmpty()
				&& malformedSmall.messageEnvelopeHint().isEmpty(),
			"malformed documents must never publish a trusted envelope"
		);
		List<Source> malformedSources = new ArrayList<>(loaded.sources().values());
		malformedSources.add(malformedOversized);
		malformedSources.add(malformedSmall);
		var malformedCompilation = DefinitionCompiler.compile(
			malformedSources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			malformedCompilation.valid(),
			"malformed cue envelopes must remain isolated catalog failures"
		);
		var malformedCatalog = malformedCompilation.snapshot().orElseThrow().messageCatalog();
		for (String path : List.of(
			"self_check/oversized_untrusted",
			"self_check/small_untrusted"
		)) {
			DocumentKey key = new DocumentKey(DefinitionType.MESSAGE_CUE, id(path));
			var disabled = malformedCatalog.disabled().get(key);
			check(
				disabled != null
					&& !disabled.required()
					&& disabled.game().isEmpty(),
				"untrusted cue envelope must default to optional without an owning game"
			);
			check(
				disabled.diagnostics().stream()
					.anyMatch(problem -> problem.code().equals("UNTRUSTED_ENVELOPE")),
				"untrusted cue envelope must retain an explicit diagnostic"
			);
		}
		check(
			!malformedCatalog.startBlockedGames().contains(id("main")),
			"malformed cue text must never block game start"
		);

		String requiredDocument = oversizedCueDocument(true, Optional.of(id("main")));
		Source required = streamedSource(
			DefinitionType.MESSAGE_CUE,
			"self_check/oversized_required_tail",
			requiredDocument
		);
		check(
			!required.json().contains("\"required\"")
				&& !required.json().contains("\"game\""),
			"required/game test fields must be beyond the retained prefix"
		);
		check(
			required.messageEnvelopeHint().equals(
				Optional.of(new MessageEnvelopeHint(true, Optional.of(id("main"))))
			),
			"streaming registry must retain a required envelope declared at the file tail"
		);

		List<Source> requiredSources = new ArrayList<>(loaded.sources().values());
		requiredSources.add(required);
		var requiredCompilation = DefinitionCompiler.compile(
			requiredSources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			requiredCompilation.valid(),
			"oversized required cue must remain an isolated catalog failure"
		);
		var requiredCatalog = requiredCompilation.snapshot().orElseThrow().messageCatalog();
		DocumentKey requiredKey = new DocumentKey(
			DefinitionType.MESSAGE_CUE,
			id("self_check/oversized_required_tail")
		);
		check(
			requiredCatalog.disabled().get(requiredKey).required()
				&& requiredCatalog.disabled().get(requiredKey).game().equals(Optional.of(id("main"))),
			"oversized required cue must preserve its exact owning-game envelope"
		);
		check(
			requiredCatalog.startBlockedGames().contains(id("main")),
			"oversized required cue must block its owning game"
		);
	}

	private static void checkCrossTypeIdentityIsolation() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());

		String cueSurvivorPath = "self_check/shared_cue_survives";
		sources.add(source(cueSurvivorPath, validCueJson("cue_survivor")));
		sources.add(
			source(
				DefinitionType.TEXT_EFFECT,
				cueSurvivorPath,
				"{\"format_version\":1}"
			)
		);

		String effectSurvivorPath = "self_check/shared_effect_survives";
		sources.add(
			source(
				DefinitionType.TEXT_EFFECT,
				effectSurvivorPath,
				"""
				{
				  "format_version": 1,
				  "typewriter": {
				    "character_interval": "1t"
				  }
				}
				"""
			)
		);
		sources.add(
			source(
				effectSurvivorPath,
				"""
				{
				  "format_version": 1,
				  "nodes": [
				    {
				      "id": "bad_cue",
				      "type": "text",
				      "channel": "chat",
				      "effect": "pixel_tzz:missing_effect",
				      "text": {
				        "parts": [
				          {
				            "component": {
				              "text": "bad"
				            }
				          }
				        ]
				      }
				    }
				  ]
				}
				"""
			)
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"same-id effect/cue failures must remain isolated by definition type"
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		check(
			catalog.messageCues().containsKey(id(cueSurvivorPath)),
			"disabling a same-id text effect must not remove the valid cue"
		);
		check(
			catalog.textEffects().containsKey(id(effectSurvivorPath)),
			"disabling a same-id cue must not remove the valid text effect"
		);
	}

	private static void checkDuplicateRequiredEnvelope() {
		Path base = Path.of("examples", "pixel-tzz-base-datapack");
		LoadedPacks loaded = DatapackFixtureLoader.loadInPriorityOrder(base);
		List<Source> sources = new ArrayList<>(loaded.sources().values());
		String path = "self_check/duplicate_required_conflict";
		sources.add(
			source(path, cueWithEnvelope(false, id("main"), "first"))
		);
		sources.add(
			source(path, cueWithEnvelope(true, id("alternate"), "second"))
		);
		sources.add(
			source(path, cueWithEnvelope(false, id("main"), "third"))
		);

		var compilation = DefinitionCompiler.compile(
			sources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			compilation.valid(),
			"duplicate message identities must be isolated inside the catalog"
		);
		var catalog = compilation.snapshot().orElseThrow().messageCatalog();
		DocumentKey key = new DocumentKey(DefinitionType.MESSAGE_CUE, id(path));
		var disabled = catalog.disabled().get(key);
		check(disabled != null && disabled.required(), "duplicate required flag must use cumulative OR");
		check(
			disabled.game().isEmpty(),
			"conflicting duplicate games must not publish one misleading owner"
		);
		check(
			disabled.diagnostics().stream()
				.anyMatch(problem -> problem.code().equals("CONFLICTING_GAME_CONTEXT")),
			"conflicting duplicate games must retain an explicit diagnostic"
		);
		check(
			catalog.startBlockedGames().contains(id("main"))
				&& catalog.startBlockedGames().contains(id("alternate")),
			"required duplicate conflict must block every declared game context"
		);

		String untrustedPath = "self_check/duplicate_untrusted_optional";
		List<Source> untrustedSources = new ArrayList<>(loaded.sources().values());
		untrustedSources.add(
			source(untrustedPath, cueWithEnvelope(false, id("main"), "trusted_optional"))
		);
		untrustedSources.add(
			streamedSource(
				DefinitionType.MESSAGE_CUE,
				untrustedPath,
				"{\"required\":true"
			)
		);
		var untrustedCompilation = DefinitionCompiler.compile(
			untrustedSources,
			loaded.functions(),
			loaded.predicates()
		);
		check(
			untrustedCompilation.valid(),
			"untrusted duplicate envelope must remain isolated"
		);
		var untrustedCatalog = untrustedCompilation.snapshot().orElseThrow().messageCatalog();
		var untrustedDisabled = untrustedCatalog.disabled().get(
			new DocumentKey(DefinitionType.MESSAGE_CUE, id(untrustedPath))
		);
		check(
			untrustedDisabled != null && !untrustedDisabled.required(),
			"malformed duplicate text must not upgrade a trusted optional cue to required"
		);
		check(
			untrustedDisabled.diagnostics().stream()
				.anyMatch(problem -> problem.code().equals("UNTRUSTED_ENVELOPE")),
			"malformed duplicate must retain an untrusted-envelope diagnostic"
		);
		check(
			!untrustedCatalog.startBlockedGames().contains(id("main")),
			"malformed duplicate must not block the trusted optional cue's game"
		);
	}

	private static String nodeBoundaryCue(final int nodeCount) {
		StringBuilder json = new StringBuilder(
			"{\"format_version\":1,\"nodes\":["
		);
		for (int index = 0; index < nodeCount; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(
				"{\"id\":\"node_"
			).append(index).append(
				"\",\"type\":\"text\",\"channel\":\"chat\",\"text\":{\"parts\":[{\"component\":{\"text\":\""
			).append(index).append(
				"\"}}]}}"
			);
		}
		return json.append("]}").toString();
	}

	private static Source source(final String path, final String json) {
		return source(DefinitionType.MESSAGE_CUE, path, json);
	}

	private static Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		Identifier definitionId = id(path);
		return new Source(
			type,
			definitionId,
			Identifier.fromNamespaceAndPath(
				"pixel_tzz",
				"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
			),
			"message-definition-self-check",
			json
		);
	}

	private static String oversizedCueDocument(
		final boolean required,
		final Optional<Identifier> game
	) {
		String gameMember = game
			.map(value -> ",\"game\":\"" + value + "\"")
			.orElse("");
		return "{\"format_version\":1,\"padding\":\""
			+ "x".repeat(DefinitionCompiler.MAX_DEFINITION_CHARACTERS + 128)
			+ "\",\"nodes\":[],\"required\":"
			+ required
			+ gameMember
			+ "}";
	}

	private static Source streamedSource(
		final DefinitionType type,
		final String path,
		final String fullJson
	) {
		try {
			Method readDefinition = DefinitionRegistry.class.getDeclaredMethod(
				"readDefinition",
				java.io.Reader.class,
				DefinitionType.class
			);
			readDefinition.setAccessible(true);
			Object read = readDefinition.invoke(null, new StringReader(fullJson), type);
			Method jsonAccessor = read.getClass().getDeclaredMethod("json");
			Method envelopeAccessor = read.getClass().getDeclaredMethod("envelopeHint");
			jsonAccessor.setAccessible(true);
			envelopeAccessor.setAccessible(true);
			@SuppressWarnings("unchecked")
			Optional<MessageEnvelopeHint> envelope =
				(Optional<MessageEnvelopeHint>) envelopeAccessor.invoke(read);
			Identifier definitionId = id(path);
			return new Source(
				type,
				definitionId,
				Identifier.fromNamespaceAndPath(
					"pixel_tzz",
					"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
				),
				"message-definition-self-check",
				(String) jsonAccessor.invoke(read),
				envelope
			);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("cannot exercise registry streaming reader", error);
		}
	}

	private static String validCueJson(final String nodeId) {
		return """
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "%s",
			      "type": "text",
			      "channel": "chat",
			      "text": {
			        "parts": [
			          {
			            "component": {
			              "text": "ok"
			            }
			          }
			        ]
			      }
			    }
			  ]
			}
			""".formatted(nodeId);
	}

	private static String cueWithEnvelope(
		final boolean required,
		final Identifier game,
		final String nodeId
	) {
		return """
			{
			  "format_version": 1,
			  "game": "%s",
			  "required": %s,
			  "nodes": [
			    {
			      "id": "%s",
			      "type": "text",
			      "channel": "chat",
			      "text": {
			        "parts": [
			          {
			            "component": {
			              "text": "duplicate"
			            }
			          }
			        ]
			      }
			    }
			  ]
			}
			""".formatted(game, required, nodeId);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void assertIssue(
		final ParseResult<?> result,
		final String code,
		final String message
	) {
		check(!result.valid(), message + " (resource unexpectedly valid)");
		check(
			result.issues().stream().anyMatch(issue -> issue.code().equals(code)),
			message + " (missing " + code + ": " + result.issues() + ")"
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
