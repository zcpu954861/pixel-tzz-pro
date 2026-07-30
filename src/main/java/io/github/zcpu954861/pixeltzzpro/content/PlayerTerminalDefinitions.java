package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Confirmation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable data-pack-owned definitions for the ordinary player terminal.
 *
 * <p>These records deliberately contain no runtime authority. The compiler resolves every
 * cross-reference before a definition generation is published; the server later projects only
 * the definitions and values that are legal for one viewer.
 */
public final class PlayerTerminalDefinitions {
	private PlayerTerminalDefinitions() {
	}

	/**
	 * Game-level terminal fallback and the global player-history gate.
	 */
	public record PlayerTerminalConfig(
		Identifier defaultPage,
		boolean historyEnabled,
		HistorySource historySource
	) {
		public PlayerTerminalConfig(
			final Identifier defaultPage,
			final boolean historyEnabled
		) {
			this(defaultPage, historyEnabled, HistorySource.EVENTS);
		}

		public PlayerTerminalConfig {
			Objects.requireNonNull(defaultPage, "defaultPage");
			Objects.requireNonNull(historySource, "historySource");
		}
	}

	/**
	 * Chooses whether the history surface lists each released event or groups released events by
	 * completed task. Task grouping never expands visibility: only events already released and
	 * audience-authorized for the current viewer contribute to one task summary.
	 */
	public enum HistorySource {
		EVENTS,
		TASKS
	}

	/**
	 * Stable task lifecycle names exposed to terminal route and action conditions.
	 *
	 * <p>This content-layer enum is intentionally independent from persisted runtime enums. It is
	 * a declarative filter contract and can therefore be compiled and frozen with a task plan.
	 */
	public enum PlayerTaskState {
		STARTING,
		RUNNING,
		PAUSED,
		SETTLING,
		INTERMISSION,
		SETTLED,
		COMPLETED,
		INTERRUPTED,
		BLOCKED
	}

	/**
	 * One authoritative candidate for the page opened by the ordinary player-terminal entry.
	 */
	public record PlayerRouteDefinition(
		Identifier id,
		Identifier game,
		Identifier page,
		int priority,
		Audience audience,
		Set<Identifier> phases,
		Set<Identifier> tasks,
		Set<PlayerTaskState> taskStates,
		Optional<Identifier> predicate
	) {
		public PlayerRouteDefinition {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(game, "game");
			Objects.requireNonNull(page, "page");
			Objects.requireNonNull(audience, "audience");
			phases = Set.copyOf(phases);
			tasks = Set.copyOf(tasks);
			taskStates = Set.copyOf(taskStates);
			predicate = optional(predicate);
		}
	}

	public enum PlayerDataSourceType {
		ROLE,
		TEAM,
		LIFE_STATE,
		INITIALIZED,
		READY,
		FIELD,
		EXCLUSIVE_CHOICE,
		TASK_ID,
		TASK_NAME,
		TASK_DESCRIPTION,
		TASK_STATUS,
		TASK_ELAPSED_TICKS,
		TASK_REMAINING_TICKS,
		GAME_ELAPSED_TICKS,
		TASK_PROGRESS,
		TASK_RESULT,
		TASK_STATISTIC
	}

	/**
	 * Source selector for one allow-listed player-visible value.
	 *
	 * <p>{@code id} identifies a field or task where the source kind requires one. {@code key}
	 * identifies a task-local progress/statistic/result key where the source kind requires one.
	 */
	public record PlayerDataSource(
		PlayerDataSourceType type,
		Optional<Identifier> id,
		Optional<String> key
	) {
		public PlayerDataSource {
			Objects.requireNonNull(type, "type");
			id = optional(id);
			key = optional(key);
		}
	}

	public enum PlayerSurface {
		TERMINAL,
		HUD,
		DYNAMIC_MESSAGE
	}

	/**
	 * Explicit authorization for one personal value and each presentation surface that may use it.
	 */
	public record PlayerDataDefinition(
		Identifier id,
		Identifier game,
		PlayerDataSource source,
		Set<PlayerSurface> surfaces,
		Audience audience,
		Set<Identifier> phases,
		Set<Identifier> tasks,
		Set<PlayerTaskState> taskStates,
		Optional<Identifier> predicate,
		Optional<RichText> name,
		Optional<String> format,
		Optional<RichText> maskedFallback
	) {
		public PlayerDataDefinition {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(game, "game");
			Objects.requireNonNull(source, "source");
			surfaces = Set.copyOf(surfaces);
			Objects.requireNonNull(audience, "audience");
			phases = Set.copyOf(phases);
			tasks = Set.copyOf(tasks);
			taskStates = Set.copyOf(taskStates);
			predicate = optional(predicate);
			name = optional(name);
			format = optional(format);
			maskedFallback = optional(maskedFallback);
		}
	}

