package io.github.zcpu954861.pixeltzzpro.network.payload;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownCheckpoint;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.CountdownPresentation;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One bounded JSON construction path shared by countdown definition compilation and live delivery.
 *
 * <p>The catalog compiler audits the exact Replace and Patch documents produced here. The live
 * projector uses the same builders through a non-throwing result, so every retained definition is
 * encodable and an unexpected runtime projection fault remains fail-closed.</p>
 */
public final class CountdownProjectionDocumentCodec {
	private static final int FORMAT_VERSION = 1;
	private static final UUID BUDGET_INSTANCE_ID = UUID.fromString(
		"ffffffff-ffff-ffff-ffff-ffffffffffff"
	);
	private static final String HANDOFF_FAILURE_ID = "purpose_handoff_failed";
	private static final int HANDOFF_FAILURE_COLOR = 0xFFFF6B6B;
	private static final String BUDGET_HANDOFF_DIAGNOSTIC = "😀".repeat(160);

	private CountdownProjectionDocumentCodec() {
	}

	/** Encodes one complete atomic Replace without allowing a size/build failure to escape. */
	public static EncodingResult encodeReplace(
		final ProjectionFrame frame,
		final Optional<CountdownDefinition> definition
	) {
		Objects.requireNonNull(frame, "frame");
		Objects.requireNonNull(definition, "definition");
		return encode(
			() -> replaceDocument(frame, definition),
			CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES,
			"countdown Replace"
		);
	}

	/** Encodes one narrow Patch without allowing a size/build failure to escape. */
	public static EncodingResult encodePatch(final ProjectionFrame frame) {
		Objects.requireNonNull(frame, "frame");
		return encode(
			() -> patchDocument(frame),
			CountdownPatchS2CPayload.MAX_PATCH_BYTES,
			"countdown Patch"
		);
	}

	/**
	 * Measures every live document shape reachable from one definition.
	 *
	 * <p>This includes the normal Replace/Patch, every visual checkpoint, and the bounded completion
	 * handoff diagnostic. A definition is retainable only when all variants fit the wire limits.</p>
	 */
	public static BudgetReport auditDefinition(final CountdownDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		List<BudgetViolation> violations = new ArrayList<>();
		int maximumReplaceBytes = 0;
		int maximumPatchBytes = 0;

		ProjectionFrame base = budgetFrame(Optional.empty());
		EncodingResult baseReplace = encodeReplace(base, Optional.of(definition));
		maximumReplaceBytes = Math.max(maximumReplaceBytes, baseReplace.utf8Bytes());
		baseReplace.diagnostic().ifPresent(diagnostic -> violations.add(new BudgetViolation(
			"/presentation",
			"RESOURCE_LIMIT",
			diagnostic,
			baseReplace.utf8Bytes(),
			CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES
		)));

		EncodingResult basePatch = encodePatch(base);
		maximumPatchBytes = Math.max(maximumPatchBytes, basePatch.utf8Bytes());
		basePatch.diagnostic().ifPresent(diagnostic -> violations.add(new BudgetViolation(
			"/presentation",
			"RESOURCE_LIMIT",
			diagnostic,
			basePatch.utf8Bytes(),
			CountdownPatchS2CPayload.MAX_PATCH_BYTES
		)));

		for (int index = 0; index < definition.checkpoints().size(); index++) {
			CountdownCheckpoint checkpoint = definition.checkpoints().get(index);
			String pointer = "/checkpoints";
			Optional<JsonObject> projection = checkpointProjection(definition, checkpoint);
			if (projection.isEmpty()) {
				continue;
			}
			ProjectionFrame frame = budgetFrame(projection);
			EncodingResult patch = encodePatch(frame);
			maximumPatchBytes = Math.max(maximumPatchBytes, patch.utf8Bytes());
			patch.diagnostic().ifPresent(diagnostic -> violations.add(new BudgetViolation(
				pointer,
				"RESOURCE_LIMIT",
				"checkpoint '" + checkpoint.id() + "' is not encodable: " + diagnostic,
				patch.utf8Bytes(),
				CountdownPatchS2CPayload.MAX_PATCH_BYTES
			)));

			/* A resync or authority refresh may send a full Replace while a checkpoint is visible. */
			EncodingResult replace = encodeReplace(frame, Optional.of(definition));
			maximumReplaceBytes = Math.max(maximumReplaceBytes, replace.utf8Bytes());
			if (baseReplace.success()) {
				replace.diagnostic().ifPresent(diagnostic -> violations.add(new BudgetViolation(
					pointer,
					"RESOURCE_LIMIT",
					"checkpoint '" + checkpoint.id() + "' is not encodable: " + diagnostic,
					replace.utf8Bytes(),
					CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES
				)));
			}
		}

		ProjectionFrame handoff = budgetFrame(Optional.of(handoffFailureProjection(
			BUDGET_HANDOFF_DIAGNOSTIC
		)));
		EncodingResult handoffPatch = encodePatch(handoff);
		maximumPatchBytes = Math.max(maximumPatchBytes, handoffPatch.utf8Bytes());
		handoffPatch.diagnostic().ifPresent(diagnostic -> violations.add(new BudgetViolation(
			"/presentation",
			"RESOURCE_LIMIT",
			"bounded handoff projection is not encodable: " + diagnostic,
			handoffPatch.utf8Bytes(),
			CountdownPatchS2CPayload.MAX_PATCH_BYTES
		)));
		EncodingResult handoffReplace = encodeReplace(handoff, Optional.of(definition));
		maximumReplaceBytes = Math.max(maximumReplaceBytes, handoffReplace.utf8Bytes());
		if (baseReplace.success()) {
			handoffReplace.diagnostic().ifPresent(diagnostic -> violations.add(new BudgetViolation(
				"/presentation",
				"RESOURCE_LIMIT",
				"bounded handoff projection is not encodable: " + diagnostic,
				handoffReplace.utf8Bytes(),
				CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES
			)));
		}

		return new BudgetReport(maximumReplaceBytes, maximumPatchBytes, violations);
	}

