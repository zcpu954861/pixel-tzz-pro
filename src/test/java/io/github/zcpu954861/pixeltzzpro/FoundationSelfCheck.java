package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.animation.SubtitleQueueTimeline;
import io.github.zcpu954861.pixeltzzpro.animation.TypewriterTimeline;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Compilation;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Source;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionRegistry;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.ExecutionSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.TimelineSnapshotCompiler;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignLifeStateOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignRoleOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ApplyTiming;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarColor;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.BossBarStyle;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompletionPolicy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ChoiceNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.CompleteNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.ConfirmNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldMigrationStrategy;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PageNode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.StartFlowOperation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TransitionPhaseOperation;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Easing;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ButtonContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.FieldInputContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionKeyframe;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionProperty;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionTrack;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NodeDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ReducedMotion;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.RepeatContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ResponsiveTier;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SingleChildContent;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.SizeMode;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.UiEvent;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.server.TabListDisplay;
import io.github.zcpu954861.pixeltzzpro.state.PixelTzzWorldState;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.UiMotionRuntime.Transform;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.RepeatVirtualization;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.RepeatVirtualization.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Small runnable contract check with no test framework or fixtures.
 */
public final class FoundationSelfCheck {
	private FoundationSelfCheck() {
	}

	public static void main(final String[] args) {
		check(NetworkProtocol.isCompatible(NetworkProtocol.CURRENT_VERSION), "current protocol must be compatible");
		check(!NetworkProtocol.isCompatible(NetworkProtocol.CURRENT_VERSION + 1), "future protocol must fail closed");
		check(!NetworkProtocol.isCompatible(NetworkProtocol.CURRENT_VERSION - 1), "older protocol must fail closed");
		check(
			TabListDisplay.format("[猎人]", "PlayerB", 0xE94F64)
				.getString()
				.equals("[猎人] PlayerB"),
			"TAB display formatting must preserve the data-pack prefix and player name"
		);

		PixelTzzWorldState state = PixelTzzWorldState.initial();
		check(state.isSchemaCompatible(), "fresh state must be compatible");
		check(state.activePhaseId().isEmpty(), "fresh state must not invent a data-pack phase");
		check(state.hostId().isEmpty(), "reload must not invent a host");
		check(state.stateRevision() == 0L, "resource loading must not advance core state revision");

		JsonElement encodedState = PixelTzzWorldState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
		PixelTzzWorldState decodedState = PixelTzzWorldState.CODEC.parse(JsonOps.INSTANCE, encodedState).getOrThrow();
		check(decodedState.isSchemaCompatible(), "current state codec round-trip must remain compatible");
		check(decodedState.activePhaseId().isEmpty(), "state codec round-trip must retain an absent phase");

		JsonElement futureData = JsonParser.parseString(
			"""
			{"schema_version":2,"phase":"future_phase","state_revision":9,"future_field":{"keep":true}}
			"""
		);
		PixelTzzWorldState futureState = PixelTzzWorldState.CODEC.parse(JsonOps.INSTANCE, futureData).getOrThrow();
		check(!futureState.isSchemaCompatible(), "future state must fail closed");
		check(!futureState.isDirty(), "read-only future state must not be marked dirty");
		JsonElement preservedFutureData = PixelTzzWorldState.CODEC.encodeStart(JsonOps.INSTANCE, futureState).getOrThrow();
		check(preservedFutureData.equals(futureData), "future state must preserve unknown data exactly");

		String unicodeText = "加载🚨";
		int unicodeLength = unicodeText.codePointCount(0, unicodeText.length());
		check(unicodeLength == 3, "self-check text must contain three Unicode code points");
		long step = 65_000_000L;
		check(TypewriterTimeline.visibleCodePoints(0L, unicodeLength, step) == 1, "typewriter must reveal immediately");
		check(
			TypewriterTimeline.visibleCodePoints(step - 1L, unicodeLength, step) == 1,
			"typewriter must wait for the next interval"
		);
		check(TypewriterTimeline.visibleCodePoints(step, unicodeLength, step) == 2, "typewriter interval boundary failed");
		check(
			TypewriterTimeline.visibleCodePoints(Long.MAX_VALUE, unicodeLength, step) == unicodeLength,
			"typewriter must clamp at the final code point"
		);
		int[] codePoints = unicodeText.codePoints().toArray();
		check(
			new String(codePoints, 0, codePoints.length).equals(unicodeText),
			"typewriter code-point storage must preserve supplementary characters"
		);
		int completedTicks = 0;
		for (int tick = 1; tick <= 30; tick++) {
			var hold = SubtitleQueueTimeline.afterCompletedFrame(completedTicks, 30);
			completedTicks = hold.completedTicks();
			check(
				hold.mayAdvance() == (tick == 30),
				"queued subtitle must retain its complete text for all 30 hold ticks"
			);
		}

		checkMotionRuntime();
		checkDefinitionRegistry(state);
		System.out.println("FOUNDATION_SELF_CHECK=PASS");
	}

	private static void checkMotionRuntime() {
		List<MotionTrack> tracks = List.of(
			track(MotionProperty.OPACITY, "0.0", "1.0"),
			track(MotionProperty.TRANSLATE_X, "0.0", "20.0"),
			track(MotionProperty.TRANSLATE_Y, "0.0", "-6.0"),
			track(MotionProperty.SCALE, "1.0", "2.0"),
			track(MotionProperty.COLOR, "\"#FF0000\"", "\"@colors.end\""),
			track(MotionProperty.CLIP_PROGRESS, "0.0", "1.0"),
			track(MotionProperty.PROGRESS_VALUE, "0.0", "10.0")
		);
		MotionDefinition keep = new MotionDefinition(
			100,
			0,
			Easing.LINEAR,
			tracks,
			ReducedMotion.KEEP
		);
		UiMotionRuntime.Sample middle = UiMotionRuntime.sample(
			keep,
			50L,
			false,
			Map.of("end", "#0000FF")
		);
		checkClose(middle.opacity(), 0.5, "motion opacity interpolation failed");
		checkClose(middle.translateX(), 10.0, "motion X interpolation failed");
		checkClose(middle.translateY(), -3.0, "motion Y interpolation failed");
		checkClose(middle.scale(), 1.5, "motion scale interpolation failed");
		check(middle.color().orElseThrow() == 0xFF800080, "motion color interpolation failed");
		check(
			UiMotionRuntime.interpolateColor(0xFF102030, 0xFF90A0B0, -1.0) == 0xFF102030
				&& UiMotionRuntime.interpolateColor(0xFF102030, 0xFF90A0B0, 2.0) == 0xFF90A0B0,
			"shared UI color interpolation must clamp animation progress"
		);
		Window repeatGridWindow = RepeatVirtualization.verticalWindow(
			64,
			2,
			48,
			6,
			480,
			240,
			2
		);
		check(
			RepeatVirtualization.fullyVisibleRows(237, 43, 5) == 5,
			"list viewport must count the final row without requiring a trailing gap"
		);
		check(
			RepeatVirtualization.fullyVisibleRows(234, 43, 5) == 4,
			"list viewport must not count a row that is still partially clipped"
		);
		check(
			repeatGridWindow.startItem() == 16
				&& repeatGridWindow.endItem() == 34
				&& repeatGridWindow.leadingExtent() == 378
				&& repeatGridWindow.trailingExtent() == 714,
			"responsive-grid Repeat virtualization window is incorrect"
		);
		check(
			RepeatVirtualization.verticalWindow(0, 2, 48, 6, 0, 240, 2)
				.equals(new Window(0, 0, 0, 0)),
			"empty Repeat virtualization window must stay empty"
		);
		checkNavigationTransitions();
		checkClose(middle.clipProgress(), 0.5, "motion clip interpolation failed");
		checkClose(
			middle.progressValue().orElseThrow(),
			5.0,
			"motion progress interpolation failed"
		);
		check(!middle.finished(), "mid-timeline motion must not report completion");

		UiMotionRuntime.Sample reducedKeep = UiMotionRuntime.sample(
			keep,
			50L,
			true,
			Map.of("end", "#0000FF")
		);
		checkClose(reducedKeep.translateX(), 20.0, "reduced KEEP must remove translation");
		checkClose(reducedKeep.translateY(), -6.0, "reduced KEEP must finish translation");
		checkClose(reducedKeep.scale(), 2.0, "reduced KEEP must finish scale");
		checkClose(reducedKeep.opacity(), 0.5, "reduced KEEP may preserve opacity feedback");

		MotionDefinition finalMotion = new MotionDefinition(
			100,
			80,
			Easing.EASE_OUT_CUBIC,
			tracks,
			ReducedMotion.FINAL
		);
		UiMotionRuntime.Sample finalSample = UiMotionRuntime.sample(
			finalMotion,
			0L,
			true,
			Map.of("end", "#0000FF")
		);
		check(finalSample.finished(), "reduced FINAL must complete immediately");
		checkClose(finalSample.progressValue().orElseThrow(), 10.0, "reduced FINAL must use final value");

		MotionDefinition fadeMotion = new MotionDefinition(
			200,
			80,
			Easing.LINEAR,
			tracks,
			ReducedMotion.FADE
		);
		UiMotionRuntime.Sample reducedFade = UiMotionRuntime.sample(
			fadeMotion,
			50L,
			true,
			Map.of("end", "#0000FF")
		);
		checkClose(reducedFade.opacity(), 0.5, "reduced FADE must use a short fade");
		checkClose(reducedFade.translateX(), 20.0, "reduced FADE must finish movement");
		check(!reducedFade.finished(), "reduced FADE must retain its short feedback window");
		check(
			UiMotionRuntime.sample(fadeMotion, 100L, true, Map.of("end", "#0000FF")).finished(),
			"reduced FADE must finish after 100 ms"
		);
		MotionDefinition movementOnlyFade = new MotionDefinition(
			200,
			0,
			Easing.LINEAR,
			List.of(track(MotionProperty.TRANSLATE_X, "0.0", "12.0")),
			ReducedMotion.FADE
		);
		checkClose(
			UiMotionRuntime.sample(movementOnlyFade, 50L, true, Map.of()).opacity(),
			0.5,
			"reduced FADE must add a fade when the source motion has no opacity track"
		);
		MotionDefinition instant = new MotionDefinition(
			0,
			40,
			Easing.LINEAR,
			List.of(track(MotionProperty.TRANSLATE_X, "0.0", "12.0")),
			ReducedMotion.FINAL
		);
		UiMotionRuntime.Sample instantAtDelay = UiMotionRuntime.sample(
			instant,
			40L,
			false,
			Map.of()
		);
		checkClose(
			instantAtDelay.translateX(),
			12.0,
			"zero-duration motion must reach its final value at the delay boundary"
		);
		check(instantAtDelay.finished(), "zero-duration motion must finish at its delay boundary");

		UiMotionRuntime.Sample composed = UiMotionRuntime.compose(
			List.of(
				new UiMotionRuntime.Sample(
					0.5F,
					4.0,
					-2.0,
					0.8,
					OptionalInt.of(0xFFFF0000),
					0.75,
					OptionalDouble.empty(),
					true
				),
				new UiMotionRuntime.Sample(
					0.8F,
					3.0,
					5.0,
					1.25,
					OptionalInt.of(0xFF00FF00),
					0.5,
					OptionalDouble.of(7.0),
					false
				)
			)
		);
		checkClose(composed.opacity(), 0.4, "composed motion opacity must multiply");
		checkClose(composed.translateX(), 7.0, "composed motion X must add");
		checkClose(composed.translateY(), 3.0, "composed motion Y must add");
		checkClose(composed.scale(), 1.0, "composed motion scale must multiply");
		checkClose(composed.clipProgress(), 0.375, "composed motion clipping must multiply");
		check(composed.color().orElseThrow() == 0xFF00FF00, "latest motion color must win");
		checkClose(composed.progressValue().orElseThrow(), 7.0, "latest progress motion must win");
		check(!composed.finished(), "composed motion must wait for every layer");

		int baseColor = 0xFF202830;
		OptionalInt colorOrigin = UiMotionRuntime.continuityColorOrigin(
			OptionalInt.of(0xFFFF0000),
			OptionalInt.empty(),
			baseColor
		);
		check(
			UiMotionRuntime.applyColorContinuity(
				OptionalInt.empty(),
				baseColor,
				colorOrigin,
				1.0
			).orElseThrow() == 0xFFFF0000,
			"color continuity must preserve the replaced slot at the trigger frame"
		);
		check(
			UiMotionRuntime.applyColorContinuity(
				OptionalInt.empty(),
				baseColor,
				colorOrigin,
				0.0
			).isEmpty(),
			"color continuity must release to the node base color after the transition"
		);
		OptionalDouble progressDelta = UiMotionRuntime.continuityNumberDelta(
			OptionalDouble.of(8.0),
			OptionalDouble.empty(),
			3.0
		);
		checkClose(
			UiMotionRuntime.applyNumberContinuity(
				OptionalDouble.empty(),
				3.0,
				progressDelta,
				1.0
			).orElseThrow(),
			8.0,
			"progress continuity must preserve the replaced display value"
		);
		check(
			UiMotionRuntime.applyNumberContinuity(
				OptionalDouble.empty(),
				3.0,
				progressDelta,
				0.0
			).isEmpty(),
			"progress continuity must release to the current bound value"
		);

		Rect pivot = new Rect(0, 0, 100, 100);
		Transform parent = Transform.root(0, 0).around(pivot, 0.5, 0.0, 0.0);
		Transform child = parent.around(new Rect(10, 10, 20, 20), 1.0, 10.0, 0.0);
		checkClose(
			child.apply(new Rect(0, 0, 0, 0)).x()
				- parent.apply(new Rect(0, 0, 0, 0)).x(),
			5.0,
			"child translation must inherit the parent scale"
		);

		Rect hitBounds = new Rect(10, 20, 200, 40);
		Rect sourceBounds = UiMotionRuntime.presentationSource(hitBounds, 2.0);
		check(
			sourceBounds.equals(new Rect(60, 30, 100, 20)),
			"scaled widgets must render logical source bounds around the transformed hit center"
		);
		check(
			hitBounds.equals(new Rect(10, 20, 200, 40)),
			"presentation scaling must not mutate the transformed hit rectangle"
		);
	}

