package com.contentfilter.core.domain.time

import java.time.ZoneId

object GloshTime {
    const val ArgentinaZoneId: String = "America/Argentina/Buenos_Aires"
    val ArgentinaZone: ZoneId = ZoneId.of(ArgentinaZoneId)
}
