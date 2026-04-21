package com.stler.tasks.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.stler.tasks.R

/**
 * Shared widget header: bold title (tappable → opens [screenUri]) + "+" button.
 * [folderId] is forwarded to the create deeplink so the create sheet pre-selects this folder.
 * [screenUri] is the deeplink URI opened when the title is tapped
 *  (e.g. "stlertasks://upcoming", "stlertasks://folder/fld_xxx").
 *
 * Uses actionStartActivity(Intent) instead of actionRunCallback so the activity
 * launch goes through a proper PendingIntent rather than a BroadcastReceiver —
 * this reliably brings the app to the foreground even when it is backgrounded.
 */
@Composable
fun WidgetHeader(
    title: String,
    folderId: String = "",
    screenUri: String = "",
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon — tappable (same deeplink as title) when screenUri is provided
        if (screenUri.isNotBlank()) {
            val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(screenUri))
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
            Image(
                provider           = ImageProvider(R.mipmap.ic_launcher_round),
                contentDescription = "Open app",
                contentScale       = ContentScale.Fit,
                modifier           = GlanceModifier
                    .size(28.dp)
                    .clickable(actionStartActivity(openIntent)),
            )
            Spacer(GlanceModifier.width(8.dp))
        }

        // Title — tappable if screenUri provided
        val openAction = if (screenUri.isNotBlank())
            actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(screenUri))
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
            )
        else null

        val titleMod = if (openAction != null)
            GlanceModifier.defaultWeight().clickable(openAction)
        else
            GlanceModifier.defaultWeight()

        Text(
            text     = title,
            modifier = titleMod,
            style    = TextStyle(
                color      = GlanceTheme.colors.onSurface,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        // "+" button — opens create screen, optionally pre-selecting the folder
        val createUri = if (folderId.isNotBlank())
            "stlertasks://create?folderId=$folderId"
        else
            "stlertasks://create"

        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(createUri))
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP,
                            )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "+",
                style = TextStyle(
                    color      = GlanceTheme.colors.primary,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