	/** Builds the visual checkpoint object used by both the audit and live runtime. */
	public static Optional<JsonObject> checkpointProjection(
		final CountdownDefinition countdown,
		final CountdownCheckpoint checkpoint
	) {
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(checkpoint, "checkpoint");
		if (
			checkpoint.text().isEmpty()
				&& checkpoint.color().isEmpty()
				&& checkpoint.scale().isEmpty()
		) {
			return Optional.empty();
		}
		JsonObject result = new JsonObject();
		result.addProperty("id", checkpoint.id());
		result.add(
			"text",
			checkpoint.text().map(CountdownProjectionDocumentCodec::component)
				.orElseGet(() -> component(countdown.presentation().title()))
		);
		result.addProperty(
			"color",
			checkpoint.color().orElse(countdown.presentation().panel().accentColor())
		);
		result.addProperty(
			"scale",
			checkpoint.scale().orElse(countdown.presentation().time().scale())
		);
		return Optional.of(result);
	}

	/** Builds the bounded runtime-only purpose handoff diagnostic checkpoint. */
	public static JsonObject handoffFailureProjection(final String diagnostic) {
		JsonObject checkpoint = new JsonObject();
		checkpoint.addProperty("id", HANDOFF_FAILURE_ID);
		checkpoint.add("text", literal("启动任务失败，请通知主持人：" + Objects.requireNonNull(diagnostic, "diagnostic")));
		checkpoint.addProperty("color", HANDOFF_FAILURE_COLOR);
		checkpoint.addProperty("scale", 1.0D);
		return checkpoint;
	}

