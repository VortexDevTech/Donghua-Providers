package com.Anoboye

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import com.lagradost.api.Log


class Anoboye : MainAPI() {
    override var mainUrl              = "https://anoboye.com"
    override var name                 = "Anoboye"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "donghua" to "Donghua",
        "animes" to "Anime",
        "movies" to "Movies",
    )

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

    // STEP 1: get all articles
    val articles = document.select("div.listupd article.bs")

    Log.d("Anoboye", "Articles found: ${articles.size}")

    // STEP 2: parse inside <code>
    val elements = articles.mapNotNull { article ->
        val innerHtml = article.selectFirst("code")?.html()
        if (innerHtml == null) {
            Log.d("Anoboye", "No inner HTML found")
            return@mapNotNull null
        }

        val innerDoc = org.jsoup.Jsoup.parse(innerHtml)
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

    private fun Element.toSearchResult(): SearchResponse? {
   
        val title = this.selectFirst("div.tt a")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null

        // Handle lazy loading
        val poster = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }

    
}


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/page/$page/?s=$query").document
        val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }.toNewSearchResponseList()
        return results
    }

    @Suppress("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href=document.selectFirst("div.eplister > ul > li a")?.attr("href") ?:""
        val poster = document.select("div.thumb img").attr("src").ifEmpty { document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString() }
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type=document.selectFirst(".spe")?.text().toString()
        val tvtag=if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val episodeRegex = Regex("(\\d+)")

            val episodes = document.select("div.eplister > ul > li").map { info ->
                val href1 = info.select("a").attr("href")
                val posterr = info.selectFirst("a img")?.attr("src") ?: ""

                val epText = info.selectFirst("div.epl-num")?.text().orEmpty()
                val epnum = episodeRegex.find(epText)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(href1) {
                    this.episode = epnum
                    this.name = epnum?.let { "Episode $it" } ?: epText
                    this.posterUrl = posterr
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select(".mobius option").forEach { server->
            val base64 = server.attr("value")
            val decoded=base64Decode(base64)
            val doc = Jsoup.parse(decoded)
            val href=doc.select("iframe").attr("src")
            val url=Http(href)
            loadExtractor(url,subtitleCallback, callback)
        }
        return true
    }
}