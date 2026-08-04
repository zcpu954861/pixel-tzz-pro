package io.github.zcpu954861.pixeltzzpro.server.message;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AttachmentMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DimensionExitMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ReloadMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ResourceReloadMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RespawnMode;
import io.github.zcpu954861.pixeltzzpro.message.MessagePresentationLifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLifecyclePolicyRouter.Directive;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageLifecyclePolicyRouter.Event;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pure-JVM matrix for distinct reload, respawn, and dimension lifecycle routing. */
public final class MessageLifecyclePolicyRouterSelfCheck {
	private MessageLifecyclePolicyRouterSelfCheck() {
	}

	public static void main(final String[] args) {
		run();
		System.out.println("MESSAGE_LIFECYCLE_POLICY_ROUTER_SELF_CHECK=PASS");
	}

	public static void run() {
		checkPresentationDrainLifecycle();
		checkClientLifecycleAuthorityBoundary();
		check(
			List.of(Event.values()).equals(
				List.of(Event.DATA_PACK_RELOAD, Event.RESPAWN, Event.DIMENSION_EXIT)
			),
			"the server lifecycle router must not accept client resource reload as authority"
		);

		for (ReloadMode mode : ReloadMode.values()) {
			LifecyclePolicy policy = policy(
				mode,
				ResourceReloadMode.CANCEL,
				RespawnMode.CANCEL,
				AttachmentMode.WORLD,
				DimensionExitMode.CANCEL
			);
			Directive expected = switch (mode) {
				case FINISH_SNAPSHOT -> Directive.CONTINUE;
				case FINALIZE -> Directive.FINALIZE;
				case CANCEL -> Directive.CANCEL;
			};
			check(
				MessageLifecyclePolicyRouter.resolve(
					policy,
					Event.DATA_PACK_RELOAD,
					true
				) == expected,
				"data-pack reload must use only lifecycle.reload: " + mode
			);
		}

		for (RespawnMode mode : RespawnMode.values()) {
			LifecyclePolicy policy = policy(
				ReloadMode.CANCEL,
				ResourceReloadMode.CANCEL,
				mode,
				AttachmentMode.WORLD,
				DimensionExitMode.CANCEL
			);
			Directive expected = switch (mode) {
				case CONTINUE -> Directive.CONTINUE;
				case FINALIZE -> Directive.FINALIZE;
				case CANCEL -> Directive.CANCEL;
			};
			check(
				MessageLifecyclePolicyRouter.resolve(policy, Event.RESPAWN, false)
					== expected,
				"respawn must use lifecycle.respawn: " + mode
			);
		}

		for (DimensionExitMode mode : DimensionExitMode.values()) {
			Directive expected = switch (mode) {
				case CONTINUE -> Directive.CONTINUE;
				case FINALIZE -> Directive.FINALIZE;
				case CANCEL -> Directive.CANCEL;
			};
			LifecyclePolicy world = policy(
				ReloadMode.CANCEL,
				ResourceReloadMode.CANCEL,
				RespawnMode.CANCEL,
				AttachmentMode.WORLD,
				mode
			);
			check(
				MessageLifecyclePolicyRouter.resolve(world, Event.DIMENSION_EXIT, true)
					== expected,
				"world attachment must apply dimension_exit after leaving origin: " + mode
			);
			check(
				MessageLifecyclePolicyRouter.resolve(world, Event.DIMENSION_EXIT, false)
					== Directive.CONTINUE,
				"world attachment must ignore unrelated dimension changes"
			);

			LifecyclePolicy player = policy(
				ReloadMode.CANCEL,
				ResourceReloadMode.CANCEL,
				RespawnMode.CANCEL,
				AttachmentMode.PLAYER,
				mode
			);
			check(
				MessageLifecyclePolicyRouter.resolve(player, Event.DIMENSION_EXIT, true)
					== Directive.CONTINUE,
				"player attachment must follow its recipient across dimensions"
			);
		}
	}

