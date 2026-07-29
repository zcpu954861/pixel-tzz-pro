package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionSnapshot;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.FieldType;
import io.github.zcpu954861.pixeltzzpro.network.OperationCode;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PersistentFieldValue;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PlayerRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ExclusiveReservation;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV3.ReservationStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/**
 * Pure authoritative transactions for data-driven {@code exclusive_choice} fields.
 *
 * <p>A player may retain one locked value while a reinitialization flow holds a replacement. The
 * final lock operation swaps both in one immutable state change; cancellation only removes the
 * replacement, so an interrupted reinitialization never destroys the old valid value.
 */
public final class ExclusiveChoiceAuthority {
	private static final Pattern STABLE_VALUE = Pattern.compile("[a-z0-9_.-]+");

	private ExclusiveChoiceAuthority() {
	}

	public static Result hold(
		final WorldStateV3 state,
		final Selection selection,
		final Set<String> registeredValues,
		final UUID reservationId,
		final long serverTick
	) {
		Objects.requireNonNull(reservationId, "reservationId");
		OperationCode preflight = preflight(state, selection, registeredValues, serverTick);
		if (preflight != OperationCode.SUCCESS) {
			return Result.rejected(preflight, message(preflight));
		}
		List<ExclusiveReservation> reservations = state.exclusiveReservations();
		ExclusiveReservation held = owned(
			reservations,
			selection,
			ReservationStatus.HELD
		).orElse(null);
		if (
			held != null
				&& held.flowInstanceId().filter(selection.flowInstanceId()::equals).isEmpty()
		) {
			return Result.rejected(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"player already has a held value from another flow"
			);
		}
		if (held != null && held.value().equals(selection.value())) {
			return Result.unchanged("exclusive value is already held by this flow");
		}

		Optional<ExclusiveReservation> occupied = occupant(reservations, selection, selection.value());
		if (occupied.isPresent()) {
			ExclusiveReservation conflict = occupied.orElseThrow();
			if (
				conflict.ownerId().equals(selection.ownerId())
					&& conflict.status() == ReservationStatus.LOCKED
					&& held == null
			) {
				return Result.unchanged("player selected the already locked exclusive value");
			}
			return Result.conflict(conflict);
		}

		try {
			List<ExclusiveReservation> next = new ArrayList<>(reservations);
			if (held == null) {
				if (next.size() >= WorldStateV3.MAX_EXCLUSIVE_RESERVATIONS) {
					return Result.rejected(
						OperationCode.RESOURCE_BLOCKED,
						"exclusive reservation capacity is exhausted"
					);
				}
				next.add(
					new ExclusiveReservation(
						reservationId,
						selection.gameId(),
						selection.fieldId(),
						selection.scopeId(),
						selection.value(),
						selection.ownerId(),
						ReservationStatus.HELD,
						Optional.of(selection.flowInstanceId()),
						serverTick,
						serverTick,
						0L
					)
				);
			} else {
				int index = next.indexOf(held);
				next.set(
					index,
					new ExclusiveReservation(
						held.reservationId(),
						held.gameId(),
						held.fieldId(),
						held.scopeId(),
						selection.value(),
						held.ownerId(),
						ReservationStatus.HELD,
						held.flowInstanceId(),
						held.createdAtServerTick(),
						serverTick,
						Math.incrementExact(held.reservationRevision())
					)
				);
			}
			return changed(state, next, "exclusive value held");
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Result.rejected(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	public static Result lock(
		final WorldStateV3 state,
		final Selection selection,
		final Set<String> registeredValues,
		final long serverTick
	) {
		OperationCode preflight = preflight(state, selection, registeredValues, serverTick);
		if (preflight != OperationCode.SUCCESS) {
			return Result.rejected(preflight, message(preflight));
		}
		List<ExclusiveReservation> reservations = state.exclusiveReservations();
		ExclusiveReservation locked = owned(
			reservations,
			selection,
			ReservationStatus.LOCKED
		).orElse(null);
		ExclusiveReservation held = owned(
			reservations,
			selection,
			ReservationStatus.HELD
		).orElse(null);
		if (held == null) {
			return locked != null && locked.value().equals(selection.value())
				? Result.unchanged("exclusive value is already locked")
				: Result.rejected(
					OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE,
					"the selected exclusive value is not held by this flow"
				);
		}
		if (
			!held.value().equals(selection.value())
				|| held.flowInstanceId().filter(selection.flowInstanceId()::equals).isEmpty()
		) {
			return Result.rejected(
				OperationCode.EXCLUSIVE_REQUIREMENT_INCOMPLETE,
				"the flow does not hold its selected exclusive value"
			);
		}
		try {
			List<ExclusiveReservation> next = new ArrayList<>(reservations);
			if (locked != null) {
				next.remove(locked);
			}
			int heldIndex = next.indexOf(held);
			next.set(
				heldIndex,
				new ExclusiveReservation(
					held.reservationId(),
					held.gameId(),
					held.fieldId(),
					held.scopeId(),
					held.value(),
					held.ownerId(),
					ReservationStatus.LOCKED,
					Optional.empty(),
					held.createdAtServerTick(),
					serverTick,
					Math.incrementExact(held.reservationRevision())
				)
			);
			return changed(state, next, "exclusive value locked");
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Result.rejected(OperationCode.INTERNAL_ERROR, safeMessage(error));
		}
	}

	/**
	 * Releases only the temporary value owned by this exact flow.
	 */
	public static Result cancelHeld(
		final WorldStateV3 state,
		final ReservationOwner owner,
		final UUID flowInstanceId,
		final long serverTick
	) {
		Objects.requireNonNull(flowInstanceId, "flowInstanceId");
		OperationCode preflight = preflight(state, owner, serverTick);
		if (preflight != OperationCode.SUCCESS) {
			return Result.rejected(preflight, message(preflight));
		}
		List<ExclusiveReservation> next = state.exclusiveReservations()
			.stream()
			.filter(reservation ->
				!(
					matchesOwner(reservation, owner)
						&& reservation.status() == ReservationStatus.HELD
						&& reservation.flowInstanceId().filter(flowInstanceId::equals).isPresent()
				)
			)
			.toList();
		return next.size() == state.exclusiveReservations().size()
			? Result.unchanged("flow has no temporary exclusive value")
			: changed(state, next, "temporary exclusive value released");
	}

	/**
	 * Releases temporary values owned by selected members of one exact flow.
	 *
	 * <p>This is the shared cancellation/removal primitive. Locked values are deliberately left
	 * untouched so canceling a reinitialization cannot destroy the player's previous valid choice.
	 */
	public static Result cancelHeld(
		final WorldStateV3 state,
		final UUID flowInstanceId,
		final Set<UUID> ownerIds,
		final long serverTick
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(flowInstanceId, "flowInstanceId");
		Set<UUID> owners = Set.copyOf(ownerIds);
		if (serverTick < 0L || state.validated().result().isEmpty()) {
			return Result.rejected(OperationCode.SCHEMA_BLOCKED, "world state is not writable");
		}
		List<ExclusiveReservation> next = state.exclusiveReservations()
			.stream()
			.filter(reservation ->
				!(
					reservation.status() == ReservationStatus.HELD
						&& reservation.flowInstanceId().filter(flowInstanceId::equals).isPresent()
						&& owners.contains(reservation.ownerId())
				)
			)
			.toList();
		return next.size() == state.exclusiveReservations().size()
			? Result.unchanged("selected flow members have no temporary exclusive values")
			: changed(state, next, "selected flow members' temporary exclusive values released");
	}

	public static Result releaseLocked(
		final WorldStateV3 state,
		final ReservationOwner owner,
		final long serverTick
	) {
		OperationCode preflight = preflight(state, owner, serverTick);
		if (preflight != OperationCode.SUCCESS) {
			return Result.rejected(preflight, message(preflight));
		}
		List<ExclusiveReservation> next = state.exclusiveReservations()
			.stream()
			.filter(reservation ->
				!(
					matchesOwner(reservation, owner)
						&& reservation.status() == ReservationStatus.LOCKED
				)
			)
			.toList();
		return next.size() == state.exclusiveReservations().size()
			? Result.unchanged("player has no locked exclusive value")
			: changed(state, next, "locked exclusive value released");
	}

	/**
	 * Releases locked values for players that no longer match a field's role constraints.
	 */
	public static Result releaseIneligibleLocked(
		final WorldStateV3 state,
		final Identifier gameId,
		final Identifier fieldId,
		final UUID scopeId,
		final Set<UUID> eligibleOwners,
		final long serverTick
	) {
		Objects.requireNonNull(eligibleOwners, "eligibleOwners");
		ReservationOwner boundary = new ReservationOwner(gameId, fieldId, scopeId, new UUID(0L, 0L));
		OperationCode preflight = preflight(state, boundary, serverTick);
		if (preflight != OperationCode.SUCCESS) {
			return Result.rejected(preflight, message(preflight));
		}
		List<ExclusiveReservation> next = state.exclusiveReservations()
			.stream()
			.filter(reservation ->
				!(
					reservation.gameId().equals(gameId)
						&& reservation.fieldId().equals(fieldId)
						&& reservation.scopeId().equals(scopeId)
						&& reservation.status() == ReservationStatus.LOCKED
						&& !eligibleOwners.contains(reservation.ownerId())
				)
			)
			.toList();
		return next.size() == state.exclusiveReservations().size()
			? Result.unchanged("all locked exclusive values remain eligible")
			: changed(state, next, "ineligible locked exclusive values released");
	}

	/**
	 * Applies all configured {@code release_when_role_mismatch} rules after an identity transaction.
	 *
	 * <p>The matching persisted player field is removed together with its lock, so data-pack reads
	 * cannot observe a value that is no longer authoritative. The caller composes this result into
	 * the same schema-v3 commit as the role change.</p>
	 */
	public static Result releaseRoleMismatches(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final UUID scopeId,
		final long serverTick
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(definitions, "definitions");
		Objects.requireNonNull(scopeId, "scopeId");
		if (serverTick < 0L || state.validated().result().isEmpty()) {
			return Result.rejected(OperationCode.SCHEMA_BLOCKED, "world state is not writable");
		}
		Identifier gameId = state.core().activeGameId().orElse(null);
		if (gameId == null) {
			return Result.rejected(OperationCode.PHASE_MISMATCH, "no game is active");
		}
		if (state.activeGameInstanceId().filter(scopeId::equals).isEmpty()) {
			return Result.rejected(
				OperationCode.FLOW_INSTANCE_MISMATCH,
				"exclusive scope is not the active game instance"
			);
		}

		List<ExclusiveReservation> released = state.exclusiveReservations()
			.stream()
			.filter(reservation ->
				reservation.gameId().equals(gameId)
					&& reservation.scopeId().equals(scopeId)
					&& reservation.status() == ReservationStatus.LOCKED
			)
			.filter(reservation -> roleMismatch(state, definitions, reservation))
			.toList();
		if (released.isEmpty()) {
			return Result.unchanged("all configured exclusive locks still match player roles");
		}

		Set<UUID> releasedIds = released.stream()
			.map(ExclusiveReservation::reservationId)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		List<ExclusiveReservation> reservations = state.exclusiveReservations()
			.stream()
			.filter(reservation -> !releasedIds.contains(reservation.reservationId()))
			.toList();
		Map<UUID, PlayerRecord> players = new LinkedHashMap<>(state.core().players());
		for (ExclusiveReservation reservation : released) {
			PlayerRecord player = players.get(reservation.ownerId());
			FieldDefinition field = definitions.fields().get(reservation.fieldId());
			if (player == null || field == null) {
				continue;
			}
			PersistentFieldValue stored = player.persistentFields().get(field.id());
			if (
				stored == null
					|| stored.fieldVersion() != field.version()
					|| stored.value().stringValue().filter(reservation.value()::equals).isEmpty()
			) {
				continue;
			}
			Map<Identifier, PersistentFieldValue> persistent = new LinkedHashMap<>(
				player.persistentFields()
			);
			persistent.remove(field.id());
			players.put(reservation.ownerId(), copyPlayer(player, persistent));
		}
		WorldStateV3 next = state
			.withCore(state.core().withPlayers(players))
			.withExclusiveReservations(reservations);
		return next.validated().result().isPresent()
			? Result.changed(next, "role-mismatched exclusive locks released")
			: Result.rejected(
				OperationCode.INTERNAL_ERROR,
				"role-mismatch release failed state validation"
			);
	}

	/**
	 * Checks the shared-option capacity used before starting a multi-player forced flow.
	 */
	public static Capacity capacity(
		final WorldStateV3 state,
		final Identifier gameId,
		final Identifier fieldId,
		final UUID scopeId,
		final Set<String> registeredValues,
		final Set<UUID> targetOwners
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(gameId, "gameId");
		Objects.requireNonNull(fieldId, "fieldId");
		Objects.requireNonNull(scopeId, "scopeId");
		Set<String> values = Set.copyOf(registeredValues);
		Set<UUID> targets = Set.copyOf(targetOwners);
		Set<String> usable = new HashSet<>(values);
		for (ExclusiveReservation reservation : state.exclusiveReservations()) {
			if (
				reservation.gameId().equals(gameId)
					&& reservation.fieldId().equals(fieldId)
					&& reservation.scopeId().equals(scopeId)
					&& !targets.contains(reservation.ownerId())
			) {
				usable.remove(reservation.value());
			}
		}
		return new Capacity(targets.size(), usable.size(), usable.size() >= targets.size());
	}

	public static Optional<String> lockedValue(
		final WorldStateV3 state,
		final ReservationOwner owner
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(owner, "owner");
		return state.exclusiveReservations()
			.stream()
			.filter(reservation ->
				matchesOwner(reservation, owner)
					&& reservation.status() == ReservationStatus.LOCKED
			)
			.map(ExclusiveReservation::value)
			.findFirst();
	}

	private static Result changed(
		final WorldStateV3 state,
		final List<ExclusiveReservation> reservations,
		final String message
	) {
		WorldStateV3 next = state.withExclusiveReservations(reservations);
		return next.validated().result().isPresent()
			? Result.changed(next, message)
			: Result.rejected(OperationCode.INTERNAL_ERROR, "exclusive reservation state failed validation");
	}

	private static OperationCode preflight(
		final WorldStateV3 state,
		final Selection selection,
		final Set<String> registeredValues,
		final long serverTick
	) {
		Objects.requireNonNull(selection, "selection");
		Objects.requireNonNull(registeredValues, "registeredValues");
		OperationCode base = preflight(state, selection.owner(), serverTick);
		if (base != OperationCode.SUCCESS) {
			return base;
		}
		return !STABLE_VALUE.matcher(selection.value()).matches()
				|| !registeredValues.contains(selection.value())
			? OperationCode.EXCLUSIVE_VALUE_INVALID
			: OperationCode.SUCCESS;
	}

	private static OperationCode preflight(
		final WorldStateV3 state,
		final ReservationOwner owner,
		final long serverTick
	) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(owner, "owner");
		if (serverTick < 0L || state.validated().result().isEmpty()) {
			return OperationCode.SCHEMA_BLOCKED;
		}
		if (state.core().activeGameId().filter(owner.gameId()::equals).isEmpty()) {
			return OperationCode.PHASE_MISMATCH;
		}
		return state.activeGameInstanceId().filter(owner.scopeId()::equals).isPresent()
			? OperationCode.SUCCESS
			: OperationCode.FLOW_INSTANCE_MISMATCH;
	}

	private static Optional<ExclusiveReservation> owned(
		final List<ExclusiveReservation> reservations,
		final Selection selection,
		final ReservationStatus status
	) {
		return reservations.stream()
			.filter(reservation ->
				matchesOwner(reservation, selection.owner()) && reservation.status() == status
			)
			.findFirst();
	}

	private static Optional<ExclusiveReservation> occupant(
		final List<ExclusiveReservation> reservations,
		final Selection selection,
		final String value
	) {
		return reservations.stream()
			.filter(reservation ->
				reservation.gameId().equals(selection.gameId())
					&& reservation.fieldId().equals(selection.fieldId())
					&& reservation.scopeId().equals(selection.scopeId())
					&& reservation.value().equals(value)
			)
			.findFirst();
	}

	private static boolean matchesOwner(
		final ExclusiveReservation reservation,
		final ReservationOwner owner
	) {
		return reservation.gameId().equals(owner.gameId())
			&& reservation.fieldId().equals(owner.fieldId())
			&& reservation.scopeId().equals(owner.scopeId())
			&& reservation.ownerId().equals(owner.ownerId());
	}

	private static boolean roleMismatch(
		final WorldStateV3 state,
		final DefinitionSnapshot definitions,
		final ExclusiveReservation reservation
	) {
		FieldDefinition field = definitions.fields().get(reservation.fieldId());
		PlayerRecord player = state.core().players().get(reservation.ownerId());
		return field != null
			&& field.game().equals(reservation.gameId())
			&& field.type() == FieldType.EXCLUSIVE_CHOICE
			&& field.exclusiveChoice()
				.filter(value -> value.releaseWhenRoleMismatch())
				.isPresent()
			&& player != null
			&& !field.roles().isEmpty()
			&& !field.roles().contains(player.roleId());
	}

	private static PlayerRecord copyPlayer(
		final PlayerRecord source,
		final Map<Identifier, PersistentFieldValue> persistentFields
	) {
		return new PlayerRecord(
			source.playerId(),
			source.lastKnownName(),
			source.roleId(),
			source.teamId(),
			source.lifeStateId(),
			persistentFields,
			source.pendingRoleChange(),
			source.activeFlowParticipation(),
			source.completionSummary(),
			source.firstSeenAtEpochMillis(),
			source.lastSeenAtEpochMillis()
		);
	}

	private static String message(final OperationCode code) {
		return switch (code) {
			case EXCLUSIVE_VALUE_INVALID -> "exclusive value is not registered";
			case PHASE_MISMATCH -> "exclusive field does not belong to the active game";
			case FLOW_INSTANCE_MISMATCH -> "exclusive scope is not the active game instance";
			case SCHEMA_BLOCKED -> "world state is not writable";
			default -> "exclusive reservation request was rejected";
		};
	}

	private static String safeMessage(final RuntimeException error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	public record ReservationOwner(
		Identifier gameId,
		Identifier fieldId,
		UUID scopeId,
		UUID ownerId
	) {
		public ReservationOwner {
			gameId = Objects.requireNonNull(gameId, "gameId");
			fieldId = Objects.requireNonNull(fieldId, "fieldId");
			scopeId = Objects.requireNonNull(scopeId, "scopeId");
			ownerId = Objects.requireNonNull(ownerId, "ownerId");
		}
	}

	public record Selection(
		Identifier gameId,
		Identifier fieldId,
		UUID scopeId,
		UUID ownerId,
		UUID flowInstanceId,
		String value
	) {
		public Selection {
			gameId = Objects.requireNonNull(gameId, "gameId");
			fieldId = Objects.requireNonNull(fieldId, "fieldId");
			scopeId = Objects.requireNonNull(scopeId, "scopeId");
			ownerId = Objects.requireNonNull(ownerId, "ownerId");
			flowInstanceId = Objects.requireNonNull(flowInstanceId, "flowInstanceId");
			value = Objects.requireNonNull(value, "value");
		}

		private ReservationOwner owner() {
			return new ReservationOwner(this.gameId, this.fieldId, this.scopeId, this.ownerId);
		}
	}

	public record Conflict(UUID ownerId, ReservationStatus status) {
		public Conflict {
			ownerId = Objects.requireNonNull(ownerId, "ownerId");
			status = Objects.requireNonNull(status, "status");
		}
	}

	public record Capacity(int required, int available, boolean sufficient) {
		public Capacity {
			if (required < 0 || available < 0 || sufficient != (available >= required)) {
				throw new IllegalArgumentException("exclusive capacity result is invalid");
			}
		}
	}

	public record Result(
		OperationCode code,
		String message,
		Optional<WorldStateV3> nextState,
		Optional<Conflict> conflict
	) {
		public Result {
			code = Objects.requireNonNull(code, "code");
			message = Objects.requireNonNull(message, "message");
			nextState = Objects.requireNonNull(nextState, "nextState");
			conflict = Objects.requireNonNull(conflict, "conflict");
			if (
				message.isBlank()
					|| (nextState.isPresent() && code != OperationCode.SUCCESS)
					|| (conflict.isPresent() != (code == OperationCode.EXCLUSIVE_OPTION_TAKEN))
			) {
				throw new IllegalArgumentException("exclusive reservation result is inconsistent");
			}
		}

		public boolean accepted() {
			return this.code == OperationCode.SUCCESS;
		}

		public boolean changed() {
			return this.nextState.isPresent();
		}

		private static Result changed(final WorldStateV3 state, final String message) {
			return new Result(
				OperationCode.SUCCESS,
				message,
				Optional.of(state),
				Optional.empty()
			);
		}

		private static Result unchanged(final String message) {
			return new Result(
				OperationCode.SUCCESS,
				message,
				Optional.empty(),
				Optional.empty()
			);
		}

		private static Result conflict(final ExclusiveReservation reservation) {
			return new Result(
				OperationCode.EXCLUSIVE_OPTION_TAKEN,
				"exclusive value is already occupied",
				Optional.empty(),
				Optional.of(new Conflict(reservation.ownerId(), reservation.status()))
			);
		}

		private static Result rejected(final OperationCode code, final String message) {
			return new Result(code, message, Optional.empty(), Optional.empty());
		}
	}
}
