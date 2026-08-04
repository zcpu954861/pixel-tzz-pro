package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceLossMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudiencePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundRole;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConcurrencyPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionEvaluation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextRecheck;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DeliveryPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.EmptyAudienceMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistorySpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTimestampSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodeHeader;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OfflineTargetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RepeatSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextLayout;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextVariant;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DurationSpec;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure structural checks for one-viewer, side-effect-free history replay cues. */
public final class MessageHistoryReplaySanitizerSelfCheck {
	private static final UUID VIEWER = UUID.fromString(
		"00000000-0000-0000-0000-000000000731"
	);
	private static final UUID OTHER = UUID.fromString(
		"00000000-0000-0000-0000-000000000732"
	);
	private static final String PLAYER_A_COMPONENT = "{\"text\":\"玩家甲\"}";
	private static final String UNUSED_COMPONENT = "{\"text\":\"仍可保留\"}";
	private static final String FORGED_COMPONENT = "{\"text\":\"篡改\"}";

	private MessageHistoryReplaySanitizerSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_HISTORY_REPLAY_SANITIZER_SELF_CHECK=PASS");
	}

	public static void run() {
		checkSingleViewerSanitization();
		checkCallbackDependencyFailsClosed();
		checkResultInvariants();
	}

	private static void checkSingleViewerSanitization() {
		MessageCueDefinition source = sourceCue();
		var result = MessageHistoryReplaySanitizer.sanitize(
			source,
			VIEWER,
			Map.of(
				"visible_name",
				PLAYER_A_COMPONENT,
				"declared_unused",
				UNUSED_COMPONENT,
				"not_declared",
				FORGED_COMPONENT
			)
		);
		MessageCueDefinition replay = result.definition();

		check(
			result.viewer().equals(VIEWER)
				&& result.callTargets().equals(Set.of(VIEWER)),
			"history replay must target exactly its authorized viewer"
		);
		check(
			result.savedFields().equals(
				Map.of(
					"visible_name",
					PLAYER_A_COMPONENT,
					"declared_unused",
					UNUSED_COMPONENT
				)
			)
				&& !result.savedFields().containsKey("not_declared"),
			"history replay must drop saved fields absent from the current cue schema"
		);
		check(
			replay != source
				&& replay.id().equals(source.id())
				&& replay.game().isEmpty()
				&& !replay.required()
				&& !replay.parameters().isEmpty()
				&& !replay.parameters().containsKey("target")
				&& frozenCueFieldsMatch(replay, result.savedFields())
				&& replay.history().isEmpty(),
			"history replay must remove live inputs and expose saved fields only through frozen component parameters"
		);
		check(
			replay.policies().context().games().isEmpty()
				&& replay.policies().context().phases().isEmpty()
				&& replay.policies().context().tasks().isEmpty()
				&& replay.policies().context().recheck()
					== ContextRecheck.BEFORE_START,
			"history replay must remove all source context restrictions"
		);
		check(
			replay.policies().lifecycle().restart() == RestartMode.TRANSIENT
				&& replay.policies().delivery().equals(DeliveryPolicy.standard())
				&& source.policies().lifecycle().restart() == RestartMode.CONTINUE
				&& !source.policies().delivery().equals(DeliveryPolicy.standard()),
			"history replay must not persist or queue delivery across a restart"
		);
		check(
			replay.policies().controlGroups().isEmpty()
				&& replay.policies().concurrency().groups().isEmpty()
				&& replay.policies().concurrency().duplicate() == DuplicateMode.ALLOW
				&& replay.policies().concurrency().cooldown().isEmpty()
				&& !replay.policies().concurrency().dedupeTargets()
				&& replay.policies().concurrency().dedupeParameters().isEmpty()
				&& replay.policies().concurrency().refreshKey().isEmpty(),
			"history replay must remove control, queue, refresh, and dedupe membership"
		);
		check(
			isCallTargetsOnly(replay.policies().audience().audience())
				&& replay.policies().audience().evolution()
					== AudienceEvolution.SNAPSHOT
				&& !replay.policies().audience().sensitive()
				&& replay.policies().audience().onLoss()
					== AudienceLossMode.FINALIZE
				&& replay.nodes().stream().allMatch(node ->
					node.policy().audience().isPresent()
						&& isCallTargetsOnly(node.policy().audience().orElseThrow())
				),
			"cue and retained-node audiences must become viewer-only call_targets"
		);
		check(
			source.nodes().size() == 4
				&& source.nodes().stream().anyMatch(CallbackNode.class::isInstance)
				&& source.nodes().stream().anyMatch(node ->
					node.header().when().isPresent()
				)
				&& source.history().isPresent()
				&& replay.nodes().size() == 2
				&& replay.nodes().stream().noneMatch(CallbackNode.class::isInstance)
				&& replay.nodes().stream().noneMatch(node ->
					node.header().when().isPresent()
				),
			"history replay must retain only unconditional text/sound nodes and remove callbacks"
		);
		TextNode sourceText = (TextNode)source.nodes().getFirst();
		TextNode replayText = (TextNode)replay.nodes().getFirst();
		check(
			sourceText.content().speaker().kind() == SpeakerKind.PLAYER_PARAMETER
				&& sourceText.variants().getFirst().content().speaker().kind()
					== SpeakerKind.REGISTERED_ENTITY
				&& replayText.content().speaker().kind() == SpeakerKind.SYSTEM
				&& replayText.content().speaker().parameter().isEmpty()
				&& replayText.content().speaker().entity().isEmpty()
				&& replayText.variants().isEmpty(),
			"base speaker must become anonymous SYSTEM and conditional variants must be removed"
		);
		DynamicFieldDefinition sourceLocal = sourceText.content()
			.fields()
			.get("live_secret");
		DynamicFieldDefinition replayLocal = replayText.content()
			.fields()
			.get("live_secret");
		check(
			sourceLocal.source() instanceof BindingSource
				&& replayLocal.source() instanceof ArgumentSource argument
				&& !replay.parameters().containsKey(argument.parameter())
				&& replayLocal.fallback().equals(sourceLocal.fallback()),
			"text-local fields must lose their live source and remain usable only through their declared fallback"
		);
		check(
			!replay.canonicalDocument().contains("dangerous_callback")
				&& !replay.canonicalDocument().contains("secret_history")
				&& !replay.canonicalDocument().equals(source.canonicalDocument()),
			"removed side effects must not survive in the canonical document"
		);
		expectUnsupported(
			() -> result.callTargets().add(OTHER),
			"history replay target set must be immutable"
		);
		expectUnsupported(
			() -> result.savedFields().put("visible_name", FORGED_COMPONENT),
			"history replay saved fields must be immutable"
		);
	}

	private static void checkCallbackDependencyFailsClosed() {
		MessageCueDefinition source = sourceCue();
		TextNode sourceText = (TextNode)source.nodes().getFirst();
		CallbackNode callback = new CallbackNode(
			"callback_anchor",
			new TimeSpan(0L),
			NodePolicy.inherited(),
			id("dangerous_callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN
		);
		TextNode dependent = new TextNode(
			new NodeHeader(
				"dependent_text",
				new AfterNode("callback_anchor", new TimeSpan(0L)),
				NodePolicy.inherited(),
				Optional.empty(),
				RepeatSpec.once()
			),
			sourceText.channel(),
			sourceText.content(),
			sourceText.variants()
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageHistoryReplaySanitizer.sanitize(
				copyWithNodes(source, List.of(callback, dependent)),
				VIEWER,
				Map.of()
			),
			"replay must reject a retained node whose schedule depends on a removed callback"
		);
	}

	private static void checkResultInvariants() {
		MessageCueDefinition source = sourceCue();
		var valid = MessageHistoryReplaySanitizer.sanitize(
			source,
			VIEWER,
			Map.of("visible_name", PLAYER_A_COMPONENT)
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> new MessageHistoryReplaySanitizer.ReplayCue(
				VIEWER,
				Set.of(VIEWER, OTHER),
				valid.definition(),
				valid.savedFields()
			),
			"result type must reject a multi-viewer replay bundle"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> new MessageHistoryReplaySanitizer.ReplayCue(
				VIEWER,
				Set.of(VIEWER),
				source,
				Map.of()
			),
			"result type must reject an unsanitized replay definition"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> new MessageHistoryReplaySanitizer.ReplayCue(
				VIEWER,
				Set.of(VIEWER),
				valid.definition(),
				Map.of("not_declared", FORGED_COMPONENT)
			),
			"result type must reject saved fields outside the sanitized cue schema"
		);
		CuePolicies safe = valid.definition().policies();
		expectThrows(
			IllegalArgumentException.class,
			() -> new MessageHistoryReplaySanitizer.ReplayCue(
				VIEWER,
				Set.of(VIEWER),
				copyWithPolicies(
					valid.definition(),
					policiesWith(
						safe,
						new ContextPolicy(
							Set.of(id("forged_game")),
							Set.of(),
							Set.of(),
							ContextRecheck.LIVE_STRICT
						),
						safe.delivery()
					)
				),
				valid.savedFields()
			),
			"result type must reject context reintroduced after sanitization"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> new MessageHistoryReplaySanitizer.ReplayCue(
				VIEWER,
				Set.of(VIEWER),
				copyWithPolicies(
					valid.definition(),
					policiesWith(
						safe,
						safe.context(),
						new DeliveryPolicy(
							EmptyAudienceMode.FAIL,
							OfflineTargetMode.QUEUE,
							Optional.of(new TimeSpan(60_000_000L))
						)
					)
				),
				valid.savedFields()
			),
			"result type must reject offline delivery persistence reintroduced after sanitization"
		);
		expectThrows(
			NullPointerException.class,
			() -> MessageHistoryReplaySanitizer.sanitize(null, VIEWER, Map.of()),
			"null source must be rejected"
		);
		expectThrows(
			NullPointerException.class,
			() -> MessageHistoryReplaySanitizer.sanitize(source, null, Map.of()),
			"null viewer must be rejected"
		);
		expectThrows(
			NullPointerException.class,
			() -> MessageHistoryReplaySanitizer.sanitize(source, VIEWER, null),
			"null saved fields must be rejected"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> MessageHistoryReplaySanitizer.sanitize(
				source,
				VIEWER,
				Map.of("visible_name", "{\"selector\":\"@a\"}")
			),
			"saved replay fields must reject unresolved selector components"
		);
	}

	private static MessageCueDefinition sourceCue() {
		AudienceSpec secretAudience = new AudienceSpec(
			AudienceSource.ALL,
			Set.of(id("hunter")),
			Set.of(id("blue")),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			true,
			true
		);
		CuePolicies standard = CuePolicies.standard();
		ConcurrencyPolicy concurrency = new ConcurrencyPolicy(
			DuplicateMode.QUEUE,
			Optional.of(new TimeSpan(20_000_000L)),
			true,
			Set.of("target"),
			Optional.of("history_refresh"),
			standard.concurrency().chatInterrupt(),
			17,
			Set.of("danger_group")
		);
		DeliveryPolicy delivery = new DeliveryPolicy(
			EmptyAudienceMode.CALLBACKS_ONLY,
			OfflineTargetMode.QUEUE,
			Optional.of(new TimeSpan(60_000_000L))
		);
		CuePolicies policies = new CuePolicies(
			standard.timing(),
			new AudiencePolicy(
				secretAudience,
				AudienceEvolution.LIVE_STRICT,
				true,
				AudienceLossMode.CANCEL
			),
			new ContextPolicy(
				Set.of(id("other_game")),
				Set.of(id("other_phase")),
				Set.of(id("other_task")),
				ContextRecheck.LIVE_STRICT
			),
			concurrency,
			delivery,
			persistentLifecycle(standard.lifecycle()),
			standard.accessibility(),
			Set.of("danger_control"),
			standard.capture(),
			standard.defaultConflict()
		);
		NodePolicy privateNodePolicy = new NodePolicy(
			Optional.of(secretAudience),
			Optional.of(8),
			Optional.empty()
		);
		TextContent base = textContent(
			"玩家身份正文",
			new SpeakerSpec(
				SpeakerKind.PLAYER_PARAMETER,
				Optional.of("target"),
				Optional.empty()
			),
			Map.of(
				"live_secret",
				new DynamicFieldDefinition(
					CaptureMode.ON_DISPLAY,
					new BindingSource("history.live_secret"),
					Optional.of(
						new RichComponent(
							"{\"text\":\"本地回退\"}",
							"本地回退"
						)
					)
				)
			)
		);
		TextVariant variant = new TextVariant(
			new ConditionSpec(
				ConditionEvaluation.CUE_START,
				Optional.empty(),
				Optional.of(id("variant_visible"))
			),
			textContent(
				"实体变体正文",
				new SpeakerSpec(
					SpeakerKind.REGISTERED_ENTITY,
					Optional.empty(),
					Optional.of(id("narrator"))
				)
			)
		);
		TextNode text = new TextNode(
			new NodeHeader(
				"intro",
				new io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime(
					new TimeSpan(10L)
				),
				privateNodePolicy,
				Optional.empty(),
				RepeatSpec.once()
			),
			TextChannel.SUBTITLE,
			base,
			List.of(variant)
		);
		SoundNode sound = new SoundNode(
			new NodeHeader(
				"sound",
				new AfterNode("intro", new TimeSpan(5L)),
				privateNodePolicy,
				Optional.empty(),
				RepeatSpec.once()
			),
			id("history_sound"),
			SoundCategory.UI,
			0.8F,
			1.1F,
			SoundAttachment.PLAYER,
			MissingAssetMode.SILENT,
			Optional.empty()
		);
		CallbackNode callback = new CallbackNode(
			"callback",
			new TimeSpan(30L),
			privateNodePolicy,
			id("dangerous_callback"),
			CallbackExecution.AS_TARGET,
			CallbackLocation.TARGET
		);
		TextNode conditional = new TextNode(
			new NodeHeader(
				"conditional_text",
				new io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime(
					new TimeSpan(20L)
				),
				privateNodePolicy,
				Optional.of(
					new ConditionSpec(
						ConditionEvaluation.NODE_START,
						Optional.empty(),
						Optional.of(id("conditional_visible"))
					)
				),
				RepeatSpec.once()
			),
			TextChannel.SUBTITLE,
			base,
			List.of()
		);
		HistorySpec history = new HistorySpec(
			localized("秘密回顾"),
			localized("不可再次写入的历史正文"),
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.COMPLETION,
			secretAudience,
			Set.of("visible_name"),
			true
		);
		return new MessageCueDefinition(
			id("history_replay_source"),
			Optional.of(id("main")),
			true,
			policies,
			Map.of(
				"target",
				new ParameterDefinition(
					ParameterType.PLAYER,
					true,
					Optional.empty()
				)
			),
			Map.of(
				"visible_name",
				field("visible_name"),
				"declared_unused",
				field("declared_unused")
			),
			List.of(text, sound, callback, conditional),
			Optional.of(history),
			Optional.empty(),
			List.of(),
			SoftLimits.empty(),
			"{\"function\":\"pixel_tzz:dangerous_callback\",\"history\":\"secret_history\"}"
		);
	}

	private static LifecyclePolicy persistentLifecycle(
		final LifecyclePolicy source
	) {
		return new LifecyclePolicy(
			source.screenStart(),
			source.obscured(),
			source.screenWaitTimeout(),
			source.screenTimeoutAction(),
			source.maxPause(),
			source.reload(),
			RestartMode.CONTINUE,
			source.externalConflict(),
			source.reconnect(),
			source.lateJoin(),
			source.resourceReload(),
			source.respawn(),
			source.attachment(),
			source.dimensionExit()
		);
	}

	private static TextContent textContent(
		final String text,
		final SpeakerSpec speaker
	) {
		return textContent(text, speaker, Map.of());
	}

	private static TextContent textContent(
		final String text,
		final SpeakerSpec speaker,
		final Map<String, DynamicFieldDefinition> fields
	) {
		return new TextContent(
			localized(text),
			fields,
			Optional.empty(),
			TextLayout.standard(),
			DurationSpec.contentDriven(),
			speaker,
			CharacterSoundRole.MUTED,
			Optional.empty()
		);
	}

	private static boolean frozenCueFieldsMatch(
		final MessageCueDefinition replay,
		final Map<String, String> savedFields
	) {
		if (
			!replay.fields().keySet().containsAll(savedFields.keySet())
				|| replay.parameters().size() != savedFields.size()
		) {
			return false;
		}
		Set<String> usedParameters = new HashSet<>();
		for (Map.Entry<String, String> saved : savedFields.entrySet()) {
			DynamicFieldDefinition field = replay.fields().get(saved.getKey());
			if (!(field.source() instanceof ArgumentSource argument)) {
				return false;
			}
			ParameterDefinition parameter = replay.parameters().get(
				argument.parameter()
			);
			if (
				parameter == null
					|| !usedParameters.add(argument.parameter())
					|| parameter.type() != ParameterType.COMPONENT
					|| !parameter.required()
					|| parameter.canonicalDefault().filter(saved.getValue()::equals).isEmpty()
					|| !parameter.sources().isEmpty()
			) {
				return false;
			}
		}
		return usedParameters.equals(replay.parameters().keySet());
	}

	private static DynamicFieldDefinition field(final String id) {
		return new DynamicFieldDefinition(
			CaptureMode.ON_DISPLAY,
			new BindingSource("history." + id),
			Optional.empty()
		);
	}

	private static MessageCueDefinition copyWithNodes(
		final MessageCueDefinition source,
		final List<CueNode> nodes
	) {
		return new MessageCueDefinition(
			source.id(),
			source.game(),
			source.required(),
			source.policies(),
			source.parameters(),
			source.fields(),
			nodes,
			source.history(),
			source.staticFallback(),
			source.assets(),
			source.softLimits(),
			source.canonicalDocument()
		);
	}

	private static MessageCueDefinition copyWithPolicies(
		final MessageCueDefinition source,
		final CuePolicies policies
	) {
		return new MessageCueDefinition(
			source.id(),
			source.game(),
			source.required(),
			policies,
			source.parameters(),
			source.fields(),
			source.nodes(),
			source.history(),
			source.staticFallback(),
			source.assets(),
			source.softLimits(),
			source.canonicalDocument()
		);
	}

	private static CuePolicies policiesWith(
		final CuePolicies source,
		final ContextPolicy context,
		final DeliveryPolicy delivery
	) {
		return new CuePolicies(
			source.timing(),
			source.audience(),
			context,
			source.concurrency(),
			delivery,
			source.lifecycle(),
			source.accessibility(),
			source.controlGroups(),
			source.capture(),
			source.defaultConflict()
		);
	}

	private static boolean isCallTargetsOnly(final AudienceSpec audience) {
		return audience.selectors().size() == 1
			&& audience.selectors().getFirst().source()
				== AudienceSource.CALL_TARGETS
			&& audience.selectors().getFirst().roles().isEmpty()
			&& audience.selectors().getFirst().teams().isEmpty()
			&& audience.selectors().getFirst().lifeStates().isEmpty()
			&& audience.selectors().getFirst().roleTags().isEmpty()
			&& audience.selectors().getFirst().teamTags().isEmpty()
			&& audience.selectors().getFirst().lifeStateTags().isEmpty()
			&& !audience.selectors().getFirst().excludeHost()
			&& audience.selectors().getFirst().onlineOnly();
	}

	private static LocalizedTemplate localized(final String text) {
		return new LocalizedTemplate(template(text), Map.of());
	}

	private static ComponentTemplate template(final String text) {
		return new ComponentTemplate(
			List.of(
				new StaticComponentPart(
					new RichComponent("{\"text\":\"" + text + "\"}", text)
				)
			)
		);
	}

	private static Identifier id(final String path) {
		return Identifier.parse("pixel_tzz:" + path);
	}

	private static void expectUnsupported(
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected immutable result.
		}
	}

	private static void expectThrows(
		final Class<? extends Throwable> expected,
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (Throwable thrown) {
			if (!expected.isInstance(thrown)) {
				throw new AssertionError(message, thrown);
			}
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
