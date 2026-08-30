package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.dzkchen.dhen.privacy.LocalUrls;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.Map;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HttpUtil.class)
public abstract class HttpUtilMixin {
	@WrapOperation(
		method = "downloadFile",
		at = @At(value = "INVOKE", target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;")
	)
	private static InputStream dhen$refuseLocalTargets(
		final HttpURLConnection connection,
		final Operation<InputStream> original,
		@Local(argsOnly = true) final Proxy proxy,
		@Local(argsOnly = true) final Map<String, String> headers,
		@Local final LocalRef<HttpURLConnection> lastHop
	) throws IOException {
		if (!LocalUrls.guarding()) return original.call(connection);
		final HttpURLConnection last = LocalUrls.follow(connection, proxy, headers);
		lastHop.set(last);
		return original.call(last);
	}
}
