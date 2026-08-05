package io.github.zcpu954861.pixeltzzpro.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Stable schema-v5 DTOs for one frozen, server-authoritative opening countdown.
 *
 * <p>The records deliberately contain no compiled content, live player object, network payload or
 * wall-clock timestamp. A cold-start integration must first ask the pure countdown authority for a
 * recovery plan; merely decoding this object can never advance time or execute a callback.
 */
public final class PersistedCountdown {
	public static final int MAX_PARTICIPANTS = 1_024;
	public static final int MAX_CHECKPOINTS = 256;
	public static final int MAX_CALLBACK_DEFINITIONS = 1_024;
	public static final int MAX_CALLBACK_LEDGER_ENTRIES = 8_192;
	public static final int MAX_EMITTED_CHECKPOINTS = MAX_CHECKPOINTS;
	public static final int MAX_LOCAL_ID_LENGTH = 128;
	public static final int MAX_PLAYER_NAME_LENGTH = 64;
	public static final int MAX_REASON_LENGTH = 1_024;

	private static final Codec<String> LOCAL_ID_CODEC =
		Codec.sizeLimitedString(MAX_LOCAL_ID_LENGTH);
	private static final Codec<String> PLAYER_NAME_CODEC =
		Codec.sizeLimitedString(MAX_PLAYER_NAME_LENGTH);
	private static final Codec<String> REASON_CODEC =
		Codec.sizeLimitedString(MAX_REASON_LENGTH);

	private PersistedCountdown() {
	}

