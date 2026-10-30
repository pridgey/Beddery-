package dev.pridgey.beddery.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable

// Google Play delivers updates for this build, so Beddery never checks or installs them itself.
// These match the GitHub build's update functions so shared screens can call either.

fun checkForUpdatesIfDue(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

@Composable
fun rememberUpdateBannerVisible(): Boolean = false

@Composable
fun UpdateBanner() = Unit

@Composable
fun UpdateRow() = Unit
