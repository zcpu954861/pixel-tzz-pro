package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.MessageParameterUseGraph;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AttachmentMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundRole;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextRecheck;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DimensionExitMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DurationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.GameContextParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTimestampSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.IdentifierKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LateArrivalMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageAssetType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OnCueEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.PlayerDataParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ReducedMotionMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RepeatCaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScoreParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScreenStartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StorageParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SynchronizationTimeoutMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextAlignment;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextOverflowMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Locks the complete V3B policy AST independently from catalog compilation and playback.
 *
 * <p>The release-facing policy fixture is parsed once as the legal matrix. Focused malformed
 * definitions then prove that authorization, references, scheduling, layout, lifecycle, history,
 * assets, and declared soft limits fail closed with stable diagnostic classes.
 */
public final class MessagePolicySchemaSelfCheck {
	private static final Path POLICY_FIXTURE = Path.of(
		"examples",
		"pixel-tzz-base-datapack",
		"data",
		"pixel_tzz",
		"pixel_tzz_pro",
		"message_cues",
		"acceptance",
		"policy_matrix.json"
	);

	private MessagePolicySchemaSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkCompletePolicyMatrix();
		checkParameterAndFieldGuardrails();
		checkParameterAllowedNodeClosure();
		checkConditionsLocalesAndVariants();
		checkEffectsLayoutDurationAndSpeaker();
		checkSchedulesRepeatsAndTriggers();
		checkAudienceContextConcurrencyAndDelivery();
		checkLifecycleAndAccessibility();
		checkHistoryAndStaticFallback();
		checkAssetPolicies();
		checkSoftLimits();