	public record Instance(
		UUID gameInstanceId,
		UUID countdownInstanceId,
		FrozenDefinition definition,
		Optional<LaunchPlan> launchPlan,
		Lifecycle lifecycle,
		long totalTicks,
		List<FrozenParticipant> participants,
		Optional<UUID> hostId,
		DisconnectPolicies disconnectPolicies,
		Restrictions restrictions,
		List<RestrictionMarker> restrictionMarkers,
		List<CheckpointDefinition> checkpoints,
		List<String> emittedCheckpointIds,
		List<CallbackDefinition> callbackDefinitions,
		CallbackLedger callbackLedger
	) {
		private static final Codec<List<FrozenParticipant>> PARTICIPANTS_CODEC =
			FrozenParticipant.CODEC.sizeLimitedListOf(MAX_PARTICIPANTS);
		private static final Codec<List<RestrictionMarker>> RESTRICTION_MARKERS_CODEC =
			RestrictionMarker.CODEC.sizeLimitedListOf(MAX_PARTICIPANTS);
		private static final Codec<List<CheckpointDefinition>> CHECKPOINTS_CODEC =
			CheckpointDefinition.CODEC.sizeLimitedListOf(MAX_CHECKPOINTS);
		private static final Codec<List<String>> EMITTED_CHECKPOINTS_CODEC =
			LOCAL_ID_CODEC.sizeLimitedListOf(MAX_EMITTED_CHECKPOINTS);
		private static final Codec<List<CallbackDefinition>> CALLBACK_DEFINITIONS_CODEC =
			CallbackDefinition.CODEC.sizeLimitedListOf(MAX_CALLBACK_DEFINITIONS);

		public static final Codec<Instance> CODEC = RecordCodecBuilder.<Instance>create(
			builder -> builder.group(
					UUIDUtil.STRING_CODEC
						.fieldOf("game_instance_id")
						.forGetter(Instance::gameInstanceId),
					UUIDUtil.STRING_CODEC
						.fieldOf("countdown_instance_id")
						.forGetter(Instance::countdownInstanceId),
					FrozenDefinition.CODEC.fieldOf("definition").forGetter(Instance::definition),
					LaunchPlan.CODEC.optionalFieldOf("launch_plan").forGetter(Instance::launchPlan),
					Lifecycle.CODEC.fieldOf("lifecycle").forGetter(Instance::lifecycle),
					Codec.LONG.fieldOf("total_ticks").forGetter(Instance::totalTicks),
					PARTICIPANTS_CODEC.fieldOf("participants").forGetter(Instance::participants),
					UUIDUtil.STRING_CODEC.optionalFieldOf("host_id").forGetter(Instance::hostId),
					DisconnectPolicies.CODEC
						.fieldOf("disconnect_policies")
						.forGetter(Instance::disconnectPolicies),
					Restrictions.CODEC.fieldOf("restrictions").forGetter(Instance::restrictions),
					RESTRICTION_MARKERS_CODEC
						.fieldOf("restriction_markers")
						.forGetter(Instance::restrictionMarkers),
					CHECKPOINTS_CODEC
						.optionalFieldOf("checkpoints", List.of())
						.forGetter(Instance::checkpoints),
					EMITTED_CHECKPOINTS_CODEC
						.optionalFieldOf("emitted_checkpoint_ids", List.of())
						.forGetter(Instance::emittedCheckpointIds),
					CALLBACK_DEFINITIONS_CODEC
						.optionalFieldOf("callback_definitions", List.of())
						.forGetter(Instance::callbackDefinitions),
					CallbackLedger.CODEC
						.fieldOf("callback_ledger")
						.forGetter(Instance::callbackLedger)
				)
				.apply(builder, Instance::new)
		).validate(Instance::validated);

		public Instance {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			Objects.requireNonNull(definition, "definition");
			launchPlan = Objects.requireNonNull(launchPlan, "launchPlan");
			Objects.requireNonNull(lifecycle, "lifecycle");
			participants = Objects.requireNonNull(participants, "participants").stream()
				.sorted(Comparator.comparing(FrozenParticipant::playerId))
				.toList();
			hostId = Objects.requireNonNull(hostId, "hostId");
			Objects.requireNonNull(disconnectPolicies, "disconnectPolicies");
			Objects.requireNonNull(restrictions, "restrictions");
			restrictionMarkers = Objects.requireNonNull(
				restrictionMarkers,
				"restrictionMarkers"
			).stream().sorted(Comparator.comparing(RestrictionMarker::participantId)).toList();
			checkpoints = Objects.requireNonNull(checkpoints, "checkpoints").stream()
				.sorted(
					Comparator.comparingLong(CheckpointDefinition::remainingTicks)
						.reversed()
						.thenComparing(CheckpointDefinition::id)
				)
				.toList();
			emittedCheckpointIds = Objects.requireNonNull(
				emittedCheckpointIds,
				"emittedCheckpointIds"
			).stream().sorted().toList();
			callbackDefinitions = Objects.requireNonNull(
				callbackDefinitions,
				"callbackDefinitions"
			).stream().sorted(CallbackDefinition.ORDER).toList();
			Objects.requireNonNull(callbackLedger, "callbackLedger");
		}

		public Instance withLifecycle(final Lifecycle value) {
			return copy(value, this.restrictionMarkers, this.emittedCheckpointIds, this.callbackLedger);
		}

		public Instance withRestrictionMarkers(final List<RestrictionMarker> value) {
			return copy(this.lifecycle, value, this.emittedCheckpointIds, this.callbackLedger);
		}

		public Instance withEmittedCheckpoints(
			final List<String> value,
			final Lifecycle nextLifecycle
		) {
			return copy(nextLifecycle, this.restrictionMarkers, value, this.callbackLedger);
		}

		public Instance withCallbackLedger(final CallbackLedger value) {
			return copy(this.lifecycle, this.restrictionMarkers, this.emittedCheckpointIds, value);
		}

		public Instance withLifecycleAndCallbacks(
			final Lifecycle nextLifecycle,
			final CallbackLedger nextLedger
		) {
			return copy(
				nextLifecycle,
				this.restrictionMarkers,
				this.emittedCheckpointIds,
				nextLedger
			);
		}

		private Instance copy(
			final Lifecycle nextLifecycle,
			final List<RestrictionMarker> nextMarkers,
			final List<String> nextEmitted,
			final CallbackLedger nextLedger
		) {
			return new Instance(
				this.gameInstanceId,
				this.countdownInstanceId,
				this.definition,
				this.launchPlan,
				nextLifecycle,
				this.totalTicks,
				this.participants,
				this.hostId,
				this.disconnectPolicies,
				this.restrictions,
				nextMarkers,
				this.checkpoints,
				nextEmitted,
				this.callbackDefinitions,
				nextLedger
			);
		}

		public DataResult<Instance> validated() {
			String error = validationError();
			return error == null ? DataResult.success(this) : DataResult.error(() -> error);
		}

		private String validationError() {
			if (this.totalTicks <= 0L) {
				return "countdown total ticks must be positive";
			}
			String nested = this.definition.validationError();
			if (nested != null) {
				return "countdown frozen definition: " + nested;
			}
			if (this.launchPlan.isPresent()) {
				nested = this.launchPlan.orElseThrow().validationError();
				if (nested != null) {
					return "countdown launch plan: " + nested;
				}
			}
			nested = this.lifecycle.validationError(this.totalTicks);
			if (nested != null) {
				return "countdown lifecycle: " + nested;
			}
			if (this.participants.isEmpty() || this.participants.size() > MAX_PARTICIPANTS) {
				return "countdown participant count is invalid";
			}
			Set<UUID> participantIds = new HashSet<>();
			for (FrozenParticipant participant : this.participants) {
				nested = participant.validationError();
				if (nested != null) {
					return "countdown participant: " + nested;
				}
				if (!participantIds.add(participant.playerId())) {
					return "duplicate countdown participant";
				}
			}
			if (
				this.launchPlan.isPresent()
					&& !Set.copyOf(this.launchPlan.orElseThrow().participantIds()).equals(participantIds)
			) {
				return "countdown launch plan participants do not match frozen participants";
			}
			Set<UUID> markerIds = new HashSet<>();
			for (RestrictionMarker marker : this.restrictionMarkers) {
				if (!participantIds.contains(marker.participantId()) || !markerIds.add(marker.participantId())) {
					return "countdown restriction markers do not match frozen participants";
				}
				if (marker.activationVersion() < 0L) {
					return "countdown restriction activation version cannot be negative";
				}
			}
			if (!markerIds.equals(participantIds)) {
				return "every frozen participant requires one restriction marker";
			}
			if (
				(this.lifecycle.state() == CountdownState.COMPLETED
					|| this.lifecycle.state() == CountdownState.CANCELED)
					&& this.restrictionMarkers.stream().anyMatch(RestrictionMarker::active)
			) {
				return "terminal countdown cannot retain active restriction markers";
			}
			if (this.checkpoints.size() > MAX_CHECKPOINTS) {
				return "countdown checkpoint count exceeds " + MAX_CHECKPOINTS;
			}
			Set<String> checkpointIds = new HashSet<>();
			Set<Long> checkpointTicks = new HashSet<>();
			for (CheckpointDefinition checkpoint : this.checkpoints) {
				nested = checkpoint.validationError(this.totalTicks);
				if (nested != null) {
					return "countdown checkpoint: " + nested;
				}
				if (!checkpointIds.add(checkpoint.id()) || !checkpointTicks.add(checkpoint.remainingTicks())) {
					return "countdown checkpoint id and remaining tick must be unique";
				}
			}
			Set<String> emitted = new HashSet<>();
			for (String id : this.emittedCheckpointIds) {
				if (!validLocalId(id) || !checkpointIds.contains(id) || !emitted.add(id)) {
					return "emitted countdown checkpoint is unknown or duplicated";
				}
			}
			if (this.callbackDefinitions.size() > MAX_CALLBACK_DEFINITIONS) {
				return "countdown callback definition count exceeds " + MAX_CALLBACK_DEFINITIONS;
			}
			Set<CallbackDefinitionKey> definitions = new HashSet<>();
			for (CallbackDefinition callback : this.callbackDefinitions) {
				nested = callback.validationError();
				if (nested != null) {
					return "countdown callback definition: " + nested;
				}
				if (!definitions.add(callback.definitionKey())) {
					return "duplicate countdown callback definition";
				}
			}
			nested = this.callbackLedger.validationError(
				this.countdownInstanceId,
				participantIds,
				this.callbackDefinitions
			);
			return nested == null ? null : "countdown callback ledger: " + nested;
		}
	}

