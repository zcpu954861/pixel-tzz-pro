package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BindingValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.BoundText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ComparisonCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConcatenatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionExpression;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ConditionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ExistsCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.JunctionOperator;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.LiteralValue;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.NotCondition;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.StaticText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TextTemplate;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.TranslatedText;
import io.github.zcpu954861.pixeltzzpro.content.UiDefinitions.ValueExpression;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Client-neutral, fail-closed evaluator for compiled page bindings.
 *
 * <p>Every public evaluation catches malformed runtime values and reports bounded diagnostics
 * instead of allowing an exception to escape into a render loop. Dependency sets are derived only
 * from the immutable expression tree, so they do not change when a condition result or fallback
 * branch changes.
 */
public final class BindingRuntime {
	public static final int MAX_DIAGNOSTICS = 50;
	private static final int MAX_DIAGNOSTIC_SOURCE_LENGTH = 256;
	private static final int MAX_DIAGNOSTIC_MESSAGE_LENGTH = 512;

	private final TranslationResolver translations;

	public BindingRuntime() {
		this((key, arguments) -> Optional.empty());
	}

	public BindingRuntime(final TranslationResolver translations) {
		this.translations = Objects.requireNonNull(translations, "translations");
	}

	public Evaluation<JsonElement> evaluateValue(
		final ValueExpression expression,
		final BindingContext context
	) {
		Set<String> dependencies = dependencies(expression);
		DiagnosticCollector diagnostics = new DiagnosticCollector();
		try {
			JsonElement value = value(expression, context, diagnostics, true);
			return result(value == null ? Optional.empty() : Optional.of(value.deepCopy()), dependencies, diagnostics);
		} catch (RuntimeException error) {
			diagnostics.add("EVALUATION_FAILED", "", safeMessage(error));
			return result(Optional.empty(), dependencies, diagnostics);
		}
	}

	public Evaluation<Boolean> evaluateCondition(
		final ConditionExpression expression,
		final BindingContext context
	) {
		Set<String> dependencies = dependencies(expression);
		DiagnosticCollector diagnostics = new DiagnosticCollector();
		try {
			ConditionResult evaluated = condition(expression, context, diagnostics);
			return result(Optional.of(evaluated.valid() && evaluated.value()), dependencies, diagnostics);
		} catch (RuntimeException error) {
			diagnostics.add("EVALUATION_FAILED", "", safeMessage(error));
			return result(Optional.of(false), dependencies, diagnostics);
		}
	}

	public Evaluation<String> evaluateText(
		final TextTemplate template,
		final BindingContext context
	) {
		Set<String> dependencies = dependencies(template);
		DiagnosticCollector diagnostics = new DiagnosticCollector();
		try {
			String value = text(template, context, diagnostics);
			return result(Optional.ofNullable(value), dependencies, diagnostics);
		} catch (RuntimeException error) {
			diagnostics.add("EVALUATION_FAILED", "", safeMessage(error));
			return result(Optional.empty(), dependencies, diagnostics);
		}
	}

