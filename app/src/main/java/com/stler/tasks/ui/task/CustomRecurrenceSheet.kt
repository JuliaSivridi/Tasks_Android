package com.stler.tasks.ui.task

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

// ── Internal enums — shared across the ui/task package ───────────────────────

internal enum class RRuleFreq(val label: String, val rruleCode: String) {
    DAILY("day", "DAILY"),
    WEEKLY("week", "WEEKLY"),
    MONTHLY("month", "MONTHLY"),
    YEARLY("year", "YEARLY"),
}

internal enum class EndsType { NEVER, ON_DATE, AFTER_COUNT }

internal data class MonthlyOption(
    val label    : String,
    val byDayStr : String,   // e.g. "4TU" or "-1TU", or "" for BYMONTHDAY
    val dayOfMonth: Int,     // used when byDayStr.isBlank()
)

// ── Shared constants ──────────────────────────────────────────────────────────

internal val DOW_CODES = mapOf(
    DayOfWeek.MONDAY    to "MO",
    DayOfWeek.TUESDAY   to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY  to "TH",
    DayOfWeek.FRIDAY    to "FR",
    DayOfWeek.SATURDAY  to "SA",
    DayOfWeek.SUNDAY    to "SU",
)

// ── RRULE builder — called from TaskFormSheet.submitEvent() ───────────────────

internal fun buildRRule(
    frequency    : RRuleFreq,
    interval     : Int,
    byDay        : Set<DayOfWeek>,
    monthlyOption: MonthlyOption?,
    ends         : EndsType,
    endDate      : LocalDate?,
    afterCount   : Int,
): String = buildString {
    append("RRULE:FREQ=${frequency.rruleCode}")
    if (interval > 1) append(";INTERVAL=$interval")
    when (frequency) {
        RRuleFreq.WEEKLY -> {
            if (byDay.isNotEmpty()) {
                val codes = byDay.sortedBy { it.value }.joinToString(",") { DOW_CODES[it]!! }
                append(";BYDAY=$codes")
            }
        }
        RRuleFreq.MONTHLY -> {
            monthlyOption?.let { opt ->
                if (opt.byDayStr.isNotBlank()) append(";BYDAY=${opt.byDayStr}")
                else                           append(";BYMONTHDAY=${opt.dayOfMonth}")
            }
        }
        else -> {}
    }
    when (ends) {
        EndsType.ON_DATE     -> endDate?.let {
            append(";UNTIL=${it.format(DateTimeFormatter.BASIC_ISO_DATE)}")
        }
        EndsType.AFTER_COUNT -> append(";COUNT=$afterCount")
        EndsType.NEVER       -> {}
    }
}

// ── RRULE parser ─────────────────────────────────────────────────────────────

/**
 * Parses a raw RRULE string (e.g. "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=10")
 * and returns a [ParsedRRule] with the components needed to pre-fill the form UI.
 * Returns null if the string is blank or unparseable.
 */
internal data class ParsedRRule(
    val freq        : RRuleFreq,
    val interval    : Int,
    val byDay       : Set<DayOfWeek>,
    val ends        : EndsType,
    val endDate     : LocalDate?,
    val afterCount  : Int,
)

private val DOW_REVERSE = DOW_CODES.entries.associate { (dow, code) -> code to dow }

internal fun parseRRule(raw: String): ParsedRRule? {
    if (raw.isBlank()) return null
    val params = raw.removePrefix("RRULE:").split(";").associate { part ->
        val i = part.indexOf('=')
        if (i < 0) part to "" else part.substring(0, i) to part.substring(i + 1)
    }

    val freq = when (params["FREQ"]) {
        "DAILY"   -> RRuleFreq.DAILY
        "WEEKLY"  -> RRuleFreq.WEEKLY
        "MONTHLY" -> RRuleFreq.MONTHLY
        "YEARLY"  -> RRuleFreq.YEARLY
        else      -> return null
    }

    val interval = params["INTERVAL"]?.toIntOrNull() ?: 1

    val byDay: Set<DayOfWeek> = params["BYDAY"]
        ?.split(",")
        ?.mapNotNull { code ->
            // strip ordinal prefix (e.g. "2TU" → "TU", "-1TU" → "TU")
            val cleaned = code.trimStart('-', '1', '2', '3', '4', '5')
            DOW_REVERSE[cleaned]
        }
        ?.toSet() ?: emptySet()

    val ends: EndsType
    val endDate: LocalDate?
    val afterCount: Int

    when {
        params.containsKey("COUNT") -> {
            ends       = EndsType.AFTER_COUNT
            endDate    = null
            afterCount = params["COUNT"]?.toIntOrNull() ?: 13
        }
        params.containsKey("UNTIL") -> {
            ends = EndsType.ON_DATE
            // UNTIL can be "20261231" (basic ISO) or "20261231T000000Z"
            endDate = runCatching {
                LocalDate.parse(params["UNTIL"]!!.take(8),
                    DateTimeFormatter.BASIC_ISO_DATE)
            }.getOrNull()
            afterCount = 13
        }
        else -> {
            ends       = EndsType.NEVER
            endDate    = null
            afterCount = 13
        }
    }

    return ParsedRRule(freq, interval, byDay, ends, endDate, afterCount)
}

// ── Monthly option computer ───────────────────────────────────────────────────

/** Returns the 2–3 monthly recurrence options available for a given [date]. */
internal fun monthlyOptions(date: LocalDate): List<MonthlyOption> {
    val dom     = date.dayOfMonth
    val dow     = date.dayOfWeek
    val dowCode = DOW_CODES[dow]!!
    val ordinal = ceil(dom / 7.0).toInt()
    val ordinalLabel = when (ordinal) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; else -> "${ordinal}th"
    }
    val dowName = dow.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }

    val options = mutableListOf(
        MonthlyOption(label = "Monthly on day $dom",  byDayStr = "",                  dayOfMonth = dom),
        MonthlyOption(label = "Monthly on the $ordinalLabel $dowName", byDayStr = "${ordinal}${dowCode}", dayOfMonth = dom),
    )

    // "Last" option: only when the same weekday cannot recur again in this month
    if (dom > 21 && date.plusWeeks(1).month != date.month) {
        options.add(MonthlyOption(
            label      = "Monthly on the last $dowName",
            byDayStr   = "-1${dowCode}",
            dayOfMonth = dom,
        ))
    }
    return options
}
