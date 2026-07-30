package io.github.zcpu954861.pixeltzzpro.content;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable, data-pack-owned page and theme definitions.
 *
 * <p>The compiler keeps the typed tree for server-side reference checks and the canonical document
 * for the later on-demand wire protocol. Neither representation contains executable code.
 */
public final class UiDefinitions {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public static final int MAX_CANONICAL_DOCUMENT_BYTES = 262_144;
	public static final Identifier DEFAULT_THEME = PixelTzzPro.id("default");

	private UiDefinitions() {
	}

	public enum NodeType {
		ROW,
		COLUMN,
		GRID,
		FLOW,
		OVERLAY,
		CAROUSEL,
		SCROLL,
		CARD,
		TEXT,
		IMAGE,
		BUTTON,
		DIVIDER,
		SPACER,
		PROGRESS,
		PLAYER_HEAD,
		REPEAT,
		FIELD_INPUT
	}

	public enum SizeMode {
		CONTENT,
		FIXED,
		FILL,
		PERCENT,
		WEIGHT
	}

	public enum AlignSelf {
		AUTO,
		START,
		CENTER,
		END,
		STRETCH
	}

	public enum MainAlign {
		START,
		CENTER,
		END,
		SPACE_BETWEEN
	}

	public enum CrossAlign {
		START,
		CENTER,
		END,
		STRETCH
	}

	public enum Direction {
		HORIZONTAL,
		VERTICAL
	}

	public enum Anchor {
		TOP_LEFT,
		TOP,
		TOP_RIGHT,
		LEFT,
		CENTER,
		RIGHT,
		BOTTOM_LEFT,
		BOTTOM,
		BOTTOM_RIGHT
	}

	public enum ResponsiveTier {
		COMPACT,
		STANDARD,
		WIDE
	}

	public enum UiEvent {
		SHOW,
		HIDE,
		HOVER_ENTER,
		HOVER_LEAVE,
		FOCUS,
		BLUR,
		PRESS,
		VALUE_CHANGE,
		SUCCESS,
		ERROR
	}

	public enum TextOverflow {
		CLIP,
		ELLIPSIS
	}

	public enum TextAlign {
		LEFT,
		CENTER,
		RIGHT
	}

	public enum ImageFit {
		CONTAIN,
		COVER,
		STRETCH
	}

	public enum FieldPresentation {
		AUTO,
		TOGGLE,
		CHOICE_BUTTONS,
		NUMBER,
		STEPPER,
		SLIDER,
		TEXT,
		IDENTIFIER_TEXT,
		CHOICE_CARDS,
		RADIO_LIST,
		DROPDOWN,
		CHECKBOX_LIST
	}

	public enum ActionType {
		LOCAL,
		FLOW,
		REGISTERED,
		HISTORY_DETAIL
	}

	public enum ConditionOperator {
		EQ,
		NOT_EQ,
		GREATER,
		GREATER_OR_EQUAL,
		LESS,
		LESS_OR_EQUAL
	}

	public enum JunctionOperator {
		ALL,
		ANY
	}

	public enum StyleState {
		NORMAL,
		HOVER,
		FOCUSED,
		PRESSED,
		DISABLED,
		SUCCESS,
		WARNING,
		DANGER
	}

	public enum MotionProperty {
		OPACITY,
		TRANSLATE_X,
		TRANSLATE_Y,
		SCALE,
		COLOR,
		CLIP_PROGRESS,
		PROGRESS_VALUE
	}

	public enum Easing {
		LINEAR,
		EASE_IN_CUBIC,
		EASE_OUT_CUBIC,
		EASE_IN_OUT_CUBIC,
		EASE_OUT_QUART
	}

	public enum ReducedMotion {
		FINAL,
		FADE,
		KEEP
	}

	public enum AssetType {
		TEXTURE,
		FONT,
		TRANSLATION,
		SOUND
	}

	public record Breakpoints(int compactMax, int wideMin) {
	}

	public record SizeSpec(SizeMode mode, Optional<Double> value) {
		public SizeSpec {
			value = value == null ? Optional.empty() : value;
		}
	}

