package io.github.zcpu954861.pixeltzzpro.client.countdown;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fixed center-bottom V3C surface, attached directly above vanilla ActionBar extraction. */
public final class CountdownOverlay {
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();
	private static final CountdownRenderer RENDERER = new CountdownRenderer();

	private CountdownOverlay() {
	}

	public static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		try {
			HudElementRegistry.attachElementAfter(
				VanillaHudElements.OVERLAY_MESSAGE,
				PixelTzzPro.id("countdown_overlay"),
				CountdownOverlay::render
			);
		} catch (RuntimeException error) {
			REGISTERED.set(false);
			throw error;
		}
	}

	public static void reset() {
		RENDERER.reset();
	}

	private static void render(
		final GuiGraphicsExtractor graphics,
		final DeltaTracker deltaTracker
	) {
		Objects.requireNonNull(graphics, "graphics");
		Objects.requireNonNull(deltaTracker, "deltaTracker");
		long nowNanos = System.nanoTime();
		ClientCountdownRuntime.view(nowNanos).ifPresent(view -> {
			graphics.nextStratum();
			int actionBarTextTop = graphics.guiHeight() - 72;
			int panelBottom = actionBarTextTop
				- view.snapshot().presentation().panel().gapAboveActionBar();
			RENDERER.renderFrame(
				graphics,
				Minecraft.getInstance().font,
				new CountdownRenderer.Frame(
					view.snapshot(),
					view.remainingTicks(),
					view.settleGeneration(),
					view.surfaceProgress(),
					view.exiting()
				),
				ClientCountdownPreferences.snapshot(),
				0,
				graphics.guiWidth(),
				panelBottom,
				nowNanos
			);
		});
	}
}
