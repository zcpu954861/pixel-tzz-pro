package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AttachmentMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DuplicateMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundAttachment;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.Ledger;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.CooldownStamp;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.DedupeKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.EventKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.OccurrenceKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RefreshKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RuntimeState;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.CheckpointResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryAction;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryActionType;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure conversion boundary between live message authority values and stable persistence DTOs.
 *
 * <p>The mapper has no Minecraft server, registry, packet, rendering, or callback side effects.
 * Recipient-stream internals remain owned by the integration layer: checkpointing receives their
 * stable snapshots, while recovery returns those snapshots beside parsed cue and authority state
 * so the integration layer can rebuild private streams deliberately.
 */
public final class MessageRuntimePersistenceMapper {
	private MessageRuntimePersistenceMapper() {
	}

	/**
	 * Maps one live authority state into a codec-valid bounded snapshot. New persistent instances
	 * must always provide an invocation origin and one stable recipient snapshot per frozen target.
	 */
	public static CheckpointMappingResult checkpoint(final CheckpointInput input) {
		Objects.requireNonNull(input, "input");
		try {
			List<PersistedMessageRuntime.Instance> instances = new ArrayList<>();
			List<PersistedMessageRuntime.Tombstone> tombstones = new ArrayList<>(input.tombstones());
			Set<UUID> retainedIds = new LinkedHashSet<>();
			int skippedTransient = 0;
			int terminalInstances = 0;
			for (MessageInstance value : input.runtimeState().instances().values().stream()
				.sorted(Comparator.comparing(MessageInstance::instanceId))
				.toList()) {
				if (value.status().terminal()) {
					terminalInstances++;
					tombstones.add(terminalTombstone(value, input.clock().gameTick()));
					continue;
				}
				PersistedMessageRuntime.RestartMode restart = toPersistedRestart(
					value.cue().policies().lifecycle().restart()
				);
				if (restart == PersistedMessageRuntime.RestartMode.TRANSIENT) {
					skippedTransient++;
					continue;
				}
				PersistedMessageRuntime.Origin origin = input.origins().get(value.instanceId());
				if (origin == null) {
					return CheckpointMappingResult.rejected(
						"持久化消息实例缺少调用维度与原点：" + value.instanceId()
					);
				}
				List<PersistedMessageRuntime.RecipientState> recipients = input.recipientStates()
					.getOrDefault(value.instanceId(), List.of());
				FrozenDocument dependencySnapshot = input.dependencySnapshots().get(
					value.instanceId()
				);
				if (dependencySnapshot == null) {
					return CheckpointMappingResult.rejected(
						"持久化消息实例缺少冻结依赖快照：" + value.instanceId()
					);
				}
				Set<UUID> recipientIds = new LinkedHashSet<>();
				recipients.forEach(recipient -> recipientIds.add(recipient.recipientId()));
				if (
					recipientIds.size() != recipients.size()
						|| !recipientIds.equals(value.cueTargets())
				) {
					return CheckpointMappingResult.rejected(
						"持久化消息实例的 recipient 快照与冻结受众不一致：" + value.instanceId()
					);
				}
				recipients.forEach(recipient ->
					MessageRecipientStateCodec.decode(value.cue(), recipient)
				);
				instances.add(
					mapInstance(
						value,
						restart,
						origin,
						recipients,
						dependencySnapshot,
						input.clock()
					)
				);
				retainedIds.add(value.instanceId());
			}

			List<PersistedMessageRuntime.PendingOfflineDelivery> pending = input.pendingOffline()
				.stream()
				.filter(value -> retainedIds.contains(value.instanceId()))
				.toList();
			PersistedMessageRuntime.CallbackLedger callbacks = toPersistedLedger(
				input.runtimeState().callbackLedger(),
				retainedIds
			);
			PersistedMessageRuntime.Snapshot raw = new PersistedMessageRuntime.Snapshot(
				input.gameInstanceId(),
				input.clock().gameTick(),
				input.runtimeState().revision(),
				instances,
				pending,
				tombstones,
				callbacks
			);
			CheckpointResult bounded = MessageRuntimePersistenceAuthority.checkpoint(raw);
			if (!bounded.accepted()) {
				return CheckpointMappingResult.rejected(bounded.message());
			}
			return new CheckpointMappingResult(
				bounded.snapshot(),
				skippedTransient,
				terminalInstances,
				"消息运行时已映射为稳定检查点"
			);
		} catch (RuntimeException error) {
			return CheckpointMappingResult.rejected(
				"消息运行时检查点映射失败：" + safeMessage(error)
			);
		}
	}

	/**
	 * Rebuilds parsed cues, authority instances/indexes/ledger, and structured stream/environment
	 * inputs from one authority recovery plan. A malformed instance is cancelled independently;
	 * other instances remain recoverable. PREPARED callbacks are never accepted here even if a
	 * caller bypasses the recovery authority.
	 */
	public static RecoveryMappingResult recover(
		final RecoveryPlan plan,
		final ClockSample recoveryClock
	) {
		return recover(plan, recoveryClock, OptionalLong.empty());
	}