	private static void checkNavigationTransitions() {
		NavigationTransitionTimeline.Sample rootStart =
			NavigationTransitionTimeline.sample(Motion.ROOT_ENTER, 0L, 500, 1.0);
		check(rootStart.translateY() > 0.0F, "root entry must rise into place");
		check(rootStart.scale() < 1.0F, "root entry must settle from a smaller scale");

		NavigationTransitionTimeline.Sample pushStart =
			NavigationTransitionTimeline.sample(Motion.PUSH_ENTER, 0L, 500, 1.0);
		NavigationTransitionTimeline.Sample pushEnd =
			NavigationTransitionTimeline.sample(Motion.PUSH_ENTER, 170L, 500, 1.0);
		check(pushStart.translateX() > 0.0F, "push entry must arrive from the right");
		checkClose(pushEnd.translateX(), 0.0, "push entry must finish at its logical bounds");
		check(pushEnd.veilAlpha() == 0 && pushEnd.finished(), "push entry end state must be clear and finished");

		NavigationTransitionTimeline.Sample pushExit =
			NavigationTransitionTimeline.sample(Motion.PUSH_EXIT, 110L, 500, 1.0);
		NavigationTransitionTimeline.Sample popExit =
			NavigationTransitionTimeline.sample(Motion.POP_EXIT, 110L, 500, 1.0);
		check(pushExit.translateX() < 0.0F, "push exit must move toward the left");
		check(popExit.translateX() > 0.0F, "pop exit must reverse toward the right");

		NavigationTransitionTimeline.Sample reduced =
			NavigationTransitionTimeline.sample(Motion.POP_ENTER, 0L, 500, 0.0);
		checkClose(reduced.translateX(), 0.0, "zero screen-effect scale must remove navigation translation");
		checkClose(reduced.scale(), 1.0, "zero screen-effect scale must remove navigation scaling");
		check(
			NavigationTransitionTimeline.durationMilliseconds(Motion.POP_ENTER, 0.0) == 80,
			"reduced navigation must use the short fade duration"
		);
	}

	private static MotionTrack track(
		final MotionProperty property,
		final String start,
		final String end
	) {
		return new MotionTrack(
			property,
			List.of(
				new MotionKeyframe(0.0, new StyleValue(start)),
				new MotionKeyframe(1.0, new StyleValue(end))
			)
		);
	}

	private static void checkClose(
		final double actual,
		final double expected,
		final String message
	) {
		check(Math.abs(actual - expected) < 0.0001, message + ": " + actual + " != " + expected);
	}

	private static void checkDefinitionRegistry(final PixelTzzWorldState worldState) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Set<Identifier> functions = Set.of(
			Identifier.parse("test:flow/start"),
			Identifier.parse("test:flow/player_complete"),
			Identifier.parse("test:flow/all_complete")
		);
		Set<Identifier> predicates = Set.of(
			Identifier.parse("test:field/visible"),
			Identifier.parse("test:panel/visible"),
			Identifier.parse("test:panel/enabled")
		);
		List<Source> validSources = validDefinitionSources();
		Compilation valid = DefinitionCompiler.compile(validSources, functions, predicates);
		check(valid.valid(), "valid definition bundle must compile: " + valid.problems());
		DefinitionSnapshot compiled = valid.snapshot().orElseThrow();
		check(compiled.games().size() == 1, "valid bundle game count failed");
		check(compiled.roles().size() == 2, "valid bundle role count failed");
		check(compiled.teams().size() == 1, "valid bundle team count failed");
		check(compiled.lifeStates().size() == 2, "valid bundle life-state count failed");
		check(compiled.phases().size() == 2, "valid bundle phase count failed");
		check(compiled.fields().size() == 1, "valid bundle field count failed");
		check(compiled.flows().size() == 1, "valid bundle flow count failed");
		check(compiled.panelActions().size() == 2, "valid bundle panel action count failed");
		check(compiled.pages().size() == 2, "valid bundle page count failed");
		check(compiled.themes().size() == 1, "valid bundle theme count failed");
		check(
			compiled.sourceDocuments().size() == validSources.size(),
			"every compiled definition must retain one canonical source document"
		);
		check(
			compiled.sourceDocuments()
				.values()
				.stream()
				.allMatch(document -> document.sha256().length() == 64),
			"canonical definition documents must retain SHA-256 identities"
		);
		check(compiled.functions().equals(functions), "function references must survive compilation");
		check(compiled.predicates().equals(predicates), "predicate references must survive compilation");
		check(compiled.definitionCount() == 15, "valid bundle total definition count failed");
		check(
			compiled.games().get(Identifier.parse("test:main")).defaultLifeState().equals(
				Identifier.parse("test:alive")
			),
			"default life state must survive compilation"
		);
		var laneField = compiled.fields().get(Identifier.parse("test:lane"));
		check(laneField.version() == 1, "field version must survive compilation");
		check(laneField.roles().contains(Identifier.parse("test:runner")), "field role applicability was lost");
		check(laneField.phases().contains(Identifier.parse("test:setup")), "field phase applicability was lost");
		check(
			laneField.visibleWhen().orElseThrow().equals(Identifier.parse("test:field/visible")),
			"field visibility predicate was lost"
		);
		check(
			laneField.migration() == FieldMigrationStrategy.PRESERVE,
			"field migration strategy was mapped incorrectly"
		);
		check(
			compiled.flows().get(Identifier.parse("test:tutorial")).hostBossBar().isPresent(),
			"required flow must retain its host BossBar definition"
		);
		var hostBossBar = compiled.flows()
			.get(Identifier.parse("test:tutorial"))
			.hostBossBar()
			.orElseThrow();
		check(hostBossBar.color() == BossBarColor.YELLOW, "BossBar color was mapped incorrectly");
		check(hostBossBar.style() == BossBarStyle.NOTCHED_10, "BossBar style was mapped incorrectly");
		check(hostBossBar.priority() == 10, "BossBar priority was mapped incorrectly");
		check(hostBossBar.completionFeedback().isPresent(), "BossBar completion feedback was lost");
		var assignHunter = compiled.panelActions().get(Identifier.parse("test:assign_hunter"));
		check(assignHunter.icon().orElseThrow().equals(Identifier.parse("test:textures/gui/hunter")), "panel icon was lost");
		check(assignHunter.color().orElseThrow().equals("#E94F64"), "panel color was lost");
		check(assignHunter.enabledWhen().isPresent(), "panel enabled predicate was lost");
		check(
			((io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.AssignRoleOperation)assignHunter.operation())
				.completionPolicy() == CompletionPolicy.ALWAYS,
			"assign_role must default to the always completion policy"
		);
		check(
			assignHunter.target().filter().incompleteFlows().contains(Identifier.parse("test:tutorial")),
			"panel completion filter was lost"
		);
		check(
			compiled.panelActions().get(Identifier.parse("test:revive")).operation()
				instanceof AssignLifeStateOperation,
			"assign_life_state operation was mapped incorrectly"
		);
		expectUnsupported(() -> compiled.games().clear(), "definition maps must be immutable");
		expectUnsupported(() -> compiled.lifeStates().clear(), "life-state maps must be immutable");
		expectUnsupported(() -> compiled.pages().clear(), "page maps must be immutable");
		expectUnsupported(() -> compiled.themes().clear(), "theme maps must be immutable");
		expectUnsupported(() -> compiled.sourceDocuments().clear(), "source-document maps must be immutable");
		expectUnsupported(() -> compiled.functions().clear(), "function sets must be immutable");
		expectUnsupported(() -> compiled.predicates().clear(), "predicate sets must be immutable");
		expectUnsupported(
			() -> compiled.pages().get(Identifier.parse("test:page/intro")).root().responsive().clear(),
			"page node maps must be immutable"
		);
		expectUnsupported(
			() -> compiled.flows().get(Identifier.parse("test:tutorial")).nodes().clear(),
			"flow node maps must be immutable"
		);
		checkUiDefinitionCompiler(validSources, functions, predicates, compiled);

