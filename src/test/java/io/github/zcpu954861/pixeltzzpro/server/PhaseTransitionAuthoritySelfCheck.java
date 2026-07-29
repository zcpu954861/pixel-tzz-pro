package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelActionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PanelSurface;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.SelectionMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetFilter;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TargetSelection;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TransitionPhaseOperation;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.PhaseEntry;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditEventType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.AuditLog;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionSummary;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.HostRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Guards the complete host-panel phase-transition slice used before the task timeline exists.
 */
public final class PhaseTransitionAuthoritySelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier SETUP = id("setup");
	private static final Identifier INITIALIZING = id("initializing");
	private static final Identifier READY = id("ready");
	private static final UUID HOST = uuid(1);
	private static final UUID PLAYER = uuid(2);
	private static final UUID GAME_INSTANCE = uuid(3);

	private PhaseTransitionAuthoritySelfCheck() {
	}

	public static void main(final String[] arguments) {
		DefinitionSnapshot definitions = definitions();
		PanelActionDefinition enterInitializing = action(
			"enter_initializing",
			SETUP,
			INITIALIZING
		);
		PanelActionDefinition enterReady = action("enter_ready", INITIALIZING, READY);
		WorldStateV3 setup = state();
		List<PhaseEntry> setupRoute = PixelTzzServerRuntime.consolePhaseRoute(
			setup.core(),
			definitions
		);
		check(
			setupRoute.stream().map(PhaseEntry::phaseId).toList()
				.equals(List.of(SETUP, INITIALIZING, READY)),
			"console phase route must retain deterministic game order"
		);
		check(
			setupRoute.stream().map(PhaseEntry::relation).toList()
				.equals(List.of("current", "future", "future")),
			"console phase route must mark the active and future phases"
		);
		List<PhaseEntry> branchedRoute = PixelTzzServerRuntime.consolePhaseRoute(
			setup.core(),
			branchedDefinitions(definitions)
		);
		check(
			branchedRoute.size() == 1
				&& branchedRoute.getFirst().phaseId().equals(SETUP)
				&& branchedRoute.getFirst().relation().equals("current"),
			"a branched phase graph must not be flattened into false completed/future ordering"
		);

		check(
			PixelTzzServerRuntime.supportedPanelAction(enterInitializing),
			"transition_phase must be included in the host action registry"
		);
		check(
			PixelTzzServerRuntime.operationType(enterInitializing).equals("transition_phase"),
			"transition_phase must retain its protocol operation type"
		);

		var transitioned = PhaseTransitionAuthority.transition(
			setup,
			definitions,
			enterInitializing,
			HOST,
			setup.stateRevision(),
			100L
		);
		check(transitioned.successful(), "setup -> initializing must succeed");
		WorldStateV3 initializing = transitioned.nextState().orElseThrow();
		check(
			initializing.core().activePhaseId().equals(Optional.of(INITIALIZING)),
			"active phase must advance to initializing"
		);
		check(
			initializing.timeline().isEmpty() && initializing.core().activeForcedFlow().isEmpty(),
			"phase transition must not start a flow or task timeline"
		);
		check(
			initializing.core().players().equals(setup.core().players()),
			"player identities and locked fields must remain unchanged"
		);
		check(
			initializing.activeGameInstanceId().equals(setup.activeGameInstanceId()),
			"game-instance scope must remain unchanged"
		);
		check(
			initializing.core().audit().recentEvents().getLast().type()
				== AuditEventType.PHASE_TRANSITIONED,
			"phase transition must enter the persistent audit"
		);

		check(
			PhaseTransitionAuthority.transition(
				setup,
				definitions,
				enterInitializing,
				HOST,
				setup.stateRevision() - 1L,
				101L
			).code() == OperationCode.STATE_REVISION_STALE,
			"an old state revision must be rejected"
		);
		check(
			PhaseTransitionAuthority.transition(
				initializing,
				definitions,
				enterInitializing,
				HOST,
				initializing.stateRevision(),
				102L
			).code() == OperationCode.PHASE_MISMATCH,
			"the setup-only action must disappear after the phase advances"
		);
		check(
			PhaseTransitionAuthority.transition(
				setup,
				definitions,
				action("skip_initialization", SETUP, READY),
				HOST,
				setup.stateRevision(),
				103L
			).code() == OperationCode.PHASE_MISMATCH,
			"an unregistered phase edge must fail closed"
		);
		check(
			PhaseTransitionAuthority.transition(
				setup,
				definitions,
				enterInitializing,
				PLAYER,
				setup.stateRevision(),
				104L
			).code() == OperationCode.NOT_HOST,
			"a non-host must not advance the phase"
		);

		var ready = PhaseTransitionAuthority.transition(
			initializing,
			definitions,
			enterReady,
			HOST,
			initializing.stateRevision(),
			105L
		);
		check(
			ready.successful()
				&& ready.nextState().orElseThrow().core().activePhaseId().equals(Optional.of(READY)),
			"enter_ready must become available from initializing"
		);
		check(
			PixelTzzServerRuntime.consolePhaseRoute(
				ready.nextState().orElseThrow().core(),
				definitions
			).stream().map(PhaseEntry::relation).toList()
				.equals(List.of("completed", "completed", "current")),
			"console phase route must preserve completed history around the current phase"
		);
		System.out.println("PHASE_TRANSITION_AUTHORITY_SELF_CHECK=PASS");
	}

	private static WorldStateV3 state() {
		Identifier spawnField = id("hunter_spawn");
		PlayerRecord player = new PlayerRecord(
			PLAYER,
			"PlayerB",
			id("hunter"),
			Optional.empty(),
			id("alive"),
			Map.of(
				spawnField,
				new PersistentFieldValue(1, StoredValue.singleChoice("north_gate"))
			),
			Optional.empty(),
			Optional.empty(),
			CompletionSummary.empty(),
			1L,
			10L
		);
		WorldStateV2 core = new WorldStateV2(
			WorldStateV2.SCHEMA_VERSION,
			7L,
			Optional.of(GAME),
			Optional.of(SETUP),
			Optional.of(HostRecord.migratedLegacy(HOST)),
			Map.of(PLAYER, player),
			Optional.empty(),
			List.of(),
			AuditLog.empty(),
			Optional.empty()
		);
		return new WorldStateV3(
			WorldStateV3.SCHEMA_VERSION,
			core,
			Optional.of(GAME_INSTANCE),
			Optional.empty(),
			List.of(),
			List.of()
		);
	}

	private static DefinitionSnapshot definitions() {
		PhaseDefinition setup = new PhaseDefinition(
			SETUP,
			GAME,
			text("开局设置"),
			Set.of(INITIALIZING),
			Optional.empty(),
			Optional.empty()
		);
		PhaseDefinition initializing = new PhaseDefinition(
			INITIALIZING,
			GAME,
			text("玩家初始化"),
			Set.of(READY),
			Optional.empty(),
			Optional.empty()
		);
		PhaseDefinition ready = new PhaseDefinition(
			READY,
			GAME,
			text("等待玩家准备"),
			Set.of(),
			Optional.empty(),
			Optional.empty()
		);
		return new DefinitionSnapshot(
			12L,
			Map.of(
				GAME,
				new GameDefinition(
					GAME,
					2,
					1,
					text("全员逃走中"),
					SETUP,
					id("runner"),
					id("alive"),
					Optional.empty()
				)
			),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(SETUP, setup, INITIALIZING, initializing, READY, ready),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Map.of(),
			Set.of(),
			Set.of(),
			Map.of()
		);
	}

	private static DefinitionSnapshot branchedDefinitions(final DefinitionSnapshot base) {
		Identifier alternate = id("alternate_ready");
		Map<Identifier, PhaseDefinition> phases = new java.util.LinkedHashMap<>(base.phases());
		PhaseDefinition setup = phases.get(SETUP);
		phases.put(
			SETUP,
			new PhaseDefinition(
				setup.id(),
				setup.game(),
				setup.name(),
				Set.of(INITIALIZING, alternate),
				setup.onEnter(),
				setup.onExit()
			)
		);
		phases.put(
			alternate,
			new PhaseDefinition(
				alternate,
				GAME,
				text("备用准备阶段"),
				Set.of(),
				Optional.empty(),
				Optional.empty()
			)
		);
		return new DefinitionSnapshot(
			base.generation(),
			base.games(),
			base.roles(),
			base.teams(),
			base.lifeStates(),
			phases,
			base.fields(),
			base.flows(),
			base.tasks(),
			base.panelActions(),
			base.pages(),
			base.themes(),
			base.sourceDocuments(),
			base.functions(),
			base.predicates(),
			base.predicateDocuments()
		);
	}

	private static PanelActionDefinition action(
		final String path,
		final Identifier source,
		final Identifier target
	) {
		TargetFilter filter = new TargetFilter(
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of()
		);
		return new PanelActionDefinition(
			id(path),
			GAME,
			PanelSurface.HOST,
			"primary",
			30,
			text(path),
			text(path),
			Optional.empty(),
			Optional.of("#6EAED8"),
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Set.of(source),
			new TargetSelection(SelectionMode.NONE, 0, 0, filter),
			Optional.empty(),
			new TransitionPhaseOperation(target)
		);
	}

	private static RichText text(final String value) {
		return new RichText("{\"text\":\"" + value + "\"}", value);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static UUID uuid(final long leastSignificantBits) {
		return new UUID(0L, leastSignificantBits);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