	/**
	 * Rebuilds a cold-start plan against the definition generation accepted by this JVM.
	 *
	 * <p>Persisted generation numbers are process-local ordering tokens. They are useful while a
	 * server process is alive, but must not be reused after restart as an asset-protocol identity.
	 * The frozen cue and all persisted semantic inputs remain unchanged; only the rebuilt live
	 * invocation context is rebound to the caller-supplied, already verified generation.</p>
	 */
	public static RecoveryMappingResult recover(
		final RecoveryPlan plan,
		final ClockSample recoveryClock,
		final long activeDefinitionGeneration
	) {
		if (activeDefinitionGeneration < 0L) {
			throw new IllegalArgumentException("active definition generation cannot be negative");
		}
		return recover(plan, recoveryClock, OptionalLong.of(activeDefinitionGeneration));
	}

	private static RecoveryMappingResult recover(
		final RecoveryPlan plan,
		final ClockSample recoveryClock,
		final OptionalLong reboundDefinitionGeneration
	) {
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(recoveryClock, "recoveryClock");
		Objects.requireNonNull(reboundDefinitionGeneration, "reboundDefinitionGeneration");
		if (!plan.accepted()) {
			return RecoveryMappingResult.rejected(
				"消息恢复计划不可用：" + plan.message()
			);
		}
		PersistedMessageRuntime.Snapshot next = plan.nextSnapshot().orElseThrow();
		Map<UUID, List<PersistedMessageRuntime.CallbackEntry>> callbacksByInstance =
			new LinkedHashMap<>();
		for (PersistedMessageRuntime.CallbackEntry entry : next.callbackLedger().entries()) {
			callbacksByInstance.computeIfAbsent(entry.key().instanceId(), ignored -> new ArrayList<>())
				.add(entry);
		}

		Map<UUID, Candidate> candidates = new LinkedHashMap<>();
		Map<UUID, RejectedInstance> rejected = new LinkedHashMap<>();
		Set<UUID> seenActionIds = new LinkedHashSet<>();
		for (RecoveryAction action : plan.actions()) {
			PersistedMessageRuntime.Instance persisted = action.instance();
			if (!seenActionIds.add(persisted.instanceId())) {
				return RecoveryMappingResult.rejected("消息恢复计划包含重复实例动作");
			}
			if (action.type() == RecoveryActionType.CANCEL) {
				/*
				 * The pure recovery authority has already removed restart=cancel from the
				 * retained instance set and written its authoritative cancelled tombstone.
				 * Re-parsing the cue or rebuilding concurrency addresses here cannot make
				 * that terminal decision safer; it can only overwrite the correct restart
				 * reason with an unrelated mapping failure.
				 */
				continue;
			}
			try {
				List<PersistedMessageRuntime.CallbackEntry> instanceCallbacks = callbacksByInstance
					.getOrDefault(persisted.instanceId(), List.of());
				MappedInstance mapped = restoreInstance(
					persisted,
					recoveryClock,
					instanceCallbacks,
					reboundDefinitionGeneration,
					action.type() == RecoveryActionType.RESTORE_CONTINUE
				);
				candidates.put(
					persisted.instanceId(),
					new Candidate(
						persisted,
						new RestoredAction(
							action.type(),
							mapped.instance(),
							mapped.environment(),
							persisted.recipients()
						)
					)
				);
			} catch (RuntimeException error) {
				reject(
					rejected,
					persisted,
					recoveryClock.gameTick(),
					"恢复实例失败：" + safeMessage(error)
				);
			}
		}

		for (Map.Entry<UUID, List<PersistedMessageRuntime.CallbackEntry>> group : callbacksByInstance.entrySet()) {
			Candidate candidate = candidates.get(group.getKey());
			if (candidate == null && rejected.containsKey(group.getKey())) {
				continue;
			}
			if (candidate == null || candidate.action().type() != RecoveryActionType.RESTORE_CONTINUE) {
				return RecoveryMappingResult.rejected(
					"回调账本引用了未继续恢复的消息实例：" + group.getKey()
				);
			}
			try {
				for (PersistedMessageRuntime.CallbackEntry entry : group.getValue()) {
					validateCallbackEntry(candidate.action().instance(), entry);
				}
			} catch (RuntimeException error) {
				candidates.remove(group.getKey());
				reject(
					rejected,
					candidate.persisted(),
					recoveryClock.gameTick(),
					"回调账本无法安全恢复：" + safeMessage(error)
				);
			}
		}

		rejectIndexCollisions(candidates, rejected, recoveryClock.gameTick());
		Set<UUID> acceptedContinueIds = candidates.values().stream()
			.filter(value -> value.action().type() == RecoveryActionType.RESTORE_CONTINUE)
			.map(value -> value.persisted().instanceId())
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<UUID> acceptedPersistedIds = candidates.values().stream()
			.filter(value ->
				value.action().type() == RecoveryActionType.RESTORE_CONTINUE
					|| value.action().type() == RecoveryActionType.FINALIZE_STATIC
			)
			.map(value -> value.persisted().instanceId())
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

		List<CallbackEntry> runtimeCallbacks = new ArrayList<>();
		List<PersistedMessageRuntime.CallbackEntry> persistedCallbacks = new ArrayList<>();
		for (PersistedMessageRuntime.CallbackEntry value : next.callbackLedger().entries()) {
			if (!acceptedContinueIds.contains(value.key().instanceId())) {
				continue;
			}
			try {
				runtimeCallbacks.add(toRuntimeCallbackEntry(value));
				persistedCallbacks.add(value);
			} catch (RuntimeException error) {
				return RecoveryMappingResult.rejected(
					"已验证回调账本仍无法构造：" + safeMessage(error)
				);
			}
		}

		Map<UUID, MessageInstance> runtimeInstances = new LinkedHashMap<>();
		Map<DedupeKey, UUID> activeDedupe = new LinkedHashMap<>();
		Map<RefreshKey, UUID> refreshIndex = new LinkedHashMap<>();
		for (Candidate value : candidates.values()) {
			if (value.action().type() != RecoveryActionType.RESTORE_CONTINUE) {
				continue;
			}
			MessageInstance instance = value.action().instance();
			runtimeInstances.put(instance.instanceId(), instance);
			if (
				instance.status().active()
					&& instance.cue().policies().concurrency().duplicate() != DuplicateMode.ALLOW
			) {
				activeDedupe.put(instance.dedupeKey(), instance.instanceId());
			}
			instance.refreshKey().ifPresent(key -> refreshIndex.put(key, instance.instanceId()));
		}

		RuntimeState runtimeState;
		try {
			runtimeState = new RuntimeState(
				next.runtimeRevision(),
				runtimeInstances,
				List.of(),
				activeDedupe,
				refreshIndex,
				Map.<DedupeKey, CooldownStamp>of(),
				new Ledger(next.callbackLedger().capacity(), runtimeCallbacks)
			);
		} catch (RuntimeException error) {
			return RecoveryMappingResult.rejected(
				"消息权威运行状态无法重建：" + safeMessage(error)
			);
		}

		List<PersistedMessageRuntime.Tombstone> tombstones = new ArrayList<>(next.tombstones());
		Map<UUID, PersistedMessageRuntime.Tombstone> rejectionTombstones = new LinkedHashMap<>();
		rejected.values().forEach(value -> rejectionTombstones.put(value.instanceId(), value.tombstone()));
		if (!rejectionTombstones.isEmpty()) {
			tombstones.removeIf(value -> rejectionTombstones.containsKey(value.instanceId()));
			tombstones.addAll(rejectionTombstones.values());
		}
		PersistedMessageRuntime.Snapshot reconciledRaw = new PersistedMessageRuntime.Snapshot(
			next.gameInstanceId(),
			next.snapshotGameTick(),
			next.runtimeRevision(),
			next.instances().stream()
				.filter(value -> acceptedPersistedIds.contains(value.instanceId()))
				.toList(),
			next.pendingOffline().stream()
				.filter(value -> acceptedContinueIds.contains(value.instanceId()))
				.toList(),
			tombstones,
			new PersistedMessageRuntime.CallbackLedger(
				next.callbackLedger().capacity(),
				persistedCallbacks
			)
		);
		CheckpointResult reconciled = MessageRuntimePersistenceAuthority.checkpoint(reconciledRaw);
		if (!reconciled.accepted()) {
			return RecoveryMappingResult.rejected(
				"恢复拒绝状态无法安全落盘：" + reconciled.message()
			);
		}
		return new RecoveryMappingResult(
			Optional.of(runtimeState),
			reconciled.snapshot(),
			candidates.values().stream().map(Candidate::action).toList(),
			List.copyOf(rejected.values()),
			"消息运行时持久化恢复映射已完成"
		);
	}

