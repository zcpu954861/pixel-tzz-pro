package io.github.zcpu954861.pixeltzzpro.client.message;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline;
import io.github.zcpu954861.pixeltzzpro.message.MessageExternalContentPolicy;
import io.github.zcpu954861.pixeltzzpro.message.MessagePlaybackSeekPolicy;
import io.github.zcpu954861.pixeltzzpro.message.MessagePresentationLifecyclePolicy;
import io.github.zcpu954861.pixeltzzpro.message.MessagePresentationLifecyclePolicy.Directive;
import io.github.zcpu954861.pixeltzzpro.message.MessageFrameTimeline.SegmentSpan;
import io.github.zcpu954861.pixeltzzpro.message.StyledMessageEffects;
import io.github.zcpu954861.pixeltzzpro.message.StyledMessageEffects.VisualFrame;
import io.github.zcpu954861.pixeltzzpro.message.StyledTypewriter;
import io.github.zcpu954861.pixeltzzpro.client.message.ChatLineProjectionAccess.MutationResult;
import io.github.zcpu954861.pixeltzzpro.mixin.HudAccessor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Channel;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.CharacterSound;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.Conflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ExternalConflict;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ScreenStart;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ScreenTimeoutAction;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.ControlEvent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.FieldDeltaEvent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessageWireInbox.NodeAppendEvent;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageControlS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageCapabilitiesC2SPayload.ChatUpdateMode;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageFieldDeltaS2CPayload.FieldValue;
import io.github.zcpu954861.pixeltzzpro.network.payload.MessageScreenStateC2SPayload.Surface;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * One client timeline for V3B Chat, Title, Subtitle, ActionBar, and sound nodes.
 *
 * <p>The service consumes only the already-authorized wire plan. It never evaluates a condition,
 * resolves an audience, or acknowledges completion to the server. Frames are sought directly from
 * elapsed time, so low FPS cannot make an animation or an authoritative callback run late.</p>
 */
public final class ClientMessagePlayback {
	private static final long NANOS_PER_GAME_TICK = 50_000_000L;
	/*
	 * A newly received live plan can be one or two client ticks late without being a reconnect.
	 * Keep that small transport allowance, but never replay a sound whose authoritative point is
	 * already clearly in the past. Text still seeks to its correct frame.
	 */
	private static final int MAX_ACTIVE_TEXT_NODES = 256;
	private static final int MAX_TRACKED_STATIC_CHAT = 512;
	private static final int MAX_PENDING_BUILTIN_PLANS = 16;
	private static final Map<UUID, InstancePlayback> INSTANCES = new LinkedHashMap<>();
	private static final ArrayDeque<MessagePlaybackPlan> BUILTIN_PLANS =
		new ArrayDeque<>();
	private static final List<PlayingText> CHAT = new ArrayList<>();
	private static final EnumMap<Channel, PlayingText> EXCLUSIVE = new EnumMap<>(Channel.class);
	private static final EnumMap<Channel, ArrayDeque<PendingText>> QUEUED =
		new EnumMap<>(Channel.class);
	private static final EnumMap<Channel, StaticHudTail> STATIC_HUD_TAILS =
		new EnumMap<>(Channel.class);
	private static final Map<StaticChatKey, DynamicChatEntry> STATIC_CHAT =
		new LinkedHashMap<>();

	static {
		for (Channel channel : Channel.values()) {
			QUEUED.put(channel, new ArrayDeque<>());
		}
	}

	private ClientMessagePlayback() {
	}

	public static void reset(final Minecraft client) {
		if (client != null) {
			for (PlayingText playing : List.copyOf(CHAT)) {
				playing.refreshFields(true);
				finishChatEntry(client, playing, true);
			}
		}
		MessageHudOverlay.reset();
		ClientMessageLifecycleTracker.reset();
		INSTANCES.clear();
		CHAT.clear();
		EXCLUSIVE.clear();
		STATIC_CHAT.clear();
		STATIC_HUD_TAILS.clear();
		synchronized (BUILTIN_PLANS) {
			BUILTIN_PLANS.clear();
		}
		QUEUED.values().forEach(ArrayDeque::clear);
	}

	static void offerBuiltinPlan(final MessagePlaybackPlan plan) {
		MessagePlaybackPlan offered = Objects.requireNonNull(plan, "plan");
		if (
			offered.disposition() != PlanDisposition.CREATE
				|| offered.nodes()
					.stream()
					.filter(ResolvedTextNode.class::isInstance)
					.map(ResolvedTextNode.class::cast)
					.flatMap(node -> node.segments().stream())
					.anyMatch(FieldSlotSegment.class::isInstance)
		) {
			throw new IllegalArgumentException(
				"a built-in client cue must be a new, fully static presentation"
			);
		}
		synchronized (BUILTIN_PLANS) {
			if (BUILTIN_PLANS.size() >= MAX_PENDING_BUILTIN_PLANS) {
				BUILTIN_PLANS.removeFirst();
			}
			BUILTIN_PLANS.addLast(offered);
		}
	}

	/**
	 * Reveals the final text of every currently authorized, locally completable cue.
	 *
	 * <p>This is deliberately presentation-only: it sends no packet, retires no server instance,
	 * and does not move the authoritative callback clock. Nodes that have not reached their
	 * scheduled start still begin on time, but begin with their final text already revealed.</p>
	 */
	public static void manualComplete() {
		for (InstancePlayback instance : INSTANCES.values()) {
			if (
				!instance.terminal
					&& instance.plan.policies().allowManualComplete()
			) {
				instance.manualCompleteRequested = true;
			}
		}
	}

	public static void tick(final Minecraft client) {
		Objects.requireNonNull(client, "client");
		if (client.player == null || client.level == null) {
			return;
		}
		tickStaticHudTails(client);
		ingestPlans(client);
		ingestBuiltinPlans(client);
		ingestEvents(client);
		applyLifecyclePolicies(client);
		scheduleDueNodes(client);
		tickChat(client);
		tickExclusive(client);
		startQueues(client);
		removeCompletedInstances();
	}

	private static void ingestPlans(final Minecraft client) {
		MessageWireInbox inbox = ClientMessageWireState.inbox();
		for (UUID instanceId : inbox.pendingPlanIds()) {
			Optional<MessagePlaybackPlan> offered = inbox.takePlan(instanceId);
			if (offered.isEmpty()) {
				continue;
			}
			MessagePlaybackPlan plan = offered.orElseThrow();
			if (plan.disposition() == PlanDisposition.REFRESH) {
				Set<Integer> retainedChatOrdinals = chatOrdinals(plan);
				removeStaticChatEntriesExcept(
					client,
					plan.instanceId(),
					retainedChatOrdinals
				);
				InstancePlayback previous = INSTANCES.remove(plan.instanceId());
				if (previous != null) {
					retireForRefresh(client, previous, retainedChatOrdinals);
				}
			} else if (plan.disposition() == PlanDisposition.REPLACE) {
				UUID replacedId = plan.replacedInstanceId().orElseThrow();
				InstancePlayback replaced = INSTANCES.remove(replacedId);
				if (replaced != null) {
					cancelInstance(client, replaced);
				}
			}
			INSTANCES.put(
				plan.instanceId(),
				new InstancePlayback(client, plan, false)
			);
		}
	}

	private static void ingestBuiltinPlans(final Minecraft client) {
		while (true) {
			MessagePlaybackPlan plan;
			synchronized (BUILTIN_PLANS) {
				plan = BUILTIN_PLANS.pollFirst();
			}
			if (plan == null) {
				return;
			}
			InstancePlayback previous = INSTANCES.put(
				plan.instanceId(),
				new InstancePlayback(client, plan, true)
			);
			if (previous != null) {
				cancelInstance(client, previous);
			}
		}
	}

	private static void ingestEvents(final Minecraft client) {
		MessageWireInbox inbox = ClientMessageWireState.inbox();
		for (InstancePlayback instance : List.copyOf(INSTANCES.values())) {
			for (MessageWireInbox.WireEvent event : inbox.drainEvents(instance.plan.instanceId())) {
				if (event instanceof NodeAppendEvent append) {
					instance.appendNodes(append.payload().nodes());
				} else if (event instanceof FieldDeltaEvent delta) {
					for (FieldValue value : delta.payload().values()) {
						instance.fields.put(value.slot(), value.component());
					}
					instance.fieldRevision++;
				} else if (event instanceof ControlEvent control) {
					applyControl(client, instance, control.payload());
				}
			}
		}
	}

	private static void applyControl(
		final Minecraft client,
		final InstancePlayback instance,
		final MessageControlS2CPayload control
	) {
		switch (control.action()) {
			case PAUSE -> instance.pauseAt(client, control.effectiveClock());
			case RESUME -> instance.resumeAt(client, control.effectiveClock());
			case FINALIZE -> {
				for (FieldValue value : control.finalValues()) {
					instance.fields.put(value.slot(), value.component());
				}
				instance.fieldRevision++;
				finalizeInstance(client, instance);
			}
			case FINISH -> {
				for (FieldValue value : control.finalValues()) {
					instance.fields.put(value.slot(), value.component());
				}
				instance.fieldRevision++;
				finishInstance(client, instance);
			}
			case CANCEL -> cancelInstance(client, instance);
		}
	}

