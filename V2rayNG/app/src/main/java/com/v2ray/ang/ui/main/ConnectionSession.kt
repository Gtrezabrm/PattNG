package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/** What the home card knows of the running connection, kept in the settings so that it outlives the screen and its ViewModel. */
data class ConnectionFigures(val pingMillis: Long?, val ipAddress: String?, val countryCode: String?)

/**
 * The start of the connection (wall clock) and its last figures. Without it, a ViewModel that is created again while the
 * service keeps running would count the duration from zero and measure the ping and the address anew.
 */
object ConnectionSession {
    /** The monotonic start the stored wall-clock start stands for at [nowNanos]; never in the future. */
    internal fun nanosSince(storedWallMillis: Long, nowWallMillis: Long, nowNanos: Long): Long =
        nowNanos - (nowWallMillis - storedWallMillis).coerceAtLeast(0L) * NANOS_PER_MILLI

    /** The monotonic start of the running connection: the stored one, or [nowNanos] when none is stored (and it is stored now). */
    fun startedAtNanos(nowNanos: Long): Long = runCatching {
        val wall = System.currentTimeMillis()
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_HOME_CONNECTED_SINCE)?.toLongOrNull()
        if (stored != null && stored > 0) return@runCatching nanosSince(stored, wall, nowNanos)
        MmkvManager.encodeSettings(AppConfig.PREF_HOME_CONNECTED_SINCE, wall.toString())
        nowNanos
    }.getOrDefault(nowNanos)

    // The settings store is absent in plain JVM tests; a failed read or write then only means a fresh start.
    fun saveFigures(figures: ConnectionFigures) {
        runCatching { MmkvManager.encodeSettings(
            AppConfig.PREF_HOME_CONNECTION_FIGURES,
            listOf(figures.pingMillis?.toString().orEmpty(), figures.ipAddress.orEmpty(), figures.countryCode.orEmpty())
                .joinToString("|"),
        ) }
    }

    fun restoreFigures(): ConnectionFigures? = runCatching {
        val parts = MmkvManager.decodeSettingsString(AppConfig.PREF_HOME_CONNECTION_FIGURES)?.split("|")
        if (parts == null || parts.size != 3) return@runCatching null
        ConnectionFigures(
            pingMillis = parts[0].toLongOrNull(),
            ipAddress = parts[1].ifBlank { null },
            countryCode = parts[2].ifBlank { null },
        )
    }.getOrNull()

    fun clear() {
        runCatching {
            MmkvManager.encodeSettings(AppConfig.PREF_HOME_CONNECTED_SINCE, "")
            MmkvManager.encodeSettings(AppConfig.PREF_HOME_CONNECTION_FIGURES, "")
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
