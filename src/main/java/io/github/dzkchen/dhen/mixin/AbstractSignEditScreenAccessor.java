package io.github.dzkchen.dhen.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
	@Accessor("sign")
	SignBlockEntity dhenSign();

	@Accessor("isFrontText")
	boolean dhenFrontText();

	@Accessor("messages")
	String[] dhenMessages();
	@Accessor("line")
	int dhenLine();

	@Accessor("line")
	void dhenSetLine(int line);

	@Invoker("setMessage")
	void dhenSetMessage(String message);
}
