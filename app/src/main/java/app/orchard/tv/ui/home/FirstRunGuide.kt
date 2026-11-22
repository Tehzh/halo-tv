package app.orchard.tv.ui.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView

/** Onboarding completion survives artwork changes and normal application version upgrades. */
object FirstRunGuideStore {
    fun shouldShow(context: Context) = HaloWelcomeView.shouldShow(context)
}

@Composable
fun FirstRunGuide(onFinished: () -> Unit) {
    BackHandler { /* The primary button explicitly completes this single-page welcome. */ }
    AndroidView(factory = { context -> HaloWelcomeView(context, onFinished) },
        modifier = Modifier.fillMaxSize())
}
