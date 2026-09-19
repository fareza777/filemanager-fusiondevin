package app.sorta.files.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.sorta.files.AppContainer
import app.sorta.files.MainActivity
import app.sorta.files.R
import kotlinx.coroutines.launch

fun hasFileAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) {
        android.os.Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

@Composable
fun OnboardingGate(container: AppContainer, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasFileAccess(context)) }
    var skipped by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // re-check on resume
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) granted = hasFileAccess(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (granted || skipped) {
        content()
        return
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasFileAccess(context) }

    val pager = rememberPagerState(pageCount = { 2 })

    Column(Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                val (icon, title, body) = when (page) {
                    0 -> Triple(Icons.Outlined.Inbox,
                        stringResource(R.string.onboarding_p1_title),
                        stringResource(R.string.onboarding_p1_body))
                    else -> Triple(Icons.Outlined.Lock,
                        stringResource(R.string.onboarding_title),
                        stringResource(R.string.onboarding_body))
                }
                Icon(icon, null, modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
            }
        }

        // dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { i ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(
                    if (pager.currentPage == i) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            }
        }
        Spacer(Modifier.height(24.dp))

        if (pager.currentPage == 0) {
            Button(onClick = { scope.launch { pager.animateScrollToPage(1) } },
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_next))
            }
        } else {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        (context as? MainActivity)?.openAllFilesAccess()
                    } else {
                        launcher.launch(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboarding_grant)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { skipped = true }) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}
