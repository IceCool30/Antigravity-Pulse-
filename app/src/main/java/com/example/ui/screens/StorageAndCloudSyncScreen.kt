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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.data.local.AntigravityDao
import com.example.data.local.ServerProfileEntity
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass
import com.example.viewmodel.AuthenticatedCloudSyncViewModel
import com.example.viewmodel.CloudUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun StorageAndCloudSyncScreen(
    dao: AntigravityDao,
    localProfiles: List<ServerProfileEntity>,
    onConnectHostUrl: (name: String, endpointUrl: String, authToken: String) -> Unit,
    onInsertPromptIntoRemote: (String) -> Unit,
    onOpenAddHostModal: () -> Unit,
    onClearLocalData: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val isFrostedGlass = appearance.isFrostedGlassEnabled

    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var authError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

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

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = surface,
            title = {
                Text(
                    text = "Clear Local Saved Hosts & History?",
                    style = MaterialTheme.typography.titleLarge,
                    color = fg
                )
            },
            text = {
                Text(
                    text = "This removes all locally saved Remote host URLs and Copilot history from this phone. Your Google Cloud backups remain untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryFg
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLocalData()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonError,
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = secondaryFg)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Saved Local Remote Hosts (Room SQLite — Always Ready)
        item {
            val hostCardShape = RoundedCornerShape(20.dp)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = hostCardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(
                        shape = hostCardShape,
                        baseColor = surface,
                        tintColor = EmeraldOnline,
                        borderColor = EmeraldOnline.copy(alpha = 0.55f),
                        enabled = isFrostedGlass,
                        fillAlpha = 0.82f,
                        tintAlpha = 0.06f,
                        borderAlpha = 0.58f
                    )
                    .testTag("local_room_storage_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Saved Remote Hosts",
                                tint = EmeraldOnline,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Saved Remote Hosts (${localProfiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldOnline
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenAddHostModal,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("sync_add_host_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Remote Host",
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Host", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (localProfiles.isEmpty()) {
                        Text(
                            text = "No Antigravity Remote hosts saved yet. Scan a QR code or paste a Remote URL to save it here and back it up to Google Cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    } else {
                        localProfiles.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .background(elevated, RoundedCornerShape(10.dp))
                                    .border(
                                        1.dp,
                                        if (profile.isSelected) accent.copy(alpha = 0.6f) else border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (profile.isSelected) accent else fg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = profile.endpointUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryFg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        onConnectHostUrl(
                                            profile.name,
                                            profile.endpointUrl,
                                            profile.authToken
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = Color(0xFF070708)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Connect",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_local_cache_button")
                        ) {
                            Text("Reset Local Saved Data", color = CrimsonError)
                        }
                    }
                }
            }
        }

        // 2. Google Cloud Sync Card (Firestore Backup for Remote Hosts & Reusable Prompts)
        item {
            val user = currentUser
            if (user == null) {
                val authCardShape = RoundedCornerShape(20.dp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = authCardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = authCardShape,
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.82f,
                            tintAlpha = 0.07f,
                            borderAlpha = 0.60f
                        )
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
                                text = "Google Cloud Sync (Firestore)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sign in with Google to back up your Antigravity Remote host URLs and save a synced library of reusable prompts you can insert into Antigravity in one tap.",
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
                        border = border,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        cloudViewModel = cloudViewModel,
                        onConnectHostUrl = onConnectHostUrl,
                        onInsertPromptIntoRemote = { promptText ->
                            clipboardManager.setText(AnnotatedString(promptText))
                            onInsertPromptIntoRemote(promptText)
                        },
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
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    cloudViewModel: AuthenticatedCloudSyncViewModel,
    onConnectHostUrl: (name: String, endpointUrl: String, authToken: String) -> Unit,
    onInsertPromptIntoRemote: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val profilesState by cloudViewModel.cloudProfilesState.collectAsStateWithLifecycle()
    val promptsState by cloudViewModel.cloudPromptsState.collectAsStateWithLifecycle()
    val syncMessage by cloudViewModel.syncMessage.collectAsStateWithLifecycle()
    val isSyncing by cloudViewModel.isSyncing.collectAsStateWithLifecycle()

    var newPromptTitle by rememberSaveable { mutableStateOf("") }
    var newPromptBody by rememberSaveable { mutableStateOf("") }
    var showAddPromptForm by rememberSaveable { mutableStateOf(false) }

    val isFrostedGlass = LocalAppearanceSettings.current.isFrostedGlassEnabled
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Card A: Cloud Account & Synced Remote Hosts
        val cloudCardShape = RoundedCornerShape(20.dp)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = cloudCardShape,
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(
                    shape = cloudCardShape,
                    baseColor = surface,
                    tintColor = accent,
                    borderColor = accent.copy(alpha = 0.55f),
                    enabled = isFrostedGlass,
                    fillAlpha = 0.82f,
                    tintAlpha = 0.07f,
                    borderAlpha = 0.60f
                )
                .testTag("authenticated_cloud_panel")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Signed in with Google",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Google Cloud Sync Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("google_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
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
                            contentDescription = "Backup Hosts to Cloud",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isSyncing) "Syncing..." else "Backup Hosts",
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
                                    contentDescription = "Restore Hosts to Phone",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pull Hosts (${cloudProfiles.size})")
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
                HorizontalDivider(color = border)
                Spacer(modifier = Modifier.height(10.dp))

                // Cloud Synced Remote Hosts
                when (val pState = profilesState) {
                    is CloudUiState.Loading -> Text(
                        "Loading cloud remote hosts...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg
                    )
                    is CloudUiState.Error -> Text(
                        "Cloud Hosts Error: ${pState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonError
                    )
                    is CloudUiState.Success -> {
                        Text(
                            text = "Cloud-Synced Remote Hosts (${pState.data.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                        if (pState.data.isEmpty()) {
                            Text(
                                text = "Tap 'Backup Hosts' above after connecting a Remote URL to sync your hosts across devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryFg,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            pState.data.forEach { cp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .background(elevated, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cp.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = fg,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = cp.endpointUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryFg,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onConnectHostUrl(cp.name, cp.endpointUrl, cp.authToken)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Connect", color = accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card B: Cloud-Synced Reusable Prompts Library (1-Tap Insert into Remote WebView!)
        val promptCardShape = RoundedCornerShape(20.dp)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = promptCardShape,
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(
                    shape = promptCardShape,
                    baseColor = surface,
                    tintColor = accent,
                    borderColor = border,
                    enabled = isFrostedGlass,
                    fillAlpha = 0.82f,
                    tintAlpha = 0.06f,
                    borderAlpha = 0.58f
                )
                .testTag("cloud_prompt_library_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Cloud Prompt Library",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Synced Prompt Snippets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                    }

                    OutlinedButton(
                        onClick = { showAddPromptForm = !showAddPromptForm },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("toggle_add_cloud_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Prompt Snippet",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showAddPromptForm) "Close" else "New Prompt",
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Save your frequently used Antigravity instructions to Firestore and insert them into Remote in one tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryFg
                )

                if (showAddPromptForm) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPromptTitle,
                        onValueChange = { newPromptTitle = it },
                        label = { Text("Snippet Title (e.g., Fix Build & Run Tests)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_prompt_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPromptBody,
                        onValueChange = { newPromptBody = it },
                        label = { Text("Prompt text to insert into Antigravity") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_prompt_body_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val title = newPromptTitle.trim().ifBlank { "Saved Prompt" }
                            val body = newPromptBody.trim()
                            if (body.isNotEmpty()) {
                                cloudViewModel.bookmarkPromptToCloud(
                                    title = title,
                                    promptText = body,
                                    rolePersona = "Antigravity Remote",
                                    preferredModel = "gemini-3.8-flash"
                                )
                                newPromptTitle = ""
                                newPromptBody = ""
                                showAddPromptForm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF070708)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_cloud_prompt_button")
                    ) {
                        Text("Save Prompt to Google Cloud", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (val prState = promptsState) {
                    is CloudUiState.Loading -> Text(
                        "Loading synced prompts...",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryFg
                    )
                    is CloudUiState.Error -> Text(
                        "Cloud Prompts Error: ${prState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonError
                    )
                    is CloudUiState.Success -> {
                        if (prState.data.isEmpty()) {
                            Text(
                                text = "No synced prompts yet. Tap '+ New Prompt' above to save your first reusable Antigravity prompt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryFg
                            )
                        } else {
                            prState.data.forEach { snippet ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .background(elevated, RoundedCornerShape(10.dp))
                                        .border(1.dp, border, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = snippet.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { cloudViewModel.deleteCloudPrompt(snippet.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Prompt",
                                                tint = CrimsonError,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = snippet.promptText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fg,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = { onInsertPromptIntoRemote(snippet.promptText) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accent,
                                                contentColor = Color(0xFF070708)
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Insert into Remote",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Insert into Remote", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
