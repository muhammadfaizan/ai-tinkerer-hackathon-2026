package com.example.intune

import com.example.intune.data.ActivityPayload
import com.example.intune.data.NudgeRequest
import com.example.intune.data.SessionAppPayload
import com.example.intune.data.SessionPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class NudgeRequestTest {
    @Test fun requestKeepsTheBackendPayloadFields() {
        val request = NudgeRequest(listOf("Read more"), ActivityPayload("Instagram", 25, "night"))
        assertEquals("Instagram", request.activity?.app)
        assertEquals(25, request.activity?.durationMin)
    }

    @Test fun sessionRequestLeavesDemoActivityAbsent() {
        val request = NudgeRequest(
            goals = listOf("Read more"),
            session = SessionPayload(listOf(SessionAppPayload("Instagram", 12)), 31),
        )
        assertEquals(null, request.activity)
        assertEquals(31, request.session?.totalDurationMin)
    }
}
