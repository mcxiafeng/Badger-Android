package top.mcxiafeng.badger

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        
        
        
        top.mcxiafeng.badger.platform.ActivityHost.activity = this

        
        DeepLinkBus.setPending(parseDeepLink(intent))

        setContent {
            AppTheme { App() }
        }
    }

    override fun onDestroy() {
        if (top.mcxiafeng.badger.platform.ActivityHost.activity === this) {
            top.mcxiafeng.badger.platform.ActivityHost.activity = null
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        val serverId = parseDeepLink(intent)
        if (serverId != null) {
            DeepLinkBus.emit(serverId)
        }
    }

    

    private fun parseDeepLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null

        
        if (uri.scheme != "badger" || uri.host != "persons") return null

        
        val serverId = uri.lastPathSegment ?: return null

        
        return try {
            java.util.UUID.fromString(serverId)
            serverId
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Invalid deep link UUID: $serverId")
            null
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AppTheme { App() }
}
