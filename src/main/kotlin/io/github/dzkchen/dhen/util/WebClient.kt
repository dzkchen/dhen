package io.github.dzkchen.dhen.util

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class WebResponse(
	val body: String?,
	val statusCode: Int? = null,
	val retryAfter: kotlin.time.Duration? = null
)

internal fun interface WebSource {
	fun text(url: String): String?

	fun response(url: String): WebResponse = WebResponse(text(url))
}

internal class WebClient(
	private val headers: Map<String, String> = emptyMap(),
	private val client: Lazy<HttpClient> = SHARED,
	private val logAs: String? = null
) : WebSource {
	override fun text(url: String): String? = response(url).body

	override fun response(url: String): WebResponse {
		val reply = sendResponse(url, HttpResponse.BodyHandlers.ofString(), REPLY_TIMEOUT)
		return WebResponse(reply.body, reply.statusCode, reply.retryAfter)
	}

	fun <T> send(url: String, body: HttpResponse.BodyHandler<T>, deadline: Duration? = null): T? =
		sendResponse(url, body, deadline).body

	private fun <T> sendResponse(
		url: String,
		body: HttpResponse.BodyHandler<T>,
		deadline: Duration?
	): HttpReply<T> = try {
		val request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", Dhen.MOD_ID)
		deadline?.let(request::timeout)
		for ((name, value) in headers) request.setHeader(name, value)
		val response = client.value.send(request.build(), body)
		val statusCode = response.statusCode()
		val reply = if (statusCode == OK) response.body() else {
			log.warn("Dhen request to {} answered {}", logAs ?: url, response.statusCode())
			null
		}
		HttpReply(reply, statusCode, retryAfter(response))
	} catch (throwable: Throwable) {
		log.warn("Dhen request to {} failed", logAs ?: url, throwable)
		HttpReply(null, null, null)
	}

	private fun retryAfter(response: HttpResponse<*>): kotlin.time.Duration? =
		response.headers().firstValue(RETRY_AFTER).orElse(null)
			?.trim()
			?.toLongOrNull()
			?.takeIf { it >= 0 }
			?.seconds

	private data class HttpReply<T>(
		val body: T?,
		val statusCode: Int?,
		val retryAfter: kotlin.time.Duration?
	)

	private companion object {
		private const val OK = 200
		private const val RETRY_AFTER = "Retry-After"
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
