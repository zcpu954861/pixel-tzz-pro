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
	private static final Path HUD_DIRECTORY_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/HudSettingsScreen.java"
	);
	private static final Path HUD_DETAIL_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/HudSettingsDetailScreen.java"
	);
	private static final Path HUD_SESSION_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/HudSettingsSession.java"
	);
	private static final Path HUD_DISCARD_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/HudDiscardScreen.java"
	);
	private static final Path HUD_DEVELOPER_SCREEN_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/HudDeveloperPreviewScreen.java"
	);
	private static final Path HUD_DEVELOPER_SESSION_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/screen/HudDeveloperPreviewSession.java"
	);
	private static final Path HUD_PREVIEW_REQUEST_SOURCE = Path.of(
		"src/main/java/io/github/zcpu954861/pixeltzzpro/network/payload/HudPreviewRequestC2SPayload.java"
	);
	private static final Path HUD_PREFERENCES_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/hud/ClientHudPreferences.java"
	);
	private static final Path HUD_OVERLAY_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/hud/InformationDockOverlay.java"
	);
	private static final Path HUD_LAYOUT_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/hud/HudLayoutEngine.java"
	);
	private static final Path HUD_RUNTIME_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/hud/ClientHudRuntime.java"
	);
	private static final Path CLIENT_CONSOLE_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/ClientConsoleState.java"
	);
	private static final Path CLIENT_ENTRYPOINT_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/PixelTzzProClient.java"
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
		"pixel_tzz_pro.settings.hud",
		"pixel_tzz_pro.settings.hud.eyebrow",
		"pixel_tzz_pro.settings.hud.description",
		"pixel_tzz_pro.settings.hud.section.global",
		"pixel_tzz_pro.settings.hud.section.layout",
		"pixel_tzz_pro.settings.hud.section.components",
		"pixel_tzz_pro.settings.hud.section.exclusions",
		"pixel_tzz_pro.settings.hud.section.countdown",
		"pixel_tzz_pro.settings.hud.section.diagnostics",
		"pixel_tzz_pro.settings.hud.components.action.show",
		"pixel_tzz_pro.settings.hud.components.action.hide",
		"pixel_tzz_pro.settings.hud.components.action.locked",
		"pixel_tzz_pro.settings.hud.components.action.unavailable",
		"pixel_tzz_pro.settings.hud.components.action.normal",
		"pixel_tzz_pro.settings.hud.components.action.compact",
		"pixel_tzz_pro.settings.hud.components.compact",
		"pixel_tzz_pro.settings.hud.components.policy_locked",
		"pixel_tzz_pro.settings.hud.components.reorder_issue.node_unavailable",
		"pixel_tzz_pro.settings.hud.components.reorder_issue.multiple_parents",
		"pixel_tzz_pro.settings.hud.components.reorder_issue.parent_not_container",
		"pixel_tzz_pro.settings.hud.components.reorder_issue.no_same_group_peer",
		"pixel_tzz_pro.settings.hud.apply",
		"pixel_tzz_pro.settings.hud.cancel",
		"pixel_tzz_pro.settings.hud.discard.title",
		"pixel_tzz_pro.settings.hud.discard.confirm",
		"pixel_tzz_pro.settings.hud.countdown.preview_empty",
		"pixel_tzz_pro.settings.hud.developer.open",
		"pixel_tzz_pro.settings.hud.developer.title",
		"pixel_tzz_pro.settings.hud.developer.description",
		"pixel_tzz_pro.settings.hud.developer.back",
		"pixel_tzz_pro.settings.hud.developer.automatic",
		"pixel_tzz_pro.settings.hud.developer.surface.hud",
		"pixel_tzz_pro.settings.hud.developer.surface.countdown",
		"pixel_tzz_pro.settings.hud.developer.action.open",
		"pixel_tzz_pro.settings.hud.developer.action.refresh",
		"pixel_tzz_pro.settings.hud.developer.action.close",
		"pixel_tzz_pro.settings.hud.developer.state.closed",
		"pixel_tzz_pro.settings.hud.developer.state.open",
		"pixel_tzz_pro.settings.hud.developer.identity.host",
		"pixel_tzz_pro.settings.hud.developer.mode.normal",
		"pixel_tzz_pro.settings.hud.developer.boundary.normal",
		"pixel_tzz_pro.settings.hud.developer.scenario.task_running",
		"pixel_tzz_pro.settings.hud.developer.scenario.countdown_start",
		"key.category.pixel-tzz-pro.message",
		"key.pixel_tzz_pro.message.complete"
	);

	private SettingsUiContractSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		String entry = normalized(Files.readString(ENTRY_SOURCE));
		String categories = normalized(Files.readString(CATEGORY_SOURCE));
		String textEffects = normalized(Files.readString(TEXT_EFFECT_SOURCE));
		String hudDirectory = normalized(Files.readString(HUD_DIRECTORY_SOURCE));
		String hudDetail = normalized(Files.readString(HUD_DETAIL_SOURCE));
		String hudSession = normalized(Files.readString(HUD_SESSION_SOURCE));
		String hudDiscard = normalized(Files.readString(HUD_DISCARD_SOURCE));
		String hudDeveloperScreen = normalized(Files.readString(HUD_DEVELOPER_SCREEN_SOURCE));
		String hudDeveloperSession = normalized(Files.readString(HUD_DEVELOPER_SESSION_SOURCE));
		String hudPreviewRequest = normalized(Files.readString(HUD_PREVIEW_REQUEST_SOURCE));
		String hudPreferences = normalized(Files.readString(HUD_PREFERENCES_SOURCE));
		String hudOverlay = normalized(Files.readString(HUD_OVERLAY_SOURCE));
		String hudLayout = normalized(Files.readString(HUD_LAYOUT_SOURCE));
		String hudRuntime = normalized(Files.readString(HUD_RUNTIME_SOURCE));
		String clientConsole = normalized(Files.readString(CLIENT_CONSOLE_SOURCE));
		String clientEntrypoint = normalized(Files.readString(CLIENT_ENTRYPOINT_SOURCE));

		check(
			entry.contains("client.gui.setScreen(new ModSettingsScreen(screen))"),
			"the vanilla Options entry must open the mod settings category page"
		);
		check(
			occurrences(categories, "new CategoryButton(") == 3,
			"the category page must expose text, controls, and HUD category cards"
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
			categories.contains("new HudSettingsScreen(this)")
				&& categories.contains("CARD_Y + CARD_HEIGHT + CARD_GAP"),
			"the HUD category must open from the next row of the two-column directory"
		);
		check(
			categories.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& textEffects.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& hudDirectory.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& hudDetail.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& hudDiscard.contains("ConsoleScreenViewport.fit(this.width, this.height)")
				&& hudDeveloperScreen.contains("ConsoleScreenViewport.fit(this.width, this.height)"),
			"every custom settings page must use the shared 640x360 proportional viewport"
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

		for (String section : List.of(
			"GLOBAL(",
			"LAYOUT(",
			"COMPONENTS(",
			"EXCLUSIONS(",
			"COUNTDOWN(",
			"RESET_DIAGNOSTICS("
		)) {
			check(hudDetail.contains(section), "missing HUD settings section: " + section);
		}
		check(
			hudDirectory.contains("Section[] sections = Section.values()")
				&& hudDirectory.contains("new HudSettingsDetailScreen(this.parent, this.session, section)"),
			"all six HUD sections must be reachable from one two-column directory"
		);
		check(
			hudSession.contains("private Snapshot original")
				&& hudSession.contains("private Snapshot draft")
				&& hudSession.contains("key -> ClientHudPreferences.apply(key, value)")
				&& hudSession.contains("ClientHudPreferences.applyUnscoped(value)")
				&& hudDirectory.contains("new HudDiscardScreen(this, this.parent, this.session)")
				&& hudDiscard.contains("this.session.discard()"),
			"HUD settings must retain profile-owned drafts, apply to the correct owner, and confirm discard on leave"
		);
		check(
			hudSession.contains("private Context context")
				&& hudSession.contains("new ProfileKey(value.profileId(), value.profileContentVersion())")
				&& hudSession.contains("String policyFingerprint()")
				&& hudSession.contains("boolean refreshContext()")
				&& hudSession.contains("boolean profileChanged")
				&& hudSession.contains("private final LinkedHashMap<Optional<ProfileKey>, DraftState> inactiveDrafts")
				&& hudSession.contains("stashCurrentDraft();")
				&& hudSession.contains("DraftState suspended = this.inactiveDrafts.remove(next.profileKey())")
				&& hudSession.contains("this.inactiveDrafts.values().stream().anyMatch(DraftState::dirty)"),
			"an open HUD settings session must detect context changes without dropping inactive-profile drafts"
		);
		String sessionApply = sourceBlock(hudSession, "void apply()");
		check(
			sessionApply.startsWith("void apply() {")
				&& sessionApply.contains("refreshContext();")
				&& sessionApply.contains("this.inactiveDrafts.entrySet()")
				&& sessionApply.contains("persist(this.context.profileKey(), this.draft)"),
			"Apply must refresh authority and commit both the active and suspended profile drafts"
		);
		check(
			hudSession.contains("Snapshot effectiveDraft()")
				&& hudSession.contains("ClientHudPreferences.effectiveSnapshot(")
				&& hudDirectory.contains("this.session.effectiveDraft()")
				&& hudDetail.contains("this.session.effectiveDraft()")
				&& occurrences(hudDirectory, "public void tick()") == 1
				&& occurrences(hudDetail, "public void tick()") == 1
				&& hudDirectory.contains("if (this.session.refreshContext())")
				&& hudDetail.contains("if (this.session.refreshContext())"),
			"settings controls and previews must rebuild from the current effective profile policy"
		);
		check(
			inputRefreshesContext(hudDirectory, "public boolean mouseClicked(")
				&& inputRefreshesContext(hudDirectory, "public boolean mouseReleased(")
				&& inputRefreshesContext(hudDirectory, "public boolean mouseDragged(")
				&& inputRefreshesContext(hudDetail, "public boolean mouseClicked(")
				&& inputRefreshesContext(hudDetail, "public boolean mouseReleased(")
				&& inputRefreshesContext(hudDetail, "public boolean mouseDragged(")
				&& inputRefreshesContext(hudDetail, "public boolean mouseScrolled("),
			"mouse interactions must refresh live authority before changing or applying a draft"
		);
		check(
			occurrences(hudDetail, "refreshApplyButton();") >= 6
				&& hudDetail.contains("private void refreshApplyButton()")
				&& hudDetail.contains("this.apply.active = this.session.dirty()"),
			"every HUD slider must immediately refresh the Apply state"
		);
		check(
			occurrences(hudDetail, "new HudDiscardScreen(") == 1
				&& !hudDetail.contains("this.session.discard()")
				&& hudDiscard.contains("this.session.discard()"),
			"detail-page Cancel must use the shared discard confirmation instead of dropping drafts directly"
		);
		check(
			hudDeveloperSession.contains("snapshot.status() == ConnectionStatus.READY")
				&& hudDeveloperSession.contains("snapshot.currentPlayerHost()")
				&& hudDeveloperSession.contains("NetworkProtocol.isCompatible(snapshot.serverProtocolVersion())")
				&& hudDeveloperSession.contains("ClientPlayNetworking.canSend(HudPreviewRequestC2SPayload.TYPE)")
				&& hudDetail.contains("if (this.session.developerPreviewAvailable())")
				&& hudDetail.contains("new HudDeveloperPreviewScreen(this, this.session)"),
			"the development-preview entry must be hidden unless the current host has a compatible send channel"
		);
		check(
			hudDeveloperSession.contains("private final UUID previewId = UUID.randomUUID()")
				&& hudDeveloperSession.contains("private static final AtomicLong REQUEST_SEQUENCE")
				&& hudDeveloperSession.contains("long sequence = nextRequestSequence()")
				&& hudDeveloperSession.contains("this.previewId, action, surface")
				&& hudDeveloperSession.contains("private final EnumMap<Surface, SurfaceSession> surfaces")
				&& hudDeveloperScreen.contains("for (Action action : Action.values())")
				&& hudDeveloperScreen.contains("addSurfaceControls(Surface.HUD")
				&& hudDeveloperScreen.contains("addSurfaceControls(Surface.COUNTDOWN"),
			"one stable preview UUID must drive monotonic, independently controlled HUD and Countdown requests"
		);
		check(
			!hudDeveloperScreen.contains("EditBox")
				&& hudDeveloperSession.contains("record Selection( Scenario scenario, Identity identity, Mode mode, Boundary boundary )")
				&& occurrences(hudDeveloperSession, "Optional.empty()") == 4
				&& hudDeveloperSession.contains("snapshot.phaseId()")
				&& hudPreviewRequest.contains("payload deliberately has no selector, score holder, NBT/storage path, function, free-form text"),
			"the host preview UI must expose only closed presets and safe server-selected registered identifiers"
		);
		check(
			hudSession.contains("boolean previewChanged = this.developerPreview.refresh()")
				&& hudDeveloperSession.contains("send(Action.CLOSE, surface, session.selection)")
				&& hudDeveloperSession.contains("ClientHudRuntime.activatePreview(this.previewId)")
				&& hudDeveloperSession.contains("ClientHudRuntime.clearPreview(this.previewId)")
				&& hudDeveloperScreen.contains("this.session.closeDeveloperPreview()")
				&& hudDirectory.contains("this.session.close()")
				&& hudDiscard.contains("this.session.close()"),
			"preview exit, settings exit, and authority loss must close both server surfaces and clear local preview state"
		);
		check(
			hudDetail.contains("if (this.section == Section.COUNTDOWN)")
				&& hudDetail.contains("ClientHudRuntime.previewCountdownSnapshot()")
				&& hudDetail.contains("InformationDockOverlay.renderCountdownPreview(")
				&& hudDeveloperScreen.contains("ClientHudRuntime.previewCountdownSnapshot()")
				&& hudDeveloperScreen.contains("InformationDockOverlay.renderCountdownPreview("),
			"Countdown settings and host simulation must use the real frozen production Countdown renderer"
		);
		check(
			hudSession.contains("boolean toggleCompact(final String id)")
				&& hudSession.contains("boolean moveComponent(final String id, final int direction)")
				&& hudSession.contains("parents.size() != 1")
				&& hudSession.contains("parent instanceof ClientHudNode.ContainerNode")
				&& hudSession.contains("new ComponentOrderKey(parentId, control.reorderGroup().orElseThrow())")
				&& hudDetail.contains("this.session.toggleCompact(control.id())")
				&& hudDetail.contains("this.session.moveComponent(control.id(), -1)")
				&& hudDetail.contains("this.session.moveComponent(control.id(), 1)"),
			"compact and reorder controls must be real, authority-aware operations with same-parent grouping"
		);
		String scrollMethod = sourceMethod(hudDetail, "public boolean mouseScrolled(");
		check(
			scrollMethod.contains("this.section == Section.COMPONENTS")
				&& !scrollMethod.contains("Section.EXCLUSIONS")
				&& !scrollMethod.contains("Section.LAYOUT"),
			"only the component list may consume settings-page scrolling"
		);
		check(
			hudPreferences.contains("pixel-tzz-pro-hud.properties")
				&& hudPreferences.contains("StandardCopyOption.ATOMIC_MOVE")
				&& occurrences(hudPreferences, "Files.move(") == 1
				&& hudPreferences.contains("catch (java.nio.file.AtomicMoveNotSupportedException ignored)")
				&& hudPreferences.contains("Never trade a known-good file for a non-atomic overwrite")
				&& hudPreferences.contains("MAX_EXCLUSIONS = 16")
				&& hudPreferences.contains("MAX_HIDDEN_COMPONENTS = 256")
				&& hudPreferences.contains("MAX_PROFILES = 16")
				&& hudPreferences.contains("MAX_FILE_BYTES")
				&& hudPreferences.contains("record ProfileKey(String profileId, long profileContentVersion)")
				&& hudPreferences.contains("profile_content_version")
				&& hudPreferences.contains("lastUsedVersion(safeKey.profileId())")
				&& hudPreferences.contains("Old flat files remain an unscoped compatibility profile")
				&& hudPreferences.contains("Snapshot effectiveSnapshot(")
				&& hudPreferences.contains("compactComponents")
				&& hudPreferences.contains("value.compactComponents()")
				&& hudPreferences.contains("value.componentOrder()")
				&& hudPreferences.contains("public record Effective(")
				&& hudPreferences.contains("componentOrder = immutableOrder(componentOrder)")
				&& hudPreferences.contains("record ComponentOrderKey(String parentId, String reorderGroup)")
				&& hudPreferences.contains("Map<ComponentOrderKey, List<String>> reorderGroups(")
				&& hudPreferences.contains("allowed.allowedAnchors().contains(local.anchor())")
				&& hudPreferences.contains("Math.clamp(local.scale(), allowed.minimumScale(), allowed.maximumScale())")
				&& hudPreferences.contains("Math.clamp(local.opacity(), allowed.minimumOpacity(), allowed.maximumOpacity())"),
			"client HUD preferences must be profile-scoped, bounded, strictly atomically persisted, and fully exposed after policy filtering"
		);
		check(
			hudOverlay.contains("client.gui.screen() == null || client.gui.screen() instanceof ChatScreen")
				&& hudOverlay.contains("VanillaHudElements.TITLE_AND_SUBTITLE")
				&& hudOverlay.contains("client.options.keyPlayerList.isDown()")
				&& hudOverlay.contains("Optional<Rect> tabBounds")
				&& hudOverlay.contains("tabBounds.map(dockBounds::intersects)")
				&& hudOverlay.contains("TabCollision.KEEP")
				&& hudOverlay.contains("List<Rect> additionalBlocked"),
			"HUD visibility must inherit F1, remain in chat, and apply TAB policy only to real collisions"
		);
		check(
			hudRuntime.contains("ClientHudPreferences.activateProfile(")
				&& hudRuntime.contains("new ProfileKey(current.profileId(), current.profileContentVersion())")
				&& occurrences(hudRuntime, "ClientHudPreferences.deactivateProfile()") >= 2,
			"accepted HUD lifecycle changes must activate and clear the matching client preference profile"
		);
		check(
			hudOverlay.contains("personalizedNodes(")
				&& hudOverlay.contains("preferences.compactComponents()")
				&& hudOverlay.contains("preferences.componentOrder()"),
			"the production overlay must consume effective compact and component-order preferences before layout"
		);
		check(
			hudOverlay.contains("REFERENCE_WIDTH = 640")
				&& hudOverlay.contains("REFERENCE_HEIGHT = 360")
				&& hudOverlay.contains("referenceScale(screenWidth, screenHeight)")
				&& hudOverlay.contains("renderCountdown(")
				&& hudOverlay.contains("PreviewFrame frame = previewFrame("),
			"formal dock, countdown, and settings preview must share the 640x360 reference geometry"
		);
		check(
			hudOverlay.contains("snapshot.layoutVariants()")
				&& hudOverlay.contains("variant.root(mode)")
				&& hudOverlay.contains("variant.size().minimumWidth()")
				&& !hudLayout.contains("density.includes("),
			"client degradation must switch only among complete server-authorized layout roots"
		);
		check(
			hudLayout.contains("MeasureStatus.FAILURE")
				&& hudLayout.contains("private MeasureResult unmeasurable(")
				&& hudLayout.contains("return MeasureResult.failure()")
				&& hudLayout.contains("if (!visible(node))")
				&& hudLayout.contains("return MeasureResult.hidden()"),
			"only explicitly hidden nodes may disappear; every visible measurement failure must invalidate the full root"
		);
		check(
			hudOverlay.contains("renderInlinePair(")
				&& hudOverlay.contains("ellipsized(font, node.label()")
				&& hudOverlay.contains("graphics.enableScissor(entry.x()")
				&& hudLayout.contains("minimumInlineWidth("),
			"bounded HUD entries must fit or ellipsize text and retain a local clipping boundary"
		);
		check(
			hudOverlay.contains("MAX_SMOOTH_PROGRESS = 1024")
				&& hudOverlay.contains("version.definitionGeneration()")
				&& hudOverlay.contains("version.epoch()")
				&& hudOverlay.contains("+ \":\" + layoutId")
				&& occurrences(clientEntrypoint, "InformationDockOverlay.clearTransientState()") == 2,
			"progress interpolation must be layout-scoped, bounded, and cleared at both connection boundaries"
		);
		for (String node : List.of(
			"TextNode value",
			"ImageNode value",
			"PlayerHeadNode value",
			"CounterNode value",
			"BadgeNode value",
			"ProgressNode value",
			"TimerNode value",
			"SeparatorNode value",
			"BackgroundNode value",
			"ContainerNode value"
		)) {
			check(hudLayout.contains(node), "HUD layout engine does not measure: " + node);
		}
		check(
			hudRuntime.contains("countdown_instance_id")
				&& hudRuntime.contains("String rootId = string(root, \"root\"")
				&& hudRuntime.contains("Map<String, ClientHudNode> nodes = nodes(array(root, \"nodes\"))")
				&& hudOverlay.contains("countdown.layoutVariants()")
				&& hudOverlay.contains("countdown.nodes()")
				&& hudOverlay.contains("PlanCandidate candidate = planCandidates("),
			"countdown presentation must use the same generic node tree, never a hard-coded card"
		);
		check(
			clientConsole.contains("CountdownControlContract.isCountdownControl(")
				&& clientConsole.contains("value.definitionGeneration() == payload.definitionGeneration()")
				&& clientConsole.contains("unrelated global-revision pushes must not erase the page"),
			"countdown cancellation review must survive unrelated global state-revision pushes"
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
		for (String suffix : List.of("hud", "countdown")) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.surface." + suffix);
		}
		for (String suffix : List.of("open", "refresh", "close")) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.action." + suffix);
		}
		for (String suffix : List.of("closed", "opening", "open", "closing")) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.state." + suffix);
		}
		for (String suffix : List.of("host", "participant", "observer")) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.identity." + suffix);
		}
		for (String suffix : List.of("normal", "compact", "summary")) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.mode." + suffix);
		}
		for (String suffix : List.of(
			"normal", "narrow", "short", "long_text", "missing", "stale", "list_limit", "safe_zone"
		)) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.boundary." + suffix);
		}
		for (String suffix : List.of(
			"setup", "task_running", "task_paused", "intermission", "countdown_start",
			"countdown_key_second", "countdown_paused", "countdown_final_tick"
		)) {
			checkTranslationPair(zhCn, enUs, "pixel_tzz_pro.settings.hud.developer.scenario." + suffix);
		}

		System.out.println("SETTINGS_UI_CONTRACT_SELF_CHECK=PASS");
	}

	private static JsonObject parseObject(final Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static void checkTranslationPair(
		final JsonObject zhCn,
		final JsonObject enUs,
		final String key
	) {
		check(zhCn.has(key), "missing zh_cn translation: " + key);
		check(enUs.has(key), "missing en_us translation: " + key);
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

	private static String sourceMethod(final String source, final String signature) {
		int start = source.indexOf(signature);
		if (start < 0) {
			return "";
		}
		int next = source.indexOf(" @Override ", start + signature.length());
		return next < 0 ? source.substring(start) : source.substring(start, next);
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

	private static boolean inputRefreshesContext(final String source, final String signature) {
		String method = sourceBlock(source, signature);
		return !method.isEmpty() && method.contains("if (this.session.refreshContext())");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
