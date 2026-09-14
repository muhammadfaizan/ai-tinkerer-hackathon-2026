package com.example.intune

import com.example.intune.data.ActivityPayload
import com.example.intune.data.NudgeRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class NudgeRequestTest {
    @Test fun requestKeepsTheBackendPayloadFields() {
        val request = NudgeRequest(listOf("Read more"), ActivityPayload("Instagram", 25, "night"))
        assertEquals("Instagram", request.activity.app)
        assertEquals(25, request.activity.durationMin)
    }
}