	private static PersistedMessageRuntime.Instance mapInstance(
		final MessageInstance value,
		final PersistedMessageRuntime.RestartMode restart,
		final PersistedMessageRuntime.Origin origin,
		final List<PersistedMessageRuntime.RecipientState> recipients,
		final FrozenDocument dependencySnapshot,
		final ClockSample clock
	) {
		Map<String, Long> durations = new TreeMap<>();
		value.authoritativeNodeDurations().forEach(
			(key, duration) -> durations.put(key, duration.nanoseconds())
		);
		List<PersistedMessageRuntime.NodeAudience> nodeTargets = value.nodeTargets().entrySet()
			.stream()
			.map(entry -> new PersistedMessageRuntime.NodeAudience(
				entry.getKey(),
				List.copyOf(entry.getValue())
			))
			.toList();
		List<PersistedMessageRuntime.Occurrence> emitted = value.emittedOccurrences().stream()
			.map(MessageRuntimePersistenceMapper::toPersistedOccurrence)
			.toList();
		List<PersistedMessageRuntime.EventOffset> eventOffsets = value.eventOffsets().entrySet()
			.stream()
			.map(entry -> new PersistedMessageRuntime.EventOffset(
				toPersistedTrigger(entry.getKey().trigger()),
				entry.getKey().targetId(),
				entry.getValue()
			))
			.toList();
		return new PersistedMessageRuntime.Instance(
			value.instanceId(),
			value.cue().id(),
			restart,
			new PersistedMessageRuntime.FrozenCue(
				value.context().definitionGeneration(),
				FrozenDocument.of(value.cue().canonicalDocument()),
				Optional.of(dependencySnapshot.sha256()),
				Optional.of(dependencySnapshot)
			),
			new PersistedMessageRuntime.InvocationContext(
				value.context().invokerId(),
				List.copyOf(value.context().callTargets()),
				value.context().gameId(),
				value.context().phaseId(),
				value.context().taskId(),
				value.context().arguments(),
				value.context().bypassContext(),
				value.context().externallyTimed(),
				Optional.of(origin)
			),
			value.canonicalParameters(),
			new PersistedMessageRuntime.AudienceState(
				List.copyOf(value.cueTargets()),
				nodeTargets
			),
			new PersistedMessageRuntime.TimingState(
				toPersistedClock(value.clockMode()),
				durations,
				Optional.empty()
			),
			new PersistedMessageRuntime.ProgressState(
				toPersistedStatus(value.status()),
				value.resumeStatus().map(MessageRuntimePersistenceMapper::toPersistedStatus),
				value.cycle(),
				value.elapsedAt(clock),
				emitted,
				List.copyOf(value.completedTargets()),
				eventOffsets,
				value.forcedCompletion(),
				value.instanceRevision()
			),
			recipients,
			Optional.of(fingerprint(value.dedupeKey())),
			value.refreshKey().map(MessageRuntimePersistenceMapper::fingerprint),
			clock.gameTick()
		);
	}

