package io.github.zcpu954861.pixeltzzpro.network;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/** Stable protocol identities for host-side opening-countdown controls. */
public final class CountdownControlContract {
	public static final String CANCEL_OPERATION_TYPE = "cancel_opening_countdown";
	public static final Identifier CANCEL_OPERATION_ID = PixelTzzPro.id("countdown/cancel");
	public static final String RETRY_CALLBACKS_OPERATION_TYPE = "retry_countdown_callbacks";
	public static final Identifier RETRY_CALLBACKS_OPERATION_ID =
		PixelTzzPro.id("countdown/callback/retry_failed");

	private CountdownControlContract() {
	}

	public static boolean isCancelOperation(final String type, final Identifier id) {
		return CANCEL_OPERATION_TYPE.equals(Objects.requireNonNull(type, "type"))
			&& CANCEL_OPERATION_ID.equals(Objects.requireNonNull(id, "id"));
	}

	public static boolean isRetryCallbacksOperation(final String type, final Identifier id) {
		return RETRY_CALLBACKS_OPERATION_TYPE.equals(Objects.requireNonNull(type, "type"))
			&& RETRY_CALLBACKS_OPERATION_ID.equals(Objects.requireNonNull(id, "id"));
	}

	public static boolean isCountdownControl(final String type, final Identifier id) {
		return isCancelOperation(type, id) || isRetryCallbacksOperation(type, id);
	}
}
