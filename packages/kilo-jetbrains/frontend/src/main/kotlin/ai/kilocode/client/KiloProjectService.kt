@file:Suppress("UnstableApiUsage")

package ai.kilocode.client

import ai.kilocode.rpc.KiloWorkspaceRpcApi
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Project-level frontend service that provides reactive access
 * to project-scoped data (providers, agents, commands, skills)
 * and resolves the real project directory from the backend.
 */
@Service(Service.Level.PROJECT)
class KiloProjectService internal constructor(
  private val project: Project,
  private val cs: CoroutineScope,
  private val rpc: KiloWorkspaceRpcApi?,
) {
    /** Platform constructor — resolves RPC from the service container. */
    constructor(project: Project, cs: CoroutineScope) : this(project, cs, null)

    companion object {
        private val LOG = Logger.getInstance(KiloProjectService::class.java)
        private val init = KiloWorkspaceStateDto(KiloWorkspaceStatusDto.PENDING)
    }

    private val hint: String get() = project.basePath ?: ""

    internal val _directory = MutableStateFlow("")

    /** The real project directory as resolved by the backend. */
    val directory: StateFlow<String> = _directory.asStateFlow()

    // ------ RPC helpers ------

    private suspend fun <T> call(block: suspend KiloWorkspaceRpcApi.() -> T): T {
        val api = rpc
        return if (api != null) block(api) else durable { block(KiloWorkspaceRpcApi.getInstance()) }
    }

    private fun <T> stream(block: suspend KiloWorkspaceRpcApi.() -> Flow<T>): Flow<T> = flow {
        val api = rpc
        if (api != null) block(api).collect { emit(it) }
        else durable { block(KiloWorkspaceRpcApi.getInstance()).collect { emit(it) } }
    }

    // ------ Init ------

    init {
        cs.launch {
            try {
                val resolved = call { directory(hint) }
                LOG.info("Resolved project directory: hint=$hint → resolved=$resolved")
                _directory.value = resolved
            } catch (e: Exception) {
                LOG.warn("Failed to resolve project directory, falling back to hint=$hint", e)
                _directory.value = hint
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<KiloWorkspaceStateDto> = _directory
        .flatMapLatest { dir ->
            if (dir.isEmpty()) return@flatMapLatest flowOf(init)
            stream { state(dir) }
        }
        .stateIn(cs, SharingStarted.Eagerly, init)

    /** Trigger a full reload of all project data. */
    fun reload() {
        cs.launch {
            val dir = _directory.value
            if (dir.isEmpty()) return@launch
            try {
                call { reload(dir) }
            } catch (e: Exception) {
                LOG.warn("project data reload failed", e)
            }
        }
    }
}