	private static void applyLifecyclePolicies(final Minecraft client) {
		boolean resourceReloading = ClientMessageWireState.resourceReloading();
		for (InstancePlayback instance : List.copyOf(INSTANCES.values())) {
			if (instance.terminal) {
				continue;
			}
			instance.advanceClock(client);
			if (resourceReloading && !instance.resourceReloading) {
				instance.resourceReloading = true;
				switch (instance.plan.policies().resourceReload()) {
					case FINISH -> {
					}
					case FINALIZE -> finalizeInstance(client, instance);
					case CANCEL -> cancelInstance(client, instance);
				}
			} else if (!resourceReloading) {
				instance.resourceReloading = false;
			}
			if (instance.terminal) {
				continue;
			}
			for (
				ClientMessageLifecycleTracker.Event event
					: ClientMessageLifecycleTracker.after(instance.lifecycleSequence)
			) {
				instance.lifecycleSequence = event.sequence();
				if (instance.terminal) {
					break;
				}
				boolean locallyOwned = instance.localBuiltin || instance.draining;
				Directive directive;
				if (event.reason() == ClientMessageLifecycleTracker.Reason.RESPAWN) {
					directive = MessagePresentationLifecyclePolicy.respawn(
						locallyOwned,
						instance.plan.policies().respawn()
					);
				} else {
					Identifier origin = instance.plan.origin().dimension();
					boolean leftFrozenOrigin = event.previousDimension().equals(origin)
						&& !event.currentDimension().equals(origin);
					directive = MessagePresentationLifecyclePolicy.dimensionExit(
						locallyOwned,
						leftFrozenOrigin,
						instance.plan.policies().dimensionExit()
					);
				}
				applyPresentationLifecycleDirective(client, instance, directive);
			}
		}
	}

	private static void applyPresentationLifecycleDirective(
		final Minecraft client,
		final InstancePlayback instance,
		final Directive directive
	) {
		switch (directive) {
			case WAIT_FOR_SERVER, CONTINUE -> {
			}
			case FINALIZE -> finalizeInstance(client, instance);
			case CANCEL -> cancelInstance(client, instance);
		}
	}

	private static Identifier currentDimension(final Minecraft client) {
		return client.level == null
			? null
			: client.level.dimension().identifier();
	}

	private static void scheduleDueNodes(final Minecraft client) {
		if (activeNodeCount() >= MAX_ACTIVE_TEXT_NODES) {
			return;
		}
		for (InstancePlayback instance : INSTANCES.values()) {
			if (instance.terminal || instance.paused) {
				continue;
			}
			long elapsed = instance.elapsedNanos(client);
			for (ResolvedNode node : instance.resolvedNodes) {
				NodeState state = instance.nodes.computeIfAbsent(node.ordinal(), ignored -> new NodeState());
				if (state.scheduled || state.complete || elapsed < node.startOffsetNanos()) {
					continue;
				}
				if (node instanceof ResolvedSoundNode sound) {
					playSound(client, sound, instance.plan, sound.ordinal());
					state.complete = true;
					continue;
				}
				ResolvedTextNode text = (ResolvedTextNode)node;
				if (!screenAllowsStart(client, instance, state, text, elapsed)) {
					continue;
				}
				if (
					text.channel() == Channel.CHAT
						&& ClientMessageWireState.chatUpdateMode()
							== ChatUpdateMode.STATIC_ONLY
						&& !fieldsReady(instance, text)
				) {
					continue;
				}
				PreparedText prepared = prepare(text, instance.fields, false);
				if (prepared == null) {
					state.complete = true;
					continue;
				}
				state.scheduled = true;
				route(
					client,
					new PendingText(
						instance,
						state,
						text,
						prepared,
						state.waitStartedElapsed >= 0L
							? elapsed
							: text.startOffsetNanos()
					),
					elapsed
				);
			}
		}
	}

	private static boolean screenAllowsStart(
		final Minecraft client,
		final InstancePlayback instance,
		final NodeState state,
		final ResolvedTextNode text,
		final long elapsed
	) {
		ScreenStart mode = instance.plan.policies().screenStart().get(
			text.channel()
		);
		if (mode == ScreenStart.IMMEDIATE) {
			return true;
		}
		if (mode == ScreenStart.FINAL_CHAT_ONLY) {
			if (fieldsReady(instance, text)) {
				finalizeTextNode(client, instance, state, text, true);
			} else {
				startScreenWait(state, elapsed);
				if (screenWaitTimedOut(instance, state, elapsed)) {
					applyNodeTimeout(client, instance, state, text);
				}
			}
			return false;
		}
		Surface surface = ClientMessageWireState.surface(client);
		if (mode == ScreenStart.CLOSE_SAFE_SCREEN && surface == Surface.MOD_SCREEN) {
			client.gui.setScreen(null);
			surface = ClientMessageWireState.surface(client);
		}
		if (surface == Surface.GAMEPLAY) {
			return true;
		}
		startScreenWait(state, elapsed);
		if (screenWaitTimedOut(instance, state, elapsed)) {
			applyNodeTimeout(client, instance, state, text);
		}
		return false;
	}

	private static void startScreenWait(
		final NodeState state,
		final long elapsed
	) {
		if (state.waitStartedElapsed < 0L) {
			state.waitStartedElapsed = elapsed;
		}
	}

	private static boolean screenWaitTimedOut(
		final InstancePlayback instance,
		final NodeState state,
		final long elapsed
	) {
		return instance.plan.policies().screenWaitTimeoutNanos().isPresent()
			&& elapsed - state.waitStartedElapsed
				>= instance.plan.policies().screenWaitTimeoutNanos().getAsLong();
	}

	private static void applyNodeTimeout(
		final Minecraft client,
		final InstancePlayback instance,
		final NodeState state,
		final ResolvedTextNode text
	) {
		switch (instance.plan.policies().screenTimeoutAction()) {
			case FINALIZE -> finalizeTextNode(
				client,
				instance,
				state,
				text,
				false
			);
			case CANCEL -> state.complete = true;
			case FINAL_CHAT_ONLY -> finalizeTextNode(
				client,
				instance,
				state,
				text,
				true
			);
		}
	}

	private static void route(
		final Minecraft client,
		final PendingText pending,
		final long instanceElapsed
	) {
		Channel channel = pending.node.channel();
		if (channel == Channel.CHAT) {
			if (pending.node.conflict() == Conflict.DISCARD && !CHAT.isEmpty()) {
				pending.state.complete = true;
				return;
			}
			if (pending.node.conflict() == Conflict.QUEUE && !CHAT.isEmpty()) {
				enqueue(pending);
				return;
			}
			if (pending.node.conflict() == Conflict.REPLACE) {
				for (PlayingText playing : List.copyOf(CHAT)) {
					interruptChat(playing, client);
					completePlaying(playing);
					CHAT.remove(playing);
				}
			}
			start(client, pending, instanceElapsed, true);
			return;
		}

		PlayingText current = EXCLUSIVE.get(channel);
		if (current == null) {
			start(client, pending, instanceElapsed, true);
			return;
		}
		switch (pending.node.conflict()) {
			case DISCARD -> pending.state.complete = true;
			case QUEUE, PARALLEL -> enqueue(pending);
			case REPLACE -> {
				release(current, client, false);
				completePlaying(current);
				EXCLUSIVE.remove(channel);
				start(client, pending, instanceElapsed, true);
			}
		}
	}

