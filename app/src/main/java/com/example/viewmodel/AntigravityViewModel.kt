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
import com.example.data.local.ServerProfileEntity
import com.example.data.local.SessionMessageEntity
import com.example.data.local.TerminalLogEntity
import com.example.data.remote.AntigravityBridgeClient
import com.example.data.remote.ApprovalNotificationBus
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RemoteFileEntry
import com.example.data.remote.RetainedWebViewManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

class AntigravityViewModel(
    private val dao: AntigravityDao,
    private val bridgeClient: AntigravityBridgeClient = AntigravityBridgeClient()
) : ViewModel() {

    private val idCounter = AtomicLong(0L)
    private fun nextUniqueId(prefix: String): String {
        return "${prefix}_${System.currentTimeMillis()}_${idCounter.incrementAndGet()}"
    }

    val serverProfiles: StateFlow<List<ServerProfileEntity>> = dao.observeServerProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<AgentSessionEntity>> = dao.observeAgentSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedSessionId = MutableStateFlow("")
    val selectedSessionId: StateFlow<String> = _selectedSessionId.asStateFlow()

    private val allMessages = dao.observeAllSessionMessages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeSessionMessages: StateFlow<List<SessionMessageEntity>> = combine(
        allMessages,
        _selectedSessionId
    ) { messages, sessionId ->
        if (sessionId.isBlank()) messages else messages.filter { it.sessionId == sessionId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allDiffs = dao.observeAllDiffs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeSessionDiffs: StateFlow<List<FileDiffEntity>> = combine(
        allDiffs,
        _selectedSessionId
    ) { diffs, sessionId ->
        if (sessionId.isBlank()) diffs else diffs.filter { it.sessionId == sessionId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allTerminalLogs = dao.observeAllTerminalLogs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeTerminalLogs: StateFlow<List<TerminalLogEntity>> = combine(
        allTerminalLogs,
        _selectedSessionId
    ) { logs, sessionId ->
        if (sessionId.isBlank()) logs else logs.filter { it.sessionId == sessionId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bridgeTelemetry: StateFlow<BridgeTelemetry> = bridgeClient.telemetry

    private val _isExecutingCommand = MutableStateFlow(false)
    val isExecutingCommand: StateFlow<Boolean> = _isExecutingCommand.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Background Step Approval Notification events observed by MainActivity
    private val _pendingApprovalNotificationEvents = MutableSharedFlow<SessionMessageEntity>(extraBufferCapacity = 16)
    val pendingApprovalNotificationEvents: SharedFlow<SessionMessageEntity> =
        _pendingApprovalNotificationEvents.asSharedFlow()

    // Live Remote File Browser state
    private val _remoteBrowsePath = MutableStateFlow(".")
    val remoteBrowsePath: StateFlow<String> = _remoteBrowsePath.asStateFlow()

    private val _remoteFileEntries = MutableStateFlow<List<RemoteFileEntry>>(emptyList())
    val remoteFileEntries: StateFlow<List<RemoteFileEntry>> = _remoteFileEntries.asStateFlow()

    private val _previewFilePath = MutableStateFlow<String?>(null)
    val previewFilePath: StateFlow<String?> = _previewFilePath.asStateFlow()

    private val _previewFileContent = MutableStateFlow<String?>(null)
    val previewFileContent: StateFlow<String?> = _previewFileContent.asStateFlow()

    private val _isLoadingRemoteFiles = MutableStateFlow(false)
    val isLoadingRemoteFiles: StateFlow<Boolean> = _isLoadingRemoteFiles.asStateFlow()

    private val _remoteFileError = MutableStateFlow<String?>(null)
    val remoteFileError: StateFlow<String?> = _remoteFileError.asStateFlow()

    init {
        viewModelScope.launch {
            val profiles = dao.getAllServerProfilesOnce()
            val active = profiles.firstOrNull { it.isSelected } ?: profiles.firstOrNull()
            if (active != null) {
                bridgeClient.connectToEndpoint(active.endpointUrl, active.authToken) { eventText ->
                    onRemoteWebSocketFrame(eventText)
                }
            }
            val existingSessions = dao.getAllSessionsOnce()
            if (existingSessions.isNotEmpty() && _selectedSessionId.value.isBlank()) {
                _selectedSessionId.value = existingSessions.first().id
            }
        }

        // Listen for Approve & Run / Reject decisions tapped inside Android notification shade
        viewModelScope.launch {
            ApprovalNotificationBus.actions.collect { action ->
                resolveStepApprovalById(
                    messageId = action.messageId,
                    command = action.command,
                    approved = action.approved
                )
            }
        }
    }

    /**
     * Handles real-time frames arriving from the connected remote WebSocket bridge.
     * Supports JSON-RPC terminal output (`terminal.output` / `terminal.exec`), step approval gates
     * (`agent.requestApproval` / `TOOL_APPROVAL`), agent messages, and raw PTY output streams.
     */
    private fun onRemoteWebSocketFrame(frameText: String) {
        viewModelScope.launch {
            val sessionId = ensureActiveSessionExists()
            val now = System.currentTimeMillis()

            try {
                if (frameText.trimStart().startsWith("{")) {
                    val json = JSONObject(frameText)
                    val method = json.optString("method")
                    val params = json.optJSONObject("params") ?: json.optJSONObject("result")

                    // 1. Step Approval Request -> persist + trigger high-priority Android notification
                    if (method.contains("approval", ignoreCase = true) ||
                        params?.optString("role").equals("TOOL_APPROVAL", ignoreCase = true) ||
                        params?.optString("type").equals("TOOL_APPROVAL", ignoreCase = true)
                    ) {
                        val cmd = params?.optString("command").orEmpty()
                            .ifBlank { params?.optString("commandOrPath").orEmpty() }
                            .ifBlank { "remote command" }
                        val desc = params?.optString("content").orEmpty()
                            .ifBlank { params?.optString("description").orEmpty() }
                            .ifBlank { "Remote Antigravity agent requested approval to run a command." }
                        val approvalMsg = SessionMessageEntity(
                            id = nextUniqueId("appr"),
                            sessionId = sessionId,
                            senderRole = "TOOL_APPROVAL",
                            content = desc,
                            commandOrPath = cmd,
                            approvalStatus = "PENDING",
                            timestampEpoch = now
                        )
                        dao.insertSessionMessage(approvalMsg)
                        _pendingApprovalNotificationEvents.tryEmit(approvalMsg)
                        return@launch
                    }

                    // 2. Terminal Output Frame -> append to TerminalView LazyColumn
                    if (method.startsWith("terminal") || params?.has("output") == true || params?.has("stdout") == true) {
                        val cmd = params?.optString("command").orEmpty().ifBlank { "remote-pty-stream" }
                        val out = params?.optString("output").orEmpty()
                            .ifBlank { params?.optString("stdout").orEmpty() }
                            .ifBlank { frameText }
                        val code = params?.optInt("exitCode", 0) ?: 0
                        val duration = params?.optInt("durationMs", 0) ?: 0

                        dao.insertTerminalLog(
                            TerminalLogEntity(
                                id = nextUniqueId("term_ws"),
                                sessionId = sessionId,
                                command = cmd,
                                output = out,
                                exitCode = code,
                                durationMs = duration,
                                timestampEpoch = now
                            )
                        )
                        return@launch
                    }
                }
            } catch (_: Exception) {
                // Treat non-JSON frames as direct remote messages/stream events
            }

            dao.insertSessionMessage(
                SessionMessageEntity(
                    id = nextUniqueId("ws"),
                    sessionId = sessionId,
                    senderRole = "AGENT",
                    content = frameText,
                    commandOrPath = bridgeClient.telemetry.value.activeEndpoint
                )
            )
        }
    }

    private suspend fun ensureActiveSessionExists(): String {
        val currentId = _selectedSessionId.value
        if (currentId.isNotBlank()) return currentId

        val existing = dao.getAllSessionsOnce()
        if (existing.isNotEmpty()) {
            val firstId = existing.first().id
            _selectedSessionId.value = firstId
            return firstId
        }

        val now = System.currentTimeMillis()
        val newId = nextUniqueId("sess")
        val activeServerId = serverProfiles.value.firstOrNull { it.isSelected }?.id ?: ""
        val defaultWorkspaceName = serverProfiles.value.firstOrNull { it.isSelected }?.name
            ?: "Remote Workspace"

        dao.upsertAgentSession(
            AgentSessionEntity(
                id = newId,
                serverId = activeServerId,
                workspaceName = defaultWorkspaceName,
                branchName = "main",
                status = "ACTIVE",
                lastSummary = "Active remote session",
                messageCount = 0,
                updatedAtEpoch = now
            )
        )
        _selectedSessionId.value = newId
        return newId
    }

    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
        bridgeClient.recordBridgeEvent("Selected session $sessionId")
    }

    fun createNewSession(workspaceName: String, branchName: String, initialTaskPrompt: String) {
        val cleanWorkspace = workspaceName.trim().ifEmpty { "Remote Workspace" }
        val cleanBranch = branchName.trim().ifEmpty { "main" }
        val sessionId = nextUniqueId("sess")
        val activeServerId = serverProfiles.value.firstOrNull { it.isSelected }?.id ?: ""

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.upsertAgentSession(
                AgentSessionEntity(
                    id = sessionId,
                    serverId = activeServerId,
                    workspaceName = cleanWorkspace,
                    branchName = cleanBranch,
                    status = "ACTIVE",
                    lastSummary = initialTaskPrompt.ifBlank { "Workspace $cleanWorkspace ($cleanBranch)" },
                    messageCount = 0,
                    updatedAtEpoch = now
                )
            )
            _selectedSessionId.value = sessionId
            if (initialTaskPrompt.isNotBlank()) {
                sendPromptToAntigravityAgent(initialTaskPrompt, sessionId)
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
            _statusBanner.value = if (ping > 0) {
                "Connected to ${profile.name} (${ping}ms)"
            } else {
                "Selected ${profile.name} (${profile.endpointUrl})"
            }
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
        val cleanUrl = endpointUrl.trim()
        if (cleanUrl.isEmpty()) return

        viewModelScope.launch {
            val profileId = existingId ?: nextUniqueId("srv")
            val ping = bridgeClient.measureLatency(cleanUrl)
            val entity = ServerProfileEntity(
                id = profileId,
                name = name.trim().ifEmpty { "Antigravity Host" },
                endpointUrl = cleanUrl,
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
            ensureActiveSessionExists()
            _statusBanner.value = if (ping > 0) {
                "Connected to ${entity.name} (${ping}ms)"
            } else {
                "Saved host ${entity.name} (${entity.endpointUrl})"
            }
        }
    }

    fun pingActiveServer() {
        val active = serverProfiles.value.firstOrNull { it.isSelected }
            ?: serverProfiles.value.firstOrNull()
        if (active == null) {
            _statusBanner.value = "No remote host configured yet. Add your Antigravity URL first."
            return
        }
        viewModelScope.launch {
            val ping = bridgeClient.measureLatency(active.endpointUrl)
            dao.updateServerLatency(active.id, ping)
            _statusBanner.value = if (ping > 0) {
                "Remote host responded: ${ping}ms round-trip"
            } else {
                "Ping completed for ${active.endpointUrl}"
            }
        }
    }

    fun updateDraftAndScroll(sessionId: String, draft: String, scrollIndex: Int) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            dao.updateSessionDraftAndScroll(sessionId, draft, scrollIndex)
        }
    }

    fun sendPromptToAntigravityAgent(promptText: String, targetSessionId: String = _selectedSessionId.value) {
        val clean = promptText.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            val sessionId = if (targetSessionId.isNotBlank()) targetSessionId else ensureActiveSessionExists()
            _selectedSessionId.value = sessionId
            val now = System.currentTimeMillis()

            dao.insertSessionMessage(
                SessionMessageEntity(
                    id = nextUniqueId("msg_u"),
                    sessionId = sessionId,
                    senderRole = "USER",
                    content = clean,
                    timestampEpoch = now
                )
            )
            dao.updateSessionDraftAndScroll(sessionId, "", 0)

            val sentOverWs = bridgeClient.sendJsonRpcMessage(
                method = "agent.sendPrompt",
                params = mapOf("sessionId" to sessionId, "prompt" to clean)
            )
            RetainedWebViewManager.injectPromptIntoWebView(clean)

            dao.updateSessionStatus(
                sessionId = sessionId,
                status = if (sentOverWs) "ACTIVE" else "QUEUED",
                summary = clean.take(80),
                deltaCount = 1,
                now = now
            )

            if (!sentOverWs) {
                val activeUrl = bridgeClient.telemetry.value.activeEndpoint
                val notice = if (activeUrl.isBlank()) {
                    "No remote host connected yet. Tap the top session dropdown -> 'Configure Remote Host' (or use the Web Bridge tab) to connect your live Antigravity URL."
                } else {
                    "Dispatched prompt to pinned Web Bridge ($activeUrl)."
                }
                _statusBanner.value = notice
            }
        }
    }

    fun resolveStepApproval(message: SessionMessageEntity, approved: Boolean) {
        resolveStepApprovalById(message.id, message.commandOrPath, approved)
    }

    fun resolveStepApprovalById(messageId: String, command: String, approved: Boolean) {
        viewModelScope.launch {
            val newStatus = if (approved) "APPROVED" else "REJECTED"
            dao.updateMessageApprovalStatus(messageId, newStatus)
            bridgeClient.sendJsonRpcMessage(
                method = "agent.resolveApproval",
                params = mapOf(
                    "messageId" to messageId,
                    "approved" to approved,
                    "command" to command
                )
            )
            _statusBanner.value = if (approved) {
                "Approved step: $command"
            } else {
                "Rejected step: $command"
            }
            if (approved && command.isNotBlank()) {
                runNativeTerminalCommand(command)
            }
        }
    }

    fun triggerTestApprovalNotification() {
        viewModelScope.launch {
            val sessionId = ensureActiveSessionExists()
            val testMsg = SessionMessageEntity(
                id = nextUniqueId("appr_test"),
                sessionId = sessionId,
                senderRole = "TOOL_APPROVAL",
                content = "Test approval gate — approve or reject right from the Android notification shade.",
                commandOrPath = "git status --short",
                approvalStatus = "PENDING",
                timestampEpoch = System.currentTimeMillis()
            )
            dao.insertSessionMessage(testMsg)
            _pendingApprovalNotificationEvents.tryEmit(testMsg)
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

    /**
     * Executes a shell command over the remote bridge (`AntigravityBridgeClient.executeShellCommandOverBridge`)
     * and appends the real-time execution output to the scrollable `TerminalView` log stream.
     */
    fun runNativeTerminalCommand(commandText: String) {
        val cleanCmd = commandText.trim()
        if (cleanCmd.isEmpty()) return

        viewModelScope.launch {
            _isExecutingCommand.value = true
            try {
                val sessionId = ensureActiveSessionExists()
                val result = bridgeClient.executeShellCommandOverBridge(
                    sessionId = sessionId,
                    command = cleanCmd
                )
                val now = System.currentTimeMillis()
                dao.insertTerminalLog(
                    TerminalLogEntity(
                        id = nextUniqueId("term"),
                        sessionId = sessionId,
                        command = result.command,
                        output = result.output,
                        exitCode = result.exitCode,
                        durationMs = result.durationMs.toInt(),
                        timestampEpoch = now
                    )
                )
            } finally {
                _isExecutingCommand.value = false
            }
        }
    }

    fun sendTerminalCtrlC() {
        viewModelScope.launch {
            val sessionId = ensureActiveSessionExists()
            bridgeClient.sendTerminalInterrupt(sessionId)
            _statusBanner.value = "Sent SIGINT (^C) to remote PTY"
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        bridgeClient.setAutoReconnectEnabled(enabled)
    }

    fun browseRemoteDirectory(path: String) {
        val targetPath = path.trim().ifBlank { "." }
        _remoteBrowsePath.value = targetPath
        _previewFilePath.value = null
        _previewFileContent.value = null
        viewModelScope.launch {
            _isLoadingRemoteFiles.value = true
            _remoteFileError.value = null
            val res = bridgeClient.fetchRemoteDirectory(targetPath)
            res.fold(
                onSuccess = { entries ->
                    _remoteFileEntries.value = entries
                    if (entries.isEmpty()) {
                        _remoteFileError.value = "Directory \"$targetPath\" is empty or remote bridge did not return workspace.listFiles entries."
                    }
                },
                onFailure = { err ->
                    _remoteFileEntries.value = emptyList()
                    _remoteFileError.value = err.localizedMessage ?: "Failed to list remote directory"
                }
            )
            _isLoadingRemoteFiles.value = false
        }
    }

    fun openRemoteFile(path: String) {
        val targetPath = path.trim()
        if (targetPath.isBlank()) return
        viewModelScope.launch {
            _isLoadingRemoteFiles.value = true
            _remoteFileError.value = null
            val res = bridgeClient.fetchRemoteFileContent(targetPath)
            res.fold(
                onSuccess = { content ->
                    _previewFilePath.value = targetPath
                    _previewFileContent.value = content
                },
                onFailure = { err ->
                    _remoteFileError.value = err.localizedMessage ?: "Failed to read remote file"
                }
            )
            _isLoadingRemoteFiles.value = false
        }
    }

    fun closeRemoteFilePreview() {
        _previewFilePath.value = null
        _previewFileContent.value = null
    }

    fun clearTerminalForActiveSession() {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value
            if (sessionId.isNotBlank()) {
                dao.clearTerminalLogs(sessionId)
            } else {
                dao.clearAllTerminalLogs()
            }
        }
    }

    fun clearAllLocalWorkspaceData() {
        viewModelScope.launch {
            dao.clearAllTerminalLogs()
            dao.clearAllFileDiffs()
            dao.clearAllSessionMessages()
            dao.clearAllAgentSessions()
            dao.clearAllServerProfiles()
            dao.clearCopilotMessages()
            _selectedSessionId.value = ""
            _remoteFileEntries.value = emptyList()
            _previewFilePath.value = null
            _previewFileContent.value = null
            bridgeClient.connectToEndpoint("", "") {}
            _statusBanner.value = "Cleared all local workspace data"
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