	private static JsonObject replaceDocument(
		final ProjectionFrame frame,
		final Optional<CountdownDefinition> definition
	) {
		JsonObject root = envelope();
		root.addProperty("countdown_instance_id", frame.countdownInstanceId().toString());
		root.addProperty("purpose", frame.purpose());
		root.addProperty("total_ticks", frame.totalTicks());
		root.addProperty("state", frame.state());
		root.addProperty("inventory_blocked", frame.inventoryBlocked());
		root.add("timing", timing(frame));
		root.add("text", text(definition));
		root.add("presentation", presentation(definition));
		root.add(
			"checkpoint",
			frame.checkpoint().<JsonElement>map(JsonObject::deepCopy).orElse(JsonNull.INSTANCE)
		);
		return root;
	}

	private static JsonObject patchDocument(final ProjectionFrame frame) {
		JsonObject root = envelope();
		root.addProperty("state", frame.state());
		root.addProperty("inventory_blocked", frame.inventoryBlocked());
		root.add("timing", timing(frame));
		root.add(
			"checkpoint",
			frame.checkpoint().<JsonElement>map(JsonObject::deepCopy).orElse(JsonNull.INSTANCE)
		);
		return root;
	}

	private static JsonObject envelope() {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", FORMAT_VERSION);
		root.addProperty("surface", "countdown");
		return root;
	}

	private static JsonObject timing(final ProjectionFrame frame) {
		JsonObject result = new JsonObject();
		result.addProperty("server_tick", frame.serverTick());
		result.addProperty("base_remaining_ticks", frame.baseRemainingTicks());
		result.addProperty("rate", frame.rate());
		result.addProperty("paused", frame.paused());
		return result;
	}

	private static JsonObject text(final Optional<CountdownDefinition> definition) {
		JsonObject result = new JsonObject();
		if (definition.isEmpty()) {
			result.add("title", literal("开局倒计时"));
			result.add("waiting", literal("等待所需玩家返回"));
			result.add("paused", literal("倒计时已暂停"));
			result.add("complete", literal("开始"));
			return result;
		}
		CountdownPresentation value = definition.orElseThrow().presentation();
		result.add("title", component(value.title()));
		value.prefix().ifPresent(prefix -> result.add("prefix", component(prefix)));
		value.suffix().ifPresent(suffix -> result.add("suffix", component(suffix)));
		result.add("waiting", component(value.waitingText()));
		result.add("paused", component(value.pausedText()));
		result.add("complete", component(value.completeText()));
		return result;
	}

	private static JsonObject presentation(final Optional<CountdownDefinition> definition) {
		if (definition.isEmpty()) {
			return fallbackPresentation();
		}
		CountdownPresentation value = definition.orElseThrow().presentation();
		JsonObject result = new JsonObject();
		var time = value.time();
		JsonObject timeJson = new JsonObject();
		timeJson.addProperty("format", enumName(time.format()));
		timeJson.addProperty("precision", enumName(time.precision()));
		timeJson.addProperty("leading_zero", time.leadingZero());
		timeJson.addProperty("separator", time.separator());
		timeJson.addProperty("color", time.color());
		timeJson.addProperty("scale", time.scale());
		timeJson.addProperty("shadow", time.shadow());
		result.add("time", timeJson);

		var panel = value.panel();
		JsonObject panelJson = new JsonObject();
		panelJson.addProperty("background_color", panel.backgroundColor());
		panelJson.addProperty("border_color", panel.borderColor());
		panelJson.addProperty("accent_color", panel.accentColor());
		panelJson.addProperty("opacity", panel.opacity());
		panelJson.addProperty("padding_x", panel.paddingX());
		panelJson.addProperty("padding_y", panel.paddingY());
		panelJson.addProperty("gap_above_action_bar", panel.gapAboveActionBar());
		panelJson.addProperty("max_width", panel.maxWidth());
		result.add("panel", panelJson);

		var progress = value.progress();
		JsonObject progressJson = new JsonObject();
		progressJson.addProperty("mode", enumName(progress.mode()));
		progressJson.addProperty("color", progress.color());
		progressJson.addProperty("track_color", progress.trackColor());
		progressJson.addProperty("thickness", progress.thickness());
		progressJson.addProperty("smooth", progress.smooth());
		result.add("progress", progressJson);

		var digits = value.digits();
		JsonObject digitsJson = new JsonObject();
		digitsJson.addProperty("transition", enumName(digits.transition()));
		digitsJson.addProperty("direction", enumName(digits.direction()));
		digitsJson.addProperty("duration_ms", digits.durationMs());
		digitsJson.addProperty("distance", digits.distance());
		digitsJson.addProperty("easing", enumName(digits.easing()));
		digitsJson.addProperty("stagger_ms", digits.staggerMs());
		digitsJson.addProperty("animate_fractional", digits.animateFractional());
		digitsJson.addProperty("incoming_color", digits.incomingColor());
		digitsJson.addProperty("outgoing_color", digits.outgoingColor());
		digitsJson.addProperty("incoming_alpha", digits.incomingAlpha());
		digitsJson.addProperty("outgoing_alpha", digits.outgoingAlpha());
		result.add("digits", digitsJson);

		result.add("enter", motion(value.enter()));
		result.add("exit", motion(value.exit()));
		JsonObject completion = new JsonObject();
		completion.addProperty("hold_ms", value.completion().holdMs());
		completion.addProperty("color", value.completion().color());
		result.add("completion", completion);
		return result;
	}

