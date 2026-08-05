package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.HudDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.Anchor;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.ChangeTransition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.EnterTransition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.ExitTransition;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.Growth;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.Surface;
import io.github.zcpu954861.pixeltzzpro.content.HudDefinitions.TabCollision;
import java.util.Set;

/** Pure parser checks for V3C layout geometry and client-adjustment policy envelopes. */
public final class HudLayoutPolicySelfCheck {
	private HudLayoutPolicySelfCheck() {
	}

	public static void main(final String[] args) {
		checkValidLayouts();
		checkLayoutRejections();
		checkValidClientPolicy();
		checkClientPolicyRejections();
		System.out.println("HUD layout policy self-check passed");
	}

	private static void checkValidLayouts() {
		var dock = HudDefinitionParser.parseLayout(
			HudSelfCheckFixtures.id("test:dock"),
			layout("dock", 120, 160, 220, 180, "up", true)
		);
		check(dock.valid(), "valid dock layout must parse: " + dock.issues());
		var dockLayout = dock.value().orElseThrow();
		check(dockLayout.surface() == Surface.DOCK, "dock surface was not retained");
		check(
			dockLayout.size().minimumWidth() == 120
				&& dockLayout.size().preferredWidth() == 160
				&& dockLayout.size().maximumWidth() == 220
				&& dockLayout.size().maximumHeight() == 180
				&& dockLayout.size().growth() == Growth.UP,
			"dock size envelope was not retained"
		);
		check(
			dockLayout.transition().enter() == EnterTransition.RISE_FADE
				&& dockLayout.transition().change() == ChangeTransition.CROSSFADE_VALUES
				&& dockLayout.transition().exit() == ExitTransition.FADE,
			"dock transitions were not retained"
		);

		var countdown = HudDefinitionParser.parseLayout(
			HudSelfCheckFixtures.id("test:countdown"),
			layout("countdown", 144, 188, 224, 112, "center", false)
		);
		check(countdown.valid(), "valid countdown layout must parse: " + countdown.issues());
		check(
			countdown.value().orElseThrow().surface() == Surface.COUNTDOWN
				&& countdown.value().orElseThrow().size().growth() == Growth.CENTER,
			"countdown layout must retain centered growth"
		);
	}

	private static void checkLayoutRejections() {
		expectLayoutIssue(
			layout("dock", 120, 160, 220, 180, "center", false),
			"/size/growth",
			"INVALID_CONSTRAINT",
			"dock center growth"
		);
		expectLayoutIssue(
			layout("countdown", 144, 188, 224, 112, "up", false),
			"/size/growth",
			"INVALID_CONSTRAINT",
			"countdown upward growth"
		);
		expectLayoutIssue(
			layout("dock", 220, 160, 120, 180, "up", false),
			"/size",
			"OUT_OF_RANGE",
			"unordered layout widths"
		);
		expectLayoutIssue(
			layout("dock", 120, 160, 220, 2049, "up", false),
			"/size/maximum_height",
			"OUT_OF_RANGE",
			"layout height outside logical-size envelope"
		);
	}

	private static void checkValidClientPolicy() {
		var parsed = HudDefinitionParser.parseProfile(
			HudSelfCheckFixtures.id("test:profile"),
			profilePolicy("""
				{
				  "allow_hide": true,
				  "default_anchor": "bottom_right",
				  "allowed_anchors": ["bottom_right", "bottom_left"],
				  "offset": {"max_x": 96, "max_y": 72},
				  "scale": {"minimum": 0.75, "default": 1.0, "maximum": 1.25},
				  "opacity": {"minimum": 0.55, "default": 0.92, "maximum": 1.0},
				  "allow_component_management": true,
				  "tab_collision": "hide"
				}
				""")
		);
		check(parsed.valid(), "valid client policy must parse: " + parsed.issues());
		var policy = parsed.value().orElseThrow().clientPolicy();
		check(policy.allowHide(), "allow_hide was not retained");
		check(policy.defaultAnchor() == Anchor.BOTTOM_RIGHT, "default anchor was not retained");
		check(
			policy.allowedAnchors().equals(Set.of(Anchor.BOTTOM_RIGHT, Anchor.BOTTOM_LEFT)),
			"allowed anchor set was not retained"
		);
		check(policy.offset().maxX() == 96 && policy.offset().maxY() == 72, "offset envelope was not retained");
		check(
			close(policy.scale().minimum(), 0.75D)
				&& close(policy.scale().defaultValue(), 1.0D)
				&& close(policy.scale().maximum(), 1.25D),
			"scale envelope was not retained"
		);
		check(
			close(policy.opacity().minimum(), 0.55D)
				&& close(policy.opacity().defaultValue(), 0.92D)
				&& close(policy.opacity().maximum(), 1.0D),
			"opacity envelope was not retained"
		);
		check(policy.allowComponentManagement(), "component-management permission was not retained");
		check(policy.tabCollision() == TabCollision.HIDE, "tab collision policy was not retained");
	}

