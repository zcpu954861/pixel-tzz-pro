package io.github.zcpu954861.pixeltzzpro.client.screen;

import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Sample;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Shared hierarchical navigation for mod-owned full-screen surfaces.
 *
 * <p>Backgrounds stay fixed while the interactive surface communicates push/pop direction.
 * Input is locked until the visual and logical screen agree, so transformed widgets never expose
 * stale hit boxes. Button and page sound ownership remains unchanged.</p>
 */
public abstract class TransitioningConsoleScreen extends Screen {
	private static final int HIDDEN_MOUSE = -10_000;

	private final Screen parent;
	private Motion motion;
	private long motionStartedAt = Long.MIN_VALUE;
	private Runnable completion;

	protected TransitioningConsoleScreen(
		final Component title,
		final Screen parent,
		final Motion entryMotion
	) {
		super(title);
		this.parent = parent;
		prepareEntry(entryMotion);
	}

	@Override
	public void tick() {
		super.tick();
		long now = Util.getMillis();
		startMotionIfNeeded(now);
		if (this.motion == null || !sample(now).finished()) {
			return;
		}
		if (this.motion.entering()) {
			this.motion = null;
			this.motionStartedAt = Long.MIN_VALUE;
			return;
		}
		Runnable next = this.completion;
		this.motion = null;
		this.motionStartedAt = Long.MIN_VALUE;
		this.completion = null;
		if (next != null) {
			next.run();
		}
	}

	protected final void pushScreen(final Screen target) {
		if (navigationLocked()) {
			return;
		}
		prepareEntry(target, Motion.PUSH_ENTER);
		startExit(Motion.PUSH_EXIT, () -> this.minecraft.gui.setScreen(target));
	}

	protected final void replaceScreen(final Screen target) {
		if (navigationLocked()) {
			return;
		}
		prepareEntry(target, Motion.REPLACE_ENTER);
		startExit(Motion.REPLACE_EXIT, () -> this.minecraft.gui.setScreen(target));
	}

	protected final void closeToParent() {
		if (navigationLocked()) {
			return;
		}
		if (this.parent instanceof TransitioningConsoleScreen) {
			prepareEntry(this.parent, Motion.POP_ENTER);
			startExit(Motion.POP_EXIT, () -> this.minecraft.gui.setScreen(this.parent));
			return;
		}
		startExit(Motion.ROOT_EXIT, () -> this.minecraft.gui.setScreen(this.parent));
	}

	static void preparePopEntry(final Screen screen) {
		prepareEntry(screen, Motion.POP_ENTER);
	}

	private static void prepareEntry(final Screen screen, final Motion entryMotion) {
		if (screen instanceof TransitioningConsoleScreen transitioning) {
			transitioning.prepareEntry(entryMotion);
		}
	}

	private void prepareEntry(final Motion entryMotion) {
		if (!entryMotion.entering()) {
			throw new IllegalArgumentException("entry motion required");
		}
		this.motion = entryMotion;
		this.motionStartedAt = Long.MIN_VALUE;
		this.completion = null;
	}

	private void startExit(final Motion exitMotion, final Runnable completion) {
		if (exitMotion.entering()) {
			throw new IllegalArgumentException("exit motion required");
		}
		this.motion = exitMotion;
		this.motionStartedAt = Util.getMillis();
		this.completion = completion;
	}

	protected final NavigationFrame beginNavigationFrame(final GuiGraphicsExtractor graphics) {
		long now = Util.getMillis();
		startMotionIfNeeded(now);
		Sample current = sample(now);
		graphics.pose().pushMatrix();
		if (current.scale() != 1.0F) {
			graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
			graphics.pose().scale(current.scale(), current.scale());
			graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
		}
		graphics.pose().translate(current.translateX(), current.translateY());
		return new NavigationFrame(current.veilAlpha());
	}

	protected final void endNavigationFrame(
		final GuiGraphicsExtractor graphics,
		final NavigationFrame frame
	) {
		graphics.pose().popMatrix();
		if (frame.veilAlpha() > 0) {
			graphics.nextStratum();
			graphics.fill(0, 0, this.width, this.height, frame.veilAlpha() << 24);
		}
	}

	protected final int transitionMouseX(final int mouseX) {
		return navigationLocked() ? HIDDEN_MOUSE : mouseX;
	}

	protected final int transitionMouseY(final int mouseY) {
		return navigationLocked() ? HIDDEN_MOUSE : mouseY;
	}

	protected final boolean navigationLocked() {
		return this.motion != null;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return navigationLocked() || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return navigationLocked() || super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		return navigationLocked() || super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		return navigationLocked()
			|| super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		return navigationLocked() || super.keyPressed(event);
	}

	private void startMotionIfNeeded(final long now) {
		if (this.motion != null && this.motionStartedAt == Long.MIN_VALUE) {
			this.motionStartedAt = now;
		}
	}

	private Sample sample(final long now) {
		if (this.motion == null) {
			return new Sample(0.0F, 0.0F, 1.0F, 0, true);
		}
		return NavigationTransitionTimeline.sample(
			this.motion,
			Math.max(0L, now - this.motionStartedAt),
			this.width,
			screenEffectScale()
		);
	}

	private double screenEffectScale() {
		return this.minecraft == null ? 1.0 : this.minecraft.options.screenEffectScale().get();
	}

	protected record NavigationFrame(int veilAlpha) {
	}
}
