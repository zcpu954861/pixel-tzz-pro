package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readList;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireBounded;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.requireIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeIdentifier;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeList;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import java.util.Comparator;
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
 * Authoritative, bounded player list for one target-selection screen instance.
 */
public record TargetSnapshotS2CPayload(
	int protocolVersion,
	long requestSequence,
	String operationType,
	Identifier operationId,
	long stateRevision,
	long definitionGeneration,
	String targetMode,
	int minimumTargets,
	int maximumTargets,
	List<Target> targets
) implements CustomPacketPayload {
	public static final int MAX_OPERATION_TYPE_LENGTH = 64;
	public static final int MAX_TARGET_MODE_LENGTH = 32;
	public static final int MAX_TARGETS = 64;
	public static final int MAX_PLAYER_NAME_LENGTH = 64;
	public static final int MAX_TEXT_LENGTH = 8_192;
	public static final int MAX_COLOR_LENGTH = 16;
	public static final int MAX_FLOW_STATUS_LENGTH = 32;
	public static final int MAX_COMPLETION_STATUSES = 8;
	public static final int MAX_REASON_LENGTH = 1_024;
	public static final Type<TargetSnapshotS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("target_snapshot_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, TargetSnapshotS2CPayload> STREAM_CODEC =
		CustomPacketPayload.codec(TargetSnapshotS2CPayload::write, TargetSnapshotS2CPayload::new);

	public TargetSnapshotS2CPayload {
		if (
			protocolVersion < 0
				|| requestSequence < 0L
				|| stateRevision < 0L
				|| definitionGeneration < 0L
		) {
			throw new IllegalArgumentException("target snapshot counters must be non-negative");
		}
		operationType = local(operationType, MAX_OPERATION_TYPE_LENGTH, "operationType");
		operationId = requireIdentifier(operationId, "operationId");
		targetMode = local(targetMode, MAX_TARGET_MODE_LENGTH, "targetMode");
		if (
			minimumTargets < 0
				|| maximumTargets < minimumTargets
				|| maximumTargets > MAX_TARGETS
		) {
			throw new IllegalArgumentException("target bounds are invalid");
		}
		Objects.requireNonNull(targets, "targets");
		if (targets.size() > MAX_TARGETS || targets.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("target snapshot exceeds " + MAX_TARGETS);
		}
		List<Target> sorted = targets.stream()
			.sorted(Comparator.comparing(Target::playerId))
			.toList();
		if (sorted.stream().map(Target::playerId).distinct().count() != sorted.size()) {
			throw new IllegalArgumentException("target snapshot contains duplicate UUIDs");
		}
		targets = sorted;
	}

	private TargetSnapshotS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readVarLong(),
			buffer.readUtf(MAX_OPERATION_TYPE_LENGTH),
			readIdentifier(buffer, "operationId"),
			buffer.readLong(),
			buffer.readLong(),
			buffer.readUtf(MAX_TARGET_MODE_LENGTH),
			buffer.readVarInt(),
			buffer.readVarInt(),
			readList(buffer, MAX_TARGETS, "targets", Target::read)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarLong(this.requestSequence);
		buffer.writeUtf(this.operationType, MAX_OPERATION_TYPE_LENGTH);
		writeIdentifier(buffer, this.operationId);
		buffer.writeLong(this.stateRevision);
		buffer.writeLong(this.definitionGeneration);
		buffer.writeUtf(this.targetMode, MAX_TARGET_MODE_LENGTH);
		buffer.writeVarInt(this.minimumTargets);
		buffer.writeVarInt(this.maximumTargets);
		writeList(buffer, this.targets, Target::write);
	}

	@Override
	public Type<TargetSnapshotS2CPayload> type() {
		return TYPE;
	}

	public record Target(
		UUID playerId,
		String name,
		boolean online,
		Identifier roleId,
		String roleNameJson,
		String roleColor,
		Optional<Identifier> teamId,
		String teamNameJson,
		Identifier lifeStateId,
		String lifeStateNameJson,
		String flowStatus,
		List<FlowCompletionStatus> completionStatuses,
		boolean eligible,
		String ineligibleReason
	) {
		public Target {
			playerId = Objects.requireNonNull(playerId, "playerId");
			name = requireBounded(name, MAX_PLAYER_NAME_LENGTH, "target.name");
			if (name.isBlank()) {
				throw new IllegalArgumentException("target.name cannot be blank");
			}
			roleId = requireIdentifier(roleId, "target.roleId");
			roleNameJson = requireBounded(roleNameJson, MAX_TEXT_LENGTH, "target.roleNameJson");
			roleColor = requireBounded(roleColor, MAX_COLOR_LENGTH, "target.roleColor");
			teamId = Objects.requireNonNull(teamId, "target.teamId");
			teamNameJson = requireBounded(teamNameJson, MAX_TEXT_LENGTH, "target.teamNameJson");
			lifeStateId = requireIdentifier(lifeStateId, "target.lifeStateId");
			lifeStateNameJson = requireBounded(
				lifeStateNameJson,
				MAX_TEXT_LENGTH,
				"target.lifeStateNameJson"
			);
			flowStatus = local(flowStatus, MAX_FLOW_STATUS_LENGTH, "target.flowStatus");
			Objects.requireNonNull(completionStatuses, "target.completionStatuses");
			if (
				completionStatuses.size() > MAX_COMPLETION_STATUSES
					|| completionStatuses.stream().anyMatch(Objects::isNull)
			) {
				throw new IllegalArgumentException(
					"target completion status count exceeds " + MAX_COMPLETION_STATUSES
				);
			}
			completionStatuses = completionStatuses.stream()
				.sorted(Comparator.comparing(FlowCompletionStatus::flowId))
				.toList();
			if (
				completionStatuses.stream()
					.map(FlowCompletionStatus::flowId)
					.distinct()
					.count() != completionStatuses.size()
			) {
				throw new IllegalArgumentException("target completion statuses contain duplicate flows");
			}
			ineligibleReason = requireBounded(
				ineligibleReason,
				MAX_REASON_LENGTH,
				"target.ineligibleReason"
			);
			if (eligible && !ineligibleReason.isEmpty()) {
				throw new IllegalArgumentException("eligible target cannot have an ineligible reason");
			}
		}

		static Target read(final RegistryFriendlyByteBuf buffer) {
			return new Target(
				buffer.readUUID(),
				buffer.readUtf(MAX_PLAYER_NAME_LENGTH),
				buffer.readBoolean(),
				readIdentifier(buffer, "target.roleId"),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readUtf(MAX_COLOR_LENGTH),
				readOptionalIdentifier(buffer, "target.teamId"),
				buffer.readUtf(MAX_TEXT_LENGTH),
				readIdentifier(buffer, "target.lifeStateId"),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readUtf(MAX_FLOW_STATUS_LENGTH),
				readList(
					buffer,
					MAX_COMPLETION_STATUSES,
					"target.completionStatuses",
					FlowCompletionStatus::read
				),
				buffer.readBoolean(),
				buffer.readUtf(MAX_REASON_LENGTH)
			);
		}

		static void write(final RegistryFriendlyByteBuf buffer, final Target value) {
			buffer.writeUUID(value.playerId);
			buffer.writeUtf(value.name, MAX_PLAYER_NAME_LENGTH);
			buffer.writeBoolean(value.online);
			writeIdentifier(buffer, value.roleId);
			buffer.writeUtf(value.roleNameJson, MAX_TEXT_LENGTH);
			buffer.writeUtf(value.roleColor, MAX_COLOR_LENGTH);
			writeOptionalIdentifier(buffer, value.teamId);
			buffer.writeUtf(value.teamNameJson, MAX_TEXT_LENGTH);
			writeIdentifier(buffer, value.lifeStateId);
			buffer.writeUtf(value.lifeStateNameJson, MAX_TEXT_LENGTH);
			buffer.writeUtf(value.flowStatus, MAX_FLOW_STATUS_LENGTH);
			writeList(buffer, value.completionStatuses, FlowCompletionStatus::write);
			buffer.writeBoolean(value.eligible);
			buffer.writeUtf(value.ineligibleReason, MAX_REASON_LENGTH);
		}
	}

	public record FlowCompletionStatus(
		Identifier flowId,
		String flowNameJson,
		int currentVersion,
		int latestCompletedVersion,
		String status
	) {
		public FlowCompletionStatus {
			flowId = requireIdentifier(flowId, "completion.flowId");
			flowNameJson = requireBounded(
				flowNameJson,
				MAX_TEXT_LENGTH,
				"completion.flowNameJson"
			);
			if (currentVersion <= 0 || latestCompletedVersion < 0) {
				throw new IllegalArgumentException("completion versions are invalid");
			}
			status = local(status, MAX_FLOW_STATUS_LENGTH, "completion.status");
			if (
				!status.equals("current")
					&& !status.equals("outdated")
					&& !status.equals("incomplete")
			) {
				throw new IllegalArgumentException("unsupported completion status " + status);
			}
			if (
				status.equals("current") != (latestCompletedVersion == currentVersion)
					|| status.equals("outdated") != (
						latestCompletedVersion > 0 && latestCompletedVersion != currentVersion
					)
					|| status.equals("incomplete") != (latestCompletedVersion == 0)
			) {
				throw new IllegalArgumentException("completion status does not match its versions");
			}
		}

		private static FlowCompletionStatus read(final RegistryFriendlyByteBuf buffer) {
			return new FlowCompletionStatus(
				readIdentifier(buffer, "completion.flowId"),
				buffer.readUtf(MAX_TEXT_LENGTH),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readUtf(MAX_FLOW_STATUS_LENGTH)
			);
		}

		private static void write(
			final RegistryFriendlyByteBuf buffer,
			final FlowCompletionStatus value
		) {
			writeIdentifier(buffer, value.flowId);
			buffer.writeUtf(value.flowNameJson, MAX_TEXT_LENGTH);
			buffer.writeVarInt(value.currentVersion);
			buffer.writeVarInt(value.latestCompletedVersion);
			buffer.writeUtf(value.status, MAX_FLOW_STATUS_LENGTH);
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
}
