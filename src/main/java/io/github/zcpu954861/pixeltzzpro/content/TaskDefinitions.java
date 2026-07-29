package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable data-pack-owned task timeline definitions.
 *
 * <p>The records are deliberately behavior-free. {@link DefinitionCompiler} validates the whole
 * reachable graph before a generation can be published.
 */
public final class TaskDefinitions {
	private static final Identifier ACTIVE_PARTICIPANT_TAG =
		Identifier.fromNamespaceAndPath("pixel_tzz", "active_participant");

	private TaskDefinitions() {
	}

	public record TaskTimeline(
		Identifier initialTask,
		boolean pauseWhenHostOffline,
		Identifier approvalPhase,
		Identifier startPhase,
		List<RequiredFieldRequirement> preStartRequirements
	) {
		public TaskTimeline {
			preStartRequirements = List.copyOf(preStartRequirements);
		}
	}

	public record RequiredFieldRequirement(Audience audience, Identifier field) {
	}

	public enum TaskKind {
		WARMUP,
		MAIN
	}

	public enum TaskCompletionPolicy {
		TIMEOUT_ONLY,
		EARLY_OR_TIMEOUT,
		EVENT_ONLY
	}

	public enum FutureVisibility {
		FULL,
		TEASER,
		HIDDEN
	}

	public enum HistoryVisibility {
		FULL,
		SUMMARY,
		HIDDEN
	}

	public enum CandidateVisibility {
		NONE,
		NAMES_ONLY
	}

	public enum RecapVisibility {
		PARTICIPANTS,
		HOST_ONLY,
		HIDDEN
	}

	public record LiveVisibility(
		FutureVisibility future,
		HistoryVisibility history,
		CandidateVisibility candidateVisibility
	) {
		public static LiveVisibility hidden() {
			return new LiveVisibility(
				FutureVisibility.HIDDEN,
				HistoryVisibility.HIDDEN,
				CandidateVisibility.NONE
			);
		}
	}

	public record TaskCallbacks(
		Optional<Identifier> onStart,
		Optional<Identifier> onPause,
		Optional<Identifier> onResume,
		Optional<Identifier> onTimeout,
		Optional<Identifier> onSettled
	) {
		public TaskCallbacks {
			onStart = optional(onStart);
			onPause = optional(onPause);
			onResume = optional(onResume);
			onTimeout = optional(onTimeout);
			onSettled = optional(onSettled);
		}

		public static TaskCallbacks empty() {
			return new TaskCallbacks(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public record PlayerCallback(
		Identifier function,
		Optional<Audience> audience,
		Map<String, Identifier> fields
	) {
		public PlayerCallback {
			audience = optional(audience);
			fields = Map.copyOf(fields);
		}
	}

	public enum ResultSemantic {
		SUCCESS,
		FAILURE,
		NEUTRAL,
		CUSTOM
	}

	public record ResultStyle(ResultSemantic semantic, Optional<Identifier> customSemantic) {
		public ResultStyle {
			customSemantic = optional(customSemantic);
		}
	}

	public enum TransitionTiming {
		BEFORE_INTERMISSION,
		AFTER_INTERMISSION
	}

	public record IntermissionDefinition(
		long durationTicks,
		boolean countsTowardGameTime,
		RichText name,
		Optional<Identifier> onStart,
		Optional<Identifier> onPause,
		Optional<Identifier> onResume,
		Optional<Identifier> onComplete
	) {
		public IntermissionDefinition {
			onStart = optional(onStart);
			onPause = optional(onPause);
			onResume = optional(onResume);
			onComplete = optional(onComplete);
		}
	}

	public record TaskRoute(
		Optional<Identifier> nextTask,
		Optional<Identifier> endPhase,
		Optional<Identifier> phase,
		Optional<TransitionTiming> transitionTiming,
		Optional<IntermissionDefinition> intermission
	) {
		public TaskRoute {
			nextTask = optional(nextTask);
			endPhase = optional(endPhase);
			phase = optional(phase);
			transitionTiming = optional(transitionTiming);
			intermission = optional(intermission);
		}
	}

	public record TaskResult(
		String id,
		RichText name,
		ResultStyle style,
		Optional<RichText> recap,
		Optional<Identifier> onApply,
		Optional<PlayerCallback> onApplyPlayers,
		boolean allowHostFallback,
		TaskRoute route
	) {
		public TaskResult {
			recap = optional(recap);
			onApply = optional(onApply);
			onApplyPlayers = optional(onApplyPlayers);
		}
	}

	public enum TaskEventPolicy {
		ONCE,
		PER_PLAYER,
		REPEATABLE
	}

	public enum TaskEventState {
		RUNNING,
		SETTLING,
		INTERMISSION
	}

	public record TaskEventDefinition(
		String id,
		RichText name,
		TaskEventPolicy policy,
		Optional<Integer> maximumRecords,
		Set<TaskEventState> allowedStates,
		Optional<Audience> audience,
		RecapVisibility recapVisibility
	) {
		public TaskEventDefinition {
			maximumRecords = optional(maximumRecords);
			allowedStates = Set.copyOf(allowedStates);
			audience = optional(audience);
		}
	}

	public enum TaskStatisticType {
		BOOLEAN,
		INTEGER,
		DURATION_TICKS,
		STRING,
		IDENTIFIER,
		PLAYER
	}

	public enum TaskStatisticWrite {
		ONCE,
		REPLACE,
		INCREMENT
	}

	public record TaskStatisticDefinition(
		String id,
		RichText name,
		TaskStatisticType type,
		Optional<Long> minimum,
		Optional<Long> maximum,
		TaskStatisticWrite write,
		RecapVisibility recapVisibility
	) {
		public TaskStatisticDefinition {
			minimum = optional(minimum);
			maximum = optional(maximum);
		}
	}

	public record TaskDefinition(
		Identifier id,
		Identifier game,
		int version,
		TaskKind kind,
		RichText name,
		Optional<RichText> description,
		Optional<Identifier> page,
		Audience audience,
		TaskCompletionPolicy completionPolicy,
		OptionalLong durationTicks,
		boolean countsTowardGameTime,
		LiveVisibility liveVisibility,
		RecapVisibility recapVisibility,
		TaskCallbacks callbacks,
		Optional<PlayerCallback> onStartPlayers,
		Map<String, TaskResult> results,
		List<TaskEventDefinition> events,
		List<TaskStatisticDefinition> statistics
	) {
		public TaskDefinition {
			description = optional(description);
			page = optional(page);
			durationTicks = durationTicks == null ? OptionalLong.empty() : durationTicks;
			onStartPlayers = optional(onStartPlayers);
			results = Map.copyOf(results);
			events = List.copyOf(events);
			statistics = List.copyOf(statistics);
		}
	}

	public static Audience defaultTaskAudience() {
		return new Audience(
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of(ACTIVE_PARTICIPANT_TAG),
			Set.of(),
			Set.of(),
			true,
			false
		);
	}

	private static <T> Optional<T> optional(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}
}
