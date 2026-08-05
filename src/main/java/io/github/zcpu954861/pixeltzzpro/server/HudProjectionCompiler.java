package io.github.zcpu954861.pixeltzzpro.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.GameDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.*;
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
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.ResolvedField;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.SourceValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.StringValue;
import io.github.zcpu954861.pixeltzzpro.server.HudBindingResolver.Value;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Server-side compiler for one already-authorized generic HUD or countdown projection.
 *
 * <p>The emitted JSON contains only resolved presentation values. Definition source descriptors,
 * score holders, storage paths, selectors, predicates and callback functions never cross this
 * boundary.</p>
 */
public final class HudProjectionCompiler {
	public static final int MAX_REPLACE_BYTES = 131_072;
	private static final int MAX_PROJECTED_NODES = HudDefinitions.MAX_EXPANDED_COMPONENTS;

	private HudProjectionCompiler() {
	}

	public static Projection projectDock(
		final DefinitionSnapshot definitions,
		final GameDefinition game,
		final ProjectionContext context
	) {
		Objects.requireNonNull(game, "game");
		if (!game.equals(definitions.games().get(game.id()))) {
			return Projection.failClosed("HUD game is not part of the supplied definition snapshot");
		}
		return game.hudProfile()
			.map(profile -> projectDock(definitions, profile, context))
			.orElseGet(Projection::clear);
	}

	public static Projection projectDock(
		final DefinitionSnapshot definitions,
		final Identifier profileId,
		final ProjectionContext context
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(profileId, "profileId");
		Objects.requireNonNull(context, "context");
		HudRouteAuthority.Resolution route = HudRouteAuthority.resolve(
			definitions.hudCatalog(),
			profileId,
			context.audience()
		);
		if (route.code() == HudRouteAuthority.Code.EMPTY) {
			return Projection.clear();
		}
		if (route.code() != HudRouteAuthority.Code.SELECTED) {
			return Projection.failClosed(route.diagnostic());
		}
		HudProfileDefinition profile = route.profile().orElseThrow();
		HudLayoutDefinition selected = route.layout().orElseThrow();
		TreeProjection tree = projectLayout(
			definitions.hudCatalog(),
			selected,
			context,
			Surface.DOCK
		);
		if (tree.code() != TreeCode.REPLACE) {
			return projection(tree);
		}
		VariantForest variants = projectVariants(
			definitions.hudCatalog(),
			tree,
			context,
			Surface.DOCK
		);
		if (!variants.valid()) {
			return Projection.failClosed(variants.diagnostics());
		}

		JsonObject root = new JsonObject();
		root.addProperty("format_version", 1);
		root.addProperty("surface", "dock");
		root.addProperty("profile_id", profile.id().toString());
		root.addProperty("profile_content_version", contentVersion(profile.sha256()));
		root.addProperty("layout_id", tree.layout().orElseThrow().id().toString());
		root.addProperty("mode", serialized(tree.mode().orElseThrow()));
		root.addProperty("root", tree.root().orElseThrow());
		root.add("nodes", variants.nodes());
		root.add("layout_variants", variants.layouts());
		root.add("policy", policy(profile.clientPolicy()));
		return document(root, tree.layout().orElseThrow().id(), variants.diagnostics());
	}

	public static Projection projectCountdown(
		final DefinitionSnapshot definitions,
		final Identifier countdownId,
		final CountdownProjectionContext context
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(countdownId, "countdownId");
		CountdownDefinition countdown = definitions.hudCatalog().countdowns().get(countdownId);
		if (countdown == null) {
			return Projection.failClosed("countdown definition is unavailable: " + countdownId);
		}
		return projectCountdown(definitions, countdown, context);
	}

	public static Projection projectCountdown(
		final DefinitionSnapshot definitions,
		final CountdownDefinition countdown,
		final CountdownProjectionContext context
	) {
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(context, "context");
		if (!countdown.equals(definitions.hudCatalog().countdowns().get(countdown.id()))) {
			return Projection.failClosed("countdown is not part of the supplied definition snapshot");
		}
		if (!HudRouteAuthority.matches(countdown.displayAudience(), context.projection().audience())) {
			return Projection.clear();
		}
		HudLayoutDefinition layout = definitions.hudCatalog().layouts().get(countdown.layout());
		if (layout == null || layout.surface() != Surface.COUNTDOWN) {
			return Projection.failClosed("countdown layout is unavailable");
		}

		ProjectionContext projectionContext = context.projection().withAccess(
			new CountdownBindingAccess(context, context.projection().access())
		);
		TreeProjection tree = projectLayout(
			definitions.hudCatalog(),
			layout,
			projectionContext,
			Surface.COUNTDOWN
		);
		if (tree.code() != TreeCode.REPLACE) {
			return projection(tree);
		}
		VariantForest variants = projectVariants(
			definitions.hudCatalog(),
			tree,
			projectionContext,
			Surface.COUNTDOWN
		);
		if (!variants.valid()) {
			return Projection.failClosed(variants.diagnostics());
		}

		JsonObject root = new JsonObject();
		root.addProperty("format_version", 1);
		root.addProperty("surface", "countdown");
		root.addProperty("countdown_instance_id", context.countdownInstanceId().toString());
		root.addProperty("layout_id", tree.layout().orElseThrow().id().toString());
		root.addProperty("mode", serialized(tree.mode().orElseThrow()));
		root.addProperty("root", tree.root().orElseThrow());
		root.add("nodes", variants.nodes());
		root.add("layout_variants", variants.layouts());
		root.addProperty("total_ticks", context.totalTicks());
		root.add("remaining", timer(
			new ClockValue(
				context.serverTick(),
				context.remainingTicks(),
				context.paused() ? 0.0D : -1.0D,
				context.paused()
			),
			TimerFormat.M_SS,
			Optional.empty()
		));
		return document(root, tree.layout().orElseThrow().id(), variants.diagnostics());
	}

