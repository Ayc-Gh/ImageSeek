package com.akslabs.multiimagesearch

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
    bitmap: Bitmap,
    requestedQuality: Int
): String = withContext(Dispatchers.IO) {
    val encoded = encodeUploadJpeg(bitmap, requestedQuality)

    var litterboxFailure: IOException? = null
    for (attempt in 1..3) {
        try {
            return@withContext uploadToLitterbox(encoded.bytes)
        } catch (error: IOException) {
            litterboxFailure = error
            if (!isRetryableUploadFailure(error) || attempt == 3) break
            delay(if (attempt == 1) 450L else 1_100L)
        }
    }

    try {
        return@withContext uploadToUguu(encoded.bytes)
    } catch (uguuFailure: IOException) {
        throw IOException(
            "临时图片服务暂时不可用（Litterbox：${describeUploadFailure(litterboxFailure)}；Uguu：${describeUploadFailure(uguuFailure)}）。请稍后重试。",
            uguuFailure
        )
    }
}

private fun uploadToLitterbox(jpeg: ByteArray): String {
    val response = postMultipart(
        endpoint = "https://litterbox.catbox.moe/resources/internals/api.php",
        fields = listOf("reqtype" to "fileupload", "time" to "1h"),
        fileField = "fileToUpload",
        jpeg = jpeg,
        accept = "text/plain",
        provider = "Litterbox"
    )
    return validateLitterboxUrl(response)
}

private fun uploadToUguu(jpeg: ByteArray): String {
    val response = postMultipart(
        endpoint = "https://uguu.se/upload?output=text",
        fields = emptyList(),
        fileField = "files[]",
        jpeg = jpeg,
        accept = "text/plain",
        provider = "Uguu"
    )
    return validateUguuUrl(response)
}

private fun postMultipart(
    endpoint: String,
    fields: List<Pair<String, String>>,
    fileField: String,
    jpeg: ByteArray,
    accept: String,
    provider: String
): String {
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
    val suffix = "$crlf--$boundary--$crlf".toByteArray(StandardCharsets.UTF_8)
    val contentLength = prefix.size.toLong() + jpeg.size.toLong() + suffix.size.toLong()

    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.doInput = true
    connection.useCaches = false
    connection.instanceFollowRedirects = false
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("User-Agent", "ImageSeek/Android")
    connection.setRequestProperty("Accept", accept)
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    connection.setFixedLengthStreamingMode(contentLength)

    try {
        connection.outputStream.buffered(64 * 1024).use { output ->
            output.write(prefix)
            output.write(jpeg)
            output.write(suffix)
            output.flush()
        }

        val status = connection.responseCode
        val body = readResponseBody(connection, status)

        if (status !in 200..299) {
            throw UploadHttpException(provider, status, body)
        }
        if (body.isBlank()) {
            throw UploadProtocolException("$provider 返回空地址")
        }
        return body.trim()
    } finally {
        connection.disconnect()
    }
}

private fun readResponseBody(connection: HttpURLConnection, status: Int): String {
    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
    if (stream == null) return ""

    return stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
        val buffer = CharArray(2048)
        val result = StringBuilder()
        while (true) {
            val count = reader.read(buffer)
            if (count <= 0) break
            if (result.length + count > 16 * 1024) {
                throw UploadProtocolException("上传服务响应过大")
            }
            result.append(buffer, 0, count)
        }
        result.toString()
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
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw IOException("无法编码图片")
        }

        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            throw IOException("图片编码结果为空")
        }

        lastSize = bytes.size
        if (bytes.size <= maxBytes) {
            return EncodedJpeg(bytes, quality)
        }
    }

    throw IOException("压缩后的图片仍过大（${lastSize / 1024 / 1024} MiB），请使用均衡或快速质量")
}

private fun validateLitterboxUrl(value: String): String {
    val uri = parseSafeHttpsUrl(value, "Litterbox")
    if (uri.host?.lowercase() != "litter.catbox.moe") {
        throw UploadProtocolException("Litterbox 返回了非预期地址")
    }
    return uri.toASCIIString()
}

private fun validateUguuUrl(value: String): String {
    val uri = parseSafeHttpsUrl(value, "Uguu")
    val host = uri.host?.lowercase().orEmpty()
    if (host != "uguu.se" && !host.endsWith(".uguu.se")) {
        throw UploadProtocolException("Uguu 返回了非预期地址")
    }
    return uri.toASCIIString()
}

private fun parseSafeHttpsUrl(value: String, provider: String): URI {
    val uri = runCatching { URI(value) }.getOrNull()
        ?: throw UploadProtocolException("$provider 返回了无效地址")

    val pathOk = Regex("^/[A-Za-z0-9._-]+$").matches(uri.rawPath.orEmpty())

    if (
        uri.scheme?.lowercase() != "https" ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null ||
        uri.port != -1 ||
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        !pathOk
    ) {
        throw UploadProtocolException("$provider 返回了非预期地址")
    }

    return uri
}
