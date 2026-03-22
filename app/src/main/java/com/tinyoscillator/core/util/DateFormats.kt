package com.tinyoscillator.core.util

import java.time.format.DateTimeFormatter

/**
 * Shared DateTimeFormatter instances to avoid creating duplicate objects.
 */
object DateFormats {
    /** Format: "yyyyMMdd" (e.g., "20260322") */
    val yyyyMMdd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
}
