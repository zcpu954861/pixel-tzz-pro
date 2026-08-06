package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.CountdownControlContract;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownCheckpointSoundS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownClearS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownPatchS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownReplaceS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.CountdownResyncRequestC2SPayload;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Protocol-v13 bounded standalone countdown frame and resynchronization check. */
public final class ProtocolV13SelfCheck {
	private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-0000000003c1");
	private static final UUID COUNTDOWN = UUID.fromString("00000000-0000-0000-0000-0000000003c3");
	private static final byte[] REPLACE = (
		"{\"format_version\":1,\"purpose\":\"opening\","
			+ "\"remaining_ticks\":200.0,\"precision\":\"hundredths\"}"
	).getBytes(StandardCharsets.UTF_8);
	private static final byte[] PATCH = (
		"{\"format_version\":1,\"remaining_ticks\":185.5,\"state\":\"running\"}"
	).getBytes(StandardCharsets.UTF_8);

	private ProtocolV13SelfCheck() {
	}

	public static void main(final String[] args) {
		check(NetworkProtocol.CURRENT_VERSION == 13, "V3C requires protocol v13");
		checkRoundTrips();
		checkBounds();
		checkSequenceContracts();
		checkRecoveryOperationIdentity();
		System.out.println("PROTOCOL_V13_SELF_CHECK=PASS");
	}

	private static void checkRecoveryOperationIdentity() {
		check(
			CountdownControlContract.isClearAllStateOperation(
				"clear_all_state",
				Identifier.fromNamespaceAndPath("pixel-tzz-pro", "reset/all")
			),
			"clear-all recovery identity changed"
		);
		check(
			CountdownControlContract.isRevisionStableHostOperation(
				CountdownControlContract.CLEAR_ALL_STATE_OPERATION_TYPE,
				CountdownControlContract.CLEAR_ALL_STATE_OPERATION_ID
			),
			"clear-all review must survive unrelated countdown revision pushes"
		);
		check(
			!CountdownControlContract.isRevisionStableHostOperation(
				"reset_game_progress",
				Identifier.fromNamespaceAndPath("pixel-tzz-pro", "reset/game_progress")
			),
			"ordinary progress reset must retain strict global-revision binding"
		);
	}

	private static void checkRoundTrips() {
		CountdownReplaceS2CPayload replace = new CountdownReplaceS2CPayload(
			GAME,
			3L,
			9L,
			10L,
			11L,
			REPLACE
		);
		CountdownPatchS2CPayload patch = new CountdownPatchS2CPayload(
			GAME,
			3L,
			9L,
			10L,
			11L,
			12L,
			PATCH
		);
		CountdownClearS2CPayload clear = new CountdownClearS2CPayload(
			GAME,
			3L,
			9L,
			10L,
			13L
		);
		CountdownResyncRequestC2SPayload resync = new CountdownResyncRequestC2SPayload(
			GAME,
			3L,
			9L,
			10L,
			12L
		);
		CountdownCheckpointSoundS2CPayload sound = new CountdownCheckpointSoundS2CPayload(
			COUNTDOWN,
			7L,
			"three_seconds",
			Identifier.parse("minecraft:block.note_block.hat"),
			0.75F,
			1.1F
		);

		CountdownReplaceS2CPayload decodedReplace = roundTrip(
			CountdownReplaceS2CPayload.STREAM_CODEC,
			replace
		);
		checkHeader(
			decodedReplace.gameInstanceId(),
			decodedReplace.definitionGeneration(),
			decodedReplace.epoch(),
			decodedReplace.contextVersion(),
			decodedReplace.sequence(),
			3L,
			9L,
			10L,
			11L
		);
		check(Arrays.equals(decodedReplace.snapshotJson(), REPLACE), "Replace document changed");

		CountdownPatchS2CPayload decodedPatch = roundTrip(
			CountdownPatchS2CPayload.STREAM_CODEC,
			patch
		);
		check(decodedPatch.baseSequence() == 11L, "Patch base sequence changed");
		check(Arrays.equals(decodedPatch.patchJson(), PATCH), "Patch document changed");
		check(roundTrip(CountdownClearS2CPayload.STREAM_CODEC, clear).equals(clear), "Clear round-trip failed");
		check(
			roundTrip(CountdownResyncRequestC2SPayload.STREAM_CODEC, resync).equals(resync),
			"Resync round-trip failed"
		);
		check(
			roundTrip(CountdownCheckpointSoundS2CPayload.STREAM_CODEC, sound).equals(sound),
			"Checkpoint sound round-trip failed"
		);
		CountdownCheckpointSoundS2CPayload longestSound = new CountdownCheckpointSoundS2CPayload(
			COUNTDOWN,
			8L,
			"a".repeat(CountdownCheckpointSoundS2CPayload.MAX_CHECKPOINT_ID_LENGTH),
			Identifier.parse("minecraft:block.note_block.hat"),
			1.0F,
			1.0F
		);
		check(
			roundTrip(CountdownCheckpointSoundS2CPayload.STREAM_CODEC, longestSound).equals(longestSound),
			"128-character data-pack checkpoint ID must survive the sound protocol"
		);
	}

