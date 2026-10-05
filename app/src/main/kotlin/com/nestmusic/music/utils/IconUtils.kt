package com.nestmusic.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.nestmusic.music.constants.LauncherIcon

object IconUtils {
    private val launcherAliases = listOf(
        "MainActivityAlias",
        "MainActivityStatic",
        "MainActivityNight",
        "MainActivityAurora",
    )

    fun setIcon(context: Context, enabled: Boolean) {
        setLauncherIcon(context, LauncherIcon.SUNSET, enabled)
    }

    fun setLauncherIcon(context: Context, icon: LauncherIcon, dynamicIconEnabled: Boolean) {
        val activeAlias = when (icon) {
            LauncherIcon.SUNSET -> if (dynamicIconEnabled) "MainActivityAlias" else "MainActivityStatic"
            LauncherIcon.NIGHT -> "MainActivityNight"
            LauncherIcon.AURORA -> "MainActivityAurora"
        }
        val packageManager = context.packageManager

        launcherAliases.filterNot { it == activeAlias }.forEach { alias ->
            packageManager.setComponentEnabledSetting(
                ComponentName(context, "com.nestmusic.music.$alias"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        packageManager.setComponentEnabledSetting(
            ComponentName(context, "com.nestmusic.music.$activeAlias"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
