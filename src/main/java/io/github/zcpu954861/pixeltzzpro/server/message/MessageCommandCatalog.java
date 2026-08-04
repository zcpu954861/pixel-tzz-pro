package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler.DefinitionType;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DisabledMessageDefinition;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.DocumentKey;
import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot.SourceDocument;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Pure bounded catalog projection used by the trusted V3B list/inspect/preview commands. */
public final class MessageCommandCatalog {
	public static final int PAGE_SIZE = 12;
	public static final int MAX_RETAINED_DIAGNOSTICS = 4;
	private static final int MAX_DIAGNOSTIC_LENGTH = 240;

	private MessageCommandCatalog() {
	}

	public static QueryResult<CatalogPage> list(
		final DefinitionSnapshot snapshot,
		final int requestedPage
	) {
		Objects.requireNonNull(snapshot, "snapshot");
		if (requestedPage < 1) {
			return QueryResult.rejected("页码必须从 1 开始。");
		}
		List<CatalogEntry> entries = allEntries(snapshot);
		int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		if (requestedPage > pages) {
			return QueryResult.rejected(
				"动态消息目录只有 " + pages + " 页。"
			);
		}
		int from = Math.min(entries.size(), (requestedPage - 1) * PAGE_SIZE);
		int to = Math.min(entries.size(), from + PAGE_SIZE);
		return QueryResult.accepted(
			new CatalogPage(
				snapshot.generation(),
				requestedPage,
				pages,
				entries.size(),
				entries.subList(from, to)
			),
			"动态消息目录已读取。"
		);
	}

	public static QueryResult<CueInspection> inspect(
		final DefinitionSnapshot snapshot,
		final Identifier cueId
	) {
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(cueId, "cueId");
		MessageCueDefinition cue = snapshot.messageCatalog().messageCues().get(cueId);
		if (cue != null) {
			return QueryResult.accepted(
				inspectEnabled(snapshot, cue),
				"动态消息定义可用。"
			);
		}
		DisabledMessageDefinition disabled = disabledCue(snapshot, cueId).orElse(null);
		if (disabled == null) {
			return QueryResult.rejected("未注册动态消息「" + cueId + "」。");
		}
		return QueryResult.accepted(
			new CueInspection(
				cueId,
				CatalogStatus.DISABLED,
				disabled.required(),
				disabled.game(),
				Map.of(),
				0,
				0,
				List.of(),
				List.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				false,
				0,
				0,
				Optional.of(sourceInfo(disabled.sourceDocument())),
				diagnostics(disabled)
			),
			"动态消息定义已禁用。"
		);
	}

	public static QueryResult<HostMessagePreviewSanitizer.HostPreview> preview(
		final DefinitionSnapshot snapshot,
		final Identifier cueId,
		final UUID host
	) {
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(cueId, "cueId");
		Objects.requireNonNull(host, "host");
		MessageCueDefinition cue = snapshot.messageCatalog().messageCues().get(cueId);
		if (cue == null) {
			QueryResult<CueInspection> inspection = inspect(snapshot, cueId);
			if (inspection.accepted()) {
				return QueryResult.rejected(
					"动态消息「" + cueId + "」已禁用，不能预览。"
				);
			}
			return QueryResult.rejected(inspection.message());
		}
		try {
			return QueryResult.accepted(
				HostMessagePreviewSanitizer.sanitize(cue, host),
				"已生成仅对主持人可见的无副作用预览。"
			);
		} catch (RuntimeException error) {
			return QueryResult.rejected(
				"动态消息「" + cueId + "」不能安全预览：" + bounded(error.getMessage())
			);
		}
	}

