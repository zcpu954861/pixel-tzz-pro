package io.github.zcpu954861.pixeltzzpro.network;

import java.util.Arrays;
import java.util.Optional;

/**
 * Stable machine-readable outcomes for protocol-v10 authority requests.
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
	DEGRADED_DIRECT("degraded_direct"),
	CALLBACK_FAILED("callback_failed"),
	TIMELINE_NOT_ACTIVE("timeline_not_active"),
	TIMELINE_ALREADY_ACTIVE("timeline_already_active"),
	TIMELINE_COMPLETED("timeline_completed"),
	TASK_NOT_CURRENT("task_not_current"),
	TASK_STATE_MISMATCH("task_state_mismatch"),
	RESULT_NOT_REGISTERED("result_not_registered"),
	RESULT_ALREADY_FROZEN("result_already_frozen"),
	COMPLETION_POLICY_MISMATCH("completion_policy_mismatch"),
	INTERMISSION_NOT_ACTIVE("intermission_not_active"),
	EVENT_NOT_REGISTERED("event_not_registered"),
	EVENT_DUPLICATE("event_duplicate"),
	EVENT_LIMIT_REACHED("event_limit_reached"),
	STATISTIC_NOT_REGISTERED("statistic_not_registered"),
	STATISTIC_TYPE_MISMATCH("statistic_type_mismatch"),
	EXCLUSIVE_OPTION_TAKEN("exclusive_option_taken"),
	EXCLUSIVE_VALUE_INVALID("exclusive_value_invalid"),
	EXCLUSIVE_REQUIREMENT_INCOMPLETE("exclusive_requirement_incomplete"),
	PHASE_MISSING("phase_missing"),
	REVISION_MISMATCH("revision_mismatch"),
	PERMISSION_DENIED("permission_denied"),
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
