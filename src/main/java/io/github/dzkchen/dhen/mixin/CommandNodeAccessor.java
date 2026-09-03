package io.github.dzkchen.dhen.mixin;

import com.mojang.brigadier.tree.CommandNode;
import io.github.dzkchen.dhen.features.chat.CommandNodeAccess;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccessor extends CommandNodeAccess {
	@Override
	@Accessor("children")
	Map<String, CommandNode<?>> commandChildren();

	@Override
	@Accessor("literals")
	Map<String, CommandNode<?>> commandLiterals();

	@Override
	@Accessor("arguments")
	Map<String, CommandNode<?>> commandArguments();
}
