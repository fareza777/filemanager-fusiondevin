package app.sorta.files.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.sorta.files.BuildConfig
import app.sorta.files.SortaApp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Real AdMob banner; renders nothing when ads are removed. */
@Composable
fun AdBanner() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as? SortaApp)?.container?.prefs ?: return
    val removed by prefs.adsRemoved.collectAsState(initial = false)
    if (removed) return
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
    )
}
