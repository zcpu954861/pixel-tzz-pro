package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.LifeStateDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.PhaseDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RoleDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TeamDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBinding;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBindingSource;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudBindingSourceType;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudValueType;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Boundary;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingRequest;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BindingSourceAccess;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.BooleanValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ClockValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ComponentValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.DecimalValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.DurationValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.IdentifierValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.IntegerValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ListValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ObjectValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.PlayerValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.StringValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.Value;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Read-only synthetic source adapter for the isolated host HUD preview.
 *
 * <p>The adapter accepts only bindings that already belong to the supplied immutable definition
 * generation. It never reads Minecraft state, scoreboards, storage, NBT or entities. Sensitive
 * bindings and all source kinds that could expose live or historical game data fail closed. As in
 * the production adapter, authorization creates a one-shot grant that must be consumed by the
 * immediately following resolve call.</p>
 */
public final class HudPreviewBindingSourceAccess implements BindingSourceAccess {
	private static final int NORMAL_LIST_SIZE = 3;
	private static final int LIMIT_LIST_SIZE = 64;
	private static final long STALE_AGE_TICKS = 40L;
	private static final String PREVIEW_PLAYER_NAME = "预览玩家";
	private static final String PREVIEW_TEXT = "预览内容";
	private static final String LONG_TEXT =
		"这是一段由服务器固定生成的长文本边界预览，用于检查自动换行、裁切、层级和安全区域；"
			+ "它不读取世界、玩家、计分板、storage、NBT 或任何由客户端提交的原始值。";
	private static final Set<HudBindingSourceType> ALLOWED_SOURCES = Set.of(
		HudBindingSourceType.GAME_FACT,
		HudBindingSourceType.PHASE_FACT,
		HudBindingSourceType.TASK_FACT,
		HudBindingSourceType.PLAYER_FACT,
		HudBindingSourceType.CLOCK
	);

	private final PreviewContext context;
	private final Set<HudBinding> registeredBindings;
	private final Set<BindingRequest> oneShotGrants = new HashSet<>();

	private HudPreviewBindingSourceAccess(final PreviewContext context) {
		this.context = Objects.requireNonNull(context, "context");
		Set<HudBinding> registered = new HashSet<>();
		context.definitions().hudCatalog().components().values().forEach(component ->
			registered.addAll(component.bindings().values())
		);
		this.registeredBindings = Set.copyOf(registered);
	}

	public static BindingSourceAccess create(final PreviewContext context) {
		return new HudPreviewBindingSourceAccess(context);
	}

	public static BindingSourceAccess create(
		final DefinitionSnapshot definitions,
		final GameDefinition game,
		final Optional<PhaseDefinition> phase,
		final Optional<TaskDefinition> task,
		final Optional<RoleDefinition> role,
		final Optional<TeamDefinition> team,
		final Optional<LifeStateDefinition> lifeState,
		final HudRouteAuthority.Context audience,
		final Boundary boundary,
		final long serverTick
	) {
		return create(new PreviewContext(
			definitions,
			game,
			phase,
			task,
			role,
			team,
			lifeState,
			audience,
			boundary,
			serverTick
		));
	}

	@Override
	public boolean authorized(final BindingRequest request) {
		Objects.requireNonNull(request, "request");
		boolean allowed;
		try {
			allowed = request.audience().equals(this.context.audience())
				&& request.source().equals(request.binding().source())
				&& this.registeredBindings.contains(request.binding())
				&& !request.binding().sensitive()
				&& ALLOWED_SOURCES.contains(request.source().type())
				&& descriptorClosed(request.source());
		} catch (RuntimeException ignored) {
			allowed = false;
		}
		if (allowed) {
			this.oneShotGrants.add(request);
		} else {
			this.oneShotGrants.remove(request);
		}
		return allowed;
	}

