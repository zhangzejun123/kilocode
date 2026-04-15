package ai.kilocode.client.chat.model

class WorkspaceWatchingTest : SessionModelTestBase() {

    fun `test workspace ready populates agents and models`() {
        val m = model()
        val events = collect(m)
        flushEdt()

        projectRpc.state.value = workspaceReady()
        flushEdt()

        assertEquals(1, m.chat.agents.size)
        assertEquals("code", m.chat.agents[0].name)
        assertEquals(1, m.chat.models.size)
        assertEquals("gpt-5", m.chat.models[0].id)
        assertTrue(m.chat.ready)
        assertTrue(events.any { it is SessionEvent.WorkspaceReady })
    }

    fun `test workspace ready sets default agent and model`() {
        val m = model()
        collect(m)
        flushEdt()

        projectRpc.state.value = workspaceReady()
        flushEdt()

        assertEquals("code", m.chat.agent)
        assertEquals("gpt-5", m.chat.model)
    }
}
