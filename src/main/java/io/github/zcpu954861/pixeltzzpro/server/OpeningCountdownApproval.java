package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.CountdownSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.OpeningCountdownReference;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.BlockMode;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownCallbackScope;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownCallbackSlot;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.DamageMode;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.DisconnectAction;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.FreezeMode;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.StartRequest;
import io.github.zcpu954861.pixeltzzpro.server.CountdownAuthority.TransitionResult;
import io.github.zcpu954861.pixeltzzpro.server.TimelineApprovalAuthority.ApprovalContext;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackScope;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackSlot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicy;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenParticipant;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.LaunchPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/** Splits existing 2D approval from V3C countdown creation without weakening either gate. */
public final class OpeningCountdownApproval {
	private OpeningCountdownApproval() {
	}

	public static Result approve(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final ApprovalContext context,
		final UUID countdownInstanceId,
		final Function<UUID, String> playerName
	) {
		Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
		Objects.requireNonNull(playerName, "playerName");
		TimelineApprovalAuthority.ApprovalResult timeline = TimelineApprovalAuthority.approve(
			state,
			definitions,
			context
		);
		if (!timeline.successful()) {
			return Result.rejected(timeline.code(), timeline.message());
		}

		Identifier gameId = state.core().activeGameId().orElseThrow();
		GameDefinition game = definitions.games().get(gameId);
		OpeningCountdownReference reference = game == null
			? null
			: game.openingCountdown().orElse(null);
		if (reference == null) {
			return Result.direct(timeline.nextState().orElseThrow(), timeline.frozenParticipants());
		}
		if (definitions.hudCatalog().gameStartBlocked(gameId)) {
			return Result.rejected(
				OperationCode.RESOURCE_BLOCKED,
				"必需的正式开局倒计时资源无效；请修复数据包并重新加载。"
			);
		}
		CountdownDefinition countdown = definitions.hudCatalog()
			.countdowns()
			.get(reference.definition());
		if (countdown == null) {
			return reference.required()
				? Result.rejected(
					OperationCode.RESOURCE_BLOCKED,
					"必需的正式开局倒计时不可用：" + reference.definition()
				)
				: Result.direct(timeline.nextState().orElseThrow(), timeline.frozenParticipants());
		}

		CountdownSnapshotCompiler.FreezeResult frozen = CountdownSnapshotCompiler.freeze(
			definitions,
			game,
			countdown
		);
		if (!frozen.success()) {
			return Result.rejected(
				OperationCode.SNAPSHOT_INVALID,
				"正式开局倒计时无法冻结：" + frozen.message()
			);
		}
		List<FrozenParticipant> participants = timeline.frozenParticipants()
			.stream()
			.sorted()
			.map(playerId -> new FrozenParticipant(
				playerId,
				boundedName(playerName.apply(playerId), playerId),
				true
			))
			.toList();
		StartRequest request = new StartRequest(
			context.gameInstanceId(),
			countdownInstanceId,
			new FrozenDefinition(
				countdown.id(),
				definitions.generation(),
				frozen.frozen().orElseThrow()
			),
			new LaunchPlan(
				state.core().activePhaseId().orElseThrow(),
				context.timelineInstanceId(),
				context.firstTaskInstanceId(),
				timeline.frozenParticipants()
			),
			countdown.durationTicks(),
			participants,
			state.core().host().map(value -> value.playerId()),
			disconnectPolicies(countdown),
			restrictions(countdown),
			countdown.checkpoints()
				.stream()
				.map(value -> new CheckpointDefinition(value.id(), value.remainingTicks()))
				.toList(),
			callbacks(countdown),
			context.serverTick()
		);
		TransitionResult started = CountdownAuthority.start(request);
		if (!started.accepted()) {
			return Result.rejected(OperationCode.SNAPSHOT_INVALID, started.message());
		}
		return Result.countdown(
			started.instance().orElseThrow(),
			started.callbackWork(),
			timeline.frozenParticipants()
		);
	}

	private static String boundedName(final String value, final UUID playerId) {
		String name = value == null || value.isBlank() ? playerId.toString() : value.strip();
		return name.length() <= 64 ? name : name.substring(0, 64);
	}

