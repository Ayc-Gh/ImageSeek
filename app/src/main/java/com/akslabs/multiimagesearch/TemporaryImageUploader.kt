package com.akslabs.multiimagesearch

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

private class UploadHttpException(
    val provider: String,
    val statusCode: Int,
    val responseBody: String
) : IOException("$provider HTTP $statusCode")

private class UploadProtocolException(message: String) : IOException(message)

private data class EncodedJpeg(val bytes: ByteArray, val quality: Int)

internal suspend fun uploadTemporaryWithFallback(
    context: Context,
    bitmap: Bitmap,
    requestedQuality: Int
): String = withContext(Dispatchers.IO) {
    val started = System.nanoTime()
    DebugLog.i(
        "UPLOAD",
        "begin bitmap=${bitmap.width}x${bitmap.height} config=${bitmap.config} allocationByteCount=${bitmap.allocationByteCount} byteCount=${bitmap.byteCount} requestedQuality=$requestedQuality"
    )

    val encoded = encodeUploadJpeg(bitmap, requestedQuality)
    DebugLog.i(
        "UPLOAD",
        "encoded jpeg quality=${encoded.quality} bytes=${encoded.bytes.size} sha256=${DebugLog.sha256(encoded.bytes)}"
    )
    DebugLog.captureBytes(context, "upload-jpeg-q${encoded.quality}", "jpg", "image/jpeg", encoded.bytes)

    var litterboxFailure: IOException? = null
    for (attempt in 1..3) {
        val attemptStarted = System.nanoTime()
        try {
            DebugLog.i("UPLOAD", "provider=Litterbox attempt=$attempt/3 start")
            val result = uploadToLitterbox(encoded.bytes, attempt)
            DebugLog.i(
                "UPLOAD",
                "provider=Litterbox attempt=$attempt success url=$result elapsedMs=${elapsedMs(attemptStarted)} totalElapsedMs=${elapsedMs(started)}"
            )
            return@withContext result
        } catch (error: IOException) {
            litterboxFailure = error
            DebugLog.exception(
                "UPLOAD",
                error,
                "provider=Litterbox attempt=$attempt failed retryable=${isRetryableUploadFailure(error)} elapsedMs=${elapsedMs(attemptStarted)}"
            )
            if (!isRetryableUploadFailure(error) || attempt == 3) break
            val delayMs = if (attempt == 1) 450L else 1_100L
            DebugLog.w("UPLOAD", "provider=Litterbox retryDelayMs=$delayMs nextAttempt=${attempt + 1}")
            delay(delayMs)
        }
    }

    val fallbackStarted = System.nanoTime()
    try {
        DebugLog.w("UPLOAD", "fallback=Uguu start litterboxFailure=${litterboxFailure?.stackTraceToString()}")
        val result = uploadToUguu(encoded.bytes)
        DebugLog.i(
            "UPLOAD",
            "provider=Uguu success url=$result elapsedMs=${elapsedMs(fallbackStarted)} totalElapsedMs=${elapsedMs(started)}"
        )
        return@withContext result
    } catch (uguuFailure: IOException) {
        DebugLog.exception(
            "UPLOAD",
            uguuFailure,
            "provider=Uguu fallback failed elapsedMs=${elapsedMs(fallbackStarted)} totalElapsedMs=${elapsedMs(started)} litterboxFailure=${litterboxFailure?.stackTraceToString()}"
        )
        throw IOException(
            "临时图片服务暂时不可用（Litterbox：${describeUploadFailure(litterboxFailure)}；Uguu：${describeUploadFailure(uguuFailure)}）。请查看详细 Debug 日志。",
            uguuFailure
        )
    }
}

private fun uploadToLitterbox(jpeg: ByteArray, attempt: Int): String {
    val response = postMultipart(
        endpoint = "https://litterbox.catbox.moe/resources/internals/api.php",
        fields = listOf("reqtype" to "fileupload", "time" to "1h"),
        fileField = "fileToUpload",
        jpeg = jpeg,
        accept = "text/plain",
        provider = "Litterbox",
        attempt = attempt
    )
    DebugLog.i("UPLOAD_VALIDATE", "provider=Litterbox rawResponse=$response")
    return validateLitterboxUrl(response).also {
        DebugLog.i("UPLOAD_VALIDATE", "provider=Litterbox validatedUrl=$it")
    }
}

