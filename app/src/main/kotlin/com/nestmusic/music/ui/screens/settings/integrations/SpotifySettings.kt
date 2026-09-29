/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.settings.integrations

import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.nestmusic.music.LocalPlayerAwareWindowInsets
import com.nestmusic.music.api.SpotifyCanvas
import com.nestmusic.music.R
import com.nestmusic.music.constants.SpotifyCanvasEnabledKey
import com.nestmusic.music.constants.SpotifySpDcKey
import com.nestmusic.music.ui.component.DefaultDialog
import com.nestmusic.music.ui.component.IconButton
import com.nestmusic.music.ui.component.Material3SettingsGroup
import com.nestmusic.music.ui.component.Material3SettingsItem
import com.nestmusic.music.ui.utils.backToMain
import kotlinx.coroutines.launch
import timber.log.Timber
import com.nestmusic.music.utils.rememberPreference

private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifySettings(
    navController: NavController
) {
    val (canvasEnabled, onCanvasEnabledChange) = rememberPreference(
        key = SpotifyCanvasEnabledKey,
        defaultValue = false
    )
    val (spDc, onSpDcChange) = rememberPreference(
        key = SpotifySpDcKey,
        defaultValue = ""
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSpDcDialog by rememberSaveable { mutableStateOf(false) }

    if (showSpDcDialog) {
        var tempSpDc by rememberSaveable { mutableStateOf(spDc) }

        DefaultDialog(
            onDismiss = { showSpDcDialog = false },
            title = { Text(stringResource(R.string.spotify_sp_dc)) },
            buttons = {
                TextButton(
                    onClick = {
                        val candidate = tempSpDc.trim()
                        if (candidate.isEmpty()) {
                            Toast.makeText(
                                context,
                                R.string.spotify_sp_dc_empty,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@TextButton
                        }
                        onSpDcChange(candidate)
                        showSpDcDialog = false
                        // A cookie can save perfectly and still be dead: Canvas
                        // then returns nothing for every song and nothing on
                        // screen says why. Check it against the real token
                        // exchange and report what Spotify actually said.
                        scope.launch {
                            val accepted = runCatching {
                                SpotifyCanvas.exchangeToken(candidate)
                            }.onFailure { Timber.tag("SpotifySettings").w(it, "cookie rejected") }
                                .isSuccess
                            Toast.makeText(
                                context,
                                if (accepted) {
                                    R.string.spotify_sp_dc_ok
                                } else {
                                    R.string.spotify_sp_dc_rejected
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
                TextButton(onClick = { showSpDcDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            content = {
                OutlinedTextField(
                    value = tempSpDc,
                    onValueChange = { tempSpDc = it },
                    label = { Text(stringResource(R.string.spotify_sp_dc)) },
                    supportingText = { Text(stringResource(R.string.spotify_sp_dc_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.options),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.spotify_canvas_title)) },
                    description = { Text(stringResource(R.string.spotify_canvas_desc)) },
                    trailingContent = {
                        Switch(
                            checked = canvasEnabled,
                            onCheckedChange = onCanvasEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (canvasEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onCanvasEnabledChange(!canvasEnabled) }
                ),
            )
        )

        Spacer(Modifier.height(8.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.account),
            items = listOf(
                if (spDc.isBlank()) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.login),
                        title = { Text(stringResource(R.string.spotify_log_in)) },
                        description = { Text(stringResource(R.string.spotify_log_in_desc)) },
                        onClick = {
                            // Not a WebView: accounts.spotify.com loads reCAPTCHA
                            // Enterprise, which calls requestStorageAccess() — an
                            // API the Android WebView cannot satisfy, so the page
                            // always ends up blank (Console: "requestStorageAccess:
                            // Permission denied"). The user's own browser can, so
                            // sign in there and paste the cookie below.
                            val intent = CustomTabsIntent.Builder().build().intent.apply {
                                data = SPOTIFY_LOGIN_URL.toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure { Timber.tag("SpotifySettings").w(it, "no browser") }
                        }
                    )
                } else {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.logout),
                        title = { Text(stringResource(R.string.spotify_logged_in)) },
                        description = { Text(stringResource(R.string.spotify_log_out_desc)) },
                        onClick = { onSpDcChange("") }
                    )
                },
                Material3SettingsItem(
                    icon = painterResource(R.drawable.token),
                    title = { Text(stringResource(R.string.spotify_sp_dc)) },
                    description = {
                        Text(
                            if (spDc.isBlank()) stringResource(R.string.none)
                            else stringResource(R.string.spotify_sp_dc_set)
                        )
                    },
                    onClick = { showSpDcDialog = true }
                ),
            )
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.spotify_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
