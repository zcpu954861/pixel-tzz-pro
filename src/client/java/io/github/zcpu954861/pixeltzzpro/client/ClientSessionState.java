package io.github.zcpu954861.pixeltzzpro.client;

import io.github.zcpu954861.pixeltzzpro.network.NetworkProtocol;
import io.github.zcpu954861.pixeltzzpro.network.payload.HandshakeS2CPayload;
import io.github.zcpu954861.pixeltzzpro.network.payload.SessionSnapshotS2CPayload;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Read-only client projection of the authoritative server snapshot.
 */
public final class ClientSessionState {
	private static Snapshot snapshot = Snapshot.disconnected();

	private ClientSessionState() {
	}

	public static void beginConnection() {
		snapshot = Snapshot.waiting();
	}

	public static void disconnect() {
		snapshot = Snapshot.disconnected();
	}

	public static void acceptHandshake(final HandshakeS2CPayload payload) {
		ConnectionStatus status = NetworkProtocol.isCompatible(payload.protocolVersion())
			? ConnectionStatus.WAITING
			: ConnectionStatus.INCOMPATIBLE;
		snapshot = snapshot.withHandshake(status, payload.protocolVersion(), payload.modVersion());
	}

	public static boolean acceptSnapshot(final SessionSnapshotS2CPayload payload) {
		if (!NetworkProtocol.isCompatible(payload.protocolVersion())) {
			snapshot = snapshot.withStatus(ConnectionStatus.INCOMPATIBLE);
			return false;
		}

		if (
			!isKnownDefinitionStatus(payload.definitionStatus())
				|| payload.definitionGeneration() < 0L
				|| payload.gameDefinitionCount() < 0
				|| payload.definitionCount() < 0
		) {
			snapshot = snapshot.withStatus(ConnectionStatus.INCOMPATIBLE);
			return false;
		}

		snapshot = new Snapshot(
			ConnectionStatus.READY,
			payload.protocolVersion(),
			snapshot.serverModVersion(),
			payload.phaseId(),
			payload.adminEligible(),
			payload.hostAssigned(),
			payload.currentPlayerHost(),
			payload.schemaCompatible(),
			payload.stateRevision(),
			payload.loadSequence(),
			payload.definitionStatus(),
			payload.definitionHealthy(),
			payload.definitionGeneration(),
			payload.gameDefinitionCount(),
			payload.definitionCount(),
			payload.definitionDiagnostic()
		);
		return true;
	}

	private static boolean isKnownDefinitionStatus(final String status) {
		return status.equals("empty")
			|| status.equals("ready")
			|| status.equals("invalid")
			|| status.equals("platform_reload_failed");
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public enum ConnectionStatus {
		WAITING("pixel_tzz_pro.screen.status.waiting"),
		READY("pixel_tzz_pro.screen.status.ready"),
		INCOMPATIBLE("pixel_tzz_pro.screen.status.incompatible"),
		OFFLINE("pixel_tzz_pro.screen.status.offline");

		private final String translationKey;

		ConnectionStatus(final String translationKey) {
			this.translationKey = translationKey;
		}

		public String translationKey() {
			return this.translationKey;
		}
	}

	public record Snapshot(
		ConnectionStatus status,
		int serverProtocolVersion,
		String serverModVersion,
		Optional<Identifier> phaseId,
		boolean adminEligible,
		boolean hostAssigned,
		boolean currentPlayerHost,
		boolean schemaCompatible,
		long stateRevision,
		long loadSequence,
		String definitionStatus,
		boolean definitionHealthy,
		long definitionGeneration,
		int gameDefinitionCount,
		int definitionCount,
		String definitionDiagnostic
	) {
		private static Snapshot waiting() {
			return new Snapshot(
				ConnectionStatus.WAITING,
				0,
				"—",
				Optional.empty(),
				false,
				false,
				false,
				true,
				0L,
				0L,
				"empty",
				false,
				0L,
				0,
				0,
				""
			);
		}

		private static Snapshot disconnected() {
			return new Snapshot(
				ConnectionStatus.OFFLINE,
				0,
				"—",
				Optional.empty(),
				false,
				false,
				false,
				true,
				0L,
				0L,
				"empty",
				false,
				0L,
				0,
				0,
				""
			);
		}

		private Snapshot withHandshake(final ConnectionStatus newStatus, final int protocolVersion, final String modVersion) {
			return new Snapshot(
				newStatus,
				protocolVersion,
				modVersion,
				this.phaseId,
				this.adminEligible,
				this.hostAssigned,
				this.currentPlayerHost,
				this.schemaCompatible,
				this.stateRevision,
				this.loadSequence,
				this.definitionStatus,
				this.definitionHealthy,
				this.definitionGeneration,
				this.gameDefinitionCount,
				this.definitionCount,
				this.definitionDiagnostic
			);
		}

		private Snapshot withStatus(final ConnectionStatus newStatus) {
			return new Snapshot(
				newStatus,
				this.serverProtocolVersion,
				this.serverModVersion,
				this.phaseId,
				this.adminEligible,
				this.hostAssigned,
				this.currentPlayerHost,
				this.schemaCompatible,
				this.stateRevision,
				this.loadSequence,
				this.definitionStatus,
				this.definitionHealthy,
				this.definitionGeneration,
				this.gameDefinitionCount,
				this.definitionCount,
				this.definitionDiagnostic
			);
		}
	}
}
