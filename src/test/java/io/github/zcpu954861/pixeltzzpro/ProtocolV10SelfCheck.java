package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState;
import io.github.zcpu954861.pixeltzzpro.client.ClientConsoleState.RequestKind;
import io.github.zcpu954861.pixeltzzpro.client.ClientTimelineState;
import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.CancelConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CommitConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ExclusiveChoiceMutationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostSubtitleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostUiStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.ExclusiveOptionState;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FieldSchema;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FlowContext;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.PagePurpose;
import io.github.zcpu954861.pixeltzzpro.network.payload.PrepareOperationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.StatefulRequest;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TimelineViewS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Small protocol-v10 codec and trust-boundary check without a test framework.
 */
public final class ProtocolV10SelfCheck {
	private static final UUID FLOW_INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID PAGE_INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000103");
	private static final Identifier GAME = Identifier.parse("pixel_tzz:main");
	private static final Identifier OPERATION = Identifier.parse("pixel_tzz:test_operation");
	private static final Identifier FLOW = Identifier.parse("pixel_tzz:test_flow");

	private ProtocolV10SelfCheck() {
	}

	public static void main(final String[] args) {
		check(NetworkProtocol.CURRENT_VERSION == 10, "exclusive live mutations require protocol v10");
		checkSessionSnapshot();
		checkStatefulRequests();
		checkConfirmationPayloads();
		checkConsolePayloads();
		checkTargetPayloads();
		checkFlowPayloads();
		checkResourceReports();
		checkForcedPagePayloads();
		checkHostUiPayload();
		checkHostSubtitlePayload();
		checkTimelinePushSupersedesPendingResponse();
		checkConsoleCommitResultSurvivesSessionPush();
		checkNoTargetReviewRemainsTargetless();
		System.out.println("PROTOCOL_V10_SELF_CHECK=PASS");
	}

	private static void checkConsoleCommitResultSurvivesSessionPush() {
		ClientConsoleState.beginConnection();
		long prepareSequence = 41L;
		long commitSequence = 42L;
		setConsolePending(prepareSequence, RequestKind.PREPARE, OPERATION);
		ClientConsoleState.acceptConfirmation(
			new ConfirmationS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				prepareSequence,
				TOKEN,
				"interrupt_timeline",
				OPERATION,
				"{\"text\":\"确认紧急终止整局\"}",
				List.of("{\"text\":\"任务时间线将立即停止\"}"),
				List.of(),
				"主线任务 · 进行中 · 无需选择玩家",
				30_000L,
				7L,
				3L
			)
		);
		check(
			ClientConsoleState.confirmation().isPresent(),
			"the fixture confirmation must be accepted before commit"
		);

		setConsolePending(commitSequence, RequestKind.COMMIT, OPERATION);
		ClientConsoleState.acceptSessionProjection(sessionSnapshot(8L, 3L));
		check(
			ClientConsoleState.pending() && ClientConsoleState.confirmation().isPresent(),
			"a newer session push from the committed transaction must not consume COMMIT state"
		);

