package io.github.zcpu954861.pixeltzzpro.client.screen;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudNode;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.ComponentOrderKey;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.ProfileKey;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudPreferences.Snapshot;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudRuntime;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ClientPolicy;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Shared profile-aware draft across the HUD category index and all six detail pages. */
final class HudSettingsSession {
	private final LinkedHashMap<Optional<ProfileKey>, DraftState> inactiveDrafts = new LinkedHashMap<>();
	private final HudDeveloperPreviewSession developerPreview;
	private Context context;
	private Snapshot original;
	private Snapshot draft;

	HudSettingsSession() {
		ClientHudPreferences.initialize();
		this.developerPreview = new HudDeveloperPreviewSession();
		this.context = liveContext();
		this.original = activate(this.context);
		this.draft = this.original;
	}

	Snapshot draft() {
		return this.draft;
	}

	Snapshot effectiveDraft() {
		return effective(this.draft);
	}

	Snapshot effective(final Snapshot raw) {
		return ClientHudPreferences.effectiveSnapshot(
			raw,
			this.context.policy(),
			this.context.components(),
			this.context.nodes()
		);
	}

	ClientPolicy policy() {
		return this.context.policy();
	}

	List<ComponentControl> controls() {
		return this.context.components();
	}

	Map<String, ClientHudNode> nodes() {
		return this.context.nodes();
	}

	boolean hasLiveProfile() {
		return this.context.profileKey().isPresent();
	}

	String policyFingerprint() {
		return this.context.fingerprint();
	}

	boolean developerPreviewAvailable() {
		return this.developerPreview.available();
	}

	HudDeveloperPreviewSession developerPreview() {
		return this.developerPreview;
	}

	void closeDeveloperPreview() {
		this.developerPreview.closeAll();
	}

	/** Releases every isolated preview owned by this complete settings session. */
	void close() {
		closeDeveloperPreview();
	}

	/**
	 * Refreshes policy and component authority while a settings page remains open.
	 *
	 * <p>A profile/version switch changes the draft owner and therefore loads that profile's raw
	 * draft. Policy or component changes within the same profile retain the raw draft and only
	 * rebuild its effective presentation.</p>
	 */
	boolean refreshContext() {
		boolean previewChanged = this.developerPreview.refresh();
		Context next = liveContext();
		if (this.context.fingerprint().equals(next.fingerprint())) {
			return previewChanged;
		}
		boolean profileChanged = !this.context.profileKey().equals(next.profileKey());
		if (profileChanged) {
			stashCurrentDraft();
		}
		this.context = next;
		Snapshot active = activate(next);
		if (profileChanged) {
			DraftState suspended = this.inactiveDrafts.remove(next.profileKey());
			this.original = suspended == null ? active : suspended.original();
			this.draft = suspended == null ? active : suspended.draft();
		}
		return true;
	}

	boolean dirty() {
		return !this.original.equals(this.draft)
			|| this.inactiveDrafts.values().stream().anyMatch(DraftState::dirty);
	}

	void update(final UnaryOperator<Snapshot> change) {
		refreshContext();
		this.draft = Objects.requireNonNull(change, "change").apply(this.draft).validated();
	}

	void apply() {
		refreshContext();
		for (Map.Entry<Optional<ProfileKey>, DraftState> entry : this.inactiveDrafts.entrySet()) {
			if (entry.getValue().dirty()) {
				persist(entry.getKey(), entry.getValue().draft());
			}
		}
		this.inactiveDrafts.clear();
		persist(this.context.profileKey(), this.draft);
		this.original = this.draft.validated();
		this.draft = this.original;
	}

	void discard() {
		this.inactiveDrafts.clear();
		this.draft = this.original;
	}

	void resetAllDraft() {
		refreshContext();
		this.draft = Snapshot.defaults(this.context.policy());
	}

	void resetLayoutDraft() {
		refreshContext();
		this.draft = this.draft.resetLayout(this.context.policy());
	}

	void resetComponentDraft() {
		refreshContext();
		this.draft = this.draft.withComponentPreferences(Set.of(), Set.of(), Map.of());
	}

	boolean toggleHidden(final String id) {
		refreshContext();
		ComponentControl control = control(id).orElse(null);
		if (
			control == null
				|| !control.visible()
				|| !control.allowHide()
				|| !this.context.policy().allowComponentManagement()
		) {
			return false;
		}
		LinkedHashSet<String> values = new LinkedHashSet<>(this.draft.hiddenComponents());
		if (!values.remove(id)) {
			values.add(id);
		}
		this.draft = this.draft.withHiddenComponents(values);
		return true;
	}

