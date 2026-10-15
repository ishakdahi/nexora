package com.nexora.app.data

import android.content.Context
import java.util.Locale

fun tr(lang: String, fr: String, ar: String): String = if (lang == "ar") ar else fr

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("nexora", Context.MODE_PRIVATE)

    /** Bouton d'arrêt général : aucune automatisation ne s'exécute. */
    var kill: Boolean
        get() = sp.getBoolean("kill", false)
        set(v) { sp.edit().putBoolean("kill", v).apply() }

    var lang: String
        get() = sp.getString("lang", null) ?: if (Locale.getDefault().language == "ar") "ar" else "fr"
        set(v) { sp.edit().putString("lang", v).apply() }

    /** system / light / dark */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) { sp.edit().putString("theme", v).apply() }
}