	@Override
	public SourceValue resolve(final BindingRequest request) {
		Objects.requireNonNull(request, "request");
		if (!this.oneShotGrants.remove(request)) {
			return SourceValue.missing();
		}
		if (this.context.boundary() == Boundary.MISSING) {
			return SourceValue.missing();
		}
		try {
			Optional<Value> value = syntheticValue(request);
			if (value.isEmpty()) {
				return SourceValue.missing();
			}
			return this.context.boundary() == Boundary.STALE
				? SourceValue.stale(value.orElseThrow(), STALE_AGE_TICKS)
				: SourceValue.current(value.orElseThrow());
		} catch (RuntimeException ignored) {
			return SourceValue.missing();
		}
	}

	private Optional<Value> syntheticValue(final BindingRequest request) {
		if (request.source().type() == HudBindingSourceType.TASK_FACT && this.context.task().isEmpty()) {
			return Optional.empty();
		}
		if (request.source().type() == HudBindingSourceType.PHASE_FACT && this.context.phase().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(switch (request.binding().valueType()) {
			case STRING -> new StringValue(text(request));
			case INTEGER -> new IntegerValue(integer(request));
			case DECIMAL -> new DecimalValue(decimal(request));
			case BOOLEAN -> new BooleanValue(bool(request));
			case IDENTIFIER -> new IdentifierValue(identifier(request));
			case PLAYER -> new PlayerValue(
				this.context.audience().playerId(),
				PREVIEW_PLAYER_NAME,
				this.context.audience().online()
			);
			case COMPONENT -> new ComponentValue(component(request));
			case DURATION -> new DurationValue(Math.max(0L, integer(request)));
			case CLOCK -> clock(request);
			case LIST -> list(request);
			case OBJECT -> object(request);
		});
	}

	private String text(final BindingRequest request) {
		if (this.context.boundary() == Boundary.LONG_TEXT) {
			return LONG_TEXT;
		}
		String name = sourceName(request);
		return switch (request.source().type()) {
			case GAME_FACT -> switch (name) {
				case "name" -> richText(this.context.game().name()).getString();
				case "state", "status" -> taskStatus();
				case "id" -> this.context.game().id().toString();
				default -> PREVIEW_TEXT;
			};
			case PHASE_FACT -> switch (name) {
				case "name" -> this.context.phase().map(PhaseDefinition::name).map(HudPreviewBindingSourceAccess::richText)
					.map(Component::getString).orElse(PREVIEW_TEXT);
				case "id" -> this.context.phase().map(PhaseDefinition::id).map(Identifier::toString).orElse(PREVIEW_TEXT);
				default -> PREVIEW_TEXT;
			};
			case TASK_FACT -> switch (name) {
				case "name" -> richText(this.context.task().orElseThrow().name()).getString();
				case "description" -> this.context.task().orElseThrow().description()
					.map(HudPreviewBindingSourceAccess::richText).map(Component::getString).orElse(PREVIEW_TEXT);
				case "kind" -> this.context.task().orElseThrow().kind().name().toLowerCase(Locale.ROOT);
				case "state", "status" -> taskStatus();
				case "id" -> this.context.task().orElseThrow().id().toString();
				default -> PREVIEW_TEXT;
			};
			case PLAYER_FACT -> switch (name) {
				case "uuid" -> this.context.audience().playerId().toString();
				case "name" -> PREVIEW_PLAYER_NAME;
				case "role_name" -> this.context.role().map(RoleDefinition::name)
					.map(HudPreviewBindingSourceAccess::richText).map(Component::getString).orElse(PREVIEW_TEXT);
				case "team_name" -> this.context.team().map(TeamDefinition::name)
					.map(HudPreviewBindingSourceAccess::richText).map(Component::getString).orElse(PREVIEW_TEXT);
				case "life_state_name" -> this.context.lifeState().map(LifeStateDefinition::name)
					.map(HudPreviewBindingSourceAccess::richText).map(Component::getString).orElse(PREVIEW_TEXT);
				default -> PREVIEW_TEXT;
			};
			case CLOCK -> sourceName(request);
			default -> PREVIEW_TEXT;
		};
	}

	private Component component(final BindingRequest request) {
		if (this.context.boundary() == Boundary.LONG_TEXT) {
			return Component.literal(LONG_TEXT);
		}
		String name = sourceName(request);
		return switch (request.source().type()) {
			case GAME_FACT -> name.equals("name") ? richText(this.context.game().name()) : Component.literal(text(request));
			case PHASE_FACT -> name.equals("name")
				? this.context.phase().map(PhaseDefinition::name).map(HudPreviewBindingSourceAccess::richText)
					.orElseGet(() -> Component.literal(PREVIEW_TEXT))
				: Component.literal(text(request));
			case TASK_FACT -> switch (name) {
				case "name" -> richText(this.context.task().orElseThrow().name());
				case "description" -> this.context.task().orElseThrow().description()
					.map(HudPreviewBindingSourceAccess::richText).orElseGet(() -> Component.literal(PREVIEW_TEXT));
				default -> Component.literal(text(request));
			};
			case PLAYER_FACT -> switch (name) {
				case "role_name" -> this.context.role().map(RoleDefinition::name)
					.map(HudPreviewBindingSourceAccess::richText).orElseGet(() -> Component.literal(PREVIEW_TEXT));
				case "team_name" -> this.context.team().map(TeamDefinition::name)
					.map(HudPreviewBindingSourceAccess::richText).orElseGet(() -> Component.literal(PREVIEW_TEXT));
				case "life_state_name" -> this.context.lifeState().map(LifeStateDefinition::name)
					.map(HudPreviewBindingSourceAccess::richText).orElseGet(() -> Component.literal(PREVIEW_TEXT));
				default -> Component.literal(text(request));
			};
			default -> Component.literal(text(request));
		};
	}

	private long integer(final BindingRequest request) {
		String name = sourceName(request);
		return switch (request.source().type()) {
			case GAME_FACT -> switch (name) {
				case "content_version" -> this.context.game().contentVersion();
				case "participant_count" -> 8L;
				case "elapsed_ticks", "game_elapsed_ticks" -> 3_600L;
				default -> 1L;
			};
			case TASK_FACT -> switch (name) {
				case "duration_ticks" -> this.context.task().orElseThrow().durationTicks().orElse(2_400L);
				case "elapsed_ticks", "progress_current" -> 900L;
				case "remaining_ticks" -> 1_500L;
				case "progress_maximum" -> 2_400L;
				case "timeline_position" -> 2L;
				case "timeline_extent" -> Math.max(1L, this.context.definitions().tasks().values().stream()
					.filter(value -> value.game().equals(this.context.game().id())).count());
				case "participant_count" -> 8L;
				default -> 1L;
			};
			case CLOCK -> 1_200L;
			default -> 1L;
		};
	}

	private double decimal(final BindingRequest request) {
		String name = sourceName(request);
		if (request.source().type() == HudBindingSourceType.TASK_FACT && name.equals("progress_current")) {
			return 0.625D;
		}
		return 0.625D;
	}

	private boolean bool(final BindingRequest request) {
		String name = sourceName(request);
		return switch (request.source().type()) {
			case GAME_FACT -> switch (name) {
				case "paused" -> this.context.audience().taskStatus()
					.filter(io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus.PAUSED::equals)
					.isPresent();
				default -> true;
			};
			case TASK_FACT -> switch (name) {
				case "paused" -> this.context.audience().taskStatus()
					.filter(io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus.PAUSED::equals)
					.isPresent();
				case "counts_toward_game_time" -> this.context.task().orElseThrow().countsTowardGameTime();
				default -> true;
			};
			case PLAYER_FACT -> switch (name) {
				case "host", "is_host" -> this.context.audience().host();
				case "participant" -> this.context.audience().participant();
				case "online" -> this.context.audience().online();
				default -> true;
			};
			default -> true;
		};
	}

	private Identifier identifier(final BindingRequest request) {
		String name = sourceName(request);
		return switch (request.source().type()) {
			case GAME_FACT -> this.context.game().id();
			case PHASE_FACT -> this.context.phase().map(PhaseDefinition::id).orElse(this.context.game().initialPhase());
			case TASK_FACT -> this.context.task().map(TaskDefinition::id).orElse(this.context.game().id());
			case PLAYER_FACT -> switch (name) {
				case "role" -> this.context.role().map(RoleDefinition::id).orElse(this.context.game().defaultRole());
				case "team" -> this.context.team().map(TeamDefinition::id).orElse(this.context.game().id());
				case "life_state" -> this.context.lifeState().map(LifeStateDefinition::id)
					.orElse(this.context.game().defaultLifeState());
				default -> this.context.game().id();
			};
			default -> this.context.game().id();
		};
	}

	private ClockValue clock(final BindingRequest request) {
		if (request.source().type() != HudBindingSourceType.CLOCK) {
			throw new IllegalArgumentException("non-clock preview source requested a clock value");
		}
		boolean paused = this.context.audience().taskStatus()
			.filter(io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskStatus.PAUSED::equals)
			.isPresent();
		return new ClockValue(
			this.context.serverTick(),
			switch (sourceName(request)) {
				case "game" -> 3_600.0D;
				case "task" -> 900.0D;
				case "interval" -> 300.0D;
				case "warmup" -> 600.0D;
				default -> 1_200.0D;
			},
			paused ? 0.0D : 1.0D,
			paused
		);
	}

	private ListValue list(final BindingRequest request) {
		HudValueType itemType = request.binding().itemType().orElseThrow();
		int count = this.context.boundary() == Boundary.LIST_LIMIT ? LIMIT_LIST_SIZE : NORMAL_LIST_SIZE;
		List<Value> values = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			values.add(listItem(itemType, index));
		}
		return new ListValue(itemType, values);
	}

