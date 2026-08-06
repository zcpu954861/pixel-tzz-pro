package io.github.zcpu954861.pixeltzzpro.client.countdown;

import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownPreferences.Motion;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.DigitPresentation;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.DigitTransition;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.Easing;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.ProgressMode;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.RollDirection;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.State;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.SurfaceMotion;
import io.github.zcpu954861.pixeltzzpro.client.countdown.ClientCountdownSnapshot.SurfaceTransition;
import io.github.zcpu954861.pixeltzzpro.client.countdown.CountdownTimeFormatter.Formatted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Stateful mechanical-reel renderer shared by the live overlay and settings preview. */
public final class CountdownRenderer {
	private static final int SAFE_MARGIN = 8;
	private static final int TITLE_GAP = 6;
	private static final int SECTION_GAP = 5;
	private static final int STATUS_GAP = 5;
	private static final int MAX_TITLE_WIDTH = 144;
	private static final int HIGH_CONTRAST_FOREGROUND = 0xFFF4F7FA;
	private static final int HIGH_CONTRAST_ACCENT = 0xFF42D9F5;
	private static final int HIGH_CONTRAST_TRACK = 0xFF526474;
	private static final Pattern GRAPHEME_PATTERN = Pattern.compile("\\X");

	private UUID instanceId;
	private long settleGeneration = Long.MIN_VALUE;
	private List<TimeSlot> settledSlots = List.of();
	private List<SlotAnimation> animations = List.of();
	private long lastPrepareNanos = Long.MIN_VALUE;

	public void reset() {
		this.instanceId = null;
		this.settleGeneration = Long.MIN_VALUE;
		this.settledSlots = List.of();
		this.animations = List.of();
		this.lastPrepareNanos = Long.MIN_VALUE;
	}

