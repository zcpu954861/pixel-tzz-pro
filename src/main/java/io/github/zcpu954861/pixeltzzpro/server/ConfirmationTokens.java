package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.DefinitionCompiler;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.resources.Identifier;

/**
 * In-memory, single-use confirmations for authoritative server operations.
 *
 * <p>Tokens deliberately do not survive disconnect cleanup, data-pack reload, or server restart.
 * The caller owns those lifecycle hooks and must call {@link #cancelOperator(UUID)} or
 * {@link #clear()} at the corresponding boundary.
 */
public final class ConfirmationTokens {
	public static final long LIFETIME_NANOS = 30_000_000_000L;
	public static final long LIFETIME_MILLISECONDS = 30_000L;
	public static final int MAX_TARGETS = 64;
	public static final int MAX_CONSEQUENCES = 64;
	public static final int MAX_OPERATION_TYPE_LENGTH = 64;
	public static final int MAX_RECENTLY_CONSUMED = 256;
	private static final int SHA_256_BYTES = 32;
	private static final int TOKEN_BYTES = 16;

	private final SecureRandom random;
	private final LongSupplier nanoClock;
	private final Map<UUID, UUID> tokenByOperator = new HashMap<>();
	private final Map<UUID, PendingConfirmation> pendingByToken = new HashMap<>();
	private final LinkedHashMap<UUID, UUID> consumedOperatorByToken = new LinkedHashMap<>();

	public ConfirmationTokens() {
		this(new SecureRandom(), System::nanoTime);
	}

	/**
	 * Testable constructor. The supplied clock must be monotonic and return nanoseconds.
	 */
	public ConfirmationTokens(final SecureRandom random, final LongSupplier nanoClock) {
		this.random = Objects.requireNonNull(random, "random");
		this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
	}

	/**
	 * Issues the sole pending confirmation for an operator, invalidating any older one.
	 */
	public synchronized IssuedConfirmation issue(
		final UUID operatorId,
		final Binding binding,
		final Prompt prompt
	) {
		Objects.requireNonNull(operatorId, "operatorId");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(prompt, "prompt");

		UUID previousToken = this.tokenByOperator.remove(operatorId);
		boolean replaced = previousToken != null;
		if (previousToken != null) {
			this.pendingByToken.remove(previousToken);
		}

		UUID tokenId;
		do {
			tokenId = nextTokenId();
		} while (
			this.pendingByToken.containsKey(tokenId)
				|| this.consumedOperatorByToken.containsKey(tokenId)
		);
		long issuedAtNanos = this.nanoClock.getAsLong();
		PendingConfirmation pending = new PendingConfirmation(
			tokenId,
			operatorId,
			binding,
			prompt,
			issuedAtNanos
		);
		this.tokenByOperator.put(operatorId, tokenId);
		this.pendingByToken.put(tokenId, pending);
		return new IssuedConfirmation(
			tokenId,
			prompt,
			LIFETIME_MILLISECONDS,
			replaced
		);
	}

	/**
	 * Consumes a matching token before checking expiry or mutable operation context.
	 *
	 * <p>Looking up by the unguessable token first prevents a stale, replaced confirmation from
	 * deleting the operator's newer confirmation.
	 */
	public synchronized ConsumeResult consume(
		final UUID operatorId,
		final UUID tokenId,
		final Binding currentBinding
	) {
		Objects.requireNonNull(operatorId, "operatorId");
		Objects.requireNonNull(tokenId, "tokenId");
		Objects.requireNonNull(currentBinding, "currentBinding");

		PendingConfirmation pending = this.pendingByToken.get(tokenId);
		if (pending == null) {
			return new Rejected(
				operatorId.equals(this.consumedOperatorByToken.get(tokenId))
					? Rejection.CONSUMED
					: Rejection.MISSING
			);
		}
		if (!pending.operatorId().equals(operatorId)) {
			return new Rejected(Rejection.MISSING);
		}
		this.pendingByToken.remove(tokenId);
		this.tokenByOperator.remove(operatorId, tokenId);
		rememberConsumed(tokenId, operatorId);
		if (expired(pending.issuedAtNanos(), this.nanoClock.getAsLong())) {
			return new Rejected(Rejection.EXPIRED);
		}
		if (!pending.binding().equals(currentBinding)) {
			return new Rejected(Rejection.CONTEXT_CHANGED);
		}
		return new Accepted(
			new ConfirmedOperation(
				pending.tokenId(),
				pending.operatorId(),
				pending.binding(),
				pending.prompt()
			)
		);
	}

	/**
	 * Cancels only the exact token shown by the operator. A stale UI cannot cancel its replacement.
	 */
	public synchronized CancelResult cancel(final UUID operatorId, final UUID tokenId) {
		Objects.requireNonNull(operatorId, "operatorId");
		Objects.requireNonNull(tokenId, "tokenId");

		PendingConfirmation pending = this.pendingByToken.get(tokenId);
		if (pending == null || !pending.operatorId().equals(operatorId)) {
			return CancelResult.NOT_FOUND;
		}
		this.pendingByToken.remove(tokenId);
		this.tokenByOperator.remove(operatorId, tokenId);
		return CancelResult.CANCELED;
	}

