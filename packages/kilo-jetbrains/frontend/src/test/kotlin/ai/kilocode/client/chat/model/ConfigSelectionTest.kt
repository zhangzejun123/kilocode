package ai.kilocode.client.chat.model

class ConfigSelectionTest : SessionModelTestBase() {

    fun `test selectModel updates ChatModel and calls updateConfig`() {
        val m = model()
        collect(m)
        flushEdt()

        edt { m.selectModel("kilo", "gpt-5") }
        flushEdt()

        assertEquals("kilo/gpt-5", m.chat.model)
        assertEquals(1, rpc.configs.size)
        assertEquals("kilo/gpt-5", rpc.configs[0].second.model)
    }

    fun `test selectAgent updates ChatModel and calls updateConfig`() {
        val m = model()
        collect(m)
        flushEdt()

        edt { m.selectAgent("plan") }
        flushEdt()

        assertEquals("plan", m.chat.agent)
        assertEquals(1, rpc.configs.size)
        assertEquals("plan", rpc.configs[0].second.agent)
    }

    fun `test selectModel fires WorkspaceReady event`() {
        val m = model()
        val events = collect(m)
        flushEdt()

        edt { m.selectModel("kilo", "gpt-5") }
        flushEdt()

        assertTrue(events.any { it is SessionEvent.WorkspaceReady })
    }
}