	private static Projection projection(final TreeProjection tree) {
		return tree.code() == TreeCode.CLEAR
			? Projection.clear(tree.diagnostics())
			: Projection.failClosed(tree.diagnostics());
	}

	private static TreeProjection projectLayout(
		final HudCatalog catalog,
		final HudLayoutDefinition initial,
		final ProjectionContext context,
		final Surface expectedSurface
	) {
		Identifier current = initial.id();
		Set<Identifier> visited = new LinkedHashSet<>();
		List<String> diagnostics = new ArrayList<>();
		while (visited.add(current) && visited.size() <= HudDefinitions.MAX_LAYOUT_DEGRADATION_DEPTH + 1) {
			HudLayoutDefinition layout = catalog.layouts().get(current);
			if (layout == null || layout.surface() != expectedSurface) {
				diagnostics.add("layout degradation target is unavailable or uses the wrong surface: " + current);
				return TreeProjection.failClosed(diagnostics);
			}
			if (layout.audience().filter(value -> !HudRouteAuthority.matches(value, context.audience())).isPresent()) {
				return TreeProjection.clear(diagnostics);
			}
			ProjectionMode mode = effectiveMode(layout, context.mode());
			Identifier root = root(layout, mode);
			TreeBuilder builder = new TreeBuilder(catalog, context);
			String projectedRoot = builder.project(
				root,
				"variant.0." + serialized(mode),
				Optional.empty()
			);
			diagnostics.addAll(builder.diagnostics());
			if (builder.requestedDegradation().isPresent()) {
				current = builder.requestedDegradation().orElseThrow();
				continue;
			}
			if (builder.criticalHidden() || builder.failed()) {
				if (layout.degradationLayout().isPresent()) {
					current = layout.degradationLayout().orElseThrow();
					continue;
				}
				diagnostics.add(builder.criticalHidden()
					? "critical HUD content could not be projected"
					: "HUD component tree could not be projected");
				return TreeProjection.failClosed(diagnostics);
			}
			if (projectedRoot == null || builder.nodes().isEmpty()) {
				return TreeProjection.clear(diagnostics);
			}
			return TreeProjection.replace(
				layout,
				mode,
				projectedRoot,
				builder.nodeArray(),
				diagnostics
			);
		}
		diagnostics.add("layout degradation cycle or depth overflow");
		return TreeProjection.failClosed(diagnostics);
	}

	private static Identifier root(final HudLayoutDefinition layout, final ProjectionMode mode) {
		return switch (mode) {
			case NORMAL -> layout.root();
			case COMPACT -> layout.compactRoot().orElse(layout.root());
			case SUMMARY -> layout.summaryRoot().orElseGet(() -> layout.compactRoot().orElse(layout.root()));
		};
	}

	private static ProjectionMode effectiveMode(
		final HudLayoutDefinition layout,
		final ProjectionMode requested
	) {
		return switch (requested) {
			case NORMAL -> ProjectionMode.NORMAL;
			case COMPACT -> layout.compactRoot().isPresent()
				? ProjectionMode.COMPACT
				: ProjectionMode.NORMAL;
			case SUMMARY -> layout.summaryRoot().isPresent()
				? ProjectionMode.SUMMARY
				: layout.compactRoot().isPresent()
					? ProjectionMode.COMPACT
					: ProjectionMode.NORMAL;
		};
	}

	private static VariantForest projectVariants(
		final HudCatalog catalog,
		final TreeProjection active,
		final ProjectionContext context,
		final Surface expectedSurface
	) {
		HudLayoutDefinition activeLayout = active.layout().orElse(null);
		ProjectionMode activeMode = active.mode().orElse(null);
		String activeRoot = active.root().orElse(null);
		List<String> diagnostics = new ArrayList<>(active.diagnostics());
		if (activeLayout == null || activeMode == null || activeRoot == null) {
			diagnostics.add("active HUD variant is incomplete");
			return VariantForest.invalid(diagnostics);
		}

		Map<String, JsonObject> nodes = new LinkedHashMap<>();
		if (!mergeNodes(nodes, active.nodes(), diagnostics)) {
			return VariantForest.invalid(diagnostics);
		}
		List<LayoutVariantProjection> layouts = new ArrayList<>();
		EnumMap<ProjectionMode, String> activeRoots = new EnumMap<>(ProjectionMode.class);
		activeRoots.put(activeMode, activeRoot);
		if (
			projectRegisteredRoots(
				catalog,
				activeLayout,
				0,
				Optional.of(activeMode),
				context,
				nodes,
				activeRoots,
				diagnostics
			) == VariantCode.INVALID
		) {
			return VariantForest.invalid(diagnostics);
		}
		layouts.add(new LayoutVariantProjection(activeLayout, activeRoots));

		Set<Identifier> visited = new LinkedHashSet<>();
		visited.add(activeLayout.id());
		Optional<Identifier> next = activeLayout.degradationLayout();
		int rank = 1;
		while (next.isPresent()) {
			if (rank > HudDefinitions.MAX_LAYOUT_DEGRADATION_DEPTH || !visited.add(next.orElseThrow())) {
				diagnostics.add("layout degradation cycle or depth overflow");
				return VariantForest.invalid(diagnostics);
			}
			HudLayoutDefinition layout = catalog.layouts().get(next.orElseThrow());
			if (layout == null || layout.surface() != expectedSurface) {
				diagnostics.add("layout degradation closure is unavailable or uses the wrong surface");
				return VariantForest.invalid(diagnostics);
			}
			if (layout.audience().filter(value -> !HudRouteAuthority.matches(value, context.audience())).isPresent()) {
				break;
			}
			EnumMap<ProjectionMode, String> roots = new EnumMap<>(ProjectionMode.class);
			VariantCode code = projectRegisteredRoots(
				catalog,
				layout,
				rank,
				Optional.empty(),
				context,
				nodes,
				roots,
				diagnostics
			);
			if (code == VariantCode.INVALID) {
				return VariantForest.invalid(diagnostics);
			}
			if (roots.isEmpty()) {
				break;
			}
			layouts.add(new LayoutVariantProjection(layout, roots));
			next = layout.degradationLayout();
			rank++;
		}

		Set<Identifier> included = layouts.stream()
			.map(value -> value.layout().id())
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		JsonArray layoutArray = new JsonArray();
		for (LayoutVariantProjection layout : layouts) {
			layoutArray.add(layoutVariant(layout, included));
		}
		JsonArray nodeArray = new JsonArray();
		nodes.values().forEach(nodeArray::add);
		return VariantForest.valid(layoutArray, nodeArray, diagnostics);
	}