	/**
	 * Invalidates the operator's current token, for example when that player disconnects.
	 */
	public synchronized CancelResult cancelOperator(final UUID operatorId) {
		Objects.requireNonNull(operatorId, "operatorId");
		UUID tokenId = this.tokenByOperator.remove(operatorId);
		if (tokenId == null) {
			return CancelResult.NOT_FOUND;
		}
		this.pendingByToken.remove(tokenId);
		return CancelResult.CANCELED;
	}

	/**
	 * Invalidates every pending confirmation and returns the number removed.
	 */
	public synchronized int clear() {
		int removed = this.pendingByToken.size();
		this.pendingByToken.clear();
		this.tokenByOperator.clear();
		this.consumedOperatorByToken.clear();
		return removed;
	}

	public synchronized int size() {
		return this.pendingByToken.size();
	}

	public synchronized boolean hasPending(final UUID operatorId, final UUID tokenId) {
		Objects.requireNonNull(operatorId, "operatorId");
		Objects.requireNonNull(tokenId, "tokenId");
		return tokenId.equals(this.tokenByOperator.get(operatorId))
			&& this.pendingByToken.containsKey(tokenId);
	}

	public synchronized boolean hasPending(final UUID operatorId) {
		Objects.requireNonNull(operatorId, "operatorId");
		UUID tokenId = this.tokenByOperator.get(operatorId);
		return tokenId != null && this.pendingByToken.containsKey(tokenId);
	}

	public synchronized Optional<Binding> pendingBinding(final UUID operatorId) {
		Objects.requireNonNull(operatorId, "operatorId");
		UUID tokenId = this.tokenByOperator.get(operatorId);
		PendingConfirmation pending = tokenId == null ? null : this.pendingByToken.get(tokenId);
		return pending == null ? Optional.empty() : Optional.of(pending.binding());
	}

	/**
	 * Returns the server-authored binding needed to recompute mutable context before consumption.
	 *
	 * <p>The token remains pending. Callers must still pass the freshly recomputed binding to
	 * {@link #consume(UUID, UUID, Binding)}; knowing a token ID never authorizes an operation.
	 */
	public synchronized Optional<Binding> pendingBinding(
		final UUID operatorId,
		final UUID tokenId
	) {
		Objects.requireNonNull(operatorId, "operatorId");
		Objects.requireNonNull(tokenId, "tokenId");
		PendingConfirmation pending = this.pendingByToken.get(tokenId);
		return pending != null && pending.operatorId().equals(operatorId)
			? Optional.of(pending.binding())
			: Optional.empty();
	}

	/**
	 * Classifies an unavailable token without disclosing another operator's token state.
	 */
	public synchronized Optional<Rejection> rejectionIfUnavailable(
		final UUID operatorId,
		final UUID tokenId
	) {
		Objects.requireNonNull(operatorId, "operatorId");
		Objects.requireNonNull(tokenId, "tokenId");
		PendingConfirmation pending = this.pendingByToken.get(tokenId);
		if (pending != null && pending.operatorId().equals(operatorId)) {
			return Optional.empty();
		}
		return Optional.of(
			operatorId.equals(this.consumedOperatorByToken.get(tokenId))
				? Rejection.CONSUMED
				: Rejection.MISSING
		);
	}

	private UUID nextTokenId() {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		bytes[6] = (byte)((bytes[6] & 0x0F) | 0x40);
		bytes[8] = (byte)((bytes[8] & 0x3F) | 0x80);
		long mostSignificant = 0L;
		long leastSignificant = 0L;
		for (int index = 0; index < Long.BYTES; index++) {
			mostSignificant = (mostSignificant << 8) | (bytes[index] & 0xFFL);
			leastSignificant = (leastSignificant << 8) | (bytes[index + Long.BYTES] & 0xFFL);
		}
		return new UUID(mostSignificant, leastSignificant);
	}

	private void rememberConsumed(final UUID tokenId, final UUID operatorId) {
		this.consumedOperatorByToken.put(tokenId, operatorId);
		while (this.consumedOperatorByToken.size() > MAX_RECENTLY_CONSUMED) {
			this.consumedOperatorByToken.remove(
				this.consumedOperatorByToken.keySet().iterator().next()
			);
		}
	}

	private static boolean expired(final long issuedAtNanos, final long nowNanos) {
		return nowNanos - issuedAtNanos >= LIFETIME_NANOS;
	}

	private record PendingConfirmation(
		UUID tokenId,
		UUID operatorId,
		Binding binding,
		Prompt prompt,
		long issuedAtNanos
	) {
	}

	public record OperationKey(String type, Identifier id) {
		public OperationKey {
			Objects.requireNonNull(type, "type");
			id = Objects.requireNonNull(id, "id");
			if (
				type.isBlank()
					|| type.length() > MAX_OPERATION_TYPE_LENGTH
					|| !type.equals(type.toLowerCase(Locale.ROOT))
			) {
				throw new IllegalArgumentException(
					"type must be non-blank lowercase text no longer than "
						+ MAX_OPERATION_TYPE_LENGTH
				);
			}
		}
	}

