package io.github.zcpu954861.pixeltzzpro.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable data-pack definitions for V3B text effects and message cues.
 *
 * <p>The message text model deliberately keeps vanilla components and dynamic fields as separate
 * parts. This preserves component styling and interaction metadata while giving the authoritative
 * runtime an exact boundary at which each dynamic field must be captured and locked.
 */
public final class MessageDefinitions {
	public static final int CURRENT_FORMAT_VERSION = 1;

	private MessageDefinitions() {
	}

	public record TimeSpan(long nanoseconds) {
		public static final long NANOS_PER_TICK = 50_000_000L;

		public TimeSpan {
			if (nanoseconds < 0L) {
				throw new IllegalArgumentException("time span cannot be negative");
			}
		}

		public long ceilingTicks() {
			if (this.nanoseconds == 0L) {
				return 0L;
			}
			return Math.addExact(this.nanoseconds, NANOS_PER_TICK - 1L) / NANOS_PER_TICK;
		}
	}

	public enum RestoreMode {
		INSTANT,
		GRADIENT
	}

	public enum SoundCategory {
		MASTER,
		MUSIC,
		RECORD,
		WEATHER,
		BLOCK,
		HOSTILE,
		NEUTRAL,
		PLAYER,
		AMBIENT,
		VOICE,
		UI
	}

	public record CursorSpec(
		boolean enabled,
		String glyph,
		String color,
		boolean blink,
		Optional<TimeSpan> blinkPeriod
	) {
		public CursorSpec {
			glyph = Objects.requireNonNull(glyph, "glyph");
			color = Objects.requireNonNull(color, "color");
			blinkPeriod = immutableOptional(blinkPeriod);
		}

		public static CursorSpec disabled() {
			return new CursorSpec(false, "▌", "#FFFFFF", false, Optional.empty());
		}
	}

	public record TypewriterSpec(
		TimeSpan characterInterval,
		CursorSpec cursor,
		Optional<String> freshColor,
		int restoreAfterCharacters,
		RestoreMode restoreMode
	) {
		public TypewriterSpec {
			characterInterval = Objects.requireNonNull(characterInterval, "characterInterval");
			cursor = Objects.requireNonNull(cursor, "cursor");
			freshColor = immutableOptional(freshColor);
			restoreMode = Objects.requireNonNull(restoreMode, "restoreMode");
			if (restoreAfterCharacters < 0) {
				throw new IllegalArgumentException("restoreAfterCharacters cannot be negative");
			}
		}
	}

	public record FadeSpec(TimeSpan enter, TimeSpan hold, TimeSpan exit) {
		public FadeSpec {
			enter = Objects.requireNonNull(enter, "enter");
			hold = Objects.requireNonNull(hold, "hold");
			exit = Objects.requireNonNull(exit, "exit");
		}
	}

	public record ScrambleSpec(TimeSpan characterInterval, String characters) {
		public ScrambleSpec {
			characterInterval = Objects.requireNonNull(characterInterval, "characterInterval");
			characters = Objects.requireNonNull(characters, "characters");
		}
	}

	public record GradientSpec(String fromColor, String toColor, TimeSpan duration) {
		public GradientSpec {
			fromColor = Objects.requireNonNull(fromColor, "fromColor");
			toColor = Objects.requireNonNull(toColor, "toColor");
			duration = Objects.requireNonNull(duration, "duration");
		}
	}

	public record MotionSpec(int slideX, int slideY, float startScale, TimeSpan duration) {
		public MotionSpec {
			if (!Float.isFinite(startScale)) {
				throw new IllegalArgumentException("startScale must be finite");
			}
			duration = Objects.requireNonNull(duration, "duration");
		}
	}

	public record PulseSpec(float scale, TimeSpan duration) {
		public PulseSpec {
			if (!Float.isFinite(scale)) {
				throw new IllegalArgumentException("scale must be finite");
			}
			duration = Objects.requireNonNull(duration, "duration");
		}
	}

	public record CharacterSoundSpec(
		Identifier sound,
		SoundCategory category,
		int everyCharacters,
		boolean skipWhitespace,
		boolean skipPunctuation,
		boolean skipNewline,
		float volume,
		float pitch,
		float pitchVariation,
		SoundAttachment attachment,
		MissingAssetMode missing,
		Optional<Identifier> fallback
	) {
		public CharacterSoundSpec {
			sound = Objects.requireNonNull(sound, "sound");
			category = Objects.requireNonNull(category, "category");
			attachment = Objects.requireNonNull(attachment, "attachment");
			missing = Objects.requireNonNull(missing, "missing");
			fallback = immutableOptional(fallback);
			if (everyCharacters < 1) {
				throw new IllegalArgumentException("everyCharacters must be positive");
			}
			if (
				!Float.isFinite(volume)
					|| !Float.isFinite(pitch)
					|| !Float.isFinite(pitchVariation)
			) {
				throw new IllegalArgumentException("sound volume and pitch must be finite");
			}
		}

		public CharacterSoundSpec(
			final Identifier sound,
			final SoundCategory category,
			final int everyCharacters,
			final boolean skipWhitespace,
			final boolean skipPunctuation,
			final float volume,
			final float pitch
		) {
			this(
				sound,
				category,
				everyCharacters,
				skipWhitespace,
				skipPunctuation,
				true,
				volume,
				pitch,
				0.0F,
				SoundAttachment.PLAYER,
				MissingAssetMode.SILENT,
				Optional.empty()
			);
		}
	}

	public record TextEffectDefinition(
		Identifier id,
		Optional<TypewriterSpec> typewriter,
		Optional<FadeSpec> fade,
		Optional<ScrambleSpec> scramble,
		Optional<GradientSpec> gradient,
		Optional<MotionSpec> motion,
		Optional<PulseSpec> pulse,
		Optional<CharacterSoundSpec> characterSound,
		int maxCharacterSoundEvents,
		String canonicalDocument
	) {
		public TextEffectDefinition {
			id = Objects.requireNonNull(id, "id");
			typewriter = immutableOptional(typewriter);
			fade = immutableOptional(fade);
			scramble = immutableOptional(scramble);
			gradient = immutableOptional(gradient);
			motion = immutableOptional(motion);
			pulse = immutableOptional(pulse);
			characterSound = immutableOptional(characterSound);
			if (maxCharacterSoundEvents < 1) {
				throw new IllegalArgumentException("maxCharacterSoundEvents must be positive");
			}
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
		}

		public TextEffectDefinition(
			final Identifier id,
			final Optional<TypewriterSpec> typewriter,
			final Optional<FadeSpec> fade,
			final Optional<ScrambleSpec> scramble,
			final Optional<CharacterSoundSpec> characterSound,
			final int maxCharacterSoundEvents,
			final String canonicalDocument
		) {
			this(
				id,
				typewriter,
				fade,
				scramble,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				characterSound,
				maxCharacterSoundEvents,
				canonicalDocument
			);
		}
	}

