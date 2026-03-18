// Use the CloudStream gradle plugin
version = 1

cloudstream {
    // Extension metadata shown in the CloudStream app
    description = "SonyLIV – Indian streaming service with Movies & TV Shows"
    authors      = listOf("YourName")

    /**
     * Status
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta (may not work correctly)
     */
    status = 1
    tvTypes = listOf("TvSeries", "Movie")

    iconUrl = "https://play-lh.googleusercontent.com/LW-VJQLQB4A9aLs6SaZbsAc3V0Q0MKIU-MRjqNitLLnvS1D9eZ3ZDqMJ5X3bQ8suYg=s180"
    language = "hi"
    name     = "SonyLIV"
}
