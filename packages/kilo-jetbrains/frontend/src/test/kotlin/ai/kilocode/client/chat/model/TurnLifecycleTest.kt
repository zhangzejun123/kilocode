package ai.kilocode.client.chat.model

import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.MessageErrorDto

class TurnLifecycleTest : SessionModelTestBase() {

    fun `test TurnOpen fires BusyChanged true`() {
        val (_, events) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flushEdt()

        assertTrue(events.any { it is SessionEvent.BusyChanged && it.busy })
        assertTrue(events.any { it is SessionEvent.StatusChanged && it.text == "Considering next steps..." })
    }

    fun `test TurnClose fires BusyChanged false and clears status`() {
        val (_, events) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flushEdt()
        emit(ChatEventDto.TurnClose("ses_test", "completed"))
        flushEdt()

        val last = events.filterIsInstance<SessionEvent.BusyChanged>().last()
        assertFalse(last.busy)
        val status = events.filterIsInstance<SessionEvent.StatusChanged>().last()
        assertNull(status.text)
    }

    fun `test Error fires Error event with message`() {
        val (_, events) = prompted()

        emit(ChatEventDto.Error("ses_test", MessageErrorDto(type = "APIError", message = "Bad Request")))
        flushEdt()

        val err = events.filterIsInstance<SessionEvent.Error>().firstOrNull()
        assertNotNull(err)
        assertEquals("Bad Request", err!!.message)
    }

    fun `test Error with null message falls back to type`() {
        val (_, events) = prompted()

        emit(ChatEventDto.Error("ses_test", MessageErrorDto(type = "timeout", message = null)))
        flushEdt()

        val err = events.filterIsInstance<SessionEvent.Error>().first()
        assertEquals("timeout", err.message)
    }
}