	private static MappedInstance restoreInstance(
		final PersistedMessageRuntime.Instance persisted,
		final ClockSample recoveryClock,
		final List<PersistedMessageRuntime.CallbackEntry> callbacks,
		final OptionalLong reboundDefinitionGeneration,
		final boolean verifyConcurrencyAddresses
	) {
		String canonical = persisted.frozenCue().canonicalDocument().normalizedJson();
		FrozenDocument rebuiltDocument = FrozenDocument.of(canonical);
		if (
			canonical.isBlank()
				|| !rebuiltDocument.sha256().equals(
					persisted.frozenCue().canonicalDocument().sha256()
				)
		) {
			throw new IllegalArgumentException("冻结 cue 文档摘要不匹配或正文缺失");
		}
		var parsed = MessageDefinitionParser.parseMessageCue(persisted.cueId(), canonical);
		if (!parsed.valid()) {
			throw new IllegalArgumentException("冻结 cue 解析失败：" + parseIssues(parsed));
		}
		MessageCueDefinition cue = parsed.value().orElseThrow();
		if (!cue.canonicalDocument().equals(canonical)) {
			throw new IllegalArgumentException("冻结 cue 不再产生相同 canonical 文档");
		}
		persisted.recipients().forEach(recipient ->
			MessageRecipientStateCodec.decode(cue, recipient)
		);
		boolean originRequired = requiresOrigin(cue)
			|| callbacks.stream().anyMatch(
				value -> value.location() == PersistedMessageRuntime.CallbackLocation.ORIGIN
			);
		if (originRequired && persisted.context().origin().isEmpty()) {
			throw new IllegalArgumentException("世界绑定声音或原点回调缺少冻结调用原点");
		}

		MessageInstanceAuthority.InvocationContext context =
			new MessageInstanceAuthority.InvocationContext(
				persisted.instanceId(),
				true,
				persisted.context().invokerId(),
				Set.copyOf(persisted.context().callTargets()),
				persisted.context().gameId(),
				persisted.context().phaseId(),
				persisted.context().taskId(),
				persisted.context().arguments(),
				reboundDefinitionGeneration.orElse(
					persisted.frozenCue().definitionGeneration()
				),
				persisted.context().bypassContext(),
				persisted.context().externallyTimed()
			);
		Map<String, TimeSpan> durations = new LinkedHashMap<>();
		persisted.timing().authoritativeNodeDurationsNanos().forEach(
			(key, nanos) -> durations.put(key, new TimeSpan(nanos))
		);
		Set<UUID> cueTargets = Set.copyOf(persisted.audience().cueTargets());
		Map<String, Set<UUID>> nodeTargets = new LinkedHashMap<>();
		persisted.audience().nodeTargets().forEach(
			value -> nodeTargets.put(value.nodeId(), Set.copyOf(value.recipients()))
		);
		DedupeKey dedupeKey = dedupeKey(cue, cueTargets, persisted.canonicalParameters());
		Optional<RefreshKey> refreshKey = refreshKey(cue, persisted.canonicalParameters());
		if (
			verifyConcurrencyAddresses
				&& (
					persisted.dedupeKey().filter(
						value -> !value.equals(fingerprint(dedupeKey))
					).isPresent()
						|| persisted.refreshKey().isPresent() != refreshKey.isPresent()
						|| persisted.refreshKey().filter(
							value -> refreshKey.filter(
								key -> value.equals(fingerprint(key))
							).isEmpty()
						).isPresent()
				)
		) {
			throw new IllegalArgumentException("冻结去重或刷新地址与 cue/参数不一致");
		}
		ClockMode clockMode = toRuntimeClock(persisted.timing().clock());
		MessageInstance instance = new MessageInstance(
			persisted.instanceId(),
			cue,
			context,
			persisted.canonicalParameters(),
			durations,
			cueTargets,
			nodeTargets,
			dedupeKey,
			refreshKey,
			clockMode,
			persisted.progress().cycle(),
			toRuntimeStatus(persisted.progress().status()),
			persisted.progress().resumeStatus().map(MessageRuntimePersistenceMapper::toRuntimeStatus),
			recoveryClock.value(clockMode),
			persisted.progress().standardElapsedNanos(),
			persisted.progress().emittedOccurrences().stream()
				.map(MessageRuntimePersistenceMapper::toRuntimeOccurrence)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
			Set.copyOf(persisted.progress().completedTargets()),
			persisted.progress().eventOffsets().stream().collect(
				java.util.stream.Collectors.toMap(
					value -> new EventKey(toRuntimeTrigger(value.trigger()), value.targetId()),
					PersistedMessageRuntime.EventOffset::offsetNanos,
					(left, right) -> {
						throw new IllegalArgumentException("重复事件偏移");
					},
					LinkedHashMap::new
				)
			),
			persisted.progress().forcedCompletion(),
			persisted.progress().instanceRevision()
		);
		RestoredInvocationEnvironment environment = new RestoredInvocationEnvironment(
			persisted.context().origin(),
			persisted.context().invokerId(),
			Set.copyOf(persisted.context().callTargets()),
			persisted.context().arguments()
		);
		return new MappedInstance(instance, environment);
	}

