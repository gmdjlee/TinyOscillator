package com.tinyoscillator.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tinyoscillator.core.worker.KEY_MESSAGE
import com.tinyoscillator.core.worker.KEY_PROGRESS
import kotlinx.coroutines.delay

@Composable
fun CollectionProgressBar(
    tag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(tag)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val runningInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }

    var lastMessage by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(runningInfo) {
        if (runningInfo != null) {
            visible = true
        } else if (visible) {
            delay(3000)
            visible = false
            lastMessage = null
        }
    }

    if (runningInfo != null) {
        val msg = runningInfo.progress.getString(KEY_MESSAGE)
        if (!msg.isNullOrBlank()) lastMessage = msg
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (runningInfo != null) {
                val progress = runningInfo.progress.getFloat(KEY_PROGRESS, 0f)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            lastMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}
