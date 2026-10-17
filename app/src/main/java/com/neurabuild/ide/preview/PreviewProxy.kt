package com.neurabuild.ide.preview

import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * --------------------------------------------------------------------------------
 * WEBVIEW REVERSE-PROXY — READ THIS, IT IS THE #2 PLACE PEOPLE GET STUCK
 * --------------------------------------------------------------------------------
 * Node runs INSIDE the PRoot guest on 127.0.0.1:3000. In theory PRoot shares the
 * app's network namespace so the port should be reachable — in practice WebView
 * cleartext localhost loads are unreliable across OEMs/WebView versions, and
 * direct loads bypass our ability to inject headers, handle errors, or enforce
 * the "preview is OFF by default" gate.
 *
 * So: WebView -> http://127.0.0.1:8080 (this NanoHTTPD proxy, app process) ->
 * http://127.0.0.1:3000 (Node inside Alpine). The proxy forwards method, path,
 * query, headers and body, then streams the response back. If Node is down we
 * return a readable error page instead of net::ERR_CONNECTION_REFUSED.
 *
 * Manifest needs android:usesCleartextTraffic="true" for http:// loads.
 */
class PreviewProxy(
    private val targetPort: Int = 3000,
    listenPort: Int = 8080
) : NanoHTTPD("127.0.0.1", listenPort) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    override fun serve(session: IHTTPSession): Response {
        val target = "http://127.0.0.1:$targetPort${session.uri}" +
            (session.queryParameterString?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        return try {
            val bodyBytes = if (session.method == Method.POST || session.method == Method.PUT) {
                val files = HashMap<String, String>()
                session.parseBody(files)
                files["postData"]?.toByteArray(Charsets.UTF_8)
            } else null
            val ctype = session.headers["content-type"] ?: "application/octet-stream"
            val reqBuilder = Request.Builder().url(target)
            when (session.method) {
                Method.POST -> reqBuilder.post((bodyBytes ?: ByteArray(0)).toRequestBody(ctype.toMediaTypeOrNull()))
                Method.PUT -> reqBuilder.put((bodyBytes ?: ByteArray(0)).toRequestBody(ctype.toMediaTypeOrNull()))
                Method.DELETE -> reqBuilder.delete()
                else -> reqBuilder.get()
            }
            session.headers["user-agent"]?.let { reqBuilder.addHeader("User-Agent", it) }
            session.headers["accept"]?.let { reqBuilder.addHeader("Accept", it) }
            http.newCall(reqBuilder.build()).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val mime = resp.header("Content-Type")?.substringBefore(";")?.trim() ?: "text/html"
                newFixedLengthResponse(Response.Status.lookup(resp.code) ?: Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong())
            }
        } catch (e: Exception) {
            val html = "<html><body style='background:#121212;color:#eee;font-family:sans-serif'>" +
                "<h3>Preview proxy error</h3><p>Is your Node server running on :$targetPort inside Alpine?</p>" +
                "<pre>${e.message?.take(500)}</pre></body></html>"
            newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/html", html)
        }
    }
}
