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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = if (page == 1) {
    "$mainUrl/${request.data}"
} else {
    "$mainUrl/${request.data}/page/$page"
}
    Log.d("Anoboye", "Fetching URL: $url")

    val document = app.get(url).document

    val elements = document.select("div.listupd > article")
    Log.d("Anoboye", "Found articles: ${elements.size}")

    val home = elements.mapNotNull { it.toSearchResult() }

    Log.d("Anoboye", "Parsed items: ${home.size}")

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
    val link = this.selectFirst("div.bsx > a")
    if (link == null) {
        Log.d("Anoboye", "Link not found")
        return null
    }

    val title = link.attr("title")
        .ifEmpty { link.attr("oldtitle") }

    val href = fixUrl(link.attr("href"))

    Log.d("Anoboye", "Title: $title")
    Log.d("Anoboye", "Href: $href")

    if (title.isBlank() || href.isBlank()) {
        Log.d("Anoboye", "Skipping item due to empty title/href")
        return null
    }

    val img = link.selectFirst("img")
    val posterUrl = fixUrlNull(
        img?.attr("data-src")?.ifEmpty { img.attr("src") }
    )

    Log.d("Anoboye", "Poster: $posterUrl")

    return newMovieSearchResponse(title, href, TvType.Anime) {
        this.posterUrl = posterUrl
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