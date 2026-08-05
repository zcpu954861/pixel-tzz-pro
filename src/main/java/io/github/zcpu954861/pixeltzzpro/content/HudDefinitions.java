package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.Problem;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable V3C HUD and opening-countdown data-pack definitions.
 *
 * <p>These records are the only representation consumed by the authority and protocol layers.
 * Source selectors, NBT paths and other server-only bindings remain in this catalog and are never
 * sent to a client as executable lookup instructions.
 */
public final class HudDefinitions {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public static final int MAX_COMPONENTS = 1_024;
	public static final int MAX_LAYOUTS = 256;
	public static final int MAX_PROFILES = 128;
	public static final int MAX_COUNTDOWNS = 128;
	public static final int MAX_COMPONENT_DEPTH = 16;
	public static final int MAX_EXPANDED_COMPONENTS = 256;
	public static final int MAX_LAYOUT_DEGRADATION_DEPTH = 8;
	public static final int MAX_CONTAINER_CHILDREN = 32;
	public static final int MAX_OVERLAY_LAYERS = 16;
	public static final int MAX_ROUTES = 64;
	public static final int MAX_CONDITION_DEPTH = 8;
	public static final int MAX_CONDITION_TERMS = 64;
	public static final int MAX_BINDINGS = 32;
	public static final int MAX_REPEAT_ITEMS = 64;
	public static final int MAX_CHECKPOINTS = 64;
	public static final int MAX_CALLBACKS_PER_SLOT = 64;
	public static final long MAX_COUNTDOWN_TICKS = 72_000L;
	public static final int MAX_CANONICAL_DOCUMENT_BYTES = 131_072;

	private HudDefinitions() {
	}

	public enum HudResourceKind {
		COMPONENT,
		LAYOUT,
		PROFILE,
		COUNTDOWN
	}

	public record HudResourceKey(HudResourceKind kind, Identifier id) {
		public HudResourceKey {
			kind = Objects.requireNonNull(kind, "kind");
			id = Objects.requireNonNull(id, "id");
		}
	}

	public enum ComponentType {
		TEXT,
		IMAGE,
		PLAYER_HEAD,
		COUNTER,
		BADGE,
		PROGRESS,
		TIMER,
		SEPARATOR,
		BACKGROUND,
		ROW,
		COLUMN,
		OVERLAY,
		REPEAT
	}

	public enum ComponentPriority {
		CRITICAL,
		PRIMARY,
		SECONDARY,
		DECORATIVE
	}

	public enum Surface {
		DOCK,
		COUNTDOWN
	}

	public enum RouteTier {
		TASK,
		CONTEXT
	}

	public enum AudienceSource {
		ALL,
		CURRENT_HOST,
		CALL_TARGETS,
		INVOKER,
		FROZEN_PARTICIPANTS
	}

	/** One OR branch of a bounded audience union. Filters inside a branch are ANDed. */
	public record HudAudienceSelector(
		AudienceSource source,
		boolean participants,
		Set<Identifier> roles,
		Set<Identifier> teams,
		Set<Identifier> lifeStates,
		Set<Identifier> roleTags,
		Set<Identifier> teamTags,
		Set<Identifier> lifeStateTags,
		boolean excludeHost,
		boolean onlineOnly
	) {
		public HudAudienceSelector {
			source = Objects.requireNonNull(source, "source");
			roles = Set.copyOf(roles);
			teams = Set.copyOf(teams);
			lifeStates = Set.copyOf(lifeStates);
			roleTags = Set.copyOf(roleTags);
			teamTags = Set.copyOf(teamTags);
			lifeStateTags = Set.copyOf(lifeStateTags);
		}
	}

	public record HudAudience(List<HudAudienceSelector> selectors) {
		public HudAudience {
			selectors = List.copyOf(selectors);
			if (selectors.isEmpty()) {
				throw new IllegalArgumentException("HUD audience must contain at least one selector");
			}
		}

		public static HudAudience allOnline() {
			return new HudAudience(List.of(new HudAudienceSelector(
				AudienceSource.ALL,
				false,
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				false,
				true
			)));
		}

