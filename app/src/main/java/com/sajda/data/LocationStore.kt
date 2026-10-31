package com.sajda.data

import android.content.Context

data class UserLocation(
    val lat: Double,
    val lon: Double,
    val label: String,
    val source: Source,
    val fixTimeMillis: Long = System.currentTimeMillis()
) {
    enum class Source { GPS, MANUAL }

    fun isValid(): Boolean = lat.isFinite() && lon.isFinite() &&
        lat in -90.0..90.0 && lon in -180.0..180.0
}

/** Stores only the last useful fix locally. No network or background tracking. */
class LocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sajda_loc", Context.MODE_PRIVATE)

    fun save(loc: UserLocation) {
        if (!loc.isValid()) return
        prefs.edit()
            .putBoolean("has", true)
            .putString("lat", loc.lat.toString())
            .putString("lon", loc.lon.toString())
            .putString("label", loc.label)
            .putString("source", loc.source.name)
            .putLong("fix_time", loc.fixTimeMillis)
            .apply()
    }

    fun load(): UserLocation? {
        return try {
            if (!prefs.getBoolean("has", false)) null
            else {
                val lat = prefs.getString("lat", null)?.toDoubleOrNull()
                val lon = prefs.getString("lon", null)?.toDoubleOrNull()
                if (lat == null || lon == null) null
                else {
                    val fixTime = prefs.getLong("fix_time", 0L)
                    val location = UserLocation(
                        lat = lat,
                        lon = lon,
                        label = prefs.getString("label", "") ?: "",
                        source = try {
                            UserLocation.Source.valueOf(prefs.getString("source", "GPS") ?: "GPS")
                        } catch (_: Exception) {
                            UserLocation.Source.GPS
                        },
                        fixTimeMillis = fixTime
                    )
                    val age = System.currentTimeMillis() - location.fixTimeMillis
                    location.takeIf {
                        it.isValid() && fixTime > 0L && age in -120_000L..MAX_CACHED_AGE_MS
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val MAX_CACHED_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