	private void render(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Frame frame,
		final ClientCountdownPreferences.Snapshot preferences,
		final int viewportLeft,
		final int viewportRight,
		final int panelBottom,
		final long nowNanos
	) {
		Objects.requireNonNull(graphics, "graphics");
		Objects.requireNonNull(font, "font");
		Objects.requireNonNull(frame, "frame");
		Objects.requireNonNull(preferences, "preferences");
		ClientCountdownSnapshot snapshot = frame.snapshot();
		if (viewportRight - viewportLeft < 24 || panelBottom < 8) {
			return;
		}

		double surfaceAlpha = surfaceAlpha(frame, preferences.motion());
		if (surfaceAlpha <= 0.001D) {
			return;
		}
		int surfaceOffsetY = surfaceOffsetY(frame, preferences.motion());
		var panel = snapshot.presentation().panel();
		var time = snapshot.presentation().time();
		Formatted formatted = CountdownTimeFormatter.format(
			time,
			snapshot.totalTicks(),
			frame.remainingTicks()
		);
		boolean showTimer = switch (snapshot.state()) {
			case WAITING, COMPLETING, COMPLETED, CANCELED -> false;
			default -> true;
		};
		Component primary = switch (snapshot.state()) {
			case WAITING -> snapshot.text().waiting();
			case COMPLETING, COMPLETED -> snapshot.text().complete();
			default -> Component.empty();
		};
		Optional<Component> status = status(snapshot);
		List<TimeSlot> timerSlots = showTimer ? timeSlots(formatted) : List.of();

		float configuredTimeScale = (float)time.scale();
		float statusScale = snapshot.checkpoint()
			.map(checkpoint -> (float)checkpoint.scale())
			.orElse(1.0F);
		int timeLocalWidth = showTimer ? localTimeWidth(font, timerSlots) : 0;
		int configuredTimerWidth = showTimer
			? scaledTimeWidth(timeLocalWidth, configuredTimeScale)
			: 0;
		int prefixNaturalWidth = showTimer
			? snapshot.text().prefix().map(font::width).orElse(0)
			: 0;
		int suffixNaturalWidth = showTimer
			? snapshot.text().suffix().map(font::width).orElse(0)
			: 0;
		int primaryNaturalWidth = showTimer ? 0 : font.width(primary);
		int statusNaturalWidth = status
			.map(value -> scaledComponentWidth(font, value, statusScale))
			.orElse(0);
		int naturalTimeBlockWidth = showTimer
			? prefixNaturalWidth + configuredTimerWidth + suffixNaturalWidth
				+ (prefixNaturalWidth > 0 ? 3 : 0) + (suffixNaturalWidth > 0 ? 3 : 0)
			: primaryNaturalWidth;
		if (statusNaturalWidth > 0) {
			naturalTimeBlockWidth += STATUS_GAP + statusNaturalWidth;
		}

		int availableWidth = Math.max(24, viewportRight - viewportLeft - SAFE_MARGIN * 2);
		int maximumPanelWidth = Math.min(panel.maxWidth(), availableWidth);
		int titleNaturalWidth = Math.min(MAX_TITLE_WIDTH, font.width(snapshot.text().title()));
		int desiredContentWidth = naturalTimeBlockWidth;
		if (titleNaturalWidth > 0) {
			desiredContentWidth += titleNaturalWidth + TITLE_GAP + 1 + SECTION_GAP;
		}
		int minimumPanelWidth = Math.min(48, maximumPanelWidth);
		int panelWidth = Math.min(
			maximumPanelWidth,
			Math.max(minimumPanelWidth, desiredContentWidth + panel.paddingX() * 2 + 3)
		);
		int horizontalPadding = Math.min(
			panel.paddingX(),
			Math.max(0, (panelWidth - 4) / 2)
		);
		int contentWidth = Math.max(1, panelWidth - horizontalPadding * 2 - 3);
		int titleWidth = Math.max(
			0,
			contentWidth - naturalTimeBlockWidth - TITLE_GAP - SECTION_GAP - 1
		);
		if (titleWidth < 18) {
			titleWidth = 0;
		}
		titleWidth = Math.min(titleNaturalWidth, titleWidth);
		int timeBlockAvailable = contentWidth
			- (titleWidth > 0 ? titleWidth + TITLE_GAP + SECTION_GAP + 1 : 0);
		float timeScale = showTimer
			? fittedTimeScale(configuredTimeScale, timeLocalWidth, Math.max(1, timeBlockAvailable))
			: configuredTimeScale;
		int timerWidth = showTimer ? scaledTimeWidth(timeLocalWidth, timeScale) : 0;
		VisibleCopy visible = visibleCopy(
			font,
			showTimer,
			primary,
			snapshot.text().prefix(),
			snapshot.text().suffix(),
			status,
			statusScale,
			timerWidth,
			Math.max(1, timeBlockAvailable)
		);
		Component visiblePrimary = visible.primary();
		Optional<Component> visiblePrefix = visible.prefix();
		Optional<Component> visibleSuffix = visible.suffix();
		Optional<Component> visibleStatus = visible.status();
		int prefixWidth = visiblePrefix.map(font::width).orElse(0);
		int suffixWidth = visibleSuffix.map(font::width).orElse(0);
		int primaryWidth = showTimer ? 0 : font.width(visiblePrimary);
		int statusWidth = visibleStatus
			.map(value -> scaledComponentWidth(font, value, statusScale))
			.orElse(0);

		int lineHeight = Math.max(
			font.lineHeight,
			Math.max(
				(int)Math.ceil(font.lineHeight * timeScale),
				(int)Math.ceil(font.lineHeight * statusScale)
			)
		);
		int progressHeight = snapshot.presentation().progress().mode() == ProgressMode.LINE
			? snapshot.presentation().progress().thickness() + 2
			: 0;
		int panelHeight = panel.paddingY() * 2 + lineHeight + progressHeight;
		int x = (viewportLeft + viewportRight - panelWidth) / 2;
		int y = panelBottom - panelHeight + surfaceOffsetY;
		int bottom = y + panelHeight;
		double panelOpacity = preferences.highContrast() ? 1.0D : panel.opacity();
		int background = preferences.highContrast()
			? withAlpha(0xFF071018, surfaceAlpha)
			: withAlpha(panel.backgroundColor(), surfaceAlpha * panelOpacity);
		int border = preferences.highContrast()
			? withAlpha(0xFFF4F7FA, surfaceAlpha)
			: withAlpha(panel.borderColor(), surfaceAlpha);
		int accent = preferences.highContrast()
			? withAlpha(0xFF42D9F5, surfaceAlpha)
			: withAlpha(panel.accentColor(), surfaceAlpha);
		graphics.fill(x, y, x + panelWidth, bottom, background);
		graphics.outline(x, y, panelWidth, panelHeight, border);
		graphics.fill(x, y, x + 3, bottom, accent);
		graphics.fill(x + 3, y, x + Math.max(18, panelWidth / 3), y + 1, accent);

		int contentX = x + 3 + horizontalPadding;
		int textTop = y + panel.paddingY() + Math.max(0, (lineHeight - font.lineHeight) / 2);
		if (titleWidth > 0) {
			Component visibleTitle = truncateComponent(font, snapshot.text().title(), titleWidth);
			if (preferences.highContrast()) {
				visibleTitle = highContrastComponent(visibleTitle, HIGH_CONTRAST_FOREGROUND);
			}
			graphics.text(
				font,
				visibleTitle,
				contentX,
				textTop,
				withAlpha(0xFFF4F7FA, surfaceAlpha),
				true
			);
			int dividerX = contentX + titleWidth + TITLE_GAP;
			graphics.fill(
				dividerX,
				y + panel.paddingY(),
				dividerX + 1,
				y + panel.paddingY() + lineHeight,
				withAlpha(panel.borderColor(), surfaceAlpha * 0.85D)
			);
			contentX = dividerX + 1 + SECTION_GAP;
		}

		if (showTimer) {
			if (prefixWidth > 0) {
				Component prefix = visiblePrefix.orElseThrow();
				if (preferences.highContrast()) {
					prefix = highContrastComponent(prefix, HIGH_CONTRAST_FOREGROUND);
				}
				graphics.text(
					font,
					prefix,
					contentX,
					textTop,
					withAlpha(0xFFF4F7FA, surfaceAlpha),
					false
				);
				contentX += prefixWidth + 3;
			}
			prepareAnimations(frame, timerSlots, preferences.motion(), nowNanos);
			renderTime(
				graphics,
				font,
				timerSlots,
				contentX,
				y + panel.paddingY() + Math.max(0, (lineHeight - (int)Math.ceil(font.lineHeight * timeScale)) / 2),
				timeScale,
				surfaceAlpha,
				preferences,
				nowNanos
			);
			contentX += timerWidth;
			if (suffixWidth > 0) {
				contentX += 3;
				Component suffix = visibleSuffix.orElseThrow();
				if (preferences.highContrast()) {
					suffix = highContrastComponent(suffix, HIGH_CONTRAST_FOREGROUND);
				}
				graphics.text(
					font,
					suffix,
					contentX,
					textTop,
					withAlpha(0xFFF4F7FA, surfaceAlpha),
					false
				);
				contentX += suffixWidth;
			}
		} else {
			Component primaryCopy = preferences.highContrast()
				? highContrastComponent(visiblePrimary, HIGH_CONTRAST_FOREGROUND)
				: visiblePrimary;
			int primaryColor = preferences.highContrast()
				? HIGH_CONTRAST_FOREGROUND
				: snapshot.state() == State.COMPLETED || snapshot.state() == State.COMPLETING
					? snapshot.presentation().completion().color()
					: time.color();
			graphics.text(
				font,
				primaryCopy,
				contentX,
				textTop,
				withAlpha(primaryColor, surfaceAlpha),
				time.shadow()
			);
			contentX += primaryWidth;
		}
		if (visibleStatus.isPresent()) {
			contentX += STATUS_GAP;
			Component statusCopy = preferences.highContrast()
				? highContrastComponent(visibleStatus.orElseThrow(), HIGH_CONTRAST_ACCENT)
				: visibleStatus.orElseThrow();
			int statusColor = preferences.highContrast()
				? HIGH_CONTRAST_ACCENT
				: snapshot.checkpoint().isPresent()
					? snapshot.checkpoint().orElseThrow().color()
					: accent;
			int statusTop = y + panel.paddingY()
				+ Math.max(0, (lineHeight - (int)Math.ceil(font.lineHeight * statusScale)) / 2);
			drawScaledComponent(
				graphics,
				font,
				statusCopy,
				contentX,
				statusTop,
				statusScale,
				withAlpha(statusColor, surfaceAlpha),
				false
			);
		}

		if (snapshot.presentation().progress().mode() == ProgressMode.LINE) {
			var progress = snapshot.presentation().progress();
			int left = x + 3 + horizontalPadding;
			int right = x + panelWidth - horizontalPadding;
			int top = bottom - panel.paddingY() - progress.thickness();
			int trackColor = preferences.highContrast()
				? HIGH_CONTRAST_TRACK
				: progress.trackColor();
			int progressColor = preferences.highContrast()
				? HIGH_CONTRAST_ACCENT
				: progress.color();
			if (right <= left) {
				return;
			}
			graphics.fill(left, top, right, top + progress.thickness(), withAlpha(trackColor, surfaceAlpha));
			double progressRemaining = progress.smooth()
				? frame.remainingTicks()
				: Math.ceil(frame.remainingTicks());
			double ratio = snapshot.totalTicks() <= 0L
				? 0.0D
				: Math.clamp(progressRemaining / snapshot.totalTicks(), 0.0D, 1.0D);
			int filled = (int)Math.round((right - left) * ratio);
			if (filled > 0) {
				graphics.fill(left, top, left + filled, top + progress.thickness(), withAlpha(progressColor, surfaceAlpha));
			}
		}
	}

