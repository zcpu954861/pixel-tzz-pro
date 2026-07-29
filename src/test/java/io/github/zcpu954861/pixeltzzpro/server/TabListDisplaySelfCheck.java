package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.RichText;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabColorMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabPrefixMode;
import io.github.zcpu954861.pixeltzzpro.content.GameDefinitions.TabStyle;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.CompletionRecord;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChange;
import io.github.zcpu954861.pixeltzzpro.state.WorldStateV2.PendingRoleChangeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Small pure regression check for data-driven TAB composition and identity visibility.
 */
public final class TabListDisplaySelfCheck {
	private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final UUID CURRENT_INSTANCE = UUID.fromString(
		"20000000-0000-0000-0000-000000000001"
	);
	private static final UUID OLD_INSTANCE = UUID.fromString(
		"20000000-0000-0000-0000-000000000002"
	);
	private static final Identifier ROLE_ID = id("test:hunter");
	private static final Identifier FLOW_ID = id("test:hunter_initialization");
	private static final Identifier ACTION_ID = id("test:assign_hunter");

	private TabListDisplaySelfCheck() {
	}

	public static void main(final String[] args) {
		WorldStateV2.initial();
		checkCompositionOrder();
		checkInitializationCredential();
		checkHostOverride();
		System.out.println("TabListDisplaySelfCheck PASS");
	}

	private static void checkCompositionOrder() {
		TabStyle role = style(
			"[身份] ",
			"#E94F64",
			100,
			TabPrefixMode.APPEND,
			TabColorMode.REPLACE
		);
		TabStyle team = style(
			"[小队] ",
			"#65D68A",
			50,
			TabPrefixMode.APPEND,
			TabColorMode.REPLACE
		);
		TabStyle lifeState = new TabStyle(
			Optional.of(text("[捕获] ")),
			Optional.empty(),
			Optional.of(-20),
			TabPrefixMode.REPLACE,
			TabColorMode.INHERIT
		);
		TabListDisplay.ResolvedStyle composed = TabListDisplay.compose(role, team, lifeState);
		check(composed.plainPrefix().equals("[捕获] "), "life state must replace prior prefixes");
		check(
			composed.color().orElseThrow() == 0x65D68A,
			"life-state inherit must retain the team color"
		);
		check(composed.sortOrder() == -20, "life state must override sort order");
		check(
			TabListDisplay.toVanillaOrder(-20) > TabListDisplay.toVanillaOrder(50),
			"smaller data-pack sort order must appear first in vanilla"
		);
		check(
			TabListDisplay.compose(role, team, null)
				.plainPrefix()
				.equals("[身份] [小队] "),
			"team append must retain the role prefix"
		);
	}

	private static void checkInitializationCredential() {
		PendingRoleChange applied = pending(PendingRoleChangeStatus.APPLIED);
		CompletionRecord oldCompletion = completion(OLD_INSTANCE, 3);
		check(
			!exposed(applied, List.of(oldCompletion), 3),
			"older completion must not satisfy the current role transaction"
		);

		CompletionRecord currentCompletion = completion(CURRENT_INSTANCE, 3);
		check(
			exposed(applied, List.of(oldCompletion, currentCompletion), 3),
			"matching same-role/default-role initialization credential must expose the role"
		);
		check(
			exposed(
				pending(PendingRoleChangeStatus.PENDING),
				List.of(oldCompletion),
				3
			),
			"same-role reinitialization must preserve the already-earned identity"
		);
		check(
			!TabListDisplay.hasCurrentIdentityCredential(
				PLAYER_ID,
				id("test:runner"),
				Optional.of(FLOW_ID),
				Optional.of(3),
				Optional.of(pending(PendingRoleChangeStatus.PENDING)),
				List.of(oldCompletion)
			),
			"a pending transaction must not expose a role that is neither its previous nor applied target"
		);
		check(
			!exposed(applied, List.of(currentCompletion), 4),
			"old flow version must not satisfy the current role definition"
		);
		check(
			TabListDisplay.hasCurrentIdentityCredential(
				PLAYER_ID,
				ROLE_ID,
				Optional.of(FLOW_ID),
				Optional.of(3),
				Optional.empty(),
				List.of(currentCompletion)
			),
			"default-role initialization completion must expose the candidate role"
		);
		check(
			!TabListDisplay.hasCurrentIdentityCredential(
				PLAYER_ID,
				ROLE_ID,
				Optional.of(FLOW_ID),
				Optional.of(3),
				Optional.empty(),
				List.of()
			),
			"default role must remain hidden before its initialization completes"
		);
		check(
			TabListDisplay.hasCurrentIdentityCredential(
				PLAYER_ID,
				ROLE_ID,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				List.of()
			),
			"role without initialization flow must be immediately visible"
		);
	}

	private static void checkHostOverride() {
		TabListDisplay.ResolvedStyle host = TabListDisplay.hostStyle();
		check(host.plainPrefix().equals("[主持人] "), "host prefix must be fixed");
		check(host.color().orElseThrow() == 0xD7B45A, "host must be gold");
		check(
			TabListDisplay.toVanillaOrder(host.sortOrder())
				> TabListDisplay.toVanillaOrder(-10_000),
			"host must sort ahead of every data-pack layer"
		);
	}

	private static boolean exposed(
		final PendingRoleChange credential,
		final List<CompletionRecord> completions,
		final int flowVersion
	) {
		return TabListDisplay.hasCurrentIdentityCredential(
			PLAYER_ID,
			ROLE_ID,
			Optional.of(FLOW_ID),
			Optional.of(flowVersion),
			Optional.of(credential),
			completions
		);
	}

	private static PendingRoleChange pending(final PendingRoleChangeStatus status) {
		return new PendingRoleChange(
			PLAYER_ID,
			ROLE_ID,
			ACTION_ID,
			CURRENT_INSTANCE,
			ACTOR_ID,
			10L,
			ROLE_ID,
			1L,
			status
		);
	}

	private static CompletionRecord completion(final UUID instanceId, final int version) {
		return new CompletionRecord(
			PLAYER_ID,
			FLOW_ID,
			version,
			instanceId,
			20L,
			ACTION_ID,
			false,
			1L
		);
	}

	private static TabStyle style(
		final String prefix,
		final String color,
		final int sortOrder,
		final TabPrefixMode prefixMode,
		final TabColorMode colorMode
	) {
		return new TabStyle(
			Optional.of(text(prefix)),
			Optional.of(color),
			Optional.of(sortOrder),
			prefixMode,
			colorMode
		);
	}

	private static RichText text(final String value) {
		return new RichText("", value);
	}

	private static Identifier id(final String value) {
		return Identifier.parse(value);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
