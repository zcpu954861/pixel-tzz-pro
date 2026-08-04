package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerKind;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerSpec;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Regression checks for server-authoritative registered-entity speaker resolution. */
public final class MessageRegisteredEntitySpeakerSelfCheck {
	private MessageRegisteredEntitySpeakerSelfCheck() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Identifier villagerId = Identifier.parse("minecraft:villager");
		String registeredName = BuiltInRegistries.ENTITY_TYPE.get(villagerId)
			.orElseThrow()
			.value()
			.getDescription()
			.getString();
		ResolvedSpeaker villager = MessageServerRuntime.resolveRegisteredEntitySpeaker(
			registeredEntity(villagerId)
		);
		check(
			villager.kind()
				== io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SpeakerKind.ENTITY,
			"a registered entity type must retain entity decoration"
		);
		check(villager.profileId().isEmpty(), "generic entities must not forge a player UUID");
		check(
			villager.displayName().orElseThrow().equals(registeredName),
			"registered entities must use EntityType's authoritative visible description"
		);
		check(
			!villager.displayName().orElseThrow().equals(villagerId.toString()),
			"the raw entity identifier must never become the visible speaker name"
		);

		Identifier missingId = Identifier.parse("pixel_tzz:not_a_registered_entity_type");
		ResolvedSpeaker missing = MessageServerRuntime.resolveRegisteredEntitySpeaker(
			registeredEntity(missingId)
		);
		check(
			missing.equals(ResolvedSpeaker.system()),
			"an unknown entity type must fail closed to anonymous system"
		);

		expectFailure(
			() -> new ResolvedSpeaker(
				io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SpeakerKind.ENTITY,
				Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
				Optional.of(registeredName)
			),
			"the wire model must reject a player profile on a generic entity"
		);

		System.out.println("MESSAGE_REGISTERED_ENTITY_SPEAKER_SELF_CHECK=PASS");
	}

	private static SpeakerSpec registeredEntity(final Identifier entityId) {
		return new SpeakerSpec(
			SpeakerKind.REGISTERED_ENTITY,
			Optional.empty(),
			Optional.of(entityId)
		);
	}

	private static void expectFailure(final Runnable action, final String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
