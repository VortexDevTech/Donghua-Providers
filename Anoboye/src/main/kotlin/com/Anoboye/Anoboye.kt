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

        val url = if (page == 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}/page/$page"
        }

        Log.d("Anoboye", "Fetching URL: $url")

        val document = app.get(url).document

        Log.d("Anoboye", "Final URL: ${document.location()}")

        val articles = document.select("div.listupd article.bs")

        Log.d("Anoboye", "Articles found: ${articles.size}")

        val elements = articles.mapNotNull { article ->

            val code = article.selectFirst("code")

            if (code != null) {
                val innerDoc = Jsoup.parse(code.html())
                innerDoc.selectFirst("div.bsx")
            } else {
                article.selectFirst("div.bsx") ?: article
            }
        }

        Log.d("Anoboye", "Parsed elements: ${elements.size}")

        val home = elements.mapNotNull { it.toSearchResult() }

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

        val url = if (page == 1) {
            "$mainUrl/?s=$query"
        } else {
            "$mainUrl/page/$page/?s=$query"
        }

        val document = app.get(url).document

        val articles = document.select("div.listupd article.bs")

        val elements = articles.mapNotNull { article ->

            val code = article.selectFirst("code")

            if (code != null) {
                val innerDoc = Jsoup.parse(code.html())
                innerDoc.selectFirst("div.bsx")
            } else {
                article.selectFirst("div.bsx") ?: article
            }
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

            newMovieLoadResponse(title, url, TvType.Anime, url) {
                this.posterUrl = poster
                this.plot = description
            }

        } else {

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
    // LOAD LINKS
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

                Log.d("Anoboye", "Raw URL: $fixedUrl")

                val finalUrl = if (fixedUrl.contains("dailyplayer.php")) {

                    val id = Regex("""id=([^&]+)""")
                        .find(fixedUrl)
                        ?.groupValues?.get(1)

                    val dmUrl = id?.let {
                        "https://www.dailymotion.com/embed/video/$it"
                    }

                    Log.d("Anoboye", "Converted: $dmUrl")
                    dmUrl ?: fixedUrl

                } else {
                    fixedUrl
                }

                loadExtractor(finalUrl, subtitleCallback, callback)

            } catch (e: Exception) {
                Log.d("Anoboye", "Error: ${e.message}")
            }
        }

        return true
    }
}

