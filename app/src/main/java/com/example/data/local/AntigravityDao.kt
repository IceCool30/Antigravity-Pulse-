package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AntigravityDao {
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

    @Query("DELETE FROM server_profiles WHERE id = :profileId")
    suspend fun deleteServerProfile(profileId: String)

    @Query("DELETE FROM server_profiles")
    suspend fun clearAllServerProfiles()
}
