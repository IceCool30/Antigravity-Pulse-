package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val endpointUrl: String,
    val connectionMode: String, // HYBRID_BRIDGE, DIRECT_TUNNEL, PAIRING_TOKEN, SIMULATOR
    val authToken: String = "",
    val keepAlivePinned: Boolean = true,
    val lastLatencyMs: Int = 12,
    val isSelected: Boolean = false,
    val updatedAtEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_sessions")
data class AgentSessionEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val workspaceName: String,
    val branchName: String,
    val status: String, // AWAITING_APPROVAL, ACTIVE, IDLE, COMPLETED
    val lastSummary: String,
    val messageCount: Int,
    val savedScrollIndex: Int = 0,
    val draftPrompt: String = "",
    val updatedAtEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "session_messages")
data class SessionMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val senderRole: String, // USER, AGENT, THOUGHT, TOOL_APPROVAL, TERMINAL
    val content: String,
    val commandOrPath: String = "",
    val approvalStatus: String = "NONE", // NONE, PENDING, APPROVED, REJECTED
    val durationMs: Int = 0,
    val timestampEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_diffs")
data class FileDiffEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val filePath: String,
    val language: String,
    val additions: Int,
    val deletions: Int,
    val unifiedPatch: String,
    val status: String = "MODIFIED", // MODIFIED, ADDED, ACCEPTED, REVERTED
    val updatedAtEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "terminal_logs")
data class TerminalLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val command: String,
    val output: String,
    val exitCode: Int = 0,
    val durationMs: Int = 45,
    val timestampEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "copilot_messages")
data class CopilotMessageEntity(
    @PrimaryKey val id: String,
    val senderRole: String, // USER, MODEL
    val text: String,
    val modelUsed: String,
    val rolePersona: String,
    val attachedToSessionId: String = "",
    val timestampEpoch: Long = System.currentTimeMillis()
)