	public record FrozenDefinition(
		Identifier definitionId,
		long generation,
		FrozenDocument closure
	) {
		public static final Codec<FrozenDefinition> CODEC =
			RecordCodecBuilder.<FrozenDefinition>create(
				builder -> builder.group(
						Identifier.CODEC.fieldOf("definition_id").forGetter(FrozenDefinition::definitionId),
						Codec.LONG.fieldOf("generation").forGetter(FrozenDefinition::generation),
						FrozenDocument.CODEC.fieldOf("closure").forGetter(FrozenDefinition::closure)
					)
					.apply(builder, FrozenDefinition::new)
			);

		public FrozenDefinition {
			Objects.requireNonNull(definitionId, "definitionId");
			Objects.requireNonNull(closure, "closure");
		}

		private String validationError() {
			if (this.generation < 0L) {
				return "generation cannot be negative";
			}
			return this.closure.sha256().matches("[0-9a-f]{64}")
				? null
				: "closure has no valid SHA-256 recovery identity";
		}
	}

	public record Lifecycle(
		CountdownState state,
		long stateVersion,
		long remainingTicks,
		long lastServerTickAnchor,
		Optional<String> recoveryReason,
		Optional<CountdownState> recoveryTarget
	) {
		public static final Codec<Lifecycle> CODEC = RecordCodecBuilder.<Lifecycle>create(
			builder -> builder.group(
					CountdownState.CODEC.fieldOf("state").forGetter(Lifecycle::state),
					Codec.LONG.fieldOf("state_version").forGetter(Lifecycle::stateVersion),
					Codec.LONG.fieldOf("remaining_ticks").forGetter(Lifecycle::remainingTicks),
					Codec.LONG
						.fieldOf("last_server_tick_anchor")
						.forGetter(Lifecycle::lastServerTickAnchor),
					REASON_CODEC.optionalFieldOf("recovery_reason").forGetter(Lifecycle::recoveryReason),
					CountdownState.CODEC
						.optionalFieldOf("recovery_target")
						.forGetter(Lifecycle::recoveryTarget)
				)
				.apply(builder, Lifecycle::new)
		);

		public Lifecycle {
			Objects.requireNonNull(state, "state");
			recoveryReason = Objects.requireNonNull(recoveryReason, "recoveryReason")
				.map(String::strip);
			recoveryTarget = Objects.requireNonNull(recoveryTarget, "recoveryTarget");
		}

		private String validationError(final long totalTicks) {
			if (
				this.stateVersion < 0L
					|| this.remainingTicks < 0L
					|| this.remainingTicks > totalTicks
					|| this.lastServerTickAnchor < 0L
			) {
				return "version, remaining ticks, or server tick anchor is out of range";
			}
			if (
				(this.state == CountdownState.COMPLETING || this.state == CountdownState.COMPLETED)
					&& this.remainingTicks != 0L
			) {
				return "completing or completed countdown must have zero remaining ticks";
			}
			if (this.state == CountdownState.RECOVERY_WAIT) {
				if (this.recoveryReason.isEmpty() || this.recoveryReason.orElseThrow().isBlank()) {
					return "recovery wait requires a recovery reason";
				}
				if (
					this.recoveryTarget.isEmpty()
						|| this.recoveryTarget.orElseThrow() == CountdownState.RECOVERY_WAIT
						|| this.recoveryTarget.orElseThrow().terminal()
				) {
					return "recovery wait requires a nonterminal resume target";
				}
				return null;
			}
			return this.recoveryReason.isPresent() || this.recoveryTarget.isPresent()
				? "recovery metadata may exist only in recovery_wait"
				: null;
		}
	}