	private static void validateCallbackEntry(
		final MessageInstance instance,
		final PersistedMessageRuntime.CallbackEntry value
	) {
		if (value.status() == PersistedMessageRuntime.CallbackStatus.PREPARED) {
			throw new IllegalArgumentException("PREPARED 回调未先转为 OUTCOME_UNKNOWN");
		}
		if (value.key().cycle() > instance.cycle()) {
			throw new IllegalArgumentException("回调 cycle 超过恢复实例 cycle");
		}
		toRuntimeCallbackEntry(value);
		if (value.key().cycle() != instance.cycle()) {
			return;
		}
		OccurrenceKey occurrence = new OccurrenceKey(
			value.key().nodeId(),
			value.key().occurrence(),
			toRuntimeTrigger(value.key().trigger()),
			value.key().targetId()
		);
		if (!instance.emittedOccurrences().contains(occurrence)) {
			throw new IllegalArgumentException("当前 cycle 回调不属于已提交 occurrence");
		}
		CueNode node = instance.cue().nodes().stream()
			.filter(candidate -> candidate.id().equals(value.key().nodeId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("回调节点不存在"));
		if (
			!(node instanceof CallbackNode callback)
				|| !callback.function().equals(value.functionId())
				|| callback.execution() != toRuntimeExecution(value.execution())
				|| callback.location() != toRuntimeLocation(value.location())
		) {
			throw new IllegalArgumentException("回调键无法映射到冻结 cue 回调节点");
		}
	}

	private static void rejectIndexCollisions(
		final Map<UUID, Candidate> candidates,
		final Map<UUID, RejectedInstance> rejected,
		final long gameTick
	) {
		Map<DedupeKey, List<Candidate>> dedupe = new HashMap<>();
		Map<RefreshKey, List<Candidate>> refresh = new HashMap<>();
		for (Candidate candidate : candidates.values()) {
			if (candidate.action().type() != RecoveryActionType.RESTORE_CONTINUE) {
				continue;
			}
			MessageInstance instance = candidate.action().instance();
			if (
				instance.status().active()
					&& instance.cue().policies().concurrency().duplicate() != DuplicateMode.ALLOW
			) {
				dedupe.computeIfAbsent(instance.dedupeKey(), ignored -> new ArrayList<>())
					.add(candidate);
			}
			instance.refreshKey().ifPresent(
				key -> refresh.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate)
			);
		}
		Set<UUID> collisions = new LinkedHashSet<>();
		dedupe.values().stream().filter(values -> values.size() > 1).forEach(
			values -> values.forEach(value -> collisions.add(value.persisted().instanceId()))
		);
		refresh.values().stream().filter(values -> values.size() > 1).forEach(
			values -> values.forEach(value -> collisions.add(value.persisted().instanceId()))
		);
		for (UUID id : collisions) {
			Candidate candidate = candidates.remove(id);
			if (candidate != null) {
				reject(rejected, candidate.persisted(), gameTick, "恢复索引发生去重或刷新键冲突");
			}
		}
	}

	private static void reject(
		final Map<UUID, RejectedInstance> rejected,
		final PersistedMessageRuntime.Instance instance,
		final long gameTick,
		final String reason
	) {
		String bounded = boundedReason(reason);
		rejected.putIfAbsent(
			instance.instanceId(),
			new RejectedInstance(
				instance.instanceId(),
				instance.cueId(),
				bounded,
				new PersistedMessageRuntime.Tombstone(
					instance.instanceId(),
					instance.cueId(),
					PersistedMessageRuntime.TerminalStatus.CANCELLED,
					gameTick,
					bounded
				)
			)
		);
	}

	private static boolean requiresOrigin(final MessageCueDefinition cue) {
		if (cue.policies().lifecycle().attachment() == AttachmentMode.WORLD) {
			return true;
		}
		for (CueNode node : cue.nodes()) {
			if (node instanceof CallbackNode callback && callback.location() == CallbackLocation.ORIGIN) {
				return true;
			}
			if (node instanceof SoundNode sound && sound.attachment() == SoundAttachment.WORLD_ORIGIN) {
				return true;
			}
		}
		return false;
	}

	private static DedupeKey dedupeKey(
		final MessageCueDefinition cue,
		final Set<UUID> targets,
		final Map<String, String> parameters
	) {
		Set<String> configured = new LinkedHashSet<>(
			cue.policies().concurrency().dedupeParameters()
		);
		cue.parameters().forEach((key, definition) -> {
			if (definition.dedupe()) {
				configured.add(key);
			}
		});
		Map<String, String> selected = new LinkedHashMap<>();
		for (String key : configured.stream().sorted().toList()) {
			String value = parameters.get(key);
			if (value != null) {
				selected.put(key, value);
			}
		}
		return new DedupeKey(
			cue.id(),
			cue.policies().concurrency().dedupeTargets() ? targets : Set.of(),
			selected
		);
	}

