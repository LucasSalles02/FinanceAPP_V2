package com.financeapp.nativeapp.presentation.theme

import android.content.Context

/** Preferência do dispositivo; sem dados financeiros nem identificação sensível. */
object ThemePreferences {
    private const val PREFS = "financeapp_visual_preferences"
    private const val DARK = "dark_theme"

    fun isDark(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(DARK, true)

    fun setDark(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(DARK, enabled).apply()
    }
}