	private static void checkPresentationDrainLifecycle() {
		for (MessagePlaybackPlan.Respawn mode : MessagePlaybackPlan.Respawn.values()) {
			MessagePresentationLifecyclePolicy.Directive expected = switch (mode) {
				case CONTINUE -> MessagePresentationLifecyclePolicy.Directive.CONTINUE;
				case FINALIZE -> MessagePresentationLifecyclePolicy.Directive.FINALIZE;
				case CANCEL -> MessagePresentationLifecyclePolicy.Directive.CANCEL;
			};
			check(
				MessagePresentationLifecyclePolicy.respawn(false, mode)
					== MessagePresentationLifecyclePolicy.Directive.WAIT_FOR_SERVER,
				"an active network presentation must wait for the server on respawn"
			);
			check(
				MessagePresentationLifecyclePolicy.respawn(true, mode) == expected,
				"a locally owned presentation must apply respawn=" + mode
			);
		}

		for (
			MessagePlaybackPlan.DimensionExit mode
				: MessagePlaybackPlan.DimensionExit.values()
		) {
			MessagePresentationLifecyclePolicy.Directive expected = switch (mode) {
				case CONTINUE -> MessagePresentationLifecyclePolicy.Directive.CONTINUE;
				case FINALIZE -> MessagePresentationLifecyclePolicy.Directive.FINALIZE;
				case CANCEL -> MessagePresentationLifecyclePolicy.Directive.CANCEL;
			};
			check(
				MessagePresentationLifecyclePolicy.dimensionExit(false, true, mode)
					== MessagePresentationLifecyclePolicy.Directive.WAIT_FOR_SERVER,
				"an active network presentation must wait for the server on dimension exit"
			);
			check(
				MessagePresentationLifecyclePolicy.dimensionExit(true, false, mode)
					== MessagePresentationLifecyclePolicy.Directive.CONTINUE,
				"entering origin or moving between non-origin dimensions must not trigger"
			);
			check(
				MessagePresentationLifecyclePolicy.dimensionExit(true, true, mode) == expected,
				"a locally owned world presentation must apply dimension_exit=" + mode
			);
		}
	}

	private static void checkClientLifecycleAuthorityBoundary() {
		String source;
		String trackerMixin;
		String compiler;
		try {
			source = Files.readString(
				Path.of(
					"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/ClientMessagePlayback.java"
				)
			);
			trackerMixin = Files.readString(
				Path.of(
					"src/client/java/io/github/zcpu954861/pixeltzzpro/mixin/ClientPacketListenerLifecycleMixin.java"
				)
			);
			compiler = Files.readString(
				Path.of(
					"src/main/java/io/github/zcpu954861/pixeltzzpro/server/MessageProjectionCompiler.java"
				)
			);
		} catch (IOException error) {
			throw new AssertionError("could not inspect the split client lifecycle source", error);
		}
		int start = source.indexOf("private static void applyLifecyclePolicies");
		int end = source.indexOf("private static Identifier currentDimension", start);
		check(start >= 0 && end > start, "client lifecycle boundary is missing");
		String lifecycle = source.substring(start, end).replaceAll("\\s+", " ");
		check(
			lifecycle.contains(
				"boolean locallyOwned = instance.localBuiltin || instance.draining"
			),
			"only built-in or FINISH-draining cues may own local lifecycle decisions"
		);
		check(
			lifecycle.contains("ClientMessageLifecycleTracker.after(instance.lifecycleSequence)"),
			"each instance must consume only lifecycle events newer than its creation cursor"
		);
		check(
			trackerMixin.contains(
				"packet.dataToKeep() == ClientboundRespawnPacket.KEEP_ALL_DATA"
			)
				&& trackerMixin.contains("Reason.DIMENSION_TRANSFER")
				&& trackerMixin.contains("Reason.RESPAWN"),
			"the client must distinguish vanilla dimension transfer from real respawn"
		);
		check(
			compiler.contains("AttachmentMode.PLAYER")
				&& compiler.contains("MessagePlaybackPlan.DimensionExit.CONTINUE"),
			"player attachment must project an effective CONTINUE dimension policy"
		);

		int finishStart = source.indexOf("private static void finishInstance");
		int cancelStart = source.indexOf("private static void cancelInstance", finishStart);
		int retireStart = source.indexOf("private static void removeCompletedInstances", cancelStart);
		check(
			finishStart >= 0 && cancelStart > finishStart && retireStart > cancelStart,
			"client terminal state methods are missing"
		);
		String finish = source.substring(finishStart, cancelStart);
		String cancel = source.substring(cancelStart, retireStart);
		String retire = source.substring(retireStart);
		check(
			finish.contains("instance.draining = true")
				&& !finish.contains("clearQueued(instance"),
			"FINISH must retain already-authorized queued presentation"
		);
		check(
			cancel.contains("instance.terminal = true")
				&& cancel.contains("clearQueued(instance, true)"),
			"CANCEL must immediately retire active and queued presentation"
		);
		check(
			retire.contains("instance.draining && allComplete"),
			"a FINISH-draining instance may retire only after every node completes"
		);
	}

	private static LifecyclePolicy policy(
		final ReloadMode reload,
		final ResourceReloadMode resourceReload,
		final RespawnMode respawn,
		final AttachmentMode attachment,
		final DimensionExitMode dimensionExit
	) {
		LifecyclePolicy standard = LifecyclePolicy.standard();
		return new LifecyclePolicy(
			standard.screenStart(),
			standard.obscured(),
			standard.screenWaitTimeout(),
			standard.screenTimeoutAction(),
			standard.maxPause(),
			reload,
			standard.restart(),
			standard.externalConflict(),
			standard.reconnect(),
			standard.lateJoin(),
			resourceReload,
			respawn,
			attachment,
			dimensionExit
		);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
