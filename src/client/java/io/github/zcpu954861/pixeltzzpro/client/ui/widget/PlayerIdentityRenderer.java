package io.github.zcpu954861.pixeltzzpro.client.ui.widget;

import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.DANGER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.INFO_CYAN;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MAIN_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.MUTED_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SECONDARY_TEXT;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SUCCESS;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.SURFACE_BORDER;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.WARNING;
import static io.github.zcpu954861.pixeltzzpro.client.ui.style.ConsolePalette.withAlpha;

import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.FlowCompletionStatus;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * One visual contract for every player identity shown by the built-in console.
 */
public final class PlayerIdentityRenderer {
	private PlayerIdentityRenderer() {
	}

	public static void drawTargetRow(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Target target,
		final int x,
		final int y,
		final int width,
		final int height,
		final boolean selected
	) {
		drawTargetRow(graphics, font, target, x, y, width, height, selected, null, 0);
	}

	public static void drawTargetRow(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Target target,
		final int x,
		final int y,
		final int width,
		final int height,
		final boolean selected,
		final String detailOverride,
		final int detailColorOverride
	) {
		int accent = selected ? INFO_CYAN : roleColor(target);
		graphics.outline(x, y, width, height, withAlpha(accent, selected ? 210 : 112));
		graphics.fill(x, y, x + 3, y + height, accent);
		int headSize = Math.clamp(height - 8, 16, 24);
		int headY = y + (height - headSize) / 2;
		drawHead(graphics, target.playerId(), target.online(), x + 9, headY, headSize);

		int textX = x + 17 + headSize;
		String online = target.online() ? "在线" : "离线";
		int onlineColor = target.online() ? SUCCESS : MUTED_TEXT;
		int onlineX = x + width - 9 - font.width(online);
		graphics.text(font, online, onlineX, y + 5, onlineColor, false);
		graphics.text(
			font,
			ellipsize(font, target.name(), Math.max(24, onlineX - textX - 8)),
			textX,
			y + 5,
			target.online() ? MAIN_TEXT : MUTED_TEXT,
			true
		);

		String role = plainRole(target);
		String completion = completionLabel(target);
		String detail = detailOverride == null
			? completion.isEmpty() ? role : role + "  ·  " + completion
			: detailOverride;
		graphics.text(
			font,
			ellipsize(font, detail, Math.max(24, width - (textX - x) - 9)),
			textX,
			y + height - 12,
			detailOverride == null ? completionColor(target) : detailColorOverride,
			false
		);
	}

	public static void drawCompactIdentity(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final Target target,
		final int x,
		final int y,
		final int width
	) {
		drawTargetRow(graphics, font, target, x, y, width, 30, false);
	}

	public static void drawHead(
		final GuiGraphicsExtractor graphics,
		final UUID playerId,
		final boolean online,
		final int x,
		final int y,
		final int size
	) {
		drawHead(graphics, playerId, online, x, y, size, true);
	}

	public static void drawHead(
		final GuiGraphicsExtractor graphics,
		final UUID playerId,
		final boolean online,
		final int x,
		final int y,
		final int size,
		final boolean showStatus
	) {
		drawHead(graphics, playerId, online, x, y, size, showStatus, true);
	}

