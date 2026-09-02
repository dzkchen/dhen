package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.event.ScreenHooks;
import io.github.dzkchen.dhen.event.TooltipEvent;
import io.github.dzkchen.dhen.features.inventory.ContainerOrigin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements ContainerOrigin {
	@Shadow
	protected Slot hoveredSlot;

	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Override
	public int dhenContainerLeft() {
		return this.leftPos;
	}

	@Override
	public int dhenContainerTop() {
		return this.topPos;
	}

	@Override
	public Slot dhenHoveredSlot() {
		return this.hoveredSlot;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeClick(
		final MouseButtonEvent click,
		final boolean doubleClick,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (ScreenHooks.beforeContainerClick((AbstractContainerScreen<?>)(Object)this, click, this.hoveredSlot)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeKey(final KeyEvent key, final CallbackInfoReturnable<Boolean> callback) {
		if (ScreenHooks.beforeContainerKey((AbstractContainerScreen<?>)(Object)this, key, this.hoveredSlot)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeScroll(
		final double mouseX,
		final double mouseY,
		final double scrollX,
		final double scrollY,
		final CallbackInfoReturnable<Boolean> callback
	) {
		final boolean swallowed = ScreenHooks.beforeContainerScroll(
			(AbstractContainerScreen<?>)(Object)this,
			mouseX,
			mouseY,
			scrollX,
			scrollY,
			this.hoveredSlot
		);
		if (swallowed) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeSlot(
		final GuiGraphicsExtractor graphics,
		final Slot slot,
		final int mouseX,
		final int mouseY,
		final CallbackInfo callback
	) {
		if (ScreenHooks.beforeSlotRender((AbstractContainerScreen<?>)(Object)this, graphics, slot)) {
			callback.cancel();
		}
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void dhen$afterSlot(
		final GuiGraphicsExtractor graphics,
		final Slot slot,
		final int mouseX,
		final int mouseY,
		final CallbackInfo callback
	) {
		ScreenHooks.afterSlotRender((AbstractContainerScreen<?>)(Object)this, graphics, slot);
	}

	@WrapOperation(
		method = "extractTooltip",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"
		)
	)
	private void dhen$rewriteTooltip(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final List<Component> lines,
		final Optional<TooltipComponent> image,
		final int x,
		final int y,
		final Identifier style,
		final Operation<Void> original,
		@Local final ItemStack stack
	) {
		final TooltipEvent event = ScreenHooks.beforeTooltip(
			(AbstractContainerScreen<?>)(Object)this,
			graphics,
			this.hoveredSlot,
			stack,
			lines,
			x,
			y
		);
		if (event == null) {
			original.call(graphics, font, lines, image, x, y, style);
			return;
		}
		try {
			original.call(graphics, font, event.getLines(), image, event.getX(), event.getY(), style);
		} finally {
			ScreenHooks.releaseTooltip(event);
		}
	}
}
