package io.github.zcpu954861.pixeltzzpro.client.screen;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_BOTTOM;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BACKGROUND_TOP;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.BRAND_GOLD;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.PANEL_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_RAISED;

import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton;
import io.github.zcpu954861.pixeltzzpro.client.ui.widget.ConsoleButton.Variant;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.ConsoleScreenViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Two-column category index for every local Pixel TZZ preference family. */
public final class ModSettingsScreen extends Screen {
	private static final int PANEL_X = 54;
	private static final int PANEL_Y = 32;
	private static final int PANEL_WIDTH = 532;
	private static final int PANEL_HEIGHT = 296;
	private static final int CARD_X = PANEL_X + 14;
	private static final int CARD_Y = 106;
	private static final int CARD_WIDTH = 248;
	private static final int CARD_HEIGHT = 78;
	private static final int CARD_GAP = 8;

	private final Screen parent;

	public ModSettingsScreen(final Screen parent) {
		super(Component.translatable("pixel_tzz_pro.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		CategoryButton textEffects = new CategoryButton(
			CARD_X,
			CARD_Y,
			CARD_WIDTH,
			CARD_HEIGHT,
			Component.translatable("pixel_tzz_pro.settings.text_effects"),
			Component.translatable("pixel_tzz_pro.settings.text_effects.eyebrow"),
			Component.translatable("pixel_tzz_pro.settings.text_effects.description"),
			"Aa▌",
			INFO_CYAN,
			button -> this.minecraft.gui.setScreen(new MessageAccessibilityScreen(this))
		);
		textEffects.setTooltip(
			Tooltip.create(Component.translatable("pixel_tzz_pro.settings.text_effects.tooltip"))
		);
		this.addRenderableWidget(textEffects);

		CategoryButton controls = new CategoryButton(
			CARD_X + CARD_WIDTH + CARD_GAP,
			CARD_Y,
			CARD_WIDTH,
			CARD_HEIGHT,
			Component.translatable("pixel_tzz_pro.settings.controls"),
			Component.translatable("pixel_tzz_pro.settings.controls.eyebrow"),
			Component.translatable("pixel_tzz_pro.settings.controls.description"),
			"KEY",
			BRAND_GOLD,
			button -> this.minecraft.gui.setScreen(
				new KeyBindsScreen(this, this.minecraft.options)
			)
		);
		controls.setTooltip(
			Tooltip.create(Component.translatable("pixel_tzz_pro.settings.controls.tooltip"))
		);
		this.addRenderableWidget(controls);

		CategoryButton hud = new CategoryButton(
			CARD_X,
			CARD_Y + CARD_HEIGHT + CARD_GAP,
			CARD_WIDTH,
			CARD_HEIGHT,
			Component.translatable("pixel_tzz_pro.settings.hud"),
			Component.translatable("pixel_tzz_pro.settings.hud.eyebrow"),
			Component.translatable("pixel_tzz_pro.settings.hud.description"),
			"HUD",
			SUCCESS,
			button -> this.minecraft.gui.setScreen(new HudSettingsScreen(this))
		);
		hud.setTooltip(Tooltip.create(Component.translatable("pixel_tzz_pro.settings.hud.tooltip")));
		this.addRenderableWidget(hud);

		ConsoleButton back = new ConsoleButton(
			PANEL_X + PANEL_WIDTH - 132,
			PANEL_Y + PANEL_HEIGHT - 34,
			118,
			22,
			Component.translatable("pixel_tzz_pro.settings.back"),
			button -> onClose(),
			Variant.PRIMARY
		);
		this.addRenderableWidget(back);
		this.setInitialFocus(textEffects);
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
			drawPanel(graphics);
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

	private void drawPanel(final GuiGraphicsExtractor graphics) {
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, PANEL);
		graphics.outline(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + 4, PANEL_Y + PANEL_HEIGHT, BRAND_GOLD);
		graphics.fill(PANEL_X + 4, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + 2, INFO_CYAN);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.subtitle"),
			PANEL_X + 18,
			PANEL_Y + 15,
			INFO_CYAN,
			false
		);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.title"),
			PANEL_X + 18,
			PANEL_Y + 36,
			MAIN_TEXT,
			true
		);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.description"),
			PANEL_X + 18,
			PANEL_Y + 58,
			PANEL_WIDTH - 36,
			SECONDARY_TEXT,
			false
		);
		graphics.fill(
			PANEL_X + 18,
			PANEL_Y + 91,
			PANEL_X + PANEL_WIDTH - 18,
			PANEL_Y + 92,
			SURFACE_BORDER
		);
		graphics.fill(
			PANEL_X + 18,
			PANEL_Y + 91,
			PANEL_X + 112,
			PANEL_Y + 92,
			INFO_CYAN
		);
		graphics.text(
			this.font,
			Component.translatable("pixel_tzz_pro.settings.local_notice"),
			PANEL_X + 18,
			PANEL_Y + PANEL_HEIGHT - 27,
			MUTED_TEXT,
			false
		);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return super.mouseClicked(referenceMouseEvent(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return super.mouseReleased(referenceMouseEvent(event));
	}

	@Override
	public boolean mouseDragged(
		final MouseButtonEvent event,
		final double deltaX,
		final double deltaY
	) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return super.mouseDragged(
			referenceMouseEvent(event),
			canvas.toReferenceLength(deltaX),
			canvas.toReferenceLength(deltaY)
		);
	}

	private MouseButtonEvent referenceMouseEvent(final MouseButtonEvent event) {
		ConsoleScreenViewport.Layout canvas = canvas();
		return new MouseButtonEvent(
			canvas.toReferenceX(event.x()),
			canvas.toReferenceY(event.y()),
			event.buttonInfo()
		);
	}

	private ConsoleScreenViewport.Layout canvas() {
		return ConsoleScreenViewport.fit(this.width, this.height);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private static final class CategoryButton extends Button {
		private final Component eyebrow;
		private final Component description;
		private final String icon;
		private final int accent;

		private CategoryButton(
			final int x,
			final int y,
			final int width,
			final int height,
			final Component title,
			final Component eyebrow,
			final Component description,
			final String icon,
			final int accent,
			final OnPress onPress
		) {
			super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
			this.eyebrow = eyebrow;
			this.description = description;
			this.icon = icon;
			this.accent = accent;
		}

		@Override
		protected void extractContents(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float tickProgress
		) {
			boolean highlighted = this.active && (isHovered() || isFocused());
			int background = this.active
				? highlighted ? SURFACE_RAISED : SURFACE
				: 0xFF151E27;
			int border = this.active
				? highlighted ? this.accent : SURFACE_BORDER
				: 0xFF2B3945;
			int text = this.active ? MAIN_TEXT : MUTED_TEXT;
			var font = Minecraft.getInstance().font;
			graphics.fill(getX(), getY(), getRight(), getBottom(), background);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
			graphics.fill(getX(), getY(), getX() + 3, getBottom(), this.accent);
			if (highlighted) {
				graphics.fill(getX() + 3, getY() + 1, getRight() - 1, getY() + 2, this.accent);
			}

			int iconX = getX() + 15;
			int iconY = getY() + 12;
			int iconSize = Math.min(52, getHeight() - 24);
			graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF17212B);
			graphics.outline(iconX, iconY, iconSize, iconSize, this.accent);
			graphics.fill(iconX + 5, iconY + 5, iconX + 7, iconY + iconSize - 5, this.accent);
			int iconTextX = iconX + Math.max(0, (iconSize - font.width(this.icon)) / 2) + 2;
			graphics.text(font, this.icon, iconTextX, iconY + Math.max(4, (iconSize - font.lineHeight) / 2), this.accent, true);

			int copyX = getX() + 82;
			int copyWidth = getWidth() - 96;
			graphics.text(font, this.eyebrow, copyX, getY() + 10, this.accent, false);
			graphics.text(font, getMessage(), copyX, getY() + 26, text, true);
			graphics.textWithWordWrap(
				font,
				this.description,
				copyX,
				getY() + 43,
				copyWidth,
				this.active ? SECONDARY_TEXT : MUTED_TEXT,
				false
			);
			if (getHeight() >= 96) {
				graphics.text(
					font,
					Component.translatable("pixel_tzz_pro.settings.open"),
					copyX,
					getBottom() - 18,
					highlighted ? this.accent : MUTED_TEXT,
					false
				);
			}
			handleCursor(graphics);
		}
	}
}