	/** Immutable hand-off created by the host approval and consumed only after countdown completion. */
	public record LaunchPlan(
		Identifier approvalPhaseId,
		UUID timelineInstanceId,
		UUID firstTaskInstanceId,
		List<UUID> participantIds
	) {
		private static final Codec<List<UUID>> PARTICIPANT_IDS_CODEC =
			UUIDUtil.STRING_CODEC.sizeLimitedListOf(MAX_PARTICIPANTS);

		public static final Codec<LaunchPlan> CODEC = RecordCodecBuilder.<LaunchPlan>create(
			builder -> builder.group(
					Identifier.CODEC.fieldOf("approval_phase_id").forGetter(LaunchPlan::approvalPhaseId),
					UUIDUtil.STRING_CODEC
						.fieldOf("timeline_instance_id")
						.forGetter(LaunchPlan::timelineInstanceId),
					UUIDUtil.STRING_CODEC
						.fieldOf("first_task_instance_id")
						.forGetter(LaunchPlan::firstTaskInstanceId),
					PARTICIPANT_IDS_CODEC.fieldOf("participant_ids").forGetter(LaunchPlan::participantIds)
				)
				.apply(builder, LaunchPlan::new)
		).validate(value -> {
			String error = value.validationError();
			return error == null ? DataResult.success(value) : DataResult.error(() -> error);
		});

		public LaunchPlan {
			Objects.requireNonNull(approvalPhaseId, "approvalPhaseId");
			Objects.requireNonNull(timelineInstanceId, "timelineInstanceId");
			Objects.requireNonNull(firstTaskInstanceId, "firstTaskInstanceId");
			participantIds = Objects.requireNonNull(participantIds, "participantIds").stream()
				.sorted()
				.toList();
		}

		private String validationError() {
			if (this.participantIds.isEmpty() || this.participantIds.size() > MAX_PARTICIPANTS) {
				return "launch participant count is invalid";
			}
			return Set.copyOf(this.participantIds).size() == this.participantIds.size()
				? null
				: "launch participants contain duplicates";
		}
	}

