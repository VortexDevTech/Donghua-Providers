package com.Anoboye

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin


@CloudstreamPlugin
class AnoboyeProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Anoboye())
         registerExtractorAPI(DarkPlayer())
        registerExtractorAPI(CustomDailymotion())
    }
}