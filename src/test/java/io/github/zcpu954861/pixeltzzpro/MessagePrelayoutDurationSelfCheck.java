package io.github.zcpu954861.pixeltzzpro;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitionParser;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.server.MessageProjectionCompiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Focused contract checks for dynamic-field pre-layout and language duration bases. */
public final class MessagePrelayoutDurationSelfCheck {
	private static final Identifier EFFECT = id("basis_effect");

	private MessagePrelayoutDurationSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		checkLanguageDurationBasis();
		checkClientPrelayoutSourceContract();

		System.out.println("MESSAGE_PRELAYOUT_DURATION_SELF_CHECK=PASS");
	}

	private static void checkLanguageDurationBasis() {
		TextEffectDefinition effect = MessageDefinitionParser.parseTextEffect(
			EFFECT,
			"""
			{
			  "format_version": 1,
			  "typewriter": {"character_interval": "100ms"}
			}
			"""
		).value().orElseThrow();
		long fixed = duration(parseCue("fixed"), effect);
		long longest = duration(parseCue("longest_declared"), effect);
		check(
			fixed == 1_000_000_000L,
			"fixed language basis must use the fallback template as its fixed estimate: "
				+ fixed
		);
		check(
			longest == 4_000_000_000L,
			"longest_declared must retain the longest fallback/locale estimate: " + longest
		);
		check(
			longest > fixed,
			"language_basis must materially affect fixed_total authoritative duration"
		);
	}

	private static long duration(
		final MessageCueDefinition cue,
		final TextEffectDefinition effect
	) {
		return MessageProjectionCompiler.authoritativeDurations(
			cue,
			Map.of(EFFECT, effect)
		).get("text").nanoseconds();
	}

	private static MessageCueDefinition parseCue(final String languageBasis) {
		var parsed = MessageDefinitionParser.parseMessageCue(
			id("cue_" + languageBasis),
			"""
			{
			  "format_version": 1,
			  "nodes": [
			    {
			      "id": "text",
			      "type": "text",
			      "schedule": {"type": "cue_start", "offset": "0t"},
			      "channel": "subtitle",
			      "content": {
			        "text": {
			          "fallback": {
			            "parts": [{"component": {"text": "甲"}}]
			          },
			          "locales": {
			            "en_us": {
			              "parts": [{"component": {"text": "LONGTEXT"}}]
			            }
			          }
			        },
			        "effect": {"preset": "pixel_tzz:basis_effect"},
			        "duration": {
			          "mode": "fixed_total",
			          "total": "1s",
			          "language_basis": "%s",
			          "maximum_speed": 2.0
			        }
			      }
			    }
			  ]
			}
			""".formatted(languageBasis)
		);
		check(parsed.valid(), "duration basis cue must parse: " + parsed.issues());
		return parsed.value().orElseThrow();
	}

	private static void checkClientPrelayoutSourceContract() throws IOException {
		String playback = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/"
				+ "ClientMessagePlayback.java"
		);
		String prelayout = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/"
				+ "MessagePrelayoutPlan.java"
		);
		String overlay = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/"
				+ "MessageHudOverlay.java"
		);
		String chatEntry = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/"
				+ "DynamicChatEntry.java"
		);
		String chatProjection = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/"
				+ "ChatLineProjection.java"
		);
		String chatMixin = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/mixin/"
				+ "ChatComponentProjectionMixin.java"
		);
		String clientEntrypoint = source(
			"src/client/java/io/github/zcpu954861/pixeltzzpro/client/"
				+ "PixelTzzProClient.java"
		);
		String playbackPlan = source(
			"src/main/java/io/github/zcpu954861/pixeltzzpro/network/message/"
				+ "MessagePlaybackPlan.java"
		);
		requireInOrder(
			playback,
			"MessagePrelayoutPlan.Builder prelayout = MessagePrelayoutPlan.builder()",
			"field.fallback()",
			"prelayout.field(layoutValue, fallback, field.reservation())",
			"prelayout.build()"
		);
		check(
			playback.contains("this.prepared.prelayout().padChatFrame("),
			"Chat must prepare a private layout frame as well as HUD geometry"
		);
		check(
			playback.contains("this.node.effect().scramble().isPresent()")
				&& playback.contains(
					"representedSourceGraphemes = this.timeline\n"
						+ "\t\t\t\t\t\t.styledText()"
				),
			"a live scramble must reserve only geometry beyond its already full source projection"
		);
		requireInOrder(
			playback,
			"Component initial = playing.componentAt(initialFrame)",
			"Component initialLayout = playing.chatLayoutFrameAt(initialFrame, initial)",
			"DynamicChatEntry.insertProjected(",
			"playing.prepared.finalComponent()"
		);
		check(
			playback.contains(
				"private Component componentAt(final MessageFrameTimeline.Frame frame)"
					+ " {\n\t\t\treturn visualFrameAt(frame).component();\n\t\t}"
			),
			"the clean visible Chat frame must not contain pre-layout padding"
		);
		requireInOrder(
			prelayout,
			"target.append(value.copy())",
			"reservation.estimatedWidth()",
			"reservation.maxGraphemes()",
			"reservation.maxLines()"
		);
		check(
			!prelayout.contains("substring(") && !prelayout.contains("substrByWidth("),
			"field reservation must pad measurement only and never truncate the authorized value"
		);
		requireInOrder(
			prelayout,
			"List<LayoutToken> layout = layoutTokens(font)",
			"token.sourceOffset() >= represented",
			"token.source() && token.sourceOffset() < represented",
			"else if (token.source() || includeReservationEnvelope)",
			"ChatLineProjection.hidden(component(token.grapheme()))"
		);
		check(
			prelayout.contains(
				"A field's\n\t\t\t\t// max-width/max-lines envelope is useful for HUD bounds"
			),
			"Chat must exclude synthetic reservation-only glyphs from vanilla line wrapping"
		);
		check(
			prelayout.contains("return grapheme.component();"),
			"Chat pre-layout must preserve style spans inside one atomic grapheme"
		);
		requireInOrder(
			prelayout,
			"StyledTypewriter.flatten(source).graphemes()",
			"new LayoutToken(grapheme, sourceOffset++, true)",
			"StyledTypewriter\n\t\t\t\t\t.flatten(fieldEnvelope(font, field))",
			"new LayoutToken(envelope.get(index), sourceOffset, false)"
		);
		check(
			!prelayout.contains("\u00a0") && !prelayout.contains("NBSP"),
			"Chat pre-layout must use real styled graphemes rather than NBSP approximation"
		);
		requireInOrder(
			overlay,
			"Component projected = snapshot.prelayout().padHudFrame(",
			"case WRAP -> prepareProjectedWrapped(",
			"case ELLIPSIS -> prepareProjectedEllipsized(",
			"snapshot.prelayout().materialize(font)",
			"Math.max(visible.alignmentWidth(), reserved.alignmentWidth())",
			"Math.max(visible.contentHeight(), reserved.contentHeight())"
		);
		check(
			overlay.contains("withoutPadding(logicalLines.get(index))")
				&& overlay.contains("ChatLineProjection.isPaddingStyle(style)"),
			"HUD wrapping must derive visible lines from the same hidden-suffix projection"
		);
		requireInOrder(
			chatEntry,
			"InsertAttempt attempt = insert(client, finalFrame, speaker)",
			"entry.beginProjected(client, initialFrame, layoutFrame)"
		);
		check(
			chatEntry.contains("chat.addServerSystemMessage(decoratedFrame)"),
			"the synchronous insert attempt must give vanilla the clean final frame for logging"
		);
		check(
			chatEntry.contains("this.speaker.apply(frame)")
				&& chatEntry.contains("new ChatLineProjection(")
				&& chatEntry.contains("this.speaker.apply(layoutFrame.orElseThrow())"),
			"GuiMessage content and measurement projection must travel through separate values"
		);
		check(
			chatEntry.contains("pixelTzzPro$replaceWithClean(this.entry, replacement)")
				&& chatEntry.contains("pixelTzzPro$removeMessage(this.entry)")
				&& chatEntry.contains("MutationResult.CAPABILITY_FAILURE")
				&& chatMixin.contains("MutationResult.ENTRY_GONE"),
			"Chat mutation must distinguish clean finalization, normal eviction, and capability failure"
		);
		check(
			chatProjection.contains(
				"style.withInsertion(PADDING_SENTINEL)"
			),
			"hidden measurement graphemes must preserve their real style and add only the private sentinel"
		);
		requireInOrder(
			chatProjection,
			"measurementEntry.splitLines(font, maxWidth)",
			"map(ChatLineProjection::withoutPadding)",
			"!isPaddingStyle(style)",
			"return HIDDEN_LINE"
		);
		check(
			chatProjection.contains("projected.size() > VANILLA_DISPLAY_LINE_LIMIT")
				&& chatProjection.contains("isHiddenLine(projected.get(index))"),
			"hidden reservations must be discarded before vanilla's 100-line cap can evict visible text"
		);
		requireInOrder(
			chatProjection,
			"boolean hasVisibleLine = projected.stream()",
			"projected.removeIf(ChatLineProjection::isHiddenLine)",
			"projected.subList(1, projected.size()).clear()"
		);
		check(
			playback.contains("return entry.finishProjected(client, finalComponent, finalLayout);")
				&& playback.contains("this.prepared.prelayout().padChatFrame("),
			"Chat completion must retain the exact resolved line projection while history content stays clean"
		);
		check(
			chatEntry.contains("MutationResult finishProjected(")
				&& chatEntry.contains("resetAge ? client.gui.hud.getGuiTicks()")
				&& chatEntry.contains("pixelTzzPro$setForceOpaque(this.entry, forceOpaque)"),
			"active Chat must stay opaque and release to a freshly aged vanilla history entry"
		);
		check(
			!playback.contains("MAX_FIELD_GATE_WAIT_NANOS")
				&& !playback.contains("lockLateFallback()")
				&& playback.contains("A client-side timeout must")
				&& playback.contains("never turn a valid late field"),
			"unresolved fields must wait at their boundary instead of using a speed-scaled local fallback"
		);
		check(
			!chatProjection.contains("allMessages")
				&& !chatProjection.contains("addServerSystemMessage"),
			"the measurement projection must have no route into vanilla content or history"
		);
		check(
			chatMixin.contains("IdentityHashMap<GuiMessage, ChatLineProjection>")
				&& chatMixin.contains("pixelTzzPro$replaceLineBlock(")
				&& chatMixin.contains("this.trimmedMessages.subList(first, last + 1).clear()")
				&& chatMixin.contains("pixelTzzPro$restoreScrollAnchor("),
			"a frame must replace only its exact vanilla history entry and display-line block"
		);
		check(
			chatMixin.contains("method = \"refreshTrimmedMessages\"")
				&& chatMixin.contains("method = \"forEachLine\"")
				&& chatMixin.contains("!ChatLineProjection.isHiddenLine(line.content())")
				&& chatMixin.contains("pixelTzzPro$keepActiveMessagesOpaque")
				&& chatMixin.contains("this.pixelTzzPro$forceOpaque.contains(line.parent())")
				&& chatMixin.contains("require = 0"),
			"vanilla-owned rebuilds must reapply projections while active lines remain opaque and hidden-only lines stay non-interactive"
		);
		check(
			chatMixin.contains("this.pixelTzzPro$forceOpaque.remove(previous);")
				&& chatMixin.contains(
					"this.pixelTzzPro$lineProjections.isEmpty()\n\t\t\t\t&& this.pixelTzzPro$forceOpaque.isEmpty()"
				)
				&& chatMixin.contains(
					"this.pixelTzzPro$forceOpaque.removeIf(message -> !live.contains(message))"
				),
			"evicted or failed Chat entries must not retain stale force-opaque identities"
		);
		check(
			chatMixin.contains("private void pixelTzzPro$ensureProjectionState()")
				&& chatMixin.contains("if (this.pixelTzzPro$lineProjections == null)")
				&& chatMixin.contains("if (this.pixelTzzPro$forceOpaque == null)")
				&& chatMixin.contains(
					"private IdentityHashMap<GuiMessage, ChatLineProjection>\n"
						+ "\t\tpixelTzzPro$lineProjections;"
				)
				&& chatMixin.contains(
					"private Set<GuiMessage> pixelTzzPro$forceOpaque;"
				)
				&& !chatMixin.contains("private final IdentityHashMap")
				&& !chatMixin.contains(
					"private final Set<GuiMessage> pixelTzzPro$forceOpaque"
				),
			"Mixin-owned Chat state must initialize lazily instead of relying on an unmerged constructor initializer"
		);
		int clearHook = chatMixin.indexOf(
			"private void pixelTzzPro$clearLineProjections("
		);
		int pruneHelper = chatMixin.indexOf(
			"private void pixelTzzPro$pruneLineProjections()"
		);
		check(
			clearHook >= 0
				&& pruneHelper > clearHook
				&& chatMixin.substring(clearHook, pruneHelper).contains(
					"this.pixelTzzPro$ensureProjectionState();"
				)
				&& chatMixin.substring(pruneHelper).contains(
					"this.pixelTzzPro$ensureProjectionState();"
				),
			"ordinary Chat insertion, vanilla rebuild and disconnect cleanup must tolerate pristine projection state"
		);
		check(
			!playback.contains("pixelTzzPro$refreshTrimmedMessages"),
			"normal animation frames must never rebuild all trimmed Chat lines"
		);
		check(
			playback.contains("case CONTINUE -> PlaybackDecision.RENDER")
				&& playbackPlan.contains(
					"channel == Channel.CHAT\n\t\t\t\t\t\t? Obscured.CONTINUE"
				)
				&& !playback.contains("instanceof ChatScreen"),
			"opening vanilla ChatScreen must keep the same live Chat projection advancing"
		);
		requireInOrder(
			playback,
			"Set<Integer> retainedChatOrdinals = chatOrdinals(plan)",
			"removeStaticChatEntriesExcept(",
			"retireForRefresh(client, previous, retainedChatOrdinals)"
		);
		requireInOrder(
			playback,
			"DynamicChatEntry previous = STATIC_CHAT.get(key)",
			"DynamicChatEntry rebound = previous.rebind(client, speaker)",
			"MutationResult result = rebound.finishClean(client, component)",
			"return;",
			"DynamicChatEntry.InsertAttempt attempt = DynamicChatEntry.insert("
		);
		check(
			playback.contains("result == MutationResult.UPDATED")
				&& playback.contains("result == MutationResult.CAPABILITY_FAILURE")
				&& playback.contains("Never resurrect it during a refresh/finalization pass"),
			"stable Chat keys must update in place, ignore normal eviction, and downgrade only on capability failure"
		);
		int startMethod = playback.indexOf("private static void start(");
		int queuesMethod = playback.indexOf("private static void startQueues", startMethod);
		check(startMethod >= 0 && queuesMethod > startMethod, "queued text start path is missing");
		String queuedStart = playback.substring(startMethod, queuesMethod);
		requireInOrder(
			queuedStart,
			"PreparedText current = prepare(",
			"pending.instance.fields",
			"if (!current.unresolvedSlots().isEmpty())",
			"enqueue(pending)",
			"current.finalComponent()"
		);
		check(
			!queuedStart.contains("pending.prepared.finalComponent()")
				&& queuedStart.replaceAll("\\s+", " ").contains("pending.node, current,"),
			"a queued node must refresh FINISH fields before static or animated playback"
		);
		requireInOrder(
			clientEntrypoint,
			"if (!messagePhysicalConnectionActive)",
			"ClientMessagePlayback.reset(client)",
			"ClientPlayConnectionEvents.DISCONNECT.register",
			"messagePhysicalConnectionActive = false"
		);
	}

	private static String source(final String path) throws IOException {
		return Files.readString(Path.of(path));
	}

	private static void requireInOrder(final String source, final String... fragments) {
		int cursor = -1;
		for (String fragment : fragments) {
			int next = source.indexOf(fragment, cursor + 1);
			check(next >= 0, "missing source contract fragment: " + fragment);
			cursor = next;
		}
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
