package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownCheckpointSoundS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudPreviewRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.HudResyncRequestC2SPayload.Surface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Protocol-v13 bounded HUD/countdown frame and namespace check. */
public final class ProtocolV13SelfCheck {
	private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-0000000003c1");
	private static final UUID PREVIEW = UUID.fromString("00000000-0000-0000-0000-0000000003c2");
	private static final UUID COUNTDOWN = UUID.fromString("00000000-0000-0000-0000-0000000003c3");
	private static final byte[] REPLACE = ("{\"format_version\":1,\"surface\":\"dock\","+
		"\"transition\":{\"enter\":\"fade\",\"change\":\"crossfade_values\","+
		"\"exit\":\"fade\",\"enter_sound\":{\"event\":\"minecraft:block.note_block.pling\","+
		"\"volume\":0.4,\"pitch\":1.1},\"change_sound\":{\"event\":"+
		"\"minecraft:block.note_block.hat\",\"volume\":0.2,\"pitch\":1.0}}}")
		.getBytes(StandardCharsets.UTF_8);
	private static final byte[] PATCH = "{\"title\":{\"text\":\"任务更新\"}}"
		.getBytes(StandardCharsets.UTF_8);

	private ProtocolV13SelfCheck() {
	}

	public static void main(final String[] args) {
		check(NetworkProtocol.CURRENT_VERSION == 13, "V3C requires protocol v13");
		checkRoundTrips();
		checkBounds();
		checkSequenceContracts();
		System.out.println("PROTOCOL_V13_SELF_CHECK=PASS");
	}

