package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.util.ThreadTuning;
import java.util.concurrent.ThreadFactory;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Util.class)
public abstract class UtilMixin {
	@ModifyArg(
		method = "makeIoExecutor",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/Executors;newCachedThreadPool"
				+ "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
		)
	)
	private static ThreadFactory dhen$prioritiseIoThreads(final ThreadFactory factory) {
		return runnable -> {
			final Thread thread = factory.newThread(runnable);
			thread.setPriority(ThreadTuning.ioPriority());
			return thread;
		};
	}
}