	private Value listItem(final HudValueType type, final int index) {
		return switch (type) {
			case STRING -> new StringValue("预览项目 " + (index + 1));
			case INTEGER -> new IntegerValue(index + 1L);
			case DECIMAL -> new DecimalValue((index + 1.0D) / 10.0D);
			case BOOLEAN -> new BooleanValue(index % 2 == 0);
			case IDENTIFIER -> new IdentifierValue(Identifier.fromNamespaceAndPath("pixel_tzz", "preview/item_" + index));
			case PLAYER -> new PlayerValue(this.context.audience().playerId(), PREVIEW_PLAYER_NAME, true);
			case COMPONENT -> new ComponentValue(Component.literal("预览项目 " + (index + 1)));
			case DURATION -> new DurationValue((index + 1L) * 20L);
			case OBJECT -> new ObjectValue(Map.of("index", new IntegerValue(index + 1L)));
			case CLOCK -> new ClockValue(this.context.serverTick(), index * 20.0D, 1.0D, false);
			case LIST -> throw new IllegalArgumentException("nested preview lists are unavailable");
		};
	}

	private ObjectValue object(final BindingRequest request) {
		Map<String, Value> values = new LinkedHashMap<>();
		values.put("label", new StringValue(text(request)));
		values.put("value", new IntegerValue(integer(request)));
		values.put("active", new BooleanValue(bool(request)));
		return new ObjectValue(values);
	}

