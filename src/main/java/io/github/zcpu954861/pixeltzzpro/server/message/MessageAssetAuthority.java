package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AssetSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundRole;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.message.MessageEffectResolver;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageAssetReportVerifier;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageAssetReportVerifier.Outcome;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageAssetReportVerifier.Verification;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetType;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.Entry;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.MissingMode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload.Resolution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Generation-scoped server authority for V3B client asset preflight.
 *
 * <p>Create one authority from the active {@link DefinitionSnapshot}. A play connection calls
 * {@link #openConnection(UUID)}, sends the returned preload/start-gate manifest, extends it through
 * {@link #declareCueAssets(UUID, Identifier)} before first cue playback, forwards reports to
 * {@link #acceptReport(UUID, MessageAssetReportC2SPayload)}, and calls
 * {@link #requiredAssetsForGame(UUID, Identifier)} before approving game start. Recreate the
 * authority and re-open live connections after a definition-generation change.
 */
public final class MessageAssetAuthority {
	public static final int MAX_MANIFEST_ENTRIES =
		MessageAssetManifestS2CPayload.MAX_ENTRIES;
	public static final int MAX_CONNECTIONS = 1_024;
	public static final int MAX_DIAGNOSTICS = 64;

	private static final Comparator<AssetKey> ASSET_ORDER = Comparator
		.comparing((AssetKey key) -> key.type().ordinal())
		.thenComparing(key -> key.id().toString());

	private final CatalogView catalog;
	private final Map<UUID, ConnectionState> connections = new LinkedHashMap<>();

	private MessageAssetAuthority(final CatalogView catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	public static MessageAssetAuthority fromSnapshot(
		final DefinitionSnapshot snapshot
	) {
		Objects.requireNonNull(snapshot, "snapshot");
		return new MessageAssetAuthority(aggregate(snapshot));
	}

	public CatalogView catalog() {
		return this.catalog;
	}

	/**
	 * Starts a fresh report sequence for a play connection. The first cumulative manifest contains
	 * only eager preload declarations and assets required by a game-start gate.
	 */
	public synchronized IssueResult openConnection(final UUID connectionId) {
		Objects.requireNonNull(connectionId, "connectionId");
		if (this.catalog.manifest().isEmpty()) {
			this.connections.remove(connectionId);
			return new IssueResult(
				IssueStatus.CATALOG_BLOCKED,
				Optional.empty(),
				this.catalog.diagnostics()
			);
		}
		if (
			!this.connections.containsKey(connectionId)
				&& this.connections.size() >= MAX_CONNECTIONS
		) {
			return new IssueResult(
				IssueStatus.CONNECTION_LIMIT,
				Optional.empty(),
				List.of(
					diagnostic(
						DiagnosticCode.CONNECTION_LIMIT_EXCEEDED,
						"消息资源连接报告数量超过上限 " + MAX_CONNECTIONS,
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		MessageAssetManifestS2CPayload manifest = manifestForKeys(
			this.catalog.manifest().orElseThrow(),
			1L,
			this.catalog.connectionAssets()
		);
		this.connections.put(connectionId, new ConnectionState(manifest));
		return new IssueResult(
			IssueStatus.ISSUED,
			Optional.of(manifest),
			List.of()
		);
	}

	/**
	 * Extends one live connection with the assets declared by a cue immediately before that cue's
	 * first playback plan. The returned manifest is cumulative so every report remains complete and
	 * the already-observed start-gate assets cannot disappear from a later observation.
	 */
	public synchronized IssueResult declareCueAssets(
		final UUID connectionId,
		final Identifier cueId
	) {
		Objects.requireNonNull(connectionId, "connectionId");
		Objects.requireNonNull(cueId, "cueId");
		if (this.catalog.manifest().isEmpty()) {
			return new IssueResult(
				IssueStatus.CATALOG_BLOCKED,
				Optional.empty(),
				this.catalog.diagnostics()
			);
		}
		ConnectionState state = this.connections.get(connectionId);
		if (state == null) {
			return new IssueResult(
				IssueStatus.MANIFEST_NOT_ISSUED,
				Optional.empty(),
				List.of(
					diagnostic(
						DiagnosticCode.MANIFEST_NOT_ISSUED,
						"当前连接尚未下发消息资源清单",
						Optional.empty(),
						List.of(cueId)
					)
				)
			);
		}
		Set<AssetKey> requested = this.catalog.assetsByCue().getOrDefault(
			cueId,
			Set.of()
		);
		Set<AssetKey> declared = state.manifest.entries()
			.stream()
			.map(Entry::key)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		if (declared.containsAll(requested)) {
			return new IssueResult(
				IssueStatus.ALREADY_DECLARED,
				Optional.empty(),
				List.of()
			);
		}
		if (state.manifest.manifestSequence() == Long.MAX_VALUE) {
			return new IssueResult(
				IssueStatus.SEQUENCE_EXHAUSTED,
				Optional.empty(),
				List.of(
					diagnostic(
						DiagnosticCode.MANIFEST_SEQUENCE_EXHAUSTED,
						"消息资源清单序号已耗尽",
						Optional.empty(),
						List.of(cueId)
					)
				)
			);
		}
		declared.addAll(requested);
		MessageAssetManifestS2CPayload next = manifestForKeys(
			this.catalog.manifest().orElseThrow(),
			state.manifest.manifestSequence() + 1L,
			declared
		);
		state.manifest = next;
		state.verification = null;
		return new IssueResult(IssueStatus.ISSUED, Optional.of(next), List.of());
	}

	public synchronized void closeConnection(final UUID connectionId) {
		this.connections.remove(Objects.requireNonNull(connectionId, "connectionId"));
	}

	public synchronized int connectionCount() {
		return this.connections.size();
	}

	/**
	 * Invalidates the last observation when the client announces resource-reload start. The next
	 * report keeps the existing sequence and manifest contract, but start approval becomes pending
	 * until that higher-sequence report is accepted.
	 */
	public synchronized boolean markReportPending(final UUID connectionId) {
		ConnectionState state = this.connections.get(
			Objects.requireNonNull(connectionId, "connectionId")
		);
		if (state == null) {
			return false;
		}
		state.verification = null;
		return true;
	}

	/**
	 * Consumes report sequence numbers after the monotonic check, even when later validation fails.
	 * This prevents a malformed frame from being replayed while still allowing a higher-sequence
	 * resource-reload report to recover the connection.
	 */
	public synchronized ReportResult acceptReport(
		final UUID connectionId,
		final MessageAssetReportC2SPayload report
	) {
		Objects.requireNonNull(connectionId, "connectionId");
		Objects.requireNonNull(report, "report");
		if (this.catalog.manifest().isEmpty()) {
			return rejectedReport(
				ReportStatus.CATALOG_BLOCKED,
				this.catalog.diagnostics()
			);
		}
		ConnectionState state = this.connections.get(connectionId);
		if (state == null) {
			return rejectedReport(
				ReportStatus.MANIFEST_NOT_ISSUED,
				List.of(
					diagnostic(
						DiagnosticCode.MANIFEST_NOT_ISSUED,
						"当前连接尚未下发消息资源清单",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		if (report.reportSequence() <= state.lastReportSequence) {
			return rejectedReport(
				ReportStatus.NON_INCREASING_SEQUENCE,
				List.of(
					diagnostic(
						DiagnosticCode.REPORT_SEQUENCE_NOT_INCREASING,
						"消息资源报告序号必须严格递增",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		state.lastReportSequence = report.reportSequence();
		if (report.generation() != state.manifest.generation()) {
			return rejectedReport(
				ReportStatus.GENERATION_MISMATCH,
				List.of(
					diagnostic(
						DiagnosticCode.REPORT_GENERATION_MISMATCH,
						"消息资源报告与当前连接清单代次不匹配",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		if (report.manifestSequence() != state.manifest.manifestSequence()) {
			return rejectedReport(
				ReportStatus.MANIFEST_SEQUENCE_MISMATCH,
				List.of(
					diagnostic(
						DiagnosticCode.REPORT_MANIFEST_SEQUENCE_MISMATCH,
						"消息资源报告与当前连接清单序号不匹配",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		try {
			Verification verification = MessageAssetReportVerifier.verify(
				state.manifest,
				report
			);
			state.verification = verification;
			return new ReportResult(
				ReportStatus.ACCEPTED,
				Optional.of(verification),
				List.of()
			);
		} catch (IllegalArgumentException invalid) {
			state.verification = null;
			return rejectedReport(
				ReportStatus.INVALID_REPORT,
				List.of(
					diagnostic(
						DiagnosticCode.REPORT_INVALID,
						"消息资源报告未完整匹配已下发清单",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
	}

	/**
	 * Returns the latest client-observed effective resource IDs for this connection.
	 *
	 * <p>An empty outer optional means that the newest cumulative manifest has not yet received a
	 * matching report. An empty value inside the map is an intentional silent resolution for a
	 * missing {@code silent} or {@code block_start} asset; it must never be replaced with the
	 * original identifier by the playback projector.
	 */
	public synchronized Optional<Map<AssetKey, Optional<Identifier>>> effectiveAssets(
		final UUID connectionId
	) {
		ConnectionState state = this.connections.get(
			Objects.requireNonNull(connectionId, "connectionId")
		);
		if (state == null || state.verification == null) {
			return Optional.empty();
		}
		Map<AssetKey, Optional<Identifier>> effective = new LinkedHashMap<>();
		for (Outcome outcome : state.verification.outcomes()) {
			effective.put(outcome.expected().key(), outcome.effectiveId());
		}
		return Optional.of(Collections.unmodifiableMap(effective));
	}

	/** Returns this connection's fail-closed gate for one game. */
	public synchronized StartGateResult requiredAssetsForGame(
		final UUID connectionId,
		final Identifier gameId
	) {
		Objects.requireNonNull(connectionId, "connectionId");
		Objects.requireNonNull(gameId, "gameId");
		if (this.catalog.manifest().isEmpty()) {
			return new StartGateResult(
				false,
				0,
				0,
				this.catalog.diagnostics()
			);
		}

		Set<AssetKey> required = new LinkedHashSet<>(
			this.catalog.globalRequiredAssets()
		);
		required.addAll(
			this.catalog.requiredAssetsByGame().getOrDefault(gameId, Set.of())
		);
		List<AssetKey> ordered = required.stream().sorted(ASSET_ORDER).toList();
		ConnectionState state = this.connections.get(connectionId);
		if (state == null) {
			return new StartGateResult(
				false,
				ordered.size(),
				ordered.size(),
				List.of(
					diagnostic(
						DiagnosticCode.MANIFEST_NOT_ISSUED,
						"当前连接尚未下发消息资源清单",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		if (state.verification == null) {
			return new StartGateResult(
				false,
				ordered.size(),
				ordered.size(),
				List.of(
					diagnostic(
						DiagnosticCode.REPORT_PENDING,
						"客户端尚未完成开局必需消息资源检查",
						Optional.empty(),
						List.of()
					)
				)
			);
		}
		if (ordered.isEmpty()) {
			return new StartGateResult(true, 0, 0, List.of());
		}

		Map<AssetKey, Outcome> outcomes = new LinkedHashMap<>();
		for (Outcome outcome : state.verification.outcomes()) {
			outcomes.put(outcome.expected().key(), outcome);
		}
		List<Diagnostic> diagnostics = new ArrayList<>();
		int missing = 0;
		for (AssetKey key : ordered) {
			Outcome outcome = outcomes.get(key);
			if (outcome == null || outcome.resolution() == Resolution.MISSING) {
				missing++;
				if (diagnostics.size() < MAX_DIAGNOSTICS) {
					diagnostics.add(
						diagnostic(
							DiagnosticCode.REQUIRED_ASSET_MISSING,
							"缺少开局必需消息资源：" + serialized(key),
							Optional.of(key),
							List.of()
						)
					);
				}
			}
		}
		return new StartGateResult(
			missing == 0,
			ordered.size(),
			missing,
			diagnostics
		);
	}

	private static CatalogView aggregate(final DefinitionSnapshot snapshot) {
		DiagnosticCollector diagnostics = new DiagnosticCollector();
		if (snapshot.generation() < 0L) {
			diagnostics.add(
				diagnostic(
					DiagnosticCode.CATALOG_GENERATION_INVALID,
					"消息资源清单代次无效",
					Optional.empty(),
					List.of()
				)
			);
			return blockedCatalog(snapshot.generation(), diagnostics);
		}

		Map<AssetKey, DeclaredAsset> declared = new LinkedHashMap<>();
		Map<Identifier, Set<AssetKey>> requiredByGame = new LinkedHashMap<>();
		Map<Identifier, Set<AssetKey>> assetsByCue = new LinkedHashMap<>();
		Set<AssetKey> globalRequired = new LinkedHashSet<>();
		Set<AssetKey> connectionAssets = new LinkedHashSet<>();
		List<MessageCueDefinition> cues = snapshot.messageCatalog()
			.messageCues()
			.values()
			.stream()
			.sorted(Comparator.comparing(cue -> cue.id().toString()))
			.toList();
		boolean overflow = false;
		// Explicit cue assets establish preload and start-gate policy first. Sound uses are
		// collected afterwards so a data pack may deliberately elevate a used sound to an eager
		// or required asset without duplicating it in the wire manifest.
		outer:
		for (MessageCueDefinition cue : cues) {
			for (AssetSpec asset : cue.assets()) {
				Entry entry;
				try {
					entry = wireEntry(asset);
				} catch (RuntimeException invalid) {
					diagnostics.add(
						diagnostic(
							DiagnosticCode.ASSET_DECLARATION_INVALID,
							"消息资源声明无效：" + asset.type() + ":" + asset.id(),
							Optional.empty(),
							List.of(cue.id())
						)
					);
					continue;
				}
				AssetKey key = entry.key();
				assetsByCue.computeIfAbsent(
					cue.id(),
					ignored -> new LinkedHashSet<>()
				).add(key);
				if (entry.preload() || entry.requiredForStart()) {
					connectionAssets.add(key);
				}
				DeclaredAsset previous = declared.get(key);
				if (previous == null) {
					if (declared.size() >= MAX_MANIFEST_ENTRIES) {
						overflow = true;
						break outer;
					}
					declared.put(key, new DeclaredAsset(entry, cue.id(), true));
				} else if (!previous.entry.equals(entry)) {
					diagnostics.add(
						diagnostic(
							DiagnosticCode.ASSET_DECLARATION_CONFLICT,
							"消息资源声明冲突：" + serialized(key),
							Optional.of(key),
							List.of(previous.firstCue, cue.id())
						)
					);
					continue;
				}

				if (entry.requiredForStart()) {
					if (cue.game().isPresent()) {
						requiredByGame.computeIfAbsent(
							cue.game().orElseThrow(),
							ignored -> new LinkedHashSet<>()
						).add(key);
					} else {
						globalRequired.add(key);
					}
				}
			}
		}
		if (!overflow) {
			outer:
			for (MessageCueDefinition cue : cues) {
				List<Entry> implicitEntries;
				try {
					implicitEntries = implicitSoundEntries(snapshot, cue);
				} catch (IllegalArgumentException conflict) {
					diagnostics.add(
						diagnostic(
							DiagnosticCode.ASSET_DECLARATION_CONFLICT,
							"消息声音资源声明冲突：" + conflict.getMessage(),
							Optional.empty(),
							List.of(cue.id())
						)
					);
					continue;
				}
				for (Entry entry : implicitEntries) {
					AssetKey key = entry.key();
					assetsByCue.computeIfAbsent(
						cue.id(),
						ignored -> new LinkedHashSet<>()
					).add(key);
					DeclaredAsset previous = declared.get(key);
					if (previous == null) {
						if (declared.size() >= MAX_MANIFEST_ENTRIES) {
							overflow = true;
							break outer;
						}
						declared.put(key, new DeclaredAsset(entry, cue.id(), false));
					} else if (entry.missing() == MissingMode.BLOCK_START) {
						// block_start is a safety promise made by the sound use itself. It cannot
						// be weakened by a separate optional declaration of the same identifier.
						Entry required = new Entry(
							entry.type(),
							entry.id(),
							MissingMode.BLOCK_START,
							Optional.empty(),
							previous.entry.preload() || entry.preload(),
							true
						);
						declared.put(
							key,
							new DeclaredAsset(required, previous.firstCue, previous.explicit)
						);
					} else if (
						!previous.explicit
							&& previous.entry.missing() != MissingMode.BLOCK_START
							&& !previous.entry.equals(entry)
					) {
						diagnostics.add(
							diagnostic(
								DiagnosticCode.ASSET_DECLARATION_CONFLICT,
								"同一声音在不同消息中声明了不兼容的缺失处理：" + entry.id(),
								Optional.of(key),
								List.of(previous.firstCue, cue.id())
							)
						);
					}
					Entry effective = declared.get(key).entry;
					if (effective.preload() || effective.requiredForStart()) {
						connectionAssets.add(key);
					}
					if (effective.requiredForStart()) {
						if (cue.game().isPresent()) {
							requiredByGame.computeIfAbsent(
								cue.game().orElseThrow(),
								ignored -> new LinkedHashSet<>()
							).add(key);
						} else {
							globalRequired.add(key);
						}
					}
				}
			}
		}
		if (overflow) {
			diagnostics.add(
				diagnostic(
					DiagnosticCode.MANIFEST_LIMIT_EXCEEDED,
					"消息资源清单超过上限 " + MAX_MANIFEST_ENTRIES,
					Optional.empty(),
					List.of()
				)
			);
		}
		if (diagnostics.totalCount() > 0) {
			return new CatalogView(
				snapshot.generation(),
				Optional.empty(),
				immutableAssetMap(requiredByGame),
				immutableAssetSet(globalRequired),
				immutableAssetMap(assetsByCue),
				immutableAssetSet(connectionAssets),
				diagnostics.retained(),
				diagnostics.totalCount()
			);
		}

		List<Entry> entries = declared.values()
			.stream()
			.map(DeclaredAsset::entry)
			.sorted(Comparator.comparing(Entry::key, ASSET_ORDER))
			.toList();
		MessageAssetManifestS2CPayload manifest =
			new MessageAssetManifestS2CPayload(snapshot.generation(), entries);
		return new CatalogView(
			snapshot.generation(),
			Optional.of(manifest),
			immutableAssetMap(requiredByGame),
			immutableAssetSet(globalRequired),
			immutableAssetMap(assetsByCue),
			immutableAssetSet(connectionAssets),
			List.of(),
			0
		);
	}

	private static CatalogView blockedCatalog(
		final long generation,
		final DiagnosticCollector diagnostics
	) {
		return new CatalogView(
			generation,
			Optional.empty(),
			Map.of(),
			Set.of(),
			Map.of(),
			Set.of(),
			diagnostics.retained(),
			diagnostics.totalCount()
		);
	}

	private static Entry wireEntry(final AssetSpec asset) {
		AssetType type = switch (asset.type()) {
			case FONT -> AssetType.FONT;
			case SOUND -> AssetType.SOUND;
		};
		MissingMode missing = switch (asset.missing()) {
			case SILENT -> MissingMode.SILENT;
			case FALLBACK -> MissingMode.FALLBACK;
			case BLOCK_START -> MissingMode.BLOCK_START;
		};
		return new Entry(
			type,
			asset.id(),
			missing,
			asset.fallback(),
			asset.preload(),
			asset.requiredForStart()
		);
	}

	private static List<Entry> implicitSoundEntries(
		final DefinitionSnapshot snapshot,
		final MessageCueDefinition cue
	) {
		Map<AssetKey, Entry> entries = new LinkedHashMap<>();
		for (CueNode node : cue.nodes()) {
			if (node instanceof SoundNode sound) {
				mergeImplicitSound(
					entries,
					sound.sound(),
					sound.missing(),
					sound.fallback()
				);
				continue;
			}
			if (!(node instanceof TextNode text)) {
				continue;
			}
			collectCharacterSound(
				entries,
				text.content(),
				snapshot.messageCatalog().textEffects()
			);
			text.variants().forEach(variant ->
				collectCharacterSound(
					entries,
					variant.content(),
					snapshot.messageCatalog().textEffects()
				)
			);
		}
		return List.copyOf(entries.values());
	}

	private static void collectCharacterSound(
		final Map<AssetKey, Entry> entries,
		final TextContent content,
		final Map<Identifier, io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition> effects
	) {
		if (content.characterSoundRole() == CharacterSoundRole.MUTED) {
			return;
		}
		MessageEffectResolver.Resolution resolution = MessageEffectResolver.resolve(
			content.effect(),
			effects
		);
		if (!resolution.successful() || resolution.effect().isEmpty()) {
			return;
		}
		resolution.effect().orElseThrow().characterSound().ifPresent(sound ->
			mergeImplicitSound(entries, sound)
		);
	}

	private static void mergeImplicitSound(
		final Map<AssetKey, Entry> entries,
		final CharacterSoundSpec sound
	) {
		mergeImplicitSound(entries, sound.sound(), sound.missing(), sound.fallback());
	}

	private static void mergeImplicitSound(
		final Map<AssetKey, Entry> entries,
		final Identifier sound,
		final io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MissingAssetMode missing,
		final Optional<Identifier> fallback
	) {
		MissingMode wireMissing = switch (missing) {
			case SILENT -> MissingMode.SILENT;
			case FALLBACK -> MissingMode.FALLBACK;
			case BLOCK_START -> MissingMode.BLOCK_START;
		};
		AssetKey key = new AssetKey(AssetType.SOUND, sound);
		Entry next = new Entry(
			AssetType.SOUND,
			sound,
			wireMissing,
			fallback,
			wireMissing == MissingMode.BLOCK_START,
			wireMissing == MissingMode.BLOCK_START
		);
		Entry existing = entries.get(key);
		if (existing == null || existing.equals(next)) {
			entries.putIfAbsent(key, next);
			return;
		}
		if (existing.missing() == MissingMode.BLOCK_START) {
			return;
		}
		if (next.missing() == MissingMode.BLOCK_START) {
			entries.put(key, next);
			return;
		}
		throw new IllegalArgumentException(
			"one cue uses sound " + sound + " with incompatible missing-resource policies"
		);
	}

	private static MessageAssetManifestS2CPayload manifestForKeys(
		final MessageAssetManifestS2CPayload catalog,
		final long sequence,
		final Set<AssetKey> keys
	) {
		List<Entry> entries = catalog.entries()
			.stream()
			.filter(entry -> keys.contains(entry.key()))
			.sorted(Comparator.comparing(Entry::key, ASSET_ORDER))
			.toList();
		return new MessageAssetManifestS2CPayload(
			catalog.generation(),
			sequence,
			entries
		);
	}

	private static Map<Identifier, Set<AssetKey>> immutableAssetMap(
		final Map<Identifier, Set<AssetKey>> source
	) {
		List<Identifier> games = source.keySet()
			.stream()
			.sorted(Comparator.comparing(Identifier::toString))
			.toList();
		Map<Identifier, Set<AssetKey>> copy = new LinkedHashMap<>();
		for (Identifier game : games) {
			copy.put(game, immutableAssetSet(source.get(game)));
		}
		return Collections.unmodifiableMap(copy);
	}

	private static Set<AssetKey> immutableAssetSet(
		final Collection<AssetKey> source
	) {
		LinkedHashSet<AssetKey> copy = new LinkedHashSet<>(
			source.stream().sorted(ASSET_ORDER).toList()
		);
		return Collections.unmodifiableSet(copy);
	}

	private static String serialized(final AssetKey key) {
		return key.type().name().toLowerCase(java.util.Locale.ROOT)
			+ " "
			+ key.id();
	}

	private static Diagnostic diagnostic(
		final DiagnosticCode code,
		final String message,
		final Optional<AssetKey> asset,
		final List<Identifier> sourceCues
	) {
		return new Diagnostic(code, message, asset, sourceCues);
	}

	private static ReportResult rejectedReport(
		final ReportStatus status,
		final List<Diagnostic> diagnostics
	) {
		return new ReportResult(status, Optional.empty(), diagnostics);
	}

	private record DeclaredAsset(Entry entry, Identifier firstCue, boolean explicit) {
	}

	private static final class ConnectionState {
		private MessageAssetManifestS2CPayload manifest;
		private long lastReportSequence = -1L;
		private Verification verification;

		private ConnectionState(final MessageAssetManifestS2CPayload manifest) {
			this.manifest = manifest;
		}
	}

	private static final class DiagnosticCollector {
		private final List<Diagnostic> retained = new ArrayList<>();
		private int totalCount;

		private void add(final Diagnostic diagnostic) {
			this.totalCount++;
			if (this.retained.size() < MAX_DIAGNOSTICS) {
				this.retained.add(diagnostic);
			}
		}

		private List<Diagnostic> retained() {
			return List.copyOf(this.retained);
		}

		private int totalCount() {
			return this.totalCount;
		}
	}

	public enum IssueStatus {
		ISSUED,
		ALREADY_DECLARED,
		CATALOG_BLOCKED,
		CONNECTION_LIMIT,
		MANIFEST_NOT_ISSUED,
		SEQUENCE_EXHAUSTED
	}

	public enum ReportStatus {
		ACCEPTED,
		CATALOG_BLOCKED,
		MANIFEST_NOT_ISSUED,
		NON_INCREASING_SEQUENCE,
		GENERATION_MISMATCH,
		MANIFEST_SEQUENCE_MISMATCH,
		INVALID_REPORT
	}

	public enum DiagnosticCode {
		CATALOG_GENERATION_INVALID,
		ASSET_DECLARATION_INVALID,
		ASSET_DECLARATION_CONFLICT,
		MANIFEST_LIMIT_EXCEEDED,
		CONNECTION_LIMIT_EXCEEDED,
		MANIFEST_NOT_ISSUED,
		REPORT_SEQUENCE_NOT_INCREASING,
		REPORT_GENERATION_MISMATCH,
		REPORT_MANIFEST_SEQUENCE_MISMATCH,
		MANIFEST_SEQUENCE_EXHAUSTED,
		REPORT_INVALID,
		REPORT_PENDING,
		REQUIRED_ASSET_MISSING
	}

	public record Diagnostic(
		DiagnosticCode code,
		String message,
		Optional<AssetKey> asset,
		List<Identifier> sourceCues
	) {
		public Diagnostic {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			if (message.isBlank()) {
				throw new IllegalArgumentException("diagnostic message cannot be blank");
			}
			asset = Objects.requireNonNull(asset, "asset");
			sourceCues = List.copyOf(sourceCues);
		}
	}

	public record CatalogView(
		long generation,
		Optional<MessageAssetManifestS2CPayload> manifest,
		Map<Identifier, Set<AssetKey>> requiredAssetsByGame,
		Set<AssetKey> globalRequiredAssets,
		Map<Identifier, Set<AssetKey>> assetsByCue,
		Set<AssetKey> connectionAssets,
		List<Diagnostic> diagnostics,
		int totalDiagnosticCount
	) {
		public CatalogView {
			manifest = Objects.requireNonNull(manifest, "manifest");
			requiredAssetsByGame = immutableAssetMap(requiredAssetsByGame);
			globalRequiredAssets = immutableAssetSet(globalRequiredAssets);
			assetsByCue = immutableAssetMap(assetsByCue);
			connectionAssets = immutableAssetSet(connectionAssets);
			diagnostics = List.copyOf(diagnostics);
			if (
				totalDiagnosticCount < diagnostics.size()
					|| diagnostics.size() > MAX_DIAGNOSTICS
			) {
				throw new IllegalArgumentException("invalid catalog diagnostic counts");
			}
			if (manifest.isPresent() != (totalDiagnosticCount == 0)) {
				throw new IllegalArgumentException(
					"catalog manifest and diagnostics disagree"
				);
			}
		}

		public boolean blocked() {
			return this.manifest.isEmpty();
		}
	}

	public record IssueResult(
		IssueStatus status,
		Optional<MessageAssetManifestS2CPayload> manifest,
		List<Diagnostic> diagnostics
	) {
		public IssueResult {
			status = Objects.requireNonNull(status, "status");
			manifest = Objects.requireNonNull(manifest, "manifest");
			diagnostics = List.copyOf(diagnostics);
			if ((status == IssueStatus.ISSUED) != manifest.isPresent()) {
				throw new IllegalArgumentException("issue status and manifest disagree");
			}
		}
	}

	public record ReportResult(
		ReportStatus status,
		Optional<Verification> verification,
		List<Diagnostic> diagnostics
	) {
		public ReportResult {
			status = Objects.requireNonNull(status, "status");
			verification = Objects.requireNonNull(verification, "verification");
			diagnostics = List.copyOf(diagnostics);
			if ((status == ReportStatus.ACCEPTED) != verification.isPresent()) {
				throw new IllegalArgumentException(
					"report status and verification disagree"
				);
			}
		}
	}

	public record StartGateResult(
		boolean ready,
		int requiredCount,
		int missingCount,
		List<Diagnostic> diagnostics
	) {
		public StartGateResult {
			if (
				requiredCount < 0
					|| missingCount < 0
					|| missingCount > requiredCount
			) {
				throw new IllegalArgumentException("invalid start-gate counts");
			}
			diagnostics = List.copyOf(diagnostics);
			if (ready != (missingCount == 0 && diagnostics.isEmpty())) {
				throw new IllegalArgumentException(
					"start gate state and diagnostics disagree"
				);
			}
		}
	}
}
