package com.credence.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.credence.mobile.ui.navigation.CredenceApp
import com.credence.mobile.ui.theme.CredenceTheme

/**
 * Single-activity entry point — everything else is Compose navigation
 * (see Navigation.kt's CredenceApp()). No other Activities exist in this
 * app; AndroidManifest.xml declares only this one.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CredenceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                ) {
                    CredenceApp()
                }
            }
        }
    }
}