	private static void checkRoundTrips() {
		HudReplaceS2CPayload hudReplace = new HudReplaceS2CPayload(GAME, 3L, 4L, 5L, 6L, REPLACE);
		HudPatchS2CPayload hudPatch = new HudPatchS2CPayload(GAME, 3L, 4L, 5L, 6L, 7L, PATCH);
		HudClearS2CPayload hudClear = new HudClearS2CPayload(GAME, 3L, 4L, 5L, 8L);
		CountdownReplaceS2CPayload countdownReplace = new CountdownReplaceS2CPayload(
			GAME, 3L, 9L, 10L, 11L, REPLACE
		);
		CountdownPatchS2CPayload countdownPatch = new CountdownPatchS2CPayload(
			GAME, 3L, 9L, 10L, 11L, 12L, PATCH
		);
		CountdownClearS2CPayload countdownClear = new CountdownClearS2CPayload(
			GAME, 3L, 9L, 10L, 13L
		);
		HudResyncRequestC2SPayload resync = new HudResyncRequestC2SPayload(
			GAME, Surface.COUNTDOWN, 3L, 9L, 10L, 12L
		);
		HudPreviewReplaceS2CPayload preview = new HudPreviewReplaceS2CPayload(
			PREVIEW, HudPreviewContract.Surface.HUD, 2L, 4L, REPLACE
		);
		HudPreviewClearS2CPayload previewClear = new HudPreviewClearS2CPayload(
			PREVIEW,
			HudPreviewContract.Surface.COUNTDOWN,
			2L,
			5L,
			HudPreviewContract.ClearReason.CLOSED
		);
		HudPreviewRequestC2SPayload previewRequest = new HudPreviewRequestC2SPayload(
			NetworkProtocol.CURRENT_VERSION,
			6L,
			PREVIEW,
			HudPreviewContract.Action.OPEN,
			HudPreviewContract.Surface.HUD,
			HudPreviewContract.Scenario.TASK_RUNNING,
			HudPreviewContract.Identity.PARTICIPANT,
			HudPreviewContract.Mode.COMPACT,
			HudPreviewContract.Boundary.LONG_TEXT,
			Optional.of(Identifier.parse("pixel_tzz:running")),
			Optional.of(Identifier.parse("pixel_tzz:branch_task")),
			Optional.of(Identifier.parse("pixel_tzz:runner")),
			Optional.empty(),
			Optional.of(Identifier.parse("pixel_tzz:alive"))
		);
		CountdownCheckpointSoundS2CPayload checkpointSound =
			new CountdownCheckpointSoundS2CPayload(
				COUNTDOWN,
				7L,
				"three_seconds",
				Identifier.parse("minecraft:block.note_block.hat"),
				0.75F,
				1.1F
			);

		HudReplaceS2CPayload decodedHudReplace = roundTrip(HudReplaceS2CPayload.STREAM_CODEC, hudReplace);
		checkHeader(decodedHudReplace.gameInstanceId(), decodedHudReplace.definitionGeneration(), decodedHudReplace.epoch(), decodedHudReplace.contextVersion(), decodedHudReplace.sequence(), GAME, 3L, 4L, 5L, 6L);
		check(Arrays.equals(decodedHudReplace.snapshotJson(), REPLACE), "HUD Replace document changed");
		HudPatchS2CPayload decodedHudPatch = roundTrip(HudPatchS2CPayload.STREAM_CODEC, hudPatch);
		check(decodedHudPatch.baseSequence() == 6L && decodedHudPatch.sequence() == 7L, "HUD Patch base changed");
		check(Arrays.equals(decodedHudPatch.patchJson(), PATCH), "HUD Patch document changed");
		check(roundTrip(HudClearS2CPayload.STREAM_CODEC, hudClear).equals(hudClear), "HUD Clear round-trip failed");

		CountdownReplaceS2CPayload decodedCountdownReplace = roundTrip(CountdownReplaceS2CPayload.STREAM_CODEC, countdownReplace);
		check(Arrays.equals(decodedCountdownReplace.snapshotJson(), REPLACE), "Countdown Replace document changed");
		CountdownPatchS2CPayload decodedCountdownPatch = roundTrip(CountdownPatchS2CPayload.STREAM_CODEC, countdownPatch);
		check(decodedCountdownPatch.baseSequence() == 11L && Arrays.equals(decodedCountdownPatch.patchJson(), PATCH), "Countdown Patch round-trip failed");
		check(roundTrip(CountdownClearS2CPayload.STREAM_CODEC, countdownClear).equals(countdownClear), "Countdown Clear round-trip failed");
		check(roundTrip(HudResyncRequestC2SPayload.STREAM_CODEC, resync).equals(resync), "HUD resync round-trip failed");
		HudPreviewReplaceS2CPayload decodedPreview = roundTrip(HudPreviewReplaceS2CPayload.STREAM_CODEC, preview);
		check(
			decodedPreview.previewId().equals(PREVIEW)
				&& decodedPreview.surface() == HudPreviewContract.Surface.HUD
				&& Arrays.equals(decodedPreview.snapshotJson(), REPLACE),
			"HUD preview round-trip failed"
		);
		check(
			roundTrip(HudPreviewClearS2CPayload.STREAM_CODEC, previewClear).equals(previewClear),
			"HUD preview clear round-trip failed"
		);
		check(
			roundTrip(HudPreviewRequestC2SPayload.STREAM_CODEC, previewRequest).equals(previewRequest),
			"HUD preview request round-trip failed"
		);
		check(
			roundTrip(CountdownCheckpointSoundS2CPayload.STREAM_CODEC, checkpointSound)
				.equals(checkpointSound),
			"Countdown checkpoint sound round-trip failed"
		);
	}

