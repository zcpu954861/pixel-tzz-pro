package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Action;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Boundary;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Identity;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Mode;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Scenario;
import io.github.zcpu954861.pixeltzzpro.network.HudPreviewContract.Surface;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bounded host-only development-preview intent.
 *
 * <p>Every simulation knob is a closed enum or an already-registered definition identifier. The
 * payload deliberately has no selector, score holder, NBT/storage path, function, free-form text,
 * player UUID target or raw value field.</p>
 */
public record HudPreviewRequestC2SPayload(
	int protocolVersion,
	long requestSequence,
	UUID previewId,
	Action action,
	Surface surface,
	Scenario scenario,
	Identity identity,
	Mode mode,
	Boundary boundary,
	Optional<Identifier> phaseId,
	Optional<Identifier> taskId,
	Optional<Identifier> roleId,
	Optional<Identifier> teamId,
	Optional<Identifier> lifeStateId
) implements CustomPacketPayload {
	public static final Type<HudPreviewRequestC2SPayload> TYPE = new Type<>(
		PixelTzzPro.id("hud_preview_request_c2s")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, HudPreviewRequestC2SPayload> STREAM_CODEC =
		CustomPacketPayload.codec(HudPreviewRequestC2SPayload::write, HudPreviewRequestC2SPayload::new);

	public HudPreviewRequestC2SPayload {
		if (protocolVersion < 0 || requestSequence <= 0L) {
			throw new IllegalArgumentException("HUD preview request counters are invalid");
		}
		previewId = Objects.requireNonNull(previewId, "previewId");
		action = Objects.requireNonNull(action, "action");
		surface = Objects.requireNonNull(surface, "surface");
		scenario = Objects.requireNonNull(scenario, "scenario");
		identity = Objects.requireNonNull(identity, "identity");
		mode = Objects.requireNonNull(mode, "mode");
		boundary = Objects.requireNonNull(boundary, "boundary");
		phaseId = bounded(phaseId, "phaseId");
		taskId = bounded(taskId, "taskId");
		roleId = bounded(roleId, "roleId");
		teamId = bounded(teamId, "teamId");
		lifeStateId = bounded(lifeStateId, "lifeStateId");
		if (!HudPreviewContract.scenarioAllowed(surface, scenario)) {
			throw new IllegalArgumentException("HUD preview scenario does not match its surface");
		}
		if (
			taskId.isPresent()
				&& (surface == Surface.COUNTDOWN || scenario == Scenario.SETUP)
		) {
			throw new IllegalArgumentException("HUD preview task is unavailable in this scenario");
		}
	}

	private HudPreviewRequestC2SPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readVarLong(),
			buffer.readUUID(),
			HudPreviewContract.byOrdinal(Action.class, buffer.readVarInt(), "preview action"),
			HudPreviewContract.byOrdinal(Surface.class, buffer.readVarInt(), "preview surface"),
			HudPreviewContract.byOrdinal(Scenario.class, buffer.readVarInt(), "preview scenario"),
			HudPreviewContract.byOrdinal(Identity.class, buffer.readVarInt(), "preview identity"),
			HudPreviewContract.byOrdinal(Mode.class, buffer.readVarInt(), "preview mode"),
			HudPreviewContract.byOrdinal(Boundary.class, buffer.readVarInt(), "preview boundary"),
			readOptionalIdentifier(buffer, "phaseId"),
			readOptionalIdentifier(buffer, "taskId"),
			readOptionalIdentifier(buffer, "roleId"),
			readOptionalIdentifier(buffer, "teamId"),
			readOptionalIdentifier(buffer, "lifeStateId")
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeUUID(this.previewId);
		buffer.writeVarInt(this.action.ordinal());
		buffer.writeVarInt(this.surface.ordinal());
		buffer.writeVarInt(this.scenario.ordinal());
		buffer.writeVarInt(this.identity.ordinal());
		buffer.writeVarInt(this.mode.ordinal());
		buffer.writeVarInt(this.boundary.ordinal());
		writeOptionalIdentifier(buffer, this.phaseId);
		writeOptionalIdentifier(buffer, this.taskId);
		writeOptionalIdentifier(buffer, this.roleId);
		writeOptionalIdentifier(buffer, this.teamId);
		writeOptionalIdentifier(buffer, this.lifeStateId);
	}

	@Override
	public Type<HudPreviewRequestC2SPayload> type() {
		return TYPE;
	}

	private static Optional<Identifier> bounded(
		final Optional<Identifier> value,
		final String field
	) {
		return Objects.requireNonNull(value, field).map(id -> requireIdentifier(id, field));
	}

	private static Optional<Identifier> readOptionalIdentifier(
		final RegistryFriendlyByteBuf buffer,
		final String field
	) {
		return buffer.readBoolean()
			? Optional.of(readIdentifier(buffer, field))
			: Optional.empty();
	}

	private static void writeOptionalIdentifier(
		final RegistryFriendlyByteBuf buffer,
		final Optional<Identifier> value
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(id -> writeIdentifier(buffer, id));
	}
}