		public static HudAudience frozenParticipants() {
			return new HudAudience(List.of(new HudAudienceSelector(
				AudienceSource.FROZEN_PARTICIPANTS,
				true,
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				false,
				true
			)));
		}
	}

	public enum TaskKind {
		WARMUP,
		MAIN
	}

	public enum TaskStatus {
		STARTING,
		RUNNING,
		PAUSED,
		SETTLING,
		INTERVAL,
		COMPLETED,
		INTERRUPTED
	}

	/** Bounded, side-effect-free route/visibility expression. */
	public record HudCondition(
		Set<Identifier> games,
		Set<Identifier> phases,
		Set<Identifier> tasks,
		Set<TaskKind> taskKinds,
		Set<TaskStatus> taskStatuses,
		Set<Identifier> roles,
		Set<Identifier> teams,
		Set<Identifier> lifeStates,
		Optional<Boolean> host,
		Optional<Boolean> online,
		List<HudCondition> all,
		List<HudCondition> any,
		Optional<HudCondition> not
	) {
		public HudCondition {
			games = Set.copyOf(games);
			phases = Set.copyOf(phases);
			tasks = Set.copyOf(tasks);
			taskKinds = Set.copyOf(taskKinds);
			taskStatuses = Set.copyOf(taskStatuses);
			roles = Set.copyOf(roles);
			teams = Set.copyOf(teams);
			lifeStates = Set.copyOf(lifeStates);
			host = optional(host);
			online = optional(online);
			all = List.copyOf(all);
			any = List.copyOf(any);
			not = optional(not);
		}

		public static HudCondition always() {
			return new HudCondition(
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
				Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty()
			);
		}

		public boolean hasTaskConstraint() {
			return !this.tasks.isEmpty() || !this.taskKinds.isEmpty() || !this.taskStatuses.isEmpty();
		}

		public boolean simpleConjunction() {
			return this.all.isEmpty() && this.any.isEmpty() && this.not.isEmpty();
		}
	}

	public enum HudValueType {
		STRING,
		INTEGER,
		DECIMAL,
		BOOLEAN,
		IDENTIFIER,
		PLAYER,
		COMPONENT,
		DURATION,
		CLOCK,
		LIST,
		OBJECT
	}

	public enum HudBindingSourceType {
		GAME_FACT,
		PHASE_FACT,
		TASK_FACT,
		PLAYER_FACT,
		EVENT,
		STATISTIC,
		RESULT,
		CLOCK,
		PLAYER_DATA,
		SCORE,
		STORAGE,
		ENTITY_DATA
	}

	/**
	 * Normalized server-only source descriptor. Only fields meaningful to {@link #type()} are
	 * populated; the parser rejects unknown or contradictory combinations.
	 */
	public record HudBindingSource(
		HudBindingSourceType type,
		Optional<String> value,
		Optional<Identifier> id,
		Optional<Identifier> field,
		Optional<String> objective,
		Optional<String> holder,
		Optional<Identifier> storage,
		Optional<String> path,
		Optional<String> target
	) {
		public HudBindingSource {
			type = Objects.requireNonNull(type, "type");
			value = optional(value);
			id = optional(id);
			field = optional(field);
			objective = optional(objective);
			holder = optional(holder);
			storage = optional(storage);
			path = optional(path);
			target = optional(target);
		}
	}

	public enum MissingMode {
		HIDE_FIELD,
		HIDE_COMPONENT,
		PLACEHOLDER,
		DEGRADATION_LAYOUT,
		RETAIN_STALE
	}

	public record MissingPolicy(
		MissingMode mode,
		Optional<String> placeholderCanonicalJson,
		Optional<Identifier> degradationLayout,
		Optional<Long> staleTtlTicks,
		Optional<RichText> staleIndicator
	) {
		public MissingPolicy {
			mode = Objects.requireNonNull(mode, "mode");
			placeholderCanonicalJson = optional(placeholderCanonicalJson);
			degradationLayout = optional(degradationLayout);
			staleTtlTicks = optional(staleTtlTicks);
			staleIndicator = optional(staleIndicator);
		}
	}

