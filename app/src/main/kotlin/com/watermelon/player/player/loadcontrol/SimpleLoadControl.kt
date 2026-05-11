package com.watermelon.player.player.loadcontrol

import androidx.media3.exoplayer.DefaultLoadControl

class SimpleLoadControl : DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        30_000, // min buffer
        60_000, // max buffer
        5_000,  // playback start buffer
        2_000   // rebuffer
    )
    .build()