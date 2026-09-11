package com.stler.tasks.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stler.tasks.auth.AuthData

@Composable
fun MenuScreen(
    authData             : AuthData,
    onNavigateToSettings : () -> Unit,
    onNavigateToHelp     : () -> Unit,
    onNavigateToFeedback : () -> Unit,
    onNavigateToAbout    : () -> Unit,
    onSignOut            : () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (authData.userAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model              = authData.userAvatarUrl,
                    contentDescription = authData.userName,
                    modifier           = Modifier.size(72.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector        = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier           = Modifier.size(72.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                if (authData.userName.isNotBlank()) {
                    Text(authData.userName, style = MaterialTheme.typography.titleMedium)
                }
                if (authData.userEmail.isNotBlank()) {
                    Text(
                        text  = authData.userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.padding(top = 8.dp))
        HorizontalDivider()

        MenuRow(Icons.Outlined.Settings,             "Settings",  onNavigateToSettings)
        MenuRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help",  onNavigateToHelp)
        MenuRow(Icons.Outlined.Feedback,             "Feedback",  onNavigateToFeedback)
        MenuRow(Icons.Outlined.Info,                 "About",     onNavigateToAbout)
        MenuRow(Icons.AutoMirrored.Outlined.Logout,  "Sign out",  onSignOut, isDestructive = true)
    }
}

@Composable
private fun MenuRow(
    icon         : ImageVector,
    label        : String,
    onClick      : () -> Unit,
    isDestructive: Boolean = false,
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = contentColor)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}
