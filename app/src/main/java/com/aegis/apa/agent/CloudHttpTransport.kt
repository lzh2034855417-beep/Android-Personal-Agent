package com.aegis.apa.agent

import java.net.HttpURLConnection
import java.net.URL

/** HTTP boundary shared by both protocols; body and response parsing stay in the provider. */
object CloudHttpTransport {
    fun post(
        config: CloudProviderConfig,
        credentials: StoredApiKey,
        body: String,
        cancellation: CloudRequestCancellation,
        open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
    ): String {
        cancellation.ensureActive()
        val connection = open(URL(config.endpoint))
        try {
            cancellation.register { connection.disconnect() }
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (config.protocol == "anthropic") {
                connection.setRequestProperty("x-api-key", credentials.apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                connection.setRequestProperty("Authorization", "Bearer ${credentials.apiKey}")
            }
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            cancellation.ensureActive()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                // Android connects lazily; a disconnect before acquisition may have no socket to close.
                cancellation.ensureActive()
                it.write(body)
                cancellation.ensureActive()
            }
            cancellation.ensureActive()
            val status = connection.responseCode
            if (status !in 200..299) throw ModelHttpException(status)
            return CloudResponseReader.read(connection.inputStream).also { cancellation.ensureActive() }
        } finally {
            cancellation.release()
            connection.disconnect()
        }
    }
}
