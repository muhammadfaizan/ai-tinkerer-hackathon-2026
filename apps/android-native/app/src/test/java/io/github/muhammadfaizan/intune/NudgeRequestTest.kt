package io.github.muhammadfaizan.intune

import io.github.muhammadfaizan.intune.data.ActivityPayload
import io.github.muhammadfaizan.intune.data.NudgeRequest
import io.github.muhammadfaizan.intune.data.SessionAppPayload
import io.github.muhammadfaizan.intune.data.SessionPayload
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
