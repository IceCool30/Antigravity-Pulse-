package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.cloud.CloudCopilotPrompt
import com.example.data.cloud.CloudServerProfile
import com.example.data.cloud.CloudSyncRepository
import com.example.data.local.AntigravityDao
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.BridgeConnectionState
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RetainedWebViewManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

/**
 * Lean, high-performance ViewModel managing saved remote host profiles, WebView bridge state,
 * and zero-overhead local persistence without background polling storms.
 */
class AntigravityViewModel(
    private val dao: AntigravityDao
) : ViewModel() {

    private val idCounter = AtomicLong(0L)
    private fun nextUniqueId(prefix: String): String {
        return "${prefix}_${System.currentTimeMillis()}_${idCounter.incrementAndGet()}"
    }

    val serverProfiles: StateFlow<List<ServerProfileEntity>> = dao.observeServerProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _bridgeTelemetry = MutableStateFlow(BridgeTelemetry())
    val bridgeTelemetry: StateFlow<BridgeTelemetry> = _bridgeTelemetry.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            val profiles = dao.getAllServerProfilesOnce()
            val active = profiles.firstOrNull { it.isSelected } ?: profiles.firstOrNull()
            if (active != null && RetainedWebViewManager.isSafeHttpUrl(active.endpointUrl)) {
                _bridgeTelemetry.value = _bridgeTelemetry.value.copy(
                    state = BridgeConnectionState.CONNECTED_HTTP_BRIDGE,
                    activeEndpoint = active.endpointUrl.trim(),
                    latencyMs = active.lastLatencyMs
                )
            }
        }
    }

    fun selectServerProfile(profile: ServerProfileEntity) {
        viewModelScope.launch {
            val cleanUrl = profile.endpointUrl.trim()
            if (!RetainedWebViewManager.isSafeHttpUrl(cleanUrl)) {
                _statusBanner.value = "Blocked unsafe URL scheme. Only https:// or http:// hosts are allowed."
                return@launch
            }
            dao.selectServerProfile(profile.id)
            _bridgeTelemetry.value = _bridgeTelemetry.value.copy(
                state = BridgeConnectionState.CONNECTED_HTTP_BRIDGE,
                activeEndpoint = cleanUrl,
                latencyMs = profile.lastLatencyMs,
                lastBridgeEvent = "Connected to ${profile.name}"
            )
            _statusBanner.value = "Switched to ${profile.name}"
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
            val cleanUrl = endpointUrl.trim()
            if (!RetainedWebViewManager.isSafeHttpUrl(cleanUrl)) {
                _statusBanner.value = "Only https:// or http:// URLs are allowed."
                return@launch
            }
            val cleanName = name.trim().ifBlank { "Antigravity Remote" }
            val entityId = existingId ?: nextUniqueId("srv")
            val entity = ServerProfileEntity(
                id = entityId,
                name = cleanName,
                endpointUrl = cleanUrl,
                connectionMode = connectionMode,
                authToken = authToken.trim(),
                keepAlivePinned = keepAlivePinned,
                lastLatencyMs = 0,
                isSelected = true
            )
            dao.upsertServerProfile(entity)
            dao.selectServerProfile(entityId)
            _bridgeTelemetry.value = _bridgeTelemetry.value.copy(
                state = BridgeConnectionState.CONNECTED_HTTP_BRIDGE,
                activeEndpoint = cleanUrl,
                lastBridgeEvent = "Saved $cleanName"
            )
            _statusBanner.value = "Connected to $cleanName"
        }
    }

    fun toggleDomLagBooster(enabled: Boolean) {
        _bridgeTelemetry.value = _bridgeTelemetry.value.copy(
            domLagBoosterEnabled = enabled,
            lastBridgeEvent = if (enabled) "DOM Lag Booster ON" else "DOM Lag Booster OFF"
        )
        if (enabled) {
            RetainedWebViewManager.injectDomLagOptimizer()
        } else {
            RetainedWebViewManager.removeDomLagOptimizer()
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        _bridgeTelemetry.value = _bridgeTelemetry.value.copy(autoReconnectEnabled = enabled)
    }

    fun insertPromptIntoRemoteWebView(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty()) return
        RetainedWebViewManager.injectPromptIntoWebView(clean)
        _statusBanner.value = "Inserted prompt into Remote (and copied to clipboard)"
    }

    fun clearAllLocalWorkspaceData() {
        viewModelScope.launch {
            dao.clearAllServerProfiles()
            dao.clearAllAgentSessions()
            dao.clearAllSessionMessages()
            dao.clearAllFileDiffs()
            dao.clearAllTerminalLogs()
            dao.clearCopilotMessages()
            _bridgeTelemetry.value = BridgeTelemetry(
                domLagBoosterEnabled = _bridgeTelemetry.value.domLagBoosterEnabled,
                autoReconnectEnabled = _bridgeTelemetry.value.autoReconnectEnabled
            )
            _statusBanner.value = "Cleared all local saved hosts and browsing cache"
        }
    }

    fun recordWebViewBridgeEvent(message: String) {
        _bridgeTelemetry.value = _bridgeTelemetry.value.copy(lastBridgeEvent = message)
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

            var failureMessage: String? = null
            for (profile in localProfiles) {
                val res = cloudRepo.syncServerProfileToCloud(
                    profileId = profile.id,
                    name = profile.name,
                    endpointUrl = profile.endpointUrl,
                    connectionMode = profile.connectionMode,
                    keepAlivePinned = profile.keepAlivePinned,
                    lastLatencyMs = profile.lastLatencyMs
                )
                if (res.isFailure) {
                    failureMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to sync host"
                }
            }

            _isSyncing.value = false
            _syncMessage.value = failureMessage
                ?: if (localProfiles.isEmpty()) {
                    "No local remote hosts yet — scan a QR code or paste a Remote URL first, then tap Backup."
                } else {
                    "Backed up ${localProfiles.size} host(s) securely to Cloud Sync (workstation tokens kept local)"
                }
        }
    }

    fun restoreCloudProfilesToLocal(cloudProfiles: List<CloudServerProfile>) {
        viewModelScope.launch {
            val safeProfiles = cloudProfiles.filter { RetainedWebViewManager.isSafeHttpUrl(it.endpointUrl) }
            val entities = safeProfiles.mapIndexed { index, cp ->
                ServerProfileEntity(
                    id = cp.id.ifBlank { "cloud_$index" },
                    name = cp.name,
                    endpointUrl = cp.endpointUrl.trim(),
                    connectionMode = cp.connectionMode,
                    authToken = "",
                    keepAlivePinned = cp.keepAlivePinned,
                    lastLatencyMs = cp.lastLatencyMs.toInt(),
                    isSelected = index == 0
                )
            }
            if (entities.isNotEmpty()) {
                dao.upsertServerProfiles(entities)
                _syncMessage.value = "Restored ${entities.size} cloud host(s) to this device"
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
                onSuccess = { "Saved prompt '$title' to Cloud Sync" },
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