	/** Draws the base face and optionally the skin hat layer and online-status marker. */
	public static void drawHead(
		final GuiGraphicsExtractor graphics,
		final UUID playerId,
		final boolean online,
		final int x,
		final int y,
		final int size,
		final boolean showStatus,
		final boolean showHat
	) {
		var profile = ResolvableProfile.createUnresolved(playerId);
		var renderInfo = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile);
		if (online && showHat) {
			PlayerFaceExtractor.extractRenderState(
				graphics,
				renderInfo.playerSkin(),
				x,
				y,
				size
			);
		} else if (online) {
			drawSkinHead(
				graphics,
				renderInfo.playerSkin().body().texturePath(),
				x,
				y,
				size,
				false,
				false
			);
		} else {
			drawOfflineHead(
				graphics,
				renderInfo.playerSkin().body().texturePath(),
				x,
				y,
				size,
				showHat
			);
		}
		if (!showStatus) {
			return;
		}
		int dot = Math.max(3, size / 5);
		int dotX = x + size - dot;
		int dotY = y + size - dot;
		graphics.fill(dotX, dotY, x + size, y + size, online ? SUCCESS : MUTED_TEXT);
		graphics.outline(dotX, dotY, dot, dot, 0xFF081018);
	}

	private static void drawOfflineHead(
		final GuiGraphicsExtractor graphics,
		final Identifier texture,
		final int x,
		final int y,
		final int size,
		final boolean showHat
	) {
		drawSkinHead(graphics, texture, x, y, size, true, showHat);
	}

	private static void drawSkinHead(
		final GuiGraphicsExtractor graphics,
		final Identifier texture,
		final int x,
		final int y,
		final int size,
		final boolean grayscale,
		final boolean showHat
	) {
		graphics.blit(
			grayscale
				? io.github.zcpu954861.pixeltzzpro.client.ui.PixelTzzRenderPipelines.GUI_TEXTURED_GRAYSCALE
				: net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			texture,
			x,
			y,
			8.0F,
			8.0F,
			size,
			size,
			8,
			8,
			64,
			64,
			grayscale ? 0xFFD0D0D0 : 0xFFFFFFFF
		);
		if (!showHat) {
			return;
		}
		graphics.blit(
			grayscale
				? io.github.zcpu954861.pixeltzzpro.client.ui.PixelTzzRenderPipelines.GUI_TEXTURED_GRAYSCALE
				: net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			texture,
			x,
			y,
			40.0F,
			8.0F,
			size,
			size,
			8,
			8,
			64,
			64,
			grayscale ? 0xFFD0D0D0 : 0xFFFFFFFF
		);
	}

	public static String completionLabel(final Target target) {
		if (target.completionStatuses().isEmpty()) {
			return flowLabel(target.flowStatus());
		}
		FlowCompletionStatus status = target.completionStatuses().getFirst();
		return switch (status.status()) {
			case "current" -> "已初始化";
			case "outdated" -> "初始化需更新";
			default -> "未初始化";
		};
	}

	public static int completionColor(final Target target) {
		if (target.completionStatuses().isEmpty()) {
			return flowColor(target.flowStatus());
		}
		return switch (target.completionStatuses().getFirst().status()) {
			case "current" -> SUCCESS;
			case "outdated" -> WARNING;
			default -> SECONDARY_TEXT;
		};
	}

	private static String plainRole(final Target target) {
		String json = target.roleNameJson();
		int marker = json.indexOf("\"text\":\"");
		if (marker < 0) {
			return target.roleId().getPath();
		}
		int start = marker + 8;
		int end = json.indexOf('"', start);
		return end < 0 ? target.roleId().getPath() : json.substring(start, end);
	}

	private static int roleColor(final Target target) {
		String color = target.roleColor();
		if (color.matches("#[0-9a-fA-F]{6}")) {
			return 0xFF000000 | Integer.parseUnsignedInt(color.substring(1), 16);
		}
		return INFO_CYAN;
	}

	private static String flowLabel(final String status) {
		return switch (status) {
			case "completed" -> "流程已完成";
			case "already_complete" -> "历史已完成";
			case "in_progress" -> "流程进行中";
			case "offline" -> "流程中离线";
			case "blocked" -> "流程阻塞";
			case "removed" -> "已移出流程";
			default -> "尚无活动流程";
		};
	}

	private static int flowColor(final String status) {
		return switch (status) {
			case "completed", "already_complete" -> SUCCESS;
			case "in_progress" -> INFO_CYAN;
			case "blocked" -> DANGER;
			default -> MUTED_TEXT;
		};
	}

	public static String ellipsize(final Font font, final String value, final int maximumWidth) {
		if (font.width(value) <= maximumWidth) {
			return value;
		}
		String suffix = "...";
		int contentWidth = Math.max(0, maximumWidth - font.width(suffix));
		return font.plainSubstrByWidth(value, contentWidth) + suffix;
	}
}
