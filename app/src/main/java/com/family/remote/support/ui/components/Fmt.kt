package com.family.remote.support.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.family.remote.support.R
import com.family.remote.support.ServerClock
import com.family.remote.support.ui.LangPrefBus
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ui/components/Fmt.kt — locale-honest time & number formatting
 * (FRONTEND_UI_PLAN_BN পর্ব ৫ — the timer table's rendering half).
 *
 * Digits follow the CHOSEN app locale's own numbering system through
 * [java.text.DecimalFormatSymbols] (bn → ০১২৩, ar → ٠١٢, ne → ०१२…, en →
 * 012): no hardcoded Bengali digits for non-Bengali locales, no Latin
 * digits forced onto Bengali readers.
 *
 * Every countdown compares against [ServerClock.now] — a phone with
 * "Automatic date & time" off must never show a live ask as already
 * expired (audit P7).
 */
// ═══════════════════════════════════════════════════════════════════

object Fmt {

    /** "1:47" / "১:৪৭" — the 2-minute-class countdown (consent, awaiting). */
    fun mmss(msLeft: Long, locale: Locale): String {
        val s = (msLeft.coerceAtLeast(0L) + 999) / 1000
        val m = s / 60
        val r = s % 60
        return localize("$m:" + r.toString().padStart(2, '0'), locale)
    }

    /** "1:59:33" — hour-class countdown (the 2-hour guest window). */
    fun hms(msLeft: Long, locale: Locale): String {
        val s = (msLeft.coerceAtLeast(0L) + 999) / 1000
        val h = s / 3600
        return if (h <= 0) {
            mmss(msLeft, locale)
        } else {
            val m = (s % 3600) / 60
            val r = s % 60
            localize(
                "$h:" + m.toString().padStart(2, '0') + ":" + r.toString().padStart(2, '0'),
                locale,
            )
        }
    }

    /** "9:15 AM" / "৯:১৫ AM" — a point in time, in the chosen locale. */
    fun clockTime(tsMillis: Long, locale: Locale): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(tsMillis))

    /** Map ASCII digits onto the locale's own zero digit (identity for en). */
    private fun localize(ascii: String, locale: Locale): String {
        val zero: Char
        try {
            zero = java.text.DecimalFormatSymbols.getInstance(locale).zeroDigit
        } catch (_: Exception) {
            return ascii
        }
        if (zero == '0') return ascii
        return buildString {
            for (c in ascii) {
                append(if (c in '0'..'9') (zero + (c - '0')) else c)
            }
        }
    }
}

/**
 * The chosen app locale as a [Locale] (live — language changes from the
 * Settings dialog recreate the activity and this re-resolves).
 */
@Composable
fun appLocale(): Locale {
    val tag by LangPrefBus.tag.collectAsState()
    return remember(tag) {
        Locale.forLanguageTag(tag.ifBlank { LangPrefBus.DEFAULT_TAG })
    }
}

/**
 * A per-block "now": a ticking flow collected ONLY inside the composable
 * that renders a live deadline (M8 ticking-block isolation — the rest of
 * the screen does not recompose every second). [intervalMillis] follows
 * the plan's timer table: 1 s for consent/session/QR, 30 s for guest
 * windows.
 */
@Composable
fun tickingNow(intervalMillis: Long = 1_000L): Long {
    val initial = remember { ServerClock.now() }
    val ticker = remember(intervalMillis) {
        flow {
            while (true) {
                emit(ServerClock.now())
                delay(intervalMillis)
            }
        }
    }
    val now by ticker.collectAsState(initial = initial)
    return now
}

/** "just now" / "12 min ago" / "3 h ago" / "2 d ago" (time_* strings). */
@Composable
fun relTime(tsMillis: Long, now: Long): String {
    val mins = ((now - tsMillis).coerceAtLeast(0L)) / 60_000L
    return when {
        mins < 1L -> stringResource(R.string.time_just_now)
        mins < 60L -> stringResource(R.string.time_min_ago, mins.toInt())
        mins < 1440L -> stringResource(R.string.time_hour_ago, (mins / 60).toInt())
        else -> stringResource(R.string.time_day_ago, (mins / 1440).toInt())
    }
}

/** Time-aware hero greeting resource (§92: 5–11 সকাল · 12–16 দুপুর · 17–19 সন্ধ্যা · else রাত). */
fun timeGreetingRes(hourOfDay: Int): Int = when (hourOfDay) {
    in 5..11 -> R.string.greet_morning
    in 12..16 -> R.string.greet_afternoon
    in 17..19 -> R.string.greet_evening
    else -> R.string.greet_night
}