	private static void start(
		final Minecraft client,
		final PendingText pending,
		final long instanceElapsed,
		final boolean seekFromPresentationStart
	) {
		/*
		 * A queued node may outlive one or more authoritative field deltas, including the final
		 * values carried by FINISH. Never start from the PreparedText snapshot captured when the
		 * node first entered the queue. In particular, a later Chat capability downgrade must not
		 * turn that stale snapshot into a permanently incomplete static history entry.
		 */
		PreparedText current = prepare(
			pending.node,
			pending.instance.fields,
			false
		);
		if (current == null) {
			pending.state.complete = true;
			return;
		}
		if (
			pending.node.channel() == Channel.CHAT
				&& ClientMessageWireState.chatUpdateMode()
					== ChatUpdateMode.STATIC_ONLY
		) {
			if (!current.unresolvedSlots().isEmpty()) {
				enqueue(pending);
				return;
			}
			insertFinalChat(
				client,
				pending.instance.plan.instanceId(),
				pending.node.ordinal(),
				current.finalComponent(),
				pending.node.speaker()
			);
			pending.state.complete = true;
			return;
		}
		PlayingText playing = new PlayingText(
			pending.instance,
			pending.state,
			pending.node,
			current,
			instanceElapsed,
			seekFromPresentationStart
				? Math.max(
					0L,
					instanceElapsed - pending.presentationStartElapsed
				)
				: 0L
		);
		if (pending.node.channel() == Channel.CHAT) {
			MessageFrameTimeline.Frame initialFrame = playing.currentFrame();
			Component initial = playing.componentAt(initialFrame);
			Component initialLayout = playing.chatLayoutFrameAt(initialFrame, initial);
			StaticChatKey key = new StaticChatKey(
				pending.instance.plan.instanceId(),
				pending.node.ordinal()
			);
			DynamicChatEntry previous = STATIC_CHAT.remove(key);
			if (previous == null) {
				playing.chatEntry = DynamicChatEntry.insertProjected(
					client,
					initial,
					playing.prepared.finalComponent(),
					pending.node.speaker(),
					initialLayout
				);
			} else {
				DynamicChatEntry rebound = previous.rebind(client, pending.node.speaker());
				MutationResult result = rebound.beginProjected(
					client,
					initial,
					initialLayout
				);
				if (result == MutationResult.UPDATED) {
					playing.chatEntry = Optional.of(rebound);
				} else {
					if (result == MutationResult.CAPABILITY_FAILURE) {
						ClientMessageWireState.downgradeChatUpdates();
					}
					playing.state.complete = true;
					return;
				}
			}
			if (playing.chatEntry.isEmpty()) {
				playing.state.complete = true;
				return;
			}
			playing.lastFrame = initial;
			playing.lastChatLayoutFrame = initialLayout;
			CHAT.add(playing);
		} else {
			STATIC_HUD_TAILS.remove(pending.node.channel());
			MessageHudOverlay.clear(pending.node.channel());
			EXCLUSIVE.put(pending.node.channel(), playing);
		}
		if (pending.node.speaker().kind() == MessagePlaybackPlan.SpeakerKind.NARRATOR) {
			client.getNarrator().saySystemChatQueued(playing.prepared.finalComponent());
		}
	}

	private static void tickChat(final Minecraft client) {
		Iterator<PlayingText> iterator = CHAT.iterator();
		while (iterator.hasNext()) {
			PlayingText playing = iterator.next();
			playing.refreshFields(false);
			PlaybackDecision decision = screenDecision(client, playing);
			if (decision == PlaybackDecision.FINALIZE) {
				playing.refreshFields(true);
				finishChatEntry(client, playing, true);
				completePlaying(playing);
				iterator.remove();
				continue;
			}
			if (decision == PlaybackDecision.CANCEL) {
				finishChatEntry(client, playing, false);
				completePlaying(playing);
				iterator.remove();
				continue;
			}
			long instanceElapsed = playing.instance.elapsedNanos(client);
			MessageFrameTimeline.Frame frame = playing.advance(
				instanceElapsed,
				decision == PlaybackDecision.PAUSE
			);
			if (decision == PlaybackDecision.PAUSE) {
				if (pauseTimedOut(playing)) {
					applyPlayingTimeout(client, playing);
					iterator.remove();
				}
				continue;
			}
			playing.playCharacterSounds(client, frame.visibleGraphemes());
			Component component = playing.componentAt(frame);
			Component layoutFrame = playing.chatLayoutFrameAt(frame, component);
			if (
				!sameVisibleFrame(playing, frame, component)
					|| !layoutFrame.equals(playing.lastChatLayoutFrame)
			) {
				MutationResult update = playing.chatEntry
					.map(entry -> entry.updateProjected(client, component, layoutFrame))
					.orElse(MutationResult.ENTRY_GONE);
				if (update != MutationResult.UPDATED) {
					if (update == MutationResult.CAPABILITY_FAILURE) {
						ClientMessageWireState.downgradeChatUpdates();
					}
					STATIC_CHAT.remove(
						new StaticChatKey(
							playing.instance.plan.instanceId(),
							playing.node.ordinal()
						)
					);
					completePlaying(playing);
					iterator.remove();
					continue;
				}
				playing.lastFrame = component;
				playing.lastChatLayoutFrame = layoutFrame;
				playing.lastVisible = frame.visibleGraphemes();
				playing.lastCursorVisible = frame.cursorVisible();
			}
			if (
				frame.complete()
					&& playing.prepared.unresolvedSlots().isEmpty()
			) {
				finishChatEntry(client, playing, true);
				completePlaying(playing);
				iterator.remove();
			}
		}
	}

	private static void tickExclusive(final Minecraft client) {
		for (Channel channel : List.of(Channel.TITLE, Channel.SUBTITLE, Channel.ACTION_BAR)) {
			PlayingText playing = EXCLUSIVE.get(channel);
			if (playing == null) {
				continue;
			}
			playing.refreshFields(false);
			PlaybackDecision decision = screenDecision(client, playing);
			boolean hiddenForExternalContent = false;
			boolean offsetForExternalContent = false;
			if (decision == PlaybackDecision.RENDER) {
				MessageExternalContentPolicy.Directive externalDirective =
					MessageExternalContentPolicy.resolve(
						externalContentOwnsSurface(client, channel),
						playing.instance.plan.policies().externalConflict()
					);
				hiddenForExternalContent = !externalDirective.visible();
				offsetForExternalContent = externalDirective.offset();
				if (externalDirective.pauseClock()) {
					decision = PlaybackDecision.PAUSE;
				} else if (hiddenForExternalContent) {
					decision = PlaybackDecision.CONTINUE_HIDDEN;
				}
			}
			if (decision == PlaybackDecision.FINALIZE) {
				playing.refreshFields(true);
				renderStaticHudChannel(
					client,
					channel,
					playing.instance.plan.instanceId(),
					playing.node.ordinal(),
					playing.prepared.finalComponent(),
					playing.prepared.prelayout(),
					playing.node.layout(),
					playing.instance.plan.policies().externalConflict(),
					playing.timeline.effectiveHoldNanos()
				);
				completePlaying(playing);
				EXCLUSIVE.remove(channel);
				continue;
			}
			if (decision == PlaybackDecision.CANCEL) {
				release(playing, client, false);
				completePlaying(playing);
				EXCLUSIVE.remove(channel);
				continue;
			}
			long instanceElapsed = playing.instance.elapsedNanos(client);
			MessageFrameTimeline.Frame frame = playing.advance(
				instanceElapsed,
				decision == PlaybackDecision.PAUSE
			);
			if (decision == PlaybackDecision.PAUSE) {
				if (hiddenForExternalContent) {
					MessageHudOverlay.clear(channel);
				}
				if (pauseTimedOut(playing)) {
					applyPlayingTimeout(client, playing);
					EXCLUSIVE.remove(channel);
				}
				continue;
			}
			if (decision == PlaybackDecision.CONTINUE_HIDDEN) {
				MessageHudOverlay.clear(channel);
				playing.suppressCharacterSounds(frame.visibleGraphemes());
				if (
					frame.complete()
						&& playing.prepared.unresolvedSlots().isEmpty()
				) {
					release(playing, client, false);
					completePlaying(playing);
					EXCLUSIVE.remove(channel);
				}
				continue;
			}
			playing.playCharacterSounds(client, frame.visibleGraphemes());
			VisualFrame visual = playing.visualFrameAt(frame);
			MessageHudOverlay.update(
				channel,
				playing.prepared.finalComponent(),
				playing.prepared.prelayout(),
				visual,
				effectiveHudLayout(
					playing.node.layout(),
					channel,
					offsetForExternalContent
				)
			);
			playing.lastFrame = visual.component();
			playing.lastVisible = frame.visibleGraphemes();
			playing.lastCursorVisible = frame.cursorVisible();
			if (
				frame.complete()
					&& playing.prepared.unresolvedSlots().isEmpty()
			) {
				release(playing, client, true);
				completePlaying(playing);
				EXCLUSIVE.remove(channel);
			}
		}
	}

	private static PlaybackDecision screenDecision(
		final Minecraft client,
		final PlayingText playing
	) {
		if (ClientMessageWireState.surface(client) == Surface.GAMEPLAY) {
			return PlaybackDecision.RENDER;
		}
		return switch (
			playing.instance.plan.policies().obscured().get(
				playing.node.channel()
			)
		) {
			case CONTINUE -> PlaybackDecision.RENDER;
			case PAUSE -> PlaybackDecision.PAUSE;
			case FINALIZE -> PlaybackDecision.FINALIZE;
			case CANCEL -> PlaybackDecision.CANCEL;
		};
	}

	private static boolean pauseTimedOut(final PlayingText playing) {
		return playing.instance.plan.policies().maxPauseNanos().isPresent()
			&& playing.pausedElapsed
				>= playing.instance.plan.policies().maxPauseNanos().getAsLong();
	}

