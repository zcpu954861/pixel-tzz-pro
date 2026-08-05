package io.github.zcpu954861.pixeltzzpro.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionParser.ParseResult;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.CountdownDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudComponentDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudLayoutDefinition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.HudProfileDefinition;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/** Persistence/freeze codecs that always re-enter the same strict V3C parser on decode. */
public final class HudDefinitionCodecs {
	public static final Codec<HudComponentDefinition> COMPONENT = codec(
		HudDefinitionParser::parseComponent,
		HudComponentDefinition::id,
		HudComponentDefinition::canonicalDocument
	);
	public static final Codec<HudLayoutDefinition> LAYOUT = codec(
		HudDefinitionParser::parseLayout,
		HudLayoutDefinition::id,
		HudLayoutDefinition::canonicalDocument
	);
	public static final Codec<HudProfileDefinition> PROFILE = codec(
		HudDefinitionParser::parseProfile,
		HudProfileDefinition::id,
		HudProfileDefinition::canonicalDocument
	);
	public static final Codec<CountdownDefinition> COUNTDOWN = codec(
		HudDefinitionParser::parseCountdown,
		CountdownDefinition::id,
		CountdownDefinition::canonicalDocument
	);

	private HudDefinitionCodecs() {
	}

	private static <T> Codec<T> codec(
		final BiFunction<Identifier, String, ParseResult<T>> parser,
		final Function<T, Identifier> id,
		final Function<T, String> document
	) {
		return EncodedDefinition.CODEC.comapFlatMap(
			encoded -> decode(parser, encoded),
			value -> new EncodedDefinition(id.apply(value), document.apply(value))
		);
	}

	private static <T> DataResult<T> decode(
		final BiFunction<Identifier, String, ParseResult<T>> parser,
		final EncodedDefinition encoded
	) {
		ParseResult<T> result = parser.apply(encoded.id(), encoded.document());
		if (result.valid()) {
			return DataResult.success(result.value().orElseThrow());
		}
		String detail = result.issues().stream()
			.limit(4L)
			.map(issue -> issue.code() + " " + issue.path() + ": " + issue.message())
			.reduce((left, right) -> left + "; " + right)
			.orElse("strict HUD parser returned no definition");
		if (result.issuesTruncated() || result.issues().size() > 4) {
			detail += "; additional diagnostics omitted";
		}
		String message = detail;
		return DataResult.error(() -> message);
	}

	private record EncodedDefinition(Identifier id, String document) {
		private static final Codec<EncodedDefinition> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				Identifier.CODEC.fieldOf("id").forGetter(EncodedDefinition::id),
				Codec.STRING.fieldOf("document").forGetter(EncodedDefinition::document)
			).apply(instance, EncodedDefinition::new)
		);
	}
}
