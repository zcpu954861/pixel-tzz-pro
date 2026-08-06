package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CallbackDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CheckpointDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.CountdownPurpose;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.DisconnectPolicies;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenDefinition;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.FrozenParticipant;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Instance;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.LaunchPlan;
import io.github.zcpu954861.pixeltzzpro.state.PersistedCountdown.Restrictions;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.FrozenDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class CountdownSelfCheckFixtures {
	public static final UUID GAME_INSTANCE_ID =
		UUID.fromString("00000000-0000-0000-0000-000000003c01");
	public static final UUID COUNTDOWN_INSTANCE_ID =
		UUID.fromString("00000000-0000-0000-0000-000000003c02");
	public static final UUID HOST_ID =
		UUID.fromString("00000000-0000-0000-0000-000000003c03");
	public static final UUID PLAYER_B =
		UUID.fromString("00000000-0000-0000-0000-000000003c04");
	public static final UUID PLAYER_C =
		UUID.fromString("00000000-0000-0000-0000-000000003c05");
	public static final Identifier DEFINITION_ID = Identifier.parse("pixel_tzz:test/opening");
	public static final Identifier CALLBACK_FUNCTION = Identifier.parse("pixel_tzz:test/countdown_callback");

	private CountdownSelfCheckFixtures() {
	}

	public static CountdownAuthority.StartRequest request(
		final DisconnectPolicies policies,
		final Restrictions restrictions,
		final List<CheckpointDefinition> checkpoints,
		final List<CallbackDefinition> callbacks
	) {
		return new CountdownAuthority.StartRequest(
			GAME_INSTANCE_ID,
			COUNTDOWN_INSTANCE_ID,
			CountdownPurpose.OPENING,
			new FrozenDefinition(
				DEFINITION_ID,
				7L,
				FrozenDocument.of("{\"format_version\":1,\"duration\":\"10s\"}")
			),
			new LaunchPlan(
				Identifier.parse("pixel_tzz:test/approval"),
				UUID.fromString("00000000-0000-0000-0000-000000003c06"),
				UUID.fromString("00000000-0000-0000-0000-000000003c07"),
				List.of(PLAYER_B, PLAYER_C),
				Optional.empty()
			),
			200L,
			List.of(
				new FrozenParticipant(PLAYER_B, "PlayerB", true),
				new FrozenParticipant(PLAYER_C, "PlayerC", true)
			),
			Optional.of(HOST_ID),
			policies,
			restrictions,
			checkpoints,
			callbacks,
			1_000L
		);
	}

	public static Instance start() {
		return CountdownAuthority.start(
			request(DisconnectPolicies.defaults(), Restrictions.defaults(), List.of(), List.of())
		).instance().orElseThrow();
	}
}