	public record FrozenParticipant(UUID playerId, String frozenName, boolean required) {
		public static final Codec<FrozenParticipant> CODEC =
			RecordCodecBuilder.<FrozenParticipant>create(
				builder -> builder.group(
						UUIDUtil.STRING_CODEC.fieldOf("player_id").forGetter(FrozenParticipant::playerId),
						PLAYER_NAME_CODEC.fieldOf("frozen_name").forGetter(FrozenParticipant::frozenName),
						Codec.BOOL.optionalFieldOf("required", true).forGetter(FrozenParticipant::required)
					)
					.apply(builder, FrozenParticipant::new)
			);

		public FrozenParticipant {
			Objects.requireNonNull(playerId, "playerId");
			frozenName = Objects.requireNonNull(frozenName, "frozenName").strip();
		}

		private String validationError() {
			return this.frozenName.isBlank() || this.frozenName.length() > MAX_PLAYER_NAME_LENGTH
				? "frozen player name is blank or too long"
				: null;
		}
	}

	public record DisconnectPolicies(
		DisconnectPolicy requiredParticipant,
		DisconnectPolicy host
	) {
		public static final Codec<DisconnectPolicies> CODEC =
			RecordCodecBuilder.<DisconnectPolicies>create(
				builder -> builder.group(
						DisconnectPolicy.CODEC
							.optionalFieldOf("required_participant", DisconnectPolicy.PAUSE)
							.forGetter(DisconnectPolicies::requiredParticipant),
						DisconnectPolicy.CODEC
							.optionalFieldOf("host", DisconnectPolicy.CONTINUE)
							.forGetter(DisconnectPolicies::host)
					)
					.apply(builder, DisconnectPolicies::new)
			);

		public DisconnectPolicies {
			Objects.requireNonNull(requiredParticipant, "requiredParticipant");
			Objects.requireNonNull(host, "host");
		}

		public static DisconnectPolicies defaults() {
			return new DisconnectPolicies(DisconnectPolicy.PAUSE, DisconnectPolicy.CONTINUE);
		}
	}

	public record Restrictions(
		boolean freezeMovement,
		boolean blockAttack,
		boolean blockInteract,
		boolean blockItemUse,
		boolean blockItemDrop,
		boolean blockItemSwap,
		boolean blockInventory,
		boolean damageImmune
	) {
		public static final Codec<Restrictions> CODEC =
			RecordCodecBuilder.<Restrictions>create(
				builder -> builder.group(
						Codec.BOOL.optionalFieldOf("freeze_movement", true).forGetter(Restrictions::freezeMovement),
						Codec.BOOL.optionalFieldOf("block_attack", true).forGetter(Restrictions::blockAttack),
						Codec.BOOL.optionalFieldOf("block_interact", true).forGetter(Restrictions::blockInteract),
						Codec.BOOL.optionalFieldOf("block_item_use", true).forGetter(Restrictions::blockItemUse),
						Codec.BOOL.optionalFieldOf("block_item_drop", true).forGetter(Restrictions::blockItemDrop),
						Codec.BOOL.optionalFieldOf("block_item_swap", true).forGetter(Restrictions::blockItemSwap),
						Codec.BOOL.optionalFieldOf("block_inventory", true).forGetter(Restrictions::blockInventory),
						Codec.BOOL.optionalFieldOf("damage_immune", true).forGetter(Restrictions::damageImmune)
					)
					.apply(builder, Restrictions::new)
			);

		public static Restrictions defaults() {
			return new Restrictions(true, true, true, true, true, true, true, true);
		}
	}

