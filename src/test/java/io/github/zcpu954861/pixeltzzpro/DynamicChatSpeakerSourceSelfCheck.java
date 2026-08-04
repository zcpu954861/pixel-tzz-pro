package io.github.zcpu954861.pixeltzzpro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Narrow source-contract check for the client-only vanilla chat integration.
 *
 * <p>The ordinary test source set cannot link Minecraft's client-only classes after Loom splits
 * the environment source sets. This check therefore guards the small set of safety properties
 * that matter at that boundary, while {@code compileClientJava} remains the type/API check.</p>
 */
public final class DynamicChatSpeakerSourceSelfCheck {
	private static final Path ENTRY_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/DynamicChatEntry.java"
	);
	private static final Path PLAYBACK_SOURCE = Path.of(
		"src/client/java/io/github/zcpu954861/pixeltzzpro/client/message/ClientMessagePlayback.java"
	);

	private DynamicChatSpeakerSourceSelfCheck() {
	}

	public static void main(final String[] args) throws IOException {
		String entry = normalized(Files.readString(ENTRY_SOURCE));
		String playback = normalized(Files.readString(PLAYBACK_SOURCE));

		check(
			entry.contains("chat.addServerSystemMessage(decoratedFrame)"),
			"dynamic chat must begin as a real vanilla server-system entry"
		);
		check(
			!entry.contains("addPlayerMessage("),
			"message cues must never forge a signed/player chat entry"
		);
		check(
			entry.contains(
				"speaker.kind() == SpeakerKind.SYSTEM || speaker.kind() == SpeakerKind.NARRATOR) { return new SpeakerDecoration(Optional.empty(), Optional.empty())"
			),
			"system and narrator speakers must remain undecorated and avatar-free"
		);
		check(
			entry.contains(
				"speaker.kind() == SpeakerKind.ENTITY) { return new SpeakerDecoration(Optional.empty(), speaker.displayName())"
			),
			"entity speakers must carry only their verified visible name"
		);
		check(
			entry.contains("new PlayerSprite(value, true)"),
			"player speakers must use vanilla PlayerSprite with the hat layer enabled"
		);
		check(
			entry.contains("Component.translatable(\"chat.type.text\", identity, frame)"),
			"identity metadata and the animated body must use vanilla chat decoration"
		);
		check(
			entry.contains(
				"message.signature() == null && message.source() == GuiMessageSource.SYSTEM_SERVER"
			),
			"tracked and replaced entries must stay unsigned server-system messages"
		);
		check(
			entry.contains(
				"this.entry.signature(), this.entry.source(), this.entry.tag()"
			),
			"in-place frames must preserve the original vanilla source and tag metadata"
		);
		check(
			playback.contains(
				"DynamicChatEntry.insertProjected( client, initial, playing.prepared.finalComponent(), pending.node.speaker(), initialLayout )"
			),
			"animated chat insertion must receive final log text, its initial frame, projection, and resolved speaker"
		);
		check(
			playback.contains(
				"DynamicChatEntry.insertStatic(client, component, speaker)"
			),
			"static fallback insertion must receive the same resolved speaker"
		);

		System.out.println("DYNAMIC_CHAT_SPEAKER_SOURCE_SELF_CHECK=PASS");
	}

	private static String normalized(final String source) {
		return source.replaceAll("\\s+", " ").trim();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
