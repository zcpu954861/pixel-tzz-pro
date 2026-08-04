package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AttachmentMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import java.util.Objects;

/**
 * Pure routing boundary for message lifecycle events observed by the Minecraft integration.
 *
 * <p>Only server-authoritative lifecycle events are routed here. A data-pack reload reads only
 * {@code lifecycle.reload}; {@code lifecycle.resource_reload} stays entirely client-local and
 * cannot complete or cancel a server target. Dimension exit is meaningful only for a
 * world-attached cue whose recipient actually left the frozen origin dimension.
 */
public final class MessageLifecyclePolicyRouter {
	private MessageLifecyclePolicyRouter() {
	}

	public static Directive resolve(
		final LifecyclePolicy policy,
		final Event event,
		final boolean leftOriginDimension
	) {
		Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(event, "event");
		return switch (event) {
			case DATA_PACK_RELOAD -> switch (policy.reload()) {
				case FINISH_SNAPSHOT -> Directive.CONTINUE;
				case FINALIZE -> Directive.FINALIZE;
				case CANCEL -> Directive.CANCEL;
			};
			case RESPAWN -> switch (policy.respawn()) {
				case CONTINUE -> Directive.CONTINUE;
				case FINALIZE -> Directive.FINALIZE;
				case CANCEL -> Directive.CANCEL;
			};
			case DIMENSION_EXIT -> {
				if (
					policy.attachment() == AttachmentMode.PLAYER
						|| !leftOriginDimension
				) {
					yield Directive.CONTINUE;
				}
				yield switch (policy.dimensionExit()) {
					case CONTINUE -> Directive.CONTINUE;
					case FINALIZE -> Directive.FINALIZE;
					case CANCEL -> Directive.CANCEL;
				};
			}
		};
	}

	public enum Event {
		DATA_PACK_RELOAD,
		RESPAWN,
		DIMENSION_EXIT
	}

	public enum Directive {
		CONTINUE,
		FINALIZE,
		CANCEL
	}
}