	private static VariantCode projectRegisteredRoots(
		final HudCatalog catalog,
		final HudLayoutDefinition layout,
		final int rank,
		final Optional<ProjectionMode> alreadyProjected,
		final ProjectionContext context,
		final Map<String, JsonObject> nodes,
		final EnumMap<ProjectionMode, String> roots,
		final List<String> diagnostics
	) {
		for (ProjectionMode mode : ProjectionMode.values()) {
			Optional<Identifier> component = explicitRoot(layout, mode);
			if (alreadyProjected.filter(mode::equals).isPresent() || component.isEmpty()) {
				continue;
			}
			TreeBuilder builder = new TreeBuilder(catalog, context);
			String root = builder.project(
				component.orElseThrow(),
				"variant." + rank + "." + serialized(mode),
				Optional.empty()
			);
			diagnostics.addAll(builder.diagnostics());
			if (builder.failed()) {
				diagnostics.add("registered HUD variant tree is structurally invalid");
				return VariantCode.INVALID;
			}
			if (
				builder.requestedDegradation().isPresent()
					|| builder.criticalHidden()
					|| root == null
					|| builder.nodes().isEmpty()
			) {
				continue;
			}
			if (!mergeNodes(nodes, builder.nodeArray(), diagnostics)) {
				return VariantCode.INVALID;
			}
			roots.put(mode, root);
		}
		return VariantCode.REPLACE;
	}

	private static Optional<Identifier> explicitRoot(
		final HudLayoutDefinition layout,
		final ProjectionMode mode
	) {
		return switch (mode) {
			case NORMAL -> Optional.of(layout.root());
			case COMPACT -> layout.compactRoot();
			case SUMMARY -> layout.summaryRoot();
		};
	}

	private static boolean mergeNodes(
		final Map<String, JsonObject> destination,
		final JsonArray source,
		final List<String> diagnostics
	) {
		for (JsonElement element : source) {
			JsonObject node = element.getAsJsonObject();
			String id = node.get("id").getAsString();
			if (destination.putIfAbsent(id, node.deepCopy()) != null) {
				diagnostics.add("duplicate node in projected HUD variant forest");
				return false;
			}
			if (destination.size() > MAX_PROJECTED_NODES) {
				diagnostics.add("projected HUD variant forest exceeds " + MAX_PROJECTED_NODES + " nodes");
				return false;
			}
		}
		return true;
	}

	private static JsonObject layoutVariant(
		final LayoutVariantProjection projection,
		final Set<Identifier> included
	) {
		HudLayoutDefinition layout = projection.layout();
		JsonObject result = new JsonObject();
		result.addProperty("layout_id", layout.id().toString());
		for (ProjectionMode mode : ProjectionMode.values()) {
			String root = projection.roots().get(mode);
			if (root != null) {
				result.addProperty(serialized(mode) + "_root", root);
			}
		}
		layout.degradationLayout()
			.filter(included::contains)
			.ifPresent(value -> result.addProperty("degradation_layout", value.toString()));
		result.add("size", layoutSize(layout.size()));
		result.add("transition", layoutTransition(layout.transition()));
		return result;
	}

	private static JsonObject layoutSize(final LayoutSize size) {
		JsonObject result = new JsonObject();
		result.addProperty("minimum_width", size.minimumWidth());
		result.addProperty("preferred_width", size.preferredWidth());
		result.addProperty("maximum_width", size.maximumWidth());
		result.addProperty("maximum_height", size.maximumHeight());
		result.addProperty("growth", serialized(size.growth()));
		return result;
	}

	private static JsonObject layoutTransition(final LayoutTransition transition) {
		JsonObject result = new JsonObject();
		result.addProperty("enter", serialized(transition.enter()));
		result.addProperty("change", serialized(transition.change()));
		result.addProperty("exit", serialized(transition.exit()));
		transition.enterSound().ifPresent(value -> result.add("enter_sound", hudSound(value)));
		transition.changeSound().ifPresent(value -> result.add("change_sound", hudSound(value)));
		return result;
	}

	private static JsonObject hudSound(final HudSound sound) {
		JsonObject result = new JsonObject();
		result.addProperty("event", sound.event().toString());
		result.addProperty("volume", sound.volume());
		result.addProperty("pitch", sound.pitch());
		return result;
	}

	private static final class TreeBuilder {
		private final HudCatalog catalog;
		private final ProjectionContext context;
		private final Map<String, JsonObject> nodes = new LinkedHashMap<>();
		private final List<String> diagnostics = new ArrayList<>();
		private Identifier requestedDegradation;
		private boolean conflictingDegradation;
		private boolean criticalHidden;
		private boolean failed;

		private TreeBuilder(final HudCatalog catalog, final ProjectionContext context) {
			this.catalog = catalog;
			this.context = context;
		}

