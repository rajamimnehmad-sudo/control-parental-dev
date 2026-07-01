package com.contentfilter.core.sync.engine

import java.time.Instant

internal fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()
