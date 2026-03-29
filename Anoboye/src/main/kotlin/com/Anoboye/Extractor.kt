package com.Anoboye

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.jsoup.Jsoup


open class DarkPlayer : ExtractorApi() {
    override val name = "DarkPlayer"
    override val mainUrl = "https://anoboye.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {

        val res = app.get(url, referer = mainUrl).text

        // 🎯 Extract videoUrl
        val videoUrl = Regex("""videoUrl\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(res)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")

        // 🎯 Extract subtitles (tracks)
        val tracksRaw = Regex("""tracks\s*:\s*(\[.*?\])""", RegexOption.DOT_MATCHES_ALL)
            .find(res)
            ?.groupValues?.get(1)

        val subs = mutableListOf<SubtitleFile>()

        tracksRaw?.let {
            try {
                val json = tryParseJson<List<Map<String, Any>>>(it)
                json?.forEach { track ->
                    val file = (track["file"] as? String)?.replace("\\/", "/")
                    val label = track["label"] as? String ?: "Unknown"

                    if (!file.isNullOrEmpty()) {
                        subs.add(SubtitleFile(label, file))
                    }
                }
            } catch (_: Exception) {}
        }

        if (videoUrl != null) {
            return listOf(
                newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        return null
    }
}

fun Http(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else {
        url
    }
}