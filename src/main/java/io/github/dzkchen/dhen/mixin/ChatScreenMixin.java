package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.chat.ChatScreenBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected EditBox input;

	@Unique
	private EditBox dhen$search;

	protected ChatScreenMixin(final Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void dhen$addSearchBar(final CallbackInfo callback) {
		this.dhen$search = null;
		dhen$openSearchBar();
		this.input.setCanLoseFocus(this.dhen$search != null);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void dhen$dropSearchBar(final CallbackInfo callback) {
		ChatScreenBar.closed();
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dhen$searchKeys(final KeyEvent key, final CallbackInfoReturnable<Boolean> callback) {
		final int outcome = ChatScreenBar.keyed(key, this.dhen$search);
		if (outcome == ChatScreenBar.IGNORED) {
			return;
		}
		if (outcome == ChatScreenBar.OPENED) {
			dhen$openSearchBar();
		} else if (outcome == ChatScreenBar.CLOSED) {
			dhen$closeSearchBar();
		}
		if (outcome != ChatScreenBar.HANDLED) {
			ChatScreenBar.focus(this, this.dhen$search, this.input, outcome == ChatScreenBar.OPENED);
		}
		callback.setReturnValue(true);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void dhen$tabClicks(
		final MouseButtonEvent click,
		final boolean doubleClick,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (ChatScreenBar.clicked(click, this.height, this.font)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void dhen$drawBarBackdrop(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		ChatScreenBar.beneath(graphics, this.font, this.width, this.height, mouseX, mouseY);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void dhen$drawMatchCount(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		ChatScreenBar.above(graphics, this.font, this.width, this.height);
	}

	@Unique
	private void dhen$openSearchBar() {
		if (this.dhen$search != null) {
			return;
		}
		this.dhen$search = ChatScreenBar.searchBox(this.font, this.width, this.height);
		if (this.dhen$search != null) {
			addRenderableWidget(this.dhen$search);
		}
	}

	@Unique
	private void dhen$closeSearchBar() {
		if (this.dhen$search == null) {
			return;
		}
		removeWidget(this.dhen$search);
		this.dhen$search = null;
	}
}
