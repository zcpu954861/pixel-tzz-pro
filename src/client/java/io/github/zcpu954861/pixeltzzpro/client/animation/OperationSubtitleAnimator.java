package io.github.zcpu954861.pixeltzzpro.client.animation;

import io.github.zcpu954861.pixeltzzpro.animation.SubtitleQueueTimeline;
import io.github.zcpu954861.pixeltzzpro.animation.TypewriterTimeline;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Non-blocking success feedback used after a control operation closes back to the game.
 */
public final class OperationSubtitleAnimator {
	private static final long BASE_NANOS_PER_CODE_POINT = 120_000_000L;
	private static final long MINIMUM_REVEAL_NANOS = 900_000_000L;
	private static final int HUD_SETTLE_TICKS = 3;
	private static final int COMPLETE_TEXT_HOLD_TICKS = 30;
	private static final int HOLD_TICKS = 80;
	private static final int FADE_TICKS = 24;
	private static final int MAX_PENDING = 3;
	private static final int MAIN_TEXT = 0xF2F2F2;
	private static final int SUCCESS = 0x65D68A;
	private static final Deque<int[]> PENDING = new ArrayDeque<>();

	private static int[] current;
	private static long startedAtNanos;
	private static long nanosPerCodePoint;
	private static int hudTicks;
	private static int completedTicks;

	private OperationSubtitleAnimator() {
	}

	public static void enqueue(final String message) {
		if (message == null || message.isBlank()) {
			return;
		}
		if (PENDING.size() == MAX_PENDING) {
			PENDING.removeFirst();
		}
		PENDING.addLast(message.codePoints().toArray());
	}

	public static void reset() {
		PENDING.clear();
		current = null;
		startedAtNanos = 0L;
		nanosPerCodePoint = 0L;
		hudTicks = 0;
		completedTicks = 0;
	}

	public static void tick(final Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (client.gui.screen() != null) {
			hudTicks = 0;
			return;
		}
		if (++hudTicks < HUD_SETTLE_TICKS) {
			return;
		}
		if (current == null) {
			current = PENDING.pollFirst();
			if (current == null) {
				return;
			}
			startedAtNanos = System.nanoTime();
			nanosPerCodePoint = Math.max(
				BASE_NANOS_PER_CODE_POINT,
				Math.ceilDiv(MINIMUM_REVEAL_NANOS, Math.max(1, current.length))
			);
			completedTicks = 0;
			client.getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.22F, 0.32F)
			);
		}

		int visible = TypewriterTimeline.visibleCodePoints(
			Math.max(0L, System.nanoTime() - startedAtNanos),
			current.length,
			nanosPerCodePoint
		);
		client.gui.hud.setTimes(0, visible == current.length ? HOLD_TICKS : 10, FADE_TICKS);
		client.gui.hud.setSubtitle(frame(current, visible));
		client.gui.hud.setTitle(Component.empty());
		if (visible == current.length) {
			var hold = SubtitleQueueTimeline.afterCompletedFrame(
				completedTicks,
				COMPLETE_TEXT_HOLD_TICKS
			);
			completedTicks = hold.completedTicks();
			if (hold.mayAdvance()) {
				current = null;
				completedTicks = 0;
			}
		}
	}

	private static Component frame(final int[] codePoints, final int visible) {
		MutableComponent result = Component.empty();
		for (int index = 0; index < visible; index++) {
			result.append(
				Component.literal(new String(Character.toChars(codePoints[index])))
					.withColor(index + 1 == visible ? SUCCESS : MAIN_TEXT)
			);
		}
		if (visible < codePoints.length) {
			result.append(Component.literal(" ▌").withColor(SUCCESS));
		}
		return result;
	}
}
