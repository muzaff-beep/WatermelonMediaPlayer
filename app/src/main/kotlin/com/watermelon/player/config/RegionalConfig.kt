// app/src/main/kotlin/com/watermelon/player/config/RegionalConfig.kt
// Regional configuration: language, subtitle defaults, RTL settings.

package com.watermelon.player.config

import java.util.Locale

/**
 * Provides regional configuration for the application.
 * Determines default language, subtitle encoding, and RTL layout direction.
 */
object RegionalConfig {

    /** Default subtitle language code (ISO 639-2). */
    val defaultSubtitleLanguage: String
        get() = "per"

    /** Default audio language preference. */
    val defaultAudioLanguage: String
        get() = "per"

    /** Whether the UI should default to RTL layout. */
    val isDefaultRtl: Boolean
        get() {
            val lang = Locale.getDefault().language
            return lang in setOf("fa", "ar", "he", "ur", "ps", "sd", "ku", "ug")
        }

    /** Default subtitle character encoding. */
    val subtitleEncoding: String
        get() = "UTF-8"

    /** Whether Vazirmatn should be the default font for Persian text. */
    val useVazirmatnAsDefault: Boolean
        get() = true
}