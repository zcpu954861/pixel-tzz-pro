package io.github.zcpu954861.pixeltzzpro.client.screen.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.zcpu954861.pixeltzzpro.client.ClientPageState.ActivePage;
import io.github.zcpu954861.pixeltzzpro.network.payload.PageBundleS2CPayload.FieldSchema;
import io.github.zcpu954861.pixeltzzpro.ui.runtime.BindingContext;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * Local-only values exposed by the administrator previewer.
 */
public final class PreviewModel {
	private Profile profile = Profile.RUNNER;
	private PlayerCount playerCount = PlayerCount.THIRTY_TWO;
	private Viewport viewport = Viewport.STANDARD;
	private boolean reducedMotion;
	private boolean highContrast;
	private boolean defaultFont;
	private boolean debugLayout;
	private final Map<String, JsonElement> uiValues = new LinkedHashMap<>();

	public BindingContext context(final ActivePage active) {
		int completed = Math.min(this.playerCount.count, Math.max(0, this.playerCount.count - 8));
		int remaining = Math.max(0, this.playerCount.count - completed);
		int percent = this.playerCount.count == 0
			? 0
			: (int)Math.round(completed * 100.0 / this.playerCount.count);
		BindingContext.Builder builder = BindingContext.builder()
			.viewer("uuid", text(deterministicUuid("viewer").toString()))
			.viewer("name", text(profile.displayName))
			.viewer("online", bool(true))
			.viewer("admin", bool(profile.admin))
			.viewer("host", bool(profile.host))
			.viewer("role", text(profile.role))
			.viewer("role_name", text(profile.roleName()))
			.viewer("team", text(profile.team))
			.viewer("team_name", text(profile.teamName))
			.viewer("life_state", text(profile.lifeState))
			.viewer("life_state_name", text(profile.lifeStateName()))
			.viewer("ready", bool(profile != Profile.NORMAL))
			.session("game", text(active.page().game().toString()))
			.session("game_name", text("全员逃走中"))
			.session("phase", text("pixel_tzz:initializing"))
			.session("phase_name", text("初始化准备"))
			.session("revision", number(active.stateRevision()))
			.session("generation", number(active.generation()))
			.session("connected", bool(true))
			.session("players", players())
			.flow("id", text("pixel_tzz:preview"))
			.flow("version", number(1))
			.flow("node", text(active.page().id().getPath()))
			.flow("completed", number(completed))
			.flow("total", number(this.playerCount.count))
			.flow("remaining", number(remaining))
			.flow("percent", number(percent));

		for (FieldSchema field : active.fields().values()) {
			builder.field(field.id(), defaultValue(field));
		}
		this.uiValues.forEach(builder::ui);
		return builder.build();
	}

	private JsonArray players() {
		JsonArray players = new JsonArray();
		for (int index = 0; index < this.playerCount.count; index++) {
			boolean hunter = index % 9 == 0;
			boolean spectator = !hunter && index % 17 == 16;
			String role = hunter
				? "pixel_tzz:hunter"
				: spectator ? "pixel_tzz:spectator" : "pixel_tzz:runner";
			String team = hunter
				? "pixel_tzz:hunter"
				: spectator ? "pixel_tzz:spectator" : index % 2 == 0 ? "pixel_tzz:gold" : "pixel_tzz:cyan";
			String lifeState = index % 7 == 0 ? "pixel_tzz:captured" : "pixel_tzz:alive";
			JsonObject player = new JsonObject();
			player.addProperty("uuid", deterministicUuid("player-" + index).toString());
			player.addProperty("name", String.format("测试玩家 %02d", index + 1));
			player.addProperty("online", index % 11 != 10);
			player.addProperty("admin_eligible", index == 0);
			player.addProperty("host", index == 0);
			player.addProperty("role", role);
			player.addProperty("role_name", hunter ? "猎人" : spectator ? "旁观者" : "逃走者");
			player.addProperty("team", team);
			player.addProperty(
				"team_name",
				hunter ? "猎人" : spectator ? "旁观" : index % 2 == 0 ? "金队" : "青队"
			);
			player.addProperty("life_state", lifeState);
			player.addProperty("life_state_name", lifeState.endsWith("captured") ? "已捕获" : "存活");
			player.addProperty("ready", index % 5 != 0);
			players.add(player);
		}
		return players;
	}

