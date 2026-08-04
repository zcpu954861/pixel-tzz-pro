package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.Audience;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ArgumentSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSelector;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.GameContextParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.GameContextValue;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.IdentifierKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterErrorMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.PlayerDataParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ScoreParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SimpleParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StorageParameterSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.VanillaComponentSource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataDefinition;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSource;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerDataSourceType;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerSurface;
import io.github.zcpu954861.pixeltzzpro.content.PlayerTerminalDefinitions.PlayerTaskState;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.server.ForcedFlowAuthority.PredicateContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.InvocationContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.AudienceResolution;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageRuntimePorts.ParameterResolution;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.StoredValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatisticValueType;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TaskStatus;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineInstance;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.TimelineStatus;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingRuntime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.ResolutionContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

/**
 * Minecraft adapters for V3B's pure message authority.
 *
 * <p>All methods execute on the server thread. They resolve only compiler-approved sources and
 * return canonical, copied values; no client report can add an audience member, parameter, field,
 * callback, or condition binding.
 */
public final class MessageMinecraftResolver {
	private static final int MAX_CANONICAL_VALUE = 16 * 1024;
	private static final Pattern DURATION = Pattern.compile(
		"^([0-9]+(?:\\.[0-9]+)?)(ns|us|ms|s|t)$"
	);
	private static final UUID ZERO_UUID = new UUID(0L, 0L);
	private static final BindingRuntime BINDINGS = new BindingRuntime();

	private MessageMinecraftResolver() {
	}

	public static ParameterResolution resolveParameters(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final MessageCueDefinition cue,
		final InvocationContext context,
		final InvocationEnvironment environment
	) {
		Objects.requireNonNull(server, "server");
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		for (Map.Entry<String, ParameterDefinition> entry : cue.parameters().entrySet()) {
			String raw = null;
			for (ParameterSource source : entry.getValue().sources()) {
				raw = resolveParameterSource(
					server,
					definitions,
					state,
					context,
					environment,
					entry.getKey(),
					source
				).orElse(null);
				if (raw != null) {
					break;
				}
			}
			if (raw == null) {
				raw = entry.getValue().canonicalDefault().orElse(null);
			}
			String canonical = raw == null
				? null
				: canonicalize(raw, entry.getValue(), definitions, state);
			if (canonical == null) {
				canonical = switch (entry.getValue().onError().mode()) {
					case USE_DEFAULT -> entry.getValue().canonicalDefault().orElse(null);
					case USE_FALLBACK -> entry.getValue()
						.onError()
						.canonicalFallback()
						.orElse(null);
					case FAIL -> null;
				};
				if (canonical != null) {
					canonical = canonicalize(canonical, entry.getValue(), definitions, state);
				}
			}
			if (canonical == null) {
				if (
					entry.getValue().required()
						|| entry.getValue().onError().mode() == ParameterErrorMode.FAIL
				) {
					return ParameterResolution.rejected(
						"参数无法解析或类型不匹配：" + entry.getKey()
					);
				}
				continue;
			}
			values.put(entry.getKey(), canonical);
		}
		Map<String, TimeSpan> durations;
		try {
			durations = MessageProjectionCompiler.authoritativeDurations(
				cue,
				definitions.messageCatalog().textEffects()
			);
		} catch (RuntimeException error) {
			return ParameterResolution.rejected(
				"动态消息时长无法确定：" + safeMessage(error)
			);
		}
		return ParameterResolution.resolved(values, durations);
	}

	public static AudienceResolution resolveAudience(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final MessageCueDefinition cue,
		final InvocationContext context,
		final AudienceSpec cueAudience
	) {
		try {
			Set<UUID> cueTargets = resolveAudienceSpec(
				server,
				definitions,
				state,
				context,
				cueAudience
			);
			LinkedHashMap<String, Set<UUID>> nodeTargets = new LinkedHashMap<>();
			cue.nodes().forEach(node ->
				node.policy().audience().ifPresent(spec ->
					nodeTargets.put(
						node.id(),
						resolveAudienceSpec(server, definitions, state, context, spec)
					)
				)
			);
			return AudienceResolution.resolved(cueTargets, nodeTargets);
		} catch (RuntimeException error) {
			return AudienceResolution.rejected(
				"动态消息受众无法解析：" + safeMessage(error)
			);
		}
	}