	private static void applyPlayingTimeout(
		final Minecraft client,
		final PlayingText playing
	) {
		playing.refreshFields(true);
		ScreenTimeoutAction action =
			playing.instance.plan.policies().screenTimeoutAction();
		if (action == ScreenTimeoutAction.FINALIZE) {
			if (playing.node.channel() == Channel.CHAT) {
				finishChatEntry(client, playing, true);
			} else {
				renderStaticHudChannel(
					client,
					playing.node.channel(),
					playing.instance.plan.instanceId(),
					playing.node.ordinal(),
					playing.prepared.finalComponent(),
					playing.prepared.prelayout(),
					playing.node.layout(),
					playing.instance.plan.policies().externalConflict(),
					playing.timeline.effectiveHoldNanos()
				);
			}
		} else if (action == ScreenTimeoutAction.FINAL_CHAT_ONLY) {
			if (playing.node.channel() == Channel.CHAT) {
				finishChatEntry(client, playing, true);
			} else {
				release(playing, client, false);
				insertFinalChat(
					client,
					playing.instance.plan.instanceId(),
					playing.node.ordinal(),
					playing.prepared.finalComponent(),
					playing.node.speaker()
				);
			}
		} else if (playing.node.channel() == Channel.CHAT) {
			finishChatEntry(client, playing, false);
		} else {
			release(playing, client, false);
		}
		completePlaying(playing);
	}

	private static void startQueues(final Minecraft client) {
		for (Channel channel : Channel.values()) {
			boolean busy = channel == Channel.CHAT ? !CHAT.isEmpty() : EXCLUSIVE.containsKey(channel);
			if (busy) {
				continue;
			}
			PendingText pending;
			while ((pending = QUEUED.get(channel).pollFirst()) != null) {
				if (pending.instance.terminal) {
					pending.state.complete = true;
					continue;
				}
				start(
					client,
					pending,
					pending.instance.elapsedNanos(client),
					false
				);
				break;
			}
		}
	}

	private static void enqueue(final PendingText pending) {
		ArrayDeque<PendingText> queue = QUEUED.get(pending.node.channel());
		List<PendingText> reordered = new ArrayList<>(queue);
		reordered.add(pending);
		reordered.sort(
			java.util.Comparator
				.comparingInt((PendingText value) -> value.node.priority())
				.reversed()
				.thenComparingInt(value -> value.node.ordinal())
		);
		queue.clear();
		queue.addAll(reordered);
	}

	private static void finalizeInstance(
		final Minecraft client,
		final InstancePlayback instance
	) {
		instance.draining = false;
		instance.terminal = true;
		for (PlayingText playing : List.copyOf(CHAT)) {
			if (playing.instance == instance) {
				playing.refreshFields(true);
				finishChatEntry(client, playing, true);
				completePlaying(playing);
				CHAT.remove(playing);
			}
		}
		for (Map.Entry<Channel, PlayingText> entry : List.copyOf(EXCLUSIVE.entrySet())) {
			if (entry.getValue().instance == instance) {
				PlayingText playing = entry.getValue();
				playing.refreshFields(true);
				Component finalComponent = playing.prepared.finalComponent();
				renderStaticHudChannel(
					client,
					playing.node.channel(),
					playing.instance.plan.instanceId(),
					playing.node.ordinal(),
					finalComponent,
					playing.prepared.prelayout(),
					playing.node.layout(),
					playing.instance.plan.policies().externalConflict(),
					playing.timeline.effectiveHoldNanos()
				);
				completePlaying(playing);
				EXCLUSIVE.remove(entry.getKey());
			}
		}
		clearQueued(instance, false);
		for (ResolvedNode node : instance.resolvedNodes) {
			NodeState state = instance.nodes.computeIfAbsent(
				node.ordinal(),
				ignored -> new NodeState()
			);
			if (state.complete) {
				continue;
			}
			if (node instanceof ResolvedTextNode text) {
				finalizeTextNode(client, instance, state, text, false);
			} else {
				state.complete = true;
			}
		}
	}

	/**
	 * Closes the authoritative stream without consuming time that this client spent waiting for an
	 * exclusive HUD channel or a safe gameplay surface. All nodes are already authorized and all
	 * final field values are locked by the FINISH packet; only local presentation work remains.
	 */
	private static void finishInstance(
		final Minecraft client,
		final InstancePlayback instance
	) {
		if (!instance.terminal) {
			instance.advanceClock(client);
			instance.draining = true;
			instance.paused = false;
			instance.pauseStartedRawNanos = -1L;
			instance.lastRawElapsedNanos = instance.rawElapsedNanos(client);
		}
	}

	private static void cancelInstance(
		final Minecraft client,
		final InstancePlayback instance
	) {
		instance.draining = false;
		instance.terminal = true;
		for (PlayingText playing : List.copyOf(CHAT)) {
			if (playing.instance == instance) {
				interruptChat(playing, client);
				completePlaying(playing);
				CHAT.remove(playing);
			}
		}
		for (Map.Entry<Channel, PlayingText> entry : List.copyOf(EXCLUSIVE.entrySet())) {
			if (entry.getValue().instance == instance) {
				release(entry.getValue(), client, false);
				completePlaying(entry.getValue());
				EXCLUSIVE.remove(entry.getKey());
			}
		}
		clearQueued(instance, true);
		clearStaticHudTailsOwnedBy(instance);
		instance.nodes.values().forEach(state -> state.complete = true);
	}

	private static void retireForRefresh(
		final Minecraft client,
		final InstancePlayback instance,
		final Set<Integer> retainedChatOrdinals
	) {
		instance.terminal = true;
		for (PlayingText playing : List.copyOf(CHAT)) {
			if (playing.instance == instance) {
				StaticChatKey key = new StaticChatKey(
					instance.plan.instanceId(),
					playing.node.ordinal()
				);
				if (retainedChatOrdinals.contains(playing.node.ordinal())) {
					playing.chatEntry.ifPresent(entry -> trackStaticChat(key, entry));
				} else {
					finishChatEntry(client, playing, false);
				}
				completePlaying(playing);
				CHAT.remove(playing);
			}
		}
		for (Map.Entry<Channel, PlayingText> entry : List.copyOf(EXCLUSIVE.entrySet())) {
			if (entry.getValue().instance == instance) {
				release(entry.getValue(), client, false);
				completePlaying(entry.getValue());
				EXCLUSIVE.remove(entry.getKey());
			}
		}
		clearQueued(instance, true);
		clearStaticHudTailsOwnedBy(instance);
		instance.nodes.values().forEach(state -> state.complete = true);
	}

	private static void clearQueued(
		final InstancePlayback instance,
		final boolean complete
	) {
		for (ArrayDeque<PendingText> queue : QUEUED.values()) {
			queue.removeIf(pending -> {
				if (pending.instance == instance) {
					pending.state.complete = complete;
					return true;
				}
				return false;
			});
		}
	}

	private static void finalizeTextNode(
		final Minecraft client,
		final InstancePlayback instance,
		final NodeState state,
		final ResolvedTextNode text,
		final boolean forceChat
	) {
		PreparedText prepared = prepare(text, instance.fields, true);
		if (prepared != null) {
			if (forceChat || text.channel() == Channel.CHAT) {
				insertFinalChat(
					client,
					instance.plan.instanceId(),
					text.ordinal(),
					prepared.finalComponent(),
					text.speaker()
				);
			} else {
				renderStaticHudChannel(
					client,
					text.channel(),
					instance.plan.instanceId(),
					text.ordinal(),
					prepared.finalComponent(),
					prepared.prelayout(),
					text.layout(),
					instance.plan.policies().externalConflict(),
					prepared.timeline().effectiveHoldNanos()
				);
			}
		}
		state.scheduled = true;
		state.complete = true;
	}

	private static void insertFinalChat(
		final Minecraft client,
		final UUID instanceId,
		final int ordinal,
		final Component component,
		final ResolvedSpeaker speaker
	) {
		StaticChatKey key = new StaticChatKey(instanceId, ordinal);
		DynamicChatEntry previous = STATIC_CHAT.get(key);
		if (previous != null) {
			DynamicChatEntry rebound = previous.rebind(client, speaker);
			MutationResult result = rebound.finishClean(client, component);
			if (result == MutationResult.UPDATED) {
				trackStaticChat(key, rebound);
			} else {
				STATIC_CHAT.remove(key);
				if (result == MutationResult.CAPABILITY_FAILURE) {
					ClientMessageWireState.downgradeChatUpdates();
				}
			}
			// ENTRY_GONE means vanilla or the player deliberately removed the history
			// entry. Never resurrect it during a refresh/finalization pass.
			return;
		}
		DynamicChatEntry.InsertAttempt attempt = DynamicChatEntry.insert(
			client,
			component,
			speaker
		);
		Optional<DynamicChatEntry> tracked = attempt.entry();
		if (tracked.isPresent()) {
			trackStaticChat(key, tracked.orElseThrow());
		} else if (!attempt.finalInserted()) {
			DynamicChatEntry.insertStatic(client, component, speaker);
		}
	}

	private static void finishChatEntry(
		final Minecraft client,
		final PlayingText playing,
		final boolean finalize
	) {
		StaticChatKey key = new StaticChatKey(
			playing.instance.plan.instanceId(),
			playing.node.ordinal()
		);
		MutationResult result = playing.finishChat(client, finalize);
		if (finalize && result == MutationResult.UPDATED) {
			playing.chatEntry.ifPresent(entry -> trackStaticChat(key, entry));
		} else {
			STATIC_CHAT.remove(key);
		}
		if (result == MutationResult.CAPABILITY_FAILURE) {
			ClientMessageWireState.downgradeChatUpdates();
		}
	}

