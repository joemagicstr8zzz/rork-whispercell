package com.joemagicstr8zzz.screenvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.joemagicstr8zzz.screenvault.data.ScreenVaultRepository
import com.joemagicstr8zzz.screenvault.ui.ScreenVaultApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ScreenVaultRepository(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2563EB),
                    secondary = Color(0xFF16A34A),
                    error = Color(0xFFDC2626),
                    background = Color(0xFFF7F9FC),
                    surface = Color.White,
                    onPrimary = Color.White,
                    onBackground = Color(0xFF111827),
                    onSurface = Color(0xFF111827)
                )
            ) {
                ScreenVaultApp(repository = repository)
            }
        }
    }
}
