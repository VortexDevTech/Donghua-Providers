package com.Anoboye

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.net.URI

// =========================
// DARK PLAYER
// =========================
open class DarkPlayer : ExtractorApi() {

    override val name = "DarkPlayer"
    override val mainUrl = "https://anoboye.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val actualUrl = url.substringBefore("#")
        val serverName = url.substringAfter("server=", "").ifBlank { "Unknown" }
        val displayName = "$serverName $name"

        val res = app.get(actualUrl, referer = referer ?: mainUrl).text

        val videoUrl = Regex("""videoUrl\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(res)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")

        if (videoUrl != null) {

            if (videoUrl.contains("action=playlist")) {

                M3u8Helper.generateM3u8(
                    displayName,
                    videoUrl,
                    mainUrl
                ).forEach(callback)

            } else {

                callback.invoke(
                    newExtractorLink(
                        displayName,
                        displayName,
                        videoUrl,
                        INFER_TYPE
                    ) {
                        referer = referer ?: mainUrl
                        quality = Qualities.Unknown.value
                    }
                )
            }
        }

        // 🔹 Subtitles
        val trackRegex = Regex(
            """"file"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"label"\s*:\s*"([^"]+)""""
        )

        trackRegex.findAll(res).forEach { track ->
            val fileUrl = track.groupValues[1].replace("\\/", "/")
            val label = track.groupValues[2]

            if (fileUrl.endsWith(".vtt") || fileUrl.endsWith(".srt")) {
                subtitleCallback.invoke(newSubtitleFile(label, fileUrl))
            }
        }
    }
}

// =========================
// CUSTOM DAILYMOTION
// =========================
class CustomDailymotion : Dailymotion() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val actualUrl = url.substringBefore("#")
        val serverName = url.substringAfter("server=", "").ifBlank { "Unknown" }
        val displayName = "$serverName Dailymotion"

        val embedUrl = getEmbedUrl(actualUrl) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$mainUrl/player/metadata/video/$id"

        val response = app.get(metaDataUrl, referer = embedUrl).text
        val meta: MetaData = Gson().fromJson(response, MetaData::class.java)

        meta.qualities?.get("auto")?.forEach { quality: Quality ->
            val videoUrl = quality.url

            if (!videoUrl.isNullOrEmpty() && videoUrl.contains(".m3u8")) {
                M3u8Helper.generateM3u8(
                    displayName,
                    videoUrl,
                    ""
                ).forEach(callback)
            }
        }

        // ✅ FIXED (no destructuring)
        meta.subtitles?.data?.values?.forEach { subData ->
            subData.urls.forEach { subUrl ->
                subtitleCallback.invoke(
                    newSubtitleFile(subData.label, subUrl)
                )
            }
        }
    }

    // =========================
    // HELPERS
    // =========================

    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) return url
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "https://www.dailymotion.com/embed/video/$videoId"
        }
        return null
    }

    private fun getVideoId(url: String): String? {
        val path = URI(url).path
        val id = path.substringAfter("/video/")
        return if (id.matches(videoIdRegex)) id else null
    }

    // =========================
    // DATA MODELS
    // =========================

    data class MetaData(
        val qualities: Map<String, List<Quality>>?,
        val subtitles: SubtitlesWrapper?
    )

    data class Quality(
        val type: String?,
        val url: String?
    )

    data class SubtitlesWrapper(
        val enable: Boolean,
        val data: Map<String, SubtitleData>?
    )

    data class SubtitleData(
        val label: String,
        val urls: List<String>
    )
}

// =========================
// HELPER
// =========================
fun Http(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else {
        url
    }
}