package io.github.zcpu954861.pixeltzzpro.server.message;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.MessageCatalog;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDependencySnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackExecution;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackLocation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackEntry;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.CallbackStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageCallbackLedger.Ledger;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.DedupeKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InstanceStatus;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InvocationContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.OccurrenceKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RefreshKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.RuntimeState;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryAction;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryActionType;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePersistenceAuthority.RecoveryPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Runnable live-to-stable mapping, origin compatibility, and fail-closed recovery check. */
public final class MessageRuntimePersistenceMapperSelfCheck {
	private static final Identifier CUE_ID = Identifier.parse("test:persistent_notice");
	private static final Identifier TEST_GAME = Identifier.parse("test:game");
	private static final UUID GAME_INSTANCE = uuid("game-instance");
	private static final UUID MESSAGE_INSTANCE = uuid("message-instance");
	private static final UUID RECIPIENT = uuid("recipient");
	private static final FrozenDocument DEPENDENCY_SNAPSHOT = FrozenDocument.of(
		"{\"fixture\":\"message-dependencies\"}"
	);
	private static final String DEFINITION_FINGERPRINT = DEPENDENCY_SNAPSHOT.sha256();
	private static final PersistedMessageRuntime.Origin ORIGIN =
		new PersistedMessageRuntime.Origin(
			Identifier.parse("minecraft:overworld"),
			12.25D,
			64.0D,
			-7.5D
		);

	private MessageRuntimePersistenceMapperSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Fixture fixture = fixture();
		checkOriginCodecCompatibility(fixture);
		checkRecipientStateCodec(fixture);
		PersistedMessageRuntime.Snapshot checkpoint = checkRoundTripMapping(fixture);
		checkDefinitionFingerprintAndRecoveryPolicy(checkpoint);
		checkDeliveryBoundaryDependencyProof();
		checkRecoveryMapping(checkpoint);
		checkTerminalRestartIgnoresLiveConcurrencyAddresses(checkpoint);
		checkMissingOriginFailsClosed(checkpoint);
		checkMalformedCueFailsClosed(checkpoint);
		checkMalformedRecipientStateFailsClosed(checkpoint);
		checkPreparedAndUnmappableCallbacksFailClosed(checkpoint);
		System.out.println("MESSAGE_RUNTIME_PERSISTENCE_MAPPER_SELF_CHECK=PASS");
	}

	private static void checkOriginCodecCompatibility(final Fixture fixture) {
		PersistedMessageRuntime.InvocationContext context =
			new PersistedMessageRuntime.InvocationContext(
				fixture.instance().context().invokerId(),
				List.copyOf(fixture.instance().context().callTargets()),
				fixture.instance().context().gameId(),
				fixture.instance().context().phaseId(),
				fixture.instance().context().taskId(),
				fixture.instance().context().arguments(),
				fixture.instance().context().bypassContext(),
				fixture.instance().context().externallyTimed(),
				Optional.of(ORIGIN)
			);
		JsonObject encoded = PersistedMessageRuntime.InvocationContext.CODEC
			.encodeStart(JsonOps.INSTANCE, context)
			.getOrThrow()
			.getAsJsonObject();
		check(
			PersistedMessageRuntime.InvocationContext.CODEC.parse(JsonOps.INSTANCE, encoded)
				.getOrThrow()
				.equals(context),
			"invocation origin must round-trip exactly"
		);
		encoded.remove("origin");
		encoded.remove("bypass_context");
		PersistedMessageRuntime.InvocationContext legacy =
			PersistedMessageRuntime.InvocationContext.CODEC.parse(JsonOps.INSTANCE, encoded)
				.getOrThrow();
		check(
			legacy.origin().isEmpty() && !legacy.bypassContext(),
			"older invocation contexts without origin or bypass must decode with safe defaults"
		);
	}

	private static PersistedMessageRuntime.Snapshot checkRoundTripMapping(final Fixture fixture) {
		PersistedMessageRuntime.RecipientState recipient =
			MessageRecipientStateCodec.encode(
				fixture.instance().cue(),
				recipientSnapshot(fixture)
			);
		var mapped = MessageRuntimePersistenceMapper.checkpoint(
			new MessageRuntimePersistenceMapper.CheckpointInput(
				GAME_INSTANCE,
				fixture.checkpointClock(),
				fixture.runtime(),
				Map.of(MESSAGE_INSTANCE, DEPENDENCY_SNAPSHOT),
				Map.of(MESSAGE_INSTANCE, ORIGIN),
				Map.of(MESSAGE_INSTANCE, List.of(recipient)),
				List.of(),
				List.of()
			)
		);
		check(mapped.accepted(), "live runtime must map to a stable snapshot: " + mapped.message());
		PersistedMessageRuntime.Snapshot snapshot = mapped.snapshot().orElseThrow();
		check(
			snapshot.instances().size() == 1
				&& snapshot.instances().getFirst().context().origin().equals(Optional.of(ORIGIN))
				&& snapshot.instances().getFirst().frozenCue().definitionFingerprint()
					.equals(Optional.of(DEFINITION_FINGERPRINT))
				&& snapshot.instances().getFirst().frozenCue().dependencySnapshot()
					.equals(Optional.of(DEPENDENCY_SNAPSHOT))
				&& snapshot.instances().getFirst().context().bypassContext()
				&& snapshot.instances().getFirst().recipients().equals(List.of(recipient))
				&& snapshot.callbackLedger().entries().getFirst().status()
					== PersistedMessageRuntime.CallbackStatus.PREPARED,
			"checkpoint must preserve dependency fingerprint, origin, context bypass, recipient locks and callback preparation"
		);
		var nbt = PersistedMessageRuntime.Snapshot.CODEC
			.encodeStart(NbtOps.INSTANCE, snapshot)
			.getOrThrow();
		check(
			PersistedMessageRuntime.Snapshot.CODEC.parse(NbtOps.INSTANCE, nbt)
				.getOrThrow()
				.equals(snapshot),
			"mapped snapshot with origin must round-trip through NBT"
		);
		var missingFingerprint = MessageRuntimePersistenceMapper.checkpoint(
			new MessageRuntimePersistenceMapper.CheckpointInput(
				GAME_INSTANCE,
				fixture.checkpointClock(),
				fixture.runtime(),
				Map.of(),
				Map.of(MESSAGE_INSTANCE, ORIGIN),
				Map.of(MESSAGE_INSTANCE, List.of(recipient)),
				List.of(),
				List.of()
			)
		);
		check(
			!missingFingerprint.accepted() && missingFingerprint.message().contains("依赖快照"),
			"persistent checkpoint without a frozen dependency snapshot must fail closed"
		);
		return snapshot;
	}

	private static void checkDefinitionFingerprintAndRecoveryPolicy(
		final PersistedMessageRuntime.Snapshot checkpoint
	) {
		String emptyFingerprint = DefinitionSnapshot.empty().stableContentFingerprint();
		check(
			emptyFingerprint.matches("[0-9a-f]{64}")
				&& emptyFingerprint.equals(
					DefinitionSnapshot.empty().withGeneration(99L).stableContentFingerprint()
				)
				&& !emptyFingerprint.equals(
					DefinitionSnapshot.empty()
						.withPredicateDocuments(Map.of(
							Identifier.parse("test:dependency"),
							"{\"condition\":\"changed\"}"
						))
						.stableContentFingerprint()
				),
			"definition fingerprint must ignore JVM generation and change with dependency content"
		);

		var matching = MessageRecoveryDefinitionPolicy.reconcile(
			checkpoint,
			DEFINITION_FINGERPRINT
		);
		check(
			matching.snapshot().equals(checkpoint)
				&& matching.finalizedInstanceIds().isEmpty()
				&& matching.cancelledInstanceIds().isEmpty(),
			"matching frozen dependencies must preserve restart=continue"
		);

		String changedFingerprint = "1".repeat(64);
		var cancelled = MessageRecoveryDefinitionPolicy.reconcile(
			checkpoint,
			changedFingerprint
		);
		check(
			cancelled.cancelledInstanceIds().equals(Set.of(MESSAGE_INSTANCE))
				&& cancelled.snapshot().instances().getFirst().restart()
					== PersistedMessageRuntime.RestartMode.CANCEL,
			"changed dependencies without a frozen fallback must cancel instead of reading new definitions"
		);

		PersistedMessageRuntime.Instance source = checkpoint.instances().getFirst();
		MessageCueDefinition fallbackCue = parseFallbackCue();
		PersistedMessageRuntime.Instance withFallback = copy(
			source,
			new PersistedMessageRuntime.FrozenCue(
				source.frozenCue().definitionGeneration(),
				FrozenDocument.of(fallbackCue.canonicalDocument()),
				Optional.of(DEFINITION_FINGERPRINT)
			),
			source.context()
		);
		var finalized = MessageRecoveryDefinitionPolicy.reconcile(
			replaceInstance(checkpoint, withFallback),
			changedFingerprint
		);
		check(
			finalized.finalizedInstanceIds().equals(Set.of(MESSAGE_INSTANCE))
				&& finalized.snapshot().instances().getFirst().restart()
					== PersistedMessageRuntime.RestartMode.FINALIZE,
			"changed dependencies with a frozen fallback must become durable static-final work"
		);

		PersistedMessageRuntime.Instance legacy = copy(
			source,
			new PersistedMessageRuntime.FrozenCue(
				source.frozenCue().definitionGeneration(),
				source.frozenCue().canonicalDocument()
			),
			source.context()
		);
		var legacyResult = MessageRecoveryDefinitionPolicy.reconcile(
			replaceInstance(checkpoint, legacy),
			DEFINITION_FINGERPRINT
		);
		check(
			legacyResult.cancelledInstanceIds().equals(Set.of(MESSAGE_INSTANCE)),
			"legacy continue checkpoint without a dependency fingerprint must fail closed"
		);
	}

	private static void checkDeliveryBoundaryDependencyProof() {
		MessageCueDefinition cue = parseFallbackCue();
		DefinitionSnapshot matching = snapshotWithCue(cue, 41L);
		var frozen = MessageDependencySnapshotCompiler.freeze(matching, cue);
		check(frozen.success(), "recovered-final dependency fixture must freeze: " + frozen.message());
		PersistedMessageRuntime.FrozenCue evidence = new PersistedMessageRuntime.FrozenCue(
			matching.generation(),
			FrozenDocument.of(cue.canonicalDocument()),
			Optional.of(matching.stableContentFingerprint()),
			frozen.snapshot()
		);
		check(
			MessageRecoveryDefinitionPolicy.dependenciesMatch(
				matching.withGeneration(42L),
				cue.id(),
				evidence
			),
			"delivery-boundary dependency proof must ignore JVM generation for unchanged definitions"
		);

		MessageCueDefinition changedCue = MessageDefinitionParser.parseMessageCue(
			cue.id(),
			cue.canonicalDocument().replace("冻结最终内容", "已变更最终内容")
		).value().orElseThrow();
		check(
			!MessageRecoveryDefinitionPolicy.dependenciesMatch(
				snapshotWithCue(changedCue, 43L),
				cue.id(),
				evidence
			),
			"a data-pack reload that changes the frozen cue must revoke recovered-final delivery"
		);
		MessageCueDefinition differentPersistedCue = MessageDefinitionParser.parseMessageCue(
			cue.id(),
			cue.canonicalDocument().replace("冻结正文", "被拼接的冻结正文")
		).value().orElseThrow();
		check(
			!MessageRecoveryDefinitionPolicy.dependenciesMatch(
				matching,
				cue.id(),
				new PersistedMessageRuntime.FrozenCue(
					matching.generation(),
					FrozenDocument.of(differentPersistedCue.canonicalDocument()),
					Optional.of(matching.stableContentFingerprint()),
					frozen.snapshot()
				)
			),
			"a frozen cue document assembled with another dependency snapshot must fail closed"
		);
	}

	private static void checkRecipientStateCodec(final Fixture fixture) {
		MessageRecipientStateCodec.RecipientStateSnapshot source = recipientSnapshot(fixture);
		PersistedMessageRuntime.RecipientState encoded = MessageRecipientStateCodec.encode(
			fixture.instance().cue(),
			source
		);
		check(
			MessageRecipientStateCodec.decode(fixture.instance().cue(), encoded).equals(source),
			"recipient checkpoint fields and all three condition scopes must round-trip"
		);
		check(
			encoded.conditionDecisions().stream().map(
				PersistedMessageRuntime.ConditionDecision::decisionKey
			).collect(java.util.stream.Collectors.toSet()).equals(
				Set.of("v1|c|0", "v1|f|callback|0", "v1|n|callback|0|0")
			),
			"recipient decisions must use deterministic versioned keys"
		);

		PersistedMessageRuntime.RecipientState wrongSentinel = new PersistedMessageRuntime.RecipientState(
			encoded.recipientId(),
			encoded.createdElapsedNanos(),
			encoded.perPlayerAnchorElapsedNanos(),
			List.of(new PersistedMessageRuntime.Occurrence(
				"callback",
				0,
				PersistedMessageRuntime.CallbackTrigger.INTERRUPT,
				Optional.empty()
			)),
			encoded.decidedOccurrences(),
			encoded.lockedFields(),
			encoded.conditionDecisions(),
			encoded.completionAtNanos(),
			encoded.completionCommitted()
		);
		checkThrows(
			() -> MessageRecipientStateCodec.decode(fixture.instance().cue(), wrongSentinel),
			"recipient occurrence callback sentinel must fail closed"
		);

		PersistedMessageRuntime.RecipientState unknownCondition = new PersistedMessageRuntime.RecipientState(
			encoded.recipientId(),
			encoded.createdElapsedNanos(),
			encoded.perPlayerAnchorElapsedNanos(),
			encoded.projectedOccurrences(),
			encoded.decidedOccurrences(),
			encoded.lockedFields(),
			List.of(new PersistedMessageRuntime.ConditionDecision(
				"v1|n|callback|0|99",
				true
			)),
			encoded.completionAtNanos(),
			encoded.completionCommitted()
		);
		checkThrows(
			() -> MessageRecipientStateCodec.decode(fixture.instance().cue(), unknownCondition),
			"condition ordinal outside the frozen cue must fail closed"
		);
	}

	private static void checkRecoveryMapping(final PersistedMessageRuntime.Snapshot checkpoint) {
		RecoveryPlan plan = MessageRuntimePersistenceAuthority.recover(
			checkpoint,
			GAME_INSTANCE,
			40L
		);
		check(plan.accepted(), "checkpoint must produce a recovery plan");
		ClockSample recoveryClock = new ClockSample(40L, 2_000_000_000L);
		var restored = MessageRuntimePersistenceMapper.recover(plan, recoveryClock);
		check(restored.accepted(), "valid frozen instance must recover: " + restored.message());
		RuntimeState state = restored.runtimeState().orElseThrow();
		MessageInstance instance = state.instances().get(MESSAGE_INSTANCE);
		check(
			instance != null
				&& instance.context().bypassContext()
				&& instance.cue().canonicalDocument().equals(
					checkpoint.instances().getFirst().frozenCue().canonicalDocument().normalizedJson()
				)
				&& instance.elapsedAt(recoveryClock)
					== checkpoint.instances().getFirst().progress().standardElapsedNanos()
				&& state.activeDedupe().get(instance.dedupeKey()).equals(MESSAGE_INSTANCE)
				&& state.refreshIndex().get(instance.refreshKey().orElseThrow()).equals(MESSAGE_INSTANCE),
			"recovery must rebuild cue, context bypass, elapsed progress, dedupe and refresh indexes"
		);
		check(
			state.callbackLedger().entries().size() == 1
				&& state.callbackLedger().entries().getFirst().status()
					== CallbackStatus.OUTCOME_UNKNOWN,
			"crash-left PREPARED callback must remain OUTCOME_UNKNOWN after mapping"
		);
		check(
			restored.actions().size() == 1
				&& restored.actions().getFirst().environment().origin().equals(Optional.of(ORIGIN))
				&& restored.actions().getFirst().recipientStates().size() == 1
				&& restored.rejectedInstances().isEmpty(),
			"stream/environment recovery data must retain origin and recipient state"
		);

		long coldStartGeneration = 77L;
		var rebound = MessageRuntimePersistenceMapper.recover(
			plan,
			recoveryClock,
			coldStartGeneration
		);
		check(
			rebound.accepted()
				&& rebound.runtimeState().orElseThrow().instances().get(MESSAGE_INSTANCE)
					.context().definitionGeneration() == coldStartGeneration
				&& checkpoint.instances().getFirst().frozenCue().definitionGeneration()
					!= coldStartGeneration,
			"cold-start recovery must rebind the live instance to the generation accepted by the new JVM"
		);
	}

	private static void checkTerminalRestartIgnoresLiveConcurrencyAddresses(
		final PersistedMessageRuntime.Snapshot checkpoint
	) {
		PersistedMessageRuntime.Instance source = checkpoint.instances().getFirst();
		Optional<String> mismatchedDedupe = Optional.of("0".repeat(64));
		Optional<String> mismatchedRefresh = Optional.of("1".repeat(64));
		PersistedMessageRuntime.Instance continuing = copy(
			source,
			PersistedMessageRuntime.RestartMode.CONTINUE,
			mismatchedDedupe,
			mismatchedRefresh
		);
		var rejectedContinue = MessageRuntimePersistenceMapper.recover(
			MessageRuntimePersistenceAuthority.recover(
				replaceInstance(checkpoint, continuing),
				GAME_INSTANCE,
				44L
			),
			new ClockSample(44L, 2_200_000_000L)
		);
		check(
			rejectedContinue.accepted()
				&& rejectedContinue.rejectedInstances().size() == 1
				&& rejectedContinue.rejectedInstances().getFirst().reason()
					.contains("去重或刷新地址"),
			"restart=continue must still fail closed when its live concurrency address is corrupt"
		);

		PersistedMessageRuntime.Instance finalizing = copy(
			source,
			PersistedMessageRuntime.RestartMode.FINALIZE,
			mismatchedDedupe,
			mismatchedRefresh
		);
		RecoveryPlan finalPlan = MessageRuntimePersistenceAuthority.recover(
			replaceInstance(checkpoint, finalizing),
			GAME_INSTANCE,
			45L
		);
		var finalized = MessageRuntimePersistenceMapper.recover(
			finalPlan,
			new ClockSample(45L, 2_250_000_000L)
		);
		check(
			finalized.accepted()
				&& finalized.rejectedInstances().isEmpty()
				&& finalized.actions().size() == 1
				&& finalized.actions().getFirst().type()
					== RecoveryActionType.FINALIZE_STATIC
				&& finalized.reconciledSnapshot().orElseThrow().instances().stream()
					.anyMatch(value -> value.instanceId().equals(MESSAGE_INSTANCE)),
			"restart=finalize must not rebuild or validate indexes it will never enter"
		);

		PersistedMessageRuntime.Instance cancelling = copy(
			source,
			PersistedMessageRuntime.RestartMode.CANCEL,
			mismatchedDedupe,
			mismatchedRefresh
		);
		RecoveryPlan cancelPlan = MessageRuntimePersistenceAuthority.recover(
			replaceInstance(checkpoint, cancelling),
			GAME_INSTANCE,
			46L
		);
		PersistedMessageRuntime.Tombstone authoritativeCancel = cancelPlan.nextSnapshot()
			.orElseThrow()
			.tombstones()
			.stream()
			.filter(value -> value.instanceId().equals(MESSAGE_INSTANCE))
			.findFirst()
			.orElseThrow();
		var cancelled = MessageRuntimePersistenceMapper.recover(
			cancelPlan,
			new ClockSample(46L, 2_300_000_000L)
		);
		check(
			cancelled.accepted()
				&& cancelled.actions().isEmpty()
				&& cancelled.rejectedInstances().isEmpty()
				&& cancelled.reconciledSnapshot().orElseThrow().instances().isEmpty()
				&& cancelled.reconciledSnapshot().orElseThrow().tombstones()
					.contains(authoritativeCancel)
				&& authoritativeCancel.reason().equals(
					"服务器重启：按 restart=cancel 取消演出"
				),
			"restart=cancel must preserve the authority tombstone instead of remapping the instance"
		);
	}

	private static void checkMissingOriginFailsClosed(
		final PersistedMessageRuntime.Snapshot checkpoint
	) {
		PersistedMessageRuntime.Instance source = checkpoint.instances().getFirst();
		PersistedMessageRuntime.InvocationContext oldContext =
			new PersistedMessageRuntime.InvocationContext(
				source.context().invokerId(),
				source.context().callTargets(),
				source.context().gameId(),
				source.context().phaseId(),
				source.context().taskId(),
				source.context().arguments(),
				source.context().bypassContext(),
				source.context().externallyTimed(),
				Optional.empty()
			);
		PersistedMessageRuntime.Snapshot missingOrigin = replaceInstance(
			checkpoint,
			copy(source, source.frozenCue(), oldContext)
		);
		var mapped = MessageRuntimePersistenceMapper.recover(
			MessageRuntimePersistenceAuthority.recover(missingOrigin, GAME_INSTANCE, 50L),
			new ClockSample(50L, 2_500_000_000L)
		);
		check(
			mapped.accepted()
				&& mapped.runtimeState().orElseThrow().instances().isEmpty()
				&& mapped.rejectedInstances().size() == 1
				&& mapped.reconciledSnapshot().orElseThrow().instances().isEmpty()
				&& mapped.reconciledSnapshot().orElseThrow().tombstones().stream()
					.anyMatch(value -> value.instanceId().equals(MESSAGE_INSTANCE)),
			"world-attached cue without a stable origin must reject and tombstone the whole instance"
		);
	}

	private static void checkMalformedCueFailsClosed(
		final PersistedMessageRuntime.Snapshot checkpoint
	) {
		PersistedMessageRuntime.Instance source = checkpoint.instances().getFirst();
		PersistedMessageRuntime.Instance malformed = copy(
			source,
			new PersistedMessageRuntime.FrozenCue(
				source.frozenCue().definitionGeneration(),
				FrozenDocument.of("{}")
			),
			source.context()
		);
		var mapped = MessageRuntimePersistenceMapper.recover(
			MessageRuntimePersistenceAuthority.recover(
				replaceInstance(checkpoint, malformed),
				GAME_INSTANCE,
				60L
			),
			new ClockSample(60L, 3_000_000_000L)
		);
		check(
			mapped.accepted()
				&& mapped.rejectedInstances().size() == 1
				&& mapped.rejectedInstances().getFirst().reason().contains("cue"),
			"unparseable frozen cue must reject only its complete instance"
		);
	}

	private static void checkMalformedRecipientStateFailsClosed(
		final PersistedMessageRuntime.Snapshot checkpoint
	) {
		PersistedMessageRuntime.Instance source = checkpoint.instances().getFirst();
		PersistedMessageRuntime.RecipientState recipient = source.recipients().getFirst();
		PersistedMessageRuntime.RecipientState malformedRecipient =
			new PersistedMessageRuntime.RecipientState(
				recipient.recipientId(),
				recipient.createdElapsedNanos(),
				recipient.perPlayerAnchorElapsedNanos(),
				recipient.projectedOccurrences(),
				recipient.decidedOccurrences(),
				recipient.lockedFields(),
				List.of(new PersistedMessageRuntime.ConditionDecision(
					"v1|n|callback|0|99",
					true
				)),
				recipient.completionAtNanos(),
				recipient.completionCommitted()
			);
		PersistedMessageRuntime.Instance malformed = copy(
			source,
			source.frozenCue(),
			source.context(),
			List.of(malformedRecipient)
		);
		var mapped = MessageRuntimePersistenceMapper.recover(
			MessageRuntimePersistenceAuthority.recover(
				replaceInstance(checkpoint, malformed),
				GAME_INSTANCE,
				65L
			),
			new ClockSample(65L, 3_250_000_000L)
		);
		check(
			mapped.accepted()
				&& mapped.rejectedInstances().size() == 1
				&& mapped.runtimeState().orElseThrow().instances().isEmpty(),
			"unmappable recipient decisions must reject and tombstone the complete instance"
		);
	}

	private static void checkPreparedAndUnmappableCallbacksFailClosed(
		final PersistedMessageRuntime.Snapshot checkpoint
	) {
		RecoveryPlan bypassedAuthority = new RecoveryPlan(
			Optional.of(checkpoint),
			List.of(
				new RecoveryAction(
					RecoveryActionType.RESTORE_CONTINUE,
					checkpoint.instances().getFirst(),
					"self-check bypass"
				)
			),
			List.of(),
			0,
			false,
			"self-check"
		);
		var prepared = MessageRuntimePersistenceMapper.recover(
			bypassedAuthority,
			new ClockSample(70L, 3_500_000_000L)
		);
		check(
			prepared.accepted()
				&& prepared.rejectedInstances().size() == 1
				&& prepared.runtimeState().orElseThrow().callbackLedger().entries().isEmpty(),
			"mapper must never turn PREPARED back into an executable callback"
		);

		PersistedMessageRuntime.CallbackEntry original = checkpoint.callbackLedger()
			.entries()
			.getFirst();
		PersistedMessageRuntime.CallbackEntry unknownKey =
			new PersistedMessageRuntime.CallbackEntry(
				new PersistedMessageRuntime.CallbackKey(
					MESSAGE_INSTANCE,
					0L,
					"missing_callback",
					0,
					PersistedMessageRuntime.CallbackTrigger.START,
					Optional.empty()
				),
				original.functionId(),
				original.execution(),
				original.location(),
				PersistedMessageRuntime.CallbackStatus.OUTCOME_UNKNOWN,
				1,
				original.updatedAtGameTick(),
				Optional.of("unknown after restart")
			);
		PersistedMessageRuntime.Snapshot badCallback = new PersistedMessageRuntime.Snapshot(
			checkpoint.gameInstanceId(),
			checkpoint.snapshotGameTick(),
			checkpoint.runtimeRevision(),
			checkpoint.instances(),
			checkpoint.pendingOffline(),
			checkpoint.tombstones(),
			new PersistedMessageRuntime.CallbackLedger(
				checkpoint.callbackLedger().capacity(),
				List.of(unknownKey)
			)
		);
		RecoveryPlan badKeyPlan = new RecoveryPlan(
			Optional.of(badCallback),
			bypassedAuthority.actions(),
			List.of(),
			0,
			false,
			"self-check"
		);
		var unmappable = MessageRuntimePersistenceMapper.recover(
			badKeyPlan,
			new ClockSample(80L, 4_000_000_000L)
		);
		check(
			unmappable.accepted() && unmappable.rejectedInstances().size() == 1,
			"callback key that cannot map to a frozen cue occurrence must reject the instance"
		);
	}

	private static MessageRecipientStateCodec.RecipientStateSnapshot recipientSnapshot(
		final Fixture fixture
	) {
		MessageRecipientStateCodec.RecipientOccurrence occurrence =
			new MessageRecipientStateCodec.RecipientOccurrence("callback", 0);
		var condition = fixture.instance().cue().nodes().getFirst().header().when().orElseThrow();
		return new MessageRecipientStateCodec.RecipientStateSnapshot(
			RECIPIENT,
			0L,
			Optional.of(0L),
			Set.of(occurrence),
			Set.of(occurrence),
			Map.of(
				0,
				new io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent(
					"{\"text\":\"locked\"}"
				)
			),
			Map.of(condition, true),
			Map.of(occurrence, true),
			Map.of(
				new MessageRecipientStateCodec.NodeConditionAddress(
					occurrence,
					condition
				),
				true
			),
			Optional.empty(),
			false
		);
	}

	private static Fixture fixture() {
		MessageCueDefinition cue = parseCue();
		Map<String, String> parameters = Map.of("key", "alpha");
		DedupeKey dedupe = new DedupeKey(CUE_ID, Set.of(RECIPIENT), parameters);
		RefreshKey refresh = new RefreshKey(CUE_ID, "key", "alpha");
		InvocationContext context = new InvocationContext(
			MESSAGE_INSTANCE,
			true,
			Optional.empty(),
			Set.of(RECIPIENT),
			Optional.of(Identifier.parse("test:game")),
			Optional.of(Identifier.parse("test:phase")),
			Optional.of(Identifier.parse("test:task")),
			Map.of("key", "alpha"),
			3L,
			true,
			false
		);
		OccurrenceKey occurrence = new OccurrenceKey(
			"callback",
			0,
			CallbackTrigger.START,
			Optional.empty()
		);
		MessageInstance instance = new MessageInstance(
			MESSAGE_INSTANCE,
			cue,
			context,
			parameters,
			Map.of("callback", new TimeSpan(0L)),
			Set.of(RECIPIENT),
			Map.of("callback", Set.of(RECIPIENT)),
			dedupe,
			Optional.of(refresh),
			ClockMode.GAME_TIME,
			0L,
			InstanceStatus.ACTIVE,
			Optional.empty(),
			900_000_000L,
			50_000_000L,
			Set.of(occurrence),
			Set.of(),
			Map.of(),
			false,
			4L
		);
		CallbackEntry callback = new CallbackEntry(
			new CallbackKey(
				MESSAGE_INSTANCE,
				0L,
				"callback",
				0,
				CallbackTrigger.START,
				Optional.empty()
			),
			Identifier.parse("test:callback"),
			CallbackExecution.AS_SERVER,
			CallbackLocation.ORIGIN,
			CallbackStatus.PREPARED,
			1,
			20L,
			Optional.empty()
		);
		RuntimeState runtime = new RuntimeState(
			7L,
			Map.of(MESSAGE_INSTANCE, instance),
			List.of(),
			Map.of(dedupe, MESSAGE_INSTANCE),
			Map.of(refresh, MESSAGE_INSTANCE),
			Map.of(),
			new Ledger(16, List.of(callback))
		);
		return new Fixture(instance, runtime, new ClockSample(20L, 1_000_000_000L));
	}

	private static MessageCueDefinition parseCue() {
		var parsed = MessageDefinitionParser.parseMessageCue(
			CUE_ID,
			"""
			{
			  "format_version": 1,
			  "policies": {
			    "timing": {"clock": "game_time"},
			    "audience": {"target": {"source": "call_targets", "online_only": false}},
			    "concurrency": {
			      "duplicate": "ignore_while_active",
			      "dedupe_targets": true,
			      "dedupe_parameters": ["key"],
			      "refresh_key": "key"
			    },
			    "lifecycle": {"restart": "continue", "attachment": "world"}
			  },
			  "parameters": {
			    "key": {"type": "string", "required": true, "dedupe": true}
			  },
			  "nodes": [
			    {
			      "id": "callback",
			      "type": "callback",
			      "schedule": {"type": "cue_start", "offset": "0t"},
			      "when": {
			        "evaluate_at": "cue_start",
			        "predicate": "test:callback_allowed"
			      },
			      "function": "test:callback",
			      "execution": "as_server",
			      "location": "origin"
			    }
			  ]
			}
			"""
		);
		check(parsed.valid(), "mapper cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static MessageCueDefinition parseFallbackCue() {
		var parsed = MessageDefinitionParser.parseMessageCue(
			CUE_ID,
			"""
			{
			  "format_version": 1,
			  "game": "test:game",
			  "nodes": [
			    {
			      "id": "notice",
			      "type": "text",
			      "channel": "chat",
			      "content": {
			        "text": {"parts": [{"component": {"text": "冻结正文"}}]}
			      }
			    }
			  ],
			  "static_fallback": {
			    "channel": "chat",
			    "component": {"text": "冻结最终内容"},
			    "fallback_satisfies_required": true
			  }
			}
			"""
		);
		check(parsed.valid(), "fallback cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static DefinitionSnapshot snapshotWithCue(
		final MessageCueDefinition cue,
		final long generation
	) {
		DefinitionSnapshot empty = DefinitionSnapshot.empty();
		GameDefinition game = new GameDefinition(
			TEST_GAME,
			1,
			1,
			new RichText("{\"text\":\"Test game\"}", "Test game"),
			Identifier.parse("test:phase"),
			Identifier.parse("test:role"),
			Identifier.parse("test:life_state"),
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
		DocumentKey gameKey = new DocumentKey(DefinitionType.GAME, TEST_GAME);
		DocumentKey cueKey = new DocumentKey(DefinitionType.MESSAGE_CUE, cue.id());
		String gameDocument = "{\"format_version\":1,\"id\":\"test:game\"}";
		Map<DocumentKey, SourceDocument> sources = Map.of(
			gameKey,
			sourceDocument(gameKey, gameDocument),
			cueKey,
			sourceDocument(cueKey, cue.canonicalDocument())
		);
		return new DefinitionSnapshot(
			generation,
			Map.of(TEST_GAME, game),
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
			new MessageCatalog(Map.of(), Map.of(cue.id(), cue), Map.of(), Set.of()),
			sources,
			Set.of(),
			Set.of(),
			Map.of()
		);
	}

	private static SourceDocument sourceDocument(
		final DocumentKey key,
		final String canonicalJson
	) {
		FrozenDocument frozen = FrozenDocument.of(canonicalJson);
		return new SourceDocument(
			key,
			Identifier.fromNamespaceAndPath(
				key.id().getNamespace(),
				"pixel_tzz_pro/" + key.type().directory() + "/" + key.id().getPath() + ".json"
			),
			"message-runtime-persistence-self-check",
			frozen.normalizedJson(),
			frozen.sha256()
		);
	}

	private static PersistedMessageRuntime.Snapshot replaceInstance(
		final PersistedMessageRuntime.Snapshot source,
		final PersistedMessageRuntime.Instance replacement
	) {
		return new PersistedMessageRuntime.Snapshot(
			source.gameInstanceId(),
			source.snapshotGameTick(),
			source.runtimeRevision(),
			List.of(replacement),
			source.pendingOffline(),
			source.tombstones(),
			source.callbackLedger()
		);
	}

	private static PersistedMessageRuntime.Instance copy(
		final PersistedMessageRuntime.Instance source,
		final PersistedMessageRuntime.FrozenCue frozenCue,
		final PersistedMessageRuntime.InvocationContext context
	) {
		return copy(source, frozenCue, context, source.recipients());
	}

	private static PersistedMessageRuntime.Instance copy(
		final PersistedMessageRuntime.Instance source,
		final PersistedMessageRuntime.FrozenCue frozenCue,
		final PersistedMessageRuntime.InvocationContext context,
		final List<PersistedMessageRuntime.RecipientState> recipients
	) {
		return new PersistedMessageRuntime.Instance(
			source.instanceId(),
			source.cueId(),
			source.restart(),
			frozenCue,
			context,
			source.canonicalParameters(),
			source.audience(),
			source.timing(),
			source.progress(),
			recipients,
			source.dedupeKey(),
			source.refreshKey(),
			source.updatedAtGameTick()
		);
	}

	private static PersistedMessageRuntime.Instance copy(
		final PersistedMessageRuntime.Instance source,
		final PersistedMessageRuntime.RestartMode restart,
		final Optional<String> dedupeKey,
		final Optional<String> refreshKey
	) {
		return new PersistedMessageRuntime.Instance(
			source.instanceId(),
			source.cueId(),
			restart,
			source.frozenCue(),
			source.context(),
			source.canonicalParameters(),
			source.audience(),
			source.timing(),
			source.progress(),
			source.recipients(),
			dedupeKey,
			refreshKey,
			source.updatedAtGameTick()
		);
	}

	private static UUID uuid(final String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void checkThrows(final Runnable action, final String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private record Fixture(
		MessageInstance instance,
		RuntimeState runtime,
		ClockSample checkpointClock
	) {
	}
}