	private static Optional<RefreshKey> refreshKey(
		final MessageCueDefinition cue,
		final Map<String, String> parameters
	) {
		Optional<String> parameter = cue.policies().concurrency().refreshKey();
		if (parameter.isEmpty()) {
			return Optional.empty();
		}
		String value = parameters.get(parameter.orElseThrow());
		if (value == null) {
			throw new IllegalArgumentException("冻结刷新键参数缺少 canonical 值");
		}
		return Optional.of(new RefreshKey(cue.id(), parameter.orElseThrow(), value));
	}

	private static PersistedMessageRuntime.CallbackLedger toPersistedLedger(
		final Ledger ledger,
		final Set<UUID> instanceIds
	) {
		return new PersistedMessageRuntime.CallbackLedger(
			ledger.capacity(),
			ledger.entries().stream()
				.filter(value -> instanceIds.contains(value.key().instanceId()))
				.map(MessageRuntimePersistenceMapper::toPersistedCallbackEntry)
				.toList()
		);
	}

	private static PersistedMessageRuntime.CallbackEntry toPersistedCallbackEntry(
		final CallbackEntry value
	) {
		return new PersistedMessageRuntime.CallbackEntry(
			new PersistedMessageRuntime.CallbackKey(
				value.key().instanceId(),
				value.key().cycle(),
				value.key().nodeId(),
				value.key().occurrence(),
				toPersistedTrigger(value.key().trigger()),
				value.key().targetId()
			),
			value.functionId(),
			toPersistedExecution(value.execution()),
			toPersistedLocation(value.location()),
			toPersistedCallbackStatus(value.status()),
			value.attemptCount(),
			value.updatedAtGameTick(),
			value.diagnostic()
		);
	}

	private static CallbackEntry toRuntimeCallbackEntry(
		final PersistedMessageRuntime.CallbackEntry value
	) {
		if (value.status() == PersistedMessageRuntime.CallbackStatus.PREPARED) {
			throw new IllegalArgumentException("PREPARED 回调不得直接恢复");
		}
		return new CallbackEntry(
			new CallbackKey(
				value.key().instanceId(),
				value.key().cycle(),
				value.key().nodeId(),
				value.key().occurrence(),
				toRuntimeTrigger(value.key().trigger()),
				value.key().targetId()
			),
			value.functionId(),
			toRuntimeExecution(value.execution()),
			toRuntimeLocation(value.location()),
			toRuntimeCallbackStatus(value.status()),
			value.attemptCount(),
			value.updatedAtGameTick(),
			value.diagnostic()
		);
	}

	private static PersistedMessageRuntime.Tombstone terminalTombstone(
		final MessageInstance value,
		final long gameTick
	) {
		return new PersistedMessageRuntime.Tombstone(
			value.instanceId(),
			value.cue().id(),
			value.status() == InstanceStatus.COMPLETED
				? PersistedMessageRuntime.TerminalStatus.COMPLETED
				: PersistedMessageRuntime.TerminalStatus.CANCELLED,
			gameTick,
			value.status() == InstanceStatus.COMPLETED ? "消息演出已完成" : "消息演出已取消"
		);
	}

	private static PersistedMessageRuntime.Occurrence toPersistedOccurrence(
		final OccurrenceKey value
	) {
		return new PersistedMessageRuntime.Occurrence(
			value.nodeId(),
			value.occurrence(),
			toPersistedTrigger(value.trigger()),
			value.targetId()
		);
	}

	private static OccurrenceKey toRuntimeOccurrence(
		final PersistedMessageRuntime.Occurrence value
	) {
		return new OccurrenceKey(
			value.nodeId(),
			value.occurrence(),
			toRuntimeTrigger(value.trigger()),
			value.targetId()
		);
	}

	private static PersistedMessageRuntime.RestartMode toPersistedRestart(
		final io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RestartMode value
	) {
		return switch (value) {
			case TRANSIENT -> PersistedMessageRuntime.RestartMode.TRANSIENT;
			case CONTINUE -> PersistedMessageRuntime.RestartMode.CONTINUE;
			case FINALIZE -> PersistedMessageRuntime.RestartMode.FINALIZE;
			case CANCEL -> PersistedMessageRuntime.RestartMode.CANCEL;
		};
	}

	private static PersistedMessageRuntime.InstanceStatus toPersistedStatus(
		final InstanceStatus value
	) {
		return switch (value) {
			case ACTIVE -> PersistedMessageRuntime.InstanceStatus.ACTIVE;
			case PAUSED -> PersistedMessageRuntime.InstanceStatus.PAUSED;
			case COMPLETING -> PersistedMessageRuntime.InstanceStatus.COMPLETING;
			case CANCELLING -> PersistedMessageRuntime.InstanceStatus.CANCELLING;
			case COMPLETED, CANCELLED -> throw new IllegalArgumentException(
				"terminal instance cannot be mapped as active persistence"
			);
		};
	}

