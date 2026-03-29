// Use an integer for version numbers
version = 2

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Donghua and Anime"
    authors = listOf("VortexDevTech")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("AnimeMovie","Anime","Cartoon")


    requiresResources = true
    language = "en"

 
    iconUrl = "https://i3.wp.com/anoboye.com/wp-content/uploads/2025/05/Anoboye-150x150.jpg"

    isCrossPlatform = true

}