	public enum ClockMode {
		GAME_TIME,
		PRESENTATION_TIME
	}

	public enum SynchronizationMode {
		IMMEDIATE,
		SYNCHRONIZED,
		PER_PLAYER
	}

	public enum LateArrivalMode {
		SEEK,
		WAIT,
		STATIC_FINAL
	}

	public enum SynchronizationTimeoutMode {
		START_AVAILABLE,
		STATIC_FINAL,
		CANCEL
	}

	public enum ConflictMode {
		PARALLEL,
		QUEUE,
		REPLACE,
		DISCARD
	}

	public enum DuplicateMode {
		ALLOW,
		IGNORE_WHILE_ACTIVE,
		RESTART,
		QUEUE,
		REFRESH
	}

	public enum CaptureMode {
		ON_DISPLAY,
		ON_FIRST_FIELD,
		PER_FIELD
	}

	public enum TextChannel {
		CHAT,
		TITLE,
		SUBTITLE,
		ACTION_BAR
	}

	public enum ParameterType {
		STRING,
		INTEGER,
		DECIMAL,
		BOOLEAN,
		IDENTIFIER,
		PLAYER,
		COMPONENT,
		DURATION
	}

	public enum IdentifierKind {
		ANY,
		GAME,
		PHASE,
		TASK,
		FIELD,
		ROLE,
		TEAM,
		LIFE_STATE,
		PLAYER_DATA,
		EVENT,
		STATISTIC
	}

	public enum ParameterErrorMode {
		FAIL,
		USE_DEFAULT,
		USE_FALLBACK
	}

	public enum ParameterSourceKind {
		CALL_ARGUMENT,
		INVOKER,
		ORIGIN,
		TARGET,
		SCORE,
		STORAGE,
		GAME_CONTEXT,
		PLAYER_DATA
	}

	public enum GameContextValue {
		GAME,
		PHASE,
		TASK,
		EVENT,
		STATISTIC
	}

	public enum FieldScope {
		CUE,
		CONTENT
	}

	public enum CallbackExecution {
		AS_SERVER,
		AS_INVOKER,
		AS_TARGET
	}

	public enum CallbackLocation {
		ORIGIN,
		TARGET
	}

	public enum ConditionEvaluation {
		CUE_START,
		NODE_START,
		FIELD_CAPTURE
	}

	public enum CallbackTrigger {
		START,
		TARGET_COMPLETE,
		ALL_COMPLETE,
		INTERRUPT
	}

	public enum RepeatCaptureMode {
		KEEP,
		PER_REPEAT
	}

	public enum TextAlignment {
		LEFT,
		CENTER,
		RIGHT
	}

	public enum TextOverflowMode {
		WRAP,
		SCALE,
		ELLIPSIS,
		STATIC_FINAL
	}

	public enum DurationMode {
		CONTENT_DRIVEN,
		FIXED_TOTAL
	}

	public enum LanguageDurationBasis {
		LONGEST_DECLARED,
		FIXED
	}

	public enum SpeakerKind {
		SYSTEM,
		NARRATOR,
		PLAYER_PARAMETER,
		REGISTERED_ENTITY
	}

	public enum CharacterSoundRole {
		PRIMARY,
		MUTED
	}

	public enum AudienceSource {
		ALL,
		CURRENT_HOST,
		CALL_TARGETS,
		INVOKER
	}

	public enum AudienceEvolution {
		SNAPSHOT,
		LIVE_ADD,
		LIVE_STRICT
	}

	public enum AudienceLossMode {
		FINALIZE,
		CANCEL
	}

	public enum ContextRecheck {
		BEFORE_START,
		LIVE_STRICT
	}

	public enum EmptyAudienceMode {
		FAIL,
		COMPLETE,
		CALLBACKS_ONLY
	}

	public enum OfflineTargetMode {
		SKIP,
		FAIL,
		QUEUE,
		FINAL_ONLY
	}

	public enum ChatInterruptMode {
		FINALIZE,
		REMOVE
	}

	public enum ScreenStartMode {
		IMMEDIATE,
		WAIT_GAMEPLAY,
		CLOSE_SAFE_SCREEN,
		FINAL_CHAT_ONLY
	}

	public enum ScreenTimeoutAction {
		FINALIZE,
		CANCEL,
		FINAL_CHAT_ONLY
	}

	public enum ObscuredMode {
		CONTINUE,
		PAUSE,
		FINALIZE,
		CANCEL
	}

	public enum ReloadMode {
		FINISH_SNAPSHOT,
		FINALIZE,
		CANCEL
	}

	public enum RestartMode {
		TRANSIENT,
		CONTINUE,
		FINALIZE,
		CANCEL
	}

	public enum ExternalConflictMode {
		YIELD,
		PAUSE,
		OFFSET,
		OVERLAY
	}

	public enum ReconnectMode {
		CONTINUE,
		RESTART,
		FINAL_ONLY,
		DROP
	}

	public enum LateJoinMode {
		CONTINUE,
		FROM_START,
		FINAL_ONLY,
		DROP
	}

	public enum ResourceReloadMode {
		FINISH,
		FINALIZE,
		CANCEL
	}

	public enum RespawnMode {
		CONTINUE,
		FINALIZE,
		CANCEL
	}

	public enum AttachmentMode {
		PLAYER,
		WORLD
	}

	public enum DimensionExitMode {
		CONTINUE,
		FINALIZE,
		CANCEL
	}

	public enum SoundAttachment {
		PLAYER,
		WORLD_ORIGIN
	}

	public enum ReducedMotionMode {
		STATIC_FINAL,
		SIMPLIFIED
	}

	public enum HistoryTaskSource {
		NONE,
		CURRENT_TASK,
		PARAMETER
	}

	public enum HistoryTimestampSource {
		GAME_TIME,
		PRESENTATION_TIME,
		CUE_START,
		COMPLETION
	}

	public enum MessageAssetType {
		FONT,
		SOUND
	}

	public enum MissingAssetMode {
		SILENT,
		FALLBACK,
		BLOCK_START
	}

	public record AudienceSelector(
		AudienceSource source,
		Set<Identifier> roles,
		Set<Identifier> teams,
		Set<Identifier> lifeStates,
		Set<Identifier> roleTags,
		Set<Identifier> teamTags,
		Set<Identifier> lifeStateTags,
		boolean excludeHost,
		boolean onlineOnly
	) {
		public AudienceSelector {
			source = Objects.requireNonNull(source, "source");
			roles = Set.copyOf(roles);
			teams = Set.copyOf(teams);
			lifeStates = Set.copyOf(lifeStates);
			roleTags = Set.copyOf(roleTags);
			teamTags = Set.copyOf(teamTags);
			lifeStateTags = Set.copyOf(lifeStateTags);
		}
	}

