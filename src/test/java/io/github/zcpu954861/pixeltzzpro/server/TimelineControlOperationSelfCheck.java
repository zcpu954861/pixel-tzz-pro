package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.server.ConfirmationTokens.OperationKey;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStep;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.CallbackStepStatus;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Small trust-boundary check for dynamic host-control operation identifiers.
 */
public final class TimelineControlOperationSelfCheck {
	private TimelineControlOperationSelfCheck() {
	}

	public static void main(final String[] args) {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-00000000002d");
		CallbackStep playerStep = new CallbackStep(
			"task/result/success/on_apply",
			Identifier.parse("pixel_tzz:acceptance_2d/timeline/faultable_result"),
			Optional.of(playerId),
			CallbackStepStatus.FAILED,
			2,
			Optional.of("fixture failure"),
			80L
		);
		Identifier playerOperationId = PixelTzzServerRuntime.timelineCallbackOperationId(playerStep);
		check(playerOperationId != null, "player callback operation id must be encodable");
		var playerKey = PixelTzzServerRuntime.decodeTimelineCallbackOperation(
			new OperationKey("retry_timeline_callback", playerOperationId)
		).orElseThrow();
		check(
			playerKey.stepKey().equals(playerStep.stepKey())
				&& playerKey.playerId().equals(Optional.of(playerId)),
			"player callback operation id must round-trip"
		);

		CallbackStep globalStep = new CallbackStep(
			"task/on_start",
			Identifier.parse("pixel_tzz:acceptance_2d/timeline/capture_context"),
			Optional.empty(),
			CallbackStepStatus.OUTCOME_UNKNOWN,
			1,
			Optional.of("server stopped before outcome"),
			40L
		);
		Identifier globalOperationId = PixelTzzServerRuntime.timelineCallbackOperationId(globalStep);
		check(globalOperationId != null, "global callback operation id must be encodable");
		var globalKey = PixelTzzServerRuntime.decodeTimelineCallbackOperation(
			new OperationKey("retry_timeline_callback", globalOperationId)
		).orElseThrow();
		check(
			globalKey.stepKey().equals(globalStep.stepKey())
				&& globalKey.playerId().isEmpty(),
			"global callback operation id must round-trip"
		);
		check(
			PixelTzzServerRuntime.decodeTimelineCallbackOperation(
				new OperationKey(
					"retry_timeline_callback",
					Identifier.parse("other:timeline/callback/retry/task/on_start/global")
				)
			).isEmpty(),
			"foreign callback operation namespaces must fail closed"
		);
		check(
			PixelTzzServerRuntime.timelineFallbackResult(
				new OperationKey(
					"host_fallback_result",
					Identifier.parse("pixel-tzz-pro:timeline/result/failure")
				)
			).filter("failure"::equals).isPresent(),
			"host fallback result id must be extracted from the trusted namespace"
		);
		check(
			PixelTzzServerRuntime.timelineFallbackResult(
				new OperationKey(
					"host_fallback_result",
					Identifier.parse("other:timeline/result/failure")
				)
			).isEmpty(),
			"foreign fallback result namespaces must fail closed"
		);

		OperationKey pause = new OperationKey(
			"pause_timeline",
			Identifier.parse("pixel-tzz-pro:timeline/pause")
		);
		check(
			PixelTzzServerRuntime.immediateTimelineOperation(pause),
			"host pause must be a direct operation"
		);
		check(
			PixelTzzServerRuntime.timelineOperation(pause),
			"host pause must remain inside the timeline operation family"
		);
		check(
			!PixelTzzServerRuntime.timelineOperation(
				new OperationKey(
					"unrelated",
					Identifier.parse("pixel-tzz-pro:unrelated")
				)
			),
			"unrelated console actions must not bypass definition health checks"
		);

		new ConsoleSnapshotS2CPayload.ActionEntry(
			"pause_timeline",
			pause.id(),
			"primary",
			-900,
			"{\"text\":\"暂停任务时间线\"}",
			"{\"text\":\"直接执行\"}",
			"#55D7E6",
			true,
			"",
			"none",
			0,
			0,
			false
		);
		System.out.println("TIMELINE_CONTROL_OPERATION_SELF_CHECK: PASS");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
