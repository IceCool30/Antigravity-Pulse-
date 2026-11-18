package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AntigravityDao {
    // --- Server Profiles ---
    @Query("SELECT * FROM server_profiles ORDER BY isSelected DESC, updatedAtEpoch DESC")
    fun observeServerProfiles(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles")
    suspend fun getAllServerProfilesOnce(): List<ServerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServerProfile(profile: ServerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServerProfiles(profiles: List<ServerProfileEntity>)

    @Query("UPDATE server_profiles SET isSelected = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun selectServerProfile(profileId: String)

    @Query("UPDATE server_profiles SET lastLatencyMs = :latencyMs, updatedAtEpoch = :now WHERE id = :profileId")
    suspend fun updateServerLatency(profileId: String, latencyMs: Int, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM server_profiles WHERE id = :profileId")
    suspend fun deleteServerProfile(profileId: String)

    // --- Agent Sessions ---
    @Query("SELECT * FROM agent_sessions ORDER BY updatedAtEpoch DESC")
    fun observeAgentSessions(): Flow<List<AgentSessionEntity>>

    @Query("SELECT * FROM agent_sessions")
    suspend fun getAllSessionsOnce(): List<AgentSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentSession(session: AgentSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentSessions(sessions: List<AgentSessionEntity>)

    @Query("UPDATE agent_sessions SET draftPrompt = :draft, savedScrollIndex = :scrollIndex WHERE id = :sessionId")
    suspend fun updateSessionDraftAndScroll(sessionId: String, draft: String, scrollIndex: Int)

    @Query("UPDATE agent_sessions SET status = :status, lastSummary = :summary, messageCount = messageCount + :deltaCount, updatedAtEpoch = :now WHERE id = :sessionId")
    suspend fun updateSessionStatus(
        sessionId: String,
        status: String,
        summary: String,
        deltaCount: Int = 0,
        now: Long = System.currentTimeMillis()
    )

    // --- Session Messages ---
    @Query("SELECT * FROM session_messages WHERE sessionId = :sessionId ORDER BY timestampEpoch ASC")
    fun observeMessagesForSession(sessionId: String): Flow<List<SessionMessageEntity>>

    @Query("SELECT * FROM session_messages ORDER BY timestampEpoch ASC")
    fun observeAllSessionMessages(): Flow<List<SessionMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionMessage(message: SessionMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionMessages(messages: List<SessionMessageEntity>)

    @Query("UPDATE session_messages SET approvalStatus = :status WHERE id = :messageId")
    suspend fun updateMessageApprovalStatus(messageId: String, status: String)

    // --- File Diffs ---
    @Query("SELECT * FROM file_diffs WHERE sessionId = :sessionId ORDER BY updatedAtEpoch DESC")
    fun observeDiffsForSession(sessionId: String): Flow<List<FileDiffEntity>>

    @Query("SELECT * FROM file_diffs ORDER BY updatedAtEpoch DESC")
    fun observeAllDiffs(): Flow<List<FileDiffEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileDiff(diff: FileDiffEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileDiffs(diffs: List<FileDiffEntity>)

    @Query("UPDATE file_diffs SET status = :status, updatedAtEpoch = :now WHERE id = :diffId")
    suspend fun updateDiffStatus(diffId: String, status: String, now: Long = System.currentTimeMillis())

    // --- Terminal Logs ---
    @Query("SELECT * FROM terminal_logs WHERE sessionId = :sessionId ORDER BY timestampEpoch ASC")
    fun observeTerminalLogsForSession(sessionId: String): Flow<List<TerminalLogEntity>>

    @Query("SELECT * FROM terminal_logs ORDER BY timestampEpoch ASC")
    fun observeAllTerminalLogs(): Flow<List<TerminalLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerminalLog(log: TerminalLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerminalLogs(logs: List<TerminalLogEntity>)

    @Query("DELETE FROM terminal_logs WHERE sessionId = :sessionId")
    suspend fun clearTerminalLogs(sessionId: String)

    // --- Copilot Messages ---
    @Query("SELECT * FROM copilot_messages ORDER BY timestampEpoch ASC")
    fun observeCopilotMessages(): Flow<List<CopilotMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCopilotMessage(message: CopilotMessageEntity)

    @Query("DELETE FROM copilot_messages")
    suspend fun clearCopilotMessages()

    // --- Clean Reset Queries ---
    @Query("DELETE FROM server_profiles")
    suspend fun clearAllServerProfiles()

    @Query("DELETE FROM agent_sessions")
    suspend fun clearAllAgentSessions()

    @Query("DELETE FROM session_messages")
    suspend fun clearAllSessionMessages()

    @Query("DELETE FROM file_diffs")
    suspend fun clearAllFileDiffs()

    @Query("DELETE FROM terminal_logs")
    suspend fun clearAllTerminalLogs()
}
