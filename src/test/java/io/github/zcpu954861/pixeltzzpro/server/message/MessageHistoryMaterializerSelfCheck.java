package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AudienceSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.BindingSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CuePolicies;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldReference;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistorySpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTaskSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.HistoryTimestampSource;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ParameterType;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoftLimits;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.DiagnosticCode;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.MaterializationResult;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.Request;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.TimestampCandidates;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageHistoryMaterializer.TimestampValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV4;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Pure per-viewer history locale, field, task, timestamp, and persistence-bound checks. */
public final class MessageHistoryMaterializerSelfCheck {
	private static final UUID GAME_INSTANCE = UUID.fromString(
		"00000000-0000-0000-0000-000000000801"
	);
	private static final UUID MESSAGE_INSTANCE = UUID.fromString(
		"00000000-0000-0000-0000-000000000802"
	);
	private static final UUID VIEWER = UUID.fromString(
		"00000000-0000-0000-0000-000000000803"
	);
	private static final Identifier CURRENT_TASK = id("task/current");
	private static final Identifier PARAMETER_TASK = id("task/parameter");
	private static final TimestampCandidates TIMES = new TimestampCandidates(
		TimestampValue.ticks(101L),
		TimestampValue.nanos(202L * TimeSpan.NANOS_PER_TICK + 49L),
		TimestampValue.nanos(303L * TimeSpan.NANOS_PER_TICK),
		TimestampValue.nanos(404L * TimeSpan.NANOS_PER_TICK + 1L)
	);

	private MessageHistoryMaterializerSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_HISTORY_MATERIALIZER_SELF_CHECK=PASS");
	}

	public static void run() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		checkLocaleFrozenFieldsAndViewerBoundary();
		checkTaskSourcesAndIdentifiers();
		checkTimestampSourcesAndOverflow();
		checkMissingFieldsAndBadComponents();
		checkPersistenceBounds();
	}

	private static void checkLocaleFrozenFieldsAndViewerBoundary() {
		HistorySpec history = localizedHistory(
			HistoryTaskSource.PARAMETER,
			Optional.of("task_id"),
			HistoryTimestampSource.COMPLETION,
			Set.of("target_name"),
			true
		);
		MessageCueDefinition cue = cue(
			history,
			Map.of(
				"target_name",
				field("target_name"),
				"unused",
				field("unused")
			),
			Map.of(
				"task_id",
				new ParameterDefinition(
					ParameterType.IDENTIFIER,
					true,
					Optional.empty()
				)
			)
		);
		Map<String, String> mutableFields = new LinkedHashMap<>();
		mutableFields.put(
			"target_name",
			"{\"text\":\"玩家甲\",\"extra\":[{\"text\":\"!\"}]}"
		);
		mutableFields.put("unused", "{broken-unused");
		Map<String, String> mutableParameters = new LinkedHashMap<>();
		mutableParameters.put("task_id", PARAMETER_TASK.toString());
		Request request = request(
			cue,
			history,
			"zh-CN",
			mutableFields,
			mutableParameters,
			Optional.of(CURRENT_TASK),
			TIMES
		);
		mutableFields.put("target_name", "{broken-after-copy");
		mutableParameters.put("task_id", "not valid");

		MaterializationResult result = MessageHistoryMaterializer.materialize(request);
		check(result.successful(), "valid localized frozen history must materialize");
		var record = result.record().orElseThrow();
		check(
			record.gameInstanceId().equals(GAME_INSTANCE)
				&& record.messageInstanceId().equals(MESSAGE_INSTANCE)
				&& record.viewerId().equals(VIEWER)
				&& record.cueId().equals(cue.id())
				&& record.taskId().equals(Optional.of(PARAMETER_TASK))
				&& record.timestampTicks() == 404L
				&& record.title().equals("『中文回顾』")
				&& record.body().equals("目标：玩家甲!")
				&& record.savedFields().equals(Map.of("target_name", "玩家甲!"))
				&& record.replayAllowed(),
			"materialized record must contain only the frozen per-viewer static projection"
		);
		check(
			!record.title().contains("伪造")
				&& !record.body().contains("伪造")
				&& !record.savedFields().containsKey("unused"),
			"vanilla component JSON, not cached plainText or extra inputs, must be authoritative"
		);
		expectUnsupported(
			() -> record.savedFields().put("other", "value"),
			"persisted saved fields must be immutable"
		);
		expectUnsupported(
			() -> result.diagnostics().add(null),
			"materialization diagnostics must be immutable"
		);

		MaterializationResult fallback = MessageHistoryMaterializer.materialize(
			request(
				cue,
				history,
				"fr_fr",
				Map.of("target_name", "{\"text\":\"Player A\"}"),
				Map.of("task_id", PARAMETER_TASK.toString()),
				Optional.empty(),
				TIMES
			)
		);
		check(
			fallback.successful()
				&& fallback.record().orElseThrow().title().equals("Fallback Review")
				&& fallback.record().orElseThrow().body().equals("Target: Player A"),
			"unknown locales must use the declared fallback template"
		);
	}

	private static void checkTaskSourcesAndIdentifiers() {
		Map<String, DynamicFieldDefinition> fields = Map.of(
			"target_name",
			field("target_name")
		);
		Map<String, ParameterDefinition> parameters = Map.of(
			"task_id",
			new ParameterDefinition(
				ParameterType.IDENTIFIER,
				true,
				Optional.empty()
			)
		);

		for (HistoryTaskSource source : HistoryTaskSource.values()) {
			Optional<String> parameter = source == HistoryTaskSource.PARAMETER
				? Optional.of("task_id")
				: Optional.empty();
			HistorySpec history = basicHistory(
				source,
				parameter,
				HistoryTimestampSource.GAME_TIME,
				Set.of()
			);
			MessageCueDefinition cue = cue(history, fields, parameters);
			MaterializationResult result = MessageHistoryMaterializer.materialize(
				request(
					cue,
					history,
					"en_us",
					Map.of(),
					Map.of("task_id", PARAMETER_TASK.toString()),
					Optional.of(CURRENT_TASK),
					TIMES
				)
			);
			Optional<Identifier> expected = switch (source) {
				case NONE -> Optional.empty();
				case CURRENT_TASK -> Optional.of(CURRENT_TASK);
				case PARAMETER -> Optional.of(PARAMETER_TASK);
			};
			check(
				result.successful()
					&& result.record().orElseThrow().taskId().equals(expected),
				"task_source must select exactly its configured source: " + source
			);
		}

		HistorySpec currentWithoutTask = basicHistory(
			HistoryTaskSource.CURRENT_TASK,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			Set.of()
		);
		check(
			MessageHistoryMaterializer.materialize(
				request(
					cue(currentWithoutTask, fields, parameters),
					currentWithoutTask,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			).record().orElseThrow().taskId().isEmpty(),
			"current_task without an active task must remain an explicit empty association"
		);

		HistorySpec parameterHistory = basicHistory(
			HistoryTaskSource.PARAMETER,
			Optional.of("task_id"),
			HistoryTimestampSource.GAME_TIME,
			Set.of()
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(parameterHistory, fields, parameters),
					parameterHistory,
					"en_us",
					Map.of(),
					Map.of("task_id", "not an id"),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.INVALID_TASK_IDENTIFIER,
			"invalid canonical task identifiers must fail closed"
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(
						parameterHistory,
						fields,
						Map.of(
							"task_id",
							new ParameterDefinition(
								ParameterType.STRING,
								true,
								Optional.empty()
							)
						)
					),
					parameterHistory,
					"en_us",
					Map.of(),
					Map.of("task_id", PARAMETER_TASK.toString()),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.INVALID_TASK_CONFIGURATION,
			"task parameters must be declared as task-compatible identifiers"
		);

		HistorySpec contradictory = basicHistory(
			HistoryTaskSource.NONE,
			Optional.of("task_id"),
			HistoryTimestampSource.GAME_TIME,
			Set.of()
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(contradictory, fields, parameters),
					contradictory,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.INVALID_TASK_CONFIGURATION,
			"task_parameter outside parameter mode must fail closed"
		);
	}

	private static void checkTimestampSourcesAndOverflow() {
		Map<HistoryTimestampSource, Long> expected = Map.of(
			HistoryTimestampSource.GAME_TIME,
			101L,
			HistoryTimestampSource.PRESENTATION_TIME,
			202L,
			HistoryTimestampSource.CUE_START,
			303L,
			HistoryTimestampSource.COMPLETION,
			404L
		);
		for (HistoryTimestampSource source : HistoryTimestampSource.values()) {
			HistorySpec history = basicHistory(
				HistoryTaskSource.NONE,
				Optional.empty(),
				source,
				Set.of()
			);
			MaterializationResult result = MessageHistoryMaterializer.materialize(
				request(
					cue(history, Map.of(), Map.of()),
					history,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			);
			check(
				result.successful()
					&& result.record().orElseThrow().timestampTicks()
						== expected.get(source),
				"timestamp_source must select and convert exactly one candidate: " + source
			);
		}

		HistorySpec completion = basicHistory(
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.COMPLETION,
			Set.of()
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(completion, Map.of(), Map.of()),
					completion,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					new TimestampCandidates(
						TimestampValue.ticks(0L),
						TimestampValue.nanos(0L),
						TimestampValue.nanos(0L),
						TimestampValue.nanos(-1L)
					)
				)
			),
			DiagnosticCode.INVALID_TIMESTAMP,
			"negative selected timestamps must fail closed"
		);

		HistorySpec gameTime = basicHistory(
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			Set.of()
		);
		long maxSafeTick = Long.MAX_VALUE / TimeSpan.NANOS_PER_TICK;
		check(
			MessageHistoryMaterializer.materialize(
				request(
					cue(gameTime, Map.of(), Map.of()),
					gameTime,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					new TimestampCandidates(
						TimestampValue.ticks(maxSafeTick),
						TimestampValue.nanos(0L),
						TimestampValue.nanos(0L),
						TimestampValue.nanos(0L)
					)
				)
			).record().orElseThrow().timestampTicks() == maxSafeTick,
			"largest safely representable game tick must be accepted"
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(gameTime, Map.of(), Map.of()),
					gameTime,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					new TimestampCandidates(
						TimestampValue.ticks(maxSafeTick + 1L),
						TimestampValue.nanos(0L),
						TimestampValue.nanos(0L),
						TimestampValue.nanos(0L)
					)
				)
			),
			DiagnosticCode.TIMESTAMP_OVERFLOW,
			"game tick values that cannot safely convert to nanos must be rejected"
		);
	}

	private static void checkMissingFieldsAndBadComponents() {
		HistorySpec history = localizedHistory(
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			Set.of("target_name"),
			false
		);
		MessageCueDefinition cue = cue(
			history,
			Map.of("target_name", field("target_name")),
			Map.of()
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(cue, history, "en_us", Map.of(), Map.of(), Optional.empty(), TIMES)
			),
			DiagnosticCode.MISSING_FROZEN_FIELD,
			"missing frozen cue fields must fail closed"
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue,
					history,
					"en_us",
					Map.of("target_name", "{broken"),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.INVALID_COMPONENT,
			"bad frozen component JSON must fail closed"
		);

		HistorySpec badStatic = new HistorySpec(
			new LocalizedTemplate(
				template(List.of(staticPart("{broken", "伪造"))),
				Map.of()
			),
			localized("Body"),
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			AudienceSpec.allOnline(),
			Set.of(),
			false
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(badStatic, Map.of(), Map.of()),
					badStatic,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.INVALID_COMPONENT,
			"bad static component JSON must fail closed"
		);

		HistorySpec contentField = new HistorySpec(
			new LocalizedTemplate(
				template(
					List.of(
						new FieldPart(
							new FieldReference(FieldScope.CONTENT, "target_name")
						)
					)
				),
				Map.of()
			),
			localized("Body"),
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			AudienceSpec.allOnline(),
			Set.of(),
			false
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(
						contentField,
						Map.of("target_name", field("target_name")),
						Map.of()
					),
					contentField,
					"en_us",
					Map.of("target_name", "{\"text\":\"value\"}"),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.CONTENT_FIELD_FORBIDDEN,
			"history templates must reject content-scoped fields"
		);

		HistorySpec undeclared = basicHistory(
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			Set.of("missing")
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(undeclared, Map.of(), Map.of()),
					undeclared,
					"en_us",
					Map.of("missing", "{\"text\":\"value\"}"),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.UNDECLARED_CUE_FIELD,
			"saved fields not declared by the cue must fail closed"
		);
	}

	private static void checkPersistenceBounds() {
		checkTextBound(
			WorldStateV4.MAX_MESSAGE_HISTORY_TITLE_LENGTH,
			true,
			"title exact bound"
		);
		checkTextBound(
			WorldStateV4.MAX_MESSAGE_HISTORY_TITLE_LENGTH + 1,
			false,
			"title overflow"
		);

		String fieldId = "saved";
		for (int length : new int[] {
			WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELD_VALUE_LENGTH,
			WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELD_VALUE_LENGTH + 1
		}) {
			HistorySpec history = basicHistory(
				HistoryTaskSource.NONE,
				Optional.empty(),
				HistoryTimestampSource.GAME_TIME,
				Set.of(fieldId)
			);
			MessageCueDefinition cue = cue(
				history,
				Map.of(fieldId, field(fieldId)),
				Map.of()
			);
			MaterializationResult result = MessageHistoryMaterializer.materialize(
				request(
					cue,
					history,
					"en_us",
					Map.of(fieldId, componentJson("x".repeat(length))),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			);
			check(
				result.successful()
					== (length == WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELD_VALUE_LENGTH),
				"saved field persistence bound must be exact"
			);
		}

		Map<String, DynamicFieldDefinition> tooManyFields = new LinkedHashMap<>();
		Map<String, String> frozen = new LinkedHashMap<>();
		Set<String> saved = new LinkedHashSet<>();
		for (int index = 0; index <= WorldStateV4.MAX_MESSAGE_HISTORY_SAVED_FIELDS; index++) {
			String id = "field_" + index;
			tooManyFields.put(id, field(id));
			frozen.put(id, "{\"text\":\"x\"}");
			saved.add(id);
		}
		HistorySpec excessive = basicHistory(
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			saved
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(excessive, tooManyFields, Map.of()),
					excessive,
					"en_us",
					frozen,
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.RECORD_LIMIT_EXCEEDED,
			"65 saved fields must be rejected before a record is built"
		);

		HistorySpec blankTitle = new HistorySpec(
			localized(""),
			localized("Body"),
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			AudienceSpec.allOnline(),
			Set.of(),
			false
		);
		assertFailure(
			MessageHistoryMaterializer.materialize(
				request(
					cue(blankTitle, Map.of(), Map.of()),
					blankTitle,
					"en_us",
					Map.of(),
					Map.of(),
					Optional.empty(),
					TIMES
				)
			),
			DiagnosticCode.RECORD_INVALID,
			"blank resolved history title must fail closed"
		);
	}

	private static void checkTextBound(
		final int length,
		final boolean expectedSuccess,
		final String message
	) {
		HistorySpec history = new HistorySpec(
			localized("x".repeat(length)),
			localized("Body"),
			HistoryTaskSource.NONE,
			Optional.empty(),
			HistoryTimestampSource.GAME_TIME,
			AudienceSpec.allOnline(),
			Set.of(),
			false
		);
		MaterializationResult result = MessageHistoryMaterializer.materialize(
			request(
				cue(history, Map.of(), Map.of()),
				history,
				"en_us",
				Map.of(),
				Map.of(),
				Optional.empty(),
				TIMES
			)
		);
		check(result.successful() == expectedSuccess, message);
	}

	private static HistorySpec localizedHistory(
		final HistoryTaskSource taskSource,
		final Optional<String> taskParameter,
		final HistoryTimestampSource timestampSource,
		final Set<String> savedFields,
		final boolean replayAllowed
	) {
		return new HistorySpec(
			new LocalizedTemplate(
				template(List.of(staticPart(componentJson("Fallback Review"), "伪造标题"))),
				Map.of(
					"zh_cn",
					template(List.of(staticPart(componentJson("『中文回顾』"), "伪造标题")))
				)
			),
			new LocalizedTemplate(
				template(
					List.of(
						staticPart(componentJson("Target: "), "伪造正文"),
						cueField("target_name")
					)
				),
				Map.of(
					"zh_cn",
					template(
						List.of(
							staticPart(componentJson("目标："), "伪造正文"),
							cueField("target_name")
						)
					)
				)
			),
			taskSource,
			taskParameter,
			timestampSource,
			AudienceSpec.allOnline(),
			savedFields,
			replayAllowed
		);
	}

	private static HistorySpec basicHistory(
		final HistoryTaskSource taskSource,
		final Optional<String> taskParameter,
		final HistoryTimestampSource timestampSource,
		final Set<String> savedFields
	) {
		return new HistorySpec(
			localized("Review"),
			localized("Body"),
			taskSource,
			taskParameter,
			timestampSource,
			AudienceSpec.allOnline(),
			savedFields,
			false
		);
	}

	private static MessageCueDefinition cue(
		final HistorySpec history,
		final Map<String, DynamicFieldDefinition> fields,
		final Map<String, ParameterDefinition> parameters
	) {
		return new MessageCueDefinition(
			id("history/materialize"),
			Optional.of(id("main")),
			false,
			CuePolicies.standard(),
			parameters,
			fields,
			List.of(),
			Optional.of(history),
			Optional.empty(),
			List.of(),
			SoftLimits.empty(),
			"{}"
		);
	}

	private static DynamicFieldDefinition field(final String id) {
		return new DynamicFieldDefinition(
			CaptureMode.ON_DISPLAY,
			new BindingSource("history." + id),
			Optional.empty()
		);
	}

	private static Request request(
		final MessageCueDefinition cue,
		final HistorySpec history,
		final String locale,
		final Map<String, String> frozenFields,
		final Map<String, String> parameters,
		final Optional<Identifier> currentTask,
		final TimestampCandidates timestamps
	) {
		return new Request(
			cue,
			history,
			GAME_INSTANCE,
			MESSAGE_INSTANCE,
			VIEWER,
			locale,
			frozenFields,
			parameters,
			currentTask,
			timestamps
		);
	}

	private static LocalizedTemplate localized(final String text) {
		return new LocalizedTemplate(
			template(List.of(staticPart(componentJson(text), "伪造 plainText"))),
			Map.of()
		);
	}

	private static ComponentTemplate template(final List<ComponentPart> parts) {
		return new ComponentTemplate(parts);
	}

	private static StaticComponentPart staticPart(
		final String canonical,
		final String fakePlainText
	) {
		return new StaticComponentPart(new RichComponent(canonical, fakePlainText));
	}

	private static FieldPart cueField(final String id) {
		return new FieldPart(new FieldReference(FieldScope.CUE, id));
	}

	private static String componentJson(final String text) {
		return "{\"text\":\"" + text + "\"}";
	}

	private static Identifier id(final String path) {
		return Identifier.parse("pixel_tzz:" + path);
	}

	private static void assertFailure(
		final MaterializationResult result,
		final DiagnosticCode expected,
		final String message
	) {
		check(
			!result.successful()
				&& result.record().isEmpty()
				&& result.diagnostics().size() == 1
				&& result.diagnostics().getFirst().code() == expected
				&& result.diagnostics().getFirst().message().codePoints().anyMatch(
					value -> value > 127
				),
			message
		);
	}

	private static void expectUnsupported(
		final Runnable operation,
		final String message
	) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (UnsupportedOperationException expected) {
			// Expected immutable result.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
