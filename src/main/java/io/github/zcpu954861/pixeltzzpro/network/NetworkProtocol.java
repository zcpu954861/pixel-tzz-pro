package io.github.zcpu954861.pixeltzzpro.network;

/**
 * Explicit wire contract. Patch releases may differ as long as this protocol version matches.
 */
public final class NetworkProtocol {
	public static final int CURRENT_VERSION = 7;

	private NetworkProtocol() {
	}

	public static boolean isCompatible(final int remoteVersion) {
		return remoteVersion == CURRENT_VERSION;
	}
}
