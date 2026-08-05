package io.github.zcpu954861.pixeltzzpro.client.hud;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * Immutable, already-authorized client projection for the single V3C information dock.
 *
 * <p>The model intentionally contains no data-source paths, selectors or callbacks. Networking
 * may replace the adapter that builds it, while the renderer continues to consume one atomic
 * value and therefore can never combine fields from two server snapshots.</p>
 */
public record ClientHudSnapshot(
	Version version,
	String profileId,
	long profileContentVersion,
	String layoutId,
	Mode mode,
	String rootNodeId,
	Map<String, ClientHudNode> nodes,
	List<LayoutVariant> layoutVariants,
	Optional<Component> eyebrow,
	Component title,
	Optional<Component> body,
	Optional<ProgressValue> progress,
	Optional<TimerValue> timer,
	List<FieldValue> fields,
	Optional<SpineValue> spine,
	ClientPolicy policy,
	List<ComponentControl> components,
	long receivedAtNanos,
	String structureKey,
	String canonicalJson
) {
	public ClientHudSnapshot {
		version = Objects.requireNonNull(version, "version");
		profileId = boundedId(profileId, "profileId");
		if (profileContentVersion < 0L) {
			throw new IllegalArgumentException("profileContentVersion must be non-negative");
		}
		layoutId = boundedId(layoutId, "layoutId");
		mode = Objects.requireNonNull(mode, "mode");
		rootNodeId = boundedId(rootNodeId, "rootNodeId");
		nodes = Map.copyOf(nodes);
		if (nodes.isEmpty() || !nodes.containsKey(rootNodeId)) {
			throw new IllegalArgumentException("HUD projection has no reachable root node");
		}
		layoutVariants = List.copyOf(layoutVariants);
		validateLayoutAuthority(layoutId, mode, rootNodeId, nodes, layoutVariants);
		eyebrow = copy(eyebrow);
		title = Objects.requireNonNull(title, "title").copy();
		body = copy(body);
		progress = Objects.requireNonNull(progress, "progress");
		timer = Objects.requireNonNull(timer, "timer");
		fields = List.copyOf(fields);
		spine = Objects.requireNonNull(spine, "spine");
		policy = Objects.requireNonNull(policy, "policy");
		components = List.copyOf(components);
		if (receivedAtNanos < 0L) {
			throw new IllegalArgumentException("receivedAtNanos must be non-negative");
		}
		structureKey = Objects.requireNonNull(structureKey, "structureKey");
		canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
	}

	private static Optional<Component> copy(final Optional<Component> value) {
		return Objects.requireNonNull(value, "component").map(Component::copy);
	}

	static String boundedId(final String value, final String label) {
		String result = Objects.requireNonNull(value, label).strip();
		if (result.isEmpty() || result.length() > 256) {
			throw new IllegalArgumentException(label + " is outside its bounded length");
		}
		return result;
	}

	private static Optional<String> boundedOptionalId(
		final Optional<String> value,
		final String label
	) {
		return Objects.requireNonNull(value, label).map(id -> boundedId(id, label));
	}

	static void validateLayoutAuthority(
		final String activeLayoutId,
		final Mode activeMode,
		final String activeRootNodeId,
		final Map<String, ClientHudNode> nodes,
		final List<LayoutVariant> variants
	) {
		if (variants.isEmpty() || variants.size() > 9) {
			throw new IllegalArgumentException("HUD layout degradation chain is empty or too deep");
		}
		if (!variants.getFirst().layoutId().equals(activeLayoutId)) {
			throw new IllegalArgumentException("HUD active layout is not the first authorized variant");
		}
		Set<String> ids = new java.util.LinkedHashSet<>();
		for (int index = 0; index < variants.size(); index++) {
			LayoutVariant variant = variants.get(index);
			if (!ids.add(variant.layoutId())) {
				throw new IllegalArgumentException("HUD layout degradation chain contains a cycle");
			}
			for (String root : variant.roots()) {
				if (!nodes.containsKey(root)) {
					throw new IllegalArgumentException("HUD layout variant references an unknown root");
				}
			}
			Optional<String> expected = index + 1 < variants.size()
				? Optional.of(variants.get(index + 1).layoutId())
				: Optional.empty();
			if (!variant.degradationLayoutId().equals(expected)) {
				throw new IllegalArgumentException("HUD degradation chain is not a closed ordered path");
			}
		}
		LayoutVariant active = variants.getFirst();
		if (active.root(activeMode).filter(activeRootNodeId::equals).isEmpty()) {
			throw new IllegalArgumentException("HUD active root was not registered for its mode");
		}
	}

	public enum Mode {
		NORMAL,
		COMPACT,
		SUMMARY
	}

	public enum Growth {
		UP,
		CENTER
	}

	public enum EnterTransition {
		NONE,
		FADE,
		RISE_FADE,
		FOCUS_FADE
	}

	public enum ChangeTransition {
		NONE,
		CROSSFADE_VALUES
	}

	public enum ExitTransition {
		NONE,
		FADE
	}

	public record HudSound(net.minecraft.resources.Identifier event, float volume, float pitch) {
		public HudSound {
			event = Objects.requireNonNull(event, "event");
			if (
				!Float.isFinite(volume)
					|| !Float.isFinite(pitch)
					|| volume < 0.0F
					|| volume > 4.0F
					|| pitch < 0.5F
					|| pitch > 2.0F
			) {
				throw new IllegalArgumentException("HUD sound values are outside safe bounds");
			}
		}
	}

	public record LayoutSize(
		int minimumWidth,
		int preferredWidth,
		int maximumWidth,
		int maximumHeight,
		Growth growth
	) {
		public LayoutSize {
			if (
				minimumWidth < 1
					|| preferredWidth < minimumWidth
					|| maximumWidth < preferredWidth
					|| maximumWidth > 2_048
					|| maximumHeight < 1
					|| maximumHeight > 2_048
			) {
				throw new IllegalArgumentException("HUD layout size is outside safe bounds");
			}
			growth = Objects.requireNonNull(growth, "growth");
		}
	}

	public record LayoutTransition(
		EnterTransition enter,
		ChangeTransition change,
		ExitTransition exit,
		Optional<HudSound> enterSound,
		Optional<HudSound> changeSound
	) {
		public LayoutTransition {
			enter = Objects.requireNonNull(enter, "enter");
			change = Objects.requireNonNull(change, "change");
			exit = Objects.requireNonNull(exit, "exit");
			enterSound = Objects.requireNonNull(enterSound, "enterSound");
			changeSound = Objects.requireNonNull(changeSound, "changeSound");
		}
	}

	/** One server-authorized layout and only the semantic roots registered by its data pack. */
	public record LayoutVariant(
		String layoutId,
		Optional<String> normalRootNodeId,
		Optional<String> compactRootNodeId,
		Optional<String> summaryRootNodeId,
		Optional<String> degradationLayoutId,
		LayoutSize size,
		LayoutTransition transition
	) {
		public LayoutVariant {
			layoutId = boundedId(layoutId, "variant layoutId");
			normalRootNodeId = boundedOptionalId(normalRootNodeId, "normalRootNodeId");
			compactRootNodeId = boundedOptionalId(compactRootNodeId, "compactRootNodeId");
			summaryRootNodeId = boundedOptionalId(summaryRootNodeId, "summaryRootNodeId");
			degradationLayoutId = boundedOptionalId(degradationLayoutId, "degradationLayoutId");
			if (
				normalRootNodeId.isEmpty()
					&& compactRootNodeId.isEmpty()
					&& summaryRootNodeId.isEmpty()
			) {
				throw new IllegalArgumentException("HUD layout variant has no authorized root");
			}
			if (degradationLayoutId.filter(layoutId::equals).isPresent()) {
				throw new IllegalArgumentException("HUD layout cannot degrade to itself");
			}
			size = Objects.requireNonNull(size, "size");
			transition = Objects.requireNonNull(transition, "transition");
		}

		public Optional<String> root(final Mode mode) {
			return switch (Objects.requireNonNull(mode, "mode")) {
				case NORMAL -> this.normalRootNodeId;
				case COMPACT -> this.compactRootNodeId;
				case SUMMARY -> this.summaryRootNodeId;
			};
		}

		public List<String> roots() {
			return java.util.stream.Stream.of(
				this.normalRootNodeId,
				this.compactRootNodeId,
				this.summaryRootNodeId
			).flatMap(Optional::stream).toList();
		}
	}

	public enum Anchor {
		BOTTOM_RIGHT,
		BOTTOM_LEFT,
		TOP_RIGHT,
		TOP_LEFT
	}

	public enum TabCollision {
		DIM,
		HIDE,
		KEEP
	}

	public enum Priority {
		CRITICAL,
		PRIMARY,
		SECONDARY,
		DECORATIVE
	}

	public enum TimerFormat {
		MINUTES_SECONDS,
		HOURS_MINUTES_SECONDS,
		LOCALIZED_DURATION
	}

	public record Version(
		UUID gameInstanceId,
		long definitionGeneration,
		long epoch,
		long contextVersion,
		long sequence
	) {
		public Version {
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			if (
				definitionGeneration < 0L
					|| epoch < 0L
					|| contextVersion < 0L
					|| sequence < 0L
			) {
				throw new IllegalArgumentException("HUD version fields must be non-negative");
			}
		}
	}

	public record ProgressValue(
		double current,
		double maximum,
		Optional<Component> label,
		boolean smooth
	) {
		public ProgressValue {
			if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0D) {
				throw new IllegalArgumentException("HUD progress is invalid");
			}
			current = Math.clamp(current, 0.0D, maximum);
			label = copy(label);
		}

		public double fraction() {
			return Math.clamp(this.current / this.maximum, 0.0D, 1.0D);
		}
	}

	/** A locally interpolated timer whose signed rate is measured in ticks per game tick. */
	public record TimerValue(
		long serverTick,
		double baseTicks,
		double rate,
		boolean paused,
		TimerFormat format,
		Optional<Component> pausedMarker
	) {
		public TimerValue {
			if (serverTick < 0L || !Double.isFinite(baseTicks) || !Double.isFinite(rate)) {
				throw new IllegalArgumentException("HUD timer is invalid");
			}
			baseTicks = Math.max(0.0D, baseTicks);
			format = Objects.requireNonNull(format, "format");
			pausedMarker = copy(pausedMarker);
		}

		public long ticksAt(final long receivedAtNanos, final long nowNanos) {
			double elapsedTicks = this.paused
				? 0.0D
				: Math.max(0L, nowNanos - receivedAtNanos) / 50_000_000.0D * this.rate;
			return Math.max(0L, Math.round(this.baseTicks + elapsedTicks));
		}

		/** Interpolates from the shared latency-corrected server clock used by formal HUD surfaces. */
		public long ticksAt(final double estimatedServerTick) {
			if (!Double.isFinite(estimatedServerTick)) {
				throw new IllegalArgumentException("estimatedServerTick must be finite");
			}
			double elapsedTicks = this.paused
				? 0.0D
				: Math.max(0.0D, estimatedServerTick - this.serverTick) * this.rate;
			return Math.max(0L, Math.round(this.baseTicks + elapsedTicks));
		}
	}

	public record FieldValue(
		String id,
		Component label,
		Component value,
		Priority priority,
		int accent
	) {
		public FieldValue {
			id = boundedId(id, "field id");
			label = Objects.requireNonNull(label, "label").copy();
			value = Objects.requireNonNull(value, "value").copy();
			priority = Objects.requireNonNull(priority, "priority");
		}
	}

	public record SpineValue(
		Optional<Component> label,
		double current,
		double maximum
	) {
		public SpineValue {
			label = copy(label);
			if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0D) {
				throw new IllegalArgumentException("HUD spine progress is invalid");
			}
			current = Math.clamp(current, 0.0D, maximum);
		}

		public double fraction() {
			return Math.clamp(this.current / this.maximum, 0.0D, 1.0D);
		}
	}

	public record ClientPolicy(
		boolean allowHide,
		Anchor defaultAnchor,
		Set<Anchor> allowedAnchors,
		int maximumOffsetX,
		int maximumOffsetY,
		double minimumScale,
		double defaultScale,
		double maximumScale,
		double minimumOpacity,
		double defaultOpacity,
		double maximumOpacity,
		boolean allowComponentManagement,
		TabCollision tabCollision
	) {
		public ClientPolicy {
			defaultAnchor = Objects.requireNonNull(defaultAnchor, "defaultAnchor");
			allowedAnchors = Set.copyOf(allowedAnchors);
			if (allowedAnchors.isEmpty() || !allowedAnchors.contains(defaultAnchor)) {
				throw new IllegalArgumentException("HUD anchor policy is invalid");
			}
			if (
				maximumOffsetX < 0
					|| maximumOffsetX > 192
					|| maximumOffsetY < 0
					|| maximumOffsetY > 192
					|| !orderedRange(minimumScale, defaultScale, maximumScale, 0.5D, 1.5D)
					|| !orderedRange(minimumOpacity, defaultOpacity, maximumOpacity, 0.35D, 1.0D)
			) {
				throw new IllegalArgumentException("HUD client policy is outside safe bounds");
			}
			tabCollision = Objects.requireNonNull(tabCollision, "tabCollision");
		}

		private static boolean orderedRange(
			final double minimum,
			final double value,
			final double maximum,
			final double floor,
			final double ceiling
		) {
			return Double.isFinite(minimum)
				&& Double.isFinite(value)
				&& Double.isFinite(maximum)
				&& minimum >= floor
				&& maximum <= ceiling
				&& minimum <= value
				&& value <= maximum;
		}

		public static ClientPolicy safeDefaults() {
			return new ClientPolicy(
				true,
				Anchor.BOTTOM_RIGHT,
				Set.of(Anchor.BOTTOM_RIGHT, Anchor.BOTTOM_LEFT),
				96,
				96,
				0.75D,
				1.0D,
				1.25D,
				0.55D,
				0.92D,
				1.0D,
				true,
				TabCollision.DIM
			);
		}
	}

	public record ComponentControl(
		String id,
		Component label,
		boolean visible,
		boolean allowHide,
		boolean allowCompact,
		Optional<String> reorderGroup
	) {
		public ComponentControl {
			id = boundedId(id, "component id");
			label = Objects.requireNonNull(label, "label").copy();
			reorderGroup = Objects.requireNonNull(reorderGroup, "reorderGroup")
				.map(value -> boundedId(value, "reorderGroup"));
		}
	}
}
