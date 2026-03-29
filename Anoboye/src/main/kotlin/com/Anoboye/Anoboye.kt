package com.Anoboye

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Anoboye : MainAPI() {

    override var mainUrl = "https://anoboye.com"
    override var name = "Anoboye"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie)

    override val mainPage = mainPageOf(
        "donghua" to "Donghua",
        "animes" to "Anime",
        "movies" to "Movies",
    )

    // =========================
    // MAIN PAGE
    // =========================
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = "$mainUrl/${request.data}/page/$page"
        Log.d("Anoboye", "Fetching URL: $url")

        val document = app.get(url).document

        val articles = document.select("div.listupd article.bs")

        Log.d("Anoboye", "Articles found: ${articles.size}")

        val elements = articles.mapNotNull { article ->
            val innerHtml = article.selectFirst("code")?.html() ?: return@mapNotNull null
            val innerDoc = Jsoup.parse(innerHtml)
            innerDoc.selectFirst("div.bsx")
        }

        Log.d("Anoboye", "Parsed bsx elements: ${elements.size}")

        val home = elements.mapNotNull { it.toSearchResult() }

        Log.d("Anoboye", "Final results: ${home.size}")

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    // =========================
    // SEARCH
    // =========================
    override suspend fun search(query: String, page: Int): SearchResponseList {

        val url = "$mainUrl/page/$page/?s=$query"
        val document = app.get(url).document

        val articles = document.select("div.listupd article.bs")

        val elements = articles.mapNotNull { article ->
            val innerHtml = article.selectFirst("code")?.html() ?: return@mapNotNull null
            val innerDoc = Jsoup.parse(innerHtml)
            innerDoc.selectFirst("div.bsx")
        }

        val results = elements.mapNotNull { it.toSearchResult() }

        return results.toNewSearchResponseList()
    }

    // =========================
    // SEARCH RESULT PARSER
    // =========================
    private fun Element.toSearchResult(): SearchResponse? {

        val title = this.selectFirst("div.tt")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)

        val poster = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    // =========================
    // LOAD (Series + Episode)
    // =========================
    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "No Title"

        val poster = document.selectFirst("div.tb img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        } ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val description = document.selectFirst("div.entry-content")?.text()?.trim()

        val isEpisodePage = document.selectFirst("div.player-embed iframe") != null

        return if (isEpisodePage) {

            // EPISODE PAGE
            newMovieLoadResponse(title, url, TvType.Anime, url) {
                this.posterUrl = poster
                this.plot = description
            }

        } else {

            // SERIES PAGE
            val episodes = document.select("div.eplister ul li").mapNotNull {
                val epUrl = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null

                val epNum = it.selectFirst(".epl-num")
                    ?.text()
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull()

                newEpisode(fixUrl(epUrl)) {
                    this.episode = epNum
                    this.name = epNum?.let { "Episode $it" }
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // =========================
    // LOAD LINKS (SERVERS)
    // =========================
    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val document = app.get(data).document

    val servers = document.select("button.server-card")

    Log.d("Anoboye", "Servers found: ${servers.size}")

    servers.forEach { server ->

        val base64 = server.attr("data-value")
        if (base64.isNullOrEmpty()) return@forEach

        try {
            val decoded = base64Decode(base64)
            val doc = Jsoup.parse(decoded)

            val iframe = doc.selectFirst("iframe")?.attr("src") ?: return@forEach

            val fixedUrl = Http(iframe)

            Log.d("Anoboye", "Raw iframe: $fixedUrl")

            // =========================
            // DAILYPLAYER → DAILYMOTION
            // =========================
            if (fixedUrl.contains("dailyplayer.php")) {

                val id = Regex("""id=([^&]+)""")
                    .find(fixedUrl)
                    ?.groupValues?.get(1)

                if (id != null) {
                    val dmUrl = "https://www.dailymotion.com/embed/video/$id"

                    Log.d("Anoboye", "Dailymotion URL: $dmUrl")

                    loadExtractor(dmUrl, subtitleCallback, callback)
                }

            }
            // =========================
            // DARKPLAYER
            // =========================
            else if (fixedUrl.contains("darkplayer.php")) {

                Log.d("Anoboye", "DarkPlayer detected")

                val extractor = DarkPlayer()
                extractor.getUrl(fixedUrl, mainUrl)?.forEach {
                    callback(it)
                }

            }
            // =========================
            // FALLBACK
            // =========================
            else {
                Log.d("Anoboye", "Fallback extractor")

                loadExtractor(fixedUrl, subtitleCallback, callback)
            }

        } catch (e: Exception) {
            Log.d("Anoboye", "Error: ${e.message}")
        }
    }

    return true
}
}