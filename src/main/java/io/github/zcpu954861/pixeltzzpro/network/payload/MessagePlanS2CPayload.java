package io.github.zcpu954861.pixeltzzpro.network.payload;

import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.readByteArray;
import static io.github.zcpu954861.pixeltzzpro.network.payload.PayloadCodecUtil.writeByteArray;

import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import io.github.zcpu954861.pixeltzzpro.network.message.MessagePlaybackPlan;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One bounded, per-player playback plan with all authorization and branching already resolved.
 */
public record MessagePlanS2CPayload(
	MessagePlaybackPlan plan
) implements CustomPacketPayload {
	public static final Type<MessagePlanS2CPayload> TYPE = new Type<>(
		PixelTzzPro.id("message_plan_s2c")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, MessagePlanS2CPayload>
		STREAM_CODEC = CustomPacketPayload.codec(
			MessagePlanS2CPayload::write,
			MessagePlanS2CPayload::new
		);

	public MessagePlanS2CPayload {
		plan = Objects.requireNonNull(plan, "plan");
	}

	private MessagePlanS2CPayload(final RegistryFriendlyByteBuf buffer) {
		this(
			MessagePlaybackPlan.decode(
				readByteArray(
					buffer,
					MessagePlaybackPlan.MAX_PLAN_BYTES,
					"messagePlan"
				)
			)
		);
	}

	private void write(final RegistryFriendlyByteBuf buffer) {
		writeByteArray(buffer, this.plan.encode());
	}

	@Override
	public Type<MessagePlanS2CPayload> type() {
		return TYPE;
	}
}
