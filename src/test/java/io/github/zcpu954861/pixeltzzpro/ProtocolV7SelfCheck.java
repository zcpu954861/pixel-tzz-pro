package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.network.payload.CancelConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CommitConfirmationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConfirmationS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ConsoleSnapshotS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.FlowActionC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ForcedPageReleaseS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HostUiStateC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.OperationResultS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FlowContext;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.PagePurpose;
import io.github.zcpu954861.pixeltzzpro.network.payload.PrepareOperationC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.ResourceReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.StatefulRequest;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Small protocol-v7 codec and trust-boundary check without a test framework.
 */
public final class ProtocolV7SelfCheck {
	private static final UUID FLOW_INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID PAGE_INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000103");
	private static final Identifier GAME = Identifier.parse("pixel_tzz:main");
	private static final Identifier OPERATION = Identifier.parse("pixel_tzz:test_operation");
	private static final Identifier FLOW = Identifier.parse("pixel_tzz:test_flow");

	private ProtocolV7SelfCheck() {
	}

	public static void main(final String[] args) {
		check(NetworkProtocol.CURRENT_VERSION == 7, "2C requires protocol v7");
		checkStatefulRequests();
		checkConfirmationPayloads();
		checkConsolePayloads();
		checkTargetPayloads();
		checkFlowPayloads();
		checkResourceReports();
		checkForcedPagePayloads();
		checkHostUiPayload();
		System.out.println("PROTOCOL_V7_SELF_CHECK=PASS");
	}

	private static void checkHostUiPayload() {
		HostUiStateC2SPayload visible = new HostUiStateC2SPayload(true);
		check(
			roundTrip(HostUiStateC2SPayload.STREAM_CODEC, visible).equals(visible),
			"host UI state codec round-trip failed"
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
			Optional.empty(),
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
			List.of(),
			PagePurpose.FORCED_FLOW,
			Optional.of(context),
			binding
		);
		PageBundleS2CPayload decoded = roundTrip(PageBundleS2CPayload.STREAM_CODEC, bundle);
		check(
			decoded.instanceId().equals(bundle.instanceId())
				&& decoded.purpose() == PagePurpose.FORCED_FLOW
				&& decoded.flowContext().equals(bundle.flowContext())
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