	public record AudienceSpec(List<AudienceSelector> selectors) {
		public AudienceSpec {
			selectors = List.copyOf(selectors);
			if (selectors.isEmpty()) {
				throw new IllegalArgumentException("audience must contain at least one selector");
			}
		}

		public AudienceSpec(
			final AudienceSource source,
			final Set<Identifier> roles,
			final Set<Identifier> teams,
			final Set<Identifier> lifeStates,
			final Set<Identifier> roleTags,
			final Set<Identifier> teamTags,
			final Set<Identifier> lifeStateTags,
			final boolean excludeHost,
			final boolean onlineOnly
		) {
			this(
				List.of(
					new AudienceSelector(
						source,
						roles,
						teams,
						lifeStates,
						roleTags,
						teamTags,
						lifeStateTags,
						excludeHost,
						onlineOnly
					)
				)
			);
		}

		public AudienceSpec(
			final Set<Identifier> roles,
			final Set<Identifier> teams,
			final Set<Identifier> lifeStates,
			final Set<Identifier> roleTags,
			final Set<Identifier> teamTags,
			final Set<Identifier> lifeStateTags,
			final boolean excludeHost,
			final boolean onlineOnly
		) {
			this(
				AudienceSource.ALL,
				roles,
				teams,
				lifeStates,
				roleTags,
				teamTags,
				lifeStateTags,
				excludeHost,
				onlineOnly
			);
		}

		public AudienceSource source() {
			return this.selectors.getFirst().source();
		}

		public Set<Identifier> roles() {
			return union(AudienceSelector::roles);
		}

		public Set<Identifier> teams() {
			return union(AudienceSelector::teams);
		}

		public Set<Identifier> lifeStates() {
			return union(AudienceSelector::lifeStates);
		}

		public Set<Identifier> roleTags() {
			return union(AudienceSelector::roleTags);
		}

		public Set<Identifier> teamTags() {
			return union(AudienceSelector::teamTags);
		}

		public Set<Identifier> lifeStateTags() {
			return union(AudienceSelector::lifeStateTags);
		}

		public boolean excludeHost() {
			return this.selectors.stream().allMatch(AudienceSelector::excludeHost);
		}

		public boolean onlineOnly() {
			return this.selectors.stream().allMatch(AudienceSelector::onlineOnly);
		}

		public static AudienceSpec allOnline() {
			return new AudienceSpec(
				AudienceSource.ALL,
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				false,
				true
			);
		}

		private Set<Identifier> union(
			final java.util.function.Function<AudienceSelector, Set<Identifier>> getter
		) {
			java.util.LinkedHashSet<Identifier> result = new java.util.LinkedHashSet<>();
			this.selectors.forEach(selector -> result.addAll(getter.apply(selector)));
			return Set.copyOf(result);
		}
	}

	public record TimingPolicy(
		Optional<ClockMode> clock,
		Optional<SynchronizationMode> synchronization,
		Optional<TimeSpan> synchronizedWait,
		LateArrivalMode lateArrival,
		SynchronizationTimeoutMode synchronizationTimeout
	) {
		public TimingPolicy {
			clock = immutableOptional(clock);
			synchronization = immutableOptional(synchronization);
			synchronizedWait = immutableOptional(synchronizedWait);
			lateArrival = Objects.requireNonNull(lateArrival, "lateArrival");
			synchronizationTimeout = Objects.requireNonNull(
				synchronizationTimeout,
				"synchronizationTimeout"
			);
		}

		public static TimingPolicy runtimeDefaults() {
			return new TimingPolicy(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				LateArrivalMode.SEEK,
				SynchronizationTimeoutMode.START_AVAILABLE
			);
		}

		public TimingPolicy(
			final Optional<ClockMode> clock,
			final Optional<SynchronizationMode> synchronization,
			final Optional<TimeSpan> synchronizedWait
		) {
			this(
				clock,
				synchronization,
				synchronizedWait,
				LateArrivalMode.SEEK,
				SynchronizationTimeoutMode.START_AVAILABLE
			);
		}
	}

	public record AudiencePolicy(
		AudienceSpec audience,
		AudienceEvolution evolution,
		boolean sensitive,
		AudienceLossMode onLoss
	) {
		public AudiencePolicy {
			audience = Objects.requireNonNull(audience, "audience");
			evolution = Objects.requireNonNull(evolution, "evolution");
			onLoss = Objects.requireNonNull(onLoss, "onLoss");
		}

		public static AudiencePolicy standard() {
			return new AudiencePolicy(
				AudienceSpec.allOnline(),
				AudienceEvolution.SNAPSHOT,
				false,
				AudienceLossMode.FINALIZE
			);
		}
	}

	public record ContextPolicy(
		Set<Identifier> games,
		Set<Identifier> phases,
		Set<Identifier> tasks,
		ContextRecheck recheck
	) {
		public ContextPolicy {
			games = Set.copyOf(games);
			phases = Set.copyOf(phases);
			tasks = Set.copyOf(tasks);
			recheck = Objects.requireNonNull(recheck, "recheck");
		}

		public static ContextPolicy unrestricted() {
			return new ContextPolicy(Set.of(), Set.of(), Set.of(), ContextRecheck.BEFORE_START);
		}
	}

	public record ConcurrencyPolicy(
		DuplicateMode duplicate,
		Optional<TimeSpan> cooldown,
		boolean dedupeTargets,
		Set<String> dedupeParameters,
		Optional<String> refreshKey,
		ChatInterruptMode chatInterrupt,
		int priority,
		Set<String> groups
	) {
		public ConcurrencyPolicy {
			duplicate = Objects.requireNonNull(duplicate, "duplicate");
			cooldown = immutableOptional(cooldown);
			dedupeParameters = Set.copyOf(dedupeParameters);
			refreshKey = immutableOptional(refreshKey);
			chatInterrupt = Objects.requireNonNull(chatInterrupt, "chatInterrupt");
			groups = Set.copyOf(groups);
		}

		public static ConcurrencyPolicy standard() {
			return new ConcurrencyPolicy(
				DuplicateMode.IGNORE_WHILE_ACTIVE,
				Optional.empty(),
				true,
				Set.of(),
				Optional.empty(),
				ChatInterruptMode.FINALIZE,
				0,
				Set.of()
			);
		}
	}

	public record DeliveryPolicy(
		EmptyAudienceMode emptyAudience,
		OfflineTargetMode offlineTargets,
		Optional<TimeSpan> offlineTtl
	) {
		public DeliveryPolicy {
			emptyAudience = Objects.requireNonNull(emptyAudience, "emptyAudience");
			offlineTargets = Objects.requireNonNull(offlineTargets, "offlineTargets");
			offlineTtl = immutableOptional(offlineTtl);
		}

		public static DeliveryPolicy standard() {
			return new DeliveryPolicy(
				EmptyAudienceMode.FAIL,
				OfflineTargetMode.SKIP,
				Optional.empty()
			);
		}
	}

