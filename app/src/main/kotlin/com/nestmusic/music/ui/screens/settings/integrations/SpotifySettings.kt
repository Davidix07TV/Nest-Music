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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nestmusic.music.BuildConfig
import com.nestmusic.music.api.SpotifyCanvas
import com.nestmusic.music.constants.SpotifyAccessTokenKey
import com.nestmusic.music.spotify.SpotifyAuth
import com.nestmusic.music.spotify.SpotifyOAuthActivity
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

private const val TAG = "SpotifySettings"

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
    val (accessToken, onAccessTokenChange) = rememberPreference(
        key = SpotifyAccessTokenKey,
        defaultValue = ""
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * Sign in with Spotify in the real browser and take the result back
     * through the redirect. Nothing is read out of the tab: its cookie store
     * belongs to the browser process and no API exposes it. What crosses back
     * is the authorization code, exchanged here for a token we own.
     */
    fun signIn() {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank()) {
            Toast.makeText(context, R.string.spotify_login_not_configured, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val pkce = SpotifyAuth.generatePkcePair()
            val state = SpotifyAuth.generateState()
            SpotifyOAuthActivity.newDeferred()
            val intent = CustomTabsIntent.Builder().build().intent.apply {
                data = SpotifyAuth.authorizeUrl(clientId, state, pkce.challenge).toUri()
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { context.startActivity(intent) }
            if (launched.isFailure) {
                Timber.tag(TAG).w(launched.exceptionOrNull(), "signIn: no browser available")
                Toast.makeText(context, R.string.spotify_login_no_browser, Toast.LENGTH_LONG).show()
                return@launch
            }

            val callback = runCatching { SpotifyOAuthActivity.awaitCallback() }
                .getOrElse {
                    Timber.tag(TAG).d("signIn: no redirect came back")
                    Toast.makeText(context, R.string.spotify_login_cancelled, Toast.LENGTH_LONG).show()
                    return@launch
                }
            if (callback.state != state) {
                Toast.makeText(context, R.string.spotify_login_state_mismatch, Toast.LENGTH_LONG).show()
                return@launch
            }
            val code = callback.code
            if (callback.error != null || code == null) {
                Toast.makeText(context, R.string.spotify_login_denied, Toast.LENGTH_LONG).show()
                return@launch
            }
            runCatching { SpotifyAuth.exchangeCode(clientId, code, pkce.verifier) }
                .onSuccess { token ->
                    onAccessTokenChange(token.accessToken)
                    Toast.makeText(context, R.string.spotify_login_ok, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Timber.tag(TAG).w(error, "signIn: token exchange failed")
                    Toast.makeText(context, R.string.spotify_login_failed, Toast.LENGTH_LONG).show()
                }
        }
    }

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
                if (accessToken.isBlank() && spDc.isBlank()) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.login),
                        title = { Text(stringResource(R.string.spotify_log_in)) },
                        description = { Text(stringResource(R.string.spotify_log_in_desc)) },
                        onClick = ::signIn
                    )
                } else {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.logout),
                        title = { Text(stringResource(R.string.spotify_logged_in)) },
                        description = { Text(stringResource(R.string.spotify_log_out_desc)) },
                        onClick = { onSpDcChange(""); onAccessTokenChange("") }
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