	private void prepareAnimations(
		final Frame frame,
		final List<TimeSlot> target,
		final Motion motion,
		final long nowNanos
	) {
		long frameIntervalNanos = this.lastPrepareNanos == Long.MIN_VALUE
			? Long.MAX_VALUE
			: Math.max(0L, nowNanos - this.lastPrepareNanos);
		this.lastPrepareNanos = nowNanos;
		UUID currentInstance = frame.snapshot().countdownInstanceId();
		boolean settle = !currentInstance.equals(this.instanceId)
			|| frame.settleGeneration() != this.settleGeneration
			|| target.size() != this.settledSlots.size()
			|| motion == Motion.STATIC;
		if (settle) {
			this.instanceId = currentInstance;
			this.settleGeneration = frame.settleGeneration();
			this.settledSlots = target;
			this.animations = emptyAnimations(target.size());
			return;
		}
		if (target.equals(this.settledSlots)) {
			return;
		}
		DigitPresentation digits = frame.snapshot().presentation().digits();
		List<SlotAnimation> next = new ArrayList<>(target.size());
		int changed = 0;
		for (int index = 0; index < target.size(); index++) {
			TimeSlot beforeSlot = this.settledSlots.get(index);
			TimeSlot afterSlot = target.get(index);
			SlotAnimation previous = index < this.animations.size()
				? this.animations.get(index)
				: null;
			if (beforeSlot.equals(afterSlot)) {
				int previousDurationMs = previous == null
					? 0
					: effectiveDuration(
						digits,
						motion,
						previous.fractional(),
						frame.snapshot().presentation().time().precision().decimals()
					);
				next.add(animationStillTargetsLatest(
					previous,
					afterSlot,
					nowNanos,
					previousDurationMs
				)
					? previous
					: null);
				continue;
			}
			boolean fractional = afterSlot.fractional();
			boolean animate = afterSlot.digit()
				&& beforeSlot.digit()
				&& (digits.animateFractional() || !fractional);
			if (!animate) {
				next.add(null);
				continue;
			}
			int durationMs = effectiveDuration(
				digits,
				motion,
				fractional,
				frame.snapshot().presentation().time().precision().decimals()
			);
			/*
			 * Rendering may stop for F1 or stall below the reel's useful frame rate. In that
			 * case every changed slot settles on the latest authoritative value; old whole-second
			 * digits are no more eligible for replay than skipped hundredths.
			 */
			if (durationMs <= 0 || frameIntervalNanos >= durationMs * 1_000_000L) {
				next.add(null);
				continue;
			}
			long stagger = motion == Motion.FULL ? digits.staggerMs() * 1_000_000L * changed : 0L;
			next.add(new SlotAnimation(
				beforeSlot.digitCharacter(),
				afterSlot.digitCharacter(),
				nowNanos + stagger,
				fractional
			));
			changed++;
		}
		this.settledSlots = target;
		this.animations = Collections.unmodifiableList(next);
	}