		Compilation validLifeStateChange = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.FLOW,
				"tutorial",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Life-state change"},
				  "entry": "capture",
				  "nodes": [
				    {
				      "id": "capture",
				      "type": "change_state",
				      "axis": "life_state",
				      "value": "test:captured",
				      "next": "done"
				    },
				    {"id": "done", "type": "complete"}
				  ]
				}
				"""
			),
			functions,
			predicates
		);
		check(
			validLifeStateChange.valid(),
			"life_state change nodes must compile against the life-state registry: "
				+ validLifeStateChange.problems()
		);

		Compilation duplicateKey = DefinitionCompiler.compile(
			List.of(
				source(
					DefinitionType.GAME,
					"main",
					"""
					{
					  "format_version": 1,
					  "format_version": 1,
					  "api_version": 1,
					  "content_version": 1,
					  "name": {"text": "Test"},
					  "initial_phase": "test:setup",
					  "default_role": "test:runner"
					}
					"""
				)
			),
			Set.of(),
			predicates
		);
		checkProblem(duplicateKey, "JSON_SYNTAX", "duplicate JSON keys must fail");

		Compilation unknownKey = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.ROLE,
				"runner",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Runner"},
				  "tags": [],
				  "unknown": true
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(unknownKey, "UNKNOWN_KEY", "unknown definition keys must fail");

		Compilation bareIdentifier = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 1,
				  "content_version": 1,
				  "name": {"text": "Test"},
				  "initial_phase": "setup",
				  "default_role": "test:runner",
				  "default_life_state": "test:alive"
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(bareIdentifier, "INVALID_IDENTIFIER", "references must have explicit namespaces");

		Compilation missingRole = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 1,
				  "content_version": 1,
				  "name": {"text": "Test"},
				  "initial_phase": "test:setup",
				  "default_role": "test:missing",
				  "default_life_state": "test:alive"
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(missingRole, "MISSING_REFERENCE", "missing cross references must fail");

		Compilation missingLifeState = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 1,
				  "content_version": 1,
				  "name": {"text": "Test"},
				  "initial_phase": "test:setup",
				  "default_role": "test:runner",
				  "default_life_state": "test:missing"
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(missingLifeState, "MISSING_REFERENCE", "missing default life states must fail");

		String assignHunterJson = validSources.stream()
			.filter(source -> source.type() == DefinitionType.PANEL_ACTION)
			.filter(source -> source.id().equals(Identifier.parse("test:assign_hunter")))
			.findFirst()
			.orElseThrow()
			.json();
		Compilation missingPanelPredicate = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PANEL_ACTION,
				"assign_hunter",
				assignHunterJson.replace("test:panel/enabled", "test:panel/missing")
			),
			functions,
			predicates
		);
		checkProblem(
			missingPanelPredicate,
			"MISSING_EXTERNAL_RESOURCE",
			"dynamic panel predicates must exist"
		);

		String reviveJson = validSources.stream()
			.filter(source -> source.type() == DefinitionType.PANEL_ACTION)
			.filter(source -> source.id().equals(Identifier.parse("test:revive")))
			.findFirst()
			.orElseThrow()
			.json();
		Compilation missingAssignedLifeState = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PANEL_ACTION,
				"revive",
				reviveJson.replace("test:alive", "test:missing")
			),
			functions,
			predicates
		);
		checkProblem(
			missingAssignedLifeState,
			"MISSING_REFERENCE",
			"assign_life_state must reference a registered life state"
		);

		Compilation cycle = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.FLOW,
				"tutorial",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Tutorial"},
				  "entry": "a",
				  "nodes": [
				    {"id": "a", "type": "page", "page": "test:page/a", "next": "b"},
				    {"id": "b", "type": "confirm", "page": "test:page/b", "next": "a"},
				    {"id": "done", "type": "complete"}
				  ]
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(cycle, "INVALID_GRAPH", "flow cycles and unreachable nodes must fail");

		Compilation emptyNodes = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.FLOW,
				"tutorial",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Tutorial"},
				  "entry": "start",
				  "nodes": []
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(emptyNodes, "INVALID_GRAPH", "empty flow graphs must fail explicitly");

		Compilation invalidBossBarTemplate = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.FLOW,
				"tutorial",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Tutorial"},
				  "entry": "done",
				  "required": true,
				  "host_bossbar": {
				    "name": {"text": "{completed}/{total}"},
				    "color": "purple",
				    "style": "progress"
				  },
				  "nodes": [
				    {"id": "done", "type": "complete"}
				  ]
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			invalidBossBarTemplate,
			"INVALID_TEMPLATE",
			"host BossBar templates must identify the event and progress"
		);

		Compilation negativeStringLength = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.FIELD,
				"lane",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Lane"},
				  "type": "string",
				  "max_length": -1
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			negativeStringLength,
			"OUT_OF_RANGE",
			"negative string length constraints must fail"
		);

		Compilation resetWithoutDefault = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.FIELD,
				"lane",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 2,
				  "name": {"text": "Lane"},
				  "type": "single_choice",
				  "migration": "reset_to_default",
				  "options": ["alpha", "beta"]
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			resetWithoutDefault,
			"MISSING_KEY",
			"reset_to_default migrations must declare a valid default"
		);

		Compilation contradictoryTargetFilter = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PANEL_ACTION,
				"assign_hunter",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "surface": "host",
				  "section": "primary",
				  "label": {"text": "Contradictory target"},
				  "description": {"text": "Must fail."},
				  "phases": ["test:setup"],
				  "target": {
				    "mode": "multiple",
				    "filter": {
				      "completed_flows": ["test:tutorial"],
				      "incomplete_flows": ["test:tutorial"]
				    }
				  },
				  "confirmation": {
				    "title": {"text": "Confirm"},
				    "consequences": [{"text": "This action is intentionally invalid."}]
				  },
				  "operation": {
				    "type": "start_flow",
				    "flow": "test:tutorial"
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			contradictoryTargetFilter,
			"INVALID_CONSTRAINT",
			"target filters must reject contradictory flow-completion requirements"
		);

		List<Source> multiChoiceSources = replaceSource(
			validSources,
			DefinitionType.FIELD,
			"lane",
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "version": 1,
			  "name": {"text": "Lane"},
			  "type": "multi_choice",
			  "options": ["alpha", "beta"]
			}
			"""
		);
		Compilation ambiguousMultiChoice = DefinitionCompiler.compile(
			multiChoiceSources,
			functions,
			predicates
		);
		checkProblem(
			ambiguousMultiChoice,
			"TYPE_MISMATCH",
			"single-route choice nodes must reject multi_choice fields"
		);

		String nestedJson = "{\"format_version\":1,\"value\":"
			+ "[".repeat(DefinitionCompiler.MAX_JSON_DEPTH + 1)
			+ "0"
			+ "]".repeat(DefinitionCompiler.MAX_JSON_DEPTH + 1)
			+ "}";
		Compilation excessiveDepth = DefinitionCompiler.compile(
			List.of(source(DefinitionType.GAME, "deep", nestedJson)),
			Set.of(),
			Set.of()
		);
		checkProblem(excessiveDepth, "JSON_SYNTAX", "excessive JSON nesting must fail without recursion overflow");

		StringBuilder noisyDefinition = new StringBuilder(
			"""
			{
			  "format_version": 1,
			  "api_version": 1,
			  "content_version": 1,
			  "name": {"text": "Noisy"},
			  "initial_phase": "test:setup",
			  "default_role": "test:runner",
			  "default_life_state": "test:alive"
			"""
		);
		int noisyProblemCount = DefinitionCompiler.MAX_DIAGNOSTIC_DETAILS + 50;
		for (int index = 0; index < noisyProblemCount; index++) {
			noisyDefinition.append(",\"unknown_").append(index).append("\":true");
		}
		noisyDefinition.append('}');
		Compilation noisyCompilation = DefinitionCompiler.compile(
			List.of(source(DefinitionType.GAME, "noisy", noisyDefinition.toString())),
			Set.of(),
			Set.of()
		);
		check(noisyCompilation.totalProblemCount() == noisyProblemCount, "diagnostic total must remain exact");
		check(
			noisyCompilation.problems().size() == DefinitionCompiler.MAX_DIAGNOSTIC_DETAILS,
			"diagnostic details must be bounded"
		);
		check(noisyCompilation.problemsTruncated(), "bounded diagnostic reports must expose truncation");
		DefinitionRegistry noisyRegistry = new DefinitionRegistry();
		check(!noisyRegistry.apply(noisyCompilation).applied(), "noisy invalid candidate must not apply");
		check(
			noisyRegistry.view().totalProblemCount() == noisyProblemCount,
			"registry must retain the exact diagnostic total"
		);
		check(
			noisyRegistry.view().diagnosticSummary().endsWith("(+" + (noisyProblemCount - 1) + ")"),
			"diagnostic summary must report the true remaining count"
		);

		String sharedLargeJson = " ".repeat(DefinitionCompiler.MAX_DEFINITION_CHARACTERS);
		List<Source> oversizedBundleSources = new ArrayList<>();
		int oversizedSourceCount = DefinitionCompiler.MAX_TOTAL_DEFINITION_CHARACTERS
			/ DefinitionCompiler.MAX_DEFINITION_CHARACTERS
			+ 1;
		for (int index = 0; index < oversizedSourceCount; index++) {
			oversizedBundleSources.add(source(DefinitionType.ROLE, "bulk_" + index, sharedLargeJson));
		}
		Compilation oversizedBundle = DefinitionCompiler.compile(
			oversizedBundleSources,
			Set.of(),
			Set.of()
		);
		checkProblem(oversizedBundle, "RESOURCE_LIMIT", "aggregate definition size must be bounded before parsing");

		DefinitionRegistry registry = new DefinitionRegistry();
		check(registry.apply(valid).applied(), "valid candidate must apply");
		DefinitionSnapshot generationOne = registry.view().active();
		check(generationOne.generation() == 1L, "first definition generation must be one");
		check(registry.view().canStartNewFlows(), "valid non-empty definitions must be healthy");

		check(!registry.apply(missingRole).applied(), "invalid candidate must not apply");
		check(registry.view().active() == generationOne, "invalid reload must retain the exact previous snapshot");
		check(registry.view().active().generation() == 1L, "invalid reload must not advance generation");
		check(!registry.view().canStartNewFlows(), "invalid latest resources must block new flows");

		check(registry.apply(valid).applied(), "corrected candidate must apply");
		check(registry.view().active() != generationOne, "corrected reload must publish a new snapshot");
		check(registry.view().active().generation() == 2L, "corrected reload must advance generation once");
		check(generationOne.games().size() == 1, "old snapshots must remain readable for pinned flows");

		Compilation empty = DefinitionCompiler.compile(List.of(), Set.of(), Set.of());
		DefinitionRegistry emptyRegistry = new DefinitionRegistry();
		check(empty.valid(), "an empty data-pack set is a valid compilation");
		check(emptyRegistry.apply(empty).applied(), "empty definitions must apply as an explicit state");
		check(!emptyRegistry.view().active().usable(), "empty definitions must not masquerade as a game");
		check(!emptyRegistry.view().canStartNewFlows(), "empty definitions must block new flows");

		check(worldState.activePhaseId().isEmpty(), "definition reload must not invent a persisted phase");
		check(worldState.hostId().isEmpty(), "definition reload must not invent a host");
		check(worldState.stateRevision() == 0L, "definition reload must not mutate core state revision");
		checkTaskDefinitionCompiler(validSources, functions, predicates);
		checkExampleDatapack();
		System.out.println("DEFINITION_REGISTRY_SELF_CHECK=PASS");
	}

	private static void checkTaskDefinitionCompiler(
		final List<Source> baseSources,
		final Set<Identifier> functions,
		final Set<Identifier> predicates
	) {
		List<Source> sources = replaceSource(
			baseSources,
			DefinitionType.GAME,
			"main",
			"""
			{
			  "format_version": 1,
			  "api_version": 2,
			  "content_version": 1,
			  "name": {"text": "Test Game"},
			  "initial_phase": "test:setup",
			  "default_role": "test:runner",
			  "default_life_state": "test:alive",
			  "task_timeline": {
			    "initial_task": "test:warmup",
			    "pause_when_host_offline": false,
			    "approval_phase": "test:setup",
			    "start_phase": "test:ready",
			    "pre_start_requirements": [{
			      "type": "required_field",
			      "audience": {
			        "roles": ["test:runner"],
			        "exclude_host": true
			      },
			      "field": "test:hunter_spawn"
			    }]
			  }
			}
			"""
		);
		sources = replaceSource(
			sources,
			DefinitionType.ROLE,
			"runner",
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Runner"},
			  "initialization_flow": "test:tutorial",
			  "tags": ["test:general_initialization", "test:ready_participant"],
			  "tab": {
			    "prefix": {"text": "[Runner]"},
			    "color": "#65D68A",
			    "sort_order": 100,
			    "prefix_mode": "replace",
			    "color_mode": "replace"
			  }
			}
			"""
		);
		sources = replaceSource(
			sources,
			DefinitionType.LIFE_STATE,
			"alive",
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "name": {"text": "Alive"},
			  "tags": ["test:active"],
			  "tab": {
			    "color": "#7BE39B",
			    "sort_order": 120,
			    "color_mode": "replace"
			  }
			}
			"""
		);
		List<Source> apiTwoSources = new ArrayList<>(sources);
		apiTwoSources.add(
			source(
				DefinitionType.FIELD,
				"hunter_spawn",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Hunter spawn"},
				  "scope": "player",
				  "type": "exclusive_choice",
				  "required": true,
				  "editable_by": "player",
				  "invalidates_ready": true,
				  "roles": ["test:runner"],
				  "phases": ["test:setup"],
				  "migration": "preserve",
				  "reservation_scope": "game_instance",
				  "release_when_role_mismatch": true,
				  "show_occupant": true,
				  "options": [
				    {
				      "value": "north_gate",
				      "name": {"text": "North gate"},
				      "description": {"text": "North deployment"},
				      "icon": "minecraft:iron_door",
				      "preview_page": "test:page/pick"
				    },
				    {
				      "value": "station",
				      "name": {"text": "Station"}
				    }
				  ]
				}
				"""
			)
		);
		apiTwoSources.add(
			source(
				DefinitionType.TASK,
				"warmup",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "kind": "warmup",
				  "name": {"text": "Warmup"},
				  "description": {"text": "Task compiler fixture"},
				  "page": "test:page/intro",
				  "audience": {
				    "roles": ["test:runner"],
				    "exclude_host": true
				  },
				  "completion_policy": "event_only",
				  "counts_toward_game_time": false,
				  "live_visibility": {
				    "future": "teaser",
				    "history": "summary",
				    "candidate_visibility": "names_only"
				  },
				  "recap_visibility": "participants",
				  "callbacks": {
				    "on_start": "test:flow/start",
				    "on_settled": "test:flow/all_complete"
				  },
				  "on_start_players": {
				    "function": "test:flow/player_complete",
				    "audience": {"roles": ["test:runner"]},
				    "fields": {"spawn_point": "test:hunter_spawn"}
				  },
				  "results": [{
				    "id": "done",
				    "name": {"text": "Done"},
				    "semantic": "success",
				    "recap": {"text": "Warmup completed"},
				    "on_apply": "test:flow/all_complete",
				    "on_apply_players": {
				      "function": "test:flow/player_complete",
				      "fields": {"spawn_point": "test:hunter_spawn"}
				    },
				    "allow_host_fallback": true,
				    "route": {
				      "end_phase": "test:ready",
				      "intermission": {
				        "duration_ticks": 20,
				        "counts_toward_game_time": false,
				        "name": {"text": "Transition"},
				        "on_complete": "test:flow/all_complete"
				      }
				    }
				  }],
				  "events": [{
				    "id": "terminal",
				    "name": {"text": "Terminal"},
				    "policy": "repeatable",
				    "max_records": 8,
				    "allowed_states": ["running", "settling"],
				    "audience": {"roles": ["test:runner"]},
				    "recap_visibility": "participants"
				  }],
				  "statistics": [{
				    "id": "rescued",
				    "name": {"text": "Rescued"},
				    "type": "integer",
				    "min": 0,
				    "max": 64,
				    "write": "increment",
				    "recap_visibility": "participants"
				  }]
				}
				"""
			)
		);
		Compilation valid = DefinitionCompiler.compile(apiTwoSources, functions, predicates);
		check(valid.valid(), "api v2 task bundle must compile: " + valid.problems());
		DefinitionSnapshot snapshot = valid.snapshot().orElseThrow();
		check(snapshot.tasks().size() == 1, "task definitions must enter the immutable snapshot");
		check(
			snapshot.games().get(Identifier.parse("test:main")).apiVersion() == 2,
			"api v2 must survive compilation"
		);
		check(
			snapshot.games().get(Identifier.parse("test:main")).taskTimeline().isPresent(),
			"task timeline must survive compilation"
		);
		check(
			!snapshot.games()
				.get(Identifier.parse("test:main"))
				.taskTimeline()
				.orElseThrow()
				.preStartRequirements()
				.getFirst()
				.audience()
				.onlineOnly(),
			"partial pre-start audiences must include frozen offline participants by default"
		);
		var warmup = snapshot.tasks().get(Identifier.parse("test:warmup"));
		check(
			!warmup.audience().onlineOnly(),
			"partial task audiences must include frozen offline participants by default"
		);
		check(
			!warmup.onStartPlayers()
				.orElseThrow()
				.audience()
				.orElseThrow()
				.onlineOnly(),
			"partial task callback audiences must default online_only to false"
		);
		check(
			!warmup.events()
				.getFirst()
				.audience()
				.orElseThrow()
				.onlineOnly(),
			"partial task event audiences must default online_only to false"
		);
		check(
			snapshot.flows()
				.get(Identifier.parse("test:tutorial"))
				.audience()
				.onlineOnly(),
			"legacy flow audiences must retain their online_only default"
		);
		check(
			snapshot.roles().get(Identifier.parse("test:runner")).initializationFlow().isPresent(),
			"role initialization flow must survive compilation"
		);
		check(
			snapshot.lifeStates().get(Identifier.parse("test:alive")).tab().sortOrder().orElseThrow() == 120,
			"life-state TAB style must survive compilation"
		);
		var spawn = snapshot.fields().get(Identifier.parse("test:hunter_spawn"));
		check(
			spawn.exclusiveChoice().orElseThrow().options().size() == 2,
			"exclusive choice option metadata must survive compilation"
		);
		check(
			snapshot.tasks().get(Identifier.parse("test:warmup")).results().containsKey("done"),
			"task result map must retain stable local ids"
		);
		List<Source> snapshotSources = new ArrayList<>(apiTwoSources);
		snapshotSources.add(
			source(
				DefinitionType.TASK,
				"unused",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "kind": "main",
				  "name": {"text": "Unreachable task"},
				  "completion_policy": "event_only",
				  "results": [{
				    "id": "done",
				    "name": {"text": "Done"},
				    "semantic": "neutral",
				    "route": {"end_phase": "test:ready"}
				  }]
				}
				"""
			)
		);
		Compilation snapshotCompilation = DefinitionCompiler.compile(
			snapshotSources,
			functions,
			predicates
		);
		check(
			snapshotCompilation.valid(),
			"timeline snapshot fixture must compile: " + snapshotCompilation.problems()
		);
		DefinitionSnapshot generationOne = snapshotCompilation.snapshot()
			.orElseThrow()
			.withGeneration(41L);
		var timelineFreeze = TimelineSnapshotCompiler.freeze(
			generationOne,
			generationOne.games().get(Identifier.parse("test:main"))
		);
		check(
			timelineFreeze.success(),
			"reachable timeline must freeze and restore: "
				+ timelineFreeze.code()
				+ " ("
				+ timelineFreeze.message()
				+ ")"
		);
		check(
			timelineFreeze.compiled().orElseThrow().tasks().keySet().equals(
				Set.of(Identifier.parse("test:warmup"))
			),
			"timeline snapshot must exclude tasks outside the initial-task reachable DAG"
		);
		var repeatedFreeze = TimelineSnapshotCompiler.freeze(
			generationOne,
			generationOne.games().get(Identifier.parse("test:main"))
		);
		check(
			repeatedFreeze.success()
				&& repeatedFreeze.frozen().orElseThrow().sha256().equals(
					timelineFreeze.frozen().orElseThrow().sha256()
				),
			"unchanged definition generations must produce deterministic timeline snapshots"
		);
		List<Source> reloadedSources = snapshotSources.stream()
			.map(
				source -> source.type() == DefinitionType.TASK
						&& source.id().equals(Identifier.parse("test:warmup"))
					? new Source(
						source.type(),
						source.id(),
						source.resource(),
						source.sourcePack(),
						source.json().replace(
							"Task compiler fixture",
							"Reloaded task compiler fixture"
						)
					)
					: source
			)
			.toList();
		Compilation reloadedCompilation = DefinitionCompiler.compile(
			reloadedSources,
			functions,
			predicates
		);
		check(reloadedCompilation.valid(), "reloaded task fixture must compile");
		check(
			reloadedCompilation.snapshot()
				.orElseThrow()
				.tasks()
				.get(Identifier.parse("test:warmup"))
				.description()
				.orElseThrow()
				.plainText()
				.startsWith("Reloaded"),
			"reload fixture must actually change the live task definition"
		);
		var restoredTimeline = TimelineSnapshotCompiler.restore(
			Identifier.parse("test:main"),
			timelineFreeze.frozen().orElseThrow()
		);
		check(
			restoredTimeline.success()
				&& restoredTimeline.snapshot().orElseThrow().generation() == 41L
				&& restoredTimeline.snapshot()
					.orElseThrow()
					.tasks()
					.get(Identifier.parse("test:warmup"))
					.description()
					.orElseThrow()
					.plainText()
					.equals("Task compiler fixture"),
			"reload must not replace the definition generation frozen for an active timeline"
		);
		List<Source> exclusiveFlowSources = apiTwoSources.stream()
			.map(source -> {
				String json = source.json();
				if (
					source.type() == DefinitionType.FIELD
						&& source.id().equals(Identifier.parse("test:hunter_spawn"))
				) {
					json = json.replace("test:page/pick", "test:spawn/preview");
				} else if (
					source.type() == DefinitionType.FLOW
						&& source.id().equals(Identifier.parse("test:tutorial"))
				) {
					json = json.replace("test:lane", "test:hunter_spawn")
						.replace("\"alpha\"", "\"north_gate\"")
						.replace("\"beta\"", "\"station\"");
				} else if (
					source.type() == DefinitionType.PAGE
						&& source.id().equals(Identifier.parse("test:page/pick"))
				) {
					json = json.replace("test:lane", "test:hunter_spawn");
				}
				return json.equals(source.json())
					? source
					: new Source(
						source.type(),
						source.id(),
						source.resource(),
						source.sourcePack(),
						json
					);
			})
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		exclusiveFlowSources.add(
			source(
				DefinitionType.PAGE,
				"spawn/preview",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Spawn preview"},
				  "root": {
				    "type": "text",
				    "text": {"text": "North deployment preview"}
				  }
				}
				"""
			)
		);
		Compilation exclusiveFlowCompilation = DefinitionCompiler.compile(
			exclusiveFlowSources,
			functions,
			predicates
		);
		check(
			exclusiveFlowCompilation.valid(),
			"exclusive-choice flow fixture must compile: " + exclusiveFlowCompilation.problems()
		);
		DefinitionSnapshot exclusiveFlowSnapshot = exclusiveFlowCompilation.snapshot()
			.orElseThrow()
			.withGeneration(42L);
		var exclusiveFlowFreeze = ExecutionSnapshotCompiler.freeze(
			exclusiveFlowSnapshot,
			exclusiveFlowSnapshot.flows().get(Identifier.parse("test:tutorial")),
			exclusiveFlowSnapshot.panelActions().get(Identifier.parse("test:assign_hunter"))
		);
		check(
			exclusiveFlowFreeze.success()
				&& exclusiveFlowFreeze.frozen()
					.orElseThrow()
					.pages()
					.containsKey(Identifier.parse("test:spawn/preview")),
			"execution snapshot must retain exclusive-choice preview pages: "
				+ exclusiveFlowFreeze.message()
		);

		List<Source> cycleSources = new ArrayList<>(apiTwoSources);
		cycleSources.removeIf(
			source -> source.type() == DefinitionType.TASK
				&& source.id().equals(Identifier.parse("test:warmup"))
		);
		cycleSources.add(
			source(
				DefinitionType.TASK,
				"warmup",
				taskCycleJson("test:second")
			)
		);
		cycleSources.add(
			source(
				DefinitionType.TASK,
				"second",
				taskCycleJson("test:warmup")
			)
		);
		checkProblem(
			DefinitionCompiler.compile(cycleSources, functions, predicates),
			"INVALID_GRAPH",
			"task route cycles must fail closed"
		);
		checkProblem(
			DefinitionCompiler.compile(
				replaceSource(
					apiTwoSources,
					DefinitionType.TASK,
					"warmup",
					"""
					{
					  "format_version": 1,
					  "game": "test:main",
					  "version": 1,
					  "kind": "warmup",
					  "name": {"text": "Skipped"},
					  "completion_policy": "event_only",
					  "results": [{
					    "id": "skipped",
					    "name": {"text": "Skipped"},
					    "semantic": "neutral",
					    "route": {"end_phase": "test:ready"}
					  }]
					}
					"""
				),
				functions,
				predicates
			),
			"INVALID_COMBINATION",
			"skipped task results must fail closed"
		);

		List<Source> apiOneSources = replaceSource(
			apiTwoSources,
			DefinitionType.GAME,
			"main",
			"""
			{
			  "format_version": 1,
			  "api_version": 1,
			  "content_version": 1,
			  "name": {"text": "Test Game"},
			  "initial_phase": "test:setup",
			  "default_role": "test:runner",
			  "default_life_state": "test:alive"
			}
			"""
		);
		checkProblem(
			DefinitionCompiler.compile(apiOneSources, functions, predicates),
			"UNSUPPORTED_API",
			"tasks and exclusive choices must require api v2"
		);
	}

	private static String taskCycleJson(final String nextTask) {
		return """
			{
			  "format_version": 1,
			  "game": "test:main",
			  "version": 1,
			  "kind": "main",
			  "name": {"text": "Cycle"},
			  "completion_policy": "event_only",
			  "results": [{
			    "id": "done",
			    "name": {"text": "Done"},
			    "semantic": "neutral",
			    "route": {"next_task": "%s"}
			  }]
			}
			""".formatted(nextTask);
	}

	private static void checkUiDefinitionCompiler(
		final List<Source> validSources,
		final Set<Identifier> functions,
		final Set<Identifier> predicates,
		final DefinitionSnapshot compiled
	) {
		var intro = compiled.pages().get(Identifier.parse("test:page/intro"));
		check(intro != null, "compiled intro page is missing");
		check(intro.sha256().length() == 64, "page SHA-256 must be a 64-character hex digest");
		check(
			intro.canonicalDocument().startsWith("{\"format_version\":"),
			"canonical page document must sort object keys"
		);
		if (intro.root().content() instanceof io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ChildrenContent children) {
			expectUnsupported(children.children()::clear, "page child lists must be immutable");
		} else {
			throw new AssertionError("intro root must compile as a multi-child container");
		}
		expectUnsupported(intro.assets()::clear, "page asset lists must be immutable");

		Compilation reorderedPage = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "root": {
				    "children": [
				      {
				        "text": {
				          "concat": [
				            {"text": "Player: "},
				            {"bind": "viewer.name"}
				          ]
				        },
				        "type": "text"
				      },
				      {
				        "action": {"name": "continue", "type": "flow"},
				        "label": {"text": "Continue"},
				        "id": "continue",
				        "type": "button"
				      }
				    ],
				    "layout": {
				      "gap": 8,
				      "height": {"mode": "fill"},
				      "width": {"mode": "fill"}
				    },
				    "id": "intro_root",
				    "type": "column"
				  },
				  "theme": "test:control_console",
				  "title": {"text": "Intro"},
				  "game": "test:main",
				  "format_version": 1
				}
				"""
			),
			functions,
			predicates
		);
		check(reorderedPage.valid(), "reordered page must compile: " + reorderedPage.problems());
		var reorderedIntro = reorderedPage.snapshot().orElseThrow().pages().get(Identifier.parse("test:page/intro"));
		check(
			reorderedIntro.canonicalDocument().equals(intro.canonicalDocument()),
			"canonical page document must ignore JSON object key order"
		);
		check(reorderedIntro.sha256().equals(intro.sha256()), "canonical page hash must be stable");

		Compilation unknownNodeKey = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Unknown key"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "text",
				    "text": {"text": "Hello"},
				    "unexpected": true
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(unknownNodeKey, "UNKNOWN_KEY", "unknown page node keys must fail closed");

		Compilation duplicateNodeId = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Duplicate IDs"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "column",
				    "id": "duplicate",
				    "children": [
				      {"type": "spacer", "id": "duplicate"}
				    ]
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(duplicateNodeId, "DUPLICATE_NODE_ID", "duplicate page node IDs must be rejected");

		Compilation actionWithoutNodeId = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Missing action source"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "button",
				    "label": {"text": "Continue"},
				    "action": {"type": "flow", "name": "continue"}
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			actionWithoutNodeId,
			"MISSING_KEY",
			"action buttons must have a stable node id"
		);

		Compilation missingTheme = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Missing theme"},
				  "theme": "test:missing_theme",
				  "root": {"type": "text", "text": {"text": "Hello"}}
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(missingTheme, "MISSING_REFERENCE", "missing page themes must be rejected");

		String progressMotionTheme = """
			{
			  "format_version": 1,
			  "tokens": {
			    "colors": {"brand": "#F4C95D"},
			    "spacing": {},
			    "fonts": {}
			  },
			  "styles": {},
			  "motions": {
			    "progress_reveal": {
			      "duration_ms": 100,
			      "delay_ms": 0,
			      "easing": "linear",
			      "tracks": [{
			        "property": "progress_value",
			        "keyframes": [
			          {"at": 0.0, "value": 0.0},
			          {"at": 1.0, "value": 1.0}
			        ]
			      }],
			      "reduced_motion": "final"
			    }
			  },
			  "sound_cues": {}
			}
			""";
		List<Source> withProgressMotion = replaceSource(
			validSources,
			DefinitionType.THEME,
			"control_console",
			progressMotionTheme
		);
		Compilation progressMotionOnText = DefinitionCompiler.compile(
			replaceSource(
				withProgressMotion,
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Invalid progress motion"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "text",
				    "text": {"text": "Not a progress bar"},
				    "events": {
				      "show": {"motion": "progress_reveal"}
				    }
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			progressMotionOnText,
			"TYPE_MISMATCH",
			"progress_value motion tracks must only target progress nodes"
		);

		Compilation wrongMotionTokenGroup = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.THEME,
				"control_console",
				motionColorTheme("@spacing.small")
			),
			functions,
			predicates
		);
		checkProblem(
			wrongMotionTokenGroup,
			"TYPE_MISMATCH",
			"motion colors must only accept color tokens"
		);

		Compilation missingMotionColor = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.THEME,
				"control_console",
				motionColorTheme("@colors.missing")
			),
			functions,
			predicates
		);
		checkProblem(
			missingMotionColor,
			"MISSING_REFERENCE",
			"motion color token references must exist"
		);

		List<Source> withoutIntroPage = validSources.stream()
			.filter(
				source -> source.type() != DefinitionType.PAGE
					|| !source.id().equals(Identifier.parse("test:page/intro"))
			)
			.toList();
		Compilation missingFlowPage = DefinitionCompiler.compile(withoutIntroPage, functions, predicates);
		checkProblem(missingFlowPage, "MISSING_REFERENCE", "flow nodes must reference an existing page");

		Compilation incompatibleFieldInput = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/pick",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Wrong field input"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "field_input",
				    "id": "lane",
				    "field": "test:lane",
				    "presentation": "slider",
				    "show_label": true,
				    "show_description": true
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			incompatibleFieldInput,
			"TYPE_MISMATCH",
			"FieldInput presentation must match the registered field type"
		);

		Compilation repeatedFieldInput = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/pick",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Repeated input"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "repeat",
				    "items": {"bind": "session.players"},
				    "item_key": {"bind": "item.uuid"},
				    "template": {
				      "type": "field_input",
				      "id": "lane",
				      "field": "test:lane",
				      "presentation": "choice_cards"
				    }
				  }
				}
				"""
			),
			functions,
			predicates
		);
		checkProblem(
			repeatedFieldInput,
			"UNSUPPORTED_COMBINATION",
			"FieldInput inside Repeat must fail until item-scoped UI state exists"
		);

		StringBuilder excessiveNodesPage = new StringBuilder(
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "title": {"text": "Too many nodes"},
			  "theme": "test:control_console",
			  "root": {"type": "column", "children": [
			"""
		);
		for (int group = 0; group < 4; group++) {
			if (group > 0) {
				excessiveNodesPage.append(',');
			}
			excessiveNodesPage.append("{\"type\":\"column\",\"children\":[");
			for (int index = 0; index < 128; index++) {
				if (index > 0) {
					excessiveNodesPage.append(',');
				}
				excessiveNodesPage.append("{\"type\":\"spacer\"}");
			}
			excessiveNodesPage.append("]}");
		}
		excessiveNodesPage.append("]}}");
		Compilation excessiveNodes = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				excessiveNodesPage.toString()
			),
			functions,
			predicates
		);
		checkProblem(excessiveNodes, "RESOURCE_LIMIT", "page node count must be bounded");

		StringBuilder excessiveUtf8Page = new StringBuilder(
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "title": {"text": "UTF-8 limit"},
			  "theme": "test:control_console",
			  "root": {"type": "column", "children": [
			"""
		);
		for (int group = 0; group < 2; group++) {
			if (group > 0) {
				excessiveUtf8Page.append(',');
			}
			excessiveUtf8Page.append("{\"type\":\"column\",\"children\":[");
			for (int index = 0; index < 128; index++) {
				if (index > 0) {
					excessiveUtf8Page.append(',');
				}
				excessiveUtf8Page.append("{\"type\":\"text\",\"text\":{\"text\":\"")
					.append("中".repeat(400))
					.append("\"}}");
			}
			excessiveUtf8Page.append("]}");
		}
		excessiveUtf8Page.append("]}}");
		Compilation excessiveUtf8 = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				excessiveUtf8Page.toString()
			),
			functions,
			predicates
		);
		checkProblem(
			excessiveUtf8,
			"RESOURCE_LIMIT",
			"canonical UTF-8 bytes must fit the page payload limit"
		);

		StringBuilder excessiveExpressionPage = new StringBuilder(
			"""
			{
			  "format_version": 1,
			  "game": "test:main",
			  "title": {"text": "Too many expressions"},
			  "theme": "test:control_console",
			  "root": {
			    "type": "button",
			    "id": "continue",
			    "label": {"text": "Continue"},
			    "enabled_when": {"any": [
			"""
		);
		for (int index = 0; index < 40; index++) {
			if (index > 0) {
				excessiveExpressionPage.append(',');
			}
			excessiveExpressionPage.append("{\"exists\":{\"bind\":\"viewer.name\"}}");
		}
		excessiveExpressionPage.append(
			"""
			    ]},
			    "disabled_reason": {"text": "Unavailable"},
			    "action": {"type": "flow", "name": "continue"}
			  }
			}
			"""
		);
		Compilation excessiveExpression = DefinitionCompiler.compile(
			replaceSource(
				validSources,
				DefinitionType.PAGE,
				"page/intro",
				excessiveExpressionPage.toString()
			),
			functions,
			predicates
		);
		checkProblem(excessiveExpression, "RESOURCE_LIMIT", "condition expression size must be bounded");
	}

	private static void checkExampleDatapack() {
		Path packRoot = Path.of("examples", "pixel-tzz-base-datapack");
		check(Files.isDirectory(packRoot), "2D example data pack is missing");
		long fileCount;
		try (var paths = Files.walk(packRoot)) {
			fileCount = paths.filter(Files::isRegularFile).count();
			check(fileCount == 70, "2D example data pack file count changed unexpectedly");
		} catch (IOException error) {
			throw new AssertionError("failed to count the 2D example data pack", error);
		}
		Path root = packRoot.resolve("data");
		check(Files.isDirectory(root), "2D example data pack data root is missing");
		List<Source> sources = new ArrayList<>();
		Set<Identifier> functions = new HashSet<>();
		Set<Identifier> predicates = new HashSet<>();
		try (var paths = Files.walk(root)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				Path relative = root.relativize(file);
				check(relative.getNameCount() >= 3, "invalid example data path: " + relative);
				String namespace = relative.getName(0).toString();
				String rootDirectory = relative.getName(1).toString();
				String relativeFile = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
				if (rootDirectory.equals("function") && relativeFile.endsWith(".mcfunction")) {
					functions.add(
						Identifier.fromNamespaceAndPath(
							namespace,
							relativeFile.substring(0, relativeFile.length() - ".mcfunction".length())
						)
					);
					continue;
				}
				if (rootDirectory.equals("predicate") && relativeFile.endsWith(".json")) {
					predicates.add(
						Identifier.fromNamespaceAndPath(
							namespace,
							relativeFile.substring(0, relativeFile.length() - ".json".length())
						)
					);
					continue;
				}
				check(rootDirectory.equals("pixel_tzz_pro"), "unexpected example data root: " + relative);
				check(relative.getNameCount() >= 4, "definition path has no type: " + relative);
				String typeDirectory = relative.getName(2).toString();
				DefinitionType type = DefinitionType.byDirectory(typeDirectory)
					.orElseThrow(() -> new AssertionError("unknown example definition type: " + typeDirectory));
				String definitionFile = relative.subpath(3, relative.getNameCount()).toString().replace('\\', '/');
				check(definitionFile.endsWith(".json"), "definition is not JSON: " + relative);
				String definitionPath = definitionFile.substring(0, definitionFile.length() - ".json".length());
				sources.add(
					new Source(
						type,
						Identifier.fromNamespaceAndPath(namespace, definitionPath),
						Identifier.fromNamespaceAndPath(
							namespace,
							"pixel_tzz_pro/" + typeDirectory + "/" + definitionFile
						),
						"example",
						Files.readString(file, StandardCharsets.UTF_8)
					)
				);
			}
		} catch (IOException error) {
			throw new AssertionError("failed to read the 2D example data pack", error);
		}

		Compilation example = DefinitionCompiler.compile(sources, functions, predicates);
		check(example.valid(), "2D example data pack must compile: " + example.problems());
		DefinitionSnapshot snapshot = example.snapshot().orElseThrow();
		check(snapshot.games().size() == 1, "example must register one game");
		check(snapshot.fields().size() == 2, "example must register two fields");
		check(snapshot.pages().size() == 12, "example must register preview, fixture, and readiness pages");
		check(snapshot.tasks().size() == 3, "example must register the three-task 2D timeline");
		check(snapshot.themes().size() == 1, "example must register one theme");
		check(snapshot.definitionCount() == 42, "example definition count changed unexpectedly");
		check(functions.size() == 26, "example callback and wrapper function count changed unexpectedly");
		Identifier gameId = Identifier.fromNamespaceAndPath("pixel_tzz", "main");
		Identifier warmupTaskId = Identifier.fromNamespaceAndPath("pixel_tzz", "acceptance/warmup");
		Identifier branchTaskId = Identifier.fromNamespaceAndPath("pixel_tzz", "acceptance/main_branch");
		Identifier timeoutTaskId = Identifier.fromNamespaceAndPath("pixel_tzz", "acceptance/timeout_only");
		Identifier warmupPhaseId = Identifier.fromNamespaceAndPath("pixel_tzz", "warmup");
		Identifier runningPhaseId = Identifier.fromNamespaceAndPath("pixel_tzz", "running");
		Identifier endedPhaseId = Identifier.fromNamespaceAndPath("pixel_tzz", "ended");
		var game = snapshot.games().get(gameId);
		check(
			game.apiVersion() == 2
				&& game.taskTimeline().isPresent()
				&& game.readiness().isPresent(),
			"2D example must opt into API v2 tasks and player readiness"
		);
		var timeline = game.taskTimeline().orElseThrow();
		check(
			timeline.initialTask().equals(warmupTaskId)
				&& timeline.approvalPhase().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "ready"))
				&& timeline.startPhase().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "warmup")),
			"2D example timeline entry and approval phases changed unexpectedly"
		);
		var warmupTask = snapshot.tasks().get(warmupTaskId);
		var branchTask = snapshot.tasks().get(branchTaskId);
		var timeoutTask = snapshot.tasks().get(timeoutTaskId);
		check(
			warmupTask.results().get("ready").route().nextTask().orElseThrow().equals(branchTaskId)
				&& branchTask.results().get("success").route().nextTask().orElseThrow().equals(timeoutTaskId)
				&& branchTask.results().get("failure").route().endPhase().orElseThrow().equals(endedPhaseId)
				&& branchTask.results().get("timeout").route().endPhase().orElseThrow().equals(endedPhaseId)
				&& timeoutTask.results().get("finished").route().endPhase().orElseThrow().equals(endedPhaseId),
			"2D example task DAG must retain its warmup, success branch, and terminal routes"
		);
		check(
			snapshot.phases()
				.get(warmupPhaseId)
				.onExit()
				.orElseThrow()
				.equals(Identifier.fromNamespaceAndPath("pixel_tzz", "acceptance_2d/phase/warmup_exit"))
				&& snapshot.phases()
					.get(runningPhaseId)
					.onEnter()
					.orElseThrow()
					.equals(Identifier.fromNamespaceAndPath("pixel_tzz", "acceptance_2d/phase/running_enter")),
			"warmup -> running must retain observable on_exit -> on_enter acceptance callbacks"
		);
		var hunterRole = snapshot.roles().get(Identifier.fromNamespaceAndPath("pixel_tzz", "hunter"));
		var runnerRole = snapshot.roles().get(Identifier.fromNamespaceAndPath("pixel_tzz", "runner"));
		var spectatorRole = snapshot.roles().get(Identifier.fromNamespaceAndPath("pixel_tzz", "spectator"));
		check(
			hunterRole.tab().sortOrder().orElseThrow() < runnerRole.tab().sortOrder().orElseThrow()
				&& runnerRole.tab().sortOrder().orElseThrow() < spectatorRole.tab().sortOrder().orElseThrow(),
			"example TAB roles must sort hunter -> runner -> spectator after the host"
		);
		check(
			hunterRole.tab().prefix().orElseThrow().plainText().endsWith(" ")
				&& runnerRole.tab().prefix().orElseThrow().plainText().endsWith(" ")
				&& spectatorRole.tab().prefix().orElseThrow().plainText().endsWith(" "),
			"example TAB role prefixes must retain a readable separator before player names"
		);
		Identifier hunterSpawnFieldId = Identifier.fromNamespaceAndPath("pixel_tzz", "hunter_spawn");
		var hunterSpawnField = snapshot.fields().get(hunterSpawnFieldId);
		check(
			hunterSpawnField.exclusiveChoice().orElseThrow().values().equals(
				List.of("north_gate", "central_station", "south_yard")
			),
			"hunter spawn must remain a three-option exclusive field"
		);
		List<NodeDefinition> hunterSpawnNodes = new ArrayList<>();
		collectNodes(
			snapshot.pages()
				.get(Identifier.fromNamespaceAndPath("pixel_tzz", "fixture/hunter/spawn"))
				.root(),
			hunterSpawnNodes
		);
		NodeDefinition spawnScroll = hunterSpawnNodes.stream()
			.filter(node -> node.id().filter("hunter_spawn_scroll"::equals).isPresent())
			.findFirst()
			.orElseThrow();
		NodeDefinition spawnIntroContent = hunterSpawnNodes.stream()
			.filter(node -> node.id().filter("hunter_spawn_intro_content"::equals).isPresent())
			.findFirst()
			.orElseThrow();
		NodeDefinition spawnSelectionContent = hunterSpawnNodes.stream()
			.filter(node -> node.id().filter("hunter_spawn_selection_content"::equals).isPresent())
			.findFirst()
			.orElseThrow();
		check(
			spawnIntroContent.layout().width().orElseThrow().mode() == SizeMode.FILL
				&& spawnSelectionContent.layout().width().orElseThrow().mode() == SizeMode.FILL,
			"hunter spawn card content must fill the card so labels and choices use the full row"
		);
		check(
			spawnScroll.content() instanceof SingleChildContent scroll
				&& scroll.showScrollbar()
				&& scroll.child().content() instanceof FieldInputContent input
				&& !input.showLabel()
				&& !input.showDescription()
				&& scroll.child().layout().width().orElseThrow().mode() == SizeMode.FILL
				&& spawnScroll.layout().height().orElseThrow().mode() == SizeMode.FIXED
				&& spawnScroll.layout().height().orElseThrow().value().orElseThrow() == 48.0,
			"hunter spawn scroll must contain only a full-width, one-row choice-card viewport"
		);
		check(
			branchTask.onStartPlayers().orElseThrow().function().equals(
				Identifier.fromNamespaceAndPath("pixel_tzz", "acceptance_2d/timeline/deploy_hunter")
			)
				&& branchTask.onStartPlayers().orElseThrow().fields().equals(
					Map.of("spawn_point", hunterSpawnFieldId)
				),
			"main task must bind each hunter's frozen spawn field into its start callback"
		);
		var enterInitializingAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "enter_initializing"));
		check(
			enterInitializingAction.operation() instanceof TransitionPhaseOperation transition
				&& transition.phase().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "initializing")),
			"enter_initializing must remain an explicit transition into the initialization phase"
		);
		check(
			snapshot.pages().containsKey(Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/buttons")),
			"example must expose the button component showcase"
		);
		check(
			snapshot.themes()
				.get(Identifier.fromNamespaceAndPath("pixel_tzz", "control_console"))
				.styles()
				.keySet()
				.containsAll(
					Set.of(
						"primary_button",
						"secondary_button",
						"navigation_button",
						"danger_button"
					)
				),
			"example theme must expose every supported button style"
		);
		List<NodeDefinition> exampleNodes = new ArrayList<>();
		snapshot.pages().values().forEach(page -> collectNodes(page.root(), exampleNodes));
		List<NodeDefinition> exampleButtons = exampleNodes.stream()
			.filter(node -> node.content() instanceof ButtonContent)
			.toList();
		check(
			exampleButtons.stream()
				.noneMatch(
					node -> node.events().containsKey(UiEvent.HOVER_ENTER)
						&& node.events().get(UiEvent.HOVER_ENTER).sound().isPresent()
				),
			"example buttons must keep hover feedback silent"
		);
		List<NodeDefinition> showcaseNodes = new ArrayList<>();
		collectNodes(
			snapshot.pages()
				.get(Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/buttons"))
				.root(),
			showcaseNodes
		);
		check(
			showcaseNodes.stream()
				.filter(node -> node.content() instanceof ButtonContent)
				.map(node -> node.style().orElse(""))
				.collect(java.util.stream.Collectors.toSet())
				.containsAll(
					Set.of(
						"primary_button",
						"secondary_button",
						"navigation_button",
						"danger_button"
					)
				),
			"button showcase must render every semantic button style"
		);
		check(
			showcaseNodes.stream()
				.filter(node -> node.style().orElse("").startsWith("button_specimen_"))
				.allMatch(
					node -> node.content() instanceof SingleChildContent single
						&& single.child().layout().height().isEmpty()
				),
			"button specimen content must leave an inset below its button"
		);
		System.out.printf(
			"EXAMPLE_DATAPACK_COUNTS files=%d definitions=%d pages=%d fields=%d tasks=%d phases=%d functions=%d%n",
			fileCount,
			snapshot.definitionCount(),
			snapshot.pages().size(),
			snapshot.fields().size(),
			snapshot.tasks().size(),
			snapshot.phases().size(),
			functions.size()
		);

		Set<Identifier> previewPages = Set.of(
			Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/welcome"),
			Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/buttons"),
			Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/rules"),
			Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/acknowledge"),
			Identifier.fromNamespaceAndPath("pixel_tzz", "hunter/briefing"),
			Identifier.fromNamespaceAndPath("pixel_tzz", "hunter/acknowledge")
		);
		check(
			snapshot.pages().keySet().containsAll(previewPages),
			"the six 2B component pages must remain available as preview-only definitions"
		);
		Identifier generalFlowId = Identifier.fromNamespaceAndPath("pixel_tzz", "general_tutorial");
		Identifier hunterFlowId = Identifier.fromNamespaceAndPath("pixel_tzz", "hunter_initialization");
		var generalFlow = snapshot.flows().get(generalFlowId);
		var hunterFlow = snapshot.flows().get(hunterFlowId);
		check(
			generalFlow.version() == 2 && generalFlow.entry().equals("briefing") && generalFlow.nodes().size() == 3,
			"general 2C fixture must be a versioned briefing -> confirm -> complete flow"
		);
		check(
			generalFlow.audience().roles().equals(
				Set.of(Identifier.fromNamespaceAndPath("pixel_tzz", "runner"))
			)
				&& generalFlow.audience().roleTags().isEmpty()
				&& generalFlow.audience().excludeHost()
				&& generalFlow.audience().onlineOnly(),
			"general initialization must only auto-select online non-host runners"
		);
		check(
			generalFlow.nodes().get("briefing") instanceof PageNode briefing
				&& briefing.page().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "fixture/general/briefing"))
				&& briefing.next().equals("confirm"),
			"general 2C fixture briefing page reference changed unexpectedly"
		);
		check(
			generalFlow.nodes().get("confirm") instanceof ConfirmNode confirmation
				&& confirmation.page().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "fixture/general/confirm"))
				&& confirmation.next().equals("done"),
			"general 2C fixture confirmation page reference changed unexpectedly"
		);
		check(
			hunterFlow.version() == 3 && hunterFlow.entry().equals("briefing") && hunterFlow.nodes().size() == 4,
			"hunter 2D fixture must be a versioned briefing -> choice -> confirm -> complete flow"
		);
		check(
			hunterFlow.nodes().get("briefing") instanceof PageNode briefing
				&& briefing.page().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "fixture/hunter/briefing"))
				&& briefing.next().equals("choose_spawn"),
			"hunter 2D fixture briefing page reference changed unexpectedly"
		);
		check(
			hunterFlow.nodes().get("choose_spawn") instanceof ChoiceNode choice
				&& choice.page().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "fixture/hunter/spawn"))
				&& choice.field().equals(hunterSpawnFieldId)
				&& choice.choices().stream().map(route -> route.value()).toList().equals(
					List.of("north_gate", "central_station", "south_yard")
				)
				&& choice.choices().stream().allMatch(route -> route.next().equals("confirm")),
			"hunter 2D fixture must route all three exclusive spawn choices into confirmation"
		);
		check(
			hunterFlow.nodes().get("confirm") instanceof ConfirmNode confirmation
				&& confirmation.page().equals(Identifier.fromNamespaceAndPath("pixel_tzz", "fixture/hunter/confirm"))
				&& confirmation.next().equals("done")
				&& confirmation.back().filter("choose_spawn"::equals).isPresent(),
			"hunter 2D fixture confirmation page reference changed unexpectedly"
		);
		check(
			hunterFlow.nodes().get("done") instanceof CompleteNode,
			"hunter 2D fixture must terminate after confirmation"
		);
		check(
			snapshot.flows()
				.values()
				.stream()
				.flatMap(flow -> flow.nodes().values().stream())
				.noneMatch(
					node -> node instanceof PageNode page && previewPages.contains(page.page())
						|| node instanceof ConfirmNode confirmation && previewPages.contains(confirmation.page())
				),
			"2B component pages must not be used as executable 2C flow pages"
		);
		var generalAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "start_general_initialization"));
		check(
			generalAction.operation() instanceof StartFlowOperation start
				&& start.flow().equals(generalFlowId)
				&& start.completionPolicy() == CompletionPolicy.IF_INCOMPLETE,
			"general fixture action must explicitly use if_incomplete"
		);
		var hunterAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "assign_hunter"));
		check(
			hunterAction.operation() instanceof AssignRoleOperation assign
				&& assign.flow().orElseThrow().equals(hunterFlowId)
				&& assign.completionPolicy() == CompletionPolicy.ALWAYS,
			"hunter fixture action must explicitly use always"
		);
		check(
			hunterAction.target().filter().roles().equals(
				Set.of(Identifier.fromNamespaceAndPath("pixel_tzz", "runner"))
			),
			"assign hunter must only target current runners"
		);
		var reinitializeHunterAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "reinitialize_hunter"));
		check(
			reinitializeHunterAction.target().filter().roles().equals(
				Set.of(Identifier.fromNamespaceAndPath("pixel_tzz", "hunter"))
			)
				&& reinitializeHunterAction.operation() instanceof AssignRoleOperation assign
				&& assign.flow().orElseThrow().equals(hunterFlowId)
				&& assign.completionPolicy() == CompletionPolicy.ALWAYS,
			"reinitialize hunter must only target current hunters"
		);
		var spectatorAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "set_spectator"));
		check(
			spectatorAction.target().filter().roles().equals(
				Set.of(
					Identifier.fromNamespaceAndPath("pixel_tzz", "runner"),
					Identifier.fromNamespaceAndPath("pixel_tzz", "hunter")
				)
			)
				&& spectatorAction.target().filter().completedFlows().isEmpty()
				&& spectatorAction.target().filter().incompleteFlows().isEmpty()
				&& spectatorAction.operation() instanceof AssignRoleOperation assign
				&& assign.apply() == ApplyTiming.IMMEDIATE,
			"set spectator must accept uninitialized participants and apply immediately"
		);
		var restoreRunnerAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "restore_runner"));
		check(
			restoreRunnerAction.target().filter().roles().equals(
				Set.of(
					Identifier.fromNamespaceAndPath("pixel_tzz", "hunter"),
					Identifier.fromNamespaceAndPath("pixel_tzz", "spectator")
				)
			)
				&& restoreRunnerAction.target().filter().completedFlows().equals(Set.of(generalFlowId))
				&& restoreRunnerAction.operation() instanceof AssignRoleOperation assign
				&& assign.apply() == ApplyTiming.IMMEDIATE,
			"initialized hunters and spectators must have an immediate runner restore path"
		);
		var initializeRunnerAction = snapshot.panelActions()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "initialize_runner"));
		check(
			initializeRunnerAction.target().filter().roles().equals(
				Set.of(
					Identifier.fromNamespaceAndPath("pixel_tzz", "hunter"),
					Identifier.fromNamespaceAndPath("pixel_tzz", "spectator")
				)
			)
				&& initializeRunnerAction.target().filter().incompleteFlows().equals(Set.of(generalFlowId))
				&& initializeRunnerAction.operation() instanceof AssignRoleOperation assign
				&& assign.flow().orElseThrow().equals(generalFlowId)
				&& assign.apply() == ApplyTiming.AFTER_FLOW
				&& assign.completionPolicy() == CompletionPolicy.IF_INCOMPLETE,
			"uninitialized hunters and spectators must complete initialization before runner restore"
		);
		DefinitionSnapshot fixtureSnapshot = snapshot.withGeneration(8L);
		var generalFreeze = ExecutionSnapshotCompiler.freeze(fixtureSnapshot, generalFlow, generalAction);
		check(
			generalFreeze.success(),
			"general 2D fixture must satisfy the executable page contract: "
				+ generalFreeze.code()
				+ " at "
				+ generalFreeze.nodeId()
				+ " ("
				+ generalFreeze.message()
				+ ")"
		);
		check(
			generalFreeze.compiled().orElseThrow().panelActions().containsKey(generalAction.id()),
			"restored execution snapshot must retain its source panel action"
		);
		var hunterFreeze = ExecutionSnapshotCompiler.freeze(
			fixtureSnapshot,
			hunterFlow,
			hunterAction
		);
		check(
			hunterFreeze.success(),
			"hunter 2C fixture must satisfy the executable page contract: "
				+ hunterFreeze.code()
				+ " at "
				+ hunterFreeze.nodeId()
				+ " ("
				+ hunterFreeze.message()
				+ ")"
		);
		Identifier referencePredicate = Identifier.fromNamespaceAndPath(
			"pixel_tzz",
			"fixture/live_reference"
		);
		Source originalActionSource = sources.stream()
			.filter(
				source -> source.type() == DefinitionType.PANEL_ACTION
					&& source.id().equals(generalAction.id())
			)
			.findFirst()
			.orElseThrow();
		var referenceActionJson = JsonParser.parseString(originalActionSource.json())
			.getAsJsonObject();
		referenceActionJson.addProperty("visible_when", referencePredicate.toString());
		List<Source> referenceSources = new ArrayList<>(sources);
		referenceSources.remove(originalActionSource);
		referenceSources.add(
			new Source(
				originalActionSource.type(),
				originalActionSource.id(),
				originalActionSource.resource(),
				originalActionSource.sourcePack(),
				referenceActionJson.toString()
			)
		);
		Set<Identifier> referencePredicates = new HashSet<>(predicates);
		referencePredicates.add(referencePredicate);
		Compilation referenceCompilation = DefinitionCompiler.compile(
			referenceSources,
			functions,
			referencePredicates
		);
		check(referenceCompilation.valid(), "reference predicate fixture must compile");
		DefinitionSnapshot referenceSnapshot = referenceCompilation.snapshot()
			.orElseThrow()
			.withGeneration(9L)
			.withPredicateDocuments(
				Map.of(
					referencePredicate,
					"""
					{
					  "condition": "minecraft:inverted",
					  "term": {
					    "condition": "minecraft:reference",
					    "name": "pixel_tzz:fixture/live_target"
					  }
					}
					"""
				)
			);
		var referenceFreeze = ExecutionSnapshotCompiler.freeze(
			referenceSnapshot,
			referenceSnapshot.flows().get(generalFlowId),
			referenceSnapshot.panelActions().get(generalAction.id())
		);
		check(
			!referenceFreeze.success()
				&& referenceFreeze.code().equals("snapshot_invalid")
				&& referenceFreeze.message().contains("minecraft:reference"),
			"execution snapshot must reject predicates that escape the frozen closure"
		);

		NodeDefinition rulesRoot = snapshot.pages()
			.get(Identifier.fromNamespaceAndPath("pixel_tzz", "tutorial/rules"))
			.root();
		NodeDefinition rulesColumns = ((ChildrenContent)rulesRoot.content()).children()
			.stream()
			.filter(node -> node.id().filter("responsive_columns"::equals).isPresent())
			.findFirst()
			.orElseThrow();
		check(
			rulesColumns.layout().height().orElseThrow().mode() == SizeMode.CONTENT,
			"rule cards must use content-driven row height"
		);
		for (NodeDefinition card : ((ChildrenContent)rulesColumns.content()).children()) {
			check(
				card.layout().width().orElseThrow().mode() == SizeMode.WEIGHT
					&& card.layout().height().orElseThrow().mode() == SizeMode.CONTENT,
				"standard rule cards must share row width without consuming spare page height"
			);
			var compact = card.responsive().get(ResponsiveTier.COMPACT);
			check(
				compact != null
					&& compact.layout().orElseThrow().width().orElseThrow().mode() == SizeMode.FILL
					&& compact.layout().orElseThrow().height().orElseThrow().mode() == SizeMode.CONTENT,
				"compact rule cards must fill the column width and keep content-driven height"
			);
		}

		Identifier setupPhase = Identifier.fromNamespaceAndPath("pixel_tzz", "setup");
		Identifier phaseEnterFunction = Identifier.fromNamespaceAndPath("pixel_tzz", "phase/setup_enter");
		List<Source> phaseHookSources = new ArrayList<>(sources);
		phaseHookSources.removeIf(
			source -> source.type() == DefinitionType.PHASE && source.id().equals(setupPhase)
		);
		phaseHookSources.add(
			new Source(
				DefinitionType.PHASE,
				setupPhase,
				Identifier.fromNamespaceAndPath(
					"pixel_tzz",
					"pixel_tzz_pro/phases/setup.json"
				),
				"snapshot-self-check",
				"""
				{
				  "format_version": 1,
				  "game": "pixel_tzz:main",
				  "name": {"text": "开局设置"},
				  "transitions": ["pixel_tzz:initializing"],
				  "on_enter": "pixel_tzz:phase/setup_enter"
				}
				"""
			)
		);
		Set<Identifier> phaseHookFunctions = new HashSet<>(functions);
		phaseHookFunctions.add(phaseEnterFunction);
		Compilation phaseHookCompilation = DefinitionCompiler.compile(
			phaseHookSources,
			phaseHookFunctions,
			predicates
		);
		check(
			phaseHookCompilation.valid(),
			"phase-hook snapshot fixture must compile: " + phaseHookCompilation.problems()
		);
		DefinitionSnapshot phaseHookSnapshot = phaseHookCompilation.snapshot()
			.orElseThrow()
			.withGeneration(9L);
		var frozen = ExecutionSnapshotCompiler.freeze(
			phaseHookSnapshot,
			phaseHookSnapshot.flows()
				.get(Identifier.fromNamespaceAndPath("pixel_tzz", "general_tutorial")),
			phaseHookSnapshot.panelActions()
				.get(Identifier.fromNamespaceAndPath("pixel_tzz", "start_general_initialization"))
		);
		check(frozen.success(), "execution snapshot with a phase hook must freeze and restore: " + frozen.message());
		check(
			frozen.frozen().orElseThrow().functionReferences().contains(phaseEnterFunction),
			"execution snapshot must retain non-callback function references required by its frozen closure"
		);
	}

	private static List<Source> validDefinitionSources() {
		return List.of(
			source(
				DefinitionType.GAME,
				"main",
				"""
				{
				  "format_version": 1,
				  "api_version": 1,
				  "content_version": 1,
				  "name": {"text": "Test Game"},
				  "initial_phase": "test:setup",
				  "default_role": "test:runner",
				  "default_life_state": "test:alive"
				}
				"""
			),
			source(
				DefinitionType.ROLE,
				"runner",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Runner"},
				  "tags": ["test:general_initialization", "test:ready_participant"],
				  "tab": {"prefix": {"text": "[Runner]"}, "color": "#65D68A"}
				}
				"""
			),
			source(
				DefinitionType.ROLE,
				"hunter",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Hunter"},
				  "tags": ["test:backstage"],
				  "tab": {"prefix": {"text": "[Hunter]"}, "color": "#E94F64"}
				}
				"""
			),
			source(
				DefinitionType.TEAM,
				"red",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Red Team"},
				  "allowed_roles": ["test:runner"],
				  "tags": []
				}
				"""
			),
			source(
				DefinitionType.LIFE_STATE,
				"alive",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Alive"},
				  "tags": ["test:active"]
				}
				"""
			),
			source(
				DefinitionType.LIFE_STATE,
				"captured",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Captured"},
				  "tags": ["test:inactive"]
				}
				"""
			),
			source(
				DefinitionType.PHASE,
				"setup",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Setup"},
				  "transitions": ["test:ready"]
				}
				"""
			),
			source(
				DefinitionType.PHASE,
				"ready",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "name": {"text": "Ready"},
				  "transitions": []
				}
				"""
			),
			source(
				DefinitionType.FIELD,
				"lane",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Lane"},
				  "scope": "player",
				  "type": "single_choice",
				  "required": true,
				  "default": "alpha",
				  "editable_by": "player",
				  "invalidates_ready": true,
				  "roles": ["test:runner"],
				  "phases": ["test:setup"],
				  "visible_when": "test:field/visible",
				  "migration": "preserve",
				  "options": ["alpha", "beta"]
				}
				"""
			),
			source(
				DefinitionType.FLOW,
				"tutorial",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "version": 1,
				  "name": {"text": "Tutorial"},
				  "entry": "intro",
				  "audience": {
				    "role_tags": ["test:general_initialization"],
				    "exclude_host": true,
				    "online_only": true
				  },
				  "required": true,
				  "host_bossbar": {
				    "name": [
				      {"text": "{event}", "color": "gold"},
				      {"text": ": ", "color": "gray"},
				      {"text": "{completed}", "color": "green"},
				      {"text": "/{total}", "color": "white"}
				    ],
				    "color": "yellow",
				    "style": "notched_10",
				    "priority": 10,
				    "completion_feedback": {
				      "name": {"text": "All players complete", "color": "green"},
				      "color": "green",
				      "hold_ticks": 40,
				      "sound": "minecraft:entity.player.levelup"
				    }
				  },
				  "on_start": "test:flow/start",
				  "on_player_complete": "test:flow/player_complete",
				  "on_all_complete": "test:flow/all_complete",
				  "nodes": [
				    {"id": "intro", "type": "page", "page": "test:page/intro", "next": "pick"},
				    {
				      "id": "pick",
				      "type": "choice",
				      "page": "test:page/pick",
				      "field": "test:lane",
				      "choices": [
				        {"value": "alpha", "next": "done"},
				        {"value": "beta", "next": "done"}
				      ]
				    },
				    {"id": "done", "type": "complete"}
				  ]
				}
				"""
			),
			source(
				DefinitionType.PANEL_ACTION,
				"assign_hunter",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "surface": "host",
				  "section": "primary",
				  "order": 10,
				  "label": {"text": "Assign hunter"},
				  "description": {"text": "Assign one or more hunters."},
				  "icon": "test:textures/gui/hunter",
				  "color": "#E94F64",
				  "visible_when": "test:panel/visible",
				  "enabled_when": "test:panel/enabled",
				  "disabled_reason": {"text": "Hunter assignment is currently unavailable."},
				  "phases": ["test:setup"],
				  "target": {
				    "mode": "multiple",
				    "min": 1,
				    "max": 64,
				    "filter": {
				      "roles": ["test:runner", "test:hunter"],
				      "life_states": ["test:alive"],
				      "incomplete_flows": ["test:tutorial"]
				    }
				  },
				  "confirmation": {
				    "title": {"text": "Confirm hunter assignment"},
				    "consequences": [
				      {"text": "Targets enter the hunter initialization flow before the role changes."}
				    ]
				  },
				  "operation": {
				    "type": "assign_role",
				    "role": "test:hunter",
				    "flow": "test:tutorial",
				    "apply": "after_flow"
				  }
				}
				"""
			),
			source(
				DefinitionType.PANEL_ACTION,
				"revive",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "surface": "host",
				  "section": "primary",
				  "order": 20,
				  "label": {"text": "Revive"},
				  "description": {"text": "Restore selected players to the alive state."},
				  "disabled_reason": {"text": "Select a captured player."},
				  "phases": ["test:ready"],
				  "target": {
				    "mode": "multiple",
				    "min": 1,
				    "max": 64,
				    "filter": {
				      "life_states": ["test:captured"],
				      "completed_flows": ["test:tutorial"]
				    }
				  },
				  "confirmation": {
				    "title": {"text": "Confirm revival"},
				    "consequences": [
				      {"text": "Targets return to the alive state immediately."}
				    ]
				  },
				  "operation": {
				    "type": "assign_life_state",
				    "life_state": "test:alive",
				    "apply": "immediate"
				  }
				}
				"""
			),
			source(
				DefinitionType.THEME,
				"control_console",
				"""
				{
				  "format_version": 1,
				  "tokens": {
				    "colors": {"brand": "#F4C95D", "text": "#F2F2F2"},
				    "spacing": {"small": 4, "medium": 8},
				    "fonts": {
				      "body": {
				        "asset": "minecraft:default",
				        "required": false,
				        "fallback": "minecraft:default"
				      }
				    }
				  },
				  "styles": {},
				  "motions": {},
				  "sound_cues": {}
				}
				"""
			),
			source(
				DefinitionType.PAGE,
				"page/intro",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Intro"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "column",
				    "id": "intro_root",
				    "layout": {
				      "width": {"mode": "fill"},
				      "height": {"mode": "fill"},
				      "gap": 8
				    },
				    "children": [
				      {
				        "type": "text",
				        "text": {
				          "concat": [
				            {"text": "Player: "},
				            {"bind": "viewer.name"}
				          ]
				        }
				      },
				      {
				        "type": "button",
				        "id": "continue",
				        "label": {"text": "Continue"},
				        "action": {"type": "flow", "name": "continue"}
				      }
				    ]
				  }
				}
				"""
			),
			source(
				DefinitionType.PAGE,
				"page/pick",
				"""
				{
				  "format_version": 1,
				  "game": "test:main",
				  "title": {"text": "Pick a lane"},
				  "theme": "test:control_console",
				  "root": {
				    "type": "column",
				    "children": [
				      {
				        "type": "field_input",
				        "id": "lane",
				        "field": "test:lane",
				        "presentation": "choice_cards",
				        "show_label": true,
				        "show_description": true
				      },
				      {
				        "type": "button",
				        "id": "submit",
				        "label": {"text": "Submit"},
				        "enabled_when": {
				          "exists": {"bind": "ui/lane/value"}
				        },
				        "disabled_reason": {"text": "Choose a lane first"},
				        "action": {"type": "flow", "name": "submit"}
				      }
				    ]
				  }
				}
				"""
			)
		);
	}

	private static Source source(
		final DefinitionType type,
		final String path,
		final String json
	) {
		return new Source(
			type,
			Identifier.fromNamespaceAndPath("test", path),
			Identifier.fromNamespaceAndPath(
				"test",
				"pixel_tzz_pro/" + type.directory() + "/" + path + ".json"
			),
			"self-check",
			json
		);
	}

	private static String motionColorTheme(final String endingColor) {
		return """
			{
			  "format_version": 1,
			  "tokens": {
			    "colors": {"brand": "#F4C95D"},
			    "spacing": {"small": 4},
			    "fonts": {}
			  },
			  "styles": {},
			  "motions": {
			    "color_fill": {
			      "duration_ms": 100,
			      "delay_ms": 0,
			      "easing": "linear",
			      "tracks": [{
			        "property": "color",
			        "keyframes": [
			          {"at": 0.0, "value": "#FFFFFF"},
			          {"at": 1.0, "value": "%s"}
			        ]
			      }],
			      "reduced_motion": "final"
			    }
			  },
			  "sound_cues": {}
			}
			""".formatted(endingColor);
	}

	private static List<Source> replaceSource(
		final List<Source> sources,
		final DefinitionType type,
		final String path,
		final String json
	) {
		List<Source> result = new ArrayList<>(sources);
		Identifier id = Identifier.fromNamespaceAndPath("test", path);
		result.removeIf(source -> source.type() == type && source.id().equals(id));
		result.add(source(type, path, json));
		return List.copyOf(result);
	}

	private static void collectNodes(
		final NodeDefinition node,
		final List<NodeDefinition> output
	) {
		output.add(node);
		if (node.content() instanceof ChildrenContent children) {
			children.children().forEach(child -> collectNodes(child, output));
		} else if (node.content() instanceof SingleChildContent single) {
			collectNodes(single.child(), output);
		} else if (node.content() instanceof RepeatContent repeat) {
			collectNodes(repeat.template(), output);
		}
	}

	private static void checkProblem(
		final Compilation compilation,
		final String code,
		final String message
	) {
		check(!compilation.valid(), message + " (candidate unexpectedly valid)");
		check(
			compilation.problems().stream().anyMatch(problem -> problem.code().equals(code)),
			message + ": missing diagnostic " + code + " in " + compilation.problems()
		);
	}

	private static void expectUnsupported(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected immutable collection.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
