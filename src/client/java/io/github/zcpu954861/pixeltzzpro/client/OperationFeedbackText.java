package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import java.util.Objects;

/**
 * Player-facing copy for operation results.
 *
 * <p>The server's raw message remains available to logs and diagnostics. Normal UI surfaces use
 * the stable operation code for expected failures, so internal English authority messages cannot
 * leak into player-facing screens.</p>
 */
public final class OperationFeedbackText {
	private OperationFeedbackText() {
	}

	public static String resolve(final OperationCode code, final String rawMessage) {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(rawMessage, "rawMessage");
		if (code == OperationCode.SUCCESS && !rawMessage.isBlank()) {
			return rawMessage;
		}
		if (
			code == OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE
				&& containsChinesePlayerFacingCopy(rawMessage)
		) {
			return rawMessage;
		}
		return fallback(code);
	}

	/**
	 * Capacity feedback is assembled by the authority from data-pack display names. Keep that
	 * concrete Chinese copy, while continuing to hide internal English diagnostics that share the
	 * same operation code.
	 */
	private static boolean containsChinesePlayerFacingCopy(final String rawMessage) {
		return rawMessage.codePoints().anyMatch(codePoint ->
			Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
		);
	}

	public static String fallback(final OperationCode code) {
		Objects.requireNonNull(code, "code");
		return switch (code) {
			case SUCCESS -> "操作已完成";
			case FORBIDDEN, PERMISSION_DENIED -> "当前没有执行此操作的权限";
			case NOT_HOST -> "此操作只能由当前主持人执行";
			case HOST_ALREADY_ASSIGNED -> "本局已经指定主持人";
			case TARGET_OFFLINE -> "目标玩家当前不在线";
			case TARGET_INVALID -> "目标玩家不符合操作条件";
			case TARGET_COUNT_INVALID -> "所选玩家数量不符合要求";
			case NO_ELIGIBLE_TARGETS -> "当前没有符合要求的玩家";
			case PHASE_MISMATCH -> "当前游戏阶段不允许此操作";
			case DEFINITION_GENERATION_STALE -> "数据包内容已更新，请重新操作";
			case STATE_REVISION_STALE, REVISION_MISMATCH ->
				"服务端状态已经变化，请重新操作";
			case CONFIRMATION_MISSING, CONFIRMATION_EXPIRED ->
				"确认凭证已更新，正在重新确认";
			case CONFIRMATION_CONSUMED -> "本次确认已经执行";
			case CONFIRMATION_CONTEXT_CHANGED -> "操作条件已经变化，请重新审阅";
			case ACTIVE_FLOW_EXISTS -> "当前已有一个强制流程正在进行";
			case FLOW_NOT_ACTIVE -> "当前没有可操作的强制流程";
			case FLOW_INSTANCE_MISMATCH -> "强制流程已经变化，请重新打开页面";
			case NODE_MISMATCH -> "当前流程节点已经变化";
			case REQUEST_REPLAYED -> "该请求已经处理";
			case UNSUPPORTED_OPERATION -> "当前版本不支持此操作";
			case UNSUPPORTED_FLOW_NODE -> "当前版本不支持此流程节点";
			case SNAPSHOT_TOO_LARGE -> "页面数据超出安全上限";
			case SNAPSHOT_INVALID -> "页面数据已失效，请重新同步";
			case RESOURCE_BLOCKED -> "缺少页面必需资源，操作已阻止";
			case DEGRADED_DIRECT -> "可选倒计时不可用；必须单独确认无倒计时开局";
			case CALLBACK_FAILED -> "数据包回调执行失败，需要主持人处理";
			case TIMELINE_NOT_ACTIVE -> "当前没有正在进行的任务时间线";
			case TIMELINE_ALREADY_ACTIVE -> "当前已有任务时间线正在进行";
			case TIMELINE_COMPLETED -> "本局任务时间线已经结束";
			case TASK_NOT_CURRENT -> "目标任务不是当前任务";
			case TASK_STATE_MISMATCH -> "当前任务状态不允许此操作";
			case RESULT_NOT_REGISTERED -> "该任务结果未在数据包中注册";
			case RESULT_ALREADY_FROZEN -> "本任务已经锁定了另一项结果，不能覆盖";
			case COMPLETION_POLICY_MISMATCH -> "当前任务不允许以这种方式结束";
			case INTERMISSION_NOT_ACTIVE -> "任务间隔已经结束，请返回任务时间线查看最新状态";
			case EVENT_NOT_REGISTERED -> "该任务事件未在数据包中注册";
			case EVENT_DUPLICATE -> "该任务事件已经记录";
			case EVENT_LIMIT_REACHED -> "任务事件记录已达到上限";
			case STATISTIC_NOT_REGISTERED -> "该统计项未在数据包中注册";
			case STATISTIC_TYPE_MISMATCH -> "统计值类型不符合数据包定义";
			case EXCLUSIVE_OPTION_TAKEN -> "该选项刚刚被其他玩家占用";
			case EXCLUSIVE_VALUE_INVALID -> "所选内容当前不可用";
			case EXCLUSIVE_REQUIREMENT_INCOMPLETE -> "仍有必选内容尚未完成";
			case PHASE_MISSING -> "数据包未注册当前阶段";
			case SCHEMA_BLOCKED -> "世界数据处于只读保护状态";
			case RATE_LIMITED -> "操作过于频繁，请稍候";
			case DEFINITION_UNAVAILABLE -> "当前没有可用的数据包内容定义";
			case ACTION_UNAVAILABLE -> "此操作当前不可用";
			case INTERNAL_ERROR -> "服务端处理操作时发生内部错误";
		};
	}
}
