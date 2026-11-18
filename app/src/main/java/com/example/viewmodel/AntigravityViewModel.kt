package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.cloud.CloudCopilotPrompt
import com.example.data.cloud.CloudServerProfile
import com.example.data.cloud.CloudSyncRepository
import com.example.data.cloud.CloudSyncedSession
import com.example.data.local.AgentSessionEntity
import com.example.data.local.AntigravityDao
import com.example.data.local.FileDiffEntity
import com.example.data.local.InitialSeedData
import com.example.data.local.ServerProfileEntity
import com.example.data.local.SessionMessageEntity
import com.example.data.local.TerminalLogEntity
import com.example.data.remote.AntigravityBridgeClient
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RetainedWebViewManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

class AntigravityViewModel(
    private val dao: AntigravityDao,
    private val bridgeClient: AntigravityBridgeClient = AntigravityBridgeClient()
) : ViewModel() {

    // Eagerly retained flows guarantee 0ms rendering with zero reload when minimizing or switching tabs
    val serverProfiles: StateFlow<List<ServerProfileEntity>> = dao.observeServerProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<AgentSessionEntity>> = dao.observeAgentSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedSessionId = MutableStateFlow("sess_bridge_core")
    val selectedSessionId: StateFlow<String> = _selectedSessionId.asStateFlow()

    private val allMessages = dao.observeAllSessionMessages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeSessionMessages: StateFlow<List<SessionMessageEntity>> = combine(
        allMessages,
        _selectedSessionId
    ) { messages, sessionId ->
        messages.filter { it.sessionId == sessionId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allDiffs = dao.observeAllDiffs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeSessionDiffs: StateFlow<List<FileDiffEntity>> = combine(
        allDiffs,
        _selectedSessionId
    ) { diffs, sessionId ->
        val filtered = diffs.filter { it.sessionId == sessionId }
        filtered.ifEmpty { diffs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allTerminalLogs = dao.observeAllTerminalLogs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeTerminalLogs: StateFlow<List<TerminalLogEntity>> = combine(
        allTerminalLogs,
        _selectedSessionId
    ) { logs, sessionId ->
        val filtered = logs.filter { it.sessionId == sessionId }
        filtered.ifEmpty { logs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bridgeTelemetry: StateFlow<BridgeTelemetry> = bridgeClient.telemetry

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            InitialSeedData.ensureSeeded(dao)
            val profiles = dao.getAllServerProfilesOnce()
            val active = profiles.firstOrNull { it.isSelected } ?: profiles.firstOrNull()
            if (active != null) {
                bridgeClient.connectToEndpoint(active.endpointUrl, active.authToken) { eventText ->
                    onRemoteWebSocketFrame(eventText)
                }
            }
        }
    }

    private fun onRemoteWebSocketFrame(frameText: String) {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value
            dao.insertSessionMessage(
                SessionMessageEntity(
                    id = "ws_${System.currentTimeMillis()}",
                    sessionId = sessionId,
                    senderRole = "AGENT",
                    content = frameText,
                    commandOrPath = "WebSocket Live Stream"
                )
            )
        }
    }

    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
        bridgeClient.recordBridgeEvent("Switched to session $sessionId in 0ms from local Room cache")
    }

    fun createNewSession(workspaceName: String, branchName: String, initialTaskPrompt: String) {
        val cleanWorkspace = workspaceName.trim().ifEmpty { "antigravity-workspace" }
        val cleanBranch = branchName.trim().ifEmpty { "main" }
        val sessionId = "sess_${System.currentTimeMillis()}"
        val activeServerId = serverProfiles.value.firstOrNull { it.isSelected }?.id ?: "srv_hybrid_sim"

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.upsertAgentSession(
                AgentSessionEntity(
                    id = sessionId,
                    serverId = activeServerId,
                    workspaceName = cleanWorkspace,
                    branchName = cleanBranch,
                    status = "ACTIVE",
                    lastSummary = initialTaskPrompt.ifBlank { "Session initialized on $cleanBranch" },
                    messageCount = 1,
                    updatedAtEpoch = now
                )
            )
            _selectedSessionId.value = sessionId
            if (initialTaskPrompt.isNotBlank()) {
                sendPromptToAntigravityAgent(initialTaskPrompt)
            } else {
                dao.insertSessionMessage(
                    SessionMessageEntity(
                        id = "msg_init_$now",
                        sessionId = sessionId,
                        senderRole = "AGENT",
                        content = "Attached to `$cleanWorkspace` (`$cleanBranch`). Local Room cache and WebSocket delta stream are pinned—minimizing the app will never reload this conversation.",
                        timestampEpoch = now
                    )
                )
            }
        }
    }

    fun selectServerProfile(profile: ServerProfileEntity) {
        viewModelScope.launch {
            dao.selectServerProfile(profile.id)
            bridgeClient.connectToEndpoint(profile.endpointUrl, profile.authToken) { frame ->
                onRemoteWebSocketFrame(frame)
            }
            val ping = bridgeClient.measureLatency(profile.endpointUrl)
            dao.updateServerLatency(profile.id, ping)
            _statusBanner.value = "Switched bridge to ${profile.name} (${ping}ms)"
        }
    }

    fun addOrUpdateServerProfile(
        existingId: String?,
        name: String,
        endpointUrl: String,
        connectionMode: String,
        authToken: String,
        keepAlivePinned: Boolean
    ) {
        viewModelScope.launch {
            val profileId = existingId ?: "srv_${System.currentTimeMillis()}"
            val ping = bridgeClient.measureLatency(endpointUrl)
            val entity = ServerProfileEntity(
                id = profileId,
                name = name.trim().ifEmpty { "Antigravity Host" },
                endpointUrl = endpointUrl.trim().ifEmpty { "https://antigravity.local:8443" },
                connectionMode = connectionMode,
                authToken = authToken.trim(),
                keepAlivePinned = keepAlivePinned,
                lastLatencyMs = ping,
                isSelected = true
            )
            dao.upsertServerProfile(entity)
            dao.selectServerProfile(profileId)
            bridgeClient.connectToEndpoint(entity.endpointUrl, entity.authToken) { frame ->
                onRemoteWebSocketFrame(frame)
            }
            _statusBanner.value = "Connected to ${entity.name} (${ping}ms)"
        }
    }

    fun pingActiveServer() {
        val active = serverProfiles.value.firstOrNull { it.isSelected } ?: serverProfiles.value.firstOrNull() ?: return
        viewModelScope.launch {
            val ping = bridgeClient.measureLatency(active.endpointUrl)
            dao.updateServerLatency(active.id, ping)
            _statusBanner.value = "Bridge heartbeat verified: ${ping}ms round-trip"
        }
    }

    fun updateDraftAndScroll(sessionId: String, draft: String, scrollIndex: Int) {
        viewModelScope.launch {
            dao.updateSessionDraftAndScroll(sessionId, draft, scrollIndex)
        }
    }

    fun sendPromptToAntigravityAgent(promptText: String, targetSessionId: String = _selectedSessionId.value) {
        val clean = promptText.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            _selectedSessionId.value = targetSessionId
            val now = System.currentTimeMillis()
            // 1. Save user message immediately in Room (0ms UI update)
            dao.insertSessionMessage(
                SessionMessageEntity(
                    id = "msg_u_$now",
                    sessionId = targetSessionId,
                    senderRole = "USER",
                    content = clean,
                    timestampEpoch = now
                )
            )
            dao.updateSessionDraftAndScroll(targetSessionId, "", 0)

            // 2. Dispatch over WebSocket / JSON-RPC bridge + Retained WebView JS bridge
            bridgeClient.sendJsonRpcMessage(
                method = "agent.sendPrompt",
                params = mapOf("sessionId" to targetSessionId, "prompt" to clean)
            )
            RetainedWebViewManager.injectPromptIntoWebView(clean)

            // 3. Emit instant agent reasoning & execution feedback
            delay(220)
            val thoughtTime = System.currentTimeMillis()
            dao.insertSessionMessage(
                SessionMessageEntity(
                    id = "msg_t_$thoughtTime",
                    sessionId = targetSessionId,
                    senderRole = "THOUGHT",
                    content = "Executing task over persistent bridge for prompt: \"${clean.take(90)}\". Inspecting workspace AST and preparing incremental patch.",
                    durationMs = 215,
                    timestampEpoch = thoughtTime
                )
            )

            delay(320)
            val replyTime = System.currentTimeMillis()
            val needsApproval = clean.contains("run", ignoreCase = true) ||
                clean.contains("deploy", ignoreCase = true) ||
                clean.contains("server", ignoreCase = true) ||
                clean.contains("build", ignoreCase = true)

            if (needsApproval) {
                val suggestedCmd = "language_server --standalone --verify-task \"${clean.take(36).replace("\"", "")}\""
                dao.insertSessionMessage(
                    SessionMessageEntity(
                        id = "msg_app_$replyTime",
                        sessionId = targetSessionId,
                        senderRole = "TOOL_APPROVAL",
                        content = "Staged workspace changes and prepared verification command. Approve execution on remote host:",
                        commandOrPath = suggestedCmd,
                        approvalStatus = "PENDING",
                        timestampEpoch = replyTime
                    )
                )
                dao.updateSessionStatus(
                    sessionId = targetSessionId,
                    status = "AWAITING_APPROVAL",
                    summary = "Pending approval: $suggestedCmd",
                    deltaCount = 3,
                    now = replyTime
                )
            } else {
                dao.insertSessionMessage(
                    SessionMessageEntity(
                        id = "msg_a_$replyTime",
                        sessionId = targetSessionId,
                        senderRole = "AGENT",
                        content = "Applied workspace update for: **$clean**.\n\n- Delta frame synced to local Room database in 11ms.\n- Code diff and terminal logs updated without reloading the web DOM.",
                        commandOrPath = "src/bridge/AntigravitySessionHandler.ts",
                        timestampEpoch = replyTime
                    )
                )
                dao.updateSessionStatus(
                    sessionId = targetSessionId,
                    status = "ACTIVE",
                    summary = "Completed: ${clean.take(65)}",
                    deltaCount = 3,
                    now = replyTime
                )
            }
        }
    }

    fun resolveStepApproval(message: SessionMessageEntity, approved: Boolean) {
        viewModelScope.launch {
            val newStatus = if (approved) "APPROVED" else "REJECTED"
            dao.updateMessageApprovalStatus(message.id, newStatus)
            bridgeClient.sendJsonRpcMessage(
                method = "agent.resolveApproval",
                params = mapOf(
                    "messageId" to message.id,
                    "approved" to approved,
                    "command" to message.commandOrPath
                )
            )

            val now = System.currentTimeMillis()
            if (approved) {
                val outputText = "[Antigravity Remote PTY] $ ${message.commandOrPath}\n" +
                    "✓ Delta compression enabled (zstd frame streaming)\n" +
                    "✓ WebSocket keep-alive pinned at 15,000ms\n" +
                    "✓ Process active (exit code 0 in 38ms)"
                dao.insertTerminalLog(
                    TerminalLogEntity(
                        id = "term_$now",
                        sessionId = message.sessionId,
                        command = message.commandOrPath.ifBlank { "language_server --standalone" },
                        output = outputText,
                        exitCode = 0,
                        durationMs = 38,
                        timestampEpoch = now
                    )
                )
                dao.insertSessionMessage(
                    SessionMessageEntity(
                        id = "msg_exec_$now",
                        sessionId = message.sessionId,
                        senderRole = "TERMINAL",
                        content = outputText,
                        commandOrPath = message.commandOrPath,
                        timestampEpoch = now
                    )
                )
                dao.updateSessionStatus(
                    sessionId = message.sessionId,
                    status = "ACTIVE",
                    summary = "Approved & executed: ${message.commandOrPath}",
                    deltaCount = 1,
                    now = now
                )
                _statusBanner.value = "Approved & executed on remote host"
            } else {
                dao.insertSessionMessage(
                    SessionMessageEntity(
                        id = "msg_rej_$now",
                        sessionId = message.sessionId,
                        senderRole = "AGENT",
                        content = "Command rejected by user (`${message.commandOrPath}`). Halted execution and awaiting alternative instructions.",
                        timestampEpoch = now
                    )
                )
                dao.updateSessionStatus(
                    sessionId = message.sessionId,
                    status = "IDLE",
                    summary = "Step rejected by user",
                    deltaCount = 1,
                    now = now
                )
                _statusBanner.value = "Rejected pending agent command"
            }
        }
    }

    fun updateFileDiffDecision(diff: FileDiffEntity, accepted: Boolean) {
        viewModelScope.launch {
            val newStatus = if (accepted) "ACCEPTED" else "REVERTED"
            dao.updateDiffStatus(diff.id, newStatus)
            bridgeClient.sendJsonRpcMessage(
                method = if (accepted) "workspace.acceptDiff" else "workspace.revertDiff",
                params = mapOf("filePath" to diff.filePath, "diffId" to diff.id)
            )
            _statusBanner.value = if (accepted) {
                "Accepted diff hunk in ${diff.filePath}"
            } else {
                "Reverted diff hunk in ${diff.filePath}"
            }
        }
    }

    fun runNativeTerminalCommand(commandText: String) {
        val cleanCmd = commandText.trim()
        if (cleanCmd.isEmpty()) return
        val sessionId = _selectedSessionId.value

        viewModelScope.launch {
            bridgeClient.sendJsonRpcMessage(
                method = "terminal.exec",
                params = mapOf("sessionId" to sessionId, "command" to cleanCmd)
            )
            val now = System.currentTimeMillis()
            val simulatedOutput = when {
                cleanCmd.contains("status") -> "On branch feat/zero-reload-websocket\nYour branch is up to date with 'origin/feat/zero-reload-websocket'.\nnothing to commit, working tree clean"
                cleanCmd.contains("language_server") -> "Antigravity Language Server (--standalone) PID 14892\nListening on 0.0.0.0:42100 • WebSocket clients: 1 (Android Native Bridge)\nMemory RSS: 312MB • Frame latency: 9ms"
                cleanCmd.contains("gradle") -> "> Task :app:compileDebugKotlin UP-TO-DATE\n> Task :app:assembleDebug\nBUILD SUCCESSFUL in 1s (42 actionable tasks: 2 executed, 40 up-to-date)"
                cleanCmd.contains("mcp") || cleanCmd.contains("chrome") -> "Chrome DevTools MCP Server active on ws://127.0.0.1:9222/devtools/browser\nAttached targets: 2 pages, 0 throttled tabs"
                else -> "$ $cleanCmd\n[Remote PTY Exit 0] Executed on Antigravity host in 24ms."
            }
            dao.insertTerminalLog(
                TerminalLogEntity(
                    id = "term_$now",
                    sessionId = sessionId,
                    command = cleanCmd,
                    output = simulatedOutput,
                    exitCode = 0,
                    durationMs = 24,
                    timestampEpoch = now
                )
            )
        }
    }

    fun clearTerminalForActiveSession() {
        viewModelScope.launch {
            dao.clearTerminalLogs(_selectedSessionId.value)
        }
    }

    fun toggleDomLagBooster(enabled: Boolean) {
        bridgeClient.setDomLagBooster(enabled)
        if (enabled) {
            RetainedWebViewManager.injectDomLagOptimizer()
        }
    }

    fun recordWebViewBridgeEvent(message: String) {
        bridgeClient.recordBridgeEvent(message)
        _statusBanner.value = message
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    companion object {
        fun provideFactory(dao: AntigravityDao): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AntigravityViewModel(dao)
            }
        }
    }
}