	private static boolean animationStillTargetsLatest(
		final SlotAnimation animation,
		final TimeSlot latest,
		final long nowNanos,
		final int durationMs
	) {
		if (
			animation == null
				|| !latest.digit()
				|| animation.incoming() != latest.digitCharacter()
				|| durationMs <= 0
		) {
			return false;
		}
		long elapsedNanos = nowNanos - animation.startedAtNanos();
		return elapsedNanos < 0L || elapsedNanos < durationMs * 1_000_000L;
	}

	private void renderTime(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final List<TimeSlot> slots,
		final int x,
		final int y,
		final float scale,
		final double surfaceAlpha,
		final ClientCountdownPreferences.Snapshot preferences,
		final long nowNanos
	) {
		DigitPresentation digits = currentDigits();
		int localX = 0;
		for (int index = 0; index < slots.size(); index++) {
			TimeSlot target = slots.get(index);
			int localWidth = timeCellWidth(font, target.text());
			SlotAnimation animation = index < this.animations.size() ? this.animations.get(index) : null;
			int clipLeft = x + Math.round(localX * scale);
			int clipRight = x + Math.round((localX + localWidth) * scale);
			int clipBottom = y + Math.max(1, Math.round(font.lineHeight * scale));
			if (animation == null || preferences.motion() == Motion.STATIC) {
				drawScaledText(
					graphics,
					font,
					target.text(),
					x,
					y,
					localX,
					0.0F,
					scale,
					baseDigitColor(preferences, surfaceAlpha),
					currentShadow()
				);
				localX += localWidth;
				continue;
			}

			int durationMs = effectiveDuration(
				digits,
				preferences.motion(),
				animation.fractional(),
				Objects.requireNonNull(this.currentFrame, "countdown frame")
					.snapshot().presentation().time().precision().decimals()
			);
			double raw = durationMs <= 0
				? 1.0D
				: (nowNanos - animation.startedAtNanos()) / (durationMs * 1_000_000.0D);
			if (raw < 0.0D) {
				drawScaledText(graphics, font, Character.toString(animation.outgoing()), x, y, localX, 0.0F, scale,
					baseDigitColor(preferences, surfaceAlpha), currentShadow());
				localX += localWidth;
				continue;
			}
			if (raw >= 1.0D || digits.transition() == DigitTransition.INSTANT) {
				drawScaledText(graphics, font, Character.toString(animation.incoming()), x, y, localX, 0.0F, scale,
					baseDigitColor(preferences, surfaceAlpha), currentShadow());
				localX += localWidth;
				continue;
			}
			double progress = ease(digits.easing(), Math.clamp(raw, 0.0D, 1.0D));
			graphics.enableScissor(clipLeft, y, Math.max(clipLeft + 1, clipRight), clipBottom);
			try {
				if (digits.transition() == DigitTransition.FADE || preferences.motion() == Motion.SIMPLIFIED) {
					drawScaledText(graphics, font, Character.toString(animation.outgoing()), x, y, localX, 0.0F, scale,
						transitionColor(digits.outgoingColor(), surfaceAlpha * (1.0D - progress) * digits.outgoingAlpha(), preferences),
						currentShadow());
					drawScaledText(graphics, font, Character.toString(animation.incoming()), x, y, localX, 0.0F, scale,
						transitionColor(digits.incomingColor(), surfaceAlpha * progress * digits.incomingAlpha(), preferences),
						currentShadow());
				} else {
					int direction = digits.direction() == RollDirection.DOWN ? 1 : -1;
					float outgoingY = (float)(direction * digits.distance() * progress / scale);
					float incomingY = (float)(-direction * digits.distance() * (1.0D - progress) / scale);
					drawScaledText(graphics, font, Character.toString(animation.outgoing()), x, y, localX, outgoingY, scale,
						transitionColor(digits.outgoingColor(), surfaceAlpha * digits.outgoingAlpha(), preferences),
						currentShadow());
					drawScaledText(graphics, font, Character.toString(animation.incoming()), x, y, localX, incomingY, scale,
						transitionColor(digits.incomingColor(), surfaceAlpha * digits.incomingAlpha(), preferences),
						currentShadow());
				}
			} finally {
				graphics.disableScissor();
			}
			localX += localWidth;
		}
	}

