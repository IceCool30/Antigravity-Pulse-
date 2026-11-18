package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AntigravityDao
import com.example.data.local.CopilotMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "CopilotViewModel"

enum class GeminiCopilotModel(
    val modelId: String,
    val displayTitle: String,
    val badgeLabel: String,
    val description: String
) {
    GEMINI_4_ARGON(
        modelId = "gemini-4-argon",
        displayTitle = "Gemini 4 Argon",
        badgeLabel = "DEEP & INTERACTIVE",
        description = "Complex interactive engineering, architecture & deep reasoning"
    ),
    GEMINI_3_8_FLASH(
        modelId = "gemini-3.8-flash",
        displayTitle = "Gemini 3.8 Flash",
        badgeLabel = "GENERAL",
        description = "Balanced frontier speed & coding accuracy"
    ),
    GEMINI_3_5_FLASH_LITE(
        modelId = "gemini-3.5-flash-lite",
        displayTitle = "Gemini 3.5 Flash-Lite",
        badgeLabel = "ULTRA FAST",
        description = "Sub-second shell one-liners & quick syntax fixes"
    )
}

enum class CopilotRolePersona(
    val title: String,
    val systemPrompt: String
) {
    PROMPT_ARCHITECT(
        title = "Antigravity Prompt Architect",
        systemPrompt = "You are the Antigravity Prompt Architect embedded inside Antigravity Pulse on Android. Help the developer craft precise, unambiguous coding prompts, task breakdowns, and `/browser` MCP commands that can be sent directly to their remote Google Antigravity IDE agent."
    ),
    DEEP_DEBUGGER(
        title = "Deep Systems & Lag Debugger",
        systemPrompt = "You are a Principal Systems & Performance Engineer specializing in Google Antigravity remote sessions, WebSocket multiplexing, Chrome DevTools MCP debugging, and Android performance optimization. Provide actionable root-cause analysis and concise code patches."
    ),
    TERMINAL_COMMANDER(
        title = "Terminal & DevOps Specialist",
        systemPrompt = "You are a Linux & Android DevOps expert for headless `language_server --standalone` deployments, SSH/Tailscale port forwarding, OAuth token management (`~/.gemini/jetski-standalone-oauth-token`), and Gradle build acceleration."
    )
}

sealed interface CopilotChatUiState {
    data object Idle : CopilotChatUiState
    data object Loading : CopilotChatUiState
    data class Streaming(val partialText: String) : CopilotChatUiState
    data class Error(val message: String) : CopilotChatUiState
}

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Success(val bitmap: Bitmap, val promptUsed: String, val wasEdited: Boolean) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

