package com.Anoboye

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.*
import java.net.URI

open class PlayerExtractor : ExtractorApi() {

    override val name = "Player"
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

       
        if (actualUrl.contains("dailyplayer.php")) {
            handleDailymotion(actualUrl, serverName, subtitleCallback, callback)
        } else {
            handleDarkPlayer(actualUrl, serverName, referer, subtitleCallback, callback)
        }
    }

  
    private suspend fun handleDarkPlayer(
        url: String,
        serverName: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val displayName = "$serverName DarkPlayer"

        val res = app.get(url, referer = referer ?: mainUrl).text

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
                        this.referer = referer ?: mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        
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

    
    private suspend fun handleDailymotion(
        url: String,
        serverName: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val displayName = "$serverName Dailymotion"

        val id = getVideoId(embedUrl) ?: return
        val embedUrl = getEmbedUrl(id) ?: return
       
        val metaDataUrl = "https://www.dailymotion.com/player/metadata/video/$id"

        val response = app.get(metaDataUrl, referer = embedUrl).text
        val meta: MetaData = Gson().fromJson(response, MetaData::class.java)

        meta.qualities?.get("auto")?.forEach { quality ->
            val videoUrl = quality.url

            if (!videoUrl.isNullOrEmpty() && videoUrl.contains(".m3u8")) {
                M3u8Helper.generateM3u8(
                    displayName,
                    videoUrl,
                    ""
                ).forEach(callback)
            }
        }

        meta.subtitles?.data?.values?.forEach { subData ->
            subData.urls.forEach { subUrl ->
                subtitleCallback.invoke(
                    newSubtitleFile(subData.label, subUrl)
                )
            }
        }
    }

   

    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    private fun getEmbedUrl(id: String): String? {
        return "https://www.dailymotion.com/embed/video/$id"
    }

    private fun getVideoId(url: String): String? {
        val id = Regex("""id=([^&]+)""").find(url)?.groupValues?.get(1)
        return if (id != null && id.matches(videoIdRegex)) id else null
    }

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

fun Http(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else {
        url
    }
}