	private static List<CatalogEntry> allEntries(final DefinitionSnapshot snapshot) {
		Map<Identifier, CatalogEntry> entries = new LinkedHashMap<>();
		snapshot.messageCatalog().messageCues().forEach((id, cue) -> entries.put(
			id,
			new CatalogEntry(
				id,
				CatalogStatus.ENABLED,
				cue.required(),
				cue.nodes().size(),
				0
			)
		));
		snapshot.messageCatalog().disabled().forEach((key, disabled) -> {
			if (key.type() != DefinitionType.MESSAGE_CUE) {
				return;
			}
			entries.putIfAbsent(
				key.id(),
				new CatalogEntry(
					key.id(),
					CatalogStatus.DISABLED,
					disabled.required(),
					0,
					disabled.totalDiagnosticCount()
				)
			);
		});
		return entries.values().stream()
			.sorted(Comparator.comparing(entry -> entry.id().toString()))
			.toList();
	}

	private static CueInspection inspectEnabled(
		final DefinitionSnapshot snapshot,
		final MessageCueDefinition cue
	) {
		Map<TextChannel, Integer> channels = new EnumMap<>(TextChannel.class);
		int sounds = 0;
		int callbacks = 0;
		for (var node : cue.nodes()) {
			if (node instanceof TextNode text) {
				channels.merge(text.channel(), 1, Integer::sum);
			} else if (node instanceof SoundNode) {
				sounds++;
			} else if (node instanceof CallbackNode) {
				callbacks++;
			}
		}
		List<ParameterSummary> parameters = cue.parameters().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> parameter(entry.getKey(), entry.getValue()))
			.toList();
		List<String> audiences = cue.policies().audience().audience().selectors().stream()
			.map(selector -> selector.source().name().toLowerCase(Locale.ROOT))
			.distinct()
			.toList();
		int requiredAssets = (int)cue.assets().stream()
			.filter(asset -> asset.requiredForStart())
			.count();
		SourceDocument source = snapshot.sourceDocuments().get(
			new DocumentKey(DefinitionType.MESSAGE_CUE, cue.id())
		);
		return new CueInspection(
			cue.id(),
			CatalogStatus.ENABLED,
			cue.required(),
			cue.game(),
			channels,
			sounds,
			callbacks,
			parameters,
			audiences,
			cue.policies().context().games(),
			cue.policies().context().phases(),
			cue.policies().context().tasks(),
			cue.history().isPresent(),
			cue.assets().size(),
			requiredAssets,
			Optional.ofNullable(source).map(MessageCommandCatalog::sourceInfo),
			List.of()
		);
	}

	private static ParameterSummary parameter(
		final String name,
		final ParameterDefinition definition
	) {
		return new ParameterSummary(
			name,
			definition.type().name().toLowerCase(Locale.ROOT),
			definition.required(),
			definition.canonicalDefault().isPresent(),
			definition.sensitive()
		);
	}

	private static Optional<DisabledMessageDefinition> disabledCue(
		final DefinitionSnapshot snapshot,
		final Identifier cueId
	) {
		return Optional.ofNullable(snapshot.messageCatalog().disabled().get(
			new DocumentKey(DefinitionType.MESSAGE_CUE, cueId)
		));
	}

	private static List<String> diagnostics(final DisabledMessageDefinition disabled) {
		List<String> result = disabled.diagnostics().stream()
			.limit(MAX_RETAINED_DIAGNOSTICS)
			.map(problem -> bounded(
				problem.code() + " " + problem.pointer() + "：" + problem.message()
			))
			.toList();
		if (result.isEmpty() && disabled.totalDiagnosticCount() > 0) {
			return List.of("诊断详情未保留，请查看服务端日志。");
		}
		return result;
	}

	private static SourceInfo sourceInfo(final SourceDocument source) {
		return new SourceInfo(source.sourcePack(), source.resource(), source.sha256());
	}

	private static String bounded(final String value) {
		String normalized = value == null || value.isBlank()
			? "未知原因"
			: value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').strip();
		return normalized.length() <= MAX_DIAGNOSTIC_LENGTH
			? normalized
			: normalized.substring(0, MAX_DIAGNOSTIC_LENGTH - 1) + "…";
	}

	public enum CatalogStatus {
		ENABLED,
		DISABLED
	}

	public record CatalogEntry(
		Identifier id,
		CatalogStatus status,
		boolean required,
		int nodeCount,
		int diagnosticCount
	) {
		public CatalogEntry {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(status, "status");
			if (nodeCount < 0 || diagnosticCount < 0) {
				throw new IllegalArgumentException("negative message catalog count");
			}
		}
	}

	public record CatalogPage(
		long generation,
		int page,
		int pages,
		int totalEntries,
		List<CatalogEntry> entries
	) {
		public CatalogPage {
			entries = List.copyOf(entries);
			if (
				generation < 0L
					|| page < 1
					|| pages < 1
					|| page > pages
					|| totalEntries < entries.size()
					|| entries.size() > PAGE_SIZE
			) {
				throw new IllegalArgumentException("invalid message catalog page");
			}
		}
	}

	public record ParameterSummary(
		String name,
		String type,
		boolean required,
		boolean hasDefault,
		boolean sensitive
	) {
		public ParameterSummary {
			name = Objects.requireNonNull(name, "name");
			type = Objects.requireNonNull(type, "type");
			if (name.isBlank() || type.isBlank()) {
				throw new IllegalArgumentException("blank parameter summary");
			}
		}
	}

	public record SourceInfo(String pack, Identifier resource, String sha256) {
		public SourceInfo {
			pack = Objects.requireNonNull(pack, "pack");
			Objects.requireNonNull(resource, "resource");
			sha256 = Objects.requireNonNull(sha256, "sha256");
			if (pack.isBlank() || sha256.isBlank()) {
				throw new IllegalArgumentException("blank message source information");
			}
		}
	}

	public record CueInspection(
		Identifier id,
		CatalogStatus status,
		boolean required,
		Optional<Identifier> game,
		Map<TextChannel, Integer> textChannels,
		int soundNodes,
		int callbackNodes,
		List<ParameterSummary> parameters,
		List<String> audienceSources,
		Set<Identifier> contextGames,
		Set<Identifier> contextPhases,
		Set<Identifier> contextTasks,
		boolean historyEnabled,
		int assetCount,
		int requiredAssetCount,
		Optional<SourceInfo> source,
		List<String> diagnostics
	) {
		public CueInspection {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(status, "status");
			game = Objects.requireNonNull(game, "game");
			textChannels = Map.copyOf(textChannels);
			parameters = List.copyOf(parameters);
			audienceSources = List.copyOf(audienceSources);
			contextGames = Set.copyOf(contextGames);
			contextPhases = Set.copyOf(contextPhases);
			contextTasks = Set.copyOf(contextTasks);
			source = Objects.requireNonNull(source, "source");
			diagnostics = List.copyOf(diagnostics);
			if (
				soundNodes < 0
					|| callbackNodes < 0
					|| assetCount < 0
					|| requiredAssetCount < 0
					|| requiredAssetCount > assetCount
					|| diagnostics.size() > MAX_RETAINED_DIAGNOSTICS
			) {
				throw new IllegalArgumentException("invalid cue inspection counts");
			}
		}

		public int textNodeCount() {
			return this.textChannels.values().stream().mapToInt(Integer::intValue).sum();
		}

		public int nodeCount() {
			return textNodeCount() + this.soundNodes + this.callbackNodes;
		}
	}

	public record QueryResult<T>(Optional<T> value, String message) {
		public QueryResult {
			value = Objects.requireNonNull(value, "value");
			message = Objects.requireNonNull(message, "message").strip();
			if (message.isBlank()) {
				throw new IllegalArgumentException("blank message command query result");
			}
		}

		public boolean accepted() {
			return this.value.isPresent();
		}

		private static <T> QueryResult<T> accepted(final T value, final String message) {
			return new QueryResult<>(Optional.of(value), message);
		}

		private static <T> QueryResult<T> rejected(final String message) {
			return new QueryResult<>(Optional.empty(), message);
		}
	}
}