	private static DisconnectPolicies disconnectPolicies(final CountdownDefinition countdown) {
		return new DisconnectPolicies(
			disconnect(countdown.disconnect().requiredPlayer()),
			disconnect(countdown.disconnect().host())
		);
	}

	private static DisconnectPolicy disconnect(final DisconnectAction action) {
		return switch (action) {
			case PAUSE -> DisconnectPolicy.PAUSE;
			case CANCEL -> DisconnectPolicy.CANCEL;
			case CONTINUE -> DisconnectPolicy.CONTINUE;
		};
	}

	private static Restrictions restrictions(final CountdownDefinition countdown) {
		var value = countdown.restrictions();
		return new Restrictions(
			value.movement() == FreezeMode.FREEZE,
			value.attack() == BlockMode.BLOCK,
			value.interact() == BlockMode.BLOCK,
			value.items().use() == BlockMode.BLOCK,
			value.items().drop() == BlockMode.BLOCK,
			value.items().swap() == BlockMode.BLOCK,
			value.items().inventory() == BlockMode.BLOCK,
			value.damage() == DamageMode.IMMUNE
		);
	}

	private static List<CallbackDefinition> callbacks(final CountdownDefinition countdown) {
		List<CallbackDefinition> result = new ArrayList<>();
		countdown.callbacks().forEach((slot, callbacks) -> callbacks.forEach(callback ->
			result.add(new CallbackDefinition(
				callbackSlot(slot),
				callback.id(),
				callback.scope() == CountdownCallbackScope.GLOBAL
					? CallbackScope.GLOBAL
					: CallbackScope.EACH_PARTICIPANT,
				callback.function(),
				callback.required()
			))
		));
		return result.stream()
			.sorted(
				Comparator.comparing((CallbackDefinition value) -> value.slot().getSerializedName())
					.thenComparing(CallbackDefinition::callbackId)
			)
			.toList();
	}

	private static CallbackSlot callbackSlot(final CountdownCallbackSlot slot) {
		return switch (slot) {
			case START -> CallbackSlot.START;
			case PAUSE -> CallbackSlot.PAUSE;
			case RESUME -> CallbackSlot.RESUME;
			case CANCEL -> CallbackSlot.CANCEL;
			case COMPLETE -> CallbackSlot.COMPLETE;
		};
	}

	public record Result(
		OperationCode code,
		String message,
		Optional<WorldStateV3> directTimelineState,
		Optional<Instance> countdown,
		List<CountdownAuthority.CallbackWork> callbackWork,
		List<UUID> frozenParticipants
	) {
		public Result {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			directTimelineState = Objects.requireNonNull(directTimelineState, "directTimelineState");
			countdown = Objects.requireNonNull(countdown, "countdown");
			callbackWork = List.copyOf(callbackWork);
			frozenParticipants = List.copyOf(frozenParticipants);
			if (
				(code == OperationCode.SUCCESS)
					!= (directTimelineState.isPresent() ^ countdown.isPresent())
			) {
				throw new IllegalArgumentException("opening countdown approval result is inconsistent");
			}
			if (countdown.isEmpty() && !callbackWork.isEmpty()) {
				throw new IllegalArgumentException("direct approval cannot carry countdown callbacks");
			}
		}

		public boolean successful() {
			return this.code == OperationCode.SUCCESS;
		}

		public boolean startsCountdown() {
			return this.countdown.isPresent();
		}

		private static Result direct(final WorldStateV3 state, final List<UUID> participants) {
			return new Result(
				OperationCode.SUCCESS,
				"任务时间线已批准并启动",
				Optional.of(state),
				Optional.empty(),
				List.of(),
				participants
			);
		}

		private static Result countdown(
			final Instance countdown,
			final List<CountdownAuthority.CallbackWork> callbacks,
			final List<UUID> participants
		) {
			return new Result(
				OperationCode.SUCCESS,
				"正式开局倒计时已批准并冻结",
				Optional.empty(),
				Optional.of(countdown),
				callbacks,
				participants
			);
		}

		private static Result rejected(final OperationCode code, final String message) {
			return new Result(
				code,
				message,
				Optional.empty(),
				Optional.empty(),
				List.of(),
				List.of()
			);
		}
	}
}
