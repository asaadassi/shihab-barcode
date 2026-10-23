package dev.fabik.bluetoothhid.shihab

import app.shihab.shared.locale.ShihabDates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Scan dates on screen: in Arabic the Levantine month names and 0-9 digits from the shared table
 * ("13 تشرين الأول 2026 14:05", the time isolated left to right); in English the phone's short format, as upstream.
 * Always the phone's own time zone, since a scan time is local to the person scanning.
 */
object BarcodeDates {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    private fun arabic() = Locale.getDefault().language == "ar"

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochMilli(millis)
        if (!arabic()) return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(zone).format(instant)
        return "${ShihabDates.longDate(millis / 1000, zone)} \u2066${time.withZone(zone).format(instant)}\u2069"
    }

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (!arabic()) return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(zone).format(Instant.ofEpochMilli(millis))
        return ShihabDates.longDate(millis / 1000, zone)
    }
}