		private String project(
			final Identifier componentId,
			final String path,
			final Optional<Map<String, Value>> item
		) {
			if (this.nodes.size() >= MAX_PROJECTED_NODES) {
				this.failed = true;
				this.diagnostics.add("projected HUD node count exceeds " + MAX_PROJECTED_NODES);
				return null;
			}
			HudComponentDefinition component = this.catalog.components().get(componentId);
			if (component == null) {
				this.failed = true;
				this.diagnostics.add("compiled HUD component is unavailable: " + componentId);
				return null;
			}
			if (
				component.audience().filter(value -> !HudRouteAuthority.matches(value, this.context.audience())).isPresent()
					|| !HudRouteAuthority.matches(component.visibleWhen(), this.context.audience())
			) {
				return null;
			}

			HudBindingResolver.Result bindings = HudBindingResolver.resolve(
				component.bindings(),
				new HudBindingResolver.ResolveContext(
					this.context.audience(),
					this.context.access(),
					item
				)
			);
			this.diagnostics.addAll(bindings.diagnostics());
			if (bindings.degradationLayout().isPresent()) {
				requestDegradation(bindings.degradationLayout().orElseThrow());
				return null;
			}
			if (bindings.hideComponent()) {
				this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
				return null;
			}

			String nodeId = nodeId(componentId, path);
			JsonObject node = common(component, nodeId);
			boolean keep = body(component, bindings.values(), path, item, node);
			if (!keep || this.requestedDegradation != null || this.failed) {
				return null;
			}
			if (this.nodes.putIfAbsent(nodeId, node) != null) {
				this.failed = true;
				this.diagnostics.add("duplicate projected node id: " + nodeId);
				return null;
			}
			return nodeId;
		}

		private boolean body(
			final HudComponentDefinition component,
			final Map<String, ResolvedField> fields,
			final String path,
			final Optional<Map<String, Value>> item,
			final JsonObject node
		) {
			ComponentBody body = component.body();
			if (body instanceof TextBody text) {
				Component value = text(text.text(), fields);
				if (value.getString().isEmpty()) {
					return false;
				}
				node.add("value", json(value));
				node.addProperty("max_lines", text.layout().maxLines());
				node.addProperty("overflow", serialized(text.layout().overflow()));
				node.addProperty("alignment", alignment(text.layout().alignment()));
				return true;
			}
			if (body instanceof ImageBody image) {
				Identifier texture = image.texture();
				if (!this.context.assets().available(texture)) {
					switch (image.onAssetMissing().mode()) {
						case HIDE_COMPONENT -> {
							this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
							return false;
						}
						case DEGRADATION_LAYOUT -> {
							requestDegradation(image.onAssetMissing().degradationLayout().orElseThrow());
							return false;
						}
						case PLACEHOLDER -> texture = image.onAssetMissing().placeholderTexture().orElseThrow();
					}
					if (!this.context.assets().available(texture)) {
						this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
						return false;
					}
				}
				node.addProperty("texture", texture.toString());
				node.addProperty("width", Math.clamp(image.size().width(), 1, 256));
				node.addProperty("height", Math.clamp(image.size().height(), 1, 256));
				node.addProperty("tint", image.tint());
				node.addProperty("fit", serialized(image.fit()));
				node.add("alt", json(component(image.alt())));
				return true;
			}
			if (body instanceof PlayerHeadBody head) {
				ResolvedField field = fields.get(head.player().field());
				if (field == null || !(field.value() instanceof PlayerValue player)) {
					this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
					return false;
				}
				node.addProperty("player_uuid", player.playerId().toString());
				node.addProperty("player_name", player.playerName());
				node.addProperty("size", Math.clamp(head.size(), 8, 64));
				node.addProperty("show_hat", head.layers().contains(HeadLayer.HAT));
				node.addProperty("offline", !player.online());
				return true;
			}
			if (body instanceof CounterBody counter) {
				ResolvedField field = fields.get(counter.value().field());
				if (field == null) {
					this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
					return false;
				}
				node.add("label", json(component(counter.label())));
				node.add("value", json(valueComponent(field)));
				counter.suffix().ifPresent(value -> node.add("suffix", json(component(value))));
				return true;
			}
			if (body instanceof BadgeBody badge) {
				ResolvedField field = fields.get(badge.value().field());
				if (field == null || !(field.value() instanceof IdentifierValue identifier)) {
					return false;
				}
				BadgeVariant variant = badge.variants().get(identifier.value());
				if (variant == null) {
					return false;
				}
				node.add("label", json(component(variant.label())));
				node.addProperty("color", variant.color());
				return true;
			}
			if (body instanceof ProgressBody progress) {
				Double current = number(fields.get(progress.current().field()));
				Double maximum = number(fields.get(progress.maximum().field()));
				if (current == null || maximum == null || maximum <= 0.0D) {
					this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
					return false;
				}
				double clamped = Math.clamp(current, 0.0D, maximum);
				node.addProperty("current", clamped);
				node.addProperty("maximum", maximum);
				node.addProperty("orientation", serialized(progress.orientation()));
				node.addProperty("segments", Math.clamp(progress.segments(), 0, 64));
				node.addProperty("smooth", progress.interpolation() == ProgressInterpolation.SMOOTH);
				progressLabel(progress.label(), clamped, maximum).ifPresent(value -> node.add("label", json(value)));
				return true;
			}
			if (body instanceof TimerBody timer) {
				ResolvedField field = fields.get(timer.clock().field());
				if (field == null || !(field.value() instanceof ClockValue clock)) {
					this.criticalHidden |= component.priority() == ComponentPriority.CRITICAL;
					return false;
				}
				ClockValue directed = timer.direction() == TimerDirection.ELAPSED
					? clock
					: new ClockValue(clock.serverTick(), clock.baseTicks(), -Math.abs(clock.rate()), clock.paused());
				node.add("timer", timer(directed, timer.format(), timer.pausedMarker().map(HudProjectionCompiler::component)));
				return true;
			}
			if (body instanceof SeparatorBody separator) {
				node.addProperty("orientation", serialized(separator.orientation()));
				node.addProperty("thickness", Math.clamp(separator.thickness(), 1, 8));
				node.addProperty("color", separator.color());
				return true;
			}
			if (body instanceof BackgroundBody background) {
				String child = project(background.child(), path + ".0", item);
				if (child == null) {
					return false;
				}
				node.addProperty("child", child);
				node.addProperty("fill", background.fill());
				node.addProperty("border_color", background.border().color());
				node.addProperty("border_width", Math.clamp(background.border().width(), 0, 8));
				node.add("padding", insets(background.padding()));
				return true;
			}
			if (body instanceof ContainerBody container) {
				List<String> children = new ArrayList<>();
				List<Double> weights = new ArrayList<>();
				for (int index = 0; index < container.children().size(); index++) {
					String child = project(container.children().get(index), path + "." + index, item);
					if (child != null) {
						children.add(child);
						if (!container.weights().isEmpty()) {
							weights.add(container.weights().get(index));
						}
					}
				}
				return container(node, children, Math.clamp(container.gap(), 0, 48), container.alignment(), weights, false);
			}
			if (body instanceof OverlayBody overlay) {
				List<String> layers = new ArrayList<>();
				for (int index = 0; index < overlay.layers().size(); index++) {
					String child = project(overlay.layers().get(index), path + "." + index, item);
					if (child != null) {
						layers.add(child);
					}
				}
				return container(node, layers, 0, overlay.alignment(), List.of(), true);
			}
			if (body instanceof RepeatBody repeat) {
				ResolvedField field = fields.get(repeat.items().field());
				if (field == null || !(field.value() instanceof ListValue list) || list.itemType() != HudValueType.OBJECT) {
					return false;
				}
				List<String> children = new ArrayList<>();
				int limit = Math.min(repeat.maximumItems(), list.values().size());
				for (int index = 0; index < limit; index++) {
					Value value = list.values().get(index);
					if (!(value instanceof ObjectValue object)) {
						continue;
					}
					String child = project(
						repeat.itemComponent(),
						path + ".item" + index,
						Optional.of(object.values())
					);
					if (child != null) {
						children.add(child);
					}
				}
				if (
					list.values().size() > limit
						&& repeat.overflowMode() == RepeatOverflowMode.COMPONENT
						&& repeat.overflowComponent().isPresent()
				) {
					Map<String, Value> overflow = Map.of(
						"remaining",
						new IntegerValue(list.values().size() - limit)
					);
					String child = project(
						repeat.overflowComponent().orElseThrow(),
						path + ".overflow",
						Optional.of(overflow)
					);
					if (child != null) {
						children.add(child);
					}
				}
				return container(node, children, component.style().gap(), ContainerAlignment.START, List.of(), false);
			}
			this.failed = true;
			this.diagnostics.add("unsupported compiled HUD component body");
			return false;
		}