	private static void checkBounds() {
		check(
			HudReplaceS2CPayload.MAX_SNAPSHOT_BYTES == 131_072,
			"HUD Replace hard limit must match the client decoder"
		);
		expectRejected(
			() -> new HudReplaceS2CPayload(GAME, 0L, 0L, 0L, 0L, new byte[0]),
			"empty HUD Replace must be rejected"
		);
		expectRejected(
			() -> new HudReplaceS2CPayload(
				GAME,
				0L,
				0L,
				0L,
				0L,
				new byte[HudReplaceS2CPayload.MAX_SNAPSHOT_BYTES + 1]
			),
			"oversized HUD Replace must be rejected"
		);
		expectRejected(
			() -> new CountdownPatchS2CPayload(
				GAME,
				0L,
				0L,
				0L,
				0L,
				1L,
				new byte[CountdownPatchS2CPayload.MAX_PATCH_BYTES + 1]
			),
			"oversized Countdown Patch must be rejected"
		);
		expectRejected(
			() -> new HudClearS2CPayload(GAME, -1L, 0L, 0L, 0L),
			"negative HUD metadata must be rejected"
		);
		expectRejected(
			() -> new CountdownCheckpointSoundS2CPayload(
				COUNTDOWN,
				0L,
				"",
				Identifier.parse("minecraft:block.note_block.hat"),
				1.0F,
				1.0F
			),
			"blank Countdown checkpoint ID must be rejected"
		);
		expectRejected(
			() -> new HudPreviewRequestC2SPayload(
				NetworkProtocol.CURRENT_VERSION,
				1L,
				PREVIEW,
				HudPreviewContract.Action.OPEN,
				HudPreviewContract.Surface.HUD,
				HudPreviewContract.Scenario.COUNTDOWN_START,
				HudPreviewContract.Identity.HOST,
				HudPreviewContract.Mode.NORMAL,
				HudPreviewContract.Boundary.NORMAL,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			),
			"HUD preview must reject countdown-only scenarios"
		);
		expectRejected(
			() -> new HudPreviewRequestC2SPayload(
				NetworkProtocol.CURRENT_VERSION,
				1L,
				PREVIEW,
				HudPreviewContract.Action.OPEN,
				HudPreviewContract.Surface.COUNTDOWN,
				HudPreviewContract.Scenario.COUNTDOWN_START,
				HudPreviewContract.Identity.HOST,
				HudPreviewContract.Mode.NORMAL,
				HudPreviewContract.Boundary.NORMAL,
				Optional.empty(),
				Optional.of(Identifier.parse("pixel_tzz:task")),
				Optional.empty(),
				Optional.empty(),
				Optional.empty()
			),
			"Countdown preview must reject task overrides"
		);
	}

	private static void checkSequenceContracts() {
		expectRejected(
			() -> new HudPatchS2CPayload(GAME, 1L, 1L, 1L, 5L, 5L, PATCH),
			"HUD Patch cannot target its own sequence"
		);
		expectRejected(
			() -> new CountdownPatchS2CPayload(GAME, 1L, 1L, 1L, 7L, 6L, PATCH),
			"Countdown Patch base cannot be newer than its sequence"
		);
		HudResyncRequestC2SPayload hud = new HudResyncRequestC2SPayload(
			GAME, Surface.HUD, 1L, 2L, 3L, 4L
		);
		HudResyncRequestC2SPayload countdown = new HudResyncRequestC2SPayload(
			GAME, Surface.COUNTDOWN, 1L, 2L, 3L, 4L
		);
		check(hud.surface() != countdown.surface(), "resync namespaces must remain distinct");
	}

	private static void checkHeader(
		final UUID game,
		final long generation,
		final long epoch,
		final long context,
		final long sequence,
		final UUID expectedGame,
		final long expectedGeneration,
		final long expectedEpoch,
		final long expectedContext,
		final long expectedSequence
	) {
		check(
			game.equals(expectedGame)
				&& generation == expectedGeneration
				&& epoch == expectedEpoch
				&& context == expectedContext
				&& sequence == expectedSequence,
			"HUD frame header changed during round-trip"
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
			T decoded = codec.decode(buffer);
			check(!buffer.isReadable(), "payload decoder left trailing bytes");
			return decoded;
		} finally {
			storage.release();
		}
	}

	private static void expectRejected(final Runnable operation, final String message) {
		try {
			operation.run();
			throw new AssertionError(message);
		} catch (RuntimeException expected) {
			// Expected trust-boundary rejection.
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
