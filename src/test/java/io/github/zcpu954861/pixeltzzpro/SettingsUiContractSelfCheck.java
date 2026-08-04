package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Static contract check for the client-only three-level local settings surface. */
public final class SettingsUiContractSelfCheck {
	private static final Path ENTRY_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/OptionsMenuEntry.java"
	);
	private static final Path CATEGORY_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/ModSettingsScreen.java"
	);
	private static final Path TEXT_EFFECT_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/MessageAccessibilityScreen.java"
	);
	private static final Path ZH_CN = Path.of(
		"src/main/resources/assets/pixel-tzz-pro/lang/zh_cn.json"
	);
	private static final Path EN_US = Path.of(
		"src/main/resources/assets/pixel-tzz-pro/lang/en_us.json"
	);

	private static final List<String> REQUIRED_KEYS = List.of(
		"pixel_tzz_pro.menu.settings",
		"pixel_tzz_pro.menu.settings.tooltip",
		"pixel_tzz_pro.settings.title",
		"pixel_tzz_pro.settings.subtitle",
		"pixel_tzz_pro.settings.description",
		"pixel_tzz_pro.settings.local_notice",
		"pixel_tzz_pro.settings.text_effects",
		"pixel_tzz_pro.settings.text_effects.eyebrow",
		"pixel_tzz_pro.settings.text_effects.description",
		"pixel_tzz_pro.settings.controls",
		"pixel_tzz_pro.settings.controls.eyebrow",
		"pixel_tzz_pro.settings.controls.description",
		"pixel_tzz_pro.settings.open",
		"pixel_tzz_pro.settings.back",
		"pixel_tzz_pro.settings.back_to_categories",
		"pixel_tzz_pro.settings.text_effects.subtitle",
		"pixel_tzz_pro.settings.text_effects.animation_speed",
		"pixel_tzz_pro.settings.text_effects.animation_speed.description",
		"pixel_tzz_pro.settings.text_effects.reduced_motion",
		"pixel_tzz_pro.settings.text_effects.reduced_motion.description",
		"pixel_tzz_pro.settings.text_effects.character_sound",
		"pixel_tzz_pro.settings.text_effects.character_sound.description",
		"pixel_tzz_pro.settings.text_effects.character_volume",
		"pixel_tzz_pro.settings.text_effects.character_volume.description",
		"pixel_tzz_pro.settings.text_effects.cursor_blink",
		"pixel_tzz_pro.settings.text_effects.cursor_blink.description",
		"pixel_tzz_pro.settings.text_effects.high_contrast",
		"pixel_tzz_pro.settings.text_effects.high_contrast.description",
		"pixel_tzz_pro.settings.text_effects.key_hint",
		"pixel_tzz_pro.settings.text_effects.reset",
		"pixel_tzz_pro.settings.text_effects.reset.tooltip",
		"pixel_tzz_pro.settings.toggle.on",
		"pixel_tzz_pro.settings.toggle.off",
		"key.category.pixel-tzz-pro.message",
		"key.pixel_tzz_pro.message.complete"
	);

	private SettingsUiContractSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		String entry = normalized(Files.readString(ENTRY_SOURCE));
		String categories = normalized(Files.readString(CATEGORY_SOURCE));
		String textEffects = normalized(Files.readString(TEXT_EFFECT_SOURCE));

		check(
			entry.contains("client.gui.setScreen(new ModSettingsScreen(screen))"),
			"the vanilla Options entry must open the mod settings category page"
		);
		check(
			occurrences(categories, "new CategoryButton(") == 2,
			"the category page must expose exactly two category cards"
		);
		check(
			categories.contains("CARD_X + CARD_WIDTH + CARD_GAP, CARD_Y"),
			"the category cards must share one row in two columns"
		);
		check(
			categories.contains("new MessageAccessibilityScreen(this)"),
			"the text-effects card must open the dedicated third-level page"
		);
		check(
			categories.contains("new KeyBindsScreen(this, this.minecraft.options)"),
			"the controls card must open vanilla key bindings with the category page as parent"
		);
		check(
			categories.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& textEffects.contains("ConsoleScreenViewport.fit(this.width, this.height)"),
			"both custom settings pages must use the shared 640x360 proportional viewport"
		);
		check(
			categories.contains("void onClose() { this.minecraft.gui.setScreen(this.parent); }")
				&& textEffects.contains("void onClose() { this.minecraft.gui.setScreen(this.parent); }"),
			"ESC and visible Back actions must return exactly one level"
		);
		for (String field : List.of(
			"animationSpeed()",
			"reducedMotion()",
			"characterSound()",
			"characterSoundVolume()",
			"cursorBlink()",
			"highContrast()"
		)) {
			check(textEffects.contains(field), "missing existing local preference: " + field);
		}
		check(
			textEffects.contains(
				"graphics.textWithWordWrap( this.font, Component.translatable(prefix + \".description\"), x + 10, y + 29, CARD_WIDTH - 120"
			),
			"setting descriptions must wrap inside the bounded left copy column"
		);

		JsonObject zhCn = parseObject(ZH_CN);
		JsonObject enUs = parseObject(EN_US);
		check(
			"全员逃走中-设置".equals(zhCn.get("pixel_tzz_pro.menu.settings").getAsString()),
			"the Chinese vanilla entry label must match the approved wording"
		);
		for (String key : REQUIRED_KEYS) {
			check(zhCn.has(key), "missing zh_cn translation: " + key);
			check(enUs.has(key), "missing en_us translation: " + key);
		}

		System.out.println("SETTINGS_UI_CONTRACT_SELF_CHECK=PASS");
	}

	private static JsonObject parseObject(final Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static int occurrences(final String source, final String needle) {
		int count = 0;
		int cursor = 0;
		while ((cursor = source.indexOf(needle, cursor)) >= 0) {
			count++;
			cursor += needle.length();
		}
		return count;
	}

	private static String normalized(final String source) {
		return source.replaceAll("\\s+", " ").trim();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
