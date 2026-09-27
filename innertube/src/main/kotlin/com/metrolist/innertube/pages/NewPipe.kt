package com.nestmusic.innertube

import com.nestmusic.innertube.models.YouTubeClient
import com.nestmusic.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Proxy

private class NewPipeDownloaderImpl(
    proxy: Proxy?,
    proxyAuth: String?,
) : Downloader() {
    private fun normalizeResponseBody(
        url: String,
        body: String?,
    ): String? {
        if (!url.contains("returnyoutubedislikeapi.com", ignoreCase = true)) {
            return body
        }

        val trimmed = body?.trimStart().orEmpty()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return body
        }

        return "{\"likes\":0,\"dislikes\":0,\"viewCount\":0}"
    }

    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .proxyAuthenticator { _, response ->
                proxyAuth?.let { auth ->
                    response.request
                        .newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }.build()

    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, dataToSend?.toRequestBody())
                .url(url)
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        return requestBuilder.build()
    }

    /**
     * Maps an okhttp response to the extractor one. [requestUrl] is only used for the
     * [ReCaptchaException] so that it keeps reporting the URL that was actually requested,
     * even when the response comes from a redirect.
     */
    @Throws(IOException::class, ReCaptchaException::class)
    private fun toExtractorResponse(
        requestUrl: String,
        response: okhttp3.Response,
    ): Response {
        if (response.code == 429) {
            response.close()

            throw ReCaptchaException("reCaptcha Challenge requested", requestUrl)
        }

        val latestUrl = response.request.url.toString()
        val responseBodyToReturn = normalizeResponseBody(latestUrl, response.body.string())
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            responseBodyToReturn?.toByteArray(),
            latestUrl,
        )
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response =
        toExtractorResponse(request.url(), client.newCall(buildOkHttpRequest(request)).execute())

    override fun executeAsync(
        request: Request,
        callback: AsyncCallback?,
    ): CancellableCall {
        val call = client.newCall(buildOkHttpRequest(request))
        val cancellableCall = CancellableCall(call)

        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    cancellableCall.setFinished()
                    callback?.onError(e)
                }

                override fun onResponse(
                    call: Call,
                    response: okhttp3.Response,
                ) {
                    val extractorResponse =
                        try {
                            toExtractorResponse(request.url(), response)
                        } catch (e: Exception) {
                            cancellableCall.setFinished()
                            callback?.onError(e)
                            return
                        }

                    cancellableCall.setFinished()
                    // A failure raised by the callback itself is not a download failure: forward it
                    // so the extractor can decide how to treat a parse error.
                    try {
                        callback?.onSuccess(extractorResponse)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                }
            },
        )

        return cancellableCall
    }
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl(YouTube.proxy, YouTube.proxyAuth))
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): Result<String> =
        runCatching {
            val url =
                format.url ?: format.signatureCipher?.let { signatureCipher ->
                    val params = parseQueryString(signatureCipher)
                    val obfuscatedSignature =
                        params["s"]
                            ?: throw ParsingException("Could not parse cipher signature")
                    val signatureParam =
                        params["sp"]
                            ?: throw ParsingException("Could not parse cipher signature parameter")
                    val url =
                        params["url"]?.let { URLBuilder(it) }
                            ?: throw ParsingException("Could not parse cipher url")
                    url.parameters[signatureParam] =
                        YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                            videoId,
                            obfuscatedSignature,
                        )
                    url.toString()
                } ?: throw ParsingException("Could not find format url")

            return@runCatching YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        }
}

object NewPipeExtractor {
    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        return try {
            val streamInfo =
                StreamInfo.getInfo(
                    NewPipe.getService(0),
                    "https://www.youtube.com/watch?v=$videoId",
                )
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streamsList.mapNotNull {
                (it.itagItem?.id ?: return@mapNotNull null) to it.content
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        return try {
            val url = format.url ?: format.signatureCipher?.let { signatureCipher ->
                val params = parseQueryString(signatureCipher)
                val obfuscatedSignature = params["s"]
                    ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam = params["sp"]
                    ?: throw ParsingException("Could not parse cipher signature parameter")
                val url = params["url"]?.let { URLBuilder(it) }
                    ?: throw ParsingException("Could not parse cipher url")
                url.parameters[signatureParam] =
                    YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                        videoId,
                        obfuscatedSignature
                    )
                url.toString()
            } ?: throw ParsingException("Could not find format url")

            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        } catch (e: Exception) {
            null
        }
    }
}