	private static Set<Integer> chatOrdinals(final MessagePlaybackPlan plan) {
		Set<Integer> ordinals = new LinkedHashSet<>();
		for (ResolvedNode node : plan.nodes()) {
			if (
				node instanceof ResolvedTextNode text
					&& text.channel() == Channel.CHAT
			) {
				ordinals.add(text.ordinal());
			}
		}
		return Set.copyOf(ordinals);
	}

	private static void trackStaticChat(
		final StaticChatKey key,
		final DynamicChatEntry entry
	) {
		STATIC_CHAT.put(key, entry);
		while (STATIC_CHAT.size() > MAX_TRACKED_STATIC_CHAT) {
			Iterator<StaticChatKey> iterator = STATIC_CHAT.keySet().iterator();
			iterator.next();
			iterator.remove();
		}
	}

	private static void removeStaticChatEntriesExcept(
		final Minecraft client,
		final UUID instanceId,
		final Set<Integer> retainedOrdinals
	) {
		Objects.requireNonNull(retainedOrdinals, "retainedOrdinals");
		Iterator<Map.Entry<StaticChatKey, DynamicChatEntry>> iterator =
			STATIC_CHAT.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<StaticChatKey, DynamicChatEntry> entry = iterator.next();
			StaticChatKey key = entry.getKey();
			if (
				!key.instanceId().equals(instanceId)
					|| retainedOrdinals.contains(key.ordinal())
			) {
				continue;
			}
			MutationResult result = entry.getValue().remove(client);
			if (result == MutationResult.CAPABILITY_FAILURE) {
				ClientMessageWireState.downgradeChatUpdates();
			}
			iterator.remove();
		}
	}

	private static void interruptChat(
		final PlayingText playing,
		final Minecraft client
	) {
		playing.refreshFields(true);
		finishChatEntry(
			client,
			playing,
			playing.instance.plan.policies().chatInterrupt()
				== MessagePlaybackPlan.ChatInterrupt.FINALIZE
		);
	}

	private static void release(
		final PlayingText playing,
		final Minecraft client,
		final boolean natural
	) {
		if (playing.node.channel() != Channel.CHAT) {
			Channel channel = playing.node.channel();
			StaticHudTail ownedTail = STATIC_HUD_TAILS.get(channel);
			if (ownedTail != null && ownedTail.ownedBy(playing)) {
				STATIC_HUD_TAILS.remove(channel, ownedTail);
			}
			if (
				!natural
					|| QUEUED.get(channel)
						.stream()
						.noneMatch(pending -> !pending.instance.terminal)
			) {
				StaticHudTail replacement = STATIC_HUD_TAILS.get(channel);
				if (replacement == null) {
					MessageHudOverlay.clear(channel);
				} else {
					renderStaticHudTail(client, channel, replacement);
				}
			}
		}
	}

	private static void completePlaying(final PlayingText playing) {
		playing.state.complete = true;
	}

	private static void removeCompletedInstances() {
		Iterator<Map.Entry<UUID, InstancePlayback>> iterator = INSTANCES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, InstancePlayback> entry = iterator.next();
			InstancePlayback instance = entry.getValue();
			boolean allComplete = instance.resolvedNodes
				.stream()
				.allMatch(node -> instance.nodes.getOrDefault(node.ordinal(), NodeState.PENDING).complete);
			boolean authoritativeStreamClosed = instance.terminal || instance.draining;
			if (
				instance.terminal
					|| instance.draining && allComplete
					|| instance.localBuiltin && allComplete
			) {
				if (authoritativeStreamClosed) {
					ClientMessageWireState.inbox().remove(entry.getKey());
				} else {
					ClientMessageWireState.inbox().retire(entry.getKey());
				}
				iterator.remove();
			}
		}
	}

	private static PreparedText prepare(
		final ResolvedTextNode node,
		final Map<Integer, ResolvedComponent> fields,
		final boolean useFallback
	) {
		try {
			MutableComponent combined = Component.empty();
			MutableComponent availablePrefix = Component.empty();
			MessagePrelayoutPlan.Builder prelayout = MessagePrelayoutPlan.builder();
			List<SegmentSpan> spans = new ArrayList<>(node.segments().size());
			Set<Integer> unresolved = new LinkedHashSet<>();
			int firstUnresolvedSegment = -1;
			boolean prefixOpen = true;
			for (int index = 0; index < node.segments().size(); index++) {
				ResolvedSegment segment = node.segments().get(index);
				Component component;
				if (segment instanceof StaticSegment fixed) {
					component = parse(fixed.component());
					prelayout.append(component);
				} else {
					FieldSlotSegment field = (FieldSlotSegment)segment;
					ResolvedComponent value = fields.get(field.slot());
					if (value != null) {
						component = parse(value);
					} else if (useFallback) {
						component = parse(field.fallback().orElseThrow());
					} else {
						component = Component.empty();
						unresolved.add(field.slot());
						prefixOpen = false;
						if (firstUnresolvedSegment < 0) {
							firstUnresolvedSegment = index;
						}
					}
					Component fallback = field.fallback()
						.map(ClientMessagePlayback::parse)
						.orElseGet(Component::empty);
					Component layoutValue = value != null || useFallback
						? component
						: Component.empty();
					prelayout.field(layoutValue, fallback, field.reservation());
				}
				combined.append(component);
				if (prefixOpen) {
					availablePrefix.append(component.copy());
				}
				spans.add(
					new SegmentSpan(
						StyledTypewriter.flatten(component).graphemes().size(),
						segment.timing()
					)
				);
			}
			MessageFrameTimeline.Timeline timeline = MessageFrameTimeline.compile(
				combined,
				node.effect(),
				node.duration(),
				spans
			);
			OptionalLong fieldGate = OptionalLong.empty();
			if (firstUnresolvedSegment >= 0) {
				MessageFrameTimeline.SegmentWindow window =
					timeline.segmentWindows().get(firstUnresolvedSegment);
				fieldGate = OptionalLong.of(
					saturatingAdd(
						window.startNanos(),
						node.segments()
							.get(firstUnresolvedSegment)
							.timing()
							.pauseBeforeNanos()
					)
				);
			}
			return new PreparedText(
				combined,
				availablePrefix,
				prelayout.build(),
				timeline,
				unresolved,
				fieldGate
			);
		} catch (RuntimeException error) {
			PixelTzzPro.LOGGER.warn(
				"V3B client could not prepare authorized node {}: {}",
				node.ordinal(),
				error.getMessage()
			);
			return null;
		}
	}

	private static Component parse(final ResolvedComponent component) {
		return ComponentSerialization.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString(component.canonicalJson()))
			.getOrThrow();
	}

	private static boolean fieldsReady(
		final InstancePlayback instance,
		final ResolvedTextNode node
	) {
		for (ResolvedSegment segment : node.segments()) {
			if (
				segment instanceof FieldSlotSegment field
					&& !instance.fields.containsKey(field.slot())
			) {
				return false;
			}
		}
		return true;
	}

	private static long initialLocalStart(
		final Minecraft client,
		final MessagePlaybackPlan plan
	) {
		if (plan.clock().kind() == MessagePlaybackPlan.ClockKind.GAME_TICK) {
			return 0L;
		}
		long difference = plan.clock().startAt() - plan.clock().serverNow();
		try {
			return Math.addExact(System.nanoTime(), difference);
		} catch (ArithmeticException overflow) {
			return difference < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
	}

	private static int activeNodeCount() {
		int result = CHAT.size() + EXCLUSIVE.size();
		for (ArrayDeque<PendingText> queue : QUEUED.values()) {
			result += queue.size();
		}
		return result;
	}

	private static boolean usesStaticReducedMotion(
		final InstancePlayback instance
	) {
		return ClientMessagePreferences.snapshot().reducedMotion()
			&& instance.plan.policies().reducedMotion()
				== MessagePlaybackPlan.ReducedMotion.STATIC_FINAL;
	}

	private static boolean sameVisibleFrame(
		final PlayingText playing,
		final MessageFrameTimeline.Frame frame,
		final Component component
	) {
		return playing.lastFrame != null
			&& playing.lastVisible == frame.visibleGraphemes()
			&& playing.lastCursorVisible == frame.cursorVisible()
			&& playing.lastFrame.equals(component);
	}

	private static boolean externalContentOwnsSurface(
		final Minecraft client,
		final Channel channel
	) {
		HudAccessor hud = (HudAccessor)(Object)client.gui.hud;
		if (channel == Channel.ACTION_BAR) {
			Component current = hud.pixelTzzPro$getOverlayMessage();
			return hud.pixelTzzPro$getOverlayMessageTime() > 0
				&& current != null
				&& !current.getString().isEmpty();
		}
		if (hud.pixelTzzPro$getTitleTime() <= 0) {
			return false;
		}
		Component title = hud.pixelTzzPro$getTitle();
		Component subtitle = hud.pixelTzzPro$getSubtitle();
		boolean externalTitle = title != null
			&& !title.getString().isEmpty();
		boolean externalSubtitle = subtitle != null
			&& !subtitle.getString().isEmpty();
		return externalTitle || externalSubtitle;
	}

	private static void renderStaticHudChannel(
		final Minecraft client,
		final Channel channel,
		final UUID ownerInstanceId,
		final int ownerNodeOrdinal,
		final Component component,
		final MessagePrelayoutPlan prelayout,
		final ResolvedLayout layout,
		final ExternalConflict externalConflict,
		final long holdNanos
	) {
		if (channel == Channel.CHAT) {
			throw new IllegalArgumentException("Chat uses a logical history entry");
		}
		StaticHudTail tail = new StaticHudTail(
			ownerInstanceId,
			ownerNodeOrdinal,
			component,
			prelayout,
			layout,
			externalConflict,
			tailTicks(holdNanos)
		);
		// One exclusive HUD surface can retain only its newest finalized projection. Replaying
		// an older forced tail after a newer terminal result would present stale information.
		STATIC_HUD_TAILS.put(channel, tail);
		PlayingText active = EXCLUSIVE.get(channel);
		if (active == null || tail.ownedBy(active)) {
			renderStaticHudTail(client, channel, tail);
		}
	}

	private static ResolvedLayout effectiveHudLayout(
		final ResolvedLayout source,
		final Channel channel,
		final boolean offsetForExternalContent
	) {
		if (!offsetForExternalContent) {
			return source;
		}
		int safeShift = switch (channel) {
			case TITLE -> -36;
			case SUBTITLE -> 24;
			case ACTION_BAR -> -18;
			case CHAT -> 0;
		};
		return new ResolvedLayout(
			source.alignment(),
			source.offsetX(),
			Math.clamp(source.offsetY() + safeShift, -256, 256),
			source.maxWidth(),
			source.maxLines(),
			source.lineSpacing(),
			source.overflow()
		);
	}

	private static void tickStaticHudTails(final Minecraft client) {
		for (
			Map.Entry<Channel, StaticHudTail> entry
			: List.copyOf(STATIC_HUD_TAILS.entrySet())
		) {
			Channel channel = entry.getKey();
			StaticHudTail tail = entry.getValue();
			if (EXCLUSIVE.containsKey(channel)) {
				continue;
			}
			MessageExternalContentPolicy.Directive directive =
				MessageExternalContentPolicy.resolve(
					externalContentOwnsSurface(client, channel),
					tail.externalConflict
				);
			if (directive.pauseClock()) {
				renderStaticHudTail(channel, tail, directive);
				continue;
			}
			tail.remainingTicks--;
			if (tail.remainingTicks <= 0) {
				boolean removed = STATIC_HUD_TAILS.remove(channel, tail);
				if (removed && !EXCLUSIVE.containsKey(channel)) {
					MessageHudOverlay.clear(channel);
				}
				continue;
			}
			renderStaticHudTail(channel, tail, directive);
		}
	}

	private static void renderStaticHudTail(
		final Minecraft client,
		final Channel channel,
		final StaticHudTail tail
	) {
		MessageExternalContentPolicy.Directive directive =
			MessageExternalContentPolicy.resolve(
				externalContentOwnsSurface(client, channel),
				tail.externalConflict
			);
		renderStaticHudTail(channel, tail, directive);
	}

	private static void renderStaticHudTail(
		final Channel channel,
		final StaticHudTail tail,
		final MessageExternalContentPolicy.Directive directive
	) {
		if (!directive.visible()) {
			MessageHudOverlay.clear(channel);
			return;
		}
		MessageHudOverlay.update(
			channel,
			tail.component,
			tail.prelayout,
			new VisualFrame(tail.component, 1.0F, 0.0F, 0.0F, 1.0F),
			effectiveHudLayout(
				tail.sourceLayout,
				channel,
				directive.offset()
			)
		);
	}

	private static void clearStaticHudTailsOwnedBy(
		final InstancePlayback instance
	) {
		for (
			Map.Entry<Channel, StaticHudTail> entry
			: List.copyOf(STATIC_HUD_TAILS.entrySet())
		) {
			Channel channel = entry.getKey();
			StaticHudTail tail = entry.getValue();
			if (
				tail.ownedBy(instance)
					&& STATIC_HUD_TAILS.remove(channel, tail)
					&& !EXCLUSIVE.containsKey(channel)
			) {
				MessageHudOverlay.clear(channel);
			}
		}
	}

	private static int tailTicks(final long holdNanos) {
		long ticks = Math.max(
			2L,
			Math.ceilDiv(Math.max(0L, holdNanos), NANOS_PER_GAME_TICK)
		);
		return (int)Math.min(Integer.MAX_VALUE, ticks);
	}

	private static void playSound(
		final Minecraft client,
		final ResolvedSoundNode sound,
		final MessagePlaybackPlan plan,
		final int salt
	) {
		playSound(
			client,
			sound.sound(),
			sound.category(),
			sound.volume(),
			sound.pitch(),
			sound.attachment(),
			plan,
			salt
		);
	}

	private static void playSound(
		final Minecraft client,
		final Identifier sound,
		final MessagePlaybackPlan.SoundCategory category,
		final float volume,
		final float pitch,
		final MessagePlaybackPlan.SoundAttachment attachment,
		final MessagePlaybackPlan plan,
		final int salt
	) {
		SoundEvent event = SoundEvent.createVariableRangeEvent(sound);
		if (client.player == null || client.level == null) {
			return;
		}
		if (attachment == MessagePlaybackPlan.SoundAttachment.PLAYER) {
			client.getSoundManager().play(
				new SimpleSoundInstance(
					event,
					soundSource(category),
					volume,
					pitch,
					RandomSource.create(
						plan.instanceId().getLeastSignificantBits() ^ salt
					),
					client.player.blockPosition()
				)
			);
			return;
		}
		if (!plan.origin().dimension().equals(currentDimension(client))) {
			return;
		}
		client.getSoundManager().play(
			new SimpleSoundInstance(
				event,
				soundSource(category),
				volume,
				pitch,
				RandomSource.create(
					plan.instanceId().getMostSignificantBits() ^ salt
				),
				plan.origin().x(),
				plan.origin().y(),
				plan.origin().z()
			)
		);
	}

	private static SoundSource soundSource(
		final MessagePlaybackPlan.SoundCategory category
	) {
		return switch (category) {
			case MASTER -> SoundSource.MASTER;
			case MUSIC -> SoundSource.MUSIC;
			case RECORD -> SoundSource.RECORDS;
			case WEATHER -> SoundSource.WEATHER;
			case BLOCK -> SoundSource.BLOCKS;
			case HOSTILE -> SoundSource.HOSTILE;
			case NEUTRAL -> SoundSource.NEUTRAL;
			case PLAYER -> SoundSource.PLAYERS;
			case AMBIENT -> SoundSource.AMBIENT;
			case VOICE -> SoundSource.VOICE;
			case UI -> SoundSource.UI;
		};
	}

	private static final class InstancePlayback {
		private final MessagePlaybackPlan plan;
		private final boolean localBuiltin;
		private final List<ResolvedNode> resolvedNodes = new ArrayList<>();
		private final long localPresentationStart;
		private final Map<Integer, ResolvedComponent> fields = new HashMap<>();
		private final Map<Integer, NodeState> nodes = new HashMap<>();
		private long lastRawElapsedNanos;
		private long playbackElapsedNanos;
		private long authoritativePauseOffsetNanos;
		private long localPauseOffsetNanos;
		private long pauseStartedRawNanos = -1L;
		private int fieldRevision;
		private long lifecycleSequence;
		private boolean resourceReloading;
		private boolean paused;
		/** Server FINISH received; retain only already-authorized local presentation work. */
		private boolean draining;
		private boolean terminal;
		private boolean manualCompleteRequested;

		private InstancePlayback(
			final Minecraft client,
			final MessagePlaybackPlan plan,
			final boolean localBuiltin
		) {
			this.plan = Objects.requireNonNull(plan, "plan");
			this.localBuiltin = localBuiltin;
			this.resolvedNodes.addAll(plan.nodes());
			this.localPresentationStart = initialLocalStart(client, plan);
			this.lastRawElapsedNanos = rawElapsedNanos(client);
			this.playbackElapsedNanos = this.lastRawElapsedNanos;
			for (ResolvedNode node : this.resolvedNodes) {
				if (
					node instanceof ResolvedSoundNode
						&& MessagePlaybackSeekPolicy.suppressPastSound(
							this.playbackElapsedNanos,
							node.startOffsetNanos()
						)
				) {
					NodeState state = new NodeState();
					state.complete = true;
					this.nodes.put(node.ordinal(), state);
				}
			}
			this.lifecycleSequence = ClientMessageLifecycleTracker.currentSequence();
		}

		private void appendNodes(final List<ResolvedNode> appended) {
			if (appended.isEmpty()) {
				throw new IllegalArgumentException(
					"an authorized node append cannot be empty"
				);
			}
			Set<Integer> ordinals = new LinkedHashSet<>();
			for (ResolvedNode existing : this.resolvedNodes) {
				ordinals.add(existing.ordinal());
			}
			for (ResolvedNode node : appended) {
				if (!ordinals.add(node.ordinal())) {
					throw new IllegalArgumentException(
						"authorized node append repeated ordinal " + node.ordinal()
					);
				}
				this.resolvedNodes.add(node);
			}
			this.resolvedNodes.sort(
				java.util.Comparator
					.comparingLong(ResolvedNode::startOffsetNanos)
					.thenComparingInt(ResolvedNode::ordinal)
			);
		}

		private long rawElapsedNanos(final Minecraft client) {
			if (this.plan.clock().kind() == MessagePlaybackPlan.ClockKind.GAME_TICK) {
				long ticks = Math.max(0L, client.level.getGameTime() - this.plan.clock().startAt());
				return ticks > Long.MAX_VALUE / NANOS_PER_GAME_TICK
					? Long.MAX_VALUE
					: ticks * NANOS_PER_GAME_TICK;
			}
			return Math.max(0L, System.nanoTime() - this.localPresentationStart);
		}

		private long elapsedNanos(final Minecraft client) {
			advanceClock(client);
			return this.playbackElapsedNanos;
		}

		private void advanceClock(final Minecraft client) {
			long raw = rawElapsedNanos(client);
			long delta = raw >= this.lastRawElapsedNanos
				? raw - this.lastRawElapsedNanos
				: 0L;
			this.lastRawElapsedNanos = raw;
			if (this.paused) {
				return;
			}
			if (client.isPaused()) {
				this.localPauseOffsetNanos = saturatingAdd(
					this.localPauseOffsetNanos,
					delta
				);
			} else {
				this.playbackElapsedNanos = saturatingAdd(
					this.playbackElapsedNanos,
					delta
				);
			}
		}

		private void pauseAt(
			final Minecraft client,
			final long effectiveClock
		) {
			advanceClock(client);
			if (!this.paused) {
				long effectiveRaw = elapsedAtClock(effectiveClock);
				this.playbackElapsedNanos = subtractOffsets(effectiveRaw);
				this.pauseStartedRawNanos = effectiveRaw;
				this.paused = true;
				this.lastRawElapsedNanos = rawElapsedNanos(client);
			}
		}

		private void resumeAt(
			final Minecraft client,
			final long effectiveClock
		) {
			if (this.paused) {
				long effectiveRaw = elapsedAtClock(effectiveClock);
				long pauseDuration = effectiveRaw >= this.pauseStartedRawNanos
					? effectiveRaw - this.pauseStartedRawNanos
					: 0L;
				this.authoritativePauseOffsetNanos = saturatingAdd(
					this.authoritativePauseOffsetNanos,
					pauseDuration
				);
				this.playbackElapsedNanos = subtractOffsets(effectiveRaw);
				this.pauseStartedRawNanos = -1L;
				this.paused = false;
				// Preserve time elapsed after the authoritative resume point even
				// when the control arrives a few client ticks later.
				this.lastRawElapsedNanos = effectiveRaw;
			}
		}

		private long elapsedAtClock(final long effectiveClock) {
			long startAt = this.plan.clock().startAt();
			if (effectiveClock <= startAt) {
				return 0L;
			}
			long difference = effectiveClock - startAt;
			if (this.plan.clock().kind() == MessagePlaybackPlan.ClockKind.GAME_TICK) {
				return difference > Long.MAX_VALUE / NANOS_PER_GAME_TICK
					? Long.MAX_VALUE
					: difference * NANOS_PER_GAME_TICK;
			}
			return difference;
		}

		private long subtractOffsets(final long raw) {
			long result = Math.max(
				0L,
				raw - Math.min(raw, this.authoritativePauseOffsetNanos)
			);
			return Math.max(
				0L,
				result - Math.min(result, this.localPauseOffsetNanos)
			);
		}
	}

	private static final class NodeState {
		private static final NodeState PENDING = new NodeState();
		private long waitStartedElapsed = -1L;
		private boolean scheduled;
		private boolean complete;
	}

	private enum PlaybackDecision {
		RENDER,
		CONTINUE_HIDDEN,
		PAUSE,
		FINALIZE,
		CANCEL
	}

	private static final class StaticHudTail {
		private final UUID ownerInstanceId;
		private final int ownerNodeOrdinal;
		private final Component component;
		private final MessagePrelayoutPlan prelayout;
		private final ResolvedLayout sourceLayout;
		private final ExternalConflict externalConflict;
		private int remainingTicks;

		private StaticHudTail(
			final UUID ownerInstanceId,
			final int ownerNodeOrdinal,
			final Component component,
			final MessagePrelayoutPlan prelayout,
			final ResolvedLayout sourceLayout,
			final ExternalConflict externalConflict,
			final int remainingTicks
		) {
			this.ownerInstanceId = Objects.requireNonNull(
				ownerInstanceId,
				"ownerInstanceId"
			);
			if (ownerNodeOrdinal < 0) {
				throw new IllegalArgumentException("ownerNodeOrdinal must be non-negative");
			}
			this.ownerNodeOrdinal = ownerNodeOrdinal;
			this.component = Objects.requireNonNull(component, "component").copy();
			this.prelayout = Objects.requireNonNull(prelayout, "prelayout");
			this.sourceLayout = Objects.requireNonNull(sourceLayout, "sourceLayout");
			this.externalConflict = Objects.requireNonNull(
				externalConflict,
				"externalConflict"
			);
			if (remainingTicks <= 0) {
				throw new IllegalArgumentException("remainingTicks must be positive");
			}
			this.remainingTicks = remainingTicks;
		}

		private boolean ownedBy(final PlayingText playing) {
			return ownedBy(playing.instance)
				&& this.ownerNodeOrdinal == playing.node.ordinal();
		}

		private boolean ownedBy(final InstancePlayback instance) {
			return this.ownerInstanceId.equals(instance.plan.instanceId());
		}
	}

	private record StaticChatKey(UUID instanceId, int ordinal) {
		private StaticChatKey {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
		}
	}

	private record PreparedText(
		Component finalComponent,
		Component availablePrefix,
		MessagePrelayoutPlan prelayout,
		MessageFrameTimeline.Timeline timeline,
		Set<Integer> unresolvedSlots,
		OptionalLong fieldGateNanos
	) {
		private PreparedText {
			finalComponent = Objects.requireNonNull(finalComponent, "finalComponent");
			availablePrefix = Objects.requireNonNull(
				availablePrefix,
				"availablePrefix"
			);
			prelayout = Objects.requireNonNull(prelayout, "prelayout");
			timeline = Objects.requireNonNull(timeline, "timeline");
			unresolvedSlots = Set.copyOf(unresolvedSlots);
			fieldGateNanos = Objects.requireNonNull(
				fieldGateNanos,
				"fieldGateNanos"
			);
		}
	}

	private record PendingText(
		InstancePlayback instance,
		NodeState state,
		ResolvedTextNode node,
		PreparedText prepared,
		long presentationStartElapsed
	) {
		private PendingText {
			instance = Objects.requireNonNull(instance, "instance");
			state = Objects.requireNonNull(state, "state");
			node = Objects.requireNonNull(node, "node");
			prepared = Objects.requireNonNull(prepared, "prepared");
			if (presentationStartElapsed < 0L) {
				throw new IllegalArgumentException(
					"presentationStartElapsed must be non-negative"
				);
			}
		}
	}

	private static final class PlayingText {
		private final InstancePlayback instance;
		private final NodeState state;
		private final ResolvedTextNode node;
		private PreparedText prepared;
		private MessageFrameTimeline.Timeline timeline;
		private Optional<DynamicChatEntry> chatEntry = Optional.empty();
		private int fieldRevision;
		private long lastInstanceElapsed;
		private long playbackElapsed;
		private long pausedElapsed;
		private double scaledNanosRemainder;
		private int lastVisible = -1;
		private boolean lastCursorVisible;
		private Component lastFrame;
		private Component lastChatLayoutFrame;
		private int characterSoundEvents;
		private int eligibleCharacterCount;
		private int soundScannedGraphemes;

		private PlayingText(
			final InstancePlayback instance,
			final NodeState state,
			final ResolvedTextNode node,
			final PreparedText prepared,
			final long instanceElapsed,
			final long initialPlaybackElapsed
		) {
			this.instance = instance;
			this.state = state;
			this.node = node;
			this.prepared = prepared;
			this.timeline = prepared.timeline();
			this.lastInstanceElapsed = instanceElapsed;
			this.playbackElapsed = Math.min(
				Math.max(0L, initialPlaybackElapsed),
				this.timeline.completeAtNanos()
			);
			this.fieldRevision = prepared.unresolvedSlots().isEmpty()
				? instance.fieldRevision
				: -1;
			revealImmediatelyWhenAllowed();
		}

		private MessageFrameTimeline.Frame advance(
			final long instanceElapsed,
			final boolean blocked
		) {
			long delta = Math.max(0L, instanceElapsed - this.lastInstanceElapsed);
			this.lastInstanceElapsed = instanceElapsed;
			if (blocked) {
				this.pausedElapsed = saturatingAdd(this.pausedElapsed, delta);
			} else {
				this.pausedElapsed = 0L;
				long scaledDelta = scalePlaybackDelta(delta);
				long advanced = saturatingAdd(
					this.playbackElapsed,
					scaledDelta
				);
				if (this.prepared.fieldGateNanos().isPresent()) {
					long gate = this.prepared.fieldGateNanos().getAsLong();
					/* A local speed preference may reach this boundary before the authoritative
					 * field delta. Hold the presentation clock here; only a field delta or the
					 * authoritative FINALIZE values may release it. A client-side timeout must
					 * never turn a valid late field into its registered fallback. */
					advanced = Math.min(advanced, gate);
				}
				this.playbackElapsed = advanced;
				revealImmediatelyWhenAllowed();
			}
			return this.timeline.frameAt(this.playbackElapsed);
		}

		private MessageFrameTimeline.Frame currentFrame() {
			revealImmediatelyWhenAllowed();
			return this.timeline.frameAt(this.playbackElapsed);
		}

		private long scalePlaybackDelta(final long delta) {
			double scaled = delta
				* (double)ClientMessagePreferences.snapshot().animationSpeed()
				+ this.scaledNanosRemainder;
			if (scaled >= Long.MAX_VALUE) {
				this.scaledNanosRemainder = 0.0D;
				return Long.MAX_VALUE;
			}
			long whole = Math.max(0L, (long)Math.floor(scaled));
			this.scaledNanosRemainder = scaled - whole;
			return whole;
		}

		private void revealImmediatelyWhenAllowed() {
			if (
				this.prepared.unresolvedSlots().isEmpty()
					&& (
						this.instance.manualCompleteRequested
							|| usesStaticReducedMotion(this.instance)
					)
			) {
				this.playbackElapsed = Math.max(
					this.playbackElapsed,
					this.timeline.revealCompleteAtNanos()
				);
			}
		}

		private void refreshFields(final boolean useFallback) {
			if (!useFallback && this.fieldRevision == this.instance.fieldRevision) {
				return;
			}
			PreparedText refreshed = prepare(
				this.node,
				this.instance.fields,
				useFallback
			);
			if (refreshed != null) {
				this.prepared = refreshed;
				this.timeline = refreshed.timeline();
				this.fieldRevision = this.instance.fieldRevision;
				revealImmediatelyWhenAllowed();
			}
		}

		private Component componentAt(final MessageFrameTimeline.Frame frame) {
			return visualFrameAt(frame).component();
		}

		private Component chatLayoutFrameAt(
			final MessageFrameTimeline.Frame frame,
			final Component visible
		) {
			if (this.node.channel() == Channel.CHAT) {
				int representedSourceGraphemes = frame.visibleGraphemes();
				if (
					this.node.effect().scramble().isPresent()
						&& !ClientMessagePreferences.snapshot().reducedMotion()
				) {
					// A live scramble already emits one glyph for every currently known source
					// grapheme. Only reservation/fallback envelope beyond that source should be
					// appended; using visibleGraphemes here would double the scramble geometry.
					representedSourceGraphemes = this.timeline
						.styledText()
						.graphemes()
						.size();
				}
				return this.prepared.prelayout().padChatFrame(
					Minecraft.getInstance().font,
					visible,
					representedSourceGraphemes
				);
			}
			return visible;
		}

		private VisualFrame visualFrameAt(
			final MessageFrameTimeline.Frame frame
		) {
			ClientMessagePreferences.Snapshot preferences =
				ClientMessagePreferences.snapshot();
			return StyledMessageEffects.frame(
				this.timeline,
				frame,
				this.instance.plan.instanceId(),
				this.node.ordinal(),
				preferences.reducedMotion(),
				preferences.cursorBlink(),
				preferences.highContrast()
			);
		}

		private void playCharacterSounds(
			final Minecraft client,
			final int visibleGraphemes
		) {
			Optional<CharacterSound> optional = this.node.effect().characterSound();
			ClientMessagePreferences.Snapshot preferences =
				ClientMessagePreferences.snapshot();
			if (optional.isEmpty() || visibleGraphemes <= this.soundScannedGraphemes) {
				return;
			}
			if (
				!preferences.characterSound()
					|| preferences.characterSoundVolume() <= 0.0F
			) {
				this.soundScannedGraphemes = visibleGraphemes;
				return;
			}
			CharacterSound sound = optional.orElseThrow();
			boolean due = false;
			int latest = this.soundScannedGraphemes;
			for (
				int index = this.soundScannedGraphemes;
				index < visibleGraphemes && index < this.timeline.styledText().graphemes().size();
				index++
			) {
				String grapheme = this.timeline.styledText().graphemes().get(index).text();
				if (skipSound(grapheme, sound)) {
					continue;
				}
				this.eligibleCharacterCount++;
				if (this.eligibleCharacterCount % sound.everyCharacters() == 0) {
					due = true;
					latest = index;
				}
			}
			this.soundScannedGraphemes = visibleGraphemes;
			if (
				due
					&& this.characterSoundEvents < this.node.effect().maxCharacterSoundEvents()
			) {
				float variation = deterministicVariation(
					this.instance.plan.instanceId(),
					this.node.ordinal(),
					latest,
					sound.pitchVariation()
				);
				playSound(
					client,
					sound.sound(),
					sound.category(),
					sound.volume() * preferences.characterSoundVolume(),
					Math.clamp(sound.pitch() + variation, 0.0F, 2.0F),
					sound.attachment(),
					this.instance.plan,
					this.node.ordinal() * 65_537 + latest
				);
				this.characterSoundEvents++;
			}
		}

		private void suppressCharacterSounds(final int visibleGraphemes) {
			Optional<CharacterSound> optional = this.node.effect().characterSound();
			if (optional.isEmpty() || visibleGraphemes <= this.soundScannedGraphemes) {
				return;
			}
			ClientMessagePreferences.Snapshot preferences =
				ClientMessagePreferences.snapshot();
			if (
				!preferences.characterSound()
					|| preferences.characterSoundVolume() <= 0.0F
			) {
				this.soundScannedGraphemes = visibleGraphemes;
				return;
			}
			CharacterSound sound = optional.orElseThrow();
			for (
				int index = this.soundScannedGraphemes;
				index < visibleGraphemes && index < this.timeline.styledText().graphemes().size();
				index++
			) {
				String grapheme = this.timeline.styledText().graphemes().get(index).text();
				if (!skipSound(grapheme, sound)) {
					this.eligibleCharacterCount++;
				}
			}
			this.soundScannedGraphemes = visibleGraphemes;
		}

		private MutationResult finishChat(
			final Minecraft client,
			final boolean finalize
		) {
			if (this.chatEntry.isEmpty()) {
				return MutationResult.ENTRY_GONE;
			}
			DynamicChatEntry entry = this.chatEntry.orElseThrow();
			if (!finalize) {
				return entry.remove(client);
			}
			Component finalComponent = this.prepared.finalComponent();
			Component finalLayout = this.prepared.prelayout().padChatFrame(
				client.font,
				finalComponent,
				this.timeline.styledText().graphemes().size()
			);
			return entry.finishProjected(client, finalComponent, finalLayout);
		}
	}

	private static boolean skipSound(
		final String grapheme,
		final CharacterSound sound
	) {
		if (grapheme.isEmpty()) {
			return true;
		}
		int codePoint = grapheme.codePointAt(0);
		if (sound.skipNewline() && (codePoint == '\n' || codePoint == '\r')) {
			return true;
		}
		if (sound.skipWhitespace() && Character.isWhitespace(codePoint)) {
			return true;
		}
		int type = Character.getType(codePoint);
		return sound.skipPunctuation()
			&& (
				type == Character.CONNECTOR_PUNCTUATION
					|| type == Character.DASH_PUNCTUATION
					|| type == Character.START_PUNCTUATION
					|| type == Character.END_PUNCTUATION
					|| type == Character.INITIAL_QUOTE_PUNCTUATION
					|| type == Character.FINAL_QUOTE_PUNCTUATION
					|| type == Character.OTHER_PUNCTUATION
			);
	}

	private static float deterministicVariation(
		final UUID instanceId,
		final int ordinal,
		final int character,
		final float maximum
	) {
		if (maximum == 0.0F) {
			return 0.0F;
		}
		long mixed = instanceId.getMostSignificantBits()
			^ Long.rotateLeft(instanceId.getLeastSignificantBits(), 19)
			^ (long)ordinal * 0x9E3779B97F4A7C15L
			^ character * 0x632BE59BD9B4E019L;
		double unit = (mixed >>> 11) * 0x1.0p-53;
		return (float)((unit * 2.0D - 1.0D) * maximum);
	}

	private static long saturatingAdd(final long left, final long right) {
		if (right > Long.MAX_VALUE - left) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}
}
