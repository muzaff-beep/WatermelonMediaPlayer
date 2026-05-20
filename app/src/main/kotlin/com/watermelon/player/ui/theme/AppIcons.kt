package com.watermelon.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * Maps semantic icon names to the custom PNG drawable resources.
 * Asset Handover v1.0 — 39 custom icons in res/drawable/.
 */
object AppIcons {
    val play: Painter @Composable get() = painterResource(id = R.drawable.icon_01)
    val pause: Painter @Composable get() = painterResource(id = R.drawable.icon_02)
    val stop: Painter @Composable get() = painterResource(id = R.drawable.icon_03)
    val next: Painter @Composable get() = painterResource(id = R.drawable.icon_04)
    val previous: Painter @Composable get() = painterResource(id = R.drawable.icon_05)
    val fastForward: Painter @Composable get() = painterResource(id = R.drawable.icon_06)
    val rewind: Painter @Composable get() = painterResource(id = R.drawable.icon_07)
    val volumeUp: Painter @Composable get() = painterResource(id = R.drawable.icon_08)
    val volumeDown: Painter @Composable get() = painterResource(id = R.drawable.icon_09)
    val mute: Painter @Composable get() = painterResource(id = R.drawable.icon_10)
    val fullscreenEnter: Painter @Composable get() = painterResource(id = R.drawable.icon_11)
    val fullscreenExit: Painter @Composable get() = painterResource(id = R.drawable.icon_12)
    val subtitlesOn: Painter @Composable get() = painterResource(id = R.drawable.icon_13)
    val subtitlesOff: Painter @Composable get() = painterResource(id = R.drawable.icon_14)
    val settings: Painter @Composable get() = painterResource(id = R.drawable.icon_15)
    val home: Painter @Composable get() = painterResource(id = R.drawable.icon_16)
    val search: Painter @Composable get() = painterResource(id = R.drawable.icon_17)
    val favorite: Painter @Composable get() = painterResource(id = R.drawable.icon_18)
    val download: Painter @Composable get() = painterResource(id = R.drawable.icon_19)
    val delete: Painter @Composable get() = painterResource(id = R.drawable.icon_20)
    val share: Painter @Composable get() = painterResource(id = R.drawable.icon_21)
    val info: Painter @Composable get() = painterResource(id = R.drawable.icon_22)
    val lock: Painter @Composable get() = painterResource(id = R.drawable.icon_23)
    val unlock: Painter @Composable get() = painterResource(id = R.drawable.icon_24)
    val refresh: Painter @Composable get() = painterResource(id = R.drawable.icon_25)
    val cast: Painter @Composable get() = painterResource(id = R.drawable.icon_26)
    val network: Painter @Composable get() = painterResource(id = R.drawable.icon_27)
    val offline: Painter @Composable get() = painterResource(id = R.drawable.icon_28)
    val error: Painter @Composable get() = painterResource(id = R.drawable.icon_29)
    val success: Painter @Composable get() = painterResource(id = R.drawable.icon_30)
    val back: Painter @Composable get() = painterResource(id = R.drawable.icon_31)
    val more: Painter @Composable get() = painterResource(id = R.drawable.icon_32)
    val sort: Painter @Composable get() = painterResource(id = R.drawable.icon_33)
    val filter: Painter @Composable get() = painterResource(id = R.drawable.icon_34)
    val playlist: Painter @Composable get() = painterResource(id = R.drawable.icon_35)
    val addToPlaylist: Painter @Composable get() = painterResource(id = R.drawable.icon_36)
    val equalizer: Painter @Composable get() = painterResource(id = R.drawable.icon_37)
    val brightness: Painter @Composable get() = painterResource(id = R.drawable.icon_38)
    val contrast: Painter @Composable get() = painterResource(id = R.drawable.icon_39)
}