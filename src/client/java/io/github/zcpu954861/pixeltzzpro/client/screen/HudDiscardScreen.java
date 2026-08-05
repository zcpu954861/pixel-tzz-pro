package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;

import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Explicit leave protection for an unsaved HUD draft. */
final class HudDiscardScreen extends Screen {
	private static final int X = 136;
	private static final int Y = 104;
	private static final int WIDTH = 368;
	private static final int HEIGHT = 152;

	private final Screen returnTo;
	private final Screen exitTo;
	private final HudSettingsSession session;

	HudDiscardScreen(final Screen returnTo, final Screen exitTo, final HudSettingsSession session) {
		super(Component.translatable("pixel_tzz_pro.settings.hud.discard.title"));
		this.returnTo = returnTo;
		this.exitTo = exitTo;
		this.session = session;
	}

	@Override
	protected void init() {
		ConsoleButton keep = new ConsoleButton(
			X + 18,
			Y + HEIGHT - 40,
			154,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.discard.keep"),
			ignored -> this.minecraft.gui.setScreen(this.returnTo),
			Variant.NORMAL
		);
		this.addRenderableWidget(keep);
		ConsoleButton discard = new ConsoleButton(
			X + WIDTH - 172,
			Y + HEIGHT - 40,
			154,
			22,
			Component.translatable("pixel_tzz_pro.settings.hud.discard.confirm"),
			ignored -> {
				this.session.discard();
				this.session.close();
				this.minecraft.gui.setScreen(this.exitTo);
			},
			Variant.DANGER
		);
		this.addRenderableWidget(discard);
		this.setInitialFocus(keep);
	}

	@Override
	public void extractBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
	}

	@Override
	public void extractRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float tickProgress
	) {
		ConsoleScreenViewport.Layout canvas = canvas();
		graphics.pose().pushMatrix();
		graphics.pose().translate(canvas.x(), canvas.y());
		graphics.pose().scale((float)canvas.scale(), (float)canvas.scale());
		try {
			graphics.fill(X, Y, X + WIDTH, Y + HEIGHT, PANEL);
			graphics.outline(X, Y, WIDTH, HEIGHT, PANEL_BORDER);
			graphics.fill(X, Y, X + 4, Y + HEIGHT, BRAND_GOLD);
			graphics.text(this.font, getTitle(), X + 20, Y + 20, MAIN_TEXT, true);
			graphics.textWithWordWrap(
				this.font,
				Component.translatable("pixel_tzz_pro.settings.hud.discard.description"),
				X + 20,
				Y + 47,
				WIDTH - 40,
				SECONDARY_TEXT,
				false
			);
			super.extractRenderState(
				graphics,
				(int)Math.floor(canvas.toReferenceX(mouseX)),
				(int)Math.floor(canvas.toReferenceY(mouseY)),
				tickProgress
			);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return super.mouseClicked(reference(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return super.mouseReleased(reference(event));
	}

	private MouseButtonEvent reference(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return new MouseButtonEvent(canvas.toReferenceX(event.x()), canvas.toReferenceY(event.y()), event.buttonInfo());
	}

	private ConsoleScreenViewport.Layout canvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.returnTo);
	}
}