	/**
	 * Every mutable fact that can invalidate a high-risk confirmation.
	 */
	public record Binding(
		OperationKey operation,
		List<UUID> targetIds,
		Optional<Identifier> gameId,
		Optional<Identifier> phaseId,
		long definitionGeneration,
		long stateRevision,
		String relatedStateSha256
	) {
		public Binding {
			operation = Objects.requireNonNull(operation, "operation");
			Objects.requireNonNull(targetIds, "targetIds");
			if (targetIds.size() > MAX_TARGETS) {
				throw new IllegalArgumentException("target count exceeds " + MAX_TARGETS);
			}
			if (targetIds.stream().anyMatch(Objects::isNull)) {
				throw new NullPointerException("targetIds contains null");
			}
			List<UUID> sortedTargets = targetIds
				.stream()
				.sorted(Comparator.naturalOrder())
				.toList();
			if (sortedTargets.size() != sortedTargets.stream().distinct().count()) {
				throw new IllegalArgumentException("targetIds contains duplicates");
			}
			targetIds = sortedTargets;

			gameId = Objects.requireNonNull(gameId, "gameId");
			phaseId = Objects.requireNonNull(phaseId, "phaseId");
			if (gameId.isPresent() != phaseId.isPresent()) {
				throw new IllegalArgumentException("gameId and phaseId must both be present or absent");
			}
			if (definitionGeneration < 0L) {
				throw new IllegalArgumentException("definitionGeneration must be non-negative");
			}
			if (stateRevision < 0L) {
				throw new IllegalArgumentException("stateRevision must be non-negative");
			}
			relatedStateSha256 = normalizeSha256(relatedStateSha256);
		}
	}

	public record Prompt(RichText title, List<RichText> consequences) {
		public Prompt {
			title = requireText(title, "title");
			Objects.requireNonNull(consequences, "consequences");
			if (consequences.isEmpty() || consequences.size() > MAX_CONSEQUENCES) {
				throw new IllegalArgumentException(
					"consequence count must be within 1.." + MAX_CONSEQUENCES
				);
			}
			consequences = consequences
				.stream()
				.map(consequence -> requireText(consequence, "consequence"))
				.toList();
		}
	}

	public record IssuedConfirmation(
		UUID tokenId,
		Prompt prompt,
		long validForMilliseconds,
		boolean replacedPrevious
	) {
	}

	public sealed interface ConsumeResult permits Accepted, Rejected {
		default boolean accepted() {
			return this instanceof Accepted;
		}
	}

	public record Accepted(ConfirmedOperation operation) implements ConsumeResult {
		public Accepted {
			operation = Objects.requireNonNull(operation, "operation");
		}
	}

	public record Rejected(Rejection reason) implements ConsumeResult {
		public Rejected {
			reason = Objects.requireNonNull(reason, "reason");
		}
	}

	/**
	 * Stored server-authored data returned only after every binding has been revalidated.
	 */
	public record ConfirmedOperation(
		UUID tokenId,
		UUID operatorId,
		Binding binding,
		Prompt prompt
	) {
	}

	public enum Rejection {
		MISSING("confirmation_missing", "确认已失效，请重新打开确认页面。"),
		CONSUMED("confirmation_consumed", "该确认已经使用，请刷新当前状态。"),
		EXPIRED("confirmation_expired", "确认已过期，请重新检查操作内容。"),
		CONTEXT_CHANGED("confirmation_context_changed", "操作上下文已经变化，请重新确认。");

		private final String code;
		private final String message;

		Rejection(final String code, final String message) {
			this.code = code;
			this.message = message;
		}

		public String code() {
			return this.code;
		}

		public String message() {
			return this.message;
		}
	}

	public enum CancelResult {
		CANCELED,
		NOT_FOUND
	}

	private static RichText requireText(final RichText value, final String field) {
		Objects.requireNonNull(value, field);
		Objects.requireNonNull(value.json(), field + ".json");
		Objects.requireNonNull(value.plainText(), field + ".plainText");
		if (
			value.json().length() > DefinitionCompiler.MAX_TEXT_JSON_LENGTH
				|| value.plainText().length() > DefinitionCompiler.MAX_TEXT_JSON_LENGTH
		) {
			throw new IllegalArgumentException(
				field + " exceeds " + DefinitionCompiler.MAX_TEXT_JSON_LENGTH + " characters"
			);
		}
		return value;
	}

	private static String normalizeSha256(final String value) {
		Objects.requireNonNull(value, "relatedStateSha256");
		byte[] bytes;
		try {
			bytes = HexFormat.of().parseHex(value);
		} catch (IllegalArgumentException error) {
			throw new IllegalArgumentException("relatedStateSha256 must be hexadecimal", error);
		}
		if (bytes.length != SHA_256_BYTES) {
			throw new IllegalArgumentException(
				"relatedStateSha256 must contain " + SHA_256_BYTES + " bytes"
			);
		}
		return HexFormat.of().formatHex(Arrays.copyOf(bytes, bytes.length));
	}
}
