package io.github.zcpu954861.pixeltzzpro.client.hud;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Alignment;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BackgroundNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BadgeNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.CounterNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ContainerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ImageNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ImageFit;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Insets;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeMeta;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.NodeStyle;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Orientation;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.Overflow;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.PlayerHeadNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ProgressNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.SeparatorNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TextNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TimerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Effective;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.ComponentOrderKey;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Exclusion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Motion;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Snapshot;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Anchor;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ChangeTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.EnterTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ExitTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutVariant;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutTransition;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Mode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TabCollision;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerFormat;
import io.github.zcpu954861.pixeltzzpro.client.hud.HudLayoutEngine.Density;
import io.github.zcpu954861.pixeltzzpro.client.hud.HudLayoutEngine.Entry;
import io.github.zcpu954861.pixeltzzpro.client.hud.HudLayoutEngine.Plan;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.PlayerIdentityRenderer;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload.Surface;
import io.github.zcpu954861.pixeltzzpro.mixin.PlayerTabOverlayAccessor;
import io.github.zcpu954861.pixeltzzpro.mixin.HudAccessor;
import io.github.zcpu954861.pixeltzzpro.mixin.SubtitleOverlayAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

/**
 * The one persistent V3C information dock plus the one allowed central countdown overlay.
 *
 * <p>It never invents content or reads server state. All visible values come from the atomic,
 * per-player projection in {@link ClientHudRuntime}; local preferences only move, simplify, dim or
 * hide values that the server has already authorized.</p>
 */