	public record HudBinding(
		String id,
		HudValueType valueType,
		Optional<HudValueType> itemType,
		HudBindingSource source,
		Optional<HudAudience> audience,
		boolean sensitive,
		boolean critical,
		MissingPolicy onMissing
	) {
		public HudBinding {
			id = Objects.requireNonNull(id, "id");
			valueType = Objects.requireNonNull(valueType, "valueType");
			itemType = optional(itemType);
			source = Objects.requireNonNull(source, "source");
			audience = optional(audience);
			onMissing = Objects.requireNonNull(onMissing, "onMissing");
		}
	}

	public enum VisibilityControl {
		LOCKED_SHOWN,
		ALLOW_HIDE
	}

	public enum CompactControl {
		LOCKED,
		ALLOW
	}

	public record ClientControl(
		VisibilityControl visibility,
		CompactControl compact,
		Optional<String> reorderGroup
	) {
		public ClientControl {
			visibility = Objects.requireNonNull(visibility, "visibility");
			compact = Objects.requireNonNull(compact, "compact");
			reorderGroup = optional(reorderGroup);
		}

		public static ClientControl locked() {
			return new ClientControl(
				VisibilityControl.LOCKED_SHOWN,
				CompactControl.LOCKED,
				Optional.empty()
			);
		}
	}

	public enum FontWeight {
		NORMAL,
		BOLD
	}

	public record Insets(int left, int top, int right, int bottom) {
		public static Insets zero() {
			return new Insets(0, 0, 0, 0);
		}
	}

