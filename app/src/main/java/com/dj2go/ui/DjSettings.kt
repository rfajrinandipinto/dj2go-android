package com.dj2go.ui

import android.content.Context
import com.dj2go.audio.CrossfaderCurve

/** How the waveform is coloured. */
enum class WaveColorMode(val label: String) {
    SPECTRUM("Spectrum"),
    MONO("Mono"),
    DECK("Deck")
}

/** User-adjustable appearance and mixer settings, persisted across launches. */
data class DjSettings(
    val secondsVisible: Float = 6f,
    val amplitudeScale: Float = 1f,
    val colorMode: WaveColorMode = WaveColorMode.SPECTRUM,
    val showBeatGrid: Boolean = true,
    val showOverview: Boolean = true,
    val showCueMarkers: Boolean = true,
    val showBarBeat: Boolean = true,
    val crossfaderCurve: CrossfaderCurve = CrossfaderCurve.SMOOTH,
    val tempoRange: Float = 0.10f,
    val quantize: Boolean = true,
    val quantizeToBar: Boolean = false,
    val deckAColor: Long = 0xFF2E9BFF,
    val deckBColor: Long = 0xFFFF7A2E
)

object SettingsStore {

    private const val PREFS = "dj2go_settings"

    val DECK_COLORS = listOf(
        0xFF2E9BFF, 0xFFFF7A2E, 0xFF34C759, 0xFFFFD400,
        0xFFE040FB, 0xFFFF3B30, 0xFF00E5FF, 0xFFB0B0C8
    )

    fun load(context: Context): DjSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = DjSettings()
        return DjSettings(
            secondsVisible = prefs.getFloat("secondsVisible", defaults.secondsVisible),
            amplitudeScale = prefs.getFloat("amplitudeScale", defaults.amplitudeScale),
            colorMode = enumOrDefault(
                prefs.getString("colorMode", null), defaults.colorMode
            ),
            showBeatGrid = prefs.getBoolean("showBeatGrid", defaults.showBeatGrid),
            showOverview = prefs.getBoolean("showOverview", defaults.showOverview),
            showCueMarkers = prefs.getBoolean("showCueMarkers", defaults.showCueMarkers),
            showBarBeat = prefs.getBoolean("showBarBeat", defaults.showBarBeat),
            crossfaderCurve = enumOrDefault(
                prefs.getString("crossfaderCurve", null), defaults.crossfaderCurve
            ),
            tempoRange = prefs.getFloat("tempoRange", defaults.tempoRange),
            quantize = prefs.getBoolean("quantize", defaults.quantize),
            quantizeToBar = prefs.getBoolean("quantizeToBar", defaults.quantizeToBar),
            deckAColor = prefs.getLong("deckAColor", defaults.deckAColor),
            deckBColor = prefs.getLong("deckBColor", defaults.deckBColor)
        )
    }

    fun save(context: Context, settings: DjSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("secondsVisible", settings.secondsVisible)
            .putFloat("amplitudeScale", settings.amplitudeScale)
            .putString("colorMode", settings.colorMode.name)
            .putBoolean("showBeatGrid", settings.showBeatGrid)
            .putBoolean("showOverview", settings.showOverview)
            .putBoolean("showCueMarkers", settings.showCueMarkers)
            .putBoolean("showBarBeat", settings.showBarBeat)
            .putString("crossfaderCurve", settings.crossfaderCurve.name)
            .putFloat("tempoRange", settings.tempoRange)
            .putBoolean("quantize", settings.quantize)
            .putBoolean("quantizeToBar", settings.quantizeToBar)
            .putLong("deckAColor", settings.deckAColor)
            .putLong("deckBColor", settings.deckBColor)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