	public static boolean conditionMatches(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final MessageInstance instance,
		final UUID recipient,
		final Optional<ConditionSpec> condition
	) {
		if (condition.isEmpty()) {
			return true;
		}
		ConditionSpec spec = condition.orElseThrow();
		try {
			if (spec.expression().isPresent()) {
				BindingContext bindings = bindingContext(
					server,
					definitions,
					state,
					instance,
					recipient
				);
				return BINDINGS.evaluateCondition(
					spec.expression().orElseThrow(),
					bindings
				).value().orElse(false);
			}
			ServerPlayer player = server.getPlayerList().getPlayer(recipient);
			if (player == null) {
				return false;
			}
			WorldStateV2 core = state.core();
			Identifier game = core.activeGameId().orElseThrow();
			Identifier phase = core.activePhaseId().orElseThrow();
			PlayerRecord record = core.players().get(recipient);
			if (record == null) {
				return false;
			}
			return new FrozenPredicateEvaluator(server, definitions.predicateDocuments())
				.evaluate(
					spec.predicate().orElseThrow(),
					new PredicateContext(
						recipient,
						state.activeGameInstanceId().orElse(ZERO_UUID),
						game,
						instance.cue().id(),
						1,
						phase,
						Map.of(),
						record.persistentFields()
					)
				);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.debug(
				"V3B condition failed closed for {} / {}",
				instance.cue().id(),
				recipient,
				error
			);
			return false;
		}
	}

	public static ResolvedComponent resolveField(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final MessageInstance instance,
		final InvocationEnvironment environment,
		final UUID recipient,
		final DynamicFieldDefinition field
	) {
		try {
			RichComponent resolved;
			if (field.source() instanceof ArgumentSource argument) {
				ParameterDefinition definition = instance.cue()
					.parameters()
					.get(argument.parameter());
				String canonical = parameterAuthorized(
					server,
					definitions,
					state,
					instance,
					recipient,
					argument.parameter()
				)
					? instance.canonicalParameters().get(argument.parameter())
					: null;
				resolved = canonical == null || definition == null
					? null
					: componentForParameter(
						server,
						state,
						canonical,
						definition.type()
					);
			} else if (field.source() instanceof BindingSource binding) {
				JsonElement value = bindingContext(
					server,
					definitions,
					state,
					instance,
					recipient
				).resolve(binding.path()).orElse(null);
				resolved = value == null ? null : componentForJson(value);
			} else {
				VanillaComponentSource vanilla = (VanillaComponentSource)field.source();
				resolved = resolveVanillaComponent(
					server,
					environment,
					recipient,
					vanilla.component()
				);
			}
			if (resolved == null) {
				resolved = field.fallback().orElse(null);
			}
			if (resolved == null) {
				resolved = new RichComponent("{\"text\":\"\"}", "");
			}
			return ResolvedComponent.canonicalizeResolved(resolved.canonicalJson());
		} catch (RuntimeException error) {
			RichComponent fallback = field.fallback().orElse(
				new RichComponent("{\"text\":\"\"}", "")
			);
			return ResolvedComponent.canonicalizeResolved(fallback.canonicalJson());
		}
	}