	private static JsonElement defaultValue(final FieldSchema field) {
		if (field.defaultJson().isPresent()) {
			try {
				return JsonParser.parseString(field.defaultJson().orElseThrow());
			} catch (RuntimeException ignored) {
				// The server compiler already validated this; a safe preview fallback is enough here.
			}
		}
		return switch (field.type()) {
			case "boolean" -> bool(false);
			case "integer" -> number(field.minimum().orElse(0L));
			case "multi_choice" -> new JsonArray();
			default -> field.options().isEmpty() ? text("") : text(field.options().getFirst());
		};
	}

	public void setUiValue(final String nodeId, final JsonElement value) {
		this.uiValues.put(nodeId, value.deepCopy());
	}

	public JsonElement uiValue(final String nodeId) {
		JsonElement value = this.uiValues.get(nodeId);
		return value == null ? null : value.deepCopy();
	}

	public void cycleProfile() {
		this.profile = this.profile.next();
	}

	public void cyclePlayerCount() {
		this.playerCount = this.playerCount.next();
	}

	public void cycleViewport() {
		this.viewport = this.viewport.next();
	}

	public void toggleReducedMotion() {
		this.reducedMotion = !this.reducedMotion;
	}

	public void toggleHighContrast() {
		this.highContrast = !this.highContrast;
	}

	public void toggleDefaultFont() {
		this.defaultFont = !this.defaultFont;
	}

	public void toggleDebugLayout() {
		this.debugLayout = !this.debugLayout;
	}

	public Profile profile() {
		return this.profile;
	}

	public PlayerCount playerCount() {
		return this.playerCount;
	}

	public Viewport viewport() {
		return this.viewport;
	}

	public boolean reducedMotion() {
		return this.reducedMotion;
	}

	public boolean highContrast() {
		return this.highContrast;
	}

	public boolean defaultFont() {
		return this.defaultFont;
	}

	public boolean debugLayout() {
		return this.debugLayout;
	}

	private static UUID deterministicUuid(final String value) {
		return UUID.nameUUIDFromBytes(("pixel-tzz-preview:" + value).getBytes(StandardCharsets.UTF_8));
	}

	private static JsonPrimitive text(final String value) {
		return new JsonPrimitive(value);
	}

	private static JsonPrimitive bool(final boolean value) {
		return new JsonPrimitive(value);
	}

	private static JsonPrimitive number(final Number value) {
		return new JsonPrimitive(value);
	}

	public enum Profile {
		NORMAL("普通玩家", false, false, "pixel_tzz:runner", "pixel_tzz:gold", "金队", "pixel_tzz:alive"),
		ADMIN("管理员", true, false, "pixel_tzz:runner", "pixel_tzz:gold", "金队", "pixel_tzz:alive"),
		HOST("主持人", true, true, "pixel_tzz:runner", "pixel_tzz:gold", "金队", "pixel_tzz:alive"),
		HUNTER("猎人", false, false, "pixel_tzz:hunter", "pixel_tzz:hunter", "猎人", "pixel_tzz:alive"),
		RUNNER("逃走者", false, false, "pixel_tzz:runner", "pixel_tzz:cyan", "青队", "pixel_tzz:alive"),
		SPECTATOR("旁观者", false, false, "pixel_tzz:spectator", "pixel_tzz:spectator", "旁观", "pixel_tzz:captured");

		private final String displayName;
		private final boolean admin;
		private final boolean host;
		private final String role;
		private final String team;
		private final String teamName;
		private final String lifeState;

		Profile(
			final String displayName,
			final boolean admin,
			final boolean host,
			final String role,
			final String team,
			final String teamName,
			final String lifeState
		) {
			this.displayName = displayName;
			this.admin = admin;
			this.host = host;
			this.role = role;
			this.team = team;
			this.teamName = teamName;
			this.lifeState = lifeState;
		}

		private Profile next() {
			Profile[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public String displayName() {
			return this.displayName;
		}

		private String roleName() {
			return switch (this) {
				case HUNTER -> "猎人";
				case SPECTATOR -> "旁观者";
				default -> "逃走者";
			};
		}

		private String lifeStateName() {
			return this.lifeState.endsWith("captured") ? "已捕获" : "存活";
		}
	}

	public enum PlayerCount {
		ZERO(0),
		ONE(1),
		THIRTY_TWO(32),
		SIXTY_FOUR(64);

		private final int count;

		PlayerCount(final int count) {
			this.count = count;
		}

		private PlayerCount next() {
			PlayerCount[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public int count() {
			return this.count;
		}
	}

	public enum Viewport {
		COMPACT,
		STANDARD,
		WIDE;

		private Viewport next() {
			Viewport[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public String label() {
			return switch (this) {
				case COMPACT -> "Compact";
				case STANDARD -> "Standard";
				case WIDE -> "Wide";
			};
		}
	}
}