public final class InformationDockOverlay {
	private static final int REFERENCE_WIDTH = 640;
	private static final int REFERENCE_HEIGHT = 360;
	private static final int SCREEN_MARGIN = 8;
	private static final int BOTTOM_GAMEPLAY_RESERVE = 78;
	private static final int MAX_DOCK_WIDTH = 248;
	private static final int MAX_DOCK_HEIGHT = 228;
	private static final int MAX_SMOOTH_PROGRESS = 1024;
	/** A state older than every supported transition settles instead of replaying missed motion. */
	private static final long MAX_ANIMATION_CATCH_UP_NANOS = 500_000_000L;
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();
	private static final SurfaceMotion<ClientHudSnapshot> DOCK_MOTION = new SurfaceMotion<>(
		ClientHudSnapshot::version,
		ClientHudSnapshot::structureKey,
		value -> HudVisualFingerprint.of(value.nodes()),
		value -> value.layoutVariants().getFirst().transition(),
		ClientHudSnapshot::receivedAtNanos
	);
	private static final SurfaceMotion<ClientCountdownSnapshot> COUNTDOWN_MOTION = new SurfaceMotion<>(
		ClientCountdownSnapshot::version,
		ClientCountdownSnapshot::structureKey,
		value -> HudVisualFingerprint.of(value.nodes()),
		value -> value.layoutVariants().getFirst().transition(),
		ClientCountdownSnapshot::receivedAtNanos
	);
	private static final Object SMOOTH_LOCK = new Object();
	private static final Map<String, String> ACTIVE_SMOOTH_SCOPES = new HashMap<>();
	private static final LinkedHashMap<ProgressKey, ProgressState> SMOOTH_PROGRESS = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<ProgressKey, ProgressState> eldest) {
			return this.size() > MAX_SMOOTH_PROGRESS;
		}
	};

	private InformationDockOverlay() {
	}

	public static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		try {
			HudElementRegistry.attachElementAfter(
				VanillaHudElements.TITLE_AND_SUBTITLE,
				PixelTzzPro.id("information_dock"),
				InformationDockOverlay::render
			);
		} catch (RuntimeException exception) {
			REGISTERED.set(false);
			throw exception;
		}
	}

	/** Clears interpolation-only client state at connection boundaries. */
	public static void clearTransientState() {
		synchronized (SMOOTH_LOCK) {
			ACTIVE_SMOOTH_SCOPES.clear();
			SMOOTH_PROGRESS.clear();
		}
		DOCK_MOTION.clear();
		COUNTDOWN_MOTION.clear();
	}

	private static void render(final GuiGraphicsExtractor graphics, final DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		long nowNanos = System.nanoTime();
		boolean surfaceVisible = visibleDuring(client);
		Motion localMotion = ClientHudPreferences.snapshot().motion();
		Optional<ClientHudSnapshot> dock = ClientHudRuntime.snapshot();
		Optional<ClientCountdownSnapshot> countdown = ClientHudRuntime.countdownSnapshot();
		MotionSample<ClientHudSnapshot> dockMotion = DOCK_MOTION.sample(
			dock,
			localMotion,
			nowNanos,
			surfaceVisible,
			ClientHudRuntime.dockVersion()
				.map(version -> ClientHudRuntime.consumePresentationSettle(Surface.HUD, version))
				.orElse(false)
		);
		MotionSample<ClientCountdownSnapshot> countdownMotion = COUNTDOWN_MOTION.sample(
			countdown,
			localMotion,
			nowNanos,
			surfaceVisible,
			ClientHudRuntime.countdownVersion()
				.map(version -> ClientHudRuntime.consumePresentationSettle(Surface.COUNTDOWN, version))
				.orElse(false)
		);
		if (dock.isEmpty() && !dockMotion.visible()) {
			clearSmoothChannel("dock");
		}
		if (countdown.isEmpty() && !countdownMotion.visible()) {
			clearSmoothChannel("countdown");
		}
		if (!surfaceVisible) {
			return;
		}
		boolean opened = false;
		if (dockMotion.visible()) {
			graphics.nextStratum();
			opened = renderDockMotion(graphics, dockMotion);
		}
		if (countdownMotion.visible()) {
			if (!opened) {
				graphics.nextStratum();
			}
			renderCountdownMotion(graphics, countdownMotion, ClientHudPreferences.snapshot());
		}
	}

	private static boolean visibleDuring(final Minecraft client) {
		return client.player != null
			&& client.level != null
			&& !client.gui.hud.isHidden()
			&& (client.gui.screen() == null || client.gui.screen() instanceof ChatScreen);
	}

	private static boolean renderDockMotion(
		final GuiGraphicsExtractor graphics,
		final MotionSample<ClientHudSnapshot> motion
	) {
		boolean rendered = false;
		if (motion.previous().isPresent() && motion.previousOpacity() > 0.001F) {
			ClientHudSnapshot previous = motion.previous().orElseThrow();
			Effective preferences = ClientHudPreferences.effective(previous.policy());
			if (preferences.visible()) {
				rendered |= renderDock(
					graphics,
					previous,
					preferences,
					motion.previousOpacity(),
					0.0F,
					1.0F
				);
			}
		}
		if (motion.current().isPresent() && motion.currentOpacity() > 0.001F) {
			ClientHudSnapshot current = motion.current().orElseThrow();
			Effective preferences = ClientHudPreferences.effective(current.policy());
			if (preferences.visible()) {
				rendered |= renderDock(
					graphics,
					current,
					preferences,
					motion.currentOpacity(),
					motion.translateY(),
					motion.focusScale()
				);
			}
		}
		return rendered;
	}

	private static boolean renderDock(
		final GuiGraphicsExtractor graphics,
		final ClientHudSnapshot snapshot,
		final Effective preferences,
		final float transitionOpacity,
		final float transitionY,
		final float transitionScale
	) {
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		double referenceScale = referenceScale(screenWidth, screenHeight);
		double scale = referenceScale * preferences.scale();
		int screenMargin = scaled(SCREEN_MARGIN, referenceScale);
		int bottomReserve = scaled(BOTTOM_GAMEPLAY_RESERVE, referenceScale);
		int logicalMaximumWidth = Math.max(
			1,
			Math.min(MAX_DOCK_WIDTH, (int)Math.floor((screenWidth - screenMargin * 2) / scale))
		);
		int logicalMaximumHeight = Math.max(
			1,
			Math.min(MAX_DOCK_HEIGHT, (int)Math.floor((screenHeight - bottomReserve - screenMargin) / scale))
		);
		Optional<Rect> tabBounds = tabBounds(Minecraft.getInstance(), screenWidth);
		Optional<Rect> subtitleBounds = subtitleBounds(
			Minecraft.getInstance(),
			screenWidth,
			screenHeight,
			referenceScale
		);
		for (PlanCandidate candidate : planCandidates(snapshot, logicalMaximumWidth, logicalMaximumHeight, preferences)) {
			Plan plan = candidate.plan();
			int renderedWidth = Math.max(1, (int)Math.ceil(plan.width() * scale));
			int renderedHeight = Math.max(1, (int)Math.ceil(plan.height() * scale));
			List<Rect> policyBlocked = new ArrayList<>();
			subtitleBounds.ifPresent(policyBlocked::add);
			if (snapshot.policy().tabCollision() == TabCollision.KEEP) {
				tabBounds.ifPresent(policyBlocked::add);
			}
			Placement placement = solvePlacement(
				preferences.anchor(),
				scaled(preferences.offsetX(), referenceScale),
				scaled(preferences.offsetY(), referenceScale),
				renderedWidth,
				renderedHeight,
				screenWidth,
				screenHeight,
				screenMargin,
				bottomReserve,
				preferences.exclusions(),
				Minecraft.getInstance().gui.screen() instanceof ChatScreen,
				policyBlocked
			).orElse(null);
			if (placement == null) {
				continue;
			}

			Rect dockBounds = new Rect(placement.x(), placement.y(), renderedWidth, renderedHeight);
			boolean collidesWithTab = tabBounds.map(dockBounds::intersects).orElse(false);
			if (collidesWithTab && snapshot.policy().tabCollision() == TabCollision.HIDE) {
				return false;
			}
			float transientOpacity = collidesWithTab && snapshot.policy().tabCollision() == TabCollision.DIM
				? 0.28F
				: 1.0F;
			float opacity = (float)Math.clamp(
				preferences.opacity() * transientOpacity * transitionOpacity,
				0.0D,
				1.0D
			);
			graphics.enableScissor(0, 0, screenWidth, screenHeight);
			graphics.pose().pushMatrix();
			double animatedScale = scale * transitionScale;
			double animatedWidth = plan.width() * animatedScale;
			double animatedHeight = plan.height() * animatedScale;
			graphics.pose().translate(
				(float)(placement.x() + (renderedWidth - animatedWidth) / 2.0D),
				(float)(placement.y() + (renderedHeight - animatedHeight) / 2.0D + transitionY * scale)
			);
			graphics.pose().scale((float)animatedScale, (float)animatedScale);
			try {
				renderEntry(
					graphics,
					Minecraft.getInstance().font,
					plan.root(),
					renderContext("dock", snapshot.version(), candidate.layoutId(), snapshot.receivedAtNanos(), true),
					opacity,
					preferences.highContrast(),
					preferences.motion()
				);
			} finally {
				graphics.pose().popMatrix();
				graphics.disableScissor();
			}
			return true;
		}
		return false;
	}

	private static Optional<PlanCandidate> choosePlan(
		final ClientHudSnapshot snapshot,
		final int maximumWidth,
		final int maximumHeight,
		final Effective preferences
	) {
		return planCandidates(snapshot, maximumWidth, maximumHeight, preferences).stream().findFirst();
	}

	private static List<PlanCandidate> planCandidates(
		final ClientHudSnapshot snapshot,
		final int maximumWidth,
		final int maximumHeight,
		final Effective preferences
	) {
		return planCandidates(
			snapshot.mode(),
			personalizedNodes(
				snapshot.nodes(),
				preferences.compactComponents(),
				preferences.componentOrder()
			),
			snapshot.layoutVariants(),
			maximumWidth,
			maximumHeight,
			preferences.hiddenComponents()
		);
	}

	private static List<PlanCandidate> planCandidates(
		final Mode startingMode,
		final Map<String, ClientHudNode> nodes,
		final List<LayoutVariant> variants,
		final int maximumWidth,
		final int maximumHeight,
		final Set<String> hiddenComponents
	) {
		List<Mode> modes = switch (startingMode) {
			case NORMAL -> List.of(Mode.NORMAL, Mode.COMPACT, Mode.SUMMARY);
			case COMPACT -> List.of(Mode.COMPACT, Mode.SUMMARY);
			case SUMMARY -> List.of(Mode.SUMMARY);
		};
		Font font = Minecraft.getInstance().font;
		List<PlanCandidate> result = new ArrayList<>();
		Set<String> measuredRoots = new LinkedHashSet<>();
		for (LayoutVariant variant : variants) {
			int widthLimit = Math.min(maximumWidth, variant.size().maximumWidth());
			int heightLimit = Math.min(maximumHeight, variant.size().maximumHeight());
			if (widthLimit < variant.size().minimumWidth() || heightLimit <= 0) {
				continue;
			}
			for (Mode mode : modes) {
				Optional<String> root = variant.root(mode);
				if (root.isEmpty() || !measuredRoots.add(variant.layoutId() + "\u0000" + root.orElseThrow())) {
					continue;
				}
				Density density = Density.valueOf(mode.name());
				int preferredWidth = Math.clamp(
					variant.size().preferredWidth(),
					variant.size().minimumWidth(),
					widthLimit
				);
				Optional<Plan> measured = HudLayoutEngine.measure(
					root.orElseThrow(),
					nodes,
					font,
					preferredWidth,
					heightLimit,
					hiddenComponents,
					density
				);
				if (measured.isEmpty() && preferredWidth < widthLimit) {
					measured = HudLayoutEngine.measure(
						root.orElseThrow(),
						nodes,
						font,
						widthLimit,
						heightLimit,
						hiddenComponents,
						density
					);
				}
				measured.ifPresent(plan -> result.add(new PlanCandidate(plan, variant.layoutId())));
			}
		}
		return List.copyOf(result);
	}

	private static Map<String, ClientHudNode> personalizedNodes(
		final Map<String, ClientHudNode> source,
		final Set<String> compactComponents,
		final Map<ComponentOrderKey, List<String>> componentOrder
	) {
		if (compactComponents.isEmpty() && componentOrder.isEmpty()) {
			return source;
		}
		LinkedHashMap<String, ClientHudNode> result = new LinkedHashMap<>(source);
		for (String id : compactComponents) {
			ClientHudNode node = result.get(id);
			if (node != null) {
				result.put(id, compactNode(node));
			}
		}
		for (Map.Entry<ComponentOrderKey, List<String>> request : componentOrder.entrySet()) {
			ClientHudNode candidate = result.get(request.getKey().parentId());
			if (!(candidate instanceof ContainerNode parent)) {
				continue;
			}
			LinkedHashSet<String> direct = new LinkedHashSet<>(parent.children());
			List<String> ordered = request.getValue().stream().filter(direct::contains).distinct().toList();
			if (ordered.size() < 2) {
				continue;
			}
			Set<String> movable = Set.copyOf(ordered);
			List<String> children = new ArrayList<>(parent.children().size());
			int orderedIndex = 0;
			for (String child : parent.children()) {
				if (movable.contains(child) && orderedIndex < ordered.size()) {
					children.add(ordered.get(orderedIndex++));
				} else {
					children.add(child);
				}
			}
			children = List.copyOf(children);
			List<Double> weights = parent.weights();
			if (!weights.isEmpty()) {
				Map<String, Double> byChild = new LinkedHashMap<>();
				for (int index = 0; index < parent.children().size(); index++) {
					byChild.put(parent.children().get(index), weights.get(index));
				}
				weights = children.stream().map(byChild::get).toList();
			}
			result.put(
				parent.id(),
				new ContainerNode(parent.meta(), children, parent.gap(), parent.alignment(), weights)
			);
		}
		return Map.copyOf(result);
	}

	private static ClientHudNode compactNode(final ClientHudNode node) {
		NodeMeta meta = compactMeta(node.meta());
		if (node instanceof TextNode value) {
			return new TextNode(meta, value.value(), 1, Overflow.ELLIPSIS, value.alignment());
		}
		if (node instanceof ImageNode value) {
			return new ImageNode(meta, value.texture(), value.width(), value.height(), value.tint(), value.alt(), value.fit());
		}
		if (node instanceof PlayerHeadNode value) {
			return new PlayerHeadNode(meta, value.playerId(), value.playerName(), value.size(), value.showHat(), value.offline());
		}
		if (node instanceof CounterNode value) {
			return new CounterNode(meta, value.label(), value.value(), value.suffix());
		}
		if (node instanceof BadgeNode value) {
			return new BadgeNode(meta, value.label(), value.color());
		}
		if (node instanceof ProgressNode value) {
			return new ProgressNode(
				meta,
				value.current(),
				value.maximum(),
				value.orientation(),
				value.label(),
				value.segments(),
				value.smooth(),
				value.spine()
			);
		}
		if (node instanceof TimerNode value) {
			return new TimerNode(meta, value.timer(), value.label());
		}
		if (node instanceof SeparatorNode value) {
			return new SeparatorNode(meta, value.orientation(), value.thickness(), value.color());
		}
		if (node instanceof BackgroundNode value) {
			return new BackgroundNode(
				meta,
				value.child(),
				value.fill(),
				value.borderColor(),
				value.borderWidth(),
				compactInsets(value.innerPadding())
			);
		}
		if (node instanceof ContainerNode value) {
			return new ContainerNode(meta, value.children(), compact(value.gap()), value.alignment(), value.weights());
		}
		return node;
	}

	private static NodeMeta compactMeta(final NodeMeta meta) {
		NodeStyle style = meta.style();
		return new NodeMeta(
			meta.id(),
			meta.type(),
			meta.priority(),
			new NodeStyle(
				style.textColor(),
				style.secondaryTextColor(),
				style.backgroundColor(),
				style.borderColor(),
				style.trackColor(),
				style.fillColor(),
				style.markerColor(),
				style.opacity(),
				style.bold(),
				compactInsets(style.padding()),
				compact(style.gap()),
				style.width(),
				style.height()
			),
			meta.clientControl()
		);
	}

	private static Insets compactInsets(final Insets value) {
		return new Insets(
			compact(value.left()),
			compact(value.top()),
			compact(value.right()),
			compact(value.bottom())
		);
	}

	private static int compact(final int value) {
		return (value + 1) / 2;
	}

	private static Optional<Placement> solvePlacement(
		final Anchor anchor,
		final int offsetX,
		final int offsetY,
		final int width,
		final int height,
		final int screenWidth,
		final int screenHeight,
		final int screenMargin,
		final int bottomGameplayReserve,
		final List<Exclusion> exclusions,
		final boolean chatOpen,
		final List<Rect> additionalBlocked
	) {
		int left = anchor == Anchor.BOTTOM_LEFT || anchor == Anchor.TOP_LEFT
			? screenMargin + offsetX
			: screenWidth - screenMargin - width - offsetX;
		int top = anchor == Anchor.TOP_LEFT || anchor == Anchor.TOP_RIGHT
			? screenMargin + offsetY
			: screenHeight - bottomGameplayReserve - height - offsetY;
		left = Math.clamp(left, screenMargin, Math.max(screenMargin, screenWidth - screenMargin - width));
		top = Math.clamp(top, screenMargin, Math.max(screenMargin, screenHeight - screenMargin - height));

		List<Rect> blocked = new ArrayList<>();
		blocked.addAll(additionalBlocked);
		int hotbarLeft = Math.max(0, screenWidth / 2 - 96);
		blocked.add(new Rect(hotbarLeft, Math.max(0, screenHeight - 48), 192, 48));
		blocked.add(new Rect(Math.max(0, screenWidth / 2 - 160), Math.max(0, screenHeight - 82), 320, 18));
		if (anchor == Anchor.TOP_LEFT || anchor == Anchor.TOP_RIGHT) {
			blocked.add(new Rect(Math.max(0, screenWidth / 2 - 180), 0, Math.min(360, screenWidth), 54));
		}
		if (chatOpen && (anchor == Anchor.BOTTOM_LEFT || anchor == Anchor.TOP_LEFT)) {
			blocked.add(new Rect(0, Math.max(0, screenHeight - 178), Math.min(326, screenWidth), 178));
		}
		for (Exclusion value : exclusions) {
			if (!value.enabled()) {
				continue;
			}
			blocked.add(new Rect(
				(int)Math.floor(value.x() * screenWidth),
				(int)Math.floor(value.y() * screenHeight),
				Math.max(1, (int)Math.ceil(value.width() * screenWidth)),
				Math.max(1, (int)Math.ceil(value.height() * screenHeight))
			));
		}

		boolean bottom = anchor == Anchor.BOTTOM_LEFT || anchor == Anchor.BOTTOM_RIGHT;
		for (int step = 0; step <= screenHeight; step += 4) {
			int candidateY = top + (bottom ? -step : step);
			if (candidateY < screenMargin || candidateY + height > screenHeight - screenMargin) {
				continue;
			}
			Rect candidate = new Rect(left, candidateY, width, height);
			if (blocked.stream().noneMatch(candidate::intersects)) {
				return Optional.of(new Placement(left, candidateY));
			}
		}
		return Optional.empty();
	}

	private static double referenceScale(final int screenWidth, final int screenHeight) {
		return Math.max(
			0.01D,
			Math.min(1.0D, Math.min(screenWidth / (double)REFERENCE_WIDTH, screenHeight / (double)REFERENCE_HEIGHT))
		);
	}

	private static int scaled(final int value, final double scale) {
		return (int)Math.round(value * scale);
	}

	/** Mirrors vanilla's complete player-list rectangle for collision-policy decisions. */
	private static Optional<Rect> tabBounds(final Minecraft client, final int screenWidth) {
		if (!client.options.keyPlayerList.isDown() || client.getConnection() == null || client.level == null) {
			return Optional.empty();
		}
		List<PlayerInfo> players = client.getConnection().getListedOnlinePlayers().stream().limit(80L).toList();
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
		if (client.isLocalServer() && players.size() <= 1 && objective == null) {
			return Optional.empty();
		}
		if (players.isEmpty()) {
			return Optional.empty();
		}

		int slots = players.size();
		int rows = slots;
		int columns;
		for (columns = 1; rows > 20; rows = (slots + columns - 1) / columns) {
			columns++;
		}
		int maximumNameWidth = 0;
		int maximumScoreWidth = 0;
		int spacerWidth = client.font.width(" ");
		for (PlayerInfo player : players) {
			maximumNameWidth = Math.max(maximumNameWidth, client.font.width(client.gui.hud.getTabList().getNameForDisplay(player)));
			if (objective != null && objective.getRenderType() != RenderType.HEARTS) {
				ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(player.getProfile()), objective);
				if (score != null) {
					NumberFormat format = objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT);
					int scoreWidth = client.font.width(score.formatValue(format));
					maximumScoreWidth = Math.max(maximumScoreWidth, scoreWidth > 0 ? spacerWidth + scoreWidth : 0);
				}
			}
		}
		boolean showHead = client.getConnection().onlineMode();
		int scoreWidth = objective == null ? 0 : objective.getRenderType() == RenderType.HEARTS ? 90 : maximumScoreWidth;
		int slotWidth = Math.min(
			columns * ((showHead ? 9 : 0) + maximumNameWidth + scoreWidth + 13),
			Math.max(1, screenWidth - 50)
		) / columns;
		int totalWidth = slotWidth * columns + (columns - 1) * 5;
		int maximumLineWidth = totalWidth;
		int y = 10;
		PlayerTabOverlayAccessor tab = (PlayerTabOverlayAccessor)client.gui.hud.getTabList();
		Component header = tab.pixelTzzPro$getHeader();
		if (header != null) {
			List<FormattedCharSequence> lines = client.font.split(header, Math.max(1, screenWidth - 50));
			maximumLineWidth = Math.max(maximumLineWidth, lines.stream().mapToInt(client.font::width).max().orElse(0));
			y += lines.size() * 9 + 1;
		}
		int bottom = y + rows * 9;
		Component footer = tab.pixelTzzPro$getFooter();
		if (footer != null) {
			List<FormattedCharSequence> lines = client.font.split(footer, Math.max(1, screenWidth - 50));
			maximumLineWidth = Math.max(maximumLineWidth, lines.stream().mapToInt(client.font::width).max().orElse(0));
			bottom += 1 + lines.size() * 9;
		}
		return Optional.of(new Rect(
			screenWidth / 2 - maximumLineWidth / 2 - 1,
			9,
			maximumLineWidth + 2,
			Math.max(1, bottom - 9)
		));
	}

	/** Conservatively mirrors vanilla's bottom-right subtitle stack without reading subtitle text. */
	private static Optional<Rect> subtitleBounds(
		final Minecraft client,
		final int screenWidth,
		final int screenHeight,
		final double referenceScale
	) {
		if (!client.options.showSubtitles().get()) {
			return Optional.empty();
		}
		int active = ((SubtitleOverlayAccessor)(
			(HudAccessor)client.gui.hud
		).pixelTzzPro$getSubtitleOverlay()).pixelTzzPro$getSubtitles().size();
		if (active <= 0) {
			return Optional.empty();
		}
		int lines = Math.clamp(active, 1, 12);
		int width = Math.min(screenWidth, scaled(204, referenceScale));
		int height = Math.min(screenHeight, scaled(lines * 10 + 6, referenceScale));
		int bottom = Math.max(height, screenHeight - scaled(30, referenceScale));
		return Optional.of(new Rect(
			Math.max(0, screenWidth - width - scaled(1, referenceScale)),
			Math.max(0, bottom - height),
			width,
			height
		));
	}

	private static void renderEntry(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Entry entry,
		final RenderContext context,
		final float opacity,
		final boolean highContrast,
		final Motion motion
	) {
		graphics.enableScissor(entry.x(), entry.y(), entry.x() + entry.width(), entry.y() + entry.height());
		try {
			ClientHudNode node = entry.node();
			NodeStyle style = node.meta().style();
			float nodeOpacity = (float)Math.clamp(opacity * style.opacity(), 0.0D, 1.0D);
			int background = highContrast && (style.backgroundColor() >>> 24) != 0
				? 0xF5080D12
				: style.backgroundColor();
			int border = highContrast && (style.borderColor() >>> 24) != 0
				? 0xFFFFFFFF
				: style.borderColor();
			if ((background >>> 24) != 0) {
				graphics.fill(entry.x(), entry.y(), entry.x() + entry.width(), entry.y() + entry.height(), alpha(background, nodeOpacity));
			}
			if ((border >>> 24) != 0) {
				graphics.outline(entry.x(), entry.y(), entry.width(), entry.height(), alpha(border, nodeOpacity));
			}

		if (node instanceof TextNode value) {
			renderText(graphics, font, entry, value, style, nodeOpacity, highContrast);
		} else if (node instanceof ImageNode value) {
			renderImage(graphics, entry, value, nodeOpacity);
		} else if (node instanceof PlayerHeadNode value) {
			PlayerIdentityRenderer.drawHead(
				graphics,
				value.playerId(),
				!value.offline(),
				entry.contentX(),
				entry.contentY(),
				Math.min(entry.contentWidth(), entry.contentHeight()),
				false,
				value.showHat()
			);
			if (nodeOpacity < 0.99F) {
				graphics.fill(
					entry.contentX(),
					entry.contentY(),
					entry.contentX() + entry.contentWidth(),
					entry.contentY() + entry.contentHeight(),
					Math.clamp(Math.round((1.0F - nodeOpacity) * 210.0F), 0, 210) << 24
				);
			}
		} else if (node instanceof CounterNode value) {
			renderCounter(graphics, font, entry, value, style, nodeOpacity, highContrast);
		} else if (node instanceof BadgeNode value) {
			renderBadge(graphics, font, entry, value, style, nodeOpacity, highContrast);
		} else if (node instanceof ProgressNode value) {
			renderProgress(graphics, font, entry, value, style, context, nodeOpacity, highContrast, motion);
		} else if (node instanceof TimerNode value) {
			renderTimer(graphics, font, entry, value, style, context, nodeOpacity, highContrast);
		} else if (node instanceof SeparatorNode value) {
			graphics.fill(
				entry.contentX(),
				entry.contentY(),
				entry.contentX() + entry.contentWidth(),
				entry.contentY() + entry.contentHeight(),
				alpha(value.color(), nodeOpacity)
			);
		} else if (node instanceof BackgroundNode value) {
			graphics.fill(entry.x(), entry.y(), entry.x() + entry.width(), entry.y() + entry.height(), alpha(value.fill(), nodeOpacity));
			for (int index = 0; index < value.borderWidth(); index++) {
				graphics.outline(
					entry.x() + index,
					entry.y() + index,
					Math.max(1, entry.width() - index * 2),
					Math.max(1, entry.height() - index * 2),
					alpha(value.borderColor(), nodeOpacity)
				);
			}
		}

			for (Entry child : entry.children()) {
				renderEntry(graphics, font, child, context, nodeOpacity, highContrast, motion);
			}
		} finally {
			graphics.disableScissor();
		}
	}

	private static void renderImage(
		final GuiGraphicsExtractor graphics,
		final Entry entry,
		final ImageNode node,
		final float opacity
	) {
		int areaWidth = entry.contentWidth();
		int areaHeight = entry.contentHeight();
		if (areaWidth <= 0 || areaHeight <= 0) {
			return;
		}
		int width = areaWidth;
		int height = areaHeight;
		if (node.fit() != ImageFit.STRETCH) {
			double scale = node.fit() == ImageFit.CONTAIN
				? Math.min(areaWidth / (double)node.width(), areaHeight / (double)node.height())
				: Math.max(areaWidth / (double)node.width(), areaHeight / (double)node.height());
			width = Math.max(1, (int)Math.round(node.width() * scale));
			height = Math.max(1, (int)Math.round(node.height() * scale));
		}
		int x = entry.contentX() + (areaWidth - width) / 2;
		int y = entry.contentY() + (areaHeight - height) / 2;
		graphics.enableScissor(
			entry.contentX(),
			entry.contentY(),
			entry.contentX() + areaWidth,
			entry.contentY() + areaHeight
		);
		try {
			graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				node.texture(),
				x,
				y,
				0.0F,
				0.0F,
				width,
				height,
				node.width(),
				node.height(),
				alpha(node.tint(), opacity)
			);
		} finally {
			graphics.disableScissor();
		}
	}

	private static void renderText(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Entry entry,
		final TextNode node,
		final NodeStyle style,
		final float opacity,
		final boolean highContrast
	) {
		int color = alpha(highContrast ? 0xFFFFFFFF : style.textColor(), opacity);
		if (entry.contentScale() < 0.999D) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(entry.contentX(), entry.contentY());
			graphics.pose().scale((float)entry.contentScale(), (float)entry.contentScale());
			try {
				graphics.text(font, entry.lines().getFirst(), 0, 0, color, style.bold());
			} finally {
				graphics.pose().popMatrix();
			}
			return;
		}
		for (int index = 0; index < entry.lines().size(); index++) {
			var line = entry.lines().get(index);
			int lineWidth = font.width(line);
			int x = switch (node.alignment()) {
				case START, STRETCH -> entry.contentX();
				case CENTER -> entry.contentX() + Math.max(0, entry.contentWidth() - lineWidth) / 2;
				case END -> entry.contentX() + Math.max(0, entry.contentWidth() - lineWidth);
			};
			graphics.text(font, line, x, entry.contentY() + index * font.lineHeight, color, style.bold());
		}
	}

	private static void renderCounter(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Entry entry,
		final CounterNode node,
		final NodeStyle style,
		final float opacity,
		final boolean highContrast
	) {
		String value = node.value().getString() + node.suffix().map(Component::getString).orElse("");
		renderInlinePair(
			graphics,
			font,
			entry.contentX(),
			entry.contentY(),
			entry.contentWidth(),
			Optional.of(node.label()),
			Component.literal(value),
			alpha(highContrast ? 0xFFFFFFFF : style.secondaryTextColor(), opacity),
			alpha(highContrast ? INFO_CYAN : style.textColor(), opacity)
		);
	}

	private static void renderBadge(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Entry entry,
		final BadgeNode node,
		final NodeStyle style,
		final float opacity,
		final boolean highContrast
	) {
		int fill = alpha(highContrast ? 0xFF16232B : node.color(), opacity * 0.32F);
		int border = alpha(highContrast ? 0xFFFFFFFF : node.color(), opacity);
		graphics.fill(entry.contentX(), entry.contentY(), entry.contentX() + entry.contentWidth(), entry.contentY() + entry.contentHeight(), fill);
		graphics.outline(entry.contentX(), entry.contentY(), entry.contentWidth(), entry.contentHeight(), border);
		int textWidth = Math.max(0, entry.contentWidth() - 6);
		FormattedCharSequence label = ellipsized(font, node.label(), textWidth);
		int x = entry.contentX() + Math.max(3, (entry.contentWidth() - font.width(label)) / 2);
		graphics.text(font, label, x, entry.contentY() + 3, alpha(highContrast ? 0xFFFFFFFF : style.textColor(), opacity), false);
	}

	private static void renderProgress(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Entry entry,
		final ProgressNode node,
		final NodeStyle style,
		final RenderContext context,
		final float opacity,
		final boolean highContrast,
		final Motion motion
	) {
		int x = entry.contentX();
		int y = entry.contentY();
		int width = entry.contentWidth();
		int height = entry.contentHeight();
		if (node.label().isPresent()) {
			graphics.text(
				font,
				ellipsized(font, node.label().orElseThrow(), width),
				x,
				y,
				alpha(highContrast ? 0xFFFFFFFF : style.secondaryTextColor(), opacity),
				false
			);
			y += font.lineHeight + 2;
			height -= font.lineHeight + 2;
		}
		double target = node.fraction();
		double fraction = smoothProgress(context, node.id(), target, node.smooth(), motion);
		int track = alpha(highContrast ? 0xFF70808C : style.trackColor(), opacity);
		int fill = alpha(highContrast ? INFO_CYAN : style.fillColor(), opacity);
		int marker = alpha(highContrast ? BRAND_GOLD : style.markerColor(), opacity);
		if (node.orientation() == Orientation.VERTICAL) {
			graphics.fill(x + width / 2 - 2, y, x + width / 2 + 2, y + height, track);
			int fillHeight = (int)Math.round(height * fraction);
			graphics.fill(x + width / 2 - 2, y + height - fillHeight, x + width / 2 + 2, y + height, fill);
			graphics.fill(x + width / 2 - 4, y + height - fillHeight - 2, x + width / 2 + 4, y + height - fillHeight + 2, marker);
			return;
		}
		int centerY = y + Math.max(1, height / 2);
		int thickness = node.spine() ? 2 : Math.max(3, Math.min(6, height));
		graphics.fill(x, centerY - thickness / 2, x + width, centerY - thickness / 2 + thickness, track);
		int filled = Math.clamp((int)Math.round(width * fraction), 0, width);
		graphics.fill(x, centerY - thickness / 2, x + filled, centerY - thickness / 2 + thickness, fill);
		if (node.segments() > 1) {
			for (int index = 0; index < node.segments(); index++) {
				int tickX = x + (int)Math.round(index * width / (double)(node.segments() - 1));
				graphics.fill(tickX, centerY - 3, tickX + 1, centerY + 4, alpha(SURFACE_BORDER, opacity));
			}
		}
		int markerX = Math.clamp(x + filled, x + 2, x + width - 2);
		graphics.fill(markerX - 2, centerY - 3, markerX + 3, centerY + 4, marker);
		if (node.spine()) {
			graphics.fill(x, centerY - 4, x + 1, centerY + 5, marker);
			graphics.fill(x + width - 1, centerY - 4, x + width, centerY + 5, marker);
		}
	}

	private static void renderTimer(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Entry entry,
		final TimerNode node,
		final NodeStyle style,
		final RenderContext context,
		final float opacity,
		final boolean highContrast
	) {
		long nowNanos = System.nanoTime();
		var estimatedServerTick = context.serverClock()
			? ClientHudRuntime.estimatedServerTick(nowNanos)
			: java.util.OptionalDouble.empty();
		long ticks = estimatedServerTick.isPresent()
			? node.timer().ticksAt(estimatedServerTick.orElseThrow())
			: node.timer().ticksAt(context.receivedAtNanos(), nowNanos);
		String value = formatTicks(ticks, node.timer().format());
		if (node.timer().paused()) {
			value += node.timer().pausedMarker().map(marker -> "  " + marker.getString()).orElse("  ·");
		}
		renderInlinePair(
			graphics,
			font,
			entry.contentX(),
			entry.contentY() + 2,
			entry.contentWidth(),
			node.label(),
			Component.literal(value),
			alpha(highContrast ? 0xFFFFFFFF : style.secondaryTextColor(), opacity),
			alpha(highContrast ? BRAND_GOLD : style.textColor(), opacity)
		);
	}

	private static void renderInlinePair(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final int x,
		final int y,
		final int width,
		final Optional<Component> left,
		final Component right,
		final int leftColor,
		final int rightColor
	) {
		if (width <= 0) {
			return;
		}
		boolean hasLeft = left.isPresent() && !left.orElseThrow().getString().isEmpty();
		boolean hasRight = !right.getString().isEmpty();
		int gap = hasLeft && hasRight ? 4 : 0;
		int fragmentWidth = Math.max(1, font.width("…"));
		int leftDesired = hasLeft ? font.width(left.orElseThrow()) : 0;
		int rightDesired = hasRight ? font.width(right) : 0;
		int leftMinimum = hasLeft ? Math.min(leftDesired, fragmentWidth) : 0;
		int rightMinimum = hasRight ? Math.min(rightDesired, fragmentWidth) : 0;
		if (leftMinimum + rightMinimum + gap > width) {
			gap = 0;
		}
		int rightWidth = hasRight
			? Math.min(width, Math.min(rightDesired, Math.max(rightMinimum, width - gap - leftMinimum)))
			: 0;
		int leftWidth = Math.max(0, width - gap - rightWidth);
		if (hasLeft && leftWidth > 0) {
			graphics.text(font, ellipsized(font, left.orElseThrow(), leftWidth), x, y, leftColor, false);
		}
		if (hasRight && rightWidth > 0) {
			FormattedCharSequence value = ellipsized(font, right, rightWidth);
			graphics.text(font, value, x + width - font.width(value), y, rightColor, true);
		}
	}

	private static FormattedCharSequence ellipsized(final Font font, final Component value, final int width) {
		if (width <= 0) {
			return Component.empty().getVisualOrderText();
		}
		if (font.width(value) <= width) {
			return value.getVisualOrderText();
		}
		Component suffix = Component.literal("…");
		if (font.width(suffix) > width) {
			return Component.empty().getVisualOrderText();
		}
		FormattedText cut = font.substrByWidth(value, Math.max(0, width - font.width(suffix)));
		return Language.getInstance().getVisualOrder(FormattedText.composite(cut, suffix));
	}

	private static String formatTicks(final long ticks, final TimerFormat format) {
		long seconds = Math.max(0L, ticks / 20L);
		long hours = seconds / 3600L;
		long minutes = seconds / 60L % 60L;
		long remainder = seconds % 60L;
		return switch (format) {
			case MINUTES_SECONDS -> String.format(Locale.ROOT, "%d:%02d", seconds / 60L, remainder);
			case HOURS_MINUTES_SECONDS -> String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder);
			case LOCALIZED_DURATION -> hours > 0L
				? hours + "时 " + minutes + "分 " + remainder + "秒"
				: minutes > 0L ? minutes + "分 " + remainder + "秒" : remainder + "秒";
		};
	}

	private static void renderCountdownMotion(
		final GuiGraphicsExtractor graphics,
		final MotionSample<ClientCountdownSnapshot> motion,
		final Snapshot preferences
	) {
		if (motion.previous().isPresent() && motion.previousOpacity() > 0.001F) {
			renderCountdown(
				graphics,
				motion.previous().orElseThrow(),
				preferences,
				motion.previousOpacity(),
				0.0F,
				1.0F
			);
		}
		if (motion.current().isPresent() && motion.currentOpacity() > 0.001F) {
			renderCountdown(
				graphics,
				motion.current().orElseThrow(),
				preferences,
				motion.currentOpacity(),
				motion.translateY(),
				motion.focusScale()
			);
		}
	}

	private static void renderCountdown(
		final GuiGraphicsExtractor graphics,
		final ClientCountdownSnapshot countdown,
		final Snapshot preferences,
		final float transitionOpacity,
		final float transitionY,
		final float transitionScale
	) {
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		double referenceScale = referenceScale(screenWidth, screenHeight);
		renderCountdownInViewport(
			graphics,
			countdown,
			preferences,
			0,
			0,
			screenWidth,
			screenHeight,
			referenceScale,
			transitionOpacity,
			transitionY,
			transitionScale,
			"countdown",
			true
		);
	}

	/**
	 * Draws a frozen settings/development Countdown with the exact production planner and node
	 * renderer. The supplied rectangle is letterboxed to the shared 640x360 reference canvas; this
	 * method owns only clipping and Countdown content, so the caller remains responsible for the
	 * surrounding preview panel.
	 */
	public static boolean renderCountdownPreview(
		final GuiGraphicsExtractor graphics,
		final ClientCountdownSnapshot countdown,
		final int x,
		final int y,
		final int width,
		final int height,
		final Snapshot preferences
	) {
		Objects.requireNonNull(graphics, "graphics");
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(preferences, "preferences");
		PreviewFrame frame = previewFrame(x, y, width, height);
		return renderCountdownInViewport(
			graphics,
			countdown,
			preferences,
			frame.x(),
			frame.y(),
			frame.width(),
			frame.height(),
			frame.scale(),
			1.0F,
			0.0F,
			1.0F,
			"countdown_preview",
			false
		);
	}

	private static boolean renderCountdownInViewport(
		final GuiGraphicsExtractor graphics,
		final ClientCountdownSnapshot countdown,
		final Snapshot preferences,
		final int viewportX,
		final int viewportY,
		final int viewportWidth,
		final int viewportHeight,
		final double referenceScale,
		final float transitionOpacity,
		final float transitionY,
		final float transitionScale,
		final String smoothChannel,
		final boolean serverClock
	) {
		if (viewportWidth < 80 || viewportHeight < 60 || referenceScale <= 0.0D) {
			return false;
		}
		Font font = Minecraft.getInstance().font;
		int maximumWidth = Math.min(300, Math.max(1, (int)Math.floor(
			(viewportWidth - scaled(20, referenceScale)) / referenceScale
		)));
		int maximumHeight = Math.min(184, Math.max(1, (int)Math.floor(
			(viewportHeight - scaled(24, referenceScale)) / referenceScale
		)));
		PlanCandidate candidate = planCandidates(
			countdown.mode(),
			countdown.nodes(),
			countdown.layoutVariants(),
			maximumWidth,
			maximumHeight,
			Set.of()
		).stream().findFirst().orElse(null);
		if (candidate == null) {
			return false;
		}
		Plan plan = candidate.plan();
		double scale = referenceScale;
		int x = viewportX + (viewportWidth - (int)Math.ceil(plan.width() * scale)) / 2;
		int y = viewportY + Math.max(
			scaled(10, referenceScale),
			(viewportHeight - (int)Math.ceil(plan.height() * scale)) / 2 - scaled(18, referenceScale)
		);
		float opacity = Math.max(0.72F, preferences.opacity()) * transitionOpacity;
		graphics.enableScissor(
			viewportX,
			viewportY,
			viewportX + viewportWidth,
			viewportY + viewportHeight
		);
		graphics.pose().pushMatrix();
		double animatedScale = scale * transitionScale;
		double baseWidth = plan.width() * scale;
		double baseHeight = plan.height() * scale;
		double animatedWidth = plan.width() * animatedScale;
		double animatedHeight = plan.height() * animatedScale;
		graphics.pose().translate(
			(float)(x + (baseWidth - animatedWidth) / 2.0D),
			(float)(y + (baseHeight - animatedHeight) / 2.0D + transitionY * scale)
		);
		graphics.pose().scale((float)animatedScale, (float)animatedScale);
		try {
			renderEntry(
				graphics,
				font,
				plan.root(),
					renderContext(
						smoothChannel,
						countdown.version(),
						countdown.countdownInstanceId() + "/" + candidate.layoutId(),
						countdown.receivedAtNanos(),
						serverClock
					),
				opacity,
				preferences.highContrast(),
				preferences.motion()
			);
		} finally {
			graphics.pose().popMatrix();
			graphics.disableScissor();
		}
		return true;
	}

	/** Draws an isolated settings/development preview without replacing the formal runtime dock. */
	public static void renderPreview(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final Snapshot draft
	) {
		Optional<ClientHudSnapshot> source = ClientHudRuntime.previewSnapshot().or(ClientHudRuntime::snapshot);
		graphics.fill(x, y, x + width, y + height, 0xB80B1219);
		graphics.outline(x, y, width, height, SURFACE_BORDER);
		PreviewFrame frame = previewFrame(x, y, width, height);
		graphics.fill(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), 0xD8070D13);
		graphics.outline(frame.x(), frame.y(), frame.width(), frame.height(), PANEL_BORDER);
		if (source.isEmpty()) {
			renderSamplePreview(graphics, frame, draft);
			return;
		}
		ClientHudSnapshot snapshot = source.orElseThrow();
		Effective preview = effectivePreview(snapshot.policy(), draft);
		double dockScale = preview.scale();
		int logicalMaximumWidth = Math.max(
			1,
			Math.min(MAX_DOCK_WIDTH, (int)Math.floor((REFERENCE_WIDTH - SCREEN_MARGIN * 2) / dockScale))
		);
		int logicalMaximumHeight = Math.max(
			1,
			Math.min(MAX_DOCK_HEIGHT, (int)Math.floor((REFERENCE_HEIGHT - BOTTOM_GAMEPLAY_RESERVE - SCREEN_MARGIN) / dockScale))
		);
		PlanCandidate candidate = choosePlan(snapshot, logicalMaximumWidth, logicalMaximumHeight, preview).orElse(null);
		if (candidate == null || !preview.visible()) {
			return;
		}
		Plan plan = candidate.plan();
		int renderedWidth = Math.max(1, (int)Math.ceil(plan.width() * dockScale));
		int renderedHeight = Math.max(1, (int)Math.ceil(plan.height() * dockScale));
		Placement placement = solvePlacement(
			preview.anchor(),
			preview.offsetX(),
			preview.offsetY(),
			renderedWidth,
			renderedHeight,
			REFERENCE_WIDTH,
			REFERENCE_HEIGHT,
			SCREEN_MARGIN,
			BOTTOM_GAMEPLAY_RESERVE,
			preview.exclusions(),
			false,
			List.of()
		).orElse(null);
		if (placement == null) {
			return;
		}
		int drawX = frame.x() + (int)Math.round(placement.x() * frame.scale());
		int drawY = frame.y() + (int)Math.round(placement.y() * frame.scale());
		graphics.enableScissor(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height());
		graphics.pose().pushMatrix();
		graphics.pose().translate(drawX, drawY);
		graphics.pose().scale((float)(frame.scale() * dockScale), (float)(frame.scale() * dockScale));
		try {
			renderEntry(
				graphics,
				Minecraft.getInstance().font,
				plan.root(),
				renderContext("preview", snapshot.version(), candidate.layoutId(), snapshot.receivedAtNanos(), false),
				(float)preview.opacity(),
				preview.highContrast(),
				preview.motion()
			);
		} finally {
			graphics.pose().popMatrix();
			graphics.disableScissor();
		}
	}

	private static Effective effectivePreview(
		final ClientHudSnapshot.ClientPolicy allowed,
		final Snapshot draft
	) {
		Anchor anchor = allowed.allowedAnchors().contains(draft.anchor()) ? draft.anchor() : allowed.defaultAnchor();
		return new Effective(
			allowed.allowHide() ? draft.hudVisible() : true,
			anchor,
			Math.clamp(draft.offsetX(), -allowed.maximumOffsetX(), allowed.maximumOffsetX()),
			Math.clamp(draft.offsetY(), -allowed.maximumOffsetY(), allowed.maximumOffsetY()),
			Math.clamp(draft.scale(), allowed.minimumScale(), allowed.maximumScale()),
			Math.clamp(draft.opacity(), allowed.minimumOpacity(), allowed.maximumOpacity()),
			draft.highContrast(),
			draft.motion(),
			draft.hudVolume(),
			draft.countdownVolume(),
			allowed.allowComponentManagement() ? draft.hiddenComponents() : Set.of(),
			allowed.allowComponentManagement() ? draft.compactComponents() : Set.of(),
			allowed.allowComponentManagement() ? draft.componentOrder() : Map.of(),
			draft.exclusions(),
			draft.rawDiagnostics()
		);
	}

	private static void renderSamplePreview(
		final GuiGraphicsExtractor graphics,
		final PreviewFrame frame,
		final Snapshot draft
	) {
		if (!draft.hudVisible()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int dockWidth = 188;
		int dockHeight = 70;
		Placement placement = solvePlacement(
			draft.anchor(),
			draft.offsetX(),
			draft.offsetY(),
			Math.max(1, (int)Math.ceil(dockWidth * draft.scale())),
			Math.max(1, (int)Math.ceil(dockHeight * draft.scale())),
			REFERENCE_WIDTH,
			REFERENCE_HEIGHT,
			SCREEN_MARGIN,
			BOTTOM_GAMEPLAY_RESERVE,
			draft.exclusions(),
			false,
			List.of()
		).orElse(null);
		if (placement == null) {
			return;
		}
		int left = frame.x() + (int)Math.round(placement.x() * frame.scale());
		int top = frame.y() + (int)Math.round(placement.y() * frame.scale());
		float opacity = draft.opacity();
		graphics.enableScissor(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height());
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale((float)(frame.scale() * draft.scale()), (float)(frame.scale() * draft.scale()));
		try {
			graphics.fill(0, 0, dockWidth, dockHeight, alpha(PANEL, opacity));
			graphics.outline(0, 0, dockWidth, dockHeight, alpha(draft.highContrast() ? 0xFFFFFFFF : PANEL_BORDER, opacity));
			graphics.fill(0, dockHeight - 8, dockWidth, dockHeight - 6, alpha(INFO_CYAN, opacity));
			graphics.fill(123, dockHeight - 10, 128, dockHeight - 4, alpha(BRAND_GOLD, opacity));
			graphics.text(font, "当前任务", 10, 9, alpha(INFO_CYAN, opacity), false);
			graphics.text(font, "示例任务标题", 10, 24, alpha(MAIN_TEXT, opacity), true);
			graphics.text(font, "剩余  12:45", 10, 42, alpha(SECONDARY_TEXT, opacity), false);
		} finally {
			graphics.pose().popMatrix();
			graphics.disableScissor();
		}
	}

	/** Returns the 16:9 reference viewport used by both settings previews and exclusion editing. */
	public static PreviewFrame previewFrame(final int x, final int y, final int width, final int height) {
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		double scale = Math.max(
			0.001D,
			Math.min(safeWidth / (double)REFERENCE_WIDTH, safeHeight / (double)REFERENCE_HEIGHT)
		);
		int frameWidth = Math.max(1, (int)Math.floor(REFERENCE_WIDTH * scale));
		int frameHeight = Math.max(1, (int)Math.floor(REFERENCE_HEIGHT * scale));
		return new PreviewFrame(
			x + (safeWidth - frameWidth) / 2,
			y + (safeHeight - frameHeight) / 2,
			frameWidth,
			frameHeight,
			scale
		);
	}

	public static double previewReferenceScale(final int width, final int height) {
		return previewFrame(0, 0, width, height).scale();
	}

	private static RenderContext renderContext(
		final String channel,
		final ClientHudSnapshot.Version version,
		final String layoutId,
		final long receivedAtNanos,
		final boolean serverClock
	) {
		String scope = version.gameInstanceId()
			+ ":" + version.definitionGeneration()
			+ ":" + version.epoch()
			+ ":" + layoutId;
		synchronized (SMOOTH_LOCK) {
			String previous = ACTIVE_SMOOTH_SCOPES.put(channel, scope);
			if (previous != null && !previous.equals(scope)) {
				SMOOTH_PROGRESS.keySet().removeIf(key -> key.channel().equals(channel) && !key.scope().equals(scope));
			}
		}
		return new RenderContext(channel, scope, receivedAtNanos, serverClock);
	}

	/** Releases all interpolated values owned by a surface after its exit has fully completed. */
	private static void clearSmoothChannel(final String channel) {
		synchronized (SMOOTH_LOCK) {
			ACTIVE_SMOOTH_SCOPES.remove(channel);
			SMOOTH_PROGRESS.keySet().removeIf(key -> key.channel().equals(channel));
		}
	}

	private static double smoothProgress(
		final RenderContext context,
		final String nodeId,
		final double target,
		final boolean smooth,
		final Motion motion
	) {
		ProgressKey key = new ProgressKey(context.smoothChannel(), context.smoothScope(), nodeId);
		long nowNanos = System.nanoTime();
		synchronized (SMOOTH_LOCK) {
			double fraction = target;
			if (smooth && motion != Motion.STATIC) {
				ProgressState previous = SMOOTH_PROGRESS.get(key);
				if (previous != null) {
					double elapsedSeconds = Math.clamp((nowNanos - previous.atNanos()) / 1_000_000_000.0D, 0.0D, 0.25D);
					double response = motion == Motion.FULL ? 10.5D : 28.5D;
					double factor = 1.0D - Math.exp(-response * elapsedSeconds);
					fraction = Math.abs(target - previous.value()) < 0.001D
						? target
						: previous.value() + (target - previous.value()) * factor;
				}
			}
			SMOOTH_PROGRESS.put(key, new ProgressState(fraction, nowNanos));
			return fraction;
		}
	}

	private static int alpha(final int color, final float opacity) {
		int source = color >>> 24;
		int alpha = Math.clamp(Math.round(source * Math.clamp(opacity, 0.0F, 1.0F)), 0, 255);
		return alpha << 24 | color & 0x00FFFFFF;
	}

	private record Placement(int x, int y) {
	}

	private record PlanCandidate(Plan plan, String layoutId) {
	}

	public record PreviewFrame(int x, int y, int width, int height, double scale) {
	}

	private record RenderContext(
		String smoothChannel,
		String smoothScope,
		long receivedAtNanos,
		boolean serverClock
	) {
	}

	private record ProgressKey(String channel, String scope, String nodeId) {
	}

	private record ProgressState(double value, long atNanos) {
	}

	private enum MotionPhase {
		NONE,
		ENTER,
		CHANGE,
		EXIT
	}

	static record MotionSample<T>(
		Optional<T> previous,
		Optional<T> current,
		float previousOpacity,
		float currentOpacity,
		float translateY,
		float focusScale
	) {
		MotionSample {
			previous = Objects.requireNonNull(previous, "previous");
			current = Objects.requireNonNull(current, "current");
		}

		boolean visible() {
			return this.previousOpacity > 0.001F && this.previous.isPresent()
				|| this.currentOpacity > 0.001F && this.current.isPresent();
		}
	}

	/** One wall-clock transition gate per formal surface; snapshot polling never restarts it. */
	static final class SurfaceMotion<T> {
		private final Function<T, ClientHudSnapshot.Version> version;
		private final Function<T, String> structure;
		private final ToIntFunction<T> fingerprint;
		private final Function<T, LayoutTransition> transition;
		private final ToLongFunction<T> receivedAtNanos;
		private T previous;
		private T current;
		private ClientHudSnapshot.Version observedVersion;
		private boolean observedEmpty = true;
		private MotionPhase phase = MotionPhase.NONE;
		private LayoutTransition activeTransition;
		private long startedAtNanos;
		private long lastSampleAtNanos;
		private boolean sampled;

		SurfaceMotion(
			final Function<T, ClientHudSnapshot.Version> version,
			final Function<T, String> structure,
			final ToIntFunction<T> fingerprint,
			final Function<T, LayoutTransition> transition,
			final ToLongFunction<T> receivedAtNanos
		) {
			this.version = Objects.requireNonNull(version, "version");
			this.structure = Objects.requireNonNull(structure, "structure");
			this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
			this.transition = Objects.requireNonNull(transition, "transition");
			this.receivedAtNanos = Objects.requireNonNull(receivedAtNanos, "receivedAtNanos");
		}

		synchronized void clear() {
			this.previous = null;
			this.current = null;
			this.observedVersion = null;
			this.observedEmpty = true;
			this.phase = MotionPhase.NONE;
			this.activeTransition = null;
			this.startedAtNanos = 0L;
			this.lastSampleAtNanos = 0L;
			this.sampled = false;
		}

		synchronized MotionSample<T> sample(
			final Optional<T> supplied,
			final Motion motion,
			final long nowNanos,
			final boolean visible,
			final boolean settleRequested
		) {
			Objects.requireNonNull(supplied, "supplied");
			Objects.requireNonNull(motion, "motion");
			if (nowNanos < 0L) {
				throw new IllegalArgumentException("nowNanos must be non-negative");
			}
			boolean missedAnimationWindow = this.sampled
				&& nowNanos >= this.lastSampleAtNanos
				&& nowNanos - this.lastSampleAtNanos > MAX_ANIMATION_CATCH_UP_NANOS;
			boolean staleFirstObservation = !this.sampled
				&& supplied.filter(value -> {
					long received = this.receivedAtNanos.applyAsLong(value);
					return received >= 0L
						&& nowNanos >= received
						&& nowNanos - received > MAX_ANIMATION_CATCH_UP_NANOS;
				}).isPresent();
			this.sampled = true;
			this.lastSampleAtNanos = nowNanos;
			if (!visible || settleRequested || missedAnimationWindow || staleFirstObservation) {
				settle(supplied);
				return visible ? steady() : hidden();
			}

			if (supplied.isEmpty()) {
				if (!this.observedEmpty) {
					this.observedEmpty = true;
					this.observedVersion = null;
					beginExit(motion, nowNanos);
				}
			} else {
				T next = supplied.orElseThrow();
				ClientHudSnapshot.Version nextVersion = this.version.apply(next);
				if (this.observedEmpty || !nextVersion.equals(this.observedVersion)) {
					accept(next, motion, nowNanos);
					this.observedEmpty = false;
					this.observedVersion = nextVersion;
				}
			}

			if (motion == Motion.STATIC && this.phase != MotionPhase.NONE) {
				this.previous = null;
				if (this.phase == MotionPhase.EXIT) {
					this.activeTransition = null;
				}
				this.phase = MotionPhase.NONE;
				this.startedAtNanos = 0L;
			}
			if (this.phase == MotionPhase.NONE) {
				return steady();
			}
			long duration = durationNanos(this.phase, motion);
			float raw = duration <= 0L
				? 1.0F
				: (float)Math.clamp((nowNanos - this.startedAtNanos) / (double)duration, 0.0D, 1.0D);
			float progress = raw * raw * (3.0F - 2.0F * raw);
			if (raw >= 1.0F) {
				boolean exited = this.phase == MotionPhase.EXIT;
				this.previous = null;
				this.phase = MotionPhase.NONE;
				this.startedAtNanos = 0L;
				if (exited) {
					this.activeTransition = null;
				}
				return steady();
			}
			return animated(progress, motion);
		}

		private void settle(final Optional<T> supplied) {
			this.previous = null;
			this.phase = MotionPhase.NONE;
			this.startedAtNanos = 0L;
			if (supplied.isEmpty()) {
				this.current = null;
				this.observedVersion = null;
				this.observedEmpty = true;
				this.activeTransition = null;
				return;
			}
			T next = supplied.orElseThrow();
			this.current = next;
			this.observedVersion = this.version.apply(next);
			this.observedEmpty = false;
			this.activeTransition = this.transition.apply(next);
		}

		private MotionSample<T> hidden() {
			return new MotionSample<>(
				Optional.empty(),
				Optional.empty(),
				0.0F,
				0.0F,
				0.0F,
				1.0F
			);
		}

		private void accept(final T next, final Motion motion, final long nowNanos) {
			LayoutTransition nextTransition = this.transition.apply(next);
			if (this.current == null) {
				this.previous = null;
				this.current = next;
				this.activeTransition = nextTransition;
				this.phase = motion != Motion.STATIC && nextTransition.enter() != EnterTransition.NONE
					? MotionPhase.ENTER
					: MotionPhase.NONE;
				this.startedAtNanos = nowNanos;
				return;
			}

			boolean changed = !this.structure.apply(this.current).equals(this.structure.apply(next))
				|| this.fingerprint.applyAsInt(this.current) != this.fingerprint.applyAsInt(next);
			if (
				changed
					&& motion != Motion.STATIC
					&& nextTransition.change() == ChangeTransition.CROSSFADE_VALUES
			) {
				this.previous = this.current;
				this.current = next;
				this.activeTransition = nextTransition;
				this.phase = MotionPhase.CHANGE;
				this.startedAtNanos = nowNanos;
				return;
			}

			this.current = next;
			this.activeTransition = nextTransition;
			if (this.phase == MotionPhase.NONE || this.phase == MotionPhase.CHANGE) {
				this.previous = null;
				this.phase = MotionPhase.NONE;
			}
		}

		private void beginExit(final Motion motion, final long nowNanos) {
			if (this.current == null) {
				return;
			}
			LayoutTransition outgoing = this.transition.apply(this.current);
			if (motion != Motion.STATIC && outgoing.exit() == ExitTransition.FADE) {
				this.previous = this.current;
				this.current = null;
				this.activeTransition = outgoing;
				this.phase = MotionPhase.EXIT;
				this.startedAtNanos = nowNanos;
				return;
			}
			this.previous = null;
			this.current = null;
			this.activeTransition = outgoing;
			this.phase = MotionPhase.NONE;
		}

		private MotionSample<T> steady() {
			return new MotionSample<>(
				Optional.empty(),
				Optional.ofNullable(this.current),
				0.0F,
				this.current == null ? 0.0F : 1.0F,
				0.0F,
				1.0F
			);
		}

		private MotionSample<T> animated(final float progress, final Motion motion) {
			if (this.phase == MotionPhase.CHANGE) {
				return new MotionSample<>(
					Optional.ofNullable(this.previous),
					Optional.ofNullable(this.current),
					1.0F - progress,
					progress,
					0.0F,
					1.0F
				);
			}
			if (this.phase == MotionPhase.EXIT) {
				return new MotionSample<>(
					Optional.ofNullable(this.previous),
					Optional.empty(),
					1.0F - progress,
					0.0F,
					0.0F,
					1.0F
				);
			}
			EnterTransition enter = this.activeTransition == null
				? EnterTransition.NONE
				: this.activeTransition.enter();
			float translateY = motion == Motion.FULL && enter == EnterTransition.RISE_FADE
				? (1.0F - progress) * 7.0F
				: 0.0F;
			float focusScale = motion == Motion.FULL && enter == EnterTransition.FOCUS_FADE
				? 0.92F + 0.08F * progress
				: 1.0F;
			return new MotionSample<>(
				Optional.empty(),
				Optional.ofNullable(this.current),
				0.0F,
				progress,
				translateY,
				focusScale
			);
		}

		private static long durationNanos(final MotionPhase phase, final Motion motion) {
			if (motion == Motion.STATIC) {
				return 0L;
			}
			return switch (phase) {
				case ENTER -> motion == Motion.FULL ? 220_000_000L : 120_000_000L;
				case CHANGE -> motion == Motion.FULL ? 160_000_000L : 100_000_000L;
				case EXIT -> motion == Motion.FULL ? 160_000_000L : 100_000_000L;
				case NONE -> 0L;
			};
		}
	}

	private record Rect(int x, int y, int width, int height) {
		private int right() {
			return this.x + this.width;
		}

		private int bottom() {
			return this.y + this.height;
		}

		private boolean intersects(final Rect other) {
			return this.x < other.right()
				&& this.right() > other.x
				&& this.y < other.bottom()
				&& this.bottom() > other.y;
		}
	}
}
