package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceEvolution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceLossMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudiencePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextRecheck;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistorySpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTimestampSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodeHeader;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RepeatSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundCategory;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure structural checks for one-host preview audience and side-effect removal. */
public final class HostMessagePreviewSanitizerSelfCheck {
	private static final UUID HOST = UUID.fromString(
		"00000000-0000-0000-0000-000000000701"
	);
	private static final UUID OTHER = UUID.fromString(
		"00000000-0000-0000-0000-000000000702"
	);

	private HostMessagePreviewSanitizerSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("HOST_MESSAGE_PREVIEW_SANITIZER_SELF_CHECK=PASS");
	}

	public static void run() {
		checkSingleHostSanitization();
		checkCallbackDependencyFailsClosed();
		checkNullsAndResultInvariant();
	}

	private static void checkSingleHostSanitization() {
		MessageCueDefinition source = sourceCue();
		var result = HostMessagePreviewSanitizer.sanitize(source, HOST);
		MessageCueDefinition preview = result.definition();

		check(
			result.host().equals(HOST)
				&& result.callTargets().equals(Set.of(HOST)),
			"preview invocation must expose exactly the explicit host target"
		);
		check(
			preview != source
				&& preview.id().equals(source.id())
				&& preview.game().isEmpty()
				&& preview.required() == source.required()
				&& preview.parameters().equals(source.parameters())
				&& preview.fields().equals(source.fields())
				&& preview.staticFallback().equals(source.staticFallback())
				&& preview.assets().equals(source.assets())
				&& preview.softLimits().equals(source.softLimits()),
			"sanitization must preserve non-side-effect cue data in a new definition"
		);
		check(
			preview.policies().context().games().isEmpty()
				&& preview.policies().context().phases().isEmpty()
				&& preview.policies().context().tasks().isEmpty()
				&& preview.policies().context().recheck()
					== ContextRecheck.BEFORE_START
				&& source.game().isPresent()
				&& !source.policies().context().games().isEmpty(),
			"host preview must bypass source game/phase/task context without widening recipients"
		);
		check(
			preview.policies().concurrency().duplicate() == DuplicateMode.RESTART
				&& preview.policies().concurrency().cooldown().isEmpty()
				&& preview.policies().concurrency().dedupeParameters().isEmpty()
				&& preview.policies().concurrency().refreshKey().isEmpty()
				&& preview.policies().concurrency().groups().isEmpty()
				&& preview.policies().controlGroups().isEmpty(),
			"preview must restart repeated requests without cooldown, refresh keys, queues, or source control groups"
		);
		check(
			source.nodes().size() == 3
				&& source.nodes().stream().anyMatch(CallbackNode.class::isInstance)
				&& source.history().isPresent()
				&& source.canonicalDocument().contains("dangerous_callback"),
			"source definition must remain unchanged"
		);
		check(
			preview.nodes().size() == 2
				&& preview.nodes().getFirst() instanceof TextNode
				&& preview.nodes().get(1) instanceof SoundNode
				&& preview.nodes().stream().noneMatch(CallbackNode.class::isInstance)
				&& preview.history().isEmpty(),
			"preview must retain only visual/sound nodes and remove history"
		);
		check(
			!preview.canonicalDocument().contains("dangerous_callback")
				&& !preview.canonicalDocument().contains("secret_history")
				&& !preview.canonicalDocument().equals(source.canonicalDocument()),
			"removed side effects must not survive in the canonical source document"
		);
		check(
			isCallTargetsOnly(preview.policies().audience().audience())
				&& preview.policies().audience().evolution()
					== AudienceEvolution.SNAPSHOT
				&& !preview.policies().audience().sensitive()
				&& preview.policies().audience().onLoss()
					== AudienceLossMode.FINALIZE,
			"cue audience must become a fixed, non-sensitive call-target snapshot"
		);
		check(
			preview.nodes().stream().allMatch(
				node -> node.policy().audience().isPresent()
					&& isCallTargetsOnly(node.policy().audience().orElseThrow())
			),
			"every retained node must explicitly override its audience to call_targets"
		);

		TextNode sourceText = (TextNode)source.nodes().getFirst();
		TextNode previewText = (TextNode)preview.nodes().getFirst();
		SoundNode sourceSound = (SoundNode)source.nodes().get(1);
		SoundNode previewSound = (SoundNode)preview.nodes().get(1);
		check(
			previewText.channel() == sourceText.channel()
				&& previewText.content().equals(sourceText.content())
				&& previewText.variants().equals(sourceText.variants())
				&& previewText.header().schedule().equals(
					sourceText.header().schedule()
				)
				&& previewText.policy().priority().equals(
					sourceText.policy().priority()
				)
				&& previewSound.sound().equals(sourceSound.sound())
				&& previewSound.header().schedule().equals(
					sourceSound.header().schedule()
				),
			"visual/sound payload, timing, and non-audience node policy must be preserved"
		);
		expectUnsupported(
			() -> result.callTargets().add(OTHER),
			"preview target set must be immutable"
		);
	}

	private static void checkCallbackDependencyFailsClosed() {
		MessageCueDefinition source = sourceCue();
		TextNode text = (TextNode)source.nodes().getFirst();
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
			text.channel(),
			text.content(),
			text.variants()
		);
		MessageCueDefinition unsafeDependency = copyWithNodes(
			source,
			List.of(callback, dependent)
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> HostMessagePreviewSanitizer.sanitize(unsafeDependency, HOST),
			"preview must fail closed instead of retiming a node that depends on a callback"
		);
	}

	private static void checkNullsAndResultInvariant() {
		MessageCueDefinition source = sourceCue();
		expectThrows(
			NullPointerException.class,
			() -> HostMessagePreviewSanitizer.sanitize(null, HOST),
			"null source must be rejected"
		);
		expectThrows(
			NullPointerException.class,
			() -> HostMessagePreviewSanitizer.sanitize(source, null),
			"null host must be rejected"
		);

		var valid = HostMessagePreviewSanitizer.sanitize(source, HOST);
		expectThrows(
			IllegalArgumentException.class,
			() -> new HostMessagePreviewSanitizer.HostPreview(
				HOST,
				Set.of(HOST, OTHER),
				valid.definition()
			),
			"result type must reject a multi-target preview bundle"
		);
		expectThrows(
			IllegalArgumentException.class,
			() -> new HostMessagePreviewSanitizer.HostPreview(
				HOST,
				Set.of(HOST),
				source
			),
			"result type must reject an unsanitized definition"
		);
	}

	private static MessageCueDefinition sourceCue() {
		AudienceSpec secretAudience = new AudienceSpec(
			AudienceSource.ALL,
			Set.of(id("hunter")),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			true,
			true
		);
		CuePolicies standard = CuePolicies.standard();
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
			standard.concurrency(),
			standard.delivery(),
			standard.lifecycle(),
			standard.accessibility(),
			standard.controlGroups(),
			standard.capture(),
			standard.defaultConflict()
		);
		NodePolicy textPolicy = new NodePolicy(
			Optional.of(secretAudience),
			Optional.of(7),
			Optional.empty()
		);
		TextNode text = new TextNode(
			"intro",
			new TimeSpan(10L),
			textPolicy,
			TextChannel.SUBTITLE,
			template("主持人安全预览"),
			Map.of(),
			Optional.empty(),
			Optional.of(new TimeSpan(20L))
		);
		SoundNode sound = new SoundNode(
			new NodeHeader(
				"sound",
				new AfterNode("intro", new TimeSpan(5L)),
				NodePolicy.inherited(),
				Optional.empty(),
				RepeatSpec.once()
			),
			id("preview_sound"),
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
			NodePolicy.inherited(),
			id("dangerous_callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN
		);
		HistorySpec history = new HistorySpec(
			localized("秘密回顾"),
			localized("不可写入的历史正文"),
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.COMPLETION,
			AudienceSpec.allOnline(),
			Set.of(),
			true
		);
		return new MessageCueDefinition(
			id("host_preview_source"),
			Optional.of(id("main")),
			true,
			policies,
			Map.of(),
			Map.of(),
			List.of(text, sound, callback),
			Optional.of(history),
			Optional.empty(),
			List.of(),
			SoftLimits.empty(),
			"{\"function\":\"pixel_tzz:dangerous_callback\",\"history\":\"secret_history\"}"
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
					new RichComponent(
						"{\"text\":\"" + text + "\"}",
						text
					)
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
