package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;

/**
 * Runnable check for colored BossBar template substitution.
 */
public final class HostFlowBossBarSelfCheck {
	private HostFlowBossBarSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		RichText template = new RichText(
			"""
			[
			  {"text":"{event}","color":"gold"},
			  {"text":": ","color":"gray"},
			  {"text":"{completed}","color":"green"},
			  {"text":"/{total}","color":"white"}
			]
			""",
			"{event}: {completed}/{total}"
		);
		Component rendered = HostFlowBossBar.render(template, "A\"事件", 3, 8);
		check(
			rendered.getString().equals("A\"事件: 3/8"),
			"BossBar placeholder substitution or JSON escaping failed: " + rendered.getString()
		);
		check(
			rendered.getStyle().getColor() != null,
			"BossBar must retain the template's leading color"
		);

		RichText malformed = new RichText("{", "{event} {completed}/{total}");
		check(
			HostFlowBossBar.render(malformed, "Fallback", 1, 2)
				.getString()
				.equals("Fallback 1/2"),
			"malformed JSON must retain a readable plain-text fallback"
		);
		check(
			HostFlowBossBar.fadeProgress(6, 12) == 0.5F,
			"completion feedback must leave through a gradual progress fade"
		);
		check(
			HostFlowBossBar.fadeProgress(0, 12) == 0.0F,
			"completion feedback fade must reach zero before removal"
		);
		System.out.println("HOST_FLOW_BOSSBAR_SELF_CHECK=PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
