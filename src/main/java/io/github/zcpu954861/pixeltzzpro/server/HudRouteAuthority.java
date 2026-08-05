package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudAudience;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudAudienceSelector;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudCatalog;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudCondition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudLayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudProfileDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudRoute;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.RouteTier;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure, fail-closed selection of one authorized V3C dock root for one player. */
public final class HudRouteAuthority {
	private HudRouteAuthority() {
	}

	public static Resolution resolve(
		final HudCatalog catalog,
		final Identifier profileId,
		final Context context
	) {
		Objects.requireNonNull(catalog, "catalog");
		Objects.requireNonNull(profileId, "profileId");
		Objects.requireNonNull(context, "context");
		HudProfileDefinition profile = catalog.profiles().get(profileId);
		if (profile == null) {
			return Resolution.unavailable("HUD Profile 不可用：" + profileId);
		}

		for (RouteTier tier : List.of(RouteTier.TASK, RouteTier.CONTEXT)) {
			List<HudRoute> matches = profile.routes()
				.stream()
				.filter(route -> route.tier() == tier)
				.filter(route -> matches(route.when(), context))
				.filter(route -> route.audience().map(value -> matches(value, context)).orElse(true))
				.toList();
			if (matches.isEmpty()) {
				continue;
			}
			int maximum = matches.stream().map(HudRoute::priority).max(Comparator.naturalOrder()).orElseThrow();
			List<HudRoute> winners = matches.stream().filter(route -> route.priority() == maximum).toList();
			if (winners.size() != 1) {
				return Resolution.ambiguous(
					"HUD 路由在 " + tier.name().toLowerCase(java.util.Locale.ROOT)
						+ " 层出现同优先级并列："
						+ winners.stream().map(HudRoute::id).sorted().toList()
				);
			}
			return layout(catalog, profile, winners.getFirst().layout(), context);
		}

		return profile.defaultLayout()
			.map(layout -> layout(catalog, profile, layout, context))
			.orElseGet(Resolution::empty);
	}

	private static Resolution layout(
		final HudCatalog catalog,
		final HudProfileDefinition profile,
		final Identifier layoutId,
		final Context context
	) {
		HudLayoutDefinition layout = catalog.layouts().get(layoutId);
		if (layout == null) {
			return Resolution.unavailable("HUD Layout 不可用：" + layoutId);
		}
		if (layout.audience().map(value -> matches(value, context)).orElse(true) == false) {
			return Resolution.empty();
		}
		return new Resolution(
			Code.SELECTED,
			Optional.of(profile),
			Optional.of(layout),
			""
		);
	}

	public static boolean matches(final HudAudience audience, final Context context) {
		Objects.requireNonNull(audience, "audience");
		Objects.requireNonNull(context, "context");
		return audience.selectors().stream().anyMatch(selector -> matches(selector, context));
	}

	private static boolean matches(final HudAudienceSelector selector, final Context context) {
		if (!sourceMatches(selector.source(), context)) {
			return false;
		}
		if (selector.participants() && !context.participant()) {
			return false;
		}
		if (selector.excludeHost() && context.host()) {
			return false;
		}
		if (selector.onlineOnly() && !context.online()) {
			return false;
		}
		return accepts(selector.roles(), context.role())
			&& accepts(selector.teams(), context.team())
			&& accepts(selector.lifeStates(), context.lifeState())
			&& containsAllOrEmpty(selector.roleTags(), context.roleTags())
			&& containsAllOrEmpty(selector.teamTags(), context.teamTags())
			&& containsAllOrEmpty(selector.lifeStateTags(), context.lifeStateTags());
	}

	private static boolean sourceMatches(final AudienceSource source, final Context context) {
		return switch (source) {
			case ALL -> true;
			case CURRENT_HOST -> context.host();
			case CALL_TARGETS -> context.callTarget();
			case INVOKER -> context.invoker();
			case FROZEN_PARTICIPANTS -> context.frozenParticipants().contains(context.playerId());
		};
	}