	private static void checkClientPolicyRejections() {
		expectProfileIssue(profilePolicy("""
			{
			  "default_anchor":"top_left",
			  "allowed_anchors":["bottom_right"]
			}
			"""), "/client_policy/default_anchor", "INVALID_CONSTRAINT", "disallowed default anchor");
		expectProfileIssue(profilePolicy("""
			{
			  "allowed_anchors":[],
			  "default_anchor":"bottom_right"
			}
			"""), "/client_policy/allowed_anchors", "OUT_OF_RANGE", "empty anchor set");
		expectProfileIssue(profilePolicy("""
			{
			  "offset":{"max_x":641,"max_y":361}
			}
			"""), "/client_policy/offset", "OUT_OF_RANGE", "offset outside reference canvas");
		expectProfileIssue(profilePolicy("""
			{
			  "scale":{"minimum":0.25,"default":1.0,"maximum":2.25}
			}
			"""), "/client_policy/scale", "OUT_OF_RANGE", "scale outside policy envelope");
		expectProfileIssue(profilePolicy("""
			{
			  "allow_hide":false,
			  "opacity":{"minimum":0.0,"default":0.5,"maximum":1.0}
			}
			"""), "/client_policy/opacity/minimum", "INVALID_CONSTRAINT", "invisible non-hideable HUD");
		expectProfileIssue(profilePolicy("""
			{
			  "scale":{"minimum":1.2,"default":1.0,"maximum":1.1}
			}
			"""), "/client_policy/scale", "OUT_OF_RANGE", "unordered scale envelope");
	}

	private static String layout(
		final String surface,
		final int minimum,
		final int preferred,
		final int maximum,
		final int maximumHeight,
		final String growth,
		final boolean transition
	) {
		return """
			{
			  "format_version":1,
			  "surface":"%s",
			  "root":"test:root",
			  "size":{
			    "minimum_width":%d,
			    "preferred_width":%d,
			    "maximum_width":%d,
			    "maximum_height":%d,
			    "growth":"%s"
			  }%s
			}
			""".formatted(
			surface,
			minimum,
			preferred,
			maximum,
			maximumHeight,
			growth,
			transition
				? ",\n  \"transition\":{\"enter\":\"rise_fade\",\"change\":\"crossfade_values\",\"exit\":\"fade\"}"
				: ""
		);
	}

	private static String profilePolicy(final String policy) {
		return """
			{
			  "format_version":1,
			  "default_layout":"test:dock",
			  "client_policy":%s
			}
			""".formatted(policy);
	}

	private static void expectLayoutIssue(
		final String json,
		final String path,
		final String code,
		final String label
	) {
		var parsed = HudDefinitionParser.parseLayout(HudSelfCheckFixtures.id("test:invalid"), json);
		check(!parsed.valid(), label + " must be rejected");
		check(
			parsed.issues().stream().anyMatch(issue -> issue.path().equals(path) && issue.code().equals(code)),
			label + " did not retain " + code + " at " + path + ": " + parsed.issues()
		);
	}

	private static void expectProfileIssue(
		final String json,
		final String path,
		final String code,
		final String label
	) {
		var parsed = HudDefinitionParser.parseProfile(HudSelfCheckFixtures.id("test:invalid"), json);
		check(!parsed.valid(), label + " must be rejected");
		check(
			parsed.issues().stream().anyMatch(issue -> issue.path().equals(path) && issue.code().equals(code)),
			label + " did not retain " + code + " at " + path + ": " + parsed.issues()
		);
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < 0.0001D;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
