package com.example.ec.presentation.support

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

fun OffsetDateTime?.toSystemLocalDateTime(): LocalDateTime? {
    return this?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
}