class CopilotViewModel(
    private val dao: AntigravityDao
) : ViewModel() {

    val messages: StateFlow<List<CopilotMessageEntity>> = dao.observeCopilotMessages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedModel = MutableStateFlow(GeminiCopilotModel.GEMINI_4_ARGON)
    val selectedModel: StateFlow<GeminiCopilotModel> = _selectedModel.asStateFlow()

    private val _selectedPersona = MutableStateFlow(CopilotRolePersona.PROMPT_ARCHITECT)
    val selectedPersona: StateFlow<CopilotRolePersona> = _selectedPersona.asStateFlow()

    private val _chatUiState = MutableStateFlow<CopilotChatUiState>(CopilotChatUiState.Idle)
    val chatUiState: StateFlow<CopilotChatUiState> = _chatUiState.asStateFlow()

    private val _imageUiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val imageUiState: StateFlow<ImageStudioUiState> = _imageUiState.asStateFlow()

    private val _selectedInputBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedInputBitmap: StateFlow<Bitmap?> = _selectedInputBitmap.asStateFlow()

    // Lazy initialization of Gemini Nano Banana 2.1 image model per firebase-ai-logic-android
    private val nanoBananaImageModel: GenerativeModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    private fun buildChatModel(
        copilotModel: GeminiCopilotModel,
        persona: CopilotRolePersona
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = copilotModel.modelId,
            systemInstruction = content { text(persona.systemPrompt) }
        )
    }

    fun selectModel(model: GeminiCopilotModel) {
        _selectedModel.value = model
    }

    fun selectPersona(persona: CopilotRolePersona) {
        _selectedPersona.value = persona
    }

    fun setInputBitmapForEditing(bitmap: Bitmap?) {
        _selectedInputBitmap.value = bitmap
    }

    fun sendChatMessage(userPrompt: String) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty()) return

        val modelSpec = _selectedModel.value
        val persona = _selectedPersona.value

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val priorHistory = messages.value.takeLast(12).map { msg ->
                val role = if (msg.senderRole == "USER") "user" else "model"
                content(role) { text(msg.text) }
            }

            dao.insertCopilotMessage(
                CopilotMessageEntity(
                    id = "cop_u_$now",
                    senderRole = "USER",
                    text = cleanPrompt,
                    modelUsed = modelSpec.modelId,
                    rolePersona = persona.title,
                    timestampEpoch = now
                )
            )

            _chatUiState.value = CopilotChatUiState.Loading
            val accumulatedText = StringBuilder()

            try {
                val generativeModel = buildChatModel(modelSpec, persona)
                val chat = generativeModel.startChat(history = priorHistory)

                withTimeout(60_000L) {
                    chat.sendMessageStream(cleanPrompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulatedText.append(chunkText)
                        _chatUiState.value = CopilotChatUiState.Streaming(accumulatedText.toString())
                    }
                }

                val finalResponse = accumulatedText.toString().trim()
                if (finalResponse.isNotEmpty()) {
                    dao.insertCopilotMessage(
                        CopilotMessageEntity(
                            id = "cop_m_${System.currentTimeMillis()}",
                            senderRole = "MODEL",
                            text = finalResponse,
                            modelUsed = modelSpec.modelId,
                            rolePersona = persona.title,
                            timestampEpoch = System.currentTimeMillis()
                        )
                    )
                    _chatUiState.value = CopilotChatUiState.Idle
                } else {
                    _chatUiState.value = CopilotChatUiState.Error("Model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Copilot streaming timed out", e)
                _chatUiState.value = CopilotChatUiState.Error(
                    "Generation timed out after 60 seconds. Try switching to Gemini 3.5 Flash-Lite for faster responses."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Copilot generation failed", e)
                _chatUiState.value = CopilotChatUiState.Error(
                    e.localizedMessage ?: "Gemini inference failed."
                )
            }
        }
    }

    fun generateOrEditUiAsset(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty()) return

        val inputBitmap = _selectedInputBitmap.value
        viewModelScope.launch {
            _imageUiState.value = ImageStudioUiState.Loading
            try {
                val outputBitmap = withTimeout(60_000L) {
                    if (inputBitmap != null) {
                        val response = nanoBananaImageModel.generateContent(
                            content {
                                image(inputBitmap)
                                text(cleanPrompt)
                            }
                        )
                        response.candidates.firstOrNull()
                            ?.content?.parts?.filterIsInstance<ImagePart>()
                            ?.firstOrNull()?.image
                    } else {
                        val response = nanoBananaImageModel.generateContent(cleanPrompt)
                        response.candidates.firstOrNull()
                            ?.content?.parts?.filterIsInstance<ImagePart>()
                            ?.firstOrNull()?.image
                    }
                }

                if (outputBitmap != null) {
                    _imageUiState.value = ImageStudioUiState.Success(
                        bitmap = outputBitmap,
                        promptUsed = cleanPrompt,
                        wasEdited = inputBitmap != null
                    )
                    // Allow chained iterative edits on the newly generated image if desired
                    _selectedInputBitmap.value = outputBitmap
                } else {
                    _imageUiState.value = ImageStudioUiState.Error("No image was returned by gemini-nano-banana-2.1.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _imageUiState.value = ImageStudioUiState.Error("Image synthesis timed out after 60 seconds.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Quota exceeded for gemini-nano-banana-2.1", e)
                _imageUiState.value = ImageStudioUiState.Error("Rate limit or quota exceeded. Please try again later.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation/editing failed", e)
                _imageUiState.value = ImageStudioUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun clearCopilotHistory() {
        viewModelScope.launch {
            dao.clearCopilotMessages()
        }
    }

    companion object {
        fun provideFactory(dao: AntigravityDao): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CopilotViewModel(dao)
            }
        }
    }
}
