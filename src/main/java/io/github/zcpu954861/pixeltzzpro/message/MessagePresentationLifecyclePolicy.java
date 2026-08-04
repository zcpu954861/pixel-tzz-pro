package io.github.zcpu954861.pixeltzzpro.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.DimensionExit;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Respawn;
import java.util.Objects;

/**
 * Resolves lifecycle events at the narrow boundary where presentation is locally owned.
 *
 * <p>Ordinary network instances remain server-authoritative. A local built-in cue has no server
 * stream, while a network cue moves to client ownership only after FINISH has locked every final
 * field and closed that stream. The projected dimension-exit mode is already normalized to
 * CONTINUE for player-attached cues, so the client only needs to prove that a transfer actually
 * left the frozen origin dimension.</p>
 */
public final class MessagePresentationLifecyclePolicy {
	private MessagePresentationLifecyclePolicy() {
	}

	public static Directive respawn(
		final boolean presentationLocallyOwned,
		final Respawn mode
	) {
		Objects.requireNonNull(mode, "mode");
		if (!presentationLocallyOwned) {
			return Directive.WAIT_FOR_SERVER;
		}
		return switch (mode) {
			case CONTINUE -> Directive.CONTINUE;
			case FINALIZE -> Directive.FINALIZE;
			case CANCEL -> Directive.CANCEL;
		};
	}

	public static Directive dimensionExit(
		final boolean presentationLocallyOwned,
		final boolean leftFrozenOriginDimension,
		final DimensionExit mode
	) {
		Objects.requireNonNull(mode, "mode");
		if (!presentationLocallyOwned) {
			return Directive.WAIT_FOR_SERVER;
		}
		if (!leftFrozenOriginDimension) {
			return Directive.CONTINUE;
		}
		return switch (mode) {
			case CONTINUE -> Directive.CONTINUE;
			case FINALIZE -> Directive.FINALIZE;
			case CANCEL -> Directive.CANCEL;
		};
	}

	public enum Directive {
		WAIT_FOR_SERVER,
		CONTINUE,
		FINALIZE,
		CANCEL
	}
}
