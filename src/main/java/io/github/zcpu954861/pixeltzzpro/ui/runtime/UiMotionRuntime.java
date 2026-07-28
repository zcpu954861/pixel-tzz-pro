package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.Easing;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionDefinition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionKeyframe;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionProperty;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.MotionTrack;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ReducedMotion;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StyleValue;
import io.github.zcpu954861.pixeltzzpro.ui.layout.UiLayoutEngine.Rect;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Pure, client-neutral sampler for one validated UI motion timeline.
 *
 * <p>The sampler changes presentation only. Callers remain responsible for applying the returned
 * frame without delaying or mutating authoritative state.
 */
public final class UiMotionRuntime {
	private static final int REDUCED_FADE_MILLIS = 100;

	private UiMotionRuntime() {
	}

	public static Sample sample(
		final MotionDefinition motion,
		final long elapsedMilliseconds,
		final boolean reduceMotion,
		final Map<String, String> colorTokens
	) {
		long elapsed = Math.max(0L, elapsedMilliseconds);
		Map<String, String> colors = colorTokens == null ? Map.of() : colorTokens;
		if (!reduceMotion) {
			double progress = timelineProgress(motion, elapsed);
			return sampleAt(
				motion,
				ease(motion.easing(), progress),
				colors,
				elapsed >= (long)motion.delayMilliseconds() + motion.durationMilliseconds()
			);
		}

		return switch (motion.reducedMotion()) {
			case FINAL -> sampleAt(motion, 1.0, colors, true);
			case FADE -> {
				Sample target = sampleAt(motion, 1.0, colors, true);
				int duration = Math.max(
					1,
					Math.min(REDUCED_FADE_MILLIS, motion.durationMilliseconds())
				);
				double fade = ease(
					motion.easing(),
					Math.clamp((double)elapsed / duration, 0.0, 1.0)
				);
				yield new Sample(
					(float)(target.opacity() * fade),
					target.translateX(),
					target.translateY(),
					target.scale(),
					target.color(),
					target.clipProgress(),
					target.progressValue(),
					elapsed >= duration
				);
			}
			case KEEP -> {
				double progress = timelineProgress(motion, elapsed);
				Sample animated = sampleAt(
					motion,
					ease(motion.easing(), progress),
					colors,
					elapsed >= (long)motion.delayMilliseconds() + motion.durationMilliseconds()
				);
				Sample target = sampleAt(motion, 1.0, colors, true);
				yield new Sample(
					animated.opacity(),
					target.translateX(),
					target.translateY(),
					target.scale(),
					animated.color(),
					animated.clipProgress(),
					animated.progressValue(),
					animated.finished()
				);
			}
		};
	}

	/**
	 * Composes concurrently active presentation layers in trigger order.
	 *
	 * <p>Opacity, scale, and clipping multiply; translations add; the latest explicit color or
	 * progress value wins. This keeps independent show, hover, press, and outcome feedback from
	 * cancelling one another while preserving identity values for tracks they do not animate.
	 */
	public static Sample compose(final List<Sample> layers) {
		float opacity = 1.0F;
		double translateX = 0.0;
		double translateY = 0.0;
		double scale = 1.0;
		OptionalInt color = OptionalInt.empty();
		double clipProgress = 1.0;
		OptionalDouble progressValue = OptionalDouble.empty();
		boolean finished = true;
		for (Sample layer : layers) {
			opacity *= layer.opacity();
			translateX += layer.translateX();
			translateY += layer.translateY();
			scale *= layer.scale();
			if (layer.color().isPresent()) {
				color = layer.color();
			}
			clipProgress *= layer.clipProgress();
			if (layer.progressValue().isPresent()) {
				progressValue = layer.progressValue();
			}
			finished &= layer.finished();
		}
		return new Sample(
			Math.clamp(opacity, 0.0F, 1.0F),
			translateX,
			translateY,
			Math.clamp(scale, 0.0, 8.0),
			color,
			Math.clamp(clipProgress, 0.0, 1.0),
			progressValue,
			finished
		);
	}

	public static OptionalInt continuityColorOrigin(
		final OptionalInt origin,
		final OptionalInt initial,
		final int baseColor
	) {
		int visibleOrigin = origin.orElse(baseColor);
		int visibleInitial = initial.orElse(baseColor);
		return visibleOrigin == visibleInitial
			? OptionalInt.empty()
			: OptionalInt.of(visibleOrigin);
	}

	public static OptionalInt applyColorContinuity(
		final OptionalInt sample,
		final int baseColor,
		final OptionalInt origin,
		final double remaining
	) {
		double amount = Math.clamp(remaining, 0.0, 1.0);
		if (origin.isEmpty() || amount <= 0.0) {
			return sample;
		}
		return OptionalInt.of(
			interpolateColor(sample.orElse(baseColor), origin.orElseThrow(), amount)
		);
	}

