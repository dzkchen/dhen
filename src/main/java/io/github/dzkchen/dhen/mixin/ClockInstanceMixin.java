package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.WorldClockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.ClientClockManager$ClockInstance")
public abstract class ClockInstanceMixin implements WorldClockAccess {
	@Shadow
	private long totalTicks;

	@Shadow
	private float partialTick;

	@Shadow
	private float rate;

	@Override
	public long dhenTotalTicks() {
		return this.totalTicks;
	}

	@Override
	public float dhenPartialTick() {
		return this.partialTick;
	}

	@Override
	public float dhenRate() {
		return this.rate;
	}
}
