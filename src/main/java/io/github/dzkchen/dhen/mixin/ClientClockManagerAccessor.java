package io.github.dzkchen.dhen.mixin;

import java.util.Map;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientClockManager.class)
public interface ClientClockManagerAccessor {
	@Accessor("clocks")
	Map<Holder<WorldClock>, Object> liveClocks();
}
