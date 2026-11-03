package com.jedain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jedain.app.ui.theme.JedaInTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JedaInTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FloatingBubbleService.setAppInForeground(true)
        val controller = FloatingBubbleController(this)
        if (controller.isEnabled() && PermissionHelper.hasOverlayPermission(this)) {
            FloatingBubbleService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        FloatingBubbleService.setAppInForeground(true)
        val controller = FloatingBubbleController(this)
        if (controller.isEnabled() && PermissionHelper.hasOverlayPermission(this)) {
            FloatingBubbleService.start(this)
        }
        DailyAwarenessWidgetProvider.updateAllWidgets(this)
    }

    override fun onStop() {
        super.onStop()
        FloatingBubbleService.setAppInForeground(false)
    }
}
