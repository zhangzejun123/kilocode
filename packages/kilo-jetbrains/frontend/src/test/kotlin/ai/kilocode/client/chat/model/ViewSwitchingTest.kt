package ai.kilocode.client.chat.model

class ViewSwitchingTest : SessionModelTestBase() {

    fun `test first prompt shows messages view`() {
        val m = model()
        val events = collect(m)

        edt { m.prompt("hello") }
        flushEdt()

        assertTrue(events.any { it is SessionEvent.ViewChanged && it.show })
    }

    fun `test ViewChanged not fired twice`() {
        val m = model()
        val events = collect(m)

        edt { m.prompt("first") }
        flushEdt()
        edt { m.prompt("second") }
        flushEdt()

        assertEquals(1, events.count { it is SessionEvent.ViewChanged && it.show })
    }
}
