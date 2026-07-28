package io.github.zcpu954861.pixeltzzpro.network;

import java.util.Arrays;
import java.util.Optional;

/**
 * Stable machine-readable outcomes for protocol-v7 authority requests.
 */
public enum OperationCode {
	SUCCESS("success"),
	FORBIDDEN("forbidden"),
	NOT_HOST("not_host"),
	HOST_ALREADY_ASSIGNED("host_already_assigned"),
	TARGET_OFFLINE("target_offline"),
	TARGET_INVALID("target_invalid"),
	TARGET_COUNT_INVALID("target_count_invalid"),
	NO_ELIGIBLE_TARGETS("no_eligible_targets"),
	PHASE_MISMATCH("phase_mismatch"),
	DEFINITION_GENERATION_STALE("definition_generation_stale"),
	STATE_REVISION_STALE("state_revision_stale"),
	CONFIRMATION_MISSING("confirmation_missing"),
	CONFIRMATION_EXPIRED("confirmation_expired"),
	CONFIRMATION_CONSUMED("confirmation_consumed"),
	CONFIRMATION_CONTEXT_CHANGED("confirmation_context_changed"),
	ACTIVE_FLOW_EXISTS("active_flow_exists"),
	FLOW_NOT_ACTIVE("flow_not_active"),
	FLOW_INSTANCE_MISMATCH("flow_instance_mismatch"),
	NODE_MISMATCH("node_mismatch"),
	REQUEST_REPLAYED("request_replayed"),
	UNSUPPORTED_OPERATION("unsupported_operation"),
	UNSUPPORTED_FLOW_NODE("unsupported_flow_node"),
	SNAPSHOT_TOO_LARGE("snapshot_too_large"),
	SNAPSHOT_INVALID("snapshot_invalid"),
	RESOURCE_BLOCKED("resource_blocked"),
	CALLBACK_FAILED("callback_failed"),
	SCHEMA_BLOCKED("schema_blocked"),
	RATE_LIMITED("rate_limited"),
	DEFINITION_UNAVAILABLE("definition_unavailable"),
	ACTION_UNAVAILABLE("action_unavailable"),
	INTERNAL_ERROR("internal_error");

	private final String serializedName;

	OperationCode(final String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return this.serializedName;
	}

	public static Optional<OperationCode> bySerializedName(final String name) {
		return Arrays.stream(values())
			.filter(value -> value.serializedName.equals(name))
			.findFirst();
	}
}
