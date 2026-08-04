package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextPolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ContextRecheck;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLiveContextAuthority.CurrentContext;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLiveContextAuthority.Directive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/** Pure-JVM matrix and runtime wiring audit for live-strict game context rechecks. */
public final class MessageLiveContextAuthoritySelfCheck {
	private static final Identifier GAME = id("main");
	private static final Identifier OTHER_GAME = id("other_game");
	private static final Identifier PHASE = id("phase");
	private static final Identifier OTHER_PHASE = id("other_phase");
	private static final Identifier TASK = id("task");
	private static final Identifier OTHER_TASK = id("other_task");

	private MessageLiveContextAuthoritySelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_LIVE_CONTEXT_AUTHORITY_SELF_CHECK=PASS");
	}

	public static void run() {
		CurrentContext matching = new CurrentContext(
			Optional.of(GAME),
			Optional.of(PHASE),
			Optional.of(TASK)
		);
		ContextPolicy strict = new ContextPolicy(
			Set.of(GAME),
			Set.of(PHASE),
			Set.of(TASK),
			ContextRecheck.LIVE_STRICT
		);
		check(
			resolve(Optional.of(GAME), strict, false, matching) == Directive.CONTINUE,
			"matching live-strict context must continue"
		);
		check(
			resolve(Optional.of(OTHER_GAME), strict, false, matching) == Directive.CANCEL,
			"cue game mismatch must cancel"
		);
		check(
			resolve(
				Optional.empty(),
				strict,
				false,
				new CurrentContext(
					Optional.of(GAME),
					Optional.of(OTHER_PHASE),
					Optional.of(TASK)
				)
			) == Directive.CANCEL,
			"phase mismatch must cancel"
		);
		check(
			resolve(
				Optional.empty(),
				strict,
				false,
				new CurrentContext(
					Optional.of(GAME),
					Optional.of(PHASE),
					Optional.of(OTHER_TASK)
				)
			) == Directive.CANCEL,
			"task mismatch must cancel"
		);
		check(
			resolve(Optional.of(GAME), strict, false, CurrentContext.empty())
				== Directive.CANCEL,
			"missing active world context must fail closed when constraints exist"
		);
		check(
			resolve(Optional.of(OTHER_GAME), strict, true, CurrentContext.empty())
				== Directive.CONTINUE,
			"authorized frozen context bypass must remain unaffected"
		);
		check(
			resolve(
				Optional.of(OTHER_GAME),
				new ContextPolicy(
					Set.of(GAME),
					Set.of(PHASE),
					Set.of(TASK),
					ContextRecheck.BEFORE_START
				),
				false,
				matching
			) == Directive.CONTINUE,
			"before_start policy must not be promoted to a live cancellation"
		);
		check(
			resolve(
				Optional.empty(),
				new ContextPolicy(
					Set.of(),
					Set.of(),
					Set.of(),
					ContextRecheck.LIVE_STRICT
				),
				false,
				CurrentContext.empty()
			) == Directive.CONTINUE,
			"an unconstrained strict policy has no context to invalidate"
		);
		checkRuntimeWiring();
	}

	private static Directive resolve(
		final Optional<Identifier> cueGame,
		final ContextPolicy policy,
		final boolean bypass,
		final CurrentContext context
	) {
		return MessageLiveContextAuthority.resolve(
			cueGame,
			policy,
			bypass,
			context
		);
	}

	private static void checkRuntimeWiring() {
		String source;
		try {
			source = Files.readString(
				Path.of(
					"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageServerRuntime.java"
				)
			);
		} catch (IOException error) {
			throw new AssertionError("could not inspect the message runtime", error);
		}
		int tickStart = source.indexOf("private static void onServerTick");
		int tickEnd = source.indexOf(
			"private static void pruneInvalidQueuedInvocations",
			tickStart
		);
		check(tickStart >= 0 && tickEnd > tickStart, "server tick boundary is missing");
		String tick = source.substring(tickStart, tickEnd);
		check(
			tick.indexOf("reauthorizeLiveInstances(server, clock)")
					< tick.indexOf("advanceRecipientSchedules(server, clock)"),
			"live context must be rechecked before any due recipient projection"
		);

		int liveStart = source.indexOf("private static void reauthorizeLiveInstances");
		int liveEnd = source.indexOf("private static void applyReauthorization", liveStart);
		check(liveStart >= 0 && liveEnd > liveStart, "live reauthorization boundary is missing");
		String live = source.substring(liveStart, liveEnd).replaceAll("\\s+", " ");
		check(
			live.contains("MessageLiveContextAuthority.resolve(")
				&& live.contains("ControlAction.CANCEL"),
			"runtime must turn a failed live context recheck into authoritative cancellation"
		);

		int controlStart = source.indexOf("private static void sendControl");
		int controlEnd = source.indexOf(
			"private static List<FieldValue> unresolvedFinalValues",
			controlStart
		);
		check(controlStart >= 0 && controlEnd > controlStart, "control projection boundary is missing");
		String control = source.substring(controlStart, controlEnd).replaceAll("\\s+", " ");
		check(
			control.contains("if (stream.plan == null || !stream.planSent)")
				&& control.contains("stream.terminal = true"),
			"plan-less or connection-less strict streams must become terminal before they can leak later content"
		);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("pixel_tzz", path);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
