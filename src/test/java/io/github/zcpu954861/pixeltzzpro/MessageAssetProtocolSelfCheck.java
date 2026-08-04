package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.network.message.MessageAssetReportVerifier;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageAssetReportVerifier.Verification;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetType;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.Entry;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.MissingMode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload.Resolution;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageAssetAuthoritySelfCheck;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Pure codec and server-verification checks for the V3B resource preflight exchange. */
public final class MessageAssetProtocolSelfCheck {
	private static final Identifier FONT = Identifier.parse("pixel_tzz:display");
	private static final Identifier FONT_FALLBACK = Identifier.parse(
		"minecraft:uniform"
	);
	private static final Identifier SOUND = Identifier.parse(
		"pixel_tzz:message.alert"
	);

	private MessageAssetProtocolSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_ASSET_PROTOCOL_SELF_CHECK=PASS");
	}

	public static void run() {
		Entry font = new Entry(
			AssetType.FONT,
			FONT,
			MissingMode.FALLBACK,
			Optional.of(FONT_FALLBACK),
			true,
			false
		);
		Entry sound = new Entry(
			AssetType.SOUND,
			SOUND,
			MissingMode.BLOCK_START,
			Optional.empty(),
			true,
			true
		);
		MessageAssetManifestS2CPayload manifest =
			new MessageAssetManifestS2CPayload(9L, 4L, List.of(font, sound));
		MessageAssetReportC2SPayload report = new MessageAssetReportC2SPayload(
			1L,
			9L,
			4L,
			List.of(
				new MessageAssetReportC2SPayload.Entry(
					AssetType.FONT,
					FONT,
					Resolution.FALLBACK_USED
				),
				new MessageAssetReportC2SPayload.Entry(
					AssetType.SOUND,
					SOUND,
					Resolution.MISSING
				)
			)
		);

		check(
			roundTrip(MessageAssetManifestS2CPayload.STREAM_CODEC, manifest)
				.equals(manifest),
			"asset manifest round-trip failed"
		);
		check(
			manifest.entries().stream().allMatch(Entry::preload),
			"asset manifest codec must preserve the preload declaration"
		);
		check(
			roundTrip(MessageAssetReportC2SPayload.STREAM_CODEC, report)
				.equals(report),
			"asset report round-trip failed"
		);
		Verification verification = MessageAssetReportVerifier.verify(
			manifest,
			report
		);
		check(
			!verification.startReady()
				&& verification.requiredMissingCount() == 1
				&& verification.missingCount() == 1
				&& verification.fallbackCount() == 1
				&& verification.outcomes().get(0).effectiveId()
					.equals(Optional.of(FONT_FALLBACK)),
			"server verification must derive fallback and start blocking from its manifest"
		);

		expectRejected(
			() -> new MessageAssetManifestS2CPayload(9L, List.of(font, font)),
			"duplicate manifest identities must be rejected"
		);
		expectRejected(
			() -> new MessageAssetReportC2SPayload(
				1L,
				9L,
				List.of(
					new MessageAssetReportC2SPayload.Entry(
						AssetType.FONT,
						FONT,
						Resolution.PRESENT
					),
					new MessageAssetReportC2SPayload.Entry(
						AssetType.FONT,
						FONT,
						Resolution.MISSING
					)
				)
			),
			"duplicate report identities must be rejected"
		);
		expectRejected(
			() -> MessageAssetReportVerifier.verify(
				manifest,
				new MessageAssetReportC2SPayload(
					2L,
					10L,
					4L,
					report.entries()
				)
			),
			"cross-generation reports must be rejected"
		);
		expectRejected(
			() -> MessageAssetReportVerifier.verify(
				manifest,
				new MessageAssetReportC2SPayload(
					2L,
					9L,
					3L,
					report.entries()
				)
			),
			"a report for an older manifest sequence must be rejected"
		);
		expectRejected(
			() -> MessageAssetReportVerifier.verify(
				manifest,
				new MessageAssetReportC2SPayload(
					2L,
					9L,
					4L,
					List.of(report.entries().get(0))
				)
			),
			"partial reports must be rejected"
		);
		expectRejected(
			() -> MessageAssetReportVerifier.verify(
				manifest,
				new MessageAssetReportC2SPayload(
					2L,
					9L,
					4L,
					List.of(
						report.entries().get(0),
						new MessageAssetReportC2SPayload.Entry(
							AssetType.SOUND,
							SOUND,
							Resolution.FALLBACK_USED
						)
					)
				)
			),
			"undeclared fallback use must be rejected"
		);

		List<Entry> tooMany = new ArrayList<>();
		for (
			int index = 0;
			index <= MessageAssetManifestS2CPayload.MAX_ENTRIES;
			index++
		) {
			tooMany.add(
				new Entry(
					AssetType.FONT,
					Identifier.parse("pixel_tzz:test/font_" + index),
					MissingMode.SILENT,
					Optional.empty(),
					false
				)
			);
		}
		expectRejected(
			() -> new MessageAssetManifestS2CPayload(9L, tooMany),
			"asset manifest count must be bounded"
		);

		expectRejected(
			() -> decodePayload(
				MessageAssetManifestS2CPayload.STREAM_CODEC,
				withTrailingDocument(
					encodePayload(
						MessageAssetManifestS2CPayload.STREAM_CODEC,
						manifest
					)
				)
			),
			"asset manifest must reject inner trailing data"
		);
		expectRejected(
			() -> decodePayload(
				MessageAssetReportC2SPayload.STREAM_CODEC,
				withTrailingDocument(
					encodePayload(
						MessageAssetReportC2SPayload.STREAM_CODEC,
						report
					)
				)
			),
			"asset report must reject inner trailing data"
		);
		MessageAssetAuthoritySelfCheck.run();
	}

	private static byte[] withTrailingDocument(final byte[] payload) {
		FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
		try {
			int length = input.readVarInt();
			check(
				length >= 0 && length == input.readableBytes(),
				"test payload must contain one length-delimited document"
			);
			byte[] original = new byte[length];
			input.readBytes(original);
			byte[] tailed = Arrays.copyOf(original, original.length + 1);
			tailed[tailed.length - 1] = 0x5A;
			FriendlyByteBuf output = new FriendlyByteBuf(Unpooled.buffer());
			try {
				output.writeVarInt(tailed.length);
				output.writeBytes(tailed);
				byte[] result = new byte[output.readableBytes()];
				output.getBytes(output.readerIndex(), result);
				return result;
			} finally {
				output.release();
			}
		} finally {
			input.release();
		}
	}

	private static <T> byte[] encodePayload(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final T value
	) {
		ByteBuf storage = Unpooled.buffer();
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				storage,
				RegistryAccess.EMPTY
			);
			codec.encode(buffer, value);
			byte[] result = new byte[buffer.readableBytes()];
			buffer.getBytes(buffer.readerIndex(), result);
			return result;
		} finally {
			storage.release();
		}
	}

	private static <T> T decodePayload(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final byte[] encoded
	) {
		ByteBuf storage = Unpooled.wrappedBuffer(encoded);
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				storage,
				RegistryAccess.EMPTY
			);
			T decoded = codec.decode(buffer);
			check(!buffer.isReadable(), "payload decoder must consume its frame");
			return decoded;
		} finally {
			storage.release();
		}
	}

	private static <T> T roundTrip(
		final StreamCodec<RegistryFriendlyByteBuf, T> codec,
		final T value
	) {
		return decodePayload(codec, encodePayload(codec, value));
	}

	private static void expectRejected(
		final Runnable operation,
		final String message
	) {
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