	public static OptionalDouble continuityNumberDelta(
		final OptionalDouble origin,
		final OptionalDouble initial,
		final double baseValue
	) {
		double delta = origin.orElse(baseValue) - initial.orElse(baseValue);
		return Math.abs(delta) < 0.000001
			? OptionalDouble.empty()
			: OptionalDouble.of(delta);
	}

	public static OptionalDouble applyNumberContinuity(
		final OptionalDouble sample,
		final double baseValue,
		final OptionalDouble delta,
		final double remaining
	) {
		double amount = Math.clamp(remaining, 0.0, 1.0);
		if (delta.isEmpty() || amount <= 0.0) {
			return sample;
		}
		return OptionalDouble.of(
			sample.orElse(baseValue) + delta.orElseThrow() * amount
		);
	}

	/**
	 * Returns the unscaled source rectangle that can be rendered around the center of a transformed
	 * hit rectangle. Input continues to use {@code hitBounds}; only presentation uses this result.
	 */
	public static Rect presentationSource(final Rect hitBounds, final double scale) {
		if (!Double.isFinite(scale) || scale <= 0.001 || Math.abs(scale - 1.0) < 0.001) {
			return hitBounds;
		}
		double centerX = hitBounds.x() + hitBounds.width() / 2.0;
		double centerY = hitBounds.y() + hitBounds.height() / 2.0;
		int width = Math.max(1, safeRound(hitBounds.width() / scale));
		int height = Math.max(1, safeRound(hitBounds.height() / scale));
		return new Rect(
			safeRound(centerX - width / 2.0),
			safeRound(centerY - height / 2.0),
			width,
			height
		);
	}

	private static Sample sampleAt(
		final MotionDefinition motion,
		final double progress,
		final Map<String, String> colors,
		final boolean finished
	) {
		float opacity = 1.0F;
		double translateX = 0.0;
		double translateY = 0.0;
		double scale = 1.0;
		OptionalInt color = OptionalInt.empty();
		double clipProgress = 1.0;
		OptionalDouble progressValue = OptionalDouble.empty();

		for (MotionTrack track : motion.tracks()) {
			switch (track.property()) {
				case OPACITY ->
					opacity = (float)Math.clamp(numberValue(track, progress, 1.0), 0.0, 1.0);
				case TRANSLATE_X -> translateX = numberValue(track, progress, 0.0);
				case TRANSLATE_Y -> translateY = numberValue(track, progress, 0.0);
				case SCALE -> scale = Math.clamp(numberValue(track, progress, 1.0), 0.0, 8.0);
				case COLOR -> color = colorValue(track, progress, colors);
				case CLIP_PROGRESS ->
					clipProgress = Math.clamp(numberValue(track, progress, 1.0), 0.0, 1.0);
				case PROGRESS_VALUE ->
					progressValue = OptionalDouble.of(numberValue(track, progress, 0.0));
			}
		}
		return new Sample(
			opacity,
			translateX,
			translateY,
			scale,
			color,
			clipProgress,
			progressValue,
			finished
		);
	}

	private static double timelineProgress(
		final MotionDefinition motion,
		final long elapsedMilliseconds
	) {
		long activeElapsed = elapsedMilliseconds - motion.delayMilliseconds();
		if (activeElapsed < 0L) {
			return 0.0;
		}
		if (motion.durationMilliseconds() <= 0) {
			return 1.0;
		}
		return Math.clamp(
			(double)activeElapsed / motion.durationMilliseconds(),
			0.0,
			1.0
		);
	}

	private static double numberValue(
		final MotionTrack track,
		final double progress,
		final double fallback
	) {
		FramePair pair = framePair(track.keyframes(), progress);
		if (pair == null) {
			return fallback;
		}
		double left = number(pair.left().value(), fallback);
		double right = number(pair.right().value(), left);
		return left + (right - left) * pair.localProgress();
	}