	private static JsonObject fallbackPresentation() {
		JsonObject result = new JsonObject();
		JsonObject time = new JsonObject();
		time.addProperty("format", "mm_ss");
		time.addProperty("precision", "seconds");
		time.addProperty("leading_zero", true);
		time.addProperty("separator", ":");
		time.addProperty("color", 0xFFF4F7FA);
		time.addProperty("scale", 1.0D);
		time.addProperty("shadow", true);
		result.add("time", time);
		JsonObject panel = new JsonObject();
		panel.addProperty("background_color", 0xD0081018);
		panel.addProperty("border_color", 0xCC33424E);
		panel.addProperty("accent_color", 0xFFF2C14E);
		panel.addProperty("opacity", 0.88D);
		panel.addProperty("padding_x", 6);
		panel.addProperty("padding_y", 3);
		panel.addProperty("gap_above_action_bar", 6);
		panel.addProperty("max_width", 320);
		result.add("panel", panel);
		JsonObject progress = new JsonObject();
		progress.addProperty("mode", "line");
		progress.addProperty("color", 0xFFF2C14E);
		progress.addProperty("track_color", 0x6033424E);
		progress.addProperty("thickness", 1);
		progress.addProperty("smooth", true);
		result.add("progress", progress);
		JsonObject digits = new JsonObject();
		digits.addProperty("transition", "roll");
		digits.addProperty("direction", "down");
		digits.addProperty("duration_ms", 240);
		digits.addProperty("distance", 10);
		digits.addProperty("easing", "ease_out_cubic");
		digits.addProperty("stagger_ms", 0);
		digits.addProperty("animate_fractional", false);
		digits.addProperty("incoming_color", 0xFFFFFFFF);
		digits.addProperty("outgoing_color", 0xFF8A9AA8);
		digits.addProperty("incoming_alpha", 1.0D);
		digits.addProperty("outgoing_alpha", 0.55D);
		result.add("digits", digits);
		result.add("enter", fallbackMotion("fade", 180));
		result.add("exit", fallbackMotion("fade", 220));
		JsonObject completion = new JsonObject();
		completion.addProperty("hold_ms", 600);
		completion.addProperty("color", 0xFFF2C14E);
		result.add("completion", completion);
		return result;
	}

	private static JsonObject motion(
		final io.github.zcpu954861.pixeltzzpro.content.CountdownDefinitions.SurfaceMotion value
	) {
		JsonObject result = new JsonObject();
		result.addProperty("mode", enumName(value.mode()));
		result.addProperty("duration_ms", value.durationMs());
		result.addProperty("easing", enumName(value.easing()));
		return result;
	}