	private String taskStatus() {
		return this.context.audience().taskStatus()
			.map(value -> value.name().toLowerCase(Locale.ROOT))
			.orElse("setup");
	}

	private static String sourceName(final BindingRequest request) {
		return request.source().value().orElse("");
	}

	private static boolean descriptorClosed(final HudBindingSource source) {
		if (source.value().isEmpty()) {
			return false;
		}
		return source.id().isEmpty()
			&& source.field().isEmpty()
			&& source.objective().isEmpty()
			&& source.holder().isEmpty()
			&& source.storage().isEmpty()
			&& source.path().isEmpty()
			&& source.target().isEmpty();
	}

	private static Component richText(final RichText value) {
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(value.json()))
			.getOrThrow();
	}

	/** Immutable, already-validated synthetic context; it contains no live Minecraft handle. */
	public record PreviewContext(
		DefinitionSnapshot definitions,
		GameDefinition game,
		Optional<PhaseDefinition> phase,
		Optional<TaskDefinition> task,
		Optional<RoleDefinition> role,
		Optional<TeamDefinition> team,
		Optional<LifeStateDefinition> lifeState,
		HudRouteAuthority.Context audience,
		Boundary boundary,
		long serverTick
	) {
		public PreviewContext {
			definitions = Objects.requireNonNull(definitions, "definitions");
			game = Objects.requireNonNull(game, "game");
			phase = Objects.requireNonNull(phase, "phase");
			task = Objects.requireNonNull(task, "task");
			role = Objects.requireNonNull(role, "role");
			team = Objects.requireNonNull(team, "team");
			lifeState = Objects.requireNonNull(lifeState, "lifeState");
			audience = Objects.requireNonNull(audience, "audience");
			boundary = Objects.requireNonNull(boundary, "boundary");
			if (serverTick < 0L) {
				throw new IllegalArgumentException("preview server tick cannot be negative");
			}
			if (!game.equals(definitions.games().get(game.id())) || !audience.game().equals(game.id())) {
				throw new IllegalArgumentException("preview game is not registered in the supplied generation");
			}
			validateMember(phase, definitions.phases(), game.id(), PhaseDefinition::id, PhaseDefinition::game, "phase");
			validateMember(task, definitions.tasks(), game.id(), TaskDefinition::id, TaskDefinition::game, "task");
			validateMember(role, definitions.roles(), game.id(), RoleDefinition::id, RoleDefinition::game, "role");
			validateMember(team, definitions.teams(), game.id(), TeamDefinition::id, TeamDefinition::game, "team");
			validateMember(lifeState, definitions.lifeStates(), game.id(), LifeStateDefinition::id, LifeStateDefinition::game, "life state");
			if (!audience.phase().equals(phase.map(PhaseDefinition::id))
				|| !audience.task().equals(task.map(TaskDefinition::id))
				|| !audience.role().equals(role.map(RoleDefinition::id))
				|| !audience.team().equals(team.map(TeamDefinition::id))
				|| !audience.lifeState().equals(lifeState.map(LifeStateDefinition::id))) {
				throw new IllegalArgumentException("preview route context does not match its registered definitions");
			}
			Optional<io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind> expectedKind = task.map(value ->
				io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TaskKind.valueOf(value.kind().name())
			);
			if (!audience.taskKind().equals(expectedKind) || (task.isEmpty() && audience.taskStatus().isPresent())) {
				throw new IllegalArgumentException("preview task route context is inconsistent");
			}
			if (!audience.roleTags().equals(role.map(RoleDefinition::tags).orElse(Set.of()))
				|| !audience.teamTags().equals(team.map(TeamDefinition::tags).orElse(Set.of()))
				|| !audience.lifeStateTags().equals(lifeState.map(LifeStateDefinition::tags).orElse(Set.of()))) {
				throw new IllegalArgumentException("preview route tags do not match their registered definitions");
			}
		}

		private static <T> void validateMember(
			final Optional<T> value,
			final Map<Identifier, T> registered,
			final Identifier game,
			final java.util.function.Function<T, Identifier> id,
			final java.util.function.Function<T, Identifier> owner,
			final String label
		) {
			if (value.isEmpty()) {
				return;
			}
			T member = value.orElseThrow();
			Identifier memberId = id.apply(member);
			if (!member.equals(registered.get(memberId)) || !owner.apply(member).equals(game)) {
				throw new IllegalArgumentException("preview " + label + " is outside the active game");
			}
		}
	}
}
