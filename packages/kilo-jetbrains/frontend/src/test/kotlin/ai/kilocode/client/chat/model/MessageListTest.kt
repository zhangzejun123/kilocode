package ai.kilocode.client.chat.model

import ai.kilocode.rpc.dto.ChatEventDto

class MessageListTest : SessionModelTestBase() {

    fun `test MessageUpdated adds message to ChatModel`() {
        val (m, events) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flushEdt()

        assertTrue(events.any { it is SessionEvent.MessageAdded && it.id == "msg1" })
        assertNotNull(m.chat.message("msg1"))
    }

    fun `test PartUpdated text fires PartUpdated event`() {
        val (m, events) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flushEdt()

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "text", text = "hello")))
        flushEdt()

        assertTrue(events.any { it is SessionEvent.PartUpdated && it.messageId == "msg1" && it.partId == "prt1" })
    }

    fun `test PartDelta appends text to ChatModel`() {
        val (m, _) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flushEdt()

        emit(ChatEventDto.PartDelta("ses_test", "msg1", "prt1", "text", "hello "))
        emit(ChatEventDto.PartDelta("ses_test", "msg1", "prt1", "text", "world"))
        flushEdt()

        val p = m.chat.part("msg1", "prt1")
        assertNotNull(p)
        assertEquals("hello world", p!!.text.toString())
    }

    fun `test MessageRemoved removes from ChatModel`() {
        val (m, _) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "user")))
        flushEdt()
        assertNotNull(m.chat.message("msg1"))

        emit(ChatEventDto.MessageRemoved("ses_test", "msg1"))
        flushEdt()
        assertNull(m.chat.message("msg1"))
    }
}