	private static JsonObject fallbackMotion(final String mode, final int duration) {
		JsonObject result = new JsonObject();
		result.addProperty("mode", mode);
		result.addProperty("duration_ms", duration);
		result.addProperty("easing", "ease_out_cubic");
		return result;
	}

	private static JsonElement component(final RichText value) {
		try {
			return JsonParser.parseString(value.json());
		} catch (RuntimeException error) {
			return literal(value.plainText());
		}
	}

	private static JsonObject literal(final String value) {
		JsonObject result = new JsonObject();
		result.addProperty("text", value);
		return result;
	}

	private static String enumName(final Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static EncodingResult encode(
		final Supplier<JsonObject> document,
		final int maximum,
		final String label
	) {
		try {
			byte[] bytes = document.get().toString().getBytes(StandardCharsets.UTF_8);
			if (bytes.length == 0 || bytes.length > maximum) {
				return EncodingResult.failure(
					bytes.length,
					label + " uses " + bytes.length + " UTF-8 bytes; limit is " + maximum
				);
			}
			return EncodingResult.success(bytes);
		} catch (RuntimeException error) {
			return EncodingResult.failure(
				0,
				label + " could not be encoded: " + safeMessage(error)
			);
		}
	}

	private static ProjectionFrame budgetFrame(final Optional<JsonObject> checkpoint) {
		return new ProjectionFrame(
			BUDGET_INSTANCE_ID,
			"opening",
			72_000L,
			"completing",
			Integer.MIN_VALUE,
			72_000.0D,
			-1.0D,
			false,
			false,
			checkpoint
		);
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	public record ProjectionFrame(
		UUID countdownInstanceId,
		String purpose,
		long totalTicks,
		String state,
		long serverTick,
		double baseRemainingTicks,
		double rate,
		boolean paused,
		boolean inventoryBlocked,
		Optional<JsonObject> checkpoint
	) {
		public ProjectionFrame {
			countdownInstanceId = Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
			purpose = Objects.requireNonNull(purpose, "purpose");
			state = Objects.requireNonNull(state, "state");
			checkpoint = Objects.requireNonNull(checkpoint, "checkpoint").map(JsonObject::deepCopy);
		}

		@Override
		public Optional<JsonObject> checkpoint() {
			return this.checkpoint.map(JsonObject::deepCopy);
		}
	}

	public record EncodingResult(
		Optional<byte[]> document,
		int utf8Bytes,
		Optional<String> diagnostic
	) {
		public EncodingResult {
			document = Objects.requireNonNull(document, "document").map(byte[]::clone);
			diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
			if (utf8Bytes < 0 || document.isPresent() == diagnostic.isPresent()) {
				throw new IllegalArgumentException("countdown encoding result is inconsistent");
			}
		}

		private static EncodingResult success(final byte[] document) {
			return new EncodingResult(Optional.of(document), document.length, Optional.empty());
		}

		private static EncodingResult failure(final int utf8Bytes, final String diagnostic) {
			return new EncodingResult(Optional.empty(), utf8Bytes, Optional.of(diagnostic));
		}

		public boolean success() {
			return this.document.isPresent();
		}

		@Override
		public Optional<byte[]> document() {
			return this.document.map(byte[]::clone);
		}
	}

	public record BudgetViolation(
		String pointer,
		String code,
		String message,
		int actualBytes,
		int maximumBytes
	) {
		public BudgetViolation {
			pointer = Objects.requireNonNull(pointer, "pointer");
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			if (actualBytes < 0 || maximumBytes < 1) {
				throw new IllegalArgumentException("countdown projection budget is invalid");
			}
		}
	}

	public record BudgetReport(
		int maximumReplaceBytes,
		int maximumPatchBytes,
		List<BudgetViolation> violations
	) {
		public BudgetReport {
			violations = List.copyOf(violations);
			if (maximumReplaceBytes < 0 || maximumPatchBytes < 0) {
				throw new IllegalArgumentException("countdown projection measurements must be non-negative");
			}
		}

		public boolean encodable() {
			return this.violations.isEmpty();
		}
	}
}
