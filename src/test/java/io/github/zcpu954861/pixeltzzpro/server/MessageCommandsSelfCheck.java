package io.github.zcpu954861.pixeltzzpro.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DisabledMessageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.MessageCatalog;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodeHeader;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.NodePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RepeatSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.HostMessagePreviewSanitizerSelfCheck;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog.CatalogStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Production command-tree and bounded host-preview catalog checks for V3B. */
public final class MessageCommandsSelfCheck {
	private static final UUID HOST = UUID.fromString(
		"00000000-0000-0000-0000-000000000731"
	);

	private MessageCommandsSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkCommandTree();
		checkServerlessCommandProjectionSource();
		checkCommandFeedback();
		checkCatalogAndPreview();
		HostMessagePreviewSanitizerSelfCheck.run();
		System.out.println("MESSAGE_COMMANDS_SELF_CHECK=PASS");
	}

	private static void checkServerlessCommandProjectionSource() {
		check(
			MessageServerRuntime.authorizedPermissionContextLazy(
				PermissionContext.Type.SYSTEM,
				HOST,
				() -> {
					throw new AssertionError("SYSTEM projection must not resolve world state");
				}
			),
			"serverless SYSTEM command projection must remain authorized"
		);
		check(
			!MessageServerRuntime.authorizedPermissionContextLazy(
				PermissionContext.Type.OTHER,
				HOST,
				() -> {
					throw new AssertionError("OTHER projection must not resolve world state");
				}
			),
			"serverless OTHER command projection must fail closed without world state"
		);
		check(
			!MessageServerRuntime.authorizedPermissionContextLazy(
				PermissionContext.Type.PLAYER,
				HOST,
				Optional::empty
			),
			"serverless PLAYER command projection must fail closed"
		);
	}

	private static void checkCommandTree() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		MessageCommands.register(dispatcher);
		CommandNode<CommandSourceStack> root = dispatcher.getRoot();

		checkPath(root, "pixel_tzz_pro", "message", "list");
		checkPath(root, "pixel_tzz_pro", "message", "list", "page");
		CommandNode<CommandSourceStack> inspect = checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"inspect",
			"message_id"
		);
		checkIdentifierArgument(
			inspect,
			"pixel_tzz:acceptance/field_capture",
			"inspect message ID"
		);
		CommandNode<CommandSourceStack> preview = checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"preview",
			"message_id"
		);
		CommandNode<CommandSourceStack> previewStorage = checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"preview",
			"message_id",
			"with",
			"storage",
			"storage"
		);
		checkIdentifierArgument(
			previewStorage,
			"pixel_tzz:acceptance_3b",
			"preview storage ID"
		);
		checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"preview",
			"message_id",
			"with",
			"storage",
			"storage",
			"path"
		);
		check(
			preview.getChild("to") == null,
			"host preview command must not expose an alternate target selector"
		);
		checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"play",
			"message_id",
			"bypass",
			"context"
		);
		checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"play",
			"message_id",
			"with",
			"storage",
			"storage",
			"path",
			"bypass",
			"context"
		);
		checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"play",
			"message_id",
			"to",
			"targets",
			"bypass",
			"context"
		);
		checkPath(
			root,
			"pixel_tzz_pro",
			"message",
			"play",
			"message_id",
			"to",
			"targets",
			"with",
			"storage",
			"storage",
			"path",
			"bypass",
			"context"
		);
		for (String action : List.of("pause", "resume", "complete", "cancel")) {
			checkPath(
				root,
				"pixel_tzz_pro",
				"message",
				"control",
				"group",
				"group",
				action
			);
			checkPath(
				root,
				"pixel_tzz_pro",
				"message",
				"control",
				"target",
				"target",
				action
			);
		}
		checkPath(root, "pixel_tzz_pro", "message", "unauthorized_command");
		check(
			MessageServerRuntime.authorizedContextBypassPermissionContext(
				PermissionContext.Type.SYSTEM
			)
				&& !MessageServerRuntime.authorizedContextBypassPermissionContext(
					PermissionContext.Type.PLAYER
				)
				&& !MessageServerRuntime.authorizedContextBypassPermissionContext(
					PermissionContext.Type.ENTITY
				)
				&& !MessageServerRuntime.authorizedContextBypassPermissionContext(
					PermissionContext.Type.OTHER
				)
				&& MessageServerRuntime.authorizedPermissionContext(
					PermissionContext.Type.PLAYER,
					HOST,
					Optional.of(HOST)
				),
			"ordinary host authority must not widen SYSTEM-only context bypass"
		);
		check(
			!MessageCommands.hostAuthorityChanged(Optional.empty(), Optional.empty())
				&& !MessageCommands.hostAuthorityChanged(
					Optional.of(HOST),
					Optional.of(HOST)
				)
				&& MessageCommands.hostAuthorityChanged(
					Optional.empty(),
					Optional.of(HOST)
				)
				&& MessageCommands.hostAuthorityChanged(
					Optional.of(HOST),
					Optional.empty()
				)
				&& MessageCommands.hostAuthorityChanged(
					Optional.of(HOST),
					Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000732"))
				),
			"claim, clear, and transfer must refresh permission-filtered command trees"
		);
		check(
			MessageServerRuntime.localizedAuthorityMessage(
				"message invocation is outside its registered game context"
			).equals("当前游戏、阶段或任务不允许发起该动态消息。")
				&& MessageServerRuntime.localizedAuthorityMessage(
					"required canonical parameter is missing: target"
				).equals("缺少必填动态消息参数：target")
				&& MessageServerRuntime.localizedAuthorityMessage(
					"control selector matched no message instance"
				).equals("没有匹配的动态消息实例。")
				&& MessageServerRuntime.localizedAuthorityMessage(
					"unmapped internal self-check diagnostic"
				).equals("动态消息未能执行，请查看服务端日志中的详细诊断。")
				&& MessageServerRuntime.localizedAuthorityMessage(
					"参数无法解析或类型不匹配：target"
				).equals("参数无法解析或类型不匹配：target"),
			"command-facing authority diagnostics must remain Chinese and preserve Chinese resolver details"
		);
	}

	private static void checkCommandFeedback() {
		UUID other = UUID.fromString("00000000-0000-0000-0000-000000000732");
		check(
			MessageCommands.unauthorizedMessage(
				PermissionContext.Type.PLAYER,
				other,
				Optional.empty(),
				false
			).contains("尚未指定主持人")
				&& MessageCommands.unauthorizedMessage(
					PermissionContext.Type.PLAYER,
					other,
					Optional.of(HOST),
					false
				).contains("尚未登记游戏身份")
				&& MessageCommands.unauthorizedMessage(
					PermissionContext.Type.PLAYER,
					other,
					Optional.of(HOST),
					true
				).contains("不是主持人")
				&& MessageCommands.unauthorizedMessage(
					PermissionContext.Type.PLAYER,
					other,
					Optional.of(HOST),
					true
				).contains("非主持人 OP"),
			"no-host, no-identity, and non-host OP denials must be explicit"
		);

		Identifier cue = id("acceptance/field_capture");
		Component playHeader = MessageCommands.playHeaderLine(cue, false);
		Component playStatus = MessageCommands.playStatusLine("已发起", 3);
		Component control = MessageCommands.controlStatusLine("已暂停", 2);
		Component failure = MessageCommands.failureLine("当前玩家不是主持人");
		check(
			hasColor(playHeader, "『动态消息已发起』", ChatFormatting.AQUA)
				&& hasColor(playStatus, "已发起", ChatFormatting.GREEN)
				&& hasColor(playStatus, "3", ChatFormatting.GOLD)
				&& hasColor(control, "『动态消息控制』", ChatFormatting.AQUA)
				&& hasColor(control, "已暂停", ChatFormatting.GREEN)
				&& hasColor(control, "2", ChatFormatting.GOLD)
				&& hasColor(failure, "『操作失败』", ChatFormatting.RED)
				&& hasColor(failure, "当前玩家不是主持人", ChatFormatting.RED),
			"play, control, and failure feedback must retain semantic colors"
		);
	}

	private static boolean hasColor(
		final Component component,
		final String text,
		final ChatFormatting color
	) {
		TextColor expected = TextColor.fromLegacyFormat(color);
		return component.getString().equals(text) && expected.equals(component.getStyle().getColor())
			|| component.getSiblings().stream().anyMatch(child ->
				child.getString().equals(text)
					&& expected.equals(child.getStyle().getColor())
			);
	}

	private static void checkIdentifierArgument(
		final CommandNode<CommandSourceStack> node,
		final String input,
		final String label
	) {
		check(node instanceof ArgumentCommandNode<?, ?>, label + " must be an argument node");
		var argument = (ArgumentCommandNode<?, ?>)node;
		check(
			argument.getType() instanceof IdentifierArgument,
			label + " must use Minecraft's namespaced identifier parser"
		);
		StringReader reader = new StringReader(input + " next");
		try {
			Identifier parsed = ((IdentifierArgument)argument.getType()).parse(reader);
			check(parsed.equals(Identifier.parse(input)), label + " must retain the complete ID");
			check(reader.getRemaining().equals(" next"), label + " must stop at whitespace");
		} catch (CommandSyntaxException error) {
			throw new AssertionError(label + " rejected a valid namespaced ID", error);
		}
	}

	private static void checkCatalogAndPreview() {
		DefinitionSnapshot snapshot = fixtureSnapshot();
		var firstResult = MessageCommandCatalog.list(snapshot, 1);
		var secondResult = MessageCommandCatalog.list(snapshot, 2);
		check(firstResult.accepted() && secondResult.accepted(), "catalog pages must exist");
		var first = firstResult.value().orElseThrow();
		var second = secondResult.value().orElseThrow();
		check(
			first.generation() == 42L
				&& first.page() == 1
				&& first.pages() == 2
				&& first.totalEntries() == 14
				&& first.entries().size() == MessageCommandCatalog.PAGE_SIZE
				&& second.entries().size() == 2,
			"catalog must expose deterministic bounded pagination"
		);
		check(
			first.entries().getFirst().id().equals(id("acceptance/policy_matrix"))
				&& second.entries().getFirst().id().equals(id("catalog/disabled"))
				&& second.entries().getLast().id().equals(id("catalog/unsafe")),
			"enabled and disabled entries must share one identifier-sorted catalog"
		);
		var headerLine = MessageCommands.catalogHeaderLine(first);
		var entryLine = MessageCommands.catalogEntryLine(first.entries().getFirst());
		check(
			headerLine.getString().contains("动态消息目录")
				&& headerLine.getString().contains("1/2")
				&& entryLine.getString().startsWith("  • 3节点")
				&& entryLine.getString().contains("可用 · 必需")
				&& entryLine.getSiblings().stream().anyMatch(component ->
					component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand command
						&& command.command().equals(
							"/pixel_tzz_pro message inspect pixel_tzz:acceptance/policy_matrix"
						)
				),
			"catalog output must provide colored hierarchy and a clickable inspect affordance"
		);
		check(
			second.entries().getFirst().status() == CatalogStatus.DISABLED
				&& second.entries().getFirst().diagnosticCount() == 8,
			"disabled catalog entry must retain its bounded diagnostic count"
		);
		check(
			!MessageCommandCatalog.list(snapshot, 0).accepted()
				&& !MessageCommandCatalog.list(snapshot, 3).accepted(),
			"invalid catalog pages must fail closed"
		);

		var inspection = MessageCommandCatalog.inspect(
			snapshot,
			id("acceptance/policy_matrix")
		).value().orElseThrow();
		check(
			inspection.status() == CatalogStatus.ENABLED
				&& inspection.required()
				&& inspection.nodeCount() == 3
				&& inspection.textNodeCount() == 1
				&& inspection.soundNodes() == 1
				&& inspection.callbackNodes() == 1
				&& inspection.parameters().size() == 6
				&& inspection.historyEnabled()
				&& inspection.assetCount() == 2
				&& inspection.requiredAssetCount() == 1
				&& inspection.source().isPresent(),
			"enabled inspection must retain bounded operational metadata"
		);
		check(
			inspection.parameters().stream().anyMatch(parameter -> parameter.sensitive()),
			"parameter inspection must retain sensitivity without exposing values"
		);
		Component inspectionHeader = MessageCommands.inspectHeaderLine(inspection.id());
		Component inspectionStatus = MessageCommands.inspectStatusLine(inspection);
		check(
			inspectionHeader.getString().contains("动态消息审阅")
				&& inspectionHeader.getString().contains(inspection.id().toString())
				&& inspectionHeader.getSiblings().stream().anyMatch(component ->
					TextColor.fromLegacyFormat(ChatFormatting.AQUA).equals(
						component.getStyle().getColor()
					)
						&& component.getStyle().getClickEvent()
							instanceof ClickEvent.RunCommand
				),
			"inspection header must keep a cyan clickable cue identifier"
		);
		check(
			inspectionStatus.getSiblings().stream().anyMatch(component ->
				component.getString().equals("可用")
					&& TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(
						component.getStyle().getColor()
					)
			)
				&& inspectionStatus.getSiblings().stream().anyMatch(component ->
					component.getString().equals("必需定义")
						&& TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(
							component.getStyle().getColor()
						)
				),
			"inspection status must use semantic green and gold components"
		);

		var disabled = MessageCommandCatalog.inspect(
			snapshot,
			id("catalog/disabled")
		).value().orElseThrow();
		check(
			disabled.status() == CatalogStatus.DISABLED
				&& disabled.diagnostics().size()
					== MessageCommandCatalog.MAX_RETAINED_DIAGNOSTICS,
			"disabled inspection must cap retained diagnostics"
		);
		check(
			!MessageCommandCatalog.inspect(snapshot, id("catalog/missing")).accepted(),
			"missing inspection must be rejected"
		);

		var preview = MessageCommandCatalog.preview(
			snapshot,
			id("acceptance/policy_matrix"),
			HOST
		).value().orElseThrow();
		check(
			preview.callTargets().equals(Set.of(HOST))
				&& preview.definition().nodes().stream()
					.noneMatch(CallbackNode.class::isInstance)
				&& preview.definition().history().isEmpty()
				&& preview.definition().game().isEmpty()
				&& preview.definition().policies().context().games().isEmpty()
				&& preview.definition().policies().context().phases().isEmpty()
				&& preview.definition().policies().context().tasks().isEmpty()
				&& preview.definition().policies().concurrency().duplicate()
					== DuplicateMode.RESTART
				&& preview.definition().policies().concurrency().groups().isEmpty()
				&& preview.definition().policies().controlGroups().isEmpty(),
			"preview must bind only the host, restart repeated previews, and remove durable/control side effects"
		);
		check(
			!MessageCommandCatalog.preview(snapshot, id("catalog/disabled"), HOST).accepted()
				&& !MessageCommandCatalog.preview(snapshot, id("catalog/missing"), HOST).accepted()
				&& !MessageCommandCatalog.preview(snapshot, id("catalog/unsafe"), HOST).accepted(),
			"disabled, missing, and callback-dependent previews must fail closed"
		);
	}

	private static DefinitionSnapshot fixtureSnapshot() {
		Identifier cueId = id("acceptance/policy_matrix");
		MessageCueDefinition cue = parsePolicyCue(cueId);
		LinkedHashMap<Identifier, MessageCueDefinition> enabled = new LinkedHashMap<>();
		enabled.put(cueId, cue);
		for (int index = 0; index < 11; index++) {
			Identifier copyId = id(String.format("catalog/%02d", index));
			enabled.put(copyId, copyCue(cue, copyId, cue.nodes()));
		}
		Identifier unsafeId = id("catalog/unsafe");
		enabled.put(unsafeId, unsafeCue(cue, unsafeId));

		Identifier disabledId = id("catalog/disabled");
		DocumentKey disabledKey = new DocumentKey(
			DefinitionType.MESSAGE_CUE,
			disabledId
		);
		SourceDocument disabledSource = source(disabledKey, "disabled.json", "d".repeat(64));
		List<Problem> problems = new ArrayList<>();
		for (int index = 0; index < 6; index++) {
			problems.add(
				new Problem(
					"TEST_" + index,
					DefinitionType.MESSAGE_CUE,
					disabledId,
					disabledSource.resource(),
					disabledSource.sourcePack(),
					"/nodes/" + index,
					"测试诊断 " + index
				)
			);
		}
		DisabledMessageDefinition disabled = new DisabledMessageDefinition(
			disabledSource,
			false,
			Optional.empty(),
			Optional.empty(),
			problems,
			8
		);
		MessageCatalog catalog = new MessageCatalog(
			Map.of(),
			enabled,
			Map.of(disabledKey, disabled),
			Set.of()
		);

		DocumentKey cueKey = new DocumentKey(DefinitionType.MESSAGE_CUE, cueId);
		SourceDocument cueSource = source(cueKey, "policy_matrix.json", "a".repeat(64));
		DefinitionSnapshot empty = DefinitionSnapshot.empty();
		return new DefinitionSnapshot(
			42L,
			empty.games(),
			empty.roles(),
			empty.teams(),
			empty.lifeStates(),
			empty.phases(),
			empty.fields(),
			empty.flows(),
			empty.tasks(),
			empty.panelActions(),
			empty.playerRoutes(),
			empty.playerData(),
			empty.playerActions(),
			empty.pages(),
			empty.themes(),
			catalog,
			Map.of(cueKey, cueSource),
			empty.functions(),
			empty.predicates(),
			empty.predicateDocuments()
		);
	}

	private static MessageCueDefinition parsePolicyCue(final Identifier cueId) {
		Path path = Path.of(
			"examples",
			"pixel-tzz-base-datapack",
			"data",
			"pixel_tzz",
			"pixel_tzz_pro",
			"message_cues",
			"acceptance",
			"policy_matrix.json"
		);
		try {
			var result = MessageDefinitionParser.parseMessageCue(
				cueId,
				Files.readString(path, StandardCharsets.UTF_8)
			);
			check(result.valid(), "policy cue fixture must parse: " + result.issues());
			return result.value().orElseThrow();
		} catch (IOException error) {
			throw new AssertionError("could not read policy cue fixture", error);
		}
	}

	private static MessageCueDefinition unsafeCue(
		final MessageCueDefinition source,
		final Identifier id
	) {
		CallbackNode callback = source.nodes().stream()
			.filter(CallbackNode.class::isInstance)
			.map(CallbackNode.class::cast)
			.findFirst()
			.orElseThrow();
		TextNode text = source.nodes().stream()
			.filter(TextNode.class::isInstance)
			.map(TextNode.class::cast)
			.findFirst()
			.orElseThrow();
		TextNode dependent = new TextNode(
			new NodeHeader(
				"unsafe_text",
				new AfterNode(callback.id(), new TimeSpan(0L)),
				NodePolicy.inherited(),
				Optional.empty(),
				RepeatSpec.once()
			),
			text.channel(),
			text.content(),
			text.variants()
		);
		return copyCue(source, id, List.of(callback, dependent));
	}

	private static MessageCueDefinition copyCue(
		final MessageCueDefinition source,
		final Identifier id,
		final List<CueNode> nodes
	) {
		return new MessageCueDefinition(
			id,
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

	private static SourceDocument source(
		final DocumentKey key,
		final String file,
		final String sha256
	) {
		return new SourceDocument(
			key,
			id("pixel_tzz_pro/message_cues/" + file),
			"message-command-self-check",
			"{}",
			sha256
		);
	}

	private static CommandNode<CommandSourceStack> checkPath(
		final CommandNode<CommandSourceStack> root,
		final String... names
	) {
		CommandNode<CommandSourceStack> current = root;
		StringBuilder traversed = new StringBuilder();
		for (String name : names) {
			traversed.append('/').append(name);
			current = current.getChild(name);
			check(current != null, "command path is missing: " + traversed);
		}
		return current;
	}

	private static Identifier id(final String path) {
		return Identifier.parse("pixel_tzz:" + path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
