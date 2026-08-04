package io.github.zcpu954861.pixeltzzpro.client.message;

import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.client.message.ChatLineProjectionAccess.MutationResult;
import io.github.zcpu954861.pixeltzzpro.mixin.ChatComponentAccessor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Handle for one real vanilla system-chat entry whose visible frame can be replaced in place.
 */
final class DynamicChatEntry {
	private GuiMessage entry;
	private final SpeakerDecoration speaker;

	private DynamicChatEntry(
		final GuiMessage entry,
		final SpeakerDecoration speaker
	) {
		this.entry = Objects.requireNonNull(entry, "entry");
		this.speaker = Objects.requireNonNull(speaker, "speaker");
	}

	static Optional<DynamicChatEntry> insertProjected(
		final Minecraft client,
		final Component initialFrame,
		final Component finalFrame,
		final ResolvedSpeaker speaker,
		final Component layoutFrame
	) {
		Objects.requireNonNull(initialFrame, "initialFrame");
		Objects.requireNonNull(finalFrame, "finalFrame");
		Objects.requireNonNull(layoutFrame, "layoutFrame");
		InsertAttempt attempt = insert(client, finalFrame, speaker);
		Optional<DynamicChatEntry> tracked = attempt.entry();
		if (tracked.isEmpty()) {
			if (!attempt.finalInserted()) {
				insertStatic(client, finalFrame, speaker);
			}
			return Optional.empty();
		}
		DynamicChatEntry entry = tracked.orElseThrow();
		MutationResult result = entry.beginProjected(client, initialFrame, layoutFrame);
		if (result == MutationResult.UPDATED) {
			return tracked;
		}
		// The clean final entry was inserted first and remains the safe fallback when the
		// same-call initial-frame replacement is unavailable.
		if (result == MutationResult.CAPABILITY_FAILURE) {
			ClientMessageWireState.downgradeChatUpdates();
		}
		return Optional.empty();
	}

	static InsertAttempt insert(
		final Minecraft client,
		final Component initialFrame,
		final ResolvedSpeaker speaker
	) {
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(initialFrame, "initialFrame");
		SpeakerDecoration decoration = SpeakerDecoration.resolve(client, speaker);
		if (client.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
			return new InsertAttempt(Optional.empty(), false);
		}

		ChatComponent chat = client.gui.hud.getChat();
		ChatComponentAccessor access;
		try {
			access = (ChatComponentAccessor)(Object)chat;
			access.pixelTzzPro$getAllMessages();
		} catch (RuntimeException | LinkageError unsupported) {
			ClientMessageWireState.downgradeChatUpdates();
			return new InsertAttempt(Optional.empty(), false);
		}
		List<GuiMessage> beforeMessages = access.pixelTzzPro$getAllMessages();
		Set<GuiMessage> before = Collections.newSetFromMap(
			new IdentityHashMap<>()
		);
		before.addAll(beforeMessages);
		Component decoratedFrame = decoration.apply(initialFrame);
		chat.addServerSystemMessage(decoratedFrame);
		List<GuiMessage> messages = access.pixelTzzPro$getAllMessages();
		GuiMessage newest = null;
		int newestIndex = -1;
		for (int index = 0; index < messages.size(); index++) {
			GuiMessage candidate = messages.get(index);
			if (before.contains(candidate)) {
				continue;
			}
			if (!isUnsignedSystemEntry(candidate)) {
				continue;
			}
			if (candidate.content() == decoratedFrame) {
				return new InsertAttempt(
					Optional.of(new DynamicChatEntry(candidate, decoration)),
					true
				);
			}
			if (
				newest == null
					|| candidate.addedTime() > newest.addedTime()
					|| (
						candidate.addedTime() == newest.addedTime()
							&& index < newestIndex
					)
			) {
				newest = candidate;
				newestIndex = index;
			}
		}
		if (newest != null) {
			return new InsertAttempt(
				Optional.of(new DynamicChatEntry(newest, decoration)),
				true
			);
		}
		ClientMessageWireState.downgradeChatUpdates();
		return new InsertAttempt(Optional.empty(), true);
	}

