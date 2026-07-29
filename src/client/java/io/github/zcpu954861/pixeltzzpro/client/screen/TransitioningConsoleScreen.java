package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.DANGER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.WARNING;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState.RequestKind;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload.ActionEntry;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Motion;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.NavigationTransitionTimeline.Sample;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
	private ActionEntry routingAction;
	private boolean routingWithPresetTargets;

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
		if (this.motion == null) {
			tickActionRouting();
			return;
		}
		if (!sample(now).finished()) {
			return;
		}
		if (this.motion.entering()) {
			this.motion = null;
			this.motionStartedAt = Long.MIN_VALUE;
			tickActionRouting();
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

	/**
	 * Replaces a request shell with its authoritative content without playing a second full-page
	 * transition. The shell is already the visible destination; only its resolved content changes.
	 */
	protected final void replaceResolvedContent(final Screen target) {
		if (navigationLocked()) {
			return;
		}
		clearEntry(target);
		this.minecraft.gui.setScreen(target);
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

	protected final void closeToHud() {
		if (!navigationLocked()) {
			startExit(Motion.ROOT_EXIT, () -> this.minecraft.gui.setScreen(null));
		}
	}

	/**
	 * Starts a server-authoritative action while keeping the current screen visible.
	 */
	protected final boolean dispatchAction(final ActionEntry action) {
		boolean sent = action.maximumTargets() == 0
			? ClientConsoleState.prepareOperation(action, List.of())
			: ClientConsoleState.requestTargets(action);
		if (sent) {
			this.routingAction = action;
			this.routingWithPresetTargets = false;
		}
		return sent;
	}

	/**
	 * Skips the target picker when a screen already identifies the exact affected players.
	 */
	protected final boolean dispatchAction(
		final ActionEntry action,
		final List<UUID> targetIds
	) {
		boolean sent = ClientConsoleState.prepareOperation(action, targetIds);
		if (sent) {
			this.routingAction = action;
			this.routingWithPresetTargets = true;
		}
		return sent;
	}

	protected final boolean actionRouting(final Identifier operationId) {
		return this.routingAction != null
			&& this.routingAction.operationId().equals(operationId);
	}

	static void preparePopEntry(final Screen screen) {
		prepareEntry(screen, Motion.POP_ENTER);
	}

	private static void prepareEntry(final Screen screen, final Motion entryMotion) {
		if (screen instanceof TransitioningConsoleScreen transitioning) {
			transitioning.prepareEntry(entryMotion);
		}
	}

	private static void clearEntry(final Screen screen) {
		if (screen instanceof TransitioningConsoleScreen transitioning) {
			transitioning.motion = null;
			transitioning.motionStartedAt = Long.MIN_VALUE;
			transitioning.completion = null;
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

	/**
	 * Draws the shared server-authority badge used by every built-in console surface.
	 *
	 * @return rendered badge width, for callers that place another badge immediately beside it
	 */
	protected final int drawServerSyncBadge(
		final GuiGraphicsExtractor graphics,
		final int rightX,
		final int y
	) {
		return drawServerSyncBadge(
			graphics,
			rightX,
			y,
			ClientConsoleState.pending(),
			ClientConsoleState.snapshot().isPresent()
		);
	}

	/**
	 * Uses the same synchronization badge for another server-authored read model.
	 */
	protected final int drawServerSyncBadge(
		final GuiGraphicsExtractor graphics,
		final int rightX,
		final int y,
		final boolean pending,
		final boolean synchronizedState
	) {
		String label = pending ? "同步中" : synchronizedState ? "已同步" : "未同步";
		int color = pending ? WARNING : synchronizedState ? SUCCESS : DANGER;
		int width = this.font.width(label) + 23;
		int x = rightX - width;
		graphics.fill(x, y, rightX, y + 17, 0xD5202B35);
		graphics.outline(x, y, width, 17, withAlpha(color, 190));
		graphics.fill(x + 6, y + 6, x + 11, y + 11, color);
		graphics.text(this.font, label, x + 15, y + 5, color, false);
		return width;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return interactionLocked() || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return interactionLocked() || super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		return interactionLocked() || super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(
		final double mouseX,
		final double mouseY,
		final double horizontalAmount,
		final double verticalAmount
	) {
		return interactionLocked()
			|| super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		return interactionLocked() || super.keyPressed(event);
	}

	private boolean interactionLocked() {
		return navigationLocked() || this.routingAction != null && ClientConsoleState.pending();
	}

	private void startMotionIfNeeded(final long now) {
		if (this.motion != null && this.motionStartedAt == Long.MIN_VALUE) {
			this.motionStartedAt = now;
		}
	}

	private void tickActionRouting() {
		ActionEntry action = this.routingAction;
		if (action == null || navigationLocked()) {
			return;
		}
		if (!this.routingWithPresetTargets && action.maximumTargets() > 0) {
			var targetSnapshot = ClientConsoleState.targets()
				.filter(value -> value.operationId().equals(action.operationId()))
				.orElse(null);
			if (targetSnapshot != null) {
				this.routingAction = null;
				pushScreen(new TargetSelectionScreen(this, action, targetSnapshot));
				return;
			}
		} else if (
			ClientConsoleState.confirmation()
				.filter(value -> value.operationId().equals(action.operationId()))
				.isPresent()
		) {
			this.routingAction = null;
			pushScreen(new OperationConfirmationScreen(this, action));
			return;
		}
		if (ClientConsoleState.pending()) {
			return;
		}
		RequestKind expected = !this.routingWithPresetTargets && action.maximumTargets() > 0
			? RequestKind.TARGETS
			: RequestKind.PREPARE;
		boolean terminal = ClientConsoleState.lastResult()
			.filter(result -> result.kind() == expected)
			.flatMap(result -> result.operationId())
			.filter(action.operationId()::equals)
			.isPresent();
		if (terminal || ClientConsoleState.feedbackError()) {
			this.routingAction = null;
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
