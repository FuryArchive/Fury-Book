package com.furybook.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.furybook.android.ui.DublApp
import androidx.compose.runtime.remember
import com.furybook.android.data.AndroidThemeStore
import com.furybook.ui.theme.FuryThemedApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeStore = remember { AndroidThemeStore(this) }
            FuryThemedApp(themeStore) {
                DublApp()
            }
        }
    }
}