	boolean toggleCompact(final String id) {
		refreshContext();
		ComponentControl control = control(id).orElse(null);
		if (
			control == null
				|| !control.visible()
				|| !control.allowCompact()
				|| !this.context.nodes().containsKey(id)
				|| !this.context.policy().allowComponentManagement()
		) {
			return false;
		}
		LinkedHashSet<String> values = new LinkedHashSet<>(this.draft.compactComponents());
		if (!values.remove(id)) {
			values.add(id);
		}
		this.draft = this.draft.withCompactComponents(values);
		return true;
	}

	boolean moveComponent(final String id, final int direction) {
		refreshContext();
		if (direction != -1 && direction != 1) {
			return false;
		}
		ReorderState state = reorderState(id);
		int target = state.index() + direction;
		if (!state.available() || target < 0 || target >= state.order().size()) {
			return false;
		}
		List<String> order = new ArrayList<>(state.order());
		String value = order.remove(state.index());
		order.add(target, value);
		LinkedHashMap<ComponentOrderKey, List<String>> preferences = new LinkedHashMap<>(this.draft.componentOrder());
		preferences.put(state.key().orElseThrow(), List.copyOf(order));
		this.draft = this.draft.withComponentOrder(preferences);
		return true;
	}

	ReorderState reorderState(final String id) {
		ComponentControl control = control(id).orElse(null);
		if (control == null || control.reorderGroup().isEmpty()) {
			return ReorderState.unavailable(ReorderIssue.NOT_AUTHORIZED);
		}
		if (!this.context.policy().allowComponentManagement()) {
			return ReorderState.unavailable(ReorderIssue.POLICY_LOCKED);
		}
		if (!control.visible() || !this.context.nodes().containsKey(id)) {
			return ReorderState.unavailable(ReorderIssue.NODE_UNAVAILABLE);
		}
		List<String> parents = parentsOf(id);
		if (parents.isEmpty()) {
			return ReorderState.unavailable(ReorderIssue.NO_PARENT);
		}
		if (parents.size() != 1) {
			return ReorderState.unavailable(ReorderIssue.MULTIPLE_PARENTS);
		}
		String parentId = parents.getFirst();
		ClientHudNode parent = this.context.nodes().get(parentId);
		if (!(parent instanceof ClientHudNode.ContainerNode)) {
			return ReorderState.unavailable(ReorderIssue.PARENT_NOT_CONTAINER);
		}
		ComponentOrderKey key = new ComponentOrderKey(parentId, control.reorderGroup().orElseThrow());
		List<String> defaults = ClientHudPreferences.reorderGroups(
			this.context.components(),
			this.context.nodes()
		).get(key);
		if (defaults == null || defaults.size() < 2) {
			return ReorderState.unavailable(ReorderIssue.NO_SAME_GROUP_PEER);
		}
		LinkedHashSet<String> order = new LinkedHashSet<>();
		for (String value : this.draft.componentOrder().getOrDefault(key, List.of())) {
			if (defaults.contains(value)) {
				order.add(value);
			}
		}
		order.addAll(defaults);
		List<String> values = List.copyOf(order);
		int index = values.indexOf(id);
		if (index < 0) {
			return ReorderState.unavailable(ReorderIssue.NO_SAME_GROUP_PEER);
		}
		return new ReorderState(Optional.of(key), values, index, ReorderIssue.NONE);
	}

	private Optional<ComponentControl> control(final String id) {
		return this.context.components().stream().filter(value -> value.id().equals(id)).findFirst();
	}

	private void stashCurrentDraft() {
		if (this.original.equals(this.draft)) {
			return;
		}
		Optional<ProfileKey> owner = this.context.profileKey();
		this.inactiveDrafts.remove(owner);
		this.inactiveDrafts.put(owner, new DraftState(this.original, this.draft));
		while (this.inactiveDrafts.size() > ClientHudPreferences.MAX_PROFILES) {
			this.inactiveDrafts.remove(this.inactiveDrafts.keySet().iterator().next());
		}
	}

	private static void persist(final Optional<ProfileKey> owner, final Snapshot value) {
		owner.ifPresentOrElse(
			key -> ClientHudPreferences.apply(key, value),
			() -> ClientHudPreferences.applyUnscoped(value)
		);
	}