	public static BindingContext bindingContext(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final MessageInstance instance,
		final UUID recipient
	) {
		BindingContext.Builder builder = BindingContext.builder();
		WorldStateV2 core = state.core();
		PlayerRecord player = core.players().get(recipient);
		boolean online = server.getPlayerList().getPlayer(recipient) != null;
		builder.external("player.uuid", new JsonPrimitive(recipient.toString()));
		builder.external(
			"player.name",
			new JsonPrimitive(
				player == null
					? server.services().nameToIdCache()
						.get(recipient)
						.map(profile -> profile.name())
						.orElse(recipient.toString())
					: player.lastKnownName()
			)
		);
		builder.external("player.online", new JsonPrimitive(online));
		builder.external(
			"player.is_host",
			new JsonPrimitive(core.host().map(value -> value.playerId().equals(recipient)).orElse(false))
		);
		core.activeGameId().ifPresent(value -> {
			builder.session("game", new JsonPrimitive(value.toString()));
			builder.external("game.id", new JsonPrimitive(value.toString()));
		});
		core.activePhaseId().ifPresent(value -> {
			builder.session("phase", new JsonPrimitive(value.toString()));
			builder.external("phase.id", new JsonPrimitive(value.toString()));
		});
		if (player != null) {
			builder.viewer("uuid", new JsonPrimitive(recipient.toString()));
			builder.viewer("name", new JsonPrimitive(player.lastKnownName()));
			builder.viewer("online", new JsonPrimitive(online));
			builder.external("player.role.id", new JsonPrimitive(player.roleId().toString()));
			Optional.ofNullable(definitions.roles().get(player.roleId())).ifPresent(role ->
				builder.external(
					"player.role.name",
					new JsonPrimitive(role.name().plainText())
				)
			);
			player.teamId().ifPresent(team -> {
				builder.external("player.team.id", new JsonPrimitive(team.toString()));
				Optional.ofNullable(definitions.teams().get(team)).ifPresent(value ->
					builder.external(
						"player.team.name",
						new JsonPrimitive(value.name().plainText())
					)
				);
			});
			builder.external(
				"player.life_state.id",
				new JsonPrimitive(player.lifeStateId().toString())
			);
			Optional.ofNullable(definitions.lifeStates().get(player.lifeStateId())).ifPresent(value ->
				builder.external(
					"player.life_state.name",
					new JsonPrimitive(value.name().plainText())
				)
			);
			addDynamicPlayerData(server, definitions, state, player, online, builder);
		}
		state.timeline().flatMap(TimelineInstance::currentTask).ifPresent(task -> {
			builder.task("id", new JsonPrimitive(task.taskId().toString()));
			builder.task("elapsed_ticks", new JsonPrimitive(task.elapsedTicks()));
			builder.external("task/current/id", new JsonPrimitive(task.taskId().toString()));
			builder.external("task/current/elapsed_ticks", new JsonPrimitive(task.elapsedTicks()));
			Optional.ofNullable(definitions.tasks().get(task.taskId())).ifPresent(definition -> {
				builder.task("name", new JsonPrimitive(definition.name().plainText()));
				builder.external(
					"task/current/name",
					new JsonPrimitive(definition.name().plainText())
				);
			});
		});
		instance.canonicalParameters().forEach((key, value) -> {
			if (
				parameterAuthorized(
					server,
					definitions,
					state,
					instance,
					recipient,
					key
				)
			) {
				builder.external("argument." + key, new JsonPrimitive(value));
			}
		});
		return builder.build();
	}

