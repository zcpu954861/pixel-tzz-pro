package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDependencySnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.RestartMode;
import io.github.zcpu954861.pixeltzzpro.state.PersistedMessageRuntime.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;

/**
 * Cold-start authority boundary for dependencies outside a persisted cue document.
 *
 * <p>A JVM-local generation number cannot prove that the current data pack is identical to the
 * one that created a persistent message. Current checkpoints carry an exact bounded dependency
 * manifest; the legacy whole-registry fingerprint is accepted only for older checkpoints that do
 * not have one. Missing, changed, or newly reachable dependencies convert the instance to a
 * durable frozen static final when the cue supplies one; otherwise the instance is cancelled.
 */
public final class MessageRecoveryDefinitionPolicy {
	private MessageRecoveryDefinitionPolicy() {
	}

	public static Reconciliation reconcile(
		final Snapshot persisted,
		final String activeDefinitionFingerprint
	) {
		Objects.requireNonNull(persisted, "persisted");
		String active = Objects.requireNonNull(
			activeDefinitionFingerprint,
			"activeDefinitionFingerprint"
		).strip();
		if (!active.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
				"active definition fingerprint must be a lowercase SHA-256 digest"
			);
		}
		return reconcileValidated(
			persisted,
			instance -> instance.frozenCue().definitionFingerprint().filter(active::equals)
				.isPresent()
		);
	}

	/**
	 * Reconciles current V3B checkpoints through their exact, bounded dependency manifests.
	 * Fingerprint-only checkpoints retain the older whole-registry comparison as a compatibility
	 * path; every newly written checkpoint carries a manifest.
	 */
	public static Reconciliation reconcile(
		final Snapshot persisted,
		final DefinitionSnapshot activeDefinitions
	) {
		Objects.requireNonNull(activeDefinitions, "activeDefinitions");
		return reconcileValidated(
			persisted,
			instance -> dependenciesMatch(
				activeDefinitions,
				instance.cueId(),
				instance.frozenCue()
			)
		);
	}

	/**
	 * Rechecks one frozen recovery unit against the definition generation that is current at the
	 * actual delivery boundary. A recovered final may wait for its player to join long after server
	 * start (and after another data-pack reload), so startup reconciliation alone is not sufficient
	 * authority to disclose its fallback.
	 */
	public static boolean dependenciesMatch(
		final DefinitionSnapshot activeDefinitions,
		final Identifier cueId,
		final PersistedMessageRuntime.FrozenCue frozenCue
	) {
		Objects.requireNonNull(activeDefinitions, "activeDefinitions");
		Objects.requireNonNull(cueId, "cueId");
		Objects.requireNonNull(frozenCue, "frozenCue");
		return frozenCue.dependencySnapshot()
			.map(snapshot ->
				MessageDependencySnapshotCompiler.matches(
					activeDefinitions,
					cueId,
					snapshot,
					frozenCue.canonicalDocument()
				).matched()
			)
			.orElseGet(() ->
				frozenCue.definitionFingerprint()
					.filter(activeDefinitions.stableContentFingerprint()::equals)
					.isPresent()
			);
	}

	private static Reconciliation reconcileValidated(
		final Snapshot persisted,
		final Predicate<Instance> dependencyMatches
	) {
		Objects.requireNonNull(persisted, "persisted");
		Objects.requireNonNull(dependencyMatches, "dependencyMatches");
		var validation = persisted.validated();
		if (validation.error().isPresent()) {
			throw new IllegalArgumentException(
				"persisted message runtime is invalid: "
					+ validation.error().orElseThrow().message()
			);
		}

		List<Instance> instances = new ArrayList<>(persisted.instances().size());
		Set<UUID> finalized = new LinkedHashSet<>();
		Set<UUID> cancelled = new LinkedHashSet<>();
		for (Instance instance : persisted.instances()) {
			if (
				instance.restart() != RestartMode.CONTINUE
					|| dependencyMatches.test(instance)
			) {
				instances.add(instance);
				continue;
			}
			RestartMode replacement = hasStaticFallback(instance)
				? RestartMode.FINALIZE
				: RestartMode.CANCEL;
			instances.add(withRestart(instance, replacement));
			(replacement == RestartMode.FINALIZE ? finalized : cancelled).add(
				instance.instanceId()
			);
		}
		if (finalized.isEmpty() && cancelled.isEmpty()) {
			return new Reconciliation(persisted, Set.of(), Set.of());
		}
		Snapshot reconciled = new Snapshot(
			persisted.gameInstanceId(),
			persisted.snapshotGameTick(),
			persisted.runtimeRevision(),
			instances,
			persisted.pendingOffline(),
			persisted.tombstones(),
			persisted.callbackLedger()
		);
		return new Reconciliation(reconciled, finalized, cancelled);
	}

	private static boolean hasStaticFallback(final Instance instance) {
		String canonical = instance.frozenCue().canonicalDocument().normalizedJson();
		var parsed = MessageDefinitionParser.parseMessageCue(instance.cueId(), canonical);
		return parsed.valid()
			&& parsed.value().filter(value -> value.staticFallback().isPresent()).isPresent();
	}

	private static Instance withRestart(
		final Instance instance,
		final RestartMode restart
	) {
		return new Instance(
			instance.instanceId(),
			instance.cueId(),
			restart,
			instance.frozenCue(),
			instance.context(),
			instance.canonicalParameters(),
			instance.audience(),
			instance.timing(),
			instance.progress(),
			instance.recipients(),
			instance.dedupeKey(),
			instance.refreshKey(),
			instance.updatedAtGameTick()
		);
	}

	public record Reconciliation(
		Snapshot snapshot,
		Set<UUID> finalizedInstanceIds,
		Set<UUID> cancelledInstanceIds
	) {
		public Reconciliation {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			finalizedInstanceIds = Set.copyOf(finalizedInstanceIds);
			cancelledInstanceIds = Set.copyOf(cancelledInstanceIds);
		}
	}
}