	private static boolean accepts(
		final Set<Identifier> allowed,
		final Optional<Identifier> actual
	) {
		return allowed.isEmpty() || actual.filter(allowed::contains).isPresent();
	}

	private static boolean containsAllOrEmpty(
		final Set<Identifier> required,
		final Set<Identifier> actual
	) {
		return required.isEmpty() || actual.containsAll(required);
	}

	public static boolean matches(final HudCondition condition, final Context context) {
		Objects.requireNonNull(condition, "condition");
		Objects.requireNonNull(context, "context");
		if (
			!accepts(condition.games(), Optional.of(context.game()))
				|| !accepts(condition.phases(), context.phase())
				|| !accepts(condition.tasks(), context.task())
				|| (!condition.taskKinds().isEmpty()
					&& context.taskKind().filter(condition.taskKinds()::contains).isEmpty())
				|| (!condition.taskStatuses().isEmpty()
					&& context.taskStatus().filter(condition.taskStatuses()::contains).isEmpty())
				|| !accepts(condition.roles(), context.role())
				|| !accepts(condition.teams(), context.team())
				|| !accepts(condition.lifeStates(), context.lifeState())
				|| condition.host().filter(value -> value != context.host()).isPresent()
				|| condition.online().filter(value -> value != context.online()).isPresent()
		) {
			return false;
		}
		if (condition.all().stream().anyMatch(child -> !matches(child, context))) {
			return false;
		}
		if (!condition.any().isEmpty() && condition.any().stream().noneMatch(child -> matches(child, context))) {
			return false;
		}
		return condition.not().map(child -> !matches(child, context)).orElse(true);
	}

	public enum Code {
		SELECTED,
		EMPTY,
		AMBIGUOUS,
		UNAVAILABLE
	}

	public record Resolution(
		Code code,
		Optional<HudProfileDefinition> profile,
		Optional<HudLayoutDefinition> layout,
		String diagnostic
	) {
		public Resolution {
			code = Objects.requireNonNull(code, "code");
			profile = Objects.requireNonNull(profile, "profile");
			layout = Objects.requireNonNull(layout, "layout");
			diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
			if ((code == Code.SELECTED) != (profile.isPresent() && layout.isPresent())) {
				throw new IllegalArgumentException("HUD route resolution is inconsistent");
			}
		}

		private static Resolution empty() {
			return new Resolution(Code.EMPTY, Optional.empty(), Optional.empty(), "");
		}

		private static Resolution ambiguous(final String message) {
			return new Resolution(Code.AMBIGUOUS, Optional.empty(), Optional.empty(), message);
		}

		private static Resolution unavailable(final String message) {
			return new Resolution(Code.UNAVAILABLE, Optional.empty(), Optional.empty(), message);
		}
	}

	public record Context(
		UUID playerId,
		Identifier game,
		Optional<Identifier> phase,
		Optional<Identifier> task,
		Optional<TaskKind> taskKind,
		Optional<TaskStatus> taskStatus,
		Optional<Identifier> role,
		Optional<Identifier> team,
		Optional<Identifier> lifeState,
		Set<Identifier> roleTags,
		Set<Identifier> teamTags,
		Set<Identifier> lifeStateTags,
		boolean host,
		boolean participant,
		boolean online,
		boolean callTarget,
		boolean invoker,
		Set<UUID> frozenParticipants
	) {
		public Context {
			playerId = Objects.requireNonNull(playerId, "playerId");
			game = Objects.requireNonNull(game, "game");
			phase = Objects.requireNonNull(phase, "phase");
			task = Objects.requireNonNull(task, "task");
			taskKind = Objects.requireNonNull(taskKind, "taskKind");
			taskStatus = Objects.requireNonNull(taskStatus, "taskStatus");
			role = Objects.requireNonNull(role, "role");
			team = Objects.requireNonNull(team, "team");
			lifeState = Objects.requireNonNull(lifeState, "lifeState");
			roleTags = Set.copyOf(roleTags);
			teamTags = Set.copyOf(teamTags);
			lifeStateTags = Set.copyOf(lifeStateTags);
			frozenParticipants = Set.copyOf(frozenParticipants);
		}
	}
}
