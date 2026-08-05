package io.github.zcpu954861.pixeltzzpro.client.hud;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.BackgroundNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ContainerNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.ProgressNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode.TimerNode;
import java.util.Map;
import java.util.Objects;

/**
 * Stable presentation fingerprint that ignores continuously interpolated progress/timer anchors.
 * It is shared by transition and sound gates so heartbeats cannot replay either effect.
 */
final class HudVisualFingerprint {
	private HudVisualFingerprint() {
	}

	static int of(final Map<String, ClientHudNode> nodes) {
		int result = 1;
		for (var entry : nodes.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			result = 31 * result + entry.getKey().hashCode();
			result = 31 * result + node(entry.getValue());
		}
		return result;
	}

	private static int node(final ClientHudNode node) {
		if (node instanceof ProgressNode progress) {
			return Objects.hash(
				progress.meta(),
				progress.maximum(),
				progress.orientation(),
				progress.label(),
				progress.segments(),
				progress.smooth(),
				progress.spine()
			);
		}
		if (node instanceof TimerNode timer) {
			return Objects.hash(
				timer.meta(),
				timer.label(),
				timer.timer().paused(),
				timer.timer().format(),
				timer.timer().pausedMarker(),
				Math.signum(timer.timer().rate())
			);
		}
		if (node instanceof ContainerNode container) {
			return Objects.hash(
				container.meta(),
				container.children(),
				container.gap(),
				container.alignment(),
				container.weights()
			);
		}
		if (node instanceof BackgroundNode background) {
			return Objects.hash(
				background.meta(),
				background.child(),
				background.fill(),
				background.borderColor(),
				background.borderWidth(),
				background.innerPadding()
			);
		}
		return node.hashCode();
	}
}
