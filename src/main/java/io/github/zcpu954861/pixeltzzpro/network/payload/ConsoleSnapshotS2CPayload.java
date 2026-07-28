package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.payload.TargetSnapshotS2CPayload.Target;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bounded, server-authored model for the context-aware administrator console.
 */
public record ConsoleSnapshotS2CPayload(
	int protocolVersion,
	long requestSequence,
	long stateRevision,
	long definitionGeneration,
	String status,
	String message,
	Optional<Identifier> gameId,
	Optional<Identifier> phaseId,
	String gameNameJson,
	String phaseNameJson,
	boolean adminEligible,
	boolean currentPlayerHost,
	Optional<HostInfo> host,
	List<Target> players,
	List<ActionEntry> actions,
	Optional<ActiveFlowSummary> activeFlow,
	Optional<ActiveFlowSummary> recentFlow,
	long auditEventCount,
	List<Diagnostic> diagnostics
) implements CustomPacketPayload {
	public static final int MAX_STATUS_LENGTH = 64;
	public static final int MAX_MESSAGE_LENGTH = 2_048;
	public static final int MAX_TEXT_LENGTH = 8_192;
	public static final int MAX_PLAYER_NAME_LENGTH = 64;
	public static final int MAX_OPERATION_TYPE_LENGTH = 64;
	public static final int MAX_SECTION_LENGTH = 64;
	public static final int MAX_COLOR_LENGTH = 16;
	public static final int MAX_TARGET_MODE_LENGTH = 32;
	public static final int MAX_FLOW_STATUS_LENGTH = 32;
	public static final int MAX_MEMBER_STATUS_LENGTH = 32;
	public static final int MAX_NODE_ID_LENGTH = 128;
	public static final int MAX_ACTIONS = 128;
	public static final int MAX_MEMBERS = 64;
	public static final int MAX_DIAGNOSTICS = 50;
	public static final Type<ConsoleSnapshotS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("console_snapshot_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleSnapshotS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(ConsoleSnapshotS2CPayload::write, ConsoleSnapshotS2CPayload::new);

	public ConsoleSnapshotS2CPayload {
		if (
			protocolVersion < 0
				|| requestSequence < 0L
				|| stateRevision < 0L
				|| definitionGeneration < 0L
				|| auditEventCount < 0L
		) {
			throw new IllegalArgumentException("console counters must be non-negative");
		}
		status = local(status, MAX_STATUS_LENGTH, "status");
		message = requireBounded(message, MAX_MESSAGE_LENGTH, "message");
		gameId = Objects.requireNonNull(gameId, "gameId");
		phaseId = Objects.requireNonNull(phaseId, "phaseId");
		if (gameId.isPresent() != phaseId.isPresent()) {
			throw new IllegalArgumentException("gameId and phaseId must both be present or absent");
		}
		gameNameJson = requireBounded(gameNameJson, MAX_TEXT_LENGTH, "gameNameJson");
		phaseNameJson = requireBounded(phaseNameJson, MAX_TEXT_LENGTH, "phaseNameJson");
		host = Objects.requireNonNull(host, "host");
		players = boundedCopy(players, MAX_MEMBERS, "players");
		if (players.stream().map(Target::playerId).distinct().count() != players.size()) {
			throw new IllegalArgumentException("players contains duplicate UUIDs");
		}
		actions = boundedCopy(actions, MAX_ACTIONS, "actions");
		activeFlow = Objects.requireNonNull(activeFlow, "activeFlow");
		recentFlow = Objects.requireNonNull(recentFlow, "recentFlow");
		diagnostics = boundedCopy(diagnostics, MAX_DIAGNOSTICS, "diagnostics");
	}

	private ConsoleSnapshotS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readVarLong(),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readUtf(MAX_STATUS_LENGTH),
			buffer.readUtf(MAX_MESSAGE_LENGTH),
			readOptionalIdentifier(buffer, "gameId"),
			readOptionalIdentifier(buffer, "phaseId"),
			buffer.readUtf(MAX_TEXT_LENGTH),
			buffer.readUtf(MAX_TEXT_LENGTH),
			buffer.readBoolean(),
			buffer.readBoolean(),
			readOptional(buffer, HostInfo::read),
			readList(buffer, MAX_MEMBERS, "players", TargetSnapshotS2CPayload.Target::read),
			readList(buffer, MAX_ACTIONS, "actions", ActionEntry::read),
			readOptional(buffer, ActiveFlowSummary::read),
			readOptional(buffer, ActiveFlowSummary::read),
			buffer.readVarLong(),
			readList(buffer, MAX_DIAGNOSTICS, "diagnostics", Diagnostic::read)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.definitionGeneration);
		buffer.writeUtf(this.status, MAX_STATUS_LENGTH);
		buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
		writeOptionalIdentifier(buffer, this.gameId);
		writeOptionalIdentifier(buffer, this.phaseId);
		buffer.writeUtf(this.gameNameJson, MAX_TEXT_LENGTH);
		buffer.writeUtf(this.phaseNameJson, MAX_TEXT_LENGTH);
		buffer.writeBoolean(this.adminEligible);
		buffer.writeBoolean(this.currentPlayerHost);
		writeOptional(buffer, this.host, HostInfo::write);
		writeList(buffer, this.players, TargetSnapshotS2CPayload.Target::write);
		writeList(buffer, this.actions, ActionEntry::write);
		writeOptional(buffer, this.activeFlow, ActiveFlowSummary::write);
		writeOptional(buffer, this.recentFlow, ActiveFlowSummary::write);
		buffer.writeVarLong(this.auditEventCount);
		writeList(buffer, this.diagnostics, Diagnostic::write);
	}

	@Override
	public Type<ConsoleSnapshotS2CPayload> type() {
		return TYPE;
	}

	public record HostInfo(UUID playerId, String name, boolean online) {
		public HostInfo {
			playerId = Objects.requireNonNull(playerId, "playerId");
			name = requireBounded(name, MAX_PLAYER_NAME_LENGTH, "host.name");
			if (name.isBlank()) {
				throw new IllegalArgumentException("host.name cannot be blank");
			}
		}

		private static HostInfo read(final RegistryFriendlyByteBuf buffer) {
			return new HostInfo(
				buffer.readUUID(),
				buffer.readUtf(MAX_PLAYER_NAME_LENGTH),
				buffer.readBoolean()
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final HostInfo value) {
			buffer.writeUUID(value.playerId);
			buffer.writeUtf(value.name, MAX_PLAYER_NAME_LENGTH);
			buffer.writeBoolean(value.online);
		}
	}

	public record ActionEntry(
		String operationType,
		Identifier operationId,
		String section,
		int order,
		String labelJson,
		String descriptionJson,
		String color,
		boolean enabled,
		String disabledReasonJson,
		String targetMode,
		int minimumTargets,
		int maximumTargets,
		boolean requiresConfirmation
	) {
		public ActionEntry {
			operationType = local(
				operationType,
				MAX_OPERATION_TYPE_LENGTH,
				"action.operationType"
			);
			operationId = requireIdentifier(operationId, "action.operationId");
			section = local(section, MAX_SECTION_LENGTH, "action.section");
			if (order < -10_000 || order > 10_000) {
				throw new IllegalArgumentException("action.order must be within -10000..10000");
			}
			labelJson = requireBounded(labelJson, MAX_TEXT_LENGTH, "action.labelJson");
			descriptionJson = requireBounded(
				descriptionJson,
				MAX_TEXT_LENGTH,
				"action.descriptionJson"
			);
			color = requireBounded(color, MAX_COLOR_LENGTH, "action.color");
			disabledReasonJson = requireBounded(
				disabledReasonJson,
				MAX_TEXT_LENGTH,
				"action.disabledReasonJson"
			);
			targetMode = local(targetMode, MAX_TARGET_MODE_LENGTH, "action.targetMode");
			if (
				minimumTargets < 0
					|| maximumTargets < minimumTargets
					|| maximumTargets > MAX_MEMBERS
			) {
				throw new IllegalArgumentException("action target bounds are invalid");
			}
		}

		private static ActionEntry read(final RegistryFriendlyByteBuf buffer) {
			return new ActionEntry(
				buffer.readUtf(MAX_OPERATION_TYPE_LENGTH),
				readIdentifier(buffer, "action.operationId"),
				buffer.readUtf(MAX_SECTION_LENGTH),
				buffer.readVarInt(),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readUtf(MAX_COLOR_LENGTH),
				buffer.readBoolean(),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readUtf(MAX_TARGET_MODE_LENGTH),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readBoolean()
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final ActionEntry value
		) {
			buffer.writeUtf(value.operationType, MAX_OPERATION_TYPE_LENGTH);
			writeIdentifier(buffer, value.operationId);
			buffer.writeUtf(value.section, MAX_SECTION_LENGTH);
			buffer.writeVarInt(value.order);
			buffer.writeUtf(value.labelJson, MAX_TEXT_LENGTH);
			buffer.writeUtf(value.descriptionJson, MAX_TEXT_LENGTH);
			buffer.writeUtf(value.color, MAX_COLOR_LENGTH);
			buffer.writeBoolean(value.enabled);
			buffer.writeUtf(value.disabledReasonJson, MAX_TEXT_LENGTH);
			buffer.writeUtf(value.targetMode, MAX_TARGET_MODE_LENGTH);
			buffer.writeVarInt(value.minimumTargets);
			buffer.writeVarInt(value.maximumTargets);
			buffer.writeBoolean(value.requiresConfirmation);
		}
	}

	public record ActiveFlowSummary(
		UUID instanceId,
		Identifier flowId,
		String flowNameJson,
		String status,
		Target initiatedBy,
		long createdAtEpochMillis,
		int completed,
		int total,
		long instanceRevision,
		List<MemberSummary> members,
		boolean callbackFailed,
		boolean resourceBlocked
	) {
		public ActiveFlowSummary {
			instanceId = Objects.requireNonNull(instanceId, "instanceId");
			flowId = requireIdentifier(flowId, "flow.flowId");
			flowNameJson = requireBounded(flowNameJson, MAX_TEXT_LENGTH, "flow.flowNameJson");
			status = local(status, MAX_FLOW_STATUS_LENGTH, "flow.status");
			initiatedBy = Objects.requireNonNull(initiatedBy, "flow.initiatedBy");
			if (
				createdAtEpochMillis < 0L
					|| completed < 0
					|| total < 0
					|| completed > total
					|| instanceRevision < 0L
			) {
				throw new IllegalArgumentException("active-flow counters are invalid");
			}
			members = boundedCopy(members, MAX_MEMBERS, "flow.members");
		}

		private static ActiveFlowSummary read(final RegistryFriendlyByteBuf buffer) {
			return new ActiveFlowSummary(
				buffer.readUUID(),
				readIdentifier(buffer, "flow.flowId"),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readUtf(MAX_FLOW_STATUS_LENGTH),
				TargetSnapshotS2CPayload.Target.read(buffer),
				buffer.readLong(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readLong(),
				readList(buffer, MAX_MEMBERS, "flow.members", MemberSummary::read),
				buffer.readBoolean(),
				buffer.readBoolean()
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final ActiveFlowSummary value
		) {
			buffer.writeUUID(value.instanceId);
			writeIdentifier(buffer, value.flowId);
			buffer.writeUtf(value.flowNameJson, MAX_TEXT_LENGTH);
			buffer.writeUtf(value.status, MAX_FLOW_STATUS_LENGTH);
			TargetSnapshotS2CPayload.Target.write(buffer, value.initiatedBy);
			buffer.writeLong(value.createdAtEpochMillis);
			buffer.writeVarInt(value.completed);
			buffer.writeVarInt(value.total);
			buffer.writeLong(value.instanceRevision);
			writeList(buffer, value.members, MemberSummary::write);
			buffer.writeBoolean(value.callbackFailed);
			buffer.writeBoolean(value.resourceBlocked);
		}
	}

	public record MemberSummary(
		Target player,
		String status,
		String currentNodeId,
		String currentNodeNameJson,
		boolean blocked
	) {
		public MemberSummary {
			player = Objects.requireNonNull(player, "member.player");
			status = local(status, MAX_MEMBER_STATUS_LENGTH, "member.status");
			currentNodeId = requireBounded(
				currentNodeId,
				MAX_NODE_ID_LENGTH,
				"member.currentNodeId"
			);
			currentNodeNameJson = requireBounded(
				currentNodeNameJson,
				MAX_TEXT_LENGTH,
				"member.currentNodeNameJson"
			);
		}

		private static MemberSummary read(final RegistryFriendlyByteBuf buffer) {
			return new MemberSummary(
				TargetSnapshotS2CPayload.Target.read(buffer),
				buffer.readUtf(MAX_MEMBER_STATUS_LENGTH),
				buffer.readUtf(MAX_NODE_ID_LENGTH),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readBoolean()
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final MemberSummary value
		) {
			TargetSnapshotS2CPayload.Target.write(buffer, value.player);
			buffer.writeUtf(value.status, MAX_MEMBER_STATUS_LENGTH);
			buffer.writeUtf(value.currentNodeId, MAX_NODE_ID_LENGTH);
			buffer.writeUtf(value.currentNodeNameJson, MAX_TEXT_LENGTH);
			buffer.writeBoolean(value.blocked);
		}
	}

	public record Diagnostic(String code, String message) {
		public Diagnostic {
			code = local(code, MAX_STATUS_LENGTH, "diagnostic.code");
			message = requireBounded(message, MAX_MESSAGE_LENGTH, "diagnostic.message");
		}

		private static Diagnostic read(final RegistryFriendlyByteBuf buffer) {
			return new Diagnostic(
				buffer.readUtf(MAX_STATUS_LENGTH),
				buffer.readUtf(MAX_MESSAGE_LENGTH)
			);
		}

		private static void write(final RegistryFriendlyByteBuf buffer, final Diagnostic value) {
			buffer.writeUtf(value.code, MAX_STATUS_LENGTH);
			buffer.writeUtf(value.message, MAX_MESSAGE_LENGTH);
		}
	}

	private static String local(
		final String value,
		final int maximumLength,
		final String field
	) {
		String bounded = requireBounded(value, maximumLength, field);
		if (
			bounded.isBlank()
				|| !bounded.equals(bounded.toLowerCase(Locale.ROOT))
				|| !bounded.matches("[a-z0-9_.-]+")
		) {
			throw new IllegalArgumentException(field + " must match [a-z0-9_.-]+");
		}
		return bounded;
	}

	private static <T> List<T> boundedCopy(
		final List<T> values,
		final int maximum,
		final String field
	) {
		Objects.requireNonNull(values, field);
		if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException(field + " exceeds its bounded size");
		}
		return List.copyOf(values);
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
		value.ifPresent(identifier -> writeIdentifier(buffer, identifier));
	}

	private static <T> Optional<T> readOptional(
		final RegistryFriendlyByteBuf buffer,
		final java.util.function.Function<RegistryFriendlyByteBuf, T> reader
	) {
		return buffer.readBoolean() ? Optional.of(reader.apply(buffer)) : Optional.empty();
	}

	private static <T> void writeOptional(
		final RegistryFriendlyByteBuf buffer,
		final Optional<T> value,
		final java.util.function.BiConsumer<RegistryFriendlyByteBuf, T> writer
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(present -> writer.accept(buffer, present));
	}
}
