package com.watermelon.player.config

object RegionalConfig {
    const val TRIAL_DURATION_DAYS = Int.MAX_VALUE
    const val IS_PREMIUM = true
    const val PROXY_BEACON_URL = "https://example.com/beacon"
    const val PERSIAN_FONT = "Vazirmatn"
    const val URDU_FONT = "Jameel_Noori_Nastaliq"
    const val DEFAULT_FONT = "sans-serif"

    fun selectFont(languageHint: String?): String {
        return when (languageHint?.lowercase()) {
            "fa", "persian" -> PERSIAN_FONT
            "ur", "urdu" -> URDU_FONT
            else -> DEFAULT_FONT
        }
    }
}