		private boolean container(
			final JsonObject node,
			final List<String> children,
			final int gap,
			final Enum<?> alignment,
			final List<Double> weights,
			final boolean overlay
		) {
			if (children.isEmpty()) {
				return false;
			}
			JsonArray array = new JsonArray();
			children.forEach(array::add);
			node.add(overlay ? "layers" : "children", array);
			node.addProperty("gap", Math.clamp(gap, 0, 48));
			node.addProperty("alignment", alignment(alignment));
			if (!weights.isEmpty()) {
				JsonArray values = new JsonArray();
				weights.forEach(values::add);
				node.add("weights", values);
			}
			return true;
		}

		private JsonObject common(final HudComponentDefinition component, final String nodeId) {
			JsonObject node = new JsonObject();
			node.addProperty("id", nodeId);
			node.addProperty("type", serialized(component.type()));
			node.addProperty("priority", serialized(component.priority()));
			node.add("style", style(component.style()));
			JsonObject control = new JsonObject();
			control.add("label", json(controlLabel(component.type())));
			control.addProperty("visible", true);
			control.addProperty(
				"allow_hide",
				component.clientControl().visibility() == VisibilityControl.ALLOW_HIDE
			);
			control.addProperty(
				"allow_compact",
				component.clientControl().compact() == CompactControl.ALLOW
			);
			component.clientControl().reorderGroup().ifPresent(value -> control.addProperty("reorder_group", value));
			node.add("client_control", control);
			return node;
		}

		private void requestDegradation(final Identifier layout) {
			if (this.requestedDegradation == null || this.requestedDegradation.equals(layout)) {
				this.requestedDegradation = layout;
				return;
			}
			this.conflictingDegradation = true;
			this.failed = true;
			this.diagnostics.add(
				"conflicting degradation layouts: " + this.requestedDegradation + " and " + layout
			);
		}

		private Optional<Identifier> requestedDegradation() {
			return this.conflictingDegradation
				? Optional.empty()
				: Optional.ofNullable(this.requestedDegradation);
		}

		private boolean criticalHidden() {
			return this.criticalHidden;
		}

		private boolean failed() {
			return this.failed;
		}

		private Map<String, JsonObject> nodes() {
			return Map.copyOf(this.nodes);
		}

		private JsonArray nodeArray() {
			JsonArray result = new JsonArray();
			this.nodes.values().forEach(result::add);
			return result;
		}

		private List<String> diagnostics() {
			return List.copyOf(this.diagnostics);
		}
	}

	private static JsonObject policy(final HudClientPolicy value) {
		JsonObject result = new JsonObject();
		result.addProperty("allow_hide", value.allowHide());
		Anchor defaultAnchor = value.allowedAnchors().contains(value.defaultAnchor())
			? value.defaultAnchor()
			: value.allowedAnchors().stream().findFirst().orElse(Anchor.BOTTOM_RIGHT);
		result.addProperty("default_anchor", serialized(defaultAnchor));
		JsonArray anchors = new JsonArray();
		Set<Anchor> allowed = value.allowedAnchors().isEmpty()
			? Set.of(defaultAnchor)
			: value.allowedAnchors();
		allowed.stream().sorted().map(HudProjectionCompiler::serialized).forEach(anchors::add);
		result.add("allowed_anchors", anchors);
		result.addProperty("max_offset_x", Math.clamp(value.offset().maxX(), 0, 192));
		result.addProperty("max_offset_y", Math.clamp(value.offset().maxY(), 0, 192));
		double minScale = Math.clamp(value.scale().minimum(), 0.5D, 1.5D);
		double maxScale = Math.clamp(value.scale().maximum(), minScale, 1.5D);
		double defaultScale = Math.clamp(value.scale().defaultValue(), minScale, maxScale);
		result.addProperty("min_scale", minScale);
		result.addProperty("default_scale", defaultScale);
		result.addProperty("max_scale", maxScale);
		double minOpacity = Math.clamp(value.opacity().minimum(), 0.35D, 1.0D);
		double maxOpacity = Math.clamp(value.opacity().maximum(), minOpacity, 1.0D);
		double defaultOpacity = Math.clamp(value.opacity().defaultValue(), minOpacity, maxOpacity);
		result.addProperty("min_opacity", minOpacity);
		result.addProperty("default_opacity", defaultOpacity);
		result.addProperty("max_opacity", maxOpacity);
		result.addProperty("allow_component_management", value.allowComponentManagement());
		result.addProperty("tab_collision", serialized(value.tabCollision()));
		return result;
	}