	public record LifecyclePolicy(
		Map<TextChannel, ScreenStartMode> screenStart,
		Map<TextChannel, ObscuredMode> obscured,
		Optional<TimeSpan> screenWaitTimeout,
		ScreenTimeoutAction screenTimeoutAction,
		Optional<TimeSpan> maxPause,
		ReloadMode reload,
		RestartMode restart,
		ExternalConflictMode externalConflict,
		ReconnectMode reconnect,
		LateJoinMode lateJoin,
		ResourceReloadMode resourceReload,
		RespawnMode respawn,
		AttachmentMode attachment,
		DimensionExitMode dimensionExit
	) {
		public LifecyclePolicy {
			screenStart = Map.copyOf(screenStart);
			obscured = Map.copyOf(obscured);
			screenWaitTimeout = immutableOptional(screenWaitTimeout);
			screenTimeoutAction = Objects.requireNonNull(
				screenTimeoutAction,
				"screenTimeoutAction"
			);
			maxPause = immutableOptional(maxPause);
			reload = Objects.requireNonNull(reload, "reload");
			restart = Objects.requireNonNull(restart, "restart");
			externalConflict = Objects.requireNonNull(externalConflict, "externalConflict");
			reconnect = Objects.requireNonNull(reconnect, "reconnect");
			lateJoin = Objects.requireNonNull(lateJoin, "lateJoin");
			resourceReload = Objects.requireNonNull(resourceReload, "resourceReload");
			respawn = Objects.requireNonNull(respawn, "respawn");
			attachment = Objects.requireNonNull(attachment, "attachment");
			dimensionExit = Objects.requireNonNull(dimensionExit, "dimensionExit");
		}

		public static LifecyclePolicy standard() {
			Map<TextChannel, ScreenStartMode> screen = new LinkedHashMap<>();
			Map<TextChannel, ObscuredMode> covered = new LinkedHashMap<>();
			for (TextChannel channel : TextChannel.values()) {
				screen.put(
					channel,
					channel == TextChannel.CHAT
						? ScreenStartMode.IMMEDIATE
						: ScreenStartMode.WAIT_GAMEPLAY
				);
				covered.put(
					channel,
					channel == TextChannel.CHAT
						? ObscuredMode.CONTINUE
						: ObscuredMode.PAUSE
				);
			}
			return new LifecyclePolicy(
				screen,
				covered,
				Optional.empty(),
				ScreenTimeoutAction.FINALIZE,
				Optional.empty(),
				ReloadMode.FINISH_SNAPSHOT,
				RestartMode.TRANSIENT,
				ExternalConflictMode.YIELD,
				ReconnectMode.CONTINUE,
				LateJoinMode.DROP,
				ResourceReloadMode.FINISH,
				RespawnMode.CONTINUE,
				AttachmentMode.PLAYER,
				DimensionExitMode.CONTINUE
			);
		}
	}

	public record AccessibilityPolicy(
		boolean allowManualComplete,
		ReducedMotionMode reducedMotion
	) {
		public AccessibilityPolicy {
			reducedMotion = Objects.requireNonNull(reducedMotion, "reducedMotion");
		}

		public static AccessibilityPolicy standard() {
			return new AccessibilityPolicy(false, ReducedMotionMode.SIMPLIFIED);
		}
	}

	public record CuePolicies(
		TimingPolicy timing,
		AudiencePolicy audience,
		ContextPolicy context,
		ConcurrencyPolicy concurrency,
		DeliveryPolicy delivery,
		LifecyclePolicy lifecycle,
		AccessibilityPolicy accessibility,
		Set<String> controlGroups,
		CaptureMode capture,
		Optional<ConflictMode> defaultConflict
	) {
		public CuePolicies {
			timing = Objects.requireNonNull(timing, "timing");
			audience = Objects.requireNonNull(audience, "audience");
			context = Objects.requireNonNull(context, "context");
			concurrency = Objects.requireNonNull(concurrency, "concurrency");
			delivery = Objects.requireNonNull(delivery, "delivery");
			lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
			accessibility = Objects.requireNonNull(accessibility, "accessibility");
			controlGroups = Set.copyOf(controlGroups);
			capture = Objects.requireNonNull(capture, "capture");
			defaultConflict = immutableOptional(defaultConflict);
		}

		public static CuePolicies standard() {
			return new CuePolicies(
				TimingPolicy.runtimeDefaults(),
				AudiencePolicy.standard(),
				ContextPolicy.unrestricted(),
				ConcurrencyPolicy.standard(),
				DeliveryPolicy.standard(),
				LifecyclePolicy.standard(),
				AccessibilityPolicy.standard(),
				Set.of(),
				CaptureMode.ON_DISPLAY,
				Optional.empty()
			);
		}
	}

