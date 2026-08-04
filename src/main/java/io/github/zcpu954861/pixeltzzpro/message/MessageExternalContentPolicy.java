package io.github.zcpu954861.pixeltzzpro.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ExternalConflict;
import java.util.Objects;

/** Resolves how one V3B HUD frame coexists with an already-active external HUD surface. */
public final class MessageExternalContentPolicy {
	private MessageExternalContentPolicy() {
	}

	public static Directive resolve(
		final boolean externalContentVisible,
		final ExternalConflict mode
	) {
		Objects.requireNonNull(mode, "mode");
		if (!externalContentVisible) {
			return Directive.RENDER;
		}
		return switch (mode) {
			case YIELD -> Directive.HIDE_CONTINUE;
			case PAUSE -> Directive.HIDE_PAUSE;
			case OFFSET -> Directive.RENDER_OFFSET;
			case OVERLAY -> Directive.RENDER;
		};
	}

	public enum Directive {
		RENDER(true, false, false),
		HIDE_CONTINUE(false, false, false),
		HIDE_PAUSE(false, true, false),
		RENDER_OFFSET(true, false, true);

		private final boolean visible;
		private final boolean pauseClock;
		private final boolean offset;

		Directive(
			final boolean visible,
			final boolean pauseClock,
			final boolean offset
		) {
			this.visible = visible;
			this.pauseClock = pauseClock;
			this.offset = offset;
		}

		public boolean visible() {
			return this.visible;
		}

		public boolean pauseClock() {
			return this.pauseClock;
		}

		public boolean offset() {
			return this.offset;
		}
	}
}
