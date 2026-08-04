package io.github.zcpu954861.pixeltzzpro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.zcpu954861.pixeltzzpro.client.message.ChatLineProjection;
import io.github.zcpu954861.pixeltzzpro.client.message.ChatLineProjectionAccess;
import io.github.zcpu954861.pixeltzzpro.client.message.ChatLineProjectionAccess.MutationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owns the bounded display-line projection for dynamic Chat entries.
 *
 * <p>Normal animation frames replace only the exact message and its existing line block. Vanilla
 * keeps ownership of message order, scroll state, logging, filtering, and the 100-entry caps. A
 * full rebuild is used only when vanilla itself requests one for resize/filter/restore.</p>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentProjectionMixin implements ChatLineProjectionAccess {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;

	@Shadow
	private int chatScrollbarPos;

	@Shadow
	protected abstract int getWidth();

	@Shadow
	protected abstract double getScale();

	@Shadow
	public abstract int getLinesPerPage();

	@Unique
	private IdentityHashMap<GuiMessage, ChatLineProjection>
		pixelTzzPro$lineProjections;

	@Unique
	private Set<GuiMessage> pixelTzzPro$forceOpaque;

	@Unique
	private boolean pixelTzzPro$refreshHookObserved;

	@Override
	public MutationResult pixelTzzPro$replaceProjection(
		final GuiMessage previous,
		final GuiMessage replacement,
		final ChatLineProjection projection
	) {
		return this.pixelTzzPro$replaceMessage(
			previous,
			replacement,
			Objects.requireNonNull(projection, "projection")
		);
	}

	@Override
	public MutationResult pixelTzzPro$replaceWithClean(
		final GuiMessage previous,
		final GuiMessage replacement
	) {
		return this.pixelTzzPro$replaceMessage(previous, replacement, null);
	}

	@Override
	public MutationResult pixelTzzPro$removeMessage(final GuiMessage message) {
		Objects.requireNonNull(message, "message");
		this.pixelTzzPro$ensureProjectionState();
		int allIndex = pixelTzzPro$identityIndex(this.allMessages, message);
		this.pixelTzzPro$lineProjections.remove(message);
		this.pixelTzzPro$forceOpaque.remove(message);
		if (allIndex < 0) {
			return MutationResult.ENTRY_GONE;
		}
		this.allMessages.remove(allIndex);
		this.pixelTzzPro$removeLineBlock(message);
		return MutationResult.UPDATED;
	}

	@Override
	public MutationResult pixelTzzPro$setForceOpaque(
		final GuiMessage message,
		final boolean forceOpaque
	) {
		Objects.requireNonNull(message, "message");
		this.pixelTzzPro$ensureProjectionState();
		if (pixelTzzPro$identityIndex(this.allMessages, message) < 0) {
			this.pixelTzzPro$forceOpaque.remove(message);
			return MutationResult.ENTRY_GONE;
		}
		if (forceOpaque) {
			this.pixelTzzPro$forceOpaque.add(message);
		} else {
			this.pixelTzzPro$forceOpaque.remove(message);
		}
		return MutationResult.UPDATED;
	}

	@Override
	public boolean pixelTzzPro$projectionHooksOperational() {
		return this.pixelTzzPro$refreshHookObserved;
	}

	@Unique
	private MutationResult pixelTzzPro$replaceMessage(
		final GuiMessage previous,
		final GuiMessage replacement,
		final ChatLineProjection projection
	) {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(replacement, "replacement");
		this.pixelTzzPro$ensureProjectionState();
		int allIndex = pixelTzzPro$identityIndex(this.allMessages, previous);
		if (allIndex < 0) {
			this.pixelTzzPro$lineProjections.remove(previous);
			this.pixelTzzPro$forceOpaque.remove(previous);
			return MutationResult.ENTRY_GONE;
		}

		this.allMessages.set(allIndex, replacement);
		this.pixelTzzPro$lineProjections.remove(previous);
		boolean forceOpaque = this.pixelTzzPro$forceOpaque.remove(previous);
		if (projection != null) {
			this.pixelTzzPro$lineProjections.put(replacement, projection);
		}
		if (forceOpaque) {
			this.pixelTzzPro$forceOpaque.add(replacement);
		}
		this.pixelTzzPro$replaceLineBlock(previous, replacement, projection);
		return MutationResult.UPDATED;
	}

	@Unique
	private void pixelTzzPro$replaceLineBlock(
		final GuiMessage previous,
		final GuiMessage replacement,
		final ChatLineProjection projection
	) {
		int first = -1;
		int last = -1;
		for (int index = 0; index < this.trimmedMessages.size(); index++) {
			if (this.trimmedMessages.get(index).parent() == previous) {
				if (first < 0) {
					first = index;
				}
				last = index;
			}
		}
		if (first < 0) {
			// The entry is filtered or already beyond vanilla's 100 display-line window.
			// Updating allMessages is sufficient; a later vanilla refresh will project it.
			return;
		}

		int oldCount = last - first + 1;
		List<FormattedCharSequence> split = projection == null
			? replacement.splitLines(
				this.minecraft.font,
				Mth.floor(this.getWidth() / this.getScale())
			)
			: projection.split(
				replacement,
				this.minecraft.font,
				Mth.floor(this.getWidth() / this.getScale())
			);
		List<GuiMessage.Line> replacementLines = pixelTzzPro$lines(replacement, split);
		int oldScroll = this.chatScrollbarPos;
		this.trimmedMessages.subList(first, last + 1).clear();
		this.trimmedMessages.addAll(first, replacementLines);
		while (this.trimmedMessages.size() > 100) {
			this.trimmedMessages.removeLast();
		}
		this.pixelTzzPro$restoreScrollAnchor(
			oldScroll,
			first,
			replacementLines.size() - oldCount
		);
	}

	@Unique
	private void pixelTzzPro$removeLineBlock(final GuiMessage message) {
		int first = -1;
		int removed = 0;
		for (int index = this.trimmedMessages.size() - 1; index >= 0; index--) {
			if (this.trimmedMessages.get(index).parent() == message) {
				first = index;
				this.trimmedMessages.remove(index);
				removed++;
			}
		}
		if (removed > 0) {
			this.pixelTzzPro$restoreScrollAnchor(
				this.chatScrollbarPos,
				first,
				-removed
			);
		}
	}

	@Unique
	private void pixelTzzPro$restoreScrollAnchor(
		final int oldScroll,
		final int rangeStart,
		final int lineDelta
	) {
		int anchored = rangeStart < oldScroll
			? oldScroll + lineDelta
			: oldScroll;
		int maximum = Math.max(0, this.trimmedMessages.size() - this.getLinesPerPage());
		this.chatScrollbarPos = Math.clamp(anchored, 0, maximum);
	}

	@Unique
	private static List<GuiMessage.Line> pixelTzzPro$lines(
		final GuiMessage message,
		final List<FormattedCharSequence> split
	) {
		int lastVisible = -1;
		for (int index = split.size() - 1; index >= 0; index--) {
			if (!ChatLineProjection.isHiddenLine(split.get(index))) {
				lastVisible = index;
				break;
			}
		}
		List<GuiMessage.Line> result = new ArrayList<>(split.size());
		for (int index = split.size() - 1; index >= 0; index--) {
			result.add(
				new GuiMessage.Line(
					message,
					split.get(index),
					index == lastVisible
				)
			);
		}
		return result;
	}

	@Unique
	private static int pixelTzzPro$identityIndex(
		final List<GuiMessage> messages,
		final GuiMessage target
	) {
		for (int index = 0; index < messages.size(); index++) {
			if (messages.get(index) == target) {
				return index;
			}
		}
		return -1;
	}

	@Inject(method = "refreshTrimmedMessages", at = @At("HEAD"), require = 0)
	private void pixelTzzPro$beforeRefresh(final CallbackInfo callback) {
		this.pixelTzzPro$refreshHookObserved = true;
		this.pixelTzzPro$pruneLineProjections();
	}

	@Inject(method = "refreshTrimmedMessages", at = @At("RETURN"), require = 0)
	private void pixelTzzPro$afterRefresh(final CallbackInfo callback) {
		this.pixelTzzPro$ensureProjectionState();
		for (Map.Entry<GuiMessage, ChatLineProjection> entry :
			List.copyOf(this.pixelTzzPro$lineProjections.entrySet())) {
			this.pixelTzzPro$replaceLineBlock(
				entry.getKey(),
				entry.getKey(),
				entry.getValue()
			);
		}
	}

	@WrapOperation(
		method = "forEachLine",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;"
				+ "accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"
		),
		require = 0
	)
	private void pixelTzzPro$hideReservedLineLayers(
		@Coerce final Object consumer,
		final GuiMessage.Line line,
		final int lineIndex,
		final float alpha,
		final Operation<Void> original
	) {
		if (!ChatLineProjection.isHiddenLine(line.content())) {
			original.call(consumer, line, lineIndex, alpha);
		}
	}

	@WrapOperation(
		method = "forEachLine",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;"
				+ "calculate(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;)F"
		),
		require = 0
	)
	private float pixelTzzPro$keepActiveMessagesOpaque(
		@Coerce final Object calculator,
		final GuiMessage.Line line,
		final Operation<Float> original
	) {
		this.pixelTzzPro$ensureProjectionState();
		if (this.pixelTzzPro$forceOpaque.contains(line.parent())) {
			return 1.0F;
		}
		return original.call(calculator, line);
	}

	@Inject(method = "addMessageToQueue", at = @At("TAIL"), require = 0)
	private void pixelTzzPro$pruneAfterQueueInsert(
		final GuiMessage message,
		final CallbackInfo callback
	) {
		this.pixelTzzPro$pruneLineProjections();
	}

	@Inject(method = "clearMessages", at = @At("RETURN"), require = 0)
	private void pixelTzzPro$clearLineProjections(
		final boolean history,
		final CallbackInfo callback
	) {
		this.pixelTzzPro$ensureProjectionState();
		this.pixelTzzPro$lineProjections.clear();
		this.pixelTzzPro$forceOpaque.clear();
	}

	@Unique
	private void pixelTzzPro$pruneLineProjections() {
		this.pixelTzzPro$ensureProjectionState();
		if (
			this.pixelTzzPro$lineProjections.isEmpty()
				&& this.pixelTzzPro$forceOpaque.isEmpty()
		) {
			return;
		}
		Set<GuiMessage> live = Collections.newSetFromMap(new IdentityHashMap<>());
		live.addAll(this.allMessages);
		this.pixelTzzPro$lineProjections.keySet().removeIf(message -> !live.contains(message));
		this.pixelTzzPro$forceOpaque.removeIf(message -> !live.contains(message));
	}

	/**
	 * Mixin does not merge this class's implicit constructor into vanilla {@link ChatComponent}.
	 * Keep target-owned projection state lazy so ordinary system Chat and disconnect cleanup are
	 * safe even before the first animated Pixel TZZ entry has been installed.
	 */
	@Unique
	private void pixelTzzPro$ensureProjectionState() {
		if (this.pixelTzzPro$lineProjections == null) {
			this.pixelTzzPro$lineProjections = new IdentityHashMap<>();
		}
		if (this.pixelTzzPro$forceOpaque == null) {
			this.pixelTzzPro$forceOpaque = Collections.newSetFromMap(
				new IdentityHashMap<>()
			);
		}
	}
}
