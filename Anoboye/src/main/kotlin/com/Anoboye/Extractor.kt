package com.Anoboye

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import org.jsoup.Jsoup
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile


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

        val res = app.get(url, referer = referer ?: mainUrl).text

        // =========================
        // 🎯 Extract video URL
        // =========================
        val videoUrl = Regex("""videoUrl\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(res)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")

        Log.d("Anoboye", "DarkPlayer videoUrl: $videoUrl")

        if (videoUrl != null) {

            if (videoUrl.contains("action=playlist")) {
                // HLS stream
                M3u8Helper.generateM3u8(name, videoUrl, mainUrl).forEach(callback)
            } else {
                // Direct MP4
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        videoUrl,
                        INFER_TYPE
                    ) {
                        this.referer = referer ?: mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        // =========================
        // 🎯 Extract subtitles
        // =========================
        val trackRegex = Regex(
            """"file"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"label"\s*:\s*"([^"]+)""""
        )

        val tracks = trackRegex.findAll(res)

        for (track in tracks) {
            val fileUrl = track.groupValues[1].replace("\\/", "/")
            val label = track.groupValues[2]

            if (fileUrl.endsWith(".vtt") || fileUrl.endsWith(".srt")) {
                Log.d("Anoboye", "Subtitle found: $label → $fileUrl")

                subtitleCallback.invoke(
                    newSubtitleFile(label, fileUrl)
                )
            }
        }
    }
}



// =========================
// URL FIX HELPER
// =========================
fun Http(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else {
        url
    }
}