	public record RestrictionMarker(UUID participantId, long activationVersion, boolean active) {
		public static final Codec<RestrictionMarker> CODEC =
			RecordCodecBuilder.<RestrictionMarker>create(
				builder -> builder.group(
						UUIDUtil.STRING_CODEC
							.fieldOf("participant_id")
							.forGetter(RestrictionMarker::participantId),
						Codec.LONG.fieldOf("activation_version").forGetter(RestrictionMarker::activationVersion),
						Codec.BOOL.fieldOf("active").forGetter(RestrictionMarker::active)
					)
					.apply(builder, RestrictionMarker::new)
			);

		public RestrictionMarker {
			Objects.requireNonNull(participantId, "participantId");
		}

		public RestrictionMarker released() {
			return this.active ? new RestrictionMarker(this.participantId, this.activationVersion, false) : this;
		}
	}

	public record CheckpointDefinition(String id, long remainingTicks) {
		public static final Codec<CheckpointDefinition> CODEC =
			RecordCodecBuilder.<CheckpointDefinition>create(
				builder -> builder.group(
						LOCAL_ID_CODEC.fieldOf("id").forGetter(CheckpointDefinition::id),
						Codec.LONG.fieldOf("remaining_ticks").forGetter(CheckpointDefinition::remainingTicks)
					)
					.apply(builder, CheckpointDefinition::new)
			);

		public CheckpointDefinition {
			id = Objects.requireNonNull(id, "id").strip();
		}

		private String validationError(final long totalTicks) {
			return !validLocalId(this.id) || this.remainingTicks < 0L || this.remainingTicks > totalTicks
				? "id or remaining ticks is invalid"
				: null;
		}
	}

	public record CallbackDefinition(
		CallbackSlot slot,
		String callbackId,
		CallbackScope scope,
		Identifier functionId,
		boolean required
	) {
		private static final Comparator<CallbackDefinition> ORDER = Comparator
			.<CallbackDefinition, String>comparing(
				value -> value.slot().getSerializedName()
			)
			.thenComparing(CallbackDefinition::callbackId)
			.thenComparing(value -> value.scope().getSerializedName());

		public static final Codec<CallbackDefinition> CODEC =
			RecordCodecBuilder.<CallbackDefinition>create(
				builder -> builder.group(
						CallbackSlot.CODEC.fieldOf("slot").forGetter(CallbackDefinition::slot),
						LOCAL_ID_CODEC.fieldOf("callback_id").forGetter(CallbackDefinition::callbackId),
						CallbackScope.CODEC.fieldOf("scope").forGetter(CallbackDefinition::scope),
						Identifier.CODEC.fieldOf("function_id").forGetter(CallbackDefinition::functionId),
						Codec.BOOL.optionalFieldOf("required", true).forGetter(CallbackDefinition::required)
					)
					.apply(builder, CallbackDefinition::new)
			);

		public CallbackDefinition {
			Objects.requireNonNull(slot, "slot");
			callbackId = Objects.requireNonNull(callbackId, "callbackId").strip();
			Objects.requireNonNull(scope, "scope");
			Objects.requireNonNull(functionId, "functionId");
		}

		private CallbackDefinitionKey definitionKey() {
			return new CallbackDefinitionKey(this.slot, this.callbackId);
		}

		private String validationError() {
			return validLocalId(this.callbackId) ? null : "callback id is invalid";
		}
	}

	private record CallbackDefinitionKey(CallbackSlot slot, String callbackId) {
	}

	public record CallbackLedger(List<CallbackEntry> entries) {
		private static final Codec<List<CallbackEntry>> ENTRIES_CODEC =
			CallbackEntry.CODEC.sizeLimitedListOf(MAX_CALLBACK_LEDGER_ENTRIES);

		public static final Codec<CallbackLedger> CODEC =
			RecordCodecBuilder.<CallbackLedger>create(
				builder -> builder.group(
						ENTRIES_CODEC.optionalFieldOf("entries", List.of()).forGetter(CallbackLedger::entries)
					)
					.apply(builder, CallbackLedger::new)
			);

		public CallbackLedger {
			entries = Objects.requireNonNull(entries, "entries").stream()
				.sorted(CallbackEntry.ORDER)
				.toList();
		}

		public static CallbackLedger empty() {
			return new CallbackLedger(List.of());
		}

