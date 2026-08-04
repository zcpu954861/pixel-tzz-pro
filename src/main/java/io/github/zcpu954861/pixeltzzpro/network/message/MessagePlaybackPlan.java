package io.github.zcpu954861.pixeltzzpro.network.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Minimal, per-player V3B playback plan after every authoritative decision has been made.
 *
 * <p>This wire model deliberately has no callback, condition, audience, parameter-source,
 * predicate, storage, score, or variant type. Repetition and dependency schedules are flattened
 * to absolute node offsets before construction. Dynamic values are addressed only by opaque
 * integer slots and arrive through a separate sequenced delta payload.
 */
public record MessagePlaybackPlan(
	UUID instanceId,
	Identifier cueId,
	long generation,
	long sequence,
	long cycle,
	PlanDisposition disposition,
	Optional<UUID> replacedInstanceId,
	ClockAnchor clock,
	ResolvedPolicies policies,
	ResolvedOrigin origin,
	List<ResolvedNode> nodes
) {
	public static final int FORMAT_VERSION = 2;
	public static final int MAX_PLAN_BYTES = 256 * 1024;
	public static final int MAX_NODES = 256;
	public static final int MAX_SEGMENTS_PER_NODE = 256;
	public static final int MAX_TOTAL_SEGMENTS = 4_096;
	public static final int MAX_FIELD_SLOTS = 512;
	public static final int MAX_COMPONENT_CHARACTERS = 16 * 1024;
	public static final int MAX_COMPONENT_UTF8_BYTES = 16 * 1024;
	public static final long MAX_TIMELINE_NANOS = 24L * 60L * 60L * 1_000_000_000L;
	public static final long MAX_TIMELINE_TICKS = 24L * 60L * 60L * 20L;
	private static final int MAX_IDENTIFIER_CHARACTERS = 256;
	private static final int MAX_LOCAL_TEXT = 256;
	private static final int MAX_JSON_DEPTH = 32;
	private static final int MAX_CURSOR_CHARACTERS = 8;
	private static final int MAX_SCRAMBLE_CHARACTERS = 128;
	private static final int MAX_SPEAKER_NAME_CHARACTERS = 64;
	private static final Set<String> FORBIDDEN_DYNAMIC_COMPONENT_KEYS = Set.of(
		"score",
		"selector",
		"nbt"
	);

	public MessagePlaybackPlan {
		instanceId = Objects.requireNonNull(instanceId, "instanceId");
		cueId = requireIdentifier(cueId, "cueId");
		requireNonNegative(generation, "generation");
		requireNonNegative(sequence, "sequence");
		requireNonNegative(cycle, "cycle");
		disposition = Objects.requireNonNull(disposition, "disposition");
		replacedInstanceId = Objects.requireNonNull(
			replacedInstanceId,
			"replacedInstanceId"
		);
		if (
			(disposition == PlanDisposition.REPLACE)
				!= replacedInstanceId.isPresent()
		) {
			throw new IllegalArgumentException(
				"only a REPLACE plan may identify a replaced instance"
			);
		}
		if (
			disposition == PlanDisposition.REPLACE
				&& replacedInstanceId.orElseThrow().equals(instanceId)
		) {
			throw new IllegalArgumentException(
				"a replacement plan must use a new instance id"
			);
		}
		if (disposition == PlanDisposition.REFRESH && cycle == 0L) {
			throw new IllegalArgumentException(
				"a refresh plan must advance to a positive cycle"
			);
		}
		clock = Objects.requireNonNull(clock, "clock");
		policies = Objects.requireNonNull(policies, "policies");
		origin = Objects.requireNonNull(origin, "origin");
		nodes = List.copyOf(nodes);
		if (nodes.isEmpty() || nodes.size() > MAX_NODES) {
			throw new IllegalArgumentException(
				"nodes size must be between 1 and " + MAX_NODES
			);
		}
		long previousStart = -1L;
		Set<Integer> ordinals = new HashSet<>();
		Set<Integer> slots = new HashSet<>();
		int segmentCount = 0;
		for (ResolvedNode node : nodes) {
			Objects.requireNonNull(node, "node");
			if (!ordinals.add(node.ordinal())) {
				throw new IllegalArgumentException("node ordinals must be unique");
			}
			if (node.startOffsetNanos() < previousStart) {
				throw new IllegalArgumentException(
					"nodes must be sorted by non-decreasing start offset"
				);
			}
			previousStart = node.startOffsetNanos();
			if (node instanceof ResolvedTextNode text) {
				segmentCount = Math.addExact(segmentCount, text.segments().size());
				for (ResolvedSegment segment : text.segments()) {
					if (segment instanceof FieldSlotSegment field) {
						// A projected value is locked once per opaque slot, then every
						// reference to that slot shares the exact same immutable value.
						// Only the number of distinct slots is bounded.
						slots.add(field.slot());
					}
				}
			}
		}
		if (segmentCount > MAX_TOTAL_SEGMENTS) {
			throw new IllegalArgumentException(
				"total segment count exceeds " + MAX_TOTAL_SEGMENTS
			);
		}
		if (slots.size() > MAX_FIELD_SLOTS) {
			throw new IllegalArgumentException(
				"field slot count exceeds " + MAX_FIELD_SLOTS
			);
		}
		if (estimatedEncodedBytes(cueId, nodes) > MAX_PLAN_BYTES) {
			throw new IllegalArgumentException(
				"estimated encoded plan size exceeds " + MAX_PLAN_BYTES
			);
		}
	}

	/**
	 * Encodes the complete plan as one bounded binary document.
	 */
	public byte[] encode() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			write(buffer);
			int length = buffer.readableBytes();
			if (length > MAX_PLAN_BYTES) {
				throw new IllegalArgumentException(
					"encoded plan size " + length + " exceeds " + MAX_PLAN_BYTES
				);
			}
			byte[] result = new byte[length];
			buffer.getBytes(buffer.readerIndex(), result);
			return result;
		} finally {
			buffer.release();
		}
	}

	/**
	 * Encodes an authorized append-only node fragment.
	 *
	 * <p>The fragment deliberately contains no cue definition, predicate, audience rule or
	 * callback. Its nodes use the same wire representation as a full plan so a node revealed at
	 * {@code node_start} or {@code field_capture} cannot diverge from the original FORMAT 2
	 * contract.
	 */
	public static byte[] encodeNodeFragment(final List<ResolvedNode> nodes) {
		List<ResolvedNode> validated = validatedNodeFragment(nodes);
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			buffer.writeVarInt(validated.size());
			validated.forEach(node -> writeNode(buffer, node));
			int length = buffer.readableBytes();
			if (length == 0 || length > MAX_PLAN_BYTES) {
				throw new IllegalArgumentException(
					"encoded node fragment must remain within 1.." + MAX_PLAN_BYTES
				);
			}
			byte[] result = new byte[length];
			buffer.getBytes(buffer.readerIndex(), result);
			return result;
		} finally {
			buffer.release();
		}
	}

	/**
	 * Decodes one bounded append-only node fragment.
	 */
	public static List<ResolvedNode> decodeNodeFragment(final byte[] encoded) {
		Objects.requireNonNull(encoded, "encoded");
		if (encoded.length == 0 || encoded.length > MAX_PLAN_BYTES) {
			throw new DecoderException(
				"node fragment length must be between 1 and " + MAX_PLAN_BYTES
			);
		}
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
		try {
			int count = readBoundedSize(buffer, MAX_NODES, "nodes", false);
			List<ResolvedNode> nodes = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				nodes.add(readNode(buffer));
			}
			if (buffer.isReadable()) {
				throw new DecoderException("node fragment contains trailing data");
			}
			return validatedNodeFragment(nodes);
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message node fragment", error);
		} finally {
			buffer.release();
		}
	}

	/**
	 * Strictly decodes one complete bounded plan document and rejects trailing data.
	 */
	public static MessagePlaybackPlan decode(final byte[] encoded) {
		Objects.requireNonNull(encoded, "encoded");
		if (encoded.length == 0 || encoded.length > MAX_PLAN_BYTES) {
			throw new DecoderException(
				"message plan length must be between 1 and " + MAX_PLAN_BYTES
			);
		}
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
		try {
			MessagePlaybackPlan plan = read(buffer);
			if (buffer.isReadable()) {
				throw new DecoderException("message plan contains trailing data");
			}
			return plan;
		} catch (IndexOutOfBoundsException | IllegalArgumentException error) {
			if (error instanceof DecoderException decoder) {
				throw decoder;
			}
			throw new DecoderException("invalid message plan", error);
		} finally {
			buffer.release();
		}
	}

	private static MessagePlaybackPlan read(final FriendlyByteBuf buffer) {
		int format = buffer.readVarInt();
		if (format != FORMAT_VERSION) {
			throw new DecoderException("unsupported message plan format " + format);
		}
		UUID instanceId = buffer.readUUID();
		Identifier cueId = readIdentifier(buffer, "cueId");
		long generation = buffer.readVarLong();
		long sequence = buffer.readVarLong();
		long cycle = buffer.readVarLong();
		PlanDisposition disposition = readEnum(
			buffer,
			PlanDisposition.class,
			"disposition"
		);
		Optional<UUID> replacedInstanceId = readOptional(
			buffer,
			buffer::readUUID
		);
		ClockAnchor clock = ClockAnchor.read(buffer);
		ResolvedPolicies policies = ResolvedPolicies.read(buffer);
		ResolvedOrigin origin = ResolvedOrigin.read(buffer);
		int count = readBoundedSize(buffer, MAX_NODES, "nodes", false);
		List<ResolvedNode> nodes = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			nodes.add(readNode(buffer));
		}
		return new MessagePlaybackPlan(
			instanceId,
			cueId,
			generation,
			sequence,
			cycle,
			disposition,
			replacedInstanceId,
			clock,
			policies,
			origin,
			nodes
		);
	}

	private void write(final FriendlyByteBuf buffer) {
		buffer.writeVarInt(FORMAT_VERSION);
		buffer.writeUUID(this.instanceId);
		writeIdentifier(buffer, this.cueId);
		buffer.writeVarLong(this.generation);
		buffer.writeVarLong(this.sequence);
		buffer.writeVarLong(this.cycle);
		writeEnum(buffer, this.disposition);
		writeOptional(buffer, this.replacedInstanceId, buffer::writeUUID);
		this.clock.write(buffer);
		this.policies.write(buffer);
		this.origin.write(buffer);
		buffer.writeVarInt(this.nodes.size());
		this.nodes.forEach(node -> writeNode(buffer, node));
	}

	public enum ClockKind {
		GAME_TICK,
		PRESENTATION_NANOS
	}

	public enum PlanDisposition {
		CREATE,
		REFRESH,
		REPLACE
	}

	public record ClockAnchor(ClockKind kind, long serverNow, long startAt) {
		public ClockAnchor {
			kind = Objects.requireNonNull(kind, "kind");
			requireNonNegative(serverNow, "clock.serverNow");
			requireNonNegative(startAt, "clock.startAt");
			long maximumDifference = kind == ClockKind.GAME_TICK
				? MAX_TIMELINE_TICKS
				: MAX_TIMELINE_NANOS;
			if (absoluteDifference(serverNow, startAt) > maximumDifference) {
				throw new IllegalArgumentException(
					"clock start must remain within the bounded presentation horizon"
				);
			}
		}

		private static ClockAnchor read(final FriendlyByteBuf buffer) {
			return new ClockAnchor(
				readEnum(buffer, ClockKind.class, "clock.kind"),
				buffer.readVarLong(),
				buffer.readVarLong()
			);
		}

		private void write(final FriendlyByteBuf buffer) {
			writeEnum(buffer, this.kind);
			buffer.writeVarLong(this.serverNow);
			buffer.writeVarLong(this.startAt);
		}
	}

	/**
	 * Client-executable policy projection after every data-pack default and override is resolved.
	 *
	 * <p>Reconnect, late-join, server reload, and restart remain server decisions and therefore do
	 * not cross this boundary. These values only govern the already-authorized local presentation.
	 */
	public record ResolvedPolicies(
		ChatInterrupt chatInterrupt,
		Map<Channel, ScreenStart> screenStart,
		Map<Channel, Obscured> obscured,
		OptionalLong screenWaitTimeoutNanos,
		ScreenTimeoutAction screenTimeoutAction,
		OptionalLong maxPauseNanos,
		ExternalConflict externalConflict,
		ResourceReload resourceReload,
		ReducedMotion reducedMotion,
		boolean allowManualComplete,
		Respawn respawn,
		DimensionExit dimensionExit
	) {
		public ResolvedPolicies {
			chatInterrupt = Objects.requireNonNull(
				chatInterrupt,
				"chatInterrupt"
			);
			screenStart = completeChannelMap(screenStart, "screenStart");
			obscured = completeChannelMap(obscured, "obscured");
			screenWaitTimeoutNanos = requireOptionalTimelineDuration(
				screenWaitTimeoutNanos,
				"screenWaitTimeoutNanos"
			);
			screenTimeoutAction = Objects.requireNonNull(
				screenTimeoutAction,
				"screenTimeoutAction"
			);
			maxPauseNanos = requireOptionalTimelineDuration(
				maxPauseNanos,
				"maxPauseNanos"
			);
			externalConflict = Objects.requireNonNull(
				externalConflict,
				"externalConflict"
			);
			resourceReload = Objects.requireNonNull(
				resourceReload,
				"resourceReload"
			);
			reducedMotion = Objects.requireNonNull(
				reducedMotion,
				"reducedMotion"
			);
			respawn = Objects.requireNonNull(respawn, "respawn");
			dimensionExit = Objects.requireNonNull(
				dimensionExit,
				"dimensionExit"
			);
		}

		public static ResolvedPolicies standard() {
			EnumMap<Channel, ScreenStart> starts = new EnumMap<>(Channel.class);
			EnumMap<Channel, Obscured> covered = new EnumMap<>(Channel.class);
			for (Channel channel : Channel.values()) {
				starts.put(
					channel,
					channel == Channel.CHAT
						? ScreenStart.IMMEDIATE
						: ScreenStart.WAIT_GAMEPLAY
				);
				covered.put(
					channel,
					channel == Channel.CHAT
						? Obscured.CONTINUE
						: Obscured.PAUSE
				);
			}
			return new ResolvedPolicies(
				ChatInterrupt.FINALIZE,
				starts,
				covered,
				OptionalLong.empty(),
				ScreenTimeoutAction.FINALIZE,
				OptionalLong.empty(),
				ExternalConflict.YIELD,
				ResourceReload.FINISH,
				ReducedMotion.SIMPLIFIED,
				false,
				Respawn.CONTINUE,
				DimensionExit.CONTINUE
			);
		}

		private static ResolvedPolicies read(final FriendlyByteBuf buffer) {
			ChatInterrupt chatInterrupt = readEnum(
				buffer,
				ChatInterrupt.class,
				"policies.chatInterrupt"
			);
			EnumMap<Channel, ScreenStart> starts = new EnumMap<>(Channel.class);
			EnumMap<Channel, Obscured> covered = new EnumMap<>(Channel.class);
			for (Channel channel : Channel.values()) {
				starts.put(
					channel,
					readEnum(
						buffer,
						ScreenStart.class,
						"policies.screenStart." + channel.name().toLowerCase()
					)
				);
			}
			for (Channel channel : Channel.values()) {
				covered.put(
					channel,
					readEnum(
						buffer,
						Obscured.class,
						"policies.obscured." + channel.name().toLowerCase()
					)
				);
			}
			return new ResolvedPolicies(
				chatInterrupt,
				starts,
				covered,
				readOptionalLong(buffer),
				readEnum(
					buffer,
					ScreenTimeoutAction.class,
					"policies.screenTimeoutAction"
				),
				readOptionalLong(buffer),
				readEnum(
					buffer,
					ExternalConflict.class,
					"policies.externalConflict"
				),
				readEnum(
					buffer,
					ResourceReload.class,
					"policies.resourceReload"
				),
				readEnum(
					buffer,
					ReducedMotion.class,
					"policies.reducedMotion"
				),
				buffer.readBoolean(),
				readEnum(buffer, Respawn.class, "policies.respawn"),
				readEnum(
					buffer,
					DimensionExit.class,
					"policies.dimensionExit"
				)
			);
		}

		private void write(final FriendlyByteBuf buffer) {
			writeEnum(buffer, this.chatInterrupt);
			for (Channel channel : Channel.values()) {
				writeEnum(buffer, this.screenStart.get(channel));
			}
			for (Channel channel : Channel.values()) {
				writeEnum(buffer, this.obscured.get(channel));
			}
			writeOptionalLong(buffer, this.screenWaitTimeoutNanos);
			writeEnum(buffer, this.screenTimeoutAction);
			writeOptionalLong(buffer, this.maxPauseNanos);
			writeEnum(buffer, this.externalConflict);
			writeEnum(buffer, this.resourceReload);
			writeEnum(buffer, this.reducedMotion);
			buffer.writeBoolean(this.allowManualComplete);
			writeEnum(buffer, this.respawn);
			writeEnum(buffer, this.dimensionExit);
		}
	}

	public enum ChatInterrupt {
		FINALIZE,
		REMOVE
	}

	public enum ScreenStart {
		IMMEDIATE,
		WAIT_GAMEPLAY,
		CLOSE_SAFE_SCREEN,
		FINAL_CHAT_ONLY
	}

	public enum ScreenTimeoutAction {
		FINALIZE,
		CANCEL,
		FINAL_CHAT_ONLY
	}

	public enum Obscured {
		CONTINUE,
		PAUSE,
		FINALIZE,
		CANCEL
	}

	public enum ExternalConflict {
		YIELD,
		PAUSE,
		OFFSET,
		OVERLAY
	}

	public enum ResourceReload {
		FINISH,
		FINALIZE,
		CANCEL
	}

	public enum ReducedMotion {
		STATIC_FINAL,
		SIMPLIFIED
	}

	public enum Respawn {
		CONTINUE,
		FINALIZE,
		CANCEL
	}

	public enum DimensionExit {
		CONTINUE,
		FINALIZE,
		CANCEL
	}

	/**
	 * Resolved invocation origin. It is presentation data only and never a client-selected target.
	 */
	public record ResolvedOrigin(
		Identifier dimension,
		double x,
		double y,
		double z
	) {
		private static final double MAX_ABSOLUTE_COORDINATE = 30_000_000.0D;

		public ResolvedOrigin {
			dimension = requireIdentifier(dimension, "origin.dimension");
			x = boundedCoordinate(x, "origin.x");
			y = boundedCoordinate(y, "origin.y");
			z = boundedCoordinate(z, "origin.z");
		}

		private static ResolvedOrigin read(final FriendlyByteBuf buffer) {
			return new ResolvedOrigin(
				readIdentifier(buffer, "origin.dimension"),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble()
			);
		}

		private void write(final FriendlyByteBuf buffer) {
			writeIdentifier(buffer, this.dimension);
			buffer.writeDouble(this.x);
			buffer.writeDouble(this.y);
			buffer.writeDouble(this.z);
		}

		private static double boundedCoordinate(
			final double value,
			final String field
		) {
			if (
				!Double.isFinite(value)
					|| Math.abs(value) > MAX_ABSOLUTE_COORDINATE
			) {
				throw new IllegalArgumentException(
					field
						+ " must be finite and remain within +/-"
						+ MAX_ABSOLUTE_COORDINATE
				);
			}
			return value;
		}
	}

	public enum Channel {
		CHAT,
		TITLE,
		SUBTITLE,
		ACTION_BAR
	}

	public enum Conflict {
		PARALLEL,
		QUEUE,
		REPLACE,
		DISCARD
	}

	public enum SoundCategory {
		MASTER,
		MUSIC,
		RECORD,
		WEATHER,
		BLOCK,
		HOSTILE,
		NEUTRAL,
		PLAYER,
		AMBIENT,
		VOICE,
		UI
	}

	public enum SoundAttachment {
		PLAYER,
		WORLD_ORIGIN
	}

	public sealed interface ResolvedNode permits ResolvedTextNode, ResolvedSoundNode {
		int ordinal();

		long startOffsetNanos();

		int priority();

		Conflict conflict();
	}

	public record ResolvedTextNode(
		int ordinal,
		long startOffsetNanos,
		int priority,
		Conflict conflict,
		Channel channel,
		List<ResolvedSegment> segments,
		ResolvedEffect effect,
		ResolvedLayout layout,
		ResolvedDuration duration,
		ResolvedSpeaker speaker
	) implements ResolvedNode {
		public ResolvedTextNode {
			validateCommonNode(ordinal, startOffsetNanos, priority, conflict);
			channel = Objects.requireNonNull(channel, "channel");
			segments = List.copyOf(segments);
			if (segments.isEmpty() || segments.size() > MAX_SEGMENTS_PER_NODE) {
				throw new IllegalArgumentException(
					"segments size must be between 1 and " + MAX_SEGMENTS_PER_NODE
				);
			}
			segments.forEach(segment -> Objects.requireNonNull(segment, "segment"));
			effect = Objects.requireNonNull(effect, "effect");
			layout = Objects.requireNonNull(layout, "layout");
			duration = Objects.requireNonNull(duration, "duration");
			speaker = Objects.requireNonNull(speaker, "speaker");
		}
	}

	public record ResolvedSoundNode(
		int ordinal,
		long startOffsetNanos,
		int priority,
		Conflict conflict,
		Identifier sound,
		SoundCategory category,
		float volume,
		float pitch,
		SoundAttachment attachment
	) implements ResolvedNode {
		public ResolvedSoundNode {
			validateCommonNode(ordinal, startOffsetNanos, priority, conflict);
			sound = requireIdentifier(sound, "sound");
			category = Objects.requireNonNull(category, "category");
			volume = boundedFloat(volume, 0.0F, 4.0F, "volume");
			pitch = boundedFloat(pitch, 0.0F, 2.0F, "pitch");
			attachment = Objects.requireNonNull(attachment, "attachment");
		}
	}

	public sealed interface ResolvedSegment permits StaticSegment, FieldSlotSegment {
		SegmentTiming timing();
	}

	public record StaticSegment(
		ResolvedComponent component,
		SegmentTiming timing
	) implements ResolvedSegment {
		public StaticSegment {
			component = Objects.requireNonNull(component, "component");
			timing = Objects.requireNonNull(timing, "timing");
		}
	}

	public record FieldSlotSegment(
		int slot,
		Optional<ResolvedComponent> fallback,
		FieldReservation reservation,
		SegmentTiming timing
	) implements ResolvedSegment {
		public FieldSlotSegment {
			if (slot < 0 || slot >= MAX_FIELD_SLOTS) {
				throw new IllegalArgumentException(
					"slot must be between 0 and " + (MAX_FIELD_SLOTS - 1)
				);
			}
			fallback = Objects.requireNonNull(fallback, "fallback");
			reservation = Objects.requireNonNull(reservation, "reservation");
			timing = Objects.requireNonNull(timing, "timing");
		}
	}

	public record SegmentTiming(
		long pauseBeforeNanos,
		long pauseAfterNanos,
		boolean emphasis
	) {
		public SegmentTiming {
			requireTimelineDuration(pauseBeforeNanos, "pauseBeforeNanos");
			requireTimelineDuration(pauseAfterNanos, "pauseAfterNanos");
		}

		public static SegmentTiming none() {
			return new SegmentTiming(0L, 0L, false);
		}
	}

	public record FieldReservation(
		OptionalInt maxGraphemes,
		OptionalInt estimatedWidth,
		OptionalInt maxLines
	) {
		public FieldReservation {
			maxGraphemes = requireOptionalRange(
				maxGraphemes,
				1,
				4_096,
				"maxGraphemes"
			);
			estimatedWidth = requireOptionalRange(
				estimatedWidth,
				1,
				16_384,
				"estimatedWidth"
			);
			maxLines = requireOptionalRange(maxLines, 1, 128, "maxLines");
		}

		public static FieldReservation none() {
			return new FieldReservation(
				OptionalInt.empty(),
				OptionalInt.empty(),
				OptionalInt.empty()
			);
		}
	}

	/**
	 * One canonical, source-free rich component.
	 *
	 * <p>The public parser rejects duplicate keys, non-canonical key order, excessive nesting,
	 * oversized UTF-16/UTF-8 content, and unresolved score/selector/NBT component sources.
	 */
	public record ResolvedComponent(String canonicalJson) {
		public ResolvedComponent {
			canonicalJson = validateCanonicalComponent(canonicalJson);
		}

		public static ResolvedComponent parseCanonical(final String canonicalJson) {
			return new ResolvedComponent(canonicalJson);
		}

		/**
		 * Canonicalizes one component that has already been resolved by the trusted server.
		 *
		 * <p>This is deliberately separate from {@link #parseCanonical(String)}: wire decoding must
		 * continue to reject non-canonical input. The trusted resolver may receive Minecraft codec
		 * output whose object keys are not sorted, so it is reparsed under the same strict bounds,
		 * checked for unresolved dynamic component sources, and only then serialized canonically.</p>
		 */
		public static ResolvedComponent canonicalizeResolved(final String resolvedJson) {
			return new ResolvedComponent(canonicalizeResolvedComponent(resolvedJson));
		}

		public int utf8Bytes() {
			return this.canonicalJson.getBytes(StandardCharsets.UTF_8).length;
		}
	}

	public record ResolvedEffect(
		Optional<Typewriter> typewriter,
		Optional<Fade> fade,
		Optional<Scramble> scramble,
		Optional<Gradient> gradient,
		Optional<Motion> motion,
		Optional<Pulse> pulse,
		Optional<CharacterSound> characterSound,
		int maxCharacterSoundEvents
	) {
		public ResolvedEffect {
			typewriter = Objects.requireNonNull(typewriter, "typewriter");
			fade = Objects.requireNonNull(fade, "fade");
			scramble = Objects.requireNonNull(scramble, "scramble");
			gradient = Objects.requireNonNull(gradient, "gradient");
			motion = Objects.requireNonNull(motion, "motion");
			pulse = Objects.requireNonNull(pulse, "pulse");
			characterSound = Objects.requireNonNull(characterSound, "characterSound");
			if (maxCharacterSoundEvents < 0 || maxCharacterSoundEvents > 4_096) {
				throw new IllegalArgumentException(
					"maxCharacterSoundEvents must be between 0 and 4096"
				);
			}
			if (characterSound.isPresent() && maxCharacterSoundEvents == 0) {
				throw new IllegalArgumentException(
					"character sound requires a positive event budget"
				);
			}
		}

		public static ResolvedEffect none() {
			return new ResolvedEffect(
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				0
			);
		}
	}

	public enum RestoreMode {
		INSTANT,
		GRADIENT
	}

	public record Typewriter(
		long characterIntervalNanos,
		Cursor cursor,
		Optional<String> freshColor,
		int restoreAfterCharacters,
		RestoreMode restoreMode
	) {
		public Typewriter {
			requirePositiveTimelineDuration(characterIntervalNanos, "characterIntervalNanos");
			cursor = Objects.requireNonNull(cursor, "cursor");
			freshColor = Objects.requireNonNull(freshColor, "freshColor")
				.map(value -> requireText(value, 16, "freshColor", false));
			if (restoreAfterCharacters < 0 || restoreAfterCharacters > 256) {
				throw new IllegalArgumentException(
					"restoreAfterCharacters must be between 0 and 256"
				);
			}
			restoreMode = Objects.requireNonNull(restoreMode, "restoreMode");
		}
	}

	public record Cursor(
		boolean enabled,
		String glyph,
		String color,
		boolean blink,
		long blinkPeriodNanos
	) {
		public Cursor {
			glyph = requireText(
				glyph,
				MAX_CURSOR_CHARACTERS,
				"cursor.glyph",
				false
			);
			color = requireText(color, 16, "cursor.color", false);
			if (blink) {
				requirePositiveTimelineDuration(blinkPeriodNanos, "cursor.blinkPeriodNanos");
			} else if (blinkPeriodNanos != 0L) {
				throw new IllegalArgumentException(
					"non-blinking cursor cannot declare a blink period"
				);
			}
		}
	}

	public record Fade(long enterNanos, long holdNanos, long exitNanos) {
		public Fade {
			requireTimelineDuration(enterNanos, "fade.enterNanos");
			requireTimelineDuration(holdNanos, "fade.holdNanos");
			requireTimelineDuration(exitNanos, "fade.exitNanos");
		}
	}

	public record Scramble(long characterIntervalNanos, String characters) {
		public Scramble {
			requirePositiveTimelineDuration(
				characterIntervalNanos,
				"scramble.characterIntervalNanos"
			);
			characters = requireText(
				characters,
				MAX_SCRAMBLE_CHARACTERS,
				"scramble.characters",
				false
			);
		}
	}

	public record Gradient(String fromColor, String toColor, long durationNanos) {
		public Gradient {
			fromColor = requireText(fromColor, 16, "gradient.fromColor", false);
			toColor = requireText(toColor, 16, "gradient.toColor", false);
			requirePositiveTimelineDuration(durationNanos, "gradient.durationNanos");
		}
	}

	public record Motion(
		int slideX,
		int slideY,
		float startScale,
		long durationNanos
	) {
		public Motion {
			if (slideX < -64 || slideX > 64 || slideY < -64 || slideY > 64) {
				throw new IllegalArgumentException("motion slide must remain within -64..64");
			}
			startScale = boundedFloat(startScale, 0.75F, 1.25F, "motion.startScale");
			requirePositiveTimelineDuration(durationNanos, "motion.durationNanos");
		}
	}

	public record Pulse(float scale, long durationNanos) {
		public Pulse {
			scale = boundedFloat(scale, 1.0F, 1.5F, "pulse.scale");
			requirePositiveTimelineDuration(durationNanos, "pulse.durationNanos");
		}
	}

	public record CharacterSound(
		Identifier sound,
		SoundCategory category,
		int everyCharacters,
		boolean skipWhitespace,
		boolean skipPunctuation,
		boolean skipNewline,
		float volume,
		float pitch,
		float pitchVariation,
		SoundAttachment attachment
	) {
		public CharacterSound {
			sound = requireIdentifier(sound, "characterSound.sound");
			category = Objects.requireNonNull(category, "category");
			if (everyCharacters < 1 || everyCharacters > 64) {
				throw new IllegalArgumentException(
					"everyCharacters must be between 1 and 64"
				);
			}
			volume = boundedFloat(volume, 0.0F, 4.0F, "characterSound.volume");
			pitch = boundedFloat(pitch, 0.0F, 2.0F, "characterSound.pitch");
			pitchVariation = boundedFloat(
				pitchVariation,
				0.0F,
				2.0F,
				"characterSound.pitchVariation"
			);
			attachment = Objects.requireNonNull(attachment, "attachment");
		}
	}

	public enum Alignment {
		LEFT,
		CENTER,
		RIGHT
	}

	public enum Overflow {
		WRAP,
		SCALE,
		ELLIPSIS,
		STATIC_FINAL
	}

	public record ResolvedLayout(
		Alignment alignment,
		int offsetX,
		int offsetY,
		OptionalInt maxWidth,
		OptionalInt maxLines,
		float lineSpacing,
		Overflow overflow
	) {
		public ResolvedLayout {
			alignment = Objects.requireNonNull(alignment, "alignment");
			if (offsetX < -256 || offsetX > 256 || offsetY < -256 || offsetY > 256) {
				throw new IllegalArgumentException("layout offsets must remain within -256..256");
			}
			maxWidth = requireOptionalRange(maxWidth, 1, 8_192, "maxWidth");
			maxLines = requireOptionalRange(maxLines, 1, 128, "maxLines");
			lineSpacing = boundedFloat(lineSpacing, 0.5F, 4.0F, "lineSpacing");
			overflow = Objects.requireNonNull(overflow, "overflow");
		}

		public static ResolvedLayout standard() {
			return new ResolvedLayout(
				Alignment.CENTER,
				0,
				0,
				OptionalInt.empty(),
				OptionalInt.empty(),
				1.0F,
				Overflow.WRAP
			);
		}
	}

	public enum DurationMode {
		CONTENT_DRIVEN,
		FIXED_TOTAL
	}

	public record ResolvedDuration(
		DurationMode mode,
		Optional<Long> totalNanos,
		Optional<Float> minimumSpeed,
		Optional<Float> maximumSpeed,
		long holdNanos
	) {
		public ResolvedDuration {
			mode = Objects.requireNonNull(mode, "mode");
			totalNanos = Objects.requireNonNull(totalNanos, "totalNanos");
			minimumSpeed = Objects.requireNonNull(minimumSpeed, "minimumSpeed")
				.map(value -> boundedFloat(value, 0.01F, 1_000.0F, "minimumSpeed"));
			maximumSpeed = Objects.requireNonNull(maximumSpeed, "maximumSpeed")
				.map(value -> boundedFloat(value, 0.01F, 1_000.0F, "maximumSpeed"));
			if (mode == DurationMode.FIXED_TOTAL && totalNanos.isEmpty()) {
				throw new IllegalArgumentException("fixed duration requires totalNanos");
			}
			if (mode == DurationMode.CONTENT_DRIVEN && totalNanos.isPresent()) {
				throw new IllegalArgumentException(
					"content-driven duration cannot declare totalNanos"
				);
			}
			totalNanos.ifPresent(
				value -> requirePositiveTimelineDuration(value, "totalNanos")
			);
			if (
				minimumSpeed.isPresent()
					&& maximumSpeed.isPresent()
					&& minimumSpeed.orElseThrow() > maximumSpeed.orElseThrow()
			) {
				throw new IllegalArgumentException(
					"minimumSpeed must not exceed maximumSpeed"
				);
			}
			requireTimelineDuration(holdNanos, "holdNanos");
		}

		public static ResolvedDuration contentDriven(final long holdNanos) {
			return new ResolvedDuration(
				DurationMode.CONTENT_DRIVEN,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				holdNanos
			);
		}
	}

	public enum SpeakerKind {
		SYSTEM,
		NARRATOR,
		PLAYER,
		ENTITY
	}

	/**
	 * Server-verified speaker decoration for one projected text node.
	 *
	 * <p>{@link SpeakerKind#ENTITY} carries only the registered entity type's resolved visible name.
	 * It intentionally carries neither an entity-type identifier nor a player profile: generic entity
	 * speakers do not synthesize UUIDs, skins, or player-chat identity.</p>
	 */
	public record ResolvedSpeaker(
		SpeakerKind kind,
		Optional<UUID> profileId,
		Optional<String> displayName
	) {
		public ResolvedSpeaker {
			kind = Objects.requireNonNull(kind, "kind");
			profileId = Objects.requireNonNull(profileId, "profileId");
			displayName = Objects.requireNonNull(displayName, "displayName")
				.map(value -> requireText(
					value,
					MAX_SPEAKER_NAME_CHARACTERS,
					"displayName",
					false
				));
			switch (kind) {
				case SYSTEM, NARRATOR -> {
					if (profileId.isPresent() || displayName.isPresent()) {
						throw new IllegalArgumentException(
							"system and narrator speakers cannot expose an identity"
						);
					}
				}
				case PLAYER -> {
					if (profileId.isEmpty() || displayName.isEmpty()) {
						throw new IllegalArgumentException(
							"player speaker requires verified UUID and visible name"
						);
					}
				}
				case ENTITY -> {
					if (profileId.isPresent()) {
						throw new IllegalArgumentException(
							"entity speaker cannot expose a player profile"
						);
					}
					if (displayName.isEmpty()) {
						throw new IllegalArgumentException(
							"entity speaker requires a visible name"
						);
					}
				}
			}
		}

		public static ResolvedSpeaker system() {
			return new ResolvedSpeaker(
				SpeakerKind.SYSTEM,
				Optional.empty(),
				Optional.empty()
			);
		}
	}

	private static ResolvedNode readNode(final FriendlyByteBuf buffer) {
		int type = buffer.readUnsignedByte();
		int ordinal = buffer.readVarInt();
		long startOffset = buffer.readVarLong();
		int priority = buffer.readInt();
		Conflict conflict = readEnum(buffer, Conflict.class, "node.conflict");
		return switch (type) {
			case 0 -> readTextNode(
				buffer,
				ordinal,
				startOffset,
				priority,
				conflict
			);
			case 1 -> readSoundNode(
				buffer,
				ordinal,
				startOffset,
				priority,
				conflict
			);
			default -> throw new DecoderException("unknown resolved node type " + type);
		};
	}

	private static ResolvedTextNode readTextNode(
		final FriendlyByteBuf buffer,
		final int ordinal,
		final long startOffset,
		final int priority,
		final Conflict conflict
	) {
		Channel channel = readEnum(buffer, Channel.class, "text.channel");
		int count = readBoundedSize(
			buffer,
			MAX_SEGMENTS_PER_NODE,
			"text.segments",
			false
		);
		List<ResolvedSegment> segments = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			segments.add(readSegment(buffer));
		}
		return new ResolvedTextNode(
			ordinal,
			startOffset,
			priority,
			conflict,
			channel,
			segments,
			readEffect(buffer),
			readLayout(buffer),
			readDuration(buffer),
			readSpeaker(buffer)
		);
	}

	private static ResolvedSoundNode readSoundNode(
		final FriendlyByteBuf buffer,
		final int ordinal,
		final long startOffset,
		final int priority,
		final Conflict conflict
	) {
		return new ResolvedSoundNode(
			ordinal,
			startOffset,
			priority,
			conflict,
			readIdentifier(buffer, "sound.sound"),
			readEnum(buffer, SoundCategory.class, "sound.category"),
			buffer.readFloat(),
			buffer.readFloat(),
			readEnum(buffer, SoundAttachment.class, "sound.attachment")
		);
	}

	private static void writeNode(final FriendlyByteBuf buffer, final ResolvedNode node) {
		buffer.writeByte(node instanceof ResolvedTextNode ? 0 : 1);
		buffer.writeVarInt(node.ordinal());
		buffer.writeVarLong(node.startOffsetNanos());
		buffer.writeInt(node.priority());
		writeEnum(buffer, node.conflict());
		if (node instanceof ResolvedTextNode text) {
			writeEnum(buffer, text.channel());
			buffer.writeVarInt(text.segments().size());
			text.segments().forEach(segment -> writeSegment(buffer, segment));
			writeEffect(buffer, text.effect());
			writeLayout(buffer, text.layout());
			writeDuration(buffer, text.duration());
			writeSpeaker(buffer, text.speaker());
			return;
		}
		ResolvedSoundNode sound = (ResolvedSoundNode)node;
		writeIdentifier(buffer, sound.sound());
		writeEnum(buffer, sound.category());
		buffer.writeFloat(sound.volume());
		buffer.writeFloat(sound.pitch());
		writeEnum(buffer, sound.attachment());
	}

	private static ResolvedSegment readSegment(final FriendlyByteBuf buffer) {
		int type = buffer.readUnsignedByte();
		SegmentTiming timing = readSegmentTiming(buffer);
		return switch (type) {
			case 0 -> new StaticSegment(readComponent(buffer), timing);
			case 1 -> new FieldSlotSegment(
				buffer.readVarInt(),
				readOptional(buffer, () -> readComponent(buffer)),
				readFieldReservation(buffer),
				timing
			);
			default -> throw new DecoderException("unknown resolved segment type " + type);
		};
	}

	private static void writeSegment(
		final FriendlyByteBuf buffer,
		final ResolvedSegment segment
	) {
		buffer.writeByte(segment instanceof StaticSegment ? 0 : 1);
		writeSegmentTiming(buffer, segment.timing());
		if (segment instanceof StaticSegment text) {
			writeComponent(buffer, text.component());
			return;
		}
		FieldSlotSegment field = (FieldSlotSegment)segment;
		buffer.writeVarInt(field.slot());
		writeOptional(buffer, field.fallback(), value -> writeComponent(buffer, value));
		writeFieldReservation(buffer, field.reservation());
	}

	private static SegmentTiming readSegmentTiming(final FriendlyByteBuf buffer) {
		return new SegmentTiming(
			buffer.readVarLong(),
			buffer.readVarLong(),
			buffer.readBoolean()
		);
	}

	private static void writeSegmentTiming(
		final FriendlyByteBuf buffer,
		final SegmentTiming timing
	) {
		buffer.writeVarLong(timing.pauseBeforeNanos());
		buffer.writeVarLong(timing.pauseAfterNanos());
		buffer.writeBoolean(timing.emphasis());
	}

	private static FieldReservation readFieldReservation(final FriendlyByteBuf buffer) {
		return new FieldReservation(
			readOptionalInt(buffer),
			readOptionalInt(buffer),
			readOptionalInt(buffer)
		);
	}

	private static void writeFieldReservation(
		final FriendlyByteBuf buffer,
		final FieldReservation reservation
	) {
		writeOptionalInt(buffer, reservation.maxGraphemes());
		writeOptionalInt(buffer, reservation.estimatedWidth());
		writeOptionalInt(buffer, reservation.maxLines());
	}

	private static ResolvedComponent readComponent(final FriendlyByteBuf buffer) {
		return ResolvedComponent.parseCanonical(
			buffer.readUtf(MAX_COMPONENT_CHARACTERS)
		);
	}

	private static void writeComponent(
		final FriendlyByteBuf buffer,
		final ResolvedComponent component
	) {
		buffer.writeUtf(component.canonicalJson(), MAX_COMPONENT_CHARACTERS);
	}

	private static ResolvedEffect readEffect(final FriendlyByteBuf buffer) {
		return new ResolvedEffect(
			readOptional(buffer, () -> readTypewriter(buffer)),
			readOptional(buffer, () -> new Fade(
				buffer.readVarLong(),
				buffer.readVarLong(),
				buffer.readVarLong()
			)),
			readOptional(buffer, () -> new Scramble(
				buffer.readVarLong(),
				buffer.readUtf(MAX_SCRAMBLE_CHARACTERS)
			)),
			readOptional(buffer, () -> new Gradient(
				buffer.readUtf(16),
				buffer.readUtf(16),
				buffer.readVarLong()
			)),
			readOptional(buffer, () -> new Motion(
				buffer.readInt(),
				buffer.readInt(),
				buffer.readFloat(),
				buffer.readVarLong()
			)),
			readOptional(buffer, () -> new Pulse(
				buffer.readFloat(),
				buffer.readVarLong()
			)),
			readOptional(buffer, () -> readCharacterSound(buffer)),
			buffer.readVarInt()
		);
	}

	private static void writeEffect(
		final FriendlyByteBuf buffer,
		final ResolvedEffect effect
	) {
		writeOptional(buffer, effect.typewriter(), value -> writeTypewriter(buffer, value));
		writeOptional(buffer, effect.fade(), value -> {
			buffer.writeVarLong(value.enterNanos());
			buffer.writeVarLong(value.holdNanos());
			buffer.writeVarLong(value.exitNanos());
		});
		writeOptional(buffer, effect.scramble(), value -> {
			buffer.writeVarLong(value.characterIntervalNanos());
			buffer.writeUtf(value.characters(), MAX_SCRAMBLE_CHARACTERS);
		});
		writeOptional(buffer, effect.gradient(), value -> {
			buffer.writeUtf(value.fromColor(), 16);
			buffer.writeUtf(value.toColor(), 16);
			buffer.writeVarLong(value.durationNanos());
		});
		writeOptional(buffer, effect.motion(), value -> {
			buffer.writeInt(value.slideX());
			buffer.writeInt(value.slideY());
			buffer.writeFloat(value.startScale());
			buffer.writeVarLong(value.durationNanos());
		});
		writeOptional(buffer, effect.pulse(), value -> {
			buffer.writeFloat(value.scale());
			buffer.writeVarLong(value.durationNanos());
		});
		writeOptional(
			buffer,
			effect.characterSound(),
			value -> writeCharacterSound(buffer, value)
		);
		buffer.writeVarInt(effect.maxCharacterSoundEvents());
	}

	private static Typewriter readTypewriter(final FriendlyByteBuf buffer) {
		return new Typewriter(
			buffer.readVarLong(),
			new Cursor(
				buffer.readBoolean(),
				buffer.readUtf(MAX_CURSOR_CHARACTERS),
				buffer.readUtf(16),
				buffer.readBoolean(),
				buffer.readVarLong()
			),
			readOptional(buffer, () -> buffer.readUtf(16)),
			buffer.readVarInt(),
			readEnum(buffer, RestoreMode.class, "typewriter.restoreMode")
		);
	}

	private static void writeTypewriter(
		final FriendlyByteBuf buffer,
		final Typewriter typewriter
	) {
		buffer.writeVarLong(typewriter.characterIntervalNanos());
		buffer.writeBoolean(typewriter.cursor().enabled());
		buffer.writeUtf(typewriter.cursor().glyph(), MAX_CURSOR_CHARACTERS);
		buffer.writeUtf(typewriter.cursor().color(), 16);
		buffer.writeBoolean(typewriter.cursor().blink());
		buffer.writeVarLong(typewriter.cursor().blinkPeriodNanos());
		writeOptional(
			buffer,
			typewriter.freshColor(),
			value -> buffer.writeUtf(value, 16)
		);
		buffer.writeVarInt(typewriter.restoreAfterCharacters());
		writeEnum(buffer, typewriter.restoreMode());
	}

	private static CharacterSound readCharacterSound(final FriendlyByteBuf buffer) {
		return new CharacterSound(
			readIdentifier(buffer, "characterSound.sound"),
			readEnum(buffer, SoundCategory.class, "characterSound.category"),
			buffer.readVarInt(),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readBoolean(),
			buffer.readFloat(),
			buffer.readFloat(),
			buffer.readFloat(),
			readEnum(buffer, SoundAttachment.class, "characterSound.attachment")
		);
	}

	private static void writeCharacterSound(
		final FriendlyByteBuf buffer,
		final CharacterSound sound
	) {
		writeIdentifier(buffer, sound.sound());
		writeEnum(buffer, sound.category());
		buffer.writeVarInt(sound.everyCharacters());
		buffer.writeBoolean(sound.skipWhitespace());
		buffer.writeBoolean(sound.skipPunctuation());
		buffer.writeBoolean(sound.skipNewline());
		buffer.writeFloat(sound.volume());
		buffer.writeFloat(sound.pitch());
		buffer.writeFloat(sound.pitchVariation());
		writeEnum(buffer, sound.attachment());
	}

	private static ResolvedLayout readLayout(final FriendlyByteBuf buffer) {
		return new ResolvedLayout(
			readEnum(buffer, Alignment.class, "layout.alignment"),
			buffer.readInt(),
			buffer.readInt(),
			readOptionalInt(buffer),
			readOptionalInt(buffer),
			buffer.readFloat(),
			readEnum(buffer, Overflow.class, "layout.overflow")
		);
	}

	private static void writeLayout(
		final FriendlyByteBuf buffer,
		final ResolvedLayout layout
	) {
		writeEnum(buffer, layout.alignment());
		buffer.writeInt(layout.offsetX());
		buffer.writeInt(layout.offsetY());
		writeOptionalInt(buffer, layout.maxWidth());
		writeOptionalInt(buffer, layout.maxLines());
		buffer.writeFloat(layout.lineSpacing());
		writeEnum(buffer, layout.overflow());
	}

	private static ResolvedDuration readDuration(final FriendlyByteBuf buffer) {
		return new ResolvedDuration(
			readEnum(buffer, DurationMode.class, "duration.mode"),
			readOptional(buffer, buffer::readVarLong),
			readOptional(buffer, buffer::readFloat),
			readOptional(buffer, buffer::readFloat),
			buffer.readVarLong()
		);
	}

	private static void writeDuration(
		final FriendlyByteBuf buffer,
		final ResolvedDuration duration
	) {
		writeEnum(buffer, duration.mode());
		writeOptional(buffer, duration.totalNanos(), buffer::writeVarLong);
		writeOptional(buffer, duration.minimumSpeed(), buffer::writeFloat);
		writeOptional(buffer, duration.maximumSpeed(), buffer::writeFloat);
		buffer.writeVarLong(duration.holdNanos());
	}

	private static ResolvedSpeaker readSpeaker(final FriendlyByteBuf buffer) {
		return new ResolvedSpeaker(
			readEnum(buffer, SpeakerKind.class, "speaker.kind"),
			readOptional(buffer, buffer::readUUID),
			readOptional(
				buffer,
				() -> buffer.readUtf(MAX_SPEAKER_NAME_CHARACTERS)
			)
		);
	}

	private static void writeSpeaker(
		final FriendlyByteBuf buffer,
		final ResolvedSpeaker speaker
	) {
		writeEnum(buffer, speaker.kind());
		writeOptional(buffer, speaker.profileId(), buffer::writeUUID);
		writeOptional(
			buffer,
			speaker.displayName(),
			value -> buffer.writeUtf(value, MAX_SPEAKER_NAME_CHARACTERS)
		);
	}

	private static String validateCanonicalComponent(final String value) {
		String canonical = canonicalizeResolvedComponent(value);
		if (!canonical.equals(value)) {
			throw new IllegalArgumentException(
				"resolved component JSON must use canonical object-key order"
			);
		}
		return value;
	}

	private static String canonicalizeResolvedComponent(final String value) {
		String bounded = requireText(
			value,
			MAX_COMPONENT_CHARACTERS,
			"canonicalJson",
			false
		);
		int bytes = bounded.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > MAX_COMPONENT_UTF8_BYTES) {
			throw new IllegalArgumentException(
				"canonicalJson UTF-8 length exceeds " + MAX_COMPONENT_UTF8_BYTES
			);
		}
		JsonElement parsed;
		try (
			JsonReader reader = new JsonReader(new StringReader(bounded))
		) {
			reader.setStrictness(Strictness.STRICT);
			parsed = readStrictValue(reader, 1);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("trailing JSON content");
			}
		} catch (IOException | RuntimeException error) {
			throw new IllegalArgumentException(
				"canonicalJson is not strict JSON",
				error
			);
		}
		if (containsForbiddenDynamicComponent(parsed)) {
			throw new IllegalArgumentException(
				"resolved components cannot contain score, selector, or NBT sources"
			);
		}
		return canonical(parsed);
	}

	private static JsonElement readStrictValue(
		final JsonReader reader,
		final int depth
	) throws IOException {
		if (depth > MAX_JSON_DEPTH) {
			throw new JsonParseException(
				"component JSON nesting exceeds " + MAX_JSON_DEPTH
			);
		}
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				JsonObject object = new JsonObject();
				Set<String> names = new HashSet<>();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (!names.add(name)) {
						throw new JsonParseException("duplicate object key " + name);
					}
					object.add(name, readStrictValue(reader, depth + 1));
				}
				reader.endObject();
				yield object;
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				JsonArray array = new JsonArray();
				while (reader.hasNext()) {
					array.add(readStrictValue(reader, depth + 1));
				}
				reader.endArray();
				yield array;
			}
			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}
			default -> throw new JsonParseException(
				"unexpected component JSON token " + reader.peek()
			);
		};
	}

	private static boolean containsForbiddenDynamicComponent(final JsonElement value) {
		if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
			return false;
		}
		if (value.isJsonArray()) {
			for (JsonElement child : value.getAsJsonArray()) {
				if (containsForbiddenDynamicComponent(child)) {
					return true;
				}
			}
			return false;
		}
		JsonObject object = value.getAsJsonObject();
		for (String key : FORBIDDEN_DYNAMIC_COMPONENT_KEYS) {
			if (object.has(key)) {
				return true;
			}
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (containsForbiddenDynamicComponent(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private static String canonical(final JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return "null";
		}
		if (value.isJsonPrimitive()) {
			return value.toString();
		}
		if (value.isJsonArray()) {
			StringBuilder result = new StringBuilder("[");
			JsonArray array = value.getAsJsonArray();
			for (int index = 0; index < array.size(); index++) {
				if (index > 0) {
					result.append(',');
				}
				result.append(canonical(array.get(index)));
			}
			return result.append(']').toString();
		}
		StringBuilder result = new StringBuilder("{");
		List<Map.Entry<String, JsonElement>> entries = value.getAsJsonObject()
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.toList();
		for (int index = 0; index < entries.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			Map.Entry<String, JsonElement> entry = entries.get(index);
			result.append(new JsonPrimitive(entry.getKey()))
				.append(':')
				.append(canonical(entry.getValue()));
		}
		return result.append('}').toString();
	}

	private static int estimatedEncodedBytes(
		final Identifier cueId,
		final List<ResolvedNode> nodes
	) {
		long total = 128L + utf8Bytes(cueId.toString());
		for (ResolvedNode node : nodes) {
			total += 64L;
			if (node instanceof ResolvedSoundNode sound) {
				total += utf8Bytes(sound.sound().toString()) + 32L;
				continue;
			}
			ResolvedTextNode text = (ResolvedTextNode)node;
			total += 512L;
			for (ResolvedSegment segment : text.segments()) {
				total += 48L;
				if (segment instanceof StaticSegment value) {
					total += value.component().utf8Bytes();
				} else {
					FieldSlotSegment field = (FieldSlotSegment)segment;
					total += field.fallback().map(ResolvedComponent::utf8Bytes).orElse(0);
				}
			}
			if (text.effect().characterSound().isPresent()) {
				total += utf8Bytes(
					text.effect().characterSound().orElseThrow().sound().toString()
				);
			}
			total += text.speaker().displayName().map(MessagePlaybackPlan::utf8Bytes).orElse(0);
			if (total > MAX_PLAN_BYTES) {
				return MAX_PLAN_BYTES + 1;
			}
		}
		return (int)total;
	}

	private static int utf8Bytes(final String value) {
		return value.getBytes(StandardCharsets.UTF_8).length;
	}

	private static List<ResolvedNode> validatedNodeFragment(
		final List<ResolvedNode> nodes
	) {
		List<ResolvedNode> copied = List.copyOf(
			Objects.requireNonNull(nodes, "nodes")
		);
		if (copied.isEmpty() || copied.size() > MAX_NODES) {
			throw new IllegalArgumentException(
				"node fragment size must be between 1 and " + MAX_NODES
			);
		}
		long previousStart = -1L;
		Set<Integer> ordinals = new HashSet<>();
		Set<Integer> slots = new HashSet<>();
		int segmentCount = 0;
		for (ResolvedNode node : copied) {
			Objects.requireNonNull(node, "node");
			if (!ordinals.add(node.ordinal())) {
				throw new IllegalArgumentException(
					"node fragment ordinals must be unique"
				);
			}
			if (node.startOffsetNanos() < previousStart) {
				throw new IllegalArgumentException(
					"node fragment must be sorted by non-decreasing start offset"
				);
			}
			previousStart = node.startOffsetNanos();
			if (node instanceof ResolvedTextNode text) {
				segmentCount = Math.addExact(segmentCount, text.segments().size());
				for (ResolvedSegment segment : text.segments()) {
					if (segment instanceof FieldSlotSegment field) {
						slots.add(field.slot());
					}
				}
			}
		}
		if (segmentCount > MAX_TOTAL_SEGMENTS) {
			throw new IllegalArgumentException(
				"node fragment segment count exceeds " + MAX_TOTAL_SEGMENTS
			);
		}
		if (slots.size() > MAX_FIELD_SLOTS) {
			throw new IllegalArgumentException(
				"node fragment field-slot count exceeds " + MAX_FIELD_SLOTS
			);
		}
		return copied;
	}

	private static void validateCommonNode(
		final int ordinal,
		final long startOffsetNanos,
		final int priority,
		final Conflict conflict
	) {
		if (ordinal < 0) {
			throw new IllegalArgumentException("node ordinal must be non-negative");
		}
		requireTimelineDuration(startOffsetNanos, "startOffsetNanos");
		if (priority < -1_024 || priority > 1_024) {
			throw new IllegalArgumentException("priority must remain within -1024..1024");
		}
		Objects.requireNonNull(conflict, "conflict");
	}

	private static Identifier requireIdentifier(
		final Identifier identifier,
		final String field
	) {
		Objects.requireNonNull(identifier, field);
		if (identifier.toString().length() > MAX_IDENTIFIER_CHARACTERS) {
			throw new IllegalArgumentException(
				field + " length exceeds " + MAX_IDENTIFIER_CHARACTERS
			);
		}
		return identifier;
	}

	private static Identifier readIdentifier(
		final FriendlyByteBuf buffer,
		final String field
	) {
		String raw = buffer.readUtf(MAX_IDENTIFIER_CHARACTERS);
		Identifier value = Identifier.tryParse(raw);
		if (value == null) {
			throw new DecoderException(field + " is not a valid identifier");
		}
		return value;
	}

	private static void writeIdentifier(
		final FriendlyByteBuf buffer,
		final Identifier value
	) {
		buffer.writeUtf(value.toString(), MAX_IDENTIFIER_CHARACTERS);
	}

	private static String requireText(
		final String value,
		final int maximumCharacters,
		final String field,
		final boolean allowBlank
	) {
		Objects.requireNonNull(value, field);
		if (
			value.length() > maximumCharacters
				|| (!allowBlank && value.isBlank())
		) {
			throw new IllegalArgumentException(
				field + " must be non-blank and at most " + maximumCharacters + " characters"
			);
		}
		return value;
	}

	private static void requireNonNegative(final long value, final String field) {
		if (value < 0L) {
			throw new IllegalArgumentException(field + " must be non-negative");
		}
	}

	private static void requireTimelineDuration(
		final long value,
		final String field
	) {
		if (value < 0L || value > MAX_TIMELINE_NANOS) {
			throw new IllegalArgumentException(
				field + " must remain within 0.." + MAX_TIMELINE_NANOS
			);
		}
	}

	private static void requirePositiveTimelineDuration(
		final long value,
		final String field
	) {
		requireTimelineDuration(value, field);
		if (value == 0L) {
			throw new IllegalArgumentException(field + " must be positive");
		}
	}

	private static OptionalInt requireOptionalRange(
		final OptionalInt value,
		final int minimum,
		final int maximum,
		final String field
	) {
		Objects.requireNonNull(value, field);
		if (
			value.isPresent()
				&& (value.getAsInt() < minimum || value.getAsInt() > maximum)
		) {
			throw new IllegalArgumentException(
				field + " must remain within " + minimum + ".." + maximum
			);
		}
		return value;
	}

	private static OptionalLong requireOptionalTimelineDuration(
		final OptionalLong value,
		final String field
	) {
		Objects.requireNonNull(value, field);
		if (value.isPresent()) {
			requireTimelineDuration(value.getAsLong(), field);
		}
		return value;
	}

	private static <T> Map<Channel, T> completeChannelMap(
		final Map<Channel, T> value,
		final String field
	) {
		Objects.requireNonNull(value, field);
		EnumMap<Channel, T> result = new EnumMap<>(Channel.class);
		for (Channel channel : Channel.values()) {
			T item = Objects.requireNonNull(
				value.get(channel),
				field + "." + channel.name().toLowerCase()
			);
			result.put(channel, item);
		}
		if (value.size() != Channel.values().length) {
			throw new IllegalArgumentException(
				field + " must contain exactly one value for every channel"
			);
		}
		return Map.copyOf(result);
	}

	private static float boundedFloat(
		final float value,
		final float minimum,
		final float maximum,
		final String field
	) {
		if (!Float.isFinite(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(
				field + " must be finite and within " + minimum + ".." + maximum
			);
		}
		return value;
	}

	private static int readBoundedSize(
		final FriendlyByteBuf buffer,
		final int maximum,
		final String field,
		final boolean allowEmpty
	) {
		int size = buffer.readVarInt();
		if (size < (allowEmpty ? 0 : 1) || size > maximum) {
			throw new DecoderException(
				field + " size " + size + " exceeds its allowed range"
			);
		}
		return size;
	}

	private static <E extends Enum<E>> E readEnum(
		final FriendlyByteBuf buffer,
		final Class<E> type,
		final String field
	) {
		int ordinal = buffer.readVarInt();
		E[] values = type.getEnumConstants();
		if (ordinal < 0 || ordinal >= values.length) {
			throw new DecoderException(field + " has unknown ordinal " + ordinal);
		}
		return values[ordinal];
	}

	private static void writeEnum(
		final FriendlyByteBuf buffer,
		final Enum<?> value
	) {
		buffer.writeVarInt(value.ordinal());
	}

	private static OptionalInt readOptionalInt(final FriendlyByteBuf buffer) {
		return buffer.readBoolean()
			? OptionalInt.of(buffer.readVarInt())
			: OptionalInt.empty();
	}

	private static void writeOptionalInt(
		final FriendlyByteBuf buffer,
		final OptionalInt value
	) {
		buffer.writeBoolean(value.isPresent());
		if (value.isPresent()) {
			buffer.writeVarInt(value.getAsInt());
		}
	}

	private static OptionalLong readOptionalLong(final FriendlyByteBuf buffer) {
		return buffer.readBoolean()
			? OptionalLong.of(buffer.readVarLong())
			: OptionalLong.empty();
	}

	private static void writeOptionalLong(
		final FriendlyByteBuf buffer,
		final OptionalLong value
	) {
		buffer.writeBoolean(value.isPresent());
		if (value.isPresent()) {
			buffer.writeVarLong(value.getAsLong());
		}
	}

	private static <T> Optional<T> readOptional(
		final FriendlyByteBuf buffer,
		final java.util.function.Supplier<T> reader
	) {
		return buffer.readBoolean() ? Optional.of(reader.get()) : Optional.empty();
	}

	private static <T> void writeOptional(
		final FriendlyByteBuf buffer,
		final Optional<T> value,
		final java.util.function.Consumer<T> writer
	) {
		buffer.writeBoolean(value.isPresent());
		value.ifPresent(writer);
	}

	private static long absoluteDifference(final long left, final long right) {
		return left >= right ? left - right : right - left;
	}
}
