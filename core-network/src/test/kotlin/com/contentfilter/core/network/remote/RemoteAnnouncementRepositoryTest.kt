package com.contentfilter.core.network.remote

import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAnnouncementRepositoryTest {
    @Test
    fun `parses read state and defaults old payloads to unread`() {
        val payload =
            JSONArray(
                """
                [
                  {
                    "announcement_id":"00000000-0000-0000-0000-000000000001",
                    "title":"Nuevo",
                    "body":"Mensaje nuevo",
                    "created_at":"2026-07-19T20:00:00Z",
                    "is_read":true
                  },
                  {
                    "announcement_id":"00000000-0000-0000-0000-000000000002",
                    "title":"Anterior",
                    "body":"Mensaje anterior",
                    "created_at":"2026-07-19T19:00:00Z"
                  }
                ]
                """.trimIndent(),
            )

        val announcements = parseRemoteAnnouncements(payload)

        assertTrue(announcements.first().isRead)
        assertFalse(announcements.last().isRead)
    }
}