		private String validationError(
			final UUID countdownInstanceId,
			final Set<UUID> participantIds,
			final List<CallbackDefinition> definitions
		) {
			if (this.entries.size() > MAX_CALLBACK_LEDGER_ENTRIES) {
				return "entry count exceeds " + MAX_CALLBACK_LEDGER_ENTRIES;
			}
			Set<CallbackKey> keys = new HashSet<>();
			for (CallbackEntry entry : this.entries) {
				String nested = entry.validationError();
				if (nested != null) {
					return nested;
				}
				if (!entry.key().countdownInstanceId().equals(countdownInstanceId) || !keys.add(entry.key())) {
					return "entry belongs to another countdown or has a duplicate key";
				}
				CallbackDefinition definition = definitions.stream()
					.filter(value -> value.slot() == entry.key().slot())
					.filter(value -> value.callbackId().equals(entry.key().callbackId()))
					.findFirst()
					.orElse(null);
				if (
					definition == null
						|| definition.scope() != entry.key().scope()
						|| !definition.functionId().equals(entry.functionId())
						|| definition.required() != entry.required()
				) {
					return "entry no longer matches its frozen callback definition";
				}
				if (
					entry.key().scope() == CallbackScope.GLOBAL
						? entry.key().participantId().isPresent()
						: entry.key().participantId().filter(participantIds::contains).isEmpty()
				) {
					return "entry participant does not match callback scope";
				}
			}
			return null;
		}
	}

	public record CallbackEntry(
		CallbackKey key,
		Identifier functionId,
		boolean required,
		CallbackStatus status,
		int attemptCount,
		Optional<Long> preparedAtServerTick,
		Optional<Long> outcomeAtServerTick,
		Optional<String> failureReason
	) {
		private static final Comparator<CallbackEntry> ORDER = Comparator
			.<CallbackEntry, String>comparing(
				value -> value.key().slot().getSerializedName()
			)
			.thenComparingLong(value -> value.key().occurrence())
			.thenComparing(value -> value.key().callbackId())
			.thenComparing(value -> value.key().participantId().map(UUID::toString).orElse(""));

		public static final Codec<CallbackEntry> CODEC =
			RecordCodecBuilder.<CallbackEntry>create(
				builder -> builder.group(
						CallbackKey.CODEC.fieldOf("key").forGetter(CallbackEntry::key),
						Identifier.CODEC.fieldOf("function_id").forGetter(CallbackEntry::functionId),
						Codec.BOOL.fieldOf("required").forGetter(CallbackEntry::required),
						CallbackStatus.CODEC.fieldOf("status").forGetter(CallbackEntry::status),
						Codec.INT.fieldOf("attempt_count").forGetter(CallbackEntry::attemptCount),
						Codec.LONG.optionalFieldOf("prepared_at_server_tick").forGetter(CallbackEntry::preparedAtServerTick),
						Codec.LONG.optionalFieldOf("outcome_at_server_tick").forGetter(CallbackEntry::outcomeAtServerTick),
						REASON_CODEC.optionalFieldOf("failure_reason").forGetter(CallbackEntry::failureReason)
					)
					.apply(builder, CallbackEntry::new)
			);

		public CallbackEntry {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(functionId, "functionId");
			Objects.requireNonNull(status, "status");
			preparedAtServerTick = Objects.requireNonNull(preparedAtServerTick, "preparedAtServerTick");
			outcomeAtServerTick = Objects.requireNonNull(outcomeAtServerTick, "outcomeAtServerTick");
			failureReason = Objects.requireNonNull(failureReason, "failureReason").map(String::strip);
		}

		private String validationError() {
			if (this.key.occurrence() < 0L || this.attemptCount < 0) {
				return "callback occurrence or attempt count cannot be negative";
			}
			if (
				this.preparedAtServerTick.filter(value -> value < 0L).isPresent()
					|| this.outcomeAtServerTick.filter(value -> value < 0L).isPresent()
			) {
				return "callback timestamps cannot be negative";
			}
			return switch (this.status) {
				case PENDING -> this.attemptCount == 0
					&& this.preparedAtServerTick.isEmpty()
					&& this.outcomeAtServerTick.isEmpty()
					&& this.failureReason.isEmpty()
					? null : "pending callback contains execution state";
				case PREPARED -> this.attemptCount > 0
					&& this.preparedAtServerTick.isPresent()
					&& this.outcomeAtServerTick.isEmpty()
					&& this.failureReason.isEmpty()
					? null : "prepared callback state is inconsistent";
				case SUCCEEDED -> this.attemptCount > 0
					&& this.preparedAtServerTick.isPresent()
					&& this.outcomeAtServerTick.isPresent()
					&& this.failureReason.isEmpty()
					? null : "successful callback state is inconsistent";
				case FAILED, OUTCOME_UNKNOWN -> this.attemptCount > 0
					&& this.preparedAtServerTick.isPresent()
					&& this.outcomeAtServerTick.isPresent()
					&& this.failureReason.filter(value -> !value.isBlank()).isPresent()
					? null : "failed or unknown callback state is inconsistent";
			};
		}
	}

