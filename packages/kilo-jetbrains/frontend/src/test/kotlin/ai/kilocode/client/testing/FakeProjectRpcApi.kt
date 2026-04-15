package ai.kilocode.client.testing

import ai.kilocode.rpc.KiloProjectRpcApi
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [KiloProjectRpcApi] for testing.
 *
 * Push workspace state changes via [state].
 * Directory resolution returns [directory].
 *
 * Every `suspend` method asserts it is NOT called on the EDT.
 */
class FakeProjectRpcApi : KiloProjectRpcApi {

    var directory = "/test"
    val state = MutableStateFlow(KiloWorkspaceStateDto(KiloWorkspaceStatusDto.PENDING))

    override suspend fun directory(hint: String): String {
        assertNotEdt("directory")
        return directory
    }

    override suspend fun state(directory: String): Flow<KiloWorkspaceStateDto> {
        assertNotEdt("state")
        return state
    }

    override suspend fun reload(directory: String) {
        assertNotEdt("reload")
    }
}