	public record Insets(int top, int right, int bottom, int left) {
		public static Insets zero() {
			return new Insets(0, 0, 0, 0);
		}
	}

	/**
	 * One normalized layout object. Fields that are not meaningful for a node/container remain empty.
	 */
	public record LayoutDefinition(
		Optional<SizeSpec> width,
		Optional<SizeSpec> height,
		Optional<Integer> minimumWidth,
		Optional<Integer> maximumWidth,
		Optional<Integer> minimumHeight,
		Optional<Integer> maximumHeight,
		Insets margin,
		Insets padding,
		AlignSelf alignSelf,
		Optional<Integer> gap,
		Optional<Integer> lineGap,
		Optional<Integer> columnGap,
		Optional<Integer> rowGap,
		Optional<MainAlign> mainAlign,
		Optional<CrossAlign> crossAlign,
		Optional<Direction> direction,
		List<SizeSpec> columns,
		Optional<Integer> columnSpan,
		Optional<Anchor> anchor,
		Optional<Integer> offsetX,
		Optional<Integer> offsetY
	) {
		public LayoutDefinition {
			width = optional(width);
			height = optional(height);
			minimumWidth = optional(minimumWidth);
			maximumWidth = optional(maximumWidth);
			minimumHeight = optional(minimumHeight);
			maximumHeight = optional(maximumHeight);
			margin = margin == null ? Insets.zero() : margin;
			padding = padding == null ? Insets.zero() : padding;
			alignSelf = alignSelf == null ? AlignSelf.AUTO : alignSelf;
			gap = optional(gap);
			lineGap = optional(lineGap);
			columnGap = optional(columnGap);
			rowGap = optional(rowGap);
			mainAlign = optional(mainAlign);
			crossAlign = optional(crossAlign);
			direction = optional(direction);
			columns = List.copyOf(columns);
			columnSpan = optional(columnSpan);
			anchor = optional(anchor);
			offsetX = optional(offsetX);
			offsetY = optional(offsetY);
		}

		public static LayoutDefinition empty() {
			return new LayoutDefinition(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Insets.zero(),
				Insets.zero(),
				AlignSelf.AUTO,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				List.of(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public record StyleValue(String canonicalJson) {
	}

	public sealed interface ValueExpression permits BindingValue, LiteralValue {
	}

	public record BindingValue(String path) implements ValueExpression {
	}

	public record LiteralValue(String canonicalJson) implements ValueExpression {
	}

	public sealed interface TextTemplate permits StaticText,
		BoundText,
		DurationTicksText,
		TranslatedText,
		ConcatenatedText {
	}

	public record StaticText(RichText text) implements TextTemplate {
	}

	public record BoundText(BindingValue binding, Optional<TextTemplate> fallback) implements TextTemplate {
		public BoundText {
			fallback = optional(fallback);
		}
	}

	public record DurationTicksText(
		ValueExpression value,
		Optional<TextTemplate> fallback
	) implements TextTemplate {
		public DurationTicksText {
			fallback = optional(fallback);
		}
	}

	public record TranslatedText(
		String key,
		List<ValueExpression> arguments,
		Optional<TextTemplate> fallback
	) implements TextTemplate {
		public TranslatedText {
			arguments = List.copyOf(arguments);
			fallback = optional(fallback);
		}
	}

	public record ConcatenatedText(List<TextTemplate> parts) implements TextTemplate {
		public ConcatenatedText {
			parts = List.copyOf(parts);
		}
	}

	public sealed interface ConditionExpression permits ComparisonCondition,
		JunctionCondition,
		NotCondition,
		ExistsCondition {
	}

	public record ComparisonCondition(
		ConditionOperator operator,
		ValueExpression left,
		ValueExpression right
	) implements ConditionExpression {
	}

	public record JunctionCondition(
		JunctionOperator operator,
		List<ConditionExpression> conditions
	) implements ConditionExpression {
		public JunctionCondition {
			conditions = List.copyOf(conditions);
		}
	}

	public record NotCondition(ConditionExpression condition) implements ConditionExpression {
	}

	public record ExistsCondition(BindingValue binding) implements ConditionExpression {
	}

	public record ResponsiveOverride(
		Optional<NodeType> type,
		Optional<LayoutDefinition> layout,
		Set<String> layoutFields,
		Optional<String> style,
		Map<String, StyleValue> styleOverrides,
		Optional<Boolean> visible
	) {
		public ResponsiveOverride {
			type = optional(type);
			layout = optional(layout);
			layoutFields = Set.copyOf(layoutFields);
			style = optional(style);
			styleOverrides = Map.copyOf(styleOverrides);
			visible = optional(visible);
		}
	}

	public record EventBinding(Optional<String> motion, Optional<String> sound) {
		public EventBinding {
			motion = optional(motion);
			sound = optional(sound);
		}
	}

	public sealed interface NodeContent permits ChildrenContent,
		SingleChildContent,
		TextContent,
		ImageContent,
		ButtonContent,
		DividerContent,
		SpacerContent,
		ProgressContent,
		PlayerHeadContent,
		RepeatContent,
		FieldInputContent {
	}

	public record ChildrenContent(List<NodeDefinition> children) implements NodeContent {
		public ChildrenContent {
			children = List.copyOf(children);
		}
	}

	public record SingleChildContent(
		NodeDefinition child,
		Optional<Direction> scrollDirection,
		boolean showScrollbar
	) implements NodeContent {
		public SingleChildContent {
			scrollDirection = optional(scrollDirection);
		}
	}

	public record TextContent(
		TextTemplate text,
		boolean wrap,
		Optional<Integer> maximumLines,
		TextOverflow overflow,
		TextAlign alignment
	) implements NodeContent {
		public TextContent {
			maximumLines = optional(maximumLines);
		}
	}

	public record ImageContent(
		Identifier asset,
		ImageFit fit,
		boolean required,
		Optional<Identifier> fallback,
		Optional<TextTemplate> alternativeText
	) implements NodeContent {
		public ImageContent {
			fallback = optional(fallback);
			alternativeText = optional(alternativeText);
		}
	}

	public record UiAction(
		ActionType type,
		Optional<String> name,
		Optional<Identifier> registeredAction
	) {
		public UiAction {
			name = optional(name);
			registeredAction = optional(registeredAction);
		}
	}

	public record ButtonContent(
		TextTemplate label,
		Optional<ConditionExpression> enabledWhen,
		Optional<TextTemplate> disabledReason,
		UiAction action
	) implements NodeContent {
		public ButtonContent {
			enabledWhen = optional(enabledWhen);
			disabledReason = optional(disabledReason);
		}
	}

	public record DividerContent(Direction direction, int thickness) implements NodeContent {
	}

	public record SpacerContent() implements NodeContent {
	}

	public record ProgressContent(
		ValueExpression value,
		ValueExpression minimum,
		ValueExpression maximum,
		Optional<TextTemplate> label
	) implements NodeContent {
		public ProgressContent {
			label = optional(label);
		}
	}

	public record PlayerHeadContent(
		ValueExpression uuid,
		ValueExpression name,
		ValueExpression online,
		boolean showStatus
	) implements NodeContent {
	}

	public record RepeatContent(
		ValueExpression items,
		ValueExpression itemKey,
		int maximumItems,
		int estimatedItemHeight,
		NodeDefinition template
	) implements NodeContent {
	}

	public record FieldInputContent(
		Identifier field,
		FieldPresentation presentation,
		boolean showLabel,
		boolean showDescription
	) implements NodeContent {
		public boolean supports(final FieldType type) {
			return switch (type) {
				case BOOLEAN -> this.presentation == FieldPresentation.AUTO
					|| this.presentation == FieldPresentation.TOGGLE
					|| this.presentation == FieldPresentation.CHOICE_BUTTONS;
				case INTEGER -> this.presentation == FieldPresentation.AUTO
					|| this.presentation == FieldPresentation.NUMBER
					|| this.presentation == FieldPresentation.STEPPER
					|| this.presentation == FieldPresentation.SLIDER;
				case STRING -> this.presentation == FieldPresentation.AUTO
					|| this.presentation == FieldPresentation.TEXT;
				case IDENTIFIER -> this.presentation == FieldPresentation.AUTO
					|| this.presentation == FieldPresentation.IDENTIFIER_TEXT;
				case SINGLE_CHOICE, EXCLUSIVE_CHOICE -> this.presentation == FieldPresentation.AUTO
					|| this.presentation == FieldPresentation.CHOICE_CARDS
					|| this.presentation == FieldPresentation.RADIO_LIST
					|| this.presentation == FieldPresentation.DROPDOWN;
				case MULTI_CHOICE -> this.presentation == FieldPresentation.AUTO
					|| this.presentation == FieldPresentation.CHOICE_CARDS
					|| this.presentation == FieldPresentation.CHECKBOX_LIST;
			};
		}
	}

	public record NodeDefinition(
		NodeType type,
		Optional<String> id,
		LayoutDefinition layout,
		Optional<String> style,
		Map<String, StyleValue> styleOverrides,
		Optional<ConditionExpression> visibleWhen,
		Map<ResponsiveTier, ResponsiveOverride> responsive,
		Map<UiEvent, EventBinding> events,
		NodeContent content,
		String pointer
	) {
		public NodeDefinition {
			id = optional(id);
			layout = layout == null ? LayoutDefinition.empty() : layout;
			style = optional(style);
			styleOverrides = Map.copyOf(styleOverrides);
			visibleWhen = optional(visibleWhen);
			responsive = Map.copyOf(responsive);
			events = Map.copyOf(events);
		}
	}

	public record AssetReference(
		AssetType type,
		String id,
		String pointer,
		boolean required,
		Optional<String> fallback
	) {
		public AssetReference {
			fallback = optional(fallback);
		}
	}

	public record PageDefinition(
		Identifier id,
		Identifier game,
		RichText title,
		Identifier theme,
		Breakpoints breakpoints,
		NodeDefinition root,
		List<AssetReference> assets,
		String canonicalDocument,
		String sha256
	) {
		public PageDefinition {
			assets = List.copyOf(assets);
		}
	}

	public record FontToken(
		Identifier asset,
		boolean required,
		Optional<Identifier> fallback
	) {
		public FontToken {
			fallback = optional(fallback);
		}
	}

	public record ThemeTokens(
		Map<String, String> colors,
		Map<String, Integer> spacing,
		Map<String, FontToken> fonts
	) {
		public ThemeTokens {
			colors = Map.copyOf(colors);
			spacing = Map.copyOf(spacing);
			fonts = Map.copyOf(fonts);
		}
	}

	public record StyleDefinition(Map<StyleState, Map<String, StyleValue>> states) {
		public StyleDefinition {
			Map<StyleState, Map<String, StyleValue>> copied = new java.util.EnumMap<>(StyleState.class);
			states.forEach((state, values) -> copied.put(state, Map.copyOf(values)));
			states = Map.copyOf(copied);
		}
	}

	public record MotionKeyframe(double at, StyleValue value) {
	}

	public record MotionTrack(MotionProperty property, List<MotionKeyframe> keyframes) {
		public MotionTrack {
			keyframes = List.copyOf(keyframes);
		}
	}

	public record MotionDefinition(
		int durationMilliseconds,
		int delayMilliseconds,
		Easing easing,
		List<MotionTrack> tracks,
		ReducedMotion reducedMotion
	) {
		public MotionDefinition {
			tracks = List.copyOf(tracks);
		}
	}

	public record SoundCue(
		Identifier sound,
		int delayMilliseconds,
		double volume,
		double pitch,
		boolean required
	) {
	}

	public record ThemeDefinition(
		Identifier id,
		ThemeTokens tokens,
		Map<String, StyleDefinition> styles,
		Map<String, MotionDefinition> motions,
		Map<String, SoundCue> soundCues,
		List<AssetReference> assets,
		String canonicalDocument,
		String sha256
	) {
		public ThemeDefinition {
			styles = Map.copyOf(styles);
			motions = Map.copyOf(motions);
			soundCues = Map.copyOf(soundCues);
			assets = List.copyOf(assets);
		}
	}

	private static <T> Optional<T> optional(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}
}
