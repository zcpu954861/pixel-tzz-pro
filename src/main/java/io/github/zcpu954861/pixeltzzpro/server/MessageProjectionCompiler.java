package io.github.zcpu954861.pixeltzzpro.server;

import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AfterNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AtCueTime;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CallbackTrigger;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CaptureMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CharacterSoundRole;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ComponentTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionEvaluation;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConditionSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.CueNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DynamicFieldDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.DurationMode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldReference;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.FieldScope;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LocalizedTemplate;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.LanguageDurationBasis;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.MessageCueDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.OnCueEvent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RichComponent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SegmentTiming;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SoundNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.SpeakerSpec;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.StaticComponentPart;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextContent;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextEffectDefinition;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextNode;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextVariant;
import io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TimeSpan;
import io.github.zcpu954861.pixeltzzpro.message.MessageEffectResolver;
import io.github.zcpu954861.pixeltzzpro.message.MessageEffectResolver.Resolution;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockAnchor;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ClockKind;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldReservation;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.FieldSlotSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.PlanDisposition;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedComponent;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedDuration;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedEffect;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedLayout;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedOrigin;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSegment;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSoundNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedSpeaker;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.ResolvedTextNode;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.StaticSegment;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.ClockSample;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.EventKey;
import io.github.zcpu954861.pixeltzzpro.server.message.MessageInstanceAuthority.MessageInstance;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;

/**
 * Compiles a frozen, server-authorized message instance into one minimal per-recipient wire plan.
 *
 * <p>The compiler deliberately returns opaque field slots plus server-only capture bindings.
 * Predicates, parameter sources, callbacks, audience rules, hidden variants and field bindings
 * never cross the network boundary. Repeated references share one slot unless the node explicitly
 * requests {@code per_repeat} capture.
 */
public final class MessageProjectionCompiler {
	private static final long ONE_TICK_NANOS = TimeSpan.NANOS_PER_TICK;

	private MessageProjectionCompiler() {
	}

	/**
	 * Computes the bounded duration used by the authoritative dependency scheduler.
	 *
	 * <p>Content-driven nodes use the longest declared locale/variant and field reservation. This
	 * keeps an {@code after_node} dependency deterministic without resolving a private field early.
	 */
	public static Map<String, TimeSpan> authoritativeDurations(
		final MessageCueDefinition cue,
		final Map<Identifier, TextEffectDefinition> effects
	) {
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(effects, "effects");
		Map<String, TimeSpan> result = new LinkedHashMap<>();
		for (CueNode node : cue.nodes()) {
			if (!(node instanceof TextNode text)) {
				continue;
			}
			long duration = estimateContentDuration(cue, text.content(), effects);
			for (TextVariant variant : text.variants()) {
				duration = Math.max(
					duration,
					estimateContentDuration(cue, variant.content(), effects)
				);
			}
			result.put(node.id(), new TimeSpan(duration));
		}
		return Map.copyOf(result);
	}

	/**
	 * Returns the earliest safe relative instant for a {@code field_capture} condition.
	 *
	 * <p>The value is one game tick before the first possible dynamic-field boundary across the
	 * base content and registered variants. No predicate or field source is evaluated here.
	 */
	public static long fieldConditionDelay(
		final MessageCueDefinition cue,
		final TextNode node,
		final String locale,
		final Map<Identifier, TextEffectDefinition> effects
	) {
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(node, "node");
		Objects.requireNonNull(locale, "locale");
		Objects.requireNonNull(effects, "effects");
		long earliest = Long.MAX_VALUE;
		List<TextContent> contents = new ArrayList<>();
		contents.add(node.content());
		node.variants().forEach(variant -> contents.add(variant.content()));
		for (TextContent content : contents) {
			ComponentTemplate template = localized(content.text(), locale);
			ResolvedEffect effect = resolvedEffect(content, effects);
			ResolvedDuration duration = resolvedDuration(content);
			long interval = effectiveCharacterInterval(
				cue,
				content,
				template,
				effect,
				duration
			);
			long cursor = 0L;
			for (ComponentPart part : template.parts()) {
				long pauseBefore = part.timing()
					.pauseBefore()
					.map(TimeSpan::nanoseconds)
					.orElse(0L);
				cursor = safeAdd(cursor, pauseBefore);
				if (part instanceof FieldPart) {
					earliest = Math.min(
						earliest,
						Math.max(0L, cursor - ONE_TICK_NANOS)
					);
					break;
				}
				StaticComponentPart fixed = (StaticComponentPart)part;
				cursor = advanceReveal(
					cursor,
					graphemeCount(fixed.component().plainText()),
					interval
				);
				cursor = safeAdd(
					cursor,
					part.timing()
						.pauseAfter()
						.map(TimeSpan::nanoseconds)
						.orElse(0L)
				);
			}
		}
		return earliest == Long.MAX_VALUE ? 0L : earliest;
	}

	public static Projection compile(final CompileRequest request) {
		return compile(request, Optional.empty());
	}

	/**
	 * Compiles only the listed already-authorized occurrences for an append-only reveal.
	 */
	public static Projection compileOccurrences(
		final CompileRequest request,
		final Set<NodeOccurrence> occurrences
	) {
		Objects.requireNonNull(occurrences, "occurrences");
		if (occurrences.isEmpty()) {
			throw new IllegalArgumentException("occurrence projection cannot be empty");
		}
		return compile(request, Optional.of(Set.copyOf(occurrences)));
	}