	private static OptionalInt colorValue(
		final MotionTrack track,
		final double progress,
		final Map<String, String> colors
	) {
		FramePair pair = framePair(track.keyframes(), progress);
		if (pair == null) {
			return OptionalInt.empty();
		}
		OptionalInt left = color(pair.left().value(), colors);
		OptionalInt right = color(pair.right().value(), colors);
		if (left.isEmpty() || right.isEmpty()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(interpolateColor(left.getAsInt(), right.getAsInt(), pair.localProgress()));
	}

	private static FramePair framePair(
		final List<MotionKeyframe> frames,
		final double progress
	) {
		if (frames.isEmpty()) {
			return null;
		}
		MotionKeyframe left = frames.getFirst();
		MotionKeyframe right = frames.getLast();
		for (int index = 1; index < frames.size(); index++) {
			if (progress <= frames.get(index).at()) {
				left = frames.get(index - 1);
				right = frames.get(index);
				break;
			}
		}
		double local = right.at() <= left.at()
			? 1.0
			: Math.clamp((progress - left.at()) / (right.at() - left.at()), 0.0, 1.0);
		return new FramePair(left, right, local);
	}

	private static double number(final StyleValue value, final double fallback) {
		try {
			double parsed = JsonParser.parseString(value.canonicalJson()).getAsDouble();
			return Double.isFinite(parsed) ? parsed : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static OptionalInt color(
		final StyleValue value,
		final Map<String, String> colors
	) {
		try {
			JsonElement json = JsonParser.parseString(value.canonicalJson());
			if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
				return OptionalInt.empty();
			}
			String raw = json.getAsString();
			if (raw.startsWith("@colors.")) {
				raw = colors.get(raw.substring("@colors.".length()));
			}
			if (raw == null || !raw.matches("#[0-9a-fA-F]{6}")) {
				return OptionalInt.empty();
			}
			return OptionalInt.of(0xFF000000 | Integer.parseInt(raw.substring(1), 16));
		} catch (RuntimeException ignored) {
			return OptionalInt.empty();
		}
	}

	public static int interpolateColor(
		final int left,
		final int right,
		final double progress
	) {
		double amount = Double.isFinite(progress) ? Math.clamp(progress, 0.0, 1.0) : 0.0;
		int alpha = interpolateChannel(left >>> 24, right >>> 24, amount);
		int red = interpolateChannel(left >>> 16, right >>> 16, amount);
		int green = interpolateChannel(left >>> 8, right >>> 8, amount);
		int blue = interpolateChannel(left, right, amount);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int interpolateChannel(
		final int left,
		final int right,
		final double progress
	) {
		return Math.clamp(
			(int)Math.round((left & 0xFF) + ((right & 0xFF) - (left & 0xFF)) * progress),
			0,
			255
		);
	}

	private static int safeRound(final double value) {
		if (!Double.isFinite(value)) {
			return 0;
		}
		long rounded = Math.round(value);
		return (int)Math.clamp(rounded, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	private static double ease(final Easing easing, final double value) {
		return switch (easing) {
			case LINEAR -> value;
			case EASE_IN_CUBIC -> value * value * value;
			case EASE_OUT_CUBIC -> 1.0 - Math.pow(1.0 - value, 3.0);
			case EASE_IN_OUT_CUBIC -> value < 0.5
				? 4.0 * value * value * value
				: 1.0 - Math.pow(-2.0 * value + 2.0, 3.0) / 2.0;
			case EASE_OUT_QUART -> 1.0 - Math.pow(1.0 - value, 4.0);
		};
	}

	public record Sample(
		float opacity,
		double translateX,
		double translateY,
		double scale,
		OptionalInt color,
		double clipProgress,
		OptionalDouble progressValue,
		boolean finished
	) {
	}

	/**
	 * Uniform affine transform shared by node drawing, clipping, widgets, and hit geometry.
	 */
	public record Transform(double scale, double offsetX, double offsetY) {
		public static Transform root(final int viewportX, final int viewportY) {
			return new Transform(1.0, viewportX, viewportY);
		}

		public Transform around(
			final Rect pivot,
			final double localScale,
			final double translateX,
			final double translateY
		) {
			double centerX = this.scale * (pivot.x() + pivot.width() / 2.0) + this.offsetX;
			double centerY = this.scale * (pivot.y() + pivot.height() / 2.0) + this.offsetY;
			return new Transform(
				this.scale * localScale,
				localScale * this.offsetX
					+ (1.0 - localScale) * centerX
					+ this.scale * translateX,
				localScale * this.offsetY
					+ (1.0 - localScale) * centerY
					+ this.scale * translateY
			);
		}

		public Rect apply(final Rect value) {
			int left = safeRound(this.scale * value.x() + this.offsetX);
			int top = safeRound(this.scale * value.y() + this.offsetY);
			int right = safeRound(this.scale * value.right() + this.offsetX);
			int bottom = safeRound(this.scale * value.bottom() + this.offsetY);
			return new Rect(
				Math.min(left, right),
				Math.min(top, bottom),
				Math.abs(right - left),
				Math.abs(bottom - top)
			);
		}
	}

	private record FramePair(
		MotionKeyframe left,
		MotionKeyframe right,
		double localProgress
	) {
	}
}