	private static InstanceStatus toRuntimeStatus(
		final PersistedMessageRuntime.InstanceStatus value
	) {
		return switch (value) {
			case ACTIVE -> InstanceStatus.ACTIVE;
			case PAUSED -> InstanceStatus.PAUSED;
			case COMPLETING -> InstanceStatus.COMPLETING;
			case CANCELLING -> InstanceStatus.CANCELLING;
		};
	}

	private static PersistedMessageRuntime.ClockMode toPersistedClock(final ClockMode value) {
		return value == ClockMode.GAME_TIME
			? PersistedMessageRuntime.ClockMode.GAME_TIME
			: PersistedMessageRuntime.ClockMode.PRESENTATION_TIME;
	}

	private static ClockMode toRuntimeClock(final PersistedMessageRuntime.ClockMode value) {
		return value == PersistedMessageRuntime.ClockMode.GAME_TIME
			? ClockMode.GAME_TIME
			: ClockMode.PRESENTATION_TIME;
	}

	private static PersistedMessageRuntime.CallbackTrigger toPersistedTrigger(
		final CallbackTrigger value
	) {
		return switch (value) {
			case START -> PersistedMessageRuntime.CallbackTrigger.START;
			case TARGET_COMPLETE -> PersistedMessageRuntime.CallbackTrigger.TARGET_COMPLETE;
			case ALL_COMPLETE -> PersistedMessageRuntime.CallbackTrigger.ALL_COMPLETE;
			case INTERRUPT -> PersistedMessageRuntime.CallbackTrigger.INTERRUPT;
		};
	}

	private static CallbackTrigger toRuntimeTrigger(
		final PersistedMessageRuntime.CallbackTrigger value
	) {
		return switch (value) {
			case START -> CallbackTrigger.START;
			case TARGET_COMPLETE -> CallbackTrigger.TARGET_COMPLETE;
			case ALL_COMPLETE -> CallbackTrigger.ALL_COMPLETE;
			case INTERRUPT -> CallbackTrigger.INTERRUPT;
		};
	}

	private static PersistedMessageRuntime.CallbackExecution toPersistedExecution(
		final io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution value
	) {
		return switch (value) {
			case AS_SERVER -> PersistedMessageRuntime.CallbackExecution.AS_SERVER;
			case AS_INVOKER -> PersistedMessageRuntime.CallbackExecution.AS_INVOKER;
			case AS_TARGET -> PersistedMessageRuntime.CallbackExecution.AS_TARGET;
		};
	}

	private static io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution toRuntimeExecution(
		final PersistedMessageRuntime.CallbackExecution value
	) {
		return switch (value) {
			case AS_SERVER -> io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution.AS_SERVER;
			case AS_INVOKER -> io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution.AS_INVOKER;
			case AS_TARGET -> io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution.AS_TARGET;
		};
	}

	private static PersistedMessageRuntime.CallbackLocation toPersistedLocation(
		final CallbackLocation value
	) {
		return value == CallbackLocation.ORIGIN
			? PersistedMessageRuntime.CallbackLocation.ORIGIN
			: PersistedMessageRuntime.CallbackLocation.TARGET;
	}

	private static CallbackLocation toRuntimeLocation(
		final PersistedMessageRuntime.CallbackLocation value
	) {
		return value == PersistedMessageRuntime.CallbackLocation.ORIGIN
			? CallbackLocation.ORIGIN
			: CallbackLocation.TARGET;
	}

	private static PersistedMessageRuntime.CallbackStatus toPersistedCallbackStatus(
		final MessageCallbackLedger.CallbackStatus value
	) {
		return switch (value) {
			case PREPARED -> PersistedMessageRuntime.CallbackStatus.PREPARED;
			case SUCCEEDED -> PersistedMessageRuntime.CallbackStatus.SUCCEEDED;
			case FAILED -> PersistedMessageRuntime.CallbackStatus.FAILED;
			case OUTCOME_UNKNOWN -> PersistedMessageRuntime.CallbackStatus.OUTCOME_UNKNOWN;
		};
	}

	private static MessageCallbackLedger.CallbackStatus toRuntimeCallbackStatus(
		final PersistedMessageRuntime.CallbackStatus value
	) {
		return switch (value) {
			case PREPARED -> throw new IllegalArgumentException("PREPARED callback cannot recover");
			case SUCCEEDED -> MessageCallbackLedger.CallbackStatus.SUCCEEDED;
			case FAILED -> MessageCallbackLedger.CallbackStatus.FAILED;
			case OUTCOME_UNKNOWN -> MessageCallbackLedger.CallbackStatus.OUTCOME_UNKNOWN;
		};
	}

	private static String fingerprint(final DedupeKey value) {
		StringBuilder canonical = new StringBuilder(value.cueId().toString()).append('\n');
		value.targets().stream().sorted().forEach(target -> canonical.append(target).append('\n'));
		new TreeMap<>(value.parameters()).forEach(
			(key, item) -> canonical.append(key.length()).append(':').append(key)
				.append(item.length()).append(':').append(item).append('\n')
		);
		return sha256(canonical.toString());
	}

	private static String fingerprint(final RefreshKey value) {
		return sha256(
			value.cueId() + "\n" + value.parameter().length() + ':' + value.parameter()
				+ value.canonicalValue().length() + ':' + value.canonicalValue()
		);
	}