	static void insertStatic(
		final Minecraft client,
		final Component finalComponent,
		final ResolvedSpeaker speaker
	) {
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(finalComponent, "finalComponent");
		if (client.options.chatVisibility().get() != ChatVisiblity.HIDDEN) {
			client.gui.hud.getChat()
				.addServerSystemMessage(
					SpeakerDecoration.resolve(client, speaker).apply(finalComponent)
				);
		}
	}

	static boolean supportsInPlace(final Minecraft client) {
		Objects.requireNonNull(client, "client");
		try {
			ChatComponentAccessor access = (ChatComponentAccessor)(Object)
				client.gui.hud.getChat();
			access.pixelTzzPro$getAllMessages();
			ChatLineProjectionAccess projections = (ChatLineProjectionAccess)(Object)
				client.gui.hud.getChat();
			access.pixelTzzPro$refreshTrimmedMessages();
			return projections.pixelTzzPro$projectionHooksOperational();
		} catch (RuntimeException | LinkageError unsupported) {
			return false;
		}
	}

	DynamicChatEntry rebind(
		final Minecraft client,
		final ResolvedSpeaker resolvedSpeaker
	) {
		return new DynamicChatEntry(
			this.entry,
			SpeakerDecoration.resolve(
				Objects.requireNonNull(client, "client"),
				Objects.requireNonNull(resolvedSpeaker, "resolvedSpeaker")
			)
		);
	}

	MutationResult update(final Minecraft client, final Component frame) {
		return update(client, frame, Optional.empty(), false);
	}

	MutationResult updateProjected(
		final Minecraft client,
		final Component frame,
		final Component layoutFrame
	) {
		return update(
			client,
			frame,
			Optional.of(Objects.requireNonNull(layoutFrame, "layoutFrame")),
			false
		);
	}

	MutationResult beginProjected(
		final Minecraft client,
		final Component frame,
		final Component layoutFrame
	) {
		MutationResult updated = updateProjected(client, frame, layoutFrame);
		if (updated != MutationResult.UPDATED) {
			return updated;
		}
		return setForceOpaque(client, true);
	}

	MutationResult finishProjected(
		final Minecraft client,
		final Component frame,
		final Component layoutFrame
	) {
		MutationResult updated = update(
			client,
			frame,
			Optional.of(Objects.requireNonNull(layoutFrame, "layoutFrame")),
			true
		);
		if (updated != MutationResult.UPDATED) {
			return updated;
		}
		return setForceOpaque(client, false);
	}

	MutationResult finishClean(
		final Minecraft client,
		final Component frame
	) {
		MutationResult updated = update(
			client,
			frame,
			Optional.empty(),
			true
		);
		if (updated != MutationResult.UPDATED) {
			return updated;
		}
		return setForceOpaque(client, false);
	}

	private MutationResult update(
		final Minecraft client,
		final Component frame,
		final Optional<Component> layoutFrame,
		final boolean resetAge
	) {
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(frame, "frame");
		Objects.requireNonNull(layoutFrame, "layoutFrame");
		try {
			ChatLineProjectionAccess projections = (ChatLineProjectionAccess)(Object)
				client.gui.hud.getChat();
			GuiMessage replacement = new GuiMessage(
				resetAge ? client.gui.hud.getGuiTicks() : this.entry.addedTime(),
				this.speaker.apply(frame),
				this.entry.signature(),
				this.entry.source(),
				this.entry.tag()
			);
			MutationResult result = layoutFrame.isPresent()
				? projections.pixelTzzPro$replaceProjection(
					this.entry,
					replacement,
					new ChatLineProjection(
						this.speaker.apply(layoutFrame.orElseThrow())
					)
				)
				: projections.pixelTzzPro$replaceWithClean(this.entry, replacement);
			if (result == MutationResult.UPDATED) {
				this.entry = replacement;
			}
			return result;
		} catch (RuntimeException | LinkageError unsupported) {
			return MutationResult.CAPABILITY_FAILURE;
		}
	}

