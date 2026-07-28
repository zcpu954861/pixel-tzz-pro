package io.github.zcpu954861.pixeltzzpro.client.animation;

import io.github.zcpu954861.pixeltzzpro.animation.TypewriterTimeline;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Bounded client-side ActionBar typewriter. Server state never waits for this animation.
 */
public final class LoadingActionBarAnimator {
	private static final int BRAND_GOLD = 0xF4C95D;
	private static final int INFO_CYAN = 0x64D8E8;
	private static final int MAIN_TEXT = 0xF2F2F2;
	private static final long NANOS_PER_CODE_POINT = 65_000_000L;
	private static final int MAX_PENDING = 4;
	private static final Deque<Request> PENDING = new ArrayDeque<>();

	private static long latestSequence = Long.MIN_VALUE;
	private static Request current;
	private static long currentStartedAtNanos;
	private static int lastVisibleCharacters;

	private LoadingActionBarAnimator() {
	}

	public static void enqueue(final long sequence, final Component finalText) {
		if (sequence <= latestSequence) {
			return;
		}

		String resolvedText = finalText.getString();
		if (resolvedText.isBlank()) {
			return;
		}

		latestSequence = sequence;
		if (PENDING.size() == MAX_PENDING) {
			PENDING.removeFirst();
		}
		PENDING.addLast(new Request(sequence, resolvedText.codePoints().toArray()));
	}

	public static void reset() {
		PENDING.clear();
		current = null;
		latestSequence = Long.MIN_VALUE;
		currentStartedAtNanos = 0L;
		lastVisibleCharacters = 0;
	}

	public static void tick(final Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		if (current == null) {
			current = PENDING.pollFirst();
			currentStartedAtNanos = System.nanoTime();
			lastVisibleCharacters = 0;
			if (current == null) {
				return;
			}
		}

		long elapsedNanos = Math.max(0L, System.nanoTime() - currentStartedAtNanos);
		int visibleCharacters = TypewriterTimeline.visibleCodePoints(
			elapsedNanos,
			current.codePoints.length,
			NANOS_PER_CODE_POINT
		);
		client.gui.hud.setOverlayMessage(buildFrame(current.codePoints, visibleCharacters), false);

		if (visibleCharacters != lastVisibleCharacters) {
			lastVisibleCharacters = visibleCharacters;
			if (visibleCharacters < current.codePoints.length) {
				client.getSoundManager()
					.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.75F, 0.10F));
			} else {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35F, 0.28F));
				current = null;
			}
		}
	}

	private static Component buildFrame(final int[] codePoints, final int visibleCharacters) {
		MutableComponent frame = Component.empty();
		for (int index = 0; index < visibleCharacters; index++) {
			int color = index == visibleCharacters - 1 && visibleCharacters < codePoints.length ? INFO_CYAN : BRAND_GOLD;
			frame.append(Component.literal(new String(Character.toChars(codePoints[index]))).withColor(color));
		}
		if (visibleCharacters < codePoints.length) {
			frame.append(Component.literal("▌").withColor(MAIN_TEXT));
		}
		return frame;
	}

	private record Request(long sequence, int[] codePoints) {
	}
}