	private static JsonObject style(final HudStyle style) {
		JsonObject result = new JsonObject();
		style.textColor().ifPresent(value -> result.addProperty("text_color", value));
		style.secondaryTextColor().ifPresent(value -> result.addProperty("secondary_text_color", value));
		style.backgroundColor().ifPresent(value -> result.addProperty("background_color", value));
		style.borderColor().ifPresent(value -> result.addProperty("border_color", value));
		style.trackColor().ifPresent(value -> result.addProperty("track_color", value));
		style.fillColor().ifPresent(value -> result.addProperty("fill_color", value));
		style.markerColor().ifPresent(value -> result.addProperty("marker_color", value));
		result.addProperty("opacity", Math.clamp(style.opacity(), 0.0D, 1.0D));
		result.addProperty("font_weight", serialized(style.fontWeight()));
		result.add("padding", insets(style.padding()));
		result.addProperty("gap", Math.clamp(style.gap(), 0, 48));
		style.width().ifPresent(value -> result.addProperty("width", Math.clamp(value, 1, 640)));
		style.height().ifPresent(value -> result.addProperty("height", Math.clamp(value, 1, 640)));
		return result;
	}

	private static JsonObject insets(final Insets value) {
		JsonObject result = new JsonObject();
		result.addProperty("left", Math.clamp(value.left(), 0, 48));
		result.addProperty("top", Math.clamp(value.top(), 0, 48));
		result.addProperty("right", Math.clamp(value.right(), 0, 48));
		result.addProperty("bottom", Math.clamp(value.bottom(), 0, 48));
		return result;
	}

	private static JsonObject timer(
		final ClockValue value,
		final TimerFormat format,
		final Optional<Component> marker
	) {
		JsonObject result = new JsonObject();
		result.addProperty("server_tick", value.serverTick());
		result.addProperty("base_ticks", Math.max(0.0D, value.baseTicks()));
		result.addProperty("rate", Math.clamp(value.rate(), -64.0D, 64.0D));
		result.addProperty("paused", value.paused());
		result.addProperty("format", switch (format) {
			case M_SS -> "m:ss";
			case H_MM_SS -> "h:mm:ss";
			case LOCALIZED_DURATION -> "localized_duration";
		});
		marker.ifPresent(value1 -> result.add("paused_marker", json(value1)));
		return result;
	}