	private static Projection compile(
		final CompileRequest request,
		final Optional<Set<NodeOccurrence>> occurrenceFilter
	) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(occurrenceFilter, "occurrenceFilter");
		MessageInstance instance = request.instance();
		MessageCueDefinition cue = instance.cue();
		Map<String, Long> cueStartOffsets = cueStartOffsets(
			cue,
			instance.authoritativeNodeDurations()
		);
		SlotAllocator slots = new SlotAllocator();
		List<NodeDraft> drafts = new ArrayList<>();

		for (CueNode node : cue.nodes()) {
			if (node instanceof CallbackNode) {
				continue;
			}
			if (!nodeVisible(request, node)) {
				continue;
			}
			List<Long> bases = eventBases(
				instance,
				node,
				request.recipient(),
				cueStartOffsets
			);
			for (long base : bases) {
				for (
					int occurrence = 0;
					occurrence < node.header().repeat().count();
					occurrence++
				) {
					if (
						occurrenceFilter.isPresent()
							&& !occurrenceFilter.orElseThrow().contains(
								new NodeOccurrence(node.id(), occurrence)
							)
					) {
						continue;
					}
					long start = safeAdd(
						base,
						safeMultiply(
							node.header().repeat().interval().nanoseconds(),
							occurrence
						)
					);
					if (start > MessagePlaybackPlan.MAX_TIMELINE_NANOS) {
						throw new IllegalArgumentException(
							"projected node start exceeds the network timeline horizon"
						);
					}
					if (node instanceof TextNode text) {
						TextContent content = selectContent(request, text);
						if (content == null) {
							continue;
						}
						drafts.add(
							compileTextDraft(
								request,
								text,
								content,
								start,
								occurrence,
								slots
							)
						);
					} else {
						drafts.add(
							compileSoundDraft(
								request,
								(SoundNode)node,
								start,
								occurrence
							)
						);
					}
				}
			}
		}

		drafts.sort(
			Comparator.comparingLong(NodeDraft::startOffsetNanos)
				.thenComparingInt(NodeDraft::sourceOrder)
				.thenComparingInt(NodeDraft::occurrence)
		);
		List<ResolvedNode> nodes = new ArrayList<>(drafts.size());
		Map<Integer, NodeOccurrence> projectedOccurrences = new LinkedHashMap<>();
		for (int ordinal = 0; ordinal < drafts.size(); ordinal++) {
			NodeDraft draft = drafts.get(ordinal);
			nodes.add(draft.resolve(ordinal));
			projectedOccurrences.put(
				ordinal,
				new NodeOccurrence(draft.nodeId(), draft.occurrence())
			);
		}
		if (nodes.isEmpty()) {
			return new Projection(Optional.empty(), Map.of(), Map.of());
		}

