package io.github.zcpu954861.pixeltzzpro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Structural regression gate for the host-only, side-effect-free HUD preview server boundary. */
public final class HudPreviewServerRuntimeContractSelfCheck {
	private static final Path SOURCE = Path.of(
		"src/main/java/io/github/zcpu954861/pixeltzzpro/server/HudPreviewServerRuntime.java"
	);
	private static final Pattern SEND = Pattern.compile("ServerPlayNetworking\\.send\\(");
	private static final Pattern DIRECT_SEND = Pattern.compile(
		"ServerPlayNetworking\\.send\\(\\s*player\\s*,",
		Pattern.MULTILINE
	);

	private HudPreviewServerRuntimeContractSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		String source = Files.readString(resolveSource());
		for (String required : List.of(
			"case OPEN ->",
			"case REFRESH ->",
			"case CLOSE ->",
			"EnumMap<Surface, SurfaceSlot>",
			"SESSION_TTL_TICKS",
			"ClearReason.CLOSED",
			"ClearReason.EXPIRED",
			"ClearReason.REVOKED",
			"ClearReason.REPLACED",
			"playerDisconnected",
			"currentHost(server, player.getUUID())",
			"HudPreviewBindingSourceAccess.create(",
			"DefinitionRegistry.INSTANCE.view()",
			"freezeCountdownClocks(document)"
		)) {
			check(source.contains(required), "missing lifecycle boundary: " + required);
		}

		for (String forbidden : List.of(
			"HudReplaceS2CPayload",
			"CountdownReplaceS2CPayload",
			"PersistedCountdown",
			"CountdownCallbackRunner",
			"MessageServerRuntime",
			"TimelineServerRuntime",
			"countdownCheckpoint(",
			"commitV2(",
			"commitV3(",
			"commitV4(",
			"commitV5(",
			"setDirty(",
			"broadcastAll(",
			"playHook("
		)) {
			check(!source.contains(forbidden), "preview boundary gained forbidden side effect: " + forbidden);
		}

		int sends = matches(SEND, source);
		check(sends > 0, "preview boundary must send isolated projections");
		check(
			sends == matches(DIRECT_SEND, source),
			"every preview response must use the requesting ServerPlayer as its direct recipient"
		);
		System.out.println("HUD_PREVIEW_SERVER_RUNTIME_CONTRACT_SELF_CHECK=PASS");
	}

	private static Path resolveSource() {
		Path current = Path.of("").toAbsolutePath().normalize();
		for (int depth = 0; depth < 8 && current != null; depth++) {
			Path candidate = current.resolve(SOURCE);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("cannot locate " + SOURCE);
	}

	private static int matches(final Pattern pattern, final String source) {
		int count = 0;
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