	private static Component text(
		final HudTextTemplate template,
		final Map<String, ResolvedField> fields
	) {
		JsonObject root = JsonParser.parseString(template.canonicalJson()).getAsJsonObject();
		Component result = Component.empty();
		for (JsonElement element : root.getAsJsonArray("parts")) {
			JsonObject part = element.getAsJsonObject();
			if (part.has("component")) {
				result = result.copy().append(
					ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, part.get("component")).getOrThrow()
				);
				continue;
			}
			String id = part.getAsJsonObject("field").get("id").getAsString();
			ResolvedField field = fields.get(id);
			if (field != null) {
				result = result.copy().append(valueComponent(field));
			}
		}
		return result;
	}

	private static Component valueComponent(final ResolvedField field) {
		Component value = valueComponent(field.value());
		if (field.staleIndicator().isPresent()) {
			return value.copy().append(Component.literal(" ")).append(field.staleIndicator().orElseThrow());
		}
		return value;
	}

	private static Component valueComponent(final Value value) {
		return switch (value) {
			case StringValue text -> Component.literal(text.value());
			case IntegerValue integer -> Component.literal(Long.toString(integer.value()));
			case DecimalValue decimal -> Component.literal(decimal(decimal.value()));
			case BooleanValue bool -> Component.literal(bool.value() ? "是" : "否");
			case IdentifierValue identifier -> Component.literal(identifier.value().toString());
			case PlayerValue player -> Component.literal(player.playerName());
			case ComponentValue component -> component.value().copy();
			case DurationValue duration -> Component.literal(duration(duration.ticks()));
			case ClockValue clock -> Component.literal(duration(Math.round(clock.baseTicks())));
			case ListValue ignored -> Component.empty();
			case ObjectValue ignored -> Component.empty();
		};
	}

	private static Optional<Component> progressLabel(
		final ProgressLabel label,
		final double current,
		final double maximum
	) {
		return switch (label) {
			case NONE -> Optional.empty();
			case CURRENT -> Optional.of(Component.literal(decimal(current)));
			case PERCENT -> Optional.of(Component.literal(Math.round(current / maximum * 100.0D) + "%"));
			case CURRENT_OVER_MAX -> Optional.of(Component.literal(decimal(current) + "/" + decimal(maximum)));
		};
	}

	private static Double number(final ResolvedField field) {
		if (field == null) {
			return null;
		}
		if (field.value() instanceof IntegerValue integer) {
			return (double)integer.value();
		}
		if (field.value() instanceof DecimalValue decimal) {
			return decimal.value();
		}
		return null;
	}

	private static Component controlLabel(final ComponentType type) {
		return Component.literal(switch (type) {
			case TEXT -> "文本";
			case IMAGE -> "图像";
			case PLAYER_HEAD -> "玩家头像";
			case COUNTER -> "计数";
			case BADGE -> "状态铭牌";
			case PROGRESS -> "进度";
			case TIMER -> "计时";
			case SEPARATOR -> "分隔线";
			case BACKGROUND -> "背景";
			case ROW -> "横向信息组";
			case COLUMN -> "纵向信息组";
			case OVERLAY -> "叠加信息组";
			case REPEAT -> "列表";
		});
	}

	private static Component component(final RichText value) {
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(value.json()))
			.getOrThrow();
	}

	private static JsonElement json(final Component value) {
		return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
	}

	private static Projection document(
		final JsonObject root,
		final Identifier layout,
		final List<String> diagnostics
	) {
		byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_REPLACE_BYTES) {
			List<String> values = new ArrayList<>(diagnostics);
			values.add("HUD projection exceeds " + MAX_REPLACE_BYTES + " UTF-8 bytes");
			return Projection.failClosed(values);
		}
		return Projection.replace(bytes, layout, diagnostics);
	}

	private static long contentVersion(final String sha256) {
		return new BigInteger(sha256.substring(0, 16), 16).and(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
	}

	private static String nodeId(final Identifier component, final String path) {
		String value = component + "@" + path;
		if (value.length() <= 256) {
			return value;
		}
		return component + "@" + Integer.toUnsignedString(path.hashCode(), 16);
	}

	private static String duration(final long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		long hours = seconds / 3_600L;
		long minutes = (seconds % 3_600L) / 60L;
		long remainder = seconds % 60L;
		return hours > 0L
			? "%d:%02d:%02d".formatted(hours, minutes, remainder)
			: "%d:%02d".formatted(minutes, remainder);
	}

	private static String decimal(final double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private static String serialized(final Enum<?> value) {
		return value.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static String alignment(final Enum<?> value) {
		return switch (value.name()) {
			case "LEFT", "START" -> "start";
			case "RIGHT", "END" -> "end";
			case "CENTER" -> "center";
			case "STRETCH" -> "stretch";
			default -> throw new IllegalArgumentException("unsupported alignment " + value);
		};
	}

	private static final class CountdownBindingAccess implements BindingSourceAccess {
		private final CountdownProjectionContext countdown;
		private final BindingSourceAccess delegate;

		private CountdownBindingAccess(
			final CountdownProjectionContext countdown,
			final BindingSourceAccess delegate
		) {
			this.countdown = countdown;
			this.delegate = delegate;
		}

		@Override
		public boolean authorized(final BindingRequest request) {
			return builtIn(request).isPresent() || this.delegate.authorized(request);
		}

		@Override
		public SourceValue resolve(final BindingRequest request) {
			return builtIn(request).map(SourceValue::current).orElseGet(() -> this.delegate.resolve(request));
		}

		private Optional<Value> builtIn(final BindingRequest request) {
			if (request.binding().sensitive()) {
				return Optional.empty();
			}
			String name = request.source().value().orElse("");
			if (request.source().type() == HudBindingSourceType.CLOCK && name.equals("countdown")) {
				return Optional.of(new ClockValue(
					this.countdown.serverTick(),
					this.countdown.remainingTicks(),
					this.countdown.paused() ? 0.0D : -1.0D,
					this.countdown.paused()
				));
			}
			if (!name.startsWith("countdown.")) {
				return Optional.empty();
			}
			return switch (name.substring("countdown.".length())) {
				case "name" -> typed(request.binding().valueType(), component(this.countdown.name()));
				case "total_ticks" -> typed(request.binding().valueType(), this.countdown.totalTicks());
				case "remaining_ticks" -> typed(request.binding().valueType(), this.countdown.remainingTicks());
				case "elapsed_ticks" -> typed(
					request.binding().valueType(),
					this.countdown.totalTicks() - this.countdown.remainingTicks()
				);
				case "progress" -> typed(
					request.binding().valueType(),
					(double)(this.countdown.totalTicks() - this.countdown.remainingTicks())
						/ this.countdown.totalTicks()
				);
				case "state" -> typed(request.binding().valueType(), this.countdown.state());
				case "waiting_reason" -> typed(request.binding().valueType(), this.countdown.waitingReason());
				case "missing_player_count" -> typed(
					request.binding().valueType(),
					(long)this.countdown.missingPlayerCount()
				);
				case "paused" -> typed(request.binding().valueType(), this.countdown.paused());
				default -> Optional.empty();
			};
		}

		private Optional<Value> typed(final HudValueType type, final Object value) {
			try {
				return Optional.of(switch (type) {
					case STRING -> new StringValue(value instanceof Component component ? component.getString() : String.valueOf(value));
					case INTEGER -> new IntegerValue(((Number)value).longValue());
					case DECIMAL -> new DecimalValue(((Number)value).doubleValue());
					case BOOLEAN -> new BooleanValue((Boolean)value);
					case COMPONENT -> new ComponentValue(value instanceof Component component
						? component
						: Component.literal(String.valueOf(value)));
					case DURATION -> new DurationValue(((Number)value).longValue());
					case IDENTIFIER -> new IdentifierValue(Identifier.parse(String.valueOf(value)));
					case PLAYER, CLOCK, LIST, OBJECT -> throw new IllegalArgumentException("incompatible countdown field type");
				});
			} catch (RuntimeException error) {
				return Optional.empty();
			}
		}
	}

	public enum ProjectionMode {
		NORMAL,
		COMPACT,
		SUMMARY
	}

	@FunctionalInterface
	public interface AssetAvailability {
		boolean available(Identifier asset);

		static AssetAvailability assumeAvailable() {
			return ignored -> true;
		}
	}

	public record ProjectionContext(
		HudRouteAuthority.Context audience,
		BindingSourceAccess access,
		AssetAvailability assets,
		ProjectionMode mode
	) {
		public ProjectionContext {
			audience = Objects.requireNonNull(audience, "audience");
			access = Objects.requireNonNull(access, "access");
			assets = Objects.requireNonNull(assets, "assets");
			mode = Objects.requireNonNull(mode, "mode");
		}

		public ProjectionContext(
			final HudRouteAuthority.Context audience,
			final BindingSourceAccess access
		) {
			this(audience, access, AssetAvailability.assumeAvailable(), ProjectionMode.NORMAL);
		}

		private ProjectionContext withAccess(final BindingSourceAccess replacement) {
			return new ProjectionContext(this.audience, replacement, this.assets, this.mode);
		}
	}

	public record CountdownProjectionContext(
		ProjectionContext projection,
		UUID countdownInstanceId,
		long serverTick,
		long totalTicks,
		long remainingTicks,
		boolean paused,
		String state,
		String waitingReason,
		int missingPlayerCount,
		RichText name
	) {
		public CountdownProjectionContext {
			projection = Objects.requireNonNull(projection, "projection");
			countdownInstanceId = Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			if (
				serverTick < 0L
					|| totalTicks <= 0L
					|| totalTicks > HudDefinitions.MAX_COUNTDOWN_TICKS
					|| remainingTicks < 0L
					|| remainingTicks > totalTicks
					|| missingPlayerCount < 0
			) {
				throw new IllegalArgumentException("countdown projection timing is invalid");
			}
			state = Objects.requireNonNull(state, "state");
			waitingReason = Objects.requireNonNull(waitingReason, "waitingReason");
			name = Objects.requireNonNull(name, "name");
		}
	}

	public enum ProjectionCode {
		REPLACE,
		CLEAR,
		FAIL_CLOSED
	}

	public record Projection(
		ProjectionCode code,
		Optional<byte[]> document,
		Optional<Identifier> layout,
		List<String> diagnostics
	) {
		public Projection {
			code = Objects.requireNonNull(code, "code");
			document = Objects.requireNonNull(document, "document").map(byte[]::clone);
			layout = Objects.requireNonNull(layout, "layout");
			diagnostics = List.copyOf(diagnostics);
			if ((code == ProjectionCode.REPLACE) != (document.isPresent() && layout.isPresent())) {
				throw new IllegalArgumentException("HUD projection result is inconsistent");
			}
		}

		@Override
		public Optional<byte[]> document() {
			return this.document.map(byte[]::clone);
		}

		private static Projection replace(
			final byte[] document,
			final Identifier layout,
			final List<String> diagnostics
		) {
			return new Projection(
				ProjectionCode.REPLACE,
				Optional.of(document),
				Optional.of(layout),
				diagnostics
			);
		}

		private static Projection clear() {
			return clear(List.of());
		}

		private static Projection clear(final List<String> diagnostics) {
			return new Projection(ProjectionCode.CLEAR, Optional.empty(), Optional.empty(), diagnostics);
		}

		private static Projection failClosed(final String diagnostic) {
			return failClosed(List.of(diagnostic));
		}

		private static Projection failClosed(final List<String> diagnostics) {
			return new Projection(
				ProjectionCode.FAIL_CLOSED,
				Optional.empty(),
				Optional.empty(),
				diagnostics
			);
		}
	}

	private enum TreeCode {
		REPLACE,
		CLEAR,
		FAIL_CLOSED
	}

	private record TreeProjection(
		TreeCode code,
		Optional<HudLayoutDefinition> layout,
		Optional<ProjectionMode> mode,
		Optional<String> root,
		JsonArray nodes,
		List<String> diagnostics
	) {
		private TreeProjection {
			code = Objects.requireNonNull(code, "code");
			layout = Objects.requireNonNull(layout, "layout");
			mode = Objects.requireNonNull(mode, "mode");
			root = Objects.requireNonNull(root, "root");
			nodes = Objects.requireNonNull(nodes, "nodes").deepCopy();
			diagnostics = List.copyOf(diagnostics);
		}

		private static TreeProjection replace(
			final HudLayoutDefinition layout,
			final ProjectionMode mode,
			final String root,
			final JsonArray nodes,
			final List<String> diagnostics
		) {
			return new TreeProjection(
				TreeCode.REPLACE,
				Optional.of(layout),
				Optional.of(mode),
				Optional.of(root),
				nodes,
				diagnostics
			);
		}

		private static TreeProjection clear(final List<String> diagnostics) {
			return new TreeProjection(
				TreeCode.CLEAR,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				new JsonArray(),
				diagnostics
			);
		}

		private static TreeProjection failClosed(final List<String> diagnostics) {
			return new TreeProjection(
				TreeCode.FAIL_CLOSED,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				new JsonArray(),
				diagnostics
			);
		}
	}

	private enum VariantCode {
		REPLACE,
		INVALID
	}

	private record LayoutVariantProjection(
		HudLayoutDefinition layout,
		Map<ProjectionMode, String> roots
	) {
		private LayoutVariantProjection {
			layout = Objects.requireNonNull(layout, "layout");
			roots = Map.copyOf(roots);
			if (roots.isEmpty()) {
				throw new IllegalArgumentException("layout variant has no authorized root");
			}
		}
	}

	private record VariantForest(
		boolean valid,
		JsonArray layouts,
		JsonArray nodes,
		List<String> diagnostics
	) {
		private VariantForest {
			layouts = Objects.requireNonNull(layouts, "layouts").deepCopy();
			nodes = Objects.requireNonNull(nodes, "nodes").deepCopy();
			diagnostics = List.copyOf(diagnostics);
			if (valid && (layouts.isEmpty() || nodes.isEmpty())) {
				throw new IllegalArgumentException("valid HUD variant forest cannot be empty");
			}
		}

		private static VariantForest valid(
			final JsonArray layouts,
			final JsonArray nodes,
			final List<String> diagnostics
		) {
			return new VariantForest(true, layouts, nodes, diagnostics);
		}

		private static VariantForest invalid(final List<String> diagnostics) {
			return new VariantForest(false, new JsonArray(), new JsonArray(), diagnostics);
		}
	}
}
