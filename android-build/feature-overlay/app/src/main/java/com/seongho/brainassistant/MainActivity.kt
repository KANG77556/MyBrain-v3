package com.seongho.brainassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.seongho.brainassistant.core.settings.UserSettings
import com.seongho.brainassistant.core.settings.resolveDarkTheme
import com.seongho.brainassistant.navigation.AppNavHost
import com.seongho.brainassistant.ui.theme.BrainAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as BrainAssistantApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = UserSettings())
            BrainAssistantTheme(darkTheme = settings.themeMode.resolveDarkTheme(androidx.compose.foundation.isSystemInDarkTheme())) {
                AppNavHost(container = container, activity = this)
            }
        }
    }
}