	public Set<String> dependencies(final ValueExpression expression) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		collectDependencies(expression, result);
		return Set.copyOf(result);
	}

	public Set<String> dependencies(final ConditionExpression expression) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		collectDependencies(expression, result);
		return Set.copyOf(result);
	}

	public Set<String> dependencies(final TextTemplate template) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		collectDependencies(template, result);
		return Set.copyOf(result);
	}

	private JsonElement value(
		final ValueExpression expression,
		final BindingContext context,
		final DiagnosticCollector diagnostics,
		final boolean reportMissing
	) {
		if (expression instanceof BindingValue binding) {
			Optional<JsonElement> resolved = context.resolve(binding.path());
			if (resolved.isEmpty() && reportMissing) {
				diagnostics.add("MISSING_BINDING", binding.path(), "binding value is not available");
			}
			return resolved.orElse(null);
		}
		if (expression instanceof LiteralValue literal) {
			try {
				return JsonParser.parseString(literal.canonicalJson());
			} catch (JsonParseException | IllegalStateException error) {
				diagnostics.add("INVALID_LITERAL", "", safeMessage(error));
				return null;
			}
		}
		diagnostics.add("UNKNOWN_EXPRESSION", "", "unsupported value expression");
		return null;
	}

	private ConditionResult condition(
		final ConditionExpression expression,
		final BindingContext context,
		final DiagnosticCollector diagnostics
	) {
		if (expression instanceof ComparisonCondition comparison) {
			JsonElement left = value(comparison.left(), context, diagnostics, true);
			JsonElement right = value(comparison.right(), context, diagnostics, true);
			if (left == null || right == null) {
				return ConditionResult.invalid();
			}
			return compare(comparison.operator(), left, right, diagnostics);
		}
		if (expression instanceof JunctionCondition junction) {
			boolean result = junction.operator() == JunctionOperator.ALL;
			boolean valid = true;
			for (ConditionExpression child : junction.conditions()) {
				ConditionResult evaluated = condition(child, context, diagnostics);
				valid &= evaluated.valid();
				result = junction.operator() == JunctionOperator.ALL
					? result && evaluated.value()
					: result || evaluated.value();
			}
			return valid ? new ConditionResult(result, true) : ConditionResult.invalid();
		}
		if (expression instanceof NotCondition not) {
			ConditionResult evaluated = condition(not.condition(), context, diagnostics);
			return evaluated.valid()
				? new ConditionResult(!evaluated.value(), true)
				: ConditionResult.invalid();
		}
		if (expression instanceof ExistsCondition exists) {
			Optional<JsonElement> resolved = context.resolve(exists.binding().path());
			return new ConditionResult(resolved.isPresent() && !resolved.orElseThrow().isJsonNull(), true);
		}
		diagnostics.add("UNKNOWN_EXPRESSION", "", "unsupported condition expression");
		return ConditionResult.invalid();
	}

	private static ConditionResult compare(
		final ConditionOperator operator,
		final JsonElement left,
		final JsonElement right,
		final DiagnosticCollector diagnostics
	) {
		if (operator == ConditionOperator.EQ || operator == ConditionOperator.NOT_EQ) {
			ValueKind leftKind = kind(left);
			ValueKind rightKind = kind(right);
			if (!comparable(leftKind, rightKind)) {
				diagnostics.add(
					"TYPE_MISMATCH",
					"",
					"equality operands have incompatible runtime types"
				);
				return ConditionResult.invalid();
			}
			boolean equal = left.equals(right);
			return new ConditionResult(operator == ConditionOperator.EQ ? equal : !equal, true);
		}

		Long leftNumber = integer(left);
		Long rightNumber = integer(right);
		if (leftNumber == null || rightNumber == null) {
			diagnostics.add("TYPE_MISMATCH", "", "numeric comparison requires 64-bit integer operands");
			return ConditionResult.invalid();
		}
		int comparison = Long.compare(leftNumber, rightNumber);
		boolean value = switch (operator) {
			case GREATER -> comparison > 0;
			case GREATER_OR_EQUAL -> comparison >= 0;
			case LESS -> comparison < 0;
			case LESS_OR_EQUAL -> comparison <= 0;
			case EQ, NOT_EQ -> throw new IllegalStateException("equality was handled above");
		};
		return new ConditionResult(value, true);
	}

	private String text(
		final TextTemplate template,
		final BindingContext context,
		final DiagnosticCollector diagnostics
	) {
		if (template instanceof StaticText staticText) {
			return staticText.text().plainText();
		}
		if (template instanceof BoundText bound) {
			JsonElement resolved = value(bound.binding(), context, diagnostics, false);
			if (resolved == null || resolved.isJsonNull()) {
				diagnostics.add("MISSING_BINDING", bound.binding().path(), "text binding value is not available");
				return fallback(bound.fallback(), context, diagnostics);
			}
			String converted = primitiveText(resolved);
			if (converted == null) {
				diagnostics.add("TYPE_MISMATCH", bound.binding().path(), "text binding must be a primitive value");
				return fallback(bound.fallback(), context, diagnostics);
			}
			return converted;
		}
		if (template instanceof ConcatenatedText concatenated) {
			StringBuilder result = new StringBuilder();
			for (TextTemplate part : concatenated.parts()) {
				String resolved = text(part, context, diagnostics);
				if (resolved == null) {
					return null;
				}
				result.append(resolved);
			}
			return result.toString();
		}
		if (template instanceof TranslatedText translated) {
			List<JsonElement> arguments = new ArrayList<>();
			boolean argumentsValid = true;
			for (ValueExpression argument : translated.arguments()) {
				JsonElement resolved = value(argument, context, diagnostics, true);
				if (resolved == null || primitiveText(resolved) == null) {
					if (resolved != null) {
						diagnostics.add(
							"TYPE_MISMATCH",
							"",
							"translation arguments must be primitive values"
						);
					}
					argumentsValid = false;
				} else {
					arguments.add(resolved.deepCopy());
				}
			}
			if (argumentsValid) {
				try {
					Optional<String> translatedValue = this.translations.resolve(
						translated.key(),
						copyElements(arguments)
					);
					if (translatedValue != null && translatedValue.isPresent()) {
						return translatedValue.orElseThrow();
					}
					diagnostics.add(
						"MISSING_TRANSLATION",
						translated.key(),
						"translation is not available"
					);
				} catch (RuntimeException error) {
					diagnostics.add("TRANSLATION_FAILED", translated.key(), safeMessage(error));
				}
			}
			return fallback(translated.fallback(), context, diagnostics);
		}
		diagnostics.add("UNKNOWN_TEMPLATE", "", "unsupported text template");
		return null;
	}

	private String fallback(
		final Optional<TextTemplate> fallback,
		final BindingContext context,
		final DiagnosticCollector diagnostics
	) {
		return fallback.isEmpty() ? null : text(fallback.orElseThrow(), context, diagnostics);
	}

	private static String primitiveText(final JsonElement value) {
		if (!value.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isString() || primitive.isBoolean()) {
			return primitive.getAsString();
		}
		Long number = integer(value);
		return number == null ? null : Long.toString(number);
	}

	private static ValueKind kind(final JsonElement value) {
		if (value == null || value instanceof JsonNull) {
			return ValueKind.NULL;
		}
		if (value instanceof JsonArray) {
			return ValueKind.COLLECTION;
		}
		if (!value.isJsonPrimitive()) {
			return ValueKind.INVALID;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return ValueKind.BOOLEAN;
		}
		if (primitive.isString()) {
			return ValueKind.STRING;
		}
		return integer(value) == null ? ValueKind.INVALID : ValueKind.INTEGER;
	}

	private static boolean comparable(final ValueKind left, final ValueKind right) {
		if (left == ValueKind.INVALID || right == ValueKind.INVALID) {
			return false;
		}
		return left == ValueKind.NULL || right == ValueKind.NULL || left == right;
	}

	private static Long integer(final JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			return new BigDecimal(value.getAsJsonPrimitive().getAsString()).longValueExact();
		} catch (ArithmeticException | NumberFormatException error) {
			return null;
		}
	}

	private static void collectDependencies(
		final ValueExpression expression,
		final Set<String> dependencies
	) {
		if (expression instanceof BindingValue binding && binding.path() != null) {
			dependencies.add(binding.path());
		}
	}

	private static void collectDependencies(
		final ConditionExpression expression,
		final Set<String> dependencies
	) {
		if (expression instanceof ComparisonCondition comparison) {
			collectDependencies(comparison.left(), dependencies);
			collectDependencies(comparison.right(), dependencies);
		} else if (expression instanceof JunctionCondition junction) {
			junction.conditions().forEach(child -> collectDependencies(child, dependencies));
		} else if (expression instanceof NotCondition not) {
			collectDependencies(not.condition(), dependencies);
		} else if (expression instanceof ExistsCondition exists) {
			collectDependencies(exists.binding(), dependencies);
		}
	}

	private static void collectDependencies(
		final TextTemplate template,
		final Set<String> dependencies
	) {
		if (template instanceof BoundText bound) {
			collectDependencies(bound.binding(), dependencies);
			bound.fallback().ifPresent(value -> collectDependencies(value, dependencies));
		} else if (template instanceof TranslatedText translated) {
			translated.arguments().forEach(value -> collectDependencies(value, dependencies));
			translated.fallback().ifPresent(value -> collectDependencies(value, dependencies));
		} else if (template instanceof ConcatenatedText concatenated) {
			concatenated.parts().forEach(value -> collectDependencies(value, dependencies));
		}
	}

	private static List<JsonElement> copyElements(final List<JsonElement> values) {
		return values.stream().map(JsonElement::deepCopy).toList();
	}

	private static <T> Evaluation<T> result(
		final Optional<T> value,
		final Set<String> dependencies,
		final DiagnosticCollector diagnostics
	) {
		return new Evaluation<>(
			value,
			dependencies,
			diagnostics.retained(),
			diagnostics.totalCount()
		);
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	@FunctionalInterface
	public interface TranslationResolver {
		Optional<String> resolve(String key, List<JsonElement> arguments);
	}

	public record Diagnostic(String code, String source, String message) {
	}

	public record Evaluation<T>(
		Optional<T> value,
		Set<String> dependencies,
		List<Diagnostic> diagnostics,
		int totalDiagnosticCount
	) {
		public Evaluation {
			value = value == null ? Optional.empty() : value;
			dependencies = Set.copyOf(dependencies);
			diagnostics = List.copyOf(diagnostics);
			if (totalDiagnosticCount < diagnostics.size()) {
				throw new IllegalArgumentException("total diagnostic count cannot be smaller than retained details");
			}
		}

		public boolean successful() {
			return this.value.isPresent() && this.totalDiagnosticCount == 0;
		}

		public boolean diagnosticsTruncated() {
			return this.totalDiagnosticCount > this.diagnostics.size();
		}
	}

	private record ConditionResult(boolean value, boolean valid) {
		private static ConditionResult invalid() {
			return new ConditionResult(false, false);
		}
	}

	private enum ValueKind {
		NULL,
		BOOLEAN,
		INTEGER,
		STRING,
		COLLECTION,
		INVALID
	}

	private static final class DiagnosticCollector {
		private final List<Diagnostic> retained = new ArrayList<>();
		private int totalCount;

		private void add(final String code, final String source, final String message) {
			this.totalCount++;
			if (this.retained.size() < MAX_DIAGNOSTICS) {
				this.retained.add(
					new Diagnostic(
						limit(code, 64),
						limit(source, MAX_DIAGNOSTIC_SOURCE_LENGTH),
						limit(message, MAX_DIAGNOSTIC_MESSAGE_LENGTH)
					)
				);
			}
		}

		private List<Diagnostic> retained() {
			return List.copyOf(this.retained);
		}

		private int totalCount() {
			return this.totalCount;
		}

		private static String limit(final String value, final int maximumLength) {
			String safe = value == null ? "" : value;
			return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
		}
	}
}