	/**
	 * Resolves a parameter's own projection audience for one recipient without mutating the
	 * invocation-wide canonical value. Missing definitions and failed live resolution fail closed.
	 */
	public static boolean parameterAuthorized(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final MessageInstance instance,
		final UUID recipient,
		final String parameterId
	) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(instance, "instance");
		Objects.requireNonNull(recipient, "recipient");
		Objects.requireNonNull(parameterId, "parameterId");
		ParameterDefinition parameter = instance.cue().parameters().get(parameterId);
		if (parameter == null) {
			return false;
		}
		if (parameter.audience().isEmpty()) {
			return true;
		}
		try {
			return resolveAudienceSpec(
				server,
				definitions,
				state,
				instance.context(),
				parameter.audience().orElseThrow()
			).contains(recipient);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.debug(
				"V3B parameter audience failed closed for {} / {} / {}",
				instance.cue().id(),
				parameterId,
				recipient,
				error
			);
			return false;
		}
	}

	private static Optional<String> resolveParameterSource(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final InvocationContext context,
		final InvocationEnvironment environment,
		final String parameterName,
		final ParameterSource source
	) {
		if (source instanceof SimpleParameterSource simple) {
			return switch (simple.kind()) {
				case CALL_ARGUMENT -> Optional.ofNullable(
					environment.arguments().get(parameterName)
				);
				case INVOKER -> context.invokerId().map(UUID::toString);
				case TARGET -> singleTarget(context.callTargets()).map(UUID::toString);
				case ORIGIN -> Optional.of(
					plainDecimal(environment.origin().x())
						+ " "
						+ plainDecimal(environment.origin().y())
						+ " "
						+ plainDecimal(environment.origin().z())
				);
				default -> Optional.empty();
			};
		}
		if (source instanceof ScoreParameterSource score) {
			Objective objective = server.getScoreboard().getObjective(score.objective());
			if (objective == null) {
				return Optional.empty();
			}
			String holderName = score.holder().orElse("@s");
			if (holderName.equals("@s")) {
				holderName = context.invokerId()
					.flatMap(id -> playerName(server, id))
					.or(() -> singleTarget(context.callTargets()).flatMap(id -> playerName(server, id)))
					.orElse(null);
			}
			if (holderName == null) {
				return Optional.empty();
			}
			ReadOnlyScoreInfo info = server.getScoreboard()
				.getPlayerScoreInfo(ScoreHolder.forNameOnly(holderName), objective);
			return info == null ? Optional.empty() : Optional.of(Integer.toString(info.value()));
		}
		if (source instanceof StorageParameterSource storage) {
			try {
				Tag root = server.getCommandStorage().get(storage.storage());
				List<Tag> matches = NbtPathArgument.NbtPath.of(storage.path()).get(root);
				return matches.size() == 1 ? tagText(matches.getFirst()) : Optional.empty();
			} catch (CommandSyntaxException error) {
				return Optional.empty();
			}
		}
		if (source instanceof GameContextParameterSource gameContext) {
			return switch (gameContext.value()) {
				case GAME -> context.gameId().map(Identifier::toString);
				case PHASE -> context.phaseId().map(Identifier::toString);
				case TASK -> context.taskId().map(Identifier::toString);
				case EVENT -> Optional.ofNullable(context.arguments().get("event"));
				case STATISTIC -> Optional.ofNullable(context.arguments().get("statistic"));
			};
		}
		PlayerDataParameterSource playerData = (PlayerDataParameterSource)source;
		UUID playerId = context.invokerId()
			.or(() -> singleTarget(context.callTargets()))
			.orElse(null);
		if (playerId == null) {
			return Optional.empty();
		}
		PlayerRecord record = state.core().players().get(playerId);
		if (record == null) {
			return Optional.empty();
		}
		PlayerDataDefinition definition = definitions.playerData().get(playerData.field());
		return definition == null
			? Optional.empty()
			: playerDataValue(definitions, state, record, definition).flatMap(
				MessageMinecraftResolver::jsonText
			);
	}

	private static Set<UUID> resolveAudienceSpec(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final InvocationContext context,
		final AudienceSpec spec
	) {
		WorldStateV2 core = state.core();
		Optional<UUID> host = core.host().map(WorldStateV2.HostRecord::playerId);
		LinkedHashSet<UUID> result = new LinkedHashSet<>();
		for (AudienceSelector selector : spec.selectors()) {
			Set<UUID> base = switch (selector.source()) {
				case ALL -> core.players().keySet();
				case CURRENT_HOST -> host.map(Set::of).orElse(Set.of());
				case CALL_TARGETS -> context.callTargets();
				case INVOKER -> context.invokerId().map(Set::of).orElse(Set.of());
			};
			Audience filter = new Audience(
				selector.roles(),
				selector.teams(),
				selector.lifeStates(),
				selector.roleTags(),
				selector.teamTags(),
				selector.lifeStateTags(),
				selector.excludeHost(),
				selector.onlineOnly()
			);
			for (UUID playerId : base) {
				PlayerRecord player = core.players().get(playerId);
				boolean online = server.getPlayerList().getPlayer(playerId) != null;
				if (
					player != null
						&& AudienceMatcher.matches(definitions, filter, player, host, online)
				) {
					result.add(playerId);
				}
			}
		}
		return Set.copyOf(result);
	}

	private static String canonicalize(
		final String raw,
		final ParameterDefinition definition,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state
	) {
		if (raw.length() > MAX_CANONICAL_VALUE) {
			return null;
		}
		try {
			return switch (definition.type()) {
				case STRING -> raw;
				case INTEGER -> Long.toString(Long.parseLong(raw.strip()));
				case DECIMAL -> new BigDecimal(raw.strip()).stripTrailingZeros().toPlainString();
				case BOOLEAN -> {
					String value = raw.strip().toLowerCase(Locale.ROOT);
					yield value.equals("true") || value.equals("false") ? value : null;
				}
				case IDENTIFIER -> {
					Identifier id = Identifier.tryParse(raw.strip());
					yield id != null && identifierAllowed(id, definition.identifierKind(), definitions)
						? id.toString()
						: null;
				}
				case PLAYER -> canonicalPlayer(raw, state);
				case COMPONENT -> canonicalComponent(raw);
				case DURATION -> Long.toString(parseDuration(raw));
			};
		} catch (RuntimeException error) {
			return null;
		}
	}

	private static String canonicalPlayer(final String raw, final WorldStateV3 state) {
		try {
			UUID id = UUID.fromString(raw.strip());
			return state.core().players().containsKey(id) ? id.toString() : null;
		} catch (IllegalArgumentException ignored) {
			return state.core()
				.players()
				.values()
				.stream()
				.filter(player -> player.lastKnownName().equalsIgnoreCase(raw.strip()))
				.map(PlayerRecord::playerId)
				.sorted()
				.map(UUID::toString)
				.findFirst()
				.orElse(null);
		}
	}

	private static String canonicalComponent(final String raw) {
		JsonElement json = JsonParser.parseString(raw);
		Component component = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
			.result()
			.orElse(null);
		if (component == null) {
			return null;
		}
		return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component)
			.result()
			.map(JsonElement::toString)
			.orElse(null);
	}

	private static boolean identifierAllowed(
		final Identifier id,
		final IdentifierKind kind,
		final DefinitionSnapshot definitions
	) {
		return switch (kind) {
			case ANY -> true;
			case GAME -> definitions.games().containsKey(id);
			case PHASE -> definitions.phases().containsKey(id);
			case TASK -> definitions.tasks().containsKey(id);
			case FIELD -> definitions.fields().containsKey(id);
			case ROLE -> definitions.roles().containsKey(id);
			case TEAM -> definitions.teams().containsKey(id);
			case LIFE_STATE -> definitions.lifeStates().containsKey(id);
			case PLAYER_DATA -> definitions.playerData().containsKey(id);
			case EVENT, STATISTIC -> true;
		};
	}

	private static long parseDuration(final String raw) {
		Matcher matcher = DURATION.matcher(raw.strip().toLowerCase(Locale.ROOT));
		if (!matcher.matches()) {
			throw new IllegalArgumentException("invalid duration");
		}
		BigDecimal value = new BigDecimal(matcher.group(1));
		BigDecimal multiplier = switch (matcher.group(2)) {
			case "ns" -> BigDecimal.ONE;
			case "us" -> BigDecimal.valueOf(1_000L);
			case "ms" -> BigDecimal.valueOf(1_000_000L);
			case "s" -> BigDecimal.valueOf(1_000_000_000L);
			case "t" -> BigDecimal.valueOf(TimeSpan.NANOS_PER_TICK);
			default -> throw new IllegalArgumentException("invalid duration unit");
		};
		return value.multiply(multiplier).longValueExact();
	}

	private static RichComponent componentForParameter(
		final MinecraftServer server,
		final WorldStateV3 state,
		final String canonical,
		final ParameterType type
	) {
		if (type == ParameterType.COMPONENT) {
			Component component = ComponentSerialization.CODEC.parse(
				JsonOps.INSTANCE,
				JsonParser.parseString(canonical)
			).result().orElse(Component.empty());
			return new RichComponent(canonical, component.getString());
		}
		if (type == ParameterType.PLAYER) {
			try {
				UUID playerId = UUID.fromString(canonical);
				PlayerRecord record = state.core().players().get(playerId);
				String name = record == null
					? server.services().nameToIdCache()
						.get(playerId)
						.map(profile -> profile.name())
						.orElse(canonical)
					: record.lastKnownName();
				return literalComponent(name);
			} catch (IllegalArgumentException ignored) {
				return literalComponent(canonical);
			}
		}
		return literalComponent(canonical);
	}

	private static RichComponent componentForJson(final JsonElement value) {
		if (value.isJsonPrimitive()) {
			return literalComponent(value.getAsString());
		}
		return literalComponent(value.toString());
	}

	private static RichComponent literalComponent(final String value) {
		String json = ComponentSerialization.CODEC.encodeStart(
			JsonOps.INSTANCE,
			Component.literal(value)
		).result().orElseThrow().toString();
		return new RichComponent(json, value);
	}

	private static RichComponent resolveVanillaComponent(
		final MinecraftServer server,
		final InvocationEnvironment environment,
		final UUID recipient,
		final RichComponent source
	) {
		Component parsed = ComponentSerialization.CODEC.parse(
			JsonOps.INSTANCE,
			JsonParser.parseString(source.canonicalJson())
		).result().orElseThrow();
		ServerPlayer player = server.getPlayerList().getPlayer(recipient);
		CommandSourceStack command = player == null
			? server.createCommandSourceStack()
			: player.createCommandSourceStack();
		ServerLevel level = server.getLevel(environment.level());
		if (level != null) {
			command = command.withLevel(level).withPosition(environment.origin());
		}
		try {
			Component resolved = ComponentUtils.resolve(ResolutionContext.create(command), parsed);
			/*
			 * Vanilla dynamic components represent a missing score/NBT/selector result as an
			 * empty component rather than throwing.  At the data-pack boundary that is still a
			 * failed lookup: keeping the empty component would suppress the registered fallback
			 * and leave an apparently missing field in the finished presentation.
			 */
			if (resolved.getString().isEmpty()) {
				return null;
			}
			String json = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, resolved)
				.result()
				.orElseThrow()
				.toString();
			return new RichComponent(json, resolved.getString());
		} catch (CommandSyntaxException error) {
			return null;
		}
	}

	private static void addDynamicPlayerData(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final PlayerRecord player,
		final boolean online,
		final BindingContext.Builder builder
	) {
		Optional<UUID> host = state.core().host().map(WorldStateV2.HostRecord::playerId);
		Identifier game = state.core().activeGameId().orElse(null);
		Identifier phase = state.core().activePhaseId().orElse(null);
		Optional<Identifier> task = state.timeline()
			.flatMap(TimelineInstance::currentTask)
			.map(TaskInstance::taskId);
		Optional<PlayerTaskState> taskState = taskState(state.timeline());
		if (game == null || phase == null) {
			return;
		}
		for (PlayerDataDefinition data : definitions.playerData().values().stream()
			.sorted(Comparator.comparing(PlayerDataDefinition::id)).toList()) {
			if (
				!data.game().equals(game)
					|| !data.surfaces().contains(PlayerSurface.DYNAMIC_MESSAGE)
					|| !AudienceMatcher.matches(definitions, data.audience(), player, host, online)
					|| (!data.phases().isEmpty() && !data.phases().contains(phase))
					|| (!data.tasks().isEmpty() && task.filter(data.tasks()::contains).isEmpty())
					|| (
						!data.taskStates().isEmpty()
							&& taskState.filter(data.taskStates()::contains).isEmpty()
					)
			) {
				continue;
			}
			if (
				data.predicate().isPresent()
					&& !safePlayerDataPredicate(server, definitions, state, player, data)
			) {
				continue;
			}
			playerDataValue(definitions, state, player, data).ifPresent(value -> {
				builder.personal(data.id(), value);
				builder.external("personal/" + data.id(), value);
			});
		}
	}

	private static boolean safePlayerDataPredicate(
		final MinecraftServer server,
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final PlayerRecord player,
		final PlayerDataDefinition data
	) {
		try {
			return new FrozenPredicateEvaluator(server, definitions.predicateDocuments())
				.evaluate(
					data.predicate().orElseThrow(),
					new PredicateContext(
						player.playerId(),
						state.activeGameInstanceId().orElse(ZERO_UUID),
						data.game(),
						data.id(),
						1,
						state.core().activePhaseId().orElseThrow(),
						Map.of(),
						player.persistentFields()
					)
				);
		} catch (RuntimeException error) {
			return false;
		}
	}

	private static Optional<JsonElement> playerDataValue(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final PlayerRecord player,
		final PlayerDataDefinition data
	) {
		PlayerDataSource source = data.source();
		return switch (source.type()) {
			case ROLE -> Optional.of(new JsonPrimitive(player.roleId().toString()));
			case TEAM -> player.teamId().map(Identifier::toString).map(JsonPrimitive::new);
			case LIFE_STATE -> Optional.of(new JsonPrimitive(player.lifeStateId().toString()));
			case INITIALIZED -> Optional.of(new JsonPrimitive(playerInitialized(definitions, state, player)));
			case READY -> Optional.of(new JsonPrimitive(playerReady(state, player.playerId())));
			case FIELD, EXCLUSIVE_CHOICE -> source.id()
				.flatMap(id -> persistentValue(definitions, state, player, id, source.type() == PlayerDataSourceType.EXCLUSIVE_CHOICE));
			case GAME_ELAPSED_TICKS -> state.timeline()
				.map(TimelineInstance::gameElapsedTicks)
				.map(JsonPrimitive::new);
			case TASK_ID,
				TASK_NAME,
				TASK_DESCRIPTION,
				TASK_STATUS,
				TASK_ELAPSED_TICKS,
				TASK_REMAINING_TICKS,
				TASK_PROGRESS,
				TASK_RESULT,
				TASK_STATISTIC -> taskDataValue(definitions, state.timeline(), source);
		};
	}

	private static Optional<JsonElement> persistentValue(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final PlayerRecord player,
		final Identifier fieldId,
		final boolean exclusive
	) {
		FieldDefinition field = definitions.fields().get(fieldId);
		PersistentFieldValue value = player.persistentFields().get(fieldId);
		if (
			field == null
				|| value == null
				|| value.fieldVersion() != field.version()
				|| (exclusive && field.type() != FieldType.EXCLUSIVE_CHOICE)
		) {
			return Optional.empty();
		}
		if (
			exclusive
				&& state.exclusiveReservations()
					.stream()
					.noneMatch(reservation ->
						reservation.fieldId().equals(fieldId)
							&& reservation.ownerId().equals(player.playerId())
							&& reservation.status() == WorldStateV3.ReservationStatus.LOCKED
					)
		) {
			return Optional.empty();
		}
		return Optional.of(storedValue(value.value()));
	}

	private static Optional<JsonElement> taskDataValue(
		final DefinitionSnapshot definitions,
		final Optional<TimelineInstance> timeline,
		final PlayerDataSource source
	) {
		TaskInstance task = findTask(timeline, source.id()).orElse(null);
		if (task == null) {
			return Optional.empty();
		}
		TaskDefinition definition = definitions.tasks().get(task.taskId());
		if (definition == null) {
			return Optional.empty();
		}
		return switch (source.type()) {
			case TASK_ID -> Optional.of(new JsonPrimitive(task.taskId().toString()));
			case TASK_NAME -> Optional.of(new JsonPrimitive(definition.name().plainText()));
			case TASK_DESCRIPTION -> definition.description()
				.map(value -> new JsonPrimitive(value.plainText()));
			case TASK_STATUS -> Optional.of(new JsonPrimitive(task.status().getSerializedName()));
			case TASK_ELAPSED_TICKS -> Optional.of(new JsonPrimitive(task.elapsedTicks()));
			case TASK_REMAINING_TICKS -> definition.durationTicks().isPresent()
				? Optional.of(
					new JsonPrimitive(
						Math.max(0L, definition.durationTicks().orElseThrow() - task.elapsedTicks())
					)
				)
				: Optional.empty();
			case TASK_PROGRESS -> source.key()
				.flatMap(key -> numericStatistic(task, key))
				.map(JsonPrimitive::new);
			case TASK_RESULT -> task.result()
				.filter(result -> source.key().map(result.resultId()::equals).orElse(true))
				.map(result -> new JsonPrimitive(result.resultId()));
			case TASK_STATISTIC -> source.key()
				.flatMap(key -> task.statistics().stream()
					.filter(value -> value.statisticId().equals(key))
					.findFirst())
				.flatMap(MessageMinecraftResolver::statisticJson);
			default -> Optional.empty();
		};
	}

	private static Optional<TaskInstance> findTask(
		final Optional<TimelineInstance> timeline,
		final Optional<Identifier> requested
	) {
		if (timeline.isEmpty()) {
			return Optional.empty();
		}
		TimelineInstance value = timeline.orElseThrow();
		if (requested.isEmpty()) {
			return value.currentTask();
		}
		Identifier id = requested.orElseThrow();
		if (value.currentTask().map(TaskInstance::taskId).filter(id::equals).isPresent()) {
			return value.currentTask();
		}
		for (int index = value.taskHistory().size() - 1; index >= 0; index--) {
			TaskInstance candidate = value.taskHistory().get(index);
			if (candidate.taskId().equals(id)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private static Optional<Long> numericStatistic(final TaskInstance task, final String key) {
		return task.statistics()
			.stream()
			.filter(value -> value.statisticId().equals(key))
			.filter(value ->
				value.type() == TaskStatisticValueType.INTEGER
					|| value.type() == TaskStatisticValueType.DURATION_TICKS
			)
			.map(TaskStatisticValue::longValue)
			.flatMap(Optional::stream)
			.findFirst();
	}

	private static Optional<JsonElement> statisticJson(final TaskStatisticValue value) {
		return switch (value.type()) {
			case BOOLEAN -> value.booleanValue().map(JsonPrimitive::new);
			case INTEGER, DURATION_TICKS -> value.longValue().map(JsonPrimitive::new);
			case STRING -> value.stringValue().map(JsonPrimitive::new);
			case IDENTIFIER -> value.identifierValue().map(Identifier::toString).map(JsonPrimitive::new);
			case PLAYER -> value.playerValue()
				.map(player -> player.playerId().toString())
				.map(JsonPrimitive::new);
		};
	}

	private static Optional<PlayerTaskState> taskState(
		final Optional<TimelineInstance> timeline
	) {
		if (timeline.isEmpty()) {
			return Optional.empty();
		}
		TimelineInstance value = timeline.orElseThrow();
		if (value.status() == TimelineStatus.BLOCKED) {
			return Optional.of(PlayerTaskState.BLOCKED);
		}
		if (value.paused()) {
			return Optional.of(PlayerTaskState.PAUSED);
		}
		return switch (value.status()) {
			case PRE_START -> Optional.empty();
			case STARTING -> Optional.of(PlayerTaskState.STARTING);
			case RUNNING -> Optional.of(PlayerTaskState.RUNNING);
			case SETTLING -> Optional.of(PlayerTaskState.SETTLING);
			case INTERMISSION -> Optional.of(PlayerTaskState.INTERMISSION);
			case COMPLETED -> Optional.of(PlayerTaskState.COMPLETED);
			case INTERRUPTED -> Optional.of(PlayerTaskState.INTERRUPTED);
			case BLOCKED -> Optional.of(PlayerTaskState.BLOCKED);
		};
	}

	private static boolean playerInitialized(
		final DefinitionSnapshot definitions,
		final WorldStateV3 state,
		final PlayerRecord player
	) {
		var role = definitions.roles().get(player.roleId());
		return role != null
			&& TabListDisplay.hasCurrentIdentityCredential(
				player.playerId(),
				player.roleId(),
				role.initializationFlow(),
				role.initializationFlow()
					.map(definitions.flows()::get)
					.map(value -> value.version()),
				player.pendingRoleChange(),
				state.core().completionHistory()
			);
	}

	private static boolean playerReady(final WorldStateV3 state, final UUID playerId) {
		return state.readiness()
			.map(value -> value.flow().runtime().members().get(playerId))
			.map(WorldStateV2.FlowMemberState::status)
			.filter(value ->
				value == WorldStateV2.FlowMemberStatus.COMPLETED
					|| value == WorldStateV2.FlowMemberStatus.ALREADY_COMPLETE
			)
			.isPresent();
	}

	private static JsonElement storedValue(final StoredValue value) {
		return switch (value.type()) {
			case BOOLEAN -> new JsonPrimitive(value.booleanValue().orElseThrow());
			case INTEGER -> new JsonPrimitive(value.integerValue().orElseThrow());
			case STRING, SINGLE_CHOICE -> new JsonPrimitive(value.stringValue().orElseThrow());
			case IDENTIFIER -> new JsonPrimitive(value.identifierValue().orElseThrow().toString());
			case MULTI_CHOICE -> {
				com.google.gson.JsonArray result = new com.google.gson.JsonArray();
				value.choices().forEach(result::add);
				yield result;
			}
		};
	}

	private static Optional<String> jsonText(final JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return Optional.empty();
		}
		return value.isJsonPrimitive()
			? Optional.of(value.getAsString())
			: Optional.of(value.toString());
	}

	private static Optional<String> tagText(final Tag value) {
		return value.asString().or(() -> value.asNumber().map(Object::toString));
	}

	private static Optional<UUID> singleTarget(final Set<UUID> targets) {
		return targets.size() == 1 ? Optional.of(targets.iterator().next()) : Optional.empty();
	}

	private static Optional<String> playerName(
		final MinecraftServer server,
		final UUID playerId
	) {
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		return player == null
			? server.services().nameToIdCache().get(playerId).map(profile -> profile.name())
			: Optional.of(player.getScoreboardName());
	}

	private static String plainDecimal(final double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		return message == null || message.isBlank()
			? error.getClass().getSimpleName()
			: message.length() > 512 ? message.substring(0, 512) : message;
	}

	public record InvocationEnvironment(
		ResourceKey<Level> level,
		Vec3 origin,
		Optional<UUID> invokerId,
		Set<UUID> callTargets,
		Map<String, String> arguments
	) {
		public InvocationEnvironment {
			level = Objects.requireNonNull(level, "level");
			origin = Objects.requireNonNull(origin, "origin");
			invokerId = Objects.requireNonNull(invokerId, "invokerId");
			callTargets = Set.copyOf(callTargets);
			arguments = Map.copyOf(arguments);
		}

		public static InvocationEnvironment from(
			final CommandSourceStack source,
			final Set<UUID> callTargets,
			final Map<String, String> arguments
		) {
			return new InvocationEnvironment(
				source.getLevel().dimension(),
				source.getPosition(),
				Optional.ofNullable(source.getPlayer()).map(ServerPlayer::getUUID),
				callTargets,
				arguments
			);
		}
	}
}