		ClockKind kind = instance.clockMode()
			== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ClockMode.GAME_TIME
			? ClockKind.GAME_TICK
			: ClockKind.PRESENTATION_NANOS;
		long serverNow = kind == ClockKind.GAME_TICK
			? request.clock().gameTick()
			: request.clock().presentationNanos();
		long visualAnchorNanos = request.visualAnchorNanos().isPresent()
			? request.visualAnchorNanos().getAsLong()
			: instance.anchorClockNanos();
		long startAt = kind == ClockKind.GAME_TICK
			? visualAnchorNanos / TimeSpan.NANOS_PER_TICK
			: visualAnchorNanos;
		MessagePlaybackPlan plan = new MessagePlaybackPlan(
			instance.instanceId(),
			cue.id(),
			instance.context().definitionGeneration(),
			request.sequence(),
			instance.cycle(),
			request.disposition(),
			request.replacedInstanceId(),
			new ClockAnchor(kind, serverNow, startAt),
			resolvedPolicies(cue),
			request.origin(),
			nodes
		);
		return new Projection(
			Optional.of(plan),
			slots.bindings(),
			projectedOccurrences
		);
	}

	private static boolean nodeVisible(
		final CompileRequest request,
		final CueNode node
	) {
		if (!request.instance().nodeTargets()
			.getOrDefault(node.id(), request.instance().cueTargets())
			.contains(request.recipient())) {
			return false;
		}
		Optional<ConditionSpec> condition = node.header().when();
		if (condition.isEmpty()) {
			return true;
		}
		ConditionSpec value = condition.orElseThrow();
		if (
			value.evaluateAt() != ConditionEvaluation.CUE_START
				&& !request.dueConditionalNodes().contains(node.id())
		) {
			return false;
		}
		return request.conditionMatcher().test(value);
	}

	private static TextContent selectContent(
		final CompileRequest request,
		final TextNode node
	) {
		for (TextVariant variant : node.variants()) {
			ConditionSpec condition = variant.when();
			if (
				condition.evaluateAt() != ConditionEvaluation.CUE_START
					&& !request.dueConditionalNodes().contains(node.id())
			) {
				continue;
			}
			if (request.conditionMatcher().test(condition)) {
				return variant.content();
			}
		}
		return node.content();
	}

	private static NodeDraft compileTextDraft(
		final CompileRequest request,
		final TextNode node,
		final TextContent content,
		final long start,
		final int occurrence,
		final SlotAllocator slots
	) {
		ComponentTemplate template = localized(content.text(), request.locale());
		ResolvedEffect effect = resolvedEffect(content, request.effects());
		ResolvedDuration duration = resolvedDuration(content);
		long characterInterval = effectiveCharacterInterval(
			request.instance().cue(),
			content,
			template,
			effect,
			duration
		);
		long cursor = 0L;
		Long firstFieldBoundary = null;
		List<SegmentDraft> segmentDrafts = new ArrayList<>();
		for (ComponentPart part : template.parts()) {
			io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan.SegmentTiming timing =
				segmentTiming(part.timing());
			long boundary = safeAdd(cursor, timing.pauseBeforeNanos());
			cursor = boundary;
			if (part instanceof StaticComponentPart staticPart) {
				int graphemes = graphemeCount(staticPart.component().plainText());
				segmentDrafts.add(
					new StaticSegmentDraft(
						component(staticPart.component()),
						timing,
						graphemes
					)
				);
				cursor = advanceReveal(cursor, graphemes, characterInterval);
			} else {
				FieldPart fieldPart = (FieldPart)part;
				DynamicFieldDefinition field = field(
					request.instance().cue(),
					content,
					fieldPart.field()
				);
				if (field == null) {
					throw new IllegalArgumentException(
						"projected text references an unavailable field "
							+ fieldPart.field().id()
					);
				}
				if (firstFieldBoundary == null) {
					firstFieldBoundary = boundary;
				}
				SlotKey key = slotKey(
					node,
					fieldPart.field(),
					occurrence
				);
				int slot = slots.slot(
					key,
					field,
					node.id(),
					occurrence,
					start,
					boundary,
					firstFieldBoundary
				);
				int graphemes = estimatedFieldGraphemes(field);
				segmentDrafts.add(
					new FieldSegmentDraft(
						slot,
						field.fallback().map(MessageProjectionCompiler::component),
						reservation(field),
						timing,
						graphemes
					)
				);
				cursor = advanceReveal(cursor, graphemes, characterInterval);
			}
			cursor = safeAdd(cursor, timing.pauseAfterNanos());
		}
		if (segmentDrafts.isEmpty()) {
			throw new IllegalArgumentException("projected text node has no segments");
		}
		if (firstFieldBoundary != null) {
			slots.finishFirstFieldGroup(node.id(), occurrence, start, firstFieldBoundary);
		}
		int sourceOrder = sourceOrder(request.instance().cue(), node.id());
		return NodeDraft.text(
			node.id(),
			start,
			sourceOrder,
			occurrence,
			node.policy().priority().orElse(request.instance().cue().policies().concurrency().priority()),
			conflict(
				node.policy().conflict()
					.or(() -> request.instance().cue().policies().defaultConflict())
					.orElse(io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConflictMode.PARALLEL)
			),
			channel(node.channel()),
			segmentDrafts,
			effect,
			layout(content),
			duration,
			request.speakerResolver().apply(content.speaker())
		);
	}

	private static NodeDraft compileSoundDraft(
		final CompileRequest request,
		final SoundNode node,
		final long start,
		final int occurrence
	) {
		return NodeDraft.sound(
			node.id(),
			start,
			sourceOrder(request.instance().cue(), node.id()),
			occurrence,
			node.policy().priority().orElse(
				request.instance().cue().policies().concurrency().priority()
			),
			conflict(
				node.policy().conflict().orElse(
					io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConflictMode.PARALLEL
				)
			),
			node.sound(),
			MessagePlaybackPlan.SoundCategory.valueOf(node.category().name()),
			node.volume(),
			node.pitch(),
			MessagePlaybackPlan.SoundAttachment.valueOf(node.attachment().name())
		);
	}

	private static int sourceOrder(
		final MessageCueDefinition cue,
		final String nodeId
	) {
		for (int index = 0; index < cue.nodes().size(); index++) {
			if (cue.nodes().get(index).id().equals(nodeId)) {
				return index;
			}
		}
		return Integer.MAX_VALUE;
	}

	private static SlotKey slotKey(
		final TextNode node,
		final FieldReference reference,
		final int occurrence
	) {
		String owner = reference.scope() == FieldScope.CUE ? "" : node.id();
		int repeat = node.header().repeat().capture()
			== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.RepeatCaptureMode.PER_REPEAT
			? occurrence
			: -1;
		return new SlotKey(reference.scope(), owner, reference.id(), repeat);
	}

	private static DynamicFieldDefinition field(
		final MessageCueDefinition cue,
		final TextContent content,
		final FieldReference reference
	) {
		return reference.scope() == FieldScope.CUE
			? cue.fields().get(reference.id())
			: content.fields().get(reference.id());
	}

	private static ComponentTemplate localized(
		final LocalizedTemplate template,
		final String locale
	) {
		String normalized = Objects.requireNonNull(locale, "locale")
			.toLowerCase(Locale.ROOT)
			.replace('-', '_');
		return template.locales().getOrDefault(normalized, template.fallback());
	}

	private static ResolvedEffect resolvedEffect(
		final TextContent content,
		final Map<Identifier, TextEffectDefinition> effects
	) {
		Resolution resolution = MessageEffectResolver.resolve(content.effect(), effects);
		if (!resolution.successful()) {
			throw new IllegalArgumentException(resolution.error().orElseThrow());
		}
		if (resolution.effect().isEmpty()) {
			return ResolvedEffect.none();
		}
		MessageEffectResolver.ResolvedEffect source = resolution.effect().orElseThrow();
		Optional<MessagePlaybackPlan.CharacterSound> characterSound =
			content.characterSoundRole() == CharacterSoundRole.MUTED
				? Optional.empty()
				: source.characterSound().map(sound ->
					new MessagePlaybackPlan.CharacterSound(
						sound.sound(),
						MessagePlaybackPlan.SoundCategory.valueOf(sound.category().name()),
						sound.everyCharacters(),
						sound.skipWhitespace(),
						sound.skipPunctuation(),
						sound.skipNewline(),
						sound.volume(),
						sound.pitch(),
						sound.pitchVariation(),
						MessagePlaybackPlan.SoundAttachment.valueOf(sound.attachment().name())
					)
				);
		return new ResolvedEffect(
			source.typewriter().map(typewriter ->
				new MessagePlaybackPlan.Typewriter(
					typewriter.characterInterval().nanoseconds(),
					new MessagePlaybackPlan.Cursor(
						typewriter.cursor().enabled(),
						typewriter.cursor().glyph(),
						typewriter.cursor().color(),
						typewriter.cursor().blink(),
						typewriter.cursor().blink()
							? typewriter.cursor().blinkPeriod()
								.orElseThrow()
								.nanoseconds()
							: 0L
					),
					typewriter.freshColor(),
					typewriter.restoreAfterCharacters(),
					MessagePlaybackPlan.RestoreMode.valueOf(typewriter.restoreMode().name())
				)
			),
			source.fade().map(value ->
				new MessagePlaybackPlan.Fade(
					value.enter().nanoseconds(),
					value.hold().nanoseconds(),
					value.exit().nanoseconds()
				)
			),
			source.scramble().map(value ->
				new MessagePlaybackPlan.Scramble(
					value.characterInterval().nanoseconds(),
					value.characters()
				)
			),
			source.gradient().map(value ->
				new MessagePlaybackPlan.Gradient(
					value.fromColor(),
					value.toColor(),
					value.duration().nanoseconds()
				)
			),
			source.motion().map(value ->
				new MessagePlaybackPlan.Motion(
					value.slideX(),
					value.slideY(),
					value.startScale(),
					value.duration().nanoseconds()
				)
			),
			source.pulse().map(value ->
				new MessagePlaybackPlan.Pulse(value.scale(), value.duration().nanoseconds())
			),
			characterSound,
			characterSound.isPresent() ? source.maxCharacterSoundEvents() : 0
		);
	}

	private static ResolvedDuration resolvedDuration(final TextContent content) {
		long hold = content.hold().map(TimeSpan::nanoseconds).orElse(0L);
		return new ResolvedDuration(
			MessagePlaybackPlan.DurationMode.valueOf(content.duration().mode().name()),
			content.duration().total().map(TimeSpan::nanoseconds),
			content.duration().minimumSpeed(),
			content.duration().maximumSpeed(),
			hold
		);
	}

	private static ResolvedLayout layout(final TextContent content) {
		var source = content.layout();
		return new ResolvedLayout(
			MessagePlaybackPlan.Alignment.valueOf(source.alignment().name()),
			source.offsetX(),
			source.offsetY(),
			optionalInt(source.maxWidth()),
			optionalInt(source.maxLines()),
			source.lineSpacing(),
			MessagePlaybackPlan.Overflow.valueOf(source.overflow().name())
		);
	}

	private static MessagePlaybackPlan.ResolvedPolicies resolvedPolicies(
		final MessageCueDefinition cue
	) {
		var lifecycle = cue.policies().lifecycle();
		EnumMap<MessagePlaybackPlan.Channel, MessagePlaybackPlan.ScreenStart> screen =
			new EnumMap<>(MessagePlaybackPlan.Channel.class);
		EnumMap<MessagePlaybackPlan.Channel, MessagePlaybackPlan.Obscured> obscured =
			new EnumMap<>(MessagePlaybackPlan.Channel.class);
		for (var channel : io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel.values()) {
			MessagePlaybackPlan.Channel wire = channel(channel);
			screen.put(
				wire,
				MessagePlaybackPlan.ScreenStart.valueOf(
					lifecycle.screenStart().get(channel).name()
				)
			);
			obscured.put(
				wire,
				MessagePlaybackPlan.Obscured.valueOf(
					lifecycle.obscured().get(channel).name()
				)
			);
		}
		return new MessagePlaybackPlan.ResolvedPolicies(
			MessagePlaybackPlan.ChatInterrupt.valueOf(
				cue.policies().concurrency().chatInterrupt().name()
			),
			screen,
			obscured,
			optionalLong(lifecycle.screenWaitTimeout()),
			MessagePlaybackPlan.ScreenTimeoutAction.valueOf(
				lifecycle.screenTimeoutAction().name()
			),
			optionalLong(lifecycle.maxPause()),
			MessagePlaybackPlan.ExternalConflict.valueOf(
				lifecycle.externalConflict().name()
			),
			MessagePlaybackPlan.ResourceReload.valueOf(
				lifecycle.resourceReload().name()
			),
			MessagePlaybackPlan.ReducedMotion.valueOf(
				cue.policies().accessibility().reducedMotion().name()
			),
			cue.policies().accessibility().allowManualComplete(),
			MessagePlaybackPlan.Respawn.valueOf(lifecycle.respawn().name()),
			lifecycle.attachment()
				== io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.AttachmentMode.PLAYER
					? MessagePlaybackPlan.DimensionExit.CONTINUE
					: MessagePlaybackPlan.DimensionExit.valueOf(
						lifecycle.dimensionExit().name()
					)
		);
	}

	private static MessagePlaybackPlan.Channel channel(
		final io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.TextChannel channel
	) {
		return MessagePlaybackPlan.Channel.valueOf(channel.name());
	}

	private static MessagePlaybackPlan.Conflict conflict(
		final io.github.zcpu954861.pixeltzzpro.content.MessageDefinitions.ConflictMode conflict
	) {
		return MessagePlaybackPlan.Conflict.valueOf(conflict.name());
	}

	private static ResolvedComponent component(final RichComponent value) {
		return ResolvedComponent.parseCanonical(value.canonicalJson());
	}

	private static MessagePlaybackPlan.SegmentTiming segmentTiming(
		final SegmentTiming source
	) {
		return new MessagePlaybackPlan.SegmentTiming(
			source.pauseBefore().map(TimeSpan::nanoseconds).orElse(0L),
			source.pauseAfter().map(TimeSpan::nanoseconds).orElse(0L),
			source.emphasis()
		);
	}

	private static FieldReservation reservation(
		final DynamicFieldDefinition field
	) {
		if (field.reservation().isEmpty()) {
			return FieldReservation.none();
		}
		var source = field.reservation().orElseThrow();
		return new FieldReservation(
			optionalInt(source.maxGraphemes()),
			optionalInt(source.estimatedWidth()),
			optionalInt(source.maxLines())
		);
	}

	private static OptionalInt optionalInt(final Optional<Integer> value) {
		return value.isPresent() ? OptionalInt.of(value.orElseThrow()) : OptionalInt.empty();
	}

	private static OptionalLong optionalLong(final Optional<TimeSpan> value) {
		return value.isPresent()
			? OptionalLong.of(value.orElseThrow().nanoseconds())
			: OptionalLong.empty();
	}

	private static List<Long> eventBases(
		final MessageInstance instance,
		final CueNode node,
		final UUID recipient,
		final Map<String, Long> cueStartOffsets
	) {
		if (node.header().schedule() instanceof AtCueTime || node.header().schedule() instanceof AfterNode) {
			Long value = cueStartOffsets.get(node.id());
			return value == null ? List.of() : List.of(value);
		}
		OnCueEvent event = (OnCueEvent)node.header().schedule();
		long offset = event.offset().nanoseconds();
		return switch (event.trigger()) {
			case START -> List.of(offset);
			case TARGET_COMPLETE -> eventBase(
				instance,
				new EventKey(CallbackTrigger.TARGET_COMPLETE, Optional.of(recipient)),
				offset
			);
			case ALL_COMPLETE -> eventBase(
				instance,
				new EventKey(CallbackTrigger.ALL_COMPLETE, Optional.empty()),
				offset
			);
			case INTERRUPT -> eventBase(
				instance,
				new EventKey(CallbackTrigger.INTERRUPT, Optional.empty()),
				offset
			);
		};
	}

	private static List<Long> eventBase(
		final MessageInstance instance,
		final EventKey event,
		final long offset
	) {
		Long base = instance.eventOffsets().get(event);
		return base == null ? List.of() : List.of(safeAdd(base, offset));
	}

	private static Map<String, Long> cueStartOffsets(
		final MessageCueDefinition cue,
		final Map<String, TimeSpan> durations
	) {
		Map<String, CueNode> nodes = new LinkedHashMap<>();
		cue.nodes().forEach(node -> nodes.put(node.id(), node));
		Map<String, Long> result = new LinkedHashMap<>();
		Set<String> visiting = new LinkedHashSet<>();
		for (CueNode node : cue.nodes()) {
			if (!(node.header().schedule() instanceof OnCueEvent)) {
				resolveOffset(node, nodes, durations, result, visiting);
			}
		}
		return Map.copyOf(result);
	}

	private static long resolveOffset(
		final CueNode node,
		final Map<String, CueNode> nodes,
		final Map<String, TimeSpan> durations,
		final Map<String, Long> result,
		final Set<String> visiting
	) {
		Long existing = result.get(node.id());
		if (existing != null) {
			return existing;
		}
		if (!visiting.add(node.id())) {
			throw new IllegalArgumentException("message schedule contains a cycle");
		}
		long value;
		if (node.header().schedule() instanceof AtCueTime at) {
			value = at.offset().nanoseconds();
		} else if (node.header().schedule() instanceof AfterNode after) {
			CueNode dependency = nodes.get(after.nodeId());
			if (dependency == null || dependency.header().schedule() instanceof OnCueEvent) {
				throw new IllegalArgumentException(
					"after-node schedule references an unavailable start node"
				);
			}
			long dependencyStart = resolveOffset(
				dependency,
				nodes,
				durations,
				result,
				visiting
			);
			long lastStart = safeAdd(
				dependencyStart,
				safeMultiply(
					dependency.header().repeat().interval().nanoseconds(),
					dependency.header().repeat().count() - 1L
				)
			);
			value = safeAdd(
				safeAdd(
					lastStart,
					durations.getOrDefault(dependency.id(), new TimeSpan(0L)).nanoseconds()
				),
				after.offset().nanoseconds()
			);
		} else {
			throw new IllegalArgumentException("event node has no fixed cue-start offset");
		}
		visiting.remove(node.id());
		result.put(node.id(), value);
		return value;
	}

	private static long estimateContentDuration(
		final MessageCueDefinition cue,
		final TextContent content,
		final Map<Identifier, TextEffectDefinition> effects
	) {
		ResolvedEffect effect = resolvedEffect(content, effects);
		ResolvedDuration duration = resolvedDuration(content);
		long longest = 0L;
		List<ComponentTemplate> templates = new ArrayList<>();
		templates.add(content.text().fallback());
		if (
			content.duration().mode() != DurationMode.FIXED_TOTAL
				|| content.duration().languageBasis()
					== LanguageDurationBasis.LONGEST_DECLARED
		) {
			templates.addAll(content.text().locales().values());
		}
		for (ComponentTemplate template : templates) {
			int graphemes = 0;
			long pauses = 0L;
			for (ComponentPart part : template.parts()) {
				pauses = safeAdd(
					pauses,
					part.timing().pauseBefore().map(TimeSpan::nanoseconds).orElse(0L)
				);
				pauses = safeAdd(
					pauses,
					part.timing().pauseAfter().map(TimeSpan::nanoseconds).orElse(0L)
				);
				if (part instanceof StaticComponentPart staticPart) {
					graphemes = Math.addExact(
						graphemes,
						graphemeCount(staticPart.component().plainText())
					);
				} else {
					FieldReference reference = ((FieldPart)part).field();
					DynamicFieldDefinition field = reference.scope() == FieldScope.CUE
						? cue.fields().get(reference.id())
						: content.fields().get(reference.id());
					if (field != null) {
						graphemes = Math.addExact(graphemes, estimatedFieldGraphemes(field));
					}
				}
			}
			long interval = effectiveCharacterInterval(
				graphemes,
				pauses,
				effect,
				duration
			);
			long reveal = safeAdd(pauses, safeMultiply(interval, graphemes));
			long complete = safeAdd(reveal, duration.holdNanos());
			if (duration.mode() == MessagePlaybackPlan.DurationMode.FIXED_TOTAL) {
				complete = Math.max(complete, duration.totalNanos().orElseThrow());
			}
			if (effect.fade().isPresent()) {
				var fade = effect.fade().orElseThrow();
				complete = Math.max(
					complete,
					safeAdd(safeAdd(fade.enterNanos(), fade.holdNanos()), fade.exitNanos())
				);
				complete = Math.max(complete, safeAdd(reveal, fade.exitNanos()));
			}
			longest = Math.max(longest, complete);
		}
		return longest;
	}

	private static long effectiveCharacterInterval(
		final MessageCueDefinition cue,
		final TextContent content,
		final ComponentTemplate template,
		final ResolvedEffect effect,
		final ResolvedDuration duration
	) {
		int graphemes = 0;
		long pauses = 0L;
		for (ComponentPart part : template.parts()) {
			pauses = safeAdd(
				pauses,
				part.timing().pauseBefore().map(TimeSpan::nanoseconds).orElse(0L)
			);
			pauses = safeAdd(
				pauses,
				part.timing().pauseAfter().map(TimeSpan::nanoseconds).orElse(0L)
			);
			if (part instanceof StaticComponentPart staticPart) {
				graphemes = Math.addExact(
					graphemes,
					graphemeCount(staticPart.component().plainText())
				);
			} else {
				FieldReference reference = ((FieldPart)part).field();
				DynamicFieldDefinition field = reference.scope() == FieldScope.CUE
					? cue.fields().get(reference.id())
					: content.fields().get(reference.id());
				if (field != null) {
					graphemes = Math.addExact(graphemes, estimatedFieldGraphemes(field));
				}
			}
		}
		return effectiveCharacterInterval(graphemes, pauses, effect, duration);
	}

	private static long effectiveCharacterInterval(
		final int graphemes,
		final long pauses,
		final ResolvedEffect effect,
		final ResolvedDuration duration
	) {
		if (graphemes == 0 || (effect.typewriter().isEmpty() && effect.scramble().isEmpty())) {
			return 0L;
		}
		long registered = effect.typewriter()
			.map(MessagePlaybackPlan.Typewriter::characterIntervalNanos)
			.orElseGet(() -> effect.scramble().orElseThrow().characterIntervalNanos());
		if (duration.mode() == MessagePlaybackPlan.DurationMode.CONTENT_DRIVEN) {
			return registered;
		}
		long unavailable = safeAdd(pauses, duration.holdNanos());
		long total = duration.totalNanos().orElseThrow();
		long typing = total > unavailable ? total - unavailable : 0L;
		long desired = typing == 0L
			? 1L
			: Math.max(1L, Math.round(typing / (double)graphemes));
		long fastest = duration.maximumSpeed()
			.map(value -> Math.max(1L, (long)Math.ceil(1_000_000_000.0D / value)))
			.orElse(1L);
		long slowest = duration.minimumSpeed()
			.map(value -> Math.max(1L, (long)Math.floor(1_000_000_000.0D / value)))
			.orElse(MessagePlaybackPlan.MAX_TIMELINE_NANOS);
		if (fastest > slowest) {
			throw new IllegalArgumentException("fixed duration speed bounds have no overlap");
		}
		return Math.clamp(desired, fastest, slowest);
	}

	private static int estimatedFieldGraphemes(final DynamicFieldDefinition field) {
		return field.reservation()
			.flatMap(value -> value.maxGraphemes())
			.orElseGet(() ->
				field.fallback().map(value -> graphemeCount(value.plainText())).orElse(0)
			);
	}

	private static int graphemeCount(final String value) {
		BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
		iterator.setText(Objects.requireNonNull(value, "value"));
		int count = 0;
		for (
			int end = iterator.first(), next = iterator.next();
			next != BreakIterator.DONE;
			end = next, next = iterator.next()
		) {
			if (next > end) {
				count++;
			}
		}
		return count;
	}

	private static long advanceReveal(
		final long cursor,
		final int graphemes,
		final long interval
	) {
		return interval == 0L
			? cursor
			: safeAdd(cursor, safeMultiply(interval, graphemes));
	}

	private static long safeAdd(final long left, final long right) {
		return Math.addExact(left, right);
	}

	private static long safeMultiply(final long left, final long right) {
		return Math.multiplyExact(left, right);
	}

	public record CompileRequest(
		MessageInstance instance,
		long sequence,
		PlanDisposition disposition,
		Optional<UUID> replacedInstanceId,
		ClockSample clock,
		ResolvedOrigin origin,
		String locale,
		UUID recipient,
		Map<Identifier, TextEffectDefinition> effects,
		Predicate<ConditionSpec> conditionMatcher,
		Function<SpeakerSpec, ResolvedSpeaker> speakerResolver,
		Set<String> dueConditionalNodes,
		OptionalLong visualAnchorNanos
	) {
		public CompileRequest {
			instance = Objects.requireNonNull(instance, "instance");
			if (sequence < 0L) {
				throw new IllegalArgumentException("plan sequence cannot be negative");
			}
			disposition = Objects.requireNonNull(disposition, "disposition");
			replacedInstanceId = Objects.requireNonNull(
				replacedInstanceId,
				"replacedInstanceId"
			);
			clock = Objects.requireNonNull(clock, "clock");
			origin = Objects.requireNonNull(origin, "origin");
			locale = Objects.requireNonNull(locale, "locale");
			recipient = Objects.requireNonNull(recipient, "recipient");
			effects = Map.copyOf(effects);
			conditionMatcher = Objects.requireNonNull(conditionMatcher, "conditionMatcher");
			speakerResolver = Objects.requireNonNull(speakerResolver, "speakerResolver");
			dueConditionalNodes = Set.copyOf(dueConditionalNodes);
			visualAnchorNanos = Objects.requireNonNull(
				visualAnchorNanos,
				"visualAnchorNanos"
			);
			if (visualAnchorNanos.isPresent() && visualAnchorNanos.getAsLong() < 0L) {
				throw new IllegalArgumentException(
					"visual anchor cannot be negative"
				);
			}
		}
	}

	public record Projection(
		Optional<MessagePlaybackPlan> plan,
		Map<Integer, CaptureBinding> captures,
		Map<Integer, NodeOccurrence> occurrences
	) {
		public Projection {
			plan = Objects.requireNonNull(plan, "plan");
			captures = Map.copyOf(captures);
			occurrences = Map.copyOf(occurrences);
			if (
				plan.isEmpty()
					&& (!captures.isEmpty() || !occurrences.isEmpty())
			) {
				throw new IllegalArgumentException(
					"field captures and occurrences require a projected plan"
				);
			}
			if (
				plan.isPresent()
					&& !plan.orElseThrow().nodes().stream()
						.map(ResolvedNode::ordinal)
						.collect(java.util.stream.Collectors.toSet())
						.equals(occurrences.keySet())
			) {
				throw new IllegalArgumentException(
					"projected occurrence keys must match plan ordinals"
				);
			}
		}
	}

	public record NodeOccurrence(String nodeId, int occurrence) {
		public NodeOccurrence {
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			if (occurrence < 0) {
				throw new IllegalArgumentException("node occurrence cannot be negative");
			}
		}
	}

	public record CaptureBinding(
		int slot,
		CaptureIdentity identity,
		DynamicFieldDefinition field,
		String nodeId,
		int occurrence,
		long captureOffsetNanos
	) {
		public CaptureBinding {
			if (
				slot < 0
					|| slot >= MessagePlaybackPlan.MAX_FIELD_SLOTS
					|| occurrence < 0
					|| captureOffsetNanos < 0L
			) {
				throw new IllegalArgumentException("capture binding is outside its bounds");
			}
			identity = Objects.requireNonNull(identity, "identity");
			field = Objects.requireNonNull(field, "field");
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
		}
	}

	public record CaptureIdentity(
		FieldScope scope,
		String owner,
		String fieldId,
		int occurrence
	) {
		public CaptureIdentity {
			scope = Objects.requireNonNull(scope, "scope");
			owner = Objects.requireNonNull(owner, "owner");
			fieldId = Objects.requireNonNull(fieldId, "fieldId");
			if (occurrence < -1) {
				throw new IllegalArgumentException(
					"capture identity occurrence cannot be below -1"
				);
			}
		}
	}

	private record SlotKey(
		FieldScope scope,
		String owner,
		String fieldId,
		int occurrence
	) {
		private SlotKey {
			scope = Objects.requireNonNull(scope, "scope");
			owner = Objects.requireNonNull(owner, "owner");
			fieldId = Objects.requireNonNull(fieldId, "fieldId");
		}
	}

	private static final class SlotAllocator {
		private final Map<SlotKey, Integer> slots = new LinkedHashMap<>();
		private final Map<Integer, CaptureBinding> bindings = new LinkedHashMap<>();
		private final Map<String, List<Integer>> firstFieldGroups = new LinkedHashMap<>();

		private int slot(
			final SlotKey key,
			final DynamicFieldDefinition field,
			final String nodeId,
			final int occurrence,
			final long nodeStart,
			final long fieldBoundary,
			final long firstFieldBoundary
		) {
			Integer existing = this.slots.get(key);
			long capture = captureOffset(
				field.capture(),
				nodeStart,
				fieldBoundary,
				firstFieldBoundary
			);
			if (existing != null) {
				CaptureBinding current = this.bindings.get(existing);
				if (capture < current.captureOffsetNanos()) {
					this.bindings.put(
						existing,
						new CaptureBinding(
							existing,
							current.identity(),
							current.field(),
							nodeId,
							occurrence,
							capture
						)
					);
				}
				return existing;
			}
			int slot = this.slots.size();
			if (slot >= MessagePlaybackPlan.MAX_FIELD_SLOTS) {
				throw new IllegalArgumentException(
					"projected message exceeds its distinct field-slot limit"
				);
			}
			this.slots.put(key, slot);
			this.bindings.put(
				slot,
				new CaptureBinding(
					slot,
					new CaptureIdentity(
						key.scope(),
						key.owner(),
						key.fieldId(),
						key.occurrence()
					),
					field,
					nodeId,
					occurrence,
					capture
				)
			);
			if (field.capture() == CaptureMode.ON_FIRST_FIELD) {
				this.firstFieldGroups
					.computeIfAbsent(group(nodeId, occurrence), ignored -> new ArrayList<>())
					.add(slot);
			}
			return slot;
		}

		private void finishFirstFieldGroup(
			final String nodeId,
			final int occurrence,
			final long nodeStart,
			final long firstFieldBoundary
		) {
			long capture = captureOffset(
				CaptureMode.ON_FIRST_FIELD,
				nodeStart,
				firstFieldBoundary,
				firstFieldBoundary
			);
			for (int slot : this.firstFieldGroups.getOrDefault(group(nodeId, occurrence), List.of())) {
				CaptureBinding current = this.bindings.get(slot);
				this.bindings.put(
					slot,
					new CaptureBinding(
						slot,
						current.identity(),
						current.field(),
						current.nodeId(),
						current.occurrence(),
						capture
					)
				);
			}
		}

		private Map<Integer, CaptureBinding> bindings() {
			return Map.copyOf(this.bindings);
		}

		private static String group(final String nodeId, final int occurrence) {
			return nodeId + "\u0000" + occurrence;
		}

		private static long captureOffset(
			final CaptureMode mode,
			final long nodeStart,
			final long fieldBoundary,
			final long firstFieldBoundary
		) {
			long relative = switch (mode) {
				case ON_DISPLAY -> 0L;
				case ON_FIRST_FIELD -> firstFieldBoundary;
				case PER_FIELD -> fieldBoundary;
			};
			long absolute = safeAdd(nodeStart, relative);
			/*
			 * The value packet must arrive before the cursor reaches its gate.  Clamping the
			 * request to nodeStart made a field at the beginning of a Title/Subtitle compete
			 * with the first rendered client tick; remote clients visibly paused and then
			 * caught up.  The cue clock already begins at zero, so one tick of look-ahead is
			 * safe without introducing negative presentation time.
			 */
			return Math.max(0L, absolute - ONE_TICK_NANOS);
		}
	}

	private sealed interface SegmentDraft permits StaticSegmentDraft, FieldSegmentDraft {
		ResolvedSegment resolve();
	}

	private record StaticSegmentDraft(
		ResolvedComponent component,
		MessagePlaybackPlan.SegmentTiming timing,
		int graphemes
	) implements SegmentDraft {
		@Override
		public ResolvedSegment resolve() {
			return new StaticSegment(this.component, this.timing);
		}
	}

	private record FieldSegmentDraft(
		int slot,
		Optional<ResolvedComponent> fallback,
		FieldReservation reservation,
		MessagePlaybackPlan.SegmentTiming timing,
		int graphemes
	) implements SegmentDraft {
		@Override
		public ResolvedSegment resolve() {
			return new FieldSlotSegment(
				this.slot,
				this.fallback,
				this.reservation,
				this.timing
			);
		}
	}

	private record NodeDraft(
		String nodeId,
		long startOffsetNanos,
		int sourceOrder,
		int occurrence,
		int priority,
		MessagePlaybackPlan.Conflict conflict,
		Optional<MessagePlaybackPlan.Channel> channel,
		List<SegmentDraft> segments,
		Optional<ResolvedEffect> effect,
		Optional<ResolvedLayout> layout,
		Optional<ResolvedDuration> duration,
		Optional<ResolvedSpeaker> speaker,
		Optional<Identifier> sound,
		Optional<MessagePlaybackPlan.SoundCategory> soundCategory,
		float volume,
		float pitch,
		Optional<MessagePlaybackPlan.SoundAttachment> attachment
	) {
		private NodeDraft {
			nodeId = Objects.requireNonNull(nodeId, "nodeId");
			channel = Objects.requireNonNull(channel, "channel");
			segments = List.copyOf(segments);
			effect = Objects.requireNonNull(effect, "effect");
			layout = Objects.requireNonNull(layout, "layout");
			duration = Objects.requireNonNull(duration, "duration");
			speaker = Objects.requireNonNull(speaker, "speaker");
			sound = Objects.requireNonNull(sound, "sound");
			soundCategory = Objects.requireNonNull(soundCategory, "soundCategory");
			attachment = Objects.requireNonNull(attachment, "attachment");
		}

		private static NodeDraft text(
			final String nodeId,
			final long start,
			final int sourceOrder,
			final int occurrence,
			final int priority,
			final MessagePlaybackPlan.Conflict conflict,
			final MessagePlaybackPlan.Channel channel,
			final List<SegmentDraft> segments,
			final ResolvedEffect effect,
			final ResolvedLayout layout,
			final ResolvedDuration duration,
			final ResolvedSpeaker speaker
		) {
			return new NodeDraft(
				nodeId,
				start,
				sourceOrder,
				occurrence,
				priority,
				conflict,
				Optional.of(channel),
				segments,
				Optional.of(effect),
				Optional.of(layout),
				Optional.of(duration),
				Optional.of(speaker),
				Optional.empty(),
				Optional.empty(),
				0.0F,
				0.0F,
				Optional.empty()
			);
		}

		private static NodeDraft sound(
			final String nodeId,
			final long start,
			final int sourceOrder,
			final int occurrence,
			final int priority,
			final MessagePlaybackPlan.Conflict conflict,
			final Identifier sound,
			final MessagePlaybackPlan.SoundCategory category,
			final float volume,
			final float pitch,
			final MessagePlaybackPlan.SoundAttachment attachment
		) {
			return new NodeDraft(
				nodeId,
				start,
				sourceOrder,
				occurrence,
				priority,
				conflict,
				Optional.empty(),
				List.of(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.of(sound),
				Optional.of(category),
				volume,
				pitch,
				Optional.of(attachment)
			);
		}

		private ResolvedNode resolve(final int ordinal) {
			if (this.channel.isPresent()) {
				return new ResolvedTextNode(
					ordinal,
					this.startOffsetNanos,
					this.priority,
					this.conflict,
					this.channel.orElseThrow(),
					this.segments.stream().map(SegmentDraft::resolve).toList(),
					this.effect.orElseThrow(),
					this.layout.orElseThrow(),
					this.duration.orElseThrow(),
					this.speaker.orElseThrow()
				);
			}
			return new ResolvedSoundNode(
				ordinal,
				this.startOffsetNanos,
				this.priority,
				this.conflict,
				this.sound.orElseThrow(),
				this.soundCategory.orElseThrow(),
				this.volume,
				this.pitch,
				this.attachment.orElseThrow()
			);
		}
	}
}
