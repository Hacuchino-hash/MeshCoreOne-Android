// AndroidOnly: WP-002 Compose launcher only; no process/radio service graph or permission requests.
package com.meshcoreone.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.meshcoreone.android.core.designsystem.ScaffoldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScaffoldTheme {
                ScaffoldApp()
            }
        }
    }
}
