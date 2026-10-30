package dev.pridgey.beddery.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pridgey.beddery.BuildConfig
import dev.pridgey.beddery.R

/** The GitHub version can't use Google Play, so support goes through Ko-fi in the browser. */
@Composable
fun SupportRow() {
    val url = BuildConfig.KOFI_URL
    if (url.isBlank()) return
    val context = LocalContext.current
    ListItem(
        colors = itemColors,
        modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
        leadingContent = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(stringResource(R.string.support_kofi)) },
        supportingContent = { Text(stringResource(R.string.support_summary)) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
    )
}
