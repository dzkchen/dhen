package io.github.dzkchen.dhen.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.dzkchen.dhen.event.TabCompleteHooks;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CommandDispatcher.class, remap = false)
public abstract class CommandDispatcherMixin<S> {
	@Inject(
		method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void dhen$suggestArguments(
		final ParseResults<S> parse,
		final int cursor,
		final CallbackInfoReturnable<CompletableFuture<Suggestions>> callback
	) {
		final String typed = dhen$typed(parse, cursor);
		if (typed == null || typed.lastIndexOf(' ') < 0) {
			return;
		}
		final SuggestionsBuilder builder = dhen$offered(typed);
		if (builder != null) {
			callback.setReturnValue(builder.buildFuture());
		}
	}

	@Inject(
		method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void dhen$suggestCommandNames(
		final ParseResults<S> parse,
		final int cursor,
		final CallbackInfoReturnable<CompletableFuture<Suggestions>> callback
	) {
		final String typed = dhen$typed(parse, cursor);
		if (typed == null || typed.lastIndexOf(' ') >= 0) {
			return;
		}
		callback.setReturnValue(callback.getReturnValue().thenApply(original -> {
			final SuggestionsBuilder builder = dhen$offered(typed);
			if (builder == null) {
				return original;
			}
			for (final Suggestion suggestion : original.getList()) {
				builder.suggest(suggestion.getText());
			}
			return builder.build();
		}));
	}

	@Unique
	private String dhen$typed(final ParseResults<S> parse, final int cursor) {
		final String input = parse.getReader().getString();
		if (cursor < 0 || cursor > input.length()) {
			return null;
		}
		return input.substring(0, cursor);
	}

	@Unique
	private SuggestionsBuilder dhen$offered(final String typed) {
		final List<String> offered = TabCompleteHooks.suggestions(typed);
		if (offered == null) {
			return null;
		}
		final int space = typed.lastIndexOf(' ');
		final int start = space >= 0 ? space + 1 : (typed.startsWith("/") ? 1 : 0);
		final SuggestionsBuilder builder = new SuggestionsBuilder(typed, start);
		for (final String offer : offered) {
			builder.suggest(offer);
		}
		return builder;
	}
}