	public record HudStyle(
		Optional<String> textColor,
		Optional<String> secondaryTextColor,
		Optional<String> backgroundColor,
		Optional<String> borderColor,
		Optional<String> trackColor,
		Optional<String> fillColor,
		Optional<String> markerColor,
		double opacity,
		FontWeight fontWeight,
		Insets padding,
		int gap,
		Optional<Integer> width,
		Optional<Integer> height
	) {
		public HudStyle {
			textColor = optional(textColor);
			secondaryTextColor = optional(secondaryTextColor);
			backgroundColor = optional(backgroundColor);
			borderColor = optional(borderColor);
			trackColor = optional(trackColor);
			fillColor = optional(fillColor);
			markerColor = optional(markerColor);
			if (!Double.isFinite(opacity)) {
				throw new IllegalArgumentException("HUD opacity must be finite");
			}
			fontWeight = Objects.requireNonNull(fontWeight, "fontWeight");
			padding = Objects.requireNonNull(padding, "padding");
			width = optional(width);
			height = optional(height);
		}

		public static HudStyle standard() {
			return new HudStyle(
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), 1.0D, FontWeight.NORMAL,
				Insets.zero(), 0, Optional.empty(), Optional.empty()
			);
		}
	}

	public record HudTextTemplate(String canonicalJson, Set<String> referencedBindings) {
		public HudTextTemplate {
			canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
			referencedBindings = Set.copyOf(referencedBindings);
		}
	}

	public enum TextAlignment {
		LEFT,
		CENTER,
		RIGHT
	}

	public enum TextOverflow {
		WRAP,
		ELLIPSIS,
		SCALE_ONCE
	}

	public record TextLayout(TextAlignment alignment, int maxLines, TextOverflow overflow) {
		public TextLayout {
			alignment = Objects.requireNonNull(alignment, "alignment");
			overflow = Objects.requireNonNull(overflow, "overflow");
		}
	}

	public sealed interface ComponentBody permits
		TextBody,
		ImageBody,
		PlayerHeadBody,
		CounterBody,
		BadgeBody,
		ProgressBody,
		TimerBody,
		SeparatorBody,
		BackgroundBody,
		ContainerBody,
		OverlayBody,
		RepeatBody {
		default Set<Identifier> componentReferences() {
			return Set.of();
		}
	}

	public record TextBody(HudTextTemplate text, TextLayout layout) implements ComponentBody {
		public TextBody {
			text = Objects.requireNonNull(text, "text");
			layout = Objects.requireNonNull(layout, "layout");
		}
	}

	public record IntSize(int width, int height) {
	}

	public enum ImageFit {
		CONTAIN,
		COVER,
		STRETCH
	}

	public enum AssetMissingMode {
		HIDE_COMPONENT,
		PLACEHOLDER,
		DEGRADATION_LAYOUT
	}

	public record AssetMissingPolicy(
		AssetMissingMode mode,
		Optional<Identifier> placeholderTexture,
		Optional<Identifier> degradationLayout
	) {
		public AssetMissingPolicy {
			mode = Objects.requireNonNull(mode, "mode");
			placeholderTexture = optional(placeholderTexture);
			degradationLayout = optional(degradationLayout);
		}
	}

	public record ImageBody(
		Identifier texture,
		IntSize size,
		String tint,
		ImageFit fit,
		RichText alt,
		AssetMissingPolicy onAssetMissing
	) implements ComponentBody {
		public ImageBody {
			texture = Objects.requireNonNull(texture, "texture");
			size = Objects.requireNonNull(size, "size");
			tint = Objects.requireNonNull(tint, "tint");
			fit = Objects.requireNonNull(fit, "fit");
			alt = Objects.requireNonNull(alt, "alt");
			onAssetMissing = Objects.requireNonNull(onAssetMissing, "onAssetMissing");
		}
	}

	public enum HeadLayer {
		BASE,
		HAT
	}

	public enum OfflineHeadStyle {
		NORMAL,
		GRAYSCALE
	}

	public record FieldReference(String field) {
		public FieldReference {
			field = Objects.requireNonNull(field, "field");
		}
	}

	public record PlayerHeadBody(
		FieldReference player,
		int size,
		Set<HeadLayer> layers,
		OfflineHeadStyle offlineStyle
	) implements ComponentBody {
		public PlayerHeadBody {
			player = Objects.requireNonNull(player, "player");
			layers = Set.copyOf(layers);
			offlineStyle = Objects.requireNonNull(offlineStyle, "offlineStyle");
		}
	}

	public record CounterBody(
		RichText label,
		FieldReference value,
		Optional<RichText> suffix
	) implements ComponentBody {
		public CounterBody {
			label = Objects.requireNonNull(label, "label");
			value = Objects.requireNonNull(value, "value");
			suffix = optional(suffix);
		}
	}

	public record BadgeVariant(RichText label, String color) {
		public BadgeVariant {
			label = Objects.requireNonNull(label, "label");
			color = Objects.requireNonNull(color, "color");
		}
	}

	public enum UnknownBadgeMode {
		HIDE_COMPONENT
	}

	public record BadgeBody(
		FieldReference value,
		Map<Identifier, BadgeVariant> variants,
		UnknownBadgeMode unknown
	) implements ComponentBody {
		public BadgeBody {
			value = Objects.requireNonNull(value, "value");
			variants = Map.copyOf(variants);
			unknown = Objects.requireNonNull(unknown, "unknown");
		}
	}

	public enum Orientation {
		HORIZONTAL,
		VERTICAL
	}

	public enum ProgressLabel {
		NONE,
		CURRENT,
		PERCENT,
		CURRENT_OVER_MAX
	}

	public enum ProgressInterpolation {
		NONE,
		SMOOTH
	}

	public record ProgressBody(
		FieldReference current,
		FieldReference maximum,
		Orientation orientation,
		int segments,
		ProgressLabel label,
		ProgressInterpolation interpolation
	) implements ComponentBody {
		public ProgressBody {
			current = Objects.requireNonNull(current, "current");
			maximum = Objects.requireNonNull(maximum, "maximum");
			orientation = Objects.requireNonNull(orientation, "orientation");
			label = Objects.requireNonNull(label, "label");
			interpolation = Objects.requireNonNull(interpolation, "interpolation");
		}
	}

	public enum TimerDirection {
		ELAPSED,
		REMAINING
	}

	public enum TimerFormat {
		M_SS,
		H_MM_SS,
		LOCALIZED_DURATION
	}

	public record TimerBody(
		FieldReference clock,
		TimerDirection direction,
		TimerFormat format,
		Optional<RichText> pausedMarker
	) implements ComponentBody {
		public TimerBody {
			clock = Objects.requireNonNull(clock, "clock");
			direction = Objects.requireNonNull(direction, "direction");
			format = Objects.requireNonNull(format, "format");
			pausedMarker = optional(pausedMarker);
		}
	}

	public record SeparatorBody(Orientation orientation, int thickness, String color)
		implements ComponentBody {
		public SeparatorBody {
			orientation = Objects.requireNonNull(orientation, "orientation");
			color = Objects.requireNonNull(color, "color");
		}
	}

	public record Border(String color, int width) {
		public Border {
			color = Objects.requireNonNull(color, "color");
		}
	}

	public record BackgroundBody(
		Identifier child,
		String fill,
		Border border,
		Insets padding
	) implements ComponentBody {
		public BackgroundBody {
			child = Objects.requireNonNull(child, "child");
			fill = Objects.requireNonNull(fill, "fill");
			border = Objects.requireNonNull(border, "border");
			padding = Objects.requireNonNull(padding, "padding");
		}

		@Override
		public Set<Identifier> componentReferences() {
			return Set.of(this.child);
		}
	}

	public enum ContainerAlignment {
		START,
		CENTER,
		END,
		STRETCH
	}

	public record ContainerBody(
		List<Identifier> children,
		int gap,
		ContainerAlignment alignment,
		List<Double> weights
	) implements ComponentBody {
		public ContainerBody {
			children = List.copyOf(children);
			alignment = Objects.requireNonNull(alignment, "alignment");
			weights = List.copyOf(weights);
		}

		@Override
		public Set<Identifier> componentReferences() {
			return Set.copyOf(this.children);
		}
	}

	public enum OverlayAlignment {
		START,
		CENTER,
		END,
		STRETCH
	}

	public record OverlayBody(List<Identifier> layers, OverlayAlignment alignment)
		implements ComponentBody {
		public OverlayBody {
			layers = List.copyOf(layers);
			alignment = Objects.requireNonNull(alignment, "alignment");
		}

		@Override
		public Set<Identifier> componentReferences() {
			return Set.copyOf(this.layers);
		}
	}

	public enum RepeatLayout {
		ROW,
		COLUMN
	}

	public enum RepeatOverflowMode {
		HIDE,
		COMPONENT
	}

	public record RepeatBody(
		FieldReference items,
		Identifier itemComponent,
		RepeatLayout layout,
		int maximumItems,
		RepeatOverflowMode overflowMode,
		Optional<Identifier> overflowComponent
	) implements ComponentBody {
		public RepeatBody {
			items = Objects.requireNonNull(items, "items");
			itemComponent = Objects.requireNonNull(itemComponent, "itemComponent");
			layout = Objects.requireNonNull(layout, "layout");
			overflowMode = Objects.requireNonNull(overflowMode, "overflowMode");
			overflowComponent = optional(overflowComponent);
		}

		@Override
		public Set<Identifier> componentReferences() {
			if (this.overflowComponent.isPresent()) {
				return Set.of(this.itemComponent, this.overflowComponent.orElseThrow());
			}
			return Set.of(this.itemComponent);
		}
	}

	public record HudComponentDefinition(
		Identifier id,
		ComponentType type,
		ComponentPriority priority,
		Optional<HudAudience> audience,
		HudCondition visibleWhen,
		Map<String, HudBinding> bindings,
		HudStyle style,
		ClientControl clientControl,
		ComponentBody body,
		String canonicalDocument,
		String sha256
	) {
		public HudComponentDefinition {
			id = Objects.requireNonNull(id, "id");
			type = Objects.requireNonNull(type, "type");
			priority = Objects.requireNonNull(priority, "priority");
			audience = optional(audience);
			visibleWhen = Objects.requireNonNull(visibleWhen, "visibleWhen");
			bindings = immutableMap(bindings);
			style = Objects.requireNonNull(style, "style");
			clientControl = Objects.requireNonNull(clientControl, "clientControl");
			body = Objects.requireNonNull(body, "body");
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
			sha256 = Objects.requireNonNull(sha256, "sha256");
		}
	}

	public enum Growth {
		UP,
		CENTER
	}

	public record LayoutSize(
		int minimumWidth,
		int preferredWidth,
		int maximumWidth,
		int maximumHeight,
		Growth growth
	) {
		public LayoutSize {
			growth = Objects.requireNonNull(growth, "growth");
		}
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

	/** A bounded local UI sound; it never carries callback or server-side behavior. */
	public record HudSound(Identifier event, double volume, double pitch) {
		public HudSound {
			event = Objects.requireNonNull(event, "event");
			if (
				!Double.isFinite(volume)
					|| !Double.isFinite(pitch)
					|| volume < 0.0D
					|| volume > 4.0D
					|| pitch < 0.5D
					|| pitch > 2.0D
			) {
				throw new IllegalArgumentException("HUD sound values are outside safe bounds");
			}
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
			enterSound = optional(enterSound);
			changeSound = optional(changeSound);
		}

		public static LayoutTransition none() {
			return new LayoutTransition(
				EnterTransition.NONE,
				ChangeTransition.NONE,
				ExitTransition.NONE,
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public record HudLayoutDefinition(
		Identifier id,
		Surface surface,
		Optional<HudAudience> audience,
		Identifier root,
		Optional<Identifier> compactRoot,
		Optional<Identifier> summaryRoot,
		Optional<Identifier> degradationLayout,
		LayoutSize size,
		LayoutTransition transition,
		String canonicalDocument,
		String sha256
	) {
		public HudLayoutDefinition {
			id = Objects.requireNonNull(id, "id");
			surface = Objects.requireNonNull(surface, "surface");
			audience = optional(audience);
			root = Objects.requireNonNull(root, "root");
			compactRoot = optional(compactRoot);
			summaryRoot = optional(summaryRoot);
			degradationLayout = optional(degradationLayout);
			size = Objects.requireNonNull(size, "size");
			transition = Objects.requireNonNull(transition, "transition");
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
			sha256 = Objects.requireNonNull(sha256, "sha256");
		}
	}

	public record HudRoute(
		String id,
		RouteTier tier,
		int priority,
		HudCondition when,
		Identifier layout,
		Optional<HudAudience> audience
	) {
		public HudRoute {
			id = Objects.requireNonNull(id, "id");
			tier = Objects.requireNonNull(tier, "tier");
			when = Objects.requireNonNull(when, "when");
			layout = Objects.requireNonNull(layout, "layout");
			audience = optional(audience);
		}
	}

	public enum Anchor {
		BOTTOM_RIGHT,
		BOTTOM_LEFT,
		TOP_RIGHT,
		TOP_LEFT
	}

	public record OffsetPolicy(int maxX, int maxY) {
	}

	public record RangePolicy(double minimum, double defaultValue, double maximum) {
		public RangePolicy {
			if (!Double.isFinite(minimum) || !Double.isFinite(defaultValue) || !Double.isFinite(maximum)) {
				throw new IllegalArgumentException("HUD client range must be finite");
			}
		}
	}

	public enum TabCollision {
		DIM,
		HIDE,
		KEEP
	}

	public record HudClientPolicy(
		boolean allowHide,
		Anchor defaultAnchor,
		Set<Anchor> allowedAnchors,
		OffsetPolicy offset,
		RangePolicy scale,
		RangePolicy opacity,
		boolean allowComponentManagement,
		TabCollision tabCollision
	) {
		public HudClientPolicy {
			defaultAnchor = Objects.requireNonNull(defaultAnchor, "defaultAnchor");
			allowedAnchors = Set.copyOf(allowedAnchors);
			offset = Objects.requireNonNull(offset, "offset");
			scale = Objects.requireNonNull(scale, "scale");
			opacity = Objects.requireNonNull(opacity, "opacity");
			tabCollision = Objects.requireNonNull(tabCollision, "tabCollision");
		}

		public static HudClientPolicy safeDefault() {
			return new HudClientPolicy(
				false,
				Anchor.BOTTOM_RIGHT,
				Set.of(Anchor.BOTTOM_RIGHT),
				new OffsetPolicy(0, 0),
				new RangePolicy(1.0D, 1.0D, 1.0D),
				new RangePolicy(1.0D, 1.0D, 1.0D),
				false,
				TabCollision.DIM
			);
		}
	}

	public record HudProfileDefinition(
		Identifier id,
		Optional<Identifier> defaultLayout,
		List<HudRoute> routes,
		HudClientPolicy clientPolicy,
		String canonicalDocument,
		String sha256
	) {
		public HudProfileDefinition {
			id = Objects.requireNonNull(id, "id");
			defaultLayout = optional(defaultLayout);
			routes = List.copyOf(routes);
			clientPolicy = Objects.requireNonNull(clientPolicy, "clientPolicy");
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
			sha256 = Objects.requireNonNull(sha256, "sha256");
		}
	}

	public enum DisconnectAction {
		PAUSE,
		CANCEL,
		CONTINUE
	}

	public record DisconnectPolicy(
		DisconnectAction requiredPlayer,
		DisconnectAction host
	) {
		public DisconnectPolicy {
			requiredPlayer = Objects.requireNonNull(requiredPlayer, "requiredPlayer");
			host = Objects.requireNonNull(host, "host");
		}
	}

	public enum FreezeMode {
		FREEZE,
		ALLOW
	}

	public enum BlockMode {
		BLOCK,
		ALLOW
	}

	public enum DamageMode {
		IMMUNE,
		NORMAL
	}

	public enum AllowOnly {
		ALLOW
	}

	public record ItemRestrictions(
		BlockMode use,
		BlockMode drop,
		BlockMode swap,
		BlockMode inventory
	) {
		public ItemRestrictions {
			use = Objects.requireNonNull(use, "use");
			drop = Objects.requireNonNull(drop, "drop");
			swap = Objects.requireNonNull(swap, "swap");
			inventory = Objects.requireNonNull(inventory, "inventory");
		}
	}

	public record CountdownRestrictions(
		FreezeMode movement,
		BlockMode attack,
		BlockMode interact,
		ItemRestrictions items,
		DamageMode damage,
		AllowOnly camera,
		AllowOnly chat,
		AllowOnly escape
	) {
		public CountdownRestrictions {
			movement = Objects.requireNonNull(movement, "movement");
			attack = Objects.requireNonNull(attack, "attack");
			interact = Objects.requireNonNull(interact, "interact");
			items = Objects.requireNonNull(items, "items");
			damage = Objects.requireNonNull(damage, "damage");
			camera = Objects.requireNonNull(camera, "camera");
			chat = Objects.requireNonNull(chat, "chat");
			escape = Objects.requireNonNull(escape, "escape");
		}

		public static CountdownRestrictions safeDefault() {
			return new CountdownRestrictions(
				FreezeMode.FREEZE,
				BlockMode.BLOCK,
				BlockMode.BLOCK,
				new ItemRestrictions(BlockMode.BLOCK, BlockMode.BLOCK, BlockMode.BLOCK, BlockMode.BLOCK),
				DamageMode.IMMUNE,
				AllowOnly.ALLOW,
				AllowOnly.ALLOW,
				AllowOnly.ALLOW
			);
		}
	}

	public record CountdownSound(Identifier event, double volume, double pitch) {
		public CountdownSound {
			event = Objects.requireNonNull(event, "event");
			if (!Double.isFinite(volume) || !Double.isFinite(pitch)) {
				throw new IllegalArgumentException("countdown sound values must be finite");
			}
		}
	}

	public record CountdownCheckpoint(
		String id,
		long remainingTicks,
		Optional<CountdownSound> sound,
		Optional<Identifier> cue
	) {
		public CountdownCheckpoint {
			id = Objects.requireNonNull(id, "id");
			sound = optional(sound);
			cue = optional(cue);
		}
	}

	public enum CountdownCallbackSlot {
		START,
		PAUSE,
		RESUME,
		CANCEL,
		COMPLETE
	}

	public enum CountdownCallbackScope {
		GLOBAL,
		EACH_PARTICIPANT
	}

	public record CountdownCallback(
		String id,
		CountdownCallbackScope scope,
		Identifier function,
		boolean required
	) {
		public CountdownCallback {
			id = Objects.requireNonNull(id, "id");
			scope = Objects.requireNonNull(scope, "scope");
			function = Objects.requireNonNull(function, "function");
		}
	}

	public record CountdownDefinition(
		Identifier id,
		RichText name,
		long durationTicks,
		Identifier layout,
		HudAudience displayAudience,
		DisconnectPolicy disconnect,
		CountdownRestrictions restrictions,
		List<CountdownCheckpoint> checkpoints,
		Map<CountdownCallbackSlot, List<CountdownCallback>> callbacks,
		String canonicalDocument,
		String sha256
	) {
		public CountdownDefinition {
			id = Objects.requireNonNull(id, "id");
			name = Objects.requireNonNull(name, "name");
			layout = Objects.requireNonNull(layout, "layout");
			displayAudience = Objects.requireNonNull(displayAudience, "displayAudience");
			disconnect = Objects.requireNonNull(disconnect, "disconnect");
			restrictions = Objects.requireNonNull(restrictions, "restrictions");
			checkpoints = List.copyOf(checkpoints);
			Map<CountdownCallbackSlot, List<CountdownCallback>> copy = new LinkedHashMap<>();
			callbacks.forEach((slot, values) -> copy.put(slot, List.copyOf(values)));
			callbacks = Map.copyOf(copy);
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
			sha256 = Objects.requireNonNull(sha256, "sha256");
		}
	}

	public record DisabledHudDefinition(
		HudResourceKey key,
		Optional<SourceDocument> sourceDocument,
		List<Problem> diagnostics,
		int totalDiagnosticCount
	) {
		public DisabledHudDefinition {
			key = Objects.requireNonNull(key, "key");
			sourceDocument = optional(sourceDocument);
			diagnostics = List.copyOf(diagnostics);
			if (totalDiagnosticCount < diagnostics.size()) {
				throw new IllegalArgumentException("total HUD diagnostic count is too small");
			}
		}
	}

	public record RequiredCountdownFailure(
		Identifier game,
		Identifier countdown,
		List<Problem> diagnostics
	) {
		public RequiredCountdownFailure {
			game = Objects.requireNonNull(game, "game");
			countdown = Objects.requireNonNull(countdown, "countdown");
			diagnostics = List.copyOf(diagnostics);
		}
	}

	/** Isolated V3C catalog published as part of one immutable definition generation. */
	public record HudCatalog(
		Map<Identifier, HudComponentDefinition> components,
		Map<Identifier, HudLayoutDefinition> layouts,
		Map<Identifier, HudProfileDefinition> profiles,
		Map<Identifier, CountdownDefinition> countdowns,
		Map<HudResourceKey, DisabledHudDefinition> disabled,
		Map<Identifier, List<Problem>> gameDiagnostics,
		Map<Identifier, RequiredCountdownFailure> requiredCountdownFailures
	) {
		public HudCatalog {
			components = Map.copyOf(components);
			layouts = Map.copyOf(layouts);
			profiles = Map.copyOf(profiles);
			countdowns = Map.copyOf(countdowns);
			disabled = Map.copyOf(disabled);
			Map<Identifier, List<Problem>> diagnostics = new LinkedHashMap<>();
			gameDiagnostics.forEach((game, values) -> diagnostics.put(game, List.copyOf(values)));
			gameDiagnostics = Map.copyOf(diagnostics);
			requiredCountdownFailures = Map.copyOf(requiredCountdownFailures);
		}

		public static HudCatalog empty() {
			return new HudCatalog(
				Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
			);
		}

		public int definitionCount() {
			return this.components.size() + this.layouts.size() + this.profiles.size()
				+ this.countdowns.size();
		}

		public boolean profileUsable(final Identifier id) {
			return this.profiles.containsKey(id);
		}

		public boolean countdownUsable(final Identifier id) {
			return this.countdowns.containsKey(id);
		}

		public boolean gameStartBlocked(final Identifier game) {
			return this.requiredCountdownFailures.containsKey(game);
		}
	}

	private static <T> Optional<T> optional(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}

	private static <K, V> Map<K, V> immutableMap(final Map<K, V> values) {
		return Map.copyOf(new LinkedHashMap<>(values));
	}
}