	private MutationResult setForceOpaque(
		final Minecraft client,
		final boolean forceOpaque
	) {
		Objects.requireNonNull(client, "client");
		try {
			return ((ChatLineProjectionAccess)(Object)client.gui.hud.getChat())
				.pixelTzzPro$setForceOpaque(this.entry, forceOpaque);
		} catch (RuntimeException | LinkageError unsupported) {
			return MutationResult.CAPABILITY_FAILURE;
		}
	}

	private static boolean isUnsignedSystemEntry(final GuiMessage message) {
		return message.signature() == null
			&& message.source() == GuiMessageSource.SYSTEM_SERVER;
	}

	MutationResult remove(final Minecraft client) {
		Objects.requireNonNull(client, "client");
		try {
			return ((ChatLineProjectionAccess)(Object)client.gui.hud.getChat())
				.pixelTzzPro$removeMessage(this.entry);
		} catch (RuntimeException | LinkageError unsupported) {
			return MutationResult.CAPABILITY_FAILURE;
		}
	}

	static record InsertAttempt(
		Optional<DynamicChatEntry> entry,
		boolean finalInserted
	) {
		InsertAttempt {
			entry = Objects.requireNonNull(entry, "entry");
		}
	}

	/**
	 * Immutable decoration derived only from the server-resolved speaker projection.
	 *
	 * <p>Vanilla 26.2 does not expose a profile field on {@link GuiMessage}; its only source
	 * metadata is the coarse {@link GuiMessageSource} enum. A player profile is therefore carried
	 * by vanilla's source-aware {@link PlayerSprite} component while the enclosing history entry
	 * stays an unsigned system message. Chat Heads recognizes an existing PlayerSprite and does not
	 * add a duplicate head.</p>
	 */
	private record SpeakerDecoration(
		Optional<ResolvableProfile> profile,
		Optional<String> displayName
	) {
		private SpeakerDecoration {
			profile = Objects.requireNonNull(profile, "profile");
			displayName = Objects.requireNonNull(displayName, "displayName");
			if (profile.isPresent() && displayName.isEmpty()) {
				throw new IllegalArgumentException(
					"a player speaker decoration requires a display name"
				);
			}
		}

		private static SpeakerDecoration resolve(
			final Minecraft client,
			final ResolvedSpeaker speaker
		) {
			Objects.requireNonNull(client, "client");
			Objects.requireNonNull(speaker, "speaker");
			if (speaker.kind() == SpeakerKind.SYSTEM || speaker.kind() == SpeakerKind.NARRATOR) {
				return new SpeakerDecoration(Optional.empty(), Optional.empty());
			}
			if (speaker.kind() == SpeakerKind.ENTITY) {
				return new SpeakerDecoration(Optional.empty(), speaker.displayName());
			}
			java.util.UUID profileId = speaker.profileId().orElseThrow();
			PlayerInfo online = client.getConnection() == null
				? null
				: client.getConnection().getPlayerInfo(profileId);
			ResolvableProfile profile = online == null
				? ResolvableProfile.createUnresolved(profileId)
				: ResolvableProfile.createResolved(online.getProfile());
			return new SpeakerDecoration(Optional.of(profile), speaker.displayName());
		}

		private Component apply(final Component frame) {
			Objects.requireNonNull(frame, "frame");
			if (this.displayName.isEmpty()) {
				return frame;
			}
			MutableComponent identity = Component.empty();
			this.profile.ifPresent(value ->
				identity.append(
					Component.object(
						new PlayerSprite(value, true),
						Component.empty()
					)
				).append(Component.literal(" "))
			);
			identity.append(Component.literal(this.displayName.orElseThrow()));
			return Component.translatable("chat.type.text", identity, frame);
		}
	}
}
