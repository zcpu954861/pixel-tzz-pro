package io.github.zcpu954861.pixeltzzpro.lifecycle;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * Stable persisted phase identifiers. This milestone deliberately exposes no transitions yet.
 */
public enum GamePhase implements StringRepresentable {
	IDLE("idle"),
	INITIALIZING("initializing"),
	INITIALIZATION_LOCKED("initialization_locked"),
	READY("ready"),
	AWAITING_APPROVAL("awaiting_approval"),
	COUNTDOWN("countdown"),
	RUNNING("running"),
	ENDED("ended");

	public static final Codec<GamePhase> CODEC = StringRepresentable.fromEnum(GamePhase::values);

	private final String serializedName;

	GamePhase(final String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}

	public static Optional<GamePhase> bySerializedName(final String serializedName) {
		return Arrays.stream(values()).filter(phase -> phase.serializedName.equals(serializedName)).findFirst();
	}
}
