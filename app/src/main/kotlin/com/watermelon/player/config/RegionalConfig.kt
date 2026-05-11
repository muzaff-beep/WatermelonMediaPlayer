package com.watermelon.player.config

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import com.watermelon.player.R

/**
 * [INTEGRATED ADAPTABILITY BRAIN]
 * * DESIGN PHILOSOPHY: One APK, Multiple Environments.
 * This object centralizes all regional, business, and linguistic logic. 
 * By using this config, we avoid "Hard-Coding" values and ensure the app 
 * remains intelligent enough to adapt to Iranian network filtering or 
 * Urdu typography without needing separate app versions.
 */
object RegionalConfig {
    
    // --- BUSINESS LOGIC ---
    // Universal trial period for the singular Watermelon Player.
    const val TRIAL_DAYS = Int.MAX_VALUE
    // Global flag for premium features (Video-to-MP3, etc.)
    const val PREMIUM_ENABLED = true

    // --- NETWORK & ANTI-FILTERING (THE SHIELD) ---
    // This URL is the "Beacon." The app pings this to find fresh Proxy Nodes
    // if it detects it is behind a firewall (Iran/MENA).
    const val PROXY_BEACON_URL = "https://your-hidden-link.com/nodes.txt"
    
    // Used by GeoProbe to verify internet "freedom" status.
    const val GEO_CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204"

    // --- TYPOGRAPHY & LINGUISTIC ENGINE (THE CANVAS) ---
    // ENHANCEMENT: We use script-specific fonts to fix broken RTL rendering.
    // NOTE: Files must exist in app/src/main/res/font/
    
    // Vazirmatn: The gold standard for Persian/Arabic UI/Subtitles.
    val PersianFont = FontFamily(Font(R.font.vazir_regular, FontWeight.Normal))
    
    // Jameel Noori: Essential for Urdu Nastaliq (standard fonts ruin Urdu).
    val UrduFont = FontFamily(Font(R.font.jameel_noori, FontWeight.Normal))

    /**
     * [FUNCTION: getFontForLanguage]
     * * ENHANCEMENT: Script-Aware Injection.
     * This function is called by the SubtitleCanvas to dynamically swap 
     * the rendering engine's font.
     * * @param langCode ISO language code (e.g., "ur", "fa").
     * @return The optimized FontFamily for that specific script.
     */
    fun getFontForLanguage(langCode: String): FontFamily {
        return when (langCode) {
            "ur" -> UrduFont        // Urdu requires high-descender Nastaliq support.
            "fa", "ar" -> PersianFont // Farsi/Arabic needs clean, modern Sans-serif.
            else -> FontFamily.Default // English/Global uses system default (Roboto/Inter).
        }
    }
}