		System.out.println("MESSAGE_POLICY_SCHEMA_SELF_CHECK=PASS");
	}

	private static void checkCompletePolicyMatrix() {
		ParseResult<MessageCueDefinition> parsed = MessageDefinitionParser.parseMessageCue(
			id("acceptance/policy_matrix"),
			readFixture()
		);
		check(parsed.valid(), "complete policy fixture must parse: " + parsed.issues());
		MessageCueDefinition cue = parsed.value().orElseThrow();

		var policies = cue.policies();
		check(
			policies.timing().clock().orElseThrow() == ClockMode.GAME_TIME
				&& policies.timing().synchronization().orElseThrow()
					== SynchronizationMode.SYNCHRONIZED
				&& policies.timing().synchronizedWait().orElseThrow().nanoseconds()
					== 250_000_000L
				&& policies.timing().lateArrival() == LateArrivalMode.SEEK
				&& policies.timing().synchronizationTimeout()
					== SynchronizationTimeoutMode.START_AVAILABLE,
			"timing, late-arrival, and synchronization timeout policy must survive parsing"
		);
		check(
			policies.audience().audience().selectors().size() == 2
				&& policies.audience().audience().roles().contains(id("hunter"))
				&& policies.audience().evolution() == AudienceEvolution.LIVE_STRICT
				&& policies.audience().sensitive(),
			"union audience and sensitive live authorization must survive parsing"
		);
		check(
			policies.context().games().contains(id("main"))
				&& policies.context().phases().contains(id("running"))
				&& policies.context().tasks().contains(id("acceptance/main_branch"))
				&& policies.context().recheck() == ContextRecheck.LIVE_STRICT,
			"optional game context gates must retain all registered dimensions"
		);
		check(
			policies.concurrency().duplicate() == DuplicateMode.REFRESH
				&& policies.concurrency().dedupeParameters().equals(Set.of("target", "task_id"))
				&& policies.concurrency().refreshKey().orElseThrow().equals("target")
				&& policies.concurrency().priority() == 12,
			"duplicate, dedupe, refresh-key, and priority policy must survive parsing"
		);
		check(
			policies.delivery().offlineTargets() == OfflineTargetMode.QUEUE
				&& policies.delivery().offlineTtl().orElseThrow().nanoseconds()
					== 30_000_000_000L,
			"offline queue policy must retain its bounded TTL"
		);
		check(
			policies.lifecycle().screenStart().get(TextChannel.TITLE)
					== ScreenStartMode.CLOSE_SAFE_SCREEN
				&& policies.lifecycle().screenWaitTimeout().orElseThrow().nanoseconds()
					== 5_000_000_000L
				&& policies.lifecycle().attachment() == AttachmentMode.WORLD
				&& policies.lifecycle().dimensionExit() == DimensionExitMode.CANCEL,
			"screen, timeout, attachment, and dimension lifecycle policies must survive parsing"
		);
		check(
			policies.accessibility().allowManualComplete()
				&& policies.accessibility().reducedMotion() == ReducedMotionMode.STATIC_FINAL
				&& policies.controlGroups().contains("host_notice"),
			"accessibility and active-control groups must survive parsing"
		);

		var target = cue.parameters().get("target");
		var task = cue.parameters().get("task_id");
		check(
			target.required()
				&& target.sensitive()
				&& target.sources().size() == 4
				&& target.allowedNodes().equals(Set.of("intro", "follow_sound"))
				&& target.audience().orElseThrow().source() == AudienceSource.CALL_TARGETS,
			"authorized sensitive player parameter must retain sources and node scope"
		);
		check(
			task.identifierKind() == IdentifierKind.TASK
				&& task.sources().get(0) instanceof GameContextParameterSource
				&& task.sources().get(1) instanceof StorageParameterSource
				&& cue.parameters().get("score_value").sources().getFirst()
					instanceof ScoreParameterSource
				&& cue.parameters().get("profile_value").sources().getFirst()
					instanceof PlayerDataParameterSource,
			"structured game, storage, score, and player-data sources must retain their types"
		);
		check(
			cue.fields().get("target_name").reservation().orElseThrow()
					.maxGraphemes().orElseThrow() == 32
				&& cue.fields().get("target_name").reservation().orElseThrow()
					.estimatedWidth().orElseThrow() == 192,
			"dynamic-field layout reservation must survive parsing"
		);

		TextNode intro = (TextNode)cue.nodes().getFirst();
		check(
			intro.header().schedule() instanceof AtCueTime at
				&& at.offset().nanoseconds() == 100_000_000L
				&& intro.header().when().orElseThrow().predicate().orElseThrow()
					.equals(id("acceptance_3b/is_hunter"))
				&& intro.header().repeat().count() == 2
				&& intro.header().repeat().capture() == RepeatCaptureMode.PER_REPEAT,
			"node condition, cue-time schedule, and finite repeat policy must survive parsing"
		);
		check(
			intro.content().text().locales().keySet().equals(Set.of("zh_cn", "en_us"))
				&& intro.content().effect().orElseThrow().preset().orElseThrow()
					.equals(id("acceptance"))
				&& intro.content().effect().orElseThrow().overrides().gradient().isPresent()
				&& intro.content().effect().orElseThrow().overrides().motion().isPresent()
				&& intro.content().effect().orElseThrow().overrides().pulse().isPresent(),
			"locale fallback plus preset and advanced per-content effect overrides must survive"
		);
		check(
			intro.content().layout().alignment() == TextAlignment.LEFT
				&& intro.content().layout().overflow() == TextOverflowMode.ELLIPSIS
				&& intro.content().duration().mode() == DurationMode.FIXED_TOTAL
				&& intro.content().duration().total().orElseThrow().nanoseconds()
					== 3_000_000_000L
				&& intro.content().speaker().kind() == SpeakerKind.PLAYER_PARAMETER
				&& intro.content().characterSoundRole() == CharacterSoundRole.PRIMARY,
			"layout, fixed duration, speaker, and primary character-sound role must survive"
		);
		check(
			intro.content().text().fallback().parts().getFirst()
					instanceof StaticComponentPart first
				&& first.timing().pauseBefore().orElseThrow().nanoseconds() == 50_000_000L
				&& first.timing().pauseAfter().orElseThrow().nanoseconds() == 100_000_000L
				&& first.timing().emphasis()
				&& intro.variants().size() == 1
				&& intro.variants().getFirst().when().expression().isPresent(),
			"segment pauses, emphasis, and ordered conditional variant must survive parsing"
		);

		SoundNode sound = (SoundNode)cue.nodes().get(1);
		CallbackNode callback = (CallbackNode)cue.nodes().get(2);
		check(
			sound.header().schedule() instanceof AfterNode after
				&& after.nodeId().equals("intro")
				&& callback.header().schedule() instanceof OnCueEvent event
				&& event.trigger() == CallbackTrigger.ALL_COMPLETE,
			"node dependency and callback-event schedules must survive parsing"
		);
		check(
			cue.history().orElseThrow().taskSource() == HistoryTaskSource.PARAMETER
				&& cue.history().orElseThrow().taskParameter().orElseThrow()
					.equals("history_task_id")
				&& cue.history().orElseThrow().timestampSource()
					== HistoryTimestampSource.GAME_TIME
				&& cue.history().orElseThrow().savedFields()
					.equals(Set.of("history_target_name", "history_task_ref"))
				&& cue.parameters().get("history_target").allowedNodes().isEmpty()
				&& cue.parameters().get("history_task_id").allowedNodes().isEmpty()
				&& !cue.history().orElseThrow().replayAllowed(),
			"history must use dedicated unrestricted parameters and retain its policy fields"
		);
		check(
			cue.staticFallback().orElseThrow().channel() == TextChannel.CHAT
				&& cue.staticFallback().orElseThrow().fallbackSatisfiesRequired()
				&& cue.assets().size() == 2
				&& cue.assets().getFirst().type() == MessageAssetType.FONT
				&& cue.assets().getFirst().missing() == MissingAssetMode.FALLBACK
				&& cue.assets().get(1).missing() == MissingAssetMode.BLOCK_START,
			"static fallback and asset fallback/start requirements must survive parsing"
		);
		check(
			cue.softLimits().maxNodes().orElseThrow() == 3
				&& cue.softLimits().maxVariants().orElseThrow() == 1
				&& cue.softLimits().maxPendingOffline().orElseThrow() == 8,
			"declared cue, variant, and pending-offline soft limits must survive parsing"
		);
	}

	private static void checkParameterAndFieldGuardrails() {
		ParseResult<MessageCueDefinition> parameters = parse(
			"invalid_parameters",
			minimalCue(
				"""
				"parameters": {
				  "secret": {
				    "type": "string",
				    "sources": ["call_argument"],
				    "on_error": "use_default",
				    "allowed_nodes": ["missing"],
				    "sensitive": true,
				    "identifier_kind": "task"
				  }
				},
				"""
			)
		);
		assertIssues(
			parameters,
			"unsafe parameter policy must fail closed",
			"INSECURE_POLICY",
			"CONTRADICTORY_VALUE",
			"MISSING_REFERENCE"
		);

		ParseResult<MessageCueDefinition> reservation = parse(
			"invalid_reservation",
			minimalCue(
				"""
				"fields": {
				  "empty": {
				    "source": {"binding": "task/current/name"},
				    "reservation": {}
				  },
				  "zero": {
				    "source": {"binding": "task/current/status"},
				    "reservation": {"max_graphemes": 0}
				  }
				},
				"""
			)
		);
		assertIssues(
			reservation,
			"empty or zero field reservations must be rejected",
			"EMPTY_DEFINITION",
			"OUT_OF_RANGE"
		);
	}

	private static void checkParameterAllowedNodeClosure() {
		ParseResult<MessageCueDefinition> unrestricted = parse(
			"parameter_use_graph",
			parameterUseCue(false)
		);
		check(
			unrestricted.valid(),
			"unrestricted parameters must support every declared projection surface: "
				+ unrestricted.issues()
		);
		MessageParameterUseGraph.UseGraph uses = MessageParameterUseGraph.compile(
			unrestricted.value().orElseThrow()
		);
		Set<String> direct = Set.of(
			"direct",
			"binding",
			"header",
			"variant",
			"speaker",
			"chain"
		);
		check(
			uses.parametersForNode("anchor").equals(direct),
			"argument fields, argument.* bindings, header/variant conditions, and speakers "
				+ "must all enter the anchor dependency set: "
				+ uses.parametersForNode("anchor")
		);
		check(
			uses.parametersForNode("dependent").equals(direct),
			"after_node must transitively carry the complete anchor parameter dependency set"
		);
		check(
			uses.historyParameters().equals(
				Set.of("history_title", "history_body", "history_saved", "history_task")
			),
			"history title/body/saved_fields/task_parameter must form one closed dependency set: "
				+ uses.historyParameters()
		);

		ParseResult<MessageCueDefinition> restricted = parse(
			"parameter_allowed_node_closure",
			parameterUseCue(true)
		);
		check(!restricted.valid(), "restricted parameter uses must fail closed");
		for (String parameter : Set.of(
			"direct",
			"binding",
			"header",
			"variant",
			"speaker",
			"chain",
			"history_title",
			"history_body",
			"history_saved",
			"history_task"
		)) {
			String pointer = "/parameters/" + parameter + "/allowed_nodes";
			check(
				restricted.issues().stream().anyMatch(issue ->
					issue.code().equals("UNAUTHORIZED_REFERENCE")
						&& issue.path().equals(pointer)
				),
				"allowed_nodes closure must reject unauthorized use of " + parameter
			);
		}
		check(
			restricted.issues().stream().anyMatch(issue ->
				issue.code().equals("UNAUTHORIZED_REFERENCE")
					&& issue.path().equals("/parameters/chain/allowed_nodes")
					&& issue.message().contains("node dependent")
			),
			"after_node transitive disclosure must identify the dependent node"
		);
		check(
			restricted.issues().stream().filter(issue ->
				issue.code().equals("UNAUTHORIZED_REFERENCE")
					&& issue.message().contains("cannot be referenced by history")
			).count() == 4L,
			"history is outside node scope, so every non-empty allowed_nodes history use must fail"
		);
	}

	private static String parameterUseCue(final boolean restricted) {
		String directPolicy = restricted ? ", \"allowed_nodes\": [\"dependent\"]" : "";
		String chainPolicy = restricted ? ", \"allowed_nodes\": [\"anchor\"]" : "";
		String historyPolicy = restricted ? ", \"allowed_nodes\": [\"anchor\"]" : "";
		return """
			{
			  "format_version": 1,
			  "parameters": {
			    "direct": {"type": "string", "default": "direct"__DIRECT__},
			    "binding": {"type": "string", "default": "binding"__DIRECT__},
			    "header": {"type": "string", "default": "header"__DIRECT__},
			    "variant": {"type": "string", "default": "variant"__DIRECT__},
			    "speaker": {
			      "type": "player",
			      "required": true,
			      "sources": ["call_argument"]__DIRECT__
			    },
			    "chain": {"type": "string", "default": "chain"__CHAIN__},
			    "history_title": {"type": "string", "default": "title"__HISTORY__},
			    "history_body": {"type": "string", "default": "body"__HISTORY__},
			    "history_saved": {"type": "string", "default": "saved"__HISTORY__},
			    "history_task": {
			      "type": "identifier",
			      "default": "pixel_tzz:acceptance/main_branch",
			      "identifier_kind": "task"__HISTORY__
			    }
			  },
			  "fields": {
			    "history_title_field": {"source": {"argument": "history_title"}},
			    "history_body_field": {"source": {"binding": "argument.history_body"}},
			    "history_saved_field": {"source": {"argument": "history_saved"}}
			  },
			  "nodes": [
			    {
			      "id": "anchor",
			      "type": "text",
			      "when": {
			        "expression": {"exists": {"bind": "argument.header"}}
			      },
			      "channel": "chat",
			      "content": {
			        "fields": {
			          "direct_field": {"source": {"argument": "direct"}},
			          "binding_field": {"source": {"binding": "argument.binding"}},
			          "chain_field": {"source": {"argument": "chain"}}
			        },
			        "text": {
			          "parts": [
			            {"field": {"scope": "content", "id": "direct_field"}},
			            {"field": {"scope": "content", "id": "binding_field"}},
			            {"field": {"scope": "content", "id": "chain_field"}}
			          ]
			        },
			        "speaker": {"kind": "player_parameter", "parameter": "speaker"}
			      },
			      "variants": [
			        {
			          "when": {
			            "expression": {"exists": {"bind": "argument.variant"}}
			          },
			          "content": {
			            "text": {"parts": [{"component": {"text": "variant"}}]}
			          }
			        }
			      ]
			    },
			    {
			      "id": "dependent",
			      "type": "sound",
			      "schedule": {"type": "after_node", "node": "anchor"},
			      "sound": "minecraft:ui.button.click"
			    }
			  ],
			  "history": {
			    "title": {
			      "parts": [{"field": {"scope": "cue", "id": "history_title_field"}}]
			    },
			    "body": {
			      "parts": [{"field": {"scope": "cue", "id": "history_body_field"}}]
			    },
			    "task_source": "parameter",
			    "task_parameter": "history_task",
			    "saved_fields": ["history_saved_field"]
			  }
			}
			"""
			.replace("__DIRECT__", directPolicy)
			.replace("__CHAIN__", chainPolicy)
			.replace("__HISTORY__", historyPolicy);
	}

	private static void checkConditionsLocalesAndVariants() {
		ParseResult<MessageCueDefinition> condition = parse(
			"invalid_condition",
			minimalNode(
				"""
				{
				  "id": "notice",
				  "type": "text",
				  "when": {
				    "expression": {"exists": {"bind": "player.role.id"}},
				    "predicate": "pixel_tzz:acceptance_3b/is_hunter"
				  },
				  "channel": "chat",
				  "content": {
				    "text": {
				      "fallback": {
				        "parts": [{"component": {"text": "条件"}}]
				      },
				      "locales": {
				        "ZH CN": {
				          "parts": [{"component": {"text": "坏语言"}}]
				        }
				      }
				    }
				  },
				  "variants": [
				    {
				      "when": {"expression": {"exists": {"bind": "player.role.id"}}},
				      "content": {
				        "text": {
				          "parts": [{"component": {"text": "变体"}}]
				        }
				      }
				    }
				  ]
				}
				"""
			)
		);
		assertIssues(
			condition,
			"ambiguous condition and malformed locale must be rejected",
			"INVALID_ONE_OF",
			"INVALID_LOCALE"
		);

		assertIssues(
			parse(
				"missing_locale_fallback",
				minimalNode(
					"""
					{
					  "id": "notice",
					  "type": "text",
					  "channel": "chat",
					  "content": {
					    "text": {
					      "locales": {
					        "zh_cn": {
					          "parts": [{"component": {"text": "仅语言"}}]
					        }
					      }
					    }
					  }
					}
					"""
				)
			),
			"localized text without an explicit fallback must be rejected",
			"MISSING_REQUIRED"
		);
	}

	private static void checkEffectsLayoutDurationAndSpeaker() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_content_policy",
			minimalNode(
				"""
				{
				  "id": "notice",
				  "type": "text",
				  "channel": "chat",
				  "content": {
				    "text": {
				      "parts": [{"component": {"text": "正文"}}]
				    },
				    "effect": {
				      "overrides": {
				        "motion": {"duration": "1s"}
				      }
				    },
				    "layout": {
				      "offset_x": 999
				    },
				    "duration": {
				      "mode": "fixed_total"
				    },
				    "speaker": {
				      "kind": "player_parameter"
				    }
				  }
				}
				"""
			)
		);
		assertIssues(
			parsed,
			"empty motion, unsafe layout, incomplete duration, and speaker must be rejected",
			"EMPTY_DEFINITION",
			"OUT_OF_RANGE",
			"MISSING_REQUIRED"
		);
	}

	private static void checkSchedulesRepeatsAndTriggers() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_schedule",
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "a",
			      "type": "text",
			      "schedule": {"type": "after_node", "node": "b"},
			      "repeat": {"count": 999, "interval": "1t"},
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "A"}}]}
			      }
			    },
			    {
			      "id": "b",
			      "type": "callback",
			      "schedule": {"type": "after_node", "node": "a"},
			      "function": "pixel_tzz:acceptance_3b/message_complete"
			    },
			    {
			      "id": "c",
			      "type": "sound",
			      "schedule": {"type": "cue_event", "event": "complete"},
			      "sound": "minecraft:ui.button.click"
			    }
			  ]
			}
			"""
		);
		assertIssues(
			parsed,
			"cyclic dependencies, excessive repeats, and unknown events must be rejected",
			"CYCLIC_REFERENCE",
			"OUT_OF_RANGE",
			"INVALID_ENUM"
		);
	}

	private static void checkAudienceContextConcurrencyAndDelivery() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_root_policy",
			"""
			{
			  "format_version": 1,
			  "policies": {
			    "timing": {
			      "synchronization": "immediate",
			      "synchronized_wait": "5t"
			    },
			    "audience": {
			      "target": {
			        "source": "all",
			        "any_of": [{"source": "call_targets"}]
			      },
			      "evolution": "snapshot",
			      "sensitive": true
			    },
			    "context": {
			      "games": ["pixel_tzz:main"],
			      "phases": ["pixel_tzz:running"],
			      "tasks": ["pixel_tzz:acceptance/main_branch"]
			    },
			    "concurrency": {
			      "dedupe_parameters": ["missing"],
			      "refresh_key": "also_missing"
			    },
			    "delivery": {
			      "offline_targets": "skip",
			      "offline_ttl": "30s"
			    }
			  },
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "策略"}}]}
			      }
			    }
			  ]
			}
			"""
		);
		assertIssues(
			parsed,
			"contradictory timing, audience, dedupe, refresh, and delivery must fail closed",
			"CONTRADICTORY_VALUE",
			"INVALID_ONE_OF",
			"INSECURE_POLICY",
			"MISSING_REFERENCE"
		);
	}

	private static void checkLifecycleAndAccessibility() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_lifecycle",
			minimalCue(
				"""
				"policies": {
				  "lifecycle": {
				    "screen_start": {"title": "teleport"},
				    "screen_wait_timeout": "0t"
				  },
				  "accessibility": {
				    "allow_manual_complete": true,
				    "reduced_motion": "spin"
				  }
				},
				"""
			)
		);
		assertIssues(
			parsed,
			"unknown screen/accessibility modes and zero timeout must be rejected",
			"INVALID_ENUM",
			"OUT_OF_RANGE"
		);
	}

	private static void checkHistoryAndStaticFallback() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_history",
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "历史"}}]}
			      }
			    }
			  ],
			  "history": {
			    "title": {"parts": [{"component": {"text": "标题"}}]},
			    "body": {"parts": [{"component": {"text": "正文"}}]},
			    "task_source": "parameter",
			    "saved_fields": ["missing"]
			  },
			  "static_fallback": {
			    "channel": "chat"
			  }
			}
			"""
		);
		assertIssues(
			parsed,
			"history parameter/saved-field references and static component must be complete",
			"MISSING_REQUIRED",
			"MISSING_REFERENCE"
		);
	}

	private static void checkAssetPolicies() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_assets",
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "资源"}}]}
			      }
			    }
			  ],
			  "assets": [
			    {
			      "type": "font",
			      "id": "minecraft:default",
			      "missing": "fallback"
			    },
			    {
			      "type": "sound",
			      "id": "minecraft:ui.button.click",
			      "missing": "block_start",
			      "required_for_start": false
			    },
			    {
			      "type": "font",
			      "id": "minecraft:default"
			    }
			  ]
			}
			"""
		);
		assertIssues(
			parsed,
			"missing asset fallback, block-start contract, and duplicate asset must be rejected",
			"MISSING_REQUIRED",
			"CONTRADICTORY_VALUE",
			"DUPLICATE_VALUE"
		);
	}

	private static void checkSoftLimits() {
		ParseResult<MessageCueDefinition> parsed = parse(
			"invalid_soft_limits",
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "软上限"}}]}
			      }
			    },
			    {
			      "id": "sound",
			      "type": "sound",
			      "sound": "minecraft:ui.button.click"
			    }
			  ],
			  "soft_limits": {
			    "max_nodes": 1,
			    "max_sound_nodes": 0
			  }
			}
			"""
		);
		assertIssues(
			parsed,
			"declared soft limits below actual cue complexity must reject the resource",
			"SOFT_LIMIT_EXCEEDED"
		);
	}

	private static ParseResult<MessageCueDefinition> parse(
		final String path,
		final String json
	) {
		return MessageDefinitionParser.parseMessageCue(id("self_check/" + path), json);
	}

	private static String minimalCue(final String topLevelFragment) {
		return """
			{
			  "format_version": 1,
			  %s
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "最小演出"}}]}
			      }
			    }
			  ]
			}
			""".formatted(topLevelFragment);
	}

	private static String minimalNode(final String node) {
		return """
			{
			  "format_version": 1,
			  "nodes": [
			    %s
			  ]
			}
			""".formatted(node);
	}

	private static String readFixture() {
		try {
			return Files.readString(POLICY_FIXTURE);
		} catch (IOException error) {
			throw new AssertionError("cannot read policy fixture " + POLICY_FIXTURE, error);
		}
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void assertIssues(
		final ParseResult<?> result,
		final String message,
		final String... codes
	) {
		check(!result.valid(), message + " (resource unexpectedly valid)");
		for (String code : codes) {
			check(
				result.issues().stream().anyMatch(issue -> issue.code().equals(code)),
				message + " (missing " + code + ": " + result.issues() + ")"
			);
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