private fun uploadToUguu(jpeg: ByteArray): String {
    val response = postMultipart(
        endpoint = "https://uguu.se/upload?output=text",
        fields = emptyList(),
        fileField = "files[]",
        jpeg = jpeg,
        accept = "text/plain",
        provider = "Uguu",
        attempt = 1
    )
    DebugLog.i("UPLOAD_VALIDATE", "provider=Uguu rawResponse=$response")
    return validateUguuUrl(response).also {
        DebugLog.i("UPLOAD_VALIDATE", "provider=Uguu validatedUrl=$it")
    }
}

private fun postMultipart(
    endpoint: String,
    fields: List<Pair<String, String>>,
    fileField: String,
    jpeg: ByteArray,
    accept: String,
    provider: String,
    attempt: Int
): String {
    val started = System.nanoTime()
    val boundary = "----ImageSeek${UUID.randomUUID().toString().replace("-", "")}" 
    val crlf = "\r\n"
    val prefixText = buildString {
        for ((name, value) in fields) {
            append("--$boundary$crlf")
            append("Content-Disposition: form-data; name=\"")
            append(name)
            append("\"$crlf$crlf")
            append(value)
            append(crlf)
        }
        append("--$boundary$crlf")
        append("Content-Disposition: form-data; name=\"")
        append(fileField)
        append("\"; filename=\"image.jpg\"$crlf")
        append("Content-Type: image/jpeg$crlf$crlf")
    }
    val prefix = prefixText.toByteArray(StandardCharsets.UTF_8)
    val suffixText = "$crlf--$boundary--$crlf"
    val suffix = suffixText.toByteArray(StandardCharsets.UTF_8)
    val contentLength = prefix.size.toLong() + jpeg.size.toLong() + suffix.size.toLong()

    DebugLog.i(
        "HTTP_REQUEST",
        "provider=$provider attempt=$attempt method=POST url=$endpoint connectTimeoutMs=15000 readTimeoutMs=30000 followRedirects=false contentLength=$contentLength boundary=$boundary fields=$fields fileField=$fileField filename=image.jpg contentType=image/jpeg jpegBytes=${jpeg.size} jpegSha256=${DebugLog.sha256(jpeg)}\nmultipartPrefix=\n$prefixText\nmultipartSuffix=\n$suffixText"
    )

    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.doInput = true
    connection.useCaches = false
    connection.instanceFollowRedirects = false
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("User-Agent", "ImageSeek/2.1.2-debug-log Android")
    connection.setRequestProperty("Accept", accept)
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    connection.setFixedLengthStreamingMode(contentLength)

    try {
        DebugLog.i(
            "HTTP_REQUEST_HEADERS",
            "provider=$provider attempt=$attempt headers=${connection.requestProperties}"
        )
        connection.outputStream.buffered(64 * 1024).use { output ->
            output.write(prefix)
            output.write(jpeg)
            output.write(suffix)
            output.flush()
        }
        DebugLog.i("HTTP_REQUEST", "provider=$provider attempt=$attempt requestBodyWritten bytes=$contentLength")

        val status = connection.responseCode
        val message = connection.responseMessage
        val headers = connection.headerFields.entries.joinToString(separator = "\n") { (key, values) ->
            "${key ?: "<status>"}: ${values?.joinToString(" | ") ?: "null"}"
        }
        val body = runCatching {
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.getOrElse { readError ->
            DebugLog.exception("HTTP_RESPONSE", readError, "provider=$provider attempt=$attempt responseBodyReadFailed status=$status")
            "<response body read failed: ${readError.stackTraceToString()}>"
        }

        DebugLog.i(
            "HTTP_RESPONSE",
            "provider=$provider attempt=$attempt status=$status message=$message elapsedMs=${elapsedMs(started)} url=${connection.url} contentType=${connection.contentType} contentLength=${connection.contentLengthLong}\nheaders=\n$headers\nbody=\n$body"
        )

        if (status !in 200..299) {
            throw UploadHttpException(provider, status, body)
        }
        if (body.isBlank()) throw UploadProtocolException("$provider 返回空地址")
        return body.trim()
    } catch (error: IOException) {
        DebugLog.exception(
            "HTTP_EXCEPTION",
            error,
            "provider=$provider attempt=$attempt endpoint=$endpoint elapsedMs=${elapsedMs(started)} requestProperties=${runCatching { connection.requestProperties }.getOrNull()} responseCode=${runCatching { connection.responseCode }.getOrNull()} responseMessage=${runCatching { connection.responseMessage }.getOrNull()} responseHeaders=${runCatching { connection.headerFields }.getOrNull()}"
        )
        throw error
    } finally {
        DebugLog.d("HTTP", "provider=$provider attempt=$attempt disconnect endpoint=$endpoint totalElapsedMs=${elapsedMs(started)}")
        connection.disconnect()
    }
}

private fun isRetryableUploadFailure(error: IOException): Boolean = when (error) {
    is UploadHttpException -> error.statusCode in setOf(408, 425, 429, 500, 502, 503, 504)
    is UploadProtocolException -> false
    else -> true
}

private fun describeUploadFailure(error: IOException?): String = when (error) {
    null -> "未知错误"
    is UploadHttpException -> "HTTP ${error.statusCode}"
    is SocketTimeoutException -> "连接超时"
    is UploadProtocolException -> error.message ?: "响应异常"
    else -> "网络错误"
}

private fun encodeUploadJpeg(bitmap: Bitmap, requestedQuality: Int): EncodedJpeg {
    val maxBytes = 12 * 1024 * 1024
    var lastSize = 0
    for (quality in listOf(requestedQuality, minOf(requestedQuality, 86), 80).distinct()) {
        val started = System.nanoTime()
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw IOException("无法编码图片")
        }
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) throw IOException("图片编码结果为空")
        lastSize = bytes.size
        DebugLog.i(
            "JPEG",
            "attempt quality=$quality resultBytes=${bytes.size} maxBytes=$maxBytes sha256=${DebugLog.sha256(bytes)} elapsedMs=${elapsedMs(started)}"
        )
        if (bytes.size <= maxBytes) return EncodedJpeg(bytes, quality)
    }
    throw IOException("压缩后的图片仍过大（${lastSize / 1024 / 1024} MiB），请使用均衡或快速质量")
}