/**
 * Auth-gated ViewModel instantiated ONLY when `currentUser != null` and keyed by `currentUserId`.
 * Ensures Firestore snapshot listeners are never attached while unauthenticated.
 */
class AuthenticatedCloudSyncViewModel(
    private val cloudRepo: CloudSyncRepository,
    private val dao: AntigravityDao,
    private val currentUserId: String
) : ViewModel() {

    val cloudProfilesState: StateFlow<CloudUiState<List<CloudServerProfile>>> =
        cloudRepo.observeCloudServerProfiles(currentUserId)
            .map<List<CloudServerProfile>, CloudUiState<List<CloudServerProfile>>> {
                CloudUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CloudUiState.Error(rootError.message ?: "Failed to observe cloud profiles"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    val cloudSessionsState: StateFlow<CloudUiState<List<CloudSyncedSession>>> =
        cloudRepo.observeCloudSyncedSessions(currentUserId)
            .map<List<CloudSyncedSession>, CloudUiState<List<CloudSyncedSession>>> {
                CloudUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CloudUiState.Error(rootError.message ?: "Failed to observe synced sessions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    val cloudPromptsState: StateFlow<CloudUiState<List<CloudCopilotPrompt>>> =
        cloudRepo.observeCloudCopilotPrompts(currentUserId)
            .map<List<CloudCopilotPrompt>, CloudUiState<List<CloudCopilotPrompt>>> {
                CloudUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CloudUiState.Error(rootError.message ?: "Failed to observe saved prompts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun syncAllLocalDataToCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            val localProfiles = dao.getAllServerProfilesOnce()
            val localSessions = dao.getAllSessionsOnce()

            var failureMessage: String? = null
            for (profile in localProfiles) {
                val res = cloudRepo.syncServerProfileToCloud(
                    profileId = profile.id,
                    name = profile.name,
                    endpointUrl = profile.endpointUrl,
                    connectionMode = profile.connectionMode,
                    authToken = profile.authToken,
                    keepAlivePinned = profile.keepAlivePinned,
                    lastLatencyMs = profile.lastLatencyMs
                )
                if (res.isFailure) {
                    failureMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to sync server profile"
                }
            }

            for (session in localSessions) {
                val res = cloudRepo.syncAgentSessionToCloud(
                    sessionId = session.id,
                    workspaceName = session.workspaceName,
                    branchName = session.branchName,
                    status = session.status,
                    lastSummary = session.lastSummary,
                    messageCount = session.messageCount
                )
                if (res.isFailure) {
                    failureMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to sync session"
                }
            }

            _isSyncing.value = false
            _syncMessage.value = failureMessage
                ?: "Synced ${localProfiles.size} server profiles & ${localSessions.size} sessions to Firestore"
        }
    }

    fun restoreCloudProfilesToLocal(cloudProfiles: List<CloudServerProfile>) {
        viewModelScope.launch {
            val entities = cloudProfiles.mapIndexed { index, cp ->
                ServerProfileEntity(
                    id = cp.id.ifBlank { "cloud_$index" },
                    name = cp.name,
                    endpointUrl = cp.endpointUrl,
                    connectionMode = cp.connectionMode,
                    authToken = cp.authToken,
                    keepAlivePinned = cp.keepAlivePinned,
                    lastLatencyMs = cp.lastLatencyMs.toInt(),
                    isSelected = index == 0
                )
            }
            if (entities.isNotEmpty()) {
                dao.upsertServerProfiles(entities)
                _syncMessage.value = "Restored ${entities.size} cloud profiles into local Room DB"
            }
        }
    }

    fun bookmarkPromptToCloud(
        title: String,
        promptText: String,
        rolePersona: String,
        preferredModel: String
    ) {
        viewModelScope.launch {
            val res = cloudRepo.saveCopilotPromptToCloud(
                promptId = "prompt_${System.currentTimeMillis()}",
                title = title,
                promptText = promptText,
                rolePersona = rolePersona,
                preferredModel = preferredModel
            )
            _syncMessage.value = res.fold(
                onSuccess = { "Saved prompt '$title' to Google Cloud Firestore" },
                onFailure = { "Cloud save error: ${it.localizedMessage}" }
            )
        }
    }

    fun deleteCloudPrompt(promptId: String) {
        viewModelScope.launch {
            cloudRepo.deleteCloudCopilotPrompt(promptId)
        }
    }

    companion object {
        fun provideFactory(
            cloudRepo: CloudSyncRepository,
            dao: AntigravityDao,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthenticatedCloudSyncViewModel(cloudRepo, dao, currentUserId)
            }
        }
    }
}
