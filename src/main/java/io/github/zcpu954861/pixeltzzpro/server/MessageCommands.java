package io.github.zcpu954861.pixeltzzpro.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.server.MessageServerRuntime.ControlCommandResult;
import io.github.zcpu954861.pixeltzzpro.server.MessageServerRuntime.PlayCommandResult;
import io.github.zcpu954861.pixeltzzpro.server.MessageServerRuntime.StorageArguments;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog.CatalogEntry;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog.CatalogPage;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog.CatalogStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog.CueInspection;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCommandCatalog.ParameterSummary;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ControlAction;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Data-pack and host command surface for registered V3B message cues.
 *
 * <p>The command accepts only a compiled cue identifier, an optional legal player selector, and
 * an optional bounded storage object containing declared call arguments. It never accepts raw
 * text, callback functions, predicates, or client-supplied audience rules.
 */
public final class MessageCommands {
	private MessageCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> register(dispatcher)
		);
	}

	static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("pixel_tzz_pro")
				.then(
					Commands.literal("message")
						.then(
							Commands.literal("list")
								.requires(MessageServerRuntime::authorizedSource)
								.executes(context -> list(context, 1))
								.then(
									Commands.argument(
										"page",
										IntegerArgumentType.integer(1)
									)
										.executes(context ->
											list(
												context,
												IntegerArgumentType.getInteger(
													context,
													"page"
												)
											)
										)
								)
						)
						.then(
							Commands.literal("inspect")
								.requires(MessageServerRuntime::authorizedSource)
								.then(
									Commands.argument(
										"message_id",
										IdentifierArgument.id()
									)
										.executes(MessageCommands::inspect)
								)
						)
						.then(
							Commands.literal("preview")
								.requires(MessageServerRuntime::authorizedSource)
								.then(
									Commands.argument(
										"message_id",
										IdentifierArgument.id()
									)
										.executes(context ->
											preview(context, Optional.empty())
										)
										.then(
											Commands.literal("with")
												.then(
													Commands.literal("storage")
														.then(
															Commands.argument(
																"storage",
																IdentifierArgument.id()
															)
																.then(
																	Commands.argument(
																		"path",
																		NbtPathArgument.nbtPath()
																	)
																		.executes(context ->
																			preview(
																				context,
																				Optional.of(
																					storage(context)
																				)
																			)
																		)
																)
														)
												)
										)
								)
						)
						.then(
							Commands.literal("play")
								.requires(MessageServerRuntime::authorizedSource)
								.then(
									Commands.argument("message_id", IdentifierArgument.id())
										.executes(context -> play(context, Set.of(), Optional.empty()))
										.then(
											contextBypass(context ->
												play(
													context,
													Set.of(),
													Optional.empty(),
													true
												)
											)
										)
										.then(
											Commands.literal("with")
												.then(
													Commands.literal("storage")
														.then(
														Commands.argument(
																	"storage",
																	IdentifierArgument.id()
																)
																.then(
																	Commands.argument(
																			"path",
																			NbtPathArgument.nbtPath()
																		)
																		.executes(context ->
																	play(
																		context,
																		Set.of(),
																		Optional.of(storage(context))
																	)
																)
																.then(
																	contextBypass(context ->
																		play(
																			context,
																			Set.of(),
																			Optional.of(storage(context)),
																			true
																		)
																	)
																)
														)
														)
												)
										)
										.then(
											Commands.literal("to")
												.then(
													Commands.argument(
															"targets",
															EntityArgument.players()
														)
														.executes(context ->
														play(
															context,
															targets(context),
															Optional.empty()
														)
													)
													.then(
														contextBypass(context ->
															play(
																context,
																targets(context),
																Optional.empty(),
																true
															)
														)
													)
													.then(
															Commands.literal("with")
																.then(
																	Commands.literal("storage")
																		.then(
																		Commands.argument(
																					"storage",
																					IdentifierArgument.id()
																				)
																				.then(
																					Commands.argument(
																							"path",
																							NbtPathArgument.nbtPath()
																						)
																						.executes(context ->
																			play(
																				context,
																				targets(context),
																				Optional.of(storage(context))
																			)
																		)
																		.then(
																			contextBypass(context ->
																				play(
																					context,
																					targets(context),
																					Optional.of(storage(context)),
																					true
																				)
																			)
																		)
																	)
																		)
																)
														)
												)
										)
								)
						)
						.then(
							Commands.literal("control")
								.requires(MessageServerRuntime::authorizedSource)
								.then(
									Commands.literal("instance")
										.then(
											Commands.argument(
													"instance_id",
													UuidArgument.uuid()
												)
												.then(
													Commands.literal("pause")
														.executes(context ->
															controlInstance(
																context,
																ControlAction.PAUSE
															)
														)
												)
												.then(
													Commands.literal("resume")
														.executes(context ->
															controlInstance(
																context,
																ControlAction.RESUME
															)
														)
												)
												.then(
													Commands.literal("complete")
														.executes(context ->
															controlInstance(
																context,
																ControlAction.COMPLETE
															)
														)
												)
												.then(
													Commands.literal("cancel")
														.executes(context ->
															controlInstance(
																context,
																ControlAction.CANCEL
															)
														)
												)
										)
								)
								.then(
									Commands.literal("cue")
										.then(
										Commands.argument(
													"message_id",
													IdentifierArgument.id()
												)
												.then(
													Commands.literal("pause")
														.executes(context ->
															controlCue(
																context,
																ControlAction.PAUSE
															)
														)
												)
												.then(
													Commands.literal("resume")
														.executes(context ->
															controlCue(
																context,
																ControlAction.RESUME
															)
														)
												)
												.then(
													Commands.literal("complete")
														.executes(context ->
															controlCue(
																context,
																ControlAction.COMPLETE
															)
														)
												)
												.then(
													Commands.literal("cancel")
														.executes(context ->
															controlCue(
																context,
																ControlAction.CANCEL
															)
														)
												)
										)
								)
								.then(
									Commands.literal("group")
										.then(
											Commands.argument(
													"group",
													StringArgumentType.word()
												)
												.then(
													Commands.literal("pause")
														.executes(context ->
															controlGroup(
																context,
																ControlAction.PAUSE
															)
														)
												)
												.then(
													Commands.literal("resume")
														.executes(context ->
															controlGroup(
																context,
																ControlAction.RESUME
															)
														)
												)
												.then(
													Commands.literal("complete")
														.executes(context ->
															controlGroup(
																context,
																ControlAction.COMPLETE
															)
														)
												)
												.then(
													Commands.literal("cancel")
														.executes(context ->
															controlGroup(
																context,
																ControlAction.CANCEL
															)
														)
												)
										)
								)
								.then(
									Commands.literal("target")
										.then(
											Commands.argument(
													"target",
													EntityArgument.player()
												)
												.then(
													Commands.literal("pause")
														.executes(context ->
															controlTarget(
																context,
																ControlAction.PAUSE
															)
														)
												)
												.then(
													Commands.literal("resume")
														.executes(context ->
															controlTarget(
																context,
																ControlAction.RESUME
															)
														)
												)
												.then(
													Commands.literal("complete")
														.executes(context ->
															controlTarget(
																context,
																ControlAction.COMPLETE
															)
														)
												)
												.then(
													Commands.literal("cancel")
														.executes(context ->
															controlTarget(
																context,
																ControlAction.CANCEL
															)
														)
												)
										)
								)
						)
						.then(
							Commands.argument(
								"unauthorized_command",
								StringArgumentType.greedyString()
							)
								.requires(source -> !MessageServerRuntime.authorizedSource(source))
								.executes(MessageCommands::reportUnauthorized)
						)
				)
		);
	}

	private static int reportUnauthorized(
		final CommandContext<CommandSourceStack> context
	) {
		CommandSourceStack source = context.getSource();
		PermissionContext permission = source.getPermissionContext();
		MinecraftServer server = source.getServer();
		Optional<UUID> hostId = server == null
			? Optional.empty()
			: PixelTzzWorldState.get(server).hostId();
		boolean registeredPlayer = server != null
			&& PixelTzzWorldState.get(server).currentV3()
				.map(state -> state.core().players().containsKey(permission.uuid()))
				.orElse(false);
		sendFailure(
			context,
			unauthorizedMessage(
				permission.type(),
				permission.uuid(),
				hostId,
				registeredPlayer
			)
		);
		return 0;
	}

	static String unauthorizedMessage(
		final PermissionContext.Type sourceType,
		final UUID sourceId,
		final Optional<UUID> hostId,
		final boolean registeredPlayer
	) {
		java.util.Objects.requireNonNull(sourceType, "sourceType");
		java.util.Objects.requireNonNull(sourceId, "sourceId");
		java.util.Objects.requireNonNull(hostId, "hostId");
		if (sourceType != PermissionContext.Type.PLAYER) {
			return "当前命令来源无权使用动态消息管理命令。";
		}
		if (hostId.isEmpty()) {
			return "当前世界尚未指定主持人；玩家不能执行动态消息管理命令，请由数据包函数或服务端控制台执行。";
		}
		if (!registeredPlayer) {
			return "当前玩家尚未登记游戏身份，不能使用动态消息管理命令；该命令仅向当前主持人开放。";
		}
		if (!hostId.orElseThrow().equals(sourceId)) {
			return "当前玩家不是主持人，无权使用动态消息管理命令；非主持人 OP 也不会获得该权限。";
		}
		return "当前来源未通过动态消息命令授权检查。";
	}

	private static int list(
		final CommandContext<CommandSourceStack> context,
		final int pageNumber
	) {
		var result = MessageCommandCatalog.list(
			DefinitionRegistry.INSTANCE.view().active(),
			pageNumber
		);
		if (!result.accepted()) {
			sendFailure(context, result.message());
			return 0;
		}
		CatalogPage page = result.value().orElseThrow();
		sendLine(context, catalogHeaderLine(page));
		String previousSection = null;
		for (CatalogEntry entry : page.entries()) {
			String section = catalogSection(entry.id());
			if (!section.equals(previousSection)) {
				sendLine(context, catalogSectionLine(section));
				previousSection = section;
			}
			sendLine(context, catalogEntryLine(entry));
		}
		return Math.max(1, page.entries().size());
	}

	static Component catalogHeaderLine(final CatalogPage page) {
		return Component.empty()
			.append(
				Component.literal("『动态消息目录』")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			)
			.append(Component.literal("  第 ").withStyle(ChatFormatting.DARK_GRAY))
			.append(
				Component.literal(page.page() + "/" + page.pages())
					.withStyle(ChatFormatting.GOLD)
			)
			.append(Component.literal(" 页  ·  共 ").withStyle(ChatFormatting.GRAY))
			.append(
				Component.literal(Integer.toString(page.totalEntries()))
					.withStyle(ChatFormatting.GOLD)
			)
			.append(Component.literal(" 项  ·  定义代次 ").withStyle(ChatFormatting.GRAY))
			.append(
				Component.literal(Long.toString(page.generation()))
					.withStyle(ChatFormatting.GOLD)
			);
	}

	static Component catalogSectionLine(final String section) {
		return Component.empty()
			.append(Component.literal("  ── ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(section).withStyle(ChatFormatting.DARK_AQUA))
			.append(Component.literal(" ──").withStyle(ChatFormatting.DARK_GRAY));
	}

	static Component catalogEntryLine(final CatalogEntry entry) {
		boolean enabled = entry.status() == CatalogStatus.ENABLED;
		String metric = enabled
			? entry.nodeCount() + "节点"
			: entry.diagnosticCount() + "诊断";
		MutableComponent hover = Component.empty()
			.append(
				Component.literal("点击审阅「" + entry.id() + "」")
					.withStyle(ChatFormatting.AQUA)
			)
			.append(Component.literal("\n状态：").withStyle(ChatFormatting.GRAY))
			.append(
				Component.literal(statusLabel(entry.status()))
					.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
			)
			.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
			.append(
				Component.literal(entry.required() ? "必需定义" : "可选定义")
					.withStyle(entry.required() ? ChatFormatting.GOLD : ChatFormatting.GRAY)
			)
			.append(Component.literal("\n规模：" + metric).withStyle(ChatFormatting.GRAY));
		String inspectCommand = "/pixel_tzz_pro message inspect " + entry.id();
		Component identifier = Component.literal("「" + entry.id() + "」")
			.withStyle(style ->
				style.withColor(ChatFormatting.AQUA)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.RunCommand(inspectCommand))
					.withHoverEvent(new HoverEvent.ShowText(hover))
			);
		return Component.empty()
			.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY))
			.append(
				Component.literal(metric)
					.withStyle(
						enabled ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
						ChatFormatting.BOLD
					)
			)
			.append(Component.literal("  "))
			.append(identifier)
			.append(Component.literal("  "))
			.append(
				Component.literal(statusLabel(entry.status()))
					.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
			)
			.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
			.append(
				Component.literal(entry.required() ? "必需" : "可选")
					.withStyle(entry.required() ? ChatFormatting.GOLD : ChatFormatting.GRAY)
			);
	}

	private static String catalogSection(final Identifier id) {
		String path = id.getPath();
		int separator = path.indexOf('/');
		String directory = separator < 0 ? "根目录" : path.substring(0, separator);
		return id.getNamespace() + " / " + directory;
	}

	private static int inspect(
		final CommandContext<CommandSourceStack> context
	) {
		Identifier cueId = messageId(context);
		if (cueId == null) {
			return 0;
		}
		var result = MessageCommandCatalog.inspect(
			DefinitionRegistry.INSTANCE.view().active(),
			cueId
		);
		if (!result.accepted()) {
			sendFailure(context, result.message());
			return 0;
		}

		CueInspection inspection = result.value().orElseThrow();
		sendLine(context, inspectHeaderLine(inspection.id()));
		sendLine(context, inspectStatusLine(inspection));
		inspection.source().ifPresent(source -> {
			String hash = source.sha256().length() <= 12
				? source.sha256()
				: source.sha256().substring(0, 12);
			sendLine(
				context,
				Component.empty()
					.append(fieldLabel("来源"))
					.append(Component.literal(source.pack()).withStyle(ChatFormatting.DARK_GRAY))
					.append(separator())
					.append(Component.literal(source.resource().toString()).withStyle(ChatFormatting.GRAY))
					.append(separator())
					.append(Component.literal("SHA-256 " + hash).withStyle(ChatFormatting.DARK_GRAY))
			);
		});

		if (inspection.status() == CatalogStatus.DISABLED) {
			if (inspection.diagnostics().isEmpty()) {
				sendLine(
					context,
					Component.empty()
						.append(fieldLabel("诊断"))
						.append(
							Component.literal("未保留详细信息，请查看服务端日志。")
								.withStyle(ChatFormatting.YELLOW)
						)
				);
			} else {
				for (int index = 0; index < inspection.diagnostics().size(); index++) {
					sendLine(
						context,
						Component.empty()
							.append(Component.literal("诊断 ").withStyle(ChatFormatting.GRAY))
							.append(Component.literal(Integer.toString(index + 1)).withStyle(ChatFormatting.GOLD))
							.append(Component.literal("：").withStyle(ChatFormatting.GRAY))
							.append(Component.literal(inspection.diagnostics().get(index)).withStyle(ChatFormatting.RED))
					);
				}
			}
			return 1;
		}

		sendLine(context, fieldLine(
			"归属游戏",
			Component.literal(inspection.game().map(Object::toString).orElse("全局"))
				.withStyle(ChatFormatting.WHITE)
		));
		String channelSummary = inspection.textChannels().entrySet().stream()
			.sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
			.map(entry -> channelLabel(entry.getKey().name()) + " " + entry.getValue())
			.reduce((left, right) -> left + "、" + right)
			.orElse("无");
		sendLine(
			context,
			Component.empty()
				.append(fieldLabel("节点"))
				.append(Component.literal("文本 ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(Integer.toString(inspection.textNodeCount())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("（" + channelSummary + "）").withStyle(ChatFormatting.GRAY))
				.append(separator())
				.append(Component.literal("声音 ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(Integer.toString(inspection.soundNodes())).withStyle(ChatFormatting.GOLD))
				.append(separator())
				.append(Component.literal("回调 ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(Integer.toString(inspection.callbackNodes())).withStyle(ChatFormatting.GOLD))
		);
		sendLine(context, fieldLine(
			"参数",
			Component.literal(parameterSummary(inspection.parameters())).withStyle(ChatFormatting.WHITE)
		));
		sendLine(
			context,
			fieldLine(
				"受众来源",
				Component.literal(summary(
					inspection.audienceSources()
						.stream()
						.map(MessageCommands::audienceSourceLabel)
						.toList(),
					8,
					"未声明"
				)).withStyle(ChatFormatting.WHITE)
			)
		);
		sendLine(
			context,
			Component.empty()
				.append(fieldLabel("上下文"))
				.append(Component.literal("游戏 " + summary(inspection.contextGames(), 6, "不限")).withStyle(ChatFormatting.WHITE))
				.append(separator())
				.append(Component.literal("阶段 " + summary(inspection.contextPhases(), 6, "不限")).withStyle(ChatFormatting.WHITE))
				.append(separator())
				.append(Component.literal("任务 " + summary(inspection.contextTasks(), 6, "不限")).withStyle(ChatFormatting.WHITE))
		);
		sendLine(
			context,
			Component.empty()
				.append(fieldLabel("资源"))
				.append(Component.literal("共 ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(Integer.toString(inspection.assetCount())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" 项，开播前必需 ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(Integer.toString(inspection.requiredAssetCount())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" 项").withStyle(ChatFormatting.WHITE))
		);
		sendLine(
			context,
			Component.empty()
				.append(fieldLabel("安全预览"))
				.append(Component.literal("会移除 ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(Integer.toString(inspection.callbackNodes())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" 个回调，并且").withStyle(ChatFormatting.WHITE))
				.append(
					Component.literal(
						inspection.historyEnabled()
							? "不会写入已配置的历史记录。"
							: "不会新增历史记录。"
					).withStyle(ChatFormatting.GREEN)
				)
		);
		return 1;
	}

	private static int preview(
		final CommandContext<CommandSourceStack> context,
		final Optional<StorageArguments> storage
	) {
		Identifier cueId = messageId(context);
		if (cueId == null) {
			return 0;
		}
		return reportPlay(
			context,
			cueId,
			true,
			MessageServerRuntime.preview(context.getSource(), cueId, storage)
		);
	}

	private static int play(
		final CommandContext<CommandSourceStack> context,
		final Set<UUID> targets,
		final Optional<StorageArguments> storage
	) {
		return play(context, targets, storage, false);
	}

	private static int play(
		final CommandContext<CommandSourceStack> context,
		final Set<UUID> targets,
		final Optional<StorageArguments> storage,
		final boolean bypassContext
	) {
		Identifier cueId = messageId(context);
		PlayCommandResult result = MessageServerRuntime.play(
			context.getSource(), cueId, targets, storage, bypassContext
		);
		return reportPlay(context, cueId, false, result);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> contextBypass(
		final Command<CommandSourceStack> command
	) {
		return Commands.literal("bypass")
			.requires(MessageServerRuntime::authorizedContextBypassSource)
			.then(Commands.literal("context").executes(command));
	}

	private static int reportPlay(
		final CommandContext<CommandSourceStack> context,
		final Identifier cueId,
		final boolean preview,
		final PlayCommandResult result
	) {
		if (!result.successful()) {
			sendFailure(context, result.message());
			return 0;
		}
		sendLine(context, playHeaderLine(cueId, preview));
		sendLine(context, playStatusLine(result.message(), result.affectedTargets()));
		result.instanceId().ifPresent(instanceId -> sendLine(
			context,
			fieldLine(
				"实例 UUID",
				Component.literal(instanceId.toString()).withStyle(ChatFormatting.DARK_GRAY)
			)
		));
		return Math.max(1, result.affectedTargets());
	}

	static Component playHeaderLine(final Identifier cueId, final boolean preview) {
		return Component.empty()
			.append(
				Component.literal(preview ? "『动态消息预览』" : "『动态消息已发起』")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			)
			.append(Component.literal(" "))
			.append(identifierComponent(cueId));
	}

	static Component playStatusLine(final String message, final int affectedTargets) {
		return Component.empty()
			.append(fieldLabel("状态"))
			.append(Component.literal(message).withStyle(ChatFormatting.GREEN))
			.append(separator())
			.append(Component.literal("目标 ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(Integer.toString(affectedTargets)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" 人").withStyle(ChatFormatting.GRAY));
	}

	private static Identifier messageId(
		final CommandContext<CommandSourceStack> context
	) {
		return IdentifierArgument.getId(context, "message_id");
	}

	private static String statusLabel(final CatalogStatus status) {
		return status == CatalogStatus.ENABLED ? "可用" : "已禁用";
	}

	private static String channelLabel(final String channel) {
		return switch (channel) {
			case "CHAT" -> "聊天栏";
			case "TITLE" -> "标题";
			case "SUBTITLE" -> "副标题";
			case "ACTION_BAR" -> "动作栏";
			default -> channel;
		};
	}

	private static String parameterSummary(final List<ParameterSummary> parameters) {
		if (parameters.isEmpty()) {
			return "无";
		}
		List<String> values = parameters.stream()
			.limit(8L)
			.map(parameter -> {
				StringBuilder value = new StringBuilder(parameter.name())
					.append(':')
					.append(parameterTypeLabel(parameter.type()));
				if (parameter.required()) {
					value.append("/必填");
				}
				if (parameter.hasDefault()) {
					value.append("/有默认值");
				}
				if (parameter.sensitive()) {
					value.append("/敏感");
				}
				return value.toString();
			})
			.toList();
		return String.join("、", values)
			+ (parameters.size() > values.size()
				? " 等 " + parameters.size() + " 项"
				: "");
	}

	private static String parameterTypeLabel(final String type) {
		return switch (type) {
			case "string" -> "文本";
			case "integer" -> "整数";
			case "decimal" -> "小数";
			case "boolean" -> "布尔值";
			case "identifier" -> "注册名";
			case "player" -> "玩家";
			case "component" -> "富文本";
			case "duration" -> "时长";
			default -> type;
		};
	}

	private static String audienceSourceLabel(final String source) {
		return switch (source) {
			case "all" -> "全部玩家";
			case "current_host" -> "当前主持人";
			case "call_targets" -> "调用目标";
			case "invoker" -> "调用者";
			default -> source;
		};
	}

	private static String summary(
		final Collection<?> values,
		final int maximumItems,
		final String emptyLabel
	) {
		if (values.isEmpty()) {
			return emptyLabel;
		}
		List<String> ordered = values.stream()
			.map(Object::toString)
			.sorted()
			.toList();
		String visible = String.join(
			"、",
			ordered.subList(0, Math.min(maximumItems, ordered.size()))
		);
		return visible
			+ (ordered.size() > maximumItems
				? " 等 " + ordered.size() + " 项"
				: "");
	}

	static Component inspectHeaderLine(final Identifier cueId) {
		return Component.empty()
			.append(
				Component.literal("『动态消息审阅』")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			)
			.append(Component.literal(" "))
			.append(identifierComponent(cueId));
	}

	static Component inspectStatusLine(final CueInspection inspection) {
		boolean enabled = inspection.status() == CatalogStatus.ENABLED;
		return Component.empty()
			.append(fieldLabel("状态"))
			.append(
				Component.literal(statusLabel(inspection.status()))
					.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
			)
			.append(separator())
			.append(
				Component.literal(inspection.required() ? "必需定义" : "可选定义")
					.withStyle(inspection.required() ? ChatFormatting.GOLD : ChatFormatting.GRAY)
			);
	}

	private static Component identifierComponent(final Identifier cueId) {
		String command = "/pixel_tzz_pro message inspect " + cueId;
		return Component.literal("「" + cueId + "」")
			.withStyle(style ->
				style.withColor(ChatFormatting.AQUA)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(
						new HoverEvent.ShowText(
							Component.literal("点击审阅此动态消息")
								.withStyle(ChatFormatting.YELLOW)
						)
					)
			);
	}

	private static Component fieldLine(
		final String label,
		final Component value
	) {
		return Component.empty().append(fieldLabel(label)).append(value);
	}

	private static Component fieldLabel(final String label) {
		return Component.literal(label + "：").withStyle(ChatFormatting.GRAY);
	}

	private static Component separator() {
		return Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
	}

	private static void sendFailure(
		final CommandContext<CommandSourceStack> context,
		final String message
	) {
		context.getSource().sendFailure(failureLine(message));
	}

	static Component failureLine(final String message) {
		return Component.empty()
			.append(
				Component.literal("『操作失败』")
					.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
			)
			.append(Component.literal(" "))
			.append(Component.literal(message).withStyle(ChatFormatting.RED));
	}

	private static void sendLine(
		final CommandContext<CommandSourceStack> context,
		final String rawLine
	) {
		String line = rawLine.replace('\r', ' ')
			.replace('\n', ' ')
			.replace('\t', ' ')
			.strip();
		if (line.length() > 512) {
			line = line.substring(0, 511) + "…";
		}
		String output = line;
		context.getSource().sendSuccess(() -> Component.literal(output), false);
	}

	private static void sendLine(
		final CommandContext<CommandSourceStack> context,
		final Component line
	) {
		context.getSource().sendSuccess(() -> line, false);
	}

	/**
	 * Re-projects the permission-filtered command tree immediately after host authority changes.
	 * Vanilla OP status is deliberately irrelevant: every connected player receives a fresh tree,
	 * then {@link MessageServerRuntime#authorizedSource(CommandSourceStack)} independently decides
	 * whether the protected message branches are visible.
	 */
	static void refreshPlayerCommandTreesIfHostChanged(
		final MinecraftServer server,
		final Optional<UUID> previousHost,
		final Optional<UUID> currentHost
	) {
		if (!hostAuthorityChanged(previousHost, currentHost)) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			server.getCommands().sendCommands(player);
		}
	}

	static boolean hostAuthorityChanged(
		final Optional<UUID> previousHost,
		final Optional<UUID> currentHost
	) {
		return !java.util.Objects.requireNonNull(previousHost, "previousHost")
			.equals(java.util.Objects.requireNonNull(currentHost, "currentHost"));
	}

	private static int controlInstance(
		final CommandContext<CommandSourceStack> context,
		final ControlAction action
	) {
		ControlCommandResult result = MessageServerRuntime.controlInstance(
			context.getSource(),
			UuidArgument.getUuid(context, "instance_id"),
			action
		);
		return reportControl(context, result);
	}

	private static int controlCue(
		final CommandContext<CommandSourceStack> context,
		final ControlAction action
	) {
		return reportControl(
			context,
			MessageServerRuntime.controlCue(context.getSource(), messageId(context), action)
		);
	}

	private static int controlGroup(
		final CommandContext<CommandSourceStack> context,
		final ControlAction action
	) {
		return reportControl(
			context,
			MessageServerRuntime.controlGroup(
				context.getSource(),
				StringArgumentType.getString(context, "group"),
				action
			)
		);
	}

	private static int controlTarget(
		final CommandContext<CommandSourceStack> context,
		final ControlAction action
	) throws CommandSyntaxException {
		return reportControl(
			context,
			MessageServerRuntime.controlTarget(
				context.getSource(),
				EntityArgument.getPlayer(context, "target").getUUID(),
				action
			)
		);
	}

	private static int reportControl(
		final CommandContext<CommandSourceStack> context,
		final ControlCommandResult result
	) {
		if (!result.successful()) {
			sendFailure(context, result.message());
			return 0;
		}
		sendLine(context, controlStatusLine(result.message(), result.changed()));
		return Math.max(1, result.changed());
	}

	static Component controlStatusLine(final String message, final int changed) {
		return Component.empty()
			.append(
				Component.literal("『动态消息控制』")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			)
			.append(Component.literal(" "))
			.append(Component.literal(message).withStyle(ChatFormatting.GREEN))
			.append(separator())
			.append(Component.literal("影响 ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(Integer.toString(changed)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" 个实例").withStyle(ChatFormatting.GRAY));
	}

	private static Set<UUID> targets(
		final CommandContext<CommandSourceStack> context
	) throws CommandSyntaxException {
		LinkedHashSet<UUID> result = new LinkedHashSet<>();
		for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
			result.add(player.getUUID());
		}
		return Set.copyOf(result);
	}

	private static StorageArguments storage(
		final CommandContext<CommandSourceStack> context
	) throws CommandSyntaxException {
		return new StorageArguments(
			IdentifierArgument.getId(context, "storage"),
			NbtPathArgument.getPath(context, "path")
		);
	}
}