	private DigitPresentation currentDigits() {
		return Objects.requireNonNull(this.currentFrame, "countdown frame").snapshot().presentation().digits();
	}

	private boolean currentShadow() {
		return Objects.requireNonNull(this.currentFrame, "countdown frame").snapshot().presentation().time().shadow();
	}

	private transient Frame currentFrame;

	private static void drawScaledText(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final String text,
		final int originX,
		final int originY,
		final int localX,
		final float localY,
		final float scale,
		final int color,
		final boolean shadow
	) {
		int glyphWidth = Math.max(1, font.width(text));
		int cellWidth = timeCellWidth(font, text);
		int centeredX = localX + Math.max(0, (cellWidth - glyphWidth) / 2);
		graphics.pose().pushMatrix();
		graphics.pose().translate(originX, originY);
		graphics.pose().scale(scale, scale);
		try {
			graphics.text(font, text, centeredX, Math.round(localY), color, shadow);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private static void drawScaledComponent(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Component value,
		final int x,
		final int y,
		final float scale,
		final int color,
		final boolean shadow
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		try {
			graphics.text(font, value, 0, 0, color, shadow);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private static VisibleCopy visibleCopy(
		final Font font,
		final boolean showTimer,
		final Component primary,
		final Optional<Component> prefix,
		final Optional<Component> suffix,
		final Optional<Component> status,
		final float statusScale,
		final int timerWidth,
		final int availableWidth
	) {
		if (!showTimer) {
			if (status.isEmpty()) {
				return new VisibleCopy(
					truncateComponent(font, primary, availableWidth),
					Optional.empty(),
					Optional.empty(),
					Optional.empty()
				);
			}
			int usable = Math.max(0, availableWidth - STATUS_GAP);
			int statusBudget = Math.min(
				scaledComponentWidth(font, status.orElseThrow(), statusScale),
				usable / 3
			);
			int primaryBudget = Math.max(0, usable - statusBudget);
			return new VisibleCopy(
				truncateComponent(font, primary, primaryBudget),
				Optional.empty(),
				Optional.empty(),
				optionalScaledTruncated(font, status, statusBudget, statusScale)
			);
		}

		int fieldCount = (prefix.isPresent() ? 1 : 0)
			+ (suffix.isPresent() ? 1 : 0)
			+ (status.isPresent() ? 1 : 0);
		if (fieldCount == 0 || availableWidth <= timerWidth) {
			return new VisibleCopy(
				Component.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
		int fixedGaps = (prefix.isPresent() ? 3 : 0)
			+ (suffix.isPresent() ? 3 : 0)
			+ (status.isPresent() ? STATUS_GAP : 0);
		int textBudget = Math.max(0, availableWidth - timerWidth - fixedGaps);
		if (textBudget <= 0) {
			return new VisibleCopy(
				Component.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
		int remaining = textBudget;
		int remainingFields = fieldCount;
		int prefixBudget = 0;
		int suffixBudget = 0;
		int statusBudget = 0;
		if (prefix.isPresent()) {
			prefixBudget = Math.min(font.width(prefix.orElseThrow()), remaining / remainingFields);
			remaining -= prefixBudget;
			remainingFields--;
		}
		if (suffix.isPresent()) {
			suffixBudget = Math.min(font.width(suffix.orElseThrow()), remaining / remainingFields);
			remaining -= suffixBudget;
			remainingFields--;
		}
		if (status.isPresent()) {
			statusBudget = Math.min(
				scaledComponentWidth(font, status.orElseThrow(), statusScale),
				remaining
			);
		}
		return new VisibleCopy(
			Component.empty(),
			optionalTruncated(font, prefix, prefixBudget),
			optionalTruncated(font, suffix, suffixBudget),
			optionalScaledTruncated(font, status, statusBudget, statusScale)
		);
	}

	private static Optional<Component> optionalScaledTruncated(
		final Font font,
		final Optional<Component> value,
		final int scaledWidth,
		final float scale
	) {
		if (value.isEmpty() || scaledWidth <= 0) {
			return Optional.empty();
		}
		int localWidth = Math.max(0, (int)Math.floor(scaledWidth / scale));
		return optionalTruncated(font, value, localWidth);
	}

	private static Optional<Component> optionalTruncated(
		final Font font,
		final Optional<Component> value,
		final int width
	) {
		if (value.isEmpty() || width < font.width("...")) {
			return Optional.empty();
		}
		return Optional.of(truncateComponent(font, value.orElseThrow(), width));
	}

	private static Component truncateComponent(
		final Font font,
		final Component value,
		final int width
	) {
		if (width <= 0) {
			return Component.empty();
		}
		if (font.width(value) <= width) {
			return value;
		}
		FormattedText ellipsis = FormattedText.of("...", value.getStyle());
		int ellipsisWidth = font.width(ellipsis);
		if (ellipsisWidth > width) {
			return toComponent(font.substrByWidth(value, width));
		}
		FormattedText head = font.substrByWidth(value, Math.max(0, width - ellipsisWidth));
		MutableComponent result = Component.empty();
		Style[] trailingStyle = {value.getStyle()};
		head.visit(
			(style, text) -> {
				if (!text.isEmpty()) {
					trailingStyle[0] = style;
					result.append(Component.literal(text).withStyle(style));
				}
				return Optional.empty();
			},
			Style.EMPTY
		);
		return result.append(Component.literal("...").withStyle(trailingStyle[0]));
	}

	private static Component toComponent(final FormattedText value) {
		MutableComponent result = Component.empty();
		value.visit(
			(style, text) -> {
				result.append(Component.literal(text).withStyle(style));
				return Optional.empty();
			},
			Style.EMPTY
		);
		return result;
	}

	private static Component highContrastComponent(final Component value, final int color) {
		MutableComponent result = Component.empty();
		value.visit(
			(style, text) -> {
				result.append(
					Component.literal(text).withStyle(style.withColor(color & 0x00FF_FFFF))
				);
				return Optional.empty();
			},
			Style.EMPTY
		);
		return result;
	}

	private static Optional<Component> status(final ClientCountdownSnapshot snapshot) {
		if (snapshot.checkpoint().isPresent()) {
			return Optional.of(snapshot.checkpoint().orElseThrow().text());
		}
		if (snapshot.state() == State.PAUSED || snapshot.timing().paused()) {
			return Optional.of(snapshot.text().paused());
		}
		return Optional.empty();
	}

	private static List<TimeSlot> timeSlots(final Formatted formatted) {
		List<TimeSlot> result = new ArrayList<>();
		Matcher graphemes = GRAPHEME_PATTERN.matcher(formatted.text());
		while (graphemes.find()) {
			String value = graphemes.group();
			boolean digit = value.length() == 1
				&& value.charAt(0) >= '0'
				&& value.charAt(0) <= '9';
			result.add(new TimeSlot(
				value,
				digit,
				digit && graphemes.start() >= formatted.fractionalStart()
			));
		}
		return List.copyOf(result);
	}

	private static int localTimeWidth(final Font font, final List<TimeSlot> slots) {
		int local = 0;
		for (TimeSlot slot : slots) {
			local += timeCellWidth(font, slot.text());
		}
		return Math.max(1, local);
	}

	private static int timeCellWidth(final Font font, final String value) {
		boolean digit = value.length() == 1
			&& value.charAt(0) >= '0'
			&& value.charAt(0) <= '9';
		return digit
			? digitCellWidth(font)
			: Math.max(1, font.width(value));
	}

	private static int digitCellWidth(final Font font) {
		int widest = 1;
		for (char digit = '0'; digit <= '9'; digit++) {
			widest = Math.max(widest, font.width(Character.toString(digit)));
		}
		return widest;
	}

	private static int scaledTimeWidth(final int localWidth, final float scale) {
		return Math.max(1, Math.round(localWidth * scale));
	}

	private static float fittedTimeScale(
		final float configuredScale,
		final int localWidth,
		final int availableWidth
	) {
		if (localWidth <= 0) {
			return configuredScale;
		}
		return Math.min(configuredScale, Math.max(1, availableWidth) / (float)localWidth);
	}

	private static int scaledComponentWidth(
		final Font font,
		final Component value,
		final float scale
	) {
		return Math.max(1, (int)Math.ceil(font.width(value) * scale));
	}

	private static List<SlotAnimation> emptyAnimations(final int size) {
		List<SlotAnimation> result = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			result.add(null);
		}
		return Collections.unmodifiableList(result);
	}

	static int effectiveDuration(
		final DigitPresentation digits,
		final Motion motion,
		final boolean fractional,
		final int decimalPlaces
	) {
		int configured = switch (motion) {
			case FULL -> digits.durationMs();
			case SIMPLIFIED -> Math.min(120, Math.max(40, digits.durationMs() / 2));
			case STATIC -> 0;
		};
		if (!fractional || decimalPlaces <= 0) {
			return configured;
		}
		int quantumMs = decimalPlaces == 1 ? 100 : 10;
		return Math.min(configured, quantumMs);
	}

	private int baseDigitColor(
		final ClientCountdownPreferences.Snapshot preferences,
		final double alpha
	) {
		int color = Objects.requireNonNull(this.currentFrame, "countdown frame")
			.snapshot().presentation().time().color();
		return preferences.highContrast()
			? withAlpha(0xFFFFFFFF, alpha)
			: withAlpha(color, alpha);
	}

	private static int transitionColor(
		final int color,
		final double alpha,
		final ClientCountdownPreferences.Snapshot preferences
	) {
		return preferences.highContrast()
			? withAlpha(0xFFFFFFFF, alpha)
			: withAlpha(color, alpha);
	}

	private static double surfaceAlpha(final Frame frame, final Motion motion) {
		if (motion == Motion.STATIC) {
			return 1.0D;
		}
		SurfaceMotion surface = frame.exiting()
			? frame.snapshot().presentation().exit()
			: frame.snapshot().presentation().enter();
		if (surface.mode() == SurfaceTransition.INSTANT) {
			return 1.0D;
		}
		double progress = motion == Motion.SIMPLIFIED
			? Math.clamp(frame.surfaceProgress() * 2.0D, 0.0D, 1.0D)
			: frame.surfaceProgress();
		return ease(surface.easing(), progress);
	}

	private static int surfaceOffsetY(final Frame frame, final Motion motion) {
		if (motion != Motion.FULL) {
			return 0;
		}
		SurfaceMotion surface = frame.exiting()
			? frame.snapshot().presentation().exit()
			: frame.snapshot().presentation().enter();
		if (surface.mode() != SurfaceTransition.SLIDE) {
			return 0;
		}
		return (int)Math.round((1.0D - ease(surface.easing(), frame.surfaceProgress())) * 4.0D);
	}

	private static double ease(final Easing easing, final double value) {
		double progress = Math.clamp(value, 0.0D, 1.0D);
		return switch (easing) {
			case LINEAR -> progress;
			case EASE_OUT_CUBIC -> 1.0D - Math.pow(1.0D - progress, 3.0D);
			case EASE_IN_OUT_CUBIC -> progress < 0.5D
				? 4.0D * progress * progress * progress
				: 1.0D - Math.pow(-2.0D * progress + 2.0D, 3.0D) / 2.0D;
		};
	}

	private static int withAlpha(final int color, final double multiplier) {
		int sourceAlpha = color >>> 24;
		int alpha = (int)Math.round(sourceAlpha * Math.clamp(multiplier, 0.0D, 1.0D));
		return color & 0x00FF_FFFF | alpha << 24;
	}

	public void renderFrame(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Frame frame,
		final ClientCountdownPreferences.Snapshot preferences,
		final int viewportLeft,
		final int viewportRight,
		final int panelBottom,
		final long nowNanos
	) {
		this.currentFrame = frame;
		try {
			render(graphics, font, frame, preferences, viewportLeft, viewportRight, panelBottom, nowNanos);
		} finally {
			this.currentFrame = null;
		}
	}

	public record Frame(
		ClientCountdownSnapshot snapshot,
		double remainingTicks,
		long settleGeneration,
		double surfaceProgress,
		boolean exiting
	) {
		public Frame {
			snapshot = Objects.requireNonNull(snapshot, "snapshot");
			if (!Double.isFinite(remainingTicks) || remainingTicks < 0.0D
				|| !Double.isFinite(surfaceProgress) || surfaceProgress < 0.0D || surfaceProgress > 1.0D) {
				throw new IllegalArgumentException("countdown render frame is invalid");
			}
		}
	}

	private record SlotAnimation(
		char outgoing,
		char incoming,
		long startedAtNanos,
		boolean fractional
	) {
	}

	private record TimeSlot(String text, boolean digit, boolean fractional) {
		private TimeSlot {
			text = Objects.requireNonNull(text, "text");
			if (text.isEmpty() || digit != (
				text.length() == 1 && text.charAt(0) >= '0' && text.charAt(0) <= '9'
			)) {
				throw new IllegalArgumentException("countdown time slot is invalid");
			}
			if (fractional && !digit) {
				throw new IllegalArgumentException("only a digit slot can be fractional");
			}
		}

		private char digitCharacter() {
			if (!this.digit) {
				throw new IllegalStateException("countdown time slot is not numeric");
			}
			return this.text.charAt(0);
		}
	}

	private record VisibleCopy(
		Component primary,
		Optional<Component> prefix,
		Optional<Component> suffix,
		Optional<Component> status
	) {
		private VisibleCopy {
			primary = Objects.requireNonNull(primary, "primary");
			prefix = Objects.requireNonNull(prefix, "prefix");
			suffix = Objects.requireNonNull(suffix, "suffix");
			status = Objects.requireNonNull(status, "status");
		}
	}
}