	public sealed interface PlayerOperation permits PlayerOpenPageOperation, PlayerRunFunctionOperation {
	}

	public record PlayerOpenPageOperation(Identifier page) implements PlayerOperation {
		public PlayerOpenPageOperation {
			Objects.requireNonNull(page, "page");
		}
	}

	public record PlayerRunFunctionOperation(Identifier function) implements PlayerOperation {
		public PlayerRunFunctionOperation {
			Objects.requireNonNull(function, "function");
		}
	}

	public enum UsageScope {
		UNLIMITED,
		PLAYER,
		GAME
	}

	public enum CooldownScope {
		PLAYER,
		GAME
	}

	public enum ExecutionContext {
		PLAYER,
		SERVER
	}

	public record PlayerActionFeedback(
		Optional<RichText> success,
		Optional<RichText> denied,
		Optional<RichText> failed
	) {
		public PlayerActionFeedback {
			success = optional(success);
			denied = optional(denied);
			failed = optional(failed);
		}

		public static PlayerActionFeedback empty() {
			return new PlayerActionFeedback(Optional.empty(), Optional.empty(), Optional.empty());
		}
	}

	public enum AuditPolicy {
		NONE,
		FAILURES,
		ALL
	}

	/**
	 * A page-callable operation. It is only an allow-list entry; every request must still be
	 * rebound to its page instance and revalidated against these conditions by the server.
	 */
	public record PlayerActionDefinition(
		Identifier id,
		Identifier game,
		PlayerOperation operation,
		Audience audience,
		Set<Identifier> phases,
		Set<Identifier> tasks,
		Set<PlayerTaskState> taskStates,
		Optional<Identifier> predicate,
		UsageScope usageScope,
		int maximumUses,
		long cooldownTicks,
		CooldownScope cooldownScope,
		Optional<Confirmation> confirmation,
		ExecutionContext executionContext,
		PlayerActionFeedback feedback,
		AuditPolicy audit
	) {
		public PlayerActionDefinition {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(game, "game");
			Objects.requireNonNull(operation, "operation");
			Objects.requireNonNull(audience, "audience");
			phases = Set.copyOf(phases);
			tasks = Set.copyOf(tasks);
			taskStates = Set.copyOf(taskStates);
			predicate = optional(predicate);
			Objects.requireNonNull(usageScope, "usageScope");
			Objects.requireNonNull(cooldownScope, "cooldownScope");
			confirmation = optional(confirmation);
			Objects.requireNonNull(executionContext, "executionContext");
			feedback = feedback == null ? PlayerActionFeedback.empty() : feedback;
			Objects.requireNonNull(audit, "audit");
		}
	}

	public enum HistoryRelease {
		IMMEDIATE,
		TASK_END,
		GAME_END
	}

	/**
	 * Player-facing projection policy for one authoritative task-event kind.
	 *
	 * <p>Presence enables history for that event kind. Absence is fail-closed even when the
	 * game-level history switch is on.
	 */
	public record PlayerHistoryPresentation(
		HistoryRelease release,
		Audience audience,
		RichText title,
		Optional<RichText> summary,
		Optional<Identifier> icon,
		Optional<String> color,
		Optional<String> category,
		boolean showGameTime,
		boolean showTask,
		boolean showActor,
		boolean showTargets,
		Set<String> parameters,
		Set<String> statistics,
		boolean showPersonalResult,
		Optional<RichText> details,
		Optional<Identifier> detailPage
	) {
		public PlayerHistoryPresentation {
			Objects.requireNonNull(release, "release");
			Objects.requireNonNull(audience, "audience");
			Objects.requireNonNull(title, "title");
			summary = optional(summary);
			icon = optional(icon);
			color = optional(color);
			category = optional(category);
			parameters = Set.copyOf(parameters);
			statistics = Set.copyOf(statistics);
			details = optional(details);
			detailPage = optional(detailPage);
		}
	}

	private static <T> Optional<T> optional(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}
}
