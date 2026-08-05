package io.github.zcpu954861.pixeltzzpro.client.hud;

import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.ComponentControl;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.Priority;
import io.github.zcpu954861.pixeltzzpro.client.hud.ClientHudSnapshot.TimerValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A bounded, already-resolved HUD tree node. Child references are stable node IDs within the same
 * atomic projection; no node contains a binding source, selector, path or executable behavior.
 */
public sealed interface ClientHudNode permits
	ClientHudNode.TextNode,
	ClientHudNode.ImageNode,
	ClientHudNode.PlayerHeadNode,
	ClientHudNode.CounterNode,
	ClientHudNode.BadgeNode,
	ClientHudNode.ProgressNode,
	ClientHudNode.TimerNode,
	ClientHudNode.SeparatorNode,
	ClientHudNode.BackgroundNode,
	ClientHudNode.ContainerNode {
	NodeMeta meta();

	default String id() {
		return this.meta().id();
	}

	default List<String> childIds() {
		return List.of();
	}

	enum Type {
		TEXT,
		IMAGE,
		PLAYER_HEAD,
		COUNTER,
		BADGE,
		PROGRESS,
		TIMER,
		SEPARATOR,
		BACKGROUND,
		ROW,
		COLUMN,
		OVERLAY,
		REPEAT
	}

	enum Alignment {
		START,
		CENTER,
		END,
		STRETCH
	}

	enum Overflow {
		WRAP,
		ELLIPSIS,
		SCALE_ONCE
	}

	enum Orientation {
		HORIZONTAL,
		VERTICAL
	}

	enum ImageFit {
		CONTAIN,
		COVER,
		STRETCH
	}

	record Insets(int left, int top, int right, int bottom) {
		public Insets {
			if (left < 0 || top < 0 || right < 0 || bottom < 0 || left + right > 96 || top + bottom > 96) {
				throw new IllegalArgumentException("HUD insets are outside safe bounds");
			}
		}

		public static Insets none() {
			return new Insets(0, 0, 0, 0);
		}
	}

	record NodeStyle(
		int textColor,
		int secondaryTextColor,
		int backgroundColor,
		int borderColor,
		int trackColor,
		int fillColor,
		int markerColor,
		double opacity,
		boolean bold,
		Insets padding,
		int gap,
		OptionalInt width,
		OptionalInt height
	) {
		public NodeStyle {
			if (!Double.isFinite(opacity) || opacity < 0.0D || opacity > 1.0D || gap < 0 || gap > 48) {
				throw new IllegalArgumentException("HUD node style is outside safe bounds");
			}
			padding = Objects.requireNonNull(padding, "padding");
			width = bounded(width, "width");
			height = bounded(height, "height");
		}

		private static OptionalInt bounded(final OptionalInt value, final String label) {
			OptionalInt result = Objects.requireNonNull(value, label);
			if (result.isPresent() && (result.getAsInt() <= 0 || result.getAsInt() > 640)) {
				throw new IllegalArgumentException("HUD style " + label + " is outside safe bounds");
			}
			return result;
		}

		public static NodeStyle defaults() {
			return new NodeStyle(
				0xFFF1F5F7,
				0xFFAAB4BE,
				0x00000000,
				0x00000000,
				0xFF40505F,
				0xFF55D7E6,
				0xFFF2C14E,
				1.0D,
				false,
				Insets.none(),
				0,
				OptionalInt.empty(),
				OptionalInt.empty()
			);
		}
	}

	record NodeMeta(
		String id,
		Type type,
		Priority priority,
		NodeStyle style,
		ComponentControl clientControl
	) {
		public NodeMeta {
			id = ClientHudSnapshot.boundedId(id, "node id");
			type = Objects.requireNonNull(type, "type");
			priority = Objects.requireNonNull(priority, "priority");
			style = Objects.requireNonNull(style, "style");
			clientControl = Objects.requireNonNull(clientControl, "clientControl");
			if (!clientControl.id().equals(id)) {
				throw new IllegalArgumentException("node/client-control IDs differ");
			}
		}
	}

	record TextNode(
		NodeMeta meta,
		Component value,
		int maximumLines,
		Overflow overflow,
		Alignment alignment
	) implements ClientHudNode {
		public TextNode {
			meta = typed(meta, Type.TEXT);
			value = copy(value);
			if (maximumLines <= 0 || maximumLines > 8) {
				throw new IllegalArgumentException("text maximumLines is invalid");
			}
			overflow = Objects.requireNonNull(overflow, "overflow");
			alignment = Objects.requireNonNull(alignment, "alignment");
		}
	}

	record ImageNode(
		NodeMeta meta,
		Identifier texture,
		int width,
		int height,
		int tint,
		Component alt,
		ImageFit fit
	) implements ClientHudNode {
		public ImageNode {
			meta = typed(meta, Type.IMAGE);
			texture = Objects.requireNonNull(texture, "texture");
			if (width <= 0 || width > 256 || height <= 0 || height > 256) {
				throw new IllegalArgumentException("image dimensions are invalid");
			}
			alt = copy(alt);
			fit = Objects.requireNonNull(fit, "fit");
		}
	}

	record PlayerHeadNode(
		NodeMeta meta,
		UUID playerId,
		String playerName,
		int size,
		boolean showHat,
		boolean offline
	) implements ClientHudNode {
		public PlayerHeadNode {
			meta = typed(meta, Type.PLAYER_HEAD);
			playerId = Objects.requireNonNull(playerId, "playerId");
			playerName = Objects.requireNonNull(playerName, "playerName").strip();
			if (playerName.length() > 64 || size < 8 || size > 64) {
				throw new IllegalArgumentException("player-head projection is invalid");
			}
		}
	}

	record CounterNode(
		NodeMeta meta,
		Component label,
		Component value,
		Optional<Component> suffix
	) implements ClientHudNode {
		public CounterNode {
			meta = typed(meta, Type.COUNTER);
			label = copy(label);
			value = copy(value);
			suffix = copied(suffix);
		}
	}

	record BadgeNode(NodeMeta meta, Component label, int color) implements ClientHudNode {
		public BadgeNode {
			meta = typed(meta, Type.BADGE);
			label = copy(label);
		}
	}

	record ProgressNode(
		NodeMeta meta,
		double current,
		double maximum,
		Orientation orientation,
		Optional<Component> label,
		int segments,
		boolean smooth,
		boolean spine
	) implements ClientHudNode {
		public ProgressNode {
			meta = typed(meta, Type.PROGRESS);
			if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0D) {
				throw new IllegalArgumentException("progress projection is invalid");
			}
			current = Math.clamp(current, 0.0D, maximum);
			orientation = Objects.requireNonNull(orientation, "orientation");
			label = copied(label);
			if (segments < 0 || segments > 64) {
				throw new IllegalArgumentException("progress segments are invalid");
			}
		}

		public double fraction() {
			return Math.clamp(this.current / this.maximum, 0.0D, 1.0D);
		}
	}

	record TimerNode(
		NodeMeta meta,
		TimerValue timer,
		Optional<Component> label
	) implements ClientHudNode {
		public TimerNode {
			meta = typed(meta, Type.TIMER);
			timer = Objects.requireNonNull(timer, "timer");
			label = copied(label);
		}
	}

	record SeparatorNode(
		NodeMeta meta,
		Orientation orientation,
		int thickness,
		int color
	) implements ClientHudNode {
		public SeparatorNode {
			meta = typed(meta, Type.SEPARATOR);
			orientation = Objects.requireNonNull(orientation, "orientation");
			if (thickness <= 0 || thickness > 8) {
				throw new IllegalArgumentException("separator thickness is invalid");
			}
		}
	}

	record BackgroundNode(
		NodeMeta meta,
		String child,
		int fill,
		int borderColor,
		int borderWidth,
		Insets innerPadding
	) implements ClientHudNode {
		public BackgroundNode {
			meta = typed(meta, Type.BACKGROUND);
			child = ClientHudSnapshot.boundedId(child, "background child");
			if (borderWidth < 0 || borderWidth > 8) {
				throw new IllegalArgumentException("background border width is invalid");
			}
			innerPadding = Objects.requireNonNull(innerPadding, "innerPadding");
		}

		@Override
		public List<String> childIds() {
			return List.of(this.child);
		}
	}

	record ContainerNode(
		NodeMeta meta,
		List<String> children,
		int gap,
		Alignment alignment,
		List<Double> weights
	) implements ClientHudNode {
		public ContainerNode {
			Type type = Objects.requireNonNull(meta, "meta").type();
			if (type != Type.ROW && type != Type.COLUMN && type != Type.OVERLAY && type != Type.REPEAT) {
				throw new IllegalArgumentException("container has a non-container node type");
			}
			children = List.copyOf(children);
			if (children.isEmpty() || children.size() > 32 || gap < 0 || gap > 48) {
				throw new IllegalArgumentException("container child projection is invalid");
			}
			children.forEach(value -> ClientHudSnapshot.boundedId(value, "container child"));
			alignment = Objects.requireNonNull(alignment, "alignment");
			weights = List.copyOf(weights);
			if (!weights.isEmpty() && weights.size() != children.size()) {
				throw new IllegalArgumentException("container weights do not match children");
			}
			if (weights.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0.0D)) {
				throw new IllegalArgumentException("container weights are invalid");
			}
		}

		@Override
		public List<String> childIds() {
			return this.children;
		}
	}

	private static NodeMeta typed(final NodeMeta meta, final Type expected) {
		NodeMeta result = Objects.requireNonNull(meta, "meta");
		if (result.type() != expected) {
			throw new IllegalArgumentException("node type and payload differ");
		}
		return result;
	}

	private static Component copy(final Component value) {
		return Objects.requireNonNull(value, "component").copy();
	}

	private static Optional<Component> copied(final Optional<Component> value) {
		return Objects.requireNonNull(value, "component").map(Component::copy);
	}
}
