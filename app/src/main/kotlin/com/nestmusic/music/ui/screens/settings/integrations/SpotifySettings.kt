/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.settings.integrations

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nestmusic.music.LocalPlayerAwareWindowInsets
import com.nestmusic.music.R
import com.nestmusic.music.constants.SpotifyCanvasEnabledKey
import com.nestmusic.music.constants.SpotifySpDcKey
import com.nestmusic.music.ui.component.DefaultDialog
import com.nestmusic.music.ui.component.IconButton
import com.nestmusic.music.ui.component.Material3SettingsGroup
import com.nestmusic.music.ui.component.Material3SettingsItem
import com.nestmusic.music.ui.utils.backToMain
import com.nestmusic.music.utils.rememberPreference

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

    var showSpDcDialog by rememberSaveable { mutableStateOf(false) }

    if (showSpDcDialog) {
        var tempSpDc by rememberSaveable { mutableStateOf(spDc) }

        DefaultDialog(
            onDismiss = { showSpDcDialog = false },
            title = { Text(stringResource(R.string.spotify_sp_dc)) },
            buttons = {
                TextButton(
                    onClick = {
                        onSpDcChange(tempSpDc.trim())
                        showSpDcDialog = false
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
                            navController.navigate("settings/integrations/spotify_login")
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