	private static void checkBounds() {
		check(
			CountdownCheckpointSoundS2CPayload.MAX_CHECKPOINT_ID_LENGTH == 128
				&& CountdownCheckpointSoundS2CPayload.MAX_CHECKPOINT_ID_LENGTH
					== PersistedCountdown.MAX_LOCAL_ID_LENGTH,
			"checkpoint IDs must share the explicit 128-character data-pack, persistence, and sound-protocol limit"
		);
		check(
			CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES == 65_536,
			"standalone Countdown Replace must retain its dedicated 64KiB hard limit"
		);
		check(
			CountdownPatchS2CPayload.MAX_PATCH_BYTES == 16_384,
			"standalone Countdown Patch must retain its dedicated 16KiB hard limit"
		);
		expectRejected(
			() -> new CountdownReplaceS2CPayload(GAME, 0L, 0L, 0L, 0L, new byte[0]),
			"empty Countdown Replace must be rejected"
		);
		expectRejected(
			() -> new CountdownReplaceS2CPayload(
				GAME,
				0L,
				0L,
				0L,
				0L,
				new byte[CountdownReplaceS2CPayload.MAX_SNAPSHOT_BYTES + 1]
			),
			"oversized Countdown Replace must be rejected"
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
			() -> new CountdownClearS2CPayload(GAME, -1L, 0L, 0L, 0L),
			"negative Countdown metadata must be rejected"
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
			"blank checkpoint ID must be rejected"
		);
		expectRejected(
			() -> new CountdownCheckpointSoundS2CPayload(
				COUNTDOWN,
				0L,
				"a".repeat(CountdownCheckpointSoundS2CPayload.MAX_CHECKPOINT_ID_LENGTH + 1),
				Identifier.parse("minecraft:block.note_block.hat"),
				1.0F,
				1.0F
			),
			"checkpoint ID beyond the persisted 128-character contract must be rejected"
		);
	}

	private static void checkSequenceContracts() {
		expectRejected(
			() -> new CountdownPatchS2CPayload(GAME, 1L, 1L, 1L, 5L, 5L, PATCH),
			"Patch cannot target its own sequence"
		);
		expectRejected(
			() -> new CountdownPatchS2CPayload(GAME, 1L, 1L, 1L, 7L, 6L, PATCH),
			"Patch base cannot be newer than its sequence"
		);
		expectRejected(
			() -> new CountdownResyncRequestC2SPayload(GAME, 1L, 2L, -1L, 4L),
			"Resync cannot carry a negative context version"
		);
	}

	private static void checkHeader(
		final UUID game,
		final long generation,
		final long epoch,
		final long context,
		final long sequence,
		final long expectedGeneration,
		final long expectedEpoch,
		final long expectedContext,
		final long expectedSequence
	) {
		check(
			game.equals(GAME)
				&& generation == expectedGeneration
				&& epoch == expectedEpoch
				&& context == expectedContext
				&& sequence == expectedSequence,
			"Countdown frame header changed during round-trip"
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
