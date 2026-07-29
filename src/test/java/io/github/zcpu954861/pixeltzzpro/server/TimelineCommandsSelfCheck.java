package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.RecapVisibility;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticDefinition;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticType;
import io.github.zcpu954861.pixeltzzpro.content.TaskDefinitions.TaskStatisticWrite;
import java.util.Map;
import java.util.Optional;

/**
 * Small parser check for the data-pack command trust boundary.
 */
public final class TimelineCommandsSelfCheck {
	private TimelineCommandsSelfCheck() {
	}

	public static void main(final String[] arguments) {
		check(
			TimelineCommands.parseStatisticValue(
				definition(TaskStatisticType.BOOLEAN),
				"flag",
				"true",
				Map.of()
			).orElseThrow().booleanValue().orElseThrow(),
			"boolean value"
		);
		check(
			TimelineCommands.parseStatisticValue(
				definition(TaskStatisticType.BOOLEAN),
				"flag",
				"yes",
				Map.of()
			).isEmpty(),
			"invalid boolean"
		);
		check(
			TimelineCommands.parseStatisticValue(
				definition(TaskStatisticType.INTEGER),
				"count",
				"9223372036854775807",
				Map.of()
			).orElseThrow().longValue().orElseThrow() == Long.MAX_VALUE,
			"bounded integer parser"
		);
		check(
			TimelineCommands.parseStatisticValue(
				definition(TaskStatisticType.IDENTIFIER),
				"source",
				"pixel_tzz:terminal",
				Map.of()
			).orElseThrow().identifierValue().orElseThrow().toString().equals("pixel_tzz:terminal"),
			"identifier parser"
		);
		check(
			TimelineCommands.parseStatisticValue(
				definition(TaskStatisticType.INTEGER),
				"count",
				"1.5",
				Map.of()
			).isEmpty(),
			"non-integer rejection"
		);
		System.out.println("TIMELINE_COMMANDS_SELF_CHECK=PASS");
	}

	private static TaskStatisticDefinition definition(final TaskStatisticType type) {
		return new TaskStatisticDefinition(
			"value",
			new RichText("{\"text\":\"Value\"}", "Value"),
			type,
			Optional.empty(),
			Optional.empty(),
			TaskStatisticWrite.REPLACE,
			RecapVisibility.PARTICIPANTS
		);
	}

	private static void check(final boolean condition, final String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