		String successMessage = "本局任务时间线已紧急终止。";
		ClientConsoleState.acceptOperationResult(
			new OperationResultS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				commitSequence,
				OperationCode.SUCCESS,
				successMessage,
				8L,
				3L,
				false
			)
		);
		check(!ClientConsoleState.pending(), "the correlated success result must settle COMMIT");
		check(
			ClientConsoleState.lastResult()
				.filter(result -> result.successfulCommit(OPERATION))
				.isPresent(),
			"the correlated success result must still be consumed after the newer session push"
		);
		check(
			ClientConsoleState.feedback().equals(successMessage)
				&& !ClientConsoleState.feedbackError(),
			"successful operations must retain the server-authored success message"
		);

		setConsolePending(43L, RequestKind.PREPARE, OPERATION);
		ClientConsoleState.acceptOperationResult(
			new OperationResultS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				43L,
				OperationCode.ACTION_UNAVAILABLE,
				"manual target selection is not allowed in the current state",
				8L,
				3L,
				false
			)
		);
		check(
			ClientConsoleState.feedback().equals("此操作当前不可用"),
			"expected failures must prefer stable Chinese copy over raw authority diagnostics"
		);
		ClientConsoleState.disconnect();
	}

	private static void checkNoTargetReviewRemainsTargetless() {
		ConsoleSnapshotS2CPayload.ActionEntry noTargetAction =
			new ConsoleSnapshotS2CPayload.ActionEntry(
				"interrupt_timeline",
				OPERATION,
				"danger",
				0,
				"{\"text\":\"紧急终止整局\"}",
				"{\"text\":\"停止当前任务时间线\"}",
				"red",
				true,
				"",
				"none",
				0,
				0,
				true
			);
		List<UUID> affectedPlayerLabels = List.of(
			UUID.fromString("00000000-0000-0000-0000-000000000201"),
			UUID.fromString("00000000-0000-0000-0000-000000000202")
		);
		check(
			invokeRenewalTargetIds(noTargetAction, affectedPlayerLabels).isEmpty(),
			"no-target review must not turn explanatory affected-player labels into targetIds"
		);
	}

	private static void checkTimelinePushSupersedesPendingResponse() {
		ClientTimelineState.beginConnection();
		setStaticLong(ClientTimelineState.class, "pendingSequence", 41L);
		setStaticLong(ClientTimelineState.class, "pendingSinceMillis", Util.getMillis());
		setStaticBoolean(ClientTimelineState.class, "pendingVisible", false);
		check(
			!ClientTimelineState.pending(),
			"a silent timeline refresh must not expose a pending UI state"
		);
		setStaticBoolean(ClientTimelineState.class, "pendingVisible", true);
		check(ClientTimelineState.pending(), "fixture request must begin pending");

		TimelineViewS2CPayload push = TimelineViewS2CPayload.unavailable(
			NetworkProtocol.CURRENT_VERSION,
			0L,
			"push"
		);
		ClientTimelineState.accept(push);
		check(!ClientTimelineState.pending(), "an authoritative timeline push must settle pending UI");

		ClientTimelineState.accept(
			TimelineViewS2CPayload.unavailable(
				NetworkProtocol.CURRENT_VERSION,
				41L,
				"late response"
			)
		);
		check(
			ClientTimelineState.snapshot().orElseThrow().message().equals("push"),
			"a late response must not overwrite the newer authoritative push"
		);
		ClientTimelineState.disconnect();
	}

	private static SessionSnapshotS2CPayload sessionSnapshot(
		final long stateRevision,
		final long definitionGeneration
	) {
		return new SessionSnapshotS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			Optional.empty(),
			true,
			true,
			true,
			true,
			stateRevision,
			1L,
			"ready",
			true,
			definitionGeneration,
			1,
			1,
			"",
			false
		);
	}

	private static void setConsolePending(
		final long sequence,
		final RequestKind kind,
		final Identifier operationId
	) {
		try {
			Class<?> pendingType = Arrays.stream(ClientConsoleState.class.getDeclaredClasses())
				.filter(type -> type.getSimpleName().equals("Pending"))
				.findFirst()
				.orElseThrow();
			Constructor<?> constructor = pendingType.getDeclaredConstructor(
				long.class,
				RequestKind.class,
				Optional.class
			);
			constructor.setAccessible(true);
			Object value = constructor.newInstance(sequence, kind, Optional.of(operationId));
			Field field = ClientConsoleState.class.getDeclaredField("pending");
			field.setAccessible(true);
			field.set(null, value);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("could not prepare console request fixture", error);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<UUID> invokeRenewalTargetIds(
		final ConsoleSnapshotS2CPayload.ActionEntry action,
		final List<UUID> reviewedTargetIds
	) {
		try {
			Method method = ClientConsoleState.class.getDeclaredMethod(
				"renewalTargetIds",
				ConsoleSnapshotS2CPayload.ActionEntry.class,
				List.class
			);
			method.setAccessible(true);
			return (List<UUID>) method.invoke(null, action, reviewedTargetIds);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("could not verify no-target review normalization", error);
		}
	}

	private static void setStaticBoolean(
		final Class<?> owner,
		final String fieldName,
		final boolean value
	) {
		try {
			Field field = owner.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.setBoolean(null, value);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("could not prepare timeline visibility fixture", error);
		}
	}

	private static void setStaticLong(
		final Class<?> owner,
		final String fieldName,
		final long value
	) {
		try {
			Field field = owner.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.setLong(null, value);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("could not prepare timeline response gate fixture", error);
		}
	}

	private static void checkSessionSnapshot() {
		SessionSnapshotS2CPayload dynamicPhase = new SessionSnapshotS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			Optional.of(Identifier.parse("test:cinematic/setup")),
			true,
			true,
			true,
			true,
			4L,
			2L,
			"ready",
			true,
			3L,
			1,
			8,
			"",
			false
		);
		check(
			roundTrip(SessionSnapshotS2CPayload.STREAM_CODEC, dynamicPhase).equals(dynamicPhase),
			"session snapshot must retain an arbitrary full phase identifier"
		);
		SessionSnapshotS2CPayload absentPhase = new SessionSnapshotS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			Optional.empty(),
			false,
			false,
			false,
			true,
			0L,
			0L,
			"empty",
			false,
			0L,
			0,
			0,
			"",
			false
		);
		check(
			roundTrip(SessionSnapshotS2CPayload.STREAM_CODEC, absentPhase).phaseId().isEmpty(),
			"session snapshot must retain an absent phase without inventing idle"
		);
	}

	private static void checkHostUiPayload() {
		HostUiStateC2SPayload visible = new HostUiStateC2SPayload(true);
		check(
			roundTrip(HostUiStateC2SPayload.STREAM_CODEC, visible).equals(visible),
			"host UI state codec round-trip failed"
		);
	}

	private static void checkHostSubtitlePayload() {
		HostSubtitleS2CPayload payload = new HostSubtitleS2CPayload(
			23L,
			"任务开始 · 广场逃脱"
		);
		check(
			roundTrip(HostSubtitleS2CPayload.STREAM_CODEC, payload).equals(payload),
			"host subtitle codec round-trip failed"
		);
		expectRejected(
			() -> new HostSubtitleS2CPayload(24L, " "),
			"host subtitle must reject blank feedback"
		);
	}

	private static void checkStatefulRequests() {
		StatefulRequest request = request(7L);
		check(
			roundTrip(ConsoleRequestC2SPayload.STREAM_CODEC, new ConsoleRequestC2SPayload(request))
				.request()
				.equals(request),
			"console request codec round-trip failed"
		);
		check(
			roundTrip(
				TargetSnapshotRequestC2SPayload.STREAM_CODEC,
				new TargetSnapshotRequestC2SPayload(request, "start_flow", OPERATION)
			).operationId().equals(OPERATION),
			"target request codec round-trip failed"
		);
		expectRejected(
			() -> new StatefulRequest(
				NetworkProtocol.CURRENT_VERSION,
				0L,
				Optional.of(GAME),
				0L,
				0L,
				Optional.empty(),
				Optional.of(PAGE_INSTANCE)
			),
			"a page instance without a flow instance must be rejected"
		);
	}

	private static void checkConfirmationPayloads() {
		List<UUID> unsorted = List.of(
			UUID.fromString("00000000-0000-0000-0000-000000000202"),
			UUID.fromString("00000000-0000-0000-0000-000000000201")
		);
		PrepareOperationC2SPayload prepare = new PrepareOperationC2SPayload(
			request(8L),
			"start_flow",
			OPERATION,
			unsorted,
			Optional.of("验收")
		);
		PrepareOperationC2SPayload decodedPrepare = roundTrip(
			PrepareOperationC2SPayload.STREAM_CODEC,
			prepare
		);
		check(
			decodedPrepare.equals(prepare)
				&& decodedPrepare.targetIds().getFirst().compareTo(decodedPrepare.targetIds().getLast()) < 0,
			"prepare payload must retain its canonical target ordering"
		);
		expectRejected(
			() -> new PrepareOperationC2SPayload(
				request(9L),
				"start_flow",
				OPERATION,
				List.of(unsorted.getFirst(), unsorted.getFirst()),
				Optional.empty()
			),
			"duplicate confirmation targets must be rejected"
		);

		CommitConfirmationC2SPayload commit = new CommitConfirmationC2SPayload(request(10L), TOKEN);
		check(
			roundTrip(CommitConfirmationC2SPayload.STREAM_CODEC, commit).equals(commit),
			"confirmation commit codec round-trip failed"
		);
		CancelConfirmationC2SPayload cancel = new CancelConfirmationC2SPayload(11L, TOKEN);
		check(
			roundTrip(CancelConfirmationC2SPayload.STREAM_CODEC, cancel).equals(cancel),
			"confirmation cancel codec round-trip failed"
		);

		ConfirmationS2CPayload confirmation = new ConfirmationS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			12L,
			TOKEN,
			"start_flow",
			OPERATION,
			"{\"text\":\"确认\"}",
			List.of("{\"text\":\"目标玩家会进入强制流程\"}"),
			List.of(target(unsorted.getFirst(), "Player")),
			"游戏 pixel_tzz:main / 阶段 setup",
			30_000L,
			4L,
			3L
		);
		check(
			roundTrip(ConfirmationS2CPayload.STREAM_CODEC, confirmation).equals(confirmation),
			"confirmation response codec round-trip failed"
		);
		expectRejected(
			() -> new ConfirmationS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				0L,
				TOKEN,
				"start_flow",
				OPERATION,
				"{}",
				List.of(),
				List.of(),
				"",
				30_001L,
				0L,
				0L
			),
			"empty consequences and overlong token lifetimes must fail closed"
		);

		OperationResultS2CPayload result = new OperationResultS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			13L,
			OperationCode.STATE_REVISION_STALE,
			"状态已变化，请刷新后重试",
			5L,
			3L,
			true
		);
		check(
			roundTrip(OperationResultS2CPayload.STREAM_CODEC, result).equals(result),
			"operation result codec round-trip failed"
		);
	}

	private static void checkConsolePayloads() {
		ConsoleSnapshotS2CPayload payload = new ConsoleSnapshotS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			14L,
			5L,
			3L,
			"ready",
			"",
			Optional.of(GAME),
			Optional.of(Identifier.parse("pixel_tzz:setup")),
			"{\"text\":\"全员逃走中\"}",
			"{\"text\":\"设置\"}",
			List.of(
				new ConsoleSnapshotS2CPayload.PhaseEntry(
					Identifier.parse("pixel_tzz:setup"),
					"{\"text\":\"设置\"}",
					"current"
				),
				new ConsoleSnapshotS2CPayload.PhaseEntry(
					Identifier.parse("pixel_tzz:ready"),
					"{\"text\":\"准备\"}",
					"future"
				)
			),
			true,
			true,
			Optional.of(
				new ConsoleSnapshotS2CPayload.HostInfo(
					UUID.fromString("00000000-0000-0000-0000-000000000301"),
					"Host",
					true
				)
			),
			List.of(
				target(
					UUID.fromString("00000000-0000-0000-0000-000000000302"),
					"Player"
				)
			),
			List.of(
				new ConsoleSnapshotS2CPayload.ActionEntry(
					"start_flow",
					OPERATION,
					"primary",
					10,
					"{\"text\":\"发起初始化\"}",
					"{\"text\":\"短流程验收\"}",
					"#D7B45A",
					true,
					"",
					"multiple",
					1,
					64,
					true
				)
			),
			Optional.of(
				new ConsoleSnapshotS2CPayload.ActiveFlowSummary(
					FLOW_INSTANCE,
					FLOW,
					"{\"text\":\"猎人初始化\"}",
					"active",
					target(
						UUID.fromString("00000000-0000-0000-0000-000000000301"),
						"Host"
					),
					1L,
					0,
					1,
					2L,
					List.of(
						new ConsoleSnapshotS2CPayload.MemberSummary(
							target(
								UUID.fromString("00000000-0000-0000-0000-000000000302"),
								"Player"
							),
							"in_progress",
							"choose_spawn",
							"{\"text\":\"选择出生点\"}",
							List.of(
								new ConsoleSnapshotS2CPayload.MemberFieldSummary(
									Identifier.parse("pixel_tzz:hunter_spawn"),
									"{\"text\":\"猎人出生点\"}",
									"{\"text\":\"北门\"}",
									"held"
								)
							),
							false
						)
					),
					false,
					false
				)
			),
			Optional.empty(),
			2L,
			List.of()
		);
		check(
			roundTrip(ConsoleSnapshotS2CPayload.STREAM_CODEC, payload).equals(payload),
			"console snapshot codec round-trip failed"
		);
	}

	private static void checkTargetPayloads() {
		List<TargetSnapshotS2CPayload.Target> targets = new ArrayList<>();
		for (int index = 0; index < 64; index++) {
			targets.add(
				new TargetSnapshotS2CPayload.Target(
					new UUID(0L, index + 1L),
					"Player" + index,
					true,
					Identifier.parse("pixel_tzz:runner"),
					"{\"text\":\"逃走者\"}",
					"#65D68A",
					Optional.empty(),
					"",
					Identifier.parse("pixel_tzz:alive"),
					"{\"text\":\"存活\"}",
					"not_started",
					List.of(
						new TargetSnapshotS2CPayload.FlowCompletionStatus(
							FLOW,
							"{\"text\":\"通用初始化\"}",
							2,
							index % 2 == 0 ? 2 : 0,
							index % 2 == 0 ? "current" : "incomplete"
						)
					),
					true,
					""
				)
			);
		}
		TargetSnapshotS2CPayload snapshot = new TargetSnapshotS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			15L,
			"start_flow",
			OPERATION,
			5L,
			3L,
			"multiple",
			1,
			64,
			targets.reversed()
		);
		TargetSnapshotS2CPayload decoded = roundTrip(
			TargetSnapshotS2CPayload.STREAM_CODEC,
			snapshot
		);
		check(
			decoded.equals(snapshot)
				&& decoded.targets().size() == 64
				&& decoded.targets().getFirst().playerId().equals(new UUID(0L, 1L)),
			"64-player target snapshot must remain bounded and canonically ordered"
		);
		List<TargetSnapshotS2CPayload.Target> tooMany = new ArrayList<>(targets);
		tooMany.add(targets.getFirst());
		expectRejected(
			() -> new TargetSnapshotS2CPayload(
				NetworkProtocol.CURRENT_VERSION,
				0L,
				"start_flow",
				OPERATION,
				0L,
				0L,
				"multiple",
				1,
				64,
				tooMany
			),
			"65-player target snapshots must be rejected"
		);
	}

	private static TargetSnapshotS2CPayload.Target target(
		final UUID playerId,
		final String name
	) {
		return new TargetSnapshotS2CPayload.Target(
			playerId,
			name,
			true,
			Identifier.parse("pixel_tzz:runner"),
			"{\"text\":\"逃走者\"}",
			"#65D68A",
			Optional.empty(),
			"",
			Identifier.parse("pixel_tzz:alive"),
			"{\"text\":\"存活\"}",
			"none",
			List.of(),
			true,
			""
		);
	}

	private static void checkFlowPayloads() {
		FlowActionC2SPayload action = new FlowActionC2SPayload(
			request(16L),
			FLOW,
			1,
			"confirm",
			4L,
			2L,
			"confirm",
			List.of(
				new FlowActionC2SPayload.FieldValue(
					Identifier.parse("pixel_tzz:route"),
					"single_choice",
					"\"left\""
				)
			)
		);
		check(
			roundTrip(FlowActionC2SPayload.STREAM_CODEC, action).equals(action),
			"flow action codec round-trip failed"
		);
		FlowActionC2SPayload.FieldValue oversized = new FlowActionC2SPayload.FieldValue(
			Identifier.parse("pixel_tzz:route"),
			"string",
			"\"".repeat(FlowActionC2SPayload.MAX_FIELD_JSON_LENGTH)
		);
		expectRejected(
			() -> new FlowActionC2SPayload(
				request(17L),
				FLOW,
				1,
				"confirm",
				0L,
				0L,
				"confirm",
				List.of(oversized)
			),
			"field payloads over 64 KiB must be rejected before transmission"
		);

		ExclusiveChoiceMutationC2SPayload hold = new ExclusiveChoiceMutationC2SPayload(
			request(18L),
			FLOW,
			1,
			"choose_spawn",
			4L,
			2L,
			Identifier.parse("pixel_tzz:hunter_spawn"),
			ExclusiveChoiceMutationC2SPayload.Operation.HOLD,
			Optional.of("north_gate")
		);
		check(
			roundTrip(ExclusiveChoiceMutationC2SPayload.STREAM_CODEC, hold).equals(hold),
			"exclusive-choice hold codec round-trip failed"
		);
		ExclusiveChoiceMutationC2SPayload release = new ExclusiveChoiceMutationC2SPayload(
			request(19L),
			FLOW,
			1,
			"choose_spawn",
			5L,
			3L,
			Identifier.parse("pixel_tzz:hunter_spawn"),
			ExclusiveChoiceMutationC2SPayload.Operation.RELEASE,
			Optional.empty()
		);
		check(
			roundTrip(ExclusiveChoiceMutationC2SPayload.STREAM_CODEC, release).equals(release),
			"exclusive-choice release codec round-trip failed"
		);
		expectRejected(
			() -> new ExclusiveChoiceMutationC2SPayload(
				request(20L),
				FLOW,
				1,
				"choose_spawn",
				5L,
				3L,
				Identifier.parse("pixel_tzz:hunter_spawn"),
				ExclusiveChoiceMutationC2SPayload.Operation.RELEASE,
				Optional.of("north_gate")
			),
			"exclusive-choice release payloads must not carry a value"
		);
		expectRejected(
			() -> new ExclusiveChoiceMutationC2SPayload(
				request(21L),
				FLOW,
				1,
				"choose_spawn",
				5L,
				3L,
				Identifier.parse("pixel_tzz:hunter_spawn"),
				ExclusiveChoiceMutationC2SPayload.Operation.HOLD,
				Optional.empty()
			),
			"exclusive-choice hold payloads must carry exactly one value"
		);
	}

	private static void checkResourceReports() {
		ResourceReportC2SPayload missing = new ResourceReportC2SPayload(
			PAGE_INSTANCE,
			9L,
			Identifier.parse("pixel_tzz:required_page"),
			1,
			List.of(
				new ResourceReportC2SPayload.Entry(
					"texture",
					"pixel_tzz:textures/gui/required.png",
					"/root/content/asset"
				)
			),
			true,
			false,
			Optional.empty()
		);
		check(
			roundTrip(ResourceReportC2SPayload.STREAM_CODEC, missing).equals(missing),
			"required-resource report codec round-trip failed"
		);
		ResourceReportC2SPayload renderFailure = new ResourceReportC2SPayload(
			PAGE_INSTANCE,
			9L,
			Identifier.parse("pixel_tzz:required_page"),
			0,
			List.of(),
			false,
			false,
			Optional.of("layout failed")
		);
		check(
			roundTrip(ResourceReportC2SPayload.STREAM_CODEC, renderFailure)
				.equals(renderFailure),
			"render-failure report codec round-trip failed"
		);
		expectRejected(
			() -> new ResourceReportC2SPayload(
				PAGE_INSTANCE,
				9L,
				Identifier.parse("pixel_tzz:required_page"),
				0,
				List.of(),
				true,
				true,
				Optional.empty()
			),
			"ready resource reports must reject fatal state"
		);
	}

	private static void checkForcedPagePayloads() {
		byte[] pageHash = new byte[PageBundleS2CPayload.HASH_LENGTH];
		byte[] themeHash = new byte[PageBundleS2CPayload.HASH_LENGTH];
		Arrays.fill(pageHash, (byte) 0x11);
		Arrays.fill(themeHash, (byte) 0x22);
		byte[] page = "{}".getBytes(StandardCharsets.UTF_8);
		byte[] theme = "{}".getBytes(StandardCharsets.UTF_8);
		byte[] binding = """
			{"viewer":{},"session":{},"flow":{},"fields":{},"flow_fields":{}}
			""".strip().getBytes(StandardCharsets.UTF_8);
		FlowContext context = new FlowContext(
			FLOW_INSTANCE,
			FLOW,
			1,
			"briefing",
			4L,
			2L,
			18L,
			0,
			1,
			false
		);
		UUID occupantId = UUID.fromString("00000000-0000-0000-0000-000000000104");
		ExclusiveOptionState exclusiveOption = new ExclusiveOptionState(
			"north",
			"{\"text\":\"北侧出生点\"}",
			"{\"text\":\"靠近广场入口\"}",
			Optional.of(Identifier.parse("pixel_tzz:spawn/north")),
			Optional.of(Identifier.parse("pixel_tzz:preview/spawn/north")),
			"locked_by_other",
			Optional.of(occupantId),
			Optional.of("Player B"),
			false
		);
		FieldSchema exclusiveField = new FieldSchema(
			Identifier.parse("pixel_tzz:spawn"),
			"exclusive_choice",
			"出生点",
			"每个出生点只能由一名玩家选择",
			true,
			Optional.empty(),
			OptionalLong.empty(),
			OptionalLong.empty(),
			OptionalInt.empty(),
			OptionalInt.empty(),
			List.of("north"),
			OptionalInt.empty(),
			OptionalInt.empty(),
			List.of(exclusiveOption)
		);
		PageBundleS2CPayload bundle = new PageBundleS2CPayload(
			PAGE_INSTANCE,
			3L,
			5L,
			Identifier.parse("pixel_tzz:acceptance/briefing"),
			Identifier.parse("pixel_tzz:control_console"),
			pageHash,
			page,
			themeHash,
			theme,
			List.of(exclusiveField),
			PagePurpose.FORCED_FLOW,
			Optional.of(context),
			binding
		);
		PageBundleS2CPayload decoded = roundTrip(PageBundleS2CPayload.STREAM_CODEC, bundle);
		check(
			decoded.instanceId().equals(bundle.instanceId())
				&& decoded.purpose() == PagePurpose.FORCED_FLOW
				&& decoded.flowContext().equals(bundle.flowContext())
				&& decoded.fields().equals(List.of(exclusiveField))
				&& Arrays.equals(decoded.pageHash(), pageHash)
				&& Arrays.equals(decoded.bindingJson(), binding),
			"forced page bundle codec round-trip failed"
		);
		expectRejected(
			() -> new PageBundleS2CPayload(
				PAGE_INSTANCE,
				3L,
				5L,
				Identifier.parse("pixel_tzz:acceptance/briefing"),
				Identifier.parse("pixel_tzz:control_console"),
				pageHash,
				page,
				themeHash,
				theme,
				List.of(),
				PagePurpose.FORCED_FLOW,
				Optional.of(
					new FlowContext(
						FLOW_INSTANCE,
						FLOW,
						1,
						"briefing",
						0L,
						0L,
						0L,
						0,
						1,
						true
					)
				),
				binding
			),
			"forced pages must never be client-closable"
		);

		ForcedPageReleaseS2CPayload release = new ForcedPageReleaseS2CPayload(
			NetworkProtocol.CURRENT_VERSION,
			FLOW_INSTANCE,
			PAGE_INSTANCE,
			"completed",
			6L
		);
		check(
			roundTrip(ForcedPageReleaseS2CPayload.STREAM_CODEC, release).equals(release),
			"forced page release codec round-trip failed"
		);
	}

	private static StatefulRequest request(final long sequence) {
		return new StatefulRequest(
			NetworkProtocol.CURRENT_VERSION,
			sequence,
			Optional.of(GAME),
			3L,
			5L,
			Optional.of(FLOW_INSTANCE),
			Optional.of(PAGE_INSTANCE)
		);
	}

	private static <T> T roundTrip(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final T value
	) {
		ByteBuf storage = Unpooled.buffer();
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(storage, RegistryAccess.EMPTY);
			codec.encode(buffer, value);
			check(buffer.readableBytes() > 0, "encoded payload cannot be empty");
			T decoded = codec.decode(buffer);
			check(!buffer.isReadable(), "payload decoder must consume its complete frame");
			return decoded;
		} finally {
			storage.release();
		}
	}

	private static void expectRejected(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException | NullPointerException expected) {
			// Expected trust-boundary rejection.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
