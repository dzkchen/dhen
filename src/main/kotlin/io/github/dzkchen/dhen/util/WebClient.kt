package io.github.dzkchen.dhen.util

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal fun interface WebSource {
	fun text(url: String): String?
}

internal class WebClient(
	private val headers: Map<String, String> = emptyMap(),
	private val client: Lazy<HttpClient> = SHARED,
	private val logAs: String? = null
) : WebSource {
	override fun text(url: String): String? = send(url, HttpResponse.BodyHandlers.ofString(), REPLY_TIMEOUT)

	fun <T> send(url: String, body: HttpResponse.BodyHandler<T>, deadline: Duration? = null): T? = try {
		val request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", Dhen.MOD_ID)
		deadline?.let(request::timeout)
		for ((name, value) in headers) request.setHeader(name, value)
		val response = client.value.send(request.build(), body)
		if (response.statusCode() == OK) response.body() else {
			log.warn("Dhen request to {} answered {}", logAs ?: url, response.statusCode())
			null
		}
	} catch (throwable: Throwable) {
		log.warn("Dhen request to {} failed", logAs ?: url, throwable)
		null
	}

	private companion object {
		private const val OK = 200
		private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
		private val REPLY_TIMEOUT: Duration = Duration.ofSeconds(60)
		private val SHARED: Lazy<HttpClient> = lazy {
			HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(CONNECT_TIMEOUT)
				.build()
		}
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
