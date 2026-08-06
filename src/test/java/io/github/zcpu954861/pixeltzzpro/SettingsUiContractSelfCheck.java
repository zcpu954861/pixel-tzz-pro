package io.github.zcpu954861.pixeltzzpro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static contract check for local settings and the non-hideable V3C countdown surface. */
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
	private static final Path COUNTDOWN_SCREEN_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/CountdownSettingsScreen.java"
	);
	private static final Path COUNTDOWN_PREFERENCES_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/ClientCountdownPreferences.java"
	);
	private static final Path COUNTDOWN_FORMATTER_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/CountdownTimeFormatter.java"
	);
	private static final Path COUNTDOWN_RENDERER_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/CountdownRenderer.java"
	);
	private static final Path COUNTDOWN_RUNTIME_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/ClientCountdownRuntime.java"
	);
	private static final Path COUNTDOWN_OVERLAY_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/countdown/CountdownOverlay.java"
	);
	private static final Path CLIENT_ENTRYPOINT_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/PixelTzzProClient.java"
	);
	private static final Path CONTROL_CONSOLE_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/ControlConsoleScreen.java"
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
		"pixel_tzz_pro.settings.countdown",
		"pixel_tzz_pro.settings.countdown.eyebrow",
		"pixel_tzz_pro.settings.countdown.description",
		"pixel_tzz_pro.settings.countdown.tooltip",
		"pixel_tzz_pro.settings.countdown.subtitle",
		"pixel_tzz_pro.settings.countdown.motion",
		"pixel_tzz_pro.settings.countdown.motion.description",
		"pixel_tzz_pro.settings.countdown.motion.full",
		"pixel_tzz_pro.settings.countdown.motion.simplified",
		"pixel_tzz_pro.settings.countdown.motion.static",
		"pixel_tzz_pro.settings.countdown.contrast",
		"pixel_tzz_pro.settings.countdown.contrast.description",
		"pixel_tzz_pro.settings.countdown.volume",
		"pixel_tzz_pro.settings.countdown.volume.description",
		"pixel_tzz_pro.settings.countdown.required",
		"pixel_tzz_pro.settings.countdown.reset",
		"pixel_tzz_pro.settings.countdown.reset.tooltip",
		"pixel_tzz_pro.settings.countdown.preview",
		"pixel_tzz_pro.settings.countdown.preview.live",
		"pixel_tzz_pro.settings.countdown.preview.state.running",
		"pixel_tzz_pro.settings.countdown.preview.state.paused",
		"pixel_tzz_pro.settings.countdown.preview.state.waiting",
		"pixel_tzz_pro.settings.countdown.preview.state.completed",
		"pixel_tzz_pro.settings.countdown.preview.state.tooltip",
		"pixel_tzz_pro.settings.countdown.preview.actionbar",
		"key.category.pixel-tzz-pro.message",
		"key.pixel_tzz_pro.message.complete"
	);

	private SettingsUiContractSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		String entry = normalized(Files.readString(ENTRY_SOURCE));
		String categories = normalized(Files.readString(CATEGORY_SOURCE));
		String textEffects = normalized(Files.readString(TEXT_EFFECT_SOURCE));
		String countdownScreen = normalized(Files.readString(COUNTDOWN_SCREEN_SOURCE));
		String countdownPreferences = normalized(Files.readString(COUNTDOWN_PREFERENCES_SOURCE));
		String countdownFormatter = normalized(Files.readString(COUNTDOWN_FORMATTER_SOURCE));
		String countdownRenderer = normalized(Files.readString(COUNTDOWN_RENDERER_SOURCE));
		String countdownRuntime = normalized(Files.readString(COUNTDOWN_RUNTIME_SOURCE));
		String countdownOverlay = normalized(Files.readString(COUNTDOWN_OVERLAY_SOURCE));
		String clientEntrypoint = normalized(Files.readString(CLIENT_ENTRYPOINT_SOURCE));
		String controlConsole = normalized(Files.readString(CONTROL_CONSOLE_SOURCE));

		checkNavigation(entry, categories, textEffects, countdownScreen);
		checkTextEffectSettings(textEffects);
		checkCountdownPreferences(countdownPreferences, countdownScreen);
		checkCountdownTimingAndMotion(
			countdownFormatter,
			countdownRenderer,
			countdownRuntime,
			countdownOverlay,
			clientEntrypoint
		);
		checkControlConsoleQuickActions(controlConsole);
		checkTranslations();

		System.out.println("SETTINGS_UI_CONTRACT_SELF_CHECK=PASS");
	}

	private static void checkNavigation(
		final String entry,
		final String categories,
		final String textEffects,
		final String countdownScreen
	) {
		check(
			entry.contains("client.gui.setScreen(new ModSettingsScreen(screen))"),
			"the vanilla Options entry must open the mod settings category page"
		);
		check(
			occurrences(categories, "new CategoryButton(") == 3,
			"the category page must expose text effects, controls, and countdown cards"
		);
		check(
			categories.contains("CARD_X + CARD_WIDTH + CARD_GAP, CARD_Y")
				&& categories.contains("CARD_Y + CARD_HEIGHT + CARD_GAP"),
			"the category directory must remain a two-column grid"
		);
		check(
			categories.contains("new MessageAccessibilityScreen(this)"),
			"the text-effects card must open its dedicated third-level page"
		);
		check(
			categories.contains("new KeyBindsScreen(this, this.minecraft.options)"),
			"the controls card must open vanilla key bindings with the category page as parent"
		);
		check(
			categories.contains("new CountdownSettingsScreen(this)"),
			"the countdown card must open the dedicated local accessibility page"
		);
		check(
			categories.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& textEffects.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& countdownScreen.contains("ConsoleScreenViewport.fit(this.width, this.height)"),
			"every custom settings page must use the shared proportional viewport"
		);
		check(
			categories.contains("void onClose() { this.minecraft.gui.setScreen(this.parent); }")
				&& textEffects.contains("void onClose() { this.minecraft.gui.setScreen(this.parent); }")
				&& countdownScreen.contains("void onClose() { this.minecraft.gui.setScreen(this.parent); }"),
			"ESC and visible Back actions must return exactly one level"
		);
	}

	private static void checkTextEffectSettings(final String textEffects) {
		for (String field : List.of(
			"animationSpeed()",
			"reducedMotion()",
			"characterSound()",
			"characterSoundVolume()",
			"cursorBlink()",
			"highContrast()"
		)) {
			check(textEffects.contains(field), "missing existing local text-effect preference: " + field);
		}
		check(
			textEffects.contains("Component.translatable(prefix + \".description\")")
				&& textEffects.contains("CARD_WIDTH - 120"),
			"text-effect descriptions must wrap inside their bounded copy column"
		);
	}

	private static void checkCountdownPreferences(
		final String preferences,
		final String screen
	) {
		check(
			preferences.contains("pixel-tzz-pro-countdown.json")
				&& preferences.contains("StandardCopyOption.ATOMIC_MOVE")
				&& preferences.contains("StandardCopyOption.REPLACE_EXISTING")
				&& preferences.contains("AtomicMoveNotSupportedException"),
			"countdown accessibility preferences must use bounded atomic local persistence"
		);
		check(
			preferences.contains("new Snapshot(Motion.FULL, false, 1.0F)")
				&& preferences.contains("enum Motion { FULL, SIMPLIFIED, STATIC;")
				&& preferences.contains("case FULL -> SIMPLIFIED")
				&& preferences.contains("case SIMPLIFIED -> STATIC")
				&& preferences.contains("case STATIC -> FULL"),
			"Full, Simplified, and Static countdown motion modes must all be available"
		);
		String snapshot = sourceBlock(preferences, "public record Snapshot(");
		check(
			snapshot.startsWith("public record Snapshot(Motion motion, boolean highContrast, float volume)")
				&& snapshot.contains("volume < 0.0F || volume > 1.0F")
				&& snapshot.contains("withMotion(final Motion value)")
				&& snapshot.contains("withHighContrast(final boolean value)")
				&& snapshot.contains("withVolume(final float value)"),
			"countdown preferences must expose only motion, high contrast, and bounded volume"
		);
		for (String forbidden : List.of(
			"visible",
			"visibility",
			"hidden",
			"showCountdown",
			"position",
			"offset",
			"opacity"
		)) {
			check(!snapshot.contains(forbidden), "countdown preferences must not expose hiding/layout field: " + forbidden);
		}

		String init = sourceBlock(screen, "protected void init()");
		check(
			init.contains("current.withMotion(current.motion().next())")
				&& init.contains("current.withHighContrast(!current.highContrast())")
				&& init.contains("current.withVolume((float)value)")
				&& init.contains("ClientCountdownPreferences.resetDefaults()"),
			"countdown settings controls must update motion, contrast, volume, and reset"
		);
		check(
			!init.contains("hide")
				&& !init.contains("visible")
				&& !init.contains("enabled")
				&& !screen.contains("settings.countdown.hide"),
			"the settings UI must not expose a countdown hiding switch"
		);
		check(
			screen.contains("private final CountdownRenderer previewRenderer = new CountdownRenderer()")
				&& screen.contains("this.previewRenderer.renderFrame(")
				&& screen.contains("ClientCountdownPreferences.snapshot()")
				&& screen.contains("pixel_tzz_pro.settings.countdown.required"),
			"the page must preview the production renderer and explain that the surface is required"
		);
		check(
			screen.contains("private enum PreviewState")
				&& screen.contains("RUNNING(\"running\", State.RUNNING)")
				&& screen.contains("PAUSED(\"paused\", State.PAUSED)")
				&& screen.contains("WAITING(\"waiting\", State.WAITING)")
				&& screen.contains("COMPLETED(\"completed\", State.COMPLETED)")
				&& screen.contains("this.previewState = this.previewState.next()")
				&& screen.contains("previewSnapshot(now, this.previewState, remainingTicks)")
				&& !screen.contains("ClientPlayNetworking"),
			"the local preview must expose every lifecycle state without sending a server request"
		);
	}

	private static void checkCountdownTimingAndMotion(
		final String formatter,
		final String renderer,
		final String runtime,
		final String overlay,
		final String clientEntrypoint
	) {
		check(
			formatter.contains("case 0 -> 1L")
				&& formatter.contains("case 1 -> 10L")
				&& formatter.contains("case 2 -> 100L")
				&& formatter.contains("long units = (long)Math.ceil(exactUnits - 1.0E-9D)")
				&& formatter.contains("if (clampedTicks > 0.0D && units == 0L) { units = 1L; }"),
			"seconds, tenths, and hundredths must use non-zero upward countdown quantization"
		);
		check(
			formatter.contains("int minuteWidth = Math.max(")
				&& formatter.contains("+ presentation.separator() + pad(seconds, 2)")
				&& formatter.contains("String result = integer + \".\" + pad(fraction, decimals)"),
			"formatted countdown width and separators must remain stable across boundaries"
		);

		check(
			renderer.contains("boolean animate = afterSlot.digit() && beforeSlot.digit()")
				&& renderer.contains("if (!animate) { next.add(null); continue; }"),
			"only numeric slots whose value changed may animate; punctuation and stable digits must remain still"
		);
		check(
			renderer.contains("if (beforeSlot.equals(afterSlot))")
				&& renderer.contains("animationStillTargetsLatest( previous, afterSlot, nowNanos, previousDurationMs )")
				&& renderer.contains("animation.incoming() != latest.digitCharacter()")
				&& renderer.contains("elapsedNanos < 0L || elapsedNanos < durationMs * 1_000_000L"),
			"whole-second reels must survive fractional direct updates only while they still target the latest digit"
		);
		Matcher supplementarySeparator = Pattern.compile("\\X").matcher("💠");
		check(
			supplementarySeparator.find()
				&& "💠".equals(supplementarySeparator.group())
				&& !supplementarySeparator.find()
				&& renderer.contains("private static final Pattern GRAPHEME_PATTERN = Pattern.compile(\"\\\\X\")")
				&& renderer.contains("String value = graphemes.group()")
				&& renderer.contains("timeCellWidth(font, target.text())")
				&& renderer.contains("graphics.text(font, text"),
			"a supplementary-plane separator must remain one static grapheme slot from measurement through drawing"
		);
		check(
			renderer.contains("int quantumMs = decimalPlaces == 1 ? 100 : 10")
				&& renderer.contains("return Math.min(configured, quantumMs)"),
			"fractional reels must finish within their 0.1s or 0.01s display quantum"
		);
		check(
			renderer.contains("long frameIntervalNanos = this.lastPrepareNanos == Long.MIN_VALUE")
				&& renderer.contains("if (durationMs <= 0 || frameIntervalNanos >= durationMs * 1_000_000L)")
				&& !renderer.contains("fractional && ( frameIntervalNanos"),
			"low-FPS updates must settle every changed digit on the newest value without replaying stale seconds"
		);
		check(
			renderer.contains("for (char digit = '0'; digit <= '9'; digit++)")
				&& renderer.contains("? digitCellWidth(font)")
				&& renderer.contains("int centeredX = localX + Math.max(0, (cellWidth - glyphWidth) / 2)"),
			"all numeric slots must use the widest resource-pack digit cell and center both reel glyphs"
		);
		check(
			renderer.contains("fittedTimeScale(configuredTimeScale, timeLocalWidth, Math.max(1, timeBlockAvailable))")
				&& renderer.contains("Math.min(configuredScale, Math.max(1, availableWidth) / (float)localWidth)")
				&& renderer.contains("int timerWidth = showTimer ? scaledTimeWidth(timeLocalWidth, timeScale) : 0")
				&& renderer.contains("Math.max(0, (panelWidth - 4) / 2)"),
			"the required timer must safely shrink inside the bounded panel after optional copy is removed"
		);
		check(
			renderer.contains("if (font.width(value) <= width) { return value; }")
				&& renderer.contains("font.substrByWidth(value, Math.max(0, width - ellipsisWidth))")
				&& renderer.contains("result.append(Component.literal(text).withStyle(style))"),
			"rich countdown copy must retain nested styles when truncated and preserve the original when it fits"
		);
		check(
			renderer.contains("? HIGH_CONTRAST_FOREGROUND")
				&& occurrences(renderer, "? HIGH_CONTRAST_ACCENT") >= 2
				&& renderer.contains("? HIGH_CONTRAST_TRACK")
				&& renderer.contains("style.withColor(color & 0x00FF_FFFF)"),
			"high contrast must override completion, waiting, checkpoint/status, progress, and track colors"
		);
		check(
			renderer.contains("frame.settleGeneration() != this.settleGeneration")
				&& renderer.contains("motion == Motion.STATIC")
				&& renderer.contains("preferences.motion() == Motion.SIMPLIFIED"),
			"hard settle, Static, and Simplified behavior must remain distinct"
		);
		check(
			runtime.contains("small clock error is slewed")
				&& runtime.contains("settleGeneration++")
				&& runtime.contains("double remaining = Math.clamp(clock.remainingAt(nowNanos), 0.0D, active.totalTicks())")
				&& runtime.contains("this.rate * elapsed / TICK_NANOS"),
			"the client runtime must interpolate from the authoritative clock and hard-settle discontinuities"
		);
		String replace = sourceBlock(
			runtime,
			"public static synchronized FrameAcceptance acceptReplace("
		);
		String patch = sourceBlock(
			runtime,
			"public static synchronized FrameAcceptance acceptPatch("
		);
		check(
			patch.contains("stateChanged || pauseChanged || update.settleDigits()")
				&& runtime.contains("MAX_ANIMATABLE_PATCH_CORRECTION_TICKS = 20.0D")
				&& runtime.contains("snapshot.state() == State.RUNNING")
				&& runtime.contains("clock.rate() == 0.0D && rate < 0.0D")
				&& runtime.contains("!boundedRunningCorrection"),
			"one bounded running Patch may preserve the first reel transition while lifecycle and large corrections still settle"
		);
		check(
			runtime.contains("private static Tombstone tombstone")
				&& replace.contains("tombstone != null && !versionIsStrictlyNewer(version, tombstone.version())")
				&& replace.contains("A strictly newer full Replace is also the server's explicit re-authorization grant")
				&& replace.contains("tombstone = null")
				&& runtime.contains("context > 0 || context == 0 && candidate.sequence() > current.sequence()")
				&& patch.contains("Patch has no countdown identity and therefore cannot replace a Clear tombstone")
				&& runtime.contains("tombstone = new Tombstone(clear)")
				&& runtime.contains("long appliedSequence = matchingEnvelope ? appliedVersion.sequence() : 0L")
				&& !runtime.contains("retiredCountdownInstanceId"),
			"Clear must reject old frames while a higher-sequence full Replace can re-authorize the same instance and admit later Patch"
		);

		check(
			overlay.contains("VanillaHudElements.OVERLAY_MESSAGE")
				&& overlay.contains("int actionBarTextTop = graphics.guiHeight() - 72")
				&& overlay.contains("gapAboveActionBar()")
				&& !overlay.contains("TITLE_AND_SUBTITLE"),
			"the one countdown surface must remain fixed immediately above ActionBar without taking Title/Subtitle"
		);
		check(
			clientEntrypoint.contains("ClientCountdownPreferences.initialize()")
				&& clientEntrypoint.contains("CountdownOverlay.register()")
				&& occurrences(clientEntrypoint, "ClientCountdownRuntime.reset()") >= 2
				&& occurrences(clientEntrypoint, "CountdownOverlay.reset()") >= 2,
			"countdown preferences, overlay, and connection-boundary reset must be wired at the client entrypoint"
		);
	}

	private static void checkControlConsoleQuickActions(final String console) {
		String recommended = sourceBlock(
			console,
			"private static Optional<ActionEntry> recommendedAction("
		);
		String quick = sourceBlock(
			console,
			"private static List<ActionEntry> contextQuickActions("
		);
		String contextual = sourceBlock(
			console,
			"private static boolean contextualAction("
		);
		String catalogOnly = sourceBlock(
			console,
			"private static boolean catalogOnlyAction("
		);
		String flowQuick = sourceBlock(
			console,
			"private static boolean activeFlowQuickAction("
		);
		String countdownRecovery = sourceBlock(
			console,
			"private static boolean countdownRecoveryQuickAction("
		);
		String priority = sourceBlock(
			console,
			"private static int contextPriority("
		);
		String builtInPriority = sourceBlock(
			console,
			"private static int builtInContextPriority("
		);
		String dispatch = sourceBlock(
			console,
			"private void dispatchQuickAction("
		);

		check(
			recommended.contains(".filter(ActionEntry::enabled)")
				&& recommended.contains(".filter(ControlConsoleScreen::contextualAction)")
				&& recommended.contains("contextPriority(snapshot, action)")
				&& recommended.contains(".thenComparingInt(ActionEntry::order)"),
			"the primary host action must be enabled, contextual, and deterministically ordered"
		);
		check(
			quick.contains(".filter(ActionEntry::enabled)")
				&& quick.contains(".filter(ControlConsoleScreen::contextualAction)")
				&& quick.contains("activeFlowQuickAction(action)")
				&& quick.contains("countdownRecoveryQuickAction(action)")
				&& quick.contains(".limit(2)"),
			"the bounded quick zone must retain flow and countdown recovery actions"
		);
		check(
			contextual.contains("return !catalogOnlyAction(action)")
				&& catalogOnly.contains("CLEAR_ALL_STATE_ACTION")
				&& catalogOnly.contains("INTERRUPT_TIMELINE_ACTION")
				&& !quick.contains("CLEAR_ALL_STATE_ACTION")
				&& !builtInPriority.contains("CLEAR_ALL_STATE_ACTION"),
			"clear-all and whole-game interruption must remain catalog-only actions"
		);
		for (String operation : List.of(
			"RETRY_COUNTDOWN_HANDOFF_ACTION",
			"ABANDON_COUNTDOWN_HANDOFF_ACTION",
			"CANCEL_COUNTDOWN_ACTION",
			"RETRY_COUNTDOWN_CALLBACKS_ACTION",
			"RETRY_UNKNOWN_COUNTDOWN_CALLBACKS_ACTION",
			"APPROVE_TIMELINE_ACTION",
			"APPROVE_DEGRADED_TIMELINE_ACTION",
			"RESUME_TIMELINE_ACTION",
			"RETRY_TIMELINE_CALLBACK_ACTION"
		)) {
			check(
				builtInPriority.contains("case " + operation + " ->"),
				"missing built-in host quick-action priority: " + operation
			);
		}
		check(
			before(builtInPriority, "case RETRY_COUNTDOWN_HANDOFF_ACTION", "case ABANDON_COUNTDOWN_HANDOFF_ACTION")
				&& before(builtInPriority, "case CANCEL_COUNTDOWN_ACTION", "case RETRY_COUNTDOWN_CALLBACKS_ACTION")
				&& before(builtInPriority, "case RETRY_COUNTDOWN_CALLBACKS_ACTION", "case RETRY_UNKNOWN_COUNTDOWN_CALLBACKS_ACTION")
				&& before(builtInPriority, "case APPROVE_TIMELINE_ACTION", "case APPROVE_DEGRADED_TIMELINE_ACTION"),
			"countdown recovery, cancellation, callback, and opening fallback order changed"
		);
		check(
			flowQuick.contains("ADD_FLOW_MEMBERS_ACTION")
				&& flowQuick.contains("REMOVE_FLOW_MEMBERS_ACTION")
				&& flowQuick.contains("RETRY_FLOW_CALLBACK_ACTION")
				&& flowQuick.contains("CANCEL_FLOW_ACTION")
				&& countdownRecovery.contains("CANCEL_COUNTDOWN_ACTION")
				&& countdownRecovery.contains("RETRY_COUNTDOWN_HANDOFF_ACTION")
				&& countdownRecovery.contains("ABANDON_COUNTDOWN_HANDOFF_ACTION"),
			"active-flow and countdown-recovery quick-action membership is incomplete"
		);
		check(
			priority.contains("int builtInPriority = builtInContextPriority(action)")
				&& priority.contains("if (builtInPriority >= 0) { return builtInPriority; }")
				&& priority.contains("return 100 + sectionPriority(action.section())"),
			"built-in context controls must outrank data-pack section/order fallback"
		);
		check(
			dispatch.contains("Optional<ActionEntry> action = visibleConsole")
				&& dispatch.contains("dispatchAction(action.orElseThrow())")
				&& dispatch.contains("if (!ClientConsoleState.pending())")
				&& dispatch.contains("ClientConsoleState.requestConsole()"),
			"a stale quick button must visibly resynchronize instead of becoming an enabled no-op"
		);
	}

	private static void checkTranslations() throws IOException {
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
		for (String forbidden : List.of(
			"pixel_tzz_pro.settings.countdown.hide",
			"pixel_tzz_pro.settings.countdown.hidden",
			"pixel_tzz_pro.settings.countdown.visible"
		)) {
			check(!zhCn.has(forbidden) && !enUs.has(forbidden), "countdown hiding translation must not exist: " + forbidden);
		}
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

	private static boolean before(
		final String source,
		final String first,
		final String second
	) {
		int firstIndex = source.indexOf(first);
		int secondIndex = source.indexOf(second);
		return firstIndex >= 0 && secondIndex > firstIndex;
	}

	private static String normalized(final String source) {
		return source.replaceAll("\\s+", " ").trim();
	}

	private static String sourceBlock(final String source, final String signature) {
		int start = source.indexOf(signature);
		if (start < 0) {
			return "";
		}
		int opening = source.indexOf('{', start + signature.length());
		if (opening < 0) {
			return "";
		}
		int depth = 0;
		for (int cursor = opening; cursor < source.length(); cursor++) {
			char value = source.charAt(cursor);
			if (value == '{') {
				depth++;
			} else if (value == '}' && --depth == 0) {
				return source.substring(start, cursor + 1);
			}
		}
		return "";
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
