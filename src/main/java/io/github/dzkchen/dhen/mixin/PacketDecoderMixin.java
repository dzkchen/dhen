package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.dzkchen.dhen.privacy.PacketContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketDecoder.class)
public class PacketDecoderMixin {
	@WrapOperation(
		method = "decode",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"
		)
	)
	private Object dhen$trackDecode(
		final StreamCodec<?, ?> codec,
		final Object buffer,
		final Operation<Object> original
	) {
		PacketContext.beginDecode();
		Object packet = null;
		try {
			packet = original.call(codec, buffer);
			return packet;
		} finally {
			PacketContext.endDecode(packet);
		}
	}
}