	private static String sha256(final String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static String parseIssues(
		final MessageDefinitionParser.ParseResult<MessageCueDefinition> result
	) {
		String message = result.issues().stream()
			.limit(3)
			.map(value -> value.code() + "@" + value.path() + ": " + value.message())
			.collect(java.util.stream.Collectors.joining("；"));
		return message.isBlank() ? "unknown parse failure" : message;
	}

	private static String boundedReason(final String value) {
		String reason = Objects.requireNonNull(value, "value").strip();
		if (reason.isBlank()) {
			reason = "消息实例无法安全恢复";
		}
		return reason.length() <= PersistedMessageRuntime.MAX_REASON_LENGTH
			? reason
			: reason.substring(0, PersistedMessageRuntime.MAX_REASON_LENGTH);
	}

	private static String safeMessage(final Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			message = error.getClass().getSimpleName();
		}
		return message.length() <= 1_024 ? message : message.substring(0, 1_024);
	}

	public record CheckpointInput(
		UUID gameInstanceId,
		ClockSample clock,
		RuntimeState runtimeState,
		Map<UUID, FrozenDocument> dependencySnapshots,
		Map<UUID, PersistedMessageRuntime.Origin> origins,
		Map<UUID, List<PersistedMessageRuntime.RecipientState>> recipientStates,
		List<PersistedMessageRuntime.PendingOfflineDelivery> pendingOffline,
		List<PersistedMessageRuntime.Tombstone> tombstones
	) {
		public CheckpointInput {
			Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			Objects.requireNonNull(clock, "clock");
			Objects.requireNonNull(runtimeState, "runtimeState");
			dependencySnapshots = Map.copyOf(
				Objects.requireNonNull(dependencySnapshots, "dependencySnapshots")
			);
			origins = Map.copyOf(Objects.requireNonNull(origins, "origins"));
			Map<UUID, List<PersistedMessageRuntime.RecipientState>> copied = new LinkedHashMap<>();
			Objects.requireNonNull(recipientStates, "recipientStates").forEach(
				(key, value) -> copied.put(key, List.copyOf(value))
			);
			recipientStates = Map.copyOf(copied);
			pendingOffline = List.copyOf(pendingOffline);
			tombstones = List.copyOf(tombstones);
		}
	}

	public record CheckpointMappingResult(
		Optional<PersistedMessageRuntime.Snapshot> snapshot,
		int skippedTransientInstances,
		int terminalInstances,
		String message
	) {
		public CheckpointMappingResult {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			message = Objects.requireNonNull(message, "message").strip();
			if (skippedTransientInstances < 0 || terminalInstances < 0 || message.isBlank()) {
				throw new IllegalArgumentException("invalid checkpoint mapping result");
			}
		}

		public boolean accepted() {
			return this.snapshot.isPresent();
		}

		private static CheckpointMappingResult rejected(final String message) {
			return new CheckpointMappingResult(Optional.empty(), 0, 0, message);
		}
	}

	public record RestoredInvocationEnvironment(
		Optional<PersistedMessageRuntime.Origin> origin,
		Optional<UUID> invokerId,
		Set<UUID> callTargets,
		Map<String, String> arguments
	) {
		public RestoredInvocationEnvironment {
			origin = Objects.requireNonNull(origin, "origin");
			invokerId = Objects.requireNonNull(invokerId, "invokerId");
			callTargets = Set.copyOf(callTargets);
			arguments = Map.copyOf(arguments);
		}
	}

	public record RestoredAction(
		RecoveryActionType type,
		MessageInstance instance,
		RestoredInvocationEnvironment environment,
		List<PersistedMessageRuntime.RecipientState> recipientStates
	) {
		public RestoredAction {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(instance, "instance");
			Objects.requireNonNull(environment, "environment");
			recipientStates = List.copyOf(recipientStates);
		}
	}

	public record RejectedInstance(
		UUID instanceId,
		net.minecraft.resources.Identifier cueId,
		String reason,
		PersistedMessageRuntime.Tombstone tombstone
	) {
		public RejectedInstance {
			Objects.requireNonNull(instanceId, "instanceId");
			Objects.requireNonNull(cueId, "cueId");
			reason = boundedReason(reason);
			Objects.requireNonNull(tombstone, "tombstone");
		}
	}

	public record RecoveryMappingResult(
		Optional<RuntimeState> runtimeState,
		Optional<PersistedMessageRuntime.Snapshot> reconciledSnapshot,
		List<RestoredAction> actions,
		List<RejectedInstance> rejectedInstances,
		String message
	) {
		public RecoveryMappingResult {
			runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
			reconciledSnapshot = Objects.requireNonNull(reconciledSnapshot, "reconciledSnapshot");
			actions = List.copyOf(actions);
			rejectedInstances = List.copyOf(rejectedInstances);
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank()) {
				throw new IllegalArgumentException("recovery mapping message cannot be blank");
			}
		}

		public boolean accepted() {
			return this.runtimeState.isPresent() && this.reconciledSnapshot.isPresent();
		}

		private static RecoveryMappingResult rejected(final String message) {
			return new RecoveryMappingResult(
				Optional.empty(),
				Optional.empty(),
				List.of(),
				List.of(),
				message
			);
		}
	}

	private record MappedInstance(
		MessageInstance instance,
		RestoredInvocationEnvironment environment
	) {
	}

	private record Candidate(
		PersistedMessageRuntime.Instance persisted,
		RestoredAction action
	) {
	}
}
