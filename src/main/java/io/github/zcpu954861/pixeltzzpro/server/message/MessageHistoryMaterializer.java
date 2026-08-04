package io.github.zcpu954861.pixeltzzpro.server.message;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistorySpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTimestampSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.IdentifierKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4.MessageHistoryRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Pure per-viewer conversion from frozen cue values to a static persisted history record.
 *
 * <p>This boundary accepts no audience resolver and stores no field resolver input. Every template
 * component and referenced cue field is decoded with the vanilla component codec, flattened to
 * readable text once, and then forgotten. A failed field, component, task, timestamp, or record
 * bound returns diagnostics without a partial record.
 */
public final class MessageHistoryMaterializer {
	private static final long MAX_SAFE_GAME_TICK =
		Long.MAX_VALUE / TimeSpan.NANOS_PER_TICK;

	private MessageHistoryMaterializer() {
	}

	public static MaterializationResult materialize(final Request request) {
		Objects.requireNonNull(request, "request");
		MessageCueDefinition cue = request.cue();
		HistorySpec history = request.history();
		if (history.savedFields().size() > WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELDS) {
			return failure(
				DiagnosticCode.RECORD_LIMIT_EXCEEDED,
				"消息回顾保存字段数量超过上限"
			);
		}
		ComponentTemplate titleTemplate = localized(history.title(), request.locale());
		ComponentTemplate bodyTemplate = localized(history.body(), request.locale());

		Set<String> requiredFields = new LinkedHashSet<>(history.savedFields());
		Diagnostic templateDiagnostic = collectTemplateFields(
			titleTemplate,
			cue,
			requiredFields,
			"标题"
		);
		if (templateDiagnostic != null) {
			return MaterializationResult.failed(templateDiagnostic);
		}
		templateDiagnostic = collectTemplateFields(
			bodyTemplate,
			cue,
			requiredFields,
			"正文"
		);
		if (templateDiagnostic != null) {
			return MaterializationResult.failed(templateDiagnostic);
		}
		for (String field : history.savedFields()) {
			if (!cue.fields().containsKey(field)) {
				return failure(
					DiagnosticCode.UNDECLARED_CUE_FIELD,
					"消息回顾保存字段「" + field + "」不是已声明的 cue 级字段"
				);
			}
		}

		Map<String, String> decodedFields = new LinkedHashMap<>();
		for (String field : requiredFields.stream().sorted().toList()) {
			String canonical = request.frozenCueFields().get(field);
			if (canonical == null) {
				return failure(
					DiagnosticCode.MISSING_FROZEN_FIELD,
					"消息回顾缺少已冻结字段「" + field + "」"
				);
			}
			DecodeResult decoded = decodeComponent(canonical, "字段「" + field + "」");
			if (!decoded.successful()) {
				return MaterializationResult.failed(decoded.diagnostic().orElseThrow());
			}
			decodedFields.put(field, decoded.text().orElseThrow());
		}

		RenderResult title = render(titleTemplate, decodedFields, "标题");
		if (!title.successful()) {
			return MaterializationResult.failed(title.diagnostic().orElseThrow());
		}
		RenderResult body = render(bodyTemplate, decodedFields, "正文");
		if (!body.successful()) {
			return MaterializationResult.failed(body.diagnostic().orElseThrow());
		}

		TaskResult task = resolveTask(cue, history, request);
		if (!task.successful()) {
			return MaterializationResult.failed(task.diagnostic().orElseThrow());
		}
		TickResult timestamp = resolveTimestamp(
			history.timestampSource(),
			request.timestamps()
		);
		if (!timestamp.successful()) {
			return MaterializationResult.failed(timestamp.diagnostic().orElseThrow());
		}

		Map<String, String> savedFields = new TreeMap<>();
		for (String field : history.savedFields()) {
			savedFields.put(field, decodedFields.get(field));
		}
		Diagnostic bounds = validateRecordBounds(
			title.text().orElseThrow(),
			body.text().orElseThrow(),
			savedFields
		);
		if (bounds != null) {
			return MaterializationResult.failed(bounds);
		}

		MessageHistoryRecord record = new MessageHistoryRecord(
			request.gameInstanceId(),
			request.viewerId(),
			cue.id(),
			request.messageInstanceId(),
			task.taskId().orElseThrow(),
			timestamp.ticks().orElseThrow(),
			title.text().orElseThrow(),
			body.text().orElseThrow(),
			savedFields,
			history.replayAllowed()
		);
		if (MessageHistoryRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).error().isPresent()) {
			return failure(
				DiagnosticCode.RECORD_INVALID,
				"消息回顾记录未通过持久化校验"
			);
		}
		return MaterializationResult.succeeded(record);
	}

	private static Diagnostic collectTemplateFields(
		final ComponentTemplate template,
		final MessageCueDefinition cue,
		final Set<String> requiredFields,
		final String location
	) {
		for (ComponentPart part : template.parts()) {
			if (part instanceof StaticComponentPart) {
				continue;
			}
			if (!(part instanceof FieldPart field)) {
				return diagnostic(
					DiagnosticCode.UNSUPPORTED_TEMPLATE_PART,
					"消息回顾" + location + "包含不支持的模板片段"
				);
			}
			if (field.field().scope() != FieldScope.CUE) {
				return diagnostic(
					DiagnosticCode.CONTENT_FIELD_FORBIDDEN,
					"消息回顾" + location + "只能引用 cue 级字段"
				);
			}
			String fieldId = field.field().id();
			if (!cue.fields().containsKey(fieldId)) {
				return diagnostic(
					DiagnosticCode.UNDECLARED_CUE_FIELD,
					"消息回顾" + location + "引用了未声明字段「" + fieldId + "」"
				);
			}
			requiredFields.add(fieldId);
		}
		return null;
	}

	private static RenderResult render(
		final ComponentTemplate template,
		final Map<String, String> decodedFields,
		final String location
	) {
		StringBuilder result = new StringBuilder();
		for (ComponentPart part : template.parts()) {
			if (part instanceof StaticComponentPart fixed) {
				DecodeResult decoded = decodeComponent(
					fixed.component().canonicalJson(),
					location + "静态片段"
				);
				if (!decoded.successful()) {
					return RenderResult.failed(decoded.diagnostic().orElseThrow());
				}
				result.append(decoded.text().orElseThrow());
			} else if (part instanceof FieldPart field) {
				String value = decodedFields.get(field.field().id());
				if (value == null) {
					return RenderResult.failed(
						diagnostic(
							DiagnosticCode.MISSING_FROZEN_FIELD,
							"消息回顾缺少已冻结字段「" + field.field().id() + "」"
						)
					);
				}
				result.append(value);
			}
		}
		return RenderResult.succeeded(result.toString());
	}

	private static DecodeResult decodeComponent(
		final String canonical,
		final String location
	) {
		if (canonical == null || canonical.isBlank()) {
			return DecodeResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_COMPONENT,
					"消息回顾" + location + "不是有效的原版文本组件"
				)
			);
		}
		try {
			JsonElement json = JsonParser.parseString(canonical);
			var decoded = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json);
			Optional<Component> component = decoded.result();
			if (component.isEmpty()) {
				return DecodeResult.failed(
					diagnostic(
						DiagnosticCode.INVALID_COMPONENT,
						"消息回顾" + location + "无法按原版文本组件解码"
					)
				);
			}
			return DecodeResult.succeeded(component.orElseThrow().getString());
		} catch (RuntimeException error) {
			return DecodeResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_COMPONENT,
					"消息回顾" + location + "包含损坏的组件 JSON"
				)
			);
		}
	}

	private static TaskResult resolveTask(
		final MessageCueDefinition cue,
		final HistorySpec history,
		final Request request
	) {
		HistoryTaskSource source = history.taskSource();
		if (source != HistoryTaskSource.PARAMETER && history.taskParameter().isPresent()) {
			return TaskResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_TASK_CONFIGURATION,
					"消息回顾仅在 task_source=parameter 时允许 task_parameter"
				)
			);
		}
		if (source == HistoryTaskSource.NONE) {
			return TaskResult.succeeded(Optional.empty());
		}
		if (source == HistoryTaskSource.CURRENT_TASK) {
			return TaskResult.succeeded(request.currentTask());
		}

		String parameterId = history.taskParameter().orElse(null);
		if (parameterId == null) {
			return TaskResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_TASK_CONFIGURATION,
					"消息回顾缺少任务参数声明"
				)
			);
		}
		ParameterDefinition definition = cue.parameters().get(parameterId);
		if (
			definition == null
				|| definition.type() != ParameterType.IDENTIFIER
				|| (
					definition.identifierKind() != IdentifierKind.ANY
						&& definition.identifierKind() != IdentifierKind.TASK
				)
		) {
			return TaskResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_TASK_CONFIGURATION,
					"消息回顾任务参数「" + parameterId + "」不是任务 Identifier 参数"
				)
			);
		}
		String canonical = request.canonicalParameters().get(parameterId);
		Identifier taskId = canonical == null ? null : Identifier.tryParse(canonical.strip());
		if (taskId == null) {
			return TaskResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_TASK_IDENTIFIER,
					"消息回顾任务参数「" + parameterId + "」缺失或不是有效 Identifier"
				)
			);
		}
		return TaskResult.succeeded(Optional.of(taskId));
	}

	private static TickResult resolveTimestamp(
		final HistoryTimestampSource source,
		final TimestampCandidates candidates
	) {
		TimestampValue candidate = switch (source) {
			case GAME_TIME -> candidates.gameTime();
			case PRESENTATION_TIME -> candidates.presentationTime();
			case CUE_START -> candidates.cueStart();
			case COMPLETION -> candidates.completion();
		};
		if (candidate.value() < 0L) {
			return TickResult.failed(
				diagnostic(
					DiagnosticCode.INVALID_TIMESTAMP,
					"消息回顾时间不能为负数"
				)
			);
		}
		if (candidate.unit() == TimestampUnit.GAME_TICKS) {
			if (candidate.value() > MAX_SAFE_GAME_TICK) {
				return TickResult.failed(
					diagnostic(
						DiagnosticCode.TIMESTAMP_OVERFLOW,
						"消息回顾游戏 tick 超出安全换算范围"
					)
				);
			}
			return TickResult.succeeded(candidate.value());
		}
		return TickResult.succeeded(candidate.value() / TimeSpan.NANOS_PER_TICK);
	}

	private static Diagnostic validateRecordBounds(
		final String title,
		final String body,
		final Map<String, String> savedFields
	) {
		if (title.isBlank()) {
			return diagnostic(DiagnosticCode.RECORD_INVALID, "消息回顾标题不能为空");
		}
		if (title.length() > WorldStateV4.MAX_MESSAGE_HISTORY_TITLE_LENGTH) {
			return diagnostic(DiagnosticCode.RECORD_LIMIT_EXCEEDED, "消息回顾标题超过持久化上限");
		}
		if (body.length() > WorldStateV4.MAX_MESSAGE_HISTORY_BODY_LENGTH) {
			return diagnostic(DiagnosticCode.RECORD_LIMIT_EXCEEDED, "消息回顾正文超过持久化上限");
		}
		if (savedFields.size() > WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELDS) {
			return diagnostic(DiagnosticCode.RECORD_LIMIT_EXCEEDED, "消息回顾保存字段数量超过上限");
		}
		int totalCharacters = 0;
		for (Map.Entry<String, String> entry : savedFields.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (
				!key.matches(
					"[a-z0-9_.-]{1,"
						+ WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELD_KEY_LENGTH
						+ "}"
				)
			) {
				return diagnostic(DiagnosticCode.RECORD_INVALID, "消息回顾保存字段名称无效");
			}
			if (value.length() > WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELD_VALUE_LENGTH) {
				return diagnostic(DiagnosticCode.RECORD_LIMIT_EXCEEDED, "消息回顾保存字段内容超过上限");
			}
			try {
				totalCharacters = Math.addExact(
					totalCharacters,
					Math.addExact(key.length(), value.length())
				);
			} catch (ArithmeticException overflow) {
				return diagnostic(DiagnosticCode.RECORD_LIMIT_EXCEEDED, "消息回顾保存字段长度溢出");
			}
		}
		return totalCharacters > WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELD_CHARACTERS
			? diagnostic(DiagnosticCode.RECORD_LIMIT_EXCEEDED, "消息回顾保存字段总长度超过上限")
			: null;
	}

	private static ComponentTemplate localized(
		final LocalizedTemplate template,
		final String locale
	) {
		String normalized = locale.toLowerCase(Locale.ROOT).replace('-', '_');
		return template.locales().getOrDefault(normalized, template.fallback());
	}

	private static MaterializationResult failure(
		final DiagnosticCode code,
		final String message
	) {
		return MaterializationResult.failed(diagnostic(code, message));
	}

	private static Diagnostic diagnostic(
		final DiagnosticCode code,
		final String message
	) {
		return new Diagnostic(code, message);
	}

	public enum TimestampUnit {
		GAME_TICKS,
		NANOSECONDS
	}

	public enum DiagnosticCode {
		UNSUPPORTED_TEMPLATE_PART,
		CONTENT_FIELD_FORBIDDEN,
		UNDECLARED_CUE_FIELD,
		MISSING_FROZEN_FIELD,
		INVALID_COMPONENT,
		INVALID_TASK_CONFIGURATION,
		INVALID_TASK_IDENTIFIER,
		INVALID_TIMESTAMP,
		TIMESTAMP_OVERFLOW,
		RECORD_LIMIT_EXCEEDED,
		RECORD_INVALID
	}

	public record TimestampValue(long value, TimestampUnit unit) {
		public TimestampValue {
			unit = Objects.requireNonNull(unit, "unit");
		}

		public static TimestampValue ticks(final long value) {
			return new TimestampValue(value, TimestampUnit.GAME_TICKS);
		}

		public static TimestampValue nanos(final long value) {
			return new TimestampValue(value, TimestampUnit.NANOSECONDS);
		}
	}

	public record TimestampCandidates(
		TimestampValue gameTime,
		TimestampValue presentationTime,
		TimestampValue cueStart,
		TimestampValue completion
	) {
		public TimestampCandidates {
			gameTime = Objects.requireNonNull(gameTime, "gameTime");
			presentationTime = Objects.requireNonNull(
				presentationTime,
				"presentationTime"
			);
			cueStart = Objects.requireNonNull(cueStart, "cueStart");
			completion = Objects.requireNonNull(completion, "completion");
		}
	}

	public record Request(
		MessageCueDefinition cue,
		HistorySpec history,
		UUID gameInstanceId,
		UUID messageInstanceId,
		UUID viewerId,
		String locale,
		Map<String, String> frozenCueFields,
		Map<String, String> canonicalParameters,
		Optional<Identifier> currentTask,
		TimestampCandidates timestamps
	) {
		public Request {
			cue = Objects.requireNonNull(cue, "cue");
			history = Objects.requireNonNull(history, "history");
			gameInstanceId = Objects.requireNonNull(gameInstanceId, "gameInstanceId");
			messageInstanceId = Objects.requireNonNull(
				messageInstanceId,
				"messageInstanceId"
			);
			viewerId = Objects.requireNonNull(viewerId, "viewerId");
			locale = Objects.requireNonNull(locale, "locale");
			frozenCueFields = immutableStringMap(frozenCueFields, "frozenCueFields");
			canonicalParameters = immutableStringMap(
				canonicalParameters,
				"canonicalParameters"
			);
			currentTask = Objects.requireNonNull(currentTask, "currentTask");
			timestamps = Objects.requireNonNull(timestamps, "timestamps");
		}
	}

	public record Diagnostic(DiagnosticCode code, String message) {
		public Diagnostic {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			if (message.isBlank()) {
				throw new IllegalArgumentException("diagnostic message cannot be blank");
			}
		}
	}

	public record MaterializationResult(
		Optional<MessageHistoryRecord> record,
		List<Diagnostic> diagnostics
	) {
		public MaterializationResult {
			record = Objects.requireNonNull(record, "record");
			diagnostics = List.copyOf(diagnostics);
			if (record.isPresent() == !diagnostics.isEmpty()) {
				throw new IllegalArgumentException(
					"materialization must contain either one record or diagnostics"
				);
			}
		}

		public boolean successful() {
			return this.record.isPresent();
		}

		private static MaterializationResult succeeded(
			final MessageHistoryRecord record
		) {
			return new MaterializationResult(Optional.of(record), List.of());
		}

		private static MaterializationResult failed(final Diagnostic diagnostic) {
			return new MaterializationResult(Optional.empty(), List.of(diagnostic));
		}
	}

	private record DecodeResult(
		Optional<String> text,
		Optional<Diagnostic> diagnostic
	) {
		private static DecodeResult succeeded(final String text) {
			return new DecodeResult(Optional.of(text), Optional.empty());
		}

		private static DecodeResult failed(final Diagnostic diagnostic) {
			return new DecodeResult(Optional.empty(), Optional.of(diagnostic));
		}

		private boolean successful() {
			return this.text.isPresent();
		}
	}

	private record RenderResult(
		Optional<String> text,
		Optional<Diagnostic> diagnostic
	) {
		private static RenderResult succeeded(final String text) {
			return new RenderResult(Optional.of(text), Optional.empty());
		}

		private static RenderResult failed(final Diagnostic diagnostic) {
			return new RenderResult(Optional.empty(), Optional.of(diagnostic));
		}

		private boolean successful() {
			return this.text.isPresent();
		}
	}

	private record TaskResult(
		Optional<Optional<Identifier>> taskId,
		Optional<Diagnostic> diagnostic
	) {
		private static TaskResult succeeded(final Optional<Identifier> taskId) {
			return new TaskResult(Optional.of(taskId), Optional.empty());
		}

		private static TaskResult failed(final Diagnostic diagnostic) {
			return new TaskResult(Optional.empty(), Optional.of(diagnostic));
		}

		private boolean successful() {
			return this.taskId.isPresent();
		}
	}

	private record TickResult(
		Optional<Long> ticks,
		Optional<Diagnostic> diagnostic
	) {
		private static TickResult succeeded(final long ticks) {
			return new TickResult(Optional.of(ticks), Optional.empty());
		}

		private static TickResult failed(final Diagnostic diagnostic) {
			return new TickResult(Optional.empty(), Optional.of(diagnostic));
		}

		private boolean successful() {
			return this.ticks.isPresent();
		}
	}

	private static Map<String, String> immutableStringMap(
		final Map<String, String> source,
		final String field
	) {
		Objects.requireNonNull(source, field);
		Map<String, String> copy = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : source.entrySet()) {
			copy.put(
				Objects.requireNonNull(entry.getKey(), field + " key"),
				Objects.requireNonNull(entry.getValue(), field + " value")
			);
		}
		return Collections.unmodifiableMap(copy);
	}
}
