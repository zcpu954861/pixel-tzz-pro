package io.github.zcpu954861.pixeltzzpro.network.message;

import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.AssetKey;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.Entry;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetManifestS2CPayload.MissingMode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageAssetReportC2SPayload.Resolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Stateless server-side trust-boundary check for a client message-asset report.
 *
 * <p>Callers retain the expected manifest per connection, reject a report sequence that is not
 * newer than the last accepted sequence, then call {@link #verify}. The returned start gate is
 * derived exclusively from the server-issued manifest; client-echoed policy bits do not exist.
 */
public final class MessageAssetReportVerifier {
	private MessageAssetReportVerifier() {
	}

	public static Verification verify(
		final MessageAssetManifestS2CPayload manifest,
		final MessageAssetReportC2SPayload report
	) {
		Objects.requireNonNull(manifest, "manifest");
		Objects.requireNonNull(report, "report");
		if (report.generation() != manifest.generation()) {
			throw new IllegalArgumentException(
				"message asset report generation does not match its manifest"
			);
		}
		if (report.manifestSequence() != manifest.manifestSequence()) {
			throw new IllegalArgumentException(
				"message asset report sequence does not match its manifest"
			);
		}

		Map<AssetKey, MessageAssetReportC2SPayload.Entry> reported =
			new LinkedHashMap<>();
		for (MessageAssetReportC2SPayload.Entry entry : report.entries()) {
			reported.put(entry.key(), entry);
		}
		if (reported.size() != manifest.entries().size()) {
			throw new IllegalArgumentException(
				"message asset report must cover the complete manifest"
			);
		}

		List<Outcome> outcomes = new ArrayList<>(manifest.entries().size());
		int missingCount = 0;
		int fallbackCount = 0;
		int requiredMissingCount = 0;
		for (Entry expected : manifest.entries()) {
			MessageAssetReportC2SPayload.Entry observed = reported.remove(
				expected.key()
			);
			if (observed == null) {
				throw new IllegalArgumentException(
					"message asset report omitted " + expected.type() + ":" + expected.id()
				);
			}
			if (
				observed.resolution() == Resolution.FALLBACK_USED
					&& expected.missing() != MissingMode.FALLBACK
			) {
				throw new IllegalArgumentException(
					"client reported an undeclared fallback for " + expected.id()
				);
			}
			Optional<Identifier> effective = switch (observed.resolution()) {
				case PRESENT -> Optional.of(expected.id());
				case FALLBACK_USED -> expected.fallback();
				case MISSING -> Optional.empty();
			};
			if (observed.resolution() == Resolution.MISSING) {
				missingCount++;
				if (expected.requiredForStart()) {
					requiredMissingCount++;
				}
			} else if (observed.resolution() == Resolution.FALLBACK_USED) {
				fallbackCount++;
			}
			outcomes.add(new Outcome(expected, observed.resolution(), effective));
		}
		if (!reported.isEmpty()) {
			throw new IllegalArgumentException(
				"message asset report contains assets outside its manifest"
			);
		}
		return new Verification(
			requiredMissingCount == 0,
			missingCount,
			fallbackCount,
			requiredMissingCount,
			outcomes
		);
	}

	public record Outcome(
		Entry expected,
		Resolution resolution,
		Optional<Identifier> effectiveId
	) {
		public Outcome {
			expected = Objects.requireNonNull(expected, "expected");
			resolution = Objects.requireNonNull(resolution, "resolution");
			effectiveId = Objects.requireNonNull(effectiveId, "effectiveId");
		}
	}

	public record Verification(
		boolean startReady,
		int missingCount,
		int fallbackCount,
		int requiredMissingCount,
		List<Outcome> outcomes
	) {
		public Verification {
			if (
				missingCount < 0
					|| fallbackCount < 0
					|| requiredMissingCount < 0
					|| requiredMissingCount > missingCount
			) {
				throw new IllegalArgumentException("invalid message asset report counts");
			}
			outcomes = List.copyOf(outcomes);
			if (startReady != (requiredMissingCount == 0)) {
				throw new IllegalArgumentException(
					"startReady must reflect requiredMissingCount"
				);
			}
		}
	}
}
