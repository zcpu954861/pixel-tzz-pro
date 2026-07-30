package io.github.zcpu954861.pixeltzzpro.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Connection-local ordinary-player terminal sessions.
 *
 * <p>This class owns navigation shape only. Page eligibility, bindings, actions, and world state
 * remain server-authoritative and are re-evaluated by {@link PixelTzzServerRuntime} for every
 * request. A new page-instance ID is issued whenever the visible frame changes, including Back,
 * so a delayed action can never bind to a page that merely happens to have the same data-pack ID.
 */
public final class PlayerTerminalSessions {
	public static final int MAX_STACK_DEPTH = 32;
	public static final int MAX_SESSIONS = 1_024;
	public static final int MAX_HISTORY_RECORD_KEY_LENGTH = 256;
	public static final int MAX_SOURCE_NODE_ID_LENGTH = 128;
	public static final long IDLE_EXPIRY_TICKS = 20L * 60L * 10L;

	private final Map<UUID, Session> sessions = new HashMap<>();

	public synchronized OpenResult open(
		final UUID playerId,
		final long definitionGeneration,
		final long stateRevision,
		final Optional<Identifier> routeId,
		final Identifier rootPage,
		final boolean takeoverAllowed,
		final long currentTick
	) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(routeId, "routeId");
		Objects.requireNonNull(rootPage, "rootPage");
		if (
			definitionGeneration < 0L
				|| stateRevision < 0L
				|| currentTick < 0L
		) {
			return OpenResult.rejected("终端会话上下文包含负数修订。");
		}
		if (!this.sessions.containsKey(playerId) && this.sessions.size() >= MAX_SESSIONS) {
			return OpenResult.rejected("玩家终端会话数量已达到安全上限。");
		}
		Frame frame = new Frame(rootPage, UUID.randomUUID());
		Session session = new Session(
			playerId,
			UUID.randomUUID(),
			definitionGeneration,
			stateRevision,
			0L,
			0L,
			routeId,
			rootPage,
			List.of(frame),
			takeoverAllowed,
			currentTick
		);
		this.sessions.put(playerId, session);
		return OpenResult.opened(session);
	}

	public synchronized Optional<Session> find(final UUID playerId) {
		return Optional.ofNullable(this.sessions.get(Objects.requireNonNull(playerId, "playerId")));
	}

	public synchronized List<Session> snapshot() {
		return this.sessions.values()
			.stream()
			.sorted(java.util.Comparator.comparing(Session::playerId))
			.toList();
	}

	/**
	 * Accepts exactly the next sequence in one terminal session.
	 *
	 * <p>The connection protocol does not retransmit terminal intents. Requiring an exact sequence
	 * makes dropped, duplicated, and reordered requests fail closed without opening a wide replay
	 * window around state-changing registered functions.
	 */
	public synchronized SequenceResult acceptSequence(
		final UUID playerId,
		final UUID sessionId,
		final long requestSequence,
		final long currentTick
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (session == null || !session.sessionId().equals(sessionId)) {
			return SequenceResult.rejected(SequenceStatus.SESSION_MISMATCH);
		}
		if (requestSequence != session.nextRequestSequence()) {
			return SequenceResult.rejected(
				requestSequence < session.nextRequestSequence()
					? SequenceStatus.REPLAYED
					: SequenceStatus.OUT_OF_ORDER
			);
		}
		if (requestSequence == Long.MAX_VALUE) {
			return SequenceResult.rejected(SequenceStatus.EXHAUSTED);
		}
		Session accepted = session.withRequestSequence(requestSequence + 1L, currentTick);
		this.sessions.put(playerId, accepted);
		return SequenceResult.accepted(accepted);
	}

	/**
	 * Pushes a registered {@code open_page} target together with its server-owned navigation
	 * grant.
	 *
	 * <p>The caller supplies the already-authorized source instance, node, and action. This class
	 * resolves the source page from the current server-side stack and rejects a stale source
	 * instance, so a packet can never choose or rewrite its own authorization ancestry.</p>
	 */
	public synchronized MutationResult pushAuthorized(
		final UUID playerId,
		final Identifier pageId,
		final UUID expectedSourceInstanceId,
		final String sourceNodeId,
		final Identifier actionId,
		final long stateRevision
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		Objects.requireNonNull(pageId, "pageId");
		Objects.requireNonNull(expectedSourceInstanceId, "expectedSourceInstanceId");
		Objects.requireNonNull(sourceNodeId, "sourceNodeId");
		Objects.requireNonNull(actionId, "actionId");
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		if (stateRevision < 0L) {
			return MutationResult.rejected("玩家终端状态修订不能为负数。");
		}
		if (session.stack().size() >= MAX_STACK_DEPTH) {
			return MutationResult.rejected("玩家终端页面层级已达到安全上限。");
		}
		Frame source = session.current();
		if (!source.instanceId().equals(expectedSourceInstanceId)) {
			return MutationResult.rejected("玩家终端来源页面已经变化。");
		}
		NavigationGrant grant;
		try {
			grant = new NavigationGrant(
				source.pageId(),
				source.instanceId(),
				sourceNodeId,
				actionId
			);
		} catch (IllegalArgumentException error) {
			return MutationResult.rejected("玩家终端授权来源无效。");
		}
		List<Frame> stack = new ArrayList<>(session.stack());
		stack.add(
			new Frame(
				pageId,
				UUID.randomUUID(),
				Optional.of(grant),
				Optional.empty()
			)
		);
		Session updated = session.withStack(stack, stateRevision);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	/**
	 * Selects one server-resolved history record.
	 *
	 * <p>When the registered detail page is the current non-root page, selection replaces that
	 * frame in place. Its navigation grant is retained alongside the history record, while a fresh
	 * instance ID invalidates delayed list actions. A detail page with another ID keeps the legacy
	 * push behavior.</p>
	 */
	public synchronized MutationResult pushHistoryDetail(
		final UUID playerId,
		final Identifier pageId,
		final String recordKey,
		final long stateRevision
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		Objects.requireNonNull(pageId, "pageId");
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		HistoryRecordRef history = new HistoryRecordRef(recordKey);
		List<Frame> stack = new ArrayList<>(session.stack());
		Frame current = session.current();
		if (current.pageId().equals(pageId)) {
			if (stack.size() <= 1) {
				return MutationResult.rejected("玩家终端首页不能承载历史详情。");
			}
			stack.set(
				stack.size() - 1,
				new Frame(
					current.pageId(),
					UUID.randomUUID(),
					current.navigationGrant(),
					Optional.of(history)
				)
			);
			Session updated = session.withStack(stack, stateRevision);
			this.sessions.put(playerId, updated);
			return MutationResult.changed(updated);
		}
		if (stack.size() >= MAX_STACK_DEPTH) {
			return MutationResult.rejected("玩家终端页面层级已达到安全上限。");
		}
		stack.add(
			new Frame(
				pageId,
				UUID.randomUUID(),
				Optional.empty(),
				Optional.of(history)
			)
		);
		Session updated = session.withStack(stack, stateRevision);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	public synchronized MutationResult back(
		final UUID playerId,
		final long stateRevision
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		if (session.stack().size() <= 1) {
			return MutationResult.rejected("当前已经是玩家终端首页。");
		}
		List<Frame> stack = new ArrayList<>(
			session.stack().subList(0, session.stack().size() - 1)
		);
		Frame previous = stack.getLast();
		stack.set(
			stack.size() - 1,
			new Frame(
				previous.pageId(),
				UUID.randomUUID(),
				previous.navigationGrant(),
				previous.historyRecord()
			)
		);
		Session updated = session.withStack(stack, stateRevision);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	/**
	 * Reissues the current page instance after a resource reload or explicit Refresh.
	 */
	public synchronized MutationResult reissue(
		final UUID playerId,
		final long definitionGeneration,
		final long stateRevision,
		final boolean takeoverAllowed
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		List<Frame> stack = new ArrayList<>(session.stack());
		Frame current = stack.getLast();
		stack.set(
			stack.size() - 1,
			new Frame(
				current.pageId(),
				UUID.randomUUID(),
				current.navigationGrant(),
				current.historyRecord()
			)
		);
		Session updated = new Session(
			session.playerId(),
			session.sessionId(),
			definitionGeneration,
			stateRevision,
			session.routeRevision(),
			session.nextRequestSequence(),
			session.routeId(),
			session.rootPage(),
			stack,
			takeoverAllowed,
			session.lastUserActivityTick()
		);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	/**
	 * Drops an invalid frame and every descendant while preserving the longest authorized prefix.
	 *
	 * <p>{@code retainedDepth} is a frame count, not a zero-based index. The newly visible frame
	 * always receives a fresh instance ID so delayed client actions cannot bind to the page that
	 * was visible before the authority change.</p>
	 */
	public synchronized MutationResult truncateToDepth(
		final UUID playerId,
		final int retainedDepth,
		final long stateRevision,
		final boolean takeoverAllowed
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		if (
			retainedDepth < 1
				|| retainedDepth > session.stack().size()
				|| stateRevision < 0L
		) {
			return MutationResult.rejected("玩家终端合法页面前缀无效。");
		}
		List<Frame> stack = new ArrayList<>(
			session.stack().subList(0, retainedDepth)
		);
		Frame current = stack.getLast();
		stack.set(
			stack.size() - 1,
			new Frame(
				current.pageId(),
				UUID.randomUUID(),
				current.navigationGrant(),
				current.historyRecord()
			)
		);
		Session updated = new Session(
			session.playerId(),
			session.sessionId(),
			session.definitionGeneration(),
			stateRevision,
			session.routeRevision(),
			session.nextRequestSequence(),
			session.routeId(),
			session.rootPage(),
			stack,
			takeoverAllowed,
			session.lastUserActivityTick()
		);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	/**
	 * Replaces the complete stack when the authoritative entry route actually changes.
	 */
	public synchronized MutationResult replaceRoute(
		final UUID playerId,
		final long definitionGeneration,
		final long stateRevision,
		final Optional<Identifier> routeId,
		final Identifier rootPage,
		final boolean takeoverAllowed
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		if (session.routeRevision() == Long.MAX_VALUE) {
			return MutationResult.rejected("玩家终端路由修订已经耗尽。");
		}
		Session updated = new Session(
			session.playerId(),
			session.sessionId(),
			definitionGeneration,
			stateRevision,
			session.routeRevision() + 1L,
			session.nextRequestSequence(),
			Objects.requireNonNull(routeId, "routeId"),
			Objects.requireNonNull(rootPage, "rootPage"),
			List.of(new Frame(rootPage, UUID.randomUUID())),
			takeoverAllowed,
			session.lastUserActivityTick()
		);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	/**
	 * Records a binding-only refresh without changing the visible page instance.
	 */
	public synchronized MutationResult touchProjection(
		final UUID playerId,
		final long stateRevision,
		final boolean takeoverAllowed
	) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (session == null) {
			return MutationResult.rejected("玩家终端会话已经关闭。");
		}
		Session updated = new Session(
			session.playerId(),
			session.sessionId(),
			session.definitionGeneration(),
			stateRevision,
			session.routeRevision(),
			session.nextRequestSequence(),
			session.routeId(),
			session.rootPage(),
			session.stack(),
			takeoverAllowed,
			session.lastUserActivityTick()
		);
		this.sessions.put(playerId, updated);
		return MutationResult.changed(updated);
	}

	public synchronized boolean close(final UUID playerId, final UUID pageInstanceId) {
		Session session = this.sessions.get(Objects.requireNonNull(playerId, "playerId"));
		if (
			session == null
				|| !session.current().instanceId().equals(
					Objects.requireNonNull(pageInstanceId, "pageInstanceId")
				)
		) {
			return false;
		}
		this.sessions.remove(playerId);
		return true;
	}

	public synchronized void remove(final UUID playerId) {
		this.sessions.remove(Objects.requireNonNull(playerId, "playerId"));
	}

	public synchronized void clear() {
		this.sessions.clear();
	}

	public synchronized List<UUID> expire(final long currentTick) {
		List<UUID> expired = this.sessions.entrySet()
			.stream()
			.filter(entry ->
				currentTick < entry.getValue().lastUserActivityTick()
					|| currentTick - entry.getValue().lastUserActivityTick() >= IDLE_EXPIRY_TICKS
			)
			.map(Map.Entry::getKey)
			.toList();
		expired.forEach(this.sessions::remove);
		return expired;
	}

	public record HistoryRecordRef(String recordKey) {
		public HistoryRecordRef {
			recordKey = Objects.requireNonNull(recordKey, "recordKey");
			if (
				recordKey.isBlank()
					|| recordKey.length() > MAX_HISTORY_RECORD_KEY_LENGTH
			) {
				throw new IllegalArgumentException(
					"history record key must contain 1.."
						+ MAX_HISTORY_RECORD_KEY_LENGTH
						+ " characters"
				);
			}
		}
	}

	/**
	 * Server-owned proof of the registered {@code open_page} button that produced one frame.
	 *
	 * <p>This stores identity only, never a frozen eligibility result. The runtime must rebind the
	 * node/action and re-evaluate its current audience, phase, task, and predicate before retaining
	 * the child frame.</p>
	 */
	public record NavigationGrant(
		Identifier sourcePageId,
		UUID sourcePageInstanceId,
		String sourceNodeId,
		Identifier actionId
	) {
		public NavigationGrant {
			Objects.requireNonNull(sourcePageId, "sourcePageId");
			Objects.requireNonNull(sourcePageInstanceId, "sourcePageInstanceId");
			sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
			Objects.requireNonNull(actionId, "actionId");
			if (
				sourceNodeId.isBlank()
					|| sourceNodeId.length() > MAX_SOURCE_NODE_ID_LENGTH
			) {
				throw new IllegalArgumentException(
					"source node ID must contain 1.."
						+ MAX_SOURCE_NODE_ID_LENGTH
						+ " characters"
				);
			}
		}
	}

	public record Frame(
		Identifier pageId,
		UUID instanceId,
		Optional<NavigationGrant> navigationGrant,
		Optional<HistoryRecordRef> historyRecord
	) {
		public Frame(final Identifier pageId, final UUID instanceId) {
			this(pageId, instanceId, Optional.empty(), Optional.empty());
		}

		public Frame {
			Objects.requireNonNull(pageId, "pageId");
			Objects.requireNonNull(instanceId, "instanceId");
			navigationGrant = Objects.requireNonNull(
				navigationGrant,
				"navigationGrant"
			);
			historyRecord = Objects.requireNonNull(historyRecord, "historyRecord");
		}
	}

	public record Session(
		UUID playerId,
		UUID sessionId,
		long definitionGeneration,
		long stateRevision,
		long routeRevision,
		long nextRequestSequence,
		Optional<Identifier> routeId,
		Identifier rootPage,
		List<Frame> stack,
		boolean takeoverAllowed,
		long lastUserActivityTick
	) {
		public Session {
			Objects.requireNonNull(playerId, "playerId");
			Objects.requireNonNull(sessionId, "sessionId");
			routeId = Objects.requireNonNull(routeId, "routeId");
			Objects.requireNonNull(rootPage, "rootPage");
			stack = List.copyOf(stack);
			if (
				definitionGeneration < 0L
					|| stateRevision < 0L
					|| routeRevision < 0L
					|| nextRequestSequence < 0L
					|| lastUserActivityTick < 0L
					|| stack.isEmpty()
					|| stack.size() > MAX_STACK_DEPTH
					|| !stack.getFirst().pageId().equals(rootPage)
					|| stack.getFirst().navigationGrant().isPresent()
					|| stack.getFirst().historyRecord().isPresent()
			) {
				throw new IllegalArgumentException("invalid player terminal session");
			}
			for (int index = 1; index < stack.size(); index++) {
				Frame parent = stack.get(index - 1);
				Frame frame = stack.get(index);
				boolean navigation = frame.navigationGrant().isPresent();
				boolean history = frame.historyRecord().isPresent();
				if (!navigation && !history) {
					throw new IllegalArgumentException(
						"non-root terminal frames require at least one authorization anchor"
					);
				}
				if (navigation) {
					NavigationGrant grant = frame.navigationGrant().orElseThrow();
					if (
						!grant.sourcePageId().equals(parent.pageId())
							|| !grant.sourcePageInstanceId().equals(parent.instanceId())
					) {
						throw new IllegalArgumentException(
							"terminal navigation grant does not match its parent frame"
						);
					}
				}
			}
		}

		public Frame current() {
			return this.stack.getLast();
		}

		private Session withRequestSequence(final long value, final long currentTick) {
			return new Session(
				this.playerId,
				this.sessionId,
				this.definitionGeneration,
				this.stateRevision,
				this.routeRevision,
				value,
				this.routeId,
				this.rootPage,
				this.stack,
				this.takeoverAllowed,
				currentTick
			);
		}

		private Session withStack(
			final List<Frame> value,
			final long updatedStateRevision
		) {
			return new Session(
				this.playerId,
				this.sessionId,
				this.definitionGeneration,
				updatedStateRevision,
				this.routeRevision,
				this.nextRequestSequence,
				this.routeId,
				this.rootPage,
				value,
				this.takeoverAllowed,
				this.lastUserActivityTick
			);
		}
	}

	public enum SequenceStatus {
		ACCEPTED,
		SESSION_MISMATCH,
		REPLAYED,
		OUT_OF_ORDER,
		EXHAUSTED
	}

	public record SequenceResult(SequenceStatus status, Optional<Session> session) {
		private static SequenceResult accepted(final Session session) {
			return new SequenceResult(SequenceStatus.ACCEPTED, Optional.of(session));
		}

		private static SequenceResult rejected(final SequenceStatus status) {
			return new SequenceResult(status, Optional.empty());
		}
	}

	public record OpenResult(Optional<Session> session, String message) {
		public boolean successful() {
			return this.session.isPresent();
		}

		private static OpenResult opened(final Session session) {
			return new OpenResult(Optional.of(session), "");
		}

		private static OpenResult rejected(final String message) {
			return new OpenResult(Optional.empty(), Objects.requireNonNull(message, "message"));
		}
	}

	public record MutationResult(Optional<Session> session, String message) {
		public boolean successful() {
			return this.session.isPresent();
		}

		private static MutationResult changed(final Session session) {
			return new MutationResult(Optional.of(session), "");
		}

		private static MutationResult rejected(final String message) {
			return new MutationResult(Optional.empty(), Objects.requireNonNull(message, "message"));
		}
	}
}
