package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.cloud.CloudSyncRepository
import com.example.data.cloud.attemptAutoSignIn
import com.example.data.cloud.authStateFlow
import com.example.data.cloud.onGoogleSignInClicked
import com.example.data.cloud.signOutFromGoogle
import com.example.data.local.AgentSessionEntity
import com.example.data.local.AntigravityDao
import com.example.data.local.ServerProfileEntity
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.NeuralViolet
import com.example.viewmodel.AuthenticatedCloudSyncViewModel
import com.example.viewmodel.CloudUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun StorageAndCloudSyncScreen(
    dao: AntigravityDao,
    localProfiles: List<ServerProfileEntity>,
    localSessions: List<AgentSessionEntity>
) {
    val context = LocalContext.current
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var authError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { authError = null },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Local On-Device Room SQLite Status Card (Always Active, Zero Latency)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldOnline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .testTag("local_room_storage_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Local Room Storage",
                            tint = EmeraldOnline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local On-Device Storage (Room SQLite)",
                            style = MaterialTheme.typography.titleLarge,
                            color = EmeraldOnline
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ACTIVE • 0ms Instant Cache (Private & Offline-Ready)",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cached locally: ${localProfiles.size} server profiles, ${localSessions.size} agent sessions, diffs, and terminal logs. Minimizing the app never evicts local state.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg
                    )
                }
            }
        }

        // 2. Cloud Sync Card (Gated behind CredentialManager Google Sign-In)
        item {
            val user = currentUser
            if (user == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, border, RoundedCornerShape(14.dp))
                        .testTag("cloud_auth_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Cloud Sync",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cross-Device Cloud Sync (Firebase Firestore)",
                                style = MaterialTheme.typography.titleLarge,
                                color = accent
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sign in with Google to back up your Antigravity server profiles, session bookmarks, and Copilot prompt library across all your Android devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )

                        if (authError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CrimsonError
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                isSigningIn = true
                                authError = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = { isSigningIn = false },
                                    onAuthError = { msg ->
                                        isSigningIn = false
                                        authError = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = { isSigningIn = false }
                                )
                            },
                            enabled = !isSigningIn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color(0xFF070708)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_sign_in_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF070708),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.sign_in_with_google),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Authenticated session: Key composition and ViewModel by user.uid
                key(user.uid) {
                    val cloudViewModel: AuthenticatedCloudSyncViewModel = viewModel(
                        key = user.uid,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                AuthenticatedCloudSyncViewModel(
                                    cloudRepo = CloudSyncRepository(db),
                                    dao = dao,
                                    currentUserId = user.uid
                                )
                            }
                        }
                    )
                    AuthenticatedCloudSyncPanel(
                        userEmail = user.email ?: user.uid,
                        surface = surface,
                        elevated = elevated,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        cloudViewModel = cloudViewModel,
                        onSignOut = {
                            signOutFromGoogle(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedCloudSyncPanel(
    userEmail: String,
    surface: Color,
    elevated: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    cloudViewModel: AuthenticatedCloudSyncViewModel,
    onSignOut: () -> Unit
) {
    val profilesState by cloudViewModel.cloudProfilesState.collectAsStateWithLifecycle()
    val sessionsState by cloudViewModel.cloudSessionsState.collectAsStateWithLifecycle()
    val promptsState by cloudViewModel.cloudPromptsState.collectAsStateWithLifecycle()
    val syncMessage by cloudViewModel.syncMessage.collectAsStateWithLifecycle()
    val isSyncing by cloudViewModel.isSyncing.collectAsStateWithLifecycle()

    Card(
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .testTag("authenticated_cloud_panel")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Signed in with Google",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Google Cloud Sync Connected",
                            style = MaterialTheme.typography.titleMedium,
                            color = accent
                        )
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryFg
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("google_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Sign Out",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign Out")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { cloudViewModel.syncAllLocalDataToCloud() },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_local_to_cloud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Backup Local to Cloud",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isSyncing) "Syncing..." else "Backup Local to Cloud",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (profilesState is CloudUiState.Success) {
                    val cloudProfiles = (profilesState as CloudUiState.Success).data
                    if (cloudProfiles.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { cloudViewModel.restoreCloudProfilesToLocal(cloudProfiles) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_cloud_to_local_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Restore to Local",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull Cloud (${cloudProfiles.size})")
                        }
                    }
                }
            }

            if (syncMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = syncMessage.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldOnline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cloud Profiles Snapshot
            when (val pState = profilesState) {
                is CloudUiState.Loading -> Text(
                    "Loading cloud server profiles...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryFg
                )
                is CloudUiState.Error -> Text(
                    "Cloud Profile Error: ${pState.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonError
                )
                is CloudUiState.Success -> {
                    Text(
                        text = "Synced Cloud Profiles (${pState.data.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = fg
                    )
                    pState.data.forEach { cp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .background(elevated, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${cp.name} (${cp.endpointUrl})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${cp.lastLatencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldOnline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cloud Sessions Snapshot
            when (val sState = sessionsState) {
                is CloudUiState.Loading -> Unit
                is CloudUiState.Error -> Text(
                    "Cloud Sessions Error: ${sState.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonError
                )
                is CloudUiState.Success -> {
                    Text(
                        text = "Synced Cloud Agent Sessions (${sState.data.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeuralViolet
                    )
                    sState.data.forEach { cs ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .background(elevated, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${cs.workspaceName} (${cs.branchName}) • ${cs.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = fg
                            )
                            Text(
                                text = cs.lastSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg
                            )
                        }
                    }
                }
            }

            // Saved Copilot Prompts
            if (promptsState is CloudUiState.Success) {
                val prompts = (promptsState as CloudUiState.Success).data
                if (prompts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Cloud Copilot Bookmarks (${prompts.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }
            }
        }
    }
}
