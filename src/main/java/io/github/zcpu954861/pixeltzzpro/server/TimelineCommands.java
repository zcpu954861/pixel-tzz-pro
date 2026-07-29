package io.github.zcpu954861.pixeltzzpro.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskEventDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.TaskFactAuthority.OperationResult;
import io.github.zcpu954861.pixeltzzpro.server.TimelineAuthority.ActionContext;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.FrozenPlayerValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative data-pack command bridge for one active task instance.
 *
 * <p>Clients never send these facts. Every invocation reopens the persisted frozen snapshot and
 * validates the task instance and revision immediately before commit.
 */
public final class TimelineCommands {
	private TimelineCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> register(dispatcher)
		);
	}

	static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("pixel_tzz")
				.then(
					Commands.literal("task")
						.requires(TimelineCommands::authorizedSource)
						.then(
							Commands.literal("submit_result")
								.then(
									Commands.argument("task_instance", UuidArgument.uuid())
										.then(
											Commands.argument("result", StringArgumentType.word())
												.executes(context ->
													submitResult(
														context.getSource(),
														UuidArgument.getUuid(context, "task_instance"),
														StringArgumentType.getString(context, "result")
													)
												)
										)
								)
						)
						.then(
							Commands.literal("record_event")
								.then(
									Commands.argument("task_instance", UuidArgument.uuid())
										.then(
											Commands.argument("event", StringArgumentType.word())
												.executes(context ->
													recordEvent(
														context.getSource(),
														UuidArgument.getUuid(context, "task_instance"),
														StringArgumentType.getString(context, "event"),
														Optional.empty()
													)
												)
												.then(
													Commands.argument("player", EntityArgument.player())
														.executes(context ->
															recordEvent(
																context.getSource(),
																UuidArgument.getUuid(context, "task_instance"),
																StringArgumentType.getString(context, "event"),
																Optional.of(EntityArgument.getPlayer(context, "player"))
															)
														)
												)
										)
								)
						)
						.then(
							Commands.literal("set_statistic")
								.then(
									Commands.argument("task_instance", UuidArgument.uuid())
										.then(
											Commands.argument("statistic", StringArgumentType.word())
												.then(
													Commands.argument("value", StringArgumentType.greedyString())
														.executes(context ->
															setStatistic(
																context.getSource(),
																UuidArgument.getUuid(context, "task_instance"),
																StringArgumentType.getString(context, "statistic"),
																StringArgumentType.getString(context, "value")
															)
														)
												)
										)
								)
						)
				)
		);
	}

	private static boolean authorizedSource(final CommandSourceStack source) {
		return source.getPlayer() == null
			|| Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
	}

	private static int submitResult(
		final CommandSourceStack source,
		final UUID taskInstanceId,
		final String resultId
	) {
		ResolvedTask resolved = resolve(source.getServer(), taskInstanceId);
		if (!resolved.successful()) {
			return fail(source, resolved.code(), resolved.message());
		}
		TimelineAuthority.OperationResult result = TimelineAuthority.submitResult(
			resolved.timeline().orElseThrow(),
			resolved.context().orElseThrow(),
			resolved.definition().orElseThrow(),
			resultId
		);
		if (!result.successful()) {
			return fail(source, result.code(), result.message());
		}
		if (result.changed() && !commit(source.getServer(), resolved, result.nextState().orElseThrow())) {
			return fail(source, OperationCode.REVISION_MISMATCH, "提交结果时世界状态已经变化");
		}
		return succeed(source, "任务结果已接受：" + resultId);
	}

	private static int recordEvent(
		final CommandSourceStack source,
		final UUID taskInstanceId,
		final String eventId,
		final Optional<ServerPlayer> player
	) {
		ResolvedTask resolved = resolve(source.getServer(), taskInstanceId);
		if (!resolved.successful()) {
			return fail(source, resolved.code(), resolved.message());
		}
		TaskEventDefinition event = resolved.definition().orElseThrow()
			.events()
			.stream()
			.filter(candidate -> candidate.id().equals(eventId))
			.findFirst()
			.orElse(null);
		if (event == null) {
			return fail(source, OperationCode.EVENT_NOT_REGISTERED, "当前任务未注册该事件");
		}

		Optional<FrozenPlayerValue> frozenPlayer = Optional.empty();
		if (player.isPresent()) {
			ServerPlayer target = player.orElseThrow();
			PlayerRecord record = resolved.root().orElseThrow().core().players().get(target.getUUID());
			if (
				record == null
					|| !AudienceMatcher.matches(
						resolved.snapshot().orElseThrow(),
						event.audience().orElse(resolved.definition().orElseThrow().audience()),
						record,
						resolved.root().orElseThrow().core().host().map(value -> value.playerId()),
						true
					)
			) {
				return fail(
					source,
					OperationCode.PERMISSION_DENIED,
					"该玩家不属于事件允许的受众"
				);
			}
			frozenPlayer = Optional.of(
				new FrozenPlayerValue(target.getUUID(), target.getPlainTextName())
			);
		}

		OperationResult result = TaskFactAuthority.recordEvent(
			resolved.timeline().orElseThrow(),
			resolved.context().orElseThrow(),
			resolved.definition().orElseThrow(),
			eventId,
			frozenPlayer
		);
		if (!result.successful()) {
			return fail(source, result.code(), result.message());
		}
		if (result.changed() && !commit(source.getServer(), resolved, result.nextState().orElseThrow())) {
			return fail(source, OperationCode.REVISION_MISMATCH, "记录事件时世界状态已经变化");
		}
		return succeed(source, "任务事件已记录：" + eventId);
	}

	private static int setStatistic(
		final CommandSourceStack source,
		final UUID taskInstanceId,
		final String statisticId,
		final String rawValue
	) {
		ResolvedTask resolved = resolve(source.getServer(), taskInstanceId);
		if (!resolved.successful()) {
			return fail(source, resolved.code(), resolved.message());
		}
		TaskStatisticDefinition statistic = resolved.definition().orElseThrow()
			.statistics()
			.stream()
			.filter(candidate -> candidate.id().equals(statisticId))
			.findFirst()
			.orElse(null);
		if (statistic == null) {
			return fail(
				source,
				OperationCode.STATISTIC_NOT_REGISTERED,
				"当前任务未注册该回顾统计"
			);
		}
		Optional<TaskStatisticValue> value = parseStatisticValue(
			statistic,
			statisticId,
			rawValue,
			resolved.root().orElseThrow().core().players()
		);
		if (value.isEmpty()) {
			return fail(
				source,
				OperationCode.STATISTIC_TYPE_MISMATCH,
				"统计值无法按注册类型解析"
			);
		}
		OperationResult result = TaskFactAuthority.writeStatistic(
			resolved.timeline().orElseThrow(),
			resolved.context().orElseThrow(),
			resolved.definition().orElseThrow(),
			value.orElseThrow()
		);
		if (!result.successful()) {
			return fail(source, result.code(), result.message());
		}
		if (result.changed() && !commit(source.getServer(), resolved, result.nextState().orElseThrow())) {
			return fail(source, OperationCode.REVISION_MISMATCH, "写入统计时世界状态已经变化");
		}
		return succeed(source, "任务统计已写入：" + statisticId);
	}

	static Optional<TaskStatisticValue> parseStatisticValue(
		final TaskStatisticDefinition definition,
		final String statisticId,
		final String rawValue,
		final Map<UUID, PlayerRecord> players
	) {
		String value = rawValue == null ? "" : rawValue.strip();
		if (value.isEmpty()) {
			return Optional.empty();
		}
		try {
			return switch (definition.type()) {
				case BOOLEAN -> value.equals("true") || value.equals("false")
					? Optional.of(TaskStatisticValue.booleanValue(statisticId, Boolean.parseBoolean(value)))
					: Optional.empty();
				case INTEGER -> Optional.of(
					TaskStatisticValue.integer(statisticId, Long.parseLong(value))
				);
				case DURATION_TICKS -> Optional.of(
					TaskStatisticValue.durationTicks(statisticId, Long.parseLong(value))
				);
				case STRING -> Optional.of(TaskStatisticValue.stringValue(statisticId, value));
				case IDENTIFIER -> Optional.ofNullable(Identifier.tryParse(value))
					.map(identifier -> TaskStatisticValue.identifier(statisticId, identifier));
				case PLAYER -> {
					UUID playerId = UUID.fromString(value);
					PlayerRecord player = players.get(playerId);
					yield player == null
						? Optional.empty()
						: Optional.of(
							TaskStatisticValue.player(
								statisticId,
								player.playerId(),
								player.lastKnownName()
							)
						);
				}
			};
		} catch (IllegalArgumentException error) {
			return Optional.empty();
		}
	}

	private static ResolvedTask resolve(
		final MinecraftServer server,
		final UUID taskInstanceId
	) {
		PixelTzzWorldState wrapper = PixelTzzWorldState.get(server);
		WorldStateV3 root = wrapper.currentV3().orElse(null);
		if (root == null || root.timeline().isEmpty()) {
			return ResolvedTask.failed(OperationCode.TIMELINE_NOT_ACTIVE, "当前没有活动任务时间线");
		}
		TimelineInstance timeline = root.timeline().orElseThrow();
		if (
			timeline.currentTask().isEmpty()
				|| !timeline.currentTask().orElseThrow().taskInstanceId().equals(taskInstanceId)
		) {
			return ResolvedTask.failed(OperationCode.TASK_NOT_CURRENT, "任务实例已经不是当前任务");
		}
		TimelineSnapshotCompiler.RestoreResult restored = TimelineSnapshotCompiler.restore(
			timeline.gameId(),
			timeline.snapshot()
		);
		if (!restored.success()) {
			return ResolvedTask.failed(
				OperationCode.SNAPSHOT_INVALID,
				"冻结任务快照无法恢复：" + restored.message()
			);
		}
		DefinitionSnapshot snapshot = restored.snapshot().orElseThrow();
		TaskDefinition definition = snapshot.tasks().get(
			timeline.currentTask().orElseThrow().taskId()
		);
		if (definition == null) {
			return ResolvedTask.failed(
				OperationCode.DEFINITION_UNAVAILABLE,
				"冻结快照中缺少当前任务定义"
			);
		}
		return ResolvedTask.success(
			root,
			timeline,
			snapshot,
			definition,
			context(timeline)
		);
	}

	private static boolean commit(
		final MinecraftServer server,
		final ResolvedTask resolved,
		final TimelineInstance next
	) {
		WorldStateV3 expectedRoot = resolved.root().orElseThrow();
		TimelineInstance expectedTimeline = resolved.timeline().orElseThrow();
		PixelTzzWorldState.CommitV3Result commit = PixelTzzWorldState.get(server)
			.commitV3(
				expectedRoot.stateRevision(),
				current -> {
					TimelineInstance actual = current.timeline().orElseThrow(
						() -> new IllegalStateException("timeline disappeared")
					);
					if (
						!actual.instanceId().equals(expectedTimeline.instanceId())
							|| actual.timelineRevision() != expectedTimeline.timelineRevision()
					) {
						throw new IllegalStateException("timeline changed");
					}
					return current.withTimeline(Optional.of(next));
				}
			);
		if (commit.committed()) {
			PixelTzzServerRuntime.timelineStateChanged(server);
		}
		return commit.committed();
	}

	private static ActionContext context(final TimelineInstance timeline) {
		var task = timeline.currentTask().orElseThrow();
		return new ActionContext(
			timeline.instanceId(),
			task.taskInstanceId(),
			task.taskId(),
			timeline.timelineRevision()
		);
	}

	private static int succeed(final CommandSourceStack source, final String message) {
		source.sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int fail(
		final CommandSourceStack source,
		final OperationCode code,
		final String message
	) {
		source.sendFailure(Component.literal(message + " [" + code.serializedName() + "]"));
		return 0;
	}

	private record ResolvedTask(
		OperationCode code,
		String message,
		Optional<WorldStateV3> root,
		Optional<TimelineInstance> timeline,
		Optional<DefinitionSnapshot> snapshot,
		Optional<TaskDefinition> definition,
		Optional<ActionContext> context
	) {
		private boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		private static ResolvedTask success(
			final WorldStateV3 root,
			final TimelineInstance timeline,
			final DefinitionSnapshot snapshot,
			final TaskDefinition definition,
			final ActionContext context
		) {
			return new ResolvedTask(
				OperationCode.SUCCESS,
				"",
				Optional.of(root),
				Optional.of(timeline),
				Optional.of(snapshot),
				Optional.of(definition),
				Optional.of(context)
			);
		}

		private static ResolvedTask failed(
			final OperationCode code,
			final String message
		) {
			return new ResolvedTask(
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}
}
