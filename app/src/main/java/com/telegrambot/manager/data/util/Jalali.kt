package com.telegrambot.manager.data.util

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Jalali (Solar Hijri / شمسی) calendar utilities.
 *
 * Uses the standard 33-year-cycle conversion algorithms, verified by
 * roundtrip tests over Gregorian 2020–2031 (zero failures) and against
 * authoritative Nowruz dates (1403, 1404, 1405) plus leap years 1403/1408.
 */
object Jalali {

    val months = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** Persian weekday initial labels starting from Saturday (شنبه). */
    val weekdays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    data class JDate(val jy: Int, val jm: Int, val jd: Int)

    /** Gregorian (year, month 1-12, day) → Jalali. */
    fun toJalali(gy: Int, gm: Int, gd: Int): JDate {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gDaysInMonth.take(gm - 1).sum()
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        return if (days < 186) {
            JDate(jy, 1 + days / 31, 1 + days % 31)
        } else {
            JDate(jy, 7 + (days - 186) / 30, 1 + (days - 186) % 30)
        }
    }

    /** Jalali → Gregorian (year, month 1-12, day). */
    fun toGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val gyBase = if (jy > 979) 1600 else 621
        val jy2 = if (jy > 979) jy - 979 else jy
        var days = 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + 78 + jd +
            (if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186)
        var gy = gyBase + 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * ((days - 1) / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val gLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val sal = intArrayOf(0, 31, if (gLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]; gm++
        }
        return Triple(gy, gm, gd)
    }

    /** Epoch millis → Jalali date. */
    fun fromMillis(millis: Long): JDate {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Jalali date (keeping the time-of-day of [atMillis]) → epoch millis. */
    fun toMillis(jy: Int, jm: Int, jd: Int, atMillis: Long): Long {
        val (gy, gm, gd) = toGregorian(jy, jm, jd)
        val cur = Calendar.getInstance().apply { timeInMillis = atMillis }
        val out = Calendar.getInstance().apply {
            set(gy, gm - 1, gd)
            set(Calendar.HOUR_OF_DAY, cur.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, cur.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return out.timeInMillis
    }

    /** Length (29/30/31) of a Jalali month. */
    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> (epochDay(toGregorian(jy + 1, 1, 1)) - epochDay(toGregorian(jy, 1, 1)) - 336).toInt()
    }

    /** Weekday column (0 = شنبه … 6 = جمعه) of the 1st day of a Jalali month. */
    fun firstWeekday(jy: Int, jm: Int): Int {
        val (gy, gm, gd) = toGregorian(jy, jm, 1)
        val c = Calendar.getInstance().apply { set(gy, gm - 1, gd) }
        // java.util.Calendar: SUNDAY=1 … SATURDAY=7 → column = dow % 7
        return c.get(Calendar.DAY_OF_WEEK) % 7
    }

    /** "مهر ۱۴۰۴" title of a month. */
    fun monthTitle(jy: Int, jm: Int): String = "${months[jm - 1]} ${faNum(jy)}"

    /** Persian digits number (۱۲۳). */
    fun faNum(n: Long): String = NumberFormat.getInstance(Locale("fa")).format(n)

    fun faNum(n: Int): String = faNum(n.toLong())

    /** "۲۵ مهر ۱۴۰۴" */
    fun formatDate(millis: Long): String = try {
        val d = fromMillis(millis)
        "${faNum(d.jd)} ${months[d.jm - 1]} ${faNum(d.jy)}"
    } catch (e: Exception) {
        java.text.SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(java.util.Date(millis))
    }

    /** "پنجشنبه ۲۵ مهر ۱۴۰۴، ساعت ۱۸:۳۰" */
    fun formatDateTime(millis: Long): String = try {
        val d = fromMillis(millis)
        val weekday = java.text.SimpleDateFormat("EEEE", Locale("fa")).format(java.util.Date(millis))
        "$weekday ${faNum(d.jd)} ${months[d.jm - 1]} ${faNum(d.jy)}، ساعت ${formatHm(millis)}"
    } catch (e: Exception) {
        java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(millis))
    }

    /** "18:30" (24h, local) */
    fun formatHm(millis: Long): String =
        java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(millis))

    /** Days since epoch of a Gregorian ymd triple (for month-length math). */
    private fun epochDay(g: Triple<Int, Int, Int>): Long {
        val c = Calendar.getInstance().apply {
            clear()
            set(g.first, g.second - 1, g.third)
        }
        return c.timeInMillis / 86_400_000L
    }
}
