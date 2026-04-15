package ai.kilocode.client.chat.model

import ai.kilocode.rpc.dto.ChatEventDto

class StatusComputationTest : SessionModelTestBase() {

    fun `test status shows tool-specific text`() {
        val (_, events) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flushEdt()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flushEdt()

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "tool", tool = "bash")))
        flushEdt()

        val status = events.filterIsInstance<SessionEvent.StatusChanged>()
            .lastOrNull { it.text != null && it.text != "Considering next steps..." }
        assertNotNull(status)
        assertEquals("Running commands...", status!!.text)
    }

    fun `test PartUpdated after TurnClose does not fire StatusChanged`() {
        val (_, events) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flushEdt()
        emit(ChatEventDto.TurnOpen("ses_test"))
        flushEdt()
        emit(ChatEventDto.TurnClose("ses_test", "completed"))
        flushEdt()

        val before = events.filterIsInstance<SessionEvent.StatusChanged>().size

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "text", text = "late")))
        flushEdt()

        val after = events.filterIsInstance<SessionEvent.StatusChanged>().size
        assertEquals(before, after)
    }
}
