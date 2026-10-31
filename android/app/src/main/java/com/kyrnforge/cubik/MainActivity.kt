package com.kyrnforge.cubik

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema transparentes con iconos oscuros sobre el fondo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        val quick = QuickStore(applicationContext)
        setContent {
            Box {
                if (quick.quick) QuickApp(quick) else CubikScreen(store) { quick.switchQuick(true) }
                UpdateCheck()
            }
        }
    }
}
