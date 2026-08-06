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
	public static final String RETRY_UNKNOWN_CALLBACKS_OPERATION_TYPE =
		"retry_unknown_countdown_callbacks";
	public static final Identifier RETRY_UNKNOWN_CALLBACKS_OPERATION_ID =
		PixelTzzPro.id("countdown/callback/retry_unknown_high_risk");
	public static final String RETRY_HANDOFF_OPERATION_TYPE = "retry_countdown_handoff";
	public static final Identifier RETRY_HANDOFF_OPERATION_ID =
		PixelTzzPro.id("countdown/handoff/retry");
	public static final String ABANDON_HANDOFF_OPERATION_TYPE = "abandon_countdown_handoff";
	public static final Identifier ABANDON_HANDOFF_OPERATION_ID =
		PixelTzzPro.id("countdown/handoff/abandon");
	public static final String CLEAR_ALL_STATE_OPERATION_TYPE = "clear_all_state";
	public static final Identifier CLEAR_ALL_STATE_OPERATION_ID = PixelTzzPro.id("reset/all");

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

	public static boolean isRetryUnknownCallbacksOperation(final String type, final Identifier id) {
		return RETRY_UNKNOWN_CALLBACKS_OPERATION_TYPE.equals(Objects.requireNonNull(type, "type"))
			&& RETRY_UNKNOWN_CALLBACKS_OPERATION_ID.equals(Objects.requireNonNull(id, "id"));
	}

	public static boolean isRetryHandoffOperation(final String type, final Identifier id) {
		return RETRY_HANDOFF_OPERATION_TYPE.equals(Objects.requireNonNull(type, "type"))
			&& RETRY_HANDOFF_OPERATION_ID.equals(Objects.requireNonNull(id, "id"));
	}

	public static boolean isAbandonHandoffOperation(final String type, final Identifier id) {
		return ABANDON_HANDOFF_OPERATION_TYPE.equals(Objects.requireNonNull(type, "type"))
			&& ABANDON_HANDOFF_OPERATION_ID.equals(Objects.requireNonNull(id, "id"));
	}

	public static boolean isCountdownControl(final String type, final Identifier id) {
		return isCancelOperation(type, id)
			|| isRetryCallbacksOperation(type, id)
			|| isRetryUnknownCallbacksOperation(type, id)
			|| isRetryHandoffOperation(type, id)
			|| isAbandonHandoffOperation(type, id);
	}

	public static boolean isClearAllStateOperation(final String type, final Identifier id) {
		return CLEAR_ALL_STATE_OPERATION_TYPE.equals(Objects.requireNonNull(type, "type"))
			&& CLEAR_ALL_STATE_OPERATION_ID.equals(Objects.requireNonNull(id, "id"));
	}

	/**
	 * Host recovery operations whose review must survive unrelated global-revision pushes.
	 *
	 * <p>The server still re-prepares and revalidates every operation before issuing and consuming
	 * its confirmation. This classification only prevents routine connection, audit, or countdown
	 * projection churn from making the sole recovery entry appear inert.</p>
	 */
	public static boolean isRevisionStableHostOperation(final String type, final Identifier id) {
		return isCountdownControl(type, id) || isClearAllStateOperation(type, id);
	}
}