	public record CueDefaults(
		AudienceSpec audience,
		Optional<ClockMode> clock,
		Optional<SynchronizationMode> synchronization,
		Optional<ConflictMode> conflict,
		DuplicateMode duplicate,
		CaptureMode capture,
		int priority
	) {
		public CueDefaults {
			audience = Objects.requireNonNull(audience, "audience");
			clock = immutableOptional(clock);
			synchronization = immutableOptional(synchronization);
			conflict = immutableOptional(conflict);
			duplicate = Objects.requireNonNull(duplicate, "duplicate");
			capture = Objects.requireNonNull(capture, "capture");
		}

		public static CueDefaults standard() {
			return new CueDefaults(
				AudienceSpec.allOnline(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				DuplicateMode.IGNORE_WHILE_ACTIVE,
				CaptureMode.ON_DISPLAY,
				0
			);
		}
	}

	public record RichComponent(String canonicalJson, String plainText) {
		public RichComponent {
			canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
			plainText = Objects.requireNonNull(plainText, "plainText");
		}
	}

	public sealed interface ParameterSource permits SimpleParameterSource,
		ScoreParameterSource,
		StorageParameterSource,
		GameContextParameterSource,
		PlayerDataParameterSource {
		ParameterSourceKind kind();
	}

	public record SimpleParameterSource(ParameterSourceKind kind) implements ParameterSource {
		public SimpleParameterSource {
			kind = Objects.requireNonNull(kind, "kind");
			if (
				kind != ParameterSourceKind.CALL_ARGUMENT
					&& kind != ParameterSourceKind.INVOKER
					&& kind != ParameterSourceKind.ORIGIN
					&& kind != ParameterSourceKind.TARGET
			) {
				throw new IllegalArgumentException("simple parameter source requires a context kind");
			}
		}
	}

	public record ScoreParameterSource(
		String objective,
		Optional<String> holder
	) implements ParameterSource {
		public ScoreParameterSource {
			objective = Objects.requireNonNull(objective, "objective");
			holder = immutableOptional(holder);
		}

		@Override
		public ParameterSourceKind kind() {
			return ParameterSourceKind.SCORE;
		}
	}

	public record StorageParameterSource(
		Identifier storage,
		String path
	) implements ParameterSource {
		public StorageParameterSource {
			storage = Objects.requireNonNull(storage, "storage");
			path = Objects.requireNonNull(path, "path");
		}

		@Override
		public ParameterSourceKind kind() {
			return ParameterSourceKind.STORAGE;
		}
	}

	public record GameContextParameterSource(
		GameContextValue value
	) implements ParameterSource {
		public GameContextParameterSource {
			value = Objects.requireNonNull(value, "value");
		}

		@Override
		public ParameterSourceKind kind() {
			return ParameterSourceKind.GAME_CONTEXT;
		}
	}

	public record PlayerDataParameterSource(
		Identifier field
	) implements ParameterSource {
		public PlayerDataParameterSource {
			field = Objects.requireNonNull(field, "field");
		}

		@Override
		public ParameterSourceKind kind() {
			return ParameterSourceKind.PLAYER_DATA;
		}
	}

	public record ParameterErrorPolicy(
		ParameterErrorMode mode,
		Optional<String> canonicalFallback
	) {
		public ParameterErrorPolicy {
			mode = Objects.requireNonNull(mode, "mode");
			canonicalFallback = immutableOptional(canonicalFallback);
		}

		public static ParameterErrorPolicy fail() {
			return new ParameterErrorPolicy(ParameterErrorMode.FAIL, Optional.empty());
		}
	}

	public record ParameterDefinition(
		ParameterType type,
		boolean required,
		Optional<String> canonicalDefault,
		List<ParameterSource> sources,
		ParameterErrorPolicy onError,
		boolean dedupe,
		Set<String> allowedNodes,
		Optional<AudienceSpec> audience,
		boolean sensitive,
		IdentifierKind identifierKind
	) {
		public ParameterDefinition {
			type = Objects.requireNonNull(type, "type");
			canonicalDefault = immutableOptional(canonicalDefault);
			sources = List.copyOf(sources);
			onError = Objects.requireNonNull(onError, "onError");
			allowedNodes = Set.copyOf(allowedNodes);
			audience = immutableOptional(audience);
			identifierKind = Objects.requireNonNull(identifierKind, "identifierKind");
		}

		public ParameterDefinition(
			final ParameterType type,
			final boolean required,
			final Optional<String> canonicalDefault
		) {
			this(
				type,
				required,
				canonicalDefault,
				List.of(new SimpleParameterSource(ParameterSourceKind.CALL_ARGUMENT)),
				ParameterErrorPolicy.fail(),
				false,
				Set.of(),
				Optional.empty(),
				false,
				IdentifierKind.ANY
			);
		}
	}

	public sealed interface FieldSource permits ArgumentSource, BindingSource, VanillaComponentSource {
	}

	public record ArgumentSource(String parameter) implements FieldSource {
		public ArgumentSource {
			parameter = Objects.requireNonNull(parameter, "parameter");
		}
	}

	/**
	 * A declarative key for a trusted, registered runtime binding.
	 *
	 * <p>The stored path is not permission to read an arbitrary object, storage path, or server
	 * value. Playback must resolve it through the active game's allow-listed binding registry and
	 * reject every unregistered path.
	 */
	public record BindingSource(String path) implements FieldSource {
		public BindingSource {
			path = Objects.requireNonNull(path, "path");
		}
	}

	public record VanillaComponentSource(RichComponent component) implements FieldSource {
		public VanillaComponentSource {
			component = Objects.requireNonNull(component, "component");
		}
	}

	public record FieldReservation(
		Optional<Integer> maxGraphemes,
		Optional<Integer> estimatedWidth,
		Optional<Integer> maxLines
	) {
		public FieldReservation {
			maxGraphemes = immutableOptional(maxGraphemes);
			estimatedWidth = immutableOptional(estimatedWidth);
			maxLines = immutableOptional(maxLines);
		}
	}

	public record DynamicFieldDefinition(
		CaptureMode capture,
		FieldSource source,
		Optional<RichComponent> fallback,
		Optional<FieldReservation> reservation
	) {
		public DynamicFieldDefinition {
			capture = Objects.requireNonNull(capture, "capture");
			source = Objects.requireNonNull(source, "source");
			fallback = immutableOptional(fallback);
			reservation = immutableOptional(reservation);
		}

		public DynamicFieldDefinition(
			final CaptureMode capture,
			final FieldSource source,
			final Optional<RichComponent> fallback
		) {
			this(capture, source, fallback, Optional.empty());
		}
	}

	public record SegmentTiming(
		Optional<TimeSpan> pauseBefore,
		Optional<TimeSpan> pauseAfter,
		boolean emphasis
	) {
		public SegmentTiming {
			pauseBefore = immutableOptional(pauseBefore);
			pauseAfter = immutableOptional(pauseAfter);
		}

		public static SegmentTiming none() {
			return new SegmentTiming(Optional.empty(), Optional.empty(), false);
		}
	}

	public sealed interface ComponentPart permits StaticComponentPart, FieldPart {
		SegmentTiming timing();
	}

	public record StaticComponentPart(
		RichComponent component,
		SegmentTiming timing
	) implements ComponentPart {
		public StaticComponentPart {
			component = Objects.requireNonNull(component, "component");
			timing = Objects.requireNonNull(timing, "timing");
		}

		public StaticComponentPart(final RichComponent component) {
			this(component, SegmentTiming.none());
		}
	}

	public record FieldReference(FieldScope scope, String id) {
		public FieldReference {
			scope = Objects.requireNonNull(scope, "scope");
			id = Objects.requireNonNull(id, "id");
		}
	}

	public record FieldPart(
		FieldReference field,
		SegmentTiming timing
	) implements ComponentPart {
		public FieldPart {
			field = Objects.requireNonNull(field, "field");
			timing = Objects.requireNonNull(timing, "timing");
		}

		public FieldPart(final FieldReference field) {
			this(field, SegmentTiming.none());
		}
	}

	public record ComponentTemplate(List<ComponentPart> parts) {
		public ComponentTemplate {
			parts = List.copyOf(parts);
		}
	}

	public record LocalizedTemplate(
		ComponentTemplate fallback,
		Map<String, ComponentTemplate> locales
	) {
		public LocalizedTemplate {
			fallback = Objects.requireNonNull(fallback, "fallback");
			locales = immutableMap(locales);
		}
	}

	public record ConditionSpec(
		ConditionEvaluation evaluateAt,
		Optional<UiDefinitions.ConditionExpression> expression,
		Optional<Identifier> predicate
	) {
		public ConditionSpec {
			evaluateAt = Objects.requireNonNull(evaluateAt, "evaluateAt");
			expression = immutableOptional(expression);
			predicate = immutableOptional(predicate);
			if (expression.isPresent() == predicate.isPresent()) {
				throw new IllegalArgumentException(
					"condition must declare exactly one expression or predicate"
				);
			}
		}
	}

	public record CursorPatch(
		Optional<Boolean> enabled,
		Optional<String> glyph,
		Optional<String> color,
		Optional<Boolean> blink,
		Optional<TimeSpan> blinkPeriod
	) {
		public CursorPatch {
			enabled = immutableOptional(enabled);
			glyph = immutableOptional(glyph);
			color = immutableOptional(color);
			blink = immutableOptional(blink);
			blinkPeriod = immutableOptional(blinkPeriod);
		}
	}

	public record TypewriterPatch(
		Optional<TimeSpan> characterInterval,
		Optional<CursorPatch> cursor,
		Optional<String> freshColor,
		Optional<Integer> restoreAfterCharacters,
		Optional<RestoreMode> restoreMode
	) {
		public TypewriterPatch {
			characterInterval = immutableOptional(characterInterval);
			cursor = immutableOptional(cursor);
			freshColor = immutableOptional(freshColor);
			restoreAfterCharacters = immutableOptional(restoreAfterCharacters);
			restoreMode = immutableOptional(restoreMode);
		}
	}

	public record FadePatch(
		Optional<TimeSpan> enter,
		Optional<TimeSpan> hold,
		Optional<TimeSpan> exit
	) {
		public FadePatch {
			enter = immutableOptional(enter);
			hold = immutableOptional(hold);
			exit = immutableOptional(exit);
		}
	}

	public record ScramblePatch(
		Optional<TimeSpan> characterInterval,
		Optional<String> characters
	) {
		public ScramblePatch {
			characterInterval = immutableOptional(characterInterval);
			characters = immutableOptional(characters);
		}
	}

	public record CharacterSoundPatch(
		Optional<Identifier> sound,
		Optional<SoundCategory> category,
		Optional<Integer> everyCharacters,
		Optional<Boolean> skipWhitespace,
		Optional<Boolean> skipPunctuation,
		Optional<Boolean> skipNewline,
		Optional<Float> volume,
		Optional<Float> pitch,
		Optional<Float> pitchVariation,
		Optional<SoundAttachment> attachment,
		Optional<MissingAssetMode> missing,
		Optional<Identifier> fallback
	) {
		public CharacterSoundPatch {
			sound = immutableOptional(sound);
			category = immutableOptional(category);
			everyCharacters = immutableOptional(everyCharacters);
			skipWhitespace = immutableOptional(skipWhitespace);
			skipPunctuation = immutableOptional(skipPunctuation);
			skipNewline = immutableOptional(skipNewline);
			volume = immutableOptional(volume);
			pitch = immutableOptional(pitch);
			pitchVariation = immutableOptional(pitchVariation);
			attachment = immutableOptional(attachment);
			missing = immutableOptional(missing);
			fallback = immutableOptional(fallback);
		}
	}

	public record TextEffectPatch(
		Optional<TypewriterPatch> typewriter,
		Optional<FadePatch> fade,
		Optional<ScramblePatch> scramble,
		Optional<GradientSpec> gradient,
		Optional<MotionSpec> motion,
		Optional<PulseSpec> pulse,
		Optional<CharacterSoundPatch> characterSound,
		Optional<Integer> maxCharacterSoundEvents
	) {
		public TextEffectPatch {
			typewriter = immutableOptional(typewriter);
			fade = immutableOptional(fade);
			scramble = immutableOptional(scramble);
			gradient = immutableOptional(gradient);
			motion = immutableOptional(motion);
			pulse = immutableOptional(pulse);
			characterSound = immutableOptional(characterSound);
			maxCharacterSoundEvents = immutableOptional(maxCharacterSoundEvents);
		}

		public static TextEffectPatch empty() {
			return new TextEffectPatch(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}

		public boolean isEmpty() {
			return this.typewriter.isEmpty()
				&& this.fade.isEmpty()
				&& this.scramble.isEmpty()
				&& this.gradient.isEmpty()
				&& this.motion.isEmpty()
				&& this.pulse.isEmpty()
				&& this.characterSound.isEmpty()
				&& this.maxCharacterSoundEvents.isEmpty();
		}

		public TextEffectPatch(
			final Optional<TypewriterPatch> typewriter,
			final Optional<FadePatch> fade,
			final Optional<ScramblePatch> scramble,
			final Optional<CharacterSoundPatch> characterSound,
			final Optional<Integer> maxCharacterSoundEvents
		) {
			this(
				typewriter,
				fade,
				scramble,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				characterSound,
				maxCharacterSoundEvents
			);
		}
	}

	public record EffectUse(
		Optional<Identifier> preset,
		TextEffectPatch overrides
	) {
		public EffectUse {
			preset = immutableOptional(preset);
			overrides = Objects.requireNonNull(overrides, "overrides");
			if (preset.isEmpty() && overrides.isEmpty()) {
				throw new IllegalArgumentException("effect use must declare a preset or override");
			}
		}
	}

	public record TextLayout(
		TextAlignment alignment,
		int offsetX,
		int offsetY,
		Optional<Integer> maxWidth,
		Optional<Integer> maxLines,
		float lineSpacing,
		TextOverflowMode overflow
	) {
		public TextLayout {
			alignment = Objects.requireNonNull(alignment, "alignment");
			maxWidth = immutableOptional(maxWidth);
			maxLines = immutableOptional(maxLines);
			if (!Float.isFinite(lineSpacing)) {
				throw new IllegalArgumentException("lineSpacing must be finite");
			}
			overflow = Objects.requireNonNull(overflow, "overflow");
		}

		public static TextLayout standard() {
			return new TextLayout(
				TextAlignment.CENTER,
				0,
				0,
				Optional.empty(),
				Optional.empty(),
				1.0F,
				TextOverflowMode.WRAP
			);
		}
	}

	public record DurationSpec(
		DurationMode mode,
		Optional<TimeSpan> total,
		LanguageDurationBasis languageBasis,
		Optional<Float> minimumSpeed,
		Optional<Float> maximumSpeed
	) {
		public DurationSpec {
			mode = Objects.requireNonNull(mode, "mode");
			total = immutableOptional(total);
			languageBasis = Objects.requireNonNull(languageBasis, "languageBasis");
			minimumSpeed = immutableOptional(minimumSpeed);
			maximumSpeed = immutableOptional(maximumSpeed);
		}

		public static DurationSpec contentDriven() {
			return new DurationSpec(
				DurationMode.CONTENT_DRIVEN,
				Optional.empty(),
				LanguageDurationBasis.LONGEST_DECLARED,
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public record SpeakerSpec(
		SpeakerKind kind,
		Optional<String> parameter,
		Optional<Identifier> entity
	) {
		public SpeakerSpec {
			kind = Objects.requireNonNull(kind, "kind");
			parameter = immutableOptional(parameter);
			entity = immutableOptional(entity);
		}

		public static SpeakerSpec system() {
			return new SpeakerSpec(
				SpeakerKind.SYSTEM,
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public record TextContent(
		LocalizedTemplate text,
		Map<String, DynamicFieldDefinition> fields,
		Optional<EffectUse> effect,
		TextLayout layout,
		DurationSpec duration,
		SpeakerSpec speaker,
		CharacterSoundRole characterSoundRole,
		Optional<TimeSpan> hold
	) {
		public TextContent {
			text = Objects.requireNonNull(text, "text");
			fields = immutableMap(fields);
			effect = immutableOptional(effect);
			layout = Objects.requireNonNull(layout, "layout");
			duration = Objects.requireNonNull(duration, "duration");
			speaker = Objects.requireNonNull(speaker, "speaker");
			characterSoundRole = Objects.requireNonNull(
				characterSoundRole,
				"characterSoundRole"
			);
			hold = immutableOptional(hold);
		}
	}

	public record TextVariant(
		ConditionSpec when,
		TextContent content
	) {
		public TextVariant {
			when = Objects.requireNonNull(when, "when");
			content = Objects.requireNonNull(content, "content");
		}
	}

	public record NodePolicy(
		Optional<AudienceSpec> audience,
		Optional<Integer> priority,
		Optional<ConflictMode> conflict
	) {
		public NodePolicy {
			audience = immutableOptional(audience);
			priority = immutableOptional(priority);
			conflict = immutableOptional(conflict);
		}

		public static NodePolicy inherited() {
			return new NodePolicy(Optional.empty(), Optional.empty(), Optional.empty());
		}
	}

	public sealed interface ScheduleSpec permits AtCueTime, AfterNode, OnCueEvent {
		TimeSpan offset();
	}

	public record AtCueTime(TimeSpan offset) implements ScheduleSpec {
		public AtCueTime {
			offset = Objects.requireNonNull(offset, "offset");
		}
	}

	public record AfterNode(
		String nodeId,
		TimeSpan offset
	) implements ScheduleSpec {
		public AfterNode {
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			offset = Objects.requireNonNull(offset, "offset");
		}
	}

	public record OnCueEvent(
		CallbackTrigger trigger,
		TimeSpan offset
	) implements ScheduleSpec {
		public OnCueEvent {
			trigger = Objects.requireNonNull(trigger, "trigger");
			offset = Objects.requireNonNull(offset, "offset");
		}
	}

	public record RepeatSpec(
		int count,
		TimeSpan interval,
		RepeatCaptureMode capture
	) {
		public RepeatSpec {
			if (count < 1) {
				throw new IllegalArgumentException("repeat count must be positive");
			}
			interval = Objects.requireNonNull(interval, "interval");
			capture = Objects.requireNonNull(capture, "capture");
		}

		public static RepeatSpec once() {
			return new RepeatSpec(1, new TimeSpan(0L), RepeatCaptureMode.KEEP);
		}
	}

	public record NodeHeader(
		String id,
		ScheduleSpec schedule,
		NodePolicy policy,
		Optional<ConditionSpec> when,
		RepeatSpec repeat
	) {
		public NodeHeader {
			id = Objects.requireNonNull(id, "id");
			schedule = Objects.requireNonNull(schedule, "schedule");
			policy = Objects.requireNonNull(policy, "policy");
			when = immutableOptional(when);
			repeat = Objects.requireNonNull(repeat, "repeat");
		}
	}

	public sealed interface CueNode permits TextNode, SoundNode, CallbackNode {
		NodeHeader header();

		default String id() {
			return this.header().id();
		}

		default TimeSpan at() {
			return this.header().schedule() instanceof AtCueTime at
				? at.offset()
				: new TimeSpan(0L);
		}

		default NodePolicy policy() {
			return this.header().policy();
		}
	}

	public record TextNode(
		NodeHeader header,
		TextChannel channel,
		TextContent content,
		List<TextVariant> variants
	) implements CueNode {
		public TextNode {
			header = Objects.requireNonNull(header, "header");
			channel = Objects.requireNonNull(channel, "channel");
			content = Objects.requireNonNull(content, "content");
			variants = List.copyOf(variants);
		}

		public ComponentTemplate text() {
			return this.content.text().fallback();
		}

		public Map<String, DynamicFieldDefinition> fields() {
			return this.content.fields();
		}

		public Optional<Identifier> effect() {
			return this.content.effect().flatMap(EffectUse::preset);
		}

		public Optional<TimeSpan> hold() {
			return this.content.hold();
		}

		public TextNode(
			final String id,
			final TimeSpan at,
			final NodePolicy policy,
			final TextChannel channel,
			final ComponentTemplate text,
			final Map<String, DynamicFieldDefinition> fields,
			final Optional<Identifier> effect,
			final Optional<TimeSpan> hold
		) {
			this(
				new NodeHeader(
					id,
					new AtCueTime(at),
					policy,
					Optional.empty(),
					RepeatSpec.once()
				),
				channel,
				new TextContent(
					new LocalizedTemplate(text, Map.of()),
					fields,
					effect.map(
						preset -> new EffectUse(
							Optional.of(preset),
							TextEffectPatch.empty()
						)
					),
					TextLayout.standard(),
					DurationSpec.contentDriven(),
					SpeakerSpec.system(),
					CharacterSoundRole.MUTED,
					hold
				),
				List.of()
			);
		}
	}

	public record SoundNode(
		NodeHeader header,
		Identifier sound,
		SoundCategory category,
		float volume,
		float pitch,
		SoundAttachment attachment,
		MissingAssetMode missing,
		Optional<Identifier> fallback
	) implements CueNode {
		public SoundNode {
			header = Objects.requireNonNull(header, "header");
			sound = Objects.requireNonNull(sound, "sound");
			category = Objects.requireNonNull(category, "category");
			attachment = Objects.requireNonNull(attachment, "attachment");
			missing = Objects.requireNonNull(missing, "missing");
			fallback = immutableOptional(fallback);
			if (!Float.isFinite(volume) || !Float.isFinite(pitch)) {
				throw new IllegalArgumentException("sound volume and pitch must be finite");
			}
		}

		public SoundNode(
			final String id,
			final TimeSpan at,
			final NodePolicy policy,
			final Identifier sound,
			final SoundCategory category,
			final float volume,
			final float pitch
		) {
			this(
				new NodeHeader(
					id,
					new AtCueTime(at),
					policy,
					Optional.empty(),
					RepeatSpec.once()
				),
				sound,
				category,
				volume,
				pitch,
				SoundAttachment.PLAYER,
				MissingAssetMode.SILENT,
				Optional.empty()
			);
		}
	}

	public record CallbackNode(
		NodeHeader header,
		Identifier function,
		CallbackExecution execution,
		CallbackLocation location
	) implements CueNode {
		public CallbackNode {
			header = Objects.requireNonNull(header, "header");
			function = Objects.requireNonNull(function, "function");
			execution = Objects.requireNonNull(execution, "execution");
			location = Objects.requireNonNull(location, "location");
		}

		public CallbackNode(
			final String id,
			final TimeSpan at,
			final NodePolicy policy,
			final Identifier function,
			final CallbackExecution execution,
			final CallbackLocation location
		) {
			this(
				new NodeHeader(
					id,
					new AtCueTime(at),
					policy,
					Optional.empty(),
					RepeatSpec.once()
				),
				function,
				execution,
				location
			);
		}
	}

	public record HistorySpec(
		LocalizedTemplate title,
		LocalizedTemplate body,
		HistoryTaskSource taskSource,
		Optional<String> taskParameter,
		HistoryTimestampSource timestampSource,
		AudienceSpec audience,
		Set<String> savedFields,
		boolean replayAllowed
	) {
		public HistorySpec {
			title = Objects.requireNonNull(title, "title");
			body = Objects.requireNonNull(body, "body");
			taskSource = Objects.requireNonNull(taskSource, "taskSource");
			taskParameter = immutableOptional(taskParameter);
			timestampSource = Objects.requireNonNull(timestampSource, "timestampSource");
			audience = Objects.requireNonNull(audience, "audience");
			savedFields = Set.copyOf(savedFields);
		}
	}

	public record StaticFallbackSpec(
		TextChannel channel,
		RichComponent component,
		boolean fallbackSatisfiesRequired
	) {
		public StaticFallbackSpec {
			channel = Objects.requireNonNull(channel, "channel");
			component = Objects.requireNonNull(component, "component");
		}
	}

	public record AssetSpec(
		MessageAssetType type,
		Identifier id,
		MissingAssetMode missing,
		Optional<Identifier> fallback,
		boolean preload,
		boolean requiredForStart
	) {
		public AssetSpec {
			type = Objects.requireNonNull(type, "type");
			id = Objects.requireNonNull(id, "id");
			missing = Objects.requireNonNull(missing, "missing");
			fallback = immutableOptional(fallback);
		}
	}

	public record SoftLimits(
		Optional<Integer> maxVisibleGraphemes,
		Optional<Integer> maxLines,
		Optional<Integer> maxNodes,
		Optional<Integer> maxSoundNodes,
		Optional<Integer> maxCallbackNodes,
		Optional<Integer> maxVariants,
		Optional<Integer> maxActiveInstances,
		Optional<Integer> maxQueueDepth,
		Optional<TimeSpan> maxTotalDuration,
		Optional<Integer> maxPersistedInstances,
		Optional<Integer> maxPendingOffline
	) {
		public SoftLimits {
			maxVisibleGraphemes = immutableOptional(maxVisibleGraphemes);
			maxLines = immutableOptional(maxLines);
			maxNodes = immutableOptional(maxNodes);
			maxSoundNodes = immutableOptional(maxSoundNodes);
			maxCallbackNodes = immutableOptional(maxCallbackNodes);
			maxVariants = immutableOptional(maxVariants);
			maxActiveInstances = immutableOptional(maxActiveInstances);
			maxQueueDepth = immutableOptional(maxQueueDepth);
			maxTotalDuration = immutableOptional(maxTotalDuration);
			maxPersistedInstances = immutableOptional(maxPersistedInstances);
			maxPendingOffline = immutableOptional(maxPendingOffline);
		}

		public static SoftLimits empty() {
			return new SoftLimits(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	public record MessageCueDefinition(
		Identifier id,
		Optional<Identifier> game,
		boolean required,
		CuePolicies policies,
		Map<String, ParameterDefinition> parameters,
		Map<String, DynamicFieldDefinition> fields,
		List<CueNode> nodes,
		Optional<HistorySpec> history,
		Optional<StaticFallbackSpec> staticFallback,
		List<AssetSpec> assets,
		SoftLimits softLimits,
		String canonicalDocument
	) {
		public MessageCueDefinition {
			id = Objects.requireNonNull(id, "id");
			game = immutableOptional(game);
			policies = Objects.requireNonNull(policies, "policies");
			parameters = immutableMap(parameters);
			fields = immutableMap(fields);
			nodes = List.copyOf(nodes);
			history = immutableOptional(history);
			staticFallback = immutableOptional(staticFallback);
			assets = List.copyOf(assets);
			softLimits = Objects.requireNonNull(softLimits, "softLimits");
			canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
		}

		public CueDefaults defaults() {
			return new CueDefaults(
				this.policies.audience().audience(),
				this.policies.timing().clock(),
				this.policies.timing().synchronization(),
				this.policies.defaultConflict(),
				this.policies.concurrency().duplicate(),
				this.policies.capture(),
				this.policies.concurrency().priority()
			);
		}

		public Map<String, DynamicFieldDefinition> cueFields() {
			return this.fields;
		}

		public MessageCueDefinition(
			final Identifier id,
			final Optional<Identifier> game,
			final boolean required,
			final CueDefaults defaults,
			final Map<String, ParameterDefinition> parameters,
			final Map<String, DynamicFieldDefinition> fields,
			final List<CueNode> nodes,
			final String canonicalDocument
		) {
			this(
				id,
				game,
				required,
				new CuePolicies(
					new TimingPolicy(
						defaults.clock(),
						defaults.synchronization(),
						Optional.empty()
					),
					new AudiencePolicy(
						defaults.audience(),
						AudienceEvolution.SNAPSHOT,
						false,
						AudienceLossMode.FINALIZE
					),
					ContextPolicy.unrestricted(),
					new ConcurrencyPolicy(
						defaults.duplicate(),
						Optional.empty(),
						true,
						Set.of(),
						Optional.empty(),
						ChatInterruptMode.FINALIZE,
						defaults.priority(),
						Set.of()
					),
					DeliveryPolicy.standard(),
					LifecyclePolicy.standard(),
					AccessibilityPolicy.standard(),
					Set.of(),
					defaults.capture(),
					defaults.conflict()
				),
				parameters,
				fields,
				nodes,
				Optional.empty(),
				Optional.empty(),
				List.of(),
				SoftLimits.empty(),
				canonicalDocument
			);
		}
	}

	private static <T> Optional<T> immutableOptional(final Optional<T> value) {
		return value == null ? Optional.empty() : value;
	}

	private static <K, V> Map<K, V> immutableMap(final Map<K, V> value) {
		Objects.requireNonNull(value, "value");
		return Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
