package com.wangzan101.mika.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.sources.NotificationAllowStore
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.RowChevron

@Composable
fun NotifListenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NotificationAllowStore(context) }
    var tick by remember { mutableIntStateOf(0) }
    val enabled = remember(tick) { NotificationAllowStore.listenerEnabled(context) }
    val allowed = remember(tick) { store.allowed() }
    val seen = remember(tick) { store.seen() }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    PageScaffold(stringResource(R.string.notif_listen_title), onBack) {
        GroupCard {
            ListRow(
                null,
                stringResource(R.string.notif_listen_grant),
                if (enabled) stringResource(R.string.notif_listen_grant_on) else stringResource(R.string.notif_listen_grant_sub),
                first = true,
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            ) { RowChevron() }
        }
        GroupLabel(stringResource(R.string.notif_listen_apps))
        if (!enabled) {
            GroupCard {
                ListRow(null, stringResource(R.string.notif_listen_off), first = true)
            }
        } else if (seen.isEmpty()) {
            GroupCard {
                ListRow(null, stringResource(R.string.notif_listen_empty), first = true)
            }
        } else {
            GroupCard {
                seen.forEachIndexed { i, (pkg, label) ->
                    val on = pkg in allowed
                    ListRow(null, label, pkg, first = i == 0) {
                        MikaSwitch(on, { store.setAllowed(pkg, it); tick++ }, Modifier.testTag("notif-allow-$pkg"))
                    }
                }
            }
        }
    }
}
