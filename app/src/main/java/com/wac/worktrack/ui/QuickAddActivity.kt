package com.wac.worktrack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Floating quick-add opened from the Quick Settings tile, without opening the whole app. */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        val voice = intent.getBooleanExtra(EXTRA_VOICE, false)
        setContent {
            WorkTrackTheme { QuickAddDialog(onDismiss = { finish() }, startWithVoice = voice) }
        }
    }

    companion object {
        const val EXTRA_VOICE = "voice"
    }
}