private fun validateLitterboxUrl(value: String): String {
    val uri = parseSafeHttpsUrl(value, "Litterbox")
    if (uri.host?.lowercase() != "litter.catbox.moe") {
        throw UploadProtocolException("Litterbox 返回了非预期地址：$value")
    }
    return uri.toASCIIString()
}

private fun validateUguuUrl(value: String): String {
    val uri = parseSafeHttpsUrl(value, "Uguu")
    val host = uri.host?.lowercase().orEmpty()
    if (host != "uguu.se" && !host.endsWith(".uguu.se")) {
        throw UploadProtocolException("Uguu 返回了非预期地址：$value")
    }
    return uri.toASCIIString()
}

private fun parseSafeHttpsUrl(value: String, provider: String): URI {
    DebugLog.d("UPLOAD_VALIDATE", "provider=$provider parse value=$value")
    val uri = runCatching { URI(value) }.getOrNull()
        ?: throw UploadProtocolException("$provider 返回了无效地址：$value")
    val pathOk = Regex("^/[A-Za-z0-9._-]+$").matches(uri.rawPath.orEmpty())
    DebugLog.d(
        "UPLOAD_VALIDATE",
        "provider=$provider scheme=${uri.scheme} host=${uri.host} port=${uri.port} path=${uri.rawPath} query=${uri.rawQuery} fragment=${uri.rawFragment} userInfo=${uri.userInfo} pathOk=$pathOk"
    )
    if (
        uri.scheme?.lowercase() != "https" ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null ||
        uri.port != -1 ||
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        !pathOk
    ) {
        throw UploadProtocolException("$provider 返回了非预期地址：$value")
    }
    return uri
}

private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L
