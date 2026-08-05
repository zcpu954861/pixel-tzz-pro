package io.github.zcpu954861.pixeltzzpro.client.hud;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerValue;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Version;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.LayoutVariant;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Mode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Atomic generic-node projection for the one active opening-countdown overlay. */
public record ClientCountdownSnapshot(
	Version version,
	UUID countdownInstanceId,
	String layoutId,
	Mode mode,
	String rootNodeId,
	Map<String, ClientHudNode> nodes,
	List<LayoutVariant> layoutVariants,
	TimerValue remaining,
	long totalTicks,
	long receivedAtNanos,
	String structureKey,
	String canonicalJson
) {
	public ClientCountdownSnapshot {
		version = Objects.requireNonNull(version, "version");
		countdownInstanceId = Objects.requireNonNull(countdownInstanceId, "countdownInstanceId");
		layoutId = ClientHudSnapshot.boundedId(layoutId, "layoutId");
		mode = Objects.requireNonNull(mode, "mode");
		rootNodeId = ClientHudSnapshot.boundedId(rootNodeId, "rootNodeId");
		nodes = Map.copyOf(nodes);
		if (nodes.isEmpty() || !nodes.containsKey(rootNodeId)) {
			throw new IllegalArgumentException("countdown projection has no reachable root node");
		}
		layoutVariants = List.copyOf(layoutVariants);
		ClientHudSnapshot.validateLayoutAuthority(layoutId, mode, rootNodeId, nodes, layoutVariants);
		remaining = Objects.requireNonNull(remaining, "remaining");
		if (totalTicks <= 0L || receivedAtNanos < 0L) {
			throw new IllegalArgumentException("countdown bounds are invalid");
		}
		structureKey = Objects.requireNonNull(structureKey, "structureKey");
		canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
	}
}