	private List<String> parentsOf(final String id) {
		return this.context.nodes().values().stream()
			.filter(value -> value.childIds().contains(id))
			.map(ClientHudNode::id)
			.sorted()
			.toList();
	}

	private static Snapshot activate(final Context context) {
		if (context.profileKey().isEmpty()) {
			ClientHudPreferences.deactivateProfile();
			return ClientHudPreferences.snapshot();
		}
		return ClientHudPreferences.activateProfile(
			context.profileKey().orElseThrow(),
			context.policy(),
			context.components(),
			context.nodes()
		);
	}

	private static Context liveContext() {
		return ClientHudRuntime.snapshot().map(value -> {
			ProfileKey key = new ProfileKey(value.profileId(), value.profileContentVersion());
			List<ComponentControl> components = List.copyOf(value.components());
			Map<String, ClientHudNode> nodes = Map.copyOf(value.nodes());
			return new Context(
				Optional.of(key),
				value.policy(),
				components,
				nodes,
				fingerprint(Optional.of(key), value.policy(), components, nodes)
			);
		}).orElseGet(() -> {
			ClientPolicy policy = ClientPolicy.safeDefaults();
			return new Context(Optional.empty(), policy, List.of(), Map.of(), fingerprint(Optional.empty(), policy, List.of(), Map.of()));
		});
	}

	private static String fingerprint(
		final Optional<ProfileKey> key,
		final ClientPolicy policy,
		final List<ComponentControl> components,
		final Map<String, ClientHudNode> nodes
	) {
		StringBuilder value = new StringBuilder(512);
		key.ifPresentOrElse(
			profile -> value.append(profile.profileId()).append('@').append(profile.profileContentVersion()),
			() -> value.append("unscoped")
		);
		value.append('|').append(policy);
		components.forEach(control -> value
			.append('|').append(control.id())
			.append(':').append(control.label().getString())
			.append(':').append(control.visible())
			.append(':').append(control.allowHide())
			.append(':').append(control.allowCompact())
			.append(':').append(control.reorderGroup().orElse("-"))
		);
		nodes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> value
			.append('|').append(entry.getKey())
			.append(':').append(entry.getValue().meta().type().name().toLowerCase(Locale.ROOT))
			.append(':').append(String.join(",", entry.getValue().childIds()))
		);
		return value.toString();
	}

	record ReorderState(
		Optional<ComponentOrderKey> key,
		List<String> order,
		int index,
		ReorderIssue issue
	) {
		ReorderState {
			key = Objects.requireNonNull(key, "key");
			order = List.copyOf(order);
			issue = Objects.requireNonNull(issue, "issue");
		}

		static ReorderState unavailable(final ReorderIssue issue) {
			return new ReorderState(Optional.empty(), List.of(), -1, issue);
		}

		boolean available() {
			return this.issue == ReorderIssue.NONE;
		}

		boolean canMoveUp() {
			return available() && this.index > 0;
		}

		boolean canMoveDown() {
			return available() && this.index + 1 < this.order.size();
		}
	}

	enum ReorderIssue {
		NONE,
		NOT_AUTHORIZED,
		POLICY_LOCKED,
		NODE_UNAVAILABLE,
		NO_PARENT,
		MULTIPLE_PARENTS,
		PARENT_NOT_CONTAINER,
		NO_SAME_GROUP_PEER;

		String translationKey() {
			return "pixel_tzz_pro.settings.hud.components.reorder_issue."
				+ name().toLowerCase(Locale.ROOT);
		}
	}

	private record Context(
		Optional<ProfileKey> profileKey,
		ClientPolicy policy,
		List<ComponentControl> components,
		Map<String, ClientHudNode> nodes,
		String fingerprint
	) {
		private Context {
			profileKey = Objects.requireNonNull(profileKey, "profileKey");
			policy = Objects.requireNonNull(policy, "policy");
			components = List.copyOf(components);
			nodes = Map.copyOf(nodes);
			fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
		}
	}

	private record DraftState(Snapshot original, Snapshot draft) {
		private DraftState {
			original = Objects.requireNonNull(original, "original").validated();
			draft = Objects.requireNonNull(draft, "draft").validated();
		}

		private boolean dirty() {
			return !this.original.equals(this.draft);
		}
	}
}