	public record CallbackKey(
		UUID countdownInstanceId,
		CallbackSlot slot,
		String callbackId,
		CallbackScope scope,
		Optional<UUID> participantId,
		long occurrence
	) {
		public static final Codec<CallbackKey> CODEC = RecordCodecBuilder.<CallbackKey>create(
			builder -> builder.group(
					UUIDUtil.STRING_CODEC
						.fieldOf("countdown_instance_id")
						.forGetter(CallbackKey::countdownInstanceId),
					CallbackSlot.CODEC.fieldOf("slot").forGetter(CallbackKey::slot),
					LOCAL_ID_CODEC.fieldOf("callback_id").forGetter(CallbackKey::callbackId),
					CallbackScope.CODEC.fieldOf("scope").forGetter(CallbackKey::scope),
					UUIDUtil.STRING_CODEC.optionalFieldOf("participant_id").forGetter(CallbackKey::participantId),
					Codec.LONG.fieldOf("occurrence").forGetter(CallbackKey::occurrence)
				)
				.apply(builder, CallbackKey::new)
		);

		public CallbackKey {
			Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			Objects.requireNonNull(slot, "slot");
			callbackId = Objects.requireNonNull(callbackId, "callbackId").strip();
			Objects.requireNonNull(scope, "scope");
			participantId = Objects.requireNonNull(participantId, "participantId");
		}
	}

	public enum CountdownState implements StringRepresentable {
		RUNNING("running"),
		WAITING_FOR_PLAYERS("waiting_for_players"),
		RECOVERY_WAIT("recovery_wait"),
		COMPLETING("completing"),
		CANCELING("canceling"),
		COMPLETED("completed"),
		CANCELED("canceled");

		public static final Codec<CountdownState> CODEC =
			StringRepresentable.fromEnum(CountdownState::values);
		private final String serializedName;

		CountdownState(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}

		public boolean terminal() {
			return this == COMPLETED || this == CANCELED;
		}
	}

	public enum DisconnectPolicy implements StringRepresentable {
		PAUSE("pause"),
		CANCEL("cancel"),
		CONTINUE("continue");

		public static final Codec<DisconnectPolicy> CODEC =
			StringRepresentable.fromEnum(DisconnectPolicy::values);
		private final String serializedName;

		DisconnectPolicy(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackSlot implements StringRepresentable {
		START("start"),
		PAUSE("pause"),
		RESUME("resume"),
		CANCEL("cancel"),
		COMPLETE("complete");

		public static final Codec<CallbackSlot> CODEC =
			StringRepresentable.fromEnum(CallbackSlot::values);
		private final String serializedName;

		CallbackSlot(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackScope implements StringRepresentable {
		GLOBAL("global"),
		EACH_PARTICIPANT("each_participant");

		public static final Codec<CallbackScope> CODEC =
			StringRepresentable.fromEnum(CallbackScope::values);
		private final String serializedName;

		CallbackScope(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	public enum CallbackStatus implements StringRepresentable {
		PENDING("pending"),
		PREPARED("prepared"),
		SUCCEEDED("succeeded"),
		FAILED("failed"),
		OUTCOME_UNKNOWN("outcome_unknown");

		public static final Codec<CallbackStatus> CODEC =
			StringRepresentable.fromEnum(CallbackStatus::values);
		private final String serializedName;

		CallbackStatus(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}

	private static boolean validLocalId(final String value) {
		return value != null
			&& value.matches("[a-z0-9_.-]{1," + MAX_LOCAL_ID_LENGTH + "}");
	}
}
