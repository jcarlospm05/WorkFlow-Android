package com.workflow.operaciones.ui

import androidx.compose.runtime.saveable.Saver
import com.workflow.core.DailyCodec
import kotlinx.serialization.encodeToString

/** Small private form drafts only. Reports and transfer passwords never enter saved UI state. */
internal inline fun <reified T> formSaver(): Saver<T?, String> = Saver(
    save = { value -> value?.let { DailyCodec.json.encodeToString(it) } ?: "" },
    restore = { encoded -> if (encoded.isBlank()) null else runCatching { DailyCodec.json.decodeFromString<T>(encoded) }.getOrNull() }
)
