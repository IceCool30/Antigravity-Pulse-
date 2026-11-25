package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import androidx.annotation.Keep
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val APP_CHECK_TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Mandatory App Check helper called in MainActivity.onCreate() before setContent or ViewModels.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() is required before DebugAppCheckProviderFactory initializes
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(APP_CHECK_TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(APP_CHECK_TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(APP_CHECK_TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(APP_CHECK_TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(APP_CHECK_TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(APP_CHECK_TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(APP_CHECK_TAG, "Failed to initialize Firebase App Check", e)
    }
}

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerIds = currentUser?.providerData?.map { it.providerId } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("isSignedIn", currentUser != null)
        put("emailVerified", currentUser?.isEmailVerified == true)
        put("providerIds", JSONArray(providerIds))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.javaClass.simpleName)
        put("operationType", operationType.value)
        put("path", path?.replace(Regex("users/[^/]+"), "users/<uid>"))
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    if (BuildConfig.DEBUG) {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    } else {
        Log.e("FirestoreError", "Firestore operation '${operationType.value}' failed.")
    }
    return jsonString
}

// --- Firestore Data Models (1:1 parity with firebase-blueprint.json & firestore.rules) ---

@Keep
@IgnoreExtraProperties
data class CloudServerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val endpointUrl: String = "",
    val connectionMode: String = "HYBRID_BRIDGE",
    val authToken: String = "",
    val keepAlivePinned: Boolean = true,
    val lastLatencyMs: Long = 10L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudCopilotPrompt(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val promptText: String = "",
    val rolePersona: String = "Antigravity Remote",
    val preferredModel: String = "default",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- Reactive FirebaseAuth Flow & Credential Manager Sign-In ---

private val firestoreCacheClearMutex = Mutex()

internal data class GoogleCredentialFailureDecision(
    val shouldFallbackToWebOAuth: Boolean,
    val userVisibleMessage: String?
)

internal fun safeFirebaseAuthOrNull(context: Context): FirebaseAuth? {
    return try {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isEmpty()) {
            FirebaseApp.initializeApp(appCtx) ?: return null
        }
        Firebase.auth
    } catch (_: Exception) {
        null
    }
}

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

internal fun classifyGoogleCredentialFailure(exception: Throwable): GoogleCredentialFailureDecision {
    val rawMessage = when (exception) {
        is GetCredentialException -> exception.errorMessage?.toString() ?: exception.message
        else -> exception.localizedMessage ?: exception.message
    }.orEmpty().trim()
    val lower = rawMessage.lowercase()

    return when (exception) {
        is GetCredentialCancellationException -> {
            val isPlayServicesFailure = listOf(
                "28444", "16:", "reauth", "developer", "configuration",
                "caller", "whitelisted", "sha", "cannot find", "failed",
                "error", "timeout", "network", "internal"
            ).any { token -> lower.contains(token) }

            if (isPlayServicesFailure) {
                GoogleCredentialFailureDecision(
                    shouldFallbackToWebOAuth = true,
                    userVisibleMessage = "Google Play Services sign-in failed ($rawMessage). Continuing in browser..."
                )
            } else {
                GoogleCredentialFailureDecision(
                    shouldFallbackToWebOAuth = false,
                    userVisibleMessage = "Google sign-in did not complete. Tap Sign in via Browser below if account selection keeps closing."
                )
            }
        }

        is NoCredentialException,
        is GetCredentialProviderConfigurationException,
        is GetCredentialUnknownException,
        is GetCredentialCustomException,
        is GetCredentialException -> {
            val detail = rawMessage.ifBlank { "Google Credential Manager is unavailable on this device." }
            GoogleCredentialFailureDecision(
                shouldFallbackToWebOAuth = true,
                userVisibleMessage = detail
            )
        }

        else -> GoogleCredentialFailureDecision(
            shouldFallbackToWebOAuth = false,
            userVisibleMessage = rawMessage.ifBlank { "Google Sign-In failed" }
        )
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    trySend(currentUser)
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private fun resolveWebClientId(context: Context): String? {
    return try {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId).trim().takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }
}

private suspend fun signInWithGoogleWebOAuth(
    activity: Activity,
    auth: FirebaseAuth
): FirebaseUser {
    val provider = OAuthProvider.newBuilder("google.com", auth)
        .setScopes(listOf("email", "profile"))
        .addCustomParameter("prompt", "select_account")
        .build()
    val result = withContext(NonCancellable) {
        auth.startActivityForSignInWithProvider(activity, provider).await()
    }
    return result.user ?: auth.currentUser
        ?: throw IllegalStateException("Google browser sign-in completed without an active user session.")
}

fun checkPendingGoogleSignInResult(
    context: Context,
    scope: CoroutineScope,
    onAuthUserConfirmed: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    onNoPending: () -> Unit = {}
) {
    val auth = safeFirebaseAuthOrNull(context) ?: run {
        onNoPending()
        return
    }
    val pendingTask = try {
        auth.pendingAuthResult
    } catch (_: Exception) {
        null
    }
    if (pendingTask == null) {
        onNoPending()
        return
    }
    scope.launch {
        try {
            val result = withContext(NonCancellable) { pendingTask.await() }
            val user = result.user ?: auth.currentUser
            if (user != null) {
                onAuthUserConfirmed(user)
            } else {
                onAuthError("Google sign-in completed without an active account session.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onAuthError(e.localizedMessage ?: "Google sign-in could not be completed.")
        }
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
    onAuthUserConfirmed: (FirebaseUser) -> Unit = {}
) {
    val auth = safeFirebaseAuthOrNull(context) ?: run {
        onUnauthenticated()
        return
    }
    val existing = auth.currentUser
    if (existing != null) {
        onAuthUserConfirmed(existing)
        onAuthSuccess()
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val targetContext = context.findActivity() ?: context
            val cred = credentialManager.getCredential(targetContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = withContext(NonCancellable) {
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                }
                val signedUser = authResult.user ?: auth.currentUser
                if (signedUser != null) {
                    onAuthUserConfirmed(signedUser)
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleBrowserSignInClicked(
    context: Context,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthUserConfirmed: (FirebaseUser) -> Unit = {}
) {
    val auth = safeFirebaseAuthOrNull(context) ?: run {
        onAuthError("Firebase authentication is not configured in this build.")
        return
    }
    val activityContext = context.findActivity() ?: run {
        onAuthError("Activity context required for Google Sign-In.")
        return
    }
    scope.launch {
        try {
            val signedUser = signInWithGoogleWebOAuth(activityContext, auth)
            onAuthUserConfirmed(signedUser)
            onAuthSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Browser Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Browser Google Sign-In failed.")
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (String?) -> Unit = {},
    onAuthUserConfirmed: (FirebaseUser) -> Unit = {}
) {
    val auth = safeFirebaseAuthOrNull(context) ?: run {
        onAuthError("Firebase authentication is not configured in this build.")
        return
    }
    val activityContext = context.findActivity() ?: run {
        onAuthError("Activity context required for interactive Google Sign-In.")
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        scope.launch {
            try {
                val signedUser = signInWithGoogleWebOAuth(activityContext, auth)
                onAuthUserConfirmed(signedUser)
                onAuthSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onAuthError(e.localizedMessage ?: "Google Sign-In configuration missing: default_web_client_id not found.")
            }
        }
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    scope.launch {
        try {
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = withContext(NonCancellable) {
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                }
                val signedUser = authResult.user ?: auth.currentUser
                if (signedUser != null) {
                    onAuthUserConfirmed(signedUser)
                    onAuthSuccess()
                } else {
                    onAuthError("Google Sign-In completed, but the Firebase user session did not persist.")
                }
            } else {
                onAuthError("Unexpected credential type returned")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            val decision = classifyGoogleCredentialFailure(e)
            if (decision.shouldFallbackToWebOAuth) {
                Log.w("Auth", "Credential Manager unavailable (${e.javaClass.simpleName}): falling back to web OAuth.")
                try {
                    val signedUser = signInWithGoogleWebOAuth(activityContext, auth)
                    onAuthUserConfirmed(signedUser)
                    onAuthSuccess()
                } catch (fallbackErr: CancellationException) {
                    throw fallbackErr
                } catch (fallbackErr: Exception) {
                    Log.e("Auth", "Google Web OAuth fallback failed", fallbackErr)
                    onAuthError(
                        fallbackErr.localizedMessage
                            ?: decision.userVisibleMessage
                            ?: "Google Sign-In failed."
                    )
                }
            } else {
                Log.w("Auth", "Google Sign-In cancelled: ${e.errorMessage}")
                onAuthCancelled(decision.userVisibleMessage)
            }
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed")
        }
    }
}

/**
 * Terminates the active Firestore client and wipes its on-disk offline cache so previously
 * signed-in user documents cannot linger on shared devices after sign-out or account switch.
 */
suspend fun clearFirestoreLocalCache(context: Context) {
    withContext(NonCancellable) {
        firestoreCacheClearMutex.withLock {
            try {
                if (FirebaseApp.getApps(context.applicationContext ?: context).isEmpty()) return@withLock
                val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                db.terminate().await()
                db.clearPersistence().await()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("Auth", "Firestore offline cache cleanup skipped: ${e.message}")
                }
            }
        }
    }
}

fun signOutFromGoogle(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onBeforeSignOut: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                onBeforeSignOut()
            } catch (_: Exception) {
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                }
            } finally {
                safeFirebaseAuthOrNull(context)?.signOut()
                clearFirestoreLocalCache(context)
                onSignOutComplete()
            }
        }
    }
}

// --- Firestore Cloud Sync Repository (Always uses R.string.firestore_database_id) ---

class CloudSyncRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before using Cloud Sync.")
    }

    private fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "doc_${System.currentTimeMillis()}" }
    }

    fun observeCloudServerProfiles(userId: String): Flow<List<CloudServerProfile>> = flow {
        require(userId.isNotBlank() && auth.currentUser?.uid == userId) {
            "Authenticated owner userId required"
        }
        val path = "users/$userId/serverProfiles"
        emitAll(
            db.collection("users").document(userId).collection("serverProfiles")
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudServerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudCopilotPrompts(userId: String): Flow<List<CloudCopilotPrompt>> = flow {
        require(userId.isNotBlank() && auth.currentUser?.uid == userId) {
            "Authenticated owner userId required"
        }
        val path = "users/$userId/copilotPrompts"
        emitAll(
            db.collection("users").document(userId).collection("copilotPrompts")
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudCopilotPrompt::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncServerProfileToCloud(
        profileId: String,
        name: String,
        endpointUrl: String,
        connectionMode: String,
        keepAlivePinned: Boolean,
        lastLatencyMs: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/serverProfiles"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(profileId)
            val cleanUrl = com.example.data.remote.RetainedWebViewManager
                .stripSensitiveUrlCredentials(endpointUrl)
                .take(300)
            require(
                com.example.data.remote.RetainedWebViewManager.isSafeHttpUrl(cleanUrl) &&
                    Regex("^https?://[a-zA-Z0-9._:\\[\\]-]+(:[0-9]{1,5})?(/[a-zA-Z0-9._~/%+-]*)?$").matches(cleanUrl)
            ) { "Only safe http:// or https:// host URLs can be backed up to Cloud Sync." }

            val validMode = if (connectionMode in listOf("HYBRID_BRIDGE", "DIRECT_TUNNEL", "PAIRING_TOKEN")) {
                connectionMode
            } else {
                "HYBRID_BRIDGE"
            }
            val docRef = db.collection("users").document(uid).collection("serverProfiles").document(safeId)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "name" to name.trim().take(80).ifEmpty { "Antigravity Host" },
                        "endpointUrl" to cleanUrl,
                        "connectionMode" to validMode,
                        // Zero-trust: never store plaintext workstation auth tokens in cloud backups
                        "authToken" to "",
                        "keepAlivePinned" to keepAlivePinned,
                        "lastLatencyMs" to lastLatencyMs.coerceIn(0, 60000).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to name.trim().take(80).ifEmpty { "Antigravity Host" },
                        "endpointUrl" to cleanUrl,
                        "connectionMode" to validMode,
                        // Zero-trust: never store plaintext workstation auth tokens in cloud backups
                        "authToken" to "",
                        "keepAlivePinned" to keepAlivePinned,
                        "lastLatencyMs" to lastLatencyMs.coerceIn(0, 60000).toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCopilotPromptToCloud(
        promptId: String,
        title: String,
        promptText: String,
        rolePersona: String,
        preferredModel: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/copilotPrompts"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(promptId)
            val docRef = db.collection("users").document(uid).collection("copilotPrompts").document(safeId)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "title" to title.trim().take(120).ifEmpty { "Saved Prompt" },
                        "promptText" to promptText.trim().take(5000).ifEmpty { "Prompt" },
                        "rolePersona" to rolePersona.trim().take(80).ifEmpty { "Copilot" },
                        "preferredModel" to preferredModel.trim().take(80).ifEmpty { "gemini-4-argon" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to title.trim().take(120).ifEmpty { "Saved Prompt" },
                        "promptText" to promptText.trim().take(5000).ifEmpty { "Prompt" },
                        "rolePersona" to rolePersona.trim().take(80).ifEmpty { "Copilot" },
                        "preferredModel" to preferredModel.trim().take(80).ifEmpty { "gemini-4-argon" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudCopilotPrompt(promptId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/copilotPrompts/$promptId"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(promptId)
            val docRef = db.collection("users").document(uid).collection("copilotPrompts").document(safeId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
