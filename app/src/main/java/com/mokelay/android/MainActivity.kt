package com.mokelay.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mokelay.android.ui.MokelayApp
import com.mokelay.android.ui.theme.MokelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MokelayTheme {
                MokelayApp()
            }
        }
    }